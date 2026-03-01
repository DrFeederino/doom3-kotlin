/*
 * Copyright (C) 1999-2011 id Software LLC, a ZeniMax Media company.
 * Translated to Kotlin by Dr. Feederino with support of Claude Code
 *
 * This file is part of the Doom 3 Kotlin project.
 * Original source: neo/Game/Item.cpp, neo/Game/Item.h
 *
 * Doom 3 Source Code is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Doom 3 Source Code is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 */

package neo.Game

import neo.Game.GameSys.Class.*
import neo.Game.GameSys.EV_Remove
import neo.Game.GameSys.Event.idEventDef
import neo.Game.GameSys.SaveGame.idRestoreGame
import neo.Game.GameSys.SaveGame.idSaveGame
import neo.Game.GameSys.SysCvar
import neo.Game.Game_local.Companion.gameRenderWorld
import neo.Game.Game_local.gameSoundChannel_t
import neo.Game.Game_local.idGameLocal
import neo.Game.Physics.Clip.idClipModel
import neo.Game.Physics.Physics_RigidBody.idPhysics_RigidBody
import neo.Game.Player.idPlayer
import neo.Renderer.Material
import neo.Renderer.RenderSystem.SCREEN_HEIGHT
import neo.Renderer.RenderSystem.SCREEN_WIDTH
import neo.Renderer.RenderSystem.renderSystem
import neo.Renderer.RenderWorld.deferredEntityCallback_t
import neo.Renderer.RenderWorld.renderEntity_s
import neo.Renderer.RenderWorld.renderView_s
import neo.TempDump
import neo.cm.CM_CLIP_EPSILON
import neo.cm.collisionModelManager
import neo.cm.trace_s
import neo.framework.DeclManager
import neo.framework.DeclManager.declType_t
import neo.framework.DeclParticle.idDeclParticle
import neo.framework.DeclSkin.idDeclSkin
import neo.idlib.BV.idBounds
import neo.idlib.BitMsg.idBitMsg
import neo.idlib.BitMsg.idBitMsgDelta
import neo.idlib.Dict_h.idDict
import neo.idlib.Dict_h.idKeyValue
import neo.idlib.Text.Str
import neo.idlib.Text.Str.idStr
import neo.idlib.containers.CFloat
import neo.idlib.containers.CInt
import neo.idlib.containers.List
import neo.idlib.geometry.TraceModel.idTraceModel
import neo.idlib.math.Matrix.idMat3
import neo.idlib.math.getVec3Origin
import neo.idlib.math.idAngles
import neo.idlib.math.idMath
import neo.idlib.math.idVec3
import java.nio.ByteBuffer
import java.util.*
import kotlin.math.ceil
import kotlin.math.cos


val EV_CamShot: idEventDef = idEventDef("<camshot>")
val EV_DropToFloor: idEventDef = idEventDef("<dropToFloor>")
val EV_GetPlayerPos: idEventDef = idEventDef("<getplayerpos>")
val EV_HideObjective: idEventDef = idEventDef("<hideobjective>", "e")
val EV_RespawnFx: idEventDef = idEventDef("<respawnFx>")
val EV_RespawnItem: idEventDef = idEventDef("respawn")


/*
 ===============================================================================

 Items the player can pick up or use.

 ===============================================================================
 */
open class idItem : idEntity() {
    companion object {
        // enum {
        val EVENT_PICKUP: Int = idEntity.EVENT_MAXEVENTS
        val EVENT_MAXEVENTS = EVENT_PICKUP + 3
        val EVENT_RESPAWN = EVENT_PICKUP + 1
        val EVENT_RESPAWNFX = EVENT_PICKUP + 2

        // public	CLASS_PROTOTYPE( idItem );
        private val eventCallbacks: MutableMap<idEventDef, eventCallback_t<*>> = HashMap()

        // virtual					~idItem();
        fun getEventCallBacks(): MutableMap<idEventDef, eventCallback_t<*>> {
            return eventCallbacks
        }

        init {
            eventCallbacks.putAll(idEntity.getEventCallBacks())
            eventCallbacks[EV_DropToFloor] =
                eventCallback_t0<idItem> { obj: idItem -> obj.Event_DropToFloor() }
            eventCallbacks[EV_Touch] =
                eventCallback_t2<idItem> { obj: idItem, _other: idEventArg<*>?, trace: idEventArg<*>? ->
                    obj.Event_Touch(
                        _other as idEventArg<idEntity>,
                        trace as idEventArg<trace_s>
                    )
                }
            eventCallbacks[EV_Activate] =
                eventCallback_t1<idItem> { obj: idItem, _activator: idEventArg<*>? -> obj.Event_Trigger(_activator as idEventArg<idEntity>) }
            eventCallbacks[EV_RespawnItem] =
                eventCallback_t0<idItem> { obj: idItem -> obj.Event_Respawn() }
            eventCallbacks[EV_RespawnFx] =
                eventCallback_t0<idItem> { obj: idItem -> obj.Event_RespawnFx() }
        }
    }

    // };
    private val orgOrigin: idVec3
    private var canPickUp: Boolean

    //
    // used to update the item pulse effect
    private var inView = false
    private var inViewTime = 0

    //
    // for item pulse effect
    private var itemShellHandle: Int
    private var lastCycle = 0
    private var lastRenderViewTime: Int
    private var pulse = false
    private var shellMaterial: Material.idMaterial?
    private var spin = false
    override fun _deconstructor() {
        // remove the highlight shell
        if (itemShellHandle != -1) {
            gameRenderWorld!!.FreeEntityDef(itemShellHandle)
        }
        super._deconstructor()
    }

    override fun Save(savefile: idSaveGame) {
        savefile.WriteVec3(orgOrigin)
        savefile.WriteBool(spin)
        savefile.WriteBool(pulse)
        savefile.WriteBool(canPickUp)
        savefile.WriteMaterial(shellMaterial)
        savefile.WriteBool(inView)
        savefile.WriteInt(inViewTime)
        savefile.WriteInt(lastCycle)
        savefile.WriteInt(lastRenderViewTime)
    }

    override fun Restore(savefile: idRestoreGame) {
        savefile.ReadVec3(orgOrigin)
        spin = savefile.ReadBool()
        pulse = savefile.ReadBool()
        canPickUp = savefile.ReadBool()
        savefile.ReadMaterial(shellMaterial!!)
        inView = savefile.ReadBool()
        inViewTime = savefile.ReadInt()
        lastCycle = savefile.ReadInt()
        lastRenderViewTime = savefile.ReadInt()
        itemShellHandle = -1
    }

    override fun Spawn() {
        super.Spawn()
        val giveTo: String?
        val ent: idEntity?
        val tsize = CFloat()
        if (spawnArgs.GetBool("dropToFloor")) {
            PostEventMS(EV_DropToFloor, 0)
        }
        if (spawnArgs.GetFloat("triggersize", "0", tsize)) {
            GetPhysics().GetClipModel()!!
                .LoadModel(idTraceModel(idBounds(getVec3Origin()).Expand(tsize._val)))
            GetPhysics().GetClipModel()!!.Link(Game_local.gameLocal.clip)
        }
        if (spawnArgs.GetBool("start_off")) {
            GetPhysics().SetContents(0)
            Hide()
        } else {
            GetPhysics().SetContents(Material.CONTENTS_TRIGGER)
        }
        giveTo = spawnArgs.GetString("owner")
        if (giveTo.length != 0) {
            ent = Game_local.gameLocal.FindEntity(giveTo)
            if (ent == null) {
                idGameLocal.Error("Item couldn't find owner '%s'", giveTo)
            }
            PostEventMS(EV_Touch, 0, ent, null)
        }
        if (spawnArgs.GetBool("spin") || Game_local.gameLocal.isMultiplayer) {
            spin = true
            BecomeActive(TH_THINK)
        }

        //temp hack for tim
        pulse = false
        orgOrigin.set(GetPhysics().GetOrigin())
        canPickUp = !(spawnArgs.GetBool("triggerFirst") || spawnArgs.GetBool("no_touch"))
        inViewTime = -1000
        lastCycle = -1
        itemShellHandle = -1
        shellMaterial = DeclManager.declManager.FindMaterial("itemHighlightShell")
    }

    fun GetAttributes(attributes: idDict) {
        var i: Int
        var arg: idKeyValue?
        i = 0
        while (i < spawnArgs.GetNumKeyVals()) {
            arg = spawnArgs.GetKeyVal(i)!!
            if (arg.GetKey().Left(4).toString() == "inv_") {
                attributes.Set(arg.GetKey().Right(arg.GetKey().Length() - 4), arg.GetValue())
            }
            i++
        }
    }

    open fun GiveToPlayer(player: idPlayer?): Boolean {
        if (player == null) {
            return false
        }
        return if (spawnArgs.GetBool("inv_carry")) {
            player.GiveInventoryItem(spawnArgs)
        } else player.GiveItem(this)
    }

    open fun Pickup(player: idPlayer?): Boolean {
        if (!GiveToPlayer(player)) {
            return false
        }
        if (Game_local.gameLocal.isServer) {
            ServerSendEvent(EVENT_PICKUP, null, false, -1)
        }

        // play pickup sound
        StartSound("snd_acquire", gameSoundChannel_t.SND_CHANNEL_ITEM, 0, false)

        // trigger our targets
        ActivateTargets(player)

        // clear our contents so the object isn't picked up twice
        GetPhysics().SetContents(0)

        // hide the model
        Hide()

        // add the highlight shell
        if (itemShellHandle != -1) {
            gameRenderWorld!!.FreeEntityDef(itemShellHandle)
            itemShellHandle = -1
        }
        var respawn = spawnArgs.GetFloat("respawn")
        val dropped = spawnArgs.GetBool("dropped")
        val no_respawn = spawnArgs.GetBool("no_respawn")
        if (Game_local.gameLocal.isMultiplayer && respawn == 0.0f) {
            respawn = 20.0f
        }
        if (respawn != 0.0f && !dropped && !no_respawn) {
            val sfx = spawnArgs.GetString("fxRespawn")
            if (sfx != null && !sfx.isEmpty()) {
                PostEventSec(EV_RespawnFx, respawn - 0.5f)
            }
            PostEventSec(EV_RespawnItem, respawn)
        } else if (!spawnArgs.GetBool("inv_objective") && !no_respawn) {
            // give some time for the pickup sound to play
            // FIXME: Play on the owner
            if (!spawnArgs.GetBool("inv_carry")) {
                PostEventMS(EV_Remove, 5000)
            }
        }
        BecomeInactive(TH_THINK)
        return true
    }

    override fun Think() {
        if ((thinkFlags and TH_THINK) != 0) {
            if (spin) {
                val ang = idAngles()
                val org = idVec3()
                ang.roll = 0.0f
                ang.pitch = ang.roll
                ang.yaw = (Game_local.gameLocal.time and 4095) * 360.0f / -4096.0f
                SetAngles(ang)
                val scale = 0.005 + entityNumber * 0.00001
                org.set(orgOrigin)
                org.z += (4.0f + cos(((Game_local.gameLocal.time + 2000) * scale).toFloat()) * 4.0f)
                SetOrigin(org)
            }
        }
        Present()
    }

    override fun Present() {
        super.Present()
        if (!fl.hidden && pulse) {
            // also add a highlight shell model
            // C++ creates a copy: renderEntity_t shell = renderEntity;
            // Kotlin has no copy constructor, so we save/restore modified fields
            val shell = renderEntity!!
            val origCallback = shell.callback
            val origEntityNum = shell.entityNum
            val origCustomShader = shell.customShader

            // we will mess with shader parms when the item is in view
            // to give the "item pulse" effect
            shell.callback = ModelCallback.getInstance()
            shell.entityNum = entityNumber
            shell.customShader = shellMaterial
            if (itemShellHandle == -1) {
                itemShellHandle = gameRenderWorld!!.AddEntityDef(shell)
            } else {
                gameRenderWorld!!.UpdateEntityDef(itemShellHandle, shell)
            }

            // restore original values (C++ used a stack copy that went out of scope)
            shell.callback = origCallback
            shell.entityNum = origEntityNum
            shell.customShader = origCustomShader
        }
    }

    override fun ClientPredictionThink() {
        // only think forward because the state is not synced through snapshots
        if (!Game_local.gameLocal.isNewFrame) {
            return
        }
        Think()
    }

    override fun ClientReceiveEvent(event: Int, time: Int, msg: idBitMsg): Boolean {
        return when (event) {
            EVENT_PICKUP -> {
                // play pickup sound
                StartSound("snd_acquire", gameSoundChannel_t.SND_CHANNEL_ITEM, 0, false)

                // hide the model
                Hide()

                // remove the highlight shell
                if (itemShellHandle != -1) {
                    gameRenderWorld!!.FreeEntityDef(itemShellHandle)
                    itemShellHandle = -1
                }
                true
            }

            EVENT_RESPAWN -> {
                Event_Respawn()
                true
            }

            EVENT_RESPAWNFX -> {
                Event_RespawnFx()
                true
            }

            else -> {
                super.ClientReceiveEvent(event, time, msg)
            }
        }
        //	return false;
    }

    // networking
    override fun WriteToSnapshot(msg: idBitMsgDelta) {
        msg.WriteBits(TempDump.btoi(IsHidden()), 1)
    }

    override fun ReadFromSnapshot(msg: idBitMsgDelta) {
        if (msg.ReadBits(1) != 0) {
            Hide()
        } else {
            Show()
        }
    }

    override fun UpdateRenderEntity(renderEntity: renderEntity_s, renderView: renderView_s?): Boolean {
        if (lastRenderViewTime == renderView!!.time) {
            return false
        }
        lastRenderViewTime = renderView.time

        // check for glow highlighting if near the center of the view
        val dir = idVec3(renderEntity.origin.minus(renderView.vieworg))
        dir.Normalize()
        val d = dir.times(renderView.viewaxis[0])

        // two second pulse cycle
        var cycle = (renderView.time - inViewTime) / 2000.0f
        if (d > 0.94f) {
            if (!inView) {
                inView = true
                if (cycle > lastCycle) {
                    // restart at the beginning
                    inViewTime = renderView.time
                    cycle = 0.0f
                }
            }
        } else {
            if (inView) {
                inView = false
                lastCycle = ceil(cycle).toInt()
            }
        }

        // fade down after the last pulse finishes
        if (!inView && cycle > lastCycle) {
            renderEntity.shaderParms[4] = 0.0f
        } else {
            // pulse up in 1/4 second
            cycle -= cycle.toInt().toFloat()
            if (cycle < 0.1f) {
                renderEntity.shaderParms[4] = cycle * 10.0f
            } else if (cycle < 0.2f) {
                renderEntity.shaderParms[4] = 1.0f
            } else if (cycle < 0.3f) {
                renderEntity.shaderParms[4] = 1.0f - (cycle - 0.2f) * 10.0f
            } else {
                // stay off between pulses
                renderEntity.shaderParms[4] = 0.0f
            }
        }

        // update every single time this is in view
        return true
    }

    override fun CreateInstance(): idClass {
        throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
    }

    private fun Event_DropToFloor() {
        val trace = trace_s()

        // don't drop the floor if bound to another entity
        if (GetBindMaster() != null && GetBindMaster() != this) {
            return
        }
        Game_local.gameLocal.clip.TraceBounds(
            trace,
            renderEntity!!.origin,
            renderEntity!!.origin.minus(idVec3(0, 0, 64)),
            renderEntity!!.bounds,
            Game_local.MASK_SOLID or Material.CONTENTS_CORPSE,
            this
        )
        SetOrigin(trace.endpos)
    }

    private fun Event_Touch(_other: idEventArg<idEntity>, trace: idEventArg<trace_s>) {
        val other = _other.value as? idPlayer ?: return
        if (!canPickUp) {
            return
        }
        Pickup(other)
    }

    private fun Event_Trigger(_activator: idEventArg<idEntity>) {
        val activator = _activator.value
        if (!canPickUp && spawnArgs.GetBool("triggerFirst")) {
            canPickUp = true
            return
        }
        if (activator != null && activator is idPlayer) {
            Pickup(activator as idPlayer?)
        }
    }

    private fun Event_Respawn() {
        if (Game_local.gameLocal.isServer) {
            ServerSendEvent(EVENT_RESPAWN, null, false, -1)
        }
        BecomeActive(TH_THINK)
        Show()
        inViewTime = -1000
        lastCycle = -1
        GetPhysics().SetContents(Material.CONTENTS_TRIGGER)
        SetOrigin(orgOrigin)
        StartSound("snd_respawn", gameSoundChannel_t.SND_CHANNEL_ITEM, 0, false)
        CancelEvents(EV_RespawnItem) // don't double respawn
    }

    private fun Event_RespawnFx() {
        if (Game_local.gameLocal.isServer) {
            ServerSendEvent(EVENT_RESPAWNFX, null, false, -1)
        }
        val sfx = spawnArgs.GetString("fxRespawn")
        if (sfx != "" && !sfx.isEmpty()) {
            idEntityFx.StartFx(sfx, null, null, this, true)
        }
    }

    override fun getEventCallBack(event: idEventDef): eventCallback_t<*>? {
        return eventCallbacks[event]
    }

    class ModelCallback private constructor() : deferredEntityCallback_t() {
        override fun run(e: renderEntity_s?, v: renderView_s?): Boolean {
            val ent: idItem

            // this may be triggered by a model trace or other non-view related source
            if (null == v) {
                return false
            }
            ent = Game_local.gameLocal.entities[e!!.entityNum] as idItem
            if (null == ent) {
                idGameLocal.Error("obj.ModelCallback: callback with NULL game entity")
            }
            return ent.UpdateRenderEntity(e, v)
        }

        override fun AllocBuffer(): ByteBuffer {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        override fun Read(buffer: ByteBuffer) {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        override fun Write(): ByteBuffer {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        companion object {
            private val instance: deferredEntityCallback_t = ModelCallback()
            fun getInstance(): deferredEntityCallback_t {
                return instance
            }
        }
    }

    //
    //
    init {
        lastRenderViewTime = -1
        itemShellHandle = -1
        shellMaterial = null
        orgOrigin = idVec3()
        canPickUp = true
        fl.networkSync = true
    }
}

/*
 ===============================================================================

 idItemPowerup

 ===============================================================================
 */
class idItemPowerup : idItem() {
    // public 	CLASS_PROTOTYPE( idItemPowerup );
    private val time: CInt = CInt()
    private val type: CInt = CInt()
    override fun Save(savefile: idSaveGame) {
        savefile.WriteInt(time._val)
        savefile.WriteInt(type._val)
    }

    override fun Restore(savefile: idRestoreGame) {
        savefile.ReadInt(time)
        savefile.ReadInt(type)
    }

    override fun Spawn() {
        super.Spawn()
        time._val = (spawnArgs.GetInt("time", "30"))
        type._val = (spawnArgs.GetInt("type", "0"))
    }

    override fun GiveToPlayer(player: idPlayer?): Boolean {
        if (player!!.spectating) {
            return false
        }
        player.GivePowerUp(type._val, time._val * 1000)
        return true
    }

    //
    //
    init {
        time._val = 0
        type._val = 0
    }
}

/*
 ===============================================================================

 idObjective

 ===============================================================================
 */
class idObjective : idItem() {
    companion object {
        //public 	CLASS_PROTOTYPE( idObjective );
        private val eventCallbacks: MutableMap<idEventDef, eventCallback_t<*>> = HashMap()
        fun getEventCallBacks(): MutableMap<idEventDef, eventCallback_t<*>> {
            return eventCallbacks
        }

        init {
            eventCallbacks.putAll(idItem.getEventCallBacks())
            eventCallbacks[EV_Activate] =
                eventCallback_t1<idObjective> { obj: idObjective, activator: idEventArg<*>? ->
                    obj.Event_Trigger(activator as idEventArg<idEntity>)
                }
            eventCallbacks[EV_HideObjective] =
                eventCallback_t1<idObjective> { obj: idObjective, e: idEventArg<*>? -> obj.Event_HideObjective(e as idEventArg<idEntity>) }
            eventCallbacks[EV_GetPlayerPos] =
                eventCallback_t0<idObjective> { obj: idObjective -> obj.Event_GetPlayerPos() }
            eventCallbacks[EV_CamShot] =
                eventCallback_t0<idObjective> { obj: idObjective -> obj.Event_CamShot() }
        }
    }

    private val playerPos: idVec3
    override fun Save(savefile: idSaveGame) {
        savefile.WriteVec3(playerPos)
    }

    override fun Restore(savefile: idRestoreGame) {
        savefile.ReadVec3(playerPos)
        PostEventMS(EV_CamShot, 250)
    }

    override fun Spawn() {
        super.Spawn()
        Hide()
        PostEventMS(EV_CamShot, 250)
    }

    private fun Event_Trigger(activator: idEventArg<idEntity>) {
        val player = Game_local.gameLocal.GetLocalPlayer()
        if (player != null) {

            //Pickup( player );
            if (spawnArgs.GetString("inv_objective", null) != null) {
                if ( /*player &&*/player.hud != null) {
                    val shotName = idStr(Game_local.gameLocal.GetMapName())
                    shotName.StripFileExtension()
                    shotName.plusAssign("/")
                    shotName.plusAssign(spawnArgs.GetString("screenshot"))
                    shotName.SetFileExtension(".tga")
                    player.hud!!.SetStateString("screenshot", shotName.toString())
                    player.hud!!.SetStateString("objective", "1")
                    player.hud!!.SetStateString("objectivetext", spawnArgs.GetString("objectivetext"))
                    player.hud!!.SetStateString("objectivetitle", spawnArgs.GetString("objectivetitle"))
                    player.GiveObjective(
                        spawnArgs.GetString("objectivetitle"),
                        spawnArgs.GetString("objectivetext"),
                        shotName.toString()
                    )

                    // a tad slow but keeps from having to update all objectives in all maps with a name ptr
                    for (i in 0 until Game_local.gameLocal.num_entities) {
                        if (Game_local.gameLocal.entities[i] != null && Game_local.gameLocal.entities[i] is idObjectiveComplete) {
                            if (idStr.Icmp(
                                    spawnArgs.GetString("objectivetitle"),
                                    Game_local.gameLocal.entities[i]!!.spawnArgs.GetString("objectivetitle")
                                ) == 0
                            ) {
                                Game_local.gameLocal.entities[i]!!.spawnArgs.SetBool("objEnabled", true)
                                break
                            }
                        }
                    }
                    PostEventMS(EV_GetPlayerPos, 2000)
                }
            }
        }
    }

    private fun Event_HideObjective(e: idEventArg<idEntity>) {
        val player = Game_local.gameLocal.GetLocalPlayer()
        if (player != null) {
            val v = player.GetPhysics().GetOrigin() - playerPos
            if (v.Length() > 64.0f) {
                player.HideObjective()
                PostEventMS(EV_Remove, 0)
            } else {
                PostEventMS(EV_HideObjective, 100.0f, player)
            }
        }
    }

    private fun Event_GetPlayerPos() {
        val player = Game_local.gameLocal.GetLocalPlayer()
        if (player != null) {
            playerPos.set(player.GetPhysics().GetOrigin())
            PostEventMS(EV_HideObjective, 100.0f, player)
        }
    }

    private fun Event_CamShot() {
        val camName = arrayOfNulls<String>(1)
        val shotName = idStr(Game_local.gameLocal.GetMapName())
        shotName.StripFileExtension()
        shotName.plusAssign("/")
        shotName.plusAssign(spawnArgs.GetString("screenshot"))
        shotName.SetFileExtension(".tga")
        if (spawnArgs.GetString("camShot", "", camName)) {
            val ent = Game_local.gameLocal.FindEntity(camName[0]!!)
            if (ent != null && ent.cameraTarget != null) {
                val view = ent.cameraTarget!!.GetRenderView()
                val fullView = renderView_s(view!!)
                fullView.width = SCREEN_WIDTH
                fullView.height = SCREEN_HEIGHT
                // draw a view to a texture
                renderSystem.CropRenderSize(256, 256, true)
                gameRenderWorld!!.RenderScene(fullView)
                renderSystem.CaptureRenderToFile(shotName.toString())
                renderSystem.UnCrop()
            }
        }
    }

    override fun getEventCallBack(event: idEventDef): eventCallback_t<*>? {
        return eventCallbacks[event]
    }

    //
    //
    init {
        playerPos = idVec3()
    }
}

/*
 ===============================================================================

 idVideoCDItem

 ===============================================================================
 */
class idVideoCDItem : idItem() {
    //            public 	CLASS_PROTOTYPE( idVideoCDItem );
    override fun GiveToPlayer(player: idPlayer?): Boolean {
        val str = spawnArgs.GetString("video")
        if (player != null && str.length != 0) {
            player.GiveVideo(str, spawnArgs)
        }
        return true
    }
}

/*
 ===============================================================================

 idPDAItem

 ===============================================================================
 */
class idPDAItem : idItem() {
    //public 	CLASS_PROTOTYPE( idPDAItem );
    override fun GiveToPlayer(player: idPlayer?): Boolean {
        val str = idStr(spawnArgs.GetString("pda_name"))
        player?.GivePDA(str, spawnArgs)
        return true
    }
}

/*
 ===============================================================================

 idMoveableItem

 ===============================================================================
 */
open class idMoveableItem : idItem() {
    companion object {
        // public 	CLASS_PROTOTYPE( idMoveableItem );
        private val eventCallbacks: MutableMap<idEventDef, eventCallback_t<*>> = HashMap()

        /*
     ================
     idMoveableItem::DropItems

     The entity should have the following key/value pairs set:
     "def_drop<type>Item"			"item def"
     "drop<type>ItemJoint"			"joint name"
     "drop<type>ItemRotation"		"pitch yaw roll"
     "drop<type>ItemOffset"			"x y z"
     "skin_drop<type>"				"skin name"
     To drop multiple items the following key/value pairs can be used:
     "def_drop<type>Item<X>"			"item def"
     "drop<type>Item<X>Joint"		"joint name"
     "drop<type>Item<X>Rotation"		"pitch yaw roll"
     "drop<type>Item<X>Offset"		"x y z"
     where <X> is an aribtrary string.
     ================
     */
        fun DropItems(ent: idAnimatedEntity, type: String, list: List.idList<idEntity>?) {
            var kv: idKeyValue?
            val skinName: String?
            var c: String
            var jointName: String?
            var key: String
            var key2: String
            val origin = idVec3()
            val axis = idMat3()
            val angles = idAngles()
            val skin: idDeclSkin?
            var   /*jointHandle_t*/joint: Int
            var item: idEntity?
            var length: Int

            // drop all items
            kv = ent.spawnArgs.MatchPrefix(Str.va("def_drop%sItem", type), null)
            while (kv != null) {
                c = kv.GetKey().toString() // + kv.GetKey().Length();
                length = kv.GetKey().Length()
                if (idStr.Icmp(c.substring(length - 5), "Joint") != 0 && idStr.Icmp(
                        c.substring(
                            length - 8
                        ), "Rotation"
                    ) != 0
                ) {
                    key = kv.GetKey().toString().substring(4)
                    key2 = key
                    key += "Joint"
                    key2 += "Offset"
                    jointName = ent.spawnArgs.GetString(key)
                    joint = ent.GetAnimator().GetJointHandle(jointName)
                    if (!ent.GetJointWorldTransform(joint, Game_local.gameLocal.time, origin, axis)) {
                        Game_local.gameLocal.Warning(
                            "%s refers to invalid joint '%s' on entity '%s'\n",
                            key,
                            jointName,
                            ent.name
                        )
                        origin.set(ent.GetPhysics().GetOrigin())
                        axis.set(ent.GetPhysics().GetAxis())
                    }
                    if (!SysCvar.g_dropItemRotation.GetString().isNullOrEmpty()) {
                        angles.Zero()
                        val sscanf = Scanner(SysCvar.g_dropItemRotation.GetString())
                        sscanf.useLocale(Locale.US)
                        angles.pitch = sscanf.nextFloat()
                        angles.yaw = sscanf.nextFloat()
                        angles.roll = sscanf.nextFloat()
                    } else {
                        key = kv.GetKey().toString().substring(4)
                        key += "Rotation"
                        ent.spawnArgs.GetAngles(key, "0 0 0", angles)
                    }
                    axis.set(angles.ToMat3().times(axis))
                    origin.plusAssign(ent.spawnArgs.GetVector(key2, "0 0 0"))
                    item = DropItem(kv.GetValue().toString(), origin, axis, getVec3Origin(), 0, 0)
                    if (list != null && item != null) {
                        list.Append(item)
                    }
                }
                kv = ent.spawnArgs.MatchPrefix(Str.va("def_drop%sItem", type), kv)
            }

            // change the skin to hide all items
            skinName = ent.spawnArgs.GetString(Str.va("skin_drop%s", type))
            if (skinName.isNotEmpty()) {
                skin = DeclManager.declManager.FindSkin(skinName)
                ent.SetSkin(skin)
            }
        }

        fun DropItem(
            classname: String,
            origin: idVec3,
            axis: idMat3,
            velocity: idVec3,
            activateDelay: Int,
            removeDelay: Int
        ): idEntity? {
            var removeDelay = removeDelay
            val args = idDict()
            val item = arrayOfNulls<idEntity>(1)
            args.Set("classname", classname)
            args.Set("dropped", "1")

            // we sometimes drop idMoveables here, so set 'nodrop' to 1 so that it doesn't get put on the floor
            args.Set("nodrop", "1")
            if (activateDelay != 0) {
                args.SetBool("triggerFirst", true)
            }
            Game_local.gameLocal.SpawnEntityDef(args, item)
            if (item.isNotEmpty() && item[0] != null) {
                // set item position
                item[0]!!.GetPhysics().SetOrigin(origin)
                item[0]!!.GetPhysics().SetAxis(axis)
                item[0]!!.GetPhysics().SetLinearVelocity(velocity)
                item[0]!!.UpdateVisuals()
                if (activateDelay != 0) {
                    item[0]!!.PostEventMS(EV_Activate, activateDelay.toFloat(), item[0])
                }
                if (0 == removeDelay) {
                    removeDelay = 5 * 60 * 1000
                }
                // always remove a dropped item after 5 minutes in case it dropped to an unreachable location
                item[0]!!.PostEventMS(EV_Remove, removeDelay)
            }
            return item[0]
        }

        fun getEventCallBacks(): MutableMap<idEventDef, eventCallback_t<*>> {
            return eventCallbacks
        }

        init {
            eventCallbacks.putAll(idItem.getEventCallBacks())
            eventCallbacks[EV_DropToFloor] =
                eventCallback_t0<idMoveableItem> { obj: idMoveableItem -> obj.Event_DropToFloor() }
            eventCallbacks[EV_Gib] =
                eventCallback_t1<idMoveableItem> { obj: idMoveableItem, damageDefName: idEventArg<*>? ->
                    obj.Event_Gib(damageDefName as idEventArg<String>)
                }
        }
    }

    private val physicsObj: idPhysics_RigidBody
    private var smoke: idDeclParticle?
    private var smokeTime: Int
    private var trigger: idClipModel?

    // virtual					~idMoveableItem();
    override fun _deconstructor() {
        if (trigger != null) {
            idClipModel.delete(trigger!!)
        }
        super._deconstructor()
    }

    override fun Save(savefile: idSaveGame) {
        savefile.WriteStaticObject(physicsObj)
        savefile.WriteClipModel(trigger)
        savefile.WriteParticle(smoke)
        savefile.WriteInt(smokeTime)
    }

    override fun Restore(savefile: idRestoreGame) {
        savefile.ReadStaticObject(physicsObj)
        RestorePhysics(physicsObj)
        savefile.ReadClipModel(trigger!!)
        savefile.ReadParticle(smoke!!)
        smokeTime = savefile.ReadInt()
    }

    override fun Spawn() {
        super.Spawn()
        val trm = idTraceModel()
        val density = CFloat()
        val friction = CFloat()
        val bouncyness = CFloat()
        val tsize = CFloat()
        val clipModelName = idStr()

        // create a trigger for item pickup
        spawnArgs.GetFloat("triggersize", "16.0f", tsize)
        trigger = idClipModel(idTraceModel(idBounds(getVec3Origin()).Expand(tsize._val)))
        trigger!!.Link(Game_local.gameLocal.clip, this, 0, GetPhysics().GetOrigin(), GetPhysics().GetAxis())
        trigger!!.SetContents(Material.CONTENTS_TRIGGER)

        // check if a clip model is set
        spawnArgs.GetString("clipmodel", "", clipModelName)
        if (clipModelName.IsEmpty()) {
            clipModelName.set(spawnArgs.GetString("model")) // use the visual model
        }

        // load the trace model
        if (!collisionModelManager.TrmFromModel(clipModelName, trm)) {
            idGameLocal.Error("idMoveableItem '%s': cannot load collision model %s", name, clipModelName)
            return
        }

        // if the model should be shrinked
        if (spawnArgs.GetBool("clipshrink")) {
            trm.Shrink(CM_CLIP_EPSILON)
        }

        // get rigid body properties
        spawnArgs.GetFloat("density", "0.5f", density)
        density._val = (idMath.ClampFloat(0.001f, 1000.0f, density._val))
        spawnArgs.GetFloat("friction", "0.05", friction)
        friction._val = (idMath.ClampFloat(0.0f, 1.0f, friction._val))
        spawnArgs.GetFloat("bouncyness", "0.6", bouncyness)
        bouncyness._val = (idMath.ClampFloat(0.0f, 1.0f, bouncyness._val))

        // setup the physics
        physicsObj.SetSelf(this)
        physicsObj.SetClipModel(idClipModel(trm), density._val)
        physicsObj.SetOrigin(GetPhysics().GetOrigin())
        physicsObj.SetAxis(GetPhysics().GetAxis())
        physicsObj.SetBouncyness(bouncyness._val)
        physicsObj.SetFriction(0.6f, 0.6f, friction._val)
        physicsObj.SetGravity(Game_local.gameLocal.GetGravity())
        physicsObj.SetContents(Material.CONTENTS_RENDERMODEL)
        physicsObj.SetClipMask(Game_local.MASK_SOLID or Material.CONTENTS_MOVEABLECLIP)
        SetPhysics(physicsObj)
        smoke = null
        smokeTime = 0
        val smokeName = spawnArgs.GetString("smoke_trail")
        if (!smokeName.isEmpty()) { // != '\0' ) {
            smoke = DeclManager.declManager.FindType(declType_t.DECL_PARTICLE, smokeName) as idDeclParticle
            smokeTime = Game_local.gameLocal.time
            BecomeActive(TH_UPDATEPARTICLES)
        }
    }

    override fun Think() {
        RunPhysics()
        if ((thinkFlags and TH_PHYSICS) != 0) {
            // update trigger position
            trigger!!.Link(
                Game_local.gameLocal.clip,
                this,
                0,
                GetPhysics().GetOrigin(),
                idMat3.getMat3_identity()
            )
        }
        if ((thinkFlags and TH_UPDATEPARTICLES) != 0) {
            if (!Game_local.gameLocal.smokeParticles!!.EmitSmoke(
                    smoke,
                    smokeTime,
                    Game_local.gameLocal.random.CRandomFloat(),
                    GetPhysics().GetOrigin(),
                    GetPhysics().GetAxis()
                )
            ) {
                smokeTime = 0
                BecomeInactive(TH_UPDATEPARTICLES)
            }
        }
        Present()
    }

    override fun Pickup(player: idPlayer?): Boolean {
        val ret = super.Pickup(player)
        if (ret) {
            trigger!!.SetContents(0)
        }
        return ret
    }

    override fun WriteToSnapshot(msg: idBitMsgDelta) {
        physicsObj.WriteToSnapshot(msg)
    }

    override fun ReadFromSnapshot(msg: idBitMsgDelta) {
        physicsObj.ReadFromSnapshot(msg)
        if (msg.HasChanged()) {
            UpdateVisuals()
        }
    }

    private fun Gib(dir: idVec3, damageDefName: String) {
        // spawn smoke puff
        val smokeName = spawnArgs.GetString("smoke_gib")
        if (!smokeName.isEmpty()) { // != '\0' ) {
            val smoke = DeclManager.declManager.FindType(declType_t.DECL_PARTICLE, smokeName) as idDeclParticle
            Game_local.gameLocal.smokeParticles!!.EmitSmoke(
                smoke,
                Game_local.gameLocal.time,
                Game_local.gameLocal.random.CRandomFloat(),
                renderEntity!!.origin,
                renderEntity!!.axis
            )
        }
        // remove the entity
        PostEventMS(EV_Remove, 0)
    }

    private fun Event_DropToFloor() {
        // the physics will drop the moveable to the floor
    }

    private fun Event_Gib(damageDefName: idEventArg<String>) {
        Gib(idVec3(0, 0, 1), damageDefName.value)
    }

    override fun getEventCallBack(event: idEventDef): eventCallback_t<*>? {
        return eventCallbacks[event]
    }

    //
    //
    init {
        physicsObj = idPhysics_RigidBody()
        trigger = null
        smoke = null
        smokeTime = 0
    }
}

/*
 ===============================================================================

 idMoveablePDAItem

 ===============================================================================
 */
class idMoveablePDAItem : idMoveableItem() {
    //public 	CLASS_PROTOTYPE( idMoveablePDAItem );
    override fun GiveToPlayer(player: idPlayer?): Boolean {
        val str = idStr(spawnArgs.GetString("pda_name"))
        player?.GivePDA(str, spawnArgs)
        return true
    }
}

/*
 ===============================================================================

 Item removers.

 ===============================================================================
 */
/*
 ===============================================================================

 idItemRemover

 ===============================================================================
 */
open class idItemRemover : idEntity() {
    companion object {
        //public 	CLASS_PROTOTYPE( idItemRemover );
        private val eventCallbacks: MutableMap<idEventDef, eventCallback_t<*>> = HashMap()
        fun getEventCallBacks(): MutableMap<idEventDef, eventCallback_t<*>> {
            return eventCallbacks
        }

        init {
            eventCallbacks.putAll(idEntity.getEventCallBacks())
            eventCallbacks[EV_Activate] =
                eventCallback_t1<idItemRemover> { obj: idItemRemover, _activator: idEventArg<*>? ->
                    obj.Event_Trigger(_activator as idEventArg<idEntity>)
                }
        }
    }

    fun RemoveItem(player: idPlayer) {
        val remove: String?
        remove = spawnArgs.GetString("remove")
        player.RemoveInventoryItem(remove)
    }

    private fun Event_Trigger(_activator: idEventArg<idEntity>) {
        val activator = _activator.value
        if (activator is idPlayer) {
            RemoveItem(activator as idPlayer)
        }
    }

    override fun CreateInstance(): idClass {
        throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
    }

    override fun getEventCallBack(event: idEventDef): eventCallback_t<*>? {
        return eventCallbacks[event]
    }
}

/*
 ===============================================================================

 idObjectiveComplete

 ===============================================================================
 */
class idObjectiveComplete : idItemRemover() {
    companion object {
        // public 	CLASS_PROTOTYPE( idObjectiveComplete );
        private val eventCallbacks: MutableMap<idEventDef, eventCallback_t<*>> = HashMap()
        fun getEventCallBacks(): MutableMap<idEventDef, eventCallback_t<*>> {
            return eventCallbacks
        }

        init {
            eventCallbacks.putAll(idItemRemover.getEventCallBacks())
            eventCallbacks[EV_Activate] =
                eventCallback_t1<idObjectiveComplete> { obj: idObjectiveComplete, activator: idEventArg<*>? ->
                    obj.Event_Trigger(activator as idEventArg<idEntity>)
                }
            eventCallbacks[EV_HideObjective] =
                eventCallback_t1<idObjectiveComplete> { obj: idObjectiveComplete, e: idEventArg<*>? ->
                    obj.Event_HideObjective(e as idEventArg<idEntity>)
                }
            eventCallbacks[EV_GetPlayerPos] =
                eventCallback_t0<idObjectiveComplete> { obj: idObjectiveComplete -> obj.Event_GetPlayerPos() }
        }
    }

    private val playerPos: idVec3 = idVec3()
    override fun Save(savefile: idSaveGame) {
        savefile.WriteVec3(playerPos)
    }

    override fun Restore(savefile: idRestoreGame) {
        savefile.ReadVec3(playerPos)
    }

    override fun Spawn() {
        super.Spawn()
        spawnArgs.SetBool("objEnabled", false)
        Hide()
    }

    private fun Event_Trigger(activator: idEventArg<idEntity>) {
        if (!spawnArgs.GetBool("objEnabled")) {
            return
        }
        val player = Game_local.gameLocal.GetLocalPlayer()
        if (player != null) {
            RemoveItem(player)
            if (spawnArgs.GetString("inv_objective", null) != null) {
                if (player.hud != null) {
                    player.hud!!.SetStateString("objective", "2")
                    player.hud!!.SetStateString("objectivetext", spawnArgs.GetString("objectivetext"))
                    player.hud!!.SetStateString("objectivetitle", spawnArgs.GetString("objectivetitle"))
                    player.CompleteObjective(spawnArgs.GetString("objectivetitle"))
                    PostEventMS(EV_GetPlayerPos, 2000)
                }
            }
        }
    }

    private fun Event_HideObjective(e: idEventArg<idEntity>) {
        val player = Game_local.gameLocal.GetLocalPlayer()
        if (player != null) {
            val v = player.GetPhysics().GetOrigin() - playerPos
            if (v.Length() > 64.0f) {
                player.hud!!.HandleNamedEvent("closeObjective")
                PostEventMS(EV_Remove, 0)
            } else {
                PostEventMS(EV_HideObjective, 100.0f, player)
            }
        }
    }

    private fun Event_GetPlayerPos() {
        val player = Game_local.gameLocal.GetLocalPlayer()
        if (player != null) {
            playerPos.set(player.GetPhysics().GetOrigin())
            PostEventMS(EV_HideObjective, 100.0f, player)
        }
    }

    override fun getEventCallBack(event: idEventDef): eventCallback_t<*>? {
        return eventCallbacks[event]
    }

}

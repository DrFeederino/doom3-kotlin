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
import neo.Game.Game_local.Companion.gameLocal
import neo.Game.Game_local.Companion.gameRenderWorld
import neo.Game.Game_local.Companion.isD3XP
import neo.Game.Game_local.gameSoundChannel_t
import neo.Game.Game_local.idGameLocal
import neo.Game.Physics.Clip.idClipModel
import neo.Game.Physics.Physics_RigidBody.idPhysics_RigidBody
import neo.Game.Player.idPlayer
import neo.Game.Script.Script_Program.function_t
import neo.Renderer.Material
import neo.Renderer.RenderSystem.SCREEN_HEIGHT
import neo.Renderer.RenderSystem.SCREEN_WIDTH
import neo.Renderer.RenderSystem.renderSystem
import neo.Renderer.RenderWorld.deferredEntityCallback_t
import neo.Renderer.RenderWorld.renderEntity_s
import neo.Renderer.RenderWorld.renderLight_s
import neo.Renderer.RenderWorld.renderView_s
import neo.cm.CM_CLIP_EPSILON
import neo.cm.collisionModelManager
import neo.cm.trace_s
import neo.framework.CVarSystem
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
import neo.idlib.math.idAngles
import neo.idlib.math.idMath
import neo.idlib.math.idVec3
import neo.idlib.math.vec3_origin
import neo.idlib.toInt
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
        val Type = idTypeInfo("idItem", "idEntity") { idItem() }

        // enum {
        val EVENT_PICKUP: Int = idEntity.EVENT_MAXEVENTS
        val EVENT_RESPAWN = EVENT_PICKUP + 1
        val EVENT_RESPAWNFX = EVENT_PICKUP + 2

        // D3XP CTF flag events
        val EVENT_TAKEFLAG = EVENT_PICKUP + 3
        val EVENT_DROPFLAG = EVENT_PICKUP + 4
        val EVENT_FLAGRETURN = EVENT_PICKUP + 5
        val EVENT_FLAGCAPTURE = EVENT_PICKUP + 6
        val EVENT_MAXEVENTS = EVENT_PICKUP + 7

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
        super.Save(savefile)
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
        super.Restore(savefile)
        savefile.ReadVec3(orgOrigin)
        spin = savefile.ReadBool()
        pulse = savefile.ReadBool()
        canPickUp = savefile.ReadBool()
        shellMaterial = savefile.ReadMaterial()
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
                .LoadModel(idTraceModel(idBounds(vec3_origin).Expand(tsize._val)))
            GetPhysics().GetClipModel()!!.Link(gameLocal.clip)
        }
        if (spawnArgs.GetBool("start_off")) {
            GetPhysics().SetContents(0)
            Hide()
        } else {
            GetPhysics().SetContents(Material.CONTENTS_TRIGGER)
        }
        giveTo = spawnArgs.GetString("owner")
        if (giveTo.length != 0) {
            ent = gameLocal.FindEntity(giveTo)
            if (ent == null) {
                idGameLocal.Error("Item couldn't find owner '%s'", giveTo)
            }
            PostEventMS(EV_Touch, 0, ent, null)
        }
        if (spawnArgs.GetBool("spin") || gameLocal.isMultiplayer) {
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
        if (gameLocal.isServer) {
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
        if (gameLocal.isMultiplayer && respawn == 0.0f) {
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
                ang.yaw = (gameLocal.time and 4095) * 360.0f / -4096.0f
                SetAngles(ang)
                val scale = 0.005f + entityNumber * 0.00001f
                org.set(orgOrigin)
                org.z += (4.0f + cos(((gameLocal.time + 2000) * scale).toFloat()) * 4.0f)
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
        if (!gameLocal.isNewFrame) {
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
        msg.WriteBits((IsHidden()).toInt(), 1)
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

    override fun GetType(): idTypeInfo = Type
    override fun CreateInstance(): idClass = idItem()

    private fun Event_DropToFloor() {
        val trace = trace_s()

        // don't drop the floor if bound to another entity
        if (GetBindMaster() != null && GetBindMaster() != this) {
            return
        }
        gameLocal.clip.TraceBounds(
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
        if (gameLocal.isServer) {
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
        if (gameLocal.isServer) {
            ServerSendEvent(EVENT_RESPAWNFX, null, false, -1)
        }
        val sfx = spawnArgs.GetString("fxRespawn")
        if (sfx.isNotEmpty()) {
            idEntityFx.StartFx(sfx, null, null, this, true)
        }
    }

    override fun getEventCallBack(event: idEventDef): eventCallback_t<*>? {
        return eventCallbacks[event]
    }

    class ModelCallback private constructor() : deferredEntityCallback_t() {
        override fun run(e: renderEntity_s?, v: renderView_s?): Boolean {
            // this may be triggered by a model trace or other non-view related source
            if (null == v) {
                return false
            }
            val ent = gameLocal.entities[e!!.entityNum] as? idItem
            if (ent == null) {
                gameLocal.Warning("idItem::ModelCallback: callback with NULL game entity")
                return false
            }
            return ent.UpdateRenderEntity(e, v)
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
    companion object {
        val Type = idTypeInfo("idItemPowerup", "idItem") { idItemPowerup() }
    }

    override fun GetType(): idTypeInfo = Type
    override fun CreateInstance(): idClass = idItemPowerup()

    // public 	CLASS_PROTOTYPE( idItemPowerup );
    private val time: CInt = CInt()
    private val type: CInt = CInt()
    override fun Save(savefile: idSaveGame) {
        super.Save(savefile)
        savefile.WriteInt(time._val)
        savefile.WriteInt(type._val)
    }

    override fun Restore(savefile: idRestoreGame) {
        super.Restore(savefile)
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
        val Type = idTypeInfo("idObjective", "idItem") { idObjective() }

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
        super.Save(savefile)
        savefile.WriteVec3(playerPos)
    }

    override fun Restore(savefile: idRestoreGame) {
        super.Restore(savefile)
        savefile.ReadVec3(playerPos)
        if (!isD3XP) {
            PostEventMS(EV_CamShot, 250)
        }
    }

    override fun Spawn() {
        super.Spawn()
        Hide()
        if (!isD3XP || CVarSystem.cvarSystem.GetCVarBool("com_makingBuild")) {
            PostEventMS(EV_CamShot, 250)
        }
    }

    private fun Event_Trigger(activator: idEventArg<idEntity>) {
        val player = gameLocal.GetLocalPlayer()
        if (player != null) {

            //Pickup( player );
            if (spawnArgs.GetString("inv_objective", null) != null) {
                if ( /*player &&*/player.hud != null) {
                    val shotName = idStr(gameLocal.GetMapName())
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
                    for (i in 0 until gameLocal.num_entities) {
                        if (gameLocal.entities[i] != null && gameLocal.entities[i] is idObjectiveComplete) {
                            if (idStr.Icmp(
                                    spawnArgs.GetString("objectivetitle"),
                                    gameLocal.entities[i]!!.spawnArgs.GetString("objectivetitle")
                                ) == 0
                            ) {
                                gameLocal.entities[i]!!.spawnArgs.SetBool("objEnabled", true)
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
        val player = gameLocal.GetLocalPlayer()
        if (player != null) {
            val v = player.GetPhysics().GetOrigin() - playerPos
            if (v.Length() > 64.0f) {
                player.HideObjective()
                PostEventMS(EV_Remove, 0)
            } else {
                PostEventMS(EV_HideObjective, 100, player)
            }
        }
    }

    private fun Event_GetPlayerPos() {
        val player = gameLocal.GetLocalPlayer()
        if (player != null) {
            playerPos.set(player.GetPhysics().GetOrigin())
            PostEventMS(EV_HideObjective, 100, player)
        }
    }

    private fun Event_CamShot() {
        val camName = arrayOfNulls<String>(1)
        val shotName = idStr(gameLocal.GetMapName())
        shotName.StripFileExtension()
        shotName.plusAssign("/")
        shotName.plusAssign(spawnArgs.GetString("screenshot"))
        shotName.SetFileExtension(".tga")
        if (spawnArgs.GetString("camShot", "", camName)) {
            val ent = gameLocal.FindEntity(camName[0]!!)
            if (ent != null && ent.cameraTarget != null) {
                val view = ent.cameraTarget!!.GetRenderView()
                val fullView = renderView_s(view!!)
                fullView.width = SCREEN_WIDTH
                fullView.height = SCREEN_HEIGHT

                // D3XP: HACK - always draw sky-portal view if there is one in the map
                if (isD3XP && gameLocal.portalSkyEnt.GetEntity() != null
                    && SysCvar.g_enablePortalSky.GetBool()
                ) {
                    val portalView = renderView_s(fullView)
                    portalView.vieworg.set(
                        gameLocal.portalSkyEnt.GetEntity()!!.GetPhysics().GetOrigin()
                    )

                    // setup global fixup projection vars
                    var pot: Int
                    val w = fullView.width
                    pot = 1
                    while (pot < w) pot = pot shl 1
                    val shiftX = w.toFloat() / pot

                    val h = fullView.height
                    pot = 1
                    while (pot < h) pot = pot shl 1
                    val shiftY = h.toFloat() / pot

                    fullView.shaderParms[4] = shiftX
                    fullView.shaderParms[5] = shiftY

                    gameRenderWorld!!.RenderScene(portalView)
                    renderSystem.CaptureRenderToImage("_currentRender")
                }

                // draw a view to a texture
                renderSystem.CropRenderSize(256, 256, true)
                gameRenderWorld!!.RenderScene(fullView)
                renderSystem.CaptureRenderToFile(shotName.toString())
                renderSystem.UnCrop()
            }
        }
    }

    override fun GetType(): idTypeInfo = Type
    override fun CreateInstance(): idClass = idObjective()

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
    companion object {
        val Type = idTypeInfo("idVideoCDItem", "idItem") { idVideoCDItem() }
    }

    override fun GetType(): idTypeInfo = Type
    override fun CreateInstance(): idClass = idVideoCDItem()

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
    companion object {
        val Type = idTypeInfo("idPDAItem", "idItem") { idPDAItem() }
    }

    override fun GetType(): idTypeInfo = Type
    override fun CreateInstance(): idClass = idPDAItem()

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
        val Type = idTypeInfo("idMoveableItem", "idItem") { idMoveableItem() }

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

            // drop all items
            kv = ent.spawnArgs.MatchPrefix(Str.va("def_drop%sItem", type), null)
            while (kv != null) {
                c = kv.GetKey().toString() // + kv.GetKey().Length();
                // FIX: was using substring(length-5) / substring(length-8) which crashes on short keys
                if (!c.endsWith("Joint", ignoreCase = true) && !c.endsWith("Rotation", ignoreCase = true)) {
                    key = kv.GetKey().toString().substring(4)
                    key2 = key
                    key += "Joint"
                    key2 += "Offset"
                    jointName = ent.spawnArgs.GetString(key)
                    joint = ent.GetAnimator().GetJointHandle(jointName)
                    if (!ent.GetJointWorldTransform(joint, gameLocal.time, origin, axis)) {
                        gameLocal.Warning(
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
                    item = DropItem(kv.GetValue().toString(), origin, axis, vec3_origin, 0, 0)
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
            gameLocal.SpawnEntityDef(args, item)
            if (item.isNotEmpty() && item[0] != null) {
                // set item position
                item[0]!!.GetPhysics().SetOrigin(origin)
                item[0]!!.GetPhysics().SetAxis(axis)
                item[0]!!.GetPhysics().SetLinearVelocity(velocity)
                item[0]!!.UpdateVisuals()
                if (activateDelay != 0) {
                    item[0]!!.PostEventMS(EV_Activate, activateDelay, item[0])
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

    val physicsObj: idPhysics_RigidBody
    private var smoke: idDeclParticle?
    private var smokeTime: Int
    private var nextSoundTime: Int  // D3XP: rate-limit bounce sounds
    private var repeatSmoke: Boolean // CTF: repeat smoke trail
    var trigger: idClipModel?

    // virtual					~idMoveableItem();
    override fun _deconstructor() {
        if (trigger != null) {
            idClipModel.delete(trigger!!)
        }
        super._deconstructor()
    }

    override fun Save(savefile: idSaveGame) {
        super.Save(savefile)
        savefile.WriteStaticObject(physicsObj)
        savefile.WriteClipModel(trigger)
        savefile.WriteParticle(smoke)
        savefile.WriteInt(smokeTime)
        if (isD3XP) {
            savefile.WriteInt(nextSoundTime)
        }
    }

    override fun Restore(savefile: idRestoreGame) {
        super.Restore(savefile)
        savefile.ReadStaticObject(physicsObj)
        RestorePhysics(physicsObj)
        trigger = savefile.ReadClipModel()
        smoke = savefile.ReadParticle()
        smokeTime = savefile.ReadInt()
        nextSoundTime = if (isD3XP) savefile.ReadInt() else 0
    }

    override fun Spawn() {
        super.Spawn()
        val ts = if (isD3XP) SetTimeState(timeGroup) else null
        val trm = idTraceModel()
        val density = CFloat()
        val friction = CFloat()
        val bouncyness = CFloat()
        val tsize = CFloat()
        val clipModelName = idStr()

        // create a trigger for item pickup
        spawnArgs.GetFloat("triggersize", "16.0f", tsize)
        trigger = idClipModel(idTraceModel(idBounds(vec3_origin).Expand(tsize._val)))
        trigger!!.Link(gameLocal.clip, this, 0, GetPhysics().GetOrigin(), GetPhysics().GetAxis())
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
        physicsObj.SetGravity(gameLocal.GetGravity())
        physicsObj.SetContents(Material.CONTENTS_RENDERMODEL)
        physicsObj.SetClipMask(Game_local.MASK_SOLID or Material.CONTENTS_MOVEABLECLIP)
        SetPhysics(physicsObj)
        smoke = null
        smokeTime = 0
        nextSoundTime = 0
        val smokeName = spawnArgs.GetString("smoke_trail")
        if (!smokeName.isEmpty()) {
            smoke = DeclManager.declManager.FindType(declType_t.DECL_PARTICLE, smokeName) as idDeclParticle
            smokeTime = gameLocal.time
            BecomeActive(TH_UPDATEPARTICLES)
        }
        repeatSmoke = spawnArgs.GetBool("repeatSmoke", "false")
        ts?.close()
    }

    override fun Collide(collision: trace_s, velocity: idVec3): Boolean {
        if (!isD3XP) return false
        val v = -(velocity.times(collision.c.normal))
        if (v > 80f && gameLocal.time > nextSoundTime) {
            val f = if (v > 200f) 1.0f else idMath.Sqrt(v - 80f) * 0.091f
            if (StartSound("snd_bounce", gameSoundChannel_t.SND_CHANNEL_ANY, 0, false)) {
                SetSoundVolume(f)
            }
            nextSoundTime = gameLocal.time + 500
        }
        return false
    }

    override fun Think() {
        RunPhysics()
        if ((thinkFlags and TH_PHYSICS) != 0) {
            // update trigger position
            trigger!!.Link(
                gameLocal.clip,
                this,
                0,
                GetPhysics().GetOrigin(),
                idMat3.getMat3_identity()
            )
        }
        if ((thinkFlags and TH_UPDATEPARTICLES) != 0) {
            if (!gameLocal.smokeParticles!!.EmitSmoke(
                    smoke,
                    smokeTime,
                    gameLocal.random.CRandomFloat(),
                    GetPhysics().GetOrigin(),
                    GetPhysics().GetAxis()
                )
            ) {
                if (!repeatSmoke) {
                    smokeTime = 0
                    BecomeInactive(TH_UPDATEPARTICLES)
                } else {
                    smokeTime = gameLocal.time
                }
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
            gameLocal.smokeParticles!!.EmitSmoke(
                smoke,
                gameLocal.time,
                gameLocal.random.CRandomFloat(),
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

    override fun GetType(): idTypeInfo = Type
    override fun CreateInstance(): idClass = idMoveableItem()

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
        nextSoundTime = 0
        repeatSmoke = false
    }
}

/*
 ===============================================================================

 idMoveablePDAItem

 ===============================================================================
 */
class idMoveablePDAItem : idMoveableItem() {
    companion object {
        val Type = idTypeInfo("idMoveablePDAItem", "idMoveableItem") { idMoveablePDAItem() }
    }

    override fun GetType(): idTypeInfo = Type
    override fun CreateInstance(): idClass = idMoveablePDAItem()

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
        val Type = idTypeInfo("idItemRemover", "idEntity") { idItemRemover() }

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

    override fun GetType(): idTypeInfo = Type
    override fun CreateInstance(): idClass = idItemRemover()

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
        val Type = idTypeInfo("idObjectiveComplete", "idItemRemover") { idObjectiveComplete() }

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
        super.Save(savefile)
        savefile.WriteVec3(playerPos)
    }

    override fun Restore(savefile: idRestoreGame) {
        super.Restore(savefile)
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
        val player = gameLocal.GetLocalPlayer()
        if (player != null) {
            RemoveItem(player)
            if (spawnArgs.GetString("inv_objective", null) != null) {
                if (player.hud != null) {
                    player.hud!!.SetStateString("objective", "2")
                    player.hud!!.SetStateString("objectivetext", spawnArgs.GetString("objectivetext"))
                    if (isD3XP) {
                        player.hud!!.SetStateString("objectivecompletetitle", spawnArgs.GetString("objectivetitle"))
                    } else {
                        player.hud!!.SetStateString("objectivetitle", spawnArgs.GetString("objectivetitle"))
                    }
                    player.CompleteObjective(spawnArgs.GetString("objectivetitle"))
                    PostEventMS(EV_GetPlayerPos, 2000)
                }
            }
        }
    }

    private fun Event_HideObjective(e: idEventArg<idEntity>) {
        val player = gameLocal.GetLocalPlayer()
        if (player != null) {
            val v = player.GetPhysics().GetOrigin() - playerPos
            if (v.Length() > 64.0f) {
                player.hud!!.HandleNamedEvent("closeObjective")
                PostEventMS(EV_Remove, 0)
            } else {
                PostEventMS(EV_HideObjective, 100, player)
            }
        }
    }

    private fun Event_GetPlayerPos() {
        val player = gameLocal.GetLocalPlayer()
        if (player != null) {
            playerPos.set(player.GetPhysics().GetOrigin())
            PostEventMS(EV_HideObjective, 100, player)
        }
    }

    override fun GetType(): idTypeInfo = Type
    override fun CreateInstance(): idClass = idObjectiveComplete()

    override fun getEventCallBack(event: idEventDef): eventCallback_t<*>? {
        return eventCallbacks[event]
    }

}

/*
===============================================================================

  idItemTeam - CTF flag entity

===============================================================================
*/

// CTF flag events
val EV_FlagReturn = idEventDef("flagreturn", "e")
val EV_TakeFlag = idEventDef("takeflag", "e")
val EV_DropFlag = idEventDef("dropflag", "d")
val EV_FlagCapture = idEventDef("flagcapture")

class idItemTeam : idMoveableItem() {

    var team: Int = -1
    var carried: Boolean = false           // is it being carried by a player?
    var dropped: Boolean = false            // was it dropped?

    private var returnOrigin: idVec3 = idVec3()
    private var returnAxis: idMat3 = idMat3()
    private var lastDrop: Int = 0

    private var skinDefault: idDeclSkin? = null
    private var skinCarried: idDeclSkin? = null

    private var scriptTaken: function_t? = null
    private var scriptDropped: function_t? = null
    private var scriptReturned: function_t? = null
    private var scriptCaptured: function_t? = null

    private var itemGlow: renderLight_s = renderLight_s()
    private var itemGlowHandle: Int = -1

    private var lastNuggetDrop: Int = 0
    private var nuggetName: String? = null

    override fun Spawn() {
        team = spawnArgs.GetInt("team")
        returnOrigin.set(GetPhysics().GetOrigin().plus(idVec3(0f, 0f, 20f)))
        returnAxis.set(GetPhysics().GetAxis())

        BecomeActive(TH_THINK)

        var skinName = spawnArgs.GetString("skin", "")
        if (!skinName.isNullOrEmpty()) {
            skinDefault = DeclManager.declManager!!.FindSkin(skinName)
        }

        skinName = spawnArgs.GetString("skin_carried", "")
        if (!skinName.isNullOrEmpty()) {
            skinCarried = DeclManager.declManager!!.FindSkin(skinName)
        }

        val nugget = spawnArgs.GetString("nugget_name", "")
        nuggetName = if (!nugget.isNullOrEmpty()) nugget else null

        scriptTaken = LoadScript("script_taken")
        scriptDropped = LoadScript("script_dropped")
        scriptReturned = LoadScript("script_returned")
        scriptCaptured = LoadScript("script_captured")

        super.Spawn()

        physicsObj.SetContents(0)
        physicsObj.SetClipMask(Game_local.MASK_SOLID or Material.CONTENTS_MOVEABLECLIP)
        physicsObj.SetGravity(idVec3(0f, 0f, spawnArgs.GetInt("gravity", "-30").toFloat()))
    }

    override fun Think() {
        super.Think()

        TouchTriggers()

        // should only the server do this?
        if (gameLocal.isServer && nuggetName != null && carried
            && (lastNuggetDrop == 0 || (gameLocal.time - lastNuggetDrop) > spawnArgs.GetInt("nugget_frequency"))
        ) {
            SpawnNugget(GetPhysics().GetOrigin())
            lastNuggetDrop = gameLocal.time
        }

        // return dropped flag after si_flagDropTimeLimit seconds
        if (dropped && !carried && lastDrop != 0
            && (gameLocal.time - lastDrop) > (SysCvar.si_flagDropTimeLimit.GetInteger() * 1000)
        ) {
            Return()
            return
        }
    }

    override fun Pickup(player: idPlayer?): Boolean {
        if (player == null) return false
        if (!gameLocal.mpGame.IsGametypeFlagBased()) {
            return false
        }

        if (gameLocal.mpGame.GetGameState() == MultiplayerGame.idMultiplayerGame.gameState_t.WARMUP
            || gameLocal.mpGame.GetGameState() == MultiplayerGame.idMultiplayerGame.gameState_t.COUNTDOWN
        ) {
            return false
        }

        // wait after drop before being picked up again
        if (lastDrop != 0 && (gameLocal.time - lastDrop) < spawnArgs.GetInt("pickupDelay", "500")) {
            return false
        }

        if (!carried && player.team != this.team) {
            PostEventMS(EV_TakeFlag, 0, player)
            return true
        } else if (!carried && dropped && player.team == this.team) {
            gameLocal.mpGame.PlayerScoreCTF(player.entityNumber, 5)
            // return flag
            PostEventMS(EV_FlagReturn, 0, player)
            return false
        }

        return false
    }

    fun Drop(death: Boolean = false) {
        // had to remove the delayed drop because of drop flag on disconnect
        Event_DropFlag(death)
    }

    fun Return(player: idPlayer? = null) {
        if (team != 0 && team != 1) return
        Event_FlagReturn(player)
    }

    fun Capture() {
        if (team != 0 && team != 1) return
        PostEventMS(EV_FlagCapture, 0)
    }

    override fun FreeLightDef() {
        if (itemGlowHandle != -1) {
            gameRenderWorld!!.FreeLightDef(itemGlowHandle)
            itemGlowHandle = -1
        }
    }

    override fun Present() {
        // hide the flag for localplayer if in first person
        if (carried && GetBindMaster() != null) {
            val player = GetBindMaster() as? idPlayer
            if (player === gameLocal.GetLocalPlayer() && !SysCvar.pm_thirdPerson.GetBool()) {
                FreeModelDef()
                BecomeActive(TH_UPDATEVISUALS)
                return
            }
        }
        super.Present()
    }

    override fun WriteToSnapshot(msg: idBitMsgDelta) {
        msg.WriteBits(if (carried) 1 else 0, 1)
        msg.WriteBits(if (dropped) 1 else 0, 1)
        WriteBindToSnapshot(msg)
        super.WriteToSnapshot(msg)
    }

    override fun ReadFromSnapshot(msg: idBitMsgDelta) {
        carried = msg.ReadBits(1) == 1
        dropped = msg.ReadBits(1) == 1
        ReadBindFromSnapshot(msg)

        if (msg.HasChanged()) {
            UpdateGuis()
            SetSkin(if (carried) skinCarried else skinDefault)
        }
        super.ReadFromSnapshot(msg)
    }

    private fun PrivateReturn() {
        Unbind()

        if (gameLocal.isServer && carried && !dropped) {
            val playerIdx = gameLocal.mpGame.GetFlagCarrier(1 - team)
            if (playerIdx != -1) {
                val player = gameLocal.entities[playerIdx] as? idPlayer
                if (player != null) {
                    player.carryingFlag = false
                }
            } else {
                gameLocal.Warning("BUG: carried flag has no carrier before return")
            }
        }

        dropped = false
        carried = false

        SetOrigin(returnOrigin)
        SetAxis(returnAxis)

        // Re-link trigger
        trigger?.Link(gameLocal.clip, this, 0, GetPhysics().GetOrigin(), idMat3.getMat3_identity())

        SetSkin(skinDefault)

        GetPhysics().SetLinearVelocity(idVec3(0f, 0f, 0f))
        GetPhysics().SetAngularVelocity(idVec3(0f, 0f, 0f))
    }

    private fun LoadScript(script: String): function_t? {
        val funcname = spawnArgs.GetString(script, "")
        if (!funcname.isNullOrEmpty()) {
            val function = gameLocal.program.FindFunction(funcname)
            if (function == null) {
                gameLocal.Warning(
                    "idItemTeam '%s' at (%s) calls unknown function '%s'",
                    name, GetPhysics().GetOrigin().ToString(0), funcname
                )
            }
            return function
        }
        return null
    }

    private fun SpawnNugget(pos: idVec3) {
        val angle = idAngles(
            gameLocal.random.RandomInt(spawnArgs.GetInt("nugget_pitch", "30")).toFloat(),
            gameLocal.random.RandomInt(spawnArgs.GetInt("nugget_yaw", "360")).toFloat(),
            0f
        )
        var velocity = (gameLocal.random.RandomInt(40) + 15).toFloat()
        velocity *= spawnArgs.GetFloat("nugget_velocity", "1")

        val velVec = angle.ToMat3().times(idVec3(velocity, velocity, velocity))
        val ent = DropItem(
            nuggetName!!, pos, GetPhysics().GetAxis(),
            velVec, 0, spawnArgs.GetInt("nugget_removedelay")
        )
        if (ent != null) {
            val physics = ent.GetPhysics()
            if (physics is idPhysics_RigidBody) {
                physics.DisableImpact()
            }
        }
    }

    private fun UpdateGuis() {
        for (i in 0 until gameLocal.numClients) {
            val player = gameLocal.entities[i] as? idPlayer ?: continue
            val hud = player.hud ?: continue

            hud.SetStateInt("red_flagstatus", gameLocal.mpGame.GetFlagStatus(0).ordinal)
            hud.SetStateInt("blue_flagstatus", gameLocal.mpGame.GetFlagStatus(1).ordinal)
            hud.SetStateInt("red_team_score", gameLocal.mpGame.GetFlagPoints(0))
            hud.SetStateInt("blue_team_score", gameLocal.mpGame.GetFlagPoints(1))
        }
    }

    // Events
    private fun Event_TakeFlag(player: idPlayer) {
        gameLocal.DPrintf("Event_TakeFlag()!\n")

        if (gameLocal.isServer) {
            val msg = idBitMsg()
            val msgBuf = ByteBuffer.allocate(128) // MAX_EVENT_PARAM_SIZE
            msg.Init(msgBuf, msgBuf.capacity())
            msg.BeginWriting()
            msg.WriteBits(player.entityNumber, Game_local.GENTITYNUM_BITS)
            ServerSendEvent(EVENT_TAKEFLAG, msg, false, -1)

            gameLocal.mpGame.PlayTeamSound(player.team, MultiplayerGame.snd_evt_t.SND_FLAG_TAKEN_THEIRS)
            gameLocal.mpGame.PlayTeamSound(team, MultiplayerGame.snd_evt_t.SND_FLAG_TAKEN_YOURS)

            gameLocal.mpGame.PrintMessageEvent(
                -1,
                MultiplayerGame.idMultiplayerGame.msg_evt_t.MSG_FLAGTAKEN,
                team,
                player.entityNumber
            )

            // dont drop a nugget RIGHT away
            lastNuggetDrop = gameLocal.time - gameLocal.random.RandomInt(1000)
        }

        BindToJoint(player, SysCvar.g_flagAttachJoint.GetString()!!, true)
        val origin = idVec3(
            SysCvar.g_flagAttachOffsetX.GetFloat(),
            SysCvar.g_flagAttachOffsetY.GetFloat(),
            SysCvar.g_flagAttachOffsetZ.GetFloat()
        )
        val angle = idAngles(
            SysCvar.g_flagAttachAngleX.GetFloat(),
            SysCvar.g_flagAttachAngleY.GetFloat(),
            SysCvar.g_flagAttachAngleZ.GetFloat()
        )
        SetAngles(angle)
        SetOrigin(origin)

        if (scriptTaken != null) {
            val thread = neo.Game.Script.Script_Thread.idThread()
            thread.CallFunction(scriptTaken!!, false)
            thread.DelayedStart(0)
        }

        dropped = false
        carried = true
        player.carryingFlag = true

        SetSkin(skinCarried)

        UpdateVisuals()
        UpdateGuis()

        if (gameLocal.isServer) {
            if (team == 0) {
                gameLocal.mpGame.player_red_flag = player.entityNumber
            } else {
                gameLocal.mpGame.player_blue_flag = player.entityNumber
            }
        }
    }

    private fun Event_DropFlag(death: Boolean) {
        gameLocal.DPrintf("Event_DropFlag()!\n")

        if (gameLocal.isServer) {
            val msg = idBitMsg()
            val msgBuf = ByteBuffer.allocate(128) // MAX_EVENT_PARAM_SIZE
            msg.Init(msgBuf, msgBuf.capacity())
            msg.BeginWriting()
            msg.WriteBits(if (death) 1 else 0, 1)
            ServerSendEvent(EVENT_DROPFLAG, msg, false, -1)

            if (gameLocal.mpGame.IsFlagMsgOn()) {
                gameLocal.mpGame.PlayTeamSound(1 - team, MultiplayerGame.snd_evt_t.SND_FLAG_DROPPED_THEIRS)
                gameLocal.mpGame.PlayTeamSound(team, MultiplayerGame.snd_evt_t.SND_FLAG_DROPPED_YOURS)
                gameLocal.mpGame.PrintMessageEvent(-1, MultiplayerGame.idMultiplayerGame.msg_evt_t.MSG_FLAGDROP, team)
            }
        }

        lastDrop = gameLocal.time

        BecomeActive(TH_THINK)
        Show()

        if (death) {
            GetPhysics().SetLinearVelocity(idVec3(0f, 0f, 0f))
        } else {
            GetPhysics().SetLinearVelocity(idVec3(0f, 0f, 20f))
        }
        GetPhysics().SetAngularVelocity(idVec3(0f, 0f, 0f))

        if (GetBindMaster() != null) {
            val bounds = GetPhysics().GetBounds()
            val origin = GetBindMaster()!!.GetPhysics().GetOrigin()
                .plus(idVec3(0f, 0f, (bounds[1].z - bounds[0].z) * 0.6f))
            Unbind()
            SetOrigin(origin)
        }

        val angle = GetPhysics().GetAxis().ToAngles()
        angle.roll = 0f
        angle.pitch = 0f
        SetAxis(angle.ToMat3())

        dropped = true
        carried = false

        if (scriptDropped != null) {
            val thread = neo.Game.Script.Script_Thread.idThread()
            thread.CallFunction(scriptDropped!!, false)
            thread.DelayedStart(0)
        }

        SetSkin(skinDefault)
        UpdateVisuals()
        UpdateGuis()

        if (gameLocal.isServer) {
            if (team == 0) {
                gameLocal.mpGame.player_red_flag = -1
            } else {
                gameLocal.mpGame.player_blue_flag = -1
            }
        }
    }

    private fun Event_FlagReturn(player: idPlayer? = null) {
        gameLocal.DPrintf("Event_FlagReturn()!\n")

        if (gameLocal.isServer) {
            ServerSendEvent(EVENT_FLAGRETURN, null, false, -1)

            if (gameLocal.mpGame.IsFlagMsgOn()) {
                gameLocal.mpGame.PlayTeamSound(1 - team, MultiplayerGame.snd_evt_t.SND_FLAG_RETURN)
                gameLocal.mpGame.PlayTeamSound(team, MultiplayerGame.snd_evt_t.SND_FLAG_RETURN)

                val entitynum = player?.entityNumber ?: 255
                gameLocal.mpGame.PrintMessageEvent(
                    -1,
                    MultiplayerGame.idMultiplayerGame.msg_evt_t.MSG_FLAGRETURN,
                    team,
                    entitynum
                )
            }
        }

        BecomeActive(TH_THINK)
        Show()

        PrivateReturn()

        if (scriptReturned != null) {
            val thread = neo.Game.Script.Script_Thread.idThread()
            thread.CallFunction(scriptReturned!!, false)
            thread.DelayedStart(0)
        }

        UpdateVisuals()
        UpdateGuis()

        if (gameLocal.isServer) {
            if (team == 0) {
                gameLocal.mpGame.player_red_flag = -1
            } else {
                gameLocal.mpGame.player_blue_flag = -1
            }
        }
    }

    private fun Event_FlagCapture() {
        gameLocal.DPrintf("Event_FlagCapture()!\n")

        if (gameLocal.isServer) {
            ServerSendEvent(EVENT_FLAGCAPTURE, null, false, -1)

            gameLocal.mpGame.PlayTeamSound(1 - team, MultiplayerGame.snd_evt_t.SND_FLAG_CAPTURED_THEIRS)
            gameLocal.mpGame.PlayTeamSound(team, MultiplayerGame.snd_evt_t.SND_FLAG_CAPTURED_YOURS)

            gameLocal.mpGame.TeamScoreCTF(1 - team, 1)

            val playerIdx = gameLocal.mpGame.GetFlagCarrier(1 - team)
            if (playerIdx != -1) {
                gameLocal.mpGame.PlayerScoreCTF(playerIdx, 10)
            }

            gameLocal.mpGame.PrintMessageEvent(
                -1, MultiplayerGame.idMultiplayerGame.msg_evt_t.MSG_FLAGCAPTURE, team,
                if (playerIdx != -1) playerIdx else 255
            )
        }

        BecomeActive(TH_THINK)
        Show()

        PrivateReturn()

        if (scriptCaptured != null) {
            val thread = neo.Game.Script.Script_Thread.idThread()
            thread.CallFunction(scriptCaptured!!, false)
            thread.DelayedStart(0)
        }

        UpdateVisuals()
        UpdateGuis()

        if (gameLocal.isServer) {
            if (team == 0) {
                gameLocal.mpGame.player_red_flag = -1
            } else {
                gameLocal.mpGame.player_blue_flag = -1
            }
        }
    }

    override fun ClientReceiveEvent(event: Int, time: Int, msg: idBitMsg): Boolean {
        gameLocal.DPrintf("ClientReceiveEvent: %d\n", event)
        return when (event) {
            EVENT_TAKEFLAG -> {
                val player = gameLocal.entities[msg.ReadBits(Game_local.GENTITYNUM_BITS)] as? idPlayer
                if (player == null) {
                    gameLocal.Warning("NULL player takes flag?\n")
                    false
                } else {
                    Event_TakeFlag(player)
                    true
                }
            }

            EVENT_DROPFLAG -> {
                val death = msg.ReadBits(1) == 1
                Event_DropFlag(death)
                true
            }

            EVENT_FLAGRETURN -> {
                Hide()
                FreeModelDef()
                FreeLightDef()
                Event_FlagReturn()
                true
            }

            EVENT_FLAGCAPTURE -> {
                Hide()
                FreeModelDef()
                FreeLightDef()
                Event_FlagCapture()
                true
            }

            else -> false
        }
    }

    override fun _deconstructor() {
        FreeLightDef()
        super._deconstructor()
    }

    override fun getEventCallBack(event: idEventDef): eventCallback_t<*>? {
        return eventCallbacks[event]
    }

    override fun GetType(): idTypeInfo = Type
    override fun CreateInstance(): idClass = idItemTeam()

    companion object {
        val Type: idTypeInfo = idTypeInfo(
            "idItemTeam",
            "idMoveableItem"
        ) { idItemTeam() }

        private val eventCallbacks: MutableMap<idEventDef, eventCallback_t<*>> = buildEventCallbacks()

        private fun buildEventCallbacks(): MutableMap<idEventDef, eventCallback_t<*>> {
            val callbacks = idMoveableItem.getEventCallBacks().toMutableMap()
            callbacks[EV_FlagReturn] = eventCallback_t1<idItemTeam> { obj, player ->
                obj.Event_FlagReturn(player as? idPlayer)
            }
            callbacks[EV_TakeFlag] = eventCallback_t1<idItemTeam> { obj, player ->
                obj.Event_TakeFlag(player as idPlayer)
            }
            callbacks[EV_DropFlag] = eventCallback_t1<idItemTeam> { obj, death ->
                obj.Event_DropFlag((death as Number).toInt() != 0)
            }
            callbacks[EV_FlagCapture] = eventCallback_t0<idItemTeam> { obj ->
                obj.Event_FlagCapture()
            }
            return callbacks
        }

        fun getEventCallBacks(): Map<idEventDef, eventCallback_t<*>> = eventCallbacks
    }
}

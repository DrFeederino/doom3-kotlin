/*
 * Copyright (C) 1999-2011 id Software LLC, a ZeniMax Media company.
 * Translated to Kotlin by Dr. Feederino with support of Claude Code
 *
 * This file is part of the Doom 3 Kotlin project.
 * Original source: neo/Game/Misc.cpp, neo/Game/Misc.h
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

import neo.Game.AI.AI_RandomPath
import neo.Game.AI.idAI
import neo.Game.Animation.Anim
import neo.Game.GameSys.Class.*
import neo.Game.GameSys.EV_Remove
import neo.Game.GameSys.Event.idEventDef
import neo.Game.GameSys.SaveGame
import neo.Game.GameSys.SaveGame.idRestoreGame
import neo.Game.GameSys.SaveGame.idSaveGame
import neo.Game.GameSys.SysCvar
import neo.Game.Game_local.*
import neo.Game.Game_local.Companion.MAX_GENTITIES
import neo.Game.Game_local.Companion.gameLocal
import neo.Game.Game_local.Companion.gameRenderWorld
import neo.Game.Game_local.Companion.isD3XP
import neo.Game.Moveable.idMoveable
import neo.Game.Physics.Clip.idClipModel
import neo.Game.Physics.Force_Field.forceFieldApplyType
import neo.Game.Physics.Force_Field.idForce_Field
import neo.Game.Physics.Force_Spring.idForce_Spring
import neo.Game.Physics.Physics.idPhysics
import neo.Game.Physics.Physics_Parametric.idPhysics_Parametric
import neo.Game.Player.idPlayer
import neo.Game.Projectile.idProjectile
import neo.Game.Script.Script_Program
import neo.Game.Script.Script_Thread.idThread
import neo.Game.Sound.SSF_GLOBAL
import neo.Renderer.Material
import neo.Renderer.Model
import neo.Renderer.ModelManager
import neo.Renderer.Model_liquid.idRenderModelLiquid
import neo.Renderer.RenderWorld
import neo.Renderer.RenderWorld.portalConnection_t
import neo.Sound.snd_shader.idSoundShader
import neo.TempDump
import neo.Tools.Compilers.AAS.AASFile
import neo.cm.trace_s
import neo.framework.Common
import neo.framework.DeclManager
import neo.framework.DeclManager.Companion.declManager
import neo.framework.DeclManager.declType_t
import neo.framework.DeclParticle.idDeclParticle
import neo.framework.UsercmdGen.BUTTON_ATTACK
import neo.idlib.BV.idBounds
import neo.idlib.BitMsg.idBitMsg
import neo.idlib.BitMsg.idBitMsgDelta
import neo.idlib.Dict_h.idDict
import neo.idlib.Text.Str
import neo.idlib.Text.Str.idStr
import neo.idlib.colorBlue
import neo.idlib.colorRed
import neo.idlib.colorWhite
import neo.idlib.containers.CBool
import neo.idlib.containers.CFloat
import neo.idlib.containers.CInt
import neo.idlib.containers.List
import neo.idlib.math.*
import neo.idlib.math.Matrix.idMat3
import neo.idlib.math.idMath.AngleNormalize180
import java.nio.ByteBuffer

val EV_AnimDone: idEventDef = idEventDef("<AnimDone>", "d")
val EV_Animated_Start: idEventDef = idEventDef("<start>")
val EV_LaunchMissiles: idEventDef = idEventDef("launchMissiles", "ssssdf")
val EV_LaunchMissilesUpdate: idEventDef = idEventDef("<launchMissiles>", "dddd")
val EV_ResetRadioHud: idEventDef = idEventDef("<resetradiohud>", "e")
val EV_RestoreDamagable: idEventDef = idEventDef("<RestoreDamagable>")
val EV_SetAnimation: idEventDef = idEventDef("setAnimation", "s") // D3XP
val EV_GetAnimationLength: idEventDef = idEventDef("getAnimationLength", null, 'f') // D3XP
val EV_Splat: idEventDef = idEventDef("<Splat>")
val EV_StartRagdoll: idEventDef = idEventDef("startRagdoll")
val EV_TeleportStage: idEventDef = idEventDef("<TeleportStage>", "e")
val EV_Toggle: idEventDef = idEventDef("Toggle", null)

object Misc {

    /*
     ===============================================================================

     idAnimated

     ===============================================================================
     */

    /*
     ===============================================================================

     idFuncRadioChatter

     ===============================================================================
     */

    /*
     ===============================================================================

     Object that fires targets and changes shader parms when damaged.

     ===============================================================================
     */

    /*
     ===============================================================================

     idDamagable
	
     ===============================================================================
     *//*
     ===============================================================================

     idFuncSplat

     ===============================================================================
     */

    /*
     ===============================================================================

     Potential spawning position for players.
     The first time a player enters the game, they will be at an 'initial' spot.
     Targets will be fired when someone spawns in on them.

     When triggered, will cause player to be teleported to spawn spot.

     ===============================================================================
     */

    /*
     ===============================================================================

     idForceField

     ===============================================================================
     */

    /*
     ===============================================================================

     idSpawnableEntity

     A simple, spawnable entity with a model and no functionable ability of it's own.
     For example, it can be used as a placeholder during development, for marking
     locations on maps for script, or for simple placed models without any behavior
     that can be bound to other entities.  Should not be subclassed.
     ===============================================================================
     */
    class idSpawnableEntity : idEntity() {
        companion object {
            val Type = idTypeInfo("idSpawnableEntity", "idEntity") { idSpawnableEntity() }
        }

        override fun CreateInstance(): idClass = idSpawnableEntity()

        override fun GetType(): idTypeInfo = Type
    }

    /*
     ===============================================================================

     idPlayerStart

     ===============================================================================
     */
    class idPlayerStart     //
    //
        : idEntity() {
        companion object {
            val Type = idTypeInfo("idPlayerStart", "idEntity") { idPlayerStart() }

            // enum {
            val EVENT_TELEPORTPLAYER: Int = idEntity.EVENT_MAXEVENTS
            val EVENT_MAXEVENTS = EVENT_TELEPORTPLAYER + 1

            // public 	CLASS_PROTOTYPE( idPlayerStart );
            private val eventCallbacks: MutableMap<idEventDef, eventCallback_t<*>> = HashMap()
            private fun Event_TeleportPlayer(p: idPlayerStart, activator: idEventArg<idEntity?>) {
                val player: idPlayer?
                player = if (activator.value is idPlayer) {
                    activator.value as idPlayer?
                } else {
                    gameLocal.GetLocalPlayer()
                }
                if (player != null) {
                    if (p.spawnArgs.GetBool("visualFx")) {
                        p.teleportStage = 0
                        p.Event_TeleportStage(player)
                    } else {
                        if (gameLocal.isServer) {
                            val msg = idBitMsg()
                            val msgBuf = ByteBuffer.allocate(Game_local.MAX_EVENT_PARAM_SIZE)
                            msg.Init(msgBuf, Game_local.MAX_EVENT_PARAM_SIZE)
                            msg.BeginWriting()
                            msg.WriteBits(player.entityNumber, Game_local.GENTITYNUM_BITS)
                            p.ServerSendEvent(EVENT_TELEPORTPLAYER, msg, false, -1)
                        }
                        p.TeleportPlayer(player)
                    }
                }
            }

            /*
         ===============
         p.Event_TeleportStage

         FIXME: add functionality to fx system ( could be done with player scripting too )
         ================
         */
            private fun Event_TeleportStage(p: idPlayerStart, _player: idEventArg<idEntity?>) {
                val player: idPlayer?
                if (_player.value !is idPlayer) {
                    Common.common.Warning("p.Event_TeleportStage: entity is not an idPlayer\n")
                    return
                }
                player = _player.value as idPlayer
                val teleportDelay = p.spawnArgs.GetFloat("teleportDelay")
                when (p.teleportStage) {
                    0 -> {
                        player.playerView.Flash(colorWhite, 125)
                        player.SetInfluenceLevel(Player.INFLUENCE_LEVEL3)
                        player.SetInfluenceView(p.spawnArgs.GetString("mtr_teleportFx"), null, 0.0f, null)
                        Game_local.gameSoundWorld!!.FadeSoundClasses(0, -20.0f, teleportDelay)
                        player.StartSound("snd_teleport_start", gameSoundChannel_t.SND_CHANNEL_BODY2, 0, false)
                        p.teleportStage++
                        p.PostEventSec(EV_TeleportStage, teleportDelay, player)
                    }

                    1 -> {
                        Game_local.gameSoundWorld!!.FadeSoundClasses(0, 0.0f, 0.25f)
                        p.teleportStage++
                        p.PostEventSec(EV_TeleportStage, 0.25f, player)
                    }

                    2 -> {
                        player.SetInfluenceView(null, null, 0.0f, null)
                        p.TeleportPlayer(player)
                        player.StopSound(TempDump.etoi(gameSoundChannel_t.SND_CHANNEL_BODY2), false)
                        player.SetInfluenceLevel(Player.INFLUENCE_NONE)
                        p.teleportStage = 0
                    }

                    else -> {}
                }
            }

            fun getEventCallBacks(): MutableMap<idEventDef, eventCallback_t<*>> {
                return eventCallbacks
            }

            init {
                eventCallbacks.putAll(idEntity.getEventCallBacks())
                eventCallbacks[EV_Activate] =
                    eventCallback_t1<idPlayerStart> { p: idPlayerStart, activator: idEventArg<*>? ->
                        Event_TeleportPlayer(p, activator as idEventArg<idEntity?>)
                    }
                eventCallbacks[EV_TeleportStage] =
                    eventCallback_t1<idPlayerStart> { p: idPlayerStart, _player: idEventArg<*>? ->
                        Event_TeleportStage(p, _player as idEventArg<idEntity?>)
                    }
            }
        }

        // };
        private var teleportStage = 0
        override fun Spawn() {
            super.Spawn()
            teleportStage = 0
        }

        override fun Save(savefile: idSaveGame) {
            super.Save(savefile)
            savefile.WriteInt(teleportStage)
        }

        override fun Restore(savefile: idRestoreGame) {
            super.Restore(savefile)
            val teleportStage = CInt()
            savefile.ReadInt(teleportStage)
            this.teleportStage = teleportStage._val
        }

        override fun ClientReceiveEvent(event: Int, time: Int, msg: idBitMsg): Boolean {
            val entityNumber: Int
            return when (event) {
                EVENT_TELEPORTPLAYER -> {
                    entityNumber = msg.ReadBits(Game_local.GENTITYNUM_BITS)
                    val player = gameLocal.entities[entityNumber] as idPlayer
                    if (player != null && player is idPlayer) {
                        Event_TeleportPlayer(player)
                    }
                    true
                }

                else -> {
                    super.ClientReceiveEvent(event, time, msg)
                }
            }
            //            return false;
        }

        private fun Event_TeleportPlayer(activator: idEntity?) {
            Event_TeleportPlayer(this, idEventArg.toArg(activator))
        }

        private fun Event_TeleportStage(_player: idEntity?) {
            Event_TeleportStage(this, idEventArg.toArg(_player))
        }

        private fun TeleportPlayer(player: idPlayer) {
            val pushVel = spawnArgs.GetFloat("push", "300")
            val f = spawnArgs.GetFloat("visualEffect", "0")
            val viewName = spawnArgs.GetString("visualView", "")
            val ent =
                if (!viewName.isNullOrEmpty()) gameLocal.FindEntity(viewName) else null //TODO:the standard C++ boolean checks if the bytes are switched on, which in the case of String means NOT NULL AND NOT EMPTY.
            val ts = if (isD3XP) SetTimeState(player.timeGroup) else null
            if (f != 0.0f && ent != null) {
                // place in private camera view for some time
                // the entity needs to teleport to where the camera view is to have the PVS right
                player.Teleport(ent.GetPhysics().GetOrigin(), ang_zero, this)
                player.StartSound("snd_teleport_enter", gameSoundChannel_t.SND_CHANNEL_ANY, 0, false)
                player.SetPrivateCameraView(ent as idCamera?)
                // the player entity knows where to spawn from the previous Teleport call
                if (!gameLocal.isClient) {
                    player.PostEventSec(EV_Player_ExitTeleporter, f)
                }
            } else {
                // direct to exit, Teleport will take care of the killbox
                player.Teleport(GetPhysics().GetOrigin(), GetPhysics().GetAxis().ToAngles(), null)

                // multiplayer hijacked this entity, so only push the player in multiplayer
                if (gameLocal.isMultiplayer) {
                    player.GetPhysics().SetLinearVelocity(GetPhysics().GetAxis()[0].times(pushVel))
                }
            }
            ts?.close()
        }

        override fun CreateInstance(): idClass = idPlayerStart()

        override fun GetType(): idTypeInfo = Type

        override fun getEventCallBack(event: idEventDef): eventCallback_t<*>? {
            return eventCallbacks[event]
        }
    }

    /*
     ===============================================================================

     Non-displayed entity used to activate triggers when it touches them.
     Bind to a mover to have the mover activate a trigger as it moves.
     When target by triggers, activating the trigger will toggle the
     activator on and off. Check "start_off" to have it spawn disabled.

     ===============================================================================
     */
    class idActivator : idEntity() {
        companion object {
            val Type = idTypeInfo("idActivator", "idEntity") { idActivator() }

            // public 	CLASS_PROTOTYPE( idActivator );
            private val eventCallbacks: MutableMap<idEventDef, eventCallback_t<*>> = HashMap()

            //
            //
            private fun Event_Activate(a: idActivator, activator: idEventArg<idEntity>) {
                if ((a.thinkFlags and TH_THINK) != 0) {
                    a.BecomeInactive(TH_THINK)
                } else {
                    a.BecomeActive(TH_THINK)
                }
            }

            fun getEventCallBacks(): MutableMap<idEventDef, eventCallback_t<*>> {
                return eventCallbacks
            }

            init {
                eventCallbacks.putAll(idEntity.getEventCallBacks())
                eventCallbacks[EV_Activate] =
                    eventCallback_t1<idActivator> { a: idActivator, activator: idEventArg<*>? ->
                        Event_Activate(a as idActivator, activator as idEventArg<idEntity>)
                    }
            }
        }

        private val stay_on: CBool = CBool(false)
        override fun Spawn() {
            super.Spawn()
            val start_off = CBool(false)
            spawnArgs.GetBool("stay_on", "0", stay_on)
            spawnArgs.GetBool("start_off", "0", start_off)
            GetPhysics().SetClipBox(idBounds(vec3_origin).Expand(4.0f), 1.0f)
            GetPhysics().SetContents(0)
            if (!start_off._val) {
                BecomeActive(TH_THINK)
            }
        }

        override fun Save(savefile: idSaveGame) {
            super.Save(savefile)
            savefile.WriteBool(stay_on._val)
        }

        override fun Restore(savefile: idRestoreGame) {
            super.Restore(savefile)
            savefile.ReadBool(stay_on)
            if (stay_on._val) {
                BecomeActive(TH_THINK)
            }
        }

        override fun Think() {
            RunPhysics()
            if ((thinkFlags and TH_THINK) != 0) {
                if (TouchTriggers()) {
                    if (!stay_on._val) {
                        BecomeInactive(TH_THINK)
                    }
                }
            }
            Present()
        }

        override fun CreateInstance(): idClass = idActivator()

        override fun GetType(): idTypeInfo = Type

        override fun getEventCallBack(event: idEventDef): eventCallback_t<*>? {
            return eventCallbacks[event]
        }
    }

    /*
     ===============================================================================

     Path entities for monsters to follow.

     ===============================================================================
     *//*
     ===============================================================================

     idPathCorner

     ===============================================================================
     */
    class idPathCorner : idEntity() {
        companion object {
            val Type = idTypeInfo("idPathCorner", "idEntity") { idPathCorner() }

            // public 	CLASS_PROTOTYPE( idPathCorner );
            private val eventCallbacks: MutableMap<idEventDef, eventCallback_t<*>> = HashMap()
            fun DrawDebugInfo() {
                var ent: idEntity?
                val bnds = idBounds(idVec3(-4.0f, -4.0f, -8.0f), idVec3(4.0f, 4.0f, 64.0f))
                ent = gameLocal.spawnedEntities.Next()
                while (ent != null) {
                    if (ent !is idPathCorner) {
                        ent = ent.spawnNode.Next()
                        continue
                    }
                    val org = idVec3(ent.GetPhysics().GetOrigin())
                    gameRenderWorld!!.DebugBounds(colorRed, bnds, org, 0)
                    ent = ent.spawnNode.Next()
                }
            }

            fun RandomPath(source: idEntity, ignore: idEntity?): idPathCorner? {
                var i: Int
                var num: Int
                val which: Int
                var ent: idEntity?
                val path = arrayOfNulls<idPathCorner?>(MAX_GENTITIES)
                num = 0
                i = 0
                while (i < source.targets.Num()) {
                    ent = source.targets[i].GetEntity()
                    if (ent != null && ent !== ignore && ent is idPathCorner) {
                        path[num++] = ent as idPathCorner?
                        if (num >= MAX_GENTITIES) {
                            break
                        }
                    }
                    i++
                }
                if (0 == num) {
                    return null
                }
                which = gameLocal.random.RandomInt(num)
                return path[which]
            }

            fun getEventCallBacks(): MutableMap<idEventDef, eventCallback_t<*>> {
                return eventCallbacks
            }

            init {
                eventCallbacks.putAll(idEntity.getEventCallBacks())
                eventCallbacks[AI_RandomPath] =
                    eventCallback_t0<idPathCorner> { obj: idPathCorner -> obj.Event_RandomPath() }
            }
        }

        private fun Event_RandomPath() {
            val path: idPathCorner?
            path = RandomPath(this, null)
            idThread.ReturnEntity(path)
        }

        override fun CreateInstance(): idClass = idPathCorner()

        override fun GetType(): idTypeInfo = Type

        override fun getEventCallBack(event: idEventDef): eventCallback_t<*>? {
            return eventCallbacks[event]
        }
    }

    class idDamagable : idEntity() {
        companion object {
            val Type = idTypeInfo("idDamagable", "idEntity") { idDamagable() }

            // CLASS_PROTOTYPE( idDamagable );
            private val eventCallbacks: MutableMap<idEventDef, eventCallback_t<*>> = HashMap()
            private fun Event_BecomeBroken(d: idDamagable, activator: idEventArg<idEntity>) {
                d.BecomeBroken(activator.value)
            }

            fun getEventCallBacks(): MutableMap<idEventDef, eventCallback_t<*>> {
                return eventCallbacks
            }

            init {
                eventCallbacks.putAll(idEntity.getEventCallBacks())
                eventCallbacks[EV_Activate] =
                    eventCallback_t1<idDamagable> { d: idDamagable, activator: idEventArg<*>? ->
                        Event_BecomeBroken(d, activator as idEventArg<idEntity>)
                    }
                eventCallbacks[EV_RestoreDamagable] =
                    eventCallback_t0<idDamagable> { obj: idDamagable -> obj.Event_RestoreDamagable() }
            }
        }

        private val count: CInt = CInt()
        private val nextTriggerTime: CInt = CInt()
        override fun Save(savefile: idSaveGame) {
            super.Save(savefile)
            savefile.WriteInt(count._val)
            savefile.WriteInt(nextTriggerTime._val)
        }

        override fun Restore(savefile: idRestoreGame) {
            super.Restore(savefile)
            savefile.ReadInt(count)
            savefile.ReadInt(nextTriggerTime)
        }

        override fun Spawn() {
            super.Spawn()
            val broken = idStr()
            health = spawnArgs.GetInt("health", "5")
            spawnArgs.GetInt("count", "1", count)
            nextTriggerTime._val = (0)

            // make sure the model gets cached
            spawnArgs.GetString("broken", "", broken)
            if (broken.Length() != 0 && ModelManager.renderModelManager.CheckModel(broken.toString()) == null) {
                idGameLocal.Error(
                    "idDamagable '%s' at (%s): cannot load broken model '%s'",
                    name,
                    GetPhysics().GetOrigin().ToString(0),
                    broken
                )
            }
            fl.takedamage = true
            GetPhysics().SetContents(Material.CONTENTS_SOLID)
        }

        override fun Killed(inflictor: idEntity?, attacker: idEntity?, damage: Int, dir: idVec3, location: Int) {
            if (gameLocal.time < nextTriggerTime._val) {
                health += damage
                return
            }
            BecomeBroken(attacker)
        }

        private fun BecomeBroken(activator: idEntity?) {
            val forceState = CFloat()
            val numStates = CInt()
            val cycle = CInt()
            val wait = CFloat()
            if (gameLocal.time < nextTriggerTime._val) {
                return
            }
            spawnArgs.GetFloat("wait", "0.1f", wait)
            nextTriggerTime._val = ((gameLocal.time + SEC2MS(wait._val)))
            if (count._val > 0) {
                count.decrement()
                if (0 == count._val) {
                    fl.takedamage = false
                } else {
                    health = spawnArgs.GetInt("health", "5")
                }
            }
            val broken = idStr()
            spawnArgs.GetString("broken", "", broken)
            if (broken.Length() != 0) {
                SetModel(broken.toString())
            }

            // offset the start time of the shader to sync it to the gameLocal time
            renderEntity!!.shaderParms[RenderWorld.SHADERPARM_TIMEOFFSET] = -MS2SEC(gameLocal.time.toFloat())
            spawnArgs.GetInt("numstates", "1", numStates)
            spawnArgs.GetInt("cycle", "0", cycle)
            spawnArgs.GetFloat("forcestate", "0", forceState)

            // set the state parm
            if (cycle._val != 0) {
                renderEntity!!.shaderParms[RenderWorld.SHADERPARM_MODE]++
                if (renderEntity!!.shaderParms[RenderWorld.SHADERPARM_MODE] > numStates._val) {
                    renderEntity!!.shaderParms[RenderWorld.SHADERPARM_MODE] = 0.0f
                }
            } else if (forceState._val != 0.0f) {
                renderEntity!!.shaderParms[RenderWorld.SHADERPARM_MODE] = forceState._val
            } else {
                renderEntity!!.shaderParms[RenderWorld.SHADERPARM_MODE] =
                    (gameLocal.random.RandomInt(numStates._val) + 1).toFloat()
            }
            renderEntity!!.shaderParms[RenderWorld.SHADERPARM_TIMEOFFSET] = -MS2SEC(gameLocal.time.toFloat())
            ActivateTargets(activator)
            if (spawnArgs.GetBool("hideWhenBroken")) {
                Hide()
                PostEventMS(EV_RestoreDamagable, nextTriggerTime._val - gameLocal.time)
                BecomeActive(TH_THINK)
            }
        }

        private fun Event_RestoreDamagable() {
            health = spawnArgs.GetInt("health", "5")
            Show()
        }

        override fun CreateInstance(): idClass = idDamagable()

        override fun GetType(): idTypeInfo = Type

        override fun getEventCallBack(event: idEventDef): eventCallback_t<*>? {
            return eventCallbacks[event]
        }

        // D3XP: also clear physics contents when hidden/shown
        override fun Hide() {
            super.Hide()
            if (isD3XP) {
                GetPhysics().SetContents(0)
            }
        }

        override fun Show() {
            super.Show()
            if (isD3XP) {
                GetPhysics().SetContents(Material.CONTENTS_SOLID)
            }
        }

        //
        //
        init {
            count._val = (0)
            nextTriggerTime._val = (0)
        }
    }

    /*
     ===============================================================================

     Hidden object that explodes when activated

     ===============================================================================
     *//*
     ===============================================================================

     idExplodable

     ===============================================================================
     */
    class idExplodable : idEntity() {
        companion object {
            val Type = idTypeInfo("idExplodable", "idEntity") { idExplodable() }

            //	CLASS_PROTOTYPE( idExplodable );
            private val eventCallbacks: MutableMap<idEventDef, eventCallback_t<*>> = HashMap()
            private fun Event_Explode(e: idExplodable, activator: idEventArg<idEntity>) {
                val temp = arrayOfNulls<String>(1)
                if (e.spawnArgs.GetString("def_damage", "damage_explosion", temp)) {
                    gameLocal.RadiusDamage(
                        e.GetPhysics().GetOrigin(), activator.value, activator.value, e, e, temp[0]!!
                    )
                }
                e.StartSound("snd_explode", gameSoundChannel_t.SND_CHANNEL_ANY, 0, false)

                // Show() calls UpdateVisuals, so we don't need to call it ourselves after setting the shaderParms
                e.renderEntity!!.shaderParms[RenderWorld.SHADERPARM_RED] = 1.0f
                e.renderEntity!!.shaderParms[RenderWorld.SHADERPARM_GREEN] = 1.0f
                e.renderEntity!!.shaderParms[RenderWorld.SHADERPARM_BLUE] = 1.0f
                e.renderEntity!!.shaderParms[RenderWorld.SHADERPARM_ALPHA] = 1.0f
                e.renderEntity!!.shaderParms[RenderWorld.SHADERPARM_TIMEOFFSET] = -MS2SEC(gameLocal.time.toFloat())
                e.renderEntity!!.shaderParms[RenderWorld.SHADERPARM_DIVERSITY] = 0.0f
                e.Show()
                e.PostEventMS(EV_Remove, 2000)
                e.ActivateTargets(activator.value)
            }

            fun getEventCallBacks(): MutableMap<idEventDef, eventCallback_t<*>> {
                return eventCallbacks
            }

            init {
                eventCallbacks.putAll(idEntity.getEventCallBacks())
                eventCallbacks[EV_Activate] =
                    eventCallback_t1<idExplodable> { e: idExplodable, activator: idEventArg<*>? ->
                        Event_Explode(e, activator as idEventArg<idEntity>)
                    }
            }
        }

        override fun Spawn() {
            super.Spawn()
            Hide()
        }

        override fun CreateInstance(): idClass = idExplodable()

        override fun GetType(): idTypeInfo = Type

        override fun getEventCallBack(event: idEventDef): eventCallback_t<*>? {
            return eventCallbacks[event]
        }
    }

    /*
     ===============================================================================

     idSpring

     ===============================================================================
     */
    class idSpring : idEntity() {
        companion object {
            val Type = idTypeInfo("idSpring", "idEntity") { idSpring() }

            //	CLASS_PROTOTYPE( idSpring );
            private val eventCallbacks: MutableMap<idEventDef, eventCallback_t<*>> = HashMap()

            //
            //
            fun getEventCallBacks(): MutableMap<idEventDef, eventCallback_t<*>> {
                return eventCallbacks
            }

            init {
                eventCallbacks.putAll(idEntity.getEventCallBacks())
                eventCallbacks[EV_PostSpawn] = eventCallback_t0<idSpring> { obj: idSpring -> obj.Event_LinkSpring() }
                eventCallbacks[EV_Activate] = eventCallback_t1<idSpring> { obj: idSpring, activator: idEventArg<*>? ->
                    obj.Event_Activate(activator as idEventArg<idEntity>)
                }
            }
        }

        private val id1: CInt = CInt()
        private val id2: CInt = CInt()

        // DG: changed from raw idEntity* to idEntityPtr for save/restore safety
        private var ent1: idEntityPtr<idEntity> = idEntityPtr()
        private var ent2: idEntityPtr<idEntity> = idEntityPtr()
        private val p1: idVec3 = idVec3()
        private val p2: idVec3 = idVec3()
        private val spring: idForce_Spring = idForce_Spring()
        private var enabled: Boolean = false

        override fun Spawn() {
            super.Spawn()
            val Kstretch = CFloat()
            val Kcompress = CFloat()
            val damping = CFloat()
            val restLength = CFloat()
            val maxLength = CFloat()
            val pullEnt1 = CBool(true)

            enabled = !spawnArgs.GetBool("start_off")
            spawnArgs.GetInt("id1", "0", id1)
            spawnArgs.GetInt("id2", "0", id2)
            spawnArgs.GetVector("point1", "0 0 0", p1)
            spawnArgs.GetVector("point2", "0 0 0", p2)
            spawnArgs.GetFloat("constant", "100.0f", Kstretch)
            spawnArgs.GetFloat("damping", "10.0f", damping)
            spawnArgs.GetFloat("restlength", "0.0f", restLength)
            spawnArgs.GetFloat("maxLength", "200.0f", maxLength)
            // DG: added compress and pullEntity1
            spawnArgs.GetFloat("compress", "0.0f", Kcompress)
            spawnArgs.GetBool("pullEnt1", "1", pullEnt1)

            spring.InitSpring(
                Kstretch._val, Kcompress._val, damping._val, restLength._val, maxLength._val, pullEnt1._val
            )

            ent1.oSet(null)
            ent2.oSet(null)
            PostEventMS(EV_PostSpawn, 0)
        }

        override fun Think() {
            // run physics
            RunPhysics()
            if ((thinkFlags and TH_THINK) != 0) {
                if (enabled && ent1.GetEntity() != null && ent2.GetEntity() != null) {
                    // evaluate force
                    spring.Evaluate(gameLocal.time)

                    if (SysCvar.g_debugMover.GetBool()) {
                        val start = idVec3()
                        val end = idVec3()
                        val origin = idVec3()
                        val axis = idMat3()

                        start.set(p1)
                        if (ent1.GetEntity()!!.GetPhysics() != null) {
                            axis.set(ent1.GetEntity()!!.GetPhysics().GetAxis())
                            origin.set(ent1.GetEntity()!!.GetPhysics().GetOrigin())
                            start.set(origin.plus(start.times(axis)))
                        }
                        end.set(p2)
                        if (ent2.GetEntity()!!.GetPhysics() != null) {
                            axis.set(ent2.GetEntity()!!.GetPhysics().GetAxis())
                            origin.set(ent2.GetEntity()!!.GetPhysics().GetOrigin())
                            end.set(origin.plus(p2.times(axis)))
                        }
                        gameRenderWorld!!.DebugLine(idVec4(1.0f, 1.0f, 0.0f, 1.0f), start, end, 0, true)
                    }
                } else {
                    BecomeInactive(TH_THINK)
                }
            }
            Present()
        }

        private fun Event_LinkSpring() {
            val name1 = idStr()
            val name2 = idStr()
            spawnArgs.GetString("ent1", "", name1)
            spawnArgs.GetString("ent2", "", name2)
            if (name1.Length() != 0) {
                ent1.oSet(gameLocal.FindEntity(name1.toString()))
                if (ent1.GetEntity() == null) {
                    // DG: changed from Error to Warning (dhewm3)
                    gameLocal.Warning(
                        "idSpring '%s' at (%s): cannot find first entity '%s'",
                        name,
                        GetPhysics().GetOrigin().ToString(0),
                        name1
                    )
                }
            } else {
                ent1.oSet(gameLocal.entities[Game_local.ENTITYNUM_WORLD])
            }
            if (name2.Length() != 0) {
                ent2.oSet(gameLocal.FindEntity(name2.toString()))
                if (ent2.GetEntity() == null) {
                    gameLocal.Warning(
                        "idSpring '%s' at (%s): cannot find second entity '%s'",
                        name,
                        GetPhysics().GetOrigin().ToString(0),
                        name2
                    )
                }
            } else {
                ent2.oSet(gameLocal.entities[Game_local.ENTITYNUM_WORLD])
            }
            if (ent1.GetEntity() != null && ent2.GetEntity() != null) {
                spring.SetPosition(
                    ent1.GetEntity()!!.GetPhysics(), id1._val, p1, ent2.GetEntity()!!.GetPhysics(), id2._val, p2
                )
                if (enabled) {
                    BecomeActive(TH_THINK)
                }
            }
        }

        // DG: added Event_Activate toggle (from FraggingFree)
        private fun Event_Activate(activator: idEventArg<idEntity>) {
            enabled = !enabled
            if (enabled) {
                BecomeActive(TH_THINK)
            } else {
                BecomeInactive(TH_THINK)
            }
        }

        // DG: added Save/Restore (from FraggingFree)
        override fun Save(savefile: idSaveGame) {
            super.Save(savefile)
            ent1.Save(savefile)
            ent2.Save(savefile)
            savefile.WriteInt(id1._val)
            savefile.WriteInt(id2._val)
            savefile.WriteVec3(p1)
            savefile.WriteVec3(p2)
            savefile.WriteBool(enabled)
        }

        override fun Restore(savefile: idRestoreGame) {
            super.Restore(savefile)
            ent1.Restore(savefile)
            ent2.Restore(savefile)
            id1._val = savefile.ReadInt()
            id2._val = savefile.ReadInt()
            savefile.ReadVec3(p1)
            savefile.ReadVec3(p2)
            enabled = savefile.ReadBool()
            PostEventMS(EV_PostSpawn, 0) // initialize the spring asap but not now!
        }

        override fun CreateInstance(): idClass = idSpring()

        override fun GetType(): idTypeInfo = Type

        override fun getEventCallBack(event: idEventDef): eventCallback_t<*>? {
            return eventCallbacks[event]
        }
    }

    class idForceField : idEntity() {
        companion object {
            val Type = idTypeInfo("idForceField", "idEntity") { idForceField() }

            // CLASS_PROTOTYPE( idForceField );
            private val eventCallbacks: MutableMap<idEventDef, eventCallback_t<*>> = HashMap()

            //
            //
            fun getEventCallBacks(): MutableMap<idEventDef, eventCallback_t<*>> {
                return eventCallbacks
            }

            init {
                eventCallbacks.putAll(idEntity.getEventCallBacks())
                eventCallbacks[EV_Activate] =
                    eventCallback_t1<idForceField> { obj: idForceField, activator: idEventArg<*>? ->
                        obj.Event_Activate(activator as idEventArg<idEntity>)
                    }
                eventCallbacks[EV_Toggle] = eventCallback_t0<idForceField> { obj: idForceField -> obj.Event_Toggle() }
                eventCallbacks[EV_FindTargets] =
                    eventCallback_t0<idForceField> { obj: idForceField -> obj.Event_FindTargets() }
            }
        }

        private val forceField: idForce_Field = idForce_Field()
        override fun Save(savefile: idSaveGame) {
            super.Save(savefile)
            savefile.WriteStaticObject(forceField)
        }

        override fun Restore(savefile: idRestoreGame) {
            super.Restore(savefile)
            savefile.ReadStaticObject(forceField)
        }

        override fun Spawn() {
            super.Spawn()
            val uniform = idVec3()
            val explosion = CFloat()
            val implosion = CFloat()
            val randomTorque = CFloat()
            if (spawnArgs.GetVector("uniform", "0 0 0", uniform)) {
                forceField.Uniform(uniform)
            } else if (spawnArgs.GetFloat("explosion", "0", explosion)) {
                forceField.Explosion(explosion._val)
            } else if (spawnArgs.GetFloat("implosion", "0", implosion)) {
                forceField.Implosion(implosion._val)
            }
            if (spawnArgs.GetFloat("randomTorque", "0", randomTorque)) {
                forceField.RandomTorque(randomTorque._val)
            }
            if (spawnArgs.GetBool("applyForce", "0")) {
                forceField.SetApplyType(forceFieldApplyType.FORCEFIELD_APPLY_FORCE)
            } else if (spawnArgs.GetBool("applyImpulse", "0")) {
                forceField.SetApplyType(forceFieldApplyType.FORCEFIELD_APPLY_IMPULSE)
            } else {
                forceField.SetApplyType(forceFieldApplyType.FORCEFIELD_APPLY_VELOCITY)
            }
            forceField.SetPlayerOnly(spawnArgs.GetBool("playerOnly", "0"))
            forceField.SetMonsterOnly(spawnArgs.GetBool("monsterOnly", "0"))

            // set the collision model on the force field
            forceField.SetClipModel(idClipModel(GetPhysics().GetClipModel()!!))

            // remove the collision model from the physics object
            GetPhysics().SetClipModel(null, 1.0f)
            if (spawnArgs.GetBool("start_on")) {
                BecomeActive(TH_THINK)
            }
        }

        override fun Think() {
            if ((thinkFlags and TH_THINK) != 0) {
                // evaluate force
                forceField.Evaluate(gameLocal.time)
            }
            Present()
        }

        private fun Toggle() {
            if ((thinkFlags and TH_THINK) != 0) {
                BecomeInactive(TH_THINK)
            } else {
                BecomeActive(TH_THINK)
            }
        }

        private fun Event_Activate(activator: idEventArg<idEntity>) {
            val wait = CFloat()
            Toggle()
            if (spawnArgs.GetFloat("wait", "0.01", wait)) {
                PostEventSec(EV_Toggle, wait._val)
            }
        }

        private fun Event_Toggle() {
            Toggle()
        }

        private fun Event_FindTargets() {
            FindTargets()
            RemoveNullTargets()
            if (targets.Num() != 0) {
                forceField.Uniform(
                    targets[0].GetEntity()!!.GetPhysics().GetOrigin().minus(GetPhysics().GetOrigin())
                )
            }
        }

        override fun CreateInstance(): idClass = idForceField()

        override fun GetType(): idTypeInfo = Type

        override fun getEventCallBack(event: idEventDef): eventCallback_t<*>? {
            return eventCallbacks[event]
        }
    }

    class idAnimated : idAFEntity_Gibbable() {
        companion object {
            val Type = idTypeInfo("idAnimated", "idAFEntity_Gibbable") { idAnimated() }

            // CLASS_PROTOTYPE( idAnimated );
            private val eventCallbacks: MutableMap<idEventDef, eventCallback_t<*>> = HashMap()

            // ~idAnimated();
            fun getEventCallBacks(): MutableMap<idEventDef, eventCallback_t<*>> {
                return eventCallbacks
            }

            init {
                eventCallbacks.putAll(idAFEntity_Gibbable.getEventCallBacks())
                eventCallbacks[EV_Activate] =
                    eventCallback_t1<idAnimated> { obj: idAnimated, _activator: idEventArg<*>? ->
                        obj.Event_Activate(_activator as idEventArg<idEntity>)
                    }
                eventCallbacks[EV_Animated_Start] =
                    eventCallback_t0<idAnimated> { obj: idAnimated -> obj.Event_Start() }
                eventCallbacks[EV_StartRagdoll] =
                    eventCallback_t0<idAnimated> { obj: idAnimated -> obj.Event_StartRagdoll() }
                eventCallbacks[EV_AnimDone] =
                    eventCallback_t1<idAnimated> { obj: idAnimated, animIndex: idEventArg<*>? ->
                        obj.Event_AnimDone(animIndex as idEventArg<Int>)
                    }
                eventCallbacks[EV_Footstep] = eventCallback_t0<idAnimated> { obj: idAnimated -> obj.Event_Footstep() }
                eventCallbacks[EV_FootstepLeft] =
                    eventCallback_t0<idAnimated> { obj: idAnimated -> obj.Event_Footstep() }
                eventCallbacks[EV_FootstepRight] =
                    eventCallback_t0<idAnimated> { obj: idAnimated -> obj.Event_Footstep() }
                eventCallbacks[EV_LaunchMissiles] =
                    eventCallback_t6<idAnimated> { obj: idAnimated, projectilename: idEventArg<*>?, sound: idEventArg<*>?, launchjoint: idEventArg<*>?, targetjoint: idEventArg<*>?, numshots: idEventArg<*>?, framedelay: idEventArg<*>? ->
                        obj.Event_LaunchMissiles(
                            projectilename as idEventArg<String>,
                            sound as idEventArg<String>,
                            launchjoint as idEventArg<String>,
                            targetjoint as idEventArg<String>,
                            numshots as idEventArg<Int>,
                            framedelay as idEventArg<Int>
                        )
                    }
                eventCallbacks[EV_LaunchMissilesUpdate] =
                    eventCallback_t4<idAnimated> { obj: idAnimated, launchjoint: idEventArg<*>?, targetjoint: idEventArg<*>?, numshots: idEventArg<*>?, framedelay: idEventArg<*>? ->
                        obj.Event_LaunchMissilesUpdate(
                            launchjoint as idEventArg<Int>,
                            targetjoint as idEventArg<Int>,
                            numshots as idEventArg<Int>,
                            framedelay as idEventArg<Int>
                        )
                    }
                // D3XP
                eventCallbacks[EV_SetAnimation] =
                    eventCallback_t1<idAnimated> { obj: idAnimated, animName: idEventArg<*>? ->
                        obj.Event_SetAnimation(animName as idEventArg<String>)
                    }
                eventCallbacks[EV_GetAnimationLength] =
                    eventCallback_t0<idAnimated> { obj: idAnimated -> obj.Event_GetAnimationLength() }
            }
        }

        private val activator: idEntityPtr<idEntity>
        private var activated: Boolean
        private var anim = 0
        private var blendFrames = 0
        private var current_anim_index: Int
        private var num_anims: Int
        private var   /*jointHandle_t*/soundJoint: Int
        override fun Save(savefile: idSaveGame) {
            super.Save(savefile)
            savefile.WriteInt(current_anim_index)
            savefile.WriteInt(num_anims)
            savefile.WriteInt(anim)
            savefile.WriteInt(blendFrames)
            savefile.WriteJoint(soundJoint)
            activator.Save(savefile)
            savefile.WriteBool(activated)
        }

        override fun Restore(savefile: idRestoreGame) {
            super.Restore(savefile)
            val current_anim_index = CInt()
            val num_anims = CInt()
            val anim = CInt()
            val blendFrames = CInt()
            val soundJoint = CInt()
            val activated = CBool(false)
            savefile.ReadInt(current_anim_index)
            savefile.ReadInt(num_anims)
            savefile.ReadInt(anim)
            savefile.ReadInt(blendFrames)
            savefile.ReadJoint(soundJoint)
            activator.Restore(savefile)
            savefile.ReadBool(activated)
            this.current_anim_index = current_anim_index._val
            this.num_anims = num_anims._val
            this.anim = anim._val
            this.blendFrames = blendFrames._val
            this.soundJoint = soundJoint._val
            this.activated = activated._val
        }

        override fun Spawn() {
            super.Spawn()
            val animname = arrayOfNulls<String>(1)
            val anim2: Int
            val wait = CFloat()
            val joint: String?
            val num_anims2 = CInt()
            joint = spawnArgs.GetString("sound_bone", "origin")!!
            soundJoint = animator.GetJointHandle(joint)
            if (soundJoint == Model.INVALID_JOINT) {
                gameLocal.Warning(
                    "idAnimated '%s' at (%s): cannot find joint '%s' for sound playback",
                    name,
                    GetPhysics().GetOrigin().ToString(0),
                    joint
                )
            }
            LoadAF()

            // allow bullets to collide with a combat model
            if (spawnArgs.GetBool("combatModel", "0")) {
                combatModel = idClipModel(modelDefHandle)
            }

            // allow the entity to take damage
            if (spawnArgs.GetBool("takeDamage", "0")) {
                fl.takedamage = true
            }
            blendFrames = 0
            current_anim_index = 0
            spawnArgs.GetInt("num_anims", "0", num_anims2)
            num_anims = num_anims2._val
            blendFrames = spawnArgs.GetInt("blend_in")
            animname[0] = spawnArgs.GetString(if (num_anims != 0) "anim1" else "anim")
            if (0 == animname[0]!!.length) {
                anim = 0
            } else {
                anim = animator.GetAnim(animname[0]!!)
                if (0 == anim) {
                    idGameLocal.Error(
                        "idAnimated '%s' at (%s): cannot find anim '%s'",
                        name,
                        GetPhysics().GetOrigin().ToString(0),
                        animname[0]
                    )
                }
            }
            if (spawnArgs.GetBool("hide")) {
                Hide()
                if (0 == num_anims) {
                    blendFrames = 0
                }
            } else if (spawnArgs.GetString("start_anim", "", animname)) {
                anim2 = animator.GetAnim(animname[0]!!)
                if (0 == anim2) {
                    idGameLocal.Error(
                        "idAnimated '%s' at (%s): cannot find anim '%s'",
                        name,
                        GetPhysics().GetOrigin().ToString(0),
                        animname[0]
                    )
                }
                animator.CycleAnim(Anim.ANIMCHANNEL_ALL, anim2, gameLocal.time, 0)
            } else if (anim != 0) {
                // init joints to the first frame of the animation
                animator.SetFrame(Anim.ANIMCHANNEL_ALL, anim, 1, gameLocal.time, 0)
                if (0 == num_anims) {
                    blendFrames = 0
                }
            }
            spawnArgs.GetFloat("wait", "-1", wait)
            if (wait._val >= 0) {
                PostEventSec(EV_Activate, wait._val, this)
            }
        }

        override fun LoadAF(): Boolean {
            val fileName = arrayOfNulls<String>(1)
            if (!spawnArgs.GetString("ragdoll", "*unknown*", fileName)) {
                return false
            }
            af.SetAnimator(GetAnimator())
            return af.Load(this, fileName[0]!!)
        }

        fun StartRagdoll(): Boolean {
            // if no AF loaded
            if (!af.IsLoaded()) {
                return false
            }

            // if the AF is already active
            if (af.IsActive()) {
                return true
            }

            // disable any collision model used
            GetPhysics().DisableClip()

            // start using the AF
            af.StartFromCurrentPose(spawnArgs.GetInt("velocityTime", "0"))
            return true
        }

        override fun GetPhysicsToSoundTransform(origin: idVec3, axis: idMat3): Boolean {
            animator.GetJointTransform(soundJoint, gameLocal.time, origin, axis)
            axis.set(renderEntity!!.axis)
            return true
        }

        private fun PlayNextAnim() {
            val animName = arrayOfNulls<String>(1)
            val len: Int
            val cycle = CInt()
            if (current_anim_index >= num_anims) {
                Hide()
                if (spawnArgs.GetBool("remove")) {
                    PostEventMS(EV_Remove, 0)
                } else {
                    current_anim_index = 0
                }
                return
            }
            Show()
            current_anim_index++
            spawnArgs.GetString(Str.va("anim%d", current_anim_index), "", animName)
            if (animName[0].isNullOrEmpty()) {
                anim = 0
                animator.Clear(Anim.ANIMCHANNEL_ALL, gameLocal.time, Anim.FRAME2MS(blendFrames))
                return
            }
            anim = animator.GetAnim(animName[0]!!)
            if (0 == anim) {
                gameLocal.Warning("missing anim '%s' on %s", animName[0], name)
                return
            }
            if (SysCvar.g_debugCinematic.GetBool()) {
                gameLocal.Printf(
                    "%d: '%s' start anim '%s'\n", gameLocal.framenum, GetName(), animName[0]
                )
            }
            spawnArgs.GetInt("cycle", "1", cycle)
            if (current_anim_index == num_anims && spawnArgs.GetBool("loop_last_anim")) {
                cycle._val = (-1)
            }
            animator.CycleAnim(Anim.ANIMCHANNEL_ALL, anim, gameLocal.time, Anim.FRAME2MS(blendFrames))
            animator.CurrentAnim(Anim.ANIMCHANNEL_ALL).SetCycleCount(cycle._val)
            len = animator.CurrentAnim(Anim.ANIMCHANNEL_ALL).PlayLength()
            if (len >= 0) {
                PostEventMS(EV_AnimDone, len, current_anim_index)
            }

            // offset the start time of the shader to sync it to the game time
            renderEntity!!.shaderParms[RenderWorld.SHADERPARM_TIMEOFFSET] = -MS2SEC(gameLocal.time.toFloat())
            animator.ForceUpdate()
            UpdateAnimation()
            UpdateVisuals()
            Present()
        }

        private fun Event_Activate(_activator: idEventArg<idEntity>) {
            if (num_anims != 0) {
                PlayNextAnim()
                activator.oSet(_activator.value)
                return
            }
            if (activated) {
                // already activated
                return
            }
            activated = true
            activator.oSet(_activator.value)
            ProcessEvent(EV_Animated_Start)
        }

        private fun Event_Start() {
            val cycle = CInt()
            val len: Int
            Show()
            if (num_anims != 0) {
                PlayNextAnim()
                return
            }
            if (anim != 0) {
                if (SysCvar.g_debugCinematic.GetBool()) {
                    val animPtr = animator.GetAnim(anim)
                    gameLocal.Printf(
                        "%d: '%s' start anim '%s'\n",
                        gameLocal.framenum,
                        GetName(),
                        if (animPtr != null) animPtr.Name() else ""
                    )
                }
                spawnArgs.GetInt("cycle", "1", cycle)
                animator.CycleAnim(Anim.ANIMCHANNEL_ALL, anim, gameLocal.time, Anim.FRAME2MS(blendFrames))
                animator.CurrentAnim(Anim.ANIMCHANNEL_ALL).SetCycleCount(cycle._val)
                len = animator.CurrentAnim(Anim.ANIMCHANNEL_ALL).PlayLength()
                if (len >= 0) {
                    PostEventMS(EV_AnimDone, len, 1)
                }
            }

            // offset the start time of the shader to sync it to the game time
            renderEntity!!.shaderParms[RenderWorld.SHADERPARM_TIMEOFFSET] = -MS2SEC(gameLocal.time.toFloat())
            animator.ForceUpdate()
            UpdateAnimation()
            UpdateVisuals()
            Present()
        }

        private fun Event_StartRagdoll() {
            StartRagdoll()
        }

        private fun Event_AnimDone(animIndex: idEventArg<Int>) {
            if (SysCvar.g_debugCinematic.GetBool()) {
                val animPtr = animator.GetAnim(anim)
                gameLocal.Printf(
                    "%d: '%s' end anim '%s'\n",
                    gameLocal.framenum,
                    GetName(),
                    if (animPtr != null) animPtr.Name() else ""
                )
            }
            if (animIndex.value >= num_anims && spawnArgs.GetBool("remove")) {
                Hide()
                PostEventMS(EV_Remove, 0)
            } else if (spawnArgs.GetBool("auto_advance")) {
                PlayNextAnim()
            } else {
                activated = false
            }
            ActivateTargets(activator.GetEntity())
        }

        private fun Event_Footstep() {
            StartSound("snd_footstep", gameSoundChannel_t.SND_CHANNEL_BODY, 0, false)
        }

        private fun Event_LaunchMissiles(
            projectilename: idEventArg<String>,
            sound: idEventArg<String>,
            launchjoint: idEventArg<String>,
            targetjoint: idEventArg<String>,
            numshots: idEventArg<Int>,
            framedelay: idEventArg<Int>
        ) {
            val projectileDef: idDict?
            val   /*jointHandle_t*/launch: Int
            val   /*jointHandle_t*/target: Int
            projectileDef = gameLocal.FindEntityDefDict(projectilename.value, false)
            if (null == projectileDef) {
                gameLocal.Warning(
                    "idAnimated '%s' at (%s): unknown projectile '%s'",
                    name,
                    GetPhysics().GetOrigin().ToString(0),
                    projectilename.value
                )
                return
            }
            launch = animator.GetJointHandle(launchjoint.value)
            if (launch == Model.INVALID_JOINT) {
                gameLocal.Warning(
                    "idAnimated '%s' at (%s): unknown launch joint '%s'",
                    name,
                    GetPhysics().GetOrigin().ToString(0),
                    launchjoint.value
                )
                idGameLocal.Error("Unknown joint '%s'", launchjoint.value)
            }
            target = animator.GetJointHandle(targetjoint.value)
            if (target == Model.INVALID_JOINT) {
                gameLocal.Warning(
                    "idAnimated '%s' at (%s): unknown target joint '%s'",
                    name,
                    GetPhysics().GetOrigin().ToString(0),
                    targetjoint.value
                )
            }
            spawnArgs.Set("projectilename", projectilename.value)
            spawnArgs.Set("missilesound", sound.value)
            CancelEvents(EV_LaunchMissilesUpdate)
            ProcessEvent(EV_LaunchMissilesUpdate, launch, target, numshots.value - 1, framedelay.value)
        }

        private fun Event_LaunchMissilesUpdate(
            launchjoint: idEventArg<Int>,
            targetjoint: idEventArg<Int>,
            numshots: idEventArg<Int>,
            framedelay: idEventArg<Int>
        ) {
            val launchPos = idVec3()
            val targetPos = idVec3()
            val axis = idMat3()
            val dir = idVec3()
            val ent = arrayOfNulls<idEntity>(1)
            val projectile: idProjectile?
            val projectileDef: idDict?
            val projectilename: String?
            projectilename = spawnArgs.GetString("projectilename")
            projectileDef = gameLocal.FindEntityDefDict(projectilename, false)
            if (null == projectileDef) {
                gameLocal.Warning(
                    "idAnimated '%s' at (%s): 'launchMissiles' called with unknown projectile '%s'",
                    name,
                    GetPhysics().GetOrigin().ToString(0),
                    projectilename
                )
                return
            }
            StartSound("snd_missile", gameSoundChannel_t.SND_CHANNEL_WEAPON, 0, false)
            animator.GetJointTransform(launchjoint.value, gameLocal.time, launchPos, axis)
            launchPos.set(renderEntity!!.origin.plus(launchPos.times(renderEntity!!.axis)))
            animator.GetJointTransform(targetjoint.value, gameLocal.time, targetPos, axis)
            targetPos.set(renderEntity!!.origin.plus(targetPos.times(renderEntity!!.axis)))
            dir.set(targetPos.minus(launchPos))
            dir.Normalize()
            gameLocal.SpawnEntityDef(projectileDef, ent, false)
            if (ent[0] == null || ent[0] !is idProjectile) {
                idGameLocal.Error(
                    "idAnimated '%s' at (%s): in 'launchMissiles' call '%s' is not an idProjectile",
                    name,
                    GetPhysics().GetOrigin().ToString(0),
                    projectilename
                )
            }
            projectile = ent[0] as idProjectile
            projectile.Create(this, launchPos, dir)
            projectile.Launch(launchPos, dir, vec3_origin)
            if (numshots.value > 0) {
                PostEventMS(
                    EV_LaunchMissilesUpdate,
                    Anim.FRAME2MS(framedelay.value),
                    launchjoint.value,
                    targetjoint.value,
                    numshots.value - 1,
                    framedelay.value
                )
            }
        }

        // D3XP
        private fun Event_SetAnimation(animName: idEventArg<String>) {
            anim = animator.GetAnim(animName.value)
            if (anim == 0) {
                idGameLocal.Error(
                    "idAnimated '%s' at (%s): cannot find anim '%s'",
                    name,
                    GetPhysics().GetOrigin().ToString(0),
                    animName.value
                )
            }
        }

        // D3XP
        private fun Event_GetAnimationLength() {
            var length = 0f
            if (anim != 0) {
                length = animator.AnimLength(anim).toFloat() / 1000f
            }
            idThread.ReturnFloat(length)
        }

        override fun CreateInstance(): idClass = idAnimated()

        override fun GetType(): idTypeInfo = Type

        override fun getEventCallBack(event: idEventDef): eventCallback_t<*>? {
            return eventCallbacks[event]
        }

        //
        //
        init {
            soundJoint = Model.INVALID_JOINT
            activated = false
            combatModel = null
            activator = idEntityPtr()
            current_anim_index = 0
            num_anims = 0
        }
    }

    /*
     ===============================================================================

     idStaticEntity

     Some static entities may be optimized into inline geometry by dmap

     ===============================================================================
     */
    open class idStaticEntity : idEntity() {
        companion object {
            val Type = idTypeInfo("idStaticEntity", "idEntity") { idStaticEntity() }

            // CLASS_PROTOTYPE( idStaticEntity );
            private val eventCallbacks: MutableMap<idEventDef, eventCallback_t<*>> = HashMap()
            fun getEventCallBacks(): MutableMap<idEventDef, eventCallback_t<*>> {
                return eventCallbacks
            }

            init {
                eventCallbacks.putAll(idEntity.getEventCallBacks())
                eventCallbacks[EV_Activate] =
                    eventCallback_t1<idStaticEntity> { obj: idStaticEntity, activator: idEventArg<*>? ->
                        obj.Event_Activate(activator as idEventArg<idEntity>)
                    }
            }
        }

        private val fadeFrom: idVec4
        private val fadeTo: idVec4
        private var active = false
        private var fadeEnd: Int
        private var fadeStart: Int
        private var runGui: Boolean
        private var spawnTime = 0
        override fun Save(savefile: idSaveGame) {
            super.Save(savefile)
            savefile.WriteInt(spawnTime)
            savefile.WriteBool(active)
            savefile.WriteVec4(fadeFrom)
            savefile.WriteVec4(fadeTo)
            savefile.WriteInt(fadeStart)
            savefile.WriteInt(fadeEnd)
            savefile.WriteBool(runGui)
        }

        override fun Restore(savefile: idRestoreGame) {
            super.Restore(savefile)
            val spawnTime = CInt()
            val fadeStart = CInt()
            val fadeEnd = CInt() //TODO:make sure the dumbass compiler doesn't decide that all {0}'s are the same (lol)
            val active = CBool()
            val runGui = CBool()
            savefile.ReadInt(spawnTime)
            savefile.ReadBool(active)
            savefile.ReadVec4(fadeFrom)
            savefile.ReadVec4(fadeTo)
            savefile.ReadInt(fadeStart)
            savefile.ReadInt(fadeEnd)
            savefile.ReadBool(runGui)
            this.spawnTime = spawnTime._val
            this.fadeStart = fadeStart._val
            this.fadeEnd = fadeEnd._val
            this.active = active._val
            this.runGui = runGui._val
        }

        override fun Spawn() {
            super.Spawn()
            val solid: Boolean
            val hidden: Boolean

            // an inline static model will not do anything at all
            if (spawnArgs.GetBool("inline") || gameLocal.world!!.spawnArgs.GetBool("inlineAllStatics")) {
                Hide()
                return
            }
            solid = spawnArgs.GetBool("solid")
            hidden = spawnArgs.GetBool("hide")
            if (solid && !hidden) {
                GetPhysics().SetContents(Material.CONTENTS_SOLID)
            } else {
                GetPhysics().SetContents(0)
            }
            spawnTime = gameLocal.time
            active = false
            val model = idStr(spawnArgs.GetString("model"))
            if (model.Find(".prt") >= 0) {
                // we want the parametric particles out of sync with each other
                renderEntity!!.shaderParms[RenderWorld.SHADERPARM_TIMEOFFSET] =
                    gameLocal.random.RandomInt(32767).toFloat()
            }
            fadeFrom.set(1.0f, 1.0f, 1.0f, 1.0f)
            fadeTo.set(1.0f, 1.0f, 1.0f, 1.0f)
            fadeStart = 0
            fadeEnd = 0

            // NOTE: this should be used very rarely because it is expensive
            runGui = spawnArgs.GetBool("runGui")
            if (runGui) {
                BecomeActive(TH_THINK)
            }
        }

        override fun ShowEditingDialog() {
            Common.common.InitTool(Common.EDITOR_PARTICLE, spawnArgs)
        }

        override fun Hide() {
            super.Hide()
            GetPhysics().SetContents(0)
        }

        override fun Show() {
            super.Show()
            if (spawnArgs.GetBool("solid")) {
                GetPhysics().SetContents(Material.CONTENTS_SOLID)
            }
        }

        fun Fade(to: idVec4, fadeTime: Float) {
            GetColor(fadeFrom)
            fadeTo.set(to)
            fadeStart = gameLocal.time
            fadeEnd = (gameLocal.time + SEC2MS(fadeTime))
            BecomeActive(TH_THINK)
        }

        override fun Think() {
            super.Think()
            if ((thinkFlags and TH_THINK) != 0) {
                if (runGui && renderEntity!!.gui[0] != null) {
                    val player = gameLocal.GetLocalPlayer()
                    if (player != null) {
                        if (!player.objectiveSystemOpen) {
                            renderEntity!!.gui[0]!!.StateChanged(gameLocal.time, true)
                            if (renderEntity!!.gui[1] != null) {
                                renderEntity!!.gui[1]!!.StateChanged(gameLocal.time, true)
                            }
                            if (renderEntity!!.gui[2] != null) {
                                renderEntity!!.gui[2]!!.StateChanged(gameLocal.time, true)
                            }
                        }
                    }
                }
                if (fadeEnd > 0) {
                    val color: idVec4 = idVec4()
                    if (gameLocal.time < fadeEnd) {
                        color.Lerp(
                            fadeFrom, fadeTo, (gameLocal.time - fadeStart).toFloat() / (fadeEnd - fadeStart).toFloat()
                        )
                    } else {
                        color.set(fadeTo)
                        fadeEnd = 0
                        BecomeInactive(TH_THINK)
                    }
                    SetColor(color)
                }
            }
        }

        override fun WriteToSnapshot(msg: idBitMsgDelta) {
            GetPhysics().WriteToSnapshot(msg)
            WriteBindToSnapshot(msg)
            WriteColorToSnapshot(msg)
            WriteGUIToSnapshot(msg)
            msg.WriteBits(if (IsHidden()) 1 else 0, 1)
        }

        override fun ReadFromSnapshot(msg: idBitMsgDelta) {
            val hidden: Boolean
            GetPhysics().ReadFromSnapshot(msg)
            ReadBindFromSnapshot(msg)
            ReadColorFromSnapshot(msg)
            ReadGUIFromSnapshot(msg)
            hidden = msg.ReadBits(1) == 1
            if (hidden != IsHidden()) {
                if (hidden) {
                    Hide()
                } else {
                    Show()
                }
            }
            if (msg.HasChanged()) {
                UpdateVisuals()
            }
        }

        private fun Event_Activate(activator: idEventArg<idEntity>) {
            spawnTime = gameLocal.time
            active = !active
            val kv = spawnArgs.FindKey("hide")
            if (kv != null) {
                if (IsHidden()) {
                    Show()
                } else {
                    Hide()
                }
            }
            renderEntity!!.shaderParms[RenderWorld.SHADERPARM_TIMEOFFSET] = -MS2SEC(spawnTime.toFloat())
            renderEntity!!.shaderParms[5] = if (active) 1.0f else 0.0f
            // this change should be a good thing, it will automatically turn on
            // lights etc.. when triggered so that does not have to be specifically done
            // with trigger parms.. it MIGHT break things so need to keep an eye on it
            renderEntity!!.shaderParms[RenderWorld.SHADERPARM_MODE] =
                if (renderEntity!!.shaderParms[RenderWorld.SHADERPARM_MODE] != 0.0f) 0.0f else 1.0f
            BecomeActive(TH_UPDATEVISUALS)
        }

        override fun CreateInstance(): idClass = idStaticEntity()

        override fun GetType(): idTypeInfo = Type

        override fun getEventCallBack(event: idEventDef): eventCallback_t<*>? {
            return eventCallbacks[event]
        }

        //
        //
        init {
            fadeFrom = idVec4(1.0f, 1.0f, 1.0f, 1.0f)
            fadeTo = idVec4(1.0f, 1.0f, 1.0f, 1.0f)
            fadeStart = 0
            fadeEnd = 0
            runGui = false
        }
    }

    /*
     ===============================================================================

     idFuncEmitter

     ===============================================================================
     */
    open class idFuncEmitter : idStaticEntity() {
        companion object {
            val Type = idTypeInfo("idFuncEmitter", "idStaticEntity") { idFuncEmitter() }

            // CLASS_PROTOTYPE( idFuncEmitter );
            private val eventCallbacks: MutableMap<idEventDef, eventCallback_t<*>> = HashMap()
            fun getEventCallBacks(): MutableMap<idEventDef, eventCallback_t<*>> {
                return eventCallbacks
            }

            init {
                eventCallbacks.putAll(idStaticEntity.getEventCallBacks())
                eventCallbacks[EV_Activate] =
                    eventCallback_t1<idFuncEmitter> { obj: idFuncEmitter, activator: idEventArg<*>? ->
                        obj.Event_Activate(activator as idEventArg<idEntity>)
                    }
            }
        }

        private val hidden: CBool = CBool(false)
        override fun Save(savefile: idSaveGame) {
            super.Save(savefile)
            savefile.WriteBool(hidden._val)
        }

        override fun Restore(savefile: idRestoreGame) {
            super.Restore(savefile)
            savefile.ReadBool(hidden)
        }

        override fun Spawn() {
            super.Spawn()
            if (spawnArgs.GetBool("start_off")) {
                hidden._val = (true)
                renderEntity!!.shaderParms[RenderWorld.SHADERPARM_PARTICLE_STOPTIME] = MS2SEC(1.0f)
                UpdateVisuals()
            } else {
                hidden._val = (false)
            }
        }

        open fun Event_Activate(activator: idEventArg<idEntity>) {
            if (hidden._val || spawnArgs.GetBool("cycleTrigger")) {
                renderEntity!!.shaderParms[RenderWorld.SHADERPARM_PARTICLE_STOPTIME] = 0.0f
                renderEntity!!.shaderParms[RenderWorld.SHADERPARM_TIMEOFFSET] = -MS2SEC(gameLocal.time.toFloat())
                hidden._val = (false)
            } else {
                renderEntity!!.shaderParms[RenderWorld.SHADERPARM_PARTICLE_STOPTIME] = MS2SEC(gameLocal.time.toFloat())
                hidden._val = (true)
            }
            UpdateVisuals()
        }

        override fun WriteToSnapshot(msg: idBitMsgDelta) {
            msg.WriteBits(if (hidden._val) 1 else 0, 1)
            msg.WriteFloat(renderEntity!!.shaderParms[RenderWorld.SHADERPARM_PARTICLE_STOPTIME])
            msg.WriteFloat(renderEntity!!.shaderParms[RenderWorld.SHADERPARM_TIMEOFFSET])
        }

        override fun ReadFromSnapshot(msg: idBitMsgDelta) {
            hidden._val = (msg.ReadBits(1) != 0)
            renderEntity!!.shaderParms[RenderWorld.SHADERPARM_PARTICLE_STOPTIME] = msg.ReadFloat()
            renderEntity!!.shaderParms[RenderWorld.SHADERPARM_TIMEOFFSET] = msg.ReadFloat()
            if (msg.HasChanged()) {
                UpdateVisuals()
            }
        }

        override fun CreateInstance(): idClass = idFuncEmitter()

        override fun GetType(): idTypeInfo = Type

        override fun getEventCallBack(event: idEventDef): eventCallback_t<*>? {
            return eventCallbacks[event]
        }

        //
        //
        init {
            hidden._val = (false)
        }
    }

    /*
     ===============================================================================

     idFuncSmoke

     ===============================================================================
     */
    class idFuncSmoke     //
    //
        : idEntity() {
        companion object {
            val Type = idTypeInfo("idFuncSmoke", "idEntity") { idFuncSmoke() }

            // CLASS_PROTOTYPE( idFuncSmoke );
            private val eventCallbacks: MutableMap<idEventDef, eventCallback_t<*>> = HashMap()
            fun getEventCallBacks(): MutableMap<idEventDef, eventCallback_t<*>> {
                return eventCallbacks
            }

            init {
                eventCallbacks.putAll(idEntity.getEventCallBacks())
                eventCallbacks[EV_Activate] =
                    eventCallback_t1<idFuncSmoke> { obj: idFuncSmoke, activator: idEventArg<*>? ->
                        obj.Event_Activate(activator as idEventArg<idEntity>)
                    }
            }
        }

        private var restart = false
        private var smoke: idDeclParticle? = null
        private var smokeTime = 0
        override fun Spawn() {
            super.Spawn()
            val smokeName = spawnArgs.GetString("smoke")
            smoke = if (!smokeName.isEmpty()) { // != '\0' ) {
                declManager.FindType(declType_t.DECL_PARTICLE, smokeName) as idDeclParticle
            } else {
                null
            }
            if (spawnArgs.GetBool("start_off")) {
                smokeTime = 0
                restart = false
            } else if (smoke != null) {
                smokeTime = gameLocal.time
                BecomeActive(TH_UPDATEPARTICLES)
                restart = true
            }
            GetPhysics().SetContents(0)
        }

        override fun Save(savefile: idSaveGame) {
            super.Save(savefile)
            savefile.WriteInt(smokeTime)
            savefile.WriteParticle(smoke)
            savefile.WriteBool(restart)
        }

        override fun Restore(savefile: idRestoreGame) {
            super.Restore(savefile)
            val smokeTime = CInt()
            val restart = CBool()
            savefile.ReadInt(smokeTime)
            smoke = savefile.ReadParticle()
            savefile.ReadBool(restart)
            this.smokeTime = smokeTime._val
            this.restart = restart._val
        }

        override fun Think() {

            // if we are completely closed off from the player, don't do anything at all
            if (CheckDormant() || smoke == null || smokeTime == -1) {
                return
            }
            if ((thinkFlags and TH_UPDATEPARTICLES) != 0 && !IsHidden()) {
                if (!gameLocal.smokeParticles!!.EmitSmoke(
                        smoke,
                        smokeTime,
                        gameLocal.random.CRandomFloat(),
                        GetPhysics().GetOrigin(),
                        GetPhysics().GetAxis()
                    )
                ) {
                    if (restart) {
                        smokeTime = gameLocal.time
                    } else {
                        smokeTime = 0
                        BecomeInactive(TH_UPDATEPARTICLES)
                    }
                }
            }
        }

        fun Event_Activate(activator: idEventArg<idEntity>) {
            if ((thinkFlags and TH_UPDATEPARTICLES) != 0) {
                restart = false
                return
            } else {
                BecomeActive(TH_UPDATEPARTICLES)
                restart = true
                smokeTime = gameLocal.time
            }
        }

        override fun CreateInstance(): idClass = idFuncSmoke()

        override fun GetType(): idTypeInfo = Type

        override fun getEventCallBack(event: idEventDef): eventCallback_t<*>? {
            return eventCallbacks[event]
        }
    }

    class idFuncSplat : idFuncEmitter() {
        companion object {
            val Type = idTypeInfo("idFuncSplat", "idFuncEmitter") { idFuncSplat() }

            // CLASS_PROTOTYPE( idFuncSplat );
            private val eventCallbacks: MutableMap<idEventDef, eventCallback_t<*>> = HashMap()
            fun getEventCallBacks(): MutableMap<idEventDef, eventCallback_t<*>> {
                return eventCallbacks
            }

            init {
                eventCallbacks.putAll(idFuncEmitter.getEventCallBacks())
                eventCallbacks[EV_Activate] =
                    eventCallback_t1<idFuncSplat> { obj: idFuncSplat, activator: idEventArg<*>? ->
                        obj.Event_Activate(activator as idEventArg<idEntity>)
                    }
                eventCallbacks[EV_Splat] = eventCallback_t0<idFuncSplat> { obj: idFuncSplat -> obj.Event_Splat() }
            }
        }

        override fun Event_Activate(activator: idEventArg<idEntity>) {
            super.Event_Activate(activator)
            PostEventSec(EV_Splat, spawnArgs.GetFloat("splatDelay", "0.25f"))
            StartSound("snd_spurt", gameSoundChannel_t.SND_CHANNEL_ANY, 0, false)
        }

        private fun Event_Splat() {
            var splat: String?
            val count = spawnArgs.GetInt("splatCount", "1")
            for (i in 0 until count) {
                splat = spawnArgs.RandomPrefix("mtr_splat", gameLocal.random)
                if (splat != null && !splat.isEmpty()) {
                    val size = spawnArgs.GetFloat("splatSize", "128")
                    val dist = spawnArgs.GetFloat("splatDistance", "128")
                    val angle = spawnArgs.GetFloat("splatAngle", "0")
                    gameLocal.ProjectDecal(
                        GetPhysics().GetOrigin(), GetPhysics().GetAxis()[2], dist, true, size, splat, angle
                    )
                }
            }
            StartSound("snd_splat", gameSoundChannel_t.SND_CHANNEL_ANY, 0, false)
        }

        override fun CreateInstance(): idClass = idFuncSplat()

        override fun GetType(): idTypeInfo = Type

        override fun getEventCallBack(event: idEventDef): eventCallback_t<*>? {
            return eventCallbacks[event]
        }
    }

    /*
     ===============================================================================

     idTextEntity

     ===============================================================================
     */
    class idTextEntity : idEntity() {
        companion object {
            val Type = idTypeInfo("idTextEntity", "idEntity") { idTextEntity() }
        }

        // CLASS_PROTOTYPE( idTextEntity );
        private var playerOriented = false
        private val text: idStr = idStr()

        //
        //
        override fun Spawn() {
            super.Spawn()
            // these are cached as the are used each frame
            text.set(spawnArgs.GetString("text"))
            playerOriented = spawnArgs.GetBool("playerOriented")
            val force = spawnArgs.GetBool("force")
            if (Common.com_developer.GetBool() || force) {
                BecomeActive(TH_THINK)
            }
        }

        override fun Save(savefile: idSaveGame) {
            super.Save(savefile)
            savefile.WriteString(text)
            savefile.WriteBool(playerOriented)
        }

        override fun Restore(savefile: idRestoreGame) {
            super.Restore(savefile)
            val playerOriented = CBool(false)
            savefile.ReadString(text)
            savefile.ReadBool(playerOriented)
            this.playerOriented = playerOriented._val
        }

        override fun Think() {
            if ((thinkFlags and TH_THINK) != 0) {
                gameRenderWorld!!.DrawText(
                    text.toString(),
                    GetPhysics().GetOrigin(),
                    0.25f,
                    colorWhite,
                    if (playerOriented) gameLocal.GetLocalPlayer()!!.viewAngles.ToMat3() else GetPhysics().GetAxis()
                        .Transpose(),
                    1
                )
                for (i in 0 until targets.Num()) {
                    if (targets[i].GetEntity() != null) {
                        gameRenderWorld!!.DebugArrow(
                            colorBlue, GetPhysics().GetOrigin(), targets[i].GetEntity()!!.GetPhysics().GetOrigin(), 1
                        )
                    }
                }
            } else {
                BecomeInactive(TH_ALL)
            }
        }

        override fun CreateInstance(): idClass = idTextEntity()

        override fun GetType(): idTypeInfo = Type
    }

    /*
     ===============================================================================

     idLocationEntity

     ===============================================================================
     */
    class idLocationEntity : idEntity() {
        companion object {
            val Type = idTypeInfo("idLocationEntity", "idEntity") { idLocationEntity() }
        }

        // CLASS_PROTOTYPE( idLocationEntity );
        override fun Spawn() {
            super.Spawn()
            val realName = arrayOfNulls<String>(1)

            // this just holds dict information
            // if "location" not already set, use the entity name.
            if (!spawnArgs.GetString("location", "", realName)) {
                spawnArgs.Set("location", name)
            }
        }

        fun GetLocation(): String {
            return spawnArgs.GetString("location")
        }

        override fun CreateInstance(): idClass = idLocationEntity()

        override fun GetType(): idTypeInfo = Type
    }

    /*
     ===============================================================================

     idLocationSeparatorEntity

     ===============================================================================
     */
    class idLocationSeparatorEntity : idEntity() {
        companion object {
            val Type = idTypeInfo("idLocationSeparatorEntity", "idEntity") { idLocationSeparatorEntity() }
        }

        // CLASS_PROTOTYPE( idLocationSeparatorEntity );
        override fun Spawn() {
            super.Spawn()
            val b: idBounds
            b = idBounds(spawnArgs.GetVector("origin")).Expand(16.0f)
            val   /*qhandle_t*/portal = gameRenderWorld!!.FindPortal(b)
            if (0 == portal) {
                gameLocal.Warning(
                    "LocationSeparator '%s' didn't contact a portal", spawnArgs.GetString("name")
                )
            }
            gameLocal.SetPortalState(portal, TempDump.etoi(portalConnection_t.PS_BLOCK_LOCATION))
        }

        override fun CreateInstance(): idClass = idLocationSeparatorEntity()

        override fun GetType(): idTypeInfo = Type
    }

    /*
     ===============================================================================

     idVacuumSeperatorEntity

     Can be triggered to let vacuum through a portal (blown out window)

     ===============================================================================
     */
    class idVacuumSeparatorEntity : idEntity() {
        companion object {
            val Type = idTypeInfo("idVacuumSeparatorEntity", "idEntity") { idVacuumSeparatorEntity() }

            // CLASS_PROTOTYPE( idVacuumSeparatorEntity );
            private val eventCallbacks: MutableMap<idEventDef, eventCallback_t<*>> = HashMap()
            fun getEventCallBacks(): MutableMap<idEventDef, eventCallback_t<*>> {
                return eventCallbacks
            }

            init {
                eventCallbacks.putAll(idEntity.getEventCallBacks())
                eventCallbacks[EV_Activate] =
                    eventCallback_t1<idVacuumSeparatorEntity> { obj: idVacuumSeparatorEntity, activator: idEventArg<*>? ->
                        obj.Event_Activate(activator as idEventArg<idEntity>)
                    }
            }
        }

        //
        //
        private var   /*qhandle_t*/portal = 0
        override fun Spawn() {
            super.Spawn()
            val b: idBounds
            b = idBounds(spawnArgs.GetVector("origin")).Expand(16.0f)
            portal = gameRenderWorld!!.FindPortal(b)
            if (0 == portal) {
                gameLocal.Warning(
                    "VacuumSeparator '%s' didn't contact a portal", spawnArgs.GetString("name")
                )
                return
            }
            gameLocal.SetPortalState(
                portal,
                TempDump.etoi(portalConnection_t.PS_BLOCK_AIR) or TempDump.etoi(portalConnection_t.PS_BLOCK_LOCATION)
            )
        }

        override fun Save(savefile: idSaveGame) {
            super.Save(savefile)
            savefile.WriteInt(portal)
            savefile.WriteInt(gameRenderWorld!!.GetPortalState(portal))
        }

        override fun Restore(savefile: idRestoreGame) {
            super.Restore(savefile)
            val state = CInt()
            val portal = CInt()
            savefile.ReadInt(portal)
            savefile.ReadInt(state)
            this.portal = portal._val
            gameLocal.SetPortalState(portal._val, state._val)
        }

        fun Event_Activate(activator: idEventArg<idEntity>) {
            if (0 == portal) {
                return
            }
            gameLocal.SetPortalState(portal, TempDump.etoi(portalConnection_t.PS_BLOCK_NONE))
        }

        override fun CreateInstance(): idClass = idVacuumSeparatorEntity()

        override fun GetType(): idTypeInfo = Type

        override fun getEventCallBack(event: idEventDef): eventCallback_t<*>? {
            return eventCallbacks[event]
        }
    }

    /*
     ===============================================================================

     idVacuumEntity

     Levels should only have a single vacuum entity.

     ===============================================================================
     */
    class idVacuumEntity : idEntity() {
        companion object {
            val Type = idTypeInfo("idVacuumEntity", "idEntity") { idVacuumEntity() }
        }

        // public:
        // CLASS_PROTOTYPE( idVacuumEntity );
        override fun Spawn() {
            super.Spawn()
            if (gameLocal.vacuumAreaNum != -1) {
                gameLocal.Warning("idVacuumEntity::Spawn: multiple idVacuumEntity in level")
                return
            }
            val org = idVec3(spawnArgs.GetVector("origin"))
            gameLocal.vacuumAreaNum = gameRenderWorld!!.PointInArea(org)
        }

        override fun CreateInstance(): idClass = idVacuumEntity()

        override fun GetType(): idTypeInfo = Type
    }

    /*
     ===============================================================================

     idBeam

     ===============================================================================
     */
    class idBeam : idEntity() {
        companion object {
            val Type = idTypeInfo("idBeam", "idEntity") { idBeam() }

            // CLASS_PROTOTYPE( idBeam );
            private val eventCallbacks: MutableMap<idEventDef, eventCallback_t<*>> = HashMap()
            fun getEventCallBacks(): MutableMap<idEventDef, eventCallback_t<*>> {
                return eventCallbacks
            }

            init {
                eventCallbacks.putAll(idEntity.getEventCallBacks())
                eventCallbacks[EV_PostSpawn] = eventCallback_t0<idBeam> { obj: idBeam -> obj.Event_MatchTarget() }
                eventCallbacks[EV_Activate] =
                    eventCallback_t1<idBeam> { obj: idBeam, activator: idEventArg<*>? -> obj.Event_Activate(activator as idEventArg<idEntity>) }
            }
        }

        private val master: idEntityPtr<idBeam>
        private val target: idEntityPtr<idBeam>
        override fun Spawn() {
            super.Spawn()
            val width = CFloat()
            if (spawnArgs.GetFloat("width", "0", width)) {
                renderEntity!!.shaderParms[RenderWorld.SHADERPARM_BEAM_WIDTH] = width._val
            }
            SetModel("_BEAM")
            Hide()
            PostEventMS(EV_PostSpawn, 0)
        }

        override fun Save(savefile: idSaveGame) {
            super.Save(savefile)
            target.Save(savefile)
            master.Save(savefile)
        }

        override fun Restore(savefile: idRestoreGame) {
            super.Restore(savefile)
            target.Restore(savefile)
            master.Restore(savefile)
        }

        override fun Think() {
            val masterEnt: idBeam?
            if (!IsHidden() && null == target.GetEntity()) {
                // hide if our target is removed
                Hide()
            }
            RunPhysics()
            masterEnt = master.GetEntity()
            if (masterEnt != null) {
                val origin = GetPhysics().GetOrigin()
                masterEnt.SetBeamTarget(origin)
            }
            Present()
        }

        fun SetMaster(masterbeam: idBeam?) {
            master.oSet(masterbeam)
        }

        fun SetBeamTarget(origin: idVec3) {
            if (renderEntity!!.shaderParms[RenderWorld.SHADERPARM_BEAM_END_X] != origin.x || renderEntity!!.shaderParms[RenderWorld.SHADERPARM_BEAM_END_Y] != origin.y || renderEntity!!.shaderParms[RenderWorld.SHADERPARM_BEAM_END_Z] != origin.z) {
                renderEntity!!.shaderParms[RenderWorld.SHADERPARM_BEAM_END_X] = origin.x
                renderEntity!!.shaderParms[RenderWorld.SHADERPARM_BEAM_END_Y] = origin.y
                renderEntity!!.shaderParms[RenderWorld.SHADERPARM_BEAM_END_Z] = origin.z
                UpdateVisuals()
            }
        }

        override fun Show() {
            val targetEnt: idBeam?
            super.Show()
            targetEnt = target.GetEntity()
            if (targetEnt != null) {
                val origin = targetEnt.GetPhysics().GetOrigin()
                SetBeamTarget(origin)
            }
        }

        override fun WriteToSnapshot(msg: idBitMsgDelta) {
            GetPhysics().WriteToSnapshot(msg)
            WriteBindToSnapshot(msg)
            WriteColorToSnapshot(msg)
            msg.WriteFloat(renderEntity!!.shaderParms[RenderWorld.SHADERPARM_BEAM_END_X])
            msg.WriteFloat(renderEntity!!.shaderParms[RenderWorld.SHADERPARM_BEAM_END_Y])
            msg.WriteFloat(renderEntity!!.shaderParms[RenderWorld.SHADERPARM_BEAM_END_Z])
        }

        override fun ReadFromSnapshot(msg: idBitMsgDelta) {
            GetPhysics().ReadFromSnapshot(msg)
            ReadBindFromSnapshot(msg)
            ReadColorFromSnapshot(msg)
            renderEntity!!.shaderParms[RenderWorld.SHADERPARM_BEAM_END_X] = msg.ReadFloat()
            renderEntity!!.shaderParms[RenderWorld.SHADERPARM_BEAM_END_Y] = msg.ReadFloat()
            renderEntity!!.shaderParms[RenderWorld.SHADERPARM_BEAM_END_Z] = msg.ReadFloat()
            if (msg.HasChanged()) {
                UpdateVisuals()
            }
        }

        private fun Event_MatchTarget() {
            var i: Int
            var targetEnt: idEntity?
            var targetBeam: idBeam?
            if (0 == targets.Num()) {
                return
            }
            targetBeam = null
            i = 0
            while (i < targets.Num()) {
                targetEnt = targets[i].GetEntity()
                if (targetEnt != null && targetEnt is idBeam) {
                    targetBeam = targetEnt
                    break
                }
                i++
            }
            if (null == targetBeam) {
                idGameLocal.Error("Could not find valid beam target for '%s'", name)
                return
            }
            target.oSet(targetBeam)
            targetBeam.SetMaster(this)
            if (!spawnArgs.GetBool("start_off")) {
                Show()
            }
        }

        private fun Event_Activate(activator: idEventArg<idEntity>) {
            if (IsHidden()) {
                Show()
            } else {
                Hide()
            }
        }

        override fun CreateInstance(): idClass = idBeam()

        override fun GetType(): idTypeInfo = Type

        override fun getEventCallBack(event: idEventDef): eventCallback_t<*>? {
            return eventCallbacks[event]
        }

        //
        //
        init {
            target = idEntityPtr()
            master = idEntityPtr()
        }
    }

    /*
     ===============================================================================

     idLiquid

     ===============================================================================
     */
    class idLiquid : idEntity() {
        companion object {
            val Type = idTypeInfo("idLiquid", "idEntity") { idLiquid() }

            // CLASS_PROTOTYPE( idLiquid );
            private val eventCallbacks: MutableMap<idEventDef, eventCallback_t<*>> = HashMap()

            //
            //
            fun getEventCallBacks(): MutableMap<idEventDef, eventCallback_t<*>> {
                return eventCallbacks
            }

            init {
                eventCallbacks.putAll(idEntity.getEventCallBacks())
                eventCallbacks[EV_Touch] =
                    eventCallback_t2<idLiquid> { obj: idLiquid, other: idEventArg<*>?, trace: idEventArg<*>? ->
                        obj.Event_Touch(
                            other as idEventArg<idEntity>, trace as idEventArg<trace_s?>
                        )
                    }
            }
        }

        private val model: idRenderModelLiquid? = null

        override fun Restore(savefile: idRestoreGame) {
            super.Restore(savefile)
            //FIXME: NO!
            Spawn()
        }

        private fun Event_Touch(other: idEventArg<idEntity>, trace: idEventArg<trace_s?>?) {
            // FIXME: for QuakeCon
            /*
                         idVec3 pos;

                         pos = other->GetPhysics()->GetOrigin() - GetPhysics()->GetOrigin();
                         model->IntersectBounds( other->GetPhysics()->GetBounds().Translate( pos ), -10.0f );
                         */
        }

        override fun CreateInstance(): idClass = idLiquid()

        override fun GetType(): idTypeInfo = Type

        override fun getEventCallBack(event: idEventDef): eventCallback_t<*>? {
            return eventCallbacks[event]
        }
    }

    /*
     ===============================================================================

     idShaking

     ===============================================================================
     */
    class idShaking : idEntity() {
        companion object {
            val Type = idTypeInfo("idShaking", "idEntity") { idShaking() }

            // CLASS_PROTOTYPE( idShaking );
            private val eventCallbacks: MutableMap<idEventDef, eventCallback_t<*>> = HashMap()
            fun getEventCallBacks(): MutableMap<idEventDef, eventCallback_t<*>> {
                return eventCallbacks
            }

            init {
                eventCallbacks.putAll(idEntity.getEventCallBacks())
                eventCallbacks[EV_Activate] = eventCallback_t1<idShaking> { obj: idShaking, activator: idEventArg<*>? ->
                    obj.Event_Activate(
                        activator as idEventArg<idEntity>
                    )
                }
            }
        }

        private val physicsObj: idPhysics_Parametric
        private var active: Boolean
        override fun Spawn() {
            super.Spawn()
            physicsObj.SetSelf(this)
            physicsObj.SetClipModel(idClipModel(GetPhysics().GetClipModel()!!), 1.0f)
            physicsObj.SetOrigin(GetPhysics().GetOrigin())
            physicsObj.SetAxis(GetPhysics().GetAxis())
            physicsObj.SetClipMask(Game_local.MASK_SOLID)
            SetPhysics(physicsObj)
            active = false
            if (!spawnArgs.GetBool("start_off")) {
                BeginShaking()
            }
        }

        override fun Save(savefile: idSaveGame) {
            super.Save(savefile)
            savefile.WriteBool(active)
            savefile.WriteStaticObject(physicsObj)
        }

        override fun Restore(savefile: idRestoreGame) {
            super.Restore(savefile)
            val active = CBool()
            savefile.ReadBool(active)
            savefile.ReadStaticObject(physicsObj)
            RestorePhysics(physicsObj)
            this.active = active._val
        }

        private fun BeginShaking() {
            val phase: Int
            val shake: idAngles?
            val period: Int
            active = true
            phase = gameLocal.random.RandomInt(1000)
            shake = spawnArgs.GetAngles("shake", "0.5f 0.5f 0.5f")
            period = (spawnArgs.GetFloat("period", "0.05") * 1000).toInt()
            physicsObj.SetAngularExtrapolation(
                Extrapolate.EXTRAPOLATION_DECELSINE or Extrapolate.EXTRAPOLATION_NOSTOP,
                phase,
                (period * 0.25f).toInt(),
                GetPhysics().GetAxis().ToAngles(),
                shake,
                ang_zero
            )
        }

        private fun Event_Activate(activator: idEventArg<idEntity>) {
            if (!active) {
                BeginShaking()
            } else {
                active = false
                physicsObj.SetAngularExtrapolation(
                    Extrapolate.EXTRAPOLATION_NONE, 0, 0, physicsObj.GetAxis().ToAngles(), ang_zero, ang_zero
                )
            }
        }

        override fun CreateInstance(): idClass = idShaking()

        override fun GetType(): idTypeInfo = Type

        override fun getEventCallBack(event: idEventDef): eventCallback_t<*>? {
            return eventCallbacks[event]
        }

        //
        //
        init {
            physicsObj = idPhysics_Parametric()
            active = false
        }
    }

    /*
     ===============================================================================

     idEarthQuake

     ===============================================================================
     */
    class idEarthQuake     //
    //
        : idEntity() {
        companion object {
            val Type = idTypeInfo("idEarthQuake", "idEntity") { idEarthQuake() }

            // CLASS_PROTOTYPE( idEarthQuake );
            private val eventCallbacks: MutableMap<idEventDef, eventCallback_t<*>> = HashMap()
            fun getEventCallBacks(): MutableMap<idEventDef, eventCallback_t<*>> {
                return eventCallbacks
            }

            init {
                eventCallbacks.putAll(idEntity.getEventCallBacks())
                eventCallbacks[EV_Activate] =
                    eventCallback_t1<idEarthQuake> { obj: idEarthQuake, _activator: idEventArg<*>? ->
                        obj.Event_Activate(_activator as idEventArg<idEntity>)
                    }
            }
        }

        private var disabled = false
        private var nextTriggerTime = 0
        private var playerOriented = false
        private var random = 0.0f
        private var shakeStopTime = 0
        private var shakeTime = 0.0f
        private var triggered = false
        private var wait = 0.0f
        override fun Spawn() {
            super.Spawn()
            nextTriggerTime = 0
            shakeStopTime = 0
            wait = spawnArgs.GetFloat("wait", "15")
            random = spawnArgs.GetFloat("random", "5")
            triggered = spawnArgs.GetBool("triggered")
            playerOriented = spawnArgs.GetBool("playerOriented")
            disabled = false
            shakeTime = spawnArgs.GetFloat("shakeTime", "0")
            if (!triggered) {
                PostEventSec(EV_Activate, spawnArgs.GetFloat("wait"), this)
            }
            BecomeInactive(TH_THINK)
        }

        override fun Save(savefile: idSaveGame) {
            super.Save(savefile)
            savefile.WriteInt(nextTriggerTime)
            savefile.WriteInt(shakeStopTime)
            savefile.WriteFloat(wait)
            savefile.WriteFloat(random)
            savefile.WriteBool(triggered)
            savefile.WriteBool(playerOriented)
            savefile.WriteBool(disabled)
            savefile.WriteFloat(shakeTime)
        }

        override fun Restore(savefile: idRestoreGame) {
            super.Restore(savefile)
            val nextTriggerTime = CInt()
            val shakeStopTime = CInt()
            val wait = CFloat()
            val random = CFloat()
            val shakeTime = CFloat()
            val triggered = CBool(false)
            val playerOriented = CBool(false)
            val disabled = CBool(false)
            savefile.ReadInt(nextTriggerTime)
            savefile.ReadInt(shakeStopTime)
            savefile.ReadFloat(wait)
            savefile.ReadFloat(random)
            savefile.ReadBool(triggered)
            savefile.ReadBool(playerOriented)
            savefile.ReadBool(disabled)
            savefile.ReadFloat(shakeTime)
            this.nextTriggerTime = nextTriggerTime._val
            this.shakeStopTime = shakeStopTime._val
            this.wait = wait._val
            this.random = random._val
            this.triggered = triggered._val
            this.playerOriented = playerOriented._val
            this.disabled = disabled._val
            this.shakeTime = shakeTime._val
            if (shakeStopTime._val > gameLocal.time) {
                BecomeActive(TH_THINK)
            }
        }

        override fun Think() {
            if ((thinkFlags and TH_THINK) != 0) {
                if (gameLocal.time > shakeStopTime) {
                    BecomeInactive(TH_THINK)
                    if (wait <= 0.0f) {
                        PostEventMS(EV_Remove, 0)
                    }
                    return
                }
                val shakeVolume = Game_local.gameSoundWorld!!.CurrentShakeAmplitudeForPosition(
                    gameLocal.time, gameLocal.GetLocalPlayer()!!.firstPersonViewOrigin
                )
                gameLocal.RadiusPush(
                    GetPhysics().GetOrigin(), 256.0f, 1500 * shakeVolume, this, this, 1.0f, true
                )
            }
            BecomeInactive(TH_UPDATEVISUALS)
        }

        private fun Event_Activate(_activator: idEventArg<idEntity>) {
            val activator = _activator.value
            if (nextTriggerTime > gameLocal.time) {
                return
            }
            if (disabled && activator === this) {
                return
            }
            val player = gameLocal.GetLocalPlayer() ?: return
            nextTriggerTime = 0
            if (!triggered && activator !== this) {
                // if we are not triggered ( i.e. random ), disable or enable
                disabled = disabled xor true //1;
                if (disabled) {
                    return
                } else {
                    PostEventSec(EV_Activate, wait + random * gameLocal.random.CRandomFloat(), this)
                }
            }
            ActivateTargets(activator)
            val shader = declManager.FindSound(spawnArgs.GetString("snd_quake"))
            if (playerOriented) {
                player.StartSoundShader(shader, gameSoundChannel_t.SND_CHANNEL_ANY.ordinal, SSF_GLOBAL, false)
            } else {
                StartSoundShader(shader, gameSoundChannel_t.SND_CHANNEL_ANY.ordinal, SSF_GLOBAL, false)
            }
            if (shakeTime > 0.0f) {
                shakeStopTime = (gameLocal.time + SEC2MS(shakeTime))
                BecomeActive(TH_THINK)
            }
            if (wait > 0.0f) {
                if (!triggered) {
                    PostEventSec(EV_Activate, wait + random * gameLocal.random.CRandomFloat(), this)
                } else {
                    nextTriggerTime = (gameLocal.time + SEC2MS(wait + random * gameLocal.random.CRandomFloat()))
                }
            } else if (shakeTime == 0.0f) {
                PostEventMS(EV_Remove, 0)
            }
        }

        override fun CreateInstance(): idClass = idEarthQuake()

        override fun GetType(): idTypeInfo = Type

        override fun getEventCallBack(event: idEventDef): eventCallback_t<*>? {
            return eventCallbacks[event]
        }
    }

    /*
     ===============================================================================

     idFuncPortal

     ===============================================================================
     */
    class idFuncPortal : idEntity() {
        companion object {
            val Type = idTypeInfo("idFuncPortal", "idEntity") { idFuncPortal() }

            // CLASS_PROTOTYPE( idFuncPortal );
            private val eventCallbacks: MutableMap<idEventDef, eventCallback_t<*>> = HashMap()
            fun getEventCallBacks(): MutableMap<idEventDef, eventCallback_t<*>> {
                return eventCallbacks
            }

            init {
                eventCallbacks.putAll(idEntity.getEventCallBacks())
                eventCallbacks[EV_Activate] =
                    eventCallback_t1<idFuncPortal> { obj: idFuncPortal, activator: idEventArg<*>? ->
                        obj.Event_Activate(activator as idEventArg<idEntity>)
                    }
            }
        }

        private val   /*qhandle_t*/portal: CInt = CInt()
        private val state: CBool = CBool()
        override fun Spawn() {
            super.Spawn()
            portal._val = (gameRenderWorld!!.FindPortal(GetPhysics().GetAbsBounds().Expand(32.0f)))
            if (portal._val > 0) {
                state._val = (spawnArgs.GetBool("start_on"))
                gameLocal.SetPortalState(
                    portal._val,
                    (if (state._val) portalConnection_t.PS_BLOCK_ALL else portalConnection_t.PS_BLOCK_NONE).ordinal
                )
            }
        }

        override fun Save(savefile: idSaveGame) {
            super.Save(savefile)
            savefile.WriteInt(portal._val)
            savefile.WriteBool(state._val)
        }

        override fun Restore(savefile: idRestoreGame) {
            super.Restore(savefile)
            savefile.ReadInt(portal)
            savefile.ReadBool(state)
            gameLocal.SetPortalState(
                portal._val,
                (if (state._val) portalConnection_t.PS_BLOCK_ALL else portalConnection_t.PS_BLOCK_NONE).ordinal
            )
        }

        private fun Event_Activate(activator: idEventArg<idEntity>) {
            if (portal._val > 0) {
                state._val = (!state._val)
                gameLocal.SetPortalState(
                    portal._val,
                    (if (state._val) portalConnection_t.PS_BLOCK_ALL else portalConnection_t.PS_BLOCK_NONE).ordinal
                )
            }
        }

        override fun CreateInstance(): idClass = idFuncPortal()

        override fun GetType(): idTypeInfo = Type

        override fun getEventCallBack(event: idEventDef): eventCallback_t<*>? {
            return eventCallbacks[event]
        }

        //
        //
        init {
            portal._val = (0)
            state._val = (false)
        }
    }

    /*
     ===============================================================================

     idFuncAASPortal

     ===============================================================================
     */
    class idFuncAASPortal     //
    //
        : idEntity() {
        companion object {
            val Type = idTypeInfo("idFuncAASPortal", "idEntity") { idFuncAASPortal() }

            // CLASS_PROTOTYPE( idFuncAASPortal );
            private val eventCallbacks: MutableMap<idEventDef, eventCallback_t<*>> = HashMap()
            fun getEventCallBacks(): MutableMap<idEventDef, eventCallback_t<*>> {
                return eventCallbacks
            }

            init {
                eventCallbacks.putAll(idEntity.getEventCallBacks())
                eventCallbacks[EV_Activate] =
                    eventCallback_t1<idFuncAASPortal> { obj: idFuncAASPortal, activator: idEventArg<*>? ->
                        obj.Event_Activate(activator as idEventArg<idEntity>)
                    }
            }
        }

        private var state = false
        override fun Spawn() {
            super.Spawn()
            state = spawnArgs.GetBool("start_on")
            gameLocal.SetAASAreaState(GetPhysics().GetAbsBounds(), AASFile.AREACONTENTS_CLUSTERPORTAL, state)
        }

        override fun Save(savefile: idSaveGame) {
            super.Save(savefile)
            savefile.WriteBool(state)
        }

        override fun Restore(savefile: idRestoreGame) {
            super.Restore(savefile)
            val state = CBool()
            savefile.ReadBool(state)
            gameLocal.SetAASAreaState(
                GetPhysics().GetAbsBounds(), AASFile.AREACONTENTS_CLUSTERPORTAL, state._val.also { this.state = it })
        }

        private fun Event_Activate(activator: idEventArg<idEntity>) {
            state = state xor true //1;
            gameLocal.SetAASAreaState(GetPhysics().GetAbsBounds(), AASFile.AREACONTENTS_CLUSTERPORTAL, state)
        }

        override fun CreateInstance(): idClass = idFuncAASPortal()

        override fun GetType(): idTypeInfo = Type

        override fun getEventCallBack(event: idEventDef): eventCallback_t<*>? {
            return eventCallbacks[event]
        }
    }

    /*
     ===============================================================================

     idFuncAASObstacle

     ===============================================================================
     */
    class idFuncAASObstacle : idEntity() {
        companion object {
            val Type = idTypeInfo("idFuncAASObstacle", "idEntity") { idFuncAASObstacle() }

            // CLASS_PROTOTYPE( idFuncAASObstacle );
            private val eventCallbacks: MutableMap<idEventDef, eventCallback_t<*>> = HashMap()
            fun getEventCallBacks(): MutableMap<idEventDef, eventCallback_t<*>> {
                return eventCallbacks
            }

            init {
                eventCallbacks.putAll(idEntity.getEventCallBacks())
                eventCallbacks[EV_Activate] =
                    eventCallback_t1<idFuncAASObstacle> { obj: idFuncAASObstacle, activator: idEventArg<*>? ->
                        obj.Event_Activate(activator as idEventArg<idEntity>)
                    }
            }
        }

        private val state: CBool = CBool(false)
        override fun Spawn() {
            super.Spawn()
            state._val = (spawnArgs.GetBool("start_on"))
            gameLocal.SetAASAreaState(
                GetPhysics().GetAbsBounds(), AASFile.AREACONTENTS_OBSTACLE, state._val
            )
        }

        override fun Save(savefile: idSaveGame) {
            super.Save(savefile)
            savefile.WriteBool(state._val)
        }

        override fun Restore(savefile: idRestoreGame) {
            super.Restore(savefile)
            savefile.ReadBool(state)
            gameLocal.SetAASAreaState(
                GetPhysics().GetAbsBounds(), AASFile.AREACONTENTS_OBSTACLE, state._val
            )
        }

        private fun Event_Activate(activator: idEventArg<idEntity>) {
            state._val = (state._val xor true)
            gameLocal.SetAASAreaState(
                GetPhysics().GetAbsBounds(), AASFile.AREACONTENTS_OBSTACLE, state._val
            )
        }

        override fun CreateInstance(): idClass = idFuncAASObstacle()

        override fun GetType(): idTypeInfo = Type

        override fun getEventCallBack(event: idEventDef): eventCallback_t<*>? {
            return eventCallbacks[event]
        }

        //
        //
        init {
            state._val = (false)
        }
    }

    class idFuncRadioChatter     //
    //
        : idEntity() {
        companion object {
            val Type = idTypeInfo("idFuncRadioChatter", "idEntity") { idFuncRadioChatter() }

            // CLASS_PROTOTYPE( idFuncRadioChatter );
            private val eventCallbacks: MutableMap<idEventDef, eventCallback_t<*>> = HashMap()
            fun getEventCallBacks(): MutableMap<idEventDef, eventCallback_t<*>> {
                return eventCallbacks
            }

            init {
                eventCallbacks.putAll(idEntity.getEventCallBacks())
                eventCallbacks[EV_Activate] =
                    eventCallback_t1<idFuncRadioChatter> { obj: idFuncRadioChatter, activator: idEventArg<*>? ->
                        obj.Event_Activate(activator as idEventArg<idEntity>)
                    }
                eventCallbacks[EV_ResetRadioHud] =
                    eventCallback_t1<idFuncRadioChatter> { obj: idFuncRadioChatter, _activator: idEventArg<*>? ->
                        obj.Event_ResetRadioHud(_activator as idEventArg<idEntity>)
                    }
            }
        }

        private var time = 0.0f
        override fun Spawn() {
            super.Spawn()
            time = spawnArgs.GetFloat("time", "5.0f")
        }

        override fun Save(savefile: idSaveGame) {
            super.Save(savefile)
            savefile.WriteFloat(time)
        }

        override fun Restore(savefile: idRestoreGame) {
            super.Restore(savefile)
            val time = CFloat()
            savefile.ReadFloat(time)
            this.time = time._val
        }

        private fun Event_Activate(activator: idEventArg<idEntity>) {
            val player: idPlayer?
            val sound: String?
            val shader: idSoundShader?
            val length = CInt()
            player = if (activator.value is idPlayer) {
                activator.value as idPlayer
            } else {
                gameLocal.GetLocalPlayer()!!
            }
            player.hud!!.HandleNamedEvent("radioChatterUp")
            sound = spawnArgs.GetString("snd_radiochatter", "")
            if (sound != null && !sound.isEmpty()) {
                shader = declManager.FindSound(sound)
                player.StartSoundShader(shader, gameSoundChannel_t.SND_CHANNEL_RADIO, SSF_GLOBAL, false, length)
                time = MS2SEC((length._val + 150).toFloat())
            }
            // we still put the hud up because this is used with no sound on
            // certain frame commands when the chatter is triggered
            PostEventSec(EV_ResetRadioHud, time, player)
        }

        private fun Event_ResetRadioHud(_activator: idEventArg<idEntity>) {
            val activator = _activator.value
            val player = if (activator is idPlayer) activator else gameLocal.GetLocalPlayer()!!
            player.hud!!.HandleNamedEvent("radioChatterDown")
            ActivateTargets(activator)
        }

        override fun CreateInstance(): idClass = idFuncRadioChatter()

        override fun GetType(): idTypeInfo = Type

        override fun getEventCallBack(event: idEventDef): eventCallback_t<*>? {
            return eventCallbacks[event]
        }
    }

    /*
     ===============================================================================

     idPhantomObjects

     ===============================================================================
     */
    class idPhantomObjects : idEntity() {
        companion object {
            val Type = idTypeInfo("idPhantomObjects", "idEntity") { idPhantomObjects() }

            // CLASS_PROTOTYPE( idPhantomObjects );
            private val eventCallbacks: MutableMap<idEventDef, eventCallback_t<*>> = HashMap()
            fun getEventCallBacks(): MutableMap<idEventDef, eventCallback_t<*>> {
                return eventCallbacks
            }

            init {
                eventCallbacks.putAll(idEntity.getEventCallBacks())
                eventCallbacks[EV_Activate] =
                    eventCallback_t1<idPhantomObjects> { obj: idPhantomObjects, _activator: idEventArg<*>? ->
                        obj.Event_Activate(_activator as idEventArg<idEntity?>)
                    }
            }
        }

        private val lastTargetPos: List.idList<idVec3>
        private val target: idEntityPtr<idActor?> = idEntityPtr()
        private val targetTime: List.idList<Int>
        private var end_time = 0
        private var max_wait: Int
        private var min_wait: Int
        private val shake_ang: idVec3
        private var shake_time = 0.0f
        private var speed: Float
        private var throw_time = 0.0f
        override fun Spawn() {
            super.Spawn()
            throw_time = spawnArgs.GetFloat("time", "5")
            speed = spawnArgs.GetFloat("speed", "1200")
            shake_time = spawnArgs.GetFloat("shake_time", "1")
            throw_time -= shake_time
            if (throw_time < 0.0f) {
                throw_time = 0.0f
            }
            min_wait = SEC2MS(spawnArgs.GetFloat("min_wait", "1"))
            max_wait = SEC2MS(spawnArgs.GetFloat("max_wait", "3"))
            shake_ang.set(spawnArgs.GetVector("shake_ang", "65 65 65"))
            Hide()
            GetPhysics().SetContents(0)
        }

        override fun Save(savefile: idSaveGame) {
            super.Save(savefile)
            var i: Int
            savefile.WriteInt(end_time)
            savefile.WriteFloat(throw_time)
            savefile.WriteFloat(shake_time)
            savefile.WriteVec3(shake_ang)
            savefile.WriteFloat(speed)
            savefile.WriteInt(min_wait)
            savefile.WriteInt(max_wait)
            target.Save(savefile)
            savefile.WriteInt(targetTime.Num())
            i = 0
            while (i < targetTime.Num()) {
                savefile.WriteInt(targetTime[i])
                i++
            }
            i = 0
            while (i < lastTargetPos.Num()) {
                savefile.WriteVec3(lastTargetPos[i])
                i++
            }
        }

        override fun Restore(savefile: idRestoreGame) {
            super.Restore(savefile)
            val num: Int
            var i: Int
            end_time = savefile.ReadInt()
            throw_time = savefile.ReadFloat()
            shake_time = savefile.ReadFloat()
            savefile.ReadVec3(shake_ang)
            speed = savefile.ReadFloat()
            min_wait = savefile.ReadInt()
            max_wait = savefile.ReadInt()
            target.Restore(savefile)

            num = savefile.ReadInt()
            targetTime.SetGranularity(1)
            targetTime.SetNum(num)
            lastTargetPos.SetGranularity(1)
            lastTargetPos.SetNum(num)
            i = 0
            while (i < num) {
                targetTime[i] = savefile.ReadInt()
                i++
            }
            if (savefile.GetBuildNumber() == SaveGame.INITIAL_RELEASE_BUILD_NUMBER) {
                // these weren't saved out in the first release
                i = 0
                while (i < num) {
                    lastTargetPos[i].Zero()
                    i++
                }
            } else {
                i = 0
                while (i < num) {
                    savefile.ReadVec3(lastTargetPos[i])
                    i++
                }
            }
        }

        override fun Think() {
            var i: Int
            var num: Int
            var time: Float
            val vel = idVec3()
            val ang = idVec3()
            var ent: idEntity?
            val targetEnt: idActor?
            var entPhys: idPhysics?
            val tr = trace_s()

            // if we are completely closed off from the player, don't do anything at all
            if (CheckDormant()) {
                return
            }
            if (0 == (thinkFlags and TH_THINK)) {
                BecomeInactive(thinkFlags and TH_THINK.inv())
                return
            }
            targetEnt = target.GetEntity()
            if (null == targetEnt || targetEnt.health <= 0 || end_time != 0 && gameLocal.time > end_time || gameLocal.inCinematic) {
                BecomeInactive(TH_THINK)
            }
            val toPos = targetEnt!!.GetEyePosition()
            num = 0
            i = 0
            while (i < targets.Num()) {
                ent = targets[i].GetEntity()
                if (null == ent) {
                    i++
                    continue
                }
                if (ent.fl.hidden) {
                    // don't throw hidden objects
                    i++
                    continue
                }
                if (0 == targetTime[i]) {
                    // already threw this object
                    i++
                    continue
                }
                num++
                time = MS2SEC((targetTime[i] - gameLocal.time).toFloat())
                if (time > shake_time) {
                    i++
                    continue
                }
                entPhys = ent.GetPhysics()
                val entOrg = entPhys.GetOrigin()
                gameLocal.clip.TracePoint(tr, entOrg, toPos, Game_local.MASK_OPAQUE, ent)
                if (tr.fraction >= 1.0f || gameLocal.GetTraceEntity(tr) == targetEnt) {
                    lastTargetPos[i] = toPos
                }
                if (time < 0.0f) {
                    idAI.PredictTrajectory(
                        entPhys.GetOrigin(),
                        lastTargetPos[i],
                        speed,
                        entPhys.GetGravity(),
                        entPhys.GetClipModel()!!,
                        entPhys.GetClipMask(),
                        256.0f,
                        ent,
                        targetEnt,
                        if (SysCvar.ai_debugTrajectory.GetBool()) 1 else 0,
                        vel
                    )
                    vel.timesAssign(speed)
                    entPhys.SetLinearVelocity(vel)
                    if (0 == end_time) {
                        targetTime[i] = 0
                    } else {
                        targetTime[i] = gameLocal.time + gameLocal.random.RandomInt(max_wait - min_wait) + min_wait
                    }
                    if (ent is idMoveable) {
                        val ment = ent as idMoveable
                        ment.EnableDamage(true, 2.5f)
                    }
                } else {
                    // this is not the right way to set the angular velocity, but the effect is nice, so I'm keeping it. :)
                    ang.set(
                        gameLocal.random.CRandomFloat() * shake_ang.x,
                        gameLocal.random.CRandomFloat() * shake_ang.y,
                        gameLocal.random.CRandomFloat() * shake_ang.z
                    )
                    ang.timesAssign(1.0f - time / shake_time)
                    entPhys.SetAngularVelocity(ang)
                }
                i++
            }
            if (0 == num) {
                BecomeInactive(TH_THINK)
            }
        }

        private fun Event_Activate(_activator: idEventArg<idEntity?>) {
            val activator = _activator.value
            var i: Int
            var time: Float
            var frac: Float
            val scale: Float
            if ((thinkFlags and TH_THINK) != 0) {
                BecomeInactive(TH_THINK)
                return
            }
            RemoveNullTargets()
            if (0 == targets.Num()) {
                return
            }
            if (null == activator || activator !is idActor) {
                target.oSet(gameLocal.GetLocalPlayer())
            } else {
                target.oSet(activator as idActor?)
            }
            end_time = (gameLocal.time + SEC2MS(spawnArgs.GetFloat("end_time", "0")))
            targetTime.SetNum(targets.Num())
            lastTargetPos.SetNum(targets.Num())
            val toPos = target.GetEntity()!!.GetEyePosition()

            // calculate the relative times of all the objects
            time = 0.0f
            i = 0
            while (i < targetTime.Num()) {
                targetTime[i] = SEC2MS(time)
                lastTargetPos[i] = toPos
                frac = 1.0f - i.toFloat() / targetTime.Num().toFloat()
                time += (gameLocal.random.RandomFloat() + 1.0f) * 0.5f * frac + 0.1f
                i++
            }

            // scale up the times to fit within throw_time
            scale = throw_time / time
            i = 0
            while (i < targetTime.Num()) {
                targetTime[i] = (gameLocal.time + SEC2MS(shake_time) + targetTime[i] * scale).toInt()
                i++
            }
            BecomeActive(TH_THINK)
        }

        //        private void Event_Throw();
        //
        //        private void Event_ShakeObject(idEntity object, int starttime);
        //
        override fun CreateInstance(): idClass = idPhantomObjects()

        override fun GetType(): idTypeInfo = Type

        override fun getEventCallBack(event: idEventDef): eventCallback_t<*>? {
            return eventCallbacks[event]
        }

        //
        //
        init {
            shake_ang = idVec3()
            speed = 0.0f
            min_wait = 0
            max_wait = 0
            fl.neverDormant = false
            targetTime = List.idList()
            lastTargetPos = List.idList()
        }
    }

    // D3XP
    class idShockwave : idEntity() {
        companion object {
            val Type = idTypeInfo("idShockwave", "idEntity") { idShockwave() }
            private val eventCallbacks: MutableMap<idEventDef, eventCallback_t<*>> = HashMap()
            fun getEventCallBacks(): MutableMap<idEventDef, eventCallback_t<*>> = eventCallbacks

            init {
                eventCallbacks.putAll(idEntity.getEventCallBacks())
                eventCallbacks[EV_Activate] =
                    eventCallback_t1<idShockwave> { obj: idShockwave, activator: idEventArg<*>? ->
                        obj.Event_Activate(activator as idEventArg<idEntity>)
                    }
            }
        }

        private var isActive: Boolean = false
        private var startTime: Int = 0
        private var duration: Int = 0
        private var startSize: Float = 0f
        private var endSize: Float = 0f
        private var currentSize: Float = 0f
        private var magnitude: Float = 0f
        private var height: Float = 0f
        private var playerDamaged: Boolean = false
        private var playerDamageSize: Float = 0f

        override fun Spawn() {
            duration = spawnArgs.GetInt("duration", "1000")
            startSize = spawnArgs.GetFloat("startsize", "8")
            endSize = spawnArgs.GetFloat("endsize", "512")
            magnitude = spawnArgs.GetFloat("magnitude", "100")
            height = spawnArgs.GetFloat("height", "0")
            playerDamageSize = spawnArgs.GetFloat("player_damage_size", "20")
            if (spawnArgs.GetBool("start_on")) {
                ProcessEvent(EV_Activate, this)
            }
        }

        override fun Save(savefile: idSaveGame) {
            super.Save(savefile)
            savefile.WriteBool(isActive)
            savefile.WriteInt(startTime)
            savefile.WriteInt(duration)
            savefile.WriteFloat(startSize)
            savefile.WriteFloat(endSize)
            savefile.WriteFloat(currentSize)
            savefile.WriteFloat(magnitude)
            savefile.WriteFloat(height)
            savefile.WriteBool(playerDamaged)
            savefile.WriteFloat(playerDamageSize)
        }

        override fun Restore(savefile: idRestoreGame) {
            super.Restore(savefile)
            isActive = savefile.ReadBool()
            startTime = savefile.ReadInt()
            duration = savefile.ReadInt()
            startSize = savefile.ReadFloat()
            endSize = savefile.ReadFloat()
            currentSize = savefile.ReadFloat()
            magnitude = savefile.ReadFloat()
            height = savefile.ReadFloat()
            playerDamaged = savefile.ReadBool()
            playerDamageSize = savefile.ReadFloat()
        }

        override fun Think() {
            if (!isActive) {
                BecomeInactive(TH_THINK)
                return
            }
            val endTime = startTime + duration
            if (gameLocal.time < endTime) {
                val u = (gameLocal.time - startTime).toFloat() / duration.toFloat()
                val newSize = startSize + u * (endSize - startSize)
                val pos = idVec3(GetPhysics().GetOrigin())
                val zVal = if (height == 0f) newSize else height / 2.0f

                val bounds = idBounds(pos.plus(idVec3(newSize, newSize, zVal)))
                bounds.AddPoint(pos.plus(idVec3(-newSize, -newSize, -zVal)))

                if (SysCvar.g_debugShockwave.GetBool()) {
                    gameRenderWorld!!.DebugBounds(colorRed, bounds, vec3_origin, 0)
                }

                val clipModelList = arrayOfNulls<idClipModel>(MAX_GENTITIES)
                val listedClipModels = gameLocal.clip.ClipModelsTouchingBounds(bounds, -1, clipModelList, MAX_GENTITIES)

                for (i in 0 until listedClipModels) {
                    val clip = clipModelList[i] ?: continue
                    val ent = clip.GetEntity() ?: continue
                    if (ent.IsHidden()) continue
                    if (!ent.IsType(idMoveable.Type) && !ent.IsType(idAFEntity_Base.Type) && !ent.IsType(idPlayer.Type)) continue

                    val point = idVec3(ent.GetPhysics().GetOrigin())
                    val force = idVec3(point.minus(pos))
                    val dist = force.Normalize()

                    if (ent.IsType(idPlayer.Type)) {
                        if (ent.GetPhysics().GetAbsBounds().IntersectsBounds(bounds)) {
                            if (dist <= newSize && dist > newSize - playerDamageSize) {
                                val damageDef = spawnArgs.GetString("def_player_damage", "")
                                if (!damageDef.isNullOrEmpty() && !playerDamaged) {
                                    playerDamaged = true
                                    val player = ent as idPlayer
                                    val dir = idVec3(ent.GetPhysics().GetOrigin().minus(pos))
                                    dir.NormalizeFast()
                                    player.Damage(null, null, dir, damageDef, 1.0f, Model.INVALID_JOINT)
                                }
                            }
                        }
                    } else {
                        if (dist <= newSize && dist > currentSize) {
                            force.z += 4f
                            force.NormalizeFast()
                            val scaledForce = if (ent.IsType(idAFEntity_Base.Type)) {
                                force.times(ent.GetPhysics().GetMass() * magnitude * 0.01f)
                            } else {
                                force.times(ent.GetPhysics().GetMass() * magnitude)
                            }
                            val rad = ent.GetPhysics().GetBounds().GetRadius()
                            point.x += gameLocal.random.CRandomFloat() * rad
                            point.y += gameLocal.random.CRandomFloat() * rad
                            for (j in 0 until ent.GetPhysics().GetNumClipModels()) {
                                ent.GetPhysics().AddForce(j, point, scaledForce)
                            }
                        }
                    }
                }
                currentSize = newSize
            } else {
                isActive = false
            }
        }

        private fun Event_Activate(activator: idEventArg<idEntity>) {
            isActive = true
            startTime = gameLocal.time
            playerDamaged = false
            BecomeActive(TH_THINK)
        }

        override fun CreateInstance(): idClass = idShockwave()
        override fun GetType(): idTypeInfo = Type
        override fun getEventCallBack(event: idEventDef): eventCallback_t<*>? = eventCallbacks[event]
    }

    open class idFuncMountedObject : idEntity() {
        private var harc: Int = 0
        private var varc: Int = 0

        var isMounted: Boolean = false
        var scriptFunction: Script_Program.function_t? = null
        var mountedPlayer: idPlayer? = null


        companion object {
            val Type: idTypeInfo = idTypeInfo(
                "idFuncMountedObject", "idEntity"
            ) { idFuncMountedObject() }
            private val eventCallbacks: MutableMap<idEventDef, eventCallback_t<*>> = HashMap()
            fun getEventCallBacks(): MutableMap<idEventDef, eventCallback_t<*>> = eventCallbacks

            init {
                eventCallbacks.putAll(idEntity.getEventCallBacks())
                eventCallbacks[EV_Touch] =
                    eventCallback_t2<idFuncMountedObject> { obj: idFuncMountedObject, other: idEventArg<*>?, trace: idEventArg<*>? ->
                        obj.Event_Touch(
                            other as idEventArg<idEntity>, trace as idEventArg<trace_s>
                        )
                    }
                eventCallbacks[EV_Activate] =
                    eventCallback_t1<idFuncMountedObject> { obj: idFuncMountedObject, activator: idEventArg<*>? ->
                        obj.Event_Activate(activator as idEventArg<idEntity>)
                    }
            }
        }

        override fun Spawn() {
            // Get viewOffset
            harc = spawnArgs.GetInt("harc", "45")
            varc = spawnArgs.GetInt("varc", "30")

            // Get script function
            val funcName: idStr = idStr(spawnArgs.GetString("call", "")!!)
            if (funcName.Length() != 0) {
                scriptFunction = gameLocal.program.FindFunction(funcName)
                if (scriptFunction == null) {
                    gameLocal.Warning(
                        "idFuncMountedObject '%s' at (%s) calls unknown function '%s'\n",
                        name.c_str(),
                        GetPhysics().GetOrigin().ToString(0),
                        funcName.c_str()
                    )
                }
            }

            BecomeActive(TH_THINK)
        }

        fun GetAngleRestrictions(yaw_min: CInt, yaw_max: CInt, pitch: CInt) {
            val axis: idMat3 = idMat3()
            val angs: idAngles = idAngles()

            axis.set(GetPhysics().GetAxis())
            angs.set(axis.ToAngles())

            yaw_min._val = (angs.yaw - harc).toInt()
            yaw_min._val = AngleNormalize180(yaw_min._val.toFloat()).toInt()

            yaw_max._val = (angs.yaw + harc).toInt()
            yaw_max._val = AngleNormalize180(yaw_max._val.toFloat()).toInt()

            pitch._val = varc
        }

        /*
        ================
        idFuncMountedObject::Event_Touch
        ================
        */
        fun Event_Touch(other: idEventArg<idEntity>, trace: idEventArg<trace_s>) {
            ProcessEvent(EV_Activate, other)
        }

        /*
        ================
        idFuncMountedObject::Event_Activate
        ================
        */
        fun Event_Activate(activator: idEventArg<idEntity>) {
            if (!isMounted && activator.value.IsType(idPlayer.Type)) {
                var client: idPlayer = activator.value as idPlayer

                mountedPlayer = client

                mountedPlayer!!.Bind(this, true)
                mountedPlayer!!.mountedObject = this

                // Call a script function
                var mountthread: idThread
                if (scriptFunction != null) {
                    mountthread = idThread(scriptFunction!!)
                    mountthread.DelayedStart(0)
                }

                isMounted = true
            }
        }
    }

    class idFuncMountedWeapon : idFuncMountedObject() {
        companion object {
            val Type: idTypeInfo = idTypeInfo(
                "idFuncMountedWeapon", "idFuncMountedObject"
            ) { idFuncMountedWeapon() }
            private val eventCallbacks: MutableMap<idEventDef, eventCallback_t<*>> = HashMap()
            fun getEventCallBacks(): MutableMap<idEventDef, eventCallback_t<*>> = eventCallbacks

            init {
                eventCallbacks.putAll(idFuncMountedObject.getEventCallBacks())
                eventCallbacks[EV_PostSpawn] =
                    eventCallback_t1<idFuncMountedWeapon> { obj: idFuncMountedWeapon, activator: idEventArg<*>? ->
                        obj.Event_PostSpawn(activator as idEventArg<idEntity>)
                    }
            }
        }

        override fun Spawn() {

            // Get projectile info
            projectile = gameLocal.FindEntityDefDict(spawnArgs.GetString("def_projectile"), false)
            if (projectile == null) {
                gameLocal.Warning("Invalid projectile on func_mountedweapon.")
            }

            var firerate: Float = spawnArgs.GetFloat("firerate", "3")
            weaponFireDelay = 1000f / firerate

            // Get the firing sound
            val fireSound: idStr = idStr()
            spawnArgs.GetString("snd_fire", "", fireSound)
            soundFireWeapon = declManager.FindSound(fireSound)

            PostEventMS(EV_PostSpawn, 0)
        }

        override fun Think() {

            if (isMounted && turret != null) {
                val vec: idVec3 = mountedPlayer!!.viewAngles.ToForward()
                val ang: idAngles = mountedPlayer!!.GetLocalVector(vec).ToAngles()

                turret!!.GetPhysics().SetAxis(ang.ToMat3())
                turret!!.UpdateVisuals()

                // Check for firing
                if (mountedPlayer!!.usercmd.buttons.toInt() and BUTTON_ATTACK != 0 && (gameLocal.time > weaponLastFireTime + weaponFireDelay)) {
                    // FIRE!
                    val arrOfProj = arrayOfNulls<idEntity>(1)
                    gameLocal.SpawnEntityDef(projectile!!, arrOfProj)
                    var ent: idEntity? = arrOfProj[0]
                    val projBounds = idBounds()
                    val dir = idVec3()


                    if (ent == null || !ent.IsType(idProjectile.Type)) {
                        val projectileName = spawnArgs.GetString("def_projectile")
                        idGameLocal.Error("'%s' is not an idProjectile", projectileName)
                    }

                    mountedPlayer!!.GetViewPos(muzzleOrigin, muzzleAxis)

                    muzzleOrigin.plusAssign(muzzleAxis[0] * 128)
                    muzzleOrigin.minusAssign(muzzleAxis[2] * 20)

                    dir.set(muzzleAxis[0])

                    val proj = ent as idProjectile
                    proj.Create(this, muzzleOrigin, dir)

                    projBounds.set(proj.GetPhysics().GetBounds().Rotate(proj.GetPhysics().GetAxis()))

                    proj.Launch(muzzleOrigin, dir, vec3_origin)
                    StartSoundShader(
                        soundFireWeapon,
                        gameSoundChannel_t.SND_CHANNEL_WEAPON.ordinal,
                        SSF_GLOBAL,
                        false,
                        null
                    )

                    weaponLastFireTime = gameLocal.time.toFloat()
                }
            }

            super.Think()
        }


        // The actual turret that moves with the player's view
        private var turret: idEntity? = null

        // the muzzle bone's position, used for launching projectiles and trailing smoke
        private val muzzleOrigin = idVec3()
        private val muzzleAxis = idMat3()

        private var weaponLastFireTime: Float = 0f
        private var weaponFireDelay: Float = 0f

        var projectile: idDict? = null
        var soundFireWeapon: idSoundShader? = null

        fun Event_PostSpawn(activator: idEventArg<idEntity>) {

            if (targets.Num() >= 1) {
                for (i in 0 until targets.Num()) {
                    if (targets[i].GetEntity()!!.IsType(idStaticEntity.Type)) {
                        turret = targets[i].GetEntity()
                        break
                    }
                }
            } else {
                gameLocal.Warning("idFuncMountedWeapon::Spawn:  Please target one model for a turret\n")
            }
        }
    }

    // D3XP: portal sky entity
    class idPortalSky : idEntity() {
        companion object {
            private val eventCallbacks: MutableMap<idEventDef, eventCallback_t<*>> = HashMap()

            fun getEventCallBacks(): MutableMap<idEventDef, eventCallback_t<*>> {
                return eventCallbacks
            }

            val Type: idTypeInfo = idTypeInfo(
                "idPortalSky", "idEntity"
            ) { idPortalSky() }

            init {
                eventCallbacks.putAll(idEntity.getEventCallBacks())
                eventCallbacks[EV_PostSpawn] =
                    eventCallback_t0<idPortalSky> { obj: idPortalSky -> obj.Event_PostSpawn() }
                eventCallbacks[EV_Activate] =
                    eventCallback_t1<idPortalSky> { obj: idPortalSky, activator: idEventArg<*>? ->
                        obj.Event_Activate(activator as idEventArg<idEntity>)
                    }
            }
        }

        override fun Spawn() {
            super.Spawn()
            if (!spawnArgs.GetBool("triggered")) {
                PostEventMS(EV_PostSpawn, 1)
            }
        }

        private fun Event_PostSpawn() {
            gameLocal.SetPortalSkyEnt(this)
        }

        private fun Event_Activate(activator: idEventArg<idEntity>) {
            gameLocal.SetPortalSkyEnt(this)
        }

        override fun CreateInstance(): idClass = idPortalSky()
        override fun GetType(): idTypeInfo = Type
        override fun getEventCallBack(event: idEventDef): eventCallback_t<*>? = eventCallbacks[event]
    }
}
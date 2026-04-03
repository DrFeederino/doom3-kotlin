/*
 * Copyright (C) 1999-2011 id Software LLC, a ZeniMax Media company.
 * Translated to Kotlin by Dr. Feederino with support of Claude Code
 *
 * This file is part of the Doom 3 Kotlin project.
 * Original source: neo/Game/Entity.cpp, neo/Game/Entity.h
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

import neo.Game.Animation.Anim.jointModTransform_t
import neo.Game.Animation.idAnim
import neo.Game.Animation.idAnimator
import neo.Game.Game.refSound_t
import neo.Game.GameSys.Class.*
import neo.Game.GameSys.EV_Remove
import neo.Game.GameSys.Event.idEventDef
import neo.Game.GameSys.SaveGame.idRestoreGame
import neo.Game.GameSys.SaveGame.idSaveGame
import neo.Game.GameSys.SysCvar
import neo.Game.Game_local.*
import neo.Game.Game_local.Companion.gameRenderWorld
import neo.Game.Game_local.Companion.isD3XP
import neo.Game.Physics.Clip.idClipModel
import neo.Game.Physics.Physics.idPhysics
import neo.Game.Physics.Physics.impactInfo_s
import neo.Game.Physics.Physics_AF.idPhysics_AF
import neo.Game.Physics.Physics_Actor.idPhysics_Actor
import neo.Game.Physics.Physics_Parametric.idPhysics_Parametric
import neo.Game.Physics.Physics_Static.idPhysics_Static
import neo.Game.Player.idPlayer
import neo.Game.Pvs.pvsHandle_t
import neo.Game.Script.EV_Thread_Wait
import neo.Game.Script.EV_Thread_WaitFrame
import neo.Game.Script.Script_Program.function_t
import neo.Game.Script.Script_Program.idScriptObject
import neo.Game.Script.Script_Thread.idThread
import neo.Renderer.Material
import neo.Renderer.Material.surfTypes_t
import neo.Renderer.Model
import neo.Renderer.Model.dynamicModel_t
import neo.Renderer.ModelManager
import neo.Renderer.RenderWorld
import neo.Renderer.RenderWorld.deferredEntityCallback_t
import neo.Renderer.RenderWorld.renderEntity_s
import neo.Renderer.RenderWorld.renderView_s
import neo.Sound.snd_shader.idSoundShader
import neo.Sound.sound.idSoundEmitter
import neo.TempDump
import neo.TempDump.NiLLABLE
import neo.TempDump.SERiAL
import neo.cm.trace_s
import neo.framework.Async.NetworkSystem
import neo.framework.Common
import neo.framework.DeclEntityDef.idDeclEntityDef
import neo.framework.DeclManager
import neo.framework.DeclManager.declType_t
import neo.framework.DeclParticle.idDeclParticle
import neo.framework.DeclSkin.idDeclSkin
import neo.idlib.*
import neo.idlib.BV.idBounds
import neo.idlib.BitMsg.idBitMsg
import neo.idlib.BitMsg.idBitMsgDelta
import neo.idlib.Dict_h.idDict
import neo.idlib.Dict_h.idKeyValue
import neo.idlib.Text.Lexer.idLexer
import neo.idlib.Text.Str.idStr
import neo.idlib.Text.Str.va
import neo.idlib.Text.Token.idToken
import neo.idlib.containers.CBool
import neo.idlib.containers.CFloat
import neo.idlib.containers.CInt
import neo.idlib.containers.LinkList.idLinkList
import neo.idlib.containers.List.idList
import neo.idlib.geometry.JointTransform.idJointMat
import neo.idlib.geometry.TraceModel.idTraceModel
import neo.idlib.math.*
import neo.idlib.math.Matrix.idMat3
import neo.ui.UserInterface
import neo.ui.UserInterface.idUserInterface
import java.nio.ByteBuffer
import java.util.*
import kotlin.math.max

val EV_Activate: idEventDef = idEventDef("activate", "e")
val EV_ActivateTargets: idEventDef = idEventDef("activateTargets", "e")
val EV_Bind: idEventDef = idEventDef("bind", "e")
val EV_BindPosition: idEventDef = idEventDef("bindPosition", "e")
val EV_BindToJoint: idEventDef = idEventDef("bindToJoint", "esf")
val EV_CacheSoundShader: idEventDef = idEventDef("cacheSoundShader", "s")
val EV_CallFunction: idEventDef = idEventDef("callFunction", "s")
val EV_ClearAllJoints: idEventDef = idEventDef("clearAllJoints")
val EV_ClearJoint: idEventDef = idEventDef("clearJoint", "d")
val EV_ClearSignal: idEventDef = idEventDef("clearSignal", "d")
val EV_DistanceTo: idEventDef = idEventDef("distanceTo", "E", 'f')
val EV_DistanceToPoint: idEventDef = idEventDef("distanceToPoint", "v", 'f')
val EV_FadeSound: idEventDef = idEventDef("fadeSound", "dff")
val EV_FindTargets: idEventDef = idEventDef("<findTargets>", null)
val EV_GetAngles: idEventDef = idEventDef("getAngles", null, 'v')
val EV_GetAngularVelocity: idEventDef = idEventDef("getAngularVelocity", null, 'v')
val EV_GetColor: idEventDef = idEventDef("getColor", null, 'v')
val EV_GetEntityKey: idEventDef = idEventDef("getEntityKey", "s", 'e')
val EV_GetFloatKey: idEventDef = idEventDef("getFloatKey", "s", 'f')
val EV_GetIntKey: idEventDef = idEventDef("getIntKey", "s", 'f')
val EV_GetJointAngle: idEventDef = idEventDef("getJointAngle", "d", 'v')
val EV_GetJointHandle: idEventDef = idEventDef("getJointHandle", "s", 'd')
val EV_GetJointPos: idEventDef = idEventDef("getJointPos", "d", 'v')
val EV_GetKey: idEventDef = idEventDef("getKey", "s", 's')
val EV_GetLinearVelocity: idEventDef = idEventDef("getLinearVelocity", null, 'v')
val EV_GetMaxs: idEventDef = idEventDef("getMaxs", null, 'v')
val EV_GetMins: idEventDef = idEventDef("getMins", null, 'v')
val EV_GetName: idEventDef = idEventDef("getName", null, 's')
val EV_GetNextKey: idEventDef = idEventDef("getNextKey", "ss", 's')
val EV_GetOrigin: idEventDef = idEventDef("getOrigin", null, 'v')
val EV_GetShaderParm: idEventDef = idEventDef("getShaderParm", "d", 'f')
val EV_GetSize: idEventDef = idEventDef("getSize", null, 'v')
val EV_GetTarget: idEventDef = idEventDef("getTarget", "f", 'e')
val EV_GetVectorKey: idEventDef = idEventDef("getVectorKey", "s", 'v')
val EV_GetWorldOrigin: idEventDef = idEventDef("getWorldOrigin", null, 'v')
val EV_HasFunction: idEventDef = idEventDef("hasFunction", "s", 'd')
val EV_Hide: idEventDef = idEventDef("hide", null)
val EV_IsHidden: idEventDef = idEventDef("isHidden", null, 'd')
val EV_NumTargets: idEventDef = idEventDef("numTargets", null, 'f')
val EV_PostSpawn: idEventDef = idEventDef("<postspawn>", null)
val EV_RandomTarget: idEventDef = idEventDef("randomTarget", "s", 'e')
val EV_RemoveBinds: idEventDef = idEventDef("removeBinds")
val EV_RestorePosition: idEventDef = idEventDef("restorePosition")
val EV_SetAngles: idEventDef = idEventDef("setAngles", "v")
val EV_SetAngularVelocity: idEventDef = idEventDef("setAngularVelocity", "v")
val EV_SetColor: idEventDef = idEventDef("setColor", "fff")
val EV_SetGuiFloat: idEventDef = idEventDef("setGuiFloat", "sf")
val EV_SetGuiParm: idEventDef = idEventDef("setGuiParm", "ss")

// D3XP GUI events
val EV_SetGui: idEventDef = idEventDef("setGui", "ds")
val EV_PrecacheGui: idEventDef = idEventDef("precacheGui", "s")
val EV_GetGuiParm: idEventDef = idEventDef("getGuiParm", "ds", 's')
val EV_GetGuiParmFloat: idEventDef = idEventDef("getGuiParmFloat", "ds", 'f')
val EV_GuiNamedEvent: idEventDef = idEventDef("guiNamedEvent", "ds")

// D3XP motion blur events (no handler needed - used by animation frame commands only)
val EV_MotionBlurOn: idEventDef = idEventDef("motionBlurOn", null)
val EV_MotionBlurOff: idEventDef = idEventDef("motionBlurOff", null)
val EV_SetJointAngle: idEventDef = idEventDef("setJointAngle", "ddv")
val EV_SetJointPos: idEventDef = idEventDef("setJointPos", "ddv")
val EV_SetKey: idEventDef = idEventDef("setKey", "ss")
val EV_SetLinearVelocity: idEventDef = idEventDef("setLinearVelocity", "v")
val EV_SetModel: idEventDef = idEventDef("setModel", "s")
val EV_SetName: idEventDef = idEventDef("setName", "s")
val EV_SetNeverDormant: idEventDef = idEventDef("setNeverDormant", "d")
val EV_SetOrigin: idEventDef = idEventDef("setOrigin", "v")
val EV_SetOwner: idEventDef = idEventDef("setOwner", "e")
val EV_SetShaderParm: idEventDef = idEventDef("setShaderParm", "df")
val EV_SetShaderParms: idEventDef = idEventDef("setShaderParms", "ffff")
val EV_SetSize: idEventDef = idEventDef("setSize", "vv")
val EV_SetSkin: idEventDef = idEventDef("setSkin", "s")
val EV_SetWorldOrigin: idEventDef = idEventDef("setWorldOrigin", "v")
val EV_Show: idEventDef = idEventDef("show", null)
val EV_SpawnBind: idEventDef = idEventDef("<spawnbind>", null)
val EV_StartFx: idEventDef = idEventDef("startFx", "s")
val EV_StartSound: idEventDef = idEventDef("startSound", "sdd", 'f')
val EV_StartSoundShader: idEventDef = idEventDef("startSoundShader", "sd", 'f')
val EV_StopSound: idEventDef = idEventDef("stopSound", "dd")
val EV_Touch: idEventDef = idEventDef("<touch>", "et")
val EV_Touches: idEventDef = idEventDef("touches", "E", 'd')
val EV_Unbind: idEventDef = idEventDef("unbind", null)
val EV_UpdateCameraTarget: idEventDef = idEventDef("<updateCameraTarget>", null)

/*
     ===============================================================================

     Game entity base class.

     ===============================================================================
     */
const val DELAY_DORMANT_TIME = 3000

// FIXME: At some point we may want to just limit it to one thread per signal, but
// for now, I'm allowing multiple threads.  We should reevaluate this later in the project
const val MAX_SIGNAL_THREADS = 16 // probably overkill, but idList uses a granularity of 16

// Think flags
//enum {
const val TH_ALL = -1
const val TH_ANIMATE = 4 // update animation each frame
const val TH_PHYSICS = 2 // run physics each frame
const val TH_THINK = 1 // run think function each frame
const val TH_UPDATEPARTICLES = 16

//};
const val TH_UPDATEVISUALS = 8 // update renderEntity

/*
     ================
     UpdateGuiParms
     ================
     */
fun UpdateGuiParms(gui: idUserInterface?, args: idDict?) {
    if (gui == null || args == null) {
        return
    }
    var kv = args.MatchPrefix("gui_parm", null)
    while (kv != null) {
        gui.SetStateString(kv.GetKey().toString(), kv.GetValue().toString())
        kv = args.MatchPrefix("gui_parm", kv)
    }
    gui.SetStateBool("noninteractive", args.GetBool("gui_noninteractive"))
    gui.StateChanged(Game_local.gameLocal.time)
}

/*
     ================
     AddRenderGui
     ================
     */
fun AddRenderGui(name: String, args: idDict): idUserInterface? {
    val gui: idUserInterface?
    val kv = args.MatchPrefix("gui_parm", null)
    gui = UserInterface.uiManager.FindGui(name, true, kv != null)
    UpdateGuiParms(gui, args)
    return gui
}

//
// Signals
// make sure to change script/doom_defs.script if you add any, or change their order
//
enum class signalNum_t {
    SIG_TOUCH,  // object was touched
    SIG_USE,  // object was used
    SIG_TRIGGER,  // object was activated
    SIG_REMOVED,  // object was removed from the game
    SIG_DAMAGE,  // object was damaged
    SIG_BLOCKED,  // object was blocked

    //
    SIG_MOVER_POS1,  // mover at position 1 (door closed)
    SIG_MOVER_POS2,  // mover at position 2 (door open)
    SIG_MOVER_1TO2,  // mover changing from position 1 to 2
    SIG_MOVER_2TO1,  // mover changing from position 2 to 1

    //
    NUM_SIGNALS
}

class signal_t {
    var function: function_t? = null
    var threadnum = 0
}

class signalList_t {
    val signal: Array<idList<signal_t>> = Array(TempDump.etoi(signalNum_t.NUM_SIGNALS)) { idList() }
}

open class idEntity : idClass(), NiLLABLE<idEntity?>, SERiAL {

    companion object {
        val Type = idTypeInfo("idEntity", "idClass") { idEntity() }
        const val EVENT_MAXEVENTS = 2
        const val SERIAL_BYTES = 864

        //
        // enum {
        const val EVENT_STARTSOUNDSHADER = 0
        const val EVENT_STOPSOUNDSHADER = 1
        const val MAX_PVS_AREAS = 4

        //	ABSTRACT_PROTOTYPE( idEntity );
        private val eventCallbacks: MutableMap<idEventDef, eventCallback_t<*>> = HashMap()

        /* **********************************************************************

         Physics.

         ***********************************************************************/
        // physics
        // initialize the default physics
        fun getEventCallBacks(): MutableMap<idEventDef, eventCallback_t<*>> {
            return eventCallbacks
        }

        private fun Event_SetName(e: idEntity, newName: idEventArg<String>) {
            e.SetName(newName.value)
        }

        /*
         ============
         idEntity::Event_ActivateTargets

         Activates any entities targeted by this entity.  Mainly used as an
         event to delay activating targets.
         ============
         */
        private fun Event_ActivateTargets(e: idEntity, activator: idEventArg<idEntity>) {
            e.ActivateTargets(activator.value)
        }

        private fun Event_GetTarget(e: idEntity, index: idEventArg<Float>) {
            val i: Int
            i = index.value.toInt()
            if (i < 0 || i >= e.targets.Num()) {
                idThread.ReturnEntity(null)
            } else {
                idThread.ReturnEntity(e.targets[i].GetEntity())
            }
        }

        //
        private fun Event_RandomTarget(e: idEntity, ignor: idEventArg<String?>) {
            var num: Int
            var ent: idEntity?
            var i: Int
            var ignoreNum: Int
            val ignore = ignor.value
            e.RemoveNullTargets()
            if (0 == e.targets.Num()) {
                idThread.ReturnEntity(null)
                return
            }
            ignoreNum = -1
            if (ignore != null && !ignore.isEmpty() && e.targets.Num() > 1) {
                i = 0
                while (i < e.targets.Num()) {
                    ent = e.targets[i].GetEntity()
                    if (ent != null && ent.name.toString() == ignore) {
                        ignoreNum = i
                        break
                    }
                    i++
                }
            }
            if (ignoreNum >= 0) {
                num = Game_local.gameLocal.random.RandomInt(e.targets.Num() - 1)
                if (num >= ignoreNum) {
                    num++
                }
            } else {
                num = Game_local.gameLocal.random.RandomInt(e.targets.Num())
            }
            ent = e.targets[num].GetEntity()
            idThread.ReturnEntity(ent)
        }

        private fun Event_Bind(e: idEntity, master: idEventArg<idEntity>) {
            e.Bind(master.value, true)
        }

        private fun Event_BindPosition(e: idEntity, master: idEventArg<idEntity>) {
            e.Bind(master.value, false)
        }

        private fun Event_BindToJoint(
            e: idEntity,
            master: idEventArg<idEntity>,
            jointname: idEventArg<String>,
            orientated: idEventArg<Float>
        ) {
            e.BindToJoint(master.value, jointname.value, orientated.value != 0.0f)
        }

        private fun Event_SetOwner(e: idEntity, owner: idEventArg<idEntity>) {
            var i: Int
            i = 0
            while (i < e.GetPhysics().GetNumClipModels()) {
                e.GetPhysics().GetClipModel(i)!!.SetOwner(owner.value)
                i++
            }
        }

        private fun Event_SetModel(e: idEntity, modelname: idEventArg<String>) {
            e.SetModel(modelname.value)
        }

        private fun Event_SetSkin(e: idEntity, skinname: idEventArg<String>) {
            e.renderEntity!!.customSkin = DeclManager.declManager.FindSkin(skinname.value)
            e.UpdateVisuals()
        }

        private fun Event_GetShaderParm(e: idEntity, parm: idEventArg<Int>) {
            val parmnum: Int = parm.value
            if (parmnum < 0 || parmnum >= Material.MAX_ENTITY_SHADER_PARMS) {
                idGameLocal.Error("shader parm index (%d) out of range", parmnum)
            }
            idThread.ReturnFloat(e.renderEntity!!.shaderParms[parmnum])
        }

        private fun Event_SetShaderParm(e: idEntity, parmnum: idEventArg<Int>, value: idEventArg<Float>) {
            e.SetShaderParm(parmnum.value, value.value)
        }

        private fun Event_SetShaderParms(
            e: idEntity,
            parm0: idEventArg<Float>,
            parm1: idEventArg<Float>,
            parm2: idEventArg<Float>,
            parm3: idEventArg<Float>
        ) {
            e.renderEntity!!.shaderParms[RenderWorld.SHADERPARM_RED] = parm0.value
            e.renderEntity!!.shaderParms[RenderWorld.SHADERPARM_GREEN] = parm1.value
            e.renderEntity!!.shaderParms[RenderWorld.SHADERPARM_BLUE] = parm2.value
            e.renderEntity!!.shaderParms[RenderWorld.SHADERPARM_ALPHA] = parm3.value
            e.UpdateVisuals()
        }

        private fun Event_SetColor(
            e: idEntity,
            red: idEventArg<Float>,
            green: idEventArg<Float>,
            blue: idEventArg<Float>
        ) {
            e.SetColor(red.value, green.value, blue.value)
        }

        private fun Event_CacheSoundShader(e: idEntity, soundName: idEventArg<String>) {
            DeclManager.declManager.FindSound(soundName.value)
        }

        private fun Event_StartSoundShader(
            e: idEntity,
            soundName: idEventArg<String>,
            channel: idEventArg<Int>
        ) {
            // DG: some d3xp map scripts pass "" to stop a playing sound
            if (soundName.value.isEmpty()) {
                e.StopSound(channel.value, false)
                idThread.ReturnFloat(0.0f)
                return
            }
            val length = CInt()
            e.StartSoundShader(
                DeclManager.declManager.FindSound(soundName.value),  /*(s_channelType)*/
                channel.value,
                0,
                false,
                length
            )
            idThread.ReturnFloat(MS2SEC(length._val.toFloat()))
        }

        private fun Event_StopSound(e: idEntity, channel: idEventArg<Int>, netSync: idEventArg<Int>) {
            e.StopSound(channel.value, netSync.value != 0)
        }

        private fun Event_StartSound(
            e: idEntity,
            soundName: idEventArg<String>,
            channel: idEventArg<Int>,
            netSync: idEventArg<Int>
        ) {
            val time = CInt()
            e.StartSound(soundName.value,  /*(s_channelType)*/channel.value, 0, netSync.value != 0, time)
            idThread.ReturnFloat(MS2SEC(time._val.toFloat()))
        }

        private fun Event_FadeSound(
            e: idEntity,
            channel: idEventArg<Int>,
            to: idEventArg<Float>,
            over: idEventArg<Float>
        ) {
            if (e.refSound.referenceSound != null) {
                e.refSound.referenceSound!!.FadeSound(channel.value, to.value, over.value)
            }
        }

        private fun Event_SetWorldOrigin(e: idEntity, org: idEventArg<idVec3>) {
            val neworg = idVec3(e.GetLocalCoordinates(org.value))
            e.SetOrigin(neworg)
        }

        private fun Event_SetOrigin(e: idEntity, org: idEventArg<idVec3>) {
            e.SetOrigin(org.value)
        }

        private fun Event_SetAngles(e: idEntity, eventArg: idEventArg<idVec3>) {
            e.SetAngles(eventArg.value)
        }

        private fun Event_SetLinearVelocity(e: idEntity, velocity: idEventArg<idVec3>) {
            e.GetPhysics().SetLinearVelocity(velocity.value)
        }

        private fun Event_SetAngularVelocity(e: idEntity, velocity: idEventArg<idVec3>) {
            e.GetPhysics().SetAngularVelocity(velocity.value)
        }

        private fun Event_SetSize(e: idEntity, mins: idEventArg<idVec3>, maxs: idEventArg<idVec3>) {
            e.GetPhysics().SetClipBox(idBounds(mins.value, maxs.value), 1.0f)
        }

        private fun Event_Touches(e: idEntity, ent: idEventArg<idEntity?>) {
            if (ent.value == null) {
                idThread.ReturnInt(false)
                return
            }
            val myBounds = e.GetPhysics().GetAbsBounds()
            val entBounds = ent.value!!.GetPhysics().GetAbsBounds()
            idThread.ReturnInt(myBounds.IntersectsBounds(entBounds))
        }

        private fun Event_SetGuiParm(e: idEntity, k: idEventArg<String>, v: idEventArg<String>) {
            val key = k.value
            val `val` = v.value
            for (i in 0 until RenderWorld.MAX_RENDERENTITY_GUI) {
                if (e.renderEntity!!.gui[i] != null) {
                    if (idStr.Icmpn(key, "gui_", 4) == 0) {
                        e.spawnArgs.Set(key, `val`)
                    }
                    e.renderEntity!!.gui[i]!!.SetStateString(key, `val`)
                    e.renderEntity!!.gui[i]!!.StateChanged(Game_local.gameLocal.time)
                }
            }
        }

        private fun Event_SetGuiFloat(e: idEntity, key: idEventArg<String>, f: idEventArg<Float>) {
            for (i in 0 until RenderWorld.MAX_RENDERENTITY_GUI) {
                if (e.renderEntity!!.gui[i] != null) {
                    e.renderEntity!!.gui[i]!!.SetStateString(key.value, va("%f", f.value))
                    e.renderEntity!!.gui[i]!!.StateChanged(Game_local.gameLocal.time)
                }
            }
        }

        // D3XP GUI events
        private fun Event_SetGui(e: idEntity, guiNum: idEventArg<Int>, guiName: idEventArg<String>) {
            val num = guiNum.value
            if (num >= 1 && num <= RenderWorld.MAX_RENDERENTITY_GUI) {
                e.renderEntity!!.gui[num - 1] = UserInterface.uiManager.FindGui(guiName.value, true, false)
                UpdateGuiParms(e.renderEntity!!.gui[num - 1], e.spawnArgs)
                e.UpdateChangeableSpawnArgs(null)
                gameRenderWorld!!.UpdateEntityDef(e.modelDefHandle, e.renderEntity!!)
            } else {
                idGameLocal.Error("Entity '%s' doesn't have a GUI %d", e.name, num)
            }
        }

        private fun Event_PrecacheGui(e: idEntity, guiName: idEventArg<String>) {
            UserInterface.uiManager.FindGui(guiName.value, true, true)
        }

        private fun Event_GetGuiParm(e: idEntity, guiNum: idEventArg<Int>, key: idEventArg<String>) {
            val num = guiNum.value
            if (e.renderEntity!!.gui[num - 1] != null) {
                idThread.ReturnString(e.renderEntity!!.gui[num - 1]!!.GetStateString(key.value) ?: "")
                return
            }
            idThread.ReturnString("")
        }

        private fun Event_GetGuiParmFloat(e: idEntity, guiNum: idEventArg<Int>, key: idEventArg<String>) {
            val num = guiNum.value
            if (e.renderEntity!!.gui[num - 1] != null) {
                idThread.ReturnFloat(e.renderEntity!!.gui[num - 1]!!.GetStateFloat(key.value))
                return
            }
            idThread.ReturnFloat(0.0f)
        }

        private fun Event_GuiNamedEvent(e: idEntity, guiNum: idEventArg<Int>, event: idEventArg<String>) {
            val num = guiNum.value
            if (e.renderEntity!!.gui[num - 1] != null) {
                e.renderEntity!!.gui[num - 1]!!.HandleNamedEvent(event.value)
            }
        }

        private fun Event_GetNextKey(e: idEntity, prefix: idEventArg<String>, lastMatch: idEventArg<String>) {
            val kv: idKeyValue?
            val previous: idKeyValue?
            previous = if (!lastMatch.value.isEmpty()) {
                e.spawnArgs.FindKey(lastMatch.value)
            } else {
                null
            }
            kv = e.spawnArgs.MatchPrefix(prefix.value, previous)
            if (null == kv) {
                idThread.ReturnString("")
            } else {
                idThread.ReturnString(kv.GetKey())
            }
        }

        private fun Event_SetKey(e: idEntity, key: idEventArg<String>, value: idEventArg<String>) {
            e.spawnArgs.Set(key.value, value.value)
            if (isD3XP) {
                e.UpdateChangeableSpawnArgs(null)
            }
        }

        private fun Event_GetKey(e: idEntity, key: idEventArg<String>) {
            val value = arrayOfNulls<String>(1)
            e.spawnArgs.GetString(key.value, "", value)
            idThread.ReturnString(value[0])
        }

        private fun Event_GetIntKey(e: idEntity, key: idEventArg<String>) {
            val value = CInt(0)
            e.spawnArgs.GetInt(key.value, "0", value)

            // scripts only support floats
            idThread.ReturnFloat(value._val.toFloat())
        }

        private fun Event_GetFloatKey(e: idEntity, key: idEventArg<String>) {
            val value = CFloat()
            e.spawnArgs.GetFloat(key.value, "0", value)
            idThread.ReturnFloat(value._val)
        }

        private fun Event_GetVectorKey(e: idEntity, key: idEventArg<String>) {
            val value = idVec3()
            e.spawnArgs.GetVector(key.value, "0 0 0", value)
            idThread.ReturnVector(value)
        }

        private fun Event_GetEntityKey(e: idEntity, key: idEventArg<String>) {
            val ent: idEntity?
            val entName = arrayOfNulls<String>(1)
            if (!e.spawnArgs.GetString(key.value, "", entName)) {
                idThread.ReturnEntity(null)
                return
            }
            ent = Game_local.gameLocal.FindEntity(entName[0]!!)
            if (null == ent) {
                Game_local.gameLocal.Warning(
                    "Couldn't find entity '%s' specified in '%s' key in entity '%s'",
                    entName,
                    key,
                    e.name
                )
            }
            idThread.ReturnEntity(ent)
        }

        private fun Event_DistanceTo(e: idEntity, ent: idEventArg<idEntity?>) {
            if (null == ent.value) {
                // just say it's really far away
                idThread.ReturnFloat(MAX_WORLD_SIZE.toFloat())
            } else {
                val dist = e.GetPhysics().GetOrigin().minus(ent.value!!.GetPhysics().GetOrigin()).LengthFast()
                idThread.ReturnFloat(dist)
            }
        }

        private fun Event_DistanceToPoint(e: idEntity, point: idEventArg<idVec3>) {
            val dist = e.GetPhysics().GetOrigin().minus(point.value).LengthFast()
            idThread.ReturnFloat(dist)
        }

        private fun Event_StartFx(e: idEntity, fx: idEventArg<String>) {
            idEntityFx.StartFx(fx.value, null, null, e, true)
        }

        init {
            eventCallbacks.putAll(idClass.getEventCallBacks())
            eventCallbacks[EV_GetName] =
                eventCallback_t0<idEntity> { obj: idEntity -> obj.Event_GetName() }
            eventCallbacks[EV_SetName] =
                eventCallback_t1<idEntity> { e: idEntity, newName: idEventArg<*>? ->
                    Event_SetName(
                        e,
                        newName as idEventArg<String>
                    )
                }
            eventCallbacks[EV_FindTargets] =
                eventCallback_t0<idEntity> { obj: idEntity -> obj.Event_FindTargets() }
            eventCallbacks[EV_ActivateTargets] =
                eventCallback_t1<idEntity> { e: idEntity, activator: idEventArg<*>? ->
                    Event_ActivateTargets(e, activator as idEventArg<idEntity>)
                }
            eventCallbacks[EV_NumTargets] =
                eventCallback_t0<idEntity> { obj: idEntity -> obj.Event_NumTargets() }
            eventCallbacks[EV_GetTarget] = eventCallback_t1<idEntity> { e: idEntity, index: idEventArg<*>? ->
                Event_GetTarget(e, index as idEventArg<Float>)
            }
            eventCallbacks[EV_RandomTarget] = eventCallback_t1<idEntity> { e: idEntity, ignor: idEventArg<*>? ->
                Event_RandomTarget(e, ignor as idEventArg<String?>)
            }
            eventCallbacks[EV_BindToJoint] = eventCallback_t3<idEntity> { e: idEntity,
                                                                          master: idEventArg<*>?,
                                                                          jointname: idEventArg<*>?,
                                                                          orientated: idEventArg<*>? ->
                Event_BindToJoint(
                    e,
                    master as idEventArg<idEntity>,
                    jointname as idEventArg<String>,
                    orientated as idEventArg<Float>
                )
            }
            eventCallbacks[EV_RemoveBinds] =
                eventCallback_t0<idEntity> { obj: idEntity -> obj.Event_RemoveBinds() }
            eventCallbacks[EV_Bind] = eventCallback_t1<idEntity> { e: idEntity, master: idEventArg<*>? ->
                Event_Bind(e, master as idEventArg<idEntity>)
            }
            eventCallbacks[EV_BindPosition] = eventCallback_t1<idEntity> { e: idEntity, master: idEventArg<*>? ->
                Event_BindPosition(e, master as idEventArg<idEntity>)
            }
            eventCallbacks[EV_Unbind] =
                eventCallback_t0<idEntity> { obj: idEntity -> obj.Event_Unbind() }
            eventCallbacks[EV_SpawnBind] =
                eventCallback_t0<idEntity> { obj: idEntity -> obj.Event_SpawnBind() }
            eventCallbacks[EV_SetOwner] = eventCallback_t1<idEntity> { e: idEntity, owner: idEventArg<*>? ->
                Event_SetOwner(e, owner as idEventArg<idEntity>)
            }
            eventCallbacks[EV_SetModel] = eventCallback_t1<idEntity> { e: idEntity, modelname: idEventArg<*>? ->
                Event_SetModel(e, modelname as idEventArg<String>)
            }
            eventCallbacks[EV_SetSkin] = eventCallback_t1<idEntity> { e: idEntity, skinname: idEventArg<*>? ->
                Event_SetSkin(e, skinname as idEventArg<String>)
            }
            eventCallbacks[EV_GetShaderParm] = eventCallback_t1<idEntity> { e: idEntity, parm: idEventArg<*>? ->
                Event_GetShaderParm(e, parm as idEventArg<Int>)
            }
            eventCallbacks[EV_SetShaderParm] =
                eventCallback_t2<idEntity> { e: idEntity, parmnum: idEventArg<*>?, value: idEventArg<*>? ->
                    Event_SetShaderParm(e, parmnum as idEventArg<Int>, value as idEventArg<Float>)
                }
            eventCallbacks[EV_SetShaderParms] = eventCallback_t4<idEntity> { e: idEntity, parm0: idEventArg<*>?,
                                                                             parm1: idEventArg<*>?,
                                                                             parm2: idEventArg<*>?,
                                                                             parm3: idEventArg<*>? ->
                Event_SetShaderParms(
                    e,
                    parm0 as idEventArg<Float>,
                    parm1 as idEventArg<Float>,
                    parm2 as idEventArg<Float>,
                    parm3 as idEventArg<Float>
                )
            }
            eventCallbacks[EV_SetColor] = eventCallback_t3<idEntity> { e: idEntity, red: idEventArg<*>?,
                                                                       green: idEventArg<*>?,
                                                                       blue: idEventArg<*>? ->
                Event_SetColor(
                    e,
                    red as idEventArg<Float>,
                    green as idEventArg<Float>,
                    blue as idEventArg<Float>
                )
            }
            eventCallbacks[EV_GetColor] =
                eventCallback_t0<idEntity> { obj: idEntity -> obj.Event_GetColor() }
            eventCallbacks[EV_IsHidden] =
                eventCallback_t0<idEntity> { obj: idEntity -> obj.Event_IsHidden() }
            eventCallbacks[EV_Hide] =
                eventCallback_t0<idEntity> { obj: idEntity -> obj.Event_Hide() }
            eventCallbacks[EV_Show] =
                eventCallback_t0<idEntity> { obj: idEntity -> obj.Event_Show() }
            eventCallbacks[EV_CacheSoundShader] =
                eventCallback_t1<idEntity> { e: idEntity, soundName: idEventArg<*>? ->
                    Event_CacheSoundShader(e, soundName as idEventArg<String>)
                }
            eventCallbacks[EV_StartSoundShader] =
                eventCallback_t2<idEntity> { e: idEntity, soundName: idEventArg<*>?,
                                             channel: idEventArg<*>? ->
                    Event_StartSoundShader(e, soundName as idEventArg<String>, channel as idEventArg<Int>)
                }
            eventCallbacks[EV_StartSound] = eventCallback_t3<idEntity> { e: idEntity, soundName: idEventArg<*>?,
                                                                         channel: idEventArg<*>?,
                                                                         netSync: idEventArg<*>? ->
                Event_StartSound(
                    e,
                    soundName as idEventArg<String>,
                    channel as idEventArg<Int>,
                    netSync as idEventArg<Int>
                )
            }
            eventCallbacks[EV_StopSound] =
                eventCallback_t2<idEntity> { e: idEntity, channel: idEventArg<*>?, netSync: idEventArg<*>? ->
                    Event_StopSound(e, channel as idEventArg<Int>, netSync as idEventArg<Int>)
                }
            eventCallbacks[EV_FadeSound] = eventCallback_t3<idEntity> { e: idEntity, channel: idEventArg<*>?,
                                                                        to: idEventArg<*>?,
                                                                        over: idEventArg<*>? ->
                Event_FadeSound(e, channel as idEventArg<Int>, to as idEventArg<Float>, over as idEventArg<Float>)
            }
            eventCallbacks[EV_GetWorldOrigin] =
                eventCallback_t0<idEntity> { obj: idEntity -> obj.Event_GetWorldOrigin() }
            eventCallbacks[EV_SetWorldOrigin] = eventCallback_t1<idEntity> { e: idEntity, org: idEventArg<*>? ->
                Event_SetWorldOrigin(e, org as idEventArg<idVec3>)
            }
            eventCallbacks[EV_GetOrigin] =
                eventCallback_t0<idEntity> { obj: idEntity -> obj.Event_GetOrigin() }
            eventCallbacks[EV_SetOrigin] = eventCallback_t1<idEntity> { e: idEntity, org: idEventArg<*>? ->
                Event_SetOrigin(e, org as idEventArg<idVec3>)
            }
            eventCallbacks[EV_GetAngles] =
                eventCallback_t0<idEntity> { obj: idEntity -> obj.Event_GetAngles() }
            eventCallbacks[EV_SetAngles] = eventCallback_t1<idEntity> { e: idEntity, eventArg: idEventArg<*>? ->
                Event_SetAngles(e, eventArg as idEventArg<idVec3>)
            }
            eventCallbacks[EV_GetLinearVelocity] =
                eventCallback_t0<idEntity> { obj: idEntity -> obj.Event_GetLinearVelocity() }
            eventCallbacks[EV_SetLinearVelocity] =
                eventCallback_t1<idEntity> { e: idEntity, velocity: idEventArg<*>? ->
                    Event_SetLinearVelocity(e, velocity as idEventArg<idVec3>)
                }
            eventCallbacks[EV_GetAngularVelocity] =
                eventCallback_t0<idEntity> { obj: idEntity -> obj.Event_GetAngularVelocity() }
            eventCallbacks[EV_SetAngularVelocity] =
                eventCallback_t1<idEntity> { e: idEntity, velocity: idEventArg<*>? ->
                    Event_SetAngularVelocity(e, velocity as idEventArg<idVec3>)
                }
            eventCallbacks[EV_GetSize] =
                eventCallback_t0<idEntity> { obj: idEntity -> obj.Event_GetSize() }
            eventCallbacks[EV_SetSize] =
                eventCallback_t2<idEntity> { e: idEntity, mins: idEventArg<*>?, maxs: idEventArg<*>? ->
                    Event_SetSize(e, mins as idEventArg<idVec3>, maxs as idEventArg<idVec3>)
                }
            eventCallbacks[EV_GetMins] =
                eventCallback_t0<idEntity> { obj: idEntity -> obj.Event_GetMins() }
            eventCallbacks[EV_GetMaxs] =
                eventCallback_t0<idEntity> { obj: idEntity -> obj.Event_GetMaxs() }
            eventCallbacks[EV_Touches] = eventCallback_t1<idEntity> { e: idEntity, ent: idEventArg<*>? ->
                Event_Touches(e, ent as idEventArg<idEntity?>)
            }
            eventCallbacks[EV_SetGuiParm] =
                eventCallback_t2<idEntity> { e: idEntity, k: idEventArg<*>?, v: idEventArg<*>? ->
                    Event_SetGuiParm(e, k as idEventArg<String>, v as idEventArg<String>)
                }
            eventCallbacks[EV_SetGuiFloat] =
                eventCallback_t2<idEntity> { e: idEntity, key: idEventArg<*>?, f: idEventArg<*>? ->
                    Event_SetGuiFloat(e, key as idEventArg<String>, f as idEventArg<Float>)
                }
            // D3XP GUI events
            eventCallbacks[EV_SetGui] =
                eventCallback_t2<idEntity> { e: idEntity, guiNum: idEventArg<*>?, guiName: idEventArg<*>? ->
                    Event_SetGui(e, guiNum as idEventArg<Int>, guiName as idEventArg<String>)
                }
            eventCallbacks[EV_PrecacheGui] =
                eventCallback_t1<idEntity> { e: idEntity, guiName: idEventArg<*>? ->
                    Event_PrecacheGui(e, guiName as idEventArg<String>)
                }
            eventCallbacks[EV_GetGuiParm] =
                eventCallback_t2<idEntity> { e: idEntity, guiNum: idEventArg<*>?, key: idEventArg<*>? ->
                    Event_GetGuiParm(e, guiNum as idEventArg<Int>, key as idEventArg<String>)
                }
            eventCallbacks[EV_GetGuiParmFloat] =
                eventCallback_t2<idEntity> { e: idEntity, guiNum: idEventArg<*>?, key: idEventArg<*>? ->
                    Event_GetGuiParmFloat(e, guiNum as idEventArg<Int>, key as idEventArg<String>)
                }
            eventCallbacks[EV_GuiNamedEvent] =
                eventCallback_t2<idEntity> { e: idEntity, guiNum: idEventArg<*>?, event: idEventArg<*>? ->
                    Event_GuiNamedEvent(e, guiNum as idEventArg<Int>, event as idEventArg<String>)
                }
            eventCallbacks[EV_GetNextKey] =
                eventCallback_t2<idEntity> { e: idEntity, prefix: idEventArg<*>?, lastMatch: idEventArg<*>? ->
                    Event_GetNextKey(e, prefix as idEventArg<String>, lastMatch as idEventArg<String>)
                }
            eventCallbacks[EV_SetKey] =
                eventCallback_t2<idEntity> { e: idEntity, key: idEventArg<*>?, value: idEventArg<*>? ->
                    Event_SetKey(e, key as idEventArg<String>, value as idEventArg<String>)
                }
            eventCallbacks[EV_GetKey] = eventCallback_t1<idEntity> { e: idEntity, key: idEventArg<*>? ->
                Event_GetKey(e, key as idEventArg<String>)
            }
            eventCallbacks[EV_GetIntKey] = eventCallback_t1<idEntity> { e: idEntity, key: idEventArg<*>? ->
                Event_GetIntKey(e, key as idEventArg<String>)
            }
            eventCallbacks[EV_GetFloatKey] = eventCallback_t1<idEntity> { e: idEntity, key: idEventArg<*>? ->
                Event_GetFloatKey(e, key as idEventArg<String>)
            }
            eventCallbacks[EV_GetVectorKey] = eventCallback_t1<idEntity> { e: idEntity, key: idEventArg<*>? ->
                Event_GetVectorKey(e, key as idEventArg<String>)
            }
            eventCallbacks[EV_GetEntityKey] = eventCallback_t1<idEntity> { e: idEntity, key: idEventArg<*>? ->
                Event_GetEntityKey(e, key as idEventArg<String>)
            }
            eventCallbacks[EV_RestorePosition] =
                eventCallback_t0<idEntity> { obj: idEntity -> obj.Event_RestorePosition() }
            eventCallbacks[EV_UpdateCameraTarget] =
                eventCallback_t0<idEntity> { obj: idEntity -> obj.Event_UpdateCameraTarget() }
            eventCallbacks[EV_DistanceTo] = eventCallback_t1<idEntity> { e: idEntity, ent: idEventArg<*>? ->
                Event_DistanceTo(e, ent as idEventArg<idEntity?>)
            }
            eventCallbacks[EV_DistanceToPoint] =
                eventCallback_t1<idEntity> { e: idEntity, point: idEventArg<*>? ->
                    Event_DistanceToPoint(e, point as idEventArg<idVec3>)
                }
            eventCallbacks[EV_StartFx] = eventCallback_t1<idEntity> { e: idEntity, fx: idEventArg<*>? ->
                Event_StartFx(e, fx as idEventArg<String>)
            }
            eventCallbacks[EV_Thread_WaitFrame] =
                eventCallback_t0<idEntity> { obj: idEntity -> obj.Event_WaitFrame() }
            eventCallbacks[EV_Thread_Wait] =
                eventCallback_t1<idEntity> { obj: idEntity, time: idEventArg<*>? -> obj.Event_Wait(time as idEventArg<Float>) }
            eventCallbacks[EV_HasFunction] =
                eventCallback_t1<idEntity> { obj: idEntity, name: idEventArg<*>? -> obj.Event_HasFunction(name as idEventArg<String>) }
            eventCallbacks[EV_CallFunction] =
                eventCallback_t1<idEntity> { obj: idEntity, _funcName: idEventArg<*>? ->
                    obj.Event_CallFunction(_funcName as idEventArg<String>)
                }
            eventCallbacks[EV_SetNeverDormant] =
                eventCallback_t1<idEntity> { obj: idEntity, enable: idEventArg<*>? ->
                    obj.Event_SetNeverDormant(enable as idEventArg<Int>)
                }
        }
    }

    //
    val targets // when this entity is activated these entities entity are activated
            : idList<idEntityPtr<idEntity>>
    private val PVSAreas: IntArray = IntArray(MAX_PVS_AREAS) // numbers of the renderer areas the entity covers
    var activeNode // for being linked into activeEntities list
            : idLinkList<idEntity>
    var cameraTarget // any remoteRenderMap shaders will use this
            : idEntity?
    var cinematic // during cinematics, entity will only think if cinematic is set
            : Boolean
    var dormantStart // time that the entity was first closed off from player
            : Int
    var entityDefNumber // index into the entity def list
            : Int

    //
    var entityNumber // index into the entity list
            : Int
    var fl: entityFlags_s

    //
    //
    var health // FIXME: do all objects really need health?
            : Int

    //
    val name // name of entity
            : idStr = idStr()

    //
    var renderView // for camera views from this entity
            : renderView_s?
    var scriptObject // contains all script defined data for this entity
            : idScriptObject
    var snapshotBits // number of bits this entity occupied in the last snapshot
            : Int

    //
    var snapshotNode // for being linked into snapshotEntities list
            : idLinkList<idEntity>
    var snapshotSequence // last snapshot this entity was in
            : Int
    var spawnArgs // key/value pairs used to spawn and initialize entity
            : idDict

    //
    var spawnNode // for being linked into spawnedEntities list
            : idLinkList<idEntity>

    //
    var thinkFlags // TH_? flags
            : Int

    // D3XP: dual-timeline group assignment (TIME_GROUP1 = fast, TIME_GROUP2 = slow)
    var timeGroup: Int = Game_local.TIME_GROUP1

    // D3XP: prevents the grabber from picking up this entity
    var noGrab: Boolean = false

    // D3XP: x-ray vision — secondary render entity shown through walls
    var xrayEntity: renderEntity_s? = null
    var xrayEntityHandle: Int = -1
    var xraySkin: idDeclSkin? = null
    protected var modelDefHandle // handle to static renderer model
            : Int
    protected var refSound // used to present sound to the audio engine
            : refSound_t

    //
    //
    protected var renderEntity // used to present a model to the renderer
            : renderEntity_s? = null
    private var bindBody // body bound to if unequal -1
            : Int
    private var   /*jointHandle_t*/bindJoint // joint bound to if unequal INVALID_JOINT
            : Int
    private var bindMaster // entity bound to if unequal NULL
            : idEntity?

    //
    private val defaultPhysicsObj: idPhysics_Static = idPhysics_Static() // default physics object

    //
    private var mpGUIState // local cache to avoid systematic SetStateInt
            : Int

    //
    private var numPVSAreas // number of renderer areas the entity covers
            : Int

    // set by default. Triggers are static.
    private var physics: idPhysics = defaultPhysicsObj // physics used for this entity

    //
    private var signals: signalList_t?
    private var teamChain // next entity in physics team
            : idEntity?
    private var teamMaster // master of the physics team
            : idEntity?

    override fun CreateInstance(): idClass = idEntity()

    override fun GetType(): idTypeInfo = Type

    override fun getEventCallBack(event: idEventDef): eventCallback_t<*>? {
        return eventCallbacks[event]
    }

    override fun oSet(node: idEntity?): idEntity? {
        throw UnsupportedOperationException("Not supported yet.")
    }

    override fun isNULL(): Boolean {
        throw UnsupportedOperationException("Not supported yet.")
    }

    override fun AllocBuffer(): ByteBuffer {
        return ByteBuffer.allocate(SERIAL_BYTES)
    }

    override fun Read(buffer: ByteBuffer) {
        // idEntity uses Save/Restore for game serialization, not SERiAL.
        // This is a placeholder matching the C++ sizeof for binary compatibility.
        buffer.order(java.nio.ByteOrder.LITTLE_ENDIAN)
    }

    override fun Write(): ByteBuffer {
        // idEntity uses Save/Restore for game serialization, not SERiAL.
        val buffer = AllocBuffer()
        buffer.order(java.nio.ByteOrder.LITTLE_ENDIAN)
        buffer.flip()
        return buffer
    }

    override fun oSet(oGet: idClass?) {
        throw UnsupportedOperationException("Not supported yet.")
    }

    public override fun _deconstructor() {
        if (Game_local.gameLocal.GameState() != gameState_t.GAMESTATE_SHUTDOWN && !Game_local.gameLocal.isClient && fl.networkSync && entityNumber >= Game_local.MAX_CLIENTS) {
            val msg = idBitMsg()
            val msgBuf = ByteArray(Game_local.MAX_GAME_MESSAGE_SIZE)
            msg.Init(msgBuf)
            msg.WriteByte(Game_local.GAME_RELIABLE_MESSAGE_DELETE_ENT.toByte())
            msg.WriteBits(Game_local.gameLocal.GetSpawnId(this), 32)
            NetworkSystem.networkSystem.ServerSendReliableMessage(-1, msg)
        }
        DeconstructScriptObject()
        scriptObject.Free()
        if (thinkFlags != 0) {
            BecomeInactive(thinkFlags)
        }
        activeNode.Remove()
        Signal(signalNum_t.SIG_REMOVED)

        // we have to set back the default physics object before unbinding because the entity
        // specific physics object might be an entity variable and as such could already be destroyed.
        SetPhysics(null)

        // remove any entities that are bound to me
        RemoveBinds()

        // unbind from master
        Unbind()
        QuitTeam()
        Game_local.gameLocal.RemoveEntityFromHash(name.toString(), this)

        renderView = null // delete renderView;
        signals = null // delete signals;

        // D3XP: free xray render entity
        if (isD3XP && xrayEntityHandle != -1) {
            gameRenderWorld!!.FreeEntityDef(xrayEntityHandle)
            xrayEntityHandle = -1
        }

        FreeModelDef()
        FreeSoundEmitter(false)

        Game_local.gameLocal.UnregisterEntity(this)

        delete(teamChain)
        delete(teamMaster)
        delete(bindMaster)
        delete(physics)
        if (physics !== defaultPhysicsObj) delete(defaultPhysicsObj)
        delete(cameraTarget)

        super._deconstructor()
    }

    override fun Spawn() {
        super.Spawn()
        var i: Int
        val temp = arrayOfNulls<String>(1)
        val origin = idVec3()
        val axis: idMat3 = idMat3()
        val networkSync: idKeyValue?
        val classname = arrayOfNulls<String>(1)
        val scriptObjectName = arrayOfNulls<String>(1)
        Game_local.gameLocal.RegisterEntity(this)
        spawnArgs.GetString("classname", "", classname)
        val def = Game_local.gameLocal.FindEntityDef(classname[0]!!, false)
        if (def != null) {
            entityDefNumber = def.Index()
        }
        FixupLocalizedStrings()

        // parse static models the same way the editor display does
        GameEdit.gameEdit.ParseSpawnArgsToRenderEntity(spawnArgs, renderEntity!!)
        renderEntity!!.entityNum = entityNumber

        // D3XP: initialize grab, xray, and time group fields from spawnArgs
        if (isD3XP) {
            noGrab = spawnArgs.GetBool("noGrab", "0")
            xraySkin = null
            renderEntity!!.xrayIndex = 1
            val xraySkinStr = spawnArgs.GetString("skin_xray", "")
            if (!xraySkinStr.isNullOrEmpty()) {
                xraySkin = DeclManager.declManager.FindSkin(xraySkinStr) as? idDeclSkin
            }
        }

        // go dormant within 5 frames so that when the map starts most monsters are dormant
        dormantStart = Game_local.gameLocal.time - DELAY_DORMANT_TIME + Game_local.gameLocal.msec * 5
        origin.set(renderEntity!!.origin)
        axis.set(idMat3(renderEntity!!.axis))

        // do the audio parsing the same way dmap and the editor do
        GameEdit.gameEdit.ParseSpawnArgsToRefSound(spawnArgs, refSound)

        // only play SCHANNEL_PRIVATE when sndworld.PlaceListener() is called with this listenerId
        // don't spatialize sounds from the same entity
        refSound.listenerId = entityNumber + 1
        cameraTarget = null
        temp[0] = spawnArgs.GetString("cameraTarget")
        if (temp.isNotEmpty() && !temp[0].isNullOrEmpty()) {
            // update the camera taget
            PostEventMS(EV_UpdateCameraTarget, 0)
        }
        i = 0
        while (i < RenderWorld.MAX_RENDERENTITY_GUI) {
            UpdateGuiParms(renderEntity!!.gui[i], spawnArgs)
            i++
        }
        fl.solidForTeam = spawnArgs.GetBool("solidForTeam", "0")
        fl.neverDormant = spawnArgs.GetBool("neverDormant", "0")
        fl.hidden = spawnArgs.GetBool("hide", "0")
        if (fl.hidden) {
            // make sure we're hidden, since a spawn function might not set it up right
            PostEventMS(EV_Hide, 0)
        }
        cinematic = spawnArgs.GetBool("cinematic", "0")
        networkSync = spawnArgs.FindKey("networkSync")
        if (networkSync != null) {
            fl.networkSync = TempDump.atoi(networkSync.GetValue()) != 0
        }
        if (false) {
            if (!Game_local.gameLocal.isClient) {
                // common.DPrintf( "NET: DBG %s - %s is synced: %s\n", spawnArgs.GetString( "classname", "" ), GetType().classname, fl.networkSync ? "true" : "false" );
                if (spawnArgs.GetString("classname", "")!![0] == '\u0000' && !fl.networkSync) {
                    idLib.common.DPrintf(
                        "NET: WRN %s entity, no classname, and no networkSync?\n",
                        GetType().javaClass.name
                    )
                }
            }
        }

        // every object will have a unique name
        temp[0] = spawnArgs.GetString(
            "name",
            va("%s_%s_%d", GetClassname(), spawnArgs.GetString("classname"), entityNumber)
        )!!
        SetName(temp[0])

        // if we have targets, wait until all entities are spawned to get them
        if (spawnArgs.MatchPrefix("target") != null || spawnArgs.MatchPrefix("guiTarget") != null) {
            if (Game_local.gameLocal.GameState() == gameState_t.GAMESTATE_STARTUP) {
                PostEventMS(EV_FindTargets, 0)
            } else {
                // not during spawn, so it's ok to get the targets
                FindTargets()
            }
        }
        health = spawnArgs.GetInt("health")
        InitDefaultPhysics(origin, axis)
        SetOrigin(origin)
        SetAxis(axis)
        temp[0] = spawnArgs.GetString("model")
        if (temp.isNotEmpty() && !temp[0].isNullOrEmpty()) {
            SetModel(temp[0]!!)
        }
        if (spawnArgs.GetString("bind", "", temp)) {
            PostEventMS(EV_SpawnBind, 0)
        }

        // auto-start a sound on the entity
        if (refSound.shader != null && !refSound.waitfortrigger) {
            StartSoundShader(refSound.shader, gameSoundChannel_t.SND_CHANNEL_ANY.ordinal, 0, false)
        }

        // setup script object
        if (ShouldConstructScriptObjectAtSpawn() && spawnArgs.GetString("scriptobject", "", scriptObjectName)) {
            if (!scriptObject.SetType(scriptObjectName[0])) {
                idGameLocal.Error(
                    "Script object '%s' not found on entity '%s'.",
                    scriptObjectName[0],
                    name
                )
            }
            ConstructScriptObject()
        }

        // D3XP: determine time group from "slowmo" spawnarg (default true = slow timeline)
        if (isD3XP) {
            DetermineTimeGroup(spawnArgs.GetBool("slowmo", "1"))
        }
    }

    override fun Save(savefile: idSaveGame) {
        super.Save(savefile)
        var i: Int
        var j: Int
        savefile.WriteInt(entityNumber)
        savefile.WriteInt(entityDefNumber)

        // spawnNode and activeNode are restored by gameLocal
        savefile.WriteInt(snapshotSequence)
        savefile.WriteInt(snapshotBits)
        savefile.WriteDict(spawnArgs)
        savefile.WriteString(name)
        scriptObject.Save(savefile)
        savefile.WriteInt(thinkFlags)
        savefile.WriteInt(dormantStart)
        savefile.WriteBool(cinematic)
        savefile.WriteObject(cameraTarget)
        savefile.WriteInt(health)
        savefile.WriteInt(targets.Num())
        i = 0
        while (i < targets.Num()) {
            targets[i].Save(savefile)
            i++
        }
        val flags = fl
        LittleBitField(flags /*, sizeof(flags)*/)
        savefile.Write(flags /*, sizeof(flags)*/)
        // D3XP: save time group, grab, and xray state
        if (isD3XP) {
            savefile.WriteInt(timeGroup)
            savefile.WriteBool(noGrab)
            savefile.WriteRenderEntity(xrayEntity ?: renderEntity_s())
            savefile.WriteInt(xrayEntityHandle)
            savefile.WriteSkin(xraySkin)
        }
        savefile.WriteRenderEntity(renderEntity!!)
        savefile.WriteInt(modelDefHandle)
        savefile.WriteRefSound(refSound)
        savefile.WriteObject(bindMaster)
        savefile.WriteJoint(bindJoint)
        savefile.WriteInt(bindBody)
        savefile.WriteObject(teamMaster)
        savefile.WriteObject(teamChain)
        savefile.WriteStaticObject(defaultPhysicsObj)
        savefile.WriteInt(numPVSAreas)
        i = 0
        while (i < MAX_PVS_AREAS) {
            savefile.WriteInt(PVSAreas[i])
            i++
        }
        if (null == signals) {
            savefile.WriteBool(false)
        } else {
            savefile.WriteBool(true)
            i = 0
            while (i < signalNum_t.NUM_SIGNALS.ordinal) {
                savefile.WriteInt(signals!!.signal[i].Num())
                j = 0
                while (j < signals!!.signal[i].Num()) {
                    savefile.WriteInt(signals!!.signal[i][j].threadnum)
                    savefile.WriteString(signals!!.signal[i][j].function!!.Name())
                    j++
                }
                i++
            }
        }
        savefile.WriteInt(mpGUIState)
    }

    override fun Restore(savefile: idRestoreGame) {
        super.Restore(savefile)
        var i: Int
        var j: Int
        val num = CInt()
        val funcname = idStr()
        entityNumber = savefile.ReadInt()
        entityDefNumber = savefile.ReadInt()

        // spawnNode and activeNode are restored by gameLocal
        snapshotSequence = savefile.ReadInt()
        snapshotBits = savefile.ReadInt()
        spawnArgs = savefile.ReadDict()!!
        savefile.ReadString(name)
        SetName(name)
        scriptObject.Restore(savefile)
        thinkFlags = savefile.ReadInt()
        dormantStart = savefile.ReadInt()
        cinematic = savefile.ReadBool()
        cameraTarget = savefile.ReadObject() as idEntity?
        health = savefile.ReadInt()
        targets.Clear()
        savefile.ReadInt(num)
        targets.SetNum(num._val)
        i = 0
        while (i < num._val) {
            targets[i] = idEntityPtr()
            targets[i].Restore(savefile)
            i++
        }
        savefile.Read(fl)
        LittleBitField(fl)
        // D3XP: restore time group, grab, and xray state
        if (isD3XP) {
            timeGroup = savefile.ReadInt()
            noGrab = savefile.ReadBool()
            xrayEntity = savefile.ReadRenderEntity()
            xrayEntityHandle = savefile.ReadInt()
            if (xrayEntityHandle != -1) {
                xrayEntityHandle = gameRenderWorld!!.AddEntityDef(xrayEntity!!)
            }
            xraySkin = savefile.ReadSkin() as? idDeclSkin
        }
        renderEntity = savefile.ReadRenderEntity()
        modelDefHandle = savefile.ReadInt()
        savefile.ReadRefSound(refSound)
        bindMaster = savefile.ReadObject() as idEntity?
        bindJoint = savefile.ReadJoint()
        bindBody = savefile.ReadInt()
        teamMaster = savefile.ReadObject() as idEntity?
        teamChain = savefile.ReadObject() as idEntity?
        savefile.ReadStaticObject(defaultPhysicsObj)
        RestorePhysics(defaultPhysicsObj)
        numPVSAreas = savefile.ReadInt()
        i = 0
        while (i < MAX_PVS_AREAS) {
            PVSAreas[i] = savefile.ReadInt()
            i++
        }
        val readsignals = CBool(false)
        savefile.ReadBool(readsignals)
        if (readsignals._val) {
            signals = signalList_t()
            i = 0
            while (i < signalNum_t.NUM_SIGNALS.ordinal) {
                savefile.ReadInt(num)
                signals!!.signal[i].SetNum(num._val)
                j = 0
                while (j < num._val) {
                    signals!!.signal[i][j].threadnum = savefile.ReadInt()
                    savefile.ReadString(funcname)
                    signals!!.signal[i][j].function = Game_local.gameLocal.program.FindFunction(funcname)
                    if (null == signals!!.signal[i][j].function) {
                        savefile.Error("Function '%s' not found", funcname.toString())
                    }
                    j++
                }
                i++
            }
        }
        mpGUIState = savefile.ReadInt()

        // restore must retrieve modelDefHandle from the renderer
        if (modelDefHandle != -1) {
            modelDefHandle = gameRenderWorld!!.AddEntityDef(renderEntity!!)
        }
    }

    fun GetEntityDefName(): String {
        return if (entityDefNumber < 0) {
            "*unknown*"
        } else DeclManager.declManager.DeclByIndex(declType_t.DECL_ENTITYDEF, entityDefNumber, false)!!.GetName()
    }

    fun SetName(newname: String?) {
        if (name.Length() != 0) {
            Game_local.gameLocal.RemoveEntityFromHash(name.toString(), this)
            Game_local.gameLocal.program.SetEntity(name.toString(), null)
        }
        name.set(newname)
        if (name.Length() != 0) {
//            if ( ( name == "NULL" ) || ( name == "null_entity" ) ) {
            if ("NULL" == newname || "null_entity" == newname) {
                idGameLocal.Error("Cannot name entity '%s'.  '%s' is reserved for script.", name, name)
            }
            Game_local.gameLocal.AddEntityToHash(name.toString(), this)
            Game_local.gameLocal.program.SetEntity(name.toString(), this)
        }
    }

    fun SetName(newname: idStr) {
        SetName(newname.toString())
    }

    fun GetName(): String {
        return name.toString()
    }

    /*
         ===============
         idEntity::UpdateChangeableSpawnArgs

         Any key val pair that might change during the course of the game ( via a gui or whatever )
         should be initialize here so a gui or other trigger can change something and have it updated
         properly. An optional source may be provided if the values reside in an outside dictionary and
         first need copied over to spawnArgs
         ===============
         */
    open fun UpdateChangeableSpawnArgs(source: idDict?) {
        var source = source
        var i: Int
        val target: String?
        if (null == source) { //TODO:null check
            source = spawnArgs
        }
        cameraTarget = null
        target = source.GetString("cameraTarget")
        if (target != null && !target.isEmpty()) {
            // update the camera taget
            PostEventMS(EV_UpdateCameraTarget, 0)
        }
        i = 0
        while (i < RenderWorld.MAX_RENDERENTITY_GUI) {
            UpdateGuiParms(renderEntity!!.gui[i], source)
            i++
        }
    }

    /*
         =============
         idEntity::GetRenderView

         This is used by remote camera views to look from an entity
         =============
         */
    // clients generate views based on all the player specific options,
    // cameras have custom code, and everything else just uses the axis orientation
    open fun GetRenderView(): renderView_s? {
        if (renderView == null) {
            renderView = renderView_s()
        }
        renderView = renderView_s()
        //	memset( renderView, 0, sizeof( *renderView ) );
        renderView!!.vieworg.set(GetPhysics().GetOrigin())
        renderView!!.fov_x = 120.0f
        renderView!!.fov_y = 120.0f
        renderView!!.viewaxis.set((GetPhysics().GetAxis()))

        // copy global shader parms
        System.arraycopy(
            Game_local.gameLocal.globalShaderParms,
            0,
            renderView!!.shaderParms,
            0,
            RenderWorld.MAX_GLOBAL_SHADER_PARMS
        )
        renderView!!.globalMaterial = Game_local.gameLocal.GetGlobalMaterial()
        renderView!!.time = Game_local.gameLocal.time

        return renderView!!
    }

    /* **********************************************************************

         Thinking

         ***********************************************************************/
    // thinking
    open fun Think() {
        RunPhysics()
        Present()
    }

    /*
         ================
         idEntity::CheckDormant

         Monsters and other expensive entities that are completely closed
         off from the player can skip all of their work
         ================
         */
    fun CheckDormant(): Boolean {    // dormant == on the active list, but out of PVS
        val dormant: Boolean
        dormant = DoDormantTests()
        if (dormant && !fl.isDormant) {
            fl.isDormant = true
            DormantBegin()
        } else if (!dormant && fl.isDormant) {
            fl.isDormant = false
            DormantEnd()
        }
        return dormant
    }

    /*
         ================
         idEntity::DormantBegin

         called when entity becomes dormant
         ================
         */
    open fun DormantBegin() {}

    /*
         ================
         idEntity::DormantEnd

         called when entity wakes from being dormant
         ================
         */
    open fun DormantEnd() {}
    fun IsActive(): Boolean {
        return activeNode.InList()
    }

    fun BecomeActive(flags: Int) {
        if ((flags and TH_PHYSICS) != 0) {
            // enable the team master if this entity is part of a physics team
            if (teamMaster != null && teamMaster !== this) {
                teamMaster!!.BecomeActive(TH_PHYSICS)
            } else if (0 == (thinkFlags and TH_PHYSICS)) {
                // if this is a pusher
                if (physics is idPhysics_Parametric || physics is idPhysics_Actor) {
                    Game_local.gameLocal.sortPushers = true
                }
            }
        }
        val oldFlags = thinkFlags
        thinkFlags = thinkFlags or flags
        if (thinkFlags != 0) {
            if (!IsActive()) {
                activeNode.AddToEnd(Game_local.gameLocal.activeEntities)
            } else if (0 == oldFlags) {
                // we became inactive this frame, so we have to decrease the count of entities to deactivate
                Game_local.gameLocal.numEntitiesToDeactivate--
            }
        }
    }

    fun BecomeInactive(flags: Int) {
        var flags = flags
        if ((flags and TH_PHYSICS) != 0) {
            // may only disable physics on a team master if no team members are running physics or bound to a joints
            if (teamMaster == this) {
                var ent = teamMaster!!.teamChain
                while (ent != null) {
                    if ((ent.thinkFlags and TH_PHYSICS) != 0 || ent.bindMaster == this && ent.bindJoint != Model.INVALID_JOINT) {
                        flags = flags and TH_PHYSICS.inv()
                        break
                    }
                    ent = ent.teamChain
                }
            }
        }
        if (thinkFlags != 0) {
            thinkFlags = thinkFlags and flags.inv()
            if (0 == thinkFlags && IsActive()) {
                Game_local.gameLocal.numEntitiesToDeactivate++
            }
        }
        if ((flags and TH_PHYSICS) != 0) {
            // if this entity has a team master
            if (teamMaster != null && teamMaster != this) {
                // if the team master is at rest
                if (teamMaster!!.IsAtRest()) {
                    teamMaster!!.BecomeInactive(TH_PHYSICS)
                }
            }
        }
    }

    fun UpdatePVSAreas(pos: idVec3) {
        var i: Int
        numPVSAreas = Game_local.gameLocal.pvs.GetPVSAreas(idBounds(pos), PVSAreas, MAX_PVS_AREAS)
        i = numPVSAreas
        while (i < MAX_PVS_AREAS) {
            PVSAreas[i++] = 0
        }
    }

    /* **********************************************************************

         Visuals

         ***********************************************************************/
    // visuals
    /*
         ================
         idEntity::Present

         Present is called to allow entities to generate refEntities, lights, etc for the renderer.
         ================
         */
    open fun Present() {
        if (!Game_local.gameLocal.isNewFrame) {
            return
        }

        // don't present to the renderer if the entity hasn't changed
        if (0 == (thinkFlags and TH_UPDATEVISUALS)) {
            return
        }
        BecomeInactive(TH_UPDATEVISUALS)

        // camera target for remote render views
        if (cameraTarget != null && Game_local.gameLocal.InPlayerPVS(this)) {
            renderEntity!!.remoteRenderView = cameraTarget!!.GetRenderView()
        }

        // if set to invisible, skip
        if (null == renderEntity!!.hModel || IsHidden()) {
            return
        }

        // add to refresh list
        if (modelDefHandle == -1) {
            modelDefHandle = gameRenderWorld!!.AddEntityDef(renderEntity!!)
        } else {
            gameRenderWorld!!.UpdateEntityDef(modelDefHandle, renderEntity!!)
        }
    }

    fun GetRenderEntity(): renderEntity_s? {
        return renderEntity
    }

    fun GetModelDefHandle(): Int {
        return modelDefHandle
    }

    open fun SetModel(modelname: String) {
        assert(modelname != null)
        FreeModelDef()
        renderEntity!!.hModel = ModelManager.renderModelManager.FindModel(modelname)
        if (renderEntity!!.hModel != null) {
            renderEntity!!.hModel!!.Reset()
        }
        renderEntity!!.callback = null
        renderEntity!!.numJoints = 0
        renderEntity!!.joints = null
        if (renderEntity!!.hModel != null) {
            renderEntity!!.bounds.set(renderEntity!!.hModel!!.Bounds(renderEntity))
        } else {
            renderEntity!!.bounds.Zero()
        }
        UpdateVisuals()
    }

    fun SetSkin(skin: idDeclSkin?) {
        renderEntity!!.customSkin = skin
        UpdateVisuals()
    }

    fun GetSkin(): idDeclSkin? {
        return renderEntity!!.customSkin
    }

    fun SetShaderParm(parmnum: Int, value: Float) {
        if (parmnum < 0 || parmnum >= Material.MAX_ENTITY_SHADER_PARMS) {
            Game_local.gameLocal.Warning("shader parm index (%d) out of range", parmnum)
            return
        }
        renderEntity!!.shaderParms[parmnum] = value
        UpdateVisuals()
    }

    open fun SetColor(red: Float, green: Float, blue: Float) {
        renderEntity!!.shaderParms[RenderWorld.SHADERPARM_RED] = red
        renderEntity!!.shaderParms[RenderWorld.SHADERPARM_GREEN] = green
        renderEntity!!.shaderParms[RenderWorld.SHADERPARM_BLUE] = blue
        UpdateVisuals()
    }

    open fun SetColor(color: idVec3) {
        SetColor(color[0], color[1], color[2])
        //	UpdateVisuals();
    }

    open fun GetColor(out: idVec3) {
        out[0] = renderEntity!!.shaderParms[RenderWorld.SHADERPARM_RED]
        out[1] = renderEntity!!.shaderParms[RenderWorld.SHADERPARM_GREEN]
        out[2] = renderEntity!!.shaderParms[RenderWorld.SHADERPARM_BLUE]
    }

    open fun SetColor(color: idVec4) {
        renderEntity!!.shaderParms[RenderWorld.SHADERPARM_RED] = color[0]
        renderEntity!!.shaderParms[RenderWorld.SHADERPARM_GREEN] = color[1]
        renderEntity!!.shaderParms[RenderWorld.SHADERPARM_BLUE] = color[2]
        renderEntity!!.shaderParms[RenderWorld.SHADERPARM_ALPHA] = color[3]
        UpdateVisuals()
    }

    open fun GetColor(out: idVec4) {
        out[0] = renderEntity!!.shaderParms[RenderWorld.SHADERPARM_RED]
        out[1] = renderEntity!!.shaderParms[RenderWorld.SHADERPARM_GREEN]
        out[2] = renderEntity!!.shaderParms[RenderWorld.SHADERPARM_BLUE]
        out[3] = renderEntity!!.shaderParms[RenderWorld.SHADERPARM_ALPHA]
    }

    open fun FreeModelDef() {
        if (modelDefHandle != -1) {
            gameRenderWorld!!.FreeEntityDef(modelDefHandle)
            modelDefHandle = -1
        }
    }

    open fun FreeLightDef() {}

    // D3XP: determine which timeline this entity runs on based on spawn arg "slowmo"
    fun DetermineTimeGroup(slowmo: Boolean) {
        timeGroup = if (slowmo || Game_local.gameLocal.isMultiplayer) {
            Game_local.TIME_GROUP1 // fast (player-speed) timeline
        } else {
            Game_local.TIME_GROUP2 // slow timeline (affected by slow-mo)
        }
    }

    // D3XP: grabbed state for the gravity gun (grabber) mechanic
    fun SetGrabbedState(grabbed: Boolean) {
        fl.grabbed = grabbed
    }

    fun IsGrabbed(): Boolean = fl.grabbed
    open fun Hide() {
        if (!IsHidden()) {
            fl.hidden = true
            FreeModelDef()
            UpdateVisuals()
        }
    }

    open fun Show() {
        if (IsHidden()) {
            fl.hidden = false
            UpdateVisuals()
        }
    }

    fun IsHidden(): Boolean {
        return fl.hidden
    }

    fun UpdateVisuals() {
        UpdateModel()
        UpdateSound()
    }

    fun UpdateModel() {
        if (isD3XP) {
            renderEntity!!.timeGroup = timeGroup
        }

        UpdateModelTransform()

        // check if the entity has an MD5 model
        val animator = GetAnimator()
        if (animator != null && animator.ModelHandle() != null) {
            // set the callback to update the joints
            renderEntity!!.callback = ModelCallback.getInstance()
        }

        // set to invalid number to force an update the next time the PVS areas are retrieved
        ClearPVSAreas()

        // ensure that we call Present this frame
        BecomeActive(TH_UPDATEVISUALS)

        // D3XP: if the entity has an xray skin, go ahead and add/update it
        if (isD3XP && xraySkin != null) {
            xrayEntity = renderEntity_s(renderEntity!!)
            xrayEntity!!.xrayIndex = 2
            xrayEntity!!.customSkin = xraySkin
            if (xrayEntityHandle == -1) {
                xrayEntityHandle = gameRenderWorld!!.AddEntityDef(xrayEntity!!)
            } else {
                gameRenderWorld!!.UpdateEntityDef(xrayEntityHandle, xrayEntity!!)
            }
        }
    }

    fun UpdateModelTransform() {
        val origin = idVec3()
        val axis = idMat3()
        if (GetPhysicsToVisualTransform(origin, axis)) {
            renderEntity!!.axis.set(axis * GetPhysics().GetAxis())
            renderEntity!!.origin.set(GetPhysics().GetOrigin() + origin * renderEntity!!.axis)
        } else {
            renderEntity!!.axis.set(GetPhysics().GetAxis())
            renderEntity!!.origin.set(GetPhysics().GetOrigin())
        }
    }

    open fun ProjectOverlay(origin: idVec3, dir: idVec3, size: Float, material: String) {
        var size = size
        val s = CFloat()
        val c = CFloat()
        val axis = idMat3()
        val axistemp = idMat3()
        val localOrigin = idVec3()
        val localAxis: Array<idVec3> = idVec3.generateArray(2)
        val localPlane: Array<idPlane> = idPlane.generateArray(2)

        // make sure the entity has a valid model handle
        if (modelDefHandle < 0) {
            return
        }

        // only do this on dynamic md5 models
        if (renderEntity!!.hModel!!.IsDynamicModel() != dynamicModel_t.DM_CACHED) {
            return
        }
        idMath.SinCos16(Game_local.gameLocal.random.RandomFloat() * idMath.TWO_PI, s, c)
        axis[2] = dir.unaryMinus()
        axis[2].NormalVectors(axistemp[0], axistemp[1])
        axis[0] = axistemp[0].times(c._val).plus(axistemp[1].times(-s._val))
        axis[1] = axistemp[0].times(-s._val).plus(axistemp[1].times(-c._val))
        renderEntity!!.axis.ProjectVector(origin.minus(renderEntity!!.origin), localOrigin)
        renderEntity!!.axis.ProjectVector(axis[0], localAxis[0])
        renderEntity!!.axis.ProjectVector(axis[1], localAxis[1])
        size = 1.0f / size
        localAxis[0].timesAssign(size)
        localAxis[1].timesAssign(size)
        localPlane[0].set(localAxis[0])
        localPlane[0][3] = -localOrigin.times(localAxis[0]) + 0.5f
        localPlane[1].set(localAxis[1])
        localPlane[1][3] = -localOrigin.times(localAxis[1]) + 0.5f
        val mtr: Material.idMaterial? = DeclManager.declManager.FindMaterial(material)

        // project an overlay onto the model
        gameRenderWorld!!.ProjectOverlay(modelDefHandle, localPlane as Array<idPlane?>, mtr)

        // make sure non-animating models update their overlay
        UpdateVisuals()
    }

    fun GetNumPVSAreas(): Int {
        if (numPVSAreas < 0) {
            UpdatePVSAreas()
        }
        return numPVSAreas
    }

    fun GetPVSAreas(): IntArray {
        if (numPVSAreas < 0) {
            UpdatePVSAreas()
        }
        return PVSAreas
    }

    fun ClearPVSAreas() {
        numPVSAreas = -1
    }

    /*
         ================
         idEntity::PhysicsTeamInPVS

         FIXME: for networking also return true if any of the entity shadows is in the PVS
         ================
         */
    fun PhysicsTeamInPVS(pvsHandle: pvsHandle_t): Boolean {
        var part: idEntity?
        if (teamMaster != null) {
            part = teamMaster
            while (part != null) {
                if (Game_local.gameLocal.pvs.InCurrentPVS(pvsHandle, part.GetPVSAreas(), part.GetNumPVSAreas())) {
                    return true
                }
                part = part.teamChain
            }
        } else {
            return Game_local.gameLocal.pvs.InCurrentPVS(pvsHandle, GetPVSAreas(), GetNumPVSAreas())
        }
        return false
    }

    // animation
    open fun UpdateAnimationControllers(): Boolean {
        // any ragdoll and IK animation controllers should be updated here
        return false
    }

    open fun UpdateRenderEntity(renderEntity: renderEntity_s, renderView: renderView_s?): Boolean {
        if (Game_local.gameLocal.inCinematic && Game_local.gameLocal.skipCinematic) {
            return false
        }
        val animator = GetAnimator()
        val ts = if (isD3XP) SetTimeState(timeGroup) else null
        try {
            return animator?.CreateFrame(Game_local.gameLocal.time, false) ?: false
        } finally {
            ts?.close()
        }
    }

    /*
         ================
         idEntity::GetAnimator

         Subclasses will be responsible for allocating animator.
         ================
         */
    open fun GetAnimator(): idAnimator? {    // returns animator object used by this entity
        return null
    }

    /* **********************************************************************

         Sound

         ***********************************************************************/
    // sound
    /*
         ================
         idEntity::CanPlayChatterSounds

         Used for playing chatter sounds on monsters.
         ================
         */
    open fun CanPlayChatterSounds(): Boolean {
        return true
    }

    fun StartSound(
        soundName: String,    /*s_channelType*/
        channel: Int,
        soundShaderFlags: Int,
        broadcast: Boolean,
        length: CInt? = null
    ): Boolean {
        val shader: idSoundShader?
        val sound = idStr()
        assert(idStr.Icmpn(soundName, "snd_", 4) == 0)
        if (!spawnArgs.GetString(soundName, "", sound)) {
            return false
        }
        if (sound.IsEmpty()) {
            return false
        }
        if (!Game_local.gameLocal.isNewFrame) {
            // don't play the sound, but don't report an error
            return true
        }
        shader = DeclManager.declManager.FindSound(sound)
        return StartSoundShader(shader, channel, soundShaderFlags, broadcast, length)
    }

    fun StartSound(
        soundName: String,
        channel: Enum<*>,
        soundShaderFlags: Int,
        broadcast: Boolean,
        length: CInt? = null
    ): Boolean {
        return StartSound(soundName, channel.ordinal, soundShaderFlags, broadcast, length)
    }

    fun StartSoundShader(
        shader: idSoundShader?,    /*s_channelType*/
        channel: Int,
        soundShaderFlags: Int,
        broadcast: Boolean,
        length: CInt? = null
    ): Boolean {
        val diversity: Float
        val len: Int
        if (null == shader) {
            return false
        }
        if (!Game_local.gameLocal.isNewFrame) {
            return true
        }
        if (Game_local.gameLocal.isServer && broadcast) {
            val msg = idBitMsg()
            val msgBuf = ByteBuffer.allocate(Game_local.MAX_EVENT_PARAM_SIZE)
            msg.Init(msgBuf, Game_local.MAX_EVENT_PARAM_SIZE)
            msg.BeginWriting()
            msg.WriteLong(Game_local.gameLocal.ServerRemapDecl(-1, declType_t.DECL_SOUND, shader.Index()))
            msg.WriteByte(channel.toByte())
            ServerSendEvent(EVENT_STARTSOUNDSHADER, msg, false, -1)
        }

        // set a random value for diversity unless one was parsed from the entity
        diversity = if (refSound.diversity < 0.0f) {
            Game_local.gameLocal.random.RandomFloat()
        } else {
            refSound.diversity
        }

        // if we don't have a soundEmitter allocated yet, get one now
        if (refSound.referenceSound == null) {
            refSound.referenceSound = Game_local.gameSoundWorld!!.AllocSoundEmitter()
        }
        UpdateSound()
        len = refSound.referenceSound!!.StartSound(shader, channel, diversity, soundShaderFlags)

        length?._val = len

        // set reference to the sound for shader synced effects
        renderEntity!!.referenceSound = refSound.referenceSound
        return true
    }

    fun StartSoundShader(
        shader: idSoundShader?,
        channel: Enum<*>,
        soundShaderFlags: Int,
        broadcast: Boolean,
        length: CInt
    ): Boolean {
        return StartSoundShader(shader, channel.ordinal, soundShaderFlags, broadcast, length)
    }

    fun StopSound(   /*s_channelType*/channel: Int,
                     broadcast: Boolean
    ) {    // pass SND_CHANNEL_ANY to stop all sounds
        if (!Game_local.gameLocal.isNewFrame) {
            return
        }
        if (Game_local.gameLocal.isServer && broadcast) {
            val msg = idBitMsg()
            val msgBuf = ByteBuffer.allocate(Game_local.MAX_EVENT_PARAM_SIZE)
            msg.Init(msgBuf, Game_local.MAX_EVENT_PARAM_SIZE)
            msg.BeginWriting()
            msg.WriteByte(channel.toByte())
            ServerSendEvent(EVENT_STOPSOUNDSHADER, msg, false, -1)
        }
        if (refSound.referenceSound != null) {
            refSound.referenceSound!!.StopSound(channel)
        }
    }

    /*
         ================
         idEntity::SetSoundVolume

         Must be called before starting a new sound.
         ================
         */
    fun SetSoundVolume(volume: Float) {
        refSound.parms.volume = volume
    }

    fun UpdateSound() {
        if (refSound.referenceSound != null) {
            val origin = idVec3()
            val axis = idMat3()
            if (GetPhysicsToSoundTransform(origin, axis)) {
                refSound.origin.set(GetPhysics().GetOrigin().plus(origin.times(axis)))
            } else {
                refSound.origin.set(GetPhysics().GetOrigin())
            }
            refSound.referenceSound!!.UpdateEmitter(refSound.origin, refSound.listenerId, refSound.parms)
        }
    }

    fun GetListenerId(): Int {
        return refSound.listenerId
    }

    fun GetSoundEmitter(): idSoundEmitter? {
        return refSound.referenceSound
    }

    fun FreeSoundEmitter(immediate: Boolean) {
        if (refSound.referenceSound != null) {
            refSound.referenceSound!!.Free(immediate)
            refSound.referenceSound = null
        }
    }

    /* **********************************************************************

         entity binding

         ***********************************************************************/
    // entity binding
    open fun PreBind() {}
    open fun PostBind() {}
    fun PreUnbind() {}
    fun PostUnbind() {}
    fun JoinTeam(teammember: idEntity?) {
        var ent: idEntity?
        var master: idEntity?
        var prev: idEntity?
        var next: idEntity?

        // if we're already on a team, quit it so we can join this one
        if (teamMaster != null && teamMaster !== this) {
            QuitTeam()
        }
        assert(teammember != null)
        if (teammember == this) {
            teamMaster = this
            return
        }

        // check if our new team mate is already on a team
        master = teammember!!.teamMaster
        if (null == master) {
            // he's not on a team, so he's the new teamMaster
            master = teammember
            teammember.teamMaster = teammember
            teammember.teamChain = this

            // make anyone who's bound to me part of the new team
            ent = teamChain
            while (ent != null) {
                ent.teamMaster = master
                ent = ent.teamChain
            }
        } else {
            // skip past the chain members bound to the entity we're teaming up with
            prev = teammember
            next = teammember.teamChain
            if (bindMaster != null) {
                // if we have a bindMaster, join after any entities bound to the entity
                // we're joining
                while (next != null && next.IsBoundTo(teammember)) {
                    prev = next
                    next = next.teamChain
                }
            } else {
                // if we're not bound to someone, then put us at the end of the team
                while (next != null) {
                    prev = next
                    next = next.teamChain
                }
            }

            // make anyone who's bound to me part of the new team and
            // also find the last member of my team
            ent = this
            while (ent!!.teamChain != null) {
                ent.teamChain!!.teamMaster = master
                ent = ent.teamChain
            }
            prev.teamChain = this
            ent.teamChain = next
        }
        teamMaster = master

        // reorder the active entity list
        Game_local.gameLocal.sortTeamMasters = true
    }

    /*
         ================
         idEntity::Bind

         bind relative to the visual position of the master
         ================
         */
    fun Bind(master: idEntity?, orientated: Boolean) {
        if (!InitBind(master)) {
            return
        }
        PreBind()
        bindJoint = Model.INVALID_JOINT
        bindBody = -1
        bindMaster = master
        fl.bindOrientated = orientated
        FinishBind()
        PostBind()
    }

    /*
         ================
         idEntity::BindToJoint

         bind relative to a joint of the md5 model used by the master
         ================
         */
    fun BindToJoint(master: idEntity, jointname: String, orientated: Boolean) {
        val   /*jointHandle_t*/jointnum: Int
        val masterAnimator: idAnimator?
        if (!InitBind(master)) {
            return
        }
        masterAnimator = master.GetAnimator()
        if (null == masterAnimator) {
            Game_local.gameLocal.Warning(
                "idEntity::BindToJoint: entity '%s' cannot support skeletal models.",
                master.GetName()
            )
            return
        }
        jointnum = masterAnimator.GetJointHandle(jointname)
        if (jointnum == Model.INVALID_JOINT) {
            Game_local.gameLocal.Warning(
                "idEntity::BindToJoint: joint '%s' not found on entity '%s'.",
                jointname,
                master.GetName()
            )
        }
        PreBind()
        bindJoint = jointnum
        bindBody = -1
        bindMaster = master
        fl.bindOrientated = orientated
        FinishBind()
        PostBind()
    }

    /*
         ================
         idEntity::BindToJoint

         bind relative to a joint of the md5 model used by the master
         ================
         */
    fun BindToJoint(master: idEntity?,    /*jointHandle_t*/jointnum: Int, orientated: Boolean) {
        if (!InitBind(master)) {
            return
        }
        PreBind()
        bindJoint = jointnum
        bindBody = -1
        bindMaster = master
        fl.bindOrientated = orientated
        FinishBind()
        PostBind()
    }

    /*
         ================
         idEntity::BindToBody

         bind relative to a collision model used by the physics of the master
         ================
         */
    fun BindToBody(master: idEntity?, bodyId: Int, orientated: Boolean) {
        if (!InitBind(master)) {
            return
        }
        if (bodyId < 0) {
            Game_local.gameLocal.Warning("idEntity::BindToBody: body '%d' not found.", bodyId)
        }
        PreBind()
        bindJoint = Model.INVALID_JOINT
        bindBody = bodyId
        bindMaster = master
        fl.bindOrientated = orientated
        FinishBind()
        PostBind()
    }

    fun Unbind() {
        var prev: idEntity?
        var next: idEntity?
        var last: idEntity?
        var ent: idEntity?

        // remove any bind constraints from an articulated figure
        if (this is idAFEntity_Base) {
            this.RemoveBindConstraints()
        }
        if (null == bindMaster) {
            return
        }
        if (null == teamMaster) {
            // Teammaster already has been freed
            bindMaster = null
            return
        }
        PreUnbind()
        if (physics != null) {
            physics.SetMaster(null, fl.bindOrientated)
        }

        // We're still part of a team, so that means I have to extricate myself
        // and any entities that are bound to me from the old team.
        // Find the node previous to me in the team
        prev = teamMaster
        ent = teamMaster!!.teamChain
        while (ent != null && ent !== this) {
            prev = ent
            ent = ent.teamChain
        }
        assert(
            ent == this // If ent is not pointing to this, then something is very wrong.
        )

        // Find the last node in my team that is bound to me.
        // Also find the first node not bound to me, if one exists.
        last = this
        next = teamChain
        while (next != null) {
            if (!next.IsBoundTo(this)) {
                break
            }

            // Tell them I'm now the teamMaster
            next.teamMaster = this
            last = next
            next = next.teamChain
        }

        // disconnect the last member of our team from the old team
        last.teamChain = null

        // connect up the previous member of the old team to the node that
        // follow the last node bound to me (if one exists).
        if (teamMaster !== this) {
            prev!!.teamChain = next
            if (null == next && teamMaster == prev) {
                prev.teamMaster = null
            }
        } else if (next != null) {
            // If we were the teamMaster, then the nodes that were not bound to me are now
            // a disconnected chain.  Make them into their own team.
            ent = next
            while (ent!!.teamChain != null) {
                ent.teamMaster = next
                ent = ent.teamChain
            }
            next.teamMaster = next
        }

        // If we don't have anyone on our team, then clear the team variables.
        teamMaster = if (teamChain != null) {
            // make myself my own team
            this
        } else {
            // no longer a team
            null
        }
        bindJoint = Model.INVALID_JOINT
        bindBody = -1
        bindMaster = null
        PostUnbind()
    }

    fun IsBound(): Boolean {
        return bindMaster != null
    }

    fun IsBoundTo(master: idEntity?): Boolean {
        var ent: idEntity?
        if (null == bindMaster) {
            return false
        }
        ent = bindMaster
        while (ent != null) {
            if (ent == master) {
                return true
            }
            ent = ent.bindMaster
        }
        return false
    }

    fun GetBindMaster(): idEntity? {
        return bindMaster
    }

    fun  /*jointHandle_t*/GetBindJoint(): Int {
        return bindJoint
    }

    fun GetBindBody(): Int {
        return bindBody
    }

    fun GetTeamMaster(): idEntity? {
        return teamMaster
    }

    fun GetNextTeamEntity(): idEntity? {
        return teamChain
    }

    fun ConvertLocalToWorldTransform(offset: idVec3, axis: idMat3) {
        UpdateModelTransform()
        offset.set(renderEntity!!.origin.plus(offset.times(renderEntity!!.axis)))
        axis.timesAssign(renderEntity!!.axis)
    }

    /*
         ================
         idEntity::GetLocalVector

         Takes a vector in worldspace and transforms it into the parent
         object's localspace.

         Note: Does not take origin into acount.  Use getLocalCoordinate to
         convert coordinates.
         ================
         */
    fun GetLocalVector(vec: idVec3): idVec3 {
        val pos = idVec3()
        if (null == bindMaster) {
            return vec
        }
        val masterOrigin = idVec3()
        val masterAxis = idMat3()
        GetMasterPosition(masterOrigin, masterAxis)
        masterAxis.ProjectVector(vec, pos)
        return pos
    }

    /*
         ================
         idEntity::GetLocalCoordinates

         Takes a vector in world coordinates and transforms it into the parent
         object's local coordinates.
         ================
         */
    fun GetLocalCoordinates(vec: idVec3): idVec3 {
        val pos = idVec3()
        if (null == bindMaster) {
            return vec
        }
        val masterOrigin = idVec3()
        val masterAxis = idMat3()
        GetMasterPosition(masterOrigin, masterAxis)
        masterAxis.ProjectVector(vec.minus(masterOrigin), pos)
        return pos
    }

    /*
         ================
         idEntity::GetWorldVector

         Takes a vector in the parent object's local coordinates and transforms
         it into world coordinates.

         Note: Does not take origin into acount.  Use getWorldCoordinate to
         convert coordinates.
         ================
         */
    fun GetWorldVector(vec: idVec3): idVec3 {
        val pos = idVec3()
        if (null == bindMaster) {
            return vec
        }
        val masterOrigin = idVec3()
        val masterAxis = idMat3()
        GetMasterPosition(masterOrigin, masterAxis)
        masterAxis.UnprojectVector(vec, pos)
        return pos
    }

    /*
         ================
         idEntity::GetWorldCoordinates

         Takes a vector in the parent object's local coordinates and transforms
         it into world coordinates.
         ================
         */
    fun GetWorldCoordinates(vec: idVec3): idVec3 {
        val pos = idVec3()
        if (null == bindMaster) {
            return vec
        }
        val masterOrigin = idVec3()
        val masterAxis = idMat3()
        GetMasterPosition(masterOrigin, masterAxis)
        masterAxis.UnprojectVector(vec, pos)
        pos.plusAssign(masterOrigin)
        return pos
    }

    fun GetMasterPosition(masterOrigin: idVec3, masterAxis: idMat3): Boolean {
        val masterAnimator: idAnimator?
        return if (bindMaster != null) {
            // if bound to a joint of an animated model
            if (bindJoint != Model.INVALID_JOINT) {
                masterAnimator = bindMaster!!.GetAnimator()
                if (null == masterAnimator) {
                    masterOrigin.set(vec3_origin)
                    masterAxis.set(idMat3.getMat3_identity())
                    return false
                } else {
                    masterAnimator.GetJointTransform(bindJoint, Game_local.gameLocal.time, masterOrigin, masterAxis)
                    masterAxis.timesAssign(bindMaster!!.renderEntity!!.axis)
                    masterOrigin.set(bindMaster!!.renderEntity!!.origin.plus(masterOrigin.times(bindMaster!!.renderEntity!!.axis)))
                }
            } else if (bindBody >= 0 && bindMaster!!.GetPhysics() != null) {
                masterOrigin.set(bindMaster!!.GetPhysics().GetOrigin(bindBody))
                masterAxis.set(bindMaster!!.GetPhysics().GetAxis(bindBody))
            } else {
                masterOrigin.set(bindMaster!!.renderEntity!!.origin)
                masterAxis.set(bindMaster!!.renderEntity!!.axis)
            }
            true
        } else {
            masterOrigin.set(vec3_origin)
            masterAxis.set(idMat3.getMat3_identity())
            false
        }
    }

    fun GetWorldVelocities(linearVelocity: idVec3, angularVelocity: idVec3) {
        linearVelocity.set(physics.GetLinearVelocity())
        angularVelocity.set(physics.GetAngularVelocity())
        if (bindMaster != null) {
            val masterOrigin = idVec3()
            val masterLinearVelocity = idVec3()
            val masterAngularVelocity = idVec3()
            val masterAxis = idMat3()

            // get position of master
            GetMasterPosition(masterOrigin, masterAxis)

            // get master velocities
            bindMaster!!.GetWorldVelocities(masterLinearVelocity, masterAngularVelocity)

            // linear velocity relative to master plus master linear and angular velocity
            linearVelocity.set(
                linearVelocity.times(masterAxis).plus(
                    masterLinearVelocity.plus(
                        masterAngularVelocity.Cross(
                            GetPhysics().GetOrigin().minus(masterOrigin)
                        )
                    )
                )
            )
        }
    }

    /* **********************************************************************

         Physics.

         ***********************************************************************/
    // physics
    // set a new physics object to be used by this entity
    fun SetPhysics(phys: idPhysics?) {
        // clear any contacts the current physics object has
        if (physics != null) {
            physics.ClearContacts()
        }
        // set new physics object or set the default physics if NULL
        if (phys != null) {
            defaultPhysicsObj.SetClipModel(null, 1.0f)
            physics = phys
            physics.Activate()
        } else {
            physics = defaultPhysicsObj
        }
        physics.UpdateTime(Game_local.gameLocal.time)
        physics.SetMaster(bindMaster, fl.bindOrientated)
    }

    // get the physics object used by this entity
    fun GetPhysics(): idPhysics {
        return physics
    }

    // restore physics pointer for save games
    fun RestorePhysics(phys: idPhysics) {
        assert(phys != null)
        // restore physics pointer
        physics = phys
    }

    // run the physics for this entity
    fun RunPhysics(): Boolean {
        var i: Int
        var reachedTime: Int
        val startTime: Int
        val endTime: Int
        var part: idEntity?
        var blockedPart: idEntity?
        var blockingEntity: idEntity? = null
        var moved: Boolean

        // don't run physics if not enabled
        if (0 == (thinkFlags and TH_PHYSICS)) {
            // however do update any animation controllers
            if (UpdateAnimationControllers()) {
                BecomeActive(TH_ANIMATE)
            }
            return false
        }

        // if this entity is a team slave don't do anything because the team master will handle everything
        if (teamMaster != null && teamMaster !== this) {
            return false
        }
        startTime = Game_local.gameLocal.previousTime
        endTime = Game_local.gameLocal.time
        Game_local.gameLocal.push.InitSavingPushedEntityPositions()
        blockedPart = null

        // save the physics state of the whole team and disable the team for collision detection
        part = this
        while (part != null) {
            if (part.physics != null) {
                if (!part.fl.solidForTeam) {
                    part.physics.DisableClip()
                }
                part.physics.SaveState()
            }
            part = part.teamChain
        }
        // move the whole team
        part = this
        while (part != null) {
            if (part.physics != null) {
                // run physics
                moved = part.physics.Evaluate(endTime - startTime, endTime)

                // check if the object is blocked
                blockingEntity = part.physics.GetBlockingEntity()
                if (blockingEntity != null) {
                    blockedPart = part
                    break
                }

                // if moved or forced to update the visual position and orientation from the physics
                if (moved || part.fl.forcePhysicsUpdate) {
                    part.UpdateFromPhysics(false)
                }

                // update any animation controllers here so an entity bound
                // to a joint of this entity gets the correct position
                if (part.UpdateAnimationControllers()) {
                    part.BecomeActive(TH_ANIMATE)
                }
            }
            part = part.teamChain
        }

        // enable the whole team for collision detection
        part = this
        while (part != null) {
            if (part.physics != null) {
                if (!part.fl.solidForTeam) {
                    part.physics.EnableClip()
                }
            }
            part = part.teamChain
        }

        // if one of the team entities is a pusher and blocked
        if (blockedPart != null) {
            // move the parts back to the previous position
            part = this
            while (part !== blockedPart) {
                if (part!!.physics != null) {

                    // restore the physics state
                    part.physics.RestoreState()

                    // move back the visual position and orientation
                    part.UpdateFromPhysics(true)
                }
                part = part.teamChain
            }
            part = this
            while (part != null) {
                if (part.physics != null) {
                    // update the physics time without moving
                    part.physics.UpdateTime(endTime)
                }
                part = part.teamChain
            }

            // restore the positions of any pushed entities
            Game_local.gameLocal.push.RestorePushedEntityPositions()
            if (Game_local.gameLocal.isClient) {
                return false
            }

            // if the master pusher has a "blocked" function, call it
            Signal(signalNum_t.SIG_BLOCKED)
            ProcessEvent(EV_TeamBlocked, blockedPart, blockingEntity)
            // call the blocked function on the blocked part
            blockedPart.ProcessEvent(EV_PartBlocked, blockingEntity)
            return false
        }

        // set pushed
        i = 0
        while (i < Game_local.gameLocal.push.GetNumPushedEntities()) {
            val ent: idEntity = Game_local.gameLocal.push.GetPushedEntity(i)
            ent.physics.SetPushed(endTime - startTime)
            i++
        }
        if (Game_local.gameLocal.isClient) {
            return true
        }

        // post reached event if the current time is at or past the end point of the motion
        part = this
        while (part != null) {
            if (part.physics != null) {
                reachedTime = part.physics.GetLinearEndTime()
                if (startTime < reachedTime && endTime >= reachedTime) {
                    part.ProcessEvent(EV_ReachedPos)
                }
                reachedTime = part.physics.GetAngularEndTime()
                if (startTime < reachedTime && endTime >= reachedTime) {
                    part.ProcessEvent(EV_ReachedAng)
                }
            }
            part = part.teamChain
        }
        return true
    }

    // set the origin of the physics object (relative to bindMaster if not NULL)
    fun SetOrigin(org: idVec3) {
        GetPhysics().SetOrigin(org)
        UpdateVisuals()
    }

    // set the axis of the physics object (relative to bindMaster if not NULL)
    fun SetAxis(axis: idMat3) {
        if (GetPhysics() is idPhysics_Actor) {
            (this as idActor).viewAxis.set(axis)
        } else {
            GetPhysics().SetAxis(axis)
        }
        UpdateVisuals()
    }

    // use angles to set the axis of the physics object (relative to bindMaster if not NULL)
    fun SetAngles(ang: idAngles) {
        SetAxis(ang.ToMat3())
    }

    fun SetAngles(ang: idVec3) {
        SetAxis(ang.ToAngles().ToMat3())
    }

    // get the floor position underneath the physics object
    fun GetFloorPos(max_dist: Float, floorpos: idVec3): Boolean {
        val result = trace_s()
        return if (!GetPhysics().HasGroundContacts()) {
            GetPhysics().ClipTranslation(result, GetPhysics().GetGravityNormal().times(max_dist), null)
            if (result.fraction < 1.0f) {
                floorpos.set(result.endpos)
                true
            } else {
                floorpos.set(GetPhysics().GetOrigin())
                false
            }
        } else {
            floorpos.set(GetPhysics().GetOrigin())
            true
        }
    }

    // retrieves the transformation going from the physics origin/axis to the visual origin/axis
    open fun GetPhysicsToVisualTransform(origin: idVec3, axis: idMat3): Boolean {
        return false
    }

    // };
    //
    // retrieves the transformation going from the physics origin/axis to the sound origin/axis
    open fun GetPhysicsToSoundTransform(origin: idVec3, axis: idMat3): Boolean {
        // by default play the sound at the center of the bounding box of the first clip model
        if (GetPhysics().GetNumClipModels() > 0) {
            origin.set(GetPhysics().GetBounds().GetCenter())
            axis.Identity()
            return true
        }
        return false
    }

    // called from the physics object when colliding, should return true if the physics simulation should stop
    open fun Collide(collision: trace_s, velocity: idVec3): Boolean {
        // this entity collides with collision.c.entityNum
        return false
    }

    // retrieves impact information, 'ent' is the entity retrieving the info
    open fun GetImpactInfo(ent: idEntity?, id: Int, point: idVec3): impactInfo_s {
        return GetPhysics().GetImpactInfo(id, point)
    }

    // apply an impulse to the physics object, 'ent' is the entity applying the impulse
    open fun ApplyImpulse(ent: idEntity?, id: Int, point: idVec3, impulse: idVec3) {
        GetPhysics().ApplyImpulse(id, point, impulse)
    }

    // add a force to the physics object, 'ent' is the entity adding the force
    open fun AddForce(ent: idEntity?, id: Int, point: idVec3, force: idVec3) {
        GetPhysics().AddForce(id, point, force)
    }

    // activate the physics object, 'ent' is the entity activating this entity
    fun ActivatePhysics(ent: idEntity?) {
        GetPhysics().Activate()
    }

    // returns true if the physics object is at rest
    fun IsAtRest(): Boolean {
        return GetPhysics().IsAtRest()
    }

    // returns the time the physics object came to rest
    fun GetRestStartTime(): Int {
        return GetPhysics().GetRestStartTime()
    }

    // add a contact entity
    fun AddContactEntity(ent: idEntity) {
        GetPhysics().AddContactEntity(ent)
    }

    // remove a touching entity
    fun RemoveContactEntity(ent: idEntity) {
        GetPhysics().RemoveContactEntity(ent)
    }

    /* **********************************************************************

         Damage

         ***********************************************************************/
    // damage
    /*
         ============
         idEntity::CanDamage

         Returns true if the inflictor can directly damage the target.  Used for
         explosions and melee attacks.
         ============
         */
    // returns true if this entity can be damaged from the given origin
    fun CanDamage(origin: idVec3, damagePoint: idVec3): Boolean {
        val dest = idVec3()
        val tr = trace_s()
        val midpoint = idVec3()

        // use the midpoint of the bounds instead of the origin, because
        // bmodels may have their origin at 0,0,0
        midpoint.set(
            GetPhysics().GetAbsBounds()[0].plus(GetPhysics().GetAbsBounds()[1]).times(0.5f)
        )
        dest.set(midpoint)
        Game_local.gameLocal.clip.TracePoint(tr, origin, dest, Game_local.MASK_SOLID, null)
        if (tr.fraction == 1.0f || Game_local.gameLocal.GetTraceEntity(tr) == this) {
            damagePoint.set(tr.endpos)
            return true
        }

        // this should probably check in the plane of projection, rather than in world coordinate
        dest.set(midpoint)
        dest.plusAssign(0, 15.0f)
        dest.plusAssign(1, 15.0f)
        Game_local.gameLocal.clip.TracePoint(tr, origin, dest, Game_local.MASK_SOLID, null)
        if (tr.fraction == 1.0f || Game_local.gameLocal.GetTraceEntity(tr) == this) {
            damagePoint.set(tr.endpos)
            return true
        }
        dest.set(midpoint)
        dest.plusAssign(0, 15.0f)
        dest.minusAssign(1, 15.0f)
        Game_local.gameLocal.clip.TracePoint(tr, origin, dest, Game_local.MASK_SOLID, null)
        if (tr.fraction == 1.0f || Game_local.gameLocal.GetTraceEntity(tr) == this) {
            damagePoint.set(tr.endpos)
            return true
        }
        dest.set(midpoint)
        dest.minusAssign(0, 15.0f)
        dest.plusAssign(1, 15.0f)
        Game_local.gameLocal.clip.TracePoint(tr, origin, dest, Game_local.MASK_SOLID, null)
        if (tr.fraction == 1.0f || Game_local.gameLocal.GetTraceEntity(tr) == this) {
            damagePoint.set(tr.endpos)
            return true
        }
        dest.set(midpoint)
        dest.minusAssign(0, 15.0f)
        dest.minusAssign(1, 15.0f)
        Game_local.gameLocal.clip.TracePoint(tr, origin, dest, Game_local.MASK_SOLID, null)
        if (tr.fraction == 1.0f || Game_local.gameLocal.GetTraceEntity(tr) == this) {
            damagePoint.set(tr.endpos)
            return true
        }
        dest.set(midpoint)
        dest.plusAssign(2, 15.0f)
        Game_local.gameLocal.clip.TracePoint(tr, origin, dest, Game_local.MASK_SOLID, null)
        if (tr.fraction == 1.0f || Game_local.gameLocal.GetTraceEntity(tr) == this) {
            damagePoint.set(tr.endpos)
            return true
        }
        dest.set(midpoint)
        dest.minusAssign(2, 15.0f)
        Game_local.gameLocal.clip.TracePoint(tr, origin, dest, Game_local.MASK_SOLID, null)
        if (tr.fraction == 1.0f || Game_local.gameLocal.GetTraceEntity(tr) == this) {
            damagePoint.set(tr.endpos)
            return true
        }
        return false
    }

    /*
         ============
         Damage

         this		entity that is being damaged
         inflictor	entity that is causing the damage
         attacker	entity that caused the inflictor to damage targ
         example: this=monster, inflictor=rocket, attacker=player

         dir			direction of the attack for knockback in global space
         point		point at which the damage is being inflicted, used for headshots
         damage		amount of damage being inflicted

         inflictor, attacker, dir, and point can be NULL for environmental effects

         ============
         */
    // applies damage to this entity
    open fun Damage(
        inflictor: idEntity?,
        attacker: idEntity?,
        dir: idVec3,
        damageDefName: String,
        damageScale: Float,
        location: Int
    ) {
        var inflictor = inflictor
        var attacker = attacker
        val ts = if (isD3XP) SetTimeState(timeGroup) else null
        try {
        if (!fl.takedamage) {
            return
        }
        if (null == inflictor) {
            inflictor = Game_local.gameLocal.world
        }
        if (null == attacker) {
            attacker = Game_local.gameLocal.world
        }
        val damageDef = Game_local.gameLocal.FindEntityDefDict(damageDefName, false)
        if (null == damageDef) {
            idGameLocal.Error("Unknown damageDef '%s'\n", damageDefName)
            return
        }
        val damage = CInt(damageDef.GetInt("damage"))

        // inform the attacker that they hit someone
        attacker!!.DamageFeedback(this, inflictor, damage)
        if (0 != damage._val) {
            // do the damage
            health -= damage._val
            if (health <= 0) {
                if (health < -999) {
                    health = -999
                }
                Killed(inflictor, attacker, damage._val, dir, location)
            } else {
                Pain(inflictor, attacker, damage._val, dir, location)
            }
        }
        } finally {
            ts?.close()
        }
    }

    // adds a damage effect like overlays, blood, sparks, debris etc.
    open fun AddDamageEffect(collision: trace_s, velocity: idVec3, damageDefName: String) {
        var sound: String?
        var decal: String?
        var key: String?
        val def = Game_local.gameLocal.FindEntityDef(damageDefName, false) ?: return
        val materialType = Game_local.gameLocal.sufaceTypeNames[collision.c.material!!.GetSurfaceType().ordinal]

        // start impact sound based on material type
        key = va("snd_%s", materialType)
        sound = spawnArgs.GetString(key)
        if (sound.isEmpty()) { // == '\0' ) {
            sound = def.dict.GetString(key)
        }
        if (!sound.isEmpty()) { // != '\0' ) {
            StartSoundShader(
                DeclManager.declManager.FindSound(sound),
                gameSoundChannel_t.SND_CHANNEL_BODY.ordinal,
                0,
                false
            )
        }
        if (SysCvar.g_decals.GetBool()) {
            // place a wound overlay on the model
            key = va("mtr_wound_%s", materialType)
            decal = spawnArgs.RandomPrefix(key, Game_local.gameLocal.random)!!
            if (decal.isEmpty()) { // == '\0' ) {
                decal = def.dict.RandomPrefix(key, Game_local.gameLocal.random)
            }
            if (!decal!!.isEmpty()) { // != '\0' ) {
                val dir = idVec3(velocity)
                dir.Normalize()
                ProjectOverlay(collision.c.point, dir, 20.0f, decal)
            }
        }
    }

    /*
         ================
         idEntity::DamageFeedback

         callback function for when another entity received damage from this entity.  damage can be adjusted and returned to the caller.
         ================
         */
    // callback function for when another entity received damage from this entity.  damage can be adjusted and returned to the caller.
    open fun DamageFeedback(victim: idEntity?, inflictor: idEntity?, damage: CInt) {
        // implemented in subclasses
    }

    /*
         ============
         idEntity::Pain

         Called whenever an entity recieves damage.  Returns whether the entity responds to the pain.
         This is a virtual function that subclasses are expected to implement.
         ============
         */
    // notifies this entity that it is in pain
    open fun Pain(inflictor: idEntity?, attacker: idEntity?, damage: Int, dir: idVec3, location: Int): Boolean {
        return false
    }

    /*
         ============
         idEntity::Killed

         Called whenever an entity's health is reduced to 0 or less.
         This is a virtual function that subclasses are expected to implement.
         ============
         */
    // notifies this entity that is has been killed
    open fun Killed(inflictor: idEntity?, attacker: idEntity?, damage: Int, dir: idVec3, location: Int) {}

    /* **********************************************************************

         Script functions

         ***********************************************************************/
    // scripting
    /*
         ================
         idEntity::ShouldConstructScriptObjectAtSpawn

         Called during idEntity::Spawn to see if it should construct the script object or not.
         Overridden by subclasses that need to spawn the script object themselves.
         ================
         */
    open fun ShouldConstructScriptObjectAtSpawn(): Boolean {
        return true
    }

    /*
         ================
         idEntity::ConstructScriptObject

         Called during idEntity::Spawn.  Calls the constructor on the script object.
         Can be overridden by subclasses when a thread doesn't need to be allocated.
         ================
         */
    open fun ConstructScriptObject(): idThread? {
        val thread: idThread?
        val constructor: function_t?

        // init the script object's data
        scriptObject.ClearObject()

        // call script object's constructor
        constructor = scriptObject.GetConstructor()
        if (constructor != null) {
            // start a thread that will initialize after Spawn is done being called
            thread = idThread()
            thread.SetThreadName(name.toString())
            thread.CallFunction(this, constructor, true)
            thread.DelayedStart(0)
        } else {
            thread = null
        }

        // clear out the object's memory
        scriptObject.ClearObject()
        return thread
    }

    /*
         ================
         idEntity::DeconstructScriptObject

         Called during idEntity::~idEntity.  Calls the destructor on the script object.
         Can be overridden by subclasses when a thread doesn't need to be allocated.
         Not called during idGameLocal::MapShutdown.
         ================
         */
    open fun DeconstructScriptObject() {
        val thread: idThread
        val destructor: function_t?

        // don't bother calling the script object's destructor on map shutdown
        if (Game_local.gameLocal.GameState() == gameState_t.GAMESTATE_SHUTDOWN) {
            return
        }

        // call script object's destructor
        destructor = scriptObject.GetDestructor()
        if (destructor != null) {
            // start a thread that will run immediately and be destroyed
            thread = idThread()
            thread.SetThreadName(name.toString())
            thread.CallFunction(this, destructor, true)
            thread.Execute()
            //		delete thread;
        }
    }

    fun SetSignal(_signalnum: signalNum_t, thread: idThread?, function: function_t?) {
        SetSignal(_signalnum.ordinal, thread, function)
    }

    fun SetSignal(_signalnum: Int, thread: idThread?, function: function_t?) {
        var i: Int
        val num: Int
        val sig = signal_t()
        val threadnum: Int
        assert(_signalnum >= 0 && _signalnum < signalNum_t.NUM_SIGNALS.ordinal)
        if (null == signals) {
            signals = signalList_t()
        }
        assert(thread != null)
        threadnum = thread!!.GetThreadNum()
        num = signals!!.signal[_signalnum].Num()
        i = 0
        while (i < num) {
            if (signals!!.signal[_signalnum][i].threadnum == threadnum) {
                signals!!.signal[_signalnum][i].function = function
                return
            }
            i++
        }
        if (num >= MAX_SIGNAL_THREADS) {
            thread.Error("Exceeded maximum number of signals per object")
        }
        sig.threadnum = threadnum
        sig.function = function
        signals!!.signal[_signalnum].Append(sig)
    }

    fun ClearSignal(thread: idThread, _signalnum: signalNum_t) {
        val signalnum = _signalnum.ordinal
        assert(thread != null)
        if (signalnum < 0 || signalnum >= signalNum_t.NUM_SIGNALS.ordinal) {
            idGameLocal.Error("Signal out of range")
        }
        if (null == signals) {
            return
        }
        signals!!.signal[signalnum].Clear()
    }

    fun ClearSignalThread(_signalnum: signalNum_t, thread: idThread?) {
        ClearSignalThread(_signalnum.ordinal, thread)
    }

    fun ClearSignalThread(_signalnum: Int, thread: idThread?) {
        var i: Int
        val num: Int
        val threadnum: Int
        assert(thread != null)
        if (_signalnum < 0 || _signalnum >= signalNum_t.NUM_SIGNALS.ordinal) {
            idGameLocal.Error("Signal out of range")
        }
        if (null == signals) {
            return
        }
        threadnum = thread!!.GetThreadNum()
        num = signals!!.signal[_signalnum].Num()
        i = 0
        while (i < num) {
            if (signals!!.signal[_signalnum][i].threadnum == threadnum) {
                signals!!.signal[_signalnum].RemoveIndex(i)
                return
            }
            i++
        }
    }

    fun HasSignal(_signalnum: signalNum_t): Boolean {
        val signalnum = _signalnum.ordinal
        if (null == signals) {
            return false
        }
        assert(signalnum >= 0 && signalnum < signalNum_t.NUM_SIGNALS.ordinal)
        return signals!!.signal[signalnum].Num() > 0
    }

    fun Signal(_signalnum: signalNum_t) {
        var i: Int
        val num: Int
        val sigs = arrayOfNulls<signal_t>(MAX_SIGNAL_THREADS)
        var thread: idThread?
        val signalnum = _signalnum.ordinal
        assert(signalnum >= 0 && signalnum < signalNum_t.NUM_SIGNALS.ordinal)
        if (null == signals) {
            return
        }

        // we copy the signal list since each thread has the potential
        // to end any of the threads in the list.  By copying the list
        // we don't have to worry about the list changing as we're
        // processing it.
        num = signals!!.signal[signalnum].Num()
        i = 0
        while (i < num) {
            sigs[i] = signals!!.signal[signalnum][i]
            i++
        }

        // clear out the signal list so that we don't get into an infinite loop
        signals!!.signal[signalnum].Clear()
        i = 0
        while (i < num) {
            thread = idThread.GetThread(sigs[i]!!.threadnum)
            if (thread != null) {
                thread.CallFunction(this, sigs[i]!!.function!!, true)
                thread.Execute()
            }
            i++
        }
    }

    fun SignalEvent(thread: idThread?, _signalNum: signalNum_t) {
        val signalNum = TempDump.etoi(_signalNum)
        if (signalNum < 0 || signalNum >= TempDump.etoi(signalNum_t.NUM_SIGNALS)) {
            idGameLocal.Error("Signal out of range")
        }
        if (null == signals) {
            return
        }
        Signal(_signalNum)
    }

    /* **********************************************************************

         Guis.

         ***********************************************************************/
    // gui
    fun TriggerGuis() {
        var i: Int
        i = 0
        while (i < RenderWorld.MAX_RENDERENTITY_GUI) {
            if (renderEntity!!.gui[i] != null) {
                renderEntity!!.gui[i]!!.Trigger(Game_local.gameLocal.time)
            }
            i++
        }
    }

    fun HandleGuiCommands(entityGui: idEntity?, cmds: String?): Boolean {
        var targetEnt: idEntity?
        var ret = false
        if (entityGui != null && cmds != null && !cmds.isEmpty()) {
            val src = idLexer()
            val token = idToken()
            val token2 = idToken()
            var token3 = idToken()
            val token4 = idToken()
            src.LoadMemory(cmds, cmds.length, "guiCommands")
            while (true) {
                if (!src.ReadToken(token)) {
                    return ret
                }
                if (token.toString() == ";") {
                    continue
                }
                if (token.Icmp("activate") == 0) {
                    var targets = true
                    if (src.ReadToken(token2)) {
                        if (token2.toString() == ";") {
                            src.UnreadToken(token2)
                        } else {
                            targets = false
                        }
                    }
                    if (targets) {
                        entityGui.ActivateTargets(this)
                    } else {
                        val ent: idEntity? = Game_local.gameLocal.FindEntity(token2)
                        if (ent != null) {
                            ent.Signal(signalNum_t.SIG_TRIGGER)
                            ent.PostEventMS(EV_Activate, 0, this)
                        }
                    }
                    entityGui.renderEntity!!.shaderParms[RenderWorld.SHADERPARM_MODE] = 1.0f
                    continue
                }
                if (token.Icmp("runScript") == 0) {
                    if (src.ReadToken(token2)) {
                        while (src.CheckTokenString("::")) {
                            token3 = idToken()
                            if (!src.ReadToken(token3)) {
                                idGameLocal.Error(
                                    "Expecting function name following '::' in gui for entity '%s'",
                                    entityGui.name
                                )
                            }
                            token2.Append("::$token3")
                        }
                        val func = Game_local.gameLocal.program.FindFunction(token2)
                        if (null == func) {
                            idGameLocal.Error(
                                "Can't find function '%s' for gui in entity '%s'",
                                token2,
                                entityGui.name
                            )
                        } else {
                            val thread = idThread(func)
                            thread.DelayedStart(0)
                        }
                    }
                    continue
                }
                if (token.Icmp("play") == 0) {
                    if (src.ReadToken(token2)) {
                        val shader = DeclManager.declManager.FindSound(token2)
                        entityGui.StartSoundShader(shader, gameSoundChannel_t.SND_CHANNEL_ANY.ordinal, 0, false)
                    }
                    continue
                }
                if (token.Icmp("setkeyval") == 0) {
                    if (src.ReadToken(token2) && src.ReadToken(token3) && src.ReadToken(token4)) {
                        val ent: idEntity? = Game_local.gameLocal.FindEntity(token2)
                        if (ent != null) {
                            ent.spawnArgs.Set(token3, token4)
                            ent.UpdateChangeableSpawnArgs(null)
                            ent.UpdateVisuals()
                        }
                    }
                    continue
                }
                if (token.Icmp("setshaderparm") == 0) {
                    if (src.ReadToken(token2) && src.ReadToken(token3)) {
                        entityGui.SetShaderParm(token2.toString().toInt(), token3.toString().toFloat())
                        entityGui.UpdateVisuals()
                    }
                    continue
                }
                if (token.Icmp("close") == 0) {
                    ret = true
                    continue
                }
                if (0 == token.Icmp("turkeyscore")) {
                    if (src.ReadToken(token2) && entityGui.renderEntity!!.gui[0] != null) {
                        var score = entityGui.renderEntity!!.gui[0]!!.State().GetInt("score")
                        score += token2.toString().toInt()
                        entityGui.renderEntity!!.gui[0]!!.SetStateInt("score", score)
                        if (Game_local.gameLocal.GetLocalPlayer() != null && score >= 25000 && !Game_local.gameLocal.GetLocalPlayer()!!.inventory.turkeyScore) {
                            Game_local.gameLocal.GetLocalPlayer()!!.GiveEmail("highScore")
                            Game_local.gameLocal.GetLocalPlayer()!!.inventory.turkeyScore = true
                        }
                    }
                    continue
                }

                if (isD3XP && 0 == token.Icmp("martianbuddycomplete")) {
                    Game_local.gameLocal.GetLocalPlayer()?.GiveEmail("MartianBuddyGameComplete")
                    continue
                }

                // handy for debugging GUI stuff
                if (0 == token.Icmp("print")) {
                    var msg = ""
                    while (src.ReadToken(token2)) {
                        if (token2.toString() == ";") {
                            src.UnreadToken(token2)
                            break
                        }
                        msg += token2.toString()
                    }
                    idLib.common.Printf("ent gui 0x%x '%s': %s\n", entityNumber, name, msg)
                    continue
                }

                // if we get to this point we don't know how to handle it
                src.UnreadToken(token)
                if (!HandleSingleGuiCommand(entityGui, src)) {
                    // not handled there see if entity or any of its targets can handle it
                    // this will only work for one target atm
                    if (entityGui.HandleSingleGuiCommand(entityGui, src)) {
                        continue
                    }
                    val c = entityGui.targets.Num()
                    var i: Int
                    i = 0
                    while (i < c) {
                        targetEnt = entityGui.targets[i].GetEntity()
                        if (targetEnt != null && targetEnt.HandleSingleGuiCommand(entityGui, src)) {
                            break
                        }
                        i++
                    }
                    if (i == c) {
                        // not handled
                        idLib.common.DPrintf("idEntity::HandleGuiCommands: '%s' not handled\n", token.toString())
                        src.ReadToken(token)
                    }
                }
            }
        }
        return ret
    }

    open fun HandleSingleGuiCommand(entityGui: idEntity?, src: idLexer): Boolean {
        return false
    }

    /* **********************************************************************

         Targets

         ***********************************************************************/
    // targets
    /*
         ===============
         idEntity::FindTargets

         We have to wait until all entities are spawned
         Used to build lists of targets after the entity is spawned.  Since not all entities
         have been spawned when the entity is created at map load time, we have to wait
         ===============
         */
    fun FindTargets() {
        var i: Int

        // targets can be a list of multiple names
        Game_local.gameLocal.GetTargets(spawnArgs, targets, "target")

        // ensure that we don't target ourselves since that could cause an infinite loop when activating entities
        i = 0
        while (i < targets.Num()) {
            if (targets[i].GetEntity() == this) {
                idGameLocal.Error("Entity '%s' is targeting itself", name)
            }
            i++
        }
    }

    fun RemoveNullTargets() {
        var i: Int
        i = targets.Num() - 1
        while (i >= 0) {
            if (targets[i].GetEntity() == null) {
                targets.RemoveIndex(i)
            }
            i--
        }
    }

    /*
         ==============================
         idEntity::ActivateTargets

         "activator" should be set to the entity that initiated the firing.
         ==============================
         */
    fun ActivateTargets(activator: idEntity?) {
        var ent: idEntity?
        var i: Int
        var j: Int
        i = 0
        while (i < targets.Num()) {
            ent = targets[i].GetEntity()
            if (null == ent) {
                i++
                continue
            }
            if (ent.RespondsTo(EV_Activate) || ent.HasSignal(signalNum_t.SIG_TRIGGER)) {
                ent.Signal(signalNum_t.SIG_TRIGGER)
                ent.ProcessEvent(EV_Activate, activator)
            }
            j = 0
            while (j < RenderWorld.MAX_RENDERENTITY_GUI) {
                if (ent.renderEntity!!.gui[j] != null) {
                    ent.renderEntity!!.gui[j]!!.Trigger(Game_local.gameLocal.time)
                }
                j++
            }
            i++
        }
    }

    /* **********************************************************************

         Misc.

         ***********************************************************************/
    // misc
    open fun Teleport(origin: idVec3, angles: idAngles, destination: idEntity?) {
        GetPhysics().SetOrigin(origin)
        GetPhysics().SetAxis(angles.ToMat3())
        UpdateVisuals()
    }

    /*
         ============
         idEntity::TouchTriggers

         Activate all trigger entities touched at the current position.
         ============
         */
    fun TouchTriggers(): Boolean {
        var i: Int
        val numClipModels: Int
        var numEntities: Int
        var cm: idClipModel
        val clipModels = arrayOfNulls<idClipModel>(Game_local.MAX_GENTITIES)
        var ent: idEntity?
        val trace = trace_s() //memset( &trace, 0, sizeof( trace ) );
        trace.endpos.set(GetPhysics().GetOrigin())
        trace.endAxis.set(GetPhysics().GetAxis())
        numClipModels = Game_local.gameLocal.clip.ClipModelsTouchingBounds(
            GetPhysics().GetAbsBounds(),
            Material.CONTENTS_TRIGGER,
            clipModels,
            Game_local.MAX_GENTITIES
        )
        numEntities = 0
        i = 0
        while (i < numClipModels) {
            cm = clipModels[i]!!

            // don't touch it if we're the owner
            if (cm.GetOwner() == this) {
                i++
                continue
            }
            ent = cm.GetEntity()!!
            if (!ent.RespondsTo(EV_Touch) && !ent.HasSignal(signalNum_t.SIG_TOUCH)) {
                i++
                continue
            }
            if (GetPhysics().ClipContents(cm) == 0) {
                i++
                continue
            }
            numEntities++
            trace.c.contents = cm.GetContents()
            trace.c.entityNum = cm.GetEntity()!!.entityNumber
            trace.c.id = cm.GetId()
            val ts = if (isD3XP) SetTimeState(ent.timeGroup) else null
            try {
            ent.Signal(signalNum_t.SIG_TOUCH)
            ent.ProcessEvent(EV_Touch, this, trace)
            if (Game_local.gameLocal.entities[entityNumber] == null) {
                Game_local.gameLocal.Printf("entity was removed while touching triggers\n")
                return true
            }
            } finally {
                ts?.close()
            }
            i++
        }
        return numEntities != 0
    }

    fun GetSpline(): idCurve_Spline<idVec3>? {
        var i: Int
        val numPoints: Int
        var t: Int
        val kv: idKeyValue?
        val lex = idLexer()
        val v = idVec3()
        val spline: idCurve_Spline<idVec3>
        val curveTag = "curve_"
        kv = spawnArgs.MatchPrefix(curveTag)
        if (null == kv) {
            return null
        }
        val str = kv.GetKey().Right(kv.GetKey().Length() - curveTag.length)
        spline = if (str.Icmp("CatmullRomSpline") == 0) {
            idCurve_CatmullRomSpline(idVec3::class.java)
        } else if (str.Icmp("nubs") == 0) {
            idCurve_NonUniformBSpline(idVec3::class.java)
        } else if (str.Icmp("nurbs") == 0) {
            idCurve_NURBS(idVec3::class.java)
        } else {
            idCurve_BSpline(idVec3::class.java)
        }
        spline.SetBoundaryType(idCurve_Spline.BT_CLAMPED)
        lex.LoadMemory(kv.GetValue().toString(), kv.GetValue().Length(), curveTag)
        numPoints = lex.ParseInt()
        lex.ExpectTokenString("(")
        t = 0.also { i = it }
        while (i < numPoints) {
            v.x = lex.ParseFloat()
            v.y = lex.ParseFloat()
            v.z = lex.ParseFloat()
            spline.AddValue(t.toFloat(), idVec3(v))
            i++
            t += 100
        }
        lex.ExpectTokenString(")")
        return spline
    }

    open fun ShowEditingDialog() {}

    /* **********************************************************************

         Network

         ***********************************************************************/
    open fun ClientPredictionThink() {
        RunPhysics()
        Present()
    }

    open fun WriteToSnapshot(msg: idBitMsgDelta) {}
    open fun ReadFromSnapshot(msg: idBitMsgDelta) {}
    open fun ServerReceiveEvent(event: Int, time: Int, msg: idBitMsg?): Boolean {
        return when (event) {
            0 -> false
            else -> false
        }
    }

    open fun ClientReceiveEvent(event: Int, time: Int, msg: idBitMsg): Boolean {
        val index: Int
        val shader: idSoundShader?
        val   /*s_channelType*/channel: Int
        return when (event) {
            EVENT_STARTSOUNDSHADER -> {
                assert(Game_local.gameLocal.isNewFrame)
                if (time < Game_local.gameLocal.realClientTime - 1000) {
                    // too old, skip it ( reliable messages don't need to be parsed in full )
                    idLib.common.DPrintf(
                        "ent 0x%x: start sound shader too old (%d ms)\n",
                        entityNumber.toString(),
                        (Game_local.gameLocal.realClientTime - time).toString()
                    )
                    return true
                }
                index = Game_local.gameLocal.ClientRemapDecl(declType_t.DECL_SOUND, msg.ReadLong())
                if (index >= 0 && index < DeclManager.declManager.GetNumDecls(declType_t.DECL_SOUND)) {
                    shader = DeclManager.declManager.SoundByIndex(index, false)
                    channel =  /*(s_channelType)*/msg.ReadByte().toInt()
                    StartSoundShader(shader, channel, 0, false)
                }
                true
            }

            EVENT_STOPSOUNDSHADER -> {
                assert(Game_local.gameLocal.isNewFrame)
                channel =  /*(s_channelType)*/msg.ReadByte().toInt()
                StopSound(channel, false)
                true
            }

            else -> {
                false
            }
        }
        //            return false;
    }

    fun WriteBindToSnapshot(msg: idBitMsgDelta) {
        var bindInfo: Int
        if (bindMaster != null) {
            bindInfo = bindMaster!!.entityNumber
            bindInfo = bindInfo or ((if (fl.bindOrientated) 1 else 0) shl Game_local.GENTITYNUM_BITS)
            if (bindJoint != Model.INVALID_JOINT) {
                bindInfo = bindInfo or (1 shl Game_local.GENTITYNUM_BITS + 1)
                bindInfo = bindInfo or (bindJoint shl 3 + Game_local.GENTITYNUM_BITS)
            } else if (bindBody != -1) {
                bindInfo = bindInfo or (2 shl Game_local.GENTITYNUM_BITS + 1)
                bindInfo = bindInfo or (bindBody shl 3 + Game_local.GENTITYNUM_BITS)
            }
        } else {
            bindInfo = Game_local.ENTITYNUM_NONE
        }
        msg.WriteBits(bindInfo, Game_local.GENTITYNUM_BITS + 3 + 9)
    }

    fun ReadBindFromSnapshot(msg: idBitMsgDelta) {
        val bindInfo: Int
        val bindEntityNum: Int
        val bindPos: Int
        val bindOrientated: Boolean
        val master: idEntity?
        bindInfo = msg.ReadBits(Game_local.GENTITYNUM_BITS + 3 + 9)
        bindEntityNum = bindInfo and (1 shl Game_local.GENTITYNUM_BITS) - 1
        if (bindEntityNum != Game_local.ENTITYNUM_NONE) {
            master = Game_local.gameLocal.entities[bindEntityNum]
            bindOrientated = bindInfo shr Game_local.GENTITYNUM_BITS and 1 == 1
            bindPos = bindInfo shr Game_local.GENTITYNUM_BITS + 3
            when (bindInfo shr Game_local.GENTITYNUM_BITS + 1 and 3) {
                1 -> {
                    BindToJoint(master,  /*(jointHandle_t)*/bindPos, bindOrientated)
                }

                2 -> {
                    BindToBody(master, bindPos, bindOrientated)
                }

                else -> {
                    Bind(master, bindOrientated)
                }
            }
        } else if (bindMaster != null) {
            Unbind()
        }
    }

    fun WriteColorToSnapshot(msg: idBitMsgDelta) {
        val color = idVec4(
            renderEntity!!.shaderParms[RenderWorld.SHADERPARM_RED],
            renderEntity!!.shaderParms[RenderWorld.SHADERPARM_GREEN],
            renderEntity!!.shaderParms[RenderWorld.SHADERPARM_BLUE],
            renderEntity!!.shaderParms[RenderWorld.SHADERPARM_ALPHA]
        )
        msg.WriteLong(PackColor(color).toInt())
    }

    fun ReadColorFromSnapshot(msg: idBitMsgDelta) {
        val color = idVec4()
        UnpackColor(msg.ReadLong().toLong(), color)
        renderEntity!!.shaderParms[RenderWorld.SHADERPARM_RED] = color[0]
        renderEntity!!.shaderParms[RenderWorld.SHADERPARM_GREEN] = color[1]
        renderEntity!!.shaderParms[RenderWorld.SHADERPARM_BLUE] = color[2]
        renderEntity!!.shaderParms[RenderWorld.SHADERPARM_ALPHA] = color[3]
    }

    fun WriteGUIToSnapshot(msg: idBitMsgDelta) {
        // no need to loop over MAX_RENDERENTITY_GUI at this time
        if (renderEntity!!.gui.isNotEmpty()) {
            msg.WriteByte(renderEntity!!.gui[0]!!.State().GetInt("networkState"))
        } else {
            msg.WriteByte(0)
        }
    }

    fun ReadGUIFromSnapshot(msg: idBitMsgDelta) {
        val state: Int
        val gui: idUserInterface?
        state = msg.ReadByte()
        gui = renderEntity!!.gui[0]
        if (gui != null && state != mpGUIState) {
            mpGUIState = state
            gui.SetStateInt("networkState", state)
            gui.HandleNamedEvent("networkState")
        }
    }

    /*
         ================
         idEntity::ServerSendEvent

         Saved events are also sent to any client that connects late so all clients
         always receive the events nomatter what time they join the game.
         ================
         */
    fun ServerSendEvent(eventId: Int, msg: idBitMsg?, saveEvent: Boolean, excludeClient: Int) {
        val outMsg = idBitMsg()
        val msgBuf = ByteBuffer.allocate(Game_local.MAX_GAME_MESSAGE_SIZE)
        if (!Game_local.gameLocal.isServer) {
            return
        }

        // prevent dupe events caused by frame re-runs
        if (!Game_local.gameLocal.isNewFrame) {
            return
        }
        outMsg.Init(msgBuf, Game_local.MAX_GAME_MESSAGE_SIZE)
        outMsg.BeginWriting()
        outMsg.WriteByte(Game_local.GAME_RELIABLE_MESSAGE_EVENT.toByte())
        outMsg.WriteBits(Game_local.gameLocal.GetSpawnId(this), 32)
        outMsg.WriteByte(eventId.toByte())
        outMsg.WriteLong(Game_local.gameLocal.time)
        if (msg != null) {
            outMsg.WriteBits(msg.GetSize(), idMath.BitsForInteger(Game_local.MAX_EVENT_PARAM_SIZE))
            outMsg.WriteData(msg.GetData()!!, msg.GetSize())
        } else {
            outMsg.WriteBits(0, idMath.BitsForInteger(Game_local.MAX_EVENT_PARAM_SIZE))
        }
        if (excludeClient != -1) {
            NetworkSystem.networkSystem.ServerSendReliableMessageExcluding(excludeClient, outMsg)
        } else {
            NetworkSystem.networkSystem.ServerSendReliableMessage(-1, outMsg)
        }
        if (saveEvent) {
            Game_local.gameLocal.SaveEntityNetworkEvent(this, eventId, msg)
        }
    }

    fun ClientSendEvent(eventId: Int, msg: idBitMsg?) {
        val outMsg = idBitMsg()
        val msgBuf = ByteBuffer.allocate(Game_local.MAX_GAME_MESSAGE_SIZE)
        if (!Game_local.gameLocal.isClient) {
            return
        }

        // prevent dupe events caused by frame re-runs
        if (!Game_local.gameLocal.isNewFrame) {
            return
        }
        outMsg.Init(msgBuf, Game_local.MAX_GAME_MESSAGE_SIZE)
        outMsg.BeginWriting()
        outMsg.WriteByte(Game_local.GAME_RELIABLE_MESSAGE_EVENT.toByte())
        outMsg.WriteBits(Game_local.gameLocal.GetSpawnId(this), 32)
        outMsg.WriteByte(eventId.toByte())
        outMsg.WriteLong(Game_local.gameLocal.time)
        if (msg != null) {
            outMsg.WriteBits(msg.GetSize(), idMath.BitsForInteger(Game_local.MAX_EVENT_PARAM_SIZE))
            outMsg.WriteData(msg.GetData()!!, msg.GetSize())
        } else {
            outMsg.WriteBits(0, idMath.BitsForInteger(Game_local.MAX_EVENT_PARAM_SIZE))
        }
        NetworkSystem.networkSystem.ClientSendReliableMessage(outMsg)
    }

    private fun FixupLocalizedStrings() {
        for (i in 0 until spawnArgs.GetNumKeyVals()) {
            val kv = spawnArgs.GetKeyVal(i)!!
            if (idStr.Cmpn(
                    kv.GetValue().toString(),
                    Common.STRTABLE_ID,
                    Common.STRTABLE_ID_LENGTH
                ) == 0
            ) {
                spawnArgs.Set(kv.GetKey(), idLib.common.GetLanguageDict().GetString(kv.GetValue()))
            }
        }
    }

    /*
         ================
         idEntity::DoDormantTests

         Monsters and other expensive entities that are completely closed
         off from the player can skip all of their work
         ================
         */
    private fun DoDormantTests(): Boolean {                // dormant == on the active list, but out of PVS
        if (fl.neverDormant) {
            return false
        }

        // if the monster area is not topologically connected to a player
        return if (!Game_local.gameLocal.InPlayerConnectedArea(this)) {
            if (dormantStart == 0) {
                dormantStart = Game_local.gameLocal.time
            }
            // just got closed off, don't go dormant yet
            Game_local.gameLocal.time - dormantStart >= DELAY_DORMANT_TIME
        } else {
            // the monster area is topologically connected to a player, but if
            // the monster hasn't been woken up before, do the more precise PVS check
            if (!fl.hasAwakened) {
                if (!Game_local.gameLocal.InPlayerPVS(this)) {
                    return true // stay dormant
                }
            }

            // wake up
            dormantStart = 0
            fl.hasAwakened = true // only go dormant when area closed off now, not just out of PVS
            false
        }

//            return false;
    }

    private fun InitDefaultPhysics(origin: idVec3, axis: idMat3) {
        val temp = arrayOfNulls<String>(1)
        var clipModel: idClipModel? = null

        // check if a clipmodel key/value pair is set
        if (spawnArgs.GetString("clipmodel", "", temp)) {
            if (idClipModel.CheckModel(temp[0]!!) != 0) {
                clipModel = idClipModel(temp[0]!!)
            }
        }
        if (!spawnArgs.GetBool("noclipmodel", "0")) {

            // check if mins/maxs or size key/value pairs are set
            if (clipModel == null) {
                val size = idVec3()
                val bounds = idBounds()
                var setClipModel = false
                if (spawnArgs.GetVector("mins", null, bounds[0])
                    && spawnArgs.GetVector("maxs", null, bounds[1])
                ) {
                    setClipModel = true
                    if (bounds[0].get(0) > bounds[1].get(0) || bounds[0].get(1) > bounds[1]
                            .get(1) || bounds[0].get(2) > bounds[1].get(2)
                    ) {
                        idGameLocal.Error(
                            "Invalid bounds '%s'-'%s' on entity '%s'",
                            bounds[0].ToString(),
                            bounds[1].ToString(),
                            name
                        )
                    }
                } else if (spawnArgs.GetVector("size", null, size)) {
                    if (size.x < 0.0f || size.y < 0.0f || size.z < 0.0f) {
                        idGameLocal.Error("Invalid size '%s' on entity '%s'", size.ToString(), name)
                    }
                    bounds[0].set(size.x * -0.5f, size.y * -0.5f, 0.0f)
                    bounds[1].set(size.x * 0.5f, size.y * 0.5f, size.z)
                    setClipModel = true
                }
                if (setClipModel) {
                    val numSides = CInt()
                    val trm = idTraceModel()
                    if (spawnArgs.GetInt("cylinder", "0", numSides) && numSides._val > 0) {
                        trm.SetupCylinder(bounds, max(numSides._val, 3))
                    } else if (spawnArgs.GetInt("cone", "0", numSides) && numSides._val > 0) {
                        trm.SetupCone(bounds, max(numSides._val, 3))
                    } else {
                        trm.SetupBox(bounds)
                    }
                    clipModel = idClipModel(trm)
                }
            }

            // check if the visual model can be used as collision model
            if (clipModel == null) {
                temp[0] = spawnArgs.GetString("model")
                if (temp.isNotEmpty() && !temp[0].isNullOrEmpty()) {
                    if (idClipModel.CheckModel(temp[0]!!) != 0) {
                        clipModel = idClipModel(temp[0]!!)
                    }
                }
            }
        }
        defaultPhysicsObj.SetSelf(this)
        defaultPhysicsObj.SetClipModel(clipModel, 1.0f)
        defaultPhysicsObj.SetOrigin(origin)
        defaultPhysicsObj.SetAxis(axis)
        physics = defaultPhysicsObj
    }

    // update visual position from the physics
    private fun UpdateFromPhysics(moveBack: Boolean) {
        if (this is idActor) {
            val actor = this

            // set master delta angles for actors
            if (GetBindMaster() != null) {
                val delta = actor.GetDeltaViewAngles()
                if (moveBack) {
                    delta.yaw -= (physics as idPhysics_Actor).GetMasterDeltaYaw()
                } else {
                    delta.yaw += (physics as idPhysics_Actor).GetMasterDeltaYaw()
                }
                actor.SetDeltaViewAngles(delta)
            }
        }
        UpdateVisuals()
    }

    // entity binding
    private fun InitBind(master: idEntity?): Boolean {        // initialize an entity binding
        if (master == this) { //TODO:equals
            idGameLocal.Error("Tried to bind an object to itself.")
            return false
        }
        if (this == Game_local.gameLocal.world) {
            idGameLocal.Error("Tried to bind world to another entity")
            return false
        }

        // unbind myself from my master
        Unbind()

        // add any bind constraints to an articulated figure
        if (master != null && this is idAFEntity_Base) {
            this.AddBindConstraints()
        }

        if (master == null || master == Game_local.gameLocal.world) {
            // this can happen in scripts, so safely exit out.
            return false
        }

        return true
    }

    private fun FinishBind() {                // finish an entity binding

        // set the master on the physics object
        physics.SetMaster(bindMaster, fl.bindOrientated)

        // We are now separated from our previous team and are either
        // an individual, or have a team of our own.  Now we can join
        // the new bindMaster's team.  Bindmaster must be set before
        // joining the team, or we will be placed in the wrong position
        // on the team.
        JoinTeam(bindMaster)

        // if our bindMaster is enabled during a cinematic, we must be, too
        cinematic = bindMaster!!.cinematic

        // make sure the team master is active so that physics get run
        teamMaster!!.BecomeActive(TH_PHYSICS)
    }

    private fun RemoveBinds() {                // deletes any entities bound to this object
        var ent: idEntity?
        var next: idEntity?
        ent = teamChain
        while (ent != null) {
            next = ent.teamChain
            if (ent.bindMaster == this) {
                ent.Unbind()
                ent.PostEventMS(EV_Remove, 0)
                next = teamChain
            }
            ent = next
        }
    }

    private fun QuitTeam() {                    // leave the current team
        var ent: idEntity?
        if (null == teamMaster) {
            return
        }

        // check if I'm the teamMaster
        if (teamMaster == this) {
            // do we have more than one teammate?
            if (null == teamChain!!.teamChain) {
                // no, break up the team
                teamChain!!.teamMaster = null
            } else {
                // yes, so make the first teammate the teamMaster
                ent = teamChain
                while (ent != null) {
                    ent.teamMaster = teamChain
                    ent = ent.teamChain
                }
            }
        } else {
            assert(teamMaster != null)
            assert(teamMaster!!.teamChain != null)

            // find the previous member of the teamChain
            ent = teamMaster
            while (ent!!.teamChain !== this) {
                assert(
                    ent.teamChain != null // this should never happen
                )
                ent = ent.teamChain
            }

            // remove this from the teamChain
            ent.teamChain = teamChain

            // if no one is left on the team, break it up
            if (null == teamMaster!!.teamChain) {
                teamMaster!!.teamMaster = null
            }
        }
        teamMaster = null
        teamChain = null
    }

    private fun UpdatePVSAreas() {
        var localNumPVSAreas: Int
        val localPVSAreas = IntArray(32)
        val modelAbsBounds = idBounds()
        var i: Int
        modelAbsBounds.FromTransformedBounds(renderEntity!!.bounds, renderEntity!!.origin, renderEntity!!.axis)
        localNumPVSAreas = Game_local.gameLocal.pvs.GetPVSAreas(modelAbsBounds, localPVSAreas, localPVSAreas.size)

        // FIXME: some particle systems may have huge bounds and end up in many PVS areas
        // the first MAX_PVS_AREAS may not be visible to a network client and as a result the particle system may not show up when it should
        if (localNumPVSAreas > MAX_PVS_AREAS) {
            localNumPVSAreas = Game_local.gameLocal.pvs.GetPVSAreas(
                idBounds(modelAbsBounds.GetCenter()).Expand(64.0f),
                localPVSAreas,
                localPVSAreas.size
            )
        }
        numPVSAreas = 0
        while (numPVSAreas < MAX_PVS_AREAS && numPVSAreas < localNumPVSAreas) {
            PVSAreas[numPVSAreas] = localPVSAreas[numPVSAreas]
            numPVSAreas++
        }
        i = numPVSAreas
        while (i < MAX_PVS_AREAS) {
            PVSAreas[i] = 0
            i++
        }
    }

    /* **********************************************************************

         Events

         ***********************************************************************/
    // events
    private fun Event_GetName() {
        idThread.ReturnString(name.toString())
    }

    private fun Event_FindTargets() {
        FindTargets()
    }

    private fun Event_NumTargets() {
        idThread.ReturnFloat(targets.Num().toFloat())
    }

    private fun Event_Unbind() {
        Unbind()
    }

    private fun Event_RemoveBinds() {
        RemoveBinds()
    }

    private fun Event_SpawnBind() {
        val parent: idEntity?
        val bind = arrayOfNulls<String>(1)
        val joint = arrayOfNulls<String>(1)
        val bindanim = arrayOfNulls<String>(1)
        val   /*jointHandle_t*/bindJoint: Int
        val bindOrientated: Boolean
        val id = CInt(0)
        val anim: idAnim?
        val animNum: Int
        val parentAnimator: idAnimator?
        if (spawnArgs.GetString("bind", "", bind)) {
            if (idStr.Icmp(bind[0]!!, "worldspawn") == 0) {
                //FIXME: Completely unneccessary since the worldspawn is called "world"
                parent = Game_local.gameLocal.world
            } else {
                parent = Game_local.gameLocal.FindEntity(bind[0]!!)
            }
            bindOrientated = spawnArgs.GetBool("bindOrientated", "1")
            if (parent != null) {
                // bind to a joint of the skeletal model of the parent
                if (spawnArgs.GetString(
                        "bindToJoint",
                        "",
                        joint
                    ) && joint.isNotEmpty()
                ) { //TODO:check if java actually compiles them in the right order.
                    parentAnimator = parent.GetAnimator()
                    if (null == parentAnimator) {
                        idGameLocal.Error(
                            "Cannot bind to joint '%s' on '%s'.  Entity does not support skeletal models.",
                            joint[0],
                            name
                        )
                        return
                    }
                    bindJoint = parentAnimator.GetJointHandle(joint[0]!!)
                    if (bindJoint == Model.INVALID_JOINT) {
                        idGameLocal.Error("Joint '%s' not found for bind on '%s'", joint[0], name)
                    }

                    // bind it relative to a specific anim
                    if ((parent.spawnArgs.GetString("bindanim", "", bindanim) || parent.spawnArgs.GetString(
                            "anim",
                            "",
                            bindanim
                        )) && bindanim.isNotEmpty()
                    ) {
                        animNum = parentAnimator.GetAnim(bindanim[0]!!)
                        if (0 == animNum) {
                            idGameLocal.Error("Anim '%s' not found for bind on '%s'", bindanim[0], name)
                        }
                        anim = parentAnimator.GetAnim(animNum)
                        if (null == anim) {
                            idGameLocal.Error("Anim '%s' not found for bind on '%s'", bindanim[0], name)
                        }

                        // make sure parent's render origin has been set
                        parent.UpdateModelTransform()

                        //FIXME: need a BindToJoint that accepts a joint position
                        parentAnimator.CreateFrame(Game_local.gameLocal.time, true)
                        val frame = parent.renderEntity!!.joints
                        GameEdit.gameEdit.ANIM_CreateAnimFrame(
                            parentAnimator.ModelHandle(),
                            anim!!.MD5Anim(0),
                            parent.renderEntity!!.numJoints,
                            frame,
                            0,
                            parentAnimator.ModelDef()!!.GetVisualOffset(),
                            parentAnimator.RemoveOrigin()
                        )
                        BindToJoint(parent, joint[0]!!, bindOrientated)
                        parentAnimator.ForceUpdate()
                    } else {
                        BindToJoint(parent, joint[0]!!, bindOrientated)
                    }
                } // bind to a body of the physics object of the parent
                else if (spawnArgs.GetInt("bindToBody", "0", id)) {
                    BindToBody(parent, id._val, bindOrientated)
                } // bind to the parent
                else {
                    Bind(parent, bindOrientated)
                }
            }
        }
    }

    private fun Event_GetColor() {
        val out = idVec3()
        GetColor(out)
        idThread.ReturnVector(out)
    }

    private fun Event_IsHidden() {
        idThread.ReturnInt(fl.hidden)
    }

    private fun Event_Hide() {
        Hide()
    }

    private fun Event_Show() {
        Show()
    }

    private fun Event_GetWorldOrigin() {
        idThread.ReturnVector(GetPhysics().GetOrigin())
    }

    private fun Event_GetOrigin() {
        idThread.ReturnVector(GetLocalCoordinates(GetPhysics().GetOrigin()))
    }

    private fun Event_GetAngles() {
        val ang = GetPhysics().GetAxis().ToAngles()
        idThread.ReturnVector(idVec3(ang[0], ang[1], ang[2]))
    }

    private fun Event_GetLinearVelocity() {
        idThread.ReturnVector(GetPhysics().GetLinearVelocity())
    }

    private fun Event_GetAngularVelocity() {
        idThread.ReturnVector(GetPhysics().GetAngularVelocity())
    }

    private fun Event_GetSize() {
        val bounds: idBounds
        bounds = GetPhysics().GetBounds()
        idThread.ReturnVector(bounds[1].minus(bounds[0]))
    }

    private fun Event_GetMins() {
        idThread.ReturnVector(GetPhysics().GetBounds()[0])
    }

    private fun Event_GetMaxs() {
        idThread.ReturnVector(GetPhysics().GetBounds()[1])
    }

    private fun Event_RestorePosition() {
        val org = idVec3()
        val angles = idAngles()
        val axis = idMat3()
        var part: idEntity?
        spawnArgs.GetVector("origin", "0 0 0", org)

        // get the rotation matrix in either full form, or single angle form
        if (spawnArgs.GetMatrix("rotation", "1 0 0 0 1 0 0 0 1", axis)) {
            angles.set(axis.ToAngles())
        } else {
            angles[0] = 0.0f
            angles[1] = spawnArgs.GetFloat("angle")
            angles[2] = 0.0f
        }
        // DG: save old origin for debug warning
        val oldOrg = idVec3(GetPhysics().GetOrigin())
        Teleport(org, angles, null)
        part = teamChain
        while (part != null) {
            if (part.bindMaster !== this) {
                part = part.teamChain
                continue
            }
            if (part.GetPhysics() is idPhysics_Parametric) {
                if ((part.GetPhysics() as idPhysics_Parametric).IsPusher()) {
                    Game_local.gameLocal.Warning(
                        "teleported '%s' which has the pushing mover '%s' bound to it\n",
                        GetName(),
                        part.GetName()
                    )
                    Game_local.gameLocal.Warning(
                        "  from (%.2f %.2f %.2f) to (%.2f %.2f %.2f)\n",
                        oldOrg.x, oldOrg.y, oldOrg.z, org.x, org.y, org.z
                    )
                }
            } else if (part.GetPhysics() is idPhysics_AF) {
                Game_local.gameLocal.Warning(
                    "teleported '%s' which has the articulated figure '%s' bound to it\n",
                    GetName(),
                    part.GetName()
                )
                Game_local.gameLocal.Warning(
                    "  from (%.2f %.2f %.2f) to (%.2f %.2f %.2f)\n",
                    oldOrg.x, oldOrg.y, oldOrg.z, org.x, org.y, org.z
                )
            }
            part = part.teamChain
        }
    }

    private fun Event_UpdateCameraTarget() {
        val target: String?
        var kv: idKeyValue?
        val dir = idVec3()
        target = spawnArgs.GetString("cameraTarget")
        cameraTarget = Game_local.gameLocal.FindEntity(target)
        if (cameraTarget != null) {
            kv = cameraTarget!!.spawnArgs.MatchPrefix("target", null)
            while (kv != null) {
                val ent: idEntity? = Game_local.gameLocal.FindEntity(kv.GetValue())
                if (ent != null && idStr.Icmp(ent.GetEntityDefName(), "target_null") == 0) {
                    dir.set(ent.GetPhysics().GetOrigin().minus(cameraTarget!!.GetPhysics().GetOrigin()))
                    dir.Normalize()
                    cameraTarget!!.SetAxis(dir.ToMat3())
                    SetAxis(dir.ToMat3())
                    break
                }
                kv = cameraTarget!!.spawnArgs.MatchPrefix("target", kv)
            }
        }
        UpdateVisuals()
    }

    private fun Event_WaitFrame() {
        val thread: idThread?
        thread = idThread.CurrentThread()
        if (thread != null) {
            thread.WaitFrame()
        }
    }

    private fun Event_Wait(time: idEventArg<Float>) {
        val thread: idThread? = idThread.CurrentThread()
        if (null == thread) {
            idGameLocal.Error("Event 'wait' called from outside thread")
            return
        }
        thread.WaitSec(time.value)
    }

    private fun Event_HasFunction(name: idEventArg<String>) {
        val func: function_t?
        func = scriptObject.GetFunction(name.value)
        idThread.ReturnInt(func != null)
    }

    private fun Event_CallFunction(_funcName: idEventArg<String>) {
        val funcName = _funcName.value
        val func: function_t?
        val thread: idThread?
        thread = idThread.CurrentThread()
        if (null == thread) {
            idGameLocal.Error("Event 'callFunction' called from outside thread")
            return
        }
        func = scriptObject.GetFunction(funcName)
        if (null == func) {
            idGameLocal.Error("Unknown function '%s' in '%s'", funcName, scriptObject.GetTypeName())
            return
        }
        if (func.type!!.NumParameters() != 1) {
            idGameLocal.Error(
                "Function '%s' has the wrong number of parameters for 'callFunction'",
                funcName
            )
        }
        if (!scriptObject.GetTypeDef().Inherits(func.type!!.GetParmType(0))) {
            idGameLocal.Error("Function '%s' is the wrong type for 'callFunction'", funcName)
        }

        // function args will be invalid after this call
        thread.CallFunction(this, func, false)
    }

    private fun Event_SetNeverDormant(enable: idEventArg<Int>) {
        fl.neverDormant = enable.value != 0
        dormantStart = 0
    }

    class entityFlags_s : SERiAL {
        var bindOrientated // if true both the master orientation is used for binding
                = false
        var forcePhysicsUpdate // if true always update from the physics whether the object moved or not
                = false
        var hasAwakened // before a monster has been awakened the first time, use full PVS for dormant instead of area-connected
                = false
        var hidden // if true this entity is not visible
                = false
        var isDormant // if true the entity is dormant
                = false
        var networkSync // if true the entity is synchronized over the network
                = false
        var grabbed // D3XP: if true object is currently being grabbed
                = false
        var neverDormant // if true the entity never goes dormant
                = false
        var noknockback // if true no knockback from hits
                = false
        var notarget // if true never attack or target this entity
                = false
        var selected // if true the entity is selected for editing
                = false
        var solidForTeam // if true this entity is considered solid when a physics team mate pushes entities
                = false
        var takedamage // if true this entity can be damaged
                = false

        override fun AllocBuffer(): ByteBuffer {
            return ByteBuffer.allocate(BYTES)
        }

        override fun Read(buffer: ByteBuffer) {
            buffer.order(java.nio.ByteOrder.LITTLE_ENDIAN)
            val bits = buffer.short.toInt()
            // C++ bitfield order from Entity.h lines 146-158
            notarget = (bits and (1 shl 0)) != 0
            noknockback = (bits and (1 shl 1)) != 0
            takedamage = (bits and (1 shl 2)) != 0
            hidden = (bits and (1 shl 3)) != 0
            bindOrientated = (bits and (1 shl 4)) != 0
            solidForTeam = (bits and (1 shl 5)) != 0
            forcePhysicsUpdate = (bits and (1 shl 6)) != 0
            selected = (bits and (1 shl 7)) != 0
            neverDormant = (bits and (1 shl 8)) != 0
            isDormant = (bits and (1 shl 9)) != 0
            hasAwakened = (bits and (1 shl 10)) != 0
            networkSync = (bits and (1 shl 11)) != 0
            grabbed = (bits and (1 shl 12)) != 0
        }

        override fun Write(): ByteBuffer {
            val buffer = AllocBuffer()
            buffer.order(java.nio.ByteOrder.LITTLE_ENDIAN)
            var bits = 0
            // C++ bitfield order from Entity.h lines 146-158
            if (notarget) bits = bits or (1 shl 0)
            if (noknockback) bits = bits or (1 shl 1)
            if (takedamage) bits = bits or (1 shl 2)
            if (hidden) bits = bits or (1 shl 3)
            if (bindOrientated) bits = bits or (1 shl 4)
            if (solidForTeam) bits = bits or (1 shl 5)
            if (forcePhysicsUpdate) bits = bits or (1 shl 6)
            if (selected) bits = bits or (1 shl 7)
            if (neverDormant) bits = bits or (1 shl 8)
            if (isDormant) bits = bits or (1 shl 9)
            if (hasAwakened) bits = bits or (1 shl 10)
            if (networkSync) bits = bits or (1 shl 11)
            if (grabbed) bits = bits or (1 shl 12)
            buffer.putShort(bits.toShort())
            buffer.flip()
            return buffer
        }

        companion object {
            @Transient
            val BYTES = java.lang.Short.BYTES // 2
        }
    }

    /*
         ================
         idEntity::ModelCallback

         NOTE: may not change the game state whatsoever!
         ================
         */
    class ModelCallback private constructor() : deferredEntityCallback_t() {
        override fun run(e: renderEntity_s?, v: renderView_s?): Boolean {
            val ent: idEntity?
            ent = Game_local.gameLocal.entities[e!!.entityNum]
            if (null == ent) {
                Common.common.Warning("idEntity::ModelCallback: callback with NULL game entity")
                return false
            }
            return ent.UpdateRenderEntity(e, v)
        }

        override fun AllocBuffer(): ByteBuffer {
            return ByteBuffer.allocate(0) // stateless singleton - no data to serialize
        }

        override fun Read(buffer: ByteBuffer) {
            // stateless singleton - nothing to read
        }

        override fun Write(): ByteBuffer {
            return AllocBuffer() // stateless singleton - nothing to write
        }

        companion object {
            private val instance: deferredEntityCallback_t = ModelCallback()
            fun getInstance(): deferredEntityCallback_t {
                return instance
            }
        }
    }

    //        public static final idTypeInfo Type;
    //
    //        public static idClass CreateInstance();
    //
    //        public abstract idTypeInfo GetType();
    //        public static idEventFunc<idEntity>[] eventCallbacks;
    //
    init {
        targets = idList(idEntityPtr<idEntity>(null).javaClass)
        entityNumber = Game_local.ENTITYNUM_NONE
        entityDefNumber = -1
        spawnNode = idLinkList()
        spawnNode.SetOwner(this)
        activeNode = idLinkList()
        activeNode.SetOwner(this)
        snapshotNode = idLinkList()
        snapshotNode.SetOwner(this)
        snapshotSequence = -1
        snapshotBits = 0
        spawnArgs = idDict()
        scriptObject = idScriptObject()
        thinkFlags = 0
        dormantStart = 0
        cinematic = false
        renderView = null
        cameraTarget = null
        health = 0
        bindMaster = null
        bindJoint = Model.INVALID_JOINT
        bindBody = -1
        teamMaster = null
        teamChain = null
        signals = null
        Arrays.fill(PVSAreas, 0)
        numPVSAreas = -1
        fl = entityFlags_s() //	memset( &fl, 0, sizeof( fl ) );
        fl.neverDormant = true // most entities never go dormant
        renderEntity = renderEntity_s() //memset( &renderEntity, 0, sizeof( renderEntity ) );
        modelDefHandle = -1
        refSound = refSound_t() //memset( &refSound, 0, sizeof( refSound ) );
        mpGUIState = -1
    }
}

/*
     ===============================================================================

     Animated entity base class.

     ===============================================================================
     */
class damageEffect_s {
    val localNormal: idVec3 = idVec3()
    val localOrigin: idVec3 = idVec3()
    var   /*jointHandle_t*/jointNum = 0
    var next: damageEffect_s? = null
    var time = 0
    var type: idDeclParticle? = null
}

/*
     ===============================================================================

     idAnimatedEntity

     ===============================================================================
     */
open class idAnimatedEntity : idEntity() {
    companion object {
        val Type = idTypeInfo("idAnimatedEntity", "idEntity") { idAnimatedEntity() }

        // enum {
        const val EVENT_ADD_DAMAGE_EFFECT = idEntity.EVENT_MAXEVENTS
        const val EVENT_MAXEVENTS = EVENT_ADD_DAMAGE_EFFECT + 1
        private val eventCallbacks: MutableMap<idEventDef, eventCallback_t<*>> = HashMap()

        //							// ~idAnimatedEntity();
        /*
         ================
         Event_GetJointHandle

         looks up the number of the specified joint.  returns INVALID_JOINT if the joint is not found.
         ================
         */
        private fun Event_GetJointHandle(e: idAnimatedEntity, jointname: idEventArg<String>) {
//            jointHandle_t joint = new jointHandle_t();
            val joint: Int
            joint = e.animator.GetJointHandle(jointname.value)
            idThread.ReturnInt(joint)
        }

        /*
         ================
         Event_ClearJoint

         removes any custom transforms on the specified joint
         ================
         */
        private fun Event_ClearJoint(e: idAnimatedEntity,    /*jointHandle_t*/jointnum: idEventArg<Int>) {
            e.animator.ClearJoint(jointnum.value)
        }

        /*
         ================
         Event_SetJointPos

         modifies the position of the joint based on the transform type
         ================
         */
        private fun Event_SetJointPos(
            e: idAnimatedEntity,    /*jointHandle_t*/
            jointnum: idEventArg<Int>,
            transform_type: idEventArg<jointModTransform_t>,
            pos: idEventArg<idVec3>
        ) {
            e.animator.SetJointPos(jointnum.value, transform_type.value, pos.value)
        }

        /*
         ================
         Event_SetJointAngle

         modifies the orientation of the joint based on the transform type
         ================
         */
        private fun Event_SetJointAngle(
            e: idAnimatedEntity,    /*jointHandle_t*/
            jointnum: idEventArg<Int>,    /*jointModTransform_t*/
            transform_type: idEventArg<Int>,
            angles: idEventArg<idVec3>
        ) {
            val mat: idMat3
            val ang = idAngles(angles.value[0], angles.value[1], angles.value[2])
            mat = ang.ToMat3()
            e.animator.SetJointAxis(jointnum.value, jointModTransform_t.entries.get(transform_type.value), mat)
        }

        /*
         ================
         Event_GetJointPos

         returns the position of the joint in worldspace
         ================
         */
        private fun Event_GetJointPos(e: idAnimatedEntity,    /*jointHandle_t*/jointnum: idEventArg<Int>) {
            val offset = idVec3()
            val axis = idMat3()
            val ts = if (isD3XP) SetTimeState(e.timeGroup) else null
            try {
            if (!e.GetJointWorldTransform(jointnum.value, Game_local.gameLocal.time, offset, axis)) {
                Game_local.gameLocal.Warning("Joint # %d out of range on entity '%s'", jointnum, e.name)
            }
            } finally {
                ts?.close()
            }
            idThread.ReturnVector(offset)
        }

        /*
         ================
         Event_GetJointAngle

         returns the orientation of the joint in worldspace
         ================
         */
        private fun Event_GetJointAngle(e: idAnimatedEntity,    /*jointHandle_t*/jointnum: idEventArg<Int>) {
            val offset = idVec3()
            val axis = idMat3()
            val ts = if (isD3XP) SetTimeState(e.timeGroup) else null
            try {
            if (!e.GetJointWorldTransform(jointnum.value, Game_local.gameLocal.time, offset, axis)) {
                Game_local.gameLocal.Warning("Joint # %d out of range on entity '%s'", jointnum, e.name)
            }
            } finally {
                ts?.close()
            }
            val ang = axis.ToAngles()
            val vec = idVec3(ang[0], ang[1], ang[2])
            idThread.ReturnVector(vec)
        }

        fun getEventCallBacks(): MutableMap<idEventDef, eventCallback_t<*>> {
            return eventCallbacks
        }

        init {
            eventCallbacks.putAll(idEntity.getEventCallBacks())
            eventCallbacks[EV_GetJointHandle] =
                eventCallback_t1<idAnimatedEntity> { e: idAnimatedEntity, jointname: idEventArg<*>? ->
                    Event_GetJointHandle(e, jointname as idEventArg<String>)
                }
            eventCallbacks[EV_ClearAllJoints] =
                eventCallback_t0<idAnimatedEntity> { obj: idAnimatedEntity -> obj.Event_ClearAllJoints() }
            eventCallbacks[EV_ClearJoint] =
                eventCallback_t1<idAnimatedEntity> { e: idAnimatedEntity, jointnum: idEventArg<*>? ->
                    Event_ClearJoint(e, jointnum as idEventArg<Int>)
                }
            eventCallbacks[EV_SetJointPos] =
                eventCallback_t3<idAnimatedEntity> { e: idAnimatedEntity, jointnum: idEventArg<*>?,
                                                     transform_type: idEventArg<*>?,
                                                     pos: idEventArg<*>? ->
                    Event_SetJointPos(
                        e,
                        jointnum as idEventArg<Int>,
                        transform_type as idEventArg<jointModTransform_t>,
                        pos as idEventArg<idVec3>
                    )
                }
            eventCallbacks[EV_SetJointAngle] =
                eventCallback_t3<idAnimatedEntity> { e: idAnimatedEntity, jointnum: idEventArg<*>?,
                                                     transform_type: idEventArg<*>?,
                                                     angles: idEventArg<*>? ->
                    Event_SetJointAngle(
                        e,
                        jointnum as idEventArg<Int>,
                        transform_type as idEventArg<Int>,
                        angles as idEventArg<idVec3>
                    )
                }
            eventCallbacks[EV_GetJointPos] =
                eventCallback_t1<idAnimatedEntity> { e: idAnimatedEntity, jointnum: idEventArg<*>? ->
                    Event_GetJointPos(e, jointnum as idEventArg<Int>)
                }
            eventCallbacks[EV_GetJointAngle] =
                eventCallback_t1<idAnimatedEntity> { e: idAnimatedEntity, jointnum: idEventArg<*>? ->
                    Event_GetJointAngle(e, jointnum as idEventArg<Int>)
                }
        }
    }

    protected var animator: idAnimator
    protected var damageEffects: damageEffect_s?

    /*
         ================
         Save

         archives object for save game file
         ================
         */
    override fun Save(savefile: idSaveGame) {
        super.Save(savefile)
        animator.Save(savefile)

        // Wounds are very temporary, ignored at this time
        //damageEffect_s			*damageEffects;
    }

    /*
         ================
         Restore

         unarchives object from save game file
         ================
         */
    override fun Restore(savefile: idRestoreGame) {
        super.Restore(savefile)
        animator.Restore(savefile)

        // check if the entity has an MD5 model
        if (animator.ModelHandle() != null) {
            // set the callback to update the joints
            renderEntity!!.callback = ModelCallback.getInstance()
            arrayOf<Array<idJointMat?>?>(null)
            renderEntity!!.numJoints = animator.GetJoints(renderEntity!!)
            animator.GetBounds(Game_local.gameLocal.time, renderEntity!!.bounds)
            if (modelDefHandle != -1) {
                gameRenderWorld!!.UpdateEntityDef(modelDefHandle, renderEntity!!)
            }
        }
    }

    override fun ClientPredictionThink() {
        RunPhysics()
        UpdateAnimation()
        Present()
    }

    override fun Think() {
        RunPhysics()
        UpdateAnimation()
        Present()
        UpdateDamageEffects()
    }

    fun UpdateAnimation() {
        val ts = if (isD3XP) SetTimeState(timeGroup) else null
        try {
        // don't do animations if they're not enabled
        if (0 == (thinkFlags and TH_ANIMATE)) {
            return
        }

        // is the model an MD5?
        if (animator.ModelHandle() == null) {
            // no, so nothing to do
            return
        }

        // call any frame commands that have happened in the past frame
        if (!fl.hidden) {
            animator.ServiceAnims(Game_local.gameLocal.previousTime, Game_local.gameLocal.time)
        }

        // if the model is animating then we have to update it
        if (!animator.FrameHasChanged(Game_local.gameLocal.time)) {
            // still fine the way it was
            return
        }

        // get the latest frame bounds
        animator.GetBounds(Game_local.gameLocal.time, renderEntity!!.bounds)
        if (renderEntity!!.bounds.IsCleared() && !fl.hidden) {
            Game_local.gameLocal.DPrintf("%d: inside out bounds\n", Game_local.gameLocal.time)
        }

        // update the renderEntity
        UpdateVisuals()

        // the animation is updated
        animator.ClearForceUpdate()
        } finally {
            ts?.close()
        }
    }

    override fun GetAnimator(): idAnimator {
        return animator
    }

    override fun SetModel(modelname: String) {
        FreeModelDef()
        renderEntity!!.hModel = animator.SetModel(modelname)
        if (renderEntity!!.hModel == null) {
            super.SetModel(modelname)
            return
        }
        if (null == renderEntity!!.customSkin) {
            renderEntity!!.customSkin = animator.ModelDef()!!.GetDefaultSkin()
        }

        // set the callback to update the joints
        renderEntity!!.callback = ModelCallback.getInstance()
        renderEntity!!.numJoints = animator.GetJoints(renderEntity!!)
        animator.GetBounds(Game_local.gameLocal.time, renderEntity!!.bounds)
        UpdateVisuals()
    }

    fun GetJointWorldTransform(   /*jointHandle_t*/jointHandle: Int,
                                  currentTime: Int,
                                  offset: idVec3,
                                  axis: idMat3
    ): Boolean {
        if (!animator.GetJointTransform(jointHandle, currentTime, offset, axis)) {
            return false
        }
        ConvertLocalToWorldTransform(offset, axis)
        return true
    }

    fun GetJointTransformForAnim(   /*jointHandle_t*/jointHandle: Int,
                                    animNum: Int,
                                    frameTime: Int,
                                    offset: idVec3,
                                    axis: idMat3
    ): Boolean {
        val anim: idAnim?
        val numJoints: Int
        val frame: Array<idJointMat>
        anim = animator.GetAnim(animNum)
        if (null == anim) {
            assert(false)
            return false
        }
        numJoints = animator.NumJoints()
        if (jointHandle < 0 || jointHandle >= numJoints) {
            assert(false)
            return false
        }
        frame = Array(numJoints) { idJointMat() }
        GameEdit.gameEdit.ANIM_CreateAnimFrame(
            animator.ModelHandle(),
            anim.MD5Anim(0),
            renderEntity!!.numJoints,
            frame as Array<idJointMat?>,
            frameTime,
            animator.ModelDef()!!.GetVisualOffset(),
            animator.RemoveOrigin()
        )
        offset.set(frame[jointHandle].ToVec3())
        axis.set(frame[jointHandle].ToMat3())
        return true
    }

    open fun GetDefaultSurfaceType(): Int {
        return TempDump.etoi(surfTypes_t.SURFTYPE_METAL)
    }

    override fun AddDamageEffect(collision: trace_s, velocity: idVec3, damageDefName: String) {
        val origin: idVec3
        val dir = idVec3()
        val localDir = idVec3()
        val localOrigin = idVec3()
        val localNormal = idVec3()
        val axis: idMat3

        if (!SysCvar.g_bloodEffects.GetBool() || renderEntity!!.joints == null) {
            return
        }

        val def = Game_local.gameLocal.FindEntityDef(damageDefName, false) ?: return

        val jointNum = neo.Game.Physics.Clip.CLIPMODEL_ID_TO_JOINT_HANDLE(collision.c.id)
        if (jointNum == Model.INVALID_JOINT) {
            return
        }

        dir.set(velocity)
        dir.Normalize()

        axis = renderEntity!!.joints!![jointNum]!!.ToMat3().times(renderEntity!!.axis)
        origin = renderEntity!!.origin.plus(
            renderEntity!!.joints!![jointNum]!!.ToVec3().times(renderEntity!!.axis)
        )

        localOrigin.set(collision.c.point.minus(origin).times(axis.Transpose()))
        localNormal.set(collision.c.normal.times(axis.Transpose()))
        localDir.set(dir.times(axis.Transpose()))

        AddLocalDamageEffect(jointNum, localOrigin, localNormal, localDir, def, collision.c.material)

        if (Game_local.gameLocal.isServer) {
            val msg = idBitMsg()
            val msgBuf = ByteBuffer.allocate(Game_local.MAX_EVENT_PARAM_SIZE)

            msg.Init(msgBuf, Game_local.MAX_EVENT_PARAM_SIZE)
            msg.BeginWriting()
            msg.WriteShort(jointNum.toShort())
            msg.WriteFloat(localOrigin[0])
            msg.WriteFloat(localOrigin[1])
            msg.WriteFloat(localOrigin[2])
            msg.WriteDir(localNormal, 24)
            msg.WriteDir(localDir, 24)
            msg.WriteLong(Game_local.gameLocal.ServerRemapDecl(-1, declType_t.DECL_ENTITYDEF, def.Index()))
            msg.WriteLong(
                Game_local.gameLocal.ServerRemapDecl(
                    -1,
                    declType_t.DECL_MATERIAL,
                    collision.c.material!!.Index()
                )
            )
            ServerSendEvent(EVENT_ADD_DAMAGE_EFFECT, msg, false, -1)
        }
    }

    fun AddLocalDamageEffect(   /*jointHandle_t*/jointNum: Int,
                                localOrigin: idVec3,
                                localNormal: idVec3,
                                localDir: idVec3,
                                def: idDeclEntityDef,
                                collisionMaterial: Material.idMaterial?
    ) {
        var sound: String?
        var splat: String?
        var decal: String?
        var bleed: String?
        var key: String?
        val de: damageEffect_s
        val origin = idVec3()
        val dir = idVec3()
        val axis: idMat3
        val ts = if (isD3XP) SetTimeState(timeGroup) else null
        try {
        axis = renderEntity!!.joints!![jointNum]!!.ToMat3().times(renderEntity!!.axis)
        origin.set(
            renderEntity!!.origin.plus(
                renderEntity!!.joints!![jointNum]!!.ToVec3().times(renderEntity!!.axis)
            )
        )
        origin.set(origin.plus(localOrigin.times(axis)))
        dir.set(localDir.times(axis))
        var type: Int = collisionMaterial!!.GetSurfaceType().ordinal
        if (type == surfTypes_t.SURFTYPE_NONE.ordinal) {
            type = GetDefaultSurfaceType()
        }
        val materialType = Game_local.gameLocal.sufaceTypeNames[type]

        // start impact sound based on material type
        key = va("snd_%s", materialType)
        sound = spawnArgs.GetString(key)
        if (sound == null || sound.isEmpty()) { // == '\0' ) {
            sound = def.dict.GetString(key)
        }
        if (sound != null && !sound.isEmpty()) { // != '\0' ) {
            StartSoundShader(
                DeclManager.declManager.FindSound(sound),
                gameSoundChannel_t.SND_CHANNEL_BODY.ordinal,
                0,
                false
            )
        }

        // blood splats are thrown onto nearby surfaces
        key = va("mtr_splat_%s", materialType)
        splat = spawnArgs.RandomPrefix(key, Game_local.gameLocal.random)
        if (splat == null || splat.isEmpty()) { // == '\0' ) {
            splat = def.dict.RandomPrefix(key, Game_local.gameLocal.random)
        }
        if (splat != null && !splat.isEmpty()) { // 1= '\0' ) {
            Game_local.gameLocal.BloodSplat(origin, dir, 64.0f, splat)
        }

        // can't see wounds on the player model in single player mode
        if (this !is idPlayer && !Game_local.gameLocal.isMultiplayer) {
            // place a wound overlay on the model
            key = va("mtr_wound_%s", materialType)
            decal = spawnArgs.RandomPrefix(key, Game_local.gameLocal.random)
            if (decal == null || decal.isEmpty()) { // == '\0' ) {
                decal = def.dict.RandomPrefix(key, Game_local.gameLocal.random)
            }
            if (decal != null && !decal.isEmpty()) { // == '\0' ) {
                ProjectOverlay(origin, dir, 20.0f, decal)
            }
        }

        // a blood spurting wound is added
        key = va("smoke_wound_%s", materialType)
        bleed = spawnArgs.GetString(key)
        if (bleed == null || bleed.isEmpty()) { // == '\0' ) {
            bleed = def.dict.GetString(key)
        }
        if (bleed != null && !bleed.isEmpty()) { // == '\0' ) {
            de = damageEffect_s()
            de.next = damageEffects
            damageEffects = de
            de.jointNum = jointNum
            de.localOrigin.set(localOrigin)
            de.localNormal.set(localNormal)
            de.type = DeclManager.declManager.FindType(declType_t.DECL_PARTICLE, bleed) as idDeclParticle
            de.time = Game_local.gameLocal.time
        }
        } finally {
            ts?.close()
        }
    }

    fun UpdateDamageEffects() {
        var de: damageEffect_s?
        var prev: damageEffect_s? = null

        // free any that have timed out
        de = damageEffects
        while (de != null) {
            if (de.time == 0) {    // FIXME:SMOKE
                if (prev == null) {
                    damageEffects = de.next
                } else {
                    prev.next = de.next
                }
                de = de.next
            } else {
                prev = de
                de = de.next
            }
        }
        if (!SysCvar.g_bloodEffects.GetBool()) {
            return
        }

        // emit a particle for each bleeding wound
        de = damageEffects
        while (de != null) {
            val origin = idVec3()
            val start = idVec3()
            val axis = idMat3()
            animator.GetJointTransform(de.jointNum, Game_local.gameLocal.time, origin, axis)
            axis.timesAssign(renderEntity!!.axis)
            origin.set(renderEntity!!.origin.plus(origin.times(renderEntity!!.axis)))
            start.set(origin.plus(de.localOrigin.times(axis)))
            if (!Game_local.gameLocal.smokeParticles!!.EmitSmoke(
                    de.type,
                    de.time,
                    Game_local.gameLocal.random.CRandomFloat(),
                    start,
                    axis
                )
            ) {
                de.time = 0
            }
            de = de.next
        }
    }

    override fun ClientReceiveEvent(event: Int, time: Int, msg: idBitMsg): Boolean {
        val damageDefIndex: Int
        val materialIndex: Int
        val   /*jointHandle_s*/jointNum: Int
        val localOrigin = idVec3()
        val localNormal = idVec3()
        val localDir = idVec3()
        return when (event) {
            EVENT_ADD_DAMAGE_EFFECT -> {
                jointNum =  /*(jointHandle_s)*/msg.ReadShort().toInt()
                localOrigin[0] = msg.ReadFloat()
                localOrigin[1] = msg.ReadFloat()
                localOrigin[2] = msg.ReadFloat()
                localNormal.set(msg.ReadDir(24))
                localDir.set(msg.ReadDir(24))
                damageDefIndex = Game_local.gameLocal.ClientRemapDecl(declType_t.DECL_ENTITYDEF, msg.ReadLong())
                materialIndex = Game_local.gameLocal.ClientRemapDecl(declType_t.DECL_MATERIAL, msg.ReadLong())
                val damageDef = DeclManager.declManager.DeclByIndex(
                    declType_t.DECL_ENTITYDEF,
                    damageDefIndex
                ) as idDeclEntityDef
                val collisionMaterial: Material.idMaterial =
                    DeclManager.declManager.DeclByIndex(
                        declType_t.DECL_MATERIAL,
                        materialIndex
                    ) as Material.idMaterial
                AddLocalDamageEffect(jointNum, localOrigin, localNormal, localDir, damageDef, collisionMaterial)
                true
            }

            else -> {
                super.ClientReceiveEvent(event, time, msg)
            }
        }
        //            return false;
    }

    /*
         ================
         Event_ClearAllJoints

         removes any custom transforms on all joints
         ================
         */
    private fun Event_ClearAllJoints() {
        animator.ClearAllJoints()
    }

    /**
     * inherited grandfather functions.
     */
    fun idEntity_Hide() {
        super.Hide()
    }

    fun idEntity_Show() {
        super.Show()
    }

    fun idEntity_GetImpactInfo(ent: idEntity?, id: Int, point: idVec3): impactInfo_s {
        return super.GetImpactInfo(ent, id, point)
    }

    fun idEntity_ApplyImpulse(ent: idEntity?, id: Int, point: idVec3, impulse: idVec3) {
        super.ApplyImpulse(ent, id, point, impulse)
    }

    fun idEntity_AddForce(ent: idEntity?, id: Int, point: idVec3, force: idVec3) {
        super.AddForce(ent, id, point, force)
    }

    fun idEntity_GetPhysicsToVisualTransform(origin: idVec3, axis: idMat3): Boolean {
        return super.GetPhysicsToVisualTransform(origin, axis)
    }

    fun idEntity_FreeModelDef() {
        super.FreeModelDef()
    }

    fun idEntity_Present() {
        super.Present()
    }

    fun idEntity_ProjectOverlay(origin: idVec3, dir: idVec3, size: Float, material: String) {
        super.ProjectOverlay(origin, dir, size, material)
    }

    fun idEntity_ServerReceiveEvent(event: Int, time: Int, msg: idBitMsg?): Boolean {
        return super.ServerReceiveEvent(event, time, msg)
    }

    override fun getEventCallBack(event: idEventDef): eventCallback_t<*>? {
        return eventCallbacks[event]
    }

    override fun CreateInstance(): idClass = idAnimatedEntity()

    override fun GetType(): idTypeInfo = Type

    //
    //
    //public	CLASS_PROTOTYPE( idAnimatedEntity );
    //        public static idTypeInfo Type;
    //        public static idClass CreateInstance();
    //
    //        public idTypeInfo GetType();
    //        public static idEventFunc<idAnimatedEntity>[] eventCallbacks;
    //
    init {
        animator = idAnimator()
        animator.SetEntity(this)
        damageEffects = null
    }
}

/**
 * D3XP: scoped time state switcher — saves current fast/slow timeline context,
 * switches to the specified timeGroup, and restores on close().
 *
 * In C++ this was RAII (destructor restores). In Kotlin, call with try/finally:
 *   val ts = SetTimeState(timeGroup)
 *   try { ... } finally { ts.close() }
 *
 * Or use Kotlin's built-in use block pattern (implements AutoCloseable).
 */
class SetTimeState(timeGroup: Int = -1) : AutoCloseable {
    private var activated = false
    private var previousFast = false

    init {
        if (timeGroup >= 0) push(timeGroup)
    }

    fun push(timeGroup: Int) {
        if (Game_local.gameLocal.isMultiplayer) return
        activated = true
        // remember which timeline we were on
        previousFast = Game_local.gameLocal.time != Game_local.gameLocal.slow.time
        // switch to the requested timeline
        Game_local.gameLocal.SelectTimeGroup(timeGroup)
    }

    override fun close() {
        if (activated && !Game_local.gameLocal.isMultiplayer) {
            Game_local.gameLocal.SelectTimeGroup(if (previousFast) Game_local.TIME_GROUP2 else Game_local.TIME_GROUP1)
        }
    }
}

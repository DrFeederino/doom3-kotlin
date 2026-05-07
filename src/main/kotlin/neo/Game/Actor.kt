/*
 * Copyright (C) 1999-2011 id Software LLC, a ZeniMax Media company.
 * Translated to Kotlin by Dr. Feederino with support of Claude Code
 *
 * This file is part of the Doom 3 Kotlin project.
 * Original source: neo/Game/Actor.cpp, neo/Game/Actor.h
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

import neo.Game.AI.AAS.idAAS
import neo.Game.AI.AASFile
import neo.Game.Animation.Anim
import neo.Game.Animation.Anim.animFlags_t
import neo.Game.Animation.Anim.jointModTransform_t
import neo.Game.Animation.idAnimBlend
import neo.Game.Animation.idAnimator
import neo.Game.GameSys.Class.*
import neo.Game.GameSys.EV_Remove
import neo.Game.GameSys.Event.idEventDef
import neo.Game.GameSys.SaveGame.idRestoreGame
import neo.Game.GameSys.SaveGame.idSaveGame
import neo.Game.GameSys.SysCvar
import neo.Game.Game_local.*
import neo.Game.Game_local.Companion.isD3XP
import neo.Game.IK.idIK_Walk
import neo.Game.Light.idLight
import neo.Game.Physics.Clip.idClipModel
import neo.Game.Projectile.idSoulCubeMissile
import neo.Game.Script.Script_Program.function_t
import neo.Game.Script.Script_Thread.idThread
import neo.Renderer.Material
import neo.Renderer.Material.surfTypes_t
import neo.Renderer.Model
import neo.Renderer.RenderWorld.renderView_s
import neo.cm.CM_CLIP_EPSILON
import neo.cm.trace_s
import neo.framework.DeclManager
import neo.idlib.BV.idBounds
import neo.idlib.Dict_h.idDict
import neo.idlib.Dict_h.idKeyValue
import neo.idlib.Text.Lexer.idLexer
import neo.idlib.Text.Str
import neo.idlib.Text.Str.idStr
import neo.idlib.Text.Token.idToken
import neo.idlib.Text.atof
import neo.idlib.containers.*
import neo.idlib.containers.LinkList.idLinkList
import neo.idlib.containers.List
import neo.idlib.math.*
import neo.idlib.math.Matrix.idMat3
import kotlin.collections.HashMap
import kotlin.collections.MutableMap
import kotlin.collections.set
import kotlin.math.ceil
import kotlin.math.cos

val AI_AnimDistance: idEventDef = idEventDef("animDistance", "ds", 'f')
val AI_AnimDone: idEventDef = idEventDef("animDone", "dd", 'd')
val AI_AnimLength: idEventDef = idEventDef("animLength", "ds", 'f')
val AI_AnimState: idEventDef = idEventDef("animState", "dsd")
val AI_CheckAnim: idEventDef = idEventDef("checkAnim", "ds")
val AI_ChooseAnim: idEventDef = idEventDef("chooseAnim", "ds", 's')
val AI_ClosestEnemyToPoint: idEventDef = idEventDef("closestEnemyToPoint", "v", 'e')
val AI_DisableEyeFocus: idEventDef = idEventDef("disableEyeFocus")
val AI_DisablePain: idEventDef = idEventDef("disablePain")
val AI_EnableAnim: idEventDef = idEventDef("enableAnim", "dd")
val AI_EnableEyeFocus: idEventDef = idEventDef("enableEyeFocus")
val AI_EnablePain: idEventDef = idEventDef("enablePain")
val AI_FinishAction: idEventDef = idEventDef("finishAction", "s")
val AI_GetAnimState: idEventDef = idEventDef("getAnimState", "d", 's')
val AI_GetBlendFrames: idEventDef = idEventDef("getBlendFrames", "d", 'd')
val AI_GetHead: idEventDef = idEventDef("getHead", null, 'e')
val AI_GetPainAnim: idEventDef = idEventDef("getPainAnim", null, 's')
val AI_GetState: idEventDef = idEventDef("getState", null, 's')
val AI_HasAnim: idEventDef = idEventDef("hasAnim", "ds", 'f')
val AI_HasEnemies: idEventDef = idEventDef("hasEnemies", null, 'd')
val AI_IdleAnim: idEventDef = idEventDef("idleAnim", "ds", 'd')
val AI_InAnimState: idEventDef = idEventDef("inAnimState", "ds", 'd')
val AI_NextEnemy: idEventDef = idEventDef("nextEnemy", "E", 'e')
val AI_OverrideAnim: idEventDef = idEventDef("overrideAnim", "d")
val AI_PlayAnim: idEventDef = idEventDef("playAnim", "ds", 'd')
val AI_PlayCycle: idEventDef = idEventDef("playCycle", "ds", 'd')
val AI_PreventPain: idEventDef = idEventDef("preventPain", "f")
val AI_SetAnimPrefix: idEventDef = idEventDef("setAnimPrefix", "s")
val AI_SetBlendFrames: idEventDef = idEventDef("setBlendFrames", "dd")
val AI_SetNextState: idEventDef = idEventDef("setNextState", "s")
val AI_SetState: idEventDef = idEventDef("setState", "s")
val AI_SetSyncedAnimWeight: idEventDef = idEventDef("setSyncedAnimWeight", "ddf")
val AI_StopAnim: idEventDef = idEventDef("stopAnim", "dd")
val EV_DisableLegIK: idEventDef = idEventDef("DisableLegIK", "d")
val EV_DisableWalkIK: idEventDef = idEventDef("DisableWalkIK")
val EV_EnableLegIK: idEventDef = idEventDef("EnableLegIK", "d")
val EV_EnableWalkIK: idEventDef = idEventDef("EnableWalkIK")
val EV_Footstep: idEventDef = idEventDef("footstep")
val EV_FootstepLeft: idEventDef = idEventDef("leftFoot")
val EV_FootstepRight: idEventDef = idEventDef("rightFoot")

// D3XP events
val EV_SetDamageGroupScale: idEventDef = idEventDef("setDamageGroupScale", "sf")
val EV_SetDamageGroupScaleAll: idEventDef = idEventDef("setDamageGroupScaleAll", "f")
val EV_GetDamageGroupScale: idEventDef = idEventDef("getDamageGroupScale", "s", 'f')
val EV_SetDamageCap: idEventDef = idEventDef("setDamageCap", "f")
val EV_SetWaitState: idEventDef = idEventDef("setWaitState", "s")
val EV_GetWaitState: idEventDef = idEventDef("getWaitState", null, 's')

/* **********************************************************************

 idAnimState

 ***********************************************************************/
class idAnimState {
    var animBlendFrames: Int
    var idleAnim: Boolean
    var lastAnimBlendFrames // allows override anims to blend based on the last transition time
            : Int
    val state: idStr
    private var animator: idAnimator?
    private var channel: Int
    private var disabled: Boolean
    private var self: idActor?
    private var thread: idThread?

    // ~idAnimState();
    fun Save(savefile: idSaveGame) {
        savefile.WriteObject(self)

        // Save the entity owner of the animator
        savefile.WriteObject(animator?.GetEntity())
        savefile.WriteObject(thread)
        savefile.WriteString(state)
        savefile.WriteInt(animBlendFrames)
        savefile.WriteInt(lastAnimBlendFrames)
        savefile.WriteInt(channel)
        savefile.WriteBool(idleAnim)
        savefile.WriteBool(disabled)
    }

    fun Restore(savefile: idRestoreGame) {
        self = savefile.ReadObject() as idActor?
        val animOwner = savefile.ReadObject() as idEntity?
        if (animOwner != null) {
            animator = animOwner.GetAnimator()
        }
        thread = savefile.ReadObject() as idThread?
        savefile.ReadString(state)
        animBlendFrames = savefile.ReadInt()
        lastAnimBlendFrames = savefile.ReadInt()
        channel = savefile.ReadInt()
        idleAnim = savefile.ReadBool()
        disabled = savefile.ReadBool()
    }

    fun Init(owner: idActor?, _animator: idAnimator?, animchannel: Int) {
        assert(owner != null)
        assert(_animator != null)
        self = owner
        animator = _animator
        channel = animchannel
        if (null == thread) {
            thread = idThread()
            thread!!.ManualDelete()
        }
        thread!!.EndThread()
        thread!!.ManualControl()
    }

    fun Shutdown() {
//	delete thread;
        thread = null
    }

    fun SetState(statename: String?, blendFrames: Int) {
        val func: function_t?
        func = self!!.scriptObject.GetFunction(statename)
        if (null == func) {
            assert(false)
            idGameLocal.Error(
                "Can't find function '%s' in object '%s'", statename, self!!.scriptObject.GetTypeName()
            )
            return
        }
        state.set(statename)
        disabled = false
        animBlendFrames = blendFrames
        lastAnimBlendFrames = blendFrames
        thread!!.CallFunction(self!!, func, true)
        animBlendFrames = blendFrames
        lastAnimBlendFrames = blendFrames
        disabled = false
        idleAnim = false
        if (SysCvar.ai_debugScript.GetInteger() == self!!.entityNumber) {
            Game_local.gameLocal.Printf("%d: %s: Animstate: %s\n", Game_local.gameLocal.time, self!!.name, state)
        }
    }

    fun StopAnim(frames: Int) {
        animBlendFrames = 0
        animator!!.Clear(channel, Game_local.gameLocal.time, Anim.FRAME2MS(frames))
    }

    fun PlayAnim(anim: Int) {
        if (anim != 0) {
            animator!!.PlayAnim(channel, anim, Game_local.gameLocal.time, Anim.FRAME2MS(animBlendFrames))
        }
        animBlendFrames = 0
    }

    fun CycleAnim(anim: Int) {
        if (anim != 0) {
            animator!!.CycleAnim(channel, anim, Game_local.gameLocal.time, Anim.FRAME2MS(animBlendFrames))
        }
        animBlendFrames = 0
    }

    fun BecomeIdle() {
        idleAnim = true
    }

    fun UpdateState(): Boolean {
        if (disabled) {
            return false
        }
        if (SysCvar.ai_debugScript.GetInteger() == self!!.entityNumber) {
            thread!!.EnableDebugInfo()
        } else {
            thread!!.DisableDebugInfo()
        }
        thread!!.Execute()
        return true
    }

    fun Disabled(): Boolean {
        return disabled
    }

    fun Enable(blendFrames: Int) {
        if (disabled) {
            disabled = false
            animBlendFrames = blendFrames
            lastAnimBlendFrames = blendFrames
            if (state.Length() != 0) {
                SetState(state.toString(), blendFrames)
            }
        }
    }

    fun Disable() {
        disabled = true
        idleAnim = false
    }

    fun AnimDone(blendFrames: Int): Boolean {
        val animDoneTime: Int
        animDoneTime = animator!!.CurrentAnim(channel).GetEndTime()
        return if (animDoneTime < 0) {
            // playing a cycle
            false
        } else animDoneTime - Anim.FRAME2MS(blendFrames) <= Game_local.gameLocal.time
    }

    fun IsIdle(): Boolean {
        return disabled || idleAnim
    }

    fun GetAnimFlags(): animFlags_t {
        var flags = animFlags_t()

//            memset(flags, 0, sizeof(flags));
        if (!disabled && !AnimDone(0)) {
            flags = animator!!.GetAnimFlags(animator!!.CurrentAnim(channel).AnimNum())
        }
        return flags
    }

    //
    //
    init {
        state = idStr()
        self = null
        animator = null
        thread = null
        idleAnim = true
        disabled = true
        channel = Anim.ANIMCHANNEL_ALL
        animBlendFrames = 0
        lastAnimBlendFrames = 0
    }
}

class idAttachInfo {
    var channel = 0
    val ent: idEntityPtr<idEntity?> = idEntityPtr()
}

class copyJoints_t {
    var   /*jointHandle_t*/from: CInt = CInt()
    var mod: jointModTransform_t = jointModTransform_t.entries.get(0)
    var   /*jointHandle_t*/to: CInt = CInt()
}

/* **********************************************************************

 idActor

 ***********************************************************************/
open class idActor : idAFEntity_Gibbable() {
    companion object {
        val Type = idTypeInfo("idActor", "idAFEntity_Gibbable") { idActor() }

        //public	CLASS_PROTOTYPE( idActor );
        //        public static idTypeInfo Type;
        //
        //        public static idClass CreateInstance();
        //        public idTypeInfo GetType();
        private val eventCallbacks: MutableMap<idEventDef, eventCallback_t<*>> = HashMap()

        // virtual					~idActor( void );
        fun getEventCallBacks(): MutableMap<idEventDef, eventCallback_t<*>> {
            return eventCallbacks
        }

        init {
            eventCallbacks.putAll(idAFEntity_Gibbable.getEventCallBacks())
            eventCallbacks[AI_EnableEyeFocus] = eventCallback_t0 { obj: idActor -> obj.Event_EnableEyeFocus() }
            eventCallbacks[AI_DisableEyeFocus] = eventCallback_t0 { obj: idActor -> obj.Event_DisableEyeFocus() }
            eventCallbacks[EV_Footstep] = eventCallback_t0 { obj: idActor -> obj.Event_Footstep() }
            eventCallbacks[EV_FootstepLeft] = eventCallback_t0 { obj: idActor -> obj.Event_Footstep() }
            eventCallbacks[EV_FootstepRight] = eventCallback_t0 { obj: idActor -> obj.Event_Footstep() }
            eventCallbacks[EV_EnableWalkIK] = eventCallback_t0 { obj: idActor -> obj.Event_EnableWalkIK() }
            eventCallbacks[EV_DisableWalkIK] = eventCallback_t0 { obj: idActor -> obj.Event_DisableWalkIK() }
            eventCallbacks[EV_EnableLegIK] =
                eventCallback_t1 { obj: idActor, num: idEventArg<*>? -> obj.Event_EnableLegIK(num as idEventArg<Int>) }
            eventCallbacks[EV_DisableLegIK] =
                eventCallback_t1 { obj: idActor, num: idEventArg<*>? -> obj.Event_DisableLegIK(num as idEventArg<Int>) }
            eventCallbacks[AI_PreventPain] =
                eventCallback_t1 { obj: idActor, duration: idEventArg<*>? -> obj.Event_PreventPain(duration as idEventArg<Float>) }
            eventCallbacks[AI_DisablePain] = eventCallback_t0 { obj: idActor -> obj.Event_DisablePain() }
            eventCallbacks[AI_EnablePain] = eventCallback_t0 { obj: idActor -> obj.Event_EnablePain() }
            eventCallbacks[AI_GetPainAnim] = eventCallback_t0 { obj: idActor -> obj.Event_GetPainAnim() }
            eventCallbacks[AI_SetAnimPrefix] =
                eventCallback_t1 { obj: idActor, prefix: idEventArg<*>? -> obj.Event_SetAnimPrefix(prefix as idEventArg<String?>) }
            eventCallbacks[AI_StopAnim] =
                eventCallback_t2 { obj: idActor, channel: idEventArg<*>?, frames: idEventArg<*> ->
                    obj.Event_StopAnim(
                        channel as idEventArg<Int>, frames as idEventArg<Int>
                    )
                }
            eventCallbacks[AI_PlayAnim] =
                eventCallback_t2 { obj: idActor, _channel: idEventArg<*>?, _animName: idEventArg<*> ->
                    obj.Event_PlayAnim(
                        _channel as idEventArg<Int>, _animName as idEventArg<String>
                    )
                }
            eventCallbacks[AI_PlayCycle] =
                eventCallback_t2 { obj: idActor, _channel: idEventArg<*>?, _animName: idEventArg<*> ->
                    obj.Event_PlayCycle(
                        _channel as idEventArg<Int>, _animName as idEventArg<String>
                    )
                }
            eventCallbacks[AI_IdleAnim] =
                eventCallback_t2 { obj: idActor, _channel: idEventArg<*>?, _animName: idEventArg<*> ->
                    obj.Event_IdleAnim(
                        _channel as idEventArg<Int>, _animName as idEventArg<String>
                    )
                }
            eventCallbacks[AI_SetSyncedAnimWeight] =
                eventCallback_t3 { obj: idActor, _channel: idEventArg<*>, _anim: idEventArg<*>, _weight: idEventArg<*> ->
                    obj.Event_SetSyncedAnimWeight(
                        _channel as idEventArg<Int>, _anim as idEventArg<Int>, _weight as idEventArg<Float>
                    )
                }
            eventCallbacks[AI_SetBlendFrames] =
                eventCallback_t2 { obj: idActor, _channel: idEventArg<*>, _blendFrames: idEventArg<*> ->
                    obj.Event_SetBlendFrames(_channel as idEventArg<Int>, _blendFrames as idEventArg<Int>)
                }
            eventCallbacks[AI_GetBlendFrames] = eventCallback_t1 { obj: idActor, _channel: idEventArg<*>? ->
                obj.Event_GetBlendFrames(_channel as idEventArg<Int>)
            }
            eventCallbacks[AI_AnimState] =
                eventCallback_t3 { obj: idActor, channel: idEventArg<*>, statename: idEventArg<*>, blendFrames: idEventArg<*> ->
                    obj.Event_AnimState(
                        channel as idEventArg<Int>, statename as idEventArg<String?>, blendFrames as idEventArg<Int>
                    )
                }
            eventCallbacks[AI_GetAnimState] =
                eventCallback_t1 { obj: idActor, channel: idEventArg<*>? -> obj.Event_GetAnimState(channel as idEventArg<Int>) }
            eventCallbacks[AI_InAnimState] =
                eventCallback_t2 { obj: idActor, channel: idEventArg<*>, statename: idEventArg<*> ->
                    obj.Event_InAnimState(
                        channel as idEventArg<Int>, statename as idEventArg<String?>
                    )
                }
            eventCallbacks[AI_FinishAction] = eventCallback_t1 { obj: idActor, actionname: idEventArg<*>? ->
                obj.Event_FinishAction(actionname as idEventArg<String?>)
            }
            eventCallbacks[AI_AnimDone] =
                eventCallback_t2 { obj: idActor, channel: idEventArg<*>, _blendFrames: idEventArg<*> ->
                    obj.Event_AnimDone(
                        channel as idEventArg<Int>, _blendFrames as idEventArg<Int>
                    )
                }
            eventCallbacks[AI_OverrideAnim] =
                eventCallback_t1 { obj: idActor, channel: idEventArg<*>? -> obj.Event_OverrideAnim(channel as idEventArg<Int>) }
            eventCallbacks[AI_EnableAnim] =
                eventCallback_t2 { obj: idActor, channel: idEventArg<*>, _blendFrames: idEventArg<*> ->
                    obj.Event_EnableAnim(
                        channel as idEventArg<Int>, _blendFrames as idEventArg<Int>
                    )
                }
            eventCallbacks[AI_HasAnim] =
                eventCallback_t2 { obj: idActor, channel: idEventArg<*>, animName: idEventArg<*> ->
                    obj.Event_HasAnim(
                        channel as idEventArg<Int>, animName as idEventArg<String>
                    )
                }
            eventCallbacks[AI_CheckAnim] =
                eventCallback_t2 { obj: idActor, channel: idEventArg<*>, animname: idEventArg<*> ->
                    obj.Event_CheckAnim(
                        channel as idEventArg<Int>, animname as idEventArg<String>
                    )
                }
            eventCallbacks[AI_ChooseAnim] =
                eventCallback_t2 { obj: idActor, channel: idEventArg<*>, animname: idEventArg<*> ->
                    obj.Event_ChooseAnim(
                        channel as idEventArg<Int>, animname as idEventArg<String>
                    )
                }
            eventCallbacks[AI_AnimLength] =
                eventCallback_t2 { obj: idActor, channel: idEventArg<*>?, animname: idEventArg<*> ->
                    obj.Event_AnimLength(
                        channel as idEventArg<Int>, animname as idEventArg<String>
                    )
                }
            eventCallbacks[AI_AnimDistance] =
                eventCallback_t2 { obj: idActor, channel: idEventArg<*>?, animname: idEventArg<*> ->
                    obj.Event_AnimDistance(
                        channel as idEventArg<Int>, animname as idEventArg<String>
                    )
                }
            eventCallbacks[AI_HasEnemies] = eventCallback_t0 { obj: idActor -> obj.Event_HasEnemies() }
            eventCallbacks[AI_NextEnemy] =
                eventCallback_t1 { obj: idActor, _ent: idEventArg<*>? -> obj.Event_NextEnemy(_ent as idEventArg<idEntity?>) }
            eventCallbacks[AI_ClosestEnemyToPoint] = eventCallback_t1 { obj: idActor, pos: idEventArg<*>? ->
                obj.Event_ClosestEnemyToPoint(pos as idEventArg<idVec3>)
            }
            eventCallbacks[EV_StopSound] =
                eventCallback_t2 { obj: idActor, channel: idEventArg<*>, netSync: idEventArg<*> ->
                    obj.Event_StopSound(
                        channel as idEventArg<Int>, netSync as idEventArg<Int>
                    )
                }
            eventCallbacks[AI_SetNextState] =
                eventCallback_t1 { obj: idActor, name: idEventArg<*>? -> obj.Event_SetNextState(name as idEventArg<String?>) }
            eventCallbacks[AI_SetState] = eventCallback_t1 { obj: idActor, name: idEventArg<*> ->
                obj.Event_SetState(
                    name as idEventArg<String?>
                )
            }

            eventCallbacks[AI_GetState] = eventCallback_t0 { obj: idActor -> obj.Event_GetState() }
            eventCallbacks[AI_GetHead] = eventCallback_t0 { obj: idActor -> obj.Event_GetHead() }

            // D3XP events
            eventCallbacks[EV_SetDamageGroupScale] =
                eventCallback_t2 { obj: idActor, groupName: idEventArg<*>, scale: idEventArg<*> ->
                    obj.Event_SetDamageGroupScale(groupName as idEventArg<String>, scale as idEventArg<Float>)
                }
            eventCallbacks[EV_SetDamageGroupScaleAll] =
                eventCallback_t1 { obj: idActor, scale: idEventArg<*>? ->
                    obj.Event_SetDamageGroupScaleAll(scale as idEventArg<Float>)
                }
            eventCallbacks[EV_GetDamageGroupScale] =
                eventCallback_t1 { obj: idActor, groupName: idEventArg<*>? ->
                    obj.Event_GetDamageGroupScale(groupName as idEventArg<String>)
                }
            eventCallbacks[EV_SetDamageCap] =
                eventCallback_t1 { obj: idActor, cap: idEventArg<*>? ->
                    obj.Event_SetDamageCap(cap as idEventArg<Float>)
                }
            eventCallbacks[EV_SetWaitState] =
                eventCallback_t1 { obj: idActor, state: idEventArg<*>? ->
                    obj.Event_SetWaitState(state as idEventArg<String>)
                }
            eventCallbacks[EV_GetWaitState] = eventCallback_t0 { obj: idActor -> obj.Event_GetWaitState() }
        }

    }

    var enemyList // list of characters that have targeted the player as their enemy
            : idLinkList<idActor>
    var enemyNode // node linked into an entity's enemy list for quick lookups of who is attacking him
            : idLinkList<idActor>
    var rank // monsters don't fight back if the attacker's rank is higher
            : Int
    var team: Int
    val viewAxis // view axis of the actor
            : idMat3
    protected var allowEyeFocus: Boolean
    protected var allowPain: Boolean
    protected val animPrefix: idStr
    protected var attachments: List.idList<idAttachInfo> = List.idList { idAttachInfo() }

    // blinking
    protected var blink_anim: Int
    protected var blink_max: Int
    protected var blink_min: Int
    protected var blink_time: Int
    protected var copyJoints // copied from the body animation to the head model
            : List.idList<copyJoints_t>
    protected var damageGroups // body damage groups
            : idStrList = idStrList()
    protected var damageScale // damage scale per damage gruop
            : List.idFloatList = List.idFloatList()
    protected val deltaViewAngles // delta angles relative to view input angles
            : idAngles
    protected val eyeOffset // offset of eye relative to physics origin
            : idVec3
    protected var finalBoss: Boolean

    //
    // friend class			idAnimState;
    //
    //
    protected var fovDot // cos( fovDegrees )
            : Float
    protected val head: idEntityPtr<idAFAttachment>
    protected var headAnim: idAnimState

    // D3XP: maximum damage this actor can take in a single hit (-1 = no cap)
    var damageCap: Int = -1
    protected var idealState: function_t?

    //
    // joint handles
    protected var   /*jointHandle_t*/leftEyeJoint: Int
    protected var legsAnim: idAnimState
    protected val modelOffset // offset of visual model relative to the physics origin
            : idVec3
    protected val painAnim: idStr

    //
    protected var painTime: Int

    //
    protected var pain_debounce_time // next time the actor can show pain
            : Int
    protected var pain_delay // time between playing pain sound
            : Int
    protected var pain_threshold // how much damage monster can take at any one time before playing pain animation
            : Int
    protected var   /*jointHandle_t*/rightEyeJoint: Int

    //
    // script variables
    protected var scriptThread: idThread?
    protected var   /*jointHandle_t*/soundJoint: Int

    //
    // state variables
    protected var state: function_t?
    protected var torsoAnim: idAnimState

    //
    protected var use_combat_bbox // whether to use the bounding box for combat collision
            : Boolean
    protected val waitState: idStr

    //
    protected var walkIK: idIK_Walk
    override fun Spawn() {
        super.Spawn()
        val ent = arrayOfNulls<idEntity>(1)
        val jointName = idStr()
        val fovDegrees = CFloat()
        val rank = CInt()
        val team = CInt()
        val use_combat_bbox = CBool(false)

        animPrefix.set("")
        state = null
        idealState = null

        spawnArgs.GetInt("rank", "0", rank)
        spawnArgs.GetInt("team", "0", team)
        this.rank = rank._val
        this.team = team._val
        spawnArgs.GetVector("offsetModel", "0 0 0", modelOffset)
        spawnArgs.GetBool("use_combat_bbox", "0", use_combat_bbox)
        this.use_combat_bbox = use_combat_bbox._val
        viewAxis.set(GetPhysics().GetAxis())
        spawnArgs.GetFloat("fov", "90", fovDegrees)
        SetFOV(fovDegrees._val)
        pain_debounce_time = 0
        pain_delay = SEC2MS(spawnArgs.GetFloat("pain_delay")).toInt()
        pain_threshold = spawnArgs.GetInt("pain_threshold")
        LoadAF()
        walkIK.Init(this, IK.IK_ANIM, modelOffset)

        // the animation used to be set to the IK_ANIM at this point, but that was fixed, resulting in
        // attachments not binding correctly, so we're stuck setting the IK_ANIM before attaching things.
        animator.ClearAllAnims(Game_local.gameLocal.time, 0)
        animator.SetFrame(Anim.ANIMCHANNEL_ALL, animator.GetAnim(IK.IK_ANIM), 0, 0, 0)

        // spawn any attachments we might have
        var kv = spawnArgs.MatchPrefix("def_attach", null)
        while (kv != null) {
            val args = idDict()
            args.Set("classname", kv.GetValue())

            // make items non-touchable so the player can't take them out of the character's hands
            args.Set("no_touch", "1")

            // don't let them drop to the floor
            args.Set("dropToFloor", "0")
            Game_local.gameLocal.SpawnEntityDef(args, ent)
            if (ent[0] == null) {
                idGameLocal.Error("Couldn't spawn '%s' to attach to entity '%s'", kv.GetValue(), name)
            } else {
                Attach(ent[0]!!)
            }
            kv = spawnArgs.MatchPrefix("def_attach", kv)
        }
        SetupDamageGroups()
        SetupHead()

        // clear the bind anim
        animator.ClearAllAnims(Game_local.gameLocal.time, 0)
        val headEnt: idEntity? = head.GetEntity()
        val headAnimator: idAnimator?
        headAnimator = if (headEnt != null) {
            headEnt.GetAnimator()
        } else {
            animator
        }
        if (headEnt != null) {
            // set up the list of joints to copy to the head
            kv = spawnArgs.MatchPrefix("copy_joint", null)
            while (kv != null) {
                if (kv.GetValue().IsEmpty()) {
                    // probably clearing out inherited key, so skip it
                    kv = spawnArgs.MatchPrefix("copy_joint", kv)
                    continue
                }
                val copyJoint = copyJoints_t()
                jointName.set(kv.GetKey())
                if (jointName.StripLeadingOnce("copy_joint_world ")) {
                    copyJoint.mod = jointModTransform_t.JOINTMOD_WORLD_OVERRIDE
                } else {
                    jointName.StripLeadingOnce("copy_joint ")
                    copyJoint.mod = jointModTransform_t.JOINTMOD_LOCAL_OVERRIDE
                }
                copyJoint.from._val = (animator.GetJointHandle(jointName))
                if (copyJoint.from._val == Model.INVALID_JOINT) {
                    Game_local.gameLocal.Warning("Unknown copy_joint '%s' on entity %s", jointName, name)
                    kv = spawnArgs.MatchPrefix("copy_joint", kv)
                    continue
                }
                jointName.set(kv.GetValue())
                copyJoint.to._val = (headAnimator!!.GetJointHandle(jointName))
                if (copyJoint.to._val == Model.INVALID_JOINT) {
                    Game_local.gameLocal.Warning("Unknown copy_joint '%s' on head of entity %s", jointName, name)
                    kv = spawnArgs.MatchPrefix("copy_joint", kv)
                    continue
                }
                copyJoints.Append(copyJoint)
                kv = spawnArgs.MatchPrefix("copy_joint", kv)
            }
        }

        // set up blinking
        blink_anim = headAnimator!!.GetAnim("blink")
        blink_time = 0 // it's ok to blink right away
        blink_min = SEC2MS(spawnArgs.GetFloat("blink_min", "0.5f")).toInt()
        blink_max = SEC2MS(spawnArgs.GetFloat("blink_max", "8")).toInt()

        // set up the head anim if necessary
        val headAnim = headAnimator.GetAnim("def_head")
        if (headAnim != 0) {
            if (headEnt != null) {
                headAnimator.CycleAnim(Anim.ANIMCHANNEL_ALL, headAnim, Game_local.gameLocal.time, 0)
            } else {
                headAnimator.CycleAnim(Anim.ANIMCHANNEL_HEAD, headAnim, Game_local.gameLocal.time, 0)
            }
        }
        if (spawnArgs.GetString("sound_bone", "", jointName)) {
            soundJoint = animator.GetJointHandle(jointName)
            if (soundJoint == Model.INVALID_JOINT) {
                Game_local.gameLocal.Warning(
                    "idAnimated '%s' at (%s): cannot find joint '%s' for sound playback",
                    name,
                    GetPhysics().GetOrigin().ToString(0),
                    jointName
                )
            }
        }
        finalBoss = spawnArgs.GetBool("finalBoss")
        FinishSetup()
    }

    open fun Restart() {
        assert(head.GetEntity() == null)
        SetupHead()
        FinishSetup()
    }

    /*
     ================
     obj.Save

     archive object for savegame file
     ================
     */
    override fun Save(savefile: idSaveGame) {
        super.Save(savefile)
        var ent: idActor?
        var i: Int
        savefile.WriteInt(team)
        savefile.WriteInt(rank)
        savefile.WriteMat3(viewAxis)
        savefile.WriteInt(enemyList.Num())
        ent = enemyList.Next()
        while (ent != null) {
            savefile.WriteObject(ent)
            ent = ent.enemyNode.Next()
        }
        savefile.WriteFloat(fovDot)
        savefile.WriteVec3(eyeOffset)
        savefile.WriteVec3(modelOffset)
        savefile.WriteAngles(deltaViewAngles)
        savefile.WriteInt(pain_debounce_time)
        savefile.WriteInt(pain_delay)
        savefile.WriteInt(pain_threshold)
        savefile.WriteInt(damageGroups.size())
        i = 0
        while (i < damageGroups.size()) {
            savefile.WriteString(damageGroups[i])
            i++
        }
        savefile.WriteInt(damageScale.Num())
        i = 0
        while (i < damageScale.Num()) {
            savefile.WriteFloat(damageScale[i])
            i++
        }
        savefile.WriteBool(use_combat_bbox)
        head.Save(savefile)
        savefile.WriteInt(copyJoints.Num())
        i = 0
        while (i < copyJoints.Num()) {
            savefile.WriteInt((copyJoints[i].mod).ordinal)
            savefile.WriteJoint(copyJoints[i].from._val)
            savefile.WriteJoint(copyJoints[i].to._val)
            i++
        }
        savefile.WriteJoint(leftEyeJoint)
        savefile.WriteJoint(rightEyeJoint)
        savefile.WriteJoint(soundJoint)
        walkIK.Save(savefile)
        savefile.WriteString(animPrefix)
        savefile.WriteString(painAnim)
        savefile.WriteInt(blink_anim)
        savefile.WriteInt(blink_time)
        savefile.WriteInt(blink_min)
        savefile.WriteInt(blink_max)

        // script variables
        savefile.WriteObject(scriptThread)
        savefile.WriteString(waitState)
        headAnim.Save(savefile)
        torsoAnim.Save(savefile)
        legsAnim.Save(savefile)
        savefile.WriteBool(allowPain)
        savefile.WriteBool(allowEyeFocus)
        savefile.WriteInt(painTime)
        savefile.WriteInt(attachments.Num())
        i = 0
        while (i < attachments.Num()) {
            attachments[i].ent.Save(savefile)
            savefile.WriteInt(attachments[i].channel)
            i++
        }
        savefile.WriteBool(finalBoss)
        val token = idToken()

        //FIXME: this is unneccesary
        if (state != null) {
            val src = idLexer(state!!.Name(), state!!.Name().length, "idAI::Save")
            src.ReadTokenOnLine(token)
            src.ExpectTokenString("::")
            src.ReadTokenOnLine(token)
            savefile.WriteString(token)
        } else {
            savefile.WriteString("")
        }
        if (idealState != null) {
            val src = idLexer(idealState!!.Name(), idealState!!.Name().length, "idAI::Save")
            src.ReadTokenOnLine(token)
            src.ExpectTokenString("::")
            src.ReadTokenOnLine(token)
            savefile.WriteString(token)
        } else {
            savefile.WriteString("")
        }
        if (isD3XP) {
            savefile.WriteInt(damageCap)
        }
    }

    /*
     ================
     obj.Restore

     unarchives object from save game file
     ================
     */
    override fun Restore(savefile: idRestoreGame) {
        super.Restore(savefile)
        var i: Int
        val num = CInt()
        var ent: idActor?
        team = savefile.ReadInt()
        rank = savefile.ReadInt()
        savefile.ReadMat3(viewAxis)
        savefile.ReadInt(num)
        i = 0
        while (i < num._val) {
            ent = savefile.ReadObject() as idActor?
            assert(ent != null)
            if (ent != null) {
                ent.enemyNode.AddToEnd(enemyList)
            }
            i++
        }
        fovDot = savefile.ReadFloat()
        savefile.ReadVec3(eyeOffset)
        savefile.ReadVec3(modelOffset)
        savefile.ReadAngles(deltaViewAngles)
        pain_debounce_time = savefile.ReadInt()
        pain_delay = savefile.ReadInt()
        pain_threshold = savefile.ReadInt()
        savefile.ReadInt(num)
        damageGroups.SetGranularity(1)
        damageGroups.setSize(num._val)
        i = 0
        while (i < num._val) {
            savefile.ReadString(damageGroups[i])
            i++
        }
        savefile.ReadInt(num)
        damageScale.SetNum(num._val)
        i = 0
        while (i < num._val) {
            damageScale[i] = savefile.ReadFloat()
            i++
        }
        use_combat_bbox = savefile.ReadBool()
        head.Restore(savefile)
        savefile.ReadInt(num)
        copyJoints.SetNum(num._val)
        i = 0
        while (i < num._val) {
            val `val` = CInt()
            savefile.ReadInt(`val`)

            copyJoints[i] = copyJoints_t()
            copyJoints[i].mod = jointModTransform_t.entries[`val`._val]
            savefile.ReadJoint(copyJoints[i].from)
            savefile.ReadJoint(copyJoints[i].to)
            i++
        }
        leftEyeJoint = savefile.ReadJoint()
        rightEyeJoint = savefile.ReadJoint()
        soundJoint = savefile.ReadJoint()
        walkIK.Restore(savefile)
        savefile.ReadString(animPrefix)
        savefile.ReadString(painAnim)
        blink_anim = savefile.ReadInt()
        blink_time = savefile.ReadInt()
        blink_min = savefile.ReadInt()
        blink_max = savefile.ReadInt()
        scriptThread = savefile.ReadObject() as idThread?
        savefile.ReadString(waitState)
        headAnim.Restore(savefile)
        torsoAnim.Restore(savefile)
        legsAnim.Restore(savefile)
        allowPain = savefile.ReadBool()
        allowEyeFocus = savefile.ReadBool()
        painTime = savefile.ReadInt()
        savefile.ReadInt(num)
        i = 0
        while (i < num._val) {
            val attach = attachments.Alloc()!!
            attach.ent.Restore(savefile)
            attach.channel = savefile.ReadInt()
            i++
        }
        finalBoss = savefile.ReadBool()
        val stateName = idStr()
        savefile.ReadString(stateName)
        if (stateName.Length() > 0) {
            state = GetScriptFunction(stateName.toString())
        }
        savefile.ReadString(stateName)
        if (stateName.Length() > 0) {
            idealState = GetScriptFunction(stateName.toString())
        }
        if (isD3XP) {
            damageCap = savefile.ReadInt()
        }
    }

    override fun Hide() {
        var ent: idEntity?
        var next: idEntity?
        idAFEntity_Base_Hide() //TODO:super size me
        if (head.GetEntity() != null) {
            head.GetEntity()!!.Hide()
        }
        ent = GetNextTeamEntity()
        while (ent != null) {
            next = ent.GetNextTeamEntity()
            if (ent.GetBindMaster() == this) {
                ent.Hide()
                if (ent is idLight) {
                    ent.Off()
                }
            }
            ent = next
        }
        UnlinkCombat()
    }

    override fun Show() {
        var ent: idEntity?
        var next: idEntity?
        idAFEntity_Base_Show() //TODO:super size me
        if (head.GetEntity() != null) {
            head.GetEntity()!!.Show()
        }
        ent = GetNextTeamEntity()
        while (ent != null) {
            next = ent.GetNextTeamEntity()
            if (ent.GetBindMaster() == this) {
                ent.Show()
                if (ent is idLight) {
                    if (isD3XP) {
                        if (!spawnArgs.GetBool("lights_off", "0")) {
                            ent.On()
                        }
                    }
                }
            }
            ent = next
        }
        LinkCombat()
    }

    override fun GetDefaultSurfaceType(): Int {
        return (surfTypes_t.SURFTYPE_FLESH).ordinal
    }

    override fun ProjectOverlay(origin: idVec3, dir: idVec3, size: Float, material: String) {
        var ent: idEntity?
        var next: idEntity?
        idEntity_ProjectOverlay(origin, dir, size, material)
        ent = GetNextTeamEntity()
        while (ent != null) {
            next = ent.GetNextTeamEntity()
            if (ent.GetBindMaster() == this) {
                if (ent.fl.takedamage && ent.spawnArgs.GetBool("bleed")) {
                    ent.ProjectOverlay(origin, dir, size, material)
                }
            }
            ent = next
        }
    }

    override fun LoadAF(): Boolean {
        val fileName = idStr()
        if (!spawnArgs.GetString("ragdoll", "*unknown*", fileName) || 0 == fileName.Length()) {
            return false
        }
        af.SetAnimator(GetAnimator())
        return af.Load(this, fileName)
    }

    fun SetupBody() {
        var jointname: String?
        animator.ClearAllAnims(Game_local.gameLocal.time, 0)
        animator.ClearAllJoints()
        val headEnt: idEntity? = head.GetEntity()
        if (headEnt != null) {
            jointname = spawnArgs.GetString("bone_leftEye")
            leftEyeJoint = headEnt.GetAnimator()!!.GetJointHandle(jointname)
            jointname = spawnArgs.GetString("bone_rightEye")
            rightEyeJoint = headEnt.GetAnimator()!!.GetJointHandle(jointname)

            // set up the eye height.  check if it's specified in the def.
            val eyeHeight = CFloat(eyeOffset.z)
            if (!spawnArgs.GetFloat("eye_height", "0", eyeHeight)) {
                // if not in the def, then try to base it off the idle animation
                val anim = headEnt.GetAnimator()!!.GetAnim("idle")
                if (anim != 0 && leftEyeJoint != Model.INVALID_JOINT) {
                    val pos = idVec3()
                    val axis = idMat3()
                    headEnt.GetAnimator()!!.PlayAnim(Anim.ANIMCHANNEL_ALL, anim, Game_local.gameLocal.time, 0)
                    headEnt.GetAnimator()!!.GetJointTransform(leftEyeJoint, Game_local.gameLocal.time, pos, axis)
                    headEnt.GetAnimator()!!.ClearAllAnims(Game_local.gameLocal.time, 0)
                    headEnt.GetAnimator()!!.ForceUpdate()
                    pos.plusAssign(headEnt.GetPhysics().GetOrigin().minus(GetPhysics().GetOrigin()))
                    eyeOffset.set(pos.plus(modelOffset))
                } else {
                    // just base it off the bounding box size
                    eyeOffset.z = GetPhysics().GetBounds()[1].z - 6
                }
            } else {
                eyeOffset.z = eyeHeight._val
            }
            headAnim.Init(this, headEnt.GetAnimator()!!, Anim.ANIMCHANNEL_ALL)
        } else {
            jointname = spawnArgs.GetString("bone_leftEye")
            leftEyeJoint = animator.GetJointHandle(jointname)
            jointname = spawnArgs.GetString("bone_rightEye")
            rightEyeJoint = animator.GetJointHandle(jointname)

            // set up the eye height.  check if it's specified in the def.
            val eyeHeight2 = CFloat(eyeOffset.z)
            if (!spawnArgs.GetFloat("eye_height", "0", eyeHeight2)) {
                // if not in the def, then try to base it off the idle animation
                val anim = animator.GetAnim("idle")
                if (anim != 0 && leftEyeJoint != Model.INVALID_JOINT) {
                    val pos = idVec3()
                    val axis = idMat3()
                    animator.PlayAnim(Anim.ANIMCHANNEL_ALL, anim, Game_local.gameLocal.time, 0)
                    animator.GetJointTransform(leftEyeJoint, Game_local.gameLocal.time, pos, axis)
                    animator.ClearAllAnims(Game_local.gameLocal.time, 0)
                    animator.ForceUpdate()
                    eyeOffset.set(pos.plus(modelOffset))
                } else {
                    // just base it off the bounding box size
                    eyeOffset.z = GetPhysics().GetBounds()[1].z - 6
                }
            } else {
                eyeOffset.z = eyeHeight2._val
            }
            headAnim.Init(this, animator, Anim.ANIMCHANNEL_HEAD)
        }
        waitState.set("")
        torsoAnim.Init(this, animator, Anim.ANIMCHANNEL_TORSO)
        legsAnim.Init(this, animator, Anim.ANIMCHANNEL_LEGS)
    }

    fun CheckBlink() {
        // check if it's time to blink
        if (0 == blink_anim || health <= 0 || !allowEyeFocus || blink_time > Game_local.gameLocal.time) {
            return
        }
        val headEnt: idEntity? = head.GetEntity()
        if (headEnt != null) {
            headEnt.GetAnimator()!!.PlayAnim(Anim.ANIMCHANNEL_EYELIDS, blink_anim, Game_local.gameLocal.time, 1)
        } else {
            animator.PlayAnim(Anim.ANIMCHANNEL_EYELIDS, blink_anim, Game_local.gameLocal.time, 1)
        }

        // set the next blink time
        blink_time =
            (Game_local.gameLocal.time + blink_min + Game_local.gameLocal.random.RandomFloat() * (blink_max - blink_min)).toInt()
    }

    override fun GetPhysicsToVisualTransform(origin: idVec3, axis: idMat3): Boolean {
        if (af.IsActive()) {
            af.GetPhysicsToVisualTransform(origin, axis)
            return true
        }
        origin.set(modelOffset)
        axis.set(viewAxis)
        return true
    }

    override fun GetPhysicsToSoundTransform(origin: idVec3, axis: idMat3): Boolean {
        if (soundJoint != Model.INVALID_JOINT) {
            animator.GetJointTransform(soundJoint, Game_local.gameLocal.time, origin, axis)
            origin.plusAssign(modelOffset)
            axis.set(viewAxis)
        } else {
            origin.set(GetPhysics().GetGravityNormal().times(-eyeOffset.z))
            axis.Identity()
        }
        return true
    }

    /* **********************************************************************

     script state management

     ***********************************************************************/
    // script state management
    fun ShutdownThreads() {
        headAnim.Shutdown()
        torsoAnim.Shutdown()
        legsAnim.Shutdown()
        if (scriptThread != null) {
            scriptThread!!.EndThread()
            scriptThread!!.PostEventMS(EV_Remove, 0)
            //		delete scriptThread;
            scriptThread = null
        }
    }

    /*
     ================
     obj.ShouldConstructScriptObjectAtSpawn

     Called during idEntity::Spawn to see if it should construct the script object or not.
     Overridden by subclasses that need to spawn the script object themselves.
     ================
     */
    override fun ShouldConstructScriptObjectAtSpawn(): Boolean {
        return false
    }

    /*
     ================
     obj.ConstructScriptObject

     Called during idEntity::Spawn.  Calls the constructor on the script object.
     Can be overridden by subclasses when a thread doesn't need to be allocated.
     ================
     */
    override fun ConstructScriptObject(): idThread? {
        val constructor: function_t?

        // make sure we have a scriptObject
        if (!scriptObject.HasObject()) {
            idGameLocal.Error(
                "No scriptobject set on '%s'.  Check the '%s' entityDef.", name, GetEntityDefName()
            )
        }
        if (scriptThread == null) {
            // create script thread
            scriptThread = idThread()
            scriptThread!!.ManualDelete()
            scriptThread!!.ManualControl()
            scriptThread!!.SetThreadName(name.toString())
        } else {
            scriptThread!!.EndThread()
        }

        // call script object's constructor
        constructor = scriptObject.GetConstructor()
        if (null == constructor) {
            idGameLocal.Error(
                "Missing constructor on '%s' for entity '%s'", scriptObject.GetTypeName(), name
            )
            return null
        }

        // init the script object's data
        scriptObject.ClearObject()

        // just set the current function on the script.  we'll execute in the subclasses.
        scriptThread!!.CallFunction(this, constructor, true)
        return scriptThread
    }

    fun UpdateScript() {
        var i: Int
        if (SysCvar.ai_debugScript.GetInteger() == entityNumber) {
            scriptThread!!.EnableDebugInfo()
        } else {
            scriptThread!!.DisableDebugInfo()
        }

        // a series of state changes can happen in a single frame.
        // this loop limits them in case we've entered an infinite loop.
        i = 0
        while (i < 20) {
            if (idealState != state) {
                SetState(idealState)
            }

            // don't call script until it's done waiting
            if (scriptThread!!.IsWaiting()) {
                break
            }
            scriptThread!!.Execute()
            if (idealState == state) {
                break
            }
            i++
        }
        if (i == 20) {
            scriptThread!!.Warning("obj.UpdateScript: exited loop to prevent lockup")
        }
    }

    fun GetScriptFunction(funcname: String?): function_t {
        val func: function_t?
        func = scriptObject.GetFunction(funcname)
        if (null == func) {
            if (scriptThread != null) {
                scriptThread!!.Error("Unknown function '%s' in '%s'", funcname, scriptObject.GetTypeName())
            } else {
                idGameLocal.Error("Unknown function '%s' in '%s'", funcname, scriptObject.GetTypeName())
            }
        }
        return func!!
    }

    fun SetState(newState: function_t?) {
        if (null == newState) {
            idGameLocal.Error("obj.SetState: Null state")
            return
        }
        if (SysCvar.ai_debugScript.GetInteger() == entityNumber) {
            Game_local.gameLocal.Printf("%d: %s: State: %s\n", Game_local.gameLocal.time, name, newState.Name())
        }
        state = newState
        idealState = state
        scriptThread!!.CallFunction(this, state!!, true)
    }

    fun SetState(statename: String) {
        val newState: function_t
        newState = GetScriptFunction(statename)
        SetState(newState)
    }

    /* **********************************************************************

     vision

     ***********************************************************************/
    // vision testing
    fun SetEyeHeight(height: Float) {
        eyeOffset.z = height
    }

    fun EyeHeight(): Float {
        return eyeOffset.z
    }

    fun EyeOffset(): idVec3 {
        return GetPhysics().GetGravityNormal() * -eyeOffset.z
    }

    // D3XP: expose head entity for damage group targeting
    fun GetHeadEntity(): idEntity? = head.GetEntity()

    open fun GetEyePosition(): idVec3 {
        return GetPhysics().GetOrigin() + (GetPhysics().GetGravityNormal() * -eyeOffset.z)
    }

    open fun GetViewPos(origin: idVec3, axis: idMat3) {
        origin.set(GetEyePosition())
        axis.set(viewAxis)
    }

    fun SetFOV(fov: Float) {
        fovDot = cos(DEG2RAD(fov * 0.5f))
    }

    fun CheckFOV(pos: idVec3): Boolean {
        if (fovDot == 1.0f) {
            return true
        }
        val dot: Float
        val delta = idVec3()
        delta.set(pos.minus(GetEyePosition()))

        // get our gravity normal
        val gravityDir = GetPhysics().GetGravityNormal()

        // infinite vertical vision, so project it onto our orientation plane
        delta.minusAssign(gravityDir.times(gravityDir.times(delta)))
        delta.Normalize()
        dot = viewAxis[0].times(delta)
        return dot >= fovDot
    }

    fun CanSee(ent: idEntity, useFOV: Boolean): Boolean {
        val tr = trace_s()
        val eye = idVec3()
        val toPos = idVec3()
        if (ent.IsHidden()) {
            return false
        }
        if (ent is idActor) {
            toPos.set(ent.GetEyePosition())
        } else {
            toPos.set(ent.GetPhysics().GetOrigin())
        }
        if (useFOV && !CheckFOV(toPos)) {
            return false
        }
        eye.set(GetEyePosition())
        Game_local.gameLocal.clip.TracePoint(tr, eye, toPos, Game_local.MASK_OPAQUE, this)
        return tr.fraction >= 1.0f || Game_local.gameLocal.GetTraceEntity(tr) == ent
    }

    fun PointVisible(point: idVec3): Boolean {
        val results = trace_s()
        val start = idVec3()
        val end = idVec3()
        start.set(GetEyePosition())
        end.set(point)
        end.plusAssign(2, 1.0f)
        Game_local.gameLocal.clip.TracePoint(results, start, end, Game_local.MASK_OPAQUE, this)
        return results.fraction >= 1.0f
    }

    /*
     =====================
     obj.GetAIAimTargets

     Returns positions for the AI to aim at.
     =====================
     */
    open fun GetAIAimTargets(lastSightPos: idVec3, headPos: idVec3, chestPos: idVec3) {
        headPos.set(lastSightPos.plus(EyeOffset()))
        chestPos.set(headPos.plus(lastSightPos).plus(GetPhysics().GetBounds().GetCenter()).times(0.5f))
    }

    /* **********************************************************************

     Damage

     ***********************************************************************/
    // damage
    /*
     =====================
     obj.SetupDamageGroups

     FIXME: only store group names once and store an index for each joint
     =====================
     */
    fun SetupDamageGroups() {
        var i: Int
        var arg: idKeyValue?
        val groupname = idStr()
        val jointList = List.idList<Int>()
        var jointnum: Int
        var scale: Float

        // create damage zones

        // create damage zones
        damageGroups.setSize(animator.NumJoints())
        arg = spawnArgs.MatchPrefix("damage_zone ", null)
        while (arg != null) {
            groupname.set(arg.GetKey())
            groupname.Strip("damage_zone ")
            animator.GetJointList(arg.GetValue(), jointList)
            i = 0
            while (i < jointList.Num()) {
                jointnum = jointList[i]
                damageGroups[jointnum] = groupname
                i++
            }
            jointList.Clear()
            arg = spawnArgs.MatchPrefix("damage_zone ", arg)
        }

        // initilize the damage zones to normal damage

        // initilize the damage zones to normal damage
        damageScale.SetNum(animator.NumJoints())
        i = 0
        while (i < damageScale.Num()) {
            damageScale[i] = 1.0f
            i++
        }

        // set the percentage on damage zones
        arg = spawnArgs.MatchPrefix("damage_scale ", null)
        while (arg != null) {
            scale = atof(arg.GetValue())
            groupname.set(arg.GetKey())
            groupname.Strip("damage_scale ")
            i = 0
            while (i < damageScale.Num()) {
                if (groupname == damageGroups[i]) {
                    damageScale[i] = scale
                }
                i++
            }
            arg = spawnArgs.MatchPrefix("damage_scale ", arg)
        }
    }

    /*
     ============
     obj.Damage

     this		entity that is being damaged
     inflictor	entity that is causing the damage
     attacker	entity that caused the inflictor to damage targ
     example: this=monster, inflictor=rocket, attacker=player

     dir			direction of the attack for knockback in global space
     point		point at which the damage is being inflicted, used for headshots
     damage		amount of damage being inflicted

     inflictor, attacker, dir, and point can be NULL for environmental effects

     Bleeding wounds and surface overlays are applied in the collision code that
     calls Damage()
     ============
     */
    override fun Damage(
        inflictor: idEntity?, attacker: idEntity?, dir: idVec3, damageDefName: String, damageScale: Float, location: Int
    ) {
        var inflictor = inflictor
        var attacker = attacker
        if (!fl.takedamage) {
            return
        }
        if (null == inflictor) {
            inflictor = Game_local.gameLocal.world //TODO:oSet
        }
        if (null == attacker) {
            attacker = Game_local.gameLocal.world
        }
        if (isD3XP) {
            val ts = SetTimeState(timeGroup)
            try {
                // Helltime boss is immune to all projectiles except the helltime killer
                if (finalBoss && idStr.Icmp(inflictor!!.GetEntityDefName(), "projectile_helltime_killer") != 0) {
                    return
                }
                // Maledict is immune to the falling asteroids
                if (idStr.Icmp(GetEntityDefName(), "monster_boss_d3xp_maledict") == 0 &&
                    (idStr.Icmp(damageDefName, "damage_maledict_asteroid") == 0 ||
                            idStr.Icmp(damageDefName, "damage_maledict_asteroid_splash") == 0)
                ) {
                    return
                }
            } finally {
                ts.close()
            }
        } else {
            if (finalBoss && inflictor !is idSoulCubeMissile) {
                return
            }
        }
        val damageDef = Game_local.gameLocal.FindEntityDefDict(damageDefName)
        if (null == damageDef) {
            idGameLocal.Error("Unknown damageDef '%s'", damageDefName)
            return
        }
        val damage = CInt((damageDef.GetInt("damage") * damageScale).toInt())
        damage._val = (GetDamageForLocation(damage._val, location))

        // inform the attacker that they hit someone
        attacker!!.DamageFeedback(this, inflictor, damage)
        if (damage._val > 0) {
            health -= damage._val
            // D3XP: damageCap prevents kill shots during scripted boss phases
            if (isD3XP) {
                if (damageCap >= 0 && health < damageCap) {
                    health = damageCap
                }
            }

            if (health <= 0) {
                if (health < -999) {
                    health = -999
                }
                Killed(inflictor, attacker, damage._val, dir, location)
                if (health < -20 && spawnArgs.GetBool("gib") && damageDef.GetBool("gib")) {
                    Gib(dir, damageDefName)
                }
            } else {
                Pain(inflictor, attacker, damage._val, dir, location)
            }
        } else {
            // don't accumulate knockback
            if (af.IsLoaded()) {
                // clear impacts
                af.Rest()

                // physics is turned off by calling af.Rest()
                BecomeActive(TH_PHYSICS)
            }
        }
    }

    fun GetDamageForLocation(damage: Int, location: Int): Int {
        return if (location < 0 || location >= damageScale.Num()) {
            damage
        } else ceil((damage * damageScale[location])).toInt()
    }

    fun GetDamageGroup(location: Int): String {
        return if (location < 0 || location >= damageGroups.size()) {
            ""
        } else damageGroups[location].toString()
    }

    fun ClearPain() {
        pain_debounce_time = 0
    }

    override fun Pain(
        inflictor: idEntity?, attacker: idEntity?, damage: Int, dir: idVec3, location: Int
    ): Boolean {
        if (af.IsLoaded()) {
            // clear impacts
            af.Rest()

            // physics is turned off by calling af.Rest()
            BecomeActive(TH_PHYSICS)
        }
        if (Game_local.gameLocal.time < pain_debounce_time) {
            return false
        }

        // don't play pain sounds more than necessary
        pain_debounce_time = Game_local.gameLocal.time + pain_delay
        if (health > 75) {
            StartSound("snd_pain_small", gameSoundChannel_t.SND_CHANNEL_VOICE, 0, false, null)
        } else if (health > 50) {
            StartSound("snd_pain_medium", gameSoundChannel_t.SND_CHANNEL_VOICE, 0, false, null)
        } else if (health > 25) {
            StartSound("snd_pain_large", gameSoundChannel_t.SND_CHANNEL_VOICE, 0, false, null)
        } else {
            StartSound("snd_pain_huge", gameSoundChannel_t.SND_CHANNEL_VOICE, 0, false, null)
        }
        if (!allowPain || Game_local.gameLocal.time < painTime) {
            // don't play a pain anim
            return false
        }
        if (pain_threshold != 0 && damage < pain_threshold) {
            return false
        }

        // set the pain anim
        val damageGroup = GetDamageGroup(location)
        painAnim.set("")
        if (animPrefix.Length() != 0) {
            if (damageGroup.isNotEmpty() && damageGroup != "legs") {
                painAnim.set(String.format("%s_pain_%s", animPrefix.toString(), damageGroup))
                if (!animator.HasAnim(painAnim)) {
                    painAnim.set(String.format("pain_%s", damageGroup))
                    if (!animator.HasAnim(painAnim)) {
                        painAnim.set("")
                    }
                }
            }
            if (0 == painAnim.Length()) {
                painAnim.set(String.format("%s_pain", animPrefix.toString()))
                if (!animator.HasAnim(painAnim)) {
                    painAnim.set("")
                }
            }
        } else if (damageGroup.isNotEmpty() && damageGroup != "legs") {
            painAnim.set(String.format("pain_%s", damageGroup))
            if (!animator.HasAnim(painAnim)) {
                painAnim.set(String.format("pain_%s", damageGroup))
                if (!animator.HasAnim(painAnim)) {
                    painAnim.set("")
                }
            }
        }
        if (0 == painAnim.Length()) {
            painAnim.set("pain")
        }
        if (SysCvar.g_debugDamage.GetBool()) {/*jointHandle_t*/
            Game_local.gameLocal.Printf(
                "Damage: joint: '%s', zone '%s', anim '%s'\n", animator.GetJointName(location), damageGroup, painAnim
            )
        }
        return true
    }

    /* **********************************************************************

     Model/Ragdoll

     ***********************************************************************/
    // model/combat model/ragdoll
    override fun SetCombatModel() {
        val headEnt: idAFAttachment?
        if (!use_combat_bbox) {
            if (combatModel != null) {
                combatModel!!.Unlink()
                combatModel!!.LoadModel(modelDefHandle)
            } else {
                combatModel = idClipModel(modelDefHandle)
            }
            headEnt = head.GetEntity()
            headEnt?.SetCombatModel()
        }
    }

    override fun GetCombatModel(): idClipModel? {
        return combatModel
    }

    override fun LinkCombat() {
        val headEnt: idAFAttachment?
        if (fl.hidden || use_combat_bbox) {
            return
        }
        if (combatModel != null) {
            combatModel!!.Link(
                Game_local.gameLocal.clip, this, 0, renderEntity!!.origin, renderEntity!!.axis, modelDefHandle
            )
        }
        headEnt = head.GetEntity()
        headEnt?.LinkCombat()
    }

    override fun UnlinkCombat() {
        val headEnt: idAFAttachment?
        if (combatModel != null) {
            combatModel!!.Unlink()
        }
        headEnt = head.GetEntity()
        headEnt?.UnlinkCombat()
    }

    fun StartRagdoll(): Boolean {
        val slomoStart: Float
        val slomoEnd: Float
        val jointFrictionDent: Float
        val jointFrictionDentStart: Float
        val jointFrictionDentEnd: Float
        val contactFrictionDent: Float
        val contactFrictionDentStart: Float
        val contactFrictionDentEnd: Float

        // if no AF loaded
        if (!af.IsLoaded()) {
            return false
        }

        // if the AF is already active
        if (af.IsActive()) {
            return true
        }

        // disable the monster bounding box
        GetPhysics().DisableClip()

        // start using the AF
        af.StartFromCurrentPose(spawnArgs.GetInt("velocityTime", "0"))
        slomoStart = MS2SEC(Game_local.gameLocal.time.toFloat()) + spawnArgs.GetFloat("ragdoll_slomoStart", "-1.6")
        slomoEnd = MS2SEC(Game_local.gameLocal.time.toFloat()) + spawnArgs.GetFloat("ragdoll_slomoEnd", "0.8")

        // do the first part of the death in slow motion
        af.GetPhysics().SetTimeScaleRamp(slomoStart, slomoEnd)
        jointFrictionDent = spawnArgs.GetFloat("ragdoll_jointFrictionDent", "0.1f")
        jointFrictionDentStart = MS2SEC(Game_local.gameLocal.time.toFloat()) + spawnArgs.GetFloat(
            "ragdoll_jointFrictionStart", "0.2f"
        )
        jointFrictionDentEnd = MS2SEC(Game_local.gameLocal.time.toFloat()) + spawnArgs.GetFloat(
            "ragdoll_jointFrictionEnd", "1.2"
        )

        // set joint friction dent
        af.GetPhysics().SetJointFrictionDent(jointFrictionDent, jointFrictionDentStart, jointFrictionDentEnd)
        contactFrictionDent = spawnArgs.GetFloat("ragdoll_contactFrictionDent", "0.1f")
        contactFrictionDentStart = MS2SEC(Game_local.gameLocal.time.toFloat()) + spawnArgs.GetFloat(
            "ragdoll_contactFrictionStart", "1.0f"
        )
        contactFrictionDentEnd = MS2SEC(Game_local.gameLocal.time.toFloat()) + spawnArgs.GetFloat(
            "ragdoll_contactFrictionEnd", "2.0f"
        )

        // set contact friction dent
        af.GetPhysics().SetContactFrictionDent(contactFrictionDent, contactFrictionDentStart, contactFrictionDentEnd)

        // drop any items the actor is holding
        idMoveableItem.DropItems(this, "death", null)

        // drop any articulated figures the actor is holding
        DropAFs(this, "death", null)
        RemoveAttachments()
        return true
    }

    fun StopRagdoll() {
        if (af.IsActive()) {
            af.Stop()
        }
    }

    override fun UpdateAnimationControllers(): Boolean {
        if (af.IsActive()) {
            return idAFEntity_Base_UpdateAnimationControllers()
        } else {
            animator.ClearAFPose()
        }
        if (walkIK.IsInitialized()) {
            walkIK.Evaluate()
            return true
        }
        return false
    }

    // delta view angles to allow movers to rotate the view of the actor
    fun GetDeltaViewAngles(): idAngles {
        return deltaViewAngles
    }

    fun SetDeltaViewAngles(delta: idAngles) {
        deltaViewAngles.set(delta)
    }

    fun HasEnemies(): Boolean {
        var ent: idActor?
        ent = enemyList.Next()
        while (ent != null) {
            if (!ent.fl.hidden) {
                return true
            }
            ent = ent.enemyNode.Next()
        }
        return false
    }

    fun ClosestEnemyToPoint(pos: idVec3): idActor? {
        var ent: idActor?
        var bestEnt: idActor?
        var bestDistSquared: Float
        var distSquared: Float
        val delta = idVec3()
        bestDistSquared = idMath.INFINITY
        bestEnt = null
        ent = enemyList.Next()
        while (ent != null) {
            if (ent.fl.hidden) {
                ent = ent.enemyNode.Next()
                continue
            }
            delta.set(ent.GetPhysics().GetOrigin().minus(pos))
            distSquared = delta.LengthSqr()
            if (distSquared < bestDistSquared) {
                bestEnt = ent
                bestDistSquared = distSquared
            }
            ent = ent.enemyNode.Next()
        }
        return bestEnt
    }

    fun EnemyWithMostHealth(): idActor? {
        var ent: idActor?
        var bestEnt: idActor?
        var most = -9999
        bestEnt = null
        ent = enemyList.Next()
        while (ent != null) {
            if (!ent.fl.hidden && ent.health > most) {
                bestEnt = ent
                most = ent.health
            }
            ent = ent.enemyNode.Next()
        }
        return bestEnt
    }

    open fun OnLadder(): Boolean {
        return false
    }

    open fun GetAASLocation(aas: idAAS?, pos: idVec3, areaNum: CInt) {
        val size = idVec3()
        val bounds = idBounds()
        GetFloorPos(64.0f, pos)
        if (null == aas) {
            areaNum._val = 0
            return
        }
        size.set(aas.GetSettings()!!.boundingBoxes[0][1])
        bounds[0] = size.unaryMinus()
        size.z = 32.0f
        bounds[1] = size
        areaNum._val = aas.PointReachableAreaNum(pos, bounds, AASFile.AREA_REACHABLE_WALK)
        if (areaNum._val != 0) {
            aas.PushPointIntoAreaNum(areaNum._val, pos)
        }
    }

    fun Attach(ent: idEntity) {
        val origin = idVec3()
        val axis = idMat3()
        val   /*jointHandle_t*/joint: Int
        val jointName: String?
        val attach = attachments.Alloc()!!
        val angleOffset: idAngles?
        val originOffset = idVec3()
        jointName = ent.spawnArgs.GetString("joint")
        joint = animator.GetJointHandle(jointName)
        if (joint == Model.INVALID_JOINT) {
            idGameLocal.Error(
                "Joint '%s' not found for attaching '%s' on '%s'", jointName, ent.GetClassname(), name
            )
        }
        angleOffset = ent.spawnArgs.GetAngles("angles")
        originOffset.set(ent.spawnArgs.GetVector("origin"))
        attach.channel = animator.GetChannelForJoint(joint)
        GetJointWorldTransform(joint, Game_local.gameLocal.time, origin, axis)
        attach.ent.oSet(ent)
        ent.SetOrigin(origin.plus(originOffset.times(renderEntity!!.axis)))
        val rotate = angleOffset.ToMat3()
        val newAxis = rotate.times(axis)
        ent.SetAxis(newAxis)
        ent.BindToJoint(this, joint, true)
        ent.cinematic = cinematic
    }

    override fun Teleport(origin: idVec3, angles: idAngles, destination: idEntity?) {
        GetPhysics().SetOrigin(origin.plus(idVec3(0.0f, 0.0f, CM_CLIP_EPSILON)))
        GetPhysics().SetLinearVelocity(vec3_origin)
        viewAxis.set(angles.ToMat3())
        UpdateVisuals()
        if (!IsHidden()) {
            // kill anything at the new position
            Game_local.gameLocal.KillBox(this)
        }
    }

    override fun GetRenderView(): renderView_s? {
        val rv = super.GetRenderView() //TODO:super.super....
        rv!!.viewaxis.set(viewAxis)
        rv.vieworg.set(GetEyePosition())
        return rv
    }

    /* **********************************************************************

     animation state

     ***********************************************************************/
    // animation state control
    fun GetAnim(channel: Int, animName: String): Int {
        var anim: Int
        val temp: String?
        val animatorPtr: idAnimator?
        animatorPtr = if (channel == Anim.ANIMCHANNEL_HEAD) {
            if (head.GetEntity() == null) {
                return 0
            }
            head.GetEntity()!!.GetAnimator()
        } else {
            animator
        }
        if (animPrefix.Length() != 0) {
            temp = Str.va("%s_%s", animPrefix, animName)
            anim = animatorPtr.GetAnim(temp)
            if (anim != 0) {
                return anim
            }
        }
        anim = animatorPtr.GetAnim(animName)
        return anim
    }

    fun UpdateAnimState() {
        headAnim.UpdateState()
        torsoAnim.UpdateState()
        legsAnim.UpdateState()
    }

    fun SetAnimState(channel: Int, statename: String?, blendFrames: Int) {
        val func: function_t?
        func = scriptObject.GetFunction(statename)
        if (null == func) {
            assert(false)
            idGameLocal.Error(
                "Can't find function '%s' in object '%s'", statename, scriptObject.GetTypeName()
            )
        }
        when (channel) {
            Anim.ANIMCHANNEL_HEAD -> {
                headAnim.SetState(statename, blendFrames)
                allowEyeFocus = true
            }

            Anim.ANIMCHANNEL_TORSO -> {
                torsoAnim.SetState(statename, blendFrames)
                legsAnim.Enable(blendFrames)
                allowPain = true
                allowEyeFocus = true
            }

            Anim.ANIMCHANNEL_LEGS -> {
                legsAnim.SetState(statename, blendFrames)
                torsoAnim.Enable(blendFrames)
                allowPain = true
                allowEyeFocus = true
            }

            else -> idGameLocal.Error("obj.SetAnimState: Unknown anim group")
        }
    }

    fun GetAnimState(channel: Int): idStr? {
        return when (channel) {
            Anim.ANIMCHANNEL_HEAD -> headAnim.state
            Anim.ANIMCHANNEL_TORSO -> torsoAnim.state
            Anim.ANIMCHANNEL_LEGS -> legsAnim.state
            else -> {
                idGameLocal.Error("obj.GetAnimState: Unknown anim group")
                return null
            }
        }
    }

    fun InAnimState(channel: Int, stateName: String?): Boolean {
        when (channel) {
            Anim.ANIMCHANNEL_HEAD -> if (headAnim.state.toString() == stateName) {
                return true
            }

            Anim.ANIMCHANNEL_TORSO -> if (torsoAnim.state.toString() == stateName) {
                return true
            }

            Anim.ANIMCHANNEL_LEGS -> if (legsAnim.state.toString() == stateName) {
                return true
            }

            else -> idGameLocal.Error("obj.InAnimState: Unknown anim group")
        }
        return false
    }

    fun WaitState(): String? {
        return if (waitState.Length() != 0) {
            waitState.toString()
        } else {
            null
        }
    }

    fun SetWaitState(_waitstate: String) {
        waitState.set(_waitstate)
    }

    fun AnimDone(channel: Int, blendFrames: Int): Boolean {
        val animDoneTime: Int
        animDoneTime = animator.CurrentAnim(channel).GetEndTime()
        return if (animDoneTime < 0) {
            // playing a cycle
            false
        } else animDoneTime - Anim.FRAME2MS(blendFrames) <= Game_local.gameLocal.time
    }

    override fun SpawnGibs(dir: idVec3, damageDefName: String) {
        super.SpawnGibs(dir, damageDefName)
        RemoveAttachments()
    }

    override fun Gib(dir: idVec3, damageDefName: String) {
        // no gibbing in multiplayer - by self damage or by moving objects
        if (Game_local.gameLocal.isMultiplayer) {
            return
        }
        // only gib once
        if (gibbed) {
            return
        }
        super.Gib(dir, damageDefName)
        if (head.GetEntity() != null) {
            head.GetEntity()!!.Hide()
        }
        StopSound((gameSoundChannel_t.SND_CHANNEL_VOICE).ordinal, false)
    }

    // removes attachments with "remove" set for when character dies
    protected fun RemoveAttachments() {
        var i: Int
        var ent: idEntity?

        // remove any attached entities
        i = 0
        while (i < attachments.Num()) {
            ent = attachments[i].ent.GetEntity()
            if (ent != null && ent.spawnArgs.GetBool("remove")) {
                ent.PostEventMS(EV_Remove, 0)
            }
            i++
        }
    }

    // copies animation from body to head joints
    protected fun CopyJointsFromBodyToHead() {
        val headEnt: idEntity? = head.GetEntity()
        val headAnimator: idAnimator?
        var i: Int
        val mat = idMat3()
        val axis = idMat3()
        val pos = idVec3()
        if (null == headEnt) {
            return
        }
        headAnimator = headEnt.GetAnimator()

        // copy the animation from the body to the head
        i = 0
        while (i < copyJoints.Num()) {
            if (copyJoints[i].mod == jointModTransform_t.JOINTMOD_WORLD_OVERRIDE) {
                mat.set(headEnt.GetPhysics().GetAxis().Transpose())
                GetJointWorldTransform(copyJoints[i].from._val, Game_local.gameLocal.time, pos, axis)
                pos.minusAssign(headEnt.GetPhysics().GetOrigin())
                headAnimator!!.SetJointPos(copyJoints[i].to._val, copyJoints[i].mod, pos.times(mat))
                headAnimator.SetJointAxis(
                    copyJoints[i].to._val, copyJoints[i].mod, axis.times(mat)
                )
            } else {
                animator.GetJointLocalTransform(
                    copyJoints[i].from._val, Game_local.gameLocal.time, pos, axis
                )
                headAnimator!!.SetJointPos(copyJoints[i].to._val, copyJoints[i].mod, pos)
                headAnimator.SetJointAxis(copyJoints[i].to._val, copyJoints[i].mod, axis)
            }
            i++
        }
    }

    private fun SyncAnimChannels(channel: Int, syncToChannel: Int, blendFrames: Int) {
        val headAnimator: idAnimator
        val headEnt: idAFAttachment?
        var anim: Int
        val syncAnim: idAnimBlend?
        val starttime: Int
        val blendTime: Int
        val cycle: Int
        blendTime = Anim.FRAME2MS(blendFrames)
        if (channel == Anim.ANIMCHANNEL_HEAD) {
            headEnt = head.GetEntity()
            if (headEnt != null) {
                headAnimator = headEnt.GetAnimator()
                syncAnim = animator.CurrentAnim(syncToChannel)
                if (syncAnim != null) {
                    anim = headAnimator.GetAnim(syncAnim.AnimFullName())
                    if (0 == anim) {
                        anim = headAnimator.GetAnim(syncAnim.AnimName())
                    }
                    if (anim != 0) {
                        cycle = animator.CurrentAnim(syncToChannel).GetCycleCount()
                        starttime = animator.CurrentAnim(syncToChannel).GetStartTime()
                        headAnimator.PlayAnim(Anim.ANIMCHANNEL_ALL, anim, Game_local.gameLocal.time, blendTime)
                        headAnimator.CurrentAnim(Anim.ANIMCHANNEL_ALL).SetCycleCount(cycle)
                        headAnimator.CurrentAnim(Anim.ANIMCHANNEL_ALL).SetStartTime(starttime)
                    } else {
                        headEnt.PlayIdleAnim(blendTime)
                    }
                }
            }
        } else if (syncToChannel == Anim.ANIMCHANNEL_HEAD) {
            headEnt = head.GetEntity()
            if (headEnt != null) {
                headAnimator = headEnt.GetAnimator()
                syncAnim = headAnimator.CurrentAnim(Anim.ANIMCHANNEL_ALL)
                if (syncAnim != null) {
                    anim = GetAnim(channel, syncAnim.AnimFullName())
                    if (0 == anim) {
                        anim = GetAnim(channel, syncAnim.AnimName())
                    }
                    if (anim != 0) {
                        cycle = headAnimator.CurrentAnim(Anim.ANIMCHANNEL_ALL).GetCycleCount()
                        starttime = headAnimator.CurrentAnim(Anim.ANIMCHANNEL_ALL).GetStartTime()
                        animator.PlayAnim(channel, anim, Game_local.gameLocal.time, blendTime)
                        animator.CurrentAnim(channel).SetCycleCount(cycle)
                        animator.CurrentAnim(channel).SetStartTime(starttime)
                    }
                }
            }
        } else {
            animator.SyncAnimChannels(channel, syncToChannel, Game_local.gameLocal.time, blendTime)
        }
    }

    private fun FinishSetup() {
        val scriptObjectName = arrayOfNulls<String>(1)

        // setup script object
        if (spawnArgs.GetString("scriptobject", null, scriptObjectName)) {
            if (!scriptObject.SetType(scriptObjectName[0])) {
                idGameLocal.Error("Script object '%s' not found on entity '%s'.", scriptObjectName, name)
            }
            ConstructScriptObject()
        }
        SetupBody()
    }

    private fun SetupHead() {
        val headEnt: idAFAttachment
        val jointName: String?
        val headModel: String?
        val   /*jointHandle_t*/joint: Int
        var   /*jointHandle_t*/damageJoint: Int
        var i: Int
        var sndKV: idKeyValue?
        if (Game_local.gameLocal.isClient) {
            return
        }
        headModel = spawnArgs.GetString("def_head", "")!!
        if (!headModel.isEmpty()) {
            jointName = spawnArgs.GetString("head_joint")
            joint = animator.GetJointHandle(jointName)
            if (joint == Model.INVALID_JOINT) {
                idGameLocal.Error("Joint '%s' not found for 'head_joint' on '%s'", jointName, name)
            }

            // set the damage joint to be part of the head damage group
            damageJoint = joint
            i = 0
            while (i < damageGroups.size()) {
                val d = damageGroups[i]
                if (d != null && d.toString() == "head") {
                    damageJoint =  /*(jointHandle_t)*/i
                    break
                }
                i++
            }

            // copy any sounds in case we have frame commands on the head
            val args = idDict()
            sndKV = spawnArgs.MatchPrefix("snd_", null)
            while (sndKV != null) {
                args.Set(sndKV.GetKey(), sndKV.GetValue())
                sndKV = spawnArgs.MatchPrefix("snd_", sndKV)
            }
            if (isD3XP) {
                // copy slowmo param to the head
                args.SetBool("slowmo", spawnArgs.GetBool("slowmo", "1"))
            }
            headEnt = Game_local.gameLocal.SpawnEntityType(idAFAttachment.Type, args) as idAFAttachment
            headEnt.SetName(Str.va("%s_head", name))
            headEnt.SetBody(this, headModel, damageJoint)
            head.oSet(headEnt)

            if (isD3XP) {
                val xSkin = idStr()
                if (spawnArgs.GetString("skin_head_xray", "", xSkin)) {
                    headEnt.xraySkin = DeclManager.declManager.FindSkin(xSkin.toString())
                    headEnt.UpdateModel()
                }
            }

            val origin = idVec3()
            val axis = idMat3()
            val attach = attachments.Alloc()!!
            attach.channel = animator.GetChannelForJoint(joint)
            animator.GetJointTransform(joint, Game_local.gameLocal.time, origin, axis)
            origin.set(renderEntity!!.origin + (origin + modelOffset) * renderEntity!!.axis)
            attach.ent.oSet(headEnt)
            headEnt.SetOrigin(origin)
            headEnt.SetAxis(renderEntity!!.axis)
            headEnt.BindToJoint(this, joint, true)
        }
    }

    private fun PlayFootStepSound() {
        var sound = ""
        val material: Material.idMaterial?
        if (!GetPhysics().HasGroundContacts()) {
            return
        }

        // start footstep sound based on material type
        material = GetPhysics().GetContact(0)!!.material
        if (material != null) {
            sound = spawnArgs.GetString(
                Str.va(
                    "snd_footstep_%s", Game_local.gameLocal.sufaceTypeNames[(material.GetSurfaceType()).ordinal]
                )
            )
        }
        if (sound.isEmpty()) { // == '\0' ) {
            sound = spawnArgs.GetString("snd_footstep")
        }
        if (!sound.isEmpty()) { // != '\0' ) {
            StartSoundShader(
                DeclManager.declManager.FindSound(sound),
                (gameSoundChannel_t.SND_CHANNEL_BODY).ordinal,
                0,
                false,
                null
            )
        }
    }

    private fun Event_EnableEyeFocus() {
        allowEyeFocus = true
        blink_time =
            (Game_local.gameLocal.time + blink_min + Game_local.gameLocal.random.RandomFloat() * (blink_max - blink_min)).toInt()
    }

    private fun Event_DisableEyeFocus() {
        allowEyeFocus = false
        val headEnt: idEntity? = head.GetEntity()
        if (headEnt != null) {
            headEnt.GetAnimator()!!.Clear(Anim.ANIMCHANNEL_EYELIDS, Game_local.gameLocal.time, Anim.FRAME2MS(2))
        } else {
            animator.Clear(Anim.ANIMCHANNEL_EYELIDS, Game_local.gameLocal.time, Anim.FRAME2MS(2))
        }
    }

    private fun Event_Footstep() {
        PlayFootStepSound()
    }

    private fun Event_EnableWalkIK() {
        walkIK.EnableAll()
    }

    private fun Event_DisableWalkIK() {
        walkIK.DisableAll()
    }

    private fun Event_EnableLegIK(num: idEventArg<Int>) {
        walkIK.EnableLeg(num.value)
    }

    private fun Event_DisableLegIK(num: idEventArg<Int>) {
        walkIK.DisableLeg(num.value)
    }

    private fun Event_SetAnimPrefix(prefix: idEventArg<String?>) {
        animPrefix.set(prefix.value)
    }

    //        private void Event_LookAtEntity(idEntity ent, float duration);
    private fun Event_PreventPain(duration: idEventArg<Float>) {
        painTime = (Game_local.gameLocal.time + SEC2MS(duration.value)).toInt()
    }

    private fun Event_DisablePain() {
        allowPain = false
    }

    private fun Event_EnablePain() {
        allowPain = true
    }

    private fun Event_GetPainAnim() {
        if (0 == painAnim.Length()) {
            idThread.ReturnString("pain")
        } else {
            idThread.ReturnString(painAnim)
        }
    }

    private fun Event_StopAnim(channel: idEventArg<Int>, frames: idEventArg<Int>) {
        when (channel.value) {
            Anim.ANIMCHANNEL_HEAD -> headAnim.StopAnim(frames.value)
            Anim.ANIMCHANNEL_TORSO -> torsoAnim.StopAnim(frames.value)
            Anim.ANIMCHANNEL_LEGS -> legsAnim.StopAnim(frames.value)
            else -> idGameLocal.Error("Unknown anim group")
        }
    }

    private fun Event_PlayAnim(_channel: idEventArg<Int>, _animName: idEventArg<String>) {
        val channel: Int = _channel.value
        var animName = _animName.value
        val flags: animFlags_t?
        val headEnt: idEntity?
        val anim: Int
        anim = GetAnim(channel, animName)
        if (0 == anim) {
            if (channel == Anim.ANIMCHANNEL_HEAD && head.GetEntity() != null) {
                Game_local.gameLocal.DPrintf(
                    "missing '%s' animation on '%s' (%s)\n",
                    animName,
                    name.toString(),
                    spawnArgs.GetString("def_head", "")
                )
            } else {
                Game_local.gameLocal.DPrintf(
                    "missing '%s' animation on '%s' (%s)\n", animName, name.toString(), GetEntityDefName()
                )
            }
            idThread.ReturnInt(0)
            return
        }
        when (channel) {
            Anim.ANIMCHANNEL_HEAD -> {
                headEnt = head.GetEntity()
                if (headEnt != null) {
                    headAnim.idleAnim = false
                    headAnim.PlayAnim(anim)
                    flags = headAnim.GetAnimFlags()
                    if (!flags.prevent_idle_override) {
                        if (torsoAnim.IsIdle()) {
                            torsoAnim.animBlendFrames = headAnim.lastAnimBlendFrames
                            SyncAnimChannels(
                                Anim.ANIMCHANNEL_TORSO, Anim.ANIMCHANNEL_HEAD, headAnim.lastAnimBlendFrames
                            )
                            if (legsAnim.IsIdle()) {
                                legsAnim.animBlendFrames = headAnim.lastAnimBlendFrames
                                SyncAnimChannels(
                                    Anim.ANIMCHANNEL_LEGS, Anim.ANIMCHANNEL_HEAD, headAnim.lastAnimBlendFrames
                                )
                            }
                        }
                    }
                }
            }

            Anim.ANIMCHANNEL_TORSO -> {
                torsoAnim.idleAnim = false
                torsoAnim.PlayAnim(anim)
                flags = torsoAnim.GetAnimFlags()
                if (!flags.prevent_idle_override) {
                    if (headAnim.IsIdle()) {
                        headAnim.animBlendFrames = torsoAnim.lastAnimBlendFrames
                        SyncAnimChannels(
                            Anim.ANIMCHANNEL_HEAD, Anim.ANIMCHANNEL_TORSO, torsoAnim.lastAnimBlendFrames
                        )
                    }
                    if (legsAnim.IsIdle()) {
                        legsAnim.animBlendFrames = torsoAnim.lastAnimBlendFrames
                        SyncAnimChannels(
                            Anim.ANIMCHANNEL_LEGS, Anim.ANIMCHANNEL_TORSO, torsoAnim.lastAnimBlendFrames
                        )
                    }
                }
            }

            Anim.ANIMCHANNEL_LEGS -> {
                legsAnim.idleAnim = false
                legsAnim.PlayAnim(anim)
                flags = legsAnim.GetAnimFlags()
                if (!flags.prevent_idle_override) {
                    if (torsoAnim.IsIdle()) {
                        torsoAnim.animBlendFrames = legsAnim.lastAnimBlendFrames
                        SyncAnimChannels(
                            Anim.ANIMCHANNEL_TORSO, Anim.ANIMCHANNEL_LEGS, legsAnim.lastAnimBlendFrames
                        )
                        if (headAnim.IsIdle()) {
                            headAnim.animBlendFrames = legsAnim.lastAnimBlendFrames
                            SyncAnimChannels(
                                Anim.ANIMCHANNEL_HEAD, Anim.ANIMCHANNEL_LEGS, legsAnim.lastAnimBlendFrames
                            )
                        }
                    }
                }
            }

            else -> idGameLocal.Error("Unknown anim group")
        }
        idThread.ReturnInt(1)
    }

    private fun Event_PlayCycle(_channel: idEventArg<Int>, _animName: idEventArg<String>) {
        val channel: Int = _channel.value
        val animName = _animName.value
        val flags: animFlags_t?
        val anim: Int
        anim = GetAnim(channel, animName)
        if (0 == anim) {
            if (channel == Anim.ANIMCHANNEL_HEAD && head.GetEntity() != null) {
                Game_local.gameLocal.DPrintf(
                    "missing '%s' animation on '%s' (%s)\n", animName, name, spawnArgs.GetString("def_head", "")
                )
            } else {
                Game_local.gameLocal.DPrintf(
                    "missing '%s' animation on '%s' (%s)\n", animName, name, GetEntityDefName()
                )
            }
            idThread.ReturnInt(false)
            return
        }
        when (channel) {
            Anim.ANIMCHANNEL_HEAD -> {
                headAnim.idleAnim = false
                headAnim.CycleAnim(anim)
                flags = headAnim.GetAnimFlags()
                if (!flags.prevent_idle_override) {
                    if (torsoAnim.IsIdle() && legsAnim.IsIdle()) {
                        torsoAnim.animBlendFrames = headAnim.lastAnimBlendFrames
                        SyncAnimChannels(
                            Anim.ANIMCHANNEL_TORSO, Anim.ANIMCHANNEL_HEAD, headAnim.lastAnimBlendFrames
                        )
                        legsAnim.animBlendFrames = headAnim.lastAnimBlendFrames
                        SyncAnimChannels(Anim.ANIMCHANNEL_LEGS, Anim.ANIMCHANNEL_HEAD, headAnim.lastAnimBlendFrames)
                    }
                }
            }

            Anim.ANIMCHANNEL_TORSO -> {
                torsoAnim.idleAnim = false
                torsoAnim.CycleAnim(anim)
                flags = torsoAnim.GetAnimFlags()
                if (!flags.prevent_idle_override) {
                    if (headAnim.IsIdle()) {
                        headAnim.animBlendFrames = torsoAnim.lastAnimBlendFrames
                        SyncAnimChannels(
                            Anim.ANIMCHANNEL_HEAD, Anim.ANIMCHANNEL_TORSO, torsoAnim.lastAnimBlendFrames
                        )
                    }
                    if (legsAnim.IsIdle()) {
                        legsAnim.animBlendFrames = torsoAnim.lastAnimBlendFrames
                        SyncAnimChannels(
                            Anim.ANIMCHANNEL_LEGS, Anim.ANIMCHANNEL_TORSO, torsoAnim.lastAnimBlendFrames
                        )
                    }
                }
            }

            Anim.ANIMCHANNEL_LEGS -> {
                legsAnim.idleAnim = false
                legsAnim.CycleAnim(anim)
                flags = legsAnim.GetAnimFlags()
                if (!flags.prevent_idle_override) {
                    if (torsoAnim.IsIdle()) {
                        torsoAnim.animBlendFrames = legsAnim.lastAnimBlendFrames
                        SyncAnimChannels(
                            Anim.ANIMCHANNEL_TORSO, Anim.ANIMCHANNEL_LEGS, legsAnim.lastAnimBlendFrames
                        )
                        if (headAnim.IsIdle()) {
                            headAnim.animBlendFrames = legsAnim.lastAnimBlendFrames
                            SyncAnimChannels(
                                Anim.ANIMCHANNEL_HEAD, Anim.ANIMCHANNEL_LEGS, legsAnim.lastAnimBlendFrames
                            )
                        }
                    }
                }
            }

            else -> idGameLocal.Error("Unknown anim group")
        }
        idThread.ReturnInt(true)
    }

    private fun Event_IdleAnim(_channel: idEventArg<Int>, _animName: idEventArg<String>) {
        val channel: Int = _channel.value
        val animName = _animName.value
        val anim: Int
        anim = GetAnim(channel, animName)
        if (0 == anim) {
            if (channel == Anim.ANIMCHANNEL_HEAD && head.GetEntity() != null) {
                Game_local.gameLocal.DPrintf(
                    "missing '%s' animation on '%s' (%s)\n", animName, name, spawnArgs.GetString("def_head", "")
                )
            } else {
                Game_local.gameLocal.DPrintf(
                    "missing '%s' animation on '%s' (%s)\n", animName, name, GetEntityDefName()
                )
            }
            when (channel) {
                Anim.ANIMCHANNEL_HEAD -> headAnim.BecomeIdle()
                Anim.ANIMCHANNEL_TORSO -> torsoAnim.BecomeIdle()
                Anim.ANIMCHANNEL_LEGS -> legsAnim.BecomeIdle()
                else -> idGameLocal.Error("Unknown anim group")
            }
            idThread.ReturnInt(false)
            return
        }
        when (channel) {
            Anim.ANIMCHANNEL_HEAD -> {
                headAnim.BecomeIdle()
                if (torsoAnim.GetAnimFlags().prevent_idle_override) {
                    // don't sync to torso body if it doesn't override idle anims
                    headAnim.CycleAnim(anim)
                } else if (torsoAnim.IsIdle() && legsAnim.IsIdle()) {
                    // everything is idle, so play the anim on the head and copy it to the torso and legs
                    headAnim.CycleAnim(anim)
                    torsoAnim.animBlendFrames = headAnim.lastAnimBlendFrames
                    SyncAnimChannels(Anim.ANIMCHANNEL_TORSO, Anim.ANIMCHANNEL_HEAD, headAnim.lastAnimBlendFrames)
                    legsAnim.animBlendFrames = headAnim.lastAnimBlendFrames
                    SyncAnimChannels(Anim.ANIMCHANNEL_LEGS, Anim.ANIMCHANNEL_HEAD, headAnim.lastAnimBlendFrames)
                } else if (torsoAnim.IsIdle()) {
                    // sync the head and torso to the legs
                    SyncAnimChannels(Anim.ANIMCHANNEL_HEAD, Anim.ANIMCHANNEL_LEGS, headAnim.animBlendFrames)
                    torsoAnim.animBlendFrames = headAnim.lastAnimBlendFrames
                    SyncAnimChannels(Anim.ANIMCHANNEL_TORSO, Anim.ANIMCHANNEL_LEGS, torsoAnim.animBlendFrames)
                } else {
                    // sync the head to the torso
                    SyncAnimChannels(Anim.ANIMCHANNEL_HEAD, Anim.ANIMCHANNEL_TORSO, headAnim.animBlendFrames)
                }
            }

            Anim.ANIMCHANNEL_TORSO -> {
                torsoAnim.BecomeIdle()
                if (legsAnim.GetAnimFlags().prevent_idle_override) {
                    // don't sync to legs if legs anim doesn't override idle anims
                    torsoAnim.CycleAnim(anim)
                } else if (legsAnim.IsIdle()) {
                    // play the anim in both legs and torso
                    torsoAnim.CycleAnim(anim)
                    legsAnim.animBlendFrames = torsoAnim.lastAnimBlendFrames
                    SyncAnimChannels(Anim.ANIMCHANNEL_LEGS, Anim.ANIMCHANNEL_TORSO, torsoAnim.lastAnimBlendFrames)
                } else {
                    // sync the anim to the legs
                    SyncAnimChannels(Anim.ANIMCHANNEL_TORSO, Anim.ANIMCHANNEL_LEGS, torsoAnim.animBlendFrames)
                }
                if (headAnim.IsIdle()) {
                    SyncAnimChannels(Anim.ANIMCHANNEL_HEAD, Anim.ANIMCHANNEL_TORSO, torsoAnim.lastAnimBlendFrames)
                }
            }

            Anim.ANIMCHANNEL_LEGS -> {
                legsAnim.BecomeIdle()
                if (torsoAnim.GetAnimFlags().prevent_idle_override) {
                    // don't sync to torso if torso anim doesn't override idle anims
                    legsAnim.CycleAnim(anim)
                } else if (torsoAnim.IsIdle()) {
                    // play the anim in both legs and torso
                    legsAnim.CycleAnim(anim)
                    torsoAnim.animBlendFrames = legsAnim.lastAnimBlendFrames
                    SyncAnimChannels(Anim.ANIMCHANNEL_TORSO, Anim.ANIMCHANNEL_LEGS, legsAnim.lastAnimBlendFrames)
                    if (headAnim.IsIdle()) {
                        SyncAnimChannels(Anim.ANIMCHANNEL_HEAD, Anim.ANIMCHANNEL_LEGS, legsAnim.lastAnimBlendFrames)
                    }
                } else {
                    // sync the anim to the torso
                    SyncAnimChannels(Anim.ANIMCHANNEL_LEGS, Anim.ANIMCHANNEL_TORSO, legsAnim.animBlendFrames)
                }
            }

            else -> idGameLocal.Error("Unknown anim group")
        }
        idThread.ReturnInt(true)
    }

    private fun Event_SetSyncedAnimWeight(
        _channel: idEventArg<Int>, _anim: idEventArg<Int>, _weight: idEventArg<Float>
    ) {
        val channel: Int = _channel.value
        val anim: Int = _anim.value
        val weight: Float = _weight.value
        val headEnt: idEntity?
        headEnt = head.GetEntity()
        when (channel) {
            Anim.ANIMCHANNEL_HEAD -> {
                if (headEnt != null) {
                    animator.CurrentAnim(Anim.ANIMCHANNEL_ALL).SetSyncedAnimWeight(anim, weight)
                } else {
                    animator.CurrentAnim(Anim.ANIMCHANNEL_HEAD).SetSyncedAnimWeight(anim, weight)
                }
                if (torsoAnim.IsIdle()) {
                    animator.CurrentAnim(Anim.ANIMCHANNEL_TORSO).SetSyncedAnimWeight(anim, weight)
                    if (legsAnim.IsIdle()) {
                        animator.CurrentAnim(Anim.ANIMCHANNEL_LEGS).SetSyncedAnimWeight(anim, weight)
                    }
                }
            }

            Anim.ANIMCHANNEL_TORSO -> {
                animator.CurrentAnim(Anim.ANIMCHANNEL_TORSO).SetSyncedAnimWeight(anim, weight)
                if (legsAnim.IsIdle()) {
                    animator.CurrentAnim(Anim.ANIMCHANNEL_LEGS).SetSyncedAnimWeight(anim, weight)
                }
                if (headEnt != null && headAnim.IsIdle()) {
                    animator.CurrentAnim(Anim.ANIMCHANNEL_ALL).SetSyncedAnimWeight(anim, weight)
                }
            }

            Anim.ANIMCHANNEL_LEGS -> {
                animator.CurrentAnim(Anim.ANIMCHANNEL_LEGS).SetSyncedAnimWeight(anim, weight)
                if (torsoAnim.IsIdle()) {
                    animator.CurrentAnim(Anim.ANIMCHANNEL_TORSO).SetSyncedAnimWeight(anim, weight)
                    if (headEnt != null && headAnim.IsIdle()) {
                        animator.CurrentAnim(Anim.ANIMCHANNEL_ALL).SetSyncedAnimWeight(anim, weight)
                    }
                }
            }

            else -> idGameLocal.Error("Unknown anim group")
        }
    }

    private fun Event_OverrideAnim(channel: idEventArg<Int>) {
        when (channel.value) {
            Anim.ANIMCHANNEL_HEAD -> {
                headAnim.Disable()
                if (!torsoAnim.IsIdle()) {
                    SyncAnimChannels(Anim.ANIMCHANNEL_HEAD, Anim.ANIMCHANNEL_TORSO, torsoAnim.lastAnimBlendFrames)
                } else {
                    SyncAnimChannels(Anim.ANIMCHANNEL_HEAD, Anim.ANIMCHANNEL_LEGS, legsAnim.lastAnimBlendFrames)
                }
            }

            Anim.ANIMCHANNEL_TORSO -> {
                torsoAnim.Disable()
                SyncAnimChannels(Anim.ANIMCHANNEL_TORSO, Anim.ANIMCHANNEL_LEGS, legsAnim.lastAnimBlendFrames)
                if (headAnim.IsIdle()) {
                    SyncAnimChannels(Anim.ANIMCHANNEL_HEAD, Anim.ANIMCHANNEL_TORSO, torsoAnim.lastAnimBlendFrames)
                }
            }

            Anim.ANIMCHANNEL_LEGS -> {
                legsAnim.Disable()
                SyncAnimChannels(Anim.ANIMCHANNEL_LEGS, Anim.ANIMCHANNEL_TORSO, torsoAnim.lastAnimBlendFrames)
            }

            else -> idGameLocal.Error("Unknown anim group")
        }
    }

    private fun Event_EnableAnim(channel: idEventArg<Int>, _blendFrames: idEventArg<Int>) {
        val blendFrames: Int = _blendFrames.value
        when (channel.value) {
            Anim.ANIMCHANNEL_HEAD -> headAnim.Enable(blendFrames)
            Anim.ANIMCHANNEL_TORSO -> torsoAnim.Enable(blendFrames)
            Anim.ANIMCHANNEL_LEGS -> legsAnim.Enable(blendFrames)
            else -> idGameLocal.Error("Unknown anim group")
        }
    }

    private fun Event_SetBlendFrames(_channel: idEventArg<Int>, _blendFrames: idEventArg<Int>) {
        val channel: Int = _channel.value
        val blendFrames: Int = _blendFrames.value
        when (channel) {
            Anim.ANIMCHANNEL_HEAD -> {
                headAnim.animBlendFrames = blendFrames
                headAnim.lastAnimBlendFrames = blendFrames
            }

            Anim.ANIMCHANNEL_TORSO -> {
                torsoAnim.animBlendFrames = blendFrames
                torsoAnim.lastAnimBlendFrames = blendFrames
            }

            Anim.ANIMCHANNEL_LEGS -> {
                legsAnim.animBlendFrames = blendFrames
                legsAnim.lastAnimBlendFrames = blendFrames
            }

            else -> idGameLocal.Error("Unknown anim group")
        }
    }

    private fun Event_GetBlendFrames(_channel: idEventArg<Int>) {
        val channel: Int = _channel.value
        when (channel) {
            Anim.ANIMCHANNEL_HEAD -> idThread.ReturnInt(headAnim.animBlendFrames)
            Anim.ANIMCHANNEL_TORSO -> idThread.ReturnInt(torsoAnim.animBlendFrames)
            Anim.ANIMCHANNEL_LEGS -> idThread.ReturnInt(legsAnim.animBlendFrames)
            else -> idGameLocal.Error("Unknown anim group")
        }
    }

    private fun Event_AnimState(
        channel: idEventArg<Int>, statename: idEventArg<String?>, blendFrames: idEventArg<Int>
    ) {
        SetAnimState(channel.value, statename.value, blendFrames.value)
    }

    private fun Event_GetAnimState(channel: idEventArg<Int>) {
        val state: idStr?
        state = GetAnimState(channel.value)
        idThread.ReturnString(state!!)
    }

    private fun Event_InAnimState(channel: idEventArg<Int>, statename: idEventArg<String?>) {
        val instate: Boolean
        instate = InAnimState(channel.value, statename.value)
        idThread.ReturnInt(instate)
    }

    private fun Event_FinishAction(actionname: idEventArg<String?>) {
        if (waitState.toString() == actionname.value) {
            SetWaitState("")
        }
    }

    private fun Event_AnimDone(channel: idEventArg<Int>, _blendFrames: idEventArg<Int>) {
        val blendFrames: Int = _blendFrames.value
        val result: Boolean
        when (channel.value) {
            Anim.ANIMCHANNEL_HEAD -> {
                result = headAnim.AnimDone(blendFrames)
                idThread.ReturnInt(result)
            }

            Anim.ANIMCHANNEL_TORSO -> {
                result = torsoAnim.AnimDone(blendFrames)
                idThread.ReturnInt(result)
            }

            Anim.ANIMCHANNEL_LEGS -> {
                result = legsAnim.AnimDone(blendFrames)
                idThread.ReturnInt(result)
            }

            else -> idGameLocal.Error("Unknown anim group")
        }
    }

    private fun Event_HasAnim(channel: idEventArg<Int>, animName: idEventArg<String>) {
        if (GetAnim(channel.value, animName.value) != 0) {
            idThread.ReturnFloat(1.0f)
        } else {
            idThread.ReturnFloat(0.0f)
        }
    }

    private fun Event_CheckAnim(channel: idEventArg<Int>, animname: idEventArg<String>) {
        if (0 == GetAnim(channel.value, animname.value)) {
            if (animPrefix.Length() != 0) {
                idGameLocal.Error("Can't find anim '%s_%s' for '%s'", animPrefix, animname, name)
            } else {
                idGameLocal.Error("Can't find anim '%s' for '%s'", animname, name)
            }
        }
    }

    private fun Event_ChooseAnim(channel: idEventArg<Int>, animname: idEventArg<String>) {
        val anim: Int
        anim = GetAnim(channel.value, animname.value)
        if (anim != 0) {
            if (channel.value == Anim.ANIMCHANNEL_HEAD) {
                if (head.GetEntity() != null) {
                    idThread.ReturnString(head.GetEntity()!!.GetAnimator().AnimFullName(anim))
                    return
                }
            } else {
                idThread.ReturnString(animator.AnimFullName(anim))
                return
            }
        }
        idThread.ReturnString("")
    }

    private fun Event_AnimLength(channel: idEventArg<Int>, animname: idEventArg<String>) {
        val anim: Int
        anim = GetAnim(channel.value, animname.value)
        if (anim != 0) {
            if (channel.value == Anim.ANIMCHANNEL_HEAD) {
                if (head.GetEntity() != null) {
                    idThread.ReturnFloat(
                        MS2SEC(
                            head.GetEntity()!!.GetAnimator().AnimLength(anim).toFloat()
                        )
                    )
                    return
                }
            } else {
                idThread.ReturnFloat(MS2SEC(animator.AnimLength(anim).toFloat()))
                return
            }
        }
        idThread.ReturnFloat(0.0f)
    }

    private fun Event_AnimDistance(channel: idEventArg<Int>, animname: idEventArg<String>) {
        val anim: Int
        anim = GetAnim(channel.value, animname.value)
        if (anim != 0) {
            if (channel.value == Anim.ANIMCHANNEL_HEAD) {
                if (head.GetEntity() != null) {
                    idThread.ReturnFloat(head.GetEntity()!!.GetAnimator().TotalMovementDelta(anim).Length())
                    return
                }
            } else {
                idThread.ReturnFloat(animator.TotalMovementDelta(anim).Length())
                return
            }
        }
        idThread.ReturnFloat(0.0f)
    }

    private fun Event_HasEnemies() {
        val hasEnemy: Boolean
        hasEnemy = HasEnemies()
        idThread.ReturnInt(hasEnemy)
    }

    private fun Event_NextEnemy(_ent: idEventArg<idEntity?>) {
        val ent = _ent.value
        var actor: idActor?
        if (null == ent || ent == this) {
            actor = enemyList.Next()
        } else {
            if (ent !is idActor) {
                idGameLocal.Error("'%s' cannot be an enemy", ent.name)
            }
            actor = ent as idActor
            if (actor.enemyNode.ListHead() !== enemyList) {
                idGameLocal.Error("'%s' is not in '%s' enemy list", actor.name, name)
            }
        }
        while (actor != null) {
            if (!actor.fl.hidden) {
                idThread.ReturnEntity(actor)
                return
            }
            actor = actor.enemyNode.Next()
        }
        idThread.ReturnEntity(null)
    }

    private fun Event_ClosestEnemyToPoint(pos: idEventArg<idVec3>) {
        val bestEnt = ClosestEnemyToPoint(pos.value)
        idThread.ReturnEntity(bestEnt)
    }

    private fun Event_StopSound(channel: idEventArg<Int>, netSync: idEventArg<Int>) {
        if (channel.value == (gameSoundChannel_t.SND_CHANNEL_VOICE).ordinal) {
            val headEnt: idEntity? = head.GetEntity()
            headEnt?.StopSound(channel.value, netSync.value != 0)
        }
        StopSound(channel.value, netSync.value != 0)
    }

    private fun Event_SetNextState(name: idEventArg<String?>) {
        idealState = GetScriptFunction(name.value)
        if (idealState == state) {
            state = null
        }
    }

    private fun Event_SetState(name: idEventArg<String?>) {
        idealState = GetScriptFunction(name.value)
        if (idealState == state) {
            state = null
        }
        scriptThread!!.DoneProcessing()
    }

    private fun Event_GetState() {
        if (state != null) {
            idThread.ReturnString(state!!.Name())
        } else {
            idThread.ReturnString("")
        }
    }

    private fun Event_GetHead() {
        idThread.ReturnEntity(head.GetEntity())
    }

    // D3XP event handlers
    private fun Event_SetDamageGroupScale(groupName: idEventArg<String>, scale: idEventArg<Float>) {
        val name = groupName.value
        val s = scale.value
        for (i in 0 until damageScale.Num()) {
            if (damageGroups[i].toString() == name) {
                damageScale[i] = s
            }
        }
    }

    private fun Event_SetDamageGroupScaleAll(scale: idEventArg<Float>) {
        val s = scale.value as Float
        var i = 0
        while (i < damageScale.Num()) {
            damageScale[i] = s
            i++
        }
    }

    private fun Event_GetDamageGroupScale(groupName: idEventArg<String>) {
        val name = groupName.value as String
        var i = 0
        while (i < damageScale.Num()) {
            if (damageGroups[i].toString() == name) {
                idThread.ReturnFloat(damageScale[i])
                return
            }
            i++
        }
        idThread.ReturnFloat(0f)
    }

    private fun Event_SetDamageCap(cap: idEventArg<Float>) {
        damageCap = cap.value.toInt()
    }

    private fun Event_SetWaitState(state: idEventArg<String>) {
        SetWaitState(state.value)
    }

    private fun Event_GetWaitState() {
        val ws = WaitState()
        idThread.ReturnString(ws ?: "")
    }

    override fun GetType(): idTypeInfo = Type
    override fun CreateInstance(): idClass = idActor()

    override fun getEventCallBack(event: idEventDef): eventCallback_t<*>? {
        return eventCallbacks[event]
    }

    override fun _deconstructor() {
        var i: Int
        var ent: idEntity?
        DeconstructScriptObject()
        scriptObject.Free()
        StopSound(gameSoundChannel_t.SND_CHANNEL_ANY.ordinal, false)
        idClipModel.delete(combatModel)
        combatModel = null
        if (head.GetEntity() != null) {
            head.GetEntity()!!.ClearBody()
            head.GetEntity()!!.PostEventMS(EV_Remove, 0)
        }

        // remove any attached entities
        i = 0
        while (i < attachments.Num()) {
            ent = attachments[i].ent.GetEntity()
            ent?.PostEventMS(EV_Remove, 0)
            i++
        }
        ShutdownThreads()
        super._deconstructor()
    }

    //
    //
    init {
        viewAxis =
            idMat3() // FIX: Was getMat3_identity() which may return shared static singleton — using viewAxis.set() later would corrupt the shared identity matrix (reference aliasing bug)
        viewAxis.Identity()
        scriptThread = null // initialized by ConstructScriptObject, which is called by idEntity::Spawn
        use_combat_bbox = false
        head = idEntityPtr()
        team = 0
        rank = 0
        fovDot = 0.0f
        eyeOffset = idVec3()
        pain_debounce_time = 0
        pain_delay = 0
        pain_threshold = 0
        copyJoints = List.idList()
        state = null
        idealState = null
        leftEyeJoint = Model.INVALID_JOINT
        rightEyeJoint = Model.INVALID_JOINT
        soundJoint = Model.INVALID_JOINT
        modelOffset = idVec3()
        deltaViewAngles = idAngles()
        painTime = 0
        allowPain = false
        allowEyeFocus = false
        waitState = idStr("")
        headAnim = idAnimState()
        torsoAnim = idAnimState()
        legsAnim = idAnimState()
        walkIK = idIK_Walk()
        animPrefix = idStr()
        painAnim = idStr()

        blink_anim = 0 //null;
        blink_time = 0
        blink_min = 0
        blink_max = 0

        finalBoss = false

        enemyNode = idLinkList(this)
        enemyList = idLinkList(this)
    }
}

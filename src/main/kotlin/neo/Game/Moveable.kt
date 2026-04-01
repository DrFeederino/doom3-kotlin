/*
 * Copyright (C) 1999-2011 id Software LLC, a ZeniMax Media company.
 * Translated to Kotlin by Dr. Feederino with support of Claude Code
 *
 * This file is part of the Doom 3 Kotlin project.
 * Original source: neo/game/Moveable.h, neo/game/Moveable.cpp
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

import neo.Game.AI.idAI
import neo.Game.Animation.idDeclModelDef
import neo.Game.GameSys.Class.*
import neo.Game.GameSys.EV_Remove
import neo.Game.GameSys.Event.idEventDef
import neo.Game.GameSys.SaveGame.idRestoreGame
import neo.Game.GameSys.SaveGame.idSaveGame
import neo.Game.GameSys.SysCvar
import neo.Game.Game_local.Companion.isD3XP
import neo.Game.Game_local.gameSoundChannel_t
import neo.Game.Game_local.idGameLocal
import neo.Game.Physics.Clip.idClipModel
import neo.Game.Physics.Physics_RigidBody.idPhysics_RigidBody
import neo.Game.Player.idPlayer
import neo.Game.Projectile.idDebris
import neo.Game.Script.Script_Thread.idThread
import neo.Renderer.Material
import neo.Renderer.Model
import neo.Renderer.ModelManager
import neo.Renderer.RenderWorld
import neo.Renderer.RenderWorld.renderEntity_s
import neo.Renderer.RenderWorld.renderLight_s
import neo.TempDump
import neo.cm.CM_CLIP_EPSILON
import neo.cm.collisionModelManager
import neo.cm.trace_s
import neo.framework.DeclManager
import neo.framework.DeclManager.declType_t
import neo.framework.UsercmdGen
import neo.idlib.BitMsg.idBitMsg
import neo.idlib.BitMsg.idBitMsgDelta
import neo.idlib.Text.Str.idStr
import neo.idlib.containers.CFloat
import neo.idlib.containers.CInt
import neo.idlib.geometry.TraceModel.idTraceModel
import neo.idlib.math.*
import neo.idlib.math.Matrix.idMat3
import java.nio.ByteBuffer

val EV_BecomeNonSolid: idEventDef = idEventDef("becomeNonSolid")
val EV_EnableDamage: idEventDef = idEventDef("enableDamage", "f")
val EV_IsAtRest: idEventDef = idEventDef("isAtRest", null, 'd')

val EV_Respawn: idEventDef = idEventDef("<respawn>")

//
val EV_SetOwnerFromSpawnArgs: idEventDef = idEventDef("<setOwnerFromSpawnArgs>")
val EV_TriggerTargets: idEventDef = idEventDef("<triggertargets>")

object Moveable {
    const val BOUNCE_SOUND_MAX_VELOCITY = 200.0f

    //
    const val BOUNCE_SOUND_MIN_VELOCITY = 80.0f

    /*
     ===============================================================================

     Entity using rigid body physics.

     ===============================================================================
     */
    /*
     ===============================================================================

     idMoveable

     ===============================================================================
     */

    /*
     ===============================================================================

     A barrel using rigid body physics and special handling of the view model
     orientation to make it look like it rolls instead of slides. The barrel
     can burn and explode when damaged.

     ===============================================================================
     */

    open class idMoveable : idEntity() {
        companion object {
            val Type = idTypeInfo("idMoveable", "idEntity") { idMoveable() }

            // CLASS_PROTOTYPE( idMoveable );
            private val eventCallbacks: MutableMap<idEventDef, eventCallback_t<*>> = HashMap()

            // ~idMoveable( void );
            fun getEventCallBacks(): MutableMap<idEventDef, eventCallback_t<*>> {
                return eventCallbacks
            }

            init {
                eventCallbacks.putAll(idEntity.getEventCallBacks())
                eventCallbacks[EV_Activate] =
                    eventCallback_t1<idMoveable> { obj: idMoveable, activator: idEventArg<*>? ->
                        obj.Event_Activate(activator as idEventArg<idEntity>)
                    }
                eventCallbacks[EV_BecomeNonSolid] =
                    eventCallback_t0<idMoveable> { obj: idMoveable -> obj.Event_BecomeNonSolid() }
                eventCallbacks[EV_SetOwnerFromSpawnArgs] =
                    eventCallback_t0<idMoveable> { obj: idMoveable -> obj.Event_SetOwnerFromSpawnArgs() }
                eventCallbacks[EV_IsAtRest] =
                    eventCallback_t0<idMoveable> { obj: idMoveable -> obj.Event_IsAtRest() }
                eventCallbacks[EV_EnableDamage] =
                    eventCallback_t1<idMoveable> { obj: idMoveable, enable: idEventArg<*>? ->
                        obj.Event_EnableDamage(enable as idEventArg<Float>)
                    }
            }
        }

        protected val initialSplineDir // initial relative direction along the spline path
                : idVec3
        protected var allowStep // allow monsters to step on the object
                : Boolean
        protected var brokenModel // model set when health drops down to or below zero
                : idStr = idStr()
        protected var canDamage // only apply damage when this is set
                : Boolean
        protected var damage // if > 0 apply damage to hit entities
                : idStr = idStr()
        protected var explode // entity explodes when health drops down to or below zero
                : Boolean
        protected var fxCollide // fx system to start when collides with something
                : idStr = idStr()
        protected var initialSpline // initial spline path the moveable follows
                : idCurve_Spline<idVec3>?
        protected var maxDamageVelocity // velocity at which the maximum damage is applied
                : Float
        protected var minDamageVelocity // minimum velocity before moveable applies damage
                : Float
        protected var nextCollideFxTime // next time it is ok to spawn collision fx
                : Int
        protected var nextDamageTime // next time the movable can hurt the player
                : Int
        protected var nextSoundTime // next time the moveable can make a sound
                : Int
        protected var physicsObj // physics object
                : idPhysics_RigidBody
        protected var unbindOnDeath // unbind from master when health drops down to or below zero
                : Boolean

        // D3XP
        protected var monsterDamage: idStr = idStr()
        protected var attacker: idEntity? = null

        override fun Spawn() {
            super.Spawn()
            val trm = idTraceModel()
            val density = CFloat()
            val friction = CFloat()
            val bouncyness = CFloat()
            val mass = CFloat()
            val clipShrink: Int
            val clipModelName = idStr()

            // check if a clip model is set
            spawnArgs.GetString("clipmodel", "", clipModelName)
            if (clipModelName.IsEmpty()) {
                clipModelName.set(spawnArgs.GetString("model")) // use the visual model
            }
            if (!collisionModelManager.TrmFromModel(clipModelName, trm)) {
                idGameLocal.Error("idMoveable '%s': cannot load collision model %s", name, clipModelName)
                return
            }

            // if the model should be shrinked
            clipShrink = spawnArgs.GetInt("clipshrink")
            if (clipShrink != 0) {
                trm.Shrink(clipShrink * CM_CLIP_EPSILON)
            }

            // get rigid body properties
            spawnArgs.GetFloat("density", "0.5", density)
            density._val = (idMath.ClampFloat(0.001f, 1000.0f, density._val))
            spawnArgs.GetFloat("friction", "0.05", friction)
            friction._val = (idMath.ClampFloat(0.0f, 1.0f, friction._val))
            spawnArgs.GetFloat("bouncyness", "0.6", bouncyness)
            bouncyness._val = (idMath.ClampFloat(0.0f, 1.0f, bouncyness._val))
            explode = spawnArgs.GetBool("explode")
            unbindOnDeath = spawnArgs.GetBool("unbindondeath")
            fxCollide = idStr(spawnArgs.GetString("fx_collide"))
            nextCollideFxTime = 0
            fl.takedamage = true
            damage = idStr(spawnArgs.GetString("def_damage", "")!!)
            canDamage = !spawnArgs.GetBool("damageWhenActive")
            if (isD3XP) {
                monsterDamage = idStr(spawnArgs.GetString("monster_damage", "")!!)
                fl.networkSync = true
                attacker = null
            }
            minDamageVelocity = spawnArgs.GetFloat("minDamageVelocity", "100")
            maxDamageVelocity = spawnArgs.GetFloat("maxDamageVelocity", "200")
            nextDamageTime = 0
            nextSoundTime = 0
            health = spawnArgs.GetInt("health", "0")
            spawnArgs.GetString("broken", "", brokenModel)
            if (health != 0) {
                if (!brokenModel.IsEmpty() && ModelManager.renderModelManager.CheckModel(brokenModel.toString()) == null) {
                    idGameLocal.Error(
                        "idMoveable '%s' at (%s): cannot load broken model '%s'",
                        name,
                        GetPhysics().GetOrigin().ToString(0),
                        brokenModel
                    )
                }
            }

            // setup the physics
            physicsObj.SetSelf(this)
            physicsObj.SetClipModel(idClipModel(trm), density._val)
            // FIX: C++ SetMaterial accepts null; Kotlin requires non-null. Guard with null check.
            val clipMaterial = GetRenderModelMaterial()
            if (clipMaterial != null) {
                physicsObj.GetClipModel()!!.SetMaterial(clipMaterial)
            }
            physicsObj.SetOrigin(GetPhysics().GetOrigin())
            physicsObj.SetAxis(GetPhysics().GetAxis())
            physicsObj.SetBouncyness(bouncyness._val)
            physicsObj.SetFriction(0.6f, 0.6f, friction._val)
            physicsObj.SetGravity(Game_local.gameLocal.GetGravity())
            physicsObj.SetContents(Material.CONTENTS_SOLID)
            physicsObj.SetClipMask(Game_local.MASK_SOLID or Material.CONTENTS_BODY or Material.CONTENTS_CORPSE or Material.CONTENTS_MOVEABLECLIP)
            SetPhysics(physicsObj)
            if (spawnArgs.GetFloat("mass", "10", mass)) {
                physicsObj.SetMass(mass._val)
            }
            if (spawnArgs.GetBool("nodrop")) {
                physicsObj.PutToRest()
            } else {
                physicsObj.DropToFloor()
            }
            if (spawnArgs.GetBool("noimpact") || spawnArgs.GetBool("notPushable")) {
                physicsObj.DisableImpact()
            }
            if (spawnArgs.GetBool("nonsolid")) {
                BecomeNonSolid()
            }
            allowStep = spawnArgs.GetBool("allowStep", "1")
            PostEventMS(EV_SetOwnerFromSpawnArgs, 0)
        }

        override fun Save(savefile: idSaveGame) {
            super.Save(savefile)
            savefile.WriteString(brokenModel)
            savefile.WriteString(damage)
            savefile.WriteString(fxCollide)
            savefile.WriteInt(nextCollideFxTime)
            savefile.WriteFloat(minDamageVelocity)
            savefile.WriteFloat(maxDamageVelocity)
            savefile.WriteBool(explode)
            savefile.WriteBool(unbindOnDeath)
            savefile.WriteBool(allowStep)
            savefile.WriteBool(canDamage)
            savefile.WriteInt(nextDamageTime)
            savefile.WriteInt(nextSoundTime)
            savefile.WriteInt((if (initialSpline != null) initialSpline!!.GetTime(0) else -1).toInt())
            savefile.WriteVec3(initialSplineDir)
            savefile.WriteStaticObject(physicsObj)
            if (isD3XP) {
                savefile.WriteString(monsterDamage)
                savefile.WriteObject(attacker)
            }
        }

        override fun Restore(savefile: idRestoreGame) {
            super.Restore(savefile)
            val initialSplineTime = CInt()
            savefile.ReadString(brokenModel)
            savefile.ReadString(damage)
            savefile.ReadString(fxCollide)
            nextCollideFxTime = savefile.ReadInt()
            minDamageVelocity = savefile.ReadFloat()
            maxDamageVelocity = savefile.ReadFloat()
            explode = savefile.ReadBool()
            unbindOnDeath = savefile.ReadBool()
            allowStep = savefile.ReadBool()
            canDamage = savefile.ReadBool()
            nextDamageTime = savefile.ReadInt()
            nextSoundTime = savefile.ReadInt()
            savefile.ReadInt(initialSplineTime)
            savefile.ReadVec3(initialSplineDir)
            if (initialSplineTime._val != -1) {
                InitInitialSpline(initialSplineTime._val)
            } else {
                initialSpline = null
            }
            savefile.ReadStaticObject(physicsObj)
            RestorePhysics(physicsObj)
            if (isD3XP) {
                savefile.ReadString(monsterDamage)
                attacker = savefile.ReadObject() as? idEntity
            }
        }

        override fun Think() {
            if ((thinkFlags and TH_THINK) != 0) {
                if (!FollowInitialSplinePath()) {
                    BecomeInactive(TH_THINK)
                }
            }
            super.Think()
        }

        override fun Hide() {
            super.Hide()
            physicsObj.SetContents(0)
        }

        override fun Show() {
            super.Show()
            if (!spawnArgs.GetBool("nonsolid")) {
                physicsObj.SetContents(Material.CONTENTS_SOLID)
            }
        }

        fun AllowStep(): Boolean {
            return allowStep
        }

        fun EnableDamage(enable: Boolean, duration: Float) {
            if (isD3XP && canDamage == enable) {
                return
            }
            canDamage = enable
            if (duration != 0.0f) {
                PostEventSec(EV_EnableDamage, duration, if (!enable) 0.0f else 1.0f)
            }
        }

        override fun Collide(collision: trace_s, velocity: idVec3): Boolean {
            val v: Float
            var f: Float
            val dir = idVec3()
            val ent: idEntity?
            v = -velocity.times(collision.c.normal)
            if (v > BOUNCE_SOUND_MIN_VELOCITY && Game_local.gameLocal.time > nextSoundTime) {
                f =
                    if (v > BOUNCE_SOUND_MAX_VELOCITY) 1.0f else idMath.Sqrt(v - BOUNCE_SOUND_MIN_VELOCITY) * (1.0f / idMath.Sqrt(
                        BOUNCE_SOUND_MAX_VELOCITY - BOUNCE_SOUND_MIN_VELOCITY
                    ))
                if (StartSound("snd_bounce", gameSoundChannel_t.SND_CHANNEL_ANY, 0, false)) {
                    // don't set the volume unless there is a bounce sound as it overrides the entire channel
                    // which causes footsteps on ai's to not honor their shader parms
                    SetSoundVolume(f)
                }
                nextSoundTime = Game_local.gameLocal.time + 500
            }

            // D3XP :: changes relating to the addition of monsterDamage
            if (!Game_local.gameLocal.isClient && canDamage && Game_local.gameLocal.time > nextDamageTime) {
                val hasDamage = damage.Length() > 0
                val hasMonsterDamage = if (isD3XP) monsterDamage.Length() > 0 else false

                if (hasDamage || hasMonsterDamage) {
                    ent = Game_local.gameLocal.entities[collision.c.entityNum]
                    if (ent != null && v > minDamageVelocity) {
                        f =
                            if (v > maxDamageVelocity) 1.0f else idMath.Sqrt(v - minDamageVelocity) * (1.0f / idMath.Sqrt(
                                maxDamageVelocity - minDamageVelocity
                            ))
                        dir.set(velocity)
                        dir.NormalizeFast()
                        if (ent.IsType(idAI.Type) && hasMonsterDamage) {
                            if (isD3XP && attacker != null) {
                                ent.Damage(this, attacker, dir, monsterDamage.toString(), f, Model.INVALID_JOINT)
                            } else {
                                ent.Damage(
                                    this,
                                    GetPhysics().GetClipModel()!!.GetOwner(),
                                    dir,
                                    monsterDamage.toString(),
                                    f,
                                    Model.INVALID_JOINT
                                )
                            }
                        } else if (hasDamage) {
                            if (isD3XP) {
                                // in multiplayer, scale damage wrt mass of object
                                if (Game_local.gameLocal.isMultiplayer) {
                                    f *= GetPhysics().GetMass() * SysCvar.g_moveableDamageScale.GetFloat()
                                }
                                if (attacker != null) {
                                    ent.Damage(this, attacker, dir, damage.toString(), f, Model.INVALID_JOINT)
                                } else {
                                    ent.Damage(
                                        this,
                                        GetPhysics().GetClipModel()!!.GetOwner(),
                                        dir,
                                        damage.toString(),
                                        f,
                                        Model.INVALID_JOINT
                                    )
                                }
                            } else {
                                ent.Damage(
                                    this,
                                    GetPhysics().GetClipModel()!!.GetOwner(),
                                    dir,
                                    damage.toString(),
                                    f,
                                    Model.INVALID_JOINT
                                )
                            }
                        }
                        nextDamageTime = Game_local.gameLocal.time + 1000
                    }
                }
            }

            // D3XP: unstable exploding barrel check
            if (isD3XP && this.IsType(idExplodingBarrel.Type)) {
                val ebarrel = this as idExplodingBarrel
                if (!ebarrel.IsStable()) {
                    PostEventSec(EV_Explode, 0.04f)
                }
            }

            if (fxCollide.Length() != 0 && Game_local.gameLocal.time > nextCollideFxTime) {
                idEntityFx.StartFx(fxCollide, collision.c.point, null, this, false)
                nextCollideFxTime = Game_local.gameLocal.time + 3500
            }
            return false
        }

        override fun Killed(inflictor: idEntity?, attacker: idEntity?, damage: Int, dir: idVec3, location: Int) {
            if (unbindOnDeath) {
                Unbind()
            }
            if (!brokenModel.IsEmpty()) {
                SetModel(brokenModel.toString())
            }
            if (explode) {
                if (brokenModel.IsEmpty()) {
                    PostEventMS(EV_Remove, 1000)
                }
            }
            if (renderEntity!!.gui[0] != null) {
                renderEntity!!.gui[0] = null
            }
            ActivateTargets(this)
            fl.takedamage = false
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

        protected fun GetRenderModelMaterial(): Material.idMaterial? {
            if (renderEntity!!.customShader != null) {
                return renderEntity!!.customShader
            }
            return if (renderEntity!!.hModel != null && renderEntity!!.hModel!!.NumSurfaces() != 0) {
                renderEntity!!.hModel!!.Surface(0)!!.shader
            } else null
        }

        protected fun BecomeNonSolid() {
            // set CONTENTS_RENDERMODEL so bullets still collide with the moveable
            physicsObj.SetContents(Material.CONTENTS_CORPSE or Material.CONTENTS_RENDERMODEL)
            physicsObj.SetClipMask(Game_local.MASK_SOLID or Material.CONTENTS_CORPSE or Material.CONTENTS_MOVEABLECLIP)
        }

        protected fun InitInitialSpline(startTime: Int) {
            val initialSplineTime: Int
            initialSpline = GetSpline()
            initialSplineTime = spawnArgs.GetInt("initialSplineTime", "300")
            if (initialSpline != null) {
                initialSpline!!.MakeUniform(initialSplineTime.toFloat())
                initialSpline!!.ShiftTime(startTime - initialSpline!!.GetTime(0))
                initialSplineDir.set(initialSpline!!.GetCurrentFirstDerivative(startTime.toFloat()))
                initialSplineDir.timesAssign(physicsObj.GetAxis().Transpose())
                initialSplineDir.Normalize()
                BecomeActive(TH_THINK)
            }
        }

        protected fun FollowInitialSplinePath(): Boolean {
            if (initialSpline != null) {
                initialSpline =
                    if (Game_local.gameLocal.time < initialSpline!!.GetTime(initialSpline!!.GetNumValues() - 1)) {
                        val splinePos = idVec3(initialSpline!!.GetCurrentValue(Game_local.gameLocal.time.toFloat()))
                        val linearVelocity =
                            idVec3(splinePos.minus(physicsObj.GetOrigin()).times(UsercmdGen.USERCMD_HZ.toFloat()))
                        physicsObj.SetLinearVelocity(linearVelocity)
                        val splineDir =
                            idVec3(initialSpline!!.GetCurrentFirstDerivative(Game_local.gameLocal.time.toFloat()))
                        val dir = idVec3(initialSplineDir.times(physicsObj.GetAxis()))
                        val angularVelocity = idVec3(dir.Cross(splineDir))
                        angularVelocity.Normalize()
                        angularVelocity.timesAssign(idMath.ACos16(dir.times(splineDir) / splineDir.Length()) * UsercmdGen.USERCMD_HZ)
                        physicsObj.SetAngularVelocity(angularVelocity)
                        return true
                    } else {
                        // C++: delete initialSpline; initialSpline = NULL;
                        null
                    }
            }
            return false
        }

        protected open fun Event_Activate(activator: idEventArg<idEntity>) {
            var delay: Float
            val init_velocity = idVec3()
            val init_avelocity = idVec3()
            Show()
            if (0 == spawnArgs.GetInt("notPushable")) {
                physicsObj.EnableImpact()
            }
            physicsObj.Activate()
            spawnArgs.GetVector("init_velocity", "0 0 0", init_velocity)
            spawnArgs.GetVector("init_avelocity", "0 0 0", init_avelocity)
            delay = spawnArgs.GetFloat("init_velocityDelay", "0")
            if (delay == 0.0f) {
                physicsObj.SetLinearVelocity(init_velocity)
            } else {
                PostEventSec(EV_SetLinearVelocity, delay, init_velocity)
            }
            delay = spawnArgs.GetFloat("init_avelocityDelay", "0")
            if (delay == 0.0f) {
                physicsObj.SetAngularVelocity(init_avelocity)
            } else {
                PostEventSec(EV_SetAngularVelocity, delay, init_avelocity)
            }
            InitInitialSpline(Game_local.gameLocal.time)
        }

        protected fun Event_BecomeNonSolid() {
            BecomeNonSolid()
        }

        protected fun Event_SetOwnerFromSpawnArgs() {
            val owner = arrayOfNulls<String>(1)
            if (spawnArgs.GetString("owner", "", owner)) {
                ProcessEvent(EV_SetOwner, Game_local.gameLocal.FindEntity(owner[0]!!))
            }
        }

        protected fun Event_IsAtRest() {
            idThread.ReturnInt(physicsObj.IsAtRest())
        }

        protected fun Event_EnableDamage(enable: idEventArg<Float>) {
            if (isD3XP) {
                // clear out attacker
                attacker = null
            }
            canDamage = enable.value != 0.0f
        }

        // D3XP
        fun SetAttacker(ent: idEntity?) {
            attacker = ent
        }

        override fun GetType(): idTypeInfo = Type
        override fun CreateInstance(): idClass = idMoveable()

        // NOTE: Kotlin workaround — calls grandparent idEntity.Damage() since Kotlin
        // doesn't support skipping override levels like C++ does with idEntity::Damage(...)
        fun idEntity_Damage(
            inflictor: idEntity?,
            attacker: idEntity?,
            dir: idVec3,
            damageDefName: String,
            damageScale: Float,
            location: Int
        ) {
            super.Damage(inflictor, attacker, dir, damageDefName, damageScale, location)
        }

        override fun getEventCallBack(event: idEventDef): eventCallback_t<*>? {
            return eventCallbacks[event]
        }

        //
        //
        init {
            physicsObj = idPhysics_RigidBody()
            brokenModel = idStr()
            minDamageVelocity = 100.0f
            maxDamageVelocity = 200.0f
            nextCollideFxTime = 0
            nextDamageTime = 0
            nextSoundTime = 0
            initialSpline = null
            initialSplineDir = vec3_zero
            explode = false
            unbindOnDeath = false
            allowStep = false
            canDamage = false
        }
    }

    /*
     ===============================================================================

     A barrel using rigid body physics. The barrel has special handling of
     the view model orientation to make it look like it rolls instead of slides.

     ===============================================================================
     */
    /*
     ===============================================================================

     idBarrel

     ===============================================================================
     */
    open class idBarrel : idMoveable() {
        companion object {
            val Type = idTypeInfo("idBarrel", "idMoveable") { idBarrel() }
        }

        override fun GetType(): idTypeInfo = Type
        override fun CreateInstance(): idClass = idBarrel()

        // CLASS_PROTOTYPE( idBarrel );
        private val lastOrigin // origin of the barrel the last think frame
                : idVec3
        private val additionalAxis // additional rotation axis
                : idMat3
        private var additionalRotation // additional rotation of the barrel about it's axis
                : Float
        private var barrelAxis // one of the coordinate axes the barrel cylinder is parallel to
                = 0
        private val lastAxis // axis of the barrel the last think frame
                : idMat3
        private var radius // radius of barrel
                = 1.0f

        override fun Spawn() {
            super.Spawn()
            val bounds = GetPhysics().GetBounds()

            // radius of the barrel cylinder
            radius = (bounds[1, 0] - bounds[0, 0]) * 0.5f

            // always a vertical barrel with cylinder axis parallel to the z-axis
            barrelAxis = 2
            lastOrigin.set(GetPhysics().GetOrigin())
            lastAxis.set(GetPhysics().GetAxis())
            additionalRotation = 0.0f
            additionalAxis.Identity()
            if (isD3XP) {
                fl.networkSync = true
            }
        }

        override fun Save(savefile: idSaveGame) {
            super.Save(savefile)
            savefile.WriteFloat(radius)
            savefile.WriteInt(barrelAxis)
            savefile.WriteVec3(lastOrigin)
            savefile.WriteMat3(lastAxis)
            savefile.WriteFloat(additionalRotation)
            savefile.WriteMat3(additionalAxis)
        }

        override fun Restore(savefile: idRestoreGame) {
            super.Restore(savefile)
            radius = savefile.ReadFloat()
            barrelAxis = savefile.ReadInt()
            savefile.ReadVec3(lastOrigin)
            savefile.ReadMat3(lastAxis)
            additionalRotation = savefile.ReadFloat()
            savefile.ReadMat3(additionalAxis)
        }

        fun BarrelThink() {
            val wasAtRest: Boolean
            val onGround: Boolean
            var movedDistance: Float
            val rotatedDistance: Float
            var angle: Float
            val curOrigin = idVec3()
            val gravityNormal = idVec3()
            val dir = idVec3()
            val curAxis: idMat3
            wasAtRest = IsAtRest()

            // run physics
            RunPhysics()

            // only need to give the visual model an additional rotation if the physics were run
            if (!wasAtRest) {

                // current physics state
                onGround = GetPhysics().HasGroundContacts()
                curOrigin.set(GetPhysics().GetOrigin())
                curAxis = GetPhysics().GetAxis()

                // if the barrel is on the ground
                if (onGround) {
                    gravityNormal.set(GetPhysics().GetGravityNormal())
                    dir.set(curOrigin.minus(lastOrigin))
                    dir.minusAssign(gravityNormal.times(dir.times(gravityNormal)))
                    movedDistance = dir.LengthSqr()

                    // if the barrel moved and the barrel is not aligned with the gravity direction
                    if (movedDistance > 0.0f && idMath.Fabs(gravityNormal * curAxis[barrelAxis]) < 0.7f) {

                        // barrel movement since last think frame orthogonal to the barrel axis
                        movedDistance = idMath.Sqrt(movedDistance)
                        dir.timesAssign(1.0f / movedDistance)
                        movedDistance = (1.0f - Math.abs(dir.times(curAxis[barrelAxis]))) * movedDistance

                        // get rotation about barrel axis since last think frame
                        angle = lastAxis[(barrelAxis + 1) % 3].times(curAxis[(barrelAxis + 1) % 3])
                        angle = idMath.ACos(angle)
                        // distance along cylinder hull
                        rotatedDistance = angle * radius

                        // if the barrel moved further than it rotated about it's axis
                        if (movedDistance > rotatedDistance) {

                            // additional rotation of the visual model to make it look
                            // like the barrel rolls instead of slides
                            angle = 180.0f * (movedDistance - rotatedDistance) / (radius * idMath.PI)
                            if (gravityNormal.Cross(curAxis[barrelAxis]).times(dir) < 0.0f) {
                                additionalRotation += angle
                            } else {
                                additionalRotation -= angle
                            }
                            dir.set(vec3_origin)
                            dir[barrelAxis] = 1.0f
                            additionalAxis.set(idRotation(vec3_origin, dir, additionalRotation).ToMat3())
                        }
                    }
                }

                // save state for next think
                lastOrigin.set(curOrigin)
                lastAxis.set(curAxis)
            }
            Present()
        }

        override fun Think() {
            if ((thinkFlags and TH_THINK) != 0) {
                if (!FollowInitialSplinePath()) {
                    BecomeInactive(TH_THINK)
                }
            }
            BarrelThink()
        }

        override fun GetPhysicsToVisualTransform(origin: idVec3, axis: idMat3): Boolean {
            origin.set(vec3_origin)
            axis.set(additionalAxis)
            return true
        }

        override fun ClientPredictionThink() {
            Think()
        }

        init {
            lastOrigin = idVec3()
            lastAxis = idMat3.getMat3_identity()
            additionalRotation = 0.0f
            additionalAxis = idMat3.getMat3_identity()
            fl.networkSync = true
        }
    }

    /*
     ===============================================================================

     idExplodingBarrel

     ===============================================================================
     */
    class idExplodingBarrel : idBarrel() {
        companion object {
            val Type = idTypeInfo("idExplodingBarrel", "idBarrel") { idExplodingBarrel() }

            // enum {
            val EVENT_EXPLODE: Int = idEntity.EVENT_MAXEVENTS
            val EVENT_MAXEVENTS = EVENT_EXPLODE + 1

            // CLASS_PROTOTYPE( idExplodingBarrel );
            private val eventCallbacks: MutableMap<idEventDef, eventCallback_t<*>> = HashMap()

            //
            //
            fun getEventCallBacks(): MutableMap<idEventDef, eventCallback_t<*>> {
                return eventCallbacks
            }

            init {
                eventCallbacks.putAll(idMoveable.getEventCallBacks())
                eventCallbacks[EV_Activate] =
                    eventCallback_t1<idExplodingBarrel> { obj: idExplodingBarrel, activator: idEventArg<*>? ->
                        obj.Event_Activate(activator as idEventArg<idEntity>)
                    }
                eventCallbacks[EV_Respawn] =
                    eventCallback_t0<idExplodingBarrel> { obj: idExplodingBarrel -> obj.Event_Respawn() }
                eventCallbacks[EV_Explode] =
                    eventCallback_t0<idExplodingBarrel> { obj: idExplodingBarrel -> obj.Event_Explode() }
                eventCallbacks[EV_TriggerTargets] =
                    eventCallback_t0<idExplodingBarrel> { obj: idExplodingBarrel -> obj.Event_TriggerTargets() }
            }
        }

        private val spawnOrigin: idVec3
        private var light: renderLight_s
        private var   /*qhandle_t*/lightDefHandle: Int
        private var lightTime: Int
        private var   /*qhandle_t*/particleModelDefHandle: Int
        private var particleRenderEntity: renderEntity_s
        private var particleTime: Int
        private val spawnAxis: idMat3 = idMat3()
        private var state: explode_state_t = explode_state_t.NORMAL
        private var time: Float
        private var isStable: Boolean = true // D3XP

        // ~idExplodingBarrel();
        override fun _deconstructor() {
            if (particleModelDefHandle >= 0) {
                Game_local.gameRenderWorld!!.FreeEntityDef(particleModelDefHandle)
            }
            if (lightDefHandle >= 0) {
                Game_local.gameRenderWorld!!.FreeLightDef(lightDefHandle)
            }
            super._deconstructor()
        }

        override fun Spawn() {
            super.Spawn()
            health = spawnArgs.GetInt("health", "5")
            fl.takedamage = true
            spawnOrigin.set(GetPhysics().GetOrigin())
            spawnAxis.set(GetPhysics().GetAxis())
            state = explode_state_t.NORMAL
            particleModelDefHandle = -1
            lightDefHandle = -1
            lightTime = 0
            particleTime = 0
            time = spawnArgs.GetFloat("time")
            if (isD3XP) {
                isStable = true
                fl.networkSync = true
            }
            particleRenderEntity =
                renderEntity_s() //	memset( &particleRenderEntity, 0, sizeof( particleRenderEntity ) );
            light = renderLight_s() //	memset( &light, 0, sizeof( light ) );
        }

        override fun Save(savefile: idSaveGame) {
            super.Save(savefile)
            savefile.WriteVec3(spawnOrigin)
            savefile.WriteMat3(spawnAxis)
            savefile.WriteInt(TempDump.etoi(state))
            savefile.WriteInt(particleModelDefHandle)
            savefile.WriteInt(lightDefHandle)
            savefile.WriteRenderEntity(particleRenderEntity)
            savefile.WriteRenderLight(light)
            savefile.WriteInt(particleTime)
            savefile.WriteInt(lightTime)
            savefile.WriteFloat(time)
            if (isD3XP) {
                savefile.WriteBool(isStable)
            }
        }

        override fun Restore(savefile: idRestoreGame) {
            super.Restore(savefile)
            savefile.ReadVec3(spawnOrigin)
            savefile.ReadMat3(spawnAxis)
            state = explode_state_t.values()[savefile.ReadInt()]
            particleModelDefHandle = savefile.ReadInt()
            lightDefHandle = savefile.ReadInt()
            particleRenderEntity = savefile.ReadRenderEntity()
            savefile.ReadRenderLight(light)
            particleTime = savefile.ReadInt()
            lightTime = savefile.ReadInt()
            time = savefile.ReadFloat()
            if (isD3XP) {
                isStable = savefile.ReadBool()
            }

            // DG: enforce getting fresh handle, else this may be tied to an unrelated light!
            if (lightDefHandle != -1) {
                lightDefHandle = Game_local.gameRenderWorld!!.AddLightDef(light)
            }
            // DG: same for render entity
            if (particleModelDefHandle != -1) {
                particleModelDefHandle = Game_local.gameRenderWorld!!.AddEntityDef(particleRenderEntity)
            }
        }

        override fun Think() {
            super.BarrelThink()
            if (lightDefHandle >= 0) {
                if (state == explode_state_t.BURNING) {
                    // ramp the color up over 250 ms
                    var pct = (Game_local.gameLocal.time - lightTime) / 250.0f
                    if (pct > 1.0f) {
                        pct = 1.0f
                    }
                    light.origin.set(physicsObj.GetAbsBounds().GetCenter())
                    light.axis.set(idMat3.getMat3_identity())
                    light.shaderParms[RenderWorld.SHADERPARM_RED] = pct
                    light.shaderParms[RenderWorld.SHADERPARM_GREEN] = pct
                    light.shaderParms[RenderWorld.SHADERPARM_BLUE] = pct
                    light.shaderParms[RenderWorld.SHADERPARM_ALPHA] = pct
                    Game_local.gameRenderWorld!!.UpdateLightDef(lightDefHandle, light)
                } else {
                    if (Game_local.gameLocal.time - lightTime > 250) {
                        Game_local.gameRenderWorld!!.FreeLightDef(lightDefHandle)
                        lightDefHandle = -1
                    }
                    return
                }
            }
            if (!Game_local.gameLocal.isClient && state != explode_state_t.BURNING && state != explode_state_t.EXPLODING) {
                BecomeInactive(TH_THINK)
                return
            }
            if (particleModelDefHandle >= 0) {
                particleRenderEntity.origin.set(physicsObj.GetAbsBounds().GetCenter())
                particleRenderEntity.axis.set(idMat3.getMat3_identity())
                Game_local.gameRenderWorld!!.UpdateEntityDef(particleModelDefHandle, particleRenderEntity)
            }
        }

        override fun Damage(
            inflictor: idEntity?, attacker: idEntity?, dir: idVec3,
            damageDefName: String, damageScale: Float, location: Int
        ) {
            val damageDef = Game_local.gameLocal.FindEntityDefDict(damageDefName)
            if (null == damageDef) {
                idGameLocal.Error("Unknown damageDef '%s'\n", damageDefName)
                return
            }
            if (damageDef.FindKey("radius") != null && GetPhysics().GetContents() != 0 && GetBindMaster() == null) {
                PostEventMS(EV_Explode, 400)
            } else {
                idEntity_Damage(inflictor, attacker, dir, damageDefName, damageScale, location)
            }
        }

        override fun Killed(inflictor: idEntity?, attacker: idEntity?, damage: Int, dir: idVec3, location: Int) {
            if (IsHidden() || state == explode_state_t.EXPLODING || state == explode_state_t.BURNING) {
                return
            }
            var f = spawnArgs.GetFloat("burn")
            if (f > 0.0f && state == explode_state_t.NORMAL) {
                state = explode_state_t.BURNING
                PostEventSec(EV_Explode, f)
                StartSound("snd_burn", gameSoundChannel_t.SND_CHANNEL_ANY, 0, false)
                AddParticles(spawnArgs.GetString("model_burn", ""), true)
                return
            } else {
                state = explode_state_t.EXPLODING
                if (Game_local.gameLocal.isServer) {
                    val msg = idBitMsg()
                    val msgBuf = ByteBuffer.allocate(Game_local.MAX_EVENT_PARAM_SIZE)
                    msg.Init(msgBuf, Game_local.MAX_EVENT_PARAM_SIZE)
                    msg.WriteLong(Game_local.gameLocal.time)
                    ServerSendEvent(EVENT_EXPLODE, msg, false, -1)
                }
            }

            // do this before applying radius damage so the ent can trace to any damagable ents nearby
            Hide()
            physicsObj.SetContents(0)
            val splash = spawnArgs.GetString("def_splash_damage", "damage_explosion")
            if (splash != null && !splash.isEmpty()) {
                Game_local.gameLocal.RadiusDamage(GetPhysics().GetOrigin(), this, attacker, this, this, splash)
            }
            ExplodingEffects()

            //FIXME: need to precache all the debris stuff here and in the projectiles
            var kv = spawnArgs.MatchPrefix("def_debris")
            // bool first = true;
            while (kv != null) {
                val debris_args = Game_local.gameLocal.FindEntityDefDict(kv.GetValue().toString(), false)
                if (debris_args != null) {
                    val ent = arrayOfNulls<idEntity>(1)
                    val dir2 = idVec3()
                    var debris: idDebris?
                    //if ( first ) {
                    dir2.set(physicsObj.GetAxis()[1])
                    //	first = false;
                    //} else {
                    dir2.x += Game_local.gameLocal.random.CRandomFloat() * 4.0f
                    dir2.y += Game_local.gameLocal.random.CRandomFloat() * 4.0f
                    //dir.z = gameLocal.random.RandomFloat() * 8.0f;
                    //}
                    dir2.Normalize()
                    Game_local.gameLocal.SpawnEntityDef(debris_args, ent, false)
                    // FIX: Was ent.isEmpty() which is always false since arrayOfNulls(1) has size 1
                    if (ent[0] == null || ent[0] !is idDebris) {
                        idGameLocal.Error("'projectile_debris' is not an idDebris")
                    }
                    debris = ent[0] as idDebris
                    debris.Create(this, physicsObj.GetOrigin(), dir2.ToMat3())
                    debris.Launch()
                    debris.GetRenderEntity()!!.shaderParms[RenderWorld.SHADERPARM_TIME_OF_DEATH] =
                        (Game_local.gameLocal.time + 1500) * 0.001f
                    debris.UpdateVisuals()
                }
                kv = spawnArgs.MatchPrefix("def_debris", kv)
            }
            physicsObj.PutToRest()
            CancelEvents(EV_Explode)
            CancelEvents(EV_Activate)
            f = spawnArgs.GetFloat("respawn")
            if (f > 0.0f) {
                PostEventSec(EV_Respawn, f)
            } else {
                PostEventMS(EV_Remove, 5000)
            }
            if (spawnArgs.GetBool("triggerTargets")) {
                ActivateTargets(this)
            }
        }

        override fun WriteToSnapshot(msg: idBitMsgDelta) {
            super.WriteToSnapshot(msg)
            msg.WriteBits(TempDump.btoi(IsHidden()), 1)
        }

        override fun ReadFromSnapshot(msg: idBitMsgDelta) {
            super.ReadFromSnapshot(msg)
            if (msg.ReadBits(1) != 0) {
                Hide()
            } else {
                Show()
            }
        }

        override fun ClientReceiveEvent(event: Int, time: Int, msg: idBitMsg): Boolean {
            return when (event) {
                EVENT_EXPLODE -> {
                    if (Game_local.gameLocal.realClientTime - msg.ReadLong() < spawnArgs.GetInt(
                            "explode_lapse",
                            "1000"
                        )
                    ) {
                        ExplodingEffects()
                    }
                    true
                }

                else -> {
                    super.ClientReceiveEvent(event, time, msg)
                }
            }
        }

        private fun AddParticles(name: String?, burn: Boolean) {
            if (name != null && !name.isEmpty()) {
                if (particleModelDefHandle >= 0) {
                    Game_local.gameRenderWorld!!.FreeEntityDef(particleModelDefHandle)
                }
                //  memset( &particleRenderEntity, 0, sizeof( particleRenderEntity ) )
                particleRenderEntity = renderEntity_s()
                val modelDef = DeclManager.declManager.FindType(declType_t.DECL_MODELDEF, name) as idDeclModelDef
                if (modelDef != null) {
                    particleRenderEntity.origin.set(physicsObj.GetAbsBounds().GetCenter())
                    particleRenderEntity.axis.set(idMat3.getMat3_identity())
                    particleRenderEntity.hModel = modelDef.ModelHandle()
                    val rgb = if (burn) 0.0f else 1.0f
                    particleRenderEntity.shaderParms[RenderWorld.SHADERPARM_RED] = rgb
                    particleRenderEntity.shaderParms[RenderWorld.SHADERPARM_GREEN] = rgb
                    particleRenderEntity.shaderParms[RenderWorld.SHADERPARM_BLUE] = rgb
                    particleRenderEntity.shaderParms[RenderWorld.SHADERPARM_ALPHA] = rgb
                    particleRenderEntity.shaderParms[RenderWorld.SHADERPARM_TIMEOFFSET] =
                        -MS2SEC(Game_local.gameLocal.realClientTime.toFloat())
                    particleRenderEntity.shaderParms[RenderWorld.SHADERPARM_DIVERSITY] =
                        if (burn) 1.0f else Game_local.gameLocal.random.RandomInt(90).toFloat()
                    if (null == particleRenderEntity.hModel) {
                        particleRenderEntity.hModel = ModelManager.renderModelManager.FindModel(name)
                    }
                    if (isD3XP) {
                        particleRenderEntity.timeGroup = timeGroup
                    }
                    particleModelDefHandle = Game_local.gameRenderWorld!!.AddEntityDef(particleRenderEntity)
                    if (burn) {
                        BecomeActive(TH_THINK)
                    }
                    particleTime = Game_local.gameLocal.realClientTime
                }
            }
        }

        private fun AddLight(name: String, burn: Boolean) {
            if (lightDefHandle >= 0) {
                Game_local.gameRenderWorld!!.FreeLightDef(lightDefHandle)
            }
            //	memset( &light, 0, sizeof ( light ) );
            light = renderLight_s()
            light.axis.set(idMat3.getMat3_identity())
            light.lightRadius.x = spawnArgs.GetFloat("light_radius")
            light.lightRadius.z = light.lightRadius.x
            light.lightRadius.y = light.lightRadius.z
            light.origin.set(physicsObj.GetOrigin())
            light.origin.z += 128.0f
            light.pointLight._val = true
            light.shader = DeclManager.declManager.FindMaterial(name)
            light.shaderParms[RenderWorld.SHADERPARM_RED] = 2.0f
            light.shaderParms[RenderWorld.SHADERPARM_GREEN] = 2.0f
            light.shaderParms[RenderWorld.SHADERPARM_BLUE] = 2.0f
            light.shaderParms[RenderWorld.SHADERPARM_ALPHA] = 2.0f
            lightDefHandle = Game_local.gameRenderWorld!!.AddLightDef(light)
            lightTime = Game_local.gameLocal.realClientTime
            BecomeActive(TH_THINK)
        }

        private fun ExplodingEffects() {
            var temp: String?
            StartSound("snd_explode", gameSoundChannel_t.SND_CHANNEL_ANY, 0, false)
            temp = spawnArgs.GetString("model_damage")
            if (!temp.isEmpty()) { // != '\0' ) {
                SetModel(temp)
                Show()
            }
            temp = spawnArgs.GetString("model_detonate")
            if (!temp.isEmpty()) { // != '\0' ) {
                AddParticles(temp, false)
            }
            temp = spawnArgs.GetString("mtr_lightexplode")
            if (!temp.isEmpty()) { // != '\0' ) {
                AddLight(temp, false)
            }
            temp = spawnArgs.GetString("mtr_burnmark")
            if (!temp.isEmpty()) { // != '\0' ) {
                Game_local.gameLocal.ProjectDecal(
                    GetPhysics().GetOrigin(),
                    GetPhysics().GetGravity(),
                    128.0f,
                    true,
                    96.0f,
                    temp
                )
            }
        }

        // D3XP
        fun SetStability(stability: Boolean) {
            isStable = stability
        }

        // D3XP
        fun IsStable(): Boolean {
            return isStable
        }

        // D3XP
        fun StartBurning() {
            state = explode_state_t.BURNING
            AddParticles("barrelfire.prt", true)
        }

        // D3XP
        fun StopBurning() {
            state = explode_state_t.NORMAL
            if (particleModelDefHandle >= 0) {
                Game_local.gameRenderWorld!!.FreeEntityDef(particleModelDefHandle)
                particleModelDefHandle = -1
                particleTime = 0
                particleRenderEntity = renderEntity_s()
            }
        }

        public override fun Event_Activate(activator: idEventArg<idEntity>) {
            Killed(activator.value, activator.value, 0, vec3_origin, 0)
        }

        private fun Event_Respawn() {
            var i: Int
            val minRespawnDist = spawnArgs.GetInt("respawn_range", "256")
            if (minRespawnDist != 0) {
                var minDist = -1.0f
                i = 0
                while (i < Game_local.gameLocal.numClients) {
                    if (Game_local.gameLocal.entities[i] == null || Game_local.gameLocal.entities[i] !is idPlayer) {
                        i++
                        continue
                    }
                    val v = idVec3(
                        Game_local.gameLocal.entities[i]!!.GetPhysics().GetOrigin().minus(GetPhysics().GetOrigin())
                    )
                    val dist = v.Length()
                    if (minDist < 0 || dist < minDist) {
                        minDist = dist
                    }
                    i++
                }
                if (minDist < minRespawnDist) {
                    PostEventSec(EV_Respawn, spawnArgs.GetInt("respawn_again", "10").toFloat())
                    return
                }
            }
            val temp = spawnArgs.GetString("model")
            if (temp != null && !temp.isEmpty()) {
                SetModel(temp)
            }
            health = spawnArgs.GetInt("health", "5")
            fl.takedamage = true
            physicsObj.SetOrigin(spawnOrigin)
            physicsObj.SetAxis(spawnAxis)
            physicsObj.SetContents(Material.CONTENTS_SOLID)
            physicsObj.DropToFloor()
            state = explode_state_t.NORMAL
            Show()
            UpdateVisuals()
        }

        private fun Event_Explode() {
            if (state == explode_state_t.NORMAL || state == explode_state_t.BURNING) {
                state = explode_state_t.BURNEXPIRED
                Killed(null, null, 0, vec3_zero, 0)
            }
        }

        private fun Event_TriggerTargets() {
            ActivateTargets(this)
        }

        override fun GetType(): idTypeInfo = Type
        override fun CreateInstance(): idClass = idExplodingBarrel()

        override fun getEventCallBack(event: idEventDef): eventCallback_t<*>? {
            return eventCallbacks[event]
        }

        enum class explode_state_t {
            NORMAL,  //= 0,
            BURNING,
            BURNEXPIRED,
            EXPLODING
        }

        init {
            spawnOrigin = idVec3()
            state = explode_state_t.NORMAL
            particleModelDefHandle = -1
            lightDefHandle = -1
            //	memset( &particleRenderEntity, 0, sizeof( particleRenderEntity ) );
            particleRenderEntity = renderEntity_s()
            //	memset( &light, 0, sizeof( light ) );
            light = renderLight_s()
            particleTime = 0
            lightTime = 0
            time = 0.0f
        }
    }
}
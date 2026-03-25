/*
 * Copyright (C) 1999-2011 id Software LLC, a ZeniMax Media company.
 * Translated to Kotlin by Dr. Feederino with support of Claude Code
 *
 * This file is part of the Doom 3 Kotlin project.
 * Original source: neo/Game/Physics/Physics_RigidBody.h, neo/Game/Physics/Physics_RigidBody.cpp
 */

package neo.Game.Physics

import neo.Game.GameSys.Class.idClass
import neo.Game.GameSys.Class.idTypeInfo
import neo.Game.GameSys.SaveGame.idRestoreGame
import neo.Game.GameSys.SaveGame.idSaveGame
import neo.Game.GameSys.SysCvar
import neo.Game.Game_local
import neo.Game.Physics.Clip.idClipModel
import neo.Game.Physics.Physics.impactInfo_s
import neo.Game.Physics.Physics_Base.idPhysics_Base
import neo.Game.TH_PHYSICS
import neo.Game.idEntity
import neo.cm.collisionModelManager
import neo.cm.contactInfo_t
import neo.cm.trace_s
import neo.framework.UsercmdGen
import neo.idlib.BV.idBounds
import neo.idlib.BitMsg.idBitMsgDelta
import neo.idlib.Text.Str
import neo.idlib.Timer.idTimer
import neo.idlib.colorCyan
import neo.idlib.containers.CFloat
import neo.idlib.containers.CInt
import neo.idlib.geometry.Winding.idFixedWinding
import neo.idlib.math.*
import neo.idlib.math.Matrix.idMat3
import neo.idlib.math.Ode.*
import java.nio.FloatBuffer

object Physics_RigidBody {
    const val RB_FORCE_MAX = 1e20f
    val RB_FORCE_EXPONENT_BITS = idMath.BitsForInteger(idMath.BitsForFloat(RB_FORCE_MAX)) + 1
    const val RB_FORCE_TOTAL_BITS = 16
    val RB_FORCE_MANTISSA_BITS = RB_FORCE_TOTAL_BITS - 1 - RB_FORCE_EXPONENT_BITS
    const val RB_MOMENTUM_MAX = 1e20f
    val RB_MOMENTUM_EXPONENT_BITS = idMath.BitsForInteger(idMath.BitsForFloat(RB_MOMENTUM_MAX)) + 1
    const val RB_MOMENTUM_TOTAL_BITS = 16
    val RB_MOMENTUM_MANTISSA_BITS = RB_MOMENTUM_TOTAL_BITS - 1 - RB_MOMENTUM_EXPONENT_BITS

    /*
     ===================================================================================

     Rigid body physics

     Employs an impulse based dynamic simulation which is not very accurate but
     relatively fast and still reliable due to the continuous collision detection.

     ===================================================================================
     */
    const val RB_VELOCITY_MAX = 16000.0f
    val RB_VELOCITY_EXPONENT_BITS = idMath.BitsForInteger(idMath.BitsForFloat(RB_VELOCITY_MAX)) + 1
    const val RB_VELOCITY_TOTAL_BITS = 16
    val RB_VELOCITY_MANTISSA_BITS = RB_VELOCITY_TOTAL_BITS - 1 - RB_VELOCITY_EXPONENT_BITS

    //
    const val STOP_SPEED = 10.0f

    //
    private const val RB_TIMINGS = false
    private const val RB_DEBUG_REST = false // Temporary debug flag for idMoveable rest issue
    private const val TEST_COLLISION_DETECTION = false

    //
    var lastTimerReset = 0
    var numRigidBodies = 0
    var timer_total: idTimer = idTimer()
    var timer_collision: idTimer = idTimer()

    //
    /*
     ================
     idPhysics_RigidBody_SavePState
     ================
     */
    fun idPhysics_RigidBody_SavePState(savefile: idSaveGame, state: rigidBodyPState_s) {
        savefile.WriteInt(state.atRest)
        savefile.WriteFloat(state.lastTimeStep)
        savefile.WriteVec3(state.localOrigin)
        savefile.WriteMat3(state.localAxis)
        savefile.WriteVec6(state.pushVelocity)
        savefile.WriteVec3(state.externalForce)
        savefile.WriteVec3(state.externalTorque)
        savefile.WriteVec3(state.i.position)
        savefile.WriteMat3(state.i.orientation)
        savefile.WriteVec3(state.i.linearMomentum)
        savefile.WriteVec3(state.i.angularMomentum)
    }

    /*
     ================
     idPhysics_RigidBody_RestorePState
     ================
     */
    fun idPhysics_RigidBody_RestorePState(savefile: idRestoreGame, state: rigidBodyPState_s) {
        val atRest = CInt()
        val lastTimeStep = CFloat()
        savefile.ReadInt(atRest)
        savefile.ReadFloat(lastTimeStep)
        savefile.ReadVec3(state.localOrigin)
        savefile.ReadMat3(state.localAxis)
        savefile.ReadVec6(state.pushVelocity)
        savefile.ReadVec3(state.externalForce)
        savefile.ReadVec3(state.externalTorque)
        savefile.ReadVec3(state.i.position)
        savefile.ReadMat3(state.i.orientation)
        savefile.ReadVec3(state.i.linearMomentum)
        savefile.ReadVec3(state.i.angularMomentum)
        state.atRest = atRest.integerValue
        state.lastTimeStep = lastTimeStep._val
    }

    class rigidBodyIState_s {
        val angularMomentum // rotational momentum relative to center of mass
                : idVec3
        val linearMomentum // translational momentum relative to center of mass
                : idVec3
        val orientation // orientation of trace model
                : idMat3
        val position // position of trace model
                : idVec3

        constructor() {
            position = idVec3()
            orientation = idMat3()
            linearMomentum = idVec3()
            angularMomentum = idVec3()
        }

        constructor(state: FloatArray) : this() {
            fromFloatArr(state)
        }

        fun copy(): rigidBodyIState_s {
            val c = rigidBodyIState_s()
            c.position.set(position)
            c.orientation.set(orientation)
            c.linearMomentum.set(linearMomentum)
            c.angularMomentum.set(angularMomentum)
            return c
        }

        fun toFloatArr(): FloatArray {
            val buffer = FloatBuffer.allocate(BYTES / java.lang.Float.BYTES)
            buffer.put(position.ToFloatPtr()).put(orientation.ToFloatPtr()).put(linearMomentum.ToFloatPtr())
                .put(angularMomentum.ToFloatPtr())
            return buffer.array()
        }

        fun fromFloatArr(state: FloatArray) {
            val b = FloatBuffer.wrap(state)
            if (b.hasRemaining()) {
                position.set(idVec3(b.get(), b.get(), b.get()))
            }
            if (b.hasRemaining()) {
                orientation.set(
                    idMat3(
                        b.get(), b.get(), b.get(), b.get(), b.get(), b.get(), b.get(), b.get(), b.get()
                    )
                )
            }
            if (b.hasRemaining()) {
                linearMomentum.set(idVec3(b.get(), b.get(), b.get()))
            }
            if (b.hasRemaining()) {
                angularMomentum.set(idVec3(b.get(), b.get(), b.get()))
            }
        }

        companion object {
            val BYTES: Int = (idVec3.BYTES + idMat3.BYTES + idVec3.BYTES + idVec3.BYTES)
        }
    }

    class rigidBodyPState_s {
        var atRest = 0// set when simulation is suspended
        val externalForce: idVec3 = idVec3() // external force relative to center of mass

        // external torque relative to center of mass
        val externalTorque: idVec3 = idVec3()
        var i: rigidBodyIState_s = rigidBodyIState_s()// state used for integration
        var lastTimeStep = 0.0f// length of last time step
        val localAxis: idMat3 = idMat3()// axis relative to master
        val localOrigin: idVec3 = idVec3()// origin relative to master
        val pushVelocity: idVec6 = idVec6() // push velocity

        fun copy(): rigidBodyPState_s {
            val c = rigidBodyPState_s()
            c.atRest = atRest
            c.externalForce.set(externalForce)
            c.externalTorque.set(externalTorque)
            c.i = i.copy()
            c.lastTimeStep = lastTimeStep
            c.localAxis.set(localAxis)
            c.localOrigin.set(localOrigin)
            c.pushVelocity.set(pushVelocity)
            return c
        }
    }

    class idPhysics_RigidBody : idPhysics_Base() {
        private val centerOfMass // center of mass of trace model
                : idVec3
        private val inertiaTensor // mass distribution
                : idMat3

        private val integrator // integrator
                : idODE
        private var angularFriction // rotational friction
                = 0.0f
        private var bouncyness // bouncyness
                = 0.0f
        private var clipModel // clip model used for collision detection
                : idClipModel?
        private var contactFriction // friction with contact surfaces
                = 0.0f

        // state of the rigid body
        private var current: rigidBodyPState_s = rigidBodyPState_s()
        private var dropToFloor // true if dropping to the floor and putting to rest
                : Boolean

        // master
        private var hasMaster: Boolean
        private val inverseInertiaTensor: idMat3 = idMat3() // inverse inertia tensor
        private var inverseMass // 1 / mass
                : Float
        private var isOrientated: Boolean

        // rigid body properties
        private var linearFriction // translational friction
                = 0.0f

        // derived properties
        private var mass // mass of body
                : Float

        //
        private var noContact // if true do not determine contacts and no contact friction
                : Boolean
        private var noImpact // if true do not activate when another object collides
                : Boolean
        private var saved: rigidBodyPState_s
        private var testSolid // true if testing for solid when dropping to the floor
                = false

        /*
         ================
         idPhysics_RigidBody::~idPhysics_RigidBody
         ================
         */
        override fun _deconstructor() {
            if (clipModel != null) {
                idClipModel.delete(clipModel!!)
            }
            super._deconstructor()
        }

        /*
         ================
         idPhysics_RigidBody::Save
         ================
         */
        override fun Save(savefile: idSaveGame) {
            super.Save(savefile)
            idPhysics_RigidBody_SavePState(savefile, current)
            idPhysics_RigidBody_SavePState(savefile, saved)
            savefile.WriteFloat(linearFriction)
            savefile.WriteFloat(angularFriction)
            savefile.WriteFloat(contactFriction)
            savefile.WriteFloat(bouncyness)
            savefile.WriteClipModel(clipModel)
            savefile.WriteFloat(mass)
            savefile.WriteFloat(inverseMass)
            savefile.WriteVec3(centerOfMass)
            savefile.WriteMat3(inertiaTensor)
            savefile.WriteMat3(inverseInertiaTensor)
            savefile.WriteBool(dropToFloor)
            savefile.WriteBool(testSolid)
            savefile.WriteBool(noImpact)
            savefile.WriteBool(noContact)
            savefile.WriteBool(hasMaster)
            savefile.WriteBool(isOrientated)
        }

        /*
         ================
         idPhysics_RigidBody::Restore
         ================
         */
        override fun Restore(savefile: idRestoreGame) {
            super.Restore(savefile)

            idPhysics_RigidBody_RestorePState(savefile, current)
            idPhysics_RigidBody_RestorePState(savefile, saved)

            linearFriction = savefile.ReadFloat()
            angularFriction = savefile.ReadFloat()
            contactFriction = savefile.ReadFloat()
            bouncyness = savefile.ReadFloat()
            clipModel = savefile.ReadClipModel()
            mass = savefile.ReadFloat()
            inverseMass = savefile.ReadFloat()
            savefile.ReadVec3(centerOfMass)
            savefile.ReadMat3(inertiaTensor)
            savefile.ReadMat3(inverseInertiaTensor)
            dropToFloor = savefile.ReadBool()
            testSolid = savefile.ReadBool()
            noImpact = savefile.ReadBool()
            noContact = savefile.ReadBool()
            hasMaster = savefile.ReadBool()
            isOrientated = savefile.ReadBool()
        }

        /*
         ================
         idPhysics_RigidBody::SetFriction
         ================
         */
        // initialisation
        fun SetFriction(linear: Float, angular: Float, contact: Float) {
            if (linear < 0.0f || linear > 1.0f || angular < 0.0f || angular > 1.0f || contact < 0.0f || contact > 1.0f) {
                return
            }
            linearFriction = linear
            angularFriction = angular
            contactFriction = contact
        }

        /*
         ================
         idPhysics_RigidBody::SetBouncyness
         ================
         */
        fun SetBouncyness(b: Float) {
            if (b < 0.0f || b > 1.0f) {
                return
            }
            bouncyness = b
        }

        /*
         ================
         idPhysics_RigidBody::DropToFloor
         ================
         */
        // same as above but drop to the floor first
        fun DropToFloor() {
            dropToFloor = true
            testSolid = true
        }

        /*
         ================
         idPhysics_RigidBody::NoContact
         ================
         */
        // no contact determination and contact friction
        fun NoContact() {
            noContact = true
        }

        /*
         ================
         idPhysics_RigidBody::EnableImpact
         ================
         */
        // enable/disable activation by impact
        fun EnableImpact() {
            noImpact = false
        }

        /*
         ================
         idPhysics_RigidBody::DisableImpact
         ================
         */
        fun DisableImpact() {
            noImpact = true
        }

        /*
         ================
         idPhysics_RigidBody::SetClipModel
         ================
         */
        // common physics interface
        override fun SetClipModel(model: idClipModel?, density: Float, id: Int /*= 0*/, freeOld: Boolean /*= true*/) {
            val minIndex: Int
            val inertiaScale = idMat3()
            assert(self != null)
            assert(
                model != null // we need a clip model
            )
            assert(
                model!!.IsTraceModel() // and it should be a trace model
            )
            assert(
                density > 0.0f // density should be valid
            )
            if (clipModel != null && clipModel !== model && freeOld) {
                idClipModel.delete(clipModel!!)
            }
            clipModel = model
            clipModel!!.Link(Game_local.gameLocal.clip, self, 0, current.i.position, current.i.orientation)
            val mass = CFloat()
            clipModel!!.GetMassProperties(density, mass, centerOfMass, inertiaTensor)
            this.mass = mass._val

            // check whether or not the clip model has valid mass properties
            if (mass._val <= 0.0f || FLOAT_IS_NAN(mass._val)) {
                Game_local.gameLocal.Warning(
                    "idPhysics_RigidBody::SetClipModel: invalid mass for entity '%s' type '%s'",
                    self!!.name,
                    self!!.GetType().name
                )
                mass._val = 1.0f
                centerOfMass.Zero()
                inertiaTensor.Identity()
            }

            // check whether or not the inertia tensor is balanced
            minIndex = Min3Index(inertiaTensor[0, 0], inertiaTensor[1, 1], inertiaTensor[2, 2])
            inertiaScale.Identity()
            inertiaScale.set(0, 0, inertiaTensor[0, 0] / inertiaTensor[minIndex, minIndex])
            inertiaScale.set(1, 1, inertiaTensor[1, 1] / inertiaTensor[minIndex, minIndex])
            inertiaScale.set(2, 2, inertiaTensor[2, 2] / inertiaTensor[minIndex, minIndex])
            if (inertiaScale[0, 0] > MAX_INERTIA_SCALE || inertiaScale[1, 1] > MAX_INERTIA_SCALE || inertiaScale[2, 2] > MAX_INERTIA_SCALE) {
                Game_local.gameLocal.DWarning(
                    "idPhysics_RigidBody::SetClipModel: unbalanced inertia tensor for entity '%s' type '%s'",
                    self!!.name,
                    self!!.GetType().name
                )
                val min = inertiaTensor[minIndex, minIndex] * MAX_INERTIA_SCALE
                inertiaScale.set(
                    (minIndex + 1) % 3, (minIndex + 1) % 3, min / inertiaTensor[(minIndex + 1) % 3, (minIndex + 1) % 3]
                )
                inertiaScale.set(
                    (minIndex + 2) % 3, (minIndex + 2) % 3, min / inertiaTensor[(minIndex + 2) % 3, (minIndex + 2) % 3]
                )
                inertiaTensor.timesAssign(inertiaScale)
            }
            inverseMass = 1.0f / mass._val
            inverseInertiaTensor.set(inertiaTensor.Inverse().times(1.0f / 6.0f))

            // DIAGNOSTIC: Log mass properties when setting up clip model
            if (RB_DEBUG_REST) {
                Game_local.gameLocal.Printf(
                    "RB_SETUP [%s]: mass=%.3f inertia=(%.3f,%.3f,%.3f)\n",
                    self?.name ?: "?",
                    mass._val,
                    inertiaTensor[0, 0], inertiaTensor[1, 1], inertiaTensor[2, 2]
                )
            }

            current.i.linearMomentum.Zero()
            current.i.angularMomentum.Zero()
        }

        /*
         ================
         idPhysics_RigidBody::GetClipModel
         ================
         */
        override fun GetClipModel(id: Int /*= 0*/): idClipModel? {
            return clipModel
        }

        /*
         ================
         idPhysics_RigidBody::GetNumClipModels
         ================
         */
        override fun GetNumClipModels(): Int {
            return 1
        }

        /*
         ================
         idPhysics_RigidBody::SetMass
         ================
         */
        override fun SetMass(mass: Float, id: Int /*= -1*/) {
            assert(mass > 0.0f)
            inertiaTensor.timesAssign(mass / this.mass)
            inverseInertiaTensor.set(inertiaTensor.Inverse().times(1.0f / 6.0f))
            this.mass = mass
            inverseMass = 1.0f / mass
        }

        /*
         ================
         idPhysics_RigidBody::GetMass
         ================
         */
        override fun GetMass(id: Int /*= -1*/): Float {
            return mass
        }

        /*
         ================
         idPhysics_RigidBody::SetContents
         ================
         */
        override fun SetContents(contents: Int, id: Int /*= -1*/) {
            clipModel!!.SetContents(contents)
        }

        /*
         ================
         idPhysics_RigidBody::GetContents
         ================
         */
        override fun GetContents(id: Int /*= -1*/): Int {
            return clipModel!!.GetContents()
        }

        /*
         ================
         idPhysics_RigidBody::GetBounds
         ================
         */
        override fun GetBounds(id: Int /*= -1*/): idBounds {
            return clipModel!!.GetBounds()
        }

        /*
         ================
         idPhysics_RigidBody::GetAbsBounds
         ================
         */
        override fun GetAbsBounds(id: Int /*= -1*/): idBounds {
            return clipModel!!.GetAbsBounds()
        }

        /*
         ================
         idPhysics_RigidBody::Evaluate

         Evaluate the impulse based rigid body physics.
         When a collision occurs an impulse is applied at the moment of impact but
         the remaining time after the collision is ignored.
         ================
         */
        override fun Evaluate(timeStepMSec: Int, endTimeMSec: Int): Boolean {
            val next: rigidBodyPState_s
            val collision = trace_s()
            val impulse = idVec3()
            val ent: idEntity?
            val oldOrigin = idVec3(current.i.position)
            val masterOrigin = idVec3()
            val oldAxis = idMat3()
            val masterAxis = idMat3()
            val timeStep: Float
            var collided = false
            var cameToRest = false
            timeStep = MS2SEC(timeStepMSec.toFloat())
            current.lastTimeStep = timeStep
            if (hasMaster) {
                oldAxis.set(current.i.orientation)
                self!!.GetMasterPosition(masterOrigin, masterAxis)
                current.i.position.set(masterOrigin + current.localOrigin * masterAxis)
                if (isOrientated) {
                    current.i.orientation.set(current.localAxis * masterAxis)
                } else {
                    current.i.orientation.set(current.localAxis)
                }
                clipModel!!.Link(
                    Game_local.gameLocal.clip, self, clipModel!!.GetId(), current.i.position, current.i.orientation
                )
                current.i.linearMomentum.set(((current.i.position - oldOrigin) / timeStep) * mass)
                current.i.angularMomentum.set(inertiaTensor * ((current.i.orientation * oldAxis.Transpose()).ToAngularVelocity() / timeStep))
                current.externalForce.Zero()
                current.externalTorque.Zero()
                return current.i.position != oldOrigin || current.i.orientation != oldAxis
            }

            // if the body is at rest
            if (current.atRest >= 0 || timeStep <= 0.0f) {
                DebugDraw()
                return false
            }

            // if putting the body to rest
            if (dropToFloor) {
                DropToFloorAndRest()
                current.externalForce.Zero()
                current.externalTorque.Zero()
                return true
            }
            if (RB_TIMINGS) {
                timer_total.Start()
            }

            // move the rigid body velocity into the frame of a pusher
            //	current.i.linearMomentum -= current.pushVelocity.SubVec3( 0 ) * mass;
            //	current.i.angularMomentum -= current.pushVelocity.SubVec3( 1 ) * inertiaTensor;
            clipModel!!.Unlink()
            next = current.copy()

            // calculate next position and orientation
            Integrate(timeStep, next)
            if (RB_TIMINGS) {
                timer_collision.Start()
            }

            // check for collisions from the current to the next state
            collided = CheckForCollisions(timeStep, next, collision)
            if (RB_TIMINGS) {
                timer_collision.Stop()
            }

            // set the new state
            current = next

            if (collided) {
                // DEBUG: Log BEFORE CollisionImpulse (matches C++ diagnostic placement)
                if (RB_DEBUG_REST) {
                    val linVel = current.i.linearMomentum.times(inverseMass)
                    val linSpeed = linVel.Length()
                    if (linSpeed > STOP_SPEED * 2.0f) {
                        Game_local.gameLocal.Printf(
                            "RB_DEBUG [%s] collided=true frac=%.4f linSpeed=%.2f angMom=%.2f pos=(%.1f,%.1f,%.1f)\n",
                            self!!.name,
                            collision.fraction,
                            linSpeed,
                            current.i.angularMomentum.Length(),
                            current.i.position.x,
                            current.i.position.y,
                            current.i.position.z
                        )
                    }
                }
                // apply collision impulse
                if (CollisionImpulse(collision, impulse)) {
                    current.atRest = Game_local.gameLocal.time
                }
            }

            // update the position of the clip model
            clipModel!!.Link(
                Game_local.gameLocal.clip, self, clipModel!!.GetId(), current.i.position, current.i.orientation
            )
            DebugDraw()
            if (!noContact) {
                if (RB_TIMINGS) {
                    timer_collision.Start()
                }
                // get contacts
                EvaluateContacts()
                if (RB_TIMINGS) {
                    timer_collision.Stop()
                }

                // check if the body has come to rest
                if (TestIfAtRest()) {
                    // put to rest
                    Rest()
                    cameToRest = true
                } else {
                    // apply contact friction
                    ContactFriction(timeStep)
                }
            }
            if (current.atRest < 0) {
                ActivateContactEntities()
            }
            if (collided) {
                // if the rigid body didn't come to rest or the other entity is not at rest
                ent = Game_local.gameLocal.entities[collision.c.entityNum]
                if (ent != null && (!cameToRest || !ent.IsAtRest())) {
                    // apply impact to other entity
                    ent.ApplyImpulse(self, collision.c.id, collision.c.point, impulse.unaryMinus())
                }
            }

            // move the rigid body velocity back into the world frame
//	current.i.linearMomentum += current.pushVelocity.SubVec3( 0 ) * mass;
//	current.i.angularMomentum += current.pushVelocity.SubVec3( 1 ) * inertiaTensor;
            current.pushVelocity.Zero()
            current.lastTimeStep = timeStep
            current.externalForce.Zero()
            current.externalTorque.Zero()
            if (IsOutsideWorld()) {
                Game_local.gameLocal.Warning(
                    "rigid body moved outside world bounds for entity '%s' type '%s' at (%s)",
                    self!!.name,
                    self!!.GetType().name,
                    current.i.position.ToString(0)
                )
                Rest()
            }
            if (RB_TIMINGS) {
                timer_total.Stop()
                if (SysCvar.rb_showTimings.GetInteger() == 1) {
                    Game_local.gameLocal.Printf(
                        "%12s: t %1.4f cd %1.4f\n",
                        self!!.name,
                        timer_total.Milliseconds(),
                        timer_collision.Milliseconds()
                    )
                    lastTimerReset = 0
                } else if (SysCvar.rb_showTimings.GetInteger() == 2) {
                    numRigidBodies++
                    if (endTimeMSec > lastTimerReset) {
                        Game_local.gameLocal.Printf(
                            "rb %d: t %1.4f cd %1.4f\n",
                            numRigidBodies,
                            timer_total.Milliseconds(),
                            timer_collision.Milliseconds()
                        )
                    }
                }
                if (endTimeMSec > lastTimerReset) {
                    lastTimerReset = endTimeMSec
                    numRigidBodies = 0
                    timer_total.Clear()
                    timer_collision.Clear()
                }
            }
            return true
        }

        /*
         ================
         idPhysics_RigidBody::UpdateTime
         ================
         */
        override fun UpdateTime(endTimeMSec: Int) {}

        /*
         ================
         idPhysics_RigidBody::GetTime
         ================
         */
        override fun GetTime(): Int {
            return Game_local.gameLocal.time
        }

        /*
         ================
         idPhysics_RigidBody::GetImpactInfo
         ================
         */
        override fun GetImpactInfo(id: Int, point: idVec3): impactInfo_s {
            val linearVelocity = idVec3()
            val angularVelocity = idVec3()
            val inverseWorldInertiaTensor: idMat3
            val info = impactInfo_s()
            linearVelocity.set(current.i.linearMomentum.times(inverseMass))
            inverseWorldInertiaTensor =
                current.i.orientation.Transpose().times(inverseInertiaTensor.times(current.i.orientation))
            angularVelocity.set(inverseWorldInertiaTensor.times(current.i.angularMomentum))
            info.invMass = inverseMass
            info.invInertiaTensor.set(inverseWorldInertiaTensor)
            info.position.set(point.minus(current.i.position.plus(centerOfMass.times(current.i.orientation))))
            info.velocity.set(linearVelocity.plus(angularVelocity.Cross(info.position)))
            return info
        }

        /*
         ================
         idPhysics_RigidBody::ApplyImpulse
         ================
         */
        override fun ApplyImpulse(id: Int, point: idVec3, impulse: idVec3) {
            if (noImpact) {
                return
            }
            current.i.linearMomentum.plusAssign(impulse)
            current.i.angularMomentum.plusAssign(
                point.minus(current.i.position.plus(centerOfMass.times(current.i.orientation))).Cross(impulse)
            )
            Activate()
        }

        /*
         ================
         idPhysics_RigidBody::AddForce
         ================
         */
        override fun AddForce(id: Int, point: idVec3, force: idVec3) {
            if (noImpact) {
                return
            }
            current.externalForce.plusAssign(force)
            current.externalTorque.plusAssign(
                point.minus(current.i.position.plus(centerOfMass.times(current.i.orientation))).Cross(force)
            )
            Activate()
        }

        /*
         ================
         idPhysics_RigidBody::Activate
         ================
         */
        override fun Activate() {
            current.atRest = -1
            self!!.BecomeActive(TH_PHYSICS)
        }

        /*
         ================
         idPhysics_RigidBody::PutToRest

         put to rest untill something collides with this physics object
         ================
         */
        override fun PutToRest() {
            Rest()
        }

        /*
         ================
         idPhysics_RigidBody::IsAtRest
         ================
         */
        override fun IsAtRest(): Boolean {
            return current.atRest >= 0
        }

        /*
         ================
         idPhysics_RigidBody::GetRestStartTime
         ================
         */
        override fun GetRestStartTime(): Int {
            return current.atRest
        }

        /*
         ================
         idPhysics_RigidBody::IsPushable
         ================
         */
        override fun IsPushable(): Boolean {
            return !noImpact && !hasMaster
        }

        /*
         ================
         idPhysics_RigidBody::SaveState
         ================
         */
        override fun SaveState() {
            saved = current.copy()
        }

        /*
         ================
         idPhysics_RigidBody::RestoreState
         ================
         */
        override fun RestoreState() {
            current = saved.copy()
            clipModel!!.Link(
                Game_local.gameLocal.clip, self, clipModel!!.GetId(), current.i.position, current.i.orientation
            )
            EvaluateContacts()
        }

        /*
         ================
         idPhysics::SetOrigin
         ================
         */
        override fun SetOrigin(newOrigin: idVec3, id: Int /*= -1*/) {
            val masterOrigin = idVec3()
            val masterAxis = idMat3()
            current.localOrigin.set(newOrigin)
            if (hasMaster) {
                self!!.GetMasterPosition(masterOrigin, masterAxis)
                current.i.position.set(masterOrigin.plus(newOrigin.times(masterAxis)))
            } else {
                current.i.position.set(newOrigin)
            }
            clipModel!!.Link(
                Game_local.gameLocal.clip, self, clipModel!!.GetId(), current.i.position, clipModel!!.GetAxis()
            )
            Activate()
        }

        /*
         ================
         idPhysics::SetAxis
         ================
         */
        override fun SetAxis(newAxis: idMat3, id: Int /*= -1*/) {
            val masterOrigin = idVec3()
            val masterAxis = idMat3()
            current.localAxis.set(newAxis)
            if (hasMaster && isOrientated) {
                self!!.GetMasterPosition(masterOrigin, masterAxis)
                current.i.orientation.set(newAxis.times(masterAxis))
            } else {
                current.i.orientation.set(newAxis)
            }
            clipModel!!.Link(
                Game_local.gameLocal.clip, self, clipModel!!.GetId(), clipModel!!.GetOrigin(), current.i.orientation
            )
            Activate()
        }

        /*
         ================
         idPhysics::Move
         ================
         */
        override fun Translate(translation: idVec3, id: Int /*= -1*/) {
            current.localOrigin.plusAssign(translation)
            current.i.position.plusAssign(translation)
            clipModel!!.Link(
                Game_local.gameLocal.clip, self, clipModel!!.GetId(), current.i.position, clipModel!!.GetAxis()
            )
            Activate()
        }

        /*
         ================
         idPhysics::Rotate
         ================
         */
        override fun Rotate(rotation: idRotation, id: Int /*= -1*/) {
            val masterOrigin = idVec3()
            val masterAxis = idMat3()
            current.i.orientation.timesAssign(rotation.ToMat3())
            current.i.position.timesAssign(rotation)
            if (hasMaster) {
                self!!.GetMasterPosition(masterOrigin, masterAxis)
                current.localAxis.timesAssign(rotation.ToMat3())
                current.localOrigin.set(current.i.position.minus(masterOrigin).times(masterAxis.Transpose()))
            } else {
                current.localAxis.set(current.i.orientation)
                current.localOrigin.set(current.i.position)
            }
            clipModel!!.Link(
                Game_local.gameLocal.clip, self, clipModel!!.GetId(), current.i.position, current.i.orientation
            )
            Activate()
        }

        /*
         ================
         idPhysics_RigidBody::GetOrigin
         ================
         */
        override fun GetOrigin(id: Int /*= 0*/): idVec3 {
            return current.i.position
        }

        /*
         ================
         idPhysics_RigidBody::GetAxis
         ================
         */
        override fun GetAxis(id: Int /*= 0*/): idMat3 {
            return current.i.orientation
        }

        /*
         ================
         idPhysics_RigidBody::SetLinearVelocity
         ================
         */
        override fun SetLinearVelocity(newLinearVelocity: idVec3, id: Int /*= 0*/) {
            current.i.linearMomentum.set(newLinearVelocity.times(mass))
            Activate()
        }

        /*
         ================
         idPhysics_RigidBody::SetAngularVelocity
         ================
         */
        override fun SetAngularVelocity(newAngularVelocity: idVec3, id: Int /*= 0*/) {
            current.i.angularMomentum.set(newAngularVelocity.times(inertiaTensor))
            Activate()
        }

        /*
         ================
         idPhysics_RigidBody::GetLinearVelocity
         ================
         */
        override fun GetLinearVelocity(id: Int /*= 0*/): idVec3 {
            curLinearVelocity.set(current.i.linearMomentum.times(inverseMass))
            return curLinearVelocity
        }

        /*
         ================
         idPhysics_RigidBody::GetAngularVelocity
         ================
         */
        override fun GetAngularVelocity(id: Int /*= 0*/): idVec3 {
            val inverseWorldInertiaTensor: idMat3
            inverseWorldInertiaTensor =
                current.i.orientation.Transpose().times(inverseInertiaTensor.times(current.i.orientation))
            curAngularVelocity.set(inverseWorldInertiaTensor.times(current.i.angularMomentum))
            return curAngularVelocity
        }

        /*
         ================
         idPhysics_RigidBody::ClipTranslation
         ================
         */
        override fun ClipTranslation(results: trace_s, translation: idVec3, model: idClipModel?) {
            if (model != null) {
                Game_local.gameLocal.clip.TranslationModel(
                    results,
                    clipModel!!.GetOrigin(),
                    clipModel!!.GetOrigin().plus(translation),
                    clipModel,
                    clipModel!!.GetAxis(),
                    clipMask,
                    model.Handle(),
                    model.GetOrigin(),
                    model.GetAxis()
                )
            } else {
                Game_local.gameLocal.clip.Translation(
                    results,
                    clipModel!!.GetOrigin(),
                    clipModel!!.GetOrigin().plus(translation),
                    clipModel,
                    clipModel!!.GetAxis(),
                    clipMask,
                    self
                )
            }
        }

        /*
         ================
         idPhysics_RigidBody::ClipRotation
         ================
         */
        override fun ClipRotation(results: trace_s, rotation: idRotation, model: idClipModel?) {
            if (model != null) {
                Game_local.gameLocal.clip.RotationModel(
                    results,
                    clipModel!!.GetOrigin(),
                    rotation,
                    clipModel,
                    clipModel!!.GetAxis(),
                    clipMask,
                    model.Handle(),
                    model.GetOrigin(),
                    model.GetAxis()
                )
            } else {
                Game_local.gameLocal.clip.Rotation(
                    results, clipModel!!.GetOrigin(), rotation, clipModel, clipModel!!.GetAxis(), clipMask, self
                )
            }
        }

        /*
         ================
         idPhysics_RigidBody::ClipContents
         ================
         */
        override fun ClipContents(model: idClipModel?): Int {
            return if (model != null) {
                Game_local.gameLocal.clip.ContentsModel(
                    clipModel!!.GetOrigin(),
                    clipModel,
                    clipModel!!.GetAxis(),
                    -1,
                    model.Handle(),
                    model.GetOrigin(),
                    model.GetAxis()
                )
            } else {
                Game_local.gameLocal.clip.Contents(clipModel!!.GetOrigin(), clipModel, clipModel!!.GetAxis(), -1, null)
            }
        }

        /*
         ================
         idPhysics_RigidBody::DisableClip
         ================
         */
        override fun DisableClip() {
            clipModel!!.Disable()
        }

        /*
         ================
         idPhysics_RigidBody::EnableClip
         ================
         */
        override fun EnableClip() {
            clipModel!!.Enable()
        }

        /*
         ================
         idPhysics_RigidBody::UnlinkClip
         ================
         */
        override fun UnlinkClip() {
            clipModel!!.Unlink()
        }

        /*
         ================
         idPhysics_RigidBody::LinkClip
         ================
         */
        override fun LinkClip() {
            clipModel!!.Link(
                Game_local.gameLocal.clip, self, clipModel!!.GetId(), current.i.position, current.i.orientation
            )
        }

        /*
         ================
         idPhysics_RigidBody::EvaluateContacts
         ================
         */
        override fun EvaluateContacts(): Boolean {
            val dir = idVec6()
            val num: Int
            ClearContacts()
            contacts.SetNum(10, false)
            dir.SubVec3_oSet(0, current.i.linearMomentum.plus(gravityVector.times(current.lastTimeStep * mass)))
            dir.SubVec3_oSet(1, current.i.angularMomentum)
            dir.SubVec3_Normalize(0)
            dir.SubVec3_Normalize(1)
            val contactz = contacts.getList(Array<contactInfo_t>::class.java) as Array<contactInfo_t>
            num = Game_local.gameLocal.clip.Contacts(
                contactz,
                10,
                clipModel!!.GetOrigin(),
                dir,
                Physics.CONTACT_EPSILON,
                clipModel,
                clipModel!!.GetAxis(),
                clipMask,
                self
            )
            for (i in 0 until num) {
                contacts[i] = contactz[i]
            }
            contacts.SetNum(num, false)

            // DIAGNOSTIC: Log contact sweep direction and count (always, even when 0)
            if (RB_DEBUG_REST) {
                val sweepDir = dir.SubVec3(0)
                Game_local.gameLocal.Printf(
                    "  CONTACTS [%s]: %d contacts, sweepDir=(%.2f,%.2f,%.2f) linMom=(%.2f,%.2f,%.2f)\n",
                    self!!.name, contacts.Num(),
                    sweepDir.x, sweepDir.y, sweepDir.z,
                    current.i.linearMomentum.x, current.i.linearMomentum.y, current.i.linearMomentum.z
                )
            }

            AddContactEntitiesForContacts()
            return contacts.Num() != 0
        }

        /*
         ================
         idPhysics_RigidBody::SetPushed
         ================
         */
        override fun SetPushed(deltaTime: Int) {
            val rotation: idRotation?
            rotation = saved.i.orientation.times(current.i.orientation).ToRotation()

            // velocity with which the af is pushed
            current.pushVelocity.SubVec3_oPluSet(
                0, current.i.position.minus(saved.i.position).div(deltaTime * idMath.M_MS2SEC)
            )
            current.pushVelocity.SubVec3_oPluSet(
                1, rotation.GetVec().times(-DEG2RAD(rotation.GetAngle())).div(deltaTime * idMath.M_MS2SEC)
            )
        }

        /*
         ================
         idPhysics_RigidBody::GetPushedLinearVelocity
         ================
         */
        override fun GetPushedLinearVelocity(id: Int /*= 0*/): idVec3 {
            return current.pushVelocity.SubVec3(0)
        }

        /*
         ================
         idPhysics_RigidBody::GetPushedAngularVelocity
         ================
         */
        override fun GetPushedAngularVelocity(id: Int /*= 0*/): idVec3 {
            return current.pushVelocity.SubVec3(1)
        }

        /*
         ================
         idPhysics_RigidBody::SetMaster
         ================
         */
        override fun SetMaster(master: idEntity?, orientated: Boolean) {
            val masterOrigin = idVec3()
            val masterAxis = idMat3()
            if (master != null) {
                if (!hasMaster) {
                    // transform from world space to master space
                    self!!.GetMasterPosition(masterOrigin, masterAxis)
                    current.localOrigin.set(current.i.position.minus(masterOrigin).times(masterAxis.Transpose()))
                    if (orientated) {
                        current.localAxis.set(current.i.orientation.times(masterAxis.Transpose()))
                    } else {
                        current.localAxis.set(current.i.orientation)
                    }
                    hasMaster = true
                    isOrientated = orientated
                    ClearContacts()
                }
            } else {
                if (hasMaster) {
                    hasMaster = false
                    Activate()
                }
            }
        }

        /*
         ================
         idPhysics_RigidBody::WriteToSnapshot
         ================
         */
        override fun WriteToSnapshot(msg: idBitMsgDelta) {
            val quat: idCQuat?
            val localQuat: idCQuat?
            quat = current.i.orientation.ToCQuat()
            localQuat = current.localAxis.ToCQuat()
            msg.WriteLong(current.atRest)
            msg.WriteFloat(current.i.position[0])
            msg.WriteFloat(current.i.position[1])
            msg.WriteFloat(current.i.position[2])
            msg.WriteFloat(quat.x)
            msg.WriteFloat(quat.y)
            msg.WriteFloat(quat.z)
            msg.WriteFloat(
                current.i.linearMomentum[0], RB_MOMENTUM_EXPONENT_BITS, RB_MOMENTUM_MANTISSA_BITS
            )
            msg.WriteFloat(
                current.i.linearMomentum[1], RB_MOMENTUM_EXPONENT_BITS, RB_MOMENTUM_MANTISSA_BITS
            )
            msg.WriteFloat(
                current.i.linearMomentum[2], RB_MOMENTUM_EXPONENT_BITS, RB_MOMENTUM_MANTISSA_BITS
            )
            msg.WriteFloat(
                current.i.angularMomentum[0], RB_MOMENTUM_EXPONENT_BITS, RB_MOMENTUM_MANTISSA_BITS
            )
            msg.WriteFloat(
                current.i.angularMomentum[1], RB_MOMENTUM_EXPONENT_BITS, RB_MOMENTUM_MANTISSA_BITS
            )
            msg.WriteFloat(
                current.i.angularMomentum[2], RB_MOMENTUM_EXPONENT_BITS, RB_MOMENTUM_MANTISSA_BITS
            )
            msg.WriteDeltaFloat(current.i.position[0], current.localOrigin[0])
            msg.WriteDeltaFloat(current.i.position[1], current.localOrigin[1])
            msg.WriteDeltaFloat(current.i.position[2], current.localOrigin[2])
            msg.WriteDeltaFloat(quat.x, localQuat.x)
            msg.WriteDeltaFloat(quat.y, localQuat.y)
            msg.WriteDeltaFloat(quat.z, localQuat.z)
            msg.WriteDeltaFloat(
                0.0f, current.pushVelocity[0], RB_VELOCITY_EXPONENT_BITS, RB_VELOCITY_MANTISSA_BITS
            )
            msg.WriteDeltaFloat(
                0.0f, current.pushVelocity[1], RB_VELOCITY_EXPONENT_BITS, RB_VELOCITY_MANTISSA_BITS
            )
            msg.WriteDeltaFloat(
                0.0f, current.pushVelocity[2], RB_VELOCITY_EXPONENT_BITS, RB_VELOCITY_MANTISSA_BITS
            )
            msg.WriteDeltaFloat(
                0.0f, current.externalForce[0], RB_FORCE_EXPONENT_BITS, RB_FORCE_MANTISSA_BITS
            )
            msg.WriteDeltaFloat(
                0.0f, current.externalForce[1], RB_FORCE_EXPONENT_BITS, RB_FORCE_MANTISSA_BITS
            )
            msg.WriteDeltaFloat(
                0.0f, current.externalForce[2], RB_FORCE_EXPONENT_BITS, RB_FORCE_MANTISSA_BITS
            )
            msg.WriteDeltaFloat(
                0.0f, current.externalTorque[0], RB_FORCE_EXPONENT_BITS, RB_FORCE_MANTISSA_BITS
            )
            msg.WriteDeltaFloat(
                0.0f, current.externalTorque[1], RB_FORCE_EXPONENT_BITS, RB_FORCE_MANTISSA_BITS
            )
            msg.WriteDeltaFloat(
                0.0f, current.externalTorque[2], RB_FORCE_EXPONENT_BITS, RB_FORCE_MANTISSA_BITS
            )
        }

        /*
         ================
         idPhysics_RigidBody::ReadFromSnapshot
         ================
         */
        override fun ReadFromSnapshot(msg: idBitMsgDelta) {
            val quat = idCQuat()
            val localQuat = idCQuat()
            current.atRest = msg.ReadLong()
            current.i.position[0] = msg.ReadFloat()
            current.i.position[1] = msg.ReadFloat()
            current.i.position[2] = msg.ReadFloat()
            quat.x = msg.ReadFloat()
            quat.y = msg.ReadFloat()
            quat.z = msg.ReadFloat()
            current.i.linearMomentum[0] = msg.ReadFloat(RB_MOMENTUM_EXPONENT_BITS, RB_MOMENTUM_MANTISSA_BITS)
            current.i.linearMomentum[1] = msg.ReadFloat(RB_MOMENTUM_EXPONENT_BITS, RB_MOMENTUM_MANTISSA_BITS)
            current.i.linearMomentum[2] = msg.ReadFloat(RB_MOMENTUM_EXPONENT_BITS, RB_MOMENTUM_MANTISSA_BITS)
            current.i.angularMomentum[0] = msg.ReadFloat(RB_MOMENTUM_EXPONENT_BITS, RB_MOMENTUM_MANTISSA_BITS)
            current.i.angularMomentum[1] = msg.ReadFloat(RB_MOMENTUM_EXPONENT_BITS, RB_MOMENTUM_MANTISSA_BITS)
            current.i.angularMomentum[2] = msg.ReadFloat(RB_MOMENTUM_EXPONENT_BITS, RB_MOMENTUM_MANTISSA_BITS)
            current.localOrigin[0] = msg.ReadDeltaFloat(current.i.position[0])
            current.localOrigin[1] = msg.ReadDeltaFloat(current.i.position[1])
            current.localOrigin[2] = msg.ReadDeltaFloat(current.i.position[2])
            localQuat.x = msg.ReadDeltaFloat(quat.x)
            localQuat.y = msg.ReadDeltaFloat(quat.y)
            localQuat.z = msg.ReadDeltaFloat(quat.z)
            current.pushVelocity[0] = msg.ReadDeltaFloat(
                0.0f, RB_VELOCITY_EXPONENT_BITS, RB_VELOCITY_MANTISSA_BITS
            )
            current.pushVelocity[1] = msg.ReadDeltaFloat(
                0.0f, RB_VELOCITY_EXPONENT_BITS, RB_VELOCITY_MANTISSA_BITS
            )
            current.pushVelocity[2] = msg.ReadDeltaFloat(
                0.0f, RB_VELOCITY_EXPONENT_BITS, RB_VELOCITY_MANTISSA_BITS
            )
            current.externalForce[0] = msg.ReadDeltaFloat(
                0.0f, RB_FORCE_EXPONENT_BITS, RB_FORCE_MANTISSA_BITS
            )
            current.externalForce[1] = msg.ReadDeltaFloat(
                0.0f, RB_FORCE_EXPONENT_BITS, RB_FORCE_MANTISSA_BITS
            )
            current.externalForce[2] = msg.ReadDeltaFloat(
                0.0f, RB_FORCE_EXPONENT_BITS, RB_FORCE_MANTISSA_BITS
            )
            current.externalTorque[0] = msg.ReadDeltaFloat(
                0.0f, RB_FORCE_EXPONENT_BITS, RB_FORCE_MANTISSA_BITS
            )
            current.externalTorque[1] = msg.ReadDeltaFloat(
                0.0f, RB_FORCE_EXPONENT_BITS, RB_FORCE_MANTISSA_BITS
            )
            current.externalTorque[2] = msg.ReadDeltaFloat(
                0.0f, RB_FORCE_EXPONENT_BITS, RB_FORCE_MANTISSA_BITS
            )
            current.i.orientation.set(quat.ToMat3())
            current.localAxis.set(localQuat.ToMat3())
            if (clipModel != null) {
                clipModel?.Link(
                    Game_local.gameLocal.clip, self, clipModel!!.GetId(), current.i.position, current.i.orientation
                )
            }
        }

        /*
         ================
         idPhysics_RigidBody::Integrate

         Calculate next state from the current state using an integrator.
         ================
         */
        private fun Integrate(deltaTime: Float, next: rigidBodyPState_s) {
            val position = idVec3(current.i.position)
            current.i.position.plusAssign(centerOfMass.times(current.i.orientation))
            current.i.orientation.TransposeSelf()
            val newState = next.i.toFloatArr()
            integrator.Evaluate(current.i.toFloatArr(), newState, 0.0f, deltaTime)
            next.i.fromFloatArr(newState)
            next.i.orientation.OrthoNormalizeSelf()

            // apply gravity
            next.i.linearMomentum.plusAssign(gravityVector.times(mass * deltaTime))
            current.i.orientation.TransposeSelf()
            next.i.orientation.TransposeSelf()
            current.i.position.set(position)
            next.i.position.minusAssign(centerOfMass.times(next.i.orientation))
            next.atRest = current.atRest
        }

        /*
         ================
         idPhysics_RigidBody::CheckForCollisions

         Check for collisions between the current and next state.
         If there is a collision the next state is set to the state at the moment of impact.
         ================
         */
        private fun CheckForCollisions(deltaTime: Float, next: rigidBodyPState_s, collision: trace_s): Boolean {
//#define TEST_COLLISION_DETECTION
            val axis = idMat3()
            val rotation: idRotation
            var collided = false
            var startsolid = false
            if (TEST_COLLISION_DETECTION) {
                if (Game_local.gameLocal.clip.Contents(
                        current.i.position, clipModel, current.i.orientation, clipMask, self
                    ) != 0
                ) {
                    startsolid = true
                }
            }

            // Save desired end position before Motion() call.
            // We need this to compute the actual translational fraction.
            val desiredEndPos = idVec3(next.i.position)

            idMat3.TransposeMultiply(current.i.orientation, next.i.orientation, axis)
            rotation = axis.ToRotation()

            // In C++, zero angular momentum produces an exact identity matrix, so
            // ToRotation() returns angle exactly 0.0f and Motion() takes the pure
            // translation path. In Kotlin, OrthoNormalizeSelf() introduces tiny
            // floating-point drift in the orientation matrix, causing ToRotation()
            // to produce a tiny but nonzero angle (e.g. 1e-8). This makes Motion()
            // take the combined translation+rotation path, which returns
            // Max(transFrac, rotFrac) — inflating the fraction and preventing the
            // collision damping check (fraction < 0.0001) from ever firing.
            // Snap negligible rotation angles to zero so Motion() takes the pure
            // translation path, matching C++ behavior.
            if (idMath.Fabs(rotation.GetAngle()) < 1e-3f) {
                rotation.SetAngle(0.0f)
            }

            rotation.SetOrigin(current.i.position)

            // if there was a collision
            if (Game_local.gameLocal.clip.Motion(
                    collision,
                    current.i.position,
                    next.i.position,
                    rotation,
                    clipModel,
                    current.i.orientation,
                    clipMask,
                    self
                )
            ) {
                // Clip.Motion() sets collision.fraction = Max(translationalFraction, rotationalFraction).
                // When translation collides (fraction ≈ 0) but rotation doesn't (fraction = 1.0),
                // the returned fraction is 1.0, hiding the translational collision. This breaks
                // the damping check in CollisionImpulse (fraction < 0.0001). Compute the actual
                // translational fraction from endpoint positions and use the minimum.
                val totalTranslationSqr = desiredEndPos.minus(current.i.position).LengthSqr()
                val motionFrac = collision.fraction
                if (totalTranslationSqr > idMath.FLT_EPSILON) {
                    val actualTranslation = collision.endpos.minus(current.i.position).Length()
                    val totalTranslation = idMath.Sqrt(totalTranslationSqr)
                    val translationalFraction = actualTranslation / totalTranslation
                    if (translationalFraction < collision.fraction) {
                        collision.fraction = translationalFraction
                    }
                }

                // DIAGNOSTIC: Log collision fraction details
                if (RB_DEBUG_REST) {
                    val totalDist = desiredEndPos.minus(current.i.position).Length()
                    val actualDist = collision.endpos.minus(current.i.position).Length()
                    Game_local.gameLocal.Printf(
                        "  COLLISION [%s]: motionFrac=%.4f transFrac=%.4f totalDist=%.3f actualDist=%.3f\n",
                        self!!.name, motionFrac, collision.fraction, totalDist, actualDist
                    )
                }

                // set the next state to the state at the moment of impact
                next.i.position.set(collision.endpos)
                next.i.orientation.set(collision.endAxis)
                next.i.linearMomentum.set(current.i.linearMomentum)
                next.i.angularMomentum.set(current.i.angularMomentum)
                collided = true
            }
            if (TEST_COLLISION_DETECTION) {
                if (Game_local.gameLocal.clip.Contents(
                        next.i.position, clipModel, next.i.orientation, clipMask, self
                    ) != 0
                ) {
                    if (!startsolid) {
                    }
                }
            }
            return collided
        }

        /*
         ================
         idPhysics_RigidBody::CollisionImpulse

         Calculates the collision impulse using the velocity relative to the collision object.
         The current state should be set to the moment of impact.
         ================
         */
        private fun CollisionImpulse(collision: trace_s, impulse: idVec3): Boolean {
            val r = idVec3()
            val linearVelocity = idVec3()
            val angularVelocity = idVec3()
            val velocity = idVec3()
            val inverseWorldInertiaTensor: idMat3
            val impulseNumerator: Float
            var impulseDenominator: Float
            val vel: Float
            val info: impactInfo_s
            val ent: idEntity?

            // get info from other entity involved
            ent = Game_local.gameLocal.entities[collision.c.entityNum]
            if (ent == null) {
                return false
            }
            info = ent.GetImpactInfo(self, collision.c.id, collision.c.point)

            // collision point relative to the body center of mass
            r.set(collision.c.point.minus(current.i.position.plus(centerOfMass.times(current.i.orientation))))
            // the velocity at the collision point
            linearVelocity.set(current.i.linearMomentum.times(inverseMass))
            inverseWorldInertiaTensor =
                current.i.orientation.Transpose().times(inverseInertiaTensor.times(current.i.orientation))
            angularVelocity.set(inverseWorldInertiaTensor.times(current.i.angularMomentum))
            velocity.set(linearVelocity.plus(angularVelocity.Cross(r)))
            // subtract velocity of other entity
            velocity.minusAssign(info.velocity)

            // velocity in normal direction
            vel = velocity.times(collision.c.normal)

            // if no movement at all don't blow up — must execute before the
            // separating-contact guard below, otherwise stuck objects (frac ≈ 0)
            // with vel >= 0 never get their momentum damped.
            if (collision.fraction < 0.0001f) {
                current.i.linearMomentum.timesAssign(0.5f)
                current.i.angularMomentum.timesAssign(0.5f)
            }

            // If the contact point is already separating from the surface (vel >= 0),
            // no collision impulse is needed. In C++, this case is rare because objects
            // with zero/low angular momentum take Motion()'s pure translation path, which
            // doesn't report collisions for separating objects. In Kotlin, the combined
            // translation+rotation path can report rotational collisions even when the
            // object is translating away. Without this guard, the STOP_SPEED impulse
            // (applied when vel > -STOP_SPEED) injects energy every frame, creating a
            // feedback loop that causes objects to fly.
            if (vel >= 0.0f) {
                impulse.Zero()
                return self!!.Collide(collision, velocity)
            }

            impulseNumerator = if (vel > -STOP_SPEED) {
                STOP_SPEED
            } else {
                -(1.0f + bouncyness) * vel
            }
            impulseDenominator = inverseMass + inverseWorldInertiaTensor.times(r.Cross(collision.c.normal)).Cross(r)
                .times(collision.c.normal)
            if (info.invMass != 0.0f) {
                impulseDenominator += info.invMass + info.invInertiaTensor.times(info.position.Cross(collision.c.normal))
                    .Cross(info.position).times(collision.c.normal)
            }
            impulse.set(collision.c.normal.times(impulseNumerator / impulseDenominator))

            // update linear and angular momentum with impulse
            current.i.linearMomentum.plusAssign(impulse)
            current.i.angularMomentum.plusAssign(r.Cross(impulse))

            // DIAGNOSTIC: Log collision impulse details
            if (RB_DEBUG_REST) {
                val postLinSpeed = current.i.linearMomentum.times(inverseMass).Length()
                if (postLinSpeed > STOP_SPEED * 2.0f) {
                    Game_local.gameLocal.Printf(
                        "  IMPULSE [%s]: vel=%.2f frac=%.6f impNum=%.2f impDen=%.4f impMag=%.2f postLinSpd=%.2f postAngMom=%.2f\n",
                        self!!.name, vel, collision.fraction,
                        impulseNumerator, impulseDenominator,
                        impulse.Length(), postLinSpeed,
                        current.i.angularMomentum.Length()
                    )
                }
            }

            // callback to self to let the entity know about the collision
            return self!!.Collide(collision, velocity)
        }

        /*
         ================
         idPhysics_RigidBody::ContactFriction

         Does not solve friction for multiple simultaneous contacts but applies contact friction in isolation.
         Uses absolute velocity at the contact points instead of the velocity relative to the contact object.
         ================
         */
        private fun ContactFriction(deltaTime: Float) {
            var i: Int
            var magnitude: Float
            var impulseNumerator: Float
            var impulseDenominator: Float
            val inverseWorldInertiaTensor: idMat3
            val linearVelocity = idVec3()
            val angularVelocity = idVec3()
            val massCenter = idVec3()
            val r = idVec3()
            val velocity = idVec3()
            val normal = idVec3()
            val impulse = idVec3()
            val normalVelocity = idVec3()
            inverseWorldInertiaTensor =
                current.i.orientation.Transpose().times(inverseInertiaTensor.times(current.i.orientation))
            massCenter.set(current.i.position.plus(centerOfMass.times(current.i.orientation)))
            i = 0
            while (i < contacts.Num()) {
                r.set(contacts[i].point.minus(massCenter))

                // calculate velocity at contact point
                linearVelocity.set(current.i.linearMomentum.times(inverseMass))
                angularVelocity.set(inverseWorldInertiaTensor.times(current.i.angularMomentum))
                velocity.set(linearVelocity.plus(angularVelocity.Cross(r)))

                // velocity along normal vector
                normalVelocity.set(contacts[i].normal.times(velocity.times(contacts[i].normal)))

                // calculate friction impulse
                normal.set(velocity.minus(normalVelocity).unaryMinus())
                magnitude = normal.Normalize()
                impulseNumerator = contactFriction * magnitude
                impulseDenominator =
                    inverseMass + inverseWorldInertiaTensor.times(r.Cross(normal)).Cross(r).times(normal)
                impulse.set(normal.times(impulseNumerator / impulseDenominator))

                // apply friction impulse
                current.i.linearMomentum.plusAssign(impulse)
                current.i.angularMomentum.plusAssign(r.Cross(impulse))

                // if moving towards the surface at the contact point
                if (normalVelocity.times(contacts[i].normal) < 0.0f) {
                    // calculate impulse
                    normal.set(normalVelocity.unaryMinus())
                    impulseNumerator = normal.Normalize()
                    impulseDenominator =
                        inverseMass + inverseWorldInertiaTensor.times(r.Cross(normal)).Cross(r).times(normal)
                    impulse.set(normal.times(impulseNumerator / impulseDenominator))

                    // apply impulse
                    current.i.linearMomentum.plusAssign(impulse)
                    current.i.angularMomentum.plusAssign(r.Cross(impulse))
                }
                i++
            }
        }

        /*
         ================
         idPhysics_RigidBody::DropToFloorAndRest

         Drops the object straight down to the floor and verifies if the object is at rest on the floor.
         ================
         */
        private fun DropToFloorAndRest() {
            val down = idVec3()
            val tr = trace_s()
            if (testSolid) {
                testSolid = false
                if (Game_local.gameLocal.clip.Contents(
                        current.i.position, clipModel, current.i.orientation, clipMask, self
                    ) != 0
                ) {
                    Game_local.gameLocal.DWarning(
                        "rigid body in solid for entity '%s' type '%s' at (%s)",
                        self!!.name,
                        self!!.GetType().name,
                        current.i.position.ToString(0)
                    )
                    Rest()
                    dropToFloor = false
                    return
                }
            }


            // put the body on the floor
            down.set(current.i.position + gravityNormal * 128.0f)
            Game_local.gameLocal.clip.Translation(
                tr, current.i.position, down, clipModel, current.i.orientation, clipMask, self
            )
            current.i.position.set(tr.endpos)
            clipModel!!.Link(Game_local.gameLocal.clip, self, clipModel!!.GetId(), tr.endpos, current.i.orientation)

            // if on the floor already
            if (tr.fraction == 0.0f) {
                // test if we are really at rest
                EvaluateContacts()
                if (!TestIfAtRest()) {
                    Game_local.gameLocal.DWarning(
                        "rigid body not at rest for entity '%s' type '%s' at (%s)",
                        self!!.name,
                        self!!.GetType().name,
                        current.i.position.ToString(0)
                    )
                }
                Rest()
                dropToFloor = false
            } else if (IsOutsideWorld()) {
                Game_local.gameLocal.Warning(
                    "rigid body outside world bounds for entity '%s' type '%s' at (%s)",
                    self!!.name,
                    self!!.GetType().name,
                    current.i.position.ToString(0)
                )
                Rest()
                dropToFloor = false
            }
        }

        /*
         ================
         idPhysics_RigidBody::TestIfAtRest

         Returns true if the body is considered at rest.
         Does not catch all cases where the body is at rest but is generally good enough.
         ================
         */
        private fun TestIfAtRest(): Boolean {
            var i: Int
            val gv: Float
            val v = idVec3()
            val av = idVec3()
            val normal = idVec3()
            val point = idVec3()
            val inverseWorldInertiaTensor: idMat3
            val contactWinding = idFixedWinding()
            if (current.atRest >= 0) {
                return true
            }

            // need at least 3 contact points to come to rest
            if (contacts.Num() < 3) {
                if (RB_DEBUG_REST) {
                    Game_local.gameLocal.Printf(
                        "  REST_FAIL [%s]: only %d contacts (need 3)\n",
                        self!!.name,
                        contacts.Num()
                    )
                }
                return false
            }

            // get average contact plane normal
            normal.Zero()
            i = 0
            while (i < contacts.Num()) {
                normal.plusAssign(contacts[i].normal)
                i++
            }
            normal.divAssign(contacts.Num().toFloat())
            normal.Normalize()

            // if on a too steep surface
            if (normal.times(gravityNormal) > -0.7f) {
                if (RB_DEBUG_REST) {
                    Game_local.gameLocal.Printf(
                        "  REST_FAIL [%s]: too steep (%.3f > -0.7)\n",
                        self!!.name,
                        normal.times(gravityNormal)
                    )
                }
                return false
            }

            // create bounds for contact points
            contactWinding.Clear()
            i = 0
            while (i < contacts.Num()) {

                // project point onto plane through origin orthogonal to the gravity
                point.set(
                    contacts[i].point.minus(
                        gravityNormal.times(
                            contacts[i].point.times(
                                gravityNormal
                            )
                        )
                    )
                )
                contactWinding.AddToConvexHull(point, gravityNormal)
                i++
            }

            // need at least 3 contact points to come to rest
            if (contactWinding.GetNumPoints() < 3) {
                if (RB_DEBUG_REST) {
                    Game_local.gameLocal.Printf(
                        "  REST_FAIL [%s]: convex hull has %d points (need 3), %d contacts\n",
                        self!!.name,
                        contactWinding.GetNumPoints(),
                        contacts.Num()
                    )
                }
                return false
            }

            // center of mass in world space
            point.set(current.i.position.plus(centerOfMass.times(current.i.orientation)))
            point.minusAssign(gravityNormal.times(point.times(gravityNormal)))

            // if the point is not inside the winding
            if (!contactWinding.PointInside(gravityNormal, point, 0.0f)) {
                if (RB_DEBUG_REST) {
                    Game_local.gameLocal.Printf("  REST_FAIL [%s]: CoM not inside contact winding\n", self!!.name)
                }
                return false
            }

            // linear velocity of body
            v.set(current.i.linearMomentum.times(inverseMass))
            // linear velocity in gravity direction
            gv = v.times(gravityNormal)
            // linear velocity orthogonal to gravity direction
            v.minusAssign(gravityNormal.times(gv))

            // if too much velocity orthogonal to gravity direction
            if (v.Length() > STOP_SPEED) {
                if (RB_DEBUG_REST) {
                    Game_local.gameLocal.Printf(
                        "  REST_FAIL [%s]: lateral vel %.3f > %.1f\n",
                        self!!.name,
                        v.Length(),
                        STOP_SPEED
                    )
                }
                return false
            }
            // if too much velocity in gravity direction
            if (gv > 2.0f * STOP_SPEED || gv < -2.0f * STOP_SPEED) {
                if (RB_DEBUG_REST) {
                    Game_local.gameLocal.Printf(
                        "  REST_FAIL [%s]: gravity vel %.3f outside [%.1f, %.1f]\n",
                        self!!.name,
                        gv,
                        -2.0f * STOP_SPEED,
                        2.0f * STOP_SPEED
                    )
                }
                return false
            }

            // calculate rotational velocity
            inverseWorldInertiaTensor =
                current.i.orientation.times(inverseInertiaTensor.times(current.i.orientation.Transpose()))
            av.set(inverseWorldInertiaTensor.times(current.i.angularMomentum))

            // if too much rotational velocity
            if (av.LengthSqr() > STOP_SPEED) {
                if (RB_DEBUG_REST) {
                    Game_local.gameLocal.Printf(
                        "  REST_FAIL [%s]: angular vel sqr %.3f > %.1f\n",
                        self!!.name,
                        av.LengthSqr(),
                        STOP_SPEED
                    )
                }
                return false
            }
            return true
        }

        /*
         ================
         idPhysics_RigidBody::Rest
         ================
         */
        private fun Rest() {
            current.atRest = Game_local.gameLocal.time
            current.i.linearMomentum.Zero()
            current.i.angularMomentum.Zero()
            self!!.BecomeInactive(TH_PHYSICS)
        }

        /*
         ================
         idPhysics_RigidBody::DebugDraw
         ================
         */
        private fun DebugDraw() {
            if (SysCvar.rb_showBodies.GetBool() || SysCvar.rb_showActive.GetBool() && current.atRest < 0) {
                collisionModelManager.DrawModel(
                    clipModel!!.Handle(), clipModel!!.GetOrigin(), clipModel!!.GetAxis(), vec3_origin, 0.0f
                )
            }
            if (SysCvar.rb_showMass.GetBool()) {
                Game_local.gameRenderWorld!!.DrawText(
                    Str.va("\n%1.2f", mass),
                    current.i.position,
                    0.08f,
                    colorCyan,
                    Game_local.gameLocal.GetLocalPlayer()!!.viewAngles.ToMat3(),
                    1
                )
            }
            if (SysCvar.rb_showInertia.GetBool()) {
                val I = inertiaTensor
                Game_local.gameRenderWorld!!.DrawText(
                    Str.va(
                        "\n\n\n( %.1f %.1f %.1f )\n( %.1f %.1f %.1f )\n( %.1f %.1f %.1f )",
                        I[0].x,
                        I[0].y,
                        I[0].z,
                        I[1].x,
                        I[1].y,
                        I[1].z,
                        I[2].x,
                        I[2].y,
                        I[2].z
                    ),
                    current.i.position,
                    0.05f,
                    colorCyan,
                    Game_local.gameLocal.GetLocalPlayer()!!.viewAngles.ToMat3(),
                    1
                )
            }
            if (SysCvar.rb_showVelocity.GetBool()) {
                DrawVelocity(clipModel!!.GetId(), 0.1f, 4.0f)
            }
        }

        private class rigidBodyDerivatives_s(derivatives: FloatArray) {
            val angularMatrix: idMat3 = idMat3()
            val force: idVec3 = idVec3()
            val linearVelocity: idVec3 = idVec3()
            val torque: idVec3 = idVec3()
            fun toFloats(): FloatArray {
                val buffer = FloatBuffer.allocate(BYTES / java.lang.Float.BYTES)
                buffer.put(linearVelocity.ToFloatPtr()).put(angularMatrix.ToFloatPtr()).put(force.ToFloatPtr())
                    .put(torque.ToFloatPtr())
                return buffer.array()
            }

            companion object {
                val BYTES: Int = idVec3.BYTES + idMat3.BYTES + idVec3.BYTES + idVec3.BYTES
            }

            init {
                val b = FloatBuffer.wrap(derivatives)
                if (b.hasRemaining()) {
                    linearVelocity.set(idVec3(b.get(), b.get(), b.get()))
                }
                if (b.hasRemaining()) {
                    angularMatrix.set(
                        idMat3(
                            b.get(), b.get(), b.get(), b.get(), b.get(), b.get(), b.get(), b.get(), b.get()
                        )
                    )
                }
                if (b.hasRemaining()) {
                    force.set(idVec3(b.get(), b.get(), b.get()))
                }
                if (b.hasRemaining()) {
                    torque.set(idVec3(b.get(), b.get(), b.get()))
                }
            }
        }

        /*
         ================
         RigidBodyDerivatives
         ================
         */
        /*friend*/   class RigidBodyDerivatives : deriveFunction_t() {
            override fun run(t: Float, clientData: Any, state: FloatArray, derivatives: FloatArray) {
                val p = clientData as idPhysics_RigidBody
                val s = rigidBodyIState_s(state)
                // NOTE: this struct should be build conform rigidBodyIState_t
                val d = rigidBodyDerivatives_s(derivatives)
                val angularVelocity = idVec3()
                val inverseWorldInertiaTensor: idMat3
                inverseWorldInertiaTensor = s.orientation.times(p.inverseInertiaTensor.times(s.orientation.Transpose()))
                angularVelocity.set(inverseWorldInertiaTensor.times(s.angularMomentum))
                // derivatives
                d.linearVelocity.set(s.linearMomentum.times(p.inverseMass))
                d.angularMatrix.set(idMat3.SkewSymmetric(angularVelocity).times(s.orientation))
                d.force.set(s.linearMomentum.times(-p.linearFriction).plus(p.current.externalForce))
                d.torque.set(s.angularMomentum.times(-p.angularFriction).plus(p.current.externalTorque))
                System.arraycopy(d.toFloats(), 0, derivatives, 0, derivatives.size)
            }

            companion object {
                val INSTANCE: deriveFunction_t = RigidBodyDerivatives()
            }
        }

        companion object {
            val Type = idTypeInfo("idPhysics_RigidBody", "idPhysics_Base") { idPhysics_RigidBody() }
            // CLASS_PROTOTYPE( idPhysics_RigidBody );
            const val MAX_INERTIA_SCALE = 10.0f
            val curAngularVelocity: idVec3 = idVec3()
            val curLinearVelocity: idVec3 = idVec3()
        }

        override fun GetType(): idTypeInfo = Type
        override fun CreateInstance(): idClass = idPhysics_RigidBody()

        /*
         ================
         idPhysics_RigidBody::idPhysics_RigidBody
         ================
         */
        init {

            // set default rigid body properties
            SetClipMask(Game_local.MASK_SOLID)
            SetBouncyness(0.6f)
            SetFriction(0.6f, 0.6f, 0.0f)
            clipModel = null

//	memset( &current, 0, sizeof( current ) );
            current = rigidBodyPState_s()
            current.atRest = -1
            current.lastTimeStep = UsercmdGen.USERCMD_MSEC.toFloat()
            current.i = rigidBodyIState_s()
            current.i.orientation.set(idMat3.getMat3_identity())
            // FIX: C++ struct assignment does value copy; Kotlin = creates reference alias
            saved = current.copy()
            mass = 1.0f
            inverseMass = 1.0f
            centerOfMass = idVec3()
            inertiaTensor = idMat3.getMat3_identity()
            inverseInertiaTensor.set(idMat3.getMat3_identity())

            // use the least expensive euler integrator
            integrator =
                idODE_Euler(rigidBodyIState_s.BYTES / java.lang.Float.BYTES, RigidBodyDerivatives.INSTANCE, this)
            dropToFloor = false
            noImpact = false
            noContact = false
            hasMaster = false
            isOrientated = false
            if (RB_TIMINGS) {
                lastTimerReset = 0
            }
        }
    }
}
package neo.Game.Physics

import neo.CM.CollisionModel.contactInfo_t
import neo.CM.CollisionModel.trace_s
import neo.CM.CollisionModel_local
import neo.Game.*
import neo.Game.Entity.idEntity
import neo.Game.GameSys.SaveGame.idRestoreGame
import neo.Game.GameSys.SaveGame.idSaveGame
import neo.Game.GameSys.SysCvar
import neo.Game.Physics.Clip.idClipModel
import neo.Game.Physics.Physics.impactInfo_s
import neo.Game.Physics.Physics_Base.idPhysics_Base
import neo.framework.UsercmdGen
import neo.idlib.BV.Bounds.idBounds
import neo.idlib.BitMsg.idBitMsgDelta
import neo.idlib.Lib
import neo.idlib.Text.Str
import neo.idlib.Timer.idTimer
import neo.idlib.containers.CFloat
import neo.idlib.containers.CInt
import neo.idlib.geometry.Winding.idFixedWinding
import neo.idlib.math.*
import neo.idlib.math.Math_h.idMath
import neo.idlib.math.Matrix.idMat3
import neo.idlib.math.Ode.*
import neo.idlib.math.Quat.idCQuat
import neo.idlib.math.Rotation.idRotation
import neo.idlib.math.Vector.idVec3
import neo.idlib.math.Vector.idVec6
import java.nio.FloatBuffer

/**
 *
 */
object Physics_RigidBody {
    const val RB_FORCE_MAX = 1e20f
    val RB_FORCE_EXPONENT_BITS = idMath.BitsForInteger(idMath.BitsForFloat(RB_FORCE_MAX)) + 1
    const val RB_FORCE_TOTAL_BITS = 16
    val RB_FORCE_MANTISSA_BITS = RB_FORCE_TOTAL_BITS - 1 - RB_FORCE_EXPONENT_BITS
    const val RB_MOMENTUM_MAX = 1e20f
    val RB_MOMENTUM_EXPONENT_BITS = idMath.BitsForInteger(idMath.BitsForFloat(RB_MOMENTUM_MAX)) + 1
    const val RB_MOMENTUM_TOTAL_BITS = 16
    val RB_MOMENTUM_MANTISSA_BITS =
        RB_MOMENTUM_TOTAL_BITS - 1 - RB_MOMENTUM_EXPONENT_BITS

    /*
     ===================================================================================

     Rigid body physics

     Employs an impulse based dynamic simulation which is not very accurate but
     relatively fast and still reliable due to the continuous collision detection.

     ===================================================================================
     */
    const val RB_VELOCITY_MAX = 16000f
    val RB_VELOCITY_EXPONENT_BITS = idMath.BitsForInteger(idMath.BitsForFloat(RB_VELOCITY_MAX)) + 1
    const val RB_VELOCITY_TOTAL_BITS = 16
    val RB_VELOCITY_MANTISSA_BITS =
        RB_VELOCITY_TOTAL_BITS - 1 - RB_VELOCITY_EXPONENT_BITS

    //
    const val STOP_SPEED = 10.0f

    //
    private const val RB_TIMINGS = false
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
        state.atRest = atRest._val
        state.lastTimeStep = lastTimeStep._val
    }

    class rigidBodyIState_s {
        val angularMomentum // rotational momentum relative to center of mass
                : idVec3
        val linearMomentum // translational momentum relative to center of mass
                : idVec3
        var orientation // orientation of trace model
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
            fromFloats(state)
        }

        constructor(r: rigidBodyIState_s) {
            position = idVec3(r.position)
            orientation = idMat3(r.orientation)
            linearMomentum = idVec3(r.linearMomentum)
            angularMomentum = idVec3(r.angularMomentum)
        }

        fun toFloats(): FloatArray {
            val buffer = FloatBuffer.allocate(BYTES / java.lang.Float.BYTES)
            buffer.put(position.ToFloatPtr())
                .put(orientation.ToFloatPtr())
                .put(linearMomentum.ToFloatPtr())
                .put(angularMomentum.ToFloatPtr())
            return buffer.array()
        }

        fun fromFloats(state: FloatArray) {
            val b = FloatBuffer.wrap(state)
            if (b.hasRemaining()) {
                position.set(idVec3(b.get(), b.get(), b.get()))
            }
            if (b.hasRemaining()) {
                orientation.set(
                    idMat3(
                        b.get(), b.get(), b.get(),
                        b.get(), b.get(), b.get(),
                        b.get(), b.get(), b.get()
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
            val BYTES: Int = (idVec3.BYTES
                    + idMat3.BYTES
                    + idVec3.BYTES
                    + idVec3.BYTES)
        }
    }

    class rigidBodyPState_s {
        var atRest // set when simulation is suspended
                = 0
        val externalForce // external force relative to center of mass
                : idVec3
        val externalTorque // external torque relative to center of mass
                : idVec3
        var i // state used for integration
                : rigidBodyIState_s = rigidBodyIState_s()
        var lastTimeStep // length of last time step
                = 0f
        var localAxis // axis relative to master
                : idMat3
        val localOrigin // origin relative to master
                : idVec3
        var pushVelocity // push velocity
                : idVec6

        constructor() {
            localOrigin = idVec3()
            localAxis = idMat3()
            pushVelocity = idVec6()
            externalForce = idVec3()
            externalTorque = idVec3()
        }

        constructor(r: rigidBodyPState_s) {
            atRest = r.atRest
            lastTimeStep = r.lastTimeStep
            localOrigin = idVec3(r.localOrigin)
            localAxis = idMat3(r.localAxis)
            pushVelocity = idVec6(r.pushVelocity)
            externalForce = idVec3(r.externalForce)
            externalTorque = idVec3(r.externalTorque)
            i = rigidBodyIState_s(r.i)
        }
    }

    class idPhysics_RigidBody : idPhysics_Base() {
        /*
         ================
         idPhysics_RigidBody::DropToFloorAndRest

         Drops the object straight down to the floor and verifies if the object is at rest on the floor.
         ================
         */
        private val centerOfMass // center of mass of trace model
                : idVec3
        private val inertiaTensor // mass distribution
                : idMat3

        //
        private val integrator // integrator
                : idODE
        private var angularFriction // rotational friction
                = 0f
        private var bouncyness // bouncyness
                = 0f
        private var clipModel // clip model used for collision detection
                : idClipModel?
        private var contactFriction // friction with contact surfaces
                = 0f

        // state of the rigid body
        private var current: rigidBodyPState_s = rigidBodyPState_s()
        private var dropToFloor // true if dropping to the floor and putting to rest
                : Boolean

        //
        // master
        private var hasMaster: Boolean
        private var inverseInertiaTensor // inverse inertia tensor
                : idMat3
        private var inverseMass // 1 / mass
                : Float
        private var isOrientated: Boolean

        //
        // rigid body properties
        private var linearFriction // translational friction
                = 0f

        //
        // derived properties
        private var mass // mass of body
                : Float

        //
        //
        private var noContact // if true do not determine contacts and no contact friction
                : Boolean
        private var noImpact // if true do not activate when another object collides
                : Boolean
        private var saved: rigidBodyPState_s
        private var testSolid // true if testing for solid when dropping to the floor
                = false

        // ~idPhysics_RigidBody();
        override fun _deconstructor() {
            if (clipModel != null) {
                idClipModel.delete(clipModel!!)
            }
            //            delete integrator;
            super._deconstructor()
        }

        override fun Save(savefile: idSaveGame) {
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

        override fun Restore(savefile: idRestoreGame) {
            idPhysics_RigidBody_RestorePState(savefile, current)
            idPhysics_RigidBody_RestorePState(savefile, saved)
            linearFriction = savefile.ReadFloat()
            angularFriction = savefile.ReadFloat()
            contactFriction = savefile.ReadFloat()
            bouncyness = savefile.ReadFloat()
            savefile.ReadClipModel(clipModel!!)
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

        // initialisation
        fun SetFriction(linear: Float, angular: Float, contact: Float) {
            if (linear < 0.0f || linear > 1.0f || angular < 0.0f || angular > 1.0f || contact < 0.0f || contact > 1.0f) {
                return
            }
            linearFriction = linear
            angularFriction = angular
            contactFriction = contact
        }

        fun SetBouncyness(b: Float) {
            if (b < 0.0f || b > 1.0f) {
                return
            }
            bouncyness = b
        }

        // same as above but drop to the floor first
        fun DropToFloor() {
            dropToFloor = true
            testSolid = true
        }

        // no contact determination and contact friction
        fun NoContact() {
            noContact = true
        }

        // enable/disable activation by impact
        fun EnableImpact() {
            noImpact = false
        }

        fun DisableImpact() {
            noImpact = true
        }

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
            if (mass._val <= 0.0f || Math_h.FLOAT_IS_NAN(mass._val)) {
                Game_local.gameLocal.Warning(
                    "idPhysics_RigidBody::SetClipModel: invalid mass for entity '%s' type '%s'",
                    self!!.name, self!!.GetType().name
                )
                mass._val = 1.0f
                centerOfMass.Zero()
                inertiaTensor.Identity()
            }

            // check whether or not the inertia tensor is balanced
            minIndex = Math_h.Min3Index(inertiaTensor[0, 0], inertiaTensor[1, 1], inertiaTensor[2, 2])
            inertiaScale.Identity()
            inertiaScale.set(0, 0, inertiaTensor[0, 0] / inertiaTensor[minIndex, minIndex])
            inertiaScale.set(1, 1, inertiaTensor[1, 1] / inertiaTensor[minIndex, minIndex])
            inertiaScale.set(2, 2, inertiaTensor[2, 2] / inertiaTensor[minIndex, minIndex])
            if (inertiaScale[0, 0] > MAX_INERTIA_SCALE || inertiaScale[1, 1] > MAX_INERTIA_SCALE || inertiaScale[2, 2] > MAX_INERTIA_SCALE
            ) {
                Game_local.gameLocal.DWarning(
                    "idPhysics_RigidBody::SetClipModel: unbalanced inertia tensor for entity '%s' type '%s'",
                    self!!.name, self!!.GetType().name
                )
                val min = inertiaTensor[minIndex, minIndex] * MAX_INERTIA_SCALE
                inertiaScale.set(
                    (minIndex + 1) % 3,
                    (minIndex + 1) % 3,
                    min / inertiaTensor[(minIndex + 1) % 3, (minIndex + 1) % 3]
                )
                inertiaScale.set(
                    (minIndex + 2) % 3,
                    (minIndex + 2) % 3,
                    min / inertiaTensor[(minIndex + 2) % 3, (minIndex + 2) % 3]
                )
                inertiaTensor.timesAssign(inertiaScale)
            }
            inverseMass = 1.0f / mass._val
            inverseInertiaTensor = inertiaTensor.Inverse().times(1.0f / 6.0f)
            current.i.linearMomentum.Zero()
            current.i.angularMomentum.Zero()
        }

        override fun GetClipModel(id: Int /*= 0*/): idClipModel? {
            return clipModel
        }

        override fun GetNumClipModels(): Int {
            return 1
        }

        override fun SetMass(mass: Float, id: Int /*= -1*/) {
            assert(mass > 0.0f)
            inertiaTensor.timesAssign(mass / this.mass)
            inverseInertiaTensor = inertiaTensor.Inverse().times(1.0f / 6.0f)
            this.mass = mass
            inverseMass = 1.0f / mass
        }

        override fun GetMass(id: Int /*= -1*/): Float {
            return mass
        }

        override fun SetContents(contents: Int, id: Int /*= -1*/) {
            clipModel!!.SetContents(contents)
        }

        override fun GetContents(id: Int /*= -1*/): Int {
            return clipModel!!.GetContents()
        }

        override fun GetBounds(id: Int /*= -1*/): idBounds {
            return clipModel!!.GetBounds()
        }

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
            val oldAxis: idMat3
            val masterAxis = idMat3()
            val timeStep: Float
            val collided: Boolean
            var cameToRest = false
            timeStep = Math_h.MS2SEC(timeStepMSec.toFloat())
            current.lastTimeStep = timeStep
            if (hasMaster) {
                oldAxis = idMat3(current.i.orientation)
                self!!.GetMasterPosition(masterOrigin, masterAxis)
                current.i.position.set(masterOrigin.plus(current.localOrigin.times(masterAxis)))
                if (isOrientated) {
                    current.i.orientation.set(current.localAxis.times(masterAxis))
                } else {
                    current.i.orientation.set(current.localAxis)
                }
                clipModel!!.Link(
                    Game_local.gameLocal.clip,
                    self,
                    clipModel!!.GetId(),
                    current.i.position,
                    current.i.orientation
                )
                current.i.linearMomentum.set(current.i.position.minus(oldOrigin).div(timeStep).times(mass))
                current.i.angularMomentum.set(
                    inertiaTensor.times(
                        current.i.orientation.times(oldAxis.Transpose()).ToAngularVelocity().div(timeStep)
                    )
                )
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
            next = rigidBodyPState_s(current)

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
            current = rigidBodyPState_s(next)
            if (collided) {
                // apply collision impulse
                if (CollisionImpulse(collision, impulse)) {
                    current.atRest = Game_local.gameLocal.time
                }
            }

            // update the position of the clip model
            clipModel!!.Link(
                Game_local.gameLocal.clip,
                self,
                clipModel!!.GetId(),
                current.i.position,
                current.i.orientation
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
                    self!!.name, self!!.GetType().name, current.i.position.ToString(0)
                )
                Rest()
            }
            if (RB_TIMINGS) {
                timer_total.Stop()
                if (SysCvar.rb_showTimings.GetInteger() == 1) {
                    Game_local.gameLocal.Printf(
                        "%12s: t %1.4f cd %1.4f\n",
                        self!!.name,
                        timer_total.Milliseconds(), timer_collision.Milliseconds()
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

        override fun UpdateTime(endTimeMSec: Int) {}
        override fun GetTime(): Int {
            return Game_local.gameLocal.time
        }

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

        override fun Activate() {
            current.atRest = -1
            self!!.BecomeActive(Entity.TH_PHYSICS)
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

        override fun IsAtRest(): Boolean {
            return current.atRest >= 0
        }

        override fun GetRestStartTime(): Int {
            return current.atRest
        }

        override fun IsPushable(): Boolean {
            return !noImpact && !hasMaster
        }

        override fun SaveState() {
            saved = current
        }

        override fun RestoreState() {
            current = saved
            clipModel!!.Link(
                Game_local.gameLocal.clip,
                self,
                clipModel!!.GetId(),
                current.i.position,
                current.i.orientation
            )
            EvaluateContacts()
        }

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
                Game_local.gameLocal.clip,
                self,
                clipModel!!.GetId(),
                current.i.position,
                clipModel!!.GetAxis()
            )
            Activate()
        }

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
                Game_local.gameLocal.clip,
                self,
                clipModel!!.GetId(),
                clipModel!!.GetOrigin(),
                current.i.orientation
            )
            Activate()
        }

        override fun Translate(translation: idVec3, id: Int /*= -1*/) {
            current.localOrigin.plusAssign(translation)
            current.i.position.plusAssign(translation)
            clipModel!!.Link(
                Game_local.gameLocal.clip,
                self,
                clipModel!!.GetId(),
                current.i.position,
                clipModel!!.GetAxis()
            )
            Activate()
        }

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
                Game_local.gameLocal.clip,
                self,
                clipModel!!.GetId(),
                current.i.position,
                current.i.orientation
            )
            Activate()
        }

        override fun GetOrigin(id: Int /*= 0*/): idVec3 {
            return current.i.position
        }

        override fun GetAxis(id: Int /*= 0*/): idMat3 {
            return current.i.orientation
        }

        override fun SetLinearVelocity(newLinearVelocity: idVec3, id: Int /*= 0*/) {
            current.i.linearMomentum.set(newLinearVelocity.times(mass))
            Activate()
        }

        override fun SetAngularVelocity(newAngularVelocity: idVec3, id: Int /*= 0*/) {
            current.i.angularMomentum.set(newAngularVelocity.times(inertiaTensor))
            Activate()
        }

        override fun GetLinearVelocity(id: Int /*= 0*/): idVec3 {
            curLinearVelocity.set(current.i.linearMomentum.times(inverseMass))
            return curLinearVelocity
        }

        override fun GetAngularVelocity(id: Int /*= 0*/): idVec3 {
            val inverseWorldInertiaTensor: idMat3
            inverseWorldInertiaTensor =
                current.i.orientation.Transpose().times(inverseInertiaTensor.times(current.i.orientation))
            curAngularVelocity.set(inverseWorldInertiaTensor.times(current.i.angularMomentum))
            return curAngularVelocity
        }

        override fun ClipTranslation(results: trace_s, translation: idVec3, model: idClipModel?) {
            if (model != null) {
                Game_local.gameLocal.clip.TranslationModel(
                    results, clipModel!!.GetOrigin(), clipModel!!.GetOrigin().plus(translation),
                    clipModel, clipModel!!.GetAxis(), clipMask, model.Handle(), model.GetOrigin(), model.GetAxis()
                )
            } else {
                Game_local.gameLocal.clip.Translation(
                    results, clipModel!!.GetOrigin(), clipModel!!.GetOrigin().plus(translation),
                    clipModel, clipModel!!.GetAxis(), clipMask, self
                )
            }
        }

        override fun ClipRotation(results: trace_s, rotation: idRotation, model: idClipModel?) {
            if (model != null) {
                Game_local.gameLocal.clip.RotationModel(
                    results, clipModel!!.GetOrigin(), rotation,
                    clipModel, clipModel!!.GetAxis(), clipMask, model.Handle(), model.GetOrigin(), model.GetAxis()
                )
            } else {
                Game_local.gameLocal.clip.Rotation(
                    results, clipModel!!.GetOrigin(), rotation,
                    clipModel, clipModel!!.GetAxis(), clipMask, self
                )
            }
        }

        override fun ClipContents(model: idClipModel?): Int {
            return if (model != null) {
                Game_local.gameLocal.clip.ContentsModel(
                    clipModel!!.GetOrigin(), clipModel, clipModel!!.GetAxis(), -1,
                    model.Handle(), model.GetOrigin(), model.GetAxis()
                )
            } else {
                Game_local.gameLocal.clip.Contents(clipModel!!.GetOrigin(), clipModel, clipModel!!.GetAxis(), -1, null)
            }
        }

        override fun DisableClip() {
            clipModel!!.Disable()
        }

        override fun EnableClip() {
            clipModel!!.Enable()
        }

        override fun UnlinkClip() {
            clipModel!!.Unlink()
        }

        override fun LinkClip() {
            clipModel!!.Link(
                Game_local.gameLocal.clip,
                self,
                clipModel!!.GetId(),
                current.i.position,
                current.i.orientation
            )
        }

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
                contactz, 10, clipModel!!.GetOrigin(),
                dir, Physics.CONTACT_EPSILON, clipModel, clipModel!!.GetAxis(), clipMask, self
            )
            for (i in 0 until num) {
                contacts[i] = contactz[i]
            }
            contacts.SetNum(num, false)
            AddContactEntitiesForContacts()
            return contacts.Num() != 0
        }

        override fun SetPushed(deltaTime: Int) {
            val rotation: idRotation?
            rotation = saved.i.orientation.times(current.i.orientation).ToRotation()

            // velocity with which the af is pushed
            current.pushVelocity.SubVec3_oPluSet(
                0,
                current.i.position.minus(saved.i.position).div(deltaTime * idMath.M_MS2SEC)
            )
            current.pushVelocity.SubVec3_oPluSet(
                1,
                rotation.GetVec().times(-Math_h.DEG2RAD(rotation.GetAngle())).div(deltaTime * idMath.M_MS2SEC)
            )
        }

        override fun GetPushedLinearVelocity(id: Int /*= 0*/): idVec3 {
            return current.pushVelocity.SubVec3(0)
        }

        override fun GetPushedAngularVelocity(id: Int /*= 0*/): idVec3 {
            return current.pushVelocity.SubVec3(1)
        }

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
                current.i.linearMomentum[0],
                RB_MOMENTUM_EXPONENT_BITS,
                RB_MOMENTUM_MANTISSA_BITS
            )
            msg.WriteFloat(
                current.i.linearMomentum[1],
                RB_MOMENTUM_EXPONENT_BITS,
                RB_MOMENTUM_MANTISSA_BITS
            )
            msg.WriteFloat(
                current.i.linearMomentum[2],
                RB_MOMENTUM_EXPONENT_BITS,
                RB_MOMENTUM_MANTISSA_BITS
            )
            msg.WriteFloat(
                current.i.angularMomentum[0],
                RB_MOMENTUM_EXPONENT_BITS,
                RB_MOMENTUM_MANTISSA_BITS
            )
            msg.WriteFloat(
                current.i.angularMomentum[1],
                RB_MOMENTUM_EXPONENT_BITS,
                RB_MOMENTUM_MANTISSA_BITS
            )
            msg.WriteFloat(
                current.i.angularMomentum[2],
                RB_MOMENTUM_EXPONENT_BITS,
                RB_MOMENTUM_MANTISSA_BITS
            )
            msg.WriteDeltaFloat(current.i.position[0], current.localOrigin[0])
            msg.WriteDeltaFloat(current.i.position[1], current.localOrigin[1])
            msg.WriteDeltaFloat(current.i.position[2], current.localOrigin[2])
            msg.WriteDeltaFloat(quat.x, localQuat.x)
            msg.WriteDeltaFloat(quat.y, localQuat.y)
            msg.WriteDeltaFloat(quat.z, localQuat.z)
            msg.WriteDeltaFloat(
                0.0f,
                current.pushVelocity[0],
                RB_VELOCITY_EXPONENT_BITS,
                RB_VELOCITY_MANTISSA_BITS
            )
            msg.WriteDeltaFloat(
                0.0f,
                current.pushVelocity[1],
                RB_VELOCITY_EXPONENT_BITS,
                RB_VELOCITY_MANTISSA_BITS
            )
            msg.WriteDeltaFloat(
                0.0f,
                current.pushVelocity[2],
                RB_VELOCITY_EXPONENT_BITS,
                RB_VELOCITY_MANTISSA_BITS
            )
            msg.WriteDeltaFloat(
                0.0f,
                current.externalForce[0],
                RB_FORCE_EXPONENT_BITS,
                RB_FORCE_MANTISSA_BITS
            )
            msg.WriteDeltaFloat(
                0.0f,
                current.externalForce[1],
                RB_FORCE_EXPONENT_BITS,
                RB_FORCE_MANTISSA_BITS
            )
            msg.WriteDeltaFloat(
                0.0f,
                current.externalForce[2],
                RB_FORCE_EXPONENT_BITS,
                RB_FORCE_MANTISSA_BITS
            )
            msg.WriteDeltaFloat(
                0.0f,
                current.externalTorque[0],
                RB_FORCE_EXPONENT_BITS,
                RB_FORCE_MANTISSA_BITS
            )
            msg.WriteDeltaFloat(
                0.0f,
                current.externalTorque[1],
                RB_FORCE_EXPONENT_BITS,
                RB_FORCE_MANTISSA_BITS
            )
            msg.WriteDeltaFloat(
                0.0f,
                current.externalTorque[2],
                RB_FORCE_EXPONENT_BITS,
                RB_FORCE_MANTISSA_BITS
            )
        }

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
                0.0f,
                RB_VELOCITY_EXPONENT_BITS,
                RB_VELOCITY_MANTISSA_BITS
            )
            current.pushVelocity[1] = msg.ReadDeltaFloat(
                0.0f,
                RB_VELOCITY_EXPONENT_BITS,
                RB_VELOCITY_MANTISSA_BITS
            )
            current.pushVelocity[2] = msg.ReadDeltaFloat(
                0.0f,
                RB_VELOCITY_EXPONENT_BITS,
                RB_VELOCITY_MANTISSA_BITS
            )
            current.externalForce[0] = msg.ReadDeltaFloat(
                0.0f,
                RB_FORCE_EXPONENT_BITS,
                RB_FORCE_MANTISSA_BITS
            )
            current.externalForce[1] = msg.ReadDeltaFloat(
                0.0f,
                RB_FORCE_EXPONENT_BITS,
                RB_FORCE_MANTISSA_BITS
            )
            current.externalForce[2] = msg.ReadDeltaFloat(
                0.0f,
                RB_FORCE_EXPONENT_BITS,
                RB_FORCE_MANTISSA_BITS
            )
            current.externalTorque[0] = msg.ReadDeltaFloat(
                0.0f,
                RB_FORCE_EXPONENT_BITS,
                RB_FORCE_MANTISSA_BITS
            )
            current.externalTorque[1] = msg.ReadDeltaFloat(
                0.0f,
                RB_FORCE_EXPONENT_BITS,
                RB_FORCE_MANTISSA_BITS
            )
            current.externalTorque[2] = msg.ReadDeltaFloat(
                0.0f,
                RB_FORCE_EXPONENT_BITS,
                RB_FORCE_MANTISSA_BITS
            )
            current.i.orientation.set(quat.ToMat3())
            current.localAxis.set(localQuat.ToMat3())
            if (clipModel != null) {
                clipModel?.Link(
                    Game_local.gameLocal.clip,
                    self,
                    clipModel!!.GetId(),
                    current.i.position,
                    current.i.orientation
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
            val newState = next.i.toFloats()
            integrator.Evaluate(current.i.toFloats(), newState, 0f, deltaTime)
            next.i.fromFloats(newState)
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
            var startsolid: Boolean = false
            if (TEST_COLLISION_DETECTION) {
                if (Game_local.gameLocal.clip.Contents(
                        current.i.position,
                        clipModel,
                        current.i.orientation,
                        clipMask,
                        self
                    ) != 0
                ) {
                    startsolid = true
                }
            }
            idMat3.TransposeMultiply(current.i.orientation, next.i.orientation, axis)
            rotation = axis.ToRotation()
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
                // set the next state to the state at the moment of impact
                next.i.position.set(collision.endpos)
                next.i.orientation.set(collision.endAxis)
                next.i.linearMomentum.set(current.i.linearMomentum)
                next.i.angularMomentum.set(current.i.angularMomentum)
                collided = true
            }
            if (TEST_COLLISION_DETECTION) {
                if (Game_local.gameLocal.clip.Contents(
                        next.i.position,
                        clipModel,
                        next.i.orientation,
                        clipMask,
                        self
                    ) != 0
                ) {
                    if (!startsolid) {
                        val bah = 1
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
            impulseNumerator = if (vel > -STOP_SPEED) {
                STOP_SPEED
            } else {
                -(1.0f + bouncyness) * vel
            }
            impulseDenominator = inverseMass + inverseWorldInertiaTensor.times(r.Cross(collision.c.normal)).Cross(r)
                .times(collision.c.normal)
            if (info.invMass != 0f) {
                impulseDenominator += info.invMass + info.invInertiaTensor.times(info.position.Cross(collision.c.normal))
                    .Cross(info.position).times(collision.c.normal)
            }
            impulse.set(collision.c.normal.times(impulseNumerator / impulseDenominator))

            // update linear and angular momentum with impulse
            current.i.linearMomentum.plusAssign(impulse)
            current.i.angularMomentum.plusAssign(r.Cross(impulse))

            // if no movement at all don't blow up
            if (collision.fraction < 0.0001f) {
                current.i.linearMomentum.timesAssign(0.5f)
                current.i.angularMomentum.timesAssign(0.5f)
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

        private fun DropToFloorAndRest() {
            val down = idVec3()
            val tr = trace_s()
            if (testSolid) {
                testSolid = false
                if (Game_local.gameLocal.clip.Contents(
                        current.i.position,
                        clipModel,
                        current.i.orientation,
                        clipMask,
                        self
                    ) != 0
                ) {
                    Game_local.gameLocal.DWarning(
                        "rigid body in solid for entity '%s' type '%s' at (%s)",
                        self!!.name, self!!.GetType().name, current.i.position.ToString(0)
                    )
                    Rest()
                    dropToFloor = false
                    return
                }
            }


            // put the body on the floor
            down.set(current.i.position.plus(gravityNormal.times(128.0f)))
            Game_local.gameLocal.clip.Translation(
                tr,
                current.i.position,
                down,
                clipModel,
                current.i.orientation,
                clipMask,
                self
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
                        self!!.name, self!!.GetType().name, current.i.position.ToString(0)
                    )
                }
                Rest()
                dropToFloor = false
            } else if (IsOutsideWorld()) {
                Game_local.gameLocal.Warning(
                    "rigid body outside world bounds for entity '%s' type '%s' at (%s)",
                    self!!.name, self!!.GetType().name, current.i.position.ToString(0)
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
                return false
            }

            // center of mass in world space
            point.set(current.i.position.plus(centerOfMass.times(current.i.orientation)))
            point.minusAssign(gravityNormal.times(point.times(gravityNormal)))

            // if the point is not inside the winding
            if (!contactWinding.PointInside(gravityNormal, point, 0f)) {
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
                return false
            }
            // if too much velocity in gravity direction
            if (gv > 2.0f * STOP_SPEED || gv < -2.0f * STOP_SPEED) {
                return false
            }

            // calculate rotational velocity
            inverseWorldInertiaTensor =
                current.i.orientation.times(inverseInertiaTensor.times(current.i.orientation.Transpose()))
            av.set(inverseWorldInertiaTensor.times(current.i.angularMomentum))

            // if too much rotational velocity
            return av.LengthSqr() <= STOP_SPEED
        }

        private fun Rest() {
            current.atRest = Game_local.gameLocal.time
            current.i.linearMomentum.Zero()
            current.i.angularMomentum.Zero()
            self!!.BecomeInactive(Entity.TH_PHYSICS)
        }

        private fun DebugDraw() {
            if (SysCvar.rb_showBodies.GetBool() || SysCvar.rb_showActive.GetBool() && current.atRest < 0) {
                CollisionModel_local.collisionModelManager.DrawModel(
                    clipModel!!.Handle(),
                    clipModel!!.GetOrigin(),
                    clipModel!!.GetAxis(),
                    Vector.getVec3_origin(),
                    0.0f
                )
            }
            if (SysCvar.rb_showMass.GetBool()) {
                Game_local.gameRenderWorld.DrawText(
                    Str.va("\n%1.2f", mass),
                    current.i.position,
                    0.08f,
                    Lib.colorCyan,
                    Game_local.gameLocal.GetLocalPlayer()!!.viewAngles.ToMat3(),
                    1
                )
            }
            if (SysCvar.rb_showInertia.GetBool()) {
                val I = inertiaTensor
                Game_local.gameRenderWorld.DrawText(
                    Str.va(
                        "\n\n\n( %.1f %.1f %.1f )\n( %.1f %.1f %.1f )\n( %.1f %.1f %.1f )",
                        I[0].x, I[0].y, I[0].z,
                        I[1].x, I[1].y, I[1].z,
                        I[2].x, I[2].y, I[2].z
                    ),
                    current.i.position,
                    0.05f,
                    Lib.colorCyan,
                    Game_local.gameLocal.GetLocalPlayer()!!.viewAngles.ToMat3(),
                    1
                )
            }
            if (SysCvar.rb_showVelocity.GetBool()) {
                DrawVelocity(clipModel!!.GetId(), 0.1f, 4.0f)
            }
        }

        private class rigidBodyDerivatives_s constructor(derivatives: FloatArray) {
            var angularMatrix: idMat3 = idMat3()
            val force: idVec3 = idVec3()
            val linearVelocity: idVec3 = idVec3()
            val torque: idVec3 = idVec3()
            fun toFloats(): FloatArray {
                val buffer = FloatBuffer.allocate(BYTES / java.lang.Float.BYTES)
                buffer.put(linearVelocity.ToFloatPtr())
                    .put(angularMatrix.ToFloatPtr())
                    .put(force.ToFloatPtr())
                    .put(torque.ToFloatPtr())
                return buffer.array()
            }

            companion object {
                val BYTES: Int = idVec3.BYTES +
                        idMat3.BYTES +
                        idVec3.BYTES +
                        idVec3.BYTES
            }

            init {
                val b = FloatBuffer.wrap(derivatives)
                if (b.hasRemaining()) {
                    linearVelocity.set(idVec3(b.get(), b.get(), b.get()))
                }
                if (b.hasRemaining()) {
                    angularMatrix = idMat3(
                        b.get(), b.get(), b.get(),
                        b.get(), b.get(), b.get(),
                        b.get(), b.get(), b.get()
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

        /*friend*/   class RigidBodyDerivatives constructor() : deriveFunction_t() {
            override fun run(t: Float, clientData: Any, state: FloatArray, derivatives: FloatArray) {
                val p = clientData as idPhysics_RigidBody
                val s = rigidBodyIState_s(state) //TODO:from float array to object
                // NOTE: this struct should be build conform rigidBodyIState_t
                val d = rigidBodyDerivatives_s(derivatives)
                val angularVelocity = idVec3()
                val inverseWorldInertiaTensor: idMat3
                inverseWorldInertiaTensor =
                    s.orientation.times(p.inverseInertiaTensor.times(s.orientation.Transpose()))
                angularVelocity.set(inverseWorldInertiaTensor.times(s.angularMomentum))
                // derivatives
                d.linearVelocity.set(s.linearMomentum.times(p.inverseMass))
                d.angularMatrix = idMat3.SkewSymmetric(angularVelocity).times(s.orientation)
                d.force.set(s.linearMomentum.times(-p.linearFriction).plus(p.current.externalForce))
                d.torque.set(s.angularMomentum.times(-p.angularFriction).plus(p.current.externalTorque))
                System.arraycopy(d.toFloats(), 0, derivatives, 0, derivatives.size)
            }

            companion object {
                val INSTANCE: deriveFunction_t = RigidBodyDerivatives()
            }
        }

        companion object {
            // CLASS_PROTOTYPE( idPhysics_RigidBody );
            const val MAX_INERTIA_SCALE = 10.0f
            val curAngularVelocity: idVec3 = idVec3()
            val curLinearVelocity: idVec3 = idVec3()
        }

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
            saved = current
            mass = 1.0f
            inverseMass = 1.0f
            centerOfMass = idVec3()
            inertiaTensor = idMat3.getMat3_identity()
            inverseInertiaTensor = idMat3.getMat3_identity()

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
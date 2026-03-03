/*
 * Copyright (C) 1999-2011 id Software LLC, a ZeniMax Media company.
 * Translated to Kotlin by Dr. Feederino with support of Claude Code
 *
 * This file is part of the Doom 3 Kotlin project.
 * Original source: neo/Game/Physics/Physics_Monster.h, neo/Game/Physics/Physics_Monster.cpp
 */

package neo.Game.Physics

import neo.Game.GameSys.Class
import neo.Game.GameSys.SaveGame.idRestoreGame
import neo.Game.GameSys.SaveGame.idSaveGame
import neo.Game.Game_local
import neo.Game.Physics.Physics.impactInfo_s
import neo.Game.Physics.Physics_Actor.idPhysics_Actor
import neo.Game.TH_PHYSICS
import neo.Game.idActor
import neo.Game.idEntity
import neo.TempDump
import neo.cm.trace_s
import neo.idlib.BitMsg.idBitMsgDelta
import neo.idlib.containers.CBool
import neo.idlib.containers.CInt
import neo.idlib.math.*
import neo.idlib.math.Matrix.idMat3

object Physics_Monster {
    //
    const val MONSTER_VELOCITY_MAX = 4000.0f
    val MONSTER_VELOCITY_EXPONENT_BITS =
        idMath.BitsForInteger(idMath.BitsForFloat(MONSTER_VELOCITY_MAX)) + 1
    const val MONSTER_VELOCITY_TOTAL_BITS = 16
    val MONSTER_VELOCITY_MANTISSA_BITS =
        MONSTER_VELOCITY_TOTAL_BITS - 1 - MONSTER_VELOCITY_EXPONENT_BITS
    const val OVERCLIP = 1.001f

    /*
     ================
     idPhysics_Monster_SavePState
     ================
     */
    fun idPhysics_Monster_SavePState(savefile: idSaveGame, state: monsterPState_s) {
        savefile.WriteVec3(state.origin)
        savefile.WriteVec3(state.velocity)
        savefile.WriteVec3(state.localOrigin)
        savefile.WriteVec3(state.pushVelocity)
        savefile.WriteBool(state.onGround)
        savefile.WriteInt(state.atRest)
    }

    /*
     ================
     idPhysics_Monster_RestorePState
     ================
     */
    fun idPhysics_Monster_RestorePState(savefile: idRestoreGame, state: monsterPState_s) {
        val onGround = CBool(false)
        val atRest = CInt()
        savefile.ReadVec3(state.origin)
        savefile.ReadVec3(state.velocity)
        savefile.ReadVec3(state.localOrigin)
        savefile.ReadVec3(state.pushVelocity)
        savefile.ReadBool(onGround)
        savefile.ReadInt(atRest)
        state.onGround = onGround._val
        state.atRest = atRest._val
    }

    //
    /*
     ===================================================================================

     Monster physics

     Simulates the motion of a monster through the environment. The monster motion
     is typically driven by animations.

     ===================================================================================
     */
    enum class monsterMoveResult_t {
        MM_OK, MM_SLIDING, MM_BLOCKED, MM_STEPPED, MM_FALLING
    }

    class monsterPState_s {
        var atRest = 0
        val localOrigin: idVec3
        var onGround = false
        val origin: idVec3
        val pushVelocity: idVec3
        val velocity: idVec3

        init {
            origin = idVec3()
            velocity = idVec3()
            localOrigin = idVec3()
            pushVelocity = idVec3()
        }

        // FIX: C++ struct assignment copies by value; Kotlin reference assignment does not.
        // This method performs a deep copy to simulate C++ struct copy semantics.
        fun set(other: monsterPState_s) {
            origin.set(other.origin)
            velocity.set(other.velocity)
            localOrigin.set(other.localOrigin)
            pushVelocity.set(other.pushVelocity)
            onGround = other.onGround
            atRest = other.atRest
        }
    }

    class idPhysics_Monster : idPhysics_Actor() {
        private var blockingEntity: idEntity?

        // monster physics state
        private var current: monsterPState_s
        private val delta // delta for next move
                : idVec3
        private var fly: Boolean
        private var forceDeltaMove: Boolean

        // properties
        private var maxStepHeight // maximum step height
                : Float
        private var minFloorCosine // minimum cosine of floor angle
                : Float

        // results of last evaluate
        private var moveResult: monsterMoveResult_t = monsterMoveResult_t.MM_OK
        private var noImpact // if true do not activate when another object collides
                : Boolean
        private var saved: monsterPState_s
        private var useVelocityMove: Boolean

        override fun Save(savefile: idSaveGame) {
            idPhysics_Monster_SavePState(savefile, current)
            idPhysics_Monster_SavePState(savefile, saved)
            savefile.WriteFloat(maxStepHeight)
            savefile.WriteFloat(minFloorCosine)
            savefile.WriteVec3(delta)
            savefile.WriteBool(forceDeltaMove)
            savefile.WriteBool(fly)
            savefile.WriteBool(useVelocityMove)
            savefile.WriteBool(noImpact)
            savefile.WriteInt(TempDump.etoi(moveResult))
            savefile.WriteObject(blockingEntity as Class.idClass?)
        }

        override fun Restore(savefile: idRestoreGame) {
            idPhysics_Monster_RestorePState(savefile, current)
            idPhysics_Monster_RestorePState(savefile, saved)
            maxStepHeight = savefile.ReadFloat()
            minFloorCosine = savefile.ReadFloat()
            savefile.ReadVec3(delta)
            forceDeltaMove = savefile.ReadBool()
            fly = savefile.ReadBool()
            useVelocityMove = savefile.ReadBool()
            noImpact = savefile.ReadBool()
            moveResult = monsterMoveResult_t.entries.toTypedArray()[savefile.ReadInt()]
            savefile.ReadObject( /*reinterpret_cast<idClass *&>*/blockingEntity)
        }

        // maximum step up the monster can take, default 18 units
        fun SetMaxStepHeight(newMaxStepHeight: Float) {
            maxStepHeight = newMaxStepHeight
        }

        //
        //        // minimum cosine of floor angle to be able to stand on the floor
        //        public void SetMinFloorCosine(final float newMinFloorCosine);
        //
        fun GetMaxStepHeight(): Float {
            return maxStepHeight
        }

        // set delta for next move
        fun SetDelta(d: idVec3) {
            delta.set(d)
            if (delta != vec3_origin) {
                Activate()
            }
        }

        // returns true if monster is standing on the ground
        fun OnGround(): Boolean {
            return current.onGround
        }

        // returns the movement result
        fun GetMoveResult(): monsterMoveResult_t {
            return moveResult
        }

        // overrides any velocity for pure delta movement
        fun ForceDeltaMove(force: Boolean) {
            forceDeltaMove = force
        }

        // whether velocity should be affected by gravity
        fun UseFlyMove(force: Boolean) {
            fly = force
        }

        // don't use delta movement
        fun UseVelocityMove(force: Boolean) {
            useVelocityMove = force
        }

        // get entity blocking the move
        fun GetSlideMoveEntity(): idEntity? {
            return blockingEntity
        }

        // enable/disable activation by impact
        fun EnableImpact() {
            noImpact = false
        }

        fun DisableImpact() {
            noImpact = true
        }

        // common physics interface
        override fun Evaluate(timeStepMSec: Int, endTimeMSec: Int): Boolean {
            val masterOrigin = idVec3()
            val oldOrigin = idVec3()
            val masterAxis = idMat3()
            val timeStep: Float
            timeStep = MS2SEC(timeStepMSec.toFloat())
            moveResult = monsterMoveResult_t.MM_OK
            blockingEntity = null
            oldOrigin.set(current.origin)

            // if bound to a master
            if (masterEntity != null) {
                self!!.GetMasterPosition(masterOrigin, masterAxis)
                current.origin.set(masterOrigin + current.localOrigin * masterAxis)
                clipModel!!.Link(Game_local.gameLocal.clip, self, 0, current.origin, clipModel!!.GetAxis())
                current.velocity.set((current.origin - oldOrigin) / timeStep)
                masterDeltaYaw = masterYaw
                masterYaw = masterAxis[0].ToYaw()
                masterDeltaYaw = masterYaw - masterDeltaYaw
                return true
            }

            // if the monster is at rest
            if (current.atRest >= 0) {
                return false
            }
            ActivateContactEntities()

            // move the monster velocity into the frame of a pusher
            current.velocity.minusAssign(current.pushVelocity)
            clipModel!!.Unlink()

            // check if on the ground
            CheckGround(current)

            // if not on the ground or moving upwards
            val upspeed: Float
            upspeed = if (gravityNormal != vec3_zero) {
                -current.velocity.times(gravityNormal)
            } else {
                current.velocity.z
            }
            if (fly || !forceDeltaMove && (!current.onGround || upspeed > 1.0f)) {
                if (upspeed < 0.0f) {
                    moveResult = monsterMoveResult_t.MM_FALLING
                } else {
                    current.onGround = false
                    moveResult = monsterMoveResult_t.MM_OK
                }
                delta.set(current.velocity.times(timeStep))
                if (delta != vec3_origin) {
                    moveResult = SlideMove(current.origin, current.velocity, delta)
                    delta.Zero()
                }
                if (!fly) {
                    current.velocity.plusAssign(gravityVector.times(timeStep))
                }
            } else {
                if (useVelocityMove) {
                    delta.set(current.velocity.times(timeStep))
                } else {
                    current.velocity.set(delta.div(timeStep))
                }
                current.velocity.minusAssign(gravityNormal.times(current.velocity.times(gravityNormal)))
                if (delta == vec3_origin) {
                    Rest()
                } else {
                    // try moving into the desired direction
                    moveResult = StepMove(current.origin, current.velocity, delta)
                    delta.Zero()
                }
            }
            clipModel!!.Link(Game_local.gameLocal.clip, self, 0, current.origin, clipModel!!.GetAxis())

            // get all the ground contacts
            EvaluateContacts()

            // move the monster velocity back into the world frame
            current.velocity.plusAssign(current.pushVelocity)
            current.pushVelocity.Zero()
            if (IsOutsideWorld()) {
                Game_local.gameLocal.Warning(
                    "clip model outside world bounds for entity '%s' at (%s)",
                    self!!.name,
                    current.origin.ToString(0)
                )
                Rest()
            }
            return current.origin != oldOrigin
        }

        override fun UpdateTime(endTimeMSec: Int) {}
        override fun GetTime(): Int {
            return Game_local.gameLocal.time
        }

        override fun GetImpactInfo(id: Int, point: idVec3): impactInfo_s {
            val info = impactInfo_s()
            info.invMass = invMass
            info.invInertiaTensor.Zero()
            info.position.Zero()
            info.velocity.set(current.velocity)
            return info
        }

        override fun ApplyImpulse(id: Int, point: idVec3, impulse: idVec3) {
            if (noImpact) {
                return
            }
            current.velocity.plusAssign(impulse.times(invMass))
            Activate()
        }

        override fun Activate() {
            current.atRest = -1
            self!!.BecomeActive(TH_PHYSICS)
        }

        override fun PutToRest() {
            current.atRest = Game_local.gameLocal.time
            current.velocity.Zero()
            self!!.BecomeInactive(TH_PHYSICS)
        }

        override fun IsAtRest(): Boolean {
            return current.atRest >= 0
        }

        override fun GetRestStartTime(): Int {
            return current.atRest
        }

        override fun SaveState() {
            // FIX: Was "saved = current" which only copies the reference in Kotlin.
            // C++ copies the struct by value. Use deep copy instead.
            saved.set(current)
        }

        override fun RestoreState() {
            // FIX: Was "current = saved" which only copies the reference in Kotlin.
            current.set(saved)
            clipModel!!.Link(Game_local.gameLocal.clip, self, 0, current.origin, clipModel!!.GetAxis())
            EvaluateContacts()
        }

        override fun SetOrigin(newOrigin: idVec3, id: Int /*= -1*/) {
            val masterOrigin = idVec3()
            val masterAxis = idMat3()
            current.localOrigin.set(newOrigin)
            if (masterEntity != null) {
                self!!.GetMasterPosition(masterOrigin, masterAxis)
                current.origin.set(masterOrigin.plus(newOrigin.times(masterAxis)))
            } else {
                current.origin.set(newOrigin)
            }
            clipModel!!.Link(Game_local.gameLocal.clip, self, 0, newOrigin, clipModel!!.GetAxis())
            Activate()
        }

        override fun SetAxis(newAxis: idMat3, id: Int /*= -1*/) {
            clipModel!!.Link(Game_local.gameLocal.clip, self, 0, clipModel!!.GetOrigin(), newAxis)
            Activate()
        }

        override fun Translate(translation: idVec3, id: Int /*= -1*/) {
            current.localOrigin.plusAssign(translation)
            current.origin.plusAssign(translation)
            clipModel!!.Link(Game_local.gameLocal.clip, self, 0, current.origin, clipModel!!.GetAxis())
            Activate()
        }

        override fun Rotate(rotation: idRotation, id: Int /*= -1*/) {
            val masterOrigin = idVec3()
            val masterAxis = idMat3()
            current.origin.timesAssign(rotation)
            if (masterEntity != null) {
                self!!.GetMasterPosition(masterOrigin, masterAxis)
                current.localOrigin.set(current.origin.minus(masterOrigin).times(masterAxis.Transpose()))
            } else {
                current.localOrigin.set(current.origin)
            }
            clipModel!!.Link(
                Game_local.gameLocal.clip,
                self,
                0,
                current.origin,
                clipModel!!.GetAxis().times(rotation.ToMat3())
            )
            Activate()
        }

        override fun SetLinearVelocity(newLinearVelocity: idVec3, id: Int /*= 0*/) {
            current.velocity.set(newLinearVelocity)
            Activate()
        }

        override fun GetLinearVelocity(id: Int /*= 0*/): idVec3 {
            return current.velocity
        }

        override fun SetPushed(deltaTime: Int) {
            // velocity with which the monster is pushed
            current.pushVelocity.plusAssign(current.origin.minus(saved.origin).div(deltaTime * idMath.M_MS2SEC))
        }

        override fun GetPushedLinearVelocity(id: Int /*= 0*/): idVec3 {
            return current.pushVelocity
        }

        /*
         ================
         idPhysics_Monster::SetMaster

         the binding is never orientated
         ================
         */
        override fun SetMaster(master: idEntity?, orientated: Boolean /*= true*/) {
            val masterOrigin = idVec3()
            val masterAxis = idMat3()
            if (master != null) {
                if (null == masterEntity) {
                    // transform from world space to master space
                    self!!.GetMasterPosition(masterOrigin, masterAxis)
                    current.localOrigin.set(current.origin.minus(masterOrigin).times(masterAxis.Transpose()))
                    masterEntity = master
                    masterYaw = masterAxis[0].ToYaw()
                }
                ClearContacts()
            } else {
                if (masterEntity != null) {
                    masterEntity = null
                    Activate()
                }
            }
        }

        override fun WriteToSnapshot(msg: idBitMsgDelta) {
            msg.WriteFloat(current.origin[0])
            msg.WriteFloat(current.origin[1])
            msg.WriteFloat(current.origin[2])
            msg.WriteFloat(
                current.velocity[0],
                MONSTER_VELOCITY_EXPONENT_BITS,
                MONSTER_VELOCITY_MANTISSA_BITS
            )
            msg.WriteFloat(
                current.velocity[1],
                MONSTER_VELOCITY_EXPONENT_BITS,
                MONSTER_VELOCITY_MANTISSA_BITS
            )
            msg.WriteFloat(
                current.velocity[2],
                MONSTER_VELOCITY_EXPONENT_BITS,
                MONSTER_VELOCITY_MANTISSA_BITS
            )
            msg.WriteDeltaFloat(current.origin[0], current.localOrigin[0])
            msg.WriteDeltaFloat(current.origin[1], current.localOrigin[1])
            msg.WriteDeltaFloat(current.origin[2], current.localOrigin[2])
            msg.WriteDeltaFloat(
                0.0f,
                current.pushVelocity[0],
                MONSTER_VELOCITY_EXPONENT_BITS,
                MONSTER_VELOCITY_MANTISSA_BITS
            )
            msg.WriteDeltaFloat(
                0.0f,
                current.pushVelocity[1],
                MONSTER_VELOCITY_EXPONENT_BITS,
                MONSTER_VELOCITY_MANTISSA_BITS
            )
            msg.WriteDeltaFloat(
                0.0f,
                current.pushVelocity[2],
                MONSTER_VELOCITY_EXPONENT_BITS,
                MONSTER_VELOCITY_MANTISSA_BITS
            )
            msg.WriteLong(current.atRest)
            msg.WriteBits(TempDump.btoi(current.onGround), 1)
        }

        override fun ReadFromSnapshot(msg: idBitMsgDelta) {
            current.origin[0] = msg.ReadFloat()
            current.origin[1] = msg.ReadFloat()
            current.origin[2] = msg.ReadFloat()
            current.velocity[0] = msg.ReadFloat(
                MONSTER_VELOCITY_EXPONENT_BITS,
                MONSTER_VELOCITY_MANTISSA_BITS
            )
            current.velocity[1] = msg.ReadFloat(
                MONSTER_VELOCITY_EXPONENT_BITS,
                MONSTER_VELOCITY_MANTISSA_BITS
            )
            current.velocity[2] = msg.ReadFloat(
                MONSTER_VELOCITY_EXPONENT_BITS,
                MONSTER_VELOCITY_MANTISSA_BITS
            )
            current.localOrigin[0] = msg.ReadDeltaFloat(current.origin[0])
            current.localOrigin[1] = msg.ReadDeltaFloat(current.origin[1])
            current.localOrigin[2] = msg.ReadDeltaFloat(current.origin[2])
            current.pushVelocity[0] = msg.ReadDeltaFloat(
                0.0f,
                MONSTER_VELOCITY_EXPONENT_BITS,
                MONSTER_VELOCITY_MANTISSA_BITS
            )
            current.pushVelocity[1] = msg.ReadDeltaFloat(
                0.0f,
                MONSTER_VELOCITY_EXPONENT_BITS,
                MONSTER_VELOCITY_MANTISSA_BITS
            )
            current.pushVelocity[2] = msg.ReadDeltaFloat(
                0.0f,
                MONSTER_VELOCITY_EXPONENT_BITS,
                MONSTER_VELOCITY_MANTISSA_BITS
            )
            current.atRest = msg.ReadLong()
            current.onGround = msg.ReadBits(1) != 0
        }

        private fun CheckGround(state: monsterPState_s) {
            val groundTrace = trace_s()
            val down = idVec3()
            if (gravityNormal == vec3_zero) {
                state.onGround = false
                groundEntityPtr.oSet(null)
                return
            }
            down.set(state.origin.plus(gravityNormal.times(Physics.CONTACT_EPSILON)))
            Game_local.gameLocal.clip.Translation(
                groundTrace,
                state.origin,
                down,
                clipModel,
                clipModel!!.GetAxis(),
                clipMask,
                self
            )
            if (groundTrace.fraction == 1.0f) {
                state.onGround = false
                groundEntityPtr.oSet(null)
                return
            }
            groundEntityPtr.oSet(Game_local.gameLocal.entities[groundTrace.c.entityNum])
            if (groundTrace.c.normal.times(gravityNormal.unaryMinus()) < minFloorCosine) {
                state.onGround = false
                return
            }
            state.onGround = true

            // let the entity know about the collision
            self!!.Collide(groundTrace, state.velocity)

            // apply impact to a non world floor entity
            if (groundTrace.c.entityNum != Game_local.ENTITYNUM_WORLD && groundEntityPtr.GetEntity() != null) {
                val info = groundEntityPtr.GetEntity()!!.GetImpactInfo(self, groundTrace.c.id, groundTrace.c.point)
                if (info.invMass != 0.0f) {
                    groundEntityPtr.GetEntity()!!
                        .ApplyImpulse(self, 0, groundTrace.c.point, state.velocity.div(info.invMass * 10.0f))
                }
            }
        }

        private fun SlideMove(start: idVec3, velocity: idVec3, delta: idVec3): monsterMoveResult_t {
            var i: Int
            val tr = trace_s()
            val move = idVec3()
            blockingEntity = null
            move.set(delta)
            i = 0
            while (i < 3) {
                Game_local.gameLocal.clip.Translation(
                    tr,
                    start,
                    start.plus(move),
                    clipModel,
                    clipModel!!.GetAxis(),
                    clipMask,
                    self
                )
                start.set(tr.endpos)
                if (tr.fraction == 1.0f) {
                    return if (i > 0) {
                        monsterMoveResult_t.MM_SLIDING
                    } else monsterMoveResult_t.MM_OK
                }
                if (tr.c.entityNum != Game_local.ENTITYNUM_NONE) {
                    blockingEntity = Game_local.gameLocal.entities[tr.c.entityNum]
                }

                // clip the movement delta and velocity
                move.ProjectOntoPlane(tr.c.normal, OVERCLIP)
                velocity.ProjectOntoPlane(tr.c.normal, OVERCLIP)
                i++
            }
            return monsterMoveResult_t.MM_BLOCKED
        }

        /*
         =====================
         idPhysics_Monster::StepMove

         move start into the delta direction
         the velocity is clipped conform any collisions
         =====================
         */
        private fun StepMove(start: idVec3, velocity: idVec3, delta: idVec3): monsterMoveResult_t {
            val tr = trace_s()
            val up = idVec3()
            val down = idVec3()
            val noStepPos = idVec3()
            val noStepVel = idVec3()
            val stepPos = idVec3()
            val stepVel = idVec3()
            val result1: monsterMoveResult_t
            val result2: monsterMoveResult_t
            val stepdist: Float
            val nostepdist: Float
            if (delta == vec3_origin) {
                return monsterMoveResult_t.MM_OK
            }

            // try to move without stepping up
            noStepPos.set(start)
            noStepVel.set(velocity)
            result1 = SlideMove(noStepPos, noStepVel, delta)
            if (result1 == monsterMoveResult_t.MM_OK) {
                velocity.set(noStepVel)
                if (gravityNormal == vec3_zero) {
                    start.set(noStepPos)
                    return monsterMoveResult_t.MM_OK
                }

                // try to step down so that we walk down slopes and stairs at a normal rate
                down.set(noStepPos.plus(gravityNormal.times(maxStepHeight)))
                Game_local.gameLocal.clip.Translation(
                    tr,
                    noStepPos,
                    down,
                    clipModel,
                    clipModel!!.GetAxis(),
                    clipMask,
                    self
                )
                return if (tr.fraction < 1.0f) {
                    start.set(tr.endpos)
                    monsterMoveResult_t.MM_STEPPED
                } else {
                    start.set(noStepPos)
                    monsterMoveResult_t.MM_OK
                }
            }
            if (blockingEntity != null && blockingEntity is idActor) {
                // try to step down in case walking into an actor while going down steps
                down.set(noStepPos.plus(gravityNormal.times(maxStepHeight)))
                Game_local.gameLocal.clip.Translation(
                    tr,
                    noStepPos,
                    down,
                    clipModel,
                    clipModel!!.GetAxis(),
                    clipMask,
                    self
                )
                start.set(tr.endpos)
                velocity.set(noStepVel)
                return monsterMoveResult_t.MM_BLOCKED
            }
            if (gravityNormal == vec3_zero) {
                return result1
            }

            // try to step up
            up.set(start.minus(gravityNormal.times(maxStepHeight)))
            Game_local.gameLocal.clip.Translation(tr, start, up, clipModel, clipModel!!.GetAxis(), clipMask, self)
            if (tr.fraction == 0.0f) {
                start.set(noStepPos)
                velocity.set(noStepVel)
                return result1
            }

            // try to move at the stepped up position
            stepPos.set(tr.endpos)
            stepVel.set(velocity)
            result2 = SlideMove(stepPos, stepVel, delta)
            if (result2 == monsterMoveResult_t.MM_BLOCKED) {
                start.set(noStepPos)
                velocity.set(noStepVel)
                return result1
            }

            // step down again
            down.set(stepPos.plus(gravityNormal.times(maxStepHeight)))
            Game_local.gameLocal.clip.Translation(tr, stepPos, down, clipModel, clipModel!!.GetAxis(), clipMask, self)
            stepPos.set(tr.endpos)

            // if the move is further without stepping up, or the slope is too steap, don't step up
            nostepdist = noStepPos.minus(start).LengthSqr()
            stepdist = stepPos.minus(start).LengthSqr()
            if (nostepdist >= stepdist || tr.c.normal.times(gravityNormal.unaryMinus()) < minFloorCosine) {
                start.set(noStepPos)
                velocity.set(noStepVel)
                return monsterMoveResult_t.MM_SLIDING
            }
            start.set(stepPos)
            velocity.set(stepVel)
            return monsterMoveResult_t.MM_STEPPED
        }

        private fun Rest() {
            current.atRest = Game_local.gameLocal.time
            current.velocity.Zero()
            self!!.BecomeInactive(TH_PHYSICS)
        }

        init {
            current = monsterPState_s()
            current.atRest = -1
            saved = monsterPState_s()
            saved.set(current)
            delta = idVec3()
            maxStepHeight = 18.0f
            minFloorCosine = 0.7f
            moveResult = monsterMoveResult_t.MM_OK
            forceDeltaMove = false
            fly = false
            useVelocityMove = false
            noImpact = false
            blockingEntity = null
        }
    }
}
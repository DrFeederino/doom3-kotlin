/*
 * Copyright (C) 1999-2011 id Software LLC, a ZeniMax Media company.
 * Translated to Kotlin by Dr. Feederino with support of Claude Code
 *
 * This file is part of the Doom 3 Kotlin project.
 * Original source: neo/Game/Physics/Physics_Player.h, neo/Game/Physics/Physics_Player.cpp
 */

package neo.Game.Physics

import neo.Game.GameSys.SaveGame.idRestoreGame
import neo.Game.GameSys.SaveGame.idSaveGame
import neo.Game.GameSys.SysCvar
import neo.Game.Game_local
import neo.Game.Physics.Physics.impactInfo_s
import neo.Game.Physics.Physics_Actor.idPhysics_Actor
import neo.Game.idEntity
import neo.Renderer.Material
import neo.TempDump
import neo.cm.contactInfo_t
import neo.cm.contactType_t
import neo.cm.trace_s
import neo.framework.UsercmdGen.usercmd_t
import neo.idlib.BV.idBounds
import neo.idlib.BitMsg.idBitMsgDelta
import neo.idlib.geometry.TraceModel.idTraceModel
import neo.idlib.math.*
import neo.idlib.math.Matrix.idMat3
import kotlin.math.abs

object Physics_Player {
    const val MAXTOUCH = 32
    const val MIN_WALK_NORMAL = 0.7f // can't walk on very steep slopes
    const val OVERCLIP = 1.001f
    const val PLAYER_MOVEMENT_FLAGS_BITS = 8
    const val PLAYER_MOVEMENT_TYPE_BITS = 3
    const val PLAYER_VELOCITY_MAX = 4000.0f
    val PLAYER_VELOCITY_EXPONENT_BITS =
        idMath.BitsForInteger(idMath.BitsForFloat(PLAYER_VELOCITY_MAX)) + 1
    const val PLAYER_VELOCITY_TOTAL_BITS = 16
    val PLAYER_VELOCITY_MANTISSA_BITS =
        PLAYER_VELOCITY_TOTAL_BITS - 1 - PLAYER_VELOCITY_EXPONENT_BITS

    // movementFlags
    const val PMF_DUCKED = 1 // set when ducking
    const val PMF_JUMPED = 2 // set when the player jumped this frame
    const val PMF_JUMP_HELD = 16 // set when jump button is held down
    const val PMF_STEPPED_DOWN = 8 // set when the player stepped down this frame
    const val PMF_STEPPED_UP = 4 // set when the player stepped up this frame
    const val PMF_TIME_KNOCKBACK = 64 // movementTime is an air-accelerate only time
    const val PMF_TIME_LAND = 32 // movementTime is time before rejump
    const val PMF_TIME_WATERJUMP = 128 // movementTime is waterjump
    const val PMF_ALL_TIMES =
        PMF_TIME_WATERJUMP or PMF_TIME_LAND or PMF_TIME_KNOCKBACK
    const val PM_ACCELERATE = 10.0f
    const val PM_AIRACCELERATE = 1.0f
    const val PM_AIRFRICTION = 0.0f
    const val PM_FLYACCELERATE = 8.0f
    const val PM_FLYFRICTION = 3.0f
    const val PM_FRICTION = 6.0f
    const val PM_LADDERSPEED = 100.0f
    const val PM_NOCLIPFRICTION = 12.0f
    const val PM_STEPSCALE = 1.0f

    // movement parameters
    const val PM_STOPSPEED = 100.0f
    const val PM_SWIMSCALE = 0.5f
    const val PM_WATERACCELERATE = 4.0f
    const val PM_WATERFRICTION = 1.0f
    var c_pmove = 0

    /*
     ================
     idPhysics_Player_SavePState
     ================
     */
    fun idPhysics_Player_SavePState(savefile: idSaveGame, state: playerPState_s) {
        savefile.WriteVec3(state.origin)
        savefile.WriteVec3(state.velocity)
        savefile.WriteVec3(state.localOrigin)
        savefile.WriteVec3(state.pushVelocity)
        savefile.WriteFloat(state.stepUp)
        savefile.WriteInt(state.movementType)
        savefile.WriteInt(state.movementFlags)
        savefile.WriteInt(state.movementTime)
    }

    /*
     ================
     idPhysics_Player_RestorePState
     ================
     */
    fun idPhysics_Player_RestorePState(savefile: idRestoreGame, state: playerPState_s) {
        savefile.ReadVec3(state.origin)
        savefile.ReadVec3(state.velocity)
        savefile.ReadVec3(state.localOrigin)
        savefile.ReadVec3(state.pushVelocity)
        state.stepUp = savefile.ReadFloat()
        state.movementType = savefile.ReadInt()
        state.movementFlags = savefile.ReadInt()
        state.movementTime = savefile.ReadInt()
    }

    /*
     ===================================================================================

     Player physics

     Simulates the motion of a player through the environment. Input from the
     player is used to allow a certain degree of control over the motion.

     ===================================================================================
     */
    // movementType
    enum class pmtype_t {
        PM_NORMAL,  // normal physics
        PM_DEAD,  // no acceleration or turning, but free falling
        PM_SPECTATOR,  // flying without gravity but with collision detection
        PM_FREEZE,  // stuck in place without control
        PM_NOCLIP // flying without collision detection nor gravity
    }

    //
    enum class waterLevel_t {
        WATERLEVEL_NONE, WATERLEVEL_FEET, WATERLEVEL_WAIST, WATERLEVEL_HEAD
    }

    class playerPState_s {
        val localOrigin: idVec3 = idVec3()
        var movementFlags = 0
        var movementTime = 0
        var movementType = 0
        val origin: idVec3 = idVec3()
        val pushVelocity: idVec3 = idVec3()
        var stepUp = 0.0f
        val velocity: idVec3 = idVec3()

        // FIX: C++ struct assignment copies by value; Kotlin reference assignment does not.
        fun set(other: playerPState_s) {
            origin.set(other.origin)
            velocity.set(other.velocity)
            localOrigin.set(other.localOrigin)
            pushVelocity.set(other.pushVelocity)
            stepUp = other.stepUp
            movementFlags = other.movementFlags
            movementTime = other.movementTime
            movementType = other.movementType
        }
    }

    class idPhysics_Player : idPhysics_Actor() {
        // results of last evaluate
        var waterLevel: waterLevel_t
        var waterType: Int

        // player input
        private var command: usercmd_t
        private var crouchSpeed: Float

        // player physics state
        private var current: playerPState_s
        private var debugLevel // if set, diagnostic output will be printed
                = 0

        // run-time variables
        private var framemsec: Int
        private var frametime: Float
        private var groundMaterial: Material.idMaterial?
        private var groundPlane: Boolean
        private val groundTrace: trace_s

        // ladder movement
        private var ladder: Boolean
        private val ladderNormal: idVec3
        private var maxJumpHeight: Float
        private var maxStepHeight: Float
        private var playerSpeed: Float
        private var saved: playerPState_s
        private val viewAngles: idAngles
        private val viewForward: idVec3
        private val viewRight: idVec3

        // properties
        private var walkSpeed: Float

        // walk movement
        private var walking: Boolean
        override fun Save(savefile: idSaveGame) {
            idPhysics_Player_SavePState(savefile, current)
            idPhysics_Player_SavePState(savefile, saved)
            savefile.WriteFloat(walkSpeed)
            savefile.WriteFloat(crouchSpeed)
            savefile.WriteFloat(maxStepHeight)
            savefile.WriteFloat(maxJumpHeight)
            savefile.WriteInt(debugLevel)
            savefile.WriteUsercmd(command)
            savefile.WriteAngles(viewAngles)
            savefile.WriteInt(framemsec)
            savefile.WriteFloat(frametime)
            savefile.WriteFloat(playerSpeed)
            savefile.WriteVec3(viewForward)
            savefile.WriteVec3(viewRight)
            savefile.WriteBool(walking)
            savefile.WriteBool(groundPlane)
            savefile.WriteTrace(groundTrace)
            savefile.WriteMaterial(groundMaterial)
            savefile.WriteBool(ladder)
            savefile.WriteVec3(ladderNormal)
            savefile.WriteInt(TempDump.etoi(waterLevel))
            savefile.WriteInt(waterType)
        }

        override fun Restore(savefile: idRestoreGame) {
            idPhysics_Player_RestorePState(savefile, current)
            idPhysics_Player_RestorePState(savefile, saved)
            walkSpeed = savefile.ReadFloat()
            crouchSpeed = savefile.ReadFloat()
            maxStepHeight = savefile.ReadFloat()
            maxJumpHeight = savefile.ReadFloat()
            debugLevel = savefile.ReadInt()
            savefile.ReadUsercmd(command)
            savefile.ReadAngles(viewAngles)
            framemsec = savefile.ReadInt()
            frametime = savefile.ReadFloat()
            playerSpeed = savefile.ReadFloat()
            savefile.ReadVec3(viewForward)
            savefile.ReadVec3(viewRight)
            walking = savefile.ReadBool()
            groundPlane = savefile.ReadBool()
            savefile.ReadTrace(groundTrace)
            savefile.ReadMaterial(groundMaterial as Material.idMaterial)
            ladder = savefile.ReadBool()
            savefile.ReadVec3(ladderNormal)
            waterLevel = waterLevel_t.values()[savefile.ReadInt()]
            waterType = savefile.ReadInt()
        }

        // initialisation
        fun SetSpeed(newWalkSpeed: Float, newCrouchSpeed: Float) {
            walkSpeed = newWalkSpeed
            crouchSpeed = newCrouchSpeed
        }

        fun SetMaxStepHeight(newMaxStepHeight: Float) {
            maxStepHeight = newMaxStepHeight
        }

        fun GetMaxStepHeight(): Float {
            return maxStepHeight
        }

        fun SetMaxJumpHeight(newMaxJumpHeight: Float) {
            maxJumpHeight = newMaxJumpHeight
        }

        fun SetMovementType(type: pmtype_t) {
            current.movementType = TempDump.etoi(type)
        }

        fun SetPlayerInput(cmd: usercmd_t, newViewAngles: idAngles) {
            command = cmd
            viewAngles.set(newViewAngles) // can't use cmd.angles cause of the delta_angles
        }

        fun SetKnockBack(knockBackTime: Int) {
            if (current.movementTime != 0) {
                return
            }
            current.movementFlags = current.movementFlags or PMF_TIME_KNOCKBACK
            current.movementTime = knockBackTime
        }

        fun SetDebugLevel(set: Boolean) {
            debugLevel = TempDump.btoi(set)
        }

        // feed back from last physics frame
        fun GetWaterLevel(): waterLevel_t {
            return waterLevel
        }

        fun GetWaterType(): Int {
            return waterType
        }

        fun HasJumped(): Boolean {
            return (current.movementFlags and PMF_JUMPED) != 0
        }

        fun HasSteppedUp(): Boolean {
            return (current.movementFlags and (PMF_STEPPED_UP or PMF_STEPPED_DOWN)) != 0
        }

        fun GetStepUp(): Float {
            return current.stepUp
        }

        fun IsCrouching(): Boolean {
            return (current.movementFlags and PMF_DUCKED) != 0
        }

        fun OnLadder(): Boolean {
            return ladder
        }

        // != GetOrigin
        fun PlayerGetOrigin(): idVec3 {
            return current.origin
        }

        // common physics interface
        override fun Evaluate(timeStepMSec: Int, endTimeMSec: Int): Boolean {
            val masterOrigin = idVec3()
            val oldOrigin = idVec3(current.origin)
            val masterAxis = idMat3()
            waterLevel = waterLevel_t.WATERLEVEL_NONE
            waterType = 0
            clipModel!!.Unlink()

            // if bound to a master
            if (masterEntity != null) {
                self!!.GetMasterPosition(masterOrigin, masterAxis)
                current.origin.set(masterOrigin + current.localOrigin * masterAxis)
                clipModel!!.Link(Game_local.gameLocal.clip, self, 0, current.origin, clipModel!!.GetAxis())
                current.velocity.set(current.origin.minus(oldOrigin).div(timeStepMSec * 0.001f))
                masterDeltaYaw = masterYaw
                masterYaw = masterAxis[0].ToYaw()
                masterDeltaYaw = masterYaw - masterDeltaYaw
                return true
            }
            ActivateContactEntities()
            MovePlayer(timeStepMSec)
            clipModel!!.Link(Game_local.gameLocal.clip, self, 0, current.origin, clipModel!!.GetAxis())
            if (IsOutsideWorld()) {
                Game_local.gameLocal.Warning(
                    "clip model outside world bounds for entity '%s' at (%s)",
                    self!!.name,
                    current.origin.ToString(0)
                )
            }

            return true //( current.origin != oldOrigin );
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
            if (current.movementType != TempDump.etoi(pmtype_t.PM_NOCLIP)) {
                current.velocity.plusAssign(impulse.times(invMass))
            }
        }

        override fun IsAtRest(): Boolean {
            return false
        }

        override fun GetRestStartTime(): Int {
            return -1
        }

        override fun SaveState() {
            saved.set(current)
        }

        override fun RestoreState() {
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
        }

        override fun SetAxis(newAxis: idMat3, id: Int /*= -1*/) {
            clipModel!!.Link(Game_local.gameLocal.clip, self, 0, clipModel!!.GetOrigin(), newAxis)
        }

        override fun Translate(translation: idVec3, id: Int /*= -1*/) {
            current.localOrigin.plusAssign(translation)
            current.origin.plusAssign(translation)
            clipModel!!.Link(Game_local.gameLocal.clip, self, 0, current.origin, clipModel!!.GetAxis())
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
        }

        override fun SetLinearVelocity(newLinearVelocity: idVec3, id: Int /*= 0*/) {
            current.velocity.set(newLinearVelocity)
        }

        override fun GetLinearVelocity(id: Int /*= 0*/): idVec3 {
            return current.velocity
        }

        override fun SetPushed(deltaTime: Int) {
            val velocity = idVec3()
            val d: Float

            // velocity with which the player is pushed
            velocity.set(current.origin.minus(saved.origin).div(deltaTime * idMath.M_MS2SEC))

            // remove any downward push velocity
            d = velocity.times(gravityNormal)
            if (d > 0.0f) {
                velocity.minusAssign(gravityNormal.times(d))
            }
            current.pushVelocity.plusAssign(velocity)
        }

        override fun GetPushedLinearVelocity(id: Int /*= 0*/): idVec3 {
            return current.pushVelocity
        }

        fun GetPushedLinearVelocity(): idVec3 {
            return GetPushedLinearVelocity(0)
        }

        fun ClearPushedVelocity() {
            current.pushVelocity.Zero()
        }

        /*
         ================
         idPhysics_Player::SetMaster

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
                }
            }
        }

        override fun WriteToSnapshot(msg: idBitMsgDelta) {
            msg.WriteFloat(current.origin[0])
            msg.WriteFloat(current.origin[1])
            msg.WriteFloat(current.origin[2])
            msg.WriteFloat(
                current.velocity[0],
                PLAYER_VELOCITY_EXPONENT_BITS,
                PLAYER_VELOCITY_MANTISSA_BITS
            )
            msg.WriteFloat(
                current.velocity[1],
                PLAYER_VELOCITY_EXPONENT_BITS,
                PLAYER_VELOCITY_MANTISSA_BITS
            )
            msg.WriteFloat(
                current.velocity[2],
                PLAYER_VELOCITY_EXPONENT_BITS,
                PLAYER_VELOCITY_MANTISSA_BITS
            )
            msg.WriteDeltaFloat(current.origin[0], current.localOrigin[0])
            msg.WriteDeltaFloat(current.origin[1], current.localOrigin[1])
            msg.WriteDeltaFloat(current.origin[2], current.localOrigin[2])
            msg.WriteDeltaFloat(
                0.0f,
                current.pushVelocity[0],
                PLAYER_VELOCITY_EXPONENT_BITS,
                PLAYER_VELOCITY_MANTISSA_BITS
            )
            msg.WriteDeltaFloat(
                0.0f,
                current.pushVelocity[1],
                PLAYER_VELOCITY_EXPONENT_BITS,
                PLAYER_VELOCITY_MANTISSA_BITS
            )
            msg.WriteDeltaFloat(
                0.0f,
                current.pushVelocity[2],
                PLAYER_VELOCITY_EXPONENT_BITS,
                PLAYER_VELOCITY_MANTISSA_BITS
            )
            msg.WriteDeltaFloat(0.0f, current.stepUp)
            msg.WriteBits(current.movementType, PLAYER_MOVEMENT_TYPE_BITS)
            msg.WriteBits(current.movementFlags, PLAYER_MOVEMENT_FLAGS_BITS)
            msg.WriteDeltaLong(0, current.movementTime)
        }

        override fun ReadFromSnapshot(msg: idBitMsgDelta) {
            current.origin[0] = msg.ReadFloat()
            current.origin[1] = msg.ReadFloat()
            current.origin[2] = msg.ReadFloat()
            current.velocity[0] = msg.ReadFloat(
                PLAYER_VELOCITY_EXPONENT_BITS,
                PLAYER_VELOCITY_MANTISSA_BITS
            )
            current.velocity[1] = msg.ReadFloat(
                PLAYER_VELOCITY_EXPONENT_BITS,
                PLAYER_VELOCITY_MANTISSA_BITS
            )
            current.velocity[2] = msg.ReadFloat(
                PLAYER_VELOCITY_EXPONENT_BITS,
                PLAYER_VELOCITY_MANTISSA_BITS
            )
            current.localOrigin[0] = msg.ReadDeltaFloat(current.origin[0])
            current.localOrigin[1] = msg.ReadDeltaFloat(current.origin[1])
            current.localOrigin[2] = msg.ReadDeltaFloat(current.origin[2])
            current.pushVelocity[0] = msg.ReadDeltaFloat(
                0.0f,
                PLAYER_VELOCITY_EXPONENT_BITS,
                PLAYER_VELOCITY_MANTISSA_BITS
            )
            current.pushVelocity[1] = msg.ReadDeltaFloat(
                0.0f,
                PLAYER_VELOCITY_EXPONENT_BITS,
                PLAYER_VELOCITY_MANTISSA_BITS
            )
            current.pushVelocity[2] = msg.ReadDeltaFloat(
                0.0f,
                PLAYER_VELOCITY_EXPONENT_BITS,
                PLAYER_VELOCITY_MANTISSA_BITS
            )
            current.stepUp = msg.ReadDeltaFloat(0.0f)
            current.movementType = msg.ReadBits(PLAYER_MOVEMENT_TYPE_BITS)
            current.movementFlags = msg.ReadBits(PLAYER_MOVEMENT_FLAGS_BITS)
            current.movementTime = msg.ReadDeltaLong(0)
            if (clipModel != null) {
                clipModel!!.Link(Game_local.gameLocal.clip, self, 0, current.origin, clipModel!!.GetAxis())
            }
        }

        /*
         ============
         idPhysics_Player::CmdScale

         Returns the scale factor to apply to cmd movements
         This allows the clients to use axial -127 to 127 values for all directions
         without getting a sqrt(2) distortion in speed.
         ============
         */
        private fun CmdScale(cmd: usercmd_t): Float {
            var max: Int
            val total: Float
            val scale: Float
            val forwardmove: Int
            val rightmove: Int
            val upmove: Int
            forwardmove = cmd.forwardmove.toInt()
            rightmove = cmd.rightmove.toInt()

            // since the crouch key doubles as downward movement, ignore downward movement when we're on the ground
            // otherwise crouch speed will be lower than specified
            upmove = if (walking) {
                0
            } else {
                cmd.upmove.toInt()
            }
            max = Math.abs(forwardmove)
            if (Math.abs(rightmove) > max) {
                max = Math.abs(rightmove)
            }
            if (Math.abs(upmove) > max) {
                max = Math.abs(upmove)
            }
            if (0 == max) {
                return 0.0f
            }
            total = idMath.Sqrt(forwardmove.toFloat() * forwardmove + rightmove * rightmove + upmove * upmove)
            scale = playerSpeed * max / (127.0f * total)
            return scale
        }

        /*
         ==============
         idPhysics_Player::Accelerate

         Handles user intended acceleration
         ==============
         */
        private fun Accelerate(wishdir: idVec3, wishspeed: Float, accel: Float) {
            if (true) {
                // q2 style
                val addspeed: Float
                var accelspeed: Float
                val currentspeed: Float
                currentspeed = current.velocity.times(wishdir)
                addspeed = wishspeed - currentspeed
                if (addspeed <= 0) {
                    return
                }
                accelspeed = accel * frametime * wishspeed
                if (accelspeed > addspeed) {
                    accelspeed = addspeed
                }
                current.velocity.plusAssign(wishdir.times(accelspeed))
            }
        }

        private fun SlideMove(gravity: Boolean, stepUp: Boolean, stepDown: Boolean, push: Boolean): Boolean {
            var i: Int
            var j: Int
            var k: Int
            var pushFlags: Int
            var bumpcount: Int
            val numbumps: Int
            var numplanes: Int
            var d: Float
            var time_left: Float
            var into: Float
            var totalMass: Float
            val dir = idVec3()
            val planes: Array<idVec3> = idVec3.generateArray(MAX_CLIP_PLANES)
            val end = idVec3()
            val stepEnd = idVec3()
            val primal_velocity = idVec3()
            val endVelocity = idVec3()
            val endClipVelocity = idVec3()
            val clipVelocity = idVec3()
            var trace: trace_s = trace_s()
            val stepTrace = trace_s()
            val downTrace = trace_s()
            var nearGround: Boolean
            var stepped: Boolean
            var pushed: Boolean
            numbumps = 4
            primal_velocity.set(current.velocity)
            if (gravity) {
                endVelocity.set(current.velocity + gravityVector * frametime)
                current.velocity.set((current.velocity + endVelocity) * 0.5f)
                primal_velocity.set(endVelocity)
                if (groundPlane) {
                    // slide along the ground plane
                    current.velocity.ProjectOntoPlane(groundTrace.c.normal, OVERCLIP)
                }
            } else {
                endVelocity.set(current.velocity)
            }
            time_left = frametime

            // never turn against the ground plane
            if (groundPlane) {
                numplanes = 1
                planes[0].set(groundTrace.c.normal)
            } else {
                numplanes = 0
            }

            // never turn against original velocity
            planes[numplanes].set(current.velocity)
            planes[numplanes].Normalize()
            numplanes++

            bumpcount = 0
            while (bumpcount < numbumps) {


                // calculate position we are trying to move to
                end.set(current.origin + time_left * current.velocity)

                // see if we can make it there
                Game_local.gameLocal.clip.Translation(
                    trace,
                    current.origin,
                    end,
                    clipModel,
                    clipModel!!.GetAxis(),
                    clipMask,
                    self
                )
                time_left -= time_left * trace.fraction
                current.origin.set(trace.endpos)

                // if moved the entire distance
                if (trace.fraction >= 1.0f) {
                    break
                }
                pushed = false
                stepped = pushed

                // if we are allowed to step up
                if (stepUp) {
                    nearGround = groundPlane or ladder
                    if (!nearGround) {
                        // trace down to see if the player is near the ground
                        // step checking when near the ground allows the player to move up stairs smoothly while jumping
                        stepEnd.set(current.origin + maxStepHeight * gravityNormal)
                        Game_local.gameLocal.clip.Translation(
                            downTrace,
                            current.origin,
                            stepEnd,
                            clipModel,
                            clipModel!!.GetAxis(),
                            clipMask,
                            self
                        )
                        nearGround =
                            (downTrace.fraction < 1.0f && (downTrace.c.normal * -gravityNormal) > MIN_WALK_NORMAL)
                    }

                    // may only step up if near the ground or on a ladder
                    if (nearGround) {

                        // step up
                        stepEnd.set(current.origin - maxStepHeight * gravityNormal)
                        Game_local.gameLocal.clip.Translation(
                            downTrace,
                            current.origin,
                            stepEnd,
                            clipModel,
                            clipModel!!.GetAxis(),
                            clipMask,
                            self
                        )

                        // trace along velocity
                        stepEnd.set(downTrace.endpos + time_left * current.velocity)
                        Game_local.gameLocal.clip.Translation(
                            stepTrace,
                            downTrace.endpos,
                            stepEnd,
                            clipModel,
                            clipModel!!.GetAxis(),
                            clipMask,
                            self
                        )

                        // step down
                        stepEnd.set(stepTrace.endpos + maxStepHeight * gravityNormal)
                        Game_local.gameLocal.clip.Translation(
                            downTrace,
                            stepTrace.endpos,
                            stepEnd,
                            clipModel,
                            clipModel!!.GetAxis(),
                            clipMask,
                            self
                        )
                        if (downTrace.fraction >= 1.0f || (downTrace.c.normal * -gravityNormal) > MIN_WALK_NORMAL) {

                            // if moved the entire distance
                            if (stepTrace.fraction >= 1.0f) {
                                time_left = 0.0f
                                current.stepUp -= (downTrace.endpos - current.origin) * gravityNormal
                                current.origin.set(downTrace.endpos)
                                current.movementFlags = current.movementFlags or PMF_STEPPED_UP
                                current.velocity.timesAssign(PM_STEPSCALE)
                                break
                            }

                            // if the move is further when stepping up
                            if (stepTrace.fraction > trace.fraction) {
                                time_left -= time_left * stepTrace.fraction
                                current.stepUp -= (downTrace.endpos - current.origin) * gravityNormal
                                current.origin.set(downTrace.endpos)
                                current.movementFlags = current.movementFlags or PMF_STEPPED_UP
                                current.velocity.timesAssign(PM_STEPSCALE)
                                trace = stepTrace
                                stepped = true
                            }
                        }
                    }
                }

                // if we can push other entities and not blocked by the world
                if (push && trace.c.entityNum != Game_local.ENTITYNUM_WORLD) {
                    clipModel!!.SetPosition(current.origin, clipModel!!.GetAxis())

                    // clip movement, only push idMoveables, don't push entities the player is standing on
                    // apply impact to pushed objects
                    pushFlags =
                        Push.PUSHFL_CLIP or Push.PUSHFL_ONLYMOVEABLE or Push.PUSHFL_NOGROUNDENTITIES or Push.PUSHFL_APPLYIMPULSE

                    // clip & push
                    totalMass = Game_local.gameLocal.push.ClipTranslationalPush(
                        trace,
                        self!!,
                        pushFlags,
                        end,
                        end - current.origin
                    )
                    if (totalMass > 0.0f) {
                        // decrease velocity based on the total mass of the objects being pushed ?
                        current.velocity.timesAssign(
                            1.0f - idMath.ClampFloat(0.0f, 1000.0f, totalMass - 20.0f) * (1.0f / 950.0f)
                        )
                        pushed = true
                    }
                    current.origin.set(trace.endpos)
                    time_left -= time_left * trace.fraction

                    // if moved the entire distance
                    if (trace.fraction >= 1.0f) {
                        break
                    }
                }
                if (!stepped) {
                    // let the entity know about the collision
                    self!!.Collide(trace, current.velocity)
                }
                if (numplanes >= MAX_CLIP_PLANES) {
                    // MrElusive: I think we have some relatively high poly LWO models with a lot of slanted tris
                    // where it may hit the max clip planes
                    current.velocity.set(vec3_origin)
                    return true
                }

                //
                // if this is the same plane we hit before, nudge velocity
                // out along it, which fixes some epsilon issues with
                // non-axial planes
                //
                i = 0
                while (i < numplanes) {
                    if (trace.c.normal.times(planes[i]) > 0.999) {
                        current.velocity.plusAssign(trace.c.normal)
                        break
                    }
                    i++
                }
                if (i < numplanes) {
                    bumpcount++
                    continue
                }
                planes[numplanes].set(trace.c.normal)
                numplanes++

                //
                // modify velocity so it parallels all of the clip planes
                //
                // find a plane that it enters
                i = 0
                while (i < numplanes) {
                    into = current.velocity * planes[i]
                    if (into >= 0.1f) {
                        i++
                        continue  // move doesn't interact with the plane
                    }

                    // slide along the plane
                    clipVelocity.set(current.velocity)
                    clipVelocity.ProjectOntoPlane(planes[i], OVERCLIP)

                    // slide along the plane
                    endClipVelocity.set(endVelocity)
                    endClipVelocity.ProjectOntoPlane(planes[i], OVERCLIP)

                    // see if there is a second plane that the new move enters
                    j = 0
                    while (j < numplanes) {
                        if (j == i) {
                            j++
                            continue
                        }
                        if ((clipVelocity * planes[j]) >= 0.1f) {
                            j++
                            continue  // move doesn't interact with the plane
                        }

                        // try clipping the move to the plane
                        clipVelocity.ProjectOntoPlane(planes[j], OVERCLIP)
                        endClipVelocity.ProjectOntoPlane(planes[j], OVERCLIP)

                        // see if it goes back into the first clip plane
                        if ((clipVelocity * planes[i]) >= 0) {
                            j++
                            continue
                        }

                        // slide the original velocity along the crease
                        dir.set(planes[i].Cross(planes[j]))
                        dir.Normalize()
                        d = dir * current.velocity
                        clipVelocity.set(d * dir)
                        dir.set(planes[i].Cross(planes[j]))
                        dir.Normalize()
                        d = dir * endVelocity
                        endClipVelocity.set(d * dir)

                        // see if there is a third plane the the new move enters
                        k = 0
                        while (k < numplanes) {
                            if (k == i || k == j) {
                                k++
                                continue
                            }
                            if ((clipVelocity * planes[k]) >= 0.1f) {
                                k++
                                continue  // move doesn't interact with the plane
                            }

                            // stop dead at a tripple plane interaction
                            current.velocity.set(vec3_origin)
                            return true
                            k++
                        }
                        j++
                    }

                    // if we have fixed all interactions, try another move
                    current.velocity.set(clipVelocity)
                    endVelocity.set(endClipVelocity)
                    break
                    i++
                }
                bumpcount++
            }

            // step down
            if (stepDown && groundPlane) {
                stepEnd.set(current.origin + gravityNormal * maxStepHeight)
                Game_local.gameLocal.clip.Translation(
                    downTrace,
                    current.origin,
                    stepEnd,
                    clipModel,
                    clipModel!!.GetAxis(),
                    clipMask,
                    self
                )
                if (downTrace.fraction > 1e-4 && downTrace.fraction < 1.0f) {
                    current.stepUp -= (downTrace.endpos - current.origin) * gravityNormal
                    current.origin.set(downTrace.endpos)
                    current.movementFlags = current.movementFlags or PMF_STEPPED_DOWN
                    current.velocity.timesAssign(PM_STEPSCALE)
                }
            }
            if (gravity) {
                current.velocity.set(endVelocity)
            }

            // come to a dead stop when the velocity orthogonal to the gravity flipped
            clipVelocity.set(current.velocity - gravityNormal * current.velocity * gravityNormal)
            endClipVelocity.set(endVelocity - gravityNormal * endVelocity * gravityNormal)
            if (clipVelocity * endClipVelocity < 0.0f) {
                current.velocity.set(gravityNormal * current.velocity * gravityNormal)
            }
            return bumpcount == 0
        }

        /*
         ==================
         idPhysics_Player::Friction

         Handles both ground friction and water friction
         ==================
         */
        private fun Friction() {
            val vel = idVec3(current.velocity)
            val speed: Float
            var newspeed: Float
            val control: Float
            var drop: Float
            if (walking) {
                // ignore slope movement, remove all velocity in gravity direction
                vel.plusAssign((vel * gravityNormal) * gravityNormal)
            }
            speed = vel.Length()
            if (speed < 1.0f) {
                // remove all movement orthogonal to gravity, allows for sinking underwater
                if (abs(current.velocity * gravityNormal) < 1e-5) {
                    current.velocity.Zero()
                } else {
                    current.velocity.set((current.velocity * gravityNormal) * gravityNormal)
                }
                // FIXME: still have z friction underwater?
                return
            }
            drop = 0.0f

            // spectator friction
            if (current.movementType == TempDump.etoi(pmtype_t.PM_SPECTATOR)) {
                drop += speed * PM_FLYFRICTION * frametime
            } // apply ground friction
            else if (walking && TempDump.etoi(waterLevel) <= TempDump.etoi(waterLevel_t.WATERLEVEL_FEET)) {
                // no friction on slick surfaces
                if (!(groundMaterial != null && (groundMaterial!!.GetSurfaceFlags() and Material.SURF_SLICK) != 0)) {
                    // if getting knocked back, no friction
                    if ((current.movementFlags and PMF_TIME_KNOCKBACK) == 0) {
                        control = if (speed < PM_STOPSPEED) PM_STOPSPEED else speed
                        drop += control * PM_FRICTION * frametime
                    }
                }
            } // apply water friction even if just wading
            else if (waterLevel.ordinal != 0) {
                drop += speed * PM_WATERFRICTION * waterLevel.ordinal * frametime
            } // apply air friction
            else {
                drop += speed * PM_AIRFRICTION * frametime
            }

            // scale the velocity
            newspeed = speed - drop
            if (newspeed < 0) {
                newspeed = 0.0f
            }
            current.velocity.timesAssign((newspeed / speed))
        }

        /*
         ===================
         idPhysics_Player::WaterJumpMove

         Flying out of the water
         ===================
         */
        private fun WaterJumpMove() {

            // waterjump has no control, but falls
            SlideMove(true, true, false, false)

            // add gravity
            current.velocity.plusAssign(gravityNormal.times(frametime))
            // if falling down
            if (current.velocity.times(gravityNormal) > 0.0f) {
                // cancel as soon as we are falling down again
                current.movementFlags = current.movementFlags and PMF_ALL_TIMES.inv()
                current.movementTime = 0
            }
        }

        private fun WaterMove() {
            val wishvel = idVec3()
            var wishspeed: Float
            val wishdir = idVec3()
            val scale: Float
            val vel: Float
            if (CheckWaterJump()) {
                WaterJumpMove()
                return
            }
            Friction()
            scale = CmdScale(command)

            // user intentions
            if (0.0f == scale) {
                wishvel.set(gravityNormal.times(60.0f)) // sink towards bottom
            } else {
                wishvel.set(
                    viewForward.times(command.forwardmove.toFloat())
                        .plus(viewRight.times(command.rightmove.toFloat())).times(scale)
                )
                wishvel.minusAssign(gravityNormal.times(command.upmove.toFloat()).times(scale))
            }
            wishdir.set(wishvel)
            wishspeed = wishdir.Normalize()
            if (wishspeed > playerSpeed * PM_SWIMSCALE) {
                wishspeed = playerSpeed * PM_SWIMSCALE
            }
            Accelerate(wishdir, wishspeed, PM_WATERACCELERATE)

            // make sure we can go up slopes easily under water
            if (groundPlane && current.velocity.times(groundTrace.c.normal) < 0.0f) {
                vel = current.velocity.Length()
                // slide along the ground plane
                current.velocity.ProjectOntoPlane(groundTrace.c.normal, OVERCLIP)
                current.velocity.Normalize()
                current.velocity.timesAssign(vel)
            }
            SlideMove(false, true, false, false)
        }

        private fun FlyMove() {
            val wishvel = idVec3()
            val wishspeed: Float
            val wishdir = idVec3()
            val scale: Float

            // normal slowdown
            Friction()
            scale = CmdScale(command)
            if (0.0f == scale) {
                wishvel.set(vec3_origin)
            } else {
                wishvel.set(
                    viewForward.times(command.forwardmove.toFloat())
                        .plus(viewRight.times(command.rightmove.toFloat())).times(scale)
                )
                wishvel.minusAssign(gravityNormal.times(command.upmove.toFloat()).times(scale))
            }
            wishdir.set(wishvel)
            wishspeed = wishdir.Normalize()
            Accelerate(wishdir, wishspeed, PM_FLYACCELERATE)
            SlideMove(false, false, false, false)
        }

        private fun AirMove() {
            val wishvel = idVec3()
            val wishdir = idVec3()
            var wishspeed: Float
            val scale: Float
            Friction()
            scale = CmdScale(command)

            // project moves down to flat plane
            viewForward.minusAssign(gravityNormal.times(viewForward.times(gravityNormal)))
            viewRight.minusAssign(gravityNormal.times(viewRight.times(gravityNormal)))
            viewForward.Normalize()
            viewRight.Normalize()
            wishvel.set(
                viewForward.times(command.forwardmove.toFloat())
                    .plus(viewRight.times(command.rightmove.toFloat()))
            )
            wishvel.minusAssign(gravityNormal.times(wishvel.times(gravityNormal)))
            wishdir.set(wishvel)
            wishspeed = wishdir.Normalize()
            wishspeed *= scale

            // not on ground, so little effect on velocity
            Accelerate(wishdir, wishspeed, PM_AIRACCELERATE)

            // we may have a ground plane that is very steep, even
            // though we don't have a groundentity
            // slide along the steep plane
            if (groundPlane) {
                current.velocity.ProjectOntoPlane(groundTrace.c.normal, OVERCLIP)
            }
            SlideMove(true, false, false, false)
        }

        private fun WalkMove() {
            val wishvel = idVec3()
            val wishdir = idVec3()
            var wishspeed: Float
            val scale: Float
            val accelerate: Float
            val oldVelocity = idVec3()
            val vel = idVec3()
            val oldVel: Float
            val newVel: Float
            if (TempDump.etoi(waterLevel) > TempDump.etoi(waterLevel_t.WATERLEVEL_WAIST) && viewForward.times(
                    groundTrace.c.normal
                ) > 0.0f
            ) {
                // begin swimming
                WaterMove()
                return
            }
            if (CheckJump()) {
                // jumped away
                if (TempDump.etoi(waterLevel) > TempDump.etoi(waterLevel_t.WATERLEVEL_FEET)) {
                    WaterMove()
                } else {
                    AirMove()
                }
                return
            }
            Friction()
            scale = CmdScale(command)

            // project moves down to flat plane
            viewForward.minusAssign(gravityNormal.times(viewForward.times(gravityNormal)))
            viewRight.minusAssign(gravityNormal.times(viewRight.times(gravityNormal)))

            // project the forward and right directions onto the ground plane
            viewForward.ProjectOntoPlane(groundTrace.c.normal, OVERCLIP)
            viewRight.ProjectOntoPlane(groundTrace.c.normal, OVERCLIP)
            //
            viewForward.Normalize()
            viewRight.Normalize()
            wishvel.set(
                viewForward.times(command.forwardmove.toFloat())
                    .plus(viewRight.times(command.rightmove.toFloat()))
            )
            wishdir.set(wishvel)
            wishspeed = wishdir.Normalize()
            wishspeed *= scale

            // clamp the speed lower if wading or walking on the bottom
            if (waterLevel.ordinal != 0) {
                var waterScale: Float
                waterScale = waterLevel.ordinal / 3.0f
                waterScale = 1.0f - (1.0f - PM_SWIMSCALE) * waterScale
                if (wishspeed > playerSpeed * waterScale) {
                    wishspeed = playerSpeed * waterScale
                }
            }

            // when a player gets hit, they temporarily lose full control, which allows them to be moved a bit
            accelerate =
                if (groundMaterial != null && (groundMaterial!!.GetSurfaceFlags() and Material.SURF_SLICK) != 0 || (current.movementFlags and PMF_TIME_KNOCKBACK) != 0) {
                    PM_AIRACCELERATE
                } else {
                    PM_ACCELERATE
                }
            Accelerate(wishdir, wishspeed, accelerate)
            if (groundMaterial != null && (groundMaterial!!.GetSurfaceFlags() and Material.SURF_SLICK) != 0 || (current.movementFlags and PMF_TIME_KNOCKBACK) != 0) {
                current.velocity.plusAssign(gravityVector.times(frametime))
            }
            oldVelocity.set(current.velocity)

            // slide along the ground plane
            current.velocity.ProjectOntoPlane(groundTrace.c.normal, OVERCLIP)

            // if not clipped into the opposite direction
            if (oldVelocity.times(current.velocity) > 0.0f) {
                newVel = current.velocity.LengthSqr()
                if (newVel > 1.0f) {
                    oldVel = oldVelocity.LengthSqr()
                    if (oldVel > 1.0f) {
                        // don't decrease velocity when going up or down a slope
                        current.velocity.timesAssign(idMath.Sqrt(oldVel / newVel))
                    }
                }
            }

            // don't do anything if standing still
            vel.set(current.velocity.minus(gravityNormal.times(current.velocity.times(gravityNormal))))
            if (0.0f == vel.LengthSqr()) {
                return
            }
            Game_local.gameLocal.push.InitSavingPushedEntityPositions()
            SlideMove(false, true, true, true)
        }

        private fun DeadMove() {
            var forward: Float
            if (!walking) {
                return
            }

            // extra friction
            forward = current.velocity.Length()
            forward -= 20.0f
            if (forward <= 0) {
                current.velocity.set(vec3_origin)
            } else {
                current.velocity.Normalize()
                current.velocity.timesAssign(forward)
            }
        }

        private fun NoclipMove() {
            var speed: Float
            val drop: Float
            val friction: Float
            var newspeed: Float
            val stopspeed: Float
            val scale: Float
            var wishspeed: Float
            val wishdir = idVec3()

            // friction
            speed = current.velocity.Length()
            if (speed < 20.0f) {
                current.velocity.set(vec3_origin)
            } else {
                stopspeed = playerSpeed * 0.3f
                if (speed < stopspeed) {
                    speed = stopspeed
                }
                friction = PM_NOCLIPFRICTION
                drop = speed * friction * frametime

                // scale the velocity
                newspeed = speed - drop
                if (newspeed < 0) {
                    newspeed = 0.0f
                }
                current.velocity.timesAssign(newspeed / speed)
            }

            // accelerate
            scale = CmdScale(command)
            wishdir.set((viewForward * command.forwardmove.toInt() + viewRight * command.rightmove.toInt()) * scale)
            wishdir.minusAssign(scale * gravityNormal * command.upmove.toInt())
            wishspeed = wishdir.Normalize()
            wishspeed *= scale
            Accelerate(wishdir, wishspeed, PM_ACCELERATE)

            // move
            current.origin.plusAssign(frametime * current.velocity)
        }

        private fun SpectatorMove() {
            val wishvel = idVec3()
            val wishspeed: Float
            val wishdir = idVec3()
            val scale: Float
            idVec3()

            // fly movement
            Friction()
            scale = CmdScale(command)
            if (0.0f == scale) {
                wishvel.set(vec3_origin)
            } else {
                wishvel.set(
                    viewForward.times(command.forwardmove.toFloat())
                        .plus(viewRight.times(command.rightmove.toFloat())).times(scale)
                )
            }
            wishdir.set(wishvel)
            wishspeed = wishdir.Normalize()
            Accelerate(wishdir, wishspeed, PM_FLYACCELERATE)
            SlideMove(false, false, false, false)
        }

        private fun LadderMove() {
            idVec3()
            val wishvel = idVec3()
            val right = idVec3()
            val wishspeed: Float
            val scale: Float
            var upscale: Float

            // stick to the ladder
            wishvel.set(ladderNormal.times(-100.0f))
            current.velocity.set(gravityNormal.times(current.velocity.times(gravityNormal)).plus(wishvel))
            upscale = (gravityNormal.unaryMinus().times(viewForward) + 0.5f) * 2.5f
            if (upscale > 1.0f) {
                upscale = 1.0f
            } else if (upscale < -1.0f) {
                upscale = -1.0f
            }
            scale = CmdScale(command)
            wishvel.set(gravityNormal.times(upscale * scale * command.forwardmove.toFloat() * -0.9f))

            // strafe
            if (command.rightmove.toInt() != 0) {
                // right vector orthogonal to gravity
                right.set(viewRight.minus(gravityNormal.times(viewRight.times(gravityNormal))))
                // project right vector into ladder plane
                right.set(right.minus(ladderNormal.times(right.times(ladderNormal))))
                right.Normalize()

                // if we are looking away from the ladder, reverse the right vector
                if (ladderNormal.times(viewForward) > 0.0f) {
                    right.set(right.unaryMinus())
                }
                wishvel.plusAssign(right.times(scale * command.rightmove.toFloat() * 2.0f))
            }

            // up down movement
            if (command.upmove.toInt() != 0) {
                wishvel.plusAssign(gravityNormal.times(scale * command.upmove.toFloat() * -0.5f))
            }

            // do strafe friction
            Friction()

            // accelerate
            wishspeed = wishvel.Normalize()
            Accelerate(wishvel, wishspeed, PM_ACCELERATE)

            // cap the vertical velocity
            upscale = current.velocity.times(gravityNormal.unaryMinus())
            if (upscale < -PM_LADDERSPEED) {
                current.velocity.plusAssign(gravityNormal.times(upscale + PM_LADDERSPEED))
            } else if (upscale > PM_LADDERSPEED) {
                current.velocity.plusAssign(gravityNormal.times(upscale - PM_LADDERSPEED))
            }
            if (wishvel.times(gravityNormal) == 0.0f) {
                if (current.velocity.times(gravityNormal) < 0.0f) {
                    current.velocity.plusAssign(gravityVector.times(frametime))
                    if (current.velocity.times(gravityNormal) > 0.0f) {
                        current.velocity.minusAssign(gravityNormal.times(current.velocity.times(gravityNormal)))
                    }
                } else {
                    current.velocity.minusAssign(gravityVector.times(frametime))
                    if (current.velocity.times(gravityNormal) < 0.0f) {
                        current.velocity.minusAssign(gravityNormal.times(current.velocity.times(gravityNormal)))
                    }
                }
            }
            SlideMove(false, command.forwardmove > 0, false, false)
        }

        private fun CorrectAllSolid(trace: trace_s, contents: Int) {
            if (debugLevel != 0) {
                Game_local.gameLocal.Printf("%d:allsolid\n", c_pmove)
            }

            // FIXME: jitter around to find a free spot ?
            if (trace.fraction >= 1.0f) {
//		memset( &trace, 0, sizeof( trace ) );
                trace.endpos.set(current.origin)
                trace.endAxis.set(clipModelAxis)
                trace.fraction = 0.0f
                trace.c.dist = current.origin.z
                trace.c.normal.set(0.0f, 0.0f, 1.0f)
                trace.c.point.set(current.origin)
                trace.c.entityNum = Game_local.ENTITYNUM_WORLD
                trace.c.id = 0
                trace.c.type = contactType_t.CONTACT_TRMVERTEX
                trace.c.material = null
                trace.c.contents = contents
            }
        }

        private fun CheckGround() {
            var i: Int
            val contents: Int
            idVec3()
            val hadGroundContacts: Boolean
            hadGroundContacts = HasGroundContacts()

            // set the clip model origin before getting the contacts
            clipModel!!.SetPosition(current.origin, clipModel!!.GetAxis())
            EvaluateContacts()

            // setup a ground trace from the contacts
            groundTrace.endpos.set(current.origin)
            groundTrace.endAxis.set(clipModel!!.GetAxis())
            if (contacts.Num() != 0) {
                groundTrace.fraction = 0.0f
                groundTrace.c = contactInfo_t(contacts[0])
                i = 1
                while (i < contacts.Num()) {
                    groundTrace.c.normal.plusAssign(contacts[i].normal)
                    i++
                }
                groundTrace.c.normal.Normalize()
            } else {
                groundTrace.fraction = 1.0f
            }
            contents = Game_local.gameLocal.clip.Contents(current.origin, clipModel, clipModel!!.GetAxis(), -1, self)
            if ((contents and Game_local.MASK_SOLID) != 0) {
                // do something corrective if stuck in solid
                CorrectAllSolid(groundTrace, contents)
            }

            // if the trace didn't hit anything, we are in free fall
            if (groundTrace.fraction == 1.0f) {
                groundPlane = false
                walking = false
                groundEntityPtr.oSet(null)
                return
            }
            groundMaterial = groundTrace.c.material
            groundEntityPtr.oSet(Game_local.gameLocal.entities[groundTrace.c.entityNum])

            // check if getting thrown off the ground
            if (current.velocity.times(gravityNormal.unaryMinus()) > 0.0f && current.velocity.times(groundTrace.c.normal) > 10.0f) {
                if (debugLevel != 0) {
                    Game_local.gameLocal.Printf("%d:kickoff\n", c_pmove)
                }
                groundPlane = false
                walking = false
                return
            }

            // slopes that are too steep will not be considered onground
            if (groundTrace.c.normal.times(gravityNormal.unaryMinus()) < MIN_WALK_NORMAL) {
                if (debugLevel != 0) {
                    Game_local.gameLocal.Printf("%d:steep\n", c_pmove)
                }

                // FIXME: if they can't slide down the slope, let them walk (sharp crevices)
                // make sure we don't die from sliding down a steep slope
                if (current.velocity.times(gravityNormal) > 150.0f) {
                    current.velocity.minusAssign(gravityNormal.times(current.velocity.times(gravityNormal) - 150.0f))
                }
                groundPlane = true
                walking = false
                return
            }
            groundPlane = true
            walking = true

            // hitting solid ground will end a waterjump
            if ((current.movementFlags and PMF_TIME_WATERJUMP) != 0) {
                current.movementFlags =
                    current.movementFlags and (PMF_TIME_WATERJUMP or PMF_TIME_LAND).inv()
                current.movementTime = 0
            }

            // if the player didn't have ground contacts the previous frame
            if (!hadGroundContacts) {

                // don't do landing time if we were just going down a slope
                if (current.velocity.times(gravityNormal.unaryMinus()) < -200.0f) {
                    // don't allow another jump for a little while
                    current.movementFlags = current.movementFlags or PMF_TIME_LAND
                    current.movementTime = 250
                }
            }

            // let the entity know about the collision
            self!!.Collide(groundTrace, current.velocity)
            if (groundEntityPtr.GetEntity() != null) {
                val info = groundEntityPtr.GetEntity()!!.GetImpactInfo(self, groundTrace.c.id, groundTrace.c.point)
                if (info.invMass != 0.0f) {
                    groundEntityPtr.GetEntity()!!.ApplyImpulse(
                        self,
                        groundTrace.c.id,
                        groundTrace.c.point,
                        current.velocity.div(info.invMass * 10.0f)
                    )
                }
            }
        }

        /*
         ==============
         idPhysics_Player::CheckDuck

         Sets clip model size
         ==============
         */
        private fun CheckDuck() {
            val trace = trace_s()
            val end = idVec3()
            val bounds: idBounds
            val maxZ: Float
            if (current.movementType == TempDump.etoi(pmtype_t.PM_DEAD)) {
                maxZ = SysCvar.pm_deadheight.GetFloat()
            } else {
                // stand up when up against a ladder
                if (command.upmove < 0 && !ladder) {
                    // duck
                    current.movementFlags = current.movementFlags or PMF_DUCKED
                } else {
                    // stand up if possible
                    if ((current.movementFlags and PMF_DUCKED) != 0) {
                        // try to stand up
                        end.set(current.origin.minus(gravityNormal.times(SysCvar.pm_normalheight.GetFloat() - SysCvar.pm_crouchheight.GetFloat())))
                        Game_local.gameLocal.clip.Translation(
                            trace,
                            current.origin,
                            end,
                            clipModel,
                            clipModel!!.GetAxis(),
                            clipMask,
                            self
                        )
                        if (trace.fraction >= 1.0f) {
                            current.movementFlags = current.movementFlags and PMF_DUCKED.inv()
                        }
                    }
                }
                if ((current.movementFlags and PMF_DUCKED) != 0) {
                    playerSpeed = crouchSpeed
                    maxZ = SysCvar.pm_crouchheight.GetFloat()
                } else {
                    maxZ = SysCvar.pm_normalheight.GetFloat()
                }
            }
            // if the clipModel height should change
            if (clipModel!!.GetBounds()[1, 2] != maxZ) {
                bounds = clipModel!!.GetBounds()
                bounds[1, 2] = maxZ
                if (SysCvar.pm_usecylinder.GetBool()) {
                    clipModel!!.LoadModel(idTraceModel(bounds, 8))
                } else {
                    clipModel!!.LoadModel(idTraceModel(bounds))
                }
            }
        }

        private fun CheckLadder() {
            val forward = idVec3()
            val start = idVec3()
            val end = idVec3()
            val trace = trace_s()
            val tracedist: Float
            if (current.movementTime != 0) {
                return
            }

            // if on the ground moving backwards
            if (walking && command.forwardmove <= 0) {
                return
            }

            // forward vector orthogonal to gravity
            forward.set(viewForward.minus(gravityNormal.times(viewForward.times(gravityNormal))))
            forward.Normalize()
            tracedist = if (walking) {
                // don't want to get sucked towards the ladder when still walking
                1.0f
            } else {
                48.0f
            }
            end.set(current.origin.plus(forward.times(tracedist)))
            Game_local.gameLocal.clip.Translation(
                trace,
                current.origin,
                end,
                clipModel,
                clipModel!!.GetAxis(),
                clipMask,
                self
            )

            // if near a surface
            if (trace.fraction < 1.0f) {

                // if a ladder surface
                if (trace.c.material != null
                    && (trace.c.material!!.GetSurfaceFlags() and Material.SURF_LADDER) != 0
                ) {

                    // check a step height higher
                    end.set(current.origin.minus(gravityNormal.times(maxStepHeight * 0.75f)))
                    Game_local.gameLocal.clip.Translation(
                        trace,
                        current.origin,
                        end,
                        clipModel,
                        clipModel!!.GetAxis(),
                        clipMask,
                        self
                    )
                    start.set(trace.endpos)
                    end.set(start.plus(forward.times(tracedist)))
                    Game_local.gameLocal.clip.Translation(
                        trace,
                        start,
                        end,
                        clipModel,
                        clipModel!!.GetAxis(),
                        clipMask,
                        self
                    )

                    // if also near a surface a step height higher
                    if (trace.fraction < 1.0f) {

                        // if it also is a ladder surface
                        if (trace.c.material != null
                            && (trace.c.material!!.GetSurfaceFlags() and Material.SURF_LADDER) != 0
                        ) {
                            ladder = true
                            ladderNormal.set(trace.c.normal)
                        }
                    }
                }
            }
        }

        private fun CheckJump(): Boolean {
            val addVelocity = idVec3()
            if (command.upmove < 10) {
                // not holding jump
                return false
            }

            // must wait for jump to be released
            if ((current.movementFlags and PMF_JUMP_HELD) != 0) {
                return false
            }

            // don't jump if we can't stand up
            if ((current.movementFlags and PMF_DUCKED) != 0) {
                return false
            }
            groundPlane = false // jumping away
            walking = false
            current.movementFlags = current.movementFlags or (PMF_JUMP_HELD or PMF_JUMPED)
            addVelocity.set(gravityVector.unaryMinus().times(2.0f * maxJumpHeight))
            addVelocity.timesAssign(idMath.Sqrt(addVelocity.Normalize()))
            current.velocity.plusAssign(addVelocity)
            return true
        }

        private fun CheckWaterJump(): Boolean {
            val spot = idVec3()
            var cont: Int
            val flatforward = idVec3()
            if (current.movementTime != 0) {
                return false
            }

            // check for water jump
            if (waterLevel != waterLevel_t.WATERLEVEL_WAIST) {
                return false
            }
            flatforward.set(viewForward.minus(gravityNormal.times(viewForward.times(gravityNormal))))
            flatforward.Normalize()
            spot.set(current.origin.plus(flatforward.times(30.0f)))
            spot.minusAssign(gravityNormal.times(4.0f))
            cont = Game_local.gameLocal.clip.Contents(spot, null, idMat3.getMat3_identity(), -1, self)
            if (0 == cont and Material.CONTENTS_SOLID) {
                return false
            }
            spot.minusAssign(gravityNormal.times(16.0f))
            cont = Game_local.gameLocal.clip.Contents(spot, null, idMat3.getMat3_identity(), -1, self)
            if (cont != 0) {
                return false
            }

            // jump out of water
            current.velocity.set(viewForward.times(200.0f).minus(gravityNormal.times(350.0f)))
            current.movementFlags = current.movementFlags or PMF_TIME_WATERJUMP
            current.movementTime = 2000
            return true
        }

        private fun SetWaterLevel() {
            val point = idVec3()
            val bounds: idBounds
            var contents: Int

            //
            // get waterlevel, accounting for ducking
            //
            waterLevel = waterLevel_t.WATERLEVEL_NONE
            waterType = 0
            bounds = clipModel!!.GetBounds()

            // check at feet level
            point.set(current.origin.minus(gravityNormal.times(bounds[0, 2] + 1.0f)))
            contents = Game_local.gameLocal.clip.Contents(point, null, idMat3.getMat3_identity(), -1, self)
            if ((contents and Game_local.MASK_WATER) != 0) {
                waterType = contents
                waterLevel = waterLevel_t.WATERLEVEL_FEET

                // check at waist level
                point.set(
                    current.origin.minus(
                        gravityNormal.times(
                            (bounds[1, 2] - bounds[0, 2]) * 0.5f
                        )
                    )
                )
                contents =
                    Game_local.gameLocal.clip.Contents(point, null, idMat3.getMat3_identity(), -1, self)
                if ((contents and Game_local.MASK_WATER) != 0) {
                    waterLevel = waterLevel_t.WATERLEVEL_WAIST

                    // check at head level
                    point.set(current.origin.minus(gravityNormal.times(bounds[1, 2] - 1.0f)))
                    contents =
                        Game_local.gameLocal.clip.Contents(point, null, idMat3.getMat3_identity(), -1, self)
                    if ((contents and Game_local.MASK_WATER) != 0) {
                        waterLevel = waterLevel_t.WATERLEVEL_HEAD
                    }
                }
            }
        }

        private fun DropTimers() {
            // drop misc timing counter
            if (current.movementTime != 0) {
                if (framemsec >= current.movementTime) {
                    current.movementFlags = current.movementFlags and PMF_ALL_TIMES.inv()
                    current.movementTime = 0
                } else {
                    current.movementTime -= framemsec
                }
            }
        }

        private fun MovePlayer(msec: Int) {

            // this counter lets us debug movement problems with a journal
            // by setting a conditional breakpoint for the previous frame
            c_pmove++
            walking = false
            groundPlane = false
            ladder = false

            // determine the time
            framemsec = msec
            frametime = framemsec * 0.001f

            // default speed
            playerSpeed = walkSpeed

            // remove jumped and stepped up flag
            current.movementFlags =
                current.movementFlags and (PMF_JUMPED or PMF_STEPPED_UP or PMF_STEPPED_DOWN).inv()
            current.stepUp = 0.0f
            if (command.upmove < 10) {
                // not holding jump
                current.movementFlags = current.movementFlags and PMF_JUMP_HELD.inv()
            }

            // if no movement at all
            if (current.movementType == TempDump.etoi(pmtype_t.PM_FREEZE)) {
                return
            }

            // move the player velocity into the frame of a pusher
            current.velocity.minusAssign(current.pushVelocity)

            // view vectors
            viewAngles.ToVectors(viewForward, null, null)
            viewForward.timesAssign(clipModelAxis)
            viewRight.set(gravityNormal.Cross(viewForward))
            viewRight.Normalize()

            // fly in spectator mode
            if (current.movementType == TempDump.etoi(pmtype_t.PM_SPECTATOR)) {
                SpectatorMove()
                DropTimers()
                return
            }

            // special no clip mode
            if (current.movementType == TempDump.etoi(pmtype_t.PM_NOCLIP)) {
                NoclipMove()
                DropTimers()
                return
            }

            // no control when dead
            if (current.movementType == TempDump.etoi(pmtype_t.PM_DEAD)) {
                command.forwardmove = 0
                command.rightmove = 0
                command.upmove = 0
            }

            // set watertype and waterlevel
            SetWaterLevel()

            // check for ground
            CheckGround()

            // check if up against a ladder
            CheckLadder()

            // set clip model size
            CheckDuck()

            // handle timers
            DropTimers()

            // move
            if (current.movementType == TempDump.etoi(pmtype_t.PM_DEAD)) {
                // dead
                DeadMove()
            } else if (ladder) {
                // going up or down a ladder
                LadderMove()
            } else if ((current.movementFlags and PMF_TIME_WATERJUMP) != 0) {
                // jumping out of water
                WaterJumpMove()
            } else if (TempDump.etoi(waterLevel) > 1) {
                // swimming
                WaterMove()
            } else if (walking) {
                // walking on ground
                WalkMove()
            } else {
                // airborne
                AirMove()
            }

            // set watertype, waterlevel and groundentity
            SetWaterLevel()
            CheckGround()

            // move the player velocity back into the world frame
            current.velocity.plusAssign(current.pushVelocity)
            current.pushVelocity.Zero()
        }

        companion object {
            // CLASS_PROTOTYPE( idPhysics_Player );
            /*
         ==================
         idPhysics_Player::SlideMove

         Returns true if the velocity was clipped in some way
         ==================
         */
            const val MAX_CLIP_PLANES = 5
        }

        init {
            //false;
            clipModel = null
            clipMask = 0
            current = playerPState_s() //memset( &current, 0, sizeof( current ) );
            saved = playerPState_s()
            saved.set(current)
            walkSpeed = 0.0f
            crouchSpeed = 0.0f
            maxStepHeight = 0.0f
            maxJumpHeight = 0.0f
            command = usercmd_t() //memset( &command, 0, sizeof( command ) );
            viewAngles = idAngles()
            framemsec = 0
            frametime = 0.0f
            playerSpeed = 0.0f
            viewForward = idVec3()
            viewRight = idVec3()
            walking = false
            groundPlane = false
            groundTrace = trace_s() //memset( &groundTrace, 0, sizeof( groundTrace ) );
            groundMaterial = null
            ladder = false
            ladderNormal = idVec3()
            waterLevel = waterLevel_t.WATERLEVEL_NONE
            waterType = 0
        }
    }
}
/*
 * Copyright (C) 1999-2011 id Software LLC, a ZeniMax Media company.
 * Translated to Kotlin by Dr. Feederino with support of Claude Code
 *
 * This file is part of the Doom 3 Kotlin project.
 * Original source: neo/Game/Physics/Physics_AF.h, neo/Game/Physics/Physics_AF.cpp
 */

package neo.Game.Physics

import neo.Game.GameSys.Class.idClass
import neo.Game.GameSys.SaveGame.idRestoreGame
import neo.Game.GameSys.SaveGame.idSaveGame
import neo.Game.GameSys.SysCvar
import neo.Game.Game_local
import neo.Game.Game_local.idGameLocal
import neo.Game.Physics.Clip.idClipModel
import neo.Game.Physics.Physics.impactInfo_s
import neo.Game.Physics.Physics_Base.idPhysics_Base
import neo.Game.TH_PHYSICS
import neo.Game.idEntity
import neo.cm.collisionModelManager
import neo.cm.contactInfo_t
import neo.cm.trace_s
import neo.framework.UsercmdGen
import neo.idlib.*
import neo.idlib.BV.idBounds
import neo.idlib.BitMsg.idBitMsgDelta
import neo.idlib.Text.Str
import neo.idlib.Text.Str.idStr
import neo.idlib.Timer.idTimer
import neo.idlib.containers.CBool
import neo.idlib.containers.CFloat
import neo.idlib.containers.CInt
import neo.idlib.containers.List.idList
import neo.idlib.geometry.TraceModel
import neo.idlib.math.*
import neo.idlib.math.Matrix.idMat3
import neo.idlib.math.Matrix.idMatX
import java.util.*
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

object Physics_AF {
    const val AF_TIMINGS = true
    const val CENTER_OF_MASS_EPSILON = 1e-4f
    const val CONTACT_LCP_EPSILON = 1e-6f
    const val ERROR_REDUCTION = 0.5f
    const val ERROR_REDUCTION_MAX = 256.0f
    const val IMPULSE_THRESHOLD = 500.0f
    const val LCP_EPSILON = 1e-7f
    const val LIMIT_ERROR_REDUCTION = 0.3f
    const val LIMIT_LCP_EPSILON = 1e-4f
    const val MAX_MOVE_TIME = -1.0f
    const val MIN_MOVE_TIME = -1.0f
    const val NO_MOVE_ROTATION_TOLERANCE = 10.0f
    const val NO_MOVE_TIME = 1.0f
    const val NO_MOVE_TRANSLATION_TOLERANCE = 10.0f
    const val SUSPEND_ANGULAR_ACCELERATION = 30.0f
    const val SUSPEND_ANGULAR_VELOCITY = 15.0f
    const val SUSPEND_LINEAR_ACCELERATION = 20.0f
    const val SUSPEND_LINEAR_VELOCITY = 10.0f
    const val TEST_COLLISION_DETECTION = false
    private val vec6_lcp_epsilon: idVec6 = idVec6(
        LCP_EPSILON,
        LCP_EPSILON,
        LCP_EPSILON,
        LCP_EPSILON,
        LCP_EPSILON,
        LCP_EPSILON
    )

    // #ifdef AF_TIMINGS
    var lastTimerReset = 0
    var numArticulatedFigures = 0
    var timer_total: idTimer = idTimer()
    var timer_pc: idTimer = idTimer()
    var timer_ac: idTimer = idTimer()
    var timer_collision: idTimer = idTimer()
    var timer_lcp: idTimer = idTimer()

    /*
     ================
     idPhysics_AF_SavePState
     ================
     */
    fun idPhysics_AF_SavePState(saveFile: idSaveGame, state: AFPState_s) {
        saveFile.WriteInt(state.atRest)
        saveFile.WriteFloat(state.noMoveTime)
        saveFile.WriteFloat(state.activateTime)
        saveFile.WriteFloat(state.lastTimeStep)
        saveFile.WriteVec6(state.pushVelocity)
    }

    // #endif
    /*
     ================
     idPhysics_AF_RestorePState
     ================
     */
    fun idPhysics_AF_RestorePState(saveFile: idRestoreGame, state: AFPState_s) {
        val atRest = CInt()
        val noMoveTime = CFloat()
        val activateTime = CFloat()
        val lastTimeStep = CFloat()
        saveFile.ReadInt(atRest)
        saveFile.ReadFloat(noMoveTime)
        saveFile.ReadFloat(activateTime)
        saveFile.ReadFloat(lastTimeStep)
        saveFile.ReadVec6(state.pushVelocity)
        state.atRest = atRest._val
        state.noMoveTime = noMoveTime._val
        state.activateTime = activateTime._val
        state.lastTimeStep = lastTimeStep._val
    }

    /*
     ===================================================================================

     Articulated Figure physics

     Employs a constraint force based dynamic simulation using a lagrangian
     multiplier method to solve for the constraint forces.

     ===================================================================================
     */
    enum class constraintType_t {
        CONSTRAINT_INVALID, CONSTRAINT_FIXED, CONSTRAINT_BALLANDSOCKETJOINT, CONSTRAINT_UNIVERSALJOINT, CONSTRAINT_HINGE, CONSTRAINT_HINGESTEERING, CONSTRAINT_SLIDER, CONSTRAINT_CYLINDRICALJOINT, CONSTRAINT_LINE, CONSTRAINT_PLANE, CONSTRAINT_SPRING, CONSTRAINT_CONTACT, CONSTRAINT_FRICTION, CONSTRAINT_CONELIMIT, CONSTRAINT_PYRAMIDLIMIT, CONSTRAINT_SUSPENSION;

        companion object {
            fun oGet(index: Int): constraintType_t {
                return if (index >= values().size) {
                    values()[0]
                } else {
                    values()[index]
                }
            }
        }
    }

    //===============================================================
    //
    //	idAFConstraint
    //
    //===============================================================
    // base class for all constraints
    open class idAFConstraint {
        val J: idMatX = idMatX() // transformed constraint matrix

        //
        // simulation variables set by Evaluate
        val J1: idMatX = idMatX()
        val J2: idMatX = idMatX() // matrix with left hand side of constraint equations
        val boxIndex: IntArray = IntArray(6) // indexes for special box constrained variables
        val c1: idVecX = idVecX()
        val c2: idVecX = idVecX() // right hand side of constraint equations

        //
        // simulation variables used during calculations
        val invI: idMatX = idMatX() // transformed inertia
        val lm: idVecX = idVecX() // lagrange multipliers
        val lo: idVecX
        val hi: idVecX
        val e // low and high bounds and lcp epsilon
                : idVecX
        val s: idVecX = idVecX() // temp solution
        var body1 // first constrained body
                : idAFBody?
        var body2 // second constrained body, NULL for world
                : idAFBody?
        var boxConstraint // constraint the boxIndex refers to
                : idAFConstraint?
        var firstIndex // index of the first constraint row in the lcp matrix
                : Int
        var fl: constraintFlags_s
        val name // name of constraint
                : idStr
        var physics // for adding additional constraints like limits
                : idPhysics_AF?

        //
        protected var type // constraint type
                : constraintType_t

        //
        //
        fun GetType(): constraintType_t {
            return type
        }

        // virtual					~idAFConstraint( void );
        fun GetName(): idStr {
            return name
        }

        fun GetBody1(): idAFBody? {
            return body1
        }

        fun GetBody2(): idAFBody? {
            return body2
        }

        fun SetPhysics(p: idPhysics_AF?) {
            physics = p
        }

        fun GetMultiplier(): idVecX {
            return lm
        }

        open fun SetBody1(body: idAFBody?) {
            if (body1 != body) {
                body1 = body
                physics?.SetChanged()
            }
        }

        open fun SetBody2(body: idAFBody?) {
            if (body2 != body) {
                body2 = body
                physics?.SetChanged()
            }
        }

        open fun DebugDraw() {}
        open fun GetForce(body: idAFBody?, force: idVec6) {
            val v = idVecX()
            v.SetData(6, idVecX.VECX_ALLOCA(6))
            if (body == body1) {
                J1.TransposeMultiply(v, lm)
            } else if (body == body2) {
                J2.TransposeMultiply(v, lm)
            } else {
                v.Zero()
            }
            force.p[0] = v.p[0]
            force.p[1] = v.p[1]
            force.p[2] = v.p[2]
            force.p[3] = v.p[3]
            force.p[4] = v.p[4]
            force.p[5] = v.p[5]
        }

        open fun Translate(translation: idVec3) {
            assert(false)
        }

        open fun Rotate(rotation: idRotation) {
            assert(false)
        }

        open fun GetCenter(center: idVec3) {
            center.Zero()
        }

        open fun Save(saveFile: idSaveGame) {
            saveFile.WriteInt(type.ordinal)
        }

        open fun Restore(saveFile: idRestoreGame) {
            val t = CInt()
            saveFile.ReadInt(t)
            assert(t._val == type.ordinal)
        }

        open fun Evaluate(invTimeStep: Float) {
            assert(false)
        }

        open fun ApplyFriction(invTimeStep: Float) {}
        protected fun InitSize(size: Int) {
            J1.Zero(size, 6)
            J2.Zero(size, 6)
            c1.Zero(size)
            c2.Zero(size)
            s.Zero(size)
            lm.Zero(size)
        }

        class constraintFlags_s {
            var allowPrimary //: 1;             // true if the constraint can be used as a primary constraint
                    = false
            var frameConstraint //: 1;	        // true if this constraint is added to the frame constraints
                    = false
            var isPrimary //: 1;                // true if this is a primary constraint
                    = false
            var isZero //: 1;                   // true if 's' is zero during calculations
                    = false
            var noCollision //: 1;              // true if body1 and body2 never collide with each other
                    = false
        }

        // friend class idPhysics_AF;
        // friend class idAFTree;
        init {
            type = constraintType_t.CONSTRAINT_INVALID
            name = idStr("noname")
            body1 = null
            body2 = null
            physics = null
            lo = idVecX(6)
            lo.SubVec6_oSet(0, getVec6_infinity().unaryMinus())
            hi = idVecX(6)
            hi.SubVec6_oSet(0, getVec6_infinity())
            e = idVecX(6)
            e.SubVec6_oSet(0, vec6_lcp_epsilon)
            boxConstraint = null
            boxIndex[5] = -1
            boxIndex[4] = boxIndex[5]
            boxIndex[3] = boxIndex[4]
            boxIndex[2] = boxIndex[3]
            boxIndex[1] = boxIndex[2]
            boxIndex[0] = boxIndex[1]
            firstIndex = 0

//	memset( &fl, 0, sizeof( fl ) );
            fl = constraintFlags_s()
        }
    }

    //===============================================================
    //
    //	idAFConstraint_Fixed
    //
    //===============================================================
    // fixed or rigid joint which allows zero degrees of freedom
    // constrains body1 to have a fixed position and orientation relative to body2
    class idAFConstraint_Fixed(name: idStr, body1: idAFBody?, body2: idAFBody?) : idAFConstraint() {
        //
        //
        protected val offset: idVec3 = idVec3() // offset of body1 relative to body2 in body2 space
        protected val relAxis: idMat3 = idMat3() // rotation of body1 relative to body2
        fun SetRelativeOrigin(origin: idVec3) {
            offset.set(origin)
        }

        fun SetRelativeAxis(axis: idMat3) {
            relAxis.set(axis)
        }

        override fun SetBody1(body: idAFBody?) {
            if (body1 != body) {
                body1 = body
                InitOffset()
                physics?.SetChanged()
            }
        }

        override fun SetBody2(body: idAFBody?) {
            if (body2 != body) {
                body2 = body
                InitOffset()
                physics?.SetChanged()
            }
        }

        override fun DebugDraw() {
            val master: idAFBody?
            master = if (body2 != null) body2 else physics!!.GetMasterBody()
            if (master != null) {
                Game_local.gameRenderWorld!!.DebugLine(
                    colorRed,
                    body1!!.GetWorldOrigin(),
                    master.GetWorldOrigin()
                )
            } else {
                Game_local.gameRenderWorld!!.DebugLine(
                    colorRed,
                    body1!!.GetWorldOrigin(),
                    getVec3Origin()
                )
            }
        }

        override fun Translate(translation: idVec3) {
            if (null == body2) {
                offset.plusAssign(translation)
            }
        }

        override fun Rotate(rotation: idRotation) {
            if (null == body2) {
                offset.timesAssign(rotation)
                relAxis.timesAssign(rotation.ToMat3())
            }
        }

        override fun GetCenter(center: idVec3) {
            center.set(body1!!.GetWorldOrigin())
        }

        override fun Save(saveFile: idSaveGame) {
            super.Save(saveFile)
            saveFile.WriteVec3(offset)
            saveFile.WriteMat3(relAxis)
        }

        override fun Restore(saveFile: idRestoreGame) {
            super.Restore(saveFile)
            saveFile.ReadVec3(offset)
            saveFile.ReadMat3(relAxis)
        }

        override fun Evaluate(invTimeStep: Float) {
            val ofs = idVec3()
            val a2 = idVec3()
            val ax: idMat3
            val r: idRotation
            val master: idAFBody?
            master = if (body2 != null) body2 else physics!!.GetMasterBody()
            ax = if (master != null) {
                a2.set(offset.times(master.GetWorldAxis()))
                ofs.set(a2.plus(master.GetWorldOrigin()))
                relAxis.times(master.GetWorldAxis())
            } else {
                a2.Zero()
                ofs.set(offset)
                relAxis
            }
            J1.set(
                idMat3.getMat3_identity(),
                idMat3.getMat3_zero(),
                idMat3.getMat3_zero(),
                idMat3.getMat3_identity()
            )
            if (body2 != null) {
                J2.set(
                    idMat3.getMat3_identity().unaryMinus(),
                    idMat3.SkewSymmetric(a2),
                    idMat3.getMat3_zero(),
                    idMat3.getMat3_identity().unaryMinus()
                )
            } else {
                J2.Zero(6, 6)
            }
            c1.SubVec3_oSet(
                0,
                ofs.minus(body1!!.GetWorldOrigin()).times(-(invTimeStep * ERROR_REDUCTION))
            )
            r = body1!!.GetWorldAxis().Transpose().times(ax).ToRotation()
            c1.SubVec3_oSet(
                1,
                r.GetVec().times(-DEG2RAD(r.GetAngle()))
                    .times(-(invTimeStep * ERROR_REDUCTION))
            )
            c1.Clamp(-ERROR_REDUCTION_MAX, ERROR_REDUCTION_MAX)
        }

        override fun ApplyFriction(invTimeStep: Float) {
            // no friction
        }

        protected fun InitOffset() {
            if (body2 != null) {
                offset.set(
                    body1!!.GetWorldOrigin().minus(body2!!.GetWorldOrigin()).times(body2!!.GetWorldAxis().Transpose())
                )
                relAxis.set(body1!!.GetWorldAxis().times(body2!!.GetWorldAxis().Transpose()))
            } else {
                offset.set(body1!!.GetWorldOrigin())
                relAxis.set(body1!!.GetWorldAxis())
            }
        }

        init {
            assert(body1 != null)
            type = constraintType_t.CONSTRAINT_FIXED
            this.name.set(name)
            this.body1 = body1
            this.body2 = body2
            InitSize(6)
            fl.allowPrimary = true
            fl.noCollision = true
            InitOffset()
        }
    }

    //===============================================================
    //
    //	idAFConstraint_BallAndSocketJoint
    //
    //===============================================================
    // ball and socket or spherical joint which allows 3 degrees of freedom
    // constrains body1 relative to body2 with a ball and socket joint
    class idAFConstraint_BallAndSocketJoint(name: idStr, body1: idAFBody?, body2: idAFBody?) : idAFConstraint() {
        protected val anchor1: idVec3 = idVec3() // anchor in body1 space
        protected val anchor2: idVec3 = idVec3() // anchor in body2 space
        protected var coneLimit // cone shaped limit
                : idAFConstraint_ConeLimit?
        protected var fc // friction constraint
                : idAFConstraint_BallAndSocketJointFriction?
        protected var friction // joint friction
                : Float
        protected var pyramidLimit // pyramid shaped limit
                : idAFConstraint_PyramidLimit?

        // ~idAFConstraint_BallAndSocketJoint( void );
        fun SetAnchor(worldPosition: idVec3) {

            // get anchor relative to center of mass of body1
            anchor1.set(worldPosition.minus(body1!!.GetWorldOrigin()).times(body1!!.GetWorldAxis().Transpose()))
            if (body2 != null) {
                // get anchor relative to center of mass of body2
                anchor2.set(worldPosition.minus(body2!!.GetWorldOrigin()).times(body2!!.GetWorldAxis().Transpose()))
            } else {
                anchor2.set(worldPosition)
            }
            if (coneLimit != null) {
                coneLimit!!.SetAnchor(anchor2)
            }
            if (pyramidLimit != null) {
                pyramidLimit!!.SetAnchor(anchor2)
            }
        }

        fun GetAnchor(): idVec3 {
            return if (body2 != null) {
                body2!!.GetWorldOrigin().plus(body2!!.GetWorldAxis().times(anchor2))
            } else anchor2
        }

        fun SetNoLimit() {
            if (coneLimit != null) {
//		delete coneLimit;
                coneLimit = null
            }
            if (pyramidLimit != null) {
//		delete pyramidLimit;
                pyramidLimit = null
            }
        }

        fun SetConeLimit(coneAxis: idVec3, coneAngle: Float, body1Axis: idVec3) {
            if (pyramidLimit != null) {
                pyramidLimit = null
            }
            if (null == coneLimit) {
                coneLimit = idAFConstraint_ConeLimit()
                coneLimit!!.SetPhysics(physics)
            }
            if (body2 != null) {
                coneLimit!!.Setup(
                    body1,
                    body2,
                    anchor2,
                    coneAxis.times(body2!!.GetWorldAxis().Transpose()),
                    coneAngle,
                    body1Axis.times(body1!!.GetWorldAxis().Transpose())
                )
            } else {
                coneLimit!!.Setup(
                    body1,
                    body2,
                    anchor2,
                    coneAxis,
                    coneAngle,
                    body1Axis.times(body1!!.GetWorldAxis().Transpose())
                )
            }
        }

        fun SetPyramidLimit(pyramidAxis: idVec3, baseAxis: idVec3, angle1: Float, angle2: Float, body1Axis: idVec3) {
            if (coneLimit != null) {
                coneLimit = null
            }
            if (null == pyramidLimit) {
                pyramidLimit = idAFConstraint_PyramidLimit()
                pyramidLimit!!.SetPhysics(physics)
            }
            if (body2 != null) {
                pyramidLimit!!.Setup(
                    body1,
                    body2,
                    anchor2,
                    pyramidAxis.times(body2!!.GetWorldAxis().Transpose()),
                    baseAxis.times(body2!!.GetWorldAxis().Transpose()),
                    angle1,
                    angle2,
                    body1Axis.times(body1!!.GetWorldAxis().Transpose())
                )
            } else {
                pyramidLimit!!.Setup(
                    body1,
                    body2,
                    anchor2,
                    pyramidAxis,
                    baseAxis,
                    angle1,
                    angle2,
                    body1Axis.times(body1!!.GetWorldAxis().Transpose())
                )
            }
        }

        fun SetLimitEpsilon(e: Float) {
            if (coneLimit != null) {
                coneLimit!!.SetEpsilon(e)
            }
            if (pyramidLimit != null) {
                pyramidLimit!!.SetEpsilon(e)
            }
        }

        fun SetFriction(f: Float) {
            friction = f
        }

        fun GetFriction(): Float {
            return if (SysCvar.af_forceFriction.GetFloat() > 0.0f) {
                SysCvar.af_forceFriction.GetFloat()
            } else friction * physics!!.GetJointFrictionScale()
        }

        override fun DebugDraw() {
            val a1 = idVec3(body1!!.GetWorldOrigin().plus(anchor1.times(body1!!.GetWorldAxis())))
            Game_local.gameRenderWorld!!.DebugLine(
                colorBlue,
                a1.minus(idVec3(5, 0, 0)),
                a1.plus(idVec3(5, 0, 0))
            )
            Game_local.gameRenderWorld!!.DebugLine(
                colorBlue,
                a1.minus(idVec3(0, 5, 0)),
                a1.plus(idVec3(0, 5, 0))
            )
            Game_local.gameRenderWorld!!.DebugLine(
                colorBlue,
                a1.minus(idVec3(0, 0, 5)),
                a1.plus(idVec3(0, 0, 5))
            )
            if (SysCvar.af_showLimits.GetBool()) {
                if (coneLimit != null) {
                    coneLimit!!.DebugDraw()
                }
                if (pyramidLimit != null) {
                    pyramidLimit!!.DebugDraw()
                }
            }
        }

        override fun Translate(translation: idVec3) {
            if (null == body2) {
                anchor2.plusAssign(translation)
            }
            if (coneLimit != null) {
                coneLimit!!.Translate(translation)
            } else if (pyramidLimit != null) {
                pyramidLimit!!.Translate(translation)
            }
        }

        override fun Rotate(rotation: idRotation) {
            if (null == body2) {
                anchor2.timesAssign(rotation)
            }
            if (coneLimit != null) {
                coneLimit!!.Rotate(rotation)
            } else if (pyramidLimit != null) {
                pyramidLimit!!.Rotate(rotation)
            }
        }

        override fun GetCenter(center: idVec3) {
            center.set(body1!!.GetWorldOrigin().plus(anchor1.times(body1!!.GetWorldAxis())))
        }

        override fun Save(saveFile: idSaveGame) {
            super.Save(saveFile)
            saveFile.WriteVec3(anchor1)
            saveFile.WriteVec3(anchor2)
            saveFile.WriteFloat(friction)
            if (coneLimit != null) {
                coneLimit!!.Save(saveFile)
            }
            if (pyramidLimit != null) {
                pyramidLimit!!.Save(saveFile)
            }
        }

        override fun Restore(saveFile: idRestoreGame) {
            val friction = CFloat(friction)
            super.Restore(saveFile)
            saveFile.ReadVec3(anchor1)
            saveFile.ReadVec3(anchor2)
            saveFile.ReadFloat(friction)
            this.friction = friction._val
            if (coneLimit != null) {
                coneLimit!!.Restore(saveFile)
            }
            if (pyramidLimit != null) {
                pyramidLimit!!.Restore(saveFile)
            }
        }

        override fun Evaluate(invTimeStep: Float) {
            val a1 = idVec3()
            val a2 = idVec3()
            val master: idAFBody?
            master = if (body2 != null) body2 else physics!!.GetMasterBody()
            a1.set(anchor1.times(body1!!.GetWorldAxis()))
            if (master != null) {
                a2.set(anchor2.times(master.GetWorldAxis()))
                c1.SubVec3_oSet(
                    0,
                    a2.plus(master.GetWorldOrigin()).minus(a1.plus(body1!!.GetWorldOrigin()))
                        .times(-(invTimeStep * ERROR_REDUCTION))
                )
            } else {
                c1.SubVec3_oSet(
                    0,
                    anchor2.minus(a1.plus(body1!!.GetWorldOrigin()))
                        .times(-(invTimeStep * ERROR_REDUCTION))
                )
            }
            c1.Clamp(-ERROR_REDUCTION_MAX, ERROR_REDUCTION_MAX)
            J1.set(idMat3.getMat3_identity(), idMat3.SkewSymmetric(a1).unaryMinus())
            if (body2 != null) {
                J2.set(idMat3.getMat3_identity().unaryMinus(), idMat3.SkewSymmetric(a2))
            } else {
                J2.Zero(3, 6)
            }
            if (coneLimit != null) {
                coneLimit!!.Add(physics, invTimeStep)
            } else if (pyramidLimit != null) {
                pyramidLimit!!.Add(physics, invTimeStep)
            }
        }

        override fun ApplyFriction(invTimeStep: Float) {
            val angular = idVec3()
            var invMass: Float
            val currentFriction: Float
            currentFriction = GetFriction()
            if (currentFriction <= 0.0f) {
                return
            }
            if (SysCvar.af_useImpulseFriction.GetBool() || SysCvar.af_useJointImpulseFriction.GetBool()) {
                angular.set(body1!!.GetAngularVelocity())
                invMass = body1!!.GetInverseMass()
                if (body2 != null) {
                    angular.minusAssign(body2!!.GetAngularVelocity())
                    invMass += body2!!.GetInverseMass()
                }
                angular.timesAssign(currentFriction / invMass)
                body1!!.SetAngularVelocity(body1!!.GetAngularVelocity().minus(angular.times(body1!!.GetInverseMass())))
                if (body2 != null) {
                    body2!!.SetAngularVelocity(
                        body2!!.GetAngularVelocity().plus(angular.times(body2!!.GetInverseMass()))
                    )
                }
            } else {
                if (null == fc) {
                    fc = idAFConstraint_BallAndSocketJointFriction()
                    fc!!.Setup(this)
                }
                fc!!.Add(physics, invTimeStep)
            }
        }

        //
        //
        init {
            assert(body1 != null)
            type = constraintType_t.CONSTRAINT_BALLANDSOCKETJOINT
            this.name.set(name)
            this.body1 = body1
            this.body2 = body2
            InitSize(3)
            coneLimit = null
            pyramidLimit = null
            friction = 0.0f
            fc = null
            fl.allowPrimary = true
            fl.noCollision = true
        }
    }

    //===============================================================
    //
    //	idAFConstraint_BallAndSocketJointFriction
    //
    //===============================================================
    // ball and socket joint friction
    class idAFConstraint_BallAndSocketJointFriction : idAFConstraint() {
        protected var joint: idAFConstraint_BallAndSocketJoint?
        fun Setup(bsj: idAFConstraint_BallAndSocketJoint) {
            joint = bsj
            body1 = bsj.GetBody1()
            body2 = bsj.GetBody2()
        }

        fun Add(phys: idPhysics_AF?, invTimeStep: Float): Boolean {
            val f: Float
            physics = phys
            f = joint!!.GetFriction() * joint!!.GetMultiplier().Length()
            if (f == 0.0f) {
                return false
            }
            lo.p[2] = -f
            lo.p[1] = lo.p[2]
            lo.p[0] = lo.p[1]
            hi.p[2] = f
            hi.p[1] = hi.p[2]
            hi.p[0] = hi.p[1]
            J1.Zero(3, 6)
            J1[0, 3] = J1.set(1, 4, J1.set(2, 5, 1.0f))
            if (body2 != null) {
                J2.Zero(3, 6)
                J2[0, 3] = J2.set(1, 4, J2.set(2, 5, 1.0f))
            }
            physics!!.AddFrameConstraint(this)
            return true
        }

        override fun Translate(translation: idVec3) {}
        override fun Rotate(rotation: idRotation) {}
        override fun Evaluate(invTimeStep: Float) {
            // do nothing
        }

        override fun ApplyFriction(invTimeStep: Float) {
            // do nothing
        }

        //
        //
        init {
            type = constraintType_t.CONSTRAINT_FRICTION
            name.set("ballAndSocketJointFriction")
            InitSize(3)
            joint = null
            fl.allowPrimary = false
            fl.frameConstraint = true
        }
    }

    //===============================================================
    //
    //	idAFConstraint_UniversalJoint
    //
    //===============================================================
    // universal, Cardan or Hooke joint which allows 2 degrees of freedom
    // like a ball and socket joint but also constrains the rotation about the cardan shafts
    class idAFConstraint_UniversalJoint(name: idStr, body1: idAFBody?, body2: idAFBody?) : idAFConstraint() {
        //
        //
        protected val anchor1 // anchor in body1 space
                : idVec3
        protected val anchor2 // anchor in body2 space
                : idVec3
        protected val axis1 // cardan axis in body1 space
                : idVec3
        protected val axis2 // cardan axis in body2 space
                : idVec3
        protected val shaft1 // body1 cardan shaft in body1 space
                : idVec3
        protected val shaft2 // body2 cardan shaft in body2 space
                : idVec3
        protected var coneLimit // cone shaped limit
                : idAFConstraint_ConeLimit?
        protected var fc // friction constraint
                : idAFConstraint_UniversalJointFriction?
        protected var friction // joint friction
                : Float
        protected var pyramidLimit // pyramid shaped limit
                : idAFConstraint_PyramidLimit?

        fun SetAnchor(worldPosition: idVec3) {

            // get anchor relative to center of mass of body1
            anchor1.set(worldPosition.minus(body1!!.GetWorldOrigin()).times(body1!!.GetWorldAxis().Transpose()))
            if (body2 != null) {
                // get anchor relative to center of mass of body2
                anchor2.set(worldPosition.minus(body2!!.GetWorldOrigin()).times(body2!!.GetWorldAxis().Transpose()))
            } else {
                anchor2.set(worldPosition)
            }
            if (coneLimit != null) {
                coneLimit!!.SetAnchor(anchor2)
            }
            if (pyramidLimit != null) {
                pyramidLimit!!.SetAnchor(anchor2)
            }
        }

        fun GetAnchor(): idVec3 {
            return if (body2 != null) {
                body2!!.GetWorldOrigin().plus(body2!!.GetWorldAxis().times(anchor2))
            } else anchor2
        }

        fun SetShafts(cardanShaft1: idVec3, cardanShaft2: idVec3) {
            val cardanAxis = idVec3()
            var l: Float
            shaft1.set(cardanShaft1)
            l = shaft1.Normalize()
            assert(l != 0.0f)
            shaft2.set(cardanShaft2)
            l = shaft2.Normalize()
            assert(l != 0.0f)

            // the cardan axis is a vector orthogonal to both cardan shafts
            cardanAxis.set(shaft1.Cross(shaft2))
            if (cardanAxis.Normalize() == 0.0f) {
                val vecY = idVec3()
                shaft1.OrthogonalBasis(cardanAxis, vecY)
                cardanAxis.Normalize()
            }
            shaft1.timesAssign(body1!!.GetWorldAxis().Transpose())
            axis1.set(cardanAxis.times(body1!!.GetWorldAxis().Transpose()))
            if (body2 != null) {
                shaft2.timesAssign(body2!!.GetWorldAxis().Transpose())
                axis2.set(cardanAxis.times(body2!!.GetWorldAxis().Transpose()))
            } else {
                axis2.set(cardanAxis)
            }
            if (coneLimit != null) {
                coneLimit!!.SetBody1Axis(shaft1)
            }
            if (pyramidLimit != null) {
                pyramidLimit!!.SetBody1Axis(shaft1)
            }
        }

        fun GetShafts(cardanShaft1: idVec3, cardanShaft2: idVec3) {
            cardanShaft1.set(shaft1)
            cardanShaft2.set(shaft2)
        }

        fun SetNoLimit() {
            if (coneLimit != null) {
                coneLimit = null
            }
            if (pyramidLimit != null) {
                pyramidLimit = null
            }
        }

        fun SetConeLimit(coneAxis: idVec3, coneAngle: Float) {
            if (pyramidLimit != null) {
                pyramidLimit = null
            }
            if (null == coneLimit) {
                coneLimit = idAFConstraint_ConeLimit()
                coneLimit!!.SetPhysics(physics)
            }
            if (body2 != null) {
                coneLimit!!.Setup(
                    body1,
                    body2,
                    anchor2,
                    coneAxis.times(body2!!.GetWorldAxis().Transpose()),
                    coneAngle,
                    shaft1
                )
            } else {
                coneLimit!!.Setup(body1, body2, anchor2, coneAxis, coneAngle, shaft1)
            }
        }

        fun SetPyramidLimit(pyramidAxis: idVec3, baseAxis: idVec3, angle1: Float, angle2: Float) {
            if (coneLimit != null) {
                coneLimit = null
            }
            if (null == pyramidLimit) {
                pyramidLimit = idAFConstraint_PyramidLimit()
                pyramidLimit!!.SetPhysics(physics)
            }
            if (body2 != null) {
                pyramidLimit!!.Setup(
                    body1,
                    body2,
                    anchor2,
                    pyramidAxis.times(body2!!.GetWorldAxis().Transpose()),
                    baseAxis.times(body2!!.GetWorldAxis().Transpose()),
                    angle1,
                    angle2,
                    shaft1
                )
            } else {
                pyramidLimit!!.Setup(body1, body2, anchor2, pyramidAxis, baseAxis, angle1, angle2, shaft1)
            }
        }

        fun SetLimitEpsilon(e: Float) {
            if (coneLimit != null) {
                coneLimit!!.SetEpsilon(e)
            }
            if (pyramidLimit != null) {
                pyramidLimit!!.SetEpsilon(e)
            }
        }

        fun SetFriction(f: Float) {
            friction = f
        }

        fun GetFriction(): Float {
            return if (SysCvar.af_forceFriction.GetFloat() > 0.0f) {
                SysCvar.af_forceFriction.GetFloat()
            } else friction * physics!!.GetJointFrictionScale()
        }

        override fun DebugDraw() {
            val a1 = idVec3()
            val a2 = idVec3()
            val s1 = idVec3()
            val s2 = idVec3()
            val d1 = idVec3()
            val d2 = idVec3()
            val v = idVec3()
            val master: idAFBody?
            master = if (body2 != null) body2 else physics!!.GetMasterBody()
            a1.set(body1!!.GetWorldOrigin().plus(anchor1.times(body1!!.GetWorldAxis())))
            s1.set(shaft1.times(body1!!.GetWorldAxis()))
            d1.set(axis1.times(body1!!.GetWorldAxis()))
            if (master != null) {
                a2.set(master.GetWorldOrigin().plus(anchor2.times(master.GetWorldAxis())))
                s2.set(shaft2.times(master.GetWorldAxis()))
                d2.set(axis2.times(master.GetWorldAxis()))
            } else {
                a2.set(anchor2)
                s2.set(shaft2)
                d2.set(axis2)
            }
            v.set(s1.Cross(s2))
            if (v.Normalize() != 0.0f) {
                val m1 = idMat3()
                val m2 = idMat3()
                m1.set(idMat3(s1, v, v.Cross(s1)))
                m2.set(idMat3(s2.unaryMinus(), v, v.Cross(s2.unaryMinus())))
                d2.timesAssign(m2.Transpose().times(m1))
            }
            Game_local.gameRenderWorld!!.DebugArrow(colorCyan, a1, a1.plus(s1.times(5.0f)), 1)
            Game_local.gameRenderWorld!!.DebugArrow(colorBlue, a2, a2.plus(s2.times(5.0f)), 1)
            Game_local.gameRenderWorld!!.DebugLine(colorGreen, a1, a1.plus(d1.times(5.0f)))
            Game_local.gameRenderWorld!!.DebugLine(colorGreen, a2, a2.plus(d2.times(5.0f)))
            if (SysCvar.af_showLimits.GetBool()) {
                if (coneLimit != null) {
                    coneLimit!!.DebugDraw()
                }
                if (pyramidLimit != null) {
                    pyramidLimit!!.DebugDraw()
                }
            }
        }

        override fun Translate(translation: idVec3) {
            if (null == body2) {
                anchor2.plusAssign(translation)
            }
            if (coneLimit != null) {
                coneLimit!!.Translate(translation)
            } else if (pyramidLimit != null) {
                pyramidLimit!!.Translate(translation)
            }
        }

        override fun Rotate(rotation: idRotation) {
            if (null == body2) {
                anchor2.timesAssign(rotation)
                shaft2.timesAssign(rotation.ToMat3())
                axis2.timesAssign(rotation.ToMat3())
            }
            if (coneLimit != null) {
                coneLimit!!.Rotate(rotation)
            } else if (pyramidLimit != null) {
                pyramidLimit!!.Rotate(rotation)
            }
        }

        override fun GetCenter(center: idVec3) {
            center.set(body1!!.GetWorldOrigin().plus(anchor1.times(body1!!.GetWorldAxis())))
        }

        override fun Save(saveFile: idSaveGame) {
            super.Save(saveFile)
            saveFile.WriteVec3(anchor1)
            saveFile.WriteVec3(anchor2)
            saveFile.WriteVec3(shaft1)
            saveFile.WriteVec3(shaft2)
            saveFile.WriteVec3(axis1)
            saveFile.WriteVec3(axis2)
            saveFile.WriteFloat(friction)
            if (coneLimit != null) {
                coneLimit!!.Save(saveFile)
            }
            if (pyramidLimit != null) {
                pyramidLimit!!.Save(saveFile)
            }
        }

        override fun Restore(saveFile: idRestoreGame) {
            val friction = CFloat(friction)
            super.Restore(saveFile)
            saveFile.ReadVec3(anchor1)
            saveFile.ReadVec3(anchor2)
            saveFile.ReadVec3(shaft1)
            saveFile.ReadVec3(shaft2)
            saveFile.ReadVec3(axis1)
            saveFile.ReadVec3(axis2)
            saveFile.ReadFloat(friction)
            this.friction = friction._val
            if (coneLimit != null) {
                coneLimit!!.Restore(saveFile)
            }
            if (pyramidLimit != null) {
                pyramidLimit!!.Restore(saveFile)
            }
        }

        /*
         ================
         idAFConstraint_UniversalJoint::Evaluate

         NOTE: this joint is homokinetic
         ================
         */
        override fun Evaluate(invTimeStep: Float) {
            val a1 = idVec3()
            val a2 = idVec3()
            val s1 = idVec3()
            val s2 = idVec3()
            val d1 = idVec3()
            val d2 = idVec3()
            val v = idVec3()
            val master: idAFBody?
            master = if (body2 != null) body2 else physics!!.GetMasterBody()
            a1.set(anchor1.times(body1!!.GetWorldAxis()))
            s1.set(shaft1.times(body1!!.GetWorldAxis()))
            d1.set(s1.Cross(axis1.times(body1!!.GetWorldAxis())))
            if (master != null) {
                a2.set(anchor2.times(master.GetWorldAxis()))
                s2.set(shaft2.times(master.GetWorldAxis()))
                d2.set(axis2.times(master.GetWorldAxis()))
                c1.SubVec3_oSet(
                    0,
                    a2.plus(master.GetWorldOrigin()).minus(a1.plus(body1!!.GetWorldOrigin()))
                        .times(-(invTimeStep * ERROR_REDUCTION))
                )
            } else {
                a2.set(anchor2)
                s2.set(shaft2)
                d2.set(axis2)
                c1.SubVec3_oSet(
                    0,
                    a2.minus(a1.plus(body1!!.GetWorldOrigin())).times(-(invTimeStep * ERROR_REDUCTION))
                )
            }
            J1.set(
                idMat3.getMat3_identity(),
                idMat3.SkewSymmetric(a1).unaryMinus(),
                idMat3.getMat3_zero(),
                idMat3(
                    s1[0], s1[1], s1[2],
                    0.0f, 0.0f, 0.0f,
                    0.0f, 0.0f, 0.0f
                )
            )
            J1.SetSize(4, 6)
            if (body2 != null) {
                J2.set(
                    idMat3.getMat3_identity().unaryMinus(),
                    idMat3.SkewSymmetric(a2),
                    idMat3.getMat3_zero(),
                    idMat3(
                        s2[0], s2[1], s2[2],
                        0.0f, 0.0f, 0.0f,
                        0.0f, 0.0f, 0.0f
                    )
                )
                J2.SetSize(4, 6)
            } else {
                J2.Zero(4, 6)
            }
            v.set(s1.Cross(s2))
            if (v.Normalize() != 0.0f) {
                val m1 = idMat3()
                val m2 = idMat3()
                m1.set(idMat3(s1, v, v.Cross(s1)))
                m2.set(idMat3(s2.unaryMinus(), v, v.Cross(s2.unaryMinus())))
                d2.timesAssign(m2.Transpose().times(m1))
            }
            c1.p[3] = -(invTimeStep * ERROR_REDUCTION) * d1.times(d2)
            c1.Clamp(-ERROR_REDUCTION_MAX, ERROR_REDUCTION_MAX)
            if (coneLimit != null) {
                coneLimit!!.Add(physics, invTimeStep)
            } else if (pyramidLimit != null) {
                pyramidLimit!!.Add(physics, invTimeStep)
            }
        }

        override fun ApplyFriction(invTimeStep: Float) {
            val angular = idVec3()
            var invMass: Float
            val currentFriction: Float
            currentFriction = GetFriction()
            if (currentFriction <= 0.0f) {
                return
            }
            if (SysCvar.af_useImpulseFriction.GetBool() || SysCvar.af_useJointImpulseFriction.GetBool()) {
                angular.set(body1!!.GetAngularVelocity())
                invMass = body1!!.GetInverseMass()
                if (body2 != null) {
                    angular.minusAssign(body2!!.GetAngularVelocity())
                    invMass += body2!!.GetInverseMass()
                }
                angular.timesAssign(currentFriction / invMass)
                body1!!.SetAngularVelocity(body1!!.GetAngularVelocity().minus(angular.times(body1!!.GetInverseMass())))
                if (body2 != null) {
                    body2!!.SetAngularVelocity(
                        body2!!.GetAngularVelocity().plus(angular.times(body2!!.GetInverseMass()))
                    )
                }
            } else {
                if (null == fc) {
                    fc = idAFConstraint_UniversalJointFriction()
                    fc!!.Setup(this)
                }
                fc!!.Add(physics, invTimeStep)
            }
        }

        // ~idAFConstraint_UniversalJoint();
        init {
            assert(body1 != null)
            anchor1 = idVec3()
            anchor2 = idVec3()
            shaft1 = idVec3()
            shaft2 = idVec3()
            axis1 = idVec3()
            axis2 = idVec3()
            type = constraintType_t.CONSTRAINT_UNIVERSALJOINT
            this.name.set(name)
            this.body1 = body1
            this.body2 = body2
            InitSize(4)
            coneLimit = null
            pyramidLimit = null
            friction = 0.0f
            fc = null
            fl.allowPrimary = true
            fl.noCollision = true
        }
    }

    //===============================================================
    //
    //	idAFConstraint_UniversalJointFriction
    //
    //===============================================================
    // universal joint friction
    class idAFConstraint_UniversalJointFriction : idAFConstraint() {
        protected var joint // universal joint
                : idAFConstraint_UniversalJoint?

        fun Setup(uj: idAFConstraint_UniversalJoint) {
            joint = uj
            body1 = uj.GetBody1()
            body2 = uj.GetBody2()
        }

        fun Add(phys: idPhysics_AF?, invTimeStep: Float): Boolean {
            val s1 = idVec3()
            val s2 = idVec3()
            val dir1 = idVec3()
            val dir2 = idVec3()
            val f: Float
            physics = phys
            f = joint!!.GetFriction() * joint!!.GetMultiplier().Length()
            if (f == 0.0f) {
                return false
            }
            lo.p[1] = -f
            lo.p[0] = lo.p[1]
            hi.p[1] = f
            hi.p[0] = hi.p[1]
            joint!!.GetShafts(s1, s2)
            s1.timesAssign(body1!!.GetWorldAxis())
            s1.NormalVectors(dir1, dir2)
            J1.SetSize(2, 6)
            J1.SubVec63_Zero(0, 0)
            J1.SubVec63_oSet(0, 1, dir1)
            J1.SubVec63_Zero(1, 0)
            J1.SubVec63_oSet(1, 1, dir2)
            if (body2 != null) {
                J2.SetSize(2, 6)
                J2.SubVec63_Zero(0, 0)
                J2.SubVec63_oSet(0, 1, dir1.unaryMinus())
                J2.SubVec63_Zero(1, 0)
                J2.SubVec63_oSet(1, 1, dir2.unaryMinus())
            }
            physics!!.AddFrameConstraint(this)
            return true
        }

        override fun Translate(translation: idVec3) {}
        override fun Rotate(rotation: idRotation) {}
        override fun Evaluate(invTimeStep: Float) {
            // do nothing
        }

        override fun ApplyFriction(invTimeStep: Float) {
            // do nothing
        }

        //
        //
        init {
            type = constraintType_t.CONSTRAINT_FRICTION
            name.set("universalJointFriction")
            InitSize(2)
            joint = null
            fl.allowPrimary = false
            fl.frameConstraint = true
        }
    }

    //===============================================================
    //
    //	idAFConstraint_CylindricalJoint
    //
    //===============================================================
    // cylindrical joint which allows 2 degrees of freedom
    // constrains body1 to lie on a line relative to body2 and allows only translation along and rotation about the line
    class idAFConstraint_CylindricalJoint(name: idStr, body1: idAFBody?, body2: idAFBody?) : idAFConstraint() {
        override fun DebugDraw() {
            assert(
                false // FIXME: implement
            )
        }

        override fun Translate(translation: idVec3) {
            assert(
                false // FIXME: implement
            )
        }

        override fun Rotate(rotation: idRotation) {
            assert(
                false // FIXME: implement
            )
        }

        override fun Evaluate(invTimeStep: Float) {
            assert(
                false // FIXME: implement
            )
        }

        override fun ApplyFriction(invTimeStep: Float) {
            assert(
                false // FIXME: implement
            )
        }

        init {
            assert(
                false // FIXME: implement
            )
        }
    }

    //===============================================================
    //
    //	idAFConstraint_Hinge
    //
    //===============================================================
    // hinge, revolute or pin joint which allows 1 degree of freedom
    // constrains all motion of body1 relative to body2 except the rotation about the hinge axis
    class idAFConstraint_Hinge(name: idStr, body1: idAFBody?, body2: idAFBody?) : idAFConstraint() {
        protected val anchor1 // anchor in body1 space
                : idVec3
        protected val anchor2 // anchor in body2 space
                : idVec3
        protected val axis1 // axis in body1 space
                : idVec3
        protected val axis2 // axis in body2 space
                : idVec3
        protected var coneLimit // cone limit
                : idAFConstraint_ConeLimit?
        protected var fc // friction constraint
                : idAFConstraint_HingeFriction?
        protected var friction // hinge friction
                : Float
        protected var initialAxis // initial axis of body1 relative to body2
                : idMat3
        protected var steering // steering
                : idAFConstraint_HingeSteering?

        // ~idAFConstraint_Hinge();
        fun SetAnchor(worldPosition: idVec3) {
            // get anchor relative to center of mass of body1
            anchor1.set(worldPosition.minus(body1!!.GetWorldOrigin()).times(body1!!.GetWorldAxis().Transpose()))
            if (body2 != null) {
                // get anchor relative to center of mass of body2
                anchor2.set(worldPosition.minus(body2!!.GetWorldOrigin()).times(body2!!.GetWorldAxis().Transpose()))
            } else {
                anchor2.set(worldPosition)
            }
            if (coneLimit != null) {
                coneLimit!!.SetAnchor(anchor2)
            }
        }

        fun GetAnchor(): idVec3 {
            return if (body2 != null) {
                body2!!.GetWorldOrigin().plus(body2!!.GetWorldAxis().times(anchor2))
            } else anchor2
        }

        fun SetAxis(axis: idVec3) {
            val normAxis = idVec3()
            normAxis.set(axis)
            normAxis.Normalize()

            // get axis relative to body1
            axis1.set(normAxis.times(body1!!.GetWorldAxis().Transpose()))
            if (body2 != null) {
                // get axis relative to body2
                axis2.set(normAxis.times(body2!!.GetWorldAxis().Transpose()))
            } else {
                axis2.set(normAxis)
            }
        }

        fun GetAxis(a1: idVec3, a2: idVec3) {
            a1.set(axis1)
            a2.set(axis2)
        }

        fun GetAxis(): idVec3 {
            return if (body2 != null) {
                axis2.times(body2!!.GetWorldAxis())
            } else axis2
        }

        fun SetNoLimit() {
            if (coneLimit != null) {
//		delete coneLimit;
                coneLimit = null
            }
        }

        fun SetLimit(axis: idVec3, angle: Float, body1Axis: idVec3) {
            if (null == coneLimit) {
                coneLimit = idAFConstraint_ConeLimit()
                coneLimit!!.SetPhysics(physics)
            }
            if (body2 != null) {
                coneLimit!!.Setup(
                    body1,
                    body2,
                    anchor2,
                    axis.times(body2!!.GetWorldAxis().Transpose()),
                    angle,
                    body1Axis.times(body1!!.GetWorldAxis().Transpose())
                )
            } else {
                coneLimit!!.Setup(
                    body1,
                    body2,
                    anchor2,
                    axis,
                    angle,
                    body1Axis.times(body1!!.GetWorldAxis().Transpose())
                )
            }
        }

        fun SetLimitEpsilon(e: Float) {
            if (coneLimit != null) {
                coneLimit!!.SetEpsilon(e)
            }
        }

        fun GetAngle(): Float {
            val axis: idMat3
            val rotation: idRotation
            val angle: Float
            axis = body1!!.GetWorldAxis().times(body2!!.GetWorldAxis().Transpose().times(initialAxis.Transpose()))
            rotation = axis.ToRotation()
            angle = rotation.GetAngle()
            return if (rotation.GetVec().times(axis1) < 0.0f) {
                -angle
            } else angle
        }

        fun SetSteerAngle(degrees: Float) {
            if (coneLimit != null) {
//		delete coneLimit;
                coneLimit = null
            }
            if (null == steering) {
                steering = idAFConstraint_HingeSteering()
                steering!!.Setup(this)
            }
            steering!!.SetSteerAngle(degrees)
        }

        fun SetSteerSpeed(speed: Float) {
            if (steering != null) {
                steering!!.SetSteerSpeed(speed)
            }
        }

        fun SetFriction(f: Float) {
            friction = f
        }

        fun GetFriction(): Float {
            return if (SysCvar.af_forceFriction.GetFloat() > 0.0f) {
                SysCvar.af_forceFriction.GetFloat()
            } else friction * physics!!.GetJointFrictionScale()
        }

        override fun DebugDraw() {
            val vecX = idVec3()
            val vecY = idVec3()
            val a1 = idVec3(body1!!.GetWorldOrigin().plus(anchor1.times(body1!!.GetWorldAxis())))
            val x1 = idVec3(axis1.times(body1!!.GetWorldAxis()))
            x1.OrthogonalBasis(vecX, vecY)
            Game_local.gameRenderWorld!!.DebugArrow(
                colorBlue,
                a1.minus(x1.times(4.0f)),
                a1.plus(x1.times(4.0f)),
                1
            )
            Game_local.gameRenderWorld!!.DebugLine(
                colorBlue,
                a1.minus(vecX.times(2.0f)),
                a1.plus(vecX.times(2.0f))
            )
            Game_local.gameRenderWorld!!.DebugLine(
                colorBlue,
                a1.minus(vecY.times(2.0f)),
                a1.plus(vecY.times(2.0f))
            )
            if (SysCvar.af_showLimits.GetBool()) {
                if (coneLimit != null) {
                    coneLimit!!.DebugDraw()
                }
            }
        }

        override fun Translate(translation: idVec3) {
            if (null == body2) {
                anchor2.plusAssign(translation)
            }
            if (coneLimit != null) {
                coneLimit!!.Translate(translation)
            }
        }

        override fun Rotate(rotation: idRotation) {
            if (null == body2) {
                anchor2.timesAssign(rotation)
                axis2.timesAssign(rotation.ToMat3())
            }
            if (coneLimit != null) {
                coneLimit!!.Rotate(rotation)
            }
        }

        override fun GetCenter(center: idVec3) {
            center.set(body1!!.GetWorldOrigin().plus(anchor1.times(body1!!.GetWorldAxis())))
        }

        override fun Save(saveFile: idSaveGame) {
            super.Save(saveFile)
            saveFile.WriteVec3(anchor1)
            saveFile.WriteVec3(anchor2)
            saveFile.WriteVec3(axis1)
            saveFile.WriteVec3(axis2)
            saveFile.WriteMat3(initialAxis)
            saveFile.WriteFloat(friction)
            if (coneLimit != null) {
                saveFile.WriteBool(true)
                coneLimit!!.Save(saveFile)
            } else {
                saveFile.WriteBool(false)
            }
            if (steering != null) {
                saveFile.WriteBool(true)
                steering!!.Save(saveFile)
            } else {
                saveFile.WriteBool(false)
            }
            if (fc != null) {
                saveFile.WriteBool(true)
                fc!!.Save(saveFile)
            } else {
                saveFile.WriteBool(false)
            }
        }

        override fun Restore(saveFile: idRestoreGame) {
            val b = CBool(false)
            val friction = CFloat(friction)
            super.Restore(saveFile)
            saveFile.ReadVec3(anchor1)
            saveFile.ReadVec3(anchor2)
            saveFile.ReadVec3(axis1)
            saveFile.ReadVec3(axis2)
            saveFile.ReadMat3(initialAxis)
            saveFile.ReadFloat(friction)
            saveFile.ReadBool(b)
            this.friction = friction._val
            if (b._val) {
                if (null == coneLimit) {
                    coneLimit = idAFConstraint_ConeLimit()
                }
                coneLimit!!.SetPhysics(physics)
                coneLimit!!.Restore(saveFile)
            }
            saveFile.ReadBool(b)
            if (b._val) {
                if (null == steering) {
                    steering = idAFConstraint_HingeSteering()
                }
                steering!!.Setup(this)
                steering!!.Restore(saveFile)
            }
            saveFile.ReadBool(b)
            if (b._val) {
                if (null == fc) {
                    fc = idAFConstraint_HingeFriction()
                }
                fc!!.Setup(this)
                fc!!.Restore(saveFile)
            }
        }

        override fun Evaluate(invTimeStep: Float) {
            val a1 = idVec3()
            val a2 = idVec3()
            val x1 = idVec3()
            val x2 = idVec3()
            val cross = idVec3()
            val vecX = idVec3()
            val vecY = idVec3()
            val master: idAFBody?
            master = if (body2 != null) body2 else physics!!.GetMasterBody()
            x1.set(axis1.times(body1!!.GetWorldAxis())) // axis in body1 space
            x1.OrthogonalBasis(vecX, vecY) // basis for axis in body1 space
            a1.set(anchor1.times(body1!!.GetWorldAxis())) // anchor in body1 space
            if (master != null) {
                a2.set(anchor2.times(master.GetWorldAxis())) // anchor in master space
                x2.set(axis2.times(master.GetWorldAxis()))
                c1.SubVec3_oSet(
                    0,
                    a2.plus(master.GetWorldOrigin()).minus(a1.plus(body1!!.GetWorldOrigin()))
                        .times(-(invTimeStep * ERROR_REDUCTION))
                )
            } else {
                a2.set(anchor2)
                x2.set(axis2)
                c1.SubVec3_oSet(
                    0,
                    a2.minus(a1.plus(body1!!.GetWorldOrigin())).times(-(invTimeStep * ERROR_REDUCTION))
                )
            }
            J1.set(
                idMat3.getMat3_identity(),
                idMat3.SkewSymmetric(a1).unaryMinus(),
                idMat3.getMat3_zero(),
                idMat3(
                    vecX[0], vecX[1], vecX[2],
                    vecY[0], vecY[1], vecY[2],
                    0.0f, 0.0f, 0.0f
                )
            )
            J1.SetSize(5, 6)
            if (body2 != null) {
                J2.set(
                    idMat3.getMat3_identity().unaryMinus(),
                    idMat3.SkewSymmetric(a2),
                    idMat3.getMat3_zero(),
                    idMat3(
                        -vecX[0], -vecX[1], -vecX[2],
                        -vecY[0], -vecY[1], -vecY[2],
                        0.0f, 0.0f, 0.0f
                    )
                )
                J2.SetSize(5, 6)
            } else {
                J2.Zero(5, 6)
            }
            cross.set(x1.Cross(x2))
            c1.p[3] = -(invTimeStep * ERROR_REDUCTION) * cross.times(vecX)
            c1.p[4] = -(invTimeStep * ERROR_REDUCTION) * cross.times(vecY)
            c1.Clamp(-ERROR_REDUCTION_MAX, ERROR_REDUCTION_MAX)
            if (steering != null) {
                steering!!.Add(physics, invTimeStep)
            } else if (coneLimit != null) {
                coneLimit!!.Add(physics, invTimeStep)
            }
        }

        override fun ApplyFriction(invTimeStep: Float) {
            val angular = idVec3()
            var invMass: Float
            val currentFriction: Float
            currentFriction = GetFriction()
            if (currentFriction <= 0.0f) {
                return
            }
            if (SysCvar.af_useImpulseFriction.GetBool() || SysCvar.af_useJointImpulseFriction.GetBool()) {
                angular.set(body1!!.GetAngularVelocity())
                invMass = body1!!.GetInverseMass()
                if (body2 != null) {
                    angular.minusAssign(body2!!.GetAngularVelocity())
                    invMass += body2!!.GetInverseMass()
                }
                angular.timesAssign(currentFriction / invMass)
                body1!!.SetAngularVelocity(body1!!.GetAngularVelocity().minus(angular.times(body1!!.GetInverseMass())))
                if (body2 != null) {
                    body2!!.SetAngularVelocity(
                        body2!!.GetAngularVelocity().plus(angular.times(body2!!.GetInverseMass()))
                    )
                }
            } else {
                if (null == fc) {
                    fc = idAFConstraint_HingeFriction()
                    fc!!.Setup(this)
                }
                fc!!.Add(physics, invTimeStep)
            }
        }

        //
        //
        init {
            anchor1 = idVec3()
            anchor2 = idVec3()
            axis1 = idVec3()
            axis2 = idVec3()
            assert(body1 != null)
            type = constraintType_t.CONSTRAINT_HINGE
            this.name.set(name)
            this.body1 = body1
            this.body2 = body2
            InitSize(5)
            coneLimit = null
            steering = null
            friction = 0.0f
            fc = null
            fl.allowPrimary = true
            fl.noCollision = true
            initialAxis = body1!!.GetWorldAxis()
            if (body2 != null) {
                initialAxis.timesAssign(body2.GetWorldAxis().Transpose())
            }
        }
    }

    //===============================================================
    //
    //	idAFConstraint_HingeFriction
    //
    //===============================================================
    // hinge joint friction
    class idAFConstraint_HingeFriction : idAFConstraint() {
        protected var hinge // hinge
                : idAFConstraint_Hinge?

        fun Setup(h: idAFConstraint_Hinge) {
            hinge = h
            body1 = h.GetBody1()
            body2 = h.GetBody2()
        }

        fun Add(phys: idPhysics_AF?, invTimeStep: Float): Boolean {
            val a1 = idVec3()
            val a2 = idVec3()
            val f: Float
            physics = phys
            f = hinge!!.GetFriction() * hinge!!.GetMultiplier().Length()
            if (f == 0.0f) {
                return false
            }
            lo.p[0] = -f
            hi.p[0] = f
            hinge!!.GetAxis(a1, a2)
            a1.timesAssign(body1!!.GetWorldAxis())
            J1.SetSize(1, 6)
            J1.SubVec63_Zero(0, 0)
            J1.SubVec63_oSet(0, 1, a1)
            if (body2 != null) {
                a2.timesAssign(body2!!.GetWorldAxis())
                J2.SetSize(1, 6)
                J2.SubVec63_Zero(0, 0)
                J2.SubVec63_oSet(0, 1, a2.unaryMinus())
            }
            physics!!.AddFrameConstraint(this)
            return true
        }

        override fun Translate(translation: idVec3) {}
        override fun Rotate(rotation: idRotation) {}
        override fun Evaluate(invTimeStep: Float) {
            // do nothing
        }

        override fun ApplyFriction(invTimeStep: Float) {
            // do nothing
        }

        //
        //
        init {
            type = constraintType_t.CONSTRAINT_FRICTION
            name.set("hingeFriction")
            InitSize(1)
            hinge = null
            fl.allowPrimary = false
            fl.frameConstraint = true
        }
    }

    //===============================================================
    //
    //	idAFConstraint_HingeSteering
    //
    //===============================================================
    // constrains two bodies attached to each other with a hinge to get a specified relative orientation
    class idAFConstraint_HingeSteering : idAFConstraint() {
        protected var epsilon // lcp epsilon
                : Float
        protected var hinge // hinge
                : idAFConstraint_Hinge?
        protected var steerAngle // desired steer angle in degrees
                = 0.0f
        protected var steerSpeed // steer speed
                : Float

        fun Setup(h: idAFConstraint_Hinge) {
            hinge = h
            body1 = h.GetBody1()
            body2 = h.GetBody2()
        }

        fun SetSteerAngle(degrees: Float) {
            steerAngle = degrees
        }

        fun SetSteerSpeed(speed: Float) {
            steerSpeed = speed
        }

        fun SetEpsilon(e: Float) {
            epsilon = e
        }

        fun Add(phys: idPhysics_AF?, invTimeStep: Float): Boolean {
            val angle: Float
            var speed: Float
            val a1 = idVec3()
            val a2 = idVec3()
            physics = phys
            hinge!!.GetAxis(a1, a2)
            angle = hinge!!.GetAngle()
            a1.timesAssign(body1!!.GetWorldAxis())
            J1.SetSize(1, 6)
            J1.SubVec63_Zero(0, 0)
            J1.SubVec63_oSet(0, 1, a1)
            if (body2 != null) {
                a2.timesAssign(body2!!.GetWorldAxis())
                J2.SetSize(1, 6)
                J2.SubVec63_Zero(0, 0)
                J2.SubVec63_oSet(0, 1, a2.unaryMinus())
            }
            speed = steerAngle - angle
            if (steerSpeed != 0.0f) {
                if (speed > steerSpeed) {
                    speed = steerSpeed
                } else if (speed < -steerSpeed) {
                    speed = -steerSpeed
                }
            }
            c1.p[0] = DEG2RAD(speed) * invTimeStep
            physics!!.AddFrameConstraint(this)
            return true
        }

        override fun Translate(translation: idVec3) {}
        override fun Rotate(rotation: idRotation) {}
        override fun Save(saveFile: idSaveGame) {
            saveFile.WriteFloat(steerAngle)
            saveFile.WriteFloat(steerSpeed)
            saveFile.WriteFloat(epsilon)
        }

        override fun Restore(saveFile: idRestoreGame) {
            val steerAngle =
                CFloat()
            val steerSpeed =
                CFloat()
            val epsilon =
                CFloat()
            saveFile.ReadFloat(steerAngle)
            saveFile.ReadFloat(steerSpeed)
            saveFile.ReadFloat(epsilon)
            this.steerAngle = steerAngle._val
            this.steerSpeed = steerSpeed._val
            this.epsilon = epsilon._val
        }

        override fun Evaluate(invTimeStep: Float) {
            // do nothing
        }

        override fun ApplyFriction(invTimeStep: Float) {
            // do nothing
        }

        //
        //
        init {
            type = constraintType_t.CONSTRAINT_HINGESTEERING
            name.set("hingeFriction")
            InitSize(1)
            hinge = null
            fl.allowPrimary = false
            fl.frameConstraint = true
            steerSpeed = 0.0f
            epsilon = LCP_EPSILON
        }
    }

    //===============================================================
    //
    //	idAFConstraint_Slider
    //
    //===============================================================
    // slider, prismatic or translational constraint which allows 1 degree of freedom
    // constrains body1 to lie on a line relative to body2, the orientation is also fixed relative to body2
    class idAFConstraint_Slider(name: idStr, body1: idAFBody?, body2: idAFBody?) : idAFConstraint() {
        protected val axis // axis along which body1 slides in body2 space
                : idVec3
        protected val offset // offset of body1 relative to body2
                : idVec3
        protected val relAxis // rotation of body1 relative to body2
                : idMat3 = idMat3()

        fun SetAxis(ax: idVec3) {
            val normAxis = idVec3()

            // get normalized axis relative to body1
            normAxis.set(ax)
            normAxis.Normalize()
            if (body2 != null) {
                axis.set(normAxis.times(body2!!.GetWorldAxis().Transpose()))
            } else {
                axis.set(normAxis)
            }
        }

        override fun DebugDraw() {
            val ofs = idVec3()
            val master: idAFBody?
            master = if (body2 != null) body2 else physics!!.GetMasterBody()
            if (master != null) {
                ofs.set(
                    master.GetWorldOrigin()
                        .plus(master.GetWorldAxis().times(offset).minus(body1!!.GetWorldOrigin()))
                )
            } else {
                ofs.set(offset.minus(body1!!.GetWorldOrigin()))
            }
            Game_local.gameRenderWorld!!.DebugLine(
                colorGreen,
                ofs,
                ofs.plus(axis.times(body1!!.GetWorldAxis()))
            )
        }

        override fun Translate(translation: idVec3) {
            if (null == body2) {
                offset.plusAssign(translation)
            }
        }

        override fun Rotate(rotation: idRotation) {
            if (null == body2) {
                offset.timesAssign(rotation)
            }
        }

        override fun GetCenter(center: idVec3) {
            val master: idAFBody?
            master = if (body2 != null) body2 else physics!!.GetMasterBody()
            if (master != null) {
                center.set(
                    master.GetWorldOrigin()
                        .plus(master.GetWorldAxis().times(offset).minus(body1!!.GetWorldOrigin()))
                )
            } else {
                center.set(offset.minus(body1!!.GetWorldOrigin()))
            }
        }

        override fun Save(saveFile: idSaveGame) {
            super.Save(saveFile)
            saveFile.WriteVec3(axis)
            saveFile.WriteVec3(offset)
            saveFile.WriteMat3(relAxis)
        }

        override fun Restore(saveFile: idRestoreGame) {
            super.Restore(saveFile)
            saveFile.ReadVec3(axis)
            saveFile.ReadVec3(offset)
            saveFile.ReadMat3(relAxis)
        }

        override fun Evaluate(invTimeStep: Float) {
            val vecX = idVec3()
            val vecY = idVec3()
            val ofs = idVec3()
            val r: idRotation
            val master: idAFBody?
            master = if (body2 != null) body2 else physics!!.GetMasterBody()
            r = if (master != null) {
                axis.times(master.GetWorldAxis()).OrthogonalBasis(vecX, vecY)
                ofs.set(
                    master.GetWorldOrigin()
                        .plus(master.GetWorldAxis().times(offset).minus(body1!!.GetWorldOrigin()))
                )
                body1!!.GetWorldAxis().Transpose().times(relAxis.times(master.GetWorldAxis())).ToRotation()
            } else {
                axis.OrthogonalBasis(vecX, vecY)
                ofs.set(offset.minus(body1!!.GetWorldOrigin()))
                body1!!.GetWorldAxis().Transpose().times(relAxis).ToRotation()
            }
            J1.set(
                idMat3.getMat3_zero(), idMat3.getMat3_identity(),
                idMat3(vecX, vecY, getVec3Origin()), idMat3.getMat3_zero()
            )
            J1.SetSize(5, 6)
            if (body2 != null) {
                J2.set(
                    idMat3.getMat3_zero(), idMat3.getMat3_identity().unaryMinus(),
                    idMat3(vecX.unaryMinus(), vecY.unaryMinus(), getVec3Origin()), idMat3.getMat3_zero()
                )
                J2.SetSize(5, 6)
            } else {
                J2.Zero(5, 6)
            }
            c1.SubVec3_oSet(
                0,
                r.GetVec().times(-DEG2RAD(r.GetAngle()))
                    .times(-(invTimeStep * ERROR_REDUCTION))
            )
            c1.p[3] = -(invTimeStep * ERROR_REDUCTION) * vecX.times(ofs)
            c1.p[4] = -(invTimeStep * ERROR_REDUCTION) * vecY.times(ofs)
            c1.Clamp(-ERROR_REDUCTION_MAX, ERROR_REDUCTION_MAX)
        }

        override fun ApplyFriction(invTimeStep: Float) {
            // no friction
        }

        //
        //
        init {
            axis = idVec3()
            offset = idVec3()
            assert(body1 != null)
            type = constraintType_t.CONSTRAINT_SLIDER
            this.name.set(name)
            this.body1 = body1
            this.body2 = body2
            InitSize(5)
            fl.allowPrimary = true
            fl.noCollision = true
            relAxis.set(
                if (body2 != null) {
                    offset.set(
                        body1!!.GetWorldOrigin().minus(body2.GetWorldOrigin()).times(body1.GetWorldAxis().Transpose())
                    )
                    body1.GetWorldAxis().times(body2.GetWorldAxis().Transpose())
                } else {
                    offset.set(body1!!.GetWorldOrigin())
                    body1.GetWorldAxis()
                }
            )
        }
    }

    //===============================================================
    //
    //	idAFConstraint_Line
    //
    //===============================================================
    // line constraint which allows 4 degrees of freedom
    // constrains body1 to lie on a line relative to body2, does not constrain the orientation.
    class idAFConstraint_Line(name: idStr, body1: idAFBody?, body2: idAFBody?) : idAFConstraint() {
        override fun DebugDraw() {
            assert(
                false // FIXME: implement
            )
        }

        override fun Translate(translation: idVec3) {
            assert(
                false // FIXME: implement
            )
        }

        override fun Rotate(rotation: idRotation) {
            assert(
                false // FIXME: implement
            )
        }

        override fun Evaluate(invTimeStep: Float) {
            assert(
                false // FIXME: implement
            )
        }

        override fun ApplyFriction(invTimeStep: Float) {
            assert(
                false // FIXME: implement
            )
        }

        init {
            assert(
                false // FIXME: implement
            )
        }
    }

    //===============================================================
    //
    //	idAFConstraint_Plane
    //
    //===============================================================
    // plane constraint which allows 5 degrees of freedom
    // constrains body1 to lie in a plane relative to body2, does not constrain the orientation.
    class idAFConstraint_Plane(name: idStr, body1: idAFBody?, body2: idAFBody?) : idAFConstraint() {
        protected val anchor1 // anchor in body1 space
                : idVec3
        protected val anchor2 // anchor in body2 space
                : idVec3
        protected val planeNormal // plane normal in body2 space
                : idVec3

        fun SetPlane(normal: idVec3, anchor: idVec3) {
            // get anchor relative to center of mass of body1
            anchor1.set(anchor.minus(body1!!.GetWorldOrigin()).times(body1!!.GetWorldAxis().Transpose()))
            if (body2 != null) {
                // get anchor relative to center of mass of body2
                anchor2.set(anchor.minus(body2!!.GetWorldOrigin()).times(body2!!.GetWorldAxis().Transpose()))
                planeNormal.set(normal.times(body2!!.GetWorldAxis().Transpose()))
            } else {
                anchor2.set(anchor)
                planeNormal.set(normal)
            }
        }

        override fun DebugDraw() {
            val a1 = idVec3()
            val normal = idVec3()
            val right = idVec3()
            val up = idVec3()
            val master: idAFBody?
            master = if (body2 != null) body2 else physics!!.GetMasterBody()
            a1.set(body1!!.GetWorldOrigin().plus(anchor1.times(body1!!.GetWorldAxis())))
            if (master != null) {
                normal.set(planeNormal.times(master.GetWorldAxis()))
            } else {
                normal.set(planeNormal)
            }
            normal.NormalVectors(right, up)
            normal.timesAssign(4.0f)
            right.timesAssign(4.0f)
            up.timesAssign(4.0f)
            Game_local.gameRenderWorld!!.DebugLine(colorCyan, a1.minus(right), a1.plus(right))
            Game_local.gameRenderWorld!!.DebugLine(colorCyan, a1.minus(up), a1.plus(up))
            Game_local.gameRenderWorld!!.DebugArrow(colorCyan, a1, a1.plus(normal), 1)
        }

        override fun Translate(translation: idVec3) {
            if (null == body2) {
                anchor2.plusAssign(translation)
            }
        }

        override fun Rotate(rotation: idRotation) {
            if (null == body2) {
                anchor2.timesAssign(rotation)
                planeNormal.timesAssign(rotation.ToMat3())
            }
        }

        override fun Save(saveFile: idSaveGame) {
            super.Save(saveFile)
            saveFile.WriteVec3(anchor1)
            saveFile.WriteVec3(anchor2)
            saveFile.WriteVec3(planeNormal)
        }

        override fun Restore(saveFile: idRestoreGame) {
            super.Restore(saveFile)
            saveFile.ReadVec3(anchor1)
            saveFile.ReadVec3(anchor2)
            saveFile.ReadVec3(planeNormal)
        }

        override fun Evaluate(invTimeStep: Float) {
            val a1 = idVec3()
            val a2 = idVec3()
            val normal = idVec3()
            val p = idVec3()
            val v = idVec6()
            val master: idAFBody?
            master = if (body2 != null) body2 else physics!!.GetMasterBody()
            a1.set(body1!!.GetWorldOrigin().plus(anchor1.times(body1!!.GetWorldAxis())))
            if (master != null) {
                a2.set(master.GetWorldOrigin().plus(anchor2.times(master.GetWorldAxis())))
                normal.set(planeNormal.times(master.GetWorldAxis()))
            } else {
                a2.set(anchor2)
                normal.set(planeNormal)
            }
            p.set(a1.minus(body1!!.GetWorldOrigin()))
            v.SubVec3_oSet(0, normal)
            v.SubVec3_oSet(1, p.Cross(normal))
            J1[1, 6] = v.ToFloatPtr()
            if (body2 != null) {
                p.set(a1.minus(body2!!.GetWorldOrigin()))
                v.SubVec3_oSet(0, normal.unaryMinus())
                v.SubVec3_oSet(1, p.Cross(normal.unaryMinus()))
                J2[1, 6] = v.ToFloatPtr()
            }
            c1.p[0] = -(invTimeStep * ERROR_REDUCTION) * (a1.times(normal) - a2.times(normal))
            c1.Clamp(-ERROR_REDUCTION_MAX, ERROR_REDUCTION_MAX)
        }

        override fun ApplyFriction(invTimeStep: Float) {
            // no friction
        }

        //
        //
        init {
            assert(body1 != null)
            anchor1 = idVec3()
            anchor2 = idVec3()
            planeNormal = idVec3()
            type = constraintType_t.CONSTRAINT_PLANE
            this.name.set(name)
            this.body1 = body1
            this.body2 = body2
            InitSize(1)
            fl.allowPrimary = true
            fl.noCollision = true
        }
    }

    //===============================================================
    //
    //	idAFConstraint_Spring
    //
    //===============================================================
    // spring constraint which allows 6 or 5 degrees of freedom based on the spring limits
    // constrains body1 relative to body2 with a spring
    class idAFConstraint_Spring(name: idStr, body1: idAFBody?, body2: idAFBody?) : idAFConstraint() {
        protected val anchor1 // anchor in body1 space
                : idVec3
        protected val anchor2 // anchor in body2 space
                : idVec3
        protected var damping // spring damping
                : Float
        protected var kcompress // spring constant when compressed
                : Float
        protected var kstretch // spring constant when stretched
                : Float
        protected var maxLength // maximum spring length
                : Float
        protected var minLength // minimum spring length
                : Float
        protected var restLength // rest length of spring
                : Float

        fun SetAnchor(worldAnchor1: idVec3, worldAnchor2: idVec3) {
            // get anchor relative to center of mass of body1
            anchor1.set(worldAnchor1.minus(body1!!.GetWorldOrigin()).times(body1!!.GetWorldAxis().Transpose()))
            if (body2 != null) {
                // get anchor relative to center of mass of body2
                anchor2.set(worldAnchor2.minus(body2!!.GetWorldOrigin()).times(body2!!.GetWorldAxis().Transpose()))
            } else {
                anchor2.set(worldAnchor2)
            }
        }

        fun SetSpring(stretch: Float, compress: Float, damping: Float, restLength: Float) {
            assert(stretch >= 0.0f && compress >= 0.0f && restLength >= 0.0f)
            kstretch = stretch
            kcompress = compress
            this.damping = damping
            this.restLength = restLength
        }

        fun SetLimit(minLength: Float, maxLength: Float) {
            assert(minLength >= 0.0f && maxLength >= 0.0f && maxLength >= minLength)
            this.minLength = minLength
            this.maxLength = maxLength
        }

        override fun DebugDraw() {
            val master: idAFBody?
            val length: Float
            val a1 = idVec3()
            val a2 = idVec3()
            val dir = idVec3()
            val mid = idVec3()
            val p = idVec3()
            master = if (body2 != null) body2 else physics!!.GetMasterBody()
            a1.set(body1!!.GetWorldOrigin().plus(anchor1.times(body1!!.GetWorldAxis())))
            if (master != null) {
                a2.set(master.GetWorldOrigin().plus(anchor2.times(master.GetWorldAxis())))
            } else {
                a2.set(anchor2)
            }
            dir.set(a2.minus(a1))
            mid.set(a1.plus(dir.times(0.5f)))
            length = dir.Normalize()

            // draw spring
            Game_local.gameRenderWorld!!.DebugLine(colorGreen, a1, a2)

            // draw rest length
            p.set(dir.times(restLength * 0.5f))
            Game_local.gameRenderWorld!!.DebugCircle(colorWhite, mid.plus(p), dir, 1.0f, 10)
            Game_local.gameRenderWorld!!.DebugCircle(colorWhite, mid.minus(p), dir, 1.0f, 10)
            if (restLength > length) {
                Game_local.gameRenderWorld!!.DebugLine(colorWhite, a2, mid.plus(p))
                Game_local.gameRenderWorld!!.DebugLine(colorWhite, a1, mid.minus(p))
            }
            if (minLength > 0.0f) {
                // draw min length
                Game_local.gameRenderWorld!!.DebugCircle(
                    colorBlue,
                    mid.plus(dir.times(minLength * 0.5f)),
                    dir,
                    2.0f,
                    10
                )
                Game_local.gameRenderWorld!!.DebugCircle(
                    colorBlue,
                    mid.minus(dir.times(minLength * 0.5f)),
                    dir,
                    2.0f,
                    10
                )
            }
            if (maxLength > 0.0f) {
                // draw max length
                Game_local.gameRenderWorld!!.DebugCircle(
                    colorRed,
                    mid.plus(dir.times(maxLength * 0.5f)),
                    dir,
                    2.0f,
                    10
                )
                Game_local.gameRenderWorld!!.DebugCircle(
                    colorRed,
                    mid.minus(dir.times(maxLength * 0.5f)),
                    dir,
                    2.0f,
                    10
                )
            }
        }

        override fun Translate(translation: idVec3) {
            if (null == body2) {
                anchor2.plusAssign(translation)
            }
        }

        override fun Rotate(rotation: idRotation) {
            if (null == body2) {
                anchor2.timesAssign(rotation)
            }
        }

        override fun GetCenter(center: idVec3) {
            val master: idAFBody?
            val a1 = idVec3()
            val a2 = idVec3()
            master = if (body2 != null) body2 else physics!!.GetMasterBody()
            a1.set(body1!!.GetWorldOrigin().plus(anchor1.times(body1!!.GetWorldAxis())))
            if (master != null) {
                a2.set(master.GetWorldOrigin().plus(anchor2.times(master.GetWorldAxis())))
            } else {
                a2.set(anchor2)
            }
            center.set(a1.plus(a2).times(0.5f))
        }

        override fun Save(saveFile: idSaveGame) {
            super.Save(saveFile)
            saveFile.WriteVec3(anchor1)
            saveFile.WriteVec3(anchor2)
            saveFile.WriteFloat(kstretch)
            saveFile.WriteFloat(kcompress)
            saveFile.WriteFloat(damping)
            saveFile.WriteFloat(restLength)
            saveFile.WriteFloat(minLength)
            saveFile.WriteFloat(maxLength)
        }

        override fun Restore(saveFile: idRestoreGame) {
            val kstretch = CFloat()
            val kcompress = CFloat()
            val damping = CFloat()
            val restLength = CFloat()
            val minLength = CFloat()
            val maxLength = CFloat()
            super.Restore(saveFile)
            saveFile.ReadVec3(anchor1)
            saveFile.ReadVec3(anchor2)
            saveFile.ReadFloat(kstretch)
            saveFile.ReadFloat(kcompress)
            saveFile.ReadFloat(damping)
            saveFile.ReadFloat(restLength)
            saveFile.ReadFloat(minLength)
            saveFile.ReadFloat(maxLength)
            this.kstretch = kstretch._val
            this.kcompress = kcompress._val
            this.damping = damping._val
            this.restLength = restLength._val
            this.minLength = minLength._val
            this.maxLength = maxLength._val
        }

        override fun Evaluate(invTimeStep: Float) {
            val a1 = idVec3()
            val a2 = idVec3()
            val velocity1 = idVec3()
            val velocity2 = idVec3()
            val force = idVec3()
            val v1 = idVec6()
            val v2 = idVec6()
            val d: Float
            val dampingForce: Float
            val length: Float
            val error: Float
            val limit: Boolean
            val master: idAFBody?
            master = if (body2 != null) body2 else physics!!.GetMasterBody()
            a1.set(body1!!.GetWorldOrigin().plus(anchor1.times(body1!!.GetWorldAxis())))
            velocity1.set(body1!!.GetPointVelocity(a1))
            if (master != null) {
                a2.set(master.GetWorldOrigin().plus(anchor2.times(master.GetWorldAxis())))
                velocity2.set(master.GetPointVelocity(a2))
            } else {
                a2.set(anchor2)
                velocity2.Zero()
            }
            force.set(a2.minus(a1))
            d = force.times(force)
            dampingForce = if (d != 0.0f) {
                damping * abs(velocity2.minus(velocity1).times(force)) / d
            } else {
                0.0f
            }
            length = force.Normalize()
            if (length > restLength) {
                if (kstretch > 0.0f) {
                    val springForce =
                        idVec3(force.times(Square(length - restLength) * kstretch - dampingForce))
                    body1!!.AddForce(a1, springForce)
                    master?.AddForce(a2, springForce.unaryMinus())
                }
            } else {
                if (kcompress > 0.0f) {
                    val springForce =
                        idVec3(force.times(-(Square(restLength - length) * kcompress - dampingForce)))
                    body1!!.AddForce(a1, springForce)
                    master?.AddForce(a2, springForce.unaryMinus())
                }
            }

            // check for spring limits
            if (length < minLength) {
                force.set(force.unaryMinus())
                error = minLength - length
                limit = true
            } else if (maxLength > 0.0f && length > maxLength) {
                error = length - maxLength
                limit = true
            } else {
                error = 0.0f
                limit = false
            }
            if (limit) {
                a1.minusAssign(body1!!.GetWorldOrigin())
                v1.SubVec3_oSet(0, force)
                v1.SubVec3_oSet(1, a1.Cross(force))
                J1[1, 6] = v1.ToFloatPtr()
                if (body2 != null) {
                    a2.minusAssign(body2!!.GetWorldOrigin())
                    v2.SubVec3_oSet(0, force.unaryMinus())
                    v2.SubVec3_oSet(1, a2.Cross(force.unaryMinus()))
                    J2[1, 6] = v2.ToFloatPtr()
                }
                c1.p[0] = -(invTimeStep * ERROR_REDUCTION) * error
                lo.p[0] = 0.0f
            } else {
                J1.Zero(0, 0)
                J2.Zero(0, 0)
            }
            c1.Clamp(-ERROR_REDUCTION_MAX, ERROR_REDUCTION_MAX)
        }

        override fun ApplyFriction(invTimeStep: Float) {
            // no friction
        }

        //
        //
        init {
            assert(body1 != null)
            anchor1 = idVec3()
            anchor2 = idVec3()
            type = constraintType_t.CONSTRAINT_SPRING
            this.name.set(name)
            this.body1 = body1
            this.body2 = body2
            InitSize(1)
            fl.allowPrimary = false
            damping = 1.0f
            kcompress = damping
            kstretch = kcompress
            restLength = 0.0f
            maxLength = restLength
            minLength = maxLength
        }
    }

    //===============================================================
    //
    //	idAFConstraint_Contact
    //
    //===============================================================
    // constrains body1 to either be in contact with or move away from body2
    class idAFConstraint_Contact : idAFConstraint() {
        //
        //
        protected var contact // contact information
                : contactInfo_t = contactInfo_t()

        // ~idAFConstraint_Contact();
        protected var fc // contact friction
                : idAFConstraint_ContactFriction?

        fun Setup(b1: idAFBody?, b2: idAFBody?, c: contactInfo_t) {
            val p = idVec3()
            val v = idVec6()
            var vel: Float
            val minBounceVelocity = 2.0f
            assert(b1 != null)
            body1 = b1
            body2 = b2
            contact = c
            p.set(c.point.minus(body1!!.GetWorldOrigin()))
            v.SubVec3_oSet(0, c.normal)
            v.SubVec3_oSet(1, p.Cross(c.normal))
            J1[1, 6] = v.ToFloatPtr()
            vel = v.SubVec3(0).times(body1!!.GetLinearVelocity()) + v.SubVec3(1).times(body1!!.GetAngularVelocity())
            if (body2 != null) {
                p.set(c.point.minus(body2!!.GetWorldOrigin()))
                v.SubVec3_oSet(0, c.normal.unaryMinus())
                v.SubVec3_oSet(1, p.Cross(c.normal.unaryMinus()))
                J2[1, 6] = v.ToFloatPtr()
                vel += v.SubVec3(0).times(body2!!.GetLinearVelocity()) + v.SubVec3(1)
                    .times(body2!!.GetAngularVelocity())
                c2.p[0] = 0.0f
            }
            if (body1!!.GetBouncyness() > 0.0f && -vel > minBounceVelocity) {
                c1.p[0] = body1!!.GetBouncyness() * vel
            } else {
                c1.p[0] = 0.0f
            }
            e.p[0] = CONTACT_LCP_EPSILON
            lo.p[0] = 0.0f
            hi.p[0] = idMath.INFINITY
            boxConstraint = null
            boxIndex[0] = -1
        }

        fun GetContact(): contactInfo_t {
            return contact
        }

        override fun DebugDraw() {
            val x = idVec3()
            val y = idVec3()
            contact.normal.NormalVectors(x, y)
            Game_local.gameRenderWorld!!.DebugLine(
                colorWhite,
                contact.point,
                contact.point.plus(contact.normal.times(6.0f))
            )
            Game_local.gameRenderWorld!!.DebugLine(
                colorWhite,
                contact.point.minus(x.times(2.0f)),
                contact.point.plus(x.times(2.0f))
            )
            Game_local.gameRenderWorld!!.DebugLine(
                colorWhite,
                contact.point.minus(y.times(2.0f)),
                contact.point.plus(y.times(2.0f))
            )
        }

        override fun Translate(translation: idVec3) {
            assert(
                false // contact should never be translated
            )
        }

        override fun Rotate(rotation: idRotation) {
            assert(
                false // contact should never be rotated
            )
        }

        override fun GetCenter(center: idVec3) {
            center.set(contact.point)
        }

        //
        //
        override fun Evaluate(invTimeStep: Float) {
            // do nothing
        }

        override fun ApplyFriction(invTimeStep: Float) {
            val r = idVec3()
            val velocity = idVec3()
            val normal = idVec3()
            var friction: Float
            val magnitude: Float
            val forceNumerator: Float
            val forceDenominator: Float
            val impulse = idVecX()
            val dv = idVecX()
            friction = body1!!.GetContactFriction()
            if (body2 != null && body2!!.GetContactFriction() < friction) {
                friction = body2!!.GetContactFriction()
            }
            friction *= physics!!.GetContactFrictionScale()
            if (friction <= 0.0f) {
                return
            }

            // seperate friction per contact is silly but it's fast and often looks close enough
            if (SysCvar.af_useImpulseFriction.GetBool()) {
                impulse.SetData(6, idVecX.VECX_ALLOCA(6))
                dv.SetData(6, idVecX.VECX_ALLOCA(6))

                // calculate velocity in the contact plane
                r.set(contact.point.minus(body1!!.GetWorldOrigin()))
                velocity.set(body1!!.GetLinearVelocity().plus(body1!!.GetAngularVelocity().Cross(r)))
                velocity.minusAssign(contact.normal.times(velocity.times(contact.normal)))

                // get normalized direction of friction and magnitude of velocity
                normal.set(velocity.unaryMinus())
                magnitude = normal.Normalize()
                forceNumerator = friction * magnitude
                forceDenominator =
                    body1!!.GetInverseMass() + body1!!.GetInverseWorldInertia().times(r.Cross(normal)).Cross(r)
                        .times(normal)
                impulse.SubVec3_oSet(0, normal.times(forceNumerator / forceDenominator))
                impulse.SubVec3_oSet(1, r.Cross(impulse.SubVec3(0)))
                body1!!.InverseWorldSpatialInertiaMultiply(dv, impulse.ToFloatPtr())

                // modify velocity with friction force
                body1!!.SetLinearVelocity(body1!!.GetLinearVelocity().plus(dv.SubVec3(0)))
                body1!!.SetAngularVelocity(body1!!.GetAngularVelocity().plus(dv.SubVec3(1)))
            } else {
                if (null == fc) {
                    fc = idAFConstraint_ContactFriction()
                }
                // call setup each frame because contact constraints are re-used for different bodies
                fc!!.Setup(this)
                fc!!.Add(physics, invTimeStep)
            }
        }

        init {
            name.set("contact")
            type = constraintType_t.CONSTRAINT_CONTACT
            InitSize(1)
            fc = null
            fl.allowPrimary = false
            fl.frameConstraint = true
        }
    }

    //===============================================================
    //
    //	idAFConstraint_ContactFriction
    //
    //===============================================================
    // contact friction
    class idAFConstraint_ContactFriction : idAFConstraint() {
        //
        //
        var cc // contact constraint
                : idAFConstraint_Contact?

        fun Setup(cc: idAFConstraint_Contact) {
            this.cc = cc
            body1 = cc.GetBody1()
            body2 = cc.GetBody2()
        }

        fun Add(phys: idPhysics_AF?, invTimeStep: Float): Boolean {
            val r = idVec3()
            val dir1 = idVec3()
            val dir2 = idVec3()
            var friction: Float
            val newRow: Int
            physics = phys
            friction = body1!!.GetContactFriction() * physics!!.GetContactFrictionScale()

            // if the body only has friction in one direction
            if (body1!!.GetFrictionDirection(dir1)) {
                // project the friction direction into the contact plane
                dir1.minusAssign(dir1.times(cc!!.GetContact().normal.times(dir1)))
                dir1.Normalize()
                r.set(cc!!.GetContact().point.minus(body1!!.GetWorldOrigin()))
                J1.SetSize(1, 6)
                J1.SubVec63_oSet(0, 0, dir1)
                J1.SubVec63_oSet(0, 1, r.Cross(dir1))
                c1.SetSize(1)
                c1.p[0] = 0.0f
                if (body2 != null) {
                    r.set(cc!!.GetContact().point.minus(body2!!.GetWorldOrigin()))
                    J2.SetSize(1, 6)
                    J2.SubVec63_oSet(0, 0, dir1.unaryMinus())
                    J2.SubVec63_oSet(0, 1, r.Cross(dir1.unaryMinus()))
                    c2.SetSize(1)
                    c2.p[0] = 0.0f
                }
                lo.p[0] = -friction
                hi.p[0] = friction
                boxConstraint = cc
                boxIndex[0] = 0
            } else {
                // get two friction directions orthogonal to contact normal
                cc!!.GetContact().normal.NormalVectors(dir1, dir2)
                r.set(cc!!.GetContact().point.minus(body1!!.GetWorldOrigin()))
                J1.SetSize(2, 6)
                J1.SubVec63_oSet(0, 0, dir1)
                J1.SubVec63_oSet(0, 1, r.Cross(dir1))
                J1.SubVec63_oSet(1, 0, dir2)
                J1.SubVec63_oSet(1, 1, r.Cross(dir2))
                c1.SetSize(2)
                c1.p[1] = 0.0f
                c1.p[0] = c1.p[1]
                if (body2 != null) {
                    r.set(cc!!.GetContact().point.minus(body2!!.GetWorldOrigin()))
                    J2.SetSize(2, 6)
                    J2.SubVec63_oSet(0, 0, dir1.unaryMinus())
                    J2.SubVec63_oSet(0, 1, r.Cross(dir1.unaryMinus()))
                    J2.SubVec63_oSet(1, 0, dir2.unaryMinus())
                    J2.SubVec63_oSet(1, 1, r.Cross(dir2.unaryMinus()))
                    c2.SetSize(2)
                    c2.p[1] = 0.0f
                    c2.p[0] = c2.p[1]
                    if (body2!!.GetContactFriction() < friction) {
                        friction = body2!!.GetContactFriction()
                    }
                }
                lo.p[0] = -friction
                hi.p[0] = friction
                boxConstraint = cc
                boxIndex[0] = 0
                lo.p[1] = -friction
                hi.p[1] = friction
                boxIndex[1] = 0
            }
            if (body1!!.GetContactMotorDirection(dir1) && body1!!.GetContactMotorForce() > 0.0f) {
                // project the motor force direction into the contact plane
                dir1.minusAssign(dir1.times(cc!!.GetContact().normal.times(dir1)))
                dir1.Normalize()
                r.set(cc!!.GetContact().point.minus(body1!!.GetWorldOrigin()))
                newRow = J1.GetNumRows()
                J1.ChangeSize(newRow + 1, J1.GetNumColumns())
                J1.SubVec63_oSet(newRow, 0, dir1.unaryMinus())
                J1.SubVec63_oSet(newRow, 1, r.Cross(dir1.unaryMinus()))
                c1.ChangeSize(newRow + 1)
                c1.p[newRow] = body1!!.GetContactMotorVelocity()
                if (body2 != null) {
                    r.set(cc!!.GetContact().point.minus(body2!!.GetWorldOrigin()))
                    J2.ChangeSize(newRow + 1, J2.GetNumColumns())
                    J2.SubVec63_oSet(newRow, 0, dir1.unaryMinus())
                    J2.SubVec63_oSet(newRow, 1, r.Cross(dir1.unaryMinus()))
                    c2.ChangeSize(newRow + 1)
                    c2.p[newRow] = 0.0f
                }
                lo.p[newRow] = -body1!!.GetContactMotorForce()
                hi.p[newRow] = body1!!.GetContactMotorForce()
                boxIndex[newRow] = -1
            }
            physics!!.AddFrameConstraint(this)
            return true
        }

        override fun DebugDraw() {}
        override fun Translate(translation: idVec3) {}
        override fun Rotate(rotation: idRotation) {}

        //
        //
        override fun Evaluate(invTimeStep: Float) {
            // do nothing
        }

        override fun ApplyFriction(invTimeStep: Float) {
            // do nothing
        }

        init {
            type = constraintType_t.CONSTRAINT_FRICTION
            name.set("contactFriction")
            InitSize(2)
            cc = null
            fl.allowPrimary = false
            fl.frameConstraint = true
        }
    }

    //===============================================================
    //
    //	idAFConstraint_ConeLimit
    //
    //===============================================================
    // constrains an axis attached to body1 to be inside a cone relative to body2
    class idAFConstraint_ConeLimit : idAFConstraint() {
        protected val body1Axis // axis in body1 space that should stay within the cone
                : idVec3
        protected val coneAnchor // top of the cone in body2 space
                : idVec3
        protected val coneAxis // cone axis in body2 space
                : idVec3
        protected var cosAngle // cos( coneAngle / 2 )
                = 0.0f
        protected var cosHalfAngle // cos( coneAngle / 4 )
                = 0.0f
        protected var epsilon // lcp epsilon
                = 0.0f
        protected var sinHalfAngle // sin( coneAngle / 4 )
                = 0.0f

        /*
         ================
         idAFConstraint_ConeLimit::Setup

         the coneAnchor is the top of the cone in body2 space
         the coneAxis is the axis of the cone in body2 space
         the coneAngle is the angle the cone hull makes at the top
         the body1Axis is the axis in body1 space that should stay within the cone
         ================
         */
        fun Setup(
            b1: idAFBody?, b2: idAFBody?, coneAnchor: idVec3, coneAxis: idVec3,
            coneAngle: Float, body1Axis: idVec3
        ) {
            body1 = b1
            body2 = b2
            this.coneAxis.set(coneAxis)
            this.coneAxis.Normalize()
            this.coneAnchor.set(coneAnchor)
            this.body1Axis.set(body1Axis)
            this.body1Axis.Normalize()
            cosAngle = cos(DEG2RAD(coneAngle * 0.5f))
            sinHalfAngle = sin(DEG2RAD(coneAngle * 0.25f))
            cosHalfAngle = cos(DEG2RAD(coneAngle * 0.25f))
        }

        fun SetAnchor(coneAnchor: idVec3) {
            this.coneAnchor.set(coneAnchor)
        }

        fun SetBody1Axis(body1Axis: idVec3) {
            this.body1Axis.set(body1Axis)
        }

        fun SetEpsilon(e: Float) {
            epsilon = e
        }

        fun Add(phys: idPhysics_AF?, invTimeStep: Float): Boolean {
            val a: Float
            val J1row = idVec6()
            val J2row = idVec6()
            val ax = idVec3()
            val anchor = idVec3()
            val body1ax = idVec3()
            val normal = idVec3()
            val coneVector = idVec3()
            val p1 = idVec3()
            val p2 = idVec3()
            val q = idQuat()
            val master: idAFBody?
            if (SysCvar.af_skipLimits.GetBool()) {
                lm.Zero() // constraint exerts no force
                return false
            }
            physics = phys
            master = if (body2 != null) body2 else physics!!.GetMasterBody()
            if (master != null) {
                ax.set(coneAxis.times(master.GetWorldAxis()))
                anchor.set(master.GetWorldOrigin().plus(coneAnchor.times(master.GetWorldAxis())))
            } else {
                ax.set(coneAxis)
                anchor.set(coneAnchor)
            }
            body1ax.set(body1Axis.times(body1!!.GetWorldAxis()))
            a = ax.times(body1ax)

            // if the body1 axis is inside the cone
            if (a > cosAngle) {
                lm.Zero() // constraint exerts no force
                return false
            }

            // calculate the inward cone normal for the position the body1 axis went outside the cone
            normal.set(body1ax.Cross(ax))
            normal.Normalize()
            q.x = normal.x * sinHalfAngle
            q.y = normal.y * sinHalfAngle
            q.z = normal.z * sinHalfAngle
            q.w = cosHalfAngle
            coneVector.set(ax.times(q.ToMat3()))
            normal.set(coneVector.Cross(ax).Cross(coneVector))
            normal.Normalize()
            p1.set(anchor.plus(coneVector.times(32.0f)).minus(body1!!.GetWorldOrigin()))
            J1row.SubVec3_oSet(0, normal)
            J1row.SubVec3_oSet(1, p1.Cross(normal))
            J1[1, 6] = J1row.ToFloatPtr()
            c1.p[0] = invTimeStep * LIMIT_ERROR_REDUCTION * normal.times(body1ax.times(32.0f))
            if (body2 != null) {
                p2.set(anchor.plus(coneVector.times(32.0f)).minus(master!!.GetWorldOrigin()))
                J2row.SubVec3_oSet(0, normal.unaryMinus())
                J2row.SubVec3_oSet(1, p2.Cross(normal.unaryMinus()))
                J2[1, 6] = J2row.ToFloatPtr()
                c2.p[0] = 0.0f
            }
            lo.p[0] = 0.0f
            e.p[0] = LIMIT_LCP_EPSILON
            physics!!.AddFrameConstraint(this)
            return true
        }

        override fun DebugDraw() {
            val ax = idVec3()
            val anchor = idVec3()
            val x = idVec3()
            val y = idVec3()
            val z = idVec3()
            val start = idVec3()
            val end = idVec3()
            val sinAngle: Float
            var a: Float
            val size = 10.0f
            val master: idAFBody?
            master = if (body2 != null) body2 else physics!!.GetMasterBody()
            if (master != null) {
                ax.set(coneAxis.times(master.GetWorldAxis()))
                anchor.set(master.GetWorldOrigin().plus(coneAnchor.times(master.GetWorldAxis())))
            } else {
                ax.set(coneAxis)
                anchor.set(coneAnchor)
            }

            // draw body1 axis
            Game_local.gameRenderWorld!!.DebugLine(
                colorGreen,
                anchor,
                anchor.plus(body1Axis.times(body1!!.GetWorldAxis()).times(size))
            )

            // draw cone
            ax.NormalVectors(x, y)
            sinAngle = idMath.Sqrt(1.0f - cosAngle * cosAngle)
            x.timesAssign(size * sinAngle)
            y.timesAssign(size * sinAngle)
            z.set(anchor.plus(ax.times(size * cosAngle)))
            start.set(x.plus(z))
            a = 0.0f
            while (a < 360.0f) {
                end.set(
                    x.times(cos(DEG2RAD(a + 45.0f)))
                        .plus(y.times(sin(DEG2RAD(a + 45.0f))).plus(z))
                )
                Game_local.gameRenderWorld!!.DebugLine(colorMagenta, anchor, start)
                Game_local.gameRenderWorld!!.DebugLine(colorMagenta, start, end)
                start.set(end)
                a += 45.0f
            }
        }

        override fun Translate(translation: idVec3) {
            if (null == body2) {
                coneAnchor.plusAssign(translation)
            }
        }

        override fun Rotate(rotation: idRotation) {
            if (null == body2) {
                coneAnchor.timesAssign(rotation)
                coneAxis.timesAssign(rotation.ToMat3())
            }
        }

        override fun Save(saveFile: idSaveGame) {
            super.Save(saveFile)
            saveFile.WriteVec3(coneAnchor)
            saveFile.WriteVec3(coneAxis)
            saveFile.WriteVec3(body1Axis)
            saveFile.WriteFloat(cosAngle)
            saveFile.WriteFloat(sinHalfAngle)
            saveFile.WriteFloat(cosHalfAngle)
            saveFile.WriteFloat(epsilon)
        }

        override fun Restore(saveFile: idRestoreGame) {
            val cosAngle = CFloat()
            val sinHalfAngle = CFloat()
            val cosHalfAngle = CFloat()
            val epsilon = CFloat()
            super.Restore(saveFile)
            saveFile.ReadVec3(coneAnchor)
            saveFile.ReadVec3(coneAxis)
            saveFile.ReadVec3(body1Axis)
            saveFile.ReadFloat(cosAngle)
            saveFile.ReadFloat(sinHalfAngle)
            saveFile.ReadFloat(cosHalfAngle)
            saveFile.ReadFloat(epsilon)
            this.cosAngle = cosAngle._val
            this.sinHalfAngle = sinHalfAngle._val
            this.cosHalfAngle = cosHalfAngle._val
            this.epsilon = epsilon._val
        }

        override fun Evaluate(invTimeStep: Float) {
            // do nothing
        }

        override fun ApplyFriction(invTimeStep: Float) {}

        //
        //
        init {
            coneAnchor = idVec3()
            coneAxis = idVec3()
            body1Axis = idVec3()
            type = constraintType_t.CONSTRAINT_CONELIMIT
            name.set("coneLimit")
            InitSize(1)
            fl.allowPrimary = false
            fl.frameConstraint = true
        }
    }

    //===============================================================
    //
    //	idAFConstraint_PyramidLimit
    //
    //===============================================================
    // constrains an axis attached to body1 to be inside a pyramid relative to body2
    class idAFConstraint_PyramidLimit : idAFConstraint() {
        protected val body1Axis // axis in body1 space that should stay within the cone
                : idVec3
        protected val pyramidAnchor // top of the pyramid in body2 space
                : idVec3
        protected var cosAngle: FloatArray = FloatArray(2) // cos( pyramidAngle / 2 )
        protected var cosHalfAngle: FloatArray = FloatArray(2) // cos( pyramidAngle / 4 )
        protected var epsilon // lcp epsilon
                = 0.0f
        protected val pyramidBasis // pyramid basis in body2 space with base[2] being the pyramid axis
                : idMat3
        protected var sinHalfAngle: FloatArray = FloatArray(2) // sin( pyramidAngle / 4 )
        fun Setup(
            b1: idAFBody?, b2: idAFBody?, pyramidAnchor: idVec3, pyramidAxis: idVec3,
            baseAxis: idVec3, pyramidAngle1: Float, pyramidAngle2: Float, body1Axis: idVec3
        ) {
            body1 = b1
            body2 = b2
            // setup the base and make sure the basis is orthonormal
            pyramidBasis[2] = pyramidAxis
            pyramidBasis[2].Normalize()
            pyramidBasis[0] = baseAxis
            pyramidBasis[0].minusAssign(pyramidBasis[2].times(baseAxis.times(pyramidBasis[2])))
            pyramidBasis[0].Normalize()
            pyramidBasis[1] = pyramidBasis[0].Cross(pyramidBasis[2])
            // pyramid top
            pyramidAnchor.set(pyramidAnchor)
            // angles
            cosAngle[0] = cos(DEG2RAD(pyramidAngle1 * 0.5f))
            cosAngle[1] = cos(DEG2RAD(pyramidAngle2 * 0.5f))
            sinHalfAngle[0] = sin(DEG2RAD(pyramidAngle1 * 0.25f))
            sinHalfAngle[1] = sin(DEG2RAD(pyramidAngle2 * 0.25f))
            cosHalfAngle[0] = cos(DEG2RAD(pyramidAngle1 * 0.25f))
            cosHalfAngle[1] = cos(DEG2RAD(pyramidAngle2 * 0.25f))
            body1Axis.set(body1Axis)
        }

        fun SetAnchor(pyramidAxis: idVec3) {
            pyramidAnchor.set(pyramidAnchor)
        }

        fun SetBody1Axis(body1Axis: idVec3) {
            this.body1Axis.set(body1Axis)
        }

        fun SetEpsilon(e: Float) {
            epsilon = e
        }

        fun Add(phys: idPhysics_AF?, invTimeStep: Float): Boolean {
            var i: Int
            val a = FloatArray(2)
            val J1row = idVec6()
            val J2row = idVec6()
            val worldBase = idMat3()
            val anchor = idVec3()
            val body1ax = idVec3()
            val v = idVec3()
            val normal = idVec3()
            val pyramidVector = idVec3()
            val p1 = idVec3()
            val p2 = idVec3()
            val ax: Array<idVec3> = idVec3.generateArray(2)
            val q = idQuat()
            val master: idAFBody?
            if (SysCvar.af_skipLimits.GetBool()) {
                lm.Zero() // constraint exerts no force
                return false
            }
            physics = phys
            master = if (body2 != null) body2 else physics!!.GetMasterBody()
            if (master != null) {
                worldBase[0] = pyramidBasis[0].times(master.GetWorldAxis())
                worldBase[1] = pyramidBasis[1].times(master.GetWorldAxis())
                worldBase[2] = pyramidBasis[2].times(master.GetWorldAxis())
                anchor.set(master.GetWorldOrigin().plus(pyramidAnchor.times(master.GetWorldAxis())))
            } else {
                worldBase.set(pyramidBasis)
                anchor.set(pyramidAnchor)
            }
            body1ax.set(body1Axis.times(body1!!.GetWorldAxis()))
            i = 0
            while (i < 2) {
                val he = if (i == 0) 1 else 0
                ax[i].set(body1ax.minus(worldBase[he].times(body1ax.times(worldBase[he]))))
                ax[i].Normalize()
                a[i] = worldBase[2].times(ax[i])
                i++
            }

            // if the body1 axis is inside the pyramid
            if (a[0] > cosAngle[0] && a[1] > cosAngle[1]) {
                lm.Zero() // constraint exerts no force
                return false
            }

            // calculate the inward pyramid normal for the position the body1 axis went outside the pyramid
            pyramidVector.set(worldBase[2])
            i = 0
            while (i < 2) {
                if (a[i] <= cosAngle[i]) {
                    v.set(ax[i].Cross(worldBase[2]))
                    v.Normalize()
                    q.x = v.x * sinHalfAngle[i]
                    q.y = v.y * sinHalfAngle[i]
                    q.z = v.z * sinHalfAngle[i]
                    q.w = cosHalfAngle[i]
                    pyramidVector.timesAssign(q.ToMat3())
                }
                i++
            }
            normal.set(pyramidVector.Cross(worldBase[2]).Cross(pyramidVector))
            normal.Normalize()
            p1.set(anchor.plus(pyramidVector.times(32.0f).minus(body1!!.GetWorldOrigin())))
            J1row.SubVec3_oSet(0, normal)
            J1row.SubVec3_oSet(1, p1.Cross(normal))
            J1[1, 6] = J1row.ToFloatPtr()
            c1.p[0] = invTimeStep * LIMIT_ERROR_REDUCTION * normal.times(body1ax.times(32.0f))
            if (body2 != null) {
                p2.set(anchor.plus(pyramidVector.times(32.0f).minus(master!!.GetWorldOrigin())))
                J2row.SubVec3_oSet(0, normal.unaryMinus())
                J2row.SubVec3_oSet(1, p2.Cross(normal.unaryMinus()))
                J2[1, 6] = J2row.ToFloatPtr()
                c2.p[0] = 0.0f
            }
            lo.p[0] = 0.0f
            e.p[0] = LIMIT_LCP_EPSILON
            physics!!.AddFrameConstraint(this)
            return true
        }

        override fun DebugDraw() {
            var i: Int
            val size = 10.0f
            val anchor = idVec3()
            val dir = idVec3()
            val p: Array<idVec3> = idVec3.generateArray(4)
            val worldBase = idMat3()
            val m = Array(2) { idMat3() }
            val q = idQuat()
            val master: idAFBody?
            master = if (body2 != null) body2 else physics!!.GetMasterBody()
            if (master != null) {
                worldBase[0] = pyramidBasis[0].times(master.GetWorldAxis())
                worldBase[1] = pyramidBasis[1].times(master.GetWorldAxis())
                worldBase[2] = pyramidBasis[2].times(master.GetWorldAxis())
                anchor.set(master.GetWorldOrigin().plus(pyramidAnchor.times(master.GetWorldAxis())))
            } else {
                worldBase.set(pyramidBasis)
                anchor.set(pyramidAnchor)
            }

            // draw body1 axis
            Game_local.gameRenderWorld!!.DebugLine(
                colorGreen,
                anchor,
                anchor.plus(body1Axis.times(body1!!.GetWorldAxis()).times(size))
            )

            // draw the pyramid
            i = 0
            while (i < 2) {
                val him = if (i == 0) 1 else 0
                q.x = worldBase[him].x * sinHalfAngle[i]
                q.y = worldBase[him].y * sinHalfAngle[i]
                q.z = worldBase[him].z * sinHalfAngle[i]
                q.w = cosHalfAngle[i]
                m[i] = q.ToMat3()
                i++
            }
            dir.set(worldBase[2].times(size))
            p[0].set(anchor.plus(m[0].times(m[1].times(dir))))
            p[1].set(anchor.plus(m[0].times(m[1].Transpose().times(dir))))
            p[2].set(anchor.plus(m[0].Transpose().times(m[1].Transpose().times(dir))))
            p[3].set(anchor.plus(m[0].Transpose().times(m[1].times(dir))))
            i = 0
            while (i < 4) {
                Game_local.gameRenderWorld!!.DebugLine(colorMagenta, anchor, p[i])
                Game_local.gameRenderWorld!!.DebugLine(colorMagenta, p[i], p[i + 1 and 3])
                i++
            }
        }

        override fun Translate(translation: idVec3) {
            if (null == body2) {
                pyramidAnchor.plusAssign(translation)
            }
        }

        override fun Rotate(rotation: idRotation) {
            if (null == body2) {
                pyramidAnchor.timesAssign(rotation)
                pyramidBasis[0].timesAssign(rotation.ToMat3())
                pyramidBasis[1].timesAssign(rotation.ToMat3())
                pyramidBasis[2].timesAssign(rotation.ToMat3())
            }
        }

        override fun Save(saveFile: idSaveGame) {
            super.Save(saveFile)
            saveFile.WriteVec3(pyramidAnchor)
            saveFile.WriteMat3(pyramidBasis)
            saveFile.WriteVec3(body1Axis)
            saveFile.WriteFloat(cosAngle[0])
            saveFile.WriteFloat(cosAngle[1])
            saveFile.WriteFloat(sinHalfAngle[0])
            saveFile.WriteFloat(sinHalfAngle[1])
            saveFile.WriteFloat(cosHalfAngle[0])
            saveFile.WriteFloat(cosHalfAngle[1])
            saveFile.WriteFloat(epsilon)
        }

        override fun Restore(saveFile: idRestoreGame) {
            val cosAngle = listOf(CFloat(), CFloat())
            val sinHalfAngle = listOf(CFloat(), CFloat())
            val cosHalfAngle = listOf(CFloat(), CFloat())
            val epsilon = CFloat()
            super.Restore(saveFile)
            saveFile.ReadVec3(pyramidAnchor)
            saveFile.ReadMat3(pyramidBasis)
            saveFile.ReadVec3(body1Axis)
            saveFile.ReadFloat(cosAngle[0])
            saveFile.ReadFloat(cosAngle[1])
            saveFile.ReadFloat(sinHalfAngle[0])
            saveFile.ReadFloat(sinHalfAngle[1])
            saveFile.ReadFloat(cosHalfAngle[0])
            saveFile.ReadFloat(cosHalfAngle[1])
            saveFile.ReadFloat(epsilon)
            this.cosAngle[0] = cosAngle[0]._val
            this.cosAngle[1] = cosAngle[1]._val
            this.sinHalfAngle[0] = sinHalfAngle[0]._val
            this.sinHalfAngle[1] = sinHalfAngle[1]._val
            this.cosHalfAngle[0] = cosHalfAngle[0]._val
            this.cosHalfAngle[1] = cosHalfAngle[1]._val
            this.epsilon = epsilon._val
        }

        override fun Evaluate(invTimeStep: Float) {
            // do nothing
        }

        override fun ApplyFriction(invTimeStep: Float) {}

        //
        //
        init {
            type = constraintType_t.CONSTRAINT_PYRAMIDLIMIT
            name.set("pyramidLimit")
            InitSize(1)
            fl.allowPrimary = false
            fl.frameConstraint = true
            pyramidAnchor = idVec3()
            pyramidBasis = idMat3()
            body1Axis = idVec3()
        }
    }

    //===============================================================
    //
    //	idAFConstraint_Suspension
    //
    //===============================================================
    // vehicle suspension
    class idAFConstraint_Suspension : idAFConstraint() {
        protected val localOrigin // position of suspension relative to body1
                : idVec3
        protected val wheelOffset // wheel position relative to body1
                : idVec3
        protected var epsilon // lcp epsilon
                : Float
        protected var friction // friction
                : Float
        protected val localAxis // orientation of suspension relative to body1
                : idMat3 = idMat3()
        protected var motorEnabled // whether the motor is enabled or not
                : Boolean
        protected var motorForce // motor force
                : Float
        protected var motorVelocity // desired velocity
                : Float
        protected var steerAngle // desired steer angle in degrees
                : Float
        protected var suspensionDamping // spring damping
                : Float
        protected var suspensionDown // suspension down movement
                : Float
        protected var suspensionKCompress // spring compress constant
                : Float
        protected var suspensionUp // suspension up movement
                : Float
        protected var trace // contact point with the ground
                : trace_s
        protected var wheelModel // wheel model
                : idClipModel?

        fun Setup(name: String, body: idAFBody?, origin: idVec3, axis: idMat3, clipModel: idClipModel?) {
            this.name.set(name)
            body1 = body
            body2 = null
            localOrigin.set(origin.minus(body!!.GetWorldOrigin()).times(body.GetWorldAxis().Transpose()))
            localAxis.set(axis.times(body.GetWorldAxis().Transpose()))
            wheelModel = clipModel
        }

        fun SetSuspension(up: Float, down: Float, k: Float, d: Float, f: Float) {
            suspensionUp = up
            suspensionDown = down
            suspensionKCompress = k
            suspensionDamping = d
            friction = f
        }

        fun SetSteerAngle(degrees: Float) {
            steerAngle = degrees
        }

        fun EnableMotor(enable: Boolean) {
            motorEnabled = enable
        }

        fun SetMotorForce(force: Float) {
            motorForce = force
        }

        fun SetMotorVelocity(vel: Float) {
            motorVelocity = vel
        }

        fun SetEpsilon(e: Float) {
            epsilon = e
        }

        fun GetWheelOrigin(): idVec3 {
            return body1!!.GetWorldOrigin().plus(wheelOffset.times(body1!!.GetWorldAxis()))
        }

        override fun DebugDraw() {
            val origin = idVec3()
            val axis: idMat3
            val rotation = idRotation()
            axis = localAxis.times(body1!!.GetWorldAxis())
            rotation.SetVec(axis[2])
            rotation.SetAngle(steerAngle)
            axis.timesAssign(rotation.ToMat3())
            if (trace.fraction < 1.0f) {
                origin.set(trace.c.point)
                Game_local.gameRenderWorld!!.DebugLine(
                    colorWhite,
                    origin,
                    origin.plus(axis[2].times(6.0f))
                )
                Game_local.gameRenderWorld!!.DebugLine(
                    colorWhite,
                    origin.minus(axis[0].times(4.0f)),
                    origin.plus(axis[0].times(4.0f))
                )
                Game_local.gameRenderWorld!!.DebugLine(
                    colorWhite,
                    origin.minus(axis[1].times(2.0f)),
                    origin.plus(axis[1].times(2.0f))
                )
            }
        }

        override fun Translate(translation: idVec3) {}
        override fun Rotate(rotation: idRotation) {}
        override fun Evaluate(invTimeStep: Float) {
            var velocity: Float
            val suspensionLength: Float
            val springLength: Float
            val compression: Float
            val dampingForce: Float
            val springForce: Float
            val origin = idVec3()
            val start = idVec3()
            val end = idVec3()
            val vel1 = idVec3()
            val vel2 = idVec3()
            val springDir = idVec3()
            val r = idVec3()
            val frictionDir = idVec3()
            val motorDir = idVec3()
            val axis: idMat3
            val rotation = idRotation()
            axis = localAxis.times(body1!!.GetWorldAxis())
            origin.set(body1!!.GetWorldOrigin().plus(localOrigin.times(body1!!.GetWorldAxis())))
            start.set(origin.plus(axis[2].times(suspensionUp)))
            end.set(origin.minus(axis[2].times(suspensionDown)))
            rotation.SetVec(axis[2])
            rotation.SetAngle(steerAngle)
            axis.timesAssign(rotation.ToMat3())
            run {
                val tracy = trace
                Game_local.gameLocal.clip.Translation(tracy, start, end, wheelModel, axis, Game_local.MASK_SOLID, null)
                this.trace = tracy
            }
            wheelOffset.set(trace.endpos.minus(body1!!.GetWorldOrigin()).times(body1!!.GetWorldAxis().Transpose()))
            if (trace.fraction >= 1.0f) {
                J1.SetSize(0, 6)
                if (body2 != null) {
                    J2.SetSize(0, 6)
                }
                return
            }

            // calculate and add spring force
            vel1.set(body1!!.GetPointVelocity(start))
            if (body2 != null) {
                vel2.set(body2!!.GetPointVelocity(trace.c.point))
            } else {
                vel2.Zero()
            }
            suspensionLength = suspensionUp + suspensionDown
            springDir.set(trace.endpos.minus(start))
            springLength = trace.fraction * suspensionLength
            dampingForce = suspensionDamping * abs(
                vel2.minus(vel1).times(springDir)
            ) / (1.0f + springLength * springLength)
            compression = suspensionLength - springLength
            springForce = compression * compression * suspensionKCompress - dampingForce
            r.set(trace.c.point.minus(body1!!.GetWorldOrigin()))
            J1.SetSize(2, 6)
            J1.SubVec63_oSet(0, 0, trace.c.normal)
            J1.SubVec63_oSet(0, 1, r.Cross(trace.c.normal))
            c1.SetSize(2)
            c1.p[0] = 0.0f
            velocity = J1.SubVec6(0).SubVec3(0).times(body1!!.GetLinearVelocity()) + J1.SubVec6(0).SubVec3(1)
                .times(body1!!.GetAngularVelocity())
            if (body2 != null) {
                r.set(trace.c.point.minus(body2!!.GetWorldOrigin()))
                J2.SetSize(2, 6)
                J2.SubVec63_oSet(0, 0, trace.c.normal.unaryMinus())
                J2.SubVec63_oSet(0, 1, r.Cross(trace.c.normal.unaryMinus()))
                c2.SetSize(2)
                c2.p[0] = 0.0f
                velocity += J2.SubVec6(0).SubVec3(0).times(body2!!.GetLinearVelocity()) + J2.SubVec6(0).SubVec3(1)
                    .times(body2!!.GetAngularVelocity())
            }
            c1.p[0] = -compression // + 0.5f * -velocity;
            e.p[0] = 1e-4f
            lo.p[0] = 0.0f
            hi.p[0] = springForce
            boxConstraint = null
            boxIndex[0] = -1

            // project the friction direction into the contact plane
            frictionDir.set(axis[1].minus(axis[1].times(trace.c.normal.times(axis[1]))))
            frictionDir.Normalize()
            r.set(trace.c.point.minus(body1!!.GetWorldOrigin()))
            J1.SubVec63_oSet(1, 0, frictionDir)
            J1.SubVec63_oSet(1, 1, r.Cross(frictionDir))
            c1.p[1] = 0.0f
            if (body2 != null) {
                r.set(trace.c.point.minus(body2!!.GetWorldOrigin()))
                J2.SubVec63_oSet(1, 0, frictionDir.unaryMinus())
                J2.SubVec63_oSet(1, 1, r.Cross(frictionDir.unaryMinus()))
                c2.p[1] = 0.0f
            }
            lo.p[1] = -friction * physics!!.GetContactFrictionScale()
            hi.p[1] = friction * physics!!.GetContactFrictionScale()
            boxConstraint = this
            boxIndex[1] = 0
            if (motorEnabled) {
                // project the motor force direction into the contact plane
                motorDir.set(axis[0].minus(axis[0].times(trace.c.normal.times(axis[0]))))
                motorDir.Normalize()
                r.set(trace.c.point.minus(body1!!.GetWorldOrigin()))
                J1.ChangeSize(3, J1.GetNumColumns())
                J1.SubVec63_oSet(2, 0, motorDir.unaryMinus())
                J1.SubVec63_oSet(2, 1, r.Cross(motorDir.unaryMinus()))
                c1.ChangeSize(3)
                c1.p[2] = motorVelocity
                if (body2 != null) {
                    r.set(trace.c.point.minus(body2!!.GetWorldOrigin()))
                    J2.ChangeSize(3, J2.GetNumColumns())
                    J2.SubVec63_oSet(2, 0, motorDir.unaryMinus())
                    J2.SubVec63_oSet(2, 1, r.Cross(motorDir.unaryMinus()))
                    c2.ChangeSize(3)
                    c2.p[2] = 0.0f
                }
                lo.p[2] = -motorForce
                hi.p[2] = motorForce
                boxIndex[2] = -1
            }
        }

        override fun ApplyFriction(invTimeStep: Float) {
            // do nothing
        }

        //
        //
        init {
            type = constraintType_t.CONSTRAINT_SUSPENSION
            name.set("suspension")
            InitSize(3)
            fl.allowPrimary = false
            fl.frameConstraint = true
            localOrigin = idVec3()
            localOrigin.Zero()
            localAxis.Identity()
            suspensionUp = 0.0f
            suspensionDown = 0.0f
            suspensionKCompress = 0.0f
            suspensionDamping = 0.0f
            steerAngle = 0.0f
            friction = 2.0f
            motorEnabled = false
            motorForce = 0.0f
            motorVelocity = 0.0f
            wheelModel = null
            trace = trace_s() //	memset( &trace, 0, sizeof( trace ) );
            epsilon = LCP_EPSILON
            wheelOffset = idVec3()
        }
    }

    //===============================================================
    //
    //	idAFBody
    //
    //===============================================================
    class AFBodyPState_s() {
        val worldOrigin // position in world space
                : idVec3
        val externalForce // external force and torque applied to body
                : idVec6
        val spatialVelocity // linear and rotational velocity of body
                : idVec6
        val worldAxis // axis at worldOrigin
                : idMat3

        constructor(bodyPState_s: AFBodyPState_s?) : this() {
            oSet(bodyPState_s)
        }

        fun oSet(body: AFBodyPState_s?) {
            worldOrigin.set(body!!.worldOrigin)
            worldAxis.set(body.worldAxis)
            spatialVelocity.set(body.spatialVelocity)
            externalForce.set(body.externalForce)
        }

        init {
            worldOrigin = idVec3()
            worldAxis = idMat3()
            spatialVelocity = idVec6()
            externalForce = idVec6()
        }
    }

    class idAFBody {
        val atRestOrigin: idVec3 = idVec3() // origin at rest
        val centerOfMass: idVec3 = idVec3() // center of mass of body
        private val contactMotorDir: idVec3 = idVec3() // contact motor direction
        private val frictionDir: idVec3 = idVec3() // specifies a single direction of friction in body space

        //
        // physics state
        private val state: Array<AFBodyPState_s> = Array(2) { AFBodyPState_s() }
        val I: idMatX = idMatX()
        val invI // transformed inertia
                : idMatX = idMatX()
        val J // transformed constraint matrix
                : idMatX = idMatX()
        val acceleration // acceleration
                : idVecX = idVecX(6)
        var angularFriction // rotational friction
                = 0.0f
        val atRestAxis // axis at rest
                : idMat3 = idMat3()
        val auxForce // force from auxiliary constraints
                : idVecX = idVecX(6)
        var bouncyness // bounce
                = 0.0f
        val children: idList<idAFBody> = idList() // children of this body
        var clipMask // contents this body collides with
                = 0
        var clipModel // model used for collision detection
                : idClipModel? = null
        val constraints: idList<idAFConstraint> = idList() // all constraints attached to this body
        var contactFriction // friction with contact surfaces
                = 0.0f
        private var contactMotorForce // maximum force applied to reach the motor velocity
                = 0.0f
        private var contactMotorVelocity // contact motor velocity
                = 0.0f
        lateinit var current // current physics state
                : AFBodyPState_s

        //
        var fl: bodyFlags_s = bodyFlags_s()
        val inertiaTensor // inertia tensor
                : idMat3 = idMat3()
        var invMass // inverse mass
                = 0.0f
        val inverseInertiaTensor // inverse inertia tensor
                : idMat3 = idMat3()

        //
        // simulation variables used during calculations
        val inverseWorldSpatialInertia // inverse spatial inertia in world space
                : idMatX = idMatX()
        var linearFriction // translational friction
                = 0.0f

        //
        // derived properties
        var mass // mass of body
                = 0.0f
        var maxAuxiliaryIndex // largest index of an auxiliary constraint constraining this body
                = 0
        var maxSubTreeAuxiliaryIndex // largest index of an auxiliary constraint constraining this body or one of it's children
                = 0

        // properties
        val name // name of body
                : idStr = idStr()
        lateinit var next // next physics state
                : AFBodyPState_s
        var numResponses // number of response forces
                = 0
        var parent // parent of this body
                : idAFBody? = null
        var primaryConstraint // primary constraint (this.constraint.body1 = this)
                : idAFConstraint? = null
        var response // forces on body in response to auxiliary constraint forces
                : FloatArray? = null
        var responseIndex // index to response forces
                : IntArray? = null
        val s // temp solution
                : idVecX = idVecX(6)
        var saved // saved physics state
                : AFBodyPState_s = AFBodyPState_s()
        val totalForce // total force acting on body
                : idVecX = idVecX(6)
        var tree // tree structure this body is part of
                : idAFTree? = null

        //
        //
        // friend class idPhysics_AF;
        // friend class idAFTree;
        constructor() {
            Init()
        }

        constructor(name: idStr, clipModel: idClipModel, density: Float) {
            assert(clipModel != null)
            assert(clipModel.IsTraceModel())
            Init()
            this.name.set(name)
            this.clipModel = null
            SetClipModel(clipModel)
            SetDensity(density)
            current.worldOrigin.set(clipModel.GetOrigin())
            current.worldAxis.set(clipModel.GetAxis())
            next.oSet(current)
        }

        // ~idAFBody();
        protected fun _deconstructor() {
            idClipModel.delete(clipModel!!)
        }

        fun Init() {
            name.set(idStr("noname"))
            parent = null
            children.Clear()
            constraints.Clear()
            clipModel = null
            primaryConstraint = null
            tree = null
            linearFriction = -1.0f
            angularFriction = -1.0f
            contactFriction = -1.0f
            bouncyness = -1.0f
            clipMask = 0
            frictionDir.set(getVec3_zero())
            contactMotorDir.set(getVec3_zero())
            contactMotorVelocity = 0.0f
            contactMotorForce = 0.0f
            mass = 1.0f
            invMass = 1.0f
            centerOfMass.set(getVec3_zero())
            inertiaTensor.set(idMat3.getMat3_identity())
            inverseInertiaTensor.set(idMat3.getMat3_identity())
            state[0] = AFBodyPState_s()
            current = state[0]
            state[1] = AFBodyPState_s()
            next = state[1]
            current.worldOrigin.set(getVec3_zero())
            current.worldAxis.set(idMat3.getMat3_identity())
            current.spatialVelocity.set(getVec6_zero())
            current.externalForce.set(getVec6_zero())
            next.oSet(current)
            saved = AFBodyPState_s(current)
            atRestOrigin.set(getVec3_zero())
            atRestAxis.set(idMat3.getMat3_identity())
            //inverseWorldSpatialInertia.set(idMatX())
            //I.set(idMatX())
            //invI.set(idMatX())
            //J = idMatX()
            s.set(idVecX(6))
            totalForce.set(idVecX(6))
            auxForce.set(idVecX(6))
            acceleration.set(idVecX(6))
            response = null
            responseIndex = null
            numResponses = 0
            maxAuxiliaryIndex = 0
            maxSubTreeAuxiliaryIndex = 0
            fl = bodyFlags_s() //	memset( &fl, 0, sizeof( fl ) );
            fl.selfCollision = true
            fl.isZero = true
        }

        fun GetName(): idStr {
            return name
        }

        fun GetWorldOrigin(): idVec3 {
            return current.worldOrigin
        }

        fun GetWorldAxis(): idMat3 {
            return current.worldAxis
        }

        fun GetLinearVelocity(): idVec3 {
            return current.spatialVelocity.SubVec3(0)
        }

        fun GetAngularVelocity(): idVec3 {
            return current.spatialVelocity.SubVec3(1)
        }

        fun GetPointVelocity(point: idVec3): idVec3 {
            val r = idVec3(point.minus(current.worldOrigin))
            return current.spatialVelocity.SubVec3(0).plus(current.spatialVelocity.SubVec3(1).Cross(r))
        }

        fun GetCenterOfMass(): idVec3 {
            return centerOfMass
        }

        fun SetClipModel(clipModel: idClipModel?) {
//	if ( this.clipModel && this.clipModel != clipModel ) {
//		delete this.clipModel;
//	}
            //blessed be the garbage collector
            this.clipModel = clipModel
        }

        fun GetClipModel(): idClipModel? {
            return clipModel
        }

        fun SetClipMask(mask: Int) {
            clipMask = mask
            fl.clipMaskSet = true
        }

        fun GetClipMask(): Int {
            return clipMask
        }

        fun SetSelfCollision(enable: Boolean) {
            fl.selfCollision = enable
        }

        fun SetWorldOrigin(origin: idVec3) {
            current.worldOrigin.set(origin)
        }

        fun SetWorldAxis(axis: idMat3) {
            current.worldAxis.set(axis)
        }

        fun SetLinearVelocity(linear: idVec3) {
            current.spatialVelocity.SubVec3_oSet(0, linear)
        }

        fun SetAngularVelocity(angular: idVec3) {
            current.spatialVelocity.SubVec3_oSet(1, angular)
        }

        fun SetFriction(linear: Float, angular: Float, contact: Float) {
            if (linear < 0.0f || linear > 1.0f || angular < 0.0f || angular > 1.0f || contact < 0.0f) {
                Game_local.gameLocal.Warning(
                    "idAFBody::SetFriction: friction out of range, linear = %.1f, angular = %.1f, contact = %.1f",
                    linear,
                    angular,
                    contact
                )
                return
            }
            linearFriction = linear
            angularFriction = angular
            contactFriction = contact
        }

        fun GetContactFriction(): Float {
            return contactFriction
        }

        fun SetBouncyness(bounce: Float) {
            if (bounce < 0.0f || bounce > 1.0f) {
                Game_local.gameLocal.Warning("idAFBody::SetBouncyness: bouncyness out of range, bounce = %.1f", bounce)
                return
            }
            bouncyness = bounce
        }

        fun GetBouncyness(): Float {
            return bouncyness
        }


        fun SetDensity(
            density: Float,
            inertiaScale: idMat3 = idMat3.getMat3_identity() /*= mat3_identity*/
        ) {
            val massTemp = CFloat(mass)

            // get the body mass properties
            clipModel!!.GetMassProperties(density, massTemp, centerOfMass, inertiaTensor)
            mass = massTemp._val

            // make sure we have a valid mass
            if (mass <= 0.0f || FLOAT_IS_NAN(mass)) {
                Game_local.gameLocal.Warning("idAFBody::SetDensity: invalid mass for body '%s'", name)
                mass = 1.0f
                centerOfMass.Zero()
                inertiaTensor.Identity()
            }

            // make sure the center of mass is at the body origin
            if (!centerOfMass.Compare(getVec3Origin(), CENTER_OF_MASS_EPSILON)) {
                Game_local.gameLocal.Warning("idAFBody::SetDentity: center of mass not at origin for body '%s'", name)
            }
            centerOfMass.Zero()

            // calculate the inverse mass and inverse inertia tensor
            invMass = 1.0f / mass
            if (inertiaScale != idMat3.getMat3_identity()) {
                inertiaTensor.timesAssign(inertiaScale)
            }
            if (inertiaTensor.IsDiagonal(1e-3f)) {
                inertiaTensor.set(0, 1, inertiaTensor.set(0, 2, 0.0f))
                inertiaTensor.set(1, 0, inertiaTensor.set(1, 2, 0.0f))
                inertiaTensor.set(2, 0, inertiaTensor.set(2, 1, 0.0f))
                inverseInertiaTensor.Identity()
                inverseInertiaTensor.set(0, 0, 1.0f / inertiaTensor[0, 0])
                inverseInertiaTensor.set(1, 1, 1.0f / inertiaTensor[1, 1])
                inverseInertiaTensor.set(2, 2, 1.0f / inertiaTensor[2, 2])
            } else {
                inverseInertiaTensor.set(inertiaTensor.Inverse())
            }
        }

        fun GetInverseMass(): Float {
            return invMass
        }

        fun GetInverseWorldInertia(): idMat3 {
            return current.worldAxis.Transpose().times(inverseInertiaTensor.times(current.worldAxis))
        }

        fun SetFrictionDirection(dir: idVec3) {
            frictionDir.set(dir.times(current.worldAxis.Transpose()))
            fl.useFrictionDir = true
        }

        fun GetFrictionDirection(dir: idVec3): Boolean {
            if (fl.useFrictionDir) {
                dir.set(frictionDir.times(current.worldAxis))
                return true
            }
            return false
        }

        fun SetContactMotorDirection(dir: idVec3) {
            contactMotorDir.set(dir.times(current.worldAxis.Transpose()))
            fl.useContactMotorDir = true
        }

        fun GetContactMotorDirection(dir: idVec3): Boolean {
            if (fl.useContactMotorDir) {
                dir.set(contactMotorDir.times(current.worldAxis))
                return true
            }
            return false
        }

        fun SetContactMotorVelocity(vel: Float) {
            contactMotorVelocity = vel
        }

        fun GetContactMotorVelocity(): Float {
            return contactMotorVelocity
        }

        fun SetContactMotorForce(force: Float) {
            contactMotorForce = force
        }

        fun GetContactMotorForce(): Float {
            return contactMotorForce
        }

        fun AddForce(point: idVec3, force: idVec3) {
            current.externalForce.SubVec3_oPluSet(0, force)
            current.externalForce.SubVec3_oPluSet(1, point.minus(current.worldOrigin).Cross(force))
        }

        /*
         ================
         idAFBody::InverseWorldSpatialInertiaMultiply

         dst = this->inverseWorldSpatialInertia * v;
         ================
         */
        fun InverseWorldSpatialInertiaMultiply(dst: idVecX, v: FloatArray) {
            val mPtr = inverseWorldSpatialInertia.ToFloatPtr()
            val dstPtr = dst.ToFloatPtr()
            if (fl.spatialInertiaSparse) {
                dstPtr[0] = mPtr[0 * 6 + 0] * v[0]
                dstPtr[1] = mPtr[1 * 6 + 1] * v[1]
                dstPtr[2] = mPtr[2 * 6 + 2] * v[2]
                dstPtr[3] = mPtr[3 * 6 + 3] * v[3] + mPtr[3 * 6 + 4] * v[4] + mPtr[3 * 6 + 5] * v[5]
                dstPtr[4] = mPtr[4 * 6 + 3] * v[3] + mPtr[4 * 6 + 4] * v[4] + mPtr[4 * 6 + 5] * v[5]
                dstPtr[5] = mPtr[5 * 6 + 3] * v[3] + mPtr[5 * 6 + 4] * v[4] + mPtr[5 * 6 + 5] * v[5]
            } else {
                Game_local.gameLocal.Warning("spatial inertia is not sparse for body %s", name)
            }
        }

        @Deprecated("returns immutable response")
        fun GetResponseForce(index: Int): idVec6 {
//            return reinterpret_cast < idVec6 > (response[ index * 8]);
            return idVec6(Arrays.copyOfRange(response, index * 8, index * 8 + 6))
        }

        fun SetResponseForce(index: Int, v: idVec6) {
            System.arraycopy(v.p, 0, response, index * 8, 6)
        }

        fun Save(saveFile: idSaveGame) {
            saveFile.WriteFloat(linearFriction)
            saveFile.WriteFloat(angularFriction)
            saveFile.WriteFloat(contactFriction)
            saveFile.WriteFloat(bouncyness)
            saveFile.WriteInt(clipMask)
            saveFile.WriteVec3(frictionDir)
            saveFile.WriteVec3(contactMotorDir)
            saveFile.WriteFloat(contactMotorVelocity)
            saveFile.WriteFloat(contactMotorForce)
            saveFile.WriteFloat(mass)
            saveFile.WriteFloat(invMass)
            saveFile.WriteVec3(centerOfMass)
            saveFile.WriteMat3(inertiaTensor)
            saveFile.WriteMat3(inverseInertiaTensor)
            saveFile.WriteVec3(current.worldOrigin)
            saveFile.WriteMat3(current.worldAxis)
            saveFile.WriteVec6(current.spatialVelocity)
            saveFile.WriteVec6(current.externalForce)
            saveFile.WriteVec3(atRestOrigin)
            saveFile.WriteMat3(atRestAxis)
        }

        fun Restore(saveFile: idRestoreGame) {
            linearFriction = saveFile.ReadFloat()
            angularFriction = saveFile.ReadFloat()
            contactFriction = saveFile.ReadFloat()
            bouncyness = saveFile.ReadFloat()
            clipMask = saveFile.ReadInt()
            saveFile.ReadVec3(frictionDir)
            saveFile.ReadVec3(contactMotorDir)
            contactMotorVelocity = saveFile.ReadFloat()
            contactMotorForce = saveFile.ReadFloat()
            mass = saveFile.ReadFloat()
            invMass = saveFile.ReadFloat()
            saveFile.ReadVec3(centerOfMass)
            saveFile.ReadMat3(inertiaTensor)
            saveFile.ReadMat3(inverseInertiaTensor)
            saveFile.ReadVec3(current.worldOrigin)
            saveFile.ReadMat3(current.worldAxis)
            saveFile.ReadVec6(current.spatialVelocity)
            saveFile.ReadVec6(current.externalForce)
            saveFile.ReadVec3(atRestOrigin)
            saveFile.ReadMat3(atRestAxis)
        }

        class bodyFlags_s {
            var clipMaskSet //: 1;          // true if this body has a clip mask set
                    = false
            var isZero //: 1;               // true if 's' is zero during calculations
                    = false
            var selfCollision //: 1;	// true if this body can collide with other bodies of this AF
                    = false
            var spatialInertiaSparse //: 1;	// true if the spatial inertia matrix is sparse
                    = false
            var useContactMotorDir //: 1;	// true if a contact motor should be used
                    = false
            var useFrictionDir //: 1;	// true if a single friction direction should be used
                    = false
        }
    }

    //===============================================================
    //                                                        M
    //  idAFTree                                             MrE
    //                                                        E
    //===============================================================
    class idAFTree {
        //
        //
        val sortedBodies: idList<idAFBody> = idList()

        /*
         ================
         idAFTree::Factor

         factor matrix for the primary constraints in the tree
         ================
         */
        fun Factor() {
            var i: Int
            var j: Int
            var body: idAFBody?
            var child = idAFConstraint()
            val childI = idMatX()
            childI.SetData(6, 6, idMatX.MATX_ALLOCA(6 * 6))

            // from the leaves up towards the root
            i = sortedBodies.Num() - 1
            while (i >= 0) {
                body = sortedBodies[i]
                if (body.children.Num() != 0) {
                    j = 0
                    while (j < body.children.Num()) {
                        child = body.children[j].primaryConstraint!!

                        // child.I = - child.body1!!.J.Transpose() * child.body1!!.I * child.body1!!.J;
                        childI.SetSize(child.J1.GetNumRows(), child.J1.GetNumRows())
                        child.body1!!.J.TransposeMultiply(child.body1!!.I).times(childI, child.body1!!.J)
                        childI.Negate()
                        child.invI.set(childI)
                        if (!child.invI.InverseFastSelf()) {
                            Game_local.gameLocal.Warning(
                                "idAFTree::Factor: couldn't invert %dx%d matrix for constraint '%s'",
                                child.invI.GetNumRows(), child.invI.GetNumColumns(), child.GetName()
                            )
                        }
                        child.J.set(child.invI.times(child.J))
                        body.I.ToFloatPtr().clone()
                        body.I.minusAssign(child.J.TransposeMultiply(childI).times(child.J))
                        j++
                    }
                    body.invI.set(body.I)
                    if (!body.invI.InverseFastSelf()) {
                        Game_local.gameLocal.Warning(
                            "idAFTree::Factor: couldn't invert %dx%d matrix for body %s",
                            child.invI.GetNumRows(), child.invI.GetNumColumns(), body.GetName()
                        )
                    }
                    if (body.primaryConstraint != null) {
                        body.J.ToFloatPtr().clone()
                        body.J.set(body.invI.times(body.J))
                    }
                } else if (body.primaryConstraint != null) {
                    body.J.ToFloatPtr().clone()
                    body.J.set(body.inverseWorldSpatialInertia.times(body.J))
                }
                i--
            }
        }

        /*
         ================
         idAFTree::Solve

         solve for primary constraints in the tree
         ================
         */

        fun Solve(auxiliaryIndex: Int = 0 /*= 0*/) {
            var i: Int
            var j: Int
            var body: idAFBody?
            var child: idAFBody?
            var primaryConstraint: idAFConstraint?

            // from the leaves up towards the root
            i = sortedBodies.Num() - 1
            while (i >= 0) {
                body = sortedBodies[i]
                j = 0
                while (j < body.children.Num()) {
                    child = body.children[j]
                    primaryConstraint = child.primaryConstraint
                    primaryConstraint!!.s.ToFloatPtr().clone()
                    if (!child.fl.isZero) {
                        child.J.TransposeMultiplySub(primaryConstraint.s, child.s)
                        primaryConstraint.fl.isZero = false
                    }
                    if (!primaryConstraint.fl.isZero) {
                        primaryConstraint.J.TransposeMultiplySub(body.s, primaryConstraint.s)
                        body.fl.isZero = false
                    }
                    j++
                }
                i--
            }
            val useSymmetry = SysCvar.af_useSymmetry.GetBool()

            // from the root down towards the leaves
            i = 0
            while (i < sortedBodies.Num()) {
                body = sortedBodies[i]
                primaryConstraint = body.primaryConstraint
                if (primaryConstraint != null) {
                    if (useSymmetry && body.parent!!.maxSubTreeAuxiliaryIndex < auxiliaryIndex) {
                        i++
                        continue
                    }
                    primaryConstraint.s.ToFloatPtr().clone()
                    if (!primaryConstraint.fl.isZero) {
                        primaryConstraint.s.set(primaryConstraint.invI.times(primaryConstraint.s))
                    }
                    primaryConstraint.J.MultiplySub(primaryConstraint.s, primaryConstraint.body2!!.s)
                    primaryConstraint.lm.set(primaryConstraint.s)
                    if (useSymmetry && body.maxSubTreeAuxiliaryIndex < auxiliaryIndex) {
                        i++
                        continue
                    }
                    if (body.children.Num() != 0) {
                        if (!body.fl.isZero) {
                            body.s.set(body.invI.times(body.s))
                        }
                        body.J.MultiplySub(body.s, primaryConstraint.s)
                    }
                } else if (body.children.Num() != 0) {
                    body.s.p.clone()
                    body.s.set(body.invI.times(body.s))
                }
                i++
            }
        }

        /*
         ================
         idAFTree::Response

         calculate body forces in the tree in response to a constraint force
         ================
         */
        fun Response(constraint: idAFConstraint, row: Int, auxiliaryIndex: Int) {
            var i: Int
            var j: Int
            var body: idAFBody?
            var child: idAFConstraint?
            var primaryConstraint: idAFConstraint?
            val v = idVecX()

            // if a single body don't waste time because there aren't any primary constraints
            if (sortedBodies.Num() == 1) {
                body = constraint.body1
                if (body!!.tree === this) {
                    body!!.SetResponseForce(body.numResponses, constraint.J1.SubVec6(row))
                    body.responseIndex!![body.numResponses++] = auxiliaryIndex
                } else {
                    body = constraint.body2
                    body!!.SetResponseForce(body.numResponses, constraint.J2.SubVec6(row))
                    body.responseIndex!![body.numResponses++] = auxiliaryIndex
                }
                return
            }
            v.SetData(6, idVecX.VECX_ALLOCA(6))

            // initialize right hand side to zero
            i = 0
            while (i < sortedBodies.Num()) {
                body = sortedBodies[i]
                primaryConstraint = body.primaryConstraint
                if (primaryConstraint != null) {
                    primaryConstraint.s.Zero()
                    primaryConstraint.fl.isZero = true
                }
                body.s.Zero()
                body.fl.isZero = true
                body.SetResponseForce(body.numResponses, getVec6_zero())
                i++
            }

            // set right hand side for first constrained body
            body = constraint.body1
            if (body!!.tree === this) {
                body!!.InverseWorldSpatialInertiaMultiply(v, constraint.J1[row])
                primaryConstraint = body.primaryConstraint
                if (primaryConstraint != null) {
                    primaryConstraint.J1.times(primaryConstraint.s, v)
                    primaryConstraint.fl.isZero = false
                }
                i = 0
                while (i < body.children.Num()) {
                    child = body.children[i].primaryConstraint!!
                    child.J2.times(child.s, v)
                    child.fl.isZero = false
                    i++
                }
                body.SetResponseForce(body.numResponses, constraint.J1.SubVec6(row))
            }

            // set right hand side for second constrained body
            body = constraint.body2
            if (body != null && body.tree == this) {
                body.InverseWorldSpatialInertiaMultiply(v, constraint.J2[row])
                primaryConstraint = body.primaryConstraint
                if (primaryConstraint != null) {
                    primaryConstraint.J1.MultiplyAdd(primaryConstraint.s, v)
                    primaryConstraint.fl.isZero = false
                }
                i = 0
                while (i < body.children.Num()) {
                    child = body.children[i].primaryConstraint!!
                    child.J2.MultiplyAdd(child.s, v)
                    child.fl.isZero = false
                    i++
                }
                body.SetResponseForce(body.numResponses, constraint.J2.SubVec6(row))
            }

            // solve for primary constraints
            Solve(auxiliaryIndex)
            val useSymmetry = SysCvar.af_useSymmetry.GetBool()

            // store body forces in response to the constraint force
            val force = idVecX()
            i = 0
            while (i < sortedBodies.Num()) {
                body = sortedBodies[i]
                if (useSymmetry && body.maxAuxiliaryIndex < auxiliaryIndex) {
                    i++
                    continue
                }
                val from = body.numResponses * 8
                val to = from + 6
                force.SetData(6, Arrays.copyOfRange(body.response, from, to))

                // add forces of all primary constraints acting on this body
                primaryConstraint = body.primaryConstraint
                primaryConstraint?.J1?.TransposeMultiplyAdd(force, primaryConstraint.lm)
                j = 0
                while (j < body.children.Num()) {
                    child = body.children[j].primaryConstraint!!
                    child.J2.TransposeMultiplyAdd(force, child.lm)
                    j++
                }
                System.arraycopy(force.p, 0, body.response, from, 6)
                body.responseIndex!![body.numResponses++] = auxiliaryIndex
                i++
            }
        }

        /*
         ================
         idAFTree::CalculateForces

         calculate forces on the bodies in the tree
         ================
         */
        fun CalculateForces(timeStep: Float) {
            var i: Int
            var j: Int
            val invStep: Float
            var body: idAFBody?
            var child: idAFConstraint?
            var c: idAFConstraint?
            var primaryConstraint: idAFConstraint?

            // forces on bodies
            i = 0
            while (i < sortedBodies.Num()) {
                body = sortedBodies[i]
                body.totalForce.SubVec6_oSet(0, body.current.externalForce.plus(body.auxForce.SubVec6(0)))
                i++
            }

            // if a single body don't waste time because there aren't any primary constraints
            if (sortedBodies.Num() == 1) {
                return
            }
            invStep = 1.0f / timeStep

            // initialize right hand side
            i = 0
            while (i < sortedBodies.Num()) {
                body = sortedBodies[i]
                body.InverseWorldSpatialInertiaMultiply(body.acceleration, body.totalForce.ToFloatPtr())
                body.acceleration.SubVec6_oPluSet(0, body.current.spatialVelocity.times(invStep))
                primaryConstraint = body.primaryConstraint
                if (primaryConstraint != null) {
                    // b = ( J * acc + c )
                    c = primaryConstraint
                    c.s.set(
                        c.J1.times(c.body1!!.acceleration).plus(c.J2.times(c.body2!!.acceleration))
                            .plus(c.c1.plus(c.c2).times(invStep))
                    )
                    c.fl.isZero = false
                }
                body.s.Zero()
                body.fl.isZero = true
                i++
            }

            // solve for primary constraints
            Solve()

            // calculate forces on bodies after applying primary constraints
            i = 0
            while (i < sortedBodies.Num()) {
                body = sortedBodies[i]

                // add forces of all primary constraints acting on this body
                primaryConstraint = body.primaryConstraint
                if (primaryConstraint != null) {
                    primaryConstraint.J1.TransposeMultiplyAdd(body.totalForce, primaryConstraint.lm)
                }
                j = 0
                while (j < body.children.Num()) {
                    child = body.children[j].primaryConstraint!!
                    child.J2.TransposeMultiplyAdd(body.totalForce, child.lm)
                    j++
                }
                i++
            }
        }

        fun SetMaxSubTreeAuxiliaryIndex() {
            var i: Int
            var j: Int
            var body: idAFBody?
            var child: idAFBody?

            // from the leaves up towards the root
            i = sortedBodies.Num() - 1
            while (i >= 0) {
                body = sortedBodies[i]
                body.maxSubTreeAuxiliaryIndex = body.maxAuxiliaryIndex
                j = 0
                while (j < body.children.Num()) {
                    child = body.children[j]
                    if (child.maxSubTreeAuxiliaryIndex > body.maxSubTreeAuxiliaryIndex) {
                        body.maxSubTreeAuxiliaryIndex = child.maxSubTreeAuxiliaryIndex
                    }
                    j++
                }
                i--
            }
        }

        /*
         ================
         idAFTree::SortBodies

         sort body list to make sure parents come first
         ================
         */
        fun SortBodies() {
            var i: Int
            val body: idAFBody?

            // find the root
            i = 0
            while (i < sortedBodies.Num()) {
                if (null == sortedBodies[i].parent) {
                    break
                }
                i++
            }
            if (i >= sortedBodies.Num()) {
                idGameLocal.Error("Articulated figure tree has no root.")
            }
            body = sortedBodies[i]
            sortedBodies.Clear()
            sortedBodies.Append(body)
            SortBodies_r(sortedBodies, body)
        }

        fun SortBodies_r(sortedList: idList<idAFBody>, body: idAFBody) {
            var i: Int
            i = 0
            while (i < body.children.Num()) {
                sortedList.Append(body.children[i])
                i++
            }
            i = 0
            while (i < body.children.Num()) {
                SortBodies_r(sortedList, body.children[i])
                i++
            }
        }

        fun DebugDraw(color: idVec4) {
            var i: Int
            var body: idAFBody?
            i = 1
            while (i < sortedBodies.Num()) {
                body = sortedBodies[i]
                Game_local.gameRenderWorld!!.DebugArrow(
                    color,
                    body.parent!!.current.worldOrigin,
                    body.current.worldOrigin,
                    1
                )
                i++
            }
        }

        companion object {
            // friend class idPhysics_AF;
        }
    }

    //===============================================================
    //                                                        M
    //  idPhysics_AF                                         MrE
    //                                                        E
    //===============================================================
    class AFPState_s {
        var activateTime // time since last activation
                = 0.0f
        var atRest // >= 0 if articulated figure is at rest
                = 0
        var lastTimeStep // last time step
                = 0.0f
        var noMoveTime // time the articulated figure is hardly moving
                = 0.0f
        val pushVelocity // velocity with which the af is pushed
                : idVec6 = idVec6()

        // FIX: Deep copy method to prevent reference aliasing in SaveState/RestoreState.
        fun set(other: AFPState_s) {
            activateTime = other.activateTime
            atRest = other.atRest
            lastTimeStep = other.lastTimeStep
            noMoveTime = other.noMoveTime
            pushVelocity.set(other.pushVelocity)
        }
    }

    class AFCollision_s {
        var body: idAFBody = idAFBody()
        var trace: trace_s = trace_s()
    }

    class idPhysics_AF : idPhysics_Base() {
        private val auxiliaryConstraints // list with auxiliary constraints
                : idList<idAFConstraint>
        private val bodies // all bodies
                : idList<idAFBody>
        private val collisions // collisions
                : idList<AFCollision_s>
        private val constraints // all frame independent constraints
                : idList<idAFConstraint>
        private val contactBodies // body id for each contact
                : idList<Int>
        private val contactConstraints // contact constraints
                : idList<idAFConstraint_Contact>
        private val frameConstraints // constraints that only live one frame
                : idList<idAFConstraint>
        private val lcp // linear complementarity problem solver
                : idLCP
        private val primaryConstraints // list with primary constraints
                : idList<idAFConstraint>

        // articulated figure
        private val trees // tree structures
                : idList<idAFTree>
        private var angularFriction // default rotational friction
                : Float
        private var bouncyness // default bouncyness
                : Float
        private var changedAF // true when the articulated figure just changed
                : Boolean
        private var comeToRest // if true the figure can come to rest
                : Boolean
        private var contactFriction // default friction with contact surfaces
                : Float
        private var contactFrictionDent // contact friction dives from 1 to this value and goes up again
                : Float
        private var contactFrictionDentEnd // end time of contact friction dent
                : Float
        private var contactFrictionDentScale // dent scale
                : Float
        private var contactFrictionDentStart // start time of contact friction dent
                : Float

        //
        private var contactFrictionScale // contact friction scale
                : Float

        //
        // physics state
        private var current: AFPState_s

        //
        private var enableCollision // if true collision detection is enabled
                : Boolean
        private var forcePushable // if true can be pushed even when bound to a master
                : Boolean
        private var forceTotalMass // force this total mass
                : Float
        private var impulseThreshold // threshold below which impulses are ignored to avoid continuous activation
                : Float
        private var jointFrictionDent // joint friction dives from 1 to this value and goes up again
                : Float
        private var jointFrictionDentEnd // end time of joint friction dent
                : Float
        private var jointFrictionDentScale // dent scale
                : Float
        private var jointFrictionDentStart // start time of joint friction dent
                : Float

        //
        private var jointFrictionScale // joint friction scale
                : Float

        //
        // properties
        private var linearFriction // default translational friction
                : Float
        private var linearTime // if true use the linear time algorithm
                : Boolean

        //
        private var masterBody // master body
                : idAFBody?
        private var maxMoveTime // if > 0 the simulation is always suspeded after running this many seconds
                : Float
        private var minMoveTime // if > 0 the simulation is never suspended before running this many seconds
                : Float
        private var noImpact // if true do not activate when another object collides
                : Boolean
        private var noMoveRotation // maximum rotation considered no movement
                : Float

        //
        //
        private var noMoveTime // suspend simulation if hardly any movement for this many seconds
                : Float

        // ~idPhysics_AF();
        private var noMoveTranslation // maximum translation considered no movement
                : Float
        private var saved: AFPState_s
        private var selfCollision // if true the self collision is allowed
                : Boolean
        private val suspendAcceleration // simulation may not be suspended if a body has more acceleration
                : idVec2 = idVec2()

        //
        private val suspendVelocity // simulation may not be suspended if a body has more velocity
                : idVec2 = idVec2()

        //
        private var timeScale // the time is scaled with this value for slow motion effects
                : Float
        private var timeScaleRampEnd // end of time scale change
                : Float
        private var timeScaleRampStart // start of time scale change
                : Float
        private var totalMass // total mass of articulated figure
                : Float
        private var worldConstraintsLocked // if true world constraints cannot be moved
                : Boolean

        override fun Save(saveFile: idSaveGame) {
            var i: Int

            // the articulated figure structure is handled by the owner
            idPhysics_AF_SavePState(saveFile, current)
            idPhysics_AF_SavePState(saveFile, saved)
            saveFile.WriteInt(bodies.Num())
            i = 0
            while (i < bodies.Num()) {
                bodies[i].Save(saveFile)
                i++
            }
            if (masterBody != null) {
                saveFile.WriteBool(true)
                masterBody!!.Save(saveFile)
            } else {
                saveFile.WriteBool(false)
            }
            saveFile.WriteInt(constraints.Num())
            i = 0
            while (i < constraints.Num()) {
                constraints[i].Save(saveFile)
                i++
            }
            saveFile.WriteBool(changedAF)
            saveFile.WriteFloat(linearFriction)
            saveFile.WriteFloat(angularFriction)
            saveFile.WriteFloat(contactFriction)
            saveFile.WriteFloat(bouncyness)
            saveFile.WriteFloat(totalMass)
            saveFile.WriteFloat(forceTotalMass)
            saveFile.WriteVec2(suspendVelocity)
            saveFile.WriteVec2(suspendAcceleration)
            saveFile.WriteFloat(noMoveTime)
            saveFile.WriteFloat(noMoveTranslation)
            saveFile.WriteFloat(noMoveRotation)
            saveFile.WriteFloat(minMoveTime)
            saveFile.WriteFloat(maxMoveTime)
            saveFile.WriteFloat(impulseThreshold)
            saveFile.WriteFloat(timeScale)
            saveFile.WriteFloat(timeScaleRampStart)
            saveFile.WriteFloat(timeScaleRampEnd)
            saveFile.WriteFloat(jointFrictionScale)
            saveFile.WriteFloat(jointFrictionDent)
            saveFile.WriteFloat(jointFrictionDentStart)
            saveFile.WriteFloat(jointFrictionDentEnd)
            saveFile.WriteFloat(jointFrictionDentScale)
            saveFile.WriteFloat(contactFrictionScale)
            saveFile.WriteFloat(contactFrictionDent)
            saveFile.WriteFloat(contactFrictionDentStart)
            saveFile.WriteFloat(contactFrictionDentEnd)
            saveFile.WriteFloat(contactFrictionDentScale)
            saveFile.WriteBool(enableCollision)
            saveFile.WriteBool(selfCollision)
            saveFile.WriteBool(comeToRest)
            saveFile.WriteBool(linearTime)
            saveFile.WriteBool(noImpact)
            saveFile.WriteBool(worldConstraintsLocked)
            saveFile.WriteBool(forcePushable)
        }

        override fun Restore(saveFile: idRestoreGame) {
            var i: Int
            val num = CInt()
            val hasMaster = CBool(false)

            // the articulated figure structure should have already been restored
            idPhysics_AF_RestorePState(saveFile, current)
            idPhysics_AF_RestorePState(saveFile, saved)
            saveFile.ReadInt(num)
            assert(num._val == bodies.Num())
            i = 0
            while (i < bodies.Num()) {
                bodies[i].Restore(saveFile)
                i++
            }
            saveFile.ReadBool(hasMaster)
            if (hasMaster._val) {
                masterBody = idAFBody()
                masterBody!!.Restore(saveFile)
            }
            saveFile.ReadInt(num)
            assert(num._val == constraints.Num())
            i = 0
            while (i < constraints.Num()) {
                constraints[i].Restore(saveFile)
                i++
            }
            changedAF = saveFile.ReadBool()
            linearFriction = saveFile.ReadFloat()
            angularFriction = saveFile.ReadFloat()
            contactFriction = saveFile.ReadFloat()
            bouncyness = saveFile.ReadFloat()
            totalMass = saveFile.ReadFloat()
            forceTotalMass = saveFile.ReadFloat()
            saveFile.ReadVec2(suspendVelocity)
            saveFile.ReadVec2(suspendAcceleration)
            noMoveTime = saveFile.ReadFloat()
            noMoveTranslation = saveFile.ReadFloat()
            noMoveRotation = saveFile.ReadFloat()
            minMoveTime = saveFile.ReadFloat()
            maxMoveTime = saveFile.ReadFloat()
            impulseThreshold = saveFile.ReadFloat()
            timeScale = saveFile.ReadFloat()
            timeScaleRampStart = saveFile.ReadFloat()
            timeScaleRampEnd = saveFile.ReadFloat()
            jointFrictionScale = saveFile.ReadFloat()
            jointFrictionDent = saveFile.ReadFloat()
            jointFrictionDentStart = saveFile.ReadFloat()
            jointFrictionDentEnd = saveFile.ReadFloat()
            jointFrictionDentScale = saveFile.ReadFloat()
            contactFrictionScale = saveFile.ReadFloat()
            contactFrictionDent = saveFile.ReadFloat()
            contactFrictionDentStart = saveFile.ReadFloat()
            contactFrictionDentEnd = saveFile.ReadFloat()
            contactFrictionDentScale = saveFile.ReadFloat()
            enableCollision = saveFile.ReadBool()
            selfCollision = saveFile.ReadBool()
            comeToRest = saveFile.ReadBool()
            linearTime = saveFile.ReadBool()
            noImpact = saveFile.ReadBool()
            worldConstraintsLocked = saveFile.ReadBool()
            forcePushable = saveFile.ReadBool()
            changedAF = true
            UpdateClipModels()
        }

        /*
         ================
         idPhysics_AF::AddBody

         bodies get an id in the order they are added starting at zero
         as such the first body added will get id zero
         ================
         */
        // initialisation
        fun AddBody(body: idAFBody): Int {    // returns body id
            var id = 0
            if (null == body.clipModel) {
                idGameLocal.Error("idPhysics_AF::AddBody: body '%s' has no clip model.", body.name)
            }
            if (bodies.Find(body) != null) {
                idGameLocal.Error("idPhysics_AF::AddBody: body '%s' added twice.", body.name)
            }
            if (GetBody(body.name.toString()) != null) {
                idGameLocal.Error(
                    "idPhysics_AF::AddBody: a body with the name '%s' already exists.",
                    body.name
                )
            }
            id = bodies.Num()
            body.clipModel!!.SetId(id)
            if (body.linearFriction < 0.0f) {
                body.linearFriction = linearFriction
                body.angularFriction = angularFriction
                body.contactFriction = contactFriction
            }
            if (body.bouncyness < 0.0f) {
                body.bouncyness = bouncyness
            }
            if (!body.fl.clipMaskSet) {
                body.clipMask = clipMask
            }
            bodies.Append(body)
            changedAF = true
            return id
        }

        fun AddConstraint(constraint: idAFConstraint) {
            if (constraints.Find(constraint) != null) {
                idGameLocal.Error(
                    "idPhysics_AF::AddConstraint: constraint '%s' added twice.",
                    constraint.name
                )
            }
            if (GetConstraint(constraint.name.toString()) != null) {
                idGameLocal.Error(
                    "idPhysics_AF::AddConstraint: a constraint with the name '%s' already exists.",
                    constraint.name
                )
            }
            if (null == constraint.body1) {
                idGameLocal.Error(
                    "idPhysics_AF::AddConstraint: body1 == NULL on constraint '%s'.",
                    constraint.name
                )
            }
            if (null == bodies.Find(constraint.body1!!)) {
                idGameLocal.Error(
                    "idPhysics_AF::AddConstraint: body1 of constraint '%s' is not part of the articulated figure.",
                    constraint.name
                )
            }
            if (constraint.body2 != null && null == bodies.Find(constraint.body2!!)) {
                idGameLocal.Error(
                    "idPhysics_AF::AddConstraint: body2 of constraint '%s' is not part of the articulated figure.",
                    constraint.name
                )
            }
            if (constraint.body1 == constraint.body2) {
                idGameLocal.Error(
                    "idPhysics_AF::AddConstraint: body1 and body2 of constraint '%s' are the same.",
                    constraint.name
                )
            }
            constraints.Append(constraint)
            constraint.physics = this
            changedAF = true
        }

        fun AddFrameConstraint(constraint: idAFConstraint) {
            frameConstraints.Append(constraint)
            constraint.physics = this
        }

        // force a body to have a certain id
        fun ForceBodyId(body: idAFBody, newId: Int) {
            val id: Int
            id = bodies.FindIndex(body)
            if (id == -1) {
                idGameLocal.Error(
                    "ForceBodyId: body '%s' is not part of the articulated figure.\n",
                    body!!.name
                )
            }
            if (id != newId) {
                val b = bodies[newId]
                bodies[newId] = bodies[id]
                bodies[id] = b
                changedAF = true
            }
        }

        // get body or constraint id
        fun GetBodyId(body: idAFBody): Int {
            val id: Int
            id = bodies.FindIndex(body)
            if (id == -1 && body != null) {
                idGameLocal.Error("GetBodyId: body '%s' is not part of the articulated figure.\n", body.name)
            }
            return id
        }

        fun GetBodyId(bodyName: String): Int {
            var i: Int
            i = 0
            while (i < bodies.Num()) {
                if (0 == bodies[i].name.Icmp(bodyName)) {
                    return i
                }
                i++
            }
            idGameLocal.Error(
                "GetBodyId: no body with the name '%s' is not part of the articulated figure.\n",
                bodyName
            )
            return 0
        }

        fun GetConstraintId(constraint: idAFConstraint): Int {
            val id: Int
            id = constraints.FindIndex(constraint)
            if (id == -1 && constraint != null) {
                idGameLocal.Error(
                    "GetConstraintId: constraint '%s' is not part of the articulated figure.\n",
                    constraint.name
                )
            }
            return id
        }

        fun GetConstraintId(constraintName: String): Int {
            var i: Int
            i = 0
            while (i < constraints.Num()) {
                if (constraints[i].name.Icmp(constraintName) == 0) {
                    return i
                }
                i++
            }
            idGameLocal.Error(
                "GetConstraintId: no constraint with the name '%s' is not part of the articulated figure.\n",
                constraintName
            )
            return 0
        }

        // number of bodies and constraints
        fun GetNumBodies(): Int {
            return bodies.Num()
        }

        fun GetNumConstraints(): Int {
            return constraints.Num()
        }

        // retrieve body or constraint
        fun GetBody(bodyName: String): idAFBody? {
            var i: Int
            i = 0
            while (i < bodies.Num()) {
                if (0 == bodies[i].name.Icmp(bodyName)) {
                    return bodies[i]
                }
                i++
            }
            return null
        }

        fun GetBody(bodyName: idStr): idAFBody? {
            return GetBody(bodyName.toString())
        }

        fun GetBody(id: Int): idAFBody? {
            if (id < 0 || id >= bodies.Num()) {
                idGameLocal.Error("GetBody: no body with id %d exists\n", id)
                return null
            }
            return bodies[id]
        }

        fun GetMasterBody(): idAFBody? {
            return masterBody
        }

        fun GetConstraint(constraintName: String): idAFConstraint? {
            var i: Int
            i = 0
            while (i < constraints.Num()) {
                if (constraints[i].name.Icmp(constraintName) == 0) {
                    return constraints[i]
                }
                i++
            }
            return null
        }

        // set joint friction dent
        fun GetConstraint(id: Int): idAFConstraint? {
            if (id < 0 || id >= constraints.Num()) {
                idGameLocal.Error("GetConstraint: no constraint with id %d exists\n", id)
                return null
            }
            return constraints[id]
        }

        // delete body or constraint
        fun DeleteBody(bodyName: String) {
            var i: Int

            // find the body with the given name
            i = 0
            while (i < bodies.Num()) {
                if (0 == bodies[i].name.Icmp(bodyName)) {
                    break
                }
                i++
            }
            if (i >= bodies.Num()) {
                Game_local.gameLocal.Warning(
                    "DeleteBody: no body found in the articulated figure with the name '%s' for entity '%s' type '%s'.",
                    bodyName, self!!.name, self!!.GetType().name
                )
                return
            }
            DeleteBody(i)
        }

        fun DeleteBody(id: Int) {
            var j: Int
            if (id < 0 || id > bodies.Num()) {
                idGameLocal.Error("DeleteBody: no body with id %d.", id)
                return
            }

            // remove any constraints attached to this body
            j = 0
            while (j < constraints.Num()) {
                if (constraints[j].body1 == bodies[id] || constraints[j].body2 == bodies[id]) {
//			delete constraints[j];
                    constraints.RemoveIndex(j)
                    j--
                }
                j++
            }

            // remove the body
//	delete bodies[id];
            bodies.RemoveIndex(id)

            // set new body ids
            j = 0
            while (j < bodies.Num()) {
                bodies[j].clipModel!!.SetId(j)
                j++
            }
            changedAF = true
        }

        fun DeleteConstraint(constraintName: String) {
            var i: Int

            // find the constraint with the given name
            i = 0
            while (i < constraints.Num()) {
                if (constraints[i].name.Icmp(constraintName) == 0) {
                    break
                }
                i++
            }
            if (i >= constraints.Num()) {
                Game_local.gameLocal.Warning(
                    "DeleteConstraint: no constriant found in the articulated figure with the name '%s' for entity '%s' type '%s'.",
                    constraintName, self!!.name, self!!.GetType().name
                )
                return
            }
            DeleteConstraint(i)
        }

        fun DeleteConstraint(id: Int) {
            if (id < 0 || id >= constraints.Num()) {
                idGameLocal.Error("DeleteConstraint: no constraint with id %d.", id)
                return
            }

            // remove the constraint
//	delete constraints[id];
            constraints.RemoveIndex(id)
            changedAF = true
        }

        // get all the contact constraints acting on the body
        fun GetBodyContactConstraints(id: Int, contacts: Array<idAFConstraint_Contact?>, maxContacts: Int): Int {
            var i: Int
            var numContacts: Int
            val body: idAFBody?
            var contact: idAFConstraint_Contact
            if (id < 0 || id >= bodies.Num() || maxContacts <= 0) {
                return 0
            }
            numContacts = 0
            body = bodies[id]
            i = 0
            while (i < contactConstraints.Num()) {
                contact = contactConstraints[i]
                if (contact.body1 === body || contact.body2 === body) {
                    contacts[numContacts++] = contact
                    if (numContacts >= maxContacts) {
                        return numContacts
                    }
                }
                i++
            }
            return numContacts
        }

        // set the default friction for bodies
        fun SetDefaultFriction(linear: Float, angular: Float, contact: Float) {
            if (linear < 0.0f || linear > 1.0f || angular < 0.0f || angular > 1.0f || contact < 0.0f || contact > 1.0f) {
                return
            }
            linearFriction = linear
            angularFriction = angular
            contactFriction = contact
        }

        // suspend settings
        fun SetSuspendSpeed(velocity: idVec2, acceleration: idVec2) {
            suspendVelocity.set(velocity)
            suspendAcceleration.set(acceleration)
        }

        // set the time and tolerances used to determine if the simulation can be suspended when the figure hardly moves for a while
        fun SetSuspendTolerance(noMoveTime: Float, translationTolerance: Float, rotationTolerance: Float) {
            this.noMoveTime = noMoveTime
            noMoveTranslation = translationTolerance
            noMoveRotation = rotationTolerance
        }

        // set minimum and maximum simulation time in seconds
        fun SetSuspendTime(minTime: Float, maxTime: Float) {
            minMoveTime = minTime
            maxMoveTime = maxTime
        }

        // set the time scale value
        fun SetTimeScale(ts: Float) {
            timeScale = ts
        }

        // set time scale ramp
        fun SetTimeScaleRamp(start: Float, end: Float) {
            timeScaleRampStart = start
            timeScaleRampEnd = end
        }

        // set the joint friction scale
        fun SetJointFrictionScale(scale: Float) {
            jointFrictionScale = scale
        }

        fun SetJointFrictionDent(dent: Float, start: Float, end: Float) {
            jointFrictionDent = dent
            jointFrictionDentStart = start
            jointFrictionDentEnd = end
        }

        // get the current joint friction scale
        fun GetJointFrictionScale(): Float {
            if (jointFrictionDentScale > 0.0f) {
                return jointFrictionDentScale
            } else if (jointFrictionScale > 0.0f) {
                return jointFrictionScale
            } else if (SysCvar.af_jointFrictionScale.GetFloat() > 0.0f) {
                return SysCvar.af_jointFrictionScale.GetFloat()
            }
            return 1.0f
        }

        // set the contact friction scale
        fun SetContactFrictionScale(scale: Float) {
            contactFrictionScale = scale
        }

        // set contact friction dent
        fun SetContactFrictionDent(dent: Float, start: Float, end: Float) {
            contactFrictionDent = dent
            contactFrictionDentStart = start
            contactFrictionDentEnd = end
        }

        // get the current contact friction scale
        fun GetContactFrictionScale(): Float {
            if (contactFrictionDentScale > 0.0f) {
                return contactFrictionDentScale
            } else if (contactFrictionScale > 0.0f) {
                return contactFrictionScale
            } else if (SysCvar.af_contactFrictionScale.GetFloat() > 0.0f) {
                return SysCvar.af_contactFrictionScale.GetFloat()
            }
            return 1.0f
        }

        // enable or disable collision detection
        fun SetCollision(enable: Boolean) {
            enableCollision = enable
        }

        // enable or disable self collision
        fun SetSelfCollision(enable: Boolean) {
            selfCollision = enable
        }

        // enable or disable coming to a dead stop
        fun SetComeToRest(enable: Boolean) {
            comeToRest = enable
        }

        // call when structure of articulated figure changes
        fun SetChanged() {
            changedAF = true
        }

        // enable/disable activation by impact
        fun EnableImpact() {
            noImpact = false
        }

        fun DisableImpact() {
            noImpact = true
        }

        // lock of unlock the world constraints
        fun LockWorldConstraints(lock: Boolean) {
            worldConstraintsLocked = lock
        }

        // set force pushable
        fun SetForcePushable(enable: Boolean) {
            forcePushable = enable
        }

        // update the clip model positions
        fun UpdateClipModels() {
            var i: Int
            var body: idAFBody?
            i = 0
            while (i < bodies.Num()) {
                body = bodies[i]
                body.clipModel!!.Link(
                    Game_local.gameLocal.clip,
                    self,
                    body.clipModel!!.GetId(),
                    body.current.worldOrigin,
                    body.current.worldAxis
                )
                i++
            }
        }

        // common physics interface
        override fun SetClipModel(model: idClipModel?, density: Float, id: Int /*= 0*/, freeOld: Boolean /*= true*/) {}
        override fun GetClipModel(id: Int /*= 0*/): idClipModel? {
            return if (id >= 0 && id < bodies.Num()) {
                bodies[id].GetClipModel()
            } else null
        }

        override fun GetNumClipModels(): Int {
            return bodies.Num()
        }

        override fun SetMass(mass: Float, id: Int /*= -1*/) {
            if (id >= 0 && id < bodies.Num()) {
            } else {
                forceTotalMass = mass
            }
            SetChanged()
        }

        override fun GetMass(id: Int /*= -1*/): Float {
            return if (id >= 0 && id < bodies.Num()) {
                bodies[id].mass
            } else totalMass
        }

        override fun SetContents(contents: Int, id: Int /*= -1*/) {
            var i: Int
            if (id >= 0 && id < bodies.Num()) {
                bodies[id].GetClipModel()!!.SetContents(contents)
            } else {
                i = 0
                while (i < bodies.Num()) {
                    bodies[i].GetClipModel()!!.SetContents(contents)
                    i++
                }
            }
        }

        override fun GetContents(id: Int /*= -1*/): Int {
            var i: Int
            var contents: Int
            return if (id >= 0 && id < bodies.Num()) {
                bodies[id].GetClipModel()!!.GetContents()
            } else {
                contents = 0
                i = 0
                while (i < bodies.Num()) {
                    contents = contents or bodies[i].GetClipModel()!!.GetContents()
                    i++
                }
                contents
            }
        }

        override fun GetBounds(id: Int /*= -1*/): idBounds {
            var i: Int
            return if (id >= 0 && id < bodies.Num()) {
                bodies[id].GetClipModel()!!.GetBounds()
            } else if (0 == bodies.Num()) {
                relBounds.Zero()
                relBounds
            } else {
                relBounds.set(bodies[0].GetClipModel()!!.GetBounds())
                i = 1
                while (i < bodies.Num()) {
                    val bounds = idBounds()
                    val origin = idVec3(
                        bodies[i].GetWorldOrigin().minus(bodies[0].GetWorldOrigin())
                            .times(bodies[0].GetWorldAxis().Transpose())
                    )
                    val axis = idMat3(bodies[i].GetWorldAxis().times(bodies[0].GetWorldAxis().Transpose()))
                    bounds.FromTransformedBounds(bodies[i].GetClipModel()!!.GetBounds(), origin, axis)
                    relBounds.timesAssign(bounds)
                    i++
                }
                relBounds
            }
        }

        override fun GetAbsBounds(id: Int /*= -1*/): idBounds {
            var i: Int
            return if (id >= 0 && id < bodies.Num()) {
                bodies[id].GetClipModel()!!.GetAbsBounds()
            } else if (0 == bodies.Num()) {
                absBounds.Zero()
                absBounds
            } else {
                absBounds.set(bodies[0].GetClipModel()!!.GetAbsBounds())
                i = 1
                while (i < bodies.Num()) {
                    absBounds.timesAssign(bodies[i].GetClipModel()!!.GetAbsBounds())
                    i++
                }
                absBounds
            }
        }

        override fun Evaluate(timeStepMSec: Int, endTimeMSec: Int): Boolean {
            val timeStep: Float
            timeStep =
                if (timeScaleRampStart < MS2SEC(endTimeMSec.toFloat()) && timeScaleRampEnd > MS2SEC(
                        endTimeMSec.toFloat()
                    )
                ) {
                    MS2SEC(timeStepMSec.toFloat()) * (MS2SEC(endTimeMSec.toFloat()) - timeScaleRampStart) / (timeScaleRampEnd - timeScaleRampStart)
                } else if (SysCvar.af_timeScale.GetFloat() != 1.0f) {
                    MS2SEC(timeStepMSec.toFloat()) * SysCvar.af_timeScale.GetFloat()
                } else {
                    MS2SEC(timeStepMSec.toFloat()) * timeScale
                }
            current.lastTimeStep = timeStep

            // if the articulated figure changed
            if (changedAF || linearTime != SysCvar.af_useLinearTime.GetBool()) {
                BuildTrees()
                changedAF = false
                linearTime = SysCvar.af_useLinearTime.GetBool()
            }

            // get the new master position
            if (masterBody != null) {
                val masterOrigin = idVec3()
                val masterAxis = idMat3()
                self!!.GetMasterPosition(masterOrigin, masterAxis)
                // FIX: Was !== (referential), C++ uses != (structural equality) to check if master actually moved
                if (current.atRest >= 0 && (masterBody!!.current.worldOrigin != masterOrigin || masterBody!!.current.worldAxis != masterAxis)) {
                    Activate()
                }
                masterBody!!.current.worldOrigin.set(masterOrigin)
                masterBody!!.current.worldAxis.set(masterAxis)
            }

            // if the simulation is suspended because the figure is at rest
            if (current.atRest >= 0 || timeStep <= 0.0f) {
                DebugDraw()
                return false
            }

            // move the af velocity into the frame of a pusher
            AddPushVelocity(current.pushVelocity.unaryMinus())
            if (AF_TIMINGS) {
                timer_total.Start()
            }
            if (AF_TIMINGS) {
                timer_collision.Start()
            }

            // evaluate contacts
            EvaluateContacts()

            // setup contact constraints
            SetupContactConstraints()
            if (AF_TIMINGS) {
                timer_collision.Stop()
            }

            // evaluate constraint equations
            EvaluateConstraints(timeStep)

            // apply friction
            ApplyFriction(timeStep, endTimeMSec.toFloat())

            // add frame constraints
            AddFrameConstraints()
            var i: Int
            var numPrimary = 0
            var numAuxiliary = 0
            if (AF_TIMINGS) {
                i = 0
                while (i < primaryConstraints.Num()) {
                    numPrimary += primaryConstraints[i].J1.GetNumRows()
                    i++
                }
                i = 0
                while (i < auxiliaryConstraints.Num()) {
                    numAuxiliary += auxiliaryConstraints[i].J1.GetNumRows()
                    i++
                }
                timer_pc.Start()
            }

            // factor matrices for primary constraints
            PrimaryFactor()

            // calculate forces on bodies after applying primary constraints
            PrimaryForces(timeStep)
            if (AF_TIMINGS) {
                timer_pc.Stop()
                timer_ac.Start()
            }

            // calculate and apply auxiliary constraint forces
            AuxiliaryForces(timeStep)
            if (AF_TIMINGS) {
                timer_ac.Stop()
            }

            // evolve current state to next state
            Evolve(timeStep)

            // debug graphics
            DebugDraw()

            // clear external forces on all bodies
            ClearExternalForce()

            // apply contact force to other entities
            ApplyContactForces()

            // remove all frame constraints
            RemoveFrameConstraints()
            if (AF_TIMINGS) {
                timer_collision.Start()
            }

            // check for collisions between current and next state
            CheckForCollisions(timeStep)
            if (AF_TIMINGS) {
                timer_collision.Stop()
            }

            // swap the current and next state
            SwapStates()

            // make sure all clip models are disabled in case they were enabled for self collision
            if (selfCollision && !SysCvar.af_skipSelfCollision.GetBool()) {
                DisableClip()
            }

            // apply collision impulses
            if (ApplyCollisions(timeStep)) {
                current.atRest = Game_local.gameLocal.time
                comeToRest = true
            }

            // test if the simulation can be suspended because the whole figure is at rest
            if (comeToRest && TestIfAtRest(timeStep)) {
                Rest()
            } else {
                ActivateContactEntities()
            }

            // add gravitational force
            AddGravity()

            // move the af velocity back into the world frame
            AddPushVelocity(current.pushVelocity)
            current.pushVelocity.Zero()
            if (IsOutsideWorld()) {
                Game_local.gameLocal.Warning(
                    "articulated figure moved outside world bounds for entity '%s' type '%s' at (%s)",
                    self!!.name, self!!.GetType().name, bodies[0].current.worldOrigin.ToString(0)
                )
                Rest()
            }
            if (AF_TIMINGS) {
                timer_total.Stop()
                if (SysCvar.af_showTimings.GetInteger() == 1) {
                    Game_local.gameLocal.Printf(
                        "%12s: t %1.4f pc %2d, %1.4f ac %2d %1.4f lcp %1.4f cd %1.4f\n",
                        self!!.name,
                        timer_total.Milliseconds(),
                        numPrimary, timer_pc.Milliseconds(),
                        numAuxiliary, timer_ac.Milliseconds() - timer_lcp.Milliseconds(),
                        timer_lcp.Milliseconds(), timer_collision.Milliseconds()
                    )
                } else if (SysCvar.af_showTimings.GetInteger() == 2) {
                    numArticulatedFigures++
                    if (endTimeMSec > lastTimerReset) {
                        Game_local.gameLocal.Printf(
                            "af %d: t %1.4f pc %2d, %1.4f ac %2d %1.4f lcp %1.4f cd %1.4f\n",
                            numArticulatedFigures,
                            timer_total.Milliseconds(),
                            numPrimary, timer_pc.Milliseconds(),
                            numAuxiliary, timer_ac.Milliseconds() - timer_lcp.Milliseconds(),
                            timer_lcp.Milliseconds(), timer_collision.Milliseconds()
                        )
                    }
                }
                if (endTimeMSec > lastTimerReset) {
                    lastTimerReset = endTimeMSec
                    numArticulatedFigures = 0
                    timer_total.Clear()
                    timer_pc.Clear()
                    timer_ac.Clear()
                    timer_collision.Clear()
                    timer_lcp.Clear()
                }
            }
            return true
        }

        override fun UpdateTime(endTimeMSec: Int) {}
        override fun GetTime(): Int {
            return Game_local.gameLocal.time
        }

        override fun GetImpactInfo(id: Int, point: idVec3): impactInfo_s {
            val info = impactInfo_s()
            if (id < 0 || id >= bodies.Num()) {
                return info
            }
            info.invMass = 1.0f / bodies[id].mass
            info.invInertiaTensor.set(
                bodies[id].current.worldAxis.Transpose()
                    .times(bodies[id].inverseInertiaTensor)
                    .times(bodies[id].current.worldAxis)
            )
            info.position.set(point.minus(bodies[id].current.worldOrigin))
            info.velocity.set(
                bodies[id].current.spatialVelocity.SubVec3(0)
                    .plus(bodies[id].current.spatialVelocity.SubVec3(1).Cross(info.position))
            )
            return info
        }

        override fun ApplyImpulse(id: Int, point: idVec3, impulse: idVec3) {
            if (id < 0 || id >= bodies.Num()) {
                return
            }
            if (noImpact || impulse.LengthSqr() < Square(impulseThreshold)) {
                return
            }
            val invWorldInertiaTensor = bodies[id].current.worldAxis.Transpose()
                .times(bodies[id].inverseInertiaTensor.times(bodies[id].current.worldAxis))
            bodies[id].current.spatialVelocity.SubVec3_oPluSet(0, impulse.times(bodies[id].invMass))
            bodies[id].current.spatialVelocity.SubVec3_oPluSet(
                1,
                invWorldInertiaTensor.times(point.minus(bodies[id].current.worldOrigin).Cross(impulse))
            )
            Activate()
        }

        override fun AddForce(id: Int, point: idVec3, force: idVec3) {
            if (noImpact) {
                return
            }
            if (id < 0 || id >= bodies.Num()) {
                return
            }
            bodies[id].current.externalForce.SubVec3_oPluSet(0, force)
            bodies[id].current.externalForce.SubVec3_oPluSet(
                1,
                point.minus(bodies[id].current.worldOrigin).Cross(force)
            )
            Activate()
        }

        override fun IsAtRest(): Boolean {
            return current.atRest >= 0
        }

        override fun GetRestStartTime(): Int {
            return current.atRest
        }

        override fun Activate() {
            // if the articulated figure was at rest
            if (current.atRest >= 0) {
                // normally gravity is added at the end of a simulation frame
                // if the figure was at rest add gravity here so it is applied this simulation frame
                AddGravity()
                // reset the active time for the max move time
                current.activateTime = 0.0f
            }
            current.atRest = -1
            current.noMoveTime = 0.0f
            self!!.BecomeActive(TH_PHYSICS)
        }

        /*
         ================
         idPhysics_AF::PutToRest

         put to rest untill something collides with this physics object
         ================
         */
        override fun PutToRest() {
            Rest()
        }

        override fun IsPushable(): Boolean {
            return !noImpact && (masterBody == null || forcePushable)
        }

        override fun SaveState() {
            var i: Int
            // FIX: C++ struct assignment does value copy; Kotlin = creates reference alias
            saved.set(current)
            i = 0
            while (i < bodies.Num()) {

//                memcpy(bodies.oGet(i).saved, bodies.oGet(i).current, sizeof(AFBodyPState_t));
                bodies[i].saved.oSet(bodies[i].current)
                i++
            }
        }

        override fun RestoreState() {
            var i: Int
            // FIX: C++ struct assignment does value copy; Kotlin = creates reference alias
            current.set(saved)
            i = 0
            while (i < bodies.Num()) {
                bodies[i].current.oSet(bodies[i].saved)
                i++
            }
            EvaluateContacts()
        }

        override fun SetOrigin(newOrigin: idVec3, id: Int /*= -1*/) {
            if (masterBody != null) {
                Translate(masterBody!!.current.worldOrigin + masterBody!!.current.worldAxis * newOrigin - bodies[0].current.worldOrigin)
            } else {
                Translate(newOrigin - bodies[0].current.worldOrigin)
            }
        }

        override fun SetAxis(newAxis: idMat3, id: Int /*= -1*/) {
            val axis: idMat3
            val rotation: idRotation
            axis = if (masterBody != null) {
                bodies[0].current.worldAxis.Transpose().times(newAxis.times(masterBody!!.current.worldAxis))
            } else {
                bodies[0].current.worldAxis.Transpose().times(newAxis)
            }
            rotation = axis.ToRotation()
            rotation.SetOrigin(bodies[0].current.worldOrigin)
            Rotate(rotation)
        }

        override fun Translate(translation: idVec3, id: Int /*= -1*/) {
            var i: Int
            var body: idAFBody?
            if (!worldConstraintsLocked) {
                // translate constraints attached to the world
                i = 0
                while (i < constraints.Num()) {
                    constraints[i].Translate(translation)
                    i++
                }
            }

            // translate all the bodies
            i = 0
            while (i < bodies.Num()) {
                body = bodies[i]
                body.current.worldOrigin.plusAssign(translation)
                i++
            }
            Activate()
            UpdateClipModels()
        }

        override fun Rotate(rotation: idRotation, id: Int /*= -1*/) {
            var i: Int
            var body: idAFBody?
            if (!worldConstraintsLocked) {
                // rotate constraints attached to the world
                i = 0
                while (i < constraints.Num()) {
                    constraints[i].Rotate(rotation)
                    i++
                }
            }

            // rotate all the bodies
            i = 0
            while (i < bodies.Num()) {
                body = bodies[i]
                idMat3(body.GetWorldAxis())
                body.current.worldOrigin.timesAssign(rotation)
                body.current.worldAxis.timesAssign(rotation.ToMat3())
                i++
            }
            Activate()
            UpdateClipModels()
        }

        override fun GetOrigin(id: Int /*= 0*/): idVec3 {
            return if (id < 0 || id >= bodies.Num()) {
                getVec3Origin()
            } else {
                bodies[id].current.worldOrigin
            }
        }

        override fun GetAxis(id: Int /*= 0*/): idMat3 {
            return if (id < 0 || id >= bodies.Num()) {
                idMat3.getMat3_identity()
            } else {
                bodies[id].current.worldAxis
            }
        }

        override fun SetLinearVelocity(newLinearVelocity: idVec3, id: Int /*= 0*/) {
            if (id < 0 || id >= bodies.Num()) {
                return
            }
            bodies[id].current.spatialVelocity.SubVec3_oSet(0, newLinearVelocity)
            Activate()
        }

        override fun SetAngularVelocity(newAngularVelocity: idVec3, id: Int /*= 0*/) {
            if (id < 0 || id >= bodies.Num()) {
                return
            }
            bodies[id].current.spatialVelocity.SubVec3_oSet(1, newAngularVelocity)
            Activate()
        }

        override fun GetLinearVelocity(id: Int /*= 0*/): idVec3 {
            return if (id < 0 || id >= bodies.Num()) {
                getVec3Origin()
            } else {
                bodies[id].current.spatialVelocity.SubVec3(0)
            }
        }

        override fun GetAngularVelocity(id: Int /*= 0*/): idVec3 {
            return if (id < 0 || id >= bodies.Num()) {
                getVec3Origin()
            } else {
                bodies[id].current.spatialVelocity.SubVec3(1)
            }
        }

        override fun ClipTranslation(results: trace_s, translation: idVec3, model: idClipModel?) {
            var i: Int
            var body: idAFBody?
            val bodyResults = trace_s()
            results.fraction = 1.0f
            i = 0
            while (i < bodies.Num()) {
                body = bodies[i]
                if (body.clipModel!!.IsTraceModel()) {
                    if (model != null) {
                        Game_local.gameLocal.clip.TranslationModel(
                            bodyResults, body.current.worldOrigin, body.current.worldOrigin.plus(translation),
                            body.clipModel, body.current.worldAxis, body.clipMask,
                            model.Handle(), model.GetOrigin(), model.GetAxis()
                        )
                    } else {
                        Game_local.gameLocal.clip.Translation(
                            bodyResults, body.current.worldOrigin, body.current.worldOrigin.plus(translation),
                            body.clipModel, body.current.worldAxis, body.clipMask, self
                        )
                    }
                    if (bodyResults.fraction < results.fraction) {
                        results.set(bodyResults)
                    }
                }
                i++
            }
            results.endpos.set(bodies[0].current.worldOrigin.plus(translation.times(results.fraction)))
            results.endAxis.set(bodies[0].current.worldAxis)
        }

        override fun ClipRotation(results: trace_s, rotation: idRotation, model: idClipModel?) {
            var results = results
            var i: Int
            var body: idAFBody?
            val bodyResults = trace_s()
            val partialRotation: idRotation
            results.fraction = 1.0f
            i = 0
            while (i < bodies.Num()) {
                body = bodies[i]
                if (body.clipModel!!.IsTraceModel()) {
                    if (model != null) {
                        Game_local.gameLocal.clip.RotationModel(
                            bodyResults, body.current.worldOrigin, rotation,
                            body.clipModel, body.current.worldAxis, body.clipMask,
                            model.Handle(), model.GetOrigin(), model.GetAxis()
                        )
                    } else {
                        Game_local.gameLocal.clip.Rotation(
                            bodyResults, body.current.worldOrigin, rotation,
                            body.clipModel, body.current.worldAxis, body.clipMask, self
                        )
                    }
                    if (bodyResults.fraction < results.fraction) {
                        results = bodyResults
                    }
                }
                i++
            }
            partialRotation = rotation.times(results.fraction)
            results.endpos.set(bodies[0].current.worldOrigin.times(partialRotation))
            results.endAxis.set(bodies[0].current.worldAxis.times(partialRotation.ToMat3()))
        }

        override fun ClipContents(model: idClipModel?): Int {
            var i: Int
            var contents: Int
            var body: idAFBody?
            contents = 0
            i = 0
            while (i < bodies.Num()) {
                body = bodies[i]
                if (body.clipModel!!.IsTraceModel()) {
                    contents = if (model != null) {
                        contents or Game_local.gameLocal.clip.ContentsModel(
                            body.current.worldOrigin,
                            body.clipModel, body.current.worldAxis, -1,
                            model.Handle(), model.GetOrigin(), model.GetAxis()
                        )
                    } else {
                        contents or Game_local.gameLocal.clip.Contents(
                            body.current.worldOrigin,
                            body.clipModel, body.current.worldAxis, -1, null
                        )
                    }
                }
                i++
            }
            return contents
        }

        override fun DisableClip() {
            var i: Int
            i = 0
            while (i < bodies.Num()) {
                bodies[i].clipModel!!.Disable()
                i++
            }
        }

        override fun EnableClip() {
            var i: Int
            i = 0
            while (i < bodies.Num()) {
                bodies[i].clipModel!!.Enable()
                i++
            }
        }

        override fun UnlinkClip() {
            var i: Int
            i = 0
            while (i < bodies.Num()) {
                bodies[i].clipModel!!.Unlink()
                i++
            }
        }

        override fun LinkClip() {
            UpdateClipModels()
        }

        override fun EvaluateContacts(): Boolean {
            var i: Int
            var j: Int
            var k: Int
            var numContacts: Int
            var numBodyContacts: Int
            var passEntity: idEntity?
            val dir = idVecX(6, idVecX.VECX_ALLOCA(6))

            // evaluate bodies
            EvaluateBodies(current.lastTimeStep)

            // remove all existing contacts
            ClearContacts()
            contactBodies.SetNum(0, false)
            if (!enableCollision) {
                return false
            }

            // find all the contacts
            i = 0
            while (i < bodies.Num()) {
                val body = bodies[i]
                val contactInfo = Array(10) { contactInfo_t() }
                if (body.clipMask == 0) {
                    i++
                    continue
                }
                passEntity = SetupCollisionForBody(body)
                body.InverseWorldSpatialInertiaMultiply(dir, body.current.externalForce.ToFloatPtr())
                dir.SubVec6_oSet(0, body.current.spatialVelocity.plus(dir.SubVec6(0).times(current.lastTimeStep)))
                dir.SubVec3_Normalize(0)
                dir.SubVec3_Normalize(1)
                numContacts = Game_local.gameLocal.clip.Contacts(
                    contactInfo, 10, body.current.worldOrigin, dir.SubVec6(0), 2.0f,  //CONTACT_EPSILON,
                    body.clipModel, body.current.worldAxis, body.clipMask, passEntity
                )
                if (true) {
                    // merge nearby contacts between the same bodies
                    // and assure there are at most three planar contacts between any pair of bodies
                    j = 0
                    while (j < numContacts) {
                        numBodyContacts = 0
                        k = 0
                        while (k < contacts.Num()) {
                            if (contacts[k].entityNum == contactInfo[j].entityNum) {
                                if (contacts[k].id == i && contactInfo[j].id == contactBodies[k]
                                    || contactBodies[k] == i && contacts[k].id == contactInfo[j].id
                                ) {
                                    if (contacts[k].point.minus(contactInfo[j].point).LengthSqr() < Square(
                                            2.0f
                                        )
                                    ) {
                                        break
                                    }
                                    if (abs(contacts[k].normal.times(contactInfo[j].normal)) > 0.9) {
                                        numBodyContacts++
                                    }
                                }
                            }
                            k++
                        }
                        if (k >= contacts.Num() && numBodyContacts < 3) {
                            contacts.Append(contactInfo[j])
                            contactBodies.Append(i)
                        }
                        j++
                    }

//}else{
//
//		for ( j = 0; j < numContacts; j++ ) {
//			contacts.Append( contactInfo[j] );
//			contactBodies.Append( i );
//		}
                }
                i++
            }
            AddContactEntitiesForContacts()
            return contacts.Num() != 0
        }

        override fun SetPushed(deltaTime: Int) {
            val body: idAFBody?
            val rotation: idRotation
            if (bodies.Num() != 0) {
                body = bodies[0]
                rotation = body.saved.worldAxis.Transpose().times(body.current.worldAxis).ToRotation()

                // velocity with which the af is pushed
                current.pushVelocity.SubVec3_oPluSet(
                    0,
                    body.current.worldOrigin.minus(body.saved.worldOrigin).div(deltaTime * idMath.M_MS2SEC)
                )
                current.pushVelocity.SubVec3_oPluSet(
                    1,
                    rotation.GetVec().times(-DEG2RAD(rotation.GetAngle()))
                        .div(deltaTime * idMath.M_MS2SEC)
                )
            }
        }

        override fun GetPushedLinearVelocity(id: Int /*= 0*/): idVec3 {
            return current.pushVelocity.SubVec3(0)
        }

        override fun GetPushedAngularVelocity(id: Int /*= 0*/): idVec3 {
            return current.pushVelocity.SubVec3(1)
        }

        /*
         ================
         idPhysics_AF::SetMaster

         the binding is orientated based on the constraints being used
         ================
         */
        override fun SetMaster(master: idEntity?, orientated: Boolean /*= true*/) {
            var i: Int
            val masterOrigin = idVec3()
            val masterAxis = idMat3()
            val rotation: idRotation
            if (master != null) {
                self!!.GetMasterPosition(masterOrigin, masterAxis)
                if (null == masterBody) {
                    masterBody = idAFBody()
                    // translate and rotate all the constraints with body2 == NULL from world space to master space
                    rotation = masterAxis.Transpose().ToRotation()
                    i = 0
                    while (i < constraints.Num()) {
                        if (constraints[i].GetBody2() == null) {
                            constraints[i].Translate(masterOrigin.unaryMinus())
                            constraints[i].Rotate(rotation)
                        }
                        i++
                    }
                    Activate()
                }
                masterBody!!.current.worldOrigin.set(masterOrigin)
                masterBody!!.current.worldAxis.set(masterAxis)
            } else if (masterBody != null) {
                // translate and rotate all the constraints with body2 == NULL from master space to world space
                rotation = masterBody!!.current.worldAxis.ToRotation()
                i = 0
                while (i < constraints.Num()) {
                    if (constraints[i].GetBody2() == null) {
                        constraints[i].Rotate(rotation)
                        constraints[i].Translate(masterBody!!.current.worldOrigin)
                    }
                    i++
                }
                //			delete masterBody;
                masterBody = null
                Activate()
            }
        }

        override fun WriteToSnapshot(msg: idBitMsgDelta) {
            var i: Int
            var quat: idCQuat
            msg.WriteLong(current.atRest)
            msg.WriteFloat(current.noMoveTime)
            msg.WriteFloat(current.activateTime)
            msg.WriteDeltaFloat(
                0.0f,
                current.pushVelocity[0],
                AF_VELOCITY_EXPONENT_BITS,
                AF_VELOCITY_MANTISSA_BITS
            )
            msg.WriteDeltaFloat(
                0.0f,
                current.pushVelocity[1],
                AF_VELOCITY_EXPONENT_BITS,
                AF_VELOCITY_MANTISSA_BITS
            )
            msg.WriteDeltaFloat(
                0.0f,
                current.pushVelocity[2],
                AF_VELOCITY_EXPONENT_BITS,
                AF_VELOCITY_MANTISSA_BITS
            )
            msg.WriteDeltaFloat(
                0.0f,
                current.pushVelocity[3],
                AF_VELOCITY_EXPONENT_BITS,
                AF_VELOCITY_MANTISSA_BITS
            )
            msg.WriteDeltaFloat(
                0.0f,
                current.pushVelocity[4],
                AF_VELOCITY_EXPONENT_BITS,
                AF_VELOCITY_MANTISSA_BITS
            )
            msg.WriteDeltaFloat(
                0.0f,
                current.pushVelocity[5],
                AF_VELOCITY_EXPONENT_BITS,
                AF_VELOCITY_MANTISSA_BITS
            )
            msg.WriteByte(bodies.Num())
            i = 0
            while (i < bodies.Num()) {
                val state = bodies[i].current
                quat = state.worldAxis.ToCQuat()
                msg.WriteFloat(state.worldOrigin[0])
                msg.WriteFloat(state.worldOrigin[1])
                msg.WriteFloat(state.worldOrigin[2])
                msg.WriteFloat(quat.x)
                msg.WriteFloat(quat.y)
                msg.WriteFloat(quat.z)
                msg.WriteDeltaFloat(
                    0.0f,
                    state.spatialVelocity[0],
                    AF_VELOCITY_EXPONENT_BITS,
                    AF_VELOCITY_MANTISSA_BITS
                )
                msg.WriteDeltaFloat(
                    0.0f,
                    state.spatialVelocity[1],
                    AF_VELOCITY_EXPONENT_BITS,
                    AF_VELOCITY_MANTISSA_BITS
                )
                msg.WriteDeltaFloat(
                    0.0f,
                    state.spatialVelocity[2],
                    AF_VELOCITY_EXPONENT_BITS,
                    AF_VELOCITY_MANTISSA_BITS
                )
                msg.WriteDeltaFloat(
                    0.0f,
                    state.spatialVelocity[3],
                    AF_VELOCITY_EXPONENT_BITS,
                    AF_VELOCITY_MANTISSA_BITS
                )
                msg.WriteDeltaFloat(
                    0.0f,
                    state.spatialVelocity[4],
                    AF_VELOCITY_EXPONENT_BITS,
                    AF_VELOCITY_MANTISSA_BITS
                )
                msg.WriteDeltaFloat(
                    0.0f,
                    state.spatialVelocity[5],
                    AF_VELOCITY_EXPONENT_BITS,
                    AF_VELOCITY_MANTISSA_BITS
                )
                i++
            }
        }

        override fun ReadFromSnapshot(msg: idBitMsgDelta) {
            var i: Int
            val num: Int
            val quat = idCQuat()
            current.atRest = msg.ReadLong()
            current.noMoveTime = msg.ReadFloat()
            current.activateTime = msg.ReadFloat()
            current.pushVelocity[0] = msg.ReadDeltaFloat(0.0f, AF_VELOCITY_EXPONENT_BITS, AF_VELOCITY_MANTISSA_BITS)
            current.pushVelocity[1] = msg.ReadDeltaFloat(0.0f, AF_VELOCITY_EXPONENT_BITS, AF_VELOCITY_MANTISSA_BITS)
            current.pushVelocity[2] = msg.ReadDeltaFloat(0.0f, AF_VELOCITY_EXPONENT_BITS, AF_VELOCITY_MANTISSA_BITS)
            current.pushVelocity[3] = msg.ReadDeltaFloat(0.0f, AF_VELOCITY_EXPONENT_BITS, AF_VELOCITY_MANTISSA_BITS)
            current.pushVelocity[4] = msg.ReadDeltaFloat(0.0f, AF_VELOCITY_EXPONENT_BITS, AF_VELOCITY_MANTISSA_BITS)
            current.pushVelocity[5] = msg.ReadDeltaFloat(0.0f, AF_VELOCITY_EXPONENT_BITS, AF_VELOCITY_MANTISSA_BITS)
            num = msg.ReadByte()
            assert(num == bodies.Num())
            i = 0
            while (i < bodies.Num()) {
                val state = bodies[i].current
                state.worldOrigin[0] = msg.ReadFloat()
                state.worldOrigin[1] = msg.ReadFloat()
                state.worldOrigin[2] = msg.ReadFloat()
                quat.x = msg.ReadFloat()
                quat.y = msg.ReadFloat()
                quat.z = msg.ReadFloat()
                state.spatialVelocity[0] =
                    msg.ReadDeltaFloat(0.0f, AF_VELOCITY_EXPONENT_BITS, AF_VELOCITY_MANTISSA_BITS)
                state.spatialVelocity[1] =
                    msg.ReadDeltaFloat(0.0f, AF_VELOCITY_EXPONENT_BITS, AF_VELOCITY_MANTISSA_BITS)
                state.spatialVelocity[2] =
                    msg.ReadDeltaFloat(0.0f, AF_VELOCITY_EXPONENT_BITS, AF_VELOCITY_MANTISSA_BITS)
                state.spatialVelocity[3] =
                    msg.ReadDeltaFloat(0.0f, AF_VELOCITY_EXPONENT_BITS, AF_VELOCITY_MANTISSA_BITS)
                state.spatialVelocity[4] =
                    msg.ReadDeltaFloat(0.0f, AF_VELOCITY_EXPONENT_BITS, AF_VELOCITY_MANTISSA_BITS)
                state.spatialVelocity[5] =
                    msg.ReadDeltaFloat(0.0f, AF_VELOCITY_EXPONENT_BITS, AF_VELOCITY_MANTISSA_BITS)
                /*		state.externalForce[0] = msg.ReadDeltaFloat( 0.0f, AF_FORCE_EXPONENT_BITS, AF_FORCE_MANTISSA_BITS );
                 state.externalForce[1] = msg.ReadDeltaFloat( 0.0f, AF_FORCE_EXPONENT_BITS, AF_FORCE_MANTISSA_BITS );
                 state.externalForce[2] = msg.ReadDeltaFloat( 0.0f, AF_FORCE_EXPONENT_BITS, AF_FORCE_MANTISSA_BITS );
                 state.externalForce[3] = msg.ReadDeltaFloat( 0.0f, AF_FORCE_EXPONENT_BITS, AF_FORCE_MANTISSA_BITS );
                 state.externalForce[4] = msg.ReadDeltaFloat( 0.0f, AF_FORCE_EXPONENT_BITS, AF_FORCE_MANTISSA_BITS );
                 state.externalForce[5] = msg.ReadDeltaFloat( 0.0f, AF_FORCE_EXPONENT_BITS, AF_FORCE_MANTISSA_BITS );
                 */state.worldAxis.set(quat.ToMat3())
                i++
            }
            UpdateClipModels()
        }

        private fun BuildTrees() {
            var i: Int
            val scale: Float
            var b: idAFBody?
            var c: idAFConstraint?
            var tree: idAFTree
            primaryConstraints.Clear()
            auxiliaryConstraints.Clear()
            trees.DeleteContents(true)
            totalMass = 0.0f
            i = 0
            while (i < bodies.Num()) {
                b = bodies[i]
                b.parent = null
                b.primaryConstraint = null
                b.constraints.SetNum(0, false)
                b.children.Clear()
                b.tree = null
                totalMass += b.mass
                i++
            }
            if (forceTotalMass > 0.0f) {
                scale = forceTotalMass / totalMass
                i = 0
                while (i < bodies.Num()) {
                    b = bodies[i]
                    b.mass *= scale
                    b.invMass = 1.0f / b.mass
                    b.inertiaTensor.timesAssign(scale)
                    b.inverseInertiaTensor.set(b.inertiaTensor.Inverse())
                    i++
                }
                totalMass = forceTotalMass
            }
            if (SysCvar.af_useLinearTime.GetBool()) {
                i = 0
                while (i < constraints.Num()) {
                    c = constraints[i]
                    c.body1!!.constraints.Append(c)
                    if (c.body2 != null) {
                        c.body2!!.constraints.Append(c)
                    }

                    // only bilateral constraints between two non-world bodies that do not
                    // create loops can be used as primary constraints
                    if (null == c.body1!!.primaryConstraint && c.fl.allowPrimary && c.body2 != null && !IsClosedLoop(
                            c.body1,
                            c.body2
                        )
                    ) {
                        c.body1!!.primaryConstraint = c
                        c.body1!!.parent = c.body2
                        c.body2!!.children.Append(c.body1!!)
                        c.fl.isPrimary = true
                        c.firstIndex = 0
                        primaryConstraints.Append(c)
                    } else {
                        c.fl.isPrimary = false
                        auxiliaryConstraints.Append(c)
                    }
                    i++
                }

                // create trees for all parent bodies
                i = 0
                while (i < bodies.Num()) {
                    if (null == bodies[i].parent) {
                        tree = idAFTree()
                        tree!!.sortedBodies.Clear()
                        tree!!.sortedBodies.Append(bodies[i])
                        bodies[i].tree = tree
                        trees.Append(tree)
                    }
                    i++
                }

                // add each child body to the appropriate tree
                i = 0
                while (i < bodies.Num()) {
                    if (bodies[i].parent != null) {
                        b = bodies[i].parent
                        while (null == b!!.tree) {
                            b = b.parent
                        }
                        b.tree!!.sortedBodies.Append(bodies[i])
                        bodies[i].tree = b.tree
                    }
                    i++
                }
                if (trees.Num() > 1) {
                    Game_local.gameLocal.Warning(
                        "Articulated figure has multiple seperate tree structures for entity '%s' type '%s'.",
                        self!!.name, self!!.GetType().name
                    )
                }

                // sort bodies in each tree to make sure parents come first
                i = 0
                while (i < trees.Num()) {
                    trees[i].SortBodies()
                    i++
                }
            } else {

                // create a tree for each body
                i = 0
                while (i < bodies.Num()) {
                    tree = idAFTree()
                    tree!!.sortedBodies.Clear()
                    tree!!.sortedBodies.Append(bodies[i])
                    bodies[i].tree = tree
                    trees.Append(tree)
                    i++
                }
                i = 0
                while (i < constraints.Num()) {
                    c = constraints[i]
                    c.body1!!.constraints.Append(c)
                    if (c.body2 != null) {
                        c.body2!!.constraints.Append(c)
                    }
                    c.fl.isPrimary = false
                    auxiliaryConstraints.Append(c)
                    i++
                }
            }
        }

        private fun IsClosedLoop(body1: idAFBody?, body2: idAFBody?): Boolean {
            var b1: idAFBody?
            var b2: idAFBody?
            b1 = body1
            while (b1!!.parent != null) {
                b1 = b1.parent
            }
            b2 = body2
            while (b2!!.parent != null) {
                b2 = b2.parent
            }
            return b1 == b2
        }

        private fun PrimaryFactor() {
            var i: Int
            i = 0
            while (i < trees.Num()) {
                trees[i].Factor()
                i++
            }
        }

        private fun EvaluateBodies(timeStep: Float) {
            var i: Int
            var body: idAFBody?
            val axis = idMat3()
            i = 0
            while (i < bodies.Num()) {
                body = bodies[i]

                // we transpose the axis before using it because idMat3 is column-major
                axis.set(body.current.worldAxis.Transpose())

                // if the center of mass is at the body point of reference
                if (body.centerOfMass.Compare(getVec3Origin(), CENTER_OF_MASS_EPSILON)) {

                    // spatial inertia in world space
                    body.I.set(
                        idMat3.getMat3_identity().times(body.mass),
                        idMat3.getMat3_zero(),
                        idMat3.getMat3_zero(),
                        axis.times(body.inertiaTensor).times(axis.Transpose())
                    )

                    // inverse spatial inertia in world space
                    body.inverseWorldSpatialInertia.set(
                        idMat3.getMat3_identity().times(body.invMass),
                        idMat3.getMat3_zero(),
                        idMat3.getMat3_zero(),
                        axis.times(body.inverseInertiaTensor).times(axis.Transpose())
                    )
                    body.fl.spatialInertiaSparse = true
                } else {
                    val massMoment: idMat3 = idMat3.SkewSymmetric(body.centerOfMass).times(body.mass)

                    // spatial inertia in world space
                    body.I.set(
                        idMat3.getMat3_identity().times(body.mass), massMoment,
                        massMoment.Transpose(), axis.times(body.inertiaTensor).times(axis.Transpose())
                    )

                    // inverse spatial inertia in world space
                    body.inverseWorldSpatialInertia.set(body.I.InverseFast())
                    body.fl.spatialInertiaSparse = false
                }

                // initialize auxiliary constraint force to zero
                body.auxForce.Zero()
                i++
            }
        }

        private fun EvaluateConstraints(timeStep: Float) {
            var i: Int
            val invTimeStep: Float
            var body: idAFBody?
            var c: idAFConstraint?
            invTimeStep = 1.0f / timeStep

            // setup the constraint equations for the current position and orientation of the bodies
            i = 0
            while (i < primaryConstraints.Num()) {
                c = primaryConstraints[i]
                c.Evaluate(invTimeStep)
                c.J.set(idMatX(c.J2))
                i++
            }
            i = 0
            while (i < auxiliaryConstraints.Num()) {
                auxiliaryConstraints[i].Evaluate(invTimeStep)
                i++
            }

            // add contact constraints to the list with frame constraints
            i = 0
            while (i < contactConstraints.Num()) {
                AddFrameConstraint(contactConstraints[i])
                i++
            }

            // setup body primary constraint matrix
            i = 0
            while (i < bodies.Num()) {
                body = bodies[i]
                if (body.primaryConstraint != null) {
                    body.J.set(body.primaryConstraint!!.J1.Transpose())
                }
                i++
            }
        }

        private fun AddFrameConstraints() {
            var i: Int

            // add frame constraints to auxiliary constraints
            i = 0
            while (i < frameConstraints.Num()) {
                auxiliaryConstraints.Append(frameConstraints[i])
                i++
            }
        }

        private fun RemoveFrameConstraints() {
            // remove all the frame constraints from the auxiliary constraints
            auxiliaryConstraints.SetNum(auxiliaryConstraints.Num() - frameConstraints.Num(), false)
            frameConstraints.SetNum(0, false)
        }

        private fun ApplyFriction(timeStep: Float, endTimeMSec: Float) {
            var i: Int
            val invTimeStep: Float
            if (SysCvar.af_skipFriction.GetBool()) {
                return
            }
            jointFrictionDentScale =
                if (jointFrictionDentStart < MS2SEC(endTimeMSec) && jointFrictionDentEnd > MS2SEC(
                        endTimeMSec
                    )
                ) {
                    val halfTime = (jointFrictionDentEnd - jointFrictionDentStart) * 0.5f
                    if (jointFrictionDentStart + halfTime > MS2SEC(endTimeMSec)) {
                        1.0f - (1.0f - jointFrictionDent) * (MS2SEC(endTimeMSec) - jointFrictionDentStart) / halfTime
                    } else {
                        jointFrictionDent + (1.0f - jointFrictionDent) * (MS2SEC(endTimeMSec) - jointFrictionDentStart - halfTime) / halfTime
                    }
                } else {
                    0.0f
                }
            contactFrictionDentScale =
                if (contactFrictionDentStart < MS2SEC(endTimeMSec) && contactFrictionDentEnd > MS2SEC(
                        endTimeMSec
                    )
                ) {
                    val halfTime = (contactFrictionDentEnd - contactFrictionDentStart) * 0.5f
                    if (contactFrictionDentStart + halfTime > MS2SEC(endTimeMSec)) {
                        1.0f - (1.0f - contactFrictionDent) * (MS2SEC(endTimeMSec) - contactFrictionDentStart) / halfTime
                    } else {
                        contactFrictionDent + (1.0f - contactFrictionDent) * (MS2SEC(endTimeMSec) - contactFrictionDentStart - halfTime) / halfTime
                    }
                } else {
                    0.0f
                }
            invTimeStep = 1.0f / timeStep
            i = 0
            while (i < primaryConstraints.Num()) {
                primaryConstraints[i].ApplyFriction(invTimeStep)
                i++
            }
            i = 0
            while (i < auxiliaryConstraints.Num()) {
                auxiliaryConstraints[i].ApplyFriction(invTimeStep)
                i++
            }
            i = 0
            while (i < frameConstraints.Num()) {
                frameConstraints[i].ApplyFriction(invTimeStep)
                i++
            }
        }

        private fun PrimaryForces(timeStep: Float) {
            var i: Int
            i = 0
            while (i < trees.Num()) {
                trees[i].CalculateForces(timeStep)
                i++
            }
        }

        private fun AuxiliaryForces(timeStep: Float) {
            var i: Int
            var j: Int
            var k: Int
            var l: Int
            var n: Int
            var m: Int
            var s: Int
            var numAuxConstraints: Int
            var index: IntArray
            val boxIndex: IntArray
            var ptr: FloatArray
            var j1: FloatArray
            var j2: FloatArray
            var dstPtr: FloatArray
            val invStep: Float
            var u: Float
            var body: idAFBody?
            var constraint: idAFConstraint?
            val tmp = idVecX()
            val jmk = idMatX()
            val rhs = idVecX()
            idVecX()
            val lm = idVecX()
            val lo = idVecX()
            val hi = idVecX()
            var p_i: Int
            var d_i: Int

            // get the number of one dimensional auxiliary constraints
            numAuxConstraints = 0
            i = 0
            while (i < auxiliaryConstraints.Num()) {
                numAuxConstraints += auxiliaryConstraints[i].J1.GetNumRows()
                i++
            }
            if (numAuxConstraints == 0) {
                return
            }

            // allocate memory to store the body response to auxiliary constraint forces
//            forcePtr = new float[bodies.Num() * numAuxConstraints * 8];
//            index = new int[bodies.Num() * numAuxConstraints];
            i = 0
            while (i < bodies.Num()) {
                body = bodies[i]
                body.response = FloatArray(bodies.Num() * numAuxConstraints * 8)
                body.responseIndex = IntArray(bodies.Num() * numAuxConstraints)
                body.numResponses = 0
                body.maxAuxiliaryIndex = 0
                i++
            }

            // set on each body the largest index of an auxiliary constraint constraining the body
            if (SysCvar.af_useSymmetry.GetBool()) {
                k = 0
                i = 0
                while (i < auxiliaryConstraints.Num()) {
                    constraint = auxiliaryConstraints[i]
                    j = 0
                    while (j < constraint.J1.GetNumRows()) {
                        if (k > constraint.body1!!.maxAuxiliaryIndex) {
                            constraint.body1!!.maxAuxiliaryIndex = k
                        }
                        if (constraint.body2 != null && k > constraint.body2!!.maxAuxiliaryIndex) {
                            constraint.body2!!.maxAuxiliaryIndex = k
                        }
                        j++
                        k++
                    }
                    i++
                }
                i = 0
                while (i < trees.Num()) {
                    trees[i].SetMaxSubTreeAuxiliaryIndex()
                    i++
                }
            }

            // calculate forces of primary constraints in response to the auxiliary constraint forces
            k = 0
            i = 0
            while (i < auxiliaryConstraints.Num()) {
                constraint = auxiliaryConstraints[i]
                j = 0
                while (j < constraint.J1.GetNumRows()) {


                    // calculate body forces in the tree in response to the constraint force
                    constraint.body1!!.tree!!.Response(constraint, j, k)
                    // if there is a second body which is part of a different tree
                    if (constraint.body2 != null && constraint.body2!!.tree !== constraint.body1!!.tree) {
                        // calculate body forces in the second tree in response to the constraint force
                        constraint.body2!!.tree!!.Response(constraint, j, k)
                    }
                    j++
                    k++
                }
                i++
            }

            // NOTE: the rows are 16 byte padded
            jmk.SetData(
                numAuxConstraints,
                numAuxConstraints + 3 and 3.inv(),
                idMatX.MATX_ALLOCA(numAuxConstraints * (numAuxConstraints + 3 and 3.inv()))
            )
            tmp.SetData(6, idVecX.VECX_ALLOCA(6))

            // create constraint matrix for auxiliary constraints using a mass matrix adjusted for the primary constraints
            k = 0
            i = 0
            while (i < auxiliaryConstraints.Num()) {
                constraint = auxiliaryConstraints[i]
                j = 0
                while (j < constraint.J1.GetNumRows()) {
                    constraint.body1!!.InverseWorldSpatialInertiaMultiply(tmp, constraint.J1[j])
                    j1 = tmp.ToFloatPtr()
                    ptr = constraint.body1!!.response!!
                    index = constraint.body1!!.responseIndex!!
                    dstPtr = jmk.ToFloatPtr()
                    s = if (SysCvar.af_useSymmetry.GetBool()) k + 1 else numAuxConstraints
                    val c = k * jmk.GetNumColumns()
                    l = 0.also { p_i = it }.also { n = it }
                    m = index[n]
                    while (n < constraint.body1!!.numResponses && m < s) {
                        while (l < m) {
                            dstPtr[c + l++] = 0.0f
                        }
                        dstPtr[c + l++] =
                            j1[0] * ptr[p_i + 0] + j1[1] * ptr[p_i + 1] + j1[2] * ptr[p_i + 2] + j1[3] * ptr[p_i + 3] + j1[4] * ptr[p_i + 4] + j1[5] * ptr[p_i + 5]
                        p_i += 8
                        n++
                        m = index[n]
                    }
                    while (l < s) {
                        dstPtr[c + l++] = 0.0f
                    }
                    if (constraint.body2 != null) {
                        constraint.body2!!.InverseWorldSpatialInertiaMultiply(tmp, constraint.J2[j])
                        j2 = tmp.ToFloatPtr()
                        ptr = constraint.body2!!.response!!
                        index = constraint.body2!!.responseIndex!!
                        n = 0.also { p_i = it }
                        m = index[n]
                        while (n < constraint.body2!!.numResponses && m < s) {
                            dstPtr[c + m] += j2[0] * ptr[p_i + 0] + j2[1] * ptr[p_i + 1] + j2[2] * ptr[p_i + 2] + j2[3] * ptr[p_i + 3] + j2[4] * ptr[p_i + 4] + j2[5] * ptr[p_i + 5]
                            p_i += 8
                            n++
                            m = index[n]
                        }
                    }
                    j++
                    k++
                }
                i++
            }
            if (SysCvar.af_useSymmetry.GetBool()) {
                n = jmk.GetNumColumns()
                i = 0
                while (i < numAuxConstraints) {
                    ptr = jmk.ToFloatPtr()
                    p_i = (i + 1) * n + i
                    dstPtr = jmk.ToFloatPtr()
                    d_i = i * n + i + 1
                    j = i + 1
                    while (j < numAuxConstraints) {
                        dstPtr[d_i++] = ptr[p_i]
                        p_i += n
                        j++
                    }
                    i++
                }
            }
            invStep = 1.0f / timeStep

            // calculate body acceleration
            i = 0
            while (i < bodies.Num()) {
                body = bodies[i]
                body.InverseWorldSpatialInertiaMultiply(body.acceleration, body.totalForce.ToFloatPtr())
                body.acceleration.SubVec6_oPluSet(0, body.current.spatialVelocity.times(invStep))
                i++
            }
            rhs.SetData(numAuxConstraints, idVecX.VECX_ALLOCA(numAuxConstraints))
            lo.SetData(numAuxConstraints, idVecX.VECX_ALLOCA(numAuxConstraints))
            hi.SetData(numAuxConstraints, idVecX.VECX_ALLOCA(numAuxConstraints))
            lm.SetData(numAuxConstraints, idVecX.VECX_ALLOCA(numAuxConstraints))
            boxIndex = IntArray(numAuxConstraints)

            // set first index for special box constrained variables
            k = 0
            i = 0
            while (i < auxiliaryConstraints.Num()) {
                auxiliaryConstraints[i].firstIndex = k
                k += auxiliaryConstraints[i].J1.GetNumRows()
                i++
            }

            // initialize right hand side and low and high bounds for auxiliary constraints
            k = 0
            i = 0
            while (i < auxiliaryConstraints.Num()) {
                constraint = auxiliaryConstraints[i]
                n = k
                j = 0
                while (j < constraint.J1.GetNumRows()) {
                    j1 = constraint.J1[j]
                    ptr = constraint.body1!!.acceleration.ToFloatPtr()
                    rhs.p[k] =
                        j1[0] * ptr[0] + j1[1] * ptr[1] + j1[2] * ptr[2] + j1[3] * ptr[3] + j1[4] * ptr[4] + j1[5] * ptr[5]
                    rhs.p[k] += constraint.c1.p[j] * invStep
                    if (constraint.body2 != null) {
                        j2 = constraint.J2[j]
                        ptr = constraint.body2!!.acceleration.ToFloatPtr()
                        rhs.p[k] += j2[0] * ptr[0] + j2[1] * ptr[1] + j2[2] * ptr[2] + j2[3] * ptr[3] + j2[4] * ptr[4] + j2[5] * ptr[5]
                        rhs.p[k] += constraint.c2.p[j] * invStep
                    }
                    rhs[k] = -rhs.get(k)
                    lo.p[k] = constraint.lo.p[j]
                    hi.p[k] = constraint.hi.p[j]
                    if (constraint.boxIndex[j] >= 0) {
                        if (constraint.boxConstraint!!.fl.isPrimary) {
                            idGameLocal.Error("cannot reference primary constraints for the box index")
                        }
                        boxIndex[k] = constraint.boxConstraint!!.firstIndex + constraint.boxIndex[j]
                    } else {
                        boxIndex[k] = -1
                    }
                    jmk[k][k]
                    jmk.plusAssign(k, k, constraint.e.p[j] * invStep)
                    j++
                    k++
                }
                i++
            }
            if (AF_TIMINGS) {
                timer_lcp.Start()
            }

            // calculate lagrange multipliers for auxiliary constraints
            if (!lcp.Solve(jmk, lm, rhs, lo, hi, boxIndex)) {
                return  // bad monkey!
            }
            if (AF_TIMINGS) {
                timer_lcp.Stop()
            }

            // calculate auxiliary constraint forces
            k = 0
            i = 0
            while (i < auxiliaryConstraints.Num()) {
                constraint = auxiliaryConstraints[i]
                j = 0
                while (j < constraint.J1.GetNumRows()) {
                    u = lm.get(k)
                    constraint.lm.p[j] = u
                    j1 = constraint.J1[j]
                    ptr = constraint.body1!!.auxForce.ToFloatPtr()
                    ptr[0] += j1[0] * u
                    ptr[1] += j1[1] * u
                    ptr[2] += j1[2] * u
                    ptr[3] += j1[3] * u
                    ptr[4] += j1[4] * u
                    ptr[5] += j1[5] * u
                    if (constraint.body2 != null) {
                        j2 = constraint.J2[j]
                        ptr = constraint.body2!!.auxForce.ToFloatPtr()
                        ptr[0] += j2[0] * u
                        ptr[1] += j2[1] * u
                        ptr[2] += j2[2] * u
                        ptr[3] += j2[3] * u
                        ptr[4] += j2[4] * u
                        ptr[5] += j2[5] * u
                    }
                    j++
                    k++
                }
                i++
            }

            // recalculate primary constraint forces in response to auxiliary constraint forces
            PrimaryForces(timeStep)

            // clear pointers pointing to stack space so tools don't get confused
            i = 0
            while (i < bodies.Num()) {
                body = bodies[i]
                body.response = null
                body.responseIndex = null
                i++
            }
        }

        private fun VerifyContactConstraints() {
            var i: Int
            var body: idAFBody?
            val normal = idVec3()
            i = 0
            while (i < contactConstraints.Num()) {
                body = contactConstraints[i].body1
                normal.set(contactConstraints[i].GetContact().normal)
                if (normal * body!!.next.spatialVelocity.SubVec3(0) <= 0.0f) {
                    body.next.spatialVelocity.SubVec3_oMinSet(
                        0,
                        1.0001f * (normal * body.next.spatialVelocity.SubVec3(0)) * normal
                    )
                }
                body = contactConstraints[i].body2
                if (null == body) {
                    i++
                    continue
                }
                normal.set(normal.unaryMinus())
                if (normal * body.next.spatialVelocity.SubVec3(0) <= 0.0f) {
                    body.next.spatialVelocity.SubVec3_oMinSet(
                        0,
                        1.0001f * (normal * body.next.spatialVelocity.SubVec3(0)) * normal
                    )
                }
                i++
            }
            // }
        }

        private fun SetupContactConstraints() {
            var i: Int

            // make sure enough contact constraints are allocated
            contactConstraints.AssureSizeAlloc(contacts.Num(), idAFConstraint_Contact::class.java)
            contactConstraints.SetNum(contacts.Num(), false)

            // setup contact constraints
            i = 0
            while (i < contacts.Num()) {

                // add contact constraint
                contactConstraints[i].physics = this
                if (contacts[i].entityNum == self!!.entityNumber) {
                    contactConstraints[i]
                        .Setup(bodies[contactBodies[i]], bodies[contacts[i].id], contacts[i])
                } else {
                    contactConstraints[i].Setup(bodies[contactBodies[i]], null, contacts[i])
                }
                i++
            }
        }

        private fun ApplyContactForces() {
            return  // method behind ifdef, probably not used
        }

        private fun Evolve(timeStep: Float) {
            var i: Int
            var angle: Float
            val vec = idVec3()
            var body: idAFBody?
            var rotation: idRotation
            var vSqr: Float
            val maxLinearVelocity: Float
            val maxAngularVelocity: Float
            maxLinearVelocity = SysCvar.af_maxLinearVelocity.GetFloat() / timeStep
            maxAngularVelocity = SysCvar.af_maxAngularVelocity.GetFloat() / timeStep
            i = 0
            while (i < bodies.Num()) {
                body = bodies[i]

                // calculate the spatial velocity for the next physics state
                body.InverseWorldSpatialInertiaMultiply(body.acceleration, body.totalForce.ToFloatPtr())
                body.next.spatialVelocity.set(
                    body.current.spatialVelocity.plus(
                        body.acceleration.SubVec6(0).times(timeStep)
                    )
                )
                if (maxLinearVelocity > 0.0f) {
                    // cap the linear velocity
                    vSqr = body.next.spatialVelocity.SubVec3(0).LengthSqr()
                    if (vSqr > Square(maxLinearVelocity)) {
                        body.next.spatialVelocity.SubVec3_oMulSet(0, idMath.InvSqrt(vSqr) * maxLinearVelocity)
                    }
                }
                if (maxAngularVelocity > 0.0f) {
                    // cap the angular velocity
                    vSqr = body.next.spatialVelocity.SubVec3(1).LengthSqr()
                    if (vSqr > Square(maxAngularVelocity)) {
                        body.next.spatialVelocity.SubVec3_oMulSet(1, idMath.InvSqrt(vSqr) * maxAngularVelocity)
                    }
                }
                i++
            }

            // make absolutely sure all contact constraints are satisfied
            VerifyContactConstraints()

            // calculate the position of the bodies for the next physics state
            i = 0
            while (i < bodies.Num()) {
                body = bodies[i]

                // translate world origin
                body.next.worldOrigin.set(
                    body.current.worldOrigin.plus(
                        body.next.spatialVelocity.SubVec3(0).times(timeStep)
                    )
                )

                // convert angular velocity to a rotation matrix
                vec.set(body.next.spatialVelocity.SubVec3(1))
                angle = -timeStep * RAD2DEG(vec.Normalize())
                rotation = idRotation(getVec3Origin(), vec, angle)
                rotation.Normalize180()

                // rotate world axis
                body.next.worldAxis.set(body.current.worldAxis.times(rotation.ToMat3()))
                body.next.worldAxis.OrthoNormalizeSelf()

                // linear and angular friction
                body.next.spatialVelocity.SubVec3_oMinSet(
                    0,
                    body.next.spatialVelocity.SubVec3(0).times(body.linearFriction)
                )
                body.next.spatialVelocity.SubVec3_oMinSet(
                    1,
                    body.next.spatialVelocity.SubVec3(1).times(body.angularFriction)
                )
                i++
            }
        }

        private fun SetupCollisionForBody(body: idAFBody?): idEntity? {
            var i: Int
            var b: idAFBody?
            var passEntity: idEntity?
            passEntity = null
            if (!selfCollision || !body!!.fl.selfCollision || SysCvar.af_skipSelfCollision.GetBool()) {

                // disable all bodies
                i = 0
                while (i < bodies.Num()) {
                    bodies[i].clipModel!!.Disable()
                    i++
                }

                // don't collide with world collision model if attached to the world
                i = 0
                while (i < body!!.constraints.Num()) {
                    if (!body.constraints[i].fl.noCollision) {
                        i++
                        continue
                    }
                    // if this constraint attaches the body to the world
                    if (body.constraints[i].body2 == null) {
                        // don't collide with the world collision model
                        passEntity = Game_local.gameLocal.world
                    }
                    i++
                }
            } else {

                // enable all bodies that have self collision
                i = 0
                while (i < bodies.Num()) {
                    if (bodies[i].fl.selfCollision) {
                        bodies[i].clipModel!!.Enable()
                    } else {
                        bodies[i].clipModel!!.Disable()
                    }
                    i++
                }

                // don't let the body collide with itself
                body.clipModel!!.Disable()

                // disable any bodies attached with constraints
                i = 0
                while (i < body.constraints.Num()) {
                    if (!body.constraints[i].fl.noCollision) {
                        i++
                        continue
                    }
                    // if this constraint attaches the body to the world
                    if (body.constraints[i].body2 == null) {
                        // don't collide with the world collision model
                        passEntity = Game_local.gameLocal.world
                    } else {
                        b = if (body.constraints[i].body1 == body) {
                            body.constraints[i].body2
                        } else if (body.constraints[i].body2 == body) {
                            body.constraints[i].body1
                        } else {
                            i++
                            continue
                        }
                        // don't collide with this body
                        b!!.clipModel!!.Disable()
                    }
                    i++
                }
            }
            return passEntity
        }

        /*
         ================
         idPhysics_AF::CollisionImpulse

         apply impulse to the colliding bodies
         the current state of the body should be set to the moment of impact
         this is silly as it doesn't take the AF structure into account
         ================
         */
        private fun CollisionImpulse(timeStep: Float, body: idAFBody?, collision: trace_s): Boolean {
            val r = idVec3()
            val velocity = idVec3()
            val impulse = idVec3()
            val inverseWorldInertiaTensor: idMat3
            val impulseNumerator: Float
            var impulseDenominator: Float
            val info: impactInfo_s
            val ent: idEntity?
            ent = Game_local.gameLocal.entities[collision.c.entityNum]
            if (ent === self || ent == null) {
                return false
            }

            // get info from other entity involved
            info = ent.GetImpactInfo(self, collision.c.id, collision.c.point)
            // collision point relative to the body center of mass
            r.set(collision.c.point.minus(body!!.current.worldOrigin.plus(body.centerOfMass.times(body.current.worldAxis))))
            // the velocity at the collision point
            velocity.set(
                body.current.spatialVelocity.SubVec3(0).plus(body.current.spatialVelocity.SubVec3(1).Cross(r))
            )
            // subtract velocity of other entity
            velocity.minusAssign(info.velocity)
            // never stick
            if (velocity.times(collision.c.normal) > 0.0f) {
                velocity.set(collision.c.normal)
            }
            inverseWorldInertiaTensor = body.current.worldAxis.Transpose().times(body.inverseInertiaTensor)
                .times(body.current.worldAxis)
            impulseNumerator = -(1.0f + body.bouncyness) * velocity.times(collision.c.normal)
            impulseDenominator =
                body.invMass + inverseWorldInertiaTensor.times(r.Cross(collision.c.normal)).Cross(r)
                    .times(collision.c.normal)
            if (info.invMass != 0.0f) {
                impulseDenominator += info.invMass + info.invInertiaTensor.times(info.position.Cross(collision.c.normal))
                    .Cross(info.position).times(collision.c.normal)
            }
            impulse.set(collision.c.normal.times(impulseNumerator / impulseDenominator))

            // apply impact to other entity
            ent.ApplyImpulse(self, collision.c.id, collision.c.point, impulse.unaryMinus())

            // callback to self to let the entity know about the impact
            return self!!.Collide(collision, velocity)
        }

        private fun ApplyCollisions(timeStep: Float): Boolean {
            var i: Int
            i = 0
            while (i < collisions.Num()) {
                if (CollisionImpulse(timeStep, collisions[i].body, collisions[i].trace)) {
                    return true
                }
                i++
            }
            return false
        }

        private fun CheckForCollisions(timeStep: Float) {
            //	#define TEST_COLLISION_DETECTION
            var i: Int
            var index: Int
            var body: idAFBody?
            val axis = idMat3()
            var rotation: idRotation
            val collision = trace_s()
            var passEntity: idEntity?
            var startSolid = false

            // clear list with collisions
            collisions.SetNum(0, false)
            if (!enableCollision) {
                return
            }
            i = 0
            while (i < bodies.Num()) {
                body = bodies[i]
                if (body.clipMask != 0) {
                    passEntity = SetupCollisionForBody(body)
                    if (TEST_COLLISION_DETECTION) {
                        if (Game_local.gameLocal.clip.Contents(
                                body.current.worldOrigin, body.clipModel,
                                body.current.worldAxis, body.clipMask, passEntity
                            ) != 0
                        ) {
                            startSolid = true
                        }
                    }
                    idMat3.TransposeMultiply(body.current.worldAxis, body.next.worldAxis, axis)
                    rotation = axis.ToRotation()
                    rotation.SetOrigin(body.current.worldOrigin)

                    // if there was a collision
                    if (Game_local.gameLocal.clip.Motion(
                            collision, body.current.worldOrigin, body.next.worldOrigin, rotation,
                            body.clipModel, body.current.worldAxis, body.clipMask, passEntity
                        )
                    ) {

                        // set the next state to the state at the moment of impact
                        body.next.worldOrigin.set(collision.endpos)
                        body.next.worldAxis.set(collision.endAxis)

                        // add collision to the list
                        index = collisions.Num()
                        collisions.SetNum(index + 1, false)
                        collisions[index] = AFCollision_s()
                        collisions[index].trace.set(collision)
                        collisions[index].body = body
                    }
                    if (TEST_COLLISION_DETECTION) {
                        if (Game_local.gameLocal.clip.Contents(
                                body.next.worldOrigin, body.clipModel,
                                body.next.worldAxis, body.clipMask, passEntity
                            ) != 0
                        ) {
                            if (!startSolid) {
                            }
                        }
                    }
                }
                body.clipModel!!.Link(
                    Game_local.gameLocal.clip,
                    self,
                    body.clipModel!!.GetId(),
                    body.next.worldOrigin,
                    body.next.worldAxis
                )
                i++
            }
        }

        private fun ClearExternalForce() {
            var i: Int
            var body: idAFBody?
            i = 0
            while (i < bodies.Num()) {
                body = bodies[i]

                // clear external force
                body.current.externalForce.Zero()
                body.next.externalForce.Zero()
                i++
            }
        }

        private fun AddGravity() {
            var i: Int
            var body: idAFBody?
            i = 0
            while (i < bodies.Num()) {
                body = bodies[i]
                // add gravitational force
                body.current.externalForce.SubVec3_oPluSet(0, gravityVector.times(body.mass))
                i++
            }
        }

        private fun SwapStates() {
            var i: Int
            var body: idAFBody?
            var swap: AFBodyPState_s?
            i = 0
            while (i < bodies.Num()) {
                body = bodies[i]

                // swap the current and next state for next simulation step
                swap = body.current
                body.current = body.next
                body.next = swap
                i++
            }
        }

        private fun TestIfAtRest(timeStep: Float): Boolean {
            var i: Int
            var translationSqr: Float
            var maxTranslationSqr: Float
            var rotation: Float
            var maxRotation: Float
            var body: idAFBody?
            if (current.atRest >= 0) {
                return true
            }
            current.activateTime += timeStep

            // if the simulation should never be suspended before a certaint amount of time passed
            if (minMoveTime > 0.0f && current.activateTime < minMoveTime) {
                return false
            }

            // if the simulation should always be suspended after a certain amount time passed
            if (maxMoveTime > 0.0f && current.activateTime > maxMoveTime) {
                return true
            }

            // test if all bodies hardly moved over a period of time
            if (current.noMoveTime == 0.0f) {
                i = 0
                while (i < bodies.Num()) {
                    body = bodies[i]
                    body.atRestOrigin.set(body.current.worldOrigin)
                    body.atRestAxis.set(body.current.worldAxis)
                    i++
                }
                current.noMoveTime += timeStep
            } else if (current.noMoveTime > noMoveTime) {
                current.noMoveTime = 0.0f
                maxTranslationSqr = 0.0f
                maxRotation = 0.0f
                i = 0
                while (i < bodies.Num()) {
                    body = bodies[i]
                    translationSqr = body.current.worldOrigin.minus(body.atRestOrigin).LengthSqr()
                    if (translationSqr > maxTranslationSqr) {
                        maxTranslationSqr = translationSqr
                    }
                    rotation = body.atRestAxis.Transpose().times(body.current.worldAxis).ToRotation().GetAngle()
                    if (rotation > maxRotation) {
                        maxRotation = rotation
                    }
                    i++
                }
                if (maxTranslationSqr < Square(noMoveTranslation) && maxRotation < noMoveRotation) {
                    // hardly moved over a period of time so the articulated figure may come to rest
                    return true
                }
            } else {
                current.noMoveTime += timeStep
            }

            // test if the velocity or acceleration of any body is still too large to come to rest
            i = 0
            while (i < bodies.Num()) {
                body = bodies[i]
                if (body.current.spatialVelocity.SubVec3(0).LengthSqr() > Square(suspendVelocity[0])) {
                    return false
                }
                if (body.current.spatialVelocity.SubVec3(1).LengthSqr() > Square(suspendVelocity[1])) {
                    return false
                }
                if (body.acceleration.SubVec3(0).LengthSqr() > Square(suspendAcceleration[0])) {
                    return false
                }
                if (body.acceleration.SubVec3(1).LengthSqr() > Square(suspendAcceleration[1])) {
                    return false
                }
                i++
            }

            // all bodies have a velocity and acceleration small enough to come to rest
            return true
        }

        private fun Rest() {
            var i: Int
            current.atRest = Game_local.gameLocal.time
            i = 0
            while (i < bodies.Num()) {
                bodies[i].current.spatialVelocity.Zero()
                bodies[i].current.externalForce.Zero()
                i++
            }
            self!!.BecomeInactive(TH_PHYSICS)
        }

        private fun AddPushVelocity(pushVelocity: idVec6) {
            var i: Int
            if (pushVelocity != getVec6_origin()) {
                i = 0
                while (i < bodies.Num()) {
                    bodies[i].current.spatialVelocity.plusAssign(pushVelocity)
                    i++
                }
            }
        }

        private fun DebugDraw() {
            var i: Int
            var body: idAFBody?
            var highlightBody: idAFBody? = null
            var constrainedBody1: idAFBody? = null
            var constrainedBody2: idAFBody? = null
            var constraint: idAFConstraint?
            val center = idVec3()
            val axis = idMat3()
            if (!SysCvar.af_highlightConstraint.GetString().isNullOrEmpty()) {
                constraint = GetConstraint(SysCvar.af_highlightConstraint.GetString()!!)
                if (constraint != null) {
                    constraint.GetCenter(center)
                    axis.set(Game_local.gameLocal.GetLocalPlayer()!!.viewAngles.ToMat3())
                    Game_local.gameRenderWorld!!.DebugCone(
                        colorYellow,
                        center,
                        axis[2].minus(axis[1]).times(4.0f),
                        0.0f,
                        1.0f,
                        0
                    )
                    if (SysCvar.af_showConstrainedBodies.GetBool()) {
                        idLib.cvarSystem.SetCVarString("cm_drawColor", colorCyan.ToString(0))
                        constrainedBody1 = constraint.body1
                        if (constrainedBody1 != null) {
                            collisionModelManager.DrawModel(
                                constrainedBody1.clipModel!!.Handle(), constrainedBody1.clipModel!!.GetOrigin(),
                                constrainedBody1.clipModel!!.GetAxis(), getVec3Origin(), 0.0f
                            )
                        }
                        idLib.cvarSystem.SetCVarString("cm_drawColor", colorBlue.ToString(0))
                        constrainedBody2 = constraint.body2
                        if (constrainedBody2 != null) {
                            collisionModelManager.DrawModel(
                                constrainedBody2.clipModel!!.Handle(), constrainedBody2.clipModel!!.GetOrigin(),
                                constrainedBody2.clipModel!!.GetAxis(), getVec3Origin(), 0.0f
                            )
                        }
                        idLib.cvarSystem.SetCVarString("cm_drawColor", colorRed.ToString(0))
                    }
                }
            }
            if (!SysCvar.af_highlightBody.GetString().isNullOrEmpty()) {
                highlightBody = GetBody(SysCvar.af_highlightBody.GetString()!!)
                if (highlightBody != null) {
                    idLib.cvarSystem.SetCVarString("cm_drawColor", colorYellow.ToString(0))
                    collisionModelManager.DrawModel(
                        highlightBody.clipModel!!.Handle(), highlightBody.clipModel!!.GetOrigin(),
                        highlightBody.clipModel!!.GetAxis(), getVec3Origin(), 0.0f
                    )
                    idLib.cvarSystem.SetCVarString("cm_drawColor", colorRed.ToString(0))
                }
            }
            if (SysCvar.af_showBodies.GetBool()) {
                i = 0
                while (i < bodies.Num()) {
                    body = bodies[i]
                    if (body === constrainedBody1 || body === constrainedBody2) {
                        i++
                        continue
                    }
                    if (body === highlightBody) {
                        i++
                        continue
                    }
                    collisionModelManager.DrawModel(
                        body.clipModel!!.Handle(), body.clipModel!!.GetOrigin(),
                        body.clipModel!!.GetAxis(), getVec3Origin(), 0.0f
                    )
                    DrawTraceModelSilhouette(
                        Game_local.gameLocal.GetLocalPlayer()!!.GetEyePosition(),
                        body!!.clipModel!!
                    )
                    i++
                }
            }
            if (SysCvar.af_showBodyNames.GetBool()) {
                i = 0
                while (i < bodies.Num()) {
                    body = bodies[i]
                    Game_local.gameRenderWorld!!.DrawText(
                        body.GetName().toString(),
                        body.GetWorldOrigin(),
                        0.08f,
                        colorCyan,
                        Game_local.gameLocal.GetLocalPlayer()!!.viewAngles.ToMat3(),
                        1
                    )
                    i++
                }
            }
            if (SysCvar.af_showMass.GetBool()) {
                i = 0
                while (i < bodies.Num()) {
                    body = bodies[i]
                    Game_local.gameRenderWorld!!.DrawText(
                        Str.va("\n%1.2f", 1.0f / body.GetInverseMass()),
                        body.GetWorldOrigin(),
                        0.08f,
                        colorCyan,
                        Game_local.gameLocal.GetLocalPlayer()!!.viewAngles.ToMat3(),
                        1
                    )
                    i++
                }
            }
            if (SysCvar.af_showTotalMass.GetBool()) {
                axis.set(Game_local.gameLocal.GetLocalPlayer()!!.viewAngles.ToMat3())
                Game_local.gameRenderWorld!!.DrawText(
                    Str.va("\n%1.2f", totalMass),
                    bodies[0].GetWorldOrigin().plus(axis[2].times(8.0f)),
                    0.15f,
                    colorCyan,
                    axis,
                    1
                )
            }
            if (SysCvar.af_showInertia.GetBool()) {
                i = 0
                while (i < bodies.Num()) {
                    body = bodies[i]
                    val I = body.inertiaTensor
                    Game_local.gameRenderWorld!!.DrawText(
                        Str.va(
                            "\n\n\n( %.1f %.1f %.1f )\n( %.1f %.1f %.1f )\n( %.1f %.1f %.1f )",
                            I[0].x, I[0].y, I[0].z,
                            I[1].x, I[1].y, I[1].z,
                            I[2].x, I[2].y, I[2].z
                        ),
                        body.GetWorldOrigin(),
                        0.05f,
                        colorCyan,
                        Game_local.gameLocal.GetLocalPlayer()!!.viewAngles.ToMat3(),
                        1
                    )
                    i++
                }
            }
            if (SysCvar.af_showVelocity.GetBool()) {
                i = 0
                while (i < bodies.Num()) {
                    DrawVelocity(bodies[i].clipModel!!.GetId(), 0.1f, 4.0f)
                    i++
                }
            }
            if (SysCvar.af_showConstraints.GetBool()) {
                i = 0
                while (i < primaryConstraints.Num()) {
                    constraint = primaryConstraints[i]
                    constraint.DebugDraw()
                    i++
                }
                if (!SysCvar.af_showPrimaryOnly.GetBool()) {
                    i = 0
                    while (i < auxiliaryConstraints.Num()) {
                        constraint = auxiliaryConstraints[i]
                        constraint.DebugDraw()
                        i++
                    }
                }
            }
            if (SysCvar.af_showConstraintNames.GetBool()) {
                i = 0
                while (i < primaryConstraints.Num()) {
                    constraint = primaryConstraints[i]
                    constraint.GetCenter(center)
                    Game_local.gameRenderWorld!!.DrawText(
                        constraint.GetName().toString(),
                        center,
                        0.08f,
                        colorCyan,
                        Game_local.gameLocal.GetLocalPlayer()!!.viewAngles.ToMat3(),
                        1
                    )
                    i++
                }
                if (!SysCvar.af_showPrimaryOnly.GetBool()) {
                    i = 0
                    while (i < auxiliaryConstraints.Num()) {
                        constraint = auxiliaryConstraints[i]
                        constraint.GetCenter(center)
                        Game_local.gameRenderWorld!!.DrawText(
                            constraint.GetName().toString(),
                            center,
                            0.08f,
                            colorCyan,
                            Game_local.gameLocal.GetLocalPlayer()!!.viewAngles.ToMat3(),
                            1
                        )
                        i++
                    }
                }
            }
            if (SysCvar.af_showTrees.GetBool() || SysCvar.af_showActive.GetBool() && current.atRest < 0) {
                i = 0
                while (i < trees.Num()) {
                    trees[i].DebugDraw(idStr.ColorForIndex(i + 3))
                    i++
                }
            }
        }

        private fun DrawTraceModelSilhouette(projectionOrigin: idVec3, clipModel: idClipModel) {
            var i: Int
            val numSilEdges: Int
            val silEdges = IntArray(TraceModel.MAX_TRACEMODEL_EDGES)
            val v1 = idVec3()
            val v2 = idVec3()
            val trm = clipModel.GetTraceModel()!!
            val origin = clipModel.GetOrigin()
            val axis = clipModel.GetAxis()
            numSilEdges =
                trm.GetProjectionSilhouetteEdges(projectionOrigin.minus(origin).times(axis.Transpose()), silEdges)
            i = 0
            while (i < numSilEdges) {
                v1.set(trm.verts[trm.edges[abs(silEdges[i])].v[INTSIGNBITSET(silEdges[i])]])
                v2.set(trm.verts[trm.edges[abs(silEdges[i])].v[INTSIGNBITNOTSET(silEdges[i])]])
                Game_local.gameRenderWorld!!.DebugArrow(
                    colorRed,
                    origin.plus(v1.times(axis)),
                    origin.plus(v2.times(axis)),
                    1
                )
                i++
            }
        }

        override fun oSet(oGet: idClass?) {
        }

        companion object {
            const val AF_FORCE_MAX = 1e20f
            val AF_FORCE_EXPONENT_BITS = idMath.BitsForInteger(idMath.BitsForFloat(AF_FORCE_MAX)) + 1
            const val AF_FORCE_TOTAL_BITS = 16
            val AF_FORCE_MANTISSA_BITS = AF_FORCE_TOTAL_BITS - 1 - AF_FORCE_EXPONENT_BITS
            const val AF_VELOCITY_MAX = 16000.0f
            val AF_VELOCITY_EXPONENT_BITS = idMath.BitsForInteger(idMath.BitsForFloat(AF_VELOCITY_MAX)) + 1
            const val AF_VELOCITY_TOTAL_BITS = 16
            val AF_VELOCITY_MANTISSA_BITS = AF_VELOCITY_TOTAL_BITS - 1 - AF_VELOCITY_EXPONENT_BITS

            /*
         ================
         idPhysics_AF::CheckForCollisions

         check for collisions between the current and next state
         if there is a collision the next state is set to the state at the moment of impact
         assumes all bodies are linked for collision detection and relinks all bodies after moving them
         ================
         */
            private val absBounds: idBounds = idBounds()
            private val relBounds: idBounds = idBounds()
        }

        // CLASS_PROTOTYPE( idPhysics_AF );
        init {
            trees = idList()
            bodies = idList()
            constraints = idList()
            primaryConstraints = idList()
            auxiliaryConstraints = idList()
            frameConstraints = idList()
            contactConstraints = idList()
            contactBodies = idList()
            contacts.Clear()
            collisions = idList()
            changedAF = true
            masterBody = null
            lcp = idLCP.AllocSymmetric()
            current = AFPState_s() //memset( &current, 0, sizeof( current ) );
            current.atRest = -1
            current.lastTimeStep = UsercmdGen.USERCMD_MSEC.toFloat()
            // FIX: C++ struct assignment does value copy; Kotlin = creates reference alias
            saved = AFPState_s()
            saved.set(current)
            linearFriction = 0.005f
            angularFriction = 0.005f
            contactFriction = 0.8f
            bouncyness = 0.4f
            totalMass = 0.0f
            forceTotalMass = -1.0f
            suspendVelocity.set(SUSPEND_LINEAR_VELOCITY, SUSPEND_ANGULAR_VELOCITY)
            suspendAcceleration.set(SUSPEND_LINEAR_ACCELERATION, SUSPEND_LINEAR_ACCELERATION)
            noMoveTime = NO_MOVE_TIME
            noMoveTranslation = NO_MOVE_TRANSLATION_TOLERANCE
            noMoveRotation = NO_MOVE_ROTATION_TOLERANCE
            minMoveTime = MIN_MOVE_TIME
            maxMoveTime = MAX_MOVE_TIME
            impulseThreshold = IMPULSE_THRESHOLD
            timeScale = 1.0f
            timeScaleRampStart = 0.0f
            timeScaleRampEnd = 0.0f
            jointFrictionScale = 0.0f
            jointFrictionDent = 0.0f
            jointFrictionDentStart = 0.0f
            jointFrictionDentEnd = 0.0f
            jointFrictionDentScale = 0.0f
            contactFrictionScale = 0.0f
            contactFrictionDent = 0.0f
            contactFrictionDentStart = 0.0f
            contactFrictionDentEnd = 0.0f
            contactFrictionDentScale = 0.0f
            enableCollision = true
            selfCollision = true
            comeToRest = true
            linearTime = true
            noImpact = false
            worldConstraintsLocked = false
            forcePushable = false
            if (AF_TIMINGS) {
                lastTimerReset = 0
            }
        }
    }
}
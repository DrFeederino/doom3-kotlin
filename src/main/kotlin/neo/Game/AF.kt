package neo.Game

import neo.Game.Animation.Anim.AFJointModType_t
import neo.Game.Animation.Anim_Blend.idAnimator
import neo.Game.Animation.Anim_Blend.idDeclModelDef
import neo.Game.Entity.idEntity
import neo.Game.GameSys.SaveGame.idRestoreGame
import neo.Game.GameSys.SaveGame.idSaveGame
import neo.Game.GameSys.SysCvar
import neo.Game.Game_local.idGameLocal
import neo.Game.Physics.Clip
import neo.Game.Physics.Clip.idClipModel
import neo.Game.Physics.Physics.impactInfo_s
import neo.Game.Physics.Physics_AF.constraintType_t
import neo.Game.Physics.Physics_AF.idAFBody
import neo.Game.Physics.Physics_AF.idAFConstraint
import neo.Game.Physics.Physics_AF.idAFConstraint_BallAndSocketJoint
import neo.Game.Physics.Physics_AF.idAFConstraint_Fixed
import neo.Game.Physics.Physics_AF.idAFConstraint_Hinge
import neo.Game.Physics.Physics_AF.idAFConstraint_Slider
import neo.Game.Physics.Physics_AF.idAFConstraint_Spring
import neo.Game.Physics.Physics_AF.idAFConstraint_UniversalJoint
import neo.Game.Physics.Physics_AF.idPhysics_AF
import neo.Renderer.Model
import neo.Renderer.Model.idRenderModel
import neo.Renderer.RenderWorld.renderEntity_s
import neo.cm.trace_s
import neo.framework.DeclAF.*
import neo.framework.DeclManager
import neo.framework.DeclManager.declState_t
import neo.framework.DeclManager.declType_t
import neo.idlib.BV.idBounds
import neo.idlib.Dict_h.idDict
import neo.idlib.Dict_h.idKeyValue
import neo.idlib.Text.Lexer.idLexer
import neo.idlib.Text.Str.idStr
import neo.idlib.Text.Token.idToken
import neo.idlib.containers.CFloat
import neo.idlib.containers.List.idList
import neo.idlib.geometry.JointTransform.idJointMat
import neo.idlib.geometry.TraceModel.idTraceModel
import neo.idlib.geometry.TraceModel.traceModel_t
import neo.idlib.math.*
import neo.idlib.math.Matrix.idMat3
import java.util.*
import kotlin.math.abs

object AF {
    val ARTICULATED_FIGURE_ANIM: String = "af_pose"
    const val POSE_BOUNDS_EXPANSION = 5.0f

    /*
     ===============================================================================

     Articulated Figure controller.

     ===============================================================================
     */
    class jointConversion_s {
        val jointBodyOrigin: idVec3 = idVec3() // origin of body relative to joint
        var bodyId // id of the body
                = 0
        val jointBodyAxis // axis of body relative to joint
                : idMat3 = idMat3()
        var   /*jointHandle_t*/jointHandle // handle of joint this body modifies
                = 0
        var jointMod // modify joint axis, origin or both
                : AFJointModType_t = AFJointModType_t.AF_JOINTMOD_AXIS
    }

    class afTouch_s {
        lateinit var touchedByBody: idAFBody
        lateinit var touchedClipModel: idClipModel
        lateinit var touchedEnt: idEntity
    }

    //
    class idAF {
        protected val baseOrigin // offset of base body relative to skeletal model origin
                : idVec3
        protected var animator // animator on entity
                : idAnimator?
        protected val baseAxis // axis of base body relative to skeletal model origin
                : idMat3
        protected var hasBindConstraints // true if the bind constraints have been added
                : Boolean
        protected var isActive // true if the articulated figure physics is active
                : Boolean
        protected var isLoaded // true when the articulated figure is properly loaded
                : Boolean
        protected val jointBody // table to find the nearest articulated figure body for a joint of the skeletal model
                : idList<Int>
        protected val jointMods // list with transforms from skeletal model joints to articulated figure bodies
                : idList<jointConversion_s>
        protected var modifiedAnim // anim to modify
                : Int
        protected val name // name of the loaded .af file
                : idStr
        protected var physicsObj // articulated figure physics
                : idPhysics_AF
        protected var poseTime // last time the articulated figure was transformed to reflect the current animation pose
                : Int
        protected var restStartTime // time the articulated figure came to rest
                : Int
        protected var self // entity using the animated model
                : idEntity?

        fun Save(savefile: idSaveGame) {
            savefile.WriteObject(self)
            savefile.WriteString(GetName())
            savefile.WriteBool(hasBindConstraints)
            savefile.WriteVec3(baseOrigin)
            savefile.WriteMat3(baseAxis)
            savefile.WriteInt(poseTime)
            savefile.WriteInt(restStartTime)
            savefile.WriteBool(isLoaded)
            savefile.WriteBool(isActive)
            savefile.WriteStaticObject(physicsObj)
        }

        fun Restore(savefile: idRestoreGame) {
            savefile.ReadObject(self)
            savefile.ReadString(name)
            hasBindConstraints = savefile.ReadBool()
            savefile.ReadVec3(baseOrigin)
            savefile.ReadMat3(baseAxis)
            poseTime = savefile.ReadInt()
            restStartTime = savefile.ReadInt()
            isLoaded = savefile.ReadBool()
            isActive = savefile.ReadBool()
            animator = null
            modifiedAnim = 0
            if (self != null) {
                SetAnimator(self!!.GetAnimator())
                Load(self!!, name.toString())
                if (hasBindConstraints) {
                    AddBindConstraints()
                }
            }
            savefile.ReadStaticObject(physicsObj)
            if (self != null) {
                if (isActive) {
                    // clear all animations
                    animator!!.ClearAllAnims(Game_local.gameLocal.time, 0)
                    animator!!.ClearAllJoints()

                    // switch to articulated figure physics
                    self!!.RestorePhysics(physicsObj)
                    physicsObj.EnableClip()
                }
                UpdateAnimation()
            }
        }

        fun SetAnimator(a: idAnimator?) {
            animator = a
        }

        fun Load(ent: idEntity, fileName: String): Boolean {
            var i: Int
            var j: Int
            val file: idDeclAF?
            val modelDef: idDeclModelDef?
            val model: idRenderModel?
            val numJoints: Int
            val joints: Array<idJointMat>
            assert(ent != null)
            self = ent
            physicsObj.SetSelf(self!!)
            if (animator == null) {
                Game_local.gameLocal.Warning(
                    "Couldn't load af '%s' for entity '%s' at (%s): NULL animator\n",
                    name,
                    ent.name,
                    ent.GetPhysics().GetOrigin().ToString(0)
                )
                return false
            }
            name.set(fileName)
            name.StripFileExtension()
            file = DeclManager.declManager.FindType(declType_t.DECL_AF, name) as idDeclAF?
            if (null == file) {
                Game_local.gameLocal.Warning(
                    "Couldn't load af '%s' for entity '%s' at (%s)\n",
                    name,
                    ent.name,
                    ent.GetPhysics().GetOrigin().ToString(0)
                )
                return false
            }
            if (file.bodies.Num() == 0 || file.bodies[0].jointName.toString() != "origin") {
                Game_local.gameLocal.Warning(
                    "idAF::Load: articulated figure '%s' for entity '%s' at (%s) has no body which modifies the origin joint.",
                    name.toString(), ent.name.toString(), ent.GetPhysics().GetOrigin().ToString(0)
                )
                return false
            }
            modelDef = animator!!.ModelDef()
            if (modelDef == null || modelDef.GetState() == declState_t.DS_DEFAULTED) {
                Game_local.gameLocal.Warning(
                    "idAF::Load: articulated figure '%s' for entity '%s' at (%s) has no or defaulted modelDef '%s'",
                    name.toString(),
                    ent.name.toString(),
                    ent.GetPhysics().GetOrigin().ToString(0),
                    if (modelDef != null) modelDef.GetName() else ""
                )
                return false
            }
            model = animator!!.ModelHandle()
            if (model == null || model.IsDefaultModel()) {
                Game_local.gameLocal.Warning(
                    "idAF::Load: articulated figure '%s' for entity '%s' at (%s) has no or defaulted model '%s'",
                    name.toString(),
                    ent.name.toString(),
                    ent.GetPhysics().GetOrigin().ToString(0),
                    if (model != null) model.Name() else ""
                )
                return false
            }

            // get the modified animation
            modifiedAnim = animator!!.GetAnim(ARTICULATED_FIGURE_ANIM)
            if (0 == modifiedAnim) {
                Game_local.gameLocal.Warning(
                    "idAF::Load: articulated figure '%s' for entity '%s' at (%s) has no modified animation '%s'",
                    name, ent.name, ent.GetPhysics().GetOrigin().ToString(0), ARTICULATED_FIGURE_ANIM
                )
                return false
            }

            // create the animation frame used to setup the articulated figure
            numJoints = animator!!.NumJoints()
            joints = Array<idJointMat>(numJoints) { idJointMat() }
            GameEdit.gameEdit.ANIM_CreateAnimFrame(
                model,
                animator!!.GetAnim(modifiedAnim)!!.MD5Anim(0),
                numJoints,
                joints as Array<idJointMat?>,
                1,
                animator!!.ModelDef()!!.GetVisualOffset(),
                animator!!.RemoveOrigin()
            )

            // set all vector positions from model joints
            file.Finish(GetJointTransform.INSTANCE, joints, animator!!)

            // initialize articulated figure physics
            physicsObj.SetGravity(Game_local.gameLocal.GetGravity())
            physicsObj.SetClipMask(file.clipMask._val)
            physicsObj.SetDefaultFriction(
                file.defaultLinearFriction,
                file.defaultAngularFriction,
                file.defaultContactFriction
            )
            physicsObj.SetSuspendSpeed(file.suspendVelocity, file.suspendAcceleration)
            physicsObj.SetSuspendTolerance(file.noMoveTime, file.noMoveTranslation, file.noMoveRotation)
            physicsObj.SetSuspendTime(file.minMoveTime, file.maxMoveTime)
            physicsObj.SetSelfCollision(file.selfCollision)

            // clear the list with transforms from joints to bodies
            jointMods.SetNum(0, false)

            // clear the joint to body conversion list
            jointBody.AssureSize(animator!!.NumJoints())
            i = 0
            while (i < jointBody.Num()) {
                jointBody[i] = -1
                i++
            }

            // delete any bodies in the physicsObj that are no longer in the idDeclAF
            i = 0
            while (i < physicsObj.GetNumBodies()) {
                val body = physicsObj.GetBody(i)!!
                j = 0
                while (j < file.bodies.Num()) {
                    if (file.bodies[j].name.Icmp(body.GetName()) == 0) {
                        break
                    }
                    j++
                }
                if (j >= file.bodies.Num()) {
                    physicsObj.DeleteBody(i)
                    i--
                }
                i++
            }

            // delete any constraints in the physicsObj that are no longer in the idDeclAF
            i = 0
            while (i < physicsObj.GetNumConstraints()) {
                val constraint = physicsObj.GetConstraint(i)!!
                j = 0
                while (j < file.constraints.Num()) {
                    // DG: FIXME: GCC rightfully complains that file->constraints[j]->type and constraint->GetType()
                    //  are of different enum types, and their values are different in some cases:
                    //  CONSTRAINT_HINGESTEERING has no DECLAF_CONSTRAINT_ equivalent,
                    //  and thus DECLAF_CONSTRAINT_SLIDER != CONSTRAINT_SLIDER (5 != 6)
                    //  and DECLAF_CONSTRAINT_SPRING != CONSTRAINT_SPRING (6 != 10)
                    if (file.constraints[j].name.Icmp(constraint.GetName()) == 0 &&
                        file.constraints[j].type.ordinal == constraint.GetType().ordinal
                    ) {
                        break
                    }
                    j++
                }
                if (j >= file.constraints.Num()) {
                    physicsObj.DeleteConstraint(i)
                    i--
                }
                i++
            }

            // load bodies from the file
            i = 0
            while (i < file.bodies.Num()) {
                LoadBody(file.bodies[i], joints)
                i++
            }

            // load constraints from the file
            i = 0
            while (i < file.constraints.Num()) {
                LoadConstraint(file.constraints[i])
                i++
            }
            physicsObj.UpdateClipModels()

            // check if each joint is contained by a body
            i = 0
            while (i < animator!!.NumJoints()) {
                if (jointBody[i] == -1) {
                    /*jointHandle_t*/
                    Game_local.gameLocal.Warning(
                        "idAF::Load: articulated figure '%s' for entity '%s' at (%s) joint '%s' is not contained by a body",
                        name, self!!.name, self!!.GetPhysics().GetOrigin().ToString(0), animator!!.GetJointName(i)
                    )
                }
                i++
            }
            physicsObj.SetMass(file.totalMass)
            physicsObj.SetChanged()

            // disable the articulated figure for collision detection until activated
            physicsObj.DisableClip()
            isLoaded = true
            return true
        }

        fun Load(ent: idEntity, fileName: idStr): Boolean {
            return Load(ent, fileName.toString())
        }

        fun IsLoaded(): Boolean {
            return isLoaded && self != null
        }

        fun GetName(): String {
            return name.toString()
        }

        /*
         ================
         idAF::SetupPose

         Transforms the articulated figure to match the current animation pose of the given entity.
         ================
         */
        fun SetupPose(ent: idEntity?, time: Int) {
            var i: Int
            var body: idAFBody?
            val origin = idVec3()
            val axis = idMat3()
            val animatorPtr: idAnimator?
            val renderEntity: renderEntity_s?
            if (!IsLoaded() || null == ent) {
                return
            }
            animatorPtr = ent.GetAnimator()
            if (animatorPtr == null) {
                return
            }
            renderEntity = ent.GetRenderEntity()
            if (null == renderEntity) {
                return
            }

            // if the animation is driven by the physics
            if (self!!.GetPhysics() == physicsObj) {
                return
            }

            // if the pose was already updated this frame
            if (poseTime == time) {
                return
            }
            poseTime = time
            i = 0
            while (i < jointMods.Num()) {
                body = physicsObj.GetBody(jointMods[i].bodyId)!!
                animatorPtr.GetJointTransform(jointMods[i].jointHandle, time, origin, axis)
                body.SetWorldOrigin(
                    renderEntity.origin.plus(
                        origin.plus(
                            jointMods[i].jointBodyOrigin.times(
                                axis
                            )
                        ).times(renderEntity.axis)
                    )
                )
                body.SetWorldAxis(jointMods[i].jointBodyAxis.times(axis).times(renderEntity.axis))
                i++
            }
            if (isActive) {
                physicsObj.UpdateClipModels()
            }
        }

        /*
         ================
         idAF::ChangePose

         Change the articulated figure to match the current animation pose of the given entity
         and set the velocity relative to the previous pose.
         ================
         */
        fun ChangePose(ent: idEntity?, time: Int) {
            var i: Int
            val invDelta: Float
            var body: idAFBody
            val origin = idVec3()
            val lastOrigin = idVec3()
            val axis = idMat3()
            val animatorPtr: idAnimator?
            val renderEntity: renderEntity_s?
            if (!IsLoaded() || null == ent) {
                return
            }
            animatorPtr = ent.GetAnimator()
            if (null == animatorPtr) {
                return
            }
            renderEntity = ent.GetRenderEntity()
            if (renderEntity == null) {
                return
            }

            // if the animation is driven by the physics
            if (self!!.GetPhysics() == physicsObj) {
                return
            }

            // if the pose was already updated this frame
            if (poseTime == time) {
                return
            }
            invDelta = 1.0f / MS2SEC((time - poseTime).toFloat())
            poseTime = time
            i = 0
            while (i < jointMods.Num()) {
                body = physicsObj.GetBody(jointMods[i].bodyId)!!
                animatorPtr.GetJointTransform(jointMods[i].jointHandle, time, origin, axis)
                lastOrigin.set(body.GetWorldOrigin())
                body.SetWorldOrigin(
                    renderEntity.origin.plus(
                        origin.plus(
                            jointMods[i].jointBodyOrigin.times(
                                axis
                            )
                        ).times(renderEntity.axis)
                    )
                )
                body.SetWorldAxis(jointMods[i].jointBodyAxis.times(axis).times(renderEntity.axis))
                body.SetLinearVelocity(body.GetWorldOrigin().minus(lastOrigin).times(invDelta))
                i++
            }
            physicsObj.UpdateClipModels()
        }

        fun EntitiesTouchingAF(touchList: Array<afTouch_s> /*[ MAX_GENTITIES ]*/): Int {
            var i: Int
            var j: Int
            val numClipModels: Int
            var body: idAFBody?
            var cm: idClipModel?
            val clipModels = arrayOfNulls<idClipModel>(Game_local.MAX_GENTITIES)
            var numTouching: Int
            if (!IsLoaded()) {
                return 0
            }
            numTouching = 0
            numClipModels = Game_local.gameLocal.clip.ClipModelsTouchingBounds(
                physicsObj.GetAbsBounds(),
                -1,
                clipModels,
                Game_local.MAX_GENTITIES
            )
            i = 0
            while (i < jointMods.Num()) {
                body = physicsObj.GetBody(jointMods[i].bodyId)!!
                j = 0
                while (j < numClipModels) {
                    cm = clipModels[j]
                    if (cm == null || cm.GetEntity() == self) {
                        j++
                        continue
                    }
                    if (!cm.IsTraceModel()) {
                        j++
                        continue
                    }
                    if (!body.GetClipModel()!!.GetAbsBounds().IntersectsBounds(cm.GetAbsBounds())) {
                        j++
                        continue
                    }
                    if (Game_local.gameLocal.clip.ContentsModel(
                            body.GetWorldOrigin(),
                            body.GetClipModel(),
                            body.GetWorldAxis(),
                            -1,
                            cm.Handle(),
                            cm.GetOrigin(),
                            cm.GetAxis()
                        ) != 0
                    ) {
                        touchList[numTouching].touchedByBody = body
                        touchList[numTouching].touchedClipModel = cm
                        touchList[numTouching].touchedEnt = cm.GetEntity()!!
                        numTouching++
                        clipModels[j] = null
                    }
                    j++
                }
                i++
            }
            return numTouching
        }

        fun Start() {
            if (!IsLoaded()) {
                return
            }
            // clear all animations
            animator!!.ClearAllAnims(Game_local.gameLocal.time, 0)
            animator!!.ClearAllJoints()
            // switch to articulated figure physics
            self!!.SetPhysics(physicsObj)
            // start the articulated figure physics simulation
            physicsObj.EnableClip()
            physicsObj.Activate()
            isActive = true
        }

        fun StartFromCurrentPose(inheritVelocityTime: Int) {
            if (!IsLoaded()) {
                return
            }

            // if the ragdoll should inherit velocity from the animation
            if (inheritVelocityTime > 0) {

                // make sure the ragdoll is at rest
                physicsObj.PutToRest()

                // set the pose for some time back
                SetupPose(self, Game_local.gameLocal.time - inheritVelocityTime)

                // change the pose for the current time and set velocities
                ChangePose(self, Game_local.gameLocal.time)
            } else {
                // transform the articulated figure to reflect the current animation pose
                SetupPose(self, Game_local.gameLocal.time)
            }
            physicsObj.UpdateClipModels()
            TestSolid()
            Start()
            UpdateAnimation()

            // update the render entity origin and axis
            self!!.UpdateModel()

            // make sure the renderer gets the updated origin and axis
            self!!.Present()
        }

        fun Stop() {
            // disable the articulated figure for collision detection
            physicsObj.UnlinkClip()
            isActive = false
        }

        fun Rest() {
            physicsObj.PutToRest()
        }

        fun IsActive(): Boolean {
            return isActive
        }

        /*
         ================
         idAF::SetConstraintPosition

         Only moves constraints that bind the entity to another entity.
         ================
         */
        fun SetConstraintPosition(name: String, pos: idVec3) {
            val constraint: idAFConstraint?
            constraint = GetPhysics().GetConstraint(name)
            if (null == constraint) {
                Game_local.gameLocal.Warning("can't find a constraint with the name '%s'", name)
                return
            }
            if (constraint.GetBody2() != null) {
                Game_local.gameLocal.Warning("constraint '%s' does not bind to another entity", name)
                return
            }
            when (constraint.GetType()) {
                constraintType_t.CONSTRAINT_BALLANDSOCKETJOINT -> {
                    val bs = constraint as idAFConstraint_BallAndSocketJoint
                    bs.Translate(pos.minus(bs.GetAnchor()))
                }

                constraintType_t.CONSTRAINT_UNIVERSALJOINT -> {
                    val uj = constraint as idAFConstraint_UniversalJoint
                    uj.Translate(pos.minus(uj.GetAnchor()))
                }

                constraintType_t.CONSTRAINT_HINGE -> {
                    val hinge = constraint as idAFConstraint_Hinge
                    hinge.Translate(pos.minus(hinge.GetAnchor()))
                }

                else -> {
                    Game_local.gameLocal.Warning("cannot set the constraint position for '%s'", name)
                }
            }
        }

        fun GetPhysics(): idPhysics_AF {
            return physicsObj
        }

        /*
         ================
         idAF::GetBounds

         returns bounds for the current pose
         ================
         */
        fun GetBounds(): idBounds {
            var i: Int
            var body: idAFBody?
            val origin = idVec3()
            val entityOrigin = idVec3()
            val axis = idMat3()
            val entityAxis: idMat3
            val bounds = idBounds()
            val b = idBounds()
            bounds.Clear()

            // get model base transform
            origin.set(physicsObj.GetOrigin(0))
            axis.set(physicsObj.GetAxis(0))
            entityAxis = baseAxis.Transpose().times(axis)
            entityOrigin.set(origin.minus(baseOrigin.times(entityAxis)))

            // get bounds relative to base
            i = 0
            while (i < jointMods.Num()) {
                body = physicsObj.GetBody(jointMods[i].bodyId)!!
                origin.set(body.GetWorldOrigin().minus(entityOrigin).times(entityAxis.Transpose()))
                axis.set(body.GetWorldAxis().times(entityAxis.Transpose()))
                b.FromTransformedBounds(body.GetClipModel()!!.GetBounds(), origin, axis)
                bounds.timesAssign(b)
                i++
            }
            return bounds
        }

        fun UpdateAnimation(): Boolean {
            var i: Int
            val origin = idVec3()
            val renderOrigin = idVec3()
            val bodyOrigin = idVec3()
            val axis = idMat3()
            val renderAxis: idMat3 = idMat3()
            val bodyAxis = idMat3()
            val renderEntity: renderEntity_s?
            if (!IsLoaded()) {
                return false
            }
            if (!IsActive()) {
                return false
            }
            renderEntity = self!!.GetRenderEntity()
            if (null == renderEntity) {
                return false
            }
            if (physicsObj.IsAtRest()) {
                if (restStartTime == physicsObj.GetRestStartTime()) {
                    return false
                }
                restStartTime = physicsObj.GetRestStartTime()
            }

            // get the render position
            origin.set(physicsObj.GetOrigin(0))
            axis.set(physicsObj.GetAxis(0))
            renderAxis.set(baseAxis.Transpose() * axis)
            renderOrigin.set(origin - baseOrigin * renderAxis)

            // create an animation frame which reflects the current pose of the articulated figure
            animator!!.InitAFPose()
            i = 0
            while (i < jointMods.Num()) {

                // check for the origin joint
                if (jointMods[i].jointHandle == 0) {
                    i++
                    continue
                }
                bodyOrigin.set(physicsObj.GetOrigin(jointMods[i].bodyId))
                bodyAxis.set(physicsObj.GetAxis(jointMods[i].bodyId))
                axis.set(jointMods[i].jointBodyAxis.Transpose() * (bodyAxis * renderAxis.Transpose()))
                origin.set((bodyOrigin - jointMods[i].jointBodyOrigin * axis - renderOrigin) * renderAxis.Transpose())
                animator!!.SetAFPoseJointMod(jointMods[i].jointHandle, jointMods[i].jointMod, axis, origin)
                i++
            }
            animator!!.FinishAFPose(
                modifiedAnim,
                GetBounds().Expand(POSE_BOUNDS_EXPANSION),
                Game_local.gameLocal.time
            )
            animator!!.SetAFPoseBlendWeight(1.0f)
            return true
        }

        fun GetPhysicsToVisualTransform(origin: idVec3, axis: idMat3) {
            origin.set(baseOrigin.unaryMinus())
            axis.set(baseAxis.Transpose())
        }

        fun GetImpactInfo(ent: idEntity?, id: Int, point: idVec3): impactInfo_s {
            SetupPose(self, Game_local.gameLocal.time)
            return physicsObj.GetImpactInfo(BodyForClipModelId(id), point)
        }

        fun ApplyImpulse(ent: idEntity?, id: Int, point: idVec3, impulse: idVec3) {
            SetupPose(self, Game_local.gameLocal.time)
            physicsObj.ApplyImpulse(BodyForClipModelId(id), point, impulse)
        }

        fun AddForce(ent: idEntity?, id: Int, point: idVec3, force: idVec3) {
            SetupPose(self, Game_local.gameLocal.time)
            physicsObj.AddForce(BodyForClipModelId(id), point, force)
        }

        fun BodyForClipModelId(id: Int): Int {
            var id = id
            return if (id >= 0) {
                id
            } else {
                id = Clip.CLIPMODEL_ID_TO_JOINT_HANDLE(id)
                if (id < jointBody.Num()) {
                    jointBody[id]
                } else {
                    0
                }
            }
        }

        fun SaveState(args: idDict) {
            var i: Int
            var body: idAFBody
            var key: String
            var value: String?
            i = 0
            while (i < jointMods.Num()) {
                body = physicsObj.GetBody(jointMods[i].bodyId)!!
                key = "body " + body.GetName()
                value = body.GetWorldOrigin().ToString(8)
                value += " "
                value += body.GetWorldAxis().ToAngles().ToString(8)
                args.Set(key, value)
                i++
            }
        }

        fun LoadState(args: idDict) {
            var kv: idKeyValue?
            val name = idStr()
            var body: idAFBody?
            val origin = idVec3()
            val angles = idAngles()
            kv = args.MatchPrefix("body ", null)
            while (kv != null) {
                name.set(kv.GetKey())
                name.Strip("body ")
                body = physicsObj.GetBody(name.toString())
                if (body != null) {
                    val sscanf = Scanner(kv.GetValue().toString())
                    sscanf.useLocale(Locale.US)
                    //			sscanf( kv.GetValue(), "%f %f %f %f %f %f", &origin.x, &origin.y, &origin.z, &angles.pitch, &angles.yaw, &angles.roll );
                    origin.x = sscanf.nextFloat()
                    origin.y = sscanf.nextFloat()
                    origin.z = sscanf.nextFloat()
                    angles.pitch = sscanf.nextFloat()
                    angles.yaw = sscanf.nextFloat()
                    angles.roll = sscanf.nextFloat()
                    sscanf.close()
                    body.SetWorldOrigin(origin)
                    body.SetWorldAxis(angles.ToMat3())
                } else {
                    Game_local.gameLocal.Warning("Unknown body part %s in articulated figure %s", name, this.name)
                }
                kv = args.MatchPrefix("body ", kv)
            }
            physicsObj.UpdateClipModels()
        }

        fun AddBindConstraints() {
            var kv: idKeyValue?
            val name = idStr()
            var body: idAFBody?
            val lexer = idLexer()
            val type = idToken()
            val bodyName = idToken()
            val jointName = idToken()
            val origin = idVec3()
            val renderOrigin = idVec3()
            val axis: idMat3
            val renderAxis: idMat3
            if (!IsLoaded()) {
                return
            }
            val args = self!!.spawnArgs

            // get the render position
            origin.set(physicsObj.GetOrigin(0))
            axis = physicsObj.GetAxis(0)
            renderAxis = baseAxis.Transpose().times(axis)
            renderOrigin.set(origin.minus(baseOrigin.times(renderAxis)))

            // parse all the bind constraints
            kv = args.MatchPrefix("bindConstraint ", null)
            while (kv != null) {
                name.set(kv.GetKey())
                name.Strip("bindConstraint ")
                lexer.LoadMemory(kv.GetValue(), kv.GetValue().Length(), kv.GetKey())
                lexer.ReadToken(type)
                lexer.ReadToken(bodyName)
                body = physicsObj.GetBody(bodyName)
                if (body == null) {
                    Game_local.gameLocal.Warning(
                        "idAF::AddBindConstraints: body '%s' not found on entity '%s'",
                        bodyName,
                        self!!.name
                    )
                    lexer.FreeSource()
                    kv = args.MatchPrefix("bindConstraint ", kv)
                    continue
                }
                if (type.Icmp("fixed") == 0) {
                    var c: idAFConstraint_Fixed
                    c = idAFConstraint_Fixed(name, body, null)
                    physicsObj.AddConstraint(c)
                } else if (type.Icmp("ballAndSocket") == 0) {
                    var c: idAFConstraint_BallAndSocketJoint
                    c = idAFConstraint_BallAndSocketJoint(name, body, null)
                    physicsObj.AddConstraint(c)
                    lexer.ReadToken(jointName)
                    val   /*jointHandle_t*/joint = animator!!.GetJointHandle(jointName.toString())
                    if (joint == Model.INVALID_JOINT) {
                        Game_local.gameLocal.Warning("idAF::AddBindConstraints: joint '%s' not found", jointName)
                    }
                    animator!!.GetJointTransform(joint, Game_local.gameLocal.time, origin, axis)
                    c.SetAnchor(renderOrigin.plus(origin.times(renderAxis)))
                } else if (type.Icmp("universal") == 0) {
                    var c: idAFConstraint_UniversalJoint
                    c = idAFConstraint_UniversalJoint(name, body, null)
                    physicsObj.AddConstraint(c)
                    lexer.ReadToken(jointName)
                    val   /*jointHandle_t*/joint = animator!!.GetJointHandle(jointName)
                    if (joint == Model.INVALID_JOINT) {
                        Game_local.gameLocal.Warning("idAF::AddBindConstraints: joint '%s' not found", jointName)
                    }
                    animator!!.GetJointTransform(joint, Game_local.gameLocal.time, origin, axis)
                    c.SetAnchor(renderOrigin.plus(origin.times(renderAxis)))
                    c.SetShafts(idVec3(0, 0, 1), idVec3(0, 0, -1))
                } else {
                    Game_local.gameLocal.Warning(
                        "idAF::AddBindConstraints: unknown constraint type '%s' on entity '%s'",
                        type,
                        self!!.name
                    )
                }
                lexer.FreeSource()
                kv = args.MatchPrefix("bindConstraint ", kv)
            }
            hasBindConstraints = true
        }

        fun RemoveBindConstraints() {
            var kv: idKeyValue?
            if (!IsLoaded()) {
                return
            }
            val args = self!!.spawnArgs
            val name = idStr()
            kv = args.MatchPrefix("bindConstraint ", null)
            while (kv != null) {
                name.set(kv.GetKey())
                name.Strip("bindConstraint ")
                if (physicsObj.GetConstraint(name.toString()) != null) {
                    physicsObj.DeleteConstraint(name.toString())
                }
                kv = args.MatchPrefix("bindConstraint ", kv)
            }
            hasBindConstraints = false
        }

        /*
         ================
         idAF::SetBase

         Sets the base body.
         ================
         */
        protected fun SetBase(body: idAFBody, joints: Array<idJointMat>) {
            physicsObj.ForceBodyId(body, 0)
            baseOrigin.set(body.GetWorldOrigin())
            baseAxis.set(body.GetWorldAxis())
            AddBody(
                body,
                joints,
                animator!!.GetJointName(animator!!.GetFirstChild("origin")),
                AFJointModType_t.AF_JOINTMOD_AXIS
            )
        }

        /*
         ================
         idAF::AddBody

         Adds a body.
         ================
         */
        protected fun AddBody(
            body: idAFBody,
            joints: Array<idJointMat>,
            jointName: String,
            mod: AFJointModType_t
        ) {
            val index: Int
            val   /*jointHandle_t*/handle: Int
            val origin = idVec3()
            val axis: idMat3
            handle = animator!!.GetJointHandle(jointName)
            if (handle == Model.INVALID_JOINT) {
                idGameLocal.Error(
                    "idAF for entity '%s' at (%s) modifies unknown joint '%s'",
                    self!!.name,
                    self!!.GetPhysics().GetOrigin().ToString(0),
                    jointName
                )
            }
            assert(handle < animator!!.NumJoints())
            origin.set(joints[handle].ToVec3())
            axis = joints[handle].ToMat3()
            index = jointMods.Num()
            jointMods.SetNum(index + 1, false)
            jointMods[index] = jointConversion_s()
            jointMods[index].bodyId = physicsObj.GetBodyId(body)
            jointMods[index].jointHandle = handle
            jointMods[index].jointMod = mod
            jointMods[index].jointBodyOrigin.set(body.GetWorldOrigin().minus(origin).times(axis.Transpose()))
            jointMods[index].jointBodyAxis.set(body.GetWorldAxis().times(axis.Transpose()))
        }

        protected fun LoadBody(fb: idDeclAF_Body, joints: Array<idJointMat>): Boolean {
            val id: Int
            var i: Int
            DBG_LoadBody++
            val length: Float
            val candleMass = CFloat()
            val trm = idTraceModel()
            var clip: idClipModel
            var body: idAFBody?
            val axis: idMat3
            val inertiaTensor = idMat3()
            val centerOfMass = idVec3()
            val origin = idVec3()
            val bounds = idBounds()
            val jointList = idList<Int>()
            origin.set(fb.origin.ToVec3())
            axis = fb.angles.ToMat3()
            bounds[0] = fb.v1.ToVec3()
            bounds[1] = fb.v2.ToVec3()
            when (fb.modelType) {
                traceModel_t.TRM_BOX -> {
                    trm.SetupBox(bounds)
                }

                traceModel_t.TRM_OCTAHEDRON -> {
                    trm.SetupOctahedron(bounds)
                }

                traceModel_t.TRM_DODECAHEDRON -> {
                    trm.SetupDodecahedron(bounds)
                }

                traceModel_t.TRM_CYLINDER -> {
                    trm.SetupCylinder(bounds, fb.numSides)
                }

                traceModel_t.TRM_CONE -> {

                    // place the apex at the origin
                    bounds[0].z -= bounds[1].z
                    bounds[1].z = 0.0f
                    trm.SetupCone(bounds, fb.numSides)
                }

                traceModel_t.TRM_BONE -> {

                    // direction of bone
                    axis[2] = fb.v2.ToVec3().minus(fb.v1.ToVec3())
                    length = axis[2].Normalize()
                    // axis of bone trace model
                    axis[2].NormalVectors(axis[0], axis[1])
                    axis[1] = axis[1].unaryMinus()
                    // create bone trace model
                    trm.SetupBone(length, fb.width)
                }

                else -> assert(false)
            }
            trm.GetMassProperties(1.0f, candleMass, centerOfMass, inertiaTensor)
            trm.Translate(centerOfMass.unaryMinus())
            origin.plusAssign(centerOfMass.times(axis))
            body = physicsObj.GetBody(fb.name.toString())
            if (body != null) {
                clip = body.GetClipModel()!!
                if (!clip.IsEqual(trm)) {
                    clip = idClipModel(trm)
                    clip.SetContents(fb.contents._val)
                    clip.Link(Game_local.gameLocal.clip, self, 0, origin, axis)
                    body.SetClipModel(clip)
                }
                clip.SetContents(fb.contents._val)
                body.SetDensity(fb.density, fb.inertiaScale)
                body.SetWorldOrigin(origin)
                body.SetWorldAxis(axis)
                id = physicsObj.GetBodyId(body)
            } else {
                clip = idClipModel(trm)
                clip.SetContents(fb.contents._val)
                clip.Link(Game_local.gameLocal.clip, self, 0, origin, axis)
                body = idAFBody(fb.name, clip, fb.density)
                if (fb.inertiaScale != idMat3.getMat3_identity()) {
                    body.SetDensity(fb.density, fb.inertiaScale)
                }
                id = physicsObj.AddBody(body)
            }
            if (fb.linearFriction != -1.0f) {
                body.SetFriction(fb.linearFriction, fb.angularFriction, fb.contactFriction)
            }
            body.SetClipMask(fb.clipMask._val)
            body.SetSelfCollision(fb.selfCollision)
            if (fb.jointName.toString() == "origin") {
                SetBase(body, joints)
            } else {
                val mod: AFJointModType_t
                mod = if (fb.jointMod == declAFJointMod_t.DECLAF_JOINTMOD_AXIS) {
                    AFJointModType_t.AF_JOINTMOD_AXIS
                } else if (fb.jointMod == declAFJointMod_t.DECLAF_JOINTMOD_ORIGIN) {
                    AFJointModType_t.AF_JOINTMOD_ORIGIN
                } else if (fb.jointMod == declAFJointMod_t.DECLAF_JOINTMOD_BOTH) {
                    AFJointModType_t.AF_JOINTMOD_BOTH
                } else {
                    AFJointModType_t.AF_JOINTMOD_AXIS
                }
                AddBody(body, joints, fb.jointName.toString(), mod)
            }
            if (fb.frictionDirection.ToVec3() != getVec3Origin()) {
                body.SetFrictionDirection(fb.frictionDirection.ToVec3())
            }
            if (fb.contactMotorDirection.ToVec3() != getVec3Origin()) {
                body.SetContactMotorDirection(fb.contactMotorDirection.ToVec3())
            }

            // update table to find the nearest articulated figure body for a joint of the skeletal model
            animator!!.GetJointList(fb.containedJoints.toString(), jointList)
            i = 0
            while (i < jointList.Num()) {
                if (jointBody[jointList[i]] != -1) {
                    /*jointHandle_t*/
                    Game_local.gameLocal.Warning(
                        "%s: joint '%s' is already contained by body '%s'",
                        name, animator!!.GetJointName(jointList[i]),
                        physicsObj.GetBody(jointBody[jointList[i]])!!.GetName()
                    )
                }
                jointBody[jointList[i]] = id
                i++
            }
            return true
        }

        protected fun LoadConstraint(fc: idDeclAF_Constraint): Boolean {
            val body1: idAFBody?
            val body2: idAFBody?
            val angles = idAngles()
            val axis = idMat3()
            body1 = physicsObj.GetBody(fc.body1.toString())
            body2 = physicsObj.GetBody(fc.body2.toString())
            when (fc.type) {
                declAFConstraintType_t.DECLAF_CONSTRAINT_FIXED -> {
                    var c: idAFConstraint_Fixed?
                    c = physicsObj.GetConstraint(fc.name.toString()) as idAFConstraint_Fixed?
                    if (c != null) {
                        c.SetBody1(body1)
                        c.SetBody2(body2)
                    } else {
                        c = idAFConstraint_Fixed(fc.name, body1, body2)
                        physicsObj.AddConstraint(c)
                    }
                }

                declAFConstraintType_t.DECLAF_CONSTRAINT_BALLANDSOCKETJOINT -> {
                    var c: idAFConstraint_BallAndSocketJoint?
                    c = physicsObj.GetConstraint(fc.name.toString()) as idAFConstraint_BallAndSocketJoint?
                    if (c != null) {
                        c.SetBody1(body1)
                        c.SetBody2(body2)
                    } else {
                        c = idAFConstraint_BallAndSocketJoint(fc.name, body1, body2)
                        physicsObj.AddConstraint(c)
                    }
                    c.SetAnchor(fc.anchor.ToVec3())
                    c.SetFriction(fc.friction)
                    when (fc.limit) {
                        idDeclAF_Constraint.LIMIT_CONE -> {
                            c.SetConeLimit(fc.limitAxis.ToVec3(), fc.limitAngles[0], fc.shaft[0].ToVec3())
                        }

                        idDeclAF_Constraint.LIMIT_PYRAMID -> {
                            angles.set(fc.limitAxis.ToVec3().ToAngles())
                            angles.roll = fc.limitAngles[2]
                            axis.set(angles.ToMat3())
                            c.SetPyramidLimit(
                                axis[0],
                                axis[1],
                                fc.limitAngles[0],
                                fc.limitAngles[1],
                                fc.shaft[0].ToVec3()
                            )
                        }

                        else -> {
                            c.SetNoLimit()
                        }
                    }
                }

                declAFConstraintType_t.DECLAF_CONSTRAINT_UNIVERSALJOINT -> {
                    var c: idAFConstraint_UniversalJoint?
                    c = physicsObj.GetConstraint(fc.name.toString()) as idAFConstraint_UniversalJoint?
                    if (c != null) {
                        c.SetBody1(body1)
                        c.SetBody2(body2)
                    } else {
                        c = idAFConstraint_UniversalJoint(fc.name, body1, body2)
                        physicsObj.AddConstraint(c)
                    }
                    c.SetAnchor(fc.anchor.ToVec3())
                    c.SetShafts(fc.shaft[0].ToVec3(), fc.shaft[1].ToVec3())
                    c.SetFriction(fc.friction)
                    when (fc.limit) {
                        idDeclAF_Constraint.LIMIT_CONE -> {
                            c.SetConeLimit(fc.limitAxis.ToVec3(), fc.limitAngles[0])
                        }

                        idDeclAF_Constraint.LIMIT_PYRAMID -> {
                            angles.set(fc.limitAxis.ToVec3().ToAngles())
                            angles.roll = fc.limitAngles[2]
                            axis.set(angles.ToMat3())
                            c.SetPyramidLimit(axis[0], axis[1], fc.limitAngles[0], fc.limitAngles[1])
                        }

                        else -> {
                            c.SetNoLimit()
                        }
                    }
                }

                declAFConstraintType_t.DECLAF_CONSTRAINT_HINGE -> {
                    var c: idAFConstraint_Hinge?
                    c = physicsObj.GetConstraint(fc.name.toString()) as idAFConstraint_Hinge?
                    if (c != null) {
                        c.SetBody1(body1)
                        c.SetBody2(body2)
                    } else {
                        c = idAFConstraint_Hinge(fc.name, body1, body2)
                        physicsObj.AddConstraint(c)
                    }
                    c.SetAnchor(fc.anchor.ToVec3())
                    c.SetAxis(fc.axis.ToVec3())
                    c.SetFriction(fc.friction)
                    when (fc.limit) {
                        idDeclAF_Constraint.LIMIT_CONE -> {
                            val left = idVec3()
                            val up = idVec3()
                            val axis2 = idVec3()
                            val shaft = idVec3()
                            fc.axis.ToVec3().OrthogonalBasis(left, up)
                            axis2.set(
                                left.times(
                                    idRotation(
                                        getVec3Origin(),
                                        fc.axis.ToVec3(),
                                        fc.limitAngles[0]
                                    )
                                )
                            )
                            shaft.set(
                                left.times(
                                    idRotation(
                                        getVec3Origin(),
                                        fc.axis.ToVec3(),
                                        fc.limitAngles[2]
                                    )
                                )
                            )
                            c.SetLimit(axis2, fc.limitAngles[1], shaft)
                        }

                        else -> {
                            c.SetNoLimit()
                        }
                    }
                }

                declAFConstraintType_t.DECLAF_CONSTRAINT_SLIDER -> {
                    var c: idAFConstraint_Slider?
                    c = physicsObj.GetConstraint(fc.name.toString()) as idAFConstraint_Slider?
                    if (c != null) {
                        c.SetBody1(body1)
                        c.SetBody2(body2)
                    } else {
                        c = idAFConstraint_Slider(fc.name, body1, body2)
                        physicsObj.AddConstraint(c)
                    }
                    c.SetAxis(fc.axis.ToVec3())
                }

                declAFConstraintType_t.DECLAF_CONSTRAINT_SPRING -> {
                    var c: idAFConstraint_Spring?
                    c = physicsObj.GetConstraint(fc.name.toString()) as idAFConstraint_Spring?
                    if (c != null) {
                        c.SetBody1(body1)
                        c.SetBody2(body2)
                    } else {
                        c = idAFConstraint_Spring(fc.name, body1, body2)
                        physicsObj.AddConstraint(c)
                    }
                    c.SetAnchor(fc.anchor.ToVec3(), fc.anchor2.ToVec3())
                    c.SetSpring(fc.stretch, fc.compress, fc.damping, fc.restLength)
                    c.SetLimit(fc.minLength, fc.maxLength)
                }

                else -> {}
            }
            return true
        }

        protected fun TestSolid(): Boolean {
            var i: Int
            var body: idAFBody?
            val trace = trace_s()
            //	idStr str;
            var solid: Boolean
            if (!IsLoaded()) {
                return false
            }
            if (!SysCvar.af_testSolid.GetBool()) {
                return false
            }
            solid = false
            i = 0
            while (i < physicsObj.GetNumBodies()) {
                body = physicsObj.GetBody(i)!!
                if (Game_local.gameLocal.clip.Translation(
                        trace,
                        body.GetWorldOrigin(),
                        body.GetWorldOrigin(),
                        body.GetClipModel(),
                        body.GetWorldAxis(),
                        body.GetClipMask(),
                        self
                    )
                ) {
                    val depth = abs(trace.c.point.times(trace.c.normal) - trace.c.dist)
                    body.SetWorldOrigin(body.GetWorldOrigin().plus(trace.c.normal.times(depth + 8.0f)))
                    Game_local.gameLocal.DWarning(
                        "%s: body '%s' stuck in %d (normal = %.2f %.2f %.2f, depth = %.2f)", self!!.name,
                        body.GetName(), trace.c.contents, trace.c.normal.x, trace.c.normal.y, trace.c.normal.z, depth
                    )
                    solid = true
                }
                i++
            }
            return solid
        }

        companion object {
            private var DBG_LoadBody = 0
        }

        // ~idAF( void );
        init {
            name = idStr()
            physicsObj = idPhysics_AF()
            self = null
            animator = null
            modifiedAnim = 0
            baseOrigin = idVec3()
            baseAxis = idMat3.getMat3_identity()
            jointMods = idList()
            jointBody = idList()
            poseTime = -1
            restStartTime = -1
            isLoaded = false
            isActive = false
            hasBindConstraints = false
        }
    }

    /*
     ================
     GetJointTransform
     ================
     */
    internal class GetJointTransform private constructor() : getJointTransform_t() {
        override fun run(
            model: Any,
            frame: Array<idJointMat>,
            jointName: String,
            origin: idVec3,
            axis: idMat3
        ): Boolean {
            val   /*jointHandle_t*/joint: Int

//	joint = reinterpret_cast<idAnimator *>(model).GetJointHandle( jointName );
            joint = (model as idAnimator).GetJointHandle(jointName)
            //	if ( ( joint >= 0 ) && ( joint < reinterpret_cast<idAnimator *>(model).NumJoints() ) ) {
            return if (joint >= 0 && joint < model.NumJoints()) {
                origin.set(frame[joint].ToVec3())
                axis.set(frame[joint].ToMat3())
                true
            } else {
                false
            }
        }

        override fun run(
            model: Any,
            frame: Array<idJointMat>,
            jointName: idStr,
            origin: idVec3,
            axis: idMat3
        ): Boolean {
            return run(model, frame, jointName.toString(), origin, axis)
        }

        companion object {
            val INSTANCE: getJointTransform_t = GetJointTransform()
        }
    }
}
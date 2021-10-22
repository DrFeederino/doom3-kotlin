package neo.Game;

import neo.CM.CollisionModel.trace_s;
import neo.Game.Animation.Anim_Blend.idAnimator;
import neo.Game.Entity.idEntity;
import neo.Game.GameSys.SaveGame.idRestoreGame;
import neo.Game.GameSys.SaveGame.idSaveGame;
import neo.Game.Mover.idPlat;
import neo.Game.Physics.Clip.idClipModel;
import neo.Game.Physics.Physics.idPhysics;
import neo.Renderer.Model.idRenderModel;
import neo.idlib.Text.Str.idStr;
import neo.idlib.geometry.JointTransform.idJointMat;
import neo.idlib.geometry.TraceModel.idTraceModel;
import neo.idlib.geometry.Winding.idFixedWinding;
import neo.idlib.math.Math_h.idMath;
import neo.idlib.math.Matrix.idMat3;
import neo.idlib.math.Vector.idVec3;

import java.util.stream.Stream;

import static neo.Game.Animation.Anim.jointModTransform_t.JOINTMOD_NONE;
import static neo.Game.Animation.Anim.jointModTransform_t.JOINTMOD_WORLD_OVERRIDE;
import static neo.Game.GameSys.SysCvar.ik_debug;
import static neo.Game.GameSys.SysCvar.ik_enable;
import static neo.Game.Game_local.gameLocal;
import static neo.Game.Game_local.gameRenderWorld;
import static neo.Renderer.Material.CONTENTS_IKCLIP;
import static neo.Renderer.Material.CONTENTS_SOLID;
import static neo.Renderer.Model.INVALID_JOINT;
import static neo.idlib.Lib.*;
import static neo.idlib.Text.Str.va;
import static neo.idlib.math.Matrix.idMat3.getMat3_identity;
import static neo.idlib.math.Vector.getVec3_origin;

/**
 *
 */
public class IK /*ea*/ {

    /*
     ===============================================================================

     IK base class with a simple fast two bone solver.

     ===============================================================================
     */
    public static final String IK_ANIM = "ik_pose";

    /*
     ===============================================================================

     idIK

     ===============================================================================
     */
    public static class idIK {

        protected final idVec3 modelOffset;
        protected idAnimator animator;        // animator on entity
        protected boolean ik_activate;
        protected boolean initialized;
        protected int modifiedAnim;        // animation modified by the IK
        protected idEntity self;        // entity using the animated model
        //
        //

        public idIK() {
            ik_activate = false;
            initialized = false;
            self = null;
            animator = null;
            modifiedAnim = 0;
            modelOffset = new idVec3();
        }
        // virtual					~idIK( void );

        public void Save(idSaveGame savefile) {
            savefile.WriteBool(initialized);
            savefile.WriteBool(ik_activate);
            savefile.WriteObject(self);
            savefile.WriteString(animator != null && animator.GetAnim(modifiedAnim) != null ? animator.GetAnim(modifiedAnim).Name() : "");
            savefile.WriteVec3(modelOffset);
        }

        public void Restore(idRestoreGame savefile) {
            idStr anim = new idStr();

            initialized = savefile.ReadBool();
            ik_activate = savefile.ReadBool();
            savefile.ReadObject(/*reinterpret_cast<idClass *&>*/self);
            savefile.ReadString(anim);
            savefile.ReadVec3(modelOffset);

            if (self != null) {
                animator = self.GetAnimator();
                if (animator == null || animator.ModelDef() == null) {
                    gameLocal.Warning("idIK::Restore: IK for entity '%s' at (%s) has no model set.",
                            self.name, self.GetPhysics().GetOrigin().ToString(0));
                }
                modifiedAnim = animator.GetAnim(anim.toString());
                if (modifiedAnim == 0) {
                    gameLocal.Warning("idIK::Restore: IK for entity '%s' at (%s) has no modified animation.",
                            self.name, self.GetPhysics().GetOrigin().ToString(0));
                }
            } else {
                animator = null;
                modifiedAnim = 0;
            }
        }

        public boolean IsInitialized() {
            return initialized && ik_enable.GetBool();
        }

        public boolean Init(idEntity self, final String anim, final idVec3 modelOffset) {
            idRenderModel model;//TODO:finalize objects that can be finalized. hint <- <- <-

            if (self == null) {
                return false;
            }

            this.self = self;

            animator = self.GetAnimator();
            if (animator == null || animator.ModelDef() == null) {
                gameLocal.Warning("idIK::Init: IK for entity '%s' at (%s) has no model set.",
                        self.name, self.GetPhysics().GetOrigin().ToString(0));
                return false;
            }
            if (animator.ModelDef().ModelHandle() == null) {
                gameLocal.Warning("idIK::Init: IK for entity '%s' at (%s) uses default model.",
                        self.name, self.GetPhysics().GetOrigin().ToString(0));
                return false;
            }
            model = animator.ModelHandle();
            if (model == null) {
                gameLocal.Warning("idIK::Init: IK for entity '%s' at (%s) has no model set.",
                        self.name, self.GetPhysics().GetOrigin().ToString(0));
                return false;
            }
            modifiedAnim = animator.GetAnim(anim);
            if (modifiedAnim == 0) {
                gameLocal.Warning("idIK::Init: IK for entity '%s' at (%s) has no modified animation.",
                        self.name, self.GetPhysics().GetOrigin().ToString(0));
                return false;
            }

            this.modelOffset.oSet(modelOffset);

            return true;
        }

        public void Evaluate() {
        }

        public void ClearJointMods() {
            ik_activate = false;
        }

        public boolean SolveTwoBones(final idVec3 startPos, final idVec3 endPos, final idVec3 dir, float len0, float len1, final idVec3 jointPos) {
            float length, lengthSqr, lengthInv, x, y;
            final idVec3 vec0 = new idVec3(), vec1 = new idVec3();

            vec0.oSet(endPos.oMinus(startPos));
            lengthSqr = vec0.LengthSqr();
            lengthInv = idMath.InvSqrt(lengthSqr);
            length = lengthInv * lengthSqr;

            // if the start and end position are too far out or too close to each other
            if (length > len0 + len1 || length < Math.abs(len0 - len1)) {
                jointPos.oSet(startPos.oPlus(vec0.oMultiply(0.5f)));
                return false;
            }

            vec0.oMulSet(lengthInv);
            vec1.oSet(dir.oMinus(vec0.oMultiply(dir.oMultiply(vec0))));
            vec1.Normalize();

            x = (length * length + len0 * len0 - len1 * len1) * (0.5f * lengthInv);
            y = idMath.Sqrt(len0 * len0 - x * x);

            jointPos.oSet(startPos.oPlus(vec0.oMultiply(x).oPlus(vec1.oMultiply(y))));

            return true;
        }

        public float GetBoneAxis(final idVec3 startPos, final idVec3 endPos, final idVec3 dir, idMat3 axis) {
            float length;
            axis.oSet(0, endPos.oMinus(startPos));
            length = axis.oGet(0).Normalize();
            axis.oSet(1, dir.oMinus(axis.oGet(0).oMultiply(dir.oMultiply(axis.oGet(0)))));
            axis.oGet(1).Normalize();
            axis.oGet(2).Cross(axis.oGet(1), axis.oGet(0));

            return length;
        }

    }

    /*
     ===============================================================================

     IK controller for a walking character with an arbitrary number of legs.	

     ===============================================================================
     */
    /*
     ===============================================================================

     idIK_Walk

     ===============================================================================
     */
    public static class idIK_Walk extends idIK {

        private static final int MAX_LEGS = 8;
        private static final idVec3[] footWinding/*[4]*/ = {
                new idVec3(1.0f, 1.0f, 0),
                new idVec3(-1.0f, 1.0f, 0),
                new idVec3(-1.0f, -1.0f, 0),
                new idVec3(1.0f, -1.0f, 0)
        };
        private final int/*jointHandle_t*/[] ankleJoints = new int[MAX_LEGS];
        private final int/*jointHandle_t*/[] dirJoints = new int[MAX_LEGS];
        private final int/*jointHandle_t*/[] footJoints = new int[MAX_LEGS];
        //
        private final idVec3[] hipForward = idVec3.generateArray(MAX_LEGS);
        private final int/*jointHandle_t*/[] hipJoints = new int[MAX_LEGS];
        private final idVec3[] kneeForward = idVec3.generateArray(MAX_LEGS);
        private final int/*jointHandle_t*/[] kneeJoints = new int[MAX_LEGS];
        private final float[] lowerLegLength = new float[MAX_LEGS];
        private final idMat3[] lowerLegToKneeJoint = new idMat3[MAX_LEGS];
        private final float[] oldAnkleHeights = new float[MAX_LEGS];
        private final idVec3 pivotPos;
        //
        private final float[] upperLegLength = new float[MAX_LEGS];
        //
        private final idMat3[] upperLegToHipJoint = new idMat3[MAX_LEGS];
        private final idVec3 waistOffset;
        private int enabledLegs;
        private float footDownTrace;
        //
        private idClipModel footModel;
        private float footShift;
        private float footUpTrace;
        private float minWaistAnkleDist;
        private float minWaistFloorDist;
        //
        private int numLegs;
        private boolean oldHeightsValid;
        private float oldWaistHeight;
        //
        // state
        private int pivotFoot;
        private float pivotYaw;
        //
        private float smoothing;
        private boolean tiltWaist;
        private boolean usePivot;
        private int/*jointHandle_t*/ waistJoint;
        private float waistShift;
        //
        //
        private float waistSmoothing;
        // virtual					~idIK_Walk( void );

        public idIK_Walk() {
            int i;

            initialized = false;
            footModel = null;
            numLegs = 0;
            enabledLegs = 0;
            for (i = 0; i < MAX_LEGS; i++) {
                footJoints[i] = INVALID_JOINT;
                ankleJoints[i] = INVALID_JOINT;
                kneeJoints[i] = INVALID_JOINT;
                hipJoints[i] = INVALID_JOINT;
                dirJoints[i] = INVALID_JOINT;
                upperLegLength[i] = 0;
                lowerLegLength[i] = 0;
                upperLegToHipJoint[i] = getMat3_identity();
                lowerLegToKneeJoint[i] = getMat3_identity();
                oldAnkleHeights[i] = 0;
            }
            waistJoint = INVALID_JOINT;

            smoothing = 0.75f;
            waistSmoothing = 0.5f;
            footShift = 0;
            waistShift = 0;
            minWaistFloorDist = 0;
            minWaistAnkleDist = 0;
            footUpTrace = 32.0f;
            footDownTrace = 32.0f;
            tiltWaist = false;
            usePivot = false;

            pivotFoot = -1;
            pivotYaw = 0;
            pivotPos = new idVec3();

            oldHeightsValid = false;
            oldWaistHeight = 0;
            waistOffset = new idVec3();
        }

        @Override
        public void Save(idSaveGame savefile) {
            int i;

            super.Save(savefile);

            savefile.WriteClipModel(footModel);

            savefile.WriteInt(numLegs);
            savefile.WriteInt(enabledLegs);
            for (i = 0; i < MAX_LEGS; i++) {
                savefile.WriteInt(footJoints[i]);
            }
            for (i = 0; i < MAX_LEGS; i++) {
                savefile.WriteInt(ankleJoints[i]);
            }
            for (i = 0; i < MAX_LEGS; i++) {
                savefile.WriteInt(kneeJoints[i]);
            }
            for (i = 0; i < MAX_LEGS; i++) {
                savefile.WriteInt(hipJoints[i]);
            }
            for (i = 0; i < MAX_LEGS; i++) {
                savefile.WriteInt(dirJoints[i]);
            }
            savefile.WriteInt(waistJoint);

            for (i = 0; i < MAX_LEGS; i++) {
                savefile.WriteVec3(hipForward[i]);
            }
            for (i = 0; i < MAX_LEGS; i++) {
                savefile.WriteVec3(kneeForward[i]);
            }

            for (i = 0; i < MAX_LEGS; i++) {
                savefile.WriteFloat(upperLegLength[i]);
            }
            for (i = 0; i < MAX_LEGS; i++) {
                savefile.WriteFloat(lowerLegLength[i]);
            }

            for (i = 0; i < MAX_LEGS; i++) {
                savefile.WriteMat3(upperLegToHipJoint[i]);
            }
            for (i = 0; i < MAX_LEGS; i++) {
                savefile.WriteMat3(lowerLegToKneeJoint[i]);
            }

            savefile.WriteFloat(smoothing);
            savefile.WriteFloat(waistSmoothing);
            savefile.WriteFloat(footShift);
            savefile.WriteFloat(waistShift);
            savefile.WriteFloat(minWaistFloorDist);
            savefile.WriteFloat(minWaistAnkleDist);
            savefile.WriteFloat(footUpTrace);
            savefile.WriteFloat(footDownTrace);
            savefile.WriteBool(tiltWaist);
            savefile.WriteBool(usePivot);

            savefile.WriteInt(pivotFoot);
            savefile.WriteFloat(pivotYaw);
            savefile.WriteVec3(pivotPos);
            savefile.WriteBool(oldHeightsValid);
            savefile.WriteFloat(oldWaistHeight);
            for (i = 0; i < MAX_LEGS; i++) {
                savefile.WriteFloat(oldAnkleHeights[i]);
            }
            savefile.WriteVec3(waistOffset);
        }

        @Override
        public void Restore(idRestoreGame savefile) {
            int i;

            super.Restore(savefile);

            savefile.ReadClipModel(footModel);

            numLegs = savefile.ReadInt();
            enabledLegs = savefile.ReadInt();
            for (i = 0; i < MAX_LEGS; i++) {
                footJoints[i] = savefile.ReadInt();
            }
            for (i = 0; i < MAX_LEGS; i++) {
                ankleJoints[i] = savefile.ReadInt();
            }
            for (i = 0; i < MAX_LEGS; i++) {
                kneeJoints[i] = savefile.ReadInt();
            }
            for (i = 0; i < MAX_LEGS; i++) {
                hipJoints[i] = savefile.ReadInt();
            }
            for (i = 0; i < MAX_LEGS; i++) {
                dirJoints[i] = savefile.ReadInt();
            }
            waistJoint = savefile.ReadInt();

            for (i = 0; i < MAX_LEGS; i++) {
                savefile.ReadVec3(hipForward[i]);
            }
            for (i = 0; i < MAX_LEGS; i++) {
                savefile.ReadVec3(kneeForward[i]);
            }

            for (i = 0; i < MAX_LEGS; i++) {
                upperLegLength[i] = savefile.ReadFloat();
            }
            for (i = 0; i < MAX_LEGS; i++) {
                lowerLegLength[i] = savefile.ReadFloat();
            }

            for (i = 0; i < MAX_LEGS; i++) {
                savefile.ReadMat3(upperLegToHipJoint[i]);
            }
            for (i = 0; i < MAX_LEGS; i++) {
                savefile.ReadMat3(lowerLegToKneeJoint[i]);
            }

            smoothing = savefile.ReadFloat();
            waistSmoothing = savefile.ReadFloat();
            footShift = savefile.ReadFloat();
            waistShift = savefile.ReadFloat();
            minWaistFloorDist = savefile.ReadFloat();
            minWaistAnkleDist = savefile.ReadFloat();
            footUpTrace = savefile.ReadFloat();
            footDownTrace = savefile.ReadFloat();
            tiltWaist = savefile.ReadBool();
            usePivot = savefile.ReadBool();

            pivotFoot = savefile.ReadInt();
            pivotYaw = savefile.ReadFloat();
            savefile.ReadVec3(pivotPos);
            oldHeightsValid = savefile.ReadBool();
            oldWaistHeight = savefile.ReadFloat();
            for (i = 0; i < MAX_LEGS; i++) {
                oldAnkleHeights[i] = savefile.ReadFloat();
            }
            savefile.ReadVec3(waistOffset);
        }

        @Override
        public boolean Init(idEntity self, final String anim, final idVec3 modelOffset) {
            int i;
            float footSize;
            final idVec3[] verts = idVec3.generateArray(4);
            idTraceModel trm = new idTraceModel();
            String jointName;
            final idVec3 dir = new idVec3(), ankleOrigin = new idVec3(), kneeOrigin = new idVec3(), hipOrigin = new idVec3(), dirOrigin = new idVec3();
            idMat3 axis = new idMat3(), ankleAxis, kneeAxis, hipAxis;

            if (null == self) {
                return false;
            }

            numLegs = Min(self.spawnArgs.GetInt("ik_numLegs", "0"), MAX_LEGS);
            if (numLegs == 0) {
                return true;
            }

            if (!super.Init(self, anim, modelOffset)) {
                return false;
            }

            int numJoints = animator.NumJoints();
            idJointMat[] joints = Stream.generate(idJointMat::new).limit(numJoints).toArray(idJointMat[]::new);

            // create the animation frame used to setup the IK
            GameEdit.gameEdit.ANIM_CreateAnimFrame(animator.ModelHandle(), animator.GetAnim(modifiedAnim).MD5Anim(0), numJoints, joints, 1, animator.ModelDef().GetVisualOffset().oPlus(modelOffset), animator.RemoveOrigin());

            enabledLegs = 0;

            // get all the joints
            for (i = 0; i < numLegs; i++) {

                jointName = self.spawnArgs.GetString(va("ik_foot%d", i + 1));
                footJoints[i] = animator.GetJointHandle(jointName);
                if (footJoints[i] == INVALID_JOINT) {
                    Game_local.idGameLocal.Error("idIK_Walk::Init: invalid foot joint '%s'", jointName);
                }

                jointName = self.spawnArgs.GetString(va("ik_ankle%d", i + 1));
                ankleJoints[i] = animator.GetJointHandle(jointName);
                if (ankleJoints[i] == INVALID_JOINT) {
                    Game_local.idGameLocal.Error("idIK_Walk::Init: invalid ankle joint '%s'", jointName);
                }

                jointName = self.spawnArgs.GetString(va("ik_knee%d", i + 1));
                kneeJoints[i] = animator.GetJointHandle(jointName);
                if (kneeJoints[i] == INVALID_JOINT) {
                    Game_local.idGameLocal.Error("idIK_Walk::Init: invalid knee joint '%s'\n", jointName);
                }

                jointName = self.spawnArgs.GetString(va("ik_hip%d", i + 1));
                hipJoints[i] = animator.GetJointHandle(jointName);
                if (hipJoints[i] == INVALID_JOINT) {
                    Game_local.idGameLocal.Error("idIK_Walk::Init: invalid hip joint '%s'\n", jointName);
                }

                jointName = self.spawnArgs.GetString(va("ik_dir%d", i + 1));
                dirJoints[i] = animator.GetJointHandle(jointName);

                enabledLegs |= 1 << i;
            }

            jointName = self.spawnArgs.GetString("ik_waist");
            waistJoint = animator.GetJointHandle(jointName);
            if (waistJoint == INVALID_JOINT) {
                Game_local.idGameLocal.Error("idIK_Walk::Init: invalid waist joint '%s'\n", jointName);
            }

            // get the leg bone lengths and rotation matrices
            for (i = 0; i < numLegs; i++) {
                oldAnkleHeights[i] = 0;

                ankleAxis = joints[ankleJoints[i]].ToMat3();
                ankleOrigin.oSet(joints[ankleJoints[i]].ToVec3());

                kneeAxis = joints[kneeJoints[i]].ToMat3();
                kneeOrigin.oSet(joints[kneeJoints[i]].ToVec3());

                hipAxis = joints[hipJoints[i]].ToMat3();
                hipOrigin.oSet(joints[hipJoints[i]].ToVec3());

                // get the IK direction
                if (dirJoints[i] != INVALID_JOINT) {
                    dirOrigin.oSet(joints[dirJoints[i]].ToVec3());
                    dir.oSet(dirOrigin.oMinus(kneeOrigin));
                } else {
                    dir.Set(1.0f, 0, 0);
                }

                hipForward[i].oSet(dir.oMultiply(hipAxis.Transpose()));
                kneeForward[i].oSet(dir.oMultiply(kneeAxis.Transpose()));

                // conversion from upper leg bone axis to hip joint axis
                upperLegLength[i] = GetBoneAxis(hipOrigin, kneeOrigin, dir, axis);
                upperLegToHipJoint[i] = hipAxis.oMultiply(axis.Transpose());

                // conversion from lower leg bone axis to knee joint axis
                lowerLegLength[i] = GetBoneAxis(kneeOrigin, ankleOrigin, dir, axis);
                lowerLegToKneeJoint[i] = kneeAxis.oMultiply(axis.Transpose());
            }

            smoothing = self.spawnArgs.GetFloat("ik_smoothing", "0.75");
            waistSmoothing = self.spawnArgs.GetFloat("ik_waistSmoothing", "0.75");
            footShift = self.spawnArgs.GetFloat("ik_footShift", "0");
            waistShift = self.spawnArgs.GetFloat("ik_waistShift", "0");
            minWaistFloorDist = self.spawnArgs.GetFloat("ik_minWaistFloorDist", "0");
            minWaistAnkleDist = self.spawnArgs.GetFloat("ik_minWaistAnkleDist", "0");
            footUpTrace = self.spawnArgs.GetFloat("ik_footUpTrace", "32");
            footDownTrace = self.spawnArgs.GetFloat("ik_footDownTrace", "32");
            tiltWaist = self.spawnArgs.GetBool("ik_tiltWaist", "0");
            usePivot = self.spawnArgs.GetBool("ik_usePivot", "0");

            // setup a clip model for the feet
            footSize = self.spawnArgs.GetFloat("ik_footSize", "4") * 0.5f;
            if (footSize > 0) {
                for (i = 0; i < 4; i++) {
                    verts[i].oSet(footWinding[i].oMultiply(footSize));
                }
                trm.SetupPolygon(verts, 4);
                footModel = new idClipModel(trm);
            }

            initialized = true;

            return true;
        }

        @Override
        public void Evaluate() {
            int i, newPivotFoot = 0;
            float modelHeight, jointHeight, lowestHeight;
            float[] floorHeights = new float[MAX_LEGS];
            float shift, smallestShift, newHeight, step, newPivotYaw, height, largestAnkleHeight;
            final idVec3 modelOrigin = new idVec3(), normal = new idVec3(), hipDir = new idVec3(), kneeDir = new idVec3(), start = new idVec3(), end = new idVec3();
            final idVec3[] jointOrigins = idVec3.generateArray(MAX_LEGS);
            final idVec3 footOrigin = new idVec3(), ankleOrigin = new idVec3(),
                    kneeOrigin = new idVec3(), hipOrigin = new idVec3(), waistOrigin = new idVec3();
            idMat3 modelAxis, waistAxis = new idMat3(), axis = new idMat3();
            idMat3[] hipAxis = new idMat3[MAX_LEGS], kneeAxis = new idMat3[MAX_LEGS], ankleAxis = new idMat3[MAX_LEGS];
            trace_s results = new trace_s();

            if (null == self || !gameLocal.isNewFrame) {
                return;
            }

            // if no IK enabled on any legs
            if (0 == enabledLegs) {//TODO:make booleans out of ints that are boolean anyways. damn you C programmers!!
                return;
            }

            normal.oSet(self.GetPhysics().GetGravityNormal().oNegative());
            modelOrigin.oSet(self.GetPhysics().GetOrigin());
            modelAxis = self.GetRenderEntity().axis;
            modelHeight = modelOrigin.oMultiply(normal);

            modelOrigin.oPluSet(modelOffset.oMultiply(modelAxis));

            // create frame without joint mods
            animator.CreateFrame(gameLocal.time, false);

            // get the joint positions for the feet
            lowestHeight = idMath.INFINITY;
            for (i = 0; i < numLegs; i++) {
                animator.GetJointTransform(footJoints[i], gameLocal.time, footOrigin, axis);
                jointOrigins[i].oSet(modelOrigin.oPlus(footOrigin.oMultiply(modelAxis)));
                jointHeight = jointOrigins[i].oMultiply(normal);
                if (jointHeight < lowestHeight) {
                    lowestHeight = jointHeight;
                    newPivotFoot = i;
                }
            }

            if (usePivot) {

                newPivotYaw = modelAxis.oGet(0).ToYaw();

                // change pivot foot
                if (newPivotFoot != pivotFoot || Math.abs(idMath.AngleNormalize180(newPivotYaw - pivotYaw)) > 30.0f) {
                    pivotFoot = newPivotFoot;
                    pivotYaw = newPivotYaw;
                    animator.GetJointTransform(footJoints[pivotFoot], gameLocal.time, footOrigin, axis);
                    pivotPos.oSet(modelOrigin.oPlus(footOrigin.oMultiply(modelAxis)));
                }

                // keep pivot foot in place
                jointOrigins[pivotFoot].oSet(pivotPos);
            }

            // get the floor heights for the feet
            for (i = 0; i < numLegs; i++) {

                if (0 == (enabledLegs & (1 << i))) {
                    continue;
                }

                start.oSet(jointOrigins[i].oPlus(normal.oMultiply(footUpTrace)));
                end.oSet(jointOrigins[i].oMinus(normal.oMultiply(footDownTrace)));
                gameLocal.clip.Translation(results, start, end, footModel, getMat3_identity(), CONTENTS_SOLID | CONTENTS_IKCLIP, self);
                floorHeights[i] = results.endpos.oMultiply(normal);

                if (ik_debug.GetBool() && footModel != null) {
                    idFixedWinding w = new idFixedWinding();
                    for (int j = 0; j < footModel.GetTraceModel().numVerts; j++) {
                        w.oPluSet(footModel.GetTraceModel().verts[j]);
                    }
                    gameRenderWorld.DebugWinding(colorRed, w, results.endpos, results.endAxis);
                }
            }

            final idPhysics phys = self.GetPhysics();

            // test whether or not the character standing on the ground
            boolean onGround = phys.HasGroundContacts();

            // test whether or not the character is standing on a plat
            boolean onPlat = false;
            for (i = 0; i < phys.GetNumContacts(); i++) {
                idEntity ent = gameLocal.entities[phys.GetContact(i).entityNum];
                if (ent != null && ent instanceof idPlat) {
                    onPlat = true;
                    break;
                }
            }

            // adjust heights of the ankles
            smallestShift = idMath.INFINITY;
            largestAnkleHeight = -idMath.INFINITY;
            for (i = 0; i < numLegs; i++) {

                if (onGround && (enabledLegs & (1 << i)) != 0) {
                    shift = floorHeights[i] - modelHeight + footShift;
                } else {
                    shift = 0;
                }

                if (shift < smallestShift) {
                    smallestShift = shift;
                }

                ankleAxis[i] = new idMat3();
                animator.GetJointTransform(ankleJoints[i], gameLocal.time, ankleOrigin, ankleAxis[i]);
                jointOrigins[i] = modelOrigin.oPlus(ankleOrigin.oMultiply(modelAxis));

                height = jointOrigins[i].oMultiply(normal);

                if (oldHeightsValid && !onPlat) {
                    step = height + shift - oldAnkleHeights[i];
                    shift -= smoothing * step;
                }

                newHeight = height + shift;
                if (newHeight > largestAnkleHeight) {
                    largestAnkleHeight = newHeight;
                }

                oldAnkleHeights[i] = newHeight;

                jointOrigins[i].oPluSet(normal.oMultiply(shift));
            }

            animator.GetJointTransform(waistJoint, gameLocal.time, waistOrigin, waistAxis);
            waistOrigin.oSet(modelOrigin.oPlus(waistOrigin.oMultiply(modelAxis)));

            // adjust position of the waist
            waistOffset.oSet(normal.oMultiply(smallestShift + waistShift));

            // if the waist should be at least a certain distance above the floor
            if (minWaistFloorDist > 0 && waistOffset.oMultiply(normal) < 0) {
                start.oSet(waistOrigin);
                end.oSet(waistOrigin.oPlus(waistOffset.oMinus(normal.oMultiply(minWaistFloorDist))));
                gameLocal.clip.Translation(results, start, end, footModel, modelAxis, CONTENTS_SOLID | CONTENTS_IKCLIP, self);
                height = (waistOrigin.oPlus(waistOffset.oMinus(results.endpos))).oMultiply(normal);
                if (height < minWaistFloorDist) {
                    waistOffset.oPluSet(normal.oMultiply(minWaistFloorDist - height));
                }
            }

            // if the waist should be at least a certain distance above the ankles
            if (minWaistAnkleDist > 0) {
                height = (waistOrigin.oPlus(waistOffset)).oMultiply(normal);
                if (height - largestAnkleHeight < minWaistAnkleDist) {
                    waistOffset.oPluSet(normal.oMultiply(minWaistAnkleDist - (height - largestAnkleHeight)));
                }
            }

            if (oldHeightsValid) {
                // smoothly adjust height of waist
                newHeight = (waistOrigin.oPlus(waistOffset)).oMultiply(normal);
                step = newHeight - oldWaistHeight;
                waistOffset.oMinSet(normal.oMultiply(waistSmoothing * step));
            }

            // save height of waist for smoothing
            oldWaistHeight = (waistOrigin.oPlus(waistOffset)).oMultiply(normal);

            if (!oldHeightsValid) {
                oldHeightsValid = true;
                return;
            }

            // solve IK
            for (i = 0; i < numLegs; i++) {

                // get the position of the hip in world space
                animator.GetJointTransform(hipJoints[i], gameLocal.time, hipOrigin, axis);
                hipOrigin.oSet(modelOrigin.oPlus(waistOffset.oPlus(hipOrigin.oMultiply(modelAxis))));
                hipDir.oSet(hipForward[i].oMultiply(axis.oMultiply(modelAxis)));

                // get the IK bend direction
                animator.GetJointTransform(kneeJoints[i], gameLocal.time, kneeOrigin, axis);
                kneeDir.oSet(kneeForward[i].oMultiply(axis.oMultiply(modelAxis)));

                // solve IK and calculate knee position
                SolveTwoBones(hipOrigin, jointOrigins[i], kneeDir, upperLegLength[i], lowerLegLength[i], kneeOrigin);

                if (ik_debug.GetBool()) {
                    gameRenderWorld.DebugLine(colorCyan, hipOrigin, kneeOrigin);
                    gameRenderWorld.DebugLine(colorRed, kneeOrigin, jointOrigins[i]);
                    gameRenderWorld.DebugLine(colorYellow, kneeOrigin, kneeOrigin.oPlus(hipDir));
                    gameRenderWorld.DebugLine(colorGreen, kneeOrigin, kneeOrigin.oPlus(kneeDir));
                }

                // get the axis for the hip joint
                GetBoneAxis(hipOrigin, kneeOrigin, hipDir, axis);
                hipAxis[i] = upperLegToHipJoint[i].oMultiply((axis.oMultiply(modelAxis.Transpose())));

                // get the axis for the knee joint
                GetBoneAxis(kneeOrigin, jointOrigins[i], kneeDir, axis);
                kneeAxis[i] = lowerLegToKneeJoint[i].oMultiply((axis.oMultiply(modelAxis.Transpose())));
            }

            // set the joint mods
            animator.SetJointAxis(waistJoint, JOINTMOD_WORLD_OVERRIDE, waistAxis);
            animator.SetJointPos(waistJoint, JOINTMOD_WORLD_OVERRIDE, (waistOrigin.oPlus(waistOffset.oMinus(modelOrigin))).oMultiply(modelAxis.Transpose()));
            for (i = 0; i < numLegs; i++) {
                animator.SetJointAxis(hipJoints[i], JOINTMOD_WORLD_OVERRIDE, hipAxis[i]);
                animator.SetJointAxis(kneeJoints[i], JOINTMOD_WORLD_OVERRIDE, kneeAxis[i]);
                animator.SetJointAxis(ankleJoints[i], JOINTMOD_WORLD_OVERRIDE, ankleAxis[i]);
            }

            ik_activate = true;
        }

        @Override
        public void ClearJointMods() {
            int i;

            if (null == self || !ik_activate) {
                return;
            }

            animator.SetJointAxis(waistJoint, JOINTMOD_NONE, getMat3_identity());
            animator.SetJointPos(waistJoint, JOINTMOD_NONE, getVec3_origin());
            for (i = 0; i < numLegs; i++) {
                animator.SetJointAxis(hipJoints[i], JOINTMOD_NONE, getMat3_identity());
                animator.SetJointAxis(kneeJoints[i], JOINTMOD_NONE, getMat3_identity());
                animator.SetJointAxis(ankleJoints[i], JOINTMOD_NONE, getMat3_identity());
            }

            ik_activate = false;
        }

        public void EnableAll() {
            enabledLegs = (1 << numLegs) - 1;
            oldHeightsValid = false;
        }

        public void DisableAll() {
            enabledLegs = 0;
            oldHeightsValid = false;
        }

        public void EnableLeg(int num) {
            enabledLegs |= 1 << num;
        }

        public void DisableLeg(int num) {
            enabledLegs &= ~(1 << num);
        }
    }

    /*
     ===============================================================================

     IK controller for reaching a position with an arm or leg.

     ===============================================================================
     */
    /*
     ===============================================================================

     idIK_Reach

     ===============================================================================
     */
    public static class idIK_Reach extends idIK {

        private static final int MAX_ARMS = 2;
        private final int/*jointHandle_t*/[] dirJoints = new int[MAX_ARMS];
        private final idVec3[] elbowForward = idVec3.generateArray(MAX_ARMS);
        private final int/*jointHandle_t*/[] elbowJoints = new int[MAX_ARMS];
        private final int/*jointHandle_t*/[] handJoints = new int[MAX_ARMS];
        private final float[] lowerArmLength = new float[MAX_ARMS];
        private final idMat3[] lowerArmToElbowJoint = new idMat3[MAX_ARMS];
        //
        private final idVec3[] shoulderForward = idVec3.generateArray(MAX_ARMS);
        private final int/*jointHandle_t*/[] shoulderJoints = new int[MAX_ARMS];
        //
        private final float[] upperArmLength = new float[MAX_ARMS];
        //
        private final idMat3[] upperArmToShoulderJoint = new idMat3[MAX_ARMS];
        private int enabledArms;
        //
        private int numArms;
        //
        //

        public idIK_Reach() {
            int i;

            initialized = false;
            numArms = 0;
            enabledArms = 0;
            for (i = 0; i < MAX_ARMS; i++) {
                handJoints[i] = INVALID_JOINT;
                elbowJoints[i] = INVALID_JOINT;
                shoulderJoints[i] = INVALID_JOINT;
                dirJoints[i] = INVALID_JOINT;
                shoulderForward[i].Zero();
                elbowForward[i].Zero();
                upperArmLength[i] = 0;
                lowerArmLength[i] = 0;
                upperArmToShoulderJoint[i].Identity();
                lowerArmToElbowJoint[i].Identity();
            }
        }
        // virtual					~idIK_Reach( void );

        @Override
        public void Save(idSaveGame savefile) {
            int i;
            super.Save(savefile);

            savefile.WriteInt(numArms);
            savefile.WriteInt(enabledArms);
            for (i = 0; i < MAX_ARMS; i++) {
                savefile.WriteInt(handJoints[i]);
            }
            for (i = 0; i < MAX_ARMS; i++) {
                savefile.WriteInt(elbowJoints[i]);
            }
            for (i = 0; i < MAX_ARMS; i++) {
                savefile.WriteInt(shoulderJoints[i]);
            }
            for (i = 0; i < MAX_ARMS; i++) {
                savefile.WriteInt(dirJoints[i]);
            }

            for (i = 0; i < MAX_ARMS; i++) {
                savefile.WriteVec3(shoulderForward[i]);
            }
            for (i = 0; i < MAX_ARMS; i++) {
                savefile.WriteVec3(elbowForward[i]);
            }

            for (i = 0; i < MAX_ARMS; i++) {
                savefile.WriteFloat(upperArmLength[i]);
            }
            for (i = 0; i < MAX_ARMS; i++) {
                savefile.WriteFloat(lowerArmLength[i]);
            }

            for (i = 0; i < MAX_ARMS; i++) {
                savefile.WriteMat3(upperArmToShoulderJoint[i]);
            }
            for (i = 0; i < MAX_ARMS; i++) {
                savefile.WriteMat3(lowerArmToElbowJoint[i]);
            }
        }

        @Override
        public void Restore(idRestoreGame savefile) {
            int i;
            super.Restore(savefile);

            numArms = savefile.ReadInt();
            enabledArms = savefile.ReadInt();
            for (i = 0; i < MAX_ARMS; i++) {
                handJoints[i] = savefile.ReadInt();
            }
            for (i = 0; i < MAX_ARMS; i++) {
                elbowJoints[i] = savefile.ReadInt();
            }
            for (i = 0; i < MAX_ARMS; i++) {
                shoulderJoints[i] = savefile.ReadInt();
            }
            for (i = 0; i < MAX_ARMS; i++) {
                dirJoints[i] = savefile.ReadInt();
            }

            for (i = 0; i < MAX_ARMS; i++) {
                savefile.ReadVec3(shoulderForward[i]);
            }
            for (i = 0; i < MAX_ARMS; i++) {
                savefile.ReadVec3(elbowForward[i]);
            }

            for (i = 0; i < MAX_ARMS; i++) {
                upperArmLength[i] = savefile.ReadFloat();
            }
            for (i = 0; i < MAX_ARMS; i++) {
                lowerArmLength[i] = savefile.ReadFloat();
            }

            for (i = 0; i < MAX_ARMS; i++) {
                savefile.ReadMat3(upperArmToShoulderJoint[i]);
            }
            for (i = 0; i < MAX_ARMS; i++) {
                savefile.ReadMat3(lowerArmToElbowJoint[i]);
            }
        }

        @Override
        public boolean Init(idEntity self, final String anim, final idVec3 modelOffset) {
            int i;
            String jointName;
            idTraceModel trm = new idTraceModel();
            final idVec3 dir = new idVec3(), handOrigin = new idVec3(), elbowOrigin = new idVec3(), shoulderOrigin = new idVec3(), dirOrigin = new idVec3();
            idMat3 axis = new idMat3(), handAxis = new idMat3(), elbowAxis, shoulderAxis;

            if (null == self) {
                return false;
            }

            numArms = Min(self.spawnArgs.GetInt("ik_numArms", "0"), MAX_ARMS);
            if (numArms == 0) {
                return true;
            }

            if (!super.Init(self, anim, modelOffset)) {
                return false;
            }

            int numJoints = animator.NumJoints();
            idJointMat[] joints = new idJointMat[numJoints];

            // create the animation frame used to setup the IK
            GameEdit.gameEdit.ANIM_CreateAnimFrame(animator.ModelHandle(), animator.GetAnim(modifiedAnim).MD5Anim(0), numJoints, joints, 1, animator.ModelDef().GetVisualOffset().oPlus(modelOffset), animator.RemoveOrigin());

            enabledArms = 0;

            // get all the joints
            for (i = 0; i < numArms; i++) {

                jointName = self.spawnArgs.GetString(va("ik_hand%d", i + 1));
                handJoints[i] = animator.GetJointHandle(jointName);
                if (handJoints[i] == INVALID_JOINT) {
                    Game_local.idGameLocal.Error("idIK_Reach::Init: invalid hand joint '%s'", jointName);
                }

                jointName = self.spawnArgs.GetString(va("ik_elbow%d", i + 1));
                elbowJoints[i] = animator.GetJointHandle(jointName);
                if (elbowJoints[i] == INVALID_JOINT) {
                    Game_local.idGameLocal.Error("idIK_Reach::Init: invalid elbow joint '%s'\n", jointName);
                }

                jointName = self.spawnArgs.GetString(va("ik_shoulder%d", i + 1));
                shoulderJoints[i] = animator.GetJointHandle(jointName);
                if (shoulderJoints[i] == INVALID_JOINT) {
                    Game_local.idGameLocal.Error("idIK_Reach::Init: invalid shoulder joint '%s'\n", jointName);
                }

                jointName = self.spawnArgs.GetString(va("ik_elbowDir%d", i + 1));
                dirJoints[i] = animator.GetJointHandle(jointName);

                enabledArms |= 1 << i;
            }

            // get the arm bone lengths and rotation matrices
            for (i = 0; i < numArms; i++) {

                handAxis = joints[handJoints[i]].ToMat3();
                handOrigin.oSet(joints[handJoints[i]].ToVec3());

                elbowAxis = joints[elbowJoints[i]].ToMat3();
                elbowOrigin.oSet(joints[elbowJoints[i]].ToVec3());

                shoulderAxis = joints[shoulderJoints[i]].ToMat3();
                shoulderOrigin.oSet(joints[shoulderJoints[i]].ToVec3());

                // get the IK direction
                if (dirJoints[i] != INVALID_JOINT) {
                    dirOrigin.oSet(joints[dirJoints[i]].ToVec3());
                    dir.oSet(dirOrigin.oMinus(elbowOrigin));
                } else {
                    dir.Set(-1.0f, 0.0f, 0.0f);
                }

                shoulderForward[i].oSet(dir.oMultiply(shoulderAxis.Transpose()));
                elbowForward[i].oSet(dir.oMultiply(elbowAxis.Transpose()));

                // conversion from upper arm bone axis to should joint axis
                upperArmLength[i] = GetBoneAxis(shoulderOrigin, elbowOrigin, dir, axis);
                upperArmToShoulderJoint[i] = shoulderAxis.oMultiply(axis.Transpose());

                // conversion from lower arm bone axis to elbow joint axis
                lowerArmLength[i] = GetBoneAxis(elbowOrigin, handOrigin, dir, axis);
                lowerArmToElbowJoint[i] = elbowAxis.oMultiply(axis.Transpose());
            }

            initialized = true;

            return true;
        }

        @Override
        public void Evaluate() {
            int i;
            final idVec3 modelOrigin = new idVec3(), shoulderOrigin = new idVec3(), elbowOrigin = new idVec3(), handOrigin = new idVec3(), shoulderDir = new idVec3(), elbowDir = new idVec3();
            idMat3 modelAxis, axis = new idMat3();
            idMat3[] shoulderAxis = new idMat3[MAX_ARMS], elbowAxis = new idMat3[MAX_ARMS];
            trace_s trace = new trace_s();

            modelOrigin.oSet(self.GetRenderEntity().origin);
            modelAxis = self.GetRenderEntity().axis;

            // solve IK
            for (i = 0; i < numArms; i++) {

                // get the position of the shoulder in world space
                animator.GetJointTransform(shoulderJoints[i], gameLocal.time, shoulderOrigin, axis);
                shoulderOrigin.oSet(modelOrigin.oPlus(shoulderOrigin.oMultiply(modelAxis)));
                shoulderDir.oSet(shoulderForward[i].oMultiply(axis.oMultiply(modelAxis)));

                // get the position of the hand in world space
                animator.GetJointTransform(handJoints[i], gameLocal.time, handOrigin, axis);
                handOrigin.oSet(modelOrigin.oPlus(handOrigin.oMultiply(modelAxis)));

                // get first collision going from shoulder to hand
                gameLocal.clip.TracePoint(trace, shoulderOrigin, handOrigin, CONTENTS_SOLID, self);
                handOrigin.oSet(trace.endpos);

                // get the IK bend direction
                animator.GetJointTransform(elbowJoints[i], gameLocal.time, elbowOrigin, axis);
                elbowDir.oSet(elbowForward[i].oMultiply(axis.oMultiply(modelAxis)));

                // solve IK and calculate elbow position
                SolveTwoBones(shoulderOrigin, handOrigin, elbowDir, upperArmLength[i], lowerArmLength[i], elbowOrigin);

                if (ik_debug.GetBool()) {
                    gameRenderWorld.DebugLine(colorCyan, shoulderOrigin, elbowOrigin);
                    gameRenderWorld.DebugLine(colorRed, elbowOrigin, handOrigin);
                    gameRenderWorld.DebugLine(colorYellow, elbowOrigin, elbowOrigin.oPlus(elbowDir));
                    gameRenderWorld.DebugLine(colorGreen, elbowOrigin, elbowOrigin.oPlus(shoulderDir));
                }

                // get the axis for the shoulder joint
                GetBoneAxis(shoulderOrigin, elbowOrigin, shoulderDir, axis);
                shoulderAxis[i] = upperArmToShoulderJoint[i].oMultiply(axis.oMultiply(modelAxis.Transpose()));

                // get the axis for the elbow joint
                GetBoneAxis(elbowOrigin, handOrigin, elbowDir, axis);
                elbowAxis[i] = lowerArmToElbowJoint[i].oMultiply(axis.oMultiply(modelAxis.Transpose()));
            }

            for (i = 0; i < numArms; i++) {
                animator.SetJointAxis(shoulderJoints[i], JOINTMOD_WORLD_OVERRIDE, shoulderAxis[i]);
                animator.SetJointAxis(elbowJoints[i], JOINTMOD_WORLD_OVERRIDE, elbowAxis[i]);
            }

            ik_activate = true;
        }

        @Override
        public void ClearJointMods() {
            int i;

            if (null == self || !ik_activate) {
                return;
            }

            for (i = 0; i < numArms; i++) {
                animator.SetJointAxis(shoulderJoints[i], JOINTMOD_NONE, getMat3_identity());
                animator.SetJointAxis(elbowJoints[i], JOINTMOD_NONE, getMat3_identity());
                animator.SetJointAxis(handJoints[i], JOINTMOD_NONE, getMat3_identity());
            }

            ik_activate = false;
        }
    }

}

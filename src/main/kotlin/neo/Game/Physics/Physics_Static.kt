/*
 * Copyright (C) 1999-2011 id Software LLC, a ZeniMax Media company.
 * Translated to Kotlin by Dr. Feederino with support of Claude Code
 *
 * This file is part of the Doom 3 Kotlin project.
 * Original source: neo/Game/Physics/Physics_Static.h, neo/Game/Physics/Physics_Static.cpp
 */

package neo.Game.Physics

import neo.Game.GameSys.Class.idClass
import neo.Game.GameSys.Class.idTypeInfo
import neo.Game.GameSys.SaveGame.idRestoreGame
import neo.Game.GameSys.SaveGame.idSaveGame
import neo.Game.GameSys.SysCvar
import neo.Game.Game_local
import neo.Game.Physics.Clip.idClipModel
import neo.Game.Physics.Force.idForce
import neo.Game.Physics.Physics.idPhysics
import neo.Game.Physics.Physics.impactInfo_s
import neo.Game.idEntity
import neo.cm.contactInfo_t
import neo.cm.trace_s
import neo.idlib.BV.bounds_zero
import neo.idlib.BV.idBounds
import neo.idlib.BitMsg.idBitMsgDelta
import neo.idlib.math.Matrix.idMat3
import neo.idlib.math.idCQuat
import neo.idlib.math.idRotation
import neo.idlib.math.idVec3
import neo.idlib.math.vec3_origin

class Physics_Static {
    /*
     ===============================================================================

     Physics for a non moving object using at most one collision model.

     ===============================================================================
     */
    class staticPState_s {
        val axis: idMat3 = idMat3()
        val localAxis: idMat3 = idMat3()
        val localOrigin: idVec3 = idVec3()
        val origin: idVec3 = idVec3()
    }

    class idPhysics_Static : idPhysics() {
        protected var clipModel // collision model
                : idClipModel? = null
        protected var current // physics state
                : staticPState_s

        // master
        protected var hasMaster: Boolean
        protected var isOrientated: Boolean
        protected var self // entity using this physics object
                : idEntity? = null
        private val masterOriginScratch = idVec3()
        private val masterAxisScratch = idMat3()
        private val oldOriginScratch = idVec3()
        private val oldAxisScratch = idMat3()

        /*
        ================
        idPhysics_Static::~idPhysics_Static
        ================
        */
        override fun _deconstructor() {
            if (self != null && self!!.GetPhysics() === this) {
                self!!.SetPhysics(null)
            }
            idForce.DeletePhysics(this)
            if (clipModel != null) {
                idClipModel.delete(clipModel!!)
            }
            super._deconstructor()
        }

        /*
        ================
        idPhysics_Static::Save
        ================
        */
        override fun Save(savefile: idSaveGame) {
            super.Save(savefile)
            savefile.WriteObject(self as idClass?)
            savefile.WriteVec3(current.origin)
            savefile.WriteMat3(current.axis)
            savefile.WriteVec3(current.localOrigin)
            savefile.WriteMat3(current.localAxis)
            savefile.WriteClipModel(clipModel)
            savefile.WriteBool(hasMaster)
            savefile.WriteBool(isOrientated)
        }

        /*
        ================
        idPhysics_Static::Restore
        ================
        */
        override fun Restore(savefile: idRestoreGame) {
            super.Restore(savefile)

            self = savefile.ReadObject() as idEntity?
            savefile.ReadVec3(current.origin)
            savefile.ReadMat3(current.axis)
            savefile.ReadVec3(current.localOrigin)
            savefile.ReadMat3(current.localAxis)
            clipModel = savefile.ReadClipModel()
            hasMaster = savefile.ReadBool()
            isOrientated = savefile.ReadBool()
        }

        // common physics interface
        /*
        ================
        idPhysics_Static::SetSelf
        ================
        */
        override fun SetSelf(e: idEntity) {
            assert(e != null)
            self = e
        }

        /*
        ================
        idPhysics_Static::SetClipModel
        ================
        */
        override fun SetClipModel(model: idClipModel?, density: Float, id: Int /*= 0*/, freeOld: Boolean /*= true*/) {
            assert(self != null)
            if (clipModel != null && clipModel !== model && freeOld) {
                idClipModel.delete(clipModel!!)
            }
            clipModel = model
            clipModel?.Link(Game_local.gameLocal.clip, self, 0, current.origin, current.axis)
        }

        /*
        ================
        idPhysics_Static::GetClipModel
        ================
        */
        override fun GetClipModel(id: Int /*= 0*/): idClipModel {
            return if (clipModel != null) {
                clipModel!!
            } else Game_local.gameLocal.clip.DefaultClipModel()
        }

        /*
        ================
        idPhysics_Static::GetNumClipModels
        ================
        */
        override fun GetNumClipModels(): Int {
            return if (clipModel != null) 1 else 0
        }

        /*
        ================
        idPhysics_Static::SetMass
        ================
        */
        override fun SetMass(mass: Float, id: Int /*= -1*/) {}

        /*
        ================
        idPhysics_Static::GetMass
        ================
        */
        override fun GetMass(id: Int /*= -1*/): Float {
            return 0.0f
        }

        /*
        ================
        idPhysics_Static::SetContents
        ================
        */
        override fun SetContents(contents: Int, id: Int /*= -1*/) {
            if (clipModel != null) {
                clipModel!!.SetContents(contents)
            }
        }

        /*
        ================
        idPhysics_Static::GetContents
        ================
        */
        override fun GetContents(id: Int /*= -1*/): Int {
            return if (clipModel != null) {
                clipModel!!.GetContents()
            } else 0
        }

        /*
        ================
        idPhysics_Static::SetClipMask
        ================
        */
        override fun SetClipMask(mask: Int, id: Int /*= -1*/) {}

        /*
        ================
        idPhysics_Static::GetClipMask
        ================
        */
        override fun GetClipMask(id: Int /*= -1*/): Int {
            return 0
        }

        /*
        ================
        idPhysics_Static::GetBounds
        ================
        */
        override fun GetBounds(id: Int /*= -1*/): idBounds {
            return if (clipModel != null) {
                clipModel!!.GetBounds()
            } else bounds_zero
        }

        /*
        ================
        idPhysics_Static::GetAbsBounds
        ================
        */
        override fun GetAbsBounds(id: Int /*= -1*/): idBounds {
            if (clipModel != null) {
                return clipModel!!.GetAbsBounds()
            }
            absBounds.set(idBounds(current.origin, current.origin))
            return absBounds
        }

        /*
        ================
        idPhysics_Static::Evaluate
        ================
        */
        override fun Evaluate(timeStepMSec: Int, endTimeMSec: Int): Boolean {
            if (hasMaster) {
                oldOriginScratch.set(current.origin)
                oldAxisScratch.set(current.axis)
                self!!.GetMasterPosition(masterOriginScratch, masterAxisScratch)
                TransformLocalToMaster(current.origin, masterOriginScratch, current.localOrigin, masterAxisScratch)
                if (isOrientated) {
                    current.axis.setMul(current.localAxis, masterAxisScratch)
                } else {
                    current.axis.set(current.localAxis)
                }
                clipModel?.Link(Game_local.gameLocal.clip, self, 0, current.origin, current.axis)
                return current.origin != oldOriginScratch || current.axis != oldAxisScratch
            }
            return false
        }

        /*
        ================
        idPhysics_Static::UpdateTime
        ================
        */
        override fun UpdateTime(endTimeMSec: Int) {}

        /*
        ================
        idPhysics_Static::GetTime
        ================
        */
        override fun GetTime(): Int {
            return 0
        }

        /*
        ================
        idPhysics_Static::GetImpactInfo
        ================
        */
        override fun GetImpactInfo(id: Int, point: idVec3): impactInfo_s {
            return impactInfo_s()
        }

        /*
        ================
        idPhysics_Static::ApplyImpulse
        ================
        */
        override fun ApplyImpulse(id: Int, point: idVec3, impulse: idVec3) {}

        /*
        ================
        idPhysics_Static::AddForce
        ================
        */
        override fun AddForce(id: Int, point: idVec3, force: idVec3) {}

        /*
        ================
        idPhysics_Static::Activate
        ================
        */
        override fun Activate() {}

        /*
        ================
        idPhysics_Static::PutToRest
        ================
        */
        override fun PutToRest() {}

        /*
        ================
        idPhysics_Static::IsAtRest
        ================
        */
        override fun IsAtRest(): Boolean {
            return true
        }

        /*
        ================
        idPhysics_Static::GetRestStartTime
        ================
        */
        override fun GetRestStartTime(): Int {
            return 0
        }

        /*
        ================
        idPhysics_Static::IsPushable
        ================
        */
        override fun IsPushable(): Boolean {
            return false
        }

        /*
        ================
        idPhysics_Static::SaveState
        ================
        */
        override fun SaveState() {}

        /*
        ================
        idPhysics_Static::RestoreState
        ================
        */
        override fun RestoreState() {}

        /*
        ================
        idPhysics_Static::SetOrigin
        ================
        */
        override fun SetOrigin(newOrigin: idVec3, id: Int /*= -1*/) {
            current.localOrigin.set(newOrigin)
            if (hasMaster) {
                self!!.GetMasterPosition(masterOriginScratch, masterAxisScratch)
                TransformLocalToMaster(current.origin, masterOriginScratch, newOrigin, masterAxisScratch)
            } else {
                current.origin.set(newOrigin)
            }
            clipModel?.Link(Game_local.gameLocal.clip, self, 0, current.origin, current.axis)
        }

        /*
        ================
        idPhysics_Static::SetAxis
        ================
        */
        override fun SetAxis(newAxis: idMat3, id: Int /*= -1*/) {
            current.localAxis.set(newAxis)
            if (hasMaster && isOrientated) {
                self!!.GetMasterPosition(masterOriginScratch, masterAxisScratch)
                current.axis.setMul(newAxis, masterAxisScratch)
            } else {
                current.axis.set(newAxis)
            }
            clipModel?.Link(Game_local.gameLocal.clip, self, 0, current.origin, current.axis)
        }

        /*
        ================
        idPhysics_Static::Translate
        ================
        */
        override fun Translate(translation: idVec3, id: Int /*= -1*/) {
            current.localOrigin.plusAssign(translation)
            current.origin.plusAssign(translation)
            clipModel?.Link(Game_local.gameLocal.clip, self, 0, current.origin, current.axis)
        }

        /*
        ================
        idPhysics_Static::Rotate
        ================
        */
        override fun Rotate(rotation: idRotation, id: Int /*= -1*/) {
            val rotationAxis = rotation.ToMat3()
            current.origin.timesAssign(rotation)
            current.axis.timesAssign(rotationAxis)
            if (hasMaster) {
                self!!.GetMasterPosition(masterOriginScratch, masterAxisScratch)
                current.localAxis.timesAssign(rotationAxis)
                TransformWorldToLocal(current.localOrigin, current.origin, masterOriginScratch, masterAxisScratch)
            } else {
                current.localAxis.set(current.axis)
                current.localOrigin.set(current.origin)
            }
            clipModel?.Link(Game_local.gameLocal.clip, self, 0, current.origin, current.axis)
        }

        /*
        ================
        idPhysics_Static::GetOrigin
        ================
        */
        override fun GetOrigin(id: Int /*= 0*/): idVec3 {
            return current.origin
        }

        /*
        ================
        idPhysics_Static::GetAxis
        ================
        */
        override fun GetAxis(id: Int /*= 0*/): idMat3 {
            return current.axis
        }

        /*
        ================
        idPhysics_Static::SetLinearVelocity
        ================
        */
        override fun SetLinearVelocity(newLinearVelocity: idVec3, id: Int /*= 0*/) {}

        /*
        ================
        idPhysics_Static::SetAngularVelocity
        ================
        */
        override fun SetAngularVelocity(newAngularVelocity: idVec3, id: Int /*= 0*/) {}

        /*
        ================
        idPhysics_Static::GetLinearVelocity
        ================
        */
        override fun GetLinearVelocity(id: Int /*= 0*/): idVec3 {
            return vec3_origin
        }

        /*
        ================
        idPhysics_Static::GetAngularVelocity
        ================
        */
        override fun GetAngularVelocity(id: Int /*= 0*/): idVec3 {
            return vec3_origin
        }

        /*
        ================
        idPhysics_Static::SetGravity
        ================
        */
        override fun SetGravity(newGravity: idVec3) {}

        /*
        ================
        idPhysics_Static::GetGravity
        ================
        */
        override fun GetGravity(): idVec3 {
            return gravity
        }

        /*
        ================
        idPhysics_Static::GetGravityNormal
        ================
        */
        override fun GetGravityNormal(): idVec3 {
            return gravityNormal
        }

        /*
        ================
        idPhysics_Static::ClipTranslation
        ================
        */
        override fun ClipTranslation(results: trace_s, translation: idVec3, model: idClipModel?) {
            if (model != null) {
                Game_local.gameLocal.clip.TranslationModel(
                    results, current.origin, current.origin + translation,
                    clipModel, current.axis, Game_local.MASK_SOLID, model.Handle(), model.GetOrigin(), model.GetAxis()
                )
            } else {
                Game_local.gameLocal.clip.Translation(
                    results, current.origin, current.origin + translation,
                    clipModel, current.axis, Game_local.MASK_SOLID, self
                )
            }
        }

        /*
        ================
        idPhysics_Static::ClipRotation
        ================
        */
        override fun ClipRotation(results: trace_s, rotation: idRotation, model: idClipModel?) {
            if (model != null) {
                Game_local.gameLocal.clip.RotationModel(
                    results, current.origin, rotation,
                    clipModel, current.axis, Game_local.MASK_SOLID, model.Handle(), model.GetOrigin(), model.GetAxis()
                )
            } else {
                Game_local.gameLocal.clip.Rotation(
                    results,
                    current.origin,
                    rotation,
                    clipModel,
                    current.axis,
                    Game_local.MASK_SOLID,
                    self
                )
            }
        }

        /*
        ================
        idPhysics_Static::ClipContents
        ================
        */
        override fun ClipContents(model: idClipModel?): Int {
            return if (clipModel != null) {
                if (model != null) {
                    Game_local.gameLocal.clip.ContentsModel(
                        clipModel!!.GetOrigin(), clipModel, clipModel!!.GetAxis(), -1,
                        model.Handle(), model.GetOrigin(), model.GetAxis()
                    )
                } else {
                    Game_local.gameLocal.clip.Contents(
                        clipModel!!.GetOrigin(),
                        clipModel,
                        clipModel!!.GetAxis(),
                        -1,
                        null
                    )
                }
            } else 0
        }

        /*
        ================
        idPhysics_Static::DisableClip
        ================
        */
        override fun DisableClip() {
            clipModel?.Disable()
        }

        /*
        ================
        idPhysics_Static::EnableClip
        ================
        */
        override fun EnableClip() {
            clipModel?.Enable()
        }

        /*
        ================
        idPhysics_Static::UnlinkClip
        ================
        */
        override fun UnlinkClip() {
            clipModel?.Unlink()
        }

        /*
        ================
        idPhysics_Static::LinkClip
        ================
        */
        override fun LinkClip() {
            clipModel?.Link(Game_local.gameLocal.clip, self, 0, current.origin, current.axis)
        }

        /*
        ================
        idPhysics_Static::EvaluateContacts
        ================
        */
        override fun EvaluateContacts(): Boolean {
            return false
        }

        /*
        ================
        idPhysics_Static::GetNumContacts
        ================
        */
        override fun GetNumContacts(): Int {
            return 0
        }

        /*
        ================
        idPhysics_Static::GetContact
        ================
        */
        override fun GetContact(num: Int): contactInfo_t {
            return contactInfo_t()
        }

        /*
        ================
        idPhysics_Static::ClearContacts
        ================
        */
        override fun ClearContacts() {}

        /*
        ================
        idPhysics_Static::AddContactEntity
        ================
        */
        override fun AddContactEntity(e: idEntity) {}

        /*
        ================
        idPhysics_Static::RemoveContactEntity
        ================
        */
        override fun RemoveContactEntity(e: idEntity) {}

        /*
        ================
        idPhysics_Static::HasGroundContacts
        ================
        */
        override fun HasGroundContacts(): Boolean {
            return false
        }

        /*
        ================
        idPhysics_Static::IsGroundEntity
        ================
        */
        override fun IsGroundEntity(entityNum: Int): Boolean {
            return false
        }

        /*
        ================
        idPhysics_Static::IsGroundClipModel
        ================
        */
        override fun IsGroundClipModel(entityNum: Int, id: Int): Boolean {
            return false
        }

        /*
        ================
        idPhysics_Static::SetPushed
        ================
        */
        override fun SetPushed(deltaTime: Int) {}

        /*
        ================
        idPhysics_Static::GetPushedLinearVelocity
        ================
        */
        override fun GetPushedLinearVelocity(id: Int /*= 0*/): idVec3 {
            return vec3_origin
        }

        /*
        ================
        idPhysics_Static::GetPushedAngularVelocity
        ================
        */
        override fun GetPushedAngularVelocity(id: Int /*= 0*/): idVec3 {
            return vec3_origin
        }

        /*
        ================
        idPhysics_Static::SetMaster
        ================
        */
        override fun SetMaster(master: idEntity?, orientated: Boolean /*= true*/) {
            if (master != null) {
                if (!hasMaster) {
                    // transform from world space to master space
                    self!!.GetMasterPosition(masterOriginScratch, masterAxisScratch)
                    TransformWorldToLocal(current.localOrigin, current.origin, masterOriginScratch, masterAxisScratch)
                    if (orientated) {
                        current.localAxis.setMul(current.axis, masterAxisScratch.TransposeSelf())
                        masterAxisScratch.TransposeSelf()
                    } else {
                        current.localAxis.set(current.axis)
                    }
                    hasMaster = true
                    isOrientated = orientated
                }
            } else {
                if (hasMaster) {
                    hasMaster = false
                }
            }
        }

        private fun TransformLocalToMaster(dst: idVec3, masterOrigin: idVec3, localOrigin: idVec3, masterAxis: idMat3) {
            val x =
                masterOrigin.x + masterAxis[0][0] * localOrigin.x + masterAxis[1][0] * localOrigin.y + masterAxis[2][0] * localOrigin.z
            val y =
                masterOrigin.y + masterAxis[0][1] * localOrigin.x + masterAxis[1][1] * localOrigin.y + masterAxis[2][1] * localOrigin.z
            val z =
                masterOrigin.z + masterAxis[0][2] * localOrigin.x + masterAxis[1][2] * localOrigin.y + masterAxis[2][2] * localOrigin.z
            dst.set(x, y, z)
        }

        private fun TransformWorldToLocal(dst: idVec3, worldOrigin: idVec3, masterOrigin: idVec3, masterAxis: idMat3) {
            val dx = worldOrigin.x - masterOrigin.x
            val dy = worldOrigin.y - masterOrigin.y
            val dz = worldOrigin.z - masterOrigin.z
            val x = masterAxis[0][0] * dx + masterAxis[0][1] * dy + masterAxis[0][2] * dz
            val y = masterAxis[1][0] * dx + masterAxis[1][1] * dy + masterAxis[1][2] * dz
            val z = masterAxis[2][0] * dx + masterAxis[2][1] * dy + masterAxis[2][2] * dz
            dst.set(x, y, z)
        }

        /*
        ================
        idPhysics_Static::GetBlockingInfo
        ================
        */
        override fun GetBlockingInfo(): trace_s? {
            return null
        }

        /*
        ================
        idPhysics_Static::GetBlockingEntity
        ================
        */
        override fun GetBlockingEntity(): idEntity? {
            return null
        }

        /*
        ================
        idPhysics_Static::GetLinearEndTime
        ================
        */
        override fun GetLinearEndTime(): Int {
            return 0
        }

        /*
        ================
        idPhysics_Static::GetAngularEndTime
        ================
        */
        override fun GetAngularEndTime(): Int {
            return 0
        }

        /*
        ================
        idPhysics_Static::WriteToSnapshot
        ================
        */
        override fun WriteToSnapshot(msg: idBitMsgDelta) {
            val quat: idCQuat
            val localQuat: idCQuat
            quat = current.axis.ToCQuat()
            localQuat = current.localAxis.ToCQuat()
            msg.WriteFloat(current.origin[0])
            msg.WriteFloat(current.origin[1])
            msg.WriteFloat(current.origin[2])
            msg.WriteFloat(quat.x)
            msg.WriteFloat(quat.y)
            msg.WriteFloat(quat.z)
            msg.WriteDeltaFloat(current.origin[0], current.localOrigin[0])
            msg.WriteDeltaFloat(current.origin[1], current.localOrigin[1])
            msg.WriteDeltaFloat(current.origin[2], current.localOrigin[2])
            msg.WriteDeltaFloat(quat.x, localQuat.x)
            msg.WriteDeltaFloat(quat.y, localQuat.y)
            msg.WriteDeltaFloat(quat.z, localQuat.z)
        }

        /*
        ================
        idPhysics_Base::ReadFromSnapshot
        ================
        */
        override fun ReadFromSnapshot(msg: idBitMsgDelta) {
            val quat = idCQuat()
            val localQuat = idCQuat()
            current.origin[0] = msg.ReadFloat()
            current.origin[1] = msg.ReadFloat()
            current.origin[2] = msg.ReadFloat()
            quat.x = msg.ReadFloat()
            quat.y = msg.ReadFloat()
            quat.z = msg.ReadFloat()
            current.localOrigin[0] = msg.ReadDeltaFloat(current.origin[0])
            current.localOrigin[1] = msg.ReadDeltaFloat(current.origin[1])
            current.localOrigin[2] = msg.ReadDeltaFloat(current.origin[2])
            localQuat.x = msg.ReadDeltaFloat(quat.x)
            localQuat.y = msg.ReadDeltaFloat(quat.y)
            localQuat.z = msg.ReadDeltaFloat(quat.z)
            current.axis.set(quat.ToMat3())
            current.localAxis.set(localQuat.ToMat3())
        }

        override fun CreateInstance(): idClass = idPhysics_Static()

        override fun GetType(): idTypeInfo = Type

        override fun oSet(oGet: idClass?) {
            throw UnsupportedOperationException("Not supported yet.")
        }

        companion object {
            val Type = idTypeInfo("idPhysics_Static", "idPhysics") { idPhysics_Static() }

            // CLASS_PROTOTYPE( idPhysics_Static );
            private val gravity: idVec3 = idVec3(0.0f, 0.0f, -SysCvar.g_gravity.GetFloat())
            private val gravityNormal: idVec3 = idVec3(0, 0, -1)
            private val absBounds: idBounds = idBounds()
        }

        /*
        ================
        idPhysics_Static::idPhysics_Static
        ================
        */
        init {
            current = staticPState_s()
            current.origin.Zero()
            current.axis.Identity()
            current.localOrigin.Zero()
            current.localAxis.Identity()
            hasMaster = false
            isOrientated = false
        }
    }
}

/*
 * Copyright (C) 1999-2011 id Software LLC, a ZeniMax Media company.
 * Translated to Kotlin by Dr. Feederino with support of Claude Code
 *
 * This file is part of the Doom 3 Kotlin project.
 * Original source: neo/Game/Physics/Physics_Static.h, neo/Game/Physics/Physics_Static.cpp
 */

package neo.Game.Physics

import neo.Game.Entity.idEntity
import neo.Game.GameSys.Class.idClass
import neo.Game.GameSys.SaveGame.idRestoreGame
import neo.Game.GameSys.SaveGame.idSaveGame
import neo.Game.GameSys.SysCvar
import neo.Game.Game_local
import neo.Game.Physics.Clip.idClipModel
import neo.Game.Physics.Force.idForce
import neo.Game.Physics.Physics.idPhysics
import neo.Game.Physics.Physics.impactInfo_s
import neo.cm.contactInfo_t
import neo.cm.trace_s
import neo.idlib.BV.bounds_zero
import neo.idlib.BV.idBounds
import neo.idlib.BitMsg.idBitMsgDelta
import neo.idlib.math.Matrix.idMat3
import neo.idlib.math.getVec3Origin
import neo.idlib.math.idCQuat
import neo.idlib.math.idRotation
import neo.idlib.math.idVec3

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

        override fun Save(savefile: idSaveGame) {
            savefile.WriteObject(self as idClass?)
            savefile.WriteVec3(current.origin)
            savefile.WriteMat3(current.axis)
            savefile.WriteVec3(current.localOrigin)
            savefile.WriteMat3(current.localAxis)
            savefile.WriteClipModel(clipModel)
            savefile.WriteBool(hasMaster)
            savefile.WriteBool(isOrientated)
        }

        override fun Restore(savefile: idRestoreGame) {
            savefile.ReadObject( /*reinterpret_cast<idClass*&>*/self)
            savefile.ReadVec3(current.origin)
            savefile.ReadMat3(current.axis)
            savefile.ReadVec3(current.localOrigin)
            savefile.ReadMat3(current.localAxis)
            savefile.ReadClipModel(clipModel!!)
            hasMaster = savefile.ReadBool()
            isOrientated = savefile.ReadBool()
        }

        // common physics interface
        override fun SetSelf(e: idEntity) {
            assert(e != null)
            self = e
        }

        override fun SetClipModel(model: idClipModel?, density: Float, id: Int /*= 0*/, freeOld: Boolean /*= true*/) {
            assert(self != null)
            if (clipModel != null && clipModel !== model && freeOld) {
                idClipModel.delete(clipModel!!)
            }
            clipModel = model
            clipModel?.Link(Game_local.gameLocal.clip, self, 0, current.origin, current.axis)
        }

        override fun GetClipModel(id: Int /*= 0*/): idClipModel {
            return if (clipModel != null) {
                clipModel!!
            } else Game_local.gameLocal.clip.DefaultClipModel()
        }

        override fun GetNumClipModels(): Int {
            return if (clipModel != null) 1 else 0
        }

        override fun SetMass(mass: Float, id: Int /*= -1*/) {}
        override fun GetMass(id: Int /*= -1*/): Float {
            return 0.0f
        }

        override fun SetContents(contents: Int, id: Int /*= -1*/) {
            if (clipModel != null) {
                clipModel!!.SetContents(contents)
            }
        }

        override fun GetContents(id: Int /*= -1*/): Int {
            return if (clipModel != null) {
                clipModel!!.GetContents()
            } else 0
        }

        override fun SetClipMask(mask: Int, id: Int /*= -1*/) {}
        override fun GetClipMask(id: Int /*= -1*/): Int {
            return 0
        }

        override fun GetBounds(id: Int /*= -1*/): idBounds {
            return if (clipModel != null) {
                clipModel!!.GetBounds()
            } else bounds_zero
        }

        override fun GetAbsBounds(id: Int /*= -1*/): idBounds {
            if (clipModel != null) {
                return clipModel!!.GetAbsBounds()
            }
            absBounds.set(idBounds(current.origin, current.origin))
            return absBounds
        }

        override fun Evaluate(timeStepMSec: Int, endTimeMSec: Int): Boolean {
            val masterOrigin = idVec3()
            val oldOrigin = idVec3()
            val masterAxis = idMat3()
            val oldAxis = idMat3()
            if (hasMaster) {
                oldOrigin.set(current.origin)
                oldAxis.set(current.axis)
                self!!.GetMasterPosition(masterOrigin, masterAxis)
                current.origin.set(masterOrigin + current.localOrigin * masterAxis)
                if (isOrientated) {
                    current.axis.set(current.localAxis.times(masterAxis))
                } else {
                    current.axis.set(current.localAxis)
                }
                clipModel?.Link(Game_local.gameLocal.clip, self, 0, current.origin, current.axis)
                return current.origin != oldOrigin || current.axis != oldAxis
            }
            return false
        }

        override fun UpdateTime(endTimeMSec: Int) {}
        override fun GetTime(): Int {
            return 0
        }

        override fun GetImpactInfo(id: Int, point: idVec3): impactInfo_s {
            return impactInfo_s()
        }

        override fun ApplyImpulse(id: Int, point: idVec3, impulse: idVec3) {}
        override fun AddForce(id: Int, point: idVec3, force: idVec3) {}
        override fun Activate() {}
        override fun PutToRest() {}
        override fun IsAtRest(): Boolean {
            return true
        }

        override fun GetRestStartTime(): Int {
            return 0
        }

        override fun IsPushable(): Boolean {
            return false
        }

        override fun SaveState() {}
        override fun RestoreState() {}
        override fun SetOrigin(newOrigin: idVec3, id: Int /*= -1*/) {
            val masterOrigin = idVec3()
            val masterAxis = idMat3()
            current.localOrigin.set(newOrigin)
            if (hasMaster) {
                self!!.GetMasterPosition(masterOrigin, masterAxis)
                current.origin.set(masterOrigin + newOrigin * masterAxis)
            } else {
                current.origin.set(newOrigin)
            }
            clipModel?.Link(Game_local.gameLocal.clip, self, 0, current.origin, current.axis)
        }

        override fun SetAxis(newAxis: idMat3, id: Int /*= -1*/) {
            val masterOrigin = idVec3()
            val masterAxis = idMat3()
            current.localAxis.set(newAxis)
            if (hasMaster && isOrientated) {
                self!!.GetMasterPosition(masterOrigin, masterAxis)
                current.axis.set(newAxis.times(masterAxis))
            } else {
                current.axis.set(newAxis)
            }
            clipModel?.Link(Game_local.gameLocal.clip, self, 0, current.origin, current.axis)
        }

        override fun Translate(translation: idVec3, id: Int /*= -1*/) {
            current.localOrigin.plusAssign(translation)
            current.origin.plusAssign(translation)
            clipModel?.Link(Game_local.gameLocal.clip, self, 0, current.origin, current.axis)
        }

        override fun Rotate(rotation: idRotation, id: Int /*= -1*/) {
            val masterOrigin = idVec3()
            val masterAxis = idMat3()
            current.origin.timesAssign(rotation)
            current.axis.timesAssign(rotation.ToMat3())
            if (hasMaster) {
                self!!.GetMasterPosition(masterOrigin, masterAxis)
                current.localAxis.timesAssign(rotation.ToMat3())
                current.localOrigin.set((current.origin - masterOrigin) * masterAxis.Transpose())
            } else {
                current.localAxis.set(current.axis)
                current.localOrigin.set(current.origin)
            }
            clipModel?.Link(Game_local.gameLocal.clip, self, 0, current.origin, current.axis)
        }

        override fun GetOrigin(id: Int /*= 0*/): idVec3 {
            return current.origin
        }

        override fun GetAxis(id: Int /*= 0*/): idMat3 {
            return current.axis
        }

        override fun SetLinearVelocity(newLinearVelocity: idVec3, id: Int /*= 0*/) {}
        override fun SetAngularVelocity(newAngularVelocity: idVec3, id: Int /*= 0*/) {}
        override fun GetLinearVelocity(id: Int /*= 0*/): idVec3 {
            return getVec3Origin()
        }

        override fun GetAngularVelocity(id: Int /*= 0*/): idVec3 {
            return getVec3Origin()
        }

        override fun SetGravity(newGravity: idVec3) {}
        override fun GetGravity(): idVec3 {
            return gravity
        }

        override fun GetGravityNormal(): idVec3 {
            return gravityNormal
        }

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

        override fun DisableClip() {
            clipModel?.Disable()
        }

        override fun EnableClip() {
            clipModel?.Enable()
        }

        override fun UnlinkClip() {
            clipModel?.Unlink()
        }

        override fun LinkClip() {
            clipModel?.Link(Game_local.gameLocal.clip, self, 0, current.origin, current.axis)
        }

        override fun EvaluateContacts(): Boolean {
            return false
        }

        override fun GetNumContacts(): Int {
            return 0
        }

        override fun GetContact(num: Int): contactInfo_t? {
//	memset( &info, 0, sizeof( info ) );
            return null
        }

        override fun ClearContacts() {}
        override fun AddContactEntity(e: idEntity) {}
        override fun RemoveContactEntity(e: idEntity) {}
        override fun HasGroundContacts(): Boolean {
            return false
        }

        override fun IsGroundEntity(entityNum: Int): Boolean {
            return false
        }

        override fun IsGroundClipModel(entityNum: Int, id: Int): Boolean {
            return false
        }

        override fun SetPushed(deltaTime: Int) {}
        override fun GetPushedLinearVelocity(id: Int /*= 0*/): idVec3 {
            return getVec3Origin()
        }

        override fun GetPushedAngularVelocity(id: Int /*= 0*/): idVec3 {
            return getVec3Origin()
        }

        override fun SetMaster(master: idEntity?, orientated: Boolean /*= true*/) {
            val masterOrigin = idVec3()
            val masterAxis = idMat3()
            if (master != null) {
                if (!hasMaster) {
                    // transform from world space to master space
                    self!!.GetMasterPosition(masterOrigin, masterAxis)
                    current.localOrigin.set((current.origin - masterOrigin) * masterAxis.Transpose())
                    if (orientated) {
                        current.localAxis.set(current.axis * masterAxis.Transpose())
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

        override fun GetBlockingInfo(): trace_s? {
            return null
        }

        override fun GetBlockingEntity(): idEntity? {
            return null
        }

        override fun GetLinearEndTime(): Int {
            return 0
        }

        override fun GetAngularEndTime(): Int {
            return 0
        }

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

        override fun CreateInstance(): idClass {
            throw UnsupportedOperationException("Not supported yet.")
        }

        override fun  /*idTypeInfo*/GetType(): Class<out idClass> {
            throw UnsupportedOperationException("Not supported yet.")
        }

        override fun oSet(oGet: idClass?) {
            throw UnsupportedOperationException("Not supported yet.")
        }

        companion object {
            // CLASS_PROTOTYPE( idPhysics_Static );
            private val gravity: idVec3 = idVec3(0.0f, 0.0f, -SysCvar.g_gravity.GetFloat())
            private val gravityNormal: idVec3 = idVec3(0, 0, -1)
            private val absBounds: idBounds = idBounds()
        }

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
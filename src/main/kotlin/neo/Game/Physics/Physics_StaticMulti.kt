/*
 * Copyright (C) 1999-2011 id Software LLC, a ZeniMax Media company.
 * Translated to Kotlin by Dr. Feederino with support of Claude Code
 *
 * This file is part of the Doom 3 Kotlin project.
 * Original source: neo/Game/Physics/Physics_StaticMulti.h, neo/Game/Physics/Physics_StaticMulti.cpp
 */

package neo.Game.Physics

import neo.Game.GameSys.Class.idClass
import neo.Game.GameSys.SaveGame.idRestoreGame
import neo.Game.GameSys.SaveGame.idSaveGame
import neo.Game.GameSys.SysCvar
import neo.Game.Game_local
import neo.Game.Physics.Clip.idClipModel
import neo.Game.Physics.Force.idForce
import neo.Game.Physics.Physics.idPhysics
import neo.Game.Physics.Physics.impactInfo_s
import neo.Game.Physics.Physics_Static.staticPState_s
import neo.Game.idEntity
import neo.cm.contactInfo_t
import neo.cm.trace_s
import neo.idlib.BV.bounds_zero
import neo.idlib.BV.idBounds
import neo.idlib.BitMsg.idBitMsgDelta
import neo.idlib.containers.CInt
import neo.idlib.containers.List.idList
import neo.idlib.math.Matrix.idMat3
import neo.idlib.math.idCQuat
import neo.idlib.math.idRotation
import neo.idlib.math.idVec3
import neo.idlib.math.vec3_origin

object Physics_StaticMulti {
    var defaultState: staticPState_s = staticPState_s()

    /*
     ===============================================================================

     Physics for a non moving object using no or multiple collision models.

     ===============================================================================
     */
    class idPhysics_StaticMulti : idPhysics() {
        protected val clipModels // collision model
                : idList<idClipModel?>
        protected val current // physics state
                : idList<staticPState_s>
        protected var hasMaster = false
        protected var isOrientated = false
        protected var self // entity using this physics object
                : idEntity? = null

        override fun _deconstructor() {
            if (self != null && self!!.GetPhysics() === this) {
                self!!.SetPhysics(null)
            }
            idForce.DeletePhysics(this)
            for (i in 0 until clipModels.Num()) {
                idClipModel.delete(clipModels[i])
            }
            super._deconstructor()
        }

        override fun Save(savefile: idSaveGame) {
            var i: Int
            savefile.WriteObject(self as idClass?)
            savefile.WriteInt(current.Num())
            i = 0
            while (i < current.Num()) {
                savefile.WriteVec3(current[i].origin)
                savefile.WriteMat3(current[i].axis)
                savefile.WriteVec3(current[i].localOrigin)
                savefile.WriteMat3(current[i].localAxis)
                i++
            }
            savefile.WriteInt(clipModels.Num())
            i = 0
            while (i < clipModels.Num()) {
                savefile.WriteClipModel(clipModels[i])
                i++
            }
            savefile.WriteBool(hasMaster)
            savefile.WriteBool(isOrientated)
        }

        override fun Restore(savefile: idRestoreGame) {
            var i: Int
            val num = CInt()
            savefile.ReadObject( /*reinterpret_cast<idClass *&>*/self)
            savefile.ReadInt(num)
            // FIX: AssureSize without fill value leaves new slots null in Kotlin.
            // C++ default-constructs POD structs. Must create objects for each slot.
            val oldNum = current.Num()
            current.AssureSize(num._val)
            for (j in oldNum until num._val) {
                current[j] = staticPState_s()
            }
            i = 0
            while (i < num._val) {
                savefile.ReadVec3(current[i].origin)
                savefile.ReadMat3(current[i].axis)
                savefile.ReadVec3(current[i].localOrigin)
                savefile.ReadMat3(current[i].localAxis)
                i++
            }
            savefile.ReadInt(num)
            clipModels.SetNum(num._val)
            i = 0
            while (i < num._val) {
                savefile.ReadClipModel(clipModels[i])
                i++
            }
            hasMaster = savefile.ReadBool()
            isOrientated = savefile.ReadBool()
        }


        fun RemoveIndex(id: Int /*= 0*/, freeClipModel: Boolean = true /*= true*/) {
            if (id < 0 || id >= clipModels.Num()) {
                return
            }
            if (freeClipModel) {
                idClipModel.delete(clipModels[id])
                //clipModels[id] = null
            }
            clipModels.RemoveIndex(id)
            current.RemoveIndex(id)
        }

        // common physics interface
        override fun SetSelf(e: idEntity) {
            assert(e != null)
            self = e
        }

        override fun SetClipModel(model: idClipModel?, density: Float, id: Int /*= 0*/, freeOld: Boolean /*= true*/) {
            var i: Int
            assert(self != null)
            if (id >= clipModels.Num()) {
                // FIX: C++ AssureSize copies defaultState by value into each new slot.
                // Kotlin AssureSize stores the same reference, causing aliasing corruption.
                // Manually grow and initialize each new element independently.
                val oldNum = current.Num()
                current.AssureSize(id + 1, defaultState)
                for (j in oldNum until current.Num()) {
                    current[j] = staticPState_s()
                    current[j].origin.Zero()
                    current[j].axis.Identity()
                    current[j].localOrigin.Zero()
                    current[j].localAxis.Identity()
                }
                // FIX: C++ passes NULL, not a new clip model
                clipModels.AssureSize(id + 1, null)
            }
            if (clipModels[id] !== model && freeOld) {
                idClipModel.delete(clipModels[id])
            }
            clipModels[id] = model
            clipModels[id]?.Link(Game_local.gameLocal.clip, self, id, current[id].origin, current[id].axis)
            i = clipModels.Num() - 1
            while (i >= 1) {
                if (clipModels[i] != null) {
                    break
                }
                i--
            }
            current.SetNum(i + 1, false)
            clipModels.SetNum(i + 1, false)
        }

        override fun GetClipModel(id: Int /*= 0*/): idClipModel? {
            // FIX: C++ also checks clipModels[id] != NULL before returning
            return if (id >= 0 && id < clipModels.Num() && clipModels[id] != null) {
                clipModels[id]
            } else Game_local.gameLocal.clip.DefaultClipModel()
        }

        override fun GetNumClipModels(): Int {
            return clipModels.Num()
        }

        override fun SetMass(mass: Float, id: Int /*= -1*/) {}
        override fun GetMass(id: Int /*= -1*/): Float {
            return 0.0f
        }

        override fun SetContents(contents: Int, id: Int /*= -1*/) {
            var i: Int
            if (id >= 0 && id < clipModels.Num()) {
                clipModels[id]?.SetContents(contents)
            } else if (id == -1) {
                i = 0
                while (i < clipModels.Num()) {
                    clipModels[i]?.SetContents(contents)
                    i++
                }
            }
        }

        override fun GetContents(id: Int /*= -1*/): Int {
            var i: Int
            var contents = 0
            if (id >= 0 && id < clipModels.Num()) {
                // FIX: C++ null-checks clipModels[id] before dereferencing
                if (clipModels[id] != null) {
                    contents = clipModels[id]!!.GetContents()
                }
            } else if (id == -1) {
                i = 0
                while (i < clipModels.Num()) {
                    // FIX: C++ null-checks clipModels[i] before dereferencing
                    if (clipModels[i] != null) {
                        contents = contents or clipModels[i]!!.GetContents()
                    }
                    i++
                }
            }
            return contents
        }

        override fun SetClipMask(mask: Int, id: Int /*= -1*/) {}
        override fun GetClipMask(id: Int /*= -1*/): Int {
            return 0
        }

        override fun GetBounds(id: Int /*= -1*/): idBounds {
            var i: Int
            if (id >= 0 && id < clipModels.Num()) {
                // FIX: C++ null-checks clipModels[id] before dereferencing
                if (clipModels[id] != null) {
                    return clipModels[id]!!.GetBounds()
                }
            }
            if (id == -1) {
                bounds.Clear()
                i = 0
                while (i < clipModels.Num()) {
                    // FIX: C++ null-checks clipModels[i] before dereferencing
                    if (clipModels[i] != null) {
                        bounds.AddBounds(clipModels[i]!!.GetAbsBounds())
                    }
                    i++
                }
                i = 0
                while (i < clipModels.Num()) {
                    if (clipModels[i] != null) {
                        bounds.minusAssign(0, clipModels[i]!!.GetOrigin())
                        bounds.minusAssign(1, clipModels[i]!!.GetOrigin())
                        break
                    }
                    i++
                }
                return bounds
            }
            return bounds_zero
        }

        override fun GetAbsBounds(id: Int /*= -1*/): idBounds {
            var i: Int
            if (id >= 0 && id < clipModels.Num()) {
                // FIX: C++ null-checks clipModels[id] before dereferencing
                if (clipModels[id] != null) {
                    return clipModels[id]!!.GetAbsBounds()
                }
            }
            if (id == -1) {
                absBounds.Clear()
                i = 0
                while (i < clipModels.Num()) {
                    // FIX: C++ null-checks clipModels[i] before dereferencing
                    if (clipModels[i] != null) {
                        absBounds.AddBounds(clipModels[i]!!.GetAbsBounds())
                    }
                    i++
                }
                return absBounds
            }
            return bounds_zero
        }

        override fun Evaluate(timeStepMSec: Int, endTimeMSec: Int): Boolean {
            var i: Int
            val masterOrigin = idVec3()
            val masterAxis = idMat3()
            if (hasMaster) {
                self!!.GetMasterPosition(masterOrigin, masterAxis)
                i = 0
                while (i < clipModels.Num()) {
                    current[i].origin.set(masterOrigin.plus(current[i].localOrigin.times(masterAxis)))
                    if (isOrientated) {
                        current[i].axis.set(current[i].localAxis.times(masterAxis))
                    } else {
                        current[i].axis.set(current[i].localAxis)
                    }
                    clipModels[i]?.Link(Game_local.gameLocal.clip, self, i, current[i].origin, current[i].axis)
                    i++
                }

                // FIXME: return false if master did not move
                return true
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
            if (id >= 0 && id < clipModels.Num()) {
                current[id].localOrigin.set(newOrigin)
                if (hasMaster) {
                    self!!.GetMasterPosition(masterOrigin, masterAxis)
                    current[id].origin.set(masterOrigin.plus(newOrigin.times(masterAxis)))
                } else {
                    current[id].origin.set(newOrigin)
                }
                clipModels[id]?.Link(Game_local.gameLocal.clip, self, id, current[id].origin, current[id].axis)
            } else if (id == -1) {
                if (hasMaster) {
                    self!!.GetMasterPosition(masterOrigin, masterAxis)
                    Translate(masterOrigin.plus(masterAxis.times(newOrigin).minus(current[0].origin)))
                } else {
                    Translate(newOrigin.minus(current[0].origin))
                }
            }
        }

        override fun SetAxis(newAxis: idMat3, id: Int /*= -1*/) {
            val masterOrigin = idVec3()
            val masterAxis = idMat3()
            if (id >= 0 && id < clipModels.Num()) {
                current[id].localAxis.set(newAxis)
                if (hasMaster && isOrientated) {
                    self!!.GetMasterPosition(masterOrigin, masterAxis)
                    current[id].axis.set(newAxis.times(masterAxis))
                } else {
                    current[id].axis.set(newAxis)
                }
                clipModels[id]?.Link(Game_local.gameLocal.clip, self, id, current[id].origin, current[id].axis)
            } else if (id == -1) {
                val axis: idMat3
                val rotation: idRotation
                axis = if (hasMaster) {
                    self!!.GetMasterPosition(masterOrigin, masterAxis)
                    current[0].axis.Transpose().times(newAxis.times(masterAxis))
                } else {
                    current[0].axis.Transpose().times(newAxis)
                }
                rotation = axis.ToRotation()
                rotation.SetOrigin(current[0].origin)
                Rotate(rotation)
            }
        }

        override fun Translate(translation: idVec3, id: Int /*= -1*/) {
            var i: Int
            if (id >= 0 && id < clipModels.Num()) {
                current[id].localOrigin.plusAssign(translation)
                current[id].origin.plusAssign(translation)
                clipModels[id]?.Link(Game_local.gameLocal.clip, self, id, current[id].origin, current[id].axis)
            } else if (id == -1) {
                i = 0
                while (i < clipModels.Num()) {
                    current[i].localOrigin.plusAssign(translation)
                    current[i].origin.plusAssign(translation)
                    clipModels[i]?.Link(Game_local.gameLocal.clip, self, i, current[i].origin, current[i].axis)
                    i++
                }
            }
        }

        override fun Rotate(rotation: idRotation, id: Int /*= -1*/) {
            var i: Int
            val masterOrigin = idVec3()
            val masterAxis = idMat3()
            if (id >= 0 && id < clipModels.Num()) {
                current[id].origin.timesAssign(rotation)
                current[id].axis.timesAssign(rotation.ToMat3())
                if (hasMaster) {
                    self!!.GetMasterPosition(masterOrigin, masterAxis)
                    current[id].localAxis.timesAssign(rotation.ToMat3())
                    current[id].localOrigin.set(
                        current[id].origin.minus(masterOrigin).times(masterAxis.Transpose())
                    )
                } else {
                    current[id].localAxis.set(current[id].axis)
                    current[id].localOrigin.set(current[id].origin)
                }
                clipModels[id]?.Link(Game_local.gameLocal.clip, self, id, current[id].origin, current[id].axis)
            } else if (id == -1) {
                i = 0
                while (i < clipModels.Num()) {
                    current[i].origin.timesAssign(rotation)
                    current[i].axis.timesAssign(rotation.ToMat3())
                    if (hasMaster) {
                        self!!.GetMasterPosition(masterOrigin, masterAxis)
                        current[i].localAxis.timesAssign(rotation.ToMat3())
                        current[i].localOrigin.set(
                            current[i].origin.minus(masterOrigin).times(masterAxis.Transpose())
                        )
                    } else {
                        current[i].localAxis.set(current[i].axis)
                        current[i].localOrigin.set(current[i].origin)
                    }
                    clipModels[i]?.Link(Game_local.gameLocal.clip, self, i, current[i].origin, current[i].axis)
                    i++
                }
            }
        }

        override fun GetOrigin(id: Int /*= 0*/): idVec3 {
            if (id >= 0 && id < clipModels.Num()) {
                return current[id].origin
            }
            return if (clipModels.Num() != 0) {
                current[0].origin
            } else {
                vec3_origin
            }
        }

        override fun GetAxis(id: Int /*= 0*/): idMat3 {
            if (id >= 0 && id < clipModels.Num()) {
                return current[id].axis
            }
            return if (clipModels.Num() != 0) {
                current[0].axis
            } else {
                idMat3.getMat3_identity()
            }
        }

        override fun SetLinearVelocity(newLinearVelocity: idVec3, id: Int /*= 0*/) {}
        override fun SetAngularVelocity(newAngularVelocity: idVec3, id: Int /*= 0*/) {}
        override fun GetLinearVelocity(id: Int /*= 0*/): idVec3 {
            return vec3_origin
        }

        override fun GetAngularVelocity(id: Int /*= 0*/): idVec3 {
            return vec3_origin
        }

        override fun SetGravity(newGravity: idVec3) {}
        override fun GetGravity(): idVec3 {
            return gravity
        }

        override fun GetGravityNormal(): idVec3 {
            return gravityNormal
        }

        override fun ClipTranslation(results: trace_s, translation: idVec3, model: idClipModel?) {
            results.fraction = 0.0f
            results.endAxis.set(idMat3())
            results.endpos.set(vec3_origin)
            results.c = contactInfo_t()
        }

        override fun ClipRotation(results: trace_s, rotation: idRotation, model: idClipModel?) {
            results.fraction = 0.0f
            results.endAxis.set(idMat3())
            results.endpos.set(vec3_origin)
            results.c = contactInfo_t()
        }

        override fun ClipContents(model: idClipModel?): Int {
            var i: Int
            var contents: Int
            contents = 0
            i = 0
            while (i < clipModels.Num()) {
                // FIX: C++ null-checks clipModels[i] before dereferencing
                if (clipModels[i] != null) {
                    contents = if (model != null) {
                        contents or Game_local.gameLocal.clip.ContentsModel(
                            clipModels[i]!!.GetOrigin(), clipModels[i], clipModels[i]!!.GetAxis(), -1,
                            model.Handle(), model.GetOrigin(), model.GetAxis()
                        )
                    } else {
                        contents or Game_local.gameLocal.clip.Contents(
                            clipModels[i]!!.GetOrigin(),
                            clipModels[i],
                            clipModels[i]!!.GetAxis(),
                            -1,
                            null
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
            while (i < clipModels.Num()) {
                clipModels[i]?.Disable()
                i++
            }
        }

        override fun EnableClip() {
            var i: Int
            i = 0
            while (i < clipModels.Num()) {
                clipModels[i]?.Enable()
                i++
            }
        }

        override fun UnlinkClip() {
            var i: Int
            i = 0
            while (i < clipModels.Num()) {
                clipModels[i]?.Unlink()
                i++
            }
        }

        override fun LinkClip() {
            var i: Int
            i = 0
            while (i < clipModels.Num()) {
                clipModels[i]?.Link(Game_local.gameLocal.clip, self, i, current[i].origin, current[i].axis)
                i++
            }
        }

        override fun EvaluateContacts(): Boolean {
            return false
        }

        override fun GetNumContacts(): Int {
            return 0
        }

        override fun GetContact(num: Int): contactInfo_t {
            info = contactInfo_t()
            //	memset( &info, 0, sizeof( info ) );
            return info
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
            return vec3_origin
        }

        override fun GetPushedAngularVelocity(id: Int /*= 0*/): idVec3 {
            return vec3_origin
        }

        override fun SetMaster(master: idEntity?, orientated: Boolean /*= true*/) {
            var i: Int
            val masterOrigin = idVec3()
            val masterAxis = idMat3()
            if (master != null) {
                if (!hasMaster) {
                    // transform from world space to master space
                    self!!.GetMasterPosition(masterOrigin, masterAxis)
                    i = 0
                    while (i < clipModels.Num()) {
                        current[i].localOrigin.set(
                            current[i].origin.minus(masterOrigin).times(masterAxis.Transpose())
                        )
                        if (orientated) {
                            current[i].localAxis.set(current[i].axis.times(masterAxis.Transpose()))
                        } else {
                            current[i].localAxis.set(current[i].axis)
                        }
                        i++
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
            var i: Int
            var quat: idCQuat?
            var localQuat: idCQuat?
            msg.WriteByte(current.Num())
            i = 0
            while (i < current.Num()) {
                quat = current[i].axis.ToCQuat()
                localQuat = current[i].localAxis.ToCQuat()
                msg.WriteFloat(current[i].origin[0])
                msg.WriteFloat(current[i].origin[1])
                msg.WriteFloat(current[i].origin[2])
                msg.WriteFloat(quat.x)
                msg.WriteFloat(quat.y)
                msg.WriteFloat(quat.z)
                msg.WriteDeltaFloat(current[i].origin[0], current[i].localOrigin[0])
                msg.WriteDeltaFloat(current[i].origin[1], current[i].localOrigin[1])
                msg.WriteDeltaFloat(current[i].origin[2], current[i].localOrigin[2])
                msg.WriteDeltaFloat(quat.x, localQuat.x)
                msg.WriteDeltaFloat(quat.y, localQuat.y)
                msg.WriteDeltaFloat(quat.z, localQuat.z)
                i++
            }
        }

        override fun ReadFromSnapshot(msg: idBitMsgDelta) {
            var i: Int
            val num: Int
            val quat = idCQuat()
            val localQuat = idCQuat()
            num = msg.ReadByte()
            assert(num == current.Num())
            i = 0
            while (i < current.Num()) {
                current[i].origin[0] = msg.ReadFloat()
                current[i].origin[1] = msg.ReadFloat()
                current[i].origin[2] = msg.ReadFloat()
                quat.x = msg.ReadFloat()
                quat.y = msg.ReadFloat()
                quat.z = msg.ReadFloat()
                current[i].localOrigin[0] = msg.ReadDeltaFloat(current[i].origin[0])
                current[i].localOrigin[1] = msg.ReadDeltaFloat(current[i].origin[1])
                current[i].localOrigin[2] = msg.ReadDeltaFloat(current[i].origin[2])
                localQuat.x = msg.ReadDeltaFloat(quat.x)
                localQuat.y = msg.ReadDeltaFloat(quat.y)
                localQuat.z = msg.ReadDeltaFloat(quat.z)
                current[i].axis.set(quat.ToMat3())
                current[i].localAxis.set(localQuat.ToMat3())
                i++
            }
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
            private val gravity: idVec3 = idVec3(0.0f, 0.0f, -SysCvar.g_gravity.GetFloat())
            private val gravityNormal: idVec3 = idVec3(0, 0, -1)
            private val absBounds: idBounds = idBounds()
            private val bounds: idBounds = idBounds()
            private var info: contactInfo_t = contactInfo_t()
        }

        init {
            clipModels = idList()
            current = idList()
            defaultState.origin.Zero()
            defaultState.axis.Identity()
            defaultState.localOrigin.Zero()
            defaultState.localAxis.Identity()
            current.SetNum(1)
            // FIX: C++ struct assignment does value copy; Kotlin = creates reference alias.
            // Create a new object instead of aliasing defaultState. Must match defaultState fields.
            current[0] = staticPState_s()
            current[0].origin.Zero()
            current[0].axis.Identity()
            current[0].localOrigin.Zero()
            current[0].localAxis.Identity()
            clipModels.SetNum(1)
            clipModels[0] = null
        }
    }
}
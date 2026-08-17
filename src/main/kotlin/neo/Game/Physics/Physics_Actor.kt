/*
 * Copyright (C) 1999-2011 id Software LLC, a ZeniMax Media company.
 * Translated to Kotlin by Dr. Feederino with support of Claude Code
 *
 * This file is part of the Doom 3 Kotlin project.
 * Original source: neo/Game/Physics/Physics_Actor.h, neo/Game/Physics/Physics_Actor.cpp
 */

package neo.Game.Physics

import neo.Game.GameSys.Class
import neo.Game.GameSys.Class.idTypeInfo
import neo.Game.GameSys.SaveGame.idRestoreGame
import neo.Game.GameSys.SaveGame.idSaveGame
import neo.Game.Game_local
import neo.Game.Game_local.idEntityPtr
import neo.Game.Physics.Clip.idClipModel
import neo.Game.Physics.Physics_Base.idPhysics_Base
import neo.Game.idEntity
import neo.cm.trace_s
import neo.idlib.BV.idBounds
import neo.idlib.math.Matrix.idMat3
import neo.idlib.math.idRotation
import neo.idlib.math.idVec3
import neo.idlib.math.vec3_zero

class Physics_Actor {
    /*
     ===================================================================================

     Actor physics base class

     An actor typically uses one collision model which is aligned with the gravity
     direction. The collision model is usually a simple box with the origin at the
     bottom center.

     ===================================================================================
     */
    open class idPhysics_Actor : idPhysics_Base() {
        protected var clipModel // clip model used for collision detection
                : idClipModel? = null
        protected val clipModelAxis // axis of clip model aligned with gravity direction
                : idMat3 = idMat3()

        // results of last evaluate
        protected val groundEntityPtr: idEntityPtr<idEntity?>
        protected var invMass: Float

        // derived properties
        protected var mass: Float
        protected var masterDeltaYaw: Float

        // master
        protected var masterEntity: idEntity?
        protected var masterYaw: Float

        /*
        ================
        idPhysics_Actor::~idPhysics_Actor
        ================
        */
        override fun _deconstructor() {
            if (clipModel != null) {             // null check before delete
                idClipModel.delete(clipModel!!)
                clipModel = null
            }
            super._deconstructor()
        }

        /*
        ================
        idPhysics_Actor::Save
        ================
        */
        override fun Save(savefile: idSaveGame) {
            super.Save(savefile)
            savefile.WriteClipModel(clipModel)
            savefile.WriteMat3(clipModelAxis)
            savefile.WriteFloat(mass)
            savefile.WriteFloat(invMass)
            savefile.WriteObject(masterEntity as Class.idClass?)
            savefile.WriteFloat(masterYaw)
            savefile.WriteFloat(masterDeltaYaw)
            groundEntityPtr.Save(savefile)
        }

        /*
        ================
        idPhysics_Actor::Restore
        ================
        */
        override fun Restore(savefile: idRestoreGame) {
            super.Restore(savefile)

            clipModel = savefile.ReadClipModel()
            savefile.ReadMat3(clipModelAxis)
            mass = savefile.ReadFloat()
            invMass = savefile.ReadFloat()
            masterEntity = savefile.ReadObject() as idEntity?
            masterYaw = savefile.ReadFloat()
            masterDeltaYaw = savefile.ReadFloat()
            groundEntityPtr.Restore(savefile)
        }

        /*
        ================
        idPhysics_Actor::GetMasterDeltaYaw
        ================
        */ // get delta yaw of master
        fun GetMasterDeltaYaw(): Float {
            return masterDeltaYaw
        }

        /*
        ================
        idPhysics_Actor::GetGroundEntity
        ================
        */ // returns the ground entity
        fun GetGroundEntity(): idEntity? {
            return groundEntityPtr.GetEntity()
        }

        /*
        ================
        idPhysics_Actor::SetClipModelAxis
        ================
        */ // align the clip model with the gravity direction
        fun SetClipModelAxis() { // align clip model to gravity direction
            if (gravityNormal[2] == -1.0f || gravityNormal == vec3_zero) {
                clipModelAxis.Identity()
            } else {
                clipModelAxis[2] = gravityNormal.unaryMinus()
                clipModelAxis[2].NormalVectors(clipModelAxis[0], clipModelAxis[1])
                clipModelAxis[1] = clipModelAxis[1].unaryMinus()
            }
            if (clipModel != null) {
                clipModel!!.Link(Game_local.gameLocal.clip, self, 0, clipModel!!.GetOrigin(), clipModelAxis)
            }
        }

        /*
        ================
        idPhysics_Actor::SetClipModel
        ================
        */ // common physics interface
        override fun SetClipModel(model: idClipModel?, density: Float, id: Int /*= 0*/, freeOld: Boolean /*= true*/) {
            assert(self != null)
            assert(
                model != null // a clip model is required
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
            clipModel!!.Link(Game_local.gameLocal.clip, self, 0, clipModel!!.GetOrigin(), clipModelAxis)
        }

        /*
        ================
        idPhysics_Actor::GetClipModel
        ================
        */
        override fun GetClipModel(id: Int /*= 0*/): idClipModel? {
            return clipModel
        }

        /*
        ================
        idPhysics_Actor::GetNumClipModels
        ================
        */
        override fun GetNumClipModels(): Int {
            return 1
        }

        /*
        ================
        idPhysics_Actor::SetMass
        ================
        */
        override fun SetMass(_mass: Float, id: Int /*= -1*/) {
            assert(_mass > 0.0f)
            mass = _mass
            invMass = 1.0f / _mass
        }

        /*
        ================
        idPhysics_Actor::GetMass
        ================
        */
        override fun GetMass(id: Int /*= -1*/): Float {
            return mass
        }

        /*
        ================
        idPhysics_Actor::SetClipMask
        ================
        */
        override fun SetContents(contents: Int, id: Int /*= -1*/) {
            clipModel!!.SetContents(contents)
        }

        /*
        ================
        idPhysics_Actor::SetClipMask
        ================
        */
        override fun GetContents(id: Int /*= -1*/): Int {
            return clipModel!!.GetContents()
        }

        /*
        ================
        idPhysics_Actor::GetBounds
        ================
        */
        override fun GetBounds(id: Int /*= -1*/): idBounds {
            return clipModel!!.GetBounds()
        }

        /*
        ================
        idPhysics_Actor::GetAbsBounds
        ================
        */
        override fun GetAbsBounds(id: Int /*= -1*/): idBounds {
            return clipModel!!.GetAbsBounds()
        }

        /*
        ================
        idPhysics_Actor::IsPushable
        ================
        */
        override fun IsPushable(): Boolean {
            return masterEntity == null
        }

        /*
        ================
        idPhysics_Actor::GetOrigin
        ================
        */
        override fun GetOrigin(id: Int /*= 0*/): idVec3 {
            return clipModel!!.GetOrigin()
        }

        /*
        ================
        idPhysics_Player::GetAxis
        ================
        */
        override fun GetAxis(id: Int /*= 0*/): idMat3 {
            return clipModel!!.GetAxis()
        }

        /*
        ================
        idPhysics_Actor::SetGravity
        ================
        */
        override fun SetGravity(newGravity: idVec3) {
            if (newGravity != gravityVector) {
                super.SetGravity(newGravity)
                SetClipModelAxis()
            }
        }

        /*
        ================
        idPhysics_Actor::GetGravityAxis
        ================
        */
        fun GetGravityAxis(): idMat3 {
            return clipModelAxis
        }

        /*
        ================
        idPhysics_Actor::ClipTranslation
        ================
        */
        override fun ClipTranslation(results: trace_s, translation: idVec3, model: idClipModel?) {
            if (model != null) {
                Game_local.gameLocal.clip.TranslationModel(
                    results,
                    clipModel!!.GetOrigin(),
                    clipModel!!.GetOrigin() + translation,
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
                    clipModel!!.GetOrigin() + translation,
                    clipModel,
                    clipModel!!.GetAxis(),
                    clipMask,
                    self
                )
            }
        }

        /*
        ================
        idPhysics_Actor::ClipRotation
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
        idPhysics_Actor::ClipContents
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
        idPhysics_Actor::DisableClip
        ================
        */
        override fun DisableClip() {
            clipModel!!.Disable()
        }

        /*
        ================
        idPhysics_Actor::EnableClip
        ================
        */
        override fun EnableClip() {
            clipModel!!.Enable()
        }

        /*
        ================
        idPhysics_Actor::UnlinkClip
        ================
        */
        override fun UnlinkClip() {
            clipModel!!.Unlink()
        }

        /*
        ================
        idPhysics_Actor::LinkClip
        ================
        */
        override fun LinkClip() {
            clipModel!!.Link(Game_local.gameLocal.clip, self, 0, clipModel!!.GetOrigin(), clipModel!!.GetAxis())
        }

        /*
        ================
        idPhysics_Actor::EvaluateContacts
        ================
        */
        override fun EvaluateContacts(): Boolean {

            // get all the ground contacts
            ClearContacts()
            AddGroundContacts(clipModel!!)
            AddContactEntitiesForContacts()
            return contacts.Num() != 0
        }

        //
        //
        companion object {
            val Type = idTypeInfo("idPhysics_Actor", "idPhysics_Base") { idPhysics_Actor() }
        }

        override fun GetType(): idTypeInfo = Type
        override fun CreateInstance(): Class.idClass = idPhysics_Actor()

        /*
        ================
        idPhysics_Actor::idPhysics_Actor
        ================
        */
        init {
            SetClipModelAxis()
            mass = 100.0f
            invMass = 1.0f / mass
            masterEntity = null
            masterYaw = 0.0f
            masterDeltaYaw = 0.0f
            groundEntityPtr = idEntityPtr()
        }
    }
}

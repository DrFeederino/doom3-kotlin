/*
 * Copyright (C) 1999-2011 id Software LLC, a ZeniMax Media company.
 * Translated to Kotlin by Dr. Feederino with support of Claude Code
 *
 * This file is part of the Doom 3 Kotlin project.
 * Original source: neo/Game/Physics/Physics_Base.h, neo/Game/Physics/Physics_Base.cpp
 */

package neo.Game.Physics

import neo.Game.GameSys.Class.idClass
import neo.Game.GameSys.SaveGame.idRestoreGame
import neo.Game.GameSys.SaveGame.idSaveGame
import neo.Game.Game_local
import neo.Game.Game_local.Companion.gameLocal
import neo.Game.Game_local.idEntityPtr
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
import neo.idlib.colorBlue
import neo.idlib.colorRed
import neo.idlib.containers.CInt
import neo.idlib.containers.List.idList
import neo.idlib.math.*
import neo.idlib.math.Matrix.idMat3
import kotlin.math.abs

class Physics_Base {
    /*
     ===============================================================================

     Physics base for a moving object using one or more collision models.

     ===============================================================================
     */
    class contactEntity_t : idEntityPtr<idEntity?>()
    open class idPhysics_Base : idPhysics() {
        // CLASS_PROTOTYPE( idPhysics_Base );
        protected var clipMask // contents the physics object collides with
                = 0
        protected val contactEntities // entities touching this physics object
                : idList<contactEntity_t> = idList(contactEntity_t::class.java)
        protected val contacts // contacts with other physics objects
                : idList<contactInfo_t> = idList(contactInfo_t::class.java)
        protected val gravityNormal // normalized direction of gravity
                : idVec3 = idVec3(gameLocal.GetGravity())
        protected val gravityVector // direction and magnitude of gravity
                : idVec3 = idVec3(gameLocal.GetGravity())
        protected var self // entity using this physics object
                : idEntity? = null

        // ~idPhysics_Base( void );
        override fun _deconstructor() {
            if (self != null && self!!.GetPhysics() === this) {
                self!!.SetPhysics(null)
            }
            idForce.DeletePhysics(this)
            ClearContacts()
            super._deconstructor()
        }

        override fun Save(savefile: idSaveGame) {
            var i: Int
            savefile.WriteObject(self as idClass?)
            savefile.WriteInt(clipMask)
            savefile.WriteVec3(gravityVector)
            savefile.WriteVec3(gravityNormal)
            savefile.WriteInt(contacts.Num())
            i = 0
            while (i < contacts.Num()) {
                savefile.WriteContactInfo(contacts[i])
                i++
            }
            savefile.WriteInt(contactEntities.Num())
            i = 0
            while (i < contactEntities.Num()) {
                contactEntities[i].Save(savefile)
                i++
            }
        }

        override fun GetType(): Class<out idClass> {
            return this.javaClass
        }

        override fun Restore(savefile: idRestoreGame) {
            var i: Int
            val num = CInt()
            savefile.ReadObject( /*reinterpret_cast<idClass *&>*/self)
            clipMask = savefile.ReadInt()
            savefile.ReadVec3(gravityVector)
            savefile.ReadVec3(gravityNormal)
            savefile.ReadInt(num)
            contacts.SetNum(num._val)
            i = 0
            while (i < contacts.Num()) {
                savefile.ReadContactInfo(contacts[i])
                i++
            }
            savefile.ReadInt(num)
            contactEntities.SetNum(num._val)
            i = 0
            while (i < contactEntities.Num()) {
                contactEntities[i].Restore(savefile)
                i++
            }
        }

        // common physics interface
        override fun SetSelf(e: idEntity) {
            assert(e != null)
            self = e
        }

        override fun SetClipModel(model: idClipModel?, density: Float, id: Int /*= 0*/, freeOld: Boolean /*= true*/) {}
        override fun GetClipModel(id: Int /*= 0*/): idClipModel? {
            return null
        }

        override fun GetNumClipModels(): Int {
            return 0
        }

        override fun SetMass(mass: Float, id: Int /*= -1*/) {}
        override fun GetMass(id: Int /*= -1*/): Float {
            return 0.0f
        }

        override fun SetContents(contents: Int, id: Int /*= -1*/) {}
        override fun GetContents(id: Int /*= -1*/): Int {
            return 0
        }

        override fun SetClipMask(mask: Int, id: Int /*= -1*/) {
            clipMask = mask
        }

        override fun GetClipMask(id: Int /*= -1*/): Int {
            return clipMask
        }

        override fun GetBounds(id: Int /*= -1*/): idBounds {
            return bounds_zero
        }

        override fun GetAbsBounds(id: Int /*= -1*/): idBounds {
            return bounds_zero
        }

        override fun Evaluate(timeStepMSec: Int, endTimeMSec: Int): Boolean {
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
            return true
        }

        override fun SaveState() {}
        override fun RestoreState() {}
        override fun SetOrigin(newOrigin: idVec3, id: Int /*= -1*/) {}
        override fun SetAxis(newAxis: idMat3, id: Int /*= -1*/) {}
        override fun Translate(translation: idVec3, id: Int /*= -1*/) {}
        override fun Translate(translation: idVec3) {
            Translate(translation, -1)
        }

        override fun Rotate(rotation: idRotation, id: Int /*= -1*/) {}
        override fun Rotate(rotation: idRotation) {
            Rotate(rotation, -1)
        }

        override fun GetOrigin(id: Int /*= 0*/): idVec3 {
            return getVec3Origin()
        }

        override fun GetAxis(id: Int /*= 0*/): idMat3 {
            return idMat3.getMat3_identity()
        }

        override fun SetLinearVelocity(newLinearVelocity: idVec3, id: Int /*= 0*/) {}
        override fun SetAngularVelocity(newAngularVelocity: idVec3, id: Int /*= 0*/) {}
        override fun GetLinearVelocity(id: Int /*= 0*/): idVec3 {
            return getVec3Origin()
        }

        override fun GetAngularVelocity(id: Int /*= 0*/): idVec3 {
            return getVec3Origin()
        }

        override fun SetGravity(newGravity: idVec3) {
            gravityVector.set(newGravity)
            gravityNormal.set(newGravity)
            gravityNormal.Normalize()
        }

        override fun GetGravity(): idVec3 {
            return gravityVector
        }

        override fun GetGravityNormal(): idVec3 {
            return gravityNormal
        }

        override fun ClipTranslation(results: trace_s, translation: idVec3, model: idClipModel?) {
            results.fraction = 0.0f
            results.endAxis.set(idMat3())
            results.endpos.set(getVec3Origin())
            results.c = contactInfo_t()
        }

        override fun ClipRotation(results: trace_s, rotation: idRotation, model: idClipModel?) {
            results.fraction = 0.0f
            results.endAxis.set(idMat3())
            results.endpos.set(getVec3Origin())
            results.c = contactInfo_t()
        }

        override fun ClipContents(model: idClipModel?): Int {
            return 0
        }

        override fun DisableClip() {}
        override fun EnableClip() {}
        override fun UnlinkClip() {}
        override fun LinkClip() {}
        override fun EvaluateContacts(): Boolean {
            return false
        }

        override fun GetNumContacts(): Int {
            return contacts.Num()
        }

        override fun GetContact(num: Int): contactInfo_t? {
            return contacts[num]
        }

        override fun ClearContacts() {
            var i: Int
            var ent: idEntity?
            i = 0
            while (i < contacts.Num()) {
                ent = gameLocal.entities[contacts[i].entityNum]
                ent?.RemoveContactEntity(self!!)
                i++
            }
            contacts.SetNum(0, false)
        }

        override fun AddContactEntity(e: idEntity) {
            var i: Int
            var ent: idEntity?
            var found = false
            i = 0
            while (i < contactEntities.Num()) {
                ent = contactEntities[i].GetEntity()
                if (ent == null) {
                    contactEntities.RemoveIndex(i--)
                }
                if (ent === e) {
                    found = true
                }
                i++
            }
            if (!found) {
                val contactentityT = contactEntity_t()
                contactEntities.Append(contactentityT)
                contactentityT.oSet(e)
            }
        }

        override fun RemoveContactEntity(e: idEntity) {
            var i: Int
            var ent: idEntity?
            i = 0
            while (i < contactEntities.Num()) {
                ent = contactEntities[i].GetEntity()
                if (null == ent) {
                    contactEntities.RemoveIndex(i--)
                    i++
                    continue
                }
                if (ent === e) {
                    contactEntities.RemoveIndex(i--)
                    return
                }
                i++
            }
        }

        override fun HasGroundContacts(): Boolean {
            var i: Int
            i = 0
            while (i < contacts.Num()) {
                if (contacts[i].normal.times(gravityNormal.unaryMinus()) > 0.0f) {
                    return true
                }
                i++
            }
            return false
        }

        override fun IsGroundEntity(entityNum: Int): Boolean {
            var i: Int
            i = 0
            while (i < contacts.Num()) {
                if (contacts[i].entityNum == entityNum && contacts[i].normal.times(gravityNormal.unaryMinus()) > 0.0f) {
                    return true
                }
                i++
            }
            return false
        }

        override fun IsGroundClipModel(entityNum: Int, id: Int): Boolean {
            var i: Int
            i = 0
            while (i < contacts.Num()) {
                if (contacts[i].entityNum == entityNum && contacts[i].id == id && contacts[i].normal.times(
                        gravityNormal.unaryMinus()
                    ) > 0.0f
                ) {
                    return true
                }
                i++
            }
            return false
        }

        override fun SetPushed(deltaTime: Int) {}
        override fun GetPushedLinearVelocity(id: Int /*= 0*/): idVec3 {
            return getVec3Origin()
        }

        override fun GetPushedAngularVelocity(id: Int /*= 0*/): idVec3 {
            return getVec3Origin()
        }

        override fun SetMaster(master: idEntity?, orientated: Boolean /*= true*/) {}
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

        override fun WriteToSnapshot(msg: idBitMsgDelta) {}
        override fun ReadFromSnapshot(msg: idBitMsgDelta) {}

        // add ground contacts for the clip model
        protected fun AddGroundContacts(clipModel: idClipModel) {
            val dir = idVec6()
            val index: Int
            val num: Int
            index = contacts.Num()
            contacts.SetNum(index + 10, false)
            val contactz = Array(10) { contactInfo_t() }
            dir.SubVec3_oSet(0, gravityNormal)
            dir.SubVec3_oSet(1, getVec3Origin())
            num = gameLocal.clip.Contacts(
                contactz,
                10,
                clipModel.GetOrigin(),
                dir,
                Physics.CONTACT_EPSILON,
                clipModel,
                clipModel.GetAxis(),
                clipMask,
                self
            )
            for (i in 0 until num) {
                contacts[index + i] = contactz[i]
            }
            contacts.SetNum(index + num, false)
        }

        // add contact entity links to contact entities
        protected fun AddContactEntitiesForContacts() {
            var i: Int
            var ent: idEntity?
            i = 0
            while (i < contacts.Num()) {
                ent = gameLocal.entities[contacts[i].entityNum]
                if (ent != null && ent != self) {
                    ent.AddContactEntity(self!!)
                }
                i++
            }
        }

        // active all contact entities
        protected fun ActivateContactEntities() {
            var i: Int
            var ent: idEntity?
            i = 0
            while (i < contactEntities.Num()) {
                ent = contactEntities[i].GetEntity()
                ent?.ActivatePhysics(self) ?: contactEntities.RemoveIndex(i--)
                i++
            }
        }

        // returns true if the whole physics object is outside the world bounds
        protected fun IsOutsideWorld(): Boolean {
            return !gameLocal.clip.GetWorldBounds().Expand(128.0f).IntersectsBounds(GetAbsBounds())
        }

        // draw linear and angular velocity
        protected fun DrawVelocity(id: Int, linearScale: Float, angularScale: Float) {
            val dir = idVec3()
            val org = idVec3()
            val vec = idVec3()
            val start = idVec3()
            val end = idVec3()
            val axis: idMat3
            var length: Float
            var a: Float
            dir.set(GetLinearVelocity(id))
            dir.timesAssign(linearScale)
            if (dir.LengthSqr() > Square(0.1f)) {
                dir.Truncate(10.0f)
                org.set(GetOrigin(id))
                Game_local.gameRenderWorld!!.DebugArrow(colorRed, org, org + dir, 1)
            }
            dir.set(GetAngularVelocity(id))
            length = dir.Normalize()
            length *= angularScale
            if (length > 0.1f) {
                if (length < 60.0f) {
                    length = 60.0f
                } else if (length > 360.0f) {
                    length = 360.0f
                }
                axis = GetAxis(id)
                vec.set(axis[2])
                if (abs(dir * vec) > 0.99) {
                    vec.set(axis[0])
                }
                // FIX: was vec.timesVec(dir).timesVec(vec) (element-wise), should be dot-product-then-scale
                vec.minusAssign(vec * (vec * dir))
                vec.Normalize()
                vec.timesAssign(4.0f)
                start.set(org + vec)
                a = 20.0f
                while (a < length) {
                    end.set(org + idRotation(getVec3Origin(), dir, -a).ToMat3() * vec)
                    Game_local.gameRenderWorld!!.DebugLine(colorBlue, start, end, 1)
                    start.set(end)
                    a += 20.0f
                }
                end.set(org + (idRotation(getVec3Origin(), dir, -length).ToMat3() * vec))
                Game_local.gameRenderWorld!!.DebugArrow(colorBlue, start, end, 1)
            }
        }

        override fun CreateInstance(): idClass {
            throw UnsupportedOperationException("Not supported yet.")
        }

        override fun oSet(oGet: idClass?) {
            throw UnsupportedOperationException("Not supported yet.")
        }

        init {
            //SetGravity(gameLocal.GetGravity());
            gravityNormal.Normalize()
            ClearContacts()
        }
    }
}
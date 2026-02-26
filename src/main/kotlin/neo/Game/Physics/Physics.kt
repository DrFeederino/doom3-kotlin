package neo.Game.Physics

import neo.Game.Entity.idEntity
import neo.Game.GameSys.Class.eventCallback_t
import neo.Game.GameSys.Class.idClass
import neo.Game.GameSys.Event.idEventDef
import neo.Game.GameSys.SaveGame.idRestoreGame
import neo.Game.GameSys.SaveGame.idSaveGame
import neo.Game.Physics.Clip.idClipModel
import neo.cm.contactInfo_t
import neo.cm.trace_s
import neo.framework.UsercmdGen
import neo.idlib.BV.idBounds
import neo.idlib.BitMsg.idBitMsgDelta
import neo.idlib.geometry.TraceModel.idTraceModel
import neo.idlib.math.Matrix.idMat3
import neo.idlib.math.idRotation
import neo.idlib.math.idVec3

object Physics {
    /*
     ===============================================================================

     Physics abstract class

     A physics object is a tool to manipulate the position and orientation of
     an entity. The physics object is a container for idClipModels used for
     collision detection. The physics deals with moving these collision models
     through the world according to the laws of physics or other rules.

     The mass of a clip model is the volume of the clip model times the density.
     An arbitrary mass can however be set for specific clip models or the
     whole physics object. The contents of a clip model is a set of bit flags
     that define the contents. The clip mask defines the contents a clip model
     collides with.

     The linear velocity of a physics object is a vector that defines the
     translation of the center of mass in units per second. The angular velocity
     of a physics object is a vector that passes through the center of mass. The
     direction of this vector defines the axis of rotation and the magnitude
     defines the rate of rotation about the axis in radians per second.
     The gravity is the change in velocity per second due to gravitational force.

     Entities update their visual position and orientation from the physics
     using GetOrigin() and GetAxis(). Direct origin and axis changes of
     entities should go through the physics. In other words the physics origin
     and axis are updated first and the entity updates it's visual position
     from the physics.

     ===============================================================================
     */
    const val CONTACT_EPSILON = 0.25f // maximum contact seperation distance

    class impactInfo_s {
        val invInertiaTensor // inverse inertia tensor
                : idMat3 = idMat3()
        var invMass // inverse mass
                = 0.0f
        val position // impact position relative to center of mass
                : idVec3 = idVec3()
        val velocity // velocity at the impact position
                : idVec3 = idVec3()
    }

    abstract class idPhysics : idClass() {
        protected val DBG_count = DBG_counter++


        // Must not be virtual
        override fun Save(savefile: idSaveGame) {}
        override fun Restore(savefile: idRestoreGame) {}

        // common physics interface
        // set pointer to entity using physics
        abstract fun SetSelf(e: idEntity)

        // clip models
        abstract fun SetClipModel(model: idClipModel?, density: Float, id: Int /*= 0*/, freeOld: Boolean /*= true*/)


        fun SetClipModel(model: idClipModel?, density: Float, id: Int = 0 /*= 0*/) {
            SetClipModel(model, density, id, true)
        }

        fun SetClipBox(bounds: idBounds, density: Float) {
            SetClipModel(idClipModel(idTraceModel(bounds)), density)
        }

        abstract fun GetClipModel(id: Int /*= 0*/): idClipModel?
        fun GetClipModel(): idClipModel? {
            return GetClipModel(0)
        }

        abstract fun GetNumClipModels(): Int

        // get/set the mass of a specific clip model or the whole physics object
        abstract fun SetMass(mass: Float, id: Int /*= -1*/)
        fun SetMass(mass: Float) {
            SetMass(mass, -1)
        }

        fun GetMass(): Float { //TODO:make sure this shouldn't be overrided.
            return GetMass(-1)
        }

        abstract fun GetMass(id: Int /*= -1*/): Float

        // get/set the contents of a specific clip model or the whole physics object
        abstract fun SetContents(contents: Int, id: Int /*= -1*/)
        fun SetContents(contents: Int) {
            SetContents(contents, -1)
        }

        abstract fun GetContents(id: Int /*= -1*/): Int
        fun GetContents(): Int {
            return GetContents(-1)
        }

        // get/set the contents a specific clip model or the whole physics object collides with
        abstract fun SetClipMask(mask: Int, id: Int /*= -1*/)
        fun SetClipMask(mask: Int) {
            SetClipMask(mask, -1)
        }

        abstract fun GetClipMask(id: Int /*= -1*/): Int
        fun GetClipMask(): Int {
            return GetClipMask(-1)
        }

        // get the bounds of a specific clip model or the whole physics object
        abstract fun GetBounds(id: Int /*= -1*/): idBounds
        fun GetBounds(): idBounds {
            return GetBounds(-1)
        }

        abstract fun GetAbsBounds(id: Int /*= -1*/): idBounds
        fun GetAbsBounds(): idBounds {
            return GetAbsBounds(-1)
        }

        // evaluate the physics with the given time step, returns true if the object moved
        abstract fun Evaluate(timeStepMSec: Int, endTimeMSec: Int): Boolean

        // update the time without moving
        abstract fun UpdateTime(endTimeMSec: Int)

        // get the last physics update time
        abstract fun GetTime(): Int

        // collision interaction between different physics objects
        abstract fun GetImpactInfo(id: Int, point: idVec3): impactInfo_s
        abstract fun ApplyImpulse(id: Int, point: idVec3, impulse: idVec3)
        abstract fun AddForce(id: Int, point: idVec3, force: idVec3)
        abstract fun Activate()
        abstract fun PutToRest()
        abstract fun IsAtRest(): Boolean
        abstract fun GetRestStartTime(): Int
        abstract fun IsPushable(): Boolean

        // save and restore the physics state
        abstract fun SaveState()
        abstract fun RestoreState()

        // set the position and orientation in master space or world space if no master set
        abstract fun SetOrigin(newOrigin: idVec3, id: Int /*= -1*/)
        fun SetOrigin(newOrigin: idVec3) {
            SetOrigin(newOrigin, -1)
        }

        abstract fun SetAxis(newAxis: idMat3, id: Int /*= -1*/)
        fun SetAxis(newAxis: idMat3) {
            SetAxis(newAxis, -1)
        }

        // translate or rotate the physics object in world space
        abstract fun Translate(translation: idVec3, id: Int /*= -1*/)
        open fun Translate(translation: idVec3) {
            Translate(translation, -1)
        }

        abstract fun Rotate(rotation: idRotation, id: Int /*= -1*/)
        open fun Rotate(rotation: idRotation) {
            Rotate(rotation, -1)
        }

        // get the position and orientation in world space
        abstract fun GetOrigin(id: Int /*= 0*/): idVec3
        fun GetOrigin(): idVec3 {
            return GetOrigin(0)
        }

        fun GetAxis(): idMat3 {
            return GetAxis(0)
        }

        abstract fun GetAxis(id: Int /*= 0*/): idMat3

        // set linear and angular velocity
        abstract fun SetLinearVelocity(newLinearVelocity: idVec3, id: Int /*= 0*/)
        fun SetLinearVelocity(newLinearVelocity: idVec3) {
            SetLinearVelocity(newLinearVelocity, 0)
        }

        abstract fun SetAngularVelocity(newAngularVelocity: idVec3, id: Int /*= 0*/)
        fun SetAngularVelocity(newAngularVelocity: idVec3) {
            SetAngularVelocity(newAngularVelocity, 0)
        }

        // get linear and angular velocity
        abstract fun GetLinearVelocity(id: Int /*= 0*/): idVec3
        fun GetLinearVelocity(): idVec3 {
            return GetLinearVelocity(0)
        }

        abstract fun GetAngularVelocity(id: Int /*= 0*/): idVec3
        fun GetAngularVelocity(): idVec3 {
            return GetAngularVelocity(0)
        }

        // gravity
        abstract fun SetGravity(newGravity: idVec3)
        abstract fun GetGravity(): idVec3
        abstract fun GetGravityNormal(): idVec3

        // get first collision when translating or rotating this physics object
        abstract fun ClipTranslation(results: trace_s, translation: idVec3, model: idClipModel?)
        abstract fun ClipRotation(results: trace_s, rotation: idRotation, model: idClipModel?)
        abstract fun ClipContents(model: idClipModel?): Int

        // disable/enable the clip models contained by this physics object
        abstract fun DisableClip()
        abstract fun EnableClip()

        // link/unlink the clip models contained by this physics object
        abstract fun UnlinkClip()
        abstract fun LinkClip()

        // contacts
        abstract fun EvaluateContacts(): Boolean
        abstract fun GetNumContacts(): Int
        abstract fun GetContact(num: Int): contactInfo_t?
        abstract fun ClearContacts()
        abstract fun AddContactEntity(e: idEntity)
        abstract fun RemoveContactEntity(e: idEntity)

        // ground contacts
        abstract fun HasGroundContacts(): Boolean
        abstract fun IsGroundEntity(entityNum: Int): Boolean
        abstract fun IsGroundClipModel(entityNum: Int, id: Int): Boolean

        // set the master entity for objects bound to a master
        abstract fun SetMaster(master: idEntity?, orientated: Boolean /*= true*/)

        // set pushed state
        abstract fun SetPushed(deltaTime: Int)
        abstract fun GetPushedLinearVelocity(id: Int /*= 0*/): idVec3
        abstract fun GetPushedAngularVelocity(id: Int /*= 0*/): idVec3

        // get blocking info, returns NULL if the object is not blocked
        abstract fun GetBlockingInfo(): trace_s?
        abstract fun GetBlockingEntity(): idEntity?

        // movement end times in msec for reached events at the end of predefined motion
        abstract fun GetLinearEndTime(): Int
        abstract fun GetAngularEndTime(): Int

        // networking
        abstract fun WriteToSnapshot(msg: idBitMsgDelta)
        abstract fun ReadFromSnapshot(msg: idBitMsgDelta)
        override fun getEventCallBack(event: idEventDef): eventCallback_t<*>? {
            return null
        }

        companion object {
            // ABSTRACT_PROTOTYPE( idPhysics );
            private var DBG_counter = 0
            fun SnapTimeToPhysicsFrame(t: Int): Int {
                val s: Int
                s = t + UsercmdGen.USERCMD_MSEC - 1
                return s - s % UsercmdGen.USERCMD_MSEC
            }
        }
    }
}
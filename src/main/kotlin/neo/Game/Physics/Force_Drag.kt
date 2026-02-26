package neo.Game.Physics

import neo.Game.Physics.Clip.idClipModel
import neo.Game.Physics.Force.idForce
import neo.Game.Physics.Physics.idPhysics
import neo.framework.UsercmdGen.USERCMD_MSEC
import neo.idlib.containers.CFloat
import neo.idlib.math.*
import neo.idlib.math.Matrix.idMat3

class Force_Drag {
    /*
     ===============================================================================

     Drag force

     ===============================================================================
     */
    class idForce_Drag : idForce() {
        // CLASS_PROTOTYPE( idForce_Drag );
        // properties
        private var damping = 0.5f
        private val dragPosition // drag towards this position
                : idVec3 = getVec3_zero()
        private var id // clip model id of physics object
                = 0
        private val p // position on clip model
                : idVec3 = getVec3_zero()

        //
        // positioning
        private var physics // physics object
                : idPhysics? = null

        // virtual				~idForce_Drag( void );
        // initialize the drag force
        fun Init(damping: Float) {
            if (damping >= 0.0f && damping < 1.0f) {
                this.damping = damping
            }
        }

        // set physics object being dragged
        fun SetPhysics(phys: idPhysics?, id: Int, p: idVec3) {
            physics = phys
            this.id = id
            this.p.set(p)
        }

        // set position to drag towards
        fun SetDragPosition(pos: idVec3) {
            dragPosition.set(pos)
        }

        // get the position dragged towards
        fun GetDragPosition(): idVec3 {
            return dragPosition
        }

        // get the position on the dragged physics object
        fun GetDraggedPosition(): idVec3 {
            return (physics!!.GetOrigin(id) + p * physics!!.GetAxis(id))
        }

        // common force interface
        override fun Evaluate(time: Int) {
            val l1: Float
            val l2: Float
            val mass = CFloat()
            val dragOrigin = idVec3()
            val dir1 = idVec3()
            val dir2 = idVec3()
            val velocity = idVec3()
            val centerOfMass = idVec3()
            val inertiaTensor = idMat3()
            val rotation = idRotation()
            val clipModel: idClipModel?
            if (null == physics) {
                return
            }
            clipModel = physics!!.GetClipModel(id)
            if (clipModel != null && clipModel.IsTraceModel()) {
                clipModel.GetMassProperties(1.0f, mass, centerOfMass, inertiaTensor)
            } else {
                centerOfMass.Zero()
            }

            centerOfMass.set(physics!!.GetOrigin(id) + centerOfMass * physics!!.GetAxis(id))
            dragOrigin.set(physics!!.GetOrigin(id) + p * physics!!.GetAxis(id))

            dir1.set(dragPosition - centerOfMass)
            dir2.set(dragOrigin - centerOfMass)

            l1 = dir1.Normalize()
            l2 = dir2.Normalize()

            rotation.Set(centerOfMass, dir2.Cross(dir1), RAD2DEG(idMath.ACos(dir1.times(dir2))))
            physics!!.SetAngularVelocity(rotation.ToAngularVelocity() / MS2SEC(USERCMD_MSEC.toFloat()), id)
            velocity.set(
                physics!!.GetLinearVelocity(id) * damping + dir1 * ((l1 - l2) * (1.0f - damping) / MS2SEC(USERCMD_MSEC.toFloat()))
            )
            physics!!.SetLinearVelocity(velocity, id)
        }

        override fun RemovePhysics(phys: idPhysics) {
            if (physics == phys) {
                physics = null
            }
        }

    }
}
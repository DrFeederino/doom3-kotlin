/*
 * Copyright (C) 1999-2011 id Software LLC, a ZeniMax Media company.
 * Translated to Kotlin by Dr. Feederino with support of Claude Code
 *
 * This file is part of the Doom 3 Kotlin project.
 * Original source: neo/Game/Physics/Force_Drag.h, neo/Game/Physics/Force_Drag.cpp
 */

package neo.Game.Physics

import neo.Game.GameSys.Class
import neo.Game.GameSys.Class.idTypeInfo
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
        companion object {
            val Type = idTypeInfo("idForce_Drag", "idForce") { idForce_Drag() }
        }

        override fun GetType(): idTypeInfo = Type
        override fun CreateInstance(): Class.idClass = idForce_Drag()

        // CLASS_PROTOTYPE( idForce_Drag );
        // properties
        private var damping = 0.5f
        private val dragPosition // drag towards this position
                : idVec3 = vec3_zero
        private var id // clip model id of physics object
                = 0
        private val p // position on clip model
                : idVec3 = vec3_zero

        //
        // positioning
        private var physics // physics object
                : idPhysics? = null

        // virtual				~idForce_Drag( void );

        /*
        ================
        idForce_Drag::Init
        ================
        */ // initialize the drag force
        fun Init(damping: Float) {
            if (damping >= 0.0f && damping < 1.0f) {
                this.damping = damping
            }
        }

        /*
        ================
        idForce_Drag::SetPhysics
        ================
        */ // set physics object being dragged
        fun SetPhysics(phys: idPhysics?, id: Int, p: idVec3) {
            physics = phys
            this.id = id
            this.p.set(p)
        }

        /*
        ================
        idForce_Drag::SetDragPosition
        ================
        */ // set position to drag towards
        fun SetDragPosition(pos: idVec3) {
            dragPosition.set(pos)
        }

        /*
        ================
        idForce_Drag::GetDragPosition
        ================
        */ // get the position dragged towards
        fun GetDragPosition(): idVec3 {
            return dragPosition
        }

        /*
        ================
        idForce_Drag::GetDraggedPosition
        ================
        */ // get the position on the dragged physics object
        fun GetDraggedPosition(): idVec3 {
            return (physics!!.GetOrigin(id) + p * physics!!.GetAxis(id))
        }

        /*
        ================
        idForce_Drag::Evaluate
        ================
        */ // common force interface
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

        /*
        ================
        idForce_Drag::RemovePhysics
        ================
        */
        override fun RemovePhysics(phys: idPhysics) {
            if (physics == phys) {
                physics = null
            }
        }

    }
}
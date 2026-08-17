/*
 * Copyright (C) 1999-2011 id Software LLC, a ZeniMax Media company.
 * Translated to Kotlin by Dr. Feederino with support of Claude Code
 *
 * This file is part of the Doom 3 Kotlin project.
 * Original source: neo/Game/Physics/Force_Spring.h, neo/Game/Physics/Force_Spring.cpp
 */

package neo.Game.Physics

import neo.Game.GameSys.Class.idClass
import neo.Game.GameSys.Class.idTypeInfo
import neo.Game.GameSys.SaveGame.idRestoreGame
import neo.Game.GameSys.SaveGame.idSaveGame
import neo.Game.Physics.Force.idForce
import neo.Game.Physics.Physics.idPhysics
import neo.Game.Physics.Physics.impactInfo_s
import neo.idlib.math.Matrix.idMat3
import neo.idlib.math.Square
import neo.idlib.math.idVec3
import neo.idlib.math.vec3_origin
import neo.idlib.math.vec3_zero

class Force_Spring {
    /*
     ===============================================================================

     Spring force

     ===============================================================================
     *//*
    ================
    idForce_Spring::idForce_Spring
    ================
    */
    class idForce_Spring : idForce() {
        companion object {
            val Type = idTypeInfo("idForce_Spring", "idForce") { idForce_Spring() }
        }

        override fun GetType(): idTypeInfo = Type
        override fun CreateInstance(): idClass = idForce_Spring()

        private var Kcompress = 100.0f
        private var Kstretch = 100.0f
        private var damping = 0.0f
        private var id1 // clip model id of first physics object
                = 0
        private var id2 // clip model id of second physics object
                : Int = 0
        private val p1 // position on clip model
                : idVec3 = vec3_zero
        private val p2 // position on clip model
                : idVec3 = vec3_zero

        // positioning
        private var physics1 // first physics object
                : idPhysics? = null
        private var physics2 // second physics object
                : idPhysics? = null
        private var restLength = 0.0f
        private var maxLength = 0.0f // added by ivan for FraggingFree
        private var pullEntity1 = false // added by ivan for FraggingFree

        //	virtual				~idForce_Spring( void );

        // ivan start
        override fun Save(savefile: idSaveGame) {
            super.Save(savefile)
            savefile.WriteFloat(Kstretch)
            savefile.WriteFloat(Kcompress)
            savefile.WriteFloat(damping)
            savefile.WriteFloat(restLength)
            savefile.WriteFloat(maxLength)
            savefile.WriteBool(pullEntity1)
        }

        override fun Restore(savefile: idRestoreGame) {
            Kstretch = savefile.ReadFloat()
            Kcompress = savefile.ReadFloat()
            damping = savefile.ReadFloat()
            restLength = savefile.ReadFloat()
            maxLength = savefile.ReadFloat()
            pullEntity1 = savefile.ReadBool()
        } // ivan end

        /*
        ================
        idForce_Spring::InitSpring
        ================
        */ // initialize the spring
        fun InitSpring(
            Kstretch: Float,
            Kcompress: Float,
            damping: Float,
            restLength: Float,
            maxLength: Float = 0.0f,
            pullEntity1: Boolean = false
        ) {
            this.Kstretch = Kstretch
            this.Kcompress = Kcompress
            this.damping = damping
            this.restLength = restLength
            this.maxLength = maxLength
            this.pullEntity1 = pullEntity1
        }

        /*
        ================
        idForce_Spring::SetPosition
        ================
        */ // set the entities and positions on these entities the spring is attached to
        fun SetPosition(physics1: idPhysics?, id1: Int, p1: idVec3, physics2: idPhysics?, id2: Int, p2: idVec3) {
            this.physics1 = physics1
            this.id1 = id1
            this.p1.set(p1)
            this.physics2 = physics2
            this.id2 = id2
            this.p2.set(p2)
        }

        /*
        ================
        idForce_Spring::Evaluate
        ================
        */ // common force interface
        override fun Evaluate(time: Int) {
            var length: Float
            val axis = idMat3()
            val pos1 = idVec3()
            val pos2 = idVec3()
            val velocity1 = idVec3()
            val velocity2 = idVec3()
            val force = idVec3()
            val dampingForce = idVec3()
            var info = impactInfo_s()
            pos1.set(p1)
            pos2.set(p2)
            velocity2.set(vec3_origin)
            velocity1.set(vec3_origin)
            if (physics1 != null) {
                axis.set(physics1!!.GetAxis(id1))
                pos1.set(physics1!!.GetOrigin(id1))
                pos1.plusAssign(p1.times(axis))
                if (damping > 0.0f) {
                    info = physics1!!.GetImpactInfo(id1, pos1)
                    velocity1.set(info.velocity)
                }
            }
            if (physics2 != null) {
                axis.set(physics2!!.GetAxis(id2))
                pos2.set(physics2!!.GetOrigin(id2))
                pos2.plusAssign(p2.times(axis))
                if (damping > 0.0f) {
                    info = physics2!!.GetImpactInfo(id2, pos2)
                    velocity2.set(info.velocity)
                }
            }
            force.set(pos2 - pos1)
            val relVel: Float = (velocity2 - velocity1) * force   // dot → Float
            val forceLen2: Float = force * force                   // dot → Float
            dampingForce.set(force * (damping * (relVel / forceLen2)))
            length = force.Normalize()

            if (maxLength > 0.0f && length > maxLength) { // ff1.3
                length = maxLength
            }

            // if the spring is stretched
            if (length > restLength) {
                if (Kstretch > 0.0f) {
                    force.set(force * (Square(length - restLength) * Kstretch) - dampingForce)
                    if (pullEntity1 && physics1 != null) {
                        physics1!!.AddForce(id1, pos1, force)
                    }
                    if (physics2 != null) {
                        physics2!!.AddForce(id2, pos2, force.unaryMinus())
                    }
                }
            } else {
                if (Kcompress > 0.0f) {
                    force.set(force * (Square(length - restLength) * Kcompress) - dampingForce)
                    if (pullEntity1 && physics1 != null) {
                        physics1!!.AddForce(id1, pos1, force.unaryMinus())
                    }
                    if (physics2 != null) {
                        physics2!!.AddForce(id2, pos2, force)
                    }
                }
            }
        }

        /*
        ================
        idForce_Spring::RemovePhysics
        ================
        */
        override fun RemovePhysics(phys: idPhysics) {
            if (physics1 == phys) {
                physics1 = null
            }
            if (physics2 == phys) {
                physics2 = null
            }
        }

    }
}
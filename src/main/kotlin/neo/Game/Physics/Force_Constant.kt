/*
 * Copyright (C) 1999-2011 id Software LLC, a ZeniMax Media company.
 * Translated to Kotlin by Dr. Feederino with support of Claude Code
 *
 * This file is part of the Doom 3 Kotlin project.
 * Original source: neo/Game/Physics/Force_Constant.h, neo/Game/Physics/Force_Constant.cpp
 */

package neo.Game.Physics

import neo.Game.GameSys.SaveGame.idRestoreGame
import neo.Game.GameSys.SaveGame.idSaveGame
import neo.Game.Physics.Force.idForce
import neo.Game.Physics.Physics.idPhysics
import neo.idlib.math.idVec3
import neo.idlib.math.vec3_zero

class Force_Constant {
    /*
     ===============================================================================

     Constant force

     ===============================================================================
     */
    class idForce_Constant : idForce() {
        // CLASS_PROTOTYPE( idForce_Constant );
        // force properties
        private val force: idVec3 = vec3_zero
        private var id: Int
        private var physics: idPhysics? = null
        private val point: idVec3

        // virtual				~idForce_Constant( void );
        override fun Save(savefile: idSaveGame) {
            savefile.WriteVec3(force)
            savefile.WriteInt(id)
            savefile.WriteVec3(point)
        }

        override fun Restore(savefile: idRestoreGame) {
            // Owner needs to call SetPhysics!!
            savefile.ReadVec3(force)
            id = savefile.ReadInt()
            savefile.ReadVec3(point)
        }

        // constant force
        fun SetForce(force: idVec3) {
            this.force.set(force)
        }

        // set force position
        fun SetPosition(physics: idPhysics?, id: Int, point: idVec3) {
            this.physics = physics
            this.id = id
            this.point.set(point)
        }

        fun SetPhysics(physics: idPhysics?) {
            this.physics = physics
        }

        // common force interface
        override fun Evaluate(time: Int) {
            val p = idVec3()
            if (null == physics) {
                return
            }
            p.set(physics!!.GetOrigin(id) + point * physics!!.GetAxis(id))
            physics!!.AddForce(id, p, force)
        }

        override fun RemovePhysics(phys: idPhysics) {
            if (physics == phys) {
                physics = null
            }
        }

        init {
            id = 0
            point = vec3_zero
        }
    }
}
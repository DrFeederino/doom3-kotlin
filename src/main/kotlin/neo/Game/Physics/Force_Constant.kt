/*
 * Copyright (C) 1999-2011 id Software LLC, a ZeniMax Media company.
 * Translated to Kotlin by Dr. Feederino with support of Claude Code
 *
 * This file is part of the Doom 3 Kotlin project.
 * Original source: neo/Game/Physics/Force_Constant.h, neo/Game/Physics/Force_Constant.cpp
 */

package neo.Game.Physics

import neo.Game.GameSys.Class.idClass
import neo.Game.GameSys.Class.idTypeInfo
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
        companion object {
            val Type = idTypeInfo("idForce_Constant", "idForce") { idForce_Constant() }
        }

        override fun GetType(): idTypeInfo = Type
        override fun CreateInstance(): idClass = idForce_Constant()

        // CLASS_PROTOTYPE( idForce_Constant );
        // force properties
        private val force: idVec3 = vec3_zero
        private var id: Int
        private var physics: idPhysics? = null
        private val point: idVec3

        /*
        ================
        idForce_Constant::Save
        ================
        */
        override fun Save(savefile: idSaveGame) {
            super.Save(savefile)
            savefile.WriteVec3(force)
            savefile.WriteInt(id)
            savefile.WriteVec3(point)
        }

        /*
        ================
        idForce_Constant::Restore
        ================
        */
        override fun Restore(savefile: idRestoreGame) {
            super.Restore(savefile) // Owner needs to call SetPhysics!!
            savefile.ReadVec3(force)
            id = savefile.ReadInt()
            savefile.ReadVec3(point)
        }

        /*
        ================
        idForce_Constant::SetForce
        ================
        */
        fun SetForce(force: idVec3) {
            this.force.set(force)
        }

        /*
        ================
        idForce_Constant::SetPosition
        ================
        */
        fun SetPosition(physics: idPhysics?, id: Int, point: idVec3) {
            this.physics = physics
            this.id = id
            this.point.set(point)
        }

        /*
        ================
        idForce_Constant::SetPhysics
        ================
        */
        fun SetPhysics(physics: idPhysics?) {
            this.physics = physics
        }

        /*
        ================
        idForce_Constant::Evaluate
        ================
        */
        override fun Evaluate(time: Int) {
            val p = idVec3()
            if (null == physics) {
                return
            }
            p.set(physics!!.GetOrigin(id) + point * physics!!.GetAxis(id))
            physics!!.AddForce(id, p, force)
        }

        /*
        ================
        idForce_Constant::RemovePhysics
        ================
        */
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
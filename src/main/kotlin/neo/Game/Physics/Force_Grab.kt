/*
 * Copyright (C) 1999-2011 id Software LLC, a ZeniMax Media company.
 * Translated to Kotlin by Dr. Feederino with support of Claude Code
 *
 * This file is part of the Doom 3 Kotlin project.
 * Original source: neo/d3xp/physics/Force_Grab.h, neo/d3xp/physics/Force_Grab.cpp
 *
 * Doom 3 Source Code is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Doom 3 Source Code is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 */

package neo.Game.Physics

import neo.Game.GameSys.Class
import neo.Game.GameSys.Class.idTypeInfo
import neo.Game.GameSys.SaveGame.idRestoreGame
import neo.Game.GameSys.SaveGame.idSaveGame
import neo.Game.GameSys.SysCvar.Companion.g_grabberRandomMotion
import neo.Game.Game_local.Companion.gameLocal
import neo.Game.Physics.Force.idForce
import neo.Game.Physics.Physics.idPhysics
import neo.idlib.containers.CFloat
import neo.idlib.containers.CInt
import neo.idlib.math.Square
import neo.idlib.math.idMath
import neo.idlib.math.idVec3

class Force_Grab {
    /*
    ===============================================================================

      Grab force (D3XP)

    ===============================================================================
    */
    class idForce_Grab : idForce() {
        companion object {
            val Type = idTypeInfo("idForce_Grab", "idForce") { idForce_Grab() }
        }

        override fun GetType(): idTypeInfo = Type
        override fun CreateInstance(): Class.idClass = idForce_Grab()

        // properties
        private var damping: Float = 0.5f
        private val goalPosition: idVec3 = idVec3()
        private var distanceToGoal: Float = 0f

        // positioning
        private var physics: idPhysics? = null    // physics object
        private var id: Int = 0                    // clip model id of physics object

        /*
        ================
        idForce_Grab::Save
        ================
        */
        override fun Save(savefile: idSaveGame) {
            super.Save(savefile)
            savefile.WriteFloat(damping)
            savefile.WriteVec3(goalPosition)
            savefile.WriteFloat(distanceToGoal)
            savefile.WriteInt(id)
        }

        /*
        ================
        idForce_Grab::Restore
        ================
        */
        override fun Restore(savefile: idRestoreGame) {
            super.Restore(savefile)
            // Note: Owner needs to call SetPhysics
            val _damping = CFloat()
            val _distanceToGoal = CFloat()
            val _id = CInt()
            savefile.ReadFloat(_damping)
            damping = _damping._val
            savefile.ReadVec3(goalPosition)
            savefile.ReadFloat(_distanceToGoal)
            distanceToGoal = _distanceToGoal._val
            savefile.ReadInt(_id)
            id = _id._val
        }

        /*
        ================
        idForce_Grab::Init
        ================
        */
        // initialize the grab force
        fun Init(damping: Float) {
            if (damping >= 0.0f && damping < 1.0f) {
                this.damping = damping
            }
        }

        /*
        ================
        idForce_Grab::SetPhysics
        ================
        */
        // set physics object being dragged
        fun SetPhysics(phys: idPhysics, id: Int, goal: idVec3) {
            this.physics = phys
            this.id = id
            this.goalPosition.set(goal)
        }

        /*
        ================
        idForce_Grab::SetGoalPosition
        ================
        */
        // update the goal position
        fun SetGoalPosition(goal: idVec3) {
            this.goalPosition.set(goal)
        }

        /*
        =================
        idForce_Grab::GetDistanceToGoal
        =================
        */
        fun GetDistanceToGoal(): Float {
            return distanceToGoal
        }

        /*
        ================
        idForce_Grab::Evaluate
        ================
        */
        override fun Evaluate(time: Int) {
            val phys = physics ?: return

            val forceDir: idVec3
            var v: idVec3
            var objectCenter: idVec3
            val forceAmt: Float
            val mass: Float = phys.GetMass(id)

            objectCenter = phys.GetAbsBounds(id).GetCenter()

            if (g_grabberRandomMotion.GetBool() && !gameLocal.isMultiplayer) {
                // Jitter the objectCenter around so it doesn't remain stationary
                val sinOffset = idMath.Sin(gameLocal.time.toFloat() / 66f)
                val randScale1 = gameLocal.random.RandomFloat()
                val randScale2 = gameLocal.random.CRandomFloat()
                objectCenter.x += (sinOffset * 3.5f * randScale1) + (randScale2 * 1.2f)
                objectCenter.y += (sinOffset * -3.5f * randScale1) + (randScale2 * 1.4f)
                objectCenter.z += (sinOffset * 2.4f * randScale1) + (randScale2 * 1.6f)
            }

            forceDir = goalPosition.minus(objectCenter)
            distanceToGoal = forceDir.Normalize()

            var temp = distanceToGoal
            if (temp > 12f && temp < 32f) {
                temp = 32f
            }
            forceAmt = (1000f * mass) + (500f * temp * mass)

            val clampedForceAmt = if (forceAmt / mass > 120000f) {
                120000f * mass
            } else {
                forceAmt
            }
            phys.AddForce(id, objectCenter, forceDir.times(clampedForceAmt))

            if (distanceToGoal < 196f) {
                v = phys.GetLinearVelocity(id)
                phys.SetLinearVelocity(v.times(damping), id)
            }
            if (distanceToGoal < 16f) {
                v = phys.GetAngularVelocity(id)
                if (v.LengthSqr() > Square(8f)) {
                    phys.SetAngularVelocity(v.times(0.99999f), id)
                }
            }
        }

        /*
        ================
        idForce_Grab::RemovePhysics
        ================
        */
        override fun RemovePhysics(phys: idPhysics) {
            if (physics === phys) {
                physics = null
            }
        }
    }
}

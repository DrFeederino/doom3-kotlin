/*
 * Copyright (C) 1999-2011 id Software LLC, a ZeniMax Media company.
 * Translated to Kotlin by Dr. Feederino with support of Claude Code
 *
 * This file is part of the Doom 3 Kotlin project.
 * Original source: neo/Game/ai/AI_Vagary.cpp
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

package neo.Game.AI

import neo.Game.GameSys.Class.*
import neo.Game.GameSys.Event.idEventDef
import neo.Game.GameSys.SysCvar
import neo.Game.Game_local
import neo.Game.Moveable.idMoveable
import neo.Game.Physics.Physics.idPhysics
import neo.Game.Script.Script_Thread.idThread
import neo.Game.idEntity
import neo.idlib.BV.idBounds
import neo.idlib.MAX_WORLD_SIZE
import neo.idlib.math.idVec3

class AI_Vagary {
    companion object {
        /* **********************************************************************

         game/ai/AI_Vagary.cpp

         Vagary specific AI code

         ***********************************************************************/
        private val AI_Vagary_ChooseObjectToThrow: idEventDef = idEventDef("vagary_ChooseObjectToThrow", "vvfff", 'e')
        private val AI_Vagary_ThrowObjectAtEnemy: idEventDef = idEventDef("vagary_ThrowObjectAtEnemy", "ef")
    }

    class idAI_Vagary : idAI() {
        companion object {
            val Type = idTypeInfo("idAI_Vagary", "idAI") { idAI_Vagary() }

            //CLASS_PROTOTYPE( idAI_Vagary );
            private val eventCallbacks: MutableMap<idEventDef, eventCallback_t<*>> = HashMap()
            fun getEventCallBacks(): MutableMap<idEventDef, eventCallback_t<*>> {
                return eventCallbacks
            }


            init {
                eventCallbacks.putAll(idAI.getEventCallBacks())
                eventCallbacks[AI_Vagary_ChooseObjectToThrow] = eventCallback_t5 { obj: idAI_Vagary,
                                                                                   mins: idEventArg<*>?,
                                                                                   maxs: idEventArg<*>?,
                                                                                   speed: idEventArg<*>?,
                                                                                   minDist: idEventArg<*>?,
                                                                                   offset: idEventArg<*>? ->
                    obj.Event_ChooseObjectToThrow(
                        mins as idEventArg<idVec3>, maxs as idEventArg<idVec3>, speed as idEventArg<Float>,
                        minDist as idEventArg<Float>, offset as idEventArg<Float>
                    )
                }
                eventCallbacks[AI_Vagary_ThrowObjectAtEnemy] = eventCallback_t2 { obj: idAI_Vagary,
                                                                                  _ent: idEventArg<*>?,
                                                                                  _speed: idEventArg<*>? ->
                    obj.Event_ThrowObjectAtEnemy(_ent as idEventArg<idEntity>, _speed as idEventArg<Float>)
                }
            }
        }

        /*
         ================
         idAI_Vagary::Event_ChooseObjectToThrow
         ================
         */
        private fun Event_ChooseObjectToThrow(
            mins: idEventArg<idVec3>, maxs: idEventArg<idVec3>,
            speed: idEventArg<Float>, minDist: idEventArg<Float>, offset: idEventArg<Float>
        ) {
            var ent: idEntity
            val entityList = arrayOfNulls<idEntity?>(Game_local.MAX_GENTITIES)
            val numListedEntities: Int
            var i: Int
            var index: Int
            var dist: Float
            val vel = idVec3()
            val offsetVec = idVec3(0.0f, 0.0f, offset.value)
            val enemyEnt: idEntity? = enemy.GetEntity()
            if (null == enemyEnt) {
                idThread.ReturnEntity(null)
                // FIX: C++ also lacks return here (original bug), but in Kotlin enemyEnt!! below
                // throws NPE. Adding return to avoid Kotlin-specific crash.
                return
            }
            val enemyEyePos = lastVisibleEnemyPos + lastVisibleEnemyEyeOffset
            val myBounds = physicsObj.GetAbsBounds()
            val checkBounds = idBounds(mins.value, maxs.value)
            checkBounds.TranslateSelf(physicsObj.GetOrigin())
            numListedEntities =
                Game_local.gameLocal.clip.EntitiesTouchingBounds(checkBounds, -1, entityList, Game_local.MAX_GENTITIES)
            index = Game_local.gameLocal.random.RandomInt(numListedEntities)
            i = 0
            while (i < numListedEntities) {
                if (index >= numListedEntities) {
                    index = 0
                }
                ent = entityList[index]!!
                if (ent !is idMoveable) {
                    i++
                    index++
                    continue
                }
                if (ent.fl.hidden) {
                    // don't throw hidden objects
                    i++
                    index++
                    continue
                }
                val entPhys = ent.GetPhysics()
                val entOrg = entPhys.GetOrigin()
                dist = entOrg.minus(enemyEyePos).LengthFast()
                if (dist < minDist.value) {
                    i++
                    index++
                    continue
                }
                val expandedBounds = myBounds.Expand(entPhys.GetBounds().GetRadius())
                if (expandedBounds.LineIntersection(entOrg, enemyEyePos)) {
                    // ignore objects that are behind us
                    i++
                    index++
                    continue
                }
                if (PredictTrajectory(
                        entPhys.GetOrigin() + offsetVec,
                        enemyEyePos,
                        speed.value,
                        entPhys.GetGravity(),
                        entPhys.GetClipModel()!!,
                        entPhys.GetClipMask(),
                        MAX_WORLD_SIZE.toFloat(),
                        null,
                        enemyEnt!!,
                        if (SysCvar.ai_debugTrajectory.GetBool()) 4000 else 0,
                        vel
                    )
                ) {
                    idThread.ReturnEntity(ent)
                    return
                }
                i++
                index++
            }
            idThread.ReturnEntity(null)
        }

        /*
         ================
         idAI_Vagary::Event_ThrowObjectAtEnemy
         ================
         */
        private fun Event_ThrowObjectAtEnemy(_ent: idEventArg<idEntity>, _speed: idEventArg<Float>) {
            val ent = _ent.value
            val speed: Float = _speed.value
            val vel = idVec3()
            val enemyEnt: idEntity?
            val entPhys: idPhysics?
            entPhys = ent.GetPhysics()
            enemyEnt = enemy.GetEntity()
            if (null == enemyEnt) {
                vel.set((viewAxis[0] * physicsObj.GetGravityAxis()) * speed)
            } else {
                PredictTrajectory(
                    entPhys.GetOrigin(),
                    lastVisibleEnemyPos + lastVisibleEnemyEyeOffset,
                    speed,
                    entPhys.GetGravity(),
                    entPhys.GetClipModel()!!,
                    entPhys.GetClipMask(),
                    MAX_WORLD_SIZE.toFloat(),
                    null,
                    enemyEnt,
                    if (SysCvar.ai_debugTrajectory.GetBool()) 4000 else 0,
                    vel
                )
                vel.timesAssign(speed)
            }
            entPhys.SetLinearVelocity(vel)
            if (ent is idMoveable) {
                val ment = ent
                ment.EnableDamage(true, 2.5f)
            }
        }

        override fun GetType(): idTypeInfo = Type
        override fun CreateInstance(): idClass = idAI_Vagary()

        override fun getEventCallBack(event: idEventDef): eventCallback_t<*>? {
            return eventCallbacks[event]
        }

    }
}
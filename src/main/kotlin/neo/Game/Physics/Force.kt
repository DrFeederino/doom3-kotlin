/*
 * Copyright (C) 1999-2011 id Software LLC, a ZeniMax Media company.
 * Translated to Kotlin by Dr. Feederino with support of Claude Code
 *
 * This file is part of the Doom 3 Kotlin project.
 * Original source: neo/Game/Physics/Force.h, neo/Game/Physics/Force.cpp
 *
 * Doom 3 Source Code is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package neo.Game.Physics

import neo.Game.GameSys.Class.eventCallback_t
import neo.Game.GameSys.Class.idClass
import neo.Game.GameSys.Event.idEventDef
import neo.Game.Physics.Physics.idPhysics
import neo.idlib.containers.List.idList

class Force {
    /*
     ===============================================================================

     Force base class

     A force object applies a force to a physics object.

     ===============================================================================
     */
    open class idForce : idClass() {
        // virtual				~idForce( void );
        override fun _deconstructor() {
            forceList.Remove(this)
            super._deconstructor()
        }

        // common force interface
        // evalulate the force up to the given time
        open fun Evaluate(time: Int) {}

        // removes any pointers to the physics object
        open fun RemovePhysics(phys: idPhysics) {}
        override fun CreateInstance(): idClass {
            throw UnsupportedOperationException("Not supported yet.")
        }

        override fun GetType(): Class<out idClass> {
            throw UnsupportedOperationException("Not supported yet.")
        }

        override fun getEventCallBack(event: idEventDef): eventCallback_t<*>? {
            return null
        }

        override fun oSet(oGet: idClass?) {
            throw UnsupportedOperationException("Not supported yet.")
        }

        companion object {
            // CLASS_PROTOTYPE( idForce );
            private val forceList: idList<idForce> = idList()
            fun DeletePhysics(phys: idPhysics) {
                var i: Int
                i = 0
                while (i < forceList.Num()) {
                    forceList[i].RemovePhysics(phys)
                    i++
                }
            }

            fun ClearForceList() {
                forceList.Clear()
            }
        }

        init {
            forceList.Append(this)
        }
    }
}
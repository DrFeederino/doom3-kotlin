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

import neo.Game.GameSys.Class.*
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
        /*
        ================
        idForce::~idForce
        ================
        */
        override fun _deconstructor() {
            forceList.Remove(this)
            super._deconstructor()
        }

        /*
        ================
        idForce::Evaluate
        ================
        */
        open fun Evaluate(time: Int) {}

        /*
        ================
        idForce::RemovePhysics
        ================
        */
        open fun RemovePhysics(phys: idPhysics) {}
        override fun CreateInstance(): idClass = idForce()

        override fun GetType(): idTypeInfo = Type

        override fun getEventCallBack(event: idEventDef): eventCallback_t<*>? {
            return null
        }

        override fun oSet(oGet: idClass?) {
            throw UnsupportedOperationException("Not supported yet.")
        }

        companion object {
            val Type = idTypeInfo("idForce", "idClass") { idForce() }

            // CLASS_PROTOTYPE( idForce );
            private val forceList: idList<idForce> = idList()

            /*
            ================
            idForce::DeletePhysics
            ================
            */
            fun DeletePhysics(phys: idPhysics) {
                var i: Int
                i = 0
                while (i < forceList.Num()) {
                    forceList[i].RemovePhysics(phys)
                    i++
                }
            }

            /*
            ================
            idForce::ClearForceList
            ================
            */
            fun ClearForceList() {
                forceList.Clear()
            }
        }

        init {
            forceList.Append(this)
        }
    }
}
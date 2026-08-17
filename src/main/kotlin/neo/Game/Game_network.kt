/*
 * Copyright (C) 1999-2011 id Software LLC, a ZeniMax Media company.
 * Translated to Kotlin by Dr. Feederino with support of Claude Code
 *
 * This file is part of the Doom 3 Kotlin project.
 * Original source: neo/game/Game_network.cpp
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

package neo.Game

import neo.Game.Game_local.entityNetEvent_s
import neo.framework.CVarSystem
import neo.framework.CVarSystem.idCVar
import neo.framework.CmdSystem.idCmdSystem.ArgCompletion_Integer
import neo.idlib.idLib

/*
===============================================================================

    Client running game code:
    - entity events don't work and should not be issued
    - entities should never be spawned outside idGameLocal::ClientReadSnapshot

===============================================================================
*/

// adds tags to the network protocol to detect when things go bad ( internal consistency )
// NOTE: this changes the network protocol

class Game_network {

    /*
    ===============
    idEventQueue
    ===============
    */
    class idEventQueue {
        private var start: entityNetEvent_s? = null
        private var end: entityNetEvent_s? = null

        /*
        ===============
        idEventQueue::Alloc
        ===============
        */
        fun Alloc(): entityNetEvent_s { // NOTE: Differs from C++ — C++ uses idBlockAlloc<entityNetEvent_s>(32) for pooled allocation.
            // Kotlin uses standard heap allocation since JVM handles memory management via GC.
            val event = entityNetEvent_s()
            event.prev = null
            event.next = null
            return event
        }

        /*
        ===============
        idEventQueue::Free
        ===============
        */
        fun Free(event: entityNetEvent_s) { // should only be called on an unlinked event!
            assert(null == event.next && null == event.prev) // NOTE: Differs from C++ — C++ calls eventAllocator.Free(event) to return to pool.
            // Kotlin relies on GC to reclaim the object.
        }

        /*
        ===============
        idEventQueue::Shutdown
        ===============
        */
        fun Shutdown() { // NOTE: Differs from C++ — C++ calls eventAllocator.Shutdown() to release pool memory.
            Init()
        }

        /*
        ===============
        idEventQueue::Init
        ===============
        */
        fun Init() {
            start = null
            end = null
        }

        /*
        ===============
        idEventQueue::Enqueue
        ===============
        */
        fun Enqueue(event: entityNetEvent_s, oooBehaviour: outOfOrderBehaviour_t) {
            if (oooBehaviour == outOfOrderBehaviour_t.OUTOFORDER_DROP) { // go backwards through the queue and determine if there are
                // any out-of-order events
                while (end != null && end!!.time > event.time) {
                    val outOfOrder = RemoveLast()!!
                    idLib.common.DPrintf(
                        "WARNING: new event with id %d ( time %d ) caused removal of event with id %d ( time %d ), game time = %d.\n",
                        event.event.toString(),
                        event.time.toString(),
                        outOfOrder.event.toString(),
                        outOfOrder.time.toString(),
                        Game_local.gameLocal.time.toString()
                    )
                    Free(outOfOrder)
                }
            } else if (oooBehaviour == outOfOrderBehaviour_t.OUTOFORDER_SORT && end != null) { // NOT TESTED -- sorting out of order packets hasn't been
                //               tested yet... wasn't strictly necessary for
                //               the patch fix.
                var cur = end // iterate until we find a time < the new event's
                while (cur != null && cur.time > event.time) {
                    cur = cur.prev
                }
                if (null == cur) { // add to start
                    event.next = start
                    event.prev = null
                    start = event
                } else { // insert
                    event.prev = cur
                    event.next = cur.next
                    cur.next = event
                }
                return
            }

            // add the new event
            event.next = null
            event.prev = null
            if (end != null) {
                end!!.next = event
                event.prev = end
            } else {
                start = event
            }
            end = event
        }

        /*
        ===============
        idEventQueue::Dequeue
        ===============
        */
        fun Dequeue(): entityNetEvent_s? {
            val event = start ?: return null
            start = start!!.next
            if (null == start) {
                end = null
            } else {
                start!!.prev = null
            }
            event.next = null
            event.prev = null
            return event
        }

        /*
        ===============
        idEventQueue::RemoveLast
        ===============
        */
        fun RemoveLast(): entityNetEvent_s? {
            val event = end ?: return null
            end = event.prev
            if (null == end) {
                start = null
            } else {
                end!!.next = null
            }
            event.next = null
            event.prev = null
            return event
        }

        /*
        ===============
        idEventQueue::Start
        ===============
        */
        fun Start(): entityNetEvent_s? {
            return start
        }

        enum class outOfOrderBehaviour_t {
            OUTOFORDER_IGNORE,
            OUTOFORDER_DROP,
            OUTOFORDER_SORT
        }
    }

    companion object {
        const val ASYNC_WRITE_TAGS = false

        val net_clientShowSnapshot: idCVar = idCVar(
            "net_clientShowSnapshot",
            "0",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_INTEGER,
            "",
            0.0f,
            3.0f,
            ArgCompletion_Integer(0, 3)
        )
        val net_clientShowSnapshotRadius: idCVar =
            idCVar("net_clientShowSnapshotRadius", "128", CVarSystem.CVAR_GAME or CVarSystem.CVAR_FLOAT, "")
        val net_clientSmoothing: idCVar = idCVar(
            "net_clientSmoothing",
            "0.8",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_FLOAT,
            "smooth other clients angles and position.",
            0.0f,
            0.95f
        )
        val net_clientSelfSmoothing: idCVar = idCVar(
            "net_clientSelfSmoothing",
            "0.6",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_FLOAT,
            "smooth self position if network causes prediction error.",
            0.0f,
            0.95f
        )
        val net_clientMaxPrediction: idCVar = idCVar(
            "net_clientMaxPrediction",
            "1000",
            CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_INTEGER or CVarSystem.CVAR_NOCHEAT,
            "maximum number of milliseconds a client can predict ahead of server."
        )
        val net_clientLagOMeter: idCVar = idCVar(
            "net_clientLagOMeter",
            "1",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_BOOL or CVarSystem.CVAR_NOCHEAT or CVarSystem.CVAR_ARCHIVE,
            "draw prediction graph"
        )
    }
}

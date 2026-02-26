package neo.Game

import neo.Game.Game_local.entityNetEvent_s
import neo.framework.CVarSystem
import neo.framework.CVarSystem.idCVar
import neo.framework.CmdSystem.idCmdSystem.ArgCompletion_Integer
import neo.idlib.idLib

class Game_network {
    class idEventQueue  //        private idBlockAlloc<entityNetEvent_s> eventAllocator = new idBlockAlloc<>(32);
    {
        private var end: entityNetEvent_s? = null
        private var start: entityNetEvent_s? = null
        fun Alloc(): entityNetEvent_s {
            val event = entityNetEvent_s() // eventAllocator.Alloc();
            event.prev = null
            event.next = null
            return event
        }

        fun Free(event: entityNetEvent_s) {
            // should only be called on an unlinked event!
            assert(null == event.next && null == event.prev)
            //            eventAllocator.Free(event);
        }

        fun Shutdown() {
//            eventAllocator.Shutdown();
            Init()
        }

        fun Init() {
            start = null
            end = null
        }

        fun Enqueue(event: entityNetEvent_s, oooBehaviour: outOfOrderBehaviour_t) {
            if (oooBehaviour == outOfOrderBehaviour_t.OUTOFORDER_DROP) {
                // go backwards through the queue and determine if there are
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
            } else if (oooBehaviour == outOfOrderBehaviour_t.OUTOFORDER_SORT && end != null) {
                // NOT TESTED -- sorting out of order packets hasn't been
                //				 tested yet... wasn't strictly necessary for
                //				 the patch fix.
                var cur = end
                // iterate until we find a time < the new event's
                while (cur != null && cur.time > event.time) {
                    cur = cur.prev
                }
                if (null == cur) {
                    // add to start
                    event.next = start
                    event.prev = null
                    start = event
                } else {
                    // insert
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

        fun Start(): entityNetEvent_s? {
            return start
        }

        enum class outOfOrderBehaviour_t {
            OUTOFORDER_IGNORE, OUTOFORDER_DROP, OUTOFORDER_SORT
        }
    } //============================================================================

    /*
             ===============================================================================

             Client running game code:
             - entity events don't work and should not be issued
             - entities should never be spawned outside idGameLocal::ClientReadSnapshot

             ===============================================================================
             */
    // adds tags to the network protocol to detect when things go bad ( internal consistency )
    // NOTE: this changes the network protocol
    //#ifndef ASYNC_WRITE_TAGS
    companion object {
        val net_clientLagOMeter: idCVar = idCVar(
            "net_clientLagOMeter",
            "1",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_BOOL or CVarSystem.CVAR_NOCHEAT or CVarSystem.CVAR_ARCHIVE,
            "draw prediction graph"
        )
        val net_clientMaxPrediction: idCVar = idCVar(
            "net_clientMaxPrediction",
            "1000",
            CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_INTEGER or CVarSystem.CVAR_NOCHEAT,
            "maximum number of milliseconds a client can predict ahead of server."
        )
        val net_clientSelfSmoothing: idCVar = idCVar(
            "net_clientSelfSmoothing",
            "0.6",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_FLOAT,
            "smooth self position if network causes prediction error.",
            0.0f,
            0.95f
        )

        //#endif
        //
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
        const val ASYNC_WRITE_TAGS = false
    }
}
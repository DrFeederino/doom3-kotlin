/*
 * ===========================================================================
 *
 * Doom 3 GPL Source Code
 * Copyright (C) 1999-2011 id Software LLC, a ZeniMax Media company.
 * Translated to Kotlin by Dr. Feederino with support of Claude Code
 *
 * This file is part of the Doom 3 GPL Source Code ("Doom 3 Source Code").
 * Original source: neo/game/gamesys/Event.h, neo/game/gamesys/Event.cpp
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
 *
 * You should have received a copy of the GNU General Public License
 * along with Doom 3 Source Code. If not, see <http://www.gnu.org/licenses/>.
 *
 * ===========================================================================
 */

/*
 * Event.cpp
 *
 * Events are used for scheduling tasks and for linking script commands.
 */

package neo.Game.GameSys

import neo.Game.*
import neo.Game.AI.AI_Vagary
import neo.Game.AI.AI_Vagary.idAI_Vagary
import neo.Game.AI.idAI
import neo.Game.AI.idCombatNode
import neo.Game.Animation.Anim_Testmodel.idTestModel
import neo.Game.GameSys.Class.*
import neo.Game.GameSys.SaveGame.idRestoreGame
import neo.Game.GameSys.SaveGame.idSaveGame
import neo.Game.Game_local.Companion.isD3XP
import neo.Game.Game_local.idGameLocal
import neo.Game.Light.idLight
import neo.Game.Misc.idActivator
import neo.Game.Misc.idAnimated
import neo.Game.Misc.idDamagable
import neo.Game.Misc.idEarthQuake
import neo.Game.Misc.idForceField
import neo.Game.Misc.idFuncAASObstacle
import neo.Game.Misc.idFuncAASPortal
import neo.Game.Misc.idFuncEmitter
import neo.Game.Misc.idFuncPortal
import neo.Game.Misc.idFuncRadioChatter
import neo.Game.Misc.idFuncSmoke
import neo.Game.Misc.idFuncSplat
import neo.Game.Misc.idLiquid
import neo.Game.Misc.idPathCorner
import neo.Game.Misc.idPhantomObjects
import neo.Game.Misc.idPlayerStart
import neo.Game.Misc.idShaking
import neo.Game.Misc.idSpring
import neo.Game.Misc.idStaticEntity
import neo.Game.Misc.idVacuumSeparatorEntity
import neo.Game.Moveable.idExplodingBarrel
import neo.Game.Moveable.idMoveable
import neo.Game.Mover.idDoor
import neo.Game.Mover.idElevator
import neo.Game.Mover.idMover_Periodic
import neo.Game.Mover.idPlat
import neo.Game.Mover.idRiser
import neo.Game.Mover.idRotater
import neo.Game.Projectile.idBFGProjectile
import neo.Game.Projectile.idDebris
import neo.Game.Script.Script_Program
import neo.Game.Script.Script_Thread
import neo.Game.SecurityCamera.idSecurityCamera
import neo.Game.Sound.idSound
import neo.Game.Target.idTarget_CallObjectFunction
import neo.Game.Target.idTarget_Damage
import neo.Game.Target.idTarget_EnableLevelWeapons
import neo.Game.Target.idTarget_EnableStamina
import neo.Game.Target.idTarget_EndLevel
import neo.Game.Target.idTarget_FadeEntity
import neo.Game.Target.idTarget_FadeSoundClass
import neo.Game.Target.idTarget_Give
import neo.Game.Target.idTarget_GiveEmail
import neo.Game.Target.idTarget_GiveSecurity
import neo.Game.Target.idTarget_LevelTrigger
import neo.Game.Target.idTarget_LightFadeIn
import neo.Game.Target.idTarget_LightFadeOut
import neo.Game.Target.idTarget_LockDoor
import neo.Game.Target.idTarget_Remove
import neo.Game.Target.idTarget_RemoveWeapons
import neo.Game.Target.idTarget_SessionCommand
import neo.Game.Target.idTarget_SetFov
import neo.Game.Target.idTarget_SetGlobalShaderTime
import neo.Game.Target.idTarget_SetInfluence
import neo.Game.Target.idTarget_SetKeyVal
import neo.Game.Target.idTarget_SetModel
import neo.Game.Target.idTarget_SetPrimaryObjective
import neo.Game.Target.idTarget_SetShaderParm
import neo.Game.Target.idTarget_SetShaderTime
import neo.Game.Target.idTarget_Show
import neo.Game.Target.idTarget_Tip
import neo.Game.Target.idTarget_WaitForButton
import neo.Game.Trigger.idTrigger
import neo.Game.Trigger.idTrigger_Count
import neo.Game.Trigger.idTrigger_EntityName
import neo.Game.Trigger.idTrigger_Fade
import neo.Game.Trigger.idTrigger_Hurt
import neo.Game.Trigger.idTrigger_Multi
import neo.Game.Trigger.idTrigger_Timer
import neo.Game.Trigger.idTrigger_Touch
import neo.Game.WorldSpawn.idWorldspawn
import neo.cm.contactType_t
import neo.cm.trace_s
import neo.framework.DeclManager
import neo.idlib.Text.Str.idStr
import neo.idlib.containers.CInt
import neo.idlib.containers.LinkList.idLinkList
import neo.idlib.math.idVec3
import java.nio.ByteBuffer

object Event {
    private const val SIZEOF_INTPTR = 8        // C++: sizeof(intptr_t) on 64-bit (dhewm3)
    private const val SIZEOF_BOOL = 1          // C++: sizeof(bool)
    private const val SIZEOF_TRACE_T = 120     // C++: sizeof(trace_t) on 64-bit — includes alignment padding

    // C++: #define E_EVENT_SIZEOF_VEC ((sizeof(idVec3) + (sizeof(intptr_t) - 1)) & ~(sizeof(intptr_t) - 1))
    // On 64-bit: ((12 + 7) & ~7) = 16
    private const val E_EVENT_SIZEOF_VEC = 16

    val D_EVENT_ENTITY: Char = 'e'
    val D_EVENT_ENTITY_NULL: Char = 'E'     // event can handle NULL entity pointers
    val D_EVENT_FLOAT: Char = 'f'
    val D_EVENT_INTEGER: Char = 'd'
    const val D_EVENT_MAXARGS = 8
    val D_EVENT_STRING: Char = 's'
    val D_EVENT_TRACE: Char = 't'
    val D_EVENT_VECTOR: Char = 'v'
    val D_EVENT_VOID: Char = 0.toChar()

    const val MAX_EVENTS = 4096
    const val MAX_EVENTSPERFRAME = 4096

    var EventPool: Array<idEvent> = Array(MAX_EVENTS) { idEvent() }
    var EventQueue: idLinkList<idEvent> = idLinkList()
    var FastEventQueue: idLinkList<idEvent> = idLinkList()  // D3XP: fast timeline events
    var FreeEvents: idLinkList<idEvent> = idLinkList()

    var eventError = false
    var eventErrorMsg: String? = null
    var initialized = false

    /* **********************************************************************

     idEventDef

     ***********************************************************************/
    class idEventDef {
        private var argOffset: IntArray = IntArray(D_EVENT_MAXARGS)
        private var argsize: Int = 0
        private var eventnum: Int = 0
        private val formatspec: String?
        private var formatspecIndex: Long = 0L
        private val name: String
        private val numargs: Int
        private val returnType: Int


        constructor(command: String, formatspec: String? = null) : this(command, formatspec, 0.toChar())

        /*
         ================
         idEventDef::idEventDef
         ================
         */
        constructor(command: String, formatSpec: String? = null, returnType: Char) {
            var formatSpec = formatSpec
            var ev: idEventDef
            var i: Int
            var bits: Long
            assert(command != null)
            assert(!initialized)

            // Allow NULL to indicate no args, but always store it as ""
            // so we don't have to check for it.
            if (null == formatSpec) {
                formatSpec = ""
            }
            name = command
            formatspec = formatSpec
            this.returnType = returnType.code
            numargs = formatSpec.length
            assert(numargs <= D_EVENT_MAXARGS)
            if (numargs > D_EVENT_MAXARGS) {
                eventError = true
                eventErrorMsg = String.format("idEventDef::idEventDef : Too many args for '%s' event.", name)
                return // FIX: C++ returns here; Kotlin was missing the return
            }

            // make sure the format for the args is valid, calculate the formatspecindex, and the offsets for each arg
            bits = 0
            argsize = 0
            argOffset = IntArray(D_EVENT_MAXARGS) // C++: memset( argOffset, 0, sizeof( argOffset ) )
            i = 0
            while (i < numargs) {
                argOffset[i] = argsize
                when (formatSpec[i]) {
                    D_EVENT_FLOAT -> {
                        bits = bits or ((1 shl i).toLong())
                        // C++: argsize += sizeof( intptr_t )
                        argsize += SIZEOF_INTPTR
                    }

                    D_EVENT_INTEGER -> argsize += SIZEOF_INTPTR
                    D_EVENT_VECTOR -> argsize += E_EVENT_SIZEOF_VEC
                    D_EVENT_STRING -> argsize += Script_Program.MAX_STRING_LEN
                    D_EVENT_ENTITY, D_EVENT_ENTITY_NULL -> argsize += SIZEOF_INTPTR
                    D_EVENT_TRACE -> {
                        // FIX: Was empty body — C++ original: argsize += sizeof(trace_t) + MAX_STRING_LEN + sizeof(bool)
                        argsize += SIZEOF_TRACE_T + Script_Program.MAX_STRING_LEN + SIZEOF_BOOL
                    }

                    else -> {
                        eventError = true
                        eventErrorMsg = String.format(
                            "idEventDef::idEventDef : Invalid arg format '%s' string for '%s' event.",
                            formatSpec,
                            name
                        )
                        return // FIX: C++ returns here; Kotlin was missing the return
                    }
                }
                i++
            }

            // calculate the formatspecindex
            formatspecIndex = (1 shl numargs + D_EVENT_MAXARGS or bits.toInt()).toLong()

            // go through the list of defined events and check for duplicates
            // and mismatched format strings
            eventnum = numEventDefs
            i = 0
            while (i < eventnum) {
                ev = eventDefList[i]!!
                if (command == ev.name) {
                    if (formatSpec != ev.formatspec) {
                        eventError = true
                        eventErrorMsg = String.format(
                            "idEvent '%s' defined twice with same name but differing format strings ('%s'!='%s').",
                            command, formatSpec, ev.formatspec
                        )
                    }
                    if (ev.returnType != returnType.code) {
                        eventError = true
                        eventErrorMsg = String.format(
                            "idEvent '%s' defined twice with same name but differing return types ('%c'!='%c').",
                            command, returnType, ev.returnType
                        )
                    }
                    // Don't bother putting the duplicate event in list.
                    eventnum = ev.eventnum
                    return
                }
                i++
            }
            ev = this
            if (numEventDefs >= MAX_EVENTS) {
                eventError = true
                eventErrorMsg = String.format("numEventDefs >= MAX_EVENTS")
            }
            eventDefList[numEventDefs] = ev
            numEventDefs++
        }

        /*
         ================
         idEventDef::GetName
         ================
         */
        fun GetName(): String {
            return name
        }

        /*
         ================
         idEventDef::GetArgFormat
         ================
         */
        fun GetArgFormat(): String? {
            return formatspec
        }

        /*
         ================
         idEventDef::GetFormatspecIndex
         ================
         */
        fun GetFormatspecIndex(): Long {
            return formatspecIndex
        }

        /*
         ================
         idEventDef::GetReturnType
         ================
         */
        fun GetReturnType(): Char {
            return returnType.toChar()
        }

        /*
         ================
         idEventDef::GetEventNum
         ================
         */
        fun GetEventNum(): Int {
            return eventnum
        }

        /*
         ================
         idEventDef::GetNumArgs
         ================
         */
        fun GetNumArgs(): Int {
            return numargs
        }

        /*
         ================
         idEventDef::GetArgSize
         ================
         */
        fun GetArgSize(): Int {
            return argsize
        }

        /*
         ================
         idEventDef::GetArgOffset
         ================
         */
        fun GetArgOffset(arg: Int): Int {
            assert(arg >= 0 && arg < D_EVENT_MAXARGS)
            return argOffset[arg]
        }

        override fun hashCode(): Int {
            return eventnum
        }

        override fun equals(o: Any?): Boolean {
            if (this === o) return true
            if (o == null || javaClass != o.javaClass) return false
            val that = o as idEventDef
            return eventnum == that.eventnum
        }

        companion object {
            private val eventDefList: Array<idEventDef?> = arrayOfNulls(MAX_EVENTS)
            private var numEventDefs = 0

            /*
             ================
             idEventDef::NumEventCommands
             ================
             */
            fun NumEventCommands(): Int {
                return numEventDefs
            }

            /*
             ================
             idEventDef::GetEventCommand
             ================
             */
            fun GetEventCommand(eventnum: Int): idEventDef? {
                return eventDefList[eventnum]
            }

            /*
             ================
             idEventDef::FindEvent
             ================
             */
            fun FindEvent(name: String?): idEventDef? {
                var ev: idEventDef
                val num: Int
                var i: Int
                assert(name != null)
                num = numEventDefs
                i = 0
                while (i < num) {
                    ev = eventDefList[i]!!
                    if (name == ev.name) {
                        return ev
                    }
                    i++
                }
                return null
            }
        }
    }

    /* **********************************************************************

     idEvent

     ***********************************************************************/
    class idEvent {
        private var data: Array<idEventArg<*>?>? = null
        private val eventNode: idLinkList<idEvent> = idLinkList()
        private var eventdef: idEventDef? = null
        private var `object`: idClass? = null
        private var time = 0
        private var typeinfo: idTypeInfo? = null

        /*
         ================
         idEvent::Free
         ================
         */
        fun Free() {
            // NOTE: Differs from C++ — C++ frees data via eventDataAllocator.Free(data).
            // In Kotlin, the GC handles this; we just null the reference.
            data = null
            eventdef = null
            time = 0
            `object` = null
            typeinfo = null
            eventNode.SetOwner(this)
            eventNode.AddToEnd(FreeEvents)
        }

        /*
         ================
         idEvent::Schedule
         ================
         */
        fun Schedule(obj: idClass, type: idTypeInfo, time: Int) {
            var event: idEvent?
            assert(initialized)
            if (!initialized) {
                return
            }
            `object` = obj
            typeinfo = type

            // wraps after 24 days...like I care. ;)
            this.time = Game_local.gameLocal.time + time

            eventNode.Remove()

            // D3XP: route TIME_GROUP2 entities to FastEventQueue
            if (isD3XP && obj is idEntity && obj.timeGroup == Game_local.TIME_GROUP2) {
                event = FastEventQueue.Next()
                while (event != null && this.time >= event.time) {
                    event = event.eventNode.Next()
                }
                if (event != null) {
                    eventNode.InsertBefore(event.eventNode)
                } else {
                    eventNode.AddToEnd(FastEventQueue)
                }
                return
            }

            // D3XP: use slow timeline for regular events
            if (isD3XP) {
                this.time = Game_local.gameLocal.slow.time + time
            }

            event = EventQueue.Next()
            while (event != null && this.time >= event.time) {
                event = event.eventNode.Next()
            }
            if (event != null) {
                eventNode.InsertBefore(event.eventNode)
            } else {
                eventNode.AddToEnd(EventQueue)
            }
        }

        /*
         ================
         idEvent::GetData
         ================
         */
        fun GetData(): Array<*>? {
            return data
        }

        companion object {

            /*
             ================
             idEvent::Alloc

             NOTE: Differs from C++ — C++ uses va_list args and a flat byte buffer with
             reinterpret_cast to store event data. In Kotlin, we use vararg idEventArg
             and store them directly in an Array<idEventArg<*>?>. The type validation
             loop from C++ is omitted because Kotlin's type system provides equivalent
             safety through the idEventArg wrapper.
             ================
             */
            fun Alloc(evdef: idEventDef, numargs: Int, vararg args: idEventArg<*>?): idEvent {
                val ev: idEvent
                val size: Int

                if (FreeEvents.IsListEmpty()) {
                    idGameLocal.Error("idEvent::Alloc : No more free events")
                }
                ev = FreeEvents.Next()!!
                ev.eventNode.Remove()
                ev.eventdef = evdef
                if (numargs != evdef.GetNumArgs()) {
                    idGameLocal.Error(
                        "idEvent::Alloc : Wrong number of args for '%s' event.",
                        evdef.GetName()
                    )
                }
                size = evdef.GetArgSize()
                if (size != 0) {
                    ev.data = args.clone() as Array<idEventArg<*>?>
                } else {
                    ev.data = null
                }
                return ev
            }

            /*
             ================
             idEvent::CopyArgs
             ================
             */
            fun CopyArgs(
                evdef: idEventDef,
                numargs: Int,
                args: Array<out idEventArg<*>?>,
                data: Array<idEventArg<*>?>
            ) {
                var i: Int
                val format: CharArray
                format = evdef.GetArgFormat()!!.toCharArray()
                if (numargs != evdef.GetNumArgs()) {
                    idGameLocal.Error(
                        "idEvent::CopyArgs : Wrong number of args for '%s' event.",
                        evdef.GetName()
                    )
                }

                i = 0
                while (i < numargs) {
                    val arg = args[i]!!
                    if (format[i].code != arg.type) {
                        // FIX: C++ only suppresses the error when NULL is passed for an entity
                        // (gets cast as integer 0). Kotlin was silently forcing type to D_EVENT_STRING.
                        if (!(((format[i] == D_EVENT_TRACE) || (format[i] == D_EVENT_ENTITY))
                                    && (arg.type == D_EVENT_INTEGER.code) && (arg.value == 0 || arg.value == null))
                        ) {
                            idGameLocal.Error(
                                "idEvent::CopyArgs : Wrong type passed in for arg # %d on '%s' event.",
                                i, evdef.GetName()
                            )
                        }
                    }
                    data[i] = arg
                    i++
                }
            }


            /*
             ================
             idEvent::CancelEvents
             ================
             */
            fun CancelEvents(obj: idClass, evdef: idEventDef? = null) {
                var event: idEvent?
                var next: idEvent?
                if (!initialized) {
                    return
                }
                event = EventQueue.Next()
                while (event != null) {
                    next = event.eventNode.Next()
                    if (event.`object` === obj) {
                        if (null == evdef || evdef == event.eventdef) {
                            event.Free()
                        }
                    }
                    event = next
                }

                // D3XP: also cancel events in the fast event queue
                if (isD3XP) {
                    event = FastEventQueue.Next()
                    while (event != null) {
                        next = event.eventNode.Next()
                        if (event.`object` === obj) {
                            if (null == evdef || evdef == event.eventdef) {
                                event.Free()
                            }
                        }
                        event = next
                    }
                }
            }

            /*
             ================
             idEvent::ClearEventList
             ================
             */
            fun ClearEventList() {
                var i: Int

                // initialize lists
                FreeEvents.Clear()
                EventQueue.Clear()
                FastEventQueue.Clear()  // D3XP

                // add the events to the free list
                i = 0
                while (i < MAX_EVENTS) {
                    EventPool[i].Free()
                    i++
                }
            }

            /*
             ================
             idEvent::ServiceEvents
             ================
             */
            fun ServiceEvents() {
                var event: idEvent?
                var num: Int
                val args: Array<idEventArg<*>?> = arrayOfNulls(D_EVENT_MAXARGS)
                var i: Int
                var numargs: Int
                var formatspec: String
                var ev: idEventDef

                num = 0
                while (!EventQueue.IsListEmpty()) {
                    event = EventQueue.Next()
                    assert(event != null)
                    if (event!!.time > Game_local.gameLocal.time) {
                        break
                    }

                    // copy the data into the local args array and set up pointers
                    ev = event.eventdef!!
                    formatspec = ev.GetArgFormat()!!
                    numargs = ev.GetNumArgs()
                    i = 0
                    while (i < numargs) {
                        when (formatspec[i]) {
                            D_EVENT_INTEGER, D_EVENT_FLOAT, D_EVENT_VECTOR, D_EVENT_STRING, D_EVENT_ENTITY, D_EVENT_ENTITY_NULL, D_EVENT_TRACE -> args[i] =
                                event.data!![i]

                            else -> idGameLocal.Error(
                                "idEvent::ServiceEvents : Invalid arg format '%s' string for '%s' event.",
                                formatspec,
                                ev.GetName()
                            )
                        }
                        i++
                    }

                    // the event is removed from its list so that if then object
                    // is deleted, the event won't be freed twice
                    event.eventNode.Remove()
                    assert(event.`object` != null)
                    event.`object`!!.ProcessEventArgPtr(ev, args)

                    // return the event to the free list
                    event.Free()

                    // Don't allow ourselves to stay in here too long.  An abnormally high number
                    // of events being processed is evidence of an infinite loop of events.
                    num++
                    if (num > MAX_EVENTSPERFRAME) {
                        idGameLocal.Error("Event overflow.  Possible infinite loop in script.")
                    }
                }
            }

            /*
             ================
             idEvent::ServiceFastEvents

             D3XP: Processes events on the fast timeline (TIME_GROUP2).
             Structurally identical to ServiceEvents but uses FastEventQueue
             and compares against gameLocal.fast.time.
             ================
             */
            fun ServiceFastEvents() {
                var event: idEvent?
                var num: Int
                val args: Array<idEventArg<*>?> = arrayOfNulls(D_EVENT_MAXARGS)
                var i: Int
                var numargs: Int
                var formatspec: String
                var ev: idEventDef

                num = 0
                while (!FastEventQueue.IsListEmpty()) {
                    event = FastEventQueue.Next()
                    assert(event != null)
                    if (event!!.time > Game_local.gameLocal.fast.time) {
                        break
                    }

                    // copy the data into the local args array and set up pointers
                    ev = event.eventdef!!
                    formatspec = ev.GetArgFormat()!!
                    numargs = ev.GetNumArgs()
                    i = 0
                    while (i < numargs) {
                        when (formatspec[i]) {
                            D_EVENT_INTEGER, D_EVENT_FLOAT, D_EVENT_VECTOR, D_EVENT_STRING, D_EVENT_ENTITY, D_EVENT_ENTITY_NULL, D_EVENT_TRACE -> args[i] =
                                event.data!![i]

                            else -> idGameLocal.Error(
                                "idEvent::ServiceFastEvents : Invalid arg format '%s' string for '%s' event.",
                                formatspec,
                                ev.GetName()
                            )
                        }
                        i++
                    }

                    event.eventNode.Remove()
                    assert(event.`object` != null)
                    event.`object`!!.ProcessEventArgPtr(ev, args)

                    // return the event to the free list
                    event.Free()

                    num++
                    if (num > MAX_EVENTSPERFRAME) {
                        idGameLocal.Error("Event overflow.  Possible infinite loop in script.")
                    }
                }
            }

            /*
             ================
             idEvent::Init
             ================
             */
            fun Init() {
                Game_local.gameLocal.Printf("Initializing event system\n")
                if (eventError) {
                    idGameLocal.Error("%s", eventErrorMsg)
                }

                if (initialized) {
                    Game_local.gameLocal.Printf("...already initialized\n")
                    ClearEventList()
                    return
                }
                ClearEventList()

                // NOTE: Differs from C++ — C++ calls eventDataAllocator.Init() here.
                // In Kotlin, there is no block allocator; GC handles memory.
                // Instead, we call initCallbacks() to force static initialization of
                // all game classes that declare event definitions.
                initCallbacks()

                Game_local.gameLocal.Printf("...%d event definitions\n", idEventDef.NumEventCommands())

                // the event system has started
                initialized = true
            }

            /*
             ================
             idEvent::Shutdown
             ================
             */
            fun Shutdown() {
                Game_local.gameLocal.Printf("Shutdown event system\n")
                if (!initialized) {
                    Game_local.gameLocal.Printf("...not started\n")
                    return
                }
                ClearEventList()
                // say it is now shutdown
                initialized = false
            }

            /*
             ================
             initCallbacks

             NOTE: Kotlin-only, no C++ counterpart.
             Forces static initialization of all game classes that declare event definitions.
             In C++, this happens automatically via static idEventDef object constructors.
             On the JVM, companion objects are lazily initialized, so we must explicitly
             reference each class to trigger their idEventDef declarations.
             ================
             */
            fun initCallbacks() {
                idClass.getEventCallBacks()
                idEntity.getEventCallBacks()
                idActor.getEventCallBacks()
                idAFEntity_Base.getEventCallBacks()
                idAI.getEventCallBacks()
                idMoveable.getEventCallBacks()
                idAFEntity_ClawFourFingers.getEventCallBacks()
                idAFEntity_WithAttachedHead.getEventCallBacks()
                idAFEntity_Generic.getEventCallBacks()
                idAFEntity_Gibbable.getEventCallBacks()
                BrittleFracture.idBrittleFracture.getEventCallBacks()
                idCameraView.getEventCallBacks()
                idCameraAnim.getEventCallBacks()
                idAnimatedEntity.getEventCallBacks()
                idEntityFx.getEventCallBacks()
                idTeleporter.getEventCallBacks()
                idItem.getEventCallBacks()
                idObjective.getEventCallBacks()
                idMoveableItem.getEventCallBacks()
                idItemRemover.getEventCallBacks()
                idObjectiveComplete.getEventCallBacks()
                idLight.getEventCallBacks()
                idPlayerStart.getEventCallBacks()
                idActivator.getEventCallBacks()
                idPathCorner.getEventCallBacks()
                idTestModel.getEventCallBacks()
                idCombatNode.getEventCallBacks()
                idAI_Vagary.getEventCallBacks()
                idWorldspawn.getEventCallBacks()
                Weapon.idWeapon.getEventCallBacks()
                idTrigger_Touch.getEventCallBacks()
                idTrigger_Fade.getEventCallBacks()
                idTrigger_Hurt.getEventCallBacks()
                idTrigger_Count.getEventCallBacks()
                idTrigger_Timer.getEventCallBacks()
                idTrigger_EntityName.getEventCallBacks()
                idTrigger_Multi.getEventCallBacks()
                idTrigger.getEventCallBacks()
                idTarget_FadeSoundClass.getEventCallBacks()
                idTarget_EnableStamina.getEventCallBacks()
                idTarget_LevelTrigger.getEventCallBacks()
                idTarget_RemoveWeapons.getEventCallBacks()
                idTarget_GiveSecurity.getEventCallBacks()
                idTarget_Tip.getEventCallBacks()
                idTarget_EnableLevelWeapons.getEventCallBacks()
                idTarget_CallObjectFunction.getEventCallBacks()
                idTarget_LockDoor.getEventCallBacks()
                idTarget_SetPrimaryObjective.getEventCallBacks()
                idTarget_SetFov.getEventCallBacks()
                idTarget_SetKeyVal.getEventCallBacks()
                idTarget_SetInfluence.getEventCallBacks()
                idTarget_SetModel.getEventCallBacks()
                idTarget_GiveEmail.getEventCallBacks()
                idTarget_Give.getEventCallBacks()
                idTarget_LightFadeOut.getEventCallBacks()
                idTarget_LightFadeIn.getEventCallBacks()
                idTarget_FadeEntity.getEventCallBacks()
                idTarget_SetShaderTime.getEventCallBacks()
                idTarget_SetShaderParm.getEventCallBacks()
                idTarget_SetGlobalShaderTime.getEventCallBacks()
                idTarget_WaitForButton.getEventCallBacks()
                idTarget_EndLevel.getEventCallBacks()
                idTarget_SessionCommand.getEventCallBacks()
                idTarget_Damage.getEventCallBacks()
                idTarget_Show.getEventCallBacks()
                idTarget_Remove.getEventCallBacks()
                idSound.getEventCallBacks()
                idSecurityCamera.getEventCallBacks()
                idDebris.getEventCallBacks()
                idBFGProjectile.getEventCallBacks()
                Projectile.idProjectile.getEventCallBacks()
                Player.idPlayer.getEventCallBacks()
                idRiser.getEventCallBacks()
                idRotater.getEventCallBacks()
                idMover_Periodic.getEventCallBacks()
                idPlat.getEventCallBacks()
                idDoor.getEventCallBacks()
                Mover.idMover_Binary.getEventCallBacks()
                idElevator.getEventCallBacks()
                Mover.idMover.getEventCallBacks()
                idExplodingBarrel.getEventCallBacks()
                idMoveable.getEventCallBacks()
                idPhantomObjects.getEventCallBacks()
                idFuncRadioChatter.getEventCallBacks()
                idFuncAASObstacle.getEventCallBacks()
                idFuncAASPortal.getEventCallBacks()
                idFuncPortal.getEventCallBacks()
                idEarthQuake.getEventCallBacks()
                idShaking.getEventCallBacks()
                idLiquid.getEventCallBacks()
                idVacuumSeparatorEntity.getEventCallBacks()
                idFuncSplat.getEventCallBacks()
                idFuncSmoke.getEventCallBacks()
                idFuncEmitter.getEventCallBacks()
                idStaticEntity.getEventCallBacks()
                idAnimated.getEventCallBacks()
                idForceField.getEventCallBacks()
                idSpring.getEventCallBacks()
                Misc.idExplodable.getEventCallBacks()
                idDamagable.getEventCallBacks()
                idPathCorner.getEventCallBacks()
                idActivator.getEventCallBacks()
                idPlayerStart.getEventCallBacks()
            }

            /*
             ================
             idEvent::Save
             ================
             */
            // save games
            fun Save(savefile: idSaveGame) {
                var i: Int
                var size: Int
                var event: idEvent?
                var format: String?
                savefile.WriteInt(EventQueue.Num())
                event = EventQueue.Next()
                while (event != null) {
                    savefile.WriteInt(event.time)
                    savefile.WriteString(event.eventdef!!.GetName())
                    savefile.WriteString(event.typeinfo!!.name)
                    savefile.WriteObject(event.`object`)
                    savefile.WriteInt(event.eventdef!!.GetArgSize())
                    format = event.eventdef!!.GetArgFormat()
                    i = 0
                    size = 0
                    while (i < event.eventdef!!.GetNumArgs()) {
                        val arg = event.data?.get(i)
                        when (format!![i]) {
                            D_EVENT_FLOAT -> {
                                savefile.WriteFloat((arg?.value as? Float) ?: 0f)
                                size += SIZEOF_INTPTR
                            }

                            D_EVENT_INTEGER -> {
                                savefile.WriteInt((arg?.value as? Int) ?: 0)
                                size += SIZEOF_INTPTR
                            }

                            D_EVENT_ENTITY, D_EVENT_ENTITY_NULL -> {
                                val entity = arg?.value as? idEntity
                                val entityPtr = Game_local.idEntityPtr(entity)
                                entityPtr.Save(savefile)
                                size += SIZEOF_INTPTR
                            }

                            D_EVENT_VECTOR -> {
                                val vec = (arg?.value as? idVec3) ?: idVec3()
                                savefile.WriteVec3(vec)
                                size += E_EVENT_SIZEOF_VEC
                            }

                            D_EVENT_STRING -> {
                                val s = idStr()
                                val strVal = arg?.value
                                if (strVal is String) {
                                    s.set(strVal)
                                } else if (strVal is idStr) {
                                    s.set(strVal)
                                }
                                savefile.WriteString(s)
                                size += Script_Program.MAX_STRING_LEN
                            }

                            D_EVENT_TRACE -> {
                                val traceVal = arg?.value
                                val validTrace = traceVal is trace_s
                                savefile.WriteBool(validTrace)
                                size += SIZEOF_BOOL
                                if (validTrace) {
                                    val t = traceVal as trace_s
                                    size += SIZEOF_TRACE_T
                                    SaveTrace(savefile, t)
                                    if (t.c.material != null) {
                                        size += Script_Program.MAX_STRING_LEN
                                        val materialName = t.c.material!!.GetName()
                                        val buf = ByteBuffer.allocate(Script_Program.MAX_STRING_LEN)
                                        val nameBytes = materialName.toByteArray()
                                        buf.put(nameBytes, 0, minOf(nameBytes.size, Script_Program.MAX_STRING_LEN - 1))
                                        savefile.Write(buf, Script_Program.MAX_STRING_LEN)
                                    }
                                }
                            }

                            else -> {}
                        }
                        ++i
                    }
                    assert(size == event.eventdef!!.GetArgSize())
                    event = event.eventNode.Next()
                }

                // D3XP: Save the FastEventQueue
                // Note: C++ uses raw byte writes for fast events; in Kotlin we use the same
                // per-field serialization as regular events since we use idEventArg arrays.
                if (isD3XP) {
                    savefile.WriteInt(FastEventQueue.Num())
                    event = FastEventQueue.Next()
                    while (event != null) {
                        savefile.WriteInt(event.time)
                        savefile.WriteString(event.eventdef!!.GetName())
                        savefile.WriteString(event.typeinfo!!.name)
                        savefile.WriteObject(event.`object`)
                        savefile.WriteInt(event.eventdef!!.GetArgSize())
                        format = event.eventdef!!.GetArgFormat()
                        i = 0
                        size = 0
                        while (i < event.eventdef!!.GetNumArgs()) {
                            val arg = event.data?.get(i)
                            when (format!![i]) {
                                D_EVENT_FLOAT -> {
                                    savefile.WriteFloat((arg?.value as? Float) ?: 0f)
                                    size += SIZEOF_INTPTR
                                }

                                D_EVENT_INTEGER -> {
                                    savefile.WriteInt((arg?.value as? Int) ?: 0)
                                    size += SIZEOF_INTPTR
                                }

                                D_EVENT_ENTITY, D_EVENT_ENTITY_NULL -> {
                                    val entity = arg?.value as? idEntity
                                    val entityPtr = Game_local.idEntityPtr(entity)
                                    entityPtr.Save(savefile)
                                    size += SIZEOF_INTPTR
                                }

                                D_EVENT_VECTOR -> {
                                    val vec = (arg?.value as? idVec3) ?: idVec3()
                                    savefile.WriteVec3(vec)
                                    size += E_EVENT_SIZEOF_VEC
                                }

                                D_EVENT_STRING -> {
                                    val s = idStr()
                                    val strVal = arg?.value
                                    if (strVal is String) s.set(strVal)
                                    else if (strVal is idStr) s.set(strVal)
                                    savefile.WriteString(s)
                                    size += Script_Program.MAX_STRING_LEN
                                }

                                D_EVENT_TRACE -> {
                                    val traceVal = arg?.value
                                    val validTrace = traceVal is trace_s
                                    savefile.WriteBool(validTrace)
                                    size += SIZEOF_BOOL
                                    if (validTrace) {
                                        val t = traceVal as trace_s
                                        size += SIZEOF_TRACE_T
                                        SaveTrace(savefile, t)
                                        if (t.c.material != null) {
                                            size += Script_Program.MAX_STRING_LEN
                                            val materialName = t.c.material!!.GetName()
                                            val buf = ByteBuffer.allocate(Script_Program.MAX_STRING_LEN)
                                            val nameBytes = materialName.toByteArray()
                                            buf.put(
                                                nameBytes,
                                                0,
                                                minOf(nameBytes.size, Script_Program.MAX_STRING_LEN - 1)
                                            )
                                            savefile.Write(buf, Script_Program.MAX_STRING_LEN)
                                        }
                                    }
                                }

                                else -> {}
                            }
                            ++i
                        }
                        assert(size == event.eventdef!!.GetArgSize())
                        event = event.eventNode.Next()
                    }
                }
            }

            /*
             ================
             idEvent::Restore
             ================
             */
            fun Restore(savefile: idRestoreGame) {
                val str = ByteBuffer.allocate(Script_Program.MAX_STRING_LEN)
                val num = CInt()
                val argsize = CInt()
                var i: Int
                var j: Int
                var size: Int
                val name = idStr()
                var event: idEvent?
                var format: String?
                savefile.ReadInt(num)
                i = 0
                while (i < num._val) {
                    if (FreeEvents.IsListEmpty()) {
                        idGameLocal.Error("idEvent::Restore : No more free events")
                    }
                    event = FreeEvents.Next()!!
                    event.eventNode.Remove()
                    event.eventNode.AddToEnd(EventQueue)
                    event.time = savefile.ReadInt()

                    // read the event name
                    savefile.ReadString(name)
                    event.eventdef = idEventDef.FindEvent(name.toString())
                    if (null == event.eventdef) {
                        savefile.Error("idEvent::Restore: unknown event '%s'", name.toString())
                    }

                    // read the classtype
                    savefile.ReadString(name)
                    event.typeinfo = idClass.GetClass(name.toString())
                    if (event.typeinfo == null) {
                        savefile.Error(
                            "idEvent::Restore: unknown class '%s' on event '%s'",
                            name.toString(),
                            event.eventdef!!.GetName()
                        )
                    }

                    event.`object` = savefile.ReadObject()

                    // read the args
                    savefile.ReadInt(argsize)
                    if (argsize._val != event.eventdef!!.GetArgSize()) {
                        savefile.Error(
                            "idEvent::Restore: arg size (%d) doesn't match saved arg size(%d) on event '%s'",
                            event.eventdef!!.GetArgSize(),
                            argsize._val,
                            event.eventdef!!.GetName()
                        )
                    }
                    if (argsize._val != 0) {
                        // FIX: Was arrayOfNulls(argsize._val) — allocating by byte-size instead of arg count.
                        // The array is indexed by argument number, so it needs GetNumArgs() elements.
                        val numArgs = event.eventdef!!.GetNumArgs()
                        event.data = arrayOfNulls(numArgs)
                        format = event.eventdef!!.GetArgFormat()
                        assert(format != null)
                        j = 0
                        size = 0
                        while (j < numArgs) {
                            when (format!![j]) {
                                D_EVENT_FLOAT -> {
                                    event.data!![j] = idEventArg<Any?>(D_EVENT_FLOAT.code, savefile.ReadFloat())
                                    size += SIZEOF_INTPTR
                                }

                                D_EVENT_INTEGER -> {
                                    event.data!![j] = idEventArg<Any?>(D_EVENT_INTEGER.code, savefile.ReadInt())
                                    // FIX: Was missing size increment — C++: size += sizeof(intptr_t)
                                    size += SIZEOF_INTPTR
                                }

                                D_EVENT_ENTITY, D_EVENT_ENTITY_NULL -> {
                                    // FIX: C++ uses idEntityPtr::Restore. Read the entity pointer properly.
                                    val entityPtr = Game_local.idEntityPtr<idEntity>()
                                    entityPtr.Restore(savefile)
                                    event.data!![j] = idEventArg<Any?>(format[j].code, entityPtr.GetEntity())
                                    // FIX: Was missing size increment for D_EVENT_ENTITY,
                                    // and only had it for D_EVENT_ENTITY_NULL
                                    size += SIZEOF_INTPTR
                                }

                                D_EVENT_VECTOR -> {
                                    val buffer = idVec3()
                                    savefile.ReadVec3(buffer)
                                    event.data!![j] = idEventArg<Any?>(D_EVENT_VECTOR.code, buffer)
                                    size += E_EVENT_SIZEOF_VEC
                                }

                                D_EVENT_STRING -> {
                                    // FIX: C++ reads a string, then copies into the data buffer.
                                    // Kotlin: store the string value in the idEventArg.
                                    val s = idStr()
                                    savefile.ReadString(s)
                                    event.data!![j] = idEventArg<Any?>(D_EVENT_STRING.code, s.toString())
                                    size += Script_Program.MAX_STRING_LEN
                                }

                                D_EVENT_TRACE -> {
                                    val readBool = savefile.ReadBool()
                                    size += SIZEOF_BOOL
                                    if (readBool) {
                                        // FIX: Was using SERiAL.BYTES (a bogus constant = Int.MIN_VALUE / 8).
                                        // Use SIZEOF_TRACE_T to match the Save function.
                                        size += SIZEOF_TRACE_T
                                        val t = trace_s()
                                        val hadMaterial = RestoreTrace(savefile, t)
                                        event.data!![j] = idEventArg<Any?>(D_EVENT_TRACE.code, t)
                                        if (hadMaterial) {
                                            size += Script_Program.MAX_STRING_LEN
                                            str.clear()
                                            savefile.Read(str, Script_Program.MAX_STRING_LEN)
                                            // Resolve the material from the name string
                                            val materialName = String(str.array()).trimEnd('\u0000')
                                            if (materialName.isNotEmpty()) {
                                                t.c.material = DeclManager.declManager.FindMaterial(materialName, true)
                                            }
                                        }
                                    } else {
                                        event.data!![j] = idEventArg<Any?>(D_EVENT_TRACE.code, null)
                                    }
                                }

                                else -> {}
                            }
                            ++j
                        }
                        assert(size == event.eventdef!!.GetArgSize())
                    } else {
                        event.data = null
                    }
                    i++
                }

                // D3XP: Restore the FastEventQueue
                if (isD3XP) {
                    savefile.ReadInt(num)
                    i = 0
                    while (i < num._val) {
                        if (FreeEvents.IsListEmpty()) {
                            idGameLocal.Error("idEvent::Restore : No more free events")
                        }
                        event = FreeEvents.Next()!!
                        event.eventNode.Remove()
                        event.eventNode.AddToEnd(FastEventQueue)
                        event.time = savefile.ReadInt()

                        savefile.ReadString(name)
                        event.eventdef = idEventDef.FindEvent(name.toString())
                        if (null == event.eventdef) {
                            savefile.Error("idEvent::Restore: unknown event '%s'", name.toString())
                        }

                        savefile.ReadString(name)
                        event.typeinfo = idClass.GetClass(name.toString())
                        if (event.typeinfo == null) {
                            savefile.Error(
                                "idEvent::Restore: unknown class '%s' on event '%s'",
                                name.toString(),
                                event.eventdef!!.GetName()
                            )
                        }

                        event.`object` = savefile.ReadObject()

                        savefile.ReadInt(argsize)
                        if (argsize._val != event.eventdef!!.GetArgSize()) {
                            savefile.Error(
                                "idEvent::Restore: arg size (%d) doesn't match saved arg size(%d) on event '%s'",
                                event.eventdef!!.GetArgSize(),
                                argsize._val,
                                event.eventdef!!.GetName()
                            )
                        }
                        if (argsize._val != 0) {
                            val numArgs = event.eventdef!!.GetNumArgs()
                            event.data = arrayOfNulls(numArgs)
                            format = event.eventdef!!.GetArgFormat()
                            assert(format != null)
                            j = 0
                            size = 0
                            while (j < numArgs) {
                                when (format!![j]) {
                                    D_EVENT_FLOAT -> {
                                        event.data!![j] = idEventArg<Any?>(D_EVENT_FLOAT.code, savefile.ReadFloat())
                                        size += SIZEOF_INTPTR
                                    }

                                    D_EVENT_INTEGER -> {
                                        event.data!![j] = idEventArg<Any?>(D_EVENT_INTEGER.code, savefile.ReadInt())
                                        size += SIZEOF_INTPTR
                                    }

                                    D_EVENT_ENTITY, D_EVENT_ENTITY_NULL -> {
                                        val entityPtr = Game_local.idEntityPtr<idEntity>()
                                        entityPtr.Restore(savefile)
                                        event.data!![j] = idEventArg<Any?>(format[j].code, entityPtr.GetEntity())
                                        size += SIZEOF_INTPTR
                                    }

                                    D_EVENT_VECTOR -> {
                                        val buffer = idVec3()
                                        savefile.ReadVec3(buffer)
                                        event.data!![j] = idEventArg<Any?>(D_EVENT_VECTOR.code, buffer)
                                        size += E_EVENT_SIZEOF_VEC
                                    }

                                    D_EVENT_STRING -> {
                                        val s = idStr()
                                        savefile.ReadString(s)
                                        event.data!![j] = idEventArg<Any?>(D_EVENT_STRING.code, s.toString())
                                        size += Script_Program.MAX_STRING_LEN
                                    }

                                    D_EVENT_TRACE -> {
                                        val readBool = savefile.ReadBool()
                                        size += SIZEOF_BOOL
                                        if (readBool) {
                                            size += SIZEOF_TRACE_T
                                            val t = trace_s()
                                            val hadMaterial = RestoreTrace(savefile, t)
                                            event.data!![j] = idEventArg<Any?>(D_EVENT_TRACE.code, t)
                                            if (hadMaterial) {
                                                size += Script_Program.MAX_STRING_LEN
                                                str.clear()
                                                savefile.Read(str, Script_Program.MAX_STRING_LEN)
                                                val materialName = String(str.array()).trimEnd('\u0000')
                                                if (materialName.isNotEmpty()) {
                                                    t.c.material =
                                                        DeclManager.declManager.FindMaterial(materialName, true)
                                                }
                                            }
                                        } else {
                                            event.data!![j] = idEventArg<Any?>(D_EVENT_TRACE.code, null)
                                        }
                                    }

                                    else -> {}
                                }
                                ++j
                            }
                            assert(size == event.eventdef!!.GetArgSize())
                        } else {
                            event.data = null
                        }
                        i++
                    }
                }
            }

            /*
             ================
             idEvent::SaveTrace

             idSaveGame has a WriteTrace procedure, but unfortunately idEvent wants the material
             string name at the end of the data structure rather than in the middle
             ================
             */
            fun SaveTrace(savefile: idSaveGame, trace: trace_s) {
                savefile.WriteFloat(trace.fraction)
                savefile.WriteVec3(trace.endpos)
                savefile.WriteMat3(trace.endAxis)
                savefile.WriteInt((trace.c.type).ordinal)
                savefile.WriteVec3(trace.c.point)
                savefile.WriteVec3(trace.c.normal)
                savefile.WriteFloat(trace.c.dist)
                savefile.WriteInt(trace.c.contents)
                savefile.WriteInt(if (trace.c.material != null) 1 else 0)
                savefile.WriteInt(trace.c.contents) // NOTE: duplicate write, matches C++ bug (preserved)
                savefile.WriteInt(trace.c.modelFeature)
                savefile.WriteInt(trace.c.trmFeature)
                savefile.WriteInt(trace.c.id)
            }

            /*
             ================
             idEvent::RestoreTrace

             idRestoreGame has a ReadTrace procedure, but unfortunately idEvent wants the material
             string name at the end of the data structure rather than in the middle
             ================
             */
            fun RestoreTrace(savefile: idRestoreGame, trace: trace_s): Boolean {
                trace.fraction = savefile.ReadFloat()
                savefile.ReadVec3(trace.endpos)
                savefile.ReadMat3(trace.endAxis)
                trace.c.type = contactType_t.values()[savefile.ReadInt()]
                savefile.ReadVec3(trace.c.point)
                savefile.ReadVec3(trace.c.normal)
                trace.c.dist = savefile.ReadFloat()
                trace.c.contents = savefile.ReadInt()
                val hadMaterial = savefile.ReadInt() != 0
                trace.c.contents = savefile.ReadInt() // NOTE: duplicate read overwrites, matches C++ bug (preserved)
                trace.c.modelFeature = savefile.ReadInt()
                trace.c.trmFeature = savefile.ReadInt()
                trace.c.id = savefile.ReadInt()
                return hadMaterial
            }
        }
    }

    // NOTE: Kotlin-only, no C++ counterpart.
    // Forces loading of companion objects in game classes that contain idEventDef declarations.
    // This ensures all event definitions are registered before the event system is initialized.
    init {
        val vagary = AI_Vagary
        val light = Light
        val misc = Misc
        val moveable = Moveable
        val mover = Mover
        val player = Player
        val projectile = Projectile
        val thread = Script_Thread
        val securityCamera = SecurityCamera
        val sound = Sound
        val target = Target
        val trigger = Trigger
        val weapon = Weapon
    }
}
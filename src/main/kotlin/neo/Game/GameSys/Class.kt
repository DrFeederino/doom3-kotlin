/*
 * ===========================================================================
 *
 * Doom 3 GPL Source Code
 * Copyright (C) 1999-2011 id Software LLC, a ZeniMax Media company.
 * Translated to Kotlin by Dr. Feederino with support of Claude Code
 *
 * This file is part of the Doom 3 Kotlin project.
 * Original source: neo/game/gamesys/Class.h, neo/game/gamesys/Class.cpp
 *
 * Doom 3 Source Code is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Doom 3 Source Code is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Doom 3 Source Code.  If not, see <http://www.gnu.org/licenses/>.
 *
 * ===========================================================================
 *
 * Base class for all game objects.  Provides fast run-time type checking
 * and run-time instancing of objects.
 *
 * Each idClass descendant declares a companion-level idTypeInfo (val Type)
 * that auto-registers into a central HashMap registry on construction.
 * This replaces the C++ CLASS_DECLARATION macro system. Event dispatch
 * continues through per-class getEventCallBack() virtual methods.
 */
package neo.Game.GameSys

import neo.Game.AI.AI_Vagary
import neo.Game.idAFAttachment
import neo.Game.idAFEntity_Base
import neo.Game.idAFEntity_ClawFourFingers
import neo.Game.idAFEntity_Gibbable
import neo.Game.idAFEntity_Generic
import neo.Game.idAFEntity_SteamPipe
import neo.Game.idAFEntity_Vehicle
import neo.Game.idAFEntity_VehicleFourWheels
import neo.Game.idAFEntity_VehicleSimple
import neo.Game.idAFEntity_VehicleSixWheels
import neo.Game.idAFEntity_WithAttachedHead
import neo.Game.idChain
import neo.Game.idMultiModelAF
import neo.Game.AI.idAI
import neo.Game.AI.idCombatNode
import neo.Game.Animation.Anim_Testmodel.idTestModel
import neo.Game.BrittleFracture.idBrittleFracture
import neo.Game.EV_Activate
import neo.Game.idCamera
import neo.Game.idCameraAnim
import neo.Game.idCameraView
import neo.Game.idEntityFx
import neo.Game.idTeleporter
import neo.Game.GameEdit.idCursor3D
import neo.Game.GameSys.Class.*
import neo.Game.GameSys.Event.D_EVENT_MAXARGS
import neo.Game.GameSys.Event.idEvent
import neo.Game.GameSys.Event.idEventDef
import neo.Game.GameSys.SaveGame.idRestoreGame
import neo.Game.GameSys.SaveGame.idSaveGame
import neo.Game.Game_local
import neo.Game.idItem
import neo.Game.idItemPowerup
import neo.Game.idItemRemover
import neo.Game.idMoveableItem
import neo.Game.idMoveablePDAItem
import neo.Game.idObjective
import neo.Game.idObjectiveComplete
import neo.Game.idPDAItem
import neo.Game.idVideoCDItem
import neo.Game.Light.idLight
import neo.Game.Misc.idActivator
import neo.Game.Misc.idAnimated
import neo.Game.Misc.idBeam
import neo.Game.Misc.idDamagable
import neo.Game.Misc.idEarthQuake
import neo.Game.Misc.idExplodable
import neo.Game.Misc.idForceField
import neo.Game.Misc.idFuncAASObstacle
import neo.Game.Misc.idFuncAASPortal
import neo.Game.Misc.idFuncEmitter
import neo.Game.Misc.idFuncPortal
import neo.Game.Misc.idFuncRadioChatter
import neo.Game.Misc.idFuncSmoke
import neo.Game.Misc.idFuncSplat
import neo.Game.Misc.idLiquid
import neo.Game.Misc.idLocationEntity
import neo.Game.Misc.idLocationSeparatorEntity
import neo.Game.Misc.idPathCorner
import neo.Game.Misc.idPhantomObjects
import neo.Game.Misc.idPlayerStart
import neo.Game.Misc.idShaking
import neo.Game.Misc.idSpawnableEntity
import neo.Game.Misc.idSpring
import neo.Game.Misc.idStaticEntity
import neo.Game.Misc.idTextEntity
import neo.Game.Misc.idVacuumEntity
import neo.Game.Misc.idVacuumSeparatorEntity
import neo.Game.Moveable.idBarrel
import neo.Game.Moveable.idExplodingBarrel
import neo.Game.Moveable.idMoveable
import neo.Game.Mover.idBobber
import neo.Game.Mover.idDoor
import neo.Game.Mover.idElevator
import neo.Game.Mover.idMover
import neo.Game.Mover.idMover_Binary
import neo.Game.Mover.idMover_Periodic
import neo.Game.Mover.idPendulum
import neo.Game.Mover.idPlat
import neo.Game.Mover.idRiser
import neo.Game.Mover.idRotater
import neo.Game.Mover.idSplinePath
import neo.Game.Physics.Force.idForce
import neo.Game.Physics.Force_Constant.idForce_Constant
import neo.Game.Physics.Force_Drag.idForce_Drag
import neo.Game.Physics.Force_Field.idForce_Field
import neo.Game.Physics.Force_Spring.idForce_Spring
import neo.Game.Physics.Physics.idPhysics
import neo.Game.Physics.Physics_AF.idPhysics_AF
import neo.Game.Physics.Physics_Actor.idPhysics_Actor
import neo.Game.Physics.Physics_Base.idPhysics_Base
import neo.Game.Physics.Physics_Monster.idPhysics_Monster
import neo.Game.Physics.Physics_Parametric.idPhysics_Parametric
import neo.Game.Physics.Physics_Player.idPhysics_Player
import neo.Game.Physics.Physics_RigidBody.idPhysics_RigidBody
import neo.Game.Physics.Physics_Static.idPhysics_Static
import neo.Game.Physics.Physics_StaticMulti.idPhysics_StaticMulti
import neo.Game.Player.idPlayer
import neo.Game.Projectile.idBFGProjectile
import neo.Game.Projectile.idDebris
import neo.Game.Projectile.idGuidedProjectile
import neo.Game.Projectile.idProjectile
import neo.Game.Projectile.idSoulCubeMissile
import neo.Game.Script.Script_Thread.idThread
import neo.Game.SecurityCamera.idSecurityCamera
import neo.Game.Sound.idSound
import neo.Game.Target.idTarget
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
import neo.Game.Weapon.idWeapon
import neo.Game.WorldSpawn.idWorldspawn
import neo.Game.idActor
import neo.Game.idAnimatedEntity
import neo.Game.idEntity

import neo.cm.trace_s
import neo.framework.CmdSystem.cmdFunction_t
import neo.idlib.CmdArgs
import neo.idlib.Text.Str.idStr
import neo.idlib.containers.Hierarchy.idHierarchy
import neo.idlib.containers.List.idList
import neo.idlib.idException
import neo.idlib.math.SEC2MS
import neo.idlib.math.idMath
import neo.idlib.math.idVec3

val EV_Remove: idEventDef = idEventDef("<immediateremove>", null)
val EV_SafeRemove: idEventDef = idEventDef("remove", null)

class Class {
    companion object {
        var classHierarchy: idHierarchy<idTypeInfo> = idHierarchy()
        var eventCallbackMemory = 0

        // HashMap registry for all idTypeInfo instances, keyed by classname.
        // Replaces the C++ singly-linked typelist with O(1) lookup.
        val typeRegistry: HashMap<String, idTypeInfo> = HashMap()

        // this is the head of a singly linked list of all the idTypes
        // Kept for Init() traversal ordering -- populated alongside typeRegistry
        var typelist: idTypeInfo? = null
    }


    fun interface eventCallback_t<out T : idClass> {
        fun accept(t: @UnsafeVariance T, vararg args: idEventArg<*>)
    }

    fun interface eventCallback_t0<out T : idClass> : eventCallback_t<T> {
        override fun accept(t: @UnsafeVariance T, vararg args: idEventArg<*>) {
            accept(t)
        }

        fun accept(e: @UnsafeVariance T)
    }

    fun interface eventCallback_t1<out T : idClass> : eventCallback_t<T> {
        override fun accept(t: @UnsafeVariance T, vararg args: idEventArg<*>) {
            accept(t, args[0])
        }

        fun accept(t: @UnsafeVariance T, a: idEventArg<*>)
    }

    fun interface eventCallback_t2<out T : idClass> : eventCallback_t<T> {
        override fun accept(t: @UnsafeVariance T, vararg args: idEventArg<*>) {
            accept(t, args[0], args[1])
        }

        fun accept(t: @UnsafeVariance T, a: idEventArg<*>, b: idEventArg<*>)
    }

    fun interface eventCallback_t3<out T : idClass> : eventCallback_t<T> {
        override fun accept(t: @UnsafeVariance T, vararg args: idEventArg<*>) {
            accept(t, args[0], args[1], args[2])
        }

        fun accept(t: @UnsafeVariance T, a: idEventArg<*>, b: idEventArg<*>, c: idEventArg<*>)
    }

    fun interface eventCallback_t4<out T : idClass> : eventCallback_t<T> {
        override fun accept(t: @UnsafeVariance T, vararg args: idEventArg<*>) {
            accept(t, args[0], args[1], args[2], args[3])
        }

        fun accept(t: @UnsafeVariance T, a: idEventArg<*>, b: idEventArg<*>, c: idEventArg<*>, d: idEventArg<*>)
    }

    fun interface eventCallback_t5<out T : idClass> : eventCallback_t<T> {
        override fun accept(t: @UnsafeVariance T, vararg args: idEventArg<*>) {
            accept(t, args[0], args[1], args[2], args[3], args[4])
        }

        fun accept(
            t: @UnsafeVariance T,
            a: idEventArg<*>,
            b: idEventArg<*>,
            c: idEventArg<*>,
            d: idEventArg<*>,
            e: idEventArg<*>
        )
    }

    fun interface eventCallback_t6<out T : idClass> : eventCallback_t<T> {
        override fun accept(t: @UnsafeVariance T, vararg args: idEventArg<*>) {
            accept(t, args[0], args[1], args[2], args[3], args[4], args[5])
        }

        fun accept(
            t: @UnsafeVariance T,
            a: idEventArg<*>,
            b: idEventArg<*>,
            c: idEventArg<*>,
            d: idEventArg<*>,
            e: idEventArg<*>,
            f: idEventArg<*>
        )
    }

    class idEventFunc<type> {
        var event: idEventDef? = null
        var function: eventCallback_t<*>? = null
    }

    class idEventArg<T> {
        var type = 0
        var value: T

        private constructor(data: T) {
            type =
                if (data is Int) Event.D_EVENT_INTEGER.code
                else if (data is Enum<*>) Event.D_EVENT_INTEGER.code
                else if (data is Float) Event.D_EVENT_FLOAT.code
                else if (data is idVec3) Event.D_EVENT_VECTOR.code
                else if (data is idStr) Event.D_EVENT_STRING.code
                else if (data is String) Event.D_EVENT_STRING.code
                else if (data is idEntity) Event.D_EVENT_ENTITY.code
                else if (data is trace_s) Event.D_EVENT_TRACE.code
                else {
                    Event.D_EVENT_VOID.code
                }
            value = data
        }

        constructor(type: Int, data: T) {
            this.type = type
            value = data
        }

        companion object {
            fun <T> toArg(data: T): idEventArg<T> {
                return idEventArg(data)
            }

            fun toArg(data: Int): idEventArg<Int> {
                return idEventArg(Event.D_EVENT_INTEGER.code, data)
            }

            fun toArg(data: Float): idEventArg<Float> {
                return idEventArg(Event.D_EVENT_FLOAT.code, data)
            }

            fun toArg(data: idVec3): idEventArg<idVec3> {
                return idEventArg(Event.D_EVENT_VECTOR.code, data)
            }

            fun toArg(data: idStr): idEventArg<idStr> {
                return idEventArg(Event.D_EVENT_STRING.code, data)
            }

            fun toArg(data: String?): idEventArg<String?> {
                return idEventArg(Event.D_EVENT_STRING.code, data)
            }

            fun toArg(data: idEntity?): idEventArg<idEntity?> {
                return idEventArg(Event.D_EVENT_ENTITY.code, data)
            }

            fun toArg(data: trace_s?): idEventArg<trace_s?> {
                return idEventArg(Event.D_EVENT_TRACE.code, data)
            }
        }
    }

    class idAllocError(text: String /*= ""*/) : idException(text)

    /*
     ***********************************************************************

      idClass

     ***********************************************************************/
    abstract class idClass {
        companion object {
            val Type = idTypeInfo("idClass", "")

            private val eventCallbacks: MutableMap<idEventDef, eventCallback_t<*>> = run {
                val map = HashMap<idEventDef, eventCallback_t<*>>()
                map[EV_Remove] = (eventCallback_t0 { obj: idClass -> obj.Event_Remove() })
                map[EV_SafeRemove] = eventCallback_t0 { obj: idClass -> obj.Event_SafeRemove() }
                map
            }

            //
            private var initialized = false

            // FIX: Changed from const val to var — C++ modifies these in operator new/delete.
            // In JVM we don't override new/delete, but these should still be mutable for tracking.
            private var memused = 0
            private var numobjects = 0
            private var typeNumBits = 0

            // typenum order
            private val typenums: idList<idTypeInfo> = idList()

            // alphabetical order
            private val types: idList<idTypeInfo> = idList()

            fun getEventCallBacks(): MutableMap<idEventDef, eventCallback_t<*>> {
                return eventCallbacks
            }

            fun INIT() {
                var c: idTypeInfo?
                var num: Int
                Game_local.gameLocal.Printf("Initializing class hierarchy\n")
                if (initialized) {
                    Game_local.gameLocal.Printf("...already initialized\n")
                    return
                }

                // init the event callback tables for all the classes
                for (type in typeRegistry.values) {
                    type.Init()
                }

                // number the types according to the class hierarchy so we can quickly determine if a class
                // is a subclass of another
                num = 0
                c = classHierarchy.GetNext()
                while (c != null) {
                    c.typeNum = num
                    c.lastChild += num
                    c = c.node.GetNext()
                    num++
                }

                // number of bits needed to send types over network
                typeNumBits = idMath.BitsForInteger(num)

                // create a list of the types so we can do quick lookups
                // one list in alphabetical order, one in typenum order
                types.SetGranularity(1)
                types.SetNum(num)
                typenums.SetGranularity(1)
                typenums.SetNum(num)
                num = 0
                c = typelist
                while (c != null) {
                    types[num] = c
                    typenums[c.typeNum] = c
                    c = c.next
                    num++
                }
                initialized = true
                Game_local.gameLocal.Printf(
                    "...%d classes, %d bytes for event callbacks\n",
                    types.Num(),
                    eventCallbackMemory
                )
            }

            /*
             ================
             idClass::Shutdown
             ================
             */
            fun Shutdown() {
                for (type in typeRegistry.values) {
                    type.Shutdown()
                }
                types.Clear()
                typenums.Clear()
                initialized = false
            }

            /*
         ================
         idClass::GetClass

         Returns the idTypeInfo for the name of the class passed in.  This is a static function
         so it must be called as idClass::GetClass( classname )
         ================
         */
            fun GetClass(name: String?): idTypeInfo? {
                if (name == null) return null
                return typeRegistry[name]
            }

            /*
             ================
             idClass::CreateInstance

             Looks up idTypeInfo by name and calls its factory lambda.
             ================
             */
            fun CreateInstance(name: String?): idClass? {
                val type = GetClass(name) ?: return null
                return try {
                    type.createInstance()
                } catch (e: idAllocError) {
                    null
                }
            }

            fun GetNumTypes(): Int {
                return types.Num()
            }

            fun GetTypeNumBits(): Int {
                return typeNumBits
            }

            /*
             ================
             idClass::GetType
             ================
             */
            fun GetType(typeNum: Int): idTypeInfo? {
                var c: idTypeInfo?
                if (!initialized) {
                    c = typelist
                    while (c != null) {
                        if (c.typeNum == typeNum) {
                            return c
                        }
                        c = c.next
                    }
                } else if (typeNum >= 0 && typeNum < types.Num()) {
                    return typenums[typeNum]
                }
                return null
            }

            fun delete(clazz: idClass?) {
                clazz?._deconstructor()
            }
        }

        init {
            assert(eventCallbacks[EV_Remove] != null)
        }

        abstract fun CreateInstance(): idClass
        abstract fun GetType(): idTypeInfo
        abstract fun getEventCallBack(event: idEventDef): eventCallback_t<*>?

        /*
         ================
         idClass::IsType

         Checks if the object's class is a subclass of the class defined by the
         passed in idTypeInfo.
         ================
         */
        fun IsType(c: idTypeInfo): Boolean {
            return GetType().IsType(c)
        }

        /*
         ================
         idClass::~idClass

         Destructor for object.  Cancels any events that depend on this object.
         ================
         */
        protected open fun _deconstructor() {
            idEvent.CancelEvents(this)
        }

        /*
         ================
         idClass::Spawn
         ================
         */
        open fun Spawn() {}

        /*
         ================
         idClass::CallSpawn

         NOTE: Differs from C++ — C++ uses CallSpawnFunc to walk the idTypeInfo hierarchy
         and call each level's Spawn() from base to derived, skipping duplicates.
         In the Kotlin port, entity classes use super.Spawn() chains, so calling the
         most-derived Spawn() achieves the same base-to-derived initialization order.
         ================
         */
        fun CallSpawn() {
            Spawn()
        }

        /*
         ================
         idClass::GetClassname

         Returns the text classname of the object.
         ================
         */
        fun GetClassname(): String {
            return GetType().classname
        }

        /*
         ================
         idClass::GetSuperclass

         Returns the text classname of the superclass.
         ================
         */
        fun GetSuperclass(): String {
            return GetType().superclass
        }

        /*
         ================
         idClass::FindUninitializedMemory

         NOTE: Differs from C++ — This is a no-op on JVM. The C++ version walks
         raw memory to detect 0xcdcdcdcd debug fill patterns. JVM initializes
         all fields to their default values, so this check is unnecessary.
         ================
         */
        fun FindUninitializedMemory() {
            // No-op on JVM — all fields are initialized by the runtime.
        }

        open fun Save(savefile: idSaveGame) {}
        open fun Restore(savefile: idRestoreGame) {}

        /*
         ================
         idClass::RespondsTo
         ================
         */
        fun RespondsTo(ev: idEventDef): Boolean {
            // NOTE: Differs from C++ — C++ delegates to GetType()->RespondsTo(ev) which checks
            // eventMap[eventNum]. In the Kotlin port, we use the virtual getEventCallBack dispatch
            // since the idTypeInfo eventMap system is not populated.
            return getEventCallBack(ev) != null
        }

        /*
         ================
         idClass::PostEventMS
         ================
         */
        fun PostEventMS(ev: idEventDef, time: Int): Boolean {
            return PostEventArgs(ev, time, 0)
        }

        // FIX: Changed time parameter from Float to Int to match C++ PostEventMS(ev, int time, idEventArg).
        // Callers using float literals (e.g., 0.0f) need to be updated to int literals (e.g., 0).
        fun PostEventMS(ev: idEventDef, time: Int, arg1: Any?): Boolean {
            return PostEventArgs(ev, time, 1, idEventArg.toArg<Any?>(arg1))
        }

        fun PostEventMS(ev: idEventDef, time: Int, arg1: Any?, arg2: Any?): Boolean {
            return PostEventArgs(ev, time, 2, idEventArg.toArg<Any?>(arg1), idEventArg.toArg<Any?>(arg2))
        }

        fun PostEventMS(ev: idEventDef, time: Int, arg1: Any?, arg2: Any?, arg3: Any?): Boolean {
            return PostEventArgs(
                ev,
                time,
                3,
                idEventArg.toArg<Any?>(arg1),
                idEventArg.toArg<Any?>(arg2),
                idEventArg.toArg<Any?>(arg3)
            )
        }

        fun PostEventMS(ev: idEventDef, time: Int, arg1: Any?, arg2: Any?, arg3: Any?, arg4: Any?): Boolean {
            return PostEventArgs(
                ev,
                time,
                4,
                idEventArg.toArg<Any?>(arg1),
                idEventArg.toArg<Any?>(arg2),
                idEventArg.toArg<Any?>(arg3),
                idEventArg.toArg<Any?>(arg4)
            )
        }

        fun PostEventMS(
            ev: idEventDef,
            time: Int,
            arg1: Any?,
            arg2: Any?,
            arg3: Any?,
            arg4: Any?,
            arg5: Any?
        ): Boolean {
            return PostEventArgs(
                ev,
                time,
                5,
                idEventArg.toArg<Any?>(arg1),
                idEventArg.toArg<Any?>(arg2),
                idEventArg.toArg<Any?>(arg3),
                idEventArg.toArg<Any?>(arg4),
                idEventArg.toArg<Any?>(arg5)
            )
        }

        fun PostEventMS(
            ev: idEventDef,
            time: Int,
            arg1: Any?,
            arg2: Any?,
            arg3: Any?,
            arg4: Any?,
            arg5: Any?,
            arg6: Any?
        ): Boolean {
            return PostEventArgs(
                ev,
                time,
                6,
                idEventArg.toArg<Any?>(arg1),
                idEventArg.toArg<Any?>(arg2),
                idEventArg.toArg<Any?>(arg3),
                idEventArg.toArg<Any?>(arg4),
                idEventArg.toArg<Any?>(arg5),
                idEventArg.toArg<Any?>(arg6)
            )
        }

        fun PostEventMS(
            ev: idEventDef,
            time: Int,
            arg1: Any?,
            arg2: Any?,
            arg3: Any?,
            arg4: Any?,
            arg5: Any?,
            arg6: Any?,
            arg7: Any?
        ): Boolean {
            return PostEventArgs(
                ev,
                time,
                7,
                idEventArg.toArg<Any?>(arg1),
                idEventArg.toArg<Any?>(arg2),
                idEventArg.toArg<Any?>(arg3),
                idEventArg.toArg<Any?>(arg4),
                idEventArg.toArg<Any?>(arg5),
                idEventArg.toArg<Any?>(arg6),
                idEventArg.toArg<Any?>(arg7)
            )
        }

        fun PostEventMS(
            ev: idEventDef,
            time: Int,
            arg1: Any?,
            arg2: Any?,
            arg3: Any?,
            arg4: Any?,
            arg5: Any?,
            arg6: Any?,
            arg7: Any?,
            arg8: Any?
        ): Boolean {
            return PostEventArgs(
                ev,
                time,
                8,
                idEventArg.toArg<Any?>(arg1),
                idEventArg.toArg<Any?>(arg2),
                idEventArg.toArg<Any?>(arg3),
                idEventArg.toArg<Any?>(arg4),
                idEventArg.toArg<Any?>(arg5),
                idEventArg.toArg<Any?>(arg6),
                idEventArg.toArg<Any?>(arg7),
                idEventArg.toArg<Any?>(arg8)
            )
        }

        /*
         ================
         idClass::PostEventSec
         ================
         */
        fun PostEventSec(ev: idEventDef, time: Float): Boolean {
            return PostEventArgs(ev, SEC2MS(time).toInt(), 0)
        }

        fun PostEventSec(ev: idEventDef, time: Float, arg1: idEventArg<*>?): Boolean {
            return PostEventArgs(ev, SEC2MS(time).toInt(), 1, arg1)
        }

        fun PostEventSec(ev: idEventDef, time: Float, arg1: Any?): Boolean {
            return PostEventArgs(ev, SEC2MS(time).toInt(), 1, idEventArg.toArg<Any?>(arg1))
        }

        fun PostEventSec(ev: idEventDef, time: Float, arg1: Any?, arg2: Any?): Boolean {
            return PostEventArgs(
                ev,
                SEC2MS(time).toInt(),
                2,
                idEventArg.toArg<Any?>(arg1),
                idEventArg.toArg<Any?>(arg2)
            )
        }

        fun PostEventSec(ev: idEventDef, time: Float, arg1: Any?, arg2: Any?, arg3: Any?): Boolean {
            return PostEventArgs(
                ev,
                SEC2MS(time).toInt(),
                3,
                idEventArg.toArg<Any?>(arg1),
                idEventArg.toArg<Any?>(arg2),
                idEventArg.toArg<Any?>(arg3)
            )
        }

        fun PostEventSec(ev: idEventDef, time: Float, arg1: Any?, arg2: Any?, arg3: Any?, arg4: Any?): Boolean {
            return PostEventArgs(
                ev,
                SEC2MS(time).toInt(),
                4,
                idEventArg.toArg<Any?>(arg1),
                idEventArg.toArg<Any?>(arg2),
                idEventArg.toArg<Any?>(arg3),
                idEventArg.toArg<Any?>(arg4)
            )
        }

        fun PostEventSec(
            ev: idEventDef,
            time: Float,
            arg1: Any?,
            arg2: Any?,
            arg3: Any?,
            arg4: Any?,
            arg5: Any?
        ): Boolean {
            return PostEventArgs(
                ev,
                SEC2MS(time).toInt(),
                5,
                idEventArg.toArg<Any?>(arg1),
                idEventArg.toArg<Any?>(arg2),
                idEventArg.toArg<Any?>(arg3),
                idEventArg.toArg<Any?>(arg4),
                idEventArg.toArg<Any?>(arg5)
            )
        }

        fun PostEventSec(
            ev: idEventDef,
            time: Float,
            arg1: Any?,
            arg2: Any?,
            arg3: Any?,
            arg4: Any?,
            arg5: Any?,
            arg6: Any?
        ): Boolean {
            return PostEventArgs(
                ev,
                SEC2MS(time).toInt(),
                6,
                idEventArg.toArg<Any?>(arg1),
                idEventArg.toArg<Any?>(arg2),
                idEventArg.toArg<Any?>(arg3),
                idEventArg.toArg<Any?>(arg4),
                idEventArg.toArg<Any?>(arg5),
                idEventArg.toArg<Any?>(arg6)
            )
        }

        fun PostEventSec(
            ev: idEventDef,
            time: Float,
            arg1: Any?,
            arg2: Any?,
            arg3: Any?,
            arg4: Any?,
            arg5: Any?,
            arg6: Any?,
            arg7: Any?
        ): Boolean {
            return PostEventArgs(
                ev,
                SEC2MS(time).toInt(),
                7,
                idEventArg.toArg<Any?>(arg1),
                idEventArg.toArg<Any?>(arg2),
                idEventArg.toArg<Any?>(arg3),
                idEventArg.toArg<Any?>(arg4),
                idEventArg.toArg<Any?>(arg5),
                idEventArg.toArg<Any?>(arg6),
                idEventArg.toArg<Any?>(arg7)
            )
        }

        fun PostEventSec(
            ev: idEventDef,
            time: Float,
            arg1: Any?,
            arg2: Any?,
            arg3: Any?,
            arg4: Any?,
            arg5: Any?,
            arg6: Any?,
            arg7: Any?,
            arg8: Any?
        ): Boolean {
            return PostEventArgs(
                ev,
                SEC2MS(time).toInt(),
                8,
                idEventArg.toArg<Any?>(arg1),
                idEventArg.toArg<Any?>(arg2),
                idEventArg.toArg<Any?>(arg3),
                idEventArg.toArg<Any?>(arg4),
                idEventArg.toArg<Any?>(arg5),
                idEventArg.toArg<Any?>(arg6),
                idEventArg.toArg<Any?>(arg7),
                idEventArg.toArg<Any?>(arg8)
            )
        }

        /*
         ================
         idClass::ProcessEvent
         ================
         */
        fun ProcessEvent(ev: idEventDef): Boolean {
            return ProcessEventArgs(ev, 0)
        }

        fun ProcessEvent(ev: idEventDef, arg1: Any?): Boolean {
            return ProcessEventArgs(ev, 1, idEventArg.toArg<Any?>(arg1))
        }

        fun ProcessEvent(ev: idEventDef, arg1: idEntity?): Boolean {
            return ProcessEventArgs(ev, 1, idEventArg.toArg(arg1))
        }

        fun ProcessEvent(ev: idEventDef, arg1: Any?, arg2: Any?): Boolean {
            return ProcessEventArgs(ev, 2, idEventArg.toArg<Any?>(arg1), idEventArg.toArg<Any?>(arg2))
        }

        fun ProcessEvent(ev: idEventDef, arg1: Any?, arg2: Any?, arg3: Any?): Boolean {
            return ProcessEventArgs(
                ev,
                3,
                idEventArg.toArg<Any?>(arg1),
                idEventArg.toArg<Any?>(arg2),
                idEventArg.toArg<Any?>(arg3)
            )
        }

        fun ProcessEvent(ev: idEventDef, arg1: Any?, arg2: Any?, arg3: Any?, arg4: Any?): Boolean {
            return ProcessEventArgs(
                ev,
                4,
                idEventArg.toArg<Any?>(arg1),
                idEventArg.toArg<Any?>(arg2),
                idEventArg.toArg<Any?>(arg3),
                idEventArg.toArg<Any?>(arg4)
            )
        }

        fun ProcessEvent(ev: idEventDef, arg1: Any?, arg2: Any?, arg3: Any?, arg4: Any?, arg5: Any?): Boolean {
            return ProcessEventArgs(
                ev,
                5,
                idEventArg.toArg<Any?>(arg1),
                idEventArg.toArg<Any?>(arg2),
                idEventArg.toArg<Any?>(arg3),
                idEventArg.toArg<Any?>(arg4),
                idEventArg.toArg<Any?>(arg5)
            )
        }

        fun ProcessEvent(
            ev: idEventDef,
            arg1: Any?,
            arg2: Any?,
            arg3: Any?,
            arg4: Any?,
            arg5: Any?,
            arg6: Any?
        ): Boolean {
            return ProcessEventArgs(
                ev,
                6,
                idEventArg.toArg<Any?>(arg1),
                idEventArg.toArg<Any?>(arg2),
                idEventArg.toArg<Any?>(arg3),
                idEventArg.toArg<Any?>(arg4),
                idEventArg.toArg<Any?>(arg5),
                idEventArg.toArg<Any?>(arg6)
            )
        }

        fun ProcessEvent(
            ev: idEventDef,
            arg1: Any?,
            arg2: Any?,
            arg3: Any?,
            arg4: Any?,
            arg5: Any?,
            arg6: Any?,
            arg7: Any?
        ): Boolean {
            return ProcessEventArgs(
                ev,
                7,
                idEventArg.toArg<Any?>(arg1),
                idEventArg.toArg<Any?>(arg2),
                idEventArg.toArg<Any?>(arg3),
                idEventArg.toArg<Any?>(arg4),
                idEventArg.toArg<Any?>(arg5),
                idEventArg.toArg<Any?>(arg6),
                idEventArg.toArg<Any?>(arg7)
            )
        }

        fun ProcessEvent(
            ev: idEventDef,
            arg1: Any?,
            arg2: Any?,
            arg3: Any?,
            arg4: Any?,
            arg5: Any?,
            arg6: Any?,
            arg7: Any?,
            arg8: Any?
        ): Boolean {
            return ProcessEventArgs(
                ev,
                8,
                idEventArg.toArg(arg1),
                idEventArg.toArg(arg2),
                idEventArg.toArg(arg3),
                idEventArg.toArg(arg4),
                idEventArg.toArg(arg5),
                idEventArg.toArg(arg6),
                idEventArg.toArg(arg7),
                idEventArg.toArg(arg8)
            )
        }

        /*
         ================
         idClass::ProcessEventArgPtr
         ================
         */
        fun ProcessEventArgPtr(ev: idEventDef?, data: Array<idEventArg<*>?>): Boolean {
            val callback: eventCallback_t<*>?

            assert(ev != null)
            assert(Event.initialized)

            if (SysCvar.g_debugTriggers.GetBool() && ev === EV_Activate && this is idEntity) {
                val name: String =
                    if (data[0] != null && data[0]!!.value as idClass? is idEntity) (data[0]!!.value as idEntity).GetName() else "NULL"
                Game_local.gameLocal.Printf(
                    "%d: '%s' activated by '%s'\n",
                    Game_local.gameLocal.framenum,
                    this.GetName(),
                    name
                )
            }

            // NOTE: Differs from C++ — C++ uses c->eventMap[ev->GetEventNum()] via idTypeInfo.
            // In the Kotlin port, we use getEventCallBack virtual dispatch.
            callback = getEventCallBack(ev!!)

            if (callback == null) {
                // we don't respond to this event, so ignore it
                return false
            }

            // NOTE: Differs from C++ — C++ uses a switch on ev->GetFormatspecIndex() with
            // generated code from Callbacks.cpp to cast the callback to the right function
            // pointer type based on the arg format. In Kotlin, we use the varargs-based
            // eventCallback_t.accept() which handles all arg counts uniformly.
            assert(D_EVENT_MAXARGS == 8)

            when (ev.GetNumArgs()) {
                0, 1, 2, 3, 4, 5, 6, 7, 8 ->
                    callback.accept(this, *data as Array<out idEventArg<*>>)

                else -> Game_local.gameLocal.Warning("Invalid formatspec on event '%s'", ev.GetName())
            }

            return true
        }

        /*
         ================
         idClass::CancelEvents
         ================
         */
        fun CancelEvents(ev: idEventDef?) {
            idEvent.CancelEvents(this, ev)
        }

        /*
         ================
         idClass::Event_Remove

         C++ original: delete this;
         NOTE: Differs from C++ — JVM uses garbage collection. We call _deconstructor()
         which cancels events, matching the C++ destructor behavior.
         ================
         */
        open fun Event_Remove() {
            _deconstructor()
        }

        // Static functions
        /*
         ================
         idClass::Init

         Should be called after all idTypeInfos are initialized, so must be called
         manually upon game code initialization.  Tells all the idTypeInfos to initialize
         their event callback table for the associated class.  This should only be called
         once during the execution of the program or DLL.
         ================
         */
        open fun Init() {
            INIT()
        }

        abstract fun oSet(oGet: idClass?)

        /*
         ================
         idClass::PostEventArgs
         ================
         */
        private fun PostEventArgs(ev: idEventDef, time: Int, numargs: Int, vararg args: idEventArg<*>?): Boolean {

            val event: idEvent
            assert(ev != null)
            if (!Event.initialized) {
                return false
            }

            // NOTE: Differs from C++ — C++ checks c->eventMap[ev->GetEventNum()] using the
            // idTypeInfo eventMap. In the Kotlin port, we use getEventCallBack virtual dispatch.
            val c = this.GetType()
            if (getEventCallBack(ev) == null) {
                // we don't respond to this event, so ignore it
                return false
            }

            // we service events on the client to avoid any bad code filling up the event pool
            // we don't want them processed usually, unless when the map is (re)loading.
            // we allow threads to run fine, though.
            if (Game_local.gameLocal.isClient && Game_local.gameLocal.GameState() != Game_local.gameState_t.GAMESTATE_STARTUP && this !is idThread) {
                return true
            }

            event = idEvent.Alloc(ev, numargs, *args)
            event.Schedule(this, c, time)
            return true
        }

        /*
         ================
         idClass::ProcessEventArgs
         ================
         */
        private fun ProcessEventArgs(ev: idEventDef, numargs: Int, vararg args: idEventArg<*>?): Boolean {
            assert(ev != null)
            assert(Event.initialized)

            // FIX: Restored the eventMap check that was commented out.
            // C++ checks c->eventMap[ev->GetEventNum()] before copying args.
            if (getEventCallBack(ev) == null) {
                // we don't respond to this event, so ignore it
                return false
            }

            val data: Array<idEventArg<*>?> = arrayOfNulls(D_EVENT_MAXARGS)
            idEvent.CopyArgs(ev, numargs, args, data)
            ProcessEventArgPtr(ev, data)
            return true
        }

        /*
         ================
         idClass::Event_SafeRemove
         ================
         */
        private fun Event_SafeRemove() {
            // Forces the remove to be done at a safe time
            PostEventMS(EV_Remove, 0)
        }

        /*
         ================
         idClass::DisplayInfo_f
         ================
         */
        class DisplayInfo_f private constructor() : cmdFunction_t() {
            override fun run(args: CmdArgs.idCmdArgs?) {
                Game_local.gameLocal.Printf(
                    "Class memory status: %d bytes allocated in %d objects\n",
                    memused,
                    numobjects
                )
            }

            companion object {
                private val instance: cmdFunction_t = DisplayInfo_f()
                fun getInstance(): cmdFunction_t {
                    return instance
                }
            }
        }

        /*
         ================
         idClass::ListClasses_f
         ================
         */
        class ListClasses_f private constructor() : cmdFunction_t() {
            override fun run(args: CmdArgs.idCmdArgs?) {
                var i: Int
                var type: idTypeInfo
                Game_local.gameLocal.Printf("%-24s %-24s %-6s %-6s\n", "Classname", "Superclass", "Type", "Subclasses")
                Game_local.gameLocal.Printf("----------------------------------------------------------------------\n")
                i = 0
                while (i < types.Num()) {
                    type = types[i]
                    Game_local.gameLocal.Printf(
                        "%-24s %-24s %6d %6d\n",
                        type.classname,
                        type.superclass,
                        type.typeNum,
                        type.lastChild - type.typeNum
                    )
                    i++
                }
                Game_local.gameLocal.Printf("...%d classes", types.Num())
            }

            companion object {
                private val instance: cmdFunction_t = ListClasses_f()
                fun getInstance(): cmdFunction_t {
                    return instance
                }
            }
        }
    }

    /**
     * *********************************************************************
     *
     *
     * idTypeInfo
     *
     */
    class idTypeInfo(
        classname: String,
        superclass: String,
        eventCallbacks: Array<idEventFunc<idClass>> = arrayOf(idEventFunc()),
        val createInstance: () -> idClass = { throw UnsupportedOperationException("Cannot instantiate abstract $classname") }
    ) {
        //
        var classname: String
        val name: String get() = classname  // compatibility with GetType().name call sites

        //
        var eventCallbacks: Array<idEventFunc<idClass>>
        var eventMap: Array<eventCallback_t<*>?>?
        var freeEventMap: Boolean
        var lastChild: Int
        var next: idTypeInfo? = null

        //
        var node: idHierarchy<idTypeInfo> = idHierarchy()
        var superclass: String
        var typeNum: Int
        var zuper: idTypeInfo?

        // ~idTypeInfo();
        /*
         ================
         idTypeInfo::Init

         Initializes the event callback table for the class.  Creates a 
         table for fast lookups of event functions.  Should only be called once.
         ================
         */
        fun Init() {
            var c: idTypeInfo?
            var def: Array<idEventFunc<idClass>>
            var ev: Int
            var i: Int
            val set: BooleanArray
            val num: Int
            if (eventMap != null) {
                // we've already been initialized by a subclass
                return
            }

            // make sure our superclass is initialized first
            if (zuper != null && null == zuper!!.eventMap) {
                zuper!!.Init()
            }

            // add to our node hierarchy
            if (zuper != null) {
                node.ParentTo(zuper!!.node)
            } else {
                node.ParentTo(classHierarchy)
            }
            node.SetOwner(this)

            // keep track of the number of children below each class
            c = zuper
            while (c != null) {
                c.lastChild++
                c = c.zuper
            }

            // if we're not adding any new event callbacks, we can just use our superclass's table
            if ((null == eventCallbacks || eventCallbacks[0].event == null) && zuper != null) {
                eventMap = zuper!!.eventMap
                return
            }

            // set a flag so we know to delete the eventMap table
            freeEventMap = true

            // Allocate our new table.  It has to have as many entries as there
            // are events.  NOTE: could save some space by keeping track of the maximum
            // event that the class responds to and doing range checking.
            num = idEventDef.NumEventCommands()
            eventMap = arrayOfNulls<eventCallback_t<*>?>(num)
            //	memset( eventMap, 0, sizeof( eventCallback_t ) * num );
            eventCallbackMemory += num * 4

            // allocate temporary memory for flags so that the subclass's event callbacks
            // override the superclass's event callback
            set = BooleanArray(num)
            //	memset( set, 0, sizeof( bool ) * num );

            // go through the inheritence order and copies the event callback function into
            // a list indexed by the event number.  This allows fast lookups of
            // event functions.
            c = this
            while (c != null) {
                def = c.eventCallbacks
                if (def.isNullOrEmpty()) {
                    c = c.zuper
                    continue
                }

                // go through each entry until we hit the NULL terminator
                i = 0
                while (def[i].event != null) {
                    ev = def[i].event!!.GetEventNum()
                    if (set[ev]) {
                        i++
                        continue
                    }
                    set[ev] = true
                    eventMap!![ev] = def[i].function
                    i++
                }
                c = c.zuper
            }

//	delete[] set;
        }

        /*
         ================
         idTypeInfo::Shutdown

         Should only be called when DLL or EXE is being shutdown.
         Although it cleans up any allocated memory, it doesn't bother to remove itself 
         from the class list since the program is shutting down.
         ================
         */
        fun Shutdown() {
            // free up the memory used for event lookups
            if (eventMap != null) {
//		if ( freeEventMap ) {
//			delete[] eventMap;
//		}
                eventMap = null
            }
            typeNum = 0
            lastChild = 0
        }

        /*
         ================
         idTypeInfo::IsType

         Checks if the object's class is a subclass of the class defined by the 
         passed in idTypeInfo.
         ================
         */
        fun IsType(type: idTypeInfo): Boolean {
            return typeNum >= type.typeNum && typeNum <= type.lastChild
        }

//        // @Deprecated — prefer IsType(idTypeInfo) after migration
//        fun IsType(type: Class<*>?): Boolean {
//            if (type == null) return false
//            val targetName = type.simpleName
//            // Direct match
//            if (classname == targetName) return true
//            // Walk superclass chain using zuper pointers
//            var current = zuper
//            while (current != null) {
//                if (current.classname == targetName) return true
//                current = current.zuper
//            }
//            // If zuper chain not populated, walk superclass names via typeRegistry
//            if (zuper == null) {
//                var superName: String? = this.superclass
//                while (!superName.isNullOrEmpty()) {
//                    if (superName == targetName) return true
//                    val superType = typeRegistry[superName]
//                    superName = superType?.superclass
//                }
//            }
//            return false
//        }

        /*
         ================
         idTypeInfo::RespondsTo
         ================
         */
        fun RespondsTo(ev: idEventDef): Boolean {
            assert(Event.initialized)
            // we don't respond to this event
            return null != eventMap!![ev.GetEventNum()]
        }

        /*
         ================
         idTypeInfo::idClassType()

         Constructor for class.  Should only be called from CLASS_DECLARATION macro.
         Handles linking class definition into class hierarchy.  This should only happen
         at startup as idTypeInfos are statically defined.  Since static variables can be
         initialized in any order, the constructor must handle the case that subclasses
         are initialized before superclasses.
         ================
         */
        init {
            var type: idTypeInfo?
            this.classname = classname
            this.superclass = superclass
            this.eventCallbacks = eventCallbacks
            eventMap = null
            zuper = idClass.GetClass(superclass)
            freeEventMap = false
            typeNum = 0
            lastChild = 0

            // Check if any subclasses were initialized before their superclass
            type = typelist
            while (type != null) {
                if (type.zuper == null && idStr.Cmp(type.superclass, this.classname) == 0
                    && idStr.Cmp(type.classname, "idClass") != 0
                ) {
                    type.zuper = this
                }
                type = type.next
            }

            // Also check in typeRegistry for subclasses that were registered before us
            for (registered in typeRegistry.values) {
                if (registered.zuper == null && idStr.Cmp(registered.superclass, this.classname) == 0
                    && idStr.Cmp(registered.classname, "idClass") != 0
                ) {
                    registered.zuper = this
                }
            }

            // Sorted insert into the linked list (used for INIT() ordering)
            var prev: idTypeInfo? = null
            var current = typelist
            while (current != null && idStr.Cmp(classname, current.classname) >= 0) {
                assert(idStr.Cmp(classname, current.classname) != 0) { "Duplicate class registration: $classname" }
                prev = current
                current = current.next
            }
            next = current
            if (prev == null) {
                typelist = this
            } else {
                prev.next = this
            }

            // Register into the HashMap for O(1) lookup
            typeRegistry[classname] = this
        }
    }
}

/*
 ================
 registerAllTypes

 Touches every companion-level idTypeInfo to trigger lazy initialization.
 Must be called before idClass.INIT(). Replaces the C++ static-initialization
 order that was handled by CLASS_DECLARATION macros.
 ================
 */
fun registerAllTypes() {
    // idClass (base)
    idClass.Type

    // Entity
    idEntity.Type
    idAnimatedEntity.Type

    // Actor
    idActor.Type

    // AFEntity
    idMultiModelAF.Type
    idChain.Type
    idAFAttachment.Type
    idAFEntity_Base.Type
    idAFEntity_Gibbable.Type
    idAFEntity_Generic.Type
    idAFEntity_WithAttachedHead.Type
    idAFEntity_Vehicle.Type
    idAFEntity_VehicleSimple.Type
    idAFEntity_VehicleFourWheels.Type
    idAFEntity_VehicleSixWheels.Type
    idAFEntity_SteamPipe.Type
    idAFEntity_ClawFourFingers.Type

    // AI
    idAI.Type
    idCombatNode.Type
    AI_Vagary.idAI_Vagary.Type

    // Animation
    idTestModel.Type

    // BrittleFracture
    idBrittleFracture.Type

    // Camera
    idCamera.Type
    idCameraView.Type
    idCameraAnim.Type

    // FX
    idEntityFx.Type
    idTeleporter.Type

    // GameEdit
    idCursor3D.Type

    // Item
    idItem.Type
    idItemPowerup.Type
    idObjective.Type
    idVideoCDItem.Type
    idPDAItem.Type
    idMoveableItem.Type
    idMoveablePDAItem.Type
    idItemRemover.Type
    idObjectiveComplete.Type

    // Light
    idLight.Type

    // Misc
    idSpawnableEntity.Type
    idPlayerStart.Type
    idActivator.Type
    idPathCorner.Type
    idDamagable.Type
    idExplodable.Type
    idSpring.Type
    idForceField.Type
    idAnimated.Type
    idStaticEntity.Type
    idFuncEmitter.Type
    idFuncSmoke.Type
    idFuncSplat.Type
    idTextEntity.Type
    idLocationEntity.Type
    idLocationSeparatorEntity.Type
    idVacuumSeparatorEntity.Type
    idVacuumEntity.Type
    idBeam.Type
    idLiquid.Type
    idShaking.Type
    idEarthQuake.Type
    idFuncPortal.Type
    idFuncAASPortal.Type
    idFuncAASObstacle.Type
    idFuncRadioChatter.Type
    idPhantomObjects.Type

    // Moveable
    idMoveable.Type
    idBarrel.Type
    idExplodingBarrel.Type

    // Mover
    idMover.Type
    idSplinePath.Type
    idElevator.Type
    idMover_Binary.Type
    idDoor.Type
    idPlat.Type
    idMover_Periodic.Type
    idRotater.Type
    idBobber.Type
    idPendulum.Type
    idRiser.Type

    // Player
    idPlayer.Type

    // Projectile
    idProjectile.Type
    idGuidedProjectile.Type
    idSoulCubeMissile.Type
    idBFGProjectile.Type
    idDebris.Type

    // SecurityCamera
    idSecurityCamera.Type

    // Sound
    idSound.Type

    // Target
    idTarget.Type
    idTarget_Remove.Type
    idTarget_Show.Type
    idTarget_Damage.Type
    idTarget_SessionCommand.Type
    idTarget_EndLevel.Type
    idTarget_WaitForButton.Type
    idTarget_SetGlobalShaderTime.Type
    idTarget_SetShaderParm.Type
    idTarget_SetShaderTime.Type
    idTarget_FadeEntity.Type
    idTarget_LightFadeIn.Type
    idTarget_LightFadeOut.Type
    idTarget_Give.Type
    idTarget_GiveEmail.Type
    idTarget_SetModel.Type
    idTarget_SetInfluence.Type
    idTarget_SetKeyVal.Type
    idTarget_SetFov.Type
    idTarget_SetPrimaryObjective.Type
    idTarget_LockDoor.Type
    idTarget_CallObjectFunction.Type
    idTarget_EnableLevelWeapons.Type
    idTarget_Tip.Type
    idTarget_GiveSecurity.Type
    idTarget_RemoveWeapons.Type
    idTarget_LevelTrigger.Type
    idTarget_EnableStamina.Type
    idTarget_FadeSoundClass.Type

    // Trigger
    idTrigger.Type
    idTrigger_Multi.Type
    idTrigger_EntityName.Type
    idTrigger_Timer.Type
    idTrigger_Count.Type
    idTrigger_Hurt.Type
    idTrigger_Fade.Type
    idTrigger_Touch.Type

    // Weapon
    idWeapon.Type

    // WorldSpawn
    idWorldspawn.Type

    // Script
    idThread.Type

    // Physics
    idPhysics.Type
    idForce.Type
    idForce_Constant.Type
    idForce_Drag.Type
    idForce_Field.Type
    idForce_Spring.Type
    idPhysics_Base.Type
    idPhysics_Static.Type
    idPhysics_StaticMulti.Type
    idPhysics_Actor.Type
    idPhysics_Monster.Type
    idPhysics_Player.Type
    idPhysics_Parametric.Type
    idPhysics_RigidBody.Type
    idPhysics_AF.Type
    idPhysics_RigidBody.Type
    idPhysics_Parametric.Type
}
package neo.Game

import neo.Game.GameSys.Class.*
import neo.Game.GameSys.EV_Remove
import neo.Game.GameSys.Event.idEventDef
import neo.Game.GameSys.SaveGame.idRestoreGame
import neo.Game.GameSys.SaveGame.idSaveGame
import neo.Game.Game_local.idGameLocal
import neo.Game.Physics.Clip.idClipModel
import neo.Game.Player.idPlayer
import neo.Game.Script.Script_Program.function_t
import neo.Game.Script.Script_Thread.idThread
import neo.Renderer.Material
import neo.Renderer.Model
import neo.cm.trace_s
import neo.idlib.BV.idBounds
import neo.idlib.Text.Str
import neo.idlib.Text.Str.idStr
import neo.idlib.colorGreen
import neo.idlib.colorOrange
import neo.idlib.colorWhite
import neo.idlib.colorYellow
import neo.idlib.math.*

val EV_Disable: idEventDef = idEventDef("disable", null)
val EV_Enable: idEventDef = idEventDef("enable", null)

//
val EV_Timer: idEventDef = idEventDef("<timer>", null)

//
val EV_TriggerAction: idEventDef = idEventDef("<triggerAction>", "e")


object Trigger {

    /*
     ===============================================================================

     Trigger base.

     ===============================================================================
     */
    open class idTrigger     //
    //
        : idEntity() {
        companion object {
            // CLASS_PROTOTYPE( idTrigger );
            private val eventCallbacks: MutableMap<idEventDef, eventCallback_t<*>> = HashMap()
            fun DrawDebugInfo() {
                val axis = Game_local.gameLocal.GetLocalPlayer()!!.viewAngles.ToMat3()
                val up = idVec3(axis[2].times(5.0f))
                val viewTextBounds = idBounds(Game_local.gameLocal.GetLocalPlayer()!!.GetPhysics().GetOrigin())
                val viewBounds = idBounds(Game_local.gameLocal.GetLocalPlayer()!!.GetPhysics().GetOrigin())
                val box = idBounds(idVec3(-4.0f, -4.0f, -4.0f), idVec3(4.0f, 4.0f, 4.0f))
                var ent: idEntity?
                var target: idEntity?
                var i: Int
                var show: Boolean
                var func: function_t?
                viewTextBounds.ExpandSelf(128.0f)
                viewBounds.ExpandSelf(512.0f)
                ent = Game_local.gameLocal.spawnedEntities.Next()
                while (ent != null) {
                    if (ent.GetPhysics()
                            .GetContents() and (Material.CONTENTS_TRIGGER or Material.CONTENTS_FLASHLIGHT_TRIGGER) != 0
                    ) {
                        show = viewBounds.IntersectsBounds(ent.GetPhysics().GetAbsBounds())
                        if (!show) {
                            i = 0
                            while (i < ent.targets.Num()) {
                                target = ent.targets[i].GetEntity()
                                if (target != null && viewBounds.IntersectsBounds(target.GetPhysics().GetAbsBounds())) {
                                    show = true
                                    break
                                }
                                i++
                            }
                        }
                        if (!show) {
                            ent = ent.spawnNode.Next()
                            continue
                        }
                        Game_local.gameRenderWorld!!.DebugBounds(
                            colorOrange,
                            ent.GetPhysics().GetAbsBounds()
                        )
                        if (viewTextBounds.IntersectsBounds(ent.GetPhysics().GetAbsBounds())) {
                            Game_local.gameRenderWorld!!.DrawText(
                                ent.name.toString(),
                                ent.GetPhysics().GetAbsBounds().GetCenter(),
                                0.1f,
                                colorWhite,
                                axis,
                                1
                            )
                            Game_local.gameRenderWorld!!.DrawText(
                                ent.GetEntityDefName(),
                                ent.GetPhysics().GetAbsBounds().GetCenter().plus(up),
                                0.1f,
                                colorWhite,
                                axis,
                                1
                            )
                            func = if (ent is idTrigger) {
                                ent.GetScriptFunction()
                            } else {
                                null
                            }
                            if (func != null) {
                                Game_local.gameRenderWorld!!.DrawText(
                                    Str.va("call script '%s'", func.Name()),
                                    ent.GetPhysics().GetAbsBounds().GetCenter().minus(up),
                                    0.1f,
                                    colorWhite,
                                    axis,
                                    1
                                )
                            }
                        }
                        i = 0
                        while (i < ent.targets.Num()) {
                            target = ent.targets[i].GetEntity()
                            if (target != null) {
                                Game_local.gameRenderWorld!!.DebugArrow(
                                    colorYellow,
                                    ent.GetPhysics().GetAbsBounds().GetCenter(),
                                    target.GetPhysics().GetOrigin(),
                                    10,
                                    0
                                )
                                Game_local.gameRenderWorld!!.DebugBounds(
                                    colorGreen,
                                    box,
                                    target.GetPhysics().GetOrigin()
                                )
                                if (viewTextBounds.IntersectsBounds(target.GetPhysics().GetAbsBounds())) {
                                    Game_local.gameRenderWorld!!.DrawText(
                                        target.name.toString(),
                                        target.GetPhysics().GetAbsBounds().GetCenter(),
                                        0.1f,
                                        colorWhite,
                                        axis,
                                        1
                                    )
                                }
                            }
                            i++
                        }
                    }
                    ent = ent.spawnNode.Next()
                }
            }

            fun getEventCallBacks(): MutableMap<idEventDef, eventCallback_t<*>> {
                return eventCallbacks
            }

            init {
                eventCallbacks.putAll(idEntity.getEventCallBacks())
                eventCallbacks[EV_Enable] =
                    eventCallback_t0 { obj: idTrigger -> obj.Event_Enable() }
                eventCallbacks[EV_Disable] =
                    eventCallback_t0 { obj: idTrigger -> obj.Event_Disable() }
            }
        }

        protected var scriptFunction: function_t? = null
        override fun Spawn() {
            super.Spawn()
            GetPhysics().SetContents(Material.CONTENTS_TRIGGER)
            val funcname = spawnArgs.GetString("call", "")!!
            if (funcname.length != 0) {
                scriptFunction = Game_local.gameLocal.program.FindFunction(funcname)
                if (scriptFunction == null) {
                    Game_local.gameLocal.Warning(
                        "trigger '%s' at (%s) calls unknown function '%s'",
                        name,
                        GetPhysics().GetOrigin().ToString(0),
                        funcname
                    )
                }
            } else {
                scriptFunction = null
            }
        }

        fun GetScriptFunction(): function_t? {
            return scriptFunction
        }

        override fun Save(savefile: idSaveGame) {
            if (scriptFunction != null) {
                savefile.WriteString(scriptFunction!!.Name())
            } else {
                savefile.WriteString("")
            }
        }

        override fun Restore(savefile: idRestoreGame) {
            val funcname = idStr()
            savefile.ReadString(funcname)
            if (!funcname.IsEmpty()) {
                scriptFunction = Game_local.gameLocal.program.FindFunction(funcname.toString())
                if (scriptFunction == null) {
                    Game_local.gameLocal.Warning(
                        "idTrigger_Multi '%s' at (%s) calls unknown function '%s'",
                        name,
                        GetPhysics().GetOrigin().ToString(0),
                        funcname.toString()
                    )
                }
            } else {
                scriptFunction = null
            }
        }

        open fun Enable() {
            GetPhysics().SetContents(Material.CONTENTS_TRIGGER)
            GetPhysics().EnableClip()
        }

        open fun Disable() {
            // we may be relinked if we're bound to another object, so clear the contents as well
            GetPhysics().SetContents(0)
            GetPhysics().DisableClip()
        }

        protected fun CallScript() {
            val thread: idThread
            if (scriptFunction != null) {
                thread = idThread(scriptFunction!!)
                thread.DelayedStart(0)
            }
        }

        protected fun Event_Enable() {
            Enable()
        }

        protected fun Event_Disable() {
            Disable()
        }

        fun idEntity_Think() {
            super.Think()
        }

        override fun CreateInstance(): idClass {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        override fun oSet(oGet: idClass?) {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        override fun getEventCallBack(event: idEventDef): eventCallback_t<*>? {
            return eventCallbacks[event]
        }
    }

    /*
     ===============================================================================

     Trigger which can be activated multiple times.

     ===============================================================================
     */
    class idTrigger_Multi     //
    //
        : idTrigger() {
        companion object {
            // CLASS_PROTOTYPE( idTrigger_Multi );
            private val eventCallbacks: MutableMap<idEventDef, eventCallback_t<*>> = HashMap()
            fun getEventCallBacks(): MutableMap<idEventDef, eventCallback_t<*>> {
                return eventCallbacks
            }

            init {
                eventCallbacks.putAll(idTrigger.getEventCallBacks())
                eventCallbacks[EV_Touch] =
                    eventCallback_t2 { obj: idTrigger_Multi, _other: idEventArg<*>?, trace: idEventArg<*>? ->
                        obj.Event_Touch(
                            _other as idEventArg<idEntity?>,
                            trace as idEventArg<trace_s>
                        )
                    }
                eventCallbacks[EV_Activate] =
                    eventCallback_t1 { obj: idTrigger_Multi, _activator: idEventArg<*>? ->
                        obj.Event_Trigger(_activator as idEventArg<idEntity?>)
                    }
                eventCallbacks[EV_TriggerAction] =
                    eventCallback_t1 { obj: idTrigger_Multi, activator: idEventArg<*>? ->
                        obj.Event_TriggerAction(activator as idEventArg<idEntity?>)
                    }
            }
        }

        private var delay = 0.0f
        private var nextTriggerTime = 0
        private var random = 0.0f
        private var random_delay = 0.0f
        private var removeItem = 0
        private val requires: idStr = idStr()
        private var touchClient = false
        private var touchOther = false
        private var triggerFirst = false
        private var triggerWithSelf = false
        private var wait = 0.0f

        /*
         ================
         idTrigger_Multi::Spawn

         "wait" : Seconds between triggerings, 0.5f default, -1 = one time only.
         "call" : Script function to call when triggered
         "random"	wait variance, default is 0
         Variable sized repeatable trigger.  Must be targeted at one or more entities.
         so, the basic time between firing is a random time between
         (wait - random) and (wait + random)
         ================
         */
        override fun Spawn() {
            super.Spawn()
            wait = spawnArgs.GetFloat("wait", "0.5f")
            random = spawnArgs.GetFloat("random", "0")
            delay = spawnArgs.GetFloat("delay", "0")
            random_delay = spawnArgs.GetFloat("random_delay", "0")
            if (random != 0.0f && random >= wait && wait >= 0) {
                random = wait - 1
                Game_local.gameLocal.Warning(
                    "idTrigger_Multi '%s' at (%s) has random >= wait",
                    name,
                    GetPhysics().GetOrigin().ToString(0)
                )
            }
            if (random_delay != 0.0f && random_delay >= delay && delay >= 0) {
                random_delay = delay - 1
                Game_local.gameLocal.Warning(
                    "idTrigger_Multi '%s' at (%s) has random_delay >= delay",
                    name,
                    GetPhysics().GetOrigin().ToString(0)
                )
            }
            spawnArgs.GetString("requires", "", requires)
            removeItem = spawnArgs.GetInt("removeItem", "0")
            triggerFirst = spawnArgs.GetBool("triggerFirst", "0")
            triggerWithSelf = spawnArgs.GetBool("triggerWithSelf", "0")
            if (spawnArgs.GetBool("anyTouch")) {
                touchClient = true
                touchOther = true
            } else if (spawnArgs.GetBool("noTouch")) {
                touchClient = false
                touchOther = false
            } else if (spawnArgs.GetBool("noClient")) {
                touchClient = false
                touchOther = true
            } else {
                touchClient = true
                touchOther = false
            }
            nextTriggerTime = 0
            if (spawnArgs.GetBool("flashlight_trigger")) {
                GetPhysics().SetContents(Material.CONTENTS_FLASHLIGHT_TRIGGER)
            } else {
                GetPhysics().SetContents(Material.CONTENTS_TRIGGER)
            }
        }

        override fun Save(savefile: idSaveGame) {
            savefile.WriteFloat(wait)
            savefile.WriteFloat(random)
            savefile.WriteFloat(delay)
            savefile.WriteFloat(random_delay)
            savefile.WriteInt(nextTriggerTime)
            savefile.WriteString(requires)
            savefile.WriteInt(removeItem)
            savefile.WriteBool(touchClient)
            savefile.WriteBool(touchOther)
            savefile.WriteBool(triggerFirst)
            savefile.WriteBool(triggerWithSelf)
        }

        override fun Restore(savefile: idRestoreGame) {
            wait = savefile.ReadFloat()
            random = savefile.ReadFloat()
            delay = savefile.ReadFloat()
            random_delay = savefile.ReadFloat()
            nextTriggerTime = savefile.ReadInt()
            savefile.ReadString(requires)
            removeItem = savefile.ReadInt()
            touchClient = savefile.ReadBool()
            touchOther = savefile.ReadBool()
            triggerFirst = savefile.ReadBool()
            triggerWithSelf = savefile.ReadBool()
        }

        private fun CheckFacing(activator: idEntity?): Boolean {
            if (spawnArgs.GetBool("facing")) {
                if (activator !is idPlayer) {
                    return true
                }
                val player = activator
                val dot = player.viewAngles.ToForward().times(GetPhysics().GetAxis()[0])
                val angle = RAD2DEG(idMath.ACos(dot))
                return angle <= spawnArgs.GetFloat("angleLimit", "30")
            }
            return true
        }

        private fun TriggerAction(activator: idEntity?) {
            ActivateTargets(if (triggerWithSelf) this else activator)
            CallScript()
            if (wait >= 0) {
                nextTriggerTime =
                    (Game_local.gameLocal.time + SEC2MS(wait + random * Game_local.gameLocal.random.CRandomFloat())).toInt()
            } else {
                // we can't just remove (this) here, because this is a touch function
                // called while looping through area links...
                nextTriggerTime = Game_local.gameLocal.time + 1
                PostEventMS(EV_Remove, 0)
            }
        }

        private fun Event_TriggerAction(activator: idEventArg<idEntity?>) {
            TriggerAction(activator.value)
        }

        /*
         ================
         idTrigger_Multi::Event_Trigger

         the trigger was just activated
         activated should be the entity that originated the activation sequence (ie. the original target)
         activator should be set to the activator so it can be held through a delay
         so wait for the delay time before firing
         ================
         */
        private fun Event_Trigger(_activator: idEventArg<idEntity?>) {
            val activator = _activator.value
            if (nextTriggerTime > Game_local.gameLocal.time) {
                // can't retrigger until the wait is over
                return
            }

            // see if this trigger requires an item
            if (!Game_local.gameLocal.RequirementMet(activator, requires, removeItem)) {
                return
            }
            if (!CheckFacing(activator)) {
                return
            }
            if (triggerFirst) {
                triggerFirst = false
                return
            }

            // don't allow it to trigger twice in a single frame
            nextTriggerTime = Game_local.gameLocal.time + 1
            if (delay > 0) {
                // don't allow it to trigger again until our delay has passed
                nextTriggerTime += SEC2MS(delay + random_delay * Game_local.gameLocal.random.CRandomFloat())
                    .toInt()
                PostEventSec(EV_TriggerAction, delay, _activator)
            } else {
                TriggerAction(activator)
            }
        }

        private fun Event_Touch(_other: idEventArg<idEntity?>, trace: idEventArg<trace_s>) {
            val other = _other.value
            if (triggerFirst) {
                return
            }
            val player = other is idPlayer
            if (player) {
                if (!touchClient) {
                    return
                }
                if ((other as idPlayer).spectating) {
                    return
                }
            } else if (!touchOther) {
                return
            }
            if (nextTriggerTime > Game_local.gameLocal.time) {
                // can't retrigger until the wait is over
                return
            }

            // see if this trigger requires an item
            if (!Game_local.gameLocal.RequirementMet(other, requires, removeItem)) {
                return
            }
            if (!CheckFacing(other)) {
                return
            }
            if (spawnArgs.GetBool("toggleTriggerFirst")) {
                triggerFirst = true
            }
            nextTriggerTime = Game_local.gameLocal.time + 1
            if (delay > 0) {
                // don't allow it to trigger again until our delay has passed
                nextTriggerTime += SEC2MS(delay + random_delay * Game_local.gameLocal.random.CRandomFloat())
                    .toInt()
                PostEventSec(EV_TriggerAction, delay, other)
            } else {
                TriggerAction(other)
            }
        }

        override fun oSet(oGet: idClass?) {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        override fun getEventCallBack(event: idEventDef): eventCallback_t<*>? {
            return eventCallbacks[event]
        }
    }

    /*
     ===============================================================================

     Trigger which can only be activated by an entity with a specific name.

     ===============================================================================
     */
    class idTrigger_EntityName     //
    //
        : idTrigger() {
        companion object {
            //CLASS_PROTOTYPE(idTrigger_EntityName );
            private val eventCallbacks: MutableMap<idEventDef, eventCallback_t<*>> = HashMap()
            fun getEventCallBacks(): MutableMap<idEventDef, eventCallback_t<*>> {
                return eventCallbacks
            }

            init {
                eventCallbacks.putAll(idTrigger.getEventCallBacks())
                eventCallbacks[EV_Touch] =
                    eventCallback_t2<idTrigger_EntityName> { obj: idTrigger_EntityName, _other: idEventArg<*>?, trace: idEventArg<*>? ->
                        obj.Event_Touch(_other as idEventArg<idEntity>, trace as idEventArg<trace_s>)
                    }
                eventCallbacks[EV_Activate] =
                    eventCallback_t1<idTrigger_EntityName> { obj: idTrigger_EntityName, _activator: idEventArg<*>? ->
                        obj.Event_Trigger(_activator as idEventArg<idEntity>)
                    }
                eventCallbacks[EV_TriggerAction] =
                    eventCallback_t1<idTrigger_EntityName> { obj: idTrigger_EntityName, activator: idEventArg<*>? ->
                        obj.Event_TriggerAction(activator as idEventArg<idEntity?>)
                    }
            }
        }

        private var delay = 0.0f
        private val entityName: idStr = idStr()
        private var nextTriggerTime = 0
        private var random = 0.0f
        private var random_delay = 0.0f
        private var triggerFirst = false
        private var wait = 0.0f
        override fun Save(savefile: idSaveGame) {
            savefile.WriteFloat(wait)
            savefile.WriteFloat(random)
            savefile.WriteFloat(delay)
            savefile.WriteFloat(random_delay)
            savefile.WriteInt(nextTriggerTime)
            savefile.WriteBool(triggerFirst)
            savefile.WriteString(entityName)
        }

        override fun Restore(savefile: idRestoreGame) {
            wait = savefile.ReadFloat()
            random = savefile.ReadFloat()
            delay = savefile.ReadFloat()
            random_delay = savefile.ReadFloat()
            nextTriggerTime = savefile.ReadInt()
            triggerFirst = savefile.ReadBool()
            savefile.ReadString(entityName)
        }

        override fun Spawn() {
            super.Spawn()
            wait = spawnArgs.GetFloat("wait", "0.5f")
            random = spawnArgs.GetFloat("random", "0")
            delay = spawnArgs.GetFloat("delay", "0")
            random_delay = spawnArgs.GetFloat("random_delay", "0")
            if (random != 0.0f && random >= wait && wait >= 0) {
                random = wait - 1
                Game_local.gameLocal.Warning(
                    "idTrigger_EntityName '%s' at (%s) has random >= wait",
                    name,
                    GetPhysics().GetOrigin().ToString(0)
                )
            }
            if (random_delay != 0.0f && random_delay >= delay && delay >= 0) {
                random_delay = delay - 1
                Game_local.gameLocal.Warning(
                    "idTrigger_EntityName '%s' at (%s) has random_delay >= delay",
                    name,
                    GetPhysics().GetOrigin().ToString(0)
                )
            }
            triggerFirst = spawnArgs.GetBool("triggerFirst", "0")
            entityName.set(spawnArgs.GetString("entityname"))
            if (entityName.Length() == 0) {
                idGameLocal.Error(
                    "idTrigger_EntityName '%s' at (%s) doesn't have 'entityname' key specified",
                    name,
                    GetPhysics().GetOrigin().ToString(0)
                )
            }
            nextTriggerTime = 0
            if (!spawnArgs.GetBool("noTouch")) {
                GetPhysics().SetContents(Material.CONTENTS_TRIGGER)
            }
        }

        private fun TriggerAction(activator: idEntity?) {
            ActivateTargets(activator)
            CallScript()
            if (wait >= 0) {
                nextTriggerTime =
                    (Game_local.gameLocal.time + SEC2MS(wait + random * Game_local.gameLocal.random.CRandomFloat())).toInt()
            } else {
                // we can't just remove (this) here, because this is a touch function
                // called while looping through area links...
                nextTriggerTime = Game_local.gameLocal.time + 1
                PostEventMS(EV_Remove, 0)
            }
        }

        private fun Event_TriggerAction(activator: idEventArg<idEntity?>) {
            TriggerAction(activator.value)
        }

        /*
         ================
         obj.Event_Trigger

         the trigger was just activated
         activated should be the entity that originated the activation sequence (ie. the original target)
         activator should be set to the activator so it can be held through a delay
         so wait for the delay time before firing
         ================
         */
        private fun Event_Trigger(_activator: idEventArg<idEntity>) {
            val activator = _activator.value
            if (nextTriggerTime > Game_local.gameLocal.time) {
                // can't retrigger until the wait is over
                return
            }
            if (activator.name != entityName) {
                return
            }
            if (triggerFirst) {
                triggerFirst = false
                return
            }

            // don't allow it to trigger twice in a single frame
            nextTriggerTime = Game_local.gameLocal.time + 1
            if (delay > 0) {
                // don't allow it to trigger again until our delay has passed
                nextTriggerTime += SEC2MS(delay + random_delay * Game_local.gameLocal.random.CRandomFloat())
                    .toInt()
                PostEventSec(EV_TriggerAction, delay, activator)
            } else {
                TriggerAction(activator)
            }
        }

        private fun Event_Touch(_other: idEventArg<idEntity>, trace: idEventArg<trace_s>) {
            val other = _other.value
            if (triggerFirst) {
                return
            }
            if (nextTriggerTime > Game_local.gameLocal.time) {
                // can't retrigger until the wait is over
                return
            }
            if (other.name != entityName) {
                return
            }
            nextTriggerTime = Game_local.gameLocal.time + 1
            if (delay > 0) {
                // don't allow it to trigger again until our delay has passed
                nextTriggerTime += SEC2MS(delay + random_delay * Game_local.gameLocal.random.CRandomFloat())
                    .toInt()
                PostEventSec(EV_TriggerAction, delay, other)
            } else {
                TriggerAction(other)
            }
        }

        override fun oSet(oGet: idClass?) {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        override fun getEventCallBack(event: idEventDef): eventCallback_t<*>? {
            return eventCallbacks[event]
        }
    }

    /*
     ===============================================================================

     Trigger which repeatedly fires targets.

     ===============================================================================
     */
    class idTrigger_Timer     //
    //
        : idTrigger() {
        companion object {
            //	CLASS_PROTOTYPE(idTrigger_Timer );
            private val eventCallbacks: MutableMap<idEventDef, eventCallback_t<*>> = HashMap()
            fun getEventCallBacks(): MutableMap<idEventDef, eventCallback_t<*>> {
                return eventCallbacks
            }

            init {
                eventCallbacks.putAll(idTrigger.getEventCallBacks())
                eventCallbacks[EV_Timer] =
                    eventCallback_t0<idTrigger_Timer> { obj: idTrigger_Timer -> obj.Event_Timer() }
                eventCallbacks[EV_Activate] =
                    eventCallback_t1<idTrigger_Timer> { obj: idTrigger_Timer, _activator: idEventArg<*>? ->
                        obj.Event_Use(_activator as idEventArg<idEntity>)
                    }
            }
        }

        private var delay = 0.0f
        private val offName: idStr = idStr()
        private var on = false
        private val onName: idStr = idStr()
        private var random = 0.0f
        private var wait = 0.0f
        override fun Save(savefile: idSaveGame) {
            savefile.WriteFloat(random)
            savefile.WriteFloat(wait)
            savefile.WriteBool(on)
            savefile.WriteFloat(delay)
            savefile.WriteString(onName)
            savefile.WriteString(offName)
        }

        override fun Restore(savefile: idRestoreGame) {
            random = savefile.ReadFloat()
            wait = savefile.ReadFloat()
            on = savefile.ReadBool()
            delay = savefile.ReadFloat()
            savefile.ReadString(onName)
            savefile.ReadString(offName)
        }

        /*
         ================
         idTrigger_Timer::Spawn

         Repeatedly fires its targets.
         Can be turned on or off by using.
         ================
         */
        override fun Spawn() {
            super.Spawn()
            random = spawnArgs.GetFloat("random", "1")
            wait = spawnArgs.GetFloat("wait", "1")
            on = spawnArgs.GetBool("start_on", "0")
            delay = spawnArgs.GetFloat("delay", "0")
            onName.set(spawnArgs.GetString("onName"))
            offName.set(spawnArgs.GetString("offName"))
            if (random >= wait && wait >= 0) {
                random = wait - 0.001f
                Game_local.gameLocal.Warning(
                    "idTrigger_Timer '%s' at (%s) has random >= wait",
                    name,
                    GetPhysics().GetOrigin().ToString(0)
                )
            }
            if (on) {
                PostEventSec(EV_Timer, delay)
            }
        }

        override fun Enable() {
            // if off, turn it on
            if (!on) {
                on = true
                PostEventSec(EV_Timer, delay)
            }
        }

        override fun Disable() {
            // if on, turn it off
            if (on) {
                on = false
                CancelEvents(EV_Timer)
            }
        }

        private fun Event_Timer() {
            ActivateTargets(this)

            // set time before next firing
            if (wait >= 0.0f) {
                PostEventSec(EV_Timer, wait + Game_local.gameLocal.random.CRandomFloat() * random)
            }
        }

        private fun Event_Use(_activator: idEventArg<idEntity>) {
            val activator = _activator.value
            // if on, turn it off
            if (on) {
                if (offName.Length() != 0 && offName.Icmp(activator!!.GetName()) != 0) {
                    return
                }
                on = false
                CancelEvents(EV_Timer)
            } else {
                // turn it on
                if (onName.Length() != 0 && onName.Icmp(activator.GetName()) != 0) {
                    return
                }
                on = true
                PostEventSec(EV_Timer, delay)
            }
        }

        override fun oSet(oGet: idClass?) {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        override fun getEventCallBack(event: idEventDef): eventCallback_t<*>? {
            return eventCallbacks[event]
        }
    }

    /*
     ===============================================================================

     Trigger which fires targets after being activated a specific number of times.

     ===============================================================================
     */
    class idTrigger_Count     //
    //
        : idTrigger() {
        companion object {
            //	CLASS_PROTOTYPE(idTrigger_Count );
            private val eventCallbacks: MutableMap<idEventDef, eventCallback_t<*>> = HashMap()
            fun getEventCallBacks(): MutableMap<idEventDef, eventCallback_t<*>> {
                return eventCallbacks
            }

            init {
                eventCallbacks.putAll(idTrigger.getEventCallBacks())
                eventCallbacks[EV_Activate] =
                    eventCallback_t1<idTrigger_Count> { obj: idTrigger_Count, activator: idEventArg<*>? ->
                        obj.Event_Trigger(activator as idEventArg<idEntity?>)
                    }
                eventCallbacks[EV_TriggerAction] =
                    eventCallback_t1<idTrigger_Count> { obj: idTrigger_Count, activator: idEventArg<*>? ->
                        obj.Event_TriggerAction(activator as idEventArg<idEntity?>)
                    }
            }
        }

        private var count = 0
        private var delay = 0.0f
        private var goal = 0
        override fun Save(savefile: idSaveGame) {
            savefile.WriteInt(goal)
            savefile.WriteInt(count)
            savefile.WriteFloat(delay)
        }

        override fun Restore(savefile: idRestoreGame) {
            goal = savefile.ReadInt()
            count = savefile.ReadInt()
            delay = savefile.ReadFloat()
        }

        override fun Spawn() {
            super.Spawn()
            goal = spawnArgs.GetInt("count", "1")
            delay = spawnArgs.GetFloat("delay", "0")
            count = 0
        }

        private fun Event_Trigger(activator: idEventArg<idEntity?>) {
            // goal of -1 means trigger has been exhausted
            if (goal >= 0) {
                count++
                if (count >= goal) {
                    if (spawnArgs.GetBool("repeat")) {
                        count = 0
                    } else {
                        goal = -1
                    }
                    PostEventSec(EV_TriggerAction, delay, activator.value)
                }
            }
        }

        private fun Event_TriggerAction(activator: idEventArg<idEntity?>) {
            ActivateTargets(activator.value)
            CallScript()
            if (goal == -1) {
                PostEventMS(EV_Remove, 0)
            }
        }

        override fun oSet(oGet: idClass?) {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        override fun getEventCallBack(event: idEventDef): eventCallback_t<*>? {
            return eventCallbacks[event]
        }
    }

    /*
     ===============================================================================

     Trigger which hurts touching entities.

     ===============================================================================
     */
    class idTrigger_Hurt     //
    //
        : idTrigger() {
        companion object {
            //	CLASS_PROTOTYPE(idTrigger_Hurt );
            private val eventCallbacks: MutableMap<idEventDef, eventCallback_t<*>> = HashMap()
            fun getEventCallBacks(): MutableMap<idEventDef, eventCallback_t<*>> {
                return eventCallbacks
            }

            init {
                eventCallbacks.putAll(idTrigger.getEventCallBacks())
                eventCallbacks[EV_Touch] =
                    eventCallback_t2<idTrigger_Hurt> { obj: idTrigger_Hurt, _other: idEventArg<*>?, trace: idEventArg<*>? ->
                        obj.Event_Touch(
                            _other as idEventArg<idEntity>,
                            trace as idEventArg<trace_s>
                        )
                    }
                eventCallbacks[EV_Activate] =
                    eventCallback_t1<idTrigger_Hurt> { obj: idTrigger_Hurt, activator: idEventArg<*>? ->
                        obj.Event_Toggle(activator as idEventArg<idEntity?>)
                    }
            }
        }

        private var delay = 0.0f
        private var nextTime = 0
        private var on = false
        override fun Save(savefile: idSaveGame) {
            savefile.WriteBool(on)
            savefile.WriteFloat(delay)
            savefile.WriteInt(nextTime)
        }

        override fun Restore(savefile: idRestoreGame) {
            on = savefile.ReadBool()
            delay = savefile.ReadFloat()
            nextTime = savefile.ReadInt()
        }

        /*
         ================
         idTrigger_Hurt::Spawn

         Damages activator
         Can be turned on or off by using.
         ================
         */
        override fun Spawn() {
            super.Spawn()
            on = spawnArgs.GetBool("on", "1")
            delay = spawnArgs.GetFloat("delay", "1.0f")
            nextTime = Game_local.gameLocal.time
            Enable()
        }

        private fun Event_Touch(_other: idEventArg<idEntity>, trace: idEventArg<trace_s>) {
            val other = _other.value
            val damage: String
            if (on && Game_local.gameLocal.time >= nextTime) {
                damage = spawnArgs.GetString("def_damage", "damage_painTrigger")!!
                other.Damage(null, null, getVec3Origin(), damage, 1.0f, Model.INVALID_JOINT)
                ActivateTargets(other)
                CallScript()
                nextTime = (Game_local.gameLocal.time + SEC2MS(delay)).toInt()
            }
        }

        private fun Event_Toggle(activator: idEventArg<idEntity?>) {
            on = !on
        }

        override fun oSet(oGet: idClass?) {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        override fun getEventCallBack(event: idEventDef): eventCallback_t<*>? {
            return eventCallbacks[event]
        }
    }

    /*
     ===============================================================================

     Trigger which fades the player view.

     ===============================================================================
     */
    class idTrigger_Fade : idTrigger() {
        companion object {
            // CLASS_PROTOTYPE( idTrigger_Fade );
            private val eventCallbacks: MutableMap<idEventDef, eventCallback_t<*>> = HashMap()
            fun getEventCallBacks(): MutableMap<idEventDef, eventCallback_t<*>> {
                return eventCallbacks
            }

            init {
                eventCallbacks.putAll(idTrigger.getEventCallBacks())
                eventCallbacks[EV_Activate] =
                    eventCallback_t1<idTrigger_Fade> { obj: idTrigger_Fade, activator: idEventArg<*>? ->
                        obj.Event_Trigger(activator as idEventArg<idEntity?>)
                    }
            }
        }

        private fun Event_Trigger(activator: idEventArg<idEntity?>) {
            val fadeColor: idVec4
            val fadeTime: Int
            val player: idPlayer?
            player = Game_local.gameLocal.GetLocalPlayer()
            if (player != null) {
                fadeColor = spawnArgs.GetVec4("fadeColor", "0, 0, 0, 1")
                fadeTime = SEC2MS(spawnArgs.GetFloat("fadeTime", "0.5f")).toInt()
                player.playerView.Fade(fadeColor, fadeTime)
                PostEventMS(EV_ActivateTargets, fadeTime.toFloat(), activator.value)
            }
        }

        override fun oSet(oGet: idClass?) {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        override fun getEventCallBack(event: idEventDef): eventCallback_t<*>? {
            return eventCallbacks[event]
        }
    }

    /*
     ===============================================================================

     Trigger which continuously tests whether other entities are touching it.

     ===============================================================================
     */
    class idTrigger_Touch     //
    //
        : idTrigger() {
        companion object {
            // CLASS_PROTOTYPE( idTrigger_Touch );
            private val eventCallbacks: MutableMap<idEventDef, eventCallback_t<*>> = HashMap()
            fun getEventCallBacks(): MutableMap<idEventDef, eventCallback_t<*>> {
                return eventCallbacks
            }

            init {
                eventCallbacks.putAll(idTrigger.getEventCallBacks())
                eventCallbacks[EV_Activate] =
                    eventCallback_t1<idTrigger_Touch> { obj: idTrigger_Touch, activator: idEventArg<*>? ->
                        obj.Event_Trigger(activator as idEventArg<idEntity?>)
                    }
            }
        }

        private var clipModel: idClipModel? = null
        override fun Spawn() {
            // get the clip model
            clipModel = idClipModel(GetPhysics().GetClipModel()!!)

            // remove the collision model from the physics object
            GetPhysics().SetClipModel(null, 1.0f)
            if (spawnArgs.GetBool("start_on")) {
                BecomeActive(TH_THINK)
            }
        }

        override fun Think() {
            if ((thinkFlags and TH_THINK) != 0) {
                TouchEntities()
            }
            idEntity_Think()
        }

        override fun Save(savefile: idSaveGame) {
            savefile.WriteClipModel(clipModel)
        }

        override fun Restore(savefile: idRestoreGame) {
            savefile.ReadClipModel(clipModel!!)
        }

        override fun Enable() {
            BecomeActive(TH_THINK)
        }

        override fun Disable() {
            BecomeInactive(TH_THINK)
        }

        fun TouchEntities() {
            val numClipModels: Int
            var i: Int
            val bounds = idBounds()
            var cm: idClipModel
            val clipModelList = arrayOfNulls<idClipModel>(Game_local.MAX_GENTITIES)
            if (clipModel == null || scriptFunction == null) {
                return
            }
            bounds.FromTransformedBounds(clipModel!!.GetBounds(), clipModel!!.GetOrigin(), clipModel!!.GetAxis())
            numClipModels =
                Game_local.gameLocal.clip.ClipModelsTouchingBounds(bounds, -1, clipModelList, Game_local.MAX_GENTITIES)
            i = 0
            while (i < numClipModels) {
                cm = clipModelList[i]!!
                if (!cm.IsTraceModel()) {
                    i++
                    continue
                }
                val entity = cm.GetEntity()
                if (null == entity) {
                    i++
                    continue
                }
                if (
                    Game_local.gameLocal.clip.ContentsModel(
                        cm.GetOrigin(), cm, cm.GetAxis(), -1,
                        clipModel!!.Handle(), clipModel!!.GetOrigin(), clipModel!!.GetAxis()
                    ) == 0
                ) {
                    i++
                    continue
                }
                ActivateTargets(entity)
                val thread = idThread()
                thread.CallFunction(entity, scriptFunction!!, false)
                thread.DelayedStart(0)
                i++
            }
        }

        private fun Event_Trigger(activator: idEventArg<idEntity?>) {
            if ((thinkFlags and TH_THINK) != 0) {
                BecomeInactive(TH_THINK)
            } else {
                BecomeActive(TH_THINK)
            }
        }

        override fun oSet(oGet: idClass?) {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        override fun getEventCallBack(event: idEventDef): eventCallback_t<*>? {
            return eventCallbacks[event]
        }
    }
}
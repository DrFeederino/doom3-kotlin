package neo.Game.Script

import neo.Game.*
import neo.Game.GameSys.Class.*
import neo.Game.GameSys.EV_Remove
import neo.Game.GameSys.Event.idEvent
import neo.Game.GameSys.Event.idEventDef
import neo.Game.GameSys.SaveGame.idRestoreGame
import neo.Game.GameSys.SaveGame.idSaveGame
import neo.Game.GameSys.SysCvar
import neo.Game.Game_local.Companion.gameLocal
import neo.Game.Game_local.idGameLocal
import neo.Game.Game_local.idGameLocal.Companion.Error
import neo.Game.Physics.Clip.CLIPMODEL_ID_TO_JOINT_HANDLE
import neo.Game.Player.idPlayer
import neo.Game.Script.Script_Interpreter.idInterpreter
import neo.Game.Script.Script_Program.function_t
import neo.Renderer.RenderWorld
import neo.TempDump.btoi
import neo.TempDump.etoi
import neo.cm.trace_s
import neo.framework.CVarSystem.cvarSystem
import neo.framework.CmdSystem.cmdExecution_t
import neo.framework.CmdSystem.cmdFunction_t
import neo.framework.CmdSystem.cmdSystem
import neo.framework.DeclManager
import neo.framework.UsercmdGen.USERCMD_HZ
import neo.idlib.BV.idBounds
import neo.idlib.CmdArgs
import neo.idlib.Dict_h.idDict
import neo.idlib.Text.Str.idStr
import neo.idlib.Text.Str.idStr.Companion.Cmpn
import neo.idlib.containers.CFloat
import neo.idlib.containers.List.idList
import neo.idlib.math.*
import neo.idlib.math.Matrix.idMat3
import neo.idlib.math.idMath.ACos
import neo.idlib.math.idMath.ASin
import neo.idlib.math.idMath.Cos
import neo.idlib.math.idMath.Sin
import neo.idlib.math.idMath.Sqrt


val EV_Thread_SetCallback = idEventDef("<script_setcallback>", null)
val EV_Thread_Wait = idEventDef("wait", "f")
val EV_Thread_WaitFrame = idEventDef("waitFrame")
val EV_Thread_AngToForward = idEventDef("angToForward", "v", 'v')
val EV_Thread_AngToRight = idEventDef("angToRight", "v", 'v')
val EV_Thread_AngToUp = idEventDef("angToUp", "v", 'v')
val EV_Thread_Assert = idEventDef("assert", "f")
val EV_Thread_ClearPersistantArgs = idEventDef("clearPersistantArgs")
val EV_Thread_ClearSignal = idEventDef("clearSignalThread", "de")
val EV_Thread_CopySpawnArgs = idEventDef("copySpawnArgs", "e")
val EV_Thread_Cosine = idEventDef("cos", "f", 'f')
val EV_Thread_DebugArrow = idEventDef("debugArrow", "vvvdf")
val EV_Thread_DebugBounds = idEventDef("debugBounds", "vvvf")
val EV_Thread_DebugCircle = idEventDef("debugCircle", "vvvfdf")
val EV_Thread_DebugLine = idEventDef("debugLine", "vvvf")
val EV_Thread_DrawText = idEventDef("drawText", "svfvdf")
val EV_Thread_Error = idEventDef("error", "s")
val EV_Thread_Execute = idEventDef("<execute>", null)
val EV_Thread_FadeIn = idEventDef("fadeIn", "vf")
val EV_Thread_FadeOut = idEventDef("fadeOut", "vf")
val EV_Thread_FadeTo = idEventDef("fadeTo", "vff")
val EV_Thread_FirstPerson = idEventDef("firstPerson", null)
val EV_Thread_GetCvar = idEventDef("getcvar", "s", 's')
val EV_Thread_GetEntity = idEventDef("getEntity", "s", 'e')
val EV_Thread_GetFrameTime = idEventDef("getFrameTime", null, 'f')
val EV_Thread_GetPersistantFloat = idEventDef("getPersistantFloat", "s", 'f')
val EV_Thread_GetPersistantString = idEventDef("getPersistantString", "s", 's')
val EV_Thread_GetPersistantVector = idEventDef("getPersistantVector", "s", 'v')
val EV_Thread_GetTicsPerSecond = idEventDef("getTicsPerSecond", null, 'f')
val EV_Thread_GetTime = idEventDef("getTime", null, 'f')
val EV_Thread_GetTraceBody = idEventDef("getTraceBody", null, 's')
val EV_Thread_GetTraceEndPos = idEventDef("getTraceEndPos", null, 'v')
val EV_Thread_GetTraceEntity = idEventDef("getTraceEntity", null, 'e')
val EV_Thread_GetTraceFraction = idEventDef("getTraceFraction", null, 'f')
val EV_Thread_GetTraceJoint = idEventDef("getTraceJoint", null, 's')
val EV_Thread_GetTraceNormal = idEventDef("getTraceNormal", null, 'v')
val EV_Thread_InfluenceActive = idEventDef("influenceActive", null, 'd')
val EV_Thread_IsClient = idEventDef("isClient", null, 'f')
val EV_Thread_IsMultiplayer = idEventDef("isMultiplayer", null, 'f')
val EV_Thread_KillThread = idEventDef("killthread", "s")
val EV_Thread_Normalize = idEventDef("vecNormalize", "v", 'v')
val EV_Thread_OnSignal = idEventDef("onSignal", "des")
val EV_Thread_Pause = idEventDef("pause", null)
val EV_Thread_Print = idEventDef("print", "s")
val EV_Thread_PrintLn = idEventDef("println", "s")
val EV_Thread_RadiusDamage = idEventDef("radiusDamage", "vEEEsf")
val EV_Thread_Random = idEventDef("random", "f", 'f')
val EV_Thread_RandomInt = idEventDef("randomInt", "d", 'd')  // D3XP
val EV_Thread_Say = idEventDef("say", "s")
val EV_Thread_SetCamera = idEventDef("setCamera", "e")
val EV_Thread_SetCvar = idEventDef("setcvar", "ss")
val EV_Thread_SetPersistantArg = idEventDef("setPersistantArg", "ss")
val EV_Thread_SetSpawnArg = idEventDef("setSpawnArg", "ss")
val EV_Thread_SetThreadName = idEventDef("threadname", "s")
val EV_Thread_Sine = idEventDef("sin", "f", 'f')
val EV_Thread_ArcSine = idEventDef("asin", "f", 'f')        // D3XP
val EV_Thread_ArcCosine = idEventDef("acos", "f", 'f')      // D3XP
val EV_Thread_Spawn = idEventDef("spawn", "s", 'e')
val EV_Thread_SpawnFloat = idEventDef("SpawnFloat", "sf", 'f')
val EV_Thread_SpawnString = idEventDef("SpawnString", "ss", 's')
val EV_Thread_SpawnVector = idEventDef("SpawnVector", "sv", 'v')
val EV_Thread_SquareRoot = idEventDef("sqrt", "f", 'f')
val EV_Thread_StartMusic = idEventDef("music", "s")
val EV_Thread_StrLeft = idEventDef("strLeft", "sd", 's')
val EV_Thread_StrLen = idEventDef("strLength", "s", 'd')
val EV_Thread_StrMid = idEventDef("strMid", "sdd", 's')
val EV_Thread_StrRight = idEventDef("strRight", "sd", 's')
val EV_Thread_StrSkip = idEventDef("strSkip", "sd", 's')
val EV_Thread_StrToFloat = idEventDef("strToFloat", "s", 'f')

//
// script callable events
val EV_Thread_TerminateThread = idEventDef("terminate", "d")
val EV_Thread_Trace = idEventDef("trace", "vvvvde", 'f')
val EV_Thread_TracePoint = idEventDef("tracePoint", "vvde", 'f')
val EV_Thread_Trigger = idEventDef("trigger", "e")
val EV_Thread_VecCrossProduct = idEventDef("CrossProduct", "vv", 'v')
val EV_Thread_VecDotProduct = idEventDef("DotProduct", "vv", 'f')
val EV_Thread_VecLength = idEventDef("vecLength", "v", 'f')
val EV_Thread_VecToAngles = idEventDef("VecToAngles", "v", 'v')
val EV_Thread_VecToOrthoBasisAngles = idEventDef("VecToOrthoBasisAngles", "v", 'v')  // D3XP
val EV_Thread_RotateVector = idEventDef("rotateVector", "vv", 'v')                   // D3XP
val EV_Thread_WaitFor = idEventDef("waitFor", "e")
val EV_Thread_WaitForThread = idEventDef("waitForThread", "d")
val EV_Thread_Warning = idEventDef("warning", "s")

object Script_Thread {


    class idThread : idClass {
        private var creationTime = 0
        private val interpreter = idInterpreter()

        //
        private var lastExecuteTime = 0

        //
        private var manualControl = false

        //
        private var spawnArgs: idDict? = null
        private val threadName = idStr()

        //
        private var threadNum = 0
        private var waitingFor = 0

        //
        private var waitingForThread: idThread? = null
        private var waitingUntil = 0

        constructor() {
            Init()
            SetThreadName(String.format("thread_%d", threadIndex))
            if (SysCvar.g_debugScript.GetBool()) {
                gameLocal.Printf(
                    "%d: create thread (%d) '%s'\n", gameLocal.time, threadNum, threadName
                )
            }
        }

        constructor(self: idEntity?, func: function_t) {
            assert(self != null)
            Init()
            SetThreadName(self!!.name.toString())
            interpreter.EnterObjectFunction(self, func, false)
            if (SysCvar.g_debugScript.GetBool()) {
                gameLocal.Printf(
                    "%d: create thread (%d) '%s'\n", gameLocal.time, threadNum, threadName
                )
            }
        }

        constructor(func: function_t) {
            assert(func != null)
            Init()
            SetThreadName(func.Name())
            interpreter.EnterFunction(func, false)
            if (SysCvar.g_debugScript.GetBool()) {
                gameLocal.Printf(
                    "%d: create thread (%d) '%s'\n", gameLocal.time, threadNum, threadName
                )
            }
        }

        constructor(source: idInterpreter, func: function_t, args: Int) {
            Init()
            interpreter.ThreadCall(source, func, args)
            if (SysCvar.g_debugScript.GetBool()) {
                gameLocal.Printf(
                    "%d: create thread (%d) '%s'\n", gameLocal.time, threadNum, threadName
                )
            }
        }

        constructor(source: idInterpreter, self: idEntity?, func: function_t, args: Int) {
            assert(self != null)
            Init()
            SetThreadName(self!!.name.toString())
            interpreter.ThreadCall(source, func, args)
            if (SysCvar.g_debugScript.GetBool()) {
                gameLocal.Printf(
                    "%d: create thread (%d) '%s'\n", gameLocal.time, threadNum, threadName
                )
            }
        }

        override fun Init() {
            // create a unique threadNum
            do {
                threadIndex++
                if (threadIndex == 0) {
                    threadIndex = 1
                }
            } while (GetThread(threadIndex) != null)
            threadNum = threadIndex
            threadList.Append(this)
            creationTime = gameLocal.time
            lastExecuteTime = 0
            manualControl = false
            ClearWaitFor()
            interpreter.SetThread(this)
            spawnArgs = idDict()
        }

        private fun Pause() {
            ClearWaitFor()
            interpreter.doneProcessing = true
        }

        private fun Event_Execute() {
            Execute()
        }

        private fun Event_Pause() {
            Pause()
        }

        private fun Event_WaitFrame() {
            WaitFrame()
        }

        private fun Event_GetTime() {
            ReturnFloat(MS2SEC(gameLocal.realClientTime.toFloat()))
        }

        private fun Event_ClearPersistantArgs() {
            gameLocal.persistentLevelInfo.Clear()
        }

        private fun Event_FirstPerson() {
            gameLocal.SetCamera(null)
        }

        private fun Event_GetTraceFraction() {
            ReturnFloat(trace.fraction)
        }

        private fun Event_GetTraceEndPos() {
            ReturnVector(trace.endpos)
        }

        private fun Event_GetTraceNormal() {
            if (trace.fraction < 1.0f) {
                ReturnVector(trace.c.normal)
            } else {
                ReturnVector(idVec3())
            }
        }

        private fun Event_GetTraceEntity() {
            if (trace.fraction < 1.0f) {
                ReturnEntity(gameLocal.entities[trace.c.entityNum])
            } else {
                ReturnEntity(null)
            }
        }

        private fun Event_GetTraceJoint() {
            if (trace.fraction < 1.0f && trace.c.id < 0) {
                val af = gameLocal.entities[trace.c.entityNum] as idAFEntity_Base?
                if (af != null && af is idAFEntity_Base && af.IsActiveAF()) {
                    ReturnString(af.GetAnimator().GetJointName(CLIPMODEL_ID_TO_JOINT_HANDLE(trace.c.id)))
                    return
                }
            }
            ReturnString("")
        }

        private fun Event_GetTraceBody() {
            if (trace.fraction < 1.0f && trace.c.id < 0) {
                val af = gameLocal.entities[trace.c.entityNum] as idAFEntity_Base?
                if (af != null && af is idAFEntity_Base && af.IsActiveAF()) {
                    val bodyId = af.BodyForClipModelId(trace.c.id)
                    val body = af.GetAFPhysics().GetBody(bodyId)
                    if (body != null) {
                        ReturnString(body.GetName())
                        return
                    }
                }
            }
            ReturnString("")
        }

        private fun Event_IsClient() {
            ReturnFloat(btoi(gameLocal.isClient).toFloat())
        }

        private fun Event_IsMultiplayer() {
            ReturnFloat(btoi(gameLocal.isMultiplayer).toFloat())
        }

        private fun Event_GetFrameTime() {
            ReturnFloat(MS2SEC(gameLocal.msec.toFloat()))
        }

        private fun Event_GetTicsPerSecond() {
            ReturnFloat(USERCMD_HZ.toFloat())
        }

        private fun Event_InfluenceActive() {
            val player: idPlayer?
            player = gameLocal.GetLocalPlayer()
            ReturnInt(player != null && player.GetInfluenceLevel() != 0)
        }

        // virtual						~idThread();
        override fun _deconstructor() {
            var thread: idThread
            var i: Int
            val n: Int
            if (SysCvar.g_debugScript.GetBool()) {
                gameLocal.Printf(
                    "%d: end thread (%d) '%s'\n", gameLocal.time, threadNum, threadName
                )
            }
            threadList.Remove(this)
            n = threadList.Num()
            i = 0
            while (i < n) {
                thread = threadList[i]
                if (thread.WaitingOnThread() === this) {
                    thread.ThreadCallback(this)
                }
                i++
            }
            if (currentThread === this) {
                currentThread = null
            }
            super._deconstructor()
        }

        // tells the thread manager not to delete this thread when it ends
        fun ManualDelete() {
            interpreter.terminateOnExit = false
        }

        // save games
        override fun Save(savefile: idSaveGame) {                // archives object for save game file
            super.Save(savefile)

            // We will check on restore that threadNum is still the same,
            // threads should have been restored in the same order.
            savefile.WriteInt(threadNum)
            savefile.WriteObject(waitingForThread)
            savefile.WriteInt(waitingFor)
            savefile.WriteInt(waitingUntil)
            interpreter.Save(savefile)
            savefile.WriteDict(spawnArgs)
            savefile.WriteString(threadName)
            savefile.WriteInt(lastExecuteTime)
            savefile.WriteInt(creationTime)
            savefile.WriteBool(manualControl)
        }

        override fun Restore(savefile: idRestoreGame) {                // unarchives object from save game file
            super.Restore(savefile)
            threadNum = savefile.ReadInt()
            waitingForThread = savefile.ReadObject() as idThread?
            waitingFor = savefile.ReadInt()
            waitingUntil = savefile.ReadInt()
            interpreter.Restore(savefile)
            spawnArgs = savefile.ReadDict()
            savefile.ReadString(threadName)
            lastExecuteTime = savefile.ReadInt()
            creationTime = savefile.ReadInt()
            manualControl = savefile.ReadBool()
        }

        fun EnableDebugInfo() {
            interpreter.debug = true
        }

        fun DisableDebugInfo() {
            interpreter.debug = false
        }

        fun WaitMS(time: Int) {
            Pause()
            waitingUntil = gameLocal.time + time
        }

        fun WaitSec(time: Float) {
            WaitMS(SEC2MS(time))
        }

        fun WaitFrame() {
            Pause()

            // manual control threads don't set waitingUntil so that they can be run again
            // that frame if necessary.
            if (!manualControl) {
                waitingUntil = gameLocal.time + idGameLocal.msecPrecise.toInt()
            }
        }

        /*
         ================
         idThread::CallFunction

         NOTE: If this is called from within a event called by this thread, the function arguments will be invalid after calling this function.
         ================
         */
        fun CallFunction(func: function_t, clearStack: Boolean) {
            ClearWaitFor()
            interpreter.EnterFunction(func, clearStack)
        }

        /*
         ================
         idThread::CallFunction

         NOTE: If this is called from within a event called by this thread, the function arguments will be invalid after calling this function.
         ================
         */
        fun CallFunction(self: idEntity?, func: function_t, clearStack: Boolean) {
            assert(self != null)
            ClearWaitFor()
            interpreter.EnterObjectFunction(self, func, clearStack)
        }

        fun DisplayInfo() {
            gameLocal.Printf(
                """%12i: '%s'
        File: %s(%d)
     Created: %d (%d ms ago)
      Status: """,
                threadNum,
                threadName,
                interpreter.CurrentFile(),
                interpreter.CurrentLine(),
                creationTime,
                gameLocal.time - creationTime
            )
            if (interpreter.threadDying) {
                gameLocal.Printf("Dying\n")
            } else if (interpreter.doneProcessing) {
                gameLocal.Printf(
                    """Paused since %d (%d ms)
      Reason: """, lastExecuteTime, gameLocal.time - lastExecuteTime
                )
                if (waitingForThread != null) {
                    gameLocal.Printf(
                        "Waiting for thread #%3d '%s'\n",
                        waitingForThread!!.GetThreadNum(),
                        waitingForThread!!.GetThreadName()
                    )
                } else if (waitingFor != Game_local.ENTITYNUM_NONE && gameLocal.entities[waitingFor] != null) {
                    gameLocal.Printf(
                        "Waiting for entity #%3d '%s'\n", waitingFor, gameLocal.entities[waitingFor]!!.name
                    )
                } else if (waitingUntil != 0) {
                    gameLocal.Printf(
                        "Waiting until %d (%d ms total wait time)\n", waitingUntil, waitingUntil - lastExecuteTime
                    )
                } else {
                    gameLocal.Printf("None\n")
                }
            } else {
                gameLocal.Printf("Processing\n")
            }
            interpreter.DisplayInfo()
            gameLocal.Printf("\n")
        }

        override fun CreateInstance(): idClass = idThread()

        override fun GetType(): idTypeInfo = Type

        override fun getEventCallBack(event: idEventDef): eventCallback_t<*>? {
            return eventCallbacks[event]
        }

        fun set(get: idClass?) {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        fun IsDoneProcessing(): Boolean {
            return interpreter.doneProcessing
        }

        fun IsDying(): Boolean {
            return interpreter.threadDying
        }

        fun End() {
            // Tell thread to die.  It will exit on its own.
            Pause()
            interpreter.threadDying = true
        }

        fun Execute(): Boolean {
//            return false;//HACKME::6
            val oldThread: idThread?
            val done: Boolean
            if (manualControl && waitingUntil > gameLocal.time) {
                return false
            }
            oldThread = currentThread
            currentThread = this
            lastExecuteTime = gameLocal.time
            ClearWaitFor()
            done = interpreter.Execute()
            if (done) {
                End()
                if (interpreter.terminateOnExit) {
                    PostEventMS(EV_Remove, 0)
                }
            } else if (!manualControl) {
                if (waitingUntil > lastExecuteTime) {
                    PostEventMS(EV_Thread_Execute, waitingUntil - lastExecuteTime)
                } else if (interpreter.MultiFrameEventInProgress()) {
                    PostEventMS(EV_Thread_Execute, gameLocal.msec)
                }
            }
            currentThread = oldThread
            return done
        }

        fun ManualControl() {
            manualControl = true
            CancelEvents(EV_Thread_Execute)
        }

        fun DoneProcessing() {
            interpreter.doneProcessing = true
        }

        fun ContinueProcessing() {
            interpreter.doneProcessing = false
        }

        fun ThreadDying(): Boolean {
            return interpreter.threadDying
        }

        fun EndThread() {
            interpreter.threadDying = true
        }

        /*
         ================
         idThread::IsWaiting

         Checks if thread is still waiting for some event to occur.
         ================
         */
        fun IsWaiting(): Boolean {
            return if (waitingForThread != null || waitingFor != Game_local.ENTITYNUM_NONE) {
                true
            } else waitingUntil != 0 && waitingUntil > gameLocal.time
        }

        fun ClearWaitFor() {
            waitingFor = Game_local.ENTITYNUM_NONE
            waitingForThread = null
            waitingUntil = 0
        }

        fun IsWaitingFor(obj: idEntity?): Boolean {
            assert(obj != null)
            return waitingFor == obj!!.entityNumber
        }

        fun ObjectMoveDone(obj: idEntity?) {
            assert(obj != null)
            if (IsWaitingFor(obj)) {
                ClearWaitFor()
                DelayedStart(0)
            }
        }

        fun ThreadCallback(thread: idThread) {
            if (interpreter.threadDying) {
                return
            }
            if (thread === waitingForThread) {
                ClearWaitFor()
                DelayedStart(0)
            }
        }

        fun DelayedStart(delay: Int) {
            var delay = delay
            CancelEvents(EV_Thread_Execute)
            if (gameLocal.time <= 0) {
                delay++
            }
            PostEventMS(EV_Thread_Execute, delay)
        }

        fun Start(): Boolean {
            val result: Boolean
            CancelEvents(EV_Thread_Execute)
            result = Execute()
            return result
        }

        fun WaitingOnThread(): idThread? {
            return waitingForThread
        }

        fun SetThreadNum(num: Int) {
            threadNum = num
        }

        fun GetThreadNum(): Int {
            return threadNum
        }

        fun SetThreadName(name: String?) {
            threadName.set(name)
        }

        fun GetThreadName(): String {
            return threadName.toString()
        }

        fun Error(fmt: String?, vararg objects: Any?) { // const id_attribute((format(printf,2,3)));
            val text = String.format(fmt!!, *objects)
            interpreter.Error("%s", text)
        }

        fun Warning(fmt: String?, vararg objects: Any?) { // const id_attribute((format(printf,2,3)));
            val text = String.format(fmt!!, *objects)
            interpreter.Warning("%s", text)
        }

        override fun oSet(oGet: idClass?) {}

        /*
         ================
         idThread::ListThreads_f
         ================
         */
        class ListThreads_f private constructor() : cmdFunction_t() {
            override fun run(args: CmdArgs.idCmdArgs?) {
                var i: Int
                val n: Int
                n = threadList.Num()
                i = 0
                while (i < n) {

                    //threadList[ i ].DisplayInfo();
                    gameLocal.Printf(
                        "%3d: %-20s : %s(%d)\n",
                        threadList[i].threadNum,
                        threadList[i].threadName,
                        threadList[i].interpreter.CurrentFile(),
                        threadList[i].interpreter.CurrentLine()
                    )
                    i++
                }
                gameLocal.Printf("%d active threads\n\n", n)
            }

            companion object {
                val instance: cmdFunction_t = ListThreads_f()
            }
        }

        companion object {
            val Type = idTypeInfo("idThread", "idClass") { idThread() }

            const val BYTES = Integer.BYTES * 14 //TODO
            protected var eventCallbacks: MutableMap<idEventDef, eventCallback_t<*>> = HashMap()

            //        // CLASS_PROTOTYPE( idThread );
            //        public static final idTypeInfo Type = new idTypeInfo(null, null, eventCallbacks, null, null, null, null);
            //
            //
            private var currentThread: idThread? = null

            //
            private var threadIndex = 0
            private val threadList = idList<idThread>()

            //
            private var trace = trace_s()

            init {
                eventCallbacks.putAll(getEventCallBacks())
                eventCallbacks.putAll(getEventCallBacks())
                eventCallbacks[EV_Thread_Execute] = eventCallback_t0 { obj: idThread -> obj.Event_Execute() }
                eventCallbacks[EV_Thread_TerminateThread] = eventCallback_t1 { t: idThread, num: idEventArg<*> ->
                    Event_TerminateThread(
                        t, num as idEventArg<Int>
                    )
                }
                eventCallbacks[EV_Thread_Pause] = (eventCallback_t0 { obj: idThread -> obj.Event_Pause() })
                eventCallbacks[EV_Thread_Wait] =
                    (eventCallback_t1 { t: idThread, time: idEventArg<*> -> Event_Wait(t, time as idEventArg<Float>) })
                eventCallbacks[EV_Thread_WaitFrame] = (eventCallback_t0 { obj: idThread -> obj.Event_WaitFrame() })
                eventCallbacks[EV_Thread_WaitFor] = (eventCallback_t1 { t: idThread, e: idEventArg<*> ->
                    Event_WaitFor(
                        t, e as idEventArg<idEntity>
                    )
                })
                eventCallbacks[EV_Thread_WaitForThread] = (eventCallback_t1 { t: idThread, num: idEventArg<*> ->
                    Event_WaitForThread(
                        t, num as idEventArg<Int>
                    )
                })
                eventCallbacks[EV_Thread_Print] = (eventCallback_t1 { t: idThread, text: idEventArg<*> ->
                    Event_Print(
                        t, text as idEventArg<String>
                    )
                })
                eventCallbacks[EV_Thread_PrintLn] = (eventCallback_t1 { t: idThread, text: idEventArg<*> ->
                    Event_PrintLn(
                        t, text as idEventArg<String>
                    )
                })
                eventCallbacks[EV_Thread_Say] = (eventCallback_t1 { t: idThread, text: idEventArg<*> ->
                    Event_Say(
                        t, text as idEventArg<String>
                    )
                })
                eventCallbacks[EV_Thread_Assert] = (eventCallback_t1 { t: idThread, value: idEventArg<*> ->
                    Event_Assert(
                        t, value as idEventArg<Float>
                    )
                })
                eventCallbacks[EV_Thread_Trigger] = (eventCallback_t1 { t: idThread, e: idEventArg<*> ->
                    Event_Trigger(
                        t, e as idEventArg<idEntity>
                    )
                })
                eventCallbacks[EV_Thread_SetCvar] =
                    (eventCallback_t2 { t: idThread, name: idEventArg<*>, value: idEventArg<*> ->
                        Event_SetCvar(
                            t, name as idEventArg<String>, value as idEventArg<String>
                        )
                    })
                eventCallbacks[EV_Thread_GetCvar] = (eventCallback_t1 { t: idThread, name: idEventArg<*> ->
                    Event_GetCvar(
                        t, name as idEventArg<String>
                    )
                })
                eventCallbacks[EV_Thread_Random] = (eventCallback_t1 { t: idThread, range: idEventArg<*> ->
                    Event_Random(
                        t, range as idEventArg<Float>
                    )
                })
                eventCallbacks[EV_Thread_RandomInt] = (eventCallback_t1 { t: idThread, range: idEventArg<*> ->
                    Event_RandomInt(t, range as idEventArg<Int>)
                })
                eventCallbacks[EV_Thread_GetTime] = (eventCallback_t0 { obj: idThread -> obj.Event_GetTime() })
                eventCallbacks[EV_Thread_KillThread] = (eventCallback_t1 { t: idThread, name: idEventArg<*> ->
                    Event_KillThread(
                        t, name as idEventArg<String>
                    )
                })
                eventCallbacks[EV_Thread_SetThreadName] = (eventCallback_t1 { t: idThread, name: idEventArg<*> ->
                    Event_SetThreadName(
                        t, name as idEventArg<String>
                    )
                })
                eventCallbacks[EV_Thread_GetEntity] = (eventCallback_t1 { t: idThread, n: idEventArg<*> ->
                    Event_GetEntity(
                        t, n as idEventArg<String>
                    )
                })
                eventCallbacks[EV_Thread_Spawn] = (eventCallback_t1 { t: idThread, classname: idEventArg<*> ->
                    Event_Spawn(
                        t, classname as idEventArg<String>
                    )
                })
                eventCallbacks[EV_Thread_CopySpawnArgs] = (eventCallback_t1 { t: idThread, ent: idEventArg<*> ->
                    Event_CopySpawnArgs(
                        t, ent as idEventArg<idEntity>
                    )
                })
                eventCallbacks[EV_Thread_SetSpawnArg] =
                    (eventCallback_t2 { t: idThread, key: idEventArg<*>, value: idEventArg<*> ->
                        Event_SetSpawnArg(
                            t, key as idEventArg<String>, value as idEventArg<String>
                        )
                    })
                eventCallbacks[EV_Thread_SpawnString] =
                    (eventCallback_t2 { t: idThread, key: idEventArg<*>, defaultvalue: idEventArg<*> ->
                        Event_SpawnString(
                            t, key as idEventArg<String>, defaultvalue as idEventArg<String>
                        )
                    })
                eventCallbacks[EV_Thread_SpawnFloat] =
                    (eventCallback_t2 { t: idThread, key: idEventArg<*>, defaultvalue: idEventArg<*> ->
                        Event_SpawnFloat(
                            t, key as idEventArg<String>, defaultvalue as idEventArg<Float>
                        )
                    })
                eventCallbacks[EV_Thread_SpawnVector] =
                    (eventCallback_t2 { t: idThread, key: idEventArg<*>, d: idEventArg<*> ->
                        Event_SpawnVector(
                            t, key as idEventArg<String>, d as idEventArg<idVec3>
                        )
                    })
                eventCallbacks[EV_Thread_ClearPersistantArgs] =
                    (eventCallback_t0 { obj: idThread -> obj.Event_ClearPersistantArgs() })
                eventCallbacks[EV_Thread_SetPersistantArg] =
                    (eventCallback_t2 { t: idThread, key: idEventArg<*>, value: idEventArg<*> ->
                        Event_SetPersistantArg(
                            t, key as idEventArg<String>, value as idEventArg<String>
                        )
                    })
                eventCallbacks[EV_Thread_GetPersistantString] = (eventCallback_t1 { t: idThread, key: idEventArg<*> ->
                    Event_GetPersistantString(
                        t, key as idEventArg<String>
                    )
                })
                eventCallbacks[EV_Thread_GetPersistantFloat] = (eventCallback_t1 { t: idThread, key: idEventArg<*> ->
                    Event_GetPersistantFloat(
                        t, key as idEventArg<String>
                    )
                })
                eventCallbacks[EV_Thread_GetPersistantVector] = (eventCallback_t1 { t: idThread, key: idEventArg<*> ->
                    Event_GetPersistantVector(
                        t, key as idEventArg<String>
                    )
                })
                eventCallbacks[EV_Thread_AngToForward] = (eventCallback_t1 { t: idThread, ang: idEventArg<*> ->
                    Event_AngToForward(
                        t, ang as idEventArg<idVec3>
                    )
                })
                eventCallbacks[EV_Thread_AngToRight] = (eventCallback_t1 { t: idThread, ang: idEventArg<*> ->
                    Event_AngToRight(
                        t, ang as idEventArg<idAngles>
                    )
                })
                eventCallbacks[EV_Thread_AngToUp] = (eventCallback_t1 { t: idThread, ang: idEventArg<*> ->
                    Event_AngToUp(
                        t, ang as idEventArg<idAngles>
                    )
                })
                eventCallbacks[EV_Thread_Sine] = (eventCallback_t1 { t: idThread, angle: idEventArg<*> ->
                    Event_GetSine(
                        t, angle as idEventArg<Float>
                    )
                })
                eventCallbacks[EV_Thread_Cosine] = (eventCallback_t1 { t: idThread, angle: idEventArg<*> ->
                    Event_GetCosine(
                        t, angle as idEventArg<Float>
                    )
                })
                eventCallbacks[EV_Thread_ArcSine] = (eventCallback_t1 { t: idThread, angle: idEventArg<*> ->
                    Event_GetArcSine(t, angle as idEventArg<Float>)
                })
                eventCallbacks[EV_Thread_ArcCosine] = (eventCallback_t1 { t: idThread, angle: idEventArg<*> ->
                    Event_GetArcCosine(t, angle as idEventArg<Float>)
                })
                eventCallbacks[EV_Thread_SquareRoot] = (eventCallback_t1 { t: idThread, theSquare: idEventArg<*> ->
                    Event_GetSquareRoot(
                        t, theSquare as idEventArg<Float>
                    )
                })
                eventCallbacks[EV_Thread_Normalize] = (eventCallback_t1 { t: idThread, vec: idEventArg<*> ->
                    Event_VecNormalize(
                        t, vec as idEventArg<idVec3>
                    )
                })
                eventCallbacks[EV_Thread_VecLength] = (eventCallback_t1 { t: idThread, vec: idEventArg<*> ->
                    Event_VecLength(
                        t, vec as idEventArg<idVec3>
                    )
                })
                eventCallbacks[EV_Thread_VecDotProduct] =
                    (eventCallback_t2 { t: idThread, vec1: idEventArg<*>, vec2: idEventArg<*> ->
                        Event_VecDotProduct(
                            t, vec1 as idEventArg<idVec3>, vec2 as idEventArg<idVec3>
                        )
                    })
                eventCallbacks[EV_Thread_VecCrossProduct] =
                    (eventCallback_t2 { t: idThread, vec1: idEventArg<*>, vec2: idEventArg<*> ->
                        Event_VecCrossProduct(
                            t, vec1 as idEventArg<idVec3>, vec2 as idEventArg<idVec3>
                        )
                    })
                eventCallbacks[EV_Thread_VecToAngles] = (eventCallback_t1 { t: idThread, vec: idEventArg<*> ->
                    Event_VecToAngles(
                        t, vec as idEventArg<idVec3>
                    )
                })
                eventCallbacks[EV_Thread_VecToOrthoBasisAngles] = (eventCallback_t1 { t: idThread, vec: idEventArg<*> ->
                    Event_VecToOrthoBasisAngles(t, vec as idEventArg<idVec3>)
                })
                eventCallbacks[EV_Thread_RotateVector] =
                    (eventCallback_t2 { t: idThread, vec: idEventArg<*>, ang: idEventArg<*> ->
                        Event_RotateVector(t, vec as idEventArg<idVec3>, ang as idEventArg<idVec3>)
                    })
                eventCallbacks[EV_Thread_OnSignal] =
                    (eventCallback_t3 { t: idThread, s: idEventArg<*>, e: idEventArg<*>, f: idEventArg<*> ->
                        Event_OnSignal(
                            t, s as idEventArg<Int>, e as idEventArg<idEntity>, f as idEventArg<String>
                        )
                    })
                eventCallbacks[EV_Thread_ClearSignal] =
                    (eventCallback_t2 { t: idThread, s: idEventArg<*>, e: idEventArg<*> ->
                        Event_ClearSignalThread(
                            t, s as idEventArg<Int>, e as idEventArg<idEntity>
                        )
                    })
                eventCallbacks[EV_Thread_SetCamera] = (eventCallback_t1 { t: idThread, e: idEventArg<*> ->
                    Event_SetCamera(
                        t, e as idEventArg<idEntity>
                    )
                })
                eventCallbacks[EV_Thread_FirstPerson] = (eventCallback_t0 { obj: idThread -> obj.Event_FirstPerson() })
                eventCallbacks[EV_Thread_Trace] =
                    (eventCallback_t6 { t: idThread, s: idEventArg<*>, e: idEventArg<*>, mi: idEventArg<*>, ma: idEventArg<*>, c: idEventArg<*>, p: idEventArg<*> ->
                        Event_Trace(
                            t,
                            s as idEventArg<idVec3>,
                            e as idEventArg<idVec3>,
                            mi as idEventArg<idVec3>,
                            ma as idEventArg<idVec3>,
                            c as idEventArg<Int>,
                            p as idEventArg<idEntity>
                        )
                    })
                eventCallbacks[EV_Thread_TracePoint] =
                    (eventCallback_t4 { t: idThread, startA: idEventArg<*>, endA: idEventArg<*>, c: idEventArg<*>, p: idEventArg<*> ->
                        Event_TracePoint(
                            t,
                            startA as idEventArg<idVec3>,
                            endA as idEventArg<idVec3>,
                            c as idEventArg<Int>,
                            p as idEventArg<idEntity>
                        )
                    })
                eventCallbacks[EV_Thread_GetTraceFraction] =
                    (eventCallback_t0 { obj: idThread -> obj.Event_GetTraceFraction() })
                eventCallbacks[EV_Thread_GetTraceEndPos] =
                    (eventCallback_t0 { obj: idThread -> obj.Event_GetTraceEndPos() })
                eventCallbacks[EV_Thread_GetTraceNormal] =
                    (eventCallback_t0 { obj: idThread -> obj.Event_GetTraceNormal() })
                eventCallbacks[EV_Thread_GetTraceEntity] =
                    (eventCallback_t0 { obj: idThread -> obj.Event_GetTraceEntity() })
                eventCallbacks[EV_Thread_GetTraceJoint] =
                    (eventCallback_t0 { obj: idThread -> obj.Event_GetTraceJoint() })
                eventCallbacks[EV_Thread_GetTraceBody] =
                    (eventCallback_t0 { obj: idThread -> obj.Event_GetTraceBody() })
                eventCallbacks[EV_Thread_FadeIn] =
                    (eventCallback_t2 { t: idThread, colorA: idEventArg<*>, time: idEventArg<*> ->
                        Event_FadeIn(
                            t, colorA as idEventArg<idVec3>, time as idEventArg<Float>
                        )
                    })
                eventCallbacks[EV_Thread_FadeOut] =
                    (eventCallback_t2 { t: idThread, colorA: idEventArg<*>, time: idEventArg<*> ->
                        Event_FadeOut(
                            t, colorA as idEventArg<idVec3>, time as idEventArg<Float>
                        )
                    })
                eventCallbacks[EV_Thread_FadeTo] =
                    (eventCallback_t3 { t: idThread, colorA: idEventArg<*>, alpha: idEventArg<*>, time: idEventArg<*> ->
                        Event_FadeTo(
                            t, colorA as idEventArg<idVec3>, alpha as idEventArg<Float>, time as idEventArg<Float>
                        )
                    })
                eventCallbacks[EV_SetShaderParm] =
                    (eventCallback_t2 { t: idThread, parmnumA: idEventArg<*>, value: idEventArg<*> ->
                        Event_SetShaderParm(
                            t, parmnumA as idEventArg<Int>, value as idEventArg<Float>
                        )
                    })
                eventCallbacks[EV_Thread_StartMusic] = (eventCallback_t1 { t: idThread, text: idEventArg<*> ->
                    Event_StartMusic(
                        t, text as idEventArg<String>
                    )
                })
                eventCallbacks[EV_Thread_Warning] = (eventCallback_t1 { t: idThread, text: idEventArg<*> ->
                    Event_Warning(
                        t, text as idEventArg<String>
                    )
                })
                eventCallbacks[EV_Thread_Error] = (eventCallback_t1 { t: idThread, text: idEventArg<*> ->
                    Event_Error(
                        t, text as idEventArg<String>
                    )
                })
                eventCallbacks[EV_Thread_StrLen] = (eventCallback_t1 { t: idThread, string: idEventArg<*> ->
                    Event_StrLen(
                        t, string as idEventArg<String>
                    )
                })
                eventCallbacks[EV_Thread_StrLeft] =
                    (eventCallback_t2 { t: idThread, stringA: idEventArg<*>, numA: idEventArg<*> ->
                        Event_StrLeft(
                            t, stringA as idEventArg<String>, numA as idEventArg<Int>
                        )
                    })
                eventCallbacks[EV_Thread_StrRight] =
                    (eventCallback_t2 { t: idThread, stringA: idEventArg<*>, numA: idEventArg<*> ->
                        Event_StrRight(
                            t, stringA as idEventArg<String>, numA as idEventArg<Int>
                        )
                    })
                eventCallbacks[EV_Thread_StrSkip] =
                    (eventCallback_t2 { t: idThread, stringA: idEventArg<*>, numA: idEventArg<*> ->
                        Event_StrSkip(
                            t, stringA as idEventArg<String>, numA as idEventArg<Int>
                        )
                    })
                eventCallbacks[EV_Thread_StrMid] =
                    (eventCallback_t3 { t: idThread, stringA: idEventArg<*>, startA: idEventArg<*>, numA: idEventArg<*> ->
                        Event_StrMid(
                            t, stringA as idEventArg<String>, startA as idEventArg<Int>, numA as idEventArg<Int>
                        )
                    })
                eventCallbacks[EV_Thread_StrToFloat] = (eventCallback_t1 { t: idThread, string: idEventArg<*> ->
                    Event_StrToFloat(
                        t, string as idEventArg<String>
                    )
                })
                eventCallbacks[EV_Thread_RadiusDamage] =
                    (eventCallback_t6 { t: idThread, origin: idEventArg<*>, inflictor: idEventArg<*>, attacker: idEventArg<*>, ignore: idEventArg<*>, damageDefName: idEventArg<*>, dmgPower: idEventArg<*> ->
                        Event_RadiusDamage(
                            t,
                            origin as idEventArg<idVec3>,
                            inflictor as idEventArg<idEntity>,
                            attacker as idEventArg<idEntity>,
                            ignore as idEventArg<idEntity>,
                            damageDefName as idEventArg<String>,
                            dmgPower as idEventArg<Float>
                        )
                    })
                eventCallbacks[EV_Thread_IsClient] = (eventCallback_t0 { obj: idThread -> obj.Event_IsClient() })
                eventCallbacks[EV_Thread_IsMultiplayer] =
                    (eventCallback_t0 { obj: idThread -> obj.Event_IsMultiplayer() })
                eventCallbacks[EV_Thread_GetFrameTime] =
                    (eventCallback_t0 { obj: idThread -> obj.Event_GetFrameTime() })
                eventCallbacks[EV_Thread_GetTicsPerSecond] =
                    (eventCallback_t0 { obj: idThread -> obj.Event_GetTicsPerSecond() })
                eventCallbacks[EV_CacheSoundShader] = (eventCallback_t1 { t: idThread, soundName: idEventArg<*> ->
                    Event_CacheSoundShader(
                        t, soundName as idEventArg<String>
                    )
                })
                eventCallbacks[EV_Thread_DebugLine] =
                    (eventCallback_t4 { t: idThread, colorA: idEventArg<*>, start: idEventArg<*>, end: idEventArg<*>, lifetime: idEventArg<*> ->
                        Event_DebugLine(
                            t,
                            colorA as idEventArg<idVec3>,
                            start as idEventArg<idVec3>,
                            end as idEventArg<idVec3>,
                            lifetime as idEventArg<Float>
                        )
                    })
                eventCallbacks[EV_Thread_DebugArrow] =
                    (eventCallback_t5 { t: idThread, colorA: idEventArg<*>, start: idEventArg<*>, end: idEventArg<*>, size: idEventArg<*>, lifetime: idEventArg<*> ->
                        Event_DebugArrow(
                            t,
                            colorA as idEventArg<idVec3>,
                            start as idEventArg<idVec3>,
                            end as idEventArg<idVec3>,
                            size as idEventArg<Int>,
                            lifetime as idEventArg<Float>
                        )
                    })
                eventCallbacks[EV_Thread_DebugCircle] =
                    (eventCallback_t6 { t: idThread, colorA: idEventArg<*>, origin: idEventArg<*>, dir: idEventArg<*>, radius: idEventArg<*>, numSteps: idEventArg<*>, lifetime: idEventArg<*> ->
                        Event_DebugCircle(
                            t,
                            colorA as idEventArg<idVec3>,
                            origin as idEventArg<idVec3>,
                            dir as idEventArg<idVec3>,
                            radius as idEventArg<Float>,
                            numSteps as idEventArg<Int>,
                            lifetime as idEventArg<Float>
                        )
                    })
                eventCallbacks[EV_Thread_DebugBounds] =
                    (eventCallback_t4 { t: idThread, colorA: idEventArg<*>, mins: idEventArg<*>, maxs: idEventArg<*>, lifetime: idEventArg<*> ->
                        Event_DebugBounds(
                            t,
                            colorA as idEventArg<idVec3>,
                            mins as idEventArg<idVec3>,
                            maxs as idEventArg<idVec3>,
                            lifetime as idEventArg<Float>
                        )
                    })
                eventCallbacks[EV_Thread_DrawText] =
                    (eventCallback_t6 { t: idThread, text: idEventArg<*>, origin: idEventArg<*>, scale: idEventArg<*>, colorA: idEventArg<*>, align: idEventArg<*>, lifetime: idEventArg<*> ->
                        Event_DrawText(
                            t,
                            text as idEventArg<String>,
                            origin as idEventArg<idVec3>,
                            scale as idEventArg<Float>,
                            colorA as idEventArg<idVec3>,
                            align as idEventArg<Int>,
                            lifetime as idEventArg<Float>
                        )
                    })
                eventCallbacks[EV_Thread_InfluenceActive] =
                    (eventCallback_t0 { obj: idThread -> obj.Event_InfluenceActive() })
            }

            private fun Event_DebugLine(
                t: idThread,
                colorA: idEventArg<idVec3>,
                start: idEventArg<idVec3>,
                end: idEventArg<idVec3>,
                lifetime: idEventArg<Float>
            ) {
                val color = idVec3(colorA.value)
                Game_local.gameRenderWorld!!.DebugLine(
                    idVec4(color.x, color.y, color.z, 0.0f), start.value, end.value, SEC2MS(lifetime.value)
                )
            }

            private fun Event_DebugArrow(
                t: idThread,
                colorA: idEventArg<idVec3>,
                start: idEventArg<idVec3>,
                end: idEventArg<idVec3>,
                size: idEventArg<Int>,
                lifetime: idEventArg<Float>
            ) {
                val color = idVec3(colorA.value)
                Game_local.gameRenderWorld!!.DebugArrow(
                    idVec4(color.x, color.y, color.z, 0.0f), start.value, end.value, size.value, SEC2MS(lifetime.value)
                )
            }

            private fun Event_DebugCircle(
                t: idThread,
                colorA: idEventArg<idVec3>,
                origin: idEventArg<idVec3>,
                dir: idEventArg<idVec3>,
                radius: idEventArg<Float>,
                numSteps: idEventArg<Int>,
                lifetime: idEventArg<Float>
            ) {
                val color = idVec3(colorA.value)
                Game_local.gameRenderWorld!!.DebugCircle(
                    idVec4(color.x, color.y, color.z, 0.0f),
                    origin.value,
                    dir.value,
                    radius.value,
                    numSteps.value,
                    SEC2MS(lifetime.value)
                )
            }

            private fun Event_DebugBounds(
                t: idThread,
                colorA: idEventArg<idVec3>,
                mins: idEventArg<idVec3>,
                maxs: idEventArg<idVec3>,
                lifetime: idEventArg<Float>
            ) {
                val color = idVec3(colorA.value)
                Game_local.gameRenderWorld!!.DebugBounds(
                    idVec4(color.x, color.y, color.z, 0.0f),
                    idBounds(mins.value, maxs.value),
                    vec3_origin,
                    SEC2MS(lifetime.value)
                )
            }

            private fun Event_DrawText(
                t: idThread,
                text: idEventArg<String>,
                origin: idEventArg<idVec3>,
                scale: idEventArg<Float>,
                colorA: idEventArg<idVec3>,
                align: idEventArg<Int>,
                lifetime: idEventArg<Float>
            ) {
                val color = idVec3(colorA.value)
                Game_local.gameRenderWorld!!.DrawText(
                    text.value,
                    origin.value,
                    scale.value,
                    idVec4(color.x, color.y, color.z, 0.0f),
                    gameLocal.GetLocalPlayer()!!.viewAngles.ToMat3(),
                    align.value,
                    SEC2MS(lifetime.value)
                )
            }

            private fun Event_FadeIn(t: idThread, colorA: idEventArg<idVec3>, time: idEventArg<Float>) {
                val fadeColor = idVec4()
                val player: idPlayer?
                val color = idVec3(colorA.value)
                player = gameLocal.GetLocalPlayer()
                if (player != null) {
                    fadeColor.set(color[0], color[1], color[2], 0.0f)
                    player.playerView.Fade(fadeColor, SEC2MS(time.value))
                }
            }

            private fun Event_FadeOut(t: idThread, colorA: idEventArg<idVec3>, time: idEventArg<Float>) {
                val fadeColor = idVec4()
                val player: idPlayer?
                val color = idVec3(colorA.value)
                player = gameLocal.GetLocalPlayer()
                if (player != null) {
                    fadeColor.set(color[0], color[1], color[2], 1.0f)
                    player.playerView.Fade(fadeColor, SEC2MS(time.value))
                }
            }

            private fun Event_FadeTo(
                t: idThread, colorA: idEventArg<idVec3>, alpha: idEventArg<Float>, time: idEventArg<Float>
            ) {
                val fadeColor = idVec4()
                val player: idPlayer?
                val color = idVec3(colorA.value)
                player = gameLocal.GetLocalPlayer()
                if (player != null) {
                    fadeColor.set(color[0], color[1], color[2], alpha.value)
                    player.playerView.Fade(fadeColor, SEC2MS(time.value))
                }
            }

            private fun Event_SetShaderParm(t: idThread, parmnumA: idEventArg<Int>, value: idEventArg<Float>) {
                val parmnum = parmnumA.value
                if (parmnum < 0 || parmnum >= RenderWorld.MAX_GLOBAL_SHADER_PARMS) {
                    t.Error("shader parm index (%d) out of range", parmnum)
                }
                gameLocal.globalShaderParms[parmnum] = value.value
            }

            private fun Event_StartMusic(t: idThread, text: idEventArg<String>) {
                Game_local.gameSoundWorld!!.PlayShaderDirectly(text.value)
            }

            private fun Event_Warning(t: idThread, text: idEventArg<String>) {
                t.Warning("%s", text.value)
            }

            private fun Event_Error(t: idThread, text: idEventArg<String>) {
                t.Error("%s", text.value)
            }

            private fun Event_StrLen(t: idThread, string: idEventArg<String>) {
                val len: Int
                len = string.value.length
                ReturnInt(len)
            }

            private fun Event_StrLeft(t: idThread, stringA: idEventArg<String>, numA: idEventArg<Int>) {
                val len: Int
                val string = stringA.value
                val num = numA.value
                if (num < 0) {
                    ReturnString("")
                    return
                }
                len = string.length
                if (len < num) {
                    ReturnString(string)
                    return
                }
                val result = idStr(string, 0, num)
                ReturnString(result)
            }

            private fun Event_StrRight(t: idThread, stringA: idEventArg<String>, numA: idEventArg<Int>) {
                val len: Int
                val string = stringA.value
                val num = numA.value
                if (num < 0) {
                    ReturnString("")
                    return
                }
                len = string.length
                if (len < num) {
                    ReturnString(string)
                    return
                }
                ReturnString(string + (len - num))
            }

            private fun Event_StrSkip(t: idThread, stringA: idEventArg<String>, numA: idEventArg<Int>) {
                val len: Int
                val string = stringA.value
                val num = numA.value
                if (num < 0) {
                    ReturnString(string)
                    return
                }
                len = string.length
                if (len < num) {
                    ReturnString("")
                    return
                }
                ReturnString(string + num)
            }

            private fun Event_GetCosine(t: idThread, angle: idEventArg<Float>) {
                ReturnFloat(Cos(DEG2RAD(angle.value)))
            }

            private fun Event_GetArcSine(t: idThread, angle: idEventArg<Float>) {
                ReturnFloat(RAD2DEG(ASin(angle.value)))
            }

            private fun Event_GetArcCosine(t: idThread, angle: idEventArg<Float>) {
                ReturnFloat(RAD2DEG(ACos(angle.value)))
            }

            private fun Event_GetSquareRoot(t: idThread, theSquare: idEventArg<Float>) {
                ReturnFloat(Sqrt(theSquare.value))
            }

            private fun Event_VecNormalize(t: idThread, vec: idEventArg<idVec3>) {
                val n = idVec3(vec.value)
                n.Normalize()
                ReturnVector(n)
            }

            private fun Event_VecLength(t: idThread, vec: idEventArg<idVec3>) {
                ReturnFloat(vec.value.Length())
            }

            private fun Event_VecDotProduct(t: idThread, vec1: idEventArg<idVec3>, vec2: idEventArg<idVec3>) {
                ReturnFloat(vec1.value * vec2.value)
            }

            private fun Event_VecCrossProduct(t: idThread, vec1: idEventArg<idVec3>, vec2: idEventArg<idVec3>) {
                ReturnVector(vec1.value.Cross(vec2.value))
            }

            private fun Event_VecToAngles(t: idThread, vec: idEventArg<idVec3>) {
                val ang = vec.value.ToAngles()
                ReturnVector(idVec3(ang[0], ang[1], ang[2]))
            }

            private fun Event_VecToOrthoBasisAngles(t: idThread, vec: idEventArg<idVec3>) {
                val left = idVec3()
                val up = idVec3()
                vec.value.OrthogonalBasis(left, up)
                val axis = idMat3(left, up, vec.value)
                val ang = idAngles(axis.ToAngles())
                ReturnVector(idVec3(ang[0], ang[1], ang[2]))
            }

            private fun Event_RotateVector(t: idThread, vec: idEventArg<idVec3>, ang: idEventArg<idVec3>) {
                val tempAng = idAngles(ang.value.x, ang.value.y, ang.value.z)
                val axis = tempAng.ToMat3()
                ReturnVector(vec.value * axis)
            }

            private fun Event_OnSignal(
                t: idThread, s: idEventArg<Int>, e: idEventArg<idEntity>, f: idEventArg<String>
            ) {
                val function: function_t?
                val signal = s.value
                val ent = e.value
                val func = f.value
                if (null == ent) {
                    t.Error("Entity not found")
                }
                if (signal < 0 || signal >= etoi(signalNum_t.NUM_SIGNALS)) {
                    t.Error("Signal out of range")
                }
                function = gameLocal.program.FindFunction(func)
                if (null == function) {
                    t.Error("Function '%s' not found", func)
                }
                ent.SetSignal(signal, t, function)
            }

            private fun Event_ClearSignalThread(t: idThread, s: idEventArg<Int>, e: idEventArg<idEntity>) {
                val signal = s.value
                val ent = e.value
                if (null == ent) {
                    t.Error("Entity not found")
                }
                if (signal < 0 || signal >= etoi(signalNum_t.NUM_SIGNALS)) {
                    t.Error("Signal out of range")
                }
                ent.ClearSignalThread(signal, t)
            }

            private fun Event_SetCamera(t: idThread, e: idEventArg<idEntity>) {
                val ent = e.value
                if (null == ent) {
                    t.Error("Entity not found")
                    return
                }
                if (ent !is idCamera) {
                    t.Error("Entity is not a camera")
                    return
                }
                gameLocal.SetCamera(ent)
            }

            private fun Event_Trace(
                t: idThread,
                s: idEventArg<idVec3>,
                e: idEventArg<idVec3>,
                mi: idEventArg<idVec3>,
                ma: idEventArg<idVec3>,
                c: idEventArg<Int>,
                p: idEventArg<idEntity>
            ) {
                val start = idVec3(s.value)
                val end = idVec3(e.value)
                val mins = idVec3(mi.value)
                val maxs = idVec3(ma.value)
                val contents_mask = c.value
                val passEntity = p.value
                run {
                    val trace = trace
                    if (mins == vec3_origin && maxs == vec3_origin) {
                        gameLocal.clip.TracePoint(trace, start, end, contents_mask, passEntity)
                    } else {
                        gameLocal.clip.TraceBounds(
                            trace, start, end, idBounds(mins, maxs), contents_mask, passEntity
                        )
                    }
                    idThread.trace = trace
                }
                ReturnFloat(trace.fraction)
            }

            private fun Event_TracePoint(
                t: idThread,
                startA: idEventArg<idVec3>,
                endA: idEventArg<idVec3>,
                c: idEventArg<Int>,
                p: idEventArg<idEntity>
            ) {
                val start = idVec3(startA.value)
                val end = idVec3(endA.value)
                val contents_mask = c.value
                val passEntity = p.value
                run {
                    val trace = trace
                    gameLocal.clip.TracePoint(trace, start, end, contents_mask, passEntity)
                    idThread.trace = trace
                }
                ReturnFloat(trace.fraction)
            }

            private fun Event_GetSine(t: idThread, angle: idEventArg<Float>) {
                ReturnFloat(Sin(DEG2RAD(angle.value)))
            }

            private fun Event_SetThreadName(t: idThread, name: idEventArg<String>) {
                t.SetThreadName(name.value)
            }

            //
// script callable Events
//
            private fun Event_TerminateThread(t: idThread, num: idEventArg<Int>) {
                KillThread(num.value)
            }

            private fun Event_Wait(t: idThread, time: idEventArg<Float>) {
                t.WaitSec(time.value)
            }

            private fun Event_WaitFor(t: idThread, e: idEventArg<idEntity>) {
                val ent = e.value
                if (ent != null && ent.RespondsTo(EV_Thread_SetCallback)) {
                    ent.ProcessEvent(EV_Thread_SetCallback)
                    if (gameLocal.program.GetReturnedInteger() != 0) {
                        t.Pause()
                        t.waitingFor = ent.entityNumber
                    }
                }
            }

            private fun Event_WaitForThread(t: idThread, num: idEventArg<Int>) {
                val thread: idThread?
                thread = GetThread(num.value)
                if (null == thread) {
                    if (SysCvar.g_debugScript.GetBool()) {
                        // just print a warning and continue executing
                        t.Warning("Thread %d not running", num.value)
                    }
                } else {
                    t.Pause()
                    t.waitingForThread = thread
                }
            }

            private fun Event_Print(t: idThread, text: idEventArg<String>) {
                gameLocal.Printf("%s", text.value)
            }

            private fun Event_PrintLn(t: idThread, text: idEventArg<String>) {
                gameLocal.Printf("%s\n", text.value)
            }

            private fun Event_Say(t: idThread, text: idEventArg<String>) {
                cmdSystem.BufferCommandText(cmdExecution_t.CMD_EXEC_NOW, String.format("say \"%s\"", text.value))
            }

            private fun Event_Assert(t: idThread, value: idEventArg<Float>) {
                assert(value.value != 0.0f)
            }

            private fun Event_Trigger(t: idThread, e: idEventArg<idEntity>) {
                val ent: idEntity = e.value
                if (ent != null) {
                    ent.Signal(signalNum_t.SIG_TRIGGER)
                    ent.ProcessEvent(EV_Activate, gameLocal.GetLocalPlayer())
                    ent.TriggerGuis()
                }
            }

            private fun Event_SetCvar(t: idThread, name: idEventArg<String>, value: idEventArg<String>) {
                cvarSystem.SetCVarString(name.value, value.value)
            }

            private fun Event_GetCvar(t: idThread, name: idEventArg<String>) {
                ReturnString(cvarSystem.GetCVarString(name.value))
            }

            private fun Event_Random(t: idThread, range: idEventArg<Float>) {
                val result: Float
                result = gameLocal.random.RandomFloat()
                ReturnFloat(range.value * result)
            }

            private fun Event_RandomInt(t: idThread, range: idEventArg<Int>) {
                val result = gameLocal.random.RandomInt(range.value)
                ReturnFloat(result.toFloat())
            }

            private fun Event_KillThread(t: idThread, name: idEventArg<String>) {
                KillThread(name.value)
            }

            private fun Event_GetEntity(t: idThread, n: idEventArg<String>) {
                val entnum: Int
                val ent: idEntity?
                val name = n.value
                if (name.startsWith("*")) {
                    entnum = name.substring(1).toInt()
                    if (entnum < 0 || entnum >= Game_local.MAX_GENTITIES) {
                        t.Error("Entity number in string out of range.")
                    }
                    ReturnEntity(gameLocal.entities[entnum])
                } else {
                    ent = gameLocal.FindEntity(name)
                    ReturnEntity(ent)
                }
            }

            //
            private fun Event_Spawn(t: idThread, classname: idEventArg<String>) {
                var ent: Array<idEntity?> = arrayOfNulls(1)

                t.spawnArgs!!.Set("classname", classname.value)
                gameLocal.SpawnEntityDef(t.spawnArgs!!, ent)
                ReturnEntity(ent[0])
                t.spawnArgs!!.Clear()
            }

            private fun Event_CopySpawnArgs(t: idThread, ent: idEventArg<idEntity>) {
                t.spawnArgs!!.Copy(ent.value.spawnArgs)
            }

            private fun Event_SetSpawnArg(t: idThread, key: idEventArg<String>, value: idEventArg<String>) {
                t.spawnArgs!!.Set(key.value, value.value)
            }

            private fun Event_SpawnString(t: idThread, key: idEventArg<String>, defaultvalue: idEventArg<String>) {
                val result = arrayOfNulls<String>(1)
                t.spawnArgs!!.GetString(key.value, defaultvalue.value, result)
                ReturnString(result[0])
            }

            private fun Event_SpawnFloat(t: idThread, key: idEventArg<String>, defaultvalue: idEventArg<Float>) {
                val result = CFloat()
                t.spawnArgs!!.GetFloat(key.value, String.format("%f", defaultvalue.value), result)
                ReturnFloat(result._val)
            }

            //
            private fun Event_SpawnVector(t: idThread, key: idEventArg<String>, d: idEventArg<idVec3>) {
                val result: idVec3 = idVec3()
                val defaultvalue: idVec3 = idVec3(d.value)

                t.spawnArgs!!.GetVector(
                    key.value, String.format("%f %f %f", defaultvalue.x, defaultvalue.y, defaultvalue.z), result
                )
                ReturnVector(result)
            }

            private fun Event_SetPersistantArg(t: idThread, key: idEventArg<String>, value: idEventArg<String>) {
                gameLocal.persistentLevelInfo.Set(key.value, value.value)
            }

            private fun Event_GetPersistantString(t: idThread, key: idEventArg<String>) {
                val result = arrayOfNulls<String>(1)
                gameLocal.persistentLevelInfo.GetString(key.value, "", result)
                ReturnString(result[0])
            }

            private fun Event_GetPersistantFloat(t: idThread, key: idEventArg<String>) {
                val result = CFloat()
                gameLocal.persistentLevelInfo.GetFloat(key.value, "0", result)
                ReturnFloat(result._val)
            }

            private fun Event_GetPersistantVector(t: idThread, key: idEventArg<String>) {
                val result = idVec3()
                gameLocal.persistentLevelInfo.GetVector(key.value, "0 0 0", result)
                ReturnVector(result)
            }

            private fun Event_AngToForward(t: idThread, ang: idEventArg<idVec3>) {
                ReturnVector(idAngles(ang.value).ToForward())
            }

            private fun Event_AngToRight(t: idThread, ang: idEventArg<idAngles>) {
                val vec = idVec3()
                ang.value.ToVectors(null, vec)
                ReturnVector(vec)
            }

            private fun Event_AngToUp(t: idThread, ang: idEventArg<idAngles>) {
                val vec = idVec3()
                ang.value.ToVectors(null, null, vec)
                ReturnVector(vec)
            }

            private fun Event_StrMid(
                t: idThread, stringA: idEventArg<String>, startA: idEventArg<Int>, numA: idEventArg<Int>
            ) {
                val len: Int
                val string = stringA.value
                var start = startA.value
                var num = numA.value
                if (num < 0) {
                    ReturnString("")
                    return
                }
                if (start < 0) {
                    start = 0
                }
                len = string.length
                if (start > len) {
                    start = len
                }
                if (start + num > len) {
                    num = len - start
                }
                val result = idStr(string, start, start + num)
                ReturnString(result)
            }

            private fun Event_StrToFloat(t: idThread, string: idEventArg<String>) {
                val result: Float
                result = string.value.toFloat()
                ReturnFloat(result)
            }

            private fun Event_RadiusDamage(
                t: idThread,
                origin: idEventArg<idVec3>,
                inflictor: idEventArg<idEntity>,
                attacker: idEventArg<idEntity>,
                ignore: idEventArg<idEntity>,
                damageDefName: idEventArg<String>,
                dmgPower: idEventArg<Float>
            ) {
                gameLocal.RadiusDamage(
                    origin.value,
                    inflictor.value,
                    attacker.value,
                    ignore.value,
                    ignore.value,
                    damageDefName.value,
                    dmgPower.value
                )
            }

            private fun Event_CacheSoundShader(t: idThread, soundName: idEventArg<String>) {
                DeclManager.declManager.FindSound(soundName.value)
            }

            fun GetThread(num: Int): idThread? {
                var i: Int
                val n: Int
                var thread: idThread
                n = threadList.Num()
                i = 0
                while (i < n) {
                    thread = threadList[i]
                    if (thread.GetThreadNum() == num) {
                        return thread
                    }
                    i++
                }
                return null
            }


            fun Restart() {
                // reset the threadIndex
                threadIndex = 0
                currentThread = null
                val n = threadList.Num()
                for (i in n - 1 downTo 0) {
                    idEvent.CancelEvents(threadList[i])
                }
                threadList.Clear()

                trace = trace_s()
                trace.c.entityNum = Game_local.ENTITYNUM_NONE
            }

            fun ObjectMoveDone(threadnum: Int, obj: idEntity?) {
                val thread: idThread?
                if (0 == threadnum) {
                    return
                }
                thread = GetThread(threadnum)
                thread?.ObjectMoveDone(obj)
            }

            fun GetThreads(): idList<idThread> {
                return threadList
            }

            fun KillThread(name: String) {
                var i: Int
                val num: Int
                val len: Int
                val ptr: Int
                var thread: idThread

                // see if the name uses a wild card
                ptr = name.indexOf('*')
                len = if (ptr != -1) {
                    ptr - name.length //TODO:double check this puhlease!
                } else {
                    name.length
                }

                // kill only those threads whose name matches name
                num = threadList.Num()
                i = 0
                while (i < num) {
                    thread = threadList[i]
                    if (0 == Cmpn(thread.GetThreadName(), name, len)) {
                        thread.End()
                    }
                    i++
                }
            }

            fun KillThread(num: Int) {
                val thread: idThread?
                thread = GetThread(num)
                thread?.End()
            }

            fun CurrentThread(): idThread? {
                return currentThread
            }

            fun CurrentThreadNum(): Int {
                return if (currentThread != null) {
                    currentThread!!.GetThreadNum()
                } else {
                    0
                }
            }

            fun BeginMultiFrameEvent(ent: idEntity, event: idEventDef?): Boolean {
                if (null == currentThread) {
                    Error("idThread::BeginMultiFrameEvent called without a current thread")
                }
                return currentThread!!.interpreter.BeginMultiFrameEvent(ent, event)
            }

            fun EndMultiFrameEvent(ent: idEntity?, event: idEventDef?) {
                if (null == currentThread) {
                    Error("idThread::EndMultiFrameEvent called without a current thread")
                }
                currentThread!!.interpreter.EndMultiFrameEvent(ent, event)
            }

            fun ReturnString(text: String?) {
                gameLocal.program.ReturnString(text)
            }

            fun ReturnString(text: idStr) {
                ReturnString(text.toString())
            }

            fun ReturnFloat(value: Float) {
                gameLocal.program.ReturnFloat(value)
            }

            fun ReturnInt(value: Int) {
                // true integers aren't supported in the compiler,
                // so int values are stored as floats
                gameLocal.program.ReturnFloat(value.toFloat())
            }

            fun ReturnInt(value: Boolean) {
                ReturnInt(if (value) 1 else 0)
            }

            fun ReturnVector(vec: idVec3) {
                gameLocal.program.ReturnVector(vec)
            }

            fun ReturnEntity(ent: idEntity?) {
                gameLocal.program.ReturnEntity(ent)
            }

            fun delete(thread: idThread) {
                thread._deconstructor()
            }
        }
    }
}
package neo.Game.Script

import neo.Game.GameSys.Class.idEventArg
import neo.Game.GameSys.Class.idEventArg.Companion.toArg
import neo.Game.GameSys.Event.idEventDef
import neo.Game.GameSys.Event.idEventDef.Companion.FindEvent
import neo.Game.GameSys.SaveGame.idRestoreGame
import neo.Game.GameSys.SaveGame.idSaveGame
import neo.Game.Game_local
import neo.Game.Script.Script_Program.function_t
import neo.Game.Script.Script_Program.idScriptObject
import neo.Game.Script.Script_Program.idTypeDef
import neo.Game.Script.Script_Program.idVarDef
import neo.Game.Script.Script_Program.idVarDef.initialized_t
import neo.Game.Script.Script_Program.statement_s
import neo.Game.Script.Script_Program.varEval_s
import neo.Game.Script.Script_Thread.idThread
import neo.Game.idEntity
import neo.TempDump.btoi
import neo.TempDump.btos
import neo.TempDump.ctos
import neo.TempDump.itob
import neo.TempDump.strLen
import neo.framework.Common
import neo.idlib.Text.Str.idStr
import neo.idlib.Text.Str.idStr.Companion.Append
import neo.idlib.Text.Str.idStr.Companion.Cmp
import neo.idlib.Text.Str.idStr.Companion.Copynz
import neo.idlib.containers.CInt
import neo.idlib.math.idMath
import neo.idlib.math.idVec3
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*

object Script_Interpreter {
    const val LOCALSTACK_SIZE = 6144 * 2  // DG: doubled for 64-bit (dhewm3)
    const val MAX_STACK_DEPTH = 64

    class prstack_s {
        var f: function_t? = null
        var s = 0
        var stackbase = 0
    }

    class idInterpreter {
        private val callStack = arrayOfNulls<prstack_s>(MAX_STACK_DEPTH)

        //
        private val localstack = ByteArray(LOCALSTACK_SIZE)
        var debug = false

        //
        var doneProcessing = false
        var terminateOnExit = true
        var threadDying = false
        private var callStackDepth = 0

        //
        private var currentFunction: function_t? = null
        private var eventEntity: idEntity? = null
        private var instructionPointer = 0
        private var localstackBase = 0
        private var localstackUsed = 0
        private var maxLocalstackUsed = 0
        private var maxStackDepth = 0
        private var multiFrameEvent: idEventDef? = null

        //
        //
        //
        private var popParms = 0

        //
        private var thread: idThread? = null

        init {
            //            memset(localstack, 0, sizeof(localstack));
//            memset(callStack, 0, sizeof(callStack));
            Reset()
        }

        private fun PopParms(numParms: Int) {
            // pop our parms off the stack
            if (localstackUsed < numParms) {
                Error("locals stack underflow\n")
            }
            localstackUsed -= numParms
        }

        private fun PushString(string: String?) {
//            System.out.println("+++ " + string);
            if (localstackUsed + Script_Program.MAX_STRING_LEN > LOCALSTACK_SIZE) {
                Error("PushString: locals stack overflow\n")
            }
            // C++ uses idStr::Copynz which does strncpy (zero-pads) + null-terminates
            val bytes = (string ?: "").toByteArray()
            val copyLen = Math.min(bytes.size, Script_Program.MAX_STRING_LEN - 1)
            System.arraycopy(bytes, 0, localstack, localstackUsed, copyLen)
            // zero-fill remainder of the 128-byte slot (strncpy zero-pads + null terminate)
            Arrays.fill(
                localstack,
                localstackUsed + copyLen,
                localstackUsed + Script_Program.MAX_STRING_LEN,
                0.toByte()
            )
            localstackUsed += Script_Program.MAX_STRING_LEN
        }

        private fun Push(value: Int) {
            if (localstackUsed + Script_Program.SIZEOF_INTPTR > LOCALSTACK_SIZE) {
                Error("Push: locals stack overflow\n")
            }
            localstack[localstackUsed + 0] = (value ushr 0).toByte()
            localstack[localstackUsed + 1] = (value ushr 8).toByte()
            localstack[localstackUsed + 2] = (value ushr 16).toByte()
            localstack[localstackUsed + 3] = (value ushr 24).toByte()
            // zero upper bytes (padding for 64-bit alignment)
            for (i in 4 until Script_Program.SIZEOF_INTPTR) {
                localstack[localstackUsed + i] = 0
            }
            localstackUsed += Script_Program.SIZEOF_INTPTR
        }

        private fun PushVector(vector: idVec3) {
            if (localstackUsed + Script_Program.E_EVENT_SIZEOF_VEC > LOCALSTACK_SIZE) {
                Error("Push: locals stack overflow\n")
            }
            val bb = ByteBuffer.wrap(localstack, localstackUsed, Script_Program.E_EVENT_SIZEOF_VEC)
                .order(ByteOrder.LITTLE_ENDIAN)
            bb.putFloat(vector.x)
            bb.putFloat(vector.y)
            bb.putFloat(vector.z)
            // zero padding bytes (4 bytes on 64-bit)
            for (i in 12 until Script_Program.E_EVENT_SIZEOF_VEC) {
                localstack[localstackUsed + i] = 0
            }
            localstackUsed += Script_Program.E_EVENT_SIZEOF_VEC
        }

        private fun FloatToString(value: Float): String {
            if (value == value.toInt().toFloat()) {
                text = String.format("%d", value.toInt()).toCharArray()
            } else {
                text = String.format("%f", value).toCharArray()
            }
            return ctos(text)
        }

        private fun AppendString(def: idVarDef?, from: String?) {
            if (def!!.initialized == initialized_t.stackVariable) {
                // C++ uses idStr::Append(dest, MAX_STRING_LEN, src) which finds strlen(dest),
                // then Copynz(dest + l1, size - l1, src) — limits write to remaining slot space
                val offset = localstackBase + def.value!!.stackOffset
                val existingLen = strLen(localstack, offset) - offset
                if (existingLen >= Script_Program.MAX_STRING_LEN) {
                    return // already at max, nothing to append
                }
                val remaining = Script_Program.MAX_STRING_LEN - existingLen
                val bytes = (from ?: "").toByteArray()
                val copyLen = Math.min(bytes.size, remaining - 1)
                System.arraycopy(bytes, 0, localstack, offset + existingLen, copyLen)
                // null-terminate and zero-fill remainder
                Arrays.fill(
                    localstack,
                    offset + existingLen + copyLen,
                    offset + Script_Program.MAX_STRING_LEN,
                    0.toByte()
                )
            } else {
                def.value!!.stringPtr = Append(def.value!!.stringPtr!!, Script_Program.MAX_STRING_LEN, from!!)
                def.value!!.setString(def.value!!.stringPtr) // write bytes into variables[] for save compatibility
            }
        }

        private fun SetString(def: idVarDef?, from: String?) {
            if (def!!.initialized == initialized_t.stackVariable) {
                // C++ uses idStr::Copynz which does strncpy (zero-pads) + null-terminates
                val offset = localstackBase + def.value!!.stackOffset
                val bytes = (from ?: "").toByteArray()
                val copyLen = Math.min(bytes.size, Script_Program.MAX_STRING_LEN - 1)
                System.arraycopy(bytes, 0, localstack, offset, copyLen)
                // zero-fill remainder of the 128-byte slot (strncpy zero-pads + null terminate)
                Arrays.fill(localstack, offset + copyLen, offset + Script_Program.MAX_STRING_LEN, 0.toByte())
            } else {
                def.value!!.stringPtr = from
                def.value!!.setString(from) // write bytes into variables[] for save compatibility
            }
        }

        private fun GetString(def: idVarDef?): String? {
            return if (def!!.initialized == initialized_t.stackVariable) {
                btos(localstack, localstackBase + def.value!!.stackOffset)
            } else {
                def.value!!.stringPtr
            }
        }

        private fun GetVariable(def: idVarDef?): varEval_s? {
            if (def!!.initialized == initialized_t.stackVariable) {
                val varEval_s = varEval_s()
                varEval_s.setIntPtr(localstack, localstackBase + def.value!!.stackOffset)
                return varEval_s
            } else {
                return def.value
            }
        }

        private fun GetEvalVariable(def: idVarDef?): varEval_s {
            val evalVariable = GetVariable(def)
            if (evalVariable!!.entityNumberPtr != NULL_ENTITY) {
                val scriptObject = Game_local.gameLocal.entities[evalVariable.entityNumberPtr - 1]!!.scriptObject
                val data = scriptObject.data
                if (data != null) {
                    evalVariable.evalPtr = varEval_s()
                    evalVariable.evalPtr!!.setBytePtr(data, scriptObject.offset)
                }
            }
            return evalVariable
        }

        private fun GetEntity(entnum: Int): idEntity? {
            assert(entnum <= Game_local.MAX_GENTITIES)
            return if (entnum > 0 && entnum <= Game_local.MAX_GENTITIES) {
                Game_local.gameLocal.entities[entnum - 1]
            } else null
        }

        private fun GetScriptObject(entnum: Int): idScriptObject? {
            val ent: idEntity?
            assert(entnum <= Game_local.MAX_GENTITIES)
            if (entnum > 0 && entnum <= Game_local.MAX_GENTITIES) {
                ent = Game_local.gameLocal.entities[entnum - 1]
                if (ent != null && ent.scriptObject.data != null) {
                    return ent.scriptObject
                }
            }
            return null
        }

        private fun NextInstruction(position: Int) {
            // Before we execute an instruction, we increment instructionPointer,
            // therefore we need to compensate for that here.
            instructionPointer = position - 1
        }

        private fun LeaveFunction(returnDef: idVarDef?) {
            val stack: prstack_s?
            val ret: varEval_s?
            if (callStackDepth <= 0) {
                Error("prog stack underflow")
            }

            // return value
            if (returnDef != null) {
                when (returnDef.Type()) {
                    Script_Program.ev_string -> Game_local.gameLocal.program.ReturnString(GetString(returnDef))
                    Script_Program.ev_vector -> {
                        ret = GetVariable(returnDef)
                        Game_local.gameLocal.program.ReturnVector(ret!!.getVectorPtrs())
                    }

                    else -> {
                        ret = GetVariable(returnDef)
                        Game_local.gameLocal.program.ReturnInteger(ret!!.intPtr)
                    }
                }
            }

            // remove locals from the stack
            PopParms(currentFunction!!.locals)
            assert(localstackUsed == localstackBase)
            if (debug) {
                val line = Game_local.gameLocal.program.GetStatement(instructionPointer)
                Game_local.gameLocal.Printf(
                    "%d: %s(%d): exit %s", Game_local.gameLocal.time, Game_local.gameLocal.program.GetFilename(
                        line.file
                    ), line.linenumber, currentFunction!!.Name()
                )
                if (callStackDepth > 1) {
                    Game_local.gameLocal.Printf(
                        " return to %s(line %d)\n",
                        callStack[callStackDepth - 1]!!.f!!.Name(),
                        Game_local.gameLocal.program.GetStatement(
                            callStack[callStackDepth - 1]!!.s
                        ).linenumber
                    )
                } else {
                    Game_local.gameLocal.Printf(" done\n")
                }
            }

            // up stack
            callStackDepth--
            stack = callStack[callStackDepth]
            currentFunction = stack!!.f
            localstackBase = stack.stackbase
            NextInstruction(stack.s)
            if (0 == callStackDepth) {
                // all done
                doneProcessing = true
                threadDying = true
                currentFunction = null
            }
        }

        private fun CallEvent(func: function_t, argsize: Int) {
            var i: Int
            var j: Int
            val `var` = varEval_s()
            var pos: Int
            val start: Int
            val data = arrayOfNulls<idEventArg<*>?>(D_EVENT_MAXARGS)
            val evdef: idEventDef?
            val format: CharArray
            if (func == null) {
                Error("NULL function")
            }
            assert(func.eventdef != null)
            evdef = func.eventdef
            start = localstackUsed - argsize
            `var`.setIntPtr(localstack, start)
            eventEntity = GetEntity(`var`.entityNumberPtr)
            if (null == eventEntity || !eventEntity!!.RespondsTo(evdef!!)) {
                if (eventEntity != null && Common.com_developer.GetBool()) {
                    // give a warning in developer mode
                    Warning(
                        "Function '%s' not supported on entity '%s'",
                        evdef!!.GetName(),
                        eventEntity!!.name.toString()
                    )
                }
                when (evdef!!.GetReturnType()) {
                    D_EVENT_INTEGER -> Game_local.gameLocal.program.ReturnInteger(0)
                    D_EVENT_FLOAT -> Game_local.gameLocal.program.ReturnFloat(0.0f)
                    D_EVENT_VECTOR -> Game_local.gameLocal.program.ReturnVector(idVec3())
                    D_EVENT_STRING -> Game_local.gameLocal.program.ReturnString("")
                    D_EVENT_ENTITY, D_EVENT_ENTITY_NULL -> Game_local.gameLocal.program.ReturnEntity(
                        null
                    )

                    D_EVENT_TRACE -> {}
                    else -> {}
                }
                PopParms(argsize)
                eventEntity = null
                return
            }
            format = evdef.GetArgFormat()!!.toCharArray()
            j = 0
            i = 0
            pos = Script_Program.type_object.Size()
            while (pos < argsize || i < format.size && format[i].code != 0) {
                when (format[i]) {
                    D_EVENT_INTEGER -> {
                        `var`.setIntPtr(localstack, start + pos)
                        data[i] = toArg(`var`.floatPtr.toInt())
                    }

                    D_EVENT_FLOAT -> {
                        `var`.setIntPtr(localstack, start + pos)
                        data[i] = toArg(`var`.floatPtr)
                    }

                    D_EVENT_VECTOR -> {
                        `var`.setIntPtr(localstack, start + pos)
                        data[i] = toArg(`var`.getVectorPtrs())
                    }

                    D_EVENT_STRING -> data[i] = toArg(
                        btos(
                            localstack,
                            start + pos
                        )
                    ) //( *( const char ** )&data[ i ] ) = ( char * )&localstack[ start + pos ];
                    D_EVENT_ENTITY -> {
                        `var`.setIntPtr(localstack, start + pos)
                        data[i] = toArg(GetEntity(`var`.entityNumberPtr))
                        if ((data[i] as idEventArg<*>).value == null) {
                            Warning("Entity not found for event '%s'. Terminating thread.", evdef.GetName())
                            threadDying = true
                            PopParms(argsize)
                            return
                        }
                    }

                    D_EVENT_ENTITY_NULL -> {
                        `var`.setIntPtr(localstack, start + pos)
                        data[i] = toArg(GetEntity(`var`.entityNumberPtr))
                    }

                    D_EVENT_TRACE -> Error(
                        "trace type not supported from script for '%s' event.",
                        evdef.GetName()
                    )

                    else -> Error("Invalid arg format string for '%s' event.", evdef.GetName())
                }
                pos += func.parmSize[j++]
                i++
            }
            popParms = argsize
            eventEntity!!.ProcessEventArgPtr(evdef, data)
            if (null == multiFrameEvent) {
                if (popParms != 0) {
                    PopParms(popParms)
                }
                eventEntity = null
            } else {
                doneProcessing = true
            }
            popParms = 0
        }

        private fun CallSysEvent(func: function_t?, argsize: Int) {
            var i: Int
            var j: Int
            val source = varEval_s()
            var pos: Int
            val start: Int
            val data = arrayOfNulls<idEventArg<*>?>(D_EVENT_MAXARGS)
            val evdef: idEventDef?
            val format: String?
            if (func == null) {
                Error("NULL function")
            }
            assert(func!!.eventdef != null)
            evdef = func.eventdef
            start = localstackUsed - argsize
            format = evdef!!.GetArgFormat()
            j = 0
            i = 0
            pos = 0
            while (pos < argsize || i < format!!.length) {
                when (format!![i]) {
                    D_EVENT_INTEGER -> {
                        source.setIntPtr(localstack, start + pos)
                        data[i] = toArg(source.floatPtr.toInt())
                    }

                    D_EVENT_FLOAT -> {
                        source.setIntPtr(localstack, start + pos)
                        data[i] = toArg(source.floatPtr)
                    }

                    D_EVENT_VECTOR -> {
                        source.setIntPtr(localstack, start + pos)
                        data[i] = toArg(source.getVectorPtrs())
                    }

                    D_EVENT_STRING -> data[i] = toArg(btos(localstack, start + pos))
                    D_EVENT_ENTITY -> {
                        source.setIntPtr(localstack, start + pos)
                        data[i] = toArg(GetEntity(source.entityNumberPtr))
                        if ((data[i] as idEventArg<*>).value == null) {
                            Warning("Entity not found for event '%s'. Terminating thread.", evdef.GetName())
                            threadDying = true
                            PopParms(argsize)
                            return
                        }
                    }

                    D_EVENT_ENTITY_NULL -> {
                        source.setIntPtr(localstack, start + pos)
                        data[i] = toArg(GetEntity(source.entityNumberPtr))
                    }

                    D_EVENT_TRACE -> Error(
                        "trace type not supported from script for '%s' event.",
                        evdef.GetName()
                    )

                    else -> Error("Invalid arg format string for '%s' event.", evdef.GetName())
                }
                pos += func.parmSize[j++]
                i++
            }

//            throw new TODO_Exception();
            popParms = argsize
            thread!!.ProcessEventArgPtr(evdef, data)
            if (popParms != 0) {
                PopParms(popParms)
            }
            popParms = 0
        }

        // save games
        fun Save(savefile: idSaveGame) {                // archives object for save game file
            var i: Int
            savefile.WriteInt(callStackDepth)
            i = 0
            while (i < callStackDepth) {
                savefile.WriteInt(callStack[i]!!.s)
                if (callStack[i]!!.f != null) {
                    savefile.WriteInt(Game_local.gameLocal.program.GetFunctionIndex(callStack[i]!!.f!!))
                } else {
                    savefile.WriteInt(-1)
                }
                savefile.WriteInt(callStack[i]!!.stackbase)
                i++
            }
            savefile.WriteInt(maxStackDepth)
            savefile.WriteInt(localstackUsed)
            savefile.Write(ByteBuffer.wrap(localstack, 0, localstackUsed), localstackUsed)
            savefile.WriteInt(localstackBase)
            savefile.WriteInt(maxLocalstackUsed)
            if (currentFunction != null) {
                savefile.WriteInt(Game_local.gameLocal.program.GetFunctionIndex(currentFunction!!))
            } else {
                savefile.WriteInt(-1)
            }
            savefile.WriteInt(instructionPointer)
            savefile.WriteInt(popParms)
            if (multiFrameEvent != null) {
                savefile.WriteString(multiFrameEvent!!.GetName())
            } else {
                savefile.WriteString("")
            }
            savefile.WriteObject(eventEntity)
            savefile.WriteObject(thread)
            savefile.WriteBool(doneProcessing)
            savefile.WriteBool(threadDying)
            savefile.WriteBool(terminateOnExit)
            savefile.WriteBool(debug)
        }

        fun Restore(savefile: idRestoreGame) {                // unarchives object from save game file
            var i: Int
            val funcname = idStr()
            val func_index = CInt()
            callStackDepth = savefile.ReadInt()
            i = 0
            while (i < callStackDepth) {
                callStack[i] = prstack_s()
                callStack[i]!!.s = savefile.ReadInt()
                savefile.ReadInt(func_index)
                if (func_index._val >= 0) {
                    callStack[i]!!.f = Game_local.gameLocal.program.GetFunction(func_index._val)
                } else {
                    callStack[i]!!.f = null
                }
                callStack[i]!!.stackbase = savefile.ReadInt()
                i++
            }
            maxStackDepth = savefile.ReadInt()
            localstackUsed = savefile.ReadInt()
            savefile.Read(ByteBuffer.wrap(localstack, 0, localstackUsed), localstackUsed)
            localstackBase = savefile.ReadInt()
            maxLocalstackUsed = savefile.ReadInt()
            savefile.ReadInt(func_index)
            currentFunction = if (func_index._val >= 0) {
                Game_local.gameLocal.program.GetFunction(func_index._val)
            } else {
                null
            }
            instructionPointer = savefile.ReadInt()
            popParms = savefile.ReadInt()
            savefile.ReadString(funcname)
            if (funcname.Length() != 0) {
                multiFrameEvent = FindEvent(funcname.toString())
            }
            eventEntity = savefile.ReadObject() as idEntity?
            thread = savefile.ReadObject() as idThread?
            doneProcessing = savefile.ReadBool()
            threadDying = savefile.ReadBool()
            terminateOnExit = savefile.ReadBool()
            debug = savefile.ReadBool()
        }

        fun SetThread(pThread: idThread?) {
            thread = pThread
        }

        fun StackTrace() {
            var f: function_t?
            var i: Int
            var top: Int
            if (callStackDepth == 0) {
                Game_local.gameLocal.Printf("<NO STACK>\n")
                return
            }
            top = callStackDepth
            if (top >= MAX_STACK_DEPTH) {
                top = MAX_STACK_DEPTH - 1
            }
            if (currentFunction == null) {
                Game_local.gameLocal.Printf("<NO FUNCTION>\n")
            } else {
                Game_local.gameLocal.Printf(
                    "%12s : %s\n", Game_local.gameLocal.program.GetFilename(
                        currentFunction!!.filenum
                    ), currentFunction!!.Name()
                )
            }
            i = top - 1
            while (i >= 0) {
                f = callStack[i]!!.f
                if (f == null) {
                    Game_local.gameLocal.Printf("<NO FUNCTION>\n")
                } else {
                    Game_local.gameLocal.Printf(
                        "%12s : %s\n", Game_local.gameLocal.program.GetFilename(
                            f.filenum
                        ), f.Name()
                    )
                }
                i--
            }
        }

        fun CurrentLine(): Int {
            return if (instructionPointer < 0) {
                0
            } else Game_local.gameLocal.program.GetLineNumberForStatement(instructionPointer)
        }

        fun CurrentFile(): String? {
            return if (instructionPointer < 0) {
                ""
            } else Game_local.gameLocal.program.GetFilenameForStatement(instructionPointer)
        }

        /*
         ============
         idInterpreter::Error

         Aborts the currently executing function
         ============
         */
        fun Error(fmt: String, vararg objects: Any?) { // id_attribute((format(printf,2,3)));
            val text = String.format(fmt, *objects)
            StackTrace()
            if (instructionPointer >= 0 && instructionPointer < Game_local.gameLocal.program.NumStatements()) {
                val line = Game_local.gameLocal.program.GetStatement(instructionPointer)
                Common.common.Error(
                    "%s(%d): Thread '%s': %s\n", Game_local.gameLocal.program.GetFilename(
                        line.file
                    ), line.linenumber, thread!!.GetThreadName(), text
                )
            } else {
                Common.common.Error("Thread '%s': %s\n", thread!!.GetThreadName(), text)
            }
        }

        /*
         ============
         idInterpreter::Warning

         Prints file and line number information with warning.
         ============
         */
        fun Warning(fmt: String?, vararg objects: Any?) { // id_attribute((format(printf,2,3)));
            val text = String.format(fmt!!, *objects)
            if (instructionPointer >= 0 && instructionPointer < Game_local.gameLocal.program.NumStatements()) {
                val line = Game_local.gameLocal.program.GetStatement(instructionPointer)
                Common.common.Warning(
                    "%s(%d): Thread '%s': %s", Game_local.gameLocal.program.GetFilename(
                        line.file
                    ), line.linenumber, thread!!.GetThreadName(), text
                )
            } else {
                Common.common.Warning("Thread '%s' : %s", thread!!.GetThreadName(), text)
            }
        }

        fun DisplayInfo() {
            var f: function_t?
            var i: Int
            Game_local.gameLocal.Printf(" Stack depth: %d bytes, %d max\n", localstackUsed, maxLocalstackUsed)
            Game_local.gameLocal.Printf("  Call depth: %d, %d max\n", callStackDepth, maxStackDepth)
            Game_local.gameLocal.Printf("  Call Stack: ")
            if (callStackDepth == 0) {
                Game_local.gameLocal.Printf("<NO STACK>\n")
            } else {
                if (currentFunction == null) {
                    Game_local.gameLocal.Printf("<NO FUNCTION>\n")
                } else {
                    Game_local.gameLocal.Printf(
                        "%12s : %s\n", Game_local.gameLocal.program.GetFilename(
                            currentFunction!!.filenum
                        ), currentFunction!!.Name()
                    )
                }
                i = callStackDepth
                while (i > 0) {
                    Game_local.gameLocal.Printf("              ")
                    f = callStack[i]!!.f
                    if (f == null) {
                        Game_local.gameLocal.Printf("<NO FUNCTION>\n")
                    } else {
                        Game_local.gameLocal.Printf(
                            "%12s : %s\n", Game_local.gameLocal.program.GetFilename(
                                f.filenum
                            ), f.Name()
                        )
                    }
                    i--
                }
            }
        }

        fun BeginMultiFrameEvent(ent: idEntity, event: idEventDef?): Boolean {
            if (eventEntity != ent) {
                Error("idInterpreter::BeginMultiFrameEvent called with wrong entity")
            }
            if (multiFrameEvent != null) {
                if (!multiFrameEvent!!.equals(event)) {
                    Error("idInterpreter::BeginMultiFrameEvent called with wrong event")
                }
                return false
            }
            multiFrameEvent = event
            return true
        }

        fun EndMultiFrameEvent(ent: idEntity?, event: idEventDef?) {
            if (!multiFrameEvent!!.equals(event)) {
                Error("idInterpreter::EndMultiFrameEvent called with wrong event")
            }
            multiFrameEvent = null
        }

        fun MultiFrameEventInProgress(): Boolean {
            return multiFrameEvent != null
        }

        /*
         ====================
         idInterpreter::ThreadCall

         Copys the args from the calling thread's stack
         ====================
         */
        fun ThreadCall(source: idInterpreter, func: function_t, args: Int) {
            Reset()

//	memcpy( localstack, &source.localstack[ source.localstackUsed - args ], args );
            System.arraycopy(source.localstack, source.localstackUsed - args, localstack, 0, args)
            localstackUsed = args
            localstackBase = 0
            maxLocalstackUsed = localstackUsed
            EnterFunction(func, false)
            thread!!.SetThreadName(currentFunction!!.Name())
        }

        /*
         ====================
         idInterpreter::EnterFunction

         Returns the new program statement counter

         NOTE: If this is called from within a event called by this interpreter, the function arguments will be invalid after calling this function.
         ====================
         */
        fun EnterFunction(func: function_t, clearStack: Boolean) {
            val c: Int
            val stack: prstack_s?
            if (clearStack) {
                Reset()
            }
            if (popParms != 0) {
                PopParms(popParms)
                popParms = 0
            }
            if (callStackDepth >= MAX_STACK_DEPTH) {
                Error("call stack overflow")
            }
            callStack[callStackDepth] = prstack_s()
            stack = callStack[callStackDepth]
            stack!!.s = instructionPointer + 1 // point to the next instruction to execute
            stack.f = currentFunction
            stack.stackbase = localstackBase
            callStackDepth++
            if (callStackDepth > maxStackDepth) {
                maxStackDepth = callStackDepth
            }
            if (func == null) {
                Error("NULL function")
            }
            if (debug) {
                if (currentFunction != null) {
                    Game_local.gameLocal.Printf(
                        "%d: call '%s' from '%s'(line %d)%s\n",
                        Game_local.gameLocal.time,
                        func.Name(),
                        currentFunction!!.Name(),
                        Game_local.gameLocal.program.GetStatement(instructionPointer).linenumber,
                        if (clearStack) " clear stack" else ""
                    )
                } else {
                    Game_local.gameLocal.Printf(
                        "%d: call '%s'%s\n",
                        Game_local.gameLocal.time,
                        func.Name(),
                        if (clearStack) " clear stack" else ""
                    )
                }
            }
            currentFunction = func
            assert(func.eventdef == null)
            NextInstruction(func.firstStatement)

            // allocate space on the stack for locals
            // parms are already on stack
            c = func.locals - func.parmTotal
            assert(c >= 0)
            if (localstackUsed + c > LOCALSTACK_SIZE) {
                Error("EnterFuncton: locals stack overflow\n")
            }

            // initialize local stack variables to zero
            //	memset( &localstack[ localstackUsed ], 0, c );
            Arrays.fill(localstack, localstackUsed, localstackUsed + c, 0.toByte())
            localstackUsed += c
            localstackBase = localstackUsed - func.locals
            if (localstackUsed > maxLocalstackUsed) {
                maxLocalstackUsed = localstackUsed
            }
        }

        /*
         ================
         idInterpreter::EnterObjectFunction

         Calls a function on a script object.

         NOTE: If this is called from within a event called by this interpreter, the function arguments will be invalid after calling this function.
         ================
         */
        fun EnterObjectFunction(self: idEntity?, func: function_t, clearStack: Boolean) {
            if (clearStack) {
                Reset()
            }
            if (popParms != 0) {
                PopParms(popParms)
                popParms = 0
            }
            Push(self!!.entityNumber + 1)
            EnterFunction(func, false)
        }

        fun Execute(): Boolean {
            DBG_Execute++
            var var_a: varEval_s? = varEval_s()
            var var_b: varEval_s?
            var var_c: varEval_s?
            var `var`: varEval_s? = varEval_s()
            var st: statement_s?
            var runaway: Int
            var newThread: idThread
            var floatVal: Float
            var obj: idScriptObject?
            var func: function_t?
            //            System.out.println(instructionPointer);
            if (threadDying || currentFunction == null) {
                return true
            }
            if (multiFrameEvent != null) {
                // move to previous instruction and call it again
                instructionPointer--
            }
            runaway = 5000000
            doneProcessing = false
            while (!doneProcessing && !threadDying) {
                instructionPointer++
                if (0 == --runaway) {
                    Error("runaway loop error")
                }

                // next statement
                st = Game_local.gameLocal.program.GetStatement(instructionPointer)
                when (st.op) {
                    OP_RETURN -> LeaveFunction(st.a)
                    OP_THREAD -> {
                        newThread = idThread(this, st.a!!.value!!.functionPtr!!, st.b!!.value!!.argSize)
                        newThread.Start()

                        // return the thread number to the script
                        Game_local.gameLocal.program.ReturnFloat(newThread.GetThreadNum().toFloat())
                        PopParms(st.b!!.value!!.argSize)
                    }

                    OP_OBJTHREAD -> {
                        var_a = GetVariable(st.a)
                        obj = GetScriptObject(var_a!!.entityNumberPtr)
                        if (obj != null) {
                            func = obj.GetTypeDef().GetFunction(st.b!!.value!!.virtualFunction)
                            assert(st.c!!.value!!.argSize == func!!.parmTotal)
                            newThread = idThread(this, GetEntity(var_a.entityNumberPtr), func, func.parmTotal)
                            newThread.Start()

                            // return the thread number to the script
                            Game_local.gameLocal.program.ReturnFloat(newThread.GetThreadNum().toFloat())
                        } else {
                            // return a null thread to the script
                            Game_local.gameLocal.program.ReturnFloat(0.0f)
                        }
                        PopParms(st.c!!.value!!.argSize)
                    }

                    OP_CALL -> EnterFunction(st.a!!.value!!.functionPtr!!, false)
                    OP_EVENTCALL -> CallEvent(st.a!!.value!!.functionPtr!!, st.b!!.value!!.argSize)
                    OP_OBJECTCALL -> {
                        var_a = GetVariable(st.a)
                        obj = GetScriptObject(var_a!!.entityNumberPtr)
                        if (obj != null) {
                            func = obj.GetTypeDef().GetFunction(st.b!!.value!!.virtualFunction)!!
                            EnterFunction(func, false)
                        } else {
                            // return a 'safe' value
                            Game_local.gameLocal.program.ReturnVector(idVec3())
                            Game_local.gameLocal.program.ReturnString("")
                            PopParms(st.c!!.value!!.argSize)
                        }
                    }

                    OP_SYSCALL -> CallSysEvent(st.a!!.value!!.functionPtr, st.b!!.value!!.argSize)
                    OP_IFNOT -> {
                        var_a = GetVariable(st.a)
                        if (var_a!!.intPtr == 0) {
                            NextInstruction(instructionPointer + st.b!!.value!!.jumpOffset)
                        }
                    }

                    OP_IF -> {
                        var_a = GetVariable(st.a)
                        if (var_a!!.intPtr != 0) {
                            NextInstruction(instructionPointer + st.b!!.value!!.jumpOffset)
                        }
                    }

                    OP_GOTO -> NextInstruction(instructionPointer + st.a!!.value!!.jumpOffset)
                    OP_ADD_F -> {
                        var_a = GetVariable(st.a)
                        var_b = GetVariable(st.b)
                        var_c = GetVariable(st.c)
                        var_c!!.floatPtr = var_a!!.floatPtr + var_b!!.floatPtr
                    }

                    OP_ADD_V -> {
                        var_a = GetVariable(st.a)
                        var_b = GetVariable(st.b)
                        var_c = GetVariable(st.c)
                        var_c!!.setVectorPtr(var_a!!.getVectorPtrs().plus(var_b!!.getVectorPtrs()))
                    }

                    OP_ADD_S -> {
                        SetString(st.c, GetString(st.a))
                        AppendString(st.c, GetString(st.b))
                    }

                    OP_ADD_FS -> {
                        var_a = GetVariable(st.a)
                        SetString(st.c, FloatToString(var_a!!.floatPtr))
                        AppendString(st.c, GetString(st.b))
                    }

                    OP_ADD_SF -> {
                        var_b = GetVariable(st.b)
                        SetString(st.c, GetString(st.a))
                        AppendString(st.c, FloatToString(var_b!!.floatPtr))
                    }

                    OP_ADD_VS -> {
                        var_a = GetVariable(st.a)
                        SetString(st.c, var_a!!.getVectorPtrs().ToString())
                        AppendString(st.c, GetString(st.b))
                    }

                    OP_ADD_SV -> {
                        var_b = GetVariable(st.b)
                        SetString(st.c, GetString(st.a))
                        AppendString(st.c, var_b!!.getVectorPtrs().ToString())
                    }

                    OP_SUB_F -> {
                        var_a = GetVariable(st.a)
                        var_b = GetVariable(st.b)
                        var_c = GetVariable(st.c)
                        var_c!!.floatPtr = (var_a!!.floatPtr - var_b!!.floatPtr)
                    }

                    OP_SUB_V -> {
                        var_a = GetVariable(st.a)
                        var_b = GetVariable(st.b)
                        var_c = GetVariable(st.c)
                        var_c!!.setVectorPtr(var_a!!.getVectorPtrs().minus(var_b!!.getVectorPtrs()))
                    }

                    OP_MUL_F -> {
                        var_a = GetVariable(st.a)
                        var_b = GetVariable(st.b)
                        var_c = GetVariable(st.c)
                        var_c!!.floatPtr = (var_a!!.floatPtr * var_b!!.floatPtr)
                    }

                    OP_MUL_V -> {
                        var_a = GetVariable(st.a)
                        var_b = GetVariable(st.b)
                        var_c = GetVariable(st.c)
                        var_c!!.floatPtr = (var_a!!.getVectorPtrs().times(var_b!!.getVectorPtrs()))
                    }

                    OP_MUL_FV -> {
                        var_a = GetVariable(st.a)
                        var_b = GetVariable(st.b)
                        var_c = GetVariable(st.c)
                        var_c!!.setVectorPtr(var_b!!.getVectorPtrs().times(var_a!!.floatPtr))
                    }

                    OP_MUL_VF -> {
                        var_a = GetVariable(st.a)
                        var_b = GetVariable(st.b)
                        var_c = GetVariable(st.c)
                        var_c!!.getVectorPtrs().set(var_a!!.getVectorPtrs().times(var_b!!.floatPtr))
                    }

                    OP_DIV_F -> {
                        var_a = GetVariable(st.a)
                        var_b = GetVariable(st.b)
                        var_c = GetVariable(st.c)
                        if (var_b!!.floatPtr == 0.0f) {
                            Warning("Divide by zero")
                            var_c!!.floatPtr = (idMath.INFINITY)
                        } else {
                            var_c!!.floatPtr = (var_a!!.floatPtr / var_b.floatPtr)
                        }
                    }

                    OP_MOD_F -> {
                        var_a = GetVariable(st.a)
                        var_b = GetVariable(st.b)
                        var_c = GetVariable(st.c)
                        if (var_b!!.floatPtr == 0.0f) {
                            Warning("Divide by zero")
                            var_c!!.floatPtr = (var_a!!.floatPtr)
                        } else {
                            var_c!!.floatPtr = (
                                    (var_a!!.floatPtr.toInt() % var_b.floatPtr.toInt()).toFloat()
                                    ) //TODO:casts!
                        }
                    }

                    OP_BITAND -> {
                        var_a = GetVariable(st.a)
                        var_b = GetVariable(st.b)
                        var_c = GetVariable(st.c)
                        var_c!!.floatPtr = ((var_a!!.floatPtr.toInt() and var_b!!.floatPtr.toInt()).toFloat())
                    }

                    OP_BITOR -> {
                        var_a = GetVariable(st.a)
                        var_b = GetVariable(st.b)
                        var_c = GetVariable(st.c)
                        var_c!!.floatPtr = ((var_a!!.floatPtr.toInt() or var_b!!.floatPtr.toInt()).toFloat())
                    }

                    OP_GE -> {
                        var_a = GetVariable(st.a)
                        var_b = GetVariable(st.b)
                        var_c = GetVariable(st.c)
                        var_c!!.floatPtr = (btoi(var_a!!.floatPtr >= var_b!!.floatPtr).toFloat())
                    }

                    OP_LE -> {
                        var_a = GetVariable(st.a)
                        var_b = GetVariable(st.b)
                        var_c = GetVariable(st.c)
                        var_c!!.floatPtr = (btoi(var_a!!.floatPtr <= var_b!!.floatPtr).toFloat())
                    }

                    OP_GT -> {
                        var_a = GetVariable(st.a)
                        var_b = GetVariable(st.b)
                        var_c = GetVariable(st.c)
                        var_c!!.floatPtr = (btoi(var_a!!.floatPtr > var_b!!.floatPtr).toFloat())
                    }

                    OP_LT -> {
                        var_a = GetVariable(st.a)
                        var_b = GetVariable(st.b)
                        var_c = GetVariable(st.c)
                        var_c!!.floatPtr = (btoi(var_a!!.floatPtr < var_b!!.floatPtr).toFloat())
                    }

                    OP_AND -> {
                        var_a = GetVariable(st.a)
                        var_b = GetVariable(st.b)
                        var_c = GetVariable(st.c)
                        var_c!!.floatPtr = (btoi(var_a!!.floatPtr != 0.0f && var_b!!.floatPtr != 0.0f).toFloat())
                    }

                    OP_AND_BOOLF -> {
                        var_a = GetVariable(st.a)
                        var_b = GetVariable(st.b)
                        var_c = GetVariable(st.c)
                        var_c!!.floatPtr = (btoi(var_a!!.intPtr != 0 && var_b!!.floatPtr != 0.0f).toFloat())
                    }

                    OP_AND_FBOOL -> {
                        var_a = GetVariable(st.a)
                        var_b = GetVariable(st.b)
                        var_c = GetVariable(st.c)
                        var_c!!.floatPtr = (btoi(var_a!!.floatPtr != 0.0f && var_b!!.intPtr != 0).toFloat())
                    }

                    OP_AND_BOOLBOOL -> {
                        var_a = GetVariable(st.a)
                        var_b = GetVariable(st.b)
                        var_c = GetVariable(st.c)
                        var_c!!.floatPtr = (btoi(var_a!!.intPtr != 0 && var_b!!.intPtr != 0).toFloat())
                    }

                    OP_OR -> {
                        var_a = GetVariable(st.a)
                        var_b = GetVariable(st.b)
                        var_c = GetVariable(st.c)
                        var_c!!.floatPtr = (btoi(var_a!!.floatPtr != 0.0f || var_b!!.floatPtr != 0.0f).toFloat())
                    }

                    OP_OR_BOOLF -> {
                        var_a = GetVariable(st.a)
                        var_b = GetVariable(st.b)
                        var_c = GetVariable(st.c)
                        var_c!!.floatPtr = (btoi(var_a!!.intPtr != 0 || var_b!!.floatPtr != 0.0f).toFloat())
                    }

                    OP_OR_FBOOL -> {
                        var_a = GetVariable(st.a)
                        var_b = GetVariable(st.b)
                        var_c = GetVariable(st.c)
                        var_c!!.floatPtr = (btoi(var_a!!.floatPtr != 0.0f || var_b!!.intPtr != 0).toFloat())
                    }

                    OP_OR_BOOLBOOL -> {
                        var_a = GetVariable(st.a)
                        var_b = GetVariable(st.b)
                        var_c = GetVariable(st.c)
                        var_c!!.floatPtr = (btoi(var_a!!.intPtr != 0 || var_b!!.intPtr != 0).toFloat())
                    }

                    OP_NOT_BOOL -> {
                        var_a = GetVariable(st.a)
                        var_c = GetVariable(st.c)
                        var_c!!.floatPtr = (btoi(var_a!!.intPtr == 0).toFloat())
                    }

                    OP_NOT_F -> {
                        var_a = GetVariable(st.a)
                        var_c = GetVariable(st.c)
                        var_c!!.floatPtr = (btoi(var_a!!.floatPtr == 0.0f).toFloat())
                    }

                    OP_NOT_V -> {
                        var_a = GetVariable(st.a)
                        var_c = GetVariable(st.c)
                        var_c!!.floatPtr = (btoi(var_a!!.getVectorPtrs().equals(idVec3())).toFloat())
                    }

                    OP_NOT_S -> {
                        var_c = GetVariable(st.c)
                        var_c!!.floatPtr = (btoi(GetString(st.a).isNullOrEmpty()).toFloat())
                    }

                    OP_NOT_ENT -> {
                        var_a = GetVariable(st.a)
                        var_c = GetVariable(st.c)
                        var_c!!.floatPtr = (btoi(GetEntity(var_a!!.entityNumberPtr) == null).toFloat())
                    }

                    OP_NEG_F -> {
                        var_a = GetVariable(st.a)
                        var_c = GetVariable(st.c)
                        var_c!!.floatPtr = (-var_a!!.floatPtr)
                    }

                    OP_NEG_V -> {
                        var_a = GetVariable(st.a)
                        var_c = GetVariable(st.c)
                        var_c!!.setVectorPtr(var_a!!.getVectorPtrs().unaryMinus())
                    }

                    OP_INT_F -> {
                        var_a = GetVariable(st.a)
                        var_c = GetVariable(st.c)
                        var_c!!.floatPtr = (var_a!!.floatPtr)
                    }

                    OP_EQ_F -> {
                        var_a = GetVariable(st.a)
                        var_b = GetVariable(st.b)
                        var_c = GetVariable(st.c)
                        var_c!!.floatPtr = (btoi(var_a!!.floatPtr == var_b!!.floatPtr).toFloat())
                    }

                    OP_EQ_V -> {
                        var_a = GetVariable(st.a)
                        var_b = GetVariable(st.b)
                        var_c = GetVariable(st.c)
                        var_c!!.floatPtr = (btoi(var_a!!.getVectorPtrs().equals(var_b!!.getVectorPtrs())).toFloat())
                    }

                    OP_EQ_S -> {
                        var_a = GetVariable(st.a)
                        var_b = GetVariable(st.b)
                        var_c = GetVariable(st.c)
                        var_c!!.floatPtr = (btoi(Cmp(GetString(st.a)!!, GetString(st.b)!!) == 0).toFloat())
                    }

                    OP_EQ_E, OP_EQ_EO, OP_EQ_OE, OP_EQ_OO -> {
                        var_a = GetVariable(st.a)
                        var_b = GetVariable(st.b)
                        var_c = GetVariable(st.c)
                        var_c!!.floatPtr = (btoi(var_a!!.entityNumberPtr == var_b!!.entityNumberPtr).toFloat())
                    }

                    OP_NE_F -> {
                        var_a = GetVariable(st.a)
                        var_b = GetVariable(st.b)
                        var_c = GetVariable(st.c)
                        var_c!!.floatPtr = (btoi(var_a!!.floatPtr != var_b!!.floatPtr).toFloat())
                    }

                    OP_NE_V -> {
                        var_a = GetVariable(st.a)
                        var_b = GetVariable(st.b)
                        var_c = GetVariable(st.c)
                        var_c!!.floatPtr = (btoi(!var_a!!.getVectorPtrs().equals(var_b!!.getVectorPtrs())).toFloat())
                    }

                    OP_NE_S -> {
                        var_c = GetVariable(st.c)
                        var_c!!.floatPtr = (btoi(Cmp(GetString(st.a)!!, GetString(st.b)!!) != 0).toFloat())
                    }

                    OP_NE_E, OP_NE_EO, OP_NE_OE, OP_NE_OO -> {
                        var_a = GetVariable(st.a)
                        var_b = GetVariable(st.b)
                        var_c = GetVariable(st.c)
                        var_c!!.floatPtr = (btoi(var_a!!.entityNumberPtr != var_b!!.entityNumberPtr).toFloat())
                    }

                    OP_UADD_F -> {
                        var_a = GetVariable(st.a)
                        var_b = GetVariable(st.b)
                        var_b!!.floatPtr = (var_b.floatPtr + var_a!!.floatPtr)
                    }

                    OP_UADD_V -> {
                        var_a = GetVariable(st.a)
                        var_b = GetVariable(st.b)
                        var_b!!.setVectorPtr(var_b.getVectorPtrs().plus(var_a!!.getVectorPtrs()))
                    }

                    OP_USUB_F -> {
                        var_a = GetVariable(st.a)
                        var_b = GetVariable(st.b)
                        var_b!!.floatPtr = (var_b.floatPtr - var_a!!.floatPtr)
                    }

                    OP_USUB_V -> {
                        var_a = GetVariable(st.a)
                        var_b = GetVariable(st.b)
                        var_b!!.setVectorPtr(var_b.getVectorPtrs().minus(var_a!!.getVectorPtrs()))
                    }

                    OP_UMUL_F -> {
                        var_a = GetVariable(st.a)
                        var_b = GetVariable(st.b)
                        var_b!!.floatPtr = (var_b.floatPtr * var_a!!.floatPtr)
                    }

                    OP_UMUL_V -> {
                        var_a = GetVariable(st.a)
                        var_b = GetVariable(st.b)
                        var_b!!.setVectorPtr(var_b.getVectorPtrs().times(var_a!!.floatPtr))
                    }

                    OP_UDIV_F -> {
                        var_a = GetVariable(st.a)
                        var_b = GetVariable(st.b)
                        if (var_a!!.floatPtr == 0.0f) {
                            Warning("Divide by zero")
                            var_b!!.floatPtr = (idMath.INFINITY)
                        } else {
                            var_b!!.floatPtr = (var_b.floatPtr / var_a.floatPtr)
                        }
                    }

                    OP_UDIV_V -> {
                        var_a = GetVariable(st.a)
                        var_b = GetVariable(st.b)
                        if (var_a!!.floatPtr == 0.0f) {
                            Warning("Divide by zero")
                            var_b!!.setVectorPtr(floatArrayOf(idMath.INFINITY, idMath.INFINITY, idMath.INFINITY))
                        } else {
                            var_b!!.setVectorPtr(var_b.getVectorPtrs().div(var_a.floatPtr))
                        }
                    }

                    OP_UMOD_F -> {
                        var_a = GetVariable(st.a)
                        var_b = GetVariable(st.b)
                        if (var_a!!.floatPtr == 0.0f) {
                            Warning("Divide by zero")
                            var_b!!.floatPtr = (var_a.floatPtr)
                        } else {
                            var_b!!.floatPtr = ((var_b.floatPtr.toInt() % var_a.floatPtr.toInt()).toFloat())
                        }
                    }

                    OP_UOR_F -> {
                        var_a = GetVariable(st.a)
                        var_b = GetVariable(st.b)
                        var_b!!.floatPtr = ((var_b.floatPtr.toInt() or var_a!!.floatPtr.toInt()).toFloat())
                    }

                    OP_UAND_F -> {
                        var_a = GetVariable(st.a)
                        var_b = GetVariable(st.b)
                        var_b!!.floatPtr = ((var_b.floatPtr.toInt() and var_a!!.floatPtr.toInt()).toFloat())
                    }

                    OP_UINC_F -> {
                        var_a = GetVariable(st.a)
                        var_a!!.floatPtr = (var_a.floatPtr + 1)
                    }

                    OP_UINCP_F -> {
                        var_a = GetVariable(st.a)
                        obj = GetScriptObject(var_a!!.entityNumberPtr)
                        if (obj != null) {
                            `var`!!.setBytePtr(obj.data, st.b!!.value!!.ptrOffset)
                            `var`.floatPtr = `var`.floatPtr + 1
                        }
                    }

                    OP_UDEC_F -> {
                        var_a = GetVariable(st.a)
                        var_a!!.floatPtr = (var_a.floatPtr - 1)
                    }

                    OP_UDECP_F -> {
                        var_a = GetVariable(st.a)
                        obj = GetScriptObject(var_a!!.entityNumberPtr)
                        if (obj != null) {
                            `var`!!.setBytePtr(obj.data, st.b!!.value!!.ptrOffset)
                            `var`.floatPtr = `var`.floatPtr - 1
                        }
                    }

                    OP_COMP_F -> {
                        var_a = GetVariable(st.a)
                        var_c = GetVariable(st.c)
                        var_c!!.floatPtr = (var_a!!.floatPtr.toInt().inv().toFloat())
                    }

                    OP_STORE_F -> {
                        var_a = GetVariable(st.a)
                        var_b = GetVariable(st.b)
                        var_b!!.floatPtr = (var_a!!.floatPtr)
                    }

                    OP_STORE_ENT -> {
                        var_a = GetVariable(st.a)
                        var_b = GetVariable(st.b)
                        var_b!!.entityNumberPtr = (var_a!!.entityNumberPtr)
                    }

                    OP_STORE_BOOL -> {
                        var_a = GetVariable(st.a)
                        var_b = GetVariable(st.b)
                        var_b!!.intPtr = var_a!!.intPtr
                    }

                    OP_STORE_OBJENT -> {
                        var_a = GetVariable(st.a)
                        var_b = GetVariable(st.b)
                        obj = GetScriptObject(var_a!!.entityNumberPtr)
                        if (obj == null) {
                            var_b!!.entityNumberPtr = (0)
                        } else if (!obj.GetTypeDef().Inherits(st.b!!.TypeDef())) {
                            //Warning( "object '%s' cannot be converted to '%s'", obj.GetTypeName(), st.b.TypeDef().Name() );
                            var_b!!.entityNumberPtr = (0)
                        } else {
                            var_b!!.entityNumberPtr = (var_a.entityNumberPtr)
                        }
                    }

                    OP_STORE_OBJ, OP_STORE_ENTOBJ -> {
                        var_a = GetVariable(st.a)
                        var_b = GetVariable(st.b)
                        var_b!!.entityNumberPtr = (var_a!!.entityNumberPtr)
                    }

                    OP_STORE_S -> SetString(st.b, GetString(st.a))
                    OP_STORE_V -> {
                        var_a = GetVariable(st.a)
                        var_b = GetVariable(st.b)
                        var_b!!.setVectorPtr(var_a!!.getVectorPtrs())
                    }

                    OP_STORE_FTOS -> {
                        var_a = GetVariable(st.a)
                        SetString(st.b, FloatToString(var_a!!.floatPtr))
                    }

                    OP_STORE_BTOS -> {
                        var_a = GetVariable(st.a)
                        SetString(st.b, if (itob(var_a!!.intPtr)) "true" else "false")
                    }

                    OP_STORE_VTOS -> {
                        var_a = GetVariable(st.a)
                        SetString(st.b, var_a!!.getVectorPtrs().ToString())
                    }

                    OP_STORE_FTOBOOL -> {
                        var_a = GetVariable(st.a)
                        var_b = GetVariable(st.b)
                        if (var_a!!.floatPtr != 0.0f) {
                            var_b!!.intPtr = 1
                        } else {
                            var_b!!.intPtr = 0
                        }
                    }

                    OP_STORE_BOOLTOF -> {
                        var_a = GetVariable(st.a)
                        var_b = GetVariable(st.b)
                        var_b!!.floatPtr = var_a!!.intPtr.toFloat()
                    }

                    OP_STOREP_F -> {
                        var_b = GetEvalVariable(st.b)
                        if (var_b != null && var_b.evalPtr != null) {
                            var_a = GetVariable(st.a)
                            var_b.evalPtr!!.floatPtr = var_a!!.floatPtr
                        }
                    }

                    OP_STOREP_ENT -> {
                        var_b = GetEvalVariable(st.b)
                        if (var_b != null && var_b.evalPtr != null) {
                            var_a = GetVariable(st.a)
                            var_b.evalPtr!!.entityNumberPtr = var_a!!.entityNumberPtr
                        }
                    }

                    OP_STOREP_FLD, OP_STOREP_BOOL -> {
                        var_b = GetEvalVariable(st.b)
                        if (var_b != null && var_b.evalPtr != null) {
                            var_a = GetVariable(st.a)
                            var_b.evalPtr!!.intPtr = var_a!!.intPtr
                        }
                    }

                    OP_STOREP_S -> {
                        var_b = GetEvalVariable(st.b)
                        if (var_b != null && var_b.evalPtr != null) {
                            var_b.evalPtr!!.setString(GetString(st.a)) //idStr.Copynz(var_b!!.evalPtr.stringPtr, GetString(st.a), MAX_STRING_LEN);
                        }
                    }

                    OP_STOREP_V -> {
                        var_b = GetEvalVariable(st.b)
                        if (var_b != null && var_b.evalPtr != null) {
                            var_a = GetVariable(st.a)
                            var_b.evalPtr!!.setVectorPtr(var_a!!.getVectorPtrs())
                        }
                    }

                    OP_STOREP_FTOS -> {
                        var_b = GetEvalVariable(st.b)
                        if (var_b != null && var_b.evalPtr != null) {
                            var_a = GetVariable(st.a)
                            var_b.evalPtr!!.setString(FloatToString(var_a!!.floatPtr)) //idStr.Copynz(var_b!!.evalPtr.stringPtr, FloatToString(var_a!!.floatPtr.get()), MAX_STRING_LEN);
                        }
                    }

                    OP_STOREP_BTOS -> {
                        var_b = GetEvalVariable(st.b)
                        if (var_b != null && var_b.evalPtr != null) {
                            var_a = GetVariable(st.a)
                            if (var_a!!.floatPtr != 0.0f) {
                                var_b.evalPtr!!.setString("true") //idStr.Copynz(var_b!!.evalPtr.stringPtr, "true", MAX_STRING_LEN);
                            } else {
                                var_b.evalPtr!!.setString("false") //idStr.Copynz(var_b!!.evalPtr.stringPtr, "false", MAX_STRING_LEN);
                            }
                        }
                    }

                    OP_STOREP_VTOS -> {
                        var_b = GetEvalVariable(st.b)
                        if (var_b != null && var_b.evalPtr != null) {
                            var_a = GetVariable(st.a)
                            var_b.evalPtr!!.setString(
                                var_a!!.getVectorPtrs().ToString()
                            ) //idStr.Copynz(var_b!!.evalPtr.stringPtr, var_a!!.vectorPtr[0].ToString(), MAX_STRING_LEN);
                        }
                    }

                    OP_STOREP_FTOBOOL -> {
                        var_b = GetEvalVariable(st.b)
                        if (var_b != null && var_b.evalPtr != null) {
                            var_a = GetVariable(st.a)
                            if (var_a!!.floatPtr != 0.0f) {
                                var_b.evalPtr!!.intPtr = 1
                            } else {
                                var_b.evalPtr!!.intPtr = 0
                            }
                        }
                    }

                    OP_STOREP_BOOLTOF -> {
                        var_b = GetEvalVariable(st.b)
                        if (var_b != null && var_b.evalPtr != null) {
                            var_a = GetVariable(st.a)
                            var_b.evalPtr!!.floatPtr = var_a!!.intPtr.toFloat()
                        }
                    }

                    OP_STOREP_OBJ -> {
                        var_b = GetEvalVariable(st.b)
                        if (var_b != null && var_b.evalPtr != null) {
                            var_a = GetVariable(st.a)
                            var_b.evalPtr!!.entityNumberPtr = var_a!!.entityNumberPtr
                        }
                    }

                    OP_STOREP_OBJENT -> {
                        var_b = GetEvalVariable(st.b)
                        if (var_b != null && var_b.evalPtr != null) {
                            var_a = GetVariable(st.a)
                            obj = GetScriptObject(var_a!!.entityNumberPtr)
                            if (obj == null) {
                                var_b.evalPtr!!.entityNumberPtr = 0

                                // st.b points to type_pointer, which is just a temporary that gets its type reassigned, so we store the real type in st.c
                                // so that we can do a type check during run time since we don't know what type the script object is at compile time because it
                                // comes from an entity
                            } else if (!obj.GetTypeDef().Inherits(st.c!!.TypeDef())) {
                                //Warning( "object '%s' cannot be converted to '%s'", obj.GetTypeName(), st.c.TypeDef().Name() );
                                var_b.evalPtr!!.entityNumberPtr = 0
                            } else {
                                var_b.evalPtr!!.entityNumberPtr = var_a.entityNumberPtr
                            }
                        }
                    }

                    OP_ADDRESS -> {
                        var_a = GetVariable(st.a)
                        var_c = GetVariable(st.c)
                        obj = GetScriptObject(var_a!!.entityNumberPtr)
                        if (obj != null) {
                            obj.offset = st.b!!.value!!.ptrOffset
                            var_c!!.setEvalPtr(var_a.entityNumberPtr)
                        } else {
                            var_c!!.setEvalPtr(NULL_ENTITY)
                        }
                    }

                    OP_INDIRECT_F -> {
                        var_a = GetVariable(st.a)
                        var_c = GetVariable(st.c)
                        obj = GetScriptObject(var_a!!.entityNumberPtr)
                        if (obj != null) {
                            `var`!!.setBytePtr(obj.data, st.b!!.value!!.ptrOffset)
                            var_c!!.floatPtr = (`var`.floatPtr)
                        } else {
                            var_c!!.floatPtr = (0.0f)
                        }
                    }

                    OP_INDIRECT_ENT -> {
                        var_a = GetVariable(st.a)
                        var_c = GetVariable(st.c)
                        obj = GetScriptObject(var_a!!.entityNumberPtr)
                        if (obj != null) {
                            `var`!!.setBytePtr(obj.data, st.b!!.value!!.ptrOffset)
                            var_c!!.entityNumberPtr = (`var`.entityNumberPtr)
                        } else {
                            var_c!!.entityNumberPtr = (0)
                        }
                    }

                    OP_INDIRECT_BOOL -> {
                        var_a = GetVariable(st.a)
                        var_c = GetVariable(st.c)
                        obj = GetScriptObject(var_a!!.entityNumberPtr)
                        if (obj != null) {
                            `var`!!.setBytePtr(obj.data, st.b!!.value!!.ptrOffset)
                            var_c!!.intPtr = `var`.intPtr
                        } else {
                            var_c!!.intPtr = 0
                        }
                    }

                    OP_INDIRECT_S -> {
                        var_a = GetVariable(st.a)
                        obj = GetScriptObject(var_a!!.entityNumberPtr)
                        if (obj != null) {
                            `var`!!.setStringPtr(obj.data, st.b!!.value!!.ptrOffset)
                            SetString(st.c, `var`.stringPtr)
                        } else {
                            SetString(st.c, "")
                        }
                    }

                    OP_INDIRECT_V -> {
                        var_a = GetVariable(st.a)
                        var_c = GetVariable(st.c)
                        obj = GetScriptObject(var_a!!.entityNumberPtr)
                        if (obj != null) {
                            `var`!!.setBytePtr(obj.data, st.b!!.value!!.ptrOffset)
                            var_c!!.setVectorPtr(`var`.getVectorPtrs())
                        } else {
                            var_c!!.getVectorPtrs().Zero()
                        }
                    }

                    OP_INDIRECT_OBJ -> {
                        var_a = GetVariable(st.a)
                        var_c = GetVariable(st.c)
                        obj = GetScriptObject(var_a!!.entityNumberPtr)
                        if (obj == null) {
                            var_c!!.entityNumberPtr = (0)
                        } else {
                            `var`!!.setBytePtr(obj.data, st.b!!.value!!.ptrOffset)
                            var_c!!.entityNumberPtr = (`var`.entityNumberPtr)
                        }
                    }

                    OP_PUSH_F -> {
                        var_a = GetVariable(st.a)
                        Push(var_a!!.intPtr)
                    }

                    OP_PUSH_FTOS -> {
                        var_a = GetVariable(st.a)
                        PushString(FloatToString(var_a!!.floatPtr))
                    }

                    OP_PUSH_BTOF -> {
                        var_a = GetVariable(st.a)
                        floatVal = var_a!!.intPtr.toFloat()
                        Push(floatVal.toBits())
                    }

                    OP_PUSH_FTOB -> {
                        var_a = GetVariable(st.a)
                        if (var_a!!.floatPtr != 0.0f) {
                            Push(1)
                        } else {
                            Push(0)
                        }
                    }

                    OP_PUSH_VTOS -> {
                        var_a = GetVariable(st.a)
                        PushString(var_a!!.getVectorPtrs().ToString())
                    }

                    OP_PUSH_BTOS -> {
                        var_a = GetVariable(st.a)
                        PushString(if (itob(var_a!!.intPtr)) "true" else "false")
                    }

                    OP_PUSH_ENT -> {
                        var_a = GetVariable(st.a)
                        Push(var_a!!.entityNumberPtr)
                    }

                    OP_PUSH_S -> PushString(GetString(st.a))
                    OP_PUSH_V -> {
                        var_a = GetVariable(st.a)
                        PushVector(var_a!!.getVectorPtrs())
                    }

                    OP_PUSH_OBJ -> {
                        var_a = GetVariable(st.a)
                        Push(var_a!!.entityNumberPtr)
                    }

                    OP_PUSH_OBJENT -> {
                        var_a = GetVariable(st.a)
                        Push(var_a!!.entityNumberPtr)
                    }

                    OP_BREAK, OP_CONTINUE -> Error("Bad opcode %d", st.op)
                    else -> Error("Bad opcode %d", st.op)
                }
            }
            var_c = null
            var_b = var_c
            var_a = var_b
            `var` = var_a
            return threadDying
        }

        fun Reset() {
            callStackDepth = 0
            localstackUsed = 0
            localstackBase = 0
            maxLocalstackUsed = 0
            maxStackDepth = 0
            popParms = 0
            multiFrameEvent = null
            eventEntity = null
            currentFunction = null
            NextInstruction(0)
            threadDying = false
            doneProcessing = true
        }

        /*
         ================
         idInterpreter::GetRegisterValue

         Returns a string representation of the value of the register.  This is 
         used primarily for the debugger and debugging

         //FIXME:  This is pretty much wrong.  won't access data in most situations.
         ================
         */
        fun GetRegisterValue(name: String?, out: idStr, scopeDepth: Int): Boolean {
            var scopeDepth = scopeDepth
            val reg: varEval_s?
            var d: idVarDef?
            val funcObject = arrayOf<String>() //new char[1024];
            val funcName: String?
            val scope: idVarDef?
            val field: idTypeDef?
            val obj: idScriptObject?
            val func: function_t?
            val funcIndex: Int
            out.Empty()
            if (scopeDepth == -1) {
                scopeDepth = callStackDepth
            }
            func = if (scopeDepth == callStackDepth) {
                currentFunction
            } else {
                callStack[scopeDepth]!!.f
            }
            if (func == null) {
                return false
            }
            Copynz(funcObject, func.Name(), 4)
            funcIndex = funcObject[0].indexOf("::")
            if (funcIndex != -1) {
//                funcName = "\0";
                scope = Game_local.gameLocal.program.GetDef(null, funcObject[0], Script_Program.def_namespace)
                funcName = funcObject[0].substring(funcIndex + 2) //TODO:check pointer location
            } else {
                funcName = funcObject[0]
                scope = Script_Program.def_namespace
            }

            // Get the function from the object
            d = Game_local.gameLocal.program.GetDef(null, funcName, scope)
            if (d == null) {
                return false
            }

            // Get the variable itself and check various namespaces
            d = Game_local.gameLocal.program.GetDef(null, name, d)
            if (d == null) {
                if (scope === Script_Program.def_namespace) {
                    return false
                }
                d = Game_local.gameLocal.program.GetDef(null, name, scope)
                if (d == null) {
                    d = Game_local.gameLocal.program.GetDef(null, name, Script_Program.def_namespace)
                    if (d == null) {
                        return false
                    }
                }
            }
            reg = GetVariable(d)
            return when (d.Type()) {
                Script_Program.ev_float -> {
                    if (reg!!.floatPtr != 0.0f) {
                        out.set(String.format("%g", reg.floatPtr))
                    } else {
                        out.set("0")
                    }
                    true
                }

                Script_Program.ev_vector -> {
                    //                    if (reg.vectorPtr != null) {
                    val vectorPtr = idVec3(reg!!.getVectorPtrs())
                    out.set(String.format("%g,%g,%g", vectorPtr.x, vectorPtr.y, vectorPtr.z))
                    //                    } else {
//                        out.set("0,0,0");
//                    }
                    true
                }

                Script_Program.ev_boolean -> {
                    if (reg!!.intPtr != 0) {
                        out.set(String.format("%d", reg.intPtr))
                    } else {
                        out.set("0")
                    }
                    true
                }

                Script_Program.ev_field -> {
                    if (scope === Script_Program.def_namespace) {
                        // should never happen, but handle it safely anyway
                        return false
                    }
                    field = scope!!.TypeDef()!!.GetParmType(reg!!.ptrOffset)!!.FieldType()
                    obj = idScriptObject()
                    obj.Read(
                        ByteBuffer.wrap(
                            Arrays.copyOf(
                                localstack,
                                callStack[callStackDepth]!!.stackbase
                            )
                        )
                    ) //TODO: check this range
                    if (field == null || obj == null) {
                        return false
                    }
                    when (field!!.Type()) {
                        Script_Program.ev_boolean -> {
                            out.set(String.format("%d", obj.data!!.getInt(reg.ptrOffset)))
                            true
                        }

                        Script_Program.ev_float -> {
                            out.set(String.format("%g", obj.data!!.getFloat(reg.ptrOffset)))
                            true
                        }

                        else -> false
                    }
                }

                Script_Program.ev_string -> {
                    if (reg!!.stringPtr != null) {
                        out.set("\"")
                        out.plusAssign(reg.stringPtr!!)
                        out.plusAssign("\"")
                    } else {
                        out.set("\"\"")
                    }
                    true
                }

                else -> false
            }
            //            return false;
        }

        fun GetCallstackDepth(): Int {
            return callStackDepth
        }

        fun GetCallstack(): prstack_s? {
            return callStack[0]
        }

        fun GetCurrentFunction(): function_t? {
            return currentFunction
        }

        fun GetThread(): idThread? {
            return thread
        }

        companion object {
            const val NULL_ENTITY = -1
            var text = CharArray(32)
            private var DBG_Execute = 0
        }
    }
}
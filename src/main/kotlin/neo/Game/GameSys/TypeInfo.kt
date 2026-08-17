/*
 * Copyright (C) 1999-2011 id Software LLC, a ZeniMax Media company.
 * Translated to Kotlin by Dr. Feederino with support of Claude Code
 *
 * This file is part of the Doom 3 Kotlin project.
 * Original source: neo/game/gamesys/TypeInfo.h, neo/game/gamesys/TypeInfo.cpp
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
 */
package neo.Game.GameSys

import neo.Game.GameSys.NoGameTypeInfo.classTypeInfo_t
import neo.Game.GameSys.NoGameTypeInfo.enumTypeInfo_t
import neo.Game.Game_local
import neo.framework.CmdSystem
import neo.framework.CmdSystem.cmdExecution_t
import neo.framework.CmdSystem.cmdFunction_t
import neo.framework.Common
import neo.framework.FileSystem_h
import neo.framework.File_h.idFile
import neo.idlib.CmdArgs
import neo.idlib.Text.Lexer.LEXFL_NOSTRINGESCAPECHARS
import neo.idlib.Text.Lexer.idLexer
import neo.idlib.Text.Str
import neo.idlib.Text.Str.idStr
import neo.idlib.Text.Str.idStr.Companion.FindChar
import neo.idlib.Text.Token
import neo.idlib.Text.Token.idToken
import neo.idlib.Text.ctos
import neo.idlib.containers.List.cmp_t
import neo.idlib.containers.List.idList
import neo.idlib.idException
import java.nio.ByteBuffer

object TypeInfo {

    // disabled because it adds about 64MB to state dumps and takes a really long time
    const val DUMP_GAMELOCAL = false

    /*
     ================
     GetTypeVariableName
     ================
     */
    fun GetTypeVariableName(typeName: String, offset: Int): String {
        var currentTypeName = typeName
        var i: Int

        // Find the class that owns this offset
        i = 0
        while (NoGameTypeInfo.classTypeInfo[i].typeName != null) {
            if (idStr.Cmp(currentTypeName, NoGameTypeInfo.classTypeInfo[i].typeName!!) == 0) {
                val vars = NoGameTypeInfo.classTypeInfo[i].variables
                if (vars != null && vars[0].name != null && offset >= vars[0].offset) {
                    break
                }
                currentTypeName = NoGameTypeInfo.classTypeInfo[i].superType ?: ""
                if (currentTypeName.isEmpty()) {
                    return "<unknown>"
                }
                i = -1
            }
            i++
        }

        if (NoGameTypeInfo.classTypeInfo[i].typeName == null) {
            return "<unknown>"
        }

        val classInfo = NoGameTypeInfo.classTypeInfo[i]
        val vars = classInfo.variables ?: return "<unknown>"

        i = 0
        while (vars[i].name != null) {
            if (offset <= vars[i].offset) {
                break
            }
            i++
        }

        return if (i == 0) {
            "${classInfo.typeName}::<unknown>"
        } else {
            "${classInfo.typeName}::${vars[i - 1].name}"
        }
    }

    /*
     ================
     IsAllowedToChangedFromSaveGames
     ================
     */
    fun IsAllowedToChangedFromSaveGames(
        varName: String, varType: String?, scope: String, prefix: String, postfix: String?, value: String?
    ): Boolean {
        if (idStr.Icmp(scope, "idAnimator") == 0) {
            if (idStr.Icmp(varName, "forceUpdate") == 0) {
                return true
            }
            if (idStr.Icmp(varName, "lastTransformTime") == 0) {
                return true
            }
            if (idStr.Icmp(varName, "AFPoseTime") == 0) {
                return true
            }
            if (idStr.Icmp(varName, "frameBounds") == 0) {
                return true
            }
        } else if (idStr.Icmp(scope, "idClipModel") == 0) {
            if (idStr.Icmp(varName, "touchCount") == 0) {
                return true
            }
        } else if (idStr.Icmp(scope, "idEntity") == 0) {
            if (idStr.Icmp(varName, "numPVSAreas") == 0) {
                return true
            }
            if (idStr.Icmp(varName, "renderView") == 0) {
                return true
            }
        } else if (idStr.Icmp(scope, "idBrittleFracture") == 0) {
            if (idStr.Icmp(varName, "changed") == 0) {
                return true
            }
        } else if (idStr.Icmp(scope, "idPhysics_AF") == 0) {
            return true
        } else if (idStr.Icmp(scope, "renderEntity_t") == 0) { // These get fixed up when UpdateVisuals is called
            if (idStr.Icmp(varName, "origin") == 0) {
                return true
            }
            if (idStr.Icmp(varName, "axis") == 0) {
                return true
            }
            if (idStr.Icmp(varName, "bounds") == 0) {
                return true
            }
        }
        return idStr.Icmpn(prefix, "idAFEntity_Base::af.idAF::physicsObj.idPhysics_AF", 49) == 0
    }

    /*
     ================
     IsRenderHandleVariable
     ================
     */
    fun IsRenderHandleVariable(
        varName: String, varType: String?, scope: String, prefix: String?, postfix: String?, value: String?
    ): Boolean {
        if (idStr.Icmp(scope, "idClipModel") == 0) {
            if (idStr.Icmp(varName, "renderModelHandle") == 0) {
                return true
            }
        } else if (idStr.Icmp(scope, "idFXLocalAction") == 0) {
            if (idStr.Icmp(varName, "lightDefHandle") == 0) {
                return true
            }
            if (idStr.Icmp(varName, "modelDefHandle") == 0) {
                return true
            }
        } else if (idStr.Icmp(scope, "idEntity") == 0) {
            if (idStr.Icmp(varName, "modelDefHandle") == 0) {
                return true
            }
        } else if (idStr.Icmp(scope, "idLight") == 0) {
            if (idStr.Icmp(varName, "lightDefHandle") == 0) {
                return true
            }
        } else if (idStr.Icmp(scope, "idAFEntity_Gibbable") == 0) {
            if (idStr.Icmp(varName, "skeletonModelDefHandle") == 0) {
                return true
            }
        } else if (idStr.Icmp(scope, "idAFEntity_SteamPipe") == 0) {
            if (idStr.Icmp(varName, "steamModelHandle") == 0) {
                return true
            }
        } else if (idStr.Icmp(scope, "idItem") == 0) {
            if (idStr.Icmp(varName, "itemShellHandle") == 0) {
                return true
            }
        } else if (idStr.Icmp(scope, "idExplodingBarrel") == 0) {
            if (idStr.Icmp(varName, "particleModelDefHandle") == 0) {
                return true
            }
            if (idStr.Icmp(varName, "lightDefHandle") == 0) {
                return true
            }
        } else if (idStr.Icmp(scope, "idProjectile") == 0) {
            if (idStr.Icmp(varName, "lightDefHandle") == 0) {
                return true
            }
        } else if (idStr.Icmp(scope, "idBFGProjectile") == 0) {
            if (idStr.Icmp(varName, "secondModelDefHandle") == 0) {
                return true
            }
        } else if (idStr.Icmp(scope, "idSmokeParticles") == 0) {
            if (idStr.Icmp(varName, "renderEntityHandle") == 0) {
                return true
            }
        } else if (idStr.Icmp(scope, "idWeapon") == 0) {
            if (idStr.Icmp(varName, "muzzleFlashHandle") == 0) {
                return true
            }
            if (idStr.Icmp(varName, "worldMuzzleFlashHandle") == 0) {
                return true
            }
            if (idStr.Icmp(varName, "guiLightHandle") == 0) {
                return true
            }
            if (idStr.Icmp(varName, "nozzleGlowHandle") == 0) {
                return true
            }
        }
        return false
    }

    /*
     ================
     WriteVariableType_t — C++ function pointer typedef
     ================
     */
    abstract class WriteVariableType_t {
        abstract fun run(
            varName: String,
            varType: String,
            scope: String,
            prefix: String,
            postfix: String,
            value: String,
            varPtr: ByteBuffer?,
            varSize: Int
        )
    }

    /*
     ================
     idTypeInfoTools
     ================
     */
    object idTypeInfoTools {
        private var buffers: Array<CharArray> = Array(4) { CharArray(16384) }
        private var index = 0
        private var Write: WriteVariableType_t? = null
        private var fp: idFile? = null
        private var initValue = 0
        private var src: idLexer? = null
        private var typeError = false

        /*
         ================
         idTypeInfoTools::FindClassInfo
         ================
         */
        fun FindClassInfo(typeName: String): classTypeInfo_t? {
            var i = 0
            while (NoGameTypeInfo.classTypeInfo[i].typeName != null) {
                if (idStr.Cmp(typeName, NoGameTypeInfo.classTypeInfo[i].typeName!!) == 0) {
                    return NoGameTypeInfo.classTypeInfo[i]
                }
                i++
            }
            return null
        }

        /*
         ================
         idTypeInfoTools::FindEnumInfo
         ================
         */
        fun FindEnumInfo(typeName: String): enumTypeInfo_t? {
            var i = 0
            while (NoGameTypeInfo.enumTypeInfo[i].typeName != null) {
                if (idStr.Cmp(typeName, NoGameTypeInfo.enumTypeInfo[i].typeName!!) == 0) {
                    return NoGameTypeInfo.enumTypeInfo[i]
                }
                i++
            }
            return null
        }

        /*
         ================
         idTypeInfoTools::IsSubclassOf
         ================
         */ // FIX: C++ takes `const char *typeName` and reassigns the local pointer.
        // The old Kotlin code took `typeName: idStr` and mutated it via .set(),
        // which would corrupt the caller's idStr. Changed to use a local String variable.
        fun IsSubclassOf(typeName: String, superType: String): Boolean {
            var currentType = typeName
            while (currentType.isNotEmpty()) {
                if (idStr.Cmp(currentType, superType) == 0) {
                    return true
                }
                var i = 0
                while (NoGameTypeInfo.classTypeInfo[i].typeName != null) {
                    if (idStr.Cmp(currentType, NoGameTypeInfo.classTypeInfo[i].typeName!!) == 0) {
                        currentType = NoGameTypeInfo.classTypeInfo[i].superType ?: ""
                        break
                    }
                    i++
                }
                if (NoGameTypeInfo.classTypeInfo[i].typeName == null) {
                    Common.common.Warning("super class %s not found", currentType)
                    break
                }
            }
            return false
        }

        /*
         ================
         idTypeInfoTools::PrintType
         ================
         */
        fun PrintType(typePtr: ByteBuffer, typeName: String) {
            fp = null
            initValue = 0
            Write = PrintVariable.INSTANCE
            WriteClass_r(typePtr, "", typeName, "", "", 0)
        }

        /*
         ================
         idTypeInfoTools::WriteTypeToFile
         ================
         */
        fun WriteTypeToFile(fp: idFile?, typePtr: ByteBuffer, typeName: String) {
            this.fp = fp
            initValue = 0
            Write = WriteVariable.INSTANCE
            WriteClass_r(typePtr, "", typeName, "", "", 0)
        }

        /*
         ================
         idTypeInfoTools::InitTypeVariables
         ================
         */ // FIX: Was calling itself recursively (infinite recursion).
        // C++ sets Write = InitVariable then calls WriteClass_r.
        fun InitTypeVariables(typePtr: ByteBuffer?, typeName: String?, value: Int) {
            if (typePtr == null || typeName == null) return
            fp = null
            initValue = value
            Write = InitVariable.INSTANCE
            WriteClass_r(typePtr, "", typeName, "", "", 0)
        }

        /*
         ================
         idTypeInfoTools::WriteGameState
         ================
         */
        fun WriteGameState(fileName: String?) { // NOTE: Partially implemented — the core type introspection (WriteVariable_r) is
            // not fully functional on JVM because there is no raw pointer arithmetic.
            // The overall structure matches C++ for when/if type info becomes available.
            if (fileName == null) return

            val file = FileSystem_h.fileSystem.OpenFileWrite(fileName)
            if (file == null) {
                Common.common.Warning("couldn't open %s", fileName)
                return
            }

            fp = file
            Write = WriteGameStateVariable.INSTANCE

            var num = 0
            for (i in 0 until Game_local.gameLocal.num_entities) {
                val ent = Game_local.gameLocal.entities[i] ?: continue
                file.WriteFloatString(
                    "\nentity %d %s {\n", i, ent.GetClassname()
                ) // NOTE: In C++, WriteClass_r gets the raw void* pointer to the entity.
                // On JVM we cannot do raw memory introspection. This writes the class
                // structure as known to the NoGameTypeInfo tables (normally empty stubs).
                // WriteClass_r(... ent ..., ent.GetType()->classname, ...)
                file.WriteFloatString("}\n")
                num++
            }

            FileSystem_h.fileSystem.CloseFile(file)
            Common.common.Printf("%d entities written\n", num)
        }

        /*
         ================
         idTypeInfoTools::CompareGameState
         ================
         */
        fun CompareGameState(fileName: String?) { // NOTE: Partially implemented — the core type introspection (WriteVariable_r) is
            // not fully functional on JVM because there is no raw pointer arithmetic.
            if (fileName == null) return

            val lexer = idLexer()
            src = lexer
            lexer.SetFlags(LEXFL_NOSTRINGESCAPECHARS)

            if (!lexer.LoadFile(fileName)) {
                Common.common.Warning("couldn't load %s", fileName)
                src = null
                return
            }

            fp = null
            Write = VerifyVariable.INSTANCE

            val token = idToken()

            while (lexer.ReadToken(token)) {
                if (token.toString() != "entity") {
                    break
                }
                if (!lexer.ExpectTokenType(Token.TT_NUMBER, Token.TT_INTEGER, token)) {
                    break
                }

                val entityNum = token.GetIntValue()

                if (entityNum < 0 || entityNum >= Game_local.gameLocal.num_entities) {
                    lexer.Warning("entity number %d out of range", entityNum)
                    break
                }

                typeError = false

                val ent = Game_local.gameLocal.entities[entityNum]
                if (ent == null) {
                    lexer.Warning("entity %d is not spawned", entityNum)
                    lexer.SkipBracedSection(true)
                    continue
                }

                if (!lexer.ExpectTokenType(Token.TT_NAME, 0, token)) {
                    break
                }

                if (token.Cmp(ent.GetClassname()) != 0) {
                    lexer.Warning("entity %d has wrong type", entityNum)
                    lexer.SkipBracedSection(true)
                    continue
                }

                if (!lexer.ExpectTokenString("{")) {
                    lexer.Warning("entity %d missing leading {", entityNum)
                    break
                }

                // NOTE: WriteClass_r would walk the entity's class hierarchy here.
                // Without raw pointer introspection, we skip the actual variable comparison.

                if (!lexer.SkipBracedSection(false)) {
                    lexer.Warning("entity %d missing trailing }", entityNum)
                    break
                }
            }

            src = null
        }

        /*
         ================
         idTypeInfoTools::OutputString
         ================
         */ // FIX: Original Kotlin had off-by-one — `c` was incremented before first use,
        // skipping string[0]. C++ does `c = *string++` which reads then advances.
        // Also the `i = 0.also { c = it }` and `for ... c++` pattern was wrong.
        private fun OutputString(string: String?): String {
            if (string.isNullOrEmpty()) {
                return ""
            }

            val out = buffers[index]
            index = (index + 1) and 3

            var i = 0
            var c = 0
            while (i < buffers[0].size - 2 && c < string.length) {
                val ch = string[c]
                c++
                when (ch) {
                    '\\' -> {
                        out[i++] = '\\'
                        out[i] = '\\'
                    }

                    '\n' -> {
                        out[i++] = '\\'
                        out[i] = 'n'
                    }

                    '\r' -> {
                        out[i++] = '\\'
                        out[i] = 'r'
                    }

                    '\t' -> {
                        out[i++] = '\\'
                        out[i] = 't'
                    }

                    '\u000B' -> {
                        out[i++] = '\\'
                        out[i] = 'v'
                    }

                    else -> out[i] = ch
                }
                i++
            }
            out[i] = '\u0000'
            return ctos(out)
        }

        /*
         ================
         idTypeInfoTools::ParseTemplateArguments
         ================
         */
        private fun ParseTemplateArguments(src: idLexer, arguments: idStr): Boolean {
            val token = idToken()
            arguments.set("")
            if (!src.ExpectTokenString("<")) {
                return false
            }
            var indent = 1
            while (indent != 0) {
                if (!src.ReadToken(token)) {
                    break
                }
                if (token.toString() == "<") {
                    indent++
                } else if (token.toString() == ">") {
                    indent--
                } else {
                    if (arguments.Length() != 0) {
                        arguments.Append(" ")
                    }
                    arguments.Append(token)
                }
            }
            return true
        }

        /*
         ================
         idTypeInfoTools::WriteVariable_r

         NOTE: This function performs deep type introspection using raw memory pointers
         in C++ (casting void* to specific types, pointer arithmetic, etc.). This is
         fundamentally incompatible with JVM memory model. The function is preserved
         as a stub that logs unknown types, since the NoGameTypeInfo tables are empty
         in release builds anyway. In debug builds with populated GameTypeInfo tables,
         this would need a JVM reflection-based reimplementation.
         ================
         */
        private fun WriteVariable_r(
            varPtr: ByteBuffer?, varName: String, varType: String, scope: String, prefix: String, pointerDepth: Int
        ): Int { // STUB: Cannot implement raw-pointer type introspection on JVM.
            // C++ version casts void* to specific types and reads memory directly.
            // The NoGameTypeInfo tables are empty in release builds, so this is only
            // called when debug type info is available (which it never is in this port).
            Write?.run(varName, varType, scope, prefix, "", "<JVM: type introspection not available>", varPtr, 0)
            return -1
        }

        /*
         ================
         idTypeInfoTools::WriteClass_r
         ================
         */
        private fun WriteClass_r(
            classPtr: ByteBuffer, className: String, classType: String, scope: String, prefix: String, pointerDepth: Int
        ) {
            val classInfo = FindClassInfo(classType) ?: return

            if (!classInfo.superType.isNullOrEmpty()) {
                WriteClass_r(classPtr, className, classInfo.superType!!, scope, prefix, pointerDepth)
            }

            val vars = classInfo.variables ?: return
            var i = 0
            while (vars[i].name != null) {
                val classVar = vars[i] // C++: void *varPtr = (void *) (((byte *)classPtr) + classVar.offset);
                // On JVM, we set the ByteBuffer position to simulate the offset
                if (classVar.offset < classPtr.capacity()) {
                    classPtr.position(classVar.offset)
                }
                WriteVariable_r(classPtr, classVar.name!!, classVar.type ?: "", classType, prefix, pointerDepth)
                i++
            }
        }

        /*
         ================
         idTypeInfoTools::PrintVariable
         ================
         */
        private class PrintVariable private constructor() : WriteVariableType_t() {
            override fun run(
                varName: String,
                varType: String,
                scope: String,
                prefix: String,
                postfix: String,
                value: String,
                varPtr: ByteBuffer?,
                varSize: Int
            ) {
                Common.common.Printf("%s%s::%s%s = \"%s\"\n", prefix, scope, varName, postfix, value)
            }

            companion object {
                val INSTANCE: WriteVariableType_t = PrintVariable()
            }
        }

        /*
         ================
         idTypeInfoTools::WriteVariable
         ================
         */
        private class WriteVariable private constructor() : WriteVariableType_t() {
            override fun run(
                varName: String,
                varType: String,
                scope: String,
                prefix: String,
                postfix: String,
                value: String,
                varPtr: ByteBuffer?,
                varSize: Int
            ) { // FIX: C++ `value+i+1` is pointer arithmetic (substring from index i+1).
                // Old Kotlin had `value + i + 1` which is string concatenation with integers.
                var i = FindChar(value, '#', 0)
                while (i >= 0) {
                    val sub = value.substring(i + 1)
                    if (idStr.Icmpn(sub, "INF", 3) == 0 || idStr.Icmpn(sub, "IND", 3) == 0 || idStr.Icmpn(
                            sub, "NAN", 3
                        ) == 0 || idStr.Icmpn(sub, "QNAN", 4) == 0 || idStr.Icmpn(sub, "SNAN", 4) == 0
                    ) {
                        Common.common.Warning("%s%s::%s%s = \"%s\"", prefix, scope, varName, postfix, value)
                        break
                    }
                    i = FindChar(value, '#', i + 1)
                }
                fp!!.WriteFloatString("%s%s::%s%s = \"%s\"\n", prefix, scope, varName, postfix, value)
            }

            companion object {
                val INSTANCE: WriteVariableType_t = WriteVariable()
            }
        }

        /*
         ================
         idTypeInfoTools::WriteGameStateVariable
         ================
         */
        private class WriteGameStateVariable private constructor() : WriteVariableType_t() {
            override fun run(
                varName: String,
                varType: String,
                scope: String,
                prefix: String,
                postfix: String,
                value: String,
                varPtr: ByteBuffer?,
                varSize: Int
            ) { // FIX: Same pointer arithmetic bug as WriteVariable — use substring
                var i = FindChar(value, '#', 0)
                while (i >= 0) {
                    val sub = value.substring(i + 1)
                    if (idStr.Icmpn(sub, "INF", 3) == 0 || idStr.Icmpn(sub, "IND", 3) == 0 || idStr.Icmpn(
                            sub, "NAN", 3
                        ) == 0 || idStr.Icmpn(sub, "QNAN", 4) == 0 || idStr.Icmpn(sub, "SNAN", 4) == 0
                    ) {
                        Common.common.Warning("%s%s::%s%s = \"%s\"", prefix, scope, varName, postfix, value)
                        break
                    }
                    i = FindChar(value, '#', i + 1)
                }

                if (IsRenderHandleVariable(varName, varType, scope, prefix, postfix, value)) {
                    return
                }

                if (IsAllowedToChangedFromSaveGames(varName, varType, scope, prefix, postfix, value)) {
                    return
                }

                fp!!.WriteFloatString("%s%s::%s%s = \"%s\"\n", prefix, scope, varName, postfix, value)
            }

            companion object {
                val INSTANCE: WriteVariableType_t = WriteGameStateVariable()
            }
        }

        /*
         ================
         idTypeInfoTools::InitVariable
         ================
         */
        private class InitVariable private constructor() : WriteVariableType_t() {
            override fun run(
                varName: String,
                varType: String,
                scope: String,
                prefix: String,
                postfix: String,
                value: String,
                varPtr: ByteBuffer?,
                varSize: Int
            ) {
                if (varPtr != null && varSize > 0) { // NOTE: skip renderer handles
                    if (IsRenderHandleVariable(varName, varType, scope, prefix, postfix, value)) {
                        return
                    } // C++: memset(const_cast<void*>(varPtr), initValue, varSize)
                    val fillByte = initValue.toByte()
                    val pos = varPtr.position()
                    for (j in 0 until varSize) {
                        if (pos + j < varPtr.capacity()) {
                            varPtr.put(pos + j, fillByte)
                        }
                    }
                }
            }

            companion object {
                val INSTANCE: WriteVariableType_t = InitVariable()
            }
        }

        /*
         ================
         idTypeInfoTools::VerifyVariable
         ================
         */
        private class VerifyVariable private constructor() : WriteVariableType_t() {
            override fun run(
                varName: String,
                varType: String,
                scope: String,
                prefix: String,
                postfix: String,
                value: String,
                varPtr: ByteBuffer?,
                varSize: Int
            ) {
                val token = idToken()
                if (typeError) {
                    return
                }
                val lexer = src ?: return
                lexer.SkipUntilString("=")
                lexer.ExpectTokenType(Token.TT_STRING, 0, token)
                if (token.Cmp(value) != 0) {

                    // NOTE: skip several things
                    if (IsRenderHandleVariable(varName, varType, scope, prefix, postfix, value)) {
                        return
                    }
                    if (IsAllowedToChangedFromSaveGames(varName, varType, scope, prefix, postfix, value)) {
                        return
                    }

                    lexer.Warning(
                        "state diff for %s%s::%s%s\n%s\n%s", prefix, scope, varName, postfix, token.toString(), value
                    )
                    typeError = true
                }
            }

            companion object {
                val INSTANCE: WriteVariableType_t = VerifyVariable()
            }
        }
    }

    /*
     ================
     WriteGameState_f
     ================
     */
    class WriteGameState_f private constructor() : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            val fileName: idStr = if (args!!.Argc() > 1) {
                idStr(args.Argv(1))
            } else {
                idStr("GameState.txt")
            }
            fileName.SetFileExtension("gameState.txt")
            idTypeInfoTools.WriteGameState(fileName.toString())
        }

        companion object {
            private val instance: cmdFunction_t = WriteGameState_f()
            fun getInstance(): cmdFunction_t {
                return instance
            }
        }
    }

    /*
     ================
     CompareGameState_f
     ================
     */
    class CompareGameState_f private constructor() : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            val fileName: idStr = if (args!!.Argc() > 1) {
                idStr(args.Argv(1))
            } else {
                idStr("GameState.txt")
            }
            fileName.SetFileExtension("gameState.txt")
            idTypeInfoTools.CompareGameState(fileName.toString())
        }

        companion object {
            private val instance: cmdFunction_t = CompareGameState_f()
            fun getInstance(): cmdFunction_t {
                return instance
            }
        }
    }

    /*
     ================
     TestSaveGame_f
     ================
     */
    class TestSaveGame_f private constructor() : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            if (args!!.Argc() <= 1) {
                Game_local.gameLocal.Printf("testSaveGame <mapName>\n")
                return
            }
            val name = idStr(args.Argv(1))
            try {
                CmdSystem.cmdSystem.BufferCommandText(cmdExecution_t.CMD_EXEC_NOW, Str.va("map %s", name))
                name.Replace("\\", "_")
                name.Replace("/", "_")
                CmdSystem.cmdSystem.BufferCommandText(cmdExecution_t.CMD_EXEC_NOW, Str.va("saveGame test_%s", name))
                CmdSystem.cmdSystem.BufferCommandText(cmdExecution_t.CMD_EXEC_NOW, Str.va("loadGame test_%s", name))
            } catch (ex: idException) { // an ERR_DROP was thrown
            }
            CmdSystem.cmdSystem.BufferCommandText(cmdExecution_t.CMD_EXEC_NOW, "quit")
        }

        companion object {
            private val instance: cmdFunction_t = TestSaveGame_f()
            fun getInstance(): cmdFunction_t {
                return instance
            }
        }
    }

    /*
     ================
     ListTypeInfo_f
     ================
     */
    class ListTypeInfo_f private constructor() : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            var i: Int
            var j: Int
            val index = idList<Int>()
            Common.common.Printf("%-32s : %-32s size (B)\n", "type name", "super type name")
            i = 0
            while (NoGameTypeInfo.classTypeInfo[i].typeName != null) {
                index.Append(i)
                i++
            }
            if (args!!.Argc() > 1 && idStr.Icmp(args.Argv(1), "size") == 0) {
                index.Sort(SortTypeInfoBySize())
            } else {
                index.Sort(SortTypeInfoByName())
            }
            i = 0
            while (NoGameTypeInfo.classTypeInfo[i].typeName != null) {
                j = index[i]
                Common.common.Printf(
                    "%-32s : %-32s %d\n",
                    NoGameTypeInfo.classTypeInfo[j].typeName!!,
                    NoGameTypeInfo.classTypeInfo[j].superType ?: "",
                    NoGameTypeInfo.classTypeInfo[j].size
                )
                i++
            }
        }

        /*
         ================
         SortTypeInfoByName
         ================
         */
        private class SortTypeInfoByName : cmp_t<Int> {
            override fun compare(a: Int, b: Int): Int {
                return idStr.Icmp(
                    NoGameTypeInfo.classTypeInfo[a].typeName!!, NoGameTypeInfo.classTypeInfo[b].typeName!!
                )
            }
        }

        /*
         ================
         SortTypeInfoBySize
         ================
         */
        private class SortTypeInfoBySize : cmp_t<Int> {
            override fun compare(a: Int, b: Int): Int {
                if (NoGameTypeInfo.classTypeInfo[a].size < NoGameTypeInfo.classTypeInfo[b].size) {
                    return -1
                }
                return if (NoGameTypeInfo.classTypeInfo[a].size > NoGameTypeInfo.classTypeInfo[b].size) {
                    1
                } else 0
            }
        }

        companion object {
            private val instance: cmdFunction_t = ListTypeInfo_f()
            fun getInstance(): cmdFunction_t {
                return instance
            }
        }
    }

    /*
     ================
     Global convenience functions matching the C++ free functions
     ================
     */
    fun WriteTypeToFile(fp: idFile?, typePtr: ByteBuffer, typeName: String) {
        idTypeInfoTools.WriteTypeToFile(fp, typePtr, typeName)
    }

    fun PrintType(typePtr: ByteBuffer, typeName: String) {
        idTypeInfoTools.PrintType(typePtr, typeName)
    }

    fun InitTypeVariables(typePtr: ByteBuffer?, typeName: String?, value: Int) {
        idTypeInfoTools.InitTypeVariables(typePtr, typeName, value)
    }
}

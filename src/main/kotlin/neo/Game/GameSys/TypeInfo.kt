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
 */
package neo.Game.GameSys

import neo.Game.GameSys.NoGameTypeInfo.classTypeInfo_t
import neo.Game.GameSys.NoGameTypeInfo.enumTypeInfo_t
import neo.Game.Game_local
import neo.TempDump
import neo.framework.CmdSystem
import neo.framework.CmdSystem.cmdExecution_t
import neo.framework.CmdSystem.cmdFunction_t
import neo.framework.Common
import neo.framework.File_h.idFile
import neo.idlib.CmdArgs
import neo.idlib.Text.Lexer.idLexer
import neo.idlib.Text.Str
import neo.idlib.Text.Str.idStr
import neo.idlib.Text.Str.idStr.Companion.FindChar
import neo.idlib.Text.Token
import neo.idlib.Text.Token.idToken
import neo.idlib.containers.List.cmp_t
import neo.idlib.containers.List.idList
import neo.idlib.idException
import java.nio.ByteBuffer
import java.util.*

object TypeInfo {
    const val DUMP_GAMELOCAL = false

    /*
     ================
     IsAllowedToChangedFromSaveGames
     ================
     */
    fun IsAllowedToChangedFromSaveGames(
        varName: String,
        varType: String?,
        scope: String,
        prefix: String,
        postfix: String?,
        value: String?
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
        } else if (idStr.Icmp(scope, "renderEntity_t") == 0) {
            // These get fixed up when UpdateVisuals is called
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
        varName: String,
        varType: String?,
        scope: String,
        prefix: String?,
        postfix: String?,
        value: String?
    ): Boolean {
        if (idStr.Icmp(scope, "idClipModel") == 0) {
            return idStr.Icmp(varName, "renderModelHandle") == 0
        } else if (idStr.Icmp(scope, "idFXLocalAction") == 0) {
            return if (idStr.Icmp(varName, "lightDefHandle") == 0) {
                true
            } else idStr.Icmp(varName, "modelDefHandle") == 0
        } else if (idStr.Icmp(scope, "idEntity") == 0) {
            return idStr.Icmp(varName, "modelDefHandle") == 0
        } else if (idStr.Icmp(scope, "idLight") == 0) {
            return idStr.Icmp(varName, "lightDefHandle") == 0
        } else if (idStr.Icmp(scope, "idAFEntity_Gibbable") == 0) {
            return idStr.Icmp(varName, "skeletonModelDefHandle") == 0
        } else if (idStr.Icmp(scope, "idAFEntity_SteamPipe") == 0) {
            return idStr.Icmp(varName, "steamModelHandle") == 0
        } else if (idStr.Icmp(scope, "idItem") == 0) {
            return idStr.Icmp(varName, "itemShellHandle") == 0
        } else if (idStr.Icmp(scope, "idExplodingBarrel") == 0) {
            return if (idStr.Icmp(varName, "particleModelDefHandle") == 0) {
                true
            } else idStr.Icmp(varName, "lightDefHandle") == 0
        } else if (idStr.Icmp(scope, "idProjectile") == 0) {
            return idStr.Icmp(varName, "lightDefHandle") == 0
        } else if (idStr.Icmp(scope, "idBFGProjectile") == 0) {
            return idStr.Icmp(varName, "secondModelDefHandle") == 0
        } else if (idStr.Icmp(scope, "idSmokeParticles") == 0) {
            return idStr.Icmp(varName, "renderEntityHandle") == 0
        } else if (idStr.Icmp(scope, "idWeapon") == 0) {
            if (idStr.Icmp(varName, "muzzleFlashHandle") == 0) {
                return true
            }
            if (idStr.Icmp(varName, "worldMuzzleFlashHandle") == 0) {
                return true
            }
            return if (idStr.Icmp(varName, "guiLightHandle") == 0) {
                true
            } else idStr.Icmp(varName, "nozzleGlowHandle") == 0
        }
        return false
    }

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

    object idTypeInfoTools {
        var buffers: Array<CharArray> = Array(4) { CharArray(16384) }
        var index = 0
        private var Write: WriteVariableType_t? = null
        private var fp: idFile? = null
        private var initValue = 0
        private val src: idLexer = idLexer()
        private var typeError = false

        /*
         ================
         idTypeInfoTools::FindClassInfo
         ================
         */
        fun FindClassInfo(typeName: String): classTypeInfo_t? {
            var i: Int
            i = 0
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
            var i: Int
            i = 0
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
         */
        fun IsSubclassOf(typeName: idStr, superType: String): Boolean {
            var i: Int
            while (!typeName.IsEmpty()) {
                if (idStr.Cmp(typeName.toString(), superType) == 0) {
                    return true
                }
                i = 0
                while (NoGameTypeInfo.classTypeInfo[i].typeName != null) {
                    if (idStr.Cmp(typeName.toString(), NoGameTypeInfo.classTypeInfo[i].typeName!!) == 0) {
                        typeName.set(NoGameTypeInfo.classTypeInfo[i].superType)
                        break
                    }
                    i++
                }
                if (NoGameTypeInfo.classTypeInfo[i].typeName == null) {
                    Common.common.Warning("super class %s not found", typeName)
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
            idTypeInfoTools.fp = fp
            initValue = 0
            Write = WriteVariable.INSTANCE
            WriteClass_r(typePtr, "", typeName, "", "", 0)
        }

        /*
         ================
         idTypeInfoTools::InitTypeVariables
         ================
         */
        fun InitTypeVariables(typePtr: ByteBuffer?, typeName: String?, value: Int) {
            InitTypeVariables(typePtr, typeName, value)
        }

        /*
         ================
         idTypeInfoTools::WriteGameState
         ================
         */
        fun WriteGameState(fileName: String?) {
            // NOTE: Not yet implemented -- requires deep pointer introspection not available in Kotlin/JVM.
            // See original C++ in neo/game/gamesys/TypeInfo.cpp, idTypeInfoTools::WriteGameState.
            throw UnsupportedOperationException()
        }

        /*
         ================
         idTypeInfoTools::CompareGameState
         ================
         */
        fun CompareGameState(fileName: String?) {
            // NOTE: Not yet implemented -- requires deep pointer introspection not available in Kotlin/JVM.
            // See original C++ in neo/game/gamesys/TypeInfo.cpp, idTypeInfoTools::CompareGameState.
            throw UnsupportedOperationException()
        }

        /*
         ================
         idTypeInfoTools::OutputString
         ================
         */
        private fun OutputString(string: String): String {
            val out: CharArray
            var i: Int
            var c: Int
            out = buffers[index]
            index = index + 1 and 3
            if (string.isNullOrEmpty()) {
                return ""
            }
            i = 0.also { c = it }
            while (i < buffers[0].size - 2) {
                c++
                when (string[c]) {
                    '\u0000' -> {
                        out[i] = '\u0000'
                        return TempDump.ctos(out)
                    }

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

                    else -> out[i] = string[c]
                }
                i++
            }
            out[i] = '\u0000'
            return TempDump.ctos(out)
        }

        /*
         ================
         idTypeInfoTools::ParseTemplateArguments
         ================
         */
        private fun ParseTemplateArguments(src: idLexer, arguments: idStr): Boolean {
            var indent: Int
            val token = idToken()
            arguments.set("")
            if (!src.ExpectTokenString("<")) {
                return false
            }
            indent = 1
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
         ================
         */
        private fun WriteVariable_r(
            varPtr: ByteBuffer?,
            varName: String?,
            varType: String?,
            scope: String?,
            prefix: String?,
            pointerDepth: Int
        ): Int {
            // NOTE: Not yet implemented -- requires deep pointer introspection not available in Kotlin/JVM.
            // See original C++ in neo/game/gamesys/TypeInfo.cpp, idTypeInfoTools::WriteVariable_r.
            throw UnsupportedOperationException()
        }

        /*
         ================
         idTypeInfoTools::WriteClass_r
         ================
         */
        private fun WriteClass_r(
            classPtr: ByteBuffer,
            className: String?,
            classType: String,
            scope: String?,
            prefix: String?,
            pointerDepth: Int
        ) {
            var i: Int
            val classInfo = FindClassInfo(classType) ?: return
            if (!classInfo.superType.isNullOrEmpty()) {
                WriteClass_r(classPtr, className, classInfo.superType!!, scope, prefix, pointerDepth)
            }
            i = 0
            while (classInfo.variables!![i].name != null) {
                val classVar = classInfo.variables!![i]
                classPtr.position(classVar.offset)
                WriteVariable_r(classPtr, classVar.name, classVar.type, classType, prefix, pointerDepth)
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
            ) {
                var i: Int = FindChar(value, '#', 0)
                while (i >= 0) {
                    if (idStr.Icmpn(value + i + 1, "INF", 3) == 0 || idStr.Icmpn(
                            value + i + 1,
                            "IND",
                            3
                        ) == 0 || idStr.Icmpn(
                            value + i + 1,
                            "NAN",
                            3
                        ) == 0 || idStr.Icmpn(
                            value + i + 1,
                            "QNAN",
                            4
                        ) == 0 || idStr.Icmpn(value + i + 1, "SNAN", 4) == 0
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
            ) {
                var i: Int = FindChar(value, '#', 0)
                while (i >= 0) {
                    if (idStr.Icmpn(value + i + 1, "INF", 3) == 0 || idStr.Icmpn(
                            value + i + 1,
                            "IND",
                            3
                        ) == 0 || idStr.Icmpn(
                            value + i + 1,
                            "NAN",
                            3
                        ) == 0 || idStr.Icmpn(
                            value + i + 1,
                            "QNAN",
                            4
                        ) == 0 || idStr.Icmpn(value + i + 1, "SNAN", 4) == 0
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
                if (varPtr != null && varSize > 0) {
                    // NOTE: skip renderer handles
                    if (IsRenderHandleVariable(varName, varType, scope, prefix, postfix, value)) {
                        return
                    }
                    // C++: memset(const_cast<void*>(varPtr), initValue, varSize)
                    Arrays.fill(varPtr.array(), 0, varSize, initValue.toByte())
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
                src.SkipUntilString("=")
                src.ExpectTokenType(Token.TT_STRING, 0, token)
                if (token.Cmp(value) != 0) {

                    // NOTE: skip several things
                    if (IsRenderHandleVariable(varName, varType, scope, prefix, postfix, value)) {
                        return
                    }
                    if (IsAllowedToChangedFromSaveGames(varName, varType, scope, prefix, postfix, value)) {
                        return
                    }
                    src.Warning("state diff for %s%s::%s%s\n%s\n%s", prefix, scope, varName, postfix, token, value)
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
            val fileName: idStr
            fileName = if (args!!.Argc() > 1) {
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
            val fileName: idStr
            fileName = if (args!!.Argc() > 1) {
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
            val name: idStr
            if (args!!.Argc() <= 1) {
                Game_local.gameLocal.Printf("testSaveGame <mapName>\n")
                return
            }
            name = idStr(args.Argv(1))
            try {
                CmdSystem.cmdSystem.BufferCommandText(cmdExecution_t.CMD_EXEC_NOW, Str.va("map %s", name))
                name.Replace("\\", "_")
                name.Replace("/", "_")
                CmdSystem.cmdSystem.BufferCommandText(cmdExecution_t.CMD_EXEC_NOW, Str.va("saveGame test_%s", name))
                CmdSystem.cmdSystem.BufferCommandText(cmdExecution_t.CMD_EXEC_NOW, Str.va("loadGame test_%s", name))
            } catch (ex: idException) {
                // an ERR_DROP was thrown
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
                    NoGameTypeInfo.classTypeInfo[j].superType!!,
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
                    NoGameTypeInfo.classTypeInfo.get(a).typeName!!,
                    NoGameTypeInfo.classTypeInfo.get(b).typeName!!
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
}

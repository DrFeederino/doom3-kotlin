/*
 * Copyright (C) 1999-2011 id Software LLC, a ZeniMax Media company.
 * Translated to Kotlin by Dr. Feederino with support of Claude Code
 *
 * This file is part of the Doom 3 Kotlin project.
 * Original source: neo/sys/sys_local.cpp, neo/sys/sys_local.h
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

package neo.sys

import neo.TempDump
import neo.framework.*
import neo.framework.CVarSystem.idCVar
import neo.framework.CmdSystem.idCmdSystem.ArgCompletion_String
import neo.framework.Common.Companion.common
import neo.idlib.Text.Str.idStr
import neo.idlib.idException
import neo.sys.sys_public.idSys
import neo.sys.sys_public.sysEventType_t
import neo.sys.sys_public.sysEvent_s
import java.awt.Desktop
import java.net.URI
import java.text.SimpleDateFormat
import java.util.*

class sys_local {

    /*
     ==============================================================

     idSysLocal

     ==============================================================
     */
    class idSysLocal : idSys() {
        private val startTime: Long = System.currentTimeMillis()

        override fun DebugPrintf(fmt: String, vararg arg: Any) {
            win_main.Sys_DebugVPrintf(fmt, *arg)
        }

        override fun DebugVPrintf(fmt: String, vararg arg: Any) {
            win_main.Sys_DebugVPrintf(fmt, *arg)
        }

        /*
         ================
         idSysLocal::GetMilliseconds
         ================
         */
        override fun GetMilliseconds(): Long {
            return (System.currentTimeMillis() - startTime)
        }

        /*
         ================
         idSysLocal::GetProcessorId
         ================
         */
        override fun GetProcessorId(): Int {
            return win_main.Sys_GetProcessorId()
        }

        override fun GetProcessorString(): String {
            return win_main.Sys_GetProcessorString()
        }

        override fun FPU_GetState(): String {
            return win_cpu.Sys_FPU_GetState()
        }

        override fun FPU_StackIsEmpty(): Boolean {
            return win_cpu.Sys_FPU_StackIsEmpty()
        }

        /*
         ================
         idSysLocal::FPU_SetFTZ
         ================
         */
        override fun FPU_SetFTZ(enable: Boolean) {
            win_cpu.Sys_FPU_SetFTZ(enable)
        }

        /*
         ================
         idSysLocal::FPU_SetDAZ
         ================
         */
        override fun FPU_SetDAZ(enable: Boolean) {
            win_cpu.Sys_FPU_SetDAZ(enable)
        }

        override fun FPU_EnableExceptions(exceptions: Int) {
            win_cpu.Sys_FPU_EnableExceptions(exceptions)
        }

        /*
         ================
         idSysLocal::LockMemory
         ================
         */
        override fun LockMemory(ptr: Any, bytes: Int): Boolean {
            return win_shared.Sys_LockMemory(ptr, bytes)
        }

        /*
         ================
         idSysLocal::UnlockMemory
         ================
         */
        override fun UnlockMemory(ptr: Any, bytes: Int): Boolean {
            return win_shared.Sys_UnlockMemory(ptr, bytes)
        }

        /*
         ================
         idSysLocal::DLL_Load
         ================
         */
        override fun DLL_Load(dllName: String): Int {
            return win_main.Sys_DLL_Load(dllName)
        }

        /*
         ================
         idSysLocal::DLL_GetProcAddress
         ================
         */
        override fun DLL_GetProcAddress(dllHandle: Int, procName: String): Any {
            return win_main.Sys_DLL_GetProcAddress(dllHandle, procName)
        }

        /*
         ================
         idSysLocal::DLL_Unload
         ================
         */
        override fun DLL_Unload(dllHandle: Int) {
            win_main.Sys_DLL_Unload(dllHandle)
        }

        /*
         ================
         idSysLocal::DLL_GetFileName
         ================
         */
        override fun DLL_GetFileName(baseName: String, dllName: Array<String>, maxLength: Int) {
            // NOTE: Differs from C++ — C++ uses BUILD_LIBRARY_SUFFIX macro at compile time.
            // Kotlin uses runtime platform detection instead.
            if (_WIN32) {
                idStr.snPrintf(dllName, maxLength, "%s" + sys_public.CPUSTRING + ".dll", baseName)
            } else if (__linux__) {
                idStr.snPrintf(dllName, maxLength, "%s" + sys_public.CPUSTRING + ".so", baseName)
            } else {
                throw idException("OS define is required")
            }
        }

        /*
         ================
         idSysLocal::GenerateMouseButtonEvent
         ================
         */
        override fun GenerateMouseButtonEvent(button: Int, down: Boolean): sysEvent_s {
            val ev = sysEvent_s()
            ev.evType = sysEventType_t.SE_KEY
            ev.evValue = KeyInput.K_MOUSE1 + button - 1
            ev.evValue2 = TempDump.btoi(down)
            ev.evPtrLength = 0
            ev.evPtr = null
            return ev
        }

        /*
         ================
         idSysLocal::GenerateMouseMoveEvent
         ================
         */
        override fun GenerateMouseMoveEvent(deltax: Int, deltay: Int): sysEvent_s {
            val ev = sysEvent_s()
            ev.evType = sysEventType_t.SE_MOUSE
            ev.evValue = deltax
            ev.evValue2 = deltay
            ev.evPtrLength = 0
            ev.evPtr = null
            return ev
        }

        /*
         ================
         idSysLocal::OpenURL
         // NOTE: Differs from C++ — C++ uses Win32 ShellExecute.
         // Kotlin uses java.awt.Desktop.browse() as the LWJGL/JVM equivalent.
         ================
         */
        override fun OpenURL(url: String, doExit: Boolean) {
            if (doexit_spamguard) {
                common.DPrintf("OpenURL: already in an exit sequence, ignoring %s\n", url)
                return
            }

            common.Printf("Open URL: %s\n", url)

            try {
                if (Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().browse(URI(url))
                } else {
                    common.Error("Could not open url: '%s' ", url)
                    return
                }
            } catch (e: Exception) {
                common.Error("Could not open url: '%s' ", url)
                return
            }

            if (doExit) {
                doexit_spamguard = true
                CmdSystem.cmdSystem.BufferCommandText(CmdSystem.cmdExecution_t.CMD_EXEC_APPEND, "quit\n")
            }
        }

        /*
         ================
         idSysLocal::StartProcess
         // NOTE: Differs from C++ — C++ uses Win32 CreateProcess.
         // Kotlin uses ProcessBuilder as the JVM equivalent.
         ================
         */
        override fun StartProcess(exePath: String, doExit: Boolean) {
            try {
                ProcessBuilder(exePath).start()
            } catch (e: Exception) {
                common.Error("Could not start process: '%s' ", exePath)
                return
            }

            if (doExit) {
                CmdSystem.cmdSystem.BufferCommandText(CmdSystem.cmdExecution_t.CMD_EXEC_APPEND, "quit\n")
            }
        }

        companion object {
            var doexit_spamguard = false
        }
    }

    companion object {
        var timeString: String? = null
        var sysLocal: idSysLocal = idSysLocal()
        val sysLanguageNames: Array<String?> = arrayOf(
            "english", "spanish", "italian", "german", "french", "russian",
            "polish", "korean", "japanese", "chinese", null
        )
        val sys_lang: idCVar = idCVar(
            "sys_lang",
            "english",
            CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_ARCHIVE,
            "",
            sysLanguageNames,
            ArgCompletion_String(sysLanguageNames)
        )

        /*
         =================
         Sys_TimeStampToStr
         =================
         */
        fun Sys_TimeStampToStr(/*ID_TIME_T*/ timeStamp: Long): String {
            // FIX: Was ignoring timeStamp parameter and using current time (Date()).
            // C++ uses localtime(&timeStamp) which converts the passed timestamp.
            // ID_TIME_T is time_t (seconds since epoch), so multiply by 1000 for Java ms.
            val time = Date(timeStamp * 1000)
            val out: String
            val lang = idStr(CVarSystem.cvarSystem.GetCVarString("sys_lang"))
            out = if (lang.Icmp("english") == 0) {
                // english gets "month/day/year  hour:min" + "am" or "pm"
                SimpleDateFormat("MM/dd/yyyy\thh:mmaa").format(time).lowercase(Locale.getDefault())
            } else {
                // europeans get "day/month/year  24hour:min"
                SimpleDateFormat("dd/MM/yyyy\tHH:mm").format(time)
            }
            return out.also { timeString = it }
        }
    }
}
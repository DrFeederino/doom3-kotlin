/*
===========================================================================

Doom 3 GPL Source Code
Copyright (C) 1999-2011 id Software LLC, a ZeniMax Media company.

This file is part of the Doom 3 GPL Source Code ("Doom 3 Source Code").

Doom 3 Source Code is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

Doom 3 Source Code is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with Doom 3 Source Code.  If not, see <http://www.gnu.org/licenses/>.

In addition, the Doom 3 Source Code is also subject to certain additional terms. You should have received a copy of these additional terms immediately following the terms and conditions of the GNU General Public License which accompanied the Doom 3 Source Code.  If not, please request a copy in writing from id Software at the address below.

If you have questions concerning this license or the applicable additional terms, you may contact in writing id Software LLC, c/o ZeniMax Media Inc., Suite 120, Rockville, Maryland 20850 USA.

===========================================================================
*/

package neo.sys

import neo.TempDump
import neo.framework.*
import neo.framework.CVarSystem.idCVar
import neo.framework.CmdSystem.idCmdSystem.ArgCompletion_String
import neo.framework.Common.Companion.common
import neo.idlib.Text.Str.idStr
import neo.idlib.idException
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
            if (WIN32) {
                dllName[0] = baseName + CPUSTRING + ".dll"
            } else if (MACOS_X) {
                dllName[0] = "lib" + baseName + CPUSTRING + ".dylib"
            } else if (__linux__) {
                dllName[0] = baseName + CPUSTRING + ".so"
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
        fun Sys_TimeStampToStr(timeStamp: Long): String {
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
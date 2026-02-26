package neo.sys

import neo.TempDump
import neo.TempDump.TODO_Exception
import neo.framework.BuildDefines
import neo.framework.CVarSystem
import neo.framework.CVarSystem.idCVar
import neo.framework.CmdSystem.idCmdSystem.ArgCompletion_String
import neo.framework.KeyInput
import neo.idlib.Text.Str.idStr
import neo.idlib.idException
import neo.sys.sys_public.idSys
import neo.sys.sys_public.sysEventType_t
import neo.sys.sys_public.sysEvent_s
import java.text.SimpleDateFormat
import java.util.*

class sys_local {

    /*
     =================
     Sys_TimeStampToStr
     =================
     */


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

        override fun GetMilliseconds(): Long {
            return (System.currentTimeMillis() - startTime)
        }

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

        override fun FPU_SetFTZ(enable: Boolean) {
            win_cpu.Sys_FPU_SetFTZ(enable)
        }

        override fun FPU_SetDAZ(enable: Boolean) {
            win_cpu.Sys_FPU_SetDAZ(enable)
        }

        override fun FPU_EnableExceptions(exceptions: Int) {
            win_cpu.Sys_FPU_EnableExceptions(exceptions)
        }

        override fun LockMemory(ptr: Any, bytes: Int): Boolean {
            return win_shared.Sys_LockMemory(ptr, bytes)
        }

        override fun UnlockMemory(ptr: Any, bytes: Int): Boolean {
            return win_shared.Sys_UnlockMemory(ptr, bytes)
        }

        override fun DLL_Load(dllName: String): Int {
            return win_main.Sys_DLL_Load(dllName)
        }

        override fun DLL_GetProcAddress(dllHandle: Int, procName: String): Any {
            return win_main.Sys_DLL_GetProcAddress(dllHandle, procName)
        }

        override fun DLL_Unload(dllHandle: Int) {
            win_main.Sys_DLL_Unload(dllHandle)
        }

        override fun DLL_GetFileName(baseName: String, dllName: Array<String>, maxLength: Int) {
            if (BuildDefines._WIN32) {
                idStr.snPrintf(dllName, maxLength, "%s" + sys_public.CPUSTRING + ".dll", baseName)
            } else if (BuildDefines.__linux__) {
                idStr.snPrintf(dllName, maxLength, "%s" + sys_public.CPUSTRING + ".so", baseName)
                // #elif defined( MACOS_X )
                // idStr::snPrintf( dllName, maxLength, "%s" ".dylib", baseName );
            } else {
// #error OS define is required
                throw idException("OS define is required")
                // #endif
            }
        }

        override fun GenerateMouseButtonEvent(button: Int, down: Boolean): sysEvent_s {
            val ev = sysEvent_s()
            ev.evType = sysEventType_t.SE_KEY
            ev.evValue = KeyInput.K_MOUSE1 + button - 1
            ev.evValue2 = TempDump.btoi(down)
            ev.evPtrLength = 0
            ev.evPtr = null
            return ev
        }

        override fun GenerateMouseMoveEvent(deltax: Int, deltay: Int): sysEvent_s {
            val ev = sysEvent_s()
            ev.evType = sysEventType_t.SE_MOUSE
            ev.evValue = deltax
            ev.evValue2 = deltay
            ev.evPtrLength = 0
            ev.evPtr = null
            return ev
        }

        override fun OpenURL(url: String, doExit: Boolean) {
            throw TODO_Exception()
            //            HWND wnd;
//
//            if (doexit_spamguard) {
//                common.DPrintf("OpenURL: already in an exit sequence, ignoring %s\n", url);
//                return;
//            }
//
//            common.Printf("Open URL: %s\n", url);
//
//            if (!ShellExecute(null, "open", url, null, null, SW_RESTORE)) {
//                common.Error("Could not open url: '%s' ", url);
//                return;
//            }
//
//            wnd = GetForegroundWindow();
//            if (wnd) {
//                ShowWindow(wnd, SW_MAXIMIZE);
//            }
//
//            if (doExit) {
//                doexit_spamguard = true;
//                cmdSystem.BufferCommandText(CMD_EXEC_APPEND, "quit\n");
//            }
        }

        override fun StartProcess(exePath: String, doExit: Boolean) {
            throw TODO_Exception()
            //            char[] szPathOrig = new char[_MAX_PATH];
//            STARTUPINFO si;
//            PROCESS_INFORMATION pi;
//
//            ZeroMemory(si, sizeof(si));
//            si.cb = sizeof(si);
//
//            strncpy(szPathOrig, exePath, _MAX_PATH);
//
//            if (!CreateProcess(null, szPathOrig, null, null, FALSE, 0, null, null, si, pi)) {
//                common.Error("Could not start process: '%s' ", szPathOrig);
//                return;
//            }
//
//            if (doExit) {
//                cmdSystem.BufferCommandText(CMD_EXEC_APPEND, "quit\n");
//            }
        }

        companion object {
            var doexit_spamguard = false
        }
    }

    companion object {
        var timeString //= new char[MAX_STRING_CHARS];
                : String? = null
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

        fun Sys_TimeStampToStr(   /*ID_TIME_T*/timeStamp: Long): String {
//        timeString[0] = '\0';

//        tm time = localtime(timeStamp);
            val time = Date()
            val out: String
            val lang = idStr(CVarSystem.cvarSystem.GetCVarString("sys_lang"))
            out = if (lang.Icmp("english") == 0) {
                // english gets "month/day/year  hour:min" + "am" or "pm"
                SimpleDateFormat("MM/dd/yyyy\thh:mmaa").format(time).lowercase(Locale.getDefault())
                //            out.oSet(va("%02d", time.tm_mon + 1));
//            out.oPluSet("/");
//            out.oPluSet(va("%02d", time.tm_mday));
//            out.oPluSet("/");
//            out.oPluSet(va("%d", time.tm_year + 1900));
//            out.oPluSet("\t");
//            if (time.tm_hour > 12) {
//                out.oPluSet(va("%02d", time.tm_hour - 12));
//            } else if (time.tm_hour == 0) {
//                out.oPluSet("12");
//            } else {
//                out.oPluSet(va("%02d", time.tm_hour));
//            }
//            out.oPluSet(":");
//            out.oPluSet(va("%02d", time.tm_min));
//            if (time.tm_hour >= 12) {
//                out.oPluSet("pm");
//            } else {
//                out.oPluSet("am");
//            }
            } else {
                // europeans get "day/month/year  24hour:min"
                SimpleDateFormat("dd/MM/yyyy\tHH:mm").format(time)
                //            out.oSet(va("%02d", time.tm_mday));
//            out.oPluSet("/");
//            out.oPluSet(va("%02d", time.tm_mon + 1));
//            out.oPluSet("/");
//            out.oPluSet(va("%d", time.tm_year + 1900));
//            out.oPluSet("\t");
//            out.oPluSet(va("%02d", time.tm_hour));
//            out.oPluSet(":");
//            out.oPluSet(va("%02d", time.tm_min));
            }
            //        idStr.Copynz(timeString, out, sizeof(timeString));
//
            return out.also { timeString = it }
        }
    }
}
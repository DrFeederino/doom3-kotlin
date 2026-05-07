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

import neo.framework.Async.AsyncNetwork.idAsyncNetwork
import neo.framework.CVarSystem
import neo.framework.CVarSystem.idCVar
import neo.framework.CmdSystem
import neo.framework.CmdSystem.cmdFunction_t
import neo.framework.Common
import neo.framework.FileSystem_h
import neo.idlib.CmdArgs
import neo.idlib.MAX_STRING_CHARS
import neo.idlib.Text.Lexer.idLexer
import neo.idlib.Text.Str.idStr
import neo.idlib.Text.Token.idToken
import neo.idlib.Text.atobb
import neo.idlib.Text.ctos
import neo.idlib.containers.idStrList
import neo.idlib.idException
import neo.idlib.idLib
import neo.sys.win_local.Win32Vars_t
import org.lwjgl.glfw.GLFW.*
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.UnsupportedFlavorException
import java.io.File
import java.io.FilenameFilter
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Paths
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.ReentrantLock
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.system.exitProcess

/*
 *
 *
 *
 *                    _
 *                   (_)
 *  _ __ ___    __ _  _  _ __
 * | '_ ` _ \  / _` || || '_ \
 * | | | | | || (_| || || | | |
 * |_| |_| |_| \__,_||_||_| |_|
 *
 *
 *
 *
 *
 *
 *
 *
 */

/*
 ==================
 SysThreading

 Central threading state, mirrors C++ file-level statics in threads.cpp.
 All mutex/condition state for the threading primitives.
 ==================
 */
object SysThreading {
    val mutex: Array<ReentrantLock> = Array(MAX_CRITICAL_SECTIONS) { ReentrantLock(true) }

    // All conditions use mutex[CRITICAL_SECTION_SYS] — matches dhewm3 threads.cpp
    val cond: Array<Condition> = Array(MAX_TRIGGER_EVENTS) { mutex[CRITICAL_SECTION_SYS].newCondition() }
    val signaled: BooleanArray = BooleanArray(MAX_TRIGGER_EVENTS)
    val waiting: BooleanArray = BooleanArray(MAX_TRIGGER_EVENTS)

    val threads: Array<xthreadInfo?> = arrayOfNulls(MAX_THREADS)
    var threadCount: Int = 0

    @Volatile
    var mainThreadId: Long = Thread.currentThread().id
    var mainThreadIdSet: Boolean = false

    fun init() {
        mainThreadId = Thread.currentThread().id
        mainThreadIdSet = true
        for (i in 0 until MAX_TRIGGER_EVENTS) {
            signaled[i] = false
            waiting[i] = false
        }
        for (i in 0 until MAX_THREADS) threads[i] = null
        threadCount = 0
    }

    fun shutdown() {
        for (i in 0 until MAX_THREADS) {
            if (threads[i] != null) {
                Common.common.Printf("WARNING: Thread '%s' still running\n", threads[i]!!.name ?: "unknown")
                threads[i] = null
            }
        }
        for (i in 0 until MAX_TRIGGER_EVENTS) {
            signaled[i] = false
            waiting[i] = false
        }
        threadCount = 0
    }
}

fun main(args: Array<String>) {
    win_main.main(args)
}

object win_main {
    const val MAXPRINTMSG = 4096
    const val MAX_QUED_EVENTS = 256
    const val MASK_QUED_EVENTS = MAX_QUED_EVENTS - 1
    const val SET_THREAD_AFFINITY = false
    val sys_cmdline: StringBuilder = StringBuilder(MAX_STRING_CHARS)
    val sys_showMallocs: idCVar = idCVar("sys_showMallocs", "0", CVarSystem.CVAR_SYSTEM, "")
    var hTimer: ScheduledExecutorService? = null
    var debug_current_alloc = 0
    var debug_current_alloc_count = 0
    var debug_frame_alloc = 0
    var debug_frame_alloc_count = 0
    var debug_total_alloc = 0
    var debug_total_alloc_count = 0
    var eventHead = 0

    /*
     ========================================================================

     EVENT LOOP

     ========================================================================
     */
    var eventQue: Array<sysEvent_s> = Array(MAX_QUED_EVENTS) { sysEvent_s() }
    var eventTail = 0
    var parmBytes = 0
    private var count = 0
    private var entered = false

    /*
     ==================
     Sys_EnterCriticalSection
     ==================
     */
    fun Sys_EnterCriticalSection(index: Int = CRITICAL_SECTION_ZERO) {
        assert(index in 0 until MAX_CRITICAL_SECTIONS)
        SysThreading.mutex[index].lock()
    }

    /*
     ==================
     Sys_LeaveCriticalSection
     ==================
     */

    fun Sys_LeaveCriticalSection(index: Int = CRITICAL_SECTION_ZERO) {
        assert(index in 0 until MAX_CRITICAL_SECTIONS)
        SysThreading.mutex[index].unlock()
    }

    /*
     ==================
     Sys_WaitForEvent
     ==================
     */

    fun Sys_WaitForEvent(index: Int = TRIGGER_EVENT_ZERO) {
        assert(index in 0 until MAX_TRIGGER_EVENTS)

        Sys_EnterCriticalSection(CRITICAL_SECTION_SYS)

        assert(!SysThreading.waiting[index]) // WaitForEvent from multiple threads not supported
        if (SysThreading.signaled[index]) {
            // Signal already raised — clear and pass through (Win32 auto-reset semantics)
            SysThreading.signaled[index] = false
        } else {
            SysThreading.waiting[index] = true
            SysThreading.cond[index].await()
            SysThreading.waiting[index] = false
        }

        Sys_LeaveCriticalSection(CRITICAL_SECTION_SYS)
    }

    /*
     ==================
     Sys_TriggerEvent
     ==================
     */
    fun Sys_TriggerEvent(index: Int = TRIGGER_EVENT_ZERO) {
        assert(index in 0 until MAX_TRIGGER_EVENTS)

        Sys_EnterCriticalSection(CRITICAL_SECTION_SYS)

        if (SysThreading.waiting[index]) {
            SysThreading.cond[index].signal()
        } else {
            // Latch the signal for the next wait
            SysThreading.signaled[index] = true
        }

        Sys_LeaveCriticalSection(CRITICAL_SECTION_SYS)
    }

    /*
     ==================
     Sys_CreateThread
     ==================
     */
    fun Sys_CreateThread(function: xthread_t, parms: Any?, info: xthreadInfo, name: String) {
        Sys_EnterCriticalSection()

        val thread = Thread({
            function(parms)
        }, name)
        thread.isDaemon = true

        info.name = name
        info.threadHandle = thread
        info.threadId = thread.id

        if (SysThreading.threadCount < MAX_THREADS) {
            SysThreading.threads[SysThreading.threadCount++] = info
        } else {
            Common.common.DPrintf("WARNING: MAX_THREADS reached\n")
        }

        thread.start()

        Sys_LeaveCriticalSection()
    }

    /*
     ==================
     Sys_DestroyThread
     ==================
     */
    fun Sys_DestroyThread(info: xthreadInfo) {
        assert(info.threadHandle != null)

        info.threadHandle!!.join()

        info.name = null
        info.threadHandle = null
        info.threadId = 0

        Sys_EnterCriticalSection()

        for (i in 0 until SysThreading.threadCount) {
            if (info === SysThreading.threads[i]) {
                for (j in i + 1 until SysThreading.threadCount) {
                    SysThreading.threads[j - 1] = SysThreading.threads[j]
                }
                SysThreading.threads[SysThreading.threadCount - 1] = null
                SysThreading.threadCount--
                break
            }
        }

        Sys_LeaveCriticalSection()
    }

    /*
     ==================
     Sys_InitThreads
     ==================
     */
    fun Sys_InitThreads() {
        SysThreading.init()
    }

    /*
     ==================
     Sys_ShutdownThreads
     ==================
     */
    fun Sys_ShutdownThreads() {
        SysThreading.shutdown()
    }

    /*
     ==================
     Sys_IsMainThread
     ==================
     */
    fun Sys_IsMainThread(): Boolean {
        return if (SysThreading.mainThreadIdSet) {
            Thread.currentThread().id == SysThreading.mainThreadId
        } else {
            true
        }
    }

    /*
     ==================
     Sys_GetThreadName
     ==================
     */
    fun Sys_GetThreadName(): String {
        Sys_EnterCriticalSection()
        val id = Thread.currentThread().id
        for (i in 0 until SysThreading.threadCount) {
            if (id == SysThreading.threads[i]?.threadId) {
                val name = SysThreading.threads[i]?.name ?: "unknown"
                Sys_LeaveCriticalSection()
                return name
            }
        }
        Sys_LeaveCriticalSection()
        return "main"
    }

    /*
     ==================
     Sys_DebugMemory_f
     ==================
     */
    fun Sys_DebugMemory_f() {
        Common.common.Printf("Total allocation %8dk in %d blocks\n", debug_total_alloc / 1024, debug_total_alloc_count)
        Common.common.Printf(
            "Current allocation %8dk in %d blocks\n",
            debug_current_alloc / 1024,
            debug_current_alloc_count
        )
    }

    /*
     ==================
     Sys_MemFrame
     ==================
     */
    fun Sys_MemFrame() {
        if (sys_showMallocs.GetInteger() != 0) {
            Common.common.Printf("Frame: %8dk in %5d blocks\n", debug_frame_alloc / 1024, debug_frame_alloc_count)
        }
        debug_frame_alloc = 0
        debug_frame_alloc_count = 0
    }

    /*
     =============
     Sys_Error

     Show the early console as an error dialog
     =============
     */
    fun Sys_Error(fmt: String, vararg arg: Any) {
        val text = StringBuilder(4096)

        text.append(String.format(fmt, *arg))
        win_syscon.Conbuf_AppendText(text.toString())
        win_syscon.Conbuf_AppendText("\n")
        win_syscon.Win_SetErrorText(text.toString())
        win_syscon.Sys_ShowConsole(1, true)

        win_input.Sys_ShutdownInput()
        win_glimp.GLimp_Shutdown()

        System.err.println(text)
        exitProcess(1)
    }

    /*
     ==============
     Sys_Quit
     ==============
     */
    fun Sys_Quit() {
        win_input.Sys_ShutdownInput()
        win_syscon.Sys_DestroyConsole()
        exitProcess(0)
    }

    /*
     ==============
     Sys_Printf
     ==============
     */
    fun Sys_Printf(fmt: String, vararg arg: Any) {
        val msg = StringBuilder(MAXPRINTMSG)
        msg.append(String.format(fmt, *arg))
        if (Win32Vars_t.win_outputDebugString.GetBool()) {
            print(msg)
        }
        if (Win32Vars_t.win_outputEditString.GetBool()) {
            win_syscon.Conbuf_AppendText(msg.toString())
        }
    }

    /*
     ==============
     Sys_DebugPrintf
     ==============
     */
    fun Sys_DebugPrintf(fmt: String, vararg arg: Any) {
        System.out.printf(
            fmt.trimIndent(), *arg
        )
    }

    /*
     ==============
     Sys_DebugVPrintf
     ==============
     */
    fun Sys_DebugVPrintf(fmt: String, vararg arg: Any) {
        System.out.printf(fmt, *arg)
    }

    /*
     ==============
     Sys_Sleep
     ==============
     */
    fun Sys_Sleep(msec: Int) {
        Thread.sleep(msec.toLong())
    }

    /*
     ==============
     Sys_ShowWindow
     ==============
     */
    fun Sys_ShowWindow(show: Boolean) {
        if (win_glimp.window == 0L) return
        if (show) {
            glfwShowWindow(win_glimp.window)
        } else {
            glfwHideWindow(win_glimp.window)
        }
    }

    /*
     ==============
     Sys_IsWindowVisible
     ==============
     */
    fun Sys_IsWindowVisible(): Boolean {
        if (win_glimp.window == 0L) return false
        return glfwGetWindowAttrib(win_glimp.window, GLFW_VISIBLE) == GLFW_TRUE
    }

    /*
     ==============
     Sys_Mkdir
     ==============
     */
    fun Sys_Mkdir(path: String) {
        Paths.get(path).toFile().mkdir()
    }

    fun Sys_Mkdir(path: idStr) {
        Sys_Mkdir(path.toString())
    }

    /*
     =================
     Sys_FileTimeStamp
     =================
     */
    fun  /*ID_TIME_T*/Sys_FileTimeStamp(fp: String): Long {
        val st = Paths.get(fp).toFile()
        return if (st.exists()) {
            st.lastModified() / 1000
        } else 0
    }

    fun Sys_Cwd(): String {
        return System.getProperty("user.dir")
    }

    /*
     ==============
     Sys_DefaultCDPath
     ==============
     */
    fun Sys_DefaultCDPath(): String {
        return ""
    }

    /*
     ========================================================================

     DLL Loading

     ========================================================================
     */
    /*
     ==============
     Sys_DefaultBasePath
     ==============
     */
    fun Sys_DefaultBasePath(): String {
        return Sys_Cwd()
    }

    /*
     ==============
     Sys_DefaultSavePath
     ==============
     */
    fun Sys_DefaultSavePath(): String {
        return idLib.cvarSystem.GetCVarString("fs_basepath")
    }

    /*
     ==============
     Sys_EXEPath
     ==============
     */
    fun Sys_EXEPath(): String {
        return System.getProperty("user.dir")
    }

    /*
     ==============
     Sys_ListFiles
     ==============
     */
    fun Sys_ListFiles(directory: String, extension: String, list: idStrList): Int {
        val search: FilenameFilter
        val findinfo: File
        search = FilenameFilter { pathname: File, name: String ->
            // passing a slash as extension will find directories
            if (extension == "/") {
                return@FilenameFilter pathname.isDirectory()
            } else {
                return@FilenameFilter name.endsWith(extension)
            }
        }
        findinfo = File(directory)

        // search
        list.clear()
        if (!findinfo.exists()) {
            return -1
        }
        val files = findinfo.listFiles(search)
        if (files != null) {
            for (file in files) {
                list.add(file.name)
            }
        }

        return list.size()
    }

    /*
     ================
     Sys_GetClipboardData
     ================
     */
    fun Sys_GetClipboardData(): String? {
        try {
            return Toolkit.getDefaultToolkit().systemClipboard.getData(DataFlavor.stringFlavor) as String
        } catch (ex: UnsupportedFlavorException) {
            Logger.getLogger(win_main::class.java.name).log(Level.SEVERE, null, ex)
        } catch (ex: IOException) {
            Logger.getLogger(win_main::class.java.name).log(Level.SEVERE, null, ex)
        }
        return null
    }

    /*
     ================
     Sys_SetClipboardData
     ================
     */
    fun Sys_SetClipboardData(string: String) {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(string), null)
    }

    fun Sys_SetClipboardData(string: CharArray) {
        Sys_SetClipboardData(ctos(string))
    }

    /*
     =====================
     Sys_DLL_Load

     The Kotlin port is monolithic (GAME_DLL = false) — game logic ships
     in the same JAR. Loading external native DLLs is intentionally unsupported.
     =====================
     */
    fun Sys_DLL_Load(dllName: String): Int {
        idLib.common.Warning("Sys_DLL_Load(%s): native DLL loading not supported in Kotlin port", dllName)
        return 0
    }

    fun Sys_DLL_GetProcAddress(dllHandle: Int, procName: String): Any? {
        return null
    }

    fun Sys_DLL_Unload(dllHandle: Int) {
    }

    /*
     ================
     Sys_QueEvent

     Ptr should either be null, or point to a block of data that can
     be freed by the game later.
     ================
     */
    fun Sys_QueEvent(time: Long, type: sysEventType_t, value: Int, value2: Int, ptrLength: Int, ptr: ByteBuffer?) {
        val ev: sysEvent_s
        // FIX: Check overflow BEFORE replacing the slot, so old event data can be freed
        if (eventHead - eventTail >= MAX_QUED_EVENTS) {
            Common.common.Printf("Sys_QueEvent: overflow\n")
            // we are discarding an event, but don't leak memory
            eventQue[eventHead and MASK_QUED_EVENTS].evPtr?.clear()
            eventTail++
        }
        eventQue[eventHead and MASK_QUED_EVENTS] = sysEvent_s()
        ev = eventQue[eventHead and MASK_QUED_EVENTS]
        eventHead++
        ev.evType = type
        ev.evValue = value
        ev.evValue2 = value2
        ev.evPtrLength = ptrLength
        ev.evPtr = ptr
    }

    //================================================================
    fun Sys_GenerateEvents() {
        val s: String?
        if (entered) {
            return
        }
        entered = true

        // check for console commands
        s = win_syscon.Sys_ConsoleInput()
        if (s != null) {
            val len: Int
            len = s.length
            Sys_QueEvent(0, sysEventType_t.SE_CONSOLE, 0, 0, len, atobb(s)!!)
        }
        entered = false
    }

    /*
     ================
     Sys_ClearEvents
     ================
     */
    fun Sys_ClearEvents() {
        eventTail = 0
        eventHead = eventTail
    }

    /*
     ================
     Sys_GetEvent
     ================
     */
    fun Sys_GetEvent(): sysEvent_s {
        val ev: sysEvent_s

        // return if we have data
        if (eventHead > eventTail) {
            eventTail++
            return eventQue[eventTail - 1 and MASK_QUED_EVENTS]
        }

        // return the empty event
        ev = sysEvent_s()
        return ev
    }

    fun Sys_StartAsyncThread() {
        // Async thread is now created by Common.Init() directly.
        // This function exists for API compatibility.
    }

    /*
     ================
     Sys_Init

     The cvar system must already be setup
     ================
     */
    fun Sys_Init() {
        CmdSystem.cmdSystem.AddCommand(
            "in_restart",
            Sys_In_Restart_f.INSTANCE,
            CmdSystem.CMD_FL_SYSTEM,
            "restarts the input system"
        )


        // Windows user name
        Win32Vars_t.win_username.SetString(win_shared.Sys_GetCurrentUser())

        // Windows version
        Win32Vars_t.sys_arch.SetString(System.getProperty("os.name"))

        // CPU type
        if (idStr.Icmp(Win32Vars_t.sys_cpustring.GetString()!!, "detect") == 0) {
            val string: idStr
            win_local.win32.cpuid = win_cpu.Sys_GetProcessorId()
            string = idStr() //Clear();
            string.plusAssign("generic CPU")
            string.plusAssign(" with ")
            if (win_local.win32.cpuid and CPUID_MMX != 0) {
                string.plusAssign("MMX & ")
            }
            if (win_local.win32.cpuid and CPUID_3DNOW != 0) {
                string.plusAssign("3DNow! & ")
            }
            if (win_local.win32.cpuid and CPUID_SSE != 0) {
                string.plusAssign("SSE & ")
            }
            if (win_local.win32.cpuid and CPUID_SSE2 != 0) {
                string.plusAssign("SSE2 & ")
            }
            if (win_local.win32.cpuid and CPUID_SSE3 != 0) {
                string.plusAssign("SSE3 & ")
            }
            string.StripTrailing(" & ")
            string.StripTrailing(" with ")
            Win32Vars_t.sys_cpustring.SetString(string.toString())
        } else {
            Common.common.Printf("forcing CPU type to ")
            val src = idLexer(
                Win32Vars_t.sys_cpustring.GetString()!!,
                Win32Vars_t.sys_cpustring.GetString()!!.length,
                "sys_cpustring"
            )
            val token = idToken()
            var id = CPUID_NONE
            while (src.ReadToken(token)) {
                if (token.Icmp("generic") == 0) {
                    id = id or CPUID_GENERIC
                } else if (token.Icmp("mmx") == 0) {
                    id = id or CPUID_MMX
                } else if (token.Icmp("3dnow") == 0) {
                    id = id or CPUID_3DNOW
                } else if (token.Icmp("sse") == 0) {
                    id = id or CPUID_SSE
                } else if (token.Icmp("sse2") == 0) {
                    id = id or CPUID_SSE2
                } else if (token.Icmp("sse3") == 0) {
                    id = id or CPUID_SSE3
                }
            }
            if (id == CPUID_NONE) {
                Common.common.Printf(
                    "WARNING: unknown sys_cpustring '%s'\n",
                    Win32Vars_t.sys_cpustring.GetString()!!
                )
                id = CPUID_GENERIC
            }
            win_local.win32.cpuid = id
        }
        Common.common.Printf("%s\n", Win32Vars_t.sys_cpustring.GetString()!!)
        Common.common.Printf("%d MB System Memory\n", win_shared.Sys_GetSystemRam())
    }

    /*
     ================
     Sys_Shutdown
     ================
     */
    fun Sys_Shutdown() {
        Sys_ShutdownThreads()
    }

    /*
     ================
     Sys_GetProcessorId
     ================
     */
    fun  /*cpuid_t*/Sys_GetProcessorId(): Int {
        return win_local.win32.cpuid
    }

    /*
     ================
     Sys_GetProcessorString
     ================
     */

    fun Sys_GetProcessorString(): String {
        return Win32Vars_t.sys_cpustring.GetString()!!
    }

    /*
     ====================
     Win_Frame
     ====================
     */
    fun Win_Frame() {
        // if "viewlog" has been modified, show or hide the log console
        if (Win32Vars_t.win_viewlog.IsModified()) {
            if (!Common.com_skipRenderer.GetBool() && idAsyncNetwork.serverDedicated.GetInteger() != 1) {
                win_syscon.Sys_ShowConsole(Win32Vars_t.win_viewlog.GetInteger(), false)
            }
            Win32Vars_t.win_viewlog.ClearModified()
        }
    }

    fun remove(path: String): Boolean {
        return Paths.get(path).toFile().delete()
    }

    fun remove(path: idStr): Boolean {
        return remove(path.toString())
    }

    @Throws(IOException::class)
    fun tmpfile(): FileChannel {
        val tmp = File.createTempFile("bla", "bla")
        tmp.deleteOnExit()
        return FileChannel.open(tmp.toPath(), FileSystem_h.fopenOptions("wb+"))
    }


    fun main(lpCmdLine: Array<String>) { //cmd arguments need to be escaped and surrounded by quotes to preserve spacing.
        idStr.Copynz(sys_cmdline, *lpCmdLine)

        // done before Com/Sys_Init since we need this for error output
        win_syscon.Sys_CreateConsole()

        // get the initial time base
        win_shared.Sys_Milliseconds()

        // initialize threading primitives
        Sys_InitThreads()

        Common.common.Init(0, null, sys_cmdline.toString())

        Sys_StartAsyncThread()

        // hide or show the early console as necessary
        if (Win32Vars_t.win_viewlog.GetInteger() != 0 || Common.com_skipRenderer.GetBool()
            || idAsyncNetwork.serverDedicated.GetInteger() != 0
        ) {
            win_syscon.Sys_ShowConsole(1, true)
        } else {
            win_syscon.Sys_ShowConsole(0, false)
        }

        if (sys_cmdline.indexOf("+debugger") >= 0) {
            win_syscon.Sys_ShowConsole(1, true)
            return
        }
        // main game loop
        while (true) {
            Win_Frame()

            // run the game
            Common.common.Frame()
        }
    }

    /*
     =================
     Sys_In_Restart_f

     Restart the input subsystem
     =================
     */
    class Sys_In_Restart_f private constructor() : cmdFunction_t() {
        @Throws(idException::class)
        override fun run(args: CmdArgs.idCmdArgs?) {
            win_input.Sys_ShutdownInput()
            win_input.Sys_InitInput()
        }

        companion object {
            val INSTANCE: cmdFunction_t = Sys_In_Restart_f()
        }
    }
}

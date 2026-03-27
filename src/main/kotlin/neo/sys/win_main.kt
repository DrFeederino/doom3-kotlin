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
import neo.TempDump.TODO_Exception
import neo.Tools.edit_public
import neo.framework.Async.AsyncNetwork.idAsyncNetwork
import neo.framework.CVarSystem
import neo.framework.CVarSystem.idCVar
import neo.framework.CmdSystem
import neo.framework.CmdSystem.cmdFunction_t
import neo.framework.Common
import neo.framework.ID_ALLOW_TOOLS
import neo.framework.UsercmdGen.USERCMD_MSEC
import neo.idlib.CmdArgs
import neo.idlib.MAX_STRING_CHARS
import neo.idlib.Text.Lexer.idLexer
import neo.idlib.Text.Str.idStr
import neo.idlib.Text.Token.idToken
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
import java.time.Instant
import java.util.concurrent.ScheduledExecutorService
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

fun main(args: Array<String>) {
    win_main.main(args)
}

object win_main {
    //TODO: rename to plain "main" or something.
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
        assert(index >= 0 && index < MAX_CRITICAL_SECTIONS)
        //		Sys_DebugPrintf( "busy lock '%s' in thread '%s'\n", lock->name, Sys_GetThreadName() );
        return
        //win_local.win32.criticalSections[index].lock()
    }

    /*
     ==================
     Sys_LeaveCriticalSection
     ==================
     */

    fun Sys_LeaveCriticalSection(index: Int = CRITICAL_SECTION_ZERO) {
        assert(index >= 0 && index < MAX_CRITICAL_SECTIONS)
//        if (win_local.win32.criticalSections[index].isLocked) {
//            win_local.win32.criticalSections[index].unlock()
//        }
    }

    /*
     ==================
     Sys_WaitForEvent
     ==================
     */

    fun Sys_WaitForEvent(index: Int = TRIGGER_EVENT_ZERO) {
        return
        //	assert( index == 0 );
//	if ( !win32.backgroundDownloadSemaphore ) {
//		win32.backgroundDownloadSemaphore = CreateEvent( NULL, TRUE, FALSE, NULL );
//	}
//	WaitForSingleObject( win32.backgroundDownloadSemaphore, INFINITE );
//	ResetEvent( win32.backgroundDownloadSemaphore );
    }

    /*
     ==================
     Sys_TriggerEvent
     ==================
     */
    fun Sys_TriggerEvent(index: Int = TRIGGER_EVENT_ZERO) {
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
     ==================
     Sys_FlushCacheMemory

     On windows, the vertex buffers are write combined, so they
     don't need to be flushed from the cache
     ==================
     */
    fun Sys_FlushCacheMemory(base: Any?, bytes: Int) {}

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
        val start = Instant.now().toEpochMilli()
        while (true) {
            if (Instant.now().toEpochMilli() - start >= msec) {
                return
            }
        }
//        Thread.sleep(msec.toLong())
        //LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(msec.toLong()))
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
        Sys_SetClipboardData(TempDump.ctos(string))
    }

    /*
     =============
     Sys_PumpEvents

     This allows windows to be moved during renderbump
     =============
     */
    /*
     =====================
     Sys_DLL_Load
     =====================
     */
    fun Sys_DLL_Load(dllName: String): Int {
        throw TODO_Exception()
    }

    /*
     =====================
     Sys_DLL_GetProcAddress
     =====================
     */
    fun Sys_DLL_GetProcAddress(dllHandle: Int, procName: String): Any {
        throw TODO_Exception()
    }

    /*
     =====================
     Sys_DLL_Unload
     =====================
     */
    fun Sys_DLL_Unload(dllHandle: Int) {
        throw TODO_Exception()
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

//        // pump the message loop
//        Sys_PumpEvents();
//
        // make sure mouse and joystick are only called once a frame
//        IN_Frame();//TODO:do we need this function?

        // check for console commands
        s = win_syscon.Sys_ConsoleInput()
        if (s != null) {
            val len: Int
            len = s.length
            Sys_QueEvent(0, sysEventType_t.SE_CONSOLE, 0, 0, len, TempDump.atobb(s)!!)
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
        // create an auto-reset event that happens 60 times a second
        Common.common.Async()
    }

    /*
     ================
     Sys_AlreadyRunning

     returns true if there is a copy of D3 running already
     ================
     */
    fun Sys_AlreadyRunning(): Boolean {
        if (true) {
            return false
        }
        throw TODO_Exception()
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

        //
        // CPU type
        //
        if (idStr.Icmp(Win32Vars_t.sys_cpustring.GetString()!!, "detect") == 0) {
            val string: idStr
            Common.common.Printf("%1.0f MHz ", win_cpu.Sys_ClockTicksPerSecond() / 1000000.0f)
            win_local.win32.cpuid = win_cpu.Sys_GetCPUId()
            string = idStr() //Clear();
            if (win_local.win32.cpuid and CPUID_AMD != 0) {
                string.plusAssign("AMD CPU")
            } else if (win_local.win32.cpuid and CPUID_INTEL != 0) {
                string.plusAssign("Intel CPU")
            } else if (win_local.win32.cpuid and CPUID_UNSUPPORTED != 0) {
                string.plusAssign("unsupported CPU")
            } else {
                string.plusAssign("generic CPU")
            }
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
            if (win_local.win32.cpuid and CPUID_HTT != 0) {
                string.plusAssign("HTT & ")
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
                } else if (token.Icmp("intel") == 0) {
                    id = id or CPUID_INTEL
                } else if (token.Icmp("amd") == 0) {
                    id = id or CPUID_AMD
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
                } else if (token.Icmp("htt") == 0) {
                    id = id or CPUID_HTT
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
        Common.common.Printf("%d MB Video Memory\n", win_shared.Sys_GetVideoRam())
    }

    /*
     ================
     Sys_Shutdown
     ================
     */
    fun Sys_Shutdown() {
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

    /*
     ====================
     TestChkStk
     ====================
     */
    fun TestChkStk() {
        throw TODO_Exception()
    }

    /*
     ====================
     HackChkStk
     ====================
     */
    fun HackChkStk() {
        throw TODO_Exception()
    }

    /*
     ====================
     GetExceptionCodeInfo
     ====================
     */
    fun GetExceptionCodeInfo(   /*UINT*/code: Int): String {
        throw TODO_Exception()
        //	switch( code ) {
//		case EXCEPTION_ACCESS_VIOLATION: return "The thread tried to read from or write to a virtual address for which it does not have the appropriate access.";
//		case EXCEPTION_ARRAY_BOUNDS_EXCEEDED: return "The thread tried to access an array element that is out of bounds and the underlying hardware supports bounds checking.";
//		case EXCEPTION_BREAKPOINT: return "A breakpoint was encountered.";
//		case EXCEPTION_DATATYPE_MISALIGNMENT: return "The thread tried to read or write data that is misaligned on hardware that does not provide alignment. For example, 16-bit values must be aligned on 2-byte boundaries; 32-bit values on 4-byte boundaries, and so on.";
//		case EXCEPTION_FLT_DENORMAL_OPERAND: return "One of the operands in a floating-point operation is denormal. A denormal value is one that is too small to represent as a standard floating-point value.";
//		case EXCEPTION_FLT_DIVIDE_BY_ZERO: return "The thread tried to divide a floating-point value by a floating-point divisor of zero.";
//		case EXCEPTION_FLT_INEXACT_RESULT: return "The result of a floating-point operation cannot be represented exactly as a decimal fraction.";
//		case EXCEPTION_FLT_INVALID_OPERATION: return "This exception represents any floating-point exception not included in this list.";
//		case EXCEPTION_FLT_OVERFLOW: return "The exponent of a floating-point operation is greater than the magnitude allowed by the corresponding type.";
//		case EXCEPTION_FLT_STACK_CHECK: return "The stack overflowed or underflowed as the result of a floating-point operation.";
//		case EXCEPTION_FLT_UNDERFLOW: return "The exponent of a floating-point operation is less than the magnitude allowed by the corresponding type.";
//		case EXCEPTION_ILLEGAL_INSTRUCTION: return "The thread tried to execute an invalid instruction.";
//		case EXCEPTION_IN_PAGE_ERROR: return "The thread tried to access a page that was not present, and the system was unable to load the page. For example, this exception might occur if a network connection is lost while running a program over the network.";
//		case EXCEPTION_INT_DIVIDE_BY_ZERO: return "The thread tried to divide an integer value by an integer divisor of zero.";
//		case EXCEPTION_INT_OVERFLOW: return "The result of an integer operation caused a carry out of the most significant bit of the result.";
//		case EXCEPTION_INVALID_DISPOSITION: return "An exception handler returned an invalid disposition to the exception dispatcher. Programmers using a high-level language such as C should never encounter this exception.";
//		case EXCEPTION_NONCONTINUABLE_EXCEPTION: return "The thread tried to continue execution after a noncontinuable exception occurred.";
//		case EXCEPTION_PRIV_INSTRUCTION: return "The thread tried to execute an instruction whose operation is not allowed in the current machine mode.";
//		case EXCEPTION_SINGLE_STEP: return "A trace trap or other single-instruction mechanism signaled that one instruction has been executed.";
//		case EXCEPTION_STACK_OVERFLOW: return "The thread used up its stack.";
//		default: return "Unknown exception";
//	}
    }

    /*
     ====================
     EmailCrashReport

     emailer originally from Raven/Quake 4
     ====================
     */
    //public static void EmailCrashReport( LPSTR messageText ) {throw new TODO_Exception();
    //	LPMAPISENDMAIL	MAPISendMail;
    //	MapiMessage		message;
    //	static int lastEmailTime = 0;
    //
    //	if ( Sys_Milliseconds() < lastEmailTime + 10000 ) {
    //		return;
    //	}
    //
    //	lastEmailTime = Sys_Milliseconds();
    //
    //	HINSTANCE mapi = LoadLibrary( "MAPI32.DLL" ); 
    //	if( mapi ) {
    //		MAPISendMail = ( LPMAPISENDMAIL )GetProcAddress( mapi, "MAPISendMail" );
    //		if( MAPISendMail ) {
    //			MapiRecipDesc toProgrammers =
    //			{
    //				0,										// ulReserved
    //					MAPI_TO,							// ulRecipClass
    //					"DOOM 3 Crash",						// lpszName
    //					"SMTP:programmers@idsoftware.com",	// lpszAddress
    //					0,									// ulEIDSize
    //					0									// lpEntry
    //			};
    //
    //			memset( &message, 0, sizeof( message ) );
    //			message.lpszSubject = "DOOM 3 Fatal Error";
    //			message.lpszNoteText = messageText;
    //			message.nRecipCount = 1;
    //			message.lpRecips = &toProgrammers;
    //
    //			MAPISendMail(
    //				0,									// LHANDLE lhSession
    //				0,									// ULONG ulUIParam
    //				&message,							// lpMapiMessage lpMessage
    //				MAPI_DIALOG,						// FLAGS flFlags
    //				0									// ULONG ulReserved
    //				);
    //		}
    //		FreeLibrary( mapi );
    //	}
    //}
    /*
     ====================
     _except_handler
     ====================
     */
    //public static EXCEPTION_DISPOSITION __cdecl _except_handler( struct _EXCEPTION_RECORD *ExceptionRecord, void * EstablisherFrame,
    //												struct _CONTEXT *ContextRecord, void * DispatcherContext ) {throw new TODO_Exception();
    //
    //	static char msg[ 8192 ];
    //	char FPUFlags[2048];
    //
    //	Sys_FPU_PrintStateFlags( FPUFlags, ContextRecord->FloatSave.ControlWord,
    //										ContextRecord->FloatSave.StatusWord,
    //										ContextRecord->FloatSave.TagWord,
    //										ContextRecord->FloatSave.ErrorOffset,
    //										ContextRecord->FloatSave.ErrorSelector,
    //										ContextRecord->FloatSave.DataOffset,
    //										ContextRecord->FloatSave.DataSelector );
    //
    //
    //	sprintf( msg, 
    //		"Please describe what you were doing when DOOM 3 crashed!\n"
    //		"If this text did not pop into your email client please copy and email it to programmers@idsoftware.com\n"
    //			"\n"
    //			"-= FATAL EXCEPTION =-\n"
    //			"\n"
    //			"%s\n"
    //			"\n"
    //			"0x%x at address 0x%08x\n"
    //			"\n"
    //			"%s\n"
    //			"\n"
    //			"EAX = 0x%08x EBX = 0x%08x\n"
    //			"ECX = 0x%08x EDX = 0x%08x\n"
    //			"ESI = 0x%08x EDI = 0x%08x\n"
    //			"EIP = 0x%08x ESP = 0x%08x\n"
    //			"EBP = 0x%08x EFL = 0x%08x\n"
    //			"\n"
    //			"CS = 0x%04x\n"
    //			"SS = 0x%04x\n"
    //			"DS = 0x%04x\n"
    //			"ES = 0x%04x\n"
    //			"FS = 0x%04x\n"
    //			"GS = 0x%04x\n"
    //			"\n"
    //			"%s\n",
    //			com_version.GetString(),
    //			ExceptionRecord->ExceptionCode,
    //			ExceptionRecord->ExceptionAddress,
    //			GetExceptionCodeInfo( ExceptionRecord->ExceptionCode ),
    //			ContextRecord->Eax, ContextRecord->Ebx,
    //			ContextRecord->Ecx, ContextRecord->Edx,
    //			ContextRecord->Esi, ContextRecord->Edi,
    //			ContextRecord->Eip, ContextRecord->Esp,
    //			ContextRecord->Ebp, ContextRecord->EFlags,
    //			ContextRecord->SegCs,
    //			ContextRecord->SegSs,
    //			ContextRecord->SegDs,
    //			ContextRecord->SegEs,
    //			ContextRecord->SegFs,
    //			ContextRecord->SegGs,
    //			FPUFlags
    //		);
    //
    //	EmailCrashReport( msg );
    //	common->FatalError( msg );
    //
    //    // Tell the OS to restart the faulting instruction
    //    return ExceptionContinueExecution;
    //}
    /*
     ==================
     WinMain
     ==================
     */
    //public static int WINAPI WinMain( HINSTANCE hInstance, HINSTANCE hPrevInstance, LPSTR lpCmdLine, int nCmdShow ) {
    //
    //	const HCURSOR hcurSave = ::SetCursor( LoadCursor( 0, IDC_WAIT ) );
    //
    //	Sys_SetPhysicalWorkMemory( 192 << 20, 1024 << 20 );
    //
    //	Sys_GetCurrentMemoryStatus( exeLaunchMemoryStats );
    //
    //#if 0
    //    DWORD handler = (DWORD)_except_handler;
    //    __asm
    //    {                           // Build EXCEPTION_REGISTRATION record:
    //        push    handler         // Address of handler function
    //        push    FS:[0]          // Address of previous handler
    //        mov     FS:[0],ESP      // Install new EXECEPTION_REGISTRATION
    //    }
    //#endif
    //
    //	win32.hInstance = hInstance;
    //	idStr::Copynz( sys_cmdline, lpCmdLine, sizeof( sys_cmdline ) );
    //
    //	// done before Com/Sys_Init since we need this for error output
    //	Sys_CreateConsole();
    //
    //	// no abort/retry/fail errors
    //	SetErrorMode( SEM_FAILCRITICALERRORS );
    //
    //	for ( int i = 0; i < MAX_CRITICAL_SECTIONS; i++ ) {
    //		InitializeCriticalSection( &win32.criticalSections[i] );
    //	}
    //
    //	// get the initial time base
    //	Sys_Milliseconds();
    //
    //#ifdef DEBUG
    //	// disable the painfully slow MS heap check every 1024 allocs
    //	_CrtSetDbgFlag( 0 );
    //#endif
    //
    ////	Sys_FPU_EnableExceptions( TEST_FPU_EXCEPTIONS );
    //	Sys_FPU_SetPrecision( FPU_PRECISION_DOUBLE_EXTENDED );
    //
    //	common->Init( 0, NULL, lpCmdLine );
    //
    //#if TEST_FPU_EXCEPTIONS != 0
    //	common->Printf( Sys_FPU_GetState() );
    //#endif
    //
    //#ifndef	ID_DEDICATED
    //	if ( win32.win_notaskkeys.GetInteger() ) {
    //		DisableTaskKeys( TRUE, FALSE, /*( win32.win_notaskkeys.GetInteger() == 2 )*/ FALSE );
    //	}
    //#endif
    //
    //	Sys_StartAsyncThread();
    //
    //	// hide or show the early console as necessary
    //	if ( win32.win_viewlog.GetInteger() || com_skipRenderer.GetBool() || idAsyncNetwork::serverDedicated.GetInteger() ) {
    //		Sys_ShowConsole( 1, true );
    //	} else {
    //		Sys_ShowConsole( 0, false );
    //	}
    //
    //#ifdef SET_THREAD_AFFINITY 
    //	// give the main thread an affinity for the first cpu
    //	SetThreadAffinityMask( GetCurrentThread(), 1 );
    //#endif
    //
    //	::SetCursor( hcurSave );
    //
    //	// Launch the script debugger
    //	if ( strstr( lpCmdLine, "+debugger" ) ) {
    //		// DebuggerClientInit( lpCmdLine );
    //		return 0;
    //	}
    //
    //	::SetFocus( win32.hWnd );
    //
    //    // main game loop
    //	while( 1 ) {
    //
    //		Win_Frame();
    //
    //#ifdef DEBUG
    //		Sys_MemFrame();
    //#endif
    //
    //		// set exceptions, even if some crappy syscall changes them!
    //		Sys_FPU_EnableExceptions( TEST_FPU_EXCEPTIONS );
    //
    //#ifdef ID_ALLOW_TOOLS
    //		if ( com_editors ) {
    //			if ( com_editors & EDITOR_GUI ) {
    //				// GUI editor
    //				GUIEditorRun();
    //			} else if ( com_editors & EDITOR_RADIANT ) {
    //				// Level Editor
    //				RadiantRun();
    //			}
    //			else if (com_editors & EDITOR_MATERIAL ) {
    //				//BSM Nerve: Add support for the material editor
    //				MaterialEditorRun();
    //			}
    //			else {
    //				if ( com_editors & EDITOR_LIGHT ) {
    //					// in-game Light Editor
    //					LightEditorRun();
    //				}
    //				if ( com_editors & EDITOR_SOUND ) {
    //					// in-game Sound Editor
    //					SoundEditorRun();
    //				}
    //				if ( com_editors & EDITOR_DECL ) {
    //					// in-game Declaration Browser
    //					DeclBrowserRun();
    //				}
    //				if ( com_editors & EDITOR_AF ) {
    //					// in-game Articulated Figure Editor
    //					AFEditorRun();
    //				}
    //				if ( com_editors & EDITOR_PARTICLE ) {
    //					// in-game Particle Editor
    //					ParticleEditorRun();
    //				}
    //				if ( com_editors & EDITOR_SCRIPT ) {
    //					// in-game Script Editor
    //					ScriptEditorRun();
    //				}
    //				if ( com_editors & EDITOR_PDA ) {
    //					// in-game PDA Editor
    //					PDAEditorRun();
    //				}
    //			}
    //		}
    //#endif
    //		// run the game
    //		common->Frame();
    //	}
    //
    //	// never gets here
    //	return 0;
    //}
    fun  /*__declspec( naked )*/clrstk() {
        throw TODO_Exception()
        //	// eax = bytes to add to stack
//	__asm {
//		mov		[parmBytes],eax
//        neg     eax                     ; compute new stack pointer in eax
//        add     eax,esp
//        add     eax,4
//        xchg    eax,esp
//        mov     eax,dword ptr [eax]		; copy the return address
//        push    eax
//
//        ; clear to zero
//        push	edi
//        push	ecx
//        mov		edi,esp
//        add		edi,12
//        mov		ecx,[parmBytes]
//		shr		ecx,2
//        xor		eax,eax
//		cld
//        rep	stosd
//        pop		ecx
//        pop		edi
//
//        ret
//	}
    }

    /*
     ==================
     Sys_SetFatalError
     ==================
     */
    fun Sys_SetFatalError(error: String) {}
    fun Sys_SetFatalError(error: CharArray) {
        Sys_SetFatalError(TempDump.ctos(error))
    }

    /*
     ==================
     Sys_DoPreferences
     ==================
     */
    fun Sys_DoPreferences() {}
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
        return FileChannel.open(tmp.toPath(), TempDump.fopenOptions("wb+"))
    }


    fun main(lpCmdLine: Array<String>) { //cmd arguments need to be escaped and surrounded by quotes to preserve spacing.
        // TODO: check if any of the disabled commands below can be salvaged for java.

//	const HCURSOR hcurSave = ::SetCursor( LoadCursor( 0, IDC_WAIT ) );
//
//	Sys_SetPhysicalWorkMemory( 192 << 20, 1024 << 20 );
//
//	Sys_GetCurrentMemoryStatus( exeLaunchMemoryStats );
//
//#if 0
//    DWORD handler = (DWORD)_except_handler;
//    __asm
//    {                           // Build EXCEPTION_REGISTRATION record:
//        push    handler         // Address of handler function
//        push    FS:[0]          // Address of previous handler
//        mov     FS:[0],ESP      // Install new EXECEPTION_REGISTRATION
//    }
//#endif
//
//	win32.hInstance = hInstance;
        idStr.Copynz(sys_cmdline, *lpCmdLine)

        // done before Com/Sys_Init since we need this for error output
        win_syscon.Sys_CreateConsole()

//        // no abort/retry/fail errors
//        SetErrorMode(SEM_FAILCRITICALERRORS);
//
//        for (i in 0 until MAX_CRITICAL_SECTIONS) {
////            InitializeCriticalSection( &win32.criticalSections[i] );
//            win_local.win32.criticalSections[i] =
//                ReentrantLock() //TODO: see if we can use synchronized blocks instead?
//        }
        // get the initial time base
        win_shared.Sys_Milliseconds()
        //
//        if (DEBUG) {
//            // disable the painfully slow MS heap check every 1024 allocs
//            _CrtSetDbgFlag(0);
//        }
//
//	Sys_FPU_EnableExceptions( TEST_FPU_EXCEPTIONS );
//        Sys_FPU_SetPrecision(etoi(FPU_PRECISION_DOUBLE_EXTENDED));
        Common.common.Init(0, null, sys_cmdline.toString())
        //
//        if (TEST_FPU_EXCEPTIONS != 0) {
//            common.Printf(Sys_FPU_GetState());
//        }
//
//        if (ID_DEDICATED) {
//            if (win32.win_notaskkeys.GetInteger() != 0) {
//                DisableTaskKeys(true, false, /*( win32.win_notaskkeys.GetInteger() == 2 )*/ false);
//            }
//        }
//
        Sys_StartAsyncThread()

        // hide or show the early console as necessary
        if (Win32Vars_t.win_viewlog.GetInteger() != 0 || Common.com_skipRenderer.GetBool()
            || idAsyncNetwork.serverDedicated.GetInteger() != 0
        ) {
            win_syscon.Sys_ShowConsole(1, true)
        } else {
            win_syscon.Sys_ShowConsole(0, false)
        }
        //
//        if (SET_THREAD_AFFINITY) {
//            // give the main thread an affinity for the first cpu
//            SetThreadAffinityMask(GetCurrentThread(), 1);
//        }
//
//	::SetCursor( hcurSave );
//
        // Launch the script debugger
//        if ( strstr( lpCmdLine, "+debugger" ) ) {
        // FIX: Was == 0 (only matches at start of string). C++ strstr() checks anywhere.
        if (sys_cmdline.indexOf("+debugger") >= 0) {
            // DebuggerClientInit( lpCmdLine );
            win_syscon.Sys_ShowConsole(1, true)
            return  //0;
        }
        //
//	::SetFocus( win32.hWnd );
//
        // main game loop
        var timer = Instant.now().toEpochMilli()
        while (true) {
            if (Instant.now().toEpochMilli() - timer >= USERCMD_MSEC) {
                timer = Instant.now().toEpochMilli()
            }

            Win_Frame()

            if (ID_ALLOW_TOOLS) {
                if (Common.com_editors != 0) {
                    if (Common.com_editors and Common.EDITOR_GUI != 0) {
                        // GUI editor
                        edit_public.GUIEditorRun()
                    } else if (Common.com_editors and Common.EDITOR_RADIANT != 0) {
                        // Level Editor
                        edit_public.RadiantRun()
                    } else if (Common.com_editors and Common.EDITOR_MATERIAL != 0) {
                        //BSM Nerve: Add support for the material editor
                        edit_public.MaterialEditorRun()
                    } else {
                        if (Common.com_editors and Common.EDITOR_LIGHT != 0) {
                            // in-game Light Editor
                            edit_public.LightEditorRun()
                        }
                        if (Common.com_editors and Common.EDITOR_SOUND != 0) {
                            // in-game Sound Editor
                            edit_public.SoundEditorRun()
                        }
                        if (Common.com_editors and Common.EDITOR_DECL != 0) {
                            // in-game Declaration Browser
                            edit_public.DeclBrowserRun()
                        }
                        if (Common.com_editors and Common.EDITOR_AF != 0) {
                            // in-game Articulated Figure Editor
                            edit_public.AFEditorRun()
                        }
                        if (Common.com_editors and Common.EDITOR_PARTICLE != 0) {
                            // in-game Particle Editor
                            edit_public.ParticleEditorRun()
                        }
                        if (Common.com_editors and Common.EDITOR_SCRIPT != 0) {
                            // in-game Script Editor
                            edit_public.ScriptEditorRun()
                        }
                        if (Common.com_editors and Common.EDITOR_PDA != 0) {
                            // in-game PDA Editor
                            edit_public.PDAEditorRun()
                        }
                    }
                }
            }
            // run the game
            Common.common.Frame()
        }

        // never gets here
//	return 0;
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

    /*
     ==================
     Sys_AsyncThread
     ==================
     */
//    internal class Sys_AsyncThread : xthread_t() {
//        var startTime = 0
//        var wakeNumber = 0
//        override fun run() {
//            println("Blaaaaaaaaaaaaaaaaaa!")
//            //            startTime = Sys_Milliseconds();
////            wakeNumber = 0;
////
////            while (true) {
////#ifdef WIN32
////		// this will trigger 60 times a second
////		int r = WaitForSingleObject( hTimer, 100 );
////		if ( r != WAIT_OBJECT_0 ) {
////			OutputDebugString( "idPacketServer::PacketServerInterrupt: bad wait return" );
////		}
////#endif
////
////#if 0
////		wakeNumber++;
////		int		msec = Sys_Milliseconds();
////		int		deltaTime = msec - startTime;
////		startTime = msec;
////
////		char	str[1024];
////		sprintf( str, "%i ", deltaTime );
////		OutputDebugString( str );
////#endif
////
////
//            Common.common.Async()
//            //            }
//        }
//    }
}
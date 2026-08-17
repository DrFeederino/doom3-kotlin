/*
===========================================================================

Doom 3 GPL Source Code
Copyright (C) 1999-2011 id Software LLC, a ZeniMax Media company.
Translated to Kotlin by Dr. Feederino with support of Claude Code

This file is part of the Doom 3 GPL Source Code ("Doom 3 Source Code").
Original source: neo/framework/EventLoop.h, neo/framework/EventLoop.cpp

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

In addition, the Doom 3 Source Code is also subject to certain additional terms.
You should have received a copy of these additional terms immediately following
the terms and conditions of the GNU General Public License which accompanied
the Doom 3 Source Code.  If not, please request a copy in writing from
id Software at the address below.

If you have questions concerning this license or the applicable additional terms,
you may contact in writing id Software LLC, c/o ZeniMax Media Inc., Suite 120,
Rockville, Maryland 20850 USA.

===========================================================================
*/

package neo.framework

import neo.framework.CVarSystem.idCVar
import neo.framework.CmdSystem.cmdExecution_t
import neo.framework.CmdSystem.idCmdSystem.ArgCompletion_Integer
import neo.framework.File_h.idFile
import neo.framework.KeyInput.idKeyInput
import neo.idlib.containers.bbtoa
import neo.idlib.idException
import neo.sys.sysEventType_t
import neo.sys.sysEvent_s
import neo.sys.win_main
import neo.sys.win_shared
import java.nio.ByteBuffer

/*
===============================================================================

    The event loop receives events from the system and dispatches them to
    the various parts of the engine. The event loop also handles journaling.
    The file system copies .cfg files to the journaled file.

===============================================================================
*/

class EventLoop {

    class idEventLoop {

        // Journal file.
        var com_journalFile: idFile? = null
        var com_journalDataFile: idFile? = null

        // all events will have this subtracted from their time
        private var initialTimeOffset: Int = 0

        private var com_pushedEventsHead: Int = 0
        private var com_pushedEventsTail: Int = 0
        private val com_pushedEvents: Array<sysEvent_s> = Array(MAX_PUSHED_EVENTS) { sysEvent_s() }

        /*
        =================
        idEventLoop::Init
        =================
        */
        @Throws(idException::class)
        fun Init() {
            initialTimeOffset = win_shared.Sys_Milliseconds()

            Common.common.StartupVariable("journal", false)

            if (com_journal.GetInteger() == 1) {
                Common.common.Printf("Journaling events\n")
                com_journalFile = FileSystem_h.fileSystem.OpenFileWrite("journal.dat")
                com_journalDataFile = FileSystem_h.fileSystem.OpenFileWrite("journaldata.dat")
            } else if (com_journal.GetInteger() == 2) {
                Common.common.Printf("Replaying journaled events\n")
                com_journalFile = FileSystem_h.fileSystem.OpenFileRead("journal.dat")
                com_journalDataFile = FileSystem_h.fileSystem.OpenFileRead("journaldata.dat")
            }

            if (com_journalFile == null || com_journalDataFile == null) {
                com_journal.SetInteger(0) // FIX: C++ sets both to NULL here; Kotlin had these commented out
                com_journalFile = null
                com_journalDataFile = null
                Common.common.Printf("Couldn't open journal files\n")
            }
        }

        /*
        =================
        idEventLoop::Shutdown

        Closes the journal file if needed.
        =================
        */
        fun Shutdown() {
            if (com_journalFile != null) {
                FileSystem_h.fileSystem.CloseFile(com_journalFile!!)
                com_journalFile = null
            }
            if (com_journalDataFile != null) {
                FileSystem_h.fileSystem.CloseFile(com_journalDataFile!!)
                com_journalDataFile = null
            }
        }

        /*
        =================
        idEventLoop::GetEvent

        It is possible to get an event at the beginning of a frame that
        has a time stamp lower than the last event from the previous frame.
        =================
        */
        @Throws(idException::class)
        fun GetEvent(): sysEvent_s {
            if (com_pushedEventsHead > com_pushedEventsTail) {
                com_pushedEventsTail++
                return com_pushedEvents[(com_pushedEventsTail - 1) and (MAX_PUSHED_EVENTS - 1)]
            }
            return GetRealEvent()
        }

        /*
        ===============
        idEventLoop::RunEventLoop

        Dispatches all pending events and returns the current time.
        ===============
        */
        @Throws(idException::class)
        fun RunEventLoop(commandExecution: Boolean = true): Int {
            var ev: sysEvent_s

            while (true) {
                if (commandExecution) { // execute any bound commands before processing another event
                    CmdSystem.cmdSystem.ExecuteCommandBuffer()
                }

                ev = GetEvent()

                // if no more events are available
                if (ev.evType == sysEventType_t.SE_NONE) {
                    return 0
                }
                ProcessEvent(ev)
            }

            @Suppress("UNREACHABLE_CODE") return 0 // never reached
        }

        /*
        ================
        idEventLoop::Milliseconds

        Can be used for profiling, but will be journaled properly,
        as opposed to Sys_Milliseconds(), which always reads a real timer.
        ================
        */
        fun Milliseconds(): Int { // FIXME! - matches C++ #if 1 branch
            return win_shared.Sys_Milliseconds() - initialTimeOffset
        }

        /*
        ================
        idEventLoop::JournalLevel

        Returns the journal level, 1 = record, 2 = play back.
        ================
        */
        fun JournalLevel(): Int {
            return com_journal.GetInteger()
        }

        /*
        =================
        idEventLoop::GetRealEvent
        =================
        */
        @Throws(idException::class)
        private fun GetRealEvent(): sysEvent_s {
            var r: Int
            val ev: sysEvent_s

            // either get an event from the system or the journal file
            if (com_journal.GetInteger() == 2) {
                ev = sysEvent_s()
                val before = com_journalFile!!.Tell()
                ev.readFrom(com_journalFile!!)
                if (com_journalFile!!.Tell() - before != sysEvent_s.BYTES) {
                    Common.common.FatalError("Error reading from journal file")
                }
                if (ev.evPtrLength != 0) {
                    ev.evPtr = ByteBuffer.allocate(ev.evPtrLength)
                    r = com_journalFile!!.Read(ev.evPtr!!)
                    if (r != ev.evPtrLength) {
                        Common.common.FatalError("Error reading from journal file")
                    }
                }
            } else {
                ev = win_main.Sys_GetEvent()

                // write the journal value out if needed
                if (com_journal.GetInteger() == 1) {
                    val before = com_journalFile!!.Tell()
                    ev.writeTo(com_journalFile!!)
                    if (com_journalFile!!.Tell() - before != sysEvent_s.BYTES) {
                        Common.common.FatalError("Error writing to journal file")
                    }
                    if (ev.evPtrLength != 0) {
                        r = com_journalFile!!.Write(ev.evPtr!!, ev.evPtrLength)
                        if (r != ev.evPtrLength) {
                            Common.common.FatalError("Error writing to journal file")
                        }
                    }
                }
            }

            return ev
        }

        /*
        =================
        idEventLoop::ProcessEvent
        =================
        */
        @Throws(idException::class)
        private fun ProcessEvent(ev: sysEvent_s) { // track key up / down states
            if (ev.evType == sysEventType_t.SE_KEY) {
                idKeyInput.PreliminaryKeyEvent(ev.evValue, ev.evValue2 != 0)
            }

            if (ev.evType == sysEventType_t.SE_CONSOLE) { // from a text console outside the game window
                CmdSystem.cmdSystem.BufferCommandText(cmdExecution_t.CMD_EXEC_APPEND, bbtoa(ev.evPtr!!))
                CmdSystem.cmdSystem.BufferCommandText(cmdExecution_t.CMD_EXEC_APPEND, "\n")
            } else {
                Session.session.ProcessEvent(ev)
            }

            // free any block data
            if (ev.evPtr != null) {
                ev.evPtr = null
            }
        }

        /*
        =================
        idEventLoop::PushEvent
        =================
        */
        @Throws(idException::class)
        private fun PushEvent(event: sysEvent_s) {
            val ev: sysEvent_s = com_pushedEvents[com_pushedEventsHead and (MAX_PUSHED_EVENTS - 1)]

            if (com_pushedEventsHead - com_pushedEventsTail >= MAX_PUSHED_EVENTS) {

                // don't print the warning constantly, or it can give time for more...
                if (!printedWarning) {
                    printedWarning = true
                    Common.common.Printf("WARNING: Com_PushEvent overflow\n")
                }

                if (ev.evPtr != null) {
                    ev.evPtr = null
                }
                com_pushedEventsTail++
            } else {
                printedWarning = false
            }

            com_pushedEvents[com_pushedEventsHead and (MAX_PUSHED_EVENTS - 1)] = event
            com_pushedEventsHead++
        }

        companion object {
            private val com_journal: idCVar = idCVar(
                "com_journal",
                "0",
                CVarSystem.CVAR_INIT or CVarSystem.CVAR_SYSTEM,
                "1 = record journal, 2 = play back journal",
                0.0f,
                2.0f,
                ArgCompletion_Integer(0, 2)
            )
            private var printedWarning: Boolean = false
        }
    }

    companion object {
        const val MAX_PUSHED_EVENTS: Int = 64

        val eventLoop: idEventLoop = idEventLoop()
    }
}

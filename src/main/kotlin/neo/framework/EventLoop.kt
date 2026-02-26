package neo.framework

import neo.TempDump
import neo.framework.CVarSystem.idCVar
import neo.framework.CmdSystem.cmdExecution_t
import neo.framework.CmdSystem.idCmdSystem.ArgCompletion_Integer
import neo.framework.File_h.idFile
import neo.framework.KeyInput.idKeyInput
import neo.idlib.idException
import neo.sys.sys_public.sysEventType_t
import neo.sys.sys_public.sysEvent_s
import neo.sys.win_main
import neo.sys.win_shared
import java.nio.ByteBuffer

class EventLoop {
    val com_journalFile: idCVar = idCVar(
        "com_journal",
        "0",
        CVarSystem.CVAR_INIT or CVarSystem.CVAR_SYSTEM,
        "1 = record journal, 2 = play back journal",
        0.0f,
        2.0f,
        ArgCompletion_Integer(0, 2)
    )

    class idEventLoop     //					~idEventLoop( void );
    {
        private val com_pushedEvents: Array<sysEvent_s> = Array(MAX_PUSHED_EVENTS) { sysEvent_s() }
        var com_journalDataFile: idFile? = null

        // Journal file.
        var com_journalFile: idFile? = null

        //
        private var com_pushedEventsHead = 0
        private var com_pushedEventsTail = 0

        //
        //
        //
        //
        // all events will have this subtracted from their time
        private var initialTimeOffset = 0

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
            if (null == com_journalFile || null == com_journalDataFile) {
                com_journal.SetInteger(0)
                //		com_journalFile = 0;
//		com_journalDataFile = 0;
                Common.common.Printf("Couldn't open journal files\n")
            }
        }

        // Closes the journal file if needed.
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

        // It is possible to get an event at the beginning of a frame that
        // has a time stamp lower than the last event from the previous frame.
        @Throws(idException::class)
        fun GetEvent(): sysEvent_s {
            if (com_pushedEventsHead > com_pushedEventsTail) {
                com_pushedEventsTail++
                return com_pushedEvents[com_pushedEventsTail - 1 and MAX_PUSHED_EVENTS - 1]
            }
            return GetRealEvent()
        }

        // Dispatches all pending events and returns the current time.

        @Throws(idException::class)
        fun RunEventLoop(commandExecution: Boolean = true /*= true*/): Int {
            var ev: sysEvent_s
            while (true) {
                if (commandExecution) {
                    // execute any bound commands before processing another event
                    CmdSystem.cmdSystem.ExecuteCommandBuffer()
                }
                ev = GetEvent()

                // if no more events are available
                if (ev.evType == sysEventType_t.SE_NONE) {
                    return 0
                }
                ProcessEvent(ev)
            }
            return 0

//	return 0;	// never reached
        }

        /*
         ================
         idEventLoop::Milliseconds

         Can be used for profiling, but will be journaled accurately
         ================
         */
        // Gets the current time in a way that will be journaled properly,
        // as opposed to Sys_Milliseconds(), which always reads a real timer.
        fun Milliseconds(): Int {
//            if (true) {// FIXME!
            return win_shared.Sys_Milliseconds() - initialTimeOffset
            //            } else {
//                sysEvent_s ev;
//
//                // get events and push them until we get a null event with the current time
//                do {
//
//                    ev = Com_GetRealEvent();
//                    if (ev.evType != SE_NONE) {
//                        Com_PushEvent( & ev);
//                    }
//                } while (ev.evType != SE_NONE);
//
//                return ev.evTime;
//        };
        }

        // Returns the journal level, 1 = record, 2 = play back.
        fun JournalLevel(): Int {
            return com_journal.GetInteger()
        }

        @Throws(idException::class)
        private fun GetRealEvent(): sysEvent_s {
            var r: Int
            val ev: sysEvent_s
            val event: ByteBuffer

            // either get an event from the system or the journal file
            if (com_journal.GetInteger() == 2) {
                event = ByteBuffer.allocate(sysEvent_s.BYTES)
                r = com_journalFile!!.Read(event)
                ev = sysEvent_s(event)
                if (r != sysEvent_s.BYTES) {
                    Common.common.FatalError("Error reading from journal file")
                }
                if (ev.evPtrLength != 0) {
                    ev.evPtr = ByteBuffer.allocate(ev.evPtrLength) //Mem_ClearedAlloc(ev.evPtrLength);
                    r = com_journalFile!!.Read(ev.evPtr!!) //, ev.evPtrLength);
                    if (r != ev.evPtrLength) {
                        Common.common.FatalError("Error reading from journal file")
                    }
                }
            } else {
                ev = win_main.Sys_GetEvent()

                // write the journal value out if needed
                if (com_journal.GetInteger() == 1) {
                    r = com_journalFile!!.Write(ev.Write())
                    if (r != sysEvent_s.BYTES) {
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

        @Throws(idException::class)
        private fun ProcessEvent(ev: sysEvent_s) {
            // track key up / down states
            if (ev.evType == sysEventType_t.SE_KEY) {
                idKeyInput.PreliminaryKeyEvent(ev.evValue, ev.evValue2 != 0)
            }
            if (ev.evType == sysEventType_t.SE_CONSOLE) {
                // from a text console outside the game window
                CmdSystem.cmdSystem.BufferCommandText(cmdExecution_t.CMD_EXEC_APPEND, TempDump.bbtoa(ev.evPtr!!))
                CmdSystem.cmdSystem.BufferCommandText(cmdExecution_t.CMD_EXEC_APPEND, "\n")
            } else {
                Session.session.ProcessEvent(ev)
            }

            // free any block data
            if (ev.evPtr != null) { //TODO:ptr?
//                Mem_Free(ev.evPtr);
                ev.evPtr = null
            }
        }

        @Throws(idException::class)
        private fun PushEvent(event: sysEvent_s) {
            val ev: sysEvent_s
            ev = com_pushedEvents[com_pushedEventsHead and MAX_PUSHED_EVENTS - 1]
            if (com_pushedEventsHead - com_pushedEventsTail >= MAX_PUSHED_EVENTS) {

                // don't print the warning constantly, or it can give time for more...
                if (!printedWarning) {
                    printedWarning = true
                    Common.common.Printf("WARNING: Com_PushEvent overflow\n")
                }
                if (ev.evPtr != null) {
//                    Mem_Free(ev.evPtr);
                    ev.evPtr = null
                }
                com_pushedEventsTail++
            } else {
                printedWarning = false
            }
            com_pushedEvents[com_pushedEventsHead and MAX_PUSHED_EVENTS - 1] = event
            com_pushedEventsHead++
        }

        companion object {
            //
            private val com_journal: idCVar = idCVar(
                "com_journal",
                "0",
                CVarSystem.CVAR_INIT or CVarSystem.CVAR_SYSTEM,
                "1 = record journal, 2 = play back journal",
                0.0f,
                2.0f,
                ArgCompletion_Integer(0, 2)
            )
            var printedWarning = false
        }
    }

    companion object {
        const val MAX_PUSHED_EVENTS = 64

        /*
                 ===============================================================================

                 The event loop receives events from the system and dispatches them to
                 the various parts of the engine. The event loop also handles journaling.
                 The file system copies .cfg files to the journaled file.

                 ===============================================================================
                 */

        val eventLoop: idEventLoop = idEventLoop()
    }
}
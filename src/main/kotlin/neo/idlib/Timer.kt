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

package neo.idlib

import neo.idlib.Text.Str.idStr
import neo.idlib.containers.List.idList
import neo.idlib.containers.idStrList

class Timer {
    internal enum class State {
        TS_STARTED,
        TS_STOPPED
    }

    /*
     ===============================================================================

     Clock tick counter. Should only be used for profiling.

     ===============================================================================
     */
    class idTimer {
        private var ms: Long = 0L
        private var start: Long = 0L
        private var state: State = State.TS_STOPPED

        constructor() {
            state = State.TS_STOPPED
            ms = 0L
        }

        constructor(_ms: Long) {
            state = State.TS_STOPPED
            ms = _ms
        }

        operator fun plus(t: idTimer): idTimer {
            assert(state == State.TS_STOPPED && t.state == State.TS_STOPPED)
            return idTimer(ms + t.ms)
        }

        operator fun minus(t: idTimer): idTimer {
            assert(state == State.TS_STOPPED && t.state == State.TS_STOPPED)
            return idTimer(ms - t.ms)
        }

        fun plusAssign(t: idTimer): idTimer {
            assert(state == State.TS_STOPPED && t.state == State.TS_STOPPED)
            ms += t.ms
            return this
        }

        fun minusAssign(t: idTimer): idTimer {
            assert(state == State.TS_STOPPED && t.state == State.TS_STOPPED)
            ms -= t.ms
            return this
        }

        fun Start() {
            assert(state == State.TS_STOPPED)
            state = State.TS_STARTED
            start = idLib.sys.GetMilliseconds()
        }

        fun Stop() {
            assert(state == State.TS_STARTED)
            ms += idLib.sys.GetMilliseconds() - start
            state = State.TS_STOPPED
        }

        fun Clear() {
            ms = 0L
        }

        fun Milliseconds(): Long {
            assert(state == State.TS_STOPPED)
            return ms
        }
    }

    /*
     ===============================================================================

     Report of multiple named timers.

     ===============================================================================
     */
    internal class idTimerReport  //
    //
    {
        private val names: idStrList = idStrList()
        private var reportName: idStr = idStr()
        private val timers: idList<idTimer> = idList()

        //public					~idTimerReport( void );
        //
        fun SetReportName(name: String?) {
            reportName = idStr(name ?: "Timer Report")
        }

        fun AddReport(name: String?): Int {
            if (name != null && name.isNotEmpty()) {
                names.add(idStr(name))
                return timers.Append(idTimer())
            }
            return -1
        }

        fun Clear() {
            timers.DeleteContents(true)
            names.clear()
            reportName.Clear()
        }

        fun Reset() {
            assert(timers.Num() == names.size())
            for (i in 0 until timers.Num()) {
                timers[i].Clear()
            }
        }

        @Throws(idException::class)
        fun PrintReport() {
            assert(timers.Num() == names.size())
            idLib.common.Printf("Timing Report for %s\n", reportName)
            idLib.common.Printf("-------------------------------\n")
            var total: Long = 0
            for (i in 0 until names.size()) {
                idLib.common.Printf("%s consumed %5.2f seconds\n", names[i], 0.001f * timers[i].Milliseconds())
                total += timers[i].Milliseconds()
            }
            idLib.common.Printf(
                "Total time for report %s was %5.2f\n\n", reportName, 0.001f * total
            )
        }

        fun AddTime(name: String, time: idTimer) {
            assert(timers.Num() == names.size())
            var i: Int
            i = 0
            while (i < names.size()) {
                if (names[i].Icmp(name) == 0) {
                    timers[i].plusAssign(time)
                    break
                }
                i++
            }
            if (i == names.size()) {
                val index = AddReport(name)
                if (index >= 0) {
                    timers[index].Clear()
                    timers[index].plusAssign(time)
                }
            }
        }
    }
}
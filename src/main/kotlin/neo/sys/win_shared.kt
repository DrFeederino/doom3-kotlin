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

import com.sun.management.OperatingSystemMXBean
import java.io.File
import java.lang.management.ManagementFactory
import java.time.Instant

object win_shared {
    val sys_timeBase = Instant.now().toEpochMilli()
    private val preciseTimeBase = System.nanoTime()

    /*
     ================
     Sys_MillisecondsPrecise
     High-precision millisecond timer using System.nanoTime().
     Matches dhewm3's Sys_MillisecondsPrecise().
     ================
     */
    fun Sys_MillisecondsPrecise(): Double {
        return (System.nanoTime() - preciseTimeBase) / 1_000_000.0
    }

    /*
     ================
     Sys_SleepUntilPrecise
     Sleep until Sys_MillisecondsPrecise() returns >= targetTimeMS.
     Hybrid sleep+spin for sub-ms precision.
     Matches dhewm3's Sys_SleepUntilPrecise().
     ================
     */
    fun Sys_SleepUntilPrecise(targetTimeMS: Double) {
        var msec = targetTimeMS - Sys_MillisecondsPrecise()
        if (msec > 1.5) {
            Thread.sleep((msec - 1.0).toLong().coerceAtLeast(0))
            msec = targetTimeMS - Sys_MillisecondsPrecise()
        }
        while (msec > 0.0) {
            Thread.yield()
            msec = targetTimeMS - Sys_MillisecondsPrecise()
        }
    }

    /*
     ================
     Sys_Milliseconds
     ================
     */

    fun Sys_Milliseconds(): Int {
        return (Instant.now().toEpochMilli() - sys_timeBase).toInt()
    }

    /*
     ================
     Sys_GetSystemRam

     returns amount of physical memory in MB
     ================
     */
    fun Sys_GetSystemRam(): Int {
        val ram = (ManagementFactory.getOperatingSystemMXBean() as OperatingSystemMXBean).totalPhysicalMemorySize
        return (ram / 1000000).toInt()
    }

    /*
     ================
     Sys_GetDriveFreeSpace
     returns in megabytes
     ================
     */
    fun Sys_GetDriveFreeSpace(path: String): Long {
        return File(path).freeSpace / (1024L * 1024L)
    }

    /*
     ================
     Sys_GetVideoRam
     returns in megabytes
     ================
     */
    fun Sys_GetVideoRam(): Int {
        return 0
    }


    /*
     ================
     Sys_LockMemory
     ================
     */
    fun Sys_LockMemory(ptr: Any, bytes: Int): Boolean {
        return true
    }

    fun Sys_UnlockMemory(ptr: Any, bytes: Int): Boolean {
        return true
    }


    fun Sys_GetCurrentUser(): String {
        var s_userName: String = ""
        if (System.getProperty("user.name").also { s_userName = it }.isEmpty()) {
            s_userName = "player"
        }
        return s_userName
    }

}
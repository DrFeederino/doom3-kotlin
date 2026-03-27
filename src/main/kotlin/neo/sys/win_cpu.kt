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
along with Doom 3 Source Code.	If not, see <http://www.gnu.org/licenses/>.

In addition, the Doom 3 Source Code is also subject to certain additional terms. You should have received a copy of these additional terms immediately following the terms and conditions of the GNU General Public License which accompanied the Doom 3 Source Code.  If not, please request a copy in writing from id Software at the address below.

If you have questions concerning this license or the applicable additional terms, you may contact in writing id Software LLC, c/o ZeniMax Media Inc., Suite 120, Rockville, Maryland 20850 USA.

===========================================================================
*/
package neo.sys

import neo.framework.Common.Companion.common
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader

object win_cpu {

    /*
     ==============================================================

     Clock ticks

     ==============================================================
     */

    private var ticks = 0.0f

    /*
     ================
     Sys_GetClockTicks
     ================
     */
    fun Sys_GetClockTicks(): Long {
        return System.nanoTime()
    }

    /*
     ================
     Sys_ClockTicksPerSecond
     ================
     */
    fun Sys_ClockTicksPerSecond(): Float {
        if (ticks == 0.0f) {
            ticks = 1_000_000_000.0f
        }
        return ticks
    }

    /*
     ================
     Sys_GetCPUId
     ================
     */
    fun /*cpuid_t*/ Sys_GetCPUId(): Int {
        // Any system running a modern JVM will have these capabilities.
        var flags = CPUID_GENERIC

        // Detect AMD vs Intel from processor identifier
        val procId = System.getenv("PROCESSOR_IDENTIFIER") ?: ""
        flags = if (procId.contains("AMD", ignoreCase = true)) {
            CPUID_AMD
        } else {
            CPUID_INTEL
        }

        // Assume modern x86 capabilities
        flags = flags or CPUID_MMX
        flags = flags or CPUID_SSE
        flags = flags or CPUID_FTZ
        flags = flags or CPUID_SSE2
        flags = flags or CPUID_SSE3
        flags = flags or CPUID_HTT
        flags = flags or CPUID_CMOV
        flags = flags or CPUID_DAZ

        // check for 3DNow! (AMD only)
        if (procId.contains("AMD", ignoreCase = true)) {
            flags = flags or CPUID_3DNOW
        }

        return flags
    }

    /*
     ==============================================================

     FPU

     ==============================================================
     */

    /*
     ===============
     Sys_FPU_SetDAZ
     ===============
     */
    fun Sys_FPU_SetDAZ(enable: Boolean) {
        // No-op on JVM — JVM manages its own FP denormal handling
        if (enable) {
            common.Printf("Denormals-Are-Zero mode requested (JVM manages FP internally)\n")
        }
    }

    /*
     ===============
     Sys_FPU_SetFTZ
     ===============
     */
    fun Sys_FPU_SetFTZ(enable: Boolean) {
        // No-op on JVM — JVM manages its own FP flush-to-zero behavior
        if (enable) {
            common.Printf("Flush-To-Zero mode requested (JVM manages FP internally)\n")
        }
    }

    /*
     ===============
     Sys_FPU_StackIsEmpty
     ===============
     */
    fun Sys_FPU_StackIsEmpty(): Boolean {
        return true
    }

    /*
     ===============
     Sys_FPU_ClearStack
     ===============
     */
    fun Sys_FPU_ClearStack() {
        // No-op on JVM
    }

    /*
     ===============
     Sys_FPU_GetState
     ===============
     */
    fun Sys_FPU_GetState(): String {
        return "FPU State: JVM-managed (no direct FPU access)\n"
    }

    /*
     ===============
     Sys_FPU_EnableExceptions
     ===============
     */
    fun Sys_FPU_EnableExceptions(exceptions: Int) {
        // No-op on JVM
    }

    /*
     ===============
     Sys_FPU_SetPrecision
     ===============
     */
    fun Sys_FPU_SetPrecision(precision: Int) {
        // No-op on JVM — always uses IEEE 754 double precision
    }

    /*
     ===============
     Sys_FPU_SetRounding
     ===============
     */
    fun Sys_FPU_SetRounding(rounding: Int) {
        // No-op on JVM — uses IEEE 754 default rounding
    }

    /*
     ===============
     Sys_FPU_PrintStateFlags
     ===============
     */
    fun Sys_FPU_PrintStateFlags(
        ptr: String, ctrl: Int, stat: Int, tags: Int, inof: Int, inse: Int, opof: Int, opse: Int
    ): Int {
        return 0
    }

    /*
     ================
     Helper: run a system command and return output
     ================
     */
    @Throws(IOException::class)
    fun cmd(query: String): String {
        val result: String
        val proc = Runtime.getRuntime().exec(query)
        BufferedReader(InputStreamReader(proc.inputStream)).use { reader -> result = reader.readLine() ?: "" }
        proc.destroy()
        return result
    }
}

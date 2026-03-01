/*
 * Copyright (C) 1999-2011 id Software LLC, a ZeniMax Media company.
 * Translated to Kotlin by Dr. Feederino with support of Claude Code
 *
 * This file is part of the Doom 3 Kotlin project.
 * Original source: neo/sys/cpu.cpp
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

import neo.framework.Common.Companion.common
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader

/*
 * NOTE: Differs from C++ — The original C++ uses inline assembly (x86 cpuid, FPU control
 * word manipulation, MXCSR register access) and SDL_cpuinfo for CPU feature detection.
 * None of these are available on the JVM. The JVM manages its own floating-point behavior,
 * so FPU control functions are implemented as no-ops with logging. CPU feature detection
 * assumes modern x86 with SSE/SSE2/SSE3 support, which is reasonable for any system
 * running a modern JVM.
 */
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
        // NOTE: Differs from C++ — C++ uses rdtsc instruction for CPU clock ticks.
        // JVM uses System.nanoTime() which is a high-resolution monotonic clock.
        return System.nanoTime()
    }

    /*
     ================
     Sys_ClockTicksPerSecond
     ================
     */
    fun Sys_ClockTicksPerSecond(): Float {
        // NOTE: Differs from C++ — C++ reads CPU frequency from registry or QueryPerformanceFrequency.
        // Since Sys_GetClockTicks returns System.nanoTime(), ticks per second is 1 billion.
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
        // NOTE: Differs from C++ — dhewm3 uses SDL_HasMMX/SDL_HasSSE/etc.
        // On JVM, we assume modern x86 with full SSE/SSE2/SSE3 support.
        // Any system running a modern JVM will have these capabilities.
        var flags = sys_public.CPUID_GENERIC

        // Detect AMD vs Intel from processor identifier
        val procId = System.getenv("PROCESSOR_IDENTIFIER") ?: ""
        flags = if (procId.contains("AMD", ignoreCase = true)) {
            sys_public.CPUID_AMD
        } else {
            sys_public.CPUID_INTEL
        }

        // Assume modern x86 capabilities
        flags = flags or sys_public.CPUID_MMX
        flags = flags or sys_public.CPUID_SSE
        flags = flags or sys_public.CPUID_FTZ
        flags = flags or sys_public.CPUID_SSE2
        flags = flags or sys_public.CPUID_SSE3
        flags = flags or sys_public.CPUID_HTT
        flags = flags or sys_public.CPUID_CMOV
        flags = flags or sys_public.CPUID_DAZ

        // check for 3DNow! (AMD only)
        if (procId.contains("AMD", ignoreCase = true)) {
            flags = flags or sys_public.CPUID_3DNOW
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
     // NOTE: Differs from C++ — JVM manages FP denormal handling internally.
     // The JVM spec allows but does not guarantee DAZ behavior.
     // This is a no-op on the JVM.
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
     // NOTE: Differs from C++ — JVM manages FP flush-to-zero behavior internally.
     // This is a no-op on the JVM.
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
     // NOTE: Differs from C++ — C++ inspects x87 FPU tag word via fnstenv.
     // JVM does not expose FPU stack state. Always returns true.
     ===============
     */
    fun Sys_FPU_StackIsEmpty(): Boolean {
        return true
    }

    /*
     ===============
     Sys_FPU_ClearStack
     // NOTE: Differs from C++ — C++ pops x87 FPU stack entries via fstp.
     // No-op on JVM.
     ===============
     */
    fun Sys_FPU_ClearStack() {
        // No-op on JVM
    }

    /*
     ===============
     Sys_FPU_GetState
     // NOTE: Differs from C++ — C++ reads x87 FPU state (control word, status word, stack).
     // JVM does not expose FPU internals. Returns a summary string.
     ===============
     */
    fun Sys_FPU_GetState(): String {
        return "FPU State: JVM-managed (no direct FPU access)\n"
    }

    /*
     ===============
     Sys_FPU_EnableExceptions
     // NOTE: Differs from C++ — C++ manipulates x87 FPU control word exception mask.
     // No-op on JVM — Java handles FP exceptions through its own exception model.
     ===============
     */
    fun Sys_FPU_EnableExceptions(exceptions: Int) {
        // No-op on JVM
    }

    /*
     ===============
     Sys_FPU_SetPrecision
     // NOTE: Differs from C++ — C++ uses _controlfp to set x87 precision to 64-bit.
     // JVM uses IEEE 754 double precision (64-bit) by default.
     ===============
     */
    fun Sys_FPU_SetPrecision(precision: Int) {
        // No-op on JVM — always uses IEEE 754 double precision
    }

    /*
     ===============
     Sys_FPU_SetRounding
     // NOTE: Differs from C++ — C++ manipulates x87 rounding control bits.
     // JVM uses round-to-nearest by default (IEEE 754 default).
     ===============
     */
    fun Sys_FPU_SetRounding(rounding: Int) {
        // No-op on JVM — uses IEEE 754 default rounding
    }

    /*
     ===============
     Sys_FPU_PrintStateFlags
     // NOTE: Differs from C++ — No x87 state to print on JVM.
     ===============
     */
    fun Sys_FPU_PrintStateFlags(
        ptr: String,
        ctrl: Int,
        stat: Int,
        tags: Int,
        inof: Int,
        inse: Int,
        opof: Int,
        opse: Int
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

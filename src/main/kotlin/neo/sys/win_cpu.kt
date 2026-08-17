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

object win_cpu {

    /*
     ================
     Sys_FPU_SetDAZ
     ================
     */
    fun Sys_FPU_SetDAZ(enable: Boolean) { // No-op on JVM — JVM manages its own FP denormal handling
        if (enable) {
            common.Printf("Denormals-Are-Zero mode requested (JVM manages FP internally)\n")
        }
    }

    /*
     ================
     Sys_FPU_SetFTZ
     ================
     */
    fun Sys_FPU_SetFTZ(enable: Boolean) { // No-op on JVM — JVM manages its own FP flush-to-zero behavior
        if (enable) {
            common.Printf("Flush-To-Zero mode requested (JVM manages FP internally)\n")
        }
    }

    /*
     ================
     Sys_GetProcessorId
     ================
     */
    fun Sys_GetProcessorId(): Int { // On JVM, assume modern x86 capabilities
        var flags = CPUID_GENERIC

        flags = flags or CPUID_MMX
        flags = flags or CPUID_SSE
        flags = flags or CPUID_SSE2
        flags = flags or CPUID_SSE3

        return flags
    }

    /*
     ================
     Sys_FPU_SetPrecision
     ================
     */
    fun Sys_FPU_SetPrecision() { // No-op on JVM — always uses IEEE 754 double precision
    }
}

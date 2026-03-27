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

import neo.framework.CVarSystem
import neo.framework.CVarSystem.idCVar

abstract class win_local {
    class Win32Vars_t {
        var activeApp = false
        var cdsFullscreen = false
        var cpuid = 0

        //        var criticalSections: Array<ReentrantLock> = Array(sys_public.MAX_CRITICAL_SECTIONS) { ReentrantLock() }
        var desktopBitsPixel = 0
        var desktopWidth = 0
        var desktopHeight = 0

        // current state of grab and hide
        var mouseGrabbed = false

        // when the game has the console down or is doing a long operation
        var mouseReleased = false

        // inhibit mouse grab when dragging the window
        var movingWindow = false
        var pixelformat = 0

        // when we get a windows message, we store the time off so keyboard processing
        // can know the exact time of an event (not really needed now that we use async direct input)
        var sysMsgTime = 0
        var wglErrors = 0
        var windowClassRegistered = false // SMP acceleration vars

        companion object {
            val in_mouse: idCVar =
                idCVar("in_mouse", "1", CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_BOOL, "enable mouse input")
            val sys_arch: idCVar = idCVar("sys_arch", "", CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_INIT, "")
            val sys_cpustring: idCVar =
                idCVar("sys_cpustring", "detect", CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_INIT, "")
            val win_allowAltTab: idCVar = idCVar(
                "win_allowAltTab", "0", CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_BOOL, "allow Alt-Tab when fullscreen"
            )
            val win_allowMultipleInstances: idCVar = idCVar(
                "win_allowMultipleInstances",
                "0",
                CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_BOOL,
                "allow multiple instances running concurrently"
            )
            val win_notaskkeys: idCVar = idCVar(
                "win_notaskkeys", "0", CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_INTEGER, "disable windows task keys"
            )
            val win_outputDebugString: idCVar =
                idCVar("win_outputDebugString", "1", CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_BOOL, "")
            val win_outputEditString: idCVar =
                idCVar("win_outputEditString", "1", CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_BOOL, "")
            val win_timerUpdate: idCVar = idCVar(
                "win_timerUpdate",
                "0",
                CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_BOOL,
                "allows the game to be updated while dragging the window"
            )
            val win_username: idCVar =
                idCVar("win_username", "", CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_INIT, "windows user name")
            val win_viewlog: idCVar = idCVar("win_viewlog", "0", CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_INTEGER, "")
            val win_xpos: idCVar = idCVar(
                "win_xpos",
                "3",
                CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_ARCHIVE or CVarSystem.CVAR_INTEGER,
                "horizontal position of window"
            ) // archived X coordinate of window position
            val win_ypos: idCVar = idCVar(
                "win_ypos",
                "22",
                CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_ARCHIVE or CVarSystem.CVAR_INTEGER,
                "vertical position of window"
            ) // archived Y coordinate of window position
        }
    }

    companion object {
        var win32: Win32Vars_t = Win32Vars_t()
    }
}
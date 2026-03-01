/*
 * Copyright (C) 1999-2011 id Software LLC, a ZeniMax Media company.
 * Translated to Kotlin by Dr. Feederino with support of Claude Code
 *
 * This file is part of the Doom 3 Kotlin project.
 * Original source: neo/sys/win32/win_local.h
 *
 * NOTE: Differs from C++ — The dhewm3 version of this header is much simpler
 * (only hWnd, hInstance, osversion, and 3 CVars). The Kotlin version preserves
 * more fields from the original id Software source. Win32-specific types (HWND,
 * HINSTANCE, HDC, HGLRC, DirectInput devices) are replaced with JVM equivalents
 * or omitted where GLFW/LWJGL handles the functionality.
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

import neo.framework.CVarSystem
import neo.framework.CVarSystem.idCVar
import java.awt.event.KeyListener
import java.awt.event.MouseListener

abstract class win_local {
    class Win32Vars_t {
        var activeApp // changed with WM_ACTIVATE messages
                = false

        //
        var cdsFullscreen = false

        //
        //	OSVERSIONINFOEX	osversion;
        //
        var   /*cpuid_t*/cpuid = 0

        //
//        var criticalSections: Array<ReentrantLock> = Array(sys_public.MAX_CRITICAL_SECTIONS) { ReentrantLock() }

        //
        //	HINSTANCE		hinstOpenGL;	// HINSTANCE for the OpenGL library
        //
        var desktopBitsPixel = 0
        var desktopWidth = 0
        var desktopHeight = 0

        @Deprecated("")
        var   /*LPDIRECTINPUTDEVICE8*/g_pKeyboard: KeyListener? = null

        //	HANDLE			backgroundDownloadSemaphore;
        //
        //	HINSTANCE		hInstDI;			// direct input
        //
        //	LPDIRECTINPUT8			g_pdi;
        @Deprecated("")
        var   /*LPDIRECTINPUTDEVICE8*/g_pMouse: MouseListener? = null
        var mouseGrabbed // current state of grab and hide
                = false
        var mouseReleased // when the game has the console down or is doing a long operation
                = false
        var movingWindow // inhibit mouse grab when dragging the window
                = false

        //
        //	WNDPROC			wndproc;
        //
        //	HDC				hDC;							// handle to device context
        //	HGLRC			hGLRC;						// handle to GL rendering context
        //	PIXELFORMATDESCRIPTOR pfd;
        var pixelformat = 0

        //
        //	HANDLE			renderCommandsEvent;
        //	HANDLE			renderCompletedEvent;
        //	HANDLE			renderActiveEvent;
        var   /*HANDLE*/renderThreadHandle: Thread? = null

        //
        // when we get a windows message, we store the time off so keyboard processing
        // can know the exact time of an event (not really needed now that we use async direct input)
        var sysMsgTime = 0

        //	unsigned long	renderThreadId;
        //	void			(*glimpRenderThread)( void );
        //	void			*smpData;
        var wglErrors = 0

        //
        var windowClassRegistered = false // SMP acceleration vars

        companion object {
            //	HWND			hWnd;
            //	HINSTANCE		hInstance;
            //
            val in_mouse: idCVar =
                idCVar("in_mouse", "1", CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_BOOL, "enable mouse input")

            //
            //	FILE			*log_fp;
            //
            //	unsigned short	oldHardwareGamma[3][256];
            // desktop gamma is saved here for restoration at exit
            //
            val sys_arch: idCVar = idCVar("sys_arch", "", CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_INIT, "")
            val sys_cpustring: idCVar =
                idCVar("sys_cpustring", "detect", CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_INIT, "")
            val win_allowAltTab: idCVar = idCVar(
                "win_allowAltTab",
                "0",
                CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_BOOL,
                "allow Alt-Tab when fullscreen"
            )
            val win_allowMultipleInstances: idCVar = idCVar(
                "win_allowMultipleInstances",
                "0",
                CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_BOOL,
                "allow multiple instances running concurrently"
            )
            val win_notaskkeys: idCVar = idCVar(
                "win_notaskkeys",
                "0",
                CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_INTEGER,
                "disable windows task keys"
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
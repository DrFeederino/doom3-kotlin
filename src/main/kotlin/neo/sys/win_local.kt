package neo.sys

import neo.framework.CVarSystem
import neo.framework.CVarSystem.idCVar
import java.awt.event.KeyListener
import java.awt.event.MouseListener

abstract class win_local {
    /*
     ===========================================================================

     Doom 3 GPL Source Code
     Copyright (C) 1999-2011 id Software LLC, a ZeniMax Media company.

     This file is part of the Doom 3 GPL Source Code (?Doom 3 Source Code?).

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
    //
    //
    //// WGL_ARB_extensions_string
    //public	PFNWGLGETEXTENSIONSSTRINGARBPROC wglGetExtensionsStringARB;
    //
    //// WGL_EXT_swap_interval
    //public	PFNWGLSWAPINTERVALEXTPROC wglSwapIntervalEXT;
    //
    //// WGL_ARB_pixel_format
    //public	PFNWGLGETPIXELFORMATATTRIBIVARBPROC wglGetPixelFormatAttribivARB;
    //public	PFNWGLGETPIXELFORMATATTRIBFVARBPROC wglGetPixelFormatAttribfvARB;
    //public	PFNWGLCHOOSEPIXELFORMATARBPROC wglChoosePixelFormatARB;
    //
    //// WGL_ARB_pbuffer
    //public	PFNWGLCREATEPBUFFERARBPROC	wglCreatePbufferARB;
    //public	PFNWGLGETPBUFFERDCARBPROC	wglGetPbufferDCARB;
    //public	PFNWGLRELEASEPBUFFERDCARBPROC	wglReleasePbufferDCARB;
    //public	PFNWGLDESTROYPBUFFERARBPROC	wglDestroyPbufferARB;
    //public	PFNWGLQUERYPBUFFERARBPROC	wglQueryPbufferARB;
    //
    //// WGL_ARB_render_texture
    //public	PFNWGLBINDTEXIMAGEARBPROC		wglBindTexImageARB;
    //public	PFNWGLRELEASETEXIMAGEARBPROC	wglReleaseTexImageARB;
    //public	PFNWGLSETPBUFFERATTRIBARBPROC	wglSetPbufferAttribARB;
    //
    //
    //static final int	MAX_OSPATH=			256;
    //
    ////#define	WINDOW_STYLE	(WS_OVERLAPPED|WS_BORDER|WS_CAPTION|WS_VISIBLE | WS_THICKFRAME)
    //
    //void	Sys_QueEvent( int time, sysEventType_t type, int value, int value2, int ptrLength, void *ptr );
    //
    //void	Sys_CreateConsole( void );
    //void	Sys_DestroyConsole( void );
    //
    //char	*Sys_ConsoleInput (void);
    //char	*Sys_GetCurrentUser( void );
    //
    //void	Win_SetErrorText( const char *text );
    //
    //cpuid_t	Sys_GetCPUId( void );
    //
    //int		MapKey (int key);
    //
    //
    //// Input subsystem
    //
    //void	IN_Init (void);
    //void	IN_Shutdown (void);
    //// add additional non keyboard / non mouse movement on top of the keyboard move cmd
    //
    //void	IN_DeactivateMouseIfWindowed( void );
    //void	IN_DeactivateMouse( void );
    //void	IN_ActivateMouse( void );
    //
    //void	IN_Frame( void );
    //
    //int		IN_DIMapKey( int key );
    //
    //void	DisableTaskKeys( BOOL bDisable, BOOL bBeep, BOOL bTaskMgr );
    //
    //
    //// window procedure
    //LONG WINAPI MainWndProc( HWND hWnd, UINT uMsg, WPARAM wParam, LPARAM lParam);
    //
    //void Conbuf_AppendText( const char *msg );
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
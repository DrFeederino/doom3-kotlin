package neo.sys

import neo.framework.Common
import neo.framework.EditField.idEditField
import neo.framework.Licensee
import neo.idlib.Text.Str.idStr
import neo.sys.RC.doom_resource
import neo.sys.win_local.Win32Vars_t
import java.awt.*
import java.awt.datatransfer.StringSelection
import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.util.logging.Level
import java.util.logging.Logger
import javax.swing.*

object win_syscon {
    const val CLEAR_ID = 3
    const val COMMAND_HISTORY = 64
    const val COPY_ID = 1
    const val EDIT_ID = 100
    const val ERRORBOX_ID = 10
    const val ERRORTEXT_ID = 11
    const val INPUT_ID = 101
    const val QUIT_ID = 2
    private const val CONSOLE_BUFFER_SIZE = 16384
    var s_wcd: WinConData = WinConData()
    private var s_totalChars: /*unsigned*/Long = 0

    //    static LONG WINAPI    ConWndProc(HWND hWnd, UINT uMsg, WPARAM wParam, LPARAM lParam) {
    //	char *cmdString;
    //	static bool s_timePolarity;
    //
    //	switch (uMsg) {
    //		case WM_ACTIVATE:
    //			if ( LOWORD( wParam ) != WA_INACTIVE ) {
    //				SetFocus( s_wcd.hwndInputLine );
    //			}
    //		break;
    //		case WM_CLOSE:
    //			if ( cvarSystem->IsInitialized() && com_skipRenderer.GetBool() ) {
    //				cmdString = Mem_CopyString( "quit" );
    //				Sys_QueEvent( 0, SE_CONSOLE, 0, 0, strlen( cmdString ) + 1, cmdString );
    //			} else if ( s_wcd.quitOnClose ) {
    //				PostQuitMessage( 0 );
    //			} else {
    //				Sys_ShowConsole( 0, false );
    //				win32.win_viewlog.SetBool( false );
    //			}
    //			return 0;
    //		case WM_CTLCOLORSTATIC:
    //			if ( ( HWND ) lParam == s_wcd.hwndBuffer ) {
    //				SetBkColor( ( HDC ) wParam, RGB( 0x00, 0x00, 0x80 ) );
    //				SetTextColor( ( HDC ) wParam, RGB( 0xff, 0xff, 0x00 ) );
    //				return ( long ) s_wcd.hbrEditBackground;
    //			} else if ( ( HWND ) lParam == s_wcd.hwndErrorBox ) {
    //				if ( s_timePolarity & 1 ) {
    //					SetBkColor( ( HDC ) wParam, RGB( 0x80, 0x80, 0x80 ) );
    //					SetTextColor( ( HDC ) wParam, RGB( 0xff, 0x0, 0x00 ) );
    //				} else {
    //					SetBkColor( ( HDC ) wParam, RGB( 0x80, 0x80, 0x80 ) );
    //					SetTextColor( ( HDC ) wParam, RGB( 0x00, 0x0, 0x00 ) );
    //				}
    //				return ( long ) s_wcd.hbrErrorBackground;
    //			}
    //			break;
    //		case WM_SYSCOMMAND:
    //			if ( wParam == SC_CLOSE ) {
    //				PostQuitMessage( 0 );
    //			}
    //			break;
    //		case WM_COMMAND:
    //			if ( wParam == COPY_ID ) {
    //				SendMessage( s_wcd.hwndBuffer, EM_SETSEL, 0, -1 );
    //				SendMessage( s_wcd.hwndBuffer, WM_COPY, 0, 0 );
    //			} else if ( wParam == QUIT_ID ) {
    //				if ( s_wcd.quitOnClose ) {
    //					PostQuitMessage( 0 );
    //				} else {
    //					cmdString = Mem_CopyString( "quit" );
    //					Sys_QueEvent( 0, SE_CONSOLE, 0, 0, strlen( cmdString ) + 1, cmdString );
    //				}
    //			} else if ( wParam == CLEAR_ID ) {
    //				SendMessage( s_wcd.hwndBuffer, EM_SETSEL, 0, -1 );
    //				SendMessage( s_wcd.hwndBuffer, EM_REPLACESEL, FALSE, ( LPARAM ) "" );
    //				UpdateWindow( s_wcd.hwndBuffer );
    //			}
    //			break;
    //		case WM_CREATE:
    //			s_wcd.hbrEditBackground = CreateSolidBrush( RGB( 0x00, 0x00, 0x80 ) );
    //			s_wcd.hbrErrorBackground = CreateSolidBrush( RGB( 0x80, 0x80, 0x80 ) );
    //			SetTimer( hWnd, 1, 1000, NULL );
    //			break;
    ///*
    //		case WM_ERASEBKGND:
    //			HGDIOBJ oldObject;
    //			HDC hdcScaled;
    //			hdcScaled = CreateCompatibleDC( ( HDC ) wParam );
    //			assert( hdcScaled != 0 );
    //			if ( hdcScaled ) {
    //				oldObject = SelectObject( ( HDC ) hdcScaled, s_wcd.hbmLogo );
    //				assert( oldObject != 0 );
    //				if ( oldObject )
    //				{
    //					StretchBlt( ( HDC ) wParam, 0, 0, s_wcd.windowWidth, s_wcd.windowHeight, 
    //						hdcScaled, 0, 0, 512, 384,
    //						SRCCOPY );
    //				}
    //				DeleteDC( hdcScaled );
    //				hdcScaled = 0;
    //			}
    //			return 1;
    //*/
    //		case WM_TIMER:
    //			if ( wParam == 1 ) {
    //				s_timePolarity = (bool)!s_timePolarity;
    //				if ( s_wcd.hwndErrorBox ) {
    //					InvalidateRect( s_wcd.hwndErrorBox, NULL, FALSE );
    //				}
    //			}
    //			break;
    //    }
    //
    //    return DefWindowProc( hWnd, uMsg, wParam, lParam );
    //    }
    //    LONG WINAPI    InputLineWndProc(HWND hWnd, UINT uMsg, WPARAM wParam, LPARAM lParam) {
    //	int key, cursor;
    //	switch ( uMsg ) {
    //	case WM_KILLFOCUS:
    //		if ( ( HWND ) wParam == s_wcd.hWnd || ( HWND ) wParam == s_wcd.hwndErrorBox ) {
    //			SetFocus( hWnd );
    //			return 0;
    //		}
    //		break;
    //
    //	case WM_KEYDOWN:
    //		key = MapKey( lParam );
    //
    //		// command history
    //		if ( ( key == K_UPARROW ) || ( key == K_KP_UPARROW ) ) {
    //			if ( s_wcd.nextHistoryLine - s_wcd.historyLine < COMMAND_HISTORY && s_wcd.historyLine > 0 ) {
    //				s_wcd.historyLine--;
    //			}
    //			s_wcd.consoleField = s_wcd.historyEditLines[ s_wcd.historyLine % COMMAND_HISTORY ];
    //
    //			SetWindowText( s_wcd.hwndInputLine, s_wcd.consoleField.GetBuffer() );
    //			SendMessage( s_wcd.hwndInputLine, EM_SETSEL, s_wcd.consoleField.GetCursor(), s_wcd.consoleField.GetCursor() );
    //			return 0;
    //		}
    //
    //		if ( ( key == K_DOWNARROW ) || ( key == K_KP_DOWNARROW ) ) {
    //			if ( s_wcd.historyLine == s_wcd.nextHistoryLine ) {
    //				return 0;
    //			}
    //			s_wcd.historyLine++;
    //			s_wcd.consoleField = s_wcd.historyEditLines[ s_wcd.historyLine % COMMAND_HISTORY ];
    //
    //			SetWindowText( s_wcd.hwndInputLine, s_wcd.consoleField.GetBuffer() );
    //			SendMessage( s_wcd.hwndInputLine, EM_SETSEL, s_wcd.consoleField.GetCursor(), s_wcd.consoleField.GetCursor() );
    //			return 0;
    //		}
    //		break;
    //
    //	case WM_CHAR:
    //		key = MapKey( lParam );
    //
    //		GetWindowText( s_wcd.hwndInputLine, s_wcd.consoleField.GetBuffer(), MAX_EDIT_LINE );
    //		SendMessage( s_wcd.hwndInputLine, EM_GETSEL, (WPARAM) NULL, (LPARAM) &cursor );
    //		s_wcd.consoleField.SetCursor( cursor );
    //
    //		// enter the line
    //		if ( key == K_ENTER || key == K_KP_ENTER ) {
    //			strncat( s_wcd.consoleText, s_wcd.consoleField.GetBuffer(), sizeof( s_wcd.consoleText ) - strlen( s_wcd.consoleText ) - 5 );
    //			strcat( s_wcd.consoleText, "\n" );
    //			SetWindowText( s_wcd.hwndInputLine, "" );
    //
    //			Sys_Printf( "]%s\n", s_wcd.consoleField.GetBuffer() );
    //
    //			// copy line to history buffer
    //			s_wcd.historyEditLines[s_wcd.nextHistoryLine % COMMAND_HISTORY] = s_wcd.consoleField;
    //			s_wcd.nextHistoryLine++;
    //			s_wcd.historyLine = s_wcd.nextHistoryLine;
    //
    //			s_wcd.consoleField.Clear();
    //
    //			return 0;
    //		}
    //
    //		// command completion
    //		if ( key == K_TAB ) {
    //			s_wcd.consoleField.AutoComplete();
    //
    //			SetWindowText( s_wcd.hwndInputLine, s_wcd.consoleField.GetBuffer() );
    //			//s_wcd.consoleField.SetWidthInChars( strlen( s_wcd.consoleField.GetBuffer() ) );
    //			SendMessage( s_wcd.hwndInputLine, EM_SETSEL, s_wcd.consoleField.GetCursor(), s_wcd.consoleField.GetCursor() );
    //
    //			return 0;
    //		}
    //
    //		// clear autocompletion buffer on normal key input
    //		if ( ( key >= K_SPACE && key <= K_BACKSPACE ) || 
    //			( key >= K_KP_SLASH && key <= K_KP_PLUS ) || ( key >= K_KP_STAR && key <= K_KP_EQUALS ) ) {
    //			s_wcd.consoleField.ClearAutoComplete();
    //		}
    //		break;
    //	}
    //
    //	return CallWindowProc( s_wcd.SysInputLineWndProc, hWnd, uMsg, wParam, lParam );
    //    }
    /*
     ** Sys_CreateConsole
     */
    fun Sys_CreateConsole() { //        throw new TODO_Exception();
//	HDC hDC;
        val   /*WNDCLASS*/wc: JFrame
        val   /*RECT*/rect: Rectangle
        //	const char *DEDCLASS = WIN32_CONSOLE_CLASS;
        val nHeight: Int
        val swidth: Int
        val sheight: Int
        val screen: Dimension
        //	int DEDSTYLE = WS_POPUPWINDOW | WS_CAPTION | WS_MINIMIZEBOX;
        var i: Int
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
        } catch (ex: ClassNotFoundException) {
            Logger.getLogger(win_syscon::class.java.name).log(Level.SEVERE, null, ex)
            return
        } catch (ex: InstantiationException) {
            Logger.getLogger(win_syscon::class.java.name).log(Level.SEVERE, null, ex)
            return
        } catch (ex: IllegalAccessException) {
            Logger.getLogger(win_syscon::class.java.name).log(Level.SEVERE, null, ex)
            return
        } catch (ex: UnsupportedLookAndFeelException) {
            Logger.getLogger(win_syscon::class.java.name).log(Level.SEVERE, null, ex)
            return
        }
        wc = JFrame(Licensee.GAME_NAME)
        //
//        wc.getContentPane().add(jPanel);
//
        rect = Rectangle(0, 0, 540, 450)
        //	AdjustWindowRect( &rect, DEDSTYLE, FALSE );//TODO: check this function.
//
//	hDC = GetDC( GetDesktopWindow() );
        screen = Toolkit.getDefaultToolkit().screenSize
        swidth = screen.width
        sheight = screen.height
        //	ReleaseDC( GetDesktopWindow(), hDC );
//
        s_wcd.windowWidth = rect.width - rect.x + 1
        s_wcd.windowHeight = rect.height - rect.y + 1
        //
//	//s_wcd.hbmLogo = LoadBitmap( win32.hInstance, MAKEINTRESOURCE( IDB_BITMAP_LOGO) );
//
//        wc.setName(GAME_NAME);
        wc.iconImage = doom_resource.IDI_ICON1
        wc.layout = FlowLayout()
        wc.setLocation((swidth - 600) / 2, (sheight - 450) / 2)
        wc.preferredSize = Dimension(540, 450)
        wc.isResizable = false
        //        wc.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        s_wcd.hWnd = wc

        //
        // create fonts
        //
        nHeight = 12 //-MulDiv( 8, GetDeviceCaps( hDC, LOGPIXELSY ), 72 );
        s_wcd.hfBufferFont = Font(
            "Courier New",
            0,
            nHeight
        ) //CreateFont(nHeight, 0, 0, 0, FW_LIGHT, 0, 0, 0, DEFAULT_CHARSET, OUT_DEFAULT_PRECIS, CLIP_DEFAULT_PRECIS, DEFAULT_QUALITY, FF_MODERN | FIXED_PITCH, "Courier New");

        //
        // create the input line
        //
        s_wcd.hwndInputLine = JTextField("edit")
        s_wcd.hwndInputLine!!.isEditable = false
        s_wcd.hwndInputLine!!.setLocation(6, 400)
        s_wcd.hwndInputLine!!.preferredSize = Dimension(528, 20)

        //
        // create the buttons
        //
        s_wcd.hwndButtonCopy = JButton("copy")
        s_wcd.hwndButtonCopy!!.setBounds(5, 425, 72, 24)
        //        s_wcd.hwndButtonCopy.setLocation(5, 425);
//        s_wcd.hwndButtonCopy.setPreferredSize(new Dimension(72, 24));
//        s_wcd.hwndButtonCopy.setAction();
        s_wcd.hwndButtonCopy!!.addMouseListener(object : Click() {
            override fun mouseClicked(e: MouseEvent) {
//                System.out.println("---" + s_wcd.buffer.getText());
                Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(s_wcd.textArea!!.text), null)
                println("--DBUG-- " + s_wcd.buffer.toString())
            }
        })
        s_wcd.hwndButtonClear = JButton("clear")
        s_wcd.hwndButtonClear!!.setBounds(82, 425, 72, 24)
        //        s_wcd.hwndButtonClear.setLocation(82, 425);
//        s_wcd.hwndButtonClear.setPreferredSize(new Dimension(72, 24));
//        s_wcd.hwndButtonClear.setAction();
        s_wcd.hwndButtonClear!!.addMouseListener(object : Click() {
            override fun mouseClicked(e: MouseEvent) {
                s_wcd.textArea!!.text = ""
            }
        })
        s_wcd.hwndButtonQuit = JButton("quit")
        s_wcd.hwndButtonQuit!!.setBounds(462, 425, 72, 24)
        //        s_wcd.hwndButtonQuit.setLocation(462, 425);
//        s_wcd.hwndButtonQuit.setPreferredSize(new Dimension(72, 24));
//        s_wcd.hwndButtonQuit.setAction();
        s_wcd.hwndButtonQuit!!.addMouseListener(object : Click() {
            override fun mouseClicked(e: MouseEvent) {
                Common.common.Quit()
            }
        })

        //
        // create the scrollbuffer text area
        //
        s_wcd.textArea = JTextArea()
        s_wcd.textArea!!.isEditable = false
        s_wcd.textArea!!.lineWrap = true
        s_wcd.textArea!!.wrapStyleWord = true
        s_wcd.textArea!!.font = s_wcd.hfBufferFont
        s_wcd.textArea!!.background = Color.BLUE.darker().darker()
        s_wcd.textArea!!.foreground = Color.YELLOW.brighter()

        //
        // create the scrollbuffer
        //
        s_wcd.hwndBuffer = JScrollPane(
            s_wcd.textArea,
            JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        )
        s_wcd.hwndBuffer!!.setLocation(6, 40)
        s_wcd.hwndBuffer!!.preferredSize = Dimension(526, 354)
        //
//	s_wcd.SysInputLineWndProc = ( WNDPROC ) SetWindowLong( s_wcd.hwndInputLine, GWL_WNDPROC, ( long ) InputLineWndProc );
//	SendMessage( s_wcd.hwndInputLine, WM_SETFONT, ( WPARAM ) s_wcd.hfBufferFont, 0 );
//
        wc.contentPane.add(s_wcd.hwndInputLine)
        wc.contentPane.add(s_wcd.hwndBuffer)
        wc.contentPane.add(s_wcd.hwndButtonCopy)
        wc.contentPane.add(s_wcd.hwndButtonClear)
        wc.contentPane.add(s_wcd.hwndButtonQuit)
        wc.pack()
        //TODO: switch off, for testing purposes only.
//        wc.setVisible(true);

        // don't show it now that we have a splash screen up
        if (Win32Vars_t.win_viewlog.GetBool()) {
            wc.isVisible = true
            //		UpdateWindow( s_wcd.hWnd );
//		SetForegroundWindow( s_wcd.hWnd );
            s_wcd.hwndInputLine!!.isFocusable = true
        }
        s_wcd.consoleField.Clear()
        i = 0
        while (i < COMMAND_HISTORY) {

//            s_wcd.historyEditLines[i].Clear();
            s_wcd.historyEditLines[i] = idEditField()
            i++
        }
    }
    /**
     * <editor-fold defaultstate="collapsed"> ******
    </editor-fold> */
    /*
     void Sys_CreateConsole( void ) {
     HDC hDC;
     WNDCLASS wc;
     RECT rect;
     const char *DEDCLASS = WIN32_CONSOLE_CLASS;
     int nHeight;
     int swidth, sheight;
     int DEDSTYLE = WS_POPUPWINDOW | WS_CAPTION | WS_MINIMIZEBOX;
     int i;

     memset( &wc, 0, sizeof( wc ) );

     wc.style         = 0;
     wc.lpfnWndProc   = (WNDPROC) ConWndProc;
     wc.cbClsExtra    = 0;
     wc.cbWndExtra    = 0;
     wc.hInstance     = win32.hInstance;
     wc.hIcon         = LoadIcon( win32.hInstance, MAKEINTRESOURCE(IDI_ICON1));
     wc.hCursor       = LoadCursor (NULL,IDC_ARROW);
     wc.hbrBackground = (struct HBRUSH__ *)COLOR_WINDOW;
     wc.lpszMenuName  = 0;
     wc.lpszClassName = DEDCLASS;

     if ( !RegisterClass (&wc) ) {
     return;
     }

     rect.left = 0;
     rect.right = 540;
     rect.top = 0;
     rect.bottom = 450;
     AdjustWindowRect( &rect, DEDSTYLE, FALSE );

     hDC = GetDC( GetDesktopWindow() );
     swidth = GetDeviceCaps( hDC, HORZRES );
     sheight = GetDeviceCaps( hDC, VERTRES );
     ReleaseDC( GetDesktopWindow(), hDC );

     s_wcd.windowWidth = rect.right - rect.left + 1;
     s_wcd.windowHeight = rect.bottom - rect.top + 1;

     //s_wcd.hbmLogo = LoadBitmap( win32.hInstance, MAKEINTRESOURCE( IDB_BITMAP_LOGO) );

     s_wcd.hWnd = CreateWindowEx( 0,
     DEDCLASS,
     GAME_NAME,
     DEDSTYLE,
     ( swidth - 600 ) / 2, ( sheight - 450 ) / 2 , rect.right - rect.left + 1, rect.bottom - rect.top + 1,
     NULL,
     NULL,
     win32.hInstance,
     NULL );

     if ( s_wcd.hWnd == NULL ) {
     return;
     }

     //
     // create fonts
     //
     hDC = GetDC( s_wcd.hWnd );
     nHeight = -MulDiv( 8, GetDeviceCaps( hDC, LOGPIXELSY ), 72 );

     s_wcd.hfBufferFont = CreateFont( nHeight, 0, 0, 0, FW_LIGHT, 0, 0, 0, DEFAULT_CHARSET, OUT_DEFAULT_PRECIS, CLIP_DEFAULT_PRECIS, DEFAULT_QUALITY, FF_MODERN | FIXED_PITCH, "Courier New" );

     ReleaseDC( s_wcd.hWnd, hDC );

     //
     // create the input line
     //
     s_wcd.hwndInputLine = CreateWindow( "edit", NULL, WS_CHILD | WS_VISIBLE | WS_BORDER | 
     ES_LEFT | ES_AUTOHSCROLL,
     6, 400, 528, 20,
     s_wcd.hWnd, 
     ( HMENU ) INPUT_ID,	// child window ID
     win32.hInstance, NULL );

     //
     // create the buttons
     //
     s_wcd.hwndButtonCopy = CreateWindow( "button", NULL, BS_PUSHBUTTON | WS_VISIBLE | WS_CHILD | BS_DEFPUSHBUTTON,
     5, 425, 72, 24,
     s_wcd.hWnd, 
     ( HMENU ) COPY_ID,	// child window ID
     win32.hInstance, NULL );
     SendMessage( s_wcd.hwndButtonCopy, WM_SETTEXT, 0, ( LPARAM ) "copy" );

     s_wcd.hwndButtonClear = CreateWindow( "button", NULL, BS_PUSHBUTTON | WS_VISIBLE | WS_CHILD | BS_DEFPUSHBUTTON,
     82, 425, 72, 24,
     s_wcd.hWnd, 
     ( HMENU ) CLEAR_ID,	// child window ID
     win32.hInstance, NULL );
     SendMessage( s_wcd.hwndButtonClear, WM_SETTEXT, 0, ( LPARAM ) "clear" );

     s_wcd.hwndButtonQuit = CreateWindow( "button", NULL, BS_PUSHBUTTON | WS_VISIBLE | WS_CHILD | BS_DEFPUSHBUTTON,
     462, 425, 72, 24,
     s_wcd.hWnd, 
     ( HMENU ) QUIT_ID,	// child window ID
     win32.hInstance, NULL );
     SendMessage( s_wcd.hwndButtonQuit, WM_SETTEXT, 0, ( LPARAM ) "quit" );


     //
     // create the scrollbuffer
     //
     s_wcd.hwndBuffer = CreateWindow( "edit", NULL, WS_CHILD | WS_VISIBLE | WS_VSCROLL | WS_BORDER | 
     ES_LEFT | ES_MULTILINE | ES_AUTOVSCROLL | ES_READONLY,
     6, 40, 526, 354,
     s_wcd.hWnd, 
     ( HMENU ) EDIT_ID,	// child window ID
     win32.hInstance, NULL );
     SendMessage( s_wcd.hwndBuffer, WM_SETFONT, ( WPARAM ) s_wcd.hfBufferFont, 0 );

     s_wcd.SysInputLineWndProc = ( WNDPROC ) SetWindowLong( s_wcd.hwndInputLine, GWL_WNDPROC, ( long ) InputLineWndProc );
     SendMessage( s_wcd.hwndInputLine, WM_SETFONT, ( WPARAM ) s_wcd.hfBufferFont, 0 );

     // don't show it now that we have a splash screen up
     if ( win32.win_viewlog.GetBool() ) {
     ShowWindow( s_wcd.hWnd, SW_SHOWDEFAULT);
     UpdateWindow( s_wcd.hWnd );
     SetForegroundWindow( s_wcd.hWnd );
     SetFocus( s_wcd.hwndInputLine );
     }



     s_wcd.consoleField.Clear();

     for ( i = 0 ; i < COMMAND_HISTORY ; i++ ) {
     s_wcd.historyEditLines[i].Clear();
     }
     }
     */
    /**
     * ******
     *
     */
    /*
     ** Sys_DestroyConsole
     */
    fun Sys_DestroyConsole() {
        if (s_wcd.hWnd != null) {
//		ShowWindow( s_wcd.hWnd, SW_HIDE );
            s_wcd.hWnd!!.isVisible = false
            s_wcd.hWnd!!.dispose()
            //		CloseWindow( s_wcd.hWnd );
//		DestroyWindow( s_wcd.hWnd );
            s_wcd.hWnd = null
        }
    }

    /*
     ** Sys_ShowConsole
     */
    fun Sys_ShowConsole(visLevel: Int, quitOnClose: Boolean) {
        s_wcd.setQuitOnClose(quitOnClose)
        if (s_wcd.hWnd == null) {
            return
        }
        when (visLevel) {
            0 -> s_wcd.hWnd!!.isVisible = false //ShowWindow( s_wcd.hWnd, SW_HIDE );
            1 -> {
                s_wcd.textArea!!.text = s_wcd.buffer.toString()
                s_wcd.hWnd!!.isVisible = true //ShowWindow( s_wcd.hWnd, SW_SHOWNORMAL );
                s_wcd.hwndBuffer!!.verticalScrollBar.value =
                    0xffff //SendMessage(s_wcd.hwndBuffer, EM_LINESCROLL, 0, 0xffff);
            }

            2 -> {
                s_wcd.textArea!!.text = s_wcd.buffer.toString()
                s_wcd.hWnd!!.isVisible = true
                s_wcd.hWnd!!.state = JFrame.ICONIFIED //ShowWindow( s_wcd.hWnd, SW_MINIMIZE );
            }

            else -> win_main.Sys_Error("Invalid visLevel %d sent to Sys_ShowConsole\n", visLevel)
        }
    }

    /*
     ** Sys_ConsoleInput
     */
    fun Sys_ConsoleInput(): String? {
        return null
        //	
//	if ( s_wcd.consoleText[0] == 0 ) {
//		return NULL;
//	}
//		
//	strcpy( s_wcd.returnedText, s_wcd.consoleText );
//	s_wcd.consoleText[0] = 0;
//	
//	return s_wcd.returnedText;
    }

    /*
     ** Conbuf_AppendText
     */
    fun Conbuf_AppendText(pMsg: String) {
        val buffer = StringBuilder(CONSOLE_BUFFER_SIZE * 2)
        var b = 0 //buffer;
        val msg: String
        val bufLen: Int
        var i = 0

        //
        // if the message is REALLY long, use just the last portion of it
        //
        msg = if (pMsg.isNotEmpty()
            && pMsg.length > CONSOLE_BUFFER_SIZE - 1
        ) {
            pMsg.substring(pMsg.length - CONSOLE_BUFFER_SIZE + 1)
        } else {
            pMsg
        }

        //
        // copy into an intermediate buffer
        //
        while (i < msg.length //&& msg.charAt(i) != 0)//TODO: is the character ever '0' or '\0', or are we just wasting our fucking resources?
            && b < buffer.capacity() - 1
        ) {
            if (msg[i] == '\n'
                && (i + 1 < msg.length
                        && msg[i + 1] == '\r')
            ) {
                buffer.insert(b + 0, '\r')
                buffer.insert(b + 1, '\n')
                b += 2
                i++
            } else if (msg[i] == '\r') {
                buffer.insert(b + 0, '\r')
                buffer.insert(b + 1, '\n')
            } else if (msg[i] == '\n') {
                buffer.insert(b + 0, '\r')
                buffer.insert(b + 1, '\n')
                b += 2
            } else if (idStr.IsColor(msg.substring(i))) {
                i++
            } else {
                buffer.insert(b++, msg[i])
            }
            i++
        }
        //        buffer.insert(b, '\0');
        bufLen = b //- buffer;
        s_totalChars += bufLen.toLong()

        //
        // replace selection instead of appending if we're overflowing
        //
        if (s_totalChars > 0x7000) {
            s_wcd.buffer = buffer
            s_totalChars = bufLen.toLong()
        } else {
            s_wcd.buffer.append(buffer)
        }

//        //
//        // put this text into the windows console
//        //
//        SendMessage(s_wcd.hwndBuffer, EM_LINESCROLL, 0, 0xffff);
//        SendMessage(s_wcd.hwndBuffer, EM_SCROLLCARET, 0, 0);
//        SendMessage(s_wcd.hwndBuffer, EM_REPLACESEL, 0, (LPARAM) buffer);
    }

    /*
     ** Win_SetErrorText
     */
    fun Win_SetErrorText(buf: String) {
        idStr.Copynz(s_wcd.errorString, buf)
        if (s_wcd.hwndErrorBox == null) {
            s_wcd.hwndErrorBox = JTextField("static")
            s_wcd.hwndErrorBox!!.isEditable = false
            s_wcd.hwndErrorBox!!.setLocation(6, 5)
            s_wcd.hwndErrorBox!!.preferredSize = Dimension(526, 30)
            s_wcd.hwndErrorBox!!.background = Color.GRAY
            s_wcd.hwndErrorBox!!.foreground = Color.RED
            s_wcd.hwndErrorBox!!.text = s_wcd.errorString.toString()
            s_wcd.hWnd!!.contentPane.add(s_wcd.hwndErrorBox)
            s_wcd.hWnd!!.contentPane.remove(s_wcd.hwndInputLine)
            s_wcd.hWnd!!.pack()
            s_wcd.hwndInputLine = null
        }
    }

    class WinConData {
        var buffer: StringBuilder = StringBuilder(0x7000)

        //
        //	int			nextHistoryLine;// the last line in the history buffer, not masked
        //	int			historyLine;	// the line being displayed from history buffer
        //								// will be <= nextHistoryLine
        //
        var consoleField: idEditField = idEditField()

        //
        var errorString: StringBuilder = StringBuilder(80)
        var   /*HWND*/hWnd: JFrame? = null

        //	HWND		hwndErrorText;
        //
        //	HBITMAP		hbmLogo;
        //	HBITMAP		hbmClearBitmap;
        //
        //	HBRUSH		hbrEditBackground;
        //	HBRUSH		hbrErrorBackground;
        //
        var   /*HFONT*/hfBufferFont: Font? = null
        var   /*HFONT*/hfButtonFont: Font? = null

        //
        //	WNDPROC		SysInputLineWndProc;
        //
        var historyEditLines: Array<idEditField?> = arrayOfNulls<idEditField?>(COMMAND_HISTORY)
        var   /*HWND*/hwndBuffer: JScrollPane? = null

        //
        var   /*HWND*/hwndButtonClear: JButton? = null
        var   /*HWND*/hwndButtonCopy: JButton? = null
        var   /*HWND*/hwndButtonQuit: JButton? = null

        //
        var   /*HWND*/hwndErrorBox: JTextField? = null

        //
        var   /*HWND*/hwndInputLine: JTextField? = null
        var textArea: JTextArea? = null

        //
        //	char		consoleText[512], returnedText[512];
        var windowWidth = 0
        var windowHeight = 0
        fun setQuitOnClose(quitOnClose: Boolean) {
            if (quitOnClose) {
                hWnd!!.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
                hWnd!!.addWindowListener(QUIT_ON_CLOSE)
            } else {
                hWnd!!.defaultCloseOperation = JFrame.DO_NOTHING_ON_CLOSE
                hWnd!!.removeWindowListener(QUIT_ON_CLOSE)
            }
        }

        companion object {
            //TODO:refactor names to reflect the types; e.g:hWnd -> jFrame or something.
            //
            private val QUIT_ON_CLOSE: WindowAdapter = object : WindowAdapter() {
                override fun windowClosing(e: WindowEvent) {
                    Common.common.Quit()
                }
            }
        }
    }

    internal abstract class Click : MouseListener {
        override fun mouseClicked(e: MouseEvent) {}
        override fun mousePressed(e: MouseEvent) {}
        override fun mouseReleased(e: MouseEvent) {}
        override fun mouseEntered(e: MouseEvent) {}
        override fun mouseExited(e: MouseEvent) {}
    }
}
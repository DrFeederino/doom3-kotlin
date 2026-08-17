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

    /*
     ** Sys_CreateConsole
     */
    fun Sys_CreateConsole() {
        val wc: JFrame
        val rect: Rectangle
        val nHeight: Int
        val swidth: Int
        val sheight: Int
        val screen: Dimension
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
        rect = Rectangle(0, 0, 540, 450)
        screen = Toolkit.getDefaultToolkit().screenSize

        swidth = screen.width
        sheight = screen.height

        s_wcd.windowWidth = rect.width - rect.x + 1
        s_wcd.windowHeight = rect.height - rect.y + 1
        wc.iconImage = doom_resource.IDI_ICON1
        wc.layout = FlowLayout()
        wc.setLocation((swidth - 600) / 2, (sheight - 450) / 2)
        wc.preferredSize = Dimension(540, 450)
        wc.isResizable = false
        s_wcd.hWnd = wc // create fonts
        //
        nHeight = 12
        s_wcd.hfBufferFont = Font(
            "Courier New", 0, nHeight
        ) // create the input line
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
        s_wcd.hwndButtonCopy!!.addMouseListener(object : Click() {
            override fun mouseClicked(e: MouseEvent) {
                Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(s_wcd.textArea!!.text), null)
            }
        })
        s_wcd.hwndButtonClear = JButton("clear")
        s_wcd.hwndButtonClear!!.setBounds(82, 425, 72, 24)
        s_wcd.hwndButtonClear!!.addMouseListener(object : Click() {
            override fun mouseClicked(e: MouseEvent) {
                s_wcd.textArea!!.text = ""
            }
        })
        s_wcd.hwndButtonQuit = JButton("quit")
        s_wcd.hwndButtonQuit!!.setBounds(462, 425, 72, 24)
        s_wcd.hwndButtonQuit!!.addMouseListener(object : Click() {
            override fun mouseClicked(e: MouseEvent) {
                Common.common.Quit()
            }
        })

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
            s_wcd.textArea, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        )
        s_wcd.hwndBuffer!!.setLocation(6, 40)
        s_wcd.hwndBuffer!!.preferredSize = Dimension(526, 354)

        wc.contentPane.add(s_wcd.hwndInputLine)
        wc.contentPane.add(s_wcd.hwndBuffer)
        wc.contentPane.add(s_wcd.hwndButtonCopy)
        wc.contentPane.add(s_wcd.hwndButtonClear)
        wc.contentPane.add(s_wcd.hwndButtonQuit)
        wc.pack()

        // don't show it now that we have a splash screen up
        if (Win32Vars_t.win_viewlog.GetBool()) {
            wc.isVisible = true
            s_wcd.hwndInputLine!!.isFocusable = true
        }
        s_wcd.consoleField.Clear()
        i = 0
        while (i < COMMAND_HISTORY) {
            s_wcd.historyEditLines[i] = idEditField()
            i++
        }
    }

    /*
     ** Sys_DestroyConsole
     */
    fun Sys_DestroyConsole() {
        if (s_wcd.hWnd != null) {
            EventQueue.invokeLater {
                s_wcd.hWnd?.isVisible = false
                s_wcd.hWnd?.dispose()
            }
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
        msg = if (pMsg.isNotEmpty() && pMsg.length > CONSOLE_BUFFER_SIZE - 1) {
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
            if (msg[i] == '\n' && (i + 1 < msg.length && msg[i + 1] == '\r')) {
                buffer.insert(b + 0, '\r')
                buffer.insert(b + 1, '\n')
                b += 2
                i++
            } else if (msg[i] == '\r') {
                buffer.insert(b + 0, '\r')
                buffer.insert(b + 1, '\n')
                b += 2
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
        var consoleField: idEditField = idEditField()
        var errorString: StringBuilder = StringBuilder(80)
        var hWnd: JFrame? = null
        var hfBufferFont: Font? = null
        var hfButtonFont: Font? = null

        //
        //	WNDPROC		SysInputLineWndProc;
        //
        var historyEditLines: Array<idEditField?> = arrayOfNulls<idEditField?>(COMMAND_HISTORY)
        var hwndBuffer: JScrollPane? = null
        var hwndButtonClear: JButton? = null
        var hwndButtonCopy: JButton? = null
        var hwndButtonQuit: JButton? = null
        var hwndErrorBox: JTextField? = null
        var hwndInputLine: JTextField? = null
        var textArea: JTextArea? = null
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
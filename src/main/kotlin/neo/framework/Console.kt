package neo.framework

import neo.Renderer.Material
import neo.Renderer.RenderSystem
import neo.Sound.snd_system
import neo.Sound.sound.soundDecoderInfo_t
import neo.TempDump
import neo.Tools.edit_public
import neo.framework.Async.AsyncNetwork
import neo.framework.Async.AsyncNetwork.idAsyncNetwork
import neo.framework.CVarSystem.idCVar
import neo.framework.CmdSystem.cmdExecution_t
import neo.framework.CmdSystem.cmdFunction_t
import neo.framework.EditField.idEditField
import neo.framework.File_h.idFile
import neo.framework.KeyInput.idKeyInput
import neo.idlib.*
import neo.idlib.Text.Str
import neo.idlib.Text.Str.idStr
import neo.idlib.math.idMath
import neo.idlib.math.idVec4
import neo.sys.sys_public.sysEventType_t
import neo.sys.sys_public.sysEvent_s
import neo.sys.win_input
import neo.sys.win_shared
import kotlin.experimental.and

class Console {
    /*
     ===============================================================================

     The console is strictly for development and advanced users. It should
     never be used to convey actual game information to the user, which should
     always be done through a GUI.

     The force options are for the editor console display window, which
     doesn't respond to pull up / pull down

     ===============================================================================
     */
    abstract class idConsole {
        //	virtual			~idConsole( void ) {}
        @Throws(idException::class)
        abstract fun Init()
        abstract fun Shutdown()

        // can't be combined with Init, because Init happens before renderer is started
        @Throws(idException::class)
        abstract fun LoadGraphics()

        @Throws(idException::class)
        abstract fun ProcessEvent(event: sysEvent_s, forceAccept: Boolean): Boolean

        // the system code can release the mouse pointer when the console is active
        abstract fun Active(): Boolean

        // clear the timers on any recent prints that are displayed in the notify lines
        abstract fun ClearNotifyLines()

        // some console commands, like timeDemo, will force the console closed before they start
        abstract fun Close()
        abstract fun Draw(forceFullScreen: Boolean)
        abstract fun Print(text: String)
    }

    // the console will query the cvar and command systems for
    // command completion information
    class idConsoleLocal : idConsole() {
        companion object {
            private val con_noPrint: idCVar
            private val con_notifyTime: idCVar = idCVar(
                "con_notifyTime",
                "3",
                CVarSystem.CVAR_SYSTEM,
                "time messages are displayed onscreen when console is pulled up"
            )

            //
            private val con_speed: idCVar =
                idCVar("con_speed", "3", CVarSystem.CVAR_SYSTEM, "speed at which the console moves up and down")

            /*
         ================
         DrawNotify

         Draws the last few lines of output transparently over the game top
         ================
         */
            var drawNotifyTotal = 0

            init {
                con_noPrint = idCVar(
                    "con_noPrint",
                    "1",
                    CVarSystem.CVAR_BOOL or CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_NOCHEAT,
                    "print on the console but not onscreen when console is pulled up"
                )
            }
        }

        private val historyEditLines: Array<idEditField> = Array(COMMAND_HISTORY) { idEditField() }
        private val text: ShortArray = ShortArray(CON_TEXTSIZE)
        private val times: IntArray =
            IntArray(NUM_CON_TIMES) // cls.realtime time the line was generated for transparent notify lines

        //============================
        var charSetShader: Material.idMaterial? = null
        private val color: idVec4 = idVec4()
        private var consoleField: idEditField = idEditField()
        private var consoleShader: Material.idMaterial? = null
        private var current // line where next message will be printed
                = 0
        private var display // bottom of console displays this line
                = 0
        private var displayFrac // approaches finalFrac at scr_conspeed
                = 0.0f
        private var finalFrac // 0.0f to 1.0f lines of console to display
                = 0.0f
        private var fracTime // time of last displayFrac update
                = 0
        private var historyLine // the line being displayed from history buffer will be <= nextHistoryLine
                = 0

        //
        //============================
        //
        private var keyCatching = false
        private var lastKeyEvent // time of last key event for scroll delay
                = 0

        //
        private var nextHistoryLine // the last line in the history buffer, not masked
                = 0
        private var nextKeyEvent // keyboard repeat rate
                = 0

        //
        //
        //
        private var vislines // in scanlines
                = 0

        //
        private var whiteShader: Material.idMaterial? = null
        private var x // offset in current line for next print
                = 0

        @Throws(idException::class)
        override fun Init() {
            var i: Int
            keyCatching = false
            lastKeyEvent = -1
            nextKeyEvent = CONSOLE_FIRSTREPEAT
            consoleField = idEditField() //.Clear();
            consoleField.SetWidthInChars(LINE_WIDTH)
            i = 0
            while (i < COMMAND_HISTORY) {
                historyEditLines[i] = idEditField() //.Clear();
                historyEditLines[i].SetWidthInChars(LINE_WIDTH)
                i++
            }
            CmdSystem.cmdSystem.AddCommand(
                "clear",
                Con_Clear_f.getInstance(),
                CmdSystem.CMD_FL_SYSTEM,
                "clears the console"
            )
            CmdSystem.cmdSystem.AddCommand(
                "conDump",
                Con_Dump_f.getInstance(),
                CmdSystem.CMD_FL_SYSTEM,
                "dumps the console text to a file"
            )
        }

        override fun Shutdown() {
            CmdSystem.cmdSystem.RemoveCommand("clear")
            CmdSystem.cmdSystem.RemoveCommand("conDump")
        }

        /*
         ==============
         LoadGraphics

         Can't be combined with init, because init happens before
         the renderSystem is initialized
         ==============
         */
        @Throws(idException::class)
        override fun LoadGraphics() {
            charSetShader = DeclManager.declManager.FindMaterial("textures/bigchars")
            whiteShader = DeclManager.declManager.FindMaterial("_white")
            consoleShader = DeclManager.declManager.FindMaterial("console")
        }

        @Throws(idException::class)
        override fun ProcessEvent(event: sysEvent_s, forceAccept: Boolean): Boolean {
            var consoleKey: Boolean
            consoleKey =
                event.evType == sysEventType_t.SE_KEY && (event.evValue == win_input.Sys_GetConsoleKey(false).code || event.evValue == win_input.Sys_GetConsoleKey(
                    true
                ).code)
            if (ID_CONSOLE_LOCK) {
                // If the console's not already down, and we have it turned off, check for ctrl+alt
                if (!keyCatching && !Common.com_allowConsole.GetBool()) {
                    if (!idKeyInput.IsDown(KeyInput.K_CTRL) || !idKeyInput.IsDown(KeyInput.K_ALT)) {
                        consoleKey = false
                    }
                }
            }

            // we always catch the console key event
            if (!forceAccept && consoleKey) {
                // ignore up events
                if (event.evValue2 == 0) {
                    return true
                }
                consoleField.ClearAutoComplete()

                // a down event will toggle the destination lines
                if (keyCatching) {
                    Close()
                    win_input.Sys_GrabMouseCursor(true)
                    CVarSystem.cvarSystem.SetCVarBool("ui_chat", false)
                } else {
                    consoleField.Clear()
                    keyCatching = true
                    if (idKeyInput.IsDown(KeyInput.K_SHIFT)) {
                        // if the shift key is down, don't open the console as much
                        SetDisplayFraction(0.2f)
                    } else {
                        SetDisplayFraction(0.5f)
                    }
                    CVarSystem.cvarSystem.SetCVarBool("ui_chat", true)
                }
                return true
            }

            // if we aren't key catching, dump all the other events
            if (!forceAccept && !keyCatching) {
                return false
            }

            // handle key and character events
            if (event.evType == sysEventType_t.SE_CHAR) {
                // never send the console key as a character
                if (event.evValue != win_input.Sys_GetConsoleKey(false).code && event.evValue != win_input.Sys_GetConsoleKey(
                        true
                    ).code
                ) {
                    consoleField.CharEvent(event.evValue)
                }
                return true
            }
            if (event.evType == sysEventType_t.SE_KEY) {
                // ignore up key events
                if (event.evValue2 == 0) {
                    return true
                }
                KeyDownEvent(event.evValue)
                return true
            }

            // we don't handle things like mouse, joystick, and network packets
            return false
        }

        override fun Active(): Boolean {
            return keyCatching
        }

        override fun ClearNotifyLines() {
            var i: Int
            i = 0
            while (i < NUM_CON_TIMES) {
                times[i] = 0
                i++
            }
        }

        override fun Close() {
            keyCatching = false
            SetDisplayFraction(0.0f)
            displayFrac = 0.0f // don't scroll to that point, go immediately
            ClearNotifyLines()
        }

        /*
         ================
         Print

         Handles cursor positioning, line wrapping, etc
         ================
         */
        override fun Print(txt: String) {
            var y: Int
            var c = 0
            var l: Int
            var color: Int
            var txt_p = 0
            if (ID_ALLOW_TOOLS) {
                edit_public.RadiantPrint(txt)
                if (Common.com_editors and Common.EDITOR_MATERIAL != 0) {
                    edit_public.MaterialEditorPrintConsole(txt)
                }
            }
            color = idStr.ColorIndex(Str.C_COLOR_CYAN)
            while (txt_p < txt.length
                && txt[txt_p].also { c = it.code } != Char(0)
            ) {
                if (idStr.IsColor(txt.substring(txt_p))) {
                    val colorChar: Char = txt[txt_p + 1]
                    color = if (colorChar.code == Str.C_COLOR_DEFAULT) {
                        idStr.ColorIndex(Str.C_COLOR_CYAN)
                    } else {
                        idStr.ColorIndex(colorChar.code)
                    }
                    txt_p += 2
                    continue
                }
                y = current % TOTAL_LINES

                // if we are about to print a new word, check to see
                // if we should wrap to the new line
                if (c > ' '.code && (x == 0 || text[y * LINE_WIDTH + x - 1] <= ' '.code)) {
                    // count word length
                    l = 0
                    while (l < LINE_WIDTH && l < txt.length) {
                        if (txt[l] <= ' ') {
                            break
                        }
                        l++
                    }

                    // word wrap
                    if (l != LINE_WIDTH && x + l >= LINE_WIDTH) {
                        Linefeed()
                    }
                }
                txt_p++
                when (c) {
                    '\n'.code -> Linefeed()
                    '\t'.code -> do {
                        text[y * LINE_WIDTH + x] = (color shl 8 or ' '.code).toShort()
                        x++
                        if (x >= LINE_WIDTH) {
                            Linefeed()
                            x = 0
                        }
                    } while (x and 3 != 0)

                    '\r'.code -> x = 0
                    else -> {
                        text[y * LINE_WIDTH + x] = (color shl 8 or c).toShort()
                        x++
                        if (x >= LINE_WIDTH) {
                            Linefeed()
                            x = 0
                        }
                    }
                }
            }

            // mark time for transparent overlay
            if (current >= 0) {
                times[current % NUM_CON_TIMES] = Common.com_frameTime
            }
        }

        /*
         ==============
         Draw

         ForceFullScreen is used by the editor
         ==============
         */
        override fun Draw(forceFullScreen: Boolean) {
            var y = 0.0f
            if (charSetShader == null) {
                return
            }
            if (forceFullScreen) {
                // if we are forced full screen because of a disconnect,
                // we want the console closed when we go back to a session state
                Close()
                // we are however catching keyboard input
                keyCatching = true
            }
            Scroll()
            UpdateDisplayFraction()
            if (forceFullScreen) {
                DrawSolidConsole(1.0f)
            } else if (displayFrac != 0.0f) {
                DrawSolidConsole(displayFrac)
            } else {
                // only draw the notify lines if the developer cvar is set,
                // or we are a debug build
                if (!con_noPrint.GetBool()) {
                    DrawNotify()
                }
            }

//            if (com_showFPS.GetBool()) {
            y = SCR_DrawFPS(0.0f)
            //            }
            if (Common.com_showMemoryUsage.GetBool()) {
                y = SCR_DrawMemoryUsage(y)
            }
            if (Common.com_showAsyncStats.GetBool()) {
                y = SCR_DrawAsyncStats(y)
            }
            if (Common.com_showSoundDecoders.GetBool()) {
                y = SCR_DrawSoundDecoders(y)
            }
        }

        /*
         ================
         idConsoleLocal.Dump

         Save the console contents out to a file
         ================
         */
        @Throws(idException::class)
        fun Dump(fileName: String) {
            var l: Int
            var x: Int
            var i: Int
            var line: Int
            val f: idFile?
            val buffer = CharArray(LINE_WIDTH + 3)
            f = FileSystem_h.fileSystem.OpenFileWrite(fileName)
            if (null == f) {
                Common.common.Warning("couldn't open %s", fileName)
                return
            }

            // skip empty lines
            l = current - TOTAL_LINES + 1
            if (l < 0) {
                l = 0
            }
            while (l <= current) {
                line = l % TOTAL_LINES * LINE_WIDTH
                x = 0
                while (x < LINE_WIDTH) {
                    if (text[line + x] and 0xff > ' '.code) {
                        break
                    }
                    x++
                }
                if (x != LINE_WIDTH) {
                    break
                }
                l++
            }

            // write the remaining lines
            while (l <= current) {
                line = l % TOTAL_LINES * LINE_WIDTH
                i = 0
                while (i < LINE_WIDTH) {
                    buffer[i] = (text[line + i] and 0xff).toInt().toChar()
                    i++
                }
                x = LINE_WIDTH - 1
                while (x >= 0) {
                    if (buffer[x] <= ' ') {
                        buffer[x] = Char(0)
                    } else {
                        break
                    }
                    x--
                }
                buffer[x + 1] = '\r'
                buffer[x + 2] = '\n'
                buffer[x + 3] = Char(0)
                f.WriteString(buffer)
                l++
            }
            FileSystem_h.fileSystem.CloseFile(f)
        }

        //
        //
        fun Clear() {
            var i: Int
            i = 0
            while (i < CON_TEXTSIZE) {
                text[i] = (idStr.ColorIndex(Str.C_COLOR_CYAN) shl 8 or ' '.code).toShort()
                i++
            }
            Bottom() // go to end
        }

        /*
         ====================
         KeyDownEvent

         Handles history and console scrollback
         ====================
         */
        @Throws(idException::class)
        private fun KeyDownEvent(key: Int) {

            // Execute F key bindings
            if (key >= KeyInput.K_F1 && key <= KeyInput.K_F12) {
                idKeyInput.ExecKeyBinding(key)
                return
            }

            // ctrl-L clears screen
            if (key == 'l'.code && idKeyInput.IsDown(KeyInput.K_CTRL)) {
                Clear()
                return
            }

            // enter finishes the line
            if (key == KeyInput.K_ENTER || key == KeyInput.K_KP_ENTER) {
                val buffer = TempDump.ctos(consoleField.GetBuffer())
                Common.common.Printf("]%s\n", buffer)
                CmdSystem.cmdSystem.BufferCommandText(cmdExecution_t.CMD_EXEC_APPEND, buffer) // valid command
                CmdSystem.cmdSystem.BufferCommandText(cmdExecution_t.CMD_EXEC_APPEND, "\n")

                // copy line to history buffer
                historyEditLines[nextHistoryLine % COMMAND_HISTORY] = consoleField
                nextHistoryLine++
                historyLine = nextHistoryLine
                consoleField = idEditField()
                consoleField.SetWidthInChars(LINE_WIDTH)
                Session.session.UpdateScreen() // force an update, because the command
                // may take some time
                return
            }

            // command completion
            if (key == KeyInput.K_TAB) {
                consoleField.AutoComplete()
                return
            }

            // command history (ctrl-p ctrl-n for unix style)
            if (key == KeyInput.K_UPARROW
                || key == 'p'.code && idKeyInput.IsDown(KeyInput.K_CTRL)
            ) {
                if (nextHistoryLine - historyLine < COMMAND_HISTORY && historyLine > 0) {
                    historyLine--
                }
                consoleField = historyEditLines[historyLine % COMMAND_HISTORY]
                return
            }
            if (key == KeyInput.K_DOWNARROW
                || key == 'n'.code && idKeyInput.IsDown(KeyInput.K_CTRL)
            ) {
                if (historyLine == nextHistoryLine) {
                    return
                }
                historyLine++
                consoleField = historyEditLines[historyLine % COMMAND_HISTORY]
                return
            }

            // console scrolling
            if (key == KeyInput.K_PGUP) {
                PageUp()
                lastKeyEvent = EventLoop.eventLoop.Milliseconds()
                nextKeyEvent = CONSOLE_FIRSTREPEAT
                return
            }
            if (key == KeyInput.K_PGDN) {
                PageDown()
                lastKeyEvent = EventLoop.eventLoop.Milliseconds()
                nextKeyEvent = CONSOLE_FIRSTREPEAT
                return
            }
            if (key == KeyInput.K_MWHEELUP) {
                PageUp()
                return
            }
            if (key == KeyInput.K_MWHEELDOWN) {
                PageDown()
                return
            }

            // ctrl-home = top of console
            if (key == KeyInput.K_HOME && idKeyInput.IsDown(KeyInput.K_CTRL)) {
                Top()
                return
            }

            // ctrl-end = bottom of console
            if (key == KeyInput.K_END && idKeyInput.IsDown(KeyInput.K_CTRL)) {
                Bottom()
                return
            }

            // pass to the normal editline routine
            consoleField.KeyDownEvent(key)
        }

        //
        //
        private fun Linefeed() {
            var i: Int

            // mark time for transparent overlay
            if (current >= 0) {
                times[current % NUM_CON_TIMES] = Common.com_frameTime
            }
            x = 0
            if (display == current) {
                display++
            }
            current++
            i = 0
            while (i < LINE_WIDTH) {
                text[current % TOTAL_LINES * LINE_WIDTH + i] =
                    (idStr.ColorIndex(Str.C_COLOR_CYAN) shl 8 or ' '.code).toShort()
                i++
            }
        }

        private fun PageUp() {
            display -= 2
            if (current - display >= TOTAL_LINES) {
                display = current - TOTAL_LINES + 1
            }
        }

        private fun PageDown() {
            display += 2
            if (display > current) {
                display = current
            }
        }

        private fun Top() {
            display = 0
        }

        //
        private fun Bottom() {
            display = current
        }

        /*
         ================
         DrawInput

         Draw the editline after a ] prompt
         ================
         */
        private fun DrawInput() {
            val y: Int
            val autoCompleteLength: Int
            y = vislines - RenderSystem.SMALLCHAR_HEIGHT * 2
            if (consoleField.GetAutoCompleteLength() != 0) {
                autoCompleteLength = TempDump.strLen(consoleField.GetBuffer()) - consoleField.GetAutoCompleteLength()
                if (autoCompleteLength > 0) {
                    RenderSystem.renderSystem.SetColor4(.8f, .2f, .2f, .45f)
                    RenderSystem.renderSystem.DrawStretchPic(
                        (2 * RenderSystem.SMALLCHAR_WIDTH + consoleField.GetAutoCompleteLength() * RenderSystem.SMALLCHAR_WIDTH).toFloat(),
                        (
                                y + 2).toFloat(),
                        (autoCompleteLength * RenderSystem.SMALLCHAR_WIDTH).toFloat(),
                        (RenderSystem.SMALLCHAR_HEIGHT - 2).toFloat(),
                        0.0f,
                        0.0f,
                        0.0f,
                        0.0f,
                        whiteShader
                    )
                }
            }
            RenderSystem.renderSystem.SetColor(idStr.ColorForIndex(Str.C_COLOR_CYAN))
            RenderSystem.renderSystem.DrawSmallChar(
                1 * RenderSystem.SMALLCHAR_WIDTH,
                y,
                ']'.code,
                localConsole.charSetShader
            )
            consoleField.Draw(
                2 * RenderSystem.SMALLCHAR_WIDTH,
                y,
                RenderSystem.SCREEN_WIDTH - 3 * RenderSystem.SMALLCHAR_WIDTH,
                true,
                charSetShader
            )
        }

        private fun DrawNotify() {
            var x: Int
            var v: Int
            var text_p: Int
            var i: Int
            var time: Int
            var currentColor: Int
            drawNotifyTotal++
            if (con_noPrint.GetBool()) {
                return
            }
            currentColor = idStr.ColorIndex(Str.C_COLOR_WHITE)
            RenderSystem.renderSystem.SetColor(idStr.ColorForIndex(currentColor))
            v = 0
            i = current - NUM_CON_TIMES + 1
            while (i <= current) {
                if (i < 0) {
                    i++
                    continue
                }
                time = times[i % NUM_CON_TIMES]
                if (time == 0) {
                    i++
                    continue
                }
                time = Common.com_frameTime - time
                if (time > con_notifyTime.GetFloat() * 1000) {
                    i++
                    continue
                }
                text_p = i % TOTAL_LINES * LINE_WIDTH
                //		text_p = text + (i % TOTAL_LINES)*LINE_WIDTH;
                x = 0
                while (x < LINE_WIDTH) {
                    if (text[text_p + x].toInt() and 0xff == ' '.code) {
                        x++
                        continue
                    }
                    if (idStr.ColorIndex(text[text_p + x].toInt() shr 8) != currentColor) {
                        currentColor = idStr.ColorIndex(text[text_p + x].toInt() shr 8)
                        RenderSystem.renderSystem.SetColor(idStr.ColorForIndex(currentColor))
                    }
                    RenderSystem.renderSystem.DrawSmallChar(
                        (x + 1) * RenderSystem.SMALLCHAR_WIDTH,
                        v,
                        text[text_p + x].toInt() and 0xff,
                        localConsole.charSetShader
                    )
                    x++
                }
                v += RenderSystem.SMALLCHAR_HEIGHT
                i++
            }
            RenderSystem.renderSystem.SetColor(colorCyan)
        }

        /*
         ================
         DrawSolidConsole

         Draws the console with the solid background
         ================
         */
        private fun DrawSolidConsole(frac: Float) {
            var i: Int
            var x: Int
            var y: Float
            var rows: Int
            var text_p: Int
            var row: Int
            var lines: Int
            var currentColor: Int
            lines = idMath.FtoiFast(RenderSystem.SCREEN_HEIGHT * frac)
            if (lines <= 0) {
                return
            }
            if (lines > RenderSystem.SCREEN_HEIGHT) {
                lines = RenderSystem.SCREEN_HEIGHT
            }

            // draw the background
            y = frac * RenderSystem.SCREEN_HEIGHT - 2
            if (y < 1.0f) {
                y = 0.0f
            } else {
                RenderSystem.renderSystem.DrawStretchPic(
                    0.0f,
                    0.0f,
                    RenderSystem.SCREEN_WIDTH.toFloat(),
                    y,
                    0.0f,
                    1.0f - displayFrac,
                    1.0f,
                    1.0f,
                    consoleShader
                )
            }
            RenderSystem.renderSystem.SetColor(colorCyan)
            RenderSystem.renderSystem.DrawStretchPic(
                0.0f,
                y,
                RenderSystem.SCREEN_WIDTH.toFloat(),
                2.0f,
                0.0f,
                0.0f,
                0.0f,
                0.0f,
                whiteShader
            )
            RenderSystem.renderSystem.SetColor(colorWhite)

            // draw the version number
            RenderSystem.renderSystem.SetColor(idStr.ColorForIndex(Str.C_COLOR_CYAN))
            val version = Str.va("%s.%d", Licensee.ENGINE_VERSION, BUILD_NUMBER).toCharArray()
            i = version.size
            x = 0
            while (x < i) {
                RenderSystem.renderSystem.DrawSmallChar(
                    RenderSystem.SCREEN_WIDTH - (i - x) * RenderSystem.SMALLCHAR_WIDTH,
                    lines - (RenderSystem.SMALLCHAR_HEIGHT + RenderSystem.SMALLCHAR_HEIGHT / 2),
                    version[x].code,
                    localConsole.charSetShader
                )
                x++
            }

            // draw the text
            vislines = lines
            rows = (lines - RenderSystem.SMALLCHAR_WIDTH) / RenderSystem.SMALLCHAR_WIDTH // rows of text to draw
            y = (lines - RenderSystem.SMALLCHAR_HEIGHT * 3).toFloat()

            // draw from the bottom up
            if (display != current) {
                // draw arrows to show the buffer is backscrolled
                RenderSystem.renderSystem.SetColor(idStr.ColorForIndex(Str.C_COLOR_CYAN))
                x = 0
                while (x < LINE_WIDTH) {
                    RenderSystem.renderSystem.DrawSmallChar(
                        (x + 1) * RenderSystem.SMALLCHAR_WIDTH,
                        idMath.FtoiFast(y),
                        '^'.code,
                        localConsole.charSetShader
                    )
                    x += 4
                }
                y -= RenderSystem.SMALLCHAR_HEIGHT.toFloat()
                rows--
            }
            row = display
            if (x == 0) {
                row--
            }
            currentColor = idStr.ColorIndex(Str.C_COLOR_WHITE)
            RenderSystem.renderSystem.SetColor(idStr.ColorForIndex(currentColor))
            i = 0
            while (i < rows) {
                if (row < 0) {
                    break
                }
                if (current - row >= TOTAL_LINES) {
                    // past scrollback wrap point
                    i++
                    y -= RenderSystem.SMALLCHAR_HEIGHT.toFloat()
                    row--
                    continue
                }
                text_p = row % TOTAL_LINES * LINE_WIDTH
                x = 0
                while (x < LINE_WIDTH) {
                    if (text[text_p + x].toInt() and 0xff == ' '.code) {
                        x++
                        continue
                    }
                    if (idStr.ColorIndex(text[text_p + x].toInt() shr 8) != currentColor) {
                        currentColor = idStr.ColorIndex(text[text_p + x].toInt() shr 8)
                        RenderSystem.renderSystem.SetColor(idStr.ColorForIndex(currentColor))
                    }
                    RenderSystem.renderSystem.DrawSmallChar(
                        (x + 1) * RenderSystem.SMALLCHAR_WIDTH,
                        idMath.FtoiFast(y),
                        text[text_p + x].toInt() and 0xff,
                        localConsole.charSetShader
                    )
                    x++
                }
                i++
                y -= RenderSystem.SMALLCHAR_HEIGHT.toFloat()
                row--
            }

            // draw the input prompt, user text, and cursor if desired
            DrawInput()
            RenderSystem.renderSystem.SetColor(colorCyan)
        }

        //
        /*
         ==============
         Scroll
         deals with scrolling text because we don't have key repeat
         ==============
         */
        private fun Scroll() {
            if (lastKeyEvent == -1 || lastKeyEvent + 200 > EventLoop.eventLoop.Milliseconds()) {
                return
            }
            // console scrolling
            if (idKeyInput.IsDown(KeyInput.K_PGUP)) {
                PageUp()
                nextKeyEvent = CONSOLE_REPEAT
                return
            }
            if (idKeyInput.IsDown(KeyInput.K_PGDN)) {
                PageDown()
                nextKeyEvent = CONSOLE_REPEAT
                //                return;
            }
        }

        /*
         ==============
         SetDisplayFraction

         Causes the console to start opening the desired amount.
         ==============
         */
        private fun SetDisplayFraction(frac: Float) {
            finalFrac = frac
            fracTime = Common.com_frameTime
        }

        /*
         ==============
         UpdateDisplayFraction

         Scrolls the console up or down based on conspeed
         ==============
         */
        private fun UpdateDisplayFraction() {
            if (con_speed.GetFloat() <= 0.1f) {
                fracTime = Common.com_frameTime
                displayFrac = finalFrac
                return
            }

            // scroll towards the destination height
            if (finalFrac < displayFrac) {
                displayFrac -= con_speed.GetFloat() * (Common.com_frameTime - fracTime) * 0.001f
                if (finalFrac > displayFrac) {
                    displayFrac = finalFrac
                }
                fracTime = Common.com_frameTime
            } else if (finalFrac > displayFrac) {
                displayFrac += con_speed.GetFloat() * (Common.com_frameTime - fracTime) * 0.001f
                if (finalFrac < displayFrac) {
                    displayFrac = finalFrac
                }
                fracTime = Common.com_frameTime
            }
        }
    }

    //=========================================================================
    /*
     ==============
     Con_Clear_f
     ==============
     */
    private class Con_Clear_f private constructor() : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            localConsole.Clear()
        }

        companion object {
            private val instance: cmdFunction_t = Con_Clear_f()
            fun getInstance(): cmdFunction_t {
                return instance
            }
        }
    }

    /*
     ==============
     Con_Dump_f
     ==============
     */
    private class Con_Dump_f private constructor() : cmdFunction_t() {
        @Throws(idException::class)
        override fun run(args: CmdArgs.idCmdArgs?) {
            if (args!!.Argc() != 2) {
                Common.common.Printf("usage: conDump <filename>\n")
                return
            }
            val fileName = idStr(args.Argv(1)).DefaultFileExtension(".txt").toString()
            Common.common.Printf("Dumped console text to %s.\n", fileName)
            localConsole.Dump(fileName)
        }

        companion object {
            private val instance: cmdFunction_t = Con_Dump_f()
            fun getInstance(): cmdFunction_t {
                return instance
            }
        }
    }

    companion object {
        const val CONSOLE_FIRSTREPEAT = 200
        const val CONSOLE_REPEAT = 100
        const val CON_TEXTSIZE = 0x30000

        //
        const val COMMAND_HISTORY = 64

        /**
         *
         */
        const val LINE_WIDTH = 78

        /*
                 ==================
                 SCR_DrawFPS
                 ==================
                 */
        const val FPS_FRAMES = 4
        const val TOTAL_LINES = CON_TEXTSIZE / LINE_WIDTH
        const val NUM_CON_TIMES = 4
        val localConsole: idConsoleLocal = idConsoleLocal()


        val console: idConsole = localConsole // statically initialized to an idConsoleLocal

        //
        var index = 0

        /*
         =============================================================================

         Misc stats

         =============================================================================
         */
        var previous = 0
        var previousTimes: IntArray = IntArray(FPS_FRAMES)

        /*
     ==================
     SCR_DrawTextLeftAlign
     ==================
     */
        fun SCR_DrawTextLeftAlign(y: FloatArray, fmt: String, vararg text: Any) {
            val string = arrayOf<String>("") //new char[MAX_STRING_CHARS];
            //	va_list argptr;
//	va_start( argptr, text );
            idStr.vsnPrintf(string, MAX_STRING_CHARS, fmt, *text)
            //	va_end( argptr );
            RenderSystem.renderSystem.DrawSmallStringExt(
                0,
                (y[0] + 2).toInt(),
                string[0].toCharArray(),
                colorWhite,
                true,
                localConsole.charSetShader
            )
            y[0] = y[0] + RenderSystem.SMALLCHAR_HEIGHT + 4
        }

        /*
         ==================
         SCR_DrawTextRightAlign
         ==================
         */
        fun SCR_DrawTextRightAlign(y: FloatArray, fmt: String, vararg text: Any) {
            val string = arrayOf<String>("") //new char[MAX_STRING_CHARS];
            //	va_list argptr;
//	va_start( argptr, text );
            val i: Int = idStr.vsnPrintf(string, MAX_STRING_CHARS, fmt, *text)
            //	va_end( argptr );
            RenderSystem.renderSystem.DrawSmallStringExt(
                635 - i * RenderSystem.SMALLCHAR_WIDTH,
                (y[0] + 2).toInt(),
                string[0].toCharArray(),
                colorWhite,
                true,
                localConsole.charSetShader
            )
            y[0] = y[0] + RenderSystem.SMALLCHAR_HEIGHT + 4
        }

        fun SCR_DrawFPS(y: Float): Float {
            val s: String
            val w: Int
            var i: Int
            var total: Int
            var fps: Int
            val t: Int
            val frameTime: Int

            // don't use serverTime, because that will be drifting to
            // correct for internet lag changes, timescales, timedemos, etc
            t = win_shared.Sys_Milliseconds()
            frameTime = t - previous
            previous = t
            previousTimes[index % FPS_FRAMES] = frameTime
            index++
            if (index > FPS_FRAMES) {
                // average multiple frames together to smooth changes out a bit
                total = 0
                i = 0
                while (i < FPS_FRAMES) {
                    total += previousTimes[i]
                    i++
                }
                if (0 == total) {
                    total = 1
                }
                fps = 10000 * FPS_FRAMES / total
                fps = (fps + 5) / 10
                s = Str.va("%dfps", fps)
                w = s.length * RenderSystem.BIGCHAR_WIDTH
                RenderSystem.renderSystem.DrawBigStringExt(
                    635 - w,
                    idMath.FtoiFast(y) + 2,
                    s,
                    colorWhite,
                    true,
                    localConsole.charSetShader
                )
            }
            return y + RenderSystem.BIGCHAR_HEIGHT + 4
        }

        /*
         ==================
         SCR_DrawMemoryUsage
         ==================
         */
        fun SCR_DrawMemoryUsage(y: Float): Float {
            //memoryStats_t[] allocs = new memoryStats_t[1], frees = new memoryStats_t[1];
            val yy = floatArrayOf(y)

//        Mem_GetStats(allocs);
//        SCR_DrawTextRightAlign(yy, "total allocated memory: %4d, %4dkB", allocs[0].num, allocs[0].totalSize >> 10);
//
//        Mem_GetFrameStats(allocs, frees);
//        SCR_DrawTextRightAlign(yy, "frame alloc: %4d, %4dkB  frame free: %4d, %4dkB", allocs[0].num, allocs[0].totalSize >> 10, frees[0].num, frees[0].totalSize >> 10);
//
//        Mem_ClearFrameStats();
            return yy[0]
        }

        /*
         ==================
         SCR_DrawAsyncStats
         ==================
         */
        fun SCR_DrawAsyncStats(y: Float): Float {
            var i: Int
            var outgoingRate: Int
            var incomingRate: Int
            var outgoingCompression: Float
            var incomingCompression: Float
            val yy = floatArrayOf(y)
            if (idAsyncNetwork.server.IsActive()) {
                SCR_DrawTextRightAlign(yy, "server delay = %d msec", idAsyncNetwork.server.GetDelay())
                SCR_DrawTextRightAlign(
                    yy,
                    "total outgoing rate = %d KB/s",
                    idAsyncNetwork.server.GetOutgoingRate() shr 10
                )
                SCR_DrawTextRightAlign(
                    yy,
                    "total incoming rate = %d KB/s",
                    idAsyncNetwork.server.GetIncomingRate() shr 10
                )
                i = 0
                while (i < AsyncNetwork.MAX_ASYNC_CLIENTS) {
                    outgoingRate = idAsyncNetwork.server.GetClientOutgoingRate(i)
                    incomingRate = idAsyncNetwork.server.GetClientIncomingRate(i)
                    outgoingCompression = idAsyncNetwork.server.GetClientOutgoingCompression(i)
                    incomingCompression = idAsyncNetwork.server.GetClientIncomingCompression(i)
                    if (outgoingRate != -1 && incomingRate != -1) {
                        SCR_DrawTextRightAlign(
                            yy,
                            "client %d: out rate = %d B/s (% -2.1f%%), in rate = %d B/s (% -2.1f%%)",
                            i,
                            outgoingRate,
                            outgoingCompression,
                            incomingRate,
                            incomingCompression
                        )
                    }
                    i++
                }
                val msg = idStr()
                idAsyncNetwork.server.GetAsyncStatsAvgMsg(msg)
                SCR_DrawTextRightAlign(yy, msg.toString())
            } else if (idAsyncNetwork.client.IsActive()) {
                outgoingRate = idAsyncNetwork.client.GetOutgoingRate()
                incomingRate = idAsyncNetwork.client.GetIncomingRate()
                outgoingCompression = idAsyncNetwork.client.GetOutgoingCompression()
                incomingCompression = idAsyncNetwork.client.GetIncomingCompression()
                if (outgoingRate != -1 && incomingRate != -1) {
                    SCR_DrawTextRightAlign(
                        yy,
                        "out rate = %d B/s (% -2.1f%%), in rate = %d B/s (% -2.1f%%)",
                        outgoingRate,
                        outgoingCompression,
                        incomingRate,
                        incomingCompression
                    )
                }
                SCR_DrawTextRightAlign(
                    yy,
                    "packet loss = %d%%, client prediction = %d",
                    idAsyncNetwork.client.GetIncomingPacketLoss().toInt(),
                    idAsyncNetwork.client.GetPrediction()
                )
                SCR_DrawTextRightAlign(yy, "predicted frames: %d", idAsyncNetwork.client.GetPredictedFrames())
            }
            return yy[0]
        }

        /*
         ==================
         SCR_DrawSoundDecoders
         ==================
         */
        fun SCR_DrawSoundDecoders(y: Float): Float {
            var index: Int
            var numActiveDecoders: Int
            val decoderInfo = soundDecoderInfo_t()
            val yy = floatArrayOf(y)
            index = -1
            numActiveDecoders = 0
            while (snd_system.soundSystem.GetSoundDecoderInfo(index, decoderInfo).also { index = it } != -1) {
                val localTime = decoderInfo.current44kHzTime - decoderInfo.start44kHzTime
                val sampleTime = decoderInfo.num44kHzSamples / decoderInfo.numChannels
                var percent: Int
                percent = if (localTime > sampleTime) {
                    if (decoderInfo.looping) {
                        localTime % sampleTime * 100 / sampleTime
                    } else {
                        100
                    }
                } else {
                    localTime * 100 / sampleTime
                }
                SCR_DrawTextLeftAlign(
                    yy,
                    "%3d: %3d%% (%1.2f) %s: %s (%dkB)",
                    numActiveDecoders,
                    percent,
                    decoderInfo.lastVolume,
                    decoderInfo.format.toString(),
                    decoderInfo.name.toString(),
                    decoderInfo.numBytes shr 10
                )
                numActiveDecoders++
            }
            return yy[0]
        }

    }
}
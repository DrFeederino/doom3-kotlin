package neo.ui

import neo.Renderer.Material
import neo.framework.CVarSystem.cvarSystem
import neo.framework.CVarSystem.idCVar
import neo.framework.Common.Companion.common
import neo.framework.DeclManager
import neo.framework.FileSystem_h.fileSystem
import neo.framework.KeyInput.K_BACKSPACE
import neo.framework.KeyInput.K_CTRL
import neo.framework.KeyInput.K_DEL
import neo.framework.KeyInput.K_DOWNARROW
import neo.framework.KeyInput.K_END
import neo.framework.KeyInput.K_ENTER
import neo.framework.KeyInput.K_ESCAPE
import neo.framework.KeyInput.K_HOME
import neo.framework.KeyInput.K_INS
import neo.framework.KeyInput.K_KP_ENTER
import neo.framework.KeyInput.K_LEFTARROW
import neo.framework.KeyInput.K_RIGHTARROW
import neo.framework.KeyInput.K_SHIFT
import neo.framework.KeyInput.K_UPARROW
import neo.framework.KeyInput.idKeyInput.IsDown
import neo.idlib.Text.Parser.idParser
import neo.idlib.Text.Str.idStr
import neo.idlib.Text.Str.idStr.Companion.CharIsPrintable
import neo.idlib.Text.Str.idStr.Companion.Cmpn
import neo.idlib.Text.Str.idStr.Companion.Copynz
import neo.idlib.Text.Str.idStr.Companion.Icmp
import neo.idlib.Text.Str.idStr.Companion.IsColor
import neo.idlib.Text.ctos
import neo.idlib.colorWhite
import neo.idlib.containers.CBool
import neo.idlib.containers.List.idList
import neo.idlib.math.idMath.FtoiFast
import neo.idlib.toBoolean
import neo.sys.sysEventType_t
import neo.sys.sysEvent_s
import neo.sys.win_input.Sys_GetConsoleKey
import neo.ui.DeviceContext.idDeviceContext
import neo.ui.Rectangle.idRectangle
import neo.ui.SimpleWindow.drawWin_t
import neo.ui.SliderWindow.idSliderWindow
import neo.ui.UserInterfaceLocal.idUserInterfaceLocal
import neo.ui.Window.idWindow
import neo.ui.Winvar.idWinBool
import neo.ui.Winvar.idWinVar
import java.nio.ByteBuffer

object EditWindow {
    const val MAX_EDITFIELD = 4096

    internal class idEditWindow : idWindow {
        private val breaks = idList<Int>()
        private var cursorLine = 0
        private var cursorPos = 0
        private var cvar: idCVar? = null
        private val cvarGroup = Winvar.idWinStr()
        private var cvarMax = 0

        //
        private val cvarStr = Winvar.idWinStr()
        private var forceScroll = false
        private var lastTextLength = 0

        //
        private val liveUpdate = idWinBool()
        private var maxChars = 0
        private var numeric = false
        private var paintOffset = 0
        private val password: idWinBool = idWinBool()
        private var readonly = false
        private var scroller: idSliderWindow? = null
        private var sizeBias = 0.0f
        private val sourceFile = idStr()
        private val textIndex = 0

        //
        //
        private var wrap = false

        constructor(gui: idUserInterfaceLocal) : super(gui) {
            this.gui = gui
            CommonInit()
        }

        //	// virtual 			~idEditWindow();
        //
        constructor(dc: idDeviceContext?, gui: idUserInterfaceLocal?) : super(dc, gui) {
            this.dc = dc
            this.gui = gui
            CommonInit()
        }

        override fun Draw(time: Int, x: Float, y: Float) {
            var color = foreColor.oCastIdVec4()
            UpdateCvar(true)
            val len = text.Length()
            if (len != lastTextLength) {
                scroller!!.SetValue(0.0f)
                EnsureCursorVisible()
                lastTextLength = len
            }
            val scale = textScale.oCastFloat()
            var pass = ""
            val buffer: String
            if (password.data) {
                var temp = 0 //text;
                while (temp < text.Length()) {
                    pass += "*"
                    temp++
                }
                buffer = pass
            } else {
                buffer = text.c_str()!!
            }
            if (cursorPos > len) {
                cursorPos = len
            }
            val rect = idRectangle(textRect)
            rect.x -= paintOffset
            rect.w += paintOffset
            if (wrap && scroller!!.GetHigh() > 0.0f) {
                val lineHeight = GetMaxCharHeight() + 5
                rect.y -= scroller!!.GetValue() * lineHeight
                rect.w -= sizeBias
                rect.h = (breaks.Num() + 1) * lineHeight
            }
            if (hover && !noEvents.data && Contains(gui!!.CursorX(), gui!!.CursorY())) {
                color = hoverColor.oCastIdVec4()
            } else {
                hover = false
            }
            if (flags and Window.WIN_FOCUS != 0) {
                color = hoverColor.oCastIdVec4()
            }
            dc!!.DrawText(
                buffer,
                scale,
                0,
                color,
                rect,
                wrap,
                if ((flags and Window.WIN_FOCUS).toBoolean()) cursorPos else -1
            )
        }

        override fun HandleEvent(event: sysEvent_s, updateVisuals: CBool?): String? {
            var ret: String? = ""
            if (wrap) {
                // need to call this to allow proper focus and capturing on embedded children
                ret = super.HandleEvent(event, updateVisuals)
                if (ret != null && !ret.isEmpty()) {
                    return ret
                }
            }
            if (event.evType !== sysEventType_t.SE_CHAR && event.evType !== sysEventType_t.SE_KEY) {
                return ret
            }
            Copynz(buffer, text.c_str()!!, buffer.size)
            val key = event.evValue
            var len = text.Length()
            if (event.evType == sysEventType_t.SE_CHAR) {
                if (event.evValue == Sys_GetConsoleKey(IsDown(K_SHIFT)).code) {
                    return ""
                }
                if (updateVisuals != null) {
                    updateVisuals._val = true
                }
                if (maxChars != 0 && len > maxChars) {
                    len = maxChars
                }
                if ((key == K_ENTER || key == K_KP_ENTER) && event.evValue2 != 0) {
                    RunScript((ON.ON_ACTION).ordinal)
                    RunScript((ON.ON_ENTER).ordinal)
                    return cmd.toString()
                }
                if (key == K_ESCAPE) {
                    RunScript((ON.ON_ESC).ordinal)
                    return cmd.toString()
                }
                if (readonly) {
                    return ""
                }
                if (key == 'h'.code - 'a'.code + 1 || key == K_BACKSPACE) {    // ctrl-h is backspace
                    if (cursorPos > 0) {
                        if (cursorPos >= len) {
                            buffer[len - 1] = 0.toChar()
                            cursorPos = len - 1
                        } else {
//					memmove( &buffer[ cursorPos - 1 ], &buffer[ cursorPos ], len + 1 - cursorPos);
                            System.arraycopy(buffer, cursorPos, buffer, cursorPos - 1, len + 1 - cursorPos)
                            cursorPos--
                        }
                        text.Set(String(buffer).substringBefore('\u0000'))
                        UpdateCvar(false)
                        RunScript((ON.ON_ACTION).ordinal)
                    }
                    return ""
                }

                //
                // ignore any non printable chars (except enter when wrap is enabled)
                //
                if (wrap && (key == K_ENTER || key == K_KP_ENTER)) {
                } else if (!CharIsPrintable(key)) {
                    return ""
                }
                if (numeric) {
                    if ((key < '0'.code || key > '9'.code) && key != '.'.code) {
                        return ""
                    }
                }
                if (dc!!.GetOverStrike()) {
                    if (maxChars != 0 && cursorPos >= maxChars) {
                        return ""
                    }
                } else {
                    if (len == MAX_EDITFIELD - 1 || maxChars != 0 && len >= maxChars) {
                        return ""
                    }
                    //			memmove( &buffer[ cursorPos + 1 ], &buffer[ cursorPos ], len + 1 - cursorPos );
                    System.arraycopy(buffer, cursorPos, buffer, cursorPos + 1, len + 1 - cursorPos)
                }
                buffer[cursorPos] = key.toChar()
                text.Set(String(buffer).substringBefore('\u0000'))
                UpdateCvar(false)
                RunScript((ON.ON_ACTION).ordinal)
                if (cursorPos < len + 1) {
                    cursorPos++
                }
                EnsureCursorVisible()
            } else if (event.evType == sysEventType_t.SE_KEY && event.evValue2 != 0) {
                if (updateVisuals != null) {
                    updateVisuals._val = true
                }
                if (key == K_DEL) {
                    if (readonly) {
                        return ret
                    }
                    if (cursorPos < len) {
//				memmove( &buffer[cursorPos], &buffer[cursorPos + 1], len - cursorPos);
                        System.arraycopy(buffer, cursorPos + 1, buffer, cursorPos, len - cursorPos)
                        text.Set(String(buffer).substringBefore('\u0000'))
                        UpdateCvar(false)
                        RunScript((ON.ON_ACTION).ordinal)
                    }
                    return ret
                }
                if (key == K_RIGHTARROW) {
                    if (cursorPos < len) {
                        if (IsDown(K_CTRL)) {
                            // skip to next word
                            while (cursorPos < len && buffer[cursorPos] != ' ') {
                                cursorPos++
                            }
                            while (cursorPos < len && buffer[cursorPos] == ' ') {
                                cursorPos++
                            }
                        } else {
                            if (cursorPos < len) {
                                cursorPos++
                            }
                        }
                    }
                    EnsureCursorVisible()
                    return ret
                }
                if (key == K_LEFTARROW) {
                    if (IsDown(K_CTRL)) {
                        // skip to previous word
                        while (cursorPos > 0 && buffer[cursorPos - 1] == ' ') {
                            cursorPos--
                        }
                        while (cursorPos > 0 && buffer[cursorPos - 1] != ' ') {
                            cursorPos--
                        }
                    } else {
                        if (cursorPos > 0) {
                            cursorPos--
                        }
                    }
                    EnsureCursorVisible()
                    return ret
                }
                if (key == K_HOME) {
                    cursorPos = if (IsDown(K_CTRL) || cursorLine <= 0 || cursorLine >= breaks.Num()) {
                        0
                    } else {
                        breaks[cursorLine]
                    }
                    EnsureCursorVisible()
                    return ret
                }
                if (key == K_END) {
                    cursorPos = if (IsDown(K_CTRL) || cursorLine < -1 || cursorLine >= breaks.Num() - 1) {
                        len
                    } else {
                        breaks[cursorLine + 1] - 1
                    }
                    EnsureCursorVisible()
                    return ret
                }
                if (key == K_INS) {
                    if (!readonly) {
                        dc!!.SetOverStrike(!dc!!.GetOverStrike())
                    }
                    return ret
                }
                if (key == K_DOWNARROW) {
                    if (IsDown(K_CTRL)) {
                        scroller!!.SetValue(scroller!!.GetValue() + 1.0f)
                    } else {
                        if (cursorLine < breaks.Num() - 1) {
                            val offset = cursorPos - breaks[cursorLine]
                            cursorPos = breaks[cursorLine + 1] + offset
                            EnsureCursorVisible()
                        }
                    }
                }
                if (key == K_UPARROW) {
                    if (IsDown(K_CTRL)) {
                        scroller!!.SetValue(scroller!!.GetValue() - 1.0f)
                    } else {
                        if (cursorLine > 0) {
                            val offset = cursorPos - breaks[cursorLine]
                            cursorPos = breaks[cursorLine - 1] + offset
                            EnsureCursorVisible()
                        }
                    }
                }
                if (key == K_ENTER || key == K_KP_ENTER) {
                    RunScript((ON.ON_ACTION).ordinal)
                    RunScript((ON.ON_ENTER).ordinal)
                    return cmd.toString()
                }
                if (key == K_ESCAPE) {
                    RunScript((ON.ON_ESC).ordinal)
                    return cmd.toString()
                }
            } else if (event.evType == sysEventType_t.SE_KEY && 0 == event.evValue2) {
                if (key == K_ENTER || key == K_KP_ENTER) {
                    RunScript((ON.ON_ENTERRELEASE).ordinal)
                    return cmd.toString()
                } else {
                    RunScript((ON.ON_ACTIONRELEASE).ordinal)
                }
            }
            return ret
        }

        override fun PostParse() {
            super.PostParse()
            if (maxChars == 0) {
                maxChars = 10
            }
            if (sourceFile.Length() != 0) {
                val buffer = arrayOf<ByteBuffer?>(null)
                fileSystem.ReadFile(sourceFile, buffer)
                text.data!!.set(String(buffer[0]!!.array()))
                fileSystem.FreeFile(buffer)
            }
            InitCvar()
            InitScroller(false)
            EnsureCursorVisible()
            flags = flags or Window.WIN_CANFOCUS
        }

        override fun GainFocus() {
            cursorPos = text.Length()
            EnsureCursorVisible()
        }

        override fun GetWinVarByName(
            _name: String?,
            winLookup: Boolean /*= false*/,
            owner: Array<drawWin_t?>? /*= NULL*/
        ): idWinVar? {
            if (Icmp(_name!!, "cvar") == 0) {
                return cvarStr
            }
            if (Icmp(_name, "password") == 0) {
                return password
            }
            if (Icmp(_name, "liveUpdate") == 0) {
                return liveUpdate
            }
            return if (Icmp(_name, "cvarGroup") == 0) {
                cvarGroup
            } else super.GetWinVarByName(_name, winLookup, owner)
        }

        override fun HandleBuddyUpdate(buddy: idWindow?) {}
        override fun Activate(activate: Boolean, act: idStr) {
            super.Activate(activate, act)
            if (activate) {
                UpdateCvar(true, true)
                EnsureCursorVisible()
            }
        }

        override fun RunNamedEvent(eventName: String?) {
            val event: idStr
            val group: idStr
            if (0 == Cmpn(eventName!!, "cvar read ", 10)) {
                event = idStr(eventName)
                group = event.Mid(10, event.Length() - 10)
                if (group.Cmp(cvarGroup.data!!) == 0) {
                    UpdateCvar(true, true)
                }
            } else if (0 == Cmpn(eventName, "cvar write ", 11)) {
                event = idStr(eventName)
                group = event.Mid(11, event.Length() - 11)
                if (group.Cmp(cvarGroup.data!!) == 0) {
                    UpdateCvar(false, true)
                }
            }
        }

        override fun ParseInternalVar(_name: String?, src: idParser): Boolean {
            if (Icmp(_name!!, "maxchars") == 0) {
                maxChars = src.ParseInt()
                return true
            }
            if (Icmp(_name, "numeric") == 0) {
                numeric = src.ParseBool()
                return true
            }
            if (Icmp(_name, "wrap") == 0) {
                wrap = src.ParseBool()
                return true
            }
            if (Icmp(_name, "readonly") == 0) {
                readonly = src.ParseBool()
                return true
            }
            if (Icmp(_name, "forceScroll") == 0) {
                forceScroll = src.ParseBool()
                return true
            }
            if (Icmp(_name, "source") == 0) {
                ParseString(src, sourceFile)
                return true
            }
            if (Icmp(_name, "password") == 0) {
                password.data = src.ParseBool()
                return true
            }
            if (Icmp(_name, "cvarMax") == 0) {
                cvarMax = src.ParseInt()
                return true
            }
            return super.ParseInternalVar(_name, src)
        }

        private fun InitCvar() {
            if (cvarStr.data == null || cvarStr.data!!.IsEmpty()) {
                if (text.GetName().isEmpty()) {
                    common.Warning(
                        "idEditWindow::InitCvar: gui '%s' window '%s' has an empty cvar string",
                        gui!!.GetSourceFile(),
                        name!!
                    )
                }
                cvar = null
                return
            }
            cvar = cvarSystem.Find(cvarStr.data.toString())
            if (null == cvar) {
                common.Warning(
                    "idEditWindow::InitCvar: gui '%s' window '%s' references undefined cvar '%s'",
                    gui!!.GetSourceFile(),
                    name!!,
                    cvarStr.c_str()!!
                )
                return
            }
        }

        // true: read the updated cvar from cvar system
        // false: write to the cvar system
        // force == true overrides liveUpdate 0
        private fun UpdateCvar(read: Boolean, force: Boolean = false /*= false*/) {
            if (force || liveUpdate.data) {
                if (cvar != null) {
                    if (read) {
                        text.data!!.set(cvar!!.GetString())
                    } else {
                        cvar!!.SetString(text.data.toString())
                        if (cvarMax != 0 && cvar!!.GetInteger() > cvarMax) {
                            cvar!!.SetInteger(cvarMax)
                        }
                    }
                }
            }
        }

        private fun CommonInit() {
            maxChars = 128
            numeric = false
            paintOffset = 0
            cursorPos = 0
            cursorLine = 0
            cvarMax = 0
            wrap = false
            sourceFile.set("")
            scroller = null
            sizeBias = 0.0f
            lastTextLength = 0
            forceScroll = false
            password.data = false
            cvar = null
            liveUpdate.data = true
            readonly = false
            scroller = idSliderWindow(dc, gui)
        }

        private fun EnsureCursorVisible() {
            if (readonly) {
                cursorPos = -1
            } else if (maxChars == 1) {
                cursorPos = 0
            }
            if (dc == null) {
                return
            }
            SetFont()
            if (!wrap) {
                var cursorX = 0
                if (password.data) {
                    cursorX = cursorPos * dc!!.CharWidth('*', textScale.data)
                } else {
                    var i = 0
                    while (i < text.Length() && i < cursorPos) {
                        if (IsColor(ctos(text.data!![i]))) {
                            i += 2
                        } else {
                            cursorX += dc!!.CharWidth(text.data!![i], textScale.data)
                            i++
                        }
                    }
                }
                val maxWidth = GetMaxCharWidth().toInt()
                val left = cursorX - maxWidth
                val right = (cursorX - textRect.w + maxWidth).toInt()
                if (paintOffset > left) {
                    // When we go past the left side, we want the text to jump 6 characters
                    paintOffset = left - maxWidth * 6
                }
                if (paintOffset < right) {
                    paintOffset = right
                }
                if (paintOffset < 0) {
                    paintOffset = 0
                }
                scroller!!.SetRange(0.0f, 0.0f, 1.0f)
            } else {
                // Word wrap
                breaks.Clear()
                val rect = idRectangle(textRect)
                rect.w -= sizeBias
                dc!!.DrawText(
                    text.data,
                    textScale.data,
                    textAlign.code,
                    colorWhite,
                    rect,
                    true,
                    if ((flags and Window.WIN_FOCUS).toBoolean()) cursorPos else -1,
                    true,
                    breaks
                )
                val fit = (textRect.h / (GetMaxCharHeight() + 5)).toInt()
                if (fit < breaks.Num() + 1) {
                    scroller!!.SetRange(0.0f, (breaks.Num() + 1 - fit).toFloat(), 1.0f)
                } else {
                    // The text fits completely in the box
                    scroller!!.SetRange(0.0f, 0.0f, 1.0f)
                }
                if (forceScroll) {
                    scroller!!.SetValue((breaks.Num() - fit).toFloat())
                } else if (readonly) {
                } else {
                    cursorLine = 0
                    for (i in 1 until breaks.Num()) {
                        cursorLine = if (cursorPos >= breaks[i]) {
                            i
                        } else {
                            break
                        }
                    }
                    val topLine = FtoiFast(scroller!!.GetValue())
                    if (cursorLine < topLine) {
                        scroller!!.SetValue(cursorLine.toFloat())
                    } else if (cursorLine >= topLine + fit) {
                        scroller!!.SetValue((cursorLine - fit + 1).toFloat())
                    }
                }
            }
        }

        /*
         ================
         idEditWindow::InitScroller

         This is the same as in idListWindow
         ================
         */
        private fun InitScroller(horizontal: Boolean) {
            val thumbImage = "guis/assets/scrollbar_thumb.tga"
            var barImage = "guis/assets/scrollbarv.tga"
            var scrollerName = "_scrollerWinV"
            if (horizontal) {
                barImage = "guis/assets/scrollbarh.tga"
                scrollerName = "_scrollerWinH"
            }
            val mat = DeclManager.declManager.FindMaterial(barImage)
            mat!!.SetSort(Material.SS_GUI.toFloat())
            sizeBias = mat.GetImageWidth().toFloat()
            val scrollRect = idRectangle()
            if (horizontal) {
                sizeBias = mat.GetImageHeight().toFloat()
                scrollRect.x = 0.0f
                scrollRect.y = clientRect.h - sizeBias
                scrollRect.w = clientRect.w
                scrollRect.h = sizeBias
            } else {
                scrollRect.x = clientRect.w - sizeBias
                scrollRect.y = 0.0f
                scrollRect.w = sizeBias
                scrollRect.h = clientRect.h
            }
            scroller!!.InitWithDefaults(
                scrollerName,
                scrollRect,
                foreColor.data,
                matColor.data,
                mat.GetName(),
                thumbImage,
                !horizontal,
                true
            )
            InsertChild(scroller!!, null)
            scroller!!.SetBuddy(this)
        }

        companion object {
            private val buffer = CharArray(MAX_EDITFIELD)
        }
    }
}

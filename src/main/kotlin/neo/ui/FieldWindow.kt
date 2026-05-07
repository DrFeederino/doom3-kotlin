package neo.ui

import neo.idlib.Text.Parser.idParser
import neo.idlib.Text.Str.idStr
import neo.idlib.Text.Str.idStr.Companion.Icmp
import neo.idlib.toBoolean
import neo.ui.DeviceContext.idDeviceContext
import neo.ui.Rectangle.idRectangle
import neo.ui.UserInterfaceLocal.idUserInterfaceLocal
import neo.ui.Window.idWindow

class FieldWindow {
    internal class idFieldWindow : idWindow {
        private var cursorPos = 0
        private val cursorVar: idStr = idStr()
        private var lastCursorPos = 0
        private var lastTextLength = 0
        private var paintOffset = 0
        private var showCursor = false

        constructor(gui: idUserInterfaceLocal) : super(gui) {
            this.gui = gui
            CommonInit()
        }

        constructor(dc: idDeviceContext?, gui: idUserInterfaceLocal?) : super(dc, gui) {
            this.dc = dc
            this.gui = gui
            CommonInit()
        }

        override fun Draw(time: Int, x: Float, y: Float) {
            val scale = textScale.oCastFloat()
            val len = text.Length()
            cursorPos = gui!!.State().GetInt(cursorVar.toString())
            if (len != lastTextLength || cursorPos != lastCursorPos) {
                CalcPaintOffset(len)
            }
            val rect = idRectangle(textRect)
            if (paintOffset >= len) {
                paintOffset = 0
            }
            if (cursorPos > len) {
                cursorPos = len
            }
            //            dc->DrawText(&text[paintOffset], scale, 0, foreColor, rect, false, ((flags & WIN_FOCUS) || showCursor) ? cursorPos - paintOffset : -1);
            dc!!.DrawText(
                text.data.toString().substring(paintOffset),
                scale,
                0,
                foreColor.data,
                rect,
                false,
                if ((flags and Window.WIN_FOCUS).toBoolean() || showCursor) cursorPos - paintOffset else -1
            )
        }

        override fun ParseInternalVar(_name: String?, src: idParser): Boolean {
            if (Icmp(_name!!, "cursorvar") == 0) {
                ParseString(src, cursorVar)
                return true
            }
            if (Icmp(_name, "showcursor") == 0) {
                showCursor = src.ParseBool()
                return true
            }
            return super.ParseInternalVar(_name, src)
        }

        private fun CommonInit() {
            cursorPos = 0
            lastTextLength = 0
            lastCursorPos = 0
            paintOffset = 0
            showCursor = false
        }

        private fun CalcPaintOffset(len: Int) {
            var len = len
            lastCursorPos = cursorPos
            lastTextLength = len
            paintOffset = 0
            var tw = dc!!.TextWidth(text.data, textScale.data, -1)
            if (tw < textRect.w) {
                return
            }
            while (tw > textRect.w && len > 0) {
                tw = dc!!.TextWidth(text.data, textScale.data, --len)
                paintOffset++
            }
        }
    }
}

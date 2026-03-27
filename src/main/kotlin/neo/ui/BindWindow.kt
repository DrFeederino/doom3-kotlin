package neo.ui

import neo.framework.Common
import neo.framework.KeyInput.K_ESCAPE
import neo.framework.KeyInput.K_MOUSE1
import neo.idlib.Text.Str.idStr
import neo.idlib.Text.Str.idStr.Companion.Icmp
import neo.idlib.Text.Str.idStr.Companion.snPrintf
import neo.idlib.containers.CBool
import neo.sys.sysEventType_t
import neo.sys.sysEvent_s
import neo.ui.DeviceContext.idDeviceContext
import neo.ui.SimpleWindow.drawWin_t
import neo.ui.UserInterfaceLocal.idUserInterfaceLocal
import neo.ui.Window.idWindow
import neo.ui.Winvar.idWinStr
import neo.ui.Winvar.idWinVar

class BindWindow {
    internal class idBindWindow : idWindow {
        private val bindName = idWinStr()

        //
        //
        private var waitingOnKey = false

        constructor(gui: idUserInterfaceLocal) : super(gui) {
            this.gui = gui
            CommonInit()
        }

        constructor(dc: idDeviceContext?, gui: idUserInterfaceLocal?) : super(dc, gui) {
            this.dc = dc
            this.gui = gui
            CommonInit()
        }

        override fun HandleEvent(event: sysEvent_s, updateVisuals: CBool?): String {
            if (!(event.evType === sysEventType_t.SE_KEY && event.evValue2 != 0)) {
                return ""
            }
            val key = event.evValue
            if (waitingOnKey) {
                waitingOnKey = false
                if (key == K_ESCAPE) {
                    snPrintf(ret, ret.capacity(), "clearbind \"%s\"", bindName.GetName())
                } else {
                    snPrintf(ret, ret.capacity(), "bind %d \"%s\"", key, bindName.GetName())
                }
                return ret.toString()
            } else {
                if (key == K_MOUSE1) {
                    waitingOnKey = true
                    gui!!.SetBindHandler(this)
                    return ""
                }
            }
            return ""
        }

        override fun PostParse() {
            super.PostParse()
            bindName.SetGuiInfo(gui!!.GetStateDict(), bindName.c_str())
            bindName.Update()
            //bindName = state.GetString("bind");
            flags = flags or (Window.WIN_HOLDCAPTURE or Window.WIN_CANFOCUS)
        }

        override fun Draw(time: Int, x: Float, y: Float) {
            var color = foreColor.oCastIdVec4()
            val str: String
            str = (if (waitingOnKey) {
                val s = Common.common.GetLanguageDict().GetString("#str_07000")
                if (s.startsWith("#str_")) "Press a key..." else s
            } else if (bindName.Length() != 0) {
                bindName.c_str()
            } else {
                val s = Common.common.GetLanguageDict().GetString("#str_07001")
                if (s.startsWith("#str_")) "None" else s
            }).toString()
            if (waitingOnKey || hover && !noEvents.data && Contains(gui!!.CursorX(), gui!!.CursorY())) {
                color = hoverColor.oCastIdVec4()
            } else {
                hover = false
            }
            dc!!.DrawText(str, textScale.data, textAlign.code, color, textRect, false, -1)
        }

        override fun GetWinVarByName(_name: String?, winLookup: Boolean, owner: Array<drawWin_t?>?): idWinVar? {
            return if (Icmp(_name!!, "bind") == 0) {
                bindName
            } else super.GetWinVarByName(_name, winLookup, owner)
        }

        override fun Activate(activate: Boolean, act: idStr) {
            super.Activate(activate, act)
            bindName.Update()
        }

        private fun CommonInit() {
            bindName.data!!.set("")
            waitingOnKey = false
        }

        companion object {
            private val ret = StringBuffer(256)
        }
    }
}

package neo.ui

import neo.Renderer.Material
import neo.Renderer.Material.idMaterial
import neo.Renderer.r_scaleMenusTo43
import neo.Renderer.r_skipGuiShaders
import neo.TempDump.atof
import neo.TempDump.atoi
import neo.TempDump.btoi
import neo.TempDump.etoi
import neo.TempDump.itob
import neo.framework.CVarSystem.CVAR_ARCHIVE
import neo.framework.CVarSystem.CVAR_BOOL
import neo.framework.CVarSystem.CVAR_GUI
import neo.framework.CVarSystem.idCVar
import neo.framework.Common
import neo.framework.DeclManager
import neo.framework.DeclManager.declType_t
import neo.framework.DeclTable.idDeclTable
import neo.framework.DemoFile.idDemoFile
import neo.framework.File_h.idFile
import neo.framework.KeyInput.K_ENTER
import neo.framework.KeyInput.K_ESCAPE
import neo.framework.KeyInput.K_MOUSE1
import neo.framework.KeyInput.K_MOUSE2
import neo.framework.KeyInput.K_MOUSE3
import neo.framework.KeyInput.K_SHIFT
import neo.framework.KeyInput.K_TAB
import neo.framework.KeyInput.idKeyInput.IsDown
import neo.framework.Session.Companion.session
import neo.framework.UsercmdGen.USERCMD_MSEC
import neo.idlib.Dict_h.idDict
import neo.idlib.Dict_h.idKeyValue
import neo.idlib.Text.Lexer.LEXFL_ALLOWBACKSLASHSTRINGCONCAT
import neo.idlib.Text.Lexer.LEXFL_ALLOWMULTICHARLITERALS
import neo.idlib.Text.Lexer.LEXFL_NOFATALERRORS
import neo.idlib.Text.Lexer.LEXFL_NOSTRINGCONCAT
import neo.idlib.Text.Parser.idParser
import neo.idlib.Text.Str.idStr
import neo.idlib.Text.Str.idStr.Companion.Icmp
import neo.idlib.Text.Str.va
import neo.idlib.Text.Token.TT_FLOAT
import neo.idlib.Text.Token.TT_INTEGER
import neo.idlib.Text.Token.TT_NAME
import neo.idlib.Text.Token.TT_NUMBER
import neo.idlib.Text.Token.idToken
import neo.idlib.colorBlack
import neo.idlib.containers.CBool
import neo.idlib.containers.List.idFloatList
import neo.idlib.containers.List.idList
import neo.idlib.math.*
import neo.idlib.math.Interpolate.idInterpolateAccelDecelLinear
import neo.idlib.math.Matrix.idMat3
import neo.idlib.math.Matrix.idMat3.Companion.getMat3_identity
import neo.idlib.precompiled.MAX_EXPRESSION_OPS
import neo.idlib.precompiled.MAX_EXPRESSION_REGISTERS
import neo.sys.sysEventType_t
import neo.sys.sysEvent_s
import neo.ui.BindWindow.idBindWindow
import neo.ui.ChoiceWindow.idChoiceWindow
import neo.ui.DeviceContext.CstGetParams
import neo.ui.DeviceContext.idDeviceContext
import neo.ui.DeviceContext.idDeviceContext.CURSOR
import neo.ui.EditWindow.idEditWindow
import neo.ui.FieldWindow.idFieldWindow
import neo.ui.GameBearShootWindow.idGameBearShootWindow
import neo.ui.GameBustOutWindow.idGameBustOutWindow
import neo.ui.GameSSDWindow.idGameSSDWindow
import neo.ui.GuiScript.idGuiScript
import neo.ui.GuiScript.idGuiScriptList
import neo.ui.ListWindow.idListWindow
import neo.ui.MarkerWindow.idMarkerWindow
import neo.ui.Rectangle.idRectangle
import neo.ui.RegExp.idRegister.REGTYPE
import neo.ui.RegExp.idRegisterList
import neo.ui.RenderWindow.idRenderWindow
import neo.ui.SimpleWindow.drawWin_t
import neo.ui.SimpleWindow.idSimpleWindow
import neo.ui.SliderWindow.idSliderWindow
import neo.ui.UserInterfaceLocal.idUserInterfaceLocal
import neo.ui.Winvar.idWinBackground
import neo.ui.Winvar.idWinBool
import neo.ui.Winvar.idWinFloat
import neo.ui.Winvar.idWinInt
import neo.ui.Winvar.idWinRectangle
import neo.ui.Winvar.idWinStr
import neo.ui.Winvar.idWinVar
import neo.ui.Winvar.idWinVec4

object Window {
    //
    const val CAPTION_HEIGHT = "16.0"

    //
    const val DEFAULT_BACKCOLOR = "1 1 1 1"
    const val DEFAULT_BORDERCOLOR = "0 0 0 1"
    const val DEFAULT_FORECOLOR = "0 0 0 1"
    const val DEFAULT_TEXTSCALE = "0.4"
    const val MAX_LIST_ITEMS = 1024

    //
    const val MAX_WINDOW_NAME = 32
    const val SCROLLBAR_SIZE = 16
    const val SCROLLER_SIZE = "16.0"

    //
    const val TOP_PRIORITY = 4
    const val WIN_ACTIVE = 0x00200000
    const val WIN_BORDER = 0x00000004
    const val WIN_CANFOCUS = 0x00000800
    const val WIN_CAPTION = 0x00000002
    const val WIN_CAPTURE = 0x00000040
    const val WIN_CHILD = 0x00000001

    //
    const val WIN_DESKTOP = 0x10000000

    // DG: for the "scaleto43" window flag (=> scale window to 4:3 with "empty" bars left/right or above/below)
    const val WIN_SCALETO43 = 0x20000000

    // DG: if a gui explicitly wants to be stretched despite r_scaleMenusTo43 1 it can set `scaleto43 0`
    //     (useful when using anchors in fullscreen menus)
    const val WIN_NO_SCALETO43 = 0x40000000
    const val WIN_FOCUS = 0x00000020
    const val WIN_HCENTER = 0x00000080
    const val WIN_HOLDCAPTURE = 0x00004000
    const val WIN_INTRANSITION = 0x00000400
    const val WIN_INVERTRECT = 0x00020000
    const val WIN_MENUGUI = 0x00100000
    const val WIN_MODAL = 0x00000200
    const val WIN_MOVABLE = 0x00000010
    const val WIN_NATURALMAT = 0x00040000
    const val WIN_NOCLIP = 0x00010000
    const val WIN_NOCURSOR = 0x00080000
    const val WIN_NOWRAP = 0x00008000
    const val WIN_SELECTED = 0x00001000
    const val WIN_SHOWCOORDS = 0x00400000
    const val WIN_SHOWTIME = 0x00800000
    const val WIN_SIZABLE = 0x00000008
    const val WIN_TRANSFORM = 0x00002000
    const val WIN_VCENTER = 0x00000100
    const val WIN_WANTENTER = 0x01000000

    //
    const val WRITE_GUIS = false

    enum class wexpOpType_t {
        WOP_TYPE_ADD,
        WOP_TYPE_SUBTRACT,
        WOP_TYPE_MULTIPLY,
        WOP_TYPE_DIVIDE,
        WOP_TYPE_MOD,
        WOP_TYPE_TABLE,
        WOP_TYPE_GT,
        WOP_TYPE_GE,
        WOP_TYPE_LT,
        WOP_TYPE_LE,
        WOP_TYPE_EQ,
        WOP_TYPE_NE,
        WOP_TYPE_AND,
        WOP_TYPE_OR,
        WOP_TYPE_VAR,
        WOP_TYPE_VARS,
        WOP_TYPE_VARF,
        WOP_TYPE_VARI,
        WOP_TYPE_VARB,
        WOP_TYPE_COND
    }

    internal enum class wexpRegister_t {
        WEXP_REG_TIME,
        WEXP_REG_NUM_PREDEFINED
    }

    // Looks like each offset is exactly 48 bytes long
    internal enum class TransiotonalDataOffset(val offset: Int) {
        RECT_OFFSET(368),
        BACKCOLOR_OFFSET(416),
        MATCOLOR_OFFSET(464),
        FORECOLOR_OFFSET(512),
        BORDERCOLOR_OFFSET(560),
        TEXTSCALE_OFFSET(608),
        ROTATE_OFFSET(656),
        CSTANCHORFACTOR_OFFSET(704)
    }

    class wexpOp_t {
        var a: idWinVar? = null
        var d: idWinVar? = null
        var b = 0
        var c = 0
        var opType: wexpOpType_t? = null

        /**
         * @return
         */
        fun getA(): Int {
            if (a is idWinFloat) {
                return (a as idWinFloat).data.toInt()
            } else if (a is idWinInt) {
                return (a as idWinInt).data
            }
            return -1
        }

        fun getD(): Int {
            if (d is idWinFloat) {
                return (d as idWinFloat).data.toInt()
            } else if (d is idWinInt) {
                return (d as idWinInt).data
            }
            return -1
        }
    }

    class idRegEntry(var name: String, var type: REGTYPE) {
        var index = 0
    }

    class idTimeLineEvent {
        val event: idGuiScriptList = idGuiScriptList()
        var pending = false
        var time = 0
    }

    class rvNamedEvent(name: String?) {
        val mEvent: idGuiScriptList = idGuiScriptList()
        val mName: idStr = idStr(name!!)
    }

    class idTransitionData {
        var data: idWinVar? = null
        var interp = idInterpolateAccelDecelLinear<idVec4>(idVec4())
        var offset = 0
    }

    open class idWindow {
        var cmd = idStr()
        protected var actualX = 0.0f // physical coords
        protected var actualY = 0.0f // ''
        var backColor = idWinVec4()
        var backGroundName = idWinBackground()
        private val _backgroundHolder = arrayOfNulls<idMaterial>(1)
        var background: idMaterial? // background asset
            get() = _backgroundHolder[0]
            set(value) {
                _backgroundHolder[0] = value
            }
        var borderColor = idWinVec4()
        var borderSize = 0.0f
        protected var captureChild: idWindow? = null // if a child window has mouse capture
        protected var childID = 0 // this childs id
        protected val children = idList<idWindow?>() // child windows
        val clientRect = idRectangle() // client area
        protected var comment = idStr()
        protected /*unsigned*/ var cursor = 0.toChar()
        var dc: idDeviceContext?
        protected val definedVars = idList<idWinVar?>()
        val drawRect = idRectangle() // overall rect
        protected val drawWindows = idList<drawWin_t?>()
        protected val expressionRegisters = idFloatList()
        /*unsigned*/ var flags = 0 // visible, focus, mouseover, cursor, border, etc..

        //
        protected var focusedChild: idWindow? = null // if a child window has the focus
        /*unsigned*/ var fontNum = 0.toChar()
        var forceAspectHeight = 0.0f
        var forceAspectWidth = 0.0f
        var foreColor = idWinVec4()

        //
        protected var gui: idUserInterfaceLocal?

        //
        var hideCursor = idWinBool()
        protected var hover = false
        protected var hoverColor = idWinVec4()
        protected var lastTimeRun = 0 //
        var matColor = idWinVec4()
        var matScalex = 0.0f
        var matScaley = 0.0f
        var name: idStr? = null
        protected val namedEvents = idList<rvNamedEvent?>() //  added named events
        protected var noEvents = idWinBool()
        protected var noTime = idWinBool()
        protected val ops = idList<wexpOp_t>() // evaluate to make expressionRegisters
        val origin = idVec2()
        protected var overChild: idWindow? = null // if a child window has mouse capture
        protected var parent: idWindow? = null // parent window
        var rect = idWinRectangle() // overall rect
        protected var regList = idRegisterList()
        var rotate = idWinFloat()
        protected var saveOps // evaluate to make expressionRegisters
                : Array<idList<wexpOp_t>>? = null
        protected var saveRegs: Array<idFloatList>? = null
        protected var saveTemps: BooleanArray? = null
        protected var scripts = arrayOfNulls<idGuiScriptList>(etoi(ON.SCRIPT_COUNT))
        val shear = idVec2()
        var text = idWinStr()
        /*signed*/ var textAlign = 0.toChar()
        var textAlignx = 0.0f
        var textAligny = 0.0f
        val textRect = idRectangle() // text extented rect
        var textScale = idWinFloat()
        /*signed*/ var textShadow = 0.toChar()
        protected var timeLine = 0 // time stamp used for various fx
        protected val timeLineEvents = idList<idTimeLineEvent?>()
        protected val transitions = idList<idTransitionData>()
        protected val updateVars = idList<idWinVar?>()
        var visible = idWinBool()
        protected var xOffset = 0.0f
        protected var yOffset = 0.0f

        //#modified-fva; BEGIN
        val cstAnchor = idWinInt()
        val cstAnchorTo = idWinInt()        // for anchor transitions
        val cstAnchorFactor = idWinFloat()    // for anchor transitions
        var cstNoClipBackground = false
        //#modified-fva; END

        constructor(gui: idUserInterfaceLocal?) {
            dc = null
            this.gui = gui
            CommonInit()
        }

        constructor(d: idDeviceContext?, ui: idUserInterfaceLocal?) {
            dc = d
            gui = ui
            CommonInit()
        }

        /**
         * ~idWindow()
         */
        fun close() {
            CleanUp()
        }

        fun SetDC(d: idDeviceContext?) {
            dc = d
            //if (flags & WIN_DESKTOP) {
            dc!!.SetSize(forceAspectWidth, forceAspectHeight)
            //}

            //#modified-fva; BEGIN
            if (parent != null && parent!!.cstAnchor.data != DeviceContext.CstAnchor.CST_ANCHOR_NONE.value) {
                cstAnchor.set(parent!!.cstAnchor)
                cstAnchorTo.set(parent!!.cstAnchorTo)
                cstAnchorFactor.set(parent!!.cstAnchorFactor)
            }
            //#modified-fva; END

            val c = children.Num()
            for (i in 0 until c) {
                children[i]!!.SetDC(d)
            }
        }

        fun GetDC(): idDeviceContext? {
            return dc
        }


        fun SetFocus(w: idWindow?, scripts: Boolean = false /*= true*/): idWindow? {
            // only one child can have the focus
            var lastFocus: idWindow? = null
            if (w!!.flags and WIN_CANFOCUS != 0) {
                lastFocus = gui!!.GetDesktop()!!.focusedChild
                if (lastFocus != null) {
                    lastFocus.flags = lastFocus.flags and WIN_FOCUS.inv()
                    lastFocus.LoseFocus()
                }

                //  call on lose focus
                if (scripts && lastFocus != null) {
                    // calling this broke all sorts of guis
                    // lastFocus.RunScript(ON_MOUSEEXIT);
                }
                //  call on gain focus
                if (scripts && w != null) {
                    // calling this broke all sorts of guis
                    // w.RunScript(ON_MOUSEENTER);
                }
                w.flags = w.flags or WIN_FOCUS
                w.GainFocus()
                gui!!.GetDesktop()!!.focusedChild = w
            }
            return lastFocus
        }

        fun SetCapture(w: idWindow?): idWindow? {
            // only one child can have the focus
            var last: idWindow? = null
            val c = children.Num()
            for (i in 0 until c) {
                if (children[i]!!.flags and WIN_CAPTURE != 0) {
                    last = children[i]
                    //last.flags &= ~WIN_CAPTURE;
                    last!!.LoseCapture()
                    break
                }
            }
            w!!.flags = w.flags or WIN_CAPTURE
            w.GainCapture()
            gui!!.GetDesktop()!!.captureChild = w
            return last
        }

        fun SetParent(w: idWindow?) {
            parent = w
        }

        fun SetFlag( /*unsigned*/
                     f: Int
        ) {
            flags = flags or f
        }

        fun ClearFlag( /*unsigned*/
                       f: Int
        ) {
            flags = flags and f.inv()
        }

        /*unsigned*/ fun GetFlags(): Int {
            return flags
        }

        fun Move(x: Float, y: Float) {
            val rct = idRectangle(rect.data)
            rct.x = x
            rct.y = y
            val reg = RegList().FindReg("rect")
            reg?.Enable(false)
            rect.data.set(rct)
        }

        fun BringToTop(w: idWindow?) {
            if (w != null && 0 == w.flags and WIN_MODAL) {
                return
            }
            val c = children.Num()
            for (i in 0 until c) {
                if (children[i] == w) {
                    // this is it move from i - 1 to 0 to i to 1 then shove this one into 0
                    for (j in i + 1 until c) {
                        children[j - 1] = children[j]
                    }
                    children[c - 1] = w
                    break
                }
            }
        }

        fun Adjust(xd: Float, yd: Float) {}
        fun SetAdjustMode(child: idWindow?) {}
        fun Size(x: Float, y: Float, w: Float, h: Float) {
            val rct = idRectangle(rect.data)
            rct.x = x
            rct.y = y
            rct.w = w
            rct.h = h
            rect.data.set(rct)
            CalcClientRect(0.0f, 0.0f)
        }

        fun SetupFromState() {
//	idStr str;
            background = null
            SetupBackground()
            if (borderSize != 0.0f) {
                flags = flags or WIN_BORDER
            }
            if (regList.FindReg("rotate") != null || regList.FindReg("shear") != null) {
                flags = flags or WIN_TRANSFORM
            }
            CalcClientRect(0.0f, 0.0f)
            if (scripts[etoi(ON.ON_ACTION)] != null) {
                cursor = etoi(CURSOR.CURSOR_HAND).toChar()
                flags = flags or WIN_CANFOCUS
            }
        }

        fun SetupBackground() {
            if (backGroundName.Length() != 0) {
                background = DeclManager.declManager.FindMaterial(backGroundName.data!!)
                background!!.SetImageClassifications(1) // just for resource tracking
                if (background != null && !background!!.TestMaterialFlag(Material.MF_DEFAULTED)) {
                    background!!.SetSort(Material.SS_GUI.toFloat())
                }
            }
            backGroundName.SetMaterialPtr(_backgroundHolder)
        }

        fun FindChildByName(_name: String?): drawWin_t? {
            if (Icmp(name.toString(), _name!!) == 0) {
                dw.simp = null
                dw.win = this
                return dw
            }
            val c = drawWindows.Num()
            for (i in 0 until c) {
                if (drawWindows[i]!!.win != null) {
                    if (Icmp(drawWindows[i]!!.win!!.name!!, _name) == 0) {
                        return drawWindows[i]
                    }
                    val win = drawWindows[i]!!.win!!.FindChildByName(_name)
                    if (win != null) {
                        return win
                    }
                } else {
                    if (Icmp(drawWindows[i]!!.simp!!.name, _name) == 0) {
                        return drawWindows[i]
                    }
                }
            }
            return null
        }

        fun FindSimpleWinByName(_name: String?): idSimpleWindow {
            throw UnsupportedOperationException()
        }

        fun GetParent(): idWindow? {
            return parent
        }

        fun GetGui(): idUserInterfaceLocal {
            return gui!!
        }

        fun Contains(x: Float, y: Float): Boolean {
            val r = idRectangle(drawRect)
            r.x = actualX
            r.y = actualY

            // DG: if cstAnchor is used, the coordinates must adjusted
            if (cstAnchor.data != DeviceContext.CstAnchor.CST_ANCHOR_NONE.value) {
                // adjust r like idDeviceContext does for drawing
                val scale = idVec2()
                val offset = idVec2()
                if (CstGetParams(cstAnchor.data, cstAnchorTo.data, cstAnchorFactor.data, scale, offset)) {
                    r.x = r.x * scale.x + offset.x
                    r.y = r.y * scale.y + offset.y
                    r.w *= scale.x
                    r.h *= scale.y
                }
            }

            return r.Contains(x, y)
        }

        fun  /*size_t*/Size(): Int {
            val c = children.Num()
            var sz = 0
            for (i in 0 until c) {
                sz += children[i]!!.Size()
            }
            sz += Allocated()
            return sz
        }

        open fun  /*size_t*/Allocated(): Int {
            var i: Int
            var c: Int
            var sz = name!!.Allocated()
            sz += text.Size()
            sz += backGroundName.Size()
            c = definedVars.Num()
            i = 0
            while (i < c) {
                sz += definedVars[i]!!.Size()
                i++
            }
            i = 0
            while (i < ON.SCRIPT_COUNT.ordinal) {
                if (scripts[i] != null) {
                    sz += scripts[i]!!.Size()
                }
                i++
            }
            c = timeLineEvents.Num()
            i = 0
            while (i < c) {
                sz += 4
                i++
            }
            c = namedEvents.Num()
            i = 0
            while (i < c) {
                sz += 4
                i++
            }
            c = drawWindows.Num()
            i = 0
            while (i < c) {
                if (drawWindows[i]!!.simp != null) {
                    sz += 4
                }
                i++
            }
            return sz
        }

        fun GetStrPtrByName(_name: String?): idStr? {
            return null
        }

        open fun GetWinVarByName(
            _name: String?,
            fixup: Boolean /*= false*/,
            owner: Array<drawWin_t?>? /*= NULL*/
        ): idWinVar? {
            var retVar: idWinVar? = null
            if (owner != null) {
                owner[0] = null
            }
            if (Icmp(_name!!, "notime") == 0) {
                retVar = noTime
            }// FIXME: why does all this code not use "else if"?!
            // You tell me lol
            if (Icmp(_name, "background") == 0) {
                retVar = backGroundName
            }
            if (Icmp(_name, "visible") == 0) {
                retVar = visible
            }
            if (Icmp(_name, "rect") == 0) {
                retVar = rect
            }
            if (Icmp(_name, "backColor") == 0) {
                retVar = backColor
            }
            if (Icmp(_name, "matColor") == 0) {
                retVar = matColor
            }
            if (Icmp(_name, "foreColor") == 0) {
                retVar = foreColor
            }
            if (Icmp(_name, "hoverColor") == 0) {
                retVar = hoverColor
            }
            if (Icmp(_name, "borderColor") == 0) {
                retVar = borderColor
            }
            if (Icmp(_name, "textScale") == 0) {
                retVar = textScale
            }
            if (Icmp(_name, "rotate") == 0) {
                retVar = rotate
            }
            if (Icmp(_name, "noEvents") == 0) {
                retVar = noEvents
            }
            if (Icmp(_name, "text") == 0) {
                retVar = text
            }
            if (Icmp(_name, "backGroundName") == 0) {
                retVar = backGroundName
            }
            if (Icmp(_name, "hidecursor") == 0) {
                retVar = hideCursor
            }
            //#modified-fva; BEGIN
            if (Icmp(_name, "cstAnchor") == 0) {
                retVar = cstAnchor
            } else if (Icmp(_name, "cstAnchorTo") == 0) {
                retVar = cstAnchorTo
            } else if (Icmp(_name, "cstAnchorFactor") == 0) {
                retVar = cstAnchorFactor
            }
            //#modified-fva; END

            val key = idStr(_name)
            val guiVar = key.Find(Winvar.VAR_GUIPREFIX) >= 0
            val c = definedVars.Num()
            for (i in 0 until c) {
                if (Icmp(
                        _name, if (guiVar) va("%s", definedVars[i]!!.GetName()) else definedVars[i]!!.GetName()
                    ) == 0
                ) {
                    retVar = definedVars[i]
                    break
                }
            }
            if (retVar != null) {
                if (fixup && _name[0] != '$') {
                    DisableRegister(_name)
                }
                if (owner != null && parent != null) {
                    owner[0] = parent!!.FindChildByName(name.toString())
                }
                return retVar
            }
            val len = key.Length()
            if (len > 5 && guiVar) {
                val `var`: idWinVar = idWinStr()
                `var`.Init(_name, this)
                definedVars.Append(`var`)
                return `var`
            } else if (fixup) {
                val n = key.Find("::")
                if (n > 0) {
                    val winName = key.Left(n)
                    val `var` = key.Right(key.Length() - n - 2)
                    val win = GetGui().GetDesktop()!!.FindChildByName(winName.toString())
                    if (win != null) {
                        return if (win.win != null) {
                            win.win!!.GetWinVarByName(`var`.toString(), false, owner)
                        } else {
                            if (owner != null) {
                                owner[0] = win
                            }
                            win.simp!!.GetWinVarByName(`var`.toString())
                        }
                    }
                }
            }
            return null
        }


        fun GetWinVarByName(_name: String?, winLookup: Boolean = false /*= false*/): idWinVar? {
            return GetWinVarByName(_name, winLookup, null)
        }

        fun GetWinVarOffset(wv: idWinVar?, owner: drawWin_t): Int {
            var ret = -1

            if (wv === rect) {
                ret = TransiotonalDataOffset.RECT_OFFSET.offset
            }

            if (wv === backColor) {
                ret = TransiotonalDataOffset.BACKCOLOR_OFFSET.offset
            }

            if (wv === matColor) {
                ret = TransiotonalDataOffset.MATCOLOR_OFFSET.offset
            }

            if (wv === foreColor) {
                ret = TransiotonalDataOffset.FORECOLOR_OFFSET.offset
            }

            if (wv === hoverColor) {
                // TODO: Figure it out in the vanilla.
                //ret = TransiotonalDataOffset.HOVERCOLOR_OFFSET.offset
            }

            if (wv === borderColor) {
                ret = TransiotonalDataOffset.BORDERCOLOR_OFFSET.offset
            }

            if (wv === textScale) {
                ret = TransiotonalDataOffset.TEXTSCALE_OFFSET.offset
            }

            if (wv === rotate) {
                ret = TransiotonalDataOffset.ROTATE_OFFSET.offset
            }

            //#modified-fva; BEGIN
            if (wv === cstAnchorFactor) {
                ret = TransiotonalDataOffset.CSTANCHORFACTOR_OFFSET.offset
            }
            //#modified-fva; END
            if (ret != -1) {
                owner.win = this
                return ret
            }
            for (i in 0 until drawWindows.Num()) {
                ret = if (drawWindows[i]!!.win != null) {
                    drawWindows[i]!!.win!!.GetWinVarOffset(wv, owner)
                } else {
                    drawWindows[i]!!.simp!!.GetWinVarOffset(wv, owner)
                }
                if (ret != -1) {
                    break
                }
            }
            return ret
        }

        fun GetMaxCharHeight(): Float {
            SetFont()
            return dc!!.MaxCharHeight(textScale.data).toFloat()
        }

        fun GetMaxCharWidth(): Float {
            SetFont()
            return dc!!.MaxCharWidth(textScale.data).toFloat()
        }

        fun SetFont() {
            dc!!.SetFont(fontNum.code)
        }

        fun SetInitialState(_name: String?) {
            name = idStr(_name!!)
            matScalex = 1.0f
            matScaley = 1.0f
            forceAspectWidth = 640.0f
            forceAspectHeight = 480.0f
            noTime.data = false
            visible.data = true
            flags = 0
        }

        fun AddChild(win: idWindow) {
            win.childID = children.Append(win)
        }

        fun DebugDraw(time: Int, x: Float, y: Float) {
            if (dc != null) {
                dc!!.EnableClipping(false)
                if (gui_debug.GetInteger() == 1) {
                    dc!!.DrawRect(
                        drawRect.x,
                        drawRect.y,
                        drawRect.w,
                        drawRect.h,
                        1.0f,
                        idDeviceContext.colorRed
                    )
                } else if (gui_debug.GetInteger() == 2) {
//			char out[1024];
                    var out: String //[1024];
                    val str: idStr
                    str = idStr(text.c_str()!!)
                    if (str.Length() != 0) {
                        buff = String.format("%s\n", str)
                    }
                    out = String.format("Rect: %0.1f, %0.1f, %0.1f, %0.1f\n", rect.x(), rect.y(), rect.w(), rect.h())
                    buff += out
                    out = String.format(
                        "Draw Rect: %0.1f, %0.1f, %0.1f, %0.1f\n",
                        drawRect.x,
                        drawRect.y,
                        drawRect.w,
                        drawRect.h
                    )
                    buff += out
                    out = String.format(
                        "Client Rect: %0.1f, %0.1f, %0.1f, %0.1f\n",
                        clientRect.x,
                        clientRect.y,
                        clientRect.w,
                        clientRect.h
                    )
                    buff += out
                    out = String.format("Cursor: %0.1f : %0.1f\n", gui!!.CursorX(), gui!!.CursorY())
                    buff += out

                    //idRectangle tempRect = textRect;
                    //tempRect.x += offsetX;
                    //drawRect.y += offsetY;
                    dc!!.DrawText(buff, textScale.data, textAlign.code, foreColor.data, textRect, true)
                }
                dc!!.EnableClipping(true)
            }
        }

        fun CalcClientRect(xofs: Float, yofs: Float) {
            drawRect.set(rect.data)
            if (flags and WIN_INVERTRECT != 0) {
                drawRect.x = rect.x() - rect.w()
                drawRect.y = rect.y() - rect.h()
            }
            if (flags and (WIN_HCENTER or WIN_VCENTER) != 0 && parent != null) {
                // in this case treat xofs and yofs as absolute top left coords
                // and ignore the original positioning
                if (flags and WIN_HCENTER != 0) {
                    drawRect.x = (parent!!.rect.w() - rect.w()) / 2
                } else {
                    drawRect.y = (parent!!.rect.h() - rect.h()) / 2
                }
            }
            drawRect.x += xofs
            drawRect.y += yofs
            clientRect.set(drawRect)
            if (rect.h() > 0.0f && rect.w() > 0.0f) {
                if (flags and WIN_BORDER != 0 && borderSize != 0.0f) {
                    clientRect.x += borderSize
                    clientRect.y += borderSize
                    clientRect.w -= borderSize
                    clientRect.h -= borderSize
                }
                textRect.set(clientRect)
                textRect.x += 2.0f
                textRect.w -= 2.0f
                textRect.y += 2.0f
                textRect.h -= 2.0f
                textRect.x += textAlignx
                textRect.y += textAligny
            }
            origin.set(rect.x() + rect.w() / 2, rect.y() + rect.h() / 2)
        }

        private fun CommonInit() {
            childID = 0
            flags = 0
            lastTimeRun = 0
            origin.Zero()
            fontNum = 0.toChar()
            timeLine = -1
            yOffset = 0.0f
            xOffset = yOffset
            cursor = 0.toChar()
            forceAspectWidth = 640.0f
            forceAspectHeight = 480.0f
            matScalex = 1.0f
            matScaley = 1.0f
            borderSize = 0.0f
            noTime.data = false
            visible.data = true
            textAlign = 0.toChar()
            textAlignx = 0.0f
            textAligny = 0.0f
            noEvents.data = false
            rotate.data = 0.0f
            shear.Zero()
            textScale.data = 0.35f
            backColor.Zero()
            foreColor.set(idVec4(1, 1, 1, 1))
            hoverColor.set(idVec4(1, 1, 1, 1))
            matColor.set(idVec4(1, 1, 1, 1))
            borderColor.Zero()
            background = null
            backGroundName.set(idStr(""))
            focusedChild = null
            captureChild = null
            overChild = null
            parent = null
            saveOps = null
            saveRegs = null
            timeLine = -1
            textShadow = 0.toChar()
            hover = false
            for (i in 0 until ON.SCRIPT_COUNT.ordinal) {
                scripts[i] = null
            }
            hideCursor.data = false

            //#modified-fva; BEGIN
            cstAnchor.set(DeviceContext.CstAnchor.CST_ANCHOR_NONE.value)
            cstAnchorTo.set(DeviceContext.CstAnchor.CST_ANCHOR_NONE.value)
            cstAnchorFactor.set(0.0f)
            cstNoClipBackground = false
            //#modified-fva; END
        }

        fun CleanUp() {
            var i: Int
            val c = drawWindows.Num()
            i = 0
            while (i < c) {

//		delete drawWindows[i].simp;
                drawWindows[i] = null
                i++
            }

            // ensure the register list gets cleaned up
            regList.Reset()

            // Cleanup the named events
            namedEvents.DeleteContents(true)
            drawWindows.Clear()

            // Cleanup the operations and update vars
            // (if it is not fixed, orphane register references are possible)
            ops.Clear()
            updateVars.Clear()

            children.DeleteContents(true)
            definedVars.DeleteContents(true)
            timeLineEvents.DeleteContents(true)
            i = 0
            while (i < ON.SCRIPT_COUNT.ordinal) {

//		delete scripts[i];
                scripts[i] = null
                i++
            }
            CommonInit()
        }

        fun DrawBorderAndCaption(drawRect: idRectangle) {
            if (flags and WIN_BORDER != 0 && borderSize != 0.0f && borderColor.w() != 0.0f) {
                dc!!.DrawRect(drawRect.x, drawRect.y, drawRect.w, drawRect.h, borderSize, borderColor.data)
            }
        }

        fun DrawCaption(time: Int, x: Float, y: Float) {}
        fun SetupTransforms(x: Float, y: Float) {
            trans.Identity()
            org.set(origin.x + x, origin.y + y, 0.0f)
            if (rotate.data != 0.0f) {
                rot.Set(org, vec, rotate.data)
                trans.set(rot.ToMat3())
            }
            if (shear.x != 0.0f || shear.y != 0.0f) {
                smat.Identity()
                smat.set(0, 1, shear.x)
                smat.set(1, 0, shear.y)
                trans.timesAssign(smat)
            }
            if (!trans.IsIdentity()) {
                dc!!.SetTransformInfo(org, trans)
            }
        }

        fun Contains(sr: idRectangle?, x: Float, y: Float): Boolean {
            val r = idRectangle(sr)
            r.x += actualX - drawRect.x
            r.y += actualY - drawRect.y
            // DG: if cstAnchor is used, the coordinates must adjusted
            if (cstAnchor.data != DeviceContext.CstAnchor.CST_ANCHOR_NONE.value) {
                // adjust r like idDeviceContext does for drawing
                val scale = idVec2()
                val offset = idVec2()
                if (CstGetParams(cstAnchor.data, cstAnchorTo.data, cstAnchorFactor.data, scale, offset)) {
                    r.x = r.x * scale.x + offset.x
                    r.y = r.y * scale.y + offset.y
                    r.w *= scale.x
                    r.h *= scale.y
                }
            }
            return r.Contains(x, y)
        }

        fun GetName(): String {
            return name.toString()
        }


        fun Parse(src: idParser, rebuild: Boolean = true /*= true*/): Boolean {
            val token = idToken()
            var token2: idToken
            var work: idStr
            var dwt: drawWin_t
            if (rebuild) {
                CleanUp()
            }
            timeLineEvents.Clear()
            transitions.Clear()
            namedEvents.DeleteContents(true)
            src.ExpectTokenType(TT_NAME, 0, token)
            SetInitialState(token.toString())
            src.ExpectTokenString("{")
            src.ExpectAnyToken(token)
            var ret = true

            // attach a window wrapper to the window if the gui editor is running
//            if (ID_ALLOW_TOOLS) {
//                if ((com_editors & EDITOR_GUI) != 0) {
//                    new rvGEWindowWrapper(this, rvGEWindowWrapper.WT_NORMAL);
//                }
//            }
//
            while (!token.equals("}")) {
                // track what was parsed so we can maintain it for the guieditor
                src.SetMarker()
                dwt = drawWin_t()
                if (token.equals("windowDef") || token.equals("animationDef")) {
                    if (token.equals("animationDef")) {
                        visible.data = false
                        rect.data.set(idRectangle(0.0f, 0.0f, 0.0f, 0.0f))
                    }
                    src.ExpectTokenType(TT_NAME, 0, token)
                    token2 = token
                    src.UnreadToken(token)
                    val dw = FindChildByName(token2.toString())
                    if (dw != null && dw.win != null) {
                        SaveExpressionParseState()
                        dw.win!!.Parse(src, rebuild)
                        RestoreExpressionParseState()
                    } else {
                        val win = idWindow(dc, gui)
                        SaveExpressionParseState()
                        win.Parse(src, rebuild)
                        RestoreExpressionParseState()
                        win.SetParent(this)
                        dwt.simp = null
                        dwt.win = null
                        if (win.IsSimple()) {
                            val simple = idSimpleWindow(win)
                            dwt.simp = simple
                            drawWindows.Append(dwt)
                            win.close() //delete win;
                        } else {
                            AddChild(win)
                            SetFocus(win, false)
                            dwt.win = win
                            drawWindows.Append(dwt)
                        }
                    }
                } else if (token.equals("editDef")) {
                    val win = idEditWindow(dc, gui)
                    SaveExpressionParseState()
                    win.Parse(src, rebuild)
                    RestoreExpressionParseState()
                    AddChild(win)
                    win.SetParent(this)
                    dwt.simp = null
                    dwt.win = win
                    drawWindows.Append(dwt)
                } else if (token.equals("choiceDef")) {
                    val win = idChoiceWindow(dc, gui)
                    SaveExpressionParseState()
                    win.Parse(src, rebuild)
                    RestoreExpressionParseState()
                    AddChild(win)
                    win.SetParent(this)
                    dwt.simp = null
                    dwt.win = win
                    drawWindows.Append(dwt)
                } else if (token.equals("sliderDef")) {
                    val win = idSliderWindow(dc, gui)
                    SaveExpressionParseState()
                    win.Parse(src, rebuild)
                    RestoreExpressionParseState()
                    AddChild(win)
                    win.SetParent(this)
                    dwt.simp = null
                    dwt.win = win
                    drawWindows.Append(dwt)
                } else if (token.equals("markerDef")) {
                    val win = idMarkerWindow(dc, gui)
                    SaveExpressionParseState()
                    win.Parse(src, rebuild)
                    RestoreExpressionParseState()
                    AddChild(win)
                    win.SetParent(this)
                    dwt.simp = null
                    dwt.win = win
                    drawWindows.Append(dwt)
                } else if (token.equals("bindDef")) {
                    val win = idBindWindow(dc, gui)
                    SaveExpressionParseState()
                    win.Parse(src, rebuild)
                    RestoreExpressionParseState()
                    AddChild(win)
                    win.SetParent(this)
                    dwt.simp = null
                    dwt.win = win
                    drawWindows.Append(dwt)
                } else if (token.equals("listDef")) {
                    val win = idListWindow(dc, gui)
                    SaveExpressionParseState()
                    win.Parse(src, rebuild)
                    RestoreExpressionParseState()
                    AddChild(win)
                    win.SetParent(this)
                    dwt.simp = null
                    dwt.win = win
                    drawWindows.Append(dwt)
                } else if (token.equals("fieldDef")) {
                    val win = idFieldWindow(dc, gui)
                    SaveExpressionParseState()
                    win.Parse(src, rebuild)
                    RestoreExpressionParseState()
                    AddChild(win)
                    win.SetParent(this)
                    dwt.simp = null
                    dwt.win = win
                    drawWindows.Append(dwt)
                } else if (token.equals("renderDef")) {
                    val win = idRenderWindow(dc, gui)
                    SaveExpressionParseState()
                    win.Parse(src, rebuild)
                    RestoreExpressionParseState()
                    AddChild(win)
                    win.SetParent(this)
                    dwt.simp = null
                    dwt.win = win
                    drawWindows.Append(dwt)
                } else if (token.equals("gameSSDDef")) {
                    val win = idGameSSDWindow(dc, gui)
                    SaveExpressionParseState()
                    win.Parse(src, rebuild)
                    RestoreExpressionParseState()
                    AddChild(win)
                    win.SetParent(this)
                    dwt.simp = null
                    dwt.win = win
                    drawWindows.Append(dwt)
                } else if (token.equals("gameBearShootDef")) {
                    val win = idGameBearShootWindow(dc, gui)
                    SaveExpressionParseState()
                    win.Parse(src, rebuild)
                    RestoreExpressionParseState()
                    AddChild(win)
                    win.SetParent(this)
                    dwt.simp = null
                    dwt.win = win
                    drawWindows.Append(dwt)
                } else if (token.equals("gameBustOutDef")) {
                    val win = idGameBustOutWindow(dc, gui)
                    SaveExpressionParseState()
                    win.Parse(src, rebuild)
                    RestoreExpressionParseState()
                    AddChild(win)
                    win.SetParent(this)
                    dwt.simp = null
                    dwt.win = win
                    drawWindows.Append(dwt)
                } //
                else if (token.equals("onNamedEvent")) {
                    // Read the event name
                    if (!src.ReadToken(token)) {
                        src.Error("Expected event name")
                        return false
                    }
                    val ev = rvNamedEvent(token.toString())
                    src.SetMarker()
                    if (!ParseScript(src, ev.mEvent)) {
                        ret = false
                        break
                    }

                    // If we are in the gui editor then add the internal var to the
                    // the wrapper
//                    if (ID_ALLOW_TOOLS) {
//                        if ((com_editors & EDITOR_GUI) != 0) {
//                            idStr str = new idStr();
//                            idStr out = new idStr();
//
//                            // Grab the string from the last marker
//                            src.GetStringFromMarker(str, false);
//
//                            // Parse it one more time to knock unwanted tabs out
//                            idLexer src2 = new idLexer(str.toString(), str.Length(), "", src.GetFlags());
//                            src2.ParseBracedSectionExact(out, 1);
//
//                            // Save the script
//                            rvGEWindowWrapper.GetWrapper(this).GetScriptDict().Set(va("onEvent %s", token.c_str()), out);
//                        }
//                    }
                    namedEvents.Append(ev)
                } else if (token.equals("onTime")) {
                    val ev = idTimeLineEvent()
                    if (!src.ReadToken(token)) {
                        src.Error("Unexpected end of file")
                        return false
                    }
                    ev.time = atoi(token.toString())

                    // reset the mark since we dont want it to include the time
                    src.SetMarker()
                    if (!ParseScript(src, ev.event, null /*ev.time*/)) {
                        ret = false
                        break
                    }

                    // add the script to the wrappers script list
                    // If we are in the gui editor then add the internal var to the
                    // the wrapper
//                    if (ID_ALLOW_TOOLS) {
//                        if ((com_editors & EDITOR_GUI) != 0) {
//                            idStr str = new idStr();
//                            idStr out = new idStr();
//
//                            // Grab the string from the last marker
//                            src.GetStringFromMarker(str, false);
//
//                            // Parse it one more time to knock unwanted tabs out
//                            idLexer src2 = new idLexer(str.toString(), str.Length(), "", src.GetFlags());
//                            src2.ParseBracedSectionExact(out, 1);
//
//                            // Save the script
//                            rvGEWindowWrapper.GetWrapper(this).GetScriptDict().Set(va("onTime %d", ev.time), out);
//                        }
//                    }
                    // this is a timeline event
                    ev.pending = true
                    timeLineEvents.Append(ev)
                } else if (token.equals("definefloat")) {
                    src.ReadToken(token)
                    work = token
                    work.ToLower()
                    val varf = idWinFloat()
                    varf.SetName(work.toString())
                    definedVars.Append(varf)

                    // add the float to the editors wrapper dict
                    // Set the marker after the float name
                    src.SetMarker()

                    // Read in the float
                    regList.AddReg(work.toString(), etoi(REGTYPE.FLOAT), src, this, varf)

                    // If we are in the gui editor then add the float to the defines
//                    if (ID_ALLOW_TOOLS) {
//                        if ((com_editors & EDITOR_GUI) != 0) {
//                            idStr str;
//
//                            // Grab the string from the last marker and save it in the wrapper
//                            src.GetStringFromMarker(str, true);
//                            rvGEWindowWrapper.GetWrapper(this).GetVariableDict().Set(va("definefloat\t\"%s\"", token.c_str()), str);
//                        }
//                    }
                } else if (token.equals("definevec4")) {
                    src.ReadToken(token)
                    work = token
                    work.ToLower()
                    val `var` = idWinVec4()
                    `var`.SetName(work.toString())

                    // set the marker so we can determine what was parsed
                    // set the marker after the vec4 name
                    src.SetMarker()

                    // FIXME: how about we add the var to the desktop instead of this window so it won't get deleted
                    //        when this window is destoyed which even happens during parsing with simple windows ?
                    //definedVars.Append(var);
                    gui!!.GetDesktop()!!.definedVars.Append(`var`)
                    gui!!.GetDesktop()!!.regList.AddReg(
                        work.toString(),
                        etoi(REGTYPE.VEC4),
                        src,
                        gui!!.GetDesktop(),
                        `var`
                    )

                    // store the original vec4 for the editor
                    // If we are in the gui editor then add the float to the defines
//                    if (ID_ALLOW_TOOLS) {
//                        if ((com_editors & EDITOR_GUI) != 0) {
//                            idStr str = new idStr();
//
//                            // Grab the string from the last marker and save it in the wrapper
//                            src.GetStringFromMarker(str, true);
//                            rvGEWindowWrapper.GetWrapper(this).GetVariableDict().Set(va("definevec4\t\"%s\"", token.c_str()), str);
//                        }
//                    }
                } else if (token.equals("float")) {
                    src.ReadToken(token)
                    work = token
                    work.ToLower()
                    val varf = idWinFloat()
                    varf.SetName(work.toString())
                    definedVars.Append(varf)

                    // add the float to the editors wrapper dict
                    // set the marker to after the float name
                    src.SetMarker()

                    // Parse the float
                    regList.AddReg(work.toString(), etoi(REGTYPE.FLOAT), src, this, varf)

                    // If we are in the gui editor then add the float to the defines
//                    if (ID_ALLOW_TOOLS) {
//                        if ((com_editors & EDITOR_GUI) != 0) {
//                            idStr str;
//
//                            // Grab the string from the last marker and save it in the wrapper
//                            src.GetStringFromMarker(str, true);
//                            rvGEWindowWrapper.GetWrapper(this).GetVariableDict().Set(va("float\t\"%s\"", token.c_str()), str);
//                        }
//                    }
                } else if (ParseScriptEntry(token.toString(), src)) {
                    // add the script to the wrappers script list
                    // If we are in the gui editor then add the internal var to the
                    // the wrapper
//                    if (ID_ALLOW_TOOLS) {
//                        if ((com_editors & EDITOR_GUI) != 0) {
//                            idStr str = new idStr();
//                            idStr out = new idStr();
//
//                            // Grab the string from the last marker
//                            src.GetStringFromMarker(str, false);
//
//                            // Parse it one more time to knock unwanted tabs out
//                            idLexer src2 = new idLexer(str.toString(), str.Length(), "", src.GetFlags());
//                            src2.ParseBracedSectionExact(out, 1);
//
//                            // Save the script
//                            rvGEWindowWrapper.GetWrapper(this).GetScriptDict().Set(token, out);
//                        }
//                    }
                } else if (ParseInternalVar(token.toString(), src)) {
                    // gui editor support
                    // If we are in the gui editor then add the internal var to the
                    // the wrapper
//                    if (ID_ALLOW_TOOLS) {
//                        if ((com_editors & EDITOR_GUI) != 0) {
//                            idStr str = new idStr();
//                            src.GetStringFromMarker(str);
//                            rvGEWindowWrapper.GetWrapper(this).SetStateKey(token, str, false);
//                        }
//                    }
                } else {
                    ParseRegEntry(token.toString(), src)
                    // hook into the main window parsing for the gui editor
                    // If we are in the gui editor then add the internal var to the
                    // the wrapper
//                    if (ID_ALLOW_TOOLS) {
//                        if ((com_editors & EDITOR_GUI) != 0) {
//                            idStr str;
//                            src.GetStringFromMarker(str);
//                            rvGEWindowWrapper.GetWrapper(this).SetStateKey(token, str, false);
//                        }
//                    }
                }
                if (!src.ReadToken(token)) {
                    src.Error("Unexpected end of file")
                    ret = false
                    break
                }
            }
            if (ret) {
                EvalRegs(-1, true)
            }
            SetupFromState()
            PostParse()

            // hook into the main window parsing for the gui editor
            // If we are in the gui editor then add the internal var to the
            // the wrapper
//            if (ID_ALLOW_TOOLS) {
//                if ((com_editors & EDITOR_GUI) != 0) {
//                    rvGEWindowWrapper.GetWrapper(this).Finish();
//                }
//            }
//
            return ret
        }

        open fun HandleEvent(event: sysEvent_s, updateVisuals: CBool?): String? {
            cmd.set("")
            if (flags and WIN_DESKTOP != 0) {
                actionDownRun = false
                actionUpRun = false
                if (expressionRegisters.Num() != 0 && ops.Num() != 0) {
                    EvalRegs()
                }
                RunTimeEvents(gui!!.GetTime())
                CalcRects(0.0f, 0.0f)
                dc!!.SetCursor(etoi(CURSOR.CURSOR_ARROW))
            }
            if (visible.data && !noEvents.data) {
                if (event.evType == sysEventType_t.SE_KEY) {
                    EvalRegs(-1, true)
                    if (updateVisuals != null) {
                        updateVisuals._val = true
                    }
                    if (event.evValue == K_MOUSE1) {
                        if (0 == event.evValue2 && GetCaptureChild() != null) {
                            GetCaptureChild()!!.LoseCapture()
                            gui!!.GetDesktop()!!.captureChild = null
                            return ""
                        }
                        var c = children.Num()
                        while (--c >= 0) {
                            if (children[c]!!.visible.data
                                && children[c]!!.Contains(children[c]!!.drawRect, gui!!.CursorX(), gui!!.CursorY())
                                && !children[c]!!.noEvents.data
                            ) {
                                val child = children[c]
                                if (event.evValue2 != 0) {
                                    BringToTop(child)
                                    SetFocus(child)
                                    if (child!!.flags and WIN_HOLDCAPTURE != 0) {
                                        SetCapture(child)
                                    }
                                }
                                if (child!!.Contains(child.clientRect, gui!!.CursorX(), gui!!.CursorY())) {
                                    //if ((gui_edit.GetBool() && (child.flags & WIN_SELECTED)) || (!gui_edit.GetBool() && (child.flags & WIN_MOVABLE))) {
                                    //	SetCapture(child);
                                    //}
                                    SetFocus(child)
                                    val childRet = child.HandleEvent(event, updateVisuals)
                                    if (childRet != null && !childRet.isEmpty()) {
                                        return childRet
                                    }
                                    if (child.flags and WIN_MODAL != 0) {
                                        return ""
                                    }
                                } else {
                                    if (event.evValue2 != 0) {
                                        SetFocus(child)
                                        val capture = true
                                        if (capture && (child.flags and WIN_MOVABLE != 0 || gui_edit.GetBool())) {
                                            SetCapture(child)
                                        }
                                        return ""
                                    } else {
                                    }
                                }
                            }
                        }
                        if (event.evValue2 != 0 && !actionDownRun) {
                            actionDownRun = RunScript(ON.ON_ACTION)
                        } else if (!actionUpRun) {
                            actionUpRun = RunScript(ON.ON_ACTIONRELEASE)
                        }
                    } else if (event.evValue == K_MOUSE2) {
                        if (0 == event.evValue2 && GetCaptureChild() != null) {
                            GetCaptureChild()!!.LoseCapture()
                            gui!!.GetDesktop()!!.captureChild = null
                            return ""
                        }
                        var c = children.Num()
                        while (--c >= 0) {
                            if (children[c]!!.visible.data
                                && children[c]!!.Contains(children[c]!!.drawRect, gui!!.CursorX(), gui!!.CursorY())
                                && !children[c]!!.noEvents.data
                            ) {
                                val child = children[c]
                                if (event.evValue2 != 0) {
                                    BringToTop(child)
                                    SetFocus(child)
                                }
                                if (child!!.Contains(
                                        child.clientRect,
                                        gui!!.CursorX(),
                                        gui!!.CursorY()
                                    ) || GetCaptureChild() === child
                                ) {
                                    if (gui_edit.GetBool() && child.flags and WIN_SELECTED != 0 || !gui_edit.GetBool() && child.flags and WIN_MOVABLE != 0) {
                                        SetCapture(child)
                                    }
                                    val childRet = child.HandleEvent(event, updateVisuals)
                                    if (childRet != null && !childRet.isEmpty()) {
                                        return childRet
                                    }
                                    if (child.flags and WIN_MODAL != 0) {
                                        return ""
                                    }
                                }
                            }
                        }
                    } else if (event.evValue == K_MOUSE3) {
                        if (gui_edit.GetBool()) {
                            val c = children.Num()
                            for (i in 0 until c) {
                                if (children[i]!!.drawRect.Contains(gui!!.CursorX(), gui!!.CursorY())) {
                                    if (event.evValue2 != 0) {
                                        children[i]!!.flags = children[i]!!.flags xor WIN_SELECTED
                                        if (children[i]!!.flags and WIN_SELECTED != 0) {
                                            flags = flags and WIN_SELECTED.inv()
                                            return "childsel"
                                        }
                                    }
                                }
                            }
                        }
                    } else if (event.evValue == K_TAB && event.evValue2 != 0) {
                        if (GetFocusedChild() != null) {
                            val childRet = GetFocusedChild()!!.HandleEvent(event, updateVisuals)
                            if (childRet != null && !childRet.isEmpty()) {
                                return childRet
                            }

                            // If the window didn't handle the tab, then move the focus to the next window
                            // or the previous window if shift is held down
                            var direction = 1
                            if (IsDown(K_SHIFT)) {
                                direction = -1
                            }
                            val currentFocus = GetFocusedChild()
                            var child = GetFocusedChild()
                            var parent = child!!.GetParent()
                            while (parent != null) {
                                var foundFocus = false
                                var recurse = false
                                var index = 0
                                if (child != null) {
                                    index = parent.GetChildIndex(child) + direction
                                } else if (direction < 0) {
                                    index = parent.GetChildCount() - 1
                                }
                                while (index < parent!!.GetChildCount() && index >= 0) {
                                    val testWindow = parent.GetChild(index)
                                    if (testWindow === currentFocus) {
                                        // we managed to wrap around and get back to our starting window
                                        foundFocus = true
                                        break
                                    }
                                    if (testWindow != null && !testWindow.noEvents.data && testWindow.visible.data) {
                                        if (testWindow.flags and WIN_CANFOCUS != 0) {
                                            SetFocus(testWindow)
                                            foundFocus = true
                                            break
                                        } else if (testWindow.GetChildCount() > 0) {
                                            parent = testWindow
                                            child = null
                                            recurse = true
                                            break
                                        }
                                    }
                                    index += direction
                                }
                                if (foundFocus) {
                                    // We found a child to focus on
                                    break
                                } else if (recurse) {
                                    // We found a child with children
                                    continue
                                } else {
                                    // We didn't find anything, so go back up to our parent
                                    child = parent
                                    parent = child.GetParent()
                                    if (parent === gui!!.GetDesktop()) {
                                        // We got back to the desktop, so wrap around but don't actually go to the desktop
                                        parent = null
                                        child = null
                                    }
                                }
                            }
                        }
                    } else if (event.evValue == K_ESCAPE && event.evValue2 != 0) {
                        if (GetFocusedChild() != null) {
                            val childRet = GetFocusedChild()!!.HandleEvent(event, updateVisuals)
                            if (childRet != null && !childRet.isEmpty()) {
                                return childRet
                            }
                        }
                        RunScript(ON.ON_ESC)
                    } else if (event.evValue == K_ENTER) {
                        if (GetFocusedChild() != null) {
                            val childRet = GetFocusedChild()!!.HandleEvent(event, updateVisuals)
                            if (childRet != null && !childRet.isEmpty()) {
                                return childRet
                            }
                        }
                        if (flags and WIN_WANTENTER != 0) {
                            if (event.evValue2 != 0) {
                                RunScript(ON.ON_ACTION)
                            } else {
                                RunScript(ON.ON_ACTIONRELEASE)
                            }
                        }
                    } else {
                        if (GetFocusedChild() != null) {
                            val childRet = GetFocusedChild()!!.HandleEvent(event, updateVisuals)
                            if (childRet != null && !childRet.isEmpty()) {
                                return childRet
                            }
                        }
                    }
                } else if (event.evType == sysEventType_t.SE_MOUSE || event.evType == sysEventType_t.SE_MOUSE_ABS) {
                    if (updateVisuals != null) {
                        updateVisuals._val = true
                    }
                    val mouseRet = RouteMouseCoords(event.evValue.toFloat(), event.evValue2.toFloat())
                    if (mouseRet != null && !mouseRet.isEmpty()) {
                        return mouseRet
                    }
                } else if (event.evType == sysEventType_t.SE_NONE) {
                } else if (event.evType == sysEventType_t.SE_CHAR) {
                    if (GetFocusedChild() != null) {
                        val childRet = GetFocusedChild()!!.HandleEvent(event, updateVisuals)
                        if (childRet != null && !childRet.isEmpty()) {
                            return childRet
                        }
                    }
                }
            }
            gui!!.GetReturnCmd().set(cmd)
            if (gui!!.GetPendingCmd().Length() != 0) {
                gui!!.GetReturnCmd().plusAssign(" ; ")
                gui!!.GetReturnCmd().plusAssign(gui!!.GetPendingCmd())
                gui!!.GetPendingCmd().Clear()
            }
            cmd.set("")
            return gui!!.GetReturnCmd().toString()
        }

        fun CalcRects(x: Float, y: Float) {
            CalcClientRect(0.0f, 0.0f)
            drawRect.Offset(x, y)
            clientRect.Offset(x, y)
            actualX = drawRect.x
            actualY = drawRect.y
            val c = drawWindows.Num()
            for (i in 0 until c) {
                if (drawWindows[i]!!.win != null) {
                    drawWindows[i]!!.win!!.CalcRects(clientRect.x + xOffset, clientRect.y + yOffset)
                }
            }
            drawRect.Offset(-x, -y)
            clientRect.Offset(-x, -y)
        }

        fun Redraw(x: Float, y: Float) {
            var str: idStr
            if (r_skipGuiShaders!!.GetInteger() == 1 || dc == null) {
                return
            }
            val time = gui!!.GetTime()
            if (flags and WIN_DESKTOP != 0 && r_skipGuiShaders.GetInteger() != 3) {
                RunTimeEvents(time)
            }
            if (r_skipGuiShaders.GetInteger() == 2) {
                return
            }

            // DG: allow scaling menus to 4:3
            var fixupFor43 = false
            if (flags and WIN_DESKTOP != 0) {
                // only scale desktop windows (will automatically scale its sub-windows)
                // that EITHER have the scaleto43 flag set OR are fullscreen menus and r_scaleMenusTo43 is 1
                if ((flags and WIN_SCALETO43 != 0) || ((flags and WIN_MENUGUI) != 0 && r_scaleMenusTo43.GetBool() && (flags and WIN_NO_SCALETO43) == 0)) {
                    fixupFor43 = true
                    dc!!.SetMenuScaleFix(true)
                }
            }

            if (flags and WIN_SHOWTIME != 0) {
                dc!!.DrawText(
                    va(
                        " %0.1f seconds\n%s",
                        (time - timeLine).toFloat() / 1000,
                        gui!!.State().GetString("name")
                    ), 0.35f, 0, idDeviceContext.colorWhite, idRectangle(100.0f, 0.0f, 80.0f, 80.0f), false
                )
            }
            if (flags and WIN_SHOWCOORDS != 0) {
                dc!!.EnableClipping(false)
                str = idStr(
                    String.format(
                        "x: %d y: %d  cursorx: %d cursory: %d",
                        rect.x().toInt(),
                        rect.y().toInt(),
                        gui!!.CursorX().toInt(),
                        gui!!.CursorY().toInt()
                    )
                )
                dc!!.DrawText(
                    str.toString(),
                    0.25f,
                    0,
                    idDeviceContext.colorWhite,
                    idRectangle(0.0f, 0.0f, 100.0f, 20.0f),
                    false
                )
                dc!!.EnableClipping(true)
            }
            if (!visible.data) {
                if (fixupFor43) { // DG: gotta reset that before returning this function
                    dc!!.SetMenuScaleFix(false)
                }
                return
            }

            CalcClientRect(0.0f, 0.0f)

            SetFont()

            //#modified-fva; BEGIN
            //if (flags & WIN_DESKTOP) {
            // see if this window forces a new aspect ratio
            //dc!!.SetSize(forceAspectWidth, forceAspectHeight)
            //}
            if (parent != null && parent!!.cstAnchor.data != DeviceContext.CstAnchor.CST_ANCHOR_NONE.value) {
                cstAnchor.set(parent!!.cstAnchor)
                cstAnchorTo.set(parent!!.cstAnchorTo)
                cstAnchorFactor.set(parent!!.cstAnchorFactor)
            }
            if (!cst_hudAdjustAspect.GetBool() || cstAnchor.data == DeviceContext.CstAnchor.CST_ANCHOR_NONE.value) {
                dc?.SetSize(forceAspectWidth, forceAspectHeight)
            } else {
                // DG: if this Window uses anchors, it already is aspect-ratio-aware
                //     so a potentially active menuscalefix must be disabled
                //    (else it's "fixed" twice => wrong ratio in other direction)
                dc?.SetMenuScaleFix(false)
                dc?.CstSetSize(cstAnchor.data, cstAnchorTo.data, cstAnchorFactor.data)
            }
            //#modified-fva; END

            //FIXME: go to screen coord tracking
            drawRect.Offset(x, y)
            clientRect.Offset(x, y)
            textRect.Offset(x, y)
            actualX = drawRect.x
            actualY = drawRect.y
            val oldOrg = idVec3()
            val oldTrans = idMat3()
            dc!!.GetTransformInfo(oldOrg, oldTrans)
            SetupTransforms(x, y)

            //#modified-fva; BEGIN
            if (cstNoClipBackground) {
                dc?.EnableClipping(false)
            }
            //#modified-fva; END

            DrawBackground(drawRect)


            //#modified-fva; BEGIN
            if (cstNoClipBackground) {
                dc?.EnableClipping(true)
            }
            //#modified-fva; END

            DrawBorderAndCaption(drawRect)
            if (0 == flags and WIN_NOCLIP) {
                dc!!.PushClipRect(clientRect)
            }
            if (r_skipGuiShaders!!.GetInteger() < 5) {
                Draw(time, x, y)
            }
            if (gui_debug.GetInteger() != 0) {
                DebugDraw(time, x, y)
            }
            val c = drawWindows.Num()
            for (i in 0 until c) {
                if (drawWindows[i]!!.win != null) {
                    drawWindows[i]!!.win!!.Redraw(clientRect.x + xOffset, clientRect.y + yOffset)
                } else {
                    drawWindows[i]!!.simp!!.Redraw(clientRect.x + xOffset, clientRect.y + yOffset)
                }
            }

            // Put transforms back to what they were before the children were processed
            dc!!.SetTransformInfo(oldOrg, oldTrans)
            if (0 == flags and WIN_NOCLIP) {
                dc!!.PopClipRect()
            }
            if (gui_edit.GetBool()
                || (flags and WIN_DESKTOP != 0 && 0 == flags and WIN_NOCURSOR && !hideCursor.data
                        && (gui!!.Active() || flags and WIN_MENUGUI != 0))
            ) {
                dc!!.SetTransformInfo(vec3_origin, getMat3_identity())
                gui!!.DrawCursor()
            }
            if (gui_debug.GetInteger() != 0 && flags and WIN_DESKTOP != 0) {
                dc!!.EnableClipping(false)
                str = idStr(String.format("x: %.1f y: %.1f", gui!!.CursorX(), gui!!.CursorY()))
                dc!!.DrawText(
                    str.toString(),
                    0.25f,
                    0,
                    idDeviceContext.colorWhite,
                    idRectangle(0.0f, 0.0f, 100.0f, 20.0f),
                    false
                )
                dc!!.DrawText(
                    gui!!.GetSourceFile(),
                    0.25f,
                    0,
                    idDeviceContext.colorWhite,
                    idRectangle(0.0f, 20.0f, 300.0f, 20.0f),
                    false
                )
                dc!!.EnableClipping(true)
            }

            if (fixupFor43) { // DG: gotta reset that before returning this function
                dc?.SetMenuScaleFix(false)
            }

            drawRect.Offset(-x, -y)
            clientRect.Offset(-x, -y)
            textRect.Offset(-x, -y)
        }


        fun ArchiveToDictionary(dict: idDict?, useNames: Boolean = true /*= true*/) {
            //FIXME: rewrite without state
            val c = children.Num()
            for (i in 0 until c) {
                children[i]!!.ArchiveToDictionary(dict)
            }
        }


        fun InitFromDictionary(dict: idDict?, byName: Boolean = true /*= true*/) {
            //FIXME: rewrite without state
            val c = children.Num()
            for (i in 0 until c) {
                children[i]!!.InitFromDictionary(dict)
            }
        }

        open fun PostParse() {}
        open fun Activate(activate: Boolean, act: idStr) {
            val n = (if (activate) ON.ON_ACTIVATE else ON.ON_DEACTIVATE).ordinal

            //  make sure win vars are updated before activation
            UpdateWinVars()
            RunScript(n)
            val c = children.Num()
            for (i in 0 until c) {
                children[i]!!.Activate(activate, act)
            }
            if (act.Length() != 0) {
                act.Append(" ; ")
            }
        }

        fun Trigger() {
            RunScript(ON.ON_TRIGGER)
            val c = children.Num()
            for (i in 0 until c) {
                children[i]!!.Trigger()
            }
            StateChanged(true)
        }

        open fun GainFocus() {}
        fun LoseFocus() {}
        fun GainCapture() {}
        fun LoseCapture() {
            flags = flags and WIN_CAPTURE.inv()
        }

        fun Sized() {}
        fun Moved() {}
        open fun Draw(time: Int, x: Float, y: Float) {
            if (text.Length() == 0) {
                return
            }
            if (textShadow.code != 0) {
                val shadowText = idStr(text.data!!)
                val shadowRect = idRectangle(textRect)
                shadowText.RemoveColors()
                shadowRect.x += textShadow.code.toFloat()
                shadowRect.y += textShadow.code.toFloat()
                dc!!.DrawText(
                    shadowText.toString(),
                    textScale.data,
                    textAlign.code,
                    colorBlack,
                    shadowRect,
                    !itob(flags and WIN_NOWRAP),
                    -1
                )
            }
            dc!!.DrawText(
                text.data.toString(),
                textScale.data,
                textAlign.code,
                foreColor.data,
                textRect,
                !itob(flags and WIN_NOWRAP),
                -1
            )
            if (gui_edit.GetBool()) {
                dc!!.EnableClipping(false)
                dc!!.DrawText(
                    va("x: %d  y: %d", rect.x().toInt(), rect.y().toInt()),
                    0.25f,
                    0,
                    idDeviceContext.colorWhite,
                    idRectangle(rect.x(), rect.y() - 15, 100.0f, 20.0f),
                    false
                )
                dc!!.DrawText(
                    va("w: %d  h: %d", rect.w().toInt(), rect.h().toInt()),
                    0.25f,
                    0,
                    idDeviceContext.colorWhite,
                    idRectangle(rect.x() + rect.w(), rect.w() + rect.h() + 5, 100.0f, 20.0f),
                    false
                )
                dc!!.EnableClipping(true)
            }
        }

        open fun MouseExit() {
            if (noEvents.data) {
                return
            }
            RunScript(ON.ON_MOUSEEXIT)
        }

        open fun MouseEnter() {
            if (noEvents.data) {
                return
            }
            RunScript(ON.ON_MOUSEENTER)
        }

        open fun DrawBackground(drawRect: idRectangle) {
            if (backColor.w() != 0.0f) {
                dc!!.DrawFilledRect(drawRect.x, drawRect.y, drawRect.w, drawRect.h, backColor.data)
            }
            if (background != null && matColor.w() != 0.0f) {
                val scalex: Float
                val scaley: Float
                if (flags and WIN_NATURALMAT != 0) {
                    // DG: now also multiplied with matScalex/y, don't see a reason not to support that
                    //     (it allows scaling a tiled background image)
                    scalex = (drawRect.w / background!!.GetImageWidth()) * matScalex
                    scaley = (drawRect.h / background!!.GetImageHeight()) * matScaley
                } else {
                    scalex = matScalex
                    scaley = matScaley
                }
                dc!!.DrawMaterial(
                    drawRect.x,
                    drawRect.y,
                    drawRect.w,
                    drawRect.h,
                    background,
                    matColor.data,
                    scalex,
                    scaley
                )
            }
        }

        open fun RouteMouseCoords(xd: Float, yd: Float): String? {
            var str: String
            if (GetCaptureChild() != null) {
                //FIXME: unkludge this whole mechanism
                return GetCaptureChild()!!.RouteMouseCoords(xd, yd)
            }
            if (xd == -2000.0f || yd == -2000.0f) {
                return ""
            }
            var c = children.Num()
            while (c > 0) {
                val child = children[--c]
                if (child!!.visible.data && !child.noEvents.data && child.Contains(
                        child.drawRect,
                        gui!!.CursorX(),
                        gui!!.CursorY()
                    )
                ) {
                    dc!!.SetCursor(child.cursor.code)
                    child.hover = true
                    if (overChild !== child) {
                        if (overChild != null) {
                            overChild!!.MouseExit()
                            str = overChild!!.cmd.toString()
                            if ((str != null) and !str.isEmpty()) {
                                gui!!.GetDesktop()!!.AddCommand(str)
                                overChild!!.cmd.set("")
                            }
                        }
                        overChild = child
                        overChild!!.MouseEnter()
                        str = overChild!!.cmd.toString()
                        if ((str != null) and !str.isEmpty()) {
                            gui!!.GetDesktop()!!.AddCommand(str)
                            overChild!!.cmd.set("")
                        }
                    } else {
                        if (0 == child.flags and WIN_HOLDCAPTURE) {
                            child.RouteMouseCoords(xd, yd)
                        }
                    }
                    return ""
                }
            }
            if (overChild != null) {
                overChild!!.MouseExit()
                str = overChild!!.cmd.toString()
                if ((str != null) and !str.isEmpty()) {
                    gui!!.GetDesktop()!!.AddCommand(str)
                    overChild!!.cmd.set("")
                }
                overChild = null
            }
            return ""
        }

        open fun SetBuddy(buddy: idWindow?) {}
        open fun HandleBuddyUpdate(buddy: idWindow?) {}
        open fun StateChanged(redraw: Boolean) {
            UpdateWinVars()
            if (expressionRegisters.Num() != 0 && ops.Num() != 0) {
                EvalRegs()
            }
            val c = drawWindows.Num()
            for (i in 0 until c) {
                if (drawWindows[i]!!.win != null) {
                    drawWindows[i]!!.win!!.StateChanged(redraw)
                } else {
                    drawWindows[i]!!.simp!!.StateChanged(redraw)
                }
            }
            if (redraw) {
                if (flags and WIN_DESKTOP != 0) {
                    Redraw(0.0f, 0.0f)
                }
                if (background != null && background!!.CinematicLength() != 0) {
                    background!!.UpdateCinematic(gui!!.GetTime())
                }
            }
        }


        fun ReadFromDemoFile(f: idDemoFile?, rebuild: Boolean = true /*= true*/) {

            // should never hit unless we re-enable WRITE_GUIS
            assert(WRITE_GUIS)
        }

        fun WriteToDemoFile(f: idDemoFile?) {
            // should never hit unless we re-enable WRITE_GUIS
            assert(WRITE_GUIS)
        }

        // SaveGame support
        fun WriteSaveGameString(string: String, savefile: idFile) {
            val len = string.length
            savefile.WriteInt(len)
            savefile.WriteStringData(string, len)
        }

        fun WriteSaveGameString(string: idStr?, savefile: idFile) {
            WriteSaveGameString(string.toString(), savefile)
        }

        fun WriteSaveGameTransition(trans: idTransitionData, savefile: idFile) {
            val dw = drawWin_t()
            val fdw: drawWin_t?
            var winName = idStr("")
            dw.simp = null
            dw.win = null
            var offset = gui!!.GetDesktop()!!.GetWinVarOffset(trans.data, dw)
            if (dw.win != null || dw.simp != null) {
                winName = idStr(if (dw.win != null) dw.win!!.GetName() else dw.simp!!.name.toString())
            }
            fdw = gui!!.GetDesktop()!!.FindChildByName(winName.toString())
            if (offset != -1 && fdw != null && (fdw.win != null || fdw.simp != null)) {
                savefile.WriteInt(offset)
                WriteSaveGameString(winName.toString(), savefile)
                savefile.Write(trans.interp)
            } else {
                offset = -1
                savefile.WriteInt(offset)
            }
        }

        open fun WriteToSaveGame(savefile: idFile) {
            var i: Int

            WriteSaveGameString(cmd, savefile)

            savefile.WriteFloat(actualX)
            savefile.WriteFloat(actualY)
            savefile.WriteInt(childID)
            savefile.WriteInt(flags)
            savefile.WriteInt(lastTimeRun)
            savefile.Write(drawRect)
            savefile.Write(clientRect)
            savefile.Write(origin)
            savefile.WriteChar(fontNum)
            savefile.WriteInt(timeLine)
            savefile.WriteFloat(xOffset)
            savefile.WriteFloat(yOffset)
            savefile.WriteChar(cursor)
            savefile.WriteFloat(forceAspectWidth)
            savefile.WriteFloat(forceAspectHeight)
            savefile.WriteFloat(matScalex)
            savefile.WriteFloat(matScaley)
            savefile.WriteFloat(borderSize)
            savefile.WriteChar(textAlign)
            savefile.WriteFloat(textAlignx)
            savefile.WriteFloat(textAligny)
            savefile.WriteChar(textShadow)
            savefile.Write(shear)
            WriteSaveGameString(name, savefile)
            WriteSaveGameString(comment, savefile)

            // WinVars
            noTime.WriteToSaveGame(savefile)
            visible.WriteToSaveGame(savefile)
            rect.WriteToSaveGame(savefile)
            backColor.WriteToSaveGame(savefile)
            matColor.WriteToSaveGame(savefile)
            foreColor.WriteToSaveGame(savefile)
            hoverColor.WriteToSaveGame(savefile)
            borderColor.WriteToSaveGame(savefile)
            textScale.WriteToSaveGame(savefile)
            noEvents.WriteToSaveGame(savefile)
            rotate.WriteToSaveGame(savefile)
            text.WriteToSaveGame(savefile)
            backGroundName.WriteToSaveGame(savefile)
            hideCursor.WriteToSaveGame(savefile)


            //#modified-fva; BEGIN // FIXME: savegame version?
            cstAnchor.WriteToSaveGame(savefile)
            cstAnchorTo.WriteToSaveGame(savefile)
            cstAnchorFactor.WriteToSaveGame(savefile)
            savefile.WriteBool(cstNoClipBackground)
            //#modified-fva; END

            // Defined Vars
            i = 0
            while (i < definedVars.Num()) {
                definedVars[i]!!.WriteToSaveGame(savefile)
                i++
            }

            savefile.Write(textRect)

            // Window pointers saved as the child ID of the window
            var winID: Int
            winID = if (focusedChild != null) focusedChild!!.childID else -1
            savefile.WriteInt(winID)
            winID = if (captureChild != null) captureChild!!.childID else -1
            savefile.WriteInt(winID)
            winID = if (overChild != null) overChild!!.childID else -1
            savefile.WriteInt(winID)

            // Scripts
            i = 0
            while (i < ON.SCRIPT_COUNT.ordinal) {
                if (scripts[i] != null) {
                    scripts[i]!!.WriteToSaveGame(savefile)
                }
                i++
            }

            // TimeLine Events
            i = 0
            while (i < timeLineEvents.Num()) {
                if (timeLineEvents[i] != null) {
                    savefile.WriteBool(timeLineEvents[i]!!.pending)
                    savefile.WriteInt(timeLineEvents[i]!!.time)
                    if (timeLineEvents[i]!!.event != null) {
                        timeLineEvents[i]!!.event!!.WriteToSaveGame(savefile)
                    }
                }
                i++
            }

            // Transitions
            val num = transitions.Num()
            savefile.WriteInt(num)
            i = 0
            while (i < transitions.Num()) {
                WriteSaveGameTransition(transitions[i], savefile)
                i++
            }

            // Named Events
            i = 0
            while (i < namedEvents.Num()) {
                if (namedEvents[i] != null) {
                    WriteSaveGameString(namedEvents[i]!!.mName.toString(), savefile)
                    if (namedEvents[i]!!.mEvent != null) {
                        namedEvents[i]!!.mEvent!!.WriteToSaveGame(savefile)
                    }
                }
                i++
            }

            // regList
            regList.WriteToSaveGame(savefile)

            // Save children
            i = 0
            while (i < drawWindows.Num()) {
                val window = drawWindows[i]
                if (window!!.simp != null) {
                    window.simp!!.WriteToSaveGame(savefile)
                } else if (window.win != null) {
                    window.win!!.WriteToSaveGame(savefile)
                }
                i++
            }
        }

        fun ReadSaveGameString(string: idStr?, savefile: idFile) {
            val len: Int = savefile.ReadInt()
            if (len < 0) {
                Common.common.Warning("idWindow::ReadSaveGameString: invalid length")
            }
            string!!.Fill(' ', len)
            savefile.Read(string, len)
        }

        fun ReadSaveGameTransition(trans: idTransitionData, savefile: idFile) {
            val offset: Int
            offset = savefile.ReadInt()
            if (offset != -1) {
                val winName = idStr()
                ReadSaveGameString(winName, savefile)
                savefile.Read(trans.interp)
                trans.data = null
                trans.offset = offset
                if (winName.Length() != 0) {
                    val strVar = idWinStr()
                    strVar.Set(winName)
                    trans.data = strVar
                }
            }
        }

        open fun ReadFromSaveGame(savefile: idFile) {
            var i: Int
            transitions.Clear()
            ReadSaveGameString(cmd, savefile)
            actualX = savefile.ReadFloat()
            actualY = savefile.ReadFloat()
            childID = savefile.ReadInt()
            flags = savefile.ReadInt()
            lastTimeRun = savefile.ReadInt()
            savefile.Read(drawRect)
            savefile.Read(clientRect)
            savefile.Read(origin)
            fontNum = Char(savefile.ReadChar().toUShort())
            timeLine = savefile.ReadInt()
            xOffset = savefile.ReadFloat()
            yOffset = savefile.ReadFloat()
            cursor = Char(savefile.ReadChar().toUShort())
            forceAspectWidth = savefile.ReadFloat()
            forceAspectHeight = savefile.ReadFloat()
            matScalex = savefile.ReadFloat()
            matScaley = savefile.ReadFloat()
            borderSize = savefile.ReadFloat()
            textAlign = Char(savefile.ReadChar().toUShort())
            textAlignx = savefile.ReadFloat()
            textAligny = savefile.ReadFloat()
            textShadow = Char(savefile.ReadChar().toUShort())
            savefile.Read(shear)
            ReadSaveGameString(name, savefile)
            ReadSaveGameString(comment, savefile)

            // WinVars
            noTime.ReadFromSaveGame(savefile)
            visible.ReadFromSaveGame(savefile)
            rect.ReadFromSaveGame(savefile)
            backColor.ReadFromSaveGame(savefile)
            matColor.ReadFromSaveGame(savefile)
            foreColor.ReadFromSaveGame(savefile)
            hoverColor.ReadFromSaveGame(savefile)
            borderColor.ReadFromSaveGame(savefile)
            textScale.ReadFromSaveGame(savefile)
            noEvents.ReadFromSaveGame(savefile)
            rotate.ReadFromSaveGame(savefile)
            text.ReadFromSaveGame(savefile)
            backGroundName.ReadFromSaveGame(savefile)
            if (session.GetSaveGameVersion() >= 17) {
                hideCursor.ReadFromSaveGame(savefile)
            } else {
                hideCursor.data = false
            }


            //#modified-fva; BEGIN
            // TODO: why does this have to be read from the savegame anyway, does it change?
            if (session.GetSaveGameVersion() >= 18) {
                cstAnchor.ReadFromSaveGame(savefile)
                cstAnchorTo.ReadFromSaveGame(savefile)
                cstAnchorFactor.ReadFromSaveGame(savefile)
                cstNoClipBackground = savefile.ReadBool()
            } // else keep default values, I guess
            //#modified-fva; END

            // Defined Vars
            i = 0
            while (i < definedVars.Num()) {
                definedVars[i]!!.ReadFromSaveGame(savefile)
                i++
            }
            savefile.Read(textRect)

            // Window pointers saved as the child ID of the window
            var winID = -1
            winID = savefile.ReadInt()
            i = 0
            while (i < children.Num()) {
                if (children[i]!!.childID == winID) {
                    focusedChild = children[i]
                }
                i++
            }
            winID = savefile.ReadInt()
            i = 0
            while (i < children.Num()) {
                if (children[i]!!.childID == winID) {
                    captureChild = children[i]
                }
                i++
            }
            winID = savefile.ReadInt()
            i = 0
            while (i < children.Num()) {
                if (children[i]!!.childID == winID) {
                    overChild = children[i]
                }
                i++
            }

            // Scripts
            i = 0
            while (i < ON.SCRIPT_COUNT.ordinal) {
                if (scripts[i] != null) {
                    scripts[i]!!.ReadFromSaveGame(savefile)
                }
                i++
            }

            // TimeLine Events
            i = 0
            while (i < timeLineEvents.Num()) {
                if (timeLineEvents[i] != null) {
                    timeLineEvents[i]!!.pending = savefile.ReadBool()
                    timeLineEvents[i]!!.time = savefile.ReadInt()
                    if (timeLineEvents[i]!!.event != null) {
                        timeLineEvents[i]!!.event!!.ReadFromSaveGame(savefile)
                    }
                }
                i++
            }

            // Transitions
            val num: Int
            num = savefile.ReadInt()
            i = 0
            while (i < num) {
                val trans = idTransitionData()
                trans.data = null
                ReadSaveGameTransition(trans, savefile)
                if (trans.data != null) {
                    transitions.Append(trans)
                }
                i++
            }

            // Named Events
            i = 0
            while (i < namedEvents.Num()) {
                if (namedEvents[i] != null) {
                    ReadSaveGameString(namedEvents[i]!!.mName, savefile)
                    if (namedEvents[i]!!.mEvent != null) {
                        namedEvents[i]!!.mEvent!!.ReadFromSaveGame(savefile)
                    }
                }
                i++
            }

            // regList
            regList.ReadFromSaveGame(savefile)

            // Read children
            i = 0
            while (i < drawWindows.Num()) {
                val window = drawWindows[i]
                if (window!!.simp != null) {
                    window.simp!!.ReadFromSaveGame(savefile)
                } else if (window.win != null) {
                    window.win!!.ReadFromSaveGame(savefile)
                }
                i++
            }
            if (flags and WIN_DESKTOP != 0) {
                FixupTransitions()
            }
        }

        fun FixupTransitions() {
            var i: Int
            var c = transitions.Num()
            i = 0
            while (i < c) {
                val dw = gui!!.GetDesktop()!!.FindChildByName(transitions[i].data!!.c_str())
                transitions[i].data = null
                if (dw != null && (dw.win != null || dw.simp != null)) {
                    if (dw.win != null) {
                        if (transitions[i].offset == TransiotonalDataOffset.RECT_OFFSET.offset) {
                            transitions[i].data = dw.win?.rect
                        } else if (transitions[i].offset == TransiotonalDataOffset.BACKCOLOR_OFFSET.offset) {
                            transitions[i].data = dw.win?.backColor
                        } else if (transitions[i].offset == TransiotonalDataOffset.MATCOLOR_OFFSET.offset) {
                            transitions[i].data = dw.win?.matColor
                        } else if (transitions[i].offset == TransiotonalDataOffset.FORECOLOR_OFFSET.offset) {
                            transitions[i].data = dw.win?.foreColor
                        } else if (transitions[i].offset == TransiotonalDataOffset.BORDERCOLOR_OFFSET.offset) {
                            transitions[i].data = dw.win?.borderColor
                        } else if (transitions[i].offset == TransiotonalDataOffset.TEXTSCALE_OFFSET.offset) {
                            transitions[i].data = dw.win?.textScale
                        } else if (transitions[i].offset == TransiotonalDataOffset.ROTATE_OFFSET.offset) {
                            transitions[i].data = dw.win?.rotate
                        } //#modified-fva; BEGIN
                        else if (transitions[i].offset == TransiotonalDataOffset.CSTANCHORFACTOR_OFFSET.offset) {
                            transitions[i].data = dw.win?.cstAnchorFactor
                        }
                        //#modified-fva; END
                    } else {
                        if (transitions[i].offset == TransiotonalDataOffset.RECT_OFFSET.offset) {
                            transitions[i].data = dw.simp?.rect
                        } else if (transitions[i].offset == TransiotonalDataOffset.BACKCOLOR_OFFSET.offset) {
                            transitions[i].data = dw.simp?.backColor
                        } else if (transitions[i].offset == TransiotonalDataOffset.MATCOLOR_OFFSET.offset) {
                            transitions[i].data = dw.simp?.matColor
                        } else if (transitions[i].offset == TransiotonalDataOffset.FORECOLOR_OFFSET.offset) {
                            transitions[i].data = dw.simp?.foreColor
                        } else if (transitions[i].offset == TransiotonalDataOffset.BORDERCOLOR_OFFSET.offset) {
                            transitions[i].data = dw.simp?.borderColor
                        } else if (transitions[i].offset == TransiotonalDataOffset.TEXTSCALE_OFFSET.offset) {
                            transitions[i].data = dw.simp?.textScale
                        } else if (transitions[i].offset == TransiotonalDataOffset.ROTATE_OFFSET.offset) {
                            transitions[i].data = dw.simp?.rotate
                        }
                        //#modified-fva; BEGIN
                        else if (transitions[i].offset == TransiotonalDataOffset.CSTANCHORFACTOR_OFFSET.offset) {
                            transitions[i].data = dw.simp?.cstAnchorFactor
                        }
                        //#modified-fva; END
                    }
                }
                if (transitions[i].data == null) {
                    transitions.RemoveIndex(i)
                    i--
                    c--
                }
                i++
            }

            for (c in 0 until children.Num()) {
                children[c]!!.FixupTransitions()
            }
        }

        fun HasAction() {}
        fun HasScripts() {}
        fun FixupParms() {
            var i: Int
            var c = children.Num()
            i = 0
            while (i < c) {
                children[i]!!.FixupParms()
                i++
            }
            i = 0
            while (i < ON.SCRIPT_COUNT.ordinal) {
                if (scripts[i] != null) {
                    scripts[i]!!.FixupParms(this)
                }
                i++
            }
            c = timeLineEvents.Num()
            i = 0
            while (i < c) {
                timeLineEvents[i]!!.event!!.FixupParms(this)
                i++
            }
            c = namedEvents.Num()
            i = 0
            while (i < c) {
                namedEvents[i]!!.mEvent!!.FixupParms(this)
                i++
            }
            c = ops.Num()
            i = 0
            while (i < c) {
                if (ops[i].b == -2) {
                    // need to fix this up
                    val p = ops[i].a!!.c_str()
                    val `var` = GetWinVarByName(p, true)
                    ops[i].a =  /*(int)*/`var`
                    ops[i].b = -1
                }
                i++
            }
            if (flags and WIN_DESKTOP != 0) {
                CalcRects(0.0f, 0.0f)
            }
        }

        fun GetScriptString(name: String?, out: idStr?) {}
        fun SetScriptParams() {}
        fun HasOps(): Boolean {
            return ops.Num() > 0
        }


        fun EvalRegs(test: Int = -1 /*= -1*/, force: Boolean = false /*= false*/): Float {
            if (!force && test >= 0 && test < MAX_EXPRESSION_REGISTERS && lastEval === this) {
                return regs[test]
            }
            lastEval = this
            if (expressionRegisters.Num() != 0) {
                regList.SetToRegs(regs)
                EvaluateRegisters(regs)
                regList.GetFromRegs(regs)
            }
            return if (test >= 0 && test < MAX_EXPRESSION_REGISTERS) {
                regs[test]
            } else 0.0f
        }

        fun StartTransition() {
            flags = flags or WIN_INTRANSITION
        }

        fun AddTransition(dest: idWinVar?, from: idVec4, to: idVec4, time: Int, accelTime: Float, decelTime: Float) {
            val data = idTransitionData()
            data.data = dest
            data.interp.Init(gui!!.GetTime().toFloat(), accelTime * time, decelTime * time, time.toFloat(), from, to)
            transitions.Append(data)
        }

        fun ResetTime(t: Int) {
            timeLine = gui!!.GetTime() - t
            var i: Int
            var c = timeLineEvents.Num()
            i = 0
            while (i < c) {
                if (timeLineEvents[i]!!.time >= t) {
                    timeLineEvents[i]!!.pending = true
                }
                i++
            }
            noTime.data = false
            c = transitions.Num()
            i = 0
            while (i < c) {
                val data = transitions[i]
                if (data.interp.IsDone(gui!!.GetTime().toFloat()) && data.data != null) {
                    transitions.RemoveIndex(i)
                    i--
                    c--
                }
                i++
            }
        }

        fun ResetCinematics() {
            if (background != null) {
                background!!.ResetCinematicTime(gui!!.GetTime())
            }
        }

        fun NumTransitions(): Int {
            var c = transitions.Num()
            for (i in 0 until children.Num()) {
                c += children[i]!!.NumTransitions()
            }
            return c
        }


        fun ParseScript(
            src: idParser,
            list: idGuiScriptList?,
            timeParm: IntArray? = null /*= NULL*/,
            elseBlock: Boolean = false /*= false*/
        ): Boolean {
            var ifElseBlock = false
            val token = idToken()

            // scripts start with { ( unless parm is true ) and have ; separated command lists.. commands are command,
            // arg.. basically we want everything between the { } as it will be interpreted at
            // run time
            if (elseBlock) {
                src.ReadToken(token)
                if (0 == token.Icmp("if")) {
                    ifElseBlock = true
                }
                src.UnreadToken(token)
                if (!ifElseBlock && !src.ExpectTokenString("{")) {
                    return false
                }
            } else if (!src.ExpectTokenString("{")) {
                return false
            }
            var nest = 0
            while (true) {
                if (!src.ReadToken(token)) {
                    src.Error("Unexpected end of file")
                    return false
                }
                if (token.equals("{")) {
                    nest++
                }
                if (token.equals("}")) {
                    if (nest-- <= 0) {
                        return true
                    }
                }
                val gs = idGuiScript()
                if (token.Icmp("if") == 0) {
                    gs.conditionReg = ParseExpression(src)
                    gs.ifList = idGuiScriptList()
                    ParseScript(src, gs.ifList, null)
                    if (src.ReadToken(token)) {
                        if (token.equals("else")) {
                            gs.elseList = idGuiScriptList()
                            // pass true to indicate we are parsing an else condition
                            ParseScript(src, gs.elseList, null, true)
                        } else {
                            src.UnreadToken(token)
                        }
                    }
                    list!!.Append(gs)

                    // if we are parsing an else if then return out so
                    // the initial "if" parser can handle the rest of the tokens
                    if (ifElseBlock) {
                        return true
                    }
                    continue
                } else {
                    src.UnreadToken(token)
                }

                // empty { } is not allowed
                if (token.equals("{")) {
                    src.Error("Unexpected {")
                    //			 delete gs;
                    return false
                }
                gs.Parse(src)
                list!!.Append(gs)
            }
        }

        fun RunScript(n: Int): Boolean {
            return if (n >= ON.ON_MOUSEENTER.ordinal && n < ON.SCRIPT_COUNT.ordinal) {
                RunScriptList(scripts[n])
            } else false
        }

        fun RunScript(n: Enum<*>?): Boolean {
            return this.RunScript(etoi(n!!))
        }

        fun RunScriptList(src: idGuiScriptList?): Boolean {
            if (src == null) {
                return false
            }
            src.Execute(this)
            return true
        }

        fun SetRegs(key: String?, `val`: String?) {}

        /*
         ================
         idWindow::ParseExpression

         Returns a register index
         ================
         */

        fun ParseExpression(src: idParser, `var`: idWinVar? = null /*= NULL*/, component: Int = 0 /*= 0*/): Int {
            return ParseExpressionPriority(src, TOP_PRIORITY, `var`)
        }

        fun ExpressionConstant(f: Float): Int {
            var i: Int
            i = etoi(wexpRegister_t.WEXP_REG_NUM_PREDEFINED)
            while (i < expressionRegisters.Num()) {
                if (!registerIsTemporary[i] && expressionRegisters[i] == f) {
                    return i
                }
                i++
            }
            if (expressionRegisters.Num() == MAX_EXPRESSION_REGISTERS) {
                Common.common.Warning("expressionConstant: gui %s hit MAX_EXPRESSION_REGISTERS", gui!!.GetSourceFile())
                return 0
            }
            val c = expressionRegisters.Num()
            if (i > c) {
                while (i > c) {
                    expressionRegisters.Append(-9999999.0f)
                    i--
                }
            }
            i = expressionRegisters.Append(f)
            registerIsTemporary[i] = false
            return i
        }

        fun RegList(): idRegisterList {
            return regList
        }

        fun AddCommand(_cmd: String?) {
            var str: String? = cmd.toString()
            if (!str!!.isEmpty()) {
                str += " ; "
                str += _cmd
            } else {
                str = _cmd
            }
            cmd.set(str)
        }

        fun AddUpdateVar(`var`: idWinVar?) {
            updateVars.AddUnique(`var`)
        }

        fun Interactive(): Boolean {
            if (scripts[ON.ON_ACTION.ordinal] != null) {
                return true
            }
            val c = children.Num()
            for (i in 0 until c) {
                if (children[i]!!.Interactive()) {
                    return true
                }
            }
            return false
        }

        fun ContainsStateVars(): Boolean {
            if (updateVars.Num() != 0) {
                return true
            }
            val c = children.Num()
            for (i in 0 until c) {
                if (children[i]!!.ContainsStateVars()) {
                    return true
                }
            }
            return false
        }

        fun SetChildWinVarVal(name: String?, `var`: String?, `val`: String?) {
            val dw = FindChildByName(name)
            var wv: idWinVar? = null
            if (dw != null) {
                if (dw.simp != null) {
                    wv = dw.simp!!.GetWinVarByName(`var`)
                } else if (dw.win != null) {
                    wv = dw.win!!.GetWinVarByName(`var`)
                }
                if (wv != null) {
                    wv.Set(`val`)
                    wv.SetEval(false)
                }
            }
        }

        fun GetFocusedChild(): idWindow? {
            return if (flags and WIN_DESKTOP != 0) {
                gui!!.GetDesktop()!!.focusedChild
            } else null
        }

        fun GetCaptureChild(): idWindow? {
            return if (flags and WIN_DESKTOP != 0) {
                gui!!.GetDesktop()!!.captureChild
            } else null
        }

        fun GetComment(): String {
            return comment.toString()
        }

        fun SetComment(p: String?) {
            comment.set(p)
        }

        open fun RunNamedEvent(eventName: String?) {
            var i: Int
            var c: Int

            // Find and run the event
            c = namedEvents.Num()
            for (i in 0 until c) {
                if (namedEvents[i]!!.mName.Icmp(eventName!!) != 0) {
                    continue
                }
                UpdateWinVars()

                // Make sure we got all the current values for stuff
                if (expressionRegisters.Num() != 0 && ops.Num() != 0) {
                    EvalRegs(-1, true)
                }

                RunScriptList(namedEvents[i]!!.mEvent)
                break
            }

            // Run the event in all the children as well
            c = children.Num()
            i = 0
            while (i < c) {
                children[i]!!.RunNamedEvent(eventName)
                i++
            }
        }

        fun AddDefinedVar(`var`: idWinVar?) {
            definedVars.AddUnique(`var`)
        }

        fun FindChildByPoint(x: Float, y: Float, below: idWindow? = null): idWindow? {
            val belowArr = arrayOf(below)
            return FindChildByPoint(x, y, belowArr)
        }

        fun GetChildIndex(window: idWindow): Int {
            var find: Int
            find = 0
            while (find < drawWindows.Num()) {
                if (drawWindows[find]!!.win === window) {
                    return find
                }
                find++
            }
            return -1
        }

        /*
         ================
         idWindow::GetChildCount

         Returns the number of children
         ================
         */
        fun GetChildCount(): Int {
            return drawWindows.Num()
        }

        fun GetChild(index: Int): idWindow? {
            return drawWindows[index]!!.win
        }

        /*
         ================
         idWindow::RemoveChild

         Removes the child from the list of children.   Note that the child window being
         removed must still be deallocated by the caller
         ================
         */
        fun RemoveChild(win: idWindow) {
            var find: Int

            // Remove the child window
            children.Remove(win)
            find = 0
            while (find < drawWindows.Num()) {
                if (drawWindows[find]!!.win == win) {
                    drawWindows.RemoveIndex(find)
                    break
                }
                find++
            }
        }

        /*
         ================
         idWindow::InsertChild

         Inserts the given window as a child into the given location in the zorder.
         ================
         */
        fun InsertChild(win: idWindow, before: idWindow?): Boolean {
            AddChild(win)
            win.parent = this
            val dwt = drawWin_t()
            dwt.simp = null
            dwt.win = win

            // If not inserting before anything then just add it at the end
            if (before != null) {
                val index: Int
                index = GetChildIndex(before)
                if (index != -1) {
                    drawWindows.Insert(dwt, index)
                    return true
                }
            }
            drawWindows.Append(dwt)
            return true
        }

        fun ScreenToClient(rect: idRectangle) {
            var x: Int
            var y: Int
            var p: idWindow?
            p = this
            x = 0
            y = 0
            while (p != null) {
                x = (x + p.rect.x()).toInt()
                y = (y + p.rect.y()).toInt()
                p = p.parent
            }
            rect.x -= x.toFloat()
            rect.y -= y.toFloat()
        }

        fun ClientToScreen(rect: idRectangle) {
            var x: Int
            var y: Int
            var p: idWindow?
            p = this
            x = 0
            y = 0
            while (p != null) {
                x = (x + p.rect.x()).toInt()
                y = (y + p.rect.y()).toInt()
                p = p.parent
            }
            rect.x += x.toFloat()
            rect.y += y.toFloat()
        }

        fun UpdateFromDictionary(dict: idDict): Boolean {
            var kv: idKeyValue?
            var i: Int
            SetDefaults()

            // Clear all registers since they will get recreated
            regList.Reset()
            expressionRegisters.Clear()
            ops.Clear()
            i = 0
            while (i < dict.GetNumKeyVals()) {
                kv = dict.GetKeyVal(i)

                // Special case name
                if (kv!!.GetKey().Icmp("name") == 0) {
                    name = kv.GetValue()
                    i++
                    continue
                }
                val src = idParser(
                    kv.GetValue().toString(), kv.GetValue().Length(), "",
                    LEXFL_NOFATALERRORS or LEXFL_NOSTRINGCONCAT or LEXFL_ALLOWMULTICHARLITERALS or LEXFL_ALLOWBACKSLASHSTRINGCONCAT
                )
                if (!ParseInternalVar(kv.GetKey().toString(), src)) {
                    // Kill the old register since the parse reg entry will add a new one
                    if (!ParseRegEntry(kv.GetKey().toString(), src)) {
                        i++
                        continue
                    }
                }
                i++
            }
            EvalRegs(-1, true)
            SetupFromState()
            PostParse()
            return true
        }

        // friend		class rvGEWindowWrapper;
        /*
         ================
         idWindow::FindChildByPoint

         Finds the window under the given point
         ================
         */
        protected fun FindChildByPoint(x: Float, y: Float, below: Array<idWindow?>): idWindow? {
            val c = children.Num()

            // If we are looking for a window below this one then
            // the next window should be good, but this one wasnt it
            if (below[0] == this) {
                below[0] = null
                return null
            }
            if (!Contains(drawRect, x, y)) {
                return null
            }
            for (i in c - 1 downTo 0) {
                val found = children[i]!!.FindChildByPoint(x, y, below)
                if (found != null) {
                    if (below[0] != null) {
                        continue
                    }
                    return found
                }
            }
            return this
        }

        /*
         ================
         idWindow::SetDefaults

         Set the window do a default window with no text, no background and 
         default colors, etc..
         ================
         */
        protected fun SetDefaults() {
            forceAspectWidth = 640.0f
            forceAspectHeight = 480.0f
            matScalex = 1.0f
            matScaley = 1.0f
            borderSize = 0.0f
            noTime.data = false
            visible.data = true
            textAlign = 0.toChar()
            textAlignx = 0.0f
            textAligny = 0.0f
            noEvents.data = false
            rotate.data = 0.0f
            shear.Zero()
            textScale.data = 0.35f
            backColor.Zero()
            foreColor.set(idVec4(1, 1, 1, 1))
            hoverColor.set(idVec4(1, 1, 1, 1))
            matColor.set(idVec4(1, 1, 1, 1))
            borderColor.Zero()
            text.data!!.set("")
            background = null
            backGroundName.data!!.set("")
        }

        ////
        //// friend class idSimpleWindow;
        //// friend class idUserInterfaceLocal;
        protected fun IsSimple(): Boolean {

            // dont do simple windows when in gui editor
            if (Common.com_editors and Common.EDITOR_GUI != 0) {
                return false
            }
            if (ops.Num() != 0) {
                return false
            }
            if (flags and (WIN_HCENTER or WIN_VCENTER) != 0) {
                return false
            }
            if (children.Num() != 0 || drawWindows.Num() != 0) {
                return false
            }
            for (i in 0 until ON.SCRIPT_COUNT.ordinal) {
                if (scripts[i] != null) {
                    return false
                }
            }
            return if (timeLineEvents.Num() != 0) {
                false
            } else namedEvents.Num() == 0
        }

        protected fun UpdateWinVars() {
            val c = updateVars.Num()
            for (i in 0 until c) {
                updateVars[i]!!.Update()
            }
        }

        protected fun DisableRegister(_name: String?) {
            val reg = RegList().FindReg(_name)
            reg?.Enable(false)
        }

        protected fun Transition() {
            var i: Int
            val c = transitions.Num()
            var clear = true
            i = 0
            while (i < c) {
                val data = transitions[i]
                var r: idWinRectangle? = null
                var `val`: idWinFloat? = null
                var v4: idWinVec4? = null
                if (data.data is idWinVec4) {
                    v4 = data.data as idWinVec4?
                } else if (data.data is idWinFloat) {
                    `val` = data.data as idWinFloat?
                } else {
                    r = data.data as idWinRectangle?
                }
                if (data.interp.IsDone(gui!!.GetTime().toFloat()) && data.data != null) {
                    if (v4 != null) {
                        v4.set(data.interp.GetEndValue())
                    } else `val`?.set(data.interp.GetEndValue()[0]) ?: r!!.set(data.interp.GetEndValue())
                } else {
                    clear = false
                    if (data.data != null) {
                        if (v4 != null) {
                            v4.set(data.interp.GetCurrentValue(gui!!.GetTime().toFloat()))
                        } else `val`?.set(data.interp.GetCurrentValue(gui!!.GetTime().toFloat())[0])
                            ?: r!!.set(data.interp.GetCurrentValue(gui!!.GetTime().toFloat()))
                    } else {
                        Common.common.Warning(
                            "Invalid transitional data for window %s in gui %s",
                            GetName(),
                            gui!!.GetSourceFile()
                        )
                    }
                }
                i++
            }
            if (clear) {
                transitions.SetNum(0, false)
                flags = flags and WIN_INTRANSITION.inv()
            }
        }

        protected fun Time() {
            if (noTime.data) {
                return
            }
            if (timeLine == -1) {
                timeLine = gui!!.GetTime()
            }
            cmd.set("")
            val c = timeLineEvents.Num()
            if (c > 0) {
                for (i in 0 until c) {
                    if (timeLineEvents[i]!!.pending && gui!!.GetTime() - timeLine >= timeLineEvents[i]!!.time) {
                        timeLineEvents[i]!!.pending = false
                        RunScriptList(timeLineEvents[i]!!.event)
                    }
                }
            }
            if (gui!!.Active() && cmd.Length() > 0) {
                // DG: can't just append the command, must separate commands with " ; "
                val pend = gui!!.GetPendingCmd()
                if (pend.Length() > 0) {
                    pend.plusAssign(" ; ")
                }
                gui!!.GetPendingCmd().plusAssign(cmd)
            }
        }

        protected fun RunTimeEvents(time: Int): Boolean {
            if (time - lastTimeRun < USERCMD_MSEC) {
                //common->Printf("Skipping gui time events at %d\n", time);
                return false
            }
            lastTimeRun = time
            UpdateWinVars()
            if (expressionRegisters.Num() != 0 && ops.Num() != 0) {
                EvalRegs()
            }
            if (flags and WIN_INTRANSITION != 0) {
                Transition()
            }
            Time()

            // renamed ON_EVENT to ON_FRAME
            RunScript(ON.ON_FRAME)
            val c = children.Num()
            for (i in 0 until c) {
                children[i]!!.RunTimeEvents(time)
            }
            return true
        }

        /*
         ================
         idHeap::Dump

         dump contents of the heap
         ================
         */
        @Deprecated("")
        protected fun Dump() {
            throw UnsupportedOperationException()
            //            page_s pg;
//
//            for (pg = smallFirstUsedPage; pg; pg = pg.next) {
//                idcommon.Printf("%p  bytes %-8d  (in use by small heap)\n", pg.data, pg.dataSize);
//            }
//
//            if (smallCurPage) {
//                pg = smallCurPage;
//                idcommon.Printf("%p  bytes %-8d  (small heap active page)\n", pg.data, pg.dataSize);
//            }
//
//            for (pg = mediumFirstUsedPage; pg; pg = pg.next) {
//                idcommon.Printf("%p  bytes %-8d  (completely used by medium heap)\n", pg.data, pg.dataSize);
//            }
//
//            for (pg = mediumFirstFreePage; pg; pg = pg.next) {
//                idcommon.Printf("%p  bytes %-8d  (partially used by medium heap)\n", pg.data, pg.dataSize);
//            }
//
//            for (pg = largeFirstUsedPage; pg; pg = pg.next) {
//                idcommon.Printf("%p  bytes %-8d  (fully used by large heap)\n", pg.data, pg.dataSize);
//            }
//
//            idcommon.Printf("pages allocated : %d\n", pagesAllocated);
        }

        protected fun ExpressionTemporary(): Int {
            if (expressionRegisters.Num() == MAX_EXPRESSION_REGISTERS) {
                Common.common.Warning("expressionTemporary: gui %s hit MAX_EXPRESSION_REGISTERS", gui!!.GetSourceFile())
                return 0
            }
            var i = expressionRegisters.Num()
            registerIsTemporary[i] = true
            i = expressionRegisters.Append(0.0f)
            return i
        }

        protected fun ExpressionOp(): wexpOp_t {
            if (ops.Num() == MAX_EXPRESSION_OPS) {
                Common.common.Warning("expressionOp: gui %s hit MAX_EXPRESSION_OPS", gui!!.GetSourceFile())
                return ops[0]
            }
            val wop = wexpOp_t()
            //	memset(&wop, 0, sizeof(wexpOp_t));
            val i = ops.Append(wop)
            return ops[i]
        }

        protected fun EmitOp(
            a: idWinVar?,
            b: Int,
            opType: wexpOpType_t?,
            opp: Array<wexpOp_t?>? = null /*= NULL*/
        ): Int {
            val op: wexpOp_t
            op = ExpressionOp()
            op.opType = opType
            op.a = a
            op.b = b
            op.c = ExpressionTemporary()
            if (opp != null) {
                opp[0] = op
            }
            return op.c
        }

        protected fun ParseEmitOp(
            src: idParser,
            a: idWinVar?,
            opType: wexpOpType_t?,
            priority: Int,
            opp: Array<wexpOp_t?>? = null /*= NULL*/
        ): Int {
            val b = ParseExpressionPriority(src, priority)
            return EmitOp(a, b, opType, opp)
        }

        /*
         ================
         idWindow::ParseTerm

         Returns a register index
         =================
         */
        protected fun ParseTerm(src: idParser, `var`: idWinVar? /*= NULL*/, component: Int /*= 0*/): Int {
            var `var` = `var`
            val token = idToken()
            val a: idWinVar
            var b: Int
            src.ReadToken(token)
            if (token.equals("(")) {
                b = ParseExpression(src)
                src.ExpectTokenString(")")
                return b
            }
            if (0 == token.Icmp("time")) {
                return etoi(wexpRegister_t.WEXP_REG_TIME)
            }

            // parse negative numbers
            if (token.equals("-")) {
                src.ReadToken(token)
                if (token.type == TT_NUMBER || token.equals(".")) {
                    return ExpressionConstant(-token.GetFloatValue())
                }
                src.Warning("Bad negative number '%s'", token)
                return 0
            }
            if (token.type == TT_NUMBER || token.equals(".") || token.equals("-")) {
                return ExpressionConstant(token.GetFloatValue())
            }

            // see if it is a table name
            val table = DeclManager.declManager.FindType(declType_t.DECL_TABLE, token, false) as idDeclTable?
            if (table != null) {
                a = idWinInt(table.Index())
                // parse a table expression
                src.ExpectTokenString("[")
                b = ParseExpression(src)
                src.ExpectTokenString("]")
                return EmitOp(a, b, wexpOpType_t.WOP_TYPE_TABLE)
            }
            if (`var` == null) {
                `var` = GetWinVarByName(token.toString(), true)
            }
            return if (`var` != null) {
                a =  /*(int)*/`var`
                //assert(dynamic_cast<idWinVec4*>(var));
                `var`.Init(token.toString(), this)
                b = component
                if (`var` is idWinVec4) { // if (dynamic_cast < idWinVec4 > (var)) {
                    if (src.ReadToken(token)) {
                        if (token.equals("[")) {
                            b = ParseExpression(src)
                            src.ExpectTokenString("]")
                        } else {
                            src.UnreadToken(token)
                        }
                    }
                    return EmitOp(a, b, wexpOpType_t.WOP_TYPE_VAR)
                } else if (`var` is idWinFloat) { //dynamic_cast < idWinFloat > (var)) {
                    return EmitOp(a, b, wexpOpType_t.WOP_TYPE_VARF)
                } else if (`var` is idWinInt) { //(dynamic_cast < idWinInt > (var)) {
                    return EmitOp(a, b, wexpOpType_t.WOP_TYPE_VARI)
                } else if (`var` is idWinBool) { //(dynamic_cast < idWinBool > (var)) {
                    return EmitOp(a, b, wexpOpType_t.WOP_TYPE_VARB)
                } else if (`var` is idWinStr) { //(dynamic_cast < idWinStr > (var)) {
                    return EmitOp(a, b, wexpOpType_t.WOP_TYPE_VARS)
                } else {
                    src.Warning("Var expression not vec4, float or int '%s'", token)
                }
                0
            } else {
                // ugly but used for post parsing to fixup named vars
                val p = token.toString() //new char[token.Length() + 1];
                //                strcpy(p, token);
//                a = (int) p;
                a = idWinStr(p)
                b = -2
                EmitOp(a, b, wexpOpType_t.WOP_TYPE_VAR)
            }
        }

        /*
         =================
         idWindow::ParseExpressionPriority

         Returns a register index
         =================
         */
        protected fun ParseExpressionPriority(
            src: idParser,
            priority: Int,
            `var`: idWinVar? = null /*= NULL*/,
            component: Int = 0 /*= 0*/
        ): Int {
            val token = idToken()
            val a: idWinInt
            if (priority == 0) {
                return ParseTerm(src, `var`, component)
            }
            a = idWinInt(ParseExpressionPriority(src, priority - 1, `var`, component))
            if (!src.ReadToken(token)) {
                // we won't get EOF in a real file, but we can
                // when parsing from generated strings
                return a.data
            }
            if (priority == 1 && token.equals("*")) {
                return ParseEmitOp(src, a, wexpOpType_t.WOP_TYPE_MULTIPLY, priority)
            }
            if (priority == 1 && token.equals("/")) {
                return ParseEmitOp(src, a, wexpOpType_t.WOP_TYPE_DIVIDE, priority)
            }
            if (priority == 1 && token.equals("%")) {    // implied truncate both to integer
                return ParseEmitOp(src, a, wexpOpType_t.WOP_TYPE_MOD, priority)
            }
            if (priority == 2 && token.equals("+")) {
                return ParseEmitOp(src, a, wexpOpType_t.WOP_TYPE_ADD, priority)
            }
            if (priority == 2 && token.equals("-")) {
                return ParseEmitOp(src, a, wexpOpType_t.WOP_TYPE_SUBTRACT, priority)
            }
            if (priority == 3 && token.equals(">")) {
                return ParseEmitOp(src, a, wexpOpType_t.WOP_TYPE_GT, priority)
            }
            if (priority == 3 && token.equals(">=")) {
                return ParseEmitOp(src, a, wexpOpType_t.WOP_TYPE_GE, priority)
            }
            if (priority == 3 && token.equals("<")) {
                return ParseEmitOp(src, a, wexpOpType_t.WOP_TYPE_LT, priority)
            }
            if (priority == 3 && token.equals("<=")) {
                return ParseEmitOp(src, a, wexpOpType_t.WOP_TYPE_LE, priority)
            }
            if (priority == 3 && token.equals("==")) {
                return ParseEmitOp(src, a, wexpOpType_t.WOP_TYPE_EQ, priority)
            }
            if (priority == 3 && token.equals("!=")) {
                return ParseEmitOp(src, a, wexpOpType_t.WOP_TYPE_NE, priority)
            }
            if (priority == 4 && token.equals("&&")) {
                return ParseEmitOp(src, a, wexpOpType_t.WOP_TYPE_AND, priority)
            }
            if (priority == 4 && token.equals("||")) {
                return ParseEmitOp(src, a, wexpOpType_t.WOP_TYPE_OR, priority)
            }
            if (priority == 4 && token.equals("?")) {
                val oop = arrayOf<wexpOp_t?>(null)
                val o = ParseEmitOp(src, a, wexpOpType_t.WOP_TYPE_COND, priority, oop)
                if (!src.ReadToken(token)) {
                    return o
                }
                if (token.equals(":")) {
                    oop[0]!!.d = idWinInt(ParseExpressionPriority(src, priority - 1, `var`))
                }
                return o
            }

            // assume that anything else terminates the expression
            // not too robust error checking...
            src.UnreadToken(token)
            return a.data
        }

        protected fun EvaluateRegisters(registers: FloatArray) {
            var i: Int
            var b: Int
            var op: wexpOp_t
            val erc = expressionRegisters.Num()
            val oc = ops.Num()
            // copy the constants
            i = etoi(wexpRegister_t.WEXP_REG_NUM_PREDEFINED)
            while (i < erc) {
                registers[i] = expressionRegisters[i]
                i++
            }

            // copy the local and global parameters
            registers[etoi(wexpRegister_t.WEXP_REG_TIME)] = gui!!.GetTime().toFloat()
            i = 0
            while (i < oc) {
                op = ops[i]
                if (op.b == -2) {
                    i++
                    continue
                }
                when (op.opType) {
                    wexpOpType_t.WOP_TYPE_ADD -> registers[op.c] = registers[op.getA()] + registers[op.b]
                    wexpOpType_t.WOP_TYPE_SUBTRACT -> registers[op.c] = registers[op.getA()] - registers[op.b]
                    wexpOpType_t.WOP_TYPE_MULTIPLY -> registers[op.c] = registers[op.getA()] * registers[op.b]
                    wexpOpType_t.WOP_TYPE_DIVIDE -> if (registers[op.b] == 0.0f) {
                        Common.common.Warning("Divide by zero in window '%s' in %s", GetName(), gui!!.GetSourceFile())
                        registers[op.c] = registers[op.getA()]
                    } else {
                        registers[op.c] = registers[op.getA()] / registers[op.b]
                    }

                    wexpOpType_t.WOP_TYPE_MOD -> {
                        b = registers[op.b].toInt()
                        b = if (b != 0) b else 1
                        registers[op.c] = (registers[op.getA()].toInt() % b).toFloat()
                    }

                    wexpOpType_t.WOP_TYPE_TABLE -> {
                        val table =
                            DeclManager.declManager.DeclByIndex(declType_t.DECL_TABLE, op.getA()) as idDeclTable?
                        registers[op.c] = table!!.TableLookup(registers[op.b])
                    }

                    wexpOpType_t.WOP_TYPE_GT -> registers[op.c] =
                        (if (registers[op.getA()] > registers[op.b]) 1 else 0).toFloat()

                    wexpOpType_t.WOP_TYPE_GE -> registers[op.c] =
                        (if (registers[op.getA()] >= registers[op.b]) 1 else 0).toFloat()

                    wexpOpType_t.WOP_TYPE_LT -> registers[op.c] =
                        (if (registers[op.getA()] < registers[op.b]) 1 else 0).toFloat()

                    wexpOpType_t.WOP_TYPE_LE -> registers[op.c] =
                        (if (registers[op.getA()] <= registers[op.b]) 1 else 0).toFloat()

                    wexpOpType_t.WOP_TYPE_EQ -> registers[op.c] =
                        (if (registers[op.getA()] == registers[op.b]) 1 else 0).toFloat()

                    wexpOpType_t.WOP_TYPE_NE -> registers[op.c] =
                        (if (registers[op.getA()] != registers[op.b]) 1 else 0).toFloat()

                    wexpOpType_t.WOP_TYPE_COND -> registers[op.c] =
                        if (registers[op.getA()] != 0.0f) registers[op.b] else registers[op.getD()]

                    wexpOpType_t.WOP_TYPE_AND -> registers[op.c] =
                        (if (registers[op.getA()] != 0.0f && registers[op.b] != 0.0f) 1 else 0).toFloat()

                    wexpOpType_t.WOP_TYPE_OR -> registers[op.c] =
                        (if (registers[op.getA()] != 0.0f || registers[op.b] != 0.0f) 1 else 0).toFloat()

                    wexpOpType_t.WOP_TYPE_VAR -> {
                        if (op.a == null) {
                            registers[op.c] = 0.0f
                        } else if (op.b >= 0 && registers[op.b] >= 0 && registers[op.b] < 4) {
                            // grabs vector components
                            val `var` = op.a as idWinVec4?
                            registers[op.c] = `var`!!.data[registers[op.b].toInt()]
                        } else {
                            registers[op.c] = op.a!!.x()
                        }
                    }

                    wexpOpType_t.WOP_TYPE_VARS -> if (op.a != null) {
                        val `var` = op.a as idWinStr?
                        registers[op.c] = atof(`var`!!.c_str()!!)
                    } else {
                        registers[op.c] = 0.0f
                    }

                    wexpOpType_t.WOP_TYPE_VARF -> if (op.a != null) {
                        val `var` = op.a as idWinFloat?
                        registers[op.c] = `var`!!.data
                    } else {
                        registers[op.c] = 0.0f
                    }

                    wexpOpType_t.WOP_TYPE_VARI -> if (op.a != null) {
                        val `var` = op.a as idWinInt?
                        registers[op.c] = `var`!!.data.toFloat()
                    } else {
                        registers[op.c] = 0.0f
                    }

                    wexpOpType_t.WOP_TYPE_VARB -> if (op.a != null) {
                        val `var` = op.a as idWinBool
                        registers[op.c] = btoi(`var`!!.data).toFloat()
                    } else {
                        registers[op.c] = 0.0f
                    }

                    else -> Common.common.FatalError("R_EvaluateExpression: bad opcode")
                }
                i++
            }
        }

        protected fun SaveExpressionParseState() {
            saveTemps = BooleanArray(MAX_EXPRESSION_REGISTERS)
            //	memcpy(saveTemps, registerIsTemporary, MAX_EXPRESSION_REGISTERS * sizeof(bool));
            System.arraycopy(registerIsTemporary, 0, saveTemps, 0, MAX_EXPRESSION_REGISTERS)
        }

        protected fun RestoreExpressionParseState() {
//	memcpy(registerIsTemporary, saveTemps, MAX_EXPRESSION_REGISTERS * sizeof(bool));
            System.arraycopy(saveTemps, 0, registerIsTemporary, 0, MAX_EXPRESSION_REGISTERS)
            //            Mem_Free(saveTemps);
            saveTemps = null
        }

        protected fun ParseBracedExpression(src: idParser) {
            src.ExpectTokenString("{")
            ParseExpression(src)
            src.ExpectTokenString("}")
        }

        protected fun ParseScriptEntry(name: String?, src: idParser): Boolean {
            for (i in 0 until ON.SCRIPT_COUNT.ordinal) {
                if (Icmp(name!!, ScriptNames[i]) == 0) {
                    // delete scripts[i];
                    scripts[i] = idGuiScriptList()
                    return ParseScript(src, scripts[i])
                }
            }
            return false
        }

        protected fun ParseRegEntry(name: String?, src: idParser): Boolean {
            val work: idStr
            work = idStr(name!!)
            work.ToLower()
            val `var` = GetWinVarByName(work.toString(), false)
            if (`var` != null) {
                for (i in 0 until NumRegisterVars) {
                    if (Icmp(work, RegisterVars[i].name) == 0) {
                        regList.AddReg(work.toString(), etoi(RegisterVars[i].type), src, this, `var`)
                        return true
                    }
                }
            }

            // not predefined so just read the next token and add it to the state
            val tok = idToken()
            var vari = idWinInt()
            var varf = idWinFloat()
            var vars = idWinStr()
            if (src.ReadToken(tok)) {
                if (`var` != null) {
                    `var`.Set(tok)
                    return true
                }
                when (tok.type) {
                    TT_NUMBER -> if (tok.subtype and TT_INTEGER != 0) {
                        vari = idWinInt()
                        vari.set(atoi(tok))
                        vari.SetName(work.toString())
                        definedVars.Append(vari)
                    } else if (tok.subtype and TT_FLOAT != 0) {
                        varf = idWinFloat()
                        varf.set(atof(tok))
                        varf.SetName(work.toString())
                        definedVars.Append(varf)
                    } else {
                        vars = idWinStr()
                        vars.set(tok)
                        vars.SetName(work.toString())
                        definedVars.Append(vars)
                    }

                    else -> {
                        vars = idWinStr()
                        vars.set(tok)
                        vars.SetName(work.toString())
                        definedVars.Append(vars)
                    }
                }
            }
            return true
        }

        protected open fun ParseInternalVar(_name: String?, src: idParser): Boolean {
            if (Icmp(_name!!, "showtime") == 0) {
                if (src.ParseBool()) {
                    flags = flags or WIN_SHOWTIME
                }
                return true
            }
            if (Icmp(_name, "showcoords") == 0) {
                if (src.ParseBool()) {
                    flags = flags or WIN_SHOWCOORDS
                }
                return true
            }
            // DG: added this window flag for Windows that should be scaled to 4:3
            //     (with "empty" bars left/right or above/below)
            if (Icmp(_name, "scaleto43") == 0) {
                val scaleTo43 = src.ParseInt()
                if (scaleTo43 > 0) {
                    flags = flags or WIN_SCALETO43
                } else if (scaleTo43 == 0) {
                    flags = flags or WIN_NO_SCALETO43
                }
                return true
            }
            // DG end
            if (Icmp(_name, "forceaspectwidth") == 0) {
                forceAspectWidth = src.ParseFloat()
                return true
            }
            if (Icmp(_name, "forceaspectheight") == 0) {
                forceAspectHeight = src.ParseFloat()
                return true
            }
            if (Icmp(_name, "matscalex") == 0) {
                matScalex = src.ParseFloat()
                return true
            }
            if (Icmp(_name, "matscaley") == 0) {
                matScaley = src.ParseFloat()
                return true
            }
            if (Icmp(_name, "bordersize") == 0) {
                borderSize = src.ParseFloat()
                return true
            }
            if (Icmp(_name, "nowrap") == 0) {
                if (src.ParseBool()) {
                    flags = flags or WIN_NOWRAP
                }
                return true
            }
            if (Icmp(_name, "shadow") == 0) {
                textShadow = src.ParseInt().toChar()
                return true
            }
            if (Icmp(_name, "textalign") == 0) {
                textAlign = src.ParseInt().toChar()
                return true
            }
            if (Icmp(_name, "textalignx") == 0) {
                textAlignx = src.ParseFloat()
                return true
            }
            if (Icmp(_name, "textaligny") == 0) {
                textAligny = src.ParseFloat()
                return true
            }
            if (Icmp(_name, "shear") == 0) {
                shear.x = src.ParseFloat()
                val tok = idToken()
                src.ReadToken(tok)
                if (tok.Icmp(",") != 0) {
                    src.Error("Expected comma in shear definiation")
                    return false
                }
                shear.y = src.ParseFloat()
                return true
            }
            if (Icmp(_name, "wantenter") == 0) {
                if (src.ParseBool()) {
                    flags = flags or WIN_WANTENTER
                }
                return true
            }
            if (Icmp(_name, "naturalmatscale") == 0) {
                if (src.ParseBool()) {
                    flags = flags or WIN_NATURALMAT
                }
                return true
            }
            if (Icmp(_name, "noclip") == 0) {
                if (src.ParseBool()) {
                    flags = flags or WIN_NOCLIP
                }
                return true
            }
            if (Icmp(_name, "nocursor") == 0) {
                if (src.ParseBool()) {
                    flags = flags or WIN_NOCURSOR
                }
                return true
            }
            if (Icmp(_name, "menugui") == 0) {
                if (src.ParseBool()) {
                    flags = flags or WIN_MENUGUI
                }
                return true
            }
            if (Icmp(_name, "modal") == 0) {
                if (src.ParseBool()) {
                    flags = flags or WIN_MODAL
                }
                return true
            }
            if (Icmp(_name, "invertrect") == 0) {
                if (src.ParseBool()) {
                    flags = flags or WIN_INVERTRECT
                }
                return true
            }
            if (Icmp(_name, "name") == 0) {
                ParseString(src, name)
                return true
            }
            if (Icmp(_name, "play") == 0) {
                Common.common.Warning("play encountered during gui parse.. see Robert\n")
                val playStr = idStr()
                ParseString(src, playStr)
                return true
            }
            if (Icmp(_name, "comment") == 0) {
                ParseString(src, comment)
                return true
            }
            if (Icmp(_name, "font") == 0) {
                val fontStr = idStr()
                ParseString(src, fontStr)
                fontNum = dc!!.FindFont(fontStr.toString()).toChar()
                return true
            }

            //#modified-fva; BEGIN
            if (Icmp(_name, "cstNoClipBackground") == 0) {
                cstNoClipBackground = src.ParseBool()
                return true
            }
            //#modified-fva; END

            return false
        }

        protected fun ParseString(src: idParser, out: idStr?) {
            val tok = idToken()
            if (src.ReadToken(tok)) {
                out!!.set(tok)
            }
        }

        protected fun ParseVec4(src: idParser, out: idVec4) {
            val tok = idToken()
            src.ReadToken(tok)
            out.x = atof(tok)
            src.ExpectTokenString(",")
            src.ReadToken(tok)
            out.y = atof(tok)
            src.ExpectTokenString(",")
            src.ReadToken(tok)
            out.z = atof(tok)
            src.ExpectTokenString(",")
            src.ReadToken(tok)
            out.w = atof(tok)
        }

        protected fun ConvertRegEntry(name: String?, src: idParser?, out: idStr?, tabs: Int) {}
        enum class ADJUST {
            ADJUST_MOVE,

            //= 0,
            ADJUST_TOP,
            ADJUST_RIGHT,
            ADJUST_BOTTOM,
            ADJUST_LEFT,
            ADJUST_TOPLEFT,
            ADJUST_BOTTOMRIGHT,
            ADJUST_TOPRIGHT,
            ADJUST_BOTTOMLEFT
        }

        enum class ON {
            ON_MOUSEENTER,

            //= 0,
            ON_MOUSEEXIT,
            ON_ACTION,
            ON_ACTIVATE,
            ON_DEACTIVATE,
            ON_ESC,
            ON_FRAME,
            ON_TRIGGER,
            ON_ACTIONRELEASE,
            ON_ENTER,
            ON_ENTERRELEASE,
            SCRIPT_COUNT
        }

        companion object {
            val RegisterVars = arrayOf(
                idRegEntry("forecolor", REGTYPE.VEC4),
                idRegEntry("hovercolor", REGTYPE.VEC4),
                idRegEntry("backcolor", REGTYPE.VEC4),
                idRegEntry("bordercolor", REGTYPE.VEC4),
                idRegEntry("rect", REGTYPE.RECTANGLE),
                idRegEntry("matcolor", REGTYPE.VEC4),
                idRegEntry("scale", REGTYPE.VEC2),
                idRegEntry("translate", REGTYPE.VEC2),
                idRegEntry("rotate", REGTYPE.FLOAT),
                idRegEntry("textscale", REGTYPE.FLOAT),
                idRegEntry("visible", REGTYPE.BOOL),
                idRegEntry("noevents", REGTYPE.BOOL),
                idRegEntry("text", REGTYPE.STRING),
                idRegEntry("background", REGTYPE.STRING),
                idRegEntry("runscript", REGTYPE.STRING),
                idRegEntry("varbackground", REGTYPE.STRING),
                idRegEntry("cvar", REGTYPE.STRING),
                idRegEntry("choices", REGTYPE.STRING),
                idRegEntry("choiceVar", REGTYPE.STRING),
                idRegEntry("bind", REGTYPE.STRING),
                //#modified-fva; BEGIN - FIXME: why not at the end of the list?
                idRegEntry("cstLayer", REGTYPE.INT),
                idRegEntry("cstPseudoBit", REGTYPE.INT),
                idRegEntry("cstResetScrollbar", REGTYPE.BOOL),
                idRegEntry("cstWriteTop", REGTYPE.BOOL),
                idRegEntry("cstAnchor", REGTYPE.INT),
                idRegEntry("cstAnchorTo", REGTYPE.INT),
                idRegEntry("cstAnchorFactor", REGTYPE.FLOAT),
                //#modified-fva; END
                idRegEntry("modelRotate", REGTYPE.VEC4),
                idRegEntry("modelOrigin", REGTYPE.VEC4),
                idRegEntry("lightOrigin", REGTYPE.VEC4),
                idRegEntry("lightColor", REGTYPE.VEC4),
                idRegEntry("viewOffset", REGTYPE.VEC4),
                idRegEntry("hideCursor", REGTYPE.BOOL)
            )
            val NumRegisterVars = RegisterVars.size

            //        public static final String[] ScriptNames = new String[SCRIPT_COUNT.ordinal()];
            val ScriptNames = arrayOf(
                "onMouseEnter",
                "onMouseExit",
                "onAction",
                "onActivate",
                "onDeactivate",
                "onESC",
                "onEvent",
                "onTrigger",
                "onActionRelease",
                "onEnter",
                "onEnterRelease"
            )

            //
            protected val gui_debug = idCVar("gui_debug", "0", CVAR_GUI or CVAR_BOOL, "")
            protected val gui_edit = idCVar("gui_edit", "0", CVAR_GUI or CVAR_BOOL, "")

            //#modified-fva; BEGIN
            val cst_hudAdjustAspect = idCVar(
                "cst_hudAdjustAspect",
                "1",
                CVAR_GUI or CVAR_BOOL or CVAR_ARCHIVE,
                "adjust the HUD's aspect when the screen aspect ratio isn't 4:3"
            )

            //#modified-fva; END
            private val dw = drawWin_t()
            private val vec = idVec3(0, 0, 1)

            //
            protected var registerIsTemporary =
                BooleanArray(MAX_EXPRESSION_REGISTERS) // statics to assist during parsing

            /*
         ===============
         idWindow::EvaluateRegisters

         Parameters are taken from the localSpace and the renderView,
         then all expressions are evaluated, leaving the shader registers
         set to their apropriate values.
         ===============
         */

            /*
         ================
         idWindow::GetChild

         Returns the child window at the given index
         ================
         */
            private var actionDownRun = false
            private var actionUpRun = false
            private var buff = "" //[16384];
            private var lastEval: idWindow? = null
            private val org = idVec3()
            private val regs = FloatArray(MAX_EXPRESSION_REGISTERS)
            private val rot = idRotation()
            private val smat = idMat3()

            //
            private val trans = idMat3()
        }
    }
}

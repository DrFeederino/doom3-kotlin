package neo.ui

import neo.Renderer.Material
import neo.Renderer.Material.idMaterial
import neo.TempDump.itob
import neo.framework.DeclManager
import neo.framework.File_h.idFile
import neo.framework.Session.Companion.session
import neo.idlib.Text.Str.idStr
import neo.idlib.Text.Str.idStr.Companion.Icmp
import neo.idlib.colorBlack
import neo.idlib.math.Matrix.idMat3
import neo.idlib.math.Matrix.idMat3.Companion.getMat3_identity
import neo.idlib.math.idRotation
import neo.idlib.math.idVec2
import neo.idlib.math.idVec3
import neo.idlib.math.vec3_origin
import neo.ui.DeviceContext.VIRTUAL_HEIGHT
import neo.ui.DeviceContext.VIRTUAL_WIDTH
import neo.ui.DeviceContext.idDeviceContext
import neo.ui.Rectangle.idRectangle
import neo.ui.UserInterfaceLocal.idUserInterfaceLocal
import neo.ui.Window.idWindow
import neo.ui.Winvar.idWinBackground
import neo.ui.Winvar.idWinBool
import neo.ui.Winvar.idWinFloat
import neo.ui.Winvar.idWinInt
import neo.ui.Winvar.idWinRectangle
import neo.ui.Winvar.idWinStr
import neo.ui.Winvar.idWinVar
import neo.ui.Winvar.idWinVec2
import neo.ui.Winvar.idWinVec4

class SimpleWindow {
    class drawWin_t {
        var simp: idSimpleWindow? = null
        var win: idWindow? = null
    }

    class idSimpleWindow(win: idWindow) {

        //
        var name: idStr
        var backColor = idWinVec4()
        protected var backGroundName = idWinBackground()

        //
        private val _backgroundHolder = arrayOfNulls<idMaterial>(1)
        protected var background: idMaterial?
            get() = _backgroundHolder[0]
            set(value) {
                _backgroundHolder[0] = value
            }
        var borderColor = idWinVec4()
        protected var borderSize: Float
        protected val clientRect = idRectangle() // client area
        protected var dc: idDeviceContext?
        protected val drawRect = idRectangle() // overall rect
        protected var flags: Int
        protected var fontNum: Int
        var foreColor = idWinVec4()
        protected var gui: idUserInterfaceLocal?

        //
        protected var hideCursor = idWinBool()

        //
        protected var mParent: idWindow?
        var matColor = idWinVec4()
        protected var matScalex: Float
        protected var matScaley: Float
        protected val origin: idVec2
        var rect = idWinRectangle() // overall rect
        var rotate = idWinFloat()
        protected var shear = idWinVec2()

        //#modified-fva; BEGIN
        private val cstAnchor: idWinInt = idWinInt()
        private val cstAnchorTo: idWinInt = idWinInt()       // for anchor transitions
        val cstAnchorFactor: idWinFloat = idWinFloat()    // for anchor transitions
        private var cstNoClipBackground: Boolean = true
        //#modified-fva; END

        //
        protected var text = idWinStr()
        protected var textAlign: Int
        protected var textAlignx: Float
        protected var textAligny: Float
        protected val textRect = idRectangle()
        var textScale = idWinFloat()
        protected var textShadow: Int
        protected var visible = idWinBool()

        init {
            gui = win.GetGui()
            dc = win.dc
            drawRect.set(win.drawRect)
            clientRect.set(win.clientRect)
            textRect.set(win.textRect)
            origin = idVec2(win.origin)
            fontNum = win.fontNum.code
            name = idStr(win.name!!)
            matScalex = win.matScalex
            matScaley = win.matScaley
            borderSize = win.borderSize
            textAlign = win.textAlign.code
            textAlignx = win.textAlignx
            textAligny = win.textAligny
            background = win.background
            flags = win.flags
            textShadow = win.textShadow.code
            text.set(win.text)
            visible.set(win.visible)
            rect.set(win.rect)
            backColor.set(win.backColor)
            matColor.set(win.matColor)
            foreColor.set(win.foreColor)
            borderColor.set(win.borderColor)
            textScale.set(win.textScale)
            rotate.set(win.rotate)
            shear.set(win.shear)
            backGroundName.set(win.backGroundName)
            if (backGroundName.Length() != 0) {
                background = DeclManager.declManager.FindMaterial(backGroundName.data)
                background!!.SetSort(Material.SS_GUI.toFloat())
                background!!.SetImageClassifications(1) // just for resource tracking
            }
            backGroundName.SetMaterialPtr(_backgroundHolder)

            // 
            //  added parent
            mParent = win.GetParent()
            // 
            hideCursor.set(win.hideCursor)

            //#modified-fva; BEGIN
            cstAnchor.set(win.cstAnchor)
            cstAnchorTo.set(win.cstAnchorTo)
            cstAnchorFactor.set(win.cstAnchorFactor)
            cstNoClipBackground = win.cstNoClipBackground
            //#modified-fva; END

            val parent = win.GetParent()
            if (parent != null) {
                if (text.NeedsUpdate()) {
                    parent.AddUpdateVar(text)
                }
                if (visible.NeedsUpdate()) {
                    parent.AddUpdateVar(visible)
                }
                if (rect.NeedsUpdate()) {
                    parent.AddUpdateVar(rect)
                }
                if (backColor.NeedsUpdate()) {
                    parent.AddUpdateVar(backColor)
                }
                if (matColor.NeedsUpdate()) {
                    parent.AddUpdateVar(matColor)
                }
                if (foreColor.NeedsUpdate()) {
                    parent.AddUpdateVar(foreColor)
                }
                if (borderColor.NeedsUpdate()) {
                    parent.AddUpdateVar(borderColor)
                }
                if (textScale.NeedsUpdate()) {
                    parent.AddUpdateVar(textScale)
                }
                if (rotate.NeedsUpdate()) {
                    parent.AddUpdateVar(rotate)
                }
                if (shear.NeedsUpdate()) {
                    parent.AddUpdateVar(shear)
                }
                if (backGroundName.NeedsUpdate()) {
                    parent.AddUpdateVar(backGroundName)
                }

                //#modified-fva; BEGIN
                if (cstAnchor.NeedsUpdate()) {
                    parent.AddUpdateVar(cstAnchor)
                }
                if (cstAnchorTo.NeedsUpdate()) {
                    parent.AddUpdateVar(cstAnchorTo)
                }
                if (cstAnchorFactor.NeedsUpdate()) {
                    parent.AddUpdateVar(cstAnchorFactor)
                }
                //#modified-fva; END
            }
        }

        //	virtual			~idSimpleWindow();
        fun Redraw(x: Float, y: Float) {
            if (!visible.data) {
                return
            }

            CalcClientRect(0.0f, 0.0f)
            dc!!.SetFont(fontNum)

            //#modified-fva; BEGIN
            if (mParent != null && mParent!!.cstAnchor.data != DeviceContext.CstAnchor.CST_ANCHOR_NONE.value) {
                cstAnchor.set(mParent!!.cstAnchor)
                cstAnchorTo.set(mParent!!.cstAnchorTo)
                cstAnchorFactor.set(mParent!!.cstAnchorFactor)
            }
            if (!idWindow.cst_hudAdjustAspect.GetBool() || cstAnchor.data == DeviceContext.CstAnchor.CST_ANCHOR_NONE.value) {
                if (mParent != null) {
                    dc!!.SetSize(mParent!!.forceAspectWidth, mParent!!.forceAspectHeight)
                } else {
                    dc!!.SetSize(VIRTUAL_WIDTH.toFloat(), VIRTUAL_HEIGHT.toFloat())
                }
            } else {
                dc!!.CstSetSize(cstAnchor.data, cstAnchorTo.data, cstAnchorFactor.data)
            }
            //#modified-fva; END

            drawRect.Offset(x, y)
            clientRect.Offset(x, y)
            textRect.Offset(x, y)
            SetupTransforms(x, y)

            // fva's cst: added cstNoClipBackground
            if (flags and Window.WIN_NOCLIP != 0 || cstNoClipBackground) {
                dc!!.EnableClipping(false)
            }
            DrawBackground(drawRect)

            //#modified-fva; BEGIN
            if ((flags and Window.WIN_NOCLIP) == 0 && cstNoClipBackground) {
                dc!!.EnableClipping(true)
            }
            //#modified-fva; END

            DrawBorderAndCaption(drawRect)
            if (textShadow != 0) {
                val shadowText = idStr(text.data)
                val shadowRect = idRectangle(textRect)
                shadowText.RemoveColors()
                shadowRect.x += textShadow.toFloat()
                shadowRect.y += textShadow.toFloat()
                dc!!.DrawText(
                    shadowText,
                    textScale.data,
                    textAlign,
                    colorBlack,
                    shadowRect,
                    !itob(flags and Window.WIN_NOWRAP),
                    -1
                )
            }
            dc!!.DrawText(
                text.data,
                textScale.data,
                textAlign,
                foreColor.data,
                textRect,
                !itob(flags and Window.WIN_NOWRAP),
                -1
            )
            dc!!.SetTransformInfo(vec3_origin, getMat3_identity())
            if (flags and Window.WIN_NOCLIP != 0) {
                dc!!.EnableClipping(true)
            }
            drawRect.Offset(-x, -y)
            clientRect.Offset(-x, -y)
            textRect.Offset(-x, -y)
        }

        fun StateChanged(redraw: Boolean) {
            if (redraw && background != null && background!!.CinematicLength() != 0) {
                background!!.UpdateCinematic(gui!!.GetTime())
            }
        }

        fun GetWinVarByName(_name: String?): idWinVar? {
            var retVar: idWinVar? = null
            if (Icmp(_name!!, "background") == 0) {
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
            if (Icmp(_name, "borderColor") == 0) {
                retVar = borderColor
            }
            if (Icmp(_name, "textScale") == 0) {
                retVar = textScale
            }
            if (Icmp(_name, "rotate") == 0) {
                retVar = rotate
            }
            if (Icmp(_name, "shear") == 0) {
                retVar = shear
            }
            if (Icmp(_name, "text") == 0) {
                retVar = text
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

            return retVar
        }

        fun GetWinVarOffset(wv: idWinVar?, owner: drawWin_t?): Int {
            var ret = -1

            if (wv === rect) {
                ret = Window.TransiotonalDataOffset.RECT_OFFSET.offset
            }

            if (wv === backColor) {
                ret = Window.TransiotonalDataOffset.BACKCOLOR_OFFSET.offset
            }

            if (wv === matColor) {
                ret = Window.TransiotonalDataOffset.MATCOLOR_OFFSET.offset
            }

            if (wv === foreColor) {
                ret = Window.TransiotonalDataOffset.FORECOLOR_OFFSET.offset
            }

            if (wv === borderColor) {
                ret = Window.TransiotonalDataOffset.BORDERCOLOR_OFFSET.offset
            }

            if (wv === textScale) {
                ret = Window.TransiotonalDataOffset.TEXTSCALE_OFFSET.offset
            }

            if (wv === rotate) {
                ret = Window.TransiotonalDataOffset.ROTATE_OFFSET.offset
            }

            //#modified-fva; BEGIN
            if (wv === cstAnchorFactor) {
                ret = Window.TransiotonalDataOffset.CSTANCHORFACTOR_OFFSET.offset
            }
            //#modified-fva; END

            if (ret != -1) {
                owner?.simp = this
            }
            return ret
        }

        fun GetParent(): idWindow? {
            return mParent
        }

        fun WriteToSaveGame(savefile: idFile) {
            savefile.WriteInt(flags)
            savefile.Write(drawRect)
            savefile.Write(clientRect)
            savefile.Write(textRect)
            savefile.Write(origin)
            savefile.WriteInt(fontNum)
            savefile.WriteFloat(matScalex)
            savefile.WriteFloat(matScaley)
            savefile.WriteFloat(borderSize)
            savefile.WriteInt(textAlign)
            savefile.WriteFloat(textAlignx)
            savefile.WriteFloat(textAligny)
            savefile.WriteInt(textShadow)
            text.WriteToSaveGame(savefile)
            visible.WriteToSaveGame(savefile)
            rect.WriteToSaveGame(savefile)
            backColor.WriteToSaveGame(savefile)
            matColor.WriteToSaveGame(savefile)
            foreColor.WriteToSaveGame(savefile)
            borderColor.WriteToSaveGame(savefile)
            textScale.WriteToSaveGame(savefile)
            rotate.WriteToSaveGame(savefile)
            shear.WriteToSaveGame(savefile)
            backGroundName.WriteToSaveGame(savefile)

            //#modified-fva; BEGIN // FIXME: savegame version?
            cstAnchor.WriteToSaveGame(savefile)
            cstAnchorTo.WriteToSaveGame(savefile)
            cstAnchorFactor.WriteToSaveGame(savefile)
            savefile.WriteBool(cstNoClipBackground)
            //#modified-fva; END

            val stringLen: Int
            if (background != null) {
                stringLen = background!!.GetName().length
                savefile.WriteInt(stringLen)
                savefile.WriteString(background!!.GetName())
            } else {
                stringLen = 0
                savefile.WriteInt(stringLen)
            }
        }

        fun ReadFromSaveGame(savefile: idFile) {
            flags = savefile.ReadInt()
            savefile.Read(drawRect)
            savefile.Read(clientRect)
            savefile.Read(textRect)
            savefile.Read(origin)
            fontNum = savefile.ReadInt()
            matScalex = savefile.ReadFloat()
            matScaley = savefile.ReadFloat()
            borderSize = savefile.ReadFloat()
            textAlign = savefile.ReadInt()
            textAlignx = savefile.ReadFloat()
            textAligny = savefile.ReadFloat()
            textShadow = savefile.ReadInt()
            text.ReadFromSaveGame(savefile)
            visible.ReadFromSaveGame(savefile)
            rect.ReadFromSaveGame(savefile)
            backColor.ReadFromSaveGame(savefile)
            matColor.ReadFromSaveGame(savefile)
            foreColor.ReadFromSaveGame(savefile)
            borderColor.ReadFromSaveGame(savefile)
            textScale.ReadFromSaveGame(savefile)
            rotate.ReadFromSaveGame(savefile)
            shear.ReadFromSaveGame(savefile)
            backGroundName.ReadFromSaveGame(savefile)

            //#modified-fva; BEGIN
            // TODO: why does this have to be read from the savegame anyway, does it change?
            if (session.GetSaveGameVersion() >= 18) {
                cstAnchor.ReadFromSaveGame(savefile)
                cstAnchorTo.ReadFromSaveGame(savefile)
                cstAnchorFactor.ReadFromSaveGame(savefile)
                cstNoClipBackground = savefile.ReadBool()
            } // else keep default values, I guess
            //#modified-fva; END

            val stringLen: Int = savefile.ReadInt()
            if (stringLen > 0) {
                val backName = idStr()
                backName.Fill(' ', stringLen)
                savefile.Read(backName, stringLen)
                background = DeclManager.declManager.FindMaterial(backName)
                background!!.SetSort(Material.SS_GUI.toFloat())
            } else {
                background = null
            }
        }

        protected fun CalcClientRect(xofs: Float, yofs: Float) {
            drawRect.set(rect.data)
            if (flags and Window.WIN_INVERTRECT != 0) {
                drawRect.x = rect.x() - rect.w()
                drawRect.y = rect.y() - rect.h()
            }
            drawRect.x += xofs
            drawRect.y += yofs
            clientRect.set(drawRect)
            if (rect.h() > 0.0f && rect.w() > 0.0f) {
                if (flags and Window.WIN_BORDER != 0 && borderSize != 0.0f) {
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

        protected fun SetupTransforms(x: Float, y: Float) {
            trans.Identity()
            org.set(origin.x + x, origin.y + y, 0.0f)
            if (rotate.data != 0.0f) {
                rot.Set(org, vec, rotate.data)
                trans.set(rot.ToMat3())
            }
            smat.Identity()
            if (shear.x() != 0.0f || shear.y() != 0.0f) {
                smat.set(0, 1, shear.x())
                smat.set(1, 0, shear.y())
                trans.timesAssign(smat)
            }
            if (!trans.IsIdentity()) {
                dc!!.SetTransformInfo(org, trans)
            }
        }

        protected fun DrawBackground(drawRect: idRectangle) {
            if (backColor.w() > 0) {
                dc!!.DrawFilledRect(drawRect.x, drawRect.y, drawRect.w, drawRect.h, backColor.data)
            }
            if (background != null) {
                if (matColor.w() > 0) {
                    val scaleX: Float
                    val scaleY: Float
                    if (flags and Window.WIN_NATURALMAT != 0) {
                        // DG: now also multiplied with matScalex/y, don't see a reason not to support that
                        //     (it allows scaling a tiled background image)
                        scaleX = (drawRect.w / background!!.GetImageWidth()) * matScalex
                        scaleY = (drawRect.h / background!!.GetImageHeight()) * matScaley
                    } else {
                        scaleX = matScalex
                        scaleY = matScaley
                    }
                    dc!!.DrawMaterial(
                        drawRect.x,
                        drawRect.y,
                        drawRect.w,
                        drawRect.h,
                        background,
                        matColor.data,
                        scaleX,
                        scaleY
                    )
                }
            }
        }

        protected fun DrawBorderAndCaption(drawRect: idRectangle) {
            if (flags and Window.WIN_BORDER != 0) {
                if (borderSize != 0.0f) {
                    dc!!.DrawRect(drawRect.x, drawRect.y, drawRect.w, drawRect.h, borderSize, borderColor.data)
                }
            }
        }

        companion object {
            //	friend class idWindow;
            private val org = idVec3()
            private val rot = idRotation()
            private val smat = idMat3()
            private val vec = idVec3(0, 0, 1)

            //
            private val trans = idMat3()
        }
    }
}

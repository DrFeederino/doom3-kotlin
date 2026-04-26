package neo.ui

import neo.Renderer.Material.idMaterial
import neo.Renderer.glConfig
import neo.Renderer.r_scaleMenusTo43
import neo.Renderer.r_skipGuiShaders
import neo.framework.Common
import neo.framework.DeclManager
import neo.framework.DeclManager.declType_t
import neo.framework.DemoFile.idDemoFile
import neo.framework.FileSystem_h.fileSystem
import neo.framework.File_h.idFile
import neo.framework.KeyInput.idKeyInput.KeysFromBinding
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
import neo.idlib.Text.Token.idToken
import neo.idlib.containers.CBool
import neo.idlib.containers.List.idList
import neo.idlib.math.idVec4
import neo.sys.sysEventType_t
import neo.sys.sysEvent_s
import neo.ui.DeviceContext.VIRTUAL_HEIGHT
import neo.ui.DeviceContext.VIRTUAL_WIDTH
import neo.ui.DeviceContext.idDeviceContext
import neo.ui.ListGUI.idListGUI
import neo.ui.ListGUILocal.idListGUILocal
import neo.ui.Rectangle.idRectangle
import neo.ui.UserInterface.idUserInterface
import neo.ui.UserInterface.idUserInterface.idUserInterfaceManager
import neo.ui.Window.WIN_MENUGUI
import neo.ui.Window.WIN_NO_SCALETO43
import neo.ui.Window.WIN_SCALETO43
import neo.ui.Window.idWindow
import neo.ui.Winvar.idWinStr
import java.nio.ByteBuffer

class UserInterfaceLocal {
    /*
     ===============================================================================

     idUserInterfaceLocal

     ===============================================================================
     */
    class idUserInterfaceLocal : idUserInterface() {
        // friend class idUserInterfaceManagerLocal;
        private var virtualAspectRatio = 0.0f
        private val activateStr = idStr()
        private var active = false
        private var bindHandler: idWindow? = null
        private var cursorX = 0.0f
        private var cursorY = 0.0f
        var desktop: idWindow? = null
        var interactive = false
        private var loading = false
        private val pendingCmd = idStr()

        //
        private val source = idStr()
        private val returnCmd = idStr()

        //
        private val state = idDict()

        //
        private var refs = 1

        //
        private var time = 0
        private val timeStamp = longArrayOf(0)
        private var uniqued = false
        private var lastGlWidth = 0
        private var lastGlHeight = 0

        //
        //
        // ~idUserInterfaceLocal();
        override fun Name(): String {
            return source.toString()
        }

        override fun Comment(): String? {
            return if (desktop != null) {
                desktop!!.GetComment()
            } else ""
        }

        override fun IsInteractive(): Boolean {
            return interactive
        }

        override fun InitFromFile(qpath: String?, rebuild: Boolean /*= true*/, cache: Boolean /*= true*/): Boolean {
            if (!(qpath != null && !qpath.isEmpty())) {
                // FIXME: Memory leak!!
                return false
            }
            loading = true
            if (rebuild || desktop == null) {
                desktop = idWindow(this)
            }
            source.set(qpath)
            state.Set("text", "Test Text!")
            val src =
                idParser(LEXFL_NOFATALERRORS or LEXFL_NOSTRINGCONCAT or LEXFL_ALLOWMULTICHARLITERALS or LEXFL_ALLOWBACKSLASHSTRINGCONCAT)

            //Load the timestamp so reload guis will work correctly
            fileSystem.ReadFile(qpath, null, timeStamp)
            src.LoadFile(qpath)
            if (src.IsLoaded()) {
                val token = idToken()
                while (src.ReadToken(token)) {
                    if (Icmp(token, "windowDef") == 0) {
                        desktop!!.SetDC(UserInterface.uiManagerLocal.dc)
                        if (desktop!!.Parse(src, rebuild)) {
                            desktop!!.SetFlag(Window.WIN_DESKTOP)
                            desktop!!.FixupParms()
                        }
                    }
                }
                state.Set("name", qpath)
            } else {
                desktop!!.SetDC(UserInterface.uiManagerLocal.dc)
                desktop!!.SetFlag(Window.WIN_DESKTOP)
                desktop!!.name = idStr("Desktop")
                desktop!!.text = idWinStr(va("Invalid GUI: %s", qpath))
                desktop!!.rect.set(idRectangle(0.0f, 0.0f, 640.0f, 480.0f))
                desktop!!.drawRect.set(desktop!!.rect.data)
                desktop!!.foreColor.set(idVec4(1.0f, 1.0f, 1.0f, 1.0f))
                desktop!!.backColor.set(idVec4(0.0f, 0.0f, 0.0f, 1.0f))
                desktop!!.SetupFromState()
                Common.common.Warning("Couldn't load gui: '%s'", qpath)
                loading = false
                return false
            }
            interactive = desktop!!.Interactive()
            if (UserInterface.uiManagerLocal.guis.Find(this) == null) {
                UserInterface.uiManagerLocal.guis.Append(this)
            }
            loading = false
            return true
        }

        override fun HandleEvent(event: sysEvent_s, _time: Int, updateVisuals: CBool?): String? {
            time = _time
            if (bindHandler != null && event.evType == sysEventType_t.SE_KEY && event.evValue2 == 1) {
                val ret = bindHandler!!.HandleEvent(event, updateVisuals)
                bindHandler = null
                return ret
            }

            if (event.evType == sysEventType_t.SE_MOUSE || event.evType == sysEventType_t.SE_MOUSE_ABS) {
                if (desktop == null || (desktop!!.GetFlags() and WIN_MENUGUI) != 0) {
                    // DG: this is a fullscreen GUI, scale the mousedelta added to cursorX/Y
                    //     by 640/w, because the GUI pretends that everything is 640x480
                    //     even if the actual resolution is higher => mouse moved too fast
                    var w = glConfig.winWidth
                    var h = glConfig.winHeight
                    if (w <= 0.0f || h <= 0.0f) {
                        w = VIRTUAL_WIDTH.toFloat()
                        h = VIRTUAL_HEIGHT.toFloat()
                    }
                    val realW = w
                    val realH = h

                    if (IsUserInterfaceScaledTo43(this)) {
                        // in case we're scaling menus to 4:3, we need to take that into account
                        // when scaling the mouse events.
                        // no, we can't just call uiManagerLocal.dc.GetFixScaleForMenu() or sth like that,
                        // because when we're here dc.SetMenuScaleFix(true) is not active and it'd just return (1, 1)!
                        var aspectRatio = w / h
                        virtualAspectRatio = (VIRTUAL_WIDTH.toFloat()) / (VIRTUAL_HEIGHT.toFloat()) // 4:3
                        if (aspectRatio > 1.4f) {
                            // widescreen (4:3 is 1.333 3:2 is 1.5, 16:10 is 1.6, 16:9 is 1.7778)
                            // => we need to modify cursorX scaling, by modifying w
                            w *= virtualAspectRatio / aspectRatio
                        } else if (aspectRatio < 1.24f) {
                            // portrait-mode, "thinner" than 5:4 (which is 1.25)
                            // => we need to scale cursorY via h
                            h *= aspectRatio / virtualAspectRatio
                        }
                    }

                    if (event.evType == sysEventType_t.SE_MOUSE) {
                        cursorX += event.evValue * ((VIRTUAL_WIDTH).toFloat() / w)
                        cursorY += event.evValue2 * ((VIRTUAL_HEIGHT).toFloat() / h)
                    } else { // SE_MOUSE_ABS
                        // Note: In case of scaling to 4:3, w and h are already scaled down
                        //       to the 4:3 size that fits into the real resolution.
                        //       Otherwise xOffset/yOffset will just be 0
                        var xOffset = (realW - w) * 0.5f
                        var yOffset = (realH - h) * 0.5f
                        // offset the mouse coordinates into 4:3 area and scale down to 640x480
                        // yes, result could be negative, doesn't matter, code below checks that anyway
                        cursorX = (event.evValue - xOffset) * ((VIRTUAL_WIDTH).toFloat() / w)
                        cursorY = (event.evValue2 - yOffset) * ((VIRTUAL_HEIGHT).toFloat() / h)
                    }
                } else {
                    // not a fullscreen GUI but some ingame thing - no scaling needed
                    cursorX += event.evValue
                    cursorY += event.evValue2
                }

                if (cursorX < 0) {
                    cursorX = 0.0f
                }
                if (cursorY < 0) {
                    cursorY = 0.0f
                }
            }

            return if (desktop != null) {
                desktop!!.HandleEvent(event, updateVisuals)
            } else ""
        }

        override fun HandleNamedEvent(namedEvent: String?) {
            desktop!!.RunNamedEvent(namedEvent)
        }

        override fun Redraw(_time: Int) {
            if (r_skipGuiShaders.GetInteger() > 5) {
                return
            }
            if (!loading && desktop != null) {
                if (desktop!!.GetFlags() and WIN_MENUGUI != 0) {
                    if (MaybeSetCstWinRegs()) {
                        HandleNamedEvent("CstScreenSizeChange")
                    }
                }
                time = _time
                UserInterface.uiManagerLocal.dc.PushClipRect(UserInterface.uiManagerLocal.screenRect)
                desktop!!.Redraw(0.0f, 0.0f)
                UserInterface.uiManagerLocal.dc.PopClipRect()
            }
        }

        fun IsUserInterfaceScaledTo43(ui: idUserInterface?): Boolean {
            if (ui == null) {
                // assert( 0 && "why do you call this without a ui?!" );
                return false
            }

            val uiLocal = ui as? idUserInterfaceLocal
            if (uiLocal == null) {
                return false
            }

            val win = uiLocal.GetDesktop()
            if (win == null) {
                return false
            }

            val winFlags = win.GetFlags()
            return if ((winFlags and WIN_MENUGUI) == 0 || !r_scaleMenusTo43.GetBool()) {
                // if the window is no fullscreen menu (but an ingame menu or noninteractive like the HUD)
                // or scaling menus to 4:3 by default (r_scaleMenusTo43) is disabled,
                // they only get scaled if they explicitly requested it with "scaleto43 1"
                (winFlags and WIN_SCALETO43) != 0
            } else {
                // if it's a fullscreen menu and r_scaleMenusTo43 is enabled,
                // they get scaled to 4:3 unless they explicitly disable it with "scaleto43 0"
                (winFlags and WIN_NO_SCALETO43) == 0
            }
        }

        override fun DrawCursor() {
            val cursorX = floatArrayOf(cursorX)
            val cursorY = floatArrayOf(cursorY)
            if (null == desktop || desktop!!.GetFlags() and WIN_MENUGUI != 0) {
                UserInterface.uiManagerLocal.dc.DrawCursor(cursorX, cursorY, 32.0f)
            } else {
                UserInterface.uiManagerLocal.dc.DrawCursor(cursorX, cursorY, 64.0f)
            }
            this.cursorX = cursorX[0]
            this.cursorY = cursorY[0]
        }

        override fun State(): idDict {
            return state
        }

        override fun DeleteStateVar(varName: String?) {
            state.Delete(varName!!)
        }

        override fun SetStateString(varName: String?, value: String?) {
            state.Set(varName, value!!)
        }

        override fun SetStateBool(varName: String?, value: Boolean) {
            state.SetBool(varName, value)
        }

        override fun SetStateInt(varName: String?, value: Int) {
            state.SetInt(varName, value)
        }

        override fun SetStateFloat(varName: String?, value: Float) {
            state.SetFloat(varName, value)
        }

        // Gets a gui state variable
        override fun GetStateString(varName: String?, defaultString: String? /*= ""*/): String? {
            return state.GetString(varName, defaultString)
        }

        fun GetStateBool(varName: String?, defaultString: String? /*= "0"*/): Boolean {
            return state.GetBool(varName, defaultString!!)
        }

        override fun GetStateInt(varName: String?, defaultString: String? /*= "0"*/): Int {
            return state.GetInt(varName, defaultString!!)
        }

        override fun GetStateFloat(varName: String?, defaultString: String? /*= "0"*/): Float {
            return state.GetFloat(varName, defaultString!!)
        }

        override fun StateChanged(_time: Int, redraw: Boolean) {
            time = _time
            if (desktop != null) {
                // DG: allow game DLLs to set scaleto43 via state
                val scaleTo43 = state.GetInt("scaleto43", "-1")
                if (scaleTo43 > 0) {
                    desktop!!.SetFlag(WIN_SCALETO43)
                    desktop!!.ClearFlag(WIN_NO_SCALETO43)
                } else if (scaleTo43 == 0) {
                    desktop!!.ClearFlag(WIN_SCALETO43)
                    desktop!!.SetFlag(WIN_NO_SCALETO43)
                }
                // DG end
                desktop!!.StateChanged(redraw)
            }
            interactive = if (state.GetBool("noninteractive")) {
                false
            } else {
                if (desktop != null) {
                    desktop!!.Interactive()
                } else {
                    false
                }
            }
        }

        override fun Activate(activate: Boolean, _time: Int): String {
            time = _time
            active = activate
            if (desktop != null) {
                activateStr.set("")
                if (desktop!!.GetFlags() and WIN_MENUGUI != 0) {
                    // DG: calculate and set the "gui::cst*" window register variables
                    MaybeSetCstWinRegs(true)
                }
                desktop!!.Activate(activate, activateStr)
                return activateStr.toString()
            }
            return ""
        }

        override fun Trigger(_time: Int) {
            time = _time
            if (desktop != null) {
                desktop!!.Trigger()
            }
        }

        override fun ReadFromDemoFile(f: idDemoFile) {
//	idStr work;
            f.ReadDict(state)
            source.set(state.GetString("name"))
            if (desktop == null) {
                f.Log("creating new gui\n")
                desktop = idWindow(this)
                desktop!!.SetFlag(Window.WIN_DESKTOP)
                desktop!!.SetDC(UserInterface.uiManagerLocal.dc)
                desktop!!.ReadFromDemoFile(f)
            } else {
                f.Log("re-using gui\n")
                desktop!!.ReadFromDemoFile(f, false)
            }
            cursorX = f.ReadFloat()
            cursorY = f.ReadFloat()
            var add = true
            val c = UserInterface.uiManagerLocal.demoGuis.Num()
            for (i in 0 until c) {
                if (UserInterface.uiManagerLocal.demoGuis[i] == this) {
                    add = false
                    break
                }
            }
            if (add) {
                UserInterface.uiManagerLocal.demoGuis.Append(this)
            }
        }

        override fun WriteToDemoFile(f: idDemoFile) {
//	idStr work;
            f.WriteDict(state)
            if (desktop != null) {
                desktop!!.WriteToDemoFile(f)
            }
            f.WriteFloat(cursorX)
            f.WriteFloat(cursorY)
        }

        override fun WriteToSaveGame(savefile: idFile): Boolean {
            var len: Int
            var kv: idKeyValue?
            var string: String
            val num = state.GetNumKeyVals()
            savefile.WriteInt(num)
            for (i in 0 until num) {
                kv = state.GetKeyVal(i)
                len = kv!!.GetKey().Length()
                string = kv.GetKey().toString()
                savefile.WriteInt(len)
                savefile.WriteStringData(string, len)
                len = kv.GetValue().Length()
                string = kv.GetValue().toString()
                savefile.WriteInt(len)
                savefile.WriteStringData(string, len)

            }
            savefile.WriteBool(active)
            savefile.WriteBool(interactive)
            savefile.WriteBool(uniqued)
            savefile.WriteInt(time)
            len = activateStr.Length()
            savefile.WriteInt(len)
            savefile.WriteStringData(activateStr.toString(), len)
            len = pendingCmd.Length()
            savefile.WriteInt(len)
            savefile.WriteStringData(pendingCmd.toString(), len)
            len = returnCmd.Length()
            savefile.WriteInt(len)
            savefile.WriteStringData(returnCmd.toString(), len)
            savefile.WriteFloat(cursorX)
            savefile.WriteFloat(cursorY)
            desktop!!.WriteToSaveGame(savefile)

            return true
        }

        override fun ReadFromSaveGame(savefile: idFile): Boolean {
            val num: Int
            var i: Int
            var len: Int
            val key = idStr()
            val value = idStr()
            num = savefile.ReadInt()
            state.Clear()
            i = 0
            while (i < num) {
                // Length and filling with empty strings are done in ReadString() when val > 0
                len = savefile.ReadInt()
                key.Fill(' ', len)
                savefile.Read(key, len)
                len = savefile.ReadInt()
                value.Fill(' ', len)
                savefile.Read(value, len)
                state.Set(key, value)
                i++
            }
            active = savefile.ReadBool()
            interactive = savefile.ReadBool()
            uniqued = savefile.ReadBool()
            time = savefile.ReadInt()
            len = savefile.ReadInt()
            activateStr.Fill(' ', len)
            savefile.Read(activateStr, len)
            len = savefile.ReadInt()
            pendingCmd.Fill(' ', len)
            savefile.Read(pendingCmd, len)
            len = savefile.ReadInt()
            returnCmd.Fill(' ', len)
            savefile.Read(returnCmd, len)
            cursorX = savefile.ReadFloat()
            cursorY = savefile.ReadFloat()
            desktop!!.ReadFromSaveGame(savefile)
            return true
        }

        override fun SetKeyBindingNames() {
            if (null == desktop) {
                return
            }
            // walk the windows
            RecurseSetKeyBindingNames(desktop!!)
        }

        override fun IsUniqued(): Boolean {
            return uniqued
        }

        override fun SetUniqued(b: Boolean) {
            uniqued = b
        }

        override fun SetCursor(x: Float, y: Float) {
            cursorX = x
            cursorY = y
        }

        override fun CursorX(): Float {
            return cursorX
        }

        override fun CursorY(): Float {
            return cursorY
        }

        fun GetStateDict(): idDict {
            return state
        }

        fun GetSourceFile(): String {
            return source.toString()
        }

        fun  /*ID_TIME_T*/GetTimeStamp(): LongArray {
            return timeStamp
        }

        fun GetDesktop(): idWindow? {
            return desktop
        }

        fun SetBindHandler(win: idWindow?) {
            bindHandler = win
        }

        fun Active(): Boolean {
            return active
        }

        // DG: used so we can notify GUI scripts about changes in side padding
        private fun MaybeSetCstWinRegs(force: Boolean = false): Boolean {
            if (desktop == null) {
                return false
            }
            val glWidth = glConfig.winWidth
            val glHeight = glConfig.winHeight
            if (glWidth <= 0 || glHeight <= 0 || (!force && glWidth.toInt() == lastGlWidth && glHeight.toInt() == lastGlHeight)) {
                return false
            }
            lastGlWidth = glWidth.toInt()
            lastGlHeight = glHeight.toInt()

            val glAspectRatio = glWidth / glHeight
            val vidAspectRatio = VIRTUAL_WIDTH.toFloat() / VIRTUAL_HEIGHT.toFloat()

            val desktopWidth = desktop!!.forceAspectWidth
            val desktopHeight = desktop!!.forceAspectHeight

            var horizPadding = 0f
            var vertPadding = 0f
            var modWidth = desktopWidth
            var modHeight = desktopHeight

            if (glAspectRatio >= vidAspectRatio) {
                modWidth = desktopHeight * glAspectRatio
                horizPadding = 0.5f * (modWidth - desktopWidth)
            } else {
                modHeight = desktopWidth / glAspectRatio
                vertPadding = 0.5f * (modHeight - desktopHeight)
            }

            SetStateFloat("cstAspectRatio", glAspectRatio)
            SetStateFloat("cstWidth", modWidth)
            SetStateFloat("cstHeight", modHeight)
            SetStateFloat("cstHorPad", horizPadding)
            SetStateFloat("cstVertPad", vertPadding)

            return true
        }

        fun GetTime(): Int {
            return time
        }

        fun SetTime(_time: Int) {
            time = _time
        }

        fun ClearRefs() {
            refs = 0
        }

        fun AddRef() {
            refs++
        }

        fun GetRefs(): Int {
            return refs
        }

        fun Size(): Long {
            return state.Size() + source.Allocated()
        }

        fun RecurseSetKeyBindingNames(window: idWindow) {
            var i: Int
            val v = window.GetWinVarByName("bind")
            if (v != null) {
                SetStateString(v.GetName(), KeysFromBinding(v.GetName()))
            }
            i = 0
            while (i < window.GetChildCount()) {
                val next = window.GetChild(i)
                next?.let { RecurseSetKeyBindingNames(it) }
                i++
            }
        }

        fun GetPendingCmd(): idStr {
            return pendingCmd
        }

        fun GetReturnCmd(): idStr {
            return returnCmd
        }

        override fun GetStateboolean(varName: String?, defaultString: String): Boolean {
            return state.GetBool(varName, defaultString)
        }

        override fun oSet(FindGui: idUserInterface?) {
            throw UnsupportedOperationException("Not supported yet.")
        }

        override fun AllocBuffer(): ByteBuffer {
            throw UnsupportedOperationException("Not supported yet.")
        }

        override fun Read(buffer: ByteBuffer) {
            throw UnsupportedOperationException("Not supported yet.")
        }

        override fun Write(): ByteBuffer {
            throw UnsupportedOperationException("Not supported yet.")
        }
    }

    /*
     ===============================================================================

     idUserInterfaceManagerLocal

     ===============================================================================
     */
    class idUserInterfaceManagerLocal : idUserInterfaceManager() {
        // friend class idUserInterfaceLocal;
        val dc = idDeviceContext()
        val demoGuis = idList<idUserInterfaceLocal?>()
        val guis = idList<idUserInterfaceLocal?>()
        val screenRect = idRectangle()

        //
        //
        override fun Init() {
            screenRect.set(idRectangle(0.0f, 0.0f, 640.0f, 480.0f))
            dc.Init()
        }

        override fun Shutdown() {
            guis.DeleteContents(true)
            demoGuis.DeleteContents(true)
            dc.Shutdown()
        }

        override fun Touch(name: String?) {
            val gui = Alloc()
            gui.InitFromFile(name)
            //	delete gui;
        }

        override fun WritePrecacheCommands(f: idFile) {
            val c = guis.Num()
            for (i in 0 until c) {
                val str = String.format("touchGui %s\n", guis[i]!!.Name())
                Common.common.Printf("%s", str)
                f.Printf("%s", str)
            }
        }

        override fun SetSize(width: Float, height: Float) {
            dc.SetSize(width, height)
        }

        override fun BeginLevelLoad() {
            val c = guis.Num()
            for (i in 0 until c) {
                if (guis[i]!!.GetDesktop()!!.GetFlags() and WIN_MENUGUI == 0) {
                    guis[i]!!.ClearRefs()
                    /*
                     delete guis[ i ];
                     guis.RemoveIndex( i );
                     i--; c--;
                     */
                }
            }
        }

        override fun EndLevelLoad() {
            var c = guis.Num()
            var i = 0
            while (i < c) {
                if (guis[i]!!.GetRefs() == 0) {
                    //common.Printf( "purging %s.\n", guis[i].GetSourceFile() );

                    // use this to make sure no materials still reference this gui
                    var remove = true
                    for (j in 0 until DeclManager.declManager.GetNumDecls(declType_t.DECL_MATERIAL)) {
                        val material =
                            DeclManager.declManager.DeclByIndex(declType_t.DECL_MATERIAL, j, false) as idMaterial?
                        if (material!!.GlobalGui() == guis[i]) {
                            remove = false
                            break
                        }
                    }
                    if (remove) {
//				delete guis[ i ];
                        guis.RemoveIndex(i)
                        i--
                        c--
                    }
                }
                i++
            }
        }

        override fun Reload(all: Boolean) {
            val ts = LongArray(1)
            val c = guis.Num()
            for (i in 0 until c) {
                if (!all) {
                    fileSystem.ReadFile(guis[i]!!.GetSourceFile(), null, ts)
                    if (ts[0] <= guis[i]!!.GetTimeStamp()[0]) {
                        continue
                    }
                }
                guis[i]!!.InitFromFile(guis[i]!!.GetSourceFile())
                Common.common.Printf("reloading %s.\n", guis[i]!!.GetSourceFile())
            }
        }

        override fun ListGuis() {
            val c = guis.Num()
            Common.common.Printf("\n   size   refs   name\n")
            var  /*size_t*/total: Long = 0
            var copies = 0
            var unique = 0
            for (i in 0 until c) {
                val gui = guis[i]!!
                val sz = gui.Size()
                val isUnique = gui.interactive
                if (isUnique) {
                    unique++
                } else {
                    copies++
                }
                Common.common.Printf(
                    "%6.1fk %4d (%s) %s ( %d transitions )\n",
                    sz / 1024.0f,
                    gui.GetRefs(),
                    if (isUnique) "unique" else "copy",
                    gui.GetSourceFile(),
                    gui.desktop!!.NumTransitions()
                )
                total += sz
            }
            Common.common.Printf(
                "===========\n  %d total Guis ( %d copies, %d unique ), %.2f total Mbytes",
                c,
                copies,
                unique,
                total / (1024.0f * 1024.0f)
            )
        }

        override fun CheckGui(qpath: String?): Boolean {
            val file: idFile? = fileSystem.OpenFileRead(qpath!!)
            if (file != null) {
                fileSystem.CloseFile(file)
                return true
            }
            return false
        }

        override fun Alloc(): idUserInterface {
            return idUserInterfaceLocal()
        }

        override fun DeAlloc(gui: idUserInterface?) {
            if (gui != null) {
                val c = guis.Num()
                for (i in 0 until c) {
                    if (guis[i] == gui) {
//				delete guis[i];
                        guis.RemoveIndex(i)
                        return
                    }
                }
            }
        }

        override fun FindGui(
            qpath: String?,
            autoLoad: Boolean /*= false*/,
            needInteractive: Boolean /*= false*/,
            forceUnique: Boolean /*= false*/
        ): idUserInterface? {
            val c = guis.Num()
            for (i in 0 until c) {
                if (0 == Icmp(guis[i]!!.GetSourceFile(), qpath!!)) {
                    if (!forceUnique && (needInteractive || guis[i]!!.IsInteractive())) {
                        break
                    }
                    guis[i]!!.AddRef()
                    return guis[i]
                }
            }
            if (autoLoad) {
                val gui = Alloc()
                if (gui.InitFromFile(qpath)) {
                    gui.SetUniqued(!forceUnique && needInteractive)
                    return gui
                    //                } else {
//			delete gui;
                }
            }
            return null
        }

        override fun FindDemoGui(qpath: String?): idUserInterface? {
            val c = demoGuis.Num()
            for (i in 0 until c) {
                if (0 == Icmp(demoGuis[i]!!.GetSourceFile(), qpath!!)) {
                    return demoGuis[i]
                }
            }
            return null
        }

        override fun AllocListGUI(): idListGUI {
            return idListGUILocal()
        }

        // This is unnecessary.
        override fun FreeListGUI(listgui: idListGUI?) {
//            delete listgui;
//            listgui = null
        }
    }
}

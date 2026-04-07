package neo.ui

import neo.Renderer.Material
import neo.TempDump.atoi
import neo.TempDump.dynamic_cast
import neo.framework.CVarSystem.cvarSystem
import neo.framework.CmdSystem.cmdExecution_t
import neo.framework.CmdSystem.cmdSystem
import neo.framework.Common
import neo.framework.DeclManager
import neo.framework.DemoFile.idDemoFile
import neo.framework.File_h.idFile
import neo.framework.Session
import neo.idlib.Text.Lexer.LEXFL_ALLOWBACKSLASHSTRINGCONCAT
import neo.idlib.Text.Lexer.LEXFL_ALLOWMULTICHARLITERALS
import neo.idlib.Text.Lexer.LEXFL_NOSTRINGCONCAT
import neo.idlib.Text.Parser.idParser
import neo.idlib.Text.Str.idStr
import neo.idlib.Text.Str.idStr.Companion.Cmpn
import neo.idlib.Text.Str.idStr.Companion.Icmp
import neo.idlib.Text.Str.idStr.Companion.Icmpn
import neo.idlib.Text.Token.idToken
import neo.idlib.containers.List.idList
import neo.ui.Rectangle.idRectangle
import neo.ui.SimpleWindow.drawWin_t
import neo.ui.Window.idWindow
import neo.ui.Winvar.idWinBackground
import neo.ui.Winvar.idWinStr
import neo.ui.Winvar.idWinVar

object GuiScript {
    val commandList = arrayOf(
        guiCommandDef_t("set", Script_Set.instance, 2, 999),
        guiCommandDef_t("setFocus", Script_SetFocus.instance, 1, 1),
        guiCommandDef_t("endGame", Script_EndGame.instance, 0, 0),
        guiCommandDef_t("resetTime", Script_ResetTime.instance, 0, 2),
        guiCommandDef_t("showCursor", Script_ShowCursor.instance, 1, 1),
        guiCommandDef_t("resetCinematics", Script_ResetCinematics.instance, 0, 2),
        guiCommandDef_t("transition", Script_Transition.instance, 4, 6),
        guiCommandDef_t("localSound", Script_LocalSound.instance, 1, 1),
        guiCommandDef_t("runScript", Script_RunScript.instance, 1, 1),
        guiCommandDef_t("evalRegs", Script_EvalRegs.instance, 0, 0)
    )
    val scriptCommandCount = commandList.size

    class idGSWinVar {
        var own = false
        var `var`: idWinVar? = null
    }

    class guiCommandDef_t(
        var name: String, var handler: Handler, // void (*handler) (idWindow *window, idList<idGSWinVar> *src);
        var mMinParms: Int, var mMaxParms: Int
    )

    class idGuiScript {
        // friend class idGuiScriptList;
        // friend class idWindow;
        var conditionReg: Int
        var elseList: idGuiScriptList? = null
        var ifList: idGuiScriptList? = null
        private var handler: Handler? = null
        private val parms: idList<idGSWinVar>

        init {
            conditionReg = -1
            parms = idList()
            parms.SetGranularity(2)
        }

        fun Parse(src: idParser): Boolean {
            var i: Int

            // first token should be function call
            // then a potentially variable set of parms
            // ended with a ;
            var token: idToken? = idToken()
            if (!src.ReadToken(token!!)) {
                src.Error("Unexpected end of file")
                return false
            }
            handler = null
            i = 0
            while (i < scriptCommandCount) {
                if (Icmp(token, commandList[i].name) == 0) {
                    handler = commandList[i].handler
                    break
                }
                i++
            }
            if (handler == null) {
                src.Error("Unknown script call %s", token)
            }
            // now read parms til ;
            // all parms are read as idWinStr's but will be fixed up later 
            // to be proper types
            while (true) {
                if (!src.ReadToken(idToken().also { token = it })) {
                    src.Error("Unexpected end of file")
                    return false
                }
                if (Icmp(token!!, ";") == 0) {
                    break
                }
                if (Icmp(token, "}") == 0) {
                    src.UnreadToken(token)
                    break
                }
                val str = idWinStr()
                str.data.set(token)
                val wv = idGSWinVar()
                wv.own = true
                wv.`var` = str
                parms.Append(wv)
            }

            // 
            //  verify min/max params
            if (handler != null && (parms.Num() < commandList[i].mMinParms || parms.Num() > commandList[i].mMaxParms)) {
                src.Error("incorrect number of parameters for script %s", commandList[i].name)
            }
            // 
            return true
        }

        fun Execute(win: idWindow) {
            if (handler != null) {
                handler!!.run(win, parms)
            }
        }

        fun FixupParms(win: idWindow) {
            if (handler == Script_Set.instance) {
                var precacheBackground = false
                var precacheSounds = false
                var str = (parms[0].`var` as idWinStr)
                var dest = win.GetWinVarByName(str.data.toString(), true)
                if (dest != null) {
//			delete parms[0].var;
                    parms[0].`var` = dest
                    parms[0].own = false
                    if (dest is idWinBackground) {
                        precacheBackground = true
                    }
                } else if (Icmp(str.c_str()!!, "cmd") == 0) {
                    precacheSounds = true
                }
                val parmCount = parms.Num()
                for (i in 1 until parmCount) {
                    str = parms[i].`var` as idWinStr
                    if (Icmpn(str.data!!, "gui::", 5) == 0) {

                        //  always use a string here, no point using a float if it is one
                        //  FIXME: This creates duplicate variables, while not technically a problem since they
                        //  are all bound to the same guiDict, it does consume extra memory and is generally a bad thing
                        val defvar = idWinStr()
                        defvar.Init(str.data.toString(), win)
                        win.AddDefinedVar(defvar)
                        //				delete parms[i].var;
                        parms[i].`var` = defvar
                        parms[i].own = false

                        //dest = win.GetWinVarByName(*str, true);
                        //if (dest) {
                        //	delete parms[i].var;
                        //	parms[i].var = dest;
                        //	parms[i].own = false;
                        //}
                        // 
                    } else if (str.data!!.toString().isNotEmpty() && str.data!![0] == '$') {
                        // 
                        //  dont include the $ when asking for variable
                        dest = win.GetGui().GetDesktop()!!.GetWinVarByName(str.c_str()!!.substring(1), true)
                        // 					
                        if (dest != null) {
//					delete parms[i].var;
                            parms[i].`var` = dest
                            parms[i].own = false
                        }
                    } else if (Cmpn(str.c_str()!!, Common.STRTABLE_ID, Common.STRTABLE_ID_LENGTH) == 0) {
                        str.Set(Common.common.GetLanguageDict().GetString(str.c_str()))
                    } else if (precacheBackground) {
                        val mat = DeclManager.declManager.FindMaterial(str.c_str()!!)
                        mat!!.SetSort(Material.SS_GUI.toFloat())
                    } else if (precacheSounds) {
                        // Search for "play <...>"
                        val token = idToken()
                        val parser =
                            idParser(LEXFL_NOSTRINGCONCAT or LEXFL_ALLOWMULTICHARLITERALS or LEXFL_ALLOWBACKSLASHSTRINGCONCAT)
                        parser.LoadMemory(str.c_str()!!, str.Length(), "command")
                        while (parser.ReadToken(token)) {
                            if (token.Icmp("play") == 0) {
                                if (parser.ReadToken(token) && !token.IsEmpty()) {
                                    DeclManager.declManager.FindSound(token)
                                }
                            }
                        }
                    }
                }
            } else if (handler == Script_Transition.instance) {
                if (parms.Num() < 4) {
                    Common.common.Warning(
                        "Window %s in gui %s has a bad transition definition",
                        win.GetName(),
                        win.GetGui().GetSourceFile()
                    )
                }
                var str = (parms[0].`var` as idWinStr?)!!

                // 
                val destOwner = arrayOf<drawWin_t?>(null)
                var dest = win.GetWinVarByName(str.data.toString(), true, destOwner)
                // 
                if (dest != null) {
//			delete parms[0].var;
                    parms[0].`var` = dest
                    parms[0].own = false
                } else {
                    Common.common.Warning(
                        "Window %s in gui %s: a transition does not have a valid destination var %s",
                        win.GetName(),
                        win.GetGui().GetSourceFile(),
                        str.c_str()!!
                    )
                }

                // 
                //  support variables as parameters		
                var c: Int
                c = 1
                while (c < 3) {
                    str = parms[c].`var` as idWinStr
                    val v4 = Winvar.idWinVec4()
                    parms[c].`var` = v4
                    parms[c].own = true
                    val owner = arrayOf<drawWin_t?>(null)
                    dest = if (str.data!![0] == '$') {
                        win.GetWinVarByName(str.c_str()!!.substring(1), true, owner)
                    } else {
                        null
                    }
                    if (dest != null) {
                        var ownerparent: idWindow?
                        var destparent: idWindow?
                        if (owner[0] != null) {
                            ownerparent =
                                if (owner[0]!!.simp != null) owner[0]!!.simp!!.GetParent() else owner[0]!!.win!!.GetParent()
                            destparent =
                                if (destOwner[0]!!.simp != null) destOwner[0]!!.simp!!.GetParent() else destOwner[0]!!.win!!.GetParent()

                            // If its the rectangle they are referencing then adjust it 
                            if (ownerparent != null && destparent != null && dest == if (owner[0]!!.simp != null) owner[0]!!.simp!!.GetWinVarByName(
                                    "rect"
                                ) else owner[0]!!.win!!.GetWinVarByName("rect")
                            ) {
                                val rect = idRectangle()
                                rect.set((dest as Winvar.idWinRectangle).data)
                                ownerparent.ClientToScreen(rect)
                                destparent.ScreenToClient(rect)
                                v4.set(rect.ToVec4())
                            } else {
                                v4.Set(dest.c_str())
                            }
                        } else {
                            v4.Set(dest.c_str())
                        }
                    } else {
                        v4.Set(str.data!!)
                    }
                    c++
                }
                // 
            } else {
                val c = parms.Num()
                for (i in 0 until c) {
                    parms[i].`var`!!.Init(parms[i].`var`!!.c_str(), win)
                }
            }
        }

        fun Size(): Int {
            var sz = 4
            for (i in 0 until parms.Num()) {
                sz += parms[i].`var`!!.Size()
            }
            return sz
        }

        fun WriteToSaveGame(savefile: idFile) {
            var i: Int
            if (ifList != null) {
                ifList!!.WriteToSaveGame(savefile)
            }
            if (elseList != null) {
                elseList!!.WriteToSaveGame(savefile)
            }
            savefile.WriteInt(conditionReg)
            i = 0
            while (i < parms.Num()) {
                if (parms[i].own) {
                    parms[i].`var`!!.WriteToSaveGame(savefile)
                }
                i++
            }
        }

        fun ReadFromSaveGame(savefile: idFile) {
            var i: Int
            if (ifList != null) {
                ifList!!.ReadFromSaveGame(savefile)
            }
            if (elseList != null) {
                elseList!!.ReadFromSaveGame(savefile)
            }
            conditionReg = savefile.ReadInt()
            i = 0
            while (i < parms.Num()) {
                if (parms[i].own) {
                    parms[i].`var`!!.ReadFromSaveGame(savefile)
                }
                i++
            }
        }
    }

    class idGuiScriptList {
        val list: idList<idGuiScript>

        init {
            list = idList()
            list.SetGranularity(4)
        }

        fun Execute(win: idWindow) {
            val c = list.Num()
            for (i in 0 until c) {
                val gs = list[i]
                if (gs.conditionReg >= 0) {
                    if (win.HasOps()) {
                        val f = win.EvalRegs(gs.conditionReg)
                        if (f != 0.0f) {
                            if (gs.ifList != null) {
                                win.RunScriptList(gs.ifList)
                            }
                        } else if (gs.elseList != null) {
                            win.RunScriptList(gs.elseList)
                        }
                    }
                }
                gs.Execute(win)
            }
        }

        fun Append(gs: idGuiScript) {
            list.Append(gs)
        }

        fun  /*size_t*/Size(): Int {
            var sz = 4
            for (i in 0 until list.Num()) {
                sz += list[i].Size()
            }
            return sz
        }

        fun FixupParms(win: idWindow) {
            val c = list.Num()
            for (i in 0 until c) {
                val gs = list[i]
                gs.FixupParms(win)
                if (gs.ifList != null) {
                    gs.ifList!!.FixupParms(win)
                }
                if (gs.elseList != null) {
                    gs.elseList!!.FixupParms(win)
                }
            }
        }

        fun ReadFromDemoFile(f: idDemoFile?) {}
        fun WriteToDemoFile(f: idDemoFile?) {}
        fun WriteToSaveGame(savefile: idFile) {
            var i: Int
            i = 0
            while (i < list.Num()) {
                list[i].WriteToSaveGame(savefile)
                i++
            }
        }

        fun ReadFromSaveGame(savefile: idFile) {
            var i: Int
            i = 0
            while (i < list.Num()) {
                list[i].ReadFromSaveGame(savefile)
                i++
            }
        }
    }

    abstract class Handler {
        abstract fun run(window: idWindow, src: idList<idGSWinVar>)
    }

    /*
     =========================
     Script_Set
     =========================
     */
    internal class Script_Set private constructor() : Handler() {
        override fun run(window: idWindow, src: idList<idGSWinVar>) {
            var `val`: String?
            var dest = dynamic_cast(idWinStr::class.java, src[0].`var`) as idWinStr?
            if (dest != null) {
                if (Icmp(dest.data!!, "cmd") == 0) {
                    dest = src[1].`var` as idWinStr
                    val parmCount = src.Num()
                    if (parmCount > 2) {
                        `val` = dest.c_str()
                        var i = 2
                        while (i < parmCount) {
                            `val` += " \""
                            `val` += src[i].`var`!!.c_str()
                            `val` += "\""
                            i++
                        }
                        window.AddCommand(`val`)
                    } else {
                        window.AddCommand(dest.data.toString())
                    }
                    return
                }

                // DG: allow debugprinting to the console with `set "print" "this windowDefs rect:" "$rect"`
                if (Icmp(dest.data!!, "print") == 0) {
                    val msg = idStr()
                    val parmCount = src.Num()
                    for (i in 1 until parmCount) {
                        msg.plusAssign(src[i].`var`!!.c_str()!!)
                        msg.plusAssign(" ")
                    }
                    Common.common.Printf("GUI debug: %s\n", msg)
                    return
                }
            }
            src[0].`var`!!.Set(src[1].`var`!!.c_str())
            src[0].`var`!!.SetEval(false)
        }

        companion object {
            val instance: Handler = Script_Set()
        }
    }

    /*
     =========================
     Script_SetFocus
     =========================
     */
    internal class Script_SetFocus private constructor() : Handler() {
        override fun run(window: idWindow, src: idList<idGSWinVar>) {
            val parm = src[0].`var` as idWinStr?
            if (parm != null) {
                val win = window.GetGui().GetDesktop()!!.FindChildByName(parm.data.toString())
                if (win != null && win.win != null) {
                    window.SetFocus(win.win)
                }
            }
        }

        companion object {
            val instance: Handler = Script_SetFocus()
        }
    }

    /*
     =========================
     Script_ShowCursor
     =========================
     */
    internal class Script_ShowCursor private constructor() : Handler() {
        override fun run(window: idWindow, src: idList<idGSWinVar>) {
            val parm = src[0].`var` as idWinStr?
            if (parm != null) {
                if (parm.data.toString().toInt() != 0) {
                    window.GetGui().GetDesktop()!!.ClearFlag(Window.WIN_NOCURSOR)
                } else {
                    window.GetGui().GetDesktop()!!.SetFlag(Window.WIN_NOCURSOR)
                }
            }
        }

        companion object {
            val instance: Handler = Script_ShowCursor()
        }
    }

    /*
     =========================
     Script_RunScript

     run scripts must come after any set cmd set's in the script
     =========================
     */
    internal class Script_RunScript private constructor() : Handler() {
        override fun run(window: idWindow, src: idList<idGSWinVar>) {
            val parm = src[0].`var` as idWinStr?
            if (parm != null) {
                var str: String? = window.cmd.toString()
                str += " ; runScript "
                str += parm.c_str()
                window.cmd.set(str)
            }
        }

        companion object {
            val instance: Handler = Script_RunScript()
        }
    }

    /*
     =========================
     Script_LocalSound
     =========================
     */
    internal class Script_LocalSound private constructor() : Handler() {
        override fun run(window: idWindow, src: idList<idGSWinVar>) {
            val parm = src[0].`var` as idWinStr?
            if (parm != null) {
                Session.session.sw.PlayShaderDirectly(parm.data.toString())
            }
        }

        companion object {
            val instance: Handler = Script_LocalSound()
        }
    }

    /*
     =========================
     Script_EvalRegs
     =========================
     */
    internal class Script_EvalRegs private constructor() : Handler() {
        override fun run(window: idWindow, src: idList<idGSWinVar>) {
            window.EvalRegs(-1, true)
        }

        companion object {
            val instance: Handler = Script_EvalRegs()
        }
    }

    /*
     =========================
     Script_EndGame
     =========================
     */
    internal class Script_EndGame private constructor() : Handler() {
        override fun run(window: idWindow, src: idList<idGSWinVar>) {
            cvarSystem.SetCVarBool("g_nightmare", true)
            cmdSystem.BufferCommandText(cmdExecution_t.CMD_EXEC_APPEND, "disconnect\n")
        }

        companion object {
            val instance: Handler = Script_EndGame()
        }
    }

    /*
     =========================
     Script_ResetTime
     =========================
     */
    internal class Script_ResetTime private constructor() : Handler() {
        override fun run(window: idWindow, src: idList<idGSWinVar>) {
            var parm = src[0].`var` as idWinStr?
            var win: drawWin_t? = null
            if (parm != null && src.Num() > 1) {
                val targetName = parm.data.toString()
                win = window.GetGui().GetDesktop()!!.FindChildByName(targetName)
                parm = src[1].`var` as idWinStr
            }
            if (win != null && win.win != null) {
                val t = parm!!.data.toString().toInt()
                win.win!!.ResetTime(t)
                win.win!!.EvalRegs(-1, true)
            } else {
                window.ResetTime(parm!!.data.toString().toInt())
                window.EvalRegs(-1, true)
            }
        }

        companion object {
            val instance: Handler = Script_ResetTime()
        }
    }

    /*
     =========================
     Script_ResetCinematics
     =========================
     */
    internal class Script_ResetCinematics private constructor() : Handler() {
        override fun run(window: idWindow, src: idList<idGSWinVar>) {
            window.ResetCinematics()
        }

        companion object {
            val instance: Handler = Script_ResetCinematics()
        }
    }

    /*
     =========================
     Script_Transition
     =========================
     */
    internal class Script_Transition private constructor() : Handler() {
        override fun run(window: idWindow, src: idList<idGSWinVar>) {
            // transitions always affect rect or vec4 vars
            if (src.Num() >= 4) {
                var rect: Winvar.idWinRectangle? = null
                val vec4 = dynamic_cast(Winvar.idWinVec4::class.java, src[0].`var`) as Winvar.idWinVec4?
                // 
                //  added float variable
                var `val`: Winvar.idWinFloat? = null
                // 
                if (null == vec4) {
                    rect = dynamic_cast(Winvar.idWinRectangle::class.java, src[0].`var`) as Winvar.idWinRectangle?
                    // 
                    //  added float variable					
                    if (null == rect) {
                        `val` = dynamic_cast(Winvar.idWinFloat::class.java, src[0].`var`) as Winvar.idWinFloat?
                    }
                    // 
                }
                val from = dynamic_cast(Winvar.idWinVec4::class.java, src[1].`var`) as Winvar.idWinVec4?
                val to = dynamic_cast(Winvar.idWinVec4::class.java, src[2].`var`) as Winvar.idWinVec4?
                val timeStr = dynamic_cast(idWinStr::class.java, src[3].`var`) as idWinStr?
                // 
                //  added float variable					
                if (!((vec4 != null || rect != null || `val` != null) && from != null && to != null && timeStr != null)) {
                    // 
                    Common.common.Warning(
                        "Bad transition in gui %s in window %s\n",
                        window.GetGui().GetSourceFile(),
                        window.GetName()
                    )
                    return
                }
                val time = atoi(timeStr.data.toString())
                var ac = 0.0f
                var dc = 0.0f
                if (src.Num() > 4) {
                    val acv = dynamic_cast(idWinStr::class.java, src[4].`var`) as idWinStr?
                    val dcv = dynamic_cast(idWinStr::class.java, src[5].`var`) as idWinStr?
                    assert(acv != null && dcv != null)
                    ac = acv!!.data.toString().toFloat()
                    dc = dcv!!.data.toString().toFloat()
                }
                if (vec4 != null) {
                    vec4.SetEval(false)
                    window.AddTransition(vec4, from.data, to.data, time, ac, dc)
                    // 
                    //  added float variable					
                } else if (`val` != null) {
                    `val`.SetEval(false)
                    window.AddTransition(`val`, from.data, to.data, time, ac, dc)
                    // 
                } else {
                    rect!!.SetEval(false)
                    window.AddTransition(rect, from.data, to.data, time, ac, dc)
                }
                window.StartTransition()
            }
        }

        companion object {
            val instance: Handler = Script_Transition()
        }
    }
}

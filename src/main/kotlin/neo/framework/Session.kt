package neo.framework

import neo.Game.Game_local
import neo.Renderer.ModelManager
import neo.Renderer.RenderWorld.idRenderWorld
import neo.Sound.snd_system
import neo.Sound.sound.idSoundWorld
import neo.TempDump
import neo.TempDump.SERiAL
import neo.TempDump.TODO_Exception
import neo.framework.Async.AsyncNetwork.idAsyncNetwork
import neo.framework.CmdSystem.cmdExecution_t
import neo.framework.CmdSystem.cmdFunction_t
import neo.framework.DemoFile.idDemoFile
import neo.framework.FileSystem_h.backgroundDownload_s
import neo.framework.FileSystem_h.findFile_t
import neo.framework.Session_local.idSessionLocal
import neo.framework.Session_local.timeDemo_t
import neo.idlib.CmdArgs
import neo.idlib.Text.Str
import neo.idlib.Text.Str.idStr
import neo.idlib.idException
import neo.sys.sys_public.sysEvent_s
import neo.sys.win_main
import neo.sys.win_main.Sys_EnterCriticalSection
import neo.sys.win_main.Sys_LeaveCriticalSection
import neo.ui.UserInterface
import neo.ui.UserInterface.idUserInterface
import java.nio.ByteBuffer

class Session {
    fun RandomizeStack() {
        // attempt to force uninitialized stack memory bugs
        val bytes = 4000000
        val buf = ByteArray(bytes)
        val fill = (Math.random().toInt() and 255).toByte()
        for (i in 0 until bytes) {
            buf[i] = fill
        }
    }

    enum class msgBoxType_t {
        MSG_OK, MSG_ABORT, MSG_OKCANCEL, MSG_YESNO, MSG_PROMPT, MSG_CDKEY, MSG_INFO, MSG_WAIT
    }

    //
    // needed by the gui system for the load game menu
    class logStats_t : SERiAL {
        var combat = 0
        var health = 0
        var heartRate = 0.0f
        var stamina = 0

        override fun AllocBuffer(): ByteBuffer {
            throw TODO_Exception()
        }

        override fun Read(buffer: ByteBuffer) {
            throw TODO_Exception()
        }

        override fun Write(): ByteBuffer {
            throw TODO_Exception()
        }

        companion object {
            @Transient
            val SIZE = TempDump.SERIAL_SIZE(logStats_t())
        }
    }

    abstract class HandleGuiCommand_t {
        abstract fun run(input: String): String
    }

    abstract class idSession {
        // The renderer and sound system will write changes to writeDemo.
        // Demos can be recorded and played at the same time when splicing.

        var readDemo: idDemoFile? = null


        var renderdemoVersion = 0

        // The render world and sound world used for this session.
        lateinit var rw: idRenderWorld
        lateinit var sw: idSoundWorld


        var writeDemo: idDemoFile? = null

        //	public abstract			~idSession() {}
        // Called in an orderly fashion at system startup,
        // so commands, cvars, files, etc are all available.
        @Throws(idException::class)
        abstract fun Init()

        // Shut down the session.
        abstract fun Shutdown()

        // Called on errors and game exits.
        abstract fun Stop()

        // Redraws the screen, handling games, guis, console, etc
        // during normal once-a-frame updates, outOfSequence will be false,
        // but when the screen is updated in a modal manner, as with utility
        // output, the mouse cursor will be released if running windowed.
        abstract fun UpdateScreen( /*boolean outOfSequence = true*/)
        abstract fun UpdateScreen(outOfSequence: Boolean)

        // Called when console prints happen, allowing the loading screen
        // to redraw if enough time has passed.
        abstract fun PacifierUpdate()

        // Called every frame, possibly spinning in place if we are
        // above maxFps, or we haven't advanced at least one demo frame.
        // Returns the number of milliseconds since the last frame.
        @Throws(idException::class)
        abstract fun Frame()

        // Returns true if a multiplayer game is running.
        // CVars and commands are checked differently in multiplayer mode.
        abstract fun IsMultiplayer(): Boolean

        // Processes the given event.
        @Throws(idException::class)
        abstract fun ProcessEvent(event: sysEvent_s): Boolean

        // Activates the main menu
        @Throws(idException::class)
        abstract fun StartMenu(playIntro: Boolean)

        @Throws(idException::class)
        fun StartMenu( /*boolean playIntro = false*/) {
            StartMenu(false)
        }

        abstract fun SetGUI(gui: idUserInterface?, handle: HandleGuiCommand_t?)

        // Updates gui and dispatched events to it
        abstract fun GuiFrameEvents()

        // fires up the optional GUI event, also returns them if you set wait to true
        // if MSG_PROMPT and wait, returns the prompt string or NULL if aborted
        // if MSG_CDKEY and want, returns the cd key or NULL if aborted
        // network tells wether one should still run the network loop in a wait dialog
        abstract fun MessageBox(
            type: msgBoxType_t,
            message: String /*, final String title = NULL, boolean wait = false, final String fire_yes = NULL, final String fire_no = NULL, boolean network = false*/
        ): String

        abstract fun MessageBox(
            type: msgBoxType_t,
            message: String,
            title: String /*, boolean wait = false, final String fire_yes = NULL, final String fire_no = NULL, boolean network = false*/
        ): String

        abstract fun MessageBox(
            type: msgBoxType_t,
            message: String,
            title: String?,
            wait: Boolean /*, final String fire_yes = NULL, final String fire_no = NULL, boolean network = false*/
        ): String

        abstract fun MessageBox(
            type: msgBoxType_t,
            message: String,
            title: String,
            wait: Boolean,
            fire_yes: String /*, final String fire_no = NULL, boolean network = false*/
        ): String

        abstract fun MessageBox(
            type: msgBoxType_t,
            message: String,
            title: String,
            wait: Boolean,
            fire_yes: String,
            fire_no: String /*, boolean network = false*/
        ): String

        abstract fun MessageBox(
            type: msgBoxType_t,
            message: String,
            title: String?,
            wait: Boolean,
            fire_yes: String?,
            fire_no: String?,
            network: Boolean
        ): String?

        abstract fun StopBox()

        // monitor this download in a progress box to either abort or completion
        abstract fun DownloadProgressBox(
            bgl: backgroundDownload_s,
            title: String /*, int progress_start = 0, int progress_end = 100*/
        )

        abstract fun DownloadProgressBox(
            bgl: backgroundDownload_s,
            title: String,
            progress_start: Int /*= 0, int progress_end = 100*/
        )

        abstract fun DownloadProgressBox(
            bgl: backgroundDownload_s,
            title: String,
            progress_start: Int,
            progress_end: Int
        )

        abstract fun SetPlayingSoundWorld()

        // this is used by the sound system when an OnDemand sound is loaded, so the game action
        // doesn't advance and get things out of sync
        abstract fun TimeHitch(msec: Int)

        // read and write the cd key data to files
        // doesn't perform any validity checks
        abstract fun ReadCDKey()
        abstract fun WriteCDKey()

        // returns NULL for if xp is true and xp key is not valid or not present
        abstract fun GetCDKey(xp: Boolean): String? //TODO:string pointer?

        // check keys for validity when typed in by the user ( with checksum verification )
        // store the new set of keys if they are found valid
        abstract fun CheckKey(key: String, netConnect: Boolean, offline_valid: BooleanArray /*[ 2 ]*/): Boolean

        // verify the current set of keys for validity
        // strict -> keys in state CDKEY_CHECKING state are not ok
        abstract fun CDKeysAreValid(strict: Boolean): Boolean

        // wipe the key on file if the network check finds it invalid
        abstract fun ClearCDKey(valid: BooleanArray /*[ 2 ]*/)

        // configure gui variables for mainmenu.gui and cd key state
        abstract fun SetCDKeyGuiVars()
        abstract fun WaitingForGameAuth(): Boolean

        // got reply from master about the keys. if !valid, auth_msg given
        abstract fun CDKeysAuthReply(valid: Boolean, auth_msg: String?)
        abstract fun GetCurrentMapName(): String
        abstract fun GetSaveGameVersion(): Int
    }

    /*
     =================
     Session_RescanSI_f
     =================
     */
    internal class Session_RescanSI_f : cmdFunction_t() {
        @Throws(idException::class)
        override fun run(args: CmdArgs.idCmdArgs?) {
            sessLocal.mapSpawnData.serverInfo.set(CVarSystem.cvarSystem.MoveCVarsToDict(CVarSystem.CVAR_SERVERINFO))
            if (Game_local.game != null && idAsyncNetwork.server.IsActive()) {
                Game_local.game.SetServerInfo(sessLocal.mapSpawnData.serverInfo)
            }
        }

        companion object {
            private val instance: cmdFunction_t = Session_RescanSI_f()
            fun getInstance(): cmdFunction_t {
                return instance
            }
        }
    }

    /*
     ==================
     Session_Map_f

     Restart the server on a different map
     ==================
     */
    internal class Session_Map_f : cmdFunction_t() {
        @Throws(idException::class)
        override fun run(args: CmdArgs.idCmdArgs?) {
            val map: idStr
            val string: String
            val ff: findFile_t
            val rl_args = CmdArgs.idCmdArgs()
            map = idStr(args!!.Argv(1))
            if (0 == map.Length()) {
                return
            }
            map.StripFileExtension()

            // make sure the level exists before trying to change, so that
            // a typo at the server console won't end the game
            // handle addon packs through reloadEngine
            string = String.format("maps/%s.map", map)
            ff = FileSystem_h.fileSystem.FindFile(string, true)
            when (ff) {
                findFile_t.FIND_NO -> {
                    Common.common.Printf("Can't find map %s\n", string)
                    return
                }

                findFile_t.FIND_ADDON -> {
                    Common.common.Printf("map %s is in an addon pak - reloading\n", string)
                    rl_args.AppendArg("map")
                    rl_args.AppendArg(map.toString())
                    CmdSystem.cmdSystem.SetupReloadEngine(rl_args)
                    return
                }

                else -> {}
            }
            CVarSystem.cvarSystem.SetCVarBool("developer", false)
            sessLocal.StartNewGame(map.toString(), true)
        }

        companion object {
            private val instance: cmdFunction_t = Session_Map_f()
            fun getInstance(): cmdFunction_t {
                return instance
            }
        }
    }

    /*
     ==================
     Session_DevMap_f

     Restart the server on a different map in developer mode
     ==================
     */
    internal class Session_DevMap_f : cmdFunction_t() {
        @Throws(idException::class)
        override fun run(args: CmdArgs.idCmdArgs?) {
            val map: idStr
            val string: String
            val ff: findFile_t
            val rl_args = CmdArgs.idCmdArgs()
            map = idStr(args!!.Argv(1))
            if (0 == map.Length()) {
                return
            }
            map.StripFileExtension()

            // make sure the level exists before trying to change, so that
            // a typo at the server console won't end the game
            // handle addon packs through reloadEngine
            string = String.format("maps/%s.map", map)
            ff = FileSystem_h.fileSystem.FindFile(string, true)
            when (ff) {
                findFile_t.FIND_NO -> {
                    Common.common.Printf("Can't find map %s\n", string)
                    return
                }

                findFile_t.FIND_ADDON -> {
                    Common.common.Printf("map %s is in an addon pak - reloading\n", string)
                    rl_args.AppendArg("devmap")
                    rl_args.AppendArg(map.toString())
                    CmdSystem.cmdSystem.SetupReloadEngine(rl_args)
                    return
                }

                else -> {}
            }
            CVarSystem.cvarSystem.SetCVarBool("developer", true)
            sessLocal.StartNewGame(map.toString(), true)
        }

        companion object {
            private val instance: cmdFunction_t = Session_DevMap_f()
            fun getInstance(): cmdFunction_t {
                return instance
            }
        }
    }

    /*
     ==================
     Session_TestMap_f
     ==================
     */
    internal class Session_TestMap_f : cmdFunction_t() {
        @Throws(idException::class)
        override fun run(args: CmdArgs.idCmdArgs?) {
            val map: idStr
            var string: String
            map = idStr(args!!.Argv(1))
            if (0 == map.Length()) {
                return
            }
            map.StripFileExtension()
            CmdSystem.cmdSystem.BufferCommandText(cmdExecution_t.CMD_EXEC_NOW, "disconnect")
            string = String.format("dmap maps/%s.map", map)
            CmdSystem.cmdSystem.BufferCommandText(cmdExecution_t.CMD_EXEC_NOW, string)
            string = String.format("devmap %s", map) //TODO:can this shit format char*?
            CmdSystem.cmdSystem.BufferCommandText(cmdExecution_t.CMD_EXEC_NOW, string)
        }

        companion object {
            private val instance: cmdFunction_t = Session_TestMap_f()
            fun getInstance(): cmdFunction_t {
                return instance
            }
        }
    }

    /*
     ==================
     Sess_WritePrecache_f
     ==================
     */
    internal class Sess_WritePrecache_f : cmdFunction_t() {
        @Throws(idException::class)
        override fun run(args: CmdArgs.idCmdArgs?) {
            if (args!!.Argc() != 2) {
                Common.common.Printf("USAGE: writePrecache <execFile>\n")
                return
            }
            val str = idStr(args.Argv(1))
            str.DefaultFileExtension(".cfg")
            val f = FileSystem_h.fileSystem.OpenFileWrite(str.toString())
            DeclManager.declManager.WritePrecacheCommands(f!!)
            ModelManager.renderModelManager.WritePrecacheCommands(f)
            UserInterface.uiManager.WritePrecacheCommands(f)
            FileSystem_h.fileSystem.CloseFile(f)
        }

        companion object {
            private val instance: cmdFunction_t = Sess_WritePrecache_f()
            fun getInstance(): cmdFunction_t {
                return instance
            }
        }
    }

    internal class Session_PromptKey_f : cmdFunction_t() {
        @Throws(idException::class)
        override fun run(args: CmdArgs.idCmdArgs?) {
            BooleanArray(2)
            if (recursed) {
                Common.common.Warning("promptKey recursed - aborted")
                return
            }
            recursed = true
            //HACKME::5:disable the serial messageBox
//            do {
//                // in case we're already waiting for an auth to come back to us ( may happen exceptionally )
//                if (sessLocal.MaybeWaitOnCDKey()) {
//                    if (sessLocal.CDKeysAreValid(true)) {
//                        recursed = false;
//                        return;
//                    }
//                }
//                // the auth server may have replied and set an error message, otherwise use a default
//                String prompt_msg = sessLocal.GetAuthMsg();
//                if (prompt_msg.isEmpty()/*[ 0 ] == '\0'*/) {
//                    prompt_msg = common.GetLanguageDict().GetString("#str_04308");
//                }
////                for (int d = 0; d < common.GetLanguageDict().args.Size(); d++) {
////                    LangDict.idLangKeyValue bla = common.GetLanguageDict().args.get(d);
////                    System.out.println(bla.key + " >>> " + bla.value);
////                }
//                retkey = sessLocal.MessageBox(MSG_CDKEY, prompt_msg, common.GetLanguageDict().GetString("#str_04305"), true, null, null, true);
//                if (retkey != null) {
//                    if (sessLocal.CheckKey(retkey, false, valid)) {
//                        // if all went right, then we may have sent an auth request to the master ( unless the prompt is used during a net connect )
//                        boolean canExit = true;
//                        if (sessLocal.MaybeWaitOnCDKey()) {
//                            // wait on auth reply, and got denied, prompt again
//                            if (!sessLocal.CDKeysAreValid(true)) {
//                                // server says key is invalid - MaybeWaitOnCDKey was interrupted by a CDKeysAuthReply call, which has set the right error message
//                                // the invalid keys have also been cleared in the process
//                                sessLocal.MessageBox(MSG_OK, sessLocal.GetAuthMsg(), common.GetLanguageDict().GetString("#str_04310"), true, null, null, true);
//                                canExit = false;
//                            }
//                        }
//                        if (canExit) {
//                            // make sure that's saved on file
//                            sessLocal.WriteCDKey();
//                            sessLocal.MessageBox(MSG_OK, common.GetLanguageDict().GetString("#str_04307"), common.GetLanguageDict().GetString("#str_04305"), true, null, null, true);
//                            break;
//                        }
//                    } else {
//                        // offline check sees key invalid
//                        // build a message about keys being wrong. do not attempt to change the current key state though
//                        // ( the keys may be valid, but user would have clicked on the dialog anyway, that kind of thing )
//                        idStr msg = new idStr();
//                        idAsyncNetwork.BuildInvalidKeyMsg(msg, valid);
//                        sessLocal.MessageBox(MSG_OK, msg.toString(), common.GetLanguageDict().GetString("#str_04310"), true, null, null, true);
//                    }
//                } else if (args.Argc() == 2 && idStr.Icmp(args.Argv(1), "force") == 0) {
//                    // cancelled in force mode
//                    cmdSystem.BufferCommandText(CMD_EXEC_APPEND, "quit\n");
//                    cmdSystem.ExecuteCommandBuffer();
//                }
//            } while (retkey != null);
            recursed = false
        }

        companion object {
            private val instance: cmdFunction_t = Session_PromptKey_f()
            fun getInstance(): cmdFunction_t {
                return instance
            }
        }
    }

    /*
     ================
     Session_DemoShot_f
     ================
     */
    internal class Session_DemoShot_f : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            if (args!!.Argc() != 2) {
                val filename: String = FindUnusedFileName("demos/shot%03i.demo")
                sessLocal.DemoShot(filename)
            } else {
                sessLocal.DemoShot(Str.va("demos/shot_%s.demo", args.Argv(1)))
            }
        }

        companion object {
            private val instance: cmdFunction_t = Session_DemoShot_f()
            fun getInstance(): cmdFunction_t {
                return instance
            }
        }
    }

    /*
     ================
     Session_RecordDemo_f
     ================
     */
    internal class Session_RecordDemo_f : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            if (args!!.Argc() != 2) {
                val filename: String = FindUnusedFileName("demos/demo%03i.demo")
                sessLocal.StartRecordingRenderDemo(filename)
            } else {
                sessLocal.StartRecordingRenderDemo(Str.va("demos/%s.demo", args.Argv(1)))
            }
        }

        companion object {
            private val instance: cmdFunction_t = Session_RecordDemo_f()
            fun getInstance(): cmdFunction_t {
                return instance
            }
        }
    }

    /*
     ================
     Session_CompressDemo_f
     ================
     */
    internal class Session_CompressDemo_f : cmdFunction_t() {
        @Throws(idException::class)
        override fun run(args: CmdArgs.idCmdArgs?) {
            if (args!!.Argc() == 2) {
                sessLocal.CompressDemoFile("2", args.Argv(1))
            } else if (args.Argc() == 3) {
                sessLocal.CompressDemoFile(args.Argv(2), args.Argv(1))
            } else {
                Common.common.Printf("use: CompressDemo <file> [scheme]\nscheme is the same as com_compressDemo, defaults to 2")
            }
        }

        companion object {
            private val instance: cmdFunction_t = Session_CompressDemo_f()
            fun getInstance(): cmdFunction_t {
                return instance
            }
        }
    }

    /*
     ================
     Session_StopRecordingDemo_f
     ================
     */
    internal class Session_StopRecordingDemo_f : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            sessLocal.StopRecordingRenderDemo()
        }

        companion object {
            private val instance: cmdFunction_t = Session_StopRecordingDemo_f()
            fun getInstance(): cmdFunction_t {
                return instance
            }
        }
    }

    /*
     ================
     Session_PlayDemo_f
     ================
     */
    internal class Session_PlayDemo_f : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            if (args!!.Argc() >= 2) {
                sessLocal.StartPlayingRenderDemo(Str.va("demos/%s", args.Argv(1)))
            }
        }

        companion object {
            private val instance: cmdFunction_t = Session_PlayDemo_f()
            fun getInstance(): cmdFunction_t {
                return instance
            }
        }
    }

    /*
     ================
     Session_TimeDemo_f
     ================
     */
    internal class Session_TimeDemo_f : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            if (args!!.Argc() >= 2) {
                sessLocal.TimeRenderDemo(Str.va("demos/%s", args.Argv(1)), args.Argc() > 2)
            }
        }

        companion object {
            private val instance: cmdFunction_t = Session_TimeDemo_f()
            fun getInstance(): cmdFunction_t {
                return instance
            }
        }
    }

    /*
     ================
     Session_TimeDemoQuit_f
     ================
     */
    internal class Session_TimeDemoQuit_f : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            sessLocal.TimeRenderDemo(Str.va("demos/%s", args!!.Argv(1)))
            if (sessLocal.timeDemo == timeDemo_t.TD_YES) {
                // this allows hardware vendors to automate some testing
                sessLocal.timeDemo = timeDemo_t.TD_YES_THEN_QUIT
            }
        }

        companion object {
            private val instance: cmdFunction_t = Session_TimeDemoQuit_f()
            fun getInstance(): cmdFunction_t {
                return instance
            }
        }
    }

    /*
     ================
     Session_AVIDemo_f
     ================
     */
    internal class Session_AVIDemo_f : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            sessLocal.AVIRenderDemo(Str.va("demos/%s", args!!.Argv(1)))
        }

        companion object {
            private val instance: cmdFunction_t = Session_AVIDemo_f()
            fun getInstance(): cmdFunction_t {
                return instance
            }
        }
    }

    /*
     ================
     Session_AVIGame_f
     ================
     */
    internal class Session_AVIGame_f : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            val Argv = arrayOf(args!!.Argv(1))
            val empty = Argv[0].isEmpty()
            sessLocal.AVIGame(Argv) //TODO:back reference
            if (empty) {
                args.set(Argv[0])
            }
        }

        companion object {
            private val instance: cmdFunction_t = Session_AVIGame_f()
            fun getInstance(): cmdFunction_t {
                return instance
            }
        }
    }

    /*
     ================
     Session_AVICmdDemo_f
     ================
     */
    internal class Session_AVICmdDemo_f : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            sessLocal.AVICmdDemo(args!!.Argv(1))
        }

        companion object {
            private val instance: cmdFunction_t = Session_AVICmdDemo_f()
            fun getInstance(): cmdFunction_t {
                return instance
            }
        }
    }

    /*
     ================
     Session_WriteCmdDemo_f
     ================
     */
    internal class Session_WriteCmdDemo_f : cmdFunction_t() {
        @Throws(idException::class)
        override fun run(args: CmdArgs.idCmdArgs?) {
            if (args!!.Argc() == 1) {
                val filename: String = FindUnusedFileName("demos/cmdDemo%03i.cdemo")
                sessLocal.WriteCmdDemo(filename)
            } else if (args.Argc() == 2) {
                sessLocal.WriteCmdDemo(Str.va("demos/%s.cdemo", args.Argv(1)))
            } else {
                Common.common.Printf("usage: writeCmdDemo [demoName]\n")
            }
        }

        companion object {
            private val instance: cmdFunction_t = Session_WriteCmdDemo_f()
            fun getInstance(): cmdFunction_t {
                return instance
            }
        }
    }

    /*
     ================
     Session_PlayCmdDemo_f
     ================
     */
    internal class Session_PlayCmdDemo_f : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            sessLocal.StartPlayingCmdDemo(args!!.Argv(1))
        }

        companion object {
            private val instance: cmdFunction_t = Session_PlayCmdDemo_f()
            fun getInstance(): cmdFunction_t {
                return instance
            }
        }
    }

    /*
     ================
     Session_TimeCmdDemo_f
     ================
     */
    internal class Session_TimeCmdDemo_f : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            sessLocal.TimeCmdDemo(args!!.Argv(1))
        }

        companion object {
            private val instance: cmdFunction_t = Session_TimeCmdDemo_f()
            fun getInstance(): cmdFunction_t {
                return instance
            }
        }
    }

    /*
     ================
     Session_Disconnect_f
     ================
     */
    internal class Session_Disconnect_f : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            sessLocal.Stop()
            sessLocal.StartMenu()
            if (snd_system.soundSystem != null) {
                snd_system.soundSystem.SetMute(false)
            }
        }

        companion object {
            private val instance: cmdFunction_t = Session_Disconnect_f()
            fun getInstance(): cmdFunction_t {
                return instance
            }
        }
    }

    /*
     ================
     Session_EndOfDemo_f
     ================
     */
    internal class Session_EndOfDemo_f : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            sessLocal.Stop()
            sessLocal.StartMenu()
            if (snd_system.soundSystem != null) {
                snd_system.soundSystem.SetMute(false)
            }
            sessLocal.guiActive?.HandleNamedEvent("endOfDemo")
        }

        companion object {
            private val instance: cmdFunction_t = Session_EndOfDemo_f()
            fun getInstance(): cmdFunction_t {
                return instance
            }
        }
    }

    /*
     ================
     Session_ExitCmdDemo_f
     ================
     */
    internal class Session_ExitCmdDemo_f : cmdFunction_t() {
        @Throws(idException::class)
        override fun run(args: CmdArgs.idCmdArgs?) {
            if (null == sessLocal.cmdDemoFile) {
                Common.common.Printf("not reading from a cmdDemo\n")
                return
            }
            FileSystem_h.fileSystem.CloseFile(sessLocal.cmdDemoFile!!)
            Common.common.Printf("Command demo exited at logIndex %d\n", sessLocal.logIndex)
            sessLocal.cmdDemoFile = null
        }

        companion object {
            private val instance: cmdFunction_t = Session_ExitCmdDemo_f()
            fun getInstance(): cmdFunction_t {
                return instance
            }
        }
    }

    /*
     ================
     Session_TestGUI_f
     ================
     */
    internal class Session_TestGUI_f : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            sessLocal.TestGUI(args!!.Argv(1))
        }

        companion object {
            private val instance: cmdFunction_t = Session_TestGUI_f()
            fun getInstance(): cmdFunction_t {
                return instance
            }
        }
    }

    /*
     ===============
     LoadGame_f
     ===============
     */
    internal class LoadGame_f : cmdFunction_t() {
        @Throws(idException::class)
        override fun run(args: CmdArgs.idCmdArgs?) {
            Console.console.Close()
            if (args!!.Argc() < 2 || idStr.Icmp(args.Argv(1), "quick") == 0) {
                sessLocal.QuickLoad()
            } else {
                sessLocal.LoadGame(args.Argv(1))
            }
        }

        companion object {
            private val instance: cmdFunction_t = LoadGame_f()
            fun getInstance(): cmdFunction_t {
                return instance
            }
        }
    }

    /*
     ===============
     SaveGame_f
     ===============
     */
    internal class SaveGame_f : cmdFunction_t() {
        @Throws(idException::class)
        override fun run(args: CmdArgs.idCmdArgs?) {
            if (args!!.Argc() < 2 || idStr.Icmp(args.Argv(1), "quick") == 0) {
                val saveName = Common.common.GetLanguageDict().GetString("#str_07178")
                if (sessLocal.SaveGame(saveName)) {
                    Common.common.Printf("%s\n", saveName)
                }
            } else {
                if (sessLocal.SaveGame(args.Argv(1))) {
                    Common.common.Printf("Saved %s\n", args.Argv(1))
                }
            }
        }

        companion object {
            private val instance: cmdFunction_t = SaveGame_f()
            fun getInstance(): cmdFunction_t {
                return instance
            }
        }
    }

    /*
     ===============
     TakeViewNotes_f
     ===============
     */
    internal class TakeViewNotes_f : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            val p = if (args!!.Argc() > 1) args.Argv(1) else ""
            sessLocal.TakeNotes(p)
        }

        companion object {
            private val instance: cmdFunction_t = TakeViewNotes_f()
            fun getInstance(): cmdFunction_t {
                return instance
            }
        }
    }

    /*
     ===============
     TakeViewNotes2_f
     ===============
     */
    internal class TakeViewNotes2_f : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            val p = if (args!!.Argc() > 1) args.Argv(1) else ""
            sessLocal.TakeNotes(p, true)
        }

        companion object {
            private val instance: cmdFunction_t = TakeViewNotes2_f()
            fun getInstance(): cmdFunction_t {
                return instance
            }
        }
    }

    //
    //////////////////////////////////////////////////////////////////////////////////////////////////
    //
    /*
     ===============
     Session_Hitch_f
     ===============
     */
    internal class Session_Hitch_f : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            val sw = snd_system.soundSystem.GetPlayingSoundWorld()
            if (sw != null) {
                snd_system.soundSystem.SetMute(true)
                sw.Pause()
                Sys_EnterCriticalSection()
            }
            if (args!!.Argc() == 2) {
                win_main.Sys_Sleep(args.Argv(1).toInt())
            } else {
                win_main.Sys_Sleep(100)
            }
            if (sw != null) {
                Sys_LeaveCriticalSection()
                sw.UnPause()
                snd_system.soundSystem.SetMute(false)
            }
        }

        companion object {
            private val instance: cmdFunction_t = Session_Hitch_f()
            fun getInstance(): cmdFunction_t {
                return instance
            }
        }
    }

    companion object {
        const val MAX_LOGGED_STATS = 60 * 120 // log every half second
        val sessLocal: idSessionLocal = idSessionLocal()

        /*
     ===============================================================================

     The session is the glue that holds games together between levels.

     ===============================================================================
     */

        val session: idSession = sessLocal
        var PREVIEW_HEIGHT = 298
        var PREVIEW_WIDTH = 398

        //
        //
        //
        ////    idCVar	idSessionLocal::com_showAngles( "com_showAngles", "0", CVAR_SYSTEM | CVAR_BOOL, "" );
        //idCVar	idSessionLocal::com_minTics( "com_minTics", "1", CVAR_SYSTEM, "" );
        //idCVar	idSessionLocal::com_showTics( "com_showTics", "0", CVAR_SYSTEM | CVAR_BOOL, "" );
        //idCVar	idSessionLocal::com_fixedTic( "com_fixedTic", "0", CVAR_SYSTEM | CVAR_INTEGER, "", 0, 10 );
        //idCVar	idSessionLocal::com_showDemo( "com_showDemo", "0", CVAR_SYSTEM | CVAR_BOOL, "" );
        //idCVar	idSessionLocal::com_skipGameDraw( "com_skipGameDraw", "0", CVAR_SYSTEM | CVAR_BOOL, "" );
        //idCVar	idSessionLocal::com_aviDemoSamples( "com_aviDemoSamples", "16", CVAR_SYSTEM, "" );
        //idCVar	idSessionLocal::com_aviDemoWidth( "com_aviDemoWidth", "256", CVAR_SYSTEM, "" );
        //idCVar	idSessionLocal::com_aviDemoHeight( "com_aviDemoHeight", "256", CVAR_SYSTEM, "" );
        //idCVar	idSessionLocal::com_aviDemoTics( "com_aviDemoTics", "2", CVAR_SYSTEM | CVAR_INTEGER, "", 1, 60 );
        //idCVar	idSessionLocal::com_wipeSeconds( "com_wipeSeconds", "1", CVAR_SYSTEM, "" );
        //idCVar	idSessionLocal::com_guid( "com_guid", "", CVAR_SYSTEM | CVAR_ARCHIVE | CVAR_ROM, "" );
        //
        //
        // these must be kept up to date with window Levelshot in guis/mainmenu.gui
        var PREVIEW_X = 211
        var PREVIEW_Y = 31

        /*
     ===================
     Session_PromptKey_f
     ===================
     */
        var recursed = false

        /*
     ================
     FindUnusedFileName
     ================
     */
        fun FindUnusedFileName(format: String): String {
            var i: Int
            var filename = "" //=new char[1024];
            i = 0
            while (i < 999) {
                filename = String.format(format, i)
                val len = FileSystem_h.fileSystem.ReadFile(filename, null, null)
                if (len <= 0) {
                    return filename // file doesn't exist
                }
                i++
            }
            return filename
        }
    }
}
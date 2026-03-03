package neo.framework

import neo.Game.GameSys.SysCvar
import neo.Game.Game_local
import neo.Renderer.Image
import neo.Renderer.RenderSystem
import neo.Sound.snd_system
import neo.TempDump
import neo.TempDump.void_callback
import neo.Tools.Compilers.AAS.AASBuild.RunAASDir_f
import neo.Tools.Compilers.AAS.AASBuild.RunAAS_f
import neo.Tools.Compilers.AAS.AASBuild.RunReach_f
import neo.Tools.Compilers.DMap.dmap.Dmap_f
import neo.Tools.Compilers.RenderBump.renderbump.RenderBumpFlat_f
import neo.Tools.Compilers.RenderBump.renderbump.RenderBump_f
import neo.Tools.Compilers.RoqVQ.Roq.RoQFileEncode_f
import neo.Tools.edit_public
import neo.framework.Async.AsyncNetwork.idAsyncNetwork
import neo.framework.CVarSystem.cvarSystem
import neo.framework.CVarSystem.idCVar
import neo.framework.CmdSystem.cmdExecution_t
import neo.framework.CmdSystem.cmdFunction_t
import neo.framework.CmdSystem.idCmdSystem.*
import neo.framework.Compressor.idCompressor
import neo.framework.Console.Companion.console
import neo.framework.FileSystem_h.idFileList
import neo.framework.File_h.idFile
import neo.framework.File_h.idFile_Memory
import neo.framework.KeyInput.idKeyInput
import neo.idlib.BIT
import neo.idlib.CmdArgs
import neo.idlib.Dict_h.idDict
import neo.idlib.Dict_h.idDict.ListKeys_f
import neo.idlib.Dict_h.idDict.ListValues_f
import neo.idlib.LangDict.idLangDict
import neo.idlib.MapFile.idMapFile
import neo.idlib.Text.Base64.idBase64
import neo.idlib.Text.Lexer
import neo.idlib.Text.Lexer.idLexer
import neo.idlib.Text.Str
import neo.idlib.Text.Str.idStr
import neo.idlib.Text.Token
import neo.idlib.Text.Token.idToken
import neo.idlib.containers.idStrList
import neo.idlib.idException
import neo.idlib.idLib
import neo.idlib.math.idSIMD
import neo.idlib.math.idVec4
import neo.sys.*
import neo.sys.win_main.Sys_GenerateEvents
import neo.sys.win_main.Sys_Shutdown
import neo.ui.UserInterface
import java.io.IOException
import java.nio.ByteBuffer
import java.util.*
import java.util.logging.Level
import java.util.logging.Logger

class Common {
    internal enum class errorParm_t {
        ERP_NONE, ERP_FATAL,  // exit the entire game with a popup window
        ERP_DROP,  // print to console and disconnect from game
        ERP_DISCONNECT // don't kill server
    }

    class MemInfo_t {
        var assetTotals = 0
        val filebase: idStr = idStr()

        // subsystem totals
        var gameSubsystemTotal = 0

        // asset totals
        var imageAssetsTotal = 0

        // memory manager totals
        var memoryManagerTotal = 0
        var modelAssetsTotal = 0
        var renderSubsystemTotal = 0
        var soundAssetsTotal = 0
        var total = 0
    }

    abstract class idCommon {
        // Initialize everything.
        // if the OS allows, pass argc/argv directly (without executable name)
        // otherwise pass the command line in a single string (without executable name)
        abstract fun Init(argc: Int, argv: Array<String>?, cmdline: String)

        // Shuts down everything.
        abstract fun Shutdown()

        // Shuts down everything.
        abstract fun Quit()

        // Returns true if common initialization is complete.
        abstract fun IsInitialized(): Boolean

        // Called repeatedly as the foreground thread for rendering and game logic.
        abstract fun Frame()

        // Called repeatedly by blocking function calls with GUI interactivity.
        @Throws(idException::class)
        abstract fun GUIFrame(execCmd: Boolean, network: Boolean)

        // Called 60 times a second from a background thread for sound mixing,
        // and input generation. Not called until idCommon::Init() has completed.
        abstract fun Async()

        // Checks for and removes command line "+set var arg" constructs.
        // If match is NULL, all set commands will be executed, otherwise
        // only a set with the exact name.  Only used during startup.
        // set once to clear the cvar from +set for early init code
        abstract fun StartupVariable(match: String?, once: Boolean)

        // Initializes a tool with the given dictionary.
        abstract fun InitTool(toolFlag_t: Int, dict: idDict)

        // Activates or deactivates a tool.
        abstract fun ActivateTool(active: Boolean)

        // Writes the user's configuration to a file
        abstract fun WriteConfigToFile(filename: String)

        // Writes cvars with the given flags to a file.
        @Throws(idException::class)
        abstract fun WriteFlaggedCVarsToFile(filename: String, flags: Int, setCmd: String)

        // Begins redirection of console output to the given buffer.
        abstract fun BeginRedirect(buffer: StringBuilder?, buffersize: Int, flush: void_callback<String>?)

        // Stops redirection of console output.
        abstract fun EndRedirect()

        // Update the screen with every message printed.
        abstract fun SetRefreshOnPrint(set: Boolean)

        // Prints message to the console, which may cause a screen update if com_refreshOnPrint is set.
        abstract fun Printf(fmt: String, vararg args: Any) /*id_attribute((format(printf,2,3)))*/

        // Same as Printf, with a more usable API - Printf pipes to this.
        abstract fun VPrintf(fmt: String, vararg args: Any)

        // Prints message that only shows up if the "developer" cvar is set,
        // and NEVER forces a screen update, which could cause reentrancy problems.
        abstract fun DPrintf(fmt: String, vararg args: Any) /* id_attribute((format(printf,2,3)))*/

        // Prints WARNING %s message and adds the warning message to a queue for printing later on.
        abstract fun Warning(fmt: String, vararg args: Any) /* id_attribute((format(printf,2,3)))*/

        // Prints WARNING %s message in yellow that only shows up if the "developer" cvar is set.
        @Throws(idException::class)
        abstract fun DWarning(fmt: String, vararg args: Any)

        // Prints all queued warnings.
        @Throws(idException::class)
        abstract fun PrintWarnings()

        // Removes all queued warnings.
        abstract fun ClearWarnings(reason: String)

        // Issues a C++ throw. Normal errors just abort to the game loop,
        // which is appropriate for media or dynamic logic errors.
        @Throws(idException::class)
        abstract fun Error(fmt: String, vararg args: Any)

        // Fatal errors quit all the way to a system dialog box, which is appropriate for
        // static internal errors or cases where the system may be corrupted.
        @Throws(idException::class)
        abstract fun FatalError(fmt: String, vararg args: Any)

        // Returns a pointer to the dictionary with language specific strings.
        abstract fun GetLanguageDict(): idLangDict

        // Returns key bound to the command
        abstract fun KeysFromBinding(bind: String): String

        // Returns the binding bound to the key
        abstract fun BindingFromKey(key: String): String

        // Directly sample a button.
        abstract fun ButtonState(key: Int): Int

        // Directly sample a keystate.
        abstract fun KeyState(key: Int): Int
    }

    class version_s {
        val string: String

        init {
            string = String.format(
                "%s.%d%s %s %s",
                Licensee.ENGINE_VERSION,
                BUILD_NUMBER,
                BUILD_DEBUG,
                sys_public.BUILD_STRING,
                SysCvar.__DATE__
            )
        }
    }

    class idCommonLocal : idCommon() {
        private val com_asyncStats: Array<asyncStats_t> // indexed by com_ticNumber
        var com_consoleLines: Array<CmdArgs.idCmdArgs>
        var com_numConsoleLines = 0
        var config_compressor: idCompressor? = null
        private var com_errorEntered = 0 // 0, ERP_DROP, etc
        private var com_fullyInitialized = false
        private var com_refreshOnPrint = false // update the screen every print for dmap
        private var com_shuttingDown = false
        private val errorList: idStrList
        private val errorMessage: Array<String> = arrayOf("") //new char[MAX_PRINT_MSG_SIZE];
        private val gameDLL: Int
        private val languageDict: idLangDict
        private var lastTicMsec = 0
        private var logFile: idFile? = null
        private val prevAsyncMsec = 0
        private var rd_buffer: StringBuilder? = null
        private var rd_buffersize = 0
        private var rd_flush /*)( const char *buffer )*/: void_callback<String>? = null
        private val warningCaption: idStr = idStr()
        private val warningList: idStrList = idStrList()

        override fun Init(argc: Int, argv: Array<String>?, cmdline: String) {
            var argc = argc
            var argv = argv
            try {
                // set interface pointers used by idLib
                idLib.sys = sys_public.sys
                idLib.common = common
                idLib.cvarSystem = cvarSystem
                idLib.fileSystem = FileSystem_h.fileSystem

                // initialize idLib
                idLib.Init()

                // clear warning buffer
                ClearWarnings(Licensee.GAME_NAME + " initialization")

                // parse command line options
                val args: CmdArgs.idCmdArgs
                if (cmdline.isNotEmpty()) {
                    // tokenize if the OS doesn't do it for us
                    args = CmdArgs.idCmdArgs()
                    args.TokenizeString(cmdline, true)
                    val cArg = intArrayOf(argc)
                    argv = args.GetArgs(cArg)
                    argc = cArg[0]
                }
                if (argv != null && argv.isNotEmpty()) {
                    ParseCommandLine(argc, argv)
                } else {
                    ParseCommandLine(argc)
                }

                // init console command system
                CmdSystem.cmdSystem.Init()

                // init CVar system
                cvarSystem.Init()

                // start file logging right away, before early console or whatever
                StartupVariable("win_outputDebugString", false)

                // register all static CVars
                idCVar.RegisterStaticVars()

                // print engine version
                Printf("%s\n", version.string)

                // initialize key input/binding, done early so bind command exists
                idKeyInput.Init()

                // init the console so we can take printsF
                console.Init()

                // get architecture info
                win_main.Sys_Init()

                // initialize networking
                win_net.Sys_InitNetworking()

                // override cvars from command line
                StartupVariable(null, false)
                if (idAsyncNetwork.serverDedicated.GetInteger() == 0 && win_main.Sys_AlreadyRunning()) {
                    win_main.Sys_Quit()
                }

                // initialize processor specific SIMD implementation
                InitSIMD()

                // init commands
                InitCommands()
                if (ID_WRITE_VERSION) {
                    config_compressor = idCompressor.AllocArithmetic()
                }

                // game specific initialization
                InitGame()

                // don't add startup commands if no CD key is present
                if (ID_ENFORCE_KEY && (!Session.session.CDKeysAreValid(false) || !AddStartupCommands()) || !AddStartupCommands()) {

                    // if the user didn't give any commands, run default action
                    Session.session.StartMenu(true)
                }
                Printf("--- Common Initialization Complete ---\n")

                // print all warnings queued during initialization
                PrintWarnings()
                if (ID_DEDICATED) {
                    Printf("\nType 'help' for dedicated server info.\n\n")
                }

                // remove any prints from the notify lines
                console.ClearNotifyLines()
                ClearCommandLine()
                com_fullyInitialized = true
            } catch (e: idException) {
                win_main.Sys_Error("Error during initialization")
            }
        }

        override fun Shutdown() {
            com_shuttingDown = true
            idAsyncNetwork.server.Kill()
            idAsyncNetwork.client.Shutdown()

            // game specific shut down
            ShutdownGame(false)

            // shut down non-portable system services
            Sys_Shutdown()
            // shut down the console
            console.Shutdown()

            // shut down the key system
            idKeyInput.Shutdown()

            // shut down the cvar system
            cvarSystem.Shutdown()

            // shut down the console command system
            CmdSystem.cmdSystem.Shutdown()
            if (ID_WRITE_VERSION) {
                //	delete config_compressor;
                config_compressor = null
            }

            // free any buffered warning messages
            ClearWarnings(Licensee.GAME_NAME + " shutdown")
            warningCaption.Clear()
            errorList.clear()

            // free language dictionary
            languageDict.Clear()

            // shutdown idLib
            idLib.ShutDown()
        }

        override fun Quit() {
            if (ID_ALLOW_TOOLS) {
                if (com_editors and EDITOR_RADIANT != 0) {
                    edit_public.RadiantInit()
                    return
                }
            }

            // don't try to shutdown if we are in a recursive error
            if (0 == com_errorEntered) {
                Shutdown()
            }
            win_main.Sys_Quit()
        }

        override fun IsInitialized(): Boolean {
            return com_fullyInitialized
        }

        /*
         ============================================================================

         COMMAND LINE FUNCTIONS

         + characters separate the commandLine string into multiple console
         command lines.

         All of these are valid:

         doom +set test blah +map test
         doom set test blah+map test
         doom set test blah + map test

         ============================================================================
         */
        override fun Frame() {
            try {
                // pump all the events
                Sys_GenerateEvents()
                // write config file if anything changed
                WriteConfiguration()

                // change SIMD implementation if required
                if (com_forceGenericSIMD.IsModified()) {
                    InitSIMD()
                }
                EventLoop.eventLoop.RunEventLoop()
                com_frameTime = com_ticNumber * UsercmdGen.USERCMD_MSEC
                //                System.out.println(System.nanoTime()+"com_frameTime=>"+com_frameTime);
                idAsyncNetwork.RunFrame()
                if (idAsyncNetwork.IsActive()) {
                    if (idAsyncNetwork.serverDedicated.GetInteger() != 1) {
                        Session.session.GuiFrameEvents()
                        Session.session.UpdateScreen(false)
                    }
                } else {
                    Session.session.Frame()

                    // normal, in-sequence screen update
                    Session.session.UpdateScreen(false)
                }

                // report timing information
                if (com_speeds.GetBool()) {
                    val nowTime = win_shared.Sys_Milliseconds()
                    val com_frameMsec = nowTime - lastTime
                    lastTime = nowTime
                    Printf(
                        "frame:%d all:%3d gfr:%3d rf:%3d bk:%3d\n",
                        com_frameNumber,
                        com_frameMsec,
                        time_gameFrame,
                        time_frontend,
                        time_backend
                    )
                    time_gameFrame = 0
                    time_gameDraw = 0
                }
                com_frameNumber++

                // set idLib frame number for frame based memory dumps
                idLib.frameNumber = com_frameNumber
            } catch (ex: idException) {
                return  // an ERP_DROP was thrown
            }
        }

        @Throws(idException::class)
        override fun GUIFrame(execCmd: Boolean, network: Boolean) {
            Sys_GenerateEvents()
            EventLoop.eventLoop.RunEventLoop(execCmd) // and execute any commands
            com_frameTime = com_ticNumber * UsercmdGen.USERCMD_MSEC
            if (network) {
                idAsyncNetwork.RunFrame()
            }
            Session.session.Frame()
            Session.session.UpdateScreen(false)
        }

        /*
         =================
         idCommonLocal::SingleAsyncTic

         The system will asyncronously call this function 60 times a second to
         handle the time-critical functions that we don't want limited to
         the frame rate:

         sound mixing
         user input generation (conditioned by com_asyncInput)
         packet server operation
         packet client operation

         We are not using thread safe libraries, Fso any functionality put here must
         be VERY VERY careful about what it calls.
         =================
         */
        override fun Async() {
            if (com_shuttingDown) {
                return
            }
            val msec = win_shared.Sys_Milliseconds()
            if (0 == lastTicMsec) {
                lastTicMsec = msec - UsercmdGen.USERCMD_MSEC
            }
            if (!com_preciseTic.GetBool()) {
                // just run a single tic, even if the exact msec isn't precise
                SingleAsyncTic()
                return
            }
            var ticMsec = UsercmdGen.USERCMD_MSEC

            // the number of msec per tic can be varies with the timescale cvar
            val timescale = com_timescale.GetFloat()
            if (timescale != 1.0f) {
                ticMsec =
                    (ticMsec / timescale).toInt() // FIX: C++ does float division then truncates, not toInt() first
                if (ticMsec < 1) {
                    ticMsec = 1
                }
            }

            // don't skip too many
            if (timescale == 1.0f) {
                if (lastTicMsec + 10 * UsercmdGen.USERCMD_MSEC < msec) {
                    lastTicMsec = msec - 10 * UsercmdGen.USERCMD_MSEC
                }
            }
            while (lastTicMsec + ticMsec <= msec) {
                SingleAsyncTic()
                lastTicMsec += ticMsec
            }
        }

        /*
         ==================
         idCommonLocal::StartupVariable

         Searches for command line parameters that are set commands.
         If match is not NULL, only that cvar will be looked for.
         That is necessary because cddir and basedir need to be set
         before the filesystem is started, but all other sets should
         be after execing the config and default.
         ==================
         */
        override fun StartupVariable(match: String?, once: Boolean) {
            var i: Int
            var s: String?
            i = 0
            while (i < com_numConsoleLines) {
//                if ( strcmp( com_consoleLines[ i ].Argv( 0 ), "set" ) ) {//TODO:strcmp equals returns false.
                if ("set" != com_consoleLines[i].Argv(0)) {
                    i++
                    continue
                }
                s = com_consoleLines[i].Argv(1)
                if (null == match || 0 == idStr.Icmp(s, match)) {
                    cvarSystem.SetCVarString(s, com_consoleLines[i].Argv(2))
                    if (once) {
                        // kill the line
                        var j = i + 1
                        while (j < com_numConsoleLines) {
                            com_consoleLines[j - 1] = com_consoleLines[j]
                            j++
                        }
                        com_numConsoleLines--
                        continue
                    }
                }
                i++
            }
        }

        override fun InitTool(toolFlag_t: Int, dict: idDict) {
            if (ID_ALLOW_TOOLS) {
                if (toolFlag_t and EDITOR_SOUND != 0) {
                    edit_public.SoundEditorInit(dict)
                } else if (toolFlag_t and EDITOR_LIGHT != 0) {
                    edit_public.LightEditorInit(dict)
                } else if (toolFlag_t and EDITOR_PARTICLE != 0) {
                    edit_public.ParticleEditorInit(dict)
                } else if (toolFlag_t and EDITOR_AF != 0) {
                    edit_public.AFEditorInit(dict)
                }
            }
        }

        /*
         ==================
         idCommonLocal::ActivateTool

         Activates or Deactivates a tool
         ==================
         */
        override fun ActivateTool(active: Boolean) {
            com_editorActive = active
            win_input.Sys_GrabMouseCursor(!active)
        }

        override fun WriteConfigToFile(filename: String) {
            val f: idFile?
            f = FileSystem_h.fileSystem.OpenFileWrite(filename, "fs_configpath") // FIX: C++ uses "fs_configpath"
            if (null == f) {
                Printf("Couldn't write %s.\n", filename)
                return
            }
            if (ID_WRITE_VERSION) {
                val curTime: String
                val runtag: String
                val compressed = idFile_Memory("compressed")
                val out = idBase64()
                assert(config_compressor != null)
                //                ID_TIME_T = time(null);
                curTime = Date().toString()
                runtag = String.format("%s - %s", cvarSystem.GetCVarString("si_version"), curTime)
                config_compressor!!.Init(compressed, true, 8)
                config_compressor!!.WriteString(runtag) //
                config_compressor!!.FinishCompress()
                out.Encode( /*(const byte *)*/compressed.GetDataPtr(), compressed.Length())
                f.Printf("// %s\n", out.c_str())
            }
            idKeyInput.WriteBindings(f)
            cvarSystem.WriteFlaggedVariables(CVarSystem.CVAR_ARCHIVE, "seta", f)
            FileSystem_h.fileSystem.CloseFile(f)
        }

        @Throws(idException::class)
        override fun WriteFlaggedCVarsToFile(filename: String, flags: Int, setCmd: String) {
            val f: idFile?
            f = FileSystem_h.fileSystem.OpenFileWrite(filename, "fs_configpath") // FIX: C++ uses "fs_configpath"
            if (null == f) {
                Printf("Couldn't write %s.\n", filename)
                return
            }
            cvarSystem.WriteFlaggedVariables(flags, setCmd, f)
            FileSystem_h.fileSystem.CloseFile(f)
        }

        override fun BeginRedirect(buffer: StringBuilder?, buffersize: Int, flush: void_callback<String>?) {
            if (null == buffer || 0 == buffersize || null == flush) {
                return
            }
            rd_buffer = buffer
            rd_buffersize = buffersize
            rd_flush = flush

            buffer.setLength(0) // FIX: C++ does *rd_buffer = 0; clear the buffer at start of redirect
        }

        override fun EndRedirect() {
            if (rd_flush != null && rd_buffer!!.isNotEmpty()) { // '\0') {
                rd_flush!!.run(rd_buffer.toString())
            }
            rd_buffer = null
            rd_buffersize = 0
            rd_flush = null
        }

        override fun SetRefreshOnPrint(set: Boolean) {
            com_refreshOnPrint = set
        }

        /*
         ==================
         idCommonLocal::Printf

         Both client and server can use this, and it will output to the appropriate place.

         A raw string should NEVER be passed as fmt, because of "%f" type crashers.
         ==================
         */
        override fun Printf(fmt: String, vararg args: Any) {
            VPrintf(fmt, *args)
        }

        /*
         ==================
         idCommonLocal::VPrintf

         A raw string should NEVER be passed as fmt, because of "%f" type crashes.
         ==================
         */
        override fun VPrintf(fmt: String, vararg args: Any) {
            val msg = arrayOf("") //new char(MAX_PRINT_MSG_SIZE);
            val timeLength: Int

            // if the cvar system is not initialized
            if (!cvarSystem.IsInitialized()) {
                return
            }

            // optionally put a timestamp at the beginning of each print,
            // so we can see how long different init sections are taking
            if (com_timestampPrints.GetInteger() != 0) {
                var t = win_shared.Sys_Milliseconds()
                if (com_timestampPrints.GetInteger() == 1) {
                    t /= 1000
                }
                //                sprintf(msg, "[%i]", t);
                msg[0] = String.format("[%d]", t)
                timeLength = msg[0].length
            } else {
                timeLength = 0
            }

            // don't overflow
            if (idStr.vsnPrintf(msg, MAX_PRINT_MSG_SIZE - timeLength - 1, fmt, *args) < 0) {
                msg[0] = "\n"
                win_main.Sys_Printf("idCommon::VPrintf: truncated to %d characters\n", msg[0].length /*- 1*/)
            }
            if (rd_buffer != null) {
                if (msg[0].length + rd_buffer!!.length > rd_buffersize - 1) {
                    rd_flush!!.run(rd_buffer.toString())
                    rd_buffer!!.setLength(0) // FIX: C++ clears buffer after flush (*rd_buffer = 0)
                }
                //		strcat( rd_buffer, msg );
                rd_buffer!!.append(msg[0])
                return
            }

            // echo to console buffer
            console.Print(msg[0])

            // remove any color codes
            msg[0] = idStr.RemoveColors(msg[0]) // FIX: RemoveColors returns new String; must assign back

            // echo to dedicated console and early console
            win_main.Sys_Printf("%s", msg[0])

            // print to script debugger server
            // DebuggerServerPrint( msg );
            // logFile
            if (com_logFile.GetInteger() != 0 && !logFileFailed && FileSystem_h.fileSystem.IsInitialized()) {
//		static bool recursing;
                if (null == logFile && !recursing) {
                    val newTime = Date().toString()
                    val fileName =
                        if (!com_logFileName.GetString()!!.isEmpty()) com_logFileName.GetString()!! else "qconsole.log"

                    // fileSystem.OpenFileWrite can cause recursive prints into here
                    recursing = true
                    logFile = FileSystem_h.fileSystem.OpenFileWrite(fileName)
                    if (null == logFile) {
                        logFileFailed = true
                        FatalError("failed to open log file '%s'\n", fileName)
                    }
                    recursing = false
                    if (com_logFile.GetInteger() > 1) {
                        // force it to not buffer so we get valid
                        // data even if we are crashing
                        logFile!!.ForceFlush()
                    }
                    Printf("log file '%s' opened on %s\n", fileName, newTime)
                }
                if (logFile != null) {
                    logFile!!.WriteString(msg[0])
                    logFile!!.Flush() // ForceFlush doesn't help a whole lot
                }
            }

            // don't trigger any updates if we are in the process of doing a fatal error
            if (com_errorEntered != TempDump.etoi(errorParm_t.ERP_FATAL)) {
                // update the console if we are in a long-running command, like dmap
                if (com_refreshOnPrint) {
                    Session.session.UpdateScreen()
                }

                // let session redraw the animated loading screen if necessary
                Session.session.PacifierUpdate()
            }
        }

        /*
         ==================
         idCommonLocal::DPrintf

         prints message that only shows up if the "developer" cvar is set
         ==================
         */
        override fun DPrintf(fmt: String, vararg args: Any) {
            val msg = arrayOf<String>("") //new char[MAX_PRINT_MSG_SIZE];
            if (!cvarSystem.IsInitialized() || !com_developer.GetBool()) {
                return  // don't confuse non-developers with techie stuff...
            }
            idStr.vsnPrintf(msg, MAX_PRINT_MSG_SIZE, fmt, *args)
            // never refresh the screen, which could cause reentrency problems
            val temp = com_refreshOnPrint
            com_refreshOnPrint = false
            Printf(Str.S_COLOR_RED + "%s", msg[0])
            com_refreshOnPrint = temp
        }

        /*
         ==================
         idCommonLocal::Warning

         prints WARNING %s and adds the warning message to a queue to be printed later on
         ==================
         */
        override fun Warning(fmt: String, vararg args: Any) {
            val msg = arrayOf<String>("") //[MAX_PRINT_MSG_SIZE];
            idStr.vsnPrintf(msg, MAX_PRINT_MSG_SIZE, fmt, *args)
            Printf(
                """
    ${Str.S_COLOR_YELLOW}WARNING: ${Str.S_COLOR_RED}%s
    
    """.trimIndent(), msg[0]
            )
            if (warningList.size() < MAX_WARNING_LIST) {
                warningList.addUnique(msg[0])
            }
        }

        /*
         ==================
         idCommonLocal::DWarning

         prints warning message in yellow that only shows up if the "developer" cvar is set
         ==================
         */
        @Throws(idException::class)
        override fun DWarning(fmt: String, vararg args: Any) {
            val msg = arrayOf<String>("") //new char[MAX_PRINT_MSG_SIZE];
            if (!com_developer.GetBool()) {
                return  // don't confuse non-developers with techie stuff...
            }

            idStr.vsnPrintf(msg, MAX_PRINT_MSG_SIZE, fmt, *args)
            Printf(
                """
    ${Str.S_COLOR_YELLOW}WARNING: %s
    
    """.trimIndent(), msg[0]
            )
        }

        @Throws(idException::class)
        override fun PrintWarnings() {
            var i: Int
            if (0 == warningList.size()) {
                return
            }
            warningList.sort()
            Printf("------------- Warnings ---------------\n")
            Printf("during %s...\n", warningCaption)
            i = 0
            while (i < warningList.size()) {
                Printf(
                    """
    ${Str.S_COLOR_YELLOW}WARNING: ${Str.S_COLOR_RED}%s
    
    """.trimIndent(), warningList[i]
                )
                i++
            }
            if (warningList.size() != 0) {
                if (warningList.size() >= MAX_WARNING_LIST) {
                    Printf("more than %d warnings\n", MAX_WARNING_LIST)
                } else {
                    Printf("%d warnings\n", warningList.size())
                }
            }
        }

        override fun ClearWarnings(reason: String) {
            warningCaption.set(reason)
            warningList.clear()
        }

        @Throws(idException::class)
        override fun Error(fmt: String, vararg args: Any) {
            val currentTime: Int
            var code = TempDump.etoi(errorParm_t.ERP_DROP)

            // always turn this off after an error
            com_refreshOnPrint = false

            // when we are running automated scripts, make sure we
            // know if anything failed
            if (cvarSystem.GetCVarInteger("fs_copyfiles") != 0) {
                code = TempDump.etoi(errorParm_t.ERP_FATAL)
            }

            // if we don't have GL running, make it a fatal error
            if (!RenderSystem.renderSystem.IsOpenGLRunning()) {
                code = TempDump.etoi(errorParm_t.ERP_FATAL)
            }

            // if we got a recursive error, make it fatal
            if (com_errorEntered != 0) {
                // if we are recursively erroring while exiting
                // from a fatal error, just kill the entire
                // process immediately, which will prevent a
                // full screen rendering window covering the
                // error dialog
                if (com_errorEntered == TempDump.etoi(errorParm_t.ERP_FATAL)) {
                    win_main.Sys_Quit()
                }
                code = TempDump.etoi(errorParm_t.ERP_FATAL)
            }

            // if we are getting a solid stream of ERP_DROP, do an ERP_FATAL
            currentTime = win_shared.Sys_Milliseconds()
            if (currentTime - lastErrorTime < 100) {
                if (++errorCount > 3) {
                    code = TempDump.etoi(errorParm_t.ERP_FATAL)
                }
            } else {
                errorCount = 0
            }
            lastErrorTime = currentTime
            com_errorEntered = code

            idStr.vsnPrintf(errorMessage, MAX_PRINT_MSG_SIZE, fmt, *args)

            // copy the error message to the clip board
            win_main.Sys_SetClipboardData(errorMessage[0])

            // add the message to the error list
            errorList.addUnique(idStr(errorMessage[0]))

            // Dont shut down the session for gui editor or debugger
            if (0 == com_editors and (EDITOR_GUI or EDITOR_DEBUGGER)) {
                Session.session.Stop()
            }
            if (code == TempDump.etoi(errorParm_t.ERP_DISCONNECT)) {
                com_errorEntered = 0
                throw idException(errorMessage[0])
                // The gui editor doesnt want thing to com_error so it handles exceptions instead
            } else if (com_editors and (EDITOR_GUI or EDITOR_DEBUGGER) != 0) {
                com_errorEntered = 0
                throw idException(errorMessage[0])
            } else if (code == TempDump.etoi(errorParm_t.ERP_DROP)) {
                Printf("********************\nERROR: %s\n********************\n", errorMessage[0])
                com_errorEntered = 0
                throw idException(errorMessage[0])
            } else {
                Printf("********************\nERROR: %s\n********************\n", errorMessage[0])
            }
            if (cvarSystem.GetCVarBool("r_fullscreen")) {
                CmdSystem.cmdSystem.BufferCommandText(cmdExecution_t.CMD_EXEC_NOW, "vid_restart partial windowed\n")
            }
            Shutdown()
            win_main.Sys_Error("%s", errorMessage[0])
        }

        /*
         ==================
         idCommonLocal::FatalError

         Dump out of the game to a system dialog
         ==================
         */
        @Throws(idException::class)
        override fun FatalError(fmt: String, vararg args: Any) {
            // if we got a recursive error, make it fatal
            if (com_errorEntered != 0) {
                // if we are recursively erroring while exiting
                // from a fatal error, just kill the entire
                // process immediately, which will prevent a
                // full screen rendering window covering the
                // error dialog
                win_main.Sys_Printf("FATAL: recursed fatal error:\n%s\n", errorMessage[0])

                idStr.vsnPrintf(errorMessage, MAX_PRINT_MSG_SIZE, fmt, *args)
                win_main.Sys_Printf("%s\n", errorMessage[0])
                // write the console to a log file?
                win_main.Sys_Quit()
            }
            com_errorEntered = TempDump.etoi(errorParm_t.ERP_FATAL)

            idStr.vsnPrintf(errorMessage, MAX_PRINT_MSG_SIZE, fmt, *args)
            if (cvarSystem.GetCVarBool("r_fullscreen")) {
                CmdSystem.cmdSystem.BufferCommandText(cmdExecution_t.CMD_EXEC_NOW, "vid_restart partial windowed\n")
            }
            win_main.Sys_Printf(
                "shutting down: %s\n", errorMessage[0]
            ) // FIX: was missing — C++ prints this before shutdown
            win_main.Sys_SetFatalError(errorMessage[0])
            Shutdown()
            win_main.Sys_Error("%s", errorMessage[0])
        }

        override fun GetLanguageDict(): idLangDict {
            return languageDict
        }

        /*
         ===============
         KeysFromBinding()
         Returns the key bound to the command
         ===============
         */
        override fun KeysFromBinding(bind: String): String {
            return idKeyInput.KeysFromBinding(bind)
        }

        /*
         ===============
         BindingFromKey()
         Returns the binding bound to key
         ===============
         */
        override fun BindingFromKey(key: String): String {
            return idKeyInput.BindingFromKey(key)!!
        }

        /*
         ===============
         ButtonState()
         Returns the state of the button
         ===============
         */
        override fun ButtonState(key: Int): Int {
            return UsercmdGen.usercmdGen.ButtonState(key)
        }

        /*
         ===============
         ButtonState()
         Returns the state of the key
         ===============
         */
        override fun KeyState(key: Int): Int {
            return UsercmdGen.usercmdGen.KeyState(key)
        }

        @Throws(idException::class)
        fun InitGame() {
            // initialize the file system
            FileSystem_h.fileSystem.Init()

            // initialize the declaration manager
            DeclManager.declManager.Init()

            // force r_fullscreen 0 if running a tool
            CheckToolMode()
            var file = FileSystem_h.fileSystem.OpenExplicitFileRead(
                FileSystem_h.fileSystem.RelativePathToOSPath(
                    Licensee.CONFIG_SPEC, "fs_savepath"
                )
            )
            val sysDetect = null == file
            if (!sysDetect) {
                FileSystem_h.fileSystem.CloseFile(file!!)
            } else {
                file = FileSystem_h.fileSystem.OpenFileWrite(Licensee.CONFIG_SPEC)
                FileSystem_h.fileSystem.CloseFile(file!!)
            }
            val args = CmdArgs.idCmdArgs()
            if (sysDetect) {
                SetMachineSpec()
                Com_ExecMachineSpec_f.getInstance().run(args)
            }

            // initialize the renderSystem data structures, but don't start OpenGL yet
            RenderSystem.renderSystem.Init()

            // initialize string database right off so we can use it for loading messages
            InitLanguageDict()
            PrintLoadingMessage(common.GetLanguageDict().GetString("#str_04344"))

            // load the font, etc
            console.LoadGraphics()
            // init journalling, etc
            EventLoop.eventLoop.Init()
            PrintLoadingMessage(common.GetLanguageDict().GetString("#str_04345"))

            // exec the startup scripts
            CmdSystem.cmdSystem.BufferCommandText(cmdExecution_t.CMD_EXEC_APPEND, "exec editor.cfg\n")
            CmdSystem.cmdSystem.BufferCommandText(cmdExecution_t.CMD_EXEC_APPEND, "exec default.cfg\n")

            // skip the config file if "safe" is on the command line
            if (!SafeMode()) {
                CmdSystem.cmdSystem.BufferCommandText(
                    cmdExecution_t.CMD_EXEC_APPEND, """
     exec ${Licensee.CONFIG_FILE}
     
     """.trimIndent()
                )
            }
            CmdSystem.cmdSystem.BufferCommandText(cmdExecution_t.CMD_EXEC_APPEND, "exec autoexec.cfg\n")

            // reload the language dictionary now that we've loaded config files
            CmdSystem.cmdSystem.BufferCommandText(cmdExecution_t.CMD_EXEC_APPEND, "reloadLanguage\n")

            // run cfg execution
            CmdSystem.cmdSystem.ExecuteCommandBuffer()

            // re-override anything from the config files with command line args
            StartupVariable(null, false)

            // if any archived cvars are modified after this, we will trigger a writing of the config file
            cvarSystem.ClearModifiedFlags(CVarSystem.CVAR_ARCHIVE)

            // cvars are initialized, but not the rendering system. Allow preference startup dialog
            win_main.Sys_DoPreferences()

            // init the user command input code
            UsercmdGen.usercmdGen.Init()
            PrintLoadingMessage(common.GetLanguageDict().GetString("#str_04346"))

            // start the sound system, but don't do any hardware operations yet
            snd_system.soundSystem.Init()
            PrintLoadingMessage(common.GetLanguageDict().GetString("#str_04347"))

            // init async network
            idAsyncNetwork.Init()
            if (ID_DEDICATED) {
                idAsyncNetwork.server.InitPort()
                cvarSystem.SetCVarBool("s_noSound", true)
            } else {
                if (idAsyncNetwork.serverDedicated.GetInteger() == 1) {
                    idAsyncNetwork.server.InitPort()
                    cvarSystem.SetCVarBool("s_noSound", true)
                } else {
                    // init OpenGL, which will open a window and connect sound and input hardware
                    PrintLoadingMessage(common.GetLanguageDict().GetString("#str_04348"))
                    InitRenderSystem()
                }
            }
            PrintLoadingMessage(common.GetLanguageDict().GetString("#str_04349"))

            // initialize the user interfaces
            UserInterface.uiManager.Init()

            // startup the script debugger
            // DebuggerServerInit();
            PrintLoadingMessage(common.GetLanguageDict().GetString("#str_04350"))

            // load the game dll
            LoadGameDLL()
            PrintLoadingMessage(common.GetLanguageDict().GetString("#str_04351"))

            // init the session
            Session.session.Init()

            // have to do this twice.. first one sets the correct r_mode for the renderer init
            // this time around the backend is all setup correct.. a bit fugly but do not want
            // to mess with all the gl init at this point.. an old vid card will never qualify for
            if (sysDetect) {
                SetMachineSpec()
                Com_ExecMachineSpec_f.getInstance().run(args)
                cvarSystem.SetCVarInteger("s_numberOfSpeakers", 6)
                CmdSystem.cmdSystem.BufferCommandText(cmdExecution_t.CMD_EXEC_NOW, "s_restart\n")
                CmdSystem.cmdSystem.ExecuteCommandBuffer()
            }
        }

        fun ShutdownGame(reloading: Boolean) {

            // kill sound first
            val sw = snd_system.soundSystem.GetPlayingSoundWorld()
            sw?.StopAllSounds()
            snd_system.soundSystem.ClearBuffer()

            // shutdown the script debugger
            // DebuggerServerShutdown();
            idAsyncNetwork.client.Shutdown()

            // shut down the session
            Session.session.Shutdown()

            // shut down the user interfaces
            UserInterface.uiManager.Shutdown()

            // shut down the sound system
            snd_system.soundSystem.Shutdown()

            // shut down async networking
            idAsyncNetwork.Shutdown()

            // shut down the user command input code
            UsercmdGen.usercmdGen.Shutdown()

            // shut down the event loop
            EventLoop.eventLoop.Shutdown()

            // shut down the renderSystem
            RenderSystem.renderSystem.Shutdown()

            // shutdown the decl manager
            DeclManager.declManager.Shutdown()

            // unload the game dll
            UnloadGameDLL()

            // only shut down the log file after all output is done
            CloseLogFile()

            // shut down the file system
            FileSystem_h.fileSystem.Shutdown(reloading)
        }

        // localization
        @Throws(idException::class)
        fun InitLanguageDict() {
            languageDict.Clear()

            //D3XP: Instead of just loading a single lang file for each language
            //we are going to load all files that begin with the language name
            //similar to the way pak files work. So you can place english001.lang
            //to add new strings to the english language dictionary
            val langFiles: idFileList?
            langFiles = FileSystem_h.fileSystem.ListFilesTree("strings", ".lang", true)
            val langList = langFiles.GetList()
            StartupVariable(
                "sys_lang", false
            ) // let it be set on the command line - this is needed because this init happens very early
            var langName = idStr(cvarSystem.GetCVarString("sys_lang"))

            //Loop through the list and filter
            var currentLangList = langList
            FilterLangList(currentLangList, langName)
            if (currentLangList.size() == 0) {
                // reset cvar to default and try to load again
                CmdSystem.cmdSystem.BufferCommandText(cmdExecution_t.CMD_EXEC_NOW, "reset sys_lang")
                langName = idStr(cvarSystem.GetCVarString("sys_lang"))
                currentLangList = langList
                FilterLangList(currentLangList, langName)
            }
            for (i in 0 until currentLangList.size()) {
                //common.Printf("%s\n", currentLangList[i].c_str());
                languageDict.Load(currentLangList[i].toString(), false)
            }
            FileSystem_h.fileSystem.FreeFileList(langFiles)
            win_input.Sys_InitScanTable()
        }

        @Throws(idException::class)
        fun LocalizeGui(fileName: String, langDict: idLangDict) {
            val out = idStr()
            val ws = idStr()
            var work: idStr
            val buffer = arrayOfNulls<ByteBuffer>(1)
            out.Empty()
            var k: Int
            var ch: Char
            val slash = '\\'
            val tab = 't'
            val nl = 'n'
            val src =
                idLexer(Lexer.LEXFL_NOFATALERRORS or Lexer.LEXFL_NOSTRINGCONCAT or Lexer.LEXFL_ALLOWMULTICHARLITERALS or Lexer.LEXFL_ALLOWBACKSLASHSTRINGCONCAT)
            if (FileSystem_h.fileSystem.ReadFile(fileName, buffer) > 0) {
                src.LoadMemory(TempDump.bbtocb(buffer[0]!!), TempDump.bbtocb(buffer[0]!!).capacity(), fileName)
                if (src.IsLoaded()) {
                    val outFile = FileSystem_h.fileSystem.OpenFileWrite(fileName)!!
                    common.Printf("Processing %s\n", fileName)
                    Session.session.UpdateScreen()
                    val token = idToken()
                    while (src.ReadToken(token)) {
                        src.GetLastWhiteSpace(ws)
                        out.Append(ws)
                        if (token.type == Token.TT_STRING) {
                            out.Append(Str.va("\"%s\"", token))
                        } else {
                            out.Append(token)
                        }
                        if (out.Length() > 200000) {
                            outFile.WriteString(out /*, out.Length()*/)
                            out.set("")
                        }
                        work = token.Right(6)
                        if (token.Icmp("text") == 0 || work.Icmp("::text") == 0 || token.Icmp("choices") == 0) {
                            if (src.ReadToken(token)) {
                                // see if already exists, if so save that id to this position in this file
                                // otherwise add this to the list and save the id to this position in this file
                                src.GetLastWhiteSpace(ws)
                                out.Append(ws)
                                token.set(langDict.AddString(token.toString()))
                                out.Append("\"")
                                k = 0
                                while (k < token.Length()) {
                                    ch = token[k]
                                    if (ch == '\t') {
                                        out.Append(slash)
                                        out.Append(tab)
                                    } else if (ch == '\n' || ch == '\r') {
                                        out.Append(slash)
                                        out.Append(nl)
                                    } else {
                                        out.Append(ch)
                                    }
                                    k++
                                }
                                out.Append("\"")
                            }
                        } else if (token.Icmp("comment") == 0) {
                            if (src.ReadToken(token)) {
                                // need to write these out by hand to preserve any \n's
                                // see if already exists, if so save that id to this position in this file
                                // otherwise add this to the list and save the id to this position in this file
                                src.GetLastWhiteSpace(ws)
                                out.Append(ws)
                                out.Append("\"")
                                k = 0
                                while (k < token.Length()) {
                                    ch = token[k]
                                    if (ch == '\t') {
                                        out.Append(slash)
                                        out.Append(tab)
                                    } else if (ch == '\n' || ch == '\r') {
                                        out.Append(slash)
                                        out.Append(nl)
                                    } else {
                                        out.Append(ch)
                                    }
                                    k++
                                }
                                out.Append("\"")
                            }
                        }
                    }
                    outFile.WriteString(out)
                    FileSystem_h.fileSystem.CloseFile(outFile)
                }
                FileSystem_h.fileSystem.FreeFile(buffer)
            }
        }

        @Throws(idException::class)
        fun LocalizeMapData(fileName: String, langDict: idLangDict) {
            val buffer = arrayOfNulls<ByteBuffer>(1)
            val src =
                idLexer(Lexer.LEXFL_NOFATALERRORS or Lexer.LEXFL_NOSTRINGCONCAT or Lexer.LEXFL_ALLOWMULTICHARLITERALS or Lexer.LEXFL_ALLOWBACKSLASHSTRINGCONCAT)
            common.SetRefreshOnPrint(true)
            if (FileSystem_h.fileSystem.ReadFile(fileName, buffer) > 0) {
                src.LoadMemory(TempDump.bbtocb(buffer[0]!!), TempDump.bbtocb(buffer[0]!!).capacity(), fileName)
                if (src.IsLoaded()) {
                    common.Printf("Processing %s\n", fileName)
                    var mapFileName: idStr?
                    val token = idToken()
                    val token2 = idToken()
                    val replaceArgs = idLangDict()
                    while (src.ReadToken(token)) {
                        mapFileName = token
                        replaceArgs.Clear()
                        src.ExpectTokenString("{")
                        while (src.ReadToken(token)) {
                            if (token.toString() == "}") {
                                break
                            }
                            if (src.ReadToken(token2)) {
                                if (token2.toString() == "}") {
                                    break
                                }
                                replaceArgs.AddKeyVal(token.toString(), token2.toString())
                            }
                        }
                        common.Printf("  localizing map %s...\n", mapFileName)
                        LocalizeSpecificMapData(mapFileName.toString(), langDict, replaceArgs)
                    }
                }
                FileSystem_h.fileSystem.FreeFile(buffer)
            }
            common.SetRefreshOnPrint(false)
        }

        @Throws(idException::class)
        fun LocalizeSpecificMapData(fileName: String, langDict: idLangDict, replaceArgs: idLangDict) {
            val map = idMapFile()
            if (map.Parse(fileName, false, false)) {
                val count = map.GetNumEntities()
                for (i in 0 until count) {
                    val ent = map.GetEntity(i)
                    if (ent != null) {
                        for (j in 0 until replaceArgs.GetNumKeyVals()) {
                            val kv = replaceArgs.GetKeyVal(j)
                            val temp = ent.epairs.GetString(kv.key.toString())
                            if (temp != null && !temp.isEmpty()) {
                                val `val` = kv.value
                                if (`val`.toString() == temp) {
                                    ent.epairs.Set(kv.key.toString(), langDict.AddString(temp))
                                }
                            }
                        }
                    }
                }
                map.Write(fileName, ".map")
            }
        }

        @Throws(idException::class)
        fun SetMachineSpec() {
            val cpuid_t = win_main.Sys_GetProcessorId()
            val ghz = win_cpu.Sys_ClockTicksPerSecond() * 0.000000001
            val cores = Runtime.getRuntime().availableProcessors()
            val vidRam = 512 // Sys_GetVideoRam();
            val sysRam = win_shared.Sys_GetSystemRam()
            val oldCard = booleanArrayOf(false)
            Printf(
                """Detected
 	%d x %.2f GHz CPU
	%d MB of System memory
	%d MB of Video memory on %s

""",
                cores,
                ghz,
                sysRam,
                vidRam,
                if (oldCard[0]) "a less than optimal video architecture" else "an optimal video architecture"
            )
            val cpuGhz = if (cpuid_t and sys_public.CPUID_AMD != 0) 1.9 else 2.19
            val cpuGhzPart2 = if (cpuid_t and sys_public.CPUID_AMD != 0) 1.1 else 1.25
            if (ghz >= 2.75 && vidRam >= 512 && sysRam >= 1024 && !oldCard[0]) { //TODO:try to make this shit work.
                Printf("This system qualifies for Ultra quality!\n")
                com_machineSpec.SetInteger(3)
            } else if (ghz >= cpuGhz && vidRam >= 256 && sysRam >= 512 && !oldCard[0]) {
                Printf("This system qualifies for High quality!\n")
                com_machineSpec.SetInteger(2)
            } else if (ghz >= cpuGhzPart2 && vidRam >= 128 && sysRam >= 384) {
                Printf("This system qualifies for Medium quality.\n")
                com_machineSpec.SetInteger(1)
            } else {
                Printf("This system qualifies for Low quality.\n")
                com_machineSpec.SetInteger(0)
            }
            com_videoRam.SetInteger(vidRam)
        }

        @Throws(idException::class)
        private fun InitCommands() {
            CmdSystem.cmdSystem.AddCommand(
                "error", Com_Error_f.getInstance(), CmdSystem.CMD_FL_SYSTEM or CmdSystem.CMD_FL_CHEAT, "causes an error"
            )
            CmdSystem.cmdSystem.AddCommand(
                "crash", Com_Crash_f.getInstance(), CmdSystem.CMD_FL_SYSTEM or CmdSystem.CMD_FL_CHEAT, "causes a crash"
            )
            CmdSystem.cmdSystem.AddCommand(
                "freeze",
                Com_Freeze_f.getInstance(),
                CmdSystem.CMD_FL_SYSTEM or CmdSystem.CMD_FL_CHEAT,
                "freezes the game for a number of seconds"
            )
            CmdSystem.cmdSystem.AddCommand("quit", Com_Quit_f.getInstance(), CmdSystem.CMD_FL_SYSTEM, "quits the game")
            CmdSystem.cmdSystem.AddCommand("exit", Com_Quit_f.getInstance(), CmdSystem.CMD_FL_SYSTEM, "exits the game")
            CmdSystem.cmdSystem.AddCommand(
                "writeConfig", Com_WriteConfig_f.getInstance(), CmdSystem.CMD_FL_SYSTEM, "writes a config file"
            )
            CmdSystem.cmdSystem.AddCommand(
                "reloadEngine",
                Com_ReloadEngine_f.getInstance(),
                CmdSystem.CMD_FL_SYSTEM,
                "reloads the engine down to including the file system"
            )
            CmdSystem.cmdSystem.AddCommand(
                "setMachineSpec",
                Com_SetMachineSpec_f.getInstance(),
                CmdSystem.CMD_FL_SYSTEM,
                "detects system capabilities and sets com_machineSpec to appropriate value"
            )
            CmdSystem.cmdSystem.AddCommand(
                "execMachineSpec",
                Com_ExecMachineSpec_f.getInstance(),
                CmdSystem.CMD_FL_SYSTEM,
                "execs the appropriate config files and sets cvars based on com_machineSpec"
            )
            if (!ID_DEMO_BUILD && !ID_DEDICATED) {
                // compilers
                CmdSystem.cmdSystem.AddCommand(
                    "dmap",
                    Dmap_f.getInstance(),
                    CmdSystem.CMD_FL_TOOL,
                    "compiles a map",
                    ArgCompletion_MapName.getInstance()
                )
                CmdSystem.cmdSystem.AddCommand(
                    "renderbump",
                    RenderBump_f.getInstance(),
                    CmdSystem.CMD_FL_TOOL,
                    "renders a bump map",
                    ArgCompletion_ModelName.getInstance()
                )
                CmdSystem.cmdSystem.AddCommand(
                    "renderbumpFlat",
                    RenderBumpFlat_f.getInstance(),
                    CmdSystem.CMD_FL_TOOL,
                    "renders a flat bump map",
                    ArgCompletion_ModelName.getInstance()
                )
                CmdSystem.cmdSystem.AddCommand(
                    "runAAS",
                    RunAAS_f.getInstance(),
                    CmdSystem.CMD_FL_TOOL,
                    "compiles an AAS file for a map",
                    ArgCompletion_MapName.getInstance()
                )
                CmdSystem.cmdSystem.AddCommand(
                    "runAASDir",
                    RunAASDir_f.getInstance(),
                    CmdSystem.CMD_FL_TOOL,
                    "compiles AAS files for all maps in a folder",
                    ArgCompletion_MapName.getInstance()
                )
                CmdSystem.cmdSystem.AddCommand(
                    "runReach",
                    RunReach_f.getInstance(),
                    CmdSystem.CMD_FL_TOOL,
                    "calculates reachability for an AAS file",
                    ArgCompletion_MapName.getInstance()
                )
                CmdSystem.cmdSystem.AddCommand(
                    "roq", RoQFileEncode_f.getInstance(), CmdSystem.CMD_FL_TOOL, "encodes a roq file"
                )
            }
            if (ID_ALLOW_TOOLS) {
                // editors
                CmdSystem.cmdSystem.AddCommand(
                    "editor", Com_Editor_f.getInstance(), CmdSystem.CMD_FL_TOOL, "launches the level editor Radiant"
                )
                CmdSystem.cmdSystem.AddCommand(
                    "editLights",
                    Com_EditLights_f.getInstance(),
                    CmdSystem.CMD_FL_TOOL,
                    "launches the in-game Light Editor"
                )
                CmdSystem.cmdSystem.AddCommand(
                    "editSounds",
                    Com_EditSounds_f.getInstance(),
                    CmdSystem.CMD_FL_TOOL,
                    "launches the in-game Sound Editor"
                )
                CmdSystem.cmdSystem.AddCommand(
                    "editDecls",
                    Com_EditDecls_f.getInstance(),
                    CmdSystem.CMD_FL_TOOL,
                    "launches the in-game Declaration Editor"
                )
                CmdSystem.cmdSystem.AddCommand(
                    "editAFs",
                    Com_EditAFs_f.getInstance(),
                    CmdSystem.CMD_FL_TOOL,
                    "launches the in-game Articulated Figure Editor"
                )
                CmdSystem.cmdSystem.AddCommand(
                    "editParticles",
                    Com_EditParticles_f.getInstance(),
                    CmdSystem.CMD_FL_TOOL,
                    "launches the in-game Particle Editor"
                )
                CmdSystem.cmdSystem.AddCommand(
                    "editScripts",
                    Com_EditScripts_f.getInstance(),
                    CmdSystem.CMD_FL_TOOL,
                    "launches the in-game Script Editor"
                )
                CmdSystem.cmdSystem.AddCommand(
                    "editGUIs", Com_EditGUIs_f.getInstance(), CmdSystem.CMD_FL_TOOL, "launches the GUI Editor"
                )
                CmdSystem.cmdSystem.AddCommand(
                    "editPDAs", Com_EditPDAs_f.getInstance(), CmdSystem.CMD_FL_TOOL, "launches the in-game PDA Editor"
                )
                CmdSystem.cmdSystem.AddCommand(
                    "debugger",
                    Com_ScriptDebugger_f.getInstance(),
                    CmdSystem.CMD_FL_TOOL,
                    "launches the Script Debugger"
                )

                //BSM Nerve: Add support for the material editor
                CmdSystem.cmdSystem.AddCommand(
                    "materialEditor",
                    Com_MaterialEditor_f.getInstance(),
                    CmdSystem.CMD_FL_TOOL,
                    "launches the Material Editor"
                )
            }
            CmdSystem.cmdSystem.AddCommand(
                "printMemInfo", PrintMemInfo_f.getInstance(), CmdSystem.CMD_FL_SYSTEM, "prints memory debugging data"
            )

            // idLib commands
            CmdSystem.cmdSystem.AddCommand(
                "showStringMemory",
                idStr.ShowMemoryUsage_f.getInstance(),
                CmdSystem.CMD_FL_SYSTEM,
                "shows memory used by strings"
            )
            CmdSystem.cmdSystem.AddCommand(
                "showDictMemory",
                idDict.ShowMemoryUsage_f.getInstance(),
                CmdSystem.CMD_FL_SYSTEM,
                "shows memory used by dictionaries"
            )
            CmdSystem.cmdSystem.AddCommand(
                "listDictKeys",
                ListKeys_f.getInstance(),
                CmdSystem.CMD_FL_SYSTEM or CmdSystem.CMD_FL_CHEAT,
                "lists all keys used by dictionaries"
            )
            CmdSystem.cmdSystem.AddCommand(
                "listDictValues",
                ListValues_f.getInstance(),
                CmdSystem.CMD_FL_SYSTEM or CmdSystem.CMD_FL_CHEAT,
                "lists all values used by dictionaries"
            )

            // localization
            CmdSystem.cmdSystem.AddCommand(
                "localizeGuis",
                Com_LocalizeGuis_f.getInstance(),
                CmdSystem.CMD_FL_SYSTEM or CmdSystem.CMD_FL_CHEAT,
                "localize guis"
            )
            CmdSystem.cmdSystem.AddCommand(
                "localizeMaps",
                Com_LocalizeMaps_f.getInstance(),
                CmdSystem.CMD_FL_SYSTEM or CmdSystem.CMD_FL_CHEAT,
                "localize maps"
            )
            CmdSystem.cmdSystem.AddCommand(
                "reloadLanguage", Com_ReloadLanguage_f.getInstance(), CmdSystem.CMD_FL_SYSTEM, "reload language dict"
            )

            //D3XP Localization
            CmdSystem.cmdSystem.AddCommand(
                "localizeGuiParmsTest",
                Com_LocalizeGuiParmsTest_f.getInstance(),
                CmdSystem.CMD_FL_SYSTEM,
                "Create test files that show gui parms localized and ignored."
            )
            CmdSystem.cmdSystem.AddCommand(
                "localizeMapsTest",
                Com_LocalizeMapsTest_f.getInstance(),
                CmdSystem.CMD_FL_SYSTEM,
                "Create test files that shows which strings will be localized."
            )

            // build helpers
            CmdSystem.cmdSystem.AddCommand(
                "startBuild",
                Com_StartBuild_f.getInstance(),
                CmdSystem.CMD_FL_SYSTEM or CmdSystem.CMD_FL_CHEAT,
                "prepares to make a build"
            )
            CmdSystem.cmdSystem.AddCommand(
                "finishBuild",
                Com_FinishBuild_f.getInstance(),
                CmdSystem.CMD_FL_SYSTEM or CmdSystem.CMD_FL_CHEAT,
                "finishes the build process"
            )
            if (ID_DEDICATED) {
                CmdSystem.cmdSystem.AddCommand("help", Com_Help_f.getInstance(), CmdSystem.CMD_FL_SYSTEM, "shows help")
            }
        }

        private fun InitRenderSystem() {
            if (com_skipRenderer.GetBool()) {
                return
            }
            RenderSystem.renderSystem.InitOpenGL()
            PrintLoadingMessage(common.GetLanguageDict().GetString("#str_04343"))
        }

        private fun InitSIMD() {
            idSIMD.InitProcessor("doom", com_forceGenericSIMD.GetBool())
            com_forceGenericSIMD.ClearModified()
        }

        /*
         ==================
         idCommonLocal::AddStartupCommands

         Adds command line parameters as script statements
         Commands are separated by + signs

         Returns true if any late commands were added, which
         will keep the demoloop from immediately starting
         ==================
         */
        @Throws(idException::class)
        private fun AddStartupCommands(): Boolean {
            var i: Int
            var added: Boolean
            added = false
            // quote every token, so args with semicolons can work
            i = 0
            while (i < com_numConsoleLines) {
                if (0 == com_consoleLines[i].Argc()) {
                    i++
                    continue
                }

                // set commands won't override menu startup
                if (idStr.Icmpn(com_consoleLines[i].Argv(0), "set", 3) != 0) {
                    added = true
                }
                // directly as tokenized so nothing gets screwed
                CmdSystem.cmdSystem.BufferCommandArgs(cmdExecution_t.CMD_EXEC_APPEND, com_consoleLines[i])
                i++
            }
            return added
        }

        private fun ParseCommandLine(argc: Int, argv: Array<String> = emptyArray()) {
            var i: Int
            val current_count: Int
            com_numConsoleLines = 0
            current_count = 0
            // API says no program path
            i = 0
            while (i < argc) {
                if (argv[i][0] == '+') {
                    com_numConsoleLines++
                    com_consoleLines[com_numConsoleLines - 1].AppendArg(argv[i].substring(1))
                } else {
                    if (0 == com_numConsoleLines) {
                        com_numConsoleLines++
                    }
                    com_consoleLines[com_numConsoleLines - 1].AppendArg(argv[i])
                }
                i++
            }
        }

        private fun ClearCommandLine() {
            com_numConsoleLines = 0
        }

        /*
         ==================
         idCommonLocal::SafeMode

         Check for "safe" on the command line, which will
         skip loading of config file (DoomConfig.cfg)
         ==================
         */
        private fun SafeMode(): Boolean {
            var i: Int
            i = 0
            while (i < com_numConsoleLines) {
                if (0 == idStr.Icmp(com_consoleLines[i].Argv(0), "safe") || 0 == idStr.Icmp(
                        com_consoleLines[i].Argv(0), "cvar_restart"
                    )
                ) {
                    com_consoleLines[i].Clear()
                    return true
                }
                i++
            }
            return false
        }

        /*
         ==================
         idCommonLocal::CheckToolMode

         Check for "renderbump", "dmap", or "editor" on the command line,
         and force fullscreen off in those cases
         ==================
         */
        private fun CheckToolMode() {
            var i: Int
            i = 0
            while (i < com_numConsoleLines) {
                if (0 == idStr.Icmp(com_consoleLines[i].Argv(0), "guieditor")) {
                    com_editors = com_editors or EDITOR_GUI
                } else if (0 == idStr.Icmp(com_consoleLines[i].Argv(0), "debugger")) {
                    com_editors = com_editors or EDITOR_DEBUGGER
                } else if (0 == idStr.Icmp(com_consoleLines[i].Argv(0), "editor")) {
                    com_editors = com_editors or EDITOR_RADIANT
                } // Nerve: Add support for the material editor
                else if (0 == idStr.Icmp(com_consoleLines[i].Argv(0), "materialEditor")) {
                    com_editors = com_editors or EDITOR_MATERIAL
                }
                if (0 == idStr.Icmp(
                        com_consoleLines[i].Argv(0), "renderbump"
                    ) || 0 == idStr.Icmp(
                        com_consoleLines[i].Argv(0), "editor"
                    ) || 0 == idStr.Icmp(
                        com_consoleLines[i].Argv(0), "guieditor"
                    ) || 0 == idStr.Icmp(
                        com_consoleLines[i].Argv(0), "debugger"
                    ) || 0 == idStr.Icmp(
                        com_consoleLines[i].Argv(0), "dmap"
                    ) || 0 == idStr.Icmp(com_consoleLines[i].Argv(0), "materialEditor")
                ) {
                    cvarSystem.SetCVarBool("r_fullscreen", false)
                    return
                }
                i++
            }
        }

        private fun CloseLogFile() {
            if (logFile != null) {
                com_logFile.SetBool(false) // make sure no further VPrintf attempts to open the log file again
                FileSystem_h.fileSystem.CloseFile(logFile!!)
                logFile = null
            }
        }

        /*
         ===============
         idCommonLocal::WriteConfiguration

         Writes key bindings and archived cvars to config file if modified
         ===============
         */
        private fun WriteConfiguration() {
            // if we are quiting without fully initializing, make sure
            // we don't write out anything
            if (!com_fullyInitialized) {
                return
            }
            if (0 == cvarSystem.GetModifiedFlags() and CVarSystem.CVAR_ARCHIVE) {
                return
            }
            cvarSystem.ClearModifiedFlags(CVarSystem.CVAR_ARCHIVE)

            // disable printing out the "Writing to:" message
            val developer = com_developer.GetBool()
            com_developer.SetBool(false)
            WriteConfigToFile(Licensee.CONFIG_FILE)
            Session.session.WriteCDKey()

            // restore the developer cvar
            com_developer.SetBool(developer)
        }

        private fun DumpWarnings() {
            var i: Int
            val warningFile: idFile?
            if (0 == warningList.size()) {
                return
            }
            warningFile = FileSystem_h.fileSystem.OpenFileWrite("warnings.txt", "fs_savepath")
            if (warningFile != null) {
                warningFile.Printf("------------- Warnings ---------------\n\n")
                warningFile.Printf("during %s...\n", warningCaption)
                warningList.sort()
                i = 0
                while (i < warningList.size()) {
                    warningList[i].RemoveColors()
                    warningFile.Printf("WARNING: %s\n", warningList[i])
                    i++
                }
                if (warningList.size() >= MAX_WARNING_LIST) {
                    warningFile.Printf("\nmore than %d warnings!\n", MAX_WARNING_LIST)
                } else {
                    warningFile.Printf("\n%d warnings.\n", warningList.size())
                }
                warningFile.Printf("\n\n-------------- Errors ---------------\n\n")
                errorList.sort()
                i = 0
                while (i < errorList.size()) {
                    errorList[i].RemoveColors()
                    warningFile.Printf("ERROR: %s", errorList[i])
                    i++
                }
                warningFile.ForceFlush()
                FileSystem_h.fileSystem.CloseFile(warningFile)
                if (_WIN32 && !_DEBUG) {
                    val osPath: String?
                    osPath = FileSystem_h.fileSystem.RelativePathToOSPath("warnings.txt", "fs_savepath")
                    try {
                        Runtime.getRuntime().exec(Str.va("Notepad.exe %s", osPath))
                    } catch (ex: IOException) {
                        Logger.getLogger(Common::class.java.name).log(Level.SEVERE, null, ex)
                    }
                }
            }
        }

        fun SingleAsyncTic() {
            // main thread code can prevent this from happening while modifying
            // critical data structures
            val stat = com_asyncStats[com_ticNumber and MAX_ASYNC_STATS - 1] //memset( stat, 0, sizeof( *stat ) );
            stat.milliseconds = win_shared.Sys_Milliseconds()
            stat.deltaMsec = stat.milliseconds - com_asyncStats[com_ticNumber - 1 and MAX_ASYNC_STATS - 1].milliseconds
            if (UsercmdGen.usercmdGen != null && com_asyncInput.GetBool()) {
                UsercmdGen.usercmdGen.UsercmdInterrupt()
            }
            // FIX: Cases 1,3 both call AsyncUpdateWrite (dhewm3 fall-through); case 2 calls AsyncUpdate
            when (com_asyncSound.GetInteger()) {
                1, 3 -> snd_system.soundSystem.AsyncUpdateWrite(stat.milliseconds)
                2 -> snd_system.soundSystem.AsyncUpdate(stat.milliseconds)
            }

            // we update com_ticNumber after all the background tasks
            // have completed their work for this tic
            com_ticNumber++
            stat.timeConsumed = win_shared.Sys_Milliseconds() - stat.milliseconds
        }

        @Throws(idException::class)
        private fun LoadGameDLL() {
            if (Game_local.game != null) {
                Game_local.game.Init()
            }
        }

        private fun UnloadGameDLL() {
            // shut down the game object
            if (Game_local.game != null) {
                Game_local.game.Shutdown()
            }
        }

        private fun PrintLoadingMessage(msg: String?) {
            if (msg == null || msg.isEmpty()) {
                return
            }
            RenderSystem.renderSystem.BeginFrame(
                RenderSystem.renderSystem.GetScreenWidth(), RenderSystem.renderSystem.GetScreenHeight()
            )
            RenderSystem.renderSystem.DrawStretchPic(
                0.0f,
                0.0f,
                RenderSystem.SCREEN_WIDTH.toFloat(),
                RenderSystem.SCREEN_HEIGHT.toFloat(),
                0.0f,
                0.0f,
                1.0f,
                1.0f,
                DeclManager.declManager.FindMaterial("splashScreen")
            )
            val len = msg.length
            RenderSystem.renderSystem.DrawSmallStringExt(
                (640 - len * RenderSystem.SMALLCHAR_WIDTH) / 2,
                410,
                msg.toCharArray(),
                idVec4(0.0f, 0.81f, 0.94f, 1.0f),
                true,
                DeclManager.declManager.FindMaterial("textures/bigchars")
            )
            RenderSystem.renderSystem.EndFrame(null, null)
        }

        private fun FilterLangList(list: idStrList, lang: idStr) {
            var temp: idStr
            var i = 0
            while (i < list.size()) {
                temp = list.get(i)
                temp = temp.Right(temp.Length() - "strings/".length)
                temp = temp.Left(lang.Length())
                if (idStr.Icmp(temp, lang) != 0) {
                    list.removeAtIndex(i)
                    i--
                }
                i++
            }
        }

        internal class asyncStats_t {
            var clientPacketsReceived = 0
            var deltaMsec = 0 // should always be 16
            var milliseconds = 0 // should always be incremeting by 60hz
            var mostRecentServerPacketSequence = 0
            var serverPacketsReceived = 0
            var timeConsumed = 0// msec spent in Com_AsyncThread()
        }

        companion object {
            //
            const val MAX_CONSOLE_LINES = 32
            private const val MAX_ASYNC_STATS = 1024
            var DEBUG_fraction = 0f
            var errorCount = 0
            var lastErrorTime = 0
            var logFileFailed = false
            var recursing = false
            private var lastTime = 0
        }

        init {
            errorList = idStrList()
            languageDict = idLangDict()
            gameDLL = 0
            com_asyncStats = Array(MAX_ASYNC_STATS) { asyncStats_t() }
            com_consoleLines = Array(MAX_CONSOLE_LINES) { CmdArgs.idCmdArgs() }
            if (ID_WRITE_VERSION) {
                config_compressor = null
            }
        }
    }

    /*
     ==================
     Com_Editor_f

     we can start the editor dynamically, but we won't ever get back
     ==================
     */
    internal class Com_Editor_f : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            edit_public.RadiantInit()
        }

        companion object {
            private val instance: cmdFunction_t = Com_Editor_f()
            fun getInstance(): cmdFunction_t {
                return instance
            }
        }
    }

    /*
     =============
     Com_ScriptDebugger_f
     =============
     */
    internal class Com_ScriptDebugger_f : cmdFunction_t() {
        @Throws(idException::class)
        override fun run(args: CmdArgs.idCmdArgs?) {
            // Make sure it wasnt on the command line
            if (0 == com_editors and EDITOR_DEBUGGER) {
                common.Printf("Script debugger is currently disabled\n")
                // DebuggerClientLaunch();
            }
        }

        companion object {
            private val instance: cmdFunction_t = Com_ScriptDebugger_f()
            fun getInstance(): cmdFunction_t {
                return instance
            }
        }
    }

    /*
     =============
     Com_EditGUIs_f
     =============
     */
    internal class Com_EditGUIs_f : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            edit_public.GUIEditorInit()
        }

        companion object {
            private val instance: cmdFunction_t = Com_EditGUIs_f()
            fun getInstance(): cmdFunction_t {
                return instance
            }
        }
    }

    /*
     =============
     Com_MaterialEditor_f
     =============
     */
    internal class Com_MaterialEditor_f : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            // Turn off sounds
            snd_system.soundSystem.SetMute(true)
            edit_public.MaterialEditorInit()
        }

        companion object {
            private val instance: cmdFunction_t = Com_MaterialEditor_f()
            fun getInstance(): cmdFunction_t {
                return instance
            }
        }
    }

    /*
     ============
     idCmdSystemLocal.PrintMemInfo_f

     This prints out memory debugging data
     ============
     */
    internal class PrintMemInfo_f : cmdFunction_t() {
        @Throws(idException::class)
        override fun run(args: CmdArgs.idCmdArgs?) {
            val mi = MemInfo_t() //memset( &mi, 0, sizeof( mi ) );
            mi.filebase.set(Session.session.GetCurrentMapName())
            RenderSystem.renderSystem.PrintMemInfo(mi) // textures and models
            snd_system.soundSystem.PrintMemInfo(mi) // sounds
            common.Printf(" Used image memory: %s bytes\n", idStr.FormatNumber(mi.imageAssetsTotal))
            mi.assetTotals += mi.imageAssetsTotal
            common.Printf(" Used model memory: %s bytes\n", idStr.FormatNumber(mi.modelAssetsTotal))
            mi.assetTotals += mi.modelAssetsTotal
            common.Printf(" Used sound memory: %s bytes\n", idStr.FormatNumber(mi.soundAssetsTotal))
            mi.assetTotals += mi.soundAssetsTotal
            common.Printf(" Used asset memory: %s bytes\n", idStr.FormatNumber(mi.assetTotals))

            // write overview file
            val f: idFile?
            f = FileSystem_h.fileSystem.OpenFileAppend("maps/printmeminfo.txt")
            if (null == f) {
                return
            }
            f.Printf(
                "total(%s ) image(%s ) model(%s ) sound(%s ): %s\n",
                idStr.FormatNumber(mi.assetTotals),
                idStr.FormatNumber(mi.imageAssetsTotal),
                idStr.FormatNumber(mi.modelAssetsTotal),
                idStr.FormatNumber(mi.soundAssetsTotal),
                mi.filebase
            )
            FileSystem_h.fileSystem.CloseFile(f)
        }

        companion object {
            private val instance: cmdFunction_t = PrintMemInfo_f()
            fun getInstance(): cmdFunction_t {
                return instance
            }
        }
    }

    /*
     ==================
     Com_EditLights_f
     ==================
     */
    internal class Com_EditLights_f : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            edit_public.LightEditorInit(idDict())
            cvarSystem.SetCVarInteger("g_editEntityMode", 1)
        }

        companion object {
            private val instance: cmdFunction_t = Com_EditLights_f()
            fun getInstance(): cmdFunction_t {
                return instance
            }
        }
    }

    /*
     ==================
     Com_EditSounds_f
     ==================
     */
    internal class Com_EditSounds_f : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            edit_public.SoundEditorInit(idDict())
            cvarSystem.SetCVarInteger("g_editEntityMode", 2)
        }

        companion object {
            private val instance: cmdFunction_t = Com_EditSounds_f()
            fun getInstance(): cmdFunction_t {
                return instance
            }
        }
    }

    /*
     ==================
     Com_EditDecls_f
     ==================
     */
    internal class Com_EditDecls_f : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            edit_public.DeclBrowserInit(idDict())
        }

        companion object {
            private val instance: cmdFunction_t = Com_EditDecls_f()
            fun getInstance(): cmdFunction_t {
                return instance
            }
        }
    }

    /*
     ==================
     Com_EditAFs_f
     ==================
     */
    internal class Com_EditAFs_f : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            edit_public.AFEditorInit(idDict())
        }

        companion object {
            private val instance: cmdFunction_t = Com_EditAFs_f()
            fun getInstance(): cmdFunction_t {
                return instance
            }
        }
    }

    /*
     ==================
     Com_EditParticles_f
     ==================
     */
    internal class Com_EditParticles_f : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            edit_public.ParticleEditorInit(idDict())
        }

        companion object {
            private val instance: cmdFunction_t = Com_EditParticles_f()
            fun getInstance(): cmdFunction_t {
                return instance
            }
        }
    }

    /*
     ==================
     Com_EditScripts_f
     ==================
     */
    internal class Com_EditScripts_f : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            edit_public.ScriptEditorInit(idDict())
        }

        companion object {
            private val instance: cmdFunction_t = Com_EditScripts_f()
            fun getInstance(): cmdFunction_t {
                return instance
            }
        }
    }

    /*
     ==================
     Com_EditPDAs_f
     ==================
     */
    internal class Com_EditPDAs_f : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            edit_public.PDAEditorInit(idDict())
        }

        companion object {
            private val instance: cmdFunction_t = Com_EditPDAs_f()
            fun getInstance(): cmdFunction_t {
                return instance
            }
        }
    }

    /*
     ==================
     Com_Error_f

     Just throw a fatal error to test error shutdown procedures.
     ==================
     */
    internal class Com_Error_f : cmdFunction_t() {
        @Throws(idException::class)
        override fun run(args: CmdArgs.idCmdArgs?) {
            if (!com_developer.GetBool()) {
                commonLocal.Printf("error may only be used in developer mode\n")
                return
            }
            if (args!!.Argc() > 1) {
                commonLocal.FatalError("Testing fatal error")
            } else {
                commonLocal.Error("Testing drop error")
            }
        }

        companion object {
            private val instance: cmdFunction_t = Com_Error_f()
            fun getInstance(): cmdFunction_t {
                return instance
            }
        }
    }

    /*
     ==================
     Com_Freeze_f

     Just freeze in place for a given number of seconds to test error recovery.
     ==================
     */
    internal class Com_Freeze_f : cmdFunction_t() {
        @Throws(idException::class)
        override fun run(args: CmdArgs.idCmdArgs?) {
            val s: Float
            val start: Int
            var now: Int
            if (args!!.Argc() != 2) {
                commonLocal.Printf("freeze <seconds>\n")
                return
            }
            if (!com_developer.GetBool()) {
                commonLocal.Printf("freeze may only be used in developer mode\n")
                return
            }
            s = args.Argv(1).toFloat() // FIX: C++ uses atof(); toInt().toFloat() truncated fractional seconds
            start = EventLoop.eventLoop.Milliseconds()
            while (true) {
                now = EventLoop.eventLoop.Milliseconds()
                if ((now - start) * 0.001 > s) {
                    break
                }
            }
        }

        companion object {
            private val instance: cmdFunction_t = Com_Freeze_f()
            fun getInstance(): cmdFunction_t {
                return instance
            }
        }
    }

    /*
     =================
     Com_Crash_f

     A way to force a bus error for development reasons
     =================
     */
    internal class Com_Crash_f : cmdFunction_t() {
        @Throws(idException::class)
        override fun run(args: CmdArgs.idCmdArgs?) {
            if (!com_developer.GetBool()) {
                commonLocal.Printf("crash may only be used in developer mode\n")
                //                return;
            }

        }

        companion object {
            private val instance: cmdFunction_t = Com_Crash_f()
            fun getInstance(): cmdFunction_t {
                return instance
            }
        }
    }

    /*
     =================
     Com_Quit_f
     =================
     */
    internal class Com_Quit_f : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            commonLocal.Quit()
        }

        companion object {
            private val instance: cmdFunction_t = Com_Quit_f()
            fun getInstance(): cmdFunction_t {
                return instance
            }
        }
    }

    /*
     ===============
     Com_WriteConfig_f

     Write the config file to a specific name
     ===============
     */
    internal class Com_WriteConfig_f : cmdFunction_t() {
        @Throws(idException::class)
        override fun run(args: CmdArgs.idCmdArgs?) {
            val filename: idStr
            if (args!!.Argc() != 2) {
                commonLocal.Printf("Usage: writeconfig <filename>\n")
                return
            }
            filename = idStr(args.Argv(1))
            filename.DefaultFileExtension(".cfg")
            commonLocal.Printf("Writing %s.\n", filename)
            commonLocal.WriteConfigToFile(filename.toString())
        }

        companion object {
            private val instance: cmdFunction_t = Com_WriteConfig_f()
            fun getInstance(): cmdFunction_t {
                return instance
            }
        }
    }

    /*
     =================
     Com_SetMachineSpecs_f
     =================
     */
    internal class Com_SetMachineSpec_f : cmdFunction_t() {
        @Throws(idException::class)
        override fun run(args: CmdArgs.idCmdArgs?) {
            commonLocal.SetMachineSpec()
        }

        companion object {
            private val instance: cmdFunction_t = Com_SetMachineSpec_f()
            fun getInstance(): cmdFunction_t {
                return instance
            }
        }
    }

    /*
     =================
     Com_ExecMachineSpecs_f
     =================
     */
    internal class Com_ExecMachineSpec_f : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            if (com_machineSpec.GetInteger() == 3) {
                cvarSystem.SetCVarInteger("image_anisotropy", 1, CVarSystem.CVAR_ARCHIVE)
                cvarSystem.SetCVarInteger("image_lodbias", 0, CVarSystem.CVAR_ARCHIVE)
                cvarSystem.SetCVarInteger("image_forceDownSize", 0, CVarSystem.CVAR_ARCHIVE)
                cvarSystem.SetCVarInteger("image_roundDown", 1, CVarSystem.CVAR_ARCHIVE)
                cvarSystem.SetCVarInteger("image_preload", 1, CVarSystem.CVAR_ARCHIVE)
                cvarSystem.SetCVarInteger("image_useAllFormats", 1, CVarSystem.CVAR_ARCHIVE)
                cvarSystem.SetCVarInteger("image_downSizeSpecular", 0, CVarSystem.CVAR_ARCHIVE)
                cvarSystem.SetCVarInteger("image_downSizeBump", 0, CVarSystem.CVAR_ARCHIVE)
                cvarSystem.SetCVarInteger("image_downSizeSpecularLimit", 64, CVarSystem.CVAR_ARCHIVE)
                cvarSystem.SetCVarInteger("image_downSizeBumpLimit", 256, CVarSystem.CVAR_ARCHIVE)
                cvarSystem.SetCVarInteger("image_usePrecompressedTextures", 0, CVarSystem.CVAR_ARCHIVE)
                cvarSystem.SetCVarInteger("image_downsize", 0, CVarSystem.CVAR_ARCHIVE)
                cvarSystem.SetCVarString("image_filter", "GL_LINEAR_MIPMAP_LINEAR", CVarSystem.CVAR_ARCHIVE)
                cvarSystem.SetCVarInteger("image_anisotropy", 8, CVarSystem.CVAR_ARCHIVE)
                cvarSystem.SetCVarInteger("image_useCompression", 0, CVarSystem.CVAR_ARCHIVE)
                cvarSystem.SetCVarInteger("image_ignoreHighQuality", 0, CVarSystem.CVAR_ARCHIVE)
                cvarSystem.SetCVarInteger("s_maxSoundsPerShader", 0, CVarSystem.CVAR_ARCHIVE)
                cvarSystem.SetCVarInteger("r_mode", 5, CVarSystem.CVAR_ARCHIVE)
                cvarSystem.SetCVarInteger("image_useNormalCompression", 0, CVarSystem.CVAR_ARCHIVE)
                cvarSystem.SetCVarInteger("r_multiSamples", 0, CVarSystem.CVAR_ARCHIVE)
            } else if (com_machineSpec.GetInteger() == 2) {
                cvarSystem.SetCVarString("image_filter", "GL_LINEAR_MIPMAP_LINEAR", CVarSystem.CVAR_ARCHIVE)
                cvarSystem.SetCVarInteger("image_anisotropy", 1, CVarSystem.CVAR_ARCHIVE)
                cvarSystem.SetCVarInteger("image_lodbias", 0, CVarSystem.CVAR_ARCHIVE)
                cvarSystem.SetCVarInteger("image_forceDownSize", 0, CVarSystem.CVAR_ARCHIVE)
                cvarSystem.SetCVarInteger("image_roundDown", 1, CVarSystem.CVAR_ARCHIVE)
                cvarSystem.SetCVarInteger("image_preload", 1, CVarSystem.CVAR_ARCHIVE)
                cvarSystem.SetCVarInteger("image_useAllFormats", 1, CVarSystem.CVAR_ARCHIVE)
                cvarSystem.SetCVarInteger("image_downSizeSpecular", 0, CVarSystem.CVAR_ARCHIVE)
                cvarSystem.SetCVarInteger("image_downSizeBump", 0, CVarSystem.CVAR_ARCHIVE)
                cvarSystem.SetCVarInteger("image_downSizeSpecularLimit", 64, CVarSystem.CVAR_ARCHIVE)
                cvarSystem.SetCVarInteger("image_downSizeBumpLimit", 256, CVarSystem.CVAR_ARCHIVE)
                cvarSystem.SetCVarInteger("image_usePrecompressedTextures", 1, CVarSystem.CVAR_ARCHIVE)
                cvarSystem.SetCVarInteger("image_downsize", 0, CVarSystem.CVAR_ARCHIVE)
                cvarSystem.SetCVarInteger("image_anisotropy", 8, CVarSystem.CVAR_ARCHIVE)
                cvarSystem.SetCVarInteger("image_useCompression", 1, CVarSystem.CVAR_ARCHIVE)
                cvarSystem.SetCVarInteger("image_ignoreHighQuality", 0, CVarSystem.CVAR_ARCHIVE)
                cvarSystem.SetCVarInteger("s_maxSoundsPerShader", 0, CVarSystem.CVAR_ARCHIVE)
                cvarSystem.SetCVarInteger("image_useNormalCompression", 0, CVarSystem.CVAR_ARCHIVE)
                cvarSystem.SetCVarInteger("r_mode", 4, CVarSystem.CVAR_ARCHIVE)
                cvarSystem.SetCVarInteger("r_multiSamples", 0, CVarSystem.CVAR_ARCHIVE)
            } else if (com_machineSpec.GetInteger() == 1) {
                cvarSystem.SetCVarString("image_filter", "GL_LINEAR_MIPMAP_LINEAR", CVarSystem.CVAR_ARCHIVE)
                cvarSystem.SetCVarInteger("image_anisotropy", 1, CVarSystem.CVAR_ARCHIVE)
                cvarSystem.SetCVarInteger("image_lodbias", 0, CVarSystem.CVAR_ARCHIVE)
                cvarSystem.SetCVarInteger("image_downSize", 0, CVarSystem.CVAR_ARCHIVE)
                cvarSystem.SetCVarInteger("image_forceDownSize", 0, CVarSystem.CVAR_ARCHIVE)
                cvarSystem.SetCVarInteger("image_roundDown", 1, CVarSystem.CVAR_ARCHIVE)
                cvarSystem.SetCVarInteger("image_preload", 1, CVarSystem.CVAR_ARCHIVE)
                cvarSystem.SetCVarInteger("image_useCompression", 1, CVarSystem.CVAR_ARCHIVE)
                cvarSystem.SetCVarInteger("image_useAllFormats", 1, CVarSystem.CVAR_ARCHIVE)
                cvarSystem.SetCVarInteger("image_usePrecompressedTextures", 1, CVarSystem.CVAR_ARCHIVE)
                cvarSystem.SetCVarInteger("image_downSizeSpecular", 0, CVarSystem.CVAR_ARCHIVE)
                cvarSystem.SetCVarInteger("image_downSizeBump", 0, CVarSystem.CVAR_ARCHIVE)
                cvarSystem.SetCVarInteger("image_downSizeSpecularLimit", 64, CVarSystem.CVAR_ARCHIVE)
                cvarSystem.SetCVarInteger("image_downSizeBumpLimit", 256, CVarSystem.CVAR_ARCHIVE)
                cvarSystem.SetCVarInteger("image_useNormalCompression", 2, CVarSystem.CVAR_ARCHIVE)
                cvarSystem.SetCVarInteger("r_mode", 3, CVarSystem.CVAR_ARCHIVE)
                cvarSystem.SetCVarInteger("r_multiSamples", 0, CVarSystem.CVAR_ARCHIVE)
            } else {
                cvarSystem.SetCVarString("image_filter", "GL_LINEAR_MIPMAP_LINEAR", CVarSystem.CVAR_ARCHIVE)
                cvarSystem.SetCVarInteger("image_anisotropy", 1, CVarSystem.CVAR_ARCHIVE)
                cvarSystem.SetCVarInteger("image_lodbias", 0, CVarSystem.CVAR_ARCHIVE)
                cvarSystem.SetCVarInteger("image_roundDown", 1, CVarSystem.CVAR_ARCHIVE)
                cvarSystem.SetCVarInteger("image_preload", 1, CVarSystem.CVAR_ARCHIVE)
                cvarSystem.SetCVarInteger("image_useAllFormats", 1, CVarSystem.CVAR_ARCHIVE)
                cvarSystem.SetCVarInteger("image_usePrecompressedTextures", 1, CVarSystem.CVAR_ARCHIVE)
                cvarSystem.SetCVarInteger("image_downSize", 1, CVarSystem.CVAR_ARCHIVE)
                cvarSystem.SetCVarInteger("image_anisotropy", 0, CVarSystem.CVAR_ARCHIVE)
                cvarSystem.SetCVarInteger("image_useCompression", 1, CVarSystem.CVAR_ARCHIVE)
                cvarSystem.SetCVarInteger("image_ignoreHighQuality", 1, CVarSystem.CVAR_ARCHIVE)
                cvarSystem.SetCVarInteger("s_maxSoundsPerShader", 1, CVarSystem.CVAR_ARCHIVE)
                cvarSystem.SetCVarInteger("image_downSizeSpecular", 1, CVarSystem.CVAR_ARCHIVE)
                cvarSystem.SetCVarInteger("image_downSizeBump", 1, CVarSystem.CVAR_ARCHIVE)
                cvarSystem.SetCVarInteger("image_downSizeSpecularLimit", 64, CVarSystem.CVAR_ARCHIVE)
                cvarSystem.SetCVarInteger("image_downSizeBumpLimit", 256, CVarSystem.CVAR_ARCHIVE)
                cvarSystem.SetCVarInteger("r_mode", 3, CVarSystem.CVAR_ARCHIVE)
                cvarSystem.SetCVarInteger("image_useNormalCompression", 2, CVarSystem.CVAR_ARCHIVE)
                cvarSystem.SetCVarInteger("r_multiSamples", 0, CVarSystem.CVAR_ARCHIVE)
            }
            if (win_shared.Sys_GetVideoRam() < 128) {
                cvarSystem.SetCVarBool("image_ignoreHighQuality", true, CVarSystem.CVAR_ARCHIVE)
                cvarSystem.SetCVarInteger("image_downSize", 1, CVarSystem.CVAR_ARCHIVE)
                cvarSystem.SetCVarInteger("image_downSizeLimit", 256, CVarSystem.CVAR_ARCHIVE)
                cvarSystem.SetCVarInteger("image_downSizeSpecular", 1, CVarSystem.CVAR_ARCHIVE)
                cvarSystem.SetCVarInteger("image_downSizeSpecularLimit", 64, CVarSystem.CVAR_ARCHIVE)
                cvarSystem.SetCVarInteger("image_downSizeBump", 1, CVarSystem.CVAR_ARCHIVE)
                cvarSystem.SetCVarInteger("image_downSizeBumpLimit", 256, CVarSystem.CVAR_ARCHIVE)
            }
            if (win_shared.Sys_GetSystemRam() < 512) {
                cvarSystem.SetCVarBool("image_ignoreHighQuality", true, CVarSystem.CVAR_ARCHIVE)
                cvarSystem.SetCVarInteger("s_maxSoundsPerShader", 1, CVarSystem.CVAR_ARCHIVE)
                cvarSystem.SetCVarInteger("image_downSize", 1, CVarSystem.CVAR_ARCHIVE)
                cvarSystem.SetCVarInteger("image_downSizeLimit", 256, CVarSystem.CVAR_ARCHIVE)
                cvarSystem.SetCVarInteger("image_downSizeSpecular", 1, CVarSystem.CVAR_ARCHIVE)
                cvarSystem.SetCVarInteger("image_downSizeSpecularLimit", 64, CVarSystem.CVAR_ARCHIVE)
                cvarSystem.SetCVarBool("com_purgeAll", true, CVarSystem.CVAR_ARCHIVE)
                cvarSystem.SetCVarBool("r_forceLoadImages", true, CVarSystem.CVAR_ARCHIVE)
            } else {
                cvarSystem.SetCVarBool("com_purgeAll", false, CVarSystem.CVAR_ARCHIVE)
                cvarSystem.SetCVarBool("r_forceLoadImages", false, CVarSystem.CVAR_ARCHIVE)
            }
            val oldCard = booleanArrayOf(false)
            val nv10or20 = booleanArrayOf(false)
            if (oldCard[0]) {
                cvarSystem.SetCVarBool("g_decals", false, CVarSystem.CVAR_ARCHIVE)
                cvarSystem.SetCVarBool("g_projectileLights", false, CVarSystem.CVAR_ARCHIVE)
                cvarSystem.SetCVarBool("g_doubleVision", false, CVarSystem.CVAR_ARCHIVE)
                cvarSystem.SetCVarBool("g_muzzleFlash", false, CVarSystem.CVAR_ARCHIVE)
            } else {
                cvarSystem.SetCVarBool("g_decals", true, CVarSystem.CVAR_ARCHIVE)
                cvarSystem.SetCVarBool("g_projectileLights", true, CVarSystem.CVAR_ARCHIVE)
                cvarSystem.SetCVarBool("g_doubleVision", true, CVarSystem.CVAR_ARCHIVE)
                cvarSystem.SetCVarBool("g_muzzleFlash", true, CVarSystem.CVAR_ARCHIVE)
            }
            if (nv10or20[0]) {
                cvarSystem.SetCVarInteger("image_useNormalCompression", 1, CVarSystem.CVAR_ARCHIVE)
            }
        }

        companion object {
            private val instance: cmdFunction_t = Com_ExecMachineSpec_f()
            fun getInstance(): cmdFunction_t {
                return instance
            }
        }
    }

    /*
     =================
     Com_ReloadEngine_f
     =================
     */
    internal class Com_ReloadEngine_f : cmdFunction_t() {
        @Throws(idException::class)
        override fun run(args: CmdArgs.idCmdArgs?) {
            var menu = false
            if (!commonLocal.IsInitialized()) {
                return
            }
            if (args!!.Argc() > 1 && idStr.Icmp(args.Argv(1), "menu") == 0) {
                menu = true
            }
            common.Printf("============= ReloadEngine start =============\n")
            if (!menu) {
                win_syscon.Sys_ShowConsole(1, false)
            }
            commonLocal.ShutdownGame(true)
            commonLocal.InitGame()
            if (!menu && !idAsyncNetwork.serverDedicated.GetBool()) {
                win_syscon.Sys_ShowConsole(0, false)
            }
            common.Printf("============= ReloadEngine end ===============\n")
            if (!CmdSystem.cmdSystem.PostReloadEngine()) {
                if (menu) {
                    Session.session.StartMenu()
                }
            }
        }

        companion object {
            private val instance: cmdFunction_t = Com_ReloadEngine_f()
            fun getInstance(): cmdFunction_t {
                return instance
            }
        }
    }

    class ListHash : HashMap<String, idStrList>()

    /*
     =================
     LocalizeMaps_f
     =================
     */
    internal class Com_LocalizeMaps_f : cmdFunction_t() {
        @Throws(idException::class)
        override fun run(args: CmdArgs.idCmdArgs?) {
            if (args!!.Argc() < 2) {
                common.Printf("Usage: localizeMaps <count | dictupdate | all> <map>\n")
                return
            }
            var strCount = 0
            val count: Boolean
            var dictUpdate = false
            var write = false
            if (idStr.Icmp(args.Argv(1), "count") == 0) {
                count = true
            } else if (idStr.Icmp(args.Argv(1), "dictupdate") == 0) {
                count = true
                dictUpdate = true
            } else if (idStr.Icmp(args.Argv(1), "all") == 0) {
                count = true
                dictUpdate = true
                write = true
            } else {
                common.Printf("Invalid Command\n")
                common.Printf("Usage: localizeMaps <count | dictupdate | all>\n")
                return
            }
            val strTable = idLangDict()
            val filename = Str.va("strings/english%.3i.lang", com_product_lang_ext.GetInteger())
            if (strTable.Load(filename) == false) {
                //This is a new file so set the base index
                strTable.SetBaseID(com_product_lang_ext.GetInteger() * 100000)
            }
            common.SetRefreshOnPrint(true)
            val listHash = ListHash()
            LoadMapLocalizeData(listHash)
            val excludeList = idStrList()
            LoadGuiParmExcludeList(excludeList)
            if (args.Argc() == 3) {
                strCount += LocalizeMap(args.Argv(2), strTable, listHash, excludeList, write)
            } else {
                val files = idStrList()
                GetFileList("z:/d3xp/d3xp/maps/game", "*.map", files)
                for (i in 0 until files.size()) {
                    val file = FileSystem_h.fileSystem.OSPathToRelativePath(files[i].toString())
                    strCount += LocalizeMap(file, strTable, listHash, excludeList, write)
                }
            }
            if (count) {
                common.Printf("Localize String Count: %d\n", strCount)
            }
            common.SetRefreshOnPrint(false)
            if (dictUpdate) {
                strTable.Save(filename)
            }
        }

        companion object {
            private val instance: cmdFunction_t = Com_LocalizeMaps_f()
            fun getInstance(): cmdFunction_t {
                return instance
            }
        }
    }

    /*
     =================
     LocalizeGuis_f
     =================
     */
    internal class Com_LocalizeGuis_f : cmdFunction_t() {
        @Throws(idException::class)
        override fun run(args: CmdArgs.idCmdArgs?) {
            if (args!!.Argc() != 2) {
                common.Printf("Usage: localizeGuis <all | gui>\n")
                return
            }
            val strTable = idLangDict()
            val filename = Str.va("strings/english%.3i.lang", com_product_lang_ext.GetInteger())
            if (strTable.Load(filename) == false) {
                //This is a new file so set the base index
                strTable.SetBaseID(com_product_lang_ext.GetInteger() * 100000)
            }
            var files: idFileList
            if (idStr.Icmp(args.Argv(1), "all") == 0) {
                val game = cvarSystem.GetCVarString("fs_game")
                files = if (!game.isEmpty()) {
                    FileSystem_h.fileSystem.ListFilesTree("guis", "*.gui", true, game)
                } else {
                    FileSystem_h.fileSystem.ListFilesTree("guis", "*.gui", true)
                }
                for (i in 0 until files.GetNumFiles()) {
                    commonLocal.LocalizeGui(files.GetFile(i), strTable)
                }
                FileSystem_h.fileSystem.FreeFileList(files)
                files = if (game.length != 0) {
                    FileSystem_h.fileSystem.ListFilesTree("guis", "*.pd", true, game)
                } else {
                    FileSystem_h.fileSystem.ListFilesTree("guis", "*.pd", true, "d3xp")
                }
                for (i in 0 until files.GetNumFiles()) {
                    commonLocal.LocalizeGui(files.GetFile(i), strTable)
                }
                FileSystem_h.fileSystem.FreeFileList(files)
            } else {
                commonLocal.LocalizeGui(args.Argv(1), strTable)
            }
            strTable.Save(filename)
        }

        companion object {
            private val instance: cmdFunction_t = Com_LocalizeGuis_f()
            fun getInstance(): cmdFunction_t {
                return instance
            }
        }
    }

    internal class Com_LocalizeGuiParmsTest_f : cmdFunction_t() {
        @Throws(idException::class)
        override fun run(args: CmdArgs.idCmdArgs?) {
            common.SetRefreshOnPrint(true)
            val localizeFile = FileSystem_h.fileSystem.OpenFileWrite("gui_parm_localize.csv")!!
            val noLocalizeFile = FileSystem_h.fileSystem.OpenFileWrite("gui_parm_nolocalize.csv")!!
            val excludeList = idStrList()
            LoadGuiParmExcludeList(excludeList)
            val files = idStrList()
            GetFileList("z:/d3xp/d3xp/maps/game", "*.map", files)
            for (i in 0 until files.size()) {
                common.Printf("Testing Map '%s'\n", files[i])
                val map = idMapFile()
                val file = FileSystem_h.fileSystem.OSPathToRelativePath(files[i].toString())
                if (map.Parse(file, false, false)) {
                    val count = map.GetNumEntities()
                    for (j in 0 until count) {
                        val ent = map.GetEntity(j)
                        if (ent != null) {
                            var kv = ent.epairs.MatchPrefix("gui_parm")
                            while (kv != null) {
                                if (TestGuiParm(kv.GetKey(), kv.GetValue(), excludeList)) {
                                    val out = Str.va("%s,%s,%s\r\n", kv.GetValue(), kv.GetKey(), file)
                                    localizeFile.WriteString(out)
                                } else {
                                    val out = Str.va("%s,%s,%s\r\n", kv.GetValue(), kv.GetKey(), file)
                                    noLocalizeFile.WriteString(out) //TODO:writeString?
                                }
                                kv = ent.epairs.MatchPrefix("gui_parm", kv)
                            }
                        }
                    }
                }
            }
            FileSystem_h.fileSystem.CloseFile(localizeFile)
            FileSystem_h.fileSystem.CloseFile(noLocalizeFile)
            common.SetRefreshOnPrint(false)
        }

        companion object {
            private val instance: cmdFunction_t = Com_LocalizeGuiParmsTest_f()
            fun getInstance(): cmdFunction_t {
                return instance
            }
        }
    }

    internal class Com_LocalizeMapsTest_f : cmdFunction_t() {
        @Throws(idException::class)
        override fun run(args: CmdArgs.idCmdArgs?) {
            val listHash = ListHash()
            LoadMapLocalizeData(listHash)
            common.SetRefreshOnPrint(true)
            val localizeFile = FileSystem_h.fileSystem.OpenFileWrite("map_localize.csv")!!
            val files = idStrList()
            GetFileList("z:/d3xp/d3xp/maps/game", "*.map", files)
            for (i in 0 until files.size()) {
                common.Printf("Testing Map '%s'\n", files[i])
                val map = idMapFile()
                val file = FileSystem_h.fileSystem.OSPathToRelativePath(files[i].toString())
                if (map.Parse(file, false, false)) {
                    val count = map.GetNumEntities()
                    for (j in 0 until count) {
                        val ent = map.GetEntity(j)
                        if (ent != null) {

                            //Temp code to get a list of all entity key value pairs
                            /*idStr static classname = ent.epairs.GetString("static classname");
                             if(static classname == "worldspawn" || static classname == "func_static" || static classname == "light" || static classname == "speaker" || static classname.Left(8) == "trigger_") {
                             continue;
                             }
                             for( int i = 0; i < ent.epairs.GetNumKeyVals(); i++) {
                             const idKeyValue* kv = ent.epairs.GetKeyVal(i);
                             idStr out = va("%s,%s,%s,%s\r\n", static classname.c_str(), kv.GetKey().c_str(), kv.GetValue().c_str(), file.c_str());
                             localizeFile.Write( out.c_str(), out.Length() );
                             }*/
                            val className =
                                ent.epairs.GetString("classname") // FIX: was "static classname" — translation artifact

                            //Hack: for info_location
                            var hasLocation = false
                            var list = listHash[className]
                            if (list != null) {
                                for (k in 0 until list.size()) {
                                    val `val` = ent.epairs.GetString(list[k].toString(), "")!!
                                    if ( /*static*/className == "info_location" && list[k].toString() == "location") {
                                        hasLocation = true
                                    }
                                    if (`val`.isNotEmpty() && TestMapVal(`val`)) {
                                        if (!hasLocation || list[k].toString() == "location") {
                                            val out = Str.va("%s,%s,%s\r\n", `val`, list[k], file)
                                            localizeFile.WriteString(out)
                                        }
                                    }
                                }
                            }
                            list = listHash["all"]
                            if (list != null) {
                                for (k in 0 until list.size()) {
                                    val `val` = ent.epairs.GetString(list[k].toString(), "")!!
                                    if (`val`.isNotEmpty() && TestMapVal(`val`)) {
                                        val out = Str.va("%s,%s,%s\r\n", `val`, list[k], file)
                                        localizeFile.WriteString(out)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            FileSystem_h.fileSystem.CloseFile(localizeFile)
            common.SetRefreshOnPrint(false)
        }

        companion object {
            private val instance: cmdFunction_t = Com_LocalizeMapsTest_f()
            fun getInstance(): cmdFunction_t {
                return instance
            }
        }
    }

    /*
     =================
     Com_StartBuild_f
     =================
     */
    internal class Com_StartBuild_f : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            Image.globalImages.StartBuild()
        }

        companion object {
            private val instance: cmdFunction_t = Com_StartBuild_f()
            fun getInstance(): cmdFunction_t {
                return instance
            }
        }
    }

    /*
     =================
     Com_FinishBuild_f
     =================
     */
    internal class Com_FinishBuild_f : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            if (Game_local.game != null) {
                Game_local.game.CacheDictionaryMedia(null)
            }
            Image.globalImages.FinishBuild(args!!.Argc() > 1)
        }

        companion object {
            private val instance: cmdFunction_t = Com_FinishBuild_f()
            fun getInstance(): cmdFunction_t {
                return instance
            }
        }
    }

    /*
     ==============
     Com_Help_f
     ==============
     */
    internal class Com_Help_f : cmdFunction_t() {
        @Throws(idException::class)
        override fun run(args: CmdArgs.idCmdArgs?) {
            common.Printf("\nCommonly used commands:\n")
            common.Printf("  spawnServer      - start the server.\n")
            common.Printf("  disconnect       - shut down the server.\n")
            common.Printf("  listCmds         - list all console commands.\n")
            common.Printf("  listCVars        - list all console variables.\n")
            common.Printf("  kick             - kick a client by number.\n")
            common.Printf("  gameKick         - kick a client by name.\n")
            common.Printf("  serverNextMap    - immediately load next map.\n")
            common.Printf("  serverMapRestart - restart the current map.\n")
            common.Printf("  serverForceReady - force all players to ready status.\n")
            common.Printf("\nCommonly used variables:\n")
            common.Printf("  si_name          - server name (change requires a restart to see)\n")
            common.Printf("  si_gametype      - type of game.\n")
            common.Printf("  si_fragLimit     - max kills to win (or lives in Last Man Standing).\n")
            common.Printf("  si_timeLimit     - maximum time a game will last.\n")
            common.Printf("  si_warmup        - do pre-game warmup.\n")
            common.Printf("  si_pure          - pure server.\n")
            common.Printf("  g_mapCycle       - name of .scriptcfg file for cycling maps.\n")
            common.Printf("See mapcycle.scriptcfg for an example of a mapcyle script.\n\n")
        }

        companion object {
            private val instance: cmdFunction_t = Com_Help_f()
            fun getInstance(): cmdFunction_t {
                return instance
            }
        }
    }

    /*
     =================
     ReloadLanguage_f
     =================
     */
    internal class Com_ReloadLanguage_f : cmdFunction_t() {
        @Throws(idException::class)
        override fun run(args: CmdArgs.idCmdArgs?) {
            commonLocal.InitLanguageDict()
        }

        companion object {
            private val instance: cmdFunction_t = Com_ReloadLanguage_f()
            fun getInstance(): cmdFunction_t {
                return instance
            }
        }
    }

    companion object {
        const val ASYNCSOUND_INFO: String =
            "0: mix sound inline, 1: memory mapped async mix, 2: callback mixing, 3: write async mix"
        const val DMAP_DONE: String = "DMAPDone"
        const val EDITOR_NONE = 0
        val DMAP_MSGID: String = "DMAPOutput"
        val EDITOR_AAS: Int = BIT(11)
        val EDITOR_AF: Int = BIT(8)
        val EDITOR_DEBUGGER: Int = BIT(3)
        val EDITOR_DECL: Int = BIT(7)
        val EDITOR_GUI: Int = BIT(2)
        val EDITOR_LIGHT: Int = BIT(5)
        val EDITOR_MATERIAL: Int = BIT(12)
        val EDITOR_PARTICLE: Int = BIT(9)
        val EDITOR_PDA: Int = BIT(10)
        val EDITOR_RADIANT: Int = BIT(1)
        val EDITOR_SCRIPT: Int = BIT(4)
        val EDITOR_SOUND: Int = BIT(6)
        val STRTABLE_ID: String = "#str_"
        val STRTABLE_ID_LENGTH = STRTABLE_ID.length //5
        val com_allowConsole: idCVar = idCVar(
            "com_allowConsole",
            "0",
            CVarSystem.CVAR_BOOL or CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_NOCHEAT,
            "allow toggling console with the tilde key"
        )
        val com_asyncInput: idCVar = idCVar(
            "com_asyncInput", "0", CVarSystem.CVAR_BOOL or CVarSystem.CVAR_SYSTEM, "sample input from the async thread"
        )
        val com_asyncSound: idCVar = if (MACOS_X) idCVar(
            "com_asyncSound",
            "2",
            CVarSystem.CVAR_INTEGER or CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_ROM,
            ASYNCSOUND_INFO
        ) else if (__linux__) idCVar(
            "com_asyncSound",
            "3",
            CVarSystem.CVAR_INTEGER or CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_ROM,
            ASYNCSOUND_INFO
        ) else idCVar(
            "com_asyncSound",
            "1",
            CVarSystem.CVAR_INTEGER or CVarSystem.CVAR_SYSTEM,
            ASYNCSOUND_INFO,
            0.0f,
            3.0f // FIX: was 1.0f — dhewm3 allows values 0-3
        )
        val com_developer: idCVar = idCVar(
            "developer",
            "1",
            CVarSystem.CVAR_BOOL or CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_NOCHEAT,
            "developer mode"
        )
        val com_forceGenericSIMD: idCVar = idCVar(
            "com_forceGenericSIMD",
            "1",
            CVarSystem.CVAR_BOOL or CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_NOCHEAT,
            "force generic platform independent SIMD"
        )
        val com_logFile: idCVar = idCVar(
            "logFile",
            "0",
            CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_NOCHEAT,
            "1 = buffer log, 2 = flush after each print",
            0.0f,
            2.0f,
            ArgCompletion_Integer(0, 2)
        )
        val com_logFileName: idCVar = idCVar(
            "logFileName",
            "qconsole.log",
            CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_NOCHEAT,
            "name of log file, if empty, qconsole.log will be used"
        )
        val com_machineSpec: idCVar = idCVar(
            "com_machineSpec",
            "-1",
            CVarSystem.CVAR_INTEGER or CVarSystem.CVAR_ARCHIVE or CVarSystem.CVAR_SYSTEM,
            "hardware classification, -1 = not detected, 0 = low quality, 1 = medium quality, 2 = high quality, 3 = ultra quality"
        )
        val com_makingBuild: idCVar =
            idCVar("com_makingBuild", "0", CVarSystem.CVAR_BOOL or CVarSystem.CVAR_SYSTEM, "1 when making a build")
        val com_memoryMarker: idCVar = idCVar(
            "com_memoryMarker",
            "-1",
            CVarSystem.CVAR_INTEGER or CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_INIT,
            "used as a marker for memory stats"
        )
        val com_preciseTic: idCVar = idCVar(
            "com_preciseTic",
            "1",
            CVarSystem.CVAR_BOOL or CVarSystem.CVAR_SYSTEM,
            "run one game tick every async thread update"
        )
        val com_product_lang_ext: idCVar = idCVar(
            "com_product_lang_ext",
            "1",
            CVarSystem.CVAR_INTEGER or CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_ARCHIVE,
            "Extension to use when creating language files."
        )


        val com_purgeAll: idCVar = idCVar(
            "com_purgeAll",
            "0",
            CVarSystem.CVAR_BOOL or CVarSystem.CVAR_ARCHIVE or CVarSystem.CVAR_SYSTEM,
            "purge everything between level loads"
        )
        val com_showAsyncStats: idCVar = idCVar(
            "com_showAsyncStats",
            "0",
            CVarSystem.CVAR_BOOL or CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_NOCHEAT,
            "show async network stats"
        )
        val com_showFPS: idCVar = idCVar(
            "com_showFPS",
            "1",
            CVarSystem.CVAR_BOOL or CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_ARCHIVE or CVarSystem.CVAR_NOCHEAT,
            "show frames rendered per second"
        )
        val com_showMemoryUsage: idCVar = idCVar(
            "com_showMemoryUsage",
            "0",
            CVarSystem.CVAR_BOOL or CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_NOCHEAT,
            "show total and per frame memory usage"
        )
        val com_showSoundDecoders: idCVar = idCVar(
            "com_showSoundDecoders",
            "0",
            CVarSystem.CVAR_BOOL or CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_NOCHEAT,
            "show sound decoders"
        )
        val com_skipRenderer: idCVar = idCVar(
            "com_skipRenderer", "0", CVarSystem.CVAR_BOOL or CVarSystem.CVAR_SYSTEM, "skip the renderer completely"
        )
        val com_speeds: idCVar = idCVar(
            "com_speeds",
            "0",
            CVarSystem.CVAR_BOOL or CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_NOCHEAT,
            "show engine timings"
        )
        val com_timescale: idCVar =
            idCVar("timescale", "1", CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_FLOAT, "scales the time", 0.1f, 10.0f)
        val com_timestampPrints: idCVar = idCVar(
            "com_timestampPrints",
            "0",
            CVarSystem.CVAR_SYSTEM,
            "print time with each console print, 1 = msec, 2 = sec",
            0.0f,
            2.0f,
            ArgCompletion_Integer(0, 2)
        )
        val com_updateLoadSize: idCVar = idCVar(
            "com_updateLoadSize",
            "0",
            CVarSystem.CVAR_BOOL or CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_NOCHEAT,
            "update the load size after loading a map"
        )
        val com_videoRam: idCVar = idCVar(
            "com_videoRam",
            "512",
            CVarSystem.CVAR_INTEGER or CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_NOCHEAT or CVarSystem.CVAR_ARCHIVE,
            "holds the last amount of detected video ram"
        )
        val BUILD_DEBUG: String = if (_DEBUG) "-debug" else ""
        const val ID_WRITE_VERSION = false
        const val MAX_PRINT_MSG_SIZE = 4096
        const val MAX_WARNING_LIST = 256
        val version: version_s = version_s()
        val com_version: idCVar = idCVar(
            "si_version",
            version.string,
            CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_ROM or CVarSystem.CVAR_SERVERINFO,
            "engine version"
        )
        var com_editorActive // true if an editor has focus
                = false
        var com_editors = 0 // currently opened editor(s)
        var com_frameNumber = 0 // variable frame number

        @Volatile
        var com_frameTime = 0// time for the current frame in milliseconds
        var   /*HWND*/com_hwndMsg: Long = 0
        var com_outputMsg = false

        @Volatile
        var com_ticNumber = 0// 60 hz tics
        var time_backend = 0 // renderSystem backend time
        var time_frontend = 0 // renderSystem frontend time
        var time_gameDraw = 0 // FIX: C++ uses int, not long

        // com_speeds times
        var time_gameFrame = 0 // FIX: C++ uses int, not long
        var com_msgID: Long = -1
        private var commonLocal: idCommonLocal = idCommonLocal()
        var common: /*final*/idCommon = commonLocal

        fun LoadMapLocalizeData(listHash: ListHash) {
            val fileName = "map_localize.cfg"
            val buffer = arrayOfNulls<ByteBuffer>(1)
            val src =
                idLexer(Lexer.LEXFL_NOFATALERRORS or Lexer.LEXFL_NOSTRINGCONCAT or Lexer.LEXFL_ALLOWMULTICHARLITERALS or Lexer.LEXFL_ALLOWBACKSLASHSTRINGCONCAT)

            if (FileSystem_h.fileSystem.ReadFile(fileName, buffer) > 0) {
                src.LoadMemory(TempDump.bbtocb(buffer[0]!!), TempDump.bbtocb(buffer[0]!!).capacity(), fileName)
                if (src.IsLoaded()) {
                    var classname: String
                    val token = idToken()

                    while (src.ReadToken(token)) {
                        classname = token.toString()
                        src.ExpectTokenString("{")

                        val list = idStrList()
                        while (src.ReadToken(token)) {
                            if (token.toString() == "}") {
                                break
                            }
                            list.add(idStr(token))
                        }

                        listHash[classname] = list
                    }
                }
                FileSystem_h.fileSystem.FreeFile(buffer)
            }
        }

        fun LoadGuiParmExcludeList(list: idStrList) {
            val fileName = "guiparm_exclude.cfg"
            val buffer = arrayOfNulls<ByteBuffer>(1)
            val src =
                idLexer(Lexer.LEXFL_NOFATALERRORS or Lexer.LEXFL_NOSTRINGCONCAT or Lexer.LEXFL_ALLOWMULTICHARLITERALS or Lexer.LEXFL_ALLOWBACKSLASHSTRINGCONCAT)

            if (FileSystem_h.fileSystem.ReadFile(fileName, buffer) > 0) {
                src.LoadMemory(TempDump.bbtocb(buffer[0]!!), TempDump.bbtocb(buffer[0]!!).capacity(), fileName)
                if (src.IsLoaded()) {
                    val token = idToken()

                    while (src.ReadToken(token)) {
                        list.add(idStr(token))
                    }
                }
                FileSystem_h.fileSystem.FreeFile(buffer)
            }
        }

        fun TestMapVal(str: idStr): Boolean {
            //Already Localized?
            return str.Find("#str_") == -1
        }

        fun TestMapVal(str: String): Boolean {
            return !str.contains("#str_") // FIX: was inverted — should return true when NOT localized
        }

        //#endif
        fun TestGuiParm(parm: String, value: String, excludeList: idStrList): Boolean {
            val testVal = idStr(value)

            //Already Localized?
            if (testVal.Find("#str_") != -1) {
                return false
            }

            //Numeric
            if (testVal.IsNumeric()) {
                return false
            }

            //Contains ::
            if (testVal.Find("::") != -1) {
                return false
            }

            //Contains /
            if (testVal.Find("/") != -1) {
                return false
            }

            // FIX: C++ Find() returns pointer, truthy=found → return false. Was: == 0 (only matched index 0)
            if (excludeList.Find(testVal) != null) {
                return false
            }

            return true
        }

        fun TestGuiParm(parm: idStr, value: idStr, excludeList: idStrList): Boolean {
            return TestGuiParm(parm.toString(), value.toString(), excludeList)
        }

        fun GetFileList(dir: String, ext: String, list: idStrList) {

            //Recurse Subdirectories
            val dirList = idStrList()
            sys_public.Sys_ListFiles(dir, "/", dirList)
            for (i in 0 until dirList.size()) {
                if (dirList[i].toString() == "." || dirList[i].toString() == "..") {
                    continue
                }
                val fullName = Str.va("%s/%s", dir, dirList[i])
                GetFileList(fullName, ext, list)
            }
            val fileList = idStrList()
            sys_public.Sys_ListFiles(dir, ext, fileList)
            for (i in 0 until fileList.size()) {
                val fullName = idStr(Str.va("%s/%s", dir, fileList[i]))
                list.add(fullName)
            }
        }

        fun LocalizeMap(
            mapName: String, langDict: idLangDict, listHash: ListHash, excludeList: idStrList, writeFile: Boolean
        ): Int {
            common.Printf("Localizing Map '%s'\n", mapName)

            var strCount = 0

            val map = idMapFile()
            if (map.Parse(mapName, false, false)) {
                val count = map.GetNumEntities()
                for (j in 0 until count) {
                    val ent = map.GetEntity(j)
                    if (ent != null) {
                        val className = ent.epairs.GetString("classname")

                        // Hack: for info_location
                        var hasLocation = false

                        val list = listHash[className]
                        if (list != null) {
                            for (k in 0 until list.size()) {
                                val `val` = ent.epairs.GetString(list[k].toString(), "")!!

                                if (`val`.isNotEmpty() && className == "info_location" && list[k].toString() == "location") {
                                    hasLocation = true
                                }

                                if (`val`.isNotEmpty() && TestMapVal(`val`)) {
                                    if (!hasLocation || list[k].toString() == "location") {
                                        // Localize it!
                                        strCount++
                                        ent.epairs.Set(list[k].toString(), langDict.AddString(`val`))
                                    }
                                }
                            }
                        }

                        val allList = listHash["all"]
                        if (allList != null) {
                            for (k in 0 until allList.size()) {
                                val `val` = ent.epairs.GetString(allList[k].toString(), "")!!
                                if (`val`.isNotEmpty() && TestMapVal(`val`)) {
                                    // Localize it!
                                    strCount++
                                    ent.epairs.Set(allList[k].toString(), langDict.AddString(`val`))
                                }
                            }
                        }

                        // Localize the gui_parms
                        var kv = ent.epairs.MatchPrefix("gui_parm")
                        while (kv != null) {
                            if (TestGuiParm(kv.GetKey(), kv.GetValue(), excludeList)) {
                                // Localize it!
                                strCount++
                                ent.epairs.Set(kv.GetKey(), langDict.AddString(kv.GetValue().toString()))
                            }
                            kv = ent.epairs.MatchPrefix("gui_parm", kv)
                        }
                    }
                }
                if (writeFile && strCount > 0) {
                    // Before we write the map file lets make a backup of the original
                    val file = idStr(FileSystem_h.fileSystem.RelativePathToOSPath(mapName))
                    val bak = file.Left(file.Length() - 4)
                    bak.Append(".bak_loc")
                    FileSystem_h.fileSystem.CopyFile(file.toString(), bak.toString())

                    map.Write(mapName, ".map")
                }
            }

            common.Printf("Count: %d\n", strCount)
            return strCount
        }

        fun setCommons(common: idCommon) {
            commonLocal = common as idCommonLocal
            Common.common = commonLocal
        }
    }
}
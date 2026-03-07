package neo.framework

import neo.Game.Game.escReply_t
import neo.Game.Game_local
import neo.Renderer.Material
import neo.Renderer.R_ScreenshotFilename
import neo.Renderer.RenderSystem
import neo.Renderer.RenderWorld.renderView_s
import neo.Sound.snd_system
import neo.Sound.sound.idSoundWorld
import neo.TempDump
import neo.TempDump.SERiAL
import neo.framework.Async.AsyncNetwork
import neo.framework.Async.AsyncNetwork.idAsyncNetwork
import neo.framework.Async.ServerScan.serverSort_t
import neo.framework.CVarSystem.cvarSystem
import neo.framework.CVarSystem.idCVar
import neo.framework.CmdSystem.cmdExecution_t
import neo.framework.CmdSystem.cmdSystem
import neo.framework.CmdSystem.idCmdSystem.*
import neo.framework.DeclEntityDef.idDeclEntityDef
import neo.framework.DeclManager.declType_t
import neo.framework.DemoFile.demoSystem_t
import neo.framework.DemoFile.idDemoFile
import neo.framework.FileSystem_h.backgroundDownload_s
import neo.framework.FileSystem_h.dlStatus_t
import neo.framework.FileSystem_h.idFileList
import neo.framework.File_h.idFile
import neo.framework.KeyInput.idKeyInput
import neo.framework.Licensee.GAME_NAME
import neo.framework.Licensee.SAVEGAME_VERSION
import neo.framework.Session.*
import neo.framework.Session.Companion.MAX_LOGGED_STATS
import neo.framework.Session_menu.idListSaveGameCompare
import neo.framework.UsercmdGen.inhibit_t
import neo.framework.UsercmdGen.usercmd_t
import neo.idlib.*
import neo.idlib.Dict_h.idDict
import neo.idlib.Text.Lexer
import neo.idlib.Text.Lexer.idLexer
import neo.idlib.Text.Str
import neo.idlib.Text.Str.Measure_t
import neo.idlib.Text.Str.idStr
import neo.idlib.Text.Token.idToken
import neo.idlib.containers.CInt
import neo.idlib.containers.List.idList
import neo.idlib.containers.idStrList
import neo.idlib.hashing.CRC32
import neo.sys.*
import neo.sys.sys_public.sysEventType_t
import neo.sys.sys_public.sysEvent_s
import neo.sys.win_shared.Sys_GetDriveFreeSpace
import neo.ui.ListGUI.idListGUI
import neo.ui.UserInterface
import neo.ui.UserInterface.idUserInterface
import java.nio.ByteBuffer
import java.util.*

object Session_local {
    var CONNECT_TRANSMIT_TIME = 1000
    var MAX_LOGGED_USERCMDS = 60 * 60 * 60 // one hour of single player, 15 minutes of four player

    //
    var USERCMD_PER_DEMO_FRAME = 2

    enum class timeDemo_t {
        TD_NO, TD_YES, TD_YES_THEN_QUIT
    }

    class logCmd_t : SERiAL {
        var cmd: usercmd_t? = null
        var consistencyHash = 0
        override fun AllocBuffer(): ByteBuffer {
            return ByteBuffer.allocate(BYTES)
        }

        override fun Read(buffer: ByteBuffer) {
            buffer.order(java.nio.ByteOrder.LITTLE_ENDIAN)
            if (cmd == null) cmd = usercmd_t()
            cmd!!.Read(buffer)
            consistencyHash = buffer.int
        }

        override fun Write(): ByteBuffer {
            val buffer = AllocBuffer()
            buffer.order(java.nio.ByteOrder.LITTLE_ENDIAN)
            if (cmd != null) {
                buffer.put(cmd!!.Write())
            } else {
                buffer.position(buffer.position() + usercmd_t.BYTES)
            }
            buffer.putInt(consistencyHash)
            buffer.flip()
            return buffer
        }

        companion object {
            @Transient
            private val BYTES: Int = usercmd_t.BYTES + Integer.BYTES
        }
    }

    class fileTIME_T {
        var index = 0
        var   /*ID_TIME_T*/timeStamp: Long = 0 //					operator int() const { return timeStamp; }
    }

    class mapSpawnData_t {
        var mapSpawnUsercmd: Array<usercmd_t> =
            Array(AsyncNetwork.MAX_ASYNC_CLIENTS) { usercmd_t() } // needed for tracking delta angles
        var persistentPlayerInfo: Array<idDict> = Array(AsyncNetwork.MAX_ASYNC_CLIENTS) { idDict() }
        var serverInfo: idDict = idDict()
        var syncedCVars: idDict = idDict()
        var userInfo: Array<idDict> = Array(AsyncNetwork.MAX_ASYNC_CLIENTS) { idDict() }
    }

    /*
     ===============================================================================

     SESSION LOCAL

     ===============================================================================
     */
    class idSessionLocal : idSession() {
        //
        private val cdkey: CharArray = CharArray(CDKEY_BUF_LEN)
        private val xpkey: CharArray = CharArray(CDKEY_BUF_LEN)

        //
        //
        var aviCaptureMode // if true, screenshots will be taken and sound captured
                = false
        var aviDemoFrameCount = 0.0f
        val aviDemoShortName: idStr = idStr() //
        var aviTicStart = 0
        var bytesNeededForMapLoad //
                = 0

        //
        var cmdDemoFile // if non-zero, we are reading commands from a file
                : idFile? = null
        lateinit var currentDemoRenderView: renderView_s
        val currentMapName: idStr = idStr() // for checking reload on same level
        var demoTimeOffset = 0

        //
        // #if ID_CONSOLE_LOCK
        var emptyDrawCount // watchdog to force the main menu to restart
                = 0

        // the next one will be read when
        // com_frameTime + demoTimeOffset > currentDemoRenderView.
        //
        // TODO: make this private (after sync networking removal and idnet tweaks)
        var guiActive: idUserInterface?
        var guiGameOver: idUserInterface?
        var guiHandle: HandleGuiCommand_t? = null

        //
        var guiInGame: idUserInterface?
        var guiIntro: idUserInterface?
        var guiLoading: idUserInterface?
        var guiMainMenu: idUserInterface?
        var guiMainMenu_MapList // easy map list handling
                : idListGUI = UserInterface.uiManager.AllocListGUI()

        //
        var guiMsg: idUserInterface?
        var guiMsgRestore // store the calling GUI for restore
                : idUserInterface?
        var guiRestartMenu: idUserInterface?
        var guiTakeNotes: idUserInterface? = null
        var guiTest: idUserInterface?

        //
        var insideExecuteMapChange // draw loading screen and update screen on prints
                = false

        // each game tic, numClients usercmds will be added, until full
        //
        var insideUpdateScreen // true while inside ::UpdateScreen()
                = false
        var lastDemoTic = 0
        var lastGameTic // while latchedTicNumber > lastGameTic, run game frames
                = 0

        //
        // we don't want to redraw the loading screen for every single
        // console print that happens
        var lastPacifierTime = 0
        var lastSaveIndex = 0

        //
        var latchedTicNumber // set to com_ticNumber each frame
                = 0

        //
        //	//------------------
        //	// Session_menu.cpp
        //
        var loadGameList: idStrList = idStrList()

        //
        var loadingSaveGame // currently loading map from a SaveGame
                = false

        //
        var logIndex = 0
        var loggedStats: Array<logStats_t>
        var loggedUsercmds: Array<logCmd_t>

        //
        // this is the information required to be set before ExecuteMapChange() is called,
        // which can be saved off at any time with the following commands so it can all be played back
        var mapSpawnData: mapSpawnData_t = mapSpawnData_t()
        var mapSpawned // cleared on Stop()
                = false

        //
        var menuActive = false
        var menuSoundWorld // so the game soundWorld can be muted
                : idSoundWorld?
        var modsList: idStrList = idStrList()
        var msgFireBack: Array<idStr> = arrayOf(idStr(), idStr())
        var msgIgnoreButtons = false

        // #endif
        //	
        var msgRetIndex = 0
        var msgRunning = false

        //
        var numClients // from serverInfo
                = 0
        var numDemoFrames // for timeDemo and demoShot
                = 0
        var savegameFile // this is the savegame file to load from
                : idFile? = null
        var savegameVersion = 0
        var statIndex = 0
        var syncNextGameFrame = false

        //
        var timeDemo: timeDemo_t = timeDemo_t.TD_NO
        var timeDemoStartTime = 0

        //
        //=====================================
        //
        var timeHitch = 0

        //
        var waitingOnBind = false

        //
        /*const*/  var whiteMaterial: Material.idMaterial? = null
        var wipeHold = false

        //
        /*const*/  var wipeMaterial: Material.idMaterial? = null
        var wipeStartTic = 0
        var wipeStopTic = 0
        private var authEmitTimeout = 0

        //
        private var authMsg: idStr = idStr()
        private var authWaitBox = false
        private var cdkey_state: cdKeyState_t = cdKeyState_t.CDKEY_UNKNOWN
        private var xpkey_state: cdKeyState_t = cdKeyState_t.CDKEY_UNKNOWN

        /*
         ===============
         idSessionLocal::Init

         Called in an orderly fashion at system startup,
         so commands, cvars, files, etc are all available
         ===============
         */
        @Throws(idException::class)
        override fun Init() {
            Common.common.Printf("-------- Initializing Session --------\n")
            cmdSystem.AddCommand(
                "writePrecache",
                Sess_WritePrecache_f.getInstance(),
                CmdSystem.CMD_FL_SYSTEM or CmdSystem.CMD_FL_CHEAT,
                "writes precache commands"
            )

//            if (ID_DEDICATED) {
            cmdSystem.AddCommand(
                "map",
                Session_Map_f.getInstance(),
                CmdSystem.CMD_FL_SYSTEM,
                "loads a map",
                ArgCompletion_MapName.getInstance()
            )
            cmdSystem.AddCommand(
                "devmap",
                Session_DevMap_f.getInstance(),
                CmdSystem.CMD_FL_SYSTEM,
                "loads a map in developer mode",
                ArgCompletion_MapName.getInstance()
            )
            cmdSystem.AddCommand(
                "testmap",
                Session_TestMap_f.getInstance(),
                CmdSystem.CMD_FL_SYSTEM,
                "tests a map",
                ArgCompletion_MapName.getInstance()
            )
            cmdSystem.AddCommand(
                "writeCmdDemo",
                Session_WriteCmdDemo_f.getInstance(),
                CmdSystem.CMD_FL_SYSTEM,
                "writes a command demo"
            )
            cmdSystem.AddCommand(
                "playCmdDemo",
                Session_PlayCmdDemo_f.getInstance(),
                CmdSystem.CMD_FL_SYSTEM,
                "plays back a command demo"
            )
            cmdSystem.AddCommand(
                "timeCmdDemo",
                Session_TimeCmdDemo_f.getInstance(),
                CmdSystem.CMD_FL_SYSTEM,
                "times a command demo"
            )
            cmdSystem.AddCommand(
                "exitCmdDemo",
                Session_ExitCmdDemo_f.getInstance(),
                CmdSystem.CMD_FL_SYSTEM,
                "exits a command demo"
            )
            cmdSystem.AddCommand(
                "aviCmdDemo",
                Session_AVICmdDemo_f.getInstance(),
                CmdSystem.CMD_FL_SYSTEM,
                "writes AVIs for a command demo"
            )
            cmdSystem.AddCommand(
                "aviGame",
                Session_AVIGame_f.getInstance(),
                CmdSystem.CMD_FL_SYSTEM,
                "writes AVIs for the current game"
            )
            cmdSystem.AddCommand(
                "recordDemo",
                Session_RecordDemo_f.getInstance(),
                CmdSystem.CMD_FL_SYSTEM,
                "records a demo"
            )
            cmdSystem.AddCommand(
                "stopRecording",
                Session_StopRecordingDemo_f.getInstance(),
                CmdSystem.CMD_FL_SYSTEM,
                "stops demo recording"
            )
            cmdSystem.AddCommand(
                "playDemo",
                Session_PlayDemo_f.getInstance(),
                CmdSystem.CMD_FL_SYSTEM,
                "plays back a demo",
                ArgCompletion_DemoName.getInstance()
            )
            cmdSystem.AddCommand(
                "timeDemo",
                Session_TimeDemo_f.getInstance(),
                CmdSystem.CMD_FL_SYSTEM,
                "times a demo",
                ArgCompletion_DemoName.getInstance()
            )
            cmdSystem.AddCommand(
                "timeDemoQuit",
                Session_TimeDemoQuit_f.getInstance(),
                CmdSystem.CMD_FL_SYSTEM,
                "times a demo and quits",
                ArgCompletion_DemoName.getInstance()
            )
            cmdSystem.AddCommand(
                "aviDemo",
                Session_AVIDemo_f.getInstance(),
                CmdSystem.CMD_FL_SYSTEM,
                "writes AVIs for a demo",
                ArgCompletion_DemoName.getInstance()
            )
            cmdSystem.AddCommand(
                "compressDemo",
                Session_CompressDemo_f.getInstance(),
                CmdSystem.CMD_FL_SYSTEM,
                "compresses a demo file",
                ArgCompletion_DemoName.getInstance()
            )
            //            }
            cmdSystem.AddCommand(
                "disconnect",
                Session_Disconnect_f.getInstance(),
                CmdSystem.CMD_FL_SYSTEM,
                "disconnects from a game"
            )
            if (ID_DEMO_BUILD) {
                cmdSystem.AddCommand(
                    "endOfDemo",
                    Session_EndOfDemo_f.getInstance(),
                    CmdSystem.CMD_FL_SYSTEM,
                    "ends the demo version of the game"
                )
            }
            cmdSystem.AddCommand(
                "demoShot",
                Session_DemoShot_f.getInstance(),
                CmdSystem.CMD_FL_SYSTEM,
                "writes a screenshot for a demo"
            )
            cmdSystem.AddCommand(
                "testGUI",
                Session_TestGUI_f.getInstance(),
                CmdSystem.CMD_FL_SYSTEM,
                "tests a gui"
            )
            cmdSystem.AddCommand(
                "saveGame",
                SaveGame_f.getInstance(),
                CmdSystem.CMD_FL_SYSTEM or CmdSystem.CMD_FL_CHEAT,
                "saves a game"
            )
            cmdSystem.AddCommand(
                "loadGame",
                LoadGame_f.getInstance(),
                CmdSystem.CMD_FL_SYSTEM or CmdSystem.CMD_FL_CHEAT,
                "loads a game",
                ArgCompletion_SaveGame.getInstance()
            )
            cmdSystem.AddCommand(
                "takeViewNotes",
                TakeViewNotes_f.getInstance(),
                CmdSystem.CMD_FL_SYSTEM,
                "take notes about the current map from the current view"
            )
            cmdSystem.AddCommand(
                "takeViewNotes2",
                TakeViewNotes2_f.getInstance(),
                CmdSystem.CMD_FL_SYSTEM,
                "extended take view notes"
            )
            cmdSystem.AddCommand(
                "rescanSI",
                Session_RescanSI_f.getInstance(),
                CmdSystem.CMD_FL_SYSTEM,
                "internal - rescan serverinfo cvars and tell game"
            )
            cmdSystem.AddCommand(
                "promptKey",
                Session_PromptKey_f.getInstance(),
                CmdSystem.CMD_FL_SYSTEM,
                "prompt and sets the CD Key"
            )
            cmdSystem.AddCommand(
                "hitch",
                Session_Hitch_f.getInstance(),
                CmdSystem.CMD_FL_SYSTEM or CmdSystem.CMD_FL_CHEAT,
                "hitches the game"
            )

            // the same idRenderWorld will be used for all games
            // and demos, insuring that level specific models
            // will be freed
            rw = RenderSystem.renderSystem.AllocRenderWorld()
            sw = snd_system.soundSystem.AllocSoundWorld(rw)
            menuSoundWorld = snd_system.soundSystem.AllocSoundWorld(rw)

            // we have a single instance of the main menu
            guiMainMenu = if (ID_DEMO_BUILD) { //#ifndef
                UserInterface.uiManager.FindGui("guis/demo_mainmenu.gui", true, false, true)
            } else {
                UserInterface.uiManager.FindGui("guis/mainmenu.gui", true, false, true)
            }
            guiMainMenu_MapList = UserInterface.uiManager.AllocListGUI()
            guiMainMenu_MapList.Config(guiMainMenu!!, "mapList")
            idAsyncNetwork.client.serverList.GUIConfig(guiMainMenu!!, "serverList")
            guiRestartMenu = UserInterface.uiManager.FindGui("guis/restart.gui", true, false, true)
            guiGameOver = UserInterface.uiManager.FindGui("guis/gameover.gui", true, false, true)
            guiMsg = UserInterface.uiManager.FindGui("guis/msg.gui", true, false, true)
            guiTakeNotes = UserInterface.uiManager.FindGui("guis/takeNotes.gui", true, false, true)
            guiIntro = UserInterface.uiManager.FindGui("guis/intro.gui", true, false, true)
            whiteMaterial = DeclManager.declManager.FindMaterial("_white")
            guiInGame = null
            guiTest = null
            guiActive = null
            guiHandle = null
            ReadCDKey()
            Common.common.Printf("session initialized\n")
            Common.common.Printf("--------------------------------------\n")
        }

        override fun Shutdown() {
            var i: Int
            if (aviCaptureMode) {
                EndAVICapture()
            }
            Stop()
            if (rw != null) {
//		delete rw;
                //rw = null
            }
            if (sw != null) {
//		delete sw;
                //sw = null
            }
            if (menuSoundWorld != null) {
//		delete menuSoundWorld;
                //menuSoundWorld = null
            }
            mapSpawnData.serverInfo.Clear()
            mapSpawnData.syncedCVars.Clear()
            i = 0
            while (i < AsyncNetwork.MAX_ASYNC_CLIENTS) {
                mapSpawnData.userInfo[i].Clear()
                mapSpawnData.persistentPlayerInfo[i].Clear()
                i++
            }
            if (guiMainMenu_MapList != null) {
                guiMainMenu_MapList.Shutdown()
                UserInterface.uiManager.FreeListGUI(guiMainMenu_MapList)
                guiMainMenu_MapList = UserInterface.uiManager.AllocListGUI()
            }
            Clear()
        }

        /*
         ===============
         idSessionLocal::Stop

         called on errors and game exits
         ===============
         */
        override fun Stop() {
            ClearWipe()

            // clear mapSpawned and demo playing flags
            UnloadMap()

            // disconnect async client
            idAsyncNetwork.client.DisconnectFromServer()

            // kill async server
            idAsyncNetwork.server.Kill()
            if (sw != null) {
                sw.StopAllSounds()
            }
            insideUpdateScreen = false
            insideExecuteMapChange = false

            // drop all guis
            SetGUI(null, null)
        }

        override fun UpdateScreen() {
            UpdateScreen(true)
        }

        override fun UpdateScreen(outOfSequence: Boolean) {
            if (_WIN32) {
                if (Common.com_editors != 0) {
                    if (!win_main.Sys_IsWindowVisible()) {
                        return
                    }
                }
            }
            // FIX: C++ just returns silently on recursive calls (the FatalError is commented out)
            if (insideUpdateScreen) {
                return
            }
            insideUpdateScreen = true

            // if this is a long-operation update and we are in windowed mode,
            // release the mouse capture back to the desktop
            if (outOfSequence) {
                win_input.Sys_GrabMouseCursor(false)
            }
            RenderSystem.renderSystem.BeginFrame(
                RenderSystem.renderSystem.GetScreenWidth(),
                RenderSystem.renderSystem.GetScreenHeight()
            )

            // draw everything
            Draw()
            if (Common.com_speeds.GetBool()) {
                val time_frontend = intArrayOf(0)
                val time_backend = intArrayOf(0)
                RenderSystem.renderSystem.EndFrame(time_frontend, time_backend)
                Common.time_frontend = time_frontend[0]
                Common.time_backend = time_backend[0]
            } else {
                RenderSystem.renderSystem.EndFrame(null, null)
            }
            insideUpdateScreen = false
        }

        override fun PacifierUpdate() {
            if (!insideExecuteMapChange) {
                return
            }

            // never do pacifier screen updates while inside the
            // drawing code, or we can have various recursive problems
            if (insideUpdateScreen) {
                return
            }
            val time = EventLoop.eventLoop.Milliseconds()
            if (time - lastPacifierTime < 100) {
                return
            }
            lastPacifierTime = time
            if (guiLoading != null && bytesNeededForMapLoad != 0) {
                val n = FileSystem_h.fileSystem.GetReadCount().toFloat()
                val pct = n / bytesNeededForMapLoad
                // pct = idMath::ClampFloat( 0.0f.0f, 100.0f.0f, pct );
                guiLoading!!.SetStateFloat("map_loading", pct)
                guiLoading!!.StateChanged(Common.com_frameTime)
            }
            win_main.Sys_GenerateEvents()
            UpdateScreen()
            idAsyncNetwork.client.PacifierUpdate()
            idAsyncNetwork.server.PacifierUpdate()
        }

        @Throws(idException::class)
        override fun Frame() {
            Common.common.Async()
            if (Common.com_asyncSound.GetInteger() == 0) {
                snd_system.soundSystem.AsyncUpdate(win_shared.Sys_Milliseconds())
            }

            // Editors that completely take over the game
            if (Common.com_editorActive && Common.com_editors and (Common.EDITOR_RADIANT or Common.EDITOR_GUI) != 0) {
                return
            }

            // if the console is down, we don't need to hold
            // the mouse cursor
            win_input.Sys_GrabMouseCursor(!Console.console.Active() && !Common.com_editorActive)

            // save the screenshot and audio from the last draw if needed
            if (aviCaptureMode) {
                val name: idStr
                name = idStr(
                    Str.va(
                        "demos/%s/%s_%05i.tga",
                        aviDemoShortName.toString(),
                        aviDemoShortName.toString(),
                        aviTicStart
                    )
                )
                val ratio = 30.0f / (1000.0f / UsercmdGen.USERCMD_MSEC / com_aviDemoTics.GetInteger())
                aviDemoFrameCount += ratio
                if (aviTicStart + 1 != aviDemoFrameCount.toInt()) {
                    // skipped frames so write them out
                    var c = (aviDemoFrameCount - aviTicStart).toInt()
                    while (c-- != 0) {
                        RenderSystem.renderSystem.TakeScreenshot(
                            com_aviDemoWidth.GetInteger(),
                            com_aviDemoHeight.GetInteger(),
                            name.toString(),
                            com_aviDemoSamples.GetInteger(),
                            null
                        )
                        name.set(
                            Str.va(
                                "demos/%s/%s_%05i.tga",
                                aviDemoShortName.toString(),
                                aviDemoShortName.toString(),
                                ++aviTicStart
                            )
                        )
                    }
                }
                aviTicStart = aviDemoFrameCount.toInt()

                // remove any printed lines at the top before taking the screenshot
                Console.console.ClearNotifyLines()

                // this will call Draw, possibly multiple times if com_aviDemoSamples is > 1
                RenderSystem.renderSystem.TakeScreenshot(
                    com_aviDemoWidth.GetInteger(),
                    com_aviDemoHeight.GetInteger(),
                    name.toString(),
                    com_aviDemoSamples.GetInteger(),
                    null
                )
            }

            // at startup, we may be backwards
            if (latchedTicNumber > Common.com_ticNumber) {
                latchedTicNumber = Common.com_ticNumber
            }

            // se how many tics we should have before continuing
            var minTic = latchedTicNumber + 1
            if (com_minTics.GetInteger() > 1) {
                minTic = lastGameTic + com_minTics.GetInteger()
            }
            if (readDemo != null) {
                // FIX: C++ `!timeDemo` means `timeDemo == TD_NO` (0 is falsy), not null check
                minTic = if (timeDemo == timeDemo_t.TD_NO && numDemoFrames != 1) {
                    lastDemoTic + USERCMD_PER_DEMO_FRAME
                } else {
                    // timedemos and demoshots will run as fast as they can, other demos
                    // will not run more than 30 hz
                    latchedTicNumber
                }
            } else if (writeDemo != null) {
                minTic = lastGameTic + USERCMD_PER_DEMO_FRAME // demos are recorded at 30 hz
            }

            // fixedTic lets us run a forced number of usercmd each frame without timing
            if (com_fixedTic.GetInteger() != 0) {
                minTic = latchedTicNumber
            }

            // FIXME: deserves a cleanup and abstraction
            if (_WIN32 || _MACOSX) {
                // Spin in place if needed.  The game should yield the cpu if
                // it is running over 60 hz, because there is fundamentally
                // nothing useful for it to do.
                while (true) {
                    latchedTicNumber = Common.com_ticNumber
                    //                    System.out.printf("Frame(%d, %d)\n", latchedTicNumber, minTic);
                    if (latchedTicNumber >= minTic) {
                        break
                    }
                    // Force Async tick call
                    Common.common.Async()
                    win_main.Sys_Sleep(1)
                    //win_main.hTimer.isTerminated
                    //                    if (win_main.DEBUG) {
//                        //TODO:the debugger slows the code too much at this point, so we shall manually move to the next frame.
//                        com_ticNumber = minTic;
//                    }
                }
            } else {
                while (true) {
                    latchedTicNumber = Common.com_ticNumber
                    if (latchedTicNumber >= minTic) {
                        break
                    }
                    win_main.Sys_WaitForEvent(sys_public.TRIGGER_EVENT_ONE)
                }
            }
            if (authEmitTimeout != 0) {
                // waiting for a game auth
                if (win_shared.Sys_Milliseconds() > authEmitTimeout) {
                    // expired with no reply
                    // means that if a firewall is blocking the master, we will let through
                    Common.common.DPrintf("no reply from auth\n")
                    if (authWaitBox) {
                        // close the wait box
                        StopBox()
                        authWaitBox = false
                    }
                    if (cdkey_state == cdKeyState_t.CDKEY_CHECKING) {
                        cdkey_state = cdKeyState_t.CDKEY_OK
                    }
                    if (xpkey_state == cdKeyState_t.CDKEY_CHECKING) {
                        xpkey_state = cdKeyState_t.CDKEY_OK
                    }
                    // maintain this empty as it's set by auth denials
                    authMsg.Empty()
                    authEmitTimeout = 0
                    SetCDKeyGuiVars()
                }
            }

            // send frame and mouse events to active guis
            GuiFrameEvents()

            // advance demos
            if (readDemo != null) {
                AdvanceRenderDemo(false)
                return
            }

            //------------ single player game tics --------------
            if (!mapSpawned || guiActive != null) {
                if (!Common.com_asyncInput.GetBool()) {
                    // early exit, won't do RunGameTic .. but still need to update mouse position for GUIs
                    UsercmdGen.usercmdGen.GetDirectUsercmd()
                }
            }
            if (!mapSpawned) {
                return
            }
            if (guiActive != null) {
                lastGameTic = latchedTicNumber
                return
            }

            // in message box / GUIFrame, idSessionLocal::Frame is used for GUI interactivity
            // but we early exit to avoid running game frames
            if (idAsyncNetwork.IsActive()) {
                return
            }

            // check for user info changes
            if (cvarSystem.GetModifiedFlags() and CVarSystem.CVAR_USERINFO != 0) {
                mapSpawnData.userInfo[0] = cvarSystem.MoveCVarsToDict(CVarSystem.CVAR_USERINFO)
                Game_local.game.SetUserInfo(0, mapSpawnData.userInfo[0], false, false)
                cvarSystem.ClearModifiedFlags(CVarSystem.CVAR_USERINFO)
            }

            // see how many usercmds we are going to run
            var numCmdsToRun = latchedTicNumber - lastGameTic

            // don't let a long onDemand sound load unsync everything
            if (timeHitch != 0) {
                val skip = timeHitch / UsercmdGen.USERCMD_MSEC
                lastGameTic += skip
                numCmdsToRun -= skip
                timeHitch = 0
            }

            // don't get too far behind after a hitch
            if (numCmdsToRun > 10) {
                lastGameTic = latchedTicNumber - 10
            }

            // never use more than USERCMD_PER_DEMO_FRAME,
            // which makes it go into slow motion when recording
            if (writeDemo != null) {
                val fixedTic = USERCMD_PER_DEMO_FRAME
                // we should have waited long enough
                if (numCmdsToRun < fixedTic) {
                    Common.common.Error("idSessionLocal::Frame: numCmdsToRun < fixedTic")
                }
                // we may need to dump older commands
                lastGameTic = latchedTicNumber - fixedTic
            } else if (com_fixedTic.GetInteger() > 0) {
                // this may cause commands run in a previous frame to
                // be run again if we are going at above the real time rate
                lastGameTic = latchedTicNumber - com_fixedTic.GetInteger()
            } else if (aviCaptureMode) {
                lastGameTic = latchedTicNumber - com_aviDemoTics.GetInteger()
            }

            // force only one game frame update this frame.  the game code requests this after skipping cinematics
            // so we come back immediately after the cinematic is done instead of a few frames later which can
            // cause sounds played right after the cinematic to not play.
            if (syncNextGameFrame) {
                lastGameTic = latchedTicNumber - 1
                syncNextGameFrame = false
            }

            // create client commands, which will be sent directly
            // to the game
            if (com_showTics.GetBool()) {
                Common.common.Printf("%d ", latchedTicNumber - lastGameTic)
            }
            val gameTicsToRun = latchedTicNumber - lastGameTic
            var i: Int
            i = 0
            while (i < gameTicsToRun) {
                RunGameTic()
                if (!mapSpawned) {
                    // exited game play
                    break
                }
                if (syncNextGameFrame) {
                    // long game frame, so break out and continue executing as if there was no hitch
                    break
                }
                i++
            }
        }

        override fun IsMultiplayer(): Boolean {
            return idAsyncNetwork.IsActive()
        }

        @Throws(idException::class)
        override fun ProcessEvent(event: sysEvent_s): Boolean {
            // hitting escape anywhere brings up the menu
            // FIX: C++ also checks `!idKeyInput::IsDown( K_SHIFT )` — Shift+Escape opens console, not menu
            if (guiActive == null && event.evType == sysEventType_t.SE_KEY && event.evValue2 == 1 && event.evValue == KeyInput.K_ESCAPE && !idKeyInput.IsDown(
                    KeyInput.K_SHIFT
                )
            ) {
                Console.console.Close()
                if (Game_local.game != null) {
                    val op = Game_local.game.HandleESC()
                    if (op == escReply_t.ESC_IGNORE) {
                        return true
                    } else if (op == escReply_t.ESC_GUI) {
                        SetGUI(Game_local.game.escGui, null)
                        return true
                    }
                }
                StartMenu()
                return true
            }

            // let the pull-down console take it if desired
            if (Console.console.ProcessEvent(event, false)) {
                return true
            }

            // if we are testing a GUI, send all events to it
            if (guiTest != null) {
                // hitting escape exits the testgui
                if (event.evType == sysEventType_t.SE_KEY && event.evValue2 == 1 && event.evValue == KeyInput.K_ESCAPE) {
                    guiTest = null
                    return true
                }
                cmd = guiTest!!.HandleEvent(event, Common.com_frameTime)!!.toCharArray()
                if (cmd != null && cmd!!.get(0) != '\u0000') {
                    Common.common.Printf("testGui event returned: '%s'\n", cmd!!)
                }
                return true
            }

            // menus / etc
            if (guiActive != null) {
                MenuEvent(event)
                return true
            }

            // if we aren't in a game, force the console to take it
            if (!mapSpawned) {
                Console.console.ProcessEvent(event, true)
                return true
            }

            // in game, exec bindings for all key downs
            if (event.evType == sysEventType_t.SE_KEY && event.evValue2 == 1) {
                idKeyInput.ExecKeyBinding(event.evValue)
                return true
            }
            return false
        }

        @Throws(idException::class)
        override fun StartMenu(playIntro: Boolean) {
            if (guiActive == guiMainMenu) {
                return
            }
            if (readDemo != null) {
                // if we're playing a demo, esc kills it
                UnloadMap()
            }

            // pause the game sound world
            if (sw != null && !sw.IsPaused()) {
                sw.Pause()
            }

            // start playing the menu sounds
            snd_system.soundSystem.SetPlayingSoundWorld(menuSoundWorld!!)
            SetGUI(guiMainMenu, null)
            guiMainMenu!!.HandleNamedEvent(if (playIntro) "playIntro" else "noIntro")
            if (FileSystem_h.fileSystem.HasD3XP()) {
                guiMainMenu!!.SetStateString("game_list", Common.common.GetLanguageDict().GetString("#str_07202"))
            } else {
                guiMainMenu!!.SetStateString("game_list", Common.common.GetLanguageDict().GetString("#str_07212"))
            }
            Console.console.Close()
        }

        fun ExitMenu() {
            guiActive = null

            // go back to the game sounds
            snd_system.soundSystem.SetPlayingSoundWorld(sw)

            // unpause the game sound world
            if (sw != null && sw.IsPaused()) {
                sw.UnPause()
            }
        }

        override fun SetGUI(gui: idUserInterface?, handle: HandleGuiCommand_t?) {
            val cmd: String?
            guiActive = gui
            guiHandle = handle
            if (guiMsgRestore != null) {
                Common.common.DPrintf("idSessionLocal::SetGUI: cleared an active message box\n")
                guiMsgRestore = null
            }
            if (null == guiActive) {
                return
            }
            if (guiActive == guiMainMenu) {
                SetSaveGameGuiVars()
                SetMainMenuGuiVars()
            } else if (guiActive == guiRestartMenu) {
                SetSaveGameGuiVars()
            }
            val ev: sysEvent_s
            ev = sysEvent_s() //memset( ev, 0, sizeof( ev ) );
            ev.evType = sysEventType_t.SE_NONE
            cmd = guiActive!!.HandleEvent(ev, Common.com_frameTime)
            guiActive!!.Activate(true, Common.com_frameTime)
        }

        override fun GuiFrameEvents() {
            frameEvents++
            val cmd: String?
            val ev: sysEvent_s
            val gui: idUserInterface?

            // stop generating move and button commands when a local console or menu is active
            // running here so SP, async networking and no game all go through it
            UsercmdGen.usercmdGen.InhibitUsercmd(
                inhibit_t.INHIBIT_SESSION,
                Console.console.Active() || guiActive != null
            )
            gui = if (guiTest != null) {
                guiTest
            } else if (guiActive != null) {
                guiActive
            } else {
                return
            }

//	memset( &ev, 0, sizeof( ev ) );
            ev = sysEvent_s()
            ev.evType = sysEventType_t.SE_NONE
            //            System.out.println(System.nanoTime()+"com_frameTime="+com_frameTime+" "+Common.com_ticNumber);
            cmd = gui!!.HandleEvent(ev, Common.com_frameTime)
            if (cmd != null && cmd.isNotEmpty()) {
                DispatchCommand(guiActive, cmd, false)
            }
        }

        override fun MessageBox(type: msgBoxType_t, message: String): String {
            return MessageBox(type, message, null, false, null, null, false) ?: ""
        }

        override fun MessageBox(type: msgBoxType_t, message: String, title: String): String {
            return MessageBox(type, message, title, false, null, null, false) ?: ""
        }

        override fun MessageBox(type: msgBoxType_t, message: String, title: String?, wait: Boolean): String {
            return MessageBox(type, message, title, wait, null, null, false) ?: ""
        }

        override fun MessageBox(
            type: msgBoxType_t,
            message: String,
            title: String,
            wait: Boolean,
            fire_yes: String
        ): String {
            return MessageBox(type, message, title, wait, fire_yes, null, false) ?: ""
        }

        override fun MessageBox(
            type: msgBoxType_t,
            message: String,
            title: String,
            wait: Boolean,
            fire_yes: String,
            fire_no: String
        ): String {
            return MessageBox(type, message, title, wait, fire_yes, fire_no, false) ?: ""
        }

        override fun MessageBox(
            type: msgBoxType_t,
            message: String,
            title: String?,
            wait: Boolean,
            fire_yes: String?,
            fire_no: String?,
            network: Boolean
        ): String? {
            Common.common.DPrintf("MessageBox: %s - %s\n", "" + title, "" + message)
            if (!BoxDialogSanityCheck()) {
                return null
            }
            guiMsg!!.SetStateString("title", "" + title)
            guiMsg!!.SetStateString("message", "" + message)
            if (type == msgBoxType_t.MSG_WAIT) {
                guiMsg!!.SetStateString("visible_msgbox", "0")
                guiMsg!!.SetStateString("visible_waitbox", "1")
            } else {
                guiMsg!!.SetStateString("visible_msgbox", "1")
                guiMsg!!.SetStateString("visible_waitbox", "0")
            }
            guiMsg!!.SetStateString("visible_entry", "0")
            guiMsg!!.SetStateString("visible_cdkey", "0")
            when (type) {
                msgBoxType_t.MSG_INFO -> {
                    guiMsg!!.SetStateString("mid", "")
                    guiMsg!!.SetStateString("visible_mid", "0")
                    guiMsg!!.SetStateString("visible_left", "0")
                    guiMsg!!.SetStateString("visible_right", "0")
                }

                msgBoxType_t.MSG_OK -> {
                    guiMsg!!.SetStateString("mid", Common.common.GetLanguageDict().GetString("#str_04339"))
                    guiMsg!!.SetStateString("visible_mid", "1")
                    guiMsg!!.SetStateString("visible_left", "0")
                    guiMsg!!.SetStateString("visible_right", "0")
                }

                msgBoxType_t.MSG_ABORT -> {
                    guiMsg!!.SetStateString("mid", Common.common.GetLanguageDict().GetString("#str_04340"))
                    guiMsg!!.SetStateString("visible_mid", "1")
                    guiMsg!!.SetStateString("visible_left", "0")
                    guiMsg!!.SetStateString("visible_right", "0")
                }

                msgBoxType_t.MSG_OKCANCEL -> {
                    guiMsg!!.SetStateString("left", Common.common.GetLanguageDict().GetString("#str_04339"))
                    guiMsg!!.SetStateString("right", Common.common.GetLanguageDict().GetString("#str_04340"))
                    guiMsg!!.SetStateString("visible_mid", "0")
                    guiMsg!!.SetStateString("visible_left", "1")
                    guiMsg!!.SetStateString("visible_right", "1")
                }

                msgBoxType_t.MSG_YESNO -> {
                    guiMsg!!.SetStateString("left", Common.common.GetLanguageDict().GetString("#str_04341"))
                    guiMsg!!.SetStateString("right", Common.common.GetLanguageDict().GetString("#str_04342"))
                    guiMsg!!.SetStateString("visible_mid", "0")
                    guiMsg!!.SetStateString("visible_left", "1")
                    guiMsg!!.SetStateString("visible_right", "1")
                }

                msgBoxType_t.MSG_PROMPT -> {
                    guiMsg!!.SetStateString("left", Common.common.GetLanguageDict().GetString("#str_04339"))
                    guiMsg!!.SetStateString("right", Common.common.GetLanguageDict().GetString("#str_04340"))
                    guiMsg!!.SetStateString("visible_mid", "0")
                    guiMsg!!.SetStateString("visible_left", "1")
                    guiMsg!!.SetStateString("visible_right", "1")
                    guiMsg!!.SetStateString("visible_entry", "1")
                    guiMsg!!.HandleNamedEvent("Prompt")
                }

                msgBoxType_t.MSG_CDKEY -> {
                    guiMsg!!.SetStateString("left", Common.common.GetLanguageDict().GetString("#str_04339"))
                    guiMsg!!.SetStateString("right", Common.common.GetLanguageDict().GetString("#str_04340"))
                    guiMsg!!.SetStateString("visible_msgbox", "0")
                    guiMsg!!.SetStateString("visible_cdkey", "1")
                    guiMsg!!.SetStateString("visible_hasxp", if (FileSystem_h.fileSystem.HasD3XP()) "1" else "0")
                    // the current cdkey / xpkey values may have bad/random data in them
                    // it's best to avoid printing them completely, unless the key is good
                    if (cdkey_state == cdKeyState_t.CDKEY_OK) {
                        guiMsg!!.SetStateString("str_cdkey", String(cdkey))
                        guiMsg!!.SetStateString("visible_cdchk", "0")
                    } else {
                        guiMsg!!.SetStateString("str_cdkey", "")
                        guiMsg!!.SetStateString("visible_cdchk", "1")
                    }
                    guiMsg!!.SetStateString("str_cdchk", "")
                    if (xpkey_state == cdKeyState_t.CDKEY_OK) {
                        guiMsg!!.SetStateString("str_xpkey", String(xpkey))
                        guiMsg!!.SetStateString("visible_xpchk", "0")
                    } else {
                        guiMsg!!.SetStateString("str_xpkey", "")
                        guiMsg!!.SetStateString("visible_xpchk", "1")
                    }
                    guiMsg!!.SetStateString("str_xpchk", "")
                    guiMsg!!.HandleNamedEvent("CDKey")
                }

                msgBoxType_t.MSG_WAIT -> {}
                else -> Common.common.Printf("idSessionLocal::MessageBox: unknown msg box type\n")
            }
            msgFireBack[0].set("" + fire_yes)
            msgFireBack[1].set("" + fire_no)
            guiMsgRestore = guiActive
            guiActive = guiMsg
            guiMsg!!.SetCursor(325.0f, 290.0f)
            guiActive!!.Activate(true, Common.com_frameTime)
            msgRunning = true
            msgRetIndex = -1
            if (wait) {
                // play one frame ignoring events so we don't get confused by parasite button releases
                msgIgnoreButtons = true
                Common.common.GUIFrame(true, network)
                msgIgnoreButtons = false
                while (msgRunning) {
                    Common.common.GUIFrame(true, network)
                }
                if (msgRetIndex < 0) {
                    // MSG_WAIT and other StopBox calls
                    return null
                }
                return if (type == msgBoxType_t.MSG_PROMPT) {
                    if (msgRetIndex == 0) {
                        guiMsg!!.State().GetString("str_entry", "", msgFireBack[0])
                        msgFireBack[0].toString()
                    } else {
                        null
                    }
                } else if (type == msgBoxType_t.MSG_CDKEY) {
                    if (msgRetIndex == 0) {
                        // the visible_ values distinguish looking at a valid key, or editing it
                        msgFireBack[0].set(
                            String.format(
                                "%1s;%16s;%2s;%1s;%16s;%2s",
                                guiMsg!!.State().GetString("visible_cdchk"),
                                guiMsg!!.State().GetString("str_cdkey"),
                                guiMsg!!.State().GetString("str_cdchk"),
                                guiMsg!!.State().GetString("visible_xpchk"),
                                guiMsg!!.State().GetString("str_xpkey"),
                                guiMsg!!.State().GetString("str_xpchk")
                            )
                        )
                        msgFireBack[0].toString()
                    } else {
                        null
                    }
                } else {
                    msgFireBack[msgRetIndex].toString()
                }
            }
            return null
        }

        override fun StopBox() {
            if (guiActive == guiMsg) {
                HandleMsgCommands("stop")
            }
        }

        override fun DownloadProgressBox(bgl: backgroundDownload_s, title: String) {
            DownloadProgressBox(bgl, title, 0)
        }

        override fun DownloadProgressBox(bgl: backgroundDownload_s, title: String, progress_start: Int) {
            DownloadProgressBox(bgl, title, progress_start, 100)
        }

        override fun DownloadProgressBox(
            bgl: backgroundDownload_s,
            title: String,
            progress_start: Int,
            progress_end: Int
        ) {
            var dlnow = 0
            var dltotal = 0
            val startTime = win_shared.Sys_Milliseconds()
            var lapsed: Int
            val sNow = idStr()
            val sTotal = idStr()
            var sBW = idStr()
            var sETA: String
            var sMsg: String
            if (!BoxDialogSanityCheck()) {
                return
            }
            guiMsg!!.SetStateString("visible_msgbox", "1")
            guiMsg!!.SetStateString("visible_waitbox", "0")
            guiMsg!!.SetStateString("visible_entry", "0")
            guiMsg!!.SetStateString("visible_cdkey", "0")
            guiMsg!!.SetStateString("mid", "Cancel")
            guiMsg!!.SetStateString("visible_mid", "1")
            guiMsg!!.SetStateString("visible_left", "0")
            guiMsg!!.SetStateString("visible_right", "0")
            guiMsg!!.SetStateString("title", title)
            guiMsg!!.SetStateString("message", "Connecting..")
            guiMsgRestore = guiActive
            guiActive = guiMsg
            msgRunning = true
            while (true) {
                while (msgRunning) {
                    Common.common.GUIFrame(true, false)
                    if (bgl.completed) {
                        guiActive = guiMsgRestore
                        guiMsgRestore = null
                        return
                    } else if (bgl.url.dltotal != dltotal || bgl.url.dlnow != dlnow) {
                        dltotal = bgl.url.dltotal
                        dlnow = bgl.url.dlnow
                        lapsed = win_shared.Sys_Milliseconds() - startTime
                        sNow.BestUnit("%.2f", dlnow.toFloat(), Measure_t.MEASURE_SIZE)
                        if (lapsed > 2000) {
                            sBW.BestUnit("%.1f", 1000.0f * dlnow / lapsed, Measure_t.MEASURE_BANDWIDTH)
                        } else {
                            sBW = idStr("-- KB/s")
                        }
                        if (dltotal != 0) {
                            sTotal.BestUnit("%.2f", dltotal.toFloat(), Measure_t.MEASURE_SIZE)
                            if (lapsed < 2000) {
                                sMsg = String.format("%s / %s", sNow, sTotal)
                            } else {
                                sETA = String.format(
                                    "%.0f.0f sec",
                                    (dltotal.toFloat() / dlnow.toFloat() - 1.0f) * lapsed / 1000
                                )
                                sMsg = String.format("%s / %s ( %s - %s )", sNow, sTotal, sBW, sETA)
                            }
                        } else {
                            sMsg = if (lapsed < 2000) {
                                sNow.toString()
                            } else {
                                String.format("%s - %s", sNow, sBW)
                            }
                        }
                        if (dltotal != 0) {
                            guiMsg!!.SetStateString(
                                "progress",
                                Str.va("%d", progress_start + dlnow * (progress_end - progress_start) / dltotal)
                            )
                        } else {
                            guiMsg!!.SetStateString("progress", "0")
                        }
                        guiMsg!!.SetStateString("message", sMsg)
                    }
                }
                // abort was used - tell the downloader and wait till final stop
                bgl.url.status = dlStatus_t.DL_ABORTING
                guiMsg!!.SetStateString("title", "Aborting..")
                guiMsg!!.SetStateString("visible_mid", "0")
                // continue looping
                guiMsgRestore = guiActive
                guiActive = guiMsg
                msgRunning = true
            }
        }

        override fun SetPlayingSoundWorld() {
            // FIX: C++ has `guiActive == guiLoading || ( guiActive == guiMsg && !mapSpawned )`
            // Kotlin had `guiActive == guiLoading != null` which is always true (Boolean != null)
            if (guiActive != null && (guiActive == guiMainMenu || guiActive == guiIntro || guiActive == guiLoading || (guiActive == guiMsg && !mapSpawned))) {
                snd_system.soundSystem.SetPlayingSoundWorld(menuSoundWorld!!)
            } else {
                snd_system.soundSystem.SetPlayingSoundWorld(sw)
            }
        }

        /*
         ===============
         idSessionLocal::TimeHitch

         this is used by the sound system when an OnDemand sound is loaded, so the game action
         doesn't advance and get things out of sync
         ===============
         */
        override fun TimeHitch(msec: Int) {
            timeHitch += msec
        }

        override fun ReadCDKey() {
            var filename: String
            var f: idFile?
            val buffer = ByteBuffer.allocate(32 * 2) //=new char[32];
            cdkey_state = cdKeyState_t.CDKEY_UNKNOWN
            filename = "../" + Licensee.BASE_GAMEDIR + "/" + Licensee.CDKEY_FILE
            f = FileSystem_h.fileSystem.OpenExplicitFileRead(
                FileSystem_h.fileSystem.RelativePathToOSPath(
                    filename,
                    "fs_savepath"
                )
            )
            if (null == f) {
                Common.common.Printf("Couldn't read %s.\n", filename)
                cdkey[0] = '\u0000'
            } else {
//		memset( buffer, 0, sizeof(buffer) );
                f.Read(buffer, CDKEY_BUF_LEN - 1)
                FileSystem_h.fileSystem.CloseFile(f)
                idStr.Copynz(cdkey, String(buffer.array()), CDKEY_BUF_LEN)
            }
            xpkey_state = cdKeyState_t.CDKEY_UNKNOWN
            filename = "../" + Licensee.BASE_GAMEDIR + "/" + Licensee.XPKEY_FILE
            f = FileSystem_h.fileSystem.OpenExplicitFileRead(
                FileSystem_h.fileSystem.RelativePathToOSPath(
                    filename,
                    "fs_savepath"
                )
            )
            if (null == f) {
                Common.common.Printf("Couldn't read %s.\n", filename)
                xpkey[0] = '\u0000'
            } else {
//		memset( buffer, 0, sizeof(buffer) );
                buffer.clear()
                f.Read(buffer, CDKEY_BUF_LEN - 1)
                FileSystem_h.fileSystem.CloseFile(f)
                idStr.Copynz(xpkey, String(buffer.array()), CDKEY_BUF_LEN)
            }
        }

        override fun WriteCDKey() {
            var filename: String
            var f: idFile?
            val OSPath: String?
            filename = "../" + Licensee.BASE_GAMEDIR + "/" + Licensee.CDKEY_FILE
            // OpenFileWrite advertises creating directories to the path if needed, but that won't work with a '..' in the path
            // occasionally on windows, but mostly on Linux and OSX, the fs_savepath/base may not exist in full
            OSPath = FileSystem_h.fileSystem.BuildOSPath(
                cvarSystem.GetCVarString("fs_savepath"),
                Licensee.BASE_GAMEDIR,
                Licensee.CDKEY_FILE
            )
            FileSystem_h.fileSystem.CreateOSPath(OSPath)
            f = FileSystem_h.fileSystem.OpenFileWrite(filename)
            if (null == f) {
                Common.common.Printf("Couldn't write %s.\n", filename)
                return
            }
            f.Printf("%s%s", cdkey, Licensee.CDKEY_TEXT)
            FileSystem_h.fileSystem.CloseFile(f)
            filename = "../" + Licensee.BASE_GAMEDIR + "/" + Licensee.XPKEY_FILE
            f = FileSystem_h.fileSystem.OpenFileWrite(filename)
            if (null == f) {
                Common.common.Printf("Couldn't write %s.\n", filename)
                return
            }
            f.Printf("%s%s", xpkey, Licensee.CDKEY_TEXT)
            FileSystem_h.fileSystem.CloseFile(f)
        }

        override fun GetCDKey(xp: Boolean): String? {
            if (!xp) {
                return TempDump.ctos(cdkey)
            }
            return if (xpkey_state == cdKeyState_t.CDKEY_OK || xpkey_state == cdKeyState_t.CDKEY_CHECKING) {
                // FIX: C++ returns `xpkey`, not `cdkey`, when requesting the XP key
                TempDump.ctos(xpkey)
            } else null
        }

        //        
        //        
        //        
        //        
        //=====================================
        //
        /*
         ================
         idSessionLocal::CheckKey
         the function will only modify keys to _OK or _CHECKING if the offline checks are passed
         if the function returns false, the offline checks failed, and offline_valid holds which keys are bad
         ================
         */
        override fun CheckKey(key: String, netConnect: Boolean, offline_valid: BooleanArray): Boolean {
            val lkey = Array<CharArray>(2) { CharArray(CDKEY_BUF_LEN) }
            val l_chk = Array<CharArray>(2) { CharArray(3) }
            val s_chk = CharArray(3)
            val imax: Int
            var i_key: Int
            /*unsigned*/
            var checksum: Int
            var chk8: Int //TODO:bitwise ops on longs!?
            val edited_key = BooleanArray(2)
            assert(key.length == (CDKEY_BUF_LEN - 1) * 2 + 4 + 3 + 4)
            edited_key[0] = key[0] == '1'
            idStr.Copynz(lkey[0], key + 2, CDKEY_BUF_LEN)
            idStr.ToUpper(lkey[0])
            idStr.Copynz(l_chk[0], key + CDKEY_BUF_LEN + 2, 3)
            idStr.ToUpper(l_chk[0])
            edited_key[1] = key[CDKEY_BUF_LEN + 2 + 3] == '1'
            idStr.Copynz(lkey[1], key + CDKEY_BUF_LEN + 7, CDKEY_BUF_LEN)
            idStr.ToUpper(lkey[1])
            idStr.Copynz(l_chk[1], key + CDKEY_BUF_LEN * 2 + 7, 3)
            idStr.ToUpper(l_chk[1])
            imax = if (FileSystem_h.fileSystem.HasD3XP()) {
                2
            } else {
                1
            }
            offline_valid[1] = true
            offline_valid[0] = offline_valid[1]
            i_key = 0
            while (i_key < imax) {

                // check that the characters are from the valid set
                var i: Int
                i = 0
                while (i < CDKEY_BUF_LEN - 1) {
                    if (-1 == CDKEY_DIGITS.indexOf(lkey[i_key].get(i))) {
                        offline_valid[i_key] = false
                        i++
                        continue
                    }
                    i++
                }
                if (edited_key[i_key]) {
                    // verify the checksum for edited keys only
                    checksum = CRC32.CRC32_BlockChecksum(lkey[i_key], CDKEY_BUF_LEN - 1).toInt()
                    chk8 =
                        checksum and 0xff xor (checksum and 0xff00 shr 8 xor (checksum and 0xff0000 shr 16 xor (checksum and -0x1000000 shr 24)))
                    idStr.snPrintf(s_chk, 3, "%02X", chk8)
                    if (idStr.Icmp(TempDump.ctos(l_chk[i_key]), TempDump.ctos(s_chk)) != 0) {
                        offline_valid[i_key] = false
                        i_key++
                        continue
                    }
                }
                i_key++
            }
            if (!offline_valid[0] || !offline_valid[1]) {
                return false
            }

            // offline checks passed, we'll return true and optionally emit key check requests
            // the function should only modify the key states if the offline checks passed successfully
            // set the keys, don't send a game auth if we are net connecting
            idStr.Copynz(cdkey, lkey[0], CDKEY_BUF_LEN)
            cdkey_state = if (netConnect) cdKeyState_t.CDKEY_OK else cdKeyState_t.CDKEY_CHECKING
            xpkey_state = if (FileSystem_h.fileSystem.HasD3XP()) {
                idStr.Copynz(xpkey, lkey[1], CDKEY_BUF_LEN)
                if (netConnect) cdKeyState_t.CDKEY_OK else cdKeyState_t.CDKEY_CHECKING
            } else {
                cdKeyState_t.CDKEY_NA
            }
            if (!netConnect) {
                EmitGameAuth()
            }
            SetCDKeyGuiVars()
            return true
        }

        /*
         ===============
         idSessionLocal::CDKeysAreValid
         checking that the key is present and uses only valid characters
         if d3xp is installed, check for a valid xpkey as well
         emit an auth packet to the master if possible and needed
         ===============
         */
        override fun CDKeysAreValid(strict: Boolean): Boolean {
            var i: Int
            var emitAuth = false
            if (cdkey_state == cdKeyState_t.CDKEY_UNKNOWN) {
                // FIX: C++ uses `strlen(cdkey)` which counts chars until null terminator.
                // cdkey.size always returns CDKEY_BUF_LEN (17), making the check always fail.
                if (TempDump.ctos(cdkey).length != CDKEY_BUF_LEN - 1) {
                    cdkey_state = cdKeyState_t.CDKEY_INVALID
                } else {
                    i = 0
                    while (i < CDKEY_BUF_LEN - 1) {
                        if (-1 == CDKEY_DIGITS.indexOf(cdkey[i])) {
                            cdkey_state = cdKeyState_t.CDKEY_INVALID
                            break
                        }
                        i++
                    }
                }
                if (cdkey_state == cdKeyState_t.CDKEY_UNKNOWN) {
                    cdkey_state = cdKeyState_t.CDKEY_CHECKING
                    emitAuth = true
                }
            }
            if (xpkey_state == cdKeyState_t.CDKEY_UNKNOWN) {
                if (FileSystem_h.fileSystem.HasD3XP()) {
                    if (TempDump.ctos(xpkey).length != CDKEY_BUF_LEN - 1) {
                        xpkey_state = cdKeyState_t.CDKEY_INVALID
                    } else {
                        i = 0
                        while (i < CDKEY_BUF_LEN - 1) {
                            if (-1 == CDKEY_DIGITS.indexOf(xpkey[i])) {
                                xpkey_state = cdKeyState_t.CDKEY_INVALID
                            }
                            i++
                        }
                    }
                    if (xpkey_state == cdKeyState_t.CDKEY_UNKNOWN) {
                        xpkey_state = cdKeyState_t.CDKEY_CHECKING
                        emitAuth = true
                    }
                } else {
                    xpkey_state = cdKeyState_t.CDKEY_NA
                }
            }
            if (emitAuth) {
                EmitGameAuth()
            }
            // make sure to keep the mainmenu gui up to date in case we made state changes
            SetCDKeyGuiVars()
            return if (strict) {
                cdkey_state == cdKeyState_t.CDKEY_OK && (xpkey_state == cdKeyState_t.CDKEY_OK || xpkey_state == cdKeyState_t.CDKEY_NA)
            } else {
                (cdkey_state == cdKeyState_t.CDKEY_OK || cdkey_state == cdKeyState_t.CDKEY_CHECKING) && (xpkey_state == cdKeyState_t.CDKEY_OK || xpkey_state == cdKeyState_t.CDKEY_CHECKING || xpkey_state == cdKeyState_t.CDKEY_NA)
            }
        }

        override fun ClearCDKey(valid: BooleanArray) {
            if (!valid[0]) {
//		memset( cdkey, 0, CDKEY_BUF_LEN );
                // FIX: C++ memset fills with 0 (null char), not '0' (digit character)
                Arrays.fill(cdkey, '\u0000')
                cdkey_state = cdKeyState_t.CDKEY_UNKNOWN
            } else if (cdkey_state == cdKeyState_t.CDKEY_CHECKING) {
                // if a key was in checking and not explicitely asked for clearing, put it back to ok
                cdkey_state = cdKeyState_t.CDKEY_OK
            }
            if (!valid[1]) {
//		memset( xpkey, 0, CDKEY_BUF_LEN );
                // FIX: C++ clears `xpkey`, not `cdkey`; also fills with 0 not '0'
                Arrays.fill(xpkey, '\u0000')
                xpkey_state = cdKeyState_t.CDKEY_UNKNOWN
            } else if (xpkey_state == cdKeyState_t.CDKEY_CHECKING) {
                xpkey_state = cdKeyState_t.CDKEY_OK
            }
            WriteCDKey()
        }

        // loads a map and starts a new game on it
        override fun SetCDKeyGuiVars() {
            if (guiMainMenu == null) {
                return
            }
            guiMainMenu!!.SetStateString(
                "str_d3key_state",
                Common.common.GetLanguageDict().GetString(Str.va("#str_071%d", 86 + cdkey_state.ordinal))
            )
            guiMainMenu!!.SetStateString(
                "str_xpkey_state",
                Common.common.GetLanguageDict().GetString(Str.va("#str_071%d", 86 + xpkey_state.ordinal))
            )
        }

        //        public void PlayIntroGui();
        //
        //
        //        public void LoadSession(final String name);
        //
        //        public void SaveSession(final String name);
        //
        override fun WaitingForGameAuth(): Boolean {
            return authEmitTimeout != 0
        }

        override fun CDKeysAuthReply(valid: Boolean, auth_msg: String?) {
            assert(authEmitTimeout > 0)
            if (authWaitBox) {
                // close the wait box
                StopBox()
                authWaitBox = false
            }
            if (!valid) {
                Common.common.DPrintf("auth key is invalid\n")
                authMsg = idStr(auth_msg!!)
                if (cdkey_state == cdKeyState_t.CDKEY_CHECKING) {
                    cdkey_state = cdKeyState_t.CDKEY_INVALID
                }
                if (xpkey_state == cdKeyState_t.CDKEY_CHECKING) {
                    xpkey_state = cdKeyState_t.CDKEY_INVALID
                }
            } else {
                Common.common.DPrintf("client is authed in\n")
                if (cdkey_state == cdKeyState_t.CDKEY_CHECKING) {
                    cdkey_state = cdKeyState_t.CDKEY_OK
                }
                if (xpkey_state == cdKeyState_t.CDKEY_CHECKING) {
                    xpkey_state = cdKeyState_t.CDKEY_OK
                }
            }
            authEmitTimeout = 0
            SetCDKeyGuiVars()
        }

        override fun GetCurrentMapName(): String {
            return currentMapName.toString()
        }

        override fun GetSaveGameVersion(): Int {
            return savegameVersion
        }

        fun GetLocalClientNum(): Int {
            return if (idAsyncNetwork.client.IsActive()) {
                idAsyncNetwork.client.GetLocalClientNum()
            } else if (idAsyncNetwork.server.IsActive()) {
                if (idAsyncNetwork.serverDedicated.GetInteger() == 0) {
                    0
                } else if (idAsyncNetwork.server.IsClientInGame(idAsyncNetwork.serverDrawClient.GetInteger())) {
                    idAsyncNetwork.serverDrawClient.GetInteger()
                } else {
                    -1
                }
            } else {
                0
            }
        }

        /*
         ===============
         idSessionLocal::MoveToNewMap

         Leaves the existing userinfo and serverinfo
         ===============
         */
        fun MoveToNewMap(mapName: String) {
            mapSpawnData.serverInfo.Set("si_map", mapName)
            ExecuteMapChange()
            if (!mapSpawnData.serverInfo.GetBool("devmap")) {
                // Autosave at the beginning of the level
                SaveGame(GetAutoSaveName(mapName), true)
            }
            SetGUI(null, null)
        }


        fun StartNewGame(mapName: String, devmap: Boolean = false /*= false*/) {
            if (ID_DEDICATED) {
                Common.common.Printf("Dedicated servers cannot start singleplayer games.\n")
                return
            } else {
                if (ID_ENFORCE_KEY) {
                    // strict check. don't let a game start without a definitive answer
                    if (!CDKeysAreValid(true)) {
                        var prompt = true
                        if (MaybeWaitOnCDKey()) {
                            // check again, maybe we just needed more time
                            if (CDKeysAreValid(true)) {
                                // can continue directly
                                prompt = false
                            }
                        }
                        if (prompt) {
                            cmdSystem.BufferCommandText(cmdExecution_t.CMD_EXEC_NOW, "promptKey force")
                            cmdSystem.ExecuteCommandBuffer()
                        }
                    }
                }
                if (idAsyncNetwork.server.IsActive()) {
                    Common.common.Printf("Server running, use si_map / serverMapRestart\n")
                    return
                }
                if (idAsyncNetwork.client.IsActive()) {
                    Common.common.Printf("Client running, disconnect from server first\n")
                    return
                }

                // clear the userInfo so the player starts out with the defaults
                mapSpawnData.userInfo[0].Clear()
                mapSpawnData.persistentPlayerInfo[0].Clear()
                mapSpawnData.userInfo[0].set(cvarSystem.MoveCVarsToDict(CVarSystem.CVAR_USERINFO))
                mapSpawnData.serverInfo.Clear()
                mapSpawnData.serverInfo.set(cvarSystem.MoveCVarsToDict(CVarSystem.CVAR_SERVERINFO))
                mapSpawnData.serverInfo.Set("si_gameType", "singleplayer")

                // set the devmap key so any play testing items will be given at
                // spawn time to set approximately the right weapons and ammo
                if (devmap) {
                    mapSpawnData.serverInfo.Set("devmap", "1")
                }
                mapSpawnData.syncedCVars.Clear()
                mapSpawnData.syncedCVars.set(cvarSystem.MoveCVarsToDict(CVarSystem.CVAR_NETWORKSYNC))
                MoveToNewMap(mapName)
            }
        }

        /*
         ===============
         idSessionLocal::DrawWipeModel

         Draw the fade material over everything that has been drawn
         ===============
         */
        // called by Draw when the scene to scene wipe is still running
        fun DrawWipeModel() {
            val latchedTic = Common.com_ticNumber
            if (wipeStartTic >= wipeStopTic) {
                return
            }
            if (!wipeHold && latchedTic >= wipeStopTic) {
                return
            }
            val fade = (latchedTic - wipeStartTic) / (wipeStopTic - wipeStartTic)
            RenderSystem.renderSystem.SetColor4(1.0f, 1.0f, 1.0f, fade.toFloat())
            RenderSystem.renderSystem.DrawStretchPic(0.0f, 0.0f, 640.0f, 480.0f, 0.0f, 0.0f, 1.0f, 1.0f, wipeMaterial)
        }

        /*
         ================
         idSessionLocal::StartWipe

         Draws and captures the current state, then starts a wipe with that image
         ================
         */

        fun StartWipe(_wipeMaterial: String, hold: Boolean = false /*= false*/) {
            Console.console.Close()

            // render the current screen into a texture for the wipe model
            RenderSystem.renderSystem.CropRenderSize(640, 480, true)
            Draw()
            RenderSystem.renderSystem.CaptureRenderToImage("_scratch")
            RenderSystem.renderSystem.UnCrop()
            wipeMaterial = DeclManager.declManager.FindMaterial(_wipeMaterial, false)
            wipeStartTic = Common.com_ticNumber
            wipeStopTic = (wipeStartTic + 1000.0f / UsercmdGen.USERCMD_MSEC * com_wipeSeconds.GetFloat()).toInt()
            wipeHold = hold
        }

        fun CompleteWipe() {
            if (Common.com_ticNumber == 0) {
                // if the async thread hasn't started, we would hang here
                wipeStopTic = 0
                UpdateScreen(true)
                return
            }
            while (Common.com_ticNumber < wipeStopTic) {
                if (ID_CONSOLE_LOCK) {
                    emptyDrawCount = 0
                }
                Common.common.Async()
                UpdateScreen(true)
            }
        }

        fun ClearWipe() {
            wipeHold = false
            wipeStopTic = 0
            wipeStartTic = wipeStopTic + 1
        }

        fun ShowLoadingGui() {
            if (Common.com_ticNumber == 0) {
                return
            }
            Console.console.Close()

            // introduced in D3XP code. don't think it actually fixes anything, but doesn't hurt either
            if (true) {
                // Try and prevent the while loop from being skipped over (long hitch on the main thread?)
                val stop = win_shared.Sys_Milliseconds() + 1000
                var force = 10
                while (win_shared.Sys_Milliseconds() < stop || force-- > 0) {
                    Common.com_frameTime = Common.com_ticNumber * UsercmdGen.USERCMD_MSEC
                    Session.session.Frame()
                    Session.session.UpdateScreen(false)
                }
            } else {
                val stop = (Common.com_ticNumber + 1000.0f / UsercmdGen.USERCMD_MSEC * 1.0f).toInt()
                while (Common.com_ticNumber < stop) {
                    Common.com_frameTime = Common.com_ticNumber * UsercmdGen.USERCMD_MSEC
                    Session.session.Frame()
                    Session.session.UpdateScreen(false)
                }
            }
        }

        /*
         ===============
         idSessionLocal::ScrubSaveGameFileName

         Turns a bad file name into a good one or your money back
         ===============
         */
        fun ScrubSaveGameFileName(saveFileName: idStr) {
            var i: Int
            val inFileName: idStr
            inFileName = idStr(saveFileName)
            inFileName.RemoveColors()
            inFileName.StripFileExtension()
            saveFileName.Clear()
            val len = inFileName.Length()
            i = 0
            while (i < len) {
                if ("',.~!@#$%^&*()[]{}<>\\|/=?+;:-'\"".indexOf(inFileName[i]) > -1) {
                    // random junk
                    saveFileName.Append('_')
                } else if (inFileName[i].code >= 128) {
                    // high ascii chars
                    saveFileName.Append('_')
                } else if (inFileName[i] == ' ') {
                    saveFileName.Append('_')
                } else {
                    saveFileName.Append(inFileName[i])
                }
                i++
            }
        }

        @Throws(idException::class)
        fun GetAutoSaveName(mapName: String): String {
            var mapName = mapName
            val mapDecl = DeclManager.declManager.FindType(declType_t.DECL_MAPDEF, mapName, false)
            val mapDef = mapDecl as idDeclEntityDef?
            if (mapDef != null) {
                mapName = Common.common.GetLanguageDict().GetString(mapDef.dict.GetString("name", mapName))
            }
            // Fixme: Localization
            return Str.va("^3AutoSave:^0 %s", mapName)
        }

        @Throws(idException::class)
        fun LoadGame(saveName: String): Boolean {
            val saveFilePath: idStr = idStr()
            val loadFile: idStr = idStr()
            val saveMap = idStr()
            val gamename = idStr()

            if (IsMultiplayer()) {
                Common.common.Printf("Can't load during net play.\n")
                return false
            }

            //Hide the dialog box if it is up.
            StopBox()

            loadFile.set(saveName)
            ScrubSaveGameFileName(loadFile)
            loadFile.SetFileExtension(".save")

            saveFilePath.set("savegames/")
            saveFilePath.Append(loadFile)

            // Open savegame file
            // only allow loads from the game directory because we don't want a base game to load
            val game = idStr(cvarSystem.GetCVarString("fs_game"))
            savegameFile = FileSystem_h.fileSystem.OpenFileRead(
                saveFilePath.toString(),
                true,
                if (game.Length() != 0) game.toString() else null
            )

            if (savegameFile == null) {
                Common.common.Warning("Couldn't open savegame file %s", saveFilePath.toString())
                return false
            }

            loadingSaveGame = true

            // Read in save game header
            // Game Name / Version / Map Name / Persistant Player Info

            // game
            savegameFile?.ReadString(gamename)

            // if this isn't a savegame for the correct game, abort loadgame
            if (!(gamename.toString() == GAME_NAME || gamename.toString() == "DOOM 3")) {
                Common.common.Warning("Attempted to load an invalid savegame: %s", saveFilePath.toString())

                loadingSaveGame = false
                FileSystem_h.fileSystem.CloseFile(savegameFile!!)
                savegameFile = null
                return false
            }

            // version
            val readVersion = CInt()
            savegameFile!!.ReadInt(readVersion)
            savegameVersion = readVersion.integerValue

            // map
            savegameFile!!.ReadString(saveMap)

            // persistent player info
            for (i in 0 until AsyncNetwork.MAX_ASYNC_CLIENTS) {
                mapSpawnData.persistentPlayerInfo[i].ReadFromFileHandle(savegameFile!!)
            }

            // check the version, if it doesn't match, cancel the loadgame,
            // but still load the map with the persistant playerInfo from the header
            // so that the player doesn't lose too much progress.
            if (savegameVersion < 16 || savegameVersion > SAVEGAME_VERSION) { // dhewm3 supports savegames with v16 - v18
                Common.common.Warning("Savegame Version mismatch: aborting loadgame and starting level with persistent data")
                loadingSaveGame = false
                FileSystem_h.fileSystem.CloseFile(savegameFile!!)
                savegameFile = null
            }

            Common.common.DPrintf("loading a v%d savegame\n", savegameVersion)
            if (saveMap.Length() > 0) {

                // Start loading map
                mapSpawnData.serverInfo.Clear()

                mapSpawnData.serverInfo.set(cvarSystem.MoveCVarsToDict(CVarSystem.CVAR_SERVERINFO))
                mapSpawnData.serverInfo.Set("si_gameType", "singleplayer")

                mapSpawnData.serverInfo.Set("si_map", saveMap.toString())

                mapSpawnData.syncedCVars.Clear()
                mapSpawnData.syncedCVars.set(cvarSystem.MoveCVarsToDict(CVarSystem.CVAR_NETWORKSYNC))

                mapSpawnData.mapSpawnUsercmd[0] = UsercmdGen.usercmdGen.TicCmd(latchedTicNumber)
                // make sure no buttons are pressed
                mapSpawnData.mapSpawnUsercmd[0].buttons = 0

                ExecuteMapChange()

                SetGUI(null, null)
            }

            if (loadingSaveGame) {
                FileSystem_h.fileSystem.CloseFile(savegameFile!!)
                loadingSaveGame = false
                savegameFile = null
            }

            return true
        }

        fun QuickLoad(): Boolean {
            var saveName = Common.common.GetLanguageDict().GetString("#str_07178")

            val saveFilePathBase = idStr(saveName)
            ScrubSaveGameFileName(saveFilePathBase)
            saveFilePathBase.set("savegames/" + saveFilePathBase)

            var game: String? = cvarSystem.GetCVarString("fs_game")
            // FIX: C++ checks `game != NULL && game[0] == '\0'` meaning "if game is not null AND is empty".
            // Kotlin had `isNotEmpty()` which is the opposite.
            if (game != null && game.isEmpty()) {
                game = null
            }

            // find the newest QuickSave (or QuickSave2, QuickSave3, ...)
            val maxNum = com_numQuicksaves.GetInteger()
            var indexToUse = 1
            var newestTime = 0L
            for (i in 1..maxNum) {
                val saveFilePath = idStr(saveFilePathBase)
                if (i > 1) {
                    // the first one is just called "QuickSave" without a number, like before.
                    // the others are called "QuickSave2" "QuickSave3" etc
                    saveFilePath.Append(i.toString())
                }
                saveFilePath.SetFileExtension(".save")

                val f = FileSystem_h.fileSystem.OpenFileRead(saveFilePath.toString(), true, game)
                if (f != null) {
                    val ts = f.Timestamp()
                    assert(ts != 0L)
                    if (ts > newestTime) {
                        indexToUse = i
                        newestTime = ts
                    }
                }
            }

            if (indexToUse > 1) {
                saveName += indexToUse.toString()
            }

            return Session.sessLocal.LoadGame(saveName)
        }


        @Throws(idException::class)
        fun SaveGame(saveName: String, autosave: Boolean = false /*= false*/): Boolean {
//            return false
            val previewFile = idStr()
            val descriptionFile = idStr()
            val mapName = idStr()
            // DG: support setting an explicit savename to avoid problems with autosave names
            val gameFile = idStr(saveName)
            if (!mapSpawned) {
                Common.common.Printf("Not playing a game.\n")
                return false
            }

            if (IsMultiplayer()) {
                Common.common.Printf("Can't save during net play.\n")
                return false
            }

            if (Game_local.game.GetPersistentPlayerInfo(0).GetInt("health") <= 0) {
                MessageBox(
                    msgBoxType_t.MSG_OK,
                    Common.common.GetLanguageDict().GetString("#str_04311"),
                    Common.common.GetLanguageDict().GetString("#str_04312"),
                    true
                )
                Common.common.Printf("You must be alive to save the game\n")
                return false
            }

            if (Sys_GetDriveFreeSpace(CVarSystem.cvarSystem.GetCVarString("fs_savepath")) < 25) {
                MessageBox(
                    msgBoxType_t.MSG_OK,
                    Common.common.GetLanguageDict().GetString("#str_04313"),
                    Common.common.GetLanguageDict().GetString("#str_04314"),
                    true
                )
                Common.common.Printf("Not enough drive space to save the game\n")
                return false
            }

            val pauseWorld = snd_system.soundSystem.GetPlayingSoundWorld()
            if (pauseWorld != null) {
                pauseWorld.Pause()
                snd_system.soundSystem.SetPlayingSoundWorld(null)
            }

            // setup up filenames and paths
            ScrubSaveGameFileName(gameFile)

            gameFile.set("savegames/" + gameFile)
            gameFile.SetFileExtension(".save")

            previewFile.set(gameFile)
            previewFile.SetFileExtension(".tga")

            descriptionFile.set(gameFile)
            descriptionFile.SetFileExtension(".txt")

            // Open savegame file
            val fileOut = FileSystem_h.fileSystem.OpenFileWrite(gameFile.toString())
            if (fileOut == null) {
                Common.common.Warning("Failed to open save file '%s'\n", gameFile.toString())
                if (pauseWorld != null) {
                    snd_system.soundSystem.SetPlayingSoundWorld(pauseWorld)
                    pauseWorld.UnPause()
                }
                return false
            }

            // Write SaveGame Header:
            // Game Name / Version / Map Name / Persistant Player Info

            // game
            val gamename = GAME_NAME
            fileOut.WriteString(gamename)

            // version
            fileOut.WriteInt(SAVEGAME_VERSION)

            // map
            mapName.set(mapSpawnData.serverInfo.GetString("si_map"))
            fileOut.WriteString(mapName.toString())

            // persistent player info
            for (i in 0 until AsyncNetwork.MAX_ASYNC_CLIENTS) {
                mapSpawnData.persistentPlayerInfo[i] = Game_local.game.GetPersistentPlayerInfo(i)
                mapSpawnData.persistentPlayerInfo[i].WriteToFileHandle(fileOut)
            }

            // let the game save its state
            Game_local.game.SaveGame(fileOut)

            // close the sava game file
            FileSystem_h.fileSystem.CloseFile(fileOut)

            // Write screenshot
            if (!autosave) {
                RenderSystem.renderSystem.CropRenderSize(320, 240, false)
                Game_local.game.Draw(0)
                RenderSystem.renderSystem.CaptureRenderToFile(previewFile.toString(), true)
                RenderSystem.renderSystem.UnCrop()
            }

            // Write description, which is just a text file with
            // the unclean save name on line 1, map name on line 2, screenshot on line 3
            val fileDesc = FileSystem_h.fileSystem.OpenFileWrite(descriptionFile.toString())
            if (fileDesc == null) {
                Common.common.Warning("Failed to open description file '%s'\n", descriptionFile.toString())
                if (pauseWorld != null) {
                    snd_system.soundSystem.SetPlayingSoundWorld(pauseWorld)
                    pauseWorld.UnPause()
                }
                return false
            }

            val description = idStr(saveName)
            description.Replace("\\", "\\\\")
            description.Replace("\"", "\\\"")

            val mapDef =
                DeclManager.declManager.FindType(declType_t.DECL_MAPDEF, mapName.toString(), false) as idDeclEntityDef?
            if (mapDef != null) {
                mapName.set(
                    Common.common.GetLanguageDict().GetString(mapDef.dict.GetString("name", mapName.toString()))
                )
            }

            fileDesc.Printf("\"%s\"\n", description.toString())
            fileDesc.Printf("\"%s\"\n", mapName.toString())

            if (autosave) {
                val sshot = idStr(mapSpawnData.serverInfo.GetString("si_map"))
                sshot.StripPath()
                sshot.StripFileExtension()
                fileDesc.Printf("\"guis/assets/autosave/%s\"\n", sshot.toString())
            } else {
                fileDesc.Printf("\"\"\n")
            }

            FileSystem_h.fileSystem.CloseFile(fileDesc)

            if (pauseWorld != null) {
                snd_system.soundSystem.SetPlayingSoundWorld(pauseWorld)
                pauseWorld.UnPause()
            }

            syncNextGameFrame = true

            return true
        }

        fun QuickSave(): Boolean {
            var saveName = Common.common.GetLanguageDict().GetString("#str_07178")

            val saveFilePathBase = idStr(saveName)
            ScrubSaveGameFileName(saveFilePathBase)
            saveFilePathBase.set("savegames/" + saveFilePathBase)

            var game: String? = cvarSystem.GetCVarString("fs_game")
            // FIX: Same as QuickLoad — C++ checks if game string is empty to set null
            if (game != null && game.isEmpty()) {
                game = null
            }

            val maxNum = com_numQuicksaves.GetInteger()
            var indexToUse = 1
            var oldestTime = 0L
            for (i in 1..maxNum) {
                val saveFilePath = idStr(saveFilePathBase)
                if (i > 1) {
                    // the first one is just called "QuickSave" without a number, like before.
                    // the others are called "QuickSave2" "QuickSave3" etc
                    saveFilePath.Append(i.toString())
                }
                saveFilePath.SetFileExtension(".save")

                val f = FileSystem_h.fileSystem.OpenFileRead(saveFilePath.toString(), true, game)
                if (f == null) {
                    // this savegame doesn't exist yet => we can use this index for the name
                    indexToUse = i
                    break
                } else {
                    val ts = f.Timestamp()
                    assert(ts != 0L)
                    if (ts < oldestTime || oldestTime == 0L) {
                        // this is the oldest quicksave we found so far => a candidate to be overwritten
                        indexToUse = i
                        oldestTime = ts
                    }
                }
            }

            if (indexToUse > 1) {
                saveName += indexToUse.toString()
            }

            if (SaveGame(saveName)) {
                Common.common.Printf("%s\n", saveName)
                return true
            }
            return false
        }

        fun GetAuthMsg(): String {
            return authMsg.toString()
        }

        fun Clear() {
            insideUpdateScreen = false
            insideExecuteMapChange = false
            loadingSaveGame = false
            savegameFile = null
            savegameVersion = 0
            currentMapName.Clear()
            aviDemoShortName.Clear()
            msgFireBack[0].Clear()
            msgFireBack[1].Clear()
            timeHitch = 0
            rw = RenderSystem.renderSystem.AllocRenderWorld()
            sw = snd_system.soundSystem.AllocSoundWorld(rw)
            menuSoundWorld = null
            readDemo = null
            writeDemo = null
            renderdemoVersion = 0
            cmdDemoFile = null
            syncNextGameFrame = false
            mapSpawned = false
            guiActive = null
            aviCaptureMode = false
            timeDemo = timeDemo_t.TD_NO
            waitingOnBind = false
            lastPacifierTime = 0
            msgRunning = false
            guiMsgRestore = null
            msgIgnoreButtons = false
            bytesNeededForMapLoad = 0
            if (ID_CONSOLE_LOCK) {
                emptyDrawCount = 0
            }
            ClearWipe()
            loadGameList.clear()
            modsList.clear()
            authEmitTimeout = 0
            authWaitBox = false
            authMsg.Clear()
        }

        /*
         ===============
         idSessionLocal::DrawCmdGraph

         Graphs yaw angle for testing smoothness
         ===============
         */
        @Throws(idException::class)
        fun DrawCmdGraph() {
            if (!com_showAngles.GetBool()) {
                return
            }
            RenderSystem.renderSystem.SetColor4(0.1f, 0.1f, 0.1f, 1.0f)
            RenderSystem.renderSystem.DrawStretchPic(
                0.0f,
                (480 - ANGLE_GRAPH_HEIGHT).toFloat(),
                (UsercmdGen.MAX_BUFFERED_USERCMD * ANGLE_GRAPH_STRETCH).toFloat(),
                ANGLE_GRAPH_HEIGHT.toFloat(),
                0.0f,
                0.0f,
                1.0f,
                1.0f,
                whiteMaterial
            )
            RenderSystem.renderSystem.SetColor4(0.9f, 0.9f, 0.9f, 1.0f)
            for (i in 0 until UsercmdGen.MAX_BUFFERED_USERCMD - 4) {
                val cmd = UsercmdGen.usercmdGen.TicCmd(latchedTicNumber - (UsercmdGen.MAX_BUFFERED_USERCMD - 4) + i)
                var h: Int = cmd.angles[1].toInt()
                h = h shr 8
                h = h and ANGLE_GRAPH_HEIGHT - 1
                RenderSystem.renderSystem.DrawStretchPic(
                    (i * ANGLE_GRAPH_STRETCH).toFloat(),
                    (480 - h).toFloat(),
                    1.0f,
                    h.toFloat(),
                    0.0f,
                    0.0f,
                    1.0f,
                    1.0f,
                    whiteMaterial
                )
            }
        }

        @Throws(idException::class)
        fun Draw() {
            var fullConsole = false
            if (insideExecuteMapChange) {
                if (guiLoading != null) {
                    guiLoading!!.Redraw(Common.com_frameTime)
                }
                if (guiActive == guiMsg) {
                    guiMsg!!.Redraw(Common.com_frameTime)
                }
            } else if (guiTest != null) {
                // if testing a gui, clear the screen and draw it
                // clear the background, in case the tested gui is transparent
                // NOTE that you can't use this for aviGame recording, it will tick at real com_frameTime between screenshots..
                RenderSystem.renderSystem.SetColor(colorBlack)
                RenderSystem.renderSystem.DrawStretchPic(
                    0.0f,
                    0.0f,
                    640.0f,
                    480.0f,
                    0.0f,
                    0.0f,
                    1.0f,
                    1.0f,
                    DeclManager.declManager.FindMaterial("_white")
                )
                guiTest!!.Redraw(Common.com_frameTime)
            } else if (guiActive != null && !guiActive!!.State().GetBool("gameDraw")) {

                // draw the frozen gui in the background
                if (guiActive == guiMsg && guiMsgRestore != null) {
                    guiMsgRestore!!.Redraw(Common.com_frameTime)
                }

                // draw the menus full screen
                if (guiActive == guiTakeNotes && !com_skipGameDraw.GetBool()) {
                    Game_local.game.Draw(GetLocalClientNum())
                }
                guiActive!!.Redraw(Common.com_frameTime)
            } else if (readDemo != null) {
                rw.RenderScene(currentDemoRenderView!!)
                RenderSystem.renderSystem.DrawDemoPics()
            } else if (mapSpawned) {
                var gameDraw = false
                // normal drawing for both single and multi player
                if (!com_skipGameDraw.GetBool() && GetLocalClientNum() >= 0) {
                    // draw the game view
                    val start = win_shared.Sys_Milliseconds()
                    gameDraw = Game_local.game.Draw(GetLocalClientNum())
                    val end = win_shared.Sys_Milliseconds()
                    Common.time_gameDraw += end - start // note time used for com_speeds
                }
                if (!gameDraw) {
                    RenderSystem.renderSystem.SetColor(colorBlack)
                    RenderSystem.renderSystem.DrawStretchPic(
                        0.0f,
                        0.0f,
                        640.0f,
                        480.0f,
                        0.0f,
                        0.0f,
                        1.0f,
                        1.0f,
                        DeclManager.declManager.FindMaterial("_white")
                    )
                }

                // save off the 2D drawing from the game
                if (writeDemo != null) {
                    RenderSystem.renderSystem.WriteDemoPics()
                }
            } else {
                if (ID_CONSOLE_LOCK) {
                    if (Common.com_allowConsole.GetBool()) {
                        Console.console.Draw(true)
                    } else {
                        emptyDrawCount++
                        if (emptyDrawCount > 5) {
                            // it's best if you can avoid triggering the watchgod by doing the right thing somewhere else
                            assert(false)
                            Common.common.Warning("idSession: triggering mainmenu watchdog")
                            emptyDrawCount = 0
                            StartMenu()
                        }
                        RenderSystem.renderSystem.SetColor4(0.0f, 0.0f, 0.0f, 1.0f)
                        RenderSystem.renderSystem.DrawStretchPic(
                            0.0f,
                            0.0f,
                            RenderSystem.SCREEN_WIDTH.toFloat(),
                            RenderSystem.SCREEN_HEIGHT.toFloat(),
                            0.0f,
                            0.0f,
                            1.0f,
                            1.0f,
                            DeclManager.declManager.FindMaterial("_white")
                        )
                    }
                } else {
                    // draw the console full screen - this should only ever happen in developer builds
                    Console.console.Draw(true)
                }
                fullConsole = true
            }
            if (ID_CONSOLE_LOCK) {
                if (!fullConsole && emptyDrawCount != 0) {
                    Common.common.DPrintf("idSession: %d empty frame draws\n", emptyDrawCount)
                    emptyDrawCount = 0
                }
                fullConsole = false
            }

            // draw the wipe material on top of this if it hasn't completed yet
            DrawWipeModel()

            // draw debug graphs
            DrawCmdGraph()

            // draw the half console / notify console on top of everything
            if (!fullConsole) {
                Console.console.Draw(false)
            }
        }

        /*
         ==============
         idSessionLocal::WriteCmdDemo

         Dumps the accumulated commands for the current level.
         This should still work after disconnecting from a level
         ==============
         */

        @Throws(idException::class)
        fun WriteCmdDemo(demoName: String, save: Boolean = false /*= false*/) {
            if (demoName.isEmpty()) {
                Common.common.Printf("idSessionLocal::WriteCmdDemo: no name specified\n")
                return
            }
            var statsName = idStr()
            if (save) {
                statsName = idStr(demoName)
                statsName.StripFileExtension()
                statsName.DefaultFileExtension(".stats")
            }
            Common.common.Printf("writing save data to %s\n", demoName)
            val cmdDemoFile = FileSystem_h.fileSystem.OpenFileWrite(demoName)
            if (null == cmdDemoFile) {
                Common.common.Printf("Couldn't open for writing %s\n", demoName)
                return
            }
            if (save) {
                cmdDemoFile.WriteInt(logIndex) //cmdDemoFile->Write( &logIndex, sizeof( logIndex ) );//TODO
            }
            SaveCmdDemoToFile(cmdDemoFile)
            if (save) {
                val statsFile = FileSystem_h.fileSystem.OpenFileWrite(statsName.toString())
                if (statsFile != null) {
                    statsFile.WriteInt(statIndex) //statsFile->Write( &statIndex, sizeof( statIndex ) );//TODO
                    for (i in 0 until numClients * statIndex) {
                        statsFile.Write(loggedStats[i].Write())
                    }
                    FileSystem_h.fileSystem.CloseFile(statsFile)
                }
            }
            FileSystem_h.fileSystem.CloseFile(cmdDemoFile)
        }

        @Throws(idException::class)
        fun StartPlayingCmdDemo(demoName: String) {
            // exit any current game
            Stop()
            val fullDemoName = idStr("demos/")
            fullDemoName.Append(demoName)
            fullDemoName.DefaultFileExtension(".cdemo")
            cmdDemoFile = FileSystem_h.fileSystem.OpenFileRead(fullDemoName.toString())
            if (cmdDemoFile == null) {
                Common.common.Printf("Couldn't open %s\n", fullDemoName.toString())
                return
            }
            guiLoading = UserInterface.uiManager.FindGui("guis/map/loading.gui", true, false, true)
            //cmdDemoFile.Read(&loadGameTime, sizeof(loadGameTime));
            LoadCmdDemoFromFile(cmdDemoFile!!)

            // start the map
            ExecuteMapChange()
            cmdDemoFile = FileSystem_h.fileSystem.OpenFileRead(fullDemoName.toString())

            // have to do this twice as the execmapchange clears the cmddemofile
            LoadCmdDemoFromFile(cmdDemoFile!!)

            // run one frame to get the view angles correct
            RunGameTic()
        }

        @Throws(idException::class)
        fun TimeCmdDemo(demoName: String) {
            StartPlayingCmdDemo(demoName)
            ClearWipe()
            UpdateScreen()
            val startTime = win_shared.Sys_Milliseconds()
            var count = 0
            var minuteStart: Int
            var minuteEnd: Int
            var sec: Float

            // run all the frames in sequence
            minuteStart = startTime
            while (cmdDemoFile != null) {
                RunGameTic()
                count++
                if (count / 3600 != (count - 1) / 3600) {
                    minuteEnd = win_shared.Sys_Milliseconds()
                    sec = ((minuteEnd - minuteStart) / 1000.0f).toFloat() //divide by double and roundup to float
                    minuteStart = minuteEnd
                    Common.common.Printf("minute %d took %3.1.0f seconds\n", count / 3600, sec)
                    UpdateScreen()
                }
            }
            val endTime = win_shared.Sys_Milliseconds()
            sec = ((endTime - startTime) / 1000.0f).toFloat()
            Common.common.Printf("%d seconds of game, replayed in %5.1.0f seconds\n", count / 60, sec)
        }

        @Throws(idException::class)
        fun SaveCmdDemoToFile(file: idFile) {
            mapSpawnData.serverInfo.WriteToFileHandle(file)
            for (i in 0 until AsyncNetwork.MAX_ASYNC_CLIENTS) {
                mapSpawnData.userInfo[i].WriteToFileHandle(file)
                mapSpawnData.persistentPlayerInfo[i].WriteToFileHandle(file)
            }
            for (t in mapSpawnData.mapSpawnUsercmd) {
                file.Write(t.Write() /*, sizeof( mapSpawnData.mapSpawnUsercmd )*/)
            }
            if (numClients < 1) {
                numClients = 1
            }
            for (i in 0 until numClients * logIndex) {
                file.Write(loggedUsercmds[i].Write() /* sizeof(loggedUsercmds[0])*/)
            }
        }

        @Throws(idException::class)
        fun LoadCmdDemoFromFile(file: idFile) {
            mapSpawnData.serverInfo.ReadFromFileHandle(file)
            for (i in 0 until AsyncNetwork.MAX_ASYNC_CLIENTS) {
                mapSpawnData.userInfo[i].ReadFromFileHandle(file)
                mapSpawnData.persistentPlayerInfo[i].ReadFromFileHandle(file)
            }
            for (t in mapSpawnData.mapSpawnUsercmd) {
                file.Read(t.Write() /*, sizeof( mapSpawnData.mapSpawnUsercmd )*/)
            }
        }

        fun StartRecordingRenderDemo(demoName: String) {
            if (writeDemo != null) {
                // allow it to act like a toggle
                StopRecordingRenderDemo()
                return
            }
            // FIX: C++ `!demoName[0]` means "is empty". Kotlin had `isNotEmpty()` — inverted.
            if (demoName.isEmpty()) {
                Common.common.Printf("idSessionLocal::StartRecordingRenderDemo: no name specified\n")
                return
            }
            Console.console.Close()
            writeDemo = idDemoFile()
            if (!writeDemo!!.OpenForWriting(demoName)) {
                Common.common.Printf("error opening %s\n", demoName)
                //		delete writeDemo;
                writeDemo = null
                return
            }
            Common.common.Printf("recording to %s\n", writeDemo!!.GetName())
            writeDemo!!.WriteInt(demoSystem_t.DS_VERSION.ordinal)
            writeDemo!!.WriteInt(Licensee.RENDERDEMO_VERSION)

            // if we are in a map already, dump the current state
            sw.StartWritingDemo(writeDemo!!)
            rw.StartWritingDemo(writeDemo)
        }

        fun StopRecordingRenderDemo() {
            if (null == writeDemo) {
                Common.common.Printf("idSessionLocal::StopRecordingRenderDemo: not recording\n")
                return
            }
            sw.StopWritingDemo()
            rw.StopWritingDemo()
            writeDemo!!.Close()
            Common.common.Printf("stopped recording %s.\n", writeDemo!!.GetName())
            //	delete writeDemo;
            writeDemo = null
        }

        @Throws(idException::class)
        fun StartPlayingRenderDemo(demoName: idStr) {
            // FIX: C++ `!demoName[0]` means "is empty". Kotlin had `isNotEmpty()` — inverted.
            if (demoName != null && demoName.toString().isEmpty()) {
                Common.common.Printf("idSessionLocal::StartPlayingRenderDemo: no name specified\n")
                return
            }

            // make sure localSound / GUI intro music shuts up
            sw.StopAllSounds()
            sw.PlayShaderDirectly("", 0)
            menuSoundWorld!!.StopAllSounds()
            menuSoundWorld!!.PlayShaderDirectly("", 0)

            // exit any current game
            Stop()

            // automatically put the console away
            Console.console.Close()

            // bring up the loading screen manually, since demos won't
            // call ExecuteMapChange()
            guiLoading = UserInterface.uiManager.FindGui("guis/map/loading.gui", true, false, true)
            guiLoading!!.SetStateString("demo", Common.common.GetLanguageDict().GetString("#str_02087"))
            readDemo = idDemoFile()
            demoName.DefaultFileExtension(".demo")
            if (!readDemo!!.OpenForReading(demoName.toString())) {
                Common.common.Printf("couldn't open %s\n", demoName)
                //		delete readDemo;
                readDemo = null
                Stop()
                StartMenu()
                snd_system.soundSystem.SetMute(false)
                return
            }
            insideExecuteMapChange = true
            UpdateScreen()
            insideExecuteMapChange = false
            guiLoading!!.SetStateString("demo", "")

            // setup default render demo settings
            // that's default for <= Doom3 v1.1
            renderdemoVersion = 1
            savegameVersion = 16
            AdvanceRenderDemo(true)
            numDemoFrames = 1
            lastDemoTic = -1
            timeDemoStartTime = win_shared.Sys_Milliseconds()
        }

        @Throws(idException::class)
        fun StartPlayingRenderDemo(demoName: String) {
            StartPlayingRenderDemo(idStr(demoName))
        }

        // FIX: Entire function body was a copy/paste of StopRecordingRenderDemo.
        // Rewritten to match C++ StopPlayingRenderDemo.
        fun StopPlayingRenderDemo() {
            if (readDemo == null) {
                timeDemo = timeDemo_t.TD_NO
                return
            }

            // Record the stop time before doing anything that could be time consuming
            val timeDemoStopTime = win_shared.Sys_Milliseconds()

            EndAVICapture()

            readDemo!!.Close()

            sw.StopAllSounds()
            snd_system.soundSystem.SetPlayingSoundWorld(menuSoundWorld!!)

            Common.common.Printf("stopped playing %s.\n", readDemo!!.GetName())
            readDemo = null

            if (timeDemo != timeDemo_t.TD_NO) {
                // report the stats
                val demoSeconds = (timeDemoStopTime - timeDemoStartTime) * 0.001f
                val demoFPS = numDemoFrames / demoSeconds
                val message = Str.va(
                    "%d frames rendered in %3.1f seconds = %3.1f fps\n",
                    numDemoFrames, demoSeconds, demoFPS
                )

                Common.common.Printf("%s", message)
                if (timeDemo == timeDemo_t.TD_YES_THEN_QUIT) {
                    cmdSystem.BufferCommandText(cmdExecution_t.CMD_EXEC_APPEND, "quit\n")
                } else {
                    snd_system.soundSystem.SetMute(true)
                    MessageBox(msgBoxType_t.MSG_OK, message, "Time Demo Results", true)
                    snd_system.soundSystem.SetMute(false)
                }
                timeDemo = timeDemo_t.TD_NO
            }
        }

        fun CompressDemoFile(scheme: String, demoName: String) {
            val fullDemoName = idStr("demos/")
            fullDemoName.Append(demoName)
            fullDemoName.DefaultFileExtension(".demo")
            fullDemoName.StripFileExtension()
            fullDemoName.Append("_compressed.demo")
            val savedCompression = cvarSystem.GetCVarInteger("com_compressDemos")
            val savedPreload = cvarSystem.GetCVarBool("com_preloadDemos")
            cvarSystem.SetCVarBool("com_preloadDemos", false)
            cvarSystem.SetCVarInteger("com_compressDemos", scheme.toInt())
            val demoread = idDemoFile()
            val demowrite = idDemoFile()
            if (!demoread.OpenForReading(fullDemoName.toString())) {
                Common.common.Printf("Could not open %s for reading\n", fullDemoName.toString())
                return
            }
            if (!demowrite.OpenForWriting(fullDemoName.toString())) {
                Common.common.Printf("Could not open %s for writing\n", fullDemoName.toString())
                demoread.Close()
                cvarSystem.SetCVarBool("com_preloadDemos", savedPreload)
                cvarSystem.SetCVarInteger("com_compressDemos", savedCompression)
                return
            }
            Common.common.SetRefreshOnPrint(true)
            Common.common.Printf("Compressing %s to %s...\n", fullDemoName, fullDemoName)
            val buffer = ByteBuffer.allocate(bufferSize * 2)
            var bytesRead: Int
            while (0 != demoread.Read(buffer).also { bytesRead = it }) {
                demowrite.Write(buffer, bytesRead)
                Common.common.Printf(".")
            }
            demoread.Close()
            demowrite.Close()
            cvarSystem.SetCVarBool("com_preloadDemos", savedPreload)
            cvarSystem.SetCVarInteger("com_compressDemos", savedCompression)
            Common.common.Printf("Done\n")
            Common.common.SetRefreshOnPrint(false)
        }


        @Throws(idException::class)
        fun TimeRenderDemo(demoName: String, twice: Boolean = false /*= false*/) {
            val demo = idStr(demoName)

            // no sound in time demos
            snd_system.soundSystem.SetMute(true)
            StartPlayingRenderDemo(demo)
            if (twice && readDemo != null) {
                // cycle through once to precache everything
                guiLoading!!.SetStateString("demo", Common.common.GetLanguageDict().GetString("#str_04852"))
                guiLoading!!.StateChanged(Common.com_frameTime)
                while (readDemo != null) {
                    insideExecuteMapChange = true
                    UpdateScreen()
                    insideExecuteMapChange = false
                    AdvanceRenderDemo(true)
                }
                guiLoading!!.SetStateString("demo", "")
                StartPlayingRenderDemo(demo)
            }
            if (null == readDemo) {
                return
            }
            timeDemo = timeDemo_t.TD_YES
        }

        fun AVIRenderDemo(_demoName: String) {
            val demoName = idStr(_demoName) // copy off from va() buffer
            StartPlayingRenderDemo(demoName)
            if (null == readDemo) {
                return
            }
            BeginAVICapture(demoName.toString())

            // I don't understand why I need to do this twice, something
            // strange with the nvidia swapbuffers?
            UpdateScreen()
        }

        //
        fun AVICmdDemo(demoName: String) {
            StartPlayingCmdDemo(demoName)
            BeginAVICapture(demoName)
        }

        /*
         ================
         idSessionLocal::AVIGame

         Start AVI recording the current game session
         ================
         */
        fun AVIGame(demoName: Array<String>) {
            if (aviCaptureMode) {
                EndAVICapture()
                return
            }
            if (!mapSpawned) {
                Common.common.Printf("No map spawned.\n")
            }
            if (demoName[0].isEmpty()) {
                val filename: String = Session.FindUnusedFileName("demos/game%03i.game")
                demoName[0] = filename

                // write a one byte stub .game file just so the FindUnusedFileName works,
                FileSystem_h.fileSystem.WriteFile(demoName[0], TempDump.atobb(demoName[0])!!, 1)
            }
            BeginAVICapture(demoName[0])
        }

        fun BeginAVICapture(demoName: String) {
            val name = idStr(demoName)
            name.ExtractFileBase(aviDemoShortName)
            aviCaptureMode = true
            aviDemoFrameCount = 0.0f
            aviTicStart = 0
            sw.AVIOpen(Str.va("demos/%s/", aviDemoShortName), aviDemoShortName.toString())
        }

        fun EndAVICapture() {
            if (!aviCaptureMode) {
                return
            }
            sw.AVIClose()

            // write a .roqParam file so the demo can be converted to a roq file
            val f = FileSystem_h.fileSystem.OpenFileWrite(
                Str.va(
                    "demos/%s/%s.roqParam",
                    aviDemoShortName,
                    aviDemoShortName
                )
            )!!
            f.Printf("INPUT_DIR demos/%s\n", aviDemoShortName)
            f.Printf("FILENAME demos/%s/%s.RoQ\n", aviDemoShortName, aviDemoShortName)
            f.Printf("\nINPUT\n")
            f.Printf("%s_*.tga [00000-%05i]\n", aviDemoShortName, (aviDemoFrameCount - 1).toInt())
            f.Printf("END_INPUT\n")
            //	delete f;
            Common.common.Printf("captured %d frames for %s.\n", aviDemoFrameCount.toInt(), aviDemoShortName)
            aviCaptureMode = false
        }

        @Throws(idException::class)
        fun AdvanceRenderDemo(singleFrameOnly: Boolean) {
            if (lastDemoTic == -1) {
                lastDemoTic = latchedTicNumber - 1
            }
            var skipFrames = 0
            if (!aviCaptureMode && null == timeDemo && !singleFrameOnly) {
                skipFrames = (latchedTicNumber - lastDemoTic) / USERCMD_PER_DEMO_FRAME - 1
                // never skip too many frames, just let it go into slightly slow motion
                if (skipFrames > 4) {
                    skipFrames = 4
                }
                lastDemoTic = latchedTicNumber - latchedTicNumber % USERCMD_PER_DEMO_FRAME
            } else {
                // always advance a single frame with avidemo and timedemo
                lastDemoTic = latchedTicNumber
            }
            while (skipFrames > -1) {
                val ds = CInt(demoSystem_t.DS_FINISHED.ordinal)
                readDemo!!.ReadInt(ds)
                if (ds.integerValue == demoSystem_t.DS_FINISHED.ordinal) {
                    if (numDemoFrames != 1) {
                        // if the demo has a single frame (a demoShot), continuously replay
                        // the renderView that has already been read
                        Stop()
                        StartMenu()
                    }
                    break
                }
                if (ds.integerValue == demoSystem_t.DS_RENDER.ordinal) {
                    val demoTimeOffset = CInt()
                    if (rw.ProcessDemoCommand(readDemo, currentDemoRenderView, demoTimeOffset)) {
                        // a view is ready to render
                        skipFrames--
                        numDemoFrames++
                    }
                    this.demoTimeOffset = demoTimeOffset.integerValue
                    continue
                }
                if (ds.integerValue == demoSystem_t.DS_SOUND.ordinal) {
                    sw.ProcessDemoCommand(readDemo!!)
                    continue
                }
                // appears in v1.2, with savegame format 17
                if (ds.integerValue == demoSystem_t.DS_VERSION.ordinal) {
                    val renderdemoVersion = CInt()
                    readDemo!!.ReadInt(renderdemoVersion)
                    this.renderdemoVersion = renderdemoVersion.integerValue
                    Common.common.Printf("reading a v%d render demo\n", renderdemoVersion.integerValue)
                    // set the savegameVersion to current for render demo paths that share the savegame paths
                    savegameVersion = SAVEGAME_VERSION
                    continue
                }
                Common.common.Error("Bad render demo token")
            }
            if (com_showDemo.GetBool()) {
                Common.common.Printf(
                    "frame:%d DemoTic:%d latched:%d skip:%d\n",
                    numDemoFrames,
                    lastDemoTic,
                    latchedTicNumber,
                    skipFrames
                )
            }
        }

        //
        @Throws(idException::class)
        fun RunGameTic() {
            val logCmd = logCmd_t()
            val cmd = arrayOf(usercmd_t())

            // if we are doing a command demo, read or write from the file
            if (cmdDemoFile != null) {
                if (0 == cmdDemoFile!!.Read(logCmd /*, sizeof( logCmd )*/)) {
                    Common.common.Printf("Command demo completed at logIndex %d\n", logIndex)
                    FileSystem_h.fileSystem.CloseFile(cmdDemoFile!!)
                    cmdDemoFile = null
                    if (aviCaptureMode) {
                        EndAVICapture()
                        Shutdown()
                    }
                    // we fall out of the demo to normal commands
                    // the impulse and chat character toggles may not be correct, and the view
                    // angle will definitely be wrong
                } else {
                    cmd[0] = logCmd.cmd!!
                    cmd[0].ByteSwap()
                    logCmd.consistencyHash = LittleLong(logCmd.consistencyHash)
                }
            }

            // if we didn't get one from the file, get it locally
            if (null == cmdDemoFile) {
                // get a locally created command
                if (Common.com_asyncInput.GetBool()) {
                    cmd[0] = UsercmdGen.usercmdGen.TicCmd(lastGameTic)
                } else {
                    cmd[0] = UsercmdGen.usercmdGen.GetDirectUsercmd()
                }
                lastGameTic++
            }

            // run the game logic every player move
            val start = win_shared.Sys_Milliseconds()
            val ret = Game_local.game.RunFrame(cmd)
            val end = win_shared.Sys_Milliseconds()
            Common.time_gameFrame += end - start // note time used for com_speeds

            // check for constency failure from a recorded command
            if (cmdDemoFile != null) {
                if (ret.consistencyHash != logCmd.consistencyHash) {
                    Common.common.Printf("Consistency failure on logIndex %d\n", logIndex)
                    Stop()
                    return
                }
            }

            // save the cmd for cmdDemo archiving
            if (logIndex < MAX_LOGGED_USERCMDS) {
                loggedUsercmds[logIndex].cmd = cmd[0]
                // save the consistencyHash for demo playback verification
                loggedUsercmds[logIndex].consistencyHash = ret.consistencyHash
                if (logIndex % 30 == 0 && statIndex < MAX_LOGGED_STATS) {
                    loggedStats[statIndex].health = ret.health
                    loggedStats[statIndex].heartRate = ret.heartRate
                    loggedStats[statIndex].stamina = ret.stamina
                    loggedStats[statIndex].combat = ret.combat
                    statIndex++
                }
                logIndex++
            }
            syncNextGameFrame = ret.syncNextGameFrame
            if (ret.sessionCommand[0].code != 0) {
                val args = CmdArgs.idCmdArgs()
                args.TokenizeString(TempDump.ctos(ret.sessionCommand), false)
                if (0 == idStr.Icmp(args.Argv(0), "map")) {
                    // get current player states
                    for (i in 0 until numClients) {
                        mapSpawnData.persistentPlayerInfo[i].set(Game_local.game.GetPersistentPlayerInfo(i))
                    }
                    // clear the devmap key on serverinfo, so player spawns
                    // won't get the map testing items
                    mapSpawnData.serverInfo.Delete("devmap")

                    // go to the next map
                    MoveToNewMap(args.Argv(1))
                } else if (0 == idStr.Icmp(args.Argv(0), "devmap")) {
                    mapSpawnData.serverInfo.Set("devmap", "1")
                    MoveToNewMap(args.Argv(1))
                } else if (0 == idStr.Icmp(args.Argv(0), "died")) {
                    // restart on the same map
                    UnloadMap()
                    SetGUI(guiRestartMenu, null)
                } else if (0 == idStr.Icmp(args.Argv(0), "disconnect")) {
                    cmdSystem.BufferCommandText(cmdExecution_t.CMD_EXEC_INSERT, "stoprecording ; disconnect")
                } else if (0 == idStr.Icmp(args.Argv(0), "endOfDemo")) {
                    cmdSystem.BufferCommandText(cmdExecution_t.CMD_EXEC_NOW, "endOfDemo")
                }
            }
        }

        //
        fun FinishCmdLoad() {}
        fun LoadLoadingGui(mapName: String) {
            // load / program a gui to stay up on the screen while loading
            val stripped = idStr(mapName).StripFileExtension().StripPath()
            val guiMap = Str.va(
                "guis/map/%." + MAX_STRING_CHARS + "s.gui",
                stripped.toString()
            ) //char guiMap[ MAX_STRING_CHARS ];
            // give the gamecode a chance to override
            Game_local.game.GetMapLoadingGUI(guiMap.toCharArray())
            guiLoading = if (UserInterface.uiManager.CheckGui(guiMap)) {
                UserInterface.uiManager.FindGui(guiMap, true, false, true)
            } else {
                UserInterface.uiManager.FindGui("guis/map/loading.gui", true, false, true)
            }
            guiLoading!!.SetStateFloat("map_loading", 0.0f)
        }

        //
        //
        /*
         ================
         idSessionLocal::DemoShot

         A demoShot is a single frame demo
         ================
         */
        fun DemoShot(demoName: String) {
            StartRecordingRenderDemo(demoName)

            // force draw one frame
            UpdateScreen()
            StopRecordingRenderDemo()
        }

        fun TestGUI(guiName: String?) {
            guiTest = if (guiName != null) {
                UserInterface.uiManager.FindGui(guiName, true, false, true)
            } else {
                null
            }
        }

        @Throws(idException::class)
        fun GetBytesNeededForMapLoad(mapName: String): Int {
            val mapDecl = DeclManager.declManager.FindType(declType_t.DECL_MAPDEF, mapName, false)
            val mapDef = mapDecl as idDeclEntityDef?
            if (mapDef != null) {
                return mapDef.dict.GetInt(Str.va("size%d", Max(0, Common.com_machineSpec.GetInteger())))
            } else {
                return if (Common.com_machineSpec.GetInteger() < 2) {
                    200 * 1024 * 1024
                } else {
                    400 * 1024 * 1024
                }
            }
        }

        //
        @Throws(idException::class)
        fun SetBytesNeededForMapLoad(mapName: String, bytesNeeded: Int) {
            val mapDecl =  /*const_cast<idDecl *>*/
                DeclManager.declManager.FindType(declType_t.DECL_MAPDEF, mapName, false)
            val mapDef = mapDecl as idDeclEntityDef?
            if (Common.com_updateLoadSize.GetBool() && mapDef != null) {
                // we assume that if com_updateLoadSize is true then the file is writable
                mapDef.dict.SetInt(Str.va("size%d", Common.com_machineSpec.GetInteger()), bytesNeeded)
                val declText = idStr("\nmapDef ")
                declText.Append(mapDef.GetName())
                declText.Append(" {\n")
                for (i in 0 until mapDef.dict.GetNumKeyVals()) {
                    val kv = mapDef.dict.GetKeyVal(i)
                    if (kv != null && kv.GetKey().Cmp("classname") != 0) {
                        declText.Append(
                            """	"${kv.GetKey()}"		"${kv.GetValue()}"
"""
                        )
                    }
                }
                declText.Append("}")
                mapDef.SetText(declText.toString())
                mapDef.ReplaceSourceFileText()
            }
        }

        /*
         ===============
         idSessionLocal::ExecuteMapChange

         Performs the initialization of a game based on mapSpawnData, used for both single
         player and multiplayer, but not for renderDemos, which don't
         create a game at all.
         Exits with mapSpawned = true
         ===============
         */

        @Throws(idException::class)
        fun ExecuteMapChange(noFadeWipe: Boolean = false /*= false*/) {
            var i: Int
            val reloadingSameMap: Boolean

            // close console and remove any prints from the notify lines
            Console.console.Close()
            if (IsMultiplayer()) {
                // make sure the mp GUI isn't up, or when players get back in the
                // map, mpGame's menu and the gui will be out of sync.
                SetGUI(null, null)
            }

            // mute sound
            snd_system.soundSystem.SetMute(true)

            // clear all menu sounds
            menuSoundWorld!!.ClearAllSoundEmitters()

            // unpause the game sound world
            // NOTE: we UnPause again later down. not sure this is needed
            if (sw.IsPaused()) {
                sw.UnPause()
            }
            if (!noFadeWipe) {
                // capture the current screen and start a wipe
                StartWipe("wipeMaterial", true)

                // immediately complete the wipe to fade out the level transition
                // run the wipe to completion
                CompleteWipe()
            }

            // extract the map name from serverinfo
            val mapString = mapSpawnData.serverInfo.GetString("si_map")
            val fullMapName = idStr("maps/")
            fullMapName.Append(mapString)
            fullMapName.StripFileExtension()

            // shut down the existing game if it is running
            UnloadMap()

            // don't do the deferred caching if we are reloading the same map
            if (fullMapName == currentMapName) {
                reloadingSameMap = true
            } else {
                reloadingSameMap = false
                currentMapName.set(fullMapName)
            }

            // note which media we are going to need to load
            if (!reloadingSameMap) {
                DeclManager.declManager.BeginLevelLoad()
                RenderSystem.renderSystem.BeginLevelLoad()
                snd_system.soundSystem.BeginLevelLoad()
            }
            UserInterface.uiManager.BeginLevelLoad()
            UserInterface.uiManager.Reload(true)

            // set the loading gui that we will wipe to
            LoadLoadingGui(mapString)

            // cause prints to force screen updates as a pacifier,
            // and draw the loading gui instead of game draws
            insideExecuteMapChange = true

            // if this works out we will probably want all the sizes in a def file although this solution will
            // work for new maps etc. after the first load. we can also drop the sizes into the default.cfg
            FileSystem_h.fileSystem.ResetReadCount()
            bytesNeededForMapLoad = if (!reloadingSameMap) {
                GetBytesNeededForMapLoad(mapString)
            } else {
                30 * 1024 * 1024
            }
            ClearWipe()

            // let the loading gui spin for 1 second to animate out
            ShowLoadingGui()

            // note any warning prints that happen during the load process
            Common.common.ClearWarnings(mapString)

            // release the mouse cursor
            // before we do this potentially long operation
            win_input.Sys_GrabMouseCursor(false)

            // if net play, we get the number of clients during mapSpawnInfo processing
            if (!idAsyncNetwork.IsActive()) {
                numClients = 1
            }
            val start = win_shared.Sys_Milliseconds()
            Common.common.Printf("--------- Map Initialization ---------\n")
            Common.common.Printf("Map: %s\n", mapString)

            // let the renderSystem load all the geometry
            if (!rw.InitFromMap(fullMapName.toString())) {
                Common.common.Error("couldn't load %s", fullMapName)
            }

            // for the synchronous networking we needed to roll the angles over from
            // level to level, but now we can just clear everything
            UsercmdGen.usercmdGen.InitForNewMap()
            //	memset( mapSpawnData.mapSpawnUsercmd, 0, sizeof( mapSpawnData.mapSpawnUsercmd ) );
            mapSpawnData.mapSpawnUsercmd = Array(mapSpawnData.mapSpawnUsercmd.size) { usercmd_t() }

            // set the user info
            i = 0
            while (i < numClients) {
                Game_local.game.SetUserInfo(i, mapSpawnData.userInfo[i], idAsyncNetwork.client.IsActive(), false)
                Game_local.game.SetPersistentPlayerInfo(i, mapSpawnData.persistentPlayerInfo[i])
                i++
            }

            // load and spawn all other entities ( from a savegame possibly )
            if (loadingSaveGame && savegameFile != null) {
                if (Game_local.game.InitFromSaveGame("$fullMapName.map", rw, sw, savegameFile!!) == false) {
                    // If the loadgame failed, restart the map with the player persistent data
                    loadingSaveGame = false
                    FileSystem_h.fileSystem.CloseFile(savegameFile!!)
                    savegameFile = null
                    Game_local.game.SetServerInfo(mapSpawnData.serverInfo)
                    Game_local.game.InitFromNewMap(
                        "$fullMapName.map",
                        rw,
                        sw,
                        idAsyncNetwork.server.IsActive(),
                        idAsyncNetwork.client.IsActive(),
                        win_shared.Sys_Milliseconds()
                    )
                }
            } else {
                Game_local.game.SetServerInfo(mapSpawnData.serverInfo)
                Game_local.game.InitFromNewMap(
                    "$fullMapName.map",
                    rw,
                    sw,
                    idAsyncNetwork.server.IsActive(),
                    idAsyncNetwork.client.IsActive(),
                    win_shared.Sys_Milliseconds()
                )
            }
            if (!idAsyncNetwork.IsActive() && !loadingSaveGame) {
                // spawn players
                i = 0
                while (i < numClients) {
                    Game_local.game.SpawnPlayer(i)
                    i++
                }
            }

            // actually purge/load the media
            if (!reloadingSameMap) {
                RenderSystem.renderSystem.EndLevelLoad()
                snd_system.soundSystem.EndLevelLoad(mapString)
                DeclManager.declManager.EndLevelLoad()
                SetBytesNeededForMapLoad(mapString, FileSystem_h.fileSystem.GetReadCount())
            }
            UserInterface.uiManager.EndLevelLoad()
            if (!idAsyncNetwork.IsActive() && !loadingSaveGame) {
                // run a few frames to allow everything to settle
                i = 0
                while (i < 10) {
                    Game_local.game.RunFrame(mapSpawnData.mapSpawnUsercmd /*[0]*/)
                    i++
                }
            }
            Common.common.Printf("-----------------------------------\n")
            val msec = win_shared.Sys_Milliseconds() - start
            Common.common.Printf("%6d msec to load %s\n", msec, mapString)

            // let the renderSystem generate interactions now that everything is spawned
            rw.GenerateAllInteractions()
            Common.common.PrintWarnings()
            if (guiLoading != null && bytesNeededForMapLoad != 0) {
                var pct = guiLoading!!.State().GetFloat("map_loading")
                if (pct < 0.0f) {
                    pct = 0.0f
                }
                while (pct < 1.0f) {
                    guiLoading!!.SetStateFloat("map_loading", pct)
                    guiLoading!!.StateChanged(Common.com_frameTime)
                    win_main.Sys_GenerateEvents()
                    UpdateScreen()
                    pct += 0.05f
                }
            }

            // capture the current screen and start a wipe
            StartWipe("wipe2Material")
            UsercmdGen.usercmdGen.Clear()

            // start saving commands for possible writeCmdDemo usage
            logIndex = 0
            statIndex = 0
            lastSaveIndex = 0

            // don't bother spinning over all the tics we spent loading
            latchedTicNumber = Common.com_ticNumber
            lastGameTic = latchedTicNumber

            // remove any prints from the notify lines
            Console.console.ClearNotifyLines()

            // stop drawing the laoding screen
            insideExecuteMapChange = false

//            Sys_SetPhysicalWorkMemory(-1, -1);

            // set the game sound world for playback
            snd_system.soundSystem.SetPlayingSoundWorld(sw)

            // when loading a save game the sound is paused
            if (sw.IsPaused()) {
                // unpause the game sound world
                sw.UnPause()
            }

            // restart entity sound playback
            snd_system.soundSystem.SetMute(false)

            // we are valid for game draws now
            mapSpawned = true
            win_main.Sys_ClearEvents()
        }

        //
        /*
         ===============
         idSessionLocal::UnloadMap

         Performs cleanup that needs to happen between maps, or when a
         game is exited.
         Exits with mapSpawned = false
         ===============
         */
        fun UnloadMap() {
            StopPlayingRenderDemo()

            // end the current map in the game
            if (Game_local.game != null) {
                Game_local.game.MapShutdown()
            }
            if (cmdDemoFile != null) {
                FileSystem_h.fileSystem.CloseFile(cmdDemoFile!!)
                cmdDemoFile = null
            }
            if (writeDemo != null) {
                StopRecordingRenderDemo()
            }
            mapSpawned = false
        }

        //
        // return true if we actually waiting on an auth reply
        fun MaybeWaitOnCDKey(): Boolean {
            if (authEmitTimeout > 0) {
                authWaitBox = true
                Session.sessLocal.MessageBox(
                    msgBoxType_t.MSG_WAIT,
                    Common.common.GetLanguageDict().GetString("#str_07191"),
                    null,
                    true,
                    null,
                    null,
                    true
                )
                return true
            }
            return false
        }

        fun GetActiveMenu(): idUserInterface? {
            return guiActive
        }


        @Throws(idException::class)
        fun DispatchCommand(gui: idUserInterface?, menuCommand: String, doIngame: Boolean = false /*= true*/) {
            var gui = gui
            if (gui == null) {
                gui = guiActive
            }
            if (gui == guiMainMenu) {
                HandleMainMenuCommands(menuCommand)
                return
            } else if (gui == guiIntro) {
                HandleIntroMenuCommands(menuCommand)
            } else if (gui == guiMsg) {
                HandleMsgCommands(menuCommand)
            } else if (gui == guiTakeNotes) {
                HandleNoteCommands(menuCommand)
            } else if (gui == guiRestartMenu) {
                HandleRestartMenuCommands(menuCommand)
            } else if (Game_local.game != null && guiActive != null && guiActive!!.State().GetBool("gameDraw")) {
                val cmd = Game_local.game.HandleGuiCommands(menuCommand)
                if (null == cmd) {
                    guiActive = null
                } else if (idStr.Icmp(cmd, "main") == 0) {
                    StartMenu()
                } else if (cmd.startsWith("sound ")) {
                    // pipe the GUI sound commands not handled by the game to the main menu code
                    HandleMainMenuCommands(cmd)
                }
            } else if (guiHandle != null) {
                // FIX: C++ calls `(*guiHandle)(menuCommand)` and returns if it returns non-null.
                // Kotlin was checking `menuCommand != null` (always true) instead of invoking the callback.
                if (guiHandle!!.run(menuCommand) != null) {
                    return
                }
            } else if (!doIngame) {
                Common.common.DPrintf(
                    "idSessionLocal::DispatchCommand: no dispatch found for command '%s'\n",
                    menuCommand
                )
            }
            if (doIngame) {
                HandleInGameCommands(menuCommand)
            }
        }

        /*
         ==============
         idSessionLocal::MenuEvent

         Executes any commands returned by the gui
         ==============
         */
        fun MenuEvent(event: sysEvent_s) {
            val menuCommand: String?
            if (guiActive == null) {
                return
            }
            menuCommand = guiActive!!.HandleEvent(event, Common.com_frameTime)
            if (null == menuCommand || menuCommand.isEmpty()) {
                // If the menu didn't handle the event, and it's a key down event for an F key, run the bind
                if (event.evType == sysEventType_t.SE_KEY && event.evValue2 == 1 && event.evValue >= KeyInput.K_F1 && event.evValue <= KeyInput.K_F12) {
                    idKeyInput.ExecKeyBinding(event.evValue)
                }
                return
            }
            DispatchCommand(guiActive, menuCommand)
        }

        @Throws(idException::class)
        fun HandleSaveGameMenuCommand(args: CmdArgs.idCmdArgs, icmd: CInt): Boolean {
            val cmd = args.Argv(icmd.integerValue - 1)
            if (0 == idStr.Icmp(cmd, "loadGame")) {
                val choice = guiActive!!.State().GetInt("loadgame_sel_0")
                if (choice >= 0 && choice < loadGameList.size()) {
                    Session.sessLocal.LoadGame(loadGameList[choice].toString())
                }
                return true
            }
            if (0 == idStr.Icmp(cmd, "saveGame")) {
                val saveGameName = guiActive!!.State().GetString("saveGameName")
                // FIX: C++ `saveGameName && saveGameName[0]` means "non-null AND non-empty".
                // Kotlin had `saveGameName.isEmpty()` which is the opposite.
                if (saveGameName != null && saveGameName.isNotEmpty()) {

                    // First see if the file already exists unless they pass '1' to authorize the overwrite
                    if (icmd.integerValue == args.Argc() || args.Argv(icmd.increment()).toInt() == 0) {
                        var saveFileName = idStr(saveGameName)
                        Session.sessLocal.ScrubSaveGameFileName(saveFileName)
                        saveFileName = idStr("savegames/$saveFileName")
                        saveFileName.SetFileExtension(".save")
                        val game = idStr(cvarSystem.GetCVarString("fs_game"))
                        val file: idFile?
                        file = if (game.Length() != 0) {
                            FileSystem_h.fileSystem.OpenFileRead(saveFileName.toString(), true, game.toString())
                        } else {
                            FileSystem_h.fileSystem.OpenFileRead(saveFileName.toString())
                        }
                        if (file != null) {
                            FileSystem_h.fileSystem.CloseFile(file)

                            // The file exists, see if it's an autosave
                            saveFileName.SetFileExtension(".txt")
                            val src = idLexer(Lexer.LEXFL_NOERRORS or Lexer.LEXFL_NOSTRINGCONCAT)
                            if (src.LoadFile(saveFileName.toString())) {
                                val tok = idToken()
                                src.ReadToken(tok) // Name
                                src.ReadToken(tok) // Map
                                src.ReadToken(tok) // Screenshot
                                if (!tok.IsEmpty()) {
                                    // NOTE: base/ gui doesn't handle that one
                                    guiActive!!.HandleNamedEvent("autosaveOverwriteError")
                                    return true
                                }
                            }
                            guiActive!!.HandleNamedEvent("saveGameOverwrite")
                            return true
                        }
                    }
                    Session.sessLocal.SaveGame(saveGameName)
                    SetSaveGameGuiVars()
                    // DG: select item 0 => select savegame just created (should be on top as it's newest)
                    guiActive!!.SetStateInt("loadgame_sel_0", 0)
                    guiActive!!.StateChanged(Common.com_frameTime)
                }
                return true
            }
            if (0 == idStr.Icmp(cmd, "deleteGame")) {
                val choice = guiActive!!.State().GetInt("loadgame_sel_0")
                if (choice >= 0 && choice < loadGameList.size()) {
                    FileSystem_h.fileSystem.RemoveFile(Str.va("savegames/%s.save", loadGameList[choice].toString()))
                    FileSystem_h.fileSystem.RemoveFile(Str.va("savegames/%s.tga", loadGameList[choice].toString()))
                    FileSystem_h.fileSystem.RemoveFile(Str.va("savegames/%s.txt", loadGameList[choice].toString()))
                    SetSaveGameGuiVars()
                    guiActive!!.StateChanged(Common.com_frameTime)
                }
                return true
            }
            if (0 == idStr.Icmp(cmd, "updateSaveGameInfo")) {
                val choice = guiActive!!.State().GetInt("loadgame_sel_0")
                if (choice >= 0 && choice < loadGameList.size()) {
                    val material: Material.idMaterial?
                    val saveName: idStr = idStr()
                    val description: idStr = idStr()
                    var screenshot: String?
                    val src = idLexer(Lexer.LEXFL_NOERRORS or Lexer.LEXFL_NOSTRINGCONCAT)
                    if (src.LoadFile(Str.va("savegames/%s.txt", loadGameList[choice].toString()))) {
                        val tok = idToken()
                        src.ReadToken(tok)
                        saveName.set(tok)
                        src.ReadToken(tok)
                        description.set(tok)
                        src.ReadToken(tok)
                        screenshot = tok.toString()
                    } else {
                        saveName.set(loadGameList[choice])
                        description.set(loadGameList[choice])
                        screenshot = ""
                    }
                    if (screenshot.length == 0) {
                        screenshot = Str.va("savegames/%s.tga", loadGameList[choice].toString())
                    }
                    material = DeclManager.declManager.FindMaterial(screenshot)
                    if (material != null) {
                        material.ReloadImages(false)
                    }
                    guiActive!!.SetStateString("loadgame_shot", screenshot)
                    saveName.RemoveColors()
                    guiActive!!.SetStateString("saveGameName", saveName.toString())
                    guiActive!!.SetStateString("saveGameDescription", description.toString())
                    val timeStamp = longArrayOf(0)
                    FileSystem_h.fileSystem.ReadFile(
                        Str.va("savegames/%s.save", loadGameList[choice].toString()),
                        null,
                        timeStamp
                    )
                    val date = idStr(sys_local.Sys_TimeStampToStr(timeStamp[0]))
                    val tab = date.Find('\t')
                    val time = date.Right(date.Length() - tab - 1)
                    guiActive!!.SetStateString("saveGameDate", date.Left(tab).toString())
                    guiActive!!.SetStateString("saveGameTime", time.toString())
                }
                return true
            }
            return false
        }

        /*
         ==============
         idSessionLocal::HandleInGameCommands

         Executes any commands returned by the gui
         ==============
         */
        fun HandleInGameCommands(menuCommand: String) {
            // execute the command from the menu
            val args = CmdArgs.idCmdArgs()
            args.TokenizeString(menuCommand, false)

            /*final*/
            val cmd = args.Argv(0)
            if (0 == idStr.Icmp(cmd, "close")) {
                if (guiActive != null) {
                    val ev = sysEvent_s()
                    ev.evType = sysEventType_t.SE_NONE
                    //			final String cmd;
                    args.set(guiActive!!.HandleEvent(ev, Common.com_frameTime))
                    guiActive!!.Activate(false, Common.com_frameTime)
                    guiActive = null
                }
            }
        }

        /*
         ==============
         idSessionLocal::HandleMainMenuCommands

         Executes any commands returned by the gui
         ==============
         */
        @Throws(idException::class)
        fun HandleMainMenuCommands(menuCommand: String) {
            // execute the command from the menu
            val icmd = CInt()
            val args = CmdArgs.idCmdArgs()
            args.TokenizeString(menuCommand, false)
            icmd.integerValue = (0)
            while (icmd.integerValue < args.Argc()) {
                val cmd = args.Argv(icmd.increment())
                if (HandleSaveGameMenuCommand(args, icmd)) {
                    continue
                }

                // always let the game know the command is being run
                if (Game_local.game != null) {
                    Game_local.game.HandleMainMenuCommands(cmd, guiActive)
                }
                if (0 == idStr.Icmp(cmd, "startGame")) {
                    cvarSystem.SetCVarInteger("g_skill", guiMainMenu!!.State().GetInt("skill"))
                    if (icmd.integerValue < args.Argc()) {
                        StartNewGame(args.Argv(icmd.increment()))
                    } else {
                        // FIX: Branches were swapped — demo build should use demo map, not full game map
                        if (ID_DEMO_BUILD) {
                            StartNewGame("game/demo_mars_city1")
                        } else {
                            StartNewGame("game/mars_city1")
                        }
                    }
                    // need to do this here to make sure com_frameTime is correct or the gui activates with a time that
                    // is "however long map load took" time in the past
                    Common.common.GUIFrame(false, false)
                    SetGUI(guiIntro, null)
                    guiIntro!!.StateChanged(Common.com_frameTime, true)
                    // stop playing the game sounds
                    snd_system.soundSystem.SetPlayingSoundWorld(menuSoundWorld!!)
                    continue
                }
                if (0 == idStr.Icmp(cmd, "quit")) {
                    ExitMenu()
                    Common.common.Quit()
                    return
                }
                if (0 == idStr.Icmp(cmd, "loadMod")) {
                    val choice = guiActive!!.State().GetInt("modsList_sel_0")
//                    if (choice >= 0 && choice < modsList.size()) {
//                        CVarSystem.cvarSystem.SetCVarString("fs_game", modsList[choice].toString())
//                        CmdSystem.cmdSystem.BufferCommandText(cmdExecution_t.CMD_EXEC_APPEND, "reloadEngine menu\n")
//                    }
                    if (choice >= 0 && choice < modsList.size()) {
                        loadMod(modsList[choice])
                    }
                }
                if (0 == idStr.Icmp(cmd, "UpdateServers")) {
                    if (guiActive!!.State().GetBool("lanSet")) {
                        cmdSystem.BufferCommandText(cmdExecution_t.CMD_EXEC_NOW, "LANScan")
                    } else {
                        idAsyncNetwork.GetNETServers()
                    }
                    continue
                }
                if (0 == idStr.Icmp(cmd, "RefreshServers")) {
                    if (guiActive!!.State().GetBool("lanSet")) {
                        cmdSystem.BufferCommandText(cmdExecution_t.CMD_EXEC_NOW, "LANScan")
                    } else {
                        idAsyncNetwork.client.serverList.NetScan()
                    }
                    continue
                }
                if (0 == idStr.Icmp(cmd, "FilterServers")) {
                    idAsyncNetwork.client.serverList.ApplyFilter()
                    continue
                }
                if (0 == idStr.Icmp(cmd, "sortServerName")) {
                    idAsyncNetwork.client.serverList.SetSorting(serverSort_t.SORT_SERVERNAME)
                    continue
                }
                if (0 == idStr.Icmp(cmd, "sortGame")) {
                    idAsyncNetwork.client.serverList.SetSorting(serverSort_t.SORT_GAME)
                    continue
                }
                if (0 == idStr.Icmp(cmd, "sortPlayers")) {
                    idAsyncNetwork.client.serverList.SetSorting(serverSort_t.SORT_PLAYERS)
                    continue
                }
                if (0 == idStr.Icmp(cmd, "sortPing")) {
                    idAsyncNetwork.client.serverList.SetSorting(serverSort_t.SORT_PING)
                    continue
                }
                if (0 == idStr.Icmp(cmd, "sortGameType")) {
                    idAsyncNetwork.client.serverList.SetSorting(serverSort_t.SORT_GAMETYPE)
                    continue
                }
                if (0 == idStr.Icmp(cmd, "sortMap")) {
                    idAsyncNetwork.client.serverList.SetSorting(serverSort_t.SORT_MAP)
                    continue
                }
                if (0 == idStr.Icmp(cmd, "serverList")) {
                    idAsyncNetwork.client.serverList.GUIUpdateSelected()
                    continue
                }
                if (0 == idStr.Icmp(cmd, "LANConnect")) {
                    val sel = guiActive!!.State().GetInt("serverList_selid_0")
                    cmdSystem.BufferCommandText(cmdExecution_t.CMD_EXEC_NOW, Str.va("Connect %d\n", sel))
                    return
                }
                if (0 == idStr.Icmp(cmd, "MAPScan")) {
                    /*final*/
                    var gametype = cvarSystem.GetCVarString("si_gameType")
                    if (gametype == null || gametype.isEmpty() || idStr.Icmp(gametype, "singleplayer") == 0) {
                        gametype = "Deathmatch"
                    }
                    var i: Int
                    var num: Int
                    val si_map = idStr(cvarSystem.GetCVarString("si_map"))
                    var dict: idDict?
                    guiMainMenu_MapList.Clear()
                    guiMainMenu_MapList.SetSelection(0)
                    num = FileSystem_h.fileSystem.GetNumMaps()
                    i = 0
                    while (i < num) {
                        dict = FileSystem_h.fileSystem.GetMapDecl(i)
                        if (dict != null && dict.GetBool(gametype)) {
                            /*final*/
                            var mapName = dict.GetString("name")
                            if (mapName.isEmpty()) {
                                mapName = dict.GetString("path")
                            }
                            mapName = Common.common.GetLanguageDict().GetString(mapName)
                            guiMainMenu_MapList.Add(i, idStr(mapName))
                            if (0 == si_map.Icmp(dict.GetString("path"))) {
                                guiMainMenu_MapList.SetSelection(guiMainMenu_MapList.Num() - 1)
                            }
                        }
                        i++
                    }
                    i = guiMainMenu_MapList.GetSelection(null, 0)
                    dict = if (i >= 0) {
                        FileSystem_h.fileSystem.GetMapDecl(i)
                    } else {
                        null
                    }
                    cvarSystem.SetCVarString("si_map", if (dict != null) dict.GetString("path") else "")

                    // set the current level shot
                    UpdateMPLevelShot()
                    continue
                }
                if (0 == idStr.Icmp(cmd, "click_mapList")) {
                    val mapNum = guiMainMenu_MapList.GetSelection(null, 0)
                    val dict = FileSystem_h.fileSystem.GetMapDecl(mapNum)
                    if (dict != null) {
                        cvarSystem.SetCVarString("si_map", dict.GetString("path"))
                    }
                    UpdateMPLevelShot()
                    continue
                }
                if (0 == idStr.Icmp(cmd, "inetConnect")) {
                    val s = guiMainMenu!!.State().GetString("inetGame")
                    if (null == s || s.isEmpty()) {
                        // don't put the menu away if there isn't a valid selection
                        continue
                    }
                    cmdSystem.BufferCommandText(cmdExecution_t.CMD_EXEC_NOW, Str.va("connect %s", s))
                    return
                }
                if (0 == idStr.Icmp(cmd, "startMultiplayer")) {
                    val dedicated = guiActive!!.State().GetInt("dedicated")
                    cvarSystem.SetCVarBool("net_LANServer", guiActive!!.State().GetBool("server_type"))
                    if (gui_configServerRate.GetInteger() > 0) {
                        // guess the best rate for upstream, number of internet clients
                        if (gui_configServerRate.GetInteger() == 5 || cvarSystem.GetCVarBool("net_LANServer")) {
                            cvarSystem.SetCVarInteger("net_serverMaxClientRate", 25600)
                        } else {
                            // internet players
                            var n_clients = cvarSystem.GetCVarInteger("si_maxPlayers")
                            if (0 == dedicated) {
                                n_clients--
                            }
                            var maxclients = 0
                            when (gui_configServerRate.GetInteger()) {
                                1 -> {
                                    // 128 kbits
                                    cvarSystem.SetCVarInteger("net_serverMaxClientRate", 8000)
                                    maxclients = 2
                                }

                                2 -> {
                                    // 256 kbits
                                    cvarSystem.SetCVarInteger("net_serverMaxClientRate", 9500)
                                    maxclients = 3
                                }

                                3 -> {
                                    // 384 kbits
                                    cvarSystem.SetCVarInteger("net_serverMaxClientRate", 10500)
                                    maxclients = 4
                                }

                                4 -> {
                                    // 512 and above..
                                    cvarSystem.SetCVarInteger("net_serverMaxClientRate", 14000)
                                    maxclients = 4
                                }
                            }
                            if (n_clients > maxclients) {
                                if (
                                    MessageBox(
                                        msgBoxType_t.MSG_OKCANCEL,
                                        Str.va(
                                            Common.common.GetLanguageDict().GetString("#str_04315"),
                                            if (dedicated != 0) maxclients else Min(8, maxclients + 1)
                                        ),
                                        Common.common.GetLanguageDict().GetString("#str_04316"),
                                        true,
                                        "OK"
                                        // FIX: C++ checks `[0] == '\0'` meaning "result is empty" (user cancelled).
                                        // Kotlin had `isNotEmpty()` which is the opposite.
                                    ).isEmpty()
                                ) { //[0] == '\0') {
                                    continue
                                }
                                cvarSystem.SetCVarInteger(
                                    "si_maxPlayers",
                                    if (dedicated != 0) maxclients else Min(8, maxclients + 1)
                                )
                            }
                        }
                    }
                    if (0 == dedicated && !cvarSystem.GetCVarBool("net_LANServer") && cvarSystem.GetCVarInteger(
                            "si_maxPlayers"
                        ) > 4
                    ) {
                        // "Dedicated server mode is recommended for internet servers with more than 4 players. Continue in listen mode?"
//				if ( !MessageBox( MSG_YESNO, common.GetLanguageDict().GetString ( "#str_00100625" ), common.GetLanguageDict().GetString ( "#str_00100626" ), true, "yes" )[0] ) {
                        if (MessageBox(
                                msgBoxType_t.MSG_YESNO,
                                Common.common.GetLanguageDict().GetString("#str_00100625"),
                                Common.common.GetLanguageDict().GetString("#str_00100626"),
                                true,
                                "yes"
                            ).isEmpty()
                        ) {
                            continue
                        }
                    }
                    if (dedicated != 0) {
                        cvarSystem.SetCVarInteger("net_serverDedicated", 1)
                    } else {
                        cvarSystem.SetCVarInteger("net_serverDedicated", 0)
                    }
                    ExitMenu()
                    // may trigger a reloadEngine - APPEND
                    cmdSystem.BufferCommandText(cmdExecution_t.CMD_EXEC_APPEND, "SpawnServer\n")
                    return
                }
                if (0 == idStr.Icmp(cmd, "mpSkin")) {
                    var skin: idStr
                    if (args.Argc() - icmd.integerValue >= 1) {
                        skin = idStr(args.Argv(icmd.increment()))
                        cvarSystem.SetCVarString("ui_skin", skin.toString())
                        SetMainMenuSkin()
                    }
                    continue
                }
                if (0 == idStr.Icmp(cmd, "close")) {
                    // if we aren't in a game, the menu can't be closed
                    if (mapSpawned) {
                        ExitMenu()
                    }
                    continue
                }
                if (0 == idStr.Icmp(cmd, "resetdefaults")) {
                    cmdSystem.BufferCommandText(cmdExecution_t.CMD_EXEC_NOW, "exec default.cfg")
                    guiMainMenu!!.SetKeyBindingNames()
                    continue
                }
                if (0 == idStr.Icmp(cmd, "bind")) {
                    if (args.Argc() - icmd.integerValue >= 2) {
                        val key = args.Argv(icmd.increment()).toInt()
                        val bind = args.Argv(icmd.increment())
                        if (idKeyInput.NumBinds(bind) >= 2 && !idKeyInput.KeyIsBoundTo(key, bind)) {
                            idKeyInput.UnbindBinding(bind)
                        }
                        idKeyInput.SetBinding(key, bind)
                        guiMainMenu!!.SetKeyBindingNames()
                    }
                    continue
                }
                if (0 == idStr.Icmp(cmd, "play")) {
                    if (args.Argc() - icmd.integerValue >= 1) {
                        var snd = idStr(args.Argv(icmd.increment()))
                        var channel = 1
                        if (snd.Length() == 1) {
                            channel = snd.toString().toInt()
                            snd = idStr(args.Argv(icmd.integerValue))
                        }
                        menuSoundWorld!!.PlayShaderDirectly(snd.toString(), channel)
                    }
                    continue
                }
                if (0 == idStr.Icmp(cmd, "music")) {
                    if (args.Argc() - icmd.integerValue >= 1) {
                        val snd = idStr(args.Argv(icmd.increment()))
                        menuSoundWorld!!.PlayShaderDirectly(snd.toString(), 2)
                    }
                    continue
                }

                // triggered from mainmenu or mpmain
                if (0 == idStr.Icmp(cmd, "sound")) {
                    var vcmd = idStr()
                    if (args.Argc() - icmd.integerValue >= 1) {
                        // FIX: C++ does `vcmd = args.Argv( icmd++ )` — post-increments icmd
                        vcmd = idStr(args.Argv(icmd.increment()))
                    }
                    if (0 == vcmd.Length() || 0 == vcmd.Icmp("speakers")) {
                        val old = cvarSystem.GetCVarInteger("s_numberOfSpeakers")
                        cmdSystem.BufferCommandText(cmdExecution_t.CMD_EXEC_NOW, "s_restart\n")
                        if (old != cvarSystem.GetCVarInteger("s_numberOfSpeakers")) {
                            if (_WIN32) {
                                MessageBox(
                                    msgBoxType_t.MSG_OK,
                                    Common.common.GetLanguageDict().GetString("#str_04142"),
                                    Common.common.GetLanguageDict().GetString("#str_04141"),
                                    true
                                )
                            } else {
                                // a message that doesn't mention the windows control panel
                                MessageBox(
                                    msgBoxType_t.MSG_OK,
                                    Common.common.GetLanguageDict().GetString("#str_07230"),
                                    Common.common.GetLanguageDict().GetString("#str_04141"),
                                    true
                                )
                            }
                        }
                    }
                    if (0 == vcmd.Icmp("eax")) {
                        if (cvarSystem.GetCVarBool("s_useEAXReverb")) {
                            val eax = snd_system.soundSystem.IsEAXAvailable()
                            when (eax) {
                                2 ->                                     // OpenAL subsystem load failed
                                    MessageBox(
                                        msgBoxType_t.MSG_OK,
                                        Common.common.GetLanguageDict().GetString("#str_07238"),
                                        Common.common.GetLanguageDict().GetString("#str_07231"),
                                        true
                                    )

                                1 ->                                     // when you restart
                                    MessageBox(
                                        msgBoxType_t.MSG_OK,
                                        Common.common.GetLanguageDict().GetString("#str_04137"),
                                        Common.common.GetLanguageDict().GetString("#str_07231"),
                                        true
                                    )

                                -1 -> {
                                    cvarSystem.SetCVarBool("s_useEAXReverb", false)
                                    // disabled
                                    MessageBox(
                                        msgBoxType_t.MSG_OK,
                                        Common.common.GetLanguageDict().GetString("#str_07233"),
                                        Common.common.GetLanguageDict().GetString("#str_07231"),
                                        true
                                    )
                                }

                                0 -> {
                                    cvarSystem.SetCVarBool("s_useEAXReverb", false)
                                    // not available
                                    MessageBox(
                                        msgBoxType_t.MSG_OK,
                                        Common.common.GetLanguageDict().GetString("#str_07232"),
                                        Common.common.GetLanguageDict().GetString("#str_07231"),
                                        true
                                    )
                                }
                            }
                        } else {
                            // also turn off OpenAL so we fully go back to legacy mixer
                            cvarSystem.SetCVarBool("s_useOpenAL", false)
                            // when you restart
                            MessageBox(
                                msgBoxType_t.MSG_OK,
                                Common.common.GetLanguageDict().GetString("#str_04137"),
                                Common.common.GetLanguageDict().GetString("#str_07231"),
                                true
                            )
                        }
                    }
                    if (0 == vcmd.Icmp("drivar")) {
                        cmdSystem.BufferCommandText(cmdExecution_t.CMD_EXEC_NOW, "s_restart\n")
                    }
                    continue
                }
                if (0 == idStr.Icmp(cmd, "video")) {
                    var vcmd = idStr()
                    if (args.Argc() - icmd.integerValue >= 1) {
                        vcmd = idStr(args.Argv(icmd.increment()))
                    }
                    val oldSpec = Common.com_machineSpec.GetInteger()
                    if (idStr.Icmp(vcmd.toString(), "low") == 0) {
                        Common.com_machineSpec.SetInteger(0)
                    } else if (idStr.Icmp(vcmd.toString(), "medium") == 0) {
                        Common.com_machineSpec.SetInteger(1)
                    } else if (idStr.Icmp(vcmd.toString(), "high") == 0) {
                        Common.com_machineSpec.SetInteger(2)
                    } else if (idStr.Icmp(vcmd.toString(), "ultra") == 0) {
                        Common.com_machineSpec.SetInteger(3)
                    } else if (idStr.Icmp(vcmd.toString(), "recommended") == 0) {
                        cmdSystem.BufferCommandText(cmdExecution_t.CMD_EXEC_NOW, "setMachineSpec\n")
                    }
                    if (oldSpec != Common.com_machineSpec.GetInteger()) {
                        guiActive!!.SetStateInt("com_machineSpec", Common.com_machineSpec.GetInteger())
                        guiActive!!.StateChanged(Common.com_frameTime)
                        cmdSystem.BufferCommandText(cmdExecution_t.CMD_EXEC_NOW, "execMachineSpec\n")
                    }
                    if (idStr.Icmp(vcmd.toString(), "restart") == 0) {
                        guiActive!!.HandleNamedEvent("cvar write render")
                        cmdSystem.BufferCommandText(cmdExecution_t.CMD_EXEC_NOW, "vid_restart\n")
                    }
                    continue
                }
                if (0 == idStr.Icmp(cmd, "clearBind")) {
                    if (args.Argc() - icmd.integerValue >= 1) {
                        idKeyInput.UnbindBinding(args.Argv(icmd.increment()))
                        guiMainMenu!!.SetKeyBindingNames()
                    }
                    continue
                }

                // FIXME: obsolete
                if (0 == idStr.Icmp(cmd, "chatdone")) {
                    val temp = idStr(guiActive!!.State().GetString("chattext"))
                    temp.Append("\r")
                    guiActive!!.SetStateString("chattext", "")
                    continue
                }
                if (0 == idStr.Icmp(cmd, "exec")) {

                    //Backup the language so we can restore it after defaults.
                    val lang = idStr(cvarSystem.GetCVarString("sys_lang"))
                    // FIX: C++ does `args.Argv( icmd++ )` — post-increments icmd.
                    // Then compares `args.Argv( icmd - 1 )` which is the same executed command.
                    cmdSystem.BufferCommandText(cmdExecution_t.CMD_EXEC_NOW, args.Argv(icmd.increment()))
                    if (idStr.Icmp("cvar_restart", args.Argv(icmd.integerValue - 1)) == 0) {
                        cmdSystem.BufferCommandText(cmdExecution_t.CMD_EXEC_NOW, "exec default.cfg")
                        cmdSystem.BufferCommandText(cmdExecution_t.CMD_EXEC_NOW, "setMachineSpec\n")

                        //Make sure that any r_brightness changes take effect
                        val bright = cvarSystem.GetCVarFloat("r_brightness")
                        cvarSystem.SetCVarFloat("r_brightness", 0.0f)
                        cvarSystem.SetCVarFloat("r_brightness", bright)

                        //Force user info modified after a reset to defaults
                        cvarSystem.SetModifiedFlags(CVarSystem.CVAR_USERINFO)
                        guiActive!!.SetStateInt("com_machineSpec", Common.com_machineSpec.GetInteger())

                        //Restore the language
                        cvarSystem.SetCVarString("sys_lang", lang.toString())
                    }
                    continue
                }
                if (0 == idStr.Icmp(cmd, "loadBinds")) {
                    guiMainMenu!!.SetKeyBindingNames()
                    continue
                }
                if (0 == idStr.Icmp(cmd, "systemCvars")) {
                    guiActive!!.HandleNamedEvent("cvar read render")
                    guiActive!!.HandleNamedEvent("cvar read sound")
                    continue
                }
                if (0 == idStr.Icmp(cmd, "SetCDKey")) {
                    // we can't do this from inside the HandleMainMenuCommands code, otherwise the message box stuff gets confused
                    cmdSystem.BufferCommandText(cmdExecution_t.CMD_EXEC_APPEND, "promptKey\n")
                    continue
                }
                if (0 == idStr.Icmp(cmd, "CheckUpdate")) {
                    idAsyncNetwork.client.SendVersionCheck()
                    continue
                }
                if (0 == idStr.Icmp(cmd, "CheckUpdate2")) {
                    idAsyncNetwork.client.SendVersionCheck(true)
                    continue
                }
                if (0 == idStr.Icmp(cmd, "checkKeys")) {
                    if (ID_ENFORCE_KEY) {
                        // not a strict check so you silently auth in the background without bugging the user
                        if (!Session.session.CDKeysAreValid(false)) {
                            cmdSystem.BufferCommandText(cmdExecution_t.CMD_EXEC_NOW, "promptKey force")
                            cmdSystem.ExecuteCommandBuffer()
                        }
                    }
                    continue
                }

                // triggered from mainmenu or mpmain
                if (0 == idStr.Icmp(cmd, "punkbuster")) {
                    var vcmd: idStr
                    if (args.Argc() - icmd.integerValue >= 1) {
                        vcmd = idStr(args.Argv(icmd.increment()))
                    }
                    // filtering PB based on enabled/disabled
                    idAsyncNetwork.client.serverList.ApplyFilter()
                    SetPbMenuGuiVars()
                    continue
                }
            }
        }

        private fun loadMod(modName: idStr) {
            val d3xpMods = arrayOf(
                // TODO: if there are more mods that need d3xp as base
                // (and that are supported by dhewm3), add them here
                "bloodmod_roe",
                "d3le", // The Lost Mission
                "librecoopd3xp",
                "perfected_roe",
                "sikkmodd3xp",
                // Doom 3: Phobos (they haven't released source yet, so it won't work yet,
                //                 but ain't I ever the optimist..)
                "tfphobos"
            )
            var baseMod = ""
            for (i in 0 until d3xpMods.size) {
                if (modName.Icmp(d3xpMods[i]) != 0) {
                    baseMod = "d3xp"
                    break
                }
            }

            cvarSystem.SetCVarString("fs_game", modName.toString())
            cvarSystem.SetCVarString("fs_game_base", baseMod)

            cmdSystem.BufferCommandText(cmdExecution_t.CMD_EXEC_APPEND, "reloadEngine menu\n")
        }

        /*
         ==============
         idSessionLocal::HandleChatMenuCommands

         Executes any commands returned by the gui
         ==============
         */
        fun HandleChatMenuCommands(menuCommand: String?) {
            // execute the command from the menu
            var i: Int
            val args = CmdArgs.idCmdArgs()
            args.TokenizeString(menuCommand, false)
            i = 0
            while (i < args.Argc()) {
                val cmd = args.Argv(i++)
                if (idStr.Icmp(cmd, "chatactive") == 0) {
                    //chat.chatMode = CHAT_GLOBAL;
                    continue
                }
                if (idStr.Icmp(cmd, "chatabort") == 0) {
                    //chat.chatMode = CHAT_NONE;
                    continue
                }
                if (idStr.Icmp(cmd, "netready") == 0) {
                    val b = cvarSystem.GetCVarBool("ui_ready")
                    cvarSystem.SetCVarBool("ui_ready", !b)
                    continue
                }
                if (idStr.Icmp(cmd, "netstart") == 0) {
                    cmdSystem.BufferCommandText(cmdExecution_t.CMD_EXEC_NOW, "netcommand start\n")
                    continue
                }
            }
        }

        /*
         ==============
         idSessionLocal::HandleIntroMenuCommands

         Executes any commands returned by the gui
         ==============
         */
        fun HandleIntroMenuCommands(menuCommand: String?) {
            // execute the command from the menu
            var i: Int
            val args = CmdArgs.idCmdArgs()
            args.TokenizeString(menuCommand, false)
            i = 0
            while (i < args.Argc()) {
                val cmd = args.Argv(i++)
                if (0 == idStr.Icmp(cmd, "startGame")) {
                    menuSoundWorld!!.ClearAllSoundEmitters()
                    ExitMenu()
                    continue
                }
                if (0 == idStr.Icmp(cmd, "play")) {
                    if (args.Argc() - i >= 1) {
                        val snd = args.Argv(i++)
                        menuSoundWorld!!.PlayShaderDirectly(snd)
                    }
                    continue
                }
            }
        }

        /*
         ==============
         idSessionLocal::HandleRestartMenuCommands

         Executes any commands returned by the gui
         ==============
         */
        @Throws(idException::class)
        fun HandleRestartMenuCommands(menuCommand: String?) {
            // execute the command from the menu
            val icmd = CInt()
            val args = CmdArgs.idCmdArgs()
            args.TokenizeString(menuCommand, false)
            icmd.integerValue = (0)
            while (icmd.integerValue < args.Argc()) {
                val cmd = args.Argv(icmd.increment())
                if (HandleSaveGameMenuCommand(args, icmd)) {
                    continue
                }
                if (0 == idStr.Icmp(cmd, "restart")) {
                    if (!LoadGame(GetAutoSaveName(mapSpawnData.serverInfo.GetString("si_map")))) {
                        // If we can't load the autosave then just restart the map
                        MoveToNewMap(mapSpawnData.serverInfo.GetString("si_map"))
                    }
                    continue
                }
                if (0 == idStr.Icmp(cmd, "quit")) {
                    ExitMenu()
                    Common.common.Quit()
                    return
                }
                if (0 == idStr.Icmp(cmd, "exec")) {
                    cmdSystem.BufferCommandText(cmdExecution_t.CMD_EXEC_APPEND, args.Argv(icmd.increment()))
                    continue
                }
                if (0 == idStr.Icmp(cmd, "play")) {
                    if (args.Argc() - icmd.integerValue >= 1) {
                        val snd = args.Argv(icmd.increment())
                        sw.PlayShaderDirectly(snd)
                    }
                    continue
                }
            }
        }

        fun HandleMsgCommands(menuCommand: String) {
            assert(guiActive == guiMsg)
            // "stop" works even on first frame
            if (idStr.Icmp(menuCommand, "stop") == 0) {
                // force hiding the current dialog
                guiActive = guiMsgRestore
                guiMsgRestore = null
                msgRunning = false
                msgRetIndex = -1
            }
            if (msgIgnoreButtons) {
                Common.common.DPrintf("MessageBox HandleMsgCommands 1st frame ignore\n")
                return
            }
            if (idStr.Icmp(menuCommand, "mid") == 0 || idStr.Icmp(menuCommand, "left") == 0) {
                guiActive = guiMsgRestore
                guiMsgRestore = null
                msgRunning = false
                msgRetIndex = 0
                DispatchCommand(guiActive, msgFireBack[0].toString())
            } else if (idStr.Icmp(menuCommand, "right") == 0) {
                guiActive = guiMsgRestore
                guiMsgRestore = null
                msgRunning = false
                msgRetIndex = 1
                DispatchCommand(guiActive, msgFireBack[1].toString())
            }
        }

        @Throws(idException::class)
        fun HandleNoteCommands(menuCommand: String) {
            guiActive = null
            if (idStr.Icmp(menuCommand, "note") == 0 && mapSpawned) {
                var file: idFile? = null
                for (tries in 0..9) {
                    file = FileSystem_h.fileSystem.OpenExplicitFileRead(NOTEDATFILE)
                    if (file != null) {
                        break
                    }
                    win_main.Sys_Sleep(500)
                }
                val noteNumber = CInt()
                if (file != null) {
                    file.ReadInt(noteNumber) //4);
                    FileSystem_h.fileSystem.CloseFile(file)
                }
                var i: Int
                var str: idStr
                var noteNum: idStr?
                val shotName = idStr()
                var workName: idStr?
                val fileName = idStr("viewnotes/")
                val fileList = idStrList()
                var severity: String? = null
                var p = guiTakeNotes!!.State().GetString("notefile")
                if (p == null || p.isEmpty()) {
                    p = cvarSystem.GetCVarString("ui_name")
                }
                val extended = guiTakeNotes!!.State().GetBool("extended")
                if (extended) {
                    severity = if (guiTakeNotes!!.State().GetInt("severity") == 1) {
                        "WishList_Viewnotes/"
                    } else {
                        "MustFix_Viewnotes/"
                    }
                    fileName.Append(severity)
                    val mapDecl = DeclManager.declManager.FindType(
                        declType_t.DECL_ENTITYDEF,
                        mapSpawnData.serverInfo.GetString("si_map"),
                        false
                    )
                    val mapInfo = mapDecl as idDeclEntityDef
                    if (mapInfo != null) {
                        fileName.Append(mapInfo.dict.GetString("devname"))
                    } else {
                        fileName.Append(mapSpawnData.serverInfo.GetString("si_map"))
                        fileName.StripFileExtension()
                    }
                    val count = guiTakeNotes!!.State().GetInt("person_numsel")
                    if (count == 0) {
                        fileList.add(idStr("$fileName/Nobody"))
                    } else {
                        i = 0
                        while (i < count) {
                            val person = guiTakeNotes!!.State().GetInt(Str.va("person_sel_%d", i))
                            workName = idStr("$fileName/")
                            workName.plusAssign(
                                guiTakeNotes!!.State().GetString(Str.va("person_item_%d", person), "Nobody")!!
                            )
                            fileList.add(workName)
                            i++
                        }
                    }
                } else {
                    fileName.Append("maps/")
                    fileName.Append(mapSpawnData.serverInfo.GetString("si_map"))
                    fileName.StripFileExtension()
                    fileList.add(fileName)
                }
                val bCon = cvarSystem.GetCVarBool("con_noPrint")
                cvarSystem.SetCVarBool("con_noPrint", true)
                i = 0
                while (i < fileList.size()) {
                    workName = fileList[i]
                    workName.Append("/")
                    workName.Append(p)
                    val workNote = CInt(noteNumber.integerValue)
                    R_ScreenshotFilename(workNote, workName.toString(), shotName)
                    noteNum = shotName
                    noteNum.StripPath()
                    noteNum.StripFileExtension()
                    if (severity != null && !severity.isEmpty()) {
                        workName = idStr(severity)
                        workName.Append("viewNotes")
                    }
                    str = idStr(
                        String.format(
                            "recordViewNotes \"%s\" \"%s\" \"%s\"\n",
                            workName,
                            noteNum,
                            guiTakeNotes!!.State().GetString("note")
                        )
                    )
                    cmdSystem.BufferCommandText(cmdExecution_t.CMD_EXEC_NOW, str.toString())
                    cmdSystem.ExecuteCommandBuffer()
                    UpdateScreen()
                    RenderSystem.renderSystem.TakeScreenshot(
                        RenderSystem.renderSystem.GetScreenWidth(),
                        RenderSystem.renderSystem.GetScreenHeight(),
                        shotName.toString(),
                        1,
                        null
                    )
                    i++
                }
                noteNumber.increment()
                for (tries in 0..9) {
                    file = FileSystem_h.fileSystem.OpenExplicitFileWrite("p:/viewnotes/notenumber.dat")
                    if (file != null) {
                        break
                    }
                    win_main.Sys_Sleep(500)
                }
                if (file != null) {
                    file.WriteInt(noteNumber.integerValue) //, 4);
                    FileSystem_h.fileSystem.CloseFile(file)
                }
                cmdSystem.BufferCommandText(cmdExecution_t.CMD_EXEC_NOW, "closeViewNotes\n")
                cvarSystem.SetCVarBool("con_noPrint", bCon)
            }
        }

        fun GetSaveGameList(fileList: idStrList, fileTimes: idList<fileTIME_T>) {
            var i: Int
            val files: idFileList

            // NOTE: no fs_game_base for savegames
            val game = idStr(cvarSystem.GetCVarString("fs_game"))
            files = if (game.Length() != 0) {
                FileSystem_h.fileSystem.ListFiles("savegames", ".save", false, false, game.toString())
            } else {
                FileSystem_h.fileSystem.ListFiles("savegames", ".save")
            }
            fileList.set(files.GetList())
            FileSystem_h.fileSystem.FreeFileList(files)
            i = 0
            while (i < fileList.size()) {
                val timeStamp = longArrayOf(0)
                FileSystem_h.fileSystem.ReadFile("savegames/" + fileList[i], null, timeStamp)
                fileList[i].StripLeading('/')
                fileList[i].StripFileExtension()
                val ft = fileTIME_T()
                ft.index = i
                ft.timeStamp = timeStamp[0]
                fileTimes.Append(ft)
                i++
            }
            fileTimes.Sort(idListSaveGameCompare())
        }


        fun TakeNotes(p: String, extended: Boolean = false /*= false*/) {
            if (!mapSpawned) {
                Common.common.Printf("No map loaded!\n")
                return
            }
            if (extended) {
                guiTakeNotes = UserInterface.uiManager.FindGui("guis/takeNotes2.gui", true, false, true)

//                final String[] people;
//                if (false) {
//                    people = new String[]{
//                        "Nobody", "Adam", "Brandon", "David", "PHook", "Jay", "Jake",
//                        "PatJ", "Brett", "Ted", "Darin", "Brian", "Sean"
//                    };
//                } else {
//                    people = new String[]{
//                        "Tim", "Kenneth", "Robert",
//                        "Matt", "Mal", "Jerry", "Steve", "Pat",
//                        "Xian", "Ed", "Fred", "James", "Eric", "Andy", "Seneca", "Patrick", "Kevin",
//                        "MrElusive", "Jim", "Brian", "John", "Adrian", "Nobody"
//                    };
//                }
//
//                final int numPeople = PEOPLE.length;
//
                val guiList_people = UserInterface.uiManager.AllocListGUI()
                guiList_people.Config(guiTakeNotes!!, "person")
                for (i in 0 until NUM_PEOPLE) {
                    guiList_people.Push(idStr(PEOPLE[i]))
                }
                UserInterface.uiManager.FreeListGUI(guiList_people)
            } else {
                guiTakeNotes = UserInterface.uiManager.FindGui("guis/takeNotes.gui", true, false, true)
            }
            SetGUI(guiTakeNotes, null)
            guiActive!!.SetStateString("note", "")
            guiActive!!.SetStateString("notefile", p)
            guiActive!!.SetStateBool("extended", extended)
            guiActive!!.Activate(true, Common.com_frameTime)
        }

        fun UpdateMPLevelShot() {
            val screenshot = StringBuffer()
            FileSystem_h.fileSystem.FindMapScreenshot(
                cvarSystem.GetCVarString("si_map"),
                screenshot,
                MAX_STRING_CHARS
            )
            guiMainMenu!!.SetStateString("current_levelshot", screenshot.toString())
        }

        fun SetSaveGameGuiVars() {
            var name: idStr = idStr()
            val fileList = idStrList()
            val fileTimes = idList<fileTIME_T>()
            loadGameList.clear()
            fileList.clear()
            fileTimes.Clear()
            GetSaveGameList(fileList, fileTimes)
            loadGameList.setSize(fileList.size())
            for (i in 0 until fileList.size()) {
                loadGameList[i] = fileList[fileTimes[i].index]
                val src = idLexer(Lexer.LEXFL_NOERRORS or Lexer.LEXFL_NOSTRINGCONCAT)
                if (src.LoadFile(Str.va("savegames/%s.txt", loadGameList[i]))) {
                    val tok = idToken()
                    src.ReadToken(tok)
                    name.set(tok.toString())
                } else {
                    name.set(loadGameList[i])
                }
                name.Append("\t")
                val date = sys_local.Sys_TimeStampToStr(fileTimes[i].timeStamp)
                name.Append(date)
                guiActive!!.SetStateString(Str.va("loadgame_item_%d", i), name.toString())
            }
            guiActive!!.DeleteStateVar(Str.va("loadgame_item_%d", fileList.size()))

            guiActive!!.SetStateString("loadgame_sel_0", "-1")
            guiActive!!.SetStateString("loadgame_shot", "guis/assets/blankLevelShot")
        }

        fun SetMainMenuGuiVars() {
            guiMainMenu!!.SetStateString("serverlist_sel_0", "-1")
            guiMainMenu!!.SetStateString("serverlist_selid_0", "-1")
            guiMainMenu!!.SetStateInt("com_machineSpec", Common.com_machineSpec.GetInteger())

            // "inetGame" will hold a hand-typed inet address, which is not archived to a cvar
            guiMainMenu!!.SetStateString("inetGame", "")

            // key bind names
            guiMainMenu!!.SetKeyBindingNames()

            // flag for in-game menu
            if (mapSpawned) {
                guiMainMenu!!.SetStateString("inGame", if (IsMultiplayer()) "2" else "1")
            } else {
                guiMainMenu!!.SetStateString("inGame", "0")
            }
            SetCDKeyGuiVars()
            if (ID_DEMO_BUILD) {
                guiMainMenu!!.SetStateString("nightmare", "0")
            } else {
                guiMainMenu!!.SetStateString(
                    "nightmare",
                    if (cvarSystem.GetCVarBool("g_nightmare")) "1" else "0"
                )
            }
            guiMainMenu!!.SetStateString("browser_levelshot", "guis/assets/splash/pdtempa")
            SetMainMenuSkin()
            // Mods Menu
            SetModsMenuGuiVars()
            guiMsg!!.SetStateString("visible_hasxp", if (FileSystem_h.fileSystem.HasD3XP()) "1" else "0")
            if (__linux__) {
                guiMainMenu!!.SetStateString("driver_prompt", "1")
            } else {
                guiMainMenu!!.SetStateString("driver_prompt", "0")
            }
            SetPbMenuGuiVars()
        }

        fun SetModsMenuGuiVars() {
            var i: Int
            val list = FileSystem_h.fileSystem.ListMods()
            modsList.setSize(list.GetNumMods())

            // Build the gui list
            for (i in 0 until list.GetNumMods()) {
                guiActive!!.SetStateString(Str.va("modsList_item_%d", i), list.GetDescription(i))
                modsList[i] = idStr(list.GetMod(i))
            }

            guiActive!!.DeleteStateVar(Str.va("modsList_item_%d", list.GetNumMods()))
            guiActive!!.SetStateString("modsList_sel_0", "-1")

            FileSystem_h.fileSystem.FreeModList(list)
        }

        fun SetMainMenuSkin() {
            // skins
            var str: idStr = idStr(cvarSystem.GetCVarString("mod_validSkins"))
            val uiSkin = idStr(cvarSystem.GetCVarString("ui_skin"))
            var skin: idStr
            var skinId = 1
            var count = 1
            while (str.Length() != 0) {
                val n = str.Find(";")
                if (n >= 0) {
                    skin = str.Left(n)
                    str = str.Right(str.Length() - n - 1)
                } else {
                    skin = str
                    str.set("")
                }
                if (skin.Icmp(uiSkin.toString()) == 0) {
                    skinId = count
                }
                count++
            }
            for (i in 0 until count) {
                guiMainMenu!!.SetStateInt(Str.va("skin%d", i + 1), 0)
            }
            guiMainMenu!!.SetStateInt(Str.va("skin%d", skinId), 1)
        }

        fun SetPbMenuGuiVars() {}
        private fun BoxDialogSanityCheck(): Boolean {
            if (!Common.common.IsInitialized()) {
                Common.common.DPrintf("message box sanity check: !common.IsInitialized()\n")
                return false
            }
            if (guiMsg == null) {
                return false
            }
            if (guiMsgRestore != null) {
                Common.common.DPrintf("message box sanity check: recursed\n")
                return false
            }
            if (cvarSystem.GetCVarInteger("net_serverDedicated") != 0) {
                Common.common.DPrintf("message box sanity check: not compatible with dedicated server\n")
                return false
            }
            return true
        }

        /*
         ===============
         idSessionLocal::EmitGameAuth
         we toggled some key state to CDKEY_CHECKING. send a standalone auth packet to validate
         ===============
         */
        private fun EmitGameAuth() {
            // make sure the auth reply is empty, we use it to indicate an auth reply
            authMsg.Empty()
            if (idAsyncNetwork.client.SendAuthCheck(
                    if (cdkey_state == cdKeyState_t.CDKEY_CHECKING) TempDump.ctos(cdkey) else null,
                    if (xpkey_state == cdKeyState_t.CDKEY_CHECKING) TempDump.ctos(xpkey) else null
                )
            ) {
                authEmitTimeout = win_shared.Sys_Milliseconds() + CDKEY_AUTH_TIMEOUT
                Common.common.DPrintf("authing with the master..\n")
            } else {
                // net is not available
                Common.common.DPrintf("sendAuthCheck failed\n")
                if (cdkey_state == cdKeyState_t.CDKEY_CHECKING) {
                    cdkey_state = cdKeyState_t.CDKEY_OK
                }
                if (xpkey_state == cdKeyState_t.CDKEY_CHECKING) {
                    xpkey_state = cdKeyState_t.CDKEY_OK
                }
            }
        }

        internal enum class cdKeyState_t {
            CDKEY_UNKNOWN,  // need to perform checks on the key
            CDKEY_INVALID,  // that key is wrong
            CDKEY_OK,  // valid
            CDKEY_CHECKING,  // sent a check request ( gameAuth only )
            CDKEY_NA // does not apply, xp key when xp is not present
        }

        companion object {
            val com_aviDemoHeight: idCVar = idCVar("com_aviDemoHeight", "256", CVarSystem.CVAR_SYSTEM, "")
            val com_aviDemoSamples: idCVar = idCVar("com_aviDemoSamples", "16", CVarSystem.CVAR_SYSTEM, "")
            val com_aviDemoTics: idCVar =
                idCVar("com_aviDemoTics", "2", CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_INTEGER, "", 1.0f, 60.0f)
            val com_aviDemoWidth: idCVar = idCVar("com_aviDemoWidth", "256", CVarSystem.CVAR_SYSTEM, "")
            val com_fixedTic: idCVar =
                idCVar("com_fixedTic", "0", CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_INTEGER, "", 0.0f, 10.0f)
            val com_guid: idCVar =
                idCVar("com_guid", "", CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_ARCHIVE or CVarSystem.CVAR_ROM, "")
            val com_minTics: idCVar = idCVar("com_minTics", "1", CVarSystem.CVAR_SYSTEM, "")

            //
            //=====================================
            //
            val com_showAngles: idCVar =
                idCVar("com_showAngles", "0", CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_BOOL, "")
            val com_showDemo: idCVar = idCVar("com_showDemo", "0", CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_BOOL, "")
            val com_showTics: idCVar = idCVar("com_showTics", "0", CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_BOOL, "")
            val com_skipGameDraw: idCVar =
                idCVar("com_skipGameDraw", "0", CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_BOOL, "")
            val com_wipeSeconds: idCVar = idCVar("com_wipeSeconds", "1", CVarSystem.CVAR_SYSTEM, "")

            //
            val gui_configServerRate: idCVar = idCVar(
                "gui_configServerRate",
                "0",
                CVarSystem.CVAR_GUI or CVarSystem.CVAR_ARCHIVE or CVarSystem.CVAR_ROM or CVarSystem.CVAR_INTEGER,
                ""
            )

            val com_numQuicksaves: idCVar = idCVar(
                "com_numQuicksaves",
                "4",
                CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_ARCHIVE or CVarSystem.CVAR_INTEGER,
                "number of quicksaves to keep before overwriting the oldest",
                1.0f,
                99.0f
            )


            //
            const val ANGLE_GRAPH_HEIGHT = 128
            const val ANGLE_GRAPH_STRETCH = 3

            // digits to letters table
            val CDKEY_DIGITS: String = "TWSBJCGD7PA23RLH"
            val NOTEDATFILE: String = "C:/notenumber.dat"
            const val bufferSize = 65535
            private const val CDKEY_AUTH_TIMEOUT = 5000

            //
            private const val CDKEY_BUF_LEN = 17
            private val PEOPLE: Array<String> = arrayOf(
                "Tim", "Kenneth", "Robert",
                "Matt", "Mal", "Jerry", "Steve", "Pat",
                "Xian", "Ed", "Fred", "James", "Eric", "Andy", "Seneca", "Patrick", "Kevin",
                "MrElusive", "Jim", "Brian", "John", "Adrian", "Nobody"
            )
            private val NUM_PEOPLE = PEOPLE.size
            var DBG_Draw = 0 // NOTE: debug artifact, kept to avoid changing companion object layout
            var frameEvents = 0
            private var DBG_EndFrame = 0 // NOTE: debug artifact, kept to avoid changing companion object layout
            private var cmd //TODO:stringify?
                    : CharArray? = null
        }

        init {
            guiMsgRestore = guiTakeNotes
            guiMsg = guiMsgRestore
            guiTest = guiMsg
            guiActive = guiTest
            guiGameOver = guiActive
            guiLoading = guiGameOver
            guiRestartMenu = guiLoading
            guiIntro = guiRestartMenu
            guiMainMenu = guiIntro
            guiInGame = guiMainMenu
            menuSoundWorld = null
            loggedUsercmds = Array(MAX_LOGGED_USERCMDS) { logCmd_t() }
            loggedStats = Array(MAX_LOGGED_STATS) { logStats_t() }
            Clear()
        }
    }
}
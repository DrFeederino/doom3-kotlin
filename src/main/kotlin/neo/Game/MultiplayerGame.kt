/*
 * Copyright (C) 1999-2011 id Software LLC, a ZeniMax Media company.
 * Translated to Kotlin by Dr. Feederino with support of Claude Code
 *
 * This file is part of the Doom 3 Kotlin project.
 * Original source: neo/Game/MultiplayerGame.cpp, neo/Game/MultiplayerGame.h
 *
 * Doom 3 Source Code is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Doom 3 Source Code is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 */

package neo.Game

import neo.Game.GameSys.SysCvar
import neo.Game.Game_local.gameSoundChannel_t
import neo.Game.Player.idPlayer
import neo.Sound.snd_shader.idSoundShader
import neo.TempDump
import neo.framework.*
import neo.framework.Async.NetworkSystem
import neo.framework.CVarSystem.idCVar
import neo.framework.CmdSystem.cmdExecution_t
import neo.framework.CmdSystem.cmdFunction_t
import neo.framework.DeclManager.declType_t
import neo.framework.File_h.idFile
import neo.idlib.BitMsg.idBitMsg
import neo.idlib.BitMsg.idBitMsgDelta
import neo.idlib.CmdArgs
import neo.idlib.Dict_h.idDict
import neo.idlib.Dict_h.idKeyValue
import neo.idlib.MAX_STRING_CHARS
import neo.idlib.Min
import neo.idlib.Text.Str
import neo.idlib.Text.Str.idStr
import neo.idlib.Text.Str.idStr.Companion.FindText
import neo.idlib.math.idMath
import neo.idlib.math.idVec3
import neo.ui.GameSSDWindow
import neo.ui.ListGUI.idListGUI
import neo.ui.UserInterface
import neo.ui.UserInterface.idUserInterface
import java.nio.ByteBuffer

object MultiplayerGame {
    const val CHAT_FADE_TIME = 400
    const val FRAGLIMIT_DELAY = 2000

    // could be a problem if players manage to go down sudden deaths till this .. oh well
    const val LASTMAN_NOLIVES = -20
    const val MP_PLAYER_MAXFRAGS = 100
    const val MP_PLAYER_MAXPING = 999
    const val MP_PLAYER_MAXWINS = 100

    //
    const val MP_PLAYER_MINFRAGS = -100

    //
    const val NUM_CHAT_NOTIFY = 5

    //
    val g_spectatorChat: idCVar = idCVar(
        "g_spectatorChat",
        "0",
        CVarSystem.CVAR_GAME or CVarSystem.CVAR_ARCHIVE or CVarSystem.CVAR_BOOL,
        "let spectators talk to everyone during game"
    )

    /*
     ===============================================================================

     Basic DOOM multiplayer

     ===============================================================================
     */
    enum class gameType_t {
        GAME_SP, GAME_DM, GAME_TOURNEY, GAME_TDM, GAME_LASTMAN
    }

    //
    enum class playerVote_t {
        PLAYER_VOTE_NONE, PLAYER_VOTE_NO, PLAYER_VOTE_YES, PLAYER_VOTE_WAIT // mark a player allowed to vote
    }

    enum class snd_evt_t {
        SND_YOUWIN,  //= 0,
        SND_YOULOSE, SND_FIGHT, SND_VOTE, SND_VOTE_PASSED, SND_VOTE_FAILED, SND_THREE, SND_TWO, SND_ONE, SND_SUDDENDEATH, SND_COUNT
    }

    class mpPlayerState_s {
        var fragCount // kills
                = 0
        var ingame = false
        var ping // player ping
                = 0
        var scoreBoardUp // toggle based on player scoreboard button, used to activate de-activate the scoreboard gui
                = false
        var teamFragCount // team kills
                = 0
        var vote // player's vote
                : playerVote_t = playerVote_t.PLAYER_VOTE_NONE
        var wins // wins
                = 0
    }

    class mpChatLine_s {
        var fade // starts high and decreases, line is removed once reached 0
                : Short = 0
        var line: idStr = idStr()
    }

    //
    class idMultiplayerGame {
        private var bCurrentMenuMsg // send menu state updates to server
                = false
        private var chatDataUpdated = false

        //
        // chat data
        private val chatHistory = TempDump.allocArray(mpChatLine_s::class.java, NUM_CHAT_NOTIFY)
        private var chatHistoryIndex = 0
        private var chatHistorySize // 0 <= x < NUM_CHAT_NOTIFY
                = 0
        private var currentMenu // 0 - none, 1 - mainGui, 2 - msgmodeGui
                = 0

        //
        // tourney
        private val currentTourneyPlayer: IntArray = IntArray(2) // our current set of players
        private var fragLimitTimeout = 0

        // state vars
        private var gameState // what state the current game is in
                : gameState_t = gameState_t.INACTIVE
        private var guiChat // chat text
                : idUserInterface? = null
        private val kickVoteMap: IntArray = IntArray(Game_local.MAX_CLIENTS)
        private var lastChatLineTime = 0

        //
        private var lastGameType // for restarts
                : gameType_t = gameType_t.GAME_SP
        private var lastWinner // plays again
                = 0
        private var mainGui // ready / nick / votes etc.
                : idUserInterface? = null
        private var mapList: idListGUI? = null
        private var matchStartedTime // time current match started
                = 0
        private var msgmodeGui // message mode
                : idUserInterface? = null
        private var nextMenu // if 0, will do mainGui
                = 0
        private var nextState // state to switch to when nextStateSwitch is hit
                : gameState_t = gameState_t.INACTIVE

        //
        // time related
        private var nextStateSwitch // time next state switch
                = 0
        private var noVotes // and for no votes
                = 0f

        //
        // rankings are used by UpdateScoreboard and UpdateHud
        private var numRankedPlayers // ranked players, others may be empty slots or spectators
                = 0
        private var one = false
        private var two = false
        private var three // keeps count down voice from repeating
                = false
        private var pingUpdateTime // time to update ping
                = 0

        //
        private var playerState: Array<mpPlayerState_s> = Array(Game_local.MAX_CLIENTS) { mpPlayerState_s() }

        //
        private var pureReady // defaults to false, set to true once server game is running with pure checksums
                = false
        private var rankedPlayers: Array<idPlayer> = arrayOfNulls<idPlayer>(Game_local.MAX_CLIENTS) as Array<idPlayer>

        //
        // guis
        private var scoreBoard // scoreboard
                : idUserInterface? = null
        private var spectateGui // spectate info
                : idUserInterface? = null
        private var startFragLimit // synchronize to clients in initial state, set on -> GAMEON
                = 0

        //
        private var switchThrottle: IntArray = IntArray(3)

        //
        //
        private var voiceChatThrottle = 0

        //
        // keep track of clients which are willingly in spectator mode
        //
        // vote vars
        private var vote // active vote or VOTE_NONE
                : vote_flags_t = vote_flags_t.VOTE_NONE
        private var voteExecTime // delay between vote passed msg and execute
                = 0
        private var voteString // the vote string ( client )
                : idStr = idStr()
        private var voteTimeOut // when the current vote expires
                = 0
        private var voteValue // the data voted upon ( server )
                : idStr = idStr()
        private var voted // hide vote box ( client )
                = false
        private var warmupEndTime // warmup till..
                = 0

        //
        // warmup
        private var warmupText // text shown in warmup area of screen
                : idStr = idStr()
        private var yesVotes // counter for yes votes
                = 0f

        fun Shutdown() {
            Clear()
        }

        // resets everything and prepares for a match
        fun Reset() {
            Clear()
            assert(null == scoreBoard && null == spectateGui && null == guiChat && null == mainGui && null == mapList)
            scoreBoard = UserInterface.uiManager.FindGui("guis/scoreboard.gui", true, false, true)
            spectateGui = UserInterface.uiManager.FindGui("guis/spectate.gui", true, false, true)
            guiChat = UserInterface.uiManager.FindGui("guis/chat.gui", true, false, true)
            mainGui = UserInterface.uiManager.FindGui("guis/mpmain.gui", true, false, true)
            mapList = UserInterface.uiManager.AllocListGUI()
            mapList!!.Config(mainGui!!, "mapList")
            // set this GUI so that our Draw function is still called when it becomes the active/fullscreen GUI
            mainGui!!.SetStateBool("gameDraw", true)
            mainGui!!.SetKeyBindingNames()
            mainGui!!.SetStateInt("com_machineSpec", CVarSystem.cvarSystem.GetCVarInteger("com_machineSpec"))
            SetMenuSkin()
            msgmodeGui = UserInterface.uiManager.FindGui("guis/mpmsgmode.gui", true, false, true)
            msgmodeGui!!.SetStateBool("gameDraw", true)
            ClearGuis()
            ClearChatData()
            warmupEndTime = 0
        }

        // setup local data for a new player
        fun SpawnPlayer(clientNum: Int) {
            val ingame = playerState[clientNum].ingame
            playerState[clientNum] = mpPlayerState_s()
            if (!Game_local.gameLocal.isClient) {
                val p = Game_local.gameLocal.entities[clientNum] as idPlayer
                p.spawnedTime = Game_local.gameLocal.time
                if (Game_local.gameLocal.gameType == gameType_t.GAME_TDM) {
                    SwitchToTeam(clientNum, -1, p.team)
                }
                p.tourneyRank = 0
                if (Game_local.gameLocal.gameType == gameType_t.GAME_TOURNEY && gameState == gameState_t.GAMEON) {
                    p.tourneyRank++
                }
                playerState[clientNum].ingame = ingame
            }
        }

        // checks rules and updates state of the mp game
        fun Run() {
            var i: Int
            val timeLeft: Int
            var player: idPlayer?
            val gameReviewPause: Int
            assert(Game_local.gameLocal.isMultiplayer)
            assert(!Game_local.gameLocal.isClient)
            pureReady = true
            if (gameState == gameState_t.INACTIVE) {
                lastGameType = Game_local.gameLocal.gameType
                NewState(gameState_t.WARMUP)
            }
            CheckVote()
            CheckRespawns()
            if (nextState != gameState_t.INACTIVE && Game_local.gameLocal.time > nextStateSwitch) {
                NewState(nextState)
                nextState = gameState_t.INACTIVE
            }

            // don't update the ping every frame to save bandwidth
            if (Game_local.gameLocal.time > pingUpdateTime) {
                i = 0
                while (i < Game_local.gameLocal.numClients) {
                    playerState[i].ping = NetworkSystem.networkSystem.ServerGetClientPing(i)
                    i++
                }
                pingUpdateTime = Game_local.gameLocal.time + 1000
            }
            warmupText.set("")
            when (gameState) {
                gameState_t.GAMEREVIEW -> {
                    if (nextState == gameState_t.INACTIVE) {
                        gameReviewPause = CVarSystem.cvarSystem.GetCVarInteger("g_gameReviewPause")
                        nextState = gameState_t.NEXTGAME
                        nextStateSwitch = Game_local.gameLocal.time + 1000 * gameReviewPause
                    }
                }

                gameState_t.NEXTGAME -> {
                    if (nextState == gameState_t.INACTIVE) {
                        // game rotation, new map, gametype etc.
                        if (Game_local.gameLocal.NextMap()) {
                            CmdSystem.cmdSystem.BufferCommandText(cmdExecution_t.CMD_EXEC_APPEND, "serverMapRestart\n")
                            return
                        }
                        NewState(gameState_t.WARMUP)
                        if (Game_local.gameLocal.gameType == gameType_t.GAME_TOURNEY) {
                            CycleTourneyPlayers()
                        }
                        // put everyone back in from endgame spectate
                        i = 0
                        while (i < Game_local.gameLocal.numClients) {
                            val ent = Game_local.gameLocal.entities[i]
                            if (ent != null && ent is idPlayer) {
                                if (!ent.wantSpectate) {
                                    CheckRespawns(ent)
                                }
                            }
                            i++
                        }
                    }
                }

                gameState_t.WARMUP -> {
                    if (AllPlayersReady()) {
                        NewState(gameState_t.COUNTDOWN)
                        nextState = gameState_t.GAMEON
                        nextStateSwitch =
                            Game_local.gameLocal.time + 1000 * CVarSystem.cvarSystem.GetCVarInteger("g_countDown")
                    }
                    warmupText.set("Warming up.. waiting for players to get ready")
                    three = false
                    two = three
                    one = two
                }

                gameState_t.COUNTDOWN -> {
                    timeLeft = (nextStateSwitch - Game_local.gameLocal.time) / 1000 + 1
                    if (timeLeft == 3 && !three) {
                        PlayGlobalSound(-1, snd_evt_t.SND_THREE)
                        three = true
                    } else if (timeLeft == 2 && !two) {
                        PlayGlobalSound(-1, snd_evt_t.SND_TWO)
                        two = true
                    } else if (timeLeft == 1 && !one) {
                        PlayGlobalSound(-1, snd_evt_t.SND_ONE)
                        one = true
                    }
                    warmupText.set(Str.va("Match starts in %d", timeLeft))
                }

                gameState_t.GAMEON -> {
                    player = FragLimitHit()
                    if (player != null) {
                        // delay between detecting frag limit and ending game. let the death anims play
                        if (0 == fragLimitTimeout) {
                            Common.common.DPrintf("enter FragLimit timeout, player %d is leader\n", player.entityNumber)
                            fragLimitTimeout = Game_local.gameLocal.time + FRAGLIMIT_DELAY
                        }
                        if (Game_local.gameLocal.time > fragLimitTimeout) {
                            NewState(gameState_t.GAMEREVIEW, player)
                            PrintMessageEvent(-1, msg_evt_t.MSG_FRAGLIMIT, player.entityNumber)
                        }
                    } else {
                        if (fragLimitTimeout != 0) {
                            // frag limit was hit and cancelled. means the two teams got even during FRAGLIMIT_DELAY
                            // enter sudden death, the next frag leader will win
                            SuddenRespawn()
                            PrintMessageEvent(-1, msg_evt_t.MSG_HOLYSHIT)
                            fragLimitTimeout = 0
                            NewState(gameState_t.SUDDENDEATH)
                        } else if (TimeLimitHit()) {
                            player = FragLeader()
                            if (null == player) {
                                NewState(gameState_t.SUDDENDEATH)
                            } else {
                                NewState(gameState_t.GAMEREVIEW, player)
                                PrintMessageEvent(-1, msg_evt_t.MSG_TIMELIMIT)
                            }
                        }
                    }
                }

                gameState_t.SUDDENDEATH -> {
                    player = FragLeader()
                    if (player != null) {
                        if (0 == fragLimitTimeout) {
                            Common.common.DPrintf(
                                "enter sudden death FragLeader timeout, player %d is leader\n",
                                player.entityNumber
                            )
                            fragLimitTimeout = Game_local.gameLocal.time + FRAGLIMIT_DELAY
                        }
                        if (Game_local.gameLocal.time > fragLimitTimeout) {
                            NewState(gameState_t.GAMEREVIEW, player)
                            PrintMessageEvent(-1, msg_evt_t.MSG_FRAGLIMIT, player.entityNumber)
                        }
                    } else if (fragLimitTimeout != 0) {
                        SuddenRespawn()
                        PrintMessageEvent(-1, msg_evt_t.MSG_HOLYSHIT)
                        fragLimitTimeout = 0
                    }
                }

                else -> {}
            }
        }

        // draws mp hud, scoredboard, etc..
        fun Draw(clientNum: Int): Boolean {
            var player: idPlayer?
            var viewPlayer: idPlayer?

            // clear the render entities for any players that don't need
            // icons and which might not be thinking because they weren't in
            // the last snapshot.
            for (i in 0 until Game_local.gameLocal.numClients) {
                player = Game_local.gameLocal.entities[i] as? idPlayer
                if (player != null && !player.NeedsIcon()) {
                    player.HidePlayerIcons()
                }
            }
            viewPlayer = Game_local.gameLocal.entities[clientNum] as? idPlayer
            player = viewPlayer
            if (player == null) {
                return false
            }
            if (player.spectating) {
                viewPlayer = Game_local.gameLocal.entities[player.spectator] as? idPlayer
                if (viewPlayer == null) {
                    return false
                }
            }
            UpdatePlayerRanks()
            UpdateHud(viewPlayer, player.hud)
            // use the hud of the local player
            viewPlayer.playerView.RenderPlayerView(player.hud!!)
            if (currentMenu != 0) {
// if (false){
                // // uncomment this if you want to track when players are in a menu
                // if ( !bCurrentMenuMsg ) {
                // idBitMsg	outMsg;
                // byte		msgBuf[ 128 ];

                // outMsg.Init( msgBuf, sizeof( msgBuf ) );
                // outMsg.WriteByte( GAME_RELIABLE_MESSAGE_MENU );
                // outMsg.WriteBits( 1, 1 );
                // networkSystem.ClientSendReliableMessage( outMsg );
                // bCurrentMenuMsg = true;
                // }
// }
                if (player.wantSpectate) {
                    mainGui!!.SetStateString("spectext", Common.common.GetLanguageDict().GetString("#str_04249"))
                } else {
                    mainGui!!.SetStateString("spectext", Common.common.GetLanguageDict().GetString("#str_04250"))
                }
                DrawChat()
                if (currentMenu == 1) {
                    UpdateMainGui()
                    mainGui!!.Redraw(Game_local.gameLocal.time)
                } else {
                    msgmodeGui!!.Redraw(Game_local.gameLocal.time)
                }
            } else {
// if (false){
                // // uncomment this if you want to track when players are in a menu
                // if ( bCurrentMenuMsg ) {
                // idBitMsg	outMsg;
                // byte		msgBuf[ 128 ];

                // outMsg.Init( msgBuf, sizeof( msgBuf ) );
                // outMsg.WriteByte( GAME_RELIABLE_MESSAGE_MENU );
                // outMsg.WriteBits( 0, 1 );
                // networkSystem.ClientSendReliableMessage( outMsg );
                // bCurrentMenuMsg = false;
                // }
// }
                if (player.spectating) {
                    val spectatetext = arrayOf("", "")
                    var ispecline = 0
                    if (Game_local.gameLocal.gameType == gameType_t.GAME_TOURNEY) {
                        if (!player.wantSpectate) {
                            spectatetext[0] = Common.common.GetLanguageDict().GetString("#str_04246")
                            when (player.tourneyLine) {
                                0 -> spectatetext[0] += Common.common.GetLanguageDict().GetString("#str_07003")
                                1 -> spectatetext[0] += Common.common.GetLanguageDict().GetString("#str_07004")
                                2 -> spectatetext[0] += Common.common.GetLanguageDict().GetString("#str_07005")
                                else -> spectatetext[0] += Str.va(
                                    Common.common.GetLanguageDict().GetString("#str_07006"), player.tourneyLine
                                )
                            }
                            ispecline++
                        }
                    } else if (Game_local.gameLocal.gameType == gameType_t.GAME_LASTMAN) {
                        if (!player.wantSpectate) {
                            spectatetext[0] = Common.common.GetLanguageDict().GetString("#str_07007")
                            ispecline++
                        }
                    }
                    if (player.spectator != player.entityNumber) {
                        spectatetext[ispecline] = Str.va(
                            Common.common.GetLanguageDict().GetString("#str_07008"),
                            viewPlayer.GetUserInfo().GetString("ui_name")
                        )
                    } else if (0 == ispecline) {
                        spectatetext[0] = Common.common.GetLanguageDict().GetString("#str_04246")
                    }
                    spectateGui!!.SetStateString("spectatetext0", spectatetext[0])
                    spectateGui!!.SetStateString("spectatetext1", spectatetext[1])
                    if (vote != vote_flags_t.VOTE_NONE) {
                        spectateGui!!.SetStateString(
                            "vote",
                            Str.va("%s (y: %d n: %d)", voteString, yesVotes.toInt(), noVotes.toInt())
                        )
                    } else {
                        spectateGui!!.SetStateString("vote", "")
                    }
                    spectateGui!!.Redraw(Game_local.gameLocal.time)
                }
                DrawChat()
                DrawScoreBoard(player)
            }
            return true
        }

        // updates a player vote
        fun PlayerVote(clientNum: Int, vote: playerVote_t) {
            playerState[clientNum].vote = vote
        }

        // updates frag counts and potentially ends the match in sudden death
        fun PlayerDeath(dead: idPlayer, killer: idPlayer?, telefrag: Boolean) {

            // don't do PrintMessageEvent and shit
            assert(!Game_local.gameLocal.isClient)
            if (killer != null) {
                if (Game_local.gameLocal.gameType == gameType_t.GAME_LASTMAN) {
                    playerState[dead.entityNumber].fragCount--
                } else if (Game_local.gameLocal.gameType == gameType_t.GAME_TDM) {
                    if (killer === dead || killer.team == dead.team) {
                        // suicide or teamkill
                        TeamScore(killer.entityNumber, killer.team, -1)
                    } else {
                        TeamScore(killer.entityNumber, killer.team, +1)
                    }
                } else {
                    playerState[killer.entityNumber].fragCount += if (killer === dead) -1 else 1
                }
            }
            if (killer != null && killer == dead) {
                PrintMessageEvent(-1, msg_evt_t.MSG_SUICIDE, dead.entityNumber)
            } else if (killer != null) {
                if (telefrag) {
                    PrintMessageEvent(-1, msg_evt_t.MSG_TELEFRAGGED, dead.entityNumber, killer.entityNumber)
                } else if (Game_local.gameLocal.gameType == gameType_t.GAME_TDM && dead.team == killer.team) {
                    PrintMessageEvent(-1, msg_evt_t.MSG_KILLEDTEAM, dead.entityNumber, killer.entityNumber)
                } else {
                    PrintMessageEvent(-1, msg_evt_t.MSG_KILLED, dead.entityNumber, killer.entityNumber)
                }
            } else {
                PrintMessageEvent(-1, msg_evt_t.MSG_DIED, dead.entityNumber)
                playerState[dead.entityNumber].fragCount--
            }
        }

        fun AddChatLine(fmt: String, vararg objects: Any?) { //id_attribute((format(printf,2,3)));
            val temp: idStr
            //            va_list argptr;
//
//            va_start(argptr, fmt);
//            vsprintf(temp, fmt, argptr);
//            va_end(argptr);
            temp = idStr(String.format(fmt, *objects))
            Game_local.gameLocal.Printf("%s\n", temp.toString())
            chatHistory[chatHistoryIndex % NUM_CHAT_NOTIFY].line = temp
            chatHistory[chatHistoryIndex % NUM_CHAT_NOTIFY].fade = 6
            chatHistoryIndex++
            if (chatHistorySize < NUM_CHAT_NOTIFY) {
                chatHistorySize++
            }
            chatDataUpdated = true
            lastChatLineTime = Game_local.gameLocal.time
        }

        fun UpdateMainGui() {
            var i: Int
            val mainGui = mainGui!!
            mainGui.SetStateInt(
                "readyon",
                if (gameState == gameState_t.WARMUP) 1 else 0
            )
            mainGui.SetStateInt(
                "readyoff",
                if (gameState != gameState_t.WARMUP) 1 else 0
            )
            //	idStr strReady = cvarSystem.GetCVarString( "ui_ready" );
            var strReady = CVarSystem.cvarSystem.GetCVarString("ui_ready")
            strReady = if (strReady.equals("ready", ignoreCase = true)) {
                Common.common.GetLanguageDict().GetString("#str_04248")
            } else {
                Common.common.GetLanguageDict().GetString("#str_04247")
            }
            mainGui.SetStateString("ui_ready", strReady)
            mainGui.SetStateInt("teamon", if (Game_local.gameLocal.gameType == gameType_t.GAME_TDM) 1 else 0)
            mainGui.SetStateInt("teamoff", if (Game_local.gameLocal.gameType != gameType_t.GAME_TDM) 1 else 0)
            if (Game_local.gameLocal.gameType == gameType_t.GAME_TDM) {
                val p = Game_local.gameLocal.GetClientByNum(Game_local.gameLocal.localClientNum)!!
                mainGui.SetStateInt("team", p.team)
            }
            // setup vote
            mainGui.SetStateInt("voteon", if (vote != vote_flags_t.VOTE_NONE && !voted) 1 else 0)
            mainGui.SetStateInt("voteoff", if (vote != vote_flags_t.VOTE_NONE && !voted) 0 else 1)
            // last man hack
            mainGui.SetStateInt("isLastMan", if (Game_local.gameLocal.gameType == gameType_t.GAME_LASTMAN) 1 else 0)
            // send the current serverinfo values
            i = 0
            while (i < Game_local.gameLocal.serverInfo.GetNumKeyVals()) {
                val keyval = Game_local.gameLocal.serverInfo.GetKeyVal(i)!!
                mainGui.SetStateString(keyval.GetKey().toString(), keyval.GetValue().toString())
                i++
            }
            mainGui.StateChanged(Game_local.gameLocal.time)
            if (BuildDefines.__linux__) {
                // replacing the oh-so-useful s_reverse with sound backend prompt
                mainGui.SetStateString("driver_prompt", "1")
            } else {
                mainGui.SetStateString("driver_prompt", "0")
            }
        }

        fun StartMenu(): idUserInterface? {
            if (mainGui == null) {
                return null
            }
            var i: Int
            var j: Int
            val mainGui = mainGui!!
            if (currentMenu != 0) {
                currentMenu = 0
                CVarSystem.cvarSystem.SetCVarBool("ui_chat", false)
            } else {
                currentMenu = if (nextMenu >= 2) {
                    nextMenu
                } else {
                    // for default and explicit
                    1
                }
                CVarSystem.cvarSystem.SetCVarBool("ui_chat", true)
            }
            nextMenu = 0
            Game_local.gameLocal.sessionCommand.set("") // in case we used "game_startMenu" to trigger the menu
            if (currentMenu == 1) {
                UpdateMainGui()

                // UpdateMainGui sets most things, but it doesn't set these because
                // it'd be pointless and/or harmful to set them every frame (for various reasons)
                // Currenty the gui doesn't update properly if they change anyway, so we'll leave it like this.
                // setup callvote
                if (vote == vote_flags_t.VOTE_NONE) {
                    var callvote_ok = false
                    i = 0
                    while (i < vote_flags_t.VOTE_COUNT.ordinal) {

                        // flag on means vote is denied, so default value 0 means all votes and -1 disables
                        mainGui.SetStateInt(
                            Str.va("vote%d", i),
                            if (TempDump.itob(SysCvar.g_voteFlags.GetInteger() and (1 shl i))) 0 else 1
                        )
                        if ((SysCvar.g_voteFlags.GetInteger() and (1 shl i)) == 0) {
                            callvote_ok = true
                        }
                        i++
                    }
                    mainGui.SetStateInt("callvote", TempDump.btoi(callvote_ok))
                } else {
                    mainGui.SetStateInt("callvote", 2)
                }

                // player kick data
                var kickList = ""
                j = 0
                i = 0
                while (i < Game_local.gameLocal.numClients) {
                    if (Game_local.gameLocal.entities[i] != null && Game_local.gameLocal.entities[i] is idPlayer) {
                        if (!kickList.isEmpty()) {
                            kickList += ";"
                        }
                        kickList += Str.va("\"%d - %s\"", i, Game_local.gameLocal.userInfo[i].GetString("ui_name"))
                        kickVoteMap[j] = i
                        j++
                    }
                    i++
                }
                mainGui.SetStateString("kickChoices", kickList)
                mainGui.SetStateString("chattext", "")
                mainGui.Activate(true, Game_local.gameLocal.time)
                return mainGui
            } else if (currentMenu == 2) {
                // the setup is done in MessageMode
                msgmodeGui!!.Activate(true, Game_local.gameLocal.time)
                CVarSystem.cvarSystem.SetCVarBool("ui_chat", true)
                return msgmodeGui
            }
            return null
        }

        fun HandleGuiCommands(_menuCommand: String): String? {
            val currentGui: idUserInterface?
            val voteValue: String?
            val vote_clientNum: Int
            var icmd: Int
            val args = CmdArgs.idCmdArgs()
            if (_menuCommand.isEmpty()) {
                Common.common.Printf("idMultiplayerGame::HandleGuiCommands: empty command\n")
                return "continue"
            }
            assert(currentMenu != 0)
            currentGui = if (currentMenu == 1) {
                mainGui
            } else {
                msgmodeGui
            }
            args.TokenizeString(_menuCommand, false)
            icmd = 0
            while (icmd < args.Argc()) {
                val cmd = args.Argv(icmd++)
                if (0 == idStr.Icmp(cmd, ";")) {
                    continue
                } else if (0 == idStr.Icmp(cmd, "video")) {
                    var vcmd = ""
                    if (args.Argc() - icmd >= 1) {
                        vcmd = args.Argv(icmd++)
                    }
                    val oldSpec = CVarSystem.cvarSystem.GetCVarInteger("com_machineSpec")
                    if (idStr.Icmp(vcmd, "low") == 0) {
                        CVarSystem.cvarSystem.SetCVarInteger("com_machineSpec", 0)
                    } else if (idStr.Icmp(vcmd, "medium") == 0) {
                        CVarSystem.cvarSystem.SetCVarInteger("com_machineSpec", 1)
                    } else if (idStr.Icmp(vcmd, "high") == 0) {
                        CVarSystem.cvarSystem.SetCVarInteger("com_machineSpec", 2)
                    } else if (idStr.Icmp(vcmd, "ultra") == 0) {
                        CVarSystem.cvarSystem.SetCVarInteger("com_machineSpec", 3)
                    } else if (idStr.Icmp(vcmd, "recommended") == 0) {
                        CmdSystem.cmdSystem.BufferCommandText(cmdExecution_t.CMD_EXEC_NOW, "setMachineSpec\n")
                    }
                    if (oldSpec != CVarSystem.cvarSystem.GetCVarInteger("com_machineSpec")) {
                        currentGui!!.SetStateInt(
                            "com_machineSpec",
                            CVarSystem.cvarSystem.GetCVarInteger("com_machineSpec")
                        )
                        currentGui.StateChanged(Game_local.gameLocal.realClientTime)
                        CmdSystem.cmdSystem.BufferCommandText(cmdExecution_t.CMD_EXEC_NOW, "execMachineSpec\n")
                    }
                    if (idStr.Icmp(vcmd, "restart") == 0) {
                        CmdSystem.cmdSystem.BufferCommandText(cmdExecution_t.CMD_EXEC_APPEND, "vid_restart\n")
                    }
                    continue
                } else if (0 == idStr.Icmp(cmd, "play")) {
                    if (args.Argc() - icmd >= 1) {
                        var snd = args.Argv(icmd++)
                        var channel = 1
                        if (snd.length == 1) {
                            channel = snd.toInt()
                            snd = args.Argv(icmd++)
                        }
                        Game_local.gameSoundWorld!!.PlayShaderDirectly(snd, channel)
                    }
                    continue
                } else if (0 == idStr.Icmp(cmd, "mpSkin")) {
                    var skin: String?
                    if (args.Argc() - icmd >= 1) {
                        skin = args.Argv(icmd++)
                        CVarSystem.cvarSystem.SetCVarString("ui_skin", skin)
                    }
                    SetMenuSkin()
                    continue
                } else if (0 == idStr.Icmp(cmd, "quit")) {
                    CmdSystem.cmdSystem.BufferCommandText(cmdExecution_t.CMD_EXEC_APPEND, "quit\n")
                    return null
                } else if (0 == idStr.Icmp(cmd, "disconnect")) {
                    CmdSystem.cmdSystem.BufferCommandText(cmdExecution_t.CMD_EXEC_APPEND, "disconnect\n")
                    return null
                } else if (0 == idStr.Icmp(cmd, "close")) {
                    DisableMenu()
                    return null
                } else if (0 == idStr.Icmp(cmd, "spectate")) {
                    ToggleSpectate()
                    DisableMenu()
                    return null
                } else if (0 == idStr.Icmp(cmd, "chatmessage")) {
                    val mode = currentGui!!.State().GetInt("messagemode")
                    if (mode != 0) {
                        CmdSystem.cmdSystem.BufferCommandText(
                            cmdExecution_t.CMD_EXEC_NOW,
                            Str.va("sayTeam \"%s\"", currentGui.State().GetString("chattext"))
                        )
                    } else {
                        CmdSystem.cmdSystem.BufferCommandText(
                            cmdExecution_t.CMD_EXEC_NOW,
                            Str.va("say \"%s\"", currentGui.State().GetString("chattext"))
                        )
                    }
                    currentGui.SetStateString("chattext", "")
                    return if (currentMenu == 1) {
                        "continue"
                    } else {
                        DisableMenu()
                        null
                    }
                } else if (0 == idStr.Icmp(cmd, "readytoggle")) {
                    ToggleReady()
                    DisableMenu()
                    return null
                } else if (0 == idStr.Icmp(cmd, "teamtoggle")) {
                    ToggleTeam()
                    DisableMenu()
                    return null
                } else if (0 == idStr.Icmp(cmd, "callVote")) {
                    val voteIndex: vote_flags_t =
                        vote_flags_t.entries[mainGui!!.State().GetInt("voteIndex")]
                    if (voteIndex == vote_flags_t.VOTE_MAP) {
                        val mapNum = mapList!!.GetSelection(null, 0)
                        if (mapNum >= 0) {
                            val dict = FileSystem_h.fileSystem.GetMapDecl(mapNum)
                            if (dict != null) {
                                ClientCallVote(vote_flags_t.VOTE_MAP, dict.GetString("path"))
                            }
                        }
                    } else {
                        voteValue = mainGui!!.State().GetString("str_voteValue")
                        if (voteIndex == vote_flags_t.VOTE_KICK) {
                            vote_clientNum = kickVoteMap[voteValue.toInt()]
                            ClientCallVote(voteIndex, Str.va("%d", vote_clientNum))
                        } else {
                            ClientCallVote(voteIndex, voteValue)
                        }
                    }
                    DisableMenu()
                    return null
                } else if (0 == idStr.Icmp(cmd, "voteyes")) {
                    CastVote(Game_local.gameLocal.localClientNum, true)
                    DisableMenu()
                    return null
                } else if (0 == idStr.Icmp(cmd, "voteno")) {
                    CastVote(Game_local.gameLocal.localClientNum, false)
                    DisableMenu()
                    return null
                } else if (0 == idStr.Icmp(cmd, "bind")) {
                    if (args.Argc() - icmd >= 2) {
                        val key = args.Argv(icmd++)
                        val bind = args.Argv(icmd++)
                        CmdSystem.cmdSystem.BufferCommandText(
                            cmdExecution_t.CMD_EXEC_NOW,
                            Str.va("bindunbindtwo \"%s\" \"%s\"", key, bind)
                        )
                        mainGui!!.SetKeyBindingNames()
                    }
                    continue
                } else if (0 == idStr.Icmp(cmd, "clearbind")) {
                    if (args.Argc() - icmd >= 1) {
                        val bind = args.Argv(icmd++)
                        CmdSystem.cmdSystem.BufferCommandText(
                            cmdExecution_t.CMD_EXEC_NOW,
                            Str.va("unbind \"%s\"", bind)
                        )
                        mainGui!!.SetKeyBindingNames()
                    }
                    continue
                } else if (0 == idStr.Icmp(cmd, "MAPScan")) {
                    val gametype = Game_local.gameLocal.serverInfo.GetString("si_gameType")
                    if (gametype == null || gametype.isEmpty() || idStr.Icmp(
                            gametype,
                            "singleplayer"
                        ) == 0
                    ) {
//                        gametype = "Deathmatch";
                        Game_local.gameLocal.serverInfo.Set(
                            "si_gameType",
                            "Deathmatch"
                        ) //TODO:double check that this actually works.
                    }
                    var i: Int
                    val num: Int
                    val si_map = Game_local.gameLocal.serverInfo.GetString("si_map")
                    var dict: idDict?
                    mapList!!.Clear()
                    mapList!!.SetSelection(-1)
                    num = FileSystem_h.fileSystem.GetNumMaps()
                    i = 0
                    while (i < num) {
                        dict = FileSystem_h.fileSystem.GetMapDecl(i)
                        if (dict != null) {
                            // any MP gametype supported
                            var isMP = false
                            var igt = gameType_t.GAME_SP.ordinal + 1
                            while (SysCvar.si_gameTypeArgs[igt] != null) {
                                if (dict.GetBool(SysCvar.si_gameTypeArgs[igt]!!)) {
                                    isMP = true
                                    break
                                }
                                igt++
                            }
                            if (isMP) {
                                var mapName = dict.GetString("name")
                                if (mapName.isEmpty()) {
                                    mapName = dict.GetString("path")
                                }
                                mapName = Common.common.GetLanguageDict().GetString(mapName)
                                mapList!!.Add(i, idStr(mapName))
                                if (si_map == dict.GetString("path")) {
                                    mapList!!.SetSelection(mapList!!.Num() - 1)
                                }
                            }
                        }
                        i++
                    }
                    // set the current level shot
                    SetMapShot()
                    return "continue"
                } else if (0 == idStr.Icmp(cmd, "click_maplist")) {
                    SetMapShot()
                    return "continue"
                } else if (cmd.startsWith("sound")) {
                    // pass that back to the core, will know what to do with it
                    return _menuCommand
                }
                Common.common.Printf("idMultiplayerGame::HandleGuiCommands: '%s'	unknown\n", cmd)
            }
            return "continue"
        }

        fun SetMenuSkin() {
            // skins
            var str = CVarSystem.cvarSystem.GetCVarString("mod_validSkins")
            val uiSkin = CVarSystem.cvarSystem.GetCVarString("ui_skin")
            var skin: String?
            var skinId = 1
            var count = 1
            while (str.length != 0) {
                val n = str.indexOf(";")
                if (n >= 0) {
                    skin = str.substring(0, n)
                    str = str.substring(n + 1)
                } else {
                    skin = str
                    str = ""
                }
                if (skin.equals(uiSkin, ignoreCase = true)) {
                    skinId = count
                }
                count++
            }
            for (i in 0 until count) {
                mainGui!!.SetStateInt(Str.va("skin%d", i + 1), 0)
            }
            mainGui!!.SetStateInt(Str.va("skin%d", skinId), 1)
        }

        fun WriteToSnapshot(msg: idBitMsgDelta) {
            var i: Int
            var value: Int
            msg.WriteByte(TempDump.etoi(gameState))
            msg.WriteShort(currentTourneyPlayer[0])
            msg.WriteShort(currentTourneyPlayer[1])
            i = 0
            while (i < Game_local.MAX_CLIENTS) {

                // clamp all values to min/max possible value that we can send over
                value = idMath.ClampInt(
                    MP_PLAYER_MINFRAGS,
                    MP_PLAYER_MAXFRAGS,
                    playerState[i].fragCount
                )
                msg.WriteBits(value, ASYNC_PLAYER_FRAG_BITS)
                value = idMath.ClampInt(
                    MP_PLAYER_MINFRAGS,
                    MP_PLAYER_MAXFRAGS,
                    playerState[i].teamFragCount
                )
                msg.WriteBits(value, ASYNC_PLAYER_FRAG_BITS)
                value = idMath.ClampInt(0, MP_PLAYER_MAXWINS, playerState[i].wins)
                msg.WriteBits(value, ASYNC_PLAYER_WINS_BITS)
                value = idMath.ClampInt(0, MP_PLAYER_MAXPING, playerState[i].ping)
                msg.WriteBits(value, ASYNC_PLAYER_PING_BITS)
                msg.WriteBits(TempDump.btoi(playerState[i].ingame), 1)
                i++
            }
        }

        fun ReadFromSnapshot(msg: idBitMsgDelta) {
            var i: Int
            val newState: gameState_t
            newState = gameState_t.entries.toTypedArray()[msg.ReadByte()]
            if (newState != gameState) {
                Game_local.gameLocal.DPrintf(
                    "%s . %s\n",
                    GameStateStrings[gameState.ordinal],
                    GameStateStrings[newState.ordinal]
                )
                gameState = newState
                // these could be gathered in a BGNewState() kind of thing, as we have to do them in NewState as well
                if (gameState == gameState_t.GAMEON) {
                    matchStartedTime = Game_local.gameLocal.time
                    CVarSystem.cvarSystem.SetCVarString("ui_ready", "Not Ready")
                    switchThrottle[1] = 0 // passby the throttle
                    startFragLimit = Game_local.gameLocal.serverInfo.GetInt("si_fragLimit")
                }
            }
            currentTourneyPlayer[0] = msg.ReadShort()
            currentTourneyPlayer[1] = msg.ReadShort()
            i = 0
            while (i < Game_local.MAX_CLIENTS) {
                playerState[i].fragCount = msg.ReadBits(ASYNC_PLAYER_FRAG_BITS)
                playerState[i].teamFragCount = msg.ReadBits(ASYNC_PLAYER_FRAG_BITS)
                playerState[i].wins = msg.ReadBits(ASYNC_PLAYER_WINS_BITS)
                playerState[i].ping = msg.ReadBits(ASYNC_PLAYER_PING_BITS)
                playerState[i].ingame = msg.ReadBits(1) != 0
                i++
            }
        }

        fun GetGameState(): gameState_t {
            return gameState
        }


        fun PlayGlobalSound(to: Int, evt: snd_evt_t, shader: String? = null /*= NULL*/) {
            val shaderDecl: idSoundShader?
            if (to == -1 || to == Game_local.gameLocal.localClientNum) {
                if (shader != null) {
                    Game_local.gameSoundWorld!!.PlayShaderDirectly(shader)
                } else {
                    Game_local.gameSoundWorld!!.PlayShaderDirectly(GlobalSoundStrings[TempDump.etoi(evt)])
                }
            }
            if (!Game_local.gameLocal.isClient) {
                val outMsg = idBitMsg()
                val msgBuf = ByteBuffer.allocate(1024)
                outMsg.Init(msgBuf, msgBuf.capacity())
                if (shader != null) {
                    shaderDecl = DeclManager.declManager.FindSound(shader)
                    if (null == shaderDecl) {
                        return
                    }
                    outMsg.WriteByte(Game_local.GAME_RELIABLE_MESSAGE_SOUND_INDEX.toByte())
                    outMsg.WriteLong(
                        Game_local.gameLocal.ServerRemapDecl(
                            to,
                            declType_t.DECL_SOUND,
                            shaderDecl.Index()
                        )
                    )
                } else {
                    outMsg.WriteByte(Game_local.GAME_RELIABLE_MESSAGE_SOUND_EVENT.toByte())
                    outMsg.WriteByte(evt.ordinal.toByte())
                }
                NetworkSystem.networkSystem.ServerSendReliableMessage(to, outMsg)
            }
        }


        fun PrintMessageEvent(to: Int, evt: msg_evt_t?, parm1: Int = -1 /*= -1*/, parm2: Int = -1 /*= -1*/) {
            when (evt) {
                msg_evt_t.MSG_SUICIDE -> {
                    assert(parm1 >= 0)
                    AddChatLine(
                        Common.common.GetLanguageDict().GetString("#str_04293"),
                        Game_local.gameLocal.userInfo[parm1].GetString("ui_name")
                    )
                }

                msg_evt_t.MSG_KILLED -> {
                    assert(parm1 >= 0 && parm2 >= 0)
                    AddChatLine(
                        Common.common.GetLanguageDict().GetString("#str_04292"),
                        Game_local.gameLocal.userInfo[parm1].GetString("ui_name"),
                        Game_local.gameLocal.userInfo[parm2].GetString("ui_name")
                    )
                }

                msg_evt_t.MSG_KILLEDTEAM -> {
                    assert(parm1 >= 0 && parm2 >= 0)
                    AddChatLine(
                        Common.common.GetLanguageDict().GetString("#str_04291"),
                        Game_local.gameLocal.userInfo[parm1].GetString("ui_name"),
                        Game_local.gameLocal.userInfo[parm2].GetString("ui_name")
                    )
                }

                msg_evt_t.MSG_TELEFRAGGED -> {
                    assert(parm1 >= 0 && parm2 >= 0)
                    AddChatLine(
                        Common.common.GetLanguageDict().GetString("#str_04290"),
                        Game_local.gameLocal.userInfo[parm1].GetString("ui_name"),
                        Game_local.gameLocal.userInfo[parm2].GetString("ui_name")
                    )
                }

                msg_evt_t.MSG_DIED -> {
                    assert(parm1 >= 0)
                    AddChatLine(
                        Common.common.GetLanguageDict().GetString("#str_04289"),
                        Game_local.gameLocal.userInfo[parm1].GetString("ui_name")
                    )
                }

                msg_evt_t.MSG_VOTE -> AddChatLine(Common.common.GetLanguageDict().GetString("#str_04288"))
                msg_evt_t.MSG_SUDDENDEATH -> AddChatLine(Common.common.GetLanguageDict().GetString("#str_04287"))
                msg_evt_t.MSG_FORCEREADY -> {
                    AddChatLine(
                        Common.common.GetLanguageDict().GetString("#str_04286"),
                        Game_local.gameLocal.userInfo[parm1].GetString("ui_name")
                    )
                    if (Game_local.gameLocal.entities[parm1] != null && Game_local.gameLocal.entities[parm1] is idPlayer) {
                        (Game_local.gameLocal.entities[parm1] as idPlayer).forcedReady = true
                    }
                }

                msg_evt_t.MSG_JOINEDSPEC -> AddChatLine(
                    Common.common.GetLanguageDict().GetString("#str_04285"),
                    Game_local.gameLocal.userInfo[parm1].GetString("ui_name")
                )

                msg_evt_t.MSG_TIMELIMIT -> AddChatLine(Common.common.GetLanguageDict().GetString("#str_04284"))
                msg_evt_t.MSG_FRAGLIMIT -> if (Game_local.gameLocal.gameType == gameType_t.GAME_LASTMAN) {
                    AddChatLine(
                        Common.common.GetLanguageDict().GetString("#str_04283"),
                        Game_local.gameLocal.userInfo[parm1].GetString("ui_name")
                    )
                } else if (Game_local.gameLocal.gameType == gameType_t.GAME_TDM) {
                    AddChatLine(
                        Common.common.GetLanguageDict().GetString("#str_04282"),
                        Game_local.gameLocal.userInfo[parm1].GetString("ui_team")
                    )
                } else {
                    AddChatLine(
                        Common.common.GetLanguageDict().GetString("#str_04281"),
                        Game_local.gameLocal.userInfo[parm1].GetString("ui_name")
                    )
                }

                msg_evt_t.MSG_JOINTEAM -> AddChatLine(
                    Common.common.GetLanguageDict().GetString("#str_04280"),
                    Game_local.gameLocal.userInfo[parm1].GetString("ui_name"),
                    if (parm2 != 0) Common.common.GetLanguageDict()
                        .GetString("#str_02500") else Common.common.GetLanguageDict().GetString("#str_02499")
                )

                msg_evt_t.MSG_HOLYSHIT -> AddChatLine(Common.common.GetLanguageDict().GetString("#str_06732"))
                else -> {
                    Game_local.gameLocal.DPrintf("PrintMessageEvent: unknown message type %d\n", evt)
                    return
                }
            }
            if (!Game_local.gameLocal.isClient) {
                val outMsg = idBitMsg()
                val msgBuf = ByteBuffer.allocate(1024)
                outMsg.Init(msgBuf, msgBuf.capacity())
                outMsg.WriteByte(Game_local.GAME_RELIABLE_MESSAGE_DB.toByte())
                outMsg.WriteByte(evt.ordinal.toByte())
                outMsg.WriteByte(parm1.toByte())
                outMsg.WriteByte(parm2.toByte())
                NetworkSystem.networkSystem.ServerSendReliableMessage(to, outMsg)
            }
        }

        fun DisconnectClient(clientNum: Int) {
            if (lastWinner == clientNum) {
                lastWinner = -1
            }
            UpdatePlayerRanks()
            CheckAbortGame()
        }

        fun ClientCallVote(voteIndex: vote_flags_t, voteValue: String) {
            val outMsg = idBitMsg()
            val msgBuf = ByteBuffer.allocate(Game_local.MAX_GAME_MESSAGE_SIZE)

            // send
            outMsg.Init(msgBuf, Game_local.MAX_GAME_MESSAGE_SIZE)
            outMsg.WriteByte(Game_local.GAME_RELIABLE_MESSAGE_CALLVOTE.toByte())
            outMsg.WriteByte(voteIndex.ordinal.toByte())
            outMsg.WriteString(voteValue)
            NetworkSystem.networkSystem.ClientSendReliableMessage(outMsg)
        }

        fun ServerCallVote(clientNum: Int, msg: idBitMsg) {
            val voteIndex: vote_flags_t
            val vote_timeLimit: Long
            val vote_fragLimit: Long
            val vote_clientNum: Long
            val vote_gameTypeIndex: Long //, vote_kickIndex;
            var value: String?
            val value2 = CharArray(MAX_STRING_CHARS)
            assert(clientNum != -1)
            assert(!Game_local.gameLocal.isClient)
            voteIndex = vote_flags_t.entries.toTypedArray()[msg.ReadByte().toInt()]
            msg.ReadString(value2, MAX_STRING_CHARS)
            value = TempDump.ctos(value2)

            // sanity checks - setup the vote
            if (vote != vote_flags_t.VOTE_NONE) {
                Game_local.gameLocal.ServerSendChatMessage(
                    clientNum,
                    "server",
                    Common.common.GetLanguageDict().GetString("#str_04273")
                )
                Common.common.DPrintf("client %d: called vote while voting already in progress - ignored\n", clientNum)
                return
            }
            when (voteIndex) {
                vote_flags_t.VOTE_RESTART -> {
                    ServerStartVote(clientNum, voteIndex, "")
                    ClientStartVote(clientNum, Common.common.GetLanguageDict().GetString("#str_04271"))
                }

                vote_flags_t.VOTE_NEXTMAP -> {
                    ServerStartVote(clientNum, voteIndex, "")
                    ClientStartVote(clientNum, Common.common.GetLanguageDict().GetString("#str_04272"))
                }

                vote_flags_t.VOTE_TIMELIMIT -> {
                    vote_timeLimit = value.toLong(10)
                    if (vote_timeLimit == Game_local.gameLocal.serverInfo.GetInt("si_timeLimit").toLong()) {
                        Game_local.gameLocal.ServerSendChatMessage(
                            clientNum,
                            "server",
                            Common.common.GetLanguageDict().GetString("#str_04270")
                        )
                        Common.common.DPrintf("client %d: already at the voted Time Limit\n", clientNum)
                        return
                    }
                    if (vote_timeLimit < SysCvar.si_timeLimit.GetMinValue() || vote_timeLimit > SysCvar.si_timeLimit.GetMaxValue()) {
                        Game_local.gameLocal.ServerSendChatMessage(
                            clientNum,
                            "server",
                            Common.common.GetLanguageDict().GetString("#str_04269")
                        )
                        Common.common.DPrintf(
                            "client %d: timelimit value out of range for vote: %s\n",
                            clientNum,
                            value
                        )
                        return
                    }
                    ServerStartVote(clientNum, voteIndex, value)
                    ClientStartVote(
                        clientNum,
                        Str.va(Common.common.GetLanguageDict().GetString("#str_04268"), vote_timeLimit)
                    )
                }

                vote_flags_t.VOTE_FRAGLIMIT -> {
                    vote_fragLimit = value.toLong(10)
                    if (vote_fragLimit == Game_local.gameLocal.serverInfo.GetInt("si_fragLimit").toLong()) {
                        Game_local.gameLocal.ServerSendChatMessage(
                            clientNum,
                            "server",
                            Common.common.GetLanguageDict().GetString("#str_04267")
                        )
                        Common.common.DPrintf("client %d: already at the voted Frag Limit\n", clientNum)
                        return
                    }
                    if (vote_fragLimit < SysCvar.si_fragLimit.GetMinValue() || vote_fragLimit > SysCvar.si_fragLimit.GetMaxValue()) {
                        Game_local.gameLocal.ServerSendChatMessage(
                            clientNum,
                            "server",
                            Common.common.GetLanguageDict().GetString("#str_04266")
                        )
                        Common.common.DPrintf(
                            "client %d: fraglimit value out of range for vote: %s\n",
                            clientNum,
                            value
                        )
                        return
                    }
                    ServerStartVote(clientNum, voteIndex, value)
                    ClientStartVote(
                        clientNum,
                        Str.va(
                            Common.common.GetLanguageDict().GetString("#str_04303"),
                            if (Game_local.gameLocal.gameType == gameType_t.GAME_LASTMAN) Common.common.GetLanguageDict()
                                .GetString("#str_04264") else Common.common.GetLanguageDict().GetString("#str_04265"),
                            vote_fragLimit
                        )
                    )
                }

                vote_flags_t.VOTE_GAMETYPE -> {
                    vote_gameTypeIndex = value.toLong(10)
                    assert(vote_gameTypeIndex >= 0 && vote_gameTypeIndex <= 3)
                    when (vote_gameTypeIndex.toInt()) {
                        0 -> value = "Deathmatch"
                        1 -> value = "Tourney"
                        2 -> value = "Team DM"
                        3 -> value = "Last Man"
                    }
                    if (
                        idStr.Icmp(
                            value,
                            Game_local.gameLocal.serverInfo.GetString("si_gameType")
                        ) == 0

                    ) {
                        Game_local.gameLocal.ServerSendChatMessage(
                            clientNum,
                            "server",
                            Common.common.GetLanguageDict().GetString("#str_04259")
                        )
                        Common.common.DPrintf("client %d: already at the voted Game Type\n", clientNum)
                        return
                    }
                    ServerStartVote(clientNum, voteIndex, value)
                    ClientStartVote(clientNum, Str.va(Common.common.GetLanguageDict().GetString("#str_04258"), value))
                }

                vote_flags_t.VOTE_KICK -> {
                    vote_clientNum = value.toLong(10)
                    if (vote_clientNum == Game_local.gameLocal.localClientNum.toLong()) {
                        Game_local.gameLocal.ServerSendChatMessage(
                            clientNum,
                            "server",
                            Common.common.GetLanguageDict().GetString("#str_04257")
                        )
                        Common.common.DPrintf("client %d: called kick for the server host\n", clientNum)
                        return
                    }
                    ServerStartVote(clientNum, voteIndex, Str.va("%d", vote_clientNum))
                    ClientStartVote(
                        clientNum,
                        Str.va(
                            Common.common.GetLanguageDict().GetString("#str_04302"),
                            vote_clientNum,
                            Game_local.gameLocal.userInfo[vote_clientNum.toInt()].GetString("ui_name")
                        )
                    )
                }

                vote_flags_t.VOTE_MAP -> {
                    if (FindText(Game_local.gameLocal.serverInfo.GetString("si_map"), value) != -1) {
                        Game_local.gameLocal.ServerSendChatMessage(
                            clientNum,
                            "server",
                            Str.va(Common.common.GetLanguageDict().GetString("#str_04295"), value)
                        )
                        Common.common.DPrintf("client %d: already running the voted map: %s\n", clientNum, value)
                        return
                    }
                    val num = FileSystem_h.fileSystem.GetNumMaps()
                    var i: Int
                    var dict: idDict? = null
                    var haveMap = false
                    i = 0
                    while (i < num) {
                        dict = FileSystem_h.fileSystem.GetMapDecl(i)
                        if (dict != null &&
                            idStr.Icmp(dict.GetString("path"), value) == 0
                        ) {
                            haveMap = true
                            break
                        }
                        i++
                    }
                    if (!haveMap) {
                        Game_local.gameLocal.ServerSendChatMessage(
                            clientNum,
                            "server",
                            Str.va(Common.common.GetLanguageDict().GetString("#str_04296"), value)
                        )
                        Common.common.Printf("client %d: map not found: %s\n", clientNum, value)
                        return
                    }
                    ServerStartVote(clientNum, voteIndex, value)
                    ClientStartVote(
                        clientNum,
                        Str.va(
                            Common.common.GetLanguageDict().GetString("#str_04256"),
                            Common.common.GetLanguageDict()
                                .GetString(if (dict != null) dict.GetString("name") else value)
                        )
                    )
                }

                vote_flags_t.VOTE_SPECTATORS -> if (Game_local.gameLocal.serverInfo.GetBool("si_spectators")) {
                    ServerStartVote(clientNum, voteIndex, "")
                    ClientStartVote(clientNum, Common.common.GetLanguageDict().GetString("#str_04255"))
                } else {
                    ServerStartVote(clientNum, voteIndex, "")
                    ClientStartVote(clientNum, Common.common.GetLanguageDict().GetString("#str_04254"))
                }

                else -> {
                    Game_local.gameLocal.ServerSendChatMessage(
                        clientNum,
                        "server",
                        Str.va(Common.common.GetLanguageDict().GetString("#str_04297"), voteIndex.ordinal)
                    )
                    Common.common.DPrintf("client %d: unknown vote index %d\n", clientNum, voteIndex)
                }
            }
        }

        fun ClientStartVote(clientNum: Int, _voteString: String) {
            val outMsg = idBitMsg()
            val msgBuf: ByteBuffer?
            if (!Game_local.gameLocal.isClient) {
                msgBuf = ByteBuffer.allocate(Game_local.MAX_GAME_MESSAGE_SIZE)
                outMsg.Init(msgBuf, Game_local.MAX_GAME_MESSAGE_SIZE)
                outMsg.WriteByte(Game_local.GAME_RELIABLE_MESSAGE_STARTVOTE.toByte())
                outMsg.WriteByte(clientNum.toByte())
                outMsg.WriteString(_voteString)
                NetworkSystem.networkSystem.ServerSendReliableMessage(-1, outMsg)
            }
            voteString.set(_voteString)
            AddChatLine(
                Str.va(
                    Common.common.GetLanguageDict().GetString("#str_04279"),
                    Game_local.gameLocal.userInfo[clientNum].GetString("ui_name")
                )
            )
            Game_local.gameSoundWorld!!.PlayShaderDirectly(GlobalSoundStrings[TempDump.etoi(snd_evt_t.SND_VOTE)])
            voted = clientNum == Game_local.gameLocal.localClientNum
            if (Game_local.gameLocal.isClient) {
                // the the vote value to something so the vote line is displayed
                vote = vote_flags_t.VOTE_RESTART
                yesVotes = 1f
                noVotes = 0f
            }
        }

        fun ServerStartVote(clientNum: Int, voteIndex: vote_flags_t, voteValue: String) {
            var i: Int
            assert(vote == vote_flags_t.VOTE_NONE)

            // setup
            yesVotes = 1f
            noVotes = 0f
            vote = voteIndex
            this.voteValue.set(voteValue)
            voteTimeOut = Game_local.gameLocal.time + 20000
            // mark players allowed to vote - only current ingame players, players joining during vote will be ignored
            i = 0
            while (i < Game_local.gameLocal.numClients) {
                if (Game_local.gameLocal.entities[i] != null && Game_local.gameLocal.entities[i] is idPlayer) {
                    playerState[i].vote =
                        if (i == clientNum) playerVote_t.PLAYER_VOTE_YES else playerVote_t.PLAYER_VOTE_WAIT
                } else {
                    playerState[i].vote = playerVote_t.PLAYER_VOTE_NONE
                }
                i++
            }
        }

        fun ClientUpdateVote(status: vote_result_t, yesCount: Int, noCount: Int) {
            val outMsg = idBitMsg()
            val msgBuf = ByteBuffer.allocate(Game_local.MAX_GAME_MESSAGE_SIZE)
            if (!Game_local.gameLocal.isClient) {
                outMsg.Init(msgBuf, Game_local.MAX_GAME_MESSAGE_SIZE)
                outMsg.WriteByte(Game_local.GAME_RELIABLE_MESSAGE_UPDATEVOTE.toByte())
                outMsg.WriteByte(status.ordinal.toByte())
                outMsg.WriteByte(yesCount.toByte())
                outMsg.WriteByte(noCount.toByte())
                NetworkSystem.networkSystem.ServerSendReliableMessage(-1, outMsg)
            }
            if (vote == vote_flags_t.VOTE_NONE) {
                // clients coming in late don't get the vote start and are not allowed to vote
                return
            }
            when (status) {
                vote_result_t.VOTE_FAILED -> {
                    AddChatLine(Common.common.GetLanguageDict().GetString("#str_04278"))
                    Game_local.gameSoundWorld!!.PlayShaderDirectly(GlobalSoundStrings[TempDump.etoi(snd_evt_t.SND_VOTE_FAILED)])
                    if (Game_local.gameLocal.isClient) {
                        vote = vote_flags_t.VOTE_NONE
                    }
                }

                vote_result_t.VOTE_PASSED -> {
                    AddChatLine(Common.common.GetLanguageDict().GetString("#str_04277"))
                    Game_local.gameSoundWorld!!.PlayShaderDirectly(GlobalSoundStrings[TempDump.etoi(snd_evt_t.SND_VOTE_PASSED)])
                }

                vote_result_t.VOTE_RESET -> if (Game_local.gameLocal.isClient) {
                    vote = vote_flags_t.VOTE_NONE
                }

                vote_result_t.VOTE_ABORTED -> {
                    AddChatLine(Common.common.GetLanguageDict().GetString("#str_04276"))
                    if (Game_local.gameLocal.isClient) {
                        vote = vote_flags_t.VOTE_NONE
                    }
                }

                else -> {}
            }
            if (Game_local.gameLocal.isClient) {
                yesVotes = yesCount.toFloat()
                noVotes = noCount.toFloat()
            }
        }

        fun CastVote(clientNum: Int, castVote: Boolean) {
            val outMsg = idBitMsg()
            val msgBuf = ByteBuffer.allocate(128)
            if (clientNum == Game_local.gameLocal.localClientNum) {
                voted = true
            }
            if (Game_local.gameLocal.isClient) {
                outMsg.Init(msgBuf, msgBuf.capacity())
                outMsg.WriteByte(Game_local.GAME_RELIABLE_MESSAGE_CASTVOTE.toByte())
                outMsg.WriteByte(TempDump.btoi(castVote).toByte())
                NetworkSystem.networkSystem.ClientSendReliableMessage(outMsg)
                return
            }

            // sanity
            if (vote == vote_flags_t.VOTE_NONE) {
                Game_local.gameLocal.ServerSendChatMessage(
                    clientNum,
                    "server",
                    Common.common.GetLanguageDict().GetString("#str_04275")
                )
                Common.common.DPrintf("client %d: cast vote while no vote in progress\n", clientNum)
                return
            }
            if (playerState[clientNum].vote != playerVote_t.PLAYER_VOTE_WAIT) {
                Game_local.gameLocal.ServerSendChatMessage(
                    clientNum,
                    "server",
                    Common.common.GetLanguageDict().GetString("#str_04274")
                )
                Common.common.DPrintf(
                    "client %d: cast vote - vote %d != PLAYER_VOTE_WAIT\n",
                    clientNum,
                    playerState[clientNum].vote
                )
                return
            }
            if (castVote) {
                playerState[clientNum].vote = playerVote_t.PLAYER_VOTE_YES
                yesVotes++
            } else {
                playerState[clientNum].vote = playerVote_t.PLAYER_VOTE_NO
                noVotes++
            }
            ClientUpdateVote(vote_result_t.VOTE_UPDATE, yesVotes.toInt(), noVotes.toInt())
        }

        /*
         ================
         idMultiplayerGame::ExecuteVote
         the votes are checked for validity/relevance before they are started
         we assume that they are still legit when reaching here
         ================
         */
        fun ExecuteVote() {
            val needRestart: Boolean
            when (vote) {
                vote_flags_t.VOTE_RESTART -> Game_local.gameLocal.MapRestart()
                vote_flags_t.VOTE_TIMELIMIT -> {
                    SysCvar.si_timeLimit.SetInteger(TempDump.atoi(voteValue))
                    needRestart = Game_local.gameLocal.NeedRestart()
                    CmdSystem.cmdSystem.BufferCommandText(cmdExecution_t.CMD_EXEC_NOW, "rescanSI")
                    if (needRestart) {
                        CmdSystem.cmdSystem.BufferCommandText(cmdExecution_t.CMD_EXEC_APPEND, "nextMap")
                    }
                }

                vote_flags_t.VOTE_FRAGLIMIT -> {
                    SysCvar.si_fragLimit.SetInteger(TempDump.atoi(voteValue))
                    needRestart = Game_local.gameLocal.NeedRestart()
                    CmdSystem.cmdSystem.BufferCommandText(cmdExecution_t.CMD_EXEC_NOW, "rescanSI")
                    if (needRestart) {
                        CmdSystem.cmdSystem.BufferCommandText(cmdExecution_t.CMD_EXEC_APPEND, "nextMap")
                    }
                }

                vote_flags_t.VOTE_GAMETYPE -> {
                    SysCvar.si_gameType.SetString(voteValue.toString())
                    Game_local.gameLocal.MapRestart()
                }

                vote_flags_t.VOTE_KICK -> CmdSystem.cmdSystem.BufferCommandText(
                    cmdExecution_t.CMD_EXEC_NOW,
                    Str.va("kick %s", voteValue)
                )

                vote_flags_t.VOTE_MAP -> {
                    SysCvar.si_map.SetString(voteValue.toString())
                    Game_local.gameLocal.MapRestart()
                }

                vote_flags_t.VOTE_SPECTATORS -> {
                    SysCvar.si_spectators.SetBool(!SysCvar.si_spectators.GetBool())
                    needRestart = Game_local.gameLocal.NeedRestart()
                    CmdSystem.cmdSystem.BufferCommandText(cmdExecution_t.CMD_EXEC_NOW, "rescanSI")
                    if (needRestart) {
                        CmdSystem.cmdSystem.BufferCommandText(cmdExecution_t.CMD_EXEC_APPEND, "nextMap")
                    }
                }

                vote_flags_t.VOTE_NEXTMAP -> CmdSystem.cmdSystem.BufferCommandText(
                    cmdExecution_t.CMD_EXEC_APPEND,
                    "serverNextMap\n"
                )

                else -> {}
            }
        }

        fun WantKilled(clientNum: Int) {
            val ent = Game_local.gameLocal.entities[clientNum]
            if (ent != null && ent is idPlayer) {
                ent.Kill(false, false)
            }
        }

        fun NumActualClients(countSpectators: Boolean, teamcounts: IntArray? /*= NULL*/): Int {
            var p: idPlayer
            var c = 0
            if (teamcounts != null) {
                teamcounts[1] = 0
                teamcounts[0] = teamcounts[1]
            }
            for (i in 0 until Game_local.gameLocal.numClients) {
                val ent = Game_local.gameLocal.entities[i]
                if (null == ent || ent !is idPlayer) {
                    continue
                }
                p = ent
                if (countSpectators || CanPlay(p)) {
                    c++
                }
                if (teamcounts != null && CanPlay(p)) {
                    teamcounts[p.team]++
                }
            }
            return c
        }

        fun DropWeapon(clientNum: Int) {
            assert(!Game_local.gameLocal.isClient)
            val ent = Game_local.gameLocal.entities[clientNum]
            if (null == ent || ent !is idPlayer) {
                return
            }
            ent.DropWeapon(false)
        }

        fun MapRestart() {
            var clientNum: Int
            assert(!Game_local.gameLocal.isClient)
            if (gameState != gameState_t.WARMUP) {
                NewState(gameState_t.WARMUP)
                nextState = gameState_t.INACTIVE
                nextStateSwitch = 0
            }
            if (SysCvar.g_balanceTDM.GetBool() && lastGameType != gameType_t.GAME_TDM && Game_local.gameLocal.gameType == gameType_t.GAME_TDM) {
                clientNum = 0
                while (clientNum < Game_local.gameLocal.numClients) {
                    if (Game_local.gameLocal.entities[clientNum] != null && Game_local.gameLocal.entities[clientNum] is idPlayer) {
                        if ((Game_local.gameLocal.entities[clientNum] as idPlayer).BalanceTDM()) {
                            // core is in charge of syncing down userinfo changes
                            // it will also call back game through SetUserInfo with the current info for update
                            CmdSystem.cmdSystem.BufferCommandText(
                                cmdExecution_t.CMD_EXEC_NOW,
                                Str.va("updateUI %d\n", clientNum)
                            )
                        }
                    }
                    clientNum++
                }
            }
            lastGameType = Game_local.gameLocal.gameType
        }

        fun SwitchToTeam(clientNum: Int, oldteam: Int, newteam: Int) {
            var ent: idEntity?
            var i: Int
            assert(Game_local.gameLocal.gameType == gameType_t.GAME_TDM)
            assert(oldteam != newteam)
            assert(!Game_local.gameLocal.isClient)
            if (!Game_local.gameLocal.isClient && newteam >= 0 && IsInGame(clientNum)) {
                PrintMessageEvent(-1, msg_evt_t.MSG_JOINTEAM, clientNum, newteam)
            }
            // assign the right teamFragCount
            i = 0
            while (i < Game_local.gameLocal.numClients) {
                if (i == clientNum) {
                    i++
                    continue
                }
                ent = Game_local.gameLocal.entities[i]
                if (ent != null && ent is idPlayer && (ent as idPlayer).team == newteam) {
                    playerState[clientNum].teamFragCount = playerState[i].teamFragCount
                    break
                }
                i++
            }
            if (i == Game_local.gameLocal.numClients) {
                // alone on this team
                playerState[clientNum].teamFragCount = 0
            }
            if (gameState == gameState_t.GAMEON && oldteam != -1) {
                // when changing teams during game, kill and respawn
                val p = Game_local.gameLocal.entities[clientNum] as idPlayer
                if (p.IsInTeleport()) {
                    p.ServerSendEvent(idPlayer.EVENT_ABORT_TELEPORTER, null, false, -1)
                    p.SetPrivateCameraView(null)
                }
                p.Kill(true, true)
                CheckAbortGame()
            }
        }

        fun IsPureReady(): Boolean {
            return pureReady
        }

        fun ProcessChatMessage(clientNum: Int, team: Boolean, name: String, text: String?, sound: String?) {
            val outMsg = idBitMsg()
            val msgBuf = ByteBuffer.allocate(256)
            var prefix: String? = null
            val send_to: Int // 0 - all, 1 - specs, 2 - team
            var i: Int
            var ent: idEntity?
            val p: idPlayer?
            val prefixed_name: String?
            assert(!Game_local.gameLocal.isClient)
            if (clientNum >= 0) {
                p = Game_local.gameLocal.entities[clientNum] as idPlayer
                if (!(p != null && p is idPlayer)) {
                    return
                }
                if (p.spectating) {
                    prefix = "spectating"
                    send_to =
                        if (team || !g_spectatorChat.GetBool() && (gameState == gameState_t.GAMEON || gameState == gameState_t.SUDDENDEATH)) {
                            // to specs
                            1
                        } else {
                            // to all
                            0
                        }
                } else if (team) {
                    prefix = "team"
                    // to team
                    send_to = 2
                } else {
                    // to all
                    send_to = 0
                }
            } else {
                p = null
                send_to = 0
            }
            // put the message together
            outMsg.Init(msgBuf, msgBuf.capacity())
            outMsg.WriteByte(Game_local.GAME_RELIABLE_MESSAGE_CHAT.toByte())
            prefixed_name = if (prefix != null && prefix.isNotEmpty()) {
                Str.va("(%s) %s", prefix, name)
            } else {
                name
            }
            outMsg.WriteString(prefixed_name)
            outMsg.WriteString(text, -1, false)
            if (0 == send_to) {
                AddChatLine("%s^0: %s\n", prefixed_name, text)
                NetworkSystem.networkSystem.ServerSendReliableMessage(-1, outMsg)
                if (sound != null) {
                    PlayGlobalSound(-1, snd_evt_t.SND_COUNT, sound)
                }
            } else {
                i = 0
                while (i < Game_local.gameLocal.numClients) {
                    ent = Game_local.gameLocal.entities[i]
                    if (null == ent || ent !is idPlayer) {
                        i++
                        continue
                    }
                    if (send_to == 1 && (ent as idPlayer).spectating) {
                        if (sound != null) {
                            PlayGlobalSound(i, snd_evt_t.SND_COUNT, sound)
                        }
                        if (i == Game_local.gameLocal.localClientNum) {
                            AddChatLine("%s^0: %s\n", prefixed_name, text)
                        } else {
                            NetworkSystem.networkSystem.ServerSendReliableMessage(i, outMsg)
                        }
                    } else if (send_to == 2 && (ent as idPlayer).team == p!!.team) {
                        if (sound != null) {
                            PlayGlobalSound(i, snd_evt_t.SND_COUNT, sound)
                        }
                        if (i == Game_local.gameLocal.localClientNum) {
                            AddChatLine("%s^0: %s\n", prefixed_name, text)
                        } else {
                            NetworkSystem.networkSystem.ServerSendReliableMessage(i, outMsg)
                        }
                    }
                    i++
                }
            }
        }

        fun ProcessVoiceChat(clientNum: Int, team: Boolean, index: Int) {
            var index = index
            val spawnArgs: idDict
            var keyval: idKeyValue?
            val name: String?
            val snd_key: idStr?
            val text_key: String
            val p: idPlayer
            p = Game_local.gameLocal.entities[clientNum] as idPlayer
            if (!(p != null && p is idPlayer)) {
                return
            }
            if (p.spectating) {
                return
            }

            // lookup the sound def
            spawnArgs = Game_local.gameLocal.FindEntityDefDict("player_doommarine", false)!!
            keyval = spawnArgs.MatchPrefix("snd_voc_", null)
            while (index > 0 && keyval != null) {
                keyval = spawnArgs.MatchPrefix("snd_voc_", keyval)
                index--
            }
            if (null == keyval) {
                Common.common.DPrintf("ProcessVoiceChat: unknown chat index %d\n", index)
                return
            }
            snd_key = keyval.GetKey()
            name = Game_local.gameLocal.userInfo[clientNum].GetString("ui_name")
            text_key = String.format("txt_%s", snd_key.Right(snd_key.Length() - 4).toString())
            if (team || gameState == gameState_t.COUNTDOWN || gameState == gameState_t.GAMEREVIEW) {
                ProcessChatMessage(
                    clientNum,
                    team,
                    name,
                    spawnArgs.GetString(text_key),
                    spawnArgs.GetString(snd_key.toString())
                )
            } else {
                p.StartSound(snd_key.toString(), gameSoundChannel_t.SND_CHANNEL_ANY, 0, true)
                ProcessChatMessage(clientNum, team, name, spawnArgs.GetString(text_key), null)
            }
        }

        // called by idPlayer whenever it detects a team change (init or switch)
        fun Precache() {
            var i: Int
            var f: idFile?
            if (!Game_local.gameLocal.isMultiplayer) {
                return
            }
            Game_local.gameLocal.FindEntityDefDict("player_doommarine", false)

            // skins
            var str = idStr(CVarSystem.cvarSystem.GetCVarString("mod_validSkins"))
            var skin: idStr
            while (str.Length() != 0) {
                val n = str.Find(";")
                if (n >= 0) {
                    skin = str.Left(n)
                    str = str.Right(str.Length() - n - 1)
                } else {
                    skin = str
                    str.set("")
                }
                DeclManager.declManager.FindSkin(skin, false)
            }
            i = 0
            while (SysCvar.ui_skinArgs[i] != null) {
                DeclManager.declManager.FindSkin(SysCvar.ui_skinArgs[i]!!, false)
                i++
            }
            // MP game sounds
            i = 0
            while (i < snd_evt_t.SND_COUNT.ordinal) {
                f = FileSystem_h.fileSystem.OpenFileRead(GlobalSoundStrings[i])
                if (f != null) {
                    FileSystem_h.fileSystem.CloseFile(f!!)
                }
                i++
            }
            // MP guis. just make sure we hit all of them
            i = 0
            while (i < MPGuis.size) {
                UserInterface.uiManager.FindGui(MPGuis[i], true)
                i++
            }
        }

        // throttle UI switch rates
        fun ThrottleUserInfo() {
            var i: Int
            assert(Game_local.gameLocal.localClientNum >= 0)
            i = 0
            while (i < ThrottleVars.size) {
                if (idStr.Icmp(
                        Game_local.gameLocal.userInfo[Game_local.gameLocal.localClientNum].GetString(ThrottleVars[i]),
                        CVarSystem.cvarSystem.GetCVarString(ThrottleVars[i])
                    ) != 0
                ) {
                    if (Game_local.gameLocal.realClientTime < switchThrottle[i]) {
                        AddChatLine(
                            Common.common.GetLanguageDict().GetString("#str_04299"),
                            Common.common.GetLanguageDict().GetString(ThrottleVarsInEnglish[i]),
                            (switchThrottle[i] - Game_local.gameLocal.time) / 1000 + 1
                        )
                        CVarSystem.cvarSystem.SetCVarString(
                            ThrottleVars[i],
                            Game_local.gameLocal.userInfo[Game_local.gameLocal.localClientNum].GetString(
                                ThrottleVars[i]
                            )
                        )
                    } else {
                        switchThrottle[i] = Game_local.gameLocal.time + ThrottleDelay[i] * 1000
                    }
                }
                i++
            }
        }

        fun ToggleSpectate() {
            val spectating: Boolean
            assert(Game_local.gameLocal.isClient || Game_local.gameLocal.localClientNum == 0)
            spectating = idStr.Icmp(CVarSystem.cvarSystem.GetCVarString("ui_spectate"), "Spectate") == 0
            if (spectating) {
                // always allow toggling to play
                CVarSystem.cvarSystem.SetCVarString("ui_spectate", "Play")
            } else {
                // only allow toggling to spectate if spectators are enabled.
                if (Game_local.gameLocal.serverInfo.GetBool("si_spectators")) {
                    CVarSystem.cvarSystem.SetCVarString("ui_spectate", "Spectate")
                } else {
                    Game_local.gameLocal.mpGame.AddChatLine(Common.common.GetLanguageDict().GetString("#str_06747"))
                }
            }
        }

        fun ToggleReady() {
            val ready: Boolean
            assert(Game_local.gameLocal.isClient || Game_local.gameLocal.localClientNum == 0)
            ready = idStr.Icmp(CVarSystem.cvarSystem.GetCVarString("ui_ready"), "Ready") == 0
            if (ready) {
                CVarSystem.cvarSystem.SetCVarString("ui_ready", "Not Ready")
            } else {
                CVarSystem.cvarSystem.SetCVarString("ui_ready", "Ready")
            }
        }

        fun ToggleTeam() {
            val team: Boolean
            assert(Game_local.gameLocal.isClient || Game_local.gameLocal.localClientNum == 0)
            team = idStr.Icmp(CVarSystem.cvarSystem.GetCVarString("ui_team"), "Red") == 0
            if (team) {
                CVarSystem.cvarSystem.SetCVarString("ui_team", "Blue")
            } else {
                CVarSystem.cvarSystem.SetCVarString("ui_team", "Red")
            }
        }

        fun ClearFrags(clientNum: Int) {
            playerState[clientNum].fragCount = 0
        }

        fun EnterGame(clientNum: Int) {
            assert(!Game_local.gameLocal.isClient)
            if (!playerState[clientNum].ingame) {
                playerState[clientNum].ingame = true
                if (Game_local.gameLocal.isMultiplayer) {
                    // can't use PrintMessageEvent as clients don't know the nickname yet
                    Game_local.gameLocal.ServerSendChatMessage(
                        -1,
                        Common.common.GetLanguageDict().GetString("#str_02047"),
                        Str.va(
                            Common.common.GetLanguageDict().GetString("#str_07177"),
                            Game_local.gameLocal.userInfo[clientNum].GetString("ui_name")
                        )
                    )
                }
            }
        }

        fun CanPlay(p: idPlayer): Boolean {
            return !p.wantSpectate && playerState[p.entityNumber].ingame
        }

        fun IsInGame(clientNum: Int): Boolean {
            return playerState[clientNum].ingame
        }

        fun WantRespawn(p: idPlayer): Boolean {
            return p.forceRespawn && !p.wantSpectate && playerState[p.entityNumber].ingame
        }

        fun ServerWriteInitialReliableMessages(clientNum: Int) {
            val outMsg = idBitMsg()
            val msgBuf = ByteBuffer.allocate(Game_local.MAX_GAME_MESSAGE_SIZE)
            var i: Int
            var ent: idEntity?
            outMsg.Init(msgBuf, Game_local.MAX_GAME_MESSAGE_SIZE)
            outMsg.BeginWriting()
            outMsg.WriteByte(Game_local.GAME_RELIABLE_MESSAGE_STARTSTATE.toByte())
            // send the game state and start time
            outMsg.WriteByte(gameState.ordinal.toByte())
            outMsg.WriteLong(matchStartedTime)
            outMsg.WriteShort(startFragLimit.toShort())
            // send the powerup states and the spectate states
            i = 0
            while (i < Game_local.gameLocal.numClients) {
                ent = Game_local.gameLocal.entities[i]
                if (i != clientNum && ent != null && ent is idPlayer) {
                    outMsg.WriteShort(i.toShort())
                    outMsg.WriteShort((ent as idPlayer).inventory.powerups.toShort())
                    outMsg.WriteBits(TempDump.btoi((ent as idPlayer).spectating), 1)
                }
                i++
            }
            outMsg.WriteShort(Game_local.MAX_CLIENTS.toShort())
            NetworkSystem.networkSystem.ServerSendReliableMessage(clientNum, outMsg)

            // we send SI in connectResponse messages, but it may have been modified already
            outMsg.BeginWriting()
            outMsg.WriteByte(Game_local.GAME_RELIABLE_MESSAGE_SERVERINFO.toByte())
            outMsg.WriteDeltaDict(Game_local.gameLocal.serverInfo, null)
            NetworkSystem.networkSystem.ServerSendReliableMessage(clientNum, outMsg)

            // warmup time
            if (gameState == gameState_t.COUNTDOWN) {
                outMsg.BeginWriting()
                outMsg.WriteByte(Game_local.GAME_RELIABLE_MESSAGE_WARMUPTIME.toByte())
                outMsg.WriteLong(warmupEndTime)
                NetworkSystem.networkSystem.ServerSendReliableMessage(clientNum, outMsg)
            }
        }

        fun ClientReadStartState(msg: idBitMsg) {
            var i: Int
            var client: Int
            var powerup: Int

            // read the state in preparation for reading snapshot updates
            gameState = gameState_t.entries.toTypedArray()[msg.ReadByte().toInt()]
            matchStartedTime = msg.ReadLong()
            startFragLimit = msg.ReadShort().toInt()
            while (msg.ReadShort().also { client = it.toInt() } != Game_local.MAX_CLIENTS.toShort()) {
                assert(Game_local.gameLocal.entities[client] != null && Game_local.gameLocal.entities[client] is idPlayer)
                powerup = msg.ReadShort().toInt()
                i = 0
                while (i < GameSSDWindow.MAX_POWERUPS) {
                    if (powerup and (1 shl i) != 0) {
                        (Game_local.gameLocal.entities[client] as idPlayer).GivePowerUp(i, 0)
                    }
                    i++
                }
                val spectate = msg.ReadBits(1) != 0
                (Game_local.gameLocal.entities[client] as idPlayer).Spectate(spectate)
            }
        }

        fun ClientReadWarmupTime(msg: idBitMsg) {
            warmupEndTime = msg.ReadLong()
        }

        fun ServerClientConnect(clientNum: Int) {
//	memset( &playerState[ clientNum ], 0, sizeof( playerState[ clientNum ] ) );
            playerState[clientNum] = mpPlayerState_s()
            //            for (int i = clientNum; i < playerState.length; i++) {
//                playerState[i] = new mpPlayerState_s();
//            }
        }

        fun PlayerStats(clientNum: Int, data: Array<String>, len: Int) {
            val ent: idEntity?
            val team: Int
            data[0] = ""

            // make sure we don't exceed the client list
            if (clientNum < 0 || clientNum > Game_local.gameLocal.numClients) {
                return
            }

            // find which team this player is on
            ent = Game_local.gameLocal.entities[clientNum]
            team = if (ent != null && ent is idPlayer) {
                (ent as idPlayer).team
            } else {
                return
            }
            idStr.snPrintf(
                data,
                len,
                "team=%d score=%d tks=%d",
                team,
                playerState[clientNum].fragCount,
                playerState[clientNum].teamFragCount
            )
            return
        }

        private fun UpdatePlayerRanks() {
            var i: Int
            var j: Int
            var k: Int
            val players = arrayOfNulls<idPlayer>(Game_local.MAX_CLIENTS)
            var ent: idEntity?
            var player: idPlayer?

//	memset( players, 0, sizeof( players ) );
            numRankedPlayers = 0
            i = 0
            while (i < Game_local.gameLocal.numClients) {
                ent = Game_local.gameLocal.entities[i]
                if (null == ent || ent !is idPlayer) {
                    i++
                    continue
                }
                player = ent
                if (!CanPlay(player)) {
                    i++
                    continue
                }
                if (Game_local.gameLocal.gameType == gameType_t.GAME_TOURNEY) {
                    if (i != currentTourneyPlayer[0] && i != currentTourneyPlayer[1]) {
                        i++
                        continue
                    }
                }
                if (Game_local.gameLocal.gameType == gameType_t.GAME_LASTMAN && playerState[i].fragCount == LASTMAN_NOLIVES) {
                    i++
                    continue
                }
                j = 0
                while (j < numRankedPlayers) {
                    var insert = false
                    if (Game_local.gameLocal.gameType == gameType_t.GAME_TDM) {
                        if (player.team != players[j]!!.team) {
                            if (playerState[i].teamFragCount > playerState[players[j]!!.entityNumber].teamFragCount) {
                                // team scores
                                insert = true
                            } else if (playerState[i].teamFragCount == playerState[players[j]!!.entityNumber].teamFragCount && player.team < players[j]!!.team) {
                                // at equal scores, sort by team number
                                insert = true
                            }
                        } else if (playerState[i].fragCount > playerState[players[j]!!.entityNumber].fragCount) {
                            // in the same team, sort by frag count
                            insert = true
                        }
                    } else {
                        insert = playerState[i].fragCount > playerState[players[j]!!.entityNumber].fragCount
                    }
                    if (insert) {
                        k = numRankedPlayers
                        while (k > j) {
                            players[k] = players[k - 1]
                            k--
                        }
                        players[j] = player
                        break
                    }
                    j++
                }
                if (j == numRankedPlayers) {
                    players[numRankedPlayers] = player
                }
                numRankedPlayers++
                i++
            }

//	memcpy( rankedPlayers, players, sizeof( players ) );
            System.arraycopy(players, 0, rankedPlayers, 0, players.size)
        }

        // updates the passed gui with current score information
        private fun UpdateRankColor(gui: idUserInterface, mask: String, i: Int, vec: idVec3) {
            for (j in 1..3) {
                gui.SetStateFloat(Str.va(mask, i, j), vec[j - 1])
            }
        }

        private fun UpdateScoreboard(scoreBoard: idUserInterface, player: idPlayer) {
            var i: Int
            var j: Int
            var iline: Int
            var k: Int
            val gameinfo: String
            val livesinfo: String
            val timeinfo: String
            var ent: idEntity?
            var p: idPlayer?
            var value: Int
            scoreBoard.SetStateString(
                "scoretext",
                if (Game_local.gameLocal.gameType == gameType_t.GAME_LASTMAN) Common.common.GetLanguageDict()
                    .GetString("#str_04242") else Common.common.GetLanguageDict().GetString("#str_04243")
            )
            iline = 0 // the display lines
            if (gameState != gameState_t.WARMUP) {
                i = 0
                while (i < numRankedPlayers) {

                    // ranked player
                    iline++
                    scoreBoard.SetStateString(
                        Str.va("player%d", iline),
                        rankedPlayers[i].GetUserInfo().GetString("ui_name")
                    )
                    if (Game_local.gameLocal.gameType == gameType_t.GAME_TDM) {
                        value = idMath.ClampInt(
                            MP_PLAYER_MINFRAGS,
                            MP_PLAYER_MAXFRAGS,
                            playerState[rankedPlayers[i].entityNumber].fragCount
                        )
                        scoreBoard.SetStateInt(Str.va("player%d_tdm_score", iline), value)
                        value = idMath.ClampInt(
                            MP_PLAYER_MINFRAGS,
                            MP_PLAYER_MAXFRAGS,
                            playerState[rankedPlayers[i].entityNumber].teamFragCount
                        )
                        scoreBoard.SetStateString(Str.va("player%d_tdm_tscore", iline), Str.va("/ %d", value))
                        scoreBoard.SetStateString(Str.va("player%d_score", iline), "")
                    } else {
                        value = idMath.ClampInt(
                            MP_PLAYER_MINFRAGS,
                            MP_PLAYER_MAXFRAGS,
                            playerState[rankedPlayers[i].entityNumber].fragCount
                        )
                        scoreBoard.SetStateInt(Str.va("player%d_score", iline), value)
                        scoreBoard.SetStateString(Str.va("player%d_tdm_tscore", iline), "")
                        scoreBoard.SetStateString(Str.va("player%d_tdm_score", iline), "")
                    }
                    value = idMath.ClampInt(
                        0,
                        MP_PLAYER_MAXWINS,
                        playerState[rankedPlayers[i].entityNumber].wins
                    )
                    scoreBoard.SetStateInt(Str.va("player%d_wins", iline), value)
                    scoreBoard.SetStateInt(
                        Str.va("player%d_ping", iline),
                        playerState[rankedPlayers[i].entityNumber].ping
                    )
                    // set the color band
                    scoreBoard.SetStateInt(Str.va("rank%d", iline), 1)
                    UpdateRankColor(scoreBoard, "rank%d_color%d", iline, rankedPlayers[i].colorBar)
                    if (rankedPlayers[i] === player) {
                        // highlight who we are
                        scoreBoard.SetStateInt("rank_self", iline)
                    }
                    i++
                }
            }

            // if warmup, this draws everyone, otherwise it goes over spectators only
            // when doing warmup we loop twice to draw ready/not ready first *then* spectators
            // NOTE: in tourney, shows spectators according to their playing rank order?
            k = 0
            while (k < if (gameState == gameState_t.WARMUP) 2 else 1) {
                i = 0
                while (i < Game_local.MAX_CLIENTS) {
                    ent = Game_local.gameLocal.entities[i]
                    if (null == ent || ent !is idPlayer) {
                        i++
                        continue
                    }
                    if (gameState != gameState_t.WARMUP) {
                        // check he's not covered by ranks already
                        j = 0
                        while (j < numRankedPlayers) {
                            if (ent == rankedPlayers[j]) {
                                break
                            }
                            j++
                        }
                        if (j != numRankedPlayers) {
                            i++
                            continue
                        }
                    }
                    p = ent
                    if (gameState == gameState_t.WARMUP) {
                        if (k == 0 && p.spectating) {
                            i++
                            continue
                        }
                        if (k == 1 && !p.spectating) {
                            i++
                            continue
                        }
                    }
                    iline++
                    if (!playerState[i].ingame) {
                        scoreBoard.SetStateString(
                            Str.va("player%d", iline),
                            Common.common.GetLanguageDict().GetString("#str_04244")
                        )
                        scoreBoard.SetStateString(
                            Str.va("player%d_score", iline),
                            Common.common.GetLanguageDict().GetString("#str_04245")
                        )
                        // no color band
                        scoreBoard.SetStateInt(Str.va("rank%d", iline), 0)
                    } else {
                        scoreBoard.SetStateString(
                            Str.va("player%d", iline),
                            Game_local.gameLocal.userInfo[i].GetString("ui_name")
                        )
                        if (gameState == gameState_t.WARMUP) {
                            if (p.spectating) {
                                scoreBoard.SetStateString(
                                    Str.va("player%d_score", iline),
                                    Common.common.GetLanguageDict().GetString("#str_04246")
                                )
                                // no color band
                                scoreBoard.SetStateInt(Str.va("rank%d", iline), 0)
                            } else {
                                scoreBoard.SetStateString(
                                    Str.va("player%d_score", iline),
                                    if (p.IsReady()) Common.common.GetLanguageDict()
                                        .GetString("#str_04247") else Common.common.GetLanguageDict()
                                        .GetString("#str_04248")
                                )
                                // set the color band
                                scoreBoard.SetStateInt(Str.va("rank%d", iline), 1)
                                UpdateRankColor(scoreBoard, "rank%d_color%d", iline, p.colorBar)
                            }
                        } else {
                            if (Game_local.gameLocal.gameType == gameType_t.GAME_LASTMAN && playerState[i].fragCount == LASTMAN_NOLIVES) {
                                scoreBoard.SetStateString(
                                    Str.va("player%d_score", iline),
                                    Common.common.GetLanguageDict().GetString("#str_06736")
                                )
                                // set the color band
                                scoreBoard.SetStateInt(Str.va("rank%d", iline), 1)
                                UpdateRankColor(scoreBoard, "rank%d_color%d", iline, p.colorBar)
                            } else {
                                scoreBoard.SetStateString(
                                    Str.va("player%d_score", iline),
                                    Common.common.GetLanguageDict().GetString("#str_04246")
                                )
                                // no color band
                                scoreBoard.SetStateInt(Str.va("rank%d", iline), 0)
                            }
                        }
                    }
                    scoreBoard.SetStateString(Str.va("player%d_tdm_tscore", iline), "")
                    scoreBoard.SetStateString(Str.va("player%d_tdm_score", iline), "")
                    scoreBoard.SetStateString(Str.va("player%d_wins", iline), "")
                    scoreBoard.SetStateInt(Str.va("player%d_ping", iline), playerState[i].ping)
                    if (i == player.entityNumber) {
                        // highlight who we are
                        scoreBoard.SetStateInt("rank_self", iline)
                    }
                    i++
                }
                k++
            }

            // clear remaining lines (empty slots)
            iline++
            while (iline < 5) {
                scoreBoard.SetStateString(Str.va("player%d", iline), "")
                scoreBoard.SetStateString(Str.va("player%d_score", iline), "")
                scoreBoard.SetStateString(Str.va("player%d_tdm_tscore", iline), "")
                scoreBoard.SetStateString(Str.va("player%d_tdm_score", iline), "")
                scoreBoard.SetStateString(Str.va("player%d_wins", iline), "")
                scoreBoard.SetStateString(Str.va("player%d_ping", iline), "")
                scoreBoard.SetStateInt(Str.va("rank%d", iline), 0)
                iline++
            }
            gameinfo = Str.va(
                "%s: %s",
                Common.common.GetLanguageDict().GetString("#str_02376"),
                Game_local.gameLocal.serverInfo.GetString("si_gameType")
            )
            livesinfo = if (Game_local.gameLocal.gameType == gameType_t.GAME_LASTMAN) {
                if (gameState == gameState_t.GAMEON || gameState == gameState_t.SUDDENDEATH) {
                    Str.va("%s: %d", Common.common.GetLanguageDict().GetString("#str_04264"), startFragLimit)
                } else {
                    Str.va(
                        "%s: %d",
                        Common.common.GetLanguageDict().GetString("#str_04264"),
                        Game_local.gameLocal.serverInfo.GetInt("si_fragLimit")
                    )
                }
            } else {
                Str.va(
                    "%s: %d",
                    Common.common.GetLanguageDict().GetString("#str_01982"),
                    Game_local.gameLocal.serverInfo.GetInt("si_fragLimit")
                )
            }
            timeinfo = if (Game_local.gameLocal.serverInfo.GetInt("si_timeLimit") > 0) {
                Str.va(
                    "%s: %d",
                    Common.common.GetLanguageDict().GetString("#str_01983"),
                    Game_local.gameLocal.serverInfo.GetInt("si_timeLimit")
                )
            } else {
                Str.va("%s", Common.common.GetLanguageDict().GetString("#str_07209"))
            }
            scoreBoard.SetStateString("gameinfo", gameinfo)
            scoreBoard.SetStateString("livesinfo", livesinfo)
            scoreBoard.SetStateString("timeinfo", timeinfo)
            scoreBoard.Redraw(Game_local.gameLocal.time)
        }

        private fun ClearGuis() {
            var i: Int
            i = 0
            val scoreBoard = scoreBoard!!
            while (i < Game_local.MAX_CLIENTS) {
                scoreBoard.SetStateString(Str.va("player%d", i + 1), "")
                scoreBoard.SetStateString(Str.va("player%d_score", i + 1), "")
                scoreBoard.SetStateString(Str.va("player%d_tdm_tscore", i + 1), "")
                scoreBoard.SetStateString(Str.va("player%d_tdm_score", i + 1), "")
                scoreBoard.SetStateString(Str.va("player%d_wins", i + 1), "")
                scoreBoard.SetStateString(Str.va("player%d_status", i + 1), "")
                scoreBoard.SetStateInt(Str.va("rank%d", i + 1), 0)
                scoreBoard.SetStateInt("rank_self", 0)
                val player = Game_local.gameLocal.entities[i] as idPlayer?
                if (null == player || null == player.hud) {
                    i++
                    continue
                }
                player.hud!!.SetStateString(Str.va("player%d", i + 1), "")
                player.hud!!.SetStateString(Str.va("player%d_score", i + 1), "")
                player.hud!!.SetStateString(Str.va("player%d_ready", i + 1), "")
                scoreBoard.SetStateInt(Str.va("rank%d", i + 1), 0)
                player.hud!!.SetStateInt("rank_self", 0)
                i++
            }
        }

        private fun DrawScoreBoard(player: idPlayer) {
            if (player.scoreBoardOpen || gameState == gameState_t.GAMEREVIEW) {
                if (!playerState[player.entityNumber].scoreBoardUp) {
                    scoreBoard!!.Activate(true, Game_local.gameLocal.time)
                    playerState[player.entityNumber].scoreBoardUp = true
                }
                UpdateScoreboard(scoreBoard!!, player)
            } else {
                if (playerState[player.entityNumber].scoreBoardUp) {
                    scoreBoard!!.Activate(false, Game_local.gameLocal.time)
                    playerState[player.entityNumber].scoreBoardUp = false
                }
            }
        }

        private fun UpdateHud(player: idPlayer, hud: idUserInterface?) {
            var i: Int
            if (null == hud) {
                return
            }
            hud.SetStateBool("warmup", Warmup())
            if (gameState == gameState_t.WARMUP) {
                if (player.IsReady()) {
                    hud.SetStateString("warmuptext", Common.common.GetLanguageDict().GetString("#str_04251"))
                } else {
                    hud.SetStateString("warmuptext", Common.common.GetLanguageDict().GetString("#str_07002"))
                }
            }
            hud.SetStateString(
                "timer",
                if (Warmup()) Common.common.GetLanguageDict()
                    .GetString("#str_04251") else if (gameState == gameState_t.SUDDENDEATH) Common.common.GetLanguageDict()
                    .GetString("#str_04252") else GameTime()
            )
            if (vote != vote_flags_t.VOTE_NONE) {
                hud.SetStateString("vote", Str.va("%s (y: %d n: %d)", voteString, yesVotes.toInt(), noVotes.toInt()))
            } else {
                hud.SetStateString("vote", "")
            }
            hud.SetStateInt("rank_self", 0)
            if (gameState == gameState_t.GAMEON) {
                i = 0
                while (i < numRankedPlayers) {
                    if (Game_local.gameLocal.gameType == gameType_t.GAME_TDM) {
                        hud.SetStateInt(
                            Str.va("player%d_score", i + 1),
                            playerState[rankedPlayers[i].entityNumber].teamFragCount
                        )
                    } else {
                        hud.SetStateInt(
                            Str.va("player%d_score", i + 1),
                            playerState[rankedPlayers[i].entityNumber].fragCount
                        )
                    }
                    hud.SetStateInt(Str.va("rank%d", i + 1), 1)
                    UpdateRankColor(hud, "rank%d_color%d", i + 1, rankedPlayers[i].colorBar)
                    if (rankedPlayers[i] === player) {
                        hud.SetStateInt("rank_self", i + 1)
                    }
                    i++
                }
            }
            i = if (gameState == gameState_t.GAMEON) numRankedPlayers else 0
            while (i < 5) {
                hud.SetStateString(Str.va("player%d", i + 1), "")
                hud.SetStateString(Str.va("player%d_score", i + 1), "")
                hud.SetStateInt(Str.va("rank%d", i + 1), 0)
                i++
            }
        }

        private fun Warmup(): Boolean {
            return gameState == gameState_t.WARMUP
        }

        private fun CheckVote() {
            var numVoters: Int
            var i: Int
            if (vote == vote_flags_t.VOTE_NONE) {
                return
            }
            if (voteExecTime != 0) {
                if (Game_local.gameLocal.time > voteExecTime) {
                    voteExecTime = 0
                    ClientUpdateVote(vote_result_t.VOTE_RESET, 0, 0)
                    ExecuteVote()
                    vote = vote_flags_t.VOTE_NONE
                }
                return
            }

            // count voting players
            numVoters = 0
            i = 0
            while (i < Game_local.gameLocal.numClients) {
                val ent = Game_local.gameLocal.entities[i]
                if (null == ent || ent !is idPlayer) {
                    i++
                    continue
                }
                if (playerState[i].vote != playerVote_t.PLAYER_VOTE_NONE) {
                    numVoters++
                }
                i++
            }
            if (0 == numVoters) {
                // abort
                vote = vote_flags_t.VOTE_NONE
                ClientUpdateVote(vote_result_t.VOTE_ABORTED, yesVotes.toInt(), noVotes.toInt())
                return
            }
            if (yesVotes / numVoters > 0.5f) {
                ClientUpdateVote(vote_result_t.VOTE_PASSED, yesVotes.toInt(), noVotes.toInt())
                voteExecTime = Game_local.gameLocal.time + 2000
                return
            }
            if (Game_local.gameLocal.time > voteTimeOut || noVotes / numVoters >= 0.5f) {
                ClientUpdateVote(vote_result_t.VOTE_FAILED, yesVotes.toInt(), noVotes.toInt())
                vote = vote_flags_t.VOTE_NONE
                return
            }
        }

        private fun AllPlayersReady(): Boolean {
            var i: Int
            var ent: idEntity?
            var p: idPlayer?
            val team = IntArray(2)
            if (NumActualClients(false, team) <= 1) {
                return false
            }
            if (Game_local.gameLocal.gameType == gameType_t.GAME_TDM) {
                if (0 == team[0] || 0 == team[1]) {
                    return false
                }
            }
            if (!Game_local.gameLocal.serverInfo.GetBool("si_warmup")) {
                return true
            }
            i = 0
            while (i < Game_local.gameLocal.numClients) {
                if (Game_local.gameLocal.gameType == gameType_t.GAME_TOURNEY && i != currentTourneyPlayer[0] && i != currentTourneyPlayer[1]
                ) {
                    i++
                    continue
                }
                ent = Game_local.gameLocal.entities[i]
                if (null == ent || ent !is idPlayer) {
                    i++
                    continue
                }
                p = ent
                if (CanPlay(p) && !p.IsReady()) {
                    return false
                }
                team[p.team]++
                i++
            }
            return true
        }

        /*
         ================
         idMultiplayerGame::FragLimitHit
         return the winning player (team player)
         if there is no FragLeader(), the game is tied and we return NULL
         ================
         */
        private fun FragLimitHit(): idPlayer? {
            var i: Int
            var fragLimit = Game_local.gameLocal.serverInfo.GetInt("si_fragLimit")
            val leader: idPlayer?
            leader = FragLeader()
            if (null == leader) {
                return null
            }
            if (fragLimit <= 0) {
                fragLimit = MP_PLAYER_MAXFRAGS
            }
            if (Game_local.gameLocal.gameType == gameType_t.GAME_LASTMAN) {
                // we have a leader, check if any other players have frags left
                assert(!leader.lastManOver)
                i = 0
                while (i < Game_local.gameLocal.numClients) {
                    val ent = Game_local.gameLocal.entities[i]
                    if (null == ent || ent !is idPlayer) {
                        i++
                        continue
                    }
                    if (!CanPlay(ent)) {
                        i++
                        continue
                    }
                    if (ent == leader) {
                        i++
                        continue
                    }
                    if (playerState[ent.entityNumber].fragCount > 0) {
                        return null
                    }
                    i++
                }
                // there is a leader, his score may even be negative, but no one else has frags left or is !lastManOver
                return leader
            } else if (Game_local.gameLocal.gameType == gameType_t.GAME_TDM) {
                if (playerState[leader.entityNumber].teamFragCount >= fragLimit) {
                    return leader
                }
            } else {
                if (playerState[leader.entityNumber].fragCount >= fragLimit) {
                    return leader
                }
            }
            return null
        }

        /*
         ================
         idMultiplayerGame::FragLeader
         return the current winner ( or a player from the winning team )
         NULL if even
         ================
         */
        private fun FragLeader(): idPlayer? {
            var i: Int
            val frags = IntArray(Game_local.MAX_CLIENTS)
            var leader: idPlayer? = null
            var ent: idEntity?
            var p: idPlayer?
            var high = -9999
            var count = 0
            val teamLead /*[ 2 ]*/ = booleanArrayOf(false, false)
            i = 0
            while (i < Game_local.gameLocal.numClients) {
                ent = Game_local.gameLocal.entities[i]
                if (null == ent || ent !is idPlayer) {
                    i++
                    continue
                }
                if (!CanPlay(ent as idPlayer)) {
                    i++
                    continue
                }
                if (Game_local.gameLocal.gameType == gameType_t.GAME_TOURNEY && ent.entityNumber != currentTourneyPlayer[0] && ent.entityNumber != currentTourneyPlayer[1]
                ) {
                    i++
                    continue
                }
                if ((ent as idPlayer).lastManOver) {
                    i++
                    continue
                }
                val fragc =
                    if (Game_local.gameLocal.gameType == gameType_t.GAME_TDM) playerState[i].teamFragCount else playerState[i].fragCount
                if (fragc > high) {
                    high = fragc
                }
                frags[i] = fragc
                i++
            }
            i = 0
            while (i < Game_local.gameLocal.numClients) {
                ent = Game_local.gameLocal.entities[i]
                if (null == ent || ent !is idPlayer) {
                    i++
                    continue
                }
                p = ent
                p.SetLeader(false)
                if (!CanPlay(p)) {
                    i++
                    continue
                }
                if (Game_local.gameLocal.gameType == gameType_t.GAME_TOURNEY && ent.entityNumber != currentTourneyPlayer[0] && ent.entityNumber != currentTourneyPlayer[1]
                ) {
                    i++
                    continue
                }
                if (p.lastManOver) {
                    i++
                    continue
                }
                if (p.spectating) {
                    i++
                    continue
                }
                if (frags[i] >= high) {
                    leader = p
                    count++
                    p.SetLeader(true)
                    if (Game_local.gameLocal.gameType == gameType_t.GAME_TDM) {
                        teamLead[p.team] = true
                    }
                }
                i++
            }
            return if (Game_local.gameLocal.gameType != gameType_t.GAME_TDM) {
                // more than one player at the highest frags
                if (count > 1) {
                    null
                } else {
                    leader
                }
            } else {
                if (teamLead[0] && teamLead[1]) {
                    // even game in team play
                    null
                } else leader
            }
        }

        fun TimeLimitHit(): Boolean {
            val timeLimit = Game_local.gameLocal.serverInfo.GetInt("si_timeLimit")
            return if (timeLimit != 0) {
                Game_local.gameLocal.time >= matchStartedTime + timeLimit * 60000
            } else false
        }

        private fun NewState(
            news: gameState_t,
            player: idPlayer? = null /*= NULL */
        ) {
            val outMsg = idBitMsg()
            var msgBuf = ByteBuffer.allocate(Game_local.MAX_GAME_MESSAGE_SIZE)
            var i: Int
            assert(news != gameState)
            assert(!Game_local.gameLocal.isClient)
            Game_local.gameLocal.DPrintf(
                "%s . %s\n",
                GameStateStrings[gameState.ordinal],
                GameStateStrings[TempDump.etoi(news)]
            )
            when (news) {
                gameState_t.GAMEON -> {
                    Game_local.gameLocal.LocalMapRestart()
                    outMsg.Init(msgBuf, Game_local.MAX_GAME_MESSAGE_SIZE)
                    outMsg.WriteByte(Game_local.GAME_RELIABLE_MESSAGE_RESTART.toByte())
                    outMsg.WriteBits(0, 1)
                    NetworkSystem.networkSystem.ServerSendReliableMessage(-1, outMsg)
                    PlayGlobalSound(-1, snd_evt_t.SND_FIGHT)
                    matchStartedTime = Game_local.gameLocal.time
                    fragLimitTimeout = 0
                    i = 0
                    while (i < Game_local.gameLocal.numClients) {
                        val ent = Game_local.gameLocal.entities[i]
                        if (null == ent || ent !is idPlayer) {
                            i++
                            continue
                        }
                        val p = ent
                        p.SetLeader(false) // don't carry the flag from previous games
                        if (Game_local.gameLocal.gameType == gameType_t.GAME_TOURNEY && currentTourneyPlayer[0] != i && currentTourneyPlayer[1] != i
                        ) {
                            p.ServerSpectate(true)
                            p.tourneyRank++
                        } else {
                            val fragLimit = Game_local.gameLocal.serverInfo.GetInt("si_fragLimit")
                            val startingCount =
                                if (Game_local.gameLocal.gameType == gameType_t.GAME_LASTMAN) fragLimit else 0
                            playerState[i].fragCount = startingCount
                            playerState[i].teamFragCount = startingCount
                            if (!ent.wantSpectate) {
                                ent.ServerSpectate(false)
                                if (Game_local.gameLocal.gameType == gameType_t.GAME_TOURNEY) {
                                    p.tourneyRank = 0
                                }
                            }
                        }
                        p.lastManPresent = CanPlay(p)
                        i++
                    }
                    CVarSystem.cvarSystem.SetCVarString("ui_ready", "Not Ready")
                    switchThrottle[1] = 0 // passby the throttle
                    startFragLimit = Game_local.gameLocal.serverInfo.GetInt("si_fragLimit")
                }

                gameState_t.GAMEREVIEW -> {
                    nextState =
                        gameState_t.INACTIVE // used to abort a game. cancel out any upcoming state change
                    // set all players not ready and spectating
                    i = 0
                    while (i < Game_local.gameLocal.numClients) {
                        val ent = Game_local.gameLocal.entities[i]
                        if (ent == null || ent !is idPlayer) {
                            i++
                            continue
                        }
                        ent.forcedReady = false
                        ent.ServerSpectate(true)
                        i++
                    }
                    UpdateWinsLosses(player)
                }

                gameState_t.SUDDENDEATH -> {
                    PrintMessageEvent(-1, msg_evt_t.MSG_SUDDENDEATH)
                    PlayGlobalSound(-1, snd_evt_t.SND_SUDDENDEATH)
                }

                gameState_t.COUNTDOWN -> {
                    val outMsg2 = idBitMsg()
                    msgBuf = ByteBuffer.allocate(128)
                    warmupEndTime =
                        Game_local.gameLocal.time + 1000 * CVarSystem.cvarSystem.GetCVarInteger("g_countDown")
                    outMsg2.Init(msgBuf, msgBuf.capacity())
                    outMsg2.WriteByte(Game_local.GAME_RELIABLE_MESSAGE_WARMUPTIME.toByte())
                    outMsg2.WriteLong(warmupEndTime)
                    NetworkSystem.networkSystem.ServerSendReliableMessage(-1, outMsg2)
                }

                else -> {}
            }
            gameState = news
        }

        private fun UpdateWinsLosses(winner: idPlayer?) {
            if (winner != null) {
                // run back through and update win/loss count
                for (i in 0 until Game_local.gameLocal.numClients) {
                    val ent = Game_local.gameLocal.entities[i]
                    if (null == ent || ent !is idPlayer) {
                        continue
                    }
                    val player = ent
                    if (Game_local.gameLocal.gameType == gameType_t.GAME_TDM) {
                        if (player === winner || player !== winner && player.team == winner.team) {
                            playerState[i].wins++
                            PlayGlobalSound(player.entityNumber, snd_evt_t.SND_YOUWIN)
                        } else {
                            PlayGlobalSound(player.entityNumber, snd_evt_t.SND_YOULOSE)
                        }
                    } else if (Game_local.gameLocal.gameType == gameType_t.GAME_LASTMAN) {
                        if (player === winner) {
                            playerState[i].wins++
                            PlayGlobalSound(player.entityNumber, snd_evt_t.SND_YOUWIN)
                        } else if (!player.wantSpectate) {
                            PlayGlobalSound(player.entityNumber, snd_evt_t.SND_YOULOSE)
                        }
                    } else if (Game_local.gameLocal.gameType == gameType_t.GAME_TOURNEY) {
                        if (player === winner) {
                            playerState[i].wins++
                            PlayGlobalSound(player.entityNumber, snd_evt_t.SND_YOUWIN)
                        } else if (i == currentTourneyPlayer[0] || i == currentTourneyPlayer[1]) {
                            PlayGlobalSound(player.entityNumber, snd_evt_t.SND_YOULOSE)
                        }
                    } else {
                        if (player === winner) {
                            playerState[i].wins++
                            PlayGlobalSound(player.entityNumber, snd_evt_t.SND_YOUWIN)
                        } else if (!player.wantSpectate) {
                            PlayGlobalSound(player.entityNumber, snd_evt_t.SND_YOULOSE)
                        }
                    }
                }
            }
            lastWinner = winner?.entityNumber ?: -1
        }

        /*
         ================
         idMultiplayerGame::FillTourneySlots
         NOTE: called each frame during warmup to keep the tourney slots filled
         ================
         */
        // fill any empty tourney slots based on the current tourney ranks
        private fun FillTourneySlots() {
            var i: Int
            var j: Int
            var rankmax: Int
            var rankmaxindex: Int
            var ent: idEntity?
            var p: idPlayer?

            // fill up the slots based on tourney ranks
            i = 0
            while (i < 2) {
                if (currentTourneyPlayer[i] != -1) {
                    i++
                    continue
                }
                rankmax = -1
                rankmaxindex = -1
                j = 0
                while (j < Game_local.gameLocal.numClients) {
                    ent = Game_local.gameLocal.entities[j]
                    if (null == ent || ent !is idPlayer) {
                        j++
                        continue
                    }
                    if (currentTourneyPlayer[0] == j || currentTourneyPlayer[1] == j) {
                        j++
                        continue
                    }
                    p = ent
                    if (p.wantSpectate) {
                        j++
                        continue
                    }
                    if (p.tourneyRank >= rankmax) {
                        // when ranks are equal, use time in game
                        if (p.tourneyRank == rankmax) {
                            assert(rankmaxindex >= 0)
                            if (p.spawnedTime > (Game_local.gameLocal.entities[rankmaxindex] as idPlayer).spawnedTime) {
                                j++
                                continue
                            }
                        }
                        rankmax = ent.tourneyRank
                        rankmaxindex = j
                    }
                    j++
                }
                currentTourneyPlayer[i] = rankmaxindex // may be -1 if we found nothing
                i++
            }
        }

        private fun CycleTourneyPlayers() {
            var i: Int
            var ent: idEntity?
            var player: idPlayer
            currentTourneyPlayer[0] = -1
            currentTourneyPlayer[1] = -1
            // if any, winner from last round will play again
            if (lastWinner != -1) {
                val ent2 = Game_local.gameLocal.entities[lastWinner]
                if (ent2 != null && ent2 is idPlayer) {
                    currentTourneyPlayer[0] = lastWinner
                }
            }
            FillTourneySlots()
            // force selected players in/out of the game and update the ranks
            i = 0
            while (i < Game_local.gameLocal.numClients) {
                if (currentTourneyPlayer[0] == i || currentTourneyPlayer[1] == i) {
                    player = Game_local.gameLocal.entities[i] as idPlayer
                    player.ServerSpectate(false)
                } else {
                    ent = Game_local.gameLocal.entities[i]
                    if (ent != null && ent is idPlayer) {
                        player = Game_local.gameLocal.entities[i] as idPlayer
                        player.ServerSpectate(true)
                    }
                }
                i++
            }
            UpdateTourneyLine()
        }

        /*
         ================
         idMultiplayerGame::UpdateTourneyLine
         we manipulate tourneyRank on player entities for internal ranking. it's easier to deal with.
         but we need a real wait list to be synced down to clients for GUI
         ignore current players, ignore wantSpectate
         ================
         */
        // walk through the tourneyRank to build a wait list for the clients
        private fun UpdateTourneyLine() {
            var i: Int
            var j: Int
            var imax: Int
            var max: Int
            var globalmax = -1
            var p: idPlayer
            assert(!Game_local.gameLocal.isClient)
            if (Game_local.gameLocal.gameType != gameType_t.GAME_TOURNEY) {
                return
            }
            j = 1
            while (j <= Game_local.gameLocal.numClients) {
                max = -1
                imax = -1
                i = 0
                while (i < Game_local.gameLocal.numClients) {
                    if (currentTourneyPlayer[0] == i || currentTourneyPlayer[1] == i) {
                        i++
                        continue
                    }
                    p = Game_local.gameLocal.entities[i] as idPlayer
                    if (null == p || p.wantSpectate) {
                        i++
                        continue
                    }
                    if (p.tourneyRank > max && (globalmax == -1 || p.tourneyRank < globalmax)) {
                        imax = i
                        max = p.tourneyRank
                    }
                    i++
                }
                if (imax == -1) {
                    break
                }
                val outMsg = idBitMsg()
                val msgBuf = ByteBuffer.allocate(1024)
                outMsg.Init(msgBuf, msgBuf.capacity())
                outMsg.WriteByte(Game_local.GAME_RELIABLE_MESSAGE_TOURNEYLINE.toByte())
                outMsg.WriteByte(j.toByte())
                NetworkSystem.networkSystem.ServerSendReliableMessage(imax, outMsg)
                globalmax = max
                j++
            }
        }

        private fun GameTime(): String {
            val m: Int
            var s: Int
            val t: Int
            var ms: Int
            if (gameState == gameState_t.COUNTDOWN) {
                ms = warmupEndTime - Game_local.gameLocal.realClientTime
                s = ms / 1000 + 1
                if (ms <= 0) {
//                    strcpy(buff, "WMP --");
                    buff = "WMP --"
                } else {
                    buff = String.format("WMP %d", s)
                }
            } else {
                val timeLimit = Game_local.gameLocal.serverInfo.GetInt("si_timeLimit")
                ms = if (timeLimit != 0) {
                    timeLimit * 60000 - (Game_local.gameLocal.time - matchStartedTime)
                } else {
                    Game_local.gameLocal.time - matchStartedTime
                }
                if (ms < 0) {
                    ms = 0
                }
                s = ms / 1000
                m = s / 60
                s -= m * 60
                t = s / 10
                s -= t * 10
                buff = String.format("%d:%d%d", m, t, s)
            }
            return buff
        }

        private fun Clear() {
            var i: Int
            gameState = gameState_t.INACTIVE
            nextState = gameState_t.INACTIVE
            pingUpdateTime = 0
            vote = vote_flags_t.VOTE_NONE
            voteTimeOut = 0
            voteExecTime = 0
            nextStateSwitch = 0
            matchStartedTime = 0
            currentTourneyPlayer[0] = -1
            currentTourneyPlayer[1] = -1
            three = false
            two = three
            one = two
            playerState =
                Array(playerState.size) { mpPlayerState_s() }
            lastWinner = -1
            currentMenu = 0
            bCurrentMenuMsg = false
            nextMenu = 0
            pureReady = false
            scoreBoard = null
            spectateGui = null
            guiChat = null
            mainGui = null
            msgmodeGui = null
            if (mapList != null) {
                UserInterface.uiManager.FreeListGUI(mapList!!)
                mapList = null
            }
            fragLimitTimeout = 0
            //	memset( &switchThrottle, 0, sizeof( switchThrottle ) );
            switchThrottle = IntArray(switchThrottle.size)
            voiceChatThrottle = 0
            i = 0
            while (i < NUM_CHAT_NOTIFY) {
                chatHistory[i].line.Clear()
                i++
            }
            warmupText = idStr()
            voteValue = idStr()
            voteString = idStr()
            startFragLimit = -1
        }

        private fun EnoughClientsToPlay(): Boolean {
            val team = IntArray(2)
            val clients = NumActualClients(false, team)
            return if (Game_local.gameLocal.gameType == gameType_t.GAME_TDM) {
                clients >= 2 && team[0] != 0 && team[1] != 0
            } else {
                clients >= 2
            }
        }

        private fun ClearChatData() {
            chatHistoryIndex = 0
            chatHistorySize = 0
            chatDataUpdated = true
        }

        private fun DrawChat() {
            var i: Int
            var j: Int
            if (guiChat != null) {
                val guiChat = guiChat!!
                if (Game_local.gameLocal.time - lastChatLineTime > CHAT_FADE_TIME) {
                    if (chatHistorySize > 0) {
                        i = chatHistoryIndex - chatHistorySize
                        while (i < chatHistoryIndex) {
                            chatHistory[i % NUM_CHAT_NOTIFY].fade--
                            if (chatHistory[i % NUM_CHAT_NOTIFY].fade < 0) {
                                chatHistorySize-- // this assumes the removals are always at the beginning
                            }
                            i++
                        }
                        chatDataUpdated = true
                    }
                    lastChatLineTime = Game_local.gameLocal.time
                }
                if (chatDataUpdated) {
                    j = 0
                    i = chatHistoryIndex - chatHistorySize
                    while (i < chatHistoryIndex) {
                        guiChat.SetStateString(
                            Str.va("chat%d", j),
                            chatHistory[i % NUM_CHAT_NOTIFY].line.toString()
                        )
                        // don't set alpha above 4, the gui only knows that
                        guiChat.SetStateInt(
                            Str.va("alpha%d", j),
                            Min(4, chatHistory[i % NUM_CHAT_NOTIFY].fade.toInt())
                        )
                        j++
                        i++
                    }
                    while (j < NUM_CHAT_NOTIFY) {
                        guiChat.SetStateString(Str.va("chat%d", j), "")
                        j++
                    }
                    guiChat.Activate(true, Game_local.gameLocal.time)
                    chatDataUpdated = false
                }
                guiChat.Redraw(Game_local.gameLocal.time)
            }
        }

        // go through the clients, and see if they want to be respawned, and if the game allows it
        // called during normal gameplay for death -> respawn cycles
        // and for a spectator who want back in the game (see param)
        private fun CheckRespawns(spectator: idPlayer? = null /*= NULL*/) {
            for (i in 0 until Game_local.gameLocal.numClients) {
                val ent = Game_local.gameLocal.entities[i]
                if (null == ent || ent !is idPlayer) {
                    continue
                }
                val p = ent
                // once we hit sudden death, nobody respawns till game has ended
                if (WantRespawn(p) || p === spectator) {
                    if (gameState == gameState_t.SUDDENDEATH && Game_local.gameLocal.gameType != gameType_t.GAME_LASTMAN) {
                        // respawn rules while sudden death are different
                        // sudden death may trigger while a player is dead, so there are still cases where we need to respawn
                        // don't do any respawns while we are in end game delay though
                        if (0 == fragLimitTimeout) {
                            if (Game_local.gameLocal.gameType == gameType_t.GAME_TDM || p.IsLeader()) {
                                if (BuildDefines._DEBUG) {
                                    assert(
                                        Game_local.gameLocal.gameType != gameType_t.GAME_TOURNEY || p.entityNumber == currentTourneyPlayer[0] || p.entityNumber == currentTourneyPlayer[1]
                                    )
                                }
                                p.ServerSpectate(false)
                            } else if (!p.IsLeader()) {
                                // sudden death is rolling, this player is not a leader, have him spectate
                                p.ServerSpectate(true)
                                CheckAbortGame()
                            }
                        }
                    } else {
                        if (Game_local.gameLocal.gameType == gameType_t.GAME_DM
                            || Game_local.gameLocal.gameType == gameType_t.GAME_TDM
                        ) {
                            if (gameState == gameState_t.WARMUP || gameState == gameState_t.COUNTDOWN || gameState == gameState_t.GAMEON) {
                                p.ServerSpectate(false)
                            }
                        } else if (Game_local.gameLocal.gameType == gameType_t.GAME_TOURNEY) {
                            if (i == currentTourneyPlayer[0] || i == currentTourneyPlayer[1]) {
                                if (gameState == gameState_t.WARMUP || gameState == gameState_t.COUNTDOWN || gameState == gameState_t.GAMEON) {
                                    p.ServerSpectate(false)
                                }
                            } else if (gameState == gameState_t.WARMUP) {
                                // make sure empty tourney slots get filled first
                                FillTourneySlots()
                                if (i == currentTourneyPlayer[0] || i == currentTourneyPlayer[1]) {
                                    p.ServerSpectate(false)
                                }
                            }
                        } else if (Game_local.gameLocal.gameType == gameType_t.GAME_LASTMAN) {
                            if (gameState == gameState_t.WARMUP || gameState == gameState_t.COUNTDOWN) {
                                p.ServerSpectate(false)
                            } else if (gameState == gameState_t.GAMEON || gameState == gameState_t.SUDDENDEATH) {
                                if (gameState == gameState_t.GAMEON && playerState[i].fragCount > 0 && p.lastManPresent
                                ) {
                                    assert(!p.lastManOver)
                                    p.ServerSpectate(false)
                                } else if (p.lastManPlayAgain && p.lastManPresent) {
                                    assert(gameState == gameState_t.SUDDENDEATH)
                                    p.ServerSpectate(false)
                                } else {
                                    // if a fragLimitTimeout was engaged, do NOT mark lastManOver as that could mean
                                    // everyone ends up spectator and game is stalled with no end
                                    // if the frag limit delay is engaged and cancels out before expiring, LMN players are
                                    // respawned to play the tie again ( through SuddenRespawn and lastManPlayAgain )
                                    if (0 == fragLimitTimeout && !p.lastManOver) {
                                        Common.common.DPrintf("client %d has lost all last man lives\n", i)
                                        // end of the game for this guy, send him to spectators
                                        p.lastManOver = true
                                        // clients don't have access to lastManOver
                                        // so set the fragCount to something silly ( used in scoreboard and player ranking )
                                        playerState[i].fragCount = LASTMAN_NOLIVES
                                        p.ServerSpectate(true)

                                        //Check for a situation where the last two player dies at the same time and don't
                                        //try to respawn manually...This was causing all players to go into spectate mode
                                        //and the server got stuck
                                        run {
                                            var j: Int
                                            j = 0
                                            while (j < Game_local.gameLocal.numClients) {
                                                if (null == Game_local.gameLocal.entities[j]) {
                                                    j++
                                                    continue
                                                }
                                                if (!CanPlay(Game_local.gameLocal.entities[j] as idPlayer)) {
                                                    j++
                                                    continue
                                                }
                                                if (!(Game_local.gameLocal.entities[j] as idPlayer).lastManOver) {
                                                    break
                                                }
                                                j++
                                            }
                                            if (j == Game_local.gameLocal.numClients) {
                                                //Everyone is dead so don't allow this player to spectate
                                                //so the match will end
                                                p.ServerSpectate(false)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else if (p.wantSpectate && !p.spectating) {
                    playerState[i].fragCount =
                        0 // whenever you willingly go spectate during game, your score resets
                    p.ServerSpectate(true)
                    UpdateTourneyLine()
                    CheckAbortGame()
                }
            }
        }

        private fun ForceReady() {
            for (i in 0 until Game_local.gameLocal.numClients) {
                val ent = Game_local.gameLocal.entities[i]
                if (null == ent || ent !is idPlayer) {
                    continue
                }
                val p = ent
                if (!p.IsReady()) {
                    PrintMessageEvent(-1, msg_evt_t.MSG_FORCEREADY, i)
                    p.forcedReady = true
                }
            }
        }

        // when clients disconnect or join spectate during game, check if we need to end the game
        private fun CheckAbortGame() {
            var i: Int
            if (Game_local.gameLocal.gameType == gameType_t.GAME_TOURNEY && gameState == gameState_t.WARMUP) {
                // if a tourney player joined spectators, let someone else have his spot
                i = 0
                while (i < 2) {
                    if (Game_local.gameLocal.entities[currentTourneyPlayer[i]] == null || (Game_local.gameLocal.entities[currentTourneyPlayer[i]] as idPlayer).spectating
                    ) {
                        currentTourneyPlayer[i] = -1
                    }
                    i++
                }
            }
            // only checks for aborts . game review below
            if (gameState != gameState_t.COUNTDOWN && gameState != gameState_t.GAMEON && gameState != gameState_t.SUDDENDEATH) {
                return
            }
            when (Game_local.gameLocal.gameType) {
                gameType_t.GAME_TOURNEY -> {
                    i = 0
                    while (i < 2) {
                        if (Game_local.gameLocal.entities[currentTourneyPlayer[i]] == null || (Game_local.gameLocal.entities[currentTourneyPlayer[i]] as idPlayer).spectating
                        ) {
                            NewState(gameState_t.GAMEREVIEW)
                            return
                        }
                        i++
                    }
                }

                else -> if (!EnoughClientsToPlay()) {
                    NewState(gameState_t.GAMEREVIEW)
                }
            }
        }

        private fun MessageMode(args: CmdArgs.idCmdArgs?) {
            val mode: String?
            val imode: Int
            if (!Game_local.gameLocal.isMultiplayer) {
                Common.common.Printf("clientMessageMode: only valid in multiplayer\n")
                return
            }
            if (null == mainGui) {
                Common.common.Printf("no local client\n")
                return
            }
            mode = args!!.Argv(1)
            imode = if (mode.isEmpty()) {
                0
            } else {
                mode.toInt()
            }
            msgmodeGui!!.SetStateString("messagemode", if (imode != 0) "1" else "0")
            msgmodeGui!!.SetStateString("chattext", "")
            nextMenu = 2
            // let the session know that we want our ingame main menu opened
            Game_local.gameLocal.sessionCommand.set("game_startmenu")
        }

        private fun DisableMenu() {
            Game_local.gameLocal.sessionCommand.set("") // in case we used "game_startMenu" to trigger the menu
            if (currentMenu == 1) {
                mainGui!!.Activate(false, Game_local.gameLocal.time)
            } else if (currentMenu == 2) {
                msgmodeGui!!.Activate(false, Game_local.gameLocal.time)
            }
            currentMenu = 0
            nextMenu = 0
            CVarSystem.cvarSystem.SetCVarBool("ui_chat", false)
        }

        private fun SetMapShot() {
//            char[] screenshot = new char[MAX_STRING_CHARS];
            val screenshot = StringBuffer()
            val mapNum = mapList!!.GetSelection(null, 0)
            var dict: idDict? = null
            if (mapNum >= 0) {
                dict = FileSystem_h.fileSystem.GetMapDecl(mapNum)
            }
            FileSystem_h.fileSystem.FindMapScreenshot(
                if (dict != null) dict.GetString("path") else "",
                screenshot,
                MAX_STRING_CHARS
            )
            mainGui!!.SetStateString("current_levelshot", screenshot.toString())
        }

        private fun TeamScore(entityNumber: Int, team: Int, delta: Int) {
            playerState[entityNumber].fragCount += delta
            for (i in 0 until Game_local.gameLocal.numClients) {
                val ent = Game_local.gameLocal.entities[i]
                if (null == ent || ent !is idPlayer) {
                    continue
                }
                val player = ent
                if (player.team == team) {
                    playerState[player.entityNumber].teamFragCount += delta
                }
            }
        }

        private fun VoiceChat(args: CmdArgs.idCmdArgs, team: Boolean) {
            val outMsg = idBitMsg()
            val msgBuf = ByteBuffer.allocate(128)
            val voc: String?
            val spawnArgs: idDict?
            var keyval: idKeyValue?
            var index: Int
            if (!Game_local.gameLocal.isMultiplayer) {
                Common.common.Printf("clientVoiceChat: only valid in multiplayer\n")
                return
            }
            if (args.Argc() != 2) {
                Common.common.Printf("clientVoiceChat: bad args\n")
                return
            }
            // throttle
            if (Game_local.gameLocal.realClientTime < voiceChatThrottle) {
                return
            }
            voc = args.Argv(1)
            spawnArgs = Game_local.gameLocal.FindEntityDefDict("player_doommarine", false)!!
            keyval = spawnArgs.MatchPrefix("snd_voc_", null)
            index = 0
            while (keyval != null) {
                if (0 == keyval.GetValue().Icmp(voc)) {
                    break
                }
                keyval = spawnArgs.MatchPrefix("snd_voc_", keyval)
                index++
            }
            if (null == keyval) {
                Common.common.Printf("Voice command not found: %s\n", voc)
                return
            }
            voiceChatThrottle = Game_local.gameLocal.realClientTime + 1000
            outMsg.Init(msgBuf, msgBuf.capacity())
            outMsg.WriteByte(Game_local.GAME_RELIABLE_MESSAGE_VCHAT.toByte())
            outMsg.WriteLong(index)
            outMsg.WriteBits(if (team) 1 else 0, 1)
            NetworkSystem.networkSystem.ClientSendReliableMessage(outMsg)
        }

        private fun DumpTourneyLine() {
            var i: Int
            i = 0
            while (i < Game_local.gameLocal.numClients) {
                if (Game_local.gameLocal.entities[i] != null && Game_local.gameLocal.entities[i] is idPlayer) {
                    Common.common.Printf(
                        "client %d: rank %d\n",
                        i,
                        (Game_local.gameLocal.entities[i] as idPlayer).tourneyRank
                    )
                }
                i++
            }
        }

        /*
         ================
         idMultiplayerGame::SuddenRespawns
         solely for LMN if an end game ( fragLimitTimeout ) was entered and aborted before expiration
         LMN players which still have lives left need to be respawned without being marked lastManOver
         ================
         */
        private fun SuddenRespawn() {
            var i: Int
            if (Game_local.gameLocal.gameType != gameType_t.GAME_LASTMAN) {
                return
            }
            i = 0
            while (i < Game_local.gameLocal.numClients) {
                if (null == Game_local.gameLocal.entities[i] || Game_local.gameLocal.entities[i] !is idPlayer) {
                    i++
                    continue
                }
                if (!CanPlay(Game_local.gameLocal.entities[i] as idPlayer)) {
                    i++
                    continue
                }
                if ((Game_local.gameLocal.entities[i] as idPlayer).lastManOver) {
                    i++
                    continue
                }
                (Game_local.gameLocal.entities[i] as idPlayer).lastManPlayAgain = true
                i++
            }
        }

        // game state
        enum class gameState_t {
            INACTIVE,  //= 0,						// not running
            WARMUP,  // warming up
            COUNTDOWN,  // post warmup pre-game
            GAMEON,  // game is on
            SUDDENDEATH,  // game is on but in sudden death, first frag wins
            GAMEREVIEW,  // game is over, scoreboard is up. we wait si_gameReviewPause seconds (which has a min value)
            NEXTGAME, STATE_COUNT
        }

        // more compact than a chat line
        enum class msg_evt_t {
            MSG_SUICIDE,  // = 0,
            MSG_KILLED, MSG_KILLEDTEAM, MSG_DIED, MSG_VOTE, MSG_VOTEPASSED, MSG_VOTEFAILED, MSG_SUDDENDEATH, MSG_FORCEREADY, MSG_JOINEDSPEC, MSG_TIMELIMIT, MSG_FRAGLIMIT, MSG_TELEFRAGGED, MSG_JOINTEAM, MSG_HOLYSHIT, MSG_COUNT
        }

        enum class vote_flags_t {
            VOTE_RESTART,  //= 0,
            VOTE_TIMELIMIT, VOTE_FRAGLIMIT, VOTE_GAMETYPE, VOTE_KICK, VOTE_MAP, VOTE_SPECTATORS, VOTE_NEXTMAP, VOTE_COUNT, VOTE_NONE
        }

        enum class vote_result_t {
            VOTE_UPDATE, VOTE_FAILED, VOTE_PASSED,  // passed, but no reset yet
            VOTE_ABORTED, VOTE_RESET // tell clients to reset vote state
        }

        class ForceReady_f private constructor() : cmdFunction_t() {
            override fun run(args: CmdArgs.idCmdArgs?) {
                if (!Game_local.gameLocal.isMultiplayer || Game_local.gameLocal.isClient) {
                    Common.common.Printf("forceReady: multiplayer server only\n")
                    return
                }
                Game_local.gameLocal.mpGame.ForceReady()
            }

            companion object {
                private val instance: cmdFunction_t = ForceReady_f()
                fun getInstance(): cmdFunction_t {
                    return instance
                }
            }
        }

        // scores in TDM
        class DropWeapon_f private constructor() : cmdFunction_t() {
            override fun run(args: CmdArgs.idCmdArgs?) {
                if (!Game_local.gameLocal.isMultiplayer) {
                    Common.common.Printf("clientDropWeapon: only valid in multiplayer\n")
                    return
                }
                val outMsg = idBitMsg()
                val msgBuf = ByteBuffer.allocate(128)
                outMsg.Init(msgBuf, msgBuf.capacity())
                outMsg.WriteByte(Game_local.GAME_RELIABLE_MESSAGE_DROPWEAPON.toByte())
                NetworkSystem.networkSystem.ClientSendReliableMessage(outMsg)
            }

            companion object {
                private val instance: cmdFunction_t = DropWeapon_f()
                fun getInstance(): cmdFunction_t {
                    return instance
                }
            }
        }

        class MessageMode_f private constructor() : cmdFunction_t() {
            override fun run(args: CmdArgs.idCmdArgs?) {
                Game_local.gameLocal.mpGame.MessageMode(args)
            }

            companion object {
                private val instance: cmdFunction_t = MessageMode_f()
                fun getInstance(): cmdFunction_t {
                    return instance
                }
            }
        }

        class VoiceChat_f private constructor() : cmdFunction_t() {
            override fun run(args: CmdArgs.idCmdArgs?) {
                Game_local.gameLocal.mpGame.VoiceChat(args!!, false)
            }

            companion object {
                private val instance: cmdFunction_t = VoiceChat_f()
                fun getInstance(): cmdFunction_t {
                    return instance
                }
            }
        }

        class VoiceChatTeam_f private constructor() : cmdFunction_t() {
            override fun run(args: CmdArgs.idCmdArgs?) {
                Game_local.gameLocal.mpGame.VoiceChat(args!!, true)
            }

            companion object {
                private val instance: cmdFunction_t = VoiceChatTeam_f()
                fun getInstance(): cmdFunction_t {
                    return instance
                }
            }
        }

        companion object {
            val ASYNC_PLAYER_FRAG_BITS =
                -idMath.BitsForInteger(MP_PLAYER_MAXFRAGS - MP_PLAYER_MINFRAGS) // player can have negative frags
            val ASYNC_PLAYER_PING_BITS = idMath.BitsForInteger(MP_PLAYER_MAXPING)
            val ASYNC_PLAYER_WINS_BITS = idMath.BitsForInteger(MP_PLAYER_MAXWINS)

            // handy verbose
            val GameStateStrings: Array<String> = arrayOf( //new String[STATE_COUNT];
                "INACTIVE",
                "WARMUP",
                "COUNTDOWN",
                "GAMEON",
                "SUDDENDEATH",
                "GAMEREVIEW",
                "NEXTGAME"
            )

            // global sounds transmitted by index - 0 .. SND_COUNT
            // sounds in this list get precached on MP start
            val GlobalSoundStrings: Array<String> = arrayOf( //new String[ SND_COUNT ];
                "sound/feedback/voc_youwin.wav",
                "sound/feedback/voc_youlose.wav",
                "sound/feedback/fight.wav",
                "sound/feedback/vote_now.wav",
                "sound/feedback/vote_passed.wav",
                "sound/feedback/vote_failed.wav",
                "sound/feedback/three.wav",
                "sound/feedback/two.wav",
                "sound/feedback/one.wav",
                "sound/feedback/sudden_death.wav"
            )

            //
            //
            private val MPGuis: Array<String> = arrayOf(
                "guis/mphud.gui",
                "guis/mpmain.gui",
                "guis/mpmsgmode.gui",
                "guis/netmenu.gui"
            )
            private val ThrottleDelay: IntArray = intArrayOf(
                8,
                5,
                5
            )
            private val ThrottleVars: Array<String> = arrayOf(
                "ui_spectate",
                "ui_ready",
                "ui_team"
            )
            private val ThrottleVarsInEnglish: Array<String> = arrayOf(
                "#str_06738",
                "#str_06737",
                "#str_01991"
            )

            //	private static final char []buff = new char[16];
            private var buff: String = ""

            /*
         ================
         idMultiplayerGame::Vote_f
         FIXME: voting from console
         ================
         */
            fun Vote_f(args: CmdArgs.idCmdArgs?) {}

            /*
         ================
         idMultiplayerGame::CallVote_f
         FIXME: voting from console
         ================
         */
            fun CallVote_f(args: CmdArgs.idCmdArgs?) {}
        }

        init {
            lastGameType = gameType_t.GAME_SP
            Clear()
        }
    }
}
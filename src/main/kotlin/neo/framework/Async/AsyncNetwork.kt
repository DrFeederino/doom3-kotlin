package neo.framework.Async

import neo.Game.Game_local
import neo.Renderer.RenderSystem.renderSystem
import neo.Sound.snd_system.Companion.soundSystem
import neo.TempDump
import neo.framework.*
import neo.framework.Async.AsyncClient.idAsyncClient
import neo.framework.Async.AsyncServer.idAsyncServer
import neo.framework.CVarSystem.cvarSystem
import neo.framework.CVarSystem.idCVar
import neo.framework.CmdSystem.cmdExecution_t
import neo.framework.CmdSystem.cmdFunction_t
import neo.framework.CmdSystem.cmdSystem
import neo.framework.CmdSystem.idCmdSystem.ArgCompletion_Integer
import neo.framework.CmdSystem.idCmdSystem.ArgCompletion_MapName
import neo.framework.Common.Companion.com_asyncInput
import neo.framework.UsercmdGen.inhibit_t
import neo.framework.UsercmdGen.usercmd_t
import neo.idlib.BitMsg.idBitMsg
import neo.idlib.CmdArgs.idCmdArgs
import neo.idlib.Text.Str.idStr
import neo.idlib.idException
import neo.sys.sys_public.netadr_t
import neo.sys.win_input
import neo.sys.win_net
import neo.sys.win_syscon.Sys_ShowConsole
import kotlin.experimental.and
import kotlin.math.abs


class AsyncNetwork {
    // reliable client -> server messages
    enum class CLIENT_RELIABLE {
        CLIENT_RELIABLE_MESSAGE_PURE, CLIENT_RELIABLE_MESSAGE_CLIENTINFO, CLIENT_RELIABLE_MESSAGE_PRINT, CLIENT_RELIABLE_MESSAGE_DISCONNECT, CLIENT_RELIABLE_MESSAGE_GAME
    }

    // unreliable client -> server messages
    enum class CLIENT_UNRELIABLE {
        CLIENT_UNRELIABLE_MESSAGE_EMPTY, CLIENT_UNRELIABLE_MESSAGE_PINGRESPONSE, CLIENT_UNRELIABLE_MESSAGE_USERCMD
    }

    enum class SERVER_DL {
        _0_, SERVER_DL_REDIRECT, SERVER_DL_LIST, SERVER_DL_NONE
    }

    enum class SERVER_PAK {
        SERVER_PAK_NO, SERVER_PAK_YES, SERVER_PAK_END
    }

    // server print messages
    enum class SERVER_PRINT {
        SERVER_PRINT_MISC, SERVER_PRINT_BADPROTOCOL, SERVER_PRINT_RCON, SERVER_PRINT_GAMEDENY, SERVER_PRINT_BADCHALLENGE
    }

    // reliable server -> client messages
    enum class SERVER_RELIABLE {
        SERVER_RELIABLE_MESSAGE_PURE, SERVER_RELIABLE_MESSAGE_RELOAD, SERVER_RELIABLE_MESSAGE_CLIENTINFO, SERVER_RELIABLE_MESSAGE_SYNCEDCVARS, SERVER_RELIABLE_MESSAGE_PRINT, SERVER_RELIABLE_MESSAGE_DISCONNECT, SERVER_RELIABLE_MESSAGE_APPLYSNAPSHOT, SERVER_RELIABLE_MESSAGE_GAME, SERVER_RELIABLE_MESSAGE_ENTERGAME
    }

    /*
     ===============================================================================

     Asynchronous Networking.

     ===============================================================================
     */
    // unreliable server -> client messages
    enum class SERVER_UNRELIABLE {
        SERVER_UNRELIABLE_MESSAGE_EMPTY, SERVER_UNRELIABLE_MESSAGE_PING, SERVER_UNRELIABLE_MESSAGE_GAMEINIT, SERVER_UNRELIABLE_MESSAGE_SNAPSHOT
    }

    /*master_t*/
    class master_s {
        var address: netadr_t = netadr_t()
        lateinit var cVar: idCVar
        var resolved = false
    }

    class idAsyncNetwork {
        companion object {
            private var realTime = 0
            private val masters = Array(MAX_MASTER_SERVERS) { master_s() }
            private val instance: cmdFunction_t = SpawnServer_f()

            fun getInstance(): cmdFunction_t {
                return instance
            }


            /*
             ==================
             idAsyncNetwork::NextMap_f
             ==================
             */
            private class NextMap_f private constructor() : cmdFunction_t() {
                override fun run(args: idCmdArgs?) {
                    server.ExecuteMapChange()
                }

                companion object {
                    val instance: cmdFunction_t = NextMap_f()
                }
            }

            /*
            ==================
            idAsyncNetwork::Connect_f
            ==================
            */
            private class Connect_f private constructor() : cmdFunction_t() {
                @Throws(idException::class)
                override fun run(args: idCmdArgs?) {
                    if (server.IsActive()) {
                        Common.common.Printf("already running a server\n")
                        return
                    }
                    if (args!!.Argc() !== 2) {
                        Common.common.Printf("USAGE: connect <serverName>\n")
                        return
                    }
                    com_asyncInput.SetBool(false)
                    client.ConnectToServer(args!!.Argv(1))
                }

                companion object {
                    val instance: cmdFunction_t = Connect_f()
                }
            }

            /*
         ==================
         idAsyncNetwork::SpawnServer_f
         ==================
         */
            private class SpawnServer_f : cmdFunction_t() {
                @Throws(idException::class)
                override fun run(args: idCmdArgs?) {
                    if (args!!.Argc() > 1) {
                        cvarSystem.SetCVarString("si_map", args.Argv(1))
                    }

                    // don't let a server spawn with singleplayer game type - it will crash
                    if (idStr.Icmp(cvarSystem.GetCVarString("si_gameType"), "singleplayer") == 0) {
                        cvarSystem.SetCVarString("si_gameType", "deathmatch")
                    }
                    com_asyncInput.SetBool(false)
                    when (cvarSystem.GetCVarInteger("net_serverDedicated")) {
                        0, 2 -> if (!renderSystem.IsOpenGLRunning()) {
                            Common.common.Warning(
                                "OpenGL is not running, net_serverDedicated == %d",
                                cvarSystem.GetCVarInteger("net_serverDedicated")
                            )
                        }

                        1 -> {
                            if (renderSystem.IsOpenGLRunning()) {
                                Sys_ShowConsole(1, false)
                                renderSystem.ShutdownOpenGL()
                            }
                            soundSystem.SetMute(true)
                            soundSystem.ShutdownHW()
                        }
                    }
                    // use serverMapRestart if we already have a running server
                    if (server.IsActive()) {
                        cmdSystem.BufferCommandText(cmdExecution_t.CMD_EXEC_NOW, "serverMapRestart")
                    } else {
                        server.Spawn()
                    }
                }

                companion object {
                    val instance: cmdFunction_t = SpawnServer_f()
                }
            }

            /*
         =================
         idAsyncNetwork::UpdateUI_f
         =================
         */
            private class UpdateUI_f private constructor() : cmdFunction_t() {
                override fun run(args: idCmdArgs?) {
                    if (args!!.Argc() !== 2) {
                        Common.common.Warning("idAsyncNetwork::UpdateUI_f: wrong arguments\n")
                        return
                    }
                    if (!server.IsActive()) {
                        Common.common.Warning("idAsyncNetwork::UpdateUI_f: server is not active\n")
                        return
                    }
                    val clientNum = args!!.Args(1).toInt()
                    server.UpdateUI(clientNum)
                }

                companion object {
                    val instance: cmdFunction_t = UpdateUI_f()
                }
            }

            /*
            ==================
            idAsyncNetwork::Reconnect_f
            ==================
            */
            private class Reconnect_f private constructor() : cmdFunction_t() {
                override fun run(args: idCmdArgs?) {
                    client.Reconnect()
                }

                companion object {
                    val instance: cmdFunction_t = Reconnect_f()
                }
            }

            /*
            ==================
            idAsyncNetwork::GetServerInfo_f
            ==================
            */
            private class GetServerInfo_f private constructor() : cmdFunction_t() {
                override fun run(args: idCmdArgs?) {
                    client.GetServerInfo(args!!.Argv(1))
                }

                companion object {
                    val instance: cmdFunction_t = GetServerInfo_f()
                }
            }

            /*
         ==================
         idAsyncNetwork::GetLANServers_f
         ==================
         */
            private class GetLANServers_f private constructor() : cmdFunction_t() {
                override fun run(args: idCmdArgs?) {
                    client.GetLANServers()
                }

                companion object {
                    val instance: cmdFunction_t = GetLANServers_f()
                }
            }

            /*
            ==================
            idAsyncNetwork::ListServers_f
            ==================
            */
            private class ListServers_f private constructor() : cmdFunction_t() {
                override fun run(args: idCmdArgs?) {
                    client.ListServers()
                }

                companion object {
                    val instance: cmdFunction_t = ListServers_f()
                }
            }

            /*
             ==================
             idAsyncNetwork::CheckNewVersion_f
             ==================
             */
            private class CheckNewVersion_f private constructor() : cmdFunction_t() {
                override fun run(args: idCmdArgs?) {
                    client.SendVersionCheck()
                }

                companion object {
                    val instance: cmdFunction_t = CheckNewVersion_f()
                }
            }

            /*
         ==================
         idAsyncNetwork::Kick_f
         ==================
         */
            private class Kick_f private constructor() : cmdFunction_t() {
                override fun run(args: idCmdArgs?) {
                    val clientId: idStr
                    val iclient: Int
                    if (!server.IsActive()) {
                        Common.common.Printf("server is not running\n")
                        return
                    }
                    clientId = idStr(args!!.Argv(1))
                    if (!clientId.IsNumeric()) {
                        Common.common.Printf("usage: kick <client number>\n")
                        return
                    }
                    iclient = clientId.toString().toInt()
                    if (server.GetLocalClientNum() == iclient) {
                        Common.common.Printf("can't kick the host\n")
                        return
                    }
                    server.DropClient(iclient, "#str_07134")
                }

                companion object {
                    val instance: cmdFunction_t = Kick_f()
                }
            }

            /*
         ==================
         idAsyncNetwork::Heartbeat_f
         ==================
         */
            private class Heartbeat_f private constructor() : cmdFunction_t() {
                override fun run(args: idCmdArgs?) {
                    if (!server.IsActive()) {
                        Common.common.Printf("server is not running\n")
                        return
                    }
                    server.MasterHeartbeat(true)
                }

                companion object {
                    val instance: cmdFunction_t = Heartbeat_f()
                }
            }

            /*
         ==================
         idAsyncNetwork::RemoteConsole_f
         ==================
         */
            private class RemoteConsole_f private constructor() : cmdFunction_t() {
                override fun run(args: idCmdArgs?) {
                    client.RemoteConsole(args!!.Args())
                }

                companion object {
                    val instance: cmdFunction_t = RemoteConsole_f()
                }
            }

            @Throws(idException::class)
            fun Init() {
                realTime = 0
                masters[0].cVar = master0
                masters[1].cVar = master1
                masters[2].cVar = master2
                masters[3].cVar = master3
                masters[4].cVar = master4
                if (!ID_DEMO_BUILD) { //#ifndef
                    cmdSystem.AddCommand(
                        "spawnServer",
                        SpawnServer_f.instance,
                        CmdSystem.CMD_FL_SYSTEM,
                        "spawns a server",
                        ArgCompletion_MapName.getInstance()
                    )
                    cmdSystem.AddCommand(
                        "nextMap",
                        Game_local.idGameLocal.NextMap_f.getInstance(),
                        CmdSystem.CMD_FL_SYSTEM,
                        "loads the next map on the server"
                    )
                    cmdSystem.AddCommand(
                        "connect", Connect_f.instance, CmdSystem.CMD_FL_SYSTEM, "connects to a server"
                    )
                    cmdSystem.AddCommand(
                        "reconnect",
                        Reconnect_f.instance,
                        CmdSystem.CMD_FL_SYSTEM,
                        "reconnect to the last server we tried to connect to"
                    )
                    cmdSystem.AddCommand(
                        "serverInfo", GetServerInfo_f.instance, CmdSystem.CMD_FL_SYSTEM, "shows server info"
                    )
                    cmdSystem.AddCommand(
                        "LANScan", GetLANServers_f.instance, CmdSystem.CMD_FL_SYSTEM, "scans LAN for servers"
                    )
                    cmdSystem.AddCommand(
                        "listServers", ListServers_f.instance, CmdSystem.CMD_FL_SYSTEM, "lists scanned servers"
                    )
                    cmdSystem.AddCommand(
                        "rcon",
                        RemoteConsole_f.instance,
                        CmdSystem.CMD_FL_SYSTEM,
                        "sends remote console command to server"
                    )
                    cmdSystem.AddCommand(
                        "heartbeat",
                        Heartbeat_f.instance,
                        CmdSystem.CMD_FL_SYSTEM,
                        "send a heartbeat to the the master servers"
                    )
                    cmdSystem.AddCommand(
                        "kick", Kick_f.instance, CmdSystem.CMD_FL_SYSTEM, "kick a client by connection number"
                    )
                    cmdSystem.AddCommand(
                        "checkNewVersion",
                        CheckNewVersion_f.instance,
                        CmdSystem.CMD_FL_SYSTEM,
                        "check if a new version of the game is available"
                    )
                    cmdSystem.AddCommand(
                        "updateUI",
                        UpdateUI_f.instance,
                        CmdSystem.CMD_FL_SYSTEM,
                        "internal - cause a sync down of game-modified userinfo"
                    )
                }
            }

            fun Shutdown() {
                client.serverList.Shutdown()
                client.DisconnectFromServer()
                client.ClearServers()
                client.ClosePort()
                server.Kill()
                server.ClosePort()
            }

            fun IsActive(): Boolean {
                return server.IsActive() || client.IsActive()
            }

            fun RunFrame() {
                if (Console.console.Active()) {
                    win_input.Sys_GrabMouseCursor(false)
                    UsercmdGen.usercmdGen.InhibitUsercmd(inhibit_t.INHIBIT_ASYNC, true)
                } else {
                    win_input.Sys_GrabMouseCursor(true)
                    UsercmdGen.usercmdGen.InhibitUsercmd(inhibit_t.INHIBIT_ASYNC, false)
                }
                client.RunFrame()
                server.RunFrame()
            }

            fun WriteUserCmdDelta(msg: idBitMsg, cmd: usercmd_t, base: usercmd_t?) {
                if (base != null) {
                    msg.WriteDeltaLongCounter(base.gameTime, cmd.gameTime)
                    msg.WriteDeltaByte(base.buttons, cmd.buttons)
                    msg.WriteDeltaShort(base.mx, cmd.mx)
                    msg.WriteDeltaShort(base.my, cmd.my)
                    msg.WriteDeltaChar(base.forwardmove, cmd.forwardmove)
                    msg.WriteDeltaChar(base.rightmove, cmd.rightmove)
                    msg.WriteDeltaChar(base.upmove, cmd.upmove)
                    msg.WriteDeltaShort(base.angles[0], cmd.angles[0])
                    msg.WriteDeltaShort(base.angles[1], cmd.angles[1])
                    msg.WriteDeltaShort(base.angles[2], cmd.angles[2])
                    return
                }
                msg.WriteLong(cmd.gameTime)
                msg.WriteByte(cmd.buttons)
                msg.WriteShort(cmd.mx)
                msg.WriteShort(cmd.my)
                msg.WriteChar(cmd.forwardmove.toInt())
                msg.WriteChar(cmd.rightmove.toInt())
                msg.WriteChar(cmd.upmove.toInt())
                msg.WriteShort(cmd.angles[0])
                msg.WriteShort(cmd.angles[1])
                msg.WriteShort(cmd.angles[2])
            }

            @Throws(idException::class)
            fun ReadUserCmdDelta(msg: idBitMsg, cmd: usercmd_t, base: usercmd_t?) {
                if (base != null) {
                    cmd.gameTime = msg.ReadDeltaLongCounter(base.gameTime)
                    cmd.buttons = msg.ReadDeltaByte(base.buttons)
                    cmd.mx = msg.ReadDeltaShort(base.mx)
                    cmd.my = msg.ReadDeltaShort(base.my)
                    cmd.forwardmove = msg.ReadDeltaChar(base.forwardmove)
                    cmd.rightmove = msg.ReadDeltaChar(base.rightmove)
                    cmd.upmove = msg.ReadDeltaChar(base.upmove)
                    cmd.angles[0] = msg.ReadDeltaShort(base.angles[0])
                    cmd.angles[1] = msg.ReadDeltaShort(base.angles[1])
                    cmd.angles[2] = msg.ReadDeltaShort(base.angles[2])
                    return
                }
                cmd.gameTime = msg.ReadLong()
                cmd.buttons = msg.ReadByte()
                cmd.mx = msg.ReadShort()
                cmd.my = msg.ReadShort()
                cmd.forwardmove = msg.ReadChar()
                cmd.rightmove = msg.ReadChar()
                cmd.upmove = msg.ReadChar()
                cmd.angles[0] = msg.ReadShort()
                cmd.angles[1] = msg.ReadShort()
                cmd.angles[2] = msg.ReadShort()
            }

            fun DuplicateUsercmd(
                previousUserCmd: usercmd_t, currentUserCmd: usercmd_t, frame: Int, time: Int
            ): Boolean {
                var currentUserCmd = currentUserCmd
                if (currentUserCmd.gameTime <= previousUserCmd.gameTime) {
                    currentUserCmd = previousUserCmd
                    currentUserCmd.gameFrame = frame
                    currentUserCmd.gameTime = time
                    currentUserCmd.duplicateCount++
                    if (currentUserCmd.duplicateCount > MAX_USERCMD_DUPLICATION) {
                        currentUserCmd.buttons = currentUserCmd.buttons and UsercmdGen.BUTTON_ATTACK.inv().toByte()
                        if (abs(currentUserCmd.forwardmove.toInt()) > 2) {
                            currentUserCmd.forwardmove = (currentUserCmd.forwardmove.toInt() shr 1).toByte()
                        }
                        if (abs(currentUserCmd.rightmove.toInt()) > 2) {
                            currentUserCmd.rightmove = (currentUserCmd.rightmove.toInt() shr 1).toByte()
                        }
                        if (abs(currentUserCmd.upmove.toInt()) > 2) {
                            currentUserCmd.upmove = (currentUserCmd.upmove.toInt() shr 1).toByte()
                        }
                    }
                    return true
                }
                return false
            }

            fun UsercmdInputChanged(previousUserCmd: usercmd_t, currentUserCmd: usercmd_t): Boolean {
                return previousUserCmd.buttons != currentUserCmd.buttons || previousUserCmd.forwardmove != currentUserCmd.forwardmove || previousUserCmd.rightmove != currentUserCmd.rightmove || previousUserCmd.upmove != currentUserCmd.upmove || previousUserCmd.angles[0] != currentUserCmd.angles[0] || previousUserCmd.angles[1] != currentUserCmd.angles[1] || previousUserCmd.angles[2] != currentUserCmd.angles[2]
            }

            //
            // returns true if the corresponding master is set to something (and could be resolved)
            fun GetMasterAddress(index: Int, adr: netadr_t): Boolean {
                if (null == masters[index].cVar) {
                    return false
                }
                if (masters[index].cVar.GetString()!!.isEmpty()) {
                    return false
                }
                if (!masters[index].resolved || masters[index].cVar.IsModified()) {
                    masters[index].cVar.ClearModified()
                    if (!win_net.Sys_StringToNetAdr(
                            masters[index].cVar.GetString(), masters[index].address, true
                        )
                    ) {
                        Common.common.Printf(
                            "Failed to resolve master%d: %s\n", index, masters[index].cVar.GetString()!!
                        )
                        masters[index].address =
                            netadr_t() //memset( &masters[ index ].address, 0, sizeof( netadr_t ) );
                        masters[index].resolved = true
                        return false
                    }
                    if (masters[index].address.port == 0) {
                        masters[index].address.port = Licensee.IDNET_MASTER_PORT
                    }
                    masters[index].resolved = true
                }
                adr.oSet(masters[index].address)
                return true
            }

            // get the hardcoded idnet master, equivalent to GetMasterAddress( 0, .. )
            fun GetMasterAddress(): netadr_t {
                val ret = netadr_t()
                GetMasterAddress(0, ret)
                return masters[0].address
            }

            fun GetNETServers() {
                client.GetNETServers()
            }

            fun ExecuteSessionCommand(sessCmd: String) {
                if (sessCmd.isNotEmpty()) {
                    if (0 == idStr.Icmp(sessCmd, "game_startmenu")) {
                        Session.session.SetGUI(Game_local.game.StartMenu(), null)
                    }
                }
            }

            fun ExecuteSessionCommand(sessCmd: CharArray) {
                ExecuteSessionCommand(TempDump.ctos(sessCmd))
            }

            // same message used for offline check and network reply
            @Throws(idException::class)
            fun BuildInvalidKeyMsg(msg: idStr, valid: BooleanArray /*[2 ]*/) {
                if (!valid[0]) {
                    msg.plusAssign(Common.common.GetLanguageDict().GetString("#str_07194"))
                }
                if (FileSystem_h.fileSystem.HasD3XP() && !valid[1]) {
                    if (msg.Length() != 0) {
                        msg.plusAssign("\n")
                    }
                    msg.plusAssign(Common.common.GetLanguageDict().GetString("#str_07195"))
                }
                msg.plusAssign("\n")
                msg.plusAssign(Common.common.GetLanguageDict().GetString("#str_04304"))
            }

            val LANServer: idCVar = idCVar(
                "net_LANServer",
                "0",
                CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_BOOL or CVarSystem.CVAR_NOCHEAT,
                "config LAN games only - affects clients and servers"
            )
            val allowCheats: idCVar = idCVar(
                "net_allowCheats",
                "0",
                CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_BOOL or CVarSystem.CVAR_NETWORKSYNC,
                "Allow cheats in network game"
            )
            val client: idAsyncClient = idAsyncClient()
            val clientDownload: idCVar = idCVar(
                "net_clientDownload",
                "1",
                CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_INTEGER or CVarSystem.CVAR_ARCHIVE,
                "client pk4 downloads policy: 0 - never, 1 - ask, 2 - always  = new idCVar(will still prompt for binary code)"
            )
            val clientMaxPrediction: idCVar = idCVar(
                "net_clientMaxPrediction",
                "1000",
                CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_INTEGER or CVarSystem.CVAR_NOCHEAT,
                "maximum number of milliseconds a client can predict ahead of server."
            )
            val clientMaxRate: idCVar = idCVar(
                "net_clientMaxRate",
                "16000",
                CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_INTEGER or CVarSystem.CVAR_ARCHIVE or CVarSystem.CVAR_NOCHEAT,
                "maximum rate requested by client from server in bytes/sec"
            )
            val clientPrediction: idCVar = idCVar(
                "net_clientPrediction",
                "16",
                CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_INTEGER or CVarSystem.CVAR_NOCHEAT,
                "additional client side prediction in milliseconds"
            )
            val clientRemoteConsoleAddress: idCVar = idCVar(
                "net_clientRemoteConsoleAddress",
                "localhost",
                CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_NOCHEAT,
                "remote console address"
            )
            val clientRemoteConsolePassword: idCVar = idCVar(
                "net_clientRemoteConsolePassword",
                "",
                CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_NOCHEAT,
                "remote console password"
            )
            val clientServerTimeout: idCVar = idCVar(
                "net_clientServerTimeout",
                "40",
                CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_INTEGER or CVarSystem.CVAR_NOCHEAT,
                "server time out in seconds"
            )
            val clientUsercmdBackup: idCVar = idCVar(
                "net_clientUsercmdBackup",
                "5",
                CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_INTEGER or CVarSystem.CVAR_NOCHEAT,
                "number of usercmds to resend"
            )
            val idleServer: idCVar = idCVar(
                "si_idleServer",
                "0",
                CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_BOOL or CVarSystem.CVAR_INIT or CVarSystem.CVAR_SERVERINFO,
                "game clients are idle"
            )
            val master0: idCVar = idCVar(
                "net_master0",
                Licensee.IDNET_HOST + ":" + Licensee.IDNET_MASTER_PORT,
                CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_ROM,
                "idnet master server address"
            )
            val master1: idCVar = idCVar(
                "net_master1", "", CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_ARCHIVE, "1st master server address"
            )
            val master2: idCVar = idCVar(
                "net_master2", "", CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_ARCHIVE, "2nd master server address"
            )
            val master3: idCVar = idCVar(
                "net_master3", "", CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_ARCHIVE, "3rd master server address"
            )
            val master4: idCVar = idCVar(
                "net_master4", "", CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_ARCHIVE, "4th master server address"
            )
            val server: idAsyncServer = idAsyncServer()
            val serverAllowServerMod: idCVar = idCVar(
                "net_serverAllowServerMod",
                "0",
                CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_BOOL or CVarSystem.CVAR_NOCHEAT,
                "allow server-side mods"
            )
            val serverClientTimeout: idCVar = idCVar(
                "net_serverClientTimeout",
                "40",
                CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_INTEGER or CVarSystem.CVAR_NOCHEAT,
                "client time out in seconds"
            )
            val serverDrawClient: idCVar = idCVar(
                "net_serverDrawClient",
                "-1",
                CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_INTEGER,
                "number of client for which to draw view on server"
            )
            val serverMaxClientRate: idCVar = idCVar(
                "net_serverMaxClientRate",
                "16000",
                CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_INTEGER or CVarSystem.CVAR_ARCHIVE or CVarSystem.CVAR_NOCHEAT,
                "maximum rate to a client in bytes/sec"
            )
            val serverMaxUsercmdRelay: idCVar = idCVar(
                "net_serverMaxUsercmdRelay",
                "5",
                CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_INTEGER or CVarSystem.CVAR_NOCHEAT,
                "maximum number of usercmds from other clients the server relays to a client",
                1.0f,
                MAX_USERCMD_RELAY.toFloat(),
                ArgCompletion_Integer(1, MAX_USERCMD_RELAY)
            )
            val serverReloadEngine: idCVar = idCVar(
                "net_serverReloadEngine",
                "0",
                CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_INTEGER or CVarSystem.CVAR_NOCHEAT,
                "perform a full reload on next map restart  = new idCVar(including flushing referenced pak files) - decreased if > 0"
            )
            val serverRemoteConsolePassword: idCVar = idCVar(
                "net_serverRemoteConsolePassword",
                "",
                CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_NOCHEAT,
                "remote console password"
            )
            val serverSnapshotDelay: idCVar = idCVar(
                "net_serverSnapshotDelay",
                "50",
                CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_INTEGER or CVarSystem.CVAR_NOCHEAT,
                "delay between snapshots in milliseconds"
            )
            val serverZombieTimeout: idCVar = idCVar(
                "net_serverZombieTimeout",
                "5",
                CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_INTEGER or CVarSystem.CVAR_NOCHEAT,
                "disconnected client timeout in seconds"
            )
            val verbose: idCVar = idCVar(
                "net_verbose",
                "0",
                CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_INTEGER or CVarSystem.CVAR_NOCHEAT,
                "1 = verbose output, 2 = even more verbose output",
                0.0f,
                2.0f,
                ArgCompletion_Integer(0, 2)
            )

            // if set run a dedicated server

            var serverDedicated: idCVar = idCVar(
                "net_serverDedicated",
                "0",
                CVarSystem.CVAR_SERVERINFO or CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_INTEGER or CVarSystem.CVAR_NOCHEAT,
                "1 = text console dedicated server, 2 = graphical dedicated server",
                0.0f,
                2.0f,
                ArgCompletion_Integer(0, 2)
            )

        }

        init {
            if (ID_DEDICATED) { // dedicated executable can only have a value of 1 for net_serverDedicated
                serverDedicated = idCVar(
                    "net_serverDedicated",
                    "1",
                    CVarSystem.CVAR_SERVERINFO or CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_INTEGER or CVarSystem.CVAR_NOCHEAT or CVarSystem.CVAR_ROM,
                    ""
                )
            } else {
                serverDedicated = idCVar(
                    "net_serverDedicated",
                    "0",
                    CVarSystem.CVAR_SERVERINFO or CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_INTEGER or CVarSystem.CVAR_NOCHEAT,
                    "1 = text console dedicated server, 2 = graphical dedicated server",
                    0.0f,
                    2.0f,
                    ArgCompletion_Integer(0, 2)
                )
            }
        }

    }


    companion object {
        /*
                 DOOM III gold:	    33
                 1.1 beta patch:	34
                 1.1 patch:		    35
                 1.2 XP:			36-39
                 1.3 patch:		    40
                 1.3.1:			    41
        */
        const val ASYNC_PROTOCOL_MINOR = 41
        const val ASYNC_PROTOCOL_VERSION = (Licensee.ASYNC_PROTOCOL_MAJOR shl 16) + ASYNC_PROTOCOL_MINOR

        // special game init ids
        const val GAME_INIT_ID_INVALID = -1
        const val GAME_INIT_ID_MAP_LOAD = -2
        const val MAX_ASYNC_CLIENTS = 32

        // index 0 is hardcoded to be the idnet master
        // which leaves 4 to user customization
        const val MAX_MASTER_SERVERS = 5
        const val MAX_NICKLEN = 32

        // max number of servers that will be scanned for at a single IP address
        const val MAX_SERVER_PORTS = 8
        const val MAX_USERCMD_BACKUP = 256
        const val MAX_USERCMD_DUPLICATION = 25
        const val MAX_USERCMD_RELAY = 10

        fun MAJOR_VERSION(v: Int): Int {
            return v shr 16
        }
    }
}
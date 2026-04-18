package neo.framework.Async

import neo.Game.Game.allowReply_t
import neo.Game.Game_local
import neo.TempDump
import neo.TempDump.void_callback
import neo.framework.*
import neo.framework.Async.AsyncNetwork.*
import neo.framework.Async.MsgChannel.idMsgChannel
import neo.framework.CmdSystem.cmdExecution_t
import neo.framework.FileSystem_h.findFile_t
import neo.framework.Session.msgBoxType_t
import neo.framework.UsercmdGen.usercmd_t
import neo.idlib.BitMsg.idBitMsg
import neo.idlib.Dict_h.idDict
import neo.idlib.MAX_STRING_CHARS
import neo.idlib.Max
import neo.idlib.Min
import neo.idlib.Text.Str
import neo.idlib.Text.Str.idStr
import neo.idlib.containers.CInt
import neo.idlib.containers.idStrList
import neo.idlib.idException
import neo.idlib.math.idMath
import neo.sys.*
import java.math.BigInteger
import java.nio.ByteBuffer
import java.util.*

object AsyncServer {
    //
    // if we don't hear from authorize server, assume it is down
    const val AUTHORIZE_TIMEOUT = 5000
    const val EMPTY_RESEND_TIME = 500

    //
    const val HEARTBEAT_MSEC = 5 * 60 * 1000

    /*
     ===============================================================================

     Network Server for asynchronous networking.

     ===============================================================================
     */
    // MAX_CHALLENGES is made large to prevent a denial of service attack that could cycle
    // all of them out before legitimate users connected
    const val MAX_CHALLENGES = 1024

    //
    const val MIN_RECONNECT_TIME = 2000
    const val NOINPUT_IDLE_TIME = 30000
    const val PING_RESEND_TIME = 500

    //
    // must be kept in sync with authReplyMsg_t
    val authReplyMsg: Array<String> = arrayOf( //	"Waiting for authorization",
        "#str_07204",  //	"Client unknown to auth",
        "#str_07205",  //	"Access denied - CD Key in use",
        "#str_07206",  //	"Auth custom message", // placeholder - we propagate a message from the master
        "#str_07207",  //	"Authorize Server - Waiting for client"
        "#str_07208"
    )
    val authReplyStr: Array<String> = arrayOf(
        "AUTH_NONE",
        "AUTH_OK",
        "AUTH_WAIT",
        "AUTH_DENY"
    )

    // message from auth to be forwarded back to the client
    // some are locally hardcoded to save space, auth has the possibility to send a custom reply
    internal enum class authReplyMsg_t {
        AUTH_REPLY_WAITING,  // waiting on an initial reply from auth
        AUTH_REPLY_UNKNOWN,  // client unknown to auth
        AUTH_REPLY_DENIED,  // access denied
        AUTH_REPLY_PRINT,  // custom message
        AUTH_REPLY_SRVWAIT,  // auth server replied and tells us he's working on it
        AUTH_REPLY_MAXSTATES
    }

    // states from the auth server, while the client is in CDK_WAIT
    internal enum class authReply_t {
        AUTH_NONE,  // no reply yet
        AUTH_OK,  // this client is good
        AUTH_WAIT,  // wait - keep sending me srvAuth though
        AUTH_DENY,  // denied - don't send me anything about this client anymore
        AUTH_MAXSTATES
    }

    // states for the server's authorization process
    internal enum class authState_t {
        CDK_WAIT,  // we are waiting for a confirm/deny from auth

        // this is subject to timeout if we don't hear from auth
        // or a permanent wait if auth said so
        CDK_OK,
        CDK_ONLYLAN,
        CDK_PUREWAIT,
        CDK_PUREOK,
        CDK_MAXSTATES
    }

    internal enum class serverClientState_t {
        SCS_FREE,  // can be reused for a new connection
        SCS_ZOMBIE,  // client has been disconnected, but don't reuse connection for a couple seconds
        SCS_PUREWAIT,  // client needs to update it's pure checksums before we can go further
        SCS_CONNECTED,  // client is connected
        SCS_INGAME // client is in the game
    }

    internal class challenge_s {
        var OS = 0
        var address // client address
                : netadr_t = netadr_t()
        var authReply // cd key check replies
                : authReply_t = authReply_t.AUTH_NONE
        var authReplyMsg // default auth messages
                : authReplyMsg_t = authReplyMsg_t.AUTH_REPLY_WAITING
        val authReplyPrint // custom msg
                : idStr = idStr()
        var authState // local state regarding the client
                : authState_t = authState_t.CDK_WAIT
        var challenge // challenge code
                = 0
        var clientId // client identification
                = 0
        var connected // true if the client is connected
                = false
        var guid: CharArray = CharArray(12) // guid
        var pingTime // time the challenge response was sent to client
                = 0
        var time // time the challenge was created
                = 0
    } /*challenge_t*/

    internal class serverClient_s {
        var OS = 0
        var acknowledgeSnapshotSequence = 0
        var channel: idMsgChannel = idMsgChannel()
        var clientAheadTime = 0
        var clientId = 0
        var clientPing = 0
        var clientPrediction = 0
        var clientRate = 0
        var clientState: serverClientState_t = serverClientState_t.SCS_FREE
        var gameFrame = 0
        var gameInitSequence = 0
        var gameTime = 0
        var guid: CharArray = CharArray(12) // Even Balance - M. Quinn
        var lastConnectTime = 0
        var lastEmptyTime = 0
        var lastInputTime = 0
        var lastPacketTime = 0
        var lastPingTime = 0
        var lastSnapshotTime = 0
        var numDuplicatedUsercmds = 0
        var snapshotSequence = 0
        fun isClientConnected(): Boolean {
            return clientState.ordinal < serverClientState_t.SCS_CONNECTED.ordinal
        }
    } /* serverClient_t*/

    class idAsyncServer {
        private val serverPort // UDP port
                : idPort
        private val stats_outrate: IntArray = IntArray(stats_numsamples)
        private var active // true if server is active
                : Boolean

        //
        private var challenges: Array<challenge_s>// to prevent invalid IPs from connecting
        private val clients: Array<serverClient_s> =
            Array(AsyncNetwork.MAX_ASYNC_CLIENTS) { serverClient_s() } // clients
        private var gameFrame // local game frame
                : Int

        //
        private var gameInitId // game initialization identification
                : Int
        private var gameTime // local game time
                : Int
        private var gameTimeResidual // left over time from previous frame
                : Int

        //
        private var lastAuthTime // global for auth server timeout
                : Int
        private var localClientNum // local client on listen server
                : Int
        private var nextAsyncStatsTime: Int

        //
        private var nextHeartbeatTime: Int

        //
        private var noRconOutput // for default rcon response when command is silent
                : Boolean

        //
        private var rconAddress: netadr_t = netadr_t()
        private var realTime // absolute time
                : Int
        private var serverDataChecksum // checksum of the data used by the server
                : BigInteger
        private var serverId // server identification
                : Int

        //
        private var serverReloadingEngine // flip-flop to not loop over when net_serverReloadEngine is on
                : Boolean

        //
        private var serverTime // local server time
                : Int
        private var stats_average_sum: Int
        private var stats_current: Int
        private var stats_max: Int
        private var stats_max_index: Int
        private var userCmds: Array<Array<usercmd_t>> =
            Array(AsyncNetwork.MAX_USERCMD_BACKUP) { Array(AsyncNetwork.MAX_ASYNC_CLIENTS) { usercmd_t() } }

        fun InitPort(): Boolean {
            var lastPort: Int

            // if this is the first time we have spawned a server, open the UDP port
            if (0 == serverPort.GetPort()) {
                if (CVarSystem.cvarSystem.GetCVarInteger("net_port") != 0) {
                    if (!serverPort.InitForPort(CVarSystem.cvarSystem.GetCVarInteger("net_port"))) {
                        Common.common.Printf(
                            "Unable to open server on port %d (net_port)\n",
                            CVarSystem.cvarSystem.GetCVarInteger("net_port")
                        )
                        return false
                    }
                } else {
                    // scan for multiple ports, in case other servers are running on this IP already
                    lastPort = 0
                    while (lastPort < Licensee.NUM_SERVER_PORTS) {
                        if (serverPort.InitForPort(Licensee.PORT_SERVER + lastPort)) {
                            break
                        }
                        lastPort++
                    }
                    if (lastPort >= Licensee.NUM_SERVER_PORTS) {
                        Common.common.Printf("Unable to open server network port.\n")
                        return false
                    }
                }
            }
            return true
        }

        fun ClosePort() {
            serverPort.Close()
            for (i in 0 until MAX_CHALLENGES) {
                challenges[i].authReplyPrint.Clear()
            }
        }

        fun Spawn() {
            var i: Int
            val size = CInt()
            val msgBuf = ByteBuffer.allocate(MsgChannel.MAX_MESSAGE_SIZE)
            val from = netadr_t()

            // shutdown any current game
            Session.session.Stop()
            if (active) {
                return
            }
            if (!InitPort()) {
                return
            }

            // trash any currently pending packets
            while (serverPort.GetPacket(from, msgBuf, size, msgBuf.capacity())) {
            }

            // reset cheats cvars
            if (!idAsyncNetwork.allowCheats.GetBool()) {
                CVarSystem.cvarSystem.ResetFlaggedVariables(CVarSystem.CVAR_CHEAT)
            }

//	memset( challenges, 0, sizeof( challenges ) );
//	memset( userCmds, 0, sizeof( userCmds ) );
            // FIX: Arrays.fill(challenges, null) violates non-null type safety.
            // C++ memset zeros all bytes; Kotlin equivalent is to create fresh objects.
            for (c in challenges.indices) {
                challenges[c] = challenge_s()
            }
            for (u in userCmds.indices) {
                for (v in userCmds[u].indices) {
                    userCmds[u][v] = usercmd_t()
                }
            }
            i = 0
            while (i < AsyncNetwork.MAX_ASYNC_CLIENTS) {
                ClearClient(i)
                i++
            }
            Common.common.Printf("Server spawned on port %d.\n", serverPort.GetPort())

            // calculate a checksum on some of the essential data used
            serverDataChecksum = DeclManager.declManager.GetChecksum()

            // get a pseudo random server id, but don't use the id which is reserved for connectionless packets
            serverId = win_shared.Sys_Milliseconds() and MsgChannel.CONNECTIONLESS_MESSAGE_ID_MASK
            active = true
            nextHeartbeatTime = 0
            nextAsyncStatsTime = 0
            ExecuteMapChange()
        }

        fun Kill() {
            var i: Int
            var j: Int
            if (!active) {
                return
            }

            // drop all clients
            i = 0
            while (i < AsyncNetwork.MAX_ASYNC_CLIENTS) {
                DropClient(i, "#str_07135")
                i++
            }

            // send some empty messages to the zombie clients to make sure they disconnect
            j = 0
            while (j < 4) {
                i = 0
                while (i < AsyncNetwork.MAX_ASYNC_CLIENTS) {
                    if (clients[i].clientState == serverClientState_t.SCS_ZOMBIE) {
                        if (clients[i].channel.UnsentFragmentsLeft()) {
                            clients[i].channel.SendNextFragment(serverPort, serverTime)
                        } else {
                            SendEmptyToClient(i, true)
                        }
                    }
                    i++
                }
                win_main.Sys_Sleep(10)
                j++
            }

            // reset any pureness
            FileSystem_h.fileSystem.ClearPureChecksums()
            active = false

            // shutdown any current game
            Session.session.Stop()
        }

        @Throws(idException::class)
        fun ExecuteMapChange() {
            var i: Int
            val msg = idBitMsg()
            val msgBuf = ByteBuffer.allocate(MsgChannel.MAX_MESSAGE_SIZE)
            val mapName: idStr
            val ff: findFile_t
            var addonReload = false
            val bestGameType = CharArray(MAX_STRING_CHARS)
            assert(active)

            // reset any pureness
            FileSystem_h.fileSystem.ClearPureChecksums()

            // make sure the map/gametype combo is good
            Game_local.game.GetBestGameType(
                CVarSystem.cvarSystem.GetCVarString("si_map"),
                CVarSystem.cvarSystem.GetCVarString("si_gametype"),
                bestGameType
            )
            CVarSystem.cvarSystem.SetCVarString("si_gametype", TempDump.ctos(bestGameType))

            // initialize map settings
            CmdSystem.cmdSystem.BufferCommandText(cmdExecution_t.CMD_EXEC_NOW, "rescanSI")
            mapName =
                idStr(String.format("maps/%s", Session.sessLocal.mapSpawnData.serverInfo.GetString("si_map")))
            mapName.SetFileExtension(".map")
            ff = FileSystem_h.fileSystem.FindFile(mapName.toString(), !serverReloadingEngine)
            when (ff) {
                findFile_t.FIND_NO -> {
                    Common.common.Printf("Can't find map %s\n", mapName.toString())
                    CmdSystem.cmdSystem.BufferCommandText(cmdExecution_t.CMD_EXEC_APPEND, "disconnect\n")
                    return
                }

                findFile_t.FIND_ADDON -> {
                    // NOTE: we have no problem with addon dependencies here because if the map is in
                    // an addon pack that's already on search list, then all it's deps are assumed to be on search as well
                    Common.common.Printf("map %s is in an addon pak - reloading\n", mapName.toString())
                    addonReload = true
                }

                else -> {}
            }

            // if we are asked to do a full reload, the strategy is completely different
            if (!serverReloadingEngine && (addonReload || idAsyncNetwork.serverReloadEngine.GetInteger() != 0)) {
                if (idAsyncNetwork.serverReloadEngine.GetInteger() != 0) {
                    Common.common.Printf("net_serverReloadEngine enabled - doing a full reload\n")
                }
                // tell the clients to reconnect
                // FIXME: shouldn't they wait for the new pure list, then reload?
                // in a lot of cases this is going to trigger two reloadEngines for the clients
                // one to restart, the other one to set paks right ( with addon for instance )
                // can fix by reconnecting without reloading and waiting for the server to tell..
                i = 0
                while (i < AsyncNetwork.MAX_ASYNC_CLIENTS) {
                    if (clients[i].clientState.ordinal >= serverClientState_t.SCS_PUREWAIT.ordinal && i != localClientNum) {
                        msg.Init(msgBuf, msgBuf.capacity())
                        msg.WriteByte(SERVER_RELIABLE.SERVER_RELIABLE_MESSAGE_RELOAD.ordinal.toByte())
                        SendReliableMessage(i, msg)
                        clients[i].clientState =
                            serverClientState_t.SCS_ZOMBIE // so we don't bother sending a disconnect
                    }
                    i++
                }
                CmdSystem.cmdSystem.BufferCommandText(cmdExecution_t.CMD_EXEC_NOW, "reloadEngine")
                serverReloadingEngine = true // don't get caught in endless loop
                CmdSystem.cmdSystem.BufferCommandText(cmdExecution_t.CMD_EXEC_APPEND, "spawnServer\n")
                // decrease feature
                if (idAsyncNetwork.serverReloadEngine.GetInteger() > 0) {
                    idAsyncNetwork.serverReloadEngine.SetInteger(idAsyncNetwork.serverReloadEngine.GetInteger() - 1)
                }
                return
            }
            serverReloadingEngine = false
            serverTime = 0

            // initialize game id and time
            gameInitId =
                gameInitId xor win_shared.Sys_Milliseconds() // NOTE: make sure the gameInitId is always a positive number because negative numbers have special meaning
            gameFrame = 0
            gameTime = 0
            gameTimeResidual = 0
            //            memset(userCmds, 0, sizeof(userCmds));
            userCmds =
                Array(AsyncNetwork.MAX_USERCMD_BACKUP) { Array(AsyncNetwork.MAX_ASYNC_CLIENTS) { usercmd_t() } }
            if (idAsyncNetwork.serverDedicated.GetInteger() == 0) {
                InitLocalClient(0)
            } else {
                localClientNum = -1
            }
            // re-initialize all connected clients for the new map
            i = 0
            while (i < AsyncNetwork.MAX_ASYNC_CLIENTS) {
                if (clients[i].clientState.ordinal >= serverClientState_t.SCS_PUREWAIT.ordinal && i != localClientNum) {
                    InitClient(i, clients[i].clientId, clients[i].clientRate)
                    SendGameInitToClient(i)
                    if (Session.sessLocal.mapSpawnData.serverInfo.GetBool("si_pure")) {
                        clients[i].clientState = serverClientState_t.SCS_PUREWAIT
                    }
                }
                i++
            }

            // load map
            Session.sessLocal.ExecuteMapChange()
            if (localClientNum >= 0) {
                BeginLocalClient()
            } else {
                Game_local.game.SetLocalClient(-1)
            }
            if (Session.sessLocal.mapSpawnData.serverInfo.GetInt("si_pure") != 0) {
                // lock down the pak list
                FileSystem_h.fileSystem.UpdatePureServerChecksums()
                // tell the clients so they can work out their pure lists
                i = 0
                while (i < AsyncNetwork.MAX_ASYNC_CLIENTS) {
                    if (clients[i].clientState == serverClientState_t.SCS_PUREWAIT) {
                        if (!SendReliablePureToClient(i)) {
                            clients[i].clientState = serverClientState_t.SCS_CONNECTED
                        }
                    }
                    i++
                }
            }

            // serverTime gets reset, force a heartbeat so timings restart
            MasterHeartbeat(true)
        }

        //
        fun GetPort(): Int {
            return serverPort.GetPort()
        }

        fun GetBoundAdr(): netadr_t {
            return serverPort.GetAdr()
        }

        fun IsActive(): Boolean {
            return active
        }

        fun GetDelay(): Int {
            return gameTimeResidual
        }

        fun GetOutgoingRate(): Int {
            var i: Int
            var rate: Int
            rate = 0
            i = 0
            while (i < AsyncNetwork.MAX_ASYNC_CLIENTS) {
                val client = clients[i]
                if (client.clientState.ordinal >= serverClientState_t.SCS_CONNECTED.ordinal) {
                    rate += client.channel.GetOutgoingRate()
                }
                i++
            }
            return rate
        }

        fun GetIncomingRate(): Int {
            var i: Int
            var rate: Int
            rate = 0
            i = 0
            while (i < AsyncNetwork.MAX_ASYNC_CLIENTS) {
                val client = clients[i]
                if (client.isClientConnected()) {
                    rate += client.channel.GetIncomingRate()
                }
                i++
            }
            return rate
        }

        fun IsClientInGame(clientNum: Int): Boolean {
            return clients[clientNum].clientState.ordinal >= serverClientState_t.SCS_INGAME.ordinal
        }

        fun GetClientPing(clientNum: Int): Int {
            val client = clients[clientNum]
            return if (client.isClientConnected()) {
                99999
            } else {
                client.clientPing
            }
        }

        fun GetClientPrediction(clientNum: Int): Int {
            val client = clients[clientNum]
            return if (client.isClientConnected()) {
                99999
            } else {
                client.clientPrediction
            }
        }

        fun GetClientTimeSinceLastPacket(clientNum: Int): Int {
            val client = clients[clientNum]
            return if (client.isClientConnected()) {
                99999
            } else {
                serverTime - client.lastPacketTime
            }
        }

        fun GetClientTimeSinceLastInput(clientNum: Int): Int {
            val client = clients[clientNum]
            return if (client.isClientConnected()) {
                99999
            } else {
                serverTime - client.lastInputTime
            }
        }

        fun GetClientOutgoingRate(clientNum: Int): Int {
            val client = clients[clientNum]
            return if (client.isClientConnected()) {
                -1
            } else {
                client.channel.GetOutgoingRate()
            }
        }

        fun GetClientIncomingRate(clientNum: Int): Int {
            val client = clients[clientNum]
            return if (client.isClientConnected()) {
                -1
            } else {
                client.channel.GetIncomingRate()
            }
        }

        fun GetClientOutgoingCompression(clientNum: Int): Float {
            val client = clients[clientNum]
            return if (client.isClientConnected()) {
                0.0f
            } else {
                client.channel.GetOutgoingCompression()
            }
        }

        fun GetClientIncomingCompression(clientNum: Int): Float {
            val client = clients[clientNum]
            return if (client.isClientConnected()) {
                0.0f
            } else {
                client.channel.GetIncomingCompression()
            }
        }

        fun GetClientIncomingPacketLoss(clientNum: Int): Float {
            val client = clients[clientNum]
            return if (client.isClientConnected()) {
                0.0f
            } else {
                client.channel.GetIncomingPacketLoss()
            }
        }

        fun GetNumClients(): Int {
            var ret = 0
            for (i in 0 until AsyncNetwork.MAX_ASYNC_CLIENTS) {
                if (clients[i].clientState.ordinal >= serverClientState_t.SCS_CONNECTED.ordinal) {
                    ret++
                }
            }
            return ret
        }

        fun GetNumIdleClients(): Int {
            var ret = 0
            for (i in 0 until AsyncNetwork.MAX_ASYNC_CLIENTS) {
                if (clients[i].clientState.ordinal >= serverClientState_t.SCS_CONNECTED.ordinal) {
                    if (serverTime - clients[i].lastInputTime > NOINPUT_IDLE_TIME) {
                        ret++
                    }
                }
            }
            return ret
        }

        fun GetLocalClientNum(): Int {
            return localClientNum
        }

        //
        @Throws(idException::class)
        fun RunFrame() {
            var i: Int
            var msec: Int
            val size = CInt()
            var newPacket: Boolean
            val msg = idBitMsg()
            val msgBuf = ByteBuffer.allocate(MsgChannel.MAX_MESSAGE_SIZE)
            val from = netadr_t()
            var outgoingRate: Int
            var incomingRate: Int
            var outgoingCompression: Float
            var incomingCompression: Float
            msec = UpdateTime(100)
            if (0 == serverPort.GetPort()) {
                return
            }
            if (!active) {
                ProcessConnectionLessMessages()
                return
            }
            gameTimeResidual += msec

            // spin in place processing incoming packets until enough time lapsed to run a new game frame
            do {
                do {
                    // blocking read with game time residual timeout
                    newPacket = serverPort.GetPacketBlocking(
                        from,
                        msgBuf,
                        size,
                        msgBuf.capacity(),
                        UsercmdGen.USERCMD_MSEC - gameTimeResidual - 1
                    )
                    if (newPacket) {
                        msg.Init(msgBuf, msgBuf.capacity())
                        msg.SetSize(size._val)
                        msg.BeginReading()
                        if (ProcessMessage(from, msg)) {
                            return  // return because rcon was used
                        }
                    }
                    msec = UpdateTime(100)
                    gameTimeResidual += msec
                } while (newPacket)
            } while (gameTimeResidual < UsercmdGen.USERCMD_MSEC)

            // send heart beat to master servers
            MasterHeartbeat()

            // check for clients that timed out
            CheckClientTimeouts()
            if (idAsyncNetwork.idleServer.GetBool() == (0 == GetNumClients() || GetNumIdleClients() != GetNumClients())) {
                idAsyncNetwork.idleServer.SetBool(!idAsyncNetwork.idleServer.GetBool())
                // the need to propagate right away, only this
                Session.sessLocal.mapSpawnData.serverInfo.Set(
                    "si_idleServer",
                    idAsyncNetwork.idleServer.GetString()!!
                )
                Game_local.game.SetServerInfo(Session.sessLocal.mapSpawnData.serverInfo)
            }

            // make sure the time doesn't wrap
            if (serverTime > 1879048192) {
                ExecuteMapChange()
                return
            }

            // check for synchronized cvar changes
            if (CVarSystem.cvarSystem.GetModifiedFlags() and CVarSystem.CVAR_NETWORKSYNC != 0) {
                val newCvars: idDict = idDict()
                newCvars.set(CVarSystem.cvarSystem.MoveCVarsToDict(CVarSystem.CVAR_NETWORKSYNC))
                SendSyncedCvarsBroadcast(newCvars)
                CVarSystem.cvarSystem.ClearModifiedFlags(CVarSystem.CVAR_NETWORKSYNC)
            }

            // check for user info changes of the local client
            if (CVarSystem.cvarSystem.GetModifiedFlags() and CVarSystem.CVAR_USERINFO != 0) {
                if (localClientNum >= 0) {
                    val newInfo: idDict = idDict()
                    Game_local.game.ThrottleUserInfo()
                    newInfo.set(CVarSystem.cvarSystem.MoveCVarsToDict(CVarSystem.CVAR_USERINFO))
                    SendUserInfoBroadcast(localClientNum, newInfo)
                }
                CVarSystem.cvarSystem.ClearModifiedFlags(CVarSystem.CVAR_USERINFO)
            }

            // advance the server game
            while (gameTimeResidual >= UsercmdGen.USERCMD_MSEC) {

                // sample input for the local client
                LocalClientInput()

                // duplicate usercmds for clients if no new ones are available
                DuplicateUsercmds(gameFrame, gameTime)

                // advance game
                val ret = Game_local.game.RunFrame(userCmds[gameFrame and AsyncNetwork.MAX_USERCMD_BACKUP - 1])
                idAsyncNetwork.ExecuteSessionCommand(ret.sessionCommand)

                // update time
                gameFrame++
                gameTime += UsercmdGen.USERCMD_MSEC
                gameTimeResidual -= UsercmdGen.USERCMD_MSEC
            }

            // duplicate usercmds so there is always at least one available to send with snapshots
            DuplicateUsercmds(gameFrame, gameTime)

            // send snapshots to connected clients
            i = 0
            while (i < AsyncNetwork.MAX_ASYNC_CLIENTS) {
                val client = clients[i]
                if (client.clientState == serverClientState_t.SCS_FREE || i == localClientNum) {
                    i++
                    continue
                }

                // modify maximum rate if necesary
                if (idAsyncNetwork.serverMaxClientRate.IsModified()) {
                    client.channel.SetMaxOutgoingRate(
                        Min(
                            client.clientRate,
                            idAsyncNetwork.serverMaxClientRate.GetInteger()
                        )
                    )
                }

                // if the channel is not yet ready to send new data
                if (!client.channel.ReadyToSend(serverTime)) {
                    i++
                    continue
                }

                // send additional message fragments if the last message was too large to send at once
                if (client.channel.UnsentFragmentsLeft()) {
                    client.channel.SendNextFragment(serverPort, serverTime)
                    i++
                    continue
                }
                if (client.clientState == serverClientState_t.SCS_INGAME) {
                    if (!SendSnapshotToClient(i)) {
                        SendPingToClient(i)
                    }
                } else {
                    SendEmptyToClient(i)
                }
                i++
            }
            if (Common.com_showAsyncStats.GetBool()) {
                UpdateAsyncStatsAvg()

                // dedicated will verbose to console
                if (idAsyncNetwork.serverDedicated.GetBool() && serverTime >= nextAsyncStatsTime) {
                    Common.common.Printf(
                        "delay = %d msec, total outgoing rate = %d KB/s, total incoming rate = %d KB/s\n", GetDelay(),
                        GetOutgoingRate() shr 10, GetIncomingRate() shr 10
                    )
                    i = 0
                    while (i < AsyncNetwork.MAX_ASYNC_CLIENTS) {
                        outgoingRate = GetClientOutgoingRate(i)
                        incomingRate = GetClientIncomingRate(i)
                        outgoingCompression = GetClientOutgoingCompression(i)
                        incomingCompression = GetClientIncomingCompression(i)
                        if (outgoingRate != -1 && incomingRate != -1) {
                            Common.common.Printf(
                                "client %d: out rate = %d B/s (% -2.1f%%), in rate = %d B/s (% -2.1f%%)\n",
                                i, outgoingRate, outgoingCompression, incomingRate, incomingCompression
                            )
                        }
                        i++
                    }
                    val msg1 = idStr()
                    GetAsyncStatsAvgMsg(msg1)
                    Common.common.Printf("%s\n", msg1.toString())
                    nextAsyncStatsTime = serverTime + 1000
                }
            }
            idAsyncNetwork.serverMaxClientRate.ClearModified()
        }

        fun ProcessConnectionLessMessages() {
            var id: Int
            val size = CInt()
            val msg = idBitMsg()
            val msgBuf = ByteBuffer.allocate(MsgChannel.MAX_MESSAGE_SIZE)
            val from = netadr_t()
            if (0 == serverPort.GetPort()) {
                return
            }
            while (serverPort.GetPacket(from, msgBuf, size, msgBuf.capacity())) {
                msg.Init(msgBuf, msgBuf.capacity())
                msg.SetSize(size._val)
                msg.BeginReading()
                id = msg.ReadShort().toInt()
                if (id == MsgChannel.CONNECTIONLESS_MESSAGE_ID) {
                    ConnectionlessMessage(from, msg)
                }
            }
        }

        fun RemoteConsoleOutput(string: String) {
            noRconOutput = false
            PrintOOB(rconAddress, SERVER_PRINT.SERVER_PRINT_RCON.ordinal, string)
        }

        fun SendReliableGameMessage(clientNum: Int, msg: idBitMsg) {
            var i: Int
            val outMsg = idBitMsg()
            val msgBuf = ByteBuffer.allocate(MsgChannel.MAX_MESSAGE_SIZE)
            outMsg.Init(msgBuf, msgBuf.capacity())
            outMsg.WriteByte(SERVER_RELIABLE.SERVER_RELIABLE_MESSAGE_GAME.ordinal.toByte())
            outMsg.WriteData(msg.GetData()!!, msg.GetSize())
            if (clientNum >= 0 && clientNum < AsyncNetwork.MAX_ASYNC_CLIENTS) {
                if (clients[clientNum].clientState == serverClientState_t.SCS_INGAME) {
                    SendReliableMessage(clientNum, outMsg)
                }
                return
            }
            i = 0
            while (i < AsyncNetwork.MAX_ASYNC_CLIENTS) {
                if (clients[i].clientState != serverClientState_t.SCS_INGAME) {
                    i++
                    continue
                }
                SendReliableMessage(i, outMsg)
                i++
            }
        }

        fun SendReliableGameMessageExcluding(clientNum: Int, msg: idBitMsg) {
            var i: Int
            val outMsg = idBitMsg()
            val msgBuf = ByteBuffer.allocate(MsgChannel.MAX_MESSAGE_SIZE)
            assert(clientNum >= 0 && clientNum < AsyncNetwork.MAX_ASYNC_CLIENTS)
            outMsg.Init(msgBuf, msgBuf.capacity())
            outMsg.WriteByte(SERVER_RELIABLE.SERVER_RELIABLE_MESSAGE_GAME.ordinal.toByte())
            outMsg.WriteData(msg.GetData()!!, msg.GetSize())
            i = 0
            while (i < AsyncNetwork.MAX_ASYNC_CLIENTS) {
                if (i == clientNum) {
                    i++
                    continue
                }
                if (clients[i].clientState != serverClientState_t.SCS_INGAME) {
                    i++
                    continue
                }
                SendReliableMessage(i, outMsg)
                i++
            }
        }

        fun LocalClientSendReliableMessage(msg: idBitMsg) {
            if (localClientNum < 0) {
                Common.common.Printf("LocalClientSendReliableMessage: no local client\n")
                return
            }
            Game_local.game.ServerProcessReliableMessage(localClientNum, msg)
        }

        //

        fun MasterHeartbeat(force: Boolean = false /*= false*/) {
            if (idAsyncNetwork.LANServer.GetBool()) {
                if (force) {
                    Common.common.Printf("net_LANServer is enabled. Not sending heartbeats\n")
                }
                return
            }
            if (force) {
                nextHeartbeatTime = 0
            }
            // not yet
            if (serverTime < nextHeartbeatTime) {
                return
            }
            nextHeartbeatTime = serverTime + HEARTBEAT_MSEC
            for (i in 0 until AsyncNetwork.MAX_MASTER_SERVERS) {
                val adr = netadr_t()
                if (idAsyncNetwork.GetMasterAddress(i, adr)) {
                    Common.common.Printf("Sending heartbeat to %s\n", win_net.Sys_NetAdrToString(adr))
                    val outMsg = idBitMsg()
                    val msgBuf = ByteBuffer.allocate(MsgChannel.MAX_MESSAGE_SIZE)
                    outMsg.Init(msgBuf, msgBuf.capacity())
                    outMsg.WriteShort(MsgChannel.CONNECTIONLESS_MESSAGE_ID.toShort())
                    outMsg.WriteString("heartbeat")
                    serverPort.SendPacket(adr, outMsg.GetData()!!, outMsg.GetSize())
                }
            }
        }

        fun DropClient(clientNum: Int, reason: String): String {
            var i: Int
            val msg = idBitMsg()
            val msgBuf = ByteBuffer.allocate(MsgChannel.MAX_MESSAGE_SIZE)
            val returnString: String
            val client = clients[clientNum]
            if (client.clientState.ordinal <= serverClientState_t.SCS_ZOMBIE.ordinal) {
                return ""
            }
            if (client.clientState.ordinal >= serverClientState_t.SCS_PUREWAIT.ordinal && clientNum != localClientNum) {
                msg.Init(msgBuf, msgBuf.capacity())
                msg.WriteByte(SERVER_RELIABLE.SERVER_RELIABLE_MESSAGE_DISCONNECT.ordinal.toByte())
                msg.WriteLong(clientNum)
                msg.WriteString(reason)
                i = 0
                while (i < AsyncNetwork.MAX_ASYNC_CLIENTS) {

                    // clientNum so SCS_PUREWAIT client gets it's own disconnect msg
                    if (i == clientNum || clients[i].clientState.ordinal >= serverClientState_t.SCS_CONNECTED.ordinal) {
                        SendReliableMessage(i, msg)
                    }
                    i++
                }
            }
            returnString = Common.common.GetLanguageDict().GetString(reason)
            Common.common.Printf("client %d %s\n", clientNum, reason)
            CmdSystem.cmdSystem.BufferCommandText(
                cmdExecution_t.CMD_EXEC_NOW,
                Str.va(
                    "addChatLine \"%s^0 %s\"",
                    Session.sessLocal.mapSpawnData.userInfo[clientNum].GetString("ui_name"),
                    reason
                )
            )

            // remove the player from the game
            Game_local.game.ServerClientDisconnect(clientNum)
            client.clientState = serverClientState_t.SCS_ZOMBIE
            return returnString
        }

        //
        fun PacifierUpdate() {
            var i: Int
            if (!IsActive()) {
                return
            }
            realTime = win_shared.Sys_Milliseconds()
            ProcessConnectionLessMessages()
            i = 0
            while (i < AsyncNetwork.MAX_ASYNC_CLIENTS) {
                if (clients[i].clientState.ordinal >= serverClientState_t.SCS_PUREWAIT.ordinal) {
                    if (clients[i].channel.UnsentFragmentsLeft()) {
                        clients[i].channel.SendNextFragment(serverPort, serverTime)
                    } else {
                        SendEmptyToClient(i)
                    }
                }
                i++
            }
        }

        //
        /*
         ==================
         idAsyncServer::UpdateUI
         if the game modifies userInfo, it will call this through command system
         we then need to get the info from the game, and broadcast to clients
         ( using DeltaDict and our current mapSpawnData as a base )
         ==================
         */
        fun UpdateUI(clientNum: Int) {
            val info = Game_local.game.GetUserInfo(clientNum)
            if (null == info) {
                Common.common.Warning("idAsyncServer::UpdateUI: no info from game\n")
                return
            }
            SendUserInfoBroadcast(clientNum, info, true)
        }

        //
        fun UpdateAsyncStatsAvg() {
            stats_average_sum -= stats_outrate[stats_current]
            stats_outrate[stats_current] = idAsyncNetwork.server.GetOutgoingRate()
            if (stats_outrate[stats_current] > stats_max) {
                stats_max = stats_outrate[stats_current]
                stats_max_index = stats_current
            } else if (stats_current == stats_max_index) {
                // find the new max
                var i: Int
                stats_max = 0
                i = 0
                while (i < stats_numsamples) {
                    if (stats_outrate[i] > stats_max) {
                        stats_max = stats_outrate[i]
                        stats_max_index = i
                    }
                    i++
                }
            }
            stats_average_sum += stats_outrate[stats_current]
            stats_current++
            stats_current %= stats_numsamples
        }

        fun GetAsyncStatsAvgMsg(msg: idStr) {
            msg.set(
                String.format(
                    "avrg out: %d B/s - max %d B/s ( over %d ms )",
                    stats_average_sum / stats_numsamples,
                    stats_max,
                    idAsyncNetwork.serverSnapshotDelay.GetInteger() * stats_numsamples
                )
            )
        }

        //
        /*
         ===============
         idAsyncServer::PrintLocalServerInfo
         see (client) "getInfo" -> (server) "infoResponse" -> (client)ProcessGetInfoMessage
         ===============
         */
        fun PrintLocalServerInfo() {
            var i: Int
            Common.common.Printf(
                "server '%s' IP = %s\nprotocol %d.%d\n",
                Session.sessLocal.mapSpawnData.serverInfo.GetString("si_name"),
                win_net.Sys_NetAdrToString(serverPort.GetAdr()),
                Licensee.ASYNC_PROTOCOL_MAJOR,
                AsyncNetwork.ASYNC_PROTOCOL_MINOR
            )
            Session.sessLocal.mapSpawnData.serverInfo.Print()
            i = 0
            while (i < AsyncNetwork.MAX_ASYNC_CLIENTS) {
                val client = clients[i]
                if (client.clientState.ordinal < serverClientState_t.SCS_CONNECTED.ordinal) {
                    i++
                    continue
                }
                Common.common.Printf(
                    "client %2d: %s, ping = %d, rate = %d\n", i,
                    Session.sessLocal.mapSpawnData.userInfo[i].GetString("ui_name", "Player")!!,
                    client.clientPing, client.channel.GetMaxOutgoingRate()
                )
                i++
            }
        }

        private fun PrintOOB(to: netadr_t, opcode: Int, string: String) {
            val outMsg = idBitMsg()
            val msgBuf = ByteBuffer.allocate(MsgChannel.MAX_MESSAGE_SIZE)
            outMsg.Init(msgBuf, msgBuf.capacity())
            outMsg.WriteShort(MsgChannel.CONNECTIONLESS_MESSAGE_ID.toShort())
            outMsg.WriteString("print")
            outMsg.WriteLong(opcode)
            outMsg.WriteString(string)
            serverPort.SendPacket(to, outMsg.GetData()!!, outMsg.GetSize())
        }

        private fun DuplicateUsercmds(frame: Int, time: Int) {
            var i: Int
            val previousIndex: Int
            val currentIndex: Int
            previousIndex = frame - 1 and AsyncNetwork.MAX_USERCMD_BACKUP - 1
            currentIndex = frame and AsyncNetwork.MAX_USERCMD_BACKUP - 1

            // duplicate previous user commands if no new commands are available for a client
            i = 0
            while (i < AsyncNetwork.MAX_ASYNC_CLIENTS) {
                if (clients[i].clientState == serverClientState_t.SCS_FREE) {
                    i++
                    continue
                }
                if (idAsyncNetwork.DuplicateUsercmd(
                        userCmds[previousIndex][i],
                        userCmds[currentIndex][i],
                        frame,
                        time
                    )
                ) {
                    clients[i].numDuplicatedUsercmds++
                }
                i++
            }
        }

        private fun ClearClient(clientNum: Int) {
            val client = clients[clientNum]
            client.clientId = 0
            client.clientState = serverClientState_t.SCS_FREE
            client.clientPrediction = 0
            client.clientAheadTime = 0
            client.clientRate = 0
            client.clientPing = 0
            client.gameInitSequence = 0
            client.gameFrame = 0
            client.gameTime = 0
            client.channel.Shutdown()
            client.lastConnectTime = 0
            client.lastEmptyTime = 0
            client.lastPingTime = 0
            client.lastSnapshotTime = 0
            client.lastPacketTime = 0
            client.lastInputTime = 0
            client.snapshotSequence = 0
            client.acknowledgeSnapshotSequence = 0
            client.numDuplicatedUsercmds = 0
        }

        private fun InitClient(clientNum: Int, clientId: Int, clientRate: Int) {
            var i: Int
            // clear the user info
            Session.sessLocal.mapSpawnData.userInfo[clientNum].Clear() // always start with a clean base

            // clear the server client
            val client = clients[clientNum]
            client.clientId = clientId
            client.clientState = serverClientState_t.SCS_CONNECTED
            client.clientPrediction = 0
            client.clientAheadTime = 0
            client.gameInitSequence = -1
            client.gameFrame = 0
            client.gameTime = 0
            client.channel.ResetRate()
            client.clientRate = if (clientRate != 0) clientRate else idAsyncNetwork.serverMaxClientRate.GetInteger()
            client.channel.SetMaxOutgoingRate(
                Min(
                    idAsyncNetwork.serverMaxClientRate.GetInteger(),
                    client.clientRate
                )
            )
            client.clientPing = 0
            client.lastConnectTime = serverTime
            client.lastEmptyTime = serverTime
            client.lastPingTime = serverTime
            client.lastSnapshotTime = serverTime
            client.lastPacketTime = serverTime
            client.lastInputTime = serverTime
            client.acknowledgeSnapshotSequence = 0
            client.numDuplicatedUsercmds = 0

            // clear the user commands
            i = 0
            while (i < AsyncNetwork.MAX_USERCMD_BACKUP) {
                userCmds[i][clientNum] = usercmd_t()
                i++
            }

            // let the game know a player connected
            Game_local.game.ServerClientConnect(clientNum, TempDump.ctos(client.guid))
        }

        private fun InitLocalClient(clientNum: Int) {
            val badAddress = netadr_t()
            localClientNum = clientNum
            InitClient(clientNum, 0, 0)
            //	memset( &badAddress, 0, sizeof( badAddress ) );
            badAddress.type = netadrtype_t.NA_BAD
            clients[clientNum].channel.Init(badAddress, serverId)
            clients[clientNum].clientState = serverClientState_t.SCS_INGAME
            Session.sessLocal.mapSpawnData.userInfo[clientNum]
                .set(CVarSystem.cvarSystem.MoveCVarsToDict(CVarSystem.CVAR_USERINFO))
        }

        private fun BeginLocalClient() {
            Game_local.game.SetLocalClient(localClientNum)
            Game_local.game.SetUserInfo(
                localClientNum,
                Session.sessLocal.mapSpawnData.userInfo[localClientNum],
                false,
                false
            )
            Game_local.game.ServerClientBegin(localClientNum)
        }

        private fun LocalClientInput() {
            val index: Int
            if (localClientNum < 0) {
                return
            }
            index = gameFrame and AsyncNetwork.MAX_USERCMD_BACKUP - 1
            userCmds[index][localClientNum].set(UsercmdGen.usercmdGen.GetDirectUsercmd())
            userCmds[index][localClientNum].gameFrame = gameFrame
            userCmds[index][localClientNum].gameTime = gameTime
            if (idAsyncNetwork.UsercmdInputChanged(
                    userCmds[gameFrame - 1 and AsyncNetwork.MAX_USERCMD_BACKUP - 1][localClientNum],
                    userCmds[index][localClientNum]
                )
            ) {
                clients[localClientNum].lastInputTime = serverTime
            }
            clients[localClientNum].gameFrame = gameFrame
            clients[localClientNum].gameTime = gameTime
            clients[localClientNum].lastPacketTime = serverTime
        }

        private fun CheckClientTimeouts() {
            var i: Int
            val zombieTimeout: Int
            val clientTimeout: Int
            zombieTimeout = serverTime - idAsyncNetwork.serverZombieTimeout.GetInteger() * 1000
            clientTimeout = serverTime - idAsyncNetwork.serverClientTimeout.GetInteger() * 1000
            i = 0
            while (i < AsyncNetwork.MAX_ASYNC_CLIENTS) {
                val client = clients[i]
                if (i == localClientNum) {
                    i++
                    continue
                }
                if (client.lastPacketTime > serverTime) {
                    client.lastPacketTime = serverTime
                    i++
                    continue
                }
                if (client.clientState == serverClientState_t.SCS_ZOMBIE && client.lastPacketTime < zombieTimeout) {
                    client.channel.Shutdown()
                    client.clientState = serverClientState_t.SCS_FREE
                    i++
                    continue
                }
                if (client.clientState.ordinal >= serverClientState_t.SCS_PUREWAIT.ordinal && client.lastPacketTime < clientTimeout) {
                    DropClient(i, "#str_07137")
                    i++
                    continue
                }
                i++
            }
        }

        private fun SendPrintBroadcast(string: String) {
            var i: Int
            val msg = idBitMsg()
            val msgBuf = ByteBuffer.allocate(MsgChannel.MAX_MESSAGE_SIZE)
            msg.Init(msgBuf, msgBuf.capacity())
            msg.WriteByte(SERVER_RELIABLE.SERVER_RELIABLE_MESSAGE_PRINT.ordinal.toByte())
            msg.WriteString(string)
            i = 0
            while (i < AsyncNetwork.MAX_ASYNC_CLIENTS) {
                if (clients[i].clientState.ordinal >= serverClientState_t.SCS_CONNECTED.ordinal) {
                    SendReliableMessage(i, msg)
                }
                i++
            }
        }

        private fun SendPrintToClient(clientNum: Int, string: String) {
            val msg = idBitMsg()
            val msgBuf = ByteBuffer.allocate(MsgChannel.MAX_MESSAGE_SIZE)
            val client = clients[clientNum]
            if (client.clientState.ordinal < serverClientState_t.SCS_CONNECTED.ordinal) {
                return
            }
            msg.Init(msgBuf, msgBuf.capacity())
            msg.WriteByte(SERVER_RELIABLE.SERVER_RELIABLE_MESSAGE_PRINT.ordinal.toByte())
            msg.WriteString(string)
            SendReliableMessage(clientNum, msg)
        }

        private fun SendUserInfoBroadcast(userInfoNum: Int, info: idDict, sendToAll: Boolean = false /*= false */) {
            val msg = idBitMsg()
            val msgBuf = ByteBuffer.allocate(MsgChannel.MAX_MESSAGE_SIZE)
            var gameInfo: idDict?
            val gameModifiedInfo: Boolean
            gameInfo = Game_local.game.SetUserInfo(userInfoNum, info, false, true)
            if (gameInfo != null) {
                gameModifiedInfo = true
            } else {
                gameModifiedInfo = false
                gameInfo = info
            }
            if (userInfoNum == localClientNum) {
                Common.common.DPrintf("local user info modified by server\n")
                CVarSystem.cvarSystem.SetCVarsFromDict(gameInfo)
                CVarSystem.cvarSystem.ClearModifiedFlags(CVarSystem.CVAR_USERINFO) // don't emit back
            }
            msg.Init(msgBuf, msgBuf.capacity())
            msg.WriteByte(SERVER_RELIABLE.SERVER_RELIABLE_MESSAGE_CLIENTINFO.ordinal.toByte())
            msg.WriteByte(userInfoNum.toByte())
            if (gameModifiedInfo || sendToAll) {
                msg.WriteBits(0, 1)
            } else {
                msg.WriteBits(1, 1)
            }
            if (ID_CLIENTINFO_TAGS) {
                msg.WriteLong(Session.sessLocal.mapSpawnData.userInfo[userInfoNum].Checksum().toInt())
                Common.common.DPrintf(
                    "broadcast for client %d: 0x%x\n",
                    userInfoNum,
                    Session.sessLocal.mapSpawnData.userInfo[userInfoNum].Checksum()
                )
                Session.sessLocal.mapSpawnData.userInfo[userInfoNum].Print()
            }
            if (gameModifiedInfo || sendToAll) {
                msg.WriteDeltaDict(gameInfo, null)
            } else {
                msg.WriteDeltaDict(gameInfo, Session.sessLocal.mapSpawnData.userInfo[userInfoNum])
            }
            for (i in 0 until AsyncNetwork.MAX_ASYNC_CLIENTS) {
                if (clients[i].clientState.ordinal >= serverClientState_t.SCS_CONNECTED.ordinal && (sendToAll || i != userInfoNum || gameModifiedInfo)) {
                    SendReliableMessage(i, msg)
                }
            }
            Session.sessLocal.mapSpawnData.userInfo[userInfoNum].set(gameInfo)
        }

        private fun SendUserInfoToClient(clientNum: Int, userInfoNum: Int, info: idDict) {
            val msg = idBitMsg()
            val msgBuf = ByteBuffer.allocate(MsgChannel.MAX_MESSAGE_SIZE)
            if (clients[clientNum].clientState.ordinal < serverClientState_t.SCS_CONNECTED.compareTo(
                    serverClientState_t.SCS_FREE
                )
            ) {
                return
            }
            msg.Init(msgBuf, msgBuf.capacity())
            msg.WriteByte(SERVER_RELIABLE.SERVER_RELIABLE_MESSAGE_CLIENTINFO.ordinal.toByte())
            msg.WriteByte(userInfoNum.toByte())
            msg.WriteBits(0, 1)
            if (ID_CLIENTINFO_TAGS) {
                msg.WriteLong(0)
                Common.common.DPrintf("user info %d to client %d: null base\n", userInfoNum, clientNum)
            }
            msg.WriteDeltaDict(info, null)
            SendReliableMessage(clientNum, msg)
        }

        private fun SendSyncedCvarsBroadcast(cvars: idDict) {
            val msg = idBitMsg()
            val msgBuf = ByteBuffer.allocate(MsgChannel.MAX_MESSAGE_SIZE)
            var i: Int
            msg.Init(msgBuf, msgBuf.capacity())
            msg.WriteByte(SERVER_RELIABLE.SERVER_RELIABLE_MESSAGE_SYNCEDCVARS.ordinal.toByte())
            msg.WriteDeltaDict(cvars, Session.sessLocal.mapSpawnData.syncedCVars)
            i = 0
            while (i < AsyncNetwork.MAX_ASYNC_CLIENTS) {
                if (clients[i].clientState.ordinal >= serverClientState_t.SCS_CONNECTED.ordinal) {
                    SendReliableMessage(i, msg)
                }
                i++
            }
            Session.sessLocal.mapSpawnData.syncedCVars.set(cvars)
        }

        private fun SendSyncedCvarsToClient(clientNum: Int, cvars: idDict) {
            val msg = idBitMsg()
            val msgBuf = ByteBuffer.allocate(MsgChannel.MAX_MESSAGE_SIZE)
            if (clients[clientNum].clientState.ordinal < serverClientState_t.SCS_CONNECTED.ordinal) {
                return
            }
            msg.Init(msgBuf, msgBuf.capacity())
            msg.WriteByte(SERVER_RELIABLE.SERVER_RELIABLE_MESSAGE_SYNCEDCVARS.ordinal.toByte())
            msg.WriteDeltaDict(cvars, null)
            SendReliableMessage(clientNum, msg)
        }

        private fun SendApplySnapshotToClient(clientNum: Int, sequence: Int) {
            val msg = idBitMsg()
            val msgBuf = ByteBuffer.allocate(MsgChannel.MAX_MESSAGE_SIZE)
            msg.Init(msgBuf, msgBuf.capacity())
            msg.WriteByte(SERVER_RELIABLE.SERVER_RELIABLE_MESSAGE_APPLYSNAPSHOT.ordinal.toByte())
            msg.WriteLong(sequence)
            SendReliableMessage(clientNum, msg)
        }

        private fun SendEmptyToClient(clientNum: Int, force: Boolean = false /*= false*/): Boolean {
            val msg = idBitMsg()
            val msgBuf = ByteBuffer.allocate(MsgChannel.MAX_MESSAGE_SIZE)
            val client = clients[clientNum]
            if (client.lastEmptyTime > realTime) {
                client.lastEmptyTime = realTime
            }
            if (!force && realTime - client.lastEmptyTime < EMPTY_RESEND_TIME) {
                return false
            }
            if (idAsyncNetwork.verbose.GetInteger() != 0) {
                Common.common.Printf(
                    "sending empty to client %d: gameInitId = %d, gameFrame = %d, gameTime = %d\n",
                    clientNum,
                    gameInitId,
                    gameFrame,
                    gameTime
                )
            }
            msg.Init(msgBuf, msgBuf.capacity())
            msg.WriteLong(gameInitId)
            msg.WriteByte(SERVER_UNRELIABLE.SERVER_UNRELIABLE_MESSAGE_EMPTY.ordinal.toByte())
            client.channel.SendMessage(serverPort, serverTime, msg)
            client.lastEmptyTime = realTime
            return true
        }

        private fun SendPingToClient(clientNum: Int): Boolean {
            val msg = idBitMsg()
            val msgBuf = ByteBuffer.allocate(MsgChannel.MAX_MESSAGE_SIZE)
            val client = clients[clientNum]
            if (client.lastPingTime > realTime) {
                client.lastPingTime = realTime
            }
            if (realTime - client.lastPingTime < PING_RESEND_TIME) {
                return false
            }
            if (idAsyncNetwork.verbose.GetInteger() == 2) {
                Common.common.Printf(
                    "pinging client %d: gameInitId = %d, gameFrame = %d, gameTime = %d\n",
                    clientNum,
                    gameInitId,
                    gameFrame,
                    gameTime
                )
            }
            msg.Init(msgBuf, msgBuf.capacity())
            msg.WriteLong(gameInitId)
            msg.WriteByte(SERVER_UNRELIABLE.SERVER_UNRELIABLE_MESSAGE_PING.ordinal.toByte())
            msg.WriteLong(realTime)
            client.channel.SendMessage(serverPort, serverTime, msg)
            client.lastPingTime = realTime
            return true
        }

        private fun SendGameInitToClient(clientNum: Int) {
            val msg = idBitMsg()
            val msgBuf = ByteBuffer.allocate(MsgChannel.MAX_MESSAGE_SIZE)
            if (idAsyncNetwork.verbose.GetInteger() != 0) {
                Common.common.Printf(
                    "sending gameinit to client %d: gameInitId = %d, gameFrame = %d, gameTime = %d\n",
                    clientNum,
                    gameInitId,
                    gameFrame,
                    gameTime
                )
            }
            val client = clients[clientNum]

            // clear the unsent fragments. might flood winsock but that's ok
            while (client.channel.UnsentFragmentsLeft()) {
                client.channel.SendNextFragment(serverPort, serverTime)
            }
            msg.Init(msgBuf, msgBuf.capacity())
            msg.WriteLong(gameInitId)
            msg.WriteByte(SERVER_UNRELIABLE.SERVER_UNRELIABLE_MESSAGE_GAMEINIT.ordinal.toByte())
            msg.WriteLong(gameFrame)
            msg.WriteLong(gameTime)
            msg.WriteDeltaDict(Session.sessLocal.mapSpawnData.serverInfo, null)
            client.gameInitSequence = client.channel.SendMessage(serverPort, serverTime, msg)
        }

        private fun SendSnapshotToClient(clientNum: Int): Boolean {
            var i: Int
            var j: Int
            var index: Int
            var numUsercmds: Int
            val msg = idBitMsg()
            val msgBuf = ByteBuffer.allocate(MsgChannel.MAX_MESSAGE_SIZE)
            var last: usercmd_t?
            val clientInPVS = ByteArray(AsyncNetwork.MAX_ASYNC_CLIENTS shr 3)
            var client = clients[clientNum]
            if (serverTime - client.lastSnapshotTime < idAsyncNetwork.serverSnapshotDelay.GetInteger()) {
                return false
            }
            if (idAsyncNetwork.verbose.GetInteger() == 2) {
                Common.common.Printf(
                    "sending snapshot to client %d: gameInitId = %d, gameFrame = %d, gameTime = %d\n",
                    clientNum,
                    gameInitId,
                    gameFrame,
                    gameTime
                )
            }

            // how far is the client ahead of the server minus the packet delay
            client.clientAheadTime = client.gameTime - (gameTime + gameTimeResidual)

            // write the snapshot
            msg.Init(msgBuf, msgBuf.capacity())
            msg.WriteLong(gameInitId)
            msg.WriteByte(SERVER_UNRELIABLE.SERVER_UNRELIABLE_MESSAGE_SNAPSHOT.ordinal.toByte())
            msg.WriteLong(client.snapshotSequence)
            msg.WriteLong(gameFrame)
            msg.WriteLong(gameTime)
            msg.WriteByte(idMath.ClampChar(client.numDuplicatedUsercmds).code.toByte())
            msg.WriteShort(idMath.ClampShort(client.clientAheadTime))

            // write the game snapshot
            Game_local.game.ServerWriteSnapshot(
                clientNum,
                client.snapshotSequence,
                msg,
                clientInPVS,
                AsyncNetwork.MAX_ASYNC_CLIENTS
            )

            // write the latest user commands from the other clients in the PVS to the snapshot
            last = null
            i = 0
            while (i < AsyncNetwork.MAX_ASYNC_CLIENTS) {

                /*serverClient_t*/client = clients[i]
                if (client.clientState == serverClientState_t.SCS_FREE || i == clientNum) {
                    i++
                    continue
                }

                // if the client is not in the PVS
                if (0 == clientInPVS[i shr 3].toInt() and (1 shl (i and 7))) {
                    i++
                    continue
                }
                val maxRelay = idMath.ClampInt(
                    1,
                    AsyncNetwork.MAX_USERCMD_RELAY,
                    idAsyncNetwork.serverMaxUsercmdRelay.GetInteger()
                )

                // Max( 1, to always send at least one cmd, which we know we have because we call DuplicateUsercmds in RunFrame
                numUsercmds =
                    Max(1, Min(client.gameFrame, gameFrame + maxRelay) - gameFrame)
                msg.WriteByte(i.toByte())
                msg.WriteByte(numUsercmds.toByte())
                j = 0
                while (j < numUsercmds) {
                    index = gameFrame + j and AsyncNetwork.MAX_USERCMD_BACKUP - 1
                    idAsyncNetwork.WriteUserCmdDelta(msg, userCmds[index][i], last)
                    last = userCmds[index][i]
                    j++
                }
                i++
            }
            msg.WriteByte(AsyncNetwork.MAX_ASYNC_CLIENTS.toByte())
            client.channel.SendMessage(serverPort, serverTime, msg)
            client.lastSnapshotTime = serverTime
            client.snapshotSequence++
            client.numDuplicatedUsercmds = 0
            return true
        }

        @Throws(idException::class)
        private fun ProcessUnreliableClientMessage(clientNum: Int, msg: idBitMsg) {
            var i: Int
            val id: Int
            val acknowledgeSequence: Int
            val clientGameInitId: Int
            val clientGameFrame: Int
            val numUsercmds: Int
            var index: Int
            var last: usercmd_t?
            val client = clients[clientNum]
            if (client.clientState == serverClientState_t.SCS_ZOMBIE) {
                return
            }
            acknowledgeSequence = msg.ReadLong()
            clientGameInitId = msg.ReadLong()

            // while loading a map the client may send empty messages to keep the connection alive
            if (clientGameInitId == AsyncNetwork.GAME_INIT_ID_MAP_LOAD) {
                if (idAsyncNetwork.verbose.GetInteger() != 0) {
                    Common.common.Printf("ignore unreliable msg from client %d, gameInitId == ID_MAP_LOAD\n", clientNum)
                }
                return
            }

            // check if the client is in the right game
            if (clientGameInitId != gameInitId) {
                if (acknowledgeSequence > client.gameInitSequence) {
                    // the client is connected but not in the right game
                    client.clientState = serverClientState_t.SCS_CONNECTED

                    // send game init to client
                    SendGameInitToClient(clientNum)
                    if (Session.sessLocal.mapSpawnData.serverInfo.GetBool("si_pure")) {
                        client.clientState = serverClientState_t.SCS_PUREWAIT
                        if (!SendReliablePureToClient(clientNum)) {
                            client.clientState = serverClientState_t.SCS_CONNECTED
                        }
                    }
                } else if (idAsyncNetwork.verbose.GetInteger() != 0) {
                    Common.common.Printf(
                        "ignore unreliable msg from client %d, wrong gameInit, old sequence\n",
                        clientNum
                    )
                }
                return
            }
            client.acknowledgeSnapshotSequence = msg.ReadLong()
            if (client.clientState == serverClientState_t.SCS_CONNECTED) {

                // the client is in the right game
                client.clientState = serverClientState_t.SCS_INGAME

                // send the user info of other clients
                i = 0
                while (i < AsyncNetwork.MAX_ASYNC_CLIENTS) {
                    if (clients[i].clientState.ordinal >= serverClientState_t.SCS_CONNECTED.ordinal && i != clientNum) {
                        SendUserInfoToClient(clientNum, i, Session.sessLocal.mapSpawnData.userInfo[i])
                    }
                    i++
                }

                // send synchronized cvars to client
                SendSyncedCvarsToClient(clientNum, Session.sessLocal.mapSpawnData.syncedCVars)
                SendEnterGameToClient(clientNum)

                // get the client running in the game
                Game_local.game.ServerClientBegin(clientNum)

                // write any reliable messages to initialize the client game state
                Game_local.game.ServerWriteInitialReliableMessages(clientNum)
            } else if (client.clientState == serverClientState_t.SCS_INGAME) {

                // apply the last snapshot the client received
                if (Game_local.game.ServerApplySnapshot(clientNum, client.acknowledgeSnapshotSequence)) {
                    SendApplySnapshotToClient(clientNum, client.acknowledgeSnapshotSequence)
                }
            }

            // process the unreliable message
            id = msg.ReadByte().toInt()
            when (CLIENT_UNRELIABLE.values()[id]) {
                CLIENT_UNRELIABLE.CLIENT_UNRELIABLE_MESSAGE_EMPTY -> {
                    if (idAsyncNetwork.verbose.GetInteger() != 0) {
                        Common.common.Printf("received empty message for client %d\n", clientNum)
                    }
                }

                CLIENT_UNRELIABLE.CLIENT_UNRELIABLE_MESSAGE_PINGRESPONSE -> {
                    client.clientPing = realTime - msg.ReadLong()
                }

                CLIENT_UNRELIABLE.CLIENT_UNRELIABLE_MESSAGE_USERCMD -> {
                    client.clientPrediction = msg.ReadShort().toInt()

                    // read user commands
                    clientGameFrame = msg.ReadLong()
                    numUsercmds = msg.ReadByte().toInt()
                    last = null
                    i = clientGameFrame - numUsercmds + 1
                    while (i <= clientGameFrame) {
                        index = i and AsyncNetwork.MAX_USERCMD_BACKUP - 1
                        idAsyncNetwork.ReadUserCmdDelta(msg, userCmds[index][clientNum], last)
                        userCmds[index][clientNum].gameFrame = i
                        userCmds[index][clientNum].duplicateCount = 0
                        if (idAsyncNetwork.UsercmdInputChanged(
                                userCmds[i - 1 and AsyncNetwork.MAX_USERCMD_BACKUP - 1][clientNum],
                                userCmds[index][clientNum]
                            )
                        ) {
                            client.lastInputTime = serverTime
                        }
                        last = userCmds[index][clientNum]
                        i++
                    }
                    if (last != null) {
                        client.gameFrame = last.gameFrame
                        client.gameTime = last.gameTime
                    }
                    if (idAsyncNetwork.verbose.GetInteger() == 2) {
                        Common.common.Printf(
                            "received user command for client %d, gameInitId = %d, gameFrame, %d gameTime %d\n",
                            clientNum,
                            clientGameInitId,
                            client.gameFrame,
                            client.gameTime
                        )
                    }
                }

                else -> {
                    Common.common.Printf("unknown unreliable message %d from client %d\n", id, clientNum)
                }
            }
        }

        private fun ProcessReliableClientMessages(clientNum: Int) {
            val msg = idBitMsg()
            val msgBuf = ByteBuffer.allocate(MsgChannel.MAX_MESSAGE_SIZE)
            var id: Int
            val client = clients[clientNum]
            msg.Init(msgBuf, msgBuf.capacity())
            while (client.channel.GetReliableMessage(msg)) {
                id = msg.ReadByte().toInt()
                when (CLIENT_RELIABLE.values()[id]) {
                    CLIENT_RELIABLE.CLIENT_RELIABLE_MESSAGE_CLIENTINFO -> {
                        val info = idDict()
                        msg.ReadDeltaDict(info, Session.sessLocal.mapSpawnData.userInfo[clientNum])
                        SendUserInfoBroadcast(clientNum, info)
                    }

                    CLIENT_RELIABLE.CLIENT_RELIABLE_MESSAGE_PRINT -> {
                        val string = CharArray(MAX_STRING_CHARS)
                        msg.ReadString(string, string.size)
                        Common.common.Printf("%s\n", TempDump.ctos(string))
                    }

                    CLIENT_RELIABLE.CLIENT_RELIABLE_MESSAGE_DISCONNECT -> {
                        DropClient(clientNum, "#str_07138")
                    }

                    CLIENT_RELIABLE.CLIENT_RELIABLE_MESSAGE_PURE -> {

                        // we get this message once the client has successfully updated it's pure list
                        ProcessReliablePure(clientNum, msg)
                    }

                    else -> {

                        // pass reliable message on to game code
                        Game_local.game.ServerProcessReliableMessage(clientNum, msg)
                    }
                }
            }
        }

        @Throws(idException::class)
        private fun ProcessChallengeMessage(from: netadr_t, msg: idBitMsg) {
            var i: Int
            val clientId: Int
            var oldest: Int
            var oldestTime: Int
            val outMsg = idBitMsg()
            val msgBuf = ByteBuffer.allocate(MsgChannel.MAX_MESSAGE_SIZE)
            clientId = msg.ReadLong()
            oldest = 0
            oldestTime = 2147483647

            // see if we already have a challenge for this ip
            i = 0
            while (i < MAX_CHALLENGES) {
                if (!challenges[i].connected && win_net.Sys_CompareNetAdrBase(
                        from,
                        challenges[i].address
                    ) && clientId == challenges[i].clientId
                ) {
                    break
                }
                if (challenges[i].time < oldestTime) {
                    oldestTime = challenges[i].time
                    oldest = i
                }
                i++
            }
            if (i >= MAX_CHALLENGES) {
                // this is the first time this client has asked for a challenge
                val random = Random()
                i = oldest
                challenges[i].address = from
                challenges[i].clientId = clientId
                // note: in C++ rand() is an int value in range [0, 32767]. The upper bound is at least 32767, however depends on impl.
                challenges[i].challenge = random.nextInt(32767) shl 16 xor random.nextInt(32767) xor serverTime
                challenges[i].time = serverTime
                challenges[i].connected = false
                challenges[i].authState = authState_t.CDK_WAIT
                challenges[i].authReply = authReply_t.AUTH_NONE
                challenges[i].authReplyMsg = authReplyMsg_t.AUTH_REPLY_WAITING
                challenges[i].authReplyPrint.set("")
                challenges[i].guid[0] = '\u0000'
            }
            challenges[i].pingTime = serverTime
            Common.common.Printf(
                "sending challenge 0x%x to %s\n",
                challenges[i].challenge,
                win_net.Sys_NetAdrToString(from)
            )
            outMsg.Init(msgBuf, msgBuf.capacity())
            outMsg.WriteShort(MsgChannel.CONNECTIONLESS_MESSAGE_ID.toShort())
            outMsg.WriteString("challengeResponse")
            outMsg.WriteLong(challenges[i].challenge)
            outMsg.WriteShort(serverId.toShort())
            outMsg.WriteString(CVarSystem.cvarSystem.GetCVarString("fs_game_base"))
            outMsg.WriteString(CVarSystem.cvarSystem.GetCVarString("fs_game"))
            serverPort.SendPacket(from, outMsg.GetData()!!, outMsg.GetSize())
            if (!ID_ENFORCE_KEY_CLIENT) { // dhewm3: CD key enforcement disabled, accept all clients
                if (!win_net.Sys_IsLANAddress(from)) {
                    Common.common.DPrintf(
                        "Build does not have CD Key Enforcement enabled. Client %s is not a LAN address, but will be accepted\n",
                        win_net.Sys_NetAdrToString(from)
                    )
                }
                challenges[i].authState = authState_t.CDK_OK
            } else if (win_net.Sys_IsLANAddress(from)) {
                // no CD Key check for LAN clients
                challenges[i].authState = authState_t.CDK_OK
            } else {
                if (idAsyncNetwork.LANServer.GetBool()) {
                    Common.common.Printf(
                        "net_LANServer is enabled. Client %s is not a LAN address, will be rejected\n",
                        win_net.Sys_NetAdrToString(from)
                    )
                    challenges[i].authState = authState_t.CDK_ONLYLAN
                } else {
                    // emit a cd key confirmation request
                    outMsg.BeginWriting()
                    outMsg.WriteShort(MsgChannel.CONNECTIONLESS_MESSAGE_ID.toShort())
                    outMsg.WriteString("srvAuth")
                    outMsg.WriteLong(AsyncNetwork.ASYNC_PROTOCOL_VERSION)
                    outMsg.WriteNetadr(from)
                    outMsg.WriteLong(-1) // this identifies "challenge" auth vs "connect" auth
                    // protocol 1.37 addition
                    outMsg.WriteByte(if (FileSystem_h.fileSystem.RunningD3XP()) 1 else 0)
                    serverPort.SendPacket(idAsyncNetwork.GetMasterAddress(), outMsg.GetData()!!, outMsg.GetSize())
                }
            }
        }

        @Throws(idException::class)
        private fun ProcessConnectMessage(from: netadr_t, msg: idBitMsg) {
            var clientNum = 0
            val protocol: Int
            val clientDataChecksum: Int
            val challenge: Int
            val clientId: Int
            val ping: Int
            val clientRate: Int
            val outMsg = idBitMsg()
            val msgBuf = ByteBuffer.allocate(MsgChannel.MAX_MESSAGE_SIZE)
            val guid = CharArray(12)
            val password = CharArray(17)
            var i: Int
            var ichallenge: Int = 0
            var islot: Int
            val OS: Int
            var numClients: Int
            protocol = msg.ReadLong() // C++ protocol does not include OS in the connect message
            OS = BUILD_OS_ID

            // check the protocol version
            if (protocol != AsyncNetwork.ASYNC_PROTOCOL_VERSION) {
                // that's a msg back to a client, we don't know about it's localization, so send english
                PrintOOB(
                    from,
                    SERVER_PRINT.SERVER_PRINT_BADPROTOCOL.ordinal,
                    Str.va(
                        "server uses protocol %d.%d\n",
                        Licensee.ASYNC_PROTOCOL_MAJOR,
                        AsyncNetwork.ASYNC_PROTOCOL_MINOR
                    )
                )
                return
            }
            clientDataChecksum = msg.ReadLong()
            challenge = msg.ReadLong()
            clientId = msg.ReadShort().toInt()
            clientRate = msg.ReadLong()

            // check the client data - only for non pure servers
            if (0 == Session.sessLocal.mapSpawnData.serverInfo.GetInt("si_pure") && clientDataChecksum != serverDataChecksum.toInt()) {
                PrintOOB(from, SERVER_PRINT.SERVER_PRINT_MISC.ordinal, "#str_04842")
                return
            }
            if (ValidateChallenge(from, challenge, clientId).also { ichallenge = it } == -1) {
                return
            }
            challenges[ichallenge].OS = OS
            msg.ReadString(guid, guid.size)
            when (challenges[ichallenge].authState) {
                authState_t.CDK_PUREWAIT -> {
                    SendPureServerMessage(from, OS)
                    return
                }

                authState_t.CDK_ONLYLAN -> {
                    Common.common.DPrintf("%s: not a lan client\n", win_net.Sys_NetAdrToString(from))
                    PrintOOB(from, SERVER_PRINT.SERVER_PRINT_MISC.ordinal, "#str_04843")
                    return
                }

                authState_t.CDK_WAIT -> {
                    if (challenges[ichallenge].authReply == authReply_t.AUTH_NONE && Min(
                            serverTime - lastAuthTime,
                            serverTime - challenges[ichallenge].time
                        ) > AUTHORIZE_TIMEOUT
                    ) {
                        Common.common.DPrintf("%s: Authorize server timed out\n", win_net.Sys_NetAdrToString(from))
                        //return // will continue with the connecting process
                    }
                    val msg2: String
                    val l_msg: String
                    msg2 = if (challenges[ichallenge].authReplyMsg != authReplyMsg_t.AUTH_REPLY_PRINT) {
                        authReplyMsg[challenges[ichallenge].authReplyMsg.ordinal]
                    } else {
                        challenges[ichallenge].authReplyPrint.toString()
                    }
                    l_msg = Common.common.GetLanguageDict().GetString(msg2)
                    Common.common.DPrintf("%s: %s\n", win_net.Sys_NetAdrToString(from), l_msg)
                    if (challenges[ichallenge].authReplyMsg == authReplyMsg_t.AUTH_REPLY_UNKNOWN || challenges[ichallenge].authReplyMsg == authReplyMsg_t.AUTH_REPLY_WAITING
                    ) {
                        // the client may be trying to connect to us in LAN mode, and the server disagrees
                        // let the client know so it would switch to authed connection
                        val outMsg2 = idBitMsg()
                        val msgBuf2 = ByteBuffer.allocate(MsgChannel.MAX_MESSAGE_SIZE)
                        outMsg2.Init(msgBuf2, msgBuf2.capacity())
                        outMsg2.WriteShort(MsgChannel.CONNECTIONLESS_MESSAGE_ID.toShort())
                        outMsg2.WriteString("authrequired")
                        serverPort.SendPacket(from, outMsg2.GetData()!!, outMsg2.GetSize())
                    }
                    PrintOOB(from, SERVER_PRINT.SERVER_PRINT_MISC.ordinal, msg2)

                    // update the guid in the challenges
                    idStr.snPrintf(
                        challenges[ichallenge].guid,
                        challenges[ichallenge].guid.size,
                        "%s",
                        TempDump.ctos(guid)
                    )

                    // once auth replied denied, stop sending further requests
                    if (challenges[ichallenge].authReply != authReply_t.AUTH_DENY) {
                        // emit a cd key confirmation request
                        outMsg.Init(msgBuf, msgBuf.capacity())
                        outMsg.WriteShort(MsgChannel.CONNECTIONLESS_MESSAGE_ID.toShort())
                        outMsg.WriteString("srvAuth")
                        outMsg.WriteLong(AsyncNetwork.ASYNC_PROTOCOL_VERSION)
                        outMsg.WriteNetadr(from)
                        outMsg.WriteLong(clientId)
                        outMsg.WriteString(TempDump.ctos(guid))
                        // protocol 1.37 addition
                        outMsg.WriteByte(if (FileSystem_h.fileSystem.RunningD3XP()) 1 else 0)
                        serverPort.SendPacket(idAsyncNetwork.GetMasterAddress(), outMsg.GetData()!!, outMsg.GetSize())
                    }
                    return
                }

                else -> assert(challenges[ichallenge].authState == authState_t.CDK_OK || challenges[ichallenge].authState == authState_t.CDK_PUREOK)
            }
            numClients = 0
            i = 0
            while (i < AsyncNetwork.MAX_ASYNC_CLIENTS) {
                val client = clients[i]
                if (client.clientState.ordinal >= serverClientState_t.SCS_PUREWAIT.ordinal) {
                    numClients++
                }
                i++
            }

            // game may be passworded, client banned by IP or GUID
            // if authState == CDK_PUREOK, the check was already performed once before entering pure checks
            // but meanwhile, the max players may have been reached
            msg.ReadString(password, password.size)
            val reason = CharArray(MAX_STRING_CHARS)
            val reply = Game_local.game.ServerAllowClient(
                numClients,
                win_net.Sys_NetAdrToString(from),
                TempDump.ctos(guid),
                TempDump.ctos(password),
                reason
            )
            if (reply != allowReply_t.ALLOW_YES) {
                Common.common.DPrintf("game denied connection for %s\n", win_net.Sys_NetAdrToString(from))

                // SERVER_PRINT_GAMEDENY passes the game opcode through. Don't use PrintOOB
                outMsg.Init(msgBuf, msgBuf.capacity())
                outMsg.WriteShort(MsgChannel.CONNECTIONLESS_MESSAGE_ID.toShort())
                outMsg.WriteString("print")
                outMsg.WriteLong(SERVER_PRINT.SERVER_PRINT_GAMEDENY.ordinal)
                outMsg.WriteLong(reply.ordinal)
                outMsg.WriteString(TempDump.ctos(reason))
                serverPort.SendPacket(from, outMsg.GetData()!!, outMsg.GetSize())
                return
            }

            // enter pure checks if necessary
            if (Session.sessLocal.mapSpawnData.serverInfo.GetInt("si_pure") != 0 && challenges[ichallenge].authState != authState_t.CDK_PUREOK) {
                if (SendPureServerMessage(from, OS)) {
                    challenges[ichallenge].authState = authState_t.CDK_PUREWAIT
                    return
                }
            }

            // push back decl checksum here when running pure. just an additional safe check
            if (Session.sessLocal.mapSpawnData.serverInfo.GetInt("si_pure") != 0 && clientDataChecksum != serverDataChecksum.toInt()) {
                PrintOOB(from, SERVER_PRINT.SERVER_PRINT_MISC.ordinal, "#str_04844")
                return
            }
            ping = serverTime - challenges[ichallenge].pingTime
            Common.common.Printf("challenge from %s connecting with %d ping\n", win_net.Sys_NetAdrToString(from), ping)
            challenges[ichallenge].connected = true

            // find a slot for the client
            islot = 0
            while (islot < 3) {
                clientNum = 0
                while (clientNum < AsyncNetwork.MAX_ASYNC_CLIENTS) {
                    val client = clients[clientNum]
                    if (islot == 0) {
                        // if this slot uses the same IP and port
                        if (win_net.Sys_CompareNetAdrBase(from, client.channel.GetRemoteAddress())
                            && (clientId == client.clientId || from.port == client.channel.GetRemoteAddress().port)
                        ) {
                            break
                        }
                    } else if (islot == 1) {
                        // if this client is not connected and the slot uses the same IP
                        if (client.clientState.ordinal >= serverClientState_t.SCS_PUREWAIT.ordinal) {
                            clientNum++
                            continue
                        }
                        if (win_net.Sys_CompareNetAdrBase(from, client.channel.GetRemoteAddress())) {
                            break
                        }
                    } else if (islot == 2) {
                        // if this slot is free
                        if (client.clientState == serverClientState_t.SCS_FREE) {
                            break
                        }
                    }
                    clientNum++
                }
                if (clientNum < AsyncNetwork.MAX_ASYNC_CLIENTS) {
                    // initialize
                    clients[clientNum].channel.Init(from, serverId)
                    clients[clientNum].OS = OS
                    System.arraycopy(guid, 0, clients[clientNum].guid, 0, 12)
                    clients[clientNum].guid[11] = Char(0)
                    break
                }
                islot++
            }

            // if no free spots available
            if (clientNum >= AsyncNetwork.MAX_ASYNC_CLIENTS) {
                PrintOOB(from, SERVER_PRINT.SERVER_PRINT_MISC.ordinal, "#str_04845")
                return
            }
            Common.common.Printf("sending connect response to %s\n", win_net.Sys_NetAdrToString(from))

            // send connect response message
            outMsg.Init(msgBuf, msgBuf.capacity())
            outMsg.WriteShort(MsgChannel.CONNECTIONLESS_MESSAGE_ID.toShort())
            outMsg.WriteString("connectResponse")
            outMsg.WriteLong(clientNum)
            outMsg.WriteLong(gameInitId)
            outMsg.WriteLong(gameFrame)
            outMsg.WriteLong(gameTime)
            outMsg.WriteDeltaDict(Session.sessLocal.mapSpawnData.serverInfo, null)
            serverPort.SendPacket(from, outMsg.GetData()!!, outMsg.GetSize())
            InitClient(clientNum, clientId, clientRate)
            clients[clientNum].gameInitSequence = 1
            clients[clientNum].snapshotSequence = 1

            // clear the challenge struct so a reconnect from this client IP starts clean
            challenges[ichallenge] = challenge_s()
        }

        private fun ProcessRemoteConsoleMessage(from: netadr_t, msg: idBitMsg) {
            val msgBuf = StringBuilder(952)
            val string = CharArray(MAX_STRING_CHARS)
            if (idAsyncNetwork.serverRemoteConsolePassword.GetString()!!.isEmpty()) {
                PrintOOB(from, SERVER_PRINT.SERVER_PRINT_MISC.ordinal, "#str_04846")
                return
            }
            msg.ReadString(string, string.size)
            if (idStr.Icmp(
                    TempDump.ctos(string),
                    idAsyncNetwork.serverRemoteConsolePassword.GetString()!!
                ) != 0
            ) {
                PrintOOB(from, SERVER_PRINT.SERVER_PRINT_MISC.ordinal, "#str_04847")
                return
            }
            msg.ReadString(string, string.size)
            Common.common.Printf("rcon from %s: %s\n", win_net.Sys_NetAdrToString(from), string)
            rconAddress = from
            noRconOutput = true
            Common.common.BeginRedirect(msgBuf, msgBuf.capacity(), RConRedirect.getInstance())
            CmdSystem.cmdSystem.BufferCommandText(cmdExecution_t.CMD_EXEC_NOW, TempDump.ctos(string))
            Common.common.EndRedirect()
            if (noRconOutput) {
                PrintOOB(rconAddress, SERVER_PRINT.SERVER_PRINT_RCON.ordinal, "#str_04848")
            }
        }

        private fun ProcessGetInfoMessage(from: netadr_t, msg: idBitMsg) {
            var i: Int
            val challenge: Int
            val outMsg = idBitMsg()
            val msgBuf = ByteBuffer.allocate(MsgChannel.MAX_MESSAGE_SIZE)
            if (!IsActive()) {
                return
            }
            Common.common.DPrintf("Sending info response to %s\n", win_net.Sys_NetAdrToString(from))
            challenge = msg.ReadLong()
            outMsg.Init(msgBuf, msgBuf.capacity())
            outMsg.WriteShort(MsgChannel.CONNECTIONLESS_MESSAGE_ID.toShort())
            outMsg.WriteString("infoResponse")
            outMsg.WriteLong(challenge)
            outMsg.WriteLong(AsyncNetwork.ASYNC_PROTOCOL_VERSION)
            outMsg.WriteDeltaDict(Session.sessLocal.mapSpawnData.serverInfo, null)
            i = 0
            while (i < AsyncNetwork.MAX_ASYNC_CLIENTS) {
                val client = clients[i]
                if (client.clientState.ordinal < serverClientState_t.SCS_CONNECTED.ordinal) {
                    i++
                    continue
                }
                outMsg.WriteByte(i.toByte())
                outMsg.WriteShort(client.clientPing.toShort())
                outMsg.WriteLong(client.channel.GetMaxOutgoingRate())
                outMsg.WriteString(
                    Session.sessLocal.mapSpawnData.userInfo[i].GetString("ui_name", "Player")
                )
                i++
            }
            outMsg.WriteByte(AsyncNetwork.MAX_ASYNC_CLIENTS.toByte())
            // DG: dhewm3 eliminated GetOSMask(); sending -1 restores compatibility with id's masterserver
            outMsg.WriteLong(-1)
            serverPort.SendPacket(from, outMsg.GetData()!!, outMsg.GetSize())
        }

        private fun ConnectionlessMessage(from: netadr_t, msg: idBitMsg): Boolean {
            val chrs =
                CharArray(MAX_STRING_CHARS * 2) // M. Quinn - Even Balance - PB Packets need more than 1024
            val string: String
            msg.ReadString(chrs, chrs.size)
            string = TempDump.ctos(chrs)

            // info request
            if (idStr.Icmp(string, "getInfo") == 0) {
                ProcessGetInfoMessage(from, msg)
                return false
            }

            // remote console
            if (idStr.Icmp(string, "rcon") == 0) {
                ProcessRemoteConsoleMessage(from, msg)
                return true
            }
            if (!active) {
                PrintOOB(from, SERVER_PRINT.SERVER_PRINT_MISC.ordinal, "#str_04849")
                return false
            }

            // challenge from a client
            if (idStr.Icmp(string, "challenge") == 0) {
                ProcessChallengeMessage(from, msg)
                return false
            }

            // connect from a client
            if (idStr.Icmp(string, "connect") == 0) {
                ProcessConnectMessage(from, msg)
                return false
            }

            // pure mesasge from a client
            if (idStr.Icmp(string, "pureClient") == 0) {
                ProcessPureMessage(from, msg)
                return false
            }

            // download request
            if (idStr.Icmp(string, "downloadRequest") == 0) {
                ProcessDownloadRequestMessage(from, msg)
            }

            // auth server
            if (idStr.Icmp(string, "auth") == 0) {
                if (!win_net.Sys_CompareNetAdrBase(from, idAsyncNetwork.GetMasterAddress())) {
                    Common.common.Printf("auth: bad source %s\n", win_net.Sys_NetAdrToString(from))
                    return false
                }
                if (idAsyncNetwork.LANServer.GetBool()) {
                    Common.common.Printf("auth message from master. net_LANServer is enabled, ignored.\n")
                }
                ProcessAuthMessage(msg)
                return false
            }
            return false
        }

        private fun ProcessMessage(from: netadr_t, msg: idBitMsg): Boolean {
            var i: Int
            val id: Int
            val sequence = CInt()
            val outMsg = idBitMsg()
            val msgBuf = ByteBuffer.allocate(MsgChannel.MAX_MESSAGE_SIZE)
            id = msg.ReadShort().toInt()

            // check for a connectionless message
            if (id == MsgChannel.CONNECTIONLESS_MESSAGE_ID) {
                return ConnectionlessMessage(from, msg)
            }
            if (msg.GetRemaingData() < 4) {
                Common.common.DPrintf("%s: tiny packet\n", win_net.Sys_NetAdrToString(from))
                return false
            }

            // find out which client the message is from
            i = 0
            while (i < AsyncNetwork.MAX_ASYNC_CLIENTS) {
                val client = clients[i]
                if (client.clientState == serverClientState_t.SCS_FREE) {
                    i++
                    continue
                }

                // This does not compare the UDP port, because some address translating
                // routers will change that at arbitrary times.
                if (!win_net.Sys_CompareNetAdrBase(from, client.channel.GetRemoteAddress()) || id != client.clientId) {
                    i++
                    continue
                }

                // make sure it is a valid, in sequence packet
                if (!client.channel.Process(from, serverTime, msg, sequence)) {
                    return false // out of order, duplicated, fragment, etc.
                }

                // zombie clients still need to do the channel processing to make sure they don't
                // need to retransmit the final reliable message, but they don't do any other processing
                if (client.clientState == serverClientState_t.SCS_ZOMBIE) {
                    return false
                }
                client.lastPacketTime = serverTime
                ProcessReliableClientMessages(i)
                ProcessUnreliableClientMessage(i, msg)
                return false
                i++
            }

            // if we received a sequenced packet from an address we don't recognize,
            // send an out of band disconnect packet to it
            outMsg.Init(msgBuf, msgBuf.capacity())
            outMsg.WriteShort(MsgChannel.CONNECTIONLESS_MESSAGE_ID.toShort())
            outMsg.WriteString("disconnect")
            serverPort.SendPacket(from, outMsg.GetData()!!, outMsg.GetSize())
            return false
        }

        @Throws(idException::class)
        private fun ProcessAuthMessage(msg: idBitMsg) {
            val client_from = netadr_t()
            val client_guid = CharArray(12)
            val string = CharArray(MAX_STRING_CHARS)
            var i: Int
            val clientId: Int
            val reply: authReply_t
            var replyMsg = authReplyMsg_t.AUTH_REPLY_WAITING
            val replyPrintMsg = idStr()
            reply = authReply_t.values()[msg.ReadByte().toInt()]
            if (reply.ordinal <= 0 || reply.ordinal >= authReply_t.AUTH_MAXSTATES.ordinal) {
                Common.common.DPrintf("auth: invalid reply %d\n", reply)
                return
            }
            clientId = msg.ReadShort().toInt()
            msg.ReadNetadr(client_from)
            msg.ReadString(client_guid, client_guid.size)
            if (reply != authReply_t.AUTH_OK) {
                replyMsg = authReplyMsg_t.values()[msg.ReadByte().toInt()]
                if (replyMsg.ordinal <= 0 || replyMsg.ordinal >= authReplyMsg_t.AUTH_REPLY_MAXSTATES.ordinal) {
                    Common.common.DPrintf("auth: invalid reply msg %d\n", replyMsg)
                    return
                }
                if (replyMsg == authReplyMsg_t.AUTH_REPLY_PRINT) {
                    msg.ReadString(string, MAX_STRING_CHARS)
                    replyPrintMsg.set(TempDump.ctos(string))
                }
            }
            lastAuthTime = serverTime

            // no message parsing below
            i = 0
            while (i < MAX_CHALLENGES) {
                if (!challenges[i].connected && challenges[i].clientId == clientId) {
                    // return if something is wrong
                    // break if we have found a valid auth
                    if (0 == TempDump.strLen(challenges[i].guid)) {
                        Common.common.DPrintf(
                            "auth: client %s has no guid yet\n",
                            win_net.Sys_NetAdrToString(challenges[i].address)
                        )
                        return
                    }
                    if (idStr.Cmp(challenges[i].guid, client_guid) != 0) {
                        Common.common.DPrintf(
                            "auth: client %s %s not matched, auth server says guid %s\n",
                            win_net.Sys_NetAdrToString(challenges[i].address),
                            challenges[i].guid,
                            client_guid
                        )
                        return
                    }
                    if (!win_net.Sys_CompareNetAdrBase(client_from, challenges[i].address)) {
                        // let auth work when server and master don't see the same IP
                        Common.common.DPrintf(
                            "auth: matched guid '%s' for != IPs %s and %s\n",
                            client_guid,
                            win_net.Sys_NetAdrToString(client_from),
                            win_net.Sys_NetAdrToString(challenges[i].address)
                        )
                    }
                    break
                }
                i++
            }
            if (i >= MAX_CHALLENGES) {
                Common.common.DPrintf(
                    "auth: failed client lookup %s %s\n",
                    win_net.Sys_NetAdrToString(client_from),
                    client_guid
                )
                return
            }
            if (challenges[i].authState != authState_t.CDK_WAIT) {
                Common.common.DWarning(
                    "auth: challenge 0x%x %s authState %d != CDK_WAIT",
                    challenges[i].challenge,
                    win_net.Sys_NetAdrToString(challenges[i].address),
                    challenges[i].authState
                )
                return
            }
            idStr.snPrintf(challenges[i].guid, 12, "%s", TempDump.ctos(client_guid))
            if (reply == authReply_t.AUTH_OK) {
                challenges[i].authState = authState_t.CDK_OK
                Common.common.Printf("client %s %s is authed\n", win_net.Sys_NetAdrToString(client_from), client_guid)
            } else {
                val msg1: String
                msg1 = if (replyMsg != authReplyMsg_t.AUTH_REPLY_PRINT) {
                    authReplyMsg[replyMsg.ordinal]
                } else {
                    replyPrintMsg.toString()
                }
                // maybe localize it
                val l_msg = Common.common.GetLanguageDict().GetString(msg1)
                Common.common.DPrintf(
                    "auth: client %s %s - %s %s\n",
                    win_net.Sys_NetAdrToString(client_from),
                    client_guid,
                    authReplyStr[reply.ordinal],
                    l_msg
                )
                challenges[i].authReply = reply
                challenges[i].authReplyMsg = replyMsg
                challenges[i].authReplyPrint.set(replyPrintMsg)
            }
        }

        private fun SendPureServerMessage(
            to: netadr_t,
            OS: Int
        ): Boolean {                                        // returns false if no pure paks on the list
            val outMsg = idBitMsg()
            val msgBuf = ByteBuffer.allocate(MsgChannel.MAX_MESSAGE_SIZE)
            val serverChecksums = IntArray(FileSystem_h.MAX_PURE_PAKS)
            var i: Int
            FileSystem_h.fileSystem.GetPureServerChecksums(serverChecksums, OS, null)
            if (0 == serverChecksums[0]) {
                // happens if you run fully expanded assets with si_pure 1
                Common.common.Warning("pure server has no pak files referenced")
                return false
            }
            Common.common.DPrintf("client %s: sending pure pak list\n", win_net.Sys_NetAdrToString(to))

            // send our list of required paks
            outMsg.Init(msgBuf, msgBuf.capacity())
            outMsg.WriteShort(MsgChannel.CONNECTIONLESS_MESSAGE_ID.toShort())
            outMsg.WriteString("pureServer")
            i = 0
            while (serverChecksums[i] != 0) {
                outMsg.WriteLong(serverChecksums[i++])
            }
            outMsg.WriteLong(0)

            serverPort.SendPacket(to, outMsg.GetData()!!, outMsg.GetSize())
            return true
        }

        private fun ProcessPureMessage(from: netadr_t, msg: idBitMsg) {
            var iclient: Int
            val challenge: Int
            val clientId: Int
            val reply = idStr()
            challenge = msg.ReadLong()
            clientId = msg.ReadShort().toInt()
            if (ValidateChallenge(from, challenge, clientId).also { iclient = it } == -1) {
                return
            }
            if (challenges[iclient].authState != authState_t.CDK_PUREWAIT) {
                Common.common.DPrintf(
                    "client %s: got pure message, not in CDK_PUREWAIT\n",
                    win_net.Sys_NetAdrToString(from)
                )
                return
            }
            if (!VerifyChecksumMessage(iclient, from, msg, reply, challenges[iclient].OS)) {
                PrintOOB(from, SERVER_PRINT.SERVER_PRINT_MISC.ordinal, reply.toString())
                return
            }
            Common.common.DPrintf("client %s: passed pure checks\n", win_net.Sys_NetAdrToString(from))
            challenges[iclient].authState =
                authState_t.CDK_PUREOK // next connect message will get the client through completely
        }

        private fun ValidateChallenge(
            from: netadr_t,
            challenge: Int,
            clientId: Int
        ): Int {    // returns -1 if validate failed
            var i: Int
            i = 0
            while (i < AsyncNetwork.MAX_ASYNC_CLIENTS) {
                val client = clients[i]
                if (client.clientState == serverClientState_t.SCS_FREE) {
                    i++
                    continue
                }
                if (win_net.Sys_CompareNetAdrBase(from, client.channel.GetRemoteAddress())
                    && (clientId == client.clientId || from.port == client.channel.GetRemoteAddress().port)
                ) {
                    if (serverTime - client.lastConnectTime < MIN_RECONNECT_TIME) {
                        Common.common.Printf("%s: reconnect rejected : too soon\n", win_net.Sys_NetAdrToString(from))
                        return -1
                    }
                    break
                }
                i++
            }
            i = 0
            while (i < MAX_CHALLENGES) {
                if (win_net.Sys_CompareNetAdrBase(
                        from,
                        challenges[i].address
                    ) && from.port == challenges[i].address.port
                ) {
                    if (challenge == challenges[i].challenge) {
                        break
                    }
                }
                i++
            }
            if (i == MAX_CHALLENGES) {
                PrintOOB(from, SERVER_PRINT.SERVER_PRINT_BADCHALLENGE.ordinal, "#str_04840")
                return -1
            }
            return i
        }

        private fun SendReliablePureToClient(clientNum: Int): Boolean {
            val msg = idBitMsg()
            val msgBuf = ByteBuffer.allocate(MsgChannel.MAX_MESSAGE_SIZE)
            val serverChecksums = IntArray(FileSystem_h.MAX_PURE_PAKS)
            var i: Int
            FileSystem_h.fileSystem.GetPureServerChecksums(serverChecksums, clients[clientNum].OS, null)
            if (0 == serverChecksums[0]) {
                // happens if you run fully expanded assets with si_pure 1
                Common.common.Warning("pure server has no pak files referenced")
                return false
            }
            Common.common.DPrintf(
                "client %d: sending pure pak list (reliable channel) @ gameInitId %d\n",
                clientNum,
                gameInitId
            )
            msg.Init(msgBuf, msgBuf.capacity())
            msg.WriteByte(SERVER_RELIABLE.SERVER_RELIABLE_MESSAGE_PURE.ordinal.toByte())
            msg.WriteLong(gameInitId)
            i = 0
            while (serverChecksums[i] != 0) {
                msg.WriteLong(serverChecksums[i++])
            }
            msg.WriteLong(0)
            SendReliableMessage(clientNum, msg)
            return true
        }

        private fun ProcessReliablePure(clientNum: Int, msg: idBitMsg) {
            val reply = idStr()
            val outMsg = idBitMsg()
            val msgBuf = ByteBuffer.allocate(MsgChannel.MAX_MESSAGE_SIZE)
            val clientGameInitId: Int
            clientGameInitId = msg.ReadLong()
            if (clientGameInitId != gameInitId) {
                Common.common.DPrintf(
                    "client %d: ignoring reliable pure from an old gameInit (%d)\n",
                    clientNum,
                    clientGameInitId
                )
                return
            }
            if (clients[clientNum].clientState != serverClientState_t.SCS_PUREWAIT) {
                // should not happen unless something is very wrong. still, don't let this crash us, just get rid of the client
                Common.common.DPrintf(
                    "client %d: got reliable pure while != SCS_PUREWAIT, sending a reload\n",
                    clientNum
                )
                outMsg.Init(msgBuf, msgBuf.capacity())
                outMsg.WriteByte(SERVER_RELIABLE.SERVER_RELIABLE_MESSAGE_RELOAD.ordinal.toByte())
                // FIX: was sending msg (incoming message) instead of outMsg (crafted reload message)
                SendReliableMessage(clientNum, outMsg)
                // go back to SCS_CONNECTED to sleep on the client until it goes away for a reconnect
                clients[clientNum].clientState = serverClientState_t.SCS_CONNECTED
                return
            }
            if (!VerifyChecksumMessage(clientNum, null, msg, reply, clients[clientNum].OS)) {
                reply.set(DropClient(clientNum, reply.toString()))
                return
            }
            Common.common.DPrintf("client %d: passed pure checks (reliable channel)\n", clientNum)
            clients[clientNum].clientState = serverClientState_t.SCS_CONNECTED
        }

        private fun VerifyChecksumMessage(
            clientNum: Int, from: netadr_t?, msg: idBitMsg, reply: idStr,
            OS: Int
        ): Boolean { // if from is null, clientNum is used for error messages
            var i: Int
            var numChecksums: Int
            val checksums = IntArray(FileSystem_h.MAX_PURE_PAKS)
            val serverChecksums = IntArray(FileSystem_h.MAX_PURE_PAKS)

            // pak checksums, in a 0-terminated list
            numChecksums = 0
            do {
                i = msg.ReadLong()
                checksums[numChecksums++] = i
                // just to make sure a broken client doesn't crash us
                if (numChecksums >= FileSystem_h.MAX_PURE_PAKS) {
                    Common.common.Warning(
                        "MAX_PURE_PAKS ( %d ) exceeded in idAsyncServer.ProcessPureMessage\n",
                        FileSystem_h.MAX_PURE_PAKS
                    )
                    reply.set("#str_07144")
                    return false
                }
            } while (i != 0)
            numChecksums--

            FileSystem_h.fileSystem.GetPureServerChecksums(serverChecksums, OS, null)
            assert(serverChecksums[0] != 0)

            // compare the lists
            i = 0
            while (serverChecksums[i] != 0) {
                if (checksums[i] != serverChecksums[i]) {
                    Common.common.DPrintf(
                        "client %s: pak missing ( 0x%x )\n",
                        if (from != null) win_net.Sys_NetAdrToString(from) else Str.va("%d", clientNum),
                        serverChecksums[i]
                    )
                    reply.set(String.format("pak missing ( 0x%x )\n", serverChecksums[i]))
                    return false
                }
                i++
            }
            if (checksums[i] != 0) {
                Common.common.DPrintf(
                    "client %s: extra pak file referenced ( 0x%x )\n",
                    if (from != null) win_net.Sys_NetAdrToString(from) else Str.va("%d", clientNum),
                    checksums[i]
                )
                reply.set(String.format("extra pak file referenced ( 0x%x )\n", checksums[i]))
                return false
            }
            return true
        }

        private fun SendReliableMessage(
            clientNum: Int,
            msg: idBitMsg
        ) {                // checks for overflow and disconnects the faulty client
            if (clientNum == localClientNum) {
                return
            }
            if (!clients[clientNum].channel.SendReliableMessage(msg)) {
                clients[clientNum].channel.ClearReliableMessages()
                DropClient(clientNum, "#str_07136")
            }
        }

        private fun UpdateTime(clamp: Int): Int {
            val time: Int
            val msec: Int
            time = win_shared.Sys_Milliseconds()
            msec = idMath.ClampInt(0, clamp, time - realTime)
            realTime = time
            serverTime += msec
            return msec
        }

        private fun SendEnterGameToClient(clientNum: Int) {
            val msg = idBitMsg()
            val msgBuf = ByteBuffer.allocate(MsgChannel.MAX_MESSAGE_SIZE)
            msg.Init(msgBuf, msgBuf.capacity())
            msg.WriteByte(SERVER_RELIABLE.SERVER_RELIABLE_MESSAGE_ENTERGAME.ordinal.toByte())
            SendReliableMessage(clientNum, msg)
        }

        @Throws(idException::class)
        private fun ProcessDownloadRequestMessage(from: netadr_t, msg: idBitMsg) {
            val challenge: Int
            val clientId: Short
            var iclient: Int
            var numPaks: Int
            var i: Int
            val dlGamePak: Int
            var dlPakChecksum: Int
            val dlSize = IntArray(FileSystem_h.MAX_PURE_PAKS) // sizes
            val pakNames = idStrList() // relative path
            val pakURLs = idStrList() // game URLs
            val pakbuf = CharArray(MAX_STRING_CHARS)
            val paklist = idStr()
            val msgBuf = ByteBuffer.allocate(MsgChannel.MAX_MESSAGE_SIZE)
            val tmpBuf = ByteBuffer.allocate(MsgChannel.MAX_MESSAGE_SIZE)
            val outMsg = idBitMsg()
            val tmpMsg = idBitMsg()
            val dlRequest: Int
            var voidSlots = 0 // to count and verbose the right number of paks requested for downloads
            challenge = msg.ReadLong()
            clientId = msg.ReadShort()
            dlRequest = msg.ReadLong()
            if (ValidateChallenge(from, challenge, clientId.toInt()).also { iclient = it } == -1) {
                return
            }
            if (challenges[iclient].authState != authState_t.CDK_PUREWAIT) {
                Common.common.DPrintf(
                    "client %s: got download request message, not in CDK_PUREWAIT\n",
                    win_net.Sys_NetAdrToString(from)
                )
                return
            }

            // the first token of the pak names list passed to the game will be empty if no game pak is requested
            dlGamePak = msg.ReadLong()
            if (dlGamePak != 0) {
                if (0 == FileSystem_h.fileSystem.ValidateDownloadPakForChecksum(dlGamePak, pakbuf, true)
                        .also { dlSize[0] = it }
                ) {
                    Common.common.Warning("client requested unknown game pak 0x%x", dlGamePak)
                    pakbuf[0] = '\u0000'
                    voidSlots++
                }
            } else {
                pakbuf[0] = '\u0000'
                voidSlots++
            }
            pakNames.add(idStr(pakbuf))
            numPaks = 1

            // read the checksums, build path names and pass that to the game code
            dlPakChecksum = msg.ReadLong()
            while (dlPakChecksum != 0) {
                if (0 == FileSystem_h.fileSystem.ValidateDownloadPakForChecksum(dlPakChecksum, pakbuf, false)
                        .also { dlSize[numPaks] = it }
                ) {
                    // we pass an empty token to the game so our list doesn't get offset
                    Common.common.Warning("client requested an unknown pak 0x%x", dlPakChecksum)
                    pakbuf[0] = '\u0000'
                    voidSlots++
                }
                pakNames.add(idStr(pakbuf))
                numPaks++
                dlPakChecksum = msg.ReadLong()
            }
            i = 0
            while (i < pakNames.size()) {
                if (i > 0) {
                    paklist.plusAssign(";")
                }
                paklist.plusAssign(pakNames[i].toString())
                i++
            }

            // read the message and pass it to the game code
            Common.common.DPrintf("got download request for %d paks - %s\n", numPaks - voidSlots, paklist.toString())
            outMsg.Init(msgBuf, msgBuf.capacity())
            outMsg.WriteShort(MsgChannel.CONNECTIONLESS_MESSAGE_ID.toShort())
            outMsg.WriteString("downloadInfo")
            outMsg.WriteLong(dlRequest)
            if (!Game_local.game.DownloadRequest(
                    win_net.Sys_NetAdrToString(from),
                    TempDump.ctos(challenges[iclient].guid),
                    paklist.toString(),
                    pakbuf
                )
            ) {
                Common.common.DPrintf("game: no downloads\n")
                outMsg.WriteByte(SERVER_DL.SERVER_DL_NONE.ordinal.toByte())
                serverPort.SendPacket(from, outMsg.GetData()!!, outMsg.GetSize())
                return
            }
            var token: String
            var type = 0
            var next: Int
            token = TempDump.ctos(pakbuf)
            next = token.indexOf(';')
            while (token.isNotEmpty()) {
                if (next != -1) {
                    pakbuf[next] = '\u0000'
                }
                if (type == 0) {
                    type = token.toInt()
                } else if (type == SERVER_DL.SERVER_DL_REDIRECT.ordinal) {
                    Common.common.DPrintf("download request: redirect to URL %s\n", token)
                    outMsg.WriteByte(SERVER_DL.SERVER_DL_REDIRECT.ordinal.toByte())
                    outMsg.WriteString(token)
                    serverPort.SendPacket(from, outMsg.GetData()!!, outMsg.GetSize())
                    return
                } else if (type == SERVER_DL.SERVER_DL_LIST.ordinal) {
                    pakURLs.add(idStr(token))
                } else {
                    Common.common.DPrintf("wrong op type %d\n", type)
                    next = -1
                }
                if (next != -1) {
                    token = token.substring(++next)
                    next = token.indexOf(';')
                } else {
                }
            }
            if (type == SERVER_DL.SERVER_DL_LIST.ordinal) {
                var totalDlSize = 0
                var numActualPaks = 0

                // put the answer packet together
                outMsg.WriteByte(SERVER_DL.SERVER_DL_LIST.ordinal.toByte())
                tmpMsg.Init(tmpBuf, MsgChannel.MAX_MESSAGE_SIZE)
                i = 0
                while (i < pakURLs.size()) {
                    tmpMsg.BeginWriting()
                    if (0 == dlSize[i] || 0 == pakURLs[i].Length()) {
                        // still send the relative path so the client knows what it missed
                        tmpMsg.WriteByte(SERVER_PAK.SERVER_PAK_NO.ordinal.toByte())
                        tmpMsg.WriteString(pakNames[i].toString())
                    } else {
                        totalDlSize += dlSize[i]
                        numActualPaks++
                        tmpMsg.WriteByte(SERVER_PAK.SERVER_PAK_YES.ordinal.toByte())
                        tmpMsg.WriteString(pakNames[i].toString())
                        tmpMsg.WriteString(pakURLs[i].toString())
                        tmpMsg.WriteLong(dlSize[i])
                    }

                    // keep last 5 bytes for an 'end of message' - SERVER_PAK_END and the totalDlSize long
                    if (outMsg.GetRemainingSpace() - tmpMsg.GetSize() > 5) {
                        outMsg.WriteData(tmpMsg.GetData()!!, tmpMsg.GetSize())
                    } else {
                        outMsg.WriteByte(SERVER_PAK.SERVER_PAK_END.ordinal.toByte())
                        break
                    }
                    i++
                }
                if (i == pakURLs.size()) {
                    // put a closure even if size not exceeded
                    outMsg.WriteByte(SERVER_PAK.SERVER_PAK_END.ordinal.toByte())
                }
                Common.common.DPrintf("download request: download %d paks, %d bytes\n", numActualPaks, totalDlSize)
                serverPort.SendPacket(from, outMsg.GetData()!!, outMsg.GetSize())
            }
        }

        companion object {
            //
            // track the max outgoing rate over the last few secs to watch for spikes
            // dependent on net_serverSnapshotDelay. 50ms, for a 3 seconds backlog -> 60 samples
            private const val stats_numsamples = 60
        }

        //
        //
        init {
            var i: Int
            active = false
            realTime = 0
            serverTime = 0
            serverId = 0
            serverDataChecksum = BigInteger.ZERO
            localClientNum = -1
            gameInitId = 0
            gameFrame = 0
            gameTime = 0
            gameTimeResidual = 0
            challenges = Array(MAX_CHALLENGES) { challenge_s() }
            i = 0
            while (i < AsyncNetwork.MAX_ASYNC_CLIENTS) {
                clients[i] = serverClient_s()
                ClearClient(i)
                for (j in 0 until AsyncNetwork.MAX_USERCMD_BACKUP) {
                    userCmds[j][i] = usercmd_t()
                }
                i++
            }
            serverReloadingEngine = false
            nextHeartbeatTime = 0
            nextAsyncStatsTime = 0
            noRconOutput = true
            lastAuthTime = 0

//            memset(stats_outrate, 0, sizeof(stats_outrate));
            stats_current = 0
            stats_average_sum = 0
            stats_max = 0
            stats_max_index = 0
            serverPort = idPort()
        }
    }

    /*
     ==================
     RConRedirect
     ==================
     */
    internal class RConRedirect : void_callback<String>() {
        @Throws(idException::class)
        override fun run(vararg objects: String) {
            idAsyncNetwork.server.RemoteConsoleOutput(objects[0])
        }

        companion object {
            private val instance: void_callback<String> = RConRedirect()
            fun getInstance(): void_callback<String> {
                return instance
            }
        }
    }
}
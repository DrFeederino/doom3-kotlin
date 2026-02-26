package neo.framework.Async

import neo.Game.Game.allowReply_t
import neo.Game.Game_local
import neo.Sound.snd_system
import neo.TempDump
import neo.framework.*
import neo.framework.Async.AsyncNetwork.*
import neo.framework.Async.MsgChannel.idMsgChannel
import neo.framework.Async.ServerScan.idServerScan
import neo.framework.Async.ServerScan.networkServer_t
import neo.framework.CmdSystem.cmdExecution_t
import neo.framework.FileSystem_h.backgroundDownload_s
import neo.framework.FileSystem_h.dlMime_t
import neo.framework.FileSystem_h.dlStatus_t
import neo.framework.FileSystem_h.dlType_t
import neo.framework.FileSystem_h.fsPureReply_t
import neo.framework.File_h.fsOrigin_t
import neo.framework.File_h.idFile
import neo.framework.File_h.idFile_Permanent
import neo.framework.Session.HandleGuiCommand_t
import neo.framework.Session.msgBoxType_t
import neo.framework.UsercmdGen.usercmd_t
import neo.idlib.BitMsg.idBitMsg
import neo.idlib.Dict_h.idDict
import neo.idlib.MAX_STRING_CHARS
import neo.idlib.Min
import neo.idlib.Text.Str
import neo.idlib.Text.Str.Measure_t
import neo.idlib.Text.Str.idStr
import neo.idlib.containers.CInt
import neo.idlib.containers.List.idList
import neo.idlib.containers.StrPool.idPoolStr
import neo.idlib.idException
import neo.idlib.idLib
import neo.idlib.math.INTSIGNBITSET
import neo.idlib.math.Random.idRandom
import neo.idlib.math.idMath
import neo.sys.sys_public
import neo.sys.sys_public.idPort
import neo.sys.sys_public.netadr_t
import neo.sys.sys_public.netadrtype_t
import neo.sys.win_net
import neo.sys.win_shared
import neo.ui.UserInterface
import neo.ui.UserInterface.idUserInterface
import java.math.BigInteger
import java.nio.ByteBuffer
import java.util.*

object AsyncClient {
    const val EMPTY_RESEND_TIME = 500
    const val PREDICTION_FAST_ADJUST = 4
    const val SETUP_CONNECTION_RESEND_TIME = 1000

    //
    internal enum class authBadKeyStatus_t {
        AUTHKEY_BAD_INVALID, AUTHKEY_BAD_BANNED, AUTHKEY_BAD_INUSE, AUTHKEY_BAD_MSG
    }

    internal enum class authKeyMsg_t {
        AUTHKEY_BADKEY, AUTHKEY_GUID
    }

    /*
     ===============================================================================

     Network Client for asynchronous networking.

     ===============================================================================
     */
    internal enum class clientState_t {
        CS_DISCONNECTED, CS_PURERESTART, CS_CHALLENGING, CS_CONNECTING, CS_CONNECTED, CS_INGAME
    }

    internal enum class clientUpdateState_t {
        UPDATE_NONE, UPDATE_SENT, UPDATE_READY, UPDATE_DLING, UPDATE_DONE
    }

    internal class pakDlEntry_t {
        var checksum = 0
        val filename: idStr = idStr()
        var size = 0
        val url: idStr = idStr()
    }

    class idAsyncClient {
        var serverList: idServerScan = idServerScan()

        //
        //
        private var active // true if client is active
                = false

        //
        private val backgroundDownload: backgroundDownload_s = backgroundDownload_s()

        //
        private val channel // message channel to server
                : idMsgChannel = idMsgChannel()
        private var clientDataChecksum // checksum of the data used by the client
                : BigInteger = BigInteger.ZERO
        private var clientId // client identification
                = 0
        private var clientNum // client number on server
                = 0
        private val clientPort // UDP port
                : idPort = idPort()
        private var clientPredictTime // prediction time used to send user commands
                = 0
        private var clientPrediction // how far the client predicts ahead
                = 0
        private var clientState // client state
                : clientState_t = clientState_t.CS_DISCONNECTED

        //
        private var clientTime // client local time
                = 0
        private var currentDlSize = 0
        private val dlChecksums: IntArray =
            IntArray(FileSystem_h.MAX_PURE_PAKS) // 0-terminated, first element is the game pak checksum or 0
        private var dlCount // total number of paks we request download for ( including the game pak )
                = 0
        private val dlList: idList<pakDlEntry_t> = idList() // list of paks to download, with url and name

        //
        private var dlRequest // randomized number to keep track of the requests
                = 0
        private var dlnow = 0
        private var dltotal = 0
        private var gameFrame // local game frame
                = 0

        //
        private var gameInitId // game initialization identification
                = 0
        private var gameTime // local game time
                = 0
        private var gameTimeResidual // left over time from previous frame
                = 0

        //
        private var guiNetMenu: idUserInterface? = null
        private var lastConnectTime // last time a connect message was sent
                = 0
        private var lastEmptyTime // last time an empty message was sent
                = 0

        //
        private var lastFrameDelta = 0
        private var lastPacketTime // last time a packet was received from the server
                = 0

        //
        private var lastRconAddress // last rcon address we emitted to
                : netadr_t = netadr_t()
        private var lastRconTime // when last rcon emitted
                = 0
        private var lastSnapshotTime // last time a snapshot was received
                = 0
        private var realTime // absolute time
                = 0

        //
        private var serverAddress // IP address of server
                : netadr_t = netadr_t()
        private var serverChallenge // challenge from server
                = 0
        private var serverId // server identification
                = 0
        private var serverMessageSequence // sequence number of last server message
                = 0
        private var showUpdateMessage = false
        private var snapshotGameFrame // game frame number of the last received snapshot
                = 0
        private var snapshotGameTime // game time of the last received snapshot
                = 0

        //
        private var snapshotSequence // sequence number of the last received snapshot
                = 0
        private var totalDlSize // for partial progress stuff
                = 0
        private var updateDirectDownload = false
        private val updateFallback: idStr = idStr()
        private val updateFile: idStr = idStr()
        private val updateMSG: idStr = idStr()
        private var updateMime: dlMime_t = dlMime_t.FILE_EXEC
        private var updateSentTime = 0

        //
        private var updateState: clientUpdateState_t = clientUpdateState_t.UPDATE_NONE
        private var updateURL: idStr = idStr()

        //
        private val userCmds: Array<Array<usercmd_t>> =
            Array(AsyncNetwork.MAX_USERCMD_BACKUP) { Array(AsyncNetwork.MAX_ASYNC_CLIENTS) { usercmd_t() } }

        fun Shutdown() {
            guiNetMenu = null
            updateMSG.Clear()
            updateURL.Clear()
            updateFile.Clear()
            updateFallback.Clear()
            backgroundDownload.url.url.Clear()
            dlList.Clear()
        }

        fun InitPort(): Boolean {
            // if this is the first time we connect to a server, open the UDP port
            if (0 == clientPort.GetPort()) {
                if (!clientPort.InitForPort(sys_public.PORT_ANY)) {
                    Common.common.Printf("Couldn't open client network port.\n")
                    return false
                }
            }
            // maintain it valid between connects and ui manager reloads
            guiNetMenu = UserInterface.uiManager.FindGui("guis/netmenu.gui", true, false, true)!!
            return true
        }

        fun ClosePort() {
            clientPort.Close()
        }

        fun ConnectToServer(adr: netadr_t) {
            // shutdown any current game. that includes network disconnect
            Session.session.Stop()
            if (!InitPort()) {
                return
            }
            if (CVarSystem.cvarSystem.GetCVarBool("net_serverDedicated")) {
                Common.common.Printf("Can't connect to a server as dedicated\n")
                return
            }

            // trash any currently pending packets
            ClearPendingPackets()
            serverAddress = adr

            // clear the client state
            Clear()

            // get a pseudo random client id, but don't use the id which is reserved for connectionless packets
            clientId = win_shared.Sys_Milliseconds() and MsgChannel.CONNECTIONLESS_MESSAGE_ID_MASK

            // calculate a checksum on some of the essential data used
            clientDataChecksum = DeclManager.declManager.GetChecksum()

            // start challenging the server
            clientState = clientState_t.CS_CHALLENGING
            active = true
            guiNetMenu = UserInterface.uiManager.FindGui("guis/netmenu.gui", true, false, true)!!
            guiNetMenu!!.SetStateString(
                "status",
                Str.va(Common.common.GetLanguageDict().GetString("#str_06749"), win_net.Sys_NetAdrToString(adr))
            )
            Session.session.SetGUI(guiNetMenu, HandleGuiCommand.getInstance())
        }

        @Throws(idException::class)
        fun ConnectToServer(address: String) {
            val serverNum: Int
            var adr = netadr_t()
            if (idStr.IsNumeric(address)) {
                serverNum = address.toInt()
                if (serverNum < 0 || serverNum >= serverList.Num()) {
                    Session.session.MessageBox(
                        msgBoxType_t.MSG_OK,
                        Str.va(Common.common.GetLanguageDict().GetString("#str_06733"), serverNum),
                        Common.common.GetLanguageDict().GetString("#str_06735"),
                        true
                    )
                    return
                }
                adr = serverList[serverNum].adr
            } else {
                if (!win_net.Sys_StringToNetAdr(address, adr, true)) {
                    Session.session.MessageBox(
                        msgBoxType_t.MSG_OK,
                        Str.va(Common.common.GetLanguageDict().GetString("#str_06734"), address),
                        Common.common.GetLanguageDict().GetString("#str_06735"),
                        true
                    )
                    return
                }
            }
            if (0 == adr.port) {
                adr.port = Licensee.PORT_SERVER
            }
            Common.common.Printf("\"%s\" resolved to %s\n", address, win_net.Sys_NetAdrToString(adr))
            ConnectToServer(adr)
        }

        fun Reconnect() {
            ConnectToServer(serverAddress)
        }

        fun DisconnectFromServer() {
            val msg = idBitMsg()
            val msgBuf = ByteBuffer.allocate(MsgChannel.MAX_MESSAGE_SIZE)
            if (clientState.ordinal >= clientState_t.CS_CONNECTED.ordinal) {
                // if we were actually connected, clear the pure list
                FileSystem_h.fileSystem.ClearPureChecksums()

                // send reliable disconnect to server
                msg.Init(msgBuf, msgBuf.capacity())
                msg.WriteByte(CLIENT_RELIABLE.CLIENT_RELIABLE_MESSAGE_DISCONNECT.ordinal.toByte())
                msg.WriteString("disconnect")
                if (!channel.SendReliableMessage(msg)) {
                    Common.common.Error("client.server reliable messages overflow\n")
                }
                SendEmptyToServer(true)
                SendEmptyToServer(true)
                SendEmptyToServer(true)
            }
            if (clientState != clientState_t.CS_PURERESTART) {
                channel.Shutdown()
                clientState = clientState_t.CS_DISCONNECTED
            }
            active = false
        }

        fun GetServerInfo(adr: netadr_t) {
            val msg = idBitMsg()
            val msgBuf = ByteBuffer.allocate(MsgChannel.MAX_MESSAGE_SIZE)
            if (!InitPort()) {
                return
            }
            msg.Init(msgBuf, msgBuf.capacity())
            msg.WriteShort(MsgChannel.CONNECTIONLESS_MESSAGE_ID.toShort())
            msg.WriteString("getInfo")
            msg.WriteLong(serverList.GetChallenge()) // challenge
            clientPort.SendPacket(adr, msg.GetData()!!, msg.GetSize())
        }

        fun GetServerInfo(address: String?) {
            var adr = netadr_t()
            if (address != null && !address.isEmpty()) {
                if (!win_net.Sys_StringToNetAdr(address, adr, true)) {
                    Common.common.Printf("Couldn't get server address for \"%s\"\n", address)
                    return
                }
            } else if (active) {
                adr = serverAddress
            } else if (idAsyncNetwork.server.IsActive()) {
                // used to be a Sys_StringToNetAdr( "localhost", &adr, true ); and send a packet over loopback
                // but this breaks with net_ip ( typically, for multi-homed servers )
                idAsyncNetwork.server.PrintLocalServerInfo()
                return
            } else {
                Common.common.Printf("no server found\n")
                return
            }
            if (0 == adr.port) {
                adr.port = Licensee.PORT_SERVER
            }
            GetServerInfo(adr)
        }

        fun GetLANServers() {
            var i: Int
            val msg = idBitMsg()
            val msgBuf = ByteBuffer.allocate(MsgChannel.MAX_MESSAGE_SIZE)
            val broadcastAddress = netadr_t()
            if (!InitPort()) {
                return
            }
            idAsyncNetwork.LANServer.SetBool(true)
            serverList.SetupLANScan()
            msg.Init(msgBuf, msgBuf.capacity())
            msg.WriteShort(MsgChannel.CONNECTIONLESS_MESSAGE_ID.toShort())
            msg.WriteString("getInfo")
            msg.WriteLong(serverList.GetChallenge())
            broadcastAddress.type = netadrtype_t.NA_BROADCAST
            i = 0
            while (i < AsyncNetwork.MAX_SERVER_PORTS) {
                broadcastAddress.port = (Licensee.PORT_SERVER + i)
                clientPort.SendPacket(broadcastAddress, msg.GetData()!!, msg.GetSize())
                i++
            }
        }

        fun GetNETServers() {
            val msg = idBitMsg()
            val msgBuf = ByteBuffer.allocate(MsgChannel.MAX_MESSAGE_SIZE)
            idAsyncNetwork.LANServer.SetBool(false)

            // NetScan only clears GUI and results, not the stored list
            serverList.Clear()
            serverList.NetScan()
            serverList.StartServers(true)
            msg.Init(msgBuf, msgBuf.capacity())
            msg.WriteShort(MsgChannel.CONNECTIONLESS_MESSAGE_ID.toShort())
            msg.WriteString("getServers")
            msg.WriteLong(AsyncNetwork.ASYNC_PROTOCOL_VERSION)
            msg.WriteString(CVarSystem.cvarSystem.GetCVarString("fs_game"))
            msg.WriteBits(CVarSystem.cvarSystem.GetCVarInteger("gui_filter_password"), 2)
            msg.WriteBits(CVarSystem.cvarSystem.GetCVarInteger("gui_filter_players"), 2)
            msg.WriteBits(CVarSystem.cvarSystem.GetCVarInteger("gui_filter_gameType"), 2)
            val adr = netadr_t()
            if (idAsyncNetwork.GetMasterAddress(0, adr)) {
                clientPort.SendPacket(adr, msg.GetData()!!, msg.GetSize())
            }
        }

        fun ListServers() {
            var i: Int
            i = 0
            while (i < serverList.Num()) {
                Common.common.Printf(
                    "%3d: %s %dms (%s)\n",
                    i,
                    serverList[i].serverInfo.GetString("si_name"),
                    serverList[i].ping,
                    win_net.Sys_NetAdrToString(serverList[i].adr)
                )
                i++
            }
        }

        fun ClearServers() {
            serverList.Clear()
        }

        fun RemoteConsole(command: String?) {
            var adr = netadr_t()
            val msg = idBitMsg()
            val msgBuf = ByteBuffer.allocate(MsgChannel.MAX_MESSAGE_SIZE)
            if (!InitPort()) {
                return
            }
            if (active) {
                adr = serverAddress
            } else {
                win_net.Sys_StringToNetAdr(idAsyncNetwork.clientRemoteConsoleAddress.GetString(), adr, true)
            }
            if (0 == adr.port) {
                adr.port = Licensee.PORT_SERVER
            }
            lastRconAddress = adr
            lastRconTime = realTime
            msg.Init(msgBuf, msgBuf.capacity())
            msg.WriteShort(MsgChannel.CONNECTIONLESS_MESSAGE_ID.toShort())
            msg.WriteString("rcon")
            msg.WriteString(idAsyncNetwork.clientRemoteConsolePassword.GetString())
            msg.WriteString(command)
            clientPort.SendPacket(adr, msg.GetData()!!, msg.GetSize())
        }

        fun IsPortInitialized(): Boolean {
            return clientPort.GetPort() != 0
        }

        fun IsActive(): Boolean {
            return active
        }

        fun GetLocalClientNum(): Int {
            return clientNum
        }

        fun GetPrediction(): Int {
            return if (clientState.ordinal < clientState_t.CS_CONNECTED.ordinal) {
                -1
            } else {
                clientPrediction
            }
        }

        fun GetTimeSinceLastPacket(): Int {
            return if (clientState.ordinal < clientState_t.CS_CONNECTED.ordinal) {
                -1
            } else {
                clientTime - lastPacketTime
            }
        }

        fun GetOutgoingRate(): Int {
            return if (clientState.ordinal < clientState_t.CS_CONNECTED.ordinal) {
                -1
            } else {
                channel.GetOutgoingRate()
            }
        }

        fun GetIncomingRate(): Int {
            return if (clientState.ordinal < clientState_t.CS_CONNECTED.ordinal) {
                -1
            } else {
                channel.GetIncomingRate()
            }
        }

        fun GetOutgoingCompression(): Float {
            return if (clientState.ordinal < clientState_t.CS_CONNECTED.ordinal) {
                0.0f
            } else {
                channel.GetOutgoingCompression()
            }
        }

        fun GetIncomingCompression(): Float {
            return if (clientState.ordinal < clientState_t.CS_CONNECTED.ordinal) {
                0.0f
            } else {
                channel.GetIncomingCompression()
            }
        }

        fun GetIncomingPacketLoss(): Float {
            return if (clientState.ordinal < clientState_t.CS_CONNECTED.ordinal) {
                0.0f
            } else {
                channel.GetIncomingPacketLoss()
            }
        }

        fun GetPredictedFrames(): Int {
            return lastFrameDelta
        }

        //
        fun RunFrame() {
            var msec: Int
            val size = CInt()
            var newPacket: Boolean
            val msg = idBitMsg()
            val msgBuf = ByteBuffer.allocate(MsgChannel.MAX_MESSAGE_SIZE)
            val from = netadr_t()
            msec = UpdateTime(100)
            if (0 == clientPort.GetPort()) {
                return
            }

            // handle ongoing pk4 downloads and patch downloads
            HandleDownloads()
            gameTimeResidual += msec

            // spin in place processing incoming packets until enough time lapsed to run a new game frame
            do {
                do {

                    // blocking read with game time residual timeout
                    newPacket = clientPort.GetPacketBlocking(
                        from,
                        msgBuf,
                        size,
                        msgBuf.capacity(),
                        UsercmdGen.USERCMD_MSEC - (gameTimeResidual + clientPredictTime) - 1
                    )
                    if (newPacket) {
                        msg.Init(msgBuf, msgBuf.capacity())
                        msg.SetSize(size._val)
                        msg.BeginReading()
                        ProcessMessage(from, msg)
                    }
                    msec = UpdateTime(100)
                    gameTimeResidual += msec
                } while (newPacket)
            } while (gameTimeResidual + clientPredictTime < UsercmdGen.USERCMD_MSEC)

            // update server list
            serverList.RunFrame()
            if (clientState == clientState_t.CS_DISCONNECTED) {
                UsercmdGen.usercmdGen.GetDirectUsercmd()
                gameTimeResidual = UsercmdGen.USERCMD_MSEC - 1
                clientPredictTime = 0
                return
            }
            if (clientState == clientState_t.CS_PURERESTART) {
                clientState = clientState_t.CS_DISCONNECTED
                Reconnect()
                gameTimeResidual = UsercmdGen.USERCMD_MSEC - 1
                clientPredictTime = 0
                return
            }

            // if not connected setup a connection
            if (clientState.ordinal < clientState_t.CS_CONNECTED.ordinal) {
                // also need to read mouse for the connecting guis
                UsercmdGen.usercmdGen.GetDirectUsercmd()
                SetupConnection()
                gameTimeResidual = UsercmdGen.USERCMD_MSEC - 1
                clientPredictTime = 0
                return
            }
            if (CheckTimeout()) {
                return
            }

            // if not yet in the game send empty messages to keep data flowing through the channel
            if (clientState.ordinal < clientState_t.CS_INGAME.ordinal) {
                Idle()
                gameTimeResidual = 0
                return
            }

            // check for user info changes
            if (CVarSystem.cvarSystem.GetModifiedFlags() and CVarSystem.CVAR_USERINFO != 0) {
                Game_local.game.ThrottleUserInfo()
                SendUserInfoToServer()
                Game_local.game.SetUserInfo(
                    clientNum,
                    Session.sessLocal.mapSpawnData.userInfo[clientNum],
                    true,
                    false
                )
                CVarSystem.cvarSystem.ClearModifiedFlags(CVarSystem.CVAR_USERINFO)
            }
            if (gameTimeResidual + clientPredictTime >= UsercmdGen.USERCMD_MSEC) {
                lastFrameDelta = 0
            }

            // generate user commands for the predicted time
            while (gameTimeResidual + clientPredictTime >= UsercmdGen.USERCMD_MSEC) {

                // send the user commands of this client to the server
                SendUsercmdsToServer()

                // update time
                gameFrame++
                gameTime += UsercmdGen.USERCMD_MSEC
                gameTimeResidual -= UsercmdGen.USERCMD_MSEC

                // run from the snapshot up to the local game frame
                while (snapshotGameFrame < gameFrame) {
                    lastFrameDelta++

                    // duplicate usercmds for clients if no new ones are available
                    DuplicateUsercmds(snapshotGameFrame, snapshotGameTime)

                    // indicate the last prediction frame before a render
                    val lastPredictFrame =
                        snapshotGameFrame + 1 >= gameFrame && gameTimeResidual + clientPredictTime < UsercmdGen.USERCMD_MSEC

                    // run client prediction
                    val ret = Game_local.game.ClientPrediction(
                        clientNum,
                        userCmds[snapshotGameFrame and AsyncNetwork.MAX_USERCMD_BACKUP - 1],
                        lastPredictFrame
                    )
                    idAsyncNetwork.ExecuteSessionCommand(ret.sessionCommand)
                    snapshotGameFrame++
                    snapshotGameTime += UsercmdGen.USERCMD_MSEC
                }
            }
        }

        fun SendReliableGameMessage(msg: idBitMsg) {
            val outMsg = idBitMsg()
            val msgBuf = ByteBuffer.allocate(MsgChannel.MAX_MESSAGE_SIZE)
            if (clientState.ordinal < clientState_t.CS_INGAME.ordinal) {
                return
            }
            outMsg.Init(msgBuf, msgBuf.capacity())
            outMsg.WriteByte(CLIENT_RELIABLE.CLIENT_RELIABLE_MESSAGE_GAME.ordinal.toByte())
            outMsg.WriteData(msg.GetData()!!, msg.GetSize())
            if (!channel.SendReliableMessage(outMsg)) {
                Common.common.Error("client->server reliable messages overflow\n")
            }
        }

        //

        fun SendVersionCheck(fromMenu: Boolean = false /*= false */) {
            val msg = idBitMsg()
            val msgBuf = ByteBuffer.allocate(MsgChannel.MAX_MESSAGE_SIZE)
            if (updateState != clientUpdateState_t.UPDATE_NONE && !fromMenu) {
                Common.common.DPrintf("up-to-date check was already performed\n")
                return
            }
            InitPort()
            msg.Init(msgBuf, msgBuf.capacity())
            msg.WriteShort(MsgChannel.CONNECTIONLESS_MESSAGE_ID.toShort())
            msg.WriteString("versionCheck")
            msg.WriteLong(AsyncNetwork.ASYNC_PROTOCOL_VERSION)
            msg.WriteShort(sys_public.BUILD_OS_ID.toShort())
            msg.WriteString(CVarSystem.cvarSystem.GetCVarString("si_version"))
            msg.WriteString(CVarSystem.cvarSystem.GetCVarString("com_guid"))
            clientPort.SendPacket(idAsyncNetwork.GetMasterAddress(), msg.GetData()!!, msg.GetSize())
            Common.common.DPrintf("sent a version check request\n")
            val sizePackage = CInt()
            sizePackage._val = MsgChannel.MAX_MESSAGE_SIZE
            updateState = clientUpdateState_t.UPDATE_SENT
            updateSentTime = clientTime
            showUpdateMessage = fromMenu

        }

        // pass NULL for the keys you don't care to auth for
        // returns false if internet link doesn't appear to be available
        fun SendAuthCheck(cdkey: String?, xpkey: String?): Boolean {
            val msg = idBitMsg()
            val msgBuf = ByteBuffer.allocate(MsgChannel.MAX_MESSAGE_SIZE)
            msg.Init(msgBuf, msgBuf.capacity())
            msg.WriteShort(MsgChannel.CONNECTIONLESS_MESSAGE_ID.toShort())
            msg.WriteString("gameAuth")
            msg.WriteLong(AsyncNetwork.ASYNC_PROTOCOL_VERSION)
            msg.WriteByte(if (cdkey != null) 1 else 0)
            msg.WriteString(cdkey ?: "")
            msg.WriteByte(if (xpkey != null) 1 else 0)
            msg.WriteString(xpkey ?: "")
            InitPort()
            clientPort.SendPacket(idAsyncNetwork.GetMasterAddress(), msg.GetData()!!, msg.GetSize())
            return true
        }

        //
        fun PacifierUpdate() {
            if (!IsActive()) {
                return
            }
            realTime = win_shared.Sys_Milliseconds()
            SendEmptyToServer(false, true)
        }

        private fun Clear() {
            var i: Int
            var j: Int
            active = false
            realTime = 0
            clientTime = 0
            clientId = 0
            clientDataChecksum = BigInteger.ZERO
            clientNum = 0
            clientState = clientState_t.CS_DISCONNECTED
            clientPrediction = 0
            clientPredictTime = 0
            serverId = 0
            serverChallenge = 0
            serverMessageSequence = 0
            lastConnectTime = -9999
            lastEmptyTime = -9999
            lastPacketTime = -9999
            lastSnapshotTime = -9999
            snapshotGameFrame = 0
            snapshotGameTime = 0
            snapshotSequence = 0
            gameInitId = AsyncNetwork.GAME_INIT_ID_INVALID
            gameFrame = 0
            gameTimeResidual = 0
            gameTime = 0
            //	memset( userCmds, 0, sizeof( userCmds ) );
            i = 0
            while (i < AsyncNetwork.MAX_USERCMD_BACKUP) {
                j = 0
                while (j < AsyncNetwork.MAX_ASYNC_CLIENTS) {
                    userCmds[i][j] = usercmd_t()
                    j++
                }
                i++
            }
            backgroundDownload.completed = true
            lastRconTime = 0
            showUpdateMessage = false
            lastFrameDelta = 0
            dlRequest = -1
            dlCount = -1
            //	memset( dlChecksums, 0, sizeof( int ) * MAX_PURE_PAKS );
            Arrays.fill(dlChecksums, 0)
            currentDlSize = 0
            totalDlSize = 0
        }

        private fun ClearPendingPackets() {
            val size = CInt()
            val msgBuf = ByteBuffer.allocate(MsgChannel.MAX_MESSAGE_SIZE)
            val from = netadr_t()
            while (clientPort.GetPacket(from, msgBuf, size, msgBuf.capacity()));
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
                idAsyncNetwork.DuplicateUsercmd(
                    userCmds[previousIndex][i],
                    userCmds[currentIndex][i],
                    frame,
                    time
                )
                i++
            }
        }

        private fun SendUserInfoToServer() {
            val msg = idBitMsg()
            val msgBuf = ByteBuffer.allocate(MsgChannel.MAX_MESSAGE_SIZE)
            val info: idDict = idDict()
            if (clientState.ordinal < clientState_t.CS_CONNECTED.ordinal) {
                return
            }
            info.set(CVarSystem.cvarSystem.MoveCVarsToDict(CVarSystem.CVAR_USERINFO))

            // send reliable client info to server
            msg.Init(msgBuf, msgBuf.capacity())
            msg.WriteByte(CLIENT_RELIABLE.CLIENT_RELIABLE_MESSAGE_CLIENTINFO.ordinal.toByte())
            msg.WriteDeltaDict(info, Session.sessLocal.mapSpawnData.userInfo[clientNum])
            if (!channel.SendReliableMessage(msg)) {
                Common.common.Error("client.server reliable messages overflow\n")
            }
            Session.sessLocal.mapSpawnData.userInfo[clientNum] = info
        }

        private fun SendEmptyToServer(force: Boolean = false /* = false*/, mapLoad: Boolean = false /*= false*/) {
            val msg = idBitMsg()
            val msgBuf = ByteBuffer.allocate(MsgChannel.MAX_MESSAGE_SIZE)
            if (lastEmptyTime > realTime) {
                lastEmptyTime = realTime
            }
            if (!force && realTime - lastEmptyTime < EMPTY_RESEND_TIME) {
                return
            }
            if (idAsyncNetwork.verbose.GetInteger() != 0) {
                Common.common.Printf(
                    "sending empty to server, gameInitId = %d\n",
                    if (mapLoad) AsyncNetwork.GAME_INIT_ID_MAP_LOAD else gameInitId
                )
            }
            msg.Init(msgBuf, msgBuf.capacity())
            msg.WriteLong(serverMessageSequence)
            msg.WriteLong(if (mapLoad) AsyncNetwork.GAME_INIT_ID_MAP_LOAD else gameInitId)
            msg.WriteLong(snapshotSequence)
            msg.WriteByte(CLIENT_UNRELIABLE.CLIENT_UNRELIABLE_MESSAGE_EMPTY.ordinal.toByte())
            channel.SendMessage(clientPort, clientTime, msg)
            while (channel.UnsentFragmentsLeft()) {
                channel.SendNextFragment(clientPort, clientTime)
            }
            lastEmptyTime = realTime
        }

        private fun SendPingResponseToServer(time: Int) {
            val msg = idBitMsg()
            val msgBuf = ByteBuffer.allocate(MsgChannel.MAX_MESSAGE_SIZE)
            if (idAsyncNetwork.verbose.GetInteger() == 2) {
                Common.common.Printf("sending ping response to server, gameInitId = %d\n", gameInitId)
            }
            msg.Init(msgBuf, msgBuf.capacity())
            msg.WriteLong(serverMessageSequence)
            msg.WriteLong(gameInitId)
            msg.WriteLong(snapshotSequence)
            msg.WriteByte(CLIENT_UNRELIABLE.CLIENT_UNRELIABLE_MESSAGE_PINGRESPONSE.ordinal.toByte())
            msg.WriteLong(time)
            channel.SendMessage(clientPort, clientTime, msg)
            while (channel.UnsentFragmentsLeft()) {
                channel.SendNextFragment(clientPort, clientTime)
            }
        }

        private fun SendUsercmdsToServer() {
            var i: Int
            val numUsercmds: Int
            var index: Int
            val msg = idBitMsg()
            val msgBuf = ByteBuffer.allocate(MsgChannel.MAX_MESSAGE_SIZE)
            var last: usercmd_t?
            if (idAsyncNetwork.verbose.GetInteger() == 2) {
                Common.common.Printf(
                    "sending usercmd to server: gameInitId = %d, gameFrame = %d, gameTime = %d\n",
                    gameInitId,
                    gameFrame,
                    gameTime
                )
            }

            // generate user command for this client
            index = gameFrame and AsyncNetwork.MAX_USERCMD_BACKUP - 1
            userCmds[index][clientNum] = UsercmdGen.usercmdGen.GetDirectUsercmd()
            userCmds[index][clientNum].gameFrame = gameFrame
            userCmds[index][clientNum].gameTime = gameTime

            // send the user commands to the server
            msg.Init(msgBuf, msgBuf.capacity())
            msg.WriteLong(serverMessageSequence)
            msg.WriteLong(gameInitId)
            msg.WriteLong(snapshotSequence)
            msg.WriteByte(CLIENT_UNRELIABLE.CLIENT_UNRELIABLE_MESSAGE_USERCMD.ordinal.toByte())
            msg.WriteShort(clientPrediction.toShort())
            numUsercmds = idMath.ClampInt(0, 10, idAsyncNetwork.clientUsercmdBackup.GetInteger()) + 1

            // write the user commands
            msg.WriteLong(gameFrame)
            msg.WriteByte(numUsercmds.toByte())
            last = null
            i = (gameFrame - numUsercmds + 1)
            while (i <= gameFrame) {
                index = i and AsyncNetwork.MAX_USERCMD_BACKUP - 1
                idAsyncNetwork.WriteUserCmdDelta(msg, userCmds[index][clientNum], last)
                last = userCmds[index][clientNum]
                i++
            }
            channel.SendMessage(clientPort, clientTime, msg)
            while (channel.UnsentFragmentsLeft()) {
                channel.SendNextFragment(clientPort, clientTime)
            }
        }

        private fun InitGame(serverGameInitId: Int, serverGameFrame: Int, serverGameTime: Int, serverSI: idDict) {
            gameInitId = serverGameInitId
            snapshotGameFrame = serverGameFrame
            gameFrame = snapshotGameFrame
            snapshotGameTime = serverGameTime
            gameTime = snapshotGameTime
            gameTimeResidual = 0
            //	memset( userCmds, 0, sizeof( userCmds ) );
            Arrays.fill(userCmds, 0)
            for (i in 0 until AsyncNetwork.MAX_ASYNC_CLIENTS) {
                Session.sessLocal.mapSpawnData.userInfo[i].Clear()
            }
            Session.sessLocal.mapSpawnData.serverInfo.set(serverSI)
        }

        @Throws(idException::class)
        private fun ProcessUnreliableServerMessage(msg: idBitMsg) {
            var i: Int
            var j: Int
            var index: Int
            val numDuplicatedUsercmds: Int
            val aheadOfServer: Int
            var numUsercmds: Int
            val delta: Int
            val serverGameInitId: Int
            val serverGameFrame: Int
            val serverGameTime: Int
            val serverSI = idDict()
            var last: usercmd_t?
            val pureWait: Boolean
            serverGameInitId = msg.ReadLong()
            if (msg.ReadByte() < SERVER_UNRELIABLE.values().size) {
                val id = SERVER_UNRELIABLE.values()[msg.ReadByte().toInt()]
                when (id) {
                    SERVER_UNRELIABLE.SERVER_UNRELIABLE_MESSAGE_EMPTY -> {
                        if (idAsyncNetwork.verbose.GetInteger() != 0) {
                            Common.common.Printf("received empty message from server\n")
                        }
                    }

                    SERVER_UNRELIABLE.SERVER_UNRELIABLE_MESSAGE_PING -> {
                        if (idAsyncNetwork.verbose.GetInteger() == 2) {
                            Common.common.Printf("received ping message from server\n")
                        }
                        SendPingResponseToServer(msg.ReadLong())
                    }

                    SERVER_UNRELIABLE.SERVER_UNRELIABLE_MESSAGE_GAMEINIT -> {
                        serverGameFrame = msg.ReadLong()
                        serverGameTime = msg.ReadLong()
                        msg.ReadDeltaDict(serverSI, null)
                        pureWait = serverSI.GetBool("si_pure")
                        InitGame(serverGameInitId, serverGameFrame, serverGameTime, serverSI)
                        channel.ResetRate()
                        if (idAsyncNetwork.verbose.GetInteger() != 0) {
                            Common.common.Printf(
                                "received gameinit, gameInitId = %d, gameFrame = %d, gameTime = %d\n",
                                gameInitId,
                                gameFrame,
                                gameTime
                            )
                        }

                        // mute sound
                        snd_system.soundSystem.SetMute(true)

                        // ensure chat icon goes away when the GUI is changed...
                        //cvarSystem.SetCVarBool( "ui_chat", false );
                        if (pureWait) {
                            guiNetMenu = UserInterface.uiManager.FindGui("guis/netmenu.gui", true, false, true)!!
                            Session.session.SetGUI(guiNetMenu, HandleGuiCommand.getInstance())
                            Session.session.MessageBox(
                                msgBoxType_t.MSG_ABORT,
                                Common.common.GetLanguageDict().GetString("#str_04317"),
                                Common.common.GetLanguageDict().GetString("#str_04318"),
                                false,
                                "pure_abort"
                            )
                        } else {
                            // load map
                            Session.session.SetGUI(null, null)
                            Session.sessLocal.ExecuteMapChange()
                        }
                    }

                    SERVER_UNRELIABLE.SERVER_UNRELIABLE_MESSAGE_SNAPSHOT -> {

                        // if the snapshot is from a different game
                        if (serverGameInitId != gameInitId) {
                            if (idAsyncNetwork.verbose.GetInteger() != 0) {
                                Common.common.Printf("ignoring snapshot with != gameInitId\n")
                            }
                            return
                        }
                        snapshotSequence = msg.ReadLong()
                        snapshotGameFrame = msg.ReadLong()
                        snapshotGameTime = msg.ReadLong()
                        numDuplicatedUsercmds = msg.ReadByte().toInt()
                        aheadOfServer = msg.ReadShort().toInt()

                        // read the game snapshot
                        Game_local.game.ClientReadSnapshot(
                            clientNum,
                            snapshotSequence,
                            snapshotGameFrame,
                            snapshotGameTime,
                            numDuplicatedUsercmds,
                            aheadOfServer,
                            msg
                        )

                        // read user commands of other clients from the snapshot
                        last = null
                        i = msg.ReadByte().toInt()
                        while (i < AsyncNetwork.MAX_ASYNC_CLIENTS) {
                            numUsercmds = msg.ReadByte().toInt()
                            if (numUsercmds > AsyncNetwork.MAX_USERCMD_RELAY) {
                                Common.common.Error(
                                    "snapshot %d contains too many user commands for client %d",
                                    snapshotSequence,
                                    i
                                )
                                break
                            }
                            j = 0
                            while (j < numUsercmds) {
                                index = snapshotGameFrame + j and AsyncNetwork.MAX_USERCMD_BACKUP - 1
                                idAsyncNetwork.ReadUserCmdDelta(msg, userCmds[index][i], last)
                                userCmds[index][i].gameFrame = snapshotGameFrame + j
                                userCmds[index][i].duplicateCount = 0
                                last = userCmds[index][i]
                                j++
                            }
                            // clear all user commands after the ones just read from the snapshot
                            j = numUsercmds
                            while (j < AsyncNetwork.MAX_USERCMD_BACKUP) {
                                index = snapshotGameFrame + j and AsyncNetwork.MAX_USERCMD_BACKUP - 1
                                userCmds[index][i].gameFrame = 0
                                userCmds[index][i].gameTime = 0
                                j++
                            }
                            i = msg.ReadByte().toInt()
                        }

                        // if this is the first snapshot after a game init was received
                        if (clientState == clientState_t.CS_CONNECTED) {
                            gameTimeResidual = 0
                            clientState = clientState_t.CS_INGAME
                            assert(Session.sessLocal.GetActiveMenu() == null)
                            if (idAsyncNetwork.verbose.GetInteger() != 0) {
                                Common.common.Printf(
                                    "received first snapshot, gameInitId = %d, gameFrame %d gameTime %d\n",
                                    gameInitId,
                                    snapshotGameFrame,
                                    snapshotGameTime
                                )
                            }
                        }

                        // if the snapshot is newer than the clients current game time
                        if (gameTime < snapshotGameTime || gameTime > snapshotGameTime + idAsyncNetwork.clientMaxPrediction.GetInteger()) {
                            gameFrame = snapshotGameFrame
                            gameTime = snapshotGameTime
                            gameTimeResidual = idMath.ClampInt(
                                -idAsyncNetwork.clientMaxPrediction.GetInteger(),
                                idAsyncNetwork.clientMaxPrediction.GetInteger(),
                                gameTimeResidual
                            )
                            clientPredictTime = idMath.ClampInt(
                                -idAsyncNetwork.clientMaxPrediction.GetInteger(),
                                idAsyncNetwork.clientMaxPrediction.GetInteger(),
                                clientPredictTime
                            )
                        }

                        // adjust the client prediction time based on the snapshot time
                        clientPrediction -= 1 - (INTSIGNBITSET(aheadOfServer - idAsyncNetwork.clientPrediction.GetInteger()) shl 1)
                        clientPrediction = idMath.ClampInt(
                            idAsyncNetwork.clientPrediction.GetInteger(),
                            idAsyncNetwork.clientMaxPrediction.GetInteger(),
                            clientPrediction
                        )
                        delta = gameTime - (snapshotGameTime + clientPrediction)
                        clientPredictTime -= delta / PREDICTION_FAST_ADJUST + (1 - (INTSIGNBITSET(
                            delta
                        ) shl 1))
                        lastSnapshotTime = clientTime
                        if (idAsyncNetwork.verbose.GetInteger() == 2) {
                            Common.common.Printf(
                                "received snapshot, gameInitId = %d, gameFrame = %d, gameTime = %d\n",
                                gameInitId,
                                gameFrame,
                                gameTime
                            )
                        }
                        if (numDuplicatedUsercmds != 0 && idAsyncNetwork.verbose.GetInteger() == 2) {
                            Common.common.Printf(
                                "server duplicated %d user commands before snapshot %d\n",
                                numDuplicatedUsercmds,
                                snapshotGameFrame
                            )
                        }
                    }
                }
            } else {
//		default: {
                Common.common.Printf("unknown unreliable server message %d\n", msg.ReadByte())
                //			break;
            }
        }

        @Throws(idException::class)
        private fun ProcessReliableServerMessages() {
            val msg = idBitMsg()
            val msgBuf = ByteBuffer.allocate(MsgChannel.MAX_MESSAGE_SIZE)
            var id: SERVER_RELIABLE
            msg.Init(msgBuf, msgBuf.capacity())
            while (channel.GetReliableMessage(msg)) {
                if (msg.ReadByte() < SERVER_RELIABLE.values().size) {
                    id = SERVER_RELIABLE.values()[msg.ReadByte().toInt()]
                    when (id) {
                        SERVER_RELIABLE.SERVER_RELIABLE_MESSAGE_CLIENTINFO -> {
                            var clientNum: Int
                            clientNum = msg.ReadByte().toInt()
                            val info = idDict(Session.sessLocal.mapSpawnData.userInfo[clientNum])
                            val haveBase = msg.ReadBits(1) != 0
                            if (BuildDefines.ID_CLIENTINFO_TAGS) {
                                val checksum = info.Checksum()
                                val srv_checksum = msg.ReadLong()
                                if (checksum != srv_checksum.toLong()) {
                                    Common.common.DPrintf(
                                        "SERVER_RELIABLE_MESSAGE_CLIENTINFO %d (haveBase: %s): != checksums srv: 0x%x local: 0x%x\n",
                                        clientNum,
                                        if (haveBase) "true" else "false",
                                        checksum,
                                        srv_checksum
                                    )
                                    info.Print()
                                } else {
                                    Common.common.DPrintf(
                                        "SERVER_RELIABLE_MESSAGE_CLIENTINFO %d (haveBase: %s): checksums ok 0x%x\n",
                                        clientNum,
                                        if (haveBase) "true" else "false",
                                        checksum
                                    )
                                }
                            }
                            if (haveBase) {
                                msg.ReadDeltaDict(info, info)
                            } else {
                                msg.ReadDeltaDict(info, null)
                            }

                            // server forces us to a different userinfo
                            if (clientNum == this.clientNum) {
                                Common.common.DPrintf("local user info modified by server\n")
                                CVarSystem.cvarSystem.SetCVarsFromDict(info)
                                CVarSystem.cvarSystem.ClearModifiedFlags(CVarSystem.CVAR_USERINFO) // don't emit back
                            }
                            Game_local.game.SetUserInfo(clientNum, info, true, false)
                        }

                        SERVER_RELIABLE.SERVER_RELIABLE_MESSAGE_SYNCEDCVARS -> {
                            val info: idDict = Session.sessLocal.mapSpawnData.syncedCVars
                            msg.ReadDeltaDict(info, info)
                            CVarSystem.cvarSystem.SetCVarsFromDict(info)
                            if (!idAsyncNetwork.allowCheats.GetBool()) {
                                CVarSystem.cvarSystem.ResetFlaggedVariables(CVarSystem.CVAR_CHEAT)
                            }
                        }

                        SERVER_RELIABLE.SERVER_RELIABLE_MESSAGE_PRINT -> {
                            val string = CharArray(MAX_STRING_CHARS)
                            msg.ReadString(string, MAX_STRING_CHARS)
                            Common.common.Printf("%s\n", string)
                        }

                        SERVER_RELIABLE.SERVER_RELIABLE_MESSAGE_DISCONNECT -> {
                            var clientNum: Int
                            val string = CharArray(MAX_STRING_CHARS)
                            clientNum = msg.ReadLong()
                            ReadLocalizedServerString(msg, string, MAX_STRING_CHARS)
                            if (clientNum == this.clientNum) {
                                Session.session.Stop()
                                Session.session.MessageBox(
                                    msgBoxType_t.MSG_OK,
                                    TempDump.ctos(string),
                                    Common.common.GetLanguageDict().GetString("#str_04319"),
                                    true
                                )
                                Session.session.StartMenu()
                            } else {
                                Common.common.Printf("client %d %s\n", clientNum, string)
                                CmdSystem.cmdSystem.BufferCommandText(
                                    cmdExecution_t.CMD_EXEC_NOW,
                                    Str.va(
                                        "addChatLine \"%s^0 %s\"",
                                        Session.sessLocal.mapSpawnData.userInfo[clientNum]
                                            .GetString("ui_name"),
                                        string
                                    )
                                )
                                Session.sessLocal.mapSpawnData.userInfo[clientNum].Clear()
                            }
                        }

                        SERVER_RELIABLE.SERVER_RELIABLE_MESSAGE_APPLYSNAPSHOT -> {
                            var sequence: Int
                            sequence = msg.ReadLong()
                            if (!Game_local.game.ClientApplySnapshot(clientNum, sequence)) {
                                Session.session.Stop()
                                Common.common.Error("couldn't apply snapshot %d", sequence)
                            }
                        }

                        SERVER_RELIABLE.SERVER_RELIABLE_MESSAGE_PURE -> {
                            ProcessReliableMessagePure(msg)
                        }

                        SERVER_RELIABLE.SERVER_RELIABLE_MESSAGE_RELOAD -> {
                            if (idAsyncNetwork.verbose.GetBool()) {
                                Common.common.Printf("got MESSAGE_RELOAD from server\n")
                            }
                            // simply reconnect, so that if the server restarts in pure mode we can get the right list and avoid spurious reloads
                            CmdSystem.cmdSystem.BufferCommandText(cmdExecution_t.CMD_EXEC_APPEND, "reconnect\n")
                        }

                        SERVER_RELIABLE.SERVER_RELIABLE_MESSAGE_ENTERGAME -> {
                            SendUserInfoToServer()
                            Game_local.game.SetUserInfo(
                                clientNum,
                                Session.sessLocal.mapSpawnData.userInfo[clientNum],
                                true,
                                false
                            )
                            CVarSystem.cvarSystem.ClearModifiedFlags(CVarSystem.CVAR_USERINFO)
                        }

                        else -> {}
                    }
                } else {
//			default: {
                    // pass reliable message on to game code
                    Game_local.game.ClientProcessReliableMessage(clientNum, msg)
                    //				break;
//			}
                }
            }
        }

        @Throws(idException::class)
        private fun ProcessChallengeResponseMessage(from: netadr_t, msg: idBitMsg) {
            val serverGame = CharArray(MAX_STRING_CHARS)
            val serverGameBase = CharArray(MAX_STRING_CHARS)
            val serverGameStr: String
            val serverGameBaseStr: String
            if (clientState != clientState_t.CS_CHALLENGING) {
                Common.common.Printf("Unwanted challenge response received.\n")
                return
            }
            serverChallenge = msg.ReadLong()
            serverId = msg.ReadShort().toInt()
            msg.ReadString(serverGameBase, MAX_STRING_CHARS)
            msg.ReadString(serverGame, MAX_STRING_CHARS)
            serverGameStr = TempDump.ctos(serverGame)
            serverGameBaseStr = TempDump.ctos(serverGameBase)

            // the server is running a different game... we need to reload in the correct fs_game
            // even pure pak checks would fail if we didn't, as there are files we may not even see atm
            // NOTE: we could read the pure list from the server at the same time and set it up for the restart
            // ( if the client can restart directly with the right pak order, then we avoid an extra reloadEngine later.. )
            if (idStr.Icmp(CVarSystem.cvarSystem.GetCVarString("fs_game_base"), serverGameBaseStr) != 0
                || idStr.Icmp(CVarSystem.cvarSystem.GetCVarString("fs_game"), serverGameStr) != 0
            ) {
                // bug #189 - if the server is running ROE and ROE is not locally installed, refuse to connect or we might crash
                if (!FileSystem_h.fileSystem.HasD3XP() && (0 == idStr.Icmp(
                        serverGameBaseStr,
                        "d3xp"
                    ) || 0 == idStr.Icmp(serverGameStr, "d3xp"))
                ) {
                    Common.common.Printf("The server is running Doom3: Resurrection of Evil expansion pack. RoE is not installed on this client. Aborting the connection..\n")
                    CmdSystem.cmdSystem.BufferCommandText(cmdExecution_t.CMD_EXEC_APPEND, "disconnect\n")
                    return
                }
                Common.common.Printf(
                    "The server is running a different mod (%s-%s). Restarting..\n",
                    serverGameBaseStr,
                    serverGameStr
                )
                CVarSystem.cvarSystem.SetCVarString("fs_game_base", serverGameBaseStr)
                CVarSystem.cvarSystem.SetCVarString("fs_game", serverGameStr)
                CmdSystem.cmdSystem.BufferCommandText(cmdExecution_t.CMD_EXEC_NOW, "reloadEngine")
                CmdSystem.cmdSystem.BufferCommandText(cmdExecution_t.CMD_EXEC_APPEND, "reconnect\n")
                return
            }
            Common.common.Printf(
                "received challenge response 0x%x from %s\n",
                serverChallenge,
                win_net.Sys_NetAdrToString(from)
            )

            // start sending connect packets instead of challenge request packets
            clientState = clientState_t.CS_CONNECTING
            lastConnectTime = -9999

            // take this address as the new server address.  This allows
            // a server proxy to hand off connections to multiple servers
            serverAddress = from
        }

        private fun ProcessConnectResponseMessage(from: netadr_t, msg: idBitMsg) {
            val serverGameInitId: Int
            val serverGameFrame: Int
            val serverGameTime: Int
            val serverSI = idDict()
            if (clientState.ordinal >= clientState_t.CS_CONNECTED.ordinal) {
                Common.common.Printf("Duplicate connect received.\n")
                return
            }
            if (clientState != clientState_t.CS_CONNECTING) {
                Common.common.Printf("Connect response packet while not connecting.\n")
                return
            }
            if (!win_net.Sys_CompareNetAdrBase(from, serverAddress)) {
                Common.common.Printf("Connect response from a different server.\n")
                Common.common.Printf(
                    "%s should have been %s\n",
                    win_net.Sys_NetAdrToString(from),
                    win_net.Sys_NetAdrToString(serverAddress)
                )
                return
            }
            Common.common.Printf("received connect response from %s\n", win_net.Sys_NetAdrToString(from))
            channel.Init(from, clientId)
            clientNum = msg.ReadLong()
            clientState = clientState_t.CS_CONNECTED
            lastPacketTime = -9999
            serverGameInitId = msg.ReadLong()
            serverGameFrame = msg.ReadLong()
            serverGameTime = msg.ReadLong()
            msg.ReadDeltaDict(serverSI, null)
            InitGame(serverGameInitId, serverGameFrame, serverGameTime, serverSI)

            // load map
            Session.session.SetGUI(null, null)
            Session.sessLocal.ExecuteMapChange()
            clientPrediction =
                idMath.ClampInt(0, idAsyncNetwork.clientMaxPrediction.GetInteger(), clientTime - lastConnectTime)
            clientPredictTime = clientPrediction
        }

        private fun ProcessDisconnectMessage(from: netadr_t, msg: idBitMsg) {
            if (clientState == clientState_t.CS_DISCONNECTED) {
                Common.common.Printf("Disconnect packet while not connected.\n")
                return
            }
            if (!win_net.Sys_CompareNetAdrBase(from, serverAddress)) {
                Common.common.Printf("Disconnect packet from unknown server.\n")
                return
            }
            Session.session.Stop()
            Session.session.MessageBox(
                msgBoxType_t.MSG_OK,
                Common.common.GetLanguageDict().GetString("#str_04320"),
                null,
                true
            )
            Session.session.StartMenu()
        }

        @Throws(idException::class)
        private fun ProcessInfoResponseMessage(from: netadr_t, msg: idBitMsg) {
            var i: Int
            val protocol: Int
            val index: Int
            val serverInfo = networkServer_t()
            val verbose = from.type == netadrtype_t.NA_LOOPBACK || CVarSystem.cvarSystem.GetCVarBool("developer")
            serverInfo.clients = 0
            serverInfo.adr = from
            serverInfo.challenge = msg.ReadLong() // challenge
            protocol = msg.ReadLong()
            if (protocol != AsyncNetwork.ASYNC_PROTOCOL_VERSION) {
                Common.common.Printf(
                    "server %s ignored - protocol %d.%d, expected %d.%d\n",
                    win_net.Sys_NetAdrToString(serverInfo.adr),
                    protocol shr 16,
                    protocol and 0xffff,
                    Licensee.ASYNC_PROTOCOL_MAJOR,
                    AsyncNetwork.ASYNC_PROTOCOL_MINOR
                )
                return
            }
            msg.ReadDeltaDict(serverInfo.serverInfo, null)
            if (verbose) {
                Common.common.Printf("server IP = %s\n", win_net.Sys_NetAdrToString(serverInfo.adr))
                serverInfo.serverInfo.Print()
            }
            i = msg.ReadByte().toInt()
            while (i < AsyncNetwork.MAX_ASYNC_CLIENTS) {
                serverInfo.pings[serverInfo.clients] = msg.ReadShort()
                serverInfo.rate[serverInfo.clients] = msg.ReadLong()
                msg.ReadString(serverInfo.nickname[serverInfo.clients], AsyncNetwork.MAX_NICKLEN)
                if (verbose) {
                    Common.common.Printf(
                        "client %2d: %s, ping = %d, rate = %d\n",
                        i,
                        serverInfo.nickname[serverInfo.clients],
                        serverInfo.pings[serverInfo.clients],
                        serverInfo.rate[serverInfo.clients]
                    )
                }
                serverInfo.clients++
                i = msg.ReadByte().toInt()
            }
            serverInfo.OSMask = msg.ReadLong()
            index = if (serverList.InfoResponse(serverInfo) != 0) 1 else 0
            Common.common.Printf(
                "%d: server %s - protocol %d.%d - %s\n",
                index,
                win_net.Sys_NetAdrToString(serverInfo.adr),
                protocol shr 16,
                protocol and 0xffff,
                serverInfo.serverInfo.GetString("si_name")
            )
        }

        @Throws(idException::class)
        private fun ProcessPrintMessage(from: netadr_t, msg: idBitMsg) {
            val str = CharArray(MAX_STRING_CHARS)
            val opcode: Int
            var game_opcode = allowReply_t.ALLOW_YES.ordinal
            val retpass: String
            val string: String
            opcode = msg.ReadLong()
            if (opcode == SERVER_PRINT.SERVER_PRINT_GAMEDENY.ordinal) {
                game_opcode = msg.ReadLong()
            }
            ReadLocalizedServerString(msg, str, MAX_STRING_CHARS)
            string = TempDump.ctos(str)
            Common.common.Printf("%s\n", string)
            guiNetMenu!!.SetStateString("status", string)
            if (opcode == SERVER_PRINT.SERVER_PRINT_GAMEDENY.ordinal) {
                if (game_opcode == allowReply_t.ALLOW_BADPASS.ordinal) {
                    retpass = Session.session.MessageBox(
                        msgBoxType_t.MSG_PROMPT,
                        Common.common.GetLanguageDict().GetString("#str_04321"),
                        string,
                        true,
                        "passprompt_ok"
                    )
                    ClearPendingPackets()
                    guiNetMenu!!.SetStateString("status", Common.common.GetLanguageDict().GetString("#str_04322"))
                    if (retpass != null) {
                        // #790
                        CVarSystem.cvarSystem.SetCVarString("password", "")
                        CVarSystem.cvarSystem.SetCVarString("password", retpass)
                    } else {
                        CmdSystem.cmdSystem.BufferCommandText(cmdExecution_t.CMD_EXEC_NOW, "disconnect")
                    }
                } else if (game_opcode == allowReply_t.ALLOW_NO.ordinal) {
                    Session.session.MessageBox(
                        msgBoxType_t.MSG_OK,
                        string,
                        Common.common.GetLanguageDict().GetString("#str_04323"),
                        true
                    )
                    ClearPendingPackets()
                    CmdSystem.cmdSystem.BufferCommandText(cmdExecution_t.CMD_EXEC_NOW, "disconnect")
                }
                // ALLOW_NOTYET just keeps running as usual. The GUI has an abort button
            } else if (opcode == SERVER_PRINT.SERVER_PRINT_BADCHALLENGE.ordinal && clientState.ordinal >= clientState_t.CS_CONNECTING.ordinal) {
                CmdSystem.cmdSystem.BufferCommandText(cmdExecution_t.CMD_EXEC_NOW, "reconnect")
            }
        }

        private fun ProcessServersListMessage(from: netadr_t, msg: idBitMsg) {
            if (!win_net.Sys_CompareNetAdrBase(idAsyncNetwork.GetMasterAddress(), from)) {
                Common.common.DPrintf(
                    "received a server list from %s - not a valid master\n",
                    win_net.Sys_NetAdrToString(from)
                )
                return
            }
            while (msg.GetRemaingData() != 0) {
                var a: Int
                var b: Int
                var c: Int
                var d: Int
                a = msg.ReadByte().toInt()
                b = msg.ReadByte().toInt()
                c = msg.ReadByte().toInt()
                d = msg.ReadByte().toInt()
                serverList.AddServer(serverList.Num(), Str.va("%d.%d.%d.%d:%d", a, b, c, d, msg.ReadShort()))
            }
        }

        @Throws(idException::class)
        private fun ProcessAuthKeyMessage(from: netadr_t, msg: idBitMsg) {
            val authMsg: authKeyMsg_t
            val read_string = CharArray(MAX_STRING_CHARS)
            var retkey: String
            val authBadStatus: authBadKeyStatus_t
            val key_index: Int
            val valid = BooleanArray(2)
            var auth_msg: String = ""
            val auth_msg2 = idStr()
            if (clientState != clientState_t.CS_CONNECTING && !Session.session.WaitingForGameAuth()) {
                Common.common.Printf("clientState != CS_CONNECTING, not waiting for game auth, authKey ignored\n")
                return
            }
            authMsg = authKeyMsg_t.values()[msg.ReadByte().toInt()] //TODO:out of bounds check.
            if (authMsg == authKeyMsg_t.AUTHKEY_BADKEY) {
                valid[1] = true
                valid[0] = valid[1]
                //                key_index = 0;
                authBadStatus = authBadKeyStatus_t.values()[msg.ReadByte().toInt()]
                when (authBadStatus) {
                    authBadKeyStatus_t.AUTHKEY_BAD_INVALID -> {
                        valid[0] = msg.ReadByte().toInt() == 1
                        valid[1] = msg.ReadByte().toInt() == 1
                        idAsyncNetwork.BuildInvalidKeyMsg(auth_msg2, valid)
                        auth_msg = auth_msg2.toString()
                    }

                    authBadKeyStatus_t.AUTHKEY_BAD_BANNED -> {
                        key_index = msg.ReadByte().toInt()
                        auth_msg = Common.common.GetLanguageDict().GetString(Str.va("#str_0719%1d", 6 + key_index))
                        auth_msg += "\n"
                        auth_msg += Common.common.GetLanguageDict().GetString("#str_04304")
                        valid[key_index] = false
                    }

                    authBadKeyStatus_t.AUTHKEY_BAD_INUSE -> {
                        key_index = msg.ReadByte().toInt()
                        auth_msg = Common.common.GetLanguageDict().GetString(Str.va("#str_0719%1d", 8 + key_index))
                        auth_msg += "\n"
                        auth_msg += Common.common.GetLanguageDict().GetString("#str_04304")
                        valid[key_index] = false
                    }

                    authBadKeyStatus_t.AUTHKEY_BAD_MSG -> {
                        // a general message explaining why this key is denied
                        // no specific use for this atm. let's not clear the keys either
                        msg.ReadString(read_string, MAX_STRING_CHARS)
                        auth_msg = TempDump.ctos(read_string)
                    }
                }
                Common.common.DPrintf("auth deny: %s\n", auth_msg)

                // keys to be cleared. applies to both net connect and game auth
                Session.session.ClearCDKey(valid)

                // get rid of the bad key - at least that's gonna annoy people who stole a fake key
                if (clientState == clientState_t.CS_CONNECTING) {
                    while (true) {
                        // here we use the auth status message
                        retkey = Session.session.MessageBox(
                            msgBoxType_t.MSG_CDKEY,
                            auth_msg,
                            Common.common.GetLanguageDict().GetString("#str_04325"),
                            true
                        )
                        if (retkey.isNotEmpty()) {
                            if (Session.session.CheckKey(retkey, true, valid)) {
                                CmdSystem.cmdSystem.BufferCommandText(cmdExecution_t.CMD_EXEC_NOW, "reconnect")
                            } else {
                                // build a more precise message about the offline check failure
                                idAsyncNetwork.BuildInvalidKeyMsg(auth_msg2, valid)
                                auth_msg = auth_msg2.toString()
                                Session.session.MessageBox(
                                    msgBoxType_t.MSG_OK,
                                    auth_msg,
                                    Common.common.GetLanguageDict().GetString("#str_04327"),
                                    true
                                )
                                continue
                            }
                        } else {
                            CmdSystem.cmdSystem.BufferCommandText(cmdExecution_t.CMD_EXEC_NOW, "disconnect")
                        }
                        break
                    }
                } else {
                    // forward the auth status information to the session code
                    Session.session.CDKeysAuthReply(false, auth_msg)
                }
            } else {
                msg.ReadString(read_string, MAX_STRING_CHARS)
                CVarSystem.cvarSystem.SetCVarString("com_guid", TempDump.ctos(read_string))
                Common.common.Printf("guid set to %s\n", read_string)
                Session.session.CDKeysAuthReply(true, null)
            }
        }

        private fun ProcessVersionMessage(from: netadr_t, msg: idBitMsg) {
            val string = CharArray(MAX_STRING_CHARS)
            if (updateState != clientUpdateState_t.UPDATE_SENT) {
                Common.common.Printf("ProcessVersionMessage: version reply, != UPDATE_SENT\n")
                return
            }
            Common.common.Printf("A new version is available\n")
            msg.ReadString(string, MAX_STRING_CHARS)
            updateMSG.set(string)
            updateDirectDownload = msg.ReadByte().toInt() != 0
            msg.ReadString(string, MAX_STRING_CHARS)
            updateURL = idStr(string)
            updateMime = dlMime_t.values()[msg.ReadByte().toInt()]
            msg.ReadString(string, MAX_STRING_CHARS)
            updateFallback.set(string)
            updateState = clientUpdateState_t.UPDATE_READY
        }

        @Throws(idException::class)
        private fun ConnectionlessMessage(from: netadr_t, msg: idBitMsg) {
            val str =
                CharArray(MAX_STRING_CHARS * 2) // M. Quinn - Even Balance - PB packets can go beyond 1024
            val string: String
            msg.ReadString(str, str.size)
            string = TempDump.ctos(str)

            // info response from a server, are accepted from any source
            if (idStr.Icmp(string, "infoResponse") == 0) {
                ProcessInfoResponseMessage(from, msg)
                return
            }

            // from master server:
            if (win_net.Sys_CompareNetAdrBase(from, idAsyncNetwork.GetMasterAddress())) {
                // server list
                if (idStr.Icmp(string, "servers") == 0) {
                    ProcessServersListMessage(from, msg)
                    return
                }
                if (idStr.Icmp(string, "authKey") == 0) {
                    ProcessAuthKeyMessage(from, msg)
                    return
                }
                if (idStr.Icmp(string, "newVersion") == 0) {
                    ProcessVersionMessage(from, msg)
                    return
                }
            }

            // ignore if not from the current/last server
            if (!win_net.Sys_CompareNetAdrBase(
                    from,
                    serverAddress
                ) && (lastRconTime + 10000 < realTime || !win_net.Sys_CompareNetAdrBase(from, lastRconAddress))
            ) {
                Common.common.DPrintf(
                    "got message '%s' from bad source: %s\n",
                    string,
                    win_net.Sys_NetAdrToString(from)
                )
                return
            }

            // challenge response from the server we are connecting to
            if (idStr.Icmp(string, "challengeResponse") == 0) {
                ProcessChallengeResponseMessage(from, msg)
                return
            }

            // connect response from the server we are connecting to
            if (idStr.Icmp(string, "connectResponse") == 0) {
                ProcessConnectResponseMessage(from, msg)
                return
            }

            // a disconnect message from the server, which will happen if the server
            // dropped the connection but is still getting packets from this client
            if (idStr.Icmp(string, "disconnect") == 0) {
                ProcessDisconnectMessage(from, msg)
                return
            }

            // print request from server
            if (idStr.Icmp(string, "print") == 0) {
                ProcessPrintMessage(from, msg)
                return
            }

            // server pure list
            if (idStr.Icmp(string, "pureServer") == 0) {
                ProcessPureMessage(from, msg)
                return
            }
            if (idStr.Icmp(string, "downloadInfo") == 0) {
                ProcessDownloadInfoMessage(from, msg)
            }
            if (idStr.Icmp(string, "authrequired") == 0) {
                // server telling us that he's expecting an auth mode connect, just in case we're trying to connect in LAN mode
                if (idAsyncNetwork.LANServer.GetBool()) {
                    Common.common.Warning(
                        "server %s requests master authorization for this client. Turning off LAN mode\n",
                        win_net.Sys_NetAdrToString(from)
                    )
                    idAsyncNetwork.LANServer.SetBool(false)
                }
            }
            Common.common.DPrintf("ignored message from %s: %s\n", win_net.Sys_NetAdrToString(from), string)
        }

        private fun ProcessMessage(from: netadr_t, msg: idBitMsg) {
            val id: Int
            id = msg.ReadShort().toInt()

            // check for a connectionless packet
            if (id == MsgChannel.CONNECTIONLESS_MESSAGE_ID) {
                ConnectionlessMessage(from, msg)
                return
            }
            if (clientState.ordinal < clientState_t.CS_CONNECTED.ordinal) {
                return  // can't be a valid sequenced packet
            }
            if (msg.GetRemaingData() < 4) {
                Common.common.DPrintf("%s: tiny packet\n", win_net.Sys_NetAdrToString(from))
                return
            }

            // is this a packet from the server
            if (!win_net.Sys_CompareNetAdrBase(from, channel.GetRemoteAddress()) || id != serverId) {
                Common.common.DPrintf(
                    "%s: sequenced server packet without connection\n",
                    win_net.Sys_NetAdrToString(from)
                )
                return
            }
            val serverMessageSequence = CInt()
            if (!channel.Process(from, clientTime, msg, serverMessageSequence)) {
                this.serverMessageSequence = serverMessageSequence._val
                return  // out of order, duplicated, fragment, etc.
            }
            this.serverMessageSequence = serverMessageSequence._val
            lastPacketTime = clientTime
            ProcessReliableServerMessages()
            ProcessUnreliableServerMessage(msg)
        }

        @Throws(idException::class)
        private fun SetupConnection() {
            val msg = idBitMsg()
            val msgBuf = ByteBuffer.allocate(MsgChannel.MAX_MESSAGE_SIZE)
            if (clientTime - lastConnectTime < SETUP_CONNECTION_RESEND_TIME) {
                return
            }
            if (clientState == clientState_t.CS_CHALLENGING) {
                Common.common.Printf("sending challenge to %s\n", win_net.Sys_NetAdrToString(serverAddress))
                msg.Init(msgBuf, MsgChannel.MAX_MESSAGE_SIZE)
                msg.WriteShort(MsgChannel.CONNECTIONLESS_MESSAGE_ID.toShort())
                msg.WriteString("challenge")
                msg.WriteLong(clientId)
                clientPort.SendPacket(serverAddress, msg.GetData()!!, msg.GetSize())
            } else if (clientState == clientState_t.CS_CONNECTING) {
                Common.common.Printf(
                    "sending connect to %s with challenge 0x%x\n",
                    win_net.Sys_NetAdrToString(serverAddress),
                    serverChallenge
                )
                msg.Init(msgBuf, MsgChannel.MAX_MESSAGE_SIZE)
                msg.WriteShort(MsgChannel.CONNECTIONLESS_MESSAGE_ID.toShort())
                msg.WriteString("connect")
                msg.WriteLong(AsyncNetwork.ASYNC_PROTOCOL_VERSION)
                if (BuildDefines.ID_FAKE_PURE) {
                    // fake win32 OS - might need to adapt depending on the case
                    msg.WriteShort(0)
                } else {
                    msg.WriteShort(sys_public.BUILD_OS_ID.toShort())
                }
                msg.WriteLong(clientDataChecksum.toInt())
                msg.WriteLong(serverChallenge)
                msg.WriteShort(clientId.toShort())
                msg.WriteLong(CVarSystem.cvarSystem.GetCVarInteger("net_clientMaxRate"))
                msg.WriteString(CVarSystem.cvarSystem.GetCVarString("com_guid"))
                msg.WriteString(CVarSystem.cvarSystem.GetCVarString("password"), -1, false)
                // do not make the protocol depend on PB
                msg.WriteShort(0)
                clientPort.SendPacket(serverAddress, msg.GetData()!!, msg.GetSize())
                if (idAsyncNetwork.LANServer.GetBool()) {
                    Common.common.Printf("net_LANServer is set, connecting in LAN mode\n")
                } else {
                    // emit a cd key authorization request
                    // modified at protocol 1.37 for XP key addition
                    msg.BeginWriting()
                    msg.WriteShort(MsgChannel.CONNECTIONLESS_MESSAGE_ID.toShort())
                    msg.WriteString("clAuth")
                    msg.WriteLong(AsyncNetwork.ASYNC_PROTOCOL_VERSION)
                    msg.WriteNetadr(serverAddress)
                    // if we don't have a com_guid, this will request a direct reply from auth with it
                    msg.WriteByte(if (!CVarSystem.cvarSystem.GetCVarString("com_guid").isEmpty()) 1 else 0)
                    // send the main key, and flag an extra byte to add XP key
                    msg.WriteString(Session.session.GetCDKey(false))
                    val xpkey: String? = Session.session.GetCDKey(true)
                    msg.WriteByte(if (xpkey != null) 1 else 0)
                    if (xpkey != null) {
                        msg.WriteString(xpkey)
                    }
                    clientPort.SendPacket(idAsyncNetwork.GetMasterAddress(), msg.GetData()!!, msg.GetSize())
                }
            } else {
                return
            }
            lastConnectTime = clientTime
        }

        private fun ProcessPureMessage(from: netadr_t, msg: idBitMsg) {
            val outMsg = idBitMsg()
            val msgBuf = ByteBuffer.allocate(MsgChannel.MAX_MESSAGE_SIZE)
            var i: Int
            val inChecksums = IntArray(FileSystem_h.MAX_PURE_PAKS)
            val gamePakChecksum = CInt()
            if (clientState != clientState_t.CS_CONNECTING) {
                Common.common.Printf("clientState != CS_CONNECTING, pure msg ignored\n")
                return
            }
            if (!ValidatePureServerChecksums(from, msg)) {
                return
            }
            FileSystem_h.fileSystem.GetPureServerChecksums(inChecksums, -1, gamePakChecksum)
            outMsg.Init(msgBuf, MsgChannel.MAX_MESSAGE_SIZE)
            outMsg.WriteShort(MsgChannel.CONNECTIONLESS_MESSAGE_ID.toShort())
            outMsg.WriteString("pureClient")
            outMsg.WriteLong(serverChallenge)
            outMsg.WriteShort(clientId.toShort())
            i = 0
            while (inChecksums[i] != 0) {
                outMsg.WriteLong(inChecksums[i++])
            }
            outMsg.WriteLong(0)
            outMsg.WriteLong(gamePakChecksum._val)
            clientPort.SendPacket(from, outMsg.GetData()!!, outMsg.GetSize())
        }

        @Throws(idException::class)
        private fun ValidatePureServerChecksums(from: netadr_t, msg: idBitMsg): Boolean {
            var i: Int
            var numChecksums: Int
            val numMissingChecksums: Int
            val inChecksums = IntArray(FileSystem_h.MAX_PURE_PAKS)
            val inGamePakChecksum: Int
            val missingChecksums = IntArray(FileSystem_h.MAX_PURE_PAKS)
            val missingGamePakChecksum = IntArray(1)
            val dlmsg = idBitMsg()
            val msgBuf = ByteBuffer.allocate(MsgChannel.MAX_MESSAGE_SIZE)

            // read checksums
            // pak checksums, in a 0-terminated list
            numChecksums = 0
            do {
                i = msg.ReadLong()
                inChecksums[numChecksums++] = i
                // just to make sure a broken message doesn't crash us
                if (numChecksums >= FileSystem_h.MAX_PURE_PAKS) {
                    Common.common.Warning(
                        "MAX_PURE_PAKS ( %d ) exceeded in idAsyncClient.ProcessPureMessage\n",
                        FileSystem_h.MAX_PURE_PAKS
                    )
                    return false
                }
            } while (i != 0)
            inChecksums[numChecksums] = 0
            inGamePakChecksum = msg.ReadLong()
            val reply = FileSystem_h.fileSystem.SetPureServerChecksums(
                inChecksums,
                inGamePakChecksum,
                missingChecksums,
                missingGamePakChecksum
            )
            when (reply) {
                fsPureReply_t.PURE_RESTART -> {
                    // need to restart the filesystem with a different pure configuration
                    CmdSystem.cmdSystem.BufferCommandText(cmdExecution_t.CMD_EXEC_NOW, "disconnect")
                    // restart with the right FS configuration and get back to the server
                    clientState = clientState_t.CS_PURERESTART
                    FileSystem_h.fileSystem.SetRestartChecksums(inChecksums, inGamePakChecksum)
                    CmdSystem.cmdSystem.BufferCommandText(cmdExecution_t.CMD_EXEC_NOW, "reloadEngine")
                    return false
                }

                fsPureReply_t.PURE_MISSING -> {
                    var checkSums: String = ""
                    i = 0
                    while (missingChecksums[i] != 0) {
                        checkSums += Str.va("0x%x ", missingChecksums[i++])
                    }
                    numMissingChecksums = i
                    if (idAsyncNetwork.clientDownload.GetInteger() == 0) {
                        // never any downloads
                        var message = Str.va(
                            Common.common.GetLanguageDict().GetString("#str_07210"),
                            win_net.Sys_NetAdrToString(from)
                        )
                        if (numMissingChecksums > 0) {
                            message += Str.va(
                                Common.common.GetLanguageDict().GetString("#str_06751"),
                                numMissingChecksums,
                                checkSums
                            )
                        }
                        if (missingGamePakChecksum[0] != 0) {
                            message += Str.va(
                                Common.common.GetLanguageDict().GetString("#str_06750"),
                                missingGamePakChecksum[0]
                            )
                        }
                        Common.common.Printf(message)
                        CmdSystem.cmdSystem.BufferCommandText(cmdExecution_t.CMD_EXEC_NOW, "disconnect")
                        Session.session.MessageBox(
                            msgBoxType_t.MSG_OK,
                            message,
                            Common.common.GetLanguageDict().GetString("#str_06735"),
                            true
                        )
                    } else {
                        if (clientState.compareTo(clientState_t.CS_CONNECTED) != -1) {
                            // we are already connected, reconnect to negociate the paks in connectionless mode
                            CmdSystem.cmdSystem.BufferCommandText(cmdExecution_t.CMD_EXEC_NOW, "reconnect")
                            return false
                        }
                        // ask the server to send back download info
                        Common.common.DPrintf(
                            "missing %d paks: %s\n",
                            numMissingChecksums + if (missingGamePakChecksum[0] != 0) 1 else 0,
                            checkSums
                        )
                        if (missingGamePakChecksum[0] != 0) {
                            Common.common.DPrintf("game code pak: 0x%x\n", missingGamePakChecksum[0])
                        }
                        // store the requested downloads
                        GetDownloadRequest(missingChecksums, numMissingChecksums, missingGamePakChecksum[0])
                        // build the download request message
                        // NOTE: in a specific function?
                        dlmsg.Init(msgBuf, msgBuf.capacity())
                        dlmsg.WriteShort(MsgChannel.CONNECTIONLESS_MESSAGE_ID.toShort())
                        dlmsg.WriteString("downloadRequest")
                        dlmsg.WriteLong(serverChallenge)
                        dlmsg.WriteShort(clientId.toShort())
                        // used to make sure the server replies to the same download request
                        dlmsg.WriteLong(dlRequest)
                        // special case the code pak - if we have a 0 checksum then we don't need to download it
                        dlmsg.WriteLong(missingGamePakChecksum[0])
                        // 0-terminated list of missing paks
                        i = 0
                        while (missingChecksums[i] != 0) {
                            dlmsg.WriteLong(missingChecksums[i++])
                        }
                        dlmsg.WriteLong(0)
                        clientPort.SendPacket(from, dlmsg.GetData()!!, dlmsg.GetSize())
                    }
                    return false
                }

                fsPureReply_t.PURE_NODLL -> {
                    Common.common.Printf(
                        Common.common.GetLanguageDict().GetString("#str_07211"),
                        win_net.Sys_NetAdrToString(from)
                    )
                    CmdSystem.cmdSystem.BufferCommandText(cmdExecution_t.CMD_EXEC_NOW, "disconnect")
                    return false
                }

                else -> {}
            }
            return true
        }

        private fun ProcessReliableMessagePure(msg: idBitMsg) {
            val outMsg = idBitMsg()
            val msgBuf = ByteBuffer.allocate(MsgChannel.MAX_MESSAGE_SIZE)
            val inChecksums = IntArray(FileSystem_h.MAX_PURE_PAKS)
            var i: Int
            val gamePakChecksum = CInt()
            val serverGameInitId: Int
            Session.session.SetGUI(null, null)
            serverGameInitId = msg.ReadLong()
            if (serverGameInitId != gameInitId) {
                Common.common.DPrintf(
                    "ignoring pure server checksum from an outdated gameInitId (%d)\n",
                    serverGameInitId
                )
                return
            }
            if (!ValidatePureServerChecksums(serverAddress, msg)) {
                return
            }
            if (idAsyncNetwork.verbose.GetInteger() != 0) {
                Common.common.Printf("received new pure server info. ExecuteMapChange and report back\n")
            }

            // it is now ok to load the next map with updated pure checksums
            Session.sessLocal.ExecuteMapChange(true)

            // upon receiving our pure list, the server will send us SCS_INGAME and we'll start getting snapshots
            FileSystem_h.fileSystem.GetPureServerChecksums(inChecksums, -1, gamePakChecksum)
            outMsg.Init(msgBuf, msgBuf.capacity())
            outMsg.WriteByte(CLIENT_RELIABLE.CLIENT_RELIABLE_MESSAGE_PURE.ordinal.toByte())
            outMsg.WriteLong(gameInitId)
            i = 0
            while (inChecksums[i] != 0) {
                outMsg.WriteLong(inChecksums[i++])
            }
            outMsg.WriteLong(0)
            outMsg.WriteLong(gamePakChecksum._val)
            if (!channel.SendReliableMessage(outMsg)) {
                Common.common.Error("client.server reliable messages overflow\n")
            }
        }

        private fun HandleGuiCommandInternal(cmd: String): String {
            if (0 == idStr.Cmp(cmd, "abort") || 0 == idStr.Cmp(cmd, "pure_abort")) {
                Common.common.DPrintf("connection aborted\n")
                CmdSystem.cmdSystem.BufferCommandText(cmdExecution_t.CMD_EXEC_NOW, "disconnect")
                return ""
            } else {
                Common.common.DWarning("idAsyncClient::HandleGuiCommand: unknown cmd %s", cmd)
            }
            return "" // was return null
        }

        /*
         ==================
         idAsyncClient::SendVersionDLUpdate

         sending those packets is not strictly necessary. just a way to tell the update server
         about what is going on. allows the update server to have a more precise view of the overall
         network load for the updates
         ==================
         */
        private fun SendVersionDLUpdate(state: Int) {
            val msg = idBitMsg()
            val msgBuf = ByteBuffer.allocate(MsgChannel.MAX_MESSAGE_SIZE)
            msg.Init(msgBuf, msgBuf.capacity())
            msg.WriteShort(MsgChannel.CONNECTIONLESS_MESSAGE_ID.toShort())
            msg.WriteString("versionDL")
            msg.WriteLong(AsyncNetwork.ASYNC_PROTOCOL_VERSION)
            msg.WriteShort(state.toShort())
            clientPort.SendPacket(idAsyncNetwork.GetMasterAddress(), msg.GetData()!!, msg.GetSize())
        }

        @Throws(idException::class)
        private fun HandleDownloads() {
            if (updateState == clientUpdateState_t.UPDATE_SENT && clientTime > updateSentTime + 2000) {
                // timing out on no reply
                updateState = clientUpdateState_t.UPDATE_DONE
                if (showUpdateMessage) {
                    Session.session.MessageBox(
                        msgBoxType_t.MSG_OK,
                        Common.common.GetLanguageDict().GetString("#str_04839"),
                        Common.common.GetLanguageDict().GetString("#str_04837"),
                        true
                    )
                    showUpdateMessage = false
                }
                Common.common.DPrintf("No update available\n")
            } else if (backgroundDownload.completed) {
                // only enter these if the download slot is free
                if (updateState == clientUpdateState_t.UPDATE_READY) {
                    //
                    if (Session.session.MessageBox(
                            msgBoxType_t.MSG_YESNO,
                            updateMSG.toString(),
                            Common.common.GetLanguageDict().GetString("#str_04330"),
                            true,
                            "yes"
                        ).isEmpty() == false
                    ) {
                        if (!updateDirectDownload) {
                            idLib.sys.OpenURL(updateURL.toString(), true)
                            updateState = clientUpdateState_t.UPDATE_DONE
                        } else {

                            // we're just creating the file at toplevel inside fs_savepath
                            updateURL.ExtractFileName(updateFile)
                            val f = FileSystem_h.fileSystem.OpenFileWrite(updateFile.toString()) as idFile_Permanent
                            dltotal = 0
                            dlnow = 0
                            backgroundDownload.completed = false
                            backgroundDownload.opcode = dlType_t.DLTYPE_URL
                            backgroundDownload.f = f
                            backgroundDownload.url.status = dlStatus_t.DL_WAIT
                            backgroundDownload.url.dlnow = 0
                            backgroundDownload.url.dltotal = 0
                            backgroundDownload.url.url = updateURL
                            FileSystem_h.fileSystem.BackgroundDownload(backgroundDownload)
                            updateState = clientUpdateState_t.UPDATE_DLING
                            SendVersionDLUpdate(0)
                            Session.session.DownloadProgressBox(
                                backgroundDownload,
                                Str.va("Downloading %s\n", updateFile)
                            )
                            updateState = clientUpdateState_t.UPDATE_DONE
                            if (backgroundDownload.url.status == dlStatus_t.DL_DONE) {
                                SendVersionDLUpdate(1)
                                val fullPath = idStr(f.GetFullPath())
                                FileSystem_h.fileSystem.CloseFile(f)
                                if (Session.session.MessageBox(
                                        msgBoxType_t.MSG_YESNO,
                                        Common.common.GetLanguageDict().GetString("#str_04331"),
                                        Common.common.GetLanguageDict().GetString("#str_04332"),
                                        true,
                                        "yes"
                                    ).isEmpty() == false
                                ) {
                                    if (updateMime == dlMime_t.FILE_EXEC) {
                                        idLib.sys.StartProcess(fullPath.toString(), true)
                                    } else {
                                        idLib.sys.OpenURL(Str.va("file://%s", fullPath.toString()), true)
                                    }
                                } else {
                                    Session.session.MessageBox(
                                        msgBoxType_t.MSG_OK,
                                        Str.va(Common.common.GetLanguageDict().GetString("#str_04333"), fullPath),
                                        Common.common.GetLanguageDict().GetString("#str_04334"),
                                        true
                                    )
                                }
                            } else {
                                if (!backgroundDownload.url.dlerror.isEmpty()) {
                                    Common.common.Warning(
                                        "update download failed. curl error: %s",
                                        backgroundDownload.url.dlerror
                                    )
                                }
                                SendVersionDLUpdate(2)
                                val name = idStr(f.GetName())
                                FileSystem_h.fileSystem.CloseFile(f)
                                FileSystem_h.fileSystem.RemoveFile(name.toString())
                                Session.session.MessageBox(
                                    msgBoxType_t.MSG_OK,
                                    Common.common.GetLanguageDict().GetString("#str_04335"),
                                    Common.common.GetLanguageDict().GetString("#str_04336"),
                                    true
                                )
                                if (updateFallback.Length() != 0) {
                                    idLib.sys.OpenURL(updateFallback.toString(), true)
                                } else {
                                    Common.common.Printf("no fallback URL\n")
                                }
                            }
                        }
                    } else {
                        updateState = clientUpdateState_t.UPDATE_DONE
                    }
                } else if (dlList.Num() != 0) {
                    val numPaks = dlList.Num()
                    var pakCount = 1
                    var progress_start: Int
                    var progress_end: Int
                    currentDlSize = 0
                    do {
                        if (dlList[0].url[0] == '\u0000') {
                            // ignore empty files
                            dlList.RemoveIndex(0)
                            continue
                        }
                        Common.common.Printf("start download for %s\n", dlList[0].url)
                        val f = FileSystem_h.fileSystem.MakeTemporaryFile() as idFile_Permanent
                        if (null == f) {
                            Common.common.Warning("could not create temporary file")
                            dlList.Clear()
                            return
                        }
                        backgroundDownload.completed = false
                        backgroundDownload.opcode = dlType_t.DLTYPE_URL
                        backgroundDownload.f = f
                        backgroundDownload.url.status = dlStatus_t.DL_WAIT
                        backgroundDownload.url.dlnow = 0
                        backgroundDownload.url.dltotal = dlList[0].size
                        backgroundDownload.url.url = dlList[0].url
                        FileSystem_h.fileSystem.BackgroundDownload(backgroundDownload)
                        var dltitle: String
                        // "Downloading %s"
                        dltitle = String.format(
                            Common.common.GetLanguageDict().GetString("#str_07213"),
                            dlList[0].filename.toString()
                        )
                        if (numPaks > 1) {
                            dltitle += Str.va(" (%d/%d)", pakCount, numPaks)
                        }
                        if (totalDlSize != 0) {
                            progress_start = (currentDlSize.toFloat() * 100.0f / totalDlSize.toFloat()).toInt()
                            progress_end =
                                ((currentDlSize + dlList[0].size).toFloat() * 100.0f / totalDlSize.toFloat()).toInt()
                        } else {
                            progress_start = 0
                            progress_end = 100
                        }
                        Session.session.DownloadProgressBox(
                            backgroundDownload,
                            dltitle,
                            progress_start,
                            progress_end
                        )
                        if (backgroundDownload.url.status == dlStatus_t.DL_DONE) {
                            var saveas: idFile
                            val CHUNK_SIZE = 1024 * 1024
                            var buf: ByteBuffer
                            var remainlen: Int
                            var readlen: Int
                            var retlen: Int
                            var checksum: Int
                            Common.common.Printf("file downloaded\n")
                            val finalPath = idStr(CVarSystem.cvarSystem.GetCVarString("fs_savepath"))
                            finalPath.AppendPath(dlList[0].filename.toString())
                            FileSystem_h.fileSystem.CreateOSPath(finalPath.toString())
                            // do the final copy ourselves so we do by small chunks in case the file is big
                            saveas = FileSystem_h.fileSystem.OpenExplicitFileWrite(finalPath.toString())!!
                            buf = ByteBuffer.allocate(CHUNK_SIZE) // Mem_Alloc(CHUNK_SIZE);
                            f.Seek(0, fsOrigin_t.FS_SEEK_END)
                            remainlen = f.Tell()
                            f.Seek(0, fsOrigin_t.FS_SEEK_SET)
                            while (remainlen != 0) {
                                readlen = Min(remainlen, CHUNK_SIZE)
                                retlen = f.Read(buf, readlen)
                                if (retlen != readlen) {
                                    Common.common.FatalError(
                                        "short read %d of %d in idFileSystem.HandleDownload",
                                        retlen,
                                        readlen
                                    )
                                }
                                retlen = saveas.Write(buf, readlen)
                                if (retlen != readlen) {
                                    Common.common.FatalError(
                                        "short write %d of %d in idFileSystem.HandleDownload",
                                        retlen,
                                        readlen
                                    )
                                }
                                remainlen -= readlen
                            }
                            FileSystem_h.fileSystem.CloseFile(f)
                            FileSystem_h.fileSystem.CloseFile(saveas)
                            Common.common.Printf("saved as %s\n", finalPath)

                            // add that file to our paks list
                            checksum = FileSystem_h.fileSystem.AddZipFile(dlList[0].filename.toString())

                            // verify the checksum to be what the server says
                            if (0 == checksum || checksum != dlList[0].checksum) {
                                // "pak is corrupted ( checksum 0x%x, expected 0x%x )"
                                Session.session.MessageBox(
                                    msgBoxType_t.MSG_OK,
                                    Str.va(
                                        Common.common.GetLanguageDict().GetString("#str_07214"),
                                        checksum,
                                        dlList[0].checksum
                                    ),
                                    "Download failed",
                                    true
                                )
                                FileSystem_h.fileSystem.RemoveFile(dlList[0].filename.toString())
                                dlList.Clear()
                                return
                            }
                            currentDlSize += dlList[0].size
                        } else {
                            Common.common.Warning("download failed: %s", dlList[0].url)
                            if (!backgroundDownload.url.dlerror.isEmpty()) {
                                Common.common.Warning("curl error: %s", backgroundDownload.url.dlerror)
                            }
                            // "The download failed or was cancelled"
                            // "Download failed"
                            Session.session.MessageBox(
                                msgBoxType_t.MSG_OK,
                                Common.common.GetLanguageDict().GetString("#str_07215"),
                                Common.common.GetLanguageDict().GetString("#str_07216"),
                                true
                            )
                            dlList.Clear()
                            return
                        }
                        pakCount++
                        dlList.RemoveIndex(0)
                    } while (dlList.Num() != 0)

                    // all downloads successful - do the dew
                    CmdSystem.cmdSystem.BufferCommandText(cmdExecution_t.CMD_EXEC_APPEND, "reconnect\n")
                }
            }
        }

        private fun Idle() {
            // also need to read mouse for the connecting guis
            UsercmdGen.usercmdGen.GetDirectUsercmd()
            SendEmptyToServer()
        }

        private fun UpdateTime(clamp: Int): Int {
            val time: Int
            val msec: Int
            time = win_shared.Sys_Milliseconds()
            msec = idMath.ClampInt(0, clamp, time - realTime)
            realTime = time
            clientTime += msec
            return msec
        }

        private fun ReadLocalizedServerString(msg: idBitMsg, out: CharArray, maxLen: Int) {
            msg.ReadString(out, maxLen)
            // look up localized string. if the message is not an #str_ format, we'll just get it back unchanged
            idStr.snPrintf(
                out,
                maxLen - 1,
                "%s",
                Common.common.GetLanguageDict().GetString(TempDump.ctos(out))
            )
        }

        private fun CheckTimeout(): Boolean {
            if (lastPacketTime > 0 && lastPacketTime + idAsyncNetwork.clientServerTimeout.GetInteger() * 1000 < clientTime) {
                Session.session.StopBox()
                Session.session.MessageBox(
                    msgBoxType_t.MSG_OK,
                    Common.common.GetLanguageDict().GetString("#str_04328"),
                    Common.common.GetLanguageDict().GetString("#str_04329"),
                    true
                )
                CmdSystem.cmdSystem.BufferCommandText(cmdExecution_t.CMD_EXEC_NOW, "disconnect")
                return true
            }
            return false
        }

        @Throws(idException::class)
        private fun ProcessDownloadInfoMessage(from: netadr_t, msg: idBitMsg) {
            val buf = CharArray(MAX_STRING_CHARS)
            val srvDlRequest = msg.ReadLong()
            val infoType = msg.ReadByte()
            var pakDl: Int
            var pakIndex: Int
            val entry = pakDlEntry_t()
            var gotAllFiles = true
            val sizeStr: idStr = idPoolStr()
            var gotGame = false
            if (dlRequest == -1 || srvDlRequest != dlRequest) {
                Common.common.Warning("bad download id from server, ignored")
                return
            }
            // mark the dlRequest as dead now whatever how we process it
            dlRequest = -1
            if (infoType == SERVER_DL.SERVER_DL_REDIRECT.ordinal.toByte()) {
                msg.ReadString(buf, MAX_STRING_CHARS)
                CmdSystem.cmdSystem.BufferCommandText(cmdExecution_t.CMD_EXEC_NOW, "disconnect")
                // "You are missing required pak files to connect to this server.\nThe server gave a web page though:\n%s\nDo you want to go there now?"
                // "Missing required files"
                if (
                    Session.session.MessageBox(
                        msgBoxType_t.MSG_YESNO,
                        Str.va(Common.common.GetLanguageDict().GetString("#str_07217"), buf),
                        Common.common.GetLanguageDict().GetString("#str_07218"),
                        true,
                        "yes"
                    ).isNotEmpty()
                ) {
                    idLib.sys.OpenURL(TempDump.ctos(buf), true)
                }
            } else if (infoType == SERVER_DL.SERVER_DL_LIST.ordinal.toByte()) {
                CmdSystem.cmdSystem.BufferCommandText(cmdExecution_t.CMD_EXEC_NOW, "disconnect")
                if (dlList.Num() != 0) {
                    Common.common.Warning("tried to process a download list while already busy downloading things")
                    return
                }
                // read the URLs, check against what we requested, prompt for download
                pakIndex = -1
                totalDlSize = 0
                do {
                    pakIndex++
                    pakDl = msg.ReadByte().toInt()
                    if (pakDl == SERVER_PAK.SERVER_PAK_YES.ordinal) {
                        if (pakIndex == 0) {
                            gotGame = true
                        }
                        msg.ReadString(buf, MAX_STRING_CHARS)
                        entry.filename.set(buf)
                        msg.ReadString(buf, MAX_STRING_CHARS)
                        entry.url.set(buf)
                        entry.size = msg.ReadLong()
                        // checksums are not transmitted, we read them from the dl request we sent
                        entry.checksum = dlChecksums[pakIndex]
                        totalDlSize += entry.size
                        dlList.Append(entry)
                        Common.common.Printf(
                            "download %s from %s ( 0x%x )\n",
                            entry.filename,
                            entry.url,
                            entry.checksum
                        )
                    } else if (pakDl == SERVER_PAK.SERVER_PAK_NO.ordinal) {
                        msg.ReadString(buf, MAX_STRING_CHARS)
                        entry.filename.set(buf)
                        entry.url.set("")
                        entry.size = 0
                        entry.checksum = 0
                        dlList.Append(entry)
                        // first pak is game pak, only fail it if we actually requested it
                        if (pakIndex != 0 || dlChecksums[0] != 0) {
                            Common.common.Printf(
                                "no download offered for %s ( 0x%x )\n",
                                entry.filename,
                                dlChecksums[pakIndex]
                            )
                            gotAllFiles = false
                        }
                    } else {
                        assert(pakDl == SERVER_PAK.SERVER_PAK_END.ordinal)
                    }
                } while (pakDl != SERVER_PAK.SERVER_PAK_END.ordinal)
                if (dlList.Num() < dlCount) {
                    Common.common.Printf("%d files were ignored by the server\n", dlCount - dlList.Num())
                    gotAllFiles = false
                }
                sizeStr.BestUnit("%.2f", totalDlSize.toFloat(), Measure_t.MEASURE_SIZE)
                CmdSystem.cmdSystem.BufferCommandText(cmdExecution_t.CMD_EXEC_NOW, "disconnect")
                if (totalDlSize == 0) {
                    // was no downloadable stuff for us
                    // "Can't connect to the pure server: no downloads offered"
                    // "Missing required files"
                    dlList.Clear()
                    Session.session.MessageBox(
                        msgBoxType_t.MSG_OK,
                        Common.common.GetLanguageDict().GetString("#str_07219"),
                        Common.common.GetLanguageDict().GetString("#str_07218"),
                        true
                    )
                    return
                }
                var asked = false
                if (gotGame) {
                    asked = true
                    // "You need to download game code to connect to this server. Are you sure? You should only answer yes if you trust the server administrators."
                    // "Missing game binaries"
                    if (Session.session.MessageBox(
                            msgBoxType_t.MSG_YESNO,
                            Common.common.GetLanguageDict().GetString("#str_07220"),
                            Common.common.GetLanguageDict().GetString("#str_07221"),
                            true,
                            "yes"
                        ).isEmpty()
                    ) {
                        dlList.Clear()
                        return
                    }
                }
                if (!gotAllFiles) {
                    asked = true
                    // "The server only offers to download some of the files required to connect ( %s ). Download anyway?"
                    // "Missing required files"
                    if (
                        Session.session.MessageBox(
                            msgBoxType_t.MSG_YESNO,
                            Str.va(Common.common.GetLanguageDict().GetString("#str_07222"), sizeStr.toString()),
                            Common.common.GetLanguageDict().GetString("#str_07218"),
                            true,
                            "yes"
                        ).isNotEmpty()

                    ) { //TODO:check whether a NOT on the whole string is the same as an empty string
                        dlList.Clear()
                        return
                    }
                }
                if (!asked && idAsyncNetwork.clientDownload.GetInteger() == 1) {
                    // "You need to download some files to connect to this server ( %s ), proceed?"
                    // "Missing required files"
                    if (
                        Session.session.MessageBox(
                            msgBoxType_t.MSG_YESNO,
                            Str.va(Common.common.GetLanguageDict().GetString("#str_07224"), sizeStr.toString()),
                            Common.common.GetLanguageDict().GetString("#str_07218"),
                            true,
                            "yes"
                        ).isNotEmpty()

                    ) {
                        dlList.Clear()
                        return
                    }
                }
            } else {
                CmdSystem.cmdSystem.BufferCommandText(cmdExecution_t.CMD_EXEC_NOW, "disconnect")
                // "You are missing some files to connect to this server, and the server doesn't provide downloads."
                // "Missing required files"
                Session.session.MessageBox(
                    msgBoxType_t.MSG_OK,
                    Common.common.GetLanguageDict().GetString("#str_07223"),
                    Common.common.GetLanguageDict().GetString("#str_07218"),
                    true
                )
            }
        }

        private fun GetDownloadRequest(
            checksums: IntArray /*[MAX_PURE_PAKS]*/,
            count: Int,
            gamePakChecksum: Int
        ): Int {
            assert(
                0 == checksums[count] // 0-terminated
            )
            //            if (memcmp(dlChecksums + 1, checksums, sizeof(int) * count) || gamePakChecksum != dlChecksums[ 0]) {
            if (TempDump.memcmp(dlChecksums, 1, checksums, 0, count) || gamePakChecksum != dlChecksums[0]) {
                val newreq = idRandom()
                dlChecksums[0] = gamePakChecksum
                //                memcpy(dlChecksums + 1, checksums, sizeof(int) * MAX_PURE_PAKS);
                TempDump.memcmp(dlChecksums, 1, checksums, 0, FileSystem_h.MAX_PURE_PAKS)
                newreq.SetSeed(win_shared.Sys_Milliseconds())
                dlRequest = newreq.RandomInt()
                dlCount = count + if (gamePakChecksum != 0) 1 else 0
                return dlRequest
            }
            // this is the same dlRequest, we haven't heard from the server. keep the same id
            return dlRequest
        }

        private class HandleGuiCommand private constructor() : HandleGuiCommand_t() {
            override fun run(input: String): String {
                return idAsyncNetwork.client.HandleGuiCommandInternal(input)
            }

            companion object {
                private val instance: HandleGuiCommand_t = HandleGuiCommand()
                fun getInstance(): HandleGuiCommand_t {
                    return instance
                }
            }
        }

        //
        //
        init {
            Clear()
        }
    }
}
package neo.framework.Async

import neo.framework.*
import neo.framework.Async.AsyncNetwork.idAsyncNetwork
import neo.framework.CVarSystem.idCVar
import neo.framework.DeclEntityDef.idDeclEntityDef
import neo.framework.DeclManager.declType_t
import neo.idlib.Dict_h.idDict
import neo.idlib.Dict_h.idKeyValue
import neo.idlib.MAX_STRING_CHARS
import neo.idlib.Min
import neo.idlib.Text.Str
import neo.idlib.Text.Str.idStr
import neo.idlib.Text.ctos
import neo.idlib.containers.List.cmp_t
import neo.idlib.containers.List.idList
import neo.idlib.idException
import neo.sys.netadr_t
import neo.sys.win_net
import neo.sys.win_shared
import neo.ui.ListGUI.idListGUI
import neo.ui.UserInterface
import neo.ui.UserInterface.idUserInterface

class ServerScan {/*
     ===============================================================================

     Scan for servers, on the LAN or from a list
     Update a listDef GUI through usage of idListGUI class
     When updating large lists of servers, sends out getInfo in small batches to avoid congestion

     ===============================================================================
     */


    //    
    enum class scan_state_t {
        IDLE,
        WAIT_ON_INIT,
        LAN_SCAN,
        NET_SCAN
    }

    enum class serverSort_t {
        SORT_PING,
        SORT_SERVERNAME,
        SORT_PLAYERS,
        SORT_GAMETYPE,
        SORT_MAP,
        SORT_GAME
    }

    // storage for incoming servers / server scan
    internal class inServer_t {
        var adr: netadr_t = netadr_t()
        var id = 0
        var time = 0
    }

    // the menu gui uses a hard-coded control type to display a list of network games
    class networkServer_t {
        var OSMask = 0
        var adr: netadr_t = netadr_t()
        var challenge = 0
        var clients = 0
        var id // idnet mode sends an id for each server in list
                = 0
        var nickname: Array<CharArray> = Array(AsyncNetwork.MAX_ASYNC_CLIENTS) { CharArray(AsyncNetwork.MAX_NICKLEN) }
        var ping = 0
        var pings: ShortArray = ShortArray(AsyncNetwork.MAX_ASYNC_CLIENTS)
        var rate: IntArray = IntArray(AsyncNetwork.MAX_ASYNC_CLIENTS)
        var serverInfo: idDict = idDict()
    }

    /*
     ================
     idServerScan
     ================
     */
    class idServerScan : idList<networkServer_t>() {
        private val m_sortedServers // use ascending for the walking order
                : idList<Int>

        //
        // servers we're waiting for a reply from
        // won't exceed MAX_PINGREQUESTS elements
        // holds index of net_servers elements, indexed by 'from' string
        private val net_info: idDict

        //
        private val net_servers: idList<inServer_t>

        //
        private val screenshot: idStr
        private var challenge // challenge for current scan
                : Int

        // where we are in net_servers list for getInfo emissions ( NET_SCAN only )
        // we may either be waiting on MAX_PINGREQUESTS, or for net_servers to grow some more ( through AddServer )
        private var cur_info = 0

        //
        private var endWaitTime // when to stop waiting on a port init
                = 0
        private var incoming_lastTime = 0

        //
        private var incoming_net // set to true while new servers are fed through AddServer
                = false
        private var incoming_useTimeout = false

        //
        private var lan_pingtime // holds the time of LAN scan
                = 0
        private var listGUI: idListGUI? = null

        ///
        private var m_pGUI: idUserInterface? = null

        //
        private var m_sort: serverSort_t
        private var m_sortAscending: Boolean

        //
        private var scan_state: scan_state_t = scan_state_t.IDLE

        @Throws(idException::class)
        fun InfoResponse(server: networkServer_t): Int {
            if (scan_state == scan_state_t.IDLE) {
                return 0
            }
            val serv = idStr(win_net.Sys_NetAdrToString(server.adr))
            if (server.challenge != challenge) {
                Common.common.DPrintf(
                    "idServerScan::InfoResponse - ignoring response from %s, wrong challenge %d.",
                    serv.toString(),
                    server.challenge
                )
                return 0
            }
            if (scan_state == scan_state_t.NET_SCAN) {
                val info = net_info.FindKey(serv.toString())
                if (null == info) {
                    Common.common.DPrintf(
                        "idServerScan::InfoResponse NET_SCAN: reply from unknown %s\n", serv.toString()
                    )
                    return 0
                }
                val id = info.GetValue().toString().toInt()
                net_info.Delete(serv.toString())
                val iserv = net_servers[id]
                server.ping = win_shared.Sys_Milliseconds() - iserv.time
                server.id = iserv.id
            } else {
                server.ping = win_shared.Sys_Milliseconds() - lan_pingtime
                server.id = 0

                // check for duplicate servers
                for (i in 0 until Num()) { // FIX: was != (inverted) - C++ memcmp == 0 means EQUAL, so check for equal addresses
                    if (get(i).adr == server.adr) {
                        Common.common.DPrintf(
                            "idServerScan::InfoResponse LAN_SCAN: duplicate server %s\n", serv.toString()
                        )
                        return 1
                    }
                }
            }
            val si_map = server.serverInfo.GetString("si_map")
            val mapDecl = DeclManager.declManager.FindType(declType_t.DECL_MAPDEF, si_map, false)
            val mapDef = mapDecl as idDeclEntityDef?
            if (mapDef != null) {
                val mapName = Common.common.GetLanguageDict().GetString(mapDef.dict.GetString("name", si_map))
                server.serverInfo.Set("si_mapName", mapName)
            } else {
                server.serverInfo.Set("si_mapName", si_map)
            }
            val index = Append(server) // for now, don't maintain sorting when adding new info response servers
            m_sortedServers.Append(Num() - 1)
            if (listGUI!!.IsConfigured() && !IsFiltered(server)) {
                GUIAdd(Num() - 1, server)
            }
            if (listGUI!!.GetSelection(null, 0) == Num() - 1) {
                GUIUpdateSelected()
            }
            return index
        }

        //
        // add an internet server - ( store a numeric id along with it )
        fun AddServer(id: Int, srv: String) {
            val s = inServer_t()
            incoming_net = true
            incoming_lastTime = win_shared.Sys_Milliseconds() + INCOMING_TIMEOUT
            s.id = id

            // using IPs, not hosts
            if (!win_net.Sys_StringToNetAdr(srv, s.adr, false)) {
                Common.common.DPrintf("idServerScan::AddServer: failed to parse server %s\n", srv)
                return
            }
            if (0 == s.adr.port) {
                s.adr.port = Licensee.PORT_SERVER
            }
            net_servers.Append(s)
        }

        //
        // we are going to feed server entries to be pinged
        // if timeout is true, use a timeout once we start AddServer to trigger EndServers and decide the scan is done
        fun StartServers(timeout: Boolean) {
            incoming_net = true
            incoming_useTimeout = timeout
            incoming_lastTime = win_shared.Sys_Milliseconds() + REFRESH_START
        }

        // we are done filling up the list of server entries
        fun EndServers() {
            incoming_net = false
            l_serverScan = this
            m_sortedServers.Sort(Cmp())
            ApplyFilter()
        }

        //
        // scan the current list of servers - used for refreshes and while receiving a fresh list
        fun NetScan() {
            if (!idAsyncNetwork.client.IsPortInitialized()) { // if the port isn't open, initialize it, but wait for a short
                // time to let the OS do whatever magic things it needs to do...
                idAsyncNetwork.client.InitPort() // start the scan one second from now...
                scan_state = scan_state_t.WAIT_ON_INIT
                endWaitTime = win_shared.Sys_Milliseconds() + 1000
                return
            }

            // make sure the client port is open
            idAsyncNetwork.client.InitPort()
            scan_state = scan_state_t.NET_SCAN
            challenge++
            super.Clear()
            m_sortedServers.Clear()
            cur_info = 0
            net_info.Clear()
            listGUI!!.Clear()
            GUIUpdateSelected()
            Common.common.DPrintf("NetScan with challenge %d\n", challenge)
            while (cur_info < Min(net_servers.Num(), MAX_PINGREQUESTS)) {
                val serv = net_servers[cur_info].adr
                EmitGetInfo(serv)
                net_servers[cur_info].time = win_shared.Sys_Milliseconds()
                net_info.SetInt(win_net.Sys_NetAdrToString(serv), cur_info)
                cur_info++
            }
        }

        // clear
        override fun Clear() {
            LocalClear()
            super.Clear()
        }

        // called each game frame. Updates the scanner state, takes care of ongoing scans
        fun RunFrame() {
            if (scan_state == scan_state_t.IDLE) {
                return
            }
            if (scan_state == scan_state_t.WAIT_ON_INIT) {
                if (win_shared.Sys_Milliseconds() >= endWaitTime) {
                    scan_state = scan_state_t.IDLE
                    NetScan()
                }
                return
            }
            val timeout_limit = win_shared.Sys_Milliseconds() - REPLY_TIMEOUT
            if (scan_state == scan_state_t.LAN_SCAN) {
                if (timeout_limit > lan_pingtime) {
                    Common.common.Printf("Scanned for servers on the LAN\n")
                    scan_state = scan_state_t.IDLE
                }
                return
            }

            // if scan_state == NET_SCAN
            // check for timeouts
            var i = 0
            while (i < net_info.GetNumKeyVals()) {
                if (timeout_limit > net_servers[net_info.GetKeyVal(i)!!.GetValue().toString().toInt()].time) {
                    Common.common.DPrintf("timeout %s\n", net_info.GetKeyVal(i)!!.GetKey().toString())
                    net_info.Delete(net_info.GetKeyVal(i)!!.GetKey().toString())
                } else {
                    i++
                }
            }

            // possibly send more queries
            while (cur_info < net_servers.Num() && net_info.GetNumKeyVals() < MAX_PINGREQUESTS) {
                val serv = net_servers[cur_info].adr
                EmitGetInfo(serv)
                net_servers[cur_info].time = win_shared.Sys_Milliseconds()
                net_info.SetInt(win_net.Sys_NetAdrToString(serv), cur_info)
                cur_info++
            }

            // update state
            if ((!incoming_net || incoming_useTimeout && win_shared.Sys_Milliseconds() > incoming_lastTime) && net_info.GetNumKeyVals() == 0) {
                EndServers() // the list is complete, we are no longer waiting for any getInfo replies
                Common.common.Printf("Scanned %d servers.\n", cur_info)
                scan_state = scan_state_t.IDLE
            }
        }

        fun GetState(): scan_state_t {
            return scan_state
        }

        fun SetState(scan_state: scan_state_t) {
            this.scan_state = scan_state
        }

        //	
        fun GetBestPing(serv: networkServer_t): Boolean {
            var i: Int
            val ic: Int
            ic = Num()
            if (0 == ic) {
                return false
            } // FIX: was reassigning local var reference instead of copying fields.
            // C++ operator= copies all struct fields; Kotlin reference reassignment just changes the pointer.
            var bestServer = get(0)
            i = 1
            while (i < ic) {
                if (get(i).ping < bestServer.ping) {
                    bestServer = get(i)
                }
                i++
            } // Copy best server fields to the output parameter
            serv.adr = bestServer.adr
            serv.serverInfo = bestServer.serverInfo
            serv.ping = bestServer.ping
            serv.id = bestServer.id
            serv.clients = bestServer.clients
            serv.challenge = bestServer.challenge
            serv.OSMask = bestServer.OSMask
            System.arraycopy(bestServer.pings, 0, serv.pings, 0, serv.pings.size)
            System.arraycopy(bestServer.rate, 0, serv.rate, 0, serv.rate.size)
            for (j in 0 until serv.nickname.size) {
                System.arraycopy(bestServer.nickname[j], 0, serv.nickname[j], 0, serv.nickname[j].size)
            }
            return true
        }

        //
        // prepare for a LAN scan. idAsyncClient does the network job (UDP broadcast), we do the storage
        fun SetupLANScan() {
            Clear()
            GUIUpdateSelected()
            scan_state = scan_state_t.LAN_SCAN
            challenge++
            lan_pingtime = win_shared.Sys_Milliseconds()
            Common.common.DPrintf("SetupLANScan with challenge %d\n", challenge)
        }

        //
        fun GUIConfig(pGUI: idUserInterface, name: String) {
            m_pGUI = pGUI
            if (listGUI == null) {
                listGUI = UserInterface.uiManager.AllocListGUI()
            }
            listGUI!!.Config(pGUI, name)
        }

        // update the GUI fields with information about the currently selected server
        @Throws(idException::class)
        fun GUIUpdateSelected() {
            val screenshot = StringBuffer() //new char[MAX_STRING_CHARS];
            if (null == m_pGUI) {
                return
            }
            val i = listGUI!!.GetSelection(null, 0)
            if (i == -1 || i >= Num()) {
                m_pGUI!!.SetStateString("server_name", "")
                m_pGUI!!.SetStateString("player1", "")
                m_pGUI!!.SetStateString("player2", "")
                m_pGUI!!.SetStateString("player3", "")
                m_pGUI!!.SetStateString("player4", "")
                m_pGUI!!.SetStateString("player5", "")
                m_pGUI!!.SetStateString("player6", "")
                m_pGUI!!.SetStateString("player7", "")
                m_pGUI!!.SetStateString("player8", "")
                m_pGUI!!.SetStateString("server_map", "")
                m_pGUI!!.SetStateString("browser_levelshot", "")
                m_pGUI!!.SetStateString("server_gameType", "")
                m_pGUI!!.SetStateString("server_IP", "")
                m_pGUI!!.SetStateString("server_passworded", "")
            } else {
                m_pGUI!!.SetStateString("server_name", get(i).serverInfo.GetString("si_name"))
                for (j in 0..7) {
                    if (get(i).clients > j) {
                        m_pGUI!!.SetStateString(Str.va("player%d", j + 1), ctos(get(i).nickname[j]))
                    } else {
                        m_pGUI!!.SetStateString(Str.va("player%d", j + 1), "")
                    }
                }
                m_pGUI!!.SetStateString("server_map", get(i).serverInfo.GetString("si_mapName"))
                FileSystem_h.fileSystem.FindMapScreenshot(
                    get(i).serverInfo.GetString("si_map"), screenshot, MAX_STRING_CHARS
                )
                m_pGUI!!.SetStateString("browser_levelshot", screenshot.toString())
                m_pGUI!!.SetStateString("server_gameType", get(i).serverInfo.GetString("si_gameType"))
                m_pGUI!!.SetStateString("server_IP", win_net.Sys_NetAdrToString(get(i).adr))
                if (get(i).serverInfo.GetBool("si_usePass")) {
                    m_pGUI!!.SetStateString("server_passworded", "PASSWORD REQUIRED")
                } else {
                    m_pGUI!!.SetStateString("server_passworded", "")
                }
            }
        }

        fun Shutdown() {
            m_pGUI = null
            if (listGUI != null) {
                listGUI!!.Config(null, null)
                UserInterface.uiManager.FreeListGUI(listGUI!!)
                listGUI = null
            }
            screenshot.Clear()
        }

        @Throws(idException::class)
        fun ApplyFilter() {
            var i: Int
            var serv: networkServer_t?
            listGUI!!.SetStateChanges(false)
            listGUI!!.Clear()
            i = if (m_sortAscending) 0 else m_sortedServers.Num() - 1
            while (if (m_sortAscending) i < m_sortedServers.Num() else i >= 0) {
                serv = get(m_sortedServers[i])
                if (!IsFiltered(serv)) {
                    GUIAdd(m_sortedServers[i], serv)
                }
                i += if (m_sortAscending) 1 else -1
            }
            GUIUpdateSelected()
            listGUI!!.SetStateChanges(true)
        }

        // there is an internal toggle, call twice with same sort to switch
        fun SetSorting(sort: serverSort_t) {
            l_serverScan = this
            if (sort == m_sort) {
                m_sortAscending = !m_sortAscending
            } else {
                m_sort = sort
                m_sortAscending = true // is the default for any new sort
                m_sortedServers.Sort(Cmp())
            } // trigger a redraw
            ApplyFilter()
        }

        fun GetChallenge(): Int {
            return challenge
        }

        // we need to clear some internal data as well
        private fun LocalClear() {
            scan_state = scan_state_t.IDLE
            incoming_net = false
            lan_pingtime = -1
            net_info.Clear()
            net_servers.Clear()
            cur_info = 0
            if (listGUI != null) {
                listGUI!!.Clear()
            }
            incoming_useTimeout = false
            m_sortedServers.Clear()
        }

        private fun EmitGetInfo(serv: netadr_t) {
            idAsyncNetwork.client.GetServerInfo(serv)
        }

        @Throws(idException::class)
        private fun GUIAdd(id: Int, server: networkServer_t) {
            var name = server.serverInfo.GetString("si_name", Licensee.GAME_NAME + " Server")!!
            var d3xp = false
            var mod = false
            if (0 == idStr.Icmp(
                    server.serverInfo.GetString("fs_game"), "d3xp"
                ) || 0 == idStr.Icmp(server.serverInfo.GetString("fs_game_base"), "d3xp")
            ) {
                d3xp = true
            }
            if (!server.serverInfo.GetString("fs_game").isNullOrEmpty()) {
                mod = true
            }
            name += "\t"
            if (server.serverInfo.GetString("sv_punkbuster") == "1") {
                name += "mtr_PB"
            }
            name += "\t"
            name += if (d3xp) { // FIXME: even for a 'D3XP mod'
                // could have a specific icon for this case
                "mtr_doom3XPIcon"
            } else if (mod) {
                "mtr_doom3Mod"
            } else {
                "mtr_doom3Icon"
            }
            name += "\t"
            name += Str.va("%d/%d\t", server.clients, server.serverInfo.GetInt("si_maxPlayers"))
            name += if (server.ping > -1) Str.va("%d\t", server.ping) else "na\t"
            name += server.serverInfo.GetString("si_gametype")
            name += "\t"
            name += server.serverInfo.GetString("si_mapName")
            name += "\t"
            listGUI!!.Add(id, idStr(name))
        }

        @Throws(idException::class)
        private fun IsFiltered(server: networkServer_t): Boolean {
            var i: Int
            var keyval: idKeyValue? // password filter
            keyval = server.serverInfo.FindKey("si_usePass")
            if (keyval != null && gui_filter_password.GetInteger() == 1) { // show passworded only
                if (keyval.GetValue()[0] == '0') {
                    return true
                }
            } else if (keyval != null && gui_filter_password.GetInteger() == 2) { // show no password only
                if (keyval.GetValue()[0] != '0') {
                    return true
                }
            } // players filter
            keyval = server.serverInfo.FindKey("si_maxPlayers")
            if (keyval != null) {
                if (gui_filter_players.GetInteger() == 1 && server.clients == keyval.GetValue().toString().toInt()) {
                    return true
                } else if (gui_filter_players.GetInteger() == 2 && (0 == server.clients || server.clients == keyval.GetValue()
                        .toString().toInt())
                ) {
                    return true
                }
            } // gametype filter
            keyval = server.serverInfo.FindKey("si_gameType")
            if (keyval != null && gui_filter_gameType.GetInteger() != 0) {
                i = 0
                while (l_gameTypes[i] != null) {
                    if (0 == keyval.GetValue().Icmp(l_gameTypes[i]!!)) {
                        break
                    }
                    i++
                }
                if (l_gameTypes[i] != null && i != gui_filter_gameType.GetInteger() - 1) {
                    return true
                }
            } // idle server filter
            keyval = server.serverInfo.FindKey("si_idleServer")
            if (keyval != null && 0 == gui_filter_idle.GetInteger()) {
                if (0 == keyval.GetValue().Icmp("1")) {
                    return true
                }
            }

            // autofilter D3XP games if the user does not has the XP installed
            if (!FileSystem_h.fileSystem.HasD3XP() && 0 == idStr.Icmp(
                    server.serverInfo.GetString("fs_game"), "d3xp"
                )
            ) {
                return true
            }

            // filter based on the game doom or XP
            if (gui_filter_game.GetInteger() == 1) { //Only Doom
                return idStr.Icmp(server.serverInfo.GetString("fs_game"), "") != 0
            } else if (gui_filter_game.GetInteger() == 2) { //Only D3XP
                return idStr.Icmp(server.serverInfo.GetString("fs_game"), "d3xp") != 0
            }
            return false
        }

        private class Cmp : cmp_t<Int> {
            override fun compare(a: Int?, b: Int?): Int {
                if (a == null || b == null) return 0
                val serv1: networkServer_t
                val serv2: networkServer_t
                val s1 = idStr()
                val s2 = idStr()
                val ret: Int
                serv1 = l_serverScan[a]
                serv2 = l_serverScan[b]
                when (l_serverScan.m_sort) {
                    serverSort_t.SORT_PING -> {
                        ret = if (serv1.ping < serv2.ping) -1 else if (serv1.ping > serv2.ping) 1 else 0
                        return ret
                    }

                    serverSort_t.SORT_SERVERNAME -> {
                        serv1.serverInfo.GetString("si_name", "", s1)
                        serv2.serverInfo.GetString("si_name", "", s2)
                        return s1.IcmpNoColor(s2)
                    }

                    serverSort_t.SORT_PLAYERS -> {
                        ret = if (serv1.clients < serv2.clients) -1 else if (serv1.clients > serv2.clients) 1 else 0
                        return ret
                    }

                    serverSort_t.SORT_GAMETYPE -> {
                        serv1.serverInfo.GetString("si_gameType", "", s1)
                        serv2.serverInfo.GetString("si_gameType", "", s2)
                        return s1.Icmp(s2)
                    }

                    serverSort_t.SORT_MAP -> {
                        serv1.serverInfo.GetString("si_mapName", "", s1)
                        serv2.serverInfo.GetString("si_mapName", "", s2)
                        return s1.Icmp(s2)
                    }

                    serverSort_t.SORT_GAME -> {
                        serv1.serverInfo.GetString("fs_game", "", s1)
                        serv2.serverInfo.GetString("fs_game", "", s2)
                        return s1.Icmp(s2)
                    }
                }
                return 0
            }
        }

        companion object {
            private const val INCOMING_TIMEOUT =
                1500 // when we got an incoming server list, how long till we decide the list is done
            private const val MAX_PINGREQUESTS = 32 // how many servers to query at once
            private const val REFRESH_START = 10000 // how long to wait when sending the initial refresh request
            private const val REPLY_TIMEOUT = 999 // how long should we wait for a reply from a game server
            val gui_filter_game: idCVar = idCVar(
                "gui_filter_game",
                "0",
                CVarSystem.CVAR_GUI or CVarSystem.CVAR_INTEGER or CVarSystem.CVAR_ARCHIVE,
                "Game filter"
            )
            val gui_filter_gameType: idCVar = idCVar(
                "gui_filter_gameType",
                "0",
                CVarSystem.CVAR_GUI or CVarSystem.CVAR_INTEGER or CVarSystem.CVAR_ARCHIVE,
                "Gametype filter"
            )
            val gui_filter_idle: idCVar = idCVar(
                "gui_filter_idle",
                "0",
                CVarSystem.CVAR_GUI or CVarSystem.CVAR_INTEGER or CVarSystem.CVAR_ARCHIVE,
                "Idle servers filter"
            )
            val gui_filter_password: idCVar = idCVar(
                "gui_filter_password",
                "0",
                CVarSystem.CVAR_GUI or CVarSystem.CVAR_INTEGER or CVarSystem.CVAR_ARCHIVE,
                "Password filter"
            )
            val gui_filter_players: idCVar = idCVar(
                "gui_filter_players",
                "0",
                CVarSystem.CVAR_GUI or CVarSystem.CVAR_INTEGER or CVarSystem.CVAR_ARCHIVE,
                "Players filter"
            )

            //
            val l_gameTypes: Array<String?> = arrayOf(
                "Deathmatch", "Tourney", "Team DM", "Last Man", "CTF", null
            )
            var l_serverScan: idServerScan = idServerScan()
        }

        //
        //
        init {
            m_sort = serverSort_t.SORT_PING
            m_sortAscending = true
            challenge = 0
            net_info = idDict()
            net_servers = idList()
            m_sortedServers = idList()
            screenshot = idStr()
            LocalClear()
        }
    }
}
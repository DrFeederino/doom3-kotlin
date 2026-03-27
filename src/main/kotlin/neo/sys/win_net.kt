/*
===========================================================================

Doom 3 GPL Source Code
Copyright (C) 1999-2011 id Software LLC, a ZeniMax Media company.

This file is part of the Doom 3 GPL Source Code ("Doom 3 Source Code").

Doom 3 Source Code is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

Doom 3 Source Code is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with Doom 3 Source Code.  If not, see <http://www.gnu.org/licenses/>.

In addition, the Doom 3 Source Code is also subject to certain additional terms. You should have received a copy of these additional terms immediately following the terms and conditions of the GNU General Public License which accompanied the Doom 3 Source Code.  If not, please request a copy in writing from id Software at the address below.

If you have questions concerning this license or the applicable additional terms, you may contact in writing id Software LLC, c/o ZeniMax Media Inc., Suite 120, Rockville, Maryland 20850 USA.

===========================================================================
*/
package neo.sys

import neo.TempDump
import neo.TempDump.TODO_Exception
import neo.framework.CVarSystem
import neo.framework.CVarSystem.idCVar
import neo.framework.Common.Companion.common
import neo.idlib.containers.CInt
import java.net.*
import java.nio.ByteBuffer
import java.util.*
import java.util.logging.Level
import java.util.logging.Logger


class win_net {
    class net_interface(
        var ip: Long,
        var mask: Long
    )

    class udpMsg_s {
        var address: netadr_t? = null
        var data: ByteArray = ByteArray(MAX_UDP_MSG_SIZE)
        var next: udpMsg_s? = null
        var size = 0
        var time = 0
    }

    class idUDPLag {
        fun Alloc(): udpMsg_s {
            return udpMsg_s()
        }

        var recieveFirst: udpMsg_s?

        var recieveLast: udpMsg_s? = null
        var sendFirst: udpMsg_s?
        var sendLast: udpMsg_s?

        init {
            recieveFirst = recieveLast
            sendLast = recieveFirst
            sendFirst = sendLast //TODO:check this
        }
    }

    companion object {
        const val MAX_INTERFACES = 32

        //=============================================================================
        const val MAX_UDP_MSG_SIZE = 1400
        val net_forceDrop: idCVar =
            idCVar("net_forceDrop", "0", CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_INTEGER, "percentage packet loss")
        val net_forceLatency: idCVar =
            idCVar("net_forceLatency", "0", CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_INTEGER, "milliseconds latency")
        val net_ip: idCVar = idCVar("net_ip", "localhost", CVarSystem.CVAR_SYSTEM, "local IP address")
        val net_port: idCVar =
            idCVar("net_port", "0", CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_INTEGER, "local IP port number")
        val net_socksEnabled: idCVar =
            idCVar(
                "net_socksEnabled",
                "0",
                CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_ARCHIVE or CVarSystem.CVAR_BOOL,
                ""
            )

        val net_socksPort: idCVar = idCVar(
            "net_socksPort",
            "1080",
            CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_ARCHIVE or CVarSystem.CVAR_INTEGER,
            ""
        )

        val netint: Array<net_interface?> = arrayOfNulls<net_interface?>(MAX_INTERFACES)
        var num_interfaces = 0
        var usingSocks = false
        var winsockInitialized = false

        //=============================================================================
        /*
         ====================
         NET_ErrorString
         ====================
         */
        fun NET_ErrorString(): String {
            throw TODO_Exception()
        }

        /*
         ====================
         Net_NetadrToSockadr
         ====================
         */
        fun Net_NetadrToSockadr(a: netadr_t, s: Array<InetSocketAddress?>) {
            if (a.type == netadrtype_t.NA_BROADCAST) {
                s[0] = InetSocketAddress("255.255.255.255", a.port)
            } else if (a.type == netadrtype_t.NA_IP || a.type == netadrtype_t.NA_LOOPBACK) {
                val ipStr = "${a.ip[0].code}.${a.ip[1].code}.${a.ip[2].code}.${a.ip[3].code}"
                s[0] = InetSocketAddress(ipStr, a.port)
            }
        }

        /*
         ====================
         Net_SockadrToNetadr
         ====================
         */
        fun Net_SockadrToNetadr(s: Array<InetSocketAddress?>, a: netadr_t) {
            val sockAddr = s[0] ?: return
            val addr = sockAddr.address ?: return

            // Extract IPv4 bytes — handle IPv6 addresses by mapping to IPv4
            val ipBytes: ByteArray
            if (addr is Inet6Address) {
                val raw = addr.address
                if (raw.size == 16 && raw[10] == 0xFF.toByte() && raw[11] == 0xFF.toByte()) {
                    // IPv4-mapped IPv6 (::ffff:x.x.x.x) — extract the IPv4 part
                    ipBytes = byteArrayOf(raw[12], raw[13], raw[14], raw[15])
                } else if (addr.isLoopbackAddress) {
                    ipBytes = byteArrayOf(127, 0, 0, 1)
                } else {
                    // Unspecified (::0) or other IPv6 — map to 0.0.0.0
                    ipBytes = byteArrayOf(0, 0, 0, 0)
                }
            } else {
                ipBytes = addr.address
            }

            a.ip[0] = Char(ipBytes[0].toInt() and 0xFF)
            a.ip[1] = Char(ipBytes[1].toInt() and 0xFF)
            a.ip[2] = Char(ipBytes[2].toInt() and 0xFF)
            a.ip[3] = Char(ipBytes[3].toInt() and 0xFF)
            a.port = sockAddr.port

            if (addr.isLoopbackAddress) {
                a.type = netadrtype_t.NA_LOOPBACK
            } else {
                a.type = netadrtype_t.NA_IP
            }
        }

        /*
         =============
         Net_ExtractPort
         =============
         */
        // Returns hostname (without port) in buf, port number in port. False if no ':' found.
        fun Net_ExtractPort(src: String, buf: StringBuilder, port: CInt): Boolean {
            val p = src.indexOf(':')
            if (p == -1) {
                buf.clear().append(src)
                return false
            }
            buf.clear().append(src.substring(0, p))
            port.integerValue = src.substring(p + 1).toIntOrNull() ?: return false
            return true
        }

        /*
         =============
         Net_StringToSockaddr
         =============
         */
        fun Net_StringToSockaddr(s: String, sadr: Array<InetSocketAddress?>, doDNSResolve: Boolean): Boolean {
            val buf = StringBuilder()
            val port = CInt(0)

            if (s[0] in '0'..'9') {
                // numeric IP — try parsing directly first (e.g. "192.168.1.1")
                try {
                    val addr = Inet4Address.getByName(s)
                    sadr[0] = InetSocketAddress(addr, 0)
                    return true
                } catch (_: Exception) {
                }

                // failed — try extracting port (e.g. "192.168.1.1:27666")
                if (!Net_ExtractPort(s, buf, port)) {
                    return false
                }
                try {
                    val addr = Inet4Address.getByName(buf.toString())
                    sadr[0] = InetSocketAddress(addr, port.integerValue)
                    return true
                } catch (_: Exception) {
                    return false
                }
            } else if (doDNSResolve) {
                // hostname — strip port first so DNS doesn't get confused
                Net_ExtractPort(s, buf, port)
                val h = InetSocketAddress(buf.toString(), port.integerValue)
                if (h.isUnresolved) {
                    return false
                }
                sadr[0] = h
            }
            return true
        }


        /*
         ==================
         Net_WaitForUDPPacket
         ==================
         */
        fun Net_WaitForUDPPacket(netSocket: Int, timeout: Int): Boolean {
            throw TODO_Exception()
        }

        /*
         ==================
         Net_GetUDPPacket
         ==================
         */
        fun Net_GetUDPPacket(
            netSocket: DatagramSocket,
            net_from: netadr_t,
            data: ByteArray,
            size: CInt,
            maxSize: Int
        ): Boolean {
            try {
                // C++ uses non-blocking sockets (FIONBIO). Java equivalent: short timeout.
                netSocket.soTimeout = 1
                val datagramPacket = DatagramPacket(data, maxSize)
                netSocket.receive(datagramPacket)

                // populate sender address (C++: Net_SockadrToNetadr(&from, &net_from))
                val fromAddr = InetSocketAddress(
                    datagramPacket.address,
                    datagramPacket.port
                )
                Net_SockadrToNetadr(arrayOf(fromAddr), net_from)

                if (datagramPacket.length == maxSize) {
                    common.Printf(
                        "Net_GetUDPPacket: oversize packet from %s\n",
                        Sys_NetAdrToString(net_from)
                    )
                    return false
                }
                size.integerValue = datagramPacket.length
                return true
            } catch (e: SocketTimeoutException) {
                // no data available — equivalent to C++ WSAEWOULDBLOCK
                return false
            } catch (e: Exception) {
                return false
            }
        }

        /*
         ==================
         Net_SendUDPPacket
         ==================
         */
        fun Net_SendUDPPacket(netSocket: DatagramSocket?, length: Int, data: ByteBuffer, to: netadr_t) {
            if (netSocket == null) {
                return
            }

            val addr = arrayOfNulls<InetSocketAddress>(1)
            Net_NetadrToSockadr(to, addr)
            val dest = addr[0] ?: return
            val packet = DatagramPacket(data.array(), length, dest.address, dest.port)
            try {
                netSocket.send(packet)
            } catch (e: SocketException) {
                // wouldblock is silent in C++
                common.Printf("Net_SendUDPPacket: %s\n", e.message ?: "unknown error")
            }
        }


        /*
         ====================
         Sys_ShutdownNetworking
         ====================
         */
        fun Sys_ShutdownNetworking() {
            if (!winsockInitialized) {
                return
            }
            winsockInitialized = false
        }

        /*
             ==================
             Sys_IsLANAddress
             ==================
             */
        fun Sys_IsLANAddress(adr: netadr_t?): Boolean {
            if (adr == null) return false

            if (adr.type == netadrtype_t.NA_LOOPBACK) {
                return true
            }

            if (adr.type != netadrtype_t.NA_IP) {
                return false
            }

            if (num_interfaces > 0) {
                val ip = TempDump.ntohl(
                    byteArrayOf(
                        adr.ip[0].code.toByte(),
                        adr.ip[1].code.toByte(),
                        adr.ip[2].code.toByte(),
                        adr.ip[3].code.toByte()
                    )
                )

                for (i in 0 until num_interfaces) {
                    val ni = netint[i] ?: continue
                    if ((ni.ip and ni.mask) == (ip and ni.mask)) {
                        return true
                    }
                }
            }
            return false
        }

        /*
     ====================
     Sys_InitNetworking
     ====================
     */
        fun Sys_InitNetworking() {
            winsockInitialized = true
            common.Printf("Winsock Initialized\n")
            val pAdapterInfo: Enumeration<NetworkInterface>
            var pAdapter: NetworkInterface
            var pIPAddrStrings: Enumeration<InetAddress>
            var pIPAddr: InetAddress
            num_interfaces = 0
            try {
                pAdapterInfo =
                    NetworkInterface.getNetworkInterfaces() //if( ( dwRetVal = GetAdaptersInfo( pAdapterInfo, &ulOutBufLen) ) != NO_ERROR ) {
                while (pAdapterInfo.hasMoreElements()) {
                    pAdapter = pAdapterInfo.nextElement()!!
                    common.Printf("Found interface: %s %s - ", pAdapter.name, pAdapter.displayName)
                    pIPAddrStrings = pAdapter.inetAddresses
                    while (pIPAddrStrings.hasMoreElements()) {
                        pIPAddr = pIPAddrStrings.nextElement()

                        var ip_a: Long
                        var ip_m: Long = 0
                        if (pIPAddr is Inet6Address) {
                            continue  //TODO:skip ipv6, for now.
                        }
                        ip_a = TempDump.ntohl(pIPAddr.address)
                        if (pAdapter.interfaceAddresses != null && pAdapter.interfaceAddresses.size > 0) {
                            ip_m = pAdapter.interfaceAddresses[0].networkPrefixLength.toLong()
                        }

                        //skip null netmasks
                        if (ip_m == 0L) {
                            common.Printf("%s NULL netmask - skipped", pIPAddr.hostAddress)
                            //                        pIPAddr = pIPAddr.Next;
                            continue
                        }
                        common.Printf("%s/%s", pIPAddr.hostAddress, ip_m)
                        netint[num_interfaces] = net_interface(ip_a, ip_m)
                        num_interfaces++
                        if (num_interfaces >= MAX_INTERFACES) {
                            common.Printf(
                                "\nSys_InitNetworking: MAX_INTERFACES(%d) hit.\n",
                                MAX_INTERFACES
                            )
                            //                            free( pAdapterInfo );
                            return
                        }
                    }
                    common.Printf("\n")
                }
            } catch (ex: SocketException) {
                Logger.getLogger(win_net::class.java.name).log(Level.SEVERE, null, ex)
                // happens if you have no network connection
                common.Printf("Sys_InitNetworking: GetAdaptersInfo failed (%ld).\n", -1 /*dwRetVal*/)
            }
        }

        /*
        =============
        Sys_StringToNetAdr
        =============
        */
        fun Sys_StringToNetAdr(s: String?, a: netadr_t?, doDNSResolve: Boolean): Boolean {
            var sadr = arrayOfNulls<InetSocketAddress>(1)
            if (!Net_StringToSockaddr(s!!, sadr, doDNSResolve)) {
                return false
            }
            Net_SockadrToNetadr(sadr, a!!)
            return true
        }

        /*
     =============
     Sys_NetAdrToString
     =============
     */
        fun Sys_NetAdrToString(a: netadr_t): String {
            return if (a.type == netadrtype_t.NA_LOOPBACK) {
                if (a.port != 0) {
                    String.format("localhost:%d", a.port)
                } else {
                    "localhost"
                }
            } else if (a.type == netadrtype_t.NA_IP) {
                String.format(
                    "%d.%d.%d.%d:%d",
                    a.ip[0].code, a.ip[1].code, a.ip[2].code, a.ip[3].code, a.port
                )
            } else {
                ""
            }
        }

        /*
         ===================
         Sys_CompareNetAdrBase

         Compares without the port
         ===================
         */
        fun Sys_CompareNetAdrBase(a: netadr_t?, b: netadr_t?): Boolean {
            if (a == null || b == null) return false

            if (a.type != b.type) {
                return false
            }

            if (a.type == netadrtype_t.NA_LOOPBACK) {
                return true
            }

            if (a.type == netadrtype_t.NA_IP) {
                return a.ip[0] == b.ip[0] && a.ip[1] == b.ip[1] && a.ip[2] == b.ip[2] && a.ip[3] == b.ip[3]
            }

            common.Printf("Sys_CompareNetAdrBase: bad address type\n")
            return false
        }

        /*
         ====================
         NET_IPSocket
         ====================
         */
        fun IPSocket(net_interface: String, port: Int, bound_to: netadr_t?): DatagramSocket? {
            if (net_interface.isNotEmpty()) {
                common.DPrintf("Opening IP socket: %s:%d\n", net_interface, port)
            } else {
                common.DPrintf("Opening IP socket: localhost:%d\n", port)
            }
            var newSocket: DatagramSocket? = null
            try {
                // C++: if empty or "localhost", bind to INADDR_ANY; otherwise resolve interface
                val bindAddr: InetAddress =
                    if (net_interface.isEmpty() || net_interface.equals("localhost", ignoreCase = true)) {
                        Inet4Address.getByName("0.0.0.0") // INADDR_ANY, force IPv4
                    } else {
                        Inet4Address.getByName(net_interface)
                    }
                val bindPort = if (port == PORT_ANY) 0 else port
                val address = InetSocketAddress(bindAddr, bindPort)

                newSocket = DatagramSocket(null) // create unbound socket
                newSocket.setOption(StandardSocketOptions.SO_BROADCAST, true)
                newSocket.bind(address) // bind to IPv4 address

                if (bound_to != null) {
                    // query the actual bound address (important when port was PORT_ANY)
                    Net_SockadrToNetadr(arrayOf(newSocket.localSocketAddress as InetSocketAddress), bound_to)
                }
                return newSocket
            } catch (e: UnknownHostException) {
                common.Printf("WARNING: Socket creation error occurred: %s\n", e.localizedMessage)
                newSocket?.close()
                return null
            } catch (e: SocketException) {
                common.Printf("ERROR: IPSocket: bind: %s\n", e.message!!)
                newSocket?.close()
                return null
            }
        }

        /*
         ====================
         NET_OpenSocks
         ====================
         */
        fun NET_OpenSocks(port: Int) {
            throw TODO_Exception()
        }
    }
}
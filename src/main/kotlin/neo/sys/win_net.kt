/*
 * Copyright (C) 1999-2011 id Software LLC, a ZeniMax Media company.
 * Translated to Kotlin by Dr. Feederino with support of Claude Code
 *
 * This file is part of the Doom 3 Kotlin project.
 * Original source: neo/sys/win32/win_net.cpp
 *
 * NOTE: Differs from C++ — The original C++ uses Winsock2 (WSA) for networking.
 * This Kotlin port uses java.net (DatagramSocket, InetAddress, etc.).
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

package neo.sys

import neo.TempDump
import neo.TempDump.TODO_Exception
import neo.framework.CVarSystem
import neo.framework.CVarSystem.idCVar
import neo.framework.Common.Companion.common
import neo.idlib.containers.CInt
import neo.sys.sys_public.netadr_t
import java.net.*
import java.nio.ByteBuffer
import java.util.*
import java.util.logging.Level
import java.util.logging.Logger


class win_net {
    class net_interface(/*unsigned*/
                        var ip: Long, /*unsigned*/
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

        //						~idUDPLag( void );
        var recieveLast: udpMsg_s? = null
        var sendFirst: udpMsg_s?
        var sendLast: udpMsg_s?

        init {
            recieveFirst = recieveLast
            sendLast = recieveFirst
            sendFirst = sendLast //TODO:check this
        } //        public idBlockAlloc<udpMsg_t> udpMsgAllocator = new idBlockAlloc(64);
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

        //        val net_socksPassword: idCVar =
//            idCVar("net_socksPassword", "", CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_ARCHIVE, "")
        val net_socksPort: idCVar = idCVar(
            "net_socksPort",
            "1080",
            CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_ARCHIVE or CVarSystem.CVAR_INTEGER,
            ""
        )

        //        val net_socksServer: idCVar =
//            idCVar("net_socksServer", "", CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_ARCHIVE, "")
        //val net_socksUsername: idCVar =
        //    idCVar("net_socksUsername", "", CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_ARCHIVE, "")
        val netint: Array<net_interface?> = arrayOfNulls<net_interface?>(MAX_INTERFACES)
        var num_interfaces = 0
        var usingSocks = false

        //    static WSADATA winsockdata;
        var winsockInitialized = false

        //=============================================================================
        /*
         ====================
         NET_ErrorString
         ====================
         */
        fun NET_ErrorString(): String {
            throw TODO_Exception()
            //	int		code;
//
//	code = WSAGetLastError();
//	switch( code ) {
//	case WSAEINTR: return "WSAEINTR";
//	case WSAEBADF: return "WSAEBADF";
//	case WSAEACCES: return "WSAEACCES";
//	case WSAEDISCON: return "WSAEDISCON";
//	case WSAEFAULT: return "WSAEFAULT";
//	case WSAEINVAL: return "WSAEINVAL";
//	case WSAEMFILE: return "WSAEMFILE";
//	case WSAEWOULDBLOCK: return "WSAEWOULDBLOCK";
//	case WSAEINPROGRESS: return "WSAEINPROGRESS";
//	case WSAEALREADY: return "WSAEALREADY";
//	case WSAENOTSOCK: return "WSAENOTSOCK";
//	case WSAEDESTADDRREQ: return "WSAEDESTADDRREQ";
//	case WSAEMSGSIZE: return "WSAEMSGSIZE";
//	case WSAEPROTOTYPE: return "WSAEPROTOTYPE";
//	case WSAENOPROTOOPT: return "WSAENOPROTOOPT";
//	case WSAEPROTONOSUPPORT: return "WSAEPROTONOSUPPORT";
//	case WSAESOCKTNOSUPPORT: return "WSAESOCKTNOSUPPORT";
//	case WSAEOPNOTSUPP: return "WSAEOPNOTSUPP";
//	case WSAEPFNOSUPPORT: return "WSAEPFNOSUPPORT";
//	case WSAEAFNOSUPPORT: return "WSAEAFNOSUPPORT";
//	case WSAEADDRINUSE: return "WSAEADDRINUSE";
//	case WSAEADDRNOTAVAIL: return "WSAEADDRNOTAVAIL";
//	case WSAENETDOWN: return "WSAENETDOWN";
//	case WSAENETUNREACH: return "WSAENETUNREACH";
//	case WSAENETRESET: return "WSAENETRESET";
//	case WSAECONNABORTED: return "WSWSAECONNABORTEDAEINTR";
//	case WSAECONNRESET: return "WSAECONNRESET";
//	case WSAENOBUFS: return "WSAENOBUFS";
//	case WSAEISCONN: return "WSAEISCONN";
//	case WSAENOTCONN: return "WSAENOTCONN";
//	case WSAESHUTDOWN: return "WSAESHUTDOWN";
//	case WSAETOOMANYREFS: return "WSAETOOMANYREFS";
//	case WSAETIMEDOUT: return "WSAETIMEDOUT";
//	case WSAECONNREFUSED: return "WSAECONNREFUSED";
//	case WSAELOOP: return "WSAELOOP";
//	case WSAENAMETOOLONG: return "WSAENAMETOOLONG";
//	case WSAEHOSTDOWN: return "WSAEHOSTDOWN";
//	case WSASYSNOTREADY: return "WSASYSNOTREADY";
//	case WSAVERNOTSUPPORTED: return "WSAVERNOTSUPPORTED";
//	case WSANOTINITIALISED: return "WSANOTINITIALISED";
//	case WSAHOST_NOT_FOUND: return "WSAHOST_NOT_FOUND";
//	case WSATRY_AGAIN: return "WSATRY_AGAIN";
//	case WSANO_RECOVERY: return "WSANO_RECOVERY";
//	case WSANO_DATA: return "WSANO_DATA";
//	default: return "NO ERROR";
//	}
        }

        /*
         ====================
         Net_NetadrToSockadr
         ====================
         */
        fun Net_NetadrToSockadr(a: netadr_t, s: Array<InetSocketAddress?>) {
            if (a.type == sys_public.netadrtype_t.NA_BROADCAST) {
                s[0] = InetSocketAddress("255.255.255.255", a.port)
            } else if (a.type == sys_public.netadrtype_t.NA_IP || a.type == sys_public.netadrtype_t.NA_LOOPBACK) {
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
                a.type = sys_public.netadrtype_t.NA_LOOPBACK
            } else {
                a.type = sys_public.netadrtype_t.NA_IP
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
            port._val = src.substring(p + 1).toIntOrNull() ?: return false
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
                    sadr[0] = InetSocketAddress(addr, port._val)
                    return true
                } catch (_: Exception) {
                    return false
                }
            } else if (doDNSResolve) {
                // hostname — strip port first so DNS doesn't get confused
                Net_ExtractPort(s, buf, port)
                val h = InetSocketAddress(buf.toString(), port._val)
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
            //	int					ret;
//	fd_set				set;
//	struct timeval		tv;
//
//	if ( !netSocket ) {
//		return false;
//	}
//
//	if ( timeout <= 0 ) {
//		return true;
//	}
//
//	FD_ZERO( &set );
//	FD_SET( netSocket, &set );
//
//	tv.tv_sec = 0;
//	tv.tv_usec = timeout * 1000;
//
//	ret = select( netSocket + 1, &set, NULL, NULL, &tv );
//
//	if ( ret == -1 ) {
//		common->DPrintf( "Net_WaitForUPDPacket select(): %s\n", strerror( errno ) );
//		return false;
//	}
//
//	// timeout with no data
//	if ( ret == 0 ) {
//		return false;
//	}
//
//	return true;
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
                size._val = datagramPacket.length
                return true
            } catch (e: SocketTimeoutException) {
                // no data available — equivalent to C++ WSAEWOULDBLOCK
                return false
            } catch (e: Exception) {
                return false
            }

            //	int 			ret;
//	struct sockaddr	from;
//	int				fromlen;
//	int				err;
//
//	if( !netSocket ) {
//		return false;
//	}
//
//	fromlen = sizeof(from);
//	ret = recvfrom( netSocket, data, maxSize, 0, (struct sockaddr *)&from, &fromlen );
//	if ( ret == SOCKET_ERROR ) {
//		err = WSAGetLastError();
//
//		if( err == WSAEWOULDBLOCK || err == WSAECONNRESET ) {
//			return false;
//		}
//		char	buf[1024];
//		sprintf( buf, "Net_GetUDPPacket: %s\n", NET_ErrorString() );
//		OutputDebugString( buf );
//		return false;
//	}
//
//	if ( netSocket == ip_socket ) {
//		memset( ((struct sockaddr_in *)&from)->sin_zero, 0, 8 );
//	}
//
//	if ( usingSocks && netSocket == ip_socket && memcmp( &from, &socksRelayAddr, fromlen ) == 0 ) {
//		if ( ret < 10 || data[0] != 0 || data[1] != 0 || data[2] != 0 || data[3] != 1 ) {
//			return false;
//		}
//		net_from.type = NA_IP;
//		net_from.ip[0] = data[4];
//		net_from.ip[1] = data[5];
//		net_from.ip[2] = data[6];
//		net_from.ip[3] = data[7];
//		net_from.port = *(short *)&data[8];
//		memmove( data, &data[10], ret - 10 );
//	} else {
//		Net_SockadrToNetadr( &from, &net_from );
//	}
//
//	if( ret == maxSize ) {
//		char	buf[1024];
//		sprintf( buf, "Net_GetUDPPacket: oversize packet from %s\n", Sys_NetAdrToString( net_from ) );
//		OutputDebugString( buf );
//		return false;
//	}
//
//	size = ret;
//
//	return true;
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
        // NOTE: Differs from C++ — C++ calls WSACleanup(). Java manages sockets via GC.
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

            if (adr.type == sys_public.netadrtype_t.NA_LOOPBACK) {
                return true
            }

            if (adr.type != sys_public.netadrtype_t.NA_IP) {
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
            //
//        r = WSAStartup(MAKEWORD(1, 1),  & winsockdata);
//        if (r) {
//            common.Printf("WARNING: Winsock initialization failed, returned %d\n", r);
//            return;
//        }
//
            winsockInitialized = true
            common.Printf("Winsock Initialized\n")
            val   /*PIP_ADAPTER_INFO*/pAdapterInfo: Enumeration<NetworkInterface>
            var   /*PIP_ADAPTER_INFO*/pAdapter: NetworkInterface
            //        DWORD dwRetVal = 0;
            var   /*PIP_ADDR_STRING*/pIPAddrStrings: Enumeration<InetAddress>
            var pIPAddr: InetAddress
            //        ULONG ulOutBufLen;
//        boolean foundLoopback;
            num_interfaces = 0
            //        foundLoopback = false;
//
//	pAdapterInfo = (IP_ADAPTER_INFO *)malloc( sizeof( IP_ADAPTER_INFO ) );
//	if( !pAdapterInfo ) {
//		common.FatalError( "Sys_InitNetworking: Couldn't malloc( %d )", sizeof( IP_ADAPTER_INFO ) );
//	}
//	ulOutBufLen = sizeof( IP_ADAPTER_INFO );
//
//	// Make an initial call to GetAdaptersInfo to get
//	// the necessary size into the ulOutBufLen variable
//	if( GetAdaptersInfo( pAdapterInfo, &ulOutBufLen ) == ERROR_BUFFER_OVERFLOW ) {
//		free( pAdapterInfo );
//		pAdapterInfo = (IP_ADAPTER_INFO *)malloc( ulOutBufLen );
//		if( !pAdapterInfo ) {
//			common.FatalError( "Sys_InitNetworking: Couldn't malloc( %ld )", ulOutBufLen );
//		}
//	}
//
            try {
                pAdapterInfo =
                    NetworkInterface.getNetworkInterfaces() //if( ( dwRetVal = GetAdaptersInfo( pAdapterInfo, &ulOutBufLen) ) != NO_ERROR ) {
                while (pAdapterInfo.hasMoreElements()) {
                    pAdapter = pAdapterInfo.nextElement()!!
                    common.Printf("Found interface: %s %s - ", pAdapter.name, pAdapter.displayName)
                    pIPAddrStrings = pAdapter.inetAddresses
                    while (pIPAddrStrings.hasMoreElements()) {
                        pIPAddr = pIPAddrStrings.nextElement()
                        /*unsigned*/
                        var ip_a: Long
                        var ip_m: Long = 0
                        if (pIPAddr is Inet6Address) {
                            continue  //TODO:skip ipv6, for now.
                        }
                        //                        if (!idStr.Icmp("127.0f.0f.1", pIPAddrString.IpAddress.String)) {
//                            foundLoopback = true;
//                        }
//                    foundLoopback |= pIPAddr.isLoopbackAddress();
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

//        //TODO: check if java is as retarded as win32.
//        // for some retarded reason, win32 doesn't count loopback as an adapter...
//        if (!foundLoopback && num_interfaces < MAX_INTERFACES) {
//            common.Printf("Sys_InitNetworking: adding loopback interface\n");
//            netint[num_interfaces].ip = ntohl(inet_addr("127.0f.0f.1"));
//            netint[num_interfaces].mask = ntohl(inet_addr("255.0f.0f.0f"));
//            num_interfaces++;
//        }
//            free( pAdapterInfo );
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
            return if (a.type == sys_public.netadrtype_t.NA_LOOPBACK) {
                if (a.port != 0) {
                    String.format("localhost:%d", a.port)
                } else {
                    "localhost"
                }
            } else if (a.type == sys_public.netadrtype_t.NA_IP) {
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

            if (a.type == sys_public.netadrtype_t.NA_LOOPBACK) {
                return true
            }

            if (a.type == sys_public.netadrtype_t.NA_IP) {
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
                val bindPort = if (port == sys_public.PORT_ANY) 0 else port
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
            //	struct sockaddr_in	address;
//	int					err;
//	struct hostent		*h;
//	int					len;
//	bool			rfc1929;
//	unsigned char		buf[64];
//
//	usingSocks = false;
//
//	common->Printf( "Opening connection to SOCKS server.\n" );
//
//	if ( ( socks_socket = socket( AF_INET, SOCK_STREAM, IPPROTO_TCP ) ) == INVALID_SOCKET ) {
//		err = WSAGetLastError();
//		common->Printf( "WARNING: NET_OpenSocks: socket: %s\n", NET_ErrorString() );
//		return;
//	}
//
//	h = gethostbyname( net_socksServer.GetString() );
//	if ( h == NULL ) {
//		err = WSAGetLastError();
//		common->Printf( "WARNING: NET_OpenSocks: gethostbyname: %s\n", NET_ErrorString() );
//		return;
//	}
//	if ( h->h_addrtype != AF_INET ) {
//		common->Printf( "WARNING: NET_OpenSocks: gethostbyname: address type was not AF_INET\n" );
//		return;
//	}
//	address.sin_family = AF_INET;
//	address.sin_addr.s_addr = *(int *)h->h_addr_list[0];
//	address.sin_port = htons( (short)net_socksPort.GetInteger() );
//
//	if ( connect( socks_socket, (struct sockaddr *)&address, sizeof( address ) ) == SOCKET_ERROR ) {
//		err = WSAGetLastError();
//		common->Printf( "NET_OpenSocks: connect: %s\n", NET_ErrorString() );
//		return;
//	}
//
//	// send socks authentication handshake
//	if ( *net_socksUsername.GetString() || *net_socksPassword.GetString() ) {
//		rfc1929 = true;
//	}
//	else {
//		rfc1929 = false;
//	}
//
//	buf[0] = 5;		// SOCKS version
//	// method count
//	if ( rfc1929 ) {
//		buf[1] = 2;
//		len = 4;
//	}
//	else {
//		buf[1] = 1;
//		len = 3;
//	}
//	buf[2] = 0;		// method #1 - method id #00: no authentication
//	if ( rfc1929 ) {
//		buf[2] = 2;		// method #2 - method id #02: username/password
//	}
//	if ( send( socks_socket, (const char *)buf, len, 0 ) == SOCKET_ERROR ) {
//		err = WSAGetLastError();
//		common->Printf( "NET_OpenSocks: send: %s\n", NET_ErrorString() );
//		return;
//	}
//
//	// get the response
//	len = recv( socks_socket, (char *)buf, 64, 0 );
//	if ( len == SOCKET_ERROR ) {
//		err = WSAGetLastError();
//		common->Printf( "NET_OpenSocks: recv: %s\n", NET_ErrorString() );
//		return;
//	}
//	if ( len != 2 || buf[0] != 5 ) {
//		common->Printf( "NET_OpenSocks: bad response\n" );
//		return;
//	}
//	switch( buf[1] ) {
//	case 0:	// no authentication
//		break;
//	case 2: // username/password authentication
//		break;
//	default:
//		common->Printf( "NET_OpenSocks: request denied\n" );
//		return;
//	}
//
//	// do username/password authentication if needed
//	if ( buf[1] == 2 ) {
//		int		ulen;
//		int		plen;
//
//		// build the request
//		ulen = strlen( net_socksUsername.GetString() );
//		plen = strlen( net_socksPassword.GetString() );
//
//		buf[0] = 1;		// username/password authentication version
//		buf[1] = ulen;
//		if ( ulen ) {
//			memcpy( &buf[2], net_socksUsername.GetString(), ulen );
//		}
//		buf[2 + ulen] = plen;
//		if ( plen ) {
//			memcpy( &buf[3 + ulen], net_socksPassword.GetString(), plen );
//		}
//
//		// send it
//		if ( send( socks_socket, (const char *)buf, 3 + ulen + plen, 0 ) == SOCKET_ERROR ) {
//			err = WSAGetLastError();
//			common->Printf( "NET_OpenSocks: send: %s\n", NET_ErrorString() );
//			return;
//		}
//
//		// get the response
//		len = recv( socks_socket, (char *)buf, 64, 0 );
//		if ( len == SOCKET_ERROR ) {
//			err = WSAGetLastError();
//			common->Printf( "NET_OpenSocks: recv: %s\n", NET_ErrorString() );
//			return;
//		}
//		if ( len != 2 || buf[0] != 1 ) {
//			common->Printf( "NET_OpenSocks: bad response\n" );
//			return;
//		}
//		if ( buf[1] != 0 ) {
//			common->Printf( "NET_OpenSocks: authentication failed\n" );
//			return;
//		}
//	}
//
//	// send the UDP associate request
//	buf[0] = 5;		// SOCKS version
//	buf[1] = 3;		// command: UDP associate
//	buf[2] = 0;		// reserved
//	buf[3] = 1;		// address type: IPV4
//	*(int *)&buf[4] = INADDR_ANY;
//	*(short *)&buf[8] = htons( (short)port );		// port
//	if ( send( socks_socket, (const char *)buf, 10, 0 ) == SOCKET_ERROR ) {
//		err = WSAGetLastError();
//		common->Printf( "NET_OpenSocks: send: %s\n", NET_ErrorString() );
//		return;
//	}
//
//	// get the response
//	len = recv( socks_socket, (char *)buf, 64, 0 );
//	if( len == SOCKET_ERROR ) {
//		err = WSAGetLastError();
//		common->Printf( "NET_OpenSocks: recv: %s\n", NET_ErrorString() );
//		return;
//	}
//	if( len < 2 || buf[0] != 5 ) {
//		common->Printf( "NET_OpenSocks: bad response\n" );
//		return;
//	}
//	// check completion code
//	if( buf[1] != 0 ) {
//		common->Printf( "NET_OpenSocks: request denied: %i\n", buf[1] );
//		return;
//	}
//	if( buf[3] != 1 ) {
//		common->Printf( "NET_OpenSocks: relay address is not IPV4: %i\n", buf[3] );
//		return;
//	}
//	((struct sockaddr_in *)&socksRelayAddr)->sin_family = AF_INET;
//	((struct sockaddr_in *)&socksRelayAddr)->sin_addr.s_addr = *(int *)&buf[4];
//	((struct sockaddr_in *)&socksRelayAddr)->sin_port = *(short *)&buf[8];
//	memset( ((struct sockaddr_in *)&socksRelayAddr)->sin_zero, 0, 8 );
//
//	usingSocks = true;
        }
    }
}
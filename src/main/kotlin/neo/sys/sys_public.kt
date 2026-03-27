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
import neo.TempDump.SERiAL
import neo.framework.Common.Companion.common
import neo.idlib.containers.CInt
import neo.idlib.containers.idStrList
import neo.sys.sys_local.idSysLocal
import neo.sys.win_net.Companion.MAX_UDP_MSG_SIZE
import neo.sys.win_net.Companion.Net_GetUDPPacket
import neo.sys.win_net.Companion.Net_SendUDPPacket
import neo.sys.win_net.Companion.net_forceDrop
import neo.sys.win_net.Companion.net_forceLatency
import neo.sys.win_net.idUDPLag
import neo.sys.win_shared.Sys_Milliseconds
import java.net.DatagramSocket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*

const val BUILD_OS_ID = 0 //BUILD_OS_ID = 1 for linux
val BUILD_STRING: String = "win-x86" // "linux-x86")
const val CPUID_3DNOW = 0x00020 // 3DNow!
const val CPUID_ALTIVEC = 0x00200 // AltiVec
const val CPUID_AMD = 0x00008 // AMD
const val CPUID_CMOV = 0x02000 // Conditional Move (CMOV) and fast floating point comparison (FCOMI) instructions
const val CPUID_DAZ = 0x08000 // Denormals-Are-Zero mode (denormal source operands are set to zero)
const val CPUID_FTZ = 0x04000 // Flush-To-Zero mode (denormal results are flushed to zero)
const val CPUID_GENERIC = 0x00002 // unrecognized processor
const val CPUID_HTT = 0x01000 // Hyper-Threading Technology
const val CPUID_INTEL = 0x00004 // Intel
const val CPUID_MMX = 0x00010 // Multi Media Extensions

const val CPUID_NONE = 0x00000
const val CPUID_SSE = 0x00040 // Streaming SIMD Extensions
const val CPUID_SSE2 = 0x00080 // Streaming SIMD Extensions 2
const val CPUID_SSE3 = 0x00100 // Streaming SIMD Extentions 3 aka Prescott's New Instructions
const val CPUID_UNSUPPORTED = 0x00001 // unsupported (386/486)
const val CRITICAL_SECTION_ONE = 1
const val CRITICAL_SECTION_THREE = 3
const val CRITICAL_SECTION_TWO = 2
const val RAND_MAX = 32767

// enum {
const val CRITICAL_SECTION_ZERO = 0
val PATHSEPERATOR_CHAR: Char = '/'

//
val PATHSEPERATOR_STR: String = "/"
const val PORT_ANY = -1
const val TRIGGER_EVENT_ONE = 1
val g_thread_count: IntArray = intArrayOf(0)
val CPUSTRING: String = "x86"
const val CPU_EASYARGS = 1
const val FPU_EXCEPTION_DENORMALIZED_OPERAND = 2
const val FPU_EXCEPTION_DIVIDE_BY_ZERO = 4
const val FPU_EXCEPTION_INEXACT_RESULT = 32
const val FPU_EXCEPTION_INVALID_OPERATION = 1
const val FPU_EXCEPTION_NUMERIC_OVERFLOW = 8
const val FPU_EXCEPTION_NUMERIC_UNDERFLOW = 16
const val MAX_CRITICAL_SECTIONS = 4
const val MAX_THREADS = 10

//    val g_threads: Array<xthreadInfo> = Array(MAX_THREADS) { xthreadInfo() }
const val MAX_TRIGGER_EVENTS = 4
const val TRIGGER_EVENT_THREE = 3
const val TRIGGER_EVENT_TWO = 2

// enum {
const val TRIGGER_EVENT_ZERO = 0
val udpPorts: Array<idUDPLag?> = arrayOfNulls(65536)
var sys: idSys = sys_local.sysLocal

// use fs_debug to verbose Sys_ListFiles
// returns -1 if directory was not found (the list is cleared)
fun Sys_ListFiles(directory: String, extension: String, list: idStrList): Int {
    return win_main.Sys_ListFiles(directory, extension, list)
}

fun setSysLocal(sys: idSys) {
    sys_local.sysLocal = sys.also { neo.sys.sys = it } as idSysLocal
}

internal enum class fpuPrecision_t {
    FPU_PRECISION_SINGLE,
    FPU_PRECISION_DOUBLE,
    FPU_PRECISION_DOUBLE_EXTENDED
}

internal enum class fpuRounding_t {
    FPU_ROUNDING_TO_NEAREST,
    FPU_ROUNDING_DOWN,
    FPU_ROUNDING_UP,
    FPU_ROUNDING_TO_ZERO
}

enum class joystickAxis_t {
    AXIS_SIDE,
    AXIS_FORWARD,
    AXIS_UP,
    AXIS_ROLL,
    AXIS_YAW,
    AXIS_PITCH,
    MAX_JOYSTICK_AXIS
}

/*
 ==============================================================

 Networking

 ==============================================================
 */
enum class netadrtype_t {
    NA_BAD,  // an address lookup failed
    NA_LOOPBACK,
    NA_BROADCAST,
    NA_IP
}

enum class sysEventType_t {
    SE_NONE,  // evTime is still valid
    SE_KEY,  // evValue is a key code, evValue2 is the down flag
    SE_CHAR,  // evValue is an ascii char
    SE_MOUSE,  // evValue and evValue2 are reletive signed x / y moves
    SE_MOUSE_ABS,            // evValue and evValue2 are absolute x / y coordinates in the window
    SE_JOYSTICK_AXIS,  // evValue is an axis number and evValue2 is the current state (-127 to 127)
    SE_CONSOLE // evPtr is a char*, from typing something at a non-game console
}

enum class sys_mEvents {
    M_ACTION1,
    M_ACTION2,
    M_ACTION3,
    M_ACTION4,
    M_ACTION5,
    M_ACTION6,
    M_ACTION7,
    M_ACTION8,
    M_DELTAX,
    M_DELTAY,
    M_DELTAZ
}

enum class xthreadPriority {
    THREAD_NORMAL,
    THREAD_ABOVE_NORMAL,
    THREAD_HIGHEST
}

class sysEvent_s : SERiAL {
    var evPtr // this must be manually freed if not NULL
            : ByteBuffer? = null
    var evPtrLength // bytes of data pointed to by evPtr, for journaling
            = 0
    var evType: sysEventType_t = sysEventType_t.entries[0]
    var evValue = 0
    var evValue2 = 0

    //TODO:is a byteBuffer necessary? we seem to always be converting it to a string.
    constructor()
    constructor(event: ByteBuffer) {
        Read(event)
    }

    override fun AllocBuffer(): ByteBuffer {
        return ByteBuffer.allocate(BYTES)
    }

    override fun Read(buffer: ByteBuffer) {
        buffer.order(ByteOrder.LITTLE_ENDIAN).rewind()
        evType = sysEventType_t.values()[buffer.int]
        evValue = buffer.int
        evValue2 = buffer.int
        evPtrLength = buffer.int
        buffer.int //death to the pointer
    }

    override fun Write(): ByteBuffer {
        val buffer = AllocBuffer()
        buffer.putInt(evType.ordinal)
        buffer.putInt(evValue)
        buffer.putInt(evValue2)
        buffer.putInt(evPtrLength)
        buffer.putInt(0x50) //P for pointer
        return buffer
    }

    companion object {
        @Transient
        private val SIZE = (TempDump.CPP_class.Enum.SIZE
                + Integer.SIZE
                + Integer.SIZE
                + Integer.SIZE
                + TempDump.CPP_class.Pointer.SIZE)

        @Transient
        val BYTES = SIZE / 8
    }
}

class sysMemoryStats_s {
    var availExtendedVirtual = 0
    var availPageFile = 0
    var availPhysical = 0
    var availVirtual = 0
    var memoryLoad = 0
    var totalPageFile = 0
    var totalPhysical = 0
    var totalVirtual = 0
}

class netadr_t {
    var ip: CharArray = CharArray(4)
    var port: Int = 0
    var type: netadrtype_t = netadrtype_t.NA_BAD
    fun oSet(address: netadr_t) {
        type = address.type
        for (i in 0 until ip.size) {
            ip[i] = address.ip[i]
        }
        port = address.port
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) return true
        if (o !is netadr_t) return false
        val netadr_t = o
        if (port != netadr_t.port) return false
        return if (!Arrays.equals(ip, netadr_t.ip)) false else type == netadr_t.type
    }

    override fun hashCode(): Int {
        var result = Arrays.hashCode(ip)
        result = 31 * result + port
        result = 31 * result + type.hashCode()
        return result
    }
}

class idPort {
    var bytesRead = 0
    var bytesWritten = 0
    var packetsRead = 0
    var packetsWritten = 0
    private var bound_to // interface and port
            : netadr_t = netadr_t()
    private var netSocket: DatagramSocket? = null // OS specific socket

    // virtual		~idPort();
    // if the InitForPort fails, the idPort.port field will remain 0
    fun InitForPort(portNumber: Int): Boolean {
        netSocket = win_net.IPSocket(win_net.net_ip.GetString()!!, portNumber, bound_to)
        if (netSocket == null) {
            netSocket = null
            bound_to = netadr_t() // memset( &bound_to, 0, sizeof( bound_to ) );
            return false
        }
        udpPorts[bound_to.port] = idUDPLag()

        return true
    }

    fun GetPort(): Int {
        return bound_to.port
    }

    fun GetAdr(): netadr_t {
        return bound_to
    }

    fun Close() {
        if (netSocket != null) {
            if (udpPorts[bound_to.port] != null) {
                udpPorts[bound_to.port] = null // delete udpPorts[bound_to.port ];
            }
            closesocket(netSocket) //TODO:
            netSocket = null
            bound_to = netadr_t() // memset(bound_to, 0, sizeof(bound_to));
        }
    }

    private fun closesocket(netSocket: DatagramSocket?) {
        netSocket?.close()
    }

    fun GetPacket(from: netadr_t, data: ByteBuffer, size: CInt, maxSize: Int): Boolean {
        var ret: Boolean

        while (true) {
            ret = Net_GetUDPPacket(netSocket!!, from, data.array(), size, maxSize)
            if (!ret) {
                break
            }

            if (net_forceDrop.GetInteger() > 0) {
                if (Random().nextInt(100) < net_forceDrop.GetInteger()) {
                    continue
                }
            }

            packetsRead++
            bytesRead += size.integerValue

            if (net_forceLatency.GetInteger() > 0) {
                val msg: win_net.udpMsg_s = udpPorts[bound_to.port]!!.Alloc()
                msg.size = size.integerValue
                msg.address = from
                msg.time = Sys_Milliseconds()
                msg.next = null
                if (udpPorts[bound_to.port]?.recieveLast != null) {
                    udpPorts[bound_to.port]?.recieveLast?.next = msg
                } else {
                    udpPorts[bound_to.port]?.recieveFirst = msg
                }
                udpPorts[bound_to.port]?.recieveLast = msg
            } else {
                break
            }
        }

        if (net_forceLatency.GetInteger() > 0 || (udpPorts[bound_to.port] != null && udpPorts[bound_to.port]?.recieveFirst != null)) {

            val msg = udpPorts[bound_to.port]?.recieveFirst
            if (msg != null && msg.time <= Sys_Milliseconds() - net_forceLatency.GetInteger()) {
                System.arraycopy(msg.data, 0, data, 0, msg.size)
                size.integerValue = msg.size
                from.oSet(msg.address!!)
                udpPorts[bound_to.port]?.recieveFirst = udpPorts[bound_to.port]?.recieveFirst?.next
                if (udpPorts[bound_to.port]?.recieveFirst != null) {
                    udpPorts[bound_to.port]?.recieveLast = null
                }
                // Not needed
                //udpPorts[ bound_to.port ].udpMsgAllocator.Free( msg )
                return true
            }
            return false

        } else {
            return ret
        }
    }

    fun GetPacketBlocking(from: netadr_t, data: ByteBuffer, size: CInt, maxSize: Int, timeout: Int): Boolean {
        return GetPacket(from, data, size, maxSize)
    }

    fun SendPacket(to: netadr_t, data: ByteBuffer, size: Int) {
        if (to.type == netadrtype_t.NA_BAD) {
            common.Warning("idPort::SendPacket: bad address type NA_BAD - ignored")
            return
        }


        packetsWritten++
        bytesWritten += size

        if (net_forceDrop.GetInteger() > 0) {
            if (Random().nextInt(100) < net_forceDrop.GetInteger()) {
                return
            }
        }

        if (net_forceLatency.GetInteger() > 0 || (udpPorts[bound_to.port] != null && udpPorts[bound_to.port]?.sendFirst != null)) {
            assert(size <= MAX_UDP_MSG_SIZE)
            var msg: win_net.udpMsg_s? = udpPorts[bound_to.port]!!.Alloc()
            System.arraycopy(data.array(), 0, msg!!.data, 0, size)
            msg.size = size
            msg.address = to
            msg.time = Sys_Milliseconds()
            msg.next = null
            if (udpPorts[bound_to.port]?.sendLast != null) {
                udpPorts[bound_to.port]?.sendLast?.next = msg
            } else {
                udpPorts[bound_to.port]?.sendFirst = msg
            }
            udpPorts[bound_to.port]?.sendLast = msg

            msg = udpPorts[bound_to.port]?.sendFirst
            while (msg != null && msg.time <= Sys_Milliseconds() - net_forceLatency.GetInteger()) {
                Net_SendUDPPacket(netSocket, msg.size, ByteBuffer.wrap(msg.data), msg.address!!)
                udpPorts[bound_to.port]?.sendFirst = udpPorts[bound_to.port]?.sendFirst!!.next
                if (udpPorts[bound_to.port]?.sendFirst == null) {
                    udpPorts[bound_to.port]?.sendLast = null
                }
                msg = udpPorts[bound_to.port]?.sendFirst
            }
        } else {
            Net_SendUDPPacket(netSocket, size, data, to)
        }
    }
}

/*
 ==============================================================

 idSys

 ==============================================================
 */
abstract class idSys {
    abstract fun DebugPrintf(fmt: String, vararg arg: Any)
    abstract fun DebugVPrintf(fmt: String, vararg arg: Any)
    abstract fun GetMilliseconds(): Long
    abstract /*cpuid_t*/  fun GetProcessorId(): Int
    abstract fun GetProcessorString(): String
    abstract fun FPU_GetState(): String
    abstract fun FPU_StackIsEmpty(): Boolean
    abstract fun FPU_SetFTZ(enable: Boolean)
    abstract fun FPU_SetDAZ(enable: Boolean)
    abstract fun FPU_EnableExceptions(exceptions: Int)
    abstract fun LockMemory(ptr: Any, bytes: Int): Boolean
    abstract fun UnlockMemory(ptr: Any, bytes: Int): Boolean
    abstract fun DLL_Load(dllName: String): Int
    abstract fun DLL_GetProcAddress(dllHandle: Int, procName: String): Any
    abstract fun DLL_Unload(dllHandle: Int)
    abstract fun DLL_GetFileName(baseName: String, dllName: Array<String>, maxLength: Int)
    abstract fun GenerateMouseButtonEvent(button: Int, down: Boolean): sysEvent_s
    abstract fun GenerateMouseMoveEvent(deltax: Int, deltay: Int): sysEvent_s
    abstract fun OpenURL(url: String, quit: Boolean)
    abstract fun StartProcess(exePath: String, quit: Boolean)
}
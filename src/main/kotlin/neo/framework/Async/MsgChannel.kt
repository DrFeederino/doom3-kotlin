package neo.framework.Async

import neo.framework.CVarSystem
import neo.framework.CVarSystem.idCVar
import neo.framework.Common
import neo.framework.Compressor.idCompressor
import neo.framework.File_h.idFile_BitMsg
import neo.idlib.BitMsg.idBitMsg
import neo.idlib.containers.CInt
import neo.idlib.idException
import neo.sys.sys_public.idPort
import neo.sys.sys_public.netadr_t
import neo.sys.sys_public.netadrtype_t
import neo.sys.win_net
import java.nio.ByteBuffer
import java.util.*

object MsgChannel {
    //
    const val CONNECTIONLESS_MESSAGE_ID = -1 // id for connectionless messages
    const val CONNECTIONLESS_MESSAGE_ID_MASK = 0x7FFF // value to mask away connectionless message id
    const val FRAGMENT_BIT = 1 shl 31

    //
    //
    /*
     ===============================================================================

     Network channel.

     Handles message fragmentation and out of order / duplicate suppression.
     Unreliable messages are not garrenteed to arrive but when they do, they
     arrive in order and without duplicates. Reliable messages always arrive,
     and they also arrive in order without duplicates. Reliable messages piggy
     back on unreliable messages. As such an unreliable message stream is
     required for the reliable messages to be delivered.

     ===============================================================================
     */
    const val MAX_MESSAGE_SIZE = 16384 // max length of a message, which may be fragmented into multiple packets

    //
    const val MAX_MSG_QUEUE_SIZE = 16384 // must be a power of 2

    /*

     packet header
     -------------
     2 bytes		id
     4 bytes		outgoing sequence. high bit will be set if this is a fragmented message.
     2 bytes		optional fragment start byte if fragment bit is set.
     2 bytes		optional fragment length if fragment bit is set. if < FRAGMENT_SIZE, this is the last fragment.

     If the id is -1, the packet should be handled as an out-of-band
     message instead of as part of the message channel.

     All fragments will have the same sequence numbers.

     */
    const val MAX_PACKETLEN = 1400 // max size of a network packet
    const val FRAGMENT_SIZE = MAX_PACKETLEN - 100
    val net_channelShowDrop: idCVar =
        idCVar("net_channelShowDrop", "0", CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_BOOL, "show dropped packets")

    //
    val net_channelShowPackets: idCVar =
        idCVar("net_channelShowPackets", "0", CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_BOOL, "show all packets")

    internal class idMsgChannel {
        private val fragmentBuffer = ByteBuffer.allocate(MAX_MESSAGE_SIZE)
        private val unsentBuffer = ByteBuffer.allocate(MAX_MESSAGE_SIZE)
        private var compressor // compressor used for data compression
                : idCompressor = idCompressor.AllocRunLength_ZeroBased()
        private var fragmentLength = 0

        //
        // incoming fragment assembly buffer
        private var fragmentSequence = 0
        private var id // our identification used instead of port number
                : Int
        private var incomingCompression = 0.0f
        private var incomingDroppedPackets = 0.0f
        private var incomingPacketLossTime = 0
        private var incomingRateBytes = 0
        private var incomingRateTime = 0

        //
        // variables to keep track of the incoming packet loss
        private var incomingReceivedPackets = 0.0f
        private var incomingSequence = 0
        private var lastDataBytes // bytes left to send at last send time
                = 0

        //
        // variables to control the outgoing rate
        private var lastSendTime // last time data was sent out
                = 0
        private var maxRate // maximum number of bytes that may go out per second
                = 0

        //
        // variables to keep track of the compression ratio
        private var outgoingCompression = 0.0f
        private var outgoingRateBytes = 0

        //
        // variables to keep track of the rate
        private var outgoingRateTime = 0

        //
        // sequencing variables
        private var outgoingSequence = 0
        private val reliableReceive: idMsgQueue = idMsgQueue()

        //
        // reliable messages
        private val reliableSend: idMsgQueue = idMsgQueue()
        private var remoteAddress // address of remote host
                : netadr_t = netadr_t()
        private var unsentFragmentStart = 0

        //
        // outgoing fragment buffer
        private var unsentFragments = false
        private val unsentMsg: idBitMsg = idBitMsg()

        /*
         ==============
         idMsgChannel::Init

         Opens a channel to a remote system.
         ==============
         */
        fun Init(adr: netadr_t, id: Int) {
            remoteAddress = adr
            this.id = id
            maxRate = 50000
            compressor = idCompressor.AllocRunLength_ZeroBased()
            lastSendTime = 0
            lastDataBytes = 0
            outgoingRateTime = 0
            outgoingRateBytes = 0
            incomingRateTime = 0
            incomingRateBytes = 0
            incomingReceivedPackets = 0.0f
            incomingDroppedPackets = 0.0f
            incomingPacketLossTime = 0
            outgoingCompression = 0.0f
            incomingCompression = 0.0f
            outgoingSequence = 1
            incomingSequence = 0
            unsentFragments = false
            unsentFragmentStart = 0
            fragmentSequence = 0
            fragmentLength = 0
            reliableSend.Init(1)
            reliableReceive.Init(0)
        }

        fun Shutdown() {
            //compressor = null
        }

        fun ResetRate() {
            lastSendTime = 0
            lastDataBytes = 0
            outgoingRateTime = 0
            outgoingRateBytes = 0
            incomingRateTime = 0
            incomingRateBytes = 0
        }

        // Sets the maximum outgoing rate.
        fun SetMaxOutgoingRate(rate: Int) {
            maxRate = rate
        }

        // Gets the maximum outgoing rate.
        fun GetMaxOutgoingRate(): Int {
            return maxRate
        }

        // Returns the address of the entity at the other side of the channel.
        fun GetRemoteAddress(): netadr_t {
            return remoteAddress
        }

        // Returns the average outgoing rate over the last second.
        fun GetOutgoingRate(): Int {
            return outgoingRateBytes
        }

        // Returns the average incoming rate over the last second.
        fun GetIncomingRate(): Int {
            return incomingRateBytes
        }

        // Returns the average outgoing compression ratio over the last second.
        fun GetOutgoingCompression(): Float {
            return outgoingCompression
        }

        // Returns the average incoming compression ratio over the last second.
        fun GetIncomingCompression(): Float {
            return incomingCompression
        }

        // Returns the average incoming packet loss over the last 5 seconds.
        fun GetIncomingPacketLoss(): Float {
            return if (incomingReceivedPackets == 0.0f && incomingDroppedPackets == 0.0f) {
                0.0f
            } else incomingDroppedPackets * 100.0f / (incomingReceivedPackets + incomingDroppedPackets)
        }

        //
        // Returns true if the channel is ready to send new data based on the maximum rate.
        fun ReadyToSend(time: Int): Boolean {
            val deltaTime: Int
            if (0 == maxRate) {
                return true
            }
            deltaTime = time - lastSendTime
            return if (deltaTime > 1000) {
                true
            } else lastDataBytes - deltaTime * maxRate / 1000 <= 0
        }

        //
        /*
         ===============
         idMsgChannel::SendMessage

         Sends a message to a connection, fragmenting if necessary
         A 0 length will still generate a packet.
         ================
         */
        // Sends an unreliable message, in order and without duplicates.
        @Throws(idException::class)
        fun SendMessage(port: idPort, time: Int, msg: idBitMsg): Int {
            val totalLength: Int
            if (remoteAddress.type == netadrtype_t.NA_BAD) {
                return -1
            }
            if (unsentFragments) {
                Common.common.Error("idMsgChannel::SendMessage: called with unsent fragments left")
                return -1
            }
            totalLength = 4 + reliableSend.GetTotalSize() + 4 + msg.GetSize()
            if (totalLength > MAX_MESSAGE_SIZE) {
                Common.common.Printf("idMsgChannel::SendMessage: message too large, length = %d\n", totalLength)
                return -1
            }
            unsentMsg.Init(unsentBuffer, unsentBuffer.capacity())
            unsentMsg.BeginWriting()

            // fragment large messages
            if (totalLength >= FRAGMENT_SIZE) {
                unsentFragments = true
                unsentFragmentStart = 0

                // write out the message data
                WriteMessageData(unsentMsg, msg)

                // send the first fragment now
                SendNextFragment(port, time)
                return outgoingSequence
            }

            // write the header
            unsentMsg.WriteShort(id.toShort())
            unsentMsg.WriteLong(outgoingSequence)

            // write out the message data
            WriteMessageData(unsentMsg, msg)

            // send the packet
            port.SendPacket(remoteAddress, unsentMsg.GetData()!!, unsentMsg.GetSize())

            // update rate control variables
            UpdateOutgoingRate(time, unsentMsg.GetSize())
            if (net_channelShowPackets.GetBool()) {
                Common.common.Printf(
                    "%d send %4d : s = %d ack = %d\n",
                    id,
                    unsentMsg.GetSize(),
                    outgoingSequence - 1,
                    incomingSequence
                )
            }
            outgoingSequence++
            return outgoingSequence - 1
        }

        //
        /*
         =================
         idMsgChannel::SendNextFragment

         Sends one fragment of the current message.
         =================
         */
        // Sends the next fragment if the last message was too large to send at once.
        @Throws(idException::class)
        fun SendNextFragment(port: idPort, time: Int) {
            val msg = idBitMsg()
            val msgBuf = ByteBuffer.allocate(MAX_PACKETLEN)
            var fragLength: Int
            if (remoteAddress.type == netadrtype_t.NA_BAD) {
                return
            }
            if (!unsentFragments) {
                return
            }

            // write the packet
            msg.Init(msgBuf, msgBuf.capacity())
            msg.WriteShort(id.toShort())
            msg.WriteLong(outgoingSequence or FRAGMENT_BIT)
            fragLength = FRAGMENT_SIZE
            if (unsentFragmentStart + fragLength > unsentMsg.GetSize()) {
                fragLength = unsentMsg.GetSize() - unsentFragmentStart
            }
            msg.WriteShort(unsentFragmentStart.toShort())
            msg.WriteShort(fragLength.toShort())
            msg.WriteData(unsentMsg.GetData()!!, unsentFragmentStart, fragLength)

            // send the packet
            port.SendPacket(remoteAddress, msg.GetData()!!, msg.GetSize())

            // update rate control variables
            UpdateOutgoingRate(time, msg.GetSize())
            if (net_channelShowPackets.GetBool()) {
                Common.common.Printf(
                    "%d send %4d : s = %d fragment = %d,%d\n",
                    id,
                    msg.GetSize(),
                    outgoingSequence - 1,
                    unsentFragmentStart,
                    fragLength
                )
            }
            unsentFragmentStart += fragLength

            // this exit condition is a little tricky, because a packet
            // that is exactly the fragment length still needs to send
            // a second packet of zero length so that the other side
            // can tell there aren't more to follow
            if (unsentFragmentStart == unsentMsg.GetSize() && fragLength != FRAGMENT_SIZE) {
                outgoingSequence++
                unsentFragments = false
            }
        }

        // Returns true if there are unsent fragments left.
        fun UnsentFragmentsLeft(): Boolean {
            return unsentFragments
        }

        /*
         =================
         idMsgChannel::Process

         Returns false if the message should not be processed due to being out of order or a fragment.

         msg must be large enough to hold MAX_MESSAGE_SIZE, because if this is the final
         fragment of a multi-part message, the entire thing will be copied out.
         =================
         */
        // Processes the incoming message. Returns true when a complete message
        // is ready for further processing. In that case the read pointer of msg
        // points to the first byte ready for reading, and sequence is set to
        // the sequence number of the message.
        fun Process(from: netadr_t, time: Int, msg: idBitMsg, sequence: CInt): Boolean {
            val fragStart: Int
            val fragLength: Int
            val dropped: Int
            val fragmented: Boolean
            val fragMsg = idBitMsg()

            // the IP port can't be used to differentiate them, because
            // some address translating routers periodically change UDP
            // port assignments
            if (remoteAddress.port != from.port) {
                Common.common.Printf("idMsgChannel::Process: fixing up a translated port\n")
                remoteAddress.port = from.port
            }

            // update incoming rate
            UpdateIncomingRate(time, msg.GetSize())

            // get sequence numbers
            sequence._val = (msg.ReadLong())

            // check for fragment information
            fragmented = if (sequence._val and FRAGMENT_BIT != 0) {
                sequence._val = (sequence._val and FRAGMENT_BIT.inv())
                true
            } else {
                false
            }

            // read the fragment information
            if (fragmented) {
                fragStart = msg.ReadShort().toInt()
                fragLength = msg.ReadShort().toInt()
            } else {
                fragStart = 0 // stop warning message
                fragLength = 0
            }
            if (net_channelShowPackets.GetBool()) {
                if (fragmented) {
                    Common.common.Printf(
                        "%d recv %4d : s = %d fragment = %d,%d\n",
                        id,
                        msg.GetSize(),
                        sequence._val,
                        fragStart,
                        fragLength
                    )
                } else {
                    Common.common.Printf("%d recv %4d : s = %d\n", id, msg.GetSize(), sequence._val)
                }
            }

            //
            // discard out of order or duplicated packets
            //
            if (sequence._val <= incomingSequence) {
                if (net_channelShowDrop.GetBool() || net_channelShowPackets.GetBool()) {
                    Common.common.Printf(
                        "%s: out of order packet %d at %d\n",
                        win_net.Sys_NetAdrToString(remoteAddress),
                        sequence._val,
                        incomingSequence
                    )
                }
                return false
            }

            //
            // dropped packets don't keep this message from being used
            //
            dropped = sequence._val - (incomingSequence + 1)
            if (dropped > 0) {
                if (net_channelShowDrop.GetBool() || net_channelShowPackets.GetBool()) {
                    Common.common.Printf(
                        "%s: dropped %d packets at %d\n",
                        win_net.Sys_NetAdrToString(remoteAddress),
                        dropped,
                        sequence._val
                    )
                }
                UpdatePacketLoss(time, 0, dropped)
            }

            //
            // if the message is fragmented
            //
            if (fragmented) {
                // make sure we have the correct sequence number
                if (sequence._val != fragmentSequence) {
                    fragmentSequence = sequence._val
                    fragmentLength = 0
                }

                // if we missed a fragment, dump the message
                if (fragStart != fragmentLength) {
                    if (net_channelShowDrop.GetBool() || net_channelShowPackets.GetBool()) {
                        Common.common.Printf(
                            "%s: dropped a message fragment at seq %d\n",
                            win_net.Sys_NetAdrToString(remoteAddress),
                            sequence._val
                        )
                    }
                    // we can still keep the part that we have so far,
                    // so we don't need to clear fragmentLength
                    UpdatePacketLoss(time, 0, 1)
                    return false
                }

                // copy the fragment to the fragment buffer
                if (fragLength < 0 || fragLength > msg.GetRemaingData() || fragmentLength + fragLength > fragmentBuffer.capacity()) {
                    if (net_channelShowDrop.GetBool() || net_channelShowPackets.GetBool()) {
                        Common.common.Printf("%s: illegal fragment length\n", win_net.Sys_NetAdrToString(remoteAddress))
                    }
                    UpdatePacketLoss(time, 0, 1)
                    return false
                }

//		memcpy( fragmentBuffer + fragmentLength, msg.GetData()!! + msg.GetReadCount(), fragLength );
                System.arraycopy(
                    msg.GetData()!!.array(), msg.GetReadCount(),
                    fragmentBuffer.array(), fragmentLength, fragLength
                )
                fragmentLength += fragLength
                UpdatePacketLoss(time, 1, 0)

                // if this wasn't the last fragment, don't process anything
                if (fragLength == FRAGMENT_SIZE) {
                    return false
                }
            } else {
//		memcpy( fragmentBuffer, msg.GetData()!! + msg.GetReadCount(), msg.GetRemaingData() );
                System.arraycopy(
                    msg.GetData()!!.array(), msg.GetReadCount(),
                    fragmentBuffer.array(), 0, msg.GetRemaingData()
                )
                fragmentLength = msg.GetRemaingData()
                UpdatePacketLoss(time, 1, 0)
            }
            fragMsg.Init(fragmentBuffer, fragmentLength)
            fragMsg.SetSize(fragmentLength)
            fragMsg.BeginReading()
            incomingSequence = sequence._val

            // read the message data
            return ReadMessageData(msg, fragMsg)
        }

        //
        // Sends a reliable message, in order and without duplicates.
        fun SendReliableMessage(msg: idBitMsg): Boolean {
            val result: Boolean
            assert(remoteAddress.type != netadrtype_t.NA_BAD)
            if (remoteAddress.type == netadrtype_t.NA_BAD) {
                return false
            }
            result = reliableSend.Add(msg.GetData()!!.array(), msg.GetSize())
            if (!result) {
                Common.common.Warning("idMsgChannel::SendReliableMessage: overflowed")
                return false
            }
            return result
        }

        //
        // Returns true if a new reliable message is available and stores the message.
        fun GetReliableMessage(msg: idBitMsg): Boolean {
            val size = CInt()
            val result: Boolean
            result = reliableReceive.Get(msg.GetData()!!.array(), size)
            msg.SetSize(msg.GetData()!!.capacity()) //TODO:phase out size and length fields.
            msg.BeginReading()
            return result
        }

        //
        // Removes any pending outgoing or incoming reliable messages.
        fun ClearReliableMessages() {
            reliableSend.Init(1)
            reliableReceive.Init(0)
        }

        private fun WriteMessageData(out: idBitMsg, msg: idBitMsg) {
            val tmp = idBitMsg()
            val tmpBuf = ByteBuffer.allocate(MAX_MESSAGE_SIZE)
            tmp.Init(tmpBuf, tmpBuf.capacity())

            // write acknowledgement of last received reliable message
            tmp.WriteLong(reliableReceive.GetLast())

            // write reliable messages
            reliableSend.CopyToBuffer(
                Arrays.copyOfRange(
                    tmp.GetData()!!.array(),
                    tmp.GetSize(),
                    tmp.GetData()!!.capacity()
                )
            )
            tmp.SetSize(tmp.GetSize() + reliableSend.GetTotalSize())
            tmp.WriteShort(0)

            // write data
            tmp.WriteData(msg.GetData()!!, msg.GetSize())

            // write message size
            out.WriteShort(tmp.GetSize().toShort())

            // compress message
            val file = idFile_BitMsg(out)
            compressor.Init(file, true, 3)
            compressor.Write(tmp.GetData()!!, tmp.GetSize())
            compressor.FinishCompress()
            outgoingCompression = compressor.GetCompressionRatio()
        }

        private fun ReadMessageData(out: idBitMsg, msg: idBitMsg): Boolean {
            val reliableAcknowledge: Int
            var reliableSequence: Int
            val reliableMessageSize = CInt()

            // read message size
            out.SetSize(msg.ReadShort().toInt())

            // decompress message
            val file = idFile_BitMsg(msg)
            compressor.Init(file, false, 3)
            compressor.Read(out.GetData()!!, out.GetSize())
            incomingCompression = compressor.GetCompressionRatio()
            out.BeginReading()

            // read acknowledgement of sent reliable messages
            reliableAcknowledge = out.ReadLong()

            // remove acknowledged reliable messages
            while (reliableSend.GetFirst() <= reliableAcknowledge) {
                if (!reliableSend.Get(null, reliableMessageSize)) {
                    break
                }
            }

            // read reliable messages
            reliableMessageSize._val = (out.ReadShort().toInt())
            while (reliableMessageSize._val != 0) {
                if (reliableMessageSize._val <= 0 || reliableMessageSize._val > out.GetSize() - out.GetReadCount()) {
                    Common.common.Printf("%s: bad reliable message\n", win_net.Sys_NetAdrToString(remoteAddress))
                    return false
                }
                reliableSequence = out.ReadLong()
                if (reliableSequence == reliableReceive.GetLast() + 1) {
                    reliableReceive.Add(
                        Arrays.copyOfRange(
                            out.GetData()!!.array(),
                            out.GetReadCount(),
                            out.GetData()!!.capacity()
                        ), reliableMessageSize._val
                    )
                }
                out.ReadData(null, reliableMessageSize._val)
                reliableMessageSize._val = (out.ReadShort().toInt())
            }
            return true
        }

        //
        private fun UpdateOutgoingRate(time: Int, size: Int) {
            // update the outgoing rate control variables
            val deltaTime = time - lastSendTime
            if (deltaTime > 1000) {
                lastDataBytes = 0
            } else {
                lastDataBytes -= deltaTime * maxRate / 1000
                if (lastDataBytes < 0) {
                    lastDataBytes = 0
                }
            }
            lastDataBytes += size
            lastSendTime = time

            // update outgoing rate variables
            if (time - outgoingRateTime > 1000) {
                outgoingRateBytes -= outgoingRateBytes * (time - outgoingRateTime - 1000) / 1000
                if (outgoingRateBytes < 0) {
                    outgoingRateBytes = 0
                }
            }
            outgoingRateTime = time - 1000
            outgoingRateBytes += size
        }

        private fun UpdateIncomingRate(time: Int, size: Int) {
            // update incoming rate variables
            if (time - incomingRateTime > 1000) {
                incomingRateBytes -= incomingRateBytes * (time - incomingRateTime - 1000) / 1000
                if (incomingRateBytes < 0) {
                    incomingRateBytes = 0
                }
            }
            incomingRateTime = time - 1000
            incomingRateBytes += size
        }

        private fun UpdatePacketLoss(time: Int, numReceived: Int, numDropped: Int) {
            // update incoming packet loss variables
            if (time - incomingPacketLossTime > 5000) {
                val scale = (time - incomingPacketLossTime - 5000) * (1.0f / 5000.0f)
                incomingReceivedPackets -= incomingReceivedPackets * scale
                if (incomingReceivedPackets < 0.0f) {
                    incomingReceivedPackets = 0.0f
                }
                incomingDroppedPackets -= incomingDroppedPackets * scale
                if (incomingDroppedPackets < 0.0f) {
                    incomingDroppedPackets = 0.0f
                }
            }
            incomingPacketLossTime = time - 5000
            incomingReceivedPackets += numReceived.toFloat()
            incomingDroppedPackets += numDropped.toFloat()
        }

        //
        //
        init {
            id = -1
        }
    }

    internal class idMsgQueue {
        private val buffer: ByteArray = ByteArray(MAX_MSG_QUEUE_SIZE)
        private var endIndex // index pointing to the first byte after the last message
                = 0
        private var first // sequence number of first message in queue
                = 0
        private var last // sequence number of last message in queue
                = 0
        private var startIndex // index pointing to the first byte of the first message
                = 0

        fun Init(sequence: Int) {
            last = sequence
            first = last
            endIndex = 0
            startIndex = endIndex
        }

        fun Add(data: ByteArray, size: Int): Boolean {
            if (GetSpaceLeft() < size + 8) {
                return false
            }
            val sequence = last
            WriteShort(size)
            WriteLong(sequence)
            WriteData(data, size)
            last++
            return true
        }

        fun Get(data: ByteArray?, size: CInt): Boolean {
            if (first == last) {
                size._val = (0)
                return false
            }
            val sequence: Int
            size._val = (ReadShort())
            sequence = ReadLong()
            ReadData(data, size._val)
            assert(sequence == first)
            first++
            return true
        }

        fun GetTotalSize(): Int {
            return if (startIndex <= endIndex) {
                endIndex - startIndex
            } else {
                buffer.size - startIndex + endIndex
            }
        }

        fun GetSpaceLeft(): Int {
            return if (startIndex <= endIndex) {
                buffer.size - (endIndex - startIndex) - 1
            } else {
                startIndex - endIndex - 1
            }
        }

        fun GetFirst(): Int {
            return first
        }

        fun GetLast(): Int {
            return last
        }

        fun CopyToBuffer(buf: ByteArray) {
            if (startIndex <= endIndex) {
//		memcpy( buf, buffer + startIndex, endIndex - startIndex );
                System.arraycopy(buffer, startIndex, buf, 0, endIndex - startIndex)
            } else {
//		memcpy( buf, buffer + startIndex, sizeof( buffer ) - startIndex );
                System.arraycopy(buffer, startIndex, buf, 0, buffer.size - startIndex)
                //		memcpy( buf + sizeof( buffer ) - startIndex, buffer, endIndex );
                System.arraycopy(buffer, 0, buf, buffer.size - startIndex, endIndex)
            }
        }

        private fun WriteByte(b: Byte) {
            buffer[endIndex] = b
            endIndex = endIndex + 1 and MAX_MSG_QUEUE_SIZE - 1
        }

        private fun ReadByte(): Byte {
            val b = buffer[startIndex]
            startIndex = startIndex + 1 and MAX_MSG_QUEUE_SIZE - 1
            return b
        }

        private fun WriteShort(s: Int) {
            WriteByte((s shr 0 and 255).toByte())
            WriteByte((s shr 8 and 255).toByte())
        }

        private fun ReadShort(): Int {
            return ReadByte().toInt() or (ReadByte().toInt() shl 8)
        }

        private fun WriteLong(l: Int) {
            WriteByte((l shr 0 and 255).toByte())
            WriteByte((l shr 8 and 255).toByte())
            WriteByte((l shr 16 and 255).toByte())
            WriteByte((l shr 24 and 255).toByte())
        }

        private fun ReadLong(): Int {
            return ReadByte().toInt() or (ReadByte().toInt() shl 8) or (ReadByte().toInt() shl 16) or (ReadByte().toInt() shl 24)
        }

        private fun WriteData(data: ByteArray, size: Int) {
            for (i in 0 until size) {
                WriteByte(data[i])
            }
        }

        private fun ReadData(data: ByteArray?, size: Int) {
            if (data != null) {
                for (i in 0 until size) {
                    data[i] = ReadByte()
                }
            } else {
                for (i in 0 until size) {
                    ReadByte()
                }
            }
        }

        //
        //
        init {
            Init(0)
        }
    }
}
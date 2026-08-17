/*
 * Copyright (C) 1999-2011 id Software LLC, a ZeniMax Media company.
 * Translated to Kotlin by Dr. Feederino with support of Claude Code
 *
 * This file is part of the Doom 3 Kotlin project.
 * Original source: neo/framework/Compressor.cpp, neo/framework/Compressor.h
 *
 * Doom 3 GPL Source Code
 * Copyright (C) 1999-2011 id Software LLC, a ZeniMax Media company.
 *
 * This file is part of the Doom 3 GPL Source Code ("Doom 3 Source Code").
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
 *
 * You should have received a copy of the GNU General Public License
 * along with Doom 3 Source Code. If not, see <http://www.gnu.org/licenses/>.
 */
package neo.framework

import neo.framework.File_h.fsOrigin_t
import neo.framework.File_h.idFile
import neo.idlib.Max
import neo.idlib.Min
import neo.idlib.containers.idHashIndex
import neo.idlib.idException
import java.nio.ByteBuffer
import java.util.*
import kotlin.experimental.or

object Compressor {
    const val AC_HIGH_INIT = 0xffff
    const val AC_LOW_INIT = 0x0000
    const val AC_MSB2_MASK = 0x4000
    const val AC_MSB2_SHIFT = 14
    const val AC_MSB_MASK = 0x8000
    const val AC_MSB_SHIFT = 15
    const val AC_NUM_BITS = 16
    const val AC_WORD_LENGTH = 8
    const val HMAX = 256 // Maximum symbol
    const val INTERNAL_NODE = HMAX + 1 // internal node
    const val LZSS_BLOCK_SIZE = 65535
    const val LZSS_HASH_BITS = 10
    const val LZSS_HASH_MASK = (1 shl LZSS_HASH_BITS) - 1
    const val LZSS_HASH_SIZE = 1 shl LZSS_HASH_BITS
    const val LZSS_LENGTH_BITS = 5
    const val LZSS_OFFSET_BITS = 11
    const val NYT = HMAX // NYT = Not Yet Transmitted

    /*
     ===============================================================================

     idCompressor is a layer ontop of idFile which provides lossless data
     compression. The compressor can be used as a regular file and multiple
     compressors can be stacked ontop of each other.

     ===============================================================================
     */
    abstract class idCompressor : idFile() {
        // initialization
        abstract fun Init(f: idFile, compress: Boolean, wordLength: Int)
        abstract fun FinishCompress()
        abstract fun GetCompressionRatio(): Float

        @Throws(idException::class)
        override fun Seek(offset: Long, origin: fsOrigin_t): Boolean {
            return super.Seek(offset, origin)
        }

        companion object {
            // compressor allocation
            fun AllocNoCompression(): idCompressor {
                return idCompressor_None()
            }

            fun AllocBitStream(): idCompressor {
                return idCompressor_BitStream()
            }

            fun AllocRunLength(): idCompressor {
                return idCompressor_RunLength()
            }

            fun AllocRunLength_ZeroBased(): idCompressor {
                return idCompressor_RunLength_ZeroBased()
            }

            fun AllocHuffman(): idCompressor {
                return idCompressor_Huffman()
            }

            fun AllocArithmetic(): idCompressor {
                return idCompressor_Arithmetic()
            }

            fun AllocLZSS(): idCompressor {
                return idCompressor_LZSS()
            }

            fun AllocLZSS_WordAligned(): idCompressor {
                return idCompressor_LZSS_WordAligned()
            }

            fun AllocLZW(): idCompressor {
                return idCompressor_LZW()
            }
        }
    }

    /*
     =================================================================================

     idCompressor_None

     =================================================================================
     */
    internal open class idCompressor_None : idCompressor() {
        protected var compress = true

        //
        //
        protected lateinit var file: idFile
        override fun Init(f: idFile, compress: Boolean, wordLength: Int) {
            file = f
            this.compress = compress
        }

        override fun FinishCompress() {}
        override fun GetCompressionRatio(): Float {
            return 0.0f
        }

        override fun GetName(): String {
            return if (file != null) {
                file.GetName()
            } else {
                ""
            }
        }

        override fun GetFullPath(): String {
            return if (file != null) {
                file.GetFullPath()
            } else {
                ""
            }
        }

        override fun Read(outData: ByteBuffer): Int {
            return if (compress == true) {
                0
            } else file.Read(outData)
        }

        override fun Write(inData: ByteBuffer): Int {
            return Write(inData, inData.capacity())
        }

        override fun Write(inData: ByteBuffer, inLength: Int): Int {
            if (!compress || inLength <= 0) {
                return 0
            }
            val savedLimit = inData.limit()
            inData.limit(inData.position() + inLength)
            file.Write(inData)
            inData.limit(savedLimit)
            return inLength
        }

        override fun Length(): Int {
            return if (file != null) {
                file.Length()
            } else {
                0
            }
        }

        override fun Timestamp(): Long {
            return if (file != null) {
                file.Timestamp()
            } else {
                0
            }
        }

        override fun Tell(): Int {
            return if (file != null) {
                file.Tell()
            } else {
                0
            }
        }

        override fun ForceFlush() {
            if (file != null) {
                file.ForceFlush()
            }
        }

        override fun Flush() {
            if (file != null) {
                file.ForceFlush()
            }
        }

        @Throws(idException::class)
        override fun Seek(offset: Long, origin: fsOrigin_t): Boolean {
            Common.common.Error("cannot seek on idCompressor")
            return false //-1;
        }
    }

    /*
     =================================================================================

     idCompressor_BitStream

     Base class for bit stream compression.

     =================================================================================
     */
    internal open class idCompressor_BitStream  //
    //
        : idCompressor_None() {
        protected var buffer = ByteBuffer.allocate(65536)
        protected var readBit = 0
        protected var readByte = 0
        protected var readData //= new byte[1];
                : ByteBuffer? = null
        protected var readLength = 0

        //
        protected var readTotalBytes = 0
        protected var wordLength = 0
        protected var writeBit = 0
        protected var writeByte = 0
        protected var writeData //= new byte[1];
                : ByteBuffer? = null
        protected var writeLength = 0

        //
        protected var writeTotalBytes = 0
        override fun Init(f: idFile, compress: Boolean, wordLength: Int) {
            assert(wordLength >= 1 && wordLength <= 32)
            file = f
            this.compress = compress
            this.wordLength = wordLength
            readTotalBytes = 0
            readLength = 0
            readByte = 0
            readBit = 0
            readData = null
            writeTotalBytes = 0
            writeLength = 0
            writeByte = 0
            writeBit = 0
            writeData = null
        }

        override fun FinishCompress() {
            if (compress == false) {
                return
            }
            if (writeByte != 0) {
                file.Write(buffer, writeByte)
            }
            writeLength = 0
            writeByte = 0
            writeBit = 0
        }

        override fun GetCompressionRatio(): Float {
            return if (compress) {
                (readTotalBytes - writeTotalBytes) * 100.0f / readTotalBytes
            } else {
                (writeTotalBytes - readTotalBytes) * 100.0f / writeTotalBytes
            }
        }

        override fun Write(inData: ByteBuffer, inLength: Int): Int {
            var i: Int
            if (compress == false || inLength <= 0) {
                return 0
            }
            InitCompress(inData, inLength)
            i = 0
            while (i < inLength) {
                WriteBits(ReadBits(8), 8)
                i++
            }
            return i
        }

        override fun Read(outData: ByteBuffer, outLength: Int): Int {
            var i: Int
            if (compress == true || outLength <= 0) {
                return 0
            }
            InitDecompress(outData, outLength)
            i = 0
            while (i < outLength && readLength >= 0) {
                WriteBits(ReadBits(8), 8)
                i++
            }
            return i
        }

        protected fun InitCompress(inData: ByteBuffer, inLength: Int) {
            readLength = inLength
            readByte = 0
            readBit = 0
            readData = inData
            if (0 == writeLength) {
                writeLength = buffer.capacity()
                writeByte = 0
                writeBit = 0
                writeData = buffer
            }
        }

        protected fun InitCompress(inData: ByteArray, inLength: Int) {
            InitCompress(ByteBuffer.wrap(inData), inLength)
        }

        protected fun InitDecompress(outData: ByteBuffer, outLength: Int) {
            if (0 == readLength) {
                buffer.rewind() // Reset position — ByteBuffer position was advanced by previous ReadData
                readLength = file.Read(buffer)
                readByte = 0
                readBit = 0
                readData = buffer
            }
            writeLength = outLength
            writeByte = 0
            writeBit = 0
            writeData = outData
        }

        protected fun InitDecompress(outData: ByteArray, outLength: Int) {
            InitDecompress(ByteBuffer.wrap(outData), outLength)
        }

        protected fun WriteBits(value: Int, numBits: Int) {
            var value = value
            var numBits = numBits
            var put: Int
            var fraction: Int

            // Short circuit for writing single bytes at a time
            if (writeBit == 0 && numBits == 8 && writeByte < writeLength) {
                writeByte++
                writeTotalBytes++ // FIX: C++ writes a single byte; was incorrectly using putInt (writes 4 bytes)
                writeData!!.put(writeByte - 1, value.toByte())
                return
            }
            while (numBits != 0) {
                if (writeBit == 0) {
                    if (writeByte >= writeLength) {
                        if (writeData == buffer) {
                            file.Write(buffer, writeByte)
                            writeByte = 0
                        } else {
                            put = numBits
                            writeBit = put and 7
                            writeByte += (put shr 3) + if (writeBit != 0) 1 else 0
                            writeTotalBytes += (put shr 3) + if (writeBit != 0) 1 else 0
                            return
                        }
                    } // FIX: C++ zeroes a single byte; was using putInt (overwrites 4 bytes)
                    writeData!!.put(writeByte, 0.toByte())
                    writeByte++
                    writeTotalBytes++
                }
                put = 8 - writeBit
                if (put > numBits) {
                    put = numBits
                }
                fraction =
                    value and ((1 shl put) - 1) // FIX: C++ does single-byte OR: writeData[pos] |= fraction << writeBit
                // was incorrectly using getInt/putInt (reads/writes 4 bytes)
                val pos = writeByte - 1
                writeData!!.put(pos, ((writeData!!.get(pos).toInt() and 0xFF) or (fraction shl writeBit)).toByte())
                numBits -= put
                value = value shr put
                writeBit = writeBit + put and 7
            }
        }

        protected fun ReadBits(numBits: Int): Int {
            var value: Int
            var valueBits: Int
            var get: Int
            var fraction: Int
            value = 0
            valueBits = 0

            // Short circuit for reading single bytes at a time
            if (readBit == 0 && numBits == 8 && readByte < readLength) {
                readByte++
                readTotalBytes++ // FIX: C++ returns a single unsigned byte; was incorrectly using getInt (reads 4 bytes)
                return readData!!.get(readByte - 1).toInt() and 0xFF
            }
            while (valueBits < numBits) {
                if (readBit == 0) {
                    if (readByte >= readLength) {
                        if (readData == buffer) {
                            buffer.rewind()
                            readLength = file.Read(buffer)
                            readByte = 0
                        } else {
                            get = numBits - valueBits
                            readBit = get and 7
                            readByte += (get shr 3) + if (readBit != 0) 1 else 0
                            readTotalBytes += (get shr 3) + if (readBit != 0) 1 else 0
                            return value
                        }
                    }
                    readByte++
                    readTotalBytes++
                }
                get = 8 - readBit
                if (get > numBits - valueBits) {
                    get = numBits - valueBits
                }
                fraction = readData!!.get(readByte - 1).toInt()
                fraction = fraction shr readBit
                fraction = fraction and (1 shl get) - 1
                value = value or (fraction shl valueBits)
                valueBits += get
                readBit = readBit + get and 7
            }
            return value
        }

        protected fun UnreadBits(numBits: Int) {
            readByte -= numBits shr 3
            readTotalBytes -= numBits shr 3
            if (readBit == 0) {
                readBit = 8 - (numBits and 7)
            } else {
                readBit -= numBits and 7
                if (readBit <= 0) {
                    readByte--
                    readTotalBytes--
                    readBit = readBit + 8 and 7
                }
            }
            if (readByte < 0) {
                readByte = 0
                readBit = 0
            }
        }

        protected fun Compare(src1: ByteArray, bitPtr1: Int, src2: ByteArray, bitPtr2: Int, maxBits: Int): Int {
            var bitPtr1 = bitPtr1
            var bitPtr2 = bitPtr2
            var i: Int

            // If the two bit pointers are aligned then we can use a faster comparison
            return if (bitPtr1 and 7 == bitPtr2 and 7 && maxBits > 16) {
                var p1 = bitPtr1 shr 3
                var p2 = bitPtr2 shr 3
                var bits = 0
                var bitsRemain = maxBits

                // Compare the first couple bits (if any)
                if (bitPtr1 and 7 != 0) {
                    i = bitPtr1 and 7
                    while (i < 8) {
                        if (src1[p1].toInt() shr i xor (src2[p2].toInt() shr i) and 1 == 1) {
                            return bits
                        }
                        bitsRemain--
                        i++
                        bits++
                    }
                    p1++
                    p2++
                }
                var remain = bitsRemain shr 3

                // Compare the middle bytes as ints
                // FIX: C++ compares 4 bytes at once via *(const int*)p1 == *(const int*)p2
                // Original Kotlin compared only 1 byte (src1[p1]) but skipped 4, missing mismatches
                while (remain >= 4 && src1[p1] == src2[p2] && src1[p1 + 1] == src2[p2 + 1] && src1[p1 + 2] == src2[p2 + 2] && src1[p1 + 3] == src2[p2 + 3]) {
                    p1 += 4
                    p2 += 4
                    remain -= 4
                    bits += 32
                }

                // Compare the remaining bytes
                while (remain > 0 && src1[p1] == src2[p2]) {
                    p1++
                    p2++
                    remain--
                    bits += 8
                }

                // Compare the last couple of bits (if any)
                var finalBits = 8
                if (remain == 0) {
                    finalBits = bitsRemain and 7
                }
                i = 0
                while (i < finalBits) {
                    if (src1[p1].toInt() shr i xor (src2[p2].toInt() shr i) and 1 == 1) {
                        return bits
                    }
                    i++
                    bits++
                }
                assert(bits == maxBits)
                bits
            } else {
                i = 0
                while (i < maxBits) {
                    if (src1[bitPtr1 shr 3].toInt() shr (bitPtr1 and 7) xor (src2[bitPtr2 shr 3].toInt() shr (bitPtr2 and 7)) and 1 == 1) {
                        break
                    }
                    bitPtr1++
                    bitPtr2++
                    i++
                }
                i
            }
        }
    }

    /*
     =================================================================================

     idCompressor_RunLength

     The following algorithm implements run length compression with an arbitrary
     word size.

     =================================================================================
     */
    internal class idCompressor_RunLength : idCompressor_BitStream() {
        //
        //
        private var runLengthCode = 0
        override fun Init(f: idFile, compress: Boolean, wordLength: Int) {
            super.Init(f, compress, wordLength)
            runLengthCode = (1 shl wordLength) - 1
        }

        override fun Write(inData: ByteBuffer, inLength: Int): Int {
            var bits: Int
            var nextBits: Int
            var count: Int
            if (compress == false || inLength <= 0) {
                return 0
            }
            InitCompress(inData, inLength)
            while (readByte <= readLength) {
                count = 1
                bits = ReadBits(wordLength)
                nextBits = ReadBits(wordLength)
                while (nextBits == bits) {
                    count++
                    if (count >= 1 shl wordLength) {
                        if (count >= (1 shl wordLength) + 3 || bits == runLengthCode) {
                            break
                        }
                    }
                    nextBits = ReadBits(wordLength)
                }
                if (nextBits != bits) {
                    UnreadBits(wordLength)
                }
                if (count > 3 || bits == runLengthCode) {
                    WriteBits(runLengthCode, wordLength)
                    WriteBits(bits, wordLength)
                    if (bits != runLengthCode) {
                        count -= 3
                    }
                    WriteBits(count - 1, wordLength)
                } else {
                    while (count-- != 0) {
                        WriteBits(bits, wordLength)
                    }
                }
            }
            return inLength
        }

        override fun Read(outData: ByteBuffer, outLength: Int): Int {
            var bits: Int
            var count: Int
            if (compress == true || outLength <= 0) {
                return 0
            }
            InitDecompress(outData, outLength)
            while (writeByte <= writeLength && readLength >= 0) {
                bits = ReadBits(wordLength)
                if (bits == runLengthCode) {
                    bits = ReadBits(wordLength)
                    count = ReadBits(wordLength) + 1
                    if (bits != runLengthCode) {
                        count += 3
                    }
                    while (count-- != 0) {
                        WriteBits(bits, wordLength)
                    }
                } else {
                    WriteBits(bits, wordLength)
                }
            }
            return writeByte
        }
    }

    /*
     =================================================================================

     idCompressor_RunLength_ZeroBased

     The following algorithm implements run length compression with an arbitrary
     word size for data with a lot of zero bits.

     =================================================================================
     */
    internal class idCompressor_RunLength_ZeroBased : idCompressor_BitStream() {
        override fun Write(inData: ByteBuffer, inLength: Int): Int {
            var bits: Int
            var count: Int
            if (compress == false || inLength <= 0) {
                return 0
            }
            InitCompress(inData, inLength)
            while (readByte <= readLength) {
                count = 0
                bits = ReadBits(wordLength)
                while (bits == 0 && count < 1 shl wordLength) {
                    count++
                    bits = ReadBits(wordLength)
                }
                if (count != 0) {
                    WriteBits(0, wordLength)
                    WriteBits(count - 1, wordLength)
                    UnreadBits(wordLength)
                } else {
                    WriteBits(bits, wordLength)
                }
            }
            return inLength
        }

        override fun Read(outData: ByteBuffer, outLength: Int): Int {
            var bits: Int
            var count: Int
            if (compress == true || outLength <= 0) {
                return 0
            }
            InitDecompress(outData, outLength)
            while (writeByte <= writeLength && readLength >= 0) {
                bits = ReadBits(wordLength)
                if (bits == 0) {
                    count = ReadBits(wordLength) + 1
                    while (count-- > 0) {
                        WriteBits(0, wordLength)
                    }
                } else {
                    WriteBits(bits, wordLength)
                }
            }
            return writeByte
        }
    }

    /*
     =================================================================================

     idCompressor_Huffman

     The following algorithm is based on the adaptive Huffman algorithm described
     in Sayood's Data Compression book. The ranks are not actually stored, but
     implicitly defined by the location of a node within a doubly-linked list

     =================================================================================
     */
    internal class idCompressor_Huffman  //
    //
        : idCompressor_None() {
        private var bloc = 0
        private var blocIn = 0
        private var blocMax = 0
        private var blocNode = 0
        private var blocPtrs = 0

        //
        private var compressedSize = 0

        // FIX: freelist was Array<huffmanNode_t> — should be NodeSlot? to model C++ huffmanNode_t** freelist
        private var freelist: NodeSlot? = null
        private var lhead: huffmanNode_t? = null
        private val loc: Array<huffmanNode_t?> = arrayOfNulls<huffmanNode_t?>(HMAX + 1)
        private var ltail: huffmanNode_t? = null

        //
        private val nodeList: Array<huffmanNode_t> = Array(768) { huffmanNode_t() }

        // FIX: nodePtrs was Array<huffmanNode_t> — should be Array<NodeSlot> to model C++ huffmanNode_t* nodePtrs[768]
        private val nodePtrs: Array<NodeSlot> = Array(768) { NodeSlot() }
        private val seq = ByteBuffer.allocate(65536)

        //
        private var tree: huffmanNode_t? = null
        private var unCompressedSize = 0
        override fun Init(f: idFile, compress: Boolean, wordLength: Int) {
            var i: Int
            file = f
            this.compress = compress
            bloc = 0
            blocMax = 0
            blocIn = 0
            blocNode = 0
            blocPtrs = 0
            compressedSize = 0
            unCompressedSize = 0
            tree = null
            lhead = null
            ltail = null
            i = 0
            while (i < HMAX + 1) {
                loc[i] = null
                i++
            } // FIX: C++ sets freelist = NULL; was emptyArray() which is never null
            freelist = null
            i = 0
            while (i < 768) { // FIX: C++ does memset(&nodeList[i], 0, sizeof(huffmanNode_t)) and nodePtrs[i] = NULL
                // The Kotlin loop body was empty — breaks on second Init call
                nodeList[i].reset()
                nodePtrs[i].node = null
                nodePtrs[i].nextFree = null
                i++
            }
            if (compress) { // Add the NYT (not yet transmitted) node into the tree/list
                loc[NYT] = nodeList[blocNode++]
                lhead = loc[NYT]
                tree = lhead
                tree!!.symbol = NYT
                tree!!.weight = 0
                lhead!!.prev = null
                lhead!!.next = lhead!!.prev
                tree!!.right = null
                tree!!.left = tree!!.right
                tree!!.parent = tree!!.left
                loc[NYT] = tree
            } else { // Initialize the tree & list with the NYT node
                loc[NYT] = nodeList[blocNode++]
                ltail = loc[NYT]
                lhead = ltail
                tree = lhead
                tree!!.symbol = NYT
                tree!!.weight = 0
                lhead!!.prev = null
                lhead!!.next = lhead!!.prev
                tree!!.right = null
                tree!!.left = tree!!.right
                tree!!.parent = tree!!.left
            }
        }

        override fun FinishCompress() {
            if (compress == false) {
                return
            }
            bloc += 7
            val str = bloc shr 3
            if (str != 0) {
                file.Write(seq, str)
                compressedSize += str
            }
        }

        override fun GetCompressionRatio(): Float {
            return (unCompressedSize - compressedSize) * 100.0f / unCompressedSize
        }

        override fun Write(inData: ByteBuffer, inLength: Int): Int {
            var i: Int
            var ch: Int
            if (compress == false || inLength <= 0) {
                return 0
            }
            i = 0
            while (i < inLength) { // FIX: C++ reads a single byte ch = ((const byte*)inData)[i]; was using getInt (reads 4 bytes)
                ch = inData.get(i).toInt() and 0xFF
                Transmit(ch, seq) // Transmit symbol
                AddRef(ch.toByte()) // Do update
                val b = bloc shr 3
                if (b > 32768) {
                    file.Write(seq, b)
                    seq.put(0, seq[b])
                    bloc = bloc and 7
                    compressedSize += b
                }
                i++
            }
            unCompressedSize += i
            return i
        }

        override fun Read(outData: ByteBuffer, outLength: Int): Int {
            var i: Int
            var j: Int
            val ch = IntArray(1)
            if (compress == true || outLength <= 0) {
                return 0
            }
            if (bloc == 0) {
                blocMax = file.Read(seq)
                blocIn = 0
            }
            i = 0
            while (i < outLength) {
                ch[0] = 0 // don't overflow reading from the file
                if (bloc shr 3 > blocMax) {
                    break
                }
                Receive(tree, ch) // Get a character
                if (ch[0] == NYT) {        // We got a NYT, get the symbol associated with it
                    ch[0] = 0
                    j = 0
                    while (j < 8) {
                        ch[0] = (ch[0] shl 1) + Get_bit()
                        j++
                    }
                } // FIX: C++ writes a single byte: ((byte*)outData)[i] = ch; was using putInt (writes 4 bytes)
                outData.put(i, ch[0].toByte()) // Write symbol
                AddRef(ch[0].toByte()) // Increment node
                i++
            }
            compressedSize = bloc shr 3
            unCompressedSize += i
            return i
        }

        // FIX: Rewritten to use NodeSlot for head pointer-pointer semantics
        private fun AddRef(ch: Byte) {
            val tnode: huffmanNode_t
            val tnode2: huffmanNode_t // FIX: ch.toInt() sign-extends for bytes > 127 (e.g. 0xFF -> -1), causing AIOOBE.
            // C++ uses unsigned char (0-255); mask with 0xFF to simulate unsigned.
            val chIdx = ch.toInt() and 0xFF
            if (loc[chIdx] == null) { /* if this is the first transmission of this node */
                tnode = nodeList[blocNode++]
                tnode2 = nodeList[blocNode++]
                tnode2.symbol = INTERNAL_NODE
                tnode2.weight = 1
                tnode2.next = lhead!!.next
                if (lhead!!.next != null) {
                    lhead!!.next!!.prev = tnode2
                    if (lhead!!.next!!.weight == 1) {
                        tnode2.head = lhead!!.next!!.head
                    } else {
                        tnode2.head = Get_ppnode()
                        tnode2.head!!.node = tnode2  // C++: *tnode2->head = tnode2
                    }
                } else {
                    tnode2.head = Get_ppnode()
                    tnode2.head!!.node = tnode2  // C++: *tnode2->head = tnode2
                }
                lhead!!.next = tnode2
                tnode2.prev = lhead
                tnode.symbol = chIdx
                tnode.weight = 1
                tnode.next = lhead!!.next
                if (lhead!!.next != null) {
                    lhead!!.next!!.prev = tnode
                    if (lhead!!.next!!.weight == 1) {
                        tnode.head = lhead!!.next!!.head
                    } else {/* this should never happen */
                        tnode.head = Get_ppnode()
                        tnode.head!!.node = tnode2  // C++: *tnode->head = tnode2
                    }
                } else {/* this should never happen */
                    tnode.head = Get_ppnode()
                    tnode.head!!.node = tnode  // C++: *tnode->head = tnode
                }
                lhead!!.next = tnode
                tnode.prev = lhead
                tnode.right = null
                tnode.left = tnode.right
                if (lhead!!.parent != null) {
                    if (lhead!!.parent!!.left == lhead) { /* lhead is guaranteed to by the NYT */
                        lhead!!.parent!!.left = tnode2
                    } else {
                        lhead!!.parent!!.right = tnode2
                    }
                } else {
                    tree = tnode2
                }
                tnode2.right = tnode
                tnode2.left = lhead
                tnode2.parent = lhead!!.parent
                tnode.parent = tnode2
                lhead!!.parent = tnode.parent
                loc[chIdx] = tnode
                Increment(tnode2.parent as huffmanNode_t?)
            } else {
                Increment(loc[chIdx])
            }
        }

        /*
         ================
         idCompressor_Huffman::Receive

         Get a symbol.
         ================
         */
        private fun Receive(node: huffmanNode_t?, ch: IntArray): Int {
            var node = node
            while (node != null && node.symbol == INTERNAL_NODE) {
                node = if (Get_bit() != 0) {
                    node.right as huffmanNode_t?
                } else {
                    node.left as huffmanNode_t?
                }
            }
            return node?.symbol?.also { ch[0] = it } ?: 0
        }

        /*
         ================
         idCompressor_Huffman::Transmit

         Send a symbol.
         ================
         */
        private fun Transmit(ch: Int, fout: ByteBuffer) {
            var i: Int
            if (loc[ch] == null) {/* huffmanNode_t hasn't been transmitted, send a NYT, then the symbol */
                Transmit(NYT, fout)
                i = 7
                while (i >= 0) {
                    Add_bit(ch shr i and 0x1, fout)
                    i--
                }
            } else {
                Send(loc[ch]!!, null, fout)
            }
        }

        private fun PutBit(bit: Int, fout: ByteArray, offset: IntArray) {
            bloc = offset[0]
            if (bloc and 7 == 0) {
                fout[bloc shr 3] = 0
            }
            fout[bloc shr 3] = fout[bloc shr 3] or (bit shl (bloc and 7)).toByte()
            bloc++
            offset[0] = bloc
        }

        private fun GetBit(fin: ByteArray, offset: IntArray): Int {
            val t: Int
            bloc = offset[0]
            t = fin[bloc shr 3].toInt() shr (bloc and 7) and 0x1
            bloc++
            offset[0] = bloc
            return t
        }

        //
        /*
         ================
         idCompressor_Huffman::Add_bit

         Add a bit to the output file (buffered)
         ================
         */
        private fun Add_bit(bit: Int, fout: ByteBuffer) {
            val pos = bloc shr 3
            val `val` = bit shl (bloc and 7)
            if (bloc and 7 == 0) { // FIX: C++ zeroes a single byte; was using putInt (overwrites 4 bytes)
                fout.put(pos, 0.toByte())
            } // FIX: C++ does single-byte OR; was using putInt (overwrites 4 bytes)
            fout.put(pos, (fout.get(pos).toInt() and 0xFF or `val`).toByte())
            bloc++
        }

        /*
         ================
         idCompressor_Huffman::Get_bit

         Get one bit from the input file (buffered)
         ================
         */
        private fun Get_bit(): Int {
            val t: Int
            var wh = bloc shr 3
            val whb = wh shr 16
            if (whb != blocIn) {
                blocMax += file.Read(seq /*, sizeof( seq )*/)
                blocIn++
            }
            wh = wh and 0xffff
            t = seq[wh].toInt() shr (bloc and 7) and 0x1
            bloc++
            return t
        }

        // FIX: Rewritten to use NodeSlot — models C++ huffmanNode_t** Get_ppnode()
        private fun Get_ppnode(): NodeSlot {
            return if (freelist == null) {
                nodePtrs[blocPtrs++]
            } else {
                val slot = freelist!!
                freelist = slot.nextFree
                slot.nextFree = null
                slot
            }
        }

        // FIX: Rewritten to use NodeSlot — models C++ void Free_ppnode(huffmanNode_t** ppnode)
        private fun Free_ppnode(slot: NodeSlot) {
            slot.node = null
            slot.nextFree = freelist
            freelist = slot
        }

        /*
         ================
         idCompressor_Huffman::Swap

         Swap the location of the given two nodes in the tree.
         ================
         */
        private fun Swap(node1: huffmanNode_t, node2: huffmanNode_t) {
            val par1: nodetype?
            val par2: nodetype?
            par1 = node1.parent
            par2 = node2.parent
            if (par1 != null) {
                if (par1.left == node1) {
                    par1.left = node2
                } else {
                    par1.right = node2
                }
            } else {
                tree = node2
            }
            if (par2 != null) {
                if (par2.left == node2) {
                    par2.left = node1
                } else {
                    par2.right = node1
                }
            } else {
                tree = node1
            }
            node1.parent = par2
            node2.parent = par1
        }

        /*
         ================
         idCompressor_Huffman::Swaplist

         Swap the given two nodes in the linked list (update ranks)
         ================
         */
        private fun Swaplist(node1: huffmanNode_t, node2: huffmanNode_t) {
            var par1: nodetype?
            par1 = node1.next
            node1.next = node2.next
            node2.next = par1
            par1 = node1.prev
            node1.prev = node2.prev
            node2.prev = par1
            if (node1.next == node1) {
                node1.next = node2
            }
            if (node2.next == node2) {
                node2.next = node1
            }
            if (node1.next != null) {
                node1.next!!.prev = node1
            }
            if (node2.next != null) {
                node2.next!!.prev = node2
            }
            if (node1.prev != null) {
                node1.prev!!.next = node1
            }
            if (node2.prev != null) {
                node2.prev!!.next = node2
            }
        }

        // FIX: Rewritten to use NodeSlot — matches C++ pointer-pointer dereference semantics
        private fun Increment(node: huffmanNode_t?) {
            val lnode: huffmanNode_t?
            if (null == node) {
                return
            }
            if (node.next != null && node.next!!.weight == node.weight) {
                lnode = node.head!!.node  // C++: lnode = *node->head
                if (lnode !== node.parent) {
                    Swap(lnode!!, node)
                }
                Swaplist(lnode!!, node)
            }
            if (node.prev != null && node.prev!!.weight == node.weight) {
                node.head!!.node = node.prev as huffmanNode_t?  // C++: *node->head = node->prev
            } else {
                node.head!!.node = null       // C++: *node->head = NULL
                Free_ppnode(node.head!!)       // C++: Free_ppnode(node->head)
            }
            node.weight++
            if (node.next != null && node.next!!.weight == node.weight) {
                node.head = node.next!!.head
            } else {
                node.head = Get_ppnode()
                node.head!!.node = node       // C++: *node->head = node
            }
            if (node.parent != null) {
                Increment(node.parent as huffmanNode_t?)
                if (node.prev == node.parent) {
                    Swaplist(node, node.parent as huffmanNode_t)
                    if (node.head!!.node == node) {  // C++: if (*node->head == node)
                        node.head!!.node = node.parent as huffmanNode_t?  // C++: *node->head = node->parent
                    }
                }
            }
        }

        /*
         ================
         idCompressor_Huffman::Send

         Send the prefix code for this node.
         ================
         */
        private fun Send(node: huffmanNode_t, child: huffmanNode_t?, fout: ByteBuffer) {
            if (node.parent != null) {
                Send(node.parent as huffmanNode_t, node, fout)
            }
            if (child != null) {
                if (node.right == child) {
                    Add_bit(1, fout)
                } else {
                    Add_bit(0, fout)
                }
            }
        }
    }

    /*
     =================================================================================

     idCompressor_Arithmetic

     The following algorithm is based on the Arithmetic Coding methods described
     by Mark Nelson. The probability table is implicitly stored.

     =================================================================================
     */
    internal class idCompressor_Arithmetic : idCompressor_BitStream() {
        private var code = 0
        private var high = 0

        //
        private var low = 0
        private val probabilities: Array<acProbs_t> = Array(1 shl AC_WORD_LENGTH) { acProbs_t() }
        private var scale: Long = 0
        private var symbolBit = 0

        //
        private var symbolBuffer = 0
        private var underflowBits: Long = 0
        override fun Init(f: idFile, compress: Boolean, wordLength: Int) {
            super.Init(f, compress, wordLength)
            symbolBuffer = 0
            symbolBit = 0
        }

        override fun FinishCompress() {
            if (compress == false) {
                return
            }
            WriteOverflowBits()
            super.FinishCompress()
        }

        override fun Write(inData: ByteBuffer, inLength: Int): Int {
            var i: Int
            var j: Int
            if (compress == false || inLength <= 0) {
                return 0
            }
            InitCompress(inData, inLength)
            i = 0
            while (i < inLength) {
                if (readTotalBytes and (1 shl 14) - 1 == 0) {
                    if (readTotalBytes != 0) {
                        WriteOverflowBits()
                        WriteBits(0, 15)
                        while (writeBit != 0) {
                            WriteBits(0, 1)
                        }
                        WriteBits(255, 8)
                    }
                    InitProbabilities()
                }
                j = 0
                while (j < 8) {
                    PutBit(ReadBits(1))
                    j++
                }
                i++
            }
            return inLength
        }

        //
        //
        override fun Read(outData: ByteBuffer, outLength: Int): Int {
            var i: Int
            var j: Int
            if (compress == true || outLength <= 0) {
                return 0
            }
            InitDecompress(outData, outLength)
            i = 0
            while (i < outLength && readLength >= 0) {
                if (writeTotalBytes and (1 shl 14) - 1 == 0) {
                    if (writeTotalBytes != 0) {
                        while (readBit != 0) {
                            ReadBits(1)
                        }
                        while (ReadBits(8) == 0 && readLength > 0) {
                        }
                    }
                    InitProbabilities()
                    j = 0
                    while (j < AC_NUM_BITS) { // FIX: mask code to 16 bits (C++ unsigned short)
                        code = ((code shl 1) or ReadBits(1)) and 0xFFFF
                        j++
                    }
                }
                j = 0
                while (j < 8) {
                    WriteBits(GetBit(), 1)
                    j++
                }
                i++
            }
            return i
        }

        private fun InitProbabilities() {
            high = AC_HIGH_INIT
            low = AC_LOW_INIT
            underflowBits = 0
            code = 0
            for (i in 0 until (1 shl AC_WORD_LENGTH)) {
                probabilities[i].low = i.toLong()
                probabilities[i].high = (i + 1).toLong()
            }
            scale = (1 shl AC_WORD_LENGTH).toLong()
        }

        private fun UpdateProbabilities(symbol: acSymbol_t) {
            var i: Int
            val x: Int
            x = symbol.position
            probabilities[x].high++
            i = x + 1
            while (i < 1 shl AC_WORD_LENGTH) {
                probabilities[i].low++
                probabilities[i].high++
                i++
            }
            scale++
        }

        private fun ProbabilityForCount(count: Long): Int {
            return if (true) {
                var len: Int
                var mid: Int
                var offset: Int
                var res: Int
                len = 1 shl AC_WORD_LENGTH
                mid = len
                offset = 0
                res = 0
                while (mid > 0) {
                    mid = len shr 1
                    if (count >= probabilities[offset + mid].high) {
                        offset += mid
                        len -= mid
                        res = 1
                    } else if (count < probabilities[offset + mid].low) {
                        len -= mid
                        res = 0
                    } else {
                        return offset + mid
                    }
                }
                offset + res
            } else {
                var j: Int
                j = 0
                while (j < 1 shl AC_WORD_LENGTH) {
                    if (count >= probabilities[j].low && count < probabilities[j].high) {
                        return j
                    }
                    j++
                }
                assert(false)
                0
            }
        }

        private fun CharToSymbol(c: Int, symbol: acSymbol_t) {
            symbol.low = probabilities[c].low
            symbol.high = probabilities[c].high
            symbol.position = c
        }

        private fun EncodeSymbol(symbol: acSymbol_t) {
            val range: Int

            // rescale high and low for the new symbol.
            range = high - low + 1 // FIX: C++ uses unsigned short — must mask to 16 bits to prevent overflow
            high = (low + (range.toLong() * symbol.high / scale - 1).toInt()) and 0xFFFF
            low = (low + (range.toLong() * symbol.low / scale).toInt()) and 0xFFFF
            while (true) {
                if (high and AC_MSB_MASK == low and AC_MSB_MASK) { // the high digits of low and high have converged, and can be written to the stream
                    WriteBits(high shr AC_MSB_SHIFT, 1)
                    while (underflowBits > 0) {
                        WriteBits(high.inv() shr AC_MSB_SHIFT, 1)
                        underflowBits--
                    }
                } else if (low and AC_MSB2_MASK != 0 && 0 == high and AC_MSB2_MASK) { // underflow is in danger of happening, 2nd digits are converging but 1st digits don't match
                    underflowBits += 1
                    low = low and AC_MSB2_MASK - 1
                    high = high or AC_MSB2_MASK
                } else {
                    UpdateProbabilities(symbol)
                    return
                } // FIX: mask to 16 bits after shift (C++ unsigned short auto-truncates)
                low = (low shl 1) and 0xFFFF
                high = ((high shl 1) or 1) and 0xFFFF
            }
        }

        private fun SymbolFromCount(count: Long, symbol: acSymbol_t): Int {
            val p = ProbabilityForCount(count)
            symbol.low = probabilities[p].low
            symbol.high = probabilities[p].high
            symbol.position = p
            return p
        }

        private fun GetCurrentCount(): Int {
            return (((code - low + 1) * scale - 1) / (high - low + 1)).toInt()
        }

        //
        private fun RemoveSymbolFromStream(symbol: acSymbol_t) {
            val range: Long
            range = (high - low).toLong() + 1 // FIX: C++ uses unsigned short — must mask to 16 bits
            high = (low + (range * symbol.high / scale - 1).toInt()) and 0xFFFF
            low = (low + (range * symbol.low / scale).toInt()) and 0xFFFF
            while (true) {
                if (high and AC_MSB_MASK == low and AC_MSB_MASK) {
                } else if (low and AC_MSB2_MASK == AC_MSB2_MASK && high and AC_MSB2_MASK == 0) {
                    code = code xor AC_MSB2_MASK
                    low = low and AC_MSB2_MASK - 1
                    high = high or AC_MSB2_MASK
                } else {
                    UpdateProbabilities(symbol)
                    return
                } // FIX: mask to 16 bits after shift (C++ unsigned short auto-truncates)
                low = (low shl 1) and 0xFFFF
                high = ((high shl 1) or 1) and 0xFFFF
                code = ((code shl 1) or ReadBits(1)) and 0xFFFF
            }
        }

        private fun PutBit(putbit: Int) {
            symbolBuffer = symbolBuffer or (putbit and 1 shl symbolBit)
            symbolBit++
            if (symbolBit >= AC_WORD_LENGTH) {
                val symbol = acSymbol_t()
                CharToSymbol(symbolBuffer, symbol)
                EncodeSymbol(symbol)
                symbolBit = 0
                symbolBuffer = 0
            }
        }

        //
        private fun GetBit(): Int {
            val getbit: Int
            if (symbolBit <= 0) { // read a new symbol out
                val symbol = acSymbol_t()
                symbolBuffer = SymbolFromCount(GetCurrentCount().toLong(), symbol)
                RemoveSymbolFromStream(symbol)
                symbolBit = AC_WORD_LENGTH
            }
            getbit = symbolBuffer shr AC_WORD_LENGTH - symbolBit and 0x1
            symbolBit--
            return getbit
        }

        private fun WriteOverflowBits() {
            WriteBits(low shr AC_MSB2_SHIFT, 1)
            underflowBits++
            while (underflowBits-- > 0) {
                WriteBits(low.inv() shr AC_MSB2_SHIFT, 1)
            }
        }

        private open class acProbs_s {
            var high: Long = 0
            var low: Long = 0
        }

        //
        private class acProbs_t : acProbs_s()
        private open class acSymbol_s {
            var high: Long = 0
            var low: Long = 0
            var position = 0
        }

        //
        private class acSymbol_t : acSymbol_s()
    }

    /*
     =================================================================================

     idCompressor_LZSS

     In 1977 Abraham Lempel and Jacob Ziv presented a dictionary based scheme for
     text compression called LZ77. For any new text LZ77 outputs an offset/length
     pair to previously seen text and the next new byte after the previously seen
     text.

     In 1982 James Storer and Thomas Szymanski presented a modification on the work
     of Lempel and Ziv called LZSS. LZ77 always outputs an offset/length pair, even
     if a match is only one byte long. An offset/length pair usually takes more than
     a single byte to store and the compression is not optimal for small match sizes.
     LZSS uses a bit flag which tells whether the following data is a literal (byte)
     or an offset/length pair.

     The following algorithm is an implementation of LZSS with arbitrary word size.

     =================================================================================
     */
    internal open class idCompressor_LZSS  //
    //
        : idCompressor_BitStream() {
        //
        protected var block: ByteArray = ByteArray(LZSS_BLOCK_SIZE)
        protected var blockIndex = 0
        protected var blockSize = 0
        protected var hashNext: IntArray = IntArray(LZSS_BLOCK_SIZE * 8)

        //
        protected var hashTable: IntArray = IntArray(LZSS_HASH_SIZE)
        protected var lengthBits = 0
        protected var minMatchWords = 0
        protected var offsetBits = 0
        override fun Init(f: idFile, compress: Boolean, wordLength: Int) {
            super.Init(f, compress, wordLength)
            offsetBits = LZSS_OFFSET_BITS
            lengthBits = LZSS_LENGTH_BITS
            minMatchWords = (offsetBits + lengthBits + wordLength) / wordLength
            blockSize = 0
            blockIndex = 0
        }

        override fun FinishCompress() {
            if (compress == false) {
                return
            }
            if (blockSize != 0) {
                CompressBlock()
            }
            super.FinishCompress()
        }

        //
        override fun Write(inData: ByteBuffer, inLength: Int): Int {
            var i: Int
            var n: Int
            if (compress == false || inLength <= 0) {
                return 0
            }
            n = 0.also { i = it }
            while (i < inLength) {
                n = LZSS_BLOCK_SIZE - blockSize
                if (inLength - i >= n) { // FIX: inData.get(block, i, n) reads from ByteBuffer.position() into block[i], not block[blockSize].
                    // C++: memcpy(block + blockSize, inData + i, n)  -- use arraycopy like the else-branch.
                    System.arraycopy(inData.array(), i, block, blockSize, n)
                    blockSize = LZSS_BLOCK_SIZE
                    CompressBlock()
                    blockSize = 0
                } else { //			memcpy( block + blockSize, ((const byte *)inData) + i, inLength - i );
                    System.arraycopy(inData.array(), i, block, blockSize, inLength - i)
                    n = inLength - i
                    blockSize += n
                }
                i += n
            }
            return inLength
        }

        override fun Read(outData: ByteBuffer, outLength: Int): Int {
            var i: Int
            var n: Int
            if (compress == true || outLength <= 0) {
                return 0
            }
            if (0 == blockSize) {
                DecompressBlock()
            }
            n = 0.also { i = it }
            while (i < outLength) {
                if (0 == blockSize) {
                    return i
                }
                n = blockSize - blockIndex
                if (outLength - i >= n) { //			memcpy( ((byte *)outData) + i, block + blockIndex, n );
                    System.arraycopy(block, blockIndex, outData.array(), i, n)
                    DecompressBlock()
                    blockIndex = 0
                } else { //			memcpy( ((byte *)outData) + i, block + blockIndex, outLength - i );
                    System.arraycopy(block, blockIndex, outData.array(), i, outLength - i)
                    n = outLength - i
                    blockIndex += n
                }
                i += n
            }
            return outLength
        }

        protected fun FindMatch(startWord: Int, startValue: Int, wordOffset: IntArray, numWords: IntArray): Boolean {
            var i: Int
            var n: Int
            val hash: Int
            val bottom: Int
            val maxBits: Int
            wordOffset[0] = startWord
            numWords[0] = minMatchWords - 1
            bottom = Max(0, startWord - ((1 shl offsetBits) - 1))
            maxBits = (blockSize shl 3) - startWord * wordLength
            hash = startValue and LZSS_HASH_MASK
            i = hashTable[hash]
            while (i >= bottom) {
                n = Compare(
                    block, i * wordLength, block, startWord * wordLength, Min(maxBits, (startWord - i) * wordLength)
                )
                if (n > numWords[0] * wordLength) {
                    numWords[0] = n / wordLength
                    wordOffset[0] = i
                    if (numWords[0] > (1 shl lengthBits) - 1 + minMatchWords - 1) {
                        numWords[0] = (1 shl lengthBits) - 1 + minMatchWords - 1
                        break
                    }
                }
                i = hashNext[i]
            }
            return numWords[0] >= minMatchWords
        }

        protected fun AddToHash(index: Int, hash: Int) {
            hashNext[index] = hashTable[hash]
            hashTable[hash] = index
        }

        protected fun GetWordFromBlock(wordOffset: Int): Int {
            var blockBit: Int
            var blockByte: Int
            var value: Int
            var valueBits: Int
            var get: Int
            var fraction: Int
            blockBit = wordOffset * wordLength and 7
            blockByte = wordOffset * wordLength shr 3
            if (blockBit != 0) {
                blockByte++
            }
            value = 0
            valueBits = 0
            while (valueBits < wordLength) {
                if (blockBit == 0) {
                    if (blockByte >= LZSS_BLOCK_SIZE) {
                        return value
                    }
                    blockByte++
                }
                get = 8 - blockBit
                if (get > wordLength - valueBits) {
                    get = wordLength - valueBits
                }
                fraction = block[blockByte - 1].toInt()
                fraction = fraction shr blockBit
                fraction = fraction and (1 shl get) - 1
                value = value or (fraction shl valueBits)
                valueBits += get
                blockBit = blockBit + get and 7
            }
            return value
        }

        protected open fun CompressBlock() {
            var i: Int
            var startWord: Int
            var startValue: Int
            val wordOffset = IntArray(1)
            val numWords = IntArray(1)
            InitCompress(block, blockSize)

            //	memset( hashTable, -1, sizeof( hashTable ) );
            //	memset( hashNext, -1, sizeof( hashNext ) );
            Arrays.fill(hashTable, -1)
            Arrays.fill(hashNext, -1)
            startWord = 0
            while (readByte < readLength) {
                startValue = ReadBits(wordLength)
                if (FindMatch(startWord, startValue, wordOffset, numWords)) {
                    WriteBits(1, 1)
                    WriteBits(startWord - wordOffset[0], offsetBits)
                    WriteBits(numWords[0] - minMatchWords, lengthBits)
                    UnreadBits(wordLength)
                    i = 0
                    while (i < numWords[0]) {
                        startValue = ReadBits(wordLength)
                        AddToHash(startWord, startValue and LZSS_HASH_MASK)
                        startWord++
                        i++
                    }
                } else {
                    WriteBits(0, 1)
                    WriteBits(startValue, wordLength)
                    AddToHash(startWord, startValue and LZSS_HASH_MASK)
                    startWord++
                }
            }
            blockSize = 0
        }

        protected open fun DecompressBlock() {
            var i: Int
            var offset: Int
            var startWord: Int
            var numWords: Int
            InitDecompress(block, LZSS_BLOCK_SIZE)
            startWord = 0
            while (writeByte < writeLength && readLength >= 0) {
                if (ReadBits(1) != 0) {
                    offset = startWord - ReadBits(offsetBits)
                    numWords = ReadBits(lengthBits) + minMatchWords
                    i = 0
                    while (i < numWords) {
                        WriteBits(GetWordFromBlock(offset + i), wordLength)
                        startWord++
                        i++
                    }
                } else {
                    WriteBits(ReadBits(wordLength), wordLength)
                    startWord++
                }
            }
            blockSize = Min(writeByte, LZSS_BLOCK_SIZE)
        }
    }

    /*
     =================================================================================

     idCompressor_LZSS_WordAligned

     Outputs word aligned compressed data.

     =================================================================================
     */
    internal class idCompressor_LZSS_WordAligned : idCompressor_LZSS() {
        override fun Init(f: idFile, compress: Boolean, wordLength: Int) {
            super.Init(f, compress, wordLength)
            offsetBits = 2 * wordLength
            lengthBits = wordLength
            minMatchWords = (offsetBits + lengthBits + wordLength) / wordLength
            blockSize = 0
            blockIndex = 0
        }

        override fun CompressBlock() {
            var i: Int
            var startWord: Int
            var startValue: Int
            val wordOffset = IntArray(1)
            val numWords = IntArray(1)
            InitCompress(block, blockSize)

            //	memset( hashTable, -1, sizeof( hashTable ) );
            //	memset( hashNext, -1, sizeof( hashNext ) );
            Arrays.fill(hashTable, -1)
            Arrays.fill(hashNext, -1)
            startWord = 0
            while (readByte < readLength) {
                startValue = ReadBits(wordLength)
                if (FindMatch(startWord, startValue, wordOffset, numWords)) {
                    WriteBits(numWords[0] - (minMatchWords - 1), lengthBits)
                    WriteBits(startWord - wordOffset[0], offsetBits)
                    UnreadBits(wordLength)
                    i = 0
                    while (i < numWords[0]) {
                        startValue = ReadBits(wordLength)
                        AddToHash(startWord, startValue and LZSS_HASH_MASK)
                        startWord++
                        i++
                    }
                } else {
                    WriteBits(0, lengthBits)
                    WriteBits(startValue, wordLength)
                    AddToHash(startWord, startValue and LZSS_HASH_MASK)
                    startWord++
                }
            }
            blockSize = 0
        }

        override fun DecompressBlock() {
            var i: Int
            var offset: Int
            var startWord: Int
            var numWords: Int
            InitDecompress(block, LZSS_BLOCK_SIZE)
            startWord = 0
            while (writeByte < writeLength && readLength >= 0) {
                numWords = ReadBits(lengthBits)
                if (numWords != 0) {
                    numWords += minMatchWords - 1
                    offset = startWord - ReadBits(offsetBits)
                    i = 0
                    while (i < numWords) {
                        WriteBits(GetWordFromBlock(offset + i), wordLength)
                        startWord++
                        i++
                    }
                } else {
                    WriteBits(ReadBits(wordLength), wordLength)
                    startWord++
                }
            }
            blockSize = Min(writeByte, LZSS_BLOCK_SIZE)
        }
    }

    /*
     =================================================================================

     idCompressor_LZW

     http://www.unisys.com/about__unisys/lzw
     http://www.dogma.net/markn/articles/lzw/lzw.htm
     http://www.cs.cf.ac.uk/Dave/Multimedia/node214.html
     http://www.cs.duke.edu/csed/curious/compression/lzw.html
     http://oldwww.rasip.fer.hr/research/compress/algorithms/fund/lz/lzw.html

     This is the same compression scheme used by GIF with the exception that
     the EOI and clear codes are not explicitly stored.  Instead EOI happens
     when the input stream runs dry and CC happens when the table gets to big.

     This is a derivation of LZ78, but the dictionary starts with all single
     character values so only code words are output.  It is similar in theory
     to LZ77, but instead of using the previous X bytes as a lookup table, a table
     is built as the stream is read.  The	compressor and decompressor use the
     same formula, so the tables should be exactly alike.  The only catch is the
     decompressor is always one step behind the compressor and may get a code not
     yet in the table.  In this case, it is easy to determine what the next code
     is going to be (it will be the previous string plus the first byte of the
     previous string).

     The dictionary can be any size, but 12 bits seems to produce best results for
     most sample data.  The code size is variable.  It starts with the minimum
     number of bits required to store the dictionary and automatically increases
     as the dictionary gets bigger (it starts at 9 bits and grows to 10 bits when
     item 512 is added, 11 bits when 1024 is added, etc...) once the the dictionary
     is filled (4096 items for a 12 bit dictionary), the whole thing is cleared and
     the process starts over again.

     The compressor increases the bit size after it adds the item, while the
     decompressor does before it adds the item.  The difference is subtle, but
     it's because decompressor being one step behind.  Otherwise, the decompressor
     would read 512 with only 9 bits.

     If "Hello" is in the dictionary, then "Hell", "Hel", "He" and "H" will be too.
     We use this to our advantage by storing the index of the previous code, and
     the value of the last character.  This means when we traverse through the
     dictionary, we get the characters in reverse.

     Dictionary entries 0-255 are always going to have the values 0-255

     =================================================================================
     */
    internal class idCompressor_LZW : idCompressor_BitStream() {
        //
        //
        // Dictionary data
        //
        // Block data
        protected var block: ByteArray = ByteArray(LZW_BLOCK_SIZE)
        protected var blockIndex = 0
        protected var blockSize = 0
        protected var codeBits = 0
        protected var dictionary: Array<LZWDictionary> = Array(LZW_DICT_SIZE) { LZWDictionary() }
        protected var index: idHashIndex = idHashIndex()

        //
        protected var nextCode = 0

        //
        // Used by the decompressor
        protected var oldCode = 0

        //
        // Used by the compressor
        protected var w = 0

        //
        //
        override fun Init(f: idFile, compress: Boolean, wordLength: Int) {
            super.Init(f, compress, wordLength)
            for (i in 0 until LZW_FIRST_CODE) {
                dictionary[i].k = i
                dictionary[i].w = -1
            }
            index.Clear()
            nextCode = LZW_FIRST_CODE
            codeBits = LZW_START_BITS
            blockSize = 0
            blockIndex = 0
            w = -1
            oldCode = -1
        }

        override fun FinishCompress() {
            WriteBits(w, codeBits)
            super.FinishCompress()
        }

        override fun Write(inData: ByteBuffer, inLength: Int): Int {
            var i: Int
            InitCompress(inData, inLength)
            i = 0
            while (i < inLength) {
                val k = ReadBits(8)
                val code = Lookup(w, k)
                w = if (code >= 0) {
                    code
                } else {
                    WriteBits(w, codeBits)
                    if (!BumpBits()) {
                        AddToDict(w, k)
                    }
                    k
                }
                i++
            }
            return inLength
        }

        override fun Read(outData: ByteBuffer, outLength: Int): Int {
            var i: Int
            var n: Int
            if (compress == true || outLength <= 0) {
                return 0
            }
            if (0 == blockSize) {
                DecompressBlock()
            }
            n = 0.also { i = it }
            while (i < outLength) {
                if (0 == blockSize) {
                    return i
                }
                n = blockSize - blockIndex
                if (outLength - i >= n) { //			memcpy( ((byte *)outData) + i, block + blockIndex, n );
                    System.arraycopy(block, blockIndex, outData.array(), i, n)
                    DecompressBlock()
                    blockIndex = 0
                } else { //			memcpy( ((byte *)outData) + i, block + blockIndex, outLength - i );
                    System.arraycopy(block, blockIndex, outData.array(), i, outLength - i)
                    n = outLength - i
                    blockIndex += n
                }
                i += n
            }
            return outLength
        }

        protected fun AddToDict(w: Int, k: Int): Int {
            dictionary[nextCode].k = k
            dictionary[nextCode].w = w
            index.Add(w xor k, nextCode)
            return nextCode++
        }

        protected fun Lookup(w: Int, k: Int): Int {
            var j: Int
            if (w == -1) {
                return k
            } else {
                j = index.First(w xor k)
                while (j >= 0) {
                    if (dictionary[j].k == k && dictionary[j].w == w) {
                        return j
                    }
                    j = index.Next(j)
                }
            }
            return -1
        }

        /*
         ================
         idCompressor_LZW::BumpBits

         Possibly increments codeBits
         Returns true if the dictionary was cleared
         ================
         */
        protected fun BumpBits(): Boolean {
            if (nextCode == 1 shl codeBits) {
                codeBits++
                if (codeBits > LZW_DICT_BITS) {
                    nextCode = LZW_FIRST_CODE
                    codeBits = LZW_START_BITS
                    index.Clear()
                    return true
                }
            }
            return false
        }

        /*
         ================
         idCompressor_LZW::WriteCain
         The chain is stored backwards, so we have to write it to a buffer then output the buffer in reverse
         ================
         */
        protected fun WriteChain(code: Int): Int {
            var code = code
            val chain = ByteArray(LZW_DICT_SIZE)
            var firstChar = 0
            var i = 0
            do {
                assert(i < LZW_DICT_SIZE - 1 && code >= 0)
                chain[i++] = dictionary[code].k.toByte()
                code = dictionary[code].w
            } while (code >= 0) // FIX: chain[] is a signed ByteArray in Kotlin; must mask with 0xFF to treat as unsigned (as C++ does).
            // C++ `byte` is unsigned char, so chain[i] is always 0-255 there.
            firstChar = chain[--i].toInt() and 0xFF
            while (i >= 0) {
                WriteBits(chain[i].toInt() and 0xFF, 8)
                i--
            }
            return firstChar
        }

        protected fun DecompressBlock() {
            var code: Int
            var firstChar: Int
            InitDecompress(block, LZW_BLOCK_SIZE)
            while (writeByte < writeLength - LZW_DICT_SIZE && readLength > 0) {
                assert(codeBits <= LZW_DICT_BITS)
                code = ReadBits(codeBits)
                if (readLength == 0) {
                    break
                }
                if (oldCode == -1) {
                    assert(code < 256)
                    WriteBits(code, 8)
                    oldCode = code
                    firstChar = code
                    continue
                }
                if (code >= nextCode) {
                    assert(code == nextCode)
                    firstChar = WriteChain(oldCode)
                    WriteBits(firstChar, 8)
                } else {
                    firstChar = WriteChain(code)
                }
                AddToDict(oldCode, firstChar)
                oldCode = if (BumpBits()) {
                    -1
                } else {
                    code
                }
            }
            blockSize = Min(writeByte, LZW_BLOCK_SIZE)
        }

        protected class LZWDictionary {
            var k = 0
            var w = 0
        }

        companion object {
            protected const val LZW_BLOCK_SIZE = 32767
            protected const val LZW_DICT_BITS = 12
            protected const val LZW_DICT_SIZE = 1 shl LZW_DICT_BITS
            protected const val LZW_START_BITS = 9
            protected const val LZW_FIRST_CODE = 1 shl LZW_START_BITS - 1
        }
    }

    // FIX: C++ uses huffmanNode_t** (pointer-to-pointer) for the head field.
    // Multiple nodes can share the same slot, enabling shared mutation.
    // This wrapper class simulates that indirection in Kotlin.
    internal class NodeSlot {
        var node: huffmanNode_t? = null
        var nextFree: NodeSlot? = null  // used for freelist chaining
    }

    internal open class nodetype {
        var head: NodeSlot? = null       // highest ranked node in block (C++: huffmanNode_t **)
        var left: nodetype? = null
        var right: nodetype? = null
        var parent: nodetype? = null     // tree structure
        var next: nodetype? = null
        var prev: nodetype? = null       // doubly-linked list
        var symbol = 0
        var weight = 0

        fun reset() {
            head = null
            left = null
            right = null
            parent = null
            next = null
            prev = null
            symbol = 0
            weight = 0
        }
    }

    internal class huffmanNode_t : nodetype()
}
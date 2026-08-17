package neo.idlib.hashing

import java.nio.ByteBuffer

/*
 ===============================================================================

 Calculates a checksum for a block of data using the MD4 message-digest
 algorithm. Matches dhewm3's MD4_BlockChecksum: digest[0] ^ digest[1] ^
 digest[2] ^ digest[3], interpreted as unsigned little-endian 32-bit data.

 ===============================================================================
 */
fun MD4_BlockChecksum(data: ByteBuffer, length: Int): Long {
    val currentPosition = data.position()
    val md4 = MD4()
    md4.update(data, length)
    data.position(currentPosition)
    return md4.blockChecksum()
}

fun MD4_BlockChecksum(data: IntArray, length: Int): Long {
    val md4 = MD4()
    md4.update(data, length)
    return md4.blockChecksum()
}

fun MD4_BlockChecksum(data: ByteArray, length: Int): Long {
    val md4 = MD4()
    md4.update(data, 0, length)
    return md4.blockChecksum()
}

private class MD4 {
    private val state = IntArray(4)
    private val buffer = ByteArray(64)
    private val x = IntArray(16)
    private var count = 0L

    init {
        state[0] = 0x67452301
        state[1] = 0xefcdab89.toInt()
        state[2] = 0x98badcfe.toInt()
        state[3] = 0x10325476
    }

    fun update(input: ByteArray, offset: Int, length: Int) {
        require(length >= 0)
        require(offset >= 0 && offset + length <= input.size)

        var inputOffset = offset
        var inputLen = length
        var bufferIndex = ((count ushr 3) and 0x3f).toInt()
        count += inputLen.toLong() shl 3

        if (bufferIndex != 0) {
            val partLen = minOf(64 - bufferIndex, inputLen)
            System.arraycopy(input, inputOffset, buffer, bufferIndex, partLen)
            bufferIndex += partLen
            inputOffset += partLen
            inputLen -= partLen
            if (bufferIndex == 64) {
                transform(buffer, 0)
            }
        }

        while (inputLen >= 64) {
            transform(input, inputOffset)
            inputOffset += 64
            inputLen -= 64
        }

        if (inputLen > 0) {
            System.arraycopy(input, inputOffset, buffer, 0, inputLen)
        }
    }

    fun update(input: ByteBuffer, length: Int) {
        require(length >= 0 && length <= input.remaining())
        if (input.hasArray()) {
            update(input.array(), input.arrayOffset() + input.position(), length)
            return
        }

        val temp = ByteArray(64)
        var remaining = length
        var offset = input.position()
        while (remaining > 0) {
            val count = minOf(temp.size, remaining)
            for (i in 0 until count) {
                temp[i] = input.get(offset + i)
            }
            update(temp, 0, count)
            offset += count
            remaining -= count
        }
    }

    fun update(input: IntArray, length: Int) {
        require(length >= 0 && length <= input.size * Integer.BYTES)

        val temp = ByteArray(64)
        var remaining = length
        var intIndex = 0
        var byteInInt = 0
        while (remaining > 0) {
            val count = minOf(temp.size, remaining)
            for (i in 0 until count) {
                temp[i] = (input[intIndex] ushr (byteInInt * 8)).toByte()
                byteInInt++
                if (byteInInt == 4) {
                    byteInInt = 0
                    intIndex++
                }
            }
            update(temp, 0, count)
            remaining -= count
        }
    }

    fun blockChecksum(): Long {
        val digest = finalDigest()
        val hash = littleEndianInt(digest, 0) xor littleEndianInt(digest, 4) xor littleEndianInt(
            digest, 8
        ) xor littleEndianInt(digest, 12)
        return hash.toLong() and 0xffffffffL
    }

    private fun finalDigest(): ByteArray {
        val bits = ByteArray(8)
        var bitCount = count
        for (i in 0 until 8) {
            bits[i] = bitCount.toByte()
            bitCount = bitCount ushr 8
        }

        val index = ((count ushr 3) and 0x3f).toInt()
        val padLen = if (index < 56) 56 - index else 120 - index
        update(PADDING, 0, padLen)
        update(bits, 0, bits.size)

        val digest = ByteArray(16)
        encodeState(digest)
        return digest
    }

    private fun transform(block: ByteArray, offset: Int) {
        for (i in 0 until 16) {
            x[i] = littleEndianInt(block, offset + i * 4)
        }

        var a = state[0]
        var b = state[1]
        var c = state[2]
        var d = state[3]

        a = ff(a, b, c, d, x[0], 3)
        d = ff(d, a, b, c, x[1], 7)
        c = ff(c, d, a, b, x[2], 11)
        b = ff(b, c, d, a, x[3], 19)
        a = ff(a, b, c, d, x[4], 3)
        d = ff(d, a, b, c, x[5], 7)
        c = ff(c, d, a, b, x[6], 11)
        b = ff(b, c, d, a, x[7], 19)
        a = ff(a, b, c, d, x[8], 3)
        d = ff(d, a, b, c, x[9], 7)
        c = ff(c, d, a, b, x[10], 11)
        b = ff(b, c, d, a, x[11], 19)
        a = ff(a, b, c, d, x[12], 3)
        d = ff(d, a, b, c, x[13], 7)
        c = ff(c, d, a, b, x[14], 11)
        b = ff(b, c, d, a, x[15], 19)

        a = gg(a, b, c, d, x[0], 3)
        d = gg(d, a, b, c, x[4], 5)
        c = gg(c, d, a, b, x[8], 9)
        b = gg(b, c, d, a, x[12], 13)
        a = gg(a, b, c, d, x[1], 3)
        d = gg(d, a, b, c, x[5], 5)
        c = gg(c, d, a, b, x[9], 9)
        b = gg(b, c, d, a, x[13], 13)
        a = gg(a, b, c, d, x[2], 3)
        d = gg(d, a, b, c, x[6], 5)
        c = gg(c, d, a, b, x[10], 9)
        b = gg(b, c, d, a, x[14], 13)
        a = gg(a, b, c, d, x[3], 3)
        d = gg(d, a, b, c, x[7], 5)
        c = gg(c, d, a, b, x[11], 9)
        b = gg(b, c, d, a, x[15], 13)

        a = hh(a, b, c, d, x[0], 3)
        d = hh(d, a, b, c, x[8], 9)
        c = hh(c, d, a, b, x[4], 11)
        b = hh(b, c, d, a, x[12], 15)
        a = hh(a, b, c, d, x[2], 3)
        d = hh(d, a, b, c, x[10], 9)
        c = hh(c, d, a, b, x[6], 11)
        b = hh(b, c, d, a, x[14], 15)
        a = hh(a, b, c, d, x[1], 3)
        d = hh(d, a, b, c, x[9], 9)
        c = hh(c, d, a, b, x[5], 11)
        b = hh(b, c, d, a, x[13], 15)
        a = hh(a, b, c, d, x[3], 3)
        d = hh(d, a, b, c, x[11], 9)
        c = hh(c, d, a, b, x[7], 11)
        b = hh(b, c, d, a, x[15], 15)

        state[0] += a
        state[1] += b
        state[2] += c
        state[3] += d
    }

    private fun encodeState(out: ByteArray) {
        for (i in 0 until 4) {
            val value = state[i]
            val offset = i * 4
            out[offset] = value.toByte()
            out[offset + 1] = (value ushr 8).toByte()
            out[offset + 2] = (value ushr 16).toByte()
            out[offset + 3] = (value ushr 24).toByte()
        }
    }

    private fun ff(a: Int, b: Int, c: Int, d: Int, x: Int, s: Int): Int {
        return Integer.rotateLeft(a + f(b, c, d) + x, s)
    }

    private fun gg(a: Int, b: Int, c: Int, d: Int, x: Int, s: Int): Int {
        return Integer.rotateLeft(a + g(b, c, d) + x + 0x5a827999, s)
    }

    private fun hh(a: Int, b: Int, c: Int, d: Int, x: Int, s: Int): Int {
        return Integer.rotateLeft(a + h(b, c, d) + x + 0x6ed9eba1, s)
    }

    private fun f(x: Int, y: Int, z: Int): Int {
        return (x and y) or (x.inv() and z)
    }

    private fun g(x: Int, y: Int, z: Int): Int {
        return (x and y) or (x and z) or (y and z)
    }

    private fun h(x: Int, y: Int, z: Int): Int {
        return x xor y xor z
    }

    companion object {
        private val PADDING = ByteArray(64).also { it[0] = 0x80.toByte() }
    }
}

private fun littleEndianInt(bytes: ByteArray, offset: Int): Int {
    return (bytes[offset].toInt() and 0xff) or ((bytes[offset + 1].toInt() and 0xff) shl 8) or ((bytes[offset + 2].toInt() and 0xff) shl 16) or ((bytes[offset + 3].toInt() and 0xff) shl 24)
}

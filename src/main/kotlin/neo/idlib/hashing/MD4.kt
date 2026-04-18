package neo.idlib.hashing

import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.security.Security

private const val MD4 = true

/*
 ===============================================================================

 Calculates a checksum for a block of data
 using the MD4 message-digest algorithm.

 ===============================================================================
 */
fun MD4_BlockChecksum(data: ByteBuffer, length: Int): Long {
    return BlockChecksum(data, length, MD4)
}

fun MD4_BlockChecksum(data: IntArray, length: Int): Long {
    val buffer = ByteBuffer.allocate(data.size * 4)
    buffer.order(ByteOrder.LITTLE_ENDIAN)
    buffer.asIntBuffer().put(data)
    return BlockChecksum(buffer, length, MD4)
}

fun MD4_BlockChecksum(data: ByteArray, length: Int): Long {
    return BlockChecksum(
        ByteBuffer.wrap(data),
        length,
        MD4
    )
}

fun BlockChecksum(data: ByteBuffer, length: Int, MD4: Boolean): Long {
    Security.addProvider(BouncyCastleProvider())
    val currentPosition = data.position()
    // Hash exactly 'length' bytes from the current position, matching C++ behavior
    val slice = data.slice().order(data.order())
    slice.limit(length)
    val messageDigest = if (MD4) MessageDigest.getInstance("MD4") else MessageDigest.getInstance("MD5")
    messageDigest.update(slice)
    data.position(currentPosition)
    val digest = ByteBuffer.wrap(messageDigest.digest())
    digest.order(ByteOrder.LITTLE_ENDIAN)
    val hash = digest.int xor digest.int xor digest.int xor digest.int

    return hash.toLong() and 0xFFFFFFFFL
}

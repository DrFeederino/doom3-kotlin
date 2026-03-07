package neo.idlib.hashing

import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.security.Security

/*
 ===============
 MD5_BlockChecksum
 ===============
 */
fun MD5_BlockChecksum(data: ByteArray, length: Int): String {
    Security.addProvider(BouncyCastleProvider())
    val buffer = ByteBuffer.wrap(data)
    val slice = buffer.slice()
    slice.limit(length)
    val messageDigest = MessageDigest.getInstance("MD5")
    messageDigest.update(slice)
    val digest = ByteBuffer.wrap(messageDigest.digest())
    digest.order(ByteOrder.LITTLE_ENDIAN)
    val hash = digest.int xor digest.int xor digest.int xor digest.int
    return Integer.toUnsignedString(hash)
}

fun MD5_BlockChecksum(data: String, length: Int): String {
    return MD5_BlockChecksum(data.toByteArray(), length)
}

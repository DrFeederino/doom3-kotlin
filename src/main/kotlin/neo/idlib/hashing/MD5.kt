package neo.idlib.hashing

import java.security.MessageDigest

/*
 ===============
 MD5_BlockChecksum
 ===============
 */
fun MD5_BlockChecksum(data: ByteArray, length: Int): String {
    val messageDigest = MessageDigest.getInstance("MD5")
    messageDigest.update(data, 0, length)
    val digest = messageDigest.digest()
    val hash =
        littleEndianInt(digest, 0) xor littleEndianInt(digest, 4) xor littleEndianInt(digest, 8) xor littleEndianInt(
            digest, 12
        )
    return Integer.toUnsignedString(hash)
}

fun MD5_BlockChecksum(data: String, length: Int): String {
    return MD5_BlockChecksum(data.toByteArray(), length)
}

private fun littleEndianInt(bytes: ByteArray, offset: Int): Int {
    return (bytes[offset].toInt() and 0xff) or ((bytes[offset + 1].toInt() and 0xff) shl 8) or ((bytes[offset + 2].toInt() and 0xff) shl 16) or ((bytes[offset + 3].toInt() and 0xff) shl 24)
}

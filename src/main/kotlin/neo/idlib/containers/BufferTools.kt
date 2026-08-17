package neo.idlib.containers

import org.lwjgl.BufferUtils
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.FloatBuffer
import java.util.*

/**
 * Buffer/array conversion helpers used at the FFI seams between the
 * Kotlin port and LWJGL/OpenGL/OpenAL native APIs that expect direct
 * `ByteBuffer`s or contiguous primitive arrays.
 */

/** Copy a `FloatBuffer`'s remaining elements into a fresh `FloatArray`, respecting slice offsets. */
fun fbtofa(fb: FloatBuffer): FloatArray {
    if (!fb.hasArray()) {
        val data = FloatArray(fb.remaining())
        for (i in data.indices) {
            data[i] = fb.get(fb.position() + i)
        }
        return data
    } // Must respect the FloatBuffer's arrayOffset from slice().
    // FloatBuffer.array() returns the FULL backing array ignoring offset,
    // so fb.array()[0] is NOT fb.get(0) for sliced buffers.
    val offset = fb.arrayOffset() + fb.position()
    val remaining = fb.remaining()
    val array = fb.array()
    if (offset == 0 && remaining == array.size) {
        return array
    }
    return array.copyOfRange(offset, offset + remaining)
}

/** Copy [arr] into a new big-endian `ByteBuffer`, or return null for an empty input. */
fun stobb(arr: ShortArray): ByteBuffer? {
    if (arr.isEmpty()) return null
    val buffer = ByteBuffer.allocate(arr.size * 2)
    buffer.asShortBuffer().put(arr)
    return buffer.flip()
}

/** Decode [buffer] as ISO-8859-1 starting from position 0. */
fun bbtocb(buffer: ByteBuffer): CharBuffer {
    return java.nio.charset.StandardCharsets.ISO_8859_1.decode(buffer.rewind())
}

fun bbtoa(buffer: ByteBuffer): String = bbtocb(buffer).toString()

/** Copy [bytes] into a freshly allocated direct `ByteBuffer` suitable for passing to native code. */
fun wrapToNativeBuffer(bytes: ByteArray?): ByteBuffer? {
    return if (null == bytes) null
    else BufferUtils.createByteBuffer(bytes.size).put(bytes).flip()
}

/** Pack [intArray] as 4-byte big-endian ints into a contiguous `ByteArray`. */
fun intArrayToBytes(intArray: IntArray): ByteArray {
    val buffer = ByteBuffer.allocate(intArray.size * 4)
    buffer.asIntBuffer().put(intArray)
    return buffer.array()
}

/** Flatten a 3-D byte array into a contiguous 1-D `ByteArray` (row-major). */
fun flatten(input: Array<Array<ByteArray>>): ByteArray {
    val height = input.size
    val width = input[0].size
    val length = input[0][0].size
    val output = ByteArray(height * width * length)
    for (a in 0 until height) {
        val x = a * width * length
        for (b in 0 until width) {
            val y = b * length
            System.arraycopy(input[a][b], 0, output, x + y, length)
        }
    }
    return output
}

/** C-style `memcmp` returning true when the first [size] elements of [ptr1] and [ptr2] are equal. */
fun memcmp(ptr1: IntArray, ptr2: IntArray, size: Int): Boolean = memcmp(ptr1, 0, ptr2, 0, size)

fun memcmp(ptr1: IntArray, p1Offset: Int, ptr2: IntArray, p2Offset: Int, size: Int): Boolean {
    for (i in 0 until size) {
        if (ptr1[p1Offset + i] != ptr2[p2Offset + i]) return false
    }
    return true
}

fun memcmp(a: ByteArray?, b: ByteArray?, length: Int): Boolean {
    return !(null == a || null == b || a.size < length || b.size < length) && Arrays.equals(
        Arrays.copyOf(a, length), Arrays.copyOf(b, length)
    )
}

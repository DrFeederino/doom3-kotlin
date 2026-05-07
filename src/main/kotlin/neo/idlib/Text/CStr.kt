package neo.idlib.Text

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

/**
 * C-style string helpers used to bridge the original C++ source which
 * mixes char arrays, byte arrays and `String` freely. Mostly applied at
 * file-IO and parser boundaries that mirror the dhewm3 byte layout.
 */

/** Index of the first NUL terminator in [str], or [str].size if none. */
fun strLen(str: CharArray): Int {
    if (str.isEmpty()) return 0
    var len = 0
    while (len < str.size) {
        if (str[len] == '\u0000') break
        len++
    }
    return len
}

/** Index of the first NUL byte in [str] starting at [offset], or [str].size if none. */
fun strLen(str: ByteArray, offset: Int = 0): Int {
    if (str.isEmpty()) return 0
    var len = offset
    while (len < str.size) {
        if (str[len].toInt() == 0) break
        len++
    }
    return len
}

fun strLen(str: String): Int = strLen(str.toCharArray())

/** Convert a C-style char array (terminated by NUL or end-of-array) into a `String`. */
fun ctos(ascii: CharArray): String {
    for (a in ascii.indices) {
        if ('\u0000' == ascii[a]) {
            return String(ascii).substring(0, a)
        }
    }
    return String(ascii)
}

fun ctos(ascii: Char): String = "" + ascii

/** Slice [bytes] starting at [offset] for [length] bytes into a String, or null if [bytes] is empty. */
fun btos(bytes: ByteArray, offset: Int, length: Int): String? {
    return if (bytes.isEmpty()) null else String(bytes, offset, length)
}

fun btos(bytes: ByteArray, offset: Int = 0): String? {
    val length = strLen(bytes, offset) - offset
    return btos(bytes, offset, length)
}

/** Lenient C-style atoi: trims whitespace and returns 0 on parse failure. */
fun atoi(ascii: String): Int {
    return try {
        ascii.trim { it <= ' ' }.toInt()
    } catch (e: NumberFormatException) {
        0
    }
}

fun atoi(ascii: Str.idStr): Int = atoi(ascii.toString())

fun atoi(ascii: CharArray): Int = atoi(ctos(ascii))

/** Lenient C-style atof: accepts comma decimals, returns 0 on parse failure. */
fun atof(ascii: String): Float {
    if (ascii.isBlank()) return 0f
    val value = if (ascii.indexOf(',') >= 0) {
        ascii.trim { it <= ' ' }.replace(",", ".")
    } else {
        ascii.trim { it <= ' ' }
    }
    return try {
        value.toFloat()
    } catch (nfe: NumberFormatException) {
        0f
    }
}

fun atof(ascii: Str.idStr?): Float = if (ascii == null) 0f else atof(ascii.toString())

/** Encode an ASCII/UTF-8 String into a freshly allocated ByteBuffer, or null if empty. */
fun atobb(ascii: String?): ByteBuffer? {
    return if (ascii == null || ascii.isEmpty() || ascii == "\u0000") null
    else StandardCharsets.UTF_8.encode(ascii)
}

fun atobb(ascii: Str.idStr): ByteBuffer? {
    return if (ascii.data.isEmpty() || ascii.data == "\u0000") null else atobb(ascii.toString())
}

fun atobb(ascii: CharArray): ByteBuffer? {
    return if (ascii.isEmpty() || ascii[0] == '\u0000') null else atobb(ctos(ascii))
}

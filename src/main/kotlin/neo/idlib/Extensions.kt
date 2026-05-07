package neo.idlib

/**
 * Convenience conversions between primitive types that mirror common
 * idioms from the original C++ Doom 3 source where booleans, bytes and
 * integers are freely interchanged in script/network/serialization paths.
 */

/** Boolean → C-style int (true → 1, false → 0). */
fun Boolean.toInt(): Int = if (this) 1 else 0

/** Int → C-style bool (0 → false, anything else → true). */
fun Int.toBoolean(): Boolean = this != 0

/** Byte → unsigned int (sign-extension stripped). */
fun Byte.toUnsignedInt(): Int = this.toInt() and 0xFF

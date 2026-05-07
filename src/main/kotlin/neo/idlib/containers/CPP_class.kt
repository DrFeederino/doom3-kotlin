package neo.idlib.containers

/**
 * Sizes (in bits) of fundamental C++ types, used to compute on-disk struct
 * sizes for direct file/byte-buffer reads and writes that mirror the original
 * 32-bit C++ memory layout.
 */
object CPP_class {
    /** A 32-bit C++ pointer. */
    const val POINTER_SIZE = Integer.SIZE

    /** A C++ bool (8 bits). */
    const val BOOL_SIZE = java.lang.Byte.SIZE

    /** A C++ char (8 bits). */
    const val CHAR_SIZE = java.lang.Byte.SIZE

    /** A C++ enum is the size of an int. */
    const val ENUM_SIZE = Integer.SIZE

    /** A C++ long is 4 bytes on the platforms targeted by the original game. */
    const val LONG_SIZE = Integer.SIZE
}

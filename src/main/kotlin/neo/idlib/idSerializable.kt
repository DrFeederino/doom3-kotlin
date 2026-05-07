package neo.idlib

import neo.framework.File_h.idFile

interface idSerializable {
    /**
     * Reads the object's state directly from the given file, advancing the
     * file's read position. Implementations should consume exactly the same
     * number of bytes that [writeTo] produced.
     */
    fun readFrom(file: idFile)

    /**
     * Writes the object's state directly to the given file, advancing the
     * file's write position. Must be symmetric with [readFrom].
     */
    fun writeTo(file: idFile)
}
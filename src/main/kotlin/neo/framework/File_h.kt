package neo.framework

import neo.TempDump
import neo.TempDump.SERiAL
import neo.framework.FileSystem_h.fsMode_t
import neo.idlib.*
import neo.idlib.BitMsg.idBitMsg
import neo.idlib.Text.Str.idStr
import neo.idlib.containers.CBool
import neo.idlib.containers.CFloat
import neo.idlib.containers.CInt
import neo.idlib.containers.CLong
import neo.idlib.math.Matrix.idMat3
import neo.idlib.math.idVec2
import neo.idlib.math.idVec3
import neo.idlib.math.idVec4
import neo.idlib.math.idVec6
import neo.sys.win_main
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.logging.Level
import java.util.logging.Logger
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

object File_h {
    const val MAX_PRINT_MSG = 4096

    /*
     =================
     FS_WriteFloatString
     =================
     */
    fun FS_WriteFloatString(buf: CharArray, fmtString: String?, vararg argPtr: Any): Int {
        var i: Long
        var u: Long
        var f: Float
        var str: String?
        var index: Int
        var tmp: idStr
        var format: String
        var fmt_ptr = 0
        var va_ptr = 0
        var temp: String
        if (fmtString == null) return 0
        val fmt: CharArray = fmtString.toCharArray()
        index = 0
        // FIX: was `while (fmtString != null)` which is always true (infinite loop).
        // C++ iterates `while (*fmt)` — checking character-by-character until null terminator.
        while (fmt_ptr < fmt.size) {
            when (fmt[fmt_ptr]) {
                '%' -> {
                    format = ""
                    format += fmt[fmt_ptr++]
                    while (fmt[fmt_ptr] >= '0' && fmt[fmt_ptr] <= '9' || fmt[fmt_ptr] == '.' || fmt[fmt_ptr] == '-' || fmt[fmt_ptr] == '+' || fmt[fmt_ptr] == '#') {
                        format += fmt[fmt_ptr++]
                    }
                    format += fmt[fmt_ptr]
                    when (fmt[fmt_ptr]) {
                        'f', 'e', 'E', 'g', 'G' -> {
                            f = argPtr[va_ptr++] as Float
                            if (format.length <= 2) {
                                // high precision floating point number without trailing zeros
//                                sprintf(tmp, "%1.10f", f);
                                tmp = idStr(String.format("%1.10f", f))
                                tmp.StripTrailing('0')
                                tmp.StripTrailing('.')
                                temp = String.format("%s", tmp)
                                System.arraycopy(temp.toCharArray(), 0, buf, index, temp.length)
                                index += temp.length
                                //                                index += sprintf(buf + index, "%s", tmp.c_str());
                            } else {
//                                index += sprintf(buf + index, format, f);
                                temp = String.format(format, f)
                                System.arraycopy(temp.toCharArray(), 0, buf, index, temp.length)
                                index += temp.length
                            }
                        }

                        'd', 'i' -> {
                            i = (argPtr[va_ptr++] as Number).toLong()
                            //                            index += sprintf(buf + index, format, i);
                            temp = String.format(format, i)
                            System.arraycopy(temp.toCharArray(), 0, buf, index, temp.length)
                            index += temp.length
                        }

                        'u' -> {
                            u = (argPtr[va_ptr++] as Number).toLong()
                            //                            index += sprintf(buf + index, format, u);
                            temp = String.format(format, u)
                            System.arraycopy(temp.toCharArray(), 0, buf, index, temp.length)
                            index += temp.length
                        }

                        'o' -> {
                            u = (argPtr[va_ptr++] as Number).toLong()
                            //                            index += sprintf(buf + index, format, u);
                            temp = String.format(format, u)
                            System.arraycopy(temp.toCharArray(), 0, buf, index, temp.length)
                            index += temp.length
                        }

                        'x' -> {
                            u = (argPtr[va_ptr++] as Number).toLong()
                            //                            index += sprintf(buf + index, format, u);
                            temp = String.format(format, u)
                            System.arraycopy(temp.toCharArray(), 0, buf, index, temp.length)
                            index += temp.length
                        }

                        'X' -> {
                            u = (argPtr[va_ptr++] as Number).toLong()
                            //                            index += sprintf(buf + index, format, u);
                            temp = String.format(format, u)
                            System.arraycopy(temp.toCharArray(), 0, buf, index, temp.length)
                            index += temp.length
                        }

                        'c' -> {
                            i = (argPtr[va_ptr++] as Number).toLong()
                            //                            index += sprintf(buf + index, format, (char) i);
                            temp = String.format(format, i)
                            System.arraycopy(temp.toCharArray(), 0, buf, index, temp.length)
                            index += temp.length
                        }

                        's' -> {
                            str = argPtr[va_ptr++] as String?
                            //                            index += sprintf(buf + index, format, str);
                            temp = String.format(format, str)
                            System.arraycopy(temp.toCharArray(), 0, buf, index, temp.length)
                            index += temp.length
                        }

                        '%' -> {
                            //                            index += sprintf(buf + index, format);
                            temp = String.format(format)
                            System.arraycopy(temp.toCharArray(), 0, buf, index, temp.length)
                            index += temp.length
                        }

                        else -> idLib.common.Error("FS_WriteFloatString: invalid format %s", format)
                    }
                    fmt_ptr++
                }

                '\\' -> {
                    fmt_ptr++
                    when (fmt[fmt_ptr]) {
                        't' -> //                            index += sprintf(buf + index, "\t");
                            buf[index++] = '\t'

                        'v' -> //                            index += sprintf(buf + index, "\v");
                            buf[index++] = '\u000b' //vertical tab
                        'n' -> //                            index += sprintf(buf + index, "\n");
                            buf[index++] = '\n'

                        '\\' -> //                            index += sprintf(buf + index, "\\");
                            buf[index++] = '\\'

                        else -> idLib.common.Error("FS_WriteFloatString: unknown escape character '%c'", fmt[fmt_ptr])
                    }
                    fmt_ptr++
                }

                else -> {
                    //                    index += sprintf(buf + index, "%c", fmt[fmt_ptr]);
                    buf[index++] = fmt[fmt_ptr]
                    fmt_ptr++
                }
            }
        }
        return index
    }

    /*
     ==============================================================

     File Streams.

     ==============================================================
     */
    // mode parm for Seek
    enum class fsOrigin_t {
        FS_SEEK_CUR, FS_SEEK_END, FS_SEEK_SET
    }

    /*
     =================================================================================

     idFile

     =================================================================================
     */
    abstract class idFile {
        //TODO:implement closable?
        //	abstract					~idFile( ) {};
        // Get the name of the file.
        open fun GetName(): String {
            return ""
        }

        // Get the full file path.
        open fun GetFullPath(): String {
            return ""
        }

        // Read data from the file to the buffer.
        open fun Read(buffer: ByteBuffer): Int {
            return Read(buffer, buffer.capacity())
        }

        fun Read(classObject: SERiAL): Int {
            val buffer = classObject.AllocBuffer()
            val reads = Read(buffer, buffer.capacity())
            classObject.Read(buffer)
            return reads
        }

        fun Read(`object`: SERiAL, len: Int): Int {
            val buffer = `object`.AllocBuffer()
            val reads = Read(buffer, len)
            buffer.position(len).rewind()
            `object`.Read(buffer)
            return reads
        }

        @Deprecated("") // Read data from the file to the buffer.
        open fun Read(buffer: ByteBuffer, len: Int): Int {
            idLib.common.FatalError("idFile::Read: cannot read from idFile")
            return 0
        }

        open fun Read(buffer: UByteArray, len: Int): Int {
            idLib.common.FatalError("idFile::Read: cannot read from idFile")
            return 0
        }

        @Deprecated("") // Write all data from the buffer to the file.
        open fun Write(buffer: ByteBuffer /*, int len*/): Int {
            idLib.common.FatalError("idFile::Write: cannot write to idFile")
            return 0
        }

        @Deprecated("")
        fun Write(`object`: SERiAL): Int {
            return Write(`object`.Write())
        }

        @Deprecated("") // Write some data from the buffer to the file.
        open fun Write(buffer: ByteBuffer, len: Int): Int {
            idLib.common.FatalError("idFile::Write: cannot write to idFile")
            return 0
        }

        // Returns the length of the file.
        open fun Length(): Int {
            return 0
        }

        // Return a time value for reload operations.
        open fun Timestamp(): Long {
            return 0
        }

        // Returns offset in file.
        open fun Tell(): Int {
            return 0
        }

        // Forces flush on files being writting to.
        open fun ForceFlush() {}

        // Causes any buffered data to be written to the file.
        open fun Flush() {}

        // Seek on a file.
        @Throws(idException::class)
        open fun Seek(offset: Long, origin: fsOrigin_t): Boolean {
            return false //-1;
        }

        // Go back to the beginning of the file.
        fun Rewind() {
            Seek(0, fsOrigin_t.FS_SEEK_SET)
        }

        // Like fprintf.
        fun Printf(fmt: String, vararg args: Any): Int /* id_attribute((format(printf,2,3)))*/ {
            val buf = arrayOf("") // new char[MAX_PRINT_MSG];
            val length: Int
            //            va_list argptr;

//            va_start(argptr, fmt);
            length = idStr.vsnPrintf(buf, MAX_PRINT_MSG - 1, fmt, *args /*, argptr*/)
            //            va_end(argptr);

            // so notepad formats the lines correctly
            val work = idStr(buf[0])
            work.Replace("\n", "\r\n")
            return Write(TempDump.atobb(work)!!)
        }

        // Like fprintf but with argument pointer
        fun VPrintf(fmt: String, vararg args: Any /*, va_list arg*/): Int {
            val buf = arrayOf("") //new char[MAX_PRINT_MSG];
            val length: Int
            length = idStr.vsnPrintf(buf, MAX_PRINT_MSG - 1, fmt, *args /*, args*/)
            return Write(TempDump.atobb(buf[0])!!)
        }

        // Write a string with high precision doubleing point numbers to the file.
        fun WriteFloatString(fmt: String, vararg args: Any): Int /* id_attribute((format(printf,2,3)))*/ {
            val buf = CharArray(MAX_PRINT_MSG)
            val len: Int

            len = FS_WriteFloatString(buf, fmt, *args)
            return Write(TempDump.atobb(buf)!!, len)
        }

        // Endian portable alternatives to Read(...)
        fun ReadInt(value: CInt): Int {
            val intBytes = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
            val result = Read(intBytes)
            value.integerValue = (LittleLong(intBytes.getInt()))
            return result
        }

        // Endian portable alternatives to Read(...)
        fun ReadInt(value: CLong): Int {
            val intBytes = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
            val result = Read(intBytes)
            value._val = (LittleLong(intBytes.getInt()).toLong())
            return result
        }

        fun ReadInt(): Int {
            val value = CInt()
            this.ReadInt(value)
            return value.integerValue
        }

        // Endian portable alternatives to Write(...)
        // FIX: added LITTLE_ENDIAN to match ReadInt, and flip() to reset position after putInt
        // (putInt advances position to 4, so Write would write 0 bytes without flip)
        fun WriteInt(value: Int): Int {
            val intBytes = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
            val v: Int = LittleLong(value)
            intBytes.putInt(v)
            intBytes.flip()
            return Write(intBytes)
        }

        fun WriteInt(value: Enum<*>): Int {
            return WriteInt(value.ordinal)
        }

        // FIX: was allocating 2 bytes instead of 4 (sizeof(unsigned int)) — would throw BufferOverflowException
        // FIX: added LITTLE_ENDIAN and flip() (same ByteBuffer position bug as WriteInt)
        fun WriteUnsignedInt(value: Long): Int {
            val uintBytes = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
            val v: Int = LittleLong(value.toInt())
            uintBytes.putInt(v)
            uintBytes.flip()
            return Write(uintBytes)
        }

        // FIX: was allocating 1 byte instead of 2 (sizeof(short)), and reading a single byte
        fun ReadShort(value: ShortArray): Int {
            val shortBytes = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN)
            val result = Read(shortBytes)
            value[0] = LittleShort(shortBytes.short)
            return result
        }

        fun ReadShort(): Short {
            val value = shortArrayOf(0)
            this.ReadShort(value)
            return value[0]
        }

        // FIX: added LITTLE_ENDIAN to match ReadShort, and flip() to reset position
        fun WriteShort(value: Short): Int {
            val shortBytes = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN)
            val v: Short = LittleShort(value)
            shortBytes.putShort(v)
            shortBytes.flip()
            return Write(shortBytes)
        }

        fun ReadUnsignedShort(value: IntArray): Int {
            val ushortBytes = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN)
            val result = Read(ushortBytes)
            value[0] = LittleShort(ushortBytes.short).toInt() and 0xFFFF
            return result
        }

        fun ReadUnsignedShort(): Int {
            val value = intArrayOf(0)
            ReadUnsignedShort(value)
            return value[0]
        }

        // FIX: added LITTLE_ENDIAN and flip() to reset position
        fun WriteUnsignedShort(value: Int): Int {
            val ushortBytes = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN)
            val v: Short = LittleShort(value.toShort())
            ushortBytes.putShort(v)
            ushortBytes.flip()
            return Write(ushortBytes)
        }

        // FIX: was applying LittleShort to a single byte — C++ ReadChar does no endian conversion
        fun ReadChar(value: ShortArray): Int {
            val charBytes = ByteBuffer.allocate(1)
            val result = Read(charBytes)
            value[0] = charBytes[0].toShort()
            return result
        }

        fun ReadChar(): Short {
            val value = shortArrayOf(0)
            this.ReadChar(value)
            return value[0]
        }

        // FIX: added flip() to reset position after put
        fun WriteChar(value: Short): Int {
            val charBytes = ByteBuffer.allocate(1)
            charBytes.put(value.toByte())
            charBytes.flip()
            return Write(charBytes)
        }

        fun WriteChar(value: Char): Int {
            return WriteChar(value.code.toShort())
        }

        fun ReadUnsignedChar(value: CharArray): Int {
            val ucharBytes = ByteBuffer.allocate(1)
            val result = Read(ucharBytes)
            value[0] = (ucharBytes[0].toInt() and 0xFF).toChar()
            return result
        }

        // FIX: added flip() to reset position after put
        fun WriteUnsignedChar(value: Char): Int {
            val ucharBytes = ByteBuffer.allocate(1)
            ucharBytes.put(value.code.toByte())
            ucharBytes.flip()
            return Write(ucharBytes)
        }

        // FIX: added LITTLE_ENDIAN byte order to match C++ native byte interpretation
        fun ReadFloat(value: CFloat): Int {
            val floatBytes = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
            val result = Read(floatBytes)
            value._val = (LittleFloat(floatBytes.getFloat()))
            return result
        }

        fun ReadFloat(): Float {
            val value = CFloat()
            ReadFloat(value)
            return value._val
        }

        // FIX: added LITTLE_ENDIAN to match ReadFloat, and flip() to reset position
        fun WriteFloat(value: Float): Int {
            val floatBytes = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
            val v: Float = LittleFloat(value)
            floatBytes.putFloat(v)
            floatBytes.flip()
            return Write(floatBytes)
        }

        fun ReadBool(value: CBool): Int {
            val c = CharArray(1)
            val result = ReadUnsignedChar(c)
            value._val = (c[0] != '\u0000')
            return result
        }

        fun ReadBool(): Boolean {
            val value = CBool(false)
            ReadBool(value)
            return value._val
        }

        // FIX: was writing 'c' (0x63) for true — C++ casts bool to unsigned char (1 for true, 0 for false)
        fun WriteBool(value: Boolean): Int {
            val c: Char = if (value) '\u0001' else '\u0000'
            return WriteUnsignedChar(c)
        }

        fun ReadString(string: idStr): Int {
            val len = CInt()
            var result = 0
            ReadInt(len)
            if (len.integerValue > 0) {
                assert(len.integerValue <= 1000000)
                string.Fill(' ', len.integerValue)
                result = Read(string, len.integerValue)
                string.set(string)
            }
            return result
        }

        fun WriteString(value: String): Int {
            return WriteString(idStr(value))
        }

        fun WriteString(value: idStr): Int {
            val len: Int
            len = value.data.length
            WriteInt(len)
            val buffer = ByteBuffer.allocate(len).order(ByteOrder.LITTLE_ENDIAN)
            buffer.put(value.data.toByteArray())
            buffer.flip()
            return Write(buffer, len)
        }

        open fun Write(objectToWrite: SERiAL, len: Int): Int {
            val buffer = objectToWrite.AllocBuffer()
            val reads = Write(buffer, len)
            buffer.position(len).rewind()
            objectToWrite.Write()
            return reads
        }

        fun ReadVec2(vec: idVec2): Int {
            val buffer = ByteBuffer.allocate(idVec2.BYTES)
            val result = Read(buffer)
            vec.set(idVec2(buffer.float, buffer.float))
            return result
        }

        fun WriteVec2(vec: idVec2): Int {
            val buffer = ByteBuffer.allocate(idVec2.BYTES)
            buffer.asFloatBuffer().put(vec.ToFloatPtr()).flip()
            return Write(buffer)
        }

        fun ReadVec3(vec: idVec3): Int {
            val buffer = ByteBuffer.allocate(idVec3.BYTES)
            val result = Read(buffer)
            vec.set(idVec3(buffer.float, buffer.float, buffer.float))
            return result
        }

        fun WriteVec3(vec: idVec3): Int {
            val buffer = ByteBuffer.allocate(idVec3.BYTES)
            buffer.asFloatBuffer().put(vec.ToFloatPtr()).flip()
            return Write(buffer)
        }

        fun ReadVec4(vec: idVec4): Int {
            val buffer = ByteBuffer.allocate(idVec4.BYTES)
            val result = Read(buffer)
            vec.set(idVec4(buffer.getFloat(0), buffer.getFloat(4), buffer.getFloat(8), buffer.getFloat(12)))
            return result
        }

        fun WriteVec4(vec: idVec4): Int {
            val buffer = ByteBuffer.allocate(idVec4.BYTES)
            buffer.asFloatBuffer().put(vec.ToFloatPtr()).flip()
            return Write(buffer)
        }

        fun ReadVec6(vec: idVec6): Int {
            val buffer = ByteBuffer.allocate(idVec6.BYTES)
            val result = Read(buffer)
            vec.set(
                idVec6(
                    buffer.getFloat(0),
                    buffer.getFloat(4),
                    buffer.getFloat(8),
                    buffer.getFloat(12),
                    buffer.getFloat(16),
                    buffer.getFloat(20)
                )
            )
            return result
        }

        fun WriteVec6(vec: idVec6): Int {
            val buffer = ByteBuffer.allocate(idVec6.BYTES)
            buffer.asFloatBuffer().put(vec.ToFloatPtr()).flip()
            return Write(buffer)
        }

        fun ReadMat3(mat: idMat3): Int {
            val buffer = ByteBuffer.allocate(idMat3.BYTES)
            val result = Read(buffer)
            mat.set(
                idMat3(
                    buffer.getFloat(0),
                    buffer.getFloat(4),
                    buffer.getFloat(8),
                    buffer.getFloat(12),
                    buffer.getFloat(16),
                    buffer.getFloat(20),
                    buffer.getFloat(24),
                    buffer.getFloat(28),
                    buffer.getFloat(32)
                )
            )
            return result
        }

        fun WriteMat3(mat: idMat3): Int {
            val buffer = ByteBuffer.allocate(idMat3.BYTES)
            buffer.asFloatBuffer().put(mat[0].ToFloatPtr()).put(mat[1].ToFloatPtr()).put(mat[2].ToFloatPtr()).flip()
            return Write(buffer)
        }
    }

    /*
     =================================================================================

     idFile_Memory

     =================================================================================
     */
    class idFile_Memory : idFile {
        // friend class			idFileSystemLocal;
        private var allocated // allocated size
                : Int
        private var curPtr // current read/write pointer
                : Int
        private var filePtr // buffer holding the file data
                : ByteBuffer?
        private var fileSize // size of the file
                : Int
        private var granularity // file granularity
                : Int
        private var maxSize // maximum size of file
                : Int
        private var mode // open mode
                : Int
        private val name // name of the file
                : idStr = idStr()

        //
        //
        constructor() {    // file for writing without name
            name.set("*unknown*")
            maxSize = 0
            fileSize = 0
            allocated = 0
            granularity = 16384
            mode = 1 shl fsMode_t.FS_WRITE.ordinal
            filePtr = null
            curPtr = 0
        }

        constructor(name: String) {    // file for writing
            this.name.set(name)
            maxSize = 0
            fileSize = 0
            allocated = 0
            granularity = 16384
            mode = 1 shl fsMode_t.FS_WRITE.ordinal
            filePtr = null
            curPtr = 0
        }

        constructor(name: String, data: ByteBuffer, length: Int) {    // file for writing
            this.name.set(name)
            maxSize = length
            fileSize = 0
            allocated = length
            granularity = 16384
            mode = 1 shl fsMode_t.FS_WRITE.ordinal
            filePtr = data
            curPtr = 0
        }

        //public							idFile_Memory( const char *name, const char *data, int length );	// file for reading
        //public						~idFile_Memory( void );
        //
        override fun GetName(): String {
            return name.toString()
        }

        override fun GetFullPath(): String {
            return name.toString()
        }

        override fun Read(buffer: ByteBuffer): Int {
            return Read(buffer, buffer.capacity())
        }

        override fun Read(buffer: ByteBuffer, len: Int): Int {
            var len = len

            if (0 == mode and (1 shl TempDump.etoi(fsMode_t.FS_READ))) {
                idLib.common.FatalError("idFile_Memory::Read: %s not opened in read mode", name)
                return 0
            }
            if (curPtr + len > fileSize) {
                len = fileSize - curPtr
            }
            // FIX: was `filePtr!!.get(buffer.array(), curPtr, len)` which used curPtr as the
            // destination offset in buffer (wrong). C++ does `memcpy(buffer, curPtr, len)`.
            System.arraycopy(filePtr!!.array(), curPtr, buffer.array(), 0, len)
            curPtr += len
            return len
        }

        override fun Write(buffer: ByteBuffer /*, int len*/): Int {
            val len = buffer.capacity()
            if (0 == mode and (1 shl TempDump.etoi(fsMode_t.FS_WRITE))) {
                idLib.common.FatalError("idFile_Memory::Write: %s not opened in write mode", name)
                return 0
            }
            val alloc = curPtr + len + 1 - allocated // need room for len+1
            if (alloc > 0) {
                if (maxSize != 0) {
                    idLib.common.Error("idFile_Memory::Write: exceeded maximum size %d", maxSize)
                    return 0
                }
                val extra = granularity * (1 + alloc / granularity)
                val newPtr = ByteBuffer.allocate(allocated + extra) // Heap.Mem_Alloc(allocated + extra);
                if (allocated != 0) {
//                    memcpy(newPtr, filePtr, allocated);
                    //copy old data to new array
                    newPtr.put(filePtr)
                }
                allocated += extra
                //                curPtr = newPtr + (curPtr - filePtr);
//                if (filePtr != null) {
//                    Mem_Free(filePtr);
//                    filePtr = null;
//                }
                //copy new (resized) array to old one
                filePtr = newPtr
            }
            //            memcpy(curPtr, buffer, len);
            filePtr!!.position(curPtr)
            filePtr!!.put(buffer)
            curPtr += len
            fileSize += len
            filePtr!!.put(fileSize, 0.toByte()) // len + 1
            return len
        }

        override fun Length(): Int {
            return fileSize
        }

        override fun Timestamp(): Long {
            return 0
        }

        override fun Tell(): Int {
            return curPtr
        }

        override fun ForceFlush() {}
        override fun Flush() {}

        /*
         =================
         idFile_Memory::Seek

         returns zero(true) on success and -1(false) on failure
         =================
         */
        override fun Seek(offset: Long, origin: fsOrigin_t): Boolean {
            when (origin) {
                fsOrigin_t.FS_SEEK_CUR -> {
                    curPtr += offset.toInt()
                }

                fsOrigin_t.FS_SEEK_END -> {
                    curPtr = (fileSize - offset).toInt()
                }

                fsOrigin_t.FS_SEEK_SET -> {
                    curPtr = offset.toInt()
                }

                else -> {
                    idLib.common.FatalError("idFile_Memory::Seek: bad origin for %s\n", name)
                    return false //-1;
                }
            }
            if (curPtr <  /*filePtr*/0) {
//		curPtr = filePtr;
                curPtr = 0
                return false //-1;
            }
            if (curPtr >  /*filePtr +*/fileSize) {
//		curPtr = filePtr + fileSize;
                curPtr = fileSize //TODO:-1
                return false //-1;
            }
            return true //0;
        }

        //
        // changes memory file to read only
        fun MakeReadOnly() {
            mode = 1 shl fsMode_t.FS_READ.ordinal
            Rewind()
        }

        // clear the file

        fun Clear(freeMemory: Boolean = true /*= true*/) {
            fileSize = 0
            granularity = 16384
            if (freeMemory) {
                allocated = 0
                //		Mem_Free( filePtr );
                filePtr = null
                curPtr = 0
            } else {
                curPtr = 0
            }
        }

        // set data for reading
        fun SetData(data: ByteBuffer, length: Int) {
            maxSize = 0
            fileSize = length
            allocated = 0
            granularity = 16384
            mode = 1 shl TempDump.etoi(fsMode_t.FS_READ)
            filePtr = data.duplicate()
            curPtr = 0
        }

        // returns const pointer to the memory buffer
        fun GetDataPtr(): ByteBuffer {
            return filePtr!!
        }

        // set the file granularity
        fun SetGranularity(g: Int) {
            assert(g > 0)
            granularity = g
        }
    }

    /*
     =================================================================================

     idFile_BitMsg

     =================================================================================
     */
    class idFile_BitMsg : idFile {
        // friend class			idFileSystemLocal;
        private val mode // open mode
                : Int
        private val msg: idBitMsg
        private val name // name of the file
                : idStr = idStr()

        //
        //
        constructor(msg: idBitMsg) {
            name.set("*unknown*")
            mode = 1 shl fsMode_t.FS_WRITE.ordinal
            this.msg = msg
        }

        constructor(msg: idBitMsg, readOnly: Boolean) {
            name.set("*unknown*")
            mode = 1 shl fsMode_t.FS_READ.ordinal
            this.msg = msg
        }

        // public	virtual					~idFile_BitMsg( void );
        override fun GetName(): String {
            return name.toString()
        }

        override fun GetFullPath(): String {
            return name.toString()
        }

        override fun Read(buffer: ByteBuffer): Int {
            return Read(buffer, buffer.capacity())
        }

        override fun Read(buffer: ByteBuffer, len: Int): Int {
            //buffer.order(ByteOrder.LITTLE_ENDIAN) //TODO: make sure BitMsg also need little endianness
            if (0 == mode and (1 shl fsMode_t.FS_READ.ordinal)) {
                idLib.common.FatalError("idFile_BitMsg::Read: %s not opened in read mode", name)
                return 0
            }
            return msg.ReadData(buffer, len) //TODO:cast self to self???????
        }

        override fun Write(buffer: ByteBuffer /*, int len*/): Int {
            val len = buffer.capacity()
            if (0 == mode and (1 shl fsMode_t.FS_WRITE.ordinal)) {
                idLib.common.FatalError("idFile_Memory::Write: %s not opened in write mode", name)
                return 0
            }
            msg.WriteData(buffer, len)
            return len
        }

        override fun Length(): Int {
            return msg.GetSize()
        }

        override fun Timestamp(): Long {
            return 0
        }

        override fun Tell(): Int {
            return if (mode and fsMode_t.FS_READ.ordinal != 0) {
                msg.GetReadCount()
            } else {
                msg.GetSize()
            }
        }

        override fun ForceFlush() {}
        override fun Flush() {}

        /*
         =================
         idFile_BitMsg::Seek

         returns zero on success and -1 on failure
         =================
         */
        override fun Seek(offset: Long, origin: fsOrigin_t): Boolean {
            return false //-1;
        }
    }

    /*
     =================================================================================

     idFile_Permanent

     =================================================================================
     */
    class idFile_Permanent : idFile() {
        // friend class			idFileSystemLocal;
        var fileSize // size of the file
                : Int
        val fullPath // full file path - OS path
                : idStr = idStr()
        var handleSync // true if written data is immediately flushed
                : Boolean
        var mode // open mode
                : Int
        val name // relative path of the file - relative path
                : idStr = idStr()
        var o // file handle
                : FileChannel?

        // for debugging purposes
        private var positionInFile = 0L

        // public	virtual					~idFile_Permanent( void );
        override fun GetName(): String {
            return name.toString()
        }

        override fun GetFullPath(): String {
            return fullPath.toString()
        }

        override fun Read(buffer: ByteBuffer): Int {
            return Read(buffer, buffer.capacity())
        }

        /*
         =================
         idFile_Permanent::Read

         Properly handles partial reads
         =================
         */
        override fun Read(buffer: ByteBuffer, len: Int): Int {
            var remaining: Int
            var read: Int
            var tries: Boolean

            if (0 == mode and (1 shl fsMode_t.FS_READ.ordinal)) {
                idLib.common.FatalError("idFile_Permanent::Read: %s not opened in read mode", name)
                return 0
            }
            if (null == o) {
                return 0
            }

            remaining = len
            tries = false

            // force little endian
            buffer.order(ByteOrder.LITTLE_ENDIAN)
            buffer.limit(len)

            try {
                while (remaining != 0) {
                    read = o!!.read(buffer)
                    if (read == -1 || read == 0) {
                        // we might have been trying to read from a CD, which
                        // sometimes returns a 0 read on windows
                        if (!tries) {
                            tries = true
                        } else {
                            FileSystem_h.fileSystem.AddToReadCount(len - remaining)
                            return len - remaining
                        }
                    }

                    if (read == -1) {
                        Common.common.FatalError("idFile_Permanent::Read: -1 bytes read from %s", name.toString())
                    }

                    remaining -= read
                }
            } catch (ex: IOException) {
                Logger.getLogger(File_h::class.java.name).log(Level.SEVERE, null, ex)
            }

            FileSystem_h.fileSystem.AddToReadCount(len)
            // Kotlin specific bytbuffer shenanigans to reset position in buffer
            buffer.clear()
            // for debugging purposes
            positionInFile = o!!.position()

            return len
        }

        /*
         =================
         idFile_Permanent::Write

         Properly handles partial writes
         =================
         */
        override fun Write(buffer: ByteBuffer): Int {
            return Write(buffer, buffer.capacity())
        }

        override fun Write(buffer: ByteBuffer, len: Int): Int {
            var block: Int
            var remaining: Int
            var written: Int
            //            byte[] buf;
            var tries: Int
            if (0 == mode and (1 shl fsMode_t.FS_WRITE.ordinal)) {
                idLib.common.FatalError("idFile_Permanent::Write: %s not opened in write mode", name)
                return 0
            }
            if (o == null) {
                return 0
            }

            buffer.order(ByteOrder.LITTLE_ENDIAN)
            buffer.limit(len)

            remaining = len
            tries = 0
            try {
                while (remaining != 0) {
                    block = remaining
                    //                written = fwrite(buf, 1, block, o);
                    written = o!!.write(buffer)
                    if (written == 0) {
                        tries = if (0 == tries) {
                            1
                        } else {
                            idLib.common.Printf("idFile_Permanent::Write: 0 bytes written to %s\n", name)
                            return 0
                        }
                    }
                    if (written == -1) {
                        idLib.common.Printf("idFile_Permanent::Write: -1 bytes written to %s\n", name)
                        return 0
                    }
                    remaining -= written
                    //                buf += written;
                    fileSize += written
                }
                if (handleSync) {
                    o!!.force(false)
                }
            } catch (ex: IOException) {
                Logger.getLogger(File_h::class.java.name).log(Level.SEVERE, null, ex)
            }
            buffer.clear()
            // for debugging purposes
            positionInFile = o!!.position()
            return len
        }

        override fun Length(): Int {
            return fileSize
        }

        override fun Timestamp(): Long {
            return win_main.Sys_FileTimeStamp(GetFullPath())
        }

        override fun Tell(): Int {
            try {
                return o!!.position().toInt() //return ftell(o);
            } catch (ex: IOException) {
                Logger.getLogger(File_h::class.java.name).log(Level.SEVERE, null, ex)
            }
            return -1
        }

        override fun ForceFlush() {
//            setvbuf(o, null, _IONBF, 0);
        }

        override fun Flush() {
            try {
                o!!.force(false)
            } catch (ex: IOException) {
                Logger.getLogger(File_h::class.java.name).log(Level.SEVERE, null, ex)
            }
        }

        /*
         =================
         idFile_Permanent::Seek

         returns zero on success and -1 on failure
         =================
         */
        override fun Seek(offset: Long, origin: fsOrigin_t): Boolean {
            var _origin: Long = 0
            try {
                when (origin) {
                    fsOrigin_t.FS_SEEK_CUR -> _origin = o!!.position()
                    fsOrigin_t.FS_SEEK_END -> _origin = o!!.size()
                    fsOrigin_t.FS_SEEK_SET -> _origin = 0
                    else -> {
                        _origin = o!!.position()
                        idLib.common.FatalError("idFile_Permanent::Seek: bad origin for %s\n", name)
                    }
                }
            } catch (ex: IOException) {
                Logger.getLogger(File_h::class.java.name).log(Level.SEVERE, null, ex)
            }
            try {
                // FIX: was `o!!.position(_origin)` — offset was completely ignored.
                // C++ does `fseek(o, offset, _origin)` which applies offset relative to origin.
                o!!.position(_origin + offset)
                return true
            } catch (ex: IOException) {
                Logger.getLogger(File_h::class.java.name).log(Level.SEVERE, null, ex)
            }
            return false
        }

        // returns file pointer
        fun GetFilePtr(): FileChannel? {
            return o
        }

        //
        //
        init {
            name.set("invalid")
            o = null
            mode = 0
            fileSize = 0
            handleSync = false
        }
    }

    /*
     =================================================================================

     idFile_InZip

     =================================================================================
     */
    internal class idFile_InZip : idFile() {
        var fileSize // size of the file
                : Int
        var fullPath // full file path including pak file name
                : idStr
        var name // name of the file in the pak
                : idStr
        var z // unzip info //TODO:use faster zip method
                : ZipEntry = ZipEntry("entry")
        var zipFilePos // zip file info position in pak
                : Int
        private var byteCounter // current offset within zip archive.
                : Int

        //
        //
        private var inputStream: InputStream? = null

        // public	virtual					~idFile_InZip( void );
        override fun GetName(): String {
            return name.toString()
        }

        override fun GetFullPath(): String {
            return fullPath.plus('/').plus(name).toString()
        }

        override fun Read(buffer: ByteBuffer): Int {
            return this.Read(buffer, buffer.capacity())
        }

        override fun Read(buffer: UByteArray, len: Int): Int {
            var l = 0
            var len = len
            try {
                if (inputStream == null) {
                    inputStream = ZipFile(fullPath.toString()).getInputStream(z)
                }
                // FIX: was `l += read` even when read returned -1, corrupting the return value
                while (len != 0) {
                    val read = inputStream!!.read(buffer.asByteArray(), l, len)
                    if (read == -1) break
                    l += read
                    len -= read
                }
            } catch (ex: IOException) {
                idLib.common.FatalError("idFile_InZip::Read: error while reading from %s", name)
            }
            FileSystem_h.fileSystem.AddToReadCount(l)
            byteCounter += l
            return l
        }


        override fun Read(buffer: ByteBuffer, len: Int): Int {
            var l = 0
            var len = len

            //buffer.order(ByteOrder.LITTLE_ENDIAN)

            try {
                if (inputStream == null) {
                    inputStream = ZipFile(fullPath.toString()).getInputStream(z)
                }
                // FIX: was `l += read` even when read returned -1, corrupting the return value
                while (len != 0) {
                    val read = inputStream!!.read(buffer.array(), l, len)
                    if (read == -1) break
                    l += read
                    len -= read
                }
            } catch (ex: IOException) {
                idLib.common.FatalError("idFile_InZip::Read: error while reading from %s", name)
            }
            FileSystem_h.fileSystem.AddToReadCount(l)
            byteCounter += l

            return l
        }

        override fun Write(buffer: ByteBuffer /*, int len*/): Int {
            idLib.common.FatalError("idFile_InZip::Write: cannot write to the zipped file %s", name)
            return 0
        }

        override fun Length(): Int {
            return fileSize
        }

        override fun Timestamp(): Long {
            return 0
        }

        override fun Tell(): Int {
            return byteCounter
        }

        override fun ForceFlush() {
            idLib.common.FatalError("idFile_InZip::ForceFlush: cannot flush the zipped file %s", name)
        }

        override fun Flush() {
            idLib.common.FatalError("idFile_InZip::Flush: cannot flush the zipped file %s", name)
        }

        override fun Seek(offset: Long, origin: fsOrigin_t): Boolean {
            var offset = offset
            var res: Int
            var i: Int
            var buf: ByteBuffer
            when (origin) {
                fsOrigin_t.FS_SEEK_END -> {
                    offset = fileSize - offset
                    run {

                        // set the file position in the zip file (also sets the current file info)
//                    unzSetCurrentFileInfoPosition( z, zipFilePos );
                        unzOpenCurrentFile()
                        if (offset <= 0) {
                            return true //0;
                        }
                    }
                    run {
                        //TODO: negative offsets?
                        buf = ByteBuffer.allocate(ZIP_SEEK_BUF_SIZE)
                        i = 0
                        while (i < offset - ZIP_SEEK_BUF_SIZE) {
                            res = Read(buf, ZIP_SEEK_BUF_SIZE)
                            if (res < ZIP_SEEK_BUF_SIZE) {
                                return false //-1;
                            }
                            i += ZIP_SEEK_BUF_SIZE
                        }
                        res = i + Read(buf, offset.toInt() - i)
                        return res.toLong() == offset //? 0 : -1;
                    }
                }

                fsOrigin_t.FS_SEEK_SET -> {
                    run {
                        unzOpenCurrentFile()
                        if (offset <= 0) {
                            return true
                        }
                    }
                    run {
                        buf = ByteBuffer.allocate(ZIP_SEEK_BUF_SIZE)
                        i = 0
                        while (i < offset - ZIP_SEEK_BUF_SIZE) {
                            res = Read(buf, ZIP_SEEK_BUF_SIZE)
                            if (res < ZIP_SEEK_BUF_SIZE) {
                                return false
                            }
                            i += ZIP_SEEK_BUF_SIZE
                        }
                        res = i + Read(buf, offset.toInt() - i)
                        return res.toLong() == offset
                    }
                }

                fsOrigin_t.FS_SEEK_CUR -> {
                    buf = ByteBuffer.allocate(ZIP_SEEK_BUF_SIZE)
                    i = 0
                    while (i < offset - ZIP_SEEK_BUF_SIZE) {
                        res = Read(buf, ZIP_SEEK_BUF_SIZE)
                        if (res < ZIP_SEEK_BUF_SIZE) {
                            return false
                        }
                        i += ZIP_SEEK_BUF_SIZE
                    }
                    res = i + Read(buf, offset.toInt() - i)
                    return res.toLong() == offset
                }

                else -> {
                    idLib.common.FatalError("idFile_InZip::Seek: bad origin for %s\n", name)
                }
            }
            return false //-1;
        }

        private fun unzOpenCurrentFile() {
            try {
                byteCounter = 0 //reset counter.
                if (inputStream != null) { //FS_SEEK_SET -> FS_SEEK_CUR
                    inputStream!!.close()
                }
            } catch (ex: IOException) {
                idLib.common.FatalError("idFile_InZip::unzOpenCurrentFile: we're in deep shit bub \n")
            }
            inputStream = null //reload inputStream.
        }

        companion object {
            // friend class			idFileSystemLocal;
            /*
         =================
         idFile_InZip::Seek

         returns zero on success and -1 on failure
         =================
         */
            const val ZIP_SEEK_BUF_SIZE = 1 shl 15
        }

        init {
            name = idStr("invalid")
            fullPath = idStr()
            zipFilePos = 0
            fileSize = 0
            byteCounter = 0
            // memset( &z, 0, sizeof( z ) );//TODO:size of void ptr
        }
    }
}
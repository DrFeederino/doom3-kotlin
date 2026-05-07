package neo.framework

import neo.framework.FileSystem_h.fsMode_t
import neo.idlib.*
import neo.idlib.BV.idBounds
import neo.idlib.BitMsg.idBitMsg
import neo.idlib.Text.Str.idStr
import neo.idlib.Text.atobb
import neo.idlib.containers.CBool
import neo.idlib.containers.CFloat
import neo.idlib.containers.CInt
import neo.idlib.containers.CLong
import neo.idlib.math.*
import neo.idlib.math.Matrix.idMat3
import neo.sys.win_main
import neo.ui.Rectangle.idRectangle
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
                            f = (argPtr[va_ptr++] as Number).toFloat()
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
                            temp = String.format(format.replace("u", "d"), u)
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
                            str = argPtr[va_ptr++]?.toString()
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
        FS_SEEK_CUR,
        FS_SEEK_END,
        FS_SEEK_SET
    }

    /*
     =================================================================================

     idFile

     =================================================================================
     */
    abstract class idFile {
        // Reusable read buffer for primitive reads — avoids ByteBuffer allocation per call
        // Sized to fit the largest fixed-size read (idMat3 = 36 bytes)
        private val _readBuf: ByteBuffer = ByteBuffer.allocate(36).order(ByteOrder.LITTLE_ENDIAN)
        private val _writeBuf: ByteBuffer = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN)
        private var _stringWriteBuf: ByteBuffer = ByteBuffer.allocate(1024)

        private fun BeginWrite(): ByteBuffer {
            _writeBuf.clear()
            return _writeBuf
        }

        private fun EndWrite(len: Int): Int {
            _writeBuf.flip()
            return Write(_writeBuf, len)
        }

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

        fun Read(classObject: idSerializable): Int {
            val before = Tell()
            classObject.readFrom(this)
            return Tell() - before
        }

        fun Read(`object`: idSerializable, len: Int): Int {
            // idStr serializes as `len` raw bytes (length is known externally by the caller).
            // Bypass readFrom() which would read its fixed BYTES-sized slot.
            if (`object` is idStr) {
                if (len <= 0) {
                    `object`.set("")
                    return 0
                }
                val sb = StringBuilder(len)
                var i = 0
                while (i < len) {
                    sb.append((ReadChar().toInt() and 0xFF).toChar())
                    i++
                }
                `object`.set(sb.toString())
                return len
            }
            val before = Tell()
            `object`.readFrom(this)
            return Tell() - before
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
        fun Write(`object`: idSerializable): Int {
            return when (`object`) {
                is idVec2 -> WriteVec2(`object`)
                is idVec3 -> WriteVec3(`object`)
                is idVec4 -> WriteVec4(`object`)
                is idVec5 -> WriteVec5(`object`)
                is idVec6 -> WriteVec6(`object`)
                is idBounds -> WriteBounds(`object`)
                is idAngles -> WriteAngles(`object`)
                is idRectangle -> WriteRectangle(`object`)
                else -> {
                    `object`.writeTo(this)
                    0
                }
            }
        }

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
            idStr.vsnPrintf(buf, MAX_PRINT_MSG - 1, fmt, *args /*, argptr*/)

            // so notepad formats the lines correctly
            val work = idStr(buf[0])
            work.Replace("\n", "\r\n")
            val bb = atobb(work)!!
            return Write(bb, bb.remaining())
        }

        // Like fprintf but with argument pointer
        fun VPrintf(fmt: String, vararg args: Any /*, va_list arg*/): Int {
            val buf = arrayOf("") //new char[MAX_PRINT_MSG];
            val length: Int
            length = idStr.vsnPrintf(buf, MAX_PRINT_MSG - 1, fmt, *args /*, args*/)
            return Write(atobb(buf[0])!!)
        }

        // Write a string with high precision doubleing point numbers to the file.
        fun WriteFloatString(fmt: String, vararg args: Any): Int /* id_attribute((format(printf,2,3)))*/ {
            val buf = CharArray(MAX_PRINT_MSG)
            val len: Int

            len = FS_WriteFloatString(buf, fmt, *args)
            return Write(atobb(buf)!!, len)
        }

        // Endian portable alternatives to Read(...)
        fun ReadInt(value: CInt): Int {
            _readBuf.clear()
            val result = Read(_readBuf, 4)
            _readBuf.rewind()
            value._val = (LittleLong(_readBuf.getInt()))
            return result
        }

        // Endian portable alternatives to Read(...)
        fun ReadInt(value: CLong): Int {
            _readBuf.clear()
            val result = Read(_readBuf, 4)
            _readBuf.rewind()
            value._val = (LittleLong(_readBuf.getInt()).toLong())
            return result
        }

        fun ReadInt(): Int {
            _readBuf.clear()
            Read(_readBuf, 4)
            _readBuf.rewind()
            return LittleLong(_readBuf.getInt())
        }

        fun WriteInt(value: Int): Int {
            val intBytes = BeginWrite()
            val v: Int = LittleLong(value)
            intBytes.putInt(v)
            return EndWrite(4)
        }

        fun WriteInt(value: Enum<*>): Int {
            return WriteInt(value.ordinal)
        }

        fun WriteUnsignedInt(value: Long): Int {
            val uintBytes = BeginWrite()
            val v: Int = LittleLong(value.toInt())
            uintBytes.putInt(v)
            return EndWrite(4)
        }

        fun ReadShort(value: ShortArray): Int {
            _readBuf.clear()
            val result = Read(_readBuf, 2)
            _readBuf.rewind()
            value[0] = LittleShort(_readBuf.short)
            return result
        }

        fun ReadShort(): Short {
            _readBuf.clear()
            Read(_readBuf, 2)
            _readBuf.rewind()
            return LittleShort(_readBuf.short)
        }

        fun WriteShort(value: Short): Int {
            val shortBytes = BeginWrite()
            val v: Short = LittleShort(value)
            shortBytes.putShort(v)
            return EndWrite(2)
        }

        fun ReadUnsignedShort(value: IntArray): Int {
            _readBuf.clear()
            val result = Read(_readBuf, 2)
            _readBuf.rewind()
            value[0] = LittleShort(_readBuf.short).toInt() and 0xFFFF
            return result
        }

        fun ReadUnsignedShort(): Int {
            _readBuf.clear()
            Read(_readBuf, 2)
            _readBuf.rewind()
            return LittleShort(_readBuf.short).toInt() and 0xFFFF
        }

        fun WriteUnsignedShort(value: Int): Int {
            val ushortBytes = BeginWrite()
            val v: Short = LittleShort(value.toShort())
            ushortBytes.putShort(v)
            return EndWrite(2)
        }

        fun ReadChar(value: ShortArray): Int {
            _readBuf.clear()
            val result = Read(_readBuf, 1)
            value[0] = _readBuf[0].toShort()
            return result
        }

        fun ReadChar(): Short {
            _readBuf.clear()
            Read(_readBuf, 1)
            return _readBuf[0].toShort()
        }

        fun WriteChar(value: Short): Int {
            val charBytes = BeginWrite()
            charBytes.put(value.toByte())
            return EndWrite(1)
        }

        fun WriteChar(value: Char): Int {
            return WriteChar(value.code.toShort())
        }

        fun ReadUnsignedChar(value: CharArray): Int {
            _readBuf.clear()
            val result = Read(_readBuf, 1)
            value[0] = (_readBuf[0].toInt() and 0xFF).toChar()
            return result
        }

        fun WriteUnsignedChar(value: Char): Int {
            val ucharBytes = BeginWrite()
            ucharBytes.put(value.code.toByte())
            return EndWrite(1)
        }

        fun ReadFloat(value: CFloat): Int {
            _readBuf.clear()
            val result = Read(_readBuf, 4)
            _readBuf.rewind()
            value._val = (LittleFloat(_readBuf.getFloat()))
            return result
        }

        fun ReadFloat(): Float {
            _readBuf.clear()
            Read(_readBuf, 4)
            _readBuf.rewind()
            return LittleFloat(_readBuf.getFloat())
        }

        fun WriteFloat(value: Float): Int {
            val floatBytes = BeginWrite()
            val v: Float = LittleFloat(value)
            floatBytes.putFloat(v)
            return EndWrite(4)
        }

        fun ReadBool(value: CBool): Int {
            _readBuf.clear()
            val result = Read(_readBuf, 1)
            value._val = _readBuf[0] != 0.toByte()
            return result
        }

        fun ReadBool(): Boolean {
            _readBuf.clear()
            Read(_readBuf, 1)
            return _readBuf[0] != 0.toByte()
        }

        fun WriteBool(value: Boolean): Int {
            val c: Char = if (value) '\u0001' else '\u0000'
            return WriteUnsignedChar(c)
        }

        fun ReadString(string: idStr): Int {
            val len = CInt()
            var result = 0
            ReadInt(len)
            if (len._val > 0) {
                assert(len._val <= 1000000)
                val sb = StringBuilder(len._val)
                var i = 0
                while (i < len._val) {
                    val c = ReadChar().toInt() and 0xFF
                    sb.append(c.toChar())
                    i++
                }
                string.set(sb.toString())
                result = len._val
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
            return WriteStringData(value.data, len)
        }

        fun WriteStringData(value: String, len: Int = value.length): Int {
            if (len <= 0) {
                return 0
            }
            if (_stringWriteBuf.capacity() < len) {
                var newSize = _stringWriteBuf.capacity()
                while (newSize < len) {
                    newSize *= 2
                }
                _stringWriteBuf = ByteBuffer.allocate(newSize)
            }
            _stringWriteBuf.clear()
            var i = 0
            while (i < len) {
                _stringWriteBuf.put(if (i < value.length) value[i].code.toByte() else 0)
                i++
            }
            _stringWriteBuf.flip()
            return Write(_stringWriteBuf, len)
        }

        open fun Write(objectToWrite: idSerializable, len: Int): Int {
            objectToWrite.writeTo(this)
            return len
        }

        fun ReadVec2(vec: idVec2): Int {
            _readBuf.clear()
            val result = Read(_readBuf, idVec2.BYTES)
            _readBuf.rewind()
            vec.set(_readBuf.float, _readBuf.float)
            return result
        }

        fun WriteVec2(vec: idVec2): Int {
            val buffer = BeginWrite()
            buffer.putFloat(vec.x)
            buffer.putFloat(vec.y)
            return EndWrite(idVec2.BYTES)
        }

        fun ReadVec3(vec: idVec3): Int {
            _readBuf.clear()
            val result = Read(_readBuf, idVec3.BYTES)
            _readBuf.rewind()
            vec.set(_readBuf.float, _readBuf.float, _readBuf.float)
            return result
        }

        fun WriteVec3(vec: idVec3): Int {
            val buffer = BeginWrite()
            buffer.putFloat(vec.x)
            buffer.putFloat(vec.y)
            buffer.putFloat(vec.z)
            return EndWrite(idVec3.BYTES)
        }

        fun ReadVec4(vec: idVec4): Int {
            _readBuf.clear()
            val result = Read(_readBuf, idVec4.BYTES)
            _readBuf.rewind()
            vec.set(_readBuf.float, _readBuf.float, _readBuf.float, _readBuf.float)
            return result
        }

        fun WriteVec4(vec: idVec4): Int {
            val buffer = BeginWrite()
            buffer.putFloat(vec.x)
            buffer.putFloat(vec.y)
            buffer.putFloat(vec.z)
            buffer.putFloat(vec.w)
            return EndWrite(idVec4.BYTES)
        }

        fun WriteVec5(vec: idVec5): Int {
            val buffer = BeginWrite()
            buffer.putFloat(vec.x)
            buffer.putFloat(vec.y)
            buffer.putFloat(vec.z)
            buffer.putFloat(vec.s)
            buffer.putFloat(vec.t)
            return EndWrite(idVec5.BYTES)
        }

        fun ReadVec6(vec: idVec6): Int {
            _readBuf.clear()
            val result = Read(_readBuf, idVec6.BYTES)
            vec.set(
                idVec6(
                    _readBuf.getFloat(0),
                    _readBuf.getFloat(4),
                    _readBuf.getFloat(8),
                    _readBuf.getFloat(12),
                    _readBuf.getFloat(16),
                    _readBuf.getFloat(20)
                )
            )
            return result
        }

        fun WriteVec6(vec: idVec6): Int {
            val buffer = BeginWrite()
            buffer.putFloat(vec.p[0])
            buffer.putFloat(vec.p[1])
            buffer.putFloat(vec.p[2])
            buffer.putFloat(vec.p[3])
            buffer.putFloat(vec.p[4])
            buffer.putFloat(vec.p[5])
            return EndWrite(idVec6.BYTES)
        }

        fun ReadMat3(mat: idMat3): Int {
            _readBuf.clear()
            val result = Read(_readBuf, idMat3.BYTES)
            mat.set(
                idMat3(
                    _readBuf.getFloat(0),
                    _readBuf.getFloat(4),
                    _readBuf.getFloat(8),
                    _readBuf.getFloat(12),
                    _readBuf.getFloat(16),
                    _readBuf.getFloat(20),
                    _readBuf.getFloat(24),
                    _readBuf.getFloat(28),
                    _readBuf.getFloat(32)
                )
            )
            return result
        }

        fun WriteMat3(mat: idMat3): Int {
            val buffer = BeginWrite()
            buffer.putFloat(mat[0].x)
            buffer.putFloat(mat[0].y)
            buffer.putFloat(mat[0].z)
            buffer.putFloat(mat[1].x)
            buffer.putFloat(mat[1].y)
            buffer.putFloat(mat[1].z)
            buffer.putFloat(mat[2].x)
            buffer.putFloat(mat[2].y)
            buffer.putFloat(mat[2].z)
            return EndWrite(idMat3.BYTES)
        }

        fun WriteBounds(bounds: idBounds): Int {
            val buffer = BeginWrite()
            buffer.putFloat(bounds[0].x)
            buffer.putFloat(bounds[0].y)
            buffer.putFloat(bounds[0].z)
            buffer.putFloat(bounds[1].x)
            buffer.putFloat(bounds[1].y)
            buffer.putFloat(bounds[1].z)
            return EndWrite(idBounds.BYTES)
        }

        fun WriteAngles(angles: idAngles): Int {
            val buffer = BeginWrite()
            buffer.putFloat(angles.pitch)
            buffer.putFloat(angles.yaw)
            buffer.putFloat(angles.roll)
            return EndWrite(idAngles.BYTES)
        }

        fun WriteRectangle(rect: idRectangle): Int {
            val buffer = BeginWrite()
            buffer.putFloat(rect.x)
            buffer.putFloat(rect.y)
            buffer.putFloat(rect.w)
            buffer.putFloat(rect.h)
            return EndWrite(idRectangle.BYTES)
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

            if (0 == mode and (1 shl (fsMode_t.FS_READ).ordinal)) {
                idLib.common.FatalError("idFile_Memory::Read: %s not opened in read mode", name)
                return 0
            }
            if (curPtr + len > fileSize) {
                len = fileSize - curPtr
            }
            System.arraycopy(filePtr!!.array(), curPtr, buffer.array(), 0, len)
            curPtr += len
            return len
        }

        override fun Write(buffer: ByteBuffer): Int {
            return Write(buffer, buffer.capacity())
        }

        override fun Write(buffer: ByteBuffer, len: Int): Int {
            if (0 == mode and (1 shl (fsMode_t.FS_WRITE).ordinal)) {
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
            val savedLimit = buffer.limit()
            buffer.limit(buffer.position() + len)
            filePtr!!.position(curPtr)
            filePtr!!.put(buffer)
            buffer.limit(savedLimit)
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
            mode = 1 shl (fsMode_t.FS_READ).ordinal
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

        override fun Write(buffer: ByteBuffer): Int {
            return Write(buffer, buffer.capacity())
        }

        override fun Write(buffer: ByteBuffer, len: Int): Int {
            if (0 == mode and (1 shl fsMode_t.FS_WRITE.ordinal)) {
                idLib.common.FatalError("idFile_BitMsg::Write: %s not opened in write mode", name)
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
        private val writeBuffer: ByteBuffer = ByteBuffer.allocate(64 * 1024)

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
            if (0 == mode and (1 shl fsMode_t.FS_WRITE.ordinal)) {
                idLib.common.FatalError("idFile_Permanent::Write: %s not opened in write mode", name)
                return 0
            }
            if (o == null) {
                return 0
            }

            buffer.order(ByteOrder.LITTLE_ENDIAN)
            buffer.limit(len)

            if (len >= writeBuffer.capacity() || handleSync) {
                FlushWriteBuffer()
                if (!RawWrite(buffer, len, true)) {
                    return 0
                }
                if (handleSync) {
                    ForceChannel()
                }
                buffer.clear()
                return len
            }

            if (writeBuffer.remaining() < len) {
                FlushWriteBuffer()
            }
            writeBuffer.put(buffer)
            fileSize += len
            buffer.clear()

            return len
        }

        private fun RawWrite(buffer: ByteBuffer, len: Int, updateFileSize: Boolean): Boolean {
            var remaining = len
            var tries = 0
            try {
                while (remaining != 0) {
                    val written = o!!.write(buffer)
                    if (written == 0) {
                        tries = if (0 == tries) {
                            1
                        } else {
                            idLib.common.Printf("idFile_Permanent::Write: 0 bytes written to %s\n", name)
                            return false
                        }
                    }
                    if (written == -1) {
                        idLib.common.Printf("idFile_Permanent::Write: -1 bytes written to %s\n", name)
                        return false
                    }
                    remaining -= written
                    if (updateFileSize) {
                        fileSize += written
                    }
                }
            } catch (ex: IOException) {
                Logger.getLogger(File_h::class.java.name).log(Level.SEVERE, null, ex)
                return false
            }
            return true
        }

        private fun FlushWriteBuffer() {
            if (o == null || writeBuffer.position() == 0) {
                return
            }
            val len = writeBuffer.position()
            writeBuffer.flip()
            RawWrite(writeBuffer, len, false)
            writeBuffer.clear()
        }

        private fun ForceChannel() {
            try {
                o!!.force(false)
            } catch (ex: IOException) {
                Logger.getLogger(File_h::class.java.name).log(Level.SEVERE, null, ex)
            }
        }

        override fun Length(): Int {
            return fileSize
        }

        override fun Timestamp(): Long {
            return win_main.Sys_FileTimeStamp(GetFullPath())
        }

        override fun Tell(): Int {
            try {
                return o!!.position().toInt() + writeBuffer.position() //return ftell(o);
            } catch (ex: IOException) {
                Logger.getLogger(File_h::class.java.name).log(Level.SEVERE, null, ex)
            }
            return -1
        }

        override fun ForceFlush() {
            handleSync = true
            Flush()
        }

        override fun Flush() {
            FlushWriteBuffer()
            ForceChannel()
        }

        /*
         =================
         idFile_Permanent::Seek

         returns zero on success and -1 on failure
         =================
         */
        override fun Seek(offset: Long, origin: fsOrigin_t): Boolean {
            var _origin: Long = 0
            FlushWriteBuffer()
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
                o!!.position(_origin + offset)
                return true
            } catch (ex: IOException) {
                Logger.getLogger(File_h::class.java.name).log(Level.SEVERE, null, ex)
            }
            return false
        }

        fun Close() {
            Flush()
            try {
                o?.close()
            } catch (ex: IOException) {
                Logger.getLogger(File_h::class.java.name).log(Level.SEVERE, null, ex)
            } finally {
                o = null
            }
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

            try {
                if (inputStream == null) {
                    inputStream = ZipFile(fullPath.toString()).getInputStream(z)
                }
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

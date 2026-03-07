package neo.idlib

import neo.TempDump
import neo.idlib.Dict_h.idDict
import neo.idlib.Dict_h.idKeyValue
import neo.idlib.Text.Str.idStr
import neo.idlib.containers.CInt
import neo.idlib.math.*
import neo.sys.sys_public.netadr_t
import neo.sys.sys_public.netadrtype_t
import java.nio.ByteBuffer
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.math.abs

object BitMsg {
    /*
     ==============================================================================

     idBitMsgDelta

     ==============================================================================
     */
    const val MAX_DATA_BUFFER = 1024

    /*
     ===============================================================================

     idBitMsg

     Handles byte ordering and avoids alignment errors.
     Allows concurrent writing and reading.
     The data set with Init is never freed.

     ===============================================================================
     */
    class idBitMsg//	writeData = null;
//	readData = null;
    //
    //
    {
        private var allowOverflow // if false, generate an error when the message is overflowed
                = false
        private var curSize // current size of message in bytes
                = 0
        private var maxSize // maximum size of message in bytes
                = 0
        private var overflowed // set to true if the buffer size failed (with allowOverflow set)
                = false
        private var readBit // number of bits read from the last read byte
                = 0
        private var readCount // number of bytes read so far
                = 0
        private var readData // pointer to data for reading
                : ByteBuffer? = null
        private var writeBit // number of bits written to the last written byte
                = 0
        private var writeData // pointer to data for writing
                : ByteBuffer? = null

        fun Init(data: ByteArray) {
            this.Init(ByteBuffer.wrap(data), data.size)
        }


        fun Init(data: ByteBuffer, length: Int = data.capacity()) {
            writeData = data
            readData = data
            maxSize = length
        }

        fun InitReadOnly(data: ByteBuffer, length: Int) {
            writeData = null
            readData = data
            maxSize = length
        }

        // get data for writing
        fun GetData(): ByteBuffer? {
            return writeData
        }

        // get data for reading
        fun GetDataReadOnly(): ByteBuffer {
            return readData!!.duplicate()
        }

        // get the maximum message size
        fun GetMaxSize(): Int {
            return maxSize
        }

        // generate error if not set and message is overflowed
        fun SetAllowOverflow(set: Boolean) {
            allowOverflow = set
        }

        // returns true if the message was overflowed
        fun IsOverflowed(): Boolean {
            return overflowed
        }

        // size of the message in bytes
        fun GetSize(): Int {
            return curSize
        }

        // set the message size
        fun SetSize(size: Int) {
            curSize = if (size > maxSize) {
                maxSize
            } else {
                if (size < 0) {
                    0
                } else {
                    size
                }
            }
        }

        // get current write bit
        fun GetWriteBit(): Int {
            return writeBit
        }

        // set current write bit
        fun SetWriteBit(bit: Int) {
            writeBit = bit and 7
            if (writeBit != 0) {
                val pos = curSize - 1
                val `val` = writeData!!.getInt(pos)
                writeData!!.putInt(pos, `val` and (1 shl writeBit) - 1)
            }
        }

        // returns number of bits written
        fun GetNumBitsWritten(): Int {
            return (curSize shl 3) - (8 - writeBit and 7)
        }

        // space left in bits for writing
        fun GetRemainingWriteBits(): Int {
            return (maxSize shl 3) - GetNumBitsWritten()
        }

        // save the write state
        fun SaveWriteState(s: CInt, b: CInt) {
            s.integerValue = curSize
            b.integerValue = writeBit
        }

        // restore the write state
        fun RestoreWriteState(s: Int, b: Int) {
            curSize = s
            writeBit = b and 7
            if (writeBit != 0) {
                val pos = curSize - 1
                val `val` = writeData!!.getInt(pos)
                writeData!!.putInt(pos, `val` and (1 shl writeBit) - 1)
            }
        }

        // bytes read so far
        fun GetReadCount(): Int {
            return readCount
        }

        // set the number of bytes and bits read
        fun SetReadCount(bytes: Int) {
            readCount = bytes
        }

        // get current read bit
        fun GetReadBit(): Int {
            return readBit
        }

        // set current read bit
        fun SetReadBit(bit: Int) {
            readBit = bit and 7
        }

        // returns number of bits read
        fun GetNumBitsRead(): Int {
            return (readCount shl 3) - (8 - readBit and 7)
        }

        // number of bits left to read
        fun GetRemainingReadBits(): Int {
            return (curSize shl 3) - GetNumBitsRead()
        }

        // save the read state
        fun SaveReadState(c: CInt, b: CInt) {
            c.integerValue = readCount
            b.integerValue = readBit
        }

        // restore the read state
        fun RestoreReadState(c: Int, b: Int) {
            readCount = c
            readBit = b and 7
        }

        // begin writing
        fun BeginWriting() {
            curSize = 0
            overflowed = false
            writeBit = 0
        }

        // space left in bytes
        fun GetRemainingSpace(): Int {
            return maxSize - curSize
        }

        // write up to the next byte boundary
        fun WriteByteAlign() {
            writeBit = 0
        }

        /*
         ================
         idBitMsg::WriteBits

         If the number of bits is negative a sign is included.
         ================
         */
        // write the specified number of bits
        fun WriteBits(value: Int, numBits: Int) {
            var value = value
            var numBits = numBits
            var put: Int
            var fraction: Int
            try {
                if (writeData == null) {
                    idLib.common.Error("idBitMsg.WriteBits: cannot write to message")
                }

                // check if the number of bits is valid
                if (numBits == 0 || numBits < -31 || numBits > 32) {
                    idLib.common.Error("idBitMsg.WriteBits: bad numBits %d", numBits)
                }

                // check for value overflows
                // this should be an error really, as it can go unnoticed and cause either bandwidth or corrupted data transmitted
                if (numBits != 32) {
                    if (numBits > 0) {
                        if (value > (1 shl numBits) - 1) {
                            idLib.common.Warning("idBitMsg.WriteBits: value overflow %d %d", value, numBits)
                        } else if (value < 0) {
                            idLib.common.Warning("idBitMsg.WriteBits: value overflow %d %d", value, numBits)
                        }
                    } else {
                        val r = 1 shl -1 - numBits
                        if (value > r - 1) {
                            idLib.common.Warning("idBitMsg.WriteBits: value overflow %d %d", value, numBits)
                        } else if (value < -r) {
                            idLib.common.Warning("idBitMsg.WriteBits: value overflow %d %d", value, numBits)
                        }
                    }
                }
                if (numBits < 0) {
                    numBits = -numBits
                }

                // check for msg overflow
                if (CheckOverflow(numBits)) {
                    return
                }

                // write the bits
                while (numBits != 0) {
                    if (writeBit == 0) {
//                        writeData.putInt(curSize, 0);
                        writeData!!.put(0.toByte())
                        curSize++
                    }
                    put = 8 - writeBit
                    if (put > numBits) {
                        put = numBits
                    }
                    fraction = value and ((1 shl put) - 1)
                    val pos = curSize - 1
                    val `val` = writeData!!.get(pos).toInt()
                    writeData!!.put(pos, (`val` or (fraction shl writeBit)).toByte())
                    numBits -= put
                    value = value shr put
                    writeBit = writeBit + put and 7
                }
            } catch (ex: idException) {
                Logger.getLogger(BitMsg::class.java.name).log(Level.SEVERE, null, ex)
            }
        }

        fun WriteChar(c: Int) {
            WriteBits(c, -8)
        }

        fun WriteByte(c: Byte) {
            WriteBits(c.toInt(), 8)
        }

        fun WriteShort(c: Short) {
            WriteBits(c.toInt(), -16)
        }

        fun WriteUShort(c: Int) {
            WriteBits(c, 16)
        }

        fun WriteLong(c: Int) {
            WriteBits(c, 32)
        }

        fun WriteFloat(f: Float) {
            WriteBits(f.toBits().toInt(), 32)
        }

        fun WriteFloat(f: Float, exponentBits: Int, mantissaBits: Int) {
            val bits = idMath.FloatToBits(f, exponentBits, mantissaBits)
            WriteBits(bits, 1 + exponentBits + mantissaBits)
        }

        fun WriteAngle8(f: Float) {
            WriteByte(ANGLE2BYTE(f).toInt().toByte())
        }

        fun WriteAngle16(f: Float) {
            WriteShort(ANGLE2SHORT(f).toInt().toShort())
        }

        fun WriteDir(dir: idVec3, numBits: Int) {
            WriteBits(DirToBits(dir, numBits), numBits)
        }


        @Throws(idException::class)
        fun WriteString(s: String?, maxLength: Int = -1, make7Bit: Boolean = true) {
            if (null == s) {
                WriteData(ByteBuffer.wrap("".toByteArray()), 1) //TODO:huh?
            } else {
                var i: Int
                var l: Int
                val dataPtr: ByteArray
                val bytePtr: ByteArray
                l = s.length
                if (maxLength >= 0 && l >= maxLength) {
                    l = maxLength - 1
                }
                dataPtr = GetByteSpace(l + 1)
                bytePtr = s.toByteArray()
                if (make7Bit) {
                    i = 0
                    while (i < l) {
                        if (bytePtr[i] > 127) {
                            dataPtr[i] = '.'.code.toByte()
                        } else {
                            dataPtr[i] = bytePtr[i]
                        }
                        i++
                    }
                } else {
                    i = 0
                    while (i < l) {
                        dataPtr[i] = bytePtr[i]
                        i++
                    }
                }
                dataPtr[i] = '\u0000'.code.toByte()
            }
        }

        @Throws(idException::class)
        fun WriteData(data: ByteBuffer, length: Int) {
//            memcpy(GetByteSpace(length), data, length);
            WriteData(data, 0, length)
        }

        @Throws(idException::class)
        fun WriteData(data: ByteBuffer, offset: Int, length: Int) {
//            System.arraycopy(data, offset, GetByteSpace(length), 0, length);
            data.get(GetByteSpace(length), offset, length)
        }

        @Throws(idException::class)
        fun WriteNetadr(adr: netadr_t) {
            val dataPtr: ByteArray
            dataPtr = GetByteSpace(4)
            System.arraycopy(adr.ip, 0, dataPtr, 0, 4)
            WriteUShort(adr.port)
        }

        fun WriteDeltaChar(oldValue: Byte, newValue: Byte) {
            WriteDelta(oldValue.toInt(), newValue.toInt(), -8)
        }

        fun WriteDeltaByte(oldValue: Byte, newValue: Byte) {
            WriteDelta(oldValue.toInt(), newValue.toInt(), 8)
        }

        fun WriteDeltaShort(oldValue: Short, newValue: Short) {
            WriteDelta(oldValue.toInt(), newValue.toInt(), -16)
        }

        fun WriteDeltaLong(oldValue: Int, newValue: Int) {
            WriteDelta(oldValue, newValue, 32)
        }

        fun WriteDeltaFloat(oldValue: Float, newValue: Float) {
            WriteDelta(
                java.lang.Float.floatToIntBits(oldValue.toFloat()),
                java.lang.Float.floatToIntBits(newValue.toFloat()),
                32
            )
        }

        fun WriteDeltaFloat(oldValue: Float, newValue: Float, exponentBits: Int, mantissaBits: Int) {
            val oldBits = idMath.FloatToBits(oldValue, exponentBits, mantissaBits)
            val newBits = idMath.FloatToBits(newValue, exponentBits, mantissaBits)
            WriteDelta(oldBits, newBits, 1 + exponentBits + mantissaBits)
        }

        fun WriteDeltaByteCounter(oldValue: Int, newValue: Int) {
            var i: Int
            val x: Int
            x = oldValue xor newValue
            i = 7
            while (i > 0) {
                if (x and (1 shl i) != 0) {
                    i++
                    break
                }
                i--
            }
            WriteBits(i, 3)
            if (i != 0) {
                WriteBits((1 shl i) - 1 and newValue, i)
            }
        }

        fun WriteDeltaShortCounter(oldValue: Int, newValue: Int) {
            var i: Int
            val x: Int
            x = oldValue xor newValue
            i = 15
            while (i > 0) {
                if (x and (1 shl i) != 0) {
                    i++
                    break
                }
                i--
            }
            WriteBits(i, 4)
            if (i != 0) {
                WriteBits((1 shl i) - 1 and newValue, i)
            }
        }

        //
        fun WriteDeltaLongCounter(oldValue: Int, newValue: Int) {
            var i: Int
            val x: Int
            x = oldValue xor newValue
            i = 31
            while (i > 0) {
                if (x and (1 shl i) != 0) {
                    i++
                    break
                }
                i--
            }
            WriteBits(i, 5)
            if (i != 0) {
                WriteBits((1 shl i) - 1 and newValue, i)
            }
        }

        @Throws(idException::class)
        fun WriteDeltaDict(dict: idDict, base: idDict?): Boolean {
            var i: Int
            var kv: idKeyValue?
            var basekv: idKeyValue?
            var changed = false
            if (base != null) {
                i = 0
                while (i < dict.GetNumKeyVals()) {
                    kv = dict.GetKeyVal(i)!!
                    basekv = base.FindKey(kv.GetKey().toString())
                    if (basekv == null || basekv.GetValue().Icmp(kv.GetValue().toString()) != 0) {
                        WriteString(kv.GetKey().toString())
                        WriteString(kv.GetValue().toString())
                        changed = true
                    }
                    i++
                }
                WriteString("")
                i = 0
                while (i < base.GetNumKeyVals()) {
                    basekv = base.GetKeyVal(i)!!
                    kv = dict.FindKey(basekv.GetKey().toString())
                    if (kv == null) {
                        WriteString(basekv.GetKey().toString())
                        changed = true
                    }
                    i++
                }
                WriteString("")
            } else {
                i = 0
                while (i < dict.GetNumKeyVals()) {
                    kv = dict.GetKeyVal(i)!!
                    WriteString(kv.GetKey().toString())
                    WriteString(kv.GetValue().toString())
                    changed = true
                    i++
                }
                WriteString("")
                WriteString("")
            }
            return changed
        }

        fun BeginReading() {                // begin reading.
            readCount = 0
            readBit = 0
        }

        fun GetRemaingData(): Int {            // number of bytes left to read
            return curSize - readCount
        }

        fun ReadByteAlign() {            // read up to the next byte boundary
            readBit = 0
        }

        /*
         ================
         idBitMsg::ReadBits

         If the number of bits is negative a sign is included.
         ================
         */
        // read the specified number of bits
        @Throws(idException::class)
        fun ReadBits(numBits: Int): Int {
            var numBits = numBits
            var value: Int
            var valueBits: Int
            var get: Int
            var fraction: Int
            val sgn: Boolean
            if (readData == null) {
                idLib.common.FatalError("idBitMsg.ReadBits: cannot read from message")
            }

            // check if the number of bits is valid
            if (numBits == 0 || numBits < -31 || numBits > 32) {
                idLib.common.FatalError("idBitMsg.ReadBits: bad numBits %d", numBits)
            }
            value = 0
            valueBits = 0
            if (numBits < 0) {
                numBits = -numBits
                sgn = true
            } else {
                sgn = false
            }

            // check for overflow
            if (numBits > GetRemainingReadBits()) {
                return -1
            }
            while (valueBits < numBits) {
                if (readBit == 0) {
                    readCount++
                }
                get = 8 - readBit
                if (get > numBits - valueBits) {
                    get = numBits - valueBits
                }
                fraction = readData!!.get(readCount - 1).toInt()
                fraction = fraction shr readBit
                fraction = fraction and (1 shl get) - 1
                value = value or (fraction shl valueBits)

                valueBits += get
                readBit = (readBit + get) and 7
            }
            if (sgn) {
                if (value and (1 shl numBits - 1) != 0) {
                    value = value or (-1 xor (1 shl numBits) - 1)
                }
            }
            return value
        }

        @Throws(idException::class)
        fun ReadChar(): Byte {
            return ReadBits(-8).toByte()
        }

        @Throws(idException::class)
        fun ReadByte(): Byte {
            return ReadBits(8).toByte()
        }

        @Throws(idException::class)
        fun ReadShort(): Short {
            return ReadBits(-16).toShort()
        }

        @Throws(idException::class)
        fun ReadUShort(): Int {
            return ReadBits(16)
        }

        @Throws(idException::class)
        fun ReadLong(): Int {
            return ReadBits(32)
        }

        @Throws(idException::class)
        fun ReadFloat(): Float {
            val value: Float
            value = java.lang.Float.intBitsToFloat(ReadBits(32)).toFloat()
            return value
        }

        @Throws(idException::class)
        fun ReadFloat(exponentBits: Int, mantissaBits: Int): Float {
            val bits = ReadBits(1 + exponentBits + mantissaBits)
            return idMath.BitsToFloat(bits, exponentBits, mantissaBits)
        }

        @Throws(idException::class)
        fun ReadAngle8(): Float {
            return BYTE2ANGLE(ReadByte())
        }

        @Throws(idException::class)
        fun ReadAngle16(): Float {
            return SHORT2ANGLE(ReadShort())
        }

        @Throws(idException::class)
        fun ReadDir(numBits: Int): idVec3 {
            return BitsToDir(ReadBits(numBits), numBits)
        }

        @Throws(idException::class)
        fun ReadString(buffer: CharArray, bufferSize: Int): Int {
            var l: Int
            var c: Int
            ReadByteAlign()
            l = 0
            while (1 != 0) {
                c = ReadByte().toInt()
                if (c <= 0 || c >= 255) {
                    break
                }
                // translate all fmt spec to avoid crash bugs in string routines
                if (c == '%'.code) {
                    c = '.'.code
                }

                // we will read past any excessively long string, so
                // the following data can be read, but the string will
                // be truncated
                if (l < bufferSize - 1) {
                    buffer[l] = c.toChar()
                    l++
                }
            }
            buffer[l] = Char(0)
            return l
        }

        //
        fun ReadData(data: ByteBuffer?, length: Int): Int {
            val cnt: Int
            ReadByteAlign()
            cnt = readCount
            if (readCount + length > curSize) {
                data!!.put(readData!!.array(), readCount, GetRemaingData())
                readCount = curSize
            } else {
                data!!.put(readData!!.array(), readCount, length)
                readCount += length
            }
            return readCount - cnt
        }

        @Throws(idException::class)
        fun ReadNetadr(adr: netadr_t) {
            var i: Int
            adr.type = netadrtype_t.NA_IP
            i = 0
            while (i < 4) {
                adr.ip[i] = ReadByte().toInt().toChar()
                i++
            }
            adr.port = ReadUShort()
        }

        @Throws(idException::class)
        fun ReadDeltaChar(oldValue: Byte): Byte {
            return ReadDelta(oldValue.toInt(), -8).toByte()
        }

        @Throws(idException::class)
        fun ReadDeltaByte(oldValue: Byte): Byte {
            return ReadDelta(oldValue.toInt(), 8).toByte()
        }

        @Throws(idException::class)
        fun ReadDeltaShort(oldValue: Short): Short {
            return ReadDelta(oldValue.toInt(), -16).toShort()
        }

        @Throws(idException::class)
        fun ReadDeltaLong(oldValue: Int): Int {
            return ReadDelta(oldValue, 32)
        }

        @Throws(idException::class)
        fun ReadDeltaFloat(oldValue: Float): Float {
            val value: Float
            value = java.lang.Float.intBitsToFloat(ReadDelta(java.lang.Float.floatToIntBits(oldValue.toFloat()), 32))
                .toFloat()
            return value
        }

        @Throws(idException::class)
        fun ReadDeltaFloat(oldValue: Float, exponentBits: Int, mantissaBits: Int): Float {
            val oldBits = idMath.FloatToBits(oldValue, exponentBits, mantissaBits)
            val newBits = ReadDelta(oldBits, 1 + exponentBits + mantissaBits)
            return idMath.BitsToFloat(newBits, exponentBits, mantissaBits)
        }

        @Throws(idException::class)
        fun ReadDeltaByteCounter(oldValue: Int): Int {
            val i: Int
            val newValue: Int
            i = ReadBits(3)
            if (0 == i) {
                return oldValue
            }
            newValue = ReadBits(i)
            return oldValue and ((1 shl i) - 1).inv() or newValue
        }

        @Throws(idException::class)
        fun ReadDeltaShortCounter(oldValue: Int): Int {
            val i: Int
            val newValue: Int
            i = ReadBits(4)
            if (0 == i) {
                return oldValue
            }
            newValue = ReadBits(i)
            return oldValue and ((1 shl i) - 1).inv() or newValue
        }

        //
        @Throws(idException::class)
        fun ReadDeltaLongCounter(oldValue: Int): Int {
            val i: Int
            val newValue: Int
            i = ReadBits(5)
            if (0 == i) {
                return oldValue
            }
            newValue = ReadBits(i)
            return oldValue and ((1 shl i) - 1).inv() or newValue
        }

        @Throws(idException::class)
        fun ReadDeltaDict(dict: idDict, base: idDict?): Boolean {
            val key = CharArray(MAX_STRING_CHARS)
            val value = CharArray(MAX_STRING_CHARS)
            var changed = false
            if (base != null) {
                dict.set(base)
            } else {
                dict.Clear()
            }
            while (ReadString(key, key.size) != 0) {
                ReadString(value, value.size)
                dict.Set(TempDump.ctos(key), TempDump.ctos(value))
                changed = true
            }
            while (ReadString(key, key.size) != 0) {
                dict.Delete(TempDump.ctos(key))
                changed = true
            }
            return changed
        }

        @Throws(idException::class)
        private fun CheckOverflow(numBits: Int): Boolean {
            assert(numBits >= 0)
            if (numBits > GetRemainingWriteBits()) {
                if (!allowOverflow) {
                    idLib.common.FatalError("idBitMsg: overflow without allowOverflow set")
                }
                if (numBits > maxSize shl 3) {
                    idLib.common.FatalError("idBitMsg: %d bits is > full message size", numBits)
                }
                idLib.common.Printf("idBitMsg: overflow\n")
                BeginWriting()
                overflowed = true
                return true
            }
            return false
        }

        @Throws(idException::class)
        private fun GetByteSpace(length: Int): ByteArray {
            val ptr: ByteArray
            if (writeData == null) {
                idLib.common.FatalError("idBitMsg::GetByteSpace: cannot write to message")
            }

            // round up to the next byte
            WriteByteAlign()

            // check for overflow
            CheckOverflow(length shl 3)
            ptr = ByteArray(writeData!!.capacity() - curSize)
            writeData!!.mark().position(curSize)[ptr].rewind()
            curSize += length
            return ptr
        }

        private fun WriteDelta(oldValue: Int, newValue: Int, numBits: Int) {
            if (oldValue == newValue) {
                WriteBits(0, 1)
                return
            }
            WriteBits(1, 1)
            WriteBits(newValue, numBits)
        }

        @Throws(idException::class)
        private fun ReadDelta(oldValue: Int, numBits: Int): Int {
            return if (ReadBits(1) != 0) {
                ReadBits(numBits)
            } else oldValue
        }

        companion object {
            //public					~idBitMsg() {}
            fun DirToBits(dir: idVec3, numBits: Int): Int {
                var numBits = numBits
                val max: Int
                var bits: Int
                val bias: Float
                assert(numBits >= 6 && numBits <= 32)
                assert(dir.LengthSqr() - 1.0f < 0.01f)
                numBits /= 3
                max = (1 shl numBits - 1) - 1
                bias = 0.5f / max
                bits = FLOATSIGNBITSET(dir.x) shl numBits * 3 - 1
                bits = bits or (idMath.Ftoi((abs(dir.x) + bias) * max) shl numBits * 2)
                bits = bits or (FLOATSIGNBITSET(dir.y) shl numBits * 2 - 1)
                bits = bits or (idMath.Ftoi((abs(dir.y) + bias) * max) shl numBits * 1)
                bits = bits or (FLOATSIGNBITSET(dir.z) shl numBits * 1 - 1)
                bits = bits or (idMath.Ftoi((abs(dir.z) + bias) * max) shl numBits * 0)
                return bits
            }

            fun BitsToDir(bits: Int, numBits: Int): idVec3 {
                var numBits = numBits
                val sign = floatArrayOf(1.0f, -1.0f)
                val max: Int
                val invMax: Float
                val dir = idVec3()
                assert(numBits >= 6 && numBits <= 32)
                numBits /= 3
                max = (1 shl numBits - 1) - 1
                invMax = 1.0f / max
                dir.x = sign[bits shr numBits * 3 - 1 and 1] * (bits shr numBits * 2 and max) * invMax
                dir.y = sign[bits shr numBits * 2 - 1 and 1] * (bits shr numBits * 1 and max) * invMax
                dir.z = sign[bits shr numBits * 1 - 1 and 1] * (bits shr numBits * 0 and max) * invMax
                dir.NormalizeFast()
                return dir
            }
        }
    }

    class idBitMsgDelta     //
    //
    {
        private var base // base
                : idBitMsg? = null
        private var changed // true if the new base is different from the base
                = false
        private var newBase // new base
                : idBitMsg? = null
        private var readDelta // delta from base to new base for reading
                : idBitMsg? = null
        private var writeDelta // delta from base to new base for writing
                : idBitMsg? = null

        //public					~idBitMsgDelta() {}
        //
        fun Init(base: idBitMsg, newBase: idBitMsg, delta: idBitMsg) {
            this.base = base
            this.newBase = newBase
            writeDelta = delta
            readDelta = delta
            changed = false
        }

        fun InitReadOnly(base: idBitMsg, newBase: idBitMsg, delta: idBitMsg) {
            this.base = base
            this.newBase = newBase
            writeDelta = null
            readDelta = delta
            changed = false
        }

        fun HasChanged(): Boolean {
            return changed
        }

        @Throws(idException::class)
        fun WriteBits(value: Int, numBits: Int) {
            if (newBase != null) {
                newBase!!.WriteBits(value, numBits)
            }
            if (null == base) {
                writeDelta!!.WriteBits(value, numBits)
                changed = true
            } else {
                val baseValue = base!!.ReadBits(numBits)
                if (baseValue == value) {
                    writeDelta!!.WriteBits(0, 1)
                } else {
                    writeDelta!!.WriteBits(1, 1)
                    writeDelta!!.WriteBits(value, numBits)
                    changed = true
                }
            }
        }

        @Throws(idException::class)
        fun WriteChar(c: Int) {
            WriteBits(c, -8)
        }

        @Throws(idException::class)
        fun WriteByte(c: Int) {
            WriteBits(c, 8)
        }

        @Throws(idException::class)
        fun WriteShort(c: Int) {
            WriteBits(c, -16)
        }

        @Throws(idException::class)
        fun WriteUShort(c: Int) {
            WriteBits(c, 16)
        }

        @Throws(idException::class)
        fun WriteLong(c: Int) {
            WriteBits(c, 32)
        }

        @Throws(idException::class)
        fun WriteFloat(f: Float) {
            WriteBits(java.lang.Float.floatToIntBits(f.toFloat()), 32)
        }

        @Throws(idException::class)
        fun WriteFloat(f: Float, exponentBits: Int, mantissaBits: Int) {
            val bits = idMath.FloatToBits(f, exponentBits, mantissaBits)
            WriteBits(bits, 1 + exponentBits + mantissaBits)
        }

        @Throws(idException::class)
        fun WriteAngle8(f: Float) {
            WriteBits(ANGLE2BYTE(f).toInt(), 8)
        }

        @Throws(idException::class)
        fun WriteAngle16(f: Float) {
            WriteBits(ANGLE2SHORT(f).toInt(), 16)
        }

        @Throws(idException::class)
        fun WriteDir(dir: idVec3, numBits: Int) {
            WriteBits(idBitMsg.DirToBits(dir, numBits), numBits)
        }

        //public	void			WriteString( final String s, int maxLength = -1 );
        @Throws(idException::class)
        fun WriteString(s: String?, maxLength: Int) {
            if (newBase != null) {
                newBase!!.WriteString(s, maxLength)
            }
            if (null == base) {
                writeDelta!!.WriteString(s, maxLength)
                changed = true
            } else {
                val baseString = CharArray(MAX_DATA_BUFFER)
                base!!.ReadString(baseString, MAX_DATA_BUFFER)
                if (idStr.Cmp(s!!, TempDump.ctos(baseString)) == 0) {
                    writeDelta!!.WriteBits(0, 1)
                } else {
                    writeDelta!!.WriteBits(1, 1)
                    writeDelta!!.WriteString(s, maxLength)
                    changed = true
                }
            }
        }

        @Throws(idException::class)
        fun WriteData(data: ByteBuffer, length: Int) {
            if (newBase != null) {
                newBase!!.WriteData(data, length)
            }
            if (null == base) {
                writeDelta!!.WriteData(data, length)
                changed = true
            } else {
                val baseData = ByteBuffer.allocate(MAX_DATA_BUFFER)
                assert(length < MAX_DATA_BUFFER)
                base!!.ReadData(baseData, length)
                if (data == baseData) { //TODO:compareTo??
                    writeDelta!!.WriteBits(0, 1)
                } else {
                    writeDelta!!.WriteBits(1, 1)
                    writeDelta!!.WriteData(data, length)
                    changed = true
                }
            }
        }

        @Throws(idException::class)
        fun WriteDict(dict: idDict) {
            if (newBase != null) {
                newBase!!.WriteDeltaDict(dict, null)
            }
            changed = if (null == base) {
                writeDelta!!.WriteDeltaDict(dict, null)
                true
            } else {
                val baseDict = idDict()
                base!!.ReadDeltaDict(baseDict, null)
                writeDelta!!.WriteDeltaDict(dict, baseDict)
            }
        }

        //
        @Throws(idException::class)
        fun WriteDeltaChar(oldValue: Int, newValue: Int) {
            WriteDelta(oldValue, newValue, -8)
        }

        @Throws(idException::class)
        fun WriteDeltaByte(oldValue: Int, newValue: Int) {
            WriteDelta(oldValue, newValue, 8)
        }

        @Throws(idException::class)
        fun WriteDeltaShort(oldValue: Int, newValue: Int) {
            WriteDelta(oldValue, newValue, -16)
        }

        @Throws(idException::class)
        fun WriteDeltaLong(oldValue: Int, newValue: Int) {
            WriteDelta(oldValue, newValue, 32)
        }

        @Throws(idException::class)
        fun WriteDeltaFloat(oldValue: Float, newValue: Float) {
            WriteDelta(
                java.lang.Float.floatToIntBits(oldValue.toFloat()),
                java.lang.Float.floatToIntBits(newValue.toFloat()),
                32
            )
        }

        @Throws(idException::class)
        fun WriteDeltaFloat(oldValue: Float, newValue: Float, exponentBits: Int, mantissaBits: Int) {
            val oldBits = idMath.FloatToBits(oldValue, exponentBits, mantissaBits)
            val newBits = idMath.FloatToBits(newValue, exponentBits, mantissaBits)
            WriteDelta(oldBits, newBits, 1 + exponentBits + mantissaBits)
        }

        @Throws(idException::class)
        fun WriteDeltaByteCounter(oldValue: Int, newValue: Int) {
            if (newBase != null) {
                newBase!!.WriteBits(newValue, 8)
            }
            if (null == base) {
                writeDelta!!.WriteDeltaByteCounter(oldValue, newValue)
                changed = true
            } else {
                val baseValue = base!!.ReadBits(8)
                if (baseValue == newValue) {
                    writeDelta!!.WriteBits(0, 1)
                } else {
                    writeDelta!!.WriteBits(1, 1)
                    writeDelta!!.WriteDeltaByteCounter(oldValue, newValue)
                    changed = true
                }
            }
        }

        @Throws(idException::class)
        fun WriteDeltaShortCounter(oldValue: Int, newValue: Int) {
            if (newBase != null) {
                newBase!!.WriteBits(newValue, 16)
            }
            if (null == base) {
                writeDelta!!.WriteDeltaShortCounter(oldValue, newValue)
                changed = true
            } else {
                val baseValue = base!!.ReadBits(16)
                if (baseValue == newValue) {
                    writeDelta!!.WriteBits(0, 1)
                } else {
                    writeDelta!!.WriteBits(1, 1)
                    writeDelta!!.WriteDeltaShortCounter(oldValue, newValue)
                    changed = true
                }
            }
        }

        @Throws(idException::class)
        fun WriteDeltaLongCounter(oldValue: Int, newValue: Int) {
            if (newBase != null) {
                newBase!!.WriteBits(newValue, 32)
            }
            if (null == base) {
                writeDelta!!.WriteDeltaLongCounter(oldValue, newValue)
                changed = true
            } else {
                val baseValue = base!!.ReadBits(32)
                if (baseValue == newValue) {
                    writeDelta!!.WriteBits(0, 1)
                } else {
                    writeDelta!!.WriteBits(1, 1)
                    writeDelta!!.WriteDeltaLongCounter(oldValue, newValue)
                    changed = true
                }
            }
        }

        //
        @Throws(idException::class)
        fun ReadBits(numBits: Int): Int {
            val value: Int
            if (null == base) {
                value = readDelta!!.ReadBits(numBits)
                changed = true
            } else {
                val baseValue = base!!.ReadBits(numBits)
                if (null == readDelta || readDelta!!.ReadBits(1) == 0) {
                    value = baseValue
                } else {
                    value = readDelta!!.ReadBits(numBits)
                    changed = true
                }
            }
            if (newBase != null) {
                newBase!!.WriteBits(value, numBits)
            }
            return value
        }

        @Throws(idException::class)
        fun ReadChar(): Int {
            return ReadBits(-8)
        }

        @Throws(idException::class)
        fun ReadByte(): Int {
            return ReadBits(8)
        }

        @Throws(idException::class)
        fun ReadShort(): Int {
            return ReadBits(-16)
        }

        @Throws(idException::class)
        fun ReadUShort(): Int {
            return ReadBits(16)
        }

        @Throws(idException::class)
        fun ReadLong(): Int {
            return ReadBits(32)
        }

        @Throws(idException::class)
        fun ReadFloat(): Float {
            val value: Float
            value = java.lang.Float.intBitsToFloat(ReadBits(32)).toFloat()
            return value
        }

        @Throws(idException::class)
        fun ReadFloat(exponentBits: Int, mantissaBits: Int): Float {
            val bits = ReadBits(1 + exponentBits + mantissaBits)
            return idMath.BitsToFloat(bits, exponentBits, mantissaBits)
        }

        @Throws(idException::class)
        fun ReadAngle8(): Float {
            return BYTE2ANGLE(ReadByte().toByte())
        }

        @Throws(idException::class)
        fun ReadAngle16(): Float {
            return SHORT2ANGLE(ReadShort().toShort())
        }

        @Throws(idException::class)
        fun ReadDir(numBits: Int): idVec3 {
            return idBitMsg.BitsToDir(ReadBits(numBits), numBits)
        }

        @Throws(idException::class)
        fun ReadString(buffer: CharArray, bufferSize: Int) {
            if (null == base) {
                readDelta!!.ReadString(buffer, bufferSize)
                changed = true
            } else {
                val baseString = CharArray(MAX_DATA_BUFFER)
                base!!.ReadString(baseString, MAX_DATA_BUFFER)
                if (null == readDelta || readDelta!!.ReadBits(1) == 0) {
                    idStr.Copynz(buffer, TempDump.ctos(baseString), bufferSize)
                } else {
                    readDelta!!.ReadString(buffer, bufferSize)
                    changed = true
                }
            }
            if (newBase != null) {
                newBase!!.WriteString(TempDump.ctos(buffer))
            }
        }

        @Throws(idException::class)
        fun ReadData(data: ByteBuffer, length: Int) {
            if (null == base) {
                readDelta!!.ReadData(data, length)
                changed = true
            } else {
                val baseData = ByteBuffer.allocate(MAX_DATA_BUFFER)
                assert(length < MAX_DATA_BUFFER)
                base!!.ReadData(baseData, length)
                if (null == readDelta || readDelta!!.ReadBits(1) == 0) {
//			memcpy( data, baseData, length );
                    data.put(data) //.array(), 0, length);
                } else {
                    readDelta!!.ReadData(data, length)
                    changed = true
                }
            }
            if (newBase != null) {
                newBase!!.WriteData(data, length)
            }
        }

        @Throws(idException::class)
        fun ReadDict(dict: idDict) {
            if (null == base) {
                readDelta!!.ReadDeltaDict(dict, null)
                changed = true
            } else {
                val baseDict = idDict()
                base!!.ReadDeltaDict(baseDict, null)
                if (null == readDelta) {
                    dict.set(baseDict)
                } else {
                    changed = readDelta!!.ReadDeltaDict(dict, baseDict)
                }
            }
            if (newBase != null) {
                newBase!!.WriteDeltaDict(dict, null)
            }
        }

        //
        @Throws(idException::class)
        fun ReadDeltaChar(oldValue: Int): Int {
            return ReadDelta(oldValue, -8)
        }

        @Throws(idException::class)
        fun ReadDeltaByte(oldValue: Int): Int {
            return ReadDelta(oldValue, 8)
        }

        @Throws(idException::class)
        fun ReadDeltaShort(oldValue: Int): Int {
            return ReadDelta(oldValue, -16)
        }

        @Throws(idException::class)
        fun ReadDeltaLong(oldValue: Int): Int {
            return ReadDelta(oldValue, 32)
        }

        @Throws(idException::class)
        fun ReadDeltaFloat(oldValue: Float): Float {
            val value: Float
            value = java.lang.Float.intBitsToFloat(ReadDelta(java.lang.Float.floatToIntBits(oldValue.toFloat()), 32))
                .toFloat()
            return value
        }

        @Throws(idException::class)
        fun ReadDeltaFloat(oldValue: Float, exponentBits: Int, mantissaBits: Int): Float {
            val oldBits = idMath.FloatToBits(oldValue, exponentBits, mantissaBits)
            val newBits = ReadDelta(oldBits, 1 + exponentBits + mantissaBits)
            return idMath.BitsToFloat(newBits, exponentBits, mantissaBits)
        }

        @Throws(idException::class)
        fun ReadDeltaByteCounter(oldValue: Int): Int {
            val value: Int
            if (null == base) {
                value = readDelta!!.ReadDeltaByteCounter(oldValue)
                changed = true
            } else {
                val baseValue = base!!.ReadBits(8)
                if (null == readDelta || readDelta!!.ReadBits(1) == 0) {
                    value = baseValue
                } else {
                    value = readDelta!!.ReadDeltaByteCounter(oldValue)
                    changed = true
                }
            }
            if (newBase != null) {
                newBase!!.WriteBits(value, 8)
            }
            return value
        }

        @Throws(idException::class)
        fun ReadDeltaShortCounter(oldValue: Int): Int {
            val value: Int
            if (null == base) {
                value = readDelta!!.ReadDeltaShortCounter(oldValue)
                changed = true
            } else {
                val baseValue = base!!.ReadBits(16)
                if (null == readDelta || readDelta!!.ReadBits(1) == 0) {
                    value = baseValue
                } else {
                    value = readDelta!!.ReadDeltaShortCounter(oldValue)
                    changed = true
                }
            }
            if (newBase != null) {
                newBase!!.WriteBits(value, 16)
            }
            return value
        }

        @Throws(idException::class)
        fun ReadDeltaLongCounter(oldValue: Int): Int {
            val value: Int
            if (null == base) {
                value = readDelta!!.ReadDeltaLongCounter(oldValue)
                changed = true
            } else {
                val baseValue = base!!.ReadBits(32)
                if (null == readDelta || readDelta!!.ReadBits(1) == 0) {
                    value = baseValue
                } else {
                    value = readDelta!!.ReadDeltaLongCounter(oldValue)
                    changed = true
                }
            }
            if (newBase != null) {
                newBase!!.WriteBits(value, 32)
            }
            return value
        }

        @Throws(idException::class)
        private fun WriteDelta(oldValue: Int, newValue: Int, numBits: Int) {
            if (newBase != null) {
                newBase!!.WriteBits(newValue, numBits)
            }
            if (null == base) {
                if (oldValue == newValue) {
                    writeDelta!!.WriteBits(0, 1)
                } else {
                    writeDelta!!.WriteBits(1, 1)
                    writeDelta!!.WriteBits(newValue, numBits)
                }
                changed = true
            } else {
                val baseValue = base!!.ReadBits(numBits)
                if (baseValue == newValue) {
                    writeDelta!!.WriteBits(0, 1)
                } else {
                    writeDelta!!.WriteBits(1, 1)
                    changed = if (oldValue == newValue) {
                        writeDelta!!.WriteBits(0, 1)
                        true
                    } else {
                        writeDelta!!.WriteBits(1, 1)
                        writeDelta!!.WriteBits(newValue, numBits)
                        true
                    }
                }
            }
        }

        @Throws(idException::class)
        private fun ReadDelta(oldValue: Int, numBits: Int): Int {
            val value: Int
            if (null == base) {
                value = if (readDelta!!.ReadBits(1) == 0) {
                    oldValue
                } else {
                    readDelta!!.ReadBits(numBits)
                }
                changed = true
            } else {
                val baseValue = base!!.ReadBits(numBits)
                if (null == readDelta || readDelta!!.ReadBits(1) == 0) {
                    value = baseValue
                } else if (readDelta!!.ReadBits(1) == 0) {
                    value = oldValue
                    changed = true
                } else {
                    value = readDelta!!.ReadBits(numBits)
                    changed = true
                }
            }
            if (newBase != null) {
                newBase!!.WriteBits(value, numBits)
            }
            return value
        }
    }
}
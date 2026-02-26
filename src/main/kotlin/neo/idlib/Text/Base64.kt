package neo.idlib.Text

import neo.framework.File_h.idFile
import neo.idlib.IntForSixtets
import neo.idlib.SixtetsForInt
import neo.idlib.Text.Str.idStr
import java.nio.ByteBuffer

class Base64 {
    /*
     ===============================================================================

     base64

     ===============================================================================
     */
    class idBase64 {
        private var alloced = 0

        //
        private var data: ByteArray = ByteArray(0)
        private var len = 0

        constructor() {
            Init()
        }

        constructor(s: idStr) {
            Init()
            data = s.data.toByteArray()
            len = s.len
            alloced = s.alloced
        }

        fun Encode(from: ByteArray, size: Int) {
            val from2 = IntArray(size)
            System.arraycopy(from, 0, from2, 0, size)
            Encode(from2, size)
        }

        fun Encode(from: ByteBuffer, size: Int) {
            Encode(from.array(), size)
        }

        fun Encode(from: CharArray, size: Int) {
            val from2 = IntArray(size)
            System.arraycopy(from, 0, from2, 0, size)
            Encode(from2, size)
        }

        fun Encode(from: IntArray, size: Int) {
            var size = size
            var i: Int
            var j: Int
            var w: Int
            val to: ByteArray
            var f_ptr = 0
            var t_ptr = 0
            EnsureAlloced(4 * (size + 3) / 3 + 2) // ratio and padding + trailing \0
            to = data
            w = 0
            i = 0
            while (size > 0) {
                w = w or (from[f_ptr] shl i * 8)
                ++f_ptr
                --size
                ++i
                if (size == 0 || i == 3) {
                    val out = ByteArray(4)
                    SixtetsForInt(out, w)
                    j = 0
                    while (j * 6 < i * 8) {
                        to[t_ptr++] = sixtet_to_base64[out[j].toInt()].code.toByte()
                        ++j
                    }
                    if (size == 0) {
                        j = i
                        while (j < 3) {
                            to[t_ptr++] = '='.code.toByte()
                            ++j
                        }
                    }
                    w = 0
                    i = 0
                }
            }
            to[t_ptr++] = '\u0000'.code.toByte()
            len = t_ptr
        }

        fun Encode(src: idStr) {
            Encode(src.toString().toByteArray(), src.Length())
        }

        /*
         ============
         idBase64::DecodeLength
         returns the minimum size in bytes of the target buffer for decoding
         4 base64 digits <-> 3 bytes
         ============
         */
        fun DecodeLength(): Int { // minimum size in bytes of destination buffer for decoding
            return 3 * len / 4
        }

        fun Decode(to: ByteArray): Int { // does not append a \0 - needs a DecodeLength() bytes buffer
            var w: Long
            var i: Int
            var j: Int
            var n: Int
            val base64_to_sixtet = CharArray(256)
            var tab_init = false //TODO:useless, remove
            val from = data
            var f_ptr = 0
            var t_ptr = 0
            if (!tab_init) {
//                memset(base64_to_sixtet, 0, 256);
                i = 0
                while (sixtet_to_base64[i].also { j = it.code } != '\u0000') {
                    base64_to_sixtet[j] = i.toChar()
                    ++i
                }
                tab_init = true
            }
            w = 0
            i = 0
            n = 0
            val `in` = byteArrayOf(0, 0, 0, 0)
            while (from[f_ptr] != '\u0000'.code.toByte() && from[f_ptr] != '='.code.toByte()) {
                if (from[f_ptr] == ' '.code.toByte() || from[f_ptr] == '\n'.code.toByte()) {
                    ++f_ptr
                    continue
                }
                `in`[i] = base64_to_sixtet[from[f_ptr].toInt()].code.toByte()
                ++i
                ++f_ptr
                if (from[f_ptr] == '\u0000'.code.toByte() || from[f_ptr] == '='.code.toByte() || i == 4) {
                    w = IntForSixtets(`in`).toLong()
                    j = 0
                    while (j * 8 < i * 6) {
                        to[t_ptr++] = (w and 0xff).toByte()
                        ++n
                        w = w shr 8
                        ++j
                    }
                    i = 0
                    w = 0
                }
            }
            return n
        }

        //
        fun Decode(dest: Array<idStr>) { // decodes the binary content to an idStr (a bit dodgy, \0 and other non-ascii are possible in the decoded content)
            val buf = ByteArray(DecodeLength() + 1) // +1 for trailing \0
            Decode(buf)
            //            buf[out] = '\0';
            dest[0] = idStr(String(buf))
            //	delete[] buf;
        }

        fun Decode(dest: idFile) {
            val buf = ByteBuffer.allocate(DecodeLength() + 1) // +1 for trailing \0
            val out = Decode(buf.array())
            dest.Write(buf, out)
            //	delete[] buf;
        }

        //
        fun c_str(): CharArray {
            return String(data).toCharArray()
        }

        fun oSet(s: idStr) {
            EnsureAlloced(s.Length() + 1) // trailing \0 - beware, this does a Release
            //	strcpy( (char *)data, s.c_str() );
            data = s.data.toByteArray()
            len = s.Length()
        }

        //
        private fun Init() {
            len = 0
            alloced = 0
            data = ByteArray(0)
        }

        private fun Release() {
//	if ( data ) {
//		delete[] data;
//	}
            Init()
        }

        private fun EnsureAlloced(size: Int) {
            if (size > alloced) {
                Release()
            }
            data = ByteArray(size)
            alloced = size
        }

        companion object {
            //public				~idBase64( void );
            //
            val sixtet_to_base64: CharArray =
                "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray()
        }
    }
}
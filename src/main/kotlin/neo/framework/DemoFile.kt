package neo.framework

import neo.framework.CVarSystem.idCVar
import neo.framework.Compressor.idCompressor
import neo.framework.File_h.idFile
import neo.framework.File_h.idFile_Memory
import neo.idlib.Dict_h.idDict
import neo.idlib.Text.Str
import neo.idlib.Text.Str.idStr
import neo.idlib.containers.CInt
import neo.idlib.containers.List.idList
import neo.idlib.idException
import java.nio.ByteBuffer

object DemoFile {
    val DEMO_MAGIC: String = Licensee.GAME_NAME + " RDEMO"

    /*
     ===============================================================================

     Demo file

     ===============================================================================
     */
    enum class demoSystem_t {
        DS_FINISHED, DS_RENDER, DS_SOUND, DS_VERSION
    }

    class idDemoFile : idFile() {
        private var compressor: idCompressor? = null
        private val demoStrings: idList<idStr>
        private var f: idFile? = null
        private var fLog: idFile? = null
        private var fileImage: ByteBuffer? = null
        private var log = false
        private val logStr: idStr = idStr()
        private var writing = false
        override fun GetName(): String {
            return if (f != null) f!!.GetName() else ""
        }

        override fun GetFullPath(): String {
            return if (f != null) f!!.GetFullPath() else ""
        }

        fun SetLog(b: Boolean, p: String?) {
            log = b
            if (p != null) {
                logStr.set(p)
            }
        }

        fun Log(p: String?) {
            if (fLog != null && p != null && p.isNotEmpty()) {
                fLog!!.WriteString(p /*, strlen(p)*/)
            }
        }

        fun OpenForReading(fileName: String): Boolean {
            val magicBuffer = ByteBuffer.allocate(magicLen)
            val compression = CInt()
            val fileLength: Int
            Close()
            f = FileSystem_h.fileSystem.OpenFileRead(fileName)
            if (null == f) {
                return false
            }
            fileLength = f!!.Length()
            if (com_preloadDemos.GetBool()) {
                fileImage = ByteBuffer.allocate(fileLength) // Mem_Alloc(fileLength);
                f!!.Read(fileImage!!, fileLength)
                FileSystem_h.fileSystem.CloseFile(f!!)
                f = idFile_Memory(
                    Str.va("preloaded(%s)", fileName),
                    fileImage!!,
                    fileLength
                ) //TODO:should fileImage be a reference??
            }
            if (com_logDemos.GetBool()) {
                fLog = FileSystem_h.fileSystem.OpenFileWrite("demoread.log")
            }
            writing = false
            f!!.Read(magicBuffer) //, magicLen);
            if (DEMO_MAGIC == String(magicBuffer.array()).substring(0, magicLen)) {
//	if ( memcmp(magicBuffer, DEMO_MAGIC, magicLen) == 0 ) {
                f!!.ReadInt(compression)
            } else {
                // Ideally we would error out if the magic string isn't there,
                // but for backwards compatibility we are going to assume it's just an uncompressed demo file
                compression._val = 0
                f!!.Rewind()
            }
            compressor = AllocCompressor(compression._val)
            compressor!!.Init(f!!, false, 8)
            return true
        }

        fun OpenForWriting(fileName: String): Boolean {
            Close()
            f = FileSystem_h.fileSystem.OpenFileWrite(fileName)
            if (f == null) {
                return false
            }
            if (com_logDemos.GetBool()) {
                fLog = FileSystem_h.fileSystem.OpenFileWrite("demowrite.log")
            }
            writing = true
            f!!.WriteString(DEMO_MAGIC /*, sizeof(DEMO_MAGIC)*/)
            f!!.WriteInt(com_compressDemos.GetInteger())
            f!!.Flush()
            compressor = AllocCompressor(com_compressDemos.GetInteger())
            compressor!!.Init(f!!, true, 8)
            return true
        }

        fun Close() {
            if (writing && compressor != null) {
                compressor!!.FinishCompress()
            }
            if (f != null) {
                FileSystem_h.fileSystem.CloseFile(f!!)
                f = null
            }
            if (fLog != null) {
                FileSystem_h.fileSystem.CloseFile(fLog!!)
                fLog = null
            }
            if (fileImage != null) {
//                Mem_Free(fileImage);
                fileImage = null
            }
            if (compressor != null) {
//		delete compressor;
                compressor = null
            }
            demoStrings.DeleteContents(true)
        }

        @Throws(idException::class)
        fun ReadHashString(): String {
            val index = CInt()
            if (log && fLog != null) {
                val text = Str.va("%s > Reading hash string\n", logStr.toString())
                fLog!!.WriteString(text)
            }
            ReadInt(index)
            if (index._val == -1) {
                // read a new string for the table
                val str: idStr?
                val data = idStr()
                ReadString(data)
                str = data
                demoStrings.Append(str)
                return str.toString()
            }
            if (index._val < -1 || index._val >= demoStrings.Num()) {
                Close()
                Common.common.Error("demo hash index out of range")
            }
            return demoStrings[index._val].toString() //TODO:return c_str?
        }

        fun WriteHashString(str: String) {
            if (log && fLog != null) {
                val text = Str.va("%s > Writing hash string\n", logStr.toString())
                fLog!!.WriteString(text)
            }
            // see if it is already in the has table
            for (i in 0 until demoStrings.Num()) {
                if (demoStrings[i].toString() == str) {
                    WriteInt(i)
                    return
                }
            }

            // add it to our table and the demo table
            val copy = idStr(str)
            //common.Printf( "hash:%i = %s\n", demoStrings.Num(), str );
            demoStrings.Append(copy)
            val cmd = -1
            WriteInt(cmd)
            WriteString(str)
        }

        @Throws(idException::class)
        fun ReadDict(dict: idDict) {
            var i: Int
            val c = CInt()
            var key: String?
            var `val`: String?
            dict.Clear()
            ReadInt(c)
            i = 0
            while (i < c._val) {
                key = ReadHashString()
                `val` = ReadHashString()
                dict.Set(key, `val`)
                i++
            }
        }

        fun WriteDict(dict: idDict) {
            var i: Int
            val c: Int
            c = dict.GetNumKeyVals()
            WriteInt(c)
            i = 0
            while (i < c) {
                WriteHashString(dict.GetKeyVal(i)!!.GetKey().toString())
                WriteHashString(dict.GetKeyVal(i)!!.GetValue().toString())
                i++
            }
        }

        override fun Read(buffer: ByteBuffer, len: Int): Int {
            val read = compressor!!.Read(buffer, len)
            if (read == 0 && len >= 4) {
//                *(demoSystem_t *)buffer = DS_FINISHED;
                buffer.putInt(demoSystem_t.DS_FINISHED.ordinal)
            }
            return read
        }

        override fun Write(buffer: ByteBuffer, len: Int): Int {
            return compressor!!.Write(buffer, len)
        }

        companion object {
            val magicLen = DEMO_MAGIC.length
            private val com_compressDemos: idCVar = idCVar(
                "com_compressDemos",
                "1",
                CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_INTEGER or CVarSystem.CVAR_ARCHIVE,
                """
     Compression scheme for demo files
     0: None    (Fast, large files)
     1: LZW     (Fast to compress, Fast to decompress, medium/small files)
     2: LZSS    (Slow to compress, Fast to decompress, small files)
     3: Huffman (Fast to compress, Slow to decompress, medium files)
     See also: The 'CompressDemo' command
     """.trimIndent()
            )

            //
            private val com_logDemos: idCVar = idCVar(
                "com_logDemos",
                "0",
                CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_BOOL,
                "Write demo.log with debug information in it"
            )
            private val com_preloadDemos: idCVar = idCVar(
                "com_preloadDemos",
                "0",
                CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_BOOL or CVarSystem.CVAR_ARCHIVE,
                "Load the whole demo in to RAM before running it"
            )

            private fun AllocCompressor(type: Int): idCompressor {
                return when (type) {
                    0 -> idCompressor.AllocNoCompression()
                    1 -> idCompressor.AllocLZW()
                    2 -> idCompressor.AllocLZSS()
                    3 -> idCompressor.AllocHuffman()
                    else -> idCompressor.AllocLZW()
                }
            }
        }

        //					~idDemoFile();
        init {
            demoStrings = idList()
        }
    }
}
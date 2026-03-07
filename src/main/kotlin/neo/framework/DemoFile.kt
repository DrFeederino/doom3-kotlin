/*
===========================================================================

Doom 3 GPL Source Code
Copyright (C) 1999-2011 id Software LLC, a ZeniMax Media company.
Translated to Kotlin by Dr. Feederino with support of Claude Code

This file is part of the Doom 3 GPL Source Code ("Doom 3 Source Code").
Original source: neo/framework/DemoFile.h, neo/framework/DemoFile.cpp

Doom 3 Source Code is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

Doom 3 Source Code is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with Doom 3 Source Code.  If not, see <http://www.gnu.org/licenses/>.

In addition, the Doom 3 Source Code is also subject to certain additional terms.
You should have received a copy of these additional terms immediately following
the terms and conditions of the GNU General Public License which accompanied
the Doom 3 Source Code.  If not, please request a copy in writing from
id Software at the address below.

If you have questions concerning this license or the applicable additional terms,
you may contact in writing id Software LLC, c/o ZeniMax Media Inc., Suite 120,
Rockville, Maryland 20850 USA.

===========================================================================
*/
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
        private val demoStrings: idList<idStr> = idList()
        private var f: idFile? = null
        private var fLog: idFile? = null
        private var fileImage: ByteBuffer? = null
        private var log = false
        private val logStr: idStr = idStr()
        private var writing = false

        /*
         ================
         idDemoFile::GetName
         ================
         */
        override fun GetName(): String {
            return if (f != null) f!!.GetName() else ""
        }

        /*
         ================
         idDemoFile::GetFullPath
         ================
         */
        override fun GetFullPath(): String {
            return if (f != null) f!!.GetFullPath() else ""
        }

        /*
         ================
         idDemoFile::SetLog
         ================
         */
        fun SetLog(b: Boolean, p: String?) {
            log = b
            if (p != null) {
                logStr.set(p)
            }
        }

        /*
         ================
         idDemoFile::Log
         ================
         */
        fun Log(p: String?) {
            if (fLog != null && p != null && p.isNotEmpty()) {
                // FIX: C++ uses fLog->Write(p, strlen(p)), raw write without length prefix
                val bytes = p.toByteArray()
                fLog!!.Write(ByteBuffer.wrap(bytes), bytes.size)
            }
        }

        /*
         ================
         idDemoFile::OpenForReading
         ================
         */
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
                fileImage = ByteBuffer.allocate(fileLength)
                f!!.Read(fileImage!!, fileLength)
                FileSystem_h.fileSystem.CloseFile(f!!)
                f = idFile_Memory(
                    Str.va("preloaded(%s)", fileName),
                    fileImage!!,
                    fileLength
                )
            }

            if (com_logDemos.GetBool()) {
                fLog = FileSystem_h.fileSystem.OpenFileWrite("demoread.log")
            }

            writing = false

            // FIX: Read magicLen bytes (C++ sizeof(DEMO_MAGIC) includes null terminator)
            f!!.Read(magicBuffer, magicLen)

            // FIX: Compare bytes directly like C++ memcmp, including null terminator
            val expectedMagic = ByteArray(magicLen)
            DEMO_MAGIC.toByteArray().copyInto(expectedMagic)
            // expectedMagic[DEMO_MAGIC.length] is already 0 (null terminator, matching C++)
            if (magicBuffer.array().contentEquals(expectedMagic)) {
                f!!.ReadInt(compression)
            } else {
                // Ideally we would error out if the magic string isn't there,
                // but for backwards compatibility we are going to assume it's just an uncompressed demo file
                compression.integerValue = 0
                f!!.Rewind()
            }

            compressor = AllocCompressor(compression.integerValue)
            compressor!!.Init(f!!, false, 8)

            return true
        }

        /*
         ================
         idDemoFile::OpenForWriting
         ================
         */
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

            // FIX: C++ uses f->Write(DEMO_MAGIC, sizeof(DEMO_MAGIC)) which is a raw write
            // including the null terminator. The original Kotlin used WriteString which adds
            // a 4-byte length prefix - completely wrong format.
            val magicBytes = ByteArray(magicLen)
            DEMO_MAGIC.toByteArray().copyInto(magicBytes)
            // magicBytes[DEMO_MAGIC.length] is already 0 (null terminator, matching C++)
            f!!.Write(ByteBuffer.wrap(magicBytes), magicLen)

            f!!.WriteInt(com_compressDemos.GetInteger())
            f!!.Flush()

            compressor = AllocCompressor(com_compressDemos.GetInteger())
            compressor!!.Init(f!!, true, 8)

            return true
        }

        /*
         ================
         idDemoFile::Close
         ================
         */
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
                fileImage = null
            }
            if (compressor != null) {
                compressor = null
            }

            demoStrings.DeleteContents(true)
        }

        /*
         ================
         idDemoFile::ReadHashString
         ================
         */
        @Throws(idException::class)
        fun ReadHashString(): String {
            val index = CInt()

            if (log && fLog != null) {
                val text = Str.va("%s > Reading hash string\n", logStr.toString())
                // FIX: C++ uses fLog->Write(text, strlen(text)), raw write without length prefix
                val bytes = text.toByteArray()
                fLog!!.Write(ByteBuffer.wrap(bytes), bytes.size)
            }

            ReadInt(index)

            if (index.integerValue == -1) {
                // read a new string for the table
                val data = idStr()
                ReadString(data)
                demoStrings.Append(data)
                return data.toString()
            }

            if (index.integerValue < -1 || index.integerValue >= demoStrings.Num()) {
                Close()
                Common.common.Error("demo hash index out of range")
            }

            return demoStrings[index.integerValue].toString()
        }

        /*
         ================
         idDemoFile::WriteHashString
         ================
         */
        fun WriteHashString(str: String) {
            if (log && fLog != null) {
                val text = Str.va("%s > Writing hash string\n", logStr.toString())
                // FIX: C++ uses fLog->Write(text, strlen(text)), raw write without length prefix
                val bytes = text.toByteArray()
                fLog!!.Write(ByteBuffer.wrap(bytes), bytes.size)
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

        /*
         ================
         idDemoFile::ReadDict
         ================
         */
        @Throws(idException::class)
        fun ReadDict(dict: idDict) {
            val c = CInt()

            dict.Clear()
            ReadInt(c)
            for (i in 0 until c.integerValue) {
                val key = ReadHashString()
                val `val` = ReadHashString()
                dict.Set(key, `val`)
            }
        }

        /*
         ================
         idDemoFile::WriteDict
         ================
         */
        fun WriteDict(dict: idDict) {
            val c: Int = dict.GetNumKeyVals()
            WriteInt(c)
            for (i in 0 until c) {
                WriteHashString(dict.GetKeyVal(i)!!.GetKey().toString())
                WriteHashString(dict.GetKeyVal(i)!!.GetValue().toString())
            }
        }

        /*
         ================
         idDemoFile::Read
         ================
         */
        override fun Read(buffer: ByteBuffer): Int {
            return Read(buffer, buffer.capacity())
        }

        override fun Read(buffer: ByteBuffer, len: Int): Int {
            val read = compressor!!.Read(buffer, len)
            if (read == 0 && len >= 4) {
                // FIX: C++ writes at buffer start: *(demoSystem_t *)buffer = DS_FINISHED
                // Use absolute putInt(index, value) to write at position 0 regardless of
                // the current buffer position after compressor.Read.
                buffer.putInt(0, demoSystem_t.DS_FINISHED.ordinal)
            }
            return read
        }

        /*
         ================
         idDemoFile::Write
         ================
         */
        // FIX: Must override Write(ByteBuffer) to delegate to Write(ByteBuffer, Int).
        // The base idFile.Write(ByteBuffer) fatal errors instead of delegating (unlike
        // Read(ByteBuffer) which correctly delegates). Without this override, all calls
        // through WriteInt/WriteString on idDemoFile would crash.
        override fun Write(buffer: ByteBuffer): Int {
            return Write(buffer, buffer.capacity())
        }

        override fun Write(buffer: ByteBuffer, len: Int): Int {
            return compressor!!.Write(buffer, len)
        }

        companion object {
            // C++ sizeof(DEMO_MAGIC) includes the null terminator byte
            val magicLen = DEMO_MAGIC.length + 1

            private val com_compressDemos: idCVar = idCVar(
                "com_compressDemos",
                "1",
                CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_INTEGER or CVarSystem.CVAR_ARCHIVE,
                "Compression scheme for demo files\n" +
                        "0: None    (Fast, large files)\n" +
                        "1: LZW     (Fast to compress, Fast to decompress, medium/small files)\n" +
                        "2: LZSS    (Slow to compress, Fast to decompress, small files)\n" +
                        "3: Huffman (Fast to compress, Slow to decompress, medium files)\n" +
                        "See also: The 'CompressDemo' command"
            )

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

            /*
             ================
             idDemoFile::AllocCompressor
             ================
             */
            private fun AllocCompressor(type: Int): idCompressor {
                return when (type) {
                    0 -> idCompressor.AllocNoCompression()
                    2 -> idCompressor.AllocLZSS()
                    3 -> idCompressor.AllocHuffman()
                    else -> idCompressor.AllocLZW()
                }
            }
        }
    }
}

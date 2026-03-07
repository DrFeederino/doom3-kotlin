package neo.Sound

import neo.Sound.snd_local.*
import neo.Sound.snd_system.idSoundSystemLocal
import neo.TempDump
import neo.framework.Common
import neo.framework.FileSystem_h
import neo.framework.File_h.fsOrigin_t
import neo.framework.File_h.idFile
import neo.idlib.LittleLong
import neo.idlib.LittleRevBytes
import neo.idlib.LittleShort
import neo.idlib.Text.Str.idStr
import neo.sys.sys_public
import neo.sys.win_main
import org.lwjgl.BufferUtils
import org.lwjgl.stb.STBVorbis
import org.lwjgl.stb.STBVorbisInfo
import java.nio.ByteBuffer

object snd_wavefile {
    val fourcc_riff = mmioFOURCC('R'.code, 'I'.code, 'F'.code, 'F'.code)
    fun mmioFOURCC(ch0: Int, ch1: Int, ch2: Int, ch3: Int): Long {
        return (ch0 or (ch1 shl 8)
                or (ch2 shl 16)
                or (ch3 shl 24)).toLong()
    }

    /*
     ===================================================================================

     idWaveFile

     ===================================================================================
     */
    class idWaveFile {
        var mpwfx // Pointer to waveformatex structure
                : waveformatextensible_s = waveformatextensible_s()
        private var isOgg: Boolean
        private var mMemSize // size of the wave data in memory
                : Long = 0

        //
        private var mbIsReadingFromMemory: Boolean
        private val mck // Multimedia RIFF chunk
                : mminfo_s
        private val mckRiff // used when opening a WAVE file
                : mminfo_s
        private var   /*dword*/mdwSize // size in samples
                : Long
        private var   /*ID_TIME_T*/mfileTime: Long = 0

        //
        private var mhmmio // I/O handle for the WAVE
                : idFile?
        private var mpbData: ByteBuffer?
        private var mpbDataCur: ByteBuffer? = null
        private var   /*dword*/mseekBase: Long
        private var   /*dword*/mulDataSize: Long = 0

        //
        // FIX: Changed ogg from Any? to Long — stores stb_vorbis handle (0 = null) for non-realtime OGG decoding
        private var ogg: Long = 0 // stb_vorbis handle, only != 0 when !s_realTimeDecoding

        // FIX: Added oggData to keep the raw OGG file data alive for stb_vorbis (memory-based decoding)
        private var oggData: ByteBuffer? = null

        // ~idWaveFile();
        //-----------------------------------------------------------------------------
        // Name: idWaveFile::Open()
        // Desc: Opens a wave file for reading
        //-----------------------------------------------------------------------------
        fun Open(strFileName: String?, pwfx: Array<waveformatex_s> /*= NULL*/): Int {
            mbIsReadingFromMemory = false
            mpbData = null
            mpbDataCur = mpbData
            if (strFileName == null) {
                return -1
            }
            val name = idStr(strFileName)

            // note: used to only check for .wav when making a build
            name.SetFileExtension(".ogg")
            if (FileSystem_h.fileSystem.ReadFile(name.toString(), null, null) != -1) {
                return OpenOGG(name.toString(), pwfx)
            }

            mpwfx = waveformatextensible_s()
            mhmmio = FileSystem_h.fileSystem.OpenFileRead(strFileName)
            if (null == mhmmio) {
                mdwSize = 0
                return -1
            }
            if (mhmmio!!.Length() <= 0) {
                mhmmio = null
                return -1
            }
            if (ReadMMIO() != 0) {
                // ReadMMIO will fail if its an not a wave file
                Close()
                return -1
            }
            mfileTime = mhmmio!!.Timestamp()
            if (ResetFile() != 0) {
                Close()
                return -1
            }

            // After the reset, the size of the wav file is mck.cksize so store it now
            mdwSize = (mck.cksize / java.lang.Short.BYTES).toLong()
            mMemSize = mck.cksize.toLong()
            if (mck.cksize != -0x1) {
                pwfx[0] = mpwfx.Format
                return 0
            }
            return -1
        }

        //-----------------------------------------------------------------------------
        // Name: idWaveFile::OpenFromMemory()
        // Desc: copy data to idWaveFile member variable from memory
        //-----------------------------------------------------------------------------
        fun OpenFromMemory(pbData: ShortArray, ulDataSize: Int, pwfx: waveformatextensible_s): Int {
            mpwfx = pwfx
            mulDataSize = ulDataSize.toLong()
            mpbData = TempDump.stobb(pbData)
            mpbDataCur = mpbData!!.duplicate()
            mdwSize = (ulDataSize / 2).toLong()
            mMemSize = ulDataSize.toLong()
            mbIsReadingFromMemory = true
            return 0
        }

        //-----------------------------------------------------------------------------
        // Name: idWaveFile::Read()
        // Desc: Reads section of data from a wave file into pBuffer and returns 
        //       how much read in pdwSizeRead, reading not more than dwSizeToRead.
        //       This uses mck to determine where to start reading from.  So 
        //       subsequent calls will be continue where the last left off unless 
        //       Reset() is called.
        //-----------------------------------------------------------------------------
        fun Read(pBuffer: ByteBuffer, dwSizeToRead: Int, pdwSizeRead: IntArray?): Int {
            var dwSizeToRead = dwSizeToRead
            return if (ogg != 0L) {
                ReadOGG(pBuffer.array(), dwSizeToRead, pdwSizeRead)
            } else if (mbIsReadingFromMemory) {
                if (mpbDataCur == null) {
                    return -1
                }
                if (mpbDataCur!!.position() + dwSizeToRead > mulDataSize.toInt()) {
                    dwSizeToRead = (mulDataSize - mpbDataCur!!.position()).toInt()
                }
                // FIX: SIMDProcessor.Memcpy(ByteBuffer, ByteBuffer, Int) resolves to no-op catch-all.
                // Use direct ByteBuffer copy instead.
                val src = mpbDataCur!!.duplicate()
                src.limit(src.position() + dwSizeToRead)
                pBuffer.put(src)
                mpbDataCur!!.position(mpbDataCur!!.position() + dwSizeToRead)
                if (pdwSizeRead != null) {
                    pdwSizeRead[0] = dwSizeToRead
                }
                dwSizeToRead
            } else {
                if (mhmmio == null) {
                    return -1
                }
                if (pBuffer == null) {
                    return -1
                }
                dwSizeToRead = mhmmio!!.Read(pBuffer, dwSizeToRead)
                // this is hit by ogg code, which does it's own byte swapping internally
                if (!isOgg) {
                    LittleRevBytes(pBuffer.array(), 2, dwSizeToRead / 2)
                }
                if (pdwSizeRead != null) {
                    pdwSizeRead[0] = dwSizeToRead
                }
                dwSizeToRead
            }
        }

        // FIX: Was a stub throwing TODO_Exception. Implemented from C++ snd_wavefile.cpp:333-355.
        fun Seek(offset: Int): Int {
            if (ogg != 0L) {
                Common.common.FatalError("idWaveFile::Seek: cannot seek on an OGG file\n")
            } else if (mbIsReadingFromMemory) {
                mpbDataCur = mpbData!!.duplicate()
                mpbDataCur!!.position(offset)
            } else {
                if (mhmmio == null) {
                    return -1
                }
                if ((offset + mseekBase).toInt() == mhmmio!!.Tell()) {
                    return 0
                }
                mhmmio!!.Seek((offset + mseekBase), fsOrigin_t.FS_SEEK_SET)
                return 0
            }
            return -1
        }

        //-----------------------------------------------------------------------------
        // Name: idWaveFile::Close()
        // Desc: Closes the wave file 
        //-----------------------------------------------------------------------------
        fun Close(): Int {
            if (ogg != 0L) {
                return CloseOGG()
            }
            if (mhmmio != null) {
                FileSystem_h.fileSystem.CloseFile(mhmmio!!)
                mhmmio = null
            }
            return 0
        }

        //-----------------------------------------------------------------------------
        // Name: idWaveFile::ResetFile()
        // Desc: Resets the internal mck pointer so reading starts from the 
        //       beginning of the file again 
        //-----------------------------------------------------------------------------
        fun ResetFile(): Int {
            if (mbIsReadingFromMemory) {
                // FIX: C++ pointer reassignment. Kotlin reference assignment would alias.
                // Must duplicate() to create an independent ByteBuffer pointing to same data.
                mpbDataCur = mpbData!!.duplicate()
                mpbDataCur!!.position(0)
            } else {
                if (mhmmio == null) {
                    return -1
                }

                // Seek to the data
                if (!mhmmio!!.Seek((mckRiff.dwDataOffset + Integer.BYTES).toLong(), fsOrigin_t.FS_SEEK_SET)) {
                    return -1
                }

                // Search the input file for for the 'fmt ' chunk.
                mck.ckid = 0
                do {
                    val ioin = ByteBuffer.allocate(1)
                    if (0 == mhmmio!!.Read(ioin, 1)) {
                        return -1
                    }
                    mck.ckid =
                        Integer.toUnsignedLong((mck.ckid ushr 8).toInt() or (ioin.get(0).toInt() and 0xFF shl 24))
                } while (mck.ckid != mmioFOURCC('d'.code, 'a'.code, 't'.code, 'a'.code))
                mck.cksize = mhmmio!!.ReadInt()
                assert(!isOgg)
                mck.cksize = LittleLong(mck.cksize)
                mseekBase = mhmmio!!.Tell().toLong()
            }
            return 0
        }

        fun GetOutputSize(): Int {
            return mdwSize.toInt()
        }

        fun GetMemorySize(): Int {
            return mMemSize.toInt()
        }

        //-----------------------------------------------------------------------------
        // Name: idWaveFile::ReadMMIO()
        // Desc: Support function for reading from a multimedia I/O stream.
        //       mhmmio must be valid before calling.  This function uses it to
        //       update mckRiff, and mpwfx. 
        //-----------------------------------------------------------------------------
        private fun ReadMMIO(): Int {
            val ckIn = mminfo_s() // chunk info. for general use.
            val pcmWaveFormat = pcmwaveformat_s() // Temp PCM structure to load in.       
            mpwfx = waveformatextensible_s()
            mhmmio!!.Read(mckRiff, 12)
            assert(!isOgg)
            mckRiff.ckid = LittleLong(mckRiff.ckid).toLong()
            mckRiff.cksize = LittleLong(mckRiff.cksize)
            mckRiff.fccType = LittleLong(mckRiff.fccType).toLong()
            mckRiff.dwDataOffset = 12

            // Check to make sure this is a valid wave file
            if (mckRiff.ckid != fourcc_riff || mckRiff.fccType != mmioFOURCC(
                    'W'.code,
                    'A'.code,
                    'V'.code,
                    'E'.code
                )
            ) {
                return -1
            }

            // Search the input file for for the 'fmt ' chunk.
            ckIn.dwDataOffset = 12
            do {
                if (8 != mhmmio!!.Read(ckIn, 8)) {
                    return -1
                }
                assert(!isOgg)
                ckIn.ckid = LittleLong(ckIn.ckid).toLong()
                ckIn.cksize = LittleLong(ckIn.cksize)
                ckIn.dwDataOffset += ckIn.cksize - 8
            } while (ckIn.ckid != mmioFOURCC('f'.code, 'm'.code, 't'.code, ' '.code))

            // Expect the 'fmt' chunk to be at least as large as <PCMWAVEFORMAT>;
            // if there are extra parameters at the end, we'll ignore them
            if (ckIn.cksize < pcmwaveformat_s.BYTES) {
                return -1
            }

            // Read the 'fmt ' chunk into <pcmWaveFormat>.
            if (mhmmio!!.Read(pcmWaveFormat) != pcmwaveformat_s.BYTES) {
                return -1
            }
            assert(!isOgg)
            pcmWaveFormat.wf.wFormatTag = LittleShort(pcmWaveFormat.wf.wFormatTag.toShort()).toInt()
            pcmWaveFormat.wf.nChannels = LittleShort(pcmWaveFormat.wf.nChannels.toShort()).toInt()
            pcmWaveFormat.wf.nSamplesPerSec = LittleLong(pcmWaveFormat.wf.nSamplesPerSec)
            pcmWaveFormat.wf.nAvgBytesPerSec = LittleLong(pcmWaveFormat.wf.nAvgBytesPerSec)
            pcmWaveFormat.wf.nBlockAlign = LittleShort(pcmWaveFormat.wf.nBlockAlign.toShort()).toInt()
            pcmWaveFormat.wBitsPerSample = LittleShort(pcmWaveFormat.wBitsPerSample.toShort()).toInt()

            // Copy the bytes from the pcm structure to the waveformatex_t structure
            mpwfx = waveformatextensible_s(pcmWaveFormat)

            // Allocate the waveformatex_t, but if its not pcm format, read the next
            // word, and thats how many extra bytes to allocate.
            if (pcmWaveFormat.wf.wFormatTag == snd_local.WAVE_FORMAT_TAG_PCM) {
                mpwfx.Format.cbSize = 0
            } else {
                return -1 // we don't handle these (32 bit wavefiles, etc)
                // #if 0
                // // Read in length of extra bytes.
                // word cbExtraBytes = 0L;
                // if( mhmmio.Read( (char*)&cbExtraBytes, sizeof(word) ) != sizeof(word) )
                // return -1;

                // mpwfx.Format.cbSize = cbExtraBytes;
                // // Now, read those extra bytes into the structure, if cbExtraAlloc != 0.
                // if( mhmmio.Read( (char*)(((byte*)&(mpwfx.Format.cbSize))+sizeof(word)), cbExtraBytes ) != cbExtraBytes ) {
                // memset( &mpwfx, 0, sizeof( waveformatextensible_t ) );
                // return -1;
                // }
// #endif
            }
            return 0
        }

        private fun OpenOGG(strFileName: String, pwfx: Array<waveformatex_s> /*= NULL*/): Int {
            // FIX: C++ zeroes pwfx on entry
            pwfx[0] = waveformatex_s()
            val error = intArrayOf(0)
            mhmmio = FileSystem_h.fileSystem.OpenFileRead(strFileName)
            if (null == mhmmio) {
                return -1
            }
            win_main.Sys_EnterCriticalSection(sys_public.CRITICAL_SECTION_ONE)
            try {
                val fileSize = mhmmio!!.Length()
                val buffer = ByteBuffer.allocate(fileSize)
                mhmmio!!.Read(buffer)
                val d_buffer = BufferUtils.createByteBuffer(buffer.capacity()).put(buffer).rewind() as ByteBuffer
                val ov = STBVorbis.stb_vorbis_open_memory(d_buffer, error, null)
                if (ov == 0L) {
                    Common.common.Warning("Opening OGG file '%s' with stb_vorbis failed\n", strFileName)
                    FileSystem_h.fileSystem.CloseFile(mhmmio!!)
                    mhmmio = null
                    return -1
                }
                val vi = STBVorbis.stb_vorbis_get_info(ov, STBVorbisInfo.create())
                mfileTime = mhmmio!!.Timestamp()
                mpwfx.Format.nSamplesPerSec = vi.sample_rate()
                mpwfx.Format.nChannels = vi.channels()
                mpwfx.Format.wBitsPerSample = java.lang.Short.SIZE

                // FIX: C++ checks for zero sample count and logs warning
                val numSamples = STBVorbis.stb_vorbis_stream_length_in_samples(ov)
                if (numSamples == 0) {
                    Common.common.Warning("Couldn't get sound length of '%s' with stb_vorbis\n", strFileName)
                }
                mdwSize = (numSamples * vi.channels()).toLong() // pcm samples * num channels
                mbIsReadingFromMemory = false
                if (idSoundSystemLocal.s_realTimeDecoding.GetBool()) {
                    STBVorbis.stb_vorbis_close(ov)
                    FileSystem_h.fileSystem.CloseFile(mhmmio!!)
                    mhmmio = null
                    mpwfx.Format.wFormatTag = snd_local.WAVE_FORMAT_TAG_OGG
                    mhmmio = FileSystem_h.fileSystem.OpenFileRead(strFileName)
                    mMemSize = mhmmio!!.Length().toLong()
                } else {
                    // FIX: C++ stores the stb_vorbis handle and keeps oggData alive.
                    // Kotlin was closing the handle immediately and setting ogg to a dummy string.
                    ogg = ov
                    oggData = d_buffer // keep the direct buffer alive so stb_vorbis can read from it
                    mpwfx.Format.wFormatTag = snd_local.WAVE_FORMAT_TAG_PCM
                    mMemSize = mdwSize * java.lang.Short.SIZE / java.lang.Byte.SIZE
                }
                pwfx[0] = mpwfx.Format
            } finally {
                win_main.Sys_LeaveCriticalSection(sys_public.CRITICAL_SECTION_ONE)
            }
            isOgg = true
            return 0
        }

        // FIX: Was a stub throwing TODO_Exception. Implemented from dhewm3 snd_decoder.cpp:229-262.
        // Note: stb_vorbis operates on shorts, not bytes like the old ov_read.
        private fun ReadOGG(pBuffer: ByteArray?, dwSizeToRead: Int, pdwSizeRead: IntArray?): Int {
            var total = dwSizeToRead / 2 // sizeof(short)
            // LWJGL stb_vorbis_get_samples_short_interleaved uses ShortBuffer position/limit
            val shortBuf = BufferUtils.createShortBuffer(total)
            val ov = ogg

            do {
                val numShorts = total
                shortBuf.limit(shortBuf.position() + numShorts)
                val ret = STBVorbis.stb_vorbis_get_samples_short_interleaved(ov, mpwfx.Format.nChannels, shortBuf)
                if (ret == 0) {
                    break
                }
                if (ret < 0) {
                    Common.common.Warning(
                        "idWaveFile::ReadOGG() stb_vorbis_get_samples_short_interleaved() %d shorts failed\n",
                        numShorts
                    )
                    return -1
                }
                // stb_vorbis returns samples per channel, multiply by nChannels to get total shorts
                val decoded = ret * mpwfx.Format.nChannels
                shortBuf.position(shortBuf.position() + decoded)
                total -= decoded
            } while (total > 0)

            // Convert shorts back to bytes into pBuffer
            val shortsRead = shortBuf.position()
            val bytesRead = shortsRead * 2
            if (pBuffer != null) {
                shortBuf.flip()
                val bb = ByteBuffer.allocate(bytesRead)
                bb.asShortBuffer().put(shortBuf)
                bb.rewind()
                bb.get(pBuffer, 0, bytesRead)
            }
            if (pdwSizeRead != null) {
                pdwSizeRead[0] = bytesRead
            }
            return bytesRead
        }

        // FIX: Was a stub throwing TODO_Exception. Implemented from dhewm3 snd_decoder.cpp:269-283.
        private fun CloseOGG(): Int {
            val ov = ogg
            if (ov != 0L) {
                win_main.Sys_EnterCriticalSection(sys_public.CRITICAL_SECTION_ONE)
                STBVorbis.stb_vorbis_close(ov)
                win_main.Sys_LeaveCriticalSection(sys_public.CRITICAL_SECTION_ONE)
                if (mhmmio != null) {
                    FileSystem_h.fileSystem.CloseFile(mhmmio!!)
                    mhmmio = null
                }
                ogg = 0
                oggData = null // allow GC of the raw OGG data buffer
                return 0
            }
            return -1
        }

        //
        //
        //-----------------------------------------------------------------------------
        // Name: idWaveFile::idWaveFile()
        // Desc: Constructs the class.  Call Open() to open a wave file for reading.  
        //       Then call Read() as needed.  Calling the destructor or Close() 
        //       will close the file.  
        //-----------------------------------------------------------------------------
        init {
            mpwfx = waveformatextensible_s()
            mhmmio = null
            mck = mminfo_s()
            mckRiff = mminfo_s()
            mdwSize = 0
            mseekBase = 0
            mbIsReadingFromMemory = false
            mpbData = null
            ogg = 0
            oggData = null
            isOgg = false
        }
    }
}
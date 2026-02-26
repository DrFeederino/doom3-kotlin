package neo.Sound

import neo.Sound.snd_local.*
import neo.Sound.snd_system.idSoundSystemLocal
import neo.TempDump
import neo.TempDump.TODO_Exception
import neo.framework.FileSystem_h
import neo.framework.File_h.fsOrigin_t
import neo.framework.File_h.idFile
import neo.idlib.LittleLong
import neo.idlib.LittleRevBytes
import neo.idlib.LittleShort
import neo.idlib.Text.Str.idStr
import neo.idlib.math.SIMDProcessor
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
        private var   /*dword*/mMemSize // size of the wave data in memory
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
        private var ogg // only !NULL when !s_realTimeDecoding
                : Any?

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

//	memset( &mpwfx, 0, sizeof( waveformatextensible_t ) );
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
            mdwSize = (ulDataSize / 2).toLong() //sizeof(short);
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
            return if (ogg != null) {
                ReadOGG(pBuffer.array(), dwSizeToRead, pdwSizeRead)
            } else if (mbIsReadingFromMemory) {
                if (mpbDataCur == null) {
                    return -1
                }
                val pos = dwSizeToRead + mpbDataCur!!.position() //add current offset
                if (mpbDataCur!!.get(dwSizeToRead) > mpbData!!.get(mulDataSize.toInt())) {
                    dwSizeToRead = (mulDataSize - mpbDataCur!!.position()).toInt()
                }
                SIMDProcessor!!.Memcpy(pBuffer, mpbDataCur!!, dwSizeToRead)
                mpbDataCur!!.position(pos)
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

        fun Seek(offset: Int): Int {
            throw TODO_Exception()
            //
//            if (ogg != null) {
//
//                common.FatalError("idWaveFile::Seek: cannot seek on an OGG file\n");
//
//            } else if (mbIsReadingFromMemory) {
//
//                mpbDataCur = mpbData + offset;
//
//            } else {
//                if (mhmmio == null) {
//                    return -1;
//                }
//
//                if ((int) (offset + mseekBase) == mhmmio.Tell()) {
//                    return 0;
//                }
//                mhmmio.Seek(offset + mseekBase, FS_SEEK_SET);
//                return 0;
//            }
//            return -1;
        }

        //-----------------------------------------------------------------------------
        // Name: idWaveFile::Close()
        // Desc: Closes the wave file 
        //-----------------------------------------------------------------------------
        fun Close(): Int {
            if (ogg != null) {
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
                mpbDataCur = mpbData
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
                    mck.ckid = Integer.toUnsignedLong((mck.ckid ushr 8).toInt() or (ioin.get().toInt() shl 24))
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
            mpwfx = waveformatextensible_s() //memset( &mpwfx, 0, sizeof( waveformatextensible_t ) );
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
//            memset(pwfx, 0, sizeof(waveformatex_t));
            val error = intArrayOf(0)
            var vi: STBVorbisInfo? = null
            mhmmio = FileSystem_h.fileSystem.OpenFileRead(strFileName)
            if (null == mhmmio) {
                return -1
            }
            win_main.Sys_EnterCriticalSection(sys_public.CRITICAL_SECTION_ONE)
            val buffer = ByteBuffer.allocate(mhmmio!!.Length())
            mhmmio!!.Read(buffer)
            try {
                val d_buffer = BufferUtils.createByteBuffer(buffer.capacity()).put(buffer).rewind()
                val ov = STBVorbis.stb_vorbis_open_memory(d_buffer, error, null)
                if (error[0] != 0) {
                    FileSystem_h.fileSystem.CloseFile(mhmmio!!)
                    mhmmio = null
                    return -1
                }
                vi = STBVorbis.stb_vorbis_get_info(ov, STBVorbisInfo.create())
                mfileTime = mhmmio!!.Timestamp()
                mpwfx.Format.nSamplesPerSec = vi.sample_rate()
                mpwfx.Format.nChannels = vi.channels()
                mpwfx.Format.wBitsPerSample = java.lang.Short.SIZE
                mdwSize =
                    (STBVorbis.stb_vorbis_stream_length_in_samples(ov) * vi.channels()).toLong() // pcm samples * num channels
                mbIsReadingFromMemory = false
                if (idSoundSystemLocal.s_realTimeDecoding.GetBool()) {
                    FileSystem_h.fileSystem.CloseFile(mhmmio!!)
                    mhmmio = null
                    mpwfx.Format.wFormatTag = snd_local.WAVE_FORMAT_TAG_OGG
                    mhmmio = FileSystem_h.fileSystem.OpenFileRead(strFileName)
                    mMemSize = mhmmio!!.Length().toLong()
                } else {
                    ogg = "we only check if this is not null"
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

        private fun ReadOGG(pBuffer: ByteArray?, dwSizeToRead: Int, pdwSizeRead: IntArray?): Int {
            throw TODO_Exception()
            //            int total = dwSizeToRead;
//            String bufferPtr = (char[]) pBuffer;
//            OggVorbis_File ov = (OggVorbis_File) ogg;
//
//            do {
//                int ret = ov_read(ov, bufferPtr, total >= 4096 ? 4096 : total, Swap_IsBigEndian(), 2, 1, ov.stream);
//                if (ret == 0) {
//                    break;
//                }
//                if (ret < 0) {
//                    return -1;
//                }
//                bufferPtr += ret;
//                total -= ret;
//            } while (total > 0);
//
//            dwSizeToRead = (byte[]) bufferPtr - pBuffer;
//
//            if (pdwSizeRead != null) {
//                pdwSizeRead[0] = dwSizeToRead;
//            }
//
//            return dwSizeToRead;
        }

        private fun CloseOGG(): Int {
            throw TODO_Exception()
            //            OggVorbis_File ov = (OggVorbis_File) ogg;
//            if (ov != null) {
//                Sys_EnterCriticalSection(CRITICAL_SECTION_ONE);
//                ov_clear(ov);
////		delete ov;
//                Sys_LeaveCriticalSection(CRITICAL_SECTION_ONE);
//                fileSystem.CloseFile(mhmmio);
//                mhmmio = null;
//                ogg = null;
//                return 0;
//            }
//            return -1;
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
//	memset( &mpwfx, 0, sizeof( waveformatextensible_t ) );
            mpwfx = waveformatextensible_s()
            mhmmio = null
            mck = mminfo_s()
            mckRiff = mminfo_s()
            mdwSize = 0
            mseekBase = 0
            mbIsReadingFromMemory = false
            mpbData = null
            ogg = null
            isOgg = false
        }
    }
}
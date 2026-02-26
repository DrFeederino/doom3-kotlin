package neo.Sound

import neo.Sound.snd_cache.idSoundSample
import neo.Sound.snd_local.idSampleDecoder
import neo.TempDump.TODO_Exception
import neo.framework.File_h.idFile_Memory
import neo.idlib.math.SIMDProcessor
import neo.sys.sys_public
import neo.sys.win_main
import org.lwjgl.BufferUtils
import org.lwjgl.PointerBuffer
import org.lwjgl.stb.STBVorbis
import java.nio.ByteBuffer
import java.nio.FloatBuffer
import java.util.*
import java.util.logging.Level
import java.util.logging.Logger

object snd_decoder {
    /*
     ===================================================================================

     Thread safe decoder memory allocator.

     Each OggVorbis decoder consumes about 150kB of memory.

     ===================================================================================
     */
    //    idDynamicBlockAlloc<Byte> decoderMemoryAllocator = new idDynamicBlockAlloc(1 << 20, 128);
    //
    //    static final int MIN_OGGVORBIS_MEMORY = 768 * 1024;
    //
    //    public static Object _decoder_malloc(int/*size_t*/ size) {
    //        Object ptr = decoderMemoryAllocator.Alloc(size);
    //        assert (size == 0 || ptr != null);
    //        return ptr;
    //    }
    //
    //    public static Object _decoder_calloc(int/*size_t*/ num, int/*size_t*/ size) {
    //        Object ptr = decoderMemoryAllocator.Alloc(num * size);
    //        assert ((num * size) == 0 || ptr != null);
    //        memset(ptr, 0, num * size);
    //        return ptr;
    //    }
    //
    //    public static Object _decoder_realloc(Object memblock, int/*size_t*/ size) {
    //        Object ptr = decoderMemoryAllocator.Resize((byte[]) memblock, size);
    //        assert (size == 0 || ptr != null);
    //        return ptr;
    //    }
    //
    //    public static void _decoder_free(Object memblock) {
    //        decoderMemoryAllocator.Free((byte[]) memblock);
    //    }
    //
    //
    /*
     ===================================================================================

     OggVorbis file loading/decoding.

     ===================================================================================
     */
    /*
     ====================
     FS_ReadOGG
     ====================
     */
    fun  /*size_t*/FS_ReadOGG(
        dest: ByteBuffer,    /*size_t*/
        size1: Int,    /*size_t*/
        size2: Int,
        fh: ByteBuffer
    ): Int {
        throw TODO_Exception()
        //        idFile f = reinterpret_cast < idFile > (fh);
//        return f.Read(dest, size1 * size2);
    }

    /*
     ====================
     FS_SeekOGG
     ====================
     */
    fun FS_SeekOGG(fh: Any,    /*ogg_int64_t*/to: Long, type: Int): Int {
        throw TODO_Exception()
        //        fsOrigin_t retype = FS_SEEK_SET;
//
//        if (type == SEEK_CUR) {
//            retype = FS_SEEK_CUR;
//        } else if (type == SEEK_END) {
//            retype = FS_SEEK_END;
//        } else if (type == SEEK_SET) {
//            retype = FS_SEEK_SET;
//        } else {
//            common.FatalError("fs_seekOGG: seek without type\n");
//        }
//        idFile f = reinterpret_cast < idFile > (fh);
//        return f.Seek(to, retype);
    }

    /*
     ====================
     FS_CloseOGG
     ====================
     */
    fun FS_CloseOGG(fh: Any): Int {
        return 0
    }

    /*
     ====================
     FS_TellOGG
     ====================
     */
    fun FS_TellOGG(fh: Any): Long {
        throw TODO_Exception()
        //        idFile f = reinterpret_cast < idFile > (fh);
//        return f.Tell();
    }

    /*
     ====================
     ov_openFile
     ====================
     */
    fun ov_openFile(f: idFile_Memory, error: IntArray): Long {
        return STBVorbis.stb_vorbis_open_memory(f.GetDataPtr(), error, null)
    }

    private fun getErrorMessage(errorCode: Int): String {
        when (errorCode) {
            STBVorbis.VORBIS__no_error -> return "VORBIS__no_error"
            STBVorbis.VORBIS_need_more_data -> return "VORBIS_need_more_data"
            STBVorbis.VORBIS_invalid_api_mixing -> return "VORBIS_invalid_api_mixing"
            STBVorbis.VORBIS_outofmem -> return "VORBIS_outofmem"
            STBVorbis.VORBIS_feature_not_supported -> return "VORBIS_feature_not_supported"
            STBVorbis.VORBIS_too_many_channels -> return "VORBIS_too_many_channels"
            STBVorbis.VORBIS_file_open_failure -> return "VORBIS_file_open_failure"
            STBVorbis.VORBIS_seek_without_length -> return "VORBIS_seek_without_length"
            STBVorbis.VORBIS_unexpected_eof -> return "VORBIS_unexpected_eof"
            STBVorbis.VORBIS_seek_invalid -> return "VORBIS_seek_invalid"
            STBVorbis.VORBIS_invalid_setup -> return "VORBIS_invalid_setup"
            STBVorbis.VORBIS_invalid_stream -> return "VORBIS_invalid_stream"
            STBVorbis.VORBIS_missing_capture_pattern -> return "VORBIS_missing_capture_pattern"
            STBVorbis.VORBIS_invalid_stream_structure_version -> return "VORBIS_invalid_stream_structure_version"
            STBVorbis.VORBIS_continued_packet_flag_invalid -> return "VORBIS_continued_packet_flag_invalid"
            STBVorbis.VORBIS_incorrect_stream_serial_number -> return "VORBIS_incorrect_stream_serial_number"
            STBVorbis.VORBIS_invalid_first_page -> return "VORBIS_invalid_first_page"
            STBVorbis.VORBIS_bad_packet_type -> return "VORBIS_bad_packet_type"
            STBVorbis.VORBIS_cant_find_last_page -> return "VORBIS_cant_find_last_page"
            STBVorbis.VORBIS_seek_failed -> return "VORBIS_seek_failed"
            STBVorbis.VORBIS_ogg_skeleton_not_supported -> return "VORBIS_ogg_skeleton_not_supported"
        }
        return "Unknown error"
    }

    //    static final idBlockAlloc<idSampleDecoderLocal> sampleDecoderAllocator = new idBlockAlloc<>(64);
    /*
     ===================================================================================

     idSampleDecoderLocal

     ===================================================================================
     */
    class idSampleDecoderLocal internal constructor() : idSampleDecoder() {
        private var failed // set if decoding failed
                = false
        private val file // encoded file in memory
                : idFile_Memory
        private var lastDecodeTime // last time decoding sound
                = 0
        private var lastFormat // last format being decoded
                = 0
        private var lastSample // last sample being decoded
                : idSoundSample? = null
        private var lastSampleOffset // last offset into the decoded sample
                = 0

        //
        //
        //
        private var ogg // OggVorbis file
                : Long = 0L

        override fun Decode(sample: idSoundSample, sampleOffset44k: Int, sampleCount44k: Int, dest: FloatBuffer) {
            val readSamples44k: Int
            if (sample.objectInfo.wFormatTag != lastFormat || sample !== lastSample) {
                ClearDecoder()
            }
            lastDecodeTime = snd_system.soundSystemLocal.CurrentSoundTime
            if (failed) {
//                memset(dest, 0, sampleCount44k * sizeof(dest[0]));
                dest.clear()
                return
            }

            // samples can be decoded both from the sound thread and the main thread for shakes
            win_main.Sys_EnterCriticalSection(sys_public.CRITICAL_SECTION_ONE)
            readSamples44k = try {
                when (sample.objectInfo.wFormatTag) {
                    snd_local.WAVE_FORMAT_TAG_PCM -> {
                        DecodePCM(sample, sampleOffset44k, sampleCount44k, dest.array()) //TODO:fix with offset
                    }

                    snd_local.WAVE_FORMAT_TAG_OGG -> {
                        DBG_Decode++
                        DecodeOGG(sample, sampleOffset44k, sampleCount44k, dest)
                    }

                    else -> {
                        0
                    }
                }
            } finally {
                win_main.Sys_LeaveCriticalSection(sys_public.CRITICAL_SECTION_ONE)
            }
            if (readSamples44k < sampleCount44k) {
//                memset(dest + readSamples44k, 0, (sampleCount44k - readSamples44k) * sizeof(dest[0]));
                Arrays.fill(dest.array(), readSamples44k, sampleCount44k - readSamples44k, 0.0f)
            }
        }

        override fun ClearDecoder() {
            win_main.Sys_EnterCriticalSection(sys_public.CRITICAL_SECTION_ONE)
            try {
                when (lastFormat) {
                    snd_local.WAVE_FORMAT_TAG_PCM -> {}
                    snd_local.WAVE_FORMAT_TAG_OGG -> {

//                    ov_clear(ogg);
//                    memset(ogg, 0, sizeof(ogg));
                        ogg = 0L
                    }
                }
                Clear()
            } finally {
                win_main.Sys_LeaveCriticalSection(sys_public.CRITICAL_SECTION_ONE)
            }
        }

        override fun GetSample(): idSoundSample? {
            return lastSample
        }

        override fun GetLastDecodeTime(): Int {
            return lastDecodeTime
        }

        fun Clear() {
            failed = false
            lastFormat = snd_local.WAVE_FORMAT_TAG_PCM
            lastSample = null
            lastSampleOffset = 0
            lastDecodeTime = 0
        }

        fun DecodePCM(sample: idSoundSample, sampleOffset44k: Int, sampleCount44k: Int, dest: FloatArray): Int {
            throw TODO_Exception()
            //            ByteBuffer first;
//            int[] pos = {0}, size = {0};
//            int readSamples;
//
//            lastFormat = WAVE_FORMAT_TAG_PCM;
//            lastSample = sample;
//
//            int shift = (int) (22050 / sample.objectInfo.nSamplesPerSec);
//            int sampleOffset = sampleOffset44k >> shift;
//            int sampleCount = sampleCount44k >> shift;
//
//            if (sample.nonCacheData == null) {
//                assert (false);	// this should never happen ( note: I've seen that happen with the main thread down in idGameLocal::MapClear clearing entities - TTimo )
//                failed = true;
//                return 0;
//            }
//
//            if (!sample.FetchFromCache(sampleOffset /* sizeof( short )*/, first, pos, size, false)) {
//                failed = true;
//                return 0;
//            }
//
//            if (size[0] - pos[0] < sampleCount /*sizeof(short)*/) {
//                readSamples = (size[0] - pos[0]) /* sizeof(short)*/;
//            } else {
//                readSamples = sampleCount;
//            }
//
//            // duplicate samples for 44kHz output
//            first.position(pos[0]);
//            SIMDProcessor.UpSamplePCMTo44kHz(dest, first, readSamples, sample.objectInfo.nSamplesPerSec, sample.objectInfo.nChannels);
//
//            return (readSamples << shift);
        }

        fun DecodeOGG(sample: idSoundSample, sampleOffset44k: Int, sampleCount44k: Int, dest: FloatBuffer): Int {
            var readSamples: Int
            var totalSamples: Int
            val shift = 22050 / sample.objectInfo.nSamplesPerSec
            val sampleOffset = sampleOffset44k shr shift
            val sampleCount = sampleCount44k shr shift

            // open OGG file if not yet opened
            if (lastSample == null) {
                // make sure there is enough space for another decoder
//                if (decoderMemoryAllocator.GetFreeBlockMemory() < MIN_OGGVORBIS_MEMORY) {
//                    return 0;
//                }
                if (sample.nonCacheData == null) {
                    assert(
                        false // this should never happen
                    )
                    failed = true
                    return 0
                }
                file.SetData(sample.nonCacheData!!, sample.objectMemSize)
                val error = intArrayOf(0)
                ogg = ov_openFile(file, error)
                if (error[0] != 0) {
                    Logger.getLogger(snd_decoder::class.java.name)
                        .log(Level.SEVERE, getErrorMessage(error[0]))
                    failed = true
                    return 0
                }
                lastFormat = snd_local.WAVE_FORMAT_TAG_OGG
                lastSample = sample
            }

            // seek to the right offset if necessary
            if (sampleOffset != lastSampleOffset) {
                if (!STBVorbis.stb_vorbis_seek(ogg, sampleOffset / sample.objectInfo.nChannels)) {
                    failed = true
                    return 0
                }
            }
            lastSampleOffset = sampleOffset

            // decode OGG samples
            totalSamples = sampleCount
            readSamples = 0
            do {
                val samples = PointerBuffer.allocateDirect(sample.objectInfo.nChannels)
                val num_samples = totalSamples / sample.objectInfo.nChannels
                for (i in 0 until sample.objectInfo.nChannels) {
                    samples.put(i, BufferUtils.createFloatBuffer(num_samples))
                }
                var ret = STBVorbis.stb_vorbis_get_samples_float(ogg, samples, num_samples)
                if (ret == 0) {
                    failed = true
                    break
                }
                if (ret < 0) {
                    failed = true
                    return 0
                }
                ret *= sample.objectInfo.nChannels
                val samplesArray = Array(sample.objectInfo.nChannels) { FloatArray(num_samples) }
                for (i in 0 until sample.objectInfo.nChannels) {
                    samples.getFloatBuffer(i, num_samples)[samplesArray[i]]
                }
                SIMDProcessor!!.UpSampleOGGTo44kHz(
                    dest,
                    readSamples shl shift,
                    samplesArray,
                    ret,
                    sample.objectInfo.nSamplesPerSec,
                    sample.objectInfo.nChannels
                )
                readSamples += ret
                totalSamples -= ret
            } while (totalSamples > 0)
            lastSampleOffset += readSamples
            return readSamples shl shift
        }

        companion object {
            private var DBG_Decode = 0
        }

        init {
            file = idFile_Memory()
        }
    }
}
/*
 * ===========================================================================
 *
 * Doom 3 GPL Source Code
 * Copyright (C) 1999-2011 id Software LLC, a ZeniMax Media company.
 * Translated to Kotlin by Dr. Feederino with support of Claude Code
 *
 * This file is part of the Doom 3 GPL Source Code ("Doom 3 Source Code").
 *
 * Doom 3 Source Code is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Doom 3 Source Code is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Doom 3 Source Code. If not, see <http://www.gnu.org/licenses/>.
 *
 * ===========================================================================
 *
 * Original source: neo/sound/snd_decoder.cpp, neo/sound/snd_local.h
 */

package neo.Sound

import neo.Sound.snd_cache.idSoundSample
import neo.Sound.snd_local.idSampleDecoder
import neo.framework.Common
import neo.framework.File_h.idFile_Memory
import neo.idlib.math.MIXBUFFER_SAMPLES
import neo.idlib.math.SIMDProcessor
import neo.idlib.Min
import neo.sys.sys_public
import neo.sys.win_main
import org.lwjgl.BufferUtils
import org.lwjgl.PointerBuffer
import org.lwjgl.stb.STBVorbis
import java.nio.FloatBuffer

object snd_decoder {
    /*
     ===================================================================================

       Thread safe decoder memory allocator.

       Each OggVorbis decoder consumes about 150kB of memory.

     ===================================================================================
     */
    // NOTE: Kotlin-only — decoderMemoryAllocator and custom malloc/calloc/realloc/free
    // are not needed; JVM handles memory management. dhewm3 kept these from original
    // id Tech 4 but noted they're unused with stb_vorbis.

    /*
     ===================================================================================

       OggVorbis file loading/decoding.

     ===================================================================================
     */

    // NOTE: FS_ReadOGG, FS_SeekOGG, FS_CloseOGG, FS_TellOGG were custom OGG I/O callbacks
    // for libvorbisfile. dhewm3 replaced libvorbisfile with stb_vorbis (memory-based decoding)
    // and removed these callbacks. They are dead code in the Kotlin translation.

    /*
     ====================
     ov_openFile
     ====================
     */
    fun ov_openFile(f: idFile_Memory, error: IntArray): Long {
        return STBVorbis.stb_vorbis_open_memory(f.GetDataPtr(), error, null)
    }

    /*
     ====================
     my_stbv_strerror — maps stb_vorbis error codes to human-readable strings
     ====================
     */
    private fun getErrorMessage(errorCode: Int): String {
        when (errorCode) {
            STBVorbis.VORBIS__no_error -> return "No Error"
            STBVorbis.VORBIS_need_more_data -> return "need_more_data"
            STBVorbis.VORBIS_invalid_api_mixing -> return "invalid_api_mixing"
            STBVorbis.VORBIS_outofmem -> return "outofmem"
            STBVorbis.VORBIS_feature_not_supported -> return "feature_not_supported"
            STBVorbis.VORBIS_too_many_channels -> return "too_many_channels"
            STBVorbis.VORBIS_file_open_failure -> return "file_open_failure"
            STBVorbis.VORBIS_seek_without_length -> return "seek_without_length"
            STBVorbis.VORBIS_unexpected_eof -> return "unexpected_eof"
            STBVorbis.VORBIS_seek_invalid -> return "seek_invalid"
            STBVorbis.VORBIS_invalid_setup -> return "invalid_setup"
            STBVorbis.VORBIS_invalid_stream -> return "invalid_stream"
            STBVorbis.VORBIS_missing_capture_pattern -> return "missing_capture_pattern"
            STBVorbis.VORBIS_invalid_stream_structure_version -> return "invalid_stream_structure_version"
            STBVorbis.VORBIS_continued_packet_flag_invalid -> return "continued_packet_flag_invalid"
            STBVorbis.VORBIS_incorrect_stream_serial_number -> return "incorrect_stream_serial_number"
            STBVorbis.VORBIS_invalid_first_page -> return "invalid_first_page"
            STBVorbis.VORBIS_bad_packet_type -> return "bad_packet_type"
            STBVorbis.VORBIS_cant_find_last_page -> return "cant_find_last_page"
            STBVorbis.VORBIS_seek_failed -> return "seek_failed"
            STBVorbis.VORBIS_ogg_skeleton_not_supported -> return "ogg_skeleton_not_supported"
        }
        return "Unknown Error!"
    }

    /*
     ===================================================================================

       idSampleDecoderLocal

     ===================================================================================
     */
    class idSampleDecoderLocal internal constructor() : idSampleDecoder() {
        private var failed: Boolean = false         // set if decoding failed
        private val file: idFile_Memory             // encoded file in memory
        private var lastDecodeTime: Int = 0         // last time decoding sound
        private var lastFormat: Int = 0             // last format being decoded
        private var lastSample: idSoundSample? = null // last sample being decoded
        private var lastSampleOffset: Int = 0       // last offset into the decoded sample
        private var ogg: Long = 0L                  // stb_vorbis handle

        /*
         ====================
         idSampleDecoderLocal::Decode
         ====================
         */
        override fun Decode(sample: idSoundSample, sampleOffset44k: Int, sampleCount44k: Int, dest: FloatBuffer) {
            val readSamples44k: Int

            if (sample.objectInfo.wFormatTag != lastFormat || sample !== lastSample) {
                ClearDecoder()
            }

            lastDecodeTime = snd_system.soundSystemLocal.CurrentSoundTime

            if (failed) {
                // FIX: Was dest.array() which crashes on direct FloatBuffers
                for (i in 0 until sampleCount44k) {
                    dest.put(i, 0.0f)
                }
                return
            }

            // samples can be decoded both from the sound thread and the main thread for shakes
            win_main.Sys_EnterCriticalSection(sys_public.CRITICAL_SECTION_ONE)
            readSamples44k = try {
                when (sample.objectInfo.wFormatTag) {
                    snd_local.WAVE_FORMAT_TAG_PCM -> {
                        DecodePCM(sample, sampleOffset44k, sampleCount44k, dest)
                    }

                    snd_local.WAVE_FORMAT_TAG_OGG -> {
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
                // FIX: Was dest.array() which crashes on direct FloatBuffers
                for (i in readSamples44k until sampleCount44k) {
                    dest.put(i, 0.0f)
                }
            }
        }

        /*
         ====================
         idSampleDecoderLocal::ClearDecoder
         ====================
         */
        override fun ClearDecoder() {
            win_main.Sys_EnterCriticalSection(sys_public.CRITICAL_SECTION_ONE)
            try {
                when (lastFormat) {
                    snd_local.WAVE_FORMAT_TAG_PCM -> {}
                    snd_local.WAVE_FORMAT_TAG_OGG -> {
                        if (ogg != 0L) {
                            STBVorbis.stb_vorbis_close(ogg)
                        }
                        ogg = 0L
                    }
                }
                Clear()
            } finally {
                win_main.Sys_LeaveCriticalSection(sys_public.CRITICAL_SECTION_ONE)
            }
        }

        /*
         ====================
         idSampleDecoderLocal::GetSample
         ====================
         */
        override fun GetSample(): idSoundSample? {
            return lastSample
        }

        /*
         ====================
         idSampleDecoderLocal::GetLastDecodeTime
         ====================
         */
        override fun GetLastDecodeTime(): Int {
            return lastDecodeTime
        }

        /*
         ====================
         idSampleDecoderLocal::Clear
         ====================
         */
        fun Clear() {
            failed = false
            lastFormat = snd_local.WAVE_FORMAT_TAG_PCM
            lastSample = null
            lastSampleOffset = 0
            lastDecodeTime = 0
        }

        /*
         ====================
         idSampleDecoderLocal::DecodePCM
         ====================
         */
        // FIX: Was a stub throwing TODO_Exception — now fully implemented from C++ source
        fun DecodePCM(sample: idSoundSample, sampleOffset44k: Int, sampleCount44k: Int, dest: FloatBuffer): Int {
            val pos = IntArray(1)
            val size = IntArray(1)

            lastFormat = snd_local.WAVE_FORMAT_TAG_PCM
            lastSample = sample

            val shift = 22050 / sample.objectInfo.nSamplesPerSec
            val sampleOffset = sampleOffset44k shr shift
            val sampleCount = sampleCount44k shr shift

            if (sample.nonCacheData == null) {
                // this should never happen ( note: I've seen that happen with the main thread
                // down in idGameLocal::MapClear clearing entities - TTimo )
                // DG: see comment in DecodeOGG()
                Common.common.Warning(
                    "Called idSampleDecoderLocal::DecodePCM() on idSoundSample '%s' without nonCacheData\n",
                    sample.name
                )
                failed = true
                return 0
            }

            if (!sample.FetchFromCache(sampleOffset * 2 /*sizeof(short)*/, null, pos, size, false)) {
                failed = true
                return 0
            }

            val readSamples: Int = if (size[0] - pos[0] < sampleCount * 2 /*sizeof(short)*/) {
                (size[0] - pos[0]) / 2 // sizeof(short)
            } else {
                sampleCount
            }

            // duplicate samples for 44kHz output
            // NOTE: Differs from C++ — C++ uses pointer arithmetic (first+pos) into nonCacheData,
            // Kotlin accesses nonCacheData directly as a ShortBuffer at the correct offset
            val ncd = sample.nonCacheData!!.duplicate()
            ncd.position(sampleOffset * 2 + pos[0])
            val pcmShortBuf = ncd.asShortBuffer()
            val pcmShorts = ShortArray(readSamples)
            pcmShortBuf.get(pcmShorts, 0, readSamples)

            // NOTE: Differs from C++ — SIMDProcessor.UpSamplePCMTo44kHz takes FloatArray, not FloatBuffer
            // We use a temp array and copy back to the FloatBuffer
            val destArray = FloatArray(sampleCount44k)
            SIMDProcessor!!.UpSamplePCMTo44kHz(
                destArray, pcmShorts, readSamples,
                sample.objectInfo.nSamplesPerSec, sample.objectInfo.nChannels
            )
            for (i in 0 until (readSamples shl shift)) {
                dest.put(i, destArray[i])
            }

            return readSamples shl shift
        }

        /*
         ====================
         idSampleDecoderLocal::DecodeOGG
         ====================
         */
        fun DecodeOGG(sample: idSoundSample, sampleOffset44k: Int, sampleCount44k: Int, dest: FloatBuffer): Int {
            var readSamples: Int
            var totalSamples: Int

            val shift = 22050 / sample.objectInfo.nSamplesPerSec
            val sampleOffset = sampleOffset44k shr shift
            val sampleCount = sampleCount44k shr shift

            // open OGG file if not yet opened
            if (lastSample == null) {
                if (sample.nonCacheData == null) {
                    // DG: turned this assertion into a warning, because this can happen, at least with
                    // the Classic Doom3 mod (when starting a new game). See dhewm3 issue #461
                    Common.common.Warning(
                        "Called idSampleDecoderLocal::DecodeOGG() on idSoundSample '%s' without nonCacheData\n",
                        sample.name
                    )
                    failed = true
                    return 0
                }
                file.SetData(sample.nonCacheData!!, sample.objectMemSize)
                val error = intArrayOf(0)
                ogg = ov_openFile(file, error)
                if (error[0] != 0) {
                    // FIX: Was using java.util.logging.Logger — matches C++ common->Warning()
                    Common.common.Warning(
                        "idSampleDecoderLocal::DecodeOGG() stb_vorbis_open_memory() for %s failed: %s\n",
                        sample.name, getErrorMessage(error[0])
                    )
                    failed = true
                    return 0
                }
                lastFormat = snd_local.WAVE_FORMAT_TAG_OGG
                lastSample = sample
            }

            // FIX: Added >2 channels check from dhewm3 C++ (was missing)
            if (sample.objectInfo.nChannels > 2) {
                Common.common.Warning("Ogg Vorbis files with >2 channels are not supported!\n")
                failed = true
                return 0
            }

            // seek to the right offset if necessary
            if (sampleOffset != lastSampleOffset) {
                if (!STBVorbis.stb_vorbis_seek(ogg, sampleOffset / sample.objectInfo.nChannels)) {
                    // FIX: Added error logging from dhewm3 C++ (was missing)
                    Common.common.Warning(
                        "idSampleDecoderLocal::DecodeOGG() stb_vorbis_seek(%d) for %s failed\n",
                        sampleOffset / sample.objectInfo.nChannels, sample.name
                    )
                    failed = true
                    return 0
                }
            }
            lastSampleOffset = sampleOffset

            // decode OGG samples
            totalSamples = sampleCount
            readSamples = 0
            do {
                // DG: in contrast to libvorbisfile's ov_read_float(), stb_vorbis_get_samples_float()
                // expects you to pass a buffer to store the decoded samples in,
                // so limit it to MIXBUFFER_SAMPLES samples/channel per iteration
                val nChannels = sample.objectInfo.nChannels
                // FIX: Use Min(MIXBUFFER_SAMPLES, ...) to bound allocation like C++ does
                val reqSamples = Min(MIXBUFFER_SAMPLES, totalSamples / nChannels)

                // FIX: Added reqSamples == 0 check from dhewm3 C++ to prevent infinite loop
                // (can happen with stereo files and odd sample counts)
                if (reqSamples == 0) {
                    Common.common.DPrintf(
                        "idSampleDecoderLocal::DecodeOGG() reqSamples == 0\n  for %s ?!\n",
                        sample.name
                    )
                    readSamples += totalSamples
                    totalSamples = 0
                    break
                }

                val samples = PointerBuffer.allocateDirect(nChannels)
                for (i in 0 until nChannels) {
                    samples.put(i, BufferUtils.createFloatBuffer(reqSamples))
                }
                var ret = STBVorbis.stb_vorbis_get_samples_float(ogg, samples, reqSamples)
                if (ret == 0) {
                    // FIX: Added error recovery logic from dhewm3 C++
                    // Accept up to 5 "dropped" samples if there's no actual error
                    val stbVorbErr = STBVorbis.stb_vorbis_get_error(ogg)
                    if (stbVorbErr == STBVorbis.VORBIS__no_error && reqSamples < 5) {
                        ret = reqSamples // pretend decoding went ok
                        Common.common.DPrintf(
                            "idSampleDecoderLocal::DecodeOGG() IGNORING stb_vorbis_get_samples_float() dropping %d (%d) samples\n  for %s\n",
                            reqSamples, totalSamples, sample.name
                        )
                    } else {
                        Common.common.Warning(
                            "idSampleDecoderLocal::DecodeOGG() stb_vorbis_get_samples_float() %d (%d) samples\n  for %s failed: %s\n",
                            reqSamples, totalSamples, sample.name, getErrorMessage(stbVorbErr)
                        )
                        failed = true
                        break
                    }
                }
                if (ret < 0) {
                    failed = true
                    return 0
                }
                ret *= nChannels
                val samplesArray = Array(nChannels) { FloatArray(reqSamples) }
                for (i in 0 until nChannels) {
                    samples.getFloatBuffer(i, reqSamples)[samplesArray[i]]
                }
                SIMDProcessor!!.UpSampleOGGTo44kHz(
                    dest,
                    readSamples shl shift,
                    samplesArray,
                    ret,
                    sample.objectInfo.nSamplesPerSec,
                    nChannels
                )
                readSamples += ret
                totalSamples -= ret
            } while (totalSamples > 0)

            lastSampleOffset += readSamples
            return readSamples shl shift
        }

        init {
            file = idFile_Memory()
        }
    }
}

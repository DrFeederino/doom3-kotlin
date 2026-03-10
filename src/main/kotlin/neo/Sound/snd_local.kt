package neo.Sound

import neo.Sound.snd_cache.idSoundSample
import neo.Sound.snd_decoder.idSampleDecoderLocal
import neo.TempDump.SERiAL
import neo.framework.UsercmdGen
import neo.idlib.math.MIXBUFFER_SAMPLES
import neo.sys.win_snd.idAudioHardwareWIN32
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class snd_local {

    // demo sound commands
    enum class soundDemoCommand_t {
        SCMD_STATE,  // followed by a load game state
        SCMD_PLACE_LISTENER, SCMD_ALLOC_EMITTER, SCMD_FREE, SCMD_UPDATE, SCMD_START, SCMD_MODIFY, SCMD_STOP, SCMD_FADE
    }

    /*
     ===================================================================================

     General extended waveform format structure.
     Use this for all NON PCM formats.

     ===================================================================================
     */
    class waveformatex_s {
        var cbSize // The count in bytes of the size of extra information (after cbSize)
                = 0
        var nAvgBytesPerSec // for buffer estimation
                = 0
        var nBlockAlign // block size of data
                = 0
        var nChannels // number of channels (i.e. mono, stereo...)
                = 0
        var nSamplesPerSec // sample rate
                = 0
        var wBitsPerSample // Number of bits per sample of mono data
                = 0

        //byte offsets
        var wFormatTag // format type
                = 0

        companion object {
            const val SIZE = (java.lang.Short.SIZE
                    + java.lang.Short.SIZE
                    + Integer.SIZE
                    + Integer.SIZE
                    + java.lang.Short.SIZE
                    + java.lang.Short.SIZE
                    + java.lang.Short.SIZE)
            private const val BYTES = SIZE / java.lang.Byte.SIZE
        }
    }

    /* OLD general waveform format structure (information common to all formats) */
    class waveformat_s {
        var   /*dword*/nAvgBytesPerSec // for buffer estimation
                = 0
        var   /*word*/nBlockAlign // block size of data
                = 0
        var   /*word*/nChannels // number of channels (i.e. mono, stereo, etc.)
                = 0
        var   /*dword*/nSamplesPerSec // sample rate
                = 0

        //offsets
        var   /*word*/wFormatTag // format type
                = 0

        companion object {
            const val SIZE = (java.lang.Short.SIZE
                    + java.lang.Short.SIZE
                    + Integer.SIZE
                    + Integer.SIZE
                    + java.lang.Short.SIZE)
            private const val BYTES = SIZE / java.lang.Byte.SIZE
        }
    }

    // };
    /* specific waveform format structure for PCM data */
    class pcmwaveformat_s : SERiAL {
        var   /*word*/wBitsPerSample = 0
        var wf: waveformat_s = waveformat_s()
        override fun AllocBuffer(): ByteBuffer {
            return ByteBuffer.allocate(BYTES)
        }

        override fun Read(buffer: ByteBuffer) {
            buffer.order(ByteOrder.LITTLE_ENDIAN)
            wf = waveformat_s()
            wf.wFormatTag = java.lang.Short.toUnsignedInt(buffer.short)
            wf.nChannels = java.lang.Short.toUnsignedInt(buffer.short)
            wf.nSamplesPerSec = buffer.int
            wf.nAvgBytesPerSec = buffer.int
            wf.nBlockAlign = java.lang.Short.toUnsignedInt(buffer.short)
            wBitsPerSample = java.lang.Short.toUnsignedInt(buffer.short)
        }

        override fun Write(): ByteBuffer {
            val data = ByteBuffer.allocate(BYTES)
            data.order(ByteOrder.LITTLE_ENDIAN)
            data.putShort(wf.wFormatTag.toShort())
            data.putShort(wf.nChannels.toShort())
            data.putInt(wf.nSamplesPerSec)
            data.putInt(wf.nAvgBytesPerSec)
            data.putShort(wf.nBlockAlign.toShort())
            data.putShort(wBitsPerSample.toShort())
            data.flip()
            return data
        }

        companion object {
            private const val SIZE = (waveformat_s.SIZE
                    + java.lang.Short.SIZE)
            const val BYTES = SIZE / java.lang.Byte.SIZE
        }
    }

    // #ifndef mmioFOURCC
    // #define mmioFOURCC( ch0, ch1, ch2, ch3 )				\
    // ( (dword)(byte)(ch0) | ( (dword)(byte)(ch1) << 8 ) |	\
    // ( (dword)(byte)(ch2) << 16 ) | ( (dword)(byte)(ch3) << 24 ) )
    // #endif
    // #define fourcc_riff     mmioFOURCC('R', 'I', 'F', 'F')
    class waveformatextensible_s() {
        var Format: waveformatex_s

        //        union {
        //            word wValidBitsPerSample;       /* bits of precision  */
        //            word wSamplesPerBlock;          /* valid if wBitsPerSample==0*/
        //            word wReserved;                 /* If neither applies, set to zero*/
        //            } Samples;
        var   /*word*/Samples = 0

        //                                            // present in stream  */
        var SubFormat = 0
        var   /*dword*/dwChannelMask // which channels are */
                = 0

        companion object {
            private const val SIZE = (waveformatex_s.SIZE
                    + java.lang.Short.SIZE //union
                    + Integer.SIZE
                    + Integer.SIZE)
            private const val BYTES = SIZE / java.lang.Byte.SIZE
        }

        constructor(pcmWaveFormat: pcmwaveformat_s) : this() {
            Format.wFormatTag = pcmWaveFormat.wf.wFormatTag
            Format.nChannels = pcmWaveFormat.wf.nChannels
            Format.nSamplesPerSec = pcmWaveFormat.wf.nSamplesPerSec
            Format.nAvgBytesPerSec = pcmWaveFormat.wf.nAvgBytesPerSec
            Format.nBlockAlign = pcmWaveFormat.wf.nBlockAlign
            Format.wBitsPerSample = pcmWaveFormat.wBitsPerSample
        }

        init {
            Format = waveformatex_s()
        }
    }

    // typedef dword fourcc;
    /* RIFF chunk information data structure */
    internal class mminfo_s : SERiAL {
        var   /*fourcc*/ckid // chunk ID 
                : Long = 0
        var   /*dword*/cksize // chunk size
                = 0
        var   /*dword*/dwDataOffset // offset of data portion of chunk
                = 0
        var   /*fourcc*/fccType // form type or list type
                : Long = 0

        override fun AllocBuffer(): ByteBuffer {
            return ByteBuffer.allocate(BYTES)
        }

        override fun Read(buffer: ByteBuffer) {
            buffer.order(ByteOrder.LITTLE_ENDIAN)
            ckid = Integer.toUnsignedLong(buffer.int)
            cksize = buffer.int
            if (buffer.hasRemaining()) {
                fccType = Integer.toUnsignedLong(buffer.int)
            }
            if (buffer.hasRemaining()) {
                dwDataOffset = buffer.int
            }
        }

        override fun Write(): ByteBuffer {
            val data = ByteBuffer.allocate(BYTES)
            data.order(ByteOrder.LITTLE_ENDIAN)
            data.putInt(ckid.toInt())
            data.putInt(cksize)
            data.putInt(fccType.toInt())
            data.putInt(dwDataOffset)
            data.flip()
            return data
        }

        companion object {
            private const val SIZE = (Integer.SIZE
                    + Integer.SIZE
                    + Integer.SIZE
                    + Integer.SIZE)
            private const val BYTES = SIZE / java.lang.Byte.SIZE
        }
    }

    /*
     ===================================================================================

     Sound sample decoder.

     ===================================================================================
     */
    abstract class idSampleDecoder {
        // virtual					~idSampleDecoder() {}
        abstract fun Decode(sample: idSoundSample, sampleOffset44k: Int, sampleCount44k: Int, dest: FloatBuffer)
        abstract fun ClearDecoder()
        abstract fun GetSample(): idSoundSample?
        abstract fun GetLastDecodeTime(): Int

        companion object {
            fun Init() {
            }

            fun Shutdown() {
            }

            fun Alloc(): idSampleDecoder {
                val decoder = idSampleDecoderLocal()
                decoder.Clear()
                return decoder
            }

            fun Free(decoder: idSampleDecoder) {
                val localDecoder = decoder as idSampleDecoderLocal
                localDecoder.ClearDecoder()
            }

            @Deprecated("")
            fun GetNumUsedBlocks(): Int {
                return 0
            }

            @Deprecated("")
            fun GetUsedBlockMemory(): Int {
                return 0
            }
        }
    }

    /*
     ===================================================================================

     idAudioHardware

     ===================================================================================
     */
    abstract class idAudioHardware {
        //    virtual					~idAudioHardware();
        abstract fun Initialize(): Boolean
        abstract fun Lock(pDSLockedBuffer: Any, dwDSLockedBufferSize: Long): Boolean
        abstract fun Unlock(pDSLockedBuffer: Any,    /*dword*/dwDSLockedBufferSize: Long): Boolean
        abstract fun GetCurrentPosition(pdwCurrentWriteCursor: Long): Boolean

        // try to write as many sound samples to the device as possible without blocking and prepare for a possible new mixing call
        // returns wether there is *some* space for writing available
        abstract fun Flush(): Boolean
        abstract fun Write(flushing: Boolean)
        abstract fun GetNumberOfSpeakers(): Int
        abstract fun GetMixBufferSize(): Int
        abstract fun GetMixBuffer(): ShortArray

        companion object {
            fun Alloc(): idAudioHardware {
                return idAudioHardwareWIN32()
            }
        }
    }

    companion object {
        //
        //    static final idBlockAlloc<idSampleDecoderLocal> sampleDecoderAllocator = new idBlockAlloc<>(64);
        //    static final idDynamicBlockAlloc<Byte> decoderMemoryAllocator = new idDynamicBlockAlloc<>(1 << 20, 128);
        //
        const val MIN_OGGVORBIS_MEMORY = 768 * 1024

        /* flags for wFormatTag field of WAVEFORMAT */ // enum {
        const val WAVE_FORMAT_TAG_PCM = 1
        const val WAVE_FORMAT_TAG_OGG = 2

        //
        const val PRIMARYFREQ = 44100 // samples per second

        //
        const val ROOM_SLICES_IN_BUFFER = 10
        const val SND_EPSILON = 1.0f / 32768.0f // if volume is below this, it will always multiply to zero
        const val SOUND_DECODER_FREE_DELAY = 1000 * MIXBUFFER_SAMPLES / UsercmdGen.USERCMD_MSEC // four seconds
        const val SOUND_MAX_CHANNELS = 8
    }
}
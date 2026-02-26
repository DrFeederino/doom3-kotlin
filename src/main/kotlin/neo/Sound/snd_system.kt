package neo.Sound

import neo.Renderer.Cinematic.cinData_t
import neo.Renderer.RenderWorld.idRenderWorld
import neo.Sound.snd_cache.idSoundCache
import neo.Sound.snd_efxfile.idEFXFile
import neo.Sound.snd_emitter.SoundFX
import neo.Sound.snd_emitter.SoundFX_Comb
import neo.Sound.snd_emitter.SoundFX_Lowpass
import neo.Sound.snd_emitter.idSoundChannel
import neo.Sound.snd_local.idAudioHardware
import neo.Sound.snd_local.idSampleDecoder
import neo.Sound.snd_world.idSoundWorldLocal
import neo.Sound.snd_world.s_stats
import neo.Sound.sound.idSoundSystem
import neo.Sound.sound.idSoundWorld
import neo.Sound.sound.soundDecoderInfo_t
import neo.framework.BuildDefines
import neo.framework.CVarSystem
import neo.framework.CVarSystem.idCVar
import neo.framework.CmdSystem
import neo.framework.CmdSystem.cmdFunction_t
import neo.framework.CmdSystem.idCmdSystem.ArgCompletion_Integer
import neo.framework.CmdSystem.idCmdSystem.ArgCompletion_SoundName
import neo.framework.Common
import neo.framework.Common.MemInfo_t
import neo.idlib.CmdArgs
import neo.idlib.Text.Str.idStr
import neo.idlib.containers.List.idList
import neo.idlib.math.MIXBUFFER_SAMPLES
import neo.idlib.math.SIMDProcessor
import neo.idlib.math.idMath
import neo.sys.win_main
import neo.sys.win_main.Sys_EnterCriticalSection
import neo.sys.win_main.Sys_LeaveCriticalSection
import neo.sys.win_shared
import neo.sys.win_snd
import org.lwjgl.BufferUtils
import org.lwjgl.openal.AL
import org.lwjgl.openal.AL10
import org.lwjgl.openal.ALC
import org.lwjgl.openal.ALC10
import java.nio.ByteBuffer
import java.util.*
import kotlin.math.abs
import kotlin.math.pow

class snd_system {

    /*
     ===================================================================================

     idSoundSystemLocal

     ===================================================================================
     */
    class openalSource_t {
        var chan: idSoundChannel? = null
        var   /*ALuint*/handle = 0
        var inUse = false
        var looping = false
        var startTime = 0
        var stereo = false
    }

    class idSoundSystemLocal     //        static {
    //            if (ID_OPENAL) {//TODO: turn on the rest of our openAL extensions.
    //                // off by default. OpenAL DLL gets loaded on-demand. EDIT: not anymore.
    //                //s_libOpenAL = new idCVar("s_libOpenAL", "openal32.dll", CVAR_SOUND | CVAR_ARCHIVE, "OpenAL DLL name/path");
    //                s_useOpenAL = new idCVar("s_useOpenAL", "1", CVAR_SOUND | CVAR_BOOL | CVAR_ARCHIVE, "use OpenAL");
    //                s_useEAXReverb = new idCVar("s_useEAXReverb", "1", CVAR_SOUND | CVAR_BOOL | CVAR_ARCHIVE, "use EAX reverb");
    //                s_muteEAXReverb = new idCVar("s_muteEAXReverb", "0", CVAR_SOUND | CVAR_BOOL, "mute eax reverb");
    //                s_decompressionLimit = new idCVar("s_decompressionLimit", "6", CVAR_SOUND | CVAR_INTEGER | CVAR_ARCHIVE, "specifies maximum uncompressed sample length in seconds");
    //            } else {
    //                s_libOpenAL = new idCVar("s_libOpenAL", "openal32.dll", CVAR_SOUND | CVAR_ARCHIVE, "OpenAL is not supported in this build");
    //                s_useOpenAL = new idCVar("s_useOpenAL", "0", CVAR_SOUND | CVAR_BOOL | CVAR_ROM, "OpenAL is not supported in this build");
    //                s_useEAXReverb = new idCVar("s_useEAXReverb", "0", CVAR_SOUND | CVAR_BOOL | CVAR_ROM, "EAX not available in this build");
    //                s_muteEAXReverb = new idCVar("s_muteEAXReverb", "0", CVAR_SOUND | CVAR_BOOL | CVAR_ROM, "mute eax reverb");
    //                s_decompressionLimit = new idCVar("s_decompressionLimit", "6", CVAR_SOUND | CVAR_INTEGER | CVAR_ROM, "specifies maximum uncompressed sample length in seconds");
    //            }
    //        }
        : idSoundSystem() {
        companion object {
            val s_clipVolumes: idCVar = idCVar("s_clipVolumes", "1", CVarSystem.CVAR_SOUND or CVarSystem.CVAR_BOOL, "")
            val s_constantAmplitude: idCVar =
                idCVar("s_constantAmplitude", "-1", CVarSystem.CVAR_SOUND or CVarSystem.CVAR_FLOAT, "")
            val s_decompressionLimit: idCVar = idCVar(
                "s_decompressionLimit",
                "6",
                CVarSystem.CVAR_SOUND or CVarSystem.CVAR_INTEGER or CVarSystem.CVAR_ARCHIVE,
                "specifies maximum uncompressed sample length in seconds"
            )
            val s_doorDistanceAdd: idCVar = idCVar(
                "s_doorDistanceAdd",
                "150",
                CVarSystem.CVAR_SOUND or CVarSystem.CVAR_ARCHIVE or CVarSystem.CVAR_FLOAT,
                "reduce sound volume with this distance when going through a door"
            )
            val s_dotbias2: idCVar = idCVar("s_dotbias2", "1.1", CVarSystem.CVAR_SOUND or CVarSystem.CVAR_FLOAT, "")
            val s_dotbias6: idCVar = idCVar("s_dotbias6", "0.8", CVarSystem.CVAR_SOUND or CVarSystem.CVAR_FLOAT, "")
            val s_drawSounds: idCVar = idCVar(
                "s_drawSounds",
                "0",
                CVarSystem.CVAR_SOUND or CVarSystem.CVAR_INTEGER,
                "",
                0.0f,
                2.0f,
                ArgCompletion_Integer(0, 2)
            )
            val s_enviroSuitCutoffFreq: idCVar =
                idCVar("s_enviroSuitCutoffFreq", "2000", CVarSystem.CVAR_SOUND or CVarSystem.CVAR_FLOAT, "")
            val s_enviroSuitCutoffQ: idCVar =
                idCVar("s_enviroSuitCutoffQ", "2", CVarSystem.CVAR_SOUND or CVarSystem.CVAR_FLOAT, "")
            val s_enviroSuitVolumeScale: idCVar =
                idCVar("s_enviroSuitVolumeScale", "0.9", CVarSystem.CVAR_SOUND or CVarSystem.CVAR_FLOAT, "")
            val s_force22kHz: idCVar = idCVar("s_force22kHz", "0", CVarSystem.CVAR_SOUND or CVarSystem.CVAR_BOOL, "")
            val s_globalFraction: idCVar = idCVar(
                "s_globalFraction",
                "0.8",
                CVarSystem.CVAR_SOUND or CVarSystem.CVAR_ARCHIVE or CVarSystem.CVAR_FLOAT,
                "volume to all speakers when not spatialized"
            )

            //
            val s_libOpenAL: idCVar = idCVar(
                "s_libOpenAL",
                "openal32.dll",
                CVarSystem.CVAR_SOUND or CVarSystem.CVAR_ARCHIVE,
                "OpenAL DLL name/path"
            )
            val s_maxSoundsPerShader: idCVar = idCVar(
                "s_maxSoundsPerShader",
                "0",
                CVarSystem.CVAR_SOUND or CVarSystem.CVAR_ARCHIVE,
                "",
                0.0f,
                10.0f,
                ArgCompletion_Integer(0, 10)
            )
            val s_meterTopTime: idCVar = idCVar(
                "s_meterTopTime",
                "2000",
                CVarSystem.CVAR_SOUND or CVarSystem.CVAR_ARCHIVE or CVarSystem.CVAR_INTEGER,
                ""
            )
            val s_minVolume2: idCVar =
                idCVar("s_minVolume2", "0.25f", CVarSystem.CVAR_SOUND or CVarSystem.CVAR_FLOAT, "")
            val s_minVolume6: idCVar = idCVar("s_minVolume6", "0", CVarSystem.CVAR_SOUND or CVarSystem.CVAR_FLOAT, "")
            val s_muteEAXReverb: idCVar =
                idCVar("s_muteEAXReverb", "0", CVarSystem.CVAR_SOUND or CVarSystem.CVAR_BOOL, "mute eax reverb")

            //
            //
            var s_noSound: idCVar
            val s_numberOfSpeakers: idCVar = idCVar(
                "s_numberOfSpeakers",
                "2",
                CVarSystem.CVAR_SOUND or CVarSystem.CVAR_ARCHIVE,
                "number of speakers"
            )
            val s_playDefaultSound: idCVar = idCVar(
                "s_playDefaultSound",
                "1",
                CVarSystem.CVAR_SOUND or CVarSystem.CVAR_ARCHIVE or CVarSystem.CVAR_BOOL,
                "play a beep for missing sounds"
            )
            val s_quadraticFalloff: idCVar =
                idCVar("s_quadraticFalloff", "1", CVarSystem.CVAR_SOUND or CVarSystem.CVAR_BOOL, "")
            val s_realTimeDecoding: idCVar = idCVar(
                "s_realTimeDecoding",
                "1",
                CVarSystem.CVAR_SOUND or CVarSystem.CVAR_BOOL or CVarSystem.CVAR_INIT,
                ""
            )
            val s_reverbFeedback: idCVar =
                idCVar("s_reverbFeedback", "0.333", CVarSystem.CVAR_SOUND or CVarSystem.CVAR_FLOAT, "")
            val s_reverbTime: idCVar =
                idCVar("s_reverbTime", "1000", CVarSystem.CVAR_SOUND or CVarSystem.CVAR_FLOAT, "")
            val s_reverse: idCVar =
                idCVar("s_reverse", "0", CVarSystem.CVAR_SOUND or CVarSystem.CVAR_ARCHIVE or CVarSystem.CVAR_BOOL, "")
            val s_showLevelMeter: idCVar =
                idCVar("s_showLevelMeter", "0", CVarSystem.CVAR_SOUND or CVarSystem.CVAR_BOOL, "")
            val s_showStartSound: idCVar =
                idCVar("s_showStartSound", "0", CVarSystem.CVAR_SOUND or CVarSystem.CVAR_BOOL, "")
            val s_singleEmitter: idCVar = idCVar(
                "s_singleEmitter",
                "0",
                CVarSystem.CVAR_SOUND or CVarSystem.CVAR_INTEGER,
                "mute all sounds but this emitter"
            )
            val s_skipHelltimeFX: idCVar =
                idCVar("s_skipHelltimeFX", "0", CVarSystem.CVAR_SOUND or CVarSystem.CVAR_BOOL, "")

            //
            val s_slowAttenuate: idCVar = idCVar(
                "s_slowAttenuate",
                "1",
                CVarSystem.CVAR_SOUND or CVarSystem.CVAR_BOOL,
                "slowmo sounds attenuate over shorted distance"
            )
            val s_spatializationDecay: idCVar = idCVar(
                "s_spatializationDecay",
                "2",
                CVarSystem.CVAR_SOUND or CVarSystem.CVAR_ARCHIVE or CVarSystem.CVAR_FLOAT,
                ""
            )
            val s_subFraction: idCVar = idCVar(
                "s_subFraction",
                "0.75",
                CVarSystem.CVAR_SOUND or CVarSystem.CVAR_ARCHIVE or CVarSystem.CVAR_FLOAT,
                "volume to subwoofer in 5.1"
            )
            val s_useEAXReverb: idCVar = idCVar(
                "s_useEAXReverb",
                "1",
                CVarSystem.CVAR_SOUND or CVarSystem.CVAR_BOOL or CVarSystem.CVAR_ARCHIVE,
                "use EAX reverb"
            )
            val s_useOcclusion: idCVar =
                idCVar("s_useOcclusion", "1", CVarSystem.CVAR_SOUND or CVarSystem.CVAR_BOOL, "")
            val s_useOpenAL: idCVar = idCVar(
                "s_useOpenAL",
                "1",
                CVarSystem.CVAR_SOUND or CVarSystem.CVAR_BOOL or CVarSystem.CVAR_ARCHIVE,
                "use OpenAL"
            )
            val s_volume: idCVar = idCVar(
                "s_volume_dB",
                "0",
                CVarSystem.CVAR_SOUND or CVarSystem.CVAR_ARCHIVE or CVarSystem.CVAR_FLOAT,
                "volume in dB"
            )

            // mark available during initialization, or through an explicit test
            var EAXAvailable = -1
            var useEAXReverb = true

            // latches
            var useOpenAL = false

            init {
                if (BuildDefines.ID_DEDICATED) {
                    s_noSound = idCVar(
                        "s_noSound",
                        "1",
                        CVarSystem.CVAR_SOUND or CVarSystem.CVAR_BOOL or CVarSystem.CVAR_ROM,
                        ""
                    )
                } else {
                    s_noSound = idCVar(
                        "s_noSound",
                        "0",
                        CVarSystem.CVAR_SOUND or CVarSystem.CVAR_BOOL or CVarSystem.CVAR_NOCHEAT,
                        ""
                    )
                }
            }
        }

        var CurrentSoundTime // set by the async thread and only used by the main thread
                = 0

        //        public boolean alEAXSet;
        //        public boolean alEAXGet;
        //        public boolean alEAXSetBufferMode;
        //        public boolean alEAXGetBufferMode;
        var EFXDatabase: idEFXFile = idEFXFile()
        var buffers // statistics
                = 0

        //
        var currentSoundWorld // the one to mix each async tic
                : idSoundWorldLocal? = null
        var efxloaded = false
        var finalMixBuffer // points inside realAccum at a 16 byte aligned boundary
                : FloatArray = FloatArray(6 * MIXBUFFER_SAMPLES + 16)

        //
        val fxList: idList<SoundFX> = idList()

        //
        var graph: IntArray? = null

        //
        var isInitialized = false

        //
        var meterTops: IntArray = IntArray(256)
        var meterTopsTime: IntArray = IntArray(256)
        var muted = false

        //
        /*unsigned*/  var nextWriteBlock = 0

        //
        var olddwCurrentWritePos // statistics
                = 0
        var openalContext: Long = 0

        //
        var openalDevice: Long = 0
        var   /*ALsizei*/openalSourceCount = 0
        var openalSources: Array<openalSource_t> = Array(256) { openalSource_t() }

        //
        var realAccum: FloatArray = FloatArray(6 * MIXBUFFER_SAMPLES + 16)
        var shutdown = false
        var snd_audio_hw: idAudioHardware? = null
        var soundCache: idSoundCache? = null

        //
        var soundStats: s_stats = s_stats() // NOTE: updated throughout the code, not displayed anywhere

        //
        var volumesDB: FloatArray = FloatArray(1200) // dB to float volume conversion

        // all non-hardware initialization
        /*
         ===============
         idSoundSystemLocal::Init

         initialize the sound system
         ===============
         */
        override fun Init() {
            Common.common.Printf("----- Initializing Sound System ------\n")
            isInitialized = false
            muted = false
            shutdown = false
            currentSoundWorld = null
            soundCache = null
            olddwCurrentWritePos = 0
            buffers = 0
            CurrentSoundTime = 0
            nextWriteBlock = -0x1
            meterTops = IntArray(meterTops.size)
            meterTopsTime = IntArray(meterTopsTime.size)
            for (i in -600..599) {
                val pt = i * 0.1f
                volumesDB[i + 600] = 2.0f.pow((pt * (1.0f / 6.0f)))
            }

            // make a 16 byte aligned finalMixBuffer
            finalMixBuffer = realAccum //(float[]) ((((int) realAccum) + 15) & ~15);
            graph = null
            if (!s_noSound.GetBool()) {
                idSampleDecoder.Init()
                soundCache = idSoundCache()
            }

            // set up openal device and context
            Common.common.StartupVariable("s_useOpenAL", true)
            Common.common.StartupVariable("s_useEAXReverb", true)
            if (s_useOpenAL.GetBool() && s_useEAXReverb.GetBool()) {
                if (!win_snd.Sys_LoadOpenAL()) {
                    s_useOpenAL.SetBool(false)
                } else {
                    Common.common.Printf("Setup OpenAL device and context... ")
                    openalDevice = ALC10.alcOpenDevice(null as ByteBuffer?)
                    openalContext = ALC10.alcCreateContext(openalDevice, null as IntArray?)
                    ALC10.alcMakeContextCurrent(openalContext)
                    val alcCapabilities = ALC.createCapabilities(openalDevice)
                    AL.createCapabilities(alcCapabilities)
                    Common.common.Printf("Done.\n")

                    // try to obtain EAX extensions
                    if (s_useEAXReverb.GetBool() && AL10.alIsExtensionPresent( /*ID_ALCHAR*/"EAX4.0f")) {
                        s_useOpenAL.SetBool(true) // EAX presence causes AL enable
                        //                        alEAXSet = true;//(EAXSet) alGetProcAddress(/*ID_ALCHAR*/"EAXSet");
//                        alEAXGet = true;//(EAXGet) alGetProcAddress(/*ID_ALCHAR*/"EAXGet");
                        Common.common.Printf("OpenAL: found EAX 4.0f extension\n")
                    } else {
                        Common.common.Printf("OpenAL: EAX 4.0f extension not found\n")
                        s_useEAXReverb.SetBool(false)
                        //                        alEAXSet = false;//(EAXSet) null;
//                        alEAXGet = false;//(EAXGet) null;
                    }

                    // try to obtain EAX-RAM extension - not required for operation
//                    if (alIsExtensionPresent(/*ID_ALCHAR*/"EAX-RAM")) {
//                        alEAXSetBufferMode = true;//(EAXSetBufferMode) alGetProcAddress(/*ID_ALCHAR*/"EAXSetBufferMode");
//                        alEAXGetBufferMode = true;//(EAXGetBufferMode) alGetProcAddress(/*ID_ALCHAR*/"EAXGetBufferMode");
//                        common.Printf("OpenAL: found EAX-RAM extension, %dkB\\%dkB\n", alGetInteger(alGetEnumValue(/*ID_ALCHAR*/"AL_EAX_RAM_FREE")) / 1024, alGetInteger(alGetEnumValue(/*ID_ALCHAR*/"AL_EAX_RAM_SIZE")) / 1024);
//                    } else {
//                        alEAXSetBufferMode = false;//(EAXSetBufferMode) null;
//                        alEAXGetBufferMode = false;//(EAXGetBufferMode) null;
//                        common.Printf("OpenAL: no EAX-RAM extension\n");
//                    }
                    if (!s_useOpenAL.GetBool()) {
                        Common.common.Printf("OpenAL: disabling ( no EAX ). Using legacy mixer.\n")
                        ALC10.alcMakeContextCurrent(openalContext)
                        ALC10.alcDestroyContext(openalContext)
                        openalContext = 0
                        ALC10.alcCloseDevice(openalDevice)
                        openalDevice = 0
                    } else {
                        var   /*ALuint*/handle: Int
                        openalSourceCount = 0
                        while (openalSourceCount < 256) {
                            AL10.alGetError()
                            handle = AL10.alGenSources() //alGenSources(1, handle);
                            if (AL10.alGetError() != AL10.AL_NO_ERROR) {
                                break
                            } else {
                                // store in source array
                                openalSources[openalSourceCount] = openalSource_t()
                                openalSources[openalSourceCount].handle = handle
                                openalSources[openalSourceCount].startTime = 0
                                openalSources[openalSourceCount].chan = null
                                openalSources[openalSourceCount].inUse = false
                                openalSources[openalSourceCount].looping = false

                                // initialise sources
                                AL10.alSourcef(handle, AL10.AL_ROLLOFF_FACTOR, 0.0f)

                                // found one source
                                openalSourceCount++
                            }
                        }
                        Common.common.Printf(
                            "OpenAL: found %s\n",
                            ALC10.alcGetString(openalDevice, ALC10.ALC_DEVICE_SPECIFIER)!!
                        )
                        Common.common.Printf("OpenAL: found %d hardware voices\n", openalSourceCount)

                        // adjust source count to allow for at least eight stereo sounds to play
                        openalSourceCount -= 8
                        EAXAvailable = 1
                    }
                }
            }
            useOpenAL = s_useOpenAL.GetBool()
            useEAXReverb = s_useEAXReverb.GetBool()
            CmdSystem.cmdSystem.AddCommand(
                "listSounds",
                ListSounds_f.INSTANCE,
                CmdSystem.CMD_FL_SOUND,
                "lists all sounds"
            )
            CmdSystem.cmdSystem.AddCommand(
                "listSoundDecoders",
                ListSoundDecoders_f.INSTANCE,
                CmdSystem.CMD_FL_SOUND,
                "list active sound decoders"
            )
            CmdSystem.cmdSystem.AddCommand(
                "reloadSounds",
                SoundReloadSounds_f.INSTANCE,
                CmdSystem.CMD_FL_SOUND or CmdSystem.CMD_FL_CHEAT,
                "reloads all sounds"
            )
            CmdSystem.cmdSystem.AddCommand(
                "testSound",
                TestSound_f.INSTANCE,
                CmdSystem.CMD_FL_SOUND or CmdSystem.CMD_FL_CHEAT,
                "tests a sound",
                ArgCompletion_SoundName.getInstance()
            )
            CmdSystem.cmdSystem.AddCommand(
                "s_restart",
                SoundSystemRestart_f.INSTANCE,
                CmdSystem.CMD_FL_SOUND,
                "restarts the sound system"
            )
            Common.common.Printf("sound system initialized.\n")
            Common.common.Printf("--------------------------------------\n")
        }

        // shutdown routine
        override fun Shutdown() {
            ShutdownHW()

            // EAX or not, the list needs to be cleared
            EFXDatabase.Clear()

            // destroy openal sources
            if (useOpenAL) {
                efxloaded = false

                // adjust source count back up to allow for freeing of all resources
                openalSourceCount += 8
                for ( /*ALsizei*/source in openalSources) {
                    // stop source
                    if (source != null) {
                        AL10.alSourceStop(source.handle)
                        AL10.alSourcei(source.handle, AL10.AL_BUFFER, 0)
                        AL10.alDeleteSources(source.handle)

                        // clear entry in source array
                        source.handle = 0
                        source.startTime = 0
                        source.chan = null
                        source.inUse = false
                        source.looping = false
                    }
                }
            }

            // destroy all the sounds (hardware buffers as well)
//	delete soundCache;
            soundCache = null

            // destroy openal device and context
            if (useOpenAL) {
                ALC10.alcMakeContextCurrent(openalContext)
                ALC10.alcDestroyContext(openalContext)
                openalContext = 0
                ALC10.alcCloseDevice(openalDevice)
                openalDevice = 0
            }
            win_snd.Sys_FreeOpenAL()
            idSampleDecoder.Shutdown()
        }

        override fun ClearBuffer() {

            // check to make sure hardware actually exists
            if (snd_audio_hw == null) {
                return
            }
            val fBlock = intArrayOf(0)
            val   /*ulong*/fBlockLen: Int = 0

            //TODO:see what this block does.
//            if (!snd_audio_hw.Lock( /*(void **)*/fBlock, fBlockLen)) {
//                return;
//            }
            if (fBlock[0] != 0) {
//                SIMDProcessor.Memset(fBlock, 0, fBlockLen);
                Arrays.fill(fBlock, 0, fBlockLen, 0)
                //                snd_audio_hw.Unlock(fBlock, fBlockLen);
            }
        }

        // sound is attached to the window, and must be recreated when the window is changed
        override fun ShutdownHW(): Boolean {
            if (!isInitialized) {
                return false
            }
            shutdown = true // don't do anything at AsyncUpdate() time
            win_main.Sys_Sleep(100) // sleep long enough to make sure any async sound talking to hardware has returned
            Common.common.Printf("Shutting down sound hardware\n")

//	delete snd_audio_hw;
            snd_audio_hw = null
            isInitialized = false
            if (graph != null) {
//                Mem_Free(graph);//TODO:remove all this memory crap.
                graph = null
            }
            return true
        }

        override fun InitHW(): Boolean {
            if (s_noSound.GetBool()) {
                return false
            }

//	delete snd_audio_hw;
            snd_audio_hw = idAudioHardware.Alloc()
            if (snd_audio_hw == null) {
                return false
            }
            if (!useOpenAL) {
                if (!snd_audio_hw!!.Initialize()) {
                    snd_audio_hw = null
                    return false
                }
                if (snd_audio_hw!!.GetNumberOfSpeakers() == 0) {
                    return false
                }
                s_numberOfSpeakers.SetInteger(snd_audio_hw!!.GetNumberOfSpeakers())
            }
            isInitialized = true
            shutdown = false
            return true
        }

        /*
         ===================
         idSoundSystemLocal::AsyncUpdate
         called from async sound thread when com_asyncSound == 1 ( Windows )
         ===================
         */
        // async loop, called at 60Hz
        override fun AsyncUpdate(time: Int): Int {
            if (!isInitialized || shutdown || snd_audio_hw == null) {
                return 0
            }
            var   /*ulong*/dwCurrentWritePos: Long = 0
            val   /*dword*/dwCurrentBlock: Int

            // If not using openal, get actual playback position from sound hardware
            if (useOpenAL) {
                // here we do it in samples ( overflows in 27 hours or so )
                dwCurrentWritePos =
                    idMath.Ftol(win_shared.Sys_Milliseconds() * 44.1f) % (MIXBUFFER_SAMPLES * snd_local.ROOM_SLICES_IN_BUFFER)
                dwCurrentBlock = (dwCurrentWritePos / MIXBUFFER_SAMPLES).toInt()
            } else {
                // and here in bytes
                // get the current byte position in the buffer where the sound hardware is currently reading
                if (!snd_audio_hw!!.GetCurrentPosition(dwCurrentWritePos)) {
                    return 0
                }
                // mixBufferSize is in bytes
                dwCurrentBlock = (dwCurrentWritePos / snd_audio_hw!!.GetMixBufferSize()).toInt()
            }
            if (nextWriteBlock == -0x1) {
                nextWriteBlock = dwCurrentBlock
            }
            if (dwCurrentBlock != nextWriteBlock) {
                return 0
            }

            // lock the buffer so we can actually write to it
            val fBlock: ShortArray = ShortArray(1)
            val   /*ulong*/fBlockLen: Long = 0
            if (!useOpenAL) {
                snd_audio_hw!!.Lock( /*(void **)*/fBlock, fBlockLen)
                if (null == fBlock || fBlock[0] == 0.toShort()) {
                    return 0
                }
            }
            var j: Int
            soundStats.runs++
            soundStats.activeSounds = 0
            val numSpeakers = snd_audio_hw!!.GetNumberOfSpeakers()
            nextWriteBlock++
            nextWriteBlock %= snd_local.ROOM_SLICES_IN_BUFFER
            val newPosition = nextWriteBlock * MIXBUFFER_SAMPLES
            if (newPosition < olddwCurrentWritePos) {
                buffers++ // buffer wrapped
            }

            // nextWriteSample is in multi-channel samples inside the buffer
            val nextWriteSamples = nextWriteBlock * MIXBUFFER_SAMPLES
            olddwCurrentWritePos = newPosition

            // newSoundTime is in multi-channel samples since the sound system was started
            val newSoundTime = buffers * MIXBUFFER_SAMPLES * snd_local.ROOM_SLICES_IN_BUFFER + nextWriteSamples

            // check for impending overflow
            // FIXME: we don't handle sound wrap-around correctly yet
            if (newSoundTime > 0x6fffffff) {
                buffers = 0
            }
            if (newSoundTime - CurrentSoundTime > MIXBUFFER_SAMPLES) {
                soundStats.missedWindow++
            }
            if (useOpenAL) {
                // enable audio hardware caching
                ALC10.alcSuspendContext(openalContext)
            } else {
                // clear the buffer for all the mixing output
//                SIMDProcessor.Memset(finalMixBuffer, 0, MIXBUFFER_SAMPLES * sizeof(float) * numSpeakers);
                Arrays.fill(finalMixBuffer, 0, 0, (MIXBUFFER_SAMPLES * numSpeakers).toFloat())
            }

            // let the active sound world mix all the channels in unless muted or avi demo recording
            if (!muted && currentSoundWorld != null && null == currentSoundWorld!!.fpa[0]) {
                currentSoundWorld!!.MixLoop(newSoundTime, numSpeakers, finalMixBuffer)
            }
            if (useOpenAL) {
                // disable audio hardware caching (this updates ALL settings since last alcSuspendContext)
                ALC10.alcProcessContext(openalContext)
            } else {
//                short[] dest = fBlock + nextWriteSamples * numSpeakers;
                val dest = nextWriteSamples * numSpeakers
                SIMDProcessor!!.MixedSoundToSamples(
                    fBlock,
                    dest,
                    finalMixBuffer,
                    MIXBUFFER_SAMPLES * numSpeakers
                )

                // allow swapping the left / right speaker channels for people with miswired systems
                if (numSpeakers == 2 && s_reverse.GetBool()) {
                    j = 0
                    while (j < MIXBUFFER_SAMPLES) {
                        val temp = fBlock[dest + j * 2]
                        fBlock[dest + j * 2] = fBlock[dest + j * 2 + 1]
                        fBlock[dest + j * 2 + 1] = temp
                        j++
                    }
                }
                snd_audio_hw!!.Unlock(fBlock, fBlockLen)
            }
            CurrentSoundTime = newSoundTime
            soundStats.timeinprocess = win_shared.Sys_Milliseconds() - time
            return soundStats.timeinprocess
        }

        /*
         ===================
         idSoundSystemLocal::AsyncUpdateWrite
         sound output using a write API. all the scheduling based on time
         we mix MIXBUFFER_SAMPLES at a time, but we feed the audio device with smaller chunks (and more often)
         called by the sound thread when com_asyncSound is 3 ( Linux )
         ===================
         */
        // async loop, when the sound driver uses a write strategy
        override fun AsyncUpdateWrite(inTime: Int): Int {
            if (!isInitialized || shutdown || snd_audio_hw == null) {
                return 0
            }
            if (!useOpenAL) {
                snd_audio_hw!!.Flush()
            }
            val   /*unsigned int*/dwCurrentBlock = (inTime * 44.1 / MIXBUFFER_SAMPLES).toLong()
            if (nextWriteBlock == -0x1) {
                nextWriteBlock = dwCurrentBlock.toInt()
            }
            if (dwCurrentBlock < nextWriteBlock) {
                return 0
            }
            if (nextWriteBlock.toLong() != dwCurrentBlock) {
                win_main.Sys_Printf("missed %d sound updates\n", dwCurrentBlock - nextWriteBlock)
            }
            val sampleTime = (dwCurrentBlock * MIXBUFFER_SAMPLES).toInt()
            val numSpeakers = snd_audio_hw!!.GetNumberOfSpeakers()
            if (useOpenAL) {
                // enable audio hardware caching
                ALC10.alcSuspendContext(openalContext)
            } else {
                // clear the buffer for all the mixing output
//                SIMDProcessor.Memset(finalMixBuffer, 0, MIXBUFFER_SAMPLES * sizeof(float) * numSpeakers);
                Arrays.fill(finalMixBuffer, 0.0f)
            }

            // let the active sound world mix all the channels in unless muted or avi demo recording
            if (!muted && currentSoundWorld != null && null == currentSoundWorld!!.fpa[0]) {
                currentSoundWorld!!.MixLoop(sampleTime, numSpeakers, finalMixBuffer)
            }
            if (useOpenAL) {
                // disable audio hardware caching (this updates ALL settings since last alcSuspendContext)
                ALC10.alcProcessContext(openalContext)
            } else {
                val dest = snd_audio_hw!!.GetMixBuffer()
                SIMDProcessor!!.MixedSoundToSamples(dest, finalMixBuffer, MIXBUFFER_SAMPLES * numSpeakers)

                // allow swapping the left / right speaker channels for people with miswired systems
                if (numSpeakers == 2 && s_reverse.GetBool()) {
                    var j: Int
                    j = 0
                    while (j < MIXBUFFER_SAMPLES) {
                        val temp = dest[j * 2]
                        dest[j * 2] = dest[j * 2 + 1]
                        dest[j * 2 + 1] = temp
                        j++
                    }
                }
                snd_audio_hw!!.Write(false)
            }

            // only move to the next block if the write was successful
            nextWriteBlock = (dwCurrentBlock + 1).toInt()
            CurrentSoundTime = sampleTime
            return win_shared.Sys_Milliseconds() - inTime
        }

        /*
         ===================
         idSoundSystemLocal::AsyncMix
         Mac OSX version. The system uses it's own thread and an IOProc callback
         ===================
         */
        // direct mixing called from the sound driver thread for OSes that support it
        override fun AsyncMix(soundTime: Int, mixBuffer: FloatArray): Int {
            val inTime: Int
            val numSpeakers: Int
            if (!isInitialized || shutdown || snd_audio_hw == null) {
                return 0
            }
            inTime = win_shared.Sys_Milliseconds()
            numSpeakers = snd_audio_hw!!.GetNumberOfSpeakers()

            // let the active sound world mix all the channels in unless muted or avi demo recording
            if (!muted && currentSoundWorld != null && null == currentSoundWorld!!.fpa[0]) {
                currentSoundWorld!!.MixLoop(soundTime, numSpeakers, mixBuffer)
            }
            CurrentSoundTime = soundTime
            return win_shared.Sys_Milliseconds() - inTime
        }

        override fun SetMute(muteOn: Boolean) {
            muted = muteOn
        }

        override fun ImageForTime(milliseconds: Int, waveform: Boolean): cinData_t {
            val ret = cinData_t()
            var i: Int
            var j: Int
            if (!isInitialized || snd_audio_hw == null) {
//		memset( &ret, 0, sizeof( ret ) );
                return ret
            }
            Sys_EnterCriticalSection()
            if (null == graph) {
                graph = IntArray(256 * 128 * 4) // Mem_Alloc(256 * 128 * 4);
            }
            //	memset( graph, 0, 256*128 * 4 );
            val accum = finalMixBuffer // unfortunately, these are already clamped
            val time = win_shared.Sys_Milliseconds()
            val numSpeakers = snd_audio_hw!!.GetNumberOfSpeakers()
            if (!waveform) {
                j = 0
                while (j < numSpeakers) {
                    var meter = 0
                    i = 0
                    while (i < MIXBUFFER_SAMPLES) {
                        val result = abs(accum[i * numSpeakers + j])
                        if (result > meter) {
                            meter = result.toInt()
                        }
                        i++
                    }
                    meter /= 256 // 32768 becomes 128
                    if (meter > 128) {
                        meter = 128
                    }
                    var offset: Int
                    var xsize: Int
                    if (numSpeakers == 6) {
                        offset = j * 40
                        xsize = 20
                    } else {
                        offset = j * 128
                        xsize = 63
                    }
                    var x: Int
                    var y: Int
                    val   /*dword*/color = -0xff0100
                    y = 0
                    while (y < 128) {
                        x = 0
                        while (x < xsize) {
                            graph!![(127 - y) * 256 + offset + x] = color
                            x++
                        }
                        // #if 0
                        // if ( y == 80 ) {
                        // color = 0xff00ffff;
                        // } else if ( y == 112 ) {
                        // color = 0xff0000ff;
                        // }
// #endif
                        if (y > meter) {
                            break
                        }
                        y++
                    }
                    if (meter > meterTops[j]) {
                        meterTops[j] = meter
                        meterTopsTime[j] = (time + s_meterTopTime.GetInteger())
                    } else if (time > meterTopsTime[j] && meterTops[j] > 0) {
                        meterTops[j]--
                        if (meterTops[j] != 0) {
                            meterTops[j]--
                        }
                    }
                    j++
                }
                j = 0
                while (j < numSpeakers) {
                    val meter = meterTops[j]
                    var offset: Int
                    var xsize: Int
                    if (numSpeakers == 6) {
                        offset = j * 40
                        xsize = 20
                    } else {
                        offset = j * 128
                        xsize = 63
                    }
                    var x: Int
                    var y: Int
                    var   /*dword*/color: Int
                    color = if (meter <= 80) {
                        -0xff8100
                    } else if (meter <= 112) {
                        -0xff8081
                    } else {
                        -0xffff81
                    }
                    y = meter
                    while (y < 128 && y < meter + 4) {
                        x = 0
                        while (x < xsize) {
                            graph!![(127 - y) * 256 + offset + x] = color
                            x++
                        }
                        y++
                    }
                    j++
                }
            } else {
                val colors = intArrayOf(-0xff8100, -0xff8081, -0xffff81, -0xff0100, -0xff0001, -0xffff01)
                j = 0
                while (j < numSpeakers) {
                    var xx = 0
                    var fmeter: Float
                    val step = MIXBUFFER_SAMPLES / 256
                    i = 0
                    while (i < MIXBUFFER_SAMPLES) {
                        fmeter = 0.0f
                        for (x in 0 until step) {
                            var result = accum[(i + x) * numSpeakers + j]
                            result = result / 32768.0f
                            fmeter += result
                        }
                        fmeter /= 4.0f
                        if (fmeter < -1.0f) {
                            fmeter = -1.0f
                        } else if (fmeter > 1.0f) {
                            fmeter = 1.0f
                        }
                        var meter = (fmeter * 63.0f).toInt()
                        graph!![(meter + 64) * 256 + xx] = colors[j]
                        if (meter < 0) {
                            meter = -meter
                        }
                        if (meter > meterTops[xx]) {
                            meterTops[xx] = meter
                            meterTopsTime[xx] = (time + 100)
                        } else if (time > meterTopsTime[xx] && meterTops[xx] > 0) {
                            meterTops[xx]--
                            if (meterTops[xx] != 0) {
                                meterTops[xx]--
                            }
                        }
                        xx++
                        i += step
                    }
                    j++
                }
                i = 0
                while (i < 256) {
                    val meter = meterTops[i]
                    for (y in -meter until meter) {
                        graph!![(y + 64) * 256 + i] = colors[j]
                    }
                    i++
                }
            }
            ret.imageHeight = 128
            ret.imageWidth = 256
            val image = BufferUtils.createByteBuffer(graph!!.size * 4)
            image.asIntBuffer().put(graph)
            ret.image = image
            Sys_LeaveCriticalSection()
            return ret
        }

        override fun GetSoundDecoderInfo(index: Int, decoderInfo: soundDecoderInfo_t): Int {
            var i: Int
            var j: Int
            val firstEmitter: Int
            var firstChannel: Int
            val sw = soundSystemLocal.currentSoundWorld
            if (index < 0) {
                firstEmitter = 0
                firstChannel = 0
            } else {
                firstEmitter = index / snd_local.SOUND_MAX_CHANNELS
                firstChannel = index - firstEmitter * snd_local.SOUND_MAX_CHANNELS + 1
            }
            i = firstEmitter
            while (i < sw!!.emitters.Num()) {
                val sound = sw.emitters[i]
                if (null == sound) {
                    i++
                    continue
                }

                // run through all the channels
                j = firstChannel
                while (j < snd_local.SOUND_MAX_CHANNELS) {
                    val chan = sound.channels[j]
                    if (chan.decoder == null) {
                        j++
                        continue
                    }
                    val sample = chan.decoder!!.GetSample()
                    if (sample == null) {
                        j++
                        continue
                    }
                    decoderInfo.name.set(sample.name)
                    decoderInfo.format.set(if (sample.objectInfo.wFormatTag == snd_local.WAVE_FORMAT_TAG_OGG) "OGG" else "WAV")
                    decoderInfo.numChannels = sample.objectInfo.nChannels
                    decoderInfo.numSamplesPerSecond = sample.objectInfo.nSamplesPerSec.toLong()
                    decoderInfo.num44kHzSamples = sample.LengthIn44kHzSamples()
                    decoderInfo.numBytes = sample.objectMemSize
                    decoderInfo.looping = chan.parms!!.soundShaderFlags and snd_shader.SSF_LOOPING != 0
                    decoderInfo.lastVolume = chan.lastVolume
                    decoderInfo.start44kHzTime = chan.trigger44kHzTime
                    decoderInfo.current44kHzTime = soundSystemLocal.GetCurrent44kHzTime()
                    return i * snd_local.SOUND_MAX_CHANNELS + j
                    j++
                }
                firstChannel = 0
                i++
            }
            return -1
        }

        // if rw == NULL, no portal occlusion or rendered debugging is available
        override fun AllocSoundWorld(rw: idRenderWorld): idSoundWorld {
            val local = idSoundWorldLocal()
            local.Init(rw)
            return local
        }

        /*
         ===================
         idSoundSystemLocal::SetPlayingSoundWorld

         specifying NULL will cause silence to be played
         ===================
         */
        // specifying NULL will cause silence to be played
        override fun SetPlayingSoundWorld(soundWorld: idSoundWorld?) {
            currentSoundWorld = soundWorld as idSoundWorldLocal?
        }

        // some tools, like the sound dialog, may be used in both the game and the editor
        // This can return NULL, so check!
        override fun GetPlayingSoundWorld(): idSoundWorld? {
            return currentSoundWorld
        }

        override fun BeginLevelLoad() {
            if (!isInitialized) {
                return
            }
            soundCache!!.BeginLevelLoad()
            if (efxloaded) {
                EFXDatabase.UnloadFile()
                efxloaded = false
            }
        }

        override fun EndLevelLoad(mapString: String) {
            if (!isInitialized) {
                return
            }
            soundCache!!.EndLevelLoad()
            val efxname = idStr("efxs/")
            val mapname = idStr(mapString)
            mapname.SetFileExtension(".efx")
            mapname.StripPath()
            efxname.plusAssign(mapname)
            efxloaded = EFXDatabase.LoadFile(efxname.toString())
            if (efxloaded) {
                Common.common.Printf("sound: found %s\n", efxname)
            } else {
                Common.common.Printf("sound: missing %s\n", efxname)
            }
        }

        override fun PrintMemInfo(mi: MemInfo_t) {
            soundCache!!.PrintMemInfo(mi)
        }

        override fun IsEAXAvailable(): Int {
//#if !ID_OPENAL
            return -1
            //#else
//	ALCdevice	*device;
//	ALCcontext	*context;
//
//	if ( EAXAvailable != -1 ) {
//		return EAXAvailable;
//	}
//
//	if ( !Sys_LoadOpenAL() ) {
//		EAXAvailable = 2;
//		return 2;
//	}
//	// when dynamically loading the OpenAL subsystem, we need to get a context before alIsExtensionPresent would work
//	device = alcOpenDevice( NULL );
//	context = alcCreateContext( device, NULL );
//	alcMakeContextCurrent( context );
//	if ( alIsExtensionPresent( ID_ALCHAR "EAX4.0f" ) ) {
//		alcMakeContextCurrent( NULL );
//		alcDestroyContext( context );
//		alcCloseDevice( device );
//		EAXAvailable = 1;
//		return 1;
//	}
//	alcMakeContextCurrent( NULL );
//	alcDestroyContext( context );
//	alcCloseDevice( device );
//	EAXAvailable = 0;
//	return 0;
//#endif
        }

        //-------------------------
        fun GetCurrent44kHzTime(): Int {
            return if (snd_audio_hw != null) {
                CurrentSoundTime
            } else {
                // NOTE: this would overflow 31bits within about 1h20 ( not that important since we get a snd_audio_hw right away pbly )
                //return ( ( Sys_Milliseconds()*441 ) / 10 ) * 4;
                idMath.FtoiFast(win_shared.Sys_Milliseconds() * 176.4f)
            }
        }

        fun dB2Scale(`val`: Float): Float {
            if (`val` == 0.0f) {
                return 1.0f // most common
            } else if (`val` <= -60.0f) {
                return 0.0f
            } else if (`val` >= 60.0f) {
                return 2.0f.pow((`val` * (1.0f / 6.0f)))
            }
            val ival = ((`val` + 60.0f) * 10.0f).toInt()
            return volumesDB[ival]
        }

        fun SamplesToMilliseconds(samples: Int): Int {
            return samples / (snd_local.PRIMARYFREQ / 1000)
        }

        fun MillisecondsToSamples(ms: Int): Int {
            return ms * (snd_local.PRIMARYFREQ / 1000)
        }

        fun DoEnviroSuit(samples: FloatArray, numSamples: Int, numSpeakers: Int) {
            var out: FloatArray
            val out_p = 2
            var `in`: FloatArray
            val in_p = 2
            assert(!useOpenAL)
            if (0 == fxList.Num()) {
                for (i in 0..5) {
                    var fx: SoundFX

                    // lowpass filter
                    fx = SoundFX_Lowpass()
                    fx.SetChannel(i)
                    fxList.Append(fx)

                    // comb
                    fx = SoundFX_Comb()
                    fx.SetChannel(i)
                    fx.SetParameter((i * 100).toFloat())
                    fxList.Append(fx)

                    // comb
                    fx = SoundFX_Comb()
                    fx.SetChannel(i)
                    fx.SetParameter((i * 100 + 5).toFloat())
                    fxList.Append(fx)
                }
            }
            for (i in 0 until numSpeakers) {
                var j: Int

                // restore previous samples
//		memset( in, 0, 10000 * sizeof( float ) );
                out = FloatArray(10000)
                //		memset( out, 0, 10000 * sizeof( float ) );
                `in` = FloatArray(10000)

                // fx loop
                for (k in 0 until fxList.Num()) {
                    val fx = fxList[k]

                    // skip if we're not the right channel
                    if (fx.GetChannel() != i) {
                        continue
                    }

                    // get samples and continuity
                    run {
                        val in1 = floatArrayOf(0.0f)
                        val in2 = floatArrayOf(0.0f)
                        val out1 = floatArrayOf(0.0f)
                        val out2 = floatArrayOf(0.0f)
                        fx.GetContinuitySamples(in1, in2, out1, out2)
                        `in`[in_p - 1] = in1[0]
                        `in`[in_p - 2] = in2[0]
                        out[out_p - 1] = out1[0]
                        out[out_p - 2] = out2[0]
                    }
                    j = 0
                    while (j < numSamples) {
                        `in`[in_p + j] = samples[j * numSpeakers + i] * s_enviroSuitVolumeScale.GetFloat()
                        j++
                    }

                    // process fx loop
                    j = 0
                    while (j < numSamples) {
                        fx.ProcessSample(`in`, in_p + j, out, out_p + j) //TODO:float[], int index, float[], int index
                        j++
                    }

                    // store samples and continuity
                    fx.SetContinuitySamples(
                        `in`[in_p + numSamples - 2],
                        `in`[in_p + numSamples - 3],
                        out[out_p + numSamples - 2],
                        out[out_p + numSamples - 3]
                    )
                    j = 0
                    while (j < numSamples) {
                        samples[j * numSpeakers + i] = out[out_p + j]
                        j++
                    }
                }
            }
        }

        fun  /*ALuint*/AllocOpenALSource(chan: idSoundChannel, looping: Boolean, stereo: Boolean): Int {
            var timeOldestZeroVolSingleShot = win_shared.Sys_Milliseconds()
            var timeOldestZeroVolLooping = win_shared.Sys_Milliseconds()
            var timeOldestSingle = win_shared.Sys_Milliseconds()
            var iOldestZeroVolSingleShot = -1
            var iOldestZeroVolLooping = -1
            var iOldestSingle = -1
            var iUnused = -1
            var index = -1
            var   /*ALsizei*/i: Int

            // Grab current msec time
            val time = win_shared.Sys_Milliseconds()

            // Cycle through all sources
            i = 0
            while (i < openalSourceCount) {

                // Use any unused source first,
                // Then find oldest single shot quiet source,
                // Then find oldest looping quiet source and
                // Lastly find oldest single shot non quiet source..
                if (!openalSources[i].inUse) {
                    iUnused = i
                    break
                } else if (!openalSources[i].looping && openalSources[i].chan!!.lastVolume < snd_local.SND_EPSILON) {
                    if (openalSources[i].startTime < timeOldestZeroVolSingleShot) {
                        timeOldestZeroVolSingleShot = openalSources[i].startTime
                        iOldestZeroVolSingleShot = i
                    }
                } else if (openalSources[i].looping && openalSources[i].chan!!.lastVolume < snd_local.SND_EPSILON) {
                    if (openalSources[i].startTime < timeOldestZeroVolLooping) {
                        timeOldestZeroVolLooping = openalSources[i].startTime
                        iOldestZeroVolLooping = i
                    }
                } else if (!openalSources[i].looping) {
                    if (openalSources[i].startTime < timeOldestSingle) {
                        timeOldestSingle = openalSources[i].startTime
                        iOldestSingle = i
                    }
                }
                i++
            }
            if (iUnused != -1) {
                index = iUnused
            } else if (iOldestZeroVolSingleShot != -1) {
                index = iOldestZeroVolSingleShot
            } else if (iOldestZeroVolLooping != -1) {
                index = iOldestZeroVolLooping
            } else if (iOldestSingle != -1) {
                index = iOldestSingle
            }
            return if (index != -1) {
                // stop the channel that is being ripped off
                if (openalSources[index].chan != null) {
                    // stop the channel only when not looping
                    if (!openalSources[index].looping) {
                        openalSources[index].chan!!.Stop()
                    } else {
                        openalSources[index].chan!!.triggered = true
                    }

                    // Free hardware resources
                    openalSources[index].chan!!.ALStop()
                }

                // Initialize structure
                openalSources[index].startTime = time
                openalSources[index].chan = chan
                openalSources[index].inUse = true
                openalSources[index].looping = looping
                openalSources[index].stereo = stereo
                openalSources[index].handle
            } else {
                0
            }
        }

        fun FreeOpenALSource(   /*ALuint*/handle: Int) {
            var   /*ALsizei*/i: Int
            i = 0
            while (i < openalSourceCount) {
                if (openalSources[i].handle == handle) {
                    if (openalSources[i].chan != null) {
                        openalSources[i].chan!!.openalSource = 0
                    }
                    // #if ID_OPENAL
                    // // Reset source EAX ROOM level when freeing stereo source
                    // if ( openalSources[i].stereo && alEAXSet ) {
                    // long Room = EAXSOURCE_DEFAULTROOM;
                    // alEAXSet( &EAXPROPERTYID_EAX_Source, EAXSOURCE_ROOM, openalSources[i].handle, &Room, sizeof(Room));
                    // }
// #endif
                    // Initialize structure
                    openalSources[i].startTime = 0
                    openalSources[i].chan = null
                    openalSources[i].inUse = false
                    openalSources[i].looping = false
                    openalSources[i].stereo = false
                }
                i++
            }
        }
    }

    /*
     ===============
     SoundReloadSounds_f

     this is called from the main thread
     ===============
     */
    internal class SoundReloadSounds_f private constructor() : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            if (soundSystemLocal.soundCache == null) {
                return
            }
            val force = args!!.Argc() == 2
            soundSystem.SetMute(true)
            soundSystemLocal.soundCache!!.ReloadSounds(force)
            soundSystem.SetMute(false)
            Common.common.Printf("sound: changed sounds reloaded\n")
        }

        companion object {
            val INSTANCE: cmdFunction_t = SoundReloadSounds_f()
        }
    }

    /*
     ===============
     ListSounds_f

     Optional parameter to only list sounds containing that string
     ===============
     */
    internal class ListSounds_f private constructor() : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            var i: Int
            val snd = args!!.Argv(1)
            if (soundSystemLocal.soundCache == null) {
                Common.common.Printf("No sound.\n")
                return
            }
            var totalSounds = 0
            var totalSamples = 0
            var totalMemory = 0
            var totalPCMMemory = 0
            i = 0
            while (i < soundSystemLocal.soundCache!!.GetNumObjects()) {
                val sample = soundSystemLocal.soundCache!!.GetObject(i)
                if (sample == null) {
                    i++
                    continue
                }
                if (snd != null && sample!!.name.Find(snd, false) < 0) {
                    i++
                    continue
                }
                val info = sample!!.objectInfo
                val stereo = if (info.nChannels == 2) "ST" else "  "
                val format = if (info.wFormatTag == snd_local.WAVE_FORMAT_TAG_OGG) "OGG" else "WAV"
                val defaulted = if (sample.defaultSound) "(DEFAULTED)" else if (sample.purged) "(PURGED)" else ""
                Common.common.Printf(
                    "%s %dkHz %6dms %5dkB %4s %s%s\n", stereo, sample.objectInfo.nSamplesPerSec / 1000,
                    soundSystemLocal.SamplesToMilliseconds(sample.LengthIn44kHzSamples()),
                    sample.objectMemSize shr 10, format, sample.name, defaulted
                )
                if (!sample.purged) {
                    totalSamples += sample.objectSize
                    if (info.wFormatTag != snd_local.WAVE_FORMAT_TAG_OGG) {
                        totalPCMMemory += sample.objectMemSize
                    }
                    if (!sample.hardwareBuffer) {
                        totalMemory += sample.objectMemSize
                    }
                }
                totalSounds++
                i++
            }
            Common.common.Printf("%8d total sounds\n", totalSounds)
            Common.common.Printf("%8d total samples loaded\n", totalSamples)
            Common.common.Printf("%8d kB total system memory used\n", totalMemory shr 10)
            //#if ID_OPENAL
//	common.Printf( "%8d kB total OpenAL audio memory used\n", ( alGetInteger( alGetEnumValue( "AL_EAX_RAM_SIZE" ) ) - alGetInteger( alGetEnumValue( "AL_EAX_RAM_FREE" ) ) ) >> 10 );
//#endif
        }

        companion object {
            val INSTANCE: cmdFunction_t = ListSounds_f()
        }
    }

    /*
     ===============
     ListSoundDecoders_f
     ===============
     */
    internal class ListSoundDecoders_f private constructor() : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            var i: Int
            var j: Int
            var numActiveDecoders: Int
            var numWaitingDecoders: Int
            val sw = soundSystemLocal.currentSoundWorld
            numWaitingDecoders = 0
            numActiveDecoders = numWaitingDecoders
            i = 0
            while (i < sw!!.emitters.Num()) {
                val sound = sw.emitters[i]
                if (sound == null) {
                    i++
                    continue
                }

                // run through all the channels
                j = 0
                while (j < snd_local.SOUND_MAX_CHANNELS) {
                    val chan = sound.channels[j]
                    if (chan.decoder == null) {
                        j++
                        continue
                    }
                    val sample = chan.decoder!!.GetSample()
                    if (sample != null) {
                        j++
                        continue
                    }
                    val format =
                        if (chan.leadinSample!!.objectInfo.wFormatTag == snd_local.WAVE_FORMAT_TAG_OGG) "OGG" else "WAV"
                    Common.common.Printf("%3d waiting %s: %s\n", numWaitingDecoders, format, chan.leadinSample!!.name)
                    numWaitingDecoders++
                    j++
                }
                i++
            }
            i = 0
            while (i < sw.emitters.Num()) {
                val sound = sw.emitters[i]
                if (sound == null) {
                    i++
                    continue
                }

                // run through all the channels
                j = 0
                while (j < snd_local.SOUND_MAX_CHANNELS) {
                    val chan = sound.channels[j]
                    if (chan.decoder == null) {
                        j++
                        continue
                    }
                    val sample = chan.decoder!!.GetSample()
                    if (sample == null) {
                        j++
                        continue
                    }
                    val format = if (sample.objectInfo.wFormatTag == snd_local.WAVE_FORMAT_TAG_OGG) "OGG" else "WAV"
                    val localTime = soundSystemLocal.GetCurrent44kHzTime() - chan.trigger44kHzTime
                    val sampleTime = sample.LengthIn44kHzSamples() * sample.objectInfo.nChannels
                    var percent: Int
                    percent = if (localTime > sampleTime) {
                        if (chan.parms!!.soundShaderFlags and snd_shader.SSF_LOOPING != 0) {
                            localTime % sampleTime * 100 / sampleTime
                        } else {
                            100
                        }
                    } else {
                        localTime * 100 / sampleTime
                    }
                    Common.common.Printf("%3d decoding %3d%% %s: %s\n", numActiveDecoders, percent, format, sample.name)
                    numActiveDecoders++
                    j++
                }
                i++
            }
            Common.common.Printf("%d decoders\n", numWaitingDecoders + numActiveDecoders)
            Common.common.Printf("%d waiting decoders\n", numWaitingDecoders)
            Common.common.Printf("%d active decoders\n", numActiveDecoders)
            Common.common.Printf(
                "%d kB decoder memory in %d blocks\n",
                idSampleDecoder.GetUsedBlockMemory() shr 10,
                idSampleDecoder.GetNumUsedBlocks()
            )
        }

        companion object {
            val INSTANCE: cmdFunction_t = ListSoundDecoders_f()
        }
    }

    /*
     ===============
     TestSound_f

     this is called from the main thread
     ===============
     */
    internal class TestSound_f private constructor() : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            if (args!!.Argc() != 2) {
                Common.common.Printf("Usage: testSound <file>\n")
                return
            }
            soundSystemLocal.currentSoundWorld?.PlayShaderDirectly(args!!.Argv(1))
        }

        companion object {
            val INSTANCE: cmdFunction_t = TestSound_f()
        }
    }

    /*
     ===============
     SoundSystemRestart_f

     restart the sound thread

     this is called from the main thread
     ===============
     */
    internal class SoundSystemRestart_f private constructor() : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            soundSystem.SetMute(true)
            soundSystemLocal.ShutdownHW()
            soundSystemLocal.InitHW()
            soundSystem.SetMute(false)
        }

        companion object {
            val INSTANCE: cmdFunction_t = SoundSystemRestart_f()
        }
    }

    companion object {
        var soundSystemLocal: idSoundSystemLocal = idSoundSystemLocal()


        var soundSystem: idSoundSystem = soundSystemLocal

        fun setSoundSystems(soundSystem: idSoundSystem) {
            soundSystemLocal = soundSystem as idSoundSystemLocal
            snd_system.soundSystem = soundSystemLocal
        }

    }
}
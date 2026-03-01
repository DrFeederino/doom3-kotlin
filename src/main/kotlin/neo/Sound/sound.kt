package neo.Sound

import neo.Renderer.Cinematic.cinData_t
import neo.Renderer.RenderWorld.idRenderWorld
import neo.Sound.snd_shader.idSoundShader
import neo.TempDump.SERiAL
import neo.framework.Common.MemInfo_t
import neo.framework.DemoFile.idDemoFile
import neo.framework.File_h.idFile
import neo.idlib.Text.Str.idStr
import neo.idlib.math.Matrix.idMat3
import neo.idlib.math.idVec3

object sound {
    /*
     ===============================================================================

     SOUND EMITTER

     ===============================================================================
     */
    // sound channels
    const val SCHANNEL_ANY = 0 // used in queries and commands to effect every channel at once, in

    // startSound to have it not override any other channel
    const val SCHANNEL_ONE = 1 // any following integer can be used as a channel number

    // typedef int s_channelType;	// the game uses its own series of enums, and we don't want to require casts
    abstract class idSoundEmitter : SERiAL {
        // virtual					~idSoundEmitter() {}
        // a non-immediate free will let all currently playing sounds complete
        // soundEmitters are not actually deleted, they are just marked as
        // reusable by the soundWorld
        abstract fun Free(immediate: Boolean)

        // the parms specified will be the default overrides for all sounds started on this emitter.
        // NULL is acceptable for parms
        abstract fun UpdateEmitter(origin: idVec3, listenerId: Int, parms: snd_shader.soundShaderParms_t)

        // returns the length of the started sound in msec
        abstract fun StartSound(
            shader: idSoundShader,
            channel: Int,
            diversity: Float /*= 0*/,
            shaderFlags: Int /*= 0*/,
            allowSlow: Boolean /*= true*/
        ): Int

        fun StartSound(shader: idSoundShader, channel: Int, diversity: Float /*= 0*/, shaderFlags: Int /*= 0*/): Int {
            return StartSound(shader, channel, diversity, shaderFlags, true)
        }

        // pass SCHANNEL_ANY to effect all channels
        abstract fun ModifySound(channel: Int, parms: snd_shader.soundShaderParms_t)
        abstract fun StopSound(channel: Int)

        // to is in Db (sigh), over is in seconds
        abstract fun FadeSound(channel: Int, to: Float, over: Float)

        // returns true if there are any sounds playing from this emitter.  There is some conservative
        // slop at the end to remove inconsistent race conditions with the sound thread updates.
        // FIXME: network game: on a dedicated server, this will always be false
        abstract fun CurrentlyPlaying(): Boolean

        // returns a 0.0f to 1.0f value based on the current sound amplitude, allowing
        // graphic effects to be modified in time with the audio.
        // just samples the raw wav file, it doesn't account for volume overrides in the
        abstract fun CurrentAmplitude(): Float

        // for save games.  Index will always be > 0
        abstract fun Index(): Int
    }

    /*
     ===============================================================================

     SOUND WORLD

     There can be multiple independent sound worlds, just as there can be multiple
     independent render worlds.  The prime example is the editor sound preview
     option existing simultaniously with a live game.
     ===============================================================================
     */
    abstract class idSoundWorld {
        // virtual					~idSoundWorld() {}
        // call at each map start
        abstract fun ClearAllSoundEmitters()
        abstract fun StopAllSounds()

        // get a new emitter that can play sounds in this world
        abstract fun AllocSoundEmitter(): idSoundEmitter

        // for load games, index 0 will return NULL
        abstract fun EmitterForIndex(index: Int): idSoundEmitter?

        // query sound samples from all emitters reaching a given position
        abstract fun CurrentShakeAmplitudeForPosition(time: Int, listenerPosition: idVec3): Float

        // where is the camera/microphone
        // listenerId allows listener-private and antiPrivate sounds to be filtered
        // gameTime is in msec, and is used to time sound queries and removals so that they are independent
        // of any race conditions with the async update
        abstract fun PlaceListener(origin: idVec3, axis: idMat3, listenerId: Int, gameTime: Int, areaName: idStr)
        fun PlaceListener(origin: idVec3, axis: idMat3, listenerId: Int, gameTime: Int, areaName: String) {
            PlaceListener(origin, axis, listenerId, gameTime, idStr(areaName))
        }

        // fade all sounds in the world with a given shader soundClass
        // to is in Db (sigh), over is in seconds
        abstract fun FadeSoundClasses(soundClass: Int, to: Float, over: Float)

        // background music
        abstract fun PlayShaderDirectly(name: String, channel: Int /*= -1*/)
        fun PlayShaderDirectly(name: String) {
            PlayShaderDirectly(name, -1)
        }

        // dumps the current state and begins archiving commands
        abstract fun StartWritingDemo(demo: idDemoFile)
        abstract fun StopWritingDemo()

        // read a sound command from a demo file
        abstract fun ProcessDemoCommand(demo: idDemoFile)

        // pause and unpause the sound world
        abstract fun Pause()
        abstract fun UnPause()
        abstract fun IsPaused(): Boolean

        // Write the sound output to multiple wav files.  Note that this does not use the
        // work done by AsyncUpdate, it mixes explicitly in the foreground every PlaceOrigin(),
        // under the assumption that we are rendering out screenshots and the gameTime is going
        // much slower than real time.
        // path should not include an extension, and the generated filenames will be:
        // <path>_left.raw, <path>_right.raw, or <path>_51left.raw, <path>_51right.raw, 
        // <path>_51center.raw, <path>_51lfe.raw, <path>_51backleft.raw, <path>_51backright.raw, 
        // If only two channel mixing is enabled, the left and right .raw files will also be
        // combined into a stereo .wav file.
        abstract fun AVIOpen(path: String, name: String)
        abstract fun AVIClose()

        // SaveGame / demo Support
        abstract fun WriteToSaveGame(savefile: idFile)
        abstract fun ReadFromSaveGame(savefile: idFile)
        abstract fun SetSlowmo(active: Boolean)
        abstract fun SetSlowmoSpeed(speed: Float)
        abstract fun SetEnviroSuit(active: Boolean)
    }

    /*
     ===============================================================================

     SOUND SYSTEM

     ===============================================================================
     */
    class soundDecoderInfo_t {
        var current44kHzTime = 0
        val format: idStr = idStr()
        var lastVolume = 0.0f
        var looping = false
        val name: idStr = idStr()
        var num44kHzSamples = 0
        var numBytes = 0
        var numChannels = 0
        var numSamplesPerSecond = 0
        var start44kHzTime = 0
    }

    abstract class idSoundSystem {
        // virtual					~idSoundSystem( void ) {}
        // all non-hardware initialization
        abstract fun Init()

        // shutdown routine
        abstract fun Shutdown()

        // call ClearBuffer if there is a chance that the AsyncUpdate won't get called
        // for 20+ msec, which would cause a stuttering repeat of the current
        // buffer contents
        abstract fun ClearBuffer()

        // sound is attached to the window, and must be recreated when the window is changed
        abstract fun InitHW(): Boolean
        abstract fun ShutdownHW(): Boolean

        // asyn loop, called at 60Hz
        abstract fun AsyncUpdate(time: Int): Int

        // async loop, when the sound driver uses a write strategy
        abstract fun AsyncUpdateWrite(time: Int): Int

        // it is a good idea to mute everything when starting a new level,
        // because sounds may be started before a valid listener origin
        // is specified
        abstract fun SetMute(mute: Boolean)

        // for the sound level meter window
        abstract fun ImageForTime(milliseconds: Int, waveform: Boolean): cinData_t

        // get sound decoder info
        abstract fun GetSoundDecoderInfo(index: Int, decoderInfo: soundDecoderInfo_t): Int

        // if rw == NULL, no portal occlusion or rendered debugging is available
        abstract fun AllocSoundWorld(rw: idRenderWorld): idSoundWorld

        // specifying NULL will cause silence to be played
        abstract fun SetPlayingSoundWorld(soundWorld: idSoundWorld?)

        // some tools, like the sound dialog, may be used in both the game and the editor
        // This can return NULL, so check!
        abstract fun GetPlayingSoundWorld(): idSoundWorld?

        // Mark all soundSamples as currently unused,
        // but don't free anything.
        abstract fun BeginLevelLoad()

        // Free all soundSamples marked as unused
        // We might want to defer the loading of new sounds to this point,
        // as we do with images, to avoid having a union in memory at one time.
        abstract fun EndLevelLoad(mapString: String)

        // direct mixing for OSes that support it
        abstract fun AsyncMix(soundTime: Int, mixBuffer: FloatArray): Int

        // prints memory info
        abstract fun PrintMemInfo(mi: MemInfo_t)

        // is EAX support present - -1: disabled at compile time, 0: no suitable hardware, 1: ok, 2: failed to load OpenAL DLL
        abstract fun IsEAXAvailable(): Int
    }
}
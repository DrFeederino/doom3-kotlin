package neo.Sound

import neo.Renderer.Cinematic.idSndWindow
import neo.Renderer.Material
import neo.Renderer.Material.shaderStage_t
import neo.Renderer.RenderWorld.idRenderWorld
import neo.Renderer.RenderWorld.portalConnection_t
import neo.Sound.snd_emitter.idSoundChannel
import neo.Sound.snd_emitter.idSoundEmitterLocal
import neo.Sound.snd_emitter.idSoundFade
import neo.Sound.snd_local.*
import neo.Sound.snd_shader.idSoundShader
import neo.Sound.snd_system.idSoundSystemLocal
import neo.Sound.snd_system.idSoundSystemLocal.Companion.useEFXReverb
import neo.Sound.sound.SCHANNEL_ANY
import neo.Sound.sound.idSoundEmitter
import neo.Sound.sound.idSoundWorld
import neo.framework.Common
import neo.framework.DeclManager
import neo.framework.DemoFile.demoSystem_t
import neo.framework.DemoFile.idDemoFile
import neo.framework.FileSystem_h
import neo.framework.File_h.idFile
import neo.framework.Session
import neo.idlib.BV.idBounds
import neo.idlib.Text.Str
import neo.idlib.Text.Str.idStr
import neo.idlib.colorRed
import neo.idlib.containers.CFloat
import neo.idlib.containers.List.idList
import neo.idlib.math.*
import neo.idlib.math.Matrix.idMat3
import neo.idlib.math.Random.idRandom
import neo.sys.win_main.Sys_EnterCriticalSection
import neo.sys.win_main.Sys_LeaveCriticalSection
import org.lwjgl.BufferUtils
import org.lwjgl.openal.AL10
import org.lwjgl.openal.AL11.alSource3i
import org.lwjgl.openal.EXTEfx
import java.nio.ByteBuffer
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import kotlin.math.atan
import kotlin.math.min

class snd_world {
    class s_stats {
        var activeSounds = 0
        var missedUpdateWindow = 0
        var missedWindow = 0
        var rinuse = 0
        var runs = 1
        var timeinprocess = 0
    }

    class soundPortalTrace_s {
        var portalArea = 0
        var prevStack: soundPortalTrace_s? = null
    }

    class idSoundWorldLocal : idSoundWorld() {
        val aviDemoName: idStr
        val aviDemoPath: idStr

        //
        val emitters: idList<idSoundEmitterLocal>
        var enviroSuitActive = false

        //
        // avi stuff
        var fpa: Array<idFile?> = arrayOfNulls<idFile?>(6)
        var game44kHz = 0

        //
        var gameMsec = 0
        var lastAVI44kHz // determine when we need to mix and write another block
                = 0
        var listenerArea = 0
        val listenerAreaName: idStr

        //
        val listenerAxis: idMat3
        val listenerPos // position in meters
                : idVec3
        var listenerPrivateId = 0
        val listenerQU // position in "quake units"
                : idVec3

        //
        var localSound // just for playShaderDirectly()
                : idSoundEmitterLocal? = null
        var pause44kHz = 0

        //
        //
        //============================================
        var rw // for portals and debug drawing
                : idRenderWorld? = null

        //
        var slowmoActive = false
        var slowmoSpeed = 0.0f

        //
        var soundClassFade: Array<idSoundFade> =
            Array(snd_shader.SOUND_MAX_CLASSES) { idSoundFade() } // for global sound fading
        var writeDemo // if not NULL, archive commands here
                : idDemoFile? = null

        // FIX: Missing EFX fields from C++ snd_local.h. Required for reverb effects.
        var listenerEffect: Int = 0            // ALuint - current EFX effect applied to listener slot
        var listenerSlot: Int = 0              // ALuint - auxiliary effect slot for reverb
        var listenerAreFiltersInitialized = false
        var listenerFilters: IntArray = IntArray(2) // [0] = direct filter, [1] = send filter
        var listenerSlotReverbGain: Float = 1.0f

        private val mixInputSamples = FloatArray(MIXBUFFER_SAMPLES * 2 + 16)
        private val mixInputSamplesBuffer: FloatBuffer = FloatBuffer.wrap(mixInputSamples)
        private val mixEars = FloatArray(6)
        private val channelSpatializedOriginInMeters = idVec3()
        private val listenerPositionScratch = FloatArray(3)
        private val listenerOrientationScratch: FloatBuffer = BufferUtils.createFloatBuffer(6)
        private val effectHandleScratch = IntArray(1)
        private val listenerAreaEffectName = idStr()
        private val defaultEffectName = idStr("default")
        private var listenerAreaEffectNameArea = Int.MIN_VALUE
        private val streamingBufferScratch = IntArray(3)
        private val streamingSampleBytes: ByteBuffer =
            BufferUtils.createByteBuffer(MIXBUFFER_SAMPLES * 2 * java.lang.Short.BYTES)
        private val streamingSampleShorts: ShortBuffer = streamingSampleBytes.asShortBuffer()

        // virtual					~idSoundWorldLocal();
        // call at each map start
        override fun ClearAllSoundEmitters() {
            var i: Int
            Sys_EnterCriticalSection()
            AVIClose()
            i = 0
            while (i < emitters.Num()) {
                val sound = emitters[i]
                sound.Clear()
                i++
            }
            localSound = null
            Sys_LeaveCriticalSection()
        }

        /*
         ===============
         idSoundWorldLocal::StopAllSounds

         this is called from the main thread
         ===============
         */
        override fun StopAllSounds() {
            for (i in 0 until emitters.Num()) {
                val def = emitters[i]
                def.StopSound(SCHANNEL_ANY)
            }
        }

        /*
         ===================
         idSoundWorldLocal::AllocSoundEmitter

         this is called from the main thread
         ===================
         */ // get a new emitter that can play sounds in this world
        override fun AllocSoundEmitter(): idSoundEmitter {
            val emitter = AllocLocalSoundEmitter()
            if (idSoundSystemLocal.s_showStartSound.GetInteger() != 0) {
                Common.common.Printf("AllocSoundEmitter = %d\n", emitter.index)
            }
            writeDemo?.WriteInt(demoSystem_t.DS_SOUND)
            writeDemo?.WriteInt(soundDemoCommand_t.SCMD_ALLOC_EMITTER)
            writeDemo?.WriteInt(emitter.index)
            return emitter
        }

        // for load games
        override fun EmitterForIndex(index: Int): idSoundEmitter? {
            if (index == 0) {
                return null
            }
            if (index >= emitters.Num()) {
                Common.common.Error("idSoundWorldLocal::EmitterForIndex: %d > %d", index, emitters.Num())
            }
            return emitters[index]
        }

        /*
         ===================
         idSoundWorldLocal::CurrentShakeAmplitudeForPosition

         this is called from the main thread
         ===================
         */ // query data from all emitters in the world
        override fun CurrentShakeAmplitudeForPosition(time: Int, listererPosition: idVec3): Float {
            var amp = 0.0f
            val localTime: Int
            if (idSoundSystemLocal.s_constantAmplitude.GetFloat() >= 0.0f) {
                return 0.0f
            }
            localTime = snd_system.soundSystemLocal.GetCurrent44kHzTime()
            for (i in 1 until emitters.Num()) {
                val sound = emitters[i]
                if (!sound.hasShakes) {
                    continue
                }
                amp += FindAmplitude(sound, localTime, listererPosition, SCHANNEL_ANY, true)
            }
            return amp
        }

        /*
         ===================
         idSoundWorldLocal::PlaceListener

         this is called by the main thread
         ===================
         */ // where is the camera/microphone
        // listenerId allows listener-private sounds to be added
        override fun PlaceListener(origin: idVec3, axis: idMat3, listenerId: Int, gameTime: Int, areaName: idStr) {
            val current44kHzTime: Int
            if (!snd_system.soundSystemLocal.isInitialized) {
                return
            }
            if (pause44kHz >= 0) {
                return
            }
            if (writeDemo != null) {
                writeDemo!!.WriteInt(demoSystem_t.DS_SOUND)
                writeDemo!!.WriteInt(soundDemoCommand_t.SCMD_PLACE_LISTENER)
                writeDemo!!.WriteVec3(origin)
                writeDemo!!.WriteMat3(axis)
                writeDemo!!.WriteInt(listenerId)
                writeDemo!!.WriteInt(gameTime)
            }
            current44kHzTime = snd_system.soundSystemLocal.GetCurrent44kHzTime()

            // we usually expect gameTime to be increasing by 16 or 32 msec, but when
            // a cinematic is fast-forward skipped through, it can jump by a significant
            // amount, while the hardware 44kHz position will not have changed accordingly,
            // which would make sounds (like long character speaches) continue from the
            // old time.  Fix this by killing all non-looping sounds
            if (gameTime > gameMsec + 500) {
                OffsetSoundTime((-(gameTime - gameMsec) * 0.001f * 44100.0f).toInt())
            }
            gameMsec = gameTime
            game44kHz = if (fpa[0] != null) { // exactly 30 fps so the wave file can be used for exact video frames
                idMath.FtoiFast(gameMsec * (1000.0f / 60.0f / 16.0f) * 0.001f * 44100.0f)
            } else { // the normal 16 msec / frame
                idMath.FtoiFast(gameMsec * 0.001f * 44100.0f)
            }
            listenerPrivateId = listenerId
            listenerQU.set(origin) // Doom units
            listenerPos.set(origin.times(snd_shader.DOOM_TO_METERS)) // meters
            listenerAxis.set(axis)
            listenerAreaName.set(areaName)
            listenerAreaName.ToLower()
            listenerArea = if (rw != null) {
                rw!!.PointInArea(listenerQU) // where are we?
            } else {
                0
            }
            if (listenerArea < 0) {
                return
            }
            ForegroundUpdate(current44kHzTime)
        }

        /*
         =================
         idSoundWorldLocal::FadeSoundClasses

         fade all sounds in the world with a given shader soundClass
         to is in Db (sigh), over is in seconds
         =================
         */
        override fun FadeSoundClasses(soundClass: Int, to: Float, over: Float) {
            if (soundClass < 0 || soundClass >= snd_shader.SOUND_MAX_CLASSES) {
                Common.common.Error("idSoundWorldLocal::FadeSoundClasses: bad soundClass %d", soundClass)
            }
            val fade = soundClassFade[soundClass]
            val length44kHz = snd_system.soundSystemLocal.MillisecondsToSamples((over * 1000).toInt())

            // if it is already fading to this volume at this rate, don't change it
            if (fade.fadeEndVolume == to && fade.fadeEnd44kHz - fade.fadeStart44kHz == length44kHz) {
                return
            }
            val start44kHz: Int
            start44kHz = if (fpa[0] != null) { // if we are recording an AVI demo, don't use hardware time
                lastAVI44kHz + MIXBUFFER_SAMPLES
            } else {
                snd_system.soundSystemLocal.GetCurrent44kHzTime() + MIXBUFFER_SAMPLES
            }

            // fade it
            fade.fadeStartVolume = fade.FadeDbAt44kHz(start44kHz)
            fade.fadeStart44kHz = start44kHz
            fade.fadeEnd44kHz = start44kHz + length44kHz
            fade.fadeEndVolume = to
        }

        // dumps the current state and begins archiving commands
        /*
         ===================
         idSoundWorldLocal::StartWritingDemo

         this is called from the main thread
         ===================
         */
        override fun StartWritingDemo(demo: idDemoFile) {
            writeDemo = demo
            writeDemo!!.WriteInt(demoSystem_t.DS_SOUND)
            writeDemo!!.WriteInt(soundDemoCommand_t.SCMD_STATE)

            // use the normal save game code to archive all the emitters
            WriteToSaveGame(writeDemo!!)
        }

        /*
         ===================
         idSoundWorldLocal::StopWritingDemo

         this is called from the main thread
         ===================
         */
        override fun StopWritingDemo() {
            writeDemo = null
        }

        /*
         ===================
         idSoundWorldLocal::ProcessDemoCommand

         this is called from the main thread
         ===================
         */ // read a sound command from a demo file
        override fun ProcessDemoCommand(readDemo: idDemoFile) {
            val index: Int
            var def: idSoundEmitterLocal?
            if (null == readDemo) {
                return
            }
            var _dc: Int
            if (readDemo.ReadInt().also { _dc = it } == 0) {
                return
            }
            val dc = soundDemoCommand_t.values()[_dc]
            when (dc) {
                soundDemoCommand_t.SCMD_STATE -> { // we need to protect this from the async thread
                    // other instances of calling idSoundWorldLocal::ReadFromSaveGame do this while the sound code is muted
                    // setting muted and going right in may not be good enough here, as we async thread may already be in an async tick (in which case we could still race to it)
                    Sys_EnterCriticalSection()
                    ReadFromSaveGame(readDemo)
                    Sys_LeaveCriticalSection()
                    UnPause()
                }

                soundDemoCommand_t.SCMD_PLACE_LISTENER -> {
                    val origin = idVec3()
                    val axis = idMat3()
                    val listenerId: Int
                    val gameTime: Int
                    readDemo.ReadVec3(origin)
                    readDemo.ReadMat3(axis)
                    listenerId = readDemo.ReadInt()
                    gameTime = readDemo.ReadInt()
                    PlaceListener(origin, axis, listenerId, gameTime, "")
                }

                soundDemoCommand_t.SCMD_ALLOC_EMITTER -> {
                    index = readDemo.ReadInt()
                    if (index < 1 || index > emitters.Num()) {
                        Common.common.Error("idSoundWorldLocal::ProcessDemoCommand: bad emitter number")
                    }
                    if (index == emitters.Num()) { // append a brand new one
                        def = idSoundEmitterLocal()
                        emitters.Append(def)
                    }
                    def = emitters[index]
                    def.Clear()
                    def.index = index
                    def.removeStatus = snd_emitter.REMOVE_STATUS_ALIVE
                    def.soundWorld = this
                }

                soundDemoCommand_t.SCMD_FREE -> {
                    val immediate: Int
                    index = readDemo.ReadInt()
                    immediate = readDemo.ReadInt()
                    EmitterForIndex(index)!!.Free(immediate != 0)
                }

                soundDemoCommand_t.SCMD_UPDATE -> {
                    val origin = idVec3()
                    val listenerId: Int
                    val parms = snd_shader.soundShaderParms_t()
                    index = readDemo.ReadInt()
                    readDemo.ReadVec3(origin)
                    listenerId = readDemo.ReadInt()
                    parms.minDistance = readDemo.ReadFloat()
                    parms.maxDistance = readDemo.ReadFloat()
                    parms.volume = readDemo.ReadFloat()
                    parms.shakes = readDemo.ReadFloat()
                    parms.soundShaderFlags = readDemo.ReadInt()
                    parms.soundClass = readDemo.ReadInt()
                    EmitterForIndex(index)!!.UpdateEmitter(origin, listenerId, parms)
                }

                soundDemoCommand_t.SCMD_START -> {
                    val shader: idSoundShader
                    val channel: Int
                    val diversity: Float
                    val shaderFlags: Int
                    index = readDemo.ReadInt()
                    shader = DeclManager.declManager.FindSound(readDemo.ReadHashString())!!
                    channel = readDemo.ReadInt()
                    diversity = readDemo.ReadFloat()
                    shaderFlags = readDemo.ReadInt()
                    EmitterForIndex(index)!!.StartSound(shader, channel, diversity, shaderFlags)
                }

                soundDemoCommand_t.SCMD_MODIFY -> {
                    val channel: Int
                    val parms = snd_shader.soundShaderParms_t()
                    index = readDemo.ReadInt()
                    channel = readDemo.ReadInt()
                    parms.minDistance = readDemo.ReadFloat()
                    parms.maxDistance = readDemo.ReadFloat()
                    parms.volume = readDemo.ReadFloat()
                    parms.shakes = readDemo.ReadFloat()
                    parms.soundShaderFlags = readDemo.ReadInt()
                    parms.soundClass = readDemo.ReadInt()
                    EmitterForIndex(index)!!.ModifySound(channel, parms)
                }

                soundDemoCommand_t.SCMD_STOP -> {
                    val channel: Int
                    index = readDemo.ReadInt()
                    channel = readDemo.ReadInt()
                    EmitterForIndex(index)!!.StopSound(channel)
                }

                soundDemoCommand_t.SCMD_FADE -> {
                    val channel: Int
                    val to: Float
                    val over: Float
                    index = readDemo.ReadInt()
                    channel = readDemo.ReadInt()
                    to = readDemo.ReadFloat()
                    over = readDemo.ReadFloat()
                    EmitterForIndex(index)!!.FadeSound(channel, to, over)
                }
            }
        }

        override fun PlayShaderDirectly(shaderName: String, channel: Int /*= -1*/) {
            if (localSound != null && channel == -1) {
                localSound!!.StopSound(SCHANNEL_ANY)
            } else if (localSound != null) {
                localSound!!.StopSound(channel)
            }
            if (shaderName.isEmpty()) {
                return
            }
            val shader = DeclManager.declManager.FindSound(shaderName) ?: return
            if (null == localSound) {
                localSound = AllocLocalSoundEmitter()
            }
            val diversity = rnd.RandomFloat()
            localSound!!.StartSound(
                shader, if (channel == -1) sound.SCHANNEL_ONE else channel, diversity, snd_shader.SSF_GLOBAL
            )

            // in case we are at the console without a game doing updates, force an update
            ForegroundUpdate(snd_system.soundSystemLocal.GetCurrent44kHzTime())
        }

        // pause and unpause the sound world
        override fun Pause() {
            if (pause44kHz >= 0) {
                Common.common.Warning("idSoundWorldLocal::Pause: already paused")
                return
            }
            pause44kHz = snd_system.soundSystemLocal.GetCurrent44kHzTime()

            // FIX: Missing from Kotlin — dhewm3 pauses OpenAL sources when entering menus
            for (i in 0 until emitters.Num()) {
                val emitter = emitters[i]
                if (!emitter.playing) {
                    continue
                }
                emitter.PauseAll()
            }
        }

        override fun UnPause() {
            val offset44kHz: Int
            if (pause44kHz < 0) {
                Common.common.Warning("idSoundWorldLocal::UnPause: not paused")
                return
            }
            offset44kHz = snd_system.soundSystemLocal.GetCurrent44kHzTime() - pause44kHz
            OffsetSoundTime(offset44kHz)
            pause44kHz = -1

            // FIX: Missing from Kotlin — dhewm3 resumes OpenAL sources when leaving menus
            for (i in 0 until emitters.Num()) {
                val emitter = emitters[i]
                if (!emitter.playing) {
                    continue
                }
                emitter.UnPauseAll()
            }
        }

        override fun IsPaused(): Boolean {
            return pause44kHz >= 0
        }

        /*
         ===================
         idSoundWorldLocal::AVIOpen

         this is called by the main thread
         ===================
         */ // avidump
        override fun AVIOpen(path: String, name: String) {
            aviDemoPath.set(path)
            aviDemoName.set(name)
            lastAVI44kHz = game44kHz - game44kHz % MIXBUFFER_SAMPLES
            if (idSoundSystemLocal.s_numberOfSpeakers.GetInteger() == 6) {
                fpa[0] = FileSystem_h.fileSystem.OpenFileWrite(aviDemoPath.toString() + "channel_51_left.raw")
                fpa[1] = FileSystem_h.fileSystem.OpenFileWrite(aviDemoPath.toString() + "channel_51_right.raw")
                fpa[2] = FileSystem_h.fileSystem.OpenFileWrite(aviDemoPath.toString() + "channel_51_center.raw")
                fpa[3] = FileSystem_h.fileSystem.OpenFileWrite(aviDemoPath.toString() + "channel_51_lfe.raw")
                fpa[4] = FileSystem_h.fileSystem.OpenFileWrite(aviDemoPath.toString() + "channel_51_backleft.raw")
                fpa[5] = FileSystem_h.fileSystem.OpenFileWrite(aviDemoPath.toString() + "channel_51_backright.raw")
            } else {
                fpa[0] = FileSystem_h.fileSystem.OpenFileWrite(aviDemoPath.toString() + "channel_left.raw")
                fpa[1] = FileSystem_h.fileSystem.OpenFileWrite(aviDemoPath.toString() + "channel_right.raw")
            }
            snd_system.soundSystemLocal.SetMute(true)
        }

        override fun AVIClose() {
            var i: Int
            if (null == fpa[0]) {
                return
            }

            // make sure the final block is written
            game44kHz += MIXBUFFER_SAMPLES
            AVIUpdate()
            game44kHz -= MIXBUFFER_SAMPLES
            i = 0
            while (i < 6) {
                if (fpa[i] != null) {
                    FileSystem_h.fileSystem.CloseFile(fpa[i]!!)
                    fpa[i] = null
                }
                i++
            }
            if (idSoundSystemLocal.s_numberOfSpeakers.GetInteger() == 2) { // convert it to a wave file
                val rL: idFile?
                val lL: idFile?
                val wO: idFile?
                val name: idStr
                name = idStr(aviDemoPath.toString() + aviDemoName + ".wav")
                wO = FileSystem_h.fileSystem.OpenFileWrite(name.toString())
                if (null == wO) {
                    Common.common.Error("Couldn't write %s", name.toString())
                }
                name.set(aviDemoPath.toString() + "channel_right.raw")
                rL = FileSystem_h.fileSystem.OpenFileRead(name.toString())
                if (null == rL) {
                    Common.common.Error("Couldn't open %s", name.toString())
                }
                name.set(aviDemoPath.toString() + "channel_left.raw")
                lL = FileSystem_h.fileSystem.OpenFileRead(name.toString())
                if (null == lL) {
                    Common.common.Error("Couldn't open %s", name.toString())
                }
                val numSamples = rL!!.Length() / 2
                val info = mminfo_s()
                val format = pcmwaveformat_s()
                info.ckid = snd_wavefile.fourcc_riff
                info.fccType = snd_wavefile.mmioFOURCC('W'.code, 'A'.code, 'V'.code, 'E'.code)
                info.cksize = rL.Length() * 2 - 8 + 4 + 16 + 8 + 8
                info.dwDataOffset = 12
                wO!!.WriteUnsignedInt(info.ckid)
                wO.WriteInt(info.cksize)
                wO.WriteUnsignedInt(info.fccType)
                info.ckid = snd_wavefile.mmioFOURCC('f'.code, 'm'.code, 't'.code, ' '.code)
                info.cksize = 16
                wO.WriteUnsignedInt(info.ckid)
                wO.WriteInt(info.cksize)
                format.wBitsPerSample = 16
                format.wf.nAvgBytesPerSec = 44100 * 4 // sample rate * block align
                format.wf.nChannels = 2
                format.wf.nSamplesPerSec = 44100
                format.wf.wFormatTag = snd_local.WAVE_FORMAT_TAG_PCM
                format.wf.nBlockAlign = 4 // channels * bits/sample / 8
                format.writeTo(wO)
                info.ckid = snd_wavefile.mmioFOURCC('d'.code, 'a'.code, 't'.code, 'a'.code)
                info.cksize = rL.Length() * 2
                wO.WriteUnsignedInt(info.ckid)
                wO.WriteInt(info.cksize)
                var s0: Short
                var s1: Short
                i = 0
                while (i < numSamples) {
                    s0 = lL!!.ReadShort()
                    s1 = rL.ReadShort()
                    wO.WriteShort(s0)
                    wO.WriteShort(s1)
                    i++
                }
                FileSystem_h.fileSystem.CloseFile(wO)
                FileSystem_h.fileSystem.CloseFile(lL!!)
                FileSystem_h.fileSystem.CloseFile(rL)
                FileSystem_h.fileSystem.RemoveFile(aviDemoPath.toString() + "channel_right.raw")
                FileSystem_h.fileSystem.RemoveFile(aviDemoPath.toString() + "channel_left.raw")
            }
            snd_system.soundSystemLocal.SetMute(false)
        }

        // SaveGame Support
        override fun WriteToSaveGame(savefile: idFile) {
            var i: Int
            var j: Int
            val num: Int
            val currentSoundTime: Int
            var name: String

            // the game soundworld is always paused at this point, save that time down
            currentSoundTime = if (pause44kHz > 0) {
                pause44kHz
            } else {
                snd_system.soundSystemLocal.GetCurrent44kHzTime()
            }

            // write listener data
            savefile.WriteVec3(listenerQU)
            savefile.WriteMat3(listenerAxis)
            savefile.WriteInt(listenerPrivateId)
            savefile.WriteInt(gameMsec)
            savefile.WriteInt(game44kHz)
            savefile.WriteInt(currentSoundTime)
            num = emitters.Num()
            savefile.WriteInt(num)
            i = 1
            while (i < emitters.Num()) {
                val def = emitters[i]
                if (def.removeStatus != snd_emitter.REMOVE_STATUS_ALIVE) {
                    val skip = -1 //                    savefile.Write(skip, sizeof(skip));
                    savefile.WriteInt(skip)
                    i++
                    continue
                }
                savefile.WriteInt(i)

                // Write the emitter data
                savefile.WriteVec3(def.origin)
                savefile.WriteInt(def.listenerId)
                WriteToSaveGameSoundShaderParams(savefile, def.parms)
                savefile.WriteFloat(def.amplitude)
                savefile.WriteInt(def.ampTime)
                for (k in 0 until snd_local.SOUND_MAX_CHANNELS) {
                    WriteToSaveGameSoundChannel(savefile, def.channels[k])
                }
                savefile.WriteFloat(def.distance)
                savefile.WriteBool(def.hasShakes)
                savefile.WriteInt(def.lastValidPortalArea)
                savefile.WriteFloat(def.maxDistance)
                savefile.WriteBool(def.playing)
                savefile.WriteFloat(def.realDistance)
                savefile.WriteInt(def.removeStatus)
                savefile.WriteVec3(def.spatializedOrigin)

                // write the channel data
                j = 0
                while (j < snd_local.SOUND_MAX_CHANNELS) {
                    val chan = def.channels[j]

                    // Write out any sound commands for this def
                    if (chan.triggerState && chan.soundShader != null && chan.leadinSample != null) {
                        savefile.WriteInt(j)

                        // write the pointers out separately
                        name = chan.soundShader!!.GetName()
                        savefile.WriteString(name)
                        name = chan.leadinSample!!.name.toString()
                        savefile.WriteString(name)
                    }
                    j++
                }

                // End active channels with -1
                val end = -1
                savefile.WriteInt(end)
                i++
            }

            // new in Doom3 v1.2
            savefile.WriteBool(slowmoActive)
            savefile.WriteFloat(slowmoSpeed)
            savefile.WriteBool(enviroSuitActive)
        }

        override fun ReadFromSaveGame(savefile: idFile) {
            var i: Int
            val num: Int
            var handle: Int
            val listenerId: Int
            val gameTime: Int
            var channel: Int
            val currentSoundTime: Int
            val soundTimeOffset: Int
            val savedSoundTime: Int
            var def: idSoundEmitterLocal?
            val origin = idVec3()
            val axis = idMat3()
            val soundShader = idStr()
            ClearAllSoundEmitters()
            savefile.ReadVec3(origin)
            savefile.ReadMat3(axis)
            listenerId = savefile.ReadInt()
            gameTime = savefile.ReadInt()
            game44kHz = savefile.ReadInt()
            savedSoundTime = savefile.ReadInt()

            // we will adjust the sound starting times from those saved with the demo
            currentSoundTime = snd_system.soundSystemLocal.GetCurrent44kHzTime()
            soundTimeOffset = currentSoundTime - savedSoundTime

            // at the end of the level load we unpause the sound world and adjust the sound starting times once more
            pause44kHz = currentSoundTime

            // place listener
            PlaceListener(origin, axis, listenerId, gameTime, "Undefined")

            // make sure there are enough
            // slots to read the saveGame in.  We don't shrink the list
            // if there are extras.
            num = savefile.ReadInt()
            while (emitters.Num() < num) {
                def = idSoundEmitterLocal()
                def.index = emitters.Append(def)
                def.soundWorld = this
            }

            // read in the state
            i = 1
            while (i < num) {
                handle = savefile.ReadInt()
                if (handle < 0) {
                    i++
                    continue
                }
                if (handle != i) {
                    Common.common.Error("idSoundWorldLocal::ReadFromSaveGame: index mismatch")
                }
                def = emitters[i]
                def.removeStatus = snd_emitter.REMOVE_STATUS_ALIVE
                def.playing = true // may be reset by the first UpdateListener
                savefile.ReadVec3(def.origin)
                def.listenerId = savefile.ReadInt()
                ReadFromSaveGameSoundShaderParams(savefile, def.parms)
                def.amplitude = savefile.ReadFloat()
                def.ampTime = savefile.ReadInt()
                for (k in 0 until snd_local.SOUND_MAX_CHANNELS) {
                    ReadFromSaveGameSoundChannel(savefile, def.channels[k])
                }
                def.distance = savefile.ReadFloat()
                def.hasShakes = savefile.ReadBool()
                def.lastValidPortalArea = savefile.ReadInt()
                def.maxDistance = savefile.ReadFloat()
                def.playing = savefile.ReadBool()
                def.realDistance = savefile.ReadFloat()
                def.removeStatus = savefile.ReadInt()
                savefile.ReadVec3(def.spatializedOrigin)

                // read the individual channels
                channel = savefile.ReadInt()
                while (channel >= 0) {
                    if (channel > snd_local.SOUND_MAX_CHANNELS) {
                        Common.common.Error("idSoundWorldLocal::ReadFromSaveGame: channel > SOUND_MAX_CHANNELS")
                    }
                    val chan = def.channels[channel]
                    if (chan.decoder == null) { // The pointer in the save file is not valid, so we grab a new one
                        chan.decoder = idSampleDecoder.Alloc()
                    }
                    savefile.ReadString(soundShader)
                    chan.soundShader = DeclManager.declManager.FindSound(soundShader)
                    savefile.ReadString(soundShader) // load savegames with s_noSound 1
                    if (snd_system.soundSystemLocal.soundCache != null) {
                        chan.leadinSample = snd_system.soundSystemLocal.soundCache!!.FindSound(soundShader, false)
                    } else {
                        chan.leadinSample = null
                    }

                    // adjust the hardware start time
                    chan.trigger44kHzTime += soundTimeOffset

                    // make sure we start up the hardware voice if needed
                    chan.triggered = chan.triggerState
                    chan.openalStreamingOffset =
                        currentSoundTime - chan.trigger44kHzTime // DG: round up openalStreamingOffset to multiple of 8, so it still has an even number
                    //  if we calculate "how many 11kHz stereo samples do we need to decode" and don't
                    //  run into a "I need one more sample apparently, so decode 0 stereo samples"
                    //  situation that could cause an endless loop.. (44kHz/11kHz = 4; *2 for stereo => 8)
                    chan.openalStreamingOffset = (chan.openalStreamingOffset + 7) and 7.inv()

                    // adjust the hardware fade time
                    if (chan.channelFade.fadeStart44kHz != 0) {
                        chan.channelFade.fadeStart44kHz += soundTimeOffset
                        chan.channelFade.fadeEnd44kHz += soundTimeOffset
                    }

                    // next command
                    channel = savefile.ReadInt()
                }
                i++
            }
            if (Session.session.GetSaveGameVersion() >= 17) {
                slowmoActive = savefile.ReadBool()
                slowmoSpeed = savefile.ReadFloat()
                enviroSuitActive = savefile.ReadBool()
            } else {
                slowmoActive = false
                slowmoSpeed = 0.0f
                enviroSuitActive = false
            }
        }

        // FIX: Was a stub throwing TODO_Exception. Implemented from C++ snd_world.cpp:1433-1458.
        fun ReadFromSaveGameSoundChannel(saveGame: idFile, ch: idSoundChannel) {
            ch.triggerState = saveGame.ReadBool()
            saveGame.ReadChar() // padding byte
            saveGame.ReadChar() // padding byte
            saveGame.ReadChar() // padding byte
            ch.trigger44kHzTime = saveGame.ReadInt()
            ch.triggerGame44kHzTime = saveGame.ReadInt()
            if (ch.parms == null) {
                ch.parms = snd_shader.soundShaderParms_t()
            }
            ReadFromSaveGameSoundShaderParams(saveGame, ch.parms!!)
            saveGame.ReadInt() // leadinSample pointer (not valid, set NULL)
            ch.leadinSample = null
            ch.triggerChannel = saveGame.ReadInt()
            saveGame.ReadInt() // soundShader pointer (not valid, set NULL)
            ch.soundShader = null
            saveGame.ReadInt() // decoder pointer (not valid, set NULL)
            ch.decoder = null
            ch.diversity = saveGame.ReadFloat()
            ch.lastVolume = saveGame.ReadFloat()
            for (m in 0 until 6) {
                ch.lastV[m] = saveGame.ReadFloat()
            }
            ch.channelFade.fadeStart44kHz = saveGame.ReadInt()
            ch.channelFade.fadeEnd44kHz = saveGame.ReadInt()
            ch.channelFade.fadeStartVolume = saveGame.ReadFloat()
            ch.channelFade.fadeEndVolume = saveGame.ReadFloat()
        }

        fun ReadFromSaveGameSoundShaderParams(saveGame: idFile, params: snd_shader.soundShaderParms_t) {
            params.minDistance = saveGame.ReadFloat()
            params.maxDistance = saveGame.ReadFloat()
            params.volume = saveGame.ReadFloat()
            params.shakes = saveGame.ReadFloat()
            params.soundShaderFlags = saveGame.ReadInt()
            params.soundClass = saveGame.ReadInt()
        }

        fun WriteToSaveGameSoundChannel(saveGame: idFile, ch: idSoundChannel) {
            saveGame.WriteBool(ch.triggerState)
            saveGame.WriteUnsignedChar(Char(0))
            saveGame.WriteUnsignedChar(Char(0))
            saveGame.WriteUnsignedChar(Char(0))
            saveGame.WriteInt(ch.trigger44kHzTime)
            saveGame.WriteInt(ch.triggerGame44kHzTime)
            WriteToSaveGameSoundShaderParams(saveGame, ch.parms!!)
            saveGame.WriteInt(0 /* ch.leadinSample */)
            saveGame.WriteInt(ch.triggerChannel)
            saveGame.WriteInt(0 /* ch.soundShader */)
            saveGame.WriteInt(0 /* ch.decoder */)
            saveGame.WriteFloat(ch.diversity)
            saveGame.WriteFloat(ch.lastVolume)
            for (m in 0 until 6) {
                saveGame.WriteFloat(ch.lastV[m])
            }
            saveGame.WriteInt(ch.channelFade.fadeStart44kHz)
            saveGame.WriteInt(ch.channelFade.fadeEnd44kHz)
            saveGame.WriteFloat(ch.channelFade.fadeStartVolume)
            saveGame.WriteFloat(ch.channelFade.fadeEndVolume)
        }

        fun WriteToSaveGameSoundShaderParams(saveGame: idFile, params: snd_shader.soundShaderParms_t) {
            saveGame.WriteFloat(params.minDistance)
            saveGame.WriteFloat(params.maxDistance)
            saveGame.WriteFloat(params.volume)
            saveGame.WriteFloat(params.shakes)
            saveGame.WriteInt(params.soundShaderFlags)
            saveGame.WriteInt(params.soundClass)
        }

        override fun SetSlowmo(active: Boolean) {
            slowmoActive = active
        }

        override fun SetSlowmoSpeed(speed: Float) {
            slowmoSpeed = speed
        }

        override fun SetEnviroSuit(active: Boolean) {
            enviroSuitActive = active
        }

        //=======================================
        /*
         ===============
         idSoundWorldLocal::Shutdown

         this is called from the main thread
         ===============
         */
        fun Shutdown() {
            var i: Int
            if (snd_system.soundSystemLocal.currentSoundWorld == this) {
                snd_system.soundSystemLocal.currentSoundWorld = null
            }
            AVIClose()

            // delete emitters before deleting the listenerSlot, so their sources aren't
            // associated with the listenerSlot anymore
            i = 0
            while (i < emitters.Num()) {
                if (emitters[i] != null) {
                    emitters[i].Clear() // C++ delete calls destructor -> Clear() -> ALStop() on all channels
                    emitters[i] = idSoundEmitterLocal()
                }
                i++
            }

            // FIX: Missing EFX cleanup from C++ Shutdown(). Deletes auxiliary effect slot and filters.
            if (useEFXReverb) {
                if (EXTEfx.alIsAuxiliaryEffectSlot(listenerSlot)) {
                    EXTEfx.alAuxiliaryEffectSloti(listenerSlot, EXTEfx.AL_EFFECTSLOT_EFFECT, EXTEfx.AL_EFFECTSLOT_NULL)
                    EXTEfx.alDeleteAuxiliaryEffectSlots(listenerSlot)
                    listenerSlot = EXTEfx.AL_EFFECTSLOT_NULL
                }

                if (listenerAreFiltersInitialized) {
                    listenerAreFiltersInitialized = false

                    if (listenerFilters[0] != EXTEfx.AL_FILTER_NULL && listenerFilters[1] != EXTEfx.AL_FILTER_NULL) {
                        EXTEfx.alDeleteFilters(listenerFilters[0])
                        EXTEfx.alDeleteFilters(listenerFilters[1])
                        listenerFilters[0] = EXTEfx.AL_FILTER_NULL
                        listenerFilters[1] = EXTEfx.AL_FILTER_NULL
                    }
                }
                listenerSlotReverbGain = 1.0f
            }

            localSound = null
        }

        fun Init(rw: idRenderWorld) {
            this.rw = rw
            writeDemo = null
            listenerAxis.Identity()
            listenerPos.Zero()
            listenerPrivateId = 0
            listenerQU.Zero()
            listenerArea = 0
            listenerAreaName.set("Undefined")

            // FIX: Missing EFX initialization from C++ Init(). Creates auxiliary effect slot and lowpass filters.
            // Guard: skip if OpenAL context not yet created (happens when AllocSoundWorld is called during static init)
            if (useEFXReverb && snd_system.soundSystemLocal.openalContext != 0L) {
                if (!EXTEfx.alIsAuxiliaryEffectSlot(listenerSlot)) {
                    AL10.alGetError()
                    listenerSlot = EXTEfx.alGenAuxiliaryEffectSlots()
                    val e = AL10.alGetError()
                    if (e != AL10.AL_NO_ERROR) {
                        Common.common.Warning("idSoundWorldLocal::Init: alGenAuxiliaryEffectSlots failed: 0x%x", e)
                        listenerSlot = EXTEfx.AL_EFFECTSLOT_NULL
                    }
                }

                if (!listenerAreFiltersInitialized) {
                    listenerAreFiltersInitialized = true

                    AL10.alGetError()
                    listenerFilters[0] = EXTEfx.alGenFilters()
                    listenerFilters[1] = EXTEfx.alGenFilters()
                    val e = AL10.alGetError()
                    if (e != AL10.AL_NO_ERROR) {
                        Common.common.Warning("idSoundWorldLocal::Init: alGenFilters failed: 0x%x", e)
                        listenerFilters[0] = EXTEfx.AL_FILTER_NULL
                        listenerFilters[1] = EXTEfx.AL_FILTER_NULL
                    } else {
                        EXTEfx.alFilteri(
                            listenerFilters[0], EXTEfx.AL_FILTER_TYPE, EXTEfx.AL_FILTER_LOWPASS
                        ) // original EAX occlusion value was -1150
                        // pow(10.0, (-1150*0.25*1.0)/2000.0)
                        EXTEfx.alFilterf(
                            listenerFilters[0], EXTEfx.AL_LOWPASS_GAIN, 0.718208f
                        ) // pow(10.0, (-1150*1.0)/2000.0)
                        EXTEfx.alFilterf(listenerFilters[0], EXTEfx.AL_LOWPASS_GAINHF, 0.266073f)

                        EXTEfx.alFilteri(
                            listenerFilters[1], EXTEfx.AL_FILTER_TYPE, EXTEfx.AL_FILTER_LOWPASS
                        ) // original EAX occlusion value was -1150
                        // pow(10.0, (-1150*(0.25+1.5-1.0))/2000.0)
                        EXTEfx.alFilterf(
                            listenerFilters[1], EXTEfx.AL_LOWPASS_GAIN, 0.370467f
                        ) // pow(10.0, (-1150*1.5)/2000.0)
                        EXTEfx.alFilterf(listenerFilters[1], EXTEfx.AL_LOWPASS_GAINHF, 0.137246f)
                    } // allow reducing the gain effect globally via s_alReverbGain CVar
                    listenerSlotReverbGain = idSoundSystemLocal.s_alReverbGain.GetFloat()
                    EXTEfx.alAuxiliaryEffectSlotf(listenerSlot, EXTEfx.AL_EFFECTSLOT_GAIN, listenerSlotReverbGain)
                }
            }

            gameMsec = 0
            game44kHz = 0
            pause44kHz = -1
            lastAVI44kHz = 0
            for (i in 0 until snd_shader.SOUND_MAX_CLASSES) {
                soundClassFade[i] = idSoundFade()
                soundClassFade[i].Clear()
            }

            // fill in the 0 index spot
            val placeHolder = idSoundEmitterLocal()
            emitters.Append(placeHolder)
            fpa[5] = null
            fpa[4] = fpa[5]
            fpa[3] = fpa[4]
            fpa[2] = fpa[3]
            fpa[1] = fpa[2]
            fpa[0] = fpa[1]
            aviDemoPath.set("")
            aviDemoName.set("")
            localSound = null
            slowmoActive = false
            slowmoSpeed = 0.0f
            enviroSuitActive = false
        }

        // update
        fun ForegroundUpdate(current44kHzTime: Int) {
            var current44kHzTime = current44kHzTime
            var j: Int
            var k: Int
            var def: idSoundEmitterLocal?
            if (!snd_system.soundSystemLocal.isInitialized) {
                return
            }
            Sys_EnterCriticalSection()

            // if we are recording an AVI demo, don't use hardware time
            if (fpa[0] != null) {
                current44kHzTime = lastAVI44kHz
            }

            //
            // check to see if each sound is visible or not
            // speed up by checking maxdistance to origin
            // although the sound may still need to play if it has
            // just become occluded so it can ramp down to 0
            //
            j = 1
            while (j < emitters.Num()) {
                def = emitters[j]
                if (def.removeStatus >= snd_emitter.REMOVE_STATUS_SAMPLEFINISHED) {
                    j++
                    continue
                }

                // see if our last channel just finished
                def.CheckForCompletion(current44kHzTime)
                if (!def.playing) {
                    j++
                    continue
                }

                // update virtual origin / distance, etc
                def.Spatialize(listenerPos, listenerArea, rw!!)

                // per-sound debug options
                if (idSoundSystemLocal.s_drawSounds.GetInteger() != 0 && rw != null) {
                    if (def.distance < def.maxDistance || idSoundSystemLocal.s_drawSounds.GetInteger() > 1) {
                        val ref = idBounds()
                        ref.Clear()
                        ref.AddPoint(idVec3(-10.0f, -10.0f, -10.0f))
                        ref.AddPoint(idVec3(10.0f, 10.0f, 10.0f))
                        val vis = 1.0f - def.distance / def.maxDistance

                        // draw a box
                        rw!!.DebugBounds(idVec4(vis, 0.25f, vis, vis), ref, def.origin)

                        // draw an arrow to the audible position, possible a portal center
                        if (def.origin != def.spatializedOrigin) {
                            rw!!.DebugArrow(colorRed, def.origin, def.spatializedOrigin, 4)
                        }

                        // draw the index
                        val textPos = idVec3(def.origin)
                        textPos.minusAssign(2, 8.0f)
                        rw!!.DrawText(
                            Str.va("%d", def.index), textPos, 0.1f, idVec4(1.0f, 0.0f, 0.0f, 1.0f), listenerAxis
                        )
                        textPos.plusAssign(2, 8.0f)

                        // run through all the channels
                        k = 0
                        while (k < snd_local.SOUND_MAX_CHANNELS) {
                            val chan = def.channels[k]

                            // see if we have a sound triggered on this channel
                            if (!chan.triggerState) {
                                k++
                                continue
                            }

                            //					char	[]text = new char[1024];
                            var text: String
                            val min = chan.parms!!.minDistance
                            val max = chan.parms!!.maxDistance
                            val defaulted = if (chan.leadinSample!!.defaultSound) "(DEFAULTED)" else ""
                            text = String.format(
                                "%s (%d/%d %d/%d)%s",
                                chan.soundShader!!.GetName(),
                                def.distance.toInt(),
                                def.realDistance.toInt(),
                                min.toInt(),
                                max.toInt(),
                                defaulted
                            )
                            rw!!.DrawText(text, textPos, 0.1f, idVec4(1.0f, 0.0f, 0.0f, 1.0f), listenerAxis)
                            textPos.plusAssign(2, 8.0f)
                            k++
                        }
                    }
                }
                j++
            }
            Sys_LeaveCriticalSection()

            //
            // the sound meter
            //
            if (idSoundSystemLocal.s_showLevelMeter.GetInteger() != 0) {
                val gui: Material.idMaterial? =
                    DeclManager.declManager.FindMaterial("guis/assets/soundmeter/audiobg", false)
                if (gui != null) {
                    val foo: shaderStage_t = gui.GetStage(0)!!
                    if (foo.texture.cinematic[0] == null) {
                        foo.texture.cinematic[0] = idSndWindow()
                    }
                }
            }

            //
            // optionally dump out the generated sound
            //
            if (fpa[0] != null) {
                AVIUpdate()
            }
        }

        fun OffsetSoundTime(offset44kHz: Int) {
            var i: Int
            var j: Int
            i = 0
            while (i < emitters.Num()) {
                if (emitters[i] == null) {
                    i++
                    continue
                }
                j = 0
                while (j < snd_local.SOUND_MAX_CHANNELS) {
                    val chan = emitters[i].channels[j]
                    if (!chan.triggerState) {
                        j++
                        continue
                    }
                    chan.trigger44kHzTime += offset44kHz
                    j++
                }
                i++
            }
        }

        fun AllocLocalSoundEmitter(): idSoundEmitterLocal {
            var i: Int
            var index: Int
            var def: idSoundEmitterLocal = idSoundEmitterLocal()
            index = -1

            // never use the 0 index spot
            i = 1
            while (i < emitters.Num()) {
                def = emitters[i]

                // check for a completed and freed spot
                if (def.removeStatus >= snd_emitter.REMOVE_STATUS_SAMPLEFINISHED) {
                    index = i
                    if (idSoundSystemLocal.s_showStartSound.GetInteger() != 0) {
                        Common.common.Printf("sound: recycling sound def %d\n", i)
                    }
                    break
                }
                i++
            }
            if (index == -1) { // append a brand new one
                def = idSoundEmitterLocal()

                // we need to protect this from the async thread
                Sys_EnterCriticalSection()
                index = emitters.Append(def)
                Sys_LeaveCriticalSection()
                if (idSoundSystemLocal.s_showStartSound.GetInteger() != 0) {
                    Common.common.Printf("sound: appended new sound def %d\n", index)
                }
            }
            def.Clear()
            def.index = index
            def.removeStatus = snd_emitter.REMOVE_STATUS_ALIVE
            def.soundWorld = this
            return def
        }

        fun CalcEars(
            numSpeakers: Int,
            spatializedOrigin: idVec3,
            listenerPos: idVec3,
            listenerAxis: idMat3,
            ears: FloatArray /*[6]*/,
            spatialize: Float
        ) {
            val sx = spatializedOrigin.x - listenerPos.x
            val sy = spatializedOrigin.y - listenerPos.y
            val sz = spatializedOrigin.z - listenerPos.z
            var ox = sx * listenerAxis[0].x + sy * listenerAxis[0].y + sz * listenerAxis[0].z
            var oy = sx * listenerAxis[1].x + sy * listenerAxis[1].y + sz * listenerAxis[1].z
            var oz = sx * listenerAxis[2].x + sy * listenerAxis[2].y + sz * listenerAxis[2].z
            val lenSqr = ox * ox + oy * oy + oz * oz
            if (lenSqr != 0.0f) {
                val invLen = idMath.RSqrt(lenSqr)
                ox *= invLen
                oy *= invLen
                oz *= invLen
            }
            if (numSpeakers == 6) {
                for (i in 0..5) {
                    if (i == 3) {
                        ears[i] = idSoundSystemLocal.s_subFraction.GetFloat() // subwoofer
                        continue
                    }
                    val speaker = speakerVector[i]
                    val dot = ox * speaker.x + oy * speaker.y + oz * speaker.z
                    ears[i] =
                        (idSoundSystemLocal.s_dotbias6.GetFloat() + dot) / (1.0f + idSoundSystemLocal.s_dotbias6.GetFloat())
                    if (ears[i] < idSoundSystemLocal.s_minVolume6.GetFloat()) {
                        ears[i] = idSoundSystemLocal.s_minVolume6.GetFloat()
                    }
                }
            } else {
                val dot = oy
                var dotBias: Float = idSoundSystemLocal.s_dotbias2.GetFloat()

                // when we are inside the minDistance, start reducing the amount of spatialization
                // so NPC voices right in front of us aren't quieter that off to the side
                dotBias += (idSoundSystemLocal.s_spatializationDecay.GetFloat() - dotBias) * (1.0f - spatialize)
                ears[0] = (idSoundSystemLocal.s_dotbias2.GetFloat() + dot) / (1.0f + dotBias)
                ears[1] = (idSoundSystemLocal.s_dotbias2.GetFloat() - dot) / (1.0f + dotBias)
                if (ears[0] < idSoundSystemLocal.s_minVolume2.GetFloat()) {
                    ears[0] = idSoundSystemLocal.s_minVolume2.GetFloat()
                }
                if (ears[1] < idSoundSystemLocal.s_minVolume2.GetFloat()) {
                    ears[1] = idSoundSystemLocal.s_minVolume2.GetFloat()
                }
                ears[5] = 0.0f
                ears[4] = ears[5]
                ears[3] = ears[4]
                ears[2] = ears[3]
            }
        }

        fun AddChannelContribution(
            sound: idSoundEmitterLocal,
            chan: idSoundChannel,
            current44kHz: Int,
            numSpeakers: Int,
            finalMixBuffer: FloatArray
        ) {
            var j: Int
            var volume: Float

            //
            // get the sound definition and parameters from the entity
            //
            val parms = chan.parms
            assert(chan.triggerState)

            // fetch the actual wave file and see if it's valid
            val sample = chan.leadinSample ?: return

            // if you don't want to hear all the beeps from missing sounds
            if (sample.defaultSound && !idSoundSystemLocal.s_playDefaultSound.GetBool()) {
                return
            }

            // get the actual shader
            val shader = chan.soundShader ?: return

            // this might happen if the foreground thread just deleted the sound emitter
            var maxD = parms!!.maxDistance
            val minD = parms.minDistance
            var mask = shader.speakerMask
            var omni = parms.soundShaderFlags and snd_shader.SSF_OMNIDIRECTIONAL != 0
            val looping = parms.soundShaderFlags and snd_shader.SSF_LOOPING != 0
            var global = parms.soundShaderFlags and snd_shader.SSF_GLOBAL != 0
            val noOcclusion =
                parms.soundShaderFlags and snd_shader.SSF_NO_OCCLUSION != 0 || !idSoundSystemLocal.s_useOcclusion.GetBool()

            // speed goes from 1 to 0.2f
            if (idSoundSystemLocal.s_slowAttenuate.GetBool() && slowmoActive && !chan.disallowSlow) {
                maxD *= slowmoSpeed
            }

            // stereo samples are always omni
            if (sample.objectInfo.nChannels == 2) {
                omni = true
            }

            // if the sound is playing from the current listener, it will not be spatialized at all
            if (sound.listenerId == listenerPrivateId) {
                global = true
            }

            //
            // see if it's in range
            //
            // convert volumes from decibels to float scale
            // leadin volume scale for shattering lights
            // this isn't exactly correct, because the modified volume will get applied to
            // some initial chunk of the loop as well, because the volume is scaled for the
            // entire mix buffer
            volume =
                if (shader.leadinVolume != 0.0f && current44kHz - chan.trigger44kHzTime < sample.LengthIn44kHzSamples()) {
                    snd_system.soundSystemLocal.dB2Scale(shader.leadinVolume)
                } else {
                    snd_system.soundSystemLocal.dB2Scale(parms.volume)
                }

            // FIX: dhewm3 moved global volume scale (s_volume) to AFTER the clamp+0.333 scaling.
            // Removed the original s_volume application here — it's now applied at lines below.

            // volume fading
            var fadeDb = chan.channelFade.FadeDbAt44kHz(current44kHz)
            volume *= snd_system.soundSystemLocal.dB2Scale(fadeDb)
            fadeDb = soundClassFade[parms.soundClass].FadeDbAt44kHz(current44kHz)
            volume *= snd_system.soundSystemLocal.dB2Scale(fadeDb)

            //
            // if it's a global sound then
            // it's not affected by distance or occlusion
            //
            var spatialize = 1.0f
            val spatializedOriginInMeters = channelSpatializedOriginInMeters
            if (!global) {
                val dlen: Float
                dlen = if (noOcclusion) { // use the real origin and distance
                    spatializedOriginInMeters.set(
                        sound.origin.x * snd_shader.DOOM_TO_METERS,
                        sound.origin.y * snd_shader.DOOM_TO_METERS,
                        sound.origin.z * snd_shader.DOOM_TO_METERS
                    )
                    sound.realDistance
                } else { // use the possibly portal-occluded origin and distance
                    spatializedOriginInMeters.set(
                        sound.spatializedOrigin.x * snd_shader.DOOM_TO_METERS,
                        sound.spatializedOrigin.y * snd_shader.DOOM_TO_METERS,
                        sound.spatializedOrigin.z * snd_shader.DOOM_TO_METERS
                    )
                    sound.distance
                }

                // reduce volume based on distance
                if (dlen >= maxD) {
                    volume = 0.0f
                } else if (dlen > minD) {
                    var frac = idMath.ClampFloat(0.0f, 1.0f, 1.0f - (dlen - minD) / (maxD - minD))
                    if (idSoundSystemLocal.s_quadraticFalloff.GetBool()) {
                        frac *= frac
                    }
                    volume *= frac
                } else if (minD > 0.0f) { // we tweak the spatialization bias when you are inside the minDistance
                    spatialize = dlen / minD
                }
            }

            //
            // if it is a private sound, set the volume to zero
            // unless we match the listenerId
            //
            if (parms.soundShaderFlags and snd_shader.SSF_PRIVATE_SOUND != 0) {
                if (sound.listenerId != listenerPrivateId) {
                    volume = 0.0f
                }
            }
            if (parms.soundShaderFlags and snd_shader.SSF_ANTI_PRIVATE_SOUND != 0) {
                if (sound.listenerId == listenerPrivateId) {
                    volume = 0.0f
                }
            }

            // when many loud sounds play simultaneously. Global volume scale is applied AFTER
            // clamping so reducing s_volume doesn't cause different weapon volume issues.
            // See https://github.com/dhewm/dhewm3/issues/179
            if (idSoundSystemLocal.s_scaleDownAndClamp.GetBool()) {
                if (volume > 1.0f) {
                    volume = 1.0f
                }
                volume *= 0.333f
            }

            // global volume scale
            volume *= snd_system.soundSystemLocal.dB2Scale(idSoundSystemLocal.s_volume.GetFloat())

            //
            // do we have anything to add?
            //
            if (volume < snd_local.SND_EPSILON && chan.lastVolume < snd_local.SND_EPSILON) {
                return
            }
            chan.lastVolume = volume

            //
            // fetch the sound from the cache as 44kHz, 16 bit samples
            //
            val offset =
                current44kHz - chan.trigger44kHzTime //            float[] inputSamples = new float[MIXBUFFER_SAMPLES * 2 + 16]; //            float[] alignedInputSamples = (float[]) ((((int) inputSamples) + 15) & ~15);
            val alignedInputSamples = mixInputSamples

            // allocate and initialize hardware source
            if (sound.removeStatus < snd_emitter.REMOVE_STATUS_SAMPLEFINISHED) {
                if (!AL10.alIsSource(chan.openalSource)) {
                    chan.openalSource = snd_system.soundSystemLocal.AllocOpenALSource(
                        chan,
                        !chan.leadinSample!!.hardwareBuffer || !chan.soundShader!!.entries[0]!!.hardwareBuffer || looping,
                        chan.leadinSample!!.objectInfo.nChannels == 2
                    )
                }
                if (AL10.alIsSource(chan.openalSource)) {

                    // stop source if needed..
                    if (chan.triggered) {
                        AL10.alSourceStop(chan.openalSource)
                    }

                    // update source parameters
                    if (global || omni) {
                        AL10.alSourcei(chan.openalSource, AL10.AL_SOURCE_RELATIVE, AL10.AL_TRUE)
                        AL10.alSource3f(chan.openalSource, AL10.AL_POSITION, 0.0f, 0.0f, 0.0f)
                        AL10.alSourcef(chan.openalSource, AL10.AL_GAIN, min(volume.toFloat(), 1.0f))
                    } else {
                        AL10.alSourcei(chan.openalSource, AL10.AL_SOURCE_RELATIVE, AL10.AL_FALSE)
                        AL10.alSource3f(
                            chan.openalSource,
                            AL10.AL_POSITION,
                            -spatializedOriginInMeters.y.toFloat(),
                            spatializedOriginInMeters.z.toFloat(),
                            -spatializedOriginInMeters.x.toFloat()
                        )
                        AL10.alSourcef(chan.openalSource, AL10.AL_GAIN, min(volume.toFloat(), 1.0f))
                    } // FIX: dhewm3 — looping sounds with a leadin can't use HW buffer + AL_LOOPING
                    // because we need to switch from leadin to the looped sound
                    // See https://github.com/dhewm/dhewm3/issues/291
                    val haveLeadin = chan.soundShader!!.numLeadins > 0
                    AL10.alSourcei(
                        chan.openalSource,
                        AL10.AL_LOOPING,
                        if (looping && chan.soundShader!!.entries[0]!!.hardwareBuffer && !haveLeadin) AL10.AL_TRUE else AL10.AL_FALSE
                    )
                    AL10.alSourcef(chan.openalSource, AL10.AL_REFERENCE_DISTANCE, minD.toFloat())
                    AL10.alSourcef(chan.openalSource, AL10.AL_MAX_DISTANCE, maxD.toFloat())
                    AL10.alSourcef(
                        chan.openalSource,
                        AL10.AL_PITCH,
                        if (slowmoActive && !chan.disallowSlow) slowmoSpeed.toFloat() else 1.0f
                    )

                    // FIX: dhewm3 uses EFX source sends instead of proprietary EAX occlusion
                    if (useEFXReverb) {
                        if (enviroSuitActive) {
                            AL10.alSourcei(chan.openalSource, EXTEfx.AL_DIRECT_FILTER, listenerFilters[0])
                            alSource3i(
                                chan.openalSource, EXTEfx.AL_AUXILIARY_SEND_FILTER, listenerSlot, 0, listenerFilters[1]
                            )
                        } else {
                            AL10.alSourcei(chan.openalSource, EXTEfx.AL_DIRECT_FILTER, EXTEfx.AL_FILTER_NULL)
                            alSource3i(
                                chan.openalSource,
                                EXTEfx.AL_AUXILIARY_SEND_FILTER,
                                listenerSlot,
                                0,
                                EXTEfx.AL_FILTER_NULL
                            )
                        }
                    }

                    if (!looping && chan.leadinSample!!.hardwareBuffer || looping && !haveLeadin && chan.soundShader!!.entries[0]!!.hardwareBuffer) { // handle uncompressed (non streaming) single shot and looping sounds
                        if (chan.triggered) {
                            AL10.alSourcei(
                                chan.openalSource,
                                AL10.AL_BUFFER,
                                if (looping) chan.soundShader!!.entries[0]!!.openalBuffer else chan.leadinSample!!.openalBuffer
                            )
                        }
                    } else {
                        val   /*ALint*/finishedbuffers: Int
                        val buffers = streamingBufferScratch

                        // handle streaming sounds (decode on the fly) both single shot AND looping
                        if (chan.triggered) {
                            AL10.alSourcei(chan.openalSource, AL10.AL_BUFFER, 0)
                            AL10.alDeleteBuffers(chan.lastopenalStreamingBuffer)
                            chan.lastopenalStreamingBuffer.put(0, chan.openalStreamingBuffer[0])
                            chan.lastopenalStreamingBuffer.put(1, chan.openalStreamingBuffer[1])
                            chan.lastopenalStreamingBuffer.put(2, chan.openalStreamingBuffer[2])
                            AL10.alGenBuffers(chan.openalStreamingBuffer)
                            buffers[0] = chan.openalStreamingBuffer[0]
                            buffers[1] = chan.openalStreamingBuffer[1]
                            buffers[2] = chan.openalStreamingBuffer[2]
                            finishedbuffers = 3
                        } else {
                            finishedbuffers = AL10.alGetSourcei(
                                chan.openalSource, AL10.AL_BUFFERS_PROCESSED
                            )
                            for (i in 0 until finishedbuffers) { //jake2
                                buffers[i] = AL10.alSourceUnqueueBuffers(chan.openalSource)
                            }
                            if (finishedbuffers == 3) {
                                chan.triggered = true
                            }
                        }
                        val length = MIXBUFFER_SAMPLES * sample.objectInfo.nChannels
                        j = 0
                        while (j < finishedbuffers) {
                            mixInputSamplesBuffer.clear()
                            chan.GatherChannelSamples(
                                chan.openalStreamingOffset * sample.objectInfo.nChannels, length, mixInputSamplesBuffer
                            )
                            streamingSampleBytes.clear()
                            streamingSampleBytes.limit(length * java.lang.Short.BYTES)
                            for (i in 0 until length) {
                                if (alignedInputSamples[i] < -32768.0f) {
                                    streamingSampleShorts.put(i, Short.MIN_VALUE)
                                } else if (alignedInputSamples[i] > 32767.0f) {
                                    streamingSampleShorts.put(i, Short.MAX_VALUE)
                                } else {
                                    val bla = idMath.FtoiFast(alignedInputSamples[i]).toShort()
                                    streamingSampleShorts.put(i, bla)
                                }
                            }
                            AL10.alBufferData(
                                buffers[j],
                                if (chan.leadinSample!!.objectInfo.nChannels == 1) AL10.AL_FORMAT_MONO16 else AL10.AL_FORMAT_STEREO16,
                                streamingSampleBytes,
                                44100
                            )
                            chan.openalStreamingOffset += MIXBUFFER_SAMPLES
                            j++
                        }
                        for (i in 0 until finishedbuffers) {
                            AL10.alSourceQueueBuffers(chan.openalSource, buffers[i])
                        }
                    }

                    // (re)start if needed..
                    if (chan.triggered) {
                        AL10.alSourcePlay(chan.openalSource)
                        chan.triggered = false
                    }
                }
            } else if (Common.com_asyncSound.GetInteger() == 2) {
                if (slowmoActive && !chan.disallowSlow) {
                    val slow = sound.GetSlowChannel(chan)
                    slow.AttachSoundChannel(chan)
                    if (sample.objectInfo.nChannels == 2) { // need to add a stereo path, but very few samples go through this
                        alignedInputSamples.fill(0.0f, 0, MIXBUFFER_SAMPLES * 2)
                    } else {
                        slow.GatherChannelSamples(offset, MIXBUFFER_SAMPLES, alignedInputSamples)
                    }
                    sound.SetSlowChannel(chan, slow)
                } else {
                    sound.ResetSlowChannel(chan)

                    // if we are getting a stereo sample adjust accordingly
                    if (sample.objectInfo.nChannels == 2) { // we should probably check to make sure any looping is also to a stereo sample...
                        mixInputSamplesBuffer.clear()
                        chan.GatherChannelSamples(
                            offset * 2, MIXBUFFER_SAMPLES * 2, mixInputSamplesBuffer
                        )
                    } else {
                        mixInputSamplesBuffer.clear()
                        chan.GatherChannelSamples(offset, MIXBUFFER_SAMPLES, mixInputSamplesBuffer)
                    }
                }

                //
                // work out the left / right ear values
                //
                val ears = mixEars
                if (global || omni) { // same for all speakers
                    for (i in 0..5) {
                        ears[i] = idSoundSystemLocal.s_globalFraction.GetFloat() * volume
                    }
                    ears[3] = idSoundSystemLocal.s_subFraction.GetFloat() * volume // subwoofer
                } else {
                    CalcEars(numSpeakers, spatializedOriginInMeters, listenerPos, listenerAxis, ears, spatialize)
                    for (i in 0..5) {
                        ears[i] *= volume
                    }
                }

                // if the mask is 0, it really means do every channel
                if (0 == mask) {
                    mask = 255
                } // cleared mask bits set the mix volume to zero
                for (i in 0..5) {
                    if (0 == mask and (1 shl i)) {
                        ears[i] = 0.0f
                    }
                }

                // if sounds are generally normalized, using a mixing volume over 1.0f will
                // almost always cause clipping noise.  If samples aren't normalized, there
                // is a good call to allow overvolumes
                if (idSoundSystemLocal.s_clipVolumes.GetBool() && 0 == parms.soundShaderFlags and snd_shader.SSF_UNCLAMPED) {
                    for (i in 0..5) {
                        if (ears[i] > 1.0f) {
                            ears[i] = 1.0f
                        }
                    }
                }

                // if this is the very first mixing block, set the lastV
                // to the current volume
                if (current44kHz == chan.trigger44kHzTime) {
                    j = 0
                    while (j < 6) {
                        chan.lastV[j] = ears[j]
                        j++
                    }
                }
                if (numSpeakers == 6) {
                    if (sample.objectInfo.nChannels == 1) {
                        SIMDProcessor!!.MixSoundSixSpeakerMono(
                            finalMixBuffer, alignedInputSamples, MIXBUFFER_SAMPLES, chan.lastV, ears
                        )
                    } else {
                        SIMDProcessor!!.MixSoundSixSpeakerStereo(
                            finalMixBuffer, alignedInputSamples, MIXBUFFER_SAMPLES, chan.lastV, ears
                        )
                    }
                } else {
                    if (sample.objectInfo.nChannels == 1) {
                        SIMDProcessor!!.MixSoundTwoSpeakerMono(
                            finalMixBuffer, alignedInputSamples, MIXBUFFER_SAMPLES, chan.lastV, ears
                        )
                    } else {
                        SIMDProcessor!!.MixSoundTwoSpeakerStereo(
                            finalMixBuffer, alignedInputSamples, MIXBUFFER_SAMPLES, chan.lastV, ears
                        )
                    }
                }
                j = 0
                while (j < 6) {
                    chan.lastV[j] = ears[j]
                    j++
                }
            }
            snd_system.soundSystemLocal.soundStats.activeSounds++
        }

        /*
         ===================
         idSoundWorldLocal::MixLoop

         Sum all sound contributions into finalMixBuffer, an unclamped float buffer holding
         all output channels.  MIXBUFFER_SAMPLES samples will be created, with each sample consisting
         of 2 or 6 floats depending on numSpeakers.

         this is normally called from the sound thread, but also from the main thread
         for AVIdemo writing
         ===================
         */
        fun MixLoop(current44kHz: Int, numSpeakers: Int, finalMixBuffer: FloatArray) {
            var i: Int
            var j: Int
            var sound: idSoundEmitterLocal?

            // if noclip flying outside the world, leave silence
            if (listenerArea == -1) {
                AL10.alListenerf(AL10.AL_GAIN, 0.0f)
                return
            }

            // update the listener position and orientation
            val listenerPosition = listenerPositionScratch
            listenerPosition[0] = -listenerPos.y.toFloat()
            listenerPosition[1] = listenerPos.z.toFloat()
            listenerPosition[2] = -listenerPos.x.toFloat()
            val listenerOrientation = listenerOrientationScratch
            listenerOrientation.clear()
            listenerOrientation.put(0, -listenerAxis[0].y.toFloat())
            listenerOrientation.put(1, +listenerAxis[0].z.toFloat())
            listenerOrientation.put(2, -listenerAxis[0].x.toFloat())
            listenerOrientation.put(3, -listenerAxis[2].y.toFloat())
            listenerOrientation.put(4, +listenerAxis[2].z.toFloat())
            listenerOrientation.put(5, -listenerAxis[2].x.toFloat())
            AL10.alListenerf(AL10.AL_GAIN, 1.0f)
            AL10.alListener3f(AL10.AL_POSITION, listenerPosition[0], listenerPosition[1], listenerPosition[2])
            AL10.alListenerfv(AL10.AL_ORIENTATION, listenerOrientation)

            // FIX: dhewm3 EFX reverb lookup replaces old commented-out EAX code
            if (useEFXReverb && snd_system.soundSystemLocal.efxloaded) {
                val effectHandle = effectHandleScratch
                effectHandle[0] = 0

                // allow reducing the gain effect globally via s_alReverbGain CVar
                val gain = idSoundSystemLocal.s_alReverbGain.GetFloat()
                if (listenerSlotReverbGain != gain) {
                    listenerSlotReverbGain = gain
                    EXTEfx.alAuxiliaryEffectSlotf(listenerSlot, EXTEfx.AL_EFFECTSLOT_GAIN, gain)
                }

                if (listenerAreaEffectNameArea != listenerArea) {
                    listenerAreaEffectNameArea = listenerArea
                    listenerAreaEffectName.set(listenerArea.toString())
                }
                var found = snd_system.soundSystemLocal.EFXDatabase.FindEffect(listenerAreaEffectName, effectHandle)
                if (!found) {
                    found = snd_system.soundSystemLocal.EFXDatabase.FindEffect(listenerAreaName, effectHandle)
                }
                if (!found) {
                    found = snd_system.soundSystemLocal.EFXDatabase.FindEffect(defaultEffectName, effectHandle)
                }

                // only update if change in settings
                if (found && listenerEffect != effectHandle[0]) {
                    listenerEffect = effectHandle[0]
                    EXTEfx.alAuxiliaryEffectSloti(listenerSlot, EXTEfx.AL_EFFECTSLOT_EFFECT, effectHandle[0])
                }
            }

            // debugging option to mute all but a single soundEmitter
            if (idSoundSystemLocal.s_singleEmitter.GetInteger() > 0 && idSoundSystemLocal.s_singleEmitter.GetInteger() < emitters.Num()) {
                sound = emitters[idSoundSystemLocal.s_singleEmitter.GetInteger()]
                if (sound != null && sound.playing) { // run through all the channels
                    j = 0
                    while (j < snd_local.SOUND_MAX_CHANNELS) {
                        val chan = sound.channels[j]

                        // see if we have a sound triggered on this channel
                        if (!chan.triggerState) {
                            chan.ALStop()
                            j++
                            continue
                        }
                        AddChannelContribution(sound, chan, current44kHz, numSpeakers, finalMixBuffer)
                        j++
                    }
                }
                return
            }
            i = 1
            while (i < emitters.Num()) {
                sound = emitters[i]
                if (null == sound) {
                    i++
                    continue
                } // if no channels are active, do nothing
                if (!sound.playing) {
                    i++
                    continue
                } // run through all the channels
                j = 0
                while (j < snd_local.SOUND_MAX_CHANNELS) {
                    val chan = sound.channels[j]

                    // see if we have a sound triggered on this channel
                    if (!chan.triggerState) {
                        chan.ALStop()
                        j++
                        continue
                    }
                    AddChannelContribution(sound, chan, current44kHz, numSpeakers, finalMixBuffer)
                    j++
                }
                i++
            } // TODO port to OpenAL
            if (false && enviroSuitActive) {
                snd_system.soundSystemLocal.DoEnviroSuit(finalMixBuffer, MIXBUFFER_SAMPLES, numSpeakers)
            }
        }

        /*
         ===================
         idSoundWorldLocal::AVIUpdate

         this is called by the main thread
         writes one block of sound samples if enough time has passed
         This can be used to write wave files even if no sound hardware exists
         ===================
         */
        fun AVIUpdate() {
            val numSpeakers: Int
            if (game44kHz - lastAVI44kHz < MIXBUFFER_SAMPLES) {
                return
            }
            numSpeakers = idSoundSystemLocal.s_numberOfSpeakers.GetInteger()

            val mix_p = FloatArray(MIXBUFFER_SAMPLES * 6 + 16)

            MixLoop(lastAVI44kHz, numSpeakers, mix_p)
            for (i in 0 until numSpeakers) {
                val outD = ByteBuffer.allocate(MIXBUFFER_SAMPLES * 2)
                for (j in 0 until MIXBUFFER_SAMPLES) {
                    val s = mix_p[j * numSpeakers + i]
                    if (s < -32768.0f) {
                        outD.putShort(Short.MIN_VALUE)
                    } else if (s > 32767.0f) {
                        outD.putShort(Short.MAX_VALUE)
                    } else {
                        outD.putShort(idMath.FtoiFast(s).toShort())
                    }
                } // write to file
                fpa[i]!!.Write(outD) //, MIXBUFFER_SAMPLES * sizeof(short));
            }
            lastAVI44kHz += MIXBUFFER_SAMPLES
            return
        }

        fun ResolveOrigin(
            stackDepth: Int,
            prevStack: soundPortalTrace_s?,
            soundArea: Int,
            dist: Float,
            soundOrigin: idVec3,
            def: idSoundEmitterLocal
        ) {
            if (dist >= def.distance) { // we can't possibly hear the sound through this chain of portals
                return
            }
            if (soundArea == listenerArea) {
                val fullDist = dist + (soundOrigin - listenerQU).LengthFast()
                if (fullDist < def.distance) {
                    def.distance = fullDist
                    def.spatializedOrigin.set(soundOrigin)
                }
                return
            }
            if (stackDepth == MAX_PORTAL_TRACE_DEPTH) { // don't spend too much time doing these calculations in big maps
                return
            }
            val newStack = soundPortalTrace_s()
            newStack.portalArea = soundArea
            newStack.prevStack = prevStack
            val numPortals = rw!!.NumPortalsInArea(soundArea)
            for (p in 0 until numPortals) {
                val re = rw!!.GetPortal(soundArea, p)
                var occlusionDistance = 0.0f

                // air blocking windows will block sound like closed doors
                if (0 != re.blockingBits and ((portalConnection_t.PS_BLOCK_VIEW).ordinal or portalConnection_t.PS_BLOCK_AIR.ordinal)) { // we could just completely cut sound off, but reducing the volume works better
                    // continue;
                    occlusionDistance = idSoundSystemLocal.s_doorDistanceAdd.GetFloat()
                }

                // what area are we about to go look at
                var otherArea = re.areas[0]
                if (re.areas[0] == soundArea) {
                    otherArea = re.areas[1]
                }

                // if this area is already in our portal chain, don't bother looking into it
                var prev: soundPortalTrace_s?
                prev = prevStack
                while (prev != null) {
                    if (prev.portalArea == otherArea) {
                        break
                    }
                    prev = prev.prevStack
                }
                if (prev != null) {
                    continue
                }

                // pick a point on the portal to serve as our virtual sound origin
                // #if 1
                val source = idVec3()
                val pl = idPlane()
                re.w!!.GetPlane(pl)
                val scale = CFloat()
                val dir = idVec3(listenerQU.minus(soundOrigin))
                if (!pl.RayIntersection(soundOrigin, dir, scale)) {
                    source.set(re.w!!.GetCenter())
                } else {
                    source.set(soundOrigin + (dir * scale._val))

                    // if this point isn't inside the portal edges, slide it in
                    for (i in 0 until re.w!!.GetNumPoints()) {
                        val j = (i + 1) % re.w!!.GetNumPoints()
                        val edgeDir = idVec3(re.w!![j].ToVec3().minus(re.w!![i].ToVec3()))
                        val edgeNormal = idVec3()
                        edgeNormal.Cross(pl.Normal(), edgeDir)
                        val fromVert = idVec3(source.minus(re.w!![j].ToVec3()))
                        var d = edgeNormal.times(fromVert)
                        if (d > 0) { // move it in
                            val div = edgeNormal.Normalize()
                            d /= div
                            source.minusAssign(edgeNormal.times(d))
                        }
                    }
                }
                val tlen = idVec3(source - soundOrigin)
                val tlenLength = tlen.LengthFast()
                ResolveOrigin(stackDepth + 1, newStack, otherArea, dist + tlenLength + occlusionDistance, source, def)
            }
        }

        fun FindAmplitude(
            sound: idSoundEmitterLocal, localTime: Int, listenerPosition: idVec3?,    /*s_channelType*/
            channel: Int, shakesOnly: Boolean
        ): Float {
            var i: Int
            var j: Int
            var parms: snd_shader.soundShaderParms_t
            var volume: Float
            var activeChannelCount: Int
            val sourceBuffer = FloatArray(AMPLITUDE_SAMPLES)
            val sumBuffer = FloatArray(AMPLITUDE_SAMPLES) // work out the distance from the listener to the emitter
            var dlen: Float
            if (!sound.playing) {
                return 0.0f
            }
            if (listenerPosition != null) { // this doesn't do the portal spatialization
                val dist = idVec3(sound.origin - listenerPosition)
                dlen = dist.Length()
                dlen *= snd_shader.DOOM_TO_METERS
            } else {
                dlen = 1.0f
            }
            activeChannelCount = 0
            i = 0
            while (i < snd_local.SOUND_MAX_CHANNELS) {
                val chan = sound.channels[i]
                if (!chan.triggerState) {
                    i++
                    continue
                }
                if (channel != SCHANNEL_ANY && chan.triggerChannel != channel) {
                    i++
                    continue
                }
                parms = chan.parms!!
                val localTriggerTimes = chan.trigger44kHzTime
                val looping = parms.soundShaderFlags and snd_shader.SSF_LOOPING != 0

                // check for screen shakes
                val shakes = parms.shakes
                if (shakesOnly && shakes <= 0.0f) {
                    i++
                    continue
                }

                //
                // calculate volume
                //
                if (null == listenerPosition) { // just look at the raw wav data for light shader evaluation
                    volume = 1.0f
                } else {
                    volume = parms.volume
                    volume = snd_system.soundSystemLocal.dB2Scale(volume)
                    if (shakesOnly) {
                        volume *= shakes
                    }
                    if (listenerPosition != null && 0 == parms.soundShaderFlags and snd_shader.SSF_GLOBAL) { // check for overrides
                        val maxd = parms.maxDistance
                        val mind = parms.minDistance
                        if (dlen >= maxd) {
                            volume = 0.0f
                        } else if (dlen > mind) {
                            var frac = idMath.ClampFloat(0.0f, 1.0f, 1.0f - (dlen - mind) / (maxd - mind))
                            if (idSoundSystemLocal.s_quadraticFalloff.GetBool()) {
                                frac *= frac
                            }
                            volume *= frac
                        }
                    }
                }
                if (volume <= 0) {
                    i++
                    continue
                }

                //
                // fetch the sound from the cache
                // this doesn't handle stereo samples correctly...
                //
                if (null == listenerPosition && (chan.parms!!.soundShaderFlags and snd_shader.SSF_NO_FLICKER) != 0) { // the NO_FLICKER option is to allow a light to still play a sound, but
                    // not have it effect the intensity
                    j = 0
                    while (j < AMPLITUDE_SAMPLES) {
                        sourceBuffer[j] = if (j and 1 == 1) 32767.0f else -32767.0f
                        j++
                    }
                } else {
                    val sample = if (looping) chan.soundShader!!.entries[0] else chan.leadinSample
                    if (sample == null) { // DG: this happens if sound is disabled (s_noSound 1)
                        i++
                        continue
                    }

                    var offset = localTime - localTriggerTimes // offset in samples
                    val size = sample.LengthIn44kHzSamples()
                    val plitudeData = sample.amplitudeData
                    if (plitudeData != null) {
                        val amplitudeData =
                            plitudeData.asShortBuffer() // when the amplitudeData is present use that fill a dummy sourceBuffer
                        // this is to allow for amplitude based effect on hardware audio solutions
                        if (looping) {
                            offset %= size
                        }
                        if (offset >= 0 && offset < size) {
                            j = 0
                            while (j < AMPLITUDE_SAMPLES) {
                                sourceBuffer[j] =
                                    if (j and 1 == 1) amplitudeData[(offset / 512) * 2].toFloat() else amplitudeData[(offset / 512) * 2 + 1].toFloat()
                                j++
                            }
                        }
                    } else { // get actual sample data
                        chan.GatherChannelSamples(offset, AMPLITUDE_SAMPLES, FloatBuffer.wrap(sourceBuffer))
                    }
                }
                activeChannelCount++
                if (activeChannelCount == 1) { // store to the buffer
                    j = 0
                    while (j < AMPLITUDE_SAMPLES) {
                        sumBuffer[j] = volume * sourceBuffer[j]
                        j++
                    }
                } else { // add to the buffer
                    j = 0
                    while (j < AMPLITUDE_SAMPLES) {
                        sumBuffer[j] += volume * sourceBuffer[j]
                        j++
                    }
                }
                i++
            }
            if (activeChannelCount == 0) {
                return 0.0f
            }
            var high = -32767.0f
            var low = 32767.0f

            // use a 20th of a second
            i = 0
            while (i < AMPLITUDE_SAMPLES) {
                val fabval = sumBuffer[i]
                if (high < fabval) {
                    high = fabval
                }
                if (low > fabval) {
                    low = fabval
                }
                i++
            }
            val sout: Float
            sout = (atan(((high - low) / 32767.0f)) / DEG2RAD(45.0f))
            return sout
        }

        companion object {
            /*
         ===================
         idSoundWorldLocal::ResolveOrigin

         Find out of the sound is completely occluded by a closed door portal, or
         the virtual sound origin position at the portal closest to the listener.
         this is called by the main thread

         dist is the distance from the orignial sound origin to the current portal that enters soundArea
         def->distance is the distance we are trying to reduce.

         If there is no path through open portals from the sound to the listener, def->distance will remain
         set at maxDistance
         ===================
         */
            const val MAX_PORTAL_TRACE_DEPTH = 10

            /*
         ===============
         idSoundWorldLocal::FindAmplitude

         this is called from the main thread

         if listenerPosition is NULL, this is being used for shader parameters,
         like flashing lights and glows based on sound level.  Otherwise, it is being used for
         the screen-shake on a player.

         This doesn't do the portal-occlusion currently, because it would have to reset all the defs
         which would be problematic in multiplayer
         ===============
         */
            private const val AMPLITUDE_SAMPLES = MIXBUFFER_SAMPLES / 8

            // background music
            /*
         ===============
         idSoundWorldLocal::PlayShaderDirectly

         start a music track

         this is called from the main thread
         ===============
         */
            private val rnd: idRandom = idRandom()

            /*
         ===============
         idSoundWorldLocal::CalcEars

         Determine the volumes from each speaker for a given sound emitter
         ===============
         */
            private val speakerVector: Array<idVec3> = arrayOf(
                idVec3(0.707f, 0.707f, 0.0f),  // front left
                idVec3(0.707f, -0.707f, 0.0f),  // front right
                idVec3(0.707f, 0.0f, 0.0f),  // front center
                idVec3(0.0f, 0.0f, 0.0f),  // sub
                idVec3(-0.707f, 0.707f, 0.0f),  // rear left
                idVec3(-0.707f, -0.707f, 0.0f) // rear right
            )

            /*
         ===============
         idSoundWorldLocal::AddChannelContribution

         Adds the contribution of a single sound channel to finalMixBuffer
         this is called from the async thread

         Mixes MIXBUFFER_SAMPLES samples starting at current44kHz sample time into
         finalMixBuffer
         ===============
         */
        }

        init {
            listenerAxis = idMat3()
            listenerPos = idVec3()
            listenerQU = idVec3()
            listenerAreaName = idStr()
            emitters = idList()
            aviDemoPath = idStr()
            aviDemoName = idStr()
        }
    }
}

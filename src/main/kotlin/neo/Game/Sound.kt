package neo.Game

import neo.Game.GameSys.Class.*
import neo.Game.GameSys.Event.idEventDef
import neo.Game.GameSys.SaveGame.idRestoreGame
import neo.Game.GameSys.SaveGame.idSaveGame
import neo.Game.Game_local.gameSoundChannel_t
import neo.TempDump
import neo.framework.Common
import neo.framework.DeclManager
import neo.idlib.BIT
import neo.idlib.Dict_h.idDict
import neo.idlib.containers.CInt
import neo.idlib.math.Matrix.idMat3
import neo.idlib.math.ang_zero
import neo.idlib.math.getVec3_zero
import neo.idlib.math.idAngles
import neo.idlib.math.idVec3

val EV_Speaker_Off: idEventDef = idEventDef("Off", null)
val EV_Speaker_On: idEventDef = idEventDef("On", null)
val EV_Speaker_Timer: idEventDef = idEventDef("<timer>", null)

object Sound {
    val SSF_ANTI_PRIVATE_SOUND: Int = BIT(1) // plays for everyone but the current listenerId
    val SSF_GLOBAL: Int = BIT(3) // play full volume to all speakers and all listeners
    val SSF_LOOPING: Int = BIT(5) // repeat the sound continuously
    val SSF_NO_DUPS: Int = BIT(9) // try not to play the same sound twice in a row
    val SSF_NO_FLICKER: Int = BIT(8) // always return 1.0f for volume queries
    val SSF_NO_OCCLUSION: Int = BIT(2) // don't flow through portals, only use straight line
    val SSF_OMNIDIRECTIONAL: Int = BIT(4) // fall off with distance, but play same volume in all speakers
    val SSF_PLAY_ONCE: Int = BIT(6) // never restart if already playing on any channel of a given emitter

    // sound shader flags
    val SSF_PRIVATE_SOUND: Int = BIT(0) // only plays for the current listenerId
    val SSF_UNCLAMPED: Int = BIT(7) // don't clamp calculated volumes at 1.0f

    /*
     ===============================================================================

     SOUND SHADER DECL

     ===============================================================================
     */
    // unfortunately, our minDistance / maxDistance is specified in meters, and
    // we have far too many of them to change at this time.
    const val DOOM_TO_METERS = 0.0254f // doom to meters
    const val METERS_TO_DOOM = 1.0f / DOOM_TO_METERS // meters to doom

    // sound classes are used to fade most sounds down inside cinematics, leaving dialog
    // flagged with a non-zero class full volume
    const val SOUND_MAX_CLASSES = 4
    const val SOUND_MAX_LIST_WAVS = 32

    // these options can be overriden from sound shader defaults on a per-emitter and per-channel basis
    internal class soundShaderParms_t {
        var maxDistance = 0f
        var minDistance = 0f
        var shakes = 0f
        var soundClass // for global fading of sounds
                = 0
        var soundShaderFlags // SSF_* bit flags
                = 0
        var volume // in dB, unfortunately.  Negative values get quieter
                = 0f
    }

    /*
     ===============================================================================

     Generic sound emitter.

     ===============================================================================
     */
    class idSound : idEntity() {
        companion object {
            private val eventCallbacks: MutableMap<idEventDef, eventCallback_t<*>> = HashMap()
            fun getEventCallBacks(): MutableMap<idEventDef, eventCallback_t<*>> {
                return eventCallbacks
            }

            init {
                eventCallbacks.putAll(idEntity.getEventCallBacks())
                eventCallbacks[EV_Activate] =
                    eventCallback_t1<idSound> { obj: idSound, activator: idEventArg<*>? -> obj.Event_Trigger(activator as idEventArg<idEntity>) }
                eventCallbacks[EV_Speaker_On] =
                    eventCallback_t0<idSound> { obj: idSound -> obj.Event_On() }
                eventCallbacks[EV_Speaker_Off] =
                    eventCallback_t0<idSound> { obj: idSound -> obj.Event_Off() }
                eventCallbacks[EV_Speaker_Timer] =
                    eventCallback_t0<idSound> { obj: idSound -> obj.Event_Timer() }
            }
        }

        private var lastSoundVol = 0.0f
        private var playingUntilTime: Int
        private var random: Float
        private val shakeRotate: idAngles = idAngles()
        private val shakeTranslate: idVec3 = idVec3()
        private var soundVol = 0.0f
        private var timerOn: Boolean
        private var wait: Float
        override fun Save(savefile: idSaveGame) {
            savefile.WriteFloat(lastSoundVol)
            savefile.WriteFloat(soundVol)
            savefile.WriteFloat(random)
            savefile.WriteFloat(wait)
            savefile.WriteBool(timerOn)
            savefile.WriteVec3(shakeTranslate)
            savefile.WriteAngles(shakeRotate)
            savefile.WriteInt(playingUntilTime)
        }

        override fun Restore(savefile: idRestoreGame) {
            lastSoundVol = savefile.ReadFloat()
            soundVol = savefile.ReadFloat()
            random = savefile.ReadFloat()
            wait = savefile.ReadFloat()
            timerOn = savefile.ReadBool()
            savefile.ReadVec3(shakeTranslate)
            savefile.ReadAngles(shakeRotate)
            playingUntilTime = savefile.ReadInt()
        }

        override fun UpdateChangeableSpawnArgs(source: idDict?) {
            super.UpdateChangeableSpawnArgs(source)
            if (source != null) {
                FreeSoundEmitter(true)
                spawnArgs.Copy(source)
                val saveRef = refSound.referenceSound
                GameEdit.gameEdit.ParseSpawnArgsToRefSound(spawnArgs, refSound)
                refSound.referenceSound = saveRef
                val origin = idVec3()
                val axis = idMat3()
                if (GetPhysicsToSoundTransform(origin, axis)) {
                    refSound.origin.set(GetPhysics().GetOrigin() + origin * axis)
                } else {
                    refSound.origin.set(GetPhysics().GetOrigin())
                }
                random = spawnArgs.GetFloat("random", "0")
                wait = spawnArgs.GetFloat("wait", "0")
                if (wait > 0.0f && random >= wait) {
                    random = wait - 0.001f
                    Game_local.gameLocal.Warning(
                        "speaker '%s' at (%s) has random >= wait",
                        name,
                        GetPhysics().GetOrigin().ToString(0)
                    )
                }
                if (!refSound.waitfortrigger && wait > 0.0f) {
                    timerOn = true
                    DoSound(false)
                    CancelEvents(EV_Speaker_Timer)
                    PostEventSec(EV_Speaker_Timer, wait + Game_local.gameLocal.random.CRandomFloat() * random)
                } else if (!refSound.waitfortrigger && !(refSound.referenceSound != null && refSound.referenceSound!!.CurrentlyPlaying())) {
                    // start it if it isn't already playing, and we aren't waitForTrigger
                    DoSound(true)
                    timerOn = false
                }
            }
        }

        override fun Spawn() {
            super.Spawn()
            spawnArgs.GetVector("move", "0 0 0", shakeTranslate)
            spawnArgs.GetAngles("rotate", "0 0 0", shakeRotate)
            random = spawnArgs.GetFloat("random", "0")
            wait = spawnArgs.GetFloat("wait", "0")
            if (wait > 0.0f && random >= wait) {
                random = wait - 0.001f
                Game_local.gameLocal.Warning(
                    "speaker '%s' at (%s) has random >= wait",
                    name,
                    GetPhysics().GetOrigin().ToString(0)
                )
            }
            soundVol = 0.0f
            lastSoundVol = 0.0f
            if (shakeRotate != ang_zero || shakeTranslate != getVec3_zero()) {
                BecomeActive(TH_THINK)
            }
            if (!refSound.waitfortrigger && wait > 0.0f) {
                timerOn = true
                PostEventSec(EV_Speaker_Timer, wait + Game_local.gameLocal.random.CRandomFloat() * random)
            } else {
                timerOn = false
            }
        }

        //        public void ToggleOnOff(idEntity other, idEntity activator);
        override fun Think() {
//	idAngles	ang;

            // run physics
            RunPhysics()

            // clear out our update visuals think flag since we never call Present
            BecomeInactive(TH_UPDATEVISUALS)
        }


        fun SetSound(sound: String, channel: Int = gameSoundChannel_t.SND_CHANNEL_ANY.ordinal /*= SND_CHANNEL_ANY*/) {
            val shader = DeclManager.declManager.FindSound(sound)
            if (shader != refSound.shader) {
                FreeSoundEmitter(true)
            }
            GameEdit.gameEdit.ParseSpawnArgsToRefSound(spawnArgs, refSound)
            refSound.shader = shader
            // start it if it isn't already playing, and we aren't waitForTrigger
            if (!refSound.waitfortrigger && !(refSound.referenceSound != null && refSound.referenceSound!!.CurrentlyPlaying())) {
                DoSound(true)
            }
        }

        override fun ShowEditingDialog() {
            Common.common.InitTool(Common.EDITOR_SOUND, spawnArgs)
        }

        /*
         ================
         obj.Event_Trigger

         this will toggle the idle idSound on and off
         ================
         */
        private fun Event_Trigger(activator: idEventArg<idEntity>) {
            if (wait > 0.0f) {
                if (timerOn) {
                    timerOn = false
                    CancelEvents(EV_Speaker_Timer)
                } else {
                    timerOn = true
                    DoSound(true)
                    PostEventSec(EV_Speaker_Timer, wait + Game_local.gameLocal.random.CRandomFloat() * random)
                }
            } else {
                if (Game_local.gameLocal.isMultiplayer) {
                    DoSound(refSound.referenceSound == null || Game_local.gameLocal.time >= playingUntilTime)
                } else {
                    DoSound(refSound.referenceSound == null || !refSound.referenceSound!!.CurrentlyPlaying())
                }
            }
        }

        private fun Event_Timer() {
            DoSound(true)
            PostEventSec(EV_Speaker_Timer, wait + Game_local.gameLocal.random.CRandomFloat() * random)
        }

        private fun Event_On() {
            if (wait > 0.0f) {
                timerOn = true
                PostEventSec(EV_Speaker_Timer, wait + Game_local.gameLocal.random.CRandomFloat() * random)
            }
            DoSound(true)
        }

        private fun Event_Off() {
            if (timerOn) {
                timerOn = false
                CancelEvents(EV_Speaker_Timer)
            }
            DoSound(false)
        }

        private fun DoSound(play: Boolean) {
            if (play) {
                val playingUntilTime = CInt()
                StartSoundShader(
                    refSound.shader,
                    TempDump.etoi(gameSoundChannel_t.SND_CHANNEL_ANY),
                    refSound.parms.soundShaderFlags,
                    true,
                    playingUntilTime
                )
                this.playingUntilTime = playingUntilTime._val + Game_local.gameLocal.time
            } else {
                StopSound(TempDump.etoi(gameSoundChannel_t.SND_CHANNEL_ANY), true)
                playingUntilTime = 0
            }
        }

        override fun CreateInstance(): idClass {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        override fun getEventCallBack(event: idEventDef): eventCallback_t<*>? {
            return eventCallbacks[event]
        }

        //	CLASS_PROTOTYPE( idSound );
        init {
            random = 0.0f
            wait = 0.0f
            timerOn = false
            playingUntilTime = 0
        }
    }
}
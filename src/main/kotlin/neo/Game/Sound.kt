/*
===========================================================================

Doom 3 GPL Source Code
Copyright (C) 1999-2011 id Software LLC, a ZeniMax Media company.
Translated to Kotlin by Dr. Feederino with support of Claude Code

This file is part of the Doom 3 GPL Source Code ("Doom 3 Source Code").
Original source: neo/game/Sound.h, neo/game/Sound.cpp

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

===========================================================================
*/

package neo.Game

import neo.Game.GameSys.Class.*
import neo.Game.GameSys.Event.idEventDef
import neo.Game.GameSys.SaveGame.idRestoreGame
import neo.Game.GameSys.SaveGame.idSaveGame
import neo.Game.Game_local.gameSoundChannel_t
import neo.framework.Common
import neo.framework.DeclManager
import neo.idlib.BIT
import neo.idlib.Dict_h.idDict
import neo.idlib.containers.CInt
import neo.idlib.math.Matrix.idMat3
import neo.idlib.math.ang_zero
import neo.idlib.math.idAngles
import neo.idlib.math.idVec3
import neo.idlib.math.vec3_zero

/*
===============================================================================

  SOUND

===============================================================================
*/

val EV_Speaker_On: idEventDef = idEventDef("On", null)
val EV_Speaker_Off: idEventDef = idEventDef("Off", null)
val EV_Speaker_Timer: idEventDef = idEventDef("<timer>", null)

object Sound { // NOTE: SSF constants and soundShaderParms_t originate from neo/sound/sound.h in C++.
    // They are placed here in Kotlin for organizational convenience.

    // sound shader flags
    val SSF_PRIVATE_SOUND: Int = BIT(0)      // only plays for the current listenerId
    val SSF_ANTI_PRIVATE_SOUND: Int = BIT(1) // plays for everyone but the current listenerId
    val SSF_NO_OCCLUSION: Int = BIT(2)       // don't flow through portals, only use straight line
    val SSF_GLOBAL: Int = BIT(3)             // play full volume to all speakers and all listeners
    val SSF_OMNIDIRECTIONAL: Int = BIT(4)    // fall off with distance, but play same volume in all speakers
    val SSF_LOOPING: Int = BIT(5)            // repeat the sound continuously
    val SSF_PLAY_ONCE: Int = BIT(6)          // never restart if already playing on any channel of a given emitter
    val SSF_UNCLAMPED: Int = BIT(7)          // don't clamp calculated volumes at 1.0f
    val SSF_NO_FLICKER: Int = BIT(8)         // always return 1.0f for volume queries
    val SSF_NO_DUPS: Int = BIT(9)            // try not to play the same sound twice in a row

    /*
    ===============================================================================

      SOUND SHADER DECL

    ===============================================================================
    */

    // unfortunately, our minDistance / maxDistance is specified in meters, and
    // we have far too many of them to change at this time.
    const val DOOM_TO_METERS = 0.0254f             // doom to meters
    const val METERS_TO_DOOM = 1.0f / DOOM_TO_METERS // meters to doom

    // sound classes are used to fade most sounds down inside cinematics, leaving dialog
    // flagged with a non-zero class full volume
    const val SOUND_MAX_CLASSES = 4
    const val SOUND_MAX_LIST_WAVS = 32

    // these options can be overriden from sound shader defaults on a per-emitter and per-channel basis
    internal class soundShaderParms_t {
        var volume = 0f          // in dB, unfortunately.  Negative values get quieter
        var minDistance = 0f
        var maxDistance = 0f
        var shakes = 0f
        var soundShaderFlags = 0 // SSF_* bit flags
        var soundClass = 0       // for global fading of sounds
    }

    /*
    ===============================================================================

      Generic sound emitter.

    ===============================================================================
    */
    class idSound : idEntity() {
        companion object {
            val Type = idTypeInfo("idSound", "idEntity") { idSound() }

            // CLASS_DECLARATION( idEntity, idSound )
            private val eventCallbacks: MutableMap<idEventDef, eventCallback_t<*>> = HashMap()
            fun getEventCallBacks(): MutableMap<idEventDef, eventCallback_t<*>> {
                return eventCallbacks
            }

            init {
                eventCallbacks.putAll(idEntity.getEventCallBacks())
                eventCallbacks[EV_Activate] = eventCallback_t1<idSound> { obj: idSound, activator: idEventArg<*>? ->
                    obj.Event_Trigger(activator as idEventArg<idEntity>)
                }
                eventCallbacks[EV_Speaker_On] = eventCallback_t0<idSound> { obj: idSound -> obj.Event_On() }
                eventCallbacks[EV_Speaker_Off] = eventCallback_t0<idSound> { obj: idSound -> obj.Event_Off() }
                eventCallbacks[EV_Speaker_Timer] = eventCallback_t0<idSound> { obj: idSound -> obj.Event_Timer() }
            }
        }

        // Member variables — order matches C++ header declaration
        private var lastSoundVol: Float = 0.0f
        private var soundVol: Float = 0.0f
        private var random: Float = 0.0f
        private var wait: Float = 0.0f
        private var timerOn: Boolean = false
        private val shakeTranslate: idVec3 = idVec3()
        private val shakeRotate: idAngles = idAngles()
        private var playingUntilTime: Int = 0

        /*
        ================
        idSound::Save
        ================
        */
        override fun Save(savefile: idSaveGame) {
            super.Save(savefile)
            savefile.WriteFloat(lastSoundVol)
            savefile.WriteFloat(soundVol)
            savefile.WriteFloat(random)
            savefile.WriteFloat(wait)
            savefile.WriteBool(timerOn)
            savefile.WriteVec3(shakeTranslate)
            savefile.WriteAngles(shakeRotate)
            savefile.WriteInt(playingUntilTime)
        }

        /*
        ================
        idSound::Restore
        ================
        */
        override fun Restore(savefile: idRestoreGame) {
            super.Restore(savefile)
            lastSoundVol = savefile.ReadFloat()
            soundVol = savefile.ReadFloat()
            random = savefile.ReadFloat()
            wait = savefile.ReadFloat()
            timerOn = savefile.ReadBool()
            savefile.ReadVec3(shakeTranslate)
            savefile.ReadAngles(shakeRotate)
            playingUntilTime = savefile.ReadInt()
        }

        /*
        ================
        idSound::Spawn
        ================
        */
        override fun Spawn() {
            super.Spawn()
            spawnArgs.GetVector("move", "0 0 0", shakeTranslate)
            spawnArgs.GetAngles("rotate", "0 0 0", shakeRotate)
            random = spawnArgs.GetFloat("random", "0")
            wait = spawnArgs.GetFloat("wait", "0")

            if (wait > 0.0f && random >= wait) {
                random = wait - 0.001f
                Game_local.gameLocal.Warning(
                    "speaker '%s' at (%s) has random >= wait", name, GetPhysics().GetOrigin().ToString(0)
                )
            }

            soundVol = 0.0f
            lastSoundVol = 0.0f

            if (shakeRotate != ang_zero || shakeTranslate != vec3_zero) {
                BecomeActive(TH_THINK)
            }

            if (!refSound.waitfortrigger && wait > 0.0f) {
                timerOn = true
                PostEventSec(EV_Speaker_Timer, wait + Game_local.gameLocal.random.CRandomFloat() * random)
            } else {
                timerOn = false
            }
        }

        /*
        ================
        idSound::Event_Trigger

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
            } else { // FIX: Restructured to match C++ explicit if/else toggle pattern.
                // Previously used compressed boolean expression DoSound(condition) which was
                // functionally equivalent but harder to read and maintain.
                if (Game_local.gameLocal.isMultiplayer) {
                    if (refSound.referenceSound != null && Game_local.gameLocal.time < playingUntilTime) {
                        DoSound(false)
                    } else {
                        DoSound(true)
                    }
                } else {
                    if (refSound.referenceSound != null && refSound.referenceSound!!.CurrentlyPlaying()) {
                        DoSound(false)
                    } else {
                        DoSound(true)
                    }
                }
            }
        }

        /*
        ================
        idSound::Event_Timer
        ================
        */
        private fun Event_Timer() {
            DoSound(true)
            PostEventSec(EV_Speaker_Timer, wait + Game_local.gameLocal.random.CRandomFloat() * random)
        }

        /*
        ================
        idSound::Think
        ================
        */
        override fun Think() { // run physics
            RunPhysics()

            // clear out our update visuals think flag since we never call Present
            BecomeInactive(TH_UPDATEVISUALS)
        }

        /*
        ===============
        idSound::UpdateChangeableSpawnArgs
        ===============
        */
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
                        "speaker '%s' at (%s) has random >= wait", name, GetPhysics().GetOrigin().ToString(0)
                    )
                }

                if (!refSound.waitfortrigger && wait > 0.0f) {
                    timerOn = true
                    DoSound(false)
                    CancelEvents(EV_Speaker_Timer)
                    PostEventSec(EV_Speaker_Timer, wait + Game_local.gameLocal.random.CRandomFloat() * random)
                } else if (!refSound.waitfortrigger && !(refSound.referenceSound != null && refSound.referenceSound!!.CurrentlyPlaying())) { // start it if it isn't already playing, and we aren't waitForTrigger
                    DoSound(true)
                    timerOn = false
                }
            }
        }

        /*
        ===============
        idSound::SetSound
        ===============
        */
        fun SetSound(sound: String, channel: Int = gameSoundChannel_t.SND_CHANNEL_ANY.ordinal) {
            val shader = DeclManager.declManager.FindSound(sound)
            if (shader != refSound.shader) {
                FreeSoundEmitter(true)
            }
            GameEdit.gameEdit.ParseSpawnArgsToRefSound(spawnArgs, refSound)
            refSound.shader = shader // start it if it isn't already playing, and we aren't waitForTrigger
            if (!refSound.waitfortrigger && !(refSound.referenceSound != null && refSound.referenceSound!!.CurrentlyPlaying())) {
                DoSound(true)
            }
        }

        /*
        ================
        idSound::DoSound
        ================
        */
        private fun DoSound(play: Boolean) {
            if (play) {
                val playingUntilTime = CInt()
                StartSoundShader(
                    refSound.shader,
                    (gameSoundChannel_t.SND_CHANNEL_ANY).ordinal,
                    refSound.parms.soundShaderFlags,
                    true,
                    playingUntilTime
                )
                this.playingUntilTime = playingUntilTime._val + Game_local.gameLocal.time
            } else {
                StopSound((gameSoundChannel_t.SND_CHANNEL_ANY).ordinal, true)
                playingUntilTime = 0
            }
        }

        /*
        ================
        idSound::Event_On
        ================
        */
        private fun Event_On() {
            if (wait > 0.0f) {
                timerOn = true
                PostEventSec(EV_Speaker_Timer, wait + Game_local.gameLocal.random.CRandomFloat() * random)
            }
            DoSound(true)
        }

        /*
        ================
        idSound::Event_Off
        ================
        */
        private fun Event_Off() {
            if (timerOn) {
                timerOn = false
                CancelEvents(EV_Speaker_Timer)
            }
            DoSound(false)
        }

        /*
        ===============
        idSound::ShowEditingDialog
        ===============
        */
        override fun ShowEditingDialog() {
            Common.common.InitTool(Common.EDITOR_SOUND, spawnArgs)
        }

        override fun GetType(): idTypeInfo = Type
        override fun CreateInstance(): idClass = idSound()

        override fun getEventCallBack(event: idEventDef): eventCallback_t<*>? {
            return eventCallbacks[event]
        }
    }
}

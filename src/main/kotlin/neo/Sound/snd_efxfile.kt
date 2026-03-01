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
 * Original source: neo/sound/snd_efxfile.cpp, neo/sound/efxlib.h
 */

package neo.Sound

import neo.framework.Common
import neo.idlib.Text.Lexer
import neo.idlib.Text.Lexer.idLexer
import neo.idlib.Text.Str.idStr
import neo.idlib.Text.Token.idToken
import neo.idlib.containers.List.idList
import neo.idlib.math.idMath
import org.lwjgl.openal.AL10
import org.lwjgl.openal.EXTEfx

class snd_efxfile {

    companion object {
        /*
         * mB_to_gain: Convert millibels to linear gain, clamped to [min, max]
         * C++ macro: #define mB_to_gain(millibels, property)
         *   _mB_to_gain(millibels, AL_EAXREVERB_MIN_##property, AL_EAXREVERB_MAX_##property)
         */
        fun mBToGain(millibels: Float, min: Float, max: Float): Float {
            return idMath.ClampFloat(min, max, idMath.Pow(10.0f, millibels / 2000.0f))
        }
    }

    /*
     ===============
     idSoundEffect
     ===============
     */
    // FIX: Replaced data/datasize fields with 'effect' (ALuint) to match dhewm3 C++.
    // The old fields were from original id Tech 4 code that used EAXREVERBPROPERTIES structs;
    // dhewm3 uses OpenAL EFX effects directly.
    class idSoundEffect {
        var name: idStr = idStr()
        var effect: Int = 0 // ALuint - OpenAL effect handle

        /*
         ===============
         idSoundEffect::alloc
         ===============
         */
        fun alloc(): Boolean {
            AL10.alGetError()

            effect = EXTEfx.alGenEffects()
            var e = AL10.alGetError()
            if (e != AL10.AL_NO_ERROR) {
                Common.common.Warning("idSoundEffect::alloc: alGenEffects failed: 0x%x", e)
                return false
            }

            EXTEfx.alEffecti(effect, EXTEfx.AL_EFFECT_TYPE, EXTEfx.AL_EFFECT_EAXREVERB)
            e = AL10.alGetError()
            if (e != AL10.AL_NO_ERROR) {
                Common.common.Warning("idSoundEffect::alloc: alEffecti failed: 0x%x", e)
                return false
            }

            return true
        }

        /*
         ===============
         idSoundEffect::~idSoundEffect (destructor equivalent)
         ===============
         */
        fun destroy() {
            if (effect != 0 && EXTEfx.alIsEffect(effect)) {
                EXTEfx.alDeleteEffects(effect)
            }
            effect = 0
        }
    }

    /*
     ===============
     idEFXFile
     ===============
     */
    class idEFXFile {
        val effects: idList<idSoundEffect>

        /*
         ===============
         idEFXFile::FindEffect
         ===============
         */
        // FIX: Signature changed to match C++: returns ALuint effect handle, not object + index
        fun FindEffect(name: idStr, effect: IntArray): Boolean {
            for (i in 0 until effects.Num()) {
                if (effects[i].name == name) {
                    effect[0] = effects[i].effect
                    return true
                }
            }
            return false
        }

        /*
         ===============
         idEFXFile::ReadEffect
         ===============
         */
        // FIX: Was a non-functional stub returning false. Fully implemented from dhewm3 C++ source.
        // Uses LWJGL3 EXTEfx directly instead of C++ function pointers loaded via alGetProcAddress.
        fun ReadEffect(src: idLexer, effect: idSoundEffect): Boolean {
            val name = idToken()
            val token = idToken()

            if (!src.ReadToken(token))
                return false

            // reverb effect
            if (token.toString() != "reverb") {
                // other effect (not supported at the moment)
                src.Error("idEFXFile::ReadEffect: Unknown effect definition")
                return false
            }

            src.ReadTokenOnLine(token)
            name.set(token)

            if (!src.ReadToken(token))
                return false

            if (token.toString() != "{") {
                src.Error("idEFXFile::ReadEffect: { not found, found %s", token)
                return false
            }

            var err: Int
            AL10.alGetError()

            do {
                if (!src.ReadToken(token)) {
                    src.Error("idEFXFile::ReadEffect: EOF without closing brace")
                    return false
                }

                if (token.toString() == "}") {
                    effect.name = idStr(name.toString())
                    break
                }

                val t = token.toString()
                when {
                    t == "environment" -> {
                        // <+KittyCat> the "environment" token should be ignored (efx has nothing equatable to it)
                        src.ParseInt()
                    }

                    t == "environment size" -> {
                        // density = clamp(pow(size, 3) / 16, 0, 1)
                        var size = src.ParseFloat()
                        size = idMath.ClampFloat(0.0f, 1.0f, (size * size * size) / 16.0f)
                        EXTEfx.alEffectf(effect.effect, EXTEfx.AL_EAXREVERB_DENSITY, size)
                        err = AL10.alGetError()
                        if (err != AL10.AL_NO_ERROR)
                            Common.common.Warning("alEffectf(AL_EAXREVERB_DENSITY, %.3f) failed: 0x%x", size, err)
                    }

                    t == "environment diffusion" -> {
                        val v = src.ParseFloat()
                        EXTEfx.alEffectf(effect.effect, EXTEfx.AL_EAXREVERB_DIFFUSION, v)
                        err = AL10.alGetError()
                        if (err != AL10.AL_NO_ERROR)
                            Common.common.Warning("alEffectf(AL_EAXREVERB_DIFFUSION, %.3f) failed: 0x%x", v, err)
                    }

                    t == "room" -> {
                        val v = mBToGain(src.ParseInt().toFloat(), 0.0f, 1.0f) // AL_EAXREVERB_MIN/MAX_GAIN
                        EXTEfx.alEffectf(effect.effect, EXTEfx.AL_EAXREVERB_GAIN, v)
                        err = AL10.alGetError()
                        if (err != AL10.AL_NO_ERROR)
                            Common.common.Warning("alEffectf(AL_EAXREVERB_GAIN, %.3f) failed: 0x%x", v, err)
                    }

                    t == "room hf" -> {
                        val v = mBToGain(src.ParseInt().toFloat(), 0.0f, 1.0f) // AL_EAXREVERB_MIN/MAX_GAINHF
                        EXTEfx.alEffectf(effect.effect, EXTEfx.AL_EAXREVERB_GAINHF, v)
                        err = AL10.alGetError()
                        if (err != AL10.AL_NO_ERROR)
                            Common.common.Warning("alEffectf(AL_EAXREVERB_GAINHF, %.3f) failed: 0x%x", v, err)
                    }

                    t == "room lf" -> {
                        val v = mBToGain(src.ParseInt().toFloat(), 0.0f, 1.0f) // AL_EAXREVERB_MIN/MAX_GAINLF
                        EXTEfx.alEffectf(effect.effect, EXTEfx.AL_EAXREVERB_GAINLF, v)
                        err = AL10.alGetError()
                        if (err != AL10.AL_NO_ERROR)
                            Common.common.Warning("alEffectf(AL_EAXREVERB_GAINLF, %.3f) failed: 0x%x", v, err)
                    }

                    t == "decay time" -> {
                        val v = src.ParseFloat()
                        EXTEfx.alEffectf(effect.effect, EXTEfx.AL_EAXREVERB_DECAY_TIME, v)
                        err = AL10.alGetError()
                        if (err != AL10.AL_NO_ERROR)
                            Common.common.Warning("alEffectf(AL_EAXREVERB_DECAY_TIME, %.3f) failed: 0x%x", v, err)
                    }

                    t == "decay hf ratio" -> {
                        val v = src.ParseFloat()
                        EXTEfx.alEffectf(effect.effect, EXTEfx.AL_EAXREVERB_DECAY_HFRATIO, v)
                        err = AL10.alGetError()
                        if (err != AL10.AL_NO_ERROR)
                            Common.common.Warning("alEffectf(AL_EAXREVERB_DECAY_HFRATIO, %.3f) failed: 0x%x", v, err)
                    }

                    t == "decay lf ratio" -> {
                        val v = src.ParseFloat()
                        EXTEfx.alEffectf(effect.effect, EXTEfx.AL_EAXREVERB_DECAY_LFRATIO, v)
                        err = AL10.alGetError()
                        if (err != AL10.AL_NO_ERROR)
                            Common.common.Warning("alEffectf(AL_EAXREVERB_DECAY_LFRATIO, %.3f) failed: 0x%x", v, err)
                    }

                    t == "reflections" -> {
                        val v = mBToGain(src.ParseInt().toFloat(), 0.0f, 3.16f) // AL_EAXREVERB_MIN/MAX_REFLECTIONS_GAIN
                        EXTEfx.alEffectf(effect.effect, EXTEfx.AL_EAXREVERB_REFLECTIONS_GAIN, v)
                        err = AL10.alGetError()
                        if (err != AL10.AL_NO_ERROR)
                            Common.common.Warning("alEffectf(AL_EAXREVERB_REFLECTIONS_GAIN, %.3f) failed: 0x%x", v, err)
                    }

                    t == "reflections delay" -> {
                        val v = src.ParseFloat()
                        EXTEfx.alEffectf(effect.effect, EXTEfx.AL_EAXREVERB_REFLECTIONS_DELAY, v)
                        err = AL10.alGetError()
                        if (err != AL10.AL_NO_ERROR)
                            Common.common.Warning(
                                "alEffectf(AL_EAXREVERB_REFLECTIONS_DELAY, %.3f) failed: 0x%x",
                                v,
                                err
                            )
                    }

                    t == "reflections pan" -> {
                        val v = floatArrayOf(src.ParseFloat(), src.ParseFloat(), src.ParseFloat())
                        EXTEfx.alEffectfv(effect.effect, EXTEfx.AL_EAXREVERB_REFLECTIONS_PAN, v)
                        err = AL10.alGetError()
                        if (err != AL10.AL_NO_ERROR)
                            Common.common.Warning(
                                "alEffectfv(AL_EAXREVERB_REFLECTIONS_PAN, %.3f, %.3f, %.3f) failed: 0x%x",
                                v[0], v[1], v[2], err
                            )
                    }

                    t == "reverb" -> {
                        val v = mBToGain(src.ParseInt().toFloat(), 0.0f, 10.0f) // AL_EAXREVERB_MIN/MAX_LATE_REVERB_GAIN
                        EXTEfx.alEffectf(effect.effect, EXTEfx.AL_EAXREVERB_LATE_REVERB_GAIN, v)
                        err = AL10.alGetError()
                        if (err != AL10.AL_NO_ERROR)
                            Common.common.Warning("alEffectf(AL_EAXREVERB_LATE_REVERB_GAIN, %.3f) failed: 0x%x", v, err)
                    }

                    t == "reverb delay" -> {
                        val v = src.ParseFloat()
                        EXTEfx.alEffectf(effect.effect, EXTEfx.AL_EAXREVERB_LATE_REVERB_DELAY, v)
                        err = AL10.alGetError()
                        if (err != AL10.AL_NO_ERROR)
                            Common.common.Warning(
                                "alEffectf(AL_EAXREVERB_LATE_REVERB_DELAY, %.3f) failed: 0x%x",
                                v,
                                err
                            )
                    }

                    t == "reverb pan" -> {
                        val v = floatArrayOf(src.ParseFloat(), src.ParseFloat(), src.ParseFloat())
                        EXTEfx.alEffectfv(effect.effect, EXTEfx.AL_EAXREVERB_LATE_REVERB_PAN, v)
                        err = AL10.alGetError()
                        if (err != AL10.AL_NO_ERROR)
                            Common.common.Warning(
                                "alEffectfv(AL_EAXREVERB_LATE_REVERB_PAN, %.3f, %.3f, %.3f) failed: 0x%x",
                                v[0], v[1], v[2], err
                            )
                    }

                    t == "echo time" -> {
                        val v = src.ParseFloat()
                        EXTEfx.alEffectf(effect.effect, EXTEfx.AL_EAXREVERB_ECHO_TIME, v)
                        err = AL10.alGetError()
                        if (err != AL10.AL_NO_ERROR)
                            Common.common.Warning("alEffectf(AL_EAXREVERB_ECHO_TIME, %.3f) failed: 0x%x", v, err)
                    }

                    t == "echo depth" -> {
                        val v = src.ParseFloat()
                        EXTEfx.alEffectf(effect.effect, EXTEfx.AL_EAXREVERB_ECHO_DEPTH, v)
                        err = AL10.alGetError()
                        if (err != AL10.AL_NO_ERROR)
                            Common.common.Warning("alEffectf(AL_EAXREVERB_ECHO_DEPTH, %.3f) failed: 0x%x", v, err)
                    }

                    t == "modulation time" -> {
                        val v = src.ParseFloat()
                        EXTEfx.alEffectf(effect.effect, EXTEfx.AL_EAXREVERB_MODULATION_TIME, v)
                        err = AL10.alGetError()
                        if (err != AL10.AL_NO_ERROR)
                            Common.common.Warning("alEffectf(AL_EAXREVERB_MODULATION_TIME, %.3f) failed: 0x%x", v, err)
                    }

                    t == "modulation depth" -> {
                        val v = src.ParseFloat()
                        EXTEfx.alEffectf(effect.effect, EXTEfx.AL_EAXREVERB_MODULATION_DEPTH, v)
                        err = AL10.alGetError()
                        if (err != AL10.AL_NO_ERROR)
                            Common.common.Warning("alEffectf(AL_EAXREVERB_MODULATION_DEPTH, %.3f) failed: 0x%x", v, err)
                    }

                    t == "air absorption hf" -> {
                        val v = mBToGain(src.ParseFloat(), 0.892f, 1.0f) // AL_EAXREVERB_MIN/MAX_AIR_ABSORPTION_GAINHF
                        EXTEfx.alEffectf(effect.effect, EXTEfx.AL_EAXREVERB_AIR_ABSORPTION_GAINHF, v)
                        err = AL10.alGetError()
                        if (err != AL10.AL_NO_ERROR)
                            Common.common.Warning(
                                "alEffectf(AL_EAXREVERB_AIR_ABSORPTION_GAINHF, %.3f) failed: 0x%x",
                                v,
                                err
                            )
                    }

                    t == "hf reference" -> {
                        val v = src.ParseFloat()
                        EXTEfx.alEffectf(effect.effect, EXTEfx.AL_EAXREVERB_HFREFERENCE, v)
                        err = AL10.alGetError()
                        if (err != AL10.AL_NO_ERROR)
                            Common.common.Warning("alEffectf(AL_EAXREVERB_HFREFERENCE, %.3f) failed: 0x%x", v, err)
                    }

                    t == "lf reference" -> {
                        val v = src.ParseFloat()
                        EXTEfx.alEffectf(effect.effect, EXTEfx.AL_EAXREVERB_LFREFERENCE, v)
                        err = AL10.alGetError()
                        if (err != AL10.AL_NO_ERROR)
                            Common.common.Warning("alEffectf(AL_EAXREVERB_LFREFERENCE, %.3f) failed: 0x%x", v, err)
                    }

                    t == "room rolloff factor" -> {
                        val v = src.ParseFloat()
                        EXTEfx.alEffectf(effect.effect, EXTEfx.AL_EAXREVERB_ROOM_ROLLOFF_FACTOR, v)
                        err = AL10.alGetError()
                        if (err != AL10.AL_NO_ERROR)
                            Common.common.Warning(
                                "alEffectf(AL_EAXREVERB_ROOM_ROLLOFF_FACTOR, %.3f) failed: 0x%x",
                                v,
                                err
                            )
                    }

                    t == "flags" -> {
                        src.ReadTokenOnLine(token)
                        val flags = token.GetUnsignedLongValue().toInt()
                        val decayHfLimit = if ((flags and 0x20) != 0) AL10.AL_TRUE else AL10.AL_FALSE
                        EXTEfx.alEffecti(effect.effect, EXTEfx.AL_EAXREVERB_DECAY_HFLIMIT, decayHfLimit)
                        err = AL10.alGetError()
                        if (err != AL10.AL_NO_ERROR)
                            Common.common.Warning(
                                "alEffecti(AL_EAXREVERB_DECAY_HFLIMIT, %d) failed: 0x%x",
                                decayHfLimit,
                                err
                            )
                        // the other SCALE flags have no equivalent in efx
                    }

                    else -> {
                        src.ReadTokenOnLine(token)
                        src.Error("idEFXFile::ReadEffect: Invalid parameter in reverb definition")
                    }
                }
            } while (true)

            return true
        }

        /*
         ===============
         idEFXFile::LoadFile
         ===============
         */
        // FIX: Main effects loading loop was commented out. Fully implemented from C++ source.
        fun LoadFile(filename: String, OSPath: Boolean = false): Boolean {
            val src = idLexer(Lexer.LEXFL_NOSTRINGCONCAT)
            src.LoadFile(filename, OSPath)
            if (!src.IsLoaded()) {
                return false
            }
            if (!src.ExpectTokenString("Version")) {
                return false
            }
            if (src.ParseInt() != 1) {
                src.Error("idEFXFile::LoadFile: Unknown file version")
                return false
            }

            while (!src.EndOfFile()) {
                val effect = idSoundEffect()

                if (!effect.alloc()) {
                    effect.destroy()
                    Clear()
                    return false
                }

                if (ReadEffect(src, effect)) {
                    effects.Append(effect)
                } else {
                    effect.destroy()
                }
            }

            return true
        }

        /*
         ===============
         idEFXFile::UnloadFile
         ===============
         */
        fun UnloadFile() {
            Clear()
        }

        /*
         ===============
         idEFXFile::Clear
         ===============
         */
        fun Clear() {
            // NOTE: Differs from C++ — C++ uses effects.DeleteContents(true) which calls delete
            // on each pointer, triggering the destructor (~idSoundEffect) that calls alDeleteEffects.
            // Kotlin has no destructors, so we must explicitly destroy each effect.
            for (i in 0 until effects.Num()) {
                effects[i].destroy()
            }
            effects.Clear()
        }

        init {
            effects = idList()
        }
    }
}

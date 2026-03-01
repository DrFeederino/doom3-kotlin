package neo.Sound

import neo.Sound.snd_cache.idSoundSample
import neo.Sound.snd_system.idSoundSystemLocal
import neo.TempDump
import neo.framework.CVarSystem
import neo.framework.Common
import neo.framework.DeclManager
import neo.framework.DeclManager.idDecl
import neo.framework.FileSystem_h
import neo.idlib.BIT
import neo.idlib.Text.Lexer.idLexer
import neo.idlib.Text.Str
import neo.idlib.Text.Str.idStr
import neo.idlib.Text.Token
import neo.idlib.Text.Token.idToken
import neo.idlib.math.idMath
import neo.idlib.math.speakerLabel

object snd_shader {
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

    //
    //
    const val SOUND_MAX_LIST_WAVS = 32
    val SSF_ANTI_PRIVATE_SOUND: Int = BIT(1) // plays for everyone but the current listenerId
    val SSF_GLOBAL: Int = BIT(3) // play full volume to all speakers and all listeners
    val SSF_LOOPING: Int = BIT(5) // repeat the sound continuously
    val SSF_NO_DUPS: Int = BIT(9) // try not to play the same sound twice in a row
    val SSF_NO_FLICKER: Int = BIT(8) // always return 1.0f for volume queries
    val SSF_NO_OCCLUSION: Int = BIT(2) // don't flow through portals, only use straight line
    val SSF_OMNIDIRECTIONAL: Int = BIT(4) // fall off with distance, but play same volume in all speakers
    val SSF_PLAY_ONCE: Int = BIT(6) // never restart if already playing on any channel of a given emitter

    //
    //
    // sound shader flags
    val SSF_PRIVATE_SOUND: Int = BIT(0) // only plays for the current listenerId
    val SSF_UNCLAMPED: Int = BIT(7) // don't clamp calculated volumes at 1.0f

    // these options can be overriden from sound shader defaults on a per-emitter and per-channel basis
    class soundShaderParms_t {
        var maxDistance = 0.0f
        var minDistance = 0.0f
        var shakes = 0.0f
        var soundClass // for global fading of sounds
                = 0
        var soundShaderFlags // SSF_* bit flags
                = 0
        var volume // in dB, unfortunately.  Negative values get quieter
                = 0.0f

        constructor()

        // FIX: Copy constructor for C++ struct value copy semantics (e.g. this->parms = *parms)
        constructor(other: soundShaderParms_t) {
            maxDistance = other.maxDistance
            minDistance = other.minDistance
            shakes = other.shakes
            soundClass = other.soundClass
            soundShaderFlags = other.soundShaderFlags
            volume = other.volume
        }
    }

    // it is somewhat tempting to make this a virtual class to hide the private
    // details here, but that doesn't fit easily with the decl manager at the moment.
    class idSoundShader : idDecl() {
        var entries: Array<idSoundSample?> = Array(SOUND_MAX_LIST_WAVS) { null }
        var leadinVolume // allows light breaking leadin sounds to be much louder than the broken loop
                = 0.0f

        //
        var leadins: Array<idSoundSample?> = Array(SOUND_MAX_LIST_WAVS) { null }
        var numEntries = 0

        // friend class idSoundWorldLocal;
        // friend class idSoundEmitterLocal;
        // friend class idSoundChannel;
        // friend class idSoundCache;
        //
        //
        // options from sound shader text
        var parms // can be overriden on a per-channel basis
                : soundShaderParms_t = soundShaderParms_t()
        var speakerMask = 0
        private var altSound: idSoundShader? = null
        private val desc // description
                : idStr = idStr()
        private var errorDuringParse = false
        var numLeadins = 0

        //
        private var onDemand // only load when played, and free when finished
                = false

        override fun SetDefaultText(): Boolean {
            val wavName: idStr
            wavName = idStr(GetName())
            wavName.DefaultFileExtension(".wav") // if the name has .ogg in it, that will stay

            // if there exists a wav file with the same name
            return if (true) { //fileSystem->ReadFile( wavname, NULL ) != -1 ) {
                val generated = StringBuffer(2048)
                idStr.snPrintf(
                    generated, generated.capacity(),
                    """
                        sound %s // IMPLICITLY GENERATED
                        {
                        %s
                        }
                        
                        """.trimIndent(), GetName(), wavName
                )
                SetText(generated.toString())
                true
            } else {
                false
            }
        }

        override fun DefaultDefinition(): String {
            return """{
	            _default.wav
            }"""
        }

        /*
         ===============
         idSoundShader::Parse

         this is called by the declManager
         ===============
         */
        override fun Parse(text: String, textLength: Int): Boolean {
            val src = idLexer()
            src.LoadMemory(text, textLength, GetFileName(), GetLineNum())
            src.SetFlags(DeclManager.DECL_LEXER_FLAGS)
            src.SkipUntilString("{")

            // deeper functions can set this, which will cause MakeDefault() to be called at the end
            errorDuringParse = false
            if (!ParseShader(src) || errorDuringParse) {
                MakeDefault()
                return false
            }
            return true
        }

        override fun FreeData() {
            numEntries = 0
            numLeadins = 0
        }

        override fun List() {
            Common.common.Printf("%4d: %s\n", Index(), GetName())
            if (idStr.Icmp(GetDescription(), "<no description>") != 0) {
                Common.common.Printf("      description: %s\n", GetDescription())
            }
            for (k in 0 until numLeadins) {
                val objectp = leadins[k]
                if (objectp != null) {
                    Common.common.Printf(
                        "      %5dms %4dKb %s (LEADIN)\n",
                        snd_system.soundSystemLocal.SamplesToMilliseconds(objectp.LengthIn44kHzSamples()),
                        objectp.objectMemSize / 1024,
                        objectp.name
                    )
                }
            }
            for (k in 0 until numEntries) {
                val objectp = entries[k]
                if (objectp != null) {
                    Common.common.Printf(
                        "      %5dms %4dKb %s\n",
                        snd_system.soundSystemLocal.SamplesToMilliseconds(objectp.LengthIn44kHzSamples()),
                        objectp.objectMemSize / 1024,
                        objectp.name
                    )
                }
            }
        }

        fun GetDescription(): String {
            return desc.toString()
        }

        // so the editor can draw correct default sound spheres
        // this is currently defined as meters, which sucks, IMHO.
        fun GetMinDistance(): Float {        // FIXME: replace this with a GetSoundShaderParms()
            return parms.minDistance
        }

        fun GetMaxDistance(): Float {
            return parms.maxDistance
        }

        // returns NULL if an AltSound isn't defined in the shader.
        // we use this for pairing a specific broken light sound with a normal light sound
        fun GetAltSound(): idSoundShader? {
            return altSound
        }

        fun HasDefaultSound(): Boolean {
            for (i in 0 until numEntries) {
                if (entries[i] != null && entries[i]!!.defaultSound) {
                    return true
                }
            }
            return false
        }

        fun GetParms(): soundShaderParms_t {
            return parms
        }

        fun GetNumSounds(): Int {
            return numLeadins + numEntries
        }

        fun GetSound(index: Int): String {
            var index = index
            if (index >= 0) {
                if (index < numLeadins) {
                    return leadins[index]!!.name.toString()
                }
                index -= numLeadins
                if (index < numEntries) {
                    return entries[index]!!.name.toString()
                }
            }
            return ""
        }

        fun CheckShakesAndOgg(): Boolean {
            var i: Int
            var ret = false
            i = 0
            while (i < numLeadins) {
                if (leadins[i]!!.objectInfo.wFormatTag == snd_local.WAVE_FORMAT_TAG_OGG) {
                    Common.common.Warning(
                        "sound shader '%s' has shakes and uses OGG file '%s'",
                        GetName(), leadins[i]!!.name
                    )
                    ret = true
                }
                i++
            }
            i = 0
            while (i < numEntries) {
                if (entries[i]!!.objectInfo.wFormatTag == snd_local.WAVE_FORMAT_TAG_OGG) {
                    Common.common.Warning(
                        "sound shader '%s' has shakes and uses OGG file '%s'",
                        GetName(), entries[i]!!.name
                    )
                    ret = true
                }
                i++
            }
            return ret
        }

        private fun Init() {
            desc.set("<no description>")
            errorDuringParse = false
            onDemand = false
            numEntries = 0
            numLeadins = 0
            leadinVolume = 0.0f
            altSound = null
        }

        private fun ParseShader(src: idLexer): Boolean {
            var i: Int
            val token = idToken()
            parms.minDistance = 1.0f
            parms.maxDistance = 10.0f
            parms.volume = 1.0f
            parms.shakes = 0.0f
            parms.soundShaderFlags = 0
            parms.soundClass = 0
            speakerMask = 0
            altSound = null
            i = 0
            while (i < SOUND_MAX_LIST_WAVS) {
                leadins[i] = null
                entries[i] = null
                i++
            }
            numEntries = 0
            numLeadins = 0
            var maxSamples: Int = idSoundSystemLocal.s_maxSoundsPerShader.GetInteger()
            if (Common.com_makingBuild.GetBool() || maxSamples <= 0 || maxSamples > SOUND_MAX_LIST_WAVS) {
                maxSamples = SOUND_MAX_LIST_WAVS
            }
            while (true) {
                if (!src.ExpectAnyToken(token)) {
                    return false
                } // end of definition
                else if (token.toString() == "}") {
                    break
                } // minimum number of sounds
                else if (0 == token.Icmp("minSamples")) {
                    maxSamples = idMath.ClampInt(src.ParseInt(), SOUND_MAX_LIST_WAVS, maxSamples)
                } // description
                else if (0 == token.Icmp("description")) {
                    src.ReadTokenOnLine(token)
                    desc.set(token)
                } // mindistance
                else if (0 == token.Icmp("mindistance")) {
                    parms.minDistance = src.ParseFloat()
                } // maxdistance
                else if (0 == token.Icmp("maxdistance")) {
                    parms.maxDistance = src.ParseFloat()
                } // shakes screen
                else if (0 == token.Icmp("shakes")) {
                    src.ExpectAnyToken(token)
                    if (token.type == Token.TT_NUMBER) {
                        parms.shakes = token.GetFloatValue()
                    } else {
                        src.UnreadToken(token)
                        parms.shakes = 1.0f
                    }
                } // reverb
                else if (0 == token.Icmp("reverb")) {
                    src.ParseFloat()
                    if (!src.ExpectTokenString(",")) {
                        src.FreeSource()
                        return false
                    }
                    src.ParseFloat()
                    // no longer supported
                } // volume
                else if (0 == token.Icmp("volume")) {
                    parms.volume = src.ParseFloat()
                } // leadinVolume is used to allow light breaking leadin sounds to be much louder than the broken loop
                else if (0 == token.Icmp("leadinVolume")) {
                    leadinVolume = src.ParseFloat()
                } // speaker mask
                else if (0 == token.Icmp("mask_center")) {
                    speakerMask = speakerMask or (1 shl TempDump.etoi(speakerLabel.SPEAKER_CENTER))
                } // speaker mask
                else if (0 == token.Icmp("mask_left")) {
                    speakerMask = speakerMask or (1 shl TempDump.etoi(speakerLabel.SPEAKER_LEFT))
                } // speaker mask
                else if (0 == token.Icmp("mask_right")) {
                    speakerMask = speakerMask or (1 shl TempDump.etoi(speakerLabel.SPEAKER_RIGHT))
                } // speaker mask
                else if (0 == token.Icmp("mask_backright")) {
                    speakerMask = speakerMask or (1 shl TempDump.etoi(speakerLabel.SPEAKER_BACKRIGHT))
                } // speaker mask
                else if (0 == token.Icmp("mask_backleft")) {
                    speakerMask = speakerMask or (1 shl TempDump.etoi(speakerLabel.SPEAKER_BACKLEFT))
                } // speaker mask
                else if (0 == token.Icmp("mask_lfe")) {
                    speakerMask = speakerMask or (1 shl TempDump.etoi(speakerLabel.SPEAKER_LFE))
                } // soundClass
                else if (0 == token.Icmp("soundClass")) {
                    parms.soundClass = src.ParseInt()
                    if (parms.soundClass < 0 || parms.soundClass >= SOUND_MAX_CLASSES) {
                        src.Warning("SoundClass out of range")
                        return false
                    }
                } // altSound
                else if (0 == token.Icmp("altSound")) {
                    if (!src.ExpectAnyToken(token)) {
                        return false
                    }
                    altSound = DeclManager.declManager.FindSound(token)
                } // ordered
                else if (0 == token.Icmp("ordered")) {
                    // no longer supported
                } // no_dups
                else if (0 == token.Icmp("no_dups")) {
                    parms.soundShaderFlags = parms.soundShaderFlags or SSF_NO_DUPS
                } // no_flicker
                else if (0 == token.Icmp("no_flicker")) {
                    parms.soundShaderFlags = parms.soundShaderFlags or SSF_NO_FLICKER
                } // plain
                else if (0 == token.Icmp("plain")) {
                    // no longer supported
                } // looping
                else if (0 == token.Icmp("looping")) {
                    parms.soundShaderFlags = parms.soundShaderFlags or SSF_LOOPING
                } // no occlusion
                else if (0 == token.Icmp("no_occlusion")) {
                    parms.soundShaderFlags = parms.soundShaderFlags or SSF_NO_OCCLUSION
                } // private
                else if (0 == token.Icmp("private")) {
                    parms.soundShaderFlags = parms.soundShaderFlags or SSF_PRIVATE_SOUND
                } // antiPrivate
                else if (0 == token.Icmp("antiPrivate")) {
                    parms.soundShaderFlags = parms.soundShaderFlags or SSF_ANTI_PRIVATE_SOUND
                } // once
                else if (0 == token.Icmp("playonce")) {
                    parms.soundShaderFlags = parms.soundShaderFlags or SSF_PLAY_ONCE
                } // global
                else if (0 == token.Icmp("global")) {
                    parms.soundShaderFlags = parms.soundShaderFlags or SSF_GLOBAL
                } // unclamped
                else if (0 == token.Icmp("unclamped")) {
                    parms.soundShaderFlags = parms.soundShaderFlags or SSF_UNCLAMPED
                } // omnidirectional
                else if (0 == token.Icmp("omnidirectional")) {
                    parms.soundShaderFlags = parms.soundShaderFlags or SSF_OMNIDIRECTIONAL
                } // onDemand can't be a parms, because we must track all references and overrides would confuse it
                else if (0 == token.Icmp("onDemand")) {
                    // no longer loading sounds on demand
                    //onDemand = true;
                } // the wave files
                else if (0 == token.Icmp("leadin")) {
                    // add to the leadin list
                    if (!src.ReadToken(token)) {
                        src.Warning("Expected sound after leadin")
                        return false
                    }
                    if (snd_system.soundSystemLocal.soundCache != null && numLeadins < maxSamples) {
                        leadins[numLeadins] = snd_system.soundSystemLocal.soundCache!!.FindSound(token, onDemand)
                        numLeadins++
                    }
                } else if (token.Find(".wav", false) != -1 || token.Find(".ogg", false) != -1) {
                    // add to the wav list
                    if (snd_system.soundSystemLocal.soundCache != null && numEntries < maxSamples) {
                        token.BackSlashesToSlashes()
                        val lang = idStr(CVarSystem.cvarSystem.GetCVarString("sys_lang"))
                        if (lang.Icmp("english") != 0 && token.Find("sound/vo/", false) >= 0) {
                            val work = idStr(token)
                            work.ToLower()
                            work.StripLeading("sound/vo/")
                            work.set(Str.va("sound/vo/%s/%s", lang.toString(), work.toString()))
                            if (FileSystem_h.fileSystem.ReadFile(work.toString(), null, null) > 0) {
                                token.set(work)
                            } else {
                                // also try to find it with the .ogg extension
                                work.SetFileExtension(".ogg")
                                if (FileSystem_h.fileSystem.ReadFile(work, null, null) > 0) {
                                    token.set(work)
                                }
                            }
                        }
                        entries[numEntries] = snd_system.soundSystemLocal.soundCache!!.FindSound(token, onDemand)
                        numEntries++
                    }
                } else {
                    src.Warning("unknown token '%s'", token)
                    return false
                }
            }
            if (parms.shakes > 0.0f) {
                CheckShakesAndOgg()
            }
            return true
        }

        fun oSet(FindSound: idSoundShader) {
            throw UnsupportedOperationException("Not supported yet.")
        }

        //
        //
        init {
            Init()
        }
    }
}
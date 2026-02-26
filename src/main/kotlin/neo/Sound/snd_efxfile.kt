package neo.Sound

import neo.idlib.Text.Lexer
import neo.idlib.Text.Lexer.idLexer
import neo.idlib.Text.Str.idStr
import neo.idlib.containers.List.idList
import java.nio.ByteBuffer

class snd_efxfile {
    class idSoundEffect //	~idSoundEffect() {
    //		if ( data && datasize ) {
    //			Mem_Free( data );
    //			data = NULL;
    //		}
    //	}
    {
        var data: ByteBuffer? = null
        var datasize = 0
        val name: idStr = idStr()
    }

    class idEFXFile {
        val effects: idList<idSoundEffect>

        //	~idEFXFile();
        fun FindEffect(name: idStr, effect: Array<idSoundEffect>, index: IntArray): Boolean {
            var i: Int
            i = 0
            while (i < effects.Num()) {
                if (effects[i].name == name) {
                    effect[0] = effects[i]
                    index[0] = i
                    return true
                }
                i++
            }
            return false
        }

        fun ReadEffect(src: idLexer, effect: idSoundEffect): Boolean {
            return false
            //            idToken name, token;
//
//            if (!src.ReadToken(token))
//                return false;
//
//            // reverb effect
//            if (token.equals("reverb")) {
//                EAXREVERBPROPERTIES reverb = (EAXREVERBPROPERTIES *) Mem_Alloc(sizeof(EAXREVERBPROPERTIES));
//                if (EFXUtil.isEffectSupported(EFX10.AL_EFFECT_REVERB)) {
//                    src.ReadTokenOnLine(token);
//                    name = token;
//
//                    if (!src.ReadToken(token)) {
////				Mem_Free( reverb );
//                        return false;
//                    }
//
//                    if (!token.equals("{")) {
//                        src.Error("idEFXFile::ReadEffect: { not found, found %s", token.c_str());
////				Mem_Free( reverb );
//                        return false;
//                    }
//
//                    do {
//                        if (!src.ReadToken(token)) {
//                            src.Error("idEFXFile::ReadEffect: EOF without closing brace");
////					Mem_Free( reverb );
//                            return false;
//                        }
//
//                        if (token.equals("}")) {
//                            effect.name = name;
//                            effect.data = reverb;
//                            effect.datasize = sizeof(EAXREVERBPROPERTIES);
//                            break;
//                        }
//
//                        switch (token.toString()) {
//                            case "environment":
//                                src.ReadTokenOnLine(token);
//                                reverb.ulEnvironment = token.GetUnsignedLongValue();
//                                break;
//                            case "environment size":
//                                reverb.flEnvironmentSize = src.ParseFloat();
//                                break;
//                            case "environment diffusion":
//                                reverb.flEnvironmentDiffusion = src.ParseFloat();
//                                break;
//                            case "room":
//                                reverb.lRoom = src.ParseInt();
//                                break;
//                            case "room hf":
//                                reverb.lRoomHF = src.ParseInt();
//                                break;
//                            case "room lf":
//                                reverb.lRoomLF = src.ParseInt();
//                                break;
//                            case "decay time":
//                                reverb.flDecayTime = src.ParseFloat();
//                                break;
//                            case "decay hf ratio":
//                                reverb.flDecayHFRatio = src.ParseFloat();
//                                break;
//                            case "decay lf ratio":
//                                reverb.flDecayLFRatio = src.ParseFloat();
//                                break;
//                            case "reflections":
//                                reverb.lReflections = src.ParseInt();
//                                break;
//                            case "reflections delay":
//                                reverb.flReflectionsDelay = src.ParseFloat();
//                                break;
//                            case "reflections pan":
//                                reverb.vReflectionsPan.x = src.ParseFloat();
//                                reverb.vReflectionsPan.y = src.ParseFloat();
//                                reverb.vReflectionsPan.z = src.ParseFloat();
//                                break;
//                            case "reverb":
//                                reverb.lReverb = src.ParseInt();
//                                break;
//                            case "reverb delay":
//                                reverb.flReverbDelay = src.ParseFloat();
//                                break;
//                            case "reverb pan":
//                                reverb.vReverbPan.x = src.ParseFloat();
//                                reverb.vReverbPan.y = src.ParseFloat();
//                                reverb.vReverbPan.z = src.ParseFloat();
//                                break;
//                            case "echo time":
//                                reverb.flEchoTime = src.ParseFloat();
//                                break;
//                            case "echo depth":
//                                reverb.flEchoDepth = src.ParseFloat();
//                                break;
//                            case "modulation time":
//                                reverb.flModulationTime = src.ParseFloat();
//                                break;
//                            case "modulation depth":
//                                reverb.flModulationDepth = src.ParseFloat();
//                                break;
//                            case "air absorption hf":
//                                reverb.flAirAbsorptionHF = src.ParseFloat();
//                                break;
//                            case "hf reference":
//                                reverb.flHFReference = src.ParseFloat();
//                                break;
//                            case "l`f reference":
//                                reverb.flLFReference = src.ParseFloat();
//                                break;
//                            case "room rolloff factor":
//                                reverb.flRoomRolloffFactor = src.ParseFloat();
//                                break;
//                            case "flags":
//                                src.ReadTokenOnLine(token);
//                                reverb.ulFlags = token.GetUnsignedLongValue();
//                                break;
//                            default:
//                                src.ReadTokenOnLine(token);
//                                src.Error("idEFXFile::ReadEffect: Invalid parameter in reverb definition");
////					Mem_Free( reverb );
//                        }
//                    } while (true);
//
//                    return true;
//                }
//            } else {
//                // other effect (not supported at the moment)
//                src.Error("idEFXFile::ReadEffect: Unknown effect definition");
//            }
//
//            return false;
        }


        fun LoadFile(filename: String, OSPath: Boolean = false /*= false*/): Boolean {
            val src = idLexer(Lexer.LEXFL_NOSTRINGCONCAT)
            src.LoadFile(filename, OSPath)
            if (!src.IsLoaded()) {
                return false
            }
            if (!src.ExpectTokenString("Version")) {
                return false //NULL;
            }
            if (src.ParseInt() != 1) {
                src.Error("idEFXFile::LoadFile: Unknown file version")
                return false
            }

//            while (!src.EndOfFile()) {//TODO: would be nice to have some reverb
//                idSoundEffect effect = new idSoundEffect();
//                if (ReadEffect(src, effect)) {
//                    effects.Append(effect);
//                }
//            };
            return true
        }

        fun UnloadFile() {
            Clear()
        }

        fun Clear() {
            effects.DeleteContents(true)
        }

        init {
            effects = idList()
        }
    }
}
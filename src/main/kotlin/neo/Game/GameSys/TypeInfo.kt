package neo.Game.GameSys

import neo.Game.GameSys.NoGameTypeInfo.classTypeInfo_t
import neo.Game.GameSys.NoGameTypeInfo.enumTypeInfo_t
import neo.Game.Game_local
import neo.TempDump
import neo.framework.CmdSystem
import neo.framework.CmdSystem.cmdExecution_t
import neo.framework.CmdSystem.cmdFunction_t
import neo.framework.Common
import neo.framework.File_h.idFile
import neo.idlib.CmdArgs
import neo.idlib.Text.Lexer.idLexer
import neo.idlib.Text.Str
import neo.idlib.Text.Str.idStr
import neo.idlib.Text.Str.idStr.Companion.FindChar
import neo.idlib.Text.Token
import neo.idlib.Text.Token.idToken
import neo.idlib.containers.List.cmp_t
import neo.idlib.containers.List.idList
import neo.idlib.idException
import java.nio.ByteBuffer
import java.util.*

object TypeInfo {
    const val DUMP_GAMELOCAL = false

    /*
     ================
     IsAllowedToChangedFromSaveGames
     ================
     */
    fun IsAllowedToChangedFromSaveGames(
        varName: String,
        varType: String?,
        scope: String,
        prefix: String,
        postfix: String?,
        value: String?
    ): Boolean {
        if (idStr.Icmp(scope, "idAnimator") == 0) {
            if (idStr.Icmp(varName, "forceUpdate") == 0) {
                return true
            }
            if (idStr.Icmp(varName, "lastTransformTime") == 0) {
                return true
            }
            if (idStr.Icmp(varName, "AFPoseTime") == 0) {
                return true
            }
            if (idStr.Icmp(varName, "frameBounds") == 0) {
                return true
            }
        } else if (idStr.Icmp(scope, "idClipModel") == 0) {
            if (idStr.Icmp(varName, "touchCount") == 0) {
                return true
            }
        } else if (idStr.Icmp(scope, "idEntity") == 0) {
            if (idStr.Icmp(varName, "numPVSAreas") == 0) {
                return true
            }
            if (idStr.Icmp(varName, "renderView") == 0) {
                return true
            }
        } else if (idStr.Icmp(scope, "idBrittleFracture") == 0) {
            if (idStr.Icmp(varName, "changed") == 0) {
                return true
            }
        } else if (idStr.Icmp(scope, "idPhysics_AF") == 0) {
            return true
        } else if (idStr.Icmp(scope, "renderEntity_t") == 0) {
            // These get fixed up when UpdateVisuals is called
            if (idStr.Icmp(varName, "origin") == 0) {
                return true
            }
            if (idStr.Icmp(varName, "axis") == 0) {
                return true
            }
            if (idStr.Icmp(varName, "bounds") == 0) {
                return true
            }
        }
        return idStr.Icmpn(prefix, "idAFEntity_Base::af.idAF::physicsObj.idPhysics_AF", 49) == 0
    }

    /*
     ================
     IsRenderHandleVariable
     ================
     */
    fun IsRenderHandleVariable(
        varName: String,
        varType: String?,
        scope: String,
        prefix: String?,
        postfix: String?,
        value: String?
    ): Boolean {
        if (idStr.Icmp(scope, "idClipModel") == 0) {
            return idStr.Icmp(varName, "renderModelHandle") == 0
        } else if (idStr.Icmp(scope, "idFXLocalAction") == 0) {
            return if (idStr.Icmp(varName, "lightDefHandle") == 0) {
                true
            } else idStr.Icmp(varName, "modelDefHandle") == 0
        } else if (idStr.Icmp(scope, "idEntity") == 0) {
            return idStr.Icmp(varName, "modelDefHandle") == 0
        } else if (idStr.Icmp(scope, "idLight") == 0) {
            return idStr.Icmp(varName, "lightDefHandle") == 0
        } else if (idStr.Icmp(scope, "idAFEntity_Gibbable") == 0) {
            return idStr.Icmp(varName, "skeletonModelDefHandle") == 0
        } else if (idStr.Icmp(scope, "idAFEntity_SteamPipe") == 0) {
            return idStr.Icmp(varName, "steamModelHandle") == 0
        } else if (idStr.Icmp(scope, "idItem") == 0) {
            return idStr.Icmp(varName, "itemShellHandle") == 0
        } else if (idStr.Icmp(scope, "idExplodingBarrel") == 0) {
            return if (idStr.Icmp(varName, "particleModelDefHandle") == 0) {
                true
            } else idStr.Icmp(varName, "lightDefHandle") == 0
        } else if (idStr.Icmp(scope, "idProjectile") == 0) {
            return idStr.Icmp(varName, "lightDefHandle") == 0
        } else if (idStr.Icmp(scope, "idBFGProjectile") == 0) {
            return idStr.Icmp(varName, "secondModelDefHandle") == 0
        } else if (idStr.Icmp(scope, "idSmokeParticles") == 0) {
            return idStr.Icmp(varName, "renderEntityHandle") == 0
        } else if (idStr.Icmp(scope, "idWeapon") == 0) {
            if (idStr.Icmp(varName, "muzzleFlashHandle") == 0) {
                return true
            }
            if (idStr.Icmp(varName, "worldMuzzleFlashHandle") == 0) {
                return true
            }
            return if (idStr.Icmp(varName, "guiLightHandle") == 0) {
                true
            } else idStr.Icmp(varName, "nozzleGlowHandle") == 0
        }
        return false
    }

    abstract class WriteVariableType_t {
        abstract fun run(
            varName: String,
            varType: String,
            scope: String,
            prefix: String,
            postfix: String,
            value: String,
            varPtr: ByteBuffer?,
            varSize: Int
        )
    }

    object idTypeInfoTools {
        var buffers: Array<CharArray> = Array(4) { CharArray(16384) }
        var index = 0
        private var Write: WriteVariableType_t? = null
        private var fp: idFile? = null
        private var initValue = 0

        //
        //
        private val src: idLexer = idLexer()
        private var typeError = false
        fun FindClassInfo(typeName: String): classTypeInfo_t? {
            var i: Int
            i = 0
            while (NoGameTypeInfo.classTypeInfo[i].typeName != null) {
                if (idStr.Cmp(typeName, NoGameTypeInfo.classTypeInfo[i].typeName!!) == 0) {
                    return NoGameTypeInfo.classTypeInfo[i]
                }
                i++
            }
            return null
        }

        fun FindEnumInfo(typeName: String): enumTypeInfo_t? {
            var i: Int
            i = 0
            while (NoGameTypeInfo.enumTypeInfo[i].typeName != null) {
                if (idStr.Cmp(typeName, NoGameTypeInfo.enumTypeInfo[i].typeName!!) == 0) {
                    return NoGameTypeInfo.enumTypeInfo[i]
                }
                i++
            }
            return null
        }

        fun IsSubclassOf(typeName: idStr, superType: String): Boolean {
            var i: Int
            while (!typeName.IsEmpty()) {
                if (idStr.Cmp(typeName.toString(), superType) == 0) {
                    return true
                }
                i = 0
                while (NoGameTypeInfo.classTypeInfo[i].typeName != null) {
                    if (idStr.Cmp(typeName.toString(), NoGameTypeInfo.classTypeInfo[i].typeName!!) == 0) {
                        typeName.set(NoGameTypeInfo.classTypeInfo[i].superType)
                        break
                    }
                    i++
                }
                if (NoGameTypeInfo.classTypeInfo[i].typeName == null) {
                    Common.common.Warning("super class %s not found", typeName)
                    break
                }
            }
            return false
        }

        fun PrintType(typePtr: ByteBuffer, typeName: String) {
            fp = null
            initValue = 0
            Write = PrintVariable.INSTANCE
            WriteClass_r(typePtr, "", typeName, "", "", 0)
        }

        fun WriteTypeToFile(fp: idFile?, typePtr: ByteBuffer, typeName: String) {
            idTypeInfoTools.fp = fp
            initValue = 0
            Write = WriteVariable.INSTANCE
            WriteClass_r(typePtr, "", typeName, "", "", 0)
        }

        fun InitTypeVariables(typePtr: ByteBuffer?, typeName: String?, value: Int) {
            InitTypeVariables(typePtr, typeName, value)
        }

        fun WriteGameState(fileName: String?) {
            throw UnsupportedOperationException()
            //            int i, num;
//            idFile file;
//
//            file = fileSystem.OpenFileWrite(fileName);
//            if (NOT(file)) {
//                common.Warning("couldn't open %s", fileName);
//                return;
//            }
//
//            fp = file;
//            Write = WriteGameStateVariable.INSTANCE; //WriteVariable;
//
//            if (DUMP_GAMELOCAL) {
//
//                file.WriteFloatString("\ngameLocal {\n");
//                WriteClass_r( /*(void *)&*/gameLocal, "", "idGameLocal", "idGameLocal", "", 0);
//                file.WriteFloatString("}\n");
//
//            }
//
//            for (num = i = 0; i < gameLocal.num_entities; i++) {
//                idEntity ent = gameLocal.entities[i];
//                if (ent == null) {
//                    continue;
//                }
//                file.WriteFloatString("\nentity %d %s {\n", i, ent.GetType().classname);
//                WriteClass_r( /*(void *)*/ent, "", ent.GetType().classname, ent.GetType().classname, "", 0);
//                file.WriteFloatString("}\n");
//                num++;
//            }
//
//            fileSystem.CloseFile(file);
//
//            common.Printf("%d entities written\n", num);
        }

        fun CompareGameState(fileName: String?) {
            throw UnsupportedOperationException()
            //            int entityNum;
//            idToken token = new idToken();
//
//            src = new idLexer();
//            src.SetFlags(LEXFL_NOSTRINGESCAPECHARS);
//
//            if (!src.LoadFile(fileName)) {
//                common.Warning("couldn't load %s", fileName);
////		delete src;
//                src = null;
//                return;
//            }
//
//            fp = null;
//            Write = VerifyVariable.INSTANCE;
//
//            if (DUMP_GAMELOCAL) {
//
//                if (!src.ExpectTokenString("gameLocal") || !src.ExpectTokenString("{")) {
////		delete src;
//                    src = null;
//                    return;
//                }
//
//                WriteClass_r( /*(void *)&*/gameLocal, "", "idGameLocal", "idGameLocal", "", 0);
//
//                if (!src.ExpectTokenString("}")) {
////		delete src;
//                    src = null;
//                    return;
//                }
//
//            }
//
//            while (src.ReadToken(token)) {
//                if (!token.equals("entity")) {
//                    break;
//                }
//                if (NOT(src.ExpectTokenType(TT_NUMBER, TT_INTEGER, token))) {
//                    break;
//                }
//
//                entityNum = token.GetIntValue();
//
//                if (entityNum < 0 || entityNum >= gameLocal.num_entities) {
//                    src.Warning("entity number %d out of range", entityNum);
//                    break;
//                }
//
//                typeError = false;
//
//                idEntity ent = gameLocal.entities[entityNum];
//                if (NOT(ent)) {
//                    src.Warning("entity %d is not spawned", entityNum);
//                    src.SkipBracedSection(true);
//                    continue;
//                }
//
//                if (NOT(src.ExpectTokenType(TT_NAME, 0, token))) {
//                    break;
//                }
//
//                if (token.Cmp(ent.GetType().classname) != 0) {
//                    src.Warning("entity %d has wrong type", entityNum);
//                    src.SkipBracedSection(true);
//                    continue;
//                }
//
//                if (!src.ExpectTokenString("{")) {
//                    src.Warning("entity %d missing leading {", entityNum);
//                    break;
//                }
//
//                WriteClass_r( /*(void *)*/ent, "", ent.GetType().classname, ent.GetType().classname, "", 0);
//
//                if (!src.SkipBracedSection(false)) {
//                    src.Warning("entity %d missing trailing }", entityNum);
//                    break;
//                }
//            }
//
////	delete src;
//            src = null;
        }

        private fun OutputString(string: String): String {
            val out: CharArray
            var i: Int
            var c: Int
            out = buffers[index]
            index = index + 1 and 3
            if (string.isNullOrEmpty()) {
                return ""
            }
            i = 0.also { c = it }
            while (i < buffers[0].size - 2) {
                c++
                when (string[c]) {
                    '\u0000' -> {
                        out[i] = '\u0000'
                        return TempDump.ctos(out)
                    }

                    '\\' -> {
                        out[i++] = '\\'
                        out[i] = '\\'
                    }

                    '\n' -> {
                        out[i++] = '\\'
                        out[i] = 'n'
                    }

                    '\r' -> {
                        out[i++] = '\\'
                        out[i] = 'r'
                    }

                    '\t' -> {
                        out[i++] = '\\'
                        out[i] = 't'
                    }

                    '\u000B' -> {
                        out[i++] = '\\'
                        out[i] = 'v'
                    }

                    else -> out[i] = string[c]
                }
                i++
            }
            out[i] = '\u0000'
            return TempDump.ctos(out)
        }

        private fun ParseTemplateArguments(src: idLexer, arguments: idStr): Boolean {
            var indent: Int
            val token = idToken()
            arguments.set("")
            if (!src.ExpectTokenString("<")) {
                return false
            }
            indent = 1
            while (indent != 0) {
                if (!src.ReadToken(token)) {
                    break
                }
                if (token.toString() == "<") {
                    indent++
                } else if (token.toString() == ">") {
                    indent--
                } else {
                    if (arguments.Length() != 0) {
                        arguments.Append(" ")
                    }
                    arguments.Append(token)
                }
            }
            return true
        }

        private fun WriteVariable_r(
            varPtr: ByteBuffer?,
            varName: String?,
            varType: String?,
            scope: String?,
            prefix: String?,
            pointerDepth: Int
        ): Int {
            throw UnsupportedOperationException()
            //            int i, isPointer, typeSize;
//            idLexer typeSrc = new idLexer();
//            idToken token = new idToken();
//            idStr typeString, templateArgs;
//
//            isPointer = 0;
//            typeSize = -1;
//
//            // create a type string without 'const', 'mutable', 'class', 'struct', 'union'
//            typeSrc.LoadMemory(varType, varType.length(), varName);
//            while (typeSrc.ReadToken(token)) {
//                if (!token.equals("const") && !token.equals("mutable") && !token.equals("class") && !token.equals("struct") && !token.equals("union")) {
//                    typeString.Append(token + " ");
//                }
//            }
//            typeString.StripTrailing(' ');
//            typeSrc.FreeSource();
//
//            // if this is an array
//            if (typeString.oGet(typeString.Length() - 1) == ']') {
//                for (i = typeString.Length(); i > 0 && typeString.oGet(i - 1) != '['; i--) {
//                }
//                int num = typeString.oGet(i);
//                idStr listVarType = typeString;
//                listVarType.CapLength(i - 1);
//                typeSize = 0;
//                for (i = 0; i < num; i++) {
//                    String listVarName = va("%s[%d]", varName, i);
//                    int size = WriteVariable_r(varPtr, listVarName, listVarType.toString(), scope, prefix, pointerDepth);
//                    typeSize += size;
//                    if (size == -1) {
//                        break;
//                    }
//                    varPtr = /*(void *)*/ (( /*(byte *)*/varPtr) + size);
//                }
//                return typeSize;
//            }
//
//            // if this is a pointer
//            isPointer = 0;
//            for (i = typeString.Length(); i > 0 && typeString.oGet(i - 1) == '*'; i -= 2) {
//                if (varPtr == /*(void *)*/ 0xcdcdcdcd || (varPtr != null && /*((unsigned long *)*/ varPtr) == 0xcdcdcdcd) {
//                    common.Warning("%s%s::%s%s references uninitialized memory", prefix, scope, varName, "");
//                    return typeSize;
//                }
//                if (varPtr != null) {
//                    varPtr = /*((void **)*/ varPtr;
//                }
//                isPointer++;
//            }
//
//            if (varPtr == null) {
//                Write.run(varName, varType, scope, prefix, "", "<NULL>", varPtr, 0);
//                return sizeof/*( void * )*/;
//            }
//
//            typeSrc.LoadMemory(typeString.toString(), typeString.Length(), varName);
//
//            if (!typeSrc.ReadToken(token)) {
//                Write.run(varName, varType, scope, prefix, "", va("<unknown type '%s'>", varType), varPtr, 0);
//                return -1;
//            }
//
//            // get full type
//            while (typeSrc.CheckTokenString("::")) {
//                idToken newToken = new idToken();
//                typeSrc.ExpectTokenType(TT_NAME, 0, newToken);
//                token.Append("::" + newToken);
//            }
//
//            if (token.equals("signed")) {
//
//                if (!typeSrc.ReadToken(token)) {
//                    Write.run(varName, varType, scope, prefix, "", va("<unknown type '%s'>", varType), varPtr, 0);
//                    return -1;
//                }
//                if (token.equals("char")) {
//
//                    typeSize = sizeof(/*signed char*/short);
//                    Write.run(varName, varType, scope, prefix, "", va("%d", /**
//                             * ((signed char *)
//                             */
//                            varPtr), varPtr, typeSize);
//
//                } else if (token.equals("short")) {
//
//                    typeSize = sizeof(short);
//                    Write.run(varName, varType, scope, prefix, "", va("%d", /**
//                             * ((signed short *)
//                             */
//                            varPtr), varPtr, typeSize);
//
//                } else if (token.equals("int")) {
//
//                    typeSize = sizeof(int);
//                    Write.run(varName, varType, scope, prefix, "", va("%d", /**
//                             * ((signed int *)
//                             */
//                            varPtr), varPtr, typeSize);
//
//                } else if (token.equals("long")) {
//
//                    typeSize = sizeof(long);
//                    Write.run(varName, varType, scope, prefix, "", va("%ld", /**
//                             * ((signed long *)
//                             */
//                            varPtr), varPtr, typeSize);
//
//                } else {
//
//                    Write.run(varName, varType, scope, prefix, "", va("<unknown type '%s'>", varType), varPtr, 0);
//                    return -1;
//                }
//
//            } else if (token.equals("unsigned")) {
//
//                if (!typeSrc.ReadToken(token)) {
//                    Write.run(varName, varType, scope, prefix, "", va("<unknown type '%s'>", varType), varPtr, 0);
//                    return -1;
//                }
//                if (token.equals("char")) {
//
//                    typeSize = sizeof(char);
//                    Write.run(varName, varType, scope, prefix, "", va("%d", /**
//                             * ((unsigned char *)
//                             */
//                            varPtr), varPtr, typeSize);
//
//                } else if (token.equals("short")) {
//
//                    typeSize = sizeof(/*unsigned*/short);
//                    Write.run(varName, varType, scope, prefix, "", va("%d", /**
//                             * ((unsigned short *)
//                             */
//                            varPtr), varPtr, typeSize);
//
//                } else if (token.equals("int")) {
//
//                    typeSize = sizeof(/*unsigned*/int);
//                    Write.run(varName, varType, scope, prefix, "", va("%d", /**
//                             * ((unsigned int *)
//                             */
//                            varPtr), varPtr, typeSize);
//
//                } else if (token.equals("long")) {
//
//                    typeSize = sizeof(/*unsigned*/long);
//                    Write.run(varName, varType, scope, prefix, "", va("%lu", /**
//                             * ((unsigned long *)
//                             */
//                            varPtr), varPtr, typeSize);
//
//                } else {
//
//                    Write.run(varName, varType, scope, prefix, "", va("<unknown type '%s'>", varType), varPtr, 0);
//                    return -1;
//                }
//
//            } else if (token.equals("byte")) {
//
//                typeSize = sizeof(byte);
//                Write.run(varName, varType, scope, prefix, "", va("%d", /**
//                         * ((byte *)
//                         */
//                        varPtr), varPtr, typeSize);
//
//            } else if (token.equals("word")) {
//
//                typeSize = sizeof(word);
//                Write.run(varName, varType, scope, prefix, "", va("%d", /* * ((word *)*/ varPtr), varPtr, typeSize);
//
//            } else if (token.equals("dword")) {
//
//                typeSize = sizeof(dword);
//                Write.run(varName, varType, scope, prefix, "", va("%d", /* *((dword */ varPtr), varPtr, typeSize);
//
//            } else if (token.equals("bool")) {//TODO:boolean?
//
//                typeSize = sizeof(bool);
//                Write.run(varName, varType, scope, prefix, "", va("%d", /**
//                         * ((bool *)
//                         */
//                        varPtr), varPtr, typeSize);
//
//            } else if (token.equals("char")) {
//
//                typeSize = sizeof(char);
//                Write.run(varName, varType, scope, prefix, "", va("%d", /**
//                         * ((char *)
//                         */
//                        varPtr), varPtr, typeSize);
//
//            } else if (token.equals("short")) {
//
//                typeSize = sizeof(short);
//                Write.run(varName, varType, scope, prefix, "", va("%d", /**
//                         * ((short *)
//                         */
//                        varPtr), varPtr, typeSize);
//
//            } else if (token.equals("int")) {
//
//                typeSize = sizeof(int);
//                Write.run(varName, varType, scope, prefix, "", va("%d", /**
//                         * ((int *)
//                         */
//                        varPtr), varPtr, typeSize);
//
//            } else if (token.equals("long")) {
//
//                typeSize = sizeof(long);
//                Write.run(varName, varType, scope, prefix, "", va("%ld", /**
//                         * ((long *)
//                         */
//                        varPtr), varPtr, typeSize);
//
//            } else if (token.equals("float")) {
//
//                typeSize = sizeof(float);
//                Write.run(varName, varType, scope, prefix, "", idStr( /**
//                         * ((float *)
//                         */
//                        varPtr).c_str(), varPtr, typeSize);
//
//            } else if (token.equals("double")) {
//
//                typeSize = sizeof(double);
//                Write.run(varName, varType, scope, prefix, "", idStr( /*(float)*((double *)*/varPtr).c_str(), varPtr, typeSize);
//
//            } else if (token.equals("idVec2")) {
//
//                typeSize = sizeof(idVec2);
//                Write.run(varName, varType, scope, prefix, "", (/*(idVec2 *)*/varPtr).ToString(8), varPtr, typeSize);
//
//            } else if (token.equals("idVec3")) {
//
//                typeSize = sizeof(idVec3);
//                Write.run(varName, varType, scope, prefix, "", (/*(idVec3 *)*/varPtr).ToString(8), varPtr, typeSize);
//
//            } else if (token.equals("idVec4")) {
//
//                typeSize = sizeof(idVec4);
//                Write.run(varName, varType, scope, prefix, "", (/*(idVec4 *)*/varPtr).ToString(8), varPtr, typeSize);
//
//            } else if (token.equals("idVec5")) {
//
//                typeSize = sizeof(idVec5);
//                Write.run(varName, varType, scope, prefix, "", (/*(idVec5 *)*/varPtr).ToString(8), varPtr, typeSize);
//
//            } else if (token.equals("idVec6")) {
//
//                typeSize = sizeof(idVec6);
//                Write.run(varName, varType, scope, prefix, "", (/*(idVec6 *)*/varPtr).ToString(8), varPtr, typeSize);
//
//            } else if (token.equals("idVecX")) {
//
//                final idVecX vec = (/*(idVecX *)*/varPtr);
//                if (vec.ToFloatPtr() != null) {
//                    Write.run(varName, varType, scope, prefix, "", vec.ToString(8), vec.ToFloatPtr(), vec.GetSize());
//                } else {
//                    Write.run(varName, varType, scope, prefix, "", "<NULL>", varPtr, 0);
//                }
//                typeSize = sizeof(idVecX);
//
//            } else if (token.equals("idMat2")) {
//
//                typeSize = sizeof(idMat2);
//                Write.run(varName, varType, scope, prefix, "", (/*(idMat2 *)*/varPtr).ToString(8), varPtr, typeSize);
//
//            } else if (token.equals("idMat3")) {
//
//                typeSize = sizeof(idMat3);
//                Write.run(varName, varType, scope, prefix, "", (/*(idMat3 *)*/varPtr).ToString(8), varPtr, typeSize);
//
//            } else if (token.equals("idMat4")) {
//
//                typeSize = sizeof(idMat4);
//                Write.run(varName, varType, scope, prefix, "", (/*(idMat4 *)*/varPtr).ToString(8), varPtr, typeSize);
//
//            } else if (token.equals("idMat5")) {
//
//                typeSize = sizeof(idMat5);
//                Write.run(varName, varType, scope, prefix, "", (/*(idMat5 *)*/varPtr).ToString(8), varPtr, typeSize);
//
//            } else if (token.equals("idMat6")) {
//
//                typeSize = sizeof(idMat6);
//                Write.run(varName, varType, scope, prefix, "", (/*(idMat6 *)*/varPtr).ToString(8), varPtr, typeSize);
//
//            } else if (token.equals("idMatX")) {
//
//                typeSize = sizeof(idMatX);
//                final idMatX mat = (/*(idMatX *)*/varPtr);
//                if (mat.ToFloatPtr() != null) {
//                    Write.run(varName, varType, scope, prefix, "", mat.ToString(8), mat.ToFloatPtr(), mat.GetNumColumns() * mat.GetNumRows());
//                } else {
//                    Write.run(varName, varType, scope, prefix, "", "<NULL>", null, 0);
//                }
//
//            } else if (token.equals("idAngles")) {
//
//                typeSize = sizeof(idAngles);
//                Write.run(varName, varType, scope, prefix, "", (/*(idAngles *)*/varPtr).ToString(8), varPtr, typeSize);
//
//            } else if (token.equals("idQuat")) {
//
//                typeSize = sizeof(idQuat);
//                Write.run(varName, varType, scope, prefix, "", (/*(idQuat *)*/varPtr).ToString(8), varPtr, typeSize);
//
//            } else if (token.equals("idBounds")) {
//
//                typeSize = sizeof(idBounds);
//                final idBounds bounds = (/*(idBounds *)*/varPtr);
//                if (bounds.IsCleared()) {
//                    Write.run(varName, varType, scope, prefix, "", "<cleared>", varPtr, typeSize);
//                } else {
//                    Write.run(varName, varType, scope, prefix, "", va("(%s)-(%s)", bounds.oGet(0).ToString(8), bounds.oGet(1).ToString(8)), varPtr, typeSize);
//                }
//
//            } else if (token.equals("idList")) {
//
//                idList<Integer> list = (/*(idList<int> *)*/varPtr);
//                Write.run(varName, varType, scope, prefix, ".num", va("%d", list.Num()), null, 0);
//                // NOTE: we don't care about the amount of memory allocated
//                //Write( varName, varType, scope, prefix, ".size", va( "%d", list.Size() ), NULL, 0 );
//                Write.run(varName, varType, scope, prefix, ".granularity", va("%d", list.GetGranularity()), null, 0);
//
//                if (list.Num() != 0 && ParseTemplateArguments(typeSrc, templateArgs)) {
//                    ByteBuffer listVarPtr = list.Ptr();
//                    for (i = 0; i < list.Num(); i++) {
//                        String listVarName = va("%s[%d]", varName, i);
//                        int size = WriteVariable_r(listVarPtr, listVarName, templateArgs, scope, prefix, pointerDepth);
//                        if (size == -1) {
//                            break;
//                        }
//                        listVarPtr = /*(void *)( ( (byte *)*/ listVarPtr + size;
//                    }
//                }
//
//                typeSize = sizeof(idList < int >);
//
//            } else if (token.equals("idStaticList")) {
//                idStaticList<Integer> list = new idStaticList<>(1, varPtr);
//                Write.run(varName, varType, scope, prefix, ".num", va("%d", list.Num()), null, 0);
//
//                int totalSize = 0;
//                if (list.Num() != 0 && ParseTemplateArguments(typeSrc, templateArgs)) {
//                    ByteBuffer listVarPtr = list.Ptr();
//                    for (i = 0; i < list.Num(); i++) {
//                        String listVarName = va("%s[%d]", varName, i);
//                        int size = WriteVariable_r(listVarPtr, listVarName, templateArgs, scope, prefix, pointerDepth);
//                        if (size == -1) {
//                            break;
//                        }
//                        totalSize += size;
//                        listVarPtr = /*(void *)( ( (byte *)*/ listVarPtr + size;
//                    }
//                }
//
//                typeSize = sizeof(int) + totalSize;
//
//            } else if (token.equals("idLinkList")) {
//
//                // FIXME: implement
//                typeSize = sizeof(idLinkList < idEntity >);
//                Write.run(varName, varType, scope, prefix, "", va("<unknown type '%s'>", varType), null, 0);
//
//            } else if (token.equals("idStr")) {
//
//                typeSize = sizeof(idStr);
//
//                final idStr str = (/*(idStr *)*/varPtr);
//                Write.run(varName, varType, scope, prefix, "", OutputString(str.toString()), str.c_str(), str.Length());
//
//            } else if (token.equals("idStrList")) {
//
//                typeSize = sizeof(idStrList);
//
//                final idStrList list = (/*(idStrList *)*/varPtr);
//                if (list.Num() != 0) {
//                    for (i = 0; i < list.Num(); i++) {
//                        Write.run(varName, varType, scope, prefix, va("[%d]", i), OutputString(list.oGet(i).c_str()), list.oGet(i).c_str(), list.oGet(i).Length());
//                    }
//                } else {
//                    Write.run(varName, varType, scope, prefix, "", "<empty>", null, 0);
//                }
//
//            } else if (token.equals("idDict")) {
//
//                typeSize = sizeof(idDict);
//
//                final idDict dict = (/*(idDict *)*/varPtr);
//                if (dict.GetNumKeyVals() != 0) {
//                    for (i = 0; i < dict.GetNumKeyVals(); i++) {
//                        final idKeyValue kv = dict.GetKeyVal(i);
//                        Write.run(varName, varType, scope, prefix, va("[%d]", i), va("\'%s\'  \'%s\'", OutputString(kv.GetKey().c_str()), OutputString(kv.GetValue().c_str())), null, 0);
//                    }
//                } else {
//                    Write.run(varName, varType, scope, prefix, "", "<empty>", null, 0);
//                }
//
//            } else if (token.equals("idExtrapolate")) {
//
//                final idExtrapolate<Float> interpolate = (/*(idExtrapolate<float> *)*/varPtr);
//                Write.run(varName, varType, scope, prefix, ".extrapolationType", idStr(interpolate.GetExtrapolationType()).c_str(), interpolate.extrapolationType, sizeof(interpolate.extrapolationType));
//                Write.run(varName, varType, scope, prefix, ".startTime", idStr(interpolate.GetStartTime()).c_str(), interpolate.startTime, sizeof(interpolate.startTime));
//                Write.run(varName, varType, scope, prefix, ".duration", idStr(interpolate.GetDuration()).c_str(), interpolate.duration, sizeof(interpolate.duration));
//
//                if (ParseTemplateArguments(typeSrc, templateArgs)) {
//                    if (templateArgs.equals("int")) {
//                        final idExtrapolate<Integer> interpolate = ((idExtrapolate<Integer>) varPtr);
//                        Write.run(varName, varType, scope, prefix, ".startValue", idStr(interpolate.GetStartValue()).c_str(), interpolate.startValue, sizeof(interpolate.startValue));
//                        Write.run(varName, varType, scope, prefix, ".baseSpeed", idStr(interpolate.GetBaseSpeed()).c_str(), interpolate.baseSpeed, sizeof(interpolate.baseSpeed));
//                        Write.run(varName, varType, scope, prefix, ".speed", idStr(interpolate.GetSpeed()).c_str(), interpolate.speed, sizeof(interpolate.speed));
//                        typeSize = sizeof(idExtrapolate < int >);
//                    } else if (templateArgs.equals("float")) {
//                        final idExtrapolate<Float> interpolate = ((idExtrapolate<Float>) varPtr);
//                        Write.run(varName, varType, scope, prefix, ".startValue", idStr(interpolate.GetStartValue()).c_str(), interpolate.startValue, sizeof(interpolate.startValue));
//                        Write.run(varName, varType, scope, prefix, ".baseSpeed", idStr(interpolate.GetBaseSpeed()).c_str(), interpolate.baseSpeed, sizeof(interpolate.baseSpeed));
//                        Write.run(varName, varType, scope, prefix, ".speed", idStr(interpolate.GetSpeed()).c_str(), interpolate.speed, sizeof(interpolate.speed));
//                        typeSize = sizeof(idExtrapolate < float >);
//                    } else if (templateArgs.equals("idVec3")) {
//                        final idExtrapolate<idVec3> interpolate = ((idExtrapolate<idVec3>) varPtr);
//                        Write.run(varName, varType, scope, prefix, ".startValue", interpolate.GetStartValue().ToString(8), interpolate.startValue, sizeof(interpolate.startValue));
//                        Write.run(varName, varType, scope, prefix, ".baseSpeed", interpolate.GetBaseSpeed().ToString(8), interpolate.baseSpeed, sizeof(interpolate.baseSpeed));
//                        Write.run(varName, varType, scope, prefix, ".speed", interpolate.GetSpeed().ToString(8), interpolate.speed, sizeof(interpolate.speed));
//                        typeSize = sizeof(idExtrapolate < idVec3 >);
//                    } else if (templateArgs.equals("idAngles")) {
//                        final idExtrapolate<idAngles> interpolate = ((idExtrapolate<idAngles>) varPtr);
//                        Write.run(varName, varType, scope, prefix, ".startValue", interpolate.GetStartValue().ToString(8), interpolate.startValue, sizeof(interpolate.startValue));
//                        Write.run(varName, varType, scope, prefix, ".baseSpeed", interpolate.GetBaseSpeed().ToString(8), interpolate.baseSpeed, sizeof(interpolate.baseSpeed));
//                        Write.run(varName, varType, scope, prefix, ".speed", interpolate.GetSpeed().ToString(8), interpolate.speed, sizeof(interpolate.speed));
//                        typeSize = sizeof(idExtrapolate < idAngles >);
//                    } else {
//                        Write.run(varName, varType, scope, prefix, "", va("<unknown template argument type '%s' for idExtrapolate>", templateArgs.c_str()), null, 0);
//                    }
//                }
//
//            } else if (token.equals("idInterpolate")) {
//
//                final idInterpolate<Float> interpolate = ((idInterpolate<Float>) varPtr);
//                Write.run(varName, varType, scope, prefix, ".startTime", idStr(interpolate.GetStartTime()).c_str(), interpolate.startTime, sizeof(interpolate.startTime));
//                Write.run(varName, varType, scope, prefix, ".duration", idStr(interpolate.GetDuration()).c_str(), interpolate.duration, sizeof(interpolate.duration));
//
//                if (ParseTemplateArguments(typeSrc, templateArgs)) {
//                    if (templateArgs.equals("int")) {
//                        final idInterpolate<Integer> interpolate = (/*(idInterpolate<int> *)*/varPtr);
//                        Write.run(varName, varType, scope, prefix, ".startValue", idStr(interpolate.GetStartValue()).c_str(), interpolate.startValue, sizeof(interpolate.startValue));
//                        Write.run(varName, varType, scope, prefix, ".endValue", idStr(interpolate.GetEndValue()).c_str(), interpolate.endValue, sizeof(interpolate.endValue));
//                        typeSize = sizeof(idInterpolate < int >);
//                    } else if (templateArgs.equals("float")) {
//                        final idInterpolate<Float> interpolate = ((idInterpolate<Float>) varPtr);
//                        Write.run(varName, varType, scope, prefix, ".startValue", idStr(interpolate.GetStartValue()).c_str(), interpolate.startValue, sizeof(interpolate.startValue));
//                        Write.run(varName, varType, scope, prefix, ".endValue", idStr(interpolate.GetEndValue()).c_str(), interpolate.endValue, sizeof(interpolate.endValue));
//                        typeSize = sizeof(idInterpolate < float >);
//                    } else {
//                        Write.run(varName, varType, scope, prefix, "", va("<unknown template argument type '%s' for idInterpolate>", templateArgs.c_str()), null, 0);
//                    }
//                }
//
//            } else if (token.equals("idInterpolateAccelDecelLinear")) {
//
//                final idInterpolateAccelDecelLinear<Float> interpolate = ((idInterpolateAccelDecelLinear<Float>) varPtr);
//                Write.run(varName, varType, scope, prefix, ".startTime", idStr(interpolate.GetStartTime()).c_str(), interpolate.startTime, sizeof(interpolate.startTime));
//                Write.run(varName, varType, scope, prefix, ".accelTime", idStr(interpolate.GetAcceleration()).c_str(), interpolate.accelTime, sizeof(interpolate.accelTime));
//                Write.run(varName, varType, scope, prefix, ".linearTime", idStr(interpolate.linearTime).c_str(), interpolate.linearTime, sizeof(interpolate.linearTime));
//                Write.run(varName, varType, scope, prefix, ".decelTime", idStr(interpolate.GetDeceleration()).c_str(), interpolate.decelTime, sizeof(interpolate.decelTime));
//
//                if (ParseTemplateArguments(typeSrc, templateArgs)) {
//                    if (templateArgs.equals("int")) {
//                        final idInterpolateAccelDecelLinear<Integer> interpolate = ((idInterpolateAccelDecelLinear<Integer>) varPtr);
//                        Write.run(varName, varType, scope, prefix, ".startValue", idStr(interpolate.GetStartValue()).c_str(), interpolate.startValue, sizeof(interpolate.startValue));
//                        Write.run(varName, varType, scope, prefix, ".endValue", idStr(interpolate.GetEndValue()).c_str(), interpolate.endValue, sizeof(interpolate.endValue));
//                        typeSize = sizeof(idInterpolateAccelDecelLinear < int >);
//                    } else if (templateArgs.equals("float")) {
//                        final idInterpolateAccelDecelLinear<Float> interpolate = ((idInterpolateAccelDecelLinear<Float>) varPtr);
//                        Write.run(varName, varType, scope, prefix, ".startValue", idStr(interpolate.GetStartValue()).c_str(), interpolate.startValue, sizeof(interpolate.startValue));
//                        Write.run(varName, varType, scope, prefix, ".endValue", idStr(interpolate.GetEndValue()).c_str(), interpolate.endValue, sizeof(interpolate.endValue));
//                        typeSize = sizeof(idInterpolateAccelDecelLinear < float >);
//                    } else {
//                        Write.run(varName, varType, scope, prefix, "", va("<unknown template argument type '%s' for idInterpolateAccelDecelLinear>", templateArgs.c_str()), null, 0);
//                    }
//                }
//
//            } else if (token.equals("idInterpolateAccelDecelSine")) {
//
//                final idInterpolateAccelDecelSine<Float> interpolate = ((idInterpolateAccelDecelSine<Float>) varPtr);
//                Write.run(varName, varType, scope, prefix, ".startTime", idStr(interpolate.GetStartTime()).c_str(), interpolate.startTime, sizeof(interpolate.startTime));
//                Write.run(varName, varType, scope, prefix, ".accelTime", idStr(interpolate.GetAcceleration()).c_str(), interpolate.accelTime, sizeof(interpolate.accelTime));
//                Write.run(varName, varType, scope, prefix, ".linearTime", idStr(interpolate.linearTime).c_str(), interpolate.linearTime, sizeof(interpolate.linearTime));
//                Write.run(varName, varType, scope, prefix, ".decelTime", idStr(interpolate.GetDeceleration()).c_str(), interpolate.decelTime, sizeof(interpolate.decelTime));
//
//                if (ParseTemplateArguments(typeSrc, templateArgs)) {
//                    if (templateArgs.equals("int")) {
//                        final idInterpolateAccelDecelSine<Integer> interpolate = ((idInterpolateAccelDecelSine<Integer>) varPtr);
//                        Write.run(varName, varType, scope, prefix, ".startValue", idStr(interpolate.GetStartValue()).c_str(), interpolate.startValue, sizeof(interpolate.startValue));
//                        Write.run(varName, varType, scope, prefix, ".endValue", idStr(interpolate.GetEndValue()).c_str(), interpolate.endValue, sizeof(interpolate.endValue));
//                        typeSize = sizeof(idInterpolateAccelDecelSine < int >);
//                    } else if (templateArgs.equals("float")) {
//                        final idInterpolateAccelDecelSine<Float> interpolate = ((idInterpolateAccelDecelSine<Float>) varPtr);
//                        Write.run(varName, varType, scope, prefix, ".startValue", idStr(interpolate.GetStartValue()).c_str(), interpolate.startValue, sizeof(interpolate.startValue));
//                        Write.run(varName, varType, scope, prefix, ".endValue", idStr(interpolate.GetEndValue()).c_str(), interpolate.endValue, sizeof(interpolate.endValue));
//                        typeSize = sizeof(idInterpolateAccelDecelSine < float >);
//                    } else {
//                        Write.run(varName, varType, scope, prefix, "", va("<unknown template argument type '%s' for idInterpolateAccelDecelSine>", templateArgs.c_str()), null, 0);
//                    }
//                }
//
//            } else if (token.equals("idUserInterface")) {
//
//                typeSize = sizeof(idUserInterface);
//                final idUserInterface gui = ((idUserInterface) varPtr);
//                Write.run(varName, varType, scope, prefix, "", gui.Name(), varPtr, sizeof(varPtr));
//
//            } else if (token.equals("idRenderModel")) {
//
//                typeSize = sizeof(idRenderModel);
//                final idRenderModel model = ((idRenderModel) varPtr);
//                Write.run(varName, varType, scope, prefix, "", model.Name(), varPtr, sizeof(varPtr));
//
//            } else if (token.equals("qhandle_t")) {
//
//                typeSize = sizeof(int);
//                Write.run(varName, varType, scope, prefix, "", va("%d", /**
//                         * ((int *)
//                         */
//                        varPtr), varPtr, typeSize);
//
//            } else if (token.equals("cmHandle_t")) {
//
//                typeSize = sizeof(int);
//                Write.run(varName, varType, scope, prefix, "", va("%d", /**
//                         * ((int *)
//                         */
//                        varPtr), varPtr, typeSize);
//
//            } else if (token.equals("idEntityPtr")) {
//
//                typeSize = sizeof(idEntityPtr < idEntity >);
//
//                final idEntityPtr<idEntity> entPtr = ((idEntityPtr<idEntity>) varPtr);
//                if (entPtr.GetEntity() != null) {
//                    idEntity entity = entPtr.GetEntity();
//                    Write.run(varName, varType, scope, prefix, ".", va("entity %d: \'%s\'", entity.entityNumber, entity.name.c_str()), varPtr, typeSize);
//                } else {
//                    Write.run(varName, varType, scope, prefix, "", "<null>", varPtr, typeSize);
//                }
//
//            } else if (token.equals("idEntity::entityFlags_s")) {
//
//                final idEntity.entityFlags_s flags = ((idEntity.entityFlags_s) varPtr);
//                Write.run(varName, varType, scope, prefix, ".notarget", flags.notarget ? "true" : "false", null, 0);
//                Write.run(varName, varType, scope, prefix, ".noknockback", flags.noknockback ? "true" : "false", null, 0);
//                Write.run(varName, varType, scope, prefix, ".takedamage", flags.takedamage ? "true" : "false", null, 0);
//                Write.run(varName, varType, scope, prefix, ".hidden", flags.hidden ? "true" : "false", null, 0);
//                Write.run(varName, varType, scope, prefix, ".bindOrientated", flags.bindOrientated ? "true" : "false", null, 0);
//                Write.run(varName, varType, scope, prefix, ".solidForTeam", flags.solidForTeam ? "true" : "false", null, 0);
//                Write.run(varName, varType, scope, prefix, ".forcePhysicsUpdate", flags.forcePhysicsUpdate ? "true" : "false", null, 0);
//                Write.run(varName, varType, scope, prefix, ".selected", flags.selected ? "true" : "false", null, 0);
//                Write.run(varName, varType, scope, prefix, ".neverDormant", flags.neverDormant ? "true" : "false", null, 0);
//                Write.run(varName, varType, scope, prefix, ".isDormant", flags.isDormant ? "true" : "false", null, 0);
//                Write.run(varName, varType, scope, prefix, ".hasAwakened", flags.hasAwakened ? "true" : "false", null, 0);
//                Write.run(varName, varType, scope, prefix, ".networkSync", flags.networkSync ? "true" : "false", null, 0);
//                typeSize = sizeof(idEntity.entityFlags_s);
//
//            } else if (token.equals("idScriptBool")) {
//
//                typeSize = sizeof(idScriptBool);
//
//                final idScriptBool scriptBool = ((idScriptBool) varPtr);
//                if (scriptBool.IsLinked()) {
//                    Write.run(varName, varType, scope, prefix, "", (scriptBool != 0) ? "true" : "false", varPtr, typeSize);
//                } else {
//                    Write.run(varName, varType, scope, prefix, "", "<not linked>", varPtr, typeSize);
//                }
//
//            } else {
//
//                final classTypeInfo_t classTypeInfo = FindClassInfo(scope + ("::" + token));
//                if (classTypeInfo == null) {
//                    classTypeInfo = FindClassInfo(token);
//                }
//                if (classTypeInfo != null) {
//
//                    typeSize = classTypeInfo.size;
//
//                    if (0 == isPointer) {
//
//                        char[] newPrefix = new char[1024];
//                        idStr.snPrintf(newPrefix, sizeof(newPrefix), "%s%s::%s.", prefix, scope, varName);
//                        WriteClass_r(varPtr, "", token, token, newPrefix, pointerDepth);
//
//                    } else if (token.equals("idAnim")) {
//
//                        final idAnim anim = ((idAnim) varPtr);
//                        Write.run(varName, varType, scope, prefix, "", anim.Name(), null, 0);
//
//                    } else if (token.equals("idPhysics")) {
//
//                        final idPhysics physics = ((idPhysics) varPtr);
//                        Write.run(varName, varType, scope, prefix, "", physics.GetType().classname, null, 0);
//
//                    } else if (IsSubclassOf(token, "idEntity")) {
//
//                        final idEntity entity = ((idEntity) varPtr);
//                        Write.run(varName, varType, scope, prefix, "", va("entity %d: \'%s\'", entity.entityNumber, entity.name.c_str()), null, 0);
//
//                    } else if (IsSubclassOf(token, "idDecl")) {

//                        final idDecl decl = ((idDecl) varPtr);
//                        Write.run(varName, varType, scope, prefix, "", decl.GetName(), null, 0);
//
//                    } else if (pointerDepth == 0 && (token.equals("idAFBody")
//                            || token.equals("idAFTree")
//                            || token.equals("idClipModel")
//                            || IsSubclassOf(token, "idAFConstraint"))) {
//
//                        char[] newPrefix = new char[1024];
//                        idStr.snPrintf(newPrefix, sizeof(newPrefix), "%s%s::%s.", prefix, scope, varName);
//                        WriteClass_r(varPtr, "", token, token, newPrefix, pointerDepth + 1);
//
//                    } else {
//
//                        Write.run(varName, varType, scope, prefix, "", va("<pointer type '%s' not listed>", varType), null, 0);
//                        return -1;
//                    }
//                } else {
//                    final enumTypeInfo_t enumTypeInfo = FindEnumInfo(scope + ("::" + token));
//                    if (enumTypeInfo == null) {
//                        enumTypeInfo = FindEnumInfo(token);
//                    }
//                    if (enumTypeInfo != null) {
//
//                        typeSize = sizeof(int);	// NOTE: assuming sizeof( enum ) is sizeof( int )
//
//                        for (i = 0; enumTypeInfo.values[i].name != null; i++) {
//                            if ( /**
//                                     * ((int *)
//                                     */
//                                    varPtr == enumTypeInfo.values[i].value) {
//                                break;
//                            }
//                        }
//                        if (enumTypeInfo.values[i].name != null) {
//                            Write.run(varName, varType, scope, prefix, "", enumTypeInfo.values[i].name, null, 0);
//                        } else {
//                            Write.run(varName, varType, scope, prefix, "", va("%d", /**
//                                     * ((int *)
//                                     */
//                                    varPtr), null, 0);
//                        }
//
//                    } else {
//                        Write.run(varName, varType, scope, prefix, "", va("<unknown type '%s'>", varType), null, 0);
//                        return -1;
//                    }
//                }
//            }
//
//            i = 0;
//            do {
//                if ( /*((unsigned long *)*/varPtr == 0xcdcdcdcd) {
//                    common.Warning("%s%s::%s%s uses uninitialized memory", prefix, scope, varName, "");
//                    break;
//                }
//            } while (++i < typeSize);
//
//            if (isPointer != 0) {
//                return sizeof( /*void **/);
//            }
//            return typeSize;
        }

        private fun WriteClass_r(
            classPtr: ByteBuffer,
            className: String?,
            classType: String,
            scope: String?,
            prefix: String?,
            pointerDepth: Int
        ) {
            var i: Int
            val classInfo = FindClassInfo(classType) ?: return
            if (!classInfo.superType.isNullOrEmpty()) {
                WriteClass_r(classPtr, className, classInfo.superType!!, scope, prefix, pointerDepth)
            }
            i = 0
            while (classInfo.variables!![i].name != null) {
                val classVar = classInfo.variables!![i]
                classPtr.position(classVar.offset)
                WriteVariable_r(classPtr, classVar.name, classVar.type, classType, prefix, pointerDepth)
                i++
            }
        }

        private class PrintVariable private constructor() : WriteVariableType_t() {
            override fun run(
                varName: String,
                varType: String,
                scope: String,
                prefix: String,
                postfix: String,
                value: String,
                varPtr: ByteBuffer?,
                varSize: Int
            ) {
                Common.common.Printf("%s%s::%s%s = \"%s\"\n", prefix, scope, varName, postfix, value)
            }

            companion object {
                val INSTANCE: WriteVariableType_t = PrintVariable()
            }
        }

        private class WriteVariable private constructor() : WriteVariableType_t() {
            override fun run(
                varName: String,
                varType: String,
                scope: String,
                prefix: String,
                postfix: String,
                value: String,
                varPtr: ByteBuffer?,
                varSize: Int
            ) {
                var i: Int = FindChar(value, '#', 0)
                while (i >= 0) {
                    if (idStr.Icmpn(value + i + 1, "INF", 3) == 0 || idStr.Icmpn(
                            value + i + 1,
                            "IND",
                            3
                        ) == 0 || idStr.Icmpn(
                            value + i + 1,
                            "NAN",
                            3
                        ) == 0 || idStr.Icmpn(
                            value + i + 1,
                            "QNAN",
                            4
                        ) == 0 || idStr.Icmpn(value + i + 1, "SNAN", 4) == 0
                    ) {
                        Common.common.Warning("%s%s::%s%s = \"%s\"", prefix, scope, varName, postfix, value)
                        break
                    }
                    i = FindChar(value, '#', i + 1)
                }
                fp!!.WriteFloatString("%s%s::%s%s = \"%s\"\n", prefix, scope, varName, postfix, value)
            }

            companion object {
                val INSTANCE: WriteVariableType_t = WriteVariable()
            }
        }

        private class WriteGameStateVariable private constructor() : WriteVariableType_t() {
            override fun run(
                varName: String,
                varType: String,
                scope: String,
                prefix: String,
                postfix: String,
                value: String,
                varPtr: ByteBuffer?,
                varSize: Int
            ) {
                var i: Int = FindChar(value, '#', 0)
                while (i >= 0) {
                    if (idStr.Icmpn(value + i + 1, "INF", 3) == 0 || idStr.Icmpn(
                            value + i + 1,
                            "IND",
                            3
                        ) == 0 || idStr.Icmpn(
                            value + i + 1,
                            "NAN",
                            3
                        ) == 0 || idStr.Icmpn(
                            value + i + 1,
                            "QNAN",
                            4
                        ) == 0 || idStr.Icmpn(value + i + 1, "SNAN", 4) == 0
                    ) {
                        Common.common.Warning("%s%s::%s%s = \"%s\"", prefix, scope, varName, postfix, value)
                        break
                    }
                    i = FindChar(value, '#', i + 1)
                }
                if (IsRenderHandleVariable(varName, varType, scope, prefix, postfix, value)) {
                    return
                }
                if (IsAllowedToChangedFromSaveGames(varName, varType, scope, prefix, postfix, value)) {
                    return
                }
                fp!!.WriteFloatString("%s%s::%s%s = \"%s\"\n", prefix, scope, varName, postfix, value)
            }

            companion object {
                val INSTANCE: WriteVariableType_t = WriteGameStateVariable()
            }
        }

        private class InitVariable private constructor() : WriteVariableType_t() {
            override fun run(
                varName: String,
                varType: String,
                scope: String,
                prefix: String,
                postfix: String,
                value: String,
                varPtr: ByteBuffer?,
                varSize: Int
            ) {
                if (varPtr != null && varSize > 0) {
                    // NOTE: skip renderer handles
                    if (IsRenderHandleVariable(varName, varType, scope, prefix, postfix, value)) {
                        return
                    }
                    //                    memset(const_cast < void * > (varPtr), initValue, varSize);
                    Arrays.fill(varPtr.array(), 0, varSize, initValue.toByte())
                }
            }

            companion object {
                val INSTANCE: WriteVariableType_t = InitVariable()
            }
        }

        private class VerifyVariable private constructor() : WriteVariableType_t() {
            override fun run(
                varName: String,
                varType: String,
                scope: String,
                prefix: String,
                postfix: String,
                value: String,
                varPtr: ByteBuffer?,
                varSize: Int
            ) {
                val token = idToken()
                if (typeError) {
                    return
                }
                src.SkipUntilString("=")
                src.ExpectTokenType(Token.TT_STRING, 0, token)
                if (token.Cmp(value) != 0) {

                    // NOTE: skip several things
                    if (IsRenderHandleVariable(varName, varType, scope, prefix, postfix, value)) {
                        return
                    }
                    if (IsAllowedToChangedFromSaveGames(varName, varType, scope, prefix, postfix, value)) {
                        return
                    }
                    src.Warning("state diff for %s%s::%s%s\n%s\n%s", prefix, scope, varName, postfix, token, value)
                    typeError = true
                }
            }

            companion object {
                val INSTANCE: WriteVariableType_t = VerifyVariable()
            }
        }
    }

    /*
     ================
     WriteGameState_f
     ================
     */
    class WriteGameState_f private constructor() : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            val fileName: idStr
            fileName = if (args!!.Argc() > 1) {
                idStr(args.Argv(1))
            } else {
                idStr("GameState.txt")
            }
            fileName.SetFileExtension("gameState.txt")
            idTypeInfoTools.WriteGameState(fileName.toString())
        }

        companion object {
            private val instance: cmdFunction_t = WriteGameState_f()
            fun getInstance(): cmdFunction_t {
                return instance
            }
        }
    }

    /*
     ================
     CompareGameState_f
     ================
     */
    class CompareGameState_f private constructor() : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            val fileName: idStr
            fileName = if (args!!.Argc() > 1) {
                idStr(args.Argv(1))
            } else {
                idStr("GameState.txt")
            }
            fileName.SetFileExtension("gameState.txt")
            idTypeInfoTools.CompareGameState(fileName.toString())
        }

        companion object {
            private val instance: cmdFunction_t = CompareGameState_f()
            fun getInstance(): cmdFunction_t {
                return instance
            }
        }
    }

    /*
     ================
     TestSaveGame_f
     ================
     */
    class TestSaveGame_f private constructor() : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            val name: idStr
            if (args!!.Argc() <= 1) {
                Game_local.gameLocal.Printf("testSaveGame <mapName>\n")
                return
            }
            name = idStr(args.Argv(1))
            try {
                CmdSystem.cmdSystem.BufferCommandText(cmdExecution_t.CMD_EXEC_NOW, Str.va("map %s", name))
                name.Replace("\\", "_")
                name.Replace("/", "_")
                CmdSystem.cmdSystem.BufferCommandText(cmdExecution_t.CMD_EXEC_NOW, Str.va("saveGame test_%s", name))
                CmdSystem.cmdSystem.BufferCommandText(cmdExecution_t.CMD_EXEC_NOW, Str.va("loadGame test_%s", name))
            } catch (ex: idException) {
                // an ERR_DROP was thrown
            }
            CmdSystem.cmdSystem.BufferCommandText(cmdExecution_t.CMD_EXEC_NOW, "quit")
        }

        companion object {
            private val instance: cmdFunction_t = TestSaveGame_f()
            fun getInstance(): cmdFunction_t {
                return instance
            }
        }
    }

    /*
     ================
     ListTypeInfo_f
     ================
     */
    class ListTypeInfo_f private constructor() : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            var i: Int
            var j: Int
            val index = idList<Int>()
            Common.common.Printf("%-32s : %-32s size (B)\n", "type name", "super type name")
            i = 0
            while (NoGameTypeInfo.classTypeInfo[i].typeName != null) {
                index.Append(i)
                i++
            }
            if (args!!.Argc() > 1 && idStr.Icmp(args.Argv(1), "size") == 0) {
                index.Sort(SortTypeInfoBySize())
            } else {
                index.Sort(SortTypeInfoByName())
            }
            i = 0
            while (NoGameTypeInfo.classTypeInfo[i].typeName != null) {
                j = index[i]
                Common.common.Printf(
                    "%-32s : %-32s %d\n",
                    NoGameTypeInfo.classTypeInfo[j].typeName!!,
                    NoGameTypeInfo.classTypeInfo[j].superType!!,
                    NoGameTypeInfo.classTypeInfo[j].size
                )
                i++
            }
        }

        private class SortTypeInfoByName : cmp_t<Int> {
            override fun compare(a: Int, b: Int): Int {
                return idStr.Icmp(
                    NoGameTypeInfo.classTypeInfo.get(a).typeName!!,
                    NoGameTypeInfo.classTypeInfo.get(b).typeName!!
                )
            }
        }

        private class SortTypeInfoBySize : cmp_t<Int> {
            override fun compare(a: Int, b: Int): Int {
                if (NoGameTypeInfo.classTypeInfo[a].size < NoGameTypeInfo.classTypeInfo[b].size) {
                    return -1
                }
                return if (NoGameTypeInfo.classTypeInfo[a].size > NoGameTypeInfo.classTypeInfo[b].size) {
                    1
                } else 0
            }
        }

        companion object {
            private val instance: cmdFunction_t = ListTypeInfo_f()
            fun getInstance(): cmdFunction_t { //TODO:remove function and expose "instance" variable?
                return instance
            }
        }
    }
}
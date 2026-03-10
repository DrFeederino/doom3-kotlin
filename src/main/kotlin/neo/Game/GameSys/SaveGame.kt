/*
 * Copyright (C) 1999-2011 id Software LLC, a ZeniMax Media company.
 * Translated to Kotlin by Dr. Feederino with support of Claude Code
 *
 * This file is part of the Doom 3 Kotlin project.
 * Original source: neo/game/gamesys/SaveGame.h, neo/game/gamesys/SaveGame.cpp
 *
 * Doom 3 Source Code is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package neo.Game.GameSys

import neo.Game.Animation.idDeclModelDef
import neo.Game.Game.refSound_t
import neo.Game.GameSys.Class.idClass
import neo.Game.GameSys.Class.idTypeInfo
import neo.Game.Game_local
import neo.Game.Game_local.idGameLocal
import neo.Game.Physics.Clip.idClipModel
import neo.Game.idEntity
import neo.Renderer.Material
import neo.Renderer.Model.idRenderModel
import neo.Renderer.ModelManager
import neo.Renderer.RenderWorld
import neo.Renderer.RenderWorld.renderEntity_s
import neo.Renderer.RenderWorld.renderLight_s
import neo.Renderer.RenderWorld.renderView_s
import neo.Sound.snd_shader.idSoundShader
import neo.TempDump.SERiAL
import neo.cm.contactInfo_t
import neo.cm.contactType_t
import neo.cm.trace_s
import neo.framework.BUILD_NUMBER
import neo.framework.DeclFX.idDeclFX
import neo.framework.DeclManager
import neo.framework.DeclManager.declType_t
import neo.framework.DeclParticle.idDeclParticle
import neo.framework.DeclSkin.idDeclSkin
import neo.framework.File_h.idFile
import neo.framework.UsercmdGen.usercmd_t
import neo.idlib.BV.idBounds
import neo.idlib.Dict_h.idDict
import neo.idlib.Dict_h.idKeyValue
import neo.idlib.LittleRevBytes
import neo.idlib.Text.Str.idStr
import neo.idlib.containers.CBool
import neo.idlib.containers.CFloat
import neo.idlib.containers.CInt
import neo.idlib.containers.List.idList
import neo.idlib.geometry.TraceModel
import neo.idlib.geometry.TraceModel.idTraceModel
import neo.idlib.geometry.TraceModel.traceModel_t
import neo.idlib.geometry.Winding.idWinding
import neo.idlib.math.*
import neo.idlib.math.Matrix.idMat3
import neo.ui.UserInterface
import neo.ui.UserInterface.idUserInterface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

object SaveGame {
    /*
    Save game related helper classes.

    Save games are implemented in two classes, idSaveGame and idRestoreGame, that implement write/read functions for
    common types.  They're passed in to each entity and object for them to archive themselves.  Each class
    implements save/restore functions for it's own data.  When restoring, all the objects are instantiated,
    then the restore function is called on each, superclass first, then subclasses.

    Pointers are restored by saving out an object index for each unique object pointer and adding them to a list of
    objects that are to be saved.  Restore instantiates all the objects in the list before calling the Restore function
    on each object so that the pointers returned are valid.  No object's restore function should rely on any other objects
    being fully instantiated until after the restore process is complete.  Post restore fixup should be done by posting
    events with 0 delay.

    The savegame header will have the Game Name, Version, Map Name, and Player Persistent Info.

    Changes in version make savegames incompatible, and the game will start from the beginning of the level with
    the player's persistent info.

    Changes to classes that don't need to break compatibilty can use the build number as the savegame version.
    Later versions are responsible for restoring from previous versions by ignoring any unused data and initializing
    variables that weren't in previous versions with safe information.

    At the head of the save game is enough information to restore the player to the beginning of the level should the
    file be unloadable in some way (for example, due to script changes).
    */
    const val INITIAL_RELEASE_BUILD_NUMBER = 1262

    class idSaveGame(private val file: idFile) {
        private val objects: idList<idClass?>

        /*
        ================
        idSaveGame::Close
        ================
        */
        fun Close() {
            var i: Int
            WriteSoundCommands()

            // read trace models
            idClipModel.SaveTraceModels(this)
            i = 1
            while (i < objects.Num()) {
                CallSave_r(objects[i]!!.GetType(), objects[i])
                i++
            }
            objects.Clear()

// #ifdef ID_DEBUG_MEMORY
            // idStr gameState = file.GetName();
            // gameState.StripFileExtension();
            // WriteGameState_f( idCmdArgs( va( "test %s_save", gameState.c_str() ), false ) );
// #endif
        }

        /*
        ================
        idSaveGame::AddObject
        ================
        */
        fun AddObject(obj: idClass) {
            objects.AddUnique(obj)
        }

        /*
        ================
        idSaveGame::WriteObjectList
        ================
        */
        fun WriteObjectList() {
            var i: Int
            WriteInt(objects.Num() - 1)
            i = 1
            while (i < objects.Num()) {
                WriteString(objects[i]!!.GetClassname())
                i++
            }
        }

        /*
        ================
        idSaveGame::Write
        ================
        */
        fun Write(buffer: ByteBuffer, len: Int) {
            file.Write(buffer, len)
        }

        fun Write(buffer: SERiAL) {
            file.Write(buffer)
        }

        /*
        ================
        idSaveGame::WriteInt
        ================
        */
        fun WriteInt(value: Int) {
            file.WriteInt(value)
        }

        /*
        ================
        idSaveGame::WriteJoint
        ================
        */
        fun WriteJoint(   /*jointHandle_t*/value: Int) {
            file.WriteInt(value)
        }

        /*
        ================
        idSaveGame::WriteShort
        ================
        */
        fun WriteShort(value: Short) {
            file.WriteShort(value)
        }

        /*
        ================
        idSaveGame::WriteByte
        ================
        */
        fun WriteByte(value: Byte) {
            val buffer = ByteBuffer.allocate(1)
            buffer.put(value)
            buffer.flip()
            file.Write(buffer, 1)
        }

        /*
        ================
        idSaveGame::WriteSignedChar
        ================
        */
        fun WriteSignedChar(   /*signed char*/value: Short) {
            // FIX: C++ writes sizeof(signed char) = 1 byte, not sizeof(short) = 2 bytes
            val buffer = ByteBuffer.allocate(1)
            buffer.put(value.toByte())
            buffer.flip()
            file.Write(buffer, 1)
        }

        /*
        ================
        idSaveGame::WriteFloat
        ================
        */
        fun WriteFloat(value: Float) {
            file.WriteFloat(value)
        }

        /*
        ================
        idSaveGame::WriteBool
        ================
        */
        fun WriteBool(value: Boolean) {
            file.WriteBool(value)
        }

        /*
        ================
        idSaveGame::WriteString
        ================
        */
        fun WriteString(string: String) {
            val len: Int
            len = string.length
            WriteInt(len)
            file.Write(StandardCharsets.UTF_8.encode(string), len)
        }

        fun WriteString(string: idStr) {
            this.WriteString(string.toString())
        }

        /*
        ================
        idSaveGame::WriteVec2
        ================
        */
        fun WriteVec2(vec: idVec2) {
            file.WriteVec2(vec)
        }

        /*
        ================
        idSaveGame::WriteVec3
        ================
        */
        fun WriteVec3(vec: idVec3) {
            file.WriteVec3(vec)
        }

        /*
        ================
        idSaveGame::WriteVec4
        ================
        */
        fun WriteVec4(vec: idVec4) {
            file.WriteVec4(vec)
        }

        /*
        ================
        idSaveGame::WriteVec6
        ================
        */
        fun WriteVec6(vec: idVec6) {
            file.WriteVec6(vec)
        }

        /*
        ================
        idSaveGame::WriteWinding
        ================
        */
        fun WriteWinding(w: idWinding) {
            var i: Int
            val num: Int
            num = w.GetNumPoints()
            file.WriteInt(num)
            i = 0
            while (i < num) {
                val v = idVec5(w[i])
                LittleRevBytes(v /*, sizeof(float), sizeof(v) / sizeof(float)*/)
                file.Write(v /*, sizeof(v)*/)
                i++
            }
        }

        /*
        ================
        idSaveGame::WriteBounds
        ================
        */
        fun WriteBounds(bounds: idBounds) {
            LittleRevBytes(bounds /*, sizeof(float), sizeof(b) / sizeof(float)*/)
            file.Write(bounds /*, sizeof(b)*/)
        }

        /*
        ================
        idSaveGame::WriteMat3
        ================
        */
        fun WriteMat3(mat: idMat3) {
            file.WriteMat3(mat)
        }

        /*
        ================
        idSaveGame::WriteAngles
        ================
        */
        fun WriteAngles(angles: idAngles) {
            LittleRevBytes(angles /*, sizeof(float), sizeof(v) / sizeof(float)*/)
            file.Write(angles /*, sizeof(v)*/)
        }

        /*
        ================
        idSaveGame::WriteObject
        ================
        */
        fun WriteObject(obj: idClass?) {
            var index: Int
            index = objects.FindIndex(obj)
            if (index < 0) {
                Game_local.gameLocal.DPrintf("idSaveGame::WriteObject - WriteObject FindIndex failed\n")

                // Use the NULL index
                index = 0
            }
            WriteInt(index)
        }

        /*
        ================
        idSaveGame::WriteStaticObject
        ================
        */
        fun WriteStaticObject(obj: idClass) {
            CallSave_r(obj.GetType(), obj)
        }

        /*
        ================
        idSaveGame::WriteDict
        ================
        */
        fun WriteDict(dict: idDict?) {
            val num: Int
            var i: Int
            var kv: idKeyValue
            if (null == dict) {
                WriteInt(-1)
            } else {
                num = dict.GetNumKeyVals()
                WriteInt(num)
                i = 0
                while (i < num) {
                    kv = dict.GetKeyVal(i)!!
                    WriteString(kv.GetKey())
                    WriteString(kv.GetValue())
                    i++
                }
            }
        }

        /*
        ================
        idSaveGame::WriteMaterial
        ================
        */
        fun WriteMaterial(material: Material.idMaterial?) {
            if (null == material) {
                WriteString("")
            } else {
                WriteString(material.GetName())
            }
        }

        /*
        ================
        idSaveGame::WriteSkin
        ================
        */
        fun WriteSkin(skin: idDeclSkin?) {
            if (null == skin) {
                WriteString("")
            } else {
                WriteString(skin.GetName())
            }
        }

        /*
        ================
        idSaveGame::WriteParticle
        ================
        */
        fun WriteParticle(particle: idDeclParticle?) {
            if (null == particle) {
                WriteString("")
            } else {
                WriteString(particle.GetName())
            }
        }

        /*
        ================
        idSaveGame::WriteFX
        ================
        */
        fun WriteFX(fx: idDeclFX?) {
            if (null == fx) {
                WriteString("")
            } else {
                WriteString(fx.GetName())
            }
        }

        /*
        ================
        idSaveGame::WriteSoundShader
        ================
        */
        fun WriteSoundShader(shader: idSoundShader?) {
            val name: String?
            if (null == shader) {
                WriteString("")
            } else {
                name = shader.GetName()
                WriteString(name)
            }
        }

        /*
        ================
        idSaveGame::WriteModelDef
        ================
        */
        fun WriteModelDef(modelDef: idDeclModelDef?) {
            if (null == modelDef) {
                WriteString("")
            } else {
                WriteString(modelDef.GetName())
            }
        }

        /*
        ================
        idSaveGame::WriteModel
        ================
        */
        fun WriteModel(model: idRenderModel?) {
            val name: String?
            if (null == model) {
                WriteString("")
            } else {
                name = model.Name()
                WriteString(name)
            }
        }

        /*
        ================
        idSaveGame::WriteUserInterface
        ================
        */
        fun WriteUserInterface(ui: idUserInterface?, unique: Boolean) {
            val name: String?
            if (null == ui) {
                WriteString("")
            } else {
                name = ui.Name()
                WriteString(name)
                WriteBool(unique)
                if (ui.WriteToSaveGame(file) == false) {
                    idGameLocal.Error("idSaveGame::WriteUserInterface: ui failed to write properly\n")
                }
            }
        }

        /*
        ================
        idSaveGame::WriteRenderEntity
        ================
        */
        fun WriteRenderEntity(renderEntity: renderEntity_s) {
            var i: Int
            WriteModel(renderEntity.hModel)
            WriteInt(renderEntity.entityNum)
            WriteInt(renderEntity.bodyId)
            WriteBounds(renderEntity.bounds)

            // callback is set by class's Restore function
            WriteInt(renderEntity.suppressSurfaceInViewID)
            WriteInt(renderEntity.suppressShadowInViewID)
            WriteInt(renderEntity.suppressShadowInLightID)
            WriteInt(renderEntity.allowSurfaceInViewID)
            WriteVec3(renderEntity.origin)
            WriteMat3(renderEntity.axis)
            WriteMaterial(renderEntity.customShader)
            WriteMaterial(renderEntity.referenceShader)
            WriteSkin(renderEntity.customSkin)
            if (renderEntity.referenceSound != null) {
                WriteInt(renderEntity.referenceSound!!.Index())
            } else {
                WriteInt(0)
            }
            i = 0
            while (i < Material.MAX_ENTITY_SHADER_PARMS) {
                WriteFloat(renderEntity.shaderParms[i])
                i++
            }
            i = 0
            while (i < RenderWorld.MAX_RENDERENTITY_GUI) {
                // FIX: C++ passes gui[i] which may be NULL; WriteUserInterface handles NULL.
                // Kotlin !! would NPE on null gui slots.
                WriteUserInterface(
                    renderEntity.gui[i],
                    renderEntity.gui[i]?.IsUniqued() ?: false
                )
                i++
            }
            WriteFloat(renderEntity.modelDepthHack)
            WriteBool(renderEntity.noSelfShadow)
            WriteBool(renderEntity.noShadow)
            WriteBool(renderEntity.noDynamicInteractions)
            WriteBool(renderEntity.weaponDepthHack)
            WriteInt(renderEntity.forceUpdate)
        }

        /*
        ================
        idSaveGame::WriteRenderLight
        ================
        */
        fun WriteRenderLight(renderLight: renderLight_s) {
            var i: Int
            WriteMat3(renderLight.axis)
            WriteVec3(renderLight.origin)
            WriteInt(renderLight.suppressLightInViewID.integerValue)
            WriteInt(renderLight.allowLightInViewID.integerValue)
            WriteBool(renderLight.noShadows._val)
            WriteBool(renderLight.noSpecular._val)
            WriteBool(renderLight.pointLight._val)
            WriteBool(renderLight.parallel._val)
            WriteVec3(renderLight.lightRadius)
            WriteVec3(renderLight.lightCenter)
            WriteVec3(renderLight.target)
            WriteVec3(renderLight.right)
            WriteVec3(renderLight.up)
            WriteVec3(renderLight.start)
            WriteVec3(renderLight.end)

            // only idLight has a prelightModel and it's always based on the entityname, so we'll restore it there
            // WriteModel( renderLight.prelightModel );
            WriteInt(renderLight.lightId.integerValue)
            WriteMaterial(renderLight.shader)
            i = 0
            while (i < Material.MAX_ENTITY_SHADER_PARMS) {
                WriteFloat(renderLight.shaderParms[i])
                i++
            }
            if (renderLight.referenceSound != null) {
                WriteInt(renderLight.referenceSound!!.Index())
            } else {
                WriteInt(0)
            }
        }

        /*
        ================
        idSaveGame::WriteRefSound
        ================
        */
        fun WriteRefSound(refSound: refSound_t) {
            if (refSound.referenceSound != null) {
                WriteInt(refSound.referenceSound!!.Index())
            } else {
                WriteInt(0)
            }
            WriteVec3(refSound.origin)
            WriteInt(refSound.listenerId)
            WriteSoundShader(refSound.shader)
            WriteFloat(refSound.diversity)
            WriteBool(refSound.waitfortrigger)
            WriteFloat(refSound.parms.minDistance)
            WriteFloat(refSound.parms.maxDistance)
            WriteFloat(refSound.parms.volume)
            WriteFloat(refSound.parms.shakes)
            WriteInt(refSound.parms.soundShaderFlags)
            WriteInt(refSound.parms.soundClass)
        }

        /*
        ================
        idSaveGame::WriteRenderView
        ================
        */
        fun WriteRenderView(view: renderView_s) {
            var i: Int
            WriteInt(view.viewID)
            WriteInt(view.x)
            WriteInt(view.y)
            WriteInt(view.width)
            WriteInt(view.height)
            WriteFloat(view.fov_x)
            WriteFloat(view.fov_y)
            WriteVec3(view.vieworg)
            WriteMat3(view.viewaxis)
            WriteBool(view.cramZNear)
            WriteInt(view.time)
            i = 0
            while (i < RenderWorld.MAX_GLOBAL_SHADER_PARMS) {
                WriteFloat(view.shaderParms[i])
                i++
            }
        }

        /*
        ===================
        idSaveGame::WriteUsercmd
        ===================
        */
        fun WriteUsercmd(usercmd: usercmd_t) {
            WriteInt(usercmd.gameFrame)
            WriteInt(usercmd.gameTime)
            WriteInt(usercmd.duplicateCount)
            WriteByte(usercmd.buttons)
            WriteSignedChar(usercmd.forwardmove.toShort())
            WriteSignedChar(usercmd.rightmove.toShort())
            WriteSignedChar(usercmd.upmove.toShort())
            WriteShort(usercmd.angles[0])
            WriteShort(usercmd.angles[1])
            WriteShort(usercmd.angles[2])
            WriteShort(usercmd.mx)
            WriteShort(usercmd.my)
            WriteSignedChar(usercmd.impulse.toShort())
            WriteByte(usercmd.flags)
            WriteInt(usercmd.sequence)
        }

        /*
        ===================
        idSaveGame::WriteContactInfo
        ===================
        */
        fun WriteContactInfo(contactInfo: contactInfo_t) {
            WriteInt(contactInfo.type.ordinal)
            WriteVec3(contactInfo.point)
            WriteVec3(contactInfo.normal)
            WriteFloat(contactInfo.dist)
            WriteInt(contactInfo.contents)
            WriteMaterial(contactInfo.material)
            WriteInt(contactInfo.modelFeature)
            WriteInt(contactInfo.trmFeature)
            WriteInt(contactInfo.entityNum)
            WriteInt(contactInfo.id)
        }

        /*
        ===================
        idSaveGame::WriteTrace
        ===================
        */
        fun WriteTrace(trace: trace_s) {
            WriteFloat(trace.fraction)
            WriteVec3(trace.endpos)
            WriteMat3(trace.endAxis)
            WriteContactInfo(trace.c)
        }

        /*
        ===================
        idSaveGame::WriteTraceModel
        ===================
        */
        fun WriteTraceModel(trace: idTraceModel) {
            var j: Int
            var k: Int
            WriteInt(trace.type.ordinal)
            WriteInt(trace.numVerts)
            j = 0
            while (j < TraceModel.MAX_TRACEMODEL_VERTS) {
                WriteVec3(trace.verts[j])
                j++
            }
            WriteInt(trace.numEdges)
            j = 0
            while (j < TraceModel.MAX_TRACEMODEL_EDGES + 1) {
                WriteInt(trace.edges[j].v[0])
                WriteInt(trace.edges[j].v[1])
                WriteVec3(trace.edges[j].normal)
                j++
            }
            WriteInt(trace.numPolys)
            j = 0
            while (j < TraceModel.MAX_TRACEMODEL_POLYS) {
                WriteVec3(trace.polys[j].normal)
                WriteFloat(trace.polys[j].dist)
                WriteBounds(trace.polys[j].bounds)
                WriteInt(trace.polys[j].numEdges)
                k = 0
                while (k < TraceModel.MAX_TRACEMODEL_POLYEDGES) {
                    WriteInt(trace.polys[j].edges[k])
                    k++
                }
                j++
            }
            WriteVec3(trace.offset)
            WriteBounds(trace.bounds)
            WriteBool(trace.isConvex)
            // padding win32 native structs
            // C++: char tmp[3]; memset(tmp, 0, sizeof(tmp)); file->Write(tmp, 3);
            val tmp = ByteBuffer.allocate(3)
            file.Write(tmp, 3)
        }

        /*
        ===================
        idSaveGame::WriteClipModel
        ===================
        */
        fun WriteClipModel(clipModel: idClipModel?) {
            if (clipModel != null) {
                WriteBool(true)
                clipModel.Save(this)
            } else {
                WriteBool(false)
            }
        }

        /*
        ===================
        idSaveGame::WriteSoundCommands
        ===================
        */
        fun WriteSoundCommands() {
            Game_local.gameSoundWorld!!.WriteToSaveGame(file)
        }

        /*
        ======================
        idSaveGame::WriteBuildNumber
        ======================
        */
        fun WriteBuildNumber(value: Int) {
            file.WriteInt(BUILD_NUMBER)
        }

        /*
        ================
        idSaveGame::CallSave_r
        ================
        */
        private fun CallSave_r(cls: idTypeInfo, obj: idClass?) {
            obj?.Save(this)
        }

        /*
        ================
        idSaveGame::idSaveGame
        ================
        */
        init {
            // Put NULL at the start of the list so we can skip over it.
            objects = idList()
            objects.Append(null as idClass?)
        }
    }

    /* **********************************************************************

     idRestoreGame

     ***********************************************************************/
    class idRestoreGame(
        private val file: idFile
    ) {
        private var buildNumber = 0
        private var internalSavegameVersion = 0 // DG added this

        private val objects: idList<idClass> = idList()

        // DG: added these methods, internalSavegameVersion makes us independent of the global BUILD_NUMBER
        fun ReadInternalSavegameVersion() {
            val readVersion = CInt()
            ReadInt(readVersion)
            internalSavegameVersion = readVersion.integerValue
        }

        // if it's 0, this is from a GetBuildNumber() < 1305 savegame
        // otherwise, compare it to idGameLocal::INTERNAL_SAVEGAME_VERSION
        fun GetInternalSavegameVersion(): Int {
            return internalSavegameVersion
        }
        // DG end

        /*
        ================
        idRestoreGame::CreateObjects
        ================
        */
        fun CreateObjects() {
            var i: Int
            val num = CInt()
            val className = idStr()
            var type: idTypeInfo?
            ReadInt(num)

            objects.SetNum(num.integerValue + 1)
            for (i in 1 until objects.Num()) {
                ReadString(className)
                type = idClass.GetClass(className.toString())
                if (type == null) {
                    Error("idRestoreGame::CreateObjects: Unknown class '${className}'")
                }
                objects[i] = type!!.createInstance()
            }
        }

        /*
        ================
        idRestoreGame::RestoreObjects
        ================
        */
        fun RestoreObjects() {
            var i: Int
            ReadSoundCommands()

            // read trace models
            idClipModel.RestoreTraceModels(this)

            // restore all the objects
            for (i in 1 until objects.Num()) {
                CallRestore_r(objects[i].GetType(), objects[i])
            }

            // regenerate render entities and render lights because are not saved
            i = 1
            while (i < objects.Num()) {
                if (objects[i].IsType(idEntity.Type)) {
                    val ent = objects[i] as idEntity
                    ent.UpdateVisuals()
                    ent.Present()
                }
                i++
            }

        }

        /*
        ====================
        idRestoreGame::DeleteObjects
        ====================
        */
        fun DeleteObjects() {
            // Remove the NULL object before deleting
            objects.RemoveIndex(0)
            objects.DeleteContents(true)
        }

        /*
        ================
        idRestoreGame::Error
        ================
        */
        fun Error(fmt: String, vararg objects: Any?) { // id_attribute((format(printf,2,3)));
            this.objects.DeleteContents(true)
            // FIX: must spread vararg with * — without it, the entire Array is passed as a single
            // argument, producing "[Ljava.lang.Object;@hash" instead of the actual values
            idGameLocal.Error(fmt, *objects)
        }

        /*
        ================
        idRestoreGame::Read
        ================
        */
        fun Read(buffer: ByteBuffer, len: Int) {
            file.Read(buffer, len)
        }

        fun Read(buffer: SERiAL) {
            file.Read(buffer)
        }

        /*
        ================
        idRestoreGame::ReadInt
        ================
        */
        fun ReadInt(value: CInt) {
            file.ReadInt(value)
        }

        fun ReadInt(): Int {
            val value = CInt()
            this.ReadInt(value)
            return value.integerValue
        }

        /*
        ================
        idRestoreGame::ReadJoint
        ================
        */
        fun ReadJoint(jointHandle_t: CInt) {
            file.ReadInt(jointHandle_t)
        }

        fun ReadJoint(): Int {
            val jointHandle_t = CInt()
            this.ReadJoint(jointHandle_t)
            return jointHandle_t.integerValue
        }

        /*
        ================
        idRestoreGame::ReadShort
        ================
        */
        fun ReadShort(): Short {
            val value = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN)
            file.Read(value, 2)
            return value.getShort()
        }

        /*
        ================
        idRestoreGame::ReadByte
        ================
        */
        fun ReadByte(): Byte {
            val value = ByteBuffer.allocate(1).order(ByteOrder.LITTLE_ENDIAN)
            file.Read(value, 1)
            return value.get()
        }

        /*
        ================
        idRestoreGame::ReadSignedChar
        ================
        */
        fun ReadSignedChar(value: CharArray) {
            // FIX: C++ reads sizeof(signed char) = 1 byte
            val buffer = ByteBuffer.allocate(1).order(ByteOrder.LITTLE_ENDIAN)
            file.Read(buffer, 1)
            value[0] = buffer[0].toInt().toChar()
        }

        fun ReadSignedChar(): Char {
            val c = CharArray(1)
            ReadSignedChar(c)
            return c[0]
        }

        /*
        ================
        idRestoreGame::ReadFloat
        ================
        */
        fun ReadFloat(value: CFloat) {
            file.ReadFloat(value)
        }

        fun ReadFloat(): Float {
            val value = CFloat()
            this.ReadFloat(value)
            return value._val
        }

        /*
        ================
        idRestoreGame::ReadBool
        ================
        */
        fun ReadBool(value: CBool) {
            file.ReadBool(value)
        }

        fun ReadBool(): Boolean {
            val value = CBool(false)
            this.ReadBool(value)
            return value._val
        }

        /*
        ================
        idRestoreGame::ReadString
        ================
        */
        fun ReadString(string: idStr) {
            val len = CInt()
            ReadInt(len)
            if (len.integerValue < 0) {
                Error("idRestoreGame::ReadString: invalid length")
            }
            string.Fill(' ', len.integerValue)
            file.Read(string, len.integerValue)
        }

        /*
        ================
        idRestoreGame::ReadVec2
        ================
        */
        fun ReadVec2(vec: idVec2) {
            file.ReadVec2(vec)
        }

        /*
        ================
        idRestoreGame::ReadVec3
        ================
        */
        fun ReadVec3(vec: idVec3) {
            file.ReadVec3(vec)
        }

        /*
        ================
        idRestoreGame::ReadVec4
        ================
        */
        fun ReadVec4(vec: idVec4) {
            file.ReadVec4(vec)
        }

        /*
        ================
        idRestoreGame::ReadVec6
        ================
        */
        fun ReadVec6(vec: idVec6) {
            file.ReadVec6(vec)
        }

        /*
        ================
        idRestoreGame::ReadWinding
        ================
        */
        fun ReadWinding(w: idWinding) {
            var i: Int
            val num = CInt()
            file.ReadInt(num)
            w.SetNumPoints(num.integerValue)
            i = 0
            while (i < num.integerValue) {
                file.Read(w[i])
                LittleRevBytes(w[i])
                i++
            }
        }

        /*
        ================
        idRestoreGame::ReadBounds
        ================
        */
        fun ReadBounds(bounds: idBounds) {
            file.Read(bounds)
            LittleRevBytes(bounds)
        }

        /*
        ================
        idRestoreGame::ReadMat3
        ================
        */
        fun ReadMat3(mat: idMat3) {
            file.ReadMat3(mat)
        }

        /*
        ================
        idRestoreGame::ReadAngles
        ================
        */
        fun ReadAngles(angles: idAngles) {
            file.Read(angles /*, sizeof(angles)*/)
            LittleRevBytes(angles /*, sizeof(float), sizeof(idAngles) / sizeof(float)*/)
        }


        // NOTE: Differs from C++ — return-value overload for Kotlin callers
        fun ReadObject(): idClass? {
            val index = ReadInt()
            if (index < 0 || index >= objects.Num()) {
                Error("idRestoreGame::ReadObject: invalid object index")
                return null
            }
            return objects[index]
        }

        /*
        ================
        idRestoreGame::ReadStaticObject
        ================
        */
        fun ReadStaticObject(obj: idClass?) {
            CallRestore_r(obj!!.GetType(), obj)
        }

        /*
        ================
        idRestoreGame::ReadDict
        ================
        */
        fun ReadDict(): idDict? {
            val num = CInt()
            var i: Int
            val key = idStr()
            val value = idStr()
            ReadInt(num)
            if (num.integerValue < 0) {
                return null
            } else {
                val dict = idDict()
                dict.Clear()
                i = 0
                while (i < num.integerValue) {
                    ReadString(key)
                    ReadString(value)
                    dict.Set(key, value)
                    i++
                }
                return dict
            }
        }

        /*
        ================
        idRestoreGame::ReadMaterial
        ================
        */
        fun ReadMaterial(): Material.idMaterial? {
            val name = idStr()
            ReadString(name)
            if (0 == name.Length()) {
                return null
            } else {
                return DeclManager.declManager.FindMaterial(name)
            }
        }

        /*
        ================
        idRestoreGame::ReadSkin
        ================
        */
        fun ReadSkin(): idDeclSkin? {
            val name = idStr()
            ReadString(name)
            if (0 == name.Length()) {
                return null
            } else {
                // FIX: was discarding the FindSkin result — skin was never assigned
                return DeclManager.declManager.FindSkin(name)
            }
        }

        /*
        ================
        idRestoreGame::ReadParticle
        ================
        */
        fun ReadParticle(): idDeclParticle? {
            val name = idStr()
            ReadString(name)
            if (0 == name.Length()) {
                return null
            } else {
                return DeclManager.declManager.FindType(declType_t.DECL_PARTICLE, name) as idDeclParticle?
            }
        }

        /*
        ================
        idRestoreGame::ReadFX
        ================
        */
        fun ReadFX(): idDeclFX? {
            val name = idStr()
            ReadString(name)
            if (0 == name.Length()) {
                return null
            } else {
                return DeclManager.declManager.FindType(declType_t.DECL_FX, name) as idDeclFX?
            }
        }

        /*
        ================
        idRestoreGame::ReadSoundShader
        ================
        */
        fun ReadSoundShader(): idSoundShader? {
            val name = idStr()
            ReadString(name)
            if (0 == name.Length()) {
                return null
            } else {
                return DeclManager.declManager.FindSound(name)
            }
        }

        /*
        ================
        idRestoreGame::ReadModelDef
        ================
        */
        fun ReadModelDef(): idDeclModelDef? {
            val name = idStr()
            ReadString(name)
            if (0 == name.Length()) {
                return null
            } else {
                // FIX: was completely unimplemented — body was commented out
                return DeclManager.declManager.FindType(declType_t.DECL_MODELDEF, name, false) as idDeclModelDef?
            }
        }

        /*
        ================
        idRestoreGame::ReadModel
        ================
        */
        fun ReadModel(): idRenderModel? {
            val name = idStr()
            ReadString(name)
            if (0 == name.Length()) {
                return null
            } else {
                return ModelManager.renderModelManager.FindModel(name.toString())!!
            }
        }

        /*
        ================
        idRestoreGame::ReadUserInterface
        ================
        */
        fun ReadUserInterface(): idUserInterface? {
            val name = idStr()
            ReadString(name)
            if (0 == name.Length()) {
                return null
            } else {
                val unique = CBool(false)
                ReadBool(unique)
                val ui = UserInterface.uiManager.FindGui(name.toString(), true, unique._val)
                if (ui != null) {
                    if (ui.ReadFromSaveGame(file) == false) {
                        Error("idSaveGame::ReadUserInterface: ui failed to read properly\n")
                    } else {
                        ui.StateChanged(Game_local.gameLocal.time)
                    }
                }
                return ui
            }
        }

        /*
        ================
        idRestoreGame::ReadRenderEntity
        ================
        */
        fun ReadRenderEntity(): renderEntity_s {
            var i: Int
            val index = CInt()
            val renderEntity = renderEntity_s()

            renderEntity.hModel = ReadModel()
            renderEntity.entityNum = ReadInt()
            renderEntity.bodyId = ReadInt()
            ReadBounds(renderEntity.bounds)

            // callback is set by class's Restore function
            renderEntity.callback = null
            renderEntity.callbackData = null
            renderEntity.suppressSurfaceInViewID = ReadInt()
            renderEntity.suppressShadowInViewID = ReadInt()
            renderEntity.suppressShadowInLightID = ReadInt()
            renderEntity.allowSurfaceInViewID = ReadInt()
            ReadVec3(renderEntity.origin)
            ReadMat3(renderEntity.axis)
            renderEntity.customShader = ReadMaterial()
            renderEntity.referenceShader = ReadMaterial()
            renderEntity.customSkin = ReadSkin()
            ReadInt(index)
            renderEntity.referenceSound = Game_local.gameSoundWorld!!.EmitterForIndex(index.integerValue)
            i = 0
            while (i < Material.MAX_ENTITY_SHADER_PARMS) {
                renderEntity.shaderParms[i] = ReadFloat()
                i++
            }
            i = 0
            while (i < RenderWorld.MAX_RENDERENTITY_GUI) {
                renderEntity.gui[i] = ReadUserInterface()
                i++
            }

            // idEntity will restore "cameraTarget", which will be used in idEntity::Present to restore the remoteRenderView
            renderEntity.remoteRenderView = null
            renderEntity.joints = null
            renderEntity.numJoints = 0
            renderEntity.modelDepthHack = ReadFloat()
            renderEntity.noSelfShadow = ReadBool()
            renderEntity.noShadow = ReadBool()
            renderEntity.noDynamicInteractions = ReadBool()
            renderEntity.weaponDepthHack = ReadBool()
            renderEntity.forceUpdate = ReadInt()

            return renderEntity
        }

        /*
        ================
        idRestoreGame::ReadRenderLight
        ================
        */
        fun ReadRenderLight(renderLight: renderLight_s) {
            val index = CInt()
            var i: Int
            ReadMat3(renderLight.axis)
            ReadVec3(renderLight.origin)
            renderLight.suppressLightInViewID.integerValue = ReadInt()
            renderLight.allowLightInViewID.integerValue = ReadInt()
            renderLight.noShadows._val = ReadBool()
            renderLight.noSpecular._val = ReadBool()
            renderLight.pointLight._val = ReadBool()
            renderLight.parallel._val = ReadBool()
            ReadVec3(renderLight.lightRadius)
            ReadVec3(renderLight.lightCenter)
            ReadVec3(renderLight.target)
            ReadVec3(renderLight.right)
            ReadVec3(renderLight.up)
            ReadVec3(renderLight.start)
            ReadVec3(renderLight.end)

            // only idLight has a prelightModel and it's always based on the entityname, so we'll restore it there
            // ReadModel( renderLight.prelightModel );
            renderLight.prelightModel = null
            renderLight.lightId.integerValue = ReadInt()
            renderLight.shader = ReadMaterial()
            i = 0
            while (i < Material.MAX_ENTITY_SHADER_PARMS) {
                renderLight.shaderParms[i] = ReadFloat()
                i++
            }
            ReadInt(index)
            renderLight.referenceSound = Game_local.gameSoundWorld!!.EmitterForIndex(index.integerValue)
        }

        /*
        ================
        idRestoreGame::ReadRefSound
        ================
        */
        fun ReadRefSound(refSound: refSound_t) {
            val index = CInt()
            ReadInt(index)
            refSound.referenceSound = Game_local.gameSoundWorld!!.EmitterForIndex(index.integerValue)
            ReadVec3(refSound.origin)
            refSound.listenerId = ReadInt()
            refSound.shader = ReadSoundShader()
            refSound.diversity = ReadFloat()
            refSound.waitfortrigger = ReadBool()
            refSound.parms.minDistance = ReadFloat()
            refSound.parms.maxDistance = ReadFloat()
            refSound.parms.volume = ReadFloat()
            refSound.parms.shakes = ReadFloat()
            refSound.parms.soundShaderFlags = ReadInt()
            refSound.parms.soundClass = ReadInt()
        }

        /*
        ================
        idRestoreGame::ReadRenderView
        ================
        */
        fun ReadRenderView(view: renderView_s) {
            var i: Int
            view.viewID = ReadInt()
            view.x = ReadInt()
            view.y = ReadInt()
            view.width = ReadInt()
            view.height = ReadInt()
            view.fov_x = ReadFloat()
            view.fov_y = ReadFloat()
            ReadVec3(view.vieworg)
            ReadMat3(view.viewaxis)
            view.cramZNear = ReadBool()
            view.time = ReadInt()
            i = 0
            while (i < RenderWorld.MAX_GLOBAL_SHADER_PARMS) {
                view.shaderParms[i] = ReadFloat()
                i++
            }
        }

        /*
        =================
        idRestoreGame::ReadUsercmd
        =================
        */
        fun ReadUsercmd(usercmd: usercmd_t) {
            usercmd.gameFrame = ReadInt()
            usercmd.gameTime = ReadInt()
            usercmd.duplicateCount = ReadInt()
            usercmd.buttons = ReadByte()
            usercmd.forwardmove = ReadSignedChar().code.toByte()
            usercmd.rightmove = ReadSignedChar().code.toByte()
            usercmd.upmove = ReadSignedChar().code.toByte()
            usercmd.angles[0] = ReadShort()
            usercmd.angles[1] = ReadShort()
            usercmd.angles[2] = ReadShort()
            usercmd.mx = ReadShort()
            usercmd.my = ReadShort()
            usercmd.impulse = ReadSignedChar().code.toByte()
            usercmd.flags = ReadByte()
            usercmd.sequence = ReadInt()
        }

        /*
        ===================
        idRestoreGame::ReadContactInfo
        ===================
        */
        fun ReadContactInfo(contactInfo: contactInfo_t) {
            contactInfo.type = contactType_t.entries.toTypedArray()[ReadInt()]
            ReadVec3(contactInfo.point)
            ReadVec3(contactInfo.normal)
            contactInfo.dist = ReadFloat()
            contactInfo.contents = ReadInt()
            contactInfo.material = ReadMaterial()
            contactInfo.modelFeature = ReadInt()
            contactInfo.trmFeature = ReadInt()
            contactInfo.entityNum = ReadInt()
            contactInfo.id = ReadInt()
        }

        /*
        ===================
        idRestoreGame::ReadTrace
        ===================
        */
        fun ReadTrace(trace: trace_s) {
            trace.fraction = ReadFloat()
            ReadVec3(trace.endpos)
            ReadMat3(trace.endAxis)
            ReadContactInfo(trace.c)
        }

        /*
        ===================
        idRestoreGame::ReadTraceModel
        ===================
        */
        fun ReadTraceModel(trace: idTraceModel) {
            var j: Int
            var k: Int
            trace.type = traceModel_t.entries.toTypedArray()[ReadInt()]
            trace.numVerts = ReadInt()
            j = 0
            while (j < TraceModel.MAX_TRACEMODEL_VERTS) {
                ReadVec3(trace.verts[j])
                j++
            }
            trace.numEdges = ReadInt()
            j = 0
            while (j < TraceModel.MAX_TRACEMODEL_EDGES + 1) {
                trace.edges[j].v[0] = ReadInt()
                trace.edges[j].v[1] = ReadInt()
                ReadVec3(trace.edges[j].normal)
                j++
            }
            trace.numPolys = ReadInt()
            j = 0
            while (j < TraceModel.MAX_TRACEMODEL_POLYS) {
                ReadVec3(trace.polys[j].normal)
                trace.polys[j].dist = ReadFloat()
                ReadBounds(trace.polys[j].bounds)
                trace.polys[j].numEdges = ReadInt()
                k = 0
                while (k < TraceModel.MAX_TRACEMODEL_POLYEDGES) {
                    trace.polys[j].edges[k] = ReadInt()
                    k++
                }
                j++
            }
            ReadVec3(trace.offset)
            ReadBounds(trace.bounds)
            trace.isConvex = ReadBool()
            // padding win32 native structs
            // C++: char tmp[3]; file->Read(tmp, 3);
            val tmp = ByteBuffer.allocate(3)
            file.Read(tmp, 3)
        }

        /*
        =====================
        idRestoreGame::ReadClipModel
        =====================
        */
        fun ReadClipModel(): idClipModel? {
            val restoreClipModel: Boolean
            restoreClipModel = ReadBool()
            if (restoreClipModel) {
                // NOTE: Differs from C++ — C++ creates new idClipModel() here; Kotlin reuses the passed-in instance
                val clipModel = idClipModel()
                clipModel.Restore(this)
                return clipModel
            } else {
                return null
            }
        }

        /*
        =====================
        idRestoreGame::ReadSoundCommands
        =====================
        */
        fun ReadSoundCommands() {
            Game_local.gameSoundWorld!!.StopAllSounds()
            Game_local.gameSoundWorld!!.ReadFromSaveGame(file)
        }

        /*
        =====================
        idRestoreGame::ReadBuildNumber
        =====================
        */
        fun ReadBuildNumber() {
            val buildNumber = CInt()
            file.ReadInt(buildNumber)
            this.buildNumber = buildNumber.integerValue
        }

        /*
        =====================
        idRestoreGame::GetBuildNumber
        =====================
        */
        //						Used to retrieve the saved game buildNumber from within class Restore methods
        fun GetBuildNumber(): Int {
            return buildNumber
        }

        /*
        ================
        idRestoreGame::CallRestore_r
        ================
        */
        private fun CallRestore_r(cls: idTypeInfo, obj: idClass?) {
            obj?.Restore(this)
        }
    }
}

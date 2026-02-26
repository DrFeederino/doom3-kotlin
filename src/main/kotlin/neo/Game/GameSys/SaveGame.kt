package neo.Game.GameSys

import neo.Game.Animation.Anim_Blend.idDeclModelDef
import neo.Game.Entity.idEntity
import neo.Game.Game.refSound_t
import neo.Game.GameSys.Class.idClass
import neo.Game.GameSys.Class.idTypeInfo
import neo.Game.Game_local
import neo.Game.Game_local.idGameLocal
import neo.Game.Physics.Clip.idClipModel
import neo.Renderer.Material
import neo.Renderer.Model.idRenderModel
import neo.Renderer.ModelManager
import neo.Renderer.RenderWorld
import neo.Renderer.RenderWorld.renderEntity_s
import neo.Renderer.RenderWorld.renderLight_s
import neo.Renderer.RenderWorld.renderView_s
import neo.Sound.snd_shader.idSoundShader
import neo.TempDump.SERiAL
import neo.TempDump.TODO_Exception
import neo.cm.contactInfo_t
import neo.cm.contactType_t
import neo.cm.trace_s
import neo.framework.BuildVersion
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
        //
        private val objects: idList<idClass?>

        // ~idSaveGame();
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

        fun AddObject(obj: idClass) {
            objects.AddUnique(obj)
        }

        fun WriteObjectList() {
            var i: Int
            WriteInt(objects.Num() - 1)
            i = 1
            while (i < objects.Num()) {
                WriteString(objects[i]!!.GetClassname())
                i++
            }
        }

        fun Write(buffer: ByteBuffer, len: Int) {
            file.Write(buffer, len)
        }

        fun Write(buffer: SERiAL) {
            file.Write(buffer)
        }

        fun WriteInt(value: Int) {
            file.WriteInt(value)
        }

        fun WriteJoint(   /*jointHandle_t*/value: Int) {
            file.WriteInt(value)
        }

        fun WriteShort(value: Short) {
            file.WriteShort(value)
        }

        fun WriteByte(value: Byte) {
            val buffer = ByteBuffer.allocate(1)
            buffer.put(value)
            file.Write(buffer, 1) //sizeof(value));
        }

        fun WriteSignedChar(   /*signed char*/value: Short) {
            val buffer = ByteBuffer.allocate(java.lang.Short.BYTES)
            buffer.putShort(value)
            file.Write(buffer, java.lang.Short.BYTES) //sizeof(value));
        }

        fun WriteFloat(value: Float) {
            file.WriteFloat(value)
        }

        fun WriteBool(value: Boolean) {
            file.WriteBool(value)
        }

        fun WriteString(string: String) {
            val len: Int
            len = string.length
            WriteInt(len)
            file.Write(StandardCharsets.UTF_8.encode(string), len)
        }

        fun WriteString(string: idStr) {
            this.WriteString(string.toString())
        }

        fun WriteVec2(vec: idVec2) {
            file.WriteVec2(vec)
        }

        fun WriteVec3(vec: idVec3) {
            file.WriteVec3(vec)
        }

        fun WriteVec4(vec: idVec4) {
            file.WriteVec4(vec)
        }

        fun WriteVec6(vec: idVec6) {
            file.WriteVec6(vec)
        }

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

        fun WriteBounds(bounds: idBounds) {
            LittleRevBytes(bounds /*, sizeof(float), sizeof(b) / sizeof(float)*/)
            file.Write(bounds /*, sizeof(b)*/)
        }

        fun WriteMat3(mat: idMat3) {
            file.WriteMat3(mat)
        }

        fun WriteAngles(angles: idAngles) {
            LittleRevBytes(angles /*, sizeof(float), sizeof(v) / sizeof(float)*/)
            file.Write(angles /*, sizeof(v)*/)
        }

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

        fun WriteStaticObject(obj: idClass) {
            CallSave_r(obj.GetType(), obj)
        }

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

        fun WriteMaterial(material: Material.idMaterial?) {
            if (null == material) {
                WriteString("")
            } else {
                WriteString(material.GetName())
            }
        }

        fun WriteSkin(skin: idDeclSkin?) {
            if (null == skin) {
                WriteString("")
            } else {
                WriteString(skin.GetName())
            }
        }

        fun WriteParticle(particle: idDeclParticle?) {
            if (null == particle) {
                WriteString("")
            } else {
                WriteString(particle.GetName())
            }
        }

        fun WriteFX(fx: idDeclFX?) {
            if (null == fx) {
                WriteString("")
            } else {
                WriteString(fx.GetName())
            }
        }

        fun WriteSoundShader(shader: idSoundShader?) {
            val name: String?
            if (null == shader) {
                WriteString("")
            } else {
                name = shader.GetName()
                WriteString(name)
            }
        }

        fun WriteModelDef(modelDef: idDeclModelDef?) {
            if (null == modelDef) {
                WriteString("")
            } else {
                WriteString(modelDef.GetName())
            }
        }

        fun WriteModel(model: idRenderModel?) {
            val name: String?
            if (null == model) {
                WriteString("")
            } else {
                name = model.Name()
                WriteString(name)
            }
        }

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
                WriteUserInterface(
                    renderEntity.gui[i]!!,
                    renderEntity.gui[i] != null && renderEntity.gui[i]!!.IsUniqued()
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

        fun WriteRenderLight(renderLight: renderLight_s) {
            var i: Int
            WriteMat3(renderLight.axis)
            WriteVec3(renderLight.origin)
            WriteInt(renderLight.suppressLightInViewID._val)
            WriteInt(renderLight.allowLightInViewID._val)
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
            WriteInt(renderLight.lightId._val)
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

        fun WriteTrace(trace: trace_s) {
            WriteFloat(trace.fraction)
            WriteVec3(trace.endpos)
            WriteMat3(trace.endAxis)
            WriteContactInfo(trace.c)
        }

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
//            char[] tmp = new char[3];
            val tmp = ByteBuffer.allocate(6)
            //	memset( tmp, 0, sizeof( tmp ) );
            file.Write(tmp, 3)
        }

        fun WriteClipModel(clipModel: idClipModel?) {
            if (clipModel != null) {
                WriteBool(true)
                clipModel.Save(this)
            } else {
                WriteBool(false)
            }
        }

        fun WriteSoundCommands() {
            Game_local.gameSoundWorld!!.WriteToSaveGame(file)
        }

        fun WriteBuildNumber(value: Int) {
            file.WriteInt(BuildVersion.BUILD_NUMBER)
        }

        private fun CallSave_r(cls: idTypeInfo, obj: idClass?) {
            if (cls.zuper != null) {
                CallSave_r(cls.zuper!!, obj)
                if (cls.zuper!!.Save == cls.Save) {
                    // don't call save on this inheritance level since the function was called in the super class
                    return
                }
            }
            //            (obj.cls.Save) (this);
            cls.Save.run(this)
        }

        private fun CallSave_r(   /*idTypeInfo*/cls: java.lang.Class<out idClass?>?, obj: idClass?) {
            TODO()
        }

        //
        //
        init {

            // Put NULL at the start of the list so we can skip over it.
            objects = idList()
            objects.Append(null as idClass?)
        }
    }

    /* **********************************************************************

     idRestoreGame
	
     ***********************************************************************/
    class idRestoreGame(  //
        private val file: idFile
    ) {
        private var buildNumber = 0
        private var internalSavegameVersion = 0 // DG added this


        //
        private val objects: idList<idClass> = idList()

        // DG: added these methods, internalSavegameVersion makes us independent of the global BUILD_NUMBER
        fun ReadInternalSavegameVersion() {
            val readVersion = CInt()
            ReadInt(readVersion)
            internalSavegameVersion = readVersion._val
        }

        // if it's 0, this is from a GetBuildNumber() < 1305 savegame
        // otherwise, compare it to idGameLocal::INTERNAL_SAVEGAME_VERSION
        fun GetInternalSavegameVersion(): Int {
            return internalSavegameVersion
        }
        // DG end

        fun CreateObjects() {
            var i: Int
            val num = CInt()
            val className = idStr()
            var type: idTypeInfo?
            ReadInt(num)

            // create all the objects
            objects.SetNum(num._val + 1)
            //            memset(objects.Ptr(), 0, sizeof(objects[ 0]) * objects.Num());
            i = 1
            while (i < objects.Num()) {
                ReadString(className)
                type = idClass.GetClass(className.toString())
                if (null == type) {
                    Error("idRestoreGame::CreateObjects: Unknown class '%s'", className.toString())
                }
                objects[i] = type!!.CreateInstance.run() as idClass
                i++
            }
        }

        fun RestoreObjects() {
            var i: Int
            ReadSoundCommands()

            // read trace models
            idClipModel.RestoreTraceModels(this)

            // restore all the objects
            i = 1
            while (i < objects.Num()) {
                CallRestore_r(objects[i].GetType(), objects[i])
                i++
            }

            // regenerate render entities and render lights because are not saved
            i = 1
            while (i < objects.Num()) {
                if (objects[i] is idEntity) {
                    val ent = objects[i] as idEntity
                    ent.UpdateVisuals()
                    ent.Present()
                }
                i++
            }

// #ifdef ID_DEBUG_MEMORY
            // idStr gameState = file.GetName();
            // gameState.StripFileExtension();
            // WriteGameState_f( idCmdArgs( va( "test %s_restore", gameState.c_str() ), false ) );
            // //CompareGameState_f( idCmdArgs( va( "test %s_save", gameState.c_str() ) ) );
            // gameLocal.Error( "dumped game states" );
// #endif
        }

        fun DeleteObjects() {

            // Remove the NULL object before deleting
            objects.RemoveIndex(0)
            objects.DeleteContents(true)
        }

        fun Error(fmt: String, vararg objects: Any?) { // id_attribute((format(printf,2,3)));
            this.objects.DeleteContents(true)
            idGameLocal.Error(fmt, objects)
        }

        fun Read(buffer: ByteBuffer, len: Int) {
            file.Read(buffer, len)
        }

        fun Read(buffer: SERiAL) {
            file.Read(buffer)
        }

        fun ReadInt(value: CInt) {
            file.ReadInt(value)
        }

        fun ReadInt(): Int {
            val value = CInt()
            this.ReadInt(value)
            return value._val
        }

        fun ReadJoint(jointHandle_t: CInt) {
            file.ReadInt(jointHandle_t)
        }

        fun ReadJoint(): Int {
            val jointHandle_t = CInt()
            this.ReadJoint(jointHandle_t)
            return jointHandle_t._val
        }

        fun ReadShort(value: ShortArray) {
            file.ReadShort(value)
        }

        fun ReadShort(): Short {
            val value = shortArrayOf(0)
            this.ReadShort(value)
            return value[0]
        }

        fun ReadByte(value: ByteArray?) {
            file.Read(ByteBuffer.wrap(value) /*, sizeof(value)*/)
        }

        fun ReadByte(): Byte {
            val value = byteArrayOf(0)
            this.ReadByte(value)
            return value[0]
        }

        fun ReadSignedChar(value: CharArray) {
            file.ReadUnsignedChar(value /*, sizeof(value)*/)
        }

        fun ReadSignedChar(): Char {
            val c = CharArray(1)
            ReadSignedChar(c)
            return c[0]
        }

        fun ReadFloat(value: CFloat) {
            file.ReadFloat(value)
        }

        fun ReadFloat(): Float {
            val value = CFloat()
            this.ReadFloat(value)
            return value._val
        }

        fun ReadBool(value: CBool) {
            file.ReadBool(value)
        }

        fun ReadBool(): Boolean {
            val value = CBool(false)
            this.ReadBool(value)
            return value._val
        }

        fun ReadString(string: idStr) {
            val len = CInt()
            ReadInt(len)
            if (len._val < 0) {
                Error("idRestoreGame::ReadString: invalid length")
            }
            string.Fill(' ', len._val)
            file.Read(string, len._val)
        }

        fun ReadVec2(vec: idVec2) {
            file.ReadVec2(vec)
        }

        fun ReadVec3(vec: idVec3) {
            file.ReadVec3(vec)
        }

        fun ReadVec4(vec: idVec4) {
            file.ReadVec4(vec)
        }

        fun ReadVec6(vec: idVec6) {
            file.ReadVec6(vec)
        }

        fun ReadWinding(w: idWinding) {
            var i: Int
            val num = CInt()
            file.ReadInt(num)
            w.SetNumPoints(num._val)
            i = 0
            while (i < num._val) {
                file.Read(w.get(i) /*, sizeof(idVec5)*/)
                LittleRevBytes(w.get(i) /*, sizeof(float), sizeof(idVec5) / sizeof(float)*/)
                i++
            }
        }

        fun ReadBounds(bounds: idBounds) {
            file.Read(bounds /*, sizeof(bounds)*/)
            //            LittleRevBytes(bounds, sizeof(float), sizeof(bounds) / sizeof(float));
            LittleRevBytes(bounds /*, sizeof(float), sizeof(bounds) / sizeof(float)*/)
        }

        fun ReadMat3(mat: idMat3) {
            file.ReadMat3(mat)
        }

        fun ReadAngles(angles: idAngles) {
            file.Read(angles /*, sizeof(angles)*/)
            LittleRevBytes(angles /*, sizeof(float), sizeof(idAngles) / sizeof(float)*/)
        }

        fun ReadObject(obj: idClass?) {
            throw TODO_Exception() //TODO:remove the parameter, and return obj instead
            //            int[] index = {0};
//
//            ReadInt(index);
//            if ((index[0] < 0) || (index[0] >= objects.Num())) {
//                Error("idRestoreGame::ReadObject: invalid object index");
//            }
//            obj.oSet(objects.oGet(index[0]));
        }

        fun ReadStaticObject(obj: idClass?) {
            CallRestore_r(obj!!.GetType(), obj)
        }

        fun ReadDict(dict: idDict) {
            val num = CInt()
            var i: Int
            val key = idStr()
            val value = idStr()
            ReadInt(num)
            if (num._val < 0) {
                //dict.set(null)
            } else {
                dict.Clear()
                i = 0
                while (i < num._val) {
                    ReadString(key)
                    ReadString(value)
                    dict.Set(key, value)
                    i++
                }
            }
        }

        fun ReadMaterial(material: Material.idMaterial) {
            val name = idStr()
            ReadString(name)
            if (0 == name.Length()) {
                material.oSet(null)
            } else {
                material.oSet(DeclManager.declManager.FindMaterial(name))
            }
        }

        fun ReadSkin(skin: idDeclSkin) {
            val name = idStr()
            ReadString(name)
            if (0 == name.Length()) {
                //need to find out why setting null if = is overloaded
                //skin.oSet(null)
            } else {
                DeclManager.declManager.FindSkin(name)
            }
        }

        fun ReadParticle(particle: idDeclParticle) {
            val name = idStr()
            ReadString(name)
            if (0 == name.Length()) {
                //particle.oSet(null)
            } else {
                particle.oSet(DeclManager.declManager.FindType(declType_t.DECL_PARTICLE, name) as idDeclParticle)
            }
        }

        fun ReadFX(fx: idDeclFX) {
            val name = idStr()
            ReadString(name)
            if (0 == name.Length()) {
                fx.oSet(null)
            } else {
                fx.oSet(DeclManager.declManager.FindType(declType_t.DECL_FX, name) as idDeclFX)
            }
        }

        fun ReadSoundShader(shader: idSoundShader) {
            val name = idStr()
            ReadString(name)
            if (0 == name.Length()) {
                //shader.oSet(null)
            } else {
                shader.oSet(DeclManager.declManager.FindSound(name)!!)
            }
        }

        fun ReadModelDef(modelDef: idDeclModelDef) {
            val name = idStr()
            ReadString(name)
            if (0 == name.Length()) {
                //modelDef = null;
            } else {
                //modelDef.set((idDeclModelDef) declManager.FindType(DECL_MODELDEF, name, false));
            }
        }

        fun ReadModel(model: idRenderModel) {
            val name = idStr()
            ReadString(name)
            if (0 == name.Length()) {
                //model.oSet(null)
            } else {
                model.oSet(ModelManager.renderModelManager.FindModel(name.toString())!!)
            }
        }

        fun ReadUserInterface(ui: idUserInterface) {
            val name = idStr()
            ReadString(name)
            if (0 == name.Length()) {
                //ui.oSet(null)
            } else {
                val unique = CBool(false)
                ReadBool(unique)
                ui.oSet(UserInterface.uiManager.FindGui(name.toString(), true, unique._val)!!)
                if (ui != null) {
                    if (ui.ReadFromSaveGame(file) == false) {
                        Error("idSaveGame::ReadUserInterface: ui failed to read properly\n")
                    } else {
                        ui.StateChanged(Game_local.gameLocal.time)
                    }
                }
            }
        }

        fun ReadRenderEntity(renderEntity: renderEntity_s) {
            var i: Int
            val index = CInt()
            ReadModel(renderEntity.hModel!!)
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
            ReadMaterial(renderEntity.customShader!!)
            ReadMaterial(renderEntity.referenceShader!!)
            ReadSkin(renderEntity.customSkin!!)
            ReadInt(index)
            renderEntity.referenceSound = Game_local.gameSoundWorld!!.EmitterForIndex(index._val)
            i = 0
            while (i < Material.MAX_ENTITY_SHADER_PARMS) {
                renderEntity.shaderParms[i] = ReadFloat()
                i++
            }
            i = 0
            while (i < RenderWorld.MAX_RENDERENTITY_GUI) {
                ReadUserInterface(renderEntity.gui[i]!!)
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
        }

        fun ReadRenderLight(renderLight: renderLight_s) {
            val index = CInt()
            var i: Int
            ReadMat3(renderLight.axis)
            ReadVec3(renderLight.origin)
            renderLight.suppressLightInViewID._val = ReadInt()
            renderLight.allowLightInViewID._val = ReadInt()
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
            renderLight.lightId._val = ReadInt()
            ReadMaterial(renderLight.shader!!)
            i = 0
            while (i < Material.MAX_ENTITY_SHADER_PARMS) {
                renderLight.shaderParms[i] = ReadFloat()
                i++
            }
            ReadInt(index)
            renderLight.referenceSound = Game_local.gameSoundWorld!!.EmitterForIndex(index._val)
        }

        fun ReadRefSound(refSound: refSound_t) {
            val index = CInt()
            ReadInt(index)
            refSound.referenceSound = Game_local.gameSoundWorld!!.EmitterForIndex(index._val)
            ReadVec3(refSound.origin)
            refSound.listenerId = ReadInt()
            ReadSoundShader(refSound.shader!!)
            refSound.diversity = ReadFloat()
            refSound.waitfortrigger = ReadBool()
            refSound.parms.minDistance = ReadFloat()
            refSound.parms.maxDistance = ReadFloat()
            refSound.parms.volume = ReadFloat()
            refSound.parms.shakes = ReadFloat()
            refSound.parms.soundShaderFlags = ReadInt()
            refSound.parms.soundClass = ReadInt()
        }

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

        fun ReadContactInfo(contactInfo: contactInfo_t) {
            contactInfo.type = contactType_t.entries.toTypedArray()[ReadInt()]
            ReadVec3(contactInfo.point)
            ReadVec3(contactInfo.normal)
            contactInfo.dist = ReadFloat()
            contactInfo.contents = ReadInt()
            ReadMaterial(contactInfo.material!!)
            contactInfo.modelFeature = ReadInt()
            contactInfo.trmFeature = ReadInt()
            contactInfo.entityNum = ReadInt()
            contactInfo.id = ReadInt()
        }

        fun ReadTrace(trace: trace_s) {
            trace.fraction = ReadFloat()
            ReadVec3(trace.endpos)
            ReadMat3(trace.endAxis)
            ReadContactInfo(trace.c)
        }

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
            val tmp = ByteBuffer.allocate(3 * 2)
            file.Read(tmp, 3)
        }

        fun ReadClipModel(clipModel: idClipModel?) {
            val restoreClipModel: Boolean
            restoreClipModel = ReadBool()
            if (restoreClipModel) {
//                clipModel.oSet(new idClipModel());
                clipModel?.Restore(this)
            } else {
                clipModel?.oSet(null) //TODO:
            }
        }

        fun ReadSoundCommands() {
            Game_local.gameSoundWorld!!.StopAllSounds()
            Game_local.gameSoundWorld!!.ReadFromSaveGame(file)
        }

        fun ReadBuildNumber() {
            val buildNumber = CInt()
            file.ReadInt(buildNumber)
            this.buildNumber = buildNumber._val
        }

        //						Used to retrieve the saved game buildNumber from within class Restore methods
        fun GetBuildNumber(): Int {
            return buildNumber
        }

        private fun CallRestore_r(cls: idTypeInfo, obj: idClass?) {
            if (cls.zuper != null) {
                CallRestore_r(cls.zuper!!, obj)
                if (cls.zuper!!.Restore === cls.Restore) {
                    // don't call save on this inheritance level since the function was called in the super class
                    return
                }
            }
            //            (obj.cls.Restore) (this);
            cls.Restore.run(this)
        }

        private fun CallRestore_r(cls: java.lang.Class<out idClass>, obj: idClass?) {
            TODO()
        }
    }
}
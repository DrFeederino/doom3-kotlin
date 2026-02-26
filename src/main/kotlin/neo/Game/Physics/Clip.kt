package neo.Game.Physics

import neo.Game.Entity.idEntity
import neo.Game.GameSys.Class
import neo.Game.GameSys.SaveGame.idRestoreGame
import neo.Game.GameSys.SaveGame.idSaveGame
import neo.Game.Game_local
import neo.Game.Game_local.idGameLocal
import neo.Renderer.Material
import neo.Renderer.Model
import neo.Renderer.RenderWorld.modelTrace_s
import neo.cm.*
import neo.idlib.BV.idBounds
import neo.idlib.Max
import neo.idlib.Text.Str.idStr
import neo.idlib.colorCyan
import neo.idlib.colorWhite
import neo.idlib.containers.CBool
import neo.idlib.containers.CFloat
import neo.idlib.containers.CInt
import neo.idlib.containers.List.idList
import neo.idlib.containers.idHashIndex
import neo.idlib.geometry.TraceModel.idTraceModel
import neo.idlib.geometry.Winding.idFixedWinding
import neo.idlib.math.*
import neo.idlib.math.Matrix.idMat3
import java.util.*

object Clip {
    const val MAX_SECTOR_DEPTH = 12
    const val MAX_SECTORS = (1 shl MAX_SECTOR_DEPTH + 1) - 1
    val vec3_boxEpsilon: idVec3 =
        idVec3(CM_BOX_EPSILON, CM_BOX_EPSILON, CM_BOX_EPSILON)

    //    public static final idBlockAlloc<clipLink_s> clipLinkAllocator = new idBlockAlloc<>(1024);
    /*
     ===============================================================

     idClipModel trace model cache

     ===============================================================
     */
    val traceModelCache: idList<trmCache_s> = idList()
    val traceModelHash: idHashIndex = idHashIndex()

    /*
     ===============================================================================

     Handles collision detection with the world and between physics objects.

     ===============================================================================
     */
    fun CLIPMODEL_ID_TO_JOINT_HANDLE(id: Int): Int {
        return if (id >= 0) Model.INVALID_JOINT else -1 - id
    }

    fun JOINT_HANDLE_TO_CLIPMODEL_ID(id: Int): Int {
        return -1 - id
    }

    /*
     ============
     idClip::TestHugeTranslation
     ============
     */
    fun TestHugeTranslation(
        results: trace_s,
        mdl: idClipModel?,
        start: idVec3,
        end: idVec3,
        trmAxis: idMat3
    ): Boolean {
        if (mdl != null && end.minus(start).LengthSqr() > Square(CM_MAX_TRACE_DIST)) {
            // assert (false);
            results.fraction = 0.0f
            results.endpos.set(start)
            results.endAxis.set(trmAxis)
            results.c = contactInfo_t() //memset( results.c, 0, sizeof( results.c ) );
            results.c.point.set(start)
            results.c.entityNum = Game_local.ENTITYNUM_WORLD
            if (mdl.GetEntity() != null) {
                Game_local.gameLocal.Printf(
                    "huge translation for clip model %d on entity %d '%s'\n",
                    mdl.GetId(),
                    mdl.GetEntity()!!.entityNumber,
                    mdl.GetEntity()!!.GetName()
                )
            } else {
                Game_local.gameLocal.Printf("huge translation for clip model %d\n", mdl.GetId())
            }
            return true
        }
        return false
    }

    class clipSector_s {
        var axis // -1 = leaf node
                = 0
        var children: Array<clipSector_s?> = arrayOfNulls(2)
        var clipLinks: clipLink_s? = null
        var dist = 0.0f //        private void oSet(clipSector_s clip) {
        //            this.axis = clip.axis;
        //            this.dist = clip.dist;
        //            this.children = clip.children;
        //            this.clipLinks = clip.clipLinks;
        //        }
    }

    class clipLink_s {
        lateinit var clipModel: idClipModel
        var nextInSector: clipLink_s? = null
        var nextLink: clipLink_s? = null
        var prevInSector: clipLink_s? = null
        lateinit var sector: clipSector_s
    }

    class trmCache_s {
        val centerOfMass: idVec3 = idVec3()
        val inertiaTensor: idMat3 = idMat3()
        var refCount = 0
        var trm: idTraceModel = idTraceModel()
        var volume = 0.0f
    }

    class idClipModel {
        val absBounds: idBounds = idBounds() // absolute bounds
        val axis: idMat3 = idMat3() // orientation of clip model
        private val bounds: idBounds = idBounds() // bounds
        val origin: idVec3 = idVec3() // origin of clip model
        private var clipLinks // links into sectors
                : clipLink_s? = null
        var   /*cmHandle_t*/collisionModelHandle // handle to collision model
                = 0
        var contents // all contents ored together
                = 0
        var enabled // true if this clip model is used for clipping
                = false
        var entity // entity using this clip model
                : idEntity? = null
        var id // id for entities that use multiple clip models
                = 0
        var material // material for trace models
                : Material.idMaterial? = null
        var owner // owner of the entity that owns this clip model
                : idEntity? = null
        var renderModelHandle // render model def handle
                = 0
        var touchCount = 0
        var traceModelIndex // trace model used for collision detection
                = 0

        // friend class idClip;
        constructor() {
            Init()
        }

        constructor(name: String) {
            Init()
            LoadModel(name)
        }

        constructor(trm: idTraceModel) {
            Init()
            LoadModel(trm)
        }

        // ~idClipModel( void );
        constructor(renderModelHandle: Int) {
            Init()
            contents = Material.CONTENTS_RENDERMODEL
            LoadModel(renderModelHandle)
        }

        constructor(model: idClipModel) {
            enabled = model.enabled
            entity = model.entity
            id = model.id
            owner = model.owner
            origin.set(model.origin)
            axis.set(model.axis)
            bounds.set(model.bounds)
            absBounds.set(model.absBounds)
            material = model.material
            contents = model.contents
            collisionModelHandle = model.collisionModelHandle
            traceModelIndex = -1
            if (model.traceModelIndex != -1) {
                LoadModel(GetCachedTraceModel(model.traceModelIndex))
            }
            renderModelHandle = model.renderModelHandle
            clipLinks = null
            touchCount = -1
        }

        fun LoadModel(name: String): Boolean {
            renderModelHandle = -1
            if (traceModelIndex != -1) {
                FreeTraceModel(traceModelIndex)
                traceModelIndex = -1
            }
            collisionModelHandle = collisionModelManager.LoadModel(name, false)
            return if (collisionModelHandle != 0) {
                collisionModelManager.GetModelBounds(collisionModelHandle, bounds)
                run {
                    val contents = CInt()
                    collisionModelManager.GetModelContents(collisionModelHandle, contents)
                    this.contents = contents._val
                }
                true
            } else {
                bounds.Zero()
                false
            }
        }

        fun LoadModel(trm: idTraceModel) {
            collisionModelHandle = 0
            renderModelHandle = -1
            if (traceModelIndex != -1) {
                FreeTraceModel(traceModelIndex)
            }
            traceModelIndex = AllocTraceModel(trm)
            bounds.set(trm.bounds)
        }

        fun LoadModel(renderModelHandle: Int) {
            collisionModelHandle = 0
            this.renderModelHandle = renderModelHandle
            if (renderModelHandle != -1) {
                val renderEntity = Game_local.gameRenderWorld!!.GetRenderEntity(renderModelHandle)
                if (renderEntity != null) {
                    bounds.set(renderEntity.bounds)
                }
            }
            if (traceModelIndex != -1) {
                FreeTraceModel(traceModelIndex)
                traceModelIndex = -1
            }
        }

        fun Save(savefile: idSaveGame) {
            savefile.WriteBool(enabled)
            savefile.WriteObject(entity as Class.idClass?)
            savefile.WriteInt(id)
            savefile.WriteObject(owner as Class.idClass?)
            savefile.WriteVec3(origin)
            savefile.WriteMat3(axis)
            savefile.WriteBounds(bounds)
            savefile.WriteBounds(absBounds)
            savefile.WriteMaterial(material)
            savefile.WriteInt(contents)
            if (collisionModelHandle >= 0) {
                savefile.WriteString(collisionModelManager.GetModelName(collisionModelHandle))
            } else {
                savefile.WriteString("")
            }
            savefile.WriteInt(traceModelIndex)
            savefile.WriteInt(renderModelHandle)
            savefile.WriteBool(clipLinks != null)
            savefile.WriteInt(touchCount)
        }

        fun Restore(savefile: idRestoreGame) {
            val collisionModelName = idStr()
            val linked = CBool(false)
            enabled = savefile.ReadBool()
            savefile.ReadObject( /*reinterpret_cast<idClass *&>*/entity)
            id = savefile.ReadInt()
            savefile.ReadObject( /*reinterpret_cast<idClass *&>*/owner)
            savefile.ReadVec3(origin)
            savefile.ReadMat3(axis)
            savefile.ReadBounds(bounds)
            savefile.ReadBounds(absBounds)
            savefile.ReadMaterial(material!!)
            contents = savefile.ReadInt()
            savefile.ReadString(collisionModelName)
            collisionModelHandle = if (collisionModelName.Length() != 0) {
                collisionModelManager.LoadModel(collisionModelName.toString(), false)
            } else {
                -1
            }
            traceModelIndex = savefile.ReadInt()
            if (traceModelIndex >= 0) {
                traceModelCache[traceModelIndex].refCount++
            }
            renderModelHandle = savefile.ReadInt()
            savefile.ReadBool(linked)
            touchCount = savefile.ReadInt()

            // the render model will be set when the clip model is linked
            renderModelHandle = -1
            clipLinks = null
            touchCount = -1
            if (linked._val) {
                Link(Game_local.gameLocal.clip, entity, id, origin, axis, renderModelHandle)
            }
        }

        fun Link(clp: idClip) {                // must have been linked with an entity and id before
            assert(entity != null)
            if (null == entity) {
                return
            }
            if (clipLinks != null) {
                Unlink() // unlink from old position
            }
            if (bounds.IsCleared()) {
                return
            }

            // set the abs box
            if (axis.IsRotated()) {
                // expand for rotation
                absBounds.FromTransformedBounds(bounds, origin, axis)
            } else {
                // normal
                absBounds[0] = bounds[0] + origin
                absBounds[1] = bounds[1] + origin
            }

            // because movement is clipped an epsilon away from an actual edge,
            // we must fully check even when bounding boxes don't quite touch
            absBounds.minusAssign(0, vec3_boxEpsilon)
            absBounds.plusAssign(1, vec3_boxEpsilon)
            Link_r(clp.clipSectors!![0]) //TODO:check if [0] is good enough. upd: seems it is
        }


        fun Link(
            clp: idClip,
            ent: idEntity?,
            newId: Int,
            newOrigin: idVec3,
            newAxis: idMat3,
            renderModelHandle: Int = -1 /*= -1*/
        ) {
            entity = ent
            id = newId
            origin.set(newOrigin)
            axis.set(newAxis)
            if (renderModelHandle != -1) {
                this.renderModelHandle = renderModelHandle
                val renderEntity = Game_local.gameRenderWorld!!.GetRenderEntity(renderModelHandle)
                if (renderEntity != null) {
                    bounds.set(renderEntity.bounds)
                }
            }
            this.Link(clp)
        }

        fun Unlink() {                        // unlink from sectors
            var link: clipLink_s?
            link = clipLinks
            while (link != null) {
                clipLinks = link.nextLink
                if (link.prevInSector != null) {
                    link.prevInSector!!.nextInSector = link.nextInSector
                } else {
                    link.sector.clipLinks = link.nextInSector
                }
                if (link.nextInSector != null) {
                    link.nextInSector!!.prevInSector = link.prevInSector
                }
                link = clipLinks
            }
        }

        fun SetPosition(newOrigin: idVec3, newAxis: idMat3) {    // unlinks the clip model
            if (clipLinks != null) {
                Unlink() // unlink from old position
            }
            origin.set(newOrigin)
            axis.set(newAxis)
        }

        fun Translate(translation: idVec3) {                            // unlinks the clip model
            Unlink()
            origin.plusAssign(translation)
        }

        fun Rotate(rotation: idRotation) {                            // unlinks the clip model
            Unlink()
            origin.timesAssign(rotation)
            axis.timesAssign(rotation.ToMat3())
        }

        fun Enable() {                        // enable for clipping
            enabled = true
        }

        fun Disable() {                    // keep linked but disable for clipping
            enabled = false
        }

        fun SetMaterial(m: Material.idMaterial) {
            material = m
        }

        fun GetMaterial(): Material.idMaterial? {
            return material
        }

        fun SetContents(newContents: Int) {        // override contents
            contents = newContents
        }

        fun GetContents(): Int {
            return contents
        }

        fun SetEntity(newEntity: idEntity?) {
            entity = newEntity
        }

        fun GetEntity(): idEntity? {
            return entity
        }

        fun SetId(newId: Int) {
            id = newId
        }

        fun GetId(): Int {
            return id
        }

        fun SetOwner(newOwner: idEntity?) {
            owner = newOwner
        }

        fun GetOwner(): idEntity? {
            return owner
        }

        fun GetBounds(): idBounds {
            return bounds
        }

        fun GetAbsBounds(): idBounds {
            return absBounds
        }

        fun GetOrigin(): idVec3 {
            return origin
        }

        fun GetAxis(): idMat3 {
            return axis
        }

        fun IsTraceModel(): Boolean {            // returns true if this is a trace model
            return traceModelIndex != -1
        }

        fun IsRenderModel(): Boolean {        // returns true if this is a render model
            return renderModelHandle != -1
        }

        fun IsLinked(): Boolean {                // returns true if the clip model is linked
            return clipLinks != null
        }

        fun IsEnabled(): Boolean {            // returns true if enabled for collision detection
            return enabled
        }

        fun IsEqual(trm: idTraceModel): Boolean {
            return traceModelIndex != -1 && GetCachedTraceModel(traceModelIndex) === trm
        }

        fun  /*cmHandle_t*/Handle(): Int {                // returns handle used to collide vs this model
            assert(renderModelHandle == -1)
            return if (collisionModelHandle != 0) {
                collisionModelHandle
            } else if (traceModelIndex != -1) {
                collisionModelManager.SetupTrmModel(
                    GetCachedTraceModel(traceModelIndex),
                    arrayOf(material)
                )
            } else {
                // this happens in multiplayer on the combat models
                Game_local.gameLocal.Warning(
                    "idClipModel::Handle: clip model %d on '%s' (%x) is not a collision or trace model",
                    id,
                    entity!!.name,
                    entity!!.entityNumber
                )
                0
            }
        }

        fun GetTraceModel(): idTraceModel? {
            return if (!IsTraceModel()) {
                null
            } else GetCachedTraceModel(traceModelIndex)
        }

        fun GetMassProperties(density: Float, mass: CFloat, centerOfMass: idVec3, inertiaTensor: idMat3) {
            if (traceModelIndex == -1) {
                idGameLocal.Error(
                    "idClipModel::GetMassProperties: clip model %d on '%s' is not a trace model\n",
                    id,
                    entity!!.name
                )
            }
            val entry: trmCache_s = traceModelCache[traceModelIndex]
            mass._val = entry.volume * density
            centerOfMass.set(entry.centerOfMass)
            inertiaTensor.set(entry.inertiaTensor * density)
        }

        // initialize(or does it?)
        private fun Init() {
            enabled = true
            entity = null
            id = 0
            owner = null
            origin.Zero()
            axis.Identity()
            bounds.Zero()
            absBounds.Zero()
            material = null
            contents = Material.CONTENTS_BODY
            collisionModelHandle = 0
            renderModelHandle = -1
            traceModelIndex = -1
            clipLinks = null
            touchCount = -1
        }

        private fun Link_r(node: clipSector_s) {
            var node = node
            val link: clipLink_s
            while (node.axis != -1) {
                node = if (absBounds[0, node.axis] > node.dist) {
                    node.children[0]!!
                } else if (absBounds[1, node.axis] < node.dist) {
                    node.children[1]!!
                } else {
                    Link_r(node.children[0]!!)
                    node.children[1]!!
                }
            }
            link = clipLink_s() //clipLinkAllocator.Alloc();
            link.clipModel = this
            link.sector = node
            link.nextInSector = node.clipLinks
            link.prevInSector = null
            if (node.clipLinks != null) {
                node.clipLinks!!.prevInSector = link
            }
            node.clipLinks = link
            link.nextLink = clipLinks
            clipLinks = link
        }

        fun oSet(idClipModel: idClipModel?) {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        protected fun _deconstructor() {
            // make sure the clip model is no longer linked
            Unlink()
            if (traceModelIndex != -1) {
                FreeTraceModel(traceModelIndex)
            }
        }

        companion object {
            fun  /*cmHandle_t*/CheckModel(name: String): Int {
                return collisionModelManager.LoadModel(name, false)
            }

            fun  /*cmHandle_t*/CheckModel(name: idStr): Int {
                return CheckModel(name.toString())
            }

            fun ClearTraceModelCache() {
                traceModelCache.DeleteContents(true)
                traceModelHash.Free()
            }

            fun SaveTraceModels(savefile: idSaveGame) {
                var i: Int
                savefile.WriteInt(traceModelCache.Num())
                i = 0
                while (i < traceModelCache.Num()) {
                    val entry: trmCache_s = traceModelCache[i]
                    savefile.WriteTraceModel(entry.trm)
                    savefile.WriteFloat(entry.volume)
                    savefile.WriteVec3(entry.centerOfMass)
                    savefile.WriteMat3(entry.inertiaTensor)
                    i++
                }
            }

            fun RestoreTraceModels(savefile: idRestoreGame) {
                var i: Int
                val num = CInt()
                ClearTraceModelCache()
                savefile.ReadInt(num)
                traceModelCache.SetNum(num._val)
                i = 0
                while (i < num._val) {
                    val entry = trmCache_s()
                    savefile.ReadTraceModel(entry.trm)
                    entry.volume = savefile.ReadFloat()
                    savefile.ReadVec3(entry.centerOfMass)
                    savefile.ReadMat3(entry.inertiaTensor)
                    entry.refCount = 0
                    traceModelCache[i] = entry
                    traceModelHash.Add(GetTraceModelHashKey(entry.trm), i)
                    i++
                }
            }

            private fun AllocTraceModel(trm: idTraceModel): Int {
                var i: Int
                val hashKey: Int
                val traceModelIndex: Int
                val entry: trmCache_s
                hashKey = GetTraceModelHashKey(trm)
                i = traceModelHash.First(hashKey)
                while (i >= 0) {
                    if (traceModelCache[i].trm == trm) {
                        traceModelCache[i].refCount++
                        return i
                    }
                    i = traceModelHash.Next(i)
                }
                entry = trmCache_s()
                entry.trm = trm
                val volume = CFloat()
                entry.trm.GetMassProperties(1.0f, volume, entry.centerOfMass, entry.inertiaTensor)
                entry.volume = volume._val
                entry.refCount = 1
                traceModelIndex = traceModelCache.Append(entry)
                traceModelHash.Add(hashKey, traceModelIndex)
                return traceModelIndex
            }

            fun FreeTraceModel(traceModelIndex: Int) {
                if (traceModelIndex < 0 || traceModelIndex >= traceModelCache.Num() || traceModelCache[traceModelIndex].refCount <= 0
                ) {
                    Game_local.gameLocal.Warning("idClipModel::FreeTraceModel: tried to free uncached trace model")
                    return
                }
                traceModelCache[traceModelIndex].refCount--
            }

            fun GetCachedTraceModel(traceModelIndex: Int): idTraceModel {
                return traceModelCache[traceModelIndex].trm
            }

            private fun GetTraceModelHashKey(trm: idTraceModel): Int {
                val v = trm.bounds[0]
                return trm.type.ordinal shl 8 xor (trm.numVerts shl 4) xor (trm.numEdges shl 2) xor (trm.numPolys shl 0) xor idMath.FloatHash(
                    v.ToFloatPtr(),
                    v.GetDimension()
                )
            }

            fun delete(clipModel: idClipModel?) {
                clipModel?._deconstructor()
            }
        }
    }

    //===============================================================
    //
    //	idClip
    //
    //===============================================================
    class idClip {
        // friend class idClipModel;
        private val defaultClipModel: idClipModel = idClipModel()
        private val temporaryClipModel: idClipModel = idClipModel()
        private val worldBounds: idBounds = idBounds()
        var clipSectors: Array<clipSector_s>?
        private var numClipSectors = 0
        private var numContacts: Int
        private var numContents: Int
        private var numMotions: Int
        private var numRenderModelTraces: Int
        private var numRotations: Int

        // statistics
        private var numTranslations: Int
        private var touchCount = 0
        fun Init() {
            val   /*cmHandle_t*/h: Int
            val size = idVec3()
            val maxSector = getVec3Origin()

            // clear clip sectors
            clipSectors = Array(MAX_SECTORS) { clipSector_s() }
            //	memset( clipSectors, 0, MAX_SECTORS * sizeof( clipSector_t ) );
            numClipSectors = 0
            touchCount = -1
            // get world map bounds
            h = collisionModelManager.LoadModel("worldMap", false)
            collisionModelManager.GetModelBounds(h, worldBounds)
            // create world sectors
            CreateClipSectors_r(0, worldBounds, maxSector)
            size.set(worldBounds[1].minus(worldBounds[0]))
            Game_local.gameLocal.Printf(
                "map bounds are (%1.1f, %1.1f, %1.1f)\n",
                size[0],
                size[1],
                size[2]
            )
            Game_local.gameLocal.Printf(
                "max clip sector is (%1.1f, %1.1f, %1.1f)\n",
                maxSector[0],
                maxSector[1],
                maxSector[2]
            )

            // initialize a default clip model
            defaultClipModel.LoadModel(idTraceModel(idBounds(idVec3(0.0f, 0.0f, 0.0f)).Expand(8.0f)))

            // set counters to zero
            numContacts = 0
            numContents = numContacts
            numRenderModelTraces = numContents
            numMotions = numRenderModelTraces
            numTranslations = numMotions
            numRotations = numTranslations
        }

        fun Shutdown() {
//	delete[] clipSectors;
            clipSectors = Array(0) { clipSector_s() }

            // free the trace model used for the temporaryClipModel
            if (temporaryClipModel.traceModelIndex != -1) {
                idClipModel.FreeTraceModel(temporaryClipModel.traceModelIndex)
                temporaryClipModel.traceModelIndex = -1
            }

            // free the trace model used for the defaultClipModel
            if (defaultClipModel.traceModelIndex != -1) {
                idClipModel.FreeTraceModel(defaultClipModel.traceModelIndex)
                defaultClipModel.traceModelIndex = -1
            }
            //
//            clipLinkAllocator.Shutdown();
        }

        // clip versus the rest of the world
        fun Translation(
            results: trace_s, start: idVec3, end: idVec3,
            mdl: idClipModel?, trmAxis: idMat3, contentMask: Int, passEntity: idEntity?
        ): Boolean {
            var i: Int
            val num: Int
            var touch: idClipModel?
            val clipModelList = arrayOfNulls<idClipModel>(Game_local.MAX_GENTITIES)
            val traceBounds = idBounds()
            val radius: Float
            val trace = trace_s()
            val trm: idTraceModel?
            if (TestHugeTranslation(results, mdl, start, end, trmAxis)) {
                return true
            }
            trm = TraceModelForClipModel(mdl)
            if (null == passEntity || passEntity.entityNumber != Game_local.ENTITYNUM_WORLD) {
                // test world
                numTranslations++
                collisionModelManager.Translation(
                    results,
                    start,
                    end,
                    trm,
                    trmAxis,
                    contentMask,
                    0,
                    getVec3Origin(),
                    idMat3.getMat3_default()
                )
                results.c.entityNum =
                    if (results.fraction != 1.0f) Game_local.ENTITYNUM_WORLD else Game_local.ENTITYNUM_NONE
                if (results.fraction == 0.0f) {
                    return true // blocked immediately by the world
                }
            } else {
                results.fraction = 1.0f
                results.endpos.set(end)
                results.endAxis.set(trmAxis)
            }
            radius = if (null == trm) {
                traceBounds.FromPointTranslation(start, results.endpos.minus(start))
                0.0f
            } else {
                traceBounds.FromBoundsTranslation(trm.bounds, start, trmAxis, results.endpos.minus(start))
                trm.bounds.GetRadius()
            }
            num = GetTraceClipModels(traceBounds, contentMask, passEntity, clipModelList)
            i = 0
            while (i < num) {
                touch = clipModelList[i]
                if (null == touch) {
                    i++
                    continue
                }
                if (touch.renderModelHandle != -1) {
                    numRenderModelTraces++
                    TraceRenderModel(trace, start, end, radius, trmAxis, touch)
                } else {
                    numTranslations++
                    collisionModelManager.Translation(
                        trace,
                        start,
                        end,
                        trm,
                        trmAxis,
                        contentMask,
                        touch.Handle(),
                        touch.origin,
                        touch.axis
                    )
                }
                if (trace.fraction < results.fraction) {
                    results.set(trace)
                    results.c.entityNum = touch.entity!!.entityNumber
                    results.c.id = touch.id
                    if (results.fraction == 0.0f) {
                        break
                    }
                }
                i++
            }
            return results.fraction < 1.0f
        }

        fun Rotation(
            results: trace_s, start: idVec3, rotation: idRotation,
            mdl: idClipModel?, trmAxis: idMat3, contentMask: Int, passEntity: idEntity?
        ): Boolean {
            var i: Int
            val num: Int
            var touch: idClipModel?
            val clipModelList = arrayOfNulls<idClipModel>(Game_local.MAX_GENTITIES)
            val traceBounds = idBounds()
            val trace = trace_s()
            val trm: idTraceModel?
            trm = TraceModelForClipModel(mdl)
            if (null == passEntity || passEntity.entityNumber != Game_local.ENTITYNUM_WORLD) {
                // test world
                numRotations++
                collisionModelManager.Rotation(
                    results,
                    start,
                    rotation,
                    trm!!,
                    trmAxis,
                    contentMask,
                    0,
                    getVec3Origin(),
                    idMat3.getMat3_default()
                )
                results.c.entityNum =
                    if (results.fraction != 1.0f) Game_local.ENTITYNUM_WORLD else Game_local.ENTITYNUM_NONE
                if (results.fraction == 0.0f) {
                    return true // blocked immediately by the world
                }
            } else {
//		memset( &results, 0, sizeof( results ) );
                results.fraction = 1.0f
                results.endpos.set(start)
                results.endAxis.set(trmAxis.times(rotation.ToMat3()))
            }
            if (null == trm) {
                traceBounds.FromPointRotation(start, rotation)
            } else {
                traceBounds.FromBoundsRotation(trm.bounds, start, trmAxis, rotation)
            }
            num = GetTraceClipModels(traceBounds, contentMask, passEntity, clipModelList)
            i = 0
            while (i < num) {
                touch = clipModelList[i]
                if (null == touch) {
                    i++
                    continue
                }

                // no rotational collision with render models
                if (touch.renderModelHandle != -1) {
                    i++
                    continue
                }
                numRotations++
                collisionModelManager.Rotation(
                    trace,
                    start,
                    rotation,
                    trm!!,
                    trmAxis,
                    contentMask,
                    touch.Handle(),
                    touch.origin,
                    touch.axis
                )
                if (trace.fraction < results.fraction) {
                    results.set(trace)
                    results.c.entityNum = touch.entity!!.entityNumber
                    results.c.id = touch.id
                    if (results.fraction == 0.0f) {
                        break
                    }
                }
                i++
            }
            return results.fraction < 1.0f
        }

        fun Motion(
            results: trace_s, start: idVec3, end: idVec3, rotation: idRotation,
            mdl: idClipModel?, trmAxis: idMat3, contentMask: Int, passEntity: idEntity?
        ): Boolean {
            var i: Int
            var num: Int
            var touch: idClipModel?
            val clipModelList = arrayOfNulls<idClipModel>(Game_local.MAX_GENTITIES)
            val dir = idVec3()
            val endPosition = idVec3()
            val traceBounds = idBounds()
            val radius: Float
            var translationalTrace = trace_s()
            var rotationalTrace = trace_s()
            val trace = trace_s()
            val endRotation: idRotation
            val trm: idTraceModel?
            assert(rotation.GetOrigin() == start)
            if (TestHugeTranslation(results, mdl, start, end, trmAxis)) {
                return true
            }
            if (mdl != null && rotation.GetAngle() != 0.0f && rotation.GetVec() != getVec3Origin()) {
                // if no translation
                if (start == end) {
                    // pure rotation
                    return Rotation(results, start, rotation, mdl, trmAxis, contentMask, passEntity)
                }
            } else if (start != end) {
                // pure translation
                return Translation(results, start, end, mdl, trmAxis, contentMask, passEntity)
            } else {
                // no motion
                results.fraction = 1.0f
                results.endpos.set(start)
                results.endAxis.set(trmAxis)
                return false
            }
            trm = TraceModelForClipModel(mdl)!!
            radius = trm.bounds.GetRadius()
            if (null == passEntity || passEntity.entityNumber != Game_local.ENTITYNUM_WORLD) {
                // translational collision with world
                numTranslations++
                collisionModelManager.Translation(
                    translationalTrace,
                    start,
                    end,
                    trm,
                    trmAxis,
                    contentMask,
                    0,
                    getVec3Origin(),
                    idMat3.getMat3_default()
                )
                translationalTrace.c.entityNum =
                    if (translationalTrace.fraction != 1.0f) Game_local.ENTITYNUM_WORLD else Game_local.ENTITYNUM_NONE
            } else {
//		memset( &translationalTrace, 0, sizeof( translationalTrace ) );
                translationalTrace = trace_s()
                translationalTrace.fraction = 1.0f
                translationalTrace.endpos.set(end)
                translationalTrace.endAxis.set(trmAxis)
            }
            if (translationalTrace.fraction != 0.0f) {
                traceBounds.FromBoundsRotation(trm.bounds, start, trmAxis, rotation)
                dir.set(translationalTrace.endpos.minus(start))
                i = 0
                while (i < 3) {
                    if (dir[i] < 0.0f) {
                        traceBounds[0].plusAssign(i, dir[i])
                    } else {
                        traceBounds[1].plusAssign(i, dir[i])
                    }
                    i++
                }
                num = GetTraceClipModels(traceBounds, contentMask, passEntity, clipModelList)
                i = 0
                while (i < num) {
                    touch = clipModelList[i]
                    if (null == touch) {
                        i++
                        continue
                    }
                    if (touch.renderModelHandle != -1) {
                        numRenderModelTraces++
                        TraceRenderModel(trace, start, end, radius, trmAxis, touch)
                    } else {
                        numTranslations++
                        collisionModelManager.Translation(
                            trace,
                            start,
                            end,
                            trm,
                            trmAxis,
                            contentMask,
                            touch.Handle(),
                            touch.origin,
                            touch.axis
                        )
                    }
                    if (trace.fraction < translationalTrace.fraction) {
                        translationalTrace = trace
                        translationalTrace.c.entityNum = touch.entity!!.entityNumber
                        translationalTrace.c.id = touch.id
                        if (translationalTrace.fraction == 0.0f) {
                            break
                        }
                    }
                    i++
                }
            } else {
                num = -1
            }
            endPosition.set(translationalTrace.endpos)
            endRotation = rotation
            endRotation.SetOrigin(endPosition)
            if (null == passEntity || passEntity.entityNumber != Game_local.ENTITYNUM_WORLD) {
                // rotational collision with world
                numRotations++
                collisionModelManager.Rotation(
                    rotationalTrace,
                    endPosition,
                    endRotation,
                    trm,
                    trmAxis,
                    contentMask,
                    0,
                    getVec3Origin(),
                    idMat3.getMat3_default()
                )
                rotationalTrace.c.entityNum =
                    if (rotationalTrace.fraction != 1.0f) Game_local.ENTITYNUM_WORLD else Game_local.ENTITYNUM_NONE
            } else {
//		memset( &rotationalTrace, 0, sizeof( rotationalTrace ) );
                rotationalTrace = trace_s()
                rotationalTrace.fraction = 1.0f
                rotationalTrace.endpos.set(endPosition)
                rotationalTrace.endAxis.set(trmAxis.times(rotation.ToMat3()))
            }
            if (rotationalTrace.fraction != 0.0f) {
                if (num == -1) {
                    traceBounds.FromBoundsRotation(trm.bounds, endPosition, trmAxis, endRotation)
                    num = GetTraceClipModels(traceBounds, contentMask, passEntity, clipModelList)
                }
                i = 0
                while (i < num) {
                    touch = clipModelList[i]
                    if (null == touch) {
                        i++
                        continue
                    }

                    // no rotational collision detection with render models
                    if (touch.renderModelHandle != -1) {
                        i++
                        continue
                    }
                    numRotations++
                    collisionModelManager.Rotation(
                        trace,
                        endPosition,
                        endRotation,
                        trm,
                        trmAxis,
                        contentMask,
                        touch.Handle(),
                        touch.origin,
                        touch.axis
                    )
                    if (trace.fraction < rotationalTrace.fraction) {
                        rotationalTrace.set(trace)
                        rotationalTrace.c.entityNum = touch.entity!!.entityNumber
                        rotationalTrace.c.id = touch.id
                        if (rotationalTrace.fraction == 0.0f) {
                            break
                        }
                    }
                    i++
                }
            }
            if (rotationalTrace.fraction < 1.0f) {
                results.set(rotationalTrace)
            } else {
                results.set(translationalTrace)
                results.endAxis.set(rotationalTrace.endAxis)
            }
            results.fraction = Max(translationalTrace.fraction, rotationalTrace.fraction)
            return translationalTrace.fraction < 1.0f || rotationalTrace.fraction < 1.0f
        }

        fun Contacts(
            contacts: Array<contactInfo_t>, maxContacts: Int, start: idVec3, dir: idVec6, depth: Float,
            mdl: idClipModel?, trmAxis: idMat3, contentMask: Int, passEntity: idEntity?
        ): Int {
            var i: Int
            var j: Int
            val num: Int
            var n: Int
            var numContacts: Int
            var touch: idClipModel?
            val clipModelList = arrayOfNulls<idClipModel>(Game_local.MAX_GENTITIES)
            val traceBounds = idBounds()
            val trm: idTraceModel
            trm = TraceModelForClipModel(mdl)!!
            numContacts = if (null == passEntity || passEntity.entityNumber != Game_local.ENTITYNUM_WORLD) {
                // test world
                this.numContacts++
                collisionModelManager.Contacts(
                    contacts,
                    maxContacts,
                    start,
                    dir,
                    depth,
                    trm,
                    trmAxis,
                    contentMask,
                    0,
                    getVec3Origin(),
                    idMat3.getMat3_default()
                )
            } else {
                0
            }
            i = 0
            while (i < numContacts) {
                contacts[i].entityNum = Game_local.ENTITYNUM_WORLD
                contacts[i].id = 0
                i++
            }
            if (numContacts >= maxContacts) {
                return numContacts
            }
            if (null == trm) {
                traceBounds.set(idBounds(start).Expand(depth))
            } else {
                traceBounds.FromTransformedBounds(trm.bounds, start, trmAxis)
                traceBounds.ExpandSelf(depth)
            }
            num = GetTraceClipModels(traceBounds, contentMask, passEntity, clipModelList)
            i = 0
            while (i < num) {
                touch = clipModelList[i]
                if (null == touch) {
                    i++
                    continue
                }

                // no contacts with render models
                if (touch.renderModelHandle != -1) {
                    i++
                    continue
                }
                this.numContacts++
                val contactz = contacts.copyOfRange(numContacts, contacts.size)
                n = collisionModelManager.Contacts(
                    contactz, maxContacts - numContacts,
                    start, dir, depth, trm, trmAxis, contentMask,
                    touch.Handle(), touch.origin, touch.axis
                )
                j = 0
                while (j < n) {
                    contacts[numContacts] = contactz[j]
                    contacts[numContacts].entityNum = touch.entity!!.entityNumber
                    contacts[numContacts].id = touch.id
                    numContacts++
                    j++
                }
                if (numContacts >= maxContacts) {
                    break
                }
                i++
            }
            return numContacts
        }

        fun Contents(
            start: idVec3,
            mdl: idClipModel?,
            trmAxis: idMat3,
            contentMask: Int,
            passEntity: idEntity?
        ): Int {
            var i: Int
            val num: Int
            var contents: Int
            var touch: idClipModel?
            val clipModelList = arrayOfNulls<idClipModel>(Game_local.MAX_GENTITIES)
            val traceBounds = idBounds()
            val trm: idTraceModel?
            trm = TraceModelForClipModel(mdl)
            contents = if (null == passEntity || passEntity.entityNumber != Game_local.ENTITYNUM_WORLD) {
                // test world
                numContents++
                collisionModelManager.Contents(
                    start,
                    trm,
                    trmAxis,
                    contentMask,
                    0,
                    getVec3Origin(),
                    idMat3.getMat3_default()
                )
            } else {
                0
            }
            if (null == trm) {
                traceBounds[0] = start
                traceBounds[1] = start
            } else if (trmAxis.IsRotated()) {
                traceBounds.FromTransformedBounds(trm.bounds, start, trmAxis)
            } else {
                traceBounds[0] = trm.bounds[0] + start
                traceBounds[1] = trm.bounds[1] + start
            }
            num = GetTraceClipModels(traceBounds, -1, passEntity, clipModelList)
            i = 0
            while (i < num) {
                touch = clipModelList[i]
                if (null == touch) {
                    i++
                    continue
                }

                // no contents test with render models
                if (touch.renderModelHandle != -1) {
                    i++
                    continue
                }

                // if the entity does not have any contents we are looking for
                if (touch.contents and contentMask == 0) {
                    i++
                    continue
                }

                // if the entity has no new contents flags
                if (touch.contents and contents == touch.contents) {
                    i++
                    continue
                }
                numContents++
                if (collisionModelManager.Contents(
                        start,
                        trm,
                        trmAxis,
                        contentMask,
                        touch.Handle(),
                        touch.origin,
                        touch.axis
                    ) != 0
                ) {
                    contents = contents or (touch.contents and contentMask)
                }
                i++
            }
            return contents
        }

        // special case translations versus the rest of the world
        fun TracePoint(
            results: trace_s,
            start: idVec3,
            end: idVec3,
            contentMask: Int,
            passEntity: idEntity?
        ): Boolean {
            Translation(results, start, end, null, idMat3.getMat3_identity(), contentMask, passEntity)
            return results.fraction < 1.0f
        }

        fun TraceBounds(
            results: trace_s,
            start: idVec3,
            end: idVec3,
            bounds: idBounds,
            contentMask: Int,
            passEntity: idEntity?
        ): Boolean {
            temporaryClipModel.LoadModel(idTraceModel(bounds))
            Translation(
                results,
                start,
                end,
                temporaryClipModel,
                idMat3.getMat3_identity(),
                contentMask,
                passEntity
            )
            return results.fraction < 1.0f
        }

        // clip versus a specific model
        fun TranslationModel(
            results: trace_s,
            start: idVec3,
            end: idVec3,
            mdl: idClipModel?,
            trmAxis: idMat3,
            contentMask: Int,    /*cmHandle_t*/
            model: Int,
            modelOrigin: idVec3,
            modelAxis: idMat3
        ) {
            val trm = TraceModelForClipModel(mdl)
            numTranslations++
            collisionModelManager.Translation(
                results,
                start,
                end,
                trm,
                trmAxis,
                contentMask,
                model,
                modelOrigin,
                modelAxis
            )
        }

        fun RotationModel(
            results: trace_s,
            start: idVec3,
            rotation: idRotation,
            mdl: idClipModel?,
            trmAxis: idMat3,
            contentMask: Int,    /*cmHandle_t*/
            model: Int,
            modelOrigin: idVec3,
            modelAxis: idMat3
        ) {
            val trm = TraceModelForClipModel(mdl)!!
            numRotations++
            collisionModelManager.Rotation(
                results,
                start,
                rotation,
                trm,
                trmAxis,
                contentMask,
                model,
                modelOrigin,
                modelAxis
            )
        }

        fun ContactsModel(
            contacts: Array<contactInfo_t>,
            maxContacts: Int,
            start: idVec3,
            dir: idVec6,
            depth: Float,
            mdl: idClipModel?,
            trmAxis: idMat3,
            contentMask: Int,    /*cmHandle_t*/
            model: Int,
            modelOrigin: idVec3,
            modelAxis: idMat3
        ): Int {
            val trm = TraceModelForClipModel(mdl)!!
            numContacts++
            return collisionModelManager.Contacts(
                contacts,
                maxContacts,
                start,
                dir,
                depth,
                trm,
                trmAxis,
                contentMask,
                model,
                modelOrigin,
                modelAxis
            )
        }

        fun ContentsModel(
            start: idVec3, mdl: idClipModel?, trmAxis: idMat3, contentMask: Int,
            /*cmHandle_t*/model: Int, modelOrigin: idVec3, modelAxis: idMat3
        ): Int {
            val trm = TraceModelForClipModel(mdl)!!
            numContents++
            return collisionModelManager.Contents(
                start,
                trm,
                trmAxis,
                contentMask,
                model,
                modelOrigin,
                modelAxis
            )
        }

        // clip versus all entities but not the world
        fun TranslationEntities(
            results: trace_s, start: idVec3, end: idVec3,
            mdl: idClipModel?, trmAxis: idMat3, contentMask: Int, passEntity: idEntity?
        ) {
            var i: Int
            val num: Int
            var touch: idClipModel?
            val clipModelList = arrayOfNulls<idClipModel>(Game_local.MAX_GENTITIES)
            val traceBounds = idBounds()
            val radius: Float
            val trace = trace_s()
            val trm: idTraceModel?
            if (TestHugeTranslation(results, mdl, start, end, trmAxis)) {
                return
            }
            trm = TraceModelForClipModel(mdl)
            results.fraction = 1.0f
            results.endpos.set(end)
            results.endAxis.set(trmAxis)
            radius = if (null == trm) {
                traceBounds.FromPointTranslation(start, end.minus(start))
                0.0f
            } else {
                traceBounds.FromBoundsTranslation(trm.bounds, start, trmAxis, end.minus(start))
                trm.bounds.GetRadius()
            }
            num = GetTraceClipModels(traceBounds, contentMask, passEntity, clipModelList)
            i = 0
            while (i < num) {
                touch = clipModelList[i]
                if (null == touch) {
                    i++
                    continue
                }
                if (touch.renderModelHandle != -1) {
                    numRenderModelTraces++
                    TraceRenderModel(trace, start, end, radius, trmAxis, touch)
                } else {
                    numTranslations++
                    collisionModelManager.Translation(
                        trace, start, end, trm, trmAxis, contentMask,
                        touch.Handle(), touch.origin, touch.axis
                    )
                }
                if (trace.fraction < results.fraction) {
                    results.set(trace)
                    results.c.entityNum = touch.entity!!.entityNumber
                    results.c.id = touch.id
                    if (results.fraction == 0.0f) {
                        break
                    }
                }
                i++
            }
        }

        // get a contact feature
        fun GetModelContactFeature(
            contact: contactInfo_t,
            clipModel: idClipModel?,
            winding: idFixedWinding
        ): Boolean {
            var i: Int
            var   /*cmHandle_t*/handle: Int
            val start = idVec3()
            val end = idVec3()
            handle = -1
            winding.Clear()
            handle = if (clipModel == null) {
                0
            } else {
                if (clipModel.renderModelHandle != -1) {
                    winding.plusAssign(contact.point)
                    return true
                } else if (clipModel.traceModelIndex != -1) {
                    collisionModelManager.SetupTrmModel(
                        idClipModel.GetCachedTraceModel(clipModel.traceModelIndex),
                        arrayOf(clipModel.material)
                    )
                } else {
                    clipModel.collisionModelHandle
                }
            }

            // if contact with a collision model
            if (handle != -1) {
                when (contact.type) {
                    contactType_t.CONTACT_EDGE -> {

                        // the model contact feature is a collision model edge
                        collisionModelManager.GetModelEdge(
                            handle,
                            contact.modelFeature,
                            start,
                            end
                        )
                        winding.plusAssign(start)
                        winding.plusAssign(end)
                    }

                    contactType_t.CONTACT_MODELVERTEX -> {

                        // the model contact feature is a collision model vertex
                        collisionModelManager.GetModelVertex(handle, contact.modelFeature, start)
                        winding.plusAssign(start)
                    }

                    contactType_t.CONTACT_TRMVERTEX -> {

                        // the model contact feature is a collision model polygon
                        collisionModelManager.GetModelPolygon(
                            handle,
                            contact.modelFeature,
                            winding
                        ) //TODO:is this function necessary?
                    }

                    else -> {}
                }
            }

            // transform the winding to world space
            if (clipModel != null) {
                i = 0
                while (i < winding.GetNumPoints()) {
                    winding[i].ToVec3_oMulSet(clipModel.axis)
                    winding[i].ToVec3_oPluSet(clipModel.origin)
                    i++
                }
            }
            return true
        }

        // get entities/clip models within or touching the given bounds
        fun EntitiesTouchingBounds(
            bounds: idBounds,
            contentMask: Int,
            entityList: Array<idEntity?>,
            maxCount: Int
        ): Int {
            val clipModelList = arrayOfNulls<idClipModel>(Game_local.MAX_GENTITIES)
            var i: Int
            var j: Int
            val count: Int
            var entCount: Int
            count = ClipModelsTouchingBounds(bounds, contentMask, clipModelList, Game_local.MAX_GENTITIES)
            entCount = 0
            i = 0
            while (i < count) {

                // entity could already be in the list because an entity can use multiple clip models
                j = 0
                while (j < entCount) {
                    if (entityList[j] === clipModelList[i]!!.entity) {
                        break
                    }
                    j++
                }
                if (j >= entCount) {
                    if (entCount >= maxCount) {
                        Game_local.gameLocal.Warning("idClip::EntitiesTouchingBounds: max count")
                        return entCount
                    }
                    entityList[entCount] = clipModelList[i]!!.entity!!
                    entCount++
                }
                i++
            }
            return entCount
        }

        fun ClipModelsTouchingBounds(
            bounds: idBounds,
            contentMask: Int,
            clipModelList: Array<idClipModel?>,
            maxCount: Int
        ): Int {
            val parms = listParms_s()
            if (bounds[0, 0] > bounds[1, 0] || bounds[0, 1] > bounds[1, 1] || bounds[0, 2] > bounds[1, 2]
            ) {
                // we should not go through the tree for degenerate or backwards bounds
                assert(false)
                return 0
            }
            parms.bounds[0] = bounds[0] - vec3_boxEpsilon
            parms.bounds[1] = bounds[1] + vec3_boxEpsilon
            parms.contentMask = contentMask
            parms.list = clipModelList
            parms.count = 0
            parms.maxCount = maxCount
            touchCount++
            ClipModelsTouchingBounds_r(clipSectors!![0], parms)
            return parms.count
        }

        fun GetWorldBounds(): idBounds {
            return worldBounds
        }

        fun DefaultClipModel(): idClipModel {
            return defaultClipModel
        }

        // stats and debug drawing
        fun PrintStatistics() {
            Game_local.gameLocal.Printf(
                "t = %-3d, r = %-3d, m = %-3d, render = %-3d, contents = %-3d, contacts = %-3d\n",
                numTranslations, numRotations, numMotions, numRenderModelTraces, numContents, numContacts
            )
            numContacts = 0
            numContents = numContacts
            numRenderModelTraces = numContents
            numMotions = numRenderModelTraces
            numTranslations = numMotions
            numRotations = numTranslations
        }

        fun DrawClipModels(eye: idVec3, radius: Float, passEntity: idEntity?) {
            var i: Int
            val num: Int
            val bounds = idBounds()
            val clipModelList = arrayOfNulls<idClipModel>(Game_local.MAX_GENTITIES)
            var clipModel: idClipModel
            bounds.set(idBounds(eye).Expand(radius))
            num = ClipModelsTouchingBounds(bounds, -1, clipModelList, Game_local.MAX_GENTITIES)
            i = 0
            while (i < num) {
                clipModel = clipModelList[i]!!
                if (clipModel.GetEntity() === passEntity) {
                    i++
                    continue
                }
                if (clipModel.renderModelHandle != -1) {
                    Game_local.gameRenderWorld!!.DebugBounds(colorCyan, clipModel.GetAbsBounds())
                } else {
                    collisionModelManager.DrawModel(
                        clipModel.Handle(),
                        clipModel.GetOrigin(),
                        clipModel.GetAxis(),
                        eye,
                        radius
                    )
                }
                i++
            }
        }

        fun DrawModelContactFeature(contact: contactInfo_t, clipModel: idClipModel?, lifetime: Int): Boolean {
            var i: Int
            val axis: idMat3
            val winding = idFixedWinding()
            if (!GetModelContactFeature(contact, clipModel, winding)) {
                return false
            }
            axis = contact.normal.ToMat3()
            if (winding.GetNumPoints() == 1) {
                Game_local.gameRenderWorld!!.DebugLine(
                    colorCyan,
                    winding[0].ToVec3(),
                    winding[0].ToVec3() + axis[0] * 2.0f,
                    lifetime
                )
                Game_local.gameRenderWorld!!.DebugLine(
                    colorWhite,
                    winding[0].ToVec3() -  /*- 1.0f * */axis[1],
                    winding[0].ToVec3() +  /*+ 1.0f */axis[1],
                    lifetime
                )
                Game_local.gameRenderWorld!!.DebugLine(
                    colorWhite,
                    winding[0].ToVec3() -  /*- 1.0f * */axis[2],
                    winding[0].ToVec3() + /*+ 1.0f */axis[2],
                    lifetime
                )
            } else {
                i = 0
                while (i < winding.GetNumPoints()) {
                    Game_local.gameRenderWorld!!.DebugLine(
                        colorCyan,
                        winding[i].ToVec3(),
                        winding[(i + 1) % winding.GetNumPoints()].ToVec3(),
                        lifetime
                    )
                    i++
                }
            }
            axis[0] = axis[0].unaryMinus()
            axis[2] = axis[2].unaryMinus()
            Game_local.gameRenderWorld!!.DrawText(
                contact.material!!.GetName(),
                winding.GetCenter().minus(axis[2].times(4.0f)),
                0.1f,
                colorWhite,
                axis,
                1,
                5000
            )
            return true
        }

        /*
         ===============
         idClip::CreateClipSectors_r

         Builds a uniformly subdivided tree for the given world size
         ===============
         */
        private fun CreateClipSectors_r(depth: Int, bounds: idBounds, maxSector: idVec3): clipSector_s {
            var i: Int
            val anode: clipSector_s
            val size = idVec3()
            val front = idBounds()
            val back = idBounds()
            //clipSectors[numClipSectors++] = clipSector_s()
            anode = clipSectors!![numClipSectors++]
            if (depth == MAX_SECTOR_DEPTH) {
                anode.axis = -1
                anode.children[1] = null
                anode.children[0] = anode.children[1]
                i = 0
                while (i < 3) {
                    if (bounds[1, i] - bounds[0, i] > maxSector[i]) {
                        maxSector[i] = bounds[1, i] - bounds[0, i]
                    }
                    i++
                }
                return anode
            }
            size.set(bounds[1].minus(bounds[0]))
            if (size[0] >= size[1] && size[0] >= size[2]) {
                anode.axis = 0
            } else if (size[1] >= size[0] && size[1] >= size[2]) {
                anode.axis = 1
            } else {
                anode.axis = 2
            }
            anode.dist = 0.5f * (bounds[1, anode.axis] + bounds[0, anode.axis])
            front.set(bounds)
            back.set(bounds)
            back[1, anode.axis] = anode.dist
            front[0, anode.axis] = anode.dist
            anode.children[0] = CreateClipSectors_r(depth + 1, front, maxSector)
            anode.children[1] = CreateClipSectors_r(depth + 1, back, maxSector)
            return anode
        }

        private fun ClipModelsTouchingBounds_r(node: clipSector_s, parms: listParms_s) {
            var node = node
            while (node.axis != -1) {
                node = if (parms.bounds[0, node.axis] > node.dist) {
                    node.children[0]!!
                } else if (parms.bounds[1, node.axis] < node.dist) {
                    node.children[1]!!
                } else {
                    ClipModelsTouchingBounds_r(node.children[0]!!, parms)
                    node.children[1]!!
                }
            }
            var link = node.clipLinks
            while (link != null) {
                val check = link.clipModel

                // if the clip model is enabled
                if (!check.enabled) {
                    link = link.nextInSector
                    continue
                }

                // avoid duplicates in the list
                if (check.touchCount == touchCount) {
                    link = link.nextInSector
                    continue
                }

                // if the clip model does not have any contents we are looking for
                if (0 == check.contents and parms.contentMask) {
                    link = link.nextInSector
                    continue
                }

                // if the bounds really do overlap
                if (check.absBounds[0, 0] > parms.bounds[1, 0] || check.absBounds[1, 0] < parms.bounds[0, 0] || check.absBounds[0, 1] > parms.bounds[1, 1] || check.absBounds[1, 1] < parms.bounds[0, 1] || check.absBounds[0, 2] > parms.bounds[1, 2] || check.absBounds[1, 2] < parms.bounds[0, 2]
                ) {
                    link = link.nextInSector
                    continue
                }
                if (parms.count >= parms.maxCount) {
                    Game_local.gameLocal.Warning("idClip::ClipModelsTouchingBounds_r: max count")
                    return
                }
                check.touchCount = touchCount
                parms.list[parms.count] = check
                parms.count++
                link = link.nextInSector
            }
        }

        private fun TraceModelForClipModel(mdl: idClipModel?): idTraceModel? {
            return if (null == mdl) {
                null
            } else {
                if (!mdl.IsTraceModel()) {
                    if (mdl.GetEntity() != null) {
                        idGameLocal.Error(
                            "TraceModelForClipModel: clip model %d on '%s' is not a trace model\n",
                            mdl.GetId(),
                            mdl.GetEntity()!!.name
                        )
                    } else {
                        idGameLocal.Error(
                            "TraceModelForClipModel: clip model %d is not a trace model\n",
                            mdl.GetId()
                        )
                    }
                }
                idClipModel.GetCachedTraceModel(mdl.traceModelIndex)
            }
        }

        /*
         ====================
         idClip::GetTraceClipModels

         an ent will be excluded from testing if:
         cm->entity == passEntity ( don't clip against the pass entity )
         cm->entity == passOwner ( missiles don't clip with owner )
         cm->owner == passEntity ( don't interact with your own missiles )
         cm->owner == passOwner ( don't interact with other missiles from same owner )
         ====================
         */
        private fun GetTraceClipModels(
            bounds: idBounds,
            contentMask: Int,
            passEntity: idEntity?,
            clipModelList: Array<idClipModel?>
        ): Int {
            var i: Int
            val num: Int
            var cm: idClipModel
            val passOwner: idEntity?
            num = ClipModelsTouchingBounds(bounds, contentMask, clipModelList, Game_local.MAX_GENTITIES)
            if (null == passEntity) {
                return num
            }
            passOwner = if (passEntity.GetPhysics().GetNumClipModels() > 0) {
                passEntity.GetPhysics().GetClipModel()!!.GetOwner()
            } else {
                null
            }
            i = 0
            while (i < num) {
                cm = clipModelList[i]!!

                // check if we should ignore this entity
                if (cm.entity === passEntity) {
                    clipModelList[i] = null // don't clip against the pass entity
                } else if (cm.entity === passOwner) {
                    clipModelList[i] = null // missiles don't clip with their owner
                } else if (cm.owner != null) {
                    if (cm.owner === passEntity) {
                        clipModelList[i] = null // don't clip against own missiles
                    } else if (cm.owner === passOwner) {
                        clipModelList[i] = null // don't clip against other missiles from same owner
                    }
                }
                i++
            }
            return num
        }

        private fun TraceRenderModel(
            trace: trace_s,
            start: idVec3,
            end: idVec3,
            radius: Float,
            axis: idMat3,
            touch: idClipModel
        ) {
            trace.fraction = 1.0f

            // if the trace is passing through the bounds
            if (touch.absBounds.Expand(radius).LineIntersection(start, end)) {
                val modelTrace = modelTrace_s()

                // test with exact render model and modify trace_t structure accordingly
                if (Game_local.gameRenderWorld!!.ModelTrace(modelTrace, touch.renderModelHandle, start, end, radius)) {
                    trace.fraction = modelTrace.fraction
                    trace.endAxis.set(axis)
                    trace.endpos.set(modelTrace.point)
                    trace.c.normal.set(modelTrace.normal)
                    trace.c.dist = modelTrace.point.times(modelTrace.normal)
                    trace.c.point.set(modelTrace.point)
                    trace.c.type = contactType_t.CONTACT_TRMVERTEX
                    trace.c.modelFeature = 0
                    trace.c.trmFeature = 0
                    trace.c.contents = modelTrace.material!!.GetContentFlags()
                    trace.c.material = modelTrace.material
                    // NOTE: trace.c.id will be the joint number
                    touch.id = JOINT_HANDLE_TO_CLIPMODEL_ID(modelTrace.jointNumber)
                }
            }
        }

        /*
         ====================
         idClip::ClipModelsTouchingBounds_r
         ====================
         */
        private class listParms_s {
            val bounds: idBounds = idBounds()
            var contentMask = 0
            var count = 0
            lateinit var list: Array<idClipModel?>
            var maxCount = 0
        }

        //
        //
        init {
            numClipSectors = 0
            clipSectors = null
            worldBounds.Zero()
            numContacts = 0
            numContents = numContacts
            numRenderModelTraces = numContents
            numMotions = numRenderModelTraces
            numTranslations = numMotions
            numRotations = numTranslations
        }
    }
}
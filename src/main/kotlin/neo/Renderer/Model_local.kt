/*
===========================================================================

Doom 3 GPL Source Code
Copyright (C) 1999-2011 id Software LLC, a ZeniMax Media company.
Translated to Kotlin by Dr. Feederino with support of Claude Code

This file is part of the Doom 3 GPL Source Code ("Doom 3 Source Code").
Original source: neo/renderer/Model.cpp + neo/renderer/Model_local.h

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

package neo.Renderer

import neo.Renderer.Material.deform_t
import neo.Renderer.Material.idMaterial
import neo.Renderer.Model.dynamicModel_t
import neo.Renderer.Model.idMD5Joint
import neo.Renderer.Model.idRenderModel
import neo.Renderer.Model.modelSurface_s
import neo.Renderer.Model.srfTriangles_s
import neo.Renderer.Model_ase.aseFace_t
import neo.Renderer.Model_ase.aseMaterial_t
import neo.Renderer.Model_ase.aseMesh_t
import neo.Renderer.Model_ase.aseModel_s
import neo.Renderer.Model_ase.aseObject_t
import neo.Renderer.Model_lwo.lwLayer
import neo.Renderer.Model_lwo.lwObject
import neo.Renderer.Model_lwo.lwPoint
import neo.Renderer.Model_lwo.lwPolygon
import neo.Renderer.Model_lwo.lwSurface
import neo.Renderer.Model_lwo.lwVMap
import neo.Renderer.Model_lwo.lwVMapPt
import neo.Renderer.Model_ma.maMaterial_t
import neo.Renderer.Model_ma.maMesh_t
import neo.Renderer.Model_ma.maModel_s
import neo.Renderer.Model_ma.maObject_t
import neo.Renderer.RenderWorld.renderEntity_s
import neo.TempDump.ctos
import neo.framework.CVarSystem.CVAR_BOOL
import neo.framework.CVarSystem.CVAR_RENDERER
import neo.framework.CVarSystem.idCVar
import neo.framework.Common
import neo.framework.DeclManager
import neo.framework.DemoFile.idDemoFile
import neo.framework.FileSystem_h.fileSystem
import neo.idlib.BV.idBounds
import neo.idlib.BigFloat
import neo.idlib.Text.Str.idStr
import neo.idlib.Text.Str.idStr.Companion.Cmpn
import neo.idlib.containers.CInt
import neo.idlib.containers.List.idList
import neo.idlib.containers.VectorSet.idVectorSubset
import neo.idlib.geometry.DrawVert
import neo.idlib.geometry.JointTransform.idJointQuat
import neo.idlib.geometry.Winding.idWinding.Companion.TriangleArea
import neo.idlib.idException
import neo.idlib.math.SIMDProcessor
import neo.idlib.math.idMath.Cos
import neo.idlib.math.idMath.Sin
import neo.idlib.math.idVec
import neo.idlib.math.idVec2
import neo.idlib.math.idVec3
import java.nio.*
import java.util.*
import kotlin.math.sqrt

object Model_local {
    /*
     ================
     AddCubeFace
     ================
     */
    fun AddCubeFace(tri: srfTriangles_s, v1: idVec3, v2: idVec3, v3: idVec3, v4: idVec3) {
        tri.verts!![tri.numVerts + 0]!!.Clear()
        tri.verts!![tri.numVerts + 0]!!.xyz.set(v1.times(8))
        tri.verts!![tri.numVerts + 0]!!.st[0] = 0.0f
        tri.verts!![tri.numVerts + 0]!!.st[1] = 0.0f
        tri.verts!![tri.numVerts + 1]!!.Clear()
        tri.verts!![tri.numVerts + 1]!!.xyz.set(v2.times(8))
        tri.verts!![tri.numVerts + 1]!!.st[0] = 1.0f
        tri.verts!![tri.numVerts + 1]!!.st[1] = 0.0f
        tri.verts!![tri.numVerts + 2]!!.Clear()
        tri.verts!![tri.numVerts + 2]!!.xyz.set(v3.times(8))
        tri.verts!![tri.numVerts + 2]!!.st[0] = 1.0f
        tri.verts!![tri.numVerts + 2]!!.st[1] = 1.0f
        tri.verts!![tri.numVerts + 3]!!.Clear()
        tri.verts!![tri.numVerts + 3]!!.xyz.set(v4.times(8))
        tri.verts!![tri.numVerts + 3]!!.st[0] = 0.0f
        tri.verts!![tri.numVerts + 3]!!.st[1] = 1.0f
        tri.indexes!![tri.numIndexes + 0] = tri.numVerts + 0
        tri.indexes!![tri.numIndexes + 1] = tri.numVerts + 1
        tri.indexes!![tri.numIndexes + 2] = tri.numVerts + 2
        tri.indexes!![tri.numIndexes + 3] = tri.numVerts + 0
        tri.indexes!![tri.numIndexes + 4] = tri.numVerts + 2
        tri.indexes!![tri.numIndexes + 5] = tri.numVerts + 3
        tri.numVerts += 4
        tri.numIndexes += 6
    }

    /*
     ===============================================================================

     Static model

     ===============================================================================
     */
    open class idRenderModelStatic : idRenderModel() {
        val surfaces: idList<modelSurface_s?>
        protected val  /*ID_TIME_T*/timeStamp: LongArray = LongArray(1)
        val bounds: idBounds = idBounds()
        var overlaysAdded: Int
        protected var defaulted: Boolean
        protected var fastLoad // don't generate tangents and shadow data
                : Boolean
        protected var isStaticWorldModel: Boolean
        protected var lastArchivedFrame: Int
        protected var lastModifiedFrame: Int
        protected var levelLoadReferenced // for determining if it needs to be freed
                : Boolean
        protected var name: idStr
        protected var purged // eventually we will have dynamic reloading
                : Boolean
        protected var reloadable // if not, reloadModels won't check timestamp
                : Boolean
        protected var shadowHull: srfTriangles_s?

        // the inherited public interface
        init {
            surfaces = idList()
            name = idStr("<undefined>")
            bounds.Clear()
            lastModifiedFrame = 0
            lastArchivedFrame = 0
            overlaysAdded = 0
            shadowHull = null
            isStaticWorldModel = false
            defaulted = false
            purged = false
            fastLoad = false
            reloadable = true
            levelLoadReferenced = false
            timeStamp[0] = 0
        }

        @Throws(idException::class)
        override fun InitFromFile(fileName: String?) {
            val loaded: Boolean
            val extension = idStr()
            InitEmpty(fileName)

            // FIXME: load new .proc map format
            name.ExtractFileExtension(extension)
            if (extension.Icmp("ase") == 0) {
                loaded = LoadASE(name.toString())
                reloadable = true
            } else if (extension.Icmp("lwo") == 0) {
                loaded = LoadLWO(name.toString())
                reloadable = true
            } else if (extension.Icmp("flt") == 0) {
                loaded = LoadFLT(name.toString())
                reloadable = true
            } else if (extension.Icmp("ma") == 0) {
                loaded = LoadMA(name.toString())
                reloadable = true
            } else {
                Common.common.Warning("idRenderModelStatic::InitFromFile: unknown type for model: '%s'", name)
                loaded = false
            }
            if (!loaded) {
                Common.common.Warning("Couldn't load model: '%s'", name)
                MakeDefaultModel()
                return
            }

            // it is now available for use
            purged = false

            // create the bounds for culling and dynamic surface creation
            FinishSurfaces()
        }

        override fun PartialInitFromFile(fileName: String?) {
            fastLoad = true
            InitFromFile(fileName)
        }

        override fun PurgeModel() {
            var i: Int
            var surf: modelSurface_s?
            i = 0
            while (i < surfaces.Num()) {
                surf = surfaces[i]
                if (surf!!.geometry != null) {
                    R_FreeStaticTriSurf(surf.geometry)
                }
                i++
            }
            surfaces.Clear()
            purged = true
        }

        override fun Reset() {}
        override fun LoadModel() {
            PurgeModel()
            InitFromFile(name.toString())
        }

        override fun IsLoaded(): Boolean {
            return !purged
        }

        override fun SetLevelLoadReferenced(referenced: Boolean) {
            levelLoadReferenced = referenced
        }

        override fun IsLevelLoadReferenced(): Boolean {
            return levelLoadReferenced
        }

        override fun TouchData() {
            for (i in 0 until surfaces.Num()) {
                val surf: modelSurface_s? = surfaces[i]

                // re-find the material to make sure it gets added to the
                // level keep list
                DeclManager.declManager.FindMaterial(surf!!.shader!!.GetName())
            }
        }

        override fun InitEmpty(fileName: String?) {
            // model names of the form _area* are static parts of the
            // world, and have already been considered for optimized shadows
            // other model names are inline entity models, and need to be
            // shadowed normally
            isStaticWorldModel = 0 == Cmpn((fileName)!!, "_area", 5)
            name = idStr((fileName))
            reloadable = false // if it didn't come from a file, we can't reload it
            PurgeModel()
            purged = false
            bounds.Zero()
        }

        override fun AddSurface(surface: modelSurface_s?) {
            surfaces.Append(modelSurface_s(surface))
            if (surface!!.geometry != null) {
                bounds.plusAssign(surface.geometry!!.bounds)
            }
        }

        override fun FinishSurfaces() {
            var i: Int
            var totalVerts: Int
            var totalIndexes: Int
            purged = false

            // make sure we don't have a huge bounds even if we don't finish everything
            bounds.Zero()
            if (surfaces.Num() == 0) {
                return
            }

            // renderBump doesn't care about most of this
            if (fastLoad) {
                bounds.Zero()
                i = 0
                while (i < surfaces.Num()) {
                    val surf: modelSurface_s? = surfaces[i]
                    R_BoundTriSurf(surf!!.geometry!!)
                    bounds.AddBounds(surf.geometry!!.bounds)
                    i++
                }
                return
            }

            // cleanup all the final surfaces, but don't create sil edges
            totalVerts = 0
            totalIndexes = 0

            // decide if we are going to merge all the surfaces into one shadower
            val numOriginalSurfaces: Int = surfaces.Num()

            // make sure there aren't any NULL shaders or geometry
            i = 0
            while (i < numOriginalSurfaces) {
                val surf: modelSurface_s? = surfaces[i]
                if (surf!!.geometry == null || surf.shader == null) {
                    MakeDefaultModel()
                    Common.common.Error("Model %s, surface %d had NULL geometry", name, i)
                }
                if (surf.shader == null) {
                    MakeDefaultModel()
                    Common.common.Error("Model %s, surface %d had NULL shader", name, i)
                }
                i++
            }

            // duplicate and reverse triangles for two sided bump mapped surfaces
            // note that this won't catch surfaces that have their shaders dynamically
            // changed, and won't work with animated models.
            // It is better to create completely separate surfaces, rather than
            // add vertexes and indexes to the existing surface, because the
            // tangent generation wouldn't like the acute shared edges
            i = 0
            while (i < numOriginalSurfaces) {
                val surf: modelSurface_s? = surfaces[i]
                if (surf!!.shader!!.ShouldCreateBackSides()) {
                    var newTri: srfTriangles_s?
                    newTri = R_CopyStaticTriSurf(surf.geometry!!)
                    R_ReverseTriangles(newTri)
                    val newSurf = modelSurface_s()
                    newSurf.shader = surf.shader
                    newSurf.geometry = newTri
                    AddSurface(newSurf)
                }
                i++
            }

            // clean the surfaces
            i = 0
            while (i < surfaces.Num()) {
                val surf: modelSurface_s = surfaces[i]!!
                R_CleanupTriangles(
                    surf.geometry!!,
                    surf.geometry!!.generateNormals,
                    true,
                    surf.shader!!.UseUnsmoothedTangents()
                )
                if (surf.shader!!.SurfaceCastsShadow()) {
                    totalVerts += surf.geometry!!.numVerts
                    totalIndexes += surf.geometry!!.numIndexes
                }
                i++
            }

            // add up the total surface area for development information
            i = 0
            while (i < surfaces.Num()) {
                val surf: modelSurface_s? = surfaces[i]
                val tri: srfTriangles_s? = surf!!.geometry
                var j = 0
                while (j < tri!!.numIndexes) {
                    val area: Float = TriangleArea(
                        tri.verts!![tri.indexes!![j]]!!.xyz,
                        tri.verts!![tri.indexes!![j + 1]]!!.xyz, tri.verts!![tri.indexes!![j + 2]]!!.xyz
                    )
                    surf.shader!!.AddToSurfaceArea(area)
                    j += 3
                }
                i++
            }

            // calculate the bounds
            if (surfaces.Num() == 0) {
                bounds.Zero()
            } else {
                bounds.Clear()
                i = 0
                while (i < surfaces.Num()) {
                    val surf: modelSurface_s? = surfaces[i]

                    // if the surface has a deformation, increase the bounds
                    // the amount here is somewhat arbitrary, designed to handle
                    // autosprites and flares, but could be done better with exact
                    // deformation information.
                    // Note that this doesn't handle deformations that are skinned in
                    // at run time...
                    if (surf!!.shader!!.Deform() != deform_t.DFRM_NONE) {
                        val tri: srfTriangles_s? = surf.geometry
                        val mid = idVec3((tri!!.bounds[1].plus(tri.bounds[0])).times(0.5f))
                        var radius: Float = (tri.bounds[0].minus(mid)).Length()
                        radius += 20.0f
                        tri.bounds[0, 0] = mid[0] - radius
                        tri.bounds[0, 1] = mid[1] - radius
                        tri.bounds[0, 2] = mid[2] - radius
                        tri.bounds[1, 0] = mid[0] + radius
                        tri.bounds[1, 1] = mid[1] + radius
                        tri.bounds[1, 2] = mid[2] + radius
                    }

                    // add to the model bounds
                    bounds.AddBounds(surf.geometry!!.bounds)
                    i++
                }
            }
        }

        /*
         ==============
         idRenderModelStatic::FreeVertexCache

         We are about to restart the vertex cache, so dump everything
         ==============
         */
        override fun FreeVertexCache() {
            for (j in 0 until surfaces.Num()) {
                val tri: srfTriangles_s? = surfaces[j]!!.geometry
                if (null == tri) {
                    continue
                }
                if (tri.ambientCache != null) {
                    VertexCache.vertexCache.Free(tri.ambientCache)
                    tri.ambientCache = null
                }
                // static shadows may be present
                if (tri.shadowCache != null) {
                    VertexCache.vertexCache.Free(tri.shadowCache)
                    tri.shadowCache = null
                }
            }
        }

        override fun Name(): String {
            return name.toString()
        }

        override fun Print() {
            Common.common.Printf("%s\n", name)
            Common.common.Printf("Static model.\n")
            Common.common.Printf(
                "bounds: (%f %f %f) to (%f %f %f)\n",
                bounds[0][0], bounds[0][1], bounds[0][2],
                bounds[1][0], bounds[1][1], bounds[1][2]
            )

            Common.common.Printf("    verts  tris material\n")
            for (i in 0 until NumSurfaces()) {
                val surf: modelSurface_s? = Surface(i)

                val tri: srfTriangles_s? = surf!!.geometry
                val material: idMaterial? = surf.shader

                if (tri == null) {
                    Common.common.Printf("%2i: %s, NULL surface geometry\n", i, material!!.GetName())
                    continue
                }

                Common.common.Printf("%2i: %5i %5i %s", i, tri.numVerts, tri.numIndexes / 3, material!!.GetName())
                if (tri.generateNormals) {
                    Common.common.Printf(" (smoothed)\n")
                } else {
                    Common.common.Printf("\n")
                }
            }
        }

        override fun List() {
            var totalTris = 0
            var totalVerts = 0
            val totalBytes: Int
            totalBytes = Memory()
            var closed = 'C'
            for (j in 0 until NumSurfaces()) {
                val surf: modelSurface_s? = Surface(j)
                if (null == surf!!.geometry) {
                    continue
                }
                if (!surf.geometry!!.perfectHull) {
                    closed = ' '
                }
                totalTris += surf.geometry!!.numIndexes / 3
                totalVerts += surf.geometry!!.numVerts
            }
            Common.common.Printf(
                "%c%4dk %3d %4d %4d %s",
                closed,
                totalBytes / 1024,
                NumSurfaces(),
                totalVerts,
                totalTris,
                Name()
            )
            if (IsDynamicModel() == dynamicModel_t.DM_CACHED) {
                Common.common.Printf(" (DM_CACHED)")
            }
            if (IsDynamicModel() == dynamicModel_t.DM_CONTINUOUS) {
                Common.common.Printf(" (DM_CONTINUOUS)")
            }
            if (defaulted) {
                Common.common.Printf(" (DEFAULTED)")
            }
            if (bounds[0][0] >= bounds[1][0]) {
                Common.common.Printf(" (EMPTY BOUNDS)")
            }
            if (bounds[1][0] - bounds[0][0] > 100000) {
                Common.common.Printf(" (HUGE BOUNDS)")
            }
            Common.common.Printf("\n")
        }

        override fun Memory(): Int {
            var totalBytes = 0
            totalBytes += 4
            totalBytes += name.DynamicMemoryUsed()
            totalBytes += surfaces.MemoryUsed()
            if (shadowHull != null) {
                totalBytes += R_TriSurfMemory(shadowHull)
            }
            for (j in 0 until NumSurfaces()) {
                val surf: modelSurface_s? = Surface(j)
                if (null == surf!!.geometry) {
                    continue
                }
                totalBytes += R_TriSurfMemory(surf.geometry)
            }
            return totalBytes
        }

        override fun  /*ID_TIME_T*/Timestamp(): LongArray {
            return timeStamp
        }

        override fun NumSurfaces(): Int {
            return surfaces.Num()
        }

        override fun NumBaseSurfaces(): Int {
            return surfaces.Num() - overlaysAdded
        }

        override fun Surface(surfaceNum: Int): modelSurface_s? {
            return surfaces[surfaceNum]
        }

        override fun AllocSurfaceTriangles(numVerts: Int, numIndexes: Int): srfTriangles_s {
            val tri: srfTriangles_s = R_AllocStaticTriSurf()
            R_AllocStaticTriSurfVerts(tri, numVerts)
            R_AllocStaticTriSurfIndexes(tri, numIndexes)
            return tri
        }

        override fun FreeSurfaceTriangles(tris: srfTriangles_s?) {
            R_FreeStaticTriSurf(tris)
        }

        override fun ShadowHull(): srfTriangles_s? {
            return shadowHull
        }

        override fun IsStaticWorldModel(): Boolean {
            return isStaticWorldModel
        }

        override fun IsReloadable(): Boolean {
            return reloadable
        }

        override fun IsDynamicModel(): dynamicModel_t {
            // dynamic subclasses will override this
            return dynamicModel_t.DM_STATIC
        }

        override fun IsDefaultModel(): Boolean {
            return defaulted
        }

        override fun InstantiateDynamicModel(
            ent: renderEntity_s?,
            view: viewDef_s?,
            cachedModel: idRenderModel?
        ): idRenderModel? {
            Common.common.Error("InstantiateDynamicModel called on static model '%s'", name.toString())
            return null
        }

        override fun NumJoints(): Int {
            return 0
        }

        override fun GetJoints(): Array<idMD5Joint?>? {
            return null
        }

        override fun GetJointHandle(name: String?): Int {
            return Model.INVALID_JOINT
        }

        override fun GetJointName(jointHandle_t: Int): String {
            return ""
        }

        override fun GetDefaultPose(): Array<idJointQuat?>? {
            return null
        }

        override fun NearestJoint(surfaceNum: Int, a: Int, b: Int, c: Int): Int {
            return Model.INVALID_JOINT
        }

        override fun Bounds(ent: renderEntity_s?): idBounds {
            return idBounds(bounds[0], bounds[1])
        }

        override fun Bounds(): idBounds {
            return Bounds(null)
        }

        override fun ReadFromDemoFile(f: idDemoFile?) {
            PurgeModel()
            InitEmpty(f!!.ReadHashString())
            var i: Int
            var j: Int
            val numSurfaces = CInt()
            val index = CInt()
            val vert = CInt()
            f.ReadInt(numSurfaces)
            i = 0
            while (i < numSurfaces.integerValue) {
                val surf = modelSurface_s()
                surf.shader = DeclManager.declManager.FindMaterial(f.ReadHashString())
                val tri: srfTriangles_s = R_AllocStaticTriSurf()
                f.ReadInt(index)
                tri.numIndexes = index.integerValue
                R_AllocStaticTriSurfIndexes(tri, tri.numIndexes)
                j = 0
                while (j < tri.numIndexes) {
                    f.ReadInt(index)
                    tri.indexes!![j] = index.integerValue
                    ++j
                }
                f.ReadInt(vert)
                tri.numVerts = vert.integerValue
                R_AllocStaticTriSurfVerts(tri, tri.numVerts)
                j = 0
                while (j < tri.numVerts) {
                    val color: Array<CharArray> = Array(4, { CharArray(1) })
                    f.ReadVec3(tri.verts!![j]!!.xyz)
                    f.ReadVec2(tri.verts!![j]!!.st)
                    f.ReadVec3(tri.verts!![j]!!.normal)
                    f.ReadVec3(tri.verts!![j]!!.tangents[0])
                    f.ReadVec3(tri.verts!![j]!!.tangents[1])
                    f.ReadUnsignedChar(color[0])
                    tri.verts!![j]!!.color[0] = color[0][0].code.toByte()
                    f.ReadUnsignedChar(color[1])
                    tri.verts!![j]!!.color[1] = color[1][0].code.toByte()
                    f.ReadUnsignedChar(color[2])
                    tri.verts!![j]!!.color[2] = color[2][0].code.toByte()
                    f.ReadUnsignedChar(color[3])
                    tri.verts!![j]!!.color[3] = color[3][0].code.toByte()
                    ++j
                }
                surf.geometry = tri
                AddSurface(surf)
                i++
            }
            FinishSurfaces()
        }

        override fun WriteToDemoFile(f: idDemoFile) {
            // note that it has been updated
            lastArchivedFrame = tr.frameCount

            f.WriteInt(demoCommand_t.DC_DEFINE_MODEL)
            f.WriteHashString(Name())
            var i: Int
            var j: Int
            val iData: Int = surfaces.Num()
            f.WriteInt(iData)
            i = 0
            while (i < surfaces.Num()) {
                val surf: modelSurface_s? = surfaces[i]
                f.WriteHashString(surf!!.shader!!.GetName())
                val tri: srfTriangles_s? = surf.geometry
                f.WriteInt(tri!!.numIndexes)
                j = 0
                while (j < tri.numIndexes) {
                    f.WriteInt(tri.indexes!![j])
                    ++j
                }
                f.WriteInt(tri.numVerts)
                j = 0
                while (j < tri.numVerts) {
                    f.WriteVec3(tri.verts!![j]!!.xyz)
                    f.WriteVec2(tri.verts!![j]!!.st)
                    f.WriteVec3(tri.verts!![j]!!.normal)
                    f.WriteVec3(tri.verts!![j]!!.tangents[0])
                    f.WriteVec3(tri.verts!![j]!!.tangents[1])
                    f.WriteUnsignedChar(Char(tri.verts!![j]!!.color[0].toUShort()))
                    f.WriteUnsignedChar(Char(tri.verts!![j]!!.color[1].toUShort()))
                    f.WriteUnsignedChar(Char(tri.verts!![j]!!.color[2].toUShort()))
                    f.WriteUnsignedChar(Char(tri.verts!![j]!!.color[3].toUShort()))
                    ++j
                }
                i++
            }
        }

        override fun DepthHack(): Float {
            return 0.0f
        }

        fun MakeDefaultModel() {
            defaulted = true

            // throw out any surfaces we already have
            PurgeModel()

            // create one new surface
            val surf = modelSurface_s()
            val tri = srfTriangles_s()
            surf.shader = tr.defaultMaterial
            surf.geometry = tri
            R_AllocStaticTriSurfVerts(tri, 24)
            R_AllocStaticTriSurfIndexes(tri, 36)
            AddCubeFace(tri, idVec3(-1, 1, 1), idVec3(1, 1, 1), idVec3(1, -1, 1), idVec3(-1, -1, 1))
            AddCubeFace(tri, idVec3(-1, 1, -1), idVec3(-1, -1, -1), idVec3(1, -1, -1), idVec3(1, 1, -1))
            AddCubeFace(tri, idVec3(1, -1, 1), idVec3(1, 1, 1), idVec3(1, 1, -1), idVec3(1, -1, -1))
            AddCubeFace(tri, idVec3(-1, -1, 1), idVec3(-1, -1, -1), idVec3(-1, 1, -1), idVec3(-1, 1, 1))
            AddCubeFace(tri, idVec3(-1, -1, 1), idVec3(1, -1, 1), idVec3(1, -1, -1), idVec3(-1, -1, -1))
            AddCubeFace(tri, idVec3(-1, 1, 1), idVec3(-1, 1, -1), idVec3(1, 1, -1), idVec3(1, 1, 1))
            tri.generateNormals = true
            AddSurface(surf)
            FinishSurfaces()
        }

        fun LoadASE(fileName: String?): Boolean {
            val ase: aseModel_s?
            ase = Model_ase.ASE_Load(fileName)
            if (ase == null) {
                return false
            }
            ConvertASEToModelSurfaces(ase)
            Model_ase.ASE_Free(ase)
            return true
        }

        fun LoadLWO(fileName: String?): Boolean {
            val failID: IntArray = intArrayOf(0)
            val failPos: IntArray = intArrayOf(0)
            val lwo: lwObject?
            lwo = Model_lwo.lwGetObject(fileName!!, failID, failPos)
            if (null == lwo) {
                return false
            }
            ConvertLWOToModelSurfaces(lwo)
            return true
        }

        /*
         =================
         idRenderModelStatic::LoadFLT

         USGS height map data for megaTexture experiments
         =================
         */
        fun LoadFLT(fileName: String?): Boolean {
            val buffer: Array<ByteBuffer?> = arrayOf(null)
            var data: FloatBuffer?
            val len: Int
            len = fileSystem.ReadFile(fileName!!, buffer)
            if (len <= 0) {
                return false
            }
            val size: Int = sqrt((len / 4.0f)).toInt()
            data = buffer[0]!!.asFloatBuffer()

            // bound the altitudes
            var min = 9999999.0f
            var max: Float = -9999999.0f
            for (i in 0 until (len / 4)) {
                data.put(i, BigFloat(data.get(i)))
                if (data.get(i) == -9999.0f) {
                    data.put(i, 0.0f) // unscanned areas
                }
                if (data.get(i) < min) {
                    min = data.get(i)
                }
                if (data.get(i) > max) {
                    max = data.get(i)
                }
            }
            // write out a gray scale height map
            val image: ByteBuffer = ByteBuffer.allocate(len)
            var image_p = 0
            for (i in 0 until (len / 4)) {
                val v: Float = (data.get(i) - min) / (max - min)
                val gray = (v * 255).toInt().toByte()
                image.put(image_p, gray)
                image.put(image_p + 1, gray)
                image.put(image_p + 2, gray)
                image.put(image_p + 3, 255.toByte())
                image_p += 4
            }
            val tgaName = idStr((fileName))
            tgaName.StripFileExtension()
            tgaName.Append(".tga")
            Image_files.R_WriteTGA(tgaName.toString(), image, size, size, false)

            // find the island above sea level
            var minX: Int
            var maxX: Int
            var minY: Int
            var maxY: Int
            run({
                var i: Int
                minX = 0
                while (minX < size) {
                    i = 0
                    while (i < size) {
                        if (data!!.get(i * size + minX) > 1.0f) {
                            break
                        }
                        i++
                    }
                    if (i != size) {
                        break
                    }
                    minX++
                }
                maxX = size - 1
                while (maxX > 0) {
                    i = 0
                    while (i < size) {
                        if (data!!.get(i * size + maxX) > 1.0f) {
                            break
                        }
                        i++
                    }
                    if (i != size) {
                        break
                    }
                    maxX--
                }
                minY = 0
                while (minY < size) {
                    i = 0
                    while (i < size) {
                        if (data!!.get(minY * size + i) > 1.0f) {
                            break
                        }
                        i++
                    }
                    if (i != size) {
                        break
                    }
                    minY++
                }
                maxY = size - 1
                while (maxY < size) {
                    i = 0
                    while (i < size) {
                        if (data!!.get(maxY * size + i) > 1.0f) {
                            break
                        }
                        i++
                    }
                    if (i != size) {
                        break
                    }
                    maxY--
                }
            })
            val width: Int = maxX - minX + 1
            val height: Int = maxY - minY + 1

            // allocate triangle surface
            val tri: srfTriangles_s = R_AllocStaticTriSurf()
            tri.numVerts = width * height
            tri.numIndexes = (width - 1) * (height - 1) * 6
            fastLoad = true // don't do all the sil processing
            R_AllocStaticTriSurfIndexes(tri, tri.numIndexes)
            R_AllocStaticTriSurfVerts(tri, tri.numVerts)
            for (i in 0 until height) {
                for (j in 0 until width) {
                    val v: Int = i * width + j
                    tri.verts!![v]!!.Clear()
                    tri.verts!![v]!!.xyz[0] = (j * 10).toFloat() // each sample is 10 meters
                    tri.verts!![v]!!.xyz[1] = (-i * 10).toFloat()
                    tri.verts!![v]!!.xyz[2] = data.get(((minY + i) * size) + minX + j) // height is in meters
                    tri.verts!![v]!!.st[0] = j.toFloat() / (width - 1)
                    tri.verts!![v]!!.st[1] = 1.0f - (i.toFloat() / (height - 1))
                }
            }
            for (i in 0 until (height - 1)) {
                for (j in 0 until (width - 1)) {
                    val v: Int = (i * (width - 1) + j) * 6
                    run({
                        tri.indexes!![v + 0] = i * width + j
                        tri.indexes!![v + 1] = (i * width) + j + 1
                        tri.indexes!![v + 2] = ((i + 1) * width) + j + 1
                        tri.indexes!![v + 3] = i * width + j
                        tri.indexes!![v + 4] = ((i + 1) * width) + j + 1
                        tri.indexes!![v + 5] = (i + 1) * width + j
                    })
                }
            }

            data = null
            val surface = modelSurface_s()
            surface.geometry = tri
            surface.id = 0
            surface.shader = tr.defaultMaterial // declManager.FindMaterial( "shaderDemos/megaTexture" );
            AddSurface(surface)
            return true
        }

        fun LoadMA(filename: String?): Boolean {
            val ma: maModel_s?
            ma = Model_ma.MA_Load(filename)
            if (ma == null) {
                return false
            }
            ConvertMAToModelSurfaces(ma)
            Model_ma.MA_Free(ma)
            return true
        }

        override fun oSet(FindModel: idRenderModel?) {
            throw UnsupportedOperationException("Not supported yet.")
        }

        fun ConvertASEToModelSurfaces(ase: aseModel_s?): Boolean {
            var `object`: aseObject_t?
            var mesh: aseMesh_t?
            var material: aseMaterial_t?
            var im1: idMaterial?
            var im2: idMaterial?
            var tri: srfTriangles_s
            var objectNum: Int
            var i: Int
            var j: Int
            var k: Int
            var v: Int
            var tv: Int
            var vRemap: IntArray
            var tvRemap: IntArray
            var mvTable: Array<matchVert_s?> // all of the match verts
            var mvHash: Array<matchVert_s?> // points inside mvTable for each xyz index
            var lastmv: matchVert_s?
            var mv: matchVert_s?
            val normal = idVec3()
            var uOffset: Float
            var vOffset: Float
            var textureSin: Float
            var textureCos: Float
            var uTiling: Float
            var vTiling: Float
            val mergeTo: IntArray
            var color: ByteArray
            val surf = modelSurface_s()
            var modelSurf: modelSurface_s?
            if (ase == null) {
                return false
            }
            if (ase.objects.Num() < 1) {
                return false
            }
            timeStamp[0] = ase.timeStamp[0]

            // the modeling programs can save out multiple surfaces with a common
            // material, but we would like to mege them together where possible
            // meaning that this.NumSurfaces() <= ase.objects.currentElements
            mergeTo = IntArray(ase.objects.Num())
            surf.geometry = null
            if (ase.materials.Num() == 0) {
                // if we don't have any materials, dump everything into a single surface
                surf.shader = tr.defaultMaterial
                surf.id = 0
                AddSurface(surf)
                i = 0
                while (i < ase.objects.Num()) {
                    mergeTo[i] = 0
                    i++
                }
            } else if (!r_mergeModelSurfaces.GetBool()) {
                // don't merge any
                i = 0
                while (i < ase.objects.Num()) {
                    mergeTo[i] = i
                    `object` = ase.objects[i]
                    material = ase.materials[`object`!!.materialRef]
                    surf.shader = DeclManager.declManager.FindMaterial(ctos(material!!.name))
                    surf.id = NumSurfaces()
                    AddSurface(surf)
                    i++
                }
            } else {
                // search for material matches
                i = 0
                while (i < ase.objects.Num()) {
                    `object` = ase.objects[i]
                    material = ase.materials[`object`!!.materialRef]
                    im1 = DeclManager.declManager.FindMaterial(ctos(material!!.name))
                    if (im1!!.IsDiscrete()) {
                        // flares, autosprites, etc
                        j = NumSurfaces()
                    } else {
                        j = 0
                        while (j < NumSurfaces()) {
                            modelSurf = surfaces[j]
                            im2 = modelSurf!!.shader
                            if (im1 === im2) {
                                // merge this
                                mergeTo[i] = j
                                break
                            }
                            j++
                        }
                    }
                    if (j == NumSurfaces()) {
                        // didn't merge
                        mergeTo[i] = j
                        surf.shader = im1
                        surf.id = NumSurfaces()
                        AddSurface(surf)
                    }
                    i++
                }
            }
            val vertexSubset: idVectorSubset<idVec3> = idVectorSubset(3)
            val texCoordSubset: idVectorSubset<idVec2> = idVectorSubset(2)

            // build the surfaces
            objectNum = 0
            while (objectNum < ase.objects.Num()) {
                `object` = ase.objects[objectNum]
                mesh = `object`!!.mesh
                material = if (ase.materials.Num() > `object`.materialRef) ase.materials[`object`.materialRef] else null
                im1 =
                    DeclManager.declManager.FindMaterial(if (material != null) idStr(ctos(material!!.name)) else null as idStr?)
                var normalsParsed: Boolean = mesh.normalsParsed

                // completely ignore any explict normals on surfaces with a renderbump command
                // which will guarantee the best contours and least vertexes.
                val rb: String = im1!!.GetRenderBump()
                if (rb != null && !rb.isEmpty()) {
                    normalsParsed = false
                }

                // It seems like the tools our artists are using often generate
                // verts and texcoords slightly separated that should be merged
                // note that we really should combine the surfaces with common materials
                // before doing this operation, because we can miss a slop combination
                // if they are in different surfaces
                vRemap = IntArray(mesh.numVertexes)
                if (fastLoad) {
                    // renderbump doesn't care about vertex count
                    j = 0
                    while (j < mesh.numVertexes) {
                        vRemap[j] = j
                        j++
                    }
                } else {
                    val vertexEpsilon: Float = r_slopVertex.GetFloat()
                    val expand: Float = 2 * 32 * vertexEpsilon
                    val mins = idVec3()
                    val maxs = idVec3()
                    SIMDProcessor!!.MinMax(mins, maxs, mesh.vertexes as Array<idVec3>, mesh.numVertexes)
                    mins.minusAssign(idVec3(expand, expand, expand))
                    maxs.plusAssign(idVec3(expand, expand, expand))
                    vertexSubset.Init(mins, maxs, 32, 1024)
                    j = 0
                    while (j < mesh.numVertexes) {
                        vRemap[j] = vertexSubset.FindVector(mesh.vertexes as Array<idVec<*>>, j, vertexEpsilon)
                        j++
                    }
                }
                tvRemap = IntArray(mesh.numTVertexes)
                if (fastLoad) {
                    // renderbump doesn't care about vertex count
                    j = 0
                    while (j < mesh.numTVertexes) {
                        tvRemap[j] = j
                        j++
                    }
                } else {
                    val texCoordEpsilon: Float = r_slopTexCoord.GetFloat()
                    val expand: Float = 2 * 32 * texCoordEpsilon
                    val mins = idVec2()
                    val maxs = idVec2()
                    SIMDProcessor!!.MinMax(mins, maxs, mesh.tvertexes as Array<idVec2>, mesh.numTVertexes)
                    mins.minusAssign(idVec2(expand, expand))
                    maxs.plusAssign(idVec2(expand, expand))
                    texCoordSubset.Init(mins, maxs, 32, 1024)
                    j = 0
                    while (j < mesh.numTVertexes) {
                        tvRemap[j] =
                            texCoordSubset.FindVector(mesh.tvertexes as Array<idVec<*>>, j, texCoordEpsilon)
                        j++
                    }
                }

                // we need to find out how many unique vertex / texcoord combinations
                // there are, because ASE tracks them separately but we need them unified
                // the maximum possible number of combined vertexes is the number of indexes
                mvTable = arrayOfNulls(mesh.numFaces * 3)

                // we will have a hash chain based on the xyz values
                mvHash = arrayOfNulls(mesh.numVertexes)

                // allocate triangle surface
                tri = R_AllocStaticTriSurf()
                tri.numVerts = 0
                tri.numIndexes = 0
                R_AllocStaticTriSurfIndexes(tri, mesh.numFaces * 3)
                tri.generateNormals = !normalsParsed

                // init default normal, color and tex coord index
                normal.Zero()
                color = identityColor
                tv = 0

                // find all the unique combinations
                val normalEpsilon: Float = 1.0f - r_slopNormal.GetFloat()
                j = 0
                while (j < mesh.numFaces) {
                    k = 0
                    while (k < 3) {
                        v = mesh.faces!![j]!!.vertexNum[k]
                        if (v < 0 || v >= mesh.numVertexes) {
                            Common.common.Error("ConvertASEToModelSurfaces: bad vertex index in ASE file %s", name)
                        }

                        // collapse the position if it was slightly offset
                        v = vRemap[v]

                        // we may or may not have texcoords to compare
                        if (mesh.numTVFaces == mesh.numFaces && mesh.numTVertexes != 0) {
                            tv = mesh.faces!![j]!!.tVertexNum[k]
                            if (tv < 0 || tv >= mesh.numTVertexes) {
                                Common.common.Error(
                                    "ConvertASEToModelSurfaces: bad tex coord index in ASE file %s",
                                    name
                                )
                            }
                            // collapse the tex coord if it was slightly offset
                            tv = tvRemap[tv]
                        }

                        // we may or may not have normals to compare
                        if (normalsParsed) {
                            normal.set((mesh.faces!![j]!!.vertexNormals[k]))
                        }

                        // we may or may not have colors to compare
                        if (mesh.colorsParsed) {
                            color = mesh.faces!![j]!!.vertexColors[k]
                        }

                        // find a matching vert
                        lastmv = null
                        mv = mvHash[v]
                        while (mv != null) {
                            if (mv.tv != tv) {
                                lastmv = mv
                                mv = mv.next
                                continue
                            }
                            if (!mv.color.contentEquals(color)) {
                                lastmv = mv
                                mv = mv.next
                                continue
                            }
                            if (!normalsParsed) {
                                // if we are going to create the normals, just
                                // matching texcoords is enough
                                break
                            }
                            if (mv.normal.times(normal) > normalEpsilon) {
                                break // we already have this one
                            }
                            lastmv = mv
                            mv = mv.next
                        }
                        if (null == mv) {
                            // allocate a new match vert and link to hash chain
                            mvTable[tri.numVerts] = matchVert_s(tri.numVerts)
                            mv = mvTable[tri.numVerts]
                            mv!!.v = v
                            mv.tv = tv
                            mv.normal.set(normal)
                            System.arraycopy(color, 0, mv.color, 0, color.size)
                            mv.next = null
                            if (lastmv != null) {
                                lastmv.next = mv
                            } else {
                                mvHash[v] = mv
                            }
                            tri.numVerts++
                        }
                        tri.indexes!![tri.numIndexes] = mv.index
                        tri.numIndexes++
                        k++
                    }
                    j++
                }

                // allocate space for the indexes and copy them
                if (tri.numIndexes > mesh.numFaces * 3) {
                    Common.common.FatalError("ConvertASEToModelSurfaces: index miscount in ASE file %s", name)
                }
                if (tri.numVerts > mesh.numFaces * 3) {
                    Common.common.FatalError("ConvertASEToModelSurfaces: vertex miscount in ASE file %s", name)
                }

                // an ASE allows the texture coordinates to be scaled, translated, and rotated
                if (ase.materials.Num() == 0) {
                    vOffset = 0.0f
                    uOffset = vOffset
                    vTiling = 1.0f
                    uTiling = vTiling
                    textureSin = 0.0f
                    textureCos = 1.0f
                } else {
                    material = ase.materials[`object`.materialRef]
                    uOffset = -material!!.uOffset
                    vOffset = material.vOffset
                    uTiling = material.uTiling
                    vTiling = material.vTiling
                    textureSin = Sin(material.angle)
                    textureCos = Cos(material.angle)
                }

                // now allocate and generate the combined vertexes
                R_AllocStaticTriSurfVerts(tri, tri.numVerts)
                j = 0
                while (j < tri.numVerts) {
                    mv = mvTable[j]
                    tri.verts!![j]!!.Clear()
                    tri.verts!![j]!!.xyz.set((mesh.vertexes!![mv!!.v]))
                    tri.verts!![j]!!.normal.set(mv.normal)
                    System.arraycopy(mv.color, 0, mv.color.also({ tri.verts!![j]!!.color = it }), 0, mv.color.size)
                    if (mesh.numTVFaces == mesh.numFaces && mesh.numTVertexes != 0) {
                        val tv2 = idVec2(mesh.tvertexes!![mv.tv])
                        val u: Float = tv2.x * uTiling + uOffset
                        val V: Float = tv2.y * vTiling + vOffset
                        tri.verts!![j]!!.st[0] = u * textureCos + V * textureSin
                        tri.verts!![j]!!.st[1] = u * -textureSin + V * textureCos
                    }
                    j++
                }

                // see if we need to merge with a previous surface of the same material
                modelSurf = surfaces[mergeTo[objectNum]]
                val mergeTri: srfTriangles_s? = modelSurf!!.geometry
                if (null == mergeTri) {
                    modelSurf.geometry = tri
                } else {
                    modelSurf.geometry = R_MergeTriangles(mergeTri, tri)
                    R_FreeStaticTriSurf(tri)
                    R_FreeStaticTriSurf(mergeTri)
                }
                objectNum++
            }
            return true
        }

        fun ConvertLWOToModelSurfaces(lwo: lwObject?): Boolean {
            var im1: idMaterial?
            var im2: idMaterial?
            var tri: srfTriangles_s
            var lwoSurf: lwSurface?
            var numTVertexes: Int
            var i: Int
            var j: Int
            var k: Int
            var v: Int
            var tv: Int
            val vRemap: IntArray
            val tvList: Array<idVec2>
            val tvRemap: IntArray
            var mvTable: Array<matchVert_s?> // all of the match verts
            var mvHash: Array<matchVert_s?> // points inside mvTable for each xyz index
            var lastmv: matchVert_s?
            var mv: matchVert_s?
            val normal = idVec3()
            val mergeTo: IntArray
            val color = ByteArray(4)
            var surf: modelSurface_s
            var modelSurf: modelSurface_s?
            if (lwo == null) {
                return false
            }
            if (lwo.surf == null) {
                return false
            }
            timeStamp[0] = lwo.timeStamp[0]

            // count the number of surfaces
            i = 0
            lwoSurf = lwo.surf
            while (lwoSurf != null) {
                i++
                lwoSurf = lwoSurf.next
            }

            // the modeling programs can save out multiple surfaces with a common
            // material, but we would like to merge them together where possible
            mergeTo = IntArray(i)
            if (!r_mergeModelSurfaces.GetBool()) {
                // don't merge any
                lwoSurf = lwo.surf
                i = 0
                while (lwoSurf != null) {
                    surf = modelSurface_s()
                    mergeTo[i] = i
                    surf.shader = DeclManager.declManager.FindMaterial((lwoSurf.name)!!)
                    surf.id = NumSurfaces()
                    AddSurface(surf)
                    lwoSurf = lwoSurf.next
                    i++
                }
            } else {
                // search for material matches
                lwoSurf = lwo.surf
                i = 0
                while (lwoSurf != null) {
                    surf = modelSurface_s()
                    im1 = DeclManager.declManager.FindMaterial((lwoSurf.name)!!)
                    if (im1!!.IsDiscrete()) {
                        // flares, autosprites, etc
                        j = NumSurfaces()
                    } else {
                        j = 0
                        while (j < NumSurfaces()) {
                            modelSurf = surfaces[j]
                            im2 = modelSurf!!.shader
                            if (im1 === im2) {
                                // merge this
                                mergeTo[i] = j
                                break
                            }
                            j++
                        }
                    }
                    if (j == NumSurfaces()) {
                        // didn't merge
                        mergeTo[i] = j
                        surf.shader = im1
                        surf.id = NumSurfaces()
                        AddSurface(surf)
                    }
                    lwoSurf = lwoSurf.next
                    i++
                }
            }
            val vertexSubset: idVectorSubset<idVec3> = idVectorSubset(3)
            val texCoordSubset: idVectorSubset<idVec2> = idVectorSubset(2)

            // we only ever use the first layer
            val layer: lwLayer? = lwo.layer

            // vertex positions
            if (layer!!.point.count <= 0) {
                Common.common.Warning("ConvertLWOToModelSurfaces: model '%s' has bad or missing vertex data", name)
                return false
            }
            val vList: Array<idVec3> =
                idVec3.generateArray(layer.point.count)
            j = 0
            while (j < layer.point.count) {
                vList[j].set(
                    idVec3(
                        layer.point.pt!![j]!!.pos[0],
                        layer.point.pt!![j]!!.pos[2],
                        layer.point.pt!![j]!!.pos[1]
                    )
                )
                j++
            }

            // vertex texture coords
            numTVertexes = 0
            if (layer.nvmaps != 0) {
                var vm: lwVMap? = layer.vmap
                while (vm != null) {
                    if (vm.type == Model_lwo.LWID_('T', 'X', 'U', 'V').toLong()) {
                        numTVertexes += vm.nverts
                    }
                    vm = vm.next
                }
            }
            if (numTVertexes != 0) {
                tvList = idVec2.generateArray(numTVertexes)
                var offset = 0
                var vm: lwVMap? = layer.vmap
                while (vm != null) {
                    if (vm.type == Model_lwo.LWID_('T', 'X', 'U', 'V').toLong()) {
                        vm.offset = offset
                        k = 0
                        while (k < vm.nverts) {
                            tvList[k + offset].x = vm.value!![k][0]
                            tvList[k + offset].y = 1.0f - vm.value!![k][1] // invert the t
                            k++
                        }
                        offset += vm.nverts
                    }
                    vm = vm.next
                }
            } else {
                Common.common.Warning("ConvertLWOToModelSurfaces: model '%s' has bad or missing uv data", name)
                numTVertexes = 1
                tvList = Array(numTVertexes) { idVec2() }
            }

            // It seems like the tools our artists are using often generate
            // verts and texcoords slightly separated that should be merged
            // note that we really should combine the surfaces with common materials
            // before doing this operation, because we can miss a slop combination
            // if they are in different surfaces
            vRemap = IntArray(layer.point.count)
            if (fastLoad) {
                // renderbump doesn't care about vertex count
                j = 0
                while (j < layer.point.count) {
                    vRemap[j] = j
                    j++
                }
            } else {
                val vertexEpsilon: Float = r_slopVertex.GetFloat()
                val expand: Float = 2 * 32 * vertexEpsilon
                val mins = idVec3()
                val maxs = idVec3()
                SIMDProcessor!!.MinMax(mins, maxs, vList, layer.point.count)
                mins.minusAssign(idVec3(expand, expand, expand))
                maxs.plusAssign(idVec3(expand, expand, expand))
                vertexSubset.Init(mins, maxs, 32, 1024)
                j = 0
                while (j < layer.point.count) {
                    vRemap[j] = vertexSubset.FindVector(vList as Array<idVec<*>>, j, vertexEpsilon)
                    j++
                }
            }
            tvRemap = IntArray(numTVertexes)
            if (fastLoad) {
                // renderbump doesn't care about vertex count
                j = 0
                while (j < numTVertexes) {
                    tvRemap[j] = j
                    j++
                }
            } else {
                val texCoordEpsilon: Float = r_slopTexCoord.GetFloat()
                val expand: Float = 2 * 32 * texCoordEpsilon
                val mins = idVec2()
                val maxs = idVec2()
                SIMDProcessor!!.MinMax(mins, maxs, tvList as Array<idVec2>, numTVertexes)
                mins.minusAssign(idVec2(expand, expand))
                maxs.plusAssign(idVec2(expand, expand))
                texCoordSubset.Init(mins, maxs, 32, 1024)
                j = 0
                while (j < numTVertexes) {
                    tvRemap[j] = texCoordSubset.FindVector(tvList as Array<idVec<*>>, j, texCoordEpsilon)
                    j++
                }
            }

            // build the surfaces
            lwoSurf = lwo.surf
            i = 0
            while (lwoSurf != null) {
                im1 = DeclManager.declManager.FindMaterial((lwoSurf.name)!!)
                var normalsParsed = true

                // completely ignore any explict normals on surfaces with a renderbump command
                // which will guarantee the best contours and least vertexes.
                val rb: String = im1!!.GetRenderBump()
                if (rb != null && !rb.isEmpty()) {
                    normalsParsed = false
                }

                // we need to find out how many unique vertex / texcoord combinations there are
                // the maximum possible number of combined vertexes is the number of indexes
                mvTable = arrayOfNulls(layer.polygon.count * 3)

                // we will have a hash chain based on the xyz values
                mvHash =
                    arrayOfNulls(layer.point.count)

                // allocate triangle surface
                tri = R_AllocStaticTriSurf()
                tri.numVerts = 0
                tri.numIndexes = 0
                R_AllocStaticTriSurfIndexes(tri, layer.polygon.count * 3)
                tri.generateNormals = !normalsParsed

                // find all the unique combinations
                var normalEpsilon: Float
                if (fastLoad) {
                    normalEpsilon = 1.0f // don't merge unless completely exact
                } else {
                    normalEpsilon = 1.0f - r_slopNormal.GetFloat()
                }
                j = 0
                while (j < layer.polygon.count) {
                    val poly: lwPolygon? = layer.polygon.pol!![j]
                    if (!(poly!!.surf == lwoSurf)) {
                        j++
                        continue
                    }
                    if (poly.nverts != 3) {
                        Common.common.Warning(
                            "ConvertLWOToModelSurfaces: model %s has too many verts for a poly! Make sure you triplet it down",
                            name
                        )
                        j++
                        continue
                    }
                    k = 0
                    while (k < 3) {
                        v = vRemap[poly.getV(k)!!.index]
                        normal.x = poly.getV(k)!!.norm[0]
                        normal.y = poly.getV(k)!!.norm[2]
                        normal.z = poly.getV(k)!!.norm[1]

                        // LWO models aren't all that pretty when it comes down to the floating point values they store
                        normal.FixDegenerateNormal()
                        tv = 0
                        color[0] = (lwoSurf.color.rgb[0] * 255).toInt().toByte()
                        color[1] = (lwoSurf.color.rgb[1] * 255).toInt().toByte()
                        color[2] = (lwoSurf.color.rgb[2] * 255).toInt().toByte()
                        color[3] = 255.toByte()

                        // first set attributes from the vertex
                        val pt: lwPoint? = layer.point.pt!![poly.getV(k)!!.index]
                        var nvm: Int
                        nvm = 0
                        while (nvm < pt!!.nvmaps) {
                            val vm: lwVMapPt = pt.vm!![nvm]
                            if (vm!!.vmap.type == Model_lwo.LWID_('T', 'X', 'U', 'V').toLong()) {
                                tv = tvRemap[vm.index + vm.vmap.offset]
                            }
                            if (vm.vmap.type == Model_lwo.LWID_('R', 'G', 'B', 'A').toLong()) {
                                for (chan in 0..3) {
                                    color[chan] =
                                        (255 * vm.vmap.value!![vm.index][chan]).toInt().toByte()
                                }
                            }
                            nvm++
                        }

                        // then override with polygon attributes
                        nvm = 0
                        while (nvm < poly.getV(k)!!.nvmaps) {
                            val vm: lwVMapPt = poly.getV(k)!!.vm!![nvm]
                            if (vm!!.vmap.type == Model_lwo.LWID_('T', 'X', 'U', 'V').toLong()) {
                                tv = tvRemap[vm.index + vm.vmap.offset]
                            }
                            if (vm.vmap.type == Model_lwo.LWID_('R', 'G', 'B', 'A').toLong()) {
                                for (chan in 0..3) {
                                    color[chan] =
                                        (255 * vm.vmap.value!![vm.index][chan]).toInt().toByte()
                                }
                            }
                            nvm++
                        }

                        // find a matching vert
                        lastmv = null
                        mv = mvHash[v]
                        while (mv != null) {
                            if (mv.tv != tv) {
                                lastmv = mv
                                mv = mv.next
                                continue
                            }
                            if (!mv.color.contentEquals(color)) {
                                lastmv = mv
                                mv = mv.next
                                continue
                            }
                            if (!normalsParsed) {
                                // if we are going to create the normals, just
                                // matching texcoords is enough
                                break
                            }
                            if (mv.normal.times(normal) > normalEpsilon) {
                                break // we already have this one
                            }
                            lastmv = mv
                            mv = mv.next
                        }
                        if (null == mv) {
                            // allocate a new match vert and link to hash chain
                            mvTable[tri.numVerts] = matchVert_s(tri.numVerts)
                            mv = mvTable[tri.numVerts]
                            mv!!.v = v
                            mv.tv = tv
                            mv.normal.set(normal)
                            System.arraycopy(color, 0, mv.color, 0, color.size)
                            mv.next = null
                            if (lastmv != null) {
                                lastmv.next = mv
                            } else {
                                mvHash[v] = mv
                            }
                            tri.numVerts++
                        }
                        tri.indexes!![tri.numIndexes] = mv.index
                        tri.numIndexes++
                        k++
                    }
                    j++
                }

                // allocate space for the indexes and copy them
                if (tri.numIndexes > layer.polygon.count * 3) {
                    Common.common.FatalError("ConvertLWOToModelSurfaces: index miscount in LWO file %s", name)
                }
                if (tri.numVerts > layer.polygon.count * 3) {
                    Common.common.FatalError("ConvertLWOToModelSurfaces: vertex miscount in LWO file %s", name)
                }

                // now allocate and generate the combined vertexes
                R_AllocStaticTriSurfVerts(tri, tri.numVerts)
                j = 0
                while (j < tri.numVerts) {
                    mv = mvTable[j]
                    tri.verts!![j]!!.Clear()
                    tri.verts!![j]!!.xyz.set(vList[mv!!.v])
                    tri.verts!![j]!!.st.set(tvList[mv.tv])
                    tri.verts!![j]!!.normal.set(mv.normal)
                    tri.verts!![j]!!.color = mv.color
                    j++
                }

                // see if we need to merge with a previous surface of the same material
                modelSurf = surfaces[mergeTo[i]]
                val mergeTri: srfTriangles_s? = modelSurf!!.geometry
                if (null == mergeTri) {
                    modelSurf.geometry = tri
                } else {
                    modelSurf.geometry = R_MergeTriangles(mergeTri, tri)
                    R_FreeStaticTriSurf(tri)
                    R_FreeStaticTriSurf(mergeTri)
                }
                lwoSurf = lwoSurf.next
                i++
            }
            return true
        }

        fun ConvertMAToModelSurfaces(ma: maModel_s?): Boolean {
            var `object`: maObject_t?
            var mesh: maMesh_t?
            var material: maMaterial_t?
            var im1: idMaterial?
            var im2: idMaterial?
            var tri: srfTriangles_s
            var objectNum: Int
            var i: Int
            var j: Int
            var k: Int
            var v: Int
            var tv: Int
            var vRemap: IntArray
            var tvRemap: IntArray
            var mvTable: Array<matchVert_s?> // all of the match verts
            var mvHash: Array<matchVert_s?> // points inside mvTable for each xyz index
            var lastmv: matchVert_s?
            var mv: matchVert_s?
            val normal = idVec3()
            var uOffset: Float
            var vOffset: Float
            var textureSin: Float
            var textureCos: Float
            var uTiling: Float
            var vTiling: Float
            val mergeTo: IntArray
            var color: ByteArray
            val surf = modelSurface_s()
            var modelSurf: modelSurface_s?
            if (ma == null) {
                return false
            }
            if (ma.objects.Num() < 1) {
                return false
            }
            timeStamp[0] = ma.timeStamp[0]

            // the modeling programs can save out multiple surfaces with a common
            // material, but we would like to mege them together where possible
            // meaning that this.NumSurfaces() <= ma.objects.currentElements
            mergeTo = IntArray(ma.objects.Num())
            surf.geometry = null
            if (ma.materials.Num() == 0) {
                // if we don't have any materials, dump everything into a single surface
                surf.shader = tr.defaultMaterial
                surf.id = 0
                AddSurface(surf)
                i = 0
                while (i < ma.objects.Num()) {
                    mergeTo[i] = 0
                    i++
                }
            } else if (!r_mergeModelSurfaces.GetBool()) {
                // don't merge any
                i = 0
                while (i < ma.objects.Num()) {
                    mergeTo[i] = i
                    `object` = ma.objects[i]
                    if (`object`!!.materialRef >= 0) {
                        material = ma.materials[`object`.materialRef]
                        surf.shader = DeclManager.declManager.FindMaterial((material!!.name)!!)
                    } else {
                        surf.shader = tr.defaultMaterial
                    }
                    surf.id = NumSurfaces()
                    AddSurface(surf)
                    i++
                }
            } else {
                // search for material matches
                i = 0
                while (i < ma.objects.Num()) {
                    `object` = ma.objects[i]
                    if (`object`!!.materialRef >= 0) {
                        material = ma.materials[`object`.materialRef]
                        im1 = DeclManager.declManager.FindMaterial((material!!.name)!!)
                    } else {
                        im1 = tr.defaultMaterial
                    }
                    if (im1!!.IsDiscrete()) {
                        // flares, autosprites, etc
                        j = NumSurfaces()
                    } else {
                        j = 0
                        while (j < NumSurfaces()) {
                            modelSurf = surfaces[j]
                            im2 = modelSurf!!.shader
                            if (im1 === im2) {
                                // merge this
                                mergeTo[i] = j
                                break
                            }
                            j++
                        }
                    }
                    if (j == NumSurfaces()) {
                        // didn't merge
                        mergeTo[i] = j
                        surf.shader = im1
                        surf.id = NumSurfaces()
                        AddSurface(surf)
                    }
                    i++
                }
            }
            val vertexSubset: idVectorSubset<idVec3> = idVectorSubset(3)
            val texCoordSubset: idVectorSubset<idVec2> = idVectorSubset(2)

            // build the surfaces
            objectNum = 0
            while (objectNum < ma.objects.Num()) {
                `object` = ma.objects[objectNum]
                mesh = `object`!!.mesh
                if (`object`.materialRef >= 0) {
                    material = ma.materials[`object`.materialRef]
                    im1 = DeclManager.declManager.FindMaterial((material!!.name)!!)
                } else {
                    im1 = tr.defaultMaterial
                }
                var normalsParsed: Boolean = mesh!!.normalsParsed

                // completely ignore any explict normals on surfaces with a renderbump command
                // which will guarantee the best contours and least vertexes.
                val rb: String = im1!!.GetRenderBump()
                if (rb != null && !rb.isEmpty()) {
                    normalsParsed = false
                }

                // It seems like the tools our artists are using often generate
                // verts and texcoords slightly separated that should be merged
                // note that we really should combine the surfaces with common materials
                // before doing this operation, because we can miss a slop combination
                // if they are in different surfaces
                vRemap = IntArray(mesh.numVertexes)
                if (fastLoad) {
                    // renderbump doesn't care about vertex count
                    j = 0
                    while (j < mesh.numVertexes) {
                        vRemap[j] = j
                        j++
                    }
                } else {
                    val vertexEpsilon: Float = r_slopVertex.GetFloat()
                    val expand: Float = 2 * 32 * vertexEpsilon
                    val mins = idVec3()
                    val maxs = idVec3()
                    SIMDProcessor!!.MinMax(mins, maxs, mesh.vertexes as Array<DrawVert.idDrawVert>, mesh.numVertexes)
                    mins.minusAssign(idVec3(expand, expand, expand))
                    maxs.plusAssign(idVec3(expand, expand, expand))
                    vertexSubset.Init(mins, maxs, 32, 1024)
                    j = 0
                    while (j < mesh.numVertexes) {
                        vRemap[j] = vertexSubset.FindVector(mesh.vertexes as Array<idVec<*>>, j, vertexEpsilon)
                        j++
                    }
                }
                tvRemap = IntArray(mesh.numTVertexes)
                if (fastLoad) {
                    // renderbump doesn't care about vertex count
                    j = 0
                    while (j < mesh.numTVertexes) {
                        tvRemap[j] = j
                        j++
                    }
                } else {
                    val texCoordEpsilon: Float = r_slopTexCoord.GetFloat()
                    val expand: Float = 2 * 32 * texCoordEpsilon
                    val mins = idVec2()
                    val maxs = idVec2()
                    SIMDProcessor!!.MinMax(mins, maxs, mesh.tvertexes as Array<idVec2>, mesh.numTVertexes)
                    mins.minusAssign(idVec2(expand, expand))
                    maxs.plusAssign(idVec2(expand, expand))
                    texCoordSubset.Init(mins, maxs, 32, 1024)
                    j = 0
                    while (j < mesh.numTVertexes) {
                        tvRemap[j] =
                            texCoordSubset.FindVector(mesh.tvertexes as Array<idVec<*>>, j, texCoordEpsilon)
                        j++
                    }
                }

                // we need to find out how many unique vertex / texcoord / color combinations
                // there are, because MA tracks them separately but we need them unified
                // the maximum possible number of combined vertexes is the number of indexes
                mvTable =
                    arrayOfNulls(mesh.numFaces * 3)

                // we will have a hash chain based on the xyz values
                mvHash =
                    arrayOfNulls(mesh.numVertexes)

                // allocate triangle surface
                tri = R_AllocStaticTriSurf()
                tri.numVerts = 0
                tri.numIndexes = 0
                R_AllocStaticTriSurfIndexes(tri, mesh.numFaces * 3)
                tri.generateNormals = !normalsParsed

                // init default normal, color and tex coord index
                normal.Zero()
                color = identityColor
                tv = 0

                // find all the unique combinations
                val normalEpsilon: Float = 1.0f - r_slopNormal.GetFloat()
                j = 0
                while (j < mesh.numFaces) {
                    k = 0
                    while (k < 3) {
                        v = mesh.faces!![j]!!.vertexNum[k]
                        if (v < 0 || v >= mesh.numVertexes) {
                            Common.common.Error("ConvertMAToModelSurfaces: bad vertex index in MA file %s", name)
                        }

                        // collapse the position if it was slightly offset
                        v = vRemap[v]

                        // we may or may not have texcoords to compare
                        if (mesh.numTVertexes != 0) {
                            tv = mesh.faces!![j]!!.tVertexNum[k]
                            if (tv < 0 || tv >= mesh.numTVertexes) {
                                Common.common.Error("ConvertMAToModelSurfaces: bad tex coord index in MA file %s", name)
                            }
                            // collapse the tex coord if it was slightly offset
                            tv = tvRemap[tv]
                        }

                        // we may or may not have normals to compare
                        if (normalsParsed) {
                            normal.set((mesh.faces!![j]!!.vertexNormals[k])!!)
                        }

                        // we may or may not have colors to compare
                        if (mesh.faces!![j]!!.vertexColors[k] != -1 && mesh.faces!![j]!!.vertexColors[k] != -999) {
                            val offset: Int = mesh.faces!![j]!!.vertexColors[k] * 4
                            color = Arrays.copyOfRange(mesh.colors, offset, offset + 4)
                        }

                        // find a matching vert
                        lastmv = null
                        mv = mvHash[v]
                        while (mv != null) {
                            if (mv.tv != tv) {
                                lastmv = mv
                                mv = mv.next
                                continue
                            }
                            if (!mv.color.contentEquals(color)) {
                                lastmv = mv
                                mv = mv.next
                                continue
                            }
                            if (!normalsParsed) {
                                // if we are going to create the normals, just
                                // matching texcoords is enough
                                break
                            }
                            if (mv.normal.times(normal) > normalEpsilon) {
                                break // we already have this one
                            }
                            lastmv = mv
                            mv = mv.next
                        }
                        if (null == mv) {
                            // allocate a new match vert and link to hash chain
                            mvTable[tri.numVerts] = matchVert_s(tri.numVerts)
                            mv = mvTable[tri.numVerts]
                            mv!!.v = v
                            mv.tv = tv
                            mv.normal.set(normal)
                            System.arraycopy(color, 0, mv.color, 0, color.size)
                            mv.next = null
                            if (lastmv != null) {
                                lastmv.next = mv
                            } else {
                                mvHash[v] = mv
                            }
                            tri.numVerts++
                        }
                        tri.indexes!![tri.numIndexes] = mv.index
                        tri.numIndexes++
                        k++
                    }
                    j++
                }

                // allocate space for the indexes and copy them
                if (tri.numIndexes > mesh.numFaces * 3) {
                    Common.common.FatalError("ConvertMAToModelSurfaces: index miscount in MA file %s", name)
                }
                if (tri.numVerts > mesh.numFaces * 3) {
                    Common.common.FatalError("ConvertMAToModelSurfaces: vertex miscount in MA file %s", name)
                }

                // an MA allows the texture coordinates to be scaled, translated, and rotated
                vOffset = 0.0f
                uOffset = vOffset
                vTiling = 1.0f
                uTiling = vTiling
                textureSin = 0.0f
                textureCos = 1.0f

                // now allocate and generate the combined vertexes
                R_AllocStaticTriSurfVerts(tri, tri.numVerts)
                j = 0
                while (j < tri.numVerts) {
                    mv = mvTable[j]
                    tri.verts!![j]!!.Clear()
                    tri.verts!![j]!!.xyz.set((mesh.vertexes!![mv!!.v]))
                    tri.verts!![j]!!.normal.set(mv.normal)
                    tri.verts!![j]!!.color = mv.color
                    if (mesh.numTVertexes != 0) {
                        val tv2 = idVec2(mesh.tvertexes!![mv.tv])
                        val U: Float = tv2.x * uTiling + uOffset
                        val V: Float = tv2.y * vTiling + vOffset
                        tri.verts!![j]!!.st[0] = U * textureCos + V * textureSin
                        tri.verts!![j]!!.st[1] = U * -textureSin + V * textureCos
                    }
                    j++
                }

                // see if we need to merge with a previous surface of the same material
                modelSurf = surfaces[mergeTo[objectNum]]
                val mergeTri: srfTriangles_s? = modelSurf!!.geometry
                if (null == mergeTri) {
                    modelSurf.geometry = tri
                } else {
                    modelSurf.geometry = R_MergeTriangles(mergeTri, tri)
                    R_FreeStaticTriSurf(tri)
                    R_FreeStaticTriSurf(mergeTri)
                }
                objectNum++
            }
            return true
        }

        fun ConvertLWOToASE(obj: lwObject?, fileName: String?): aseModel_s? {
            var j: Int
            var k: Int
            val ase: aseModel_s
            if (obj == null) {
                return null
            }

            // NOTE: using new operator because aseModel_s contains idList class objects
            ase = aseModel_s()
            ase.timeStamp[0] = obj.timeStamp[0]
            ase.objects.Resize(obj.nlayers, obj.nlayers)
            var materialRef = 0
            var surf: lwSurface? = obj.surf
            while (surf != null) {
                val mat = aseMaterial_t()
                System.arraycopy(surf.name!!.toCharArray(), 0, mat.name, 0, surf.name!!.length)
                mat.vTiling = 1.0f
                mat.uTiling = mat.vTiling
                mat.vOffset = 0.0f
                mat.uOffset = mat.vOffset
                mat.angle = mat.uOffset
                ase.materials.Append(mat)
                val layer: lwLayer? = obj.layer
                val `object` = aseObject_t()
                `object`.materialRef = materialRef++
                val mesh: aseMesh_t = `object`.mesh
                ase.objects.Append(`object`)
                mesh!!.numFaces = layer!!.polygon.count
                mesh.numTVFaces = mesh.numFaces
                mesh.faces = arrayOfNulls(mesh.numFaces)
                mesh.numVertexes = layer.point.count
                mesh.vertexes =
                    idVec3.generateArray(mesh.numVertexes)

                // vertex positions
                if (layer.point.count <= 0) {
                    Common.common.Warning("ConvertLWOToASE: model '%s' has bad or missing vertex data", name)
                }
                j = 0
                while (j < layer.point.count) {
                    mesh.vertexes!![j].x = layer.point.pt!![j]!!.pos[0]
                    mesh.vertexes!![j].y = layer.point.pt!![j]!!.pos[2]
                    mesh.vertexes!![j].z = layer.point.pt!![j]!!.pos[1]
                    j++
                }

                // vertex texture coords
                mesh.numTVertexes = 0
                if (layer.nvmaps != 0) {
                    var vm: lwVMap? = layer.vmap
                    while (vm != null) {
                        if (vm.type == Model_lwo.LWID_('T', 'X', 'U', 'V').toLong()) {
                            mesh.numTVertexes += vm.nverts
                        }
                        vm = vm.next
                    }
                }
                if (mesh.numTVertexes != 0) {
                    mesh.tvertexes =
                        Array(mesh.numTVertexes) { idVec2() }
                    var offset = 0
                    var vm: lwVMap? = layer.vmap
                    while (vm != null) {
                        if (vm.type == Model_lwo.LWID_('T', 'X', 'U', 'V').toLong()) {
                            vm.offset = offset
                            k = 0
                            while (k < vm.nverts) {
                                mesh.tvertexes!![k + offset].x = vm.value!![k][0]
                                mesh.tvertexes!![k + offset].y = 1.0f - vm.value!![k][1] // invert the t
                                k++
                            }
                            offset += vm.nverts
                        }
                        vm = vm.next
                    }
                } else {
                    Common.common.Warning("ConvertLWOToASE: model '%s' has bad or missing uv data", (fileName)!!)
                    mesh.numTVertexes = 1
                    mesh.tvertexes =
                        Array(mesh.numTVertexes) { idVec2() }
                }
                mesh.normalsParsed = true
                mesh.colorsParsed = true // because we are falling back to the surface color

                // triangles
                var faceIndex = 0
                j = 0
                while (j < layer.polygon.count) {
                    val poly: lwPolygon? = layer.polygon.pol!![j]
                    if (poly!!.surf !== surf) {
                        j++
                        continue
                    }
                    if (poly.nverts != 3) {
                        Common.common.Warning(
                            "ConvertLWOToASE: model %s has too many verts for a poly! Make sure you triplet it down",
                            (fileName)!!
                        )
                        j++
                        continue
                    }
                    mesh.faces!![faceIndex]!!.faceNormal.x = poly.norm[0]
                    mesh.faces!![faceIndex]!!.faceNormal.y = poly.norm[2]
                    mesh.faces!![faceIndex]!!.faceNormal.z = poly.norm[1]
                    k = 0
                    while (k < 3) {
                        mesh.faces!![faceIndex]!!.vertexNum[k] = poly.getV(k)!!.index
                        mesh.faces!![faceIndex]!!.vertexNormals[k].x = poly.getV(k)!!.norm[0]
                        mesh.faces!![faceIndex]!!.vertexNormals[k].y = poly.getV(k)!!.norm[2]
                        mesh.faces!![faceIndex]!!.vertexNormals[k].z = poly.getV(k)!!.norm[1]

                        // complete fallbacks
                        mesh.faces!![faceIndex]!!.tVertexNum[k] = 0
                        mesh.faces!![faceIndex]!!.vertexColors[k][0] =
                            (surf.color.rgb[0] * 255).toInt().toByte()
                        mesh.faces!![faceIndex]!!.vertexColors[k][1] =
                            (surf.color.rgb[1] * 255).toInt().toByte()
                        mesh.faces!![faceIndex]!!.vertexColors[k][2] =
                            (surf.color.rgb[2] * 255).toInt().toByte()
                        mesh.faces!![faceIndex]!!.vertexColors[k][3] = 255.toByte()

                        // first set attributes from the vertex
                        val pt: lwPoint? = layer.point.pt!![poly.getV(k)!!.index]
                        var nvm: Int
                        nvm = 0
                        while (nvm < pt!!.nvmaps) {
                            val vm: lwVMapPt = pt.vm!![nvm]
                            if (vm!!.vmap.type == Model_lwo.LWID_('T', 'X', 'U', 'V').toLong()) {
                                mesh.faces!![faceIndex]!!.tVertexNum[k] = vm.index + vm.vmap.offset
                            }
                            if (vm.vmap.type == Model_lwo.LWID_('R', 'G', 'B', 'A').toLong()) {
                                for (chan in 0..3) {
                                    mesh.faces!![faceIndex]!!.vertexColors[k][chan] =
                                        (255 * vm.vmap.value!![vm.index][chan]).toInt().toByte()
                                }
                            }
                            nvm++
                        }

                        // then override with polygon attributes
                        nvm = 0
                        while (nvm < poly.getV(k)!!.nvmaps) {
                            val vm: lwVMapPt = poly.getV(k)!!.vm!![nvm]
                            if (vm!!.vmap.type == Model_lwo.LWID_('T', 'X', 'U', 'V').toLong()) {
                                mesh.faces!![faceIndex]!!.tVertexNum[k] = vm.index + vm.vmap.offset
                            }
                            if (vm.vmap.type == Model_lwo.LWID_('R', 'G', 'B', 'A').toLong()) {
                                for (chan in 0..3) {
                                    mesh.faces!![faceIndex]!!.vertexColors[k][chan] =
                                        (255 * vm.vmap.value!![vm.index][chan]).toInt().toByte()
                                }
                            }
                            nvm++
                        }
                        k++
                    }
                    faceIndex++
                    j++
                }
                mesh.numFaces = faceIndex
                mesh.numTVFaces = faceIndex
                val newFaces: Array<aseFace_t?> =
                    arrayOfNulls(mesh.numFaces)
                for (i in 0 until mesh.numFaces) {
                    newFaces[i] = mesh.faces!![i]
                }
                mesh.faces = newFaces
                surf = surf.next
            }
            return ase
        }

        fun DeleteSurfaceWithId(id: Int): Boolean {
            var i: Int
            i = 0
            while (i < surfaces.Num()) {
                if (surfaces[i]!!.id == id) {
                    R_FreeStaticTriSurf(surfaces[i]!!.geometry)
                    surfaces.RemoveIndex(i)
                    return true
                }
                i++
            }
            return false
        }

        fun DeleteSurfacesWithNegativeId() {
            var i: Int
            i = 0
            while (i < surfaces.Num()) {
                if (surfaces[i]!!.id < 0) {
                    R_FreeStaticTriSurf(surfaces[i]!!.geometry)
                    surfaces.RemoveIndex(i)
                    i--
                }
                i++
            }
        }

        fun FindSurfaceWithId(id: Int, surfaceNum: CInt): Boolean {
            var i: Int
            i = 0
            while (i < surfaces.Num()) {
                if (surfaces[i]!!.id == id) {
                    surfaceNum.integerValue = i
                    return true
                }
                i++
            }
            return false
        }

        override fun AllocBuffer(): ByteBuffer {
            throw UnsupportedOperationException("Not supported yet.")
        }

        override fun Read(buffer: ByteBuffer) {
            throw UnsupportedOperationException("Not supported yet.")
        }

        override fun Write(): ByteBuffer {
            throw UnsupportedOperationException("Not supported yet.")
        }

        internal class matchVert_s(val index: Int) {
            var color: ByteArray = ByteArray(4)
            var next: matchVert_s? = null
            val normal: idVec3 = idVec3()
            var v: Int = 0
            var tv: Int = 0

            override fun hashCode(): Int {
                var result: Int = v
                result = 31 * result + tv
                return result
            }

            override fun equals(o: Any?): Boolean {
                if (this === o) return true
                if (o == null || javaClass != o.javaClass) return false
                val that: matchVert_s = o as matchVert_s
                if (v != that.v) return false
                return tv == that.tv
            }
        }

        companion object {
            protected val r_mergeModelSurfaces: idCVar = idCVar(
                "r_mergeModelSurfaces",
                "1",
                CVAR_BOOL or CVAR_RENDERER,
                "combine model surfaces with the same material"
            )
            protected val r_slopNormal: idCVar =
                idCVar("r_slopNormal", "0.02", CVAR_RENDERER, "merge normals that dot less than this")
            protected val r_slopTexCoord: idCVar =
                idCVar("r_slopTexCoord", "0.001", CVAR_RENDERER, "merge texture coordinates this far apart")
            protected val r_slopVertex: idCVar =
                idCVar("r_slopVertex", "0.01", CVAR_RENDERER, "merge xyz coordinates this far apart")
            val identityColor /*[4]*/: ByteArray = byteArrayOf(255.toByte(), 255.toByte(), 255.toByte(), 255.toByte())

            /*
         ================
         idRenderModelStatic::FinishSurfaces

         The mergeShadows option allows surfaces with different textures to share
         silhouette edges for shadow calculation, instead of leaving shared edges
         hanging.

         If any of the original shaders have the noSelfShadow flag set, the surfaces
         can't be merged, because they will need to be drawn in different order.

         If there is only one surface, a separate merged surface won't be generated.

         A model with multiple surfaces can't later have a skinned shader change the
         state of the noSelfShadow flag.

         -----------------

         Creates mirrored copies of two sided surfaces with normal maps, which would
         otherwise light funny.

         Extends the bounds of deformed surfaces so they don't cull incorrectly at screen edges.

         ================
         */
        }
    }
}

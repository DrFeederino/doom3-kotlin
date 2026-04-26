/*
===========================================================================

Doom 3 GPL Source Code
Copyright (C) 1999-2011 id Software LLC, a ZeniMax Media company.
Translated to Kotlin by Dr. Feederino with support of Claude Code

This file is part of the Doom 3 GPL Source Code ("Doom 3 Source Code").
Original source: neo/renderer/Model.h

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

import neo.Renderer.Material.idMaterial
import neo.Renderer.RenderWorld.renderEntity_s
import neo.Renderer.VertexCache.vertCache_s
import neo.TempDump.SERiAL
import neo.framework.DemoFile.idDemoFile
import neo.idlib.BV.idBounds
import neo.idlib.Text.Str.idStr
import neo.idlib.containers.List.idList
import neo.idlib.geometry.DrawVert.idDrawVert
import neo.idlib.geometry.JointTransform.idJointQuat
import neo.idlib.idException
import neo.idlib.math.idPlane
import neo.idlib.math.idVec3
import neo.idlib.math.idVec4
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11
import java.nio.ByteBuffer

object Model {
    val INVALID_JOINT: Int = -1
    val MD5_ANIM_EXT: String = "md5anim"
    val MD5_CAMERA_EXT: String = "md5camera"
    val MD5_MESH_EXT: String = "md5mesh"
    val MD5_VERSION: Int = 10

    /*
     ===============================================================================

     Render Model

     ===============================================================================
     */
    // shared between the renderer, game, and Maya export DLL
    val MD5_VERSION_STRING: String = "MD5Version"

    //
    // using shorts for triangle indexes can save a significant amount of traffic, but
    // to support the large models that renderBump loads, they need to be 32 bits
    var GL_INDEX_TYPE: Int = 0
    val SHADOW_CAP_INFINITE: Int = 64

    init {
        GL_INDEX_TYPE = GL11.GL_UNSIGNED_INT
    }

    enum class dynamicModel_t {
        DM_STATIC,

        // never creates a dynamic model
        DM_CACHED,

        // once created, stays constant until the entity is updated (animating characters)
        DM_CONTINUOUS // must be recreated for every single view (time dependent things like particles)
    }

    class silEdge_t {
        // NOTE: making this a glIndex is dubious, as there can be 2x the faces as verts
        var  /*glIndex_t*/p1: Int = 0
        var p2: Int = 0 // planes defining the edge
        var  /*glIndex_t*/v1: Int = 0
        var v2: Int = 0 // verts defining the edge

        constructor()
        constructor(`val`: silEdge_t?) {
            if (`val` == null) {
                v2 = 0
                v1 = v2
                p2 = v1
                p1 = p2
            } else {
                p1 = `val`.p1
                p2 = `val`.p2
                v1 = `val`.v1
                v2 = `val`.v2
            }
        }

        companion object {
            fun generateArray(length: Int): Array<silEdge_t> {
                return Array(length) { silEdge_t() }
            }
        }
    }

    // this is used for calculating unsmoothed normals and tangents for deformed models
    class dominantTri_s {
        val normalizationScale: FloatArray = FloatArray(3)
        var  /*glIndex_t*/v2: Int = 0
        var v3: Int = 0
    }

    class lightingCache_s {
        val localLightVector: idVec3 = idVec3()

        constructor()

        companion object {
            val BYTES: Int = idVec3.BYTES
            fun toByteBuffer(cache: Array<lightingCache_s>): ByteBuffer {
                val data: ByteBuffer = BufferUtils.createByteBuffer(BYTES * cache.size)
                for (c: lightingCache_s in cache) {
                    data.putFloat(c.localLightVector.x)
                    data.putFloat(c.localLightVector.y)
                    data.putFloat(c.localLightVector.z)
                }
                return data.flip()
            }
        }
    }

    class shadowCache_s {
        val xyz: idVec4 = idVec4() // we use homogenous coordinate tricks

        companion object {
            val BYTES: Int = idVec4.BYTES
            fun toByteBuffer(cache: Array<shadowCache_s>): ByteBuffer {
                val data: ByteBuffer = BufferUtils.createByteBuffer(BYTES * cache.size)
                for (c: shadowCache_s in cache) {
                    data.putFloat(c.xyz.x)
                    data.putFloat(c.xyz.y)
                    data.putFloat(c.xyz.z)
                    data.putFloat(c.xyz.w)
                }
                return data.flip()
            }

            fun generateArray(length: Int): Array<shadowCache_s> {
                return Array(length) { shadowCache_s() }
            }
        }
    }

    // our only drawing geometry type
    class srfTriangles_s {
        val bounds: idBounds = idBounds() // for culling
        var facePlanes // [numIndexes/3] plane equations
                : Array<idPlane?>? = null
        var indexes // indexes, allocated with special allocator
                : IntArray? = null
        var numIndexes: Int = 0 // for shadows, this has both front and rear end caps and silhouette planes
        var numShadowIndexesNoCaps: Int = 0 // shadow volumes with the front and rear caps omitted

        // pointers into the original surface, and should not be freed
        var numShadowIndexesNoFrontCaps: Int = 0 // shadow volumes with front caps omitted
        var numVerts: Int = 0 // number of vertices
        var shadowCapPlaneBits: Int = 0 // bits 0-5 are set when that plane of the interacting light has triangles
        var shadowVertexes // these will be copied to shadowCache when it is going to be drawn.
                : Array<shadowCache_s>? = null
        var silIndexes // indexes changed to be the first vertex with same XYZ, ignoring normal and texcoords
                : IntArray? = null
        var tangentsCalculated: Boolean = false // set when the vertex tangents have been calculated
        var verts // vertices, allocated with special allocator
                : Array<idDrawVert>? = null
        var ambientCache: vertCache_s? = null // idDrawVert
        var ambientSurface: srfTriangles_s? =
            null // for light interactions, point back at the original surface that generated
        var ambientViewCount: Int = 0 // if == tr.viewCount, it is visible this view
        var deformedSurface: Boolean = false // if true, indexes, silIndexes, mirrorVerts, and silEdges are
        var dominantTris // [numVerts] for deformed surface fast tangent calculation
                : Array<dominantTri_s?>? = null
        var dupVerts // pairs of the number of the first vertex and the number of the duplicate vertex
                : IntArray? = null
        var facePlanesCalculated: Boolean = false // set when the face planes have been calculated
        var generateNormals: Boolean = false // create normals from geometry, instead of using explicit ones

        // data in vertex object space, not directly readable by the CPU
        var indexCache: vertCache_s? = null // int

        // projected on it, which means that if the view is on the outside of that
        // plane, we need to draw the rear caps of the shadow volume
        // turboShadows will have SHADOW_CAP_INFINITE
        var lightingCache: vertCache_s? = null // lightingCache_t

        // these are NULL when vertex programs are available
        var mirroredVerts // tri->mirroredVerts[0] is the mirror of tri->numVerts - tri->numMirroredVerts + 0
                : IntArray? = null

        // the interaction, which we will get the ambientCache from
        var nextDeferredFree: srfTriangles_s? = null // chain of tris to free next frame
        var numDupVerts: Int = 0 // number of duplicate vertexes
        var numMirroredVerts: Int = 0 // this many verts at the end of the vert list are tangent mirrors
        var numSilEdges: Int = 0 // number of silhouette edges
        var perfectHull: Boolean = false // true if there aren't any dangling edges
        var shadowCache: vertCache_s? = null // shadowCache_t
        var silEdges // silhouette edges
                : Array<silEdge_t?>? = null

        override fun toString(): String {
            return ("srfTriangles_s{" +
                    "bounds=" + bounds +
                    ", facePlanes=" + facePlanes.contentToString() +
                    ", indexes=" + indexes.contentToString() +
                    ", numIndexes=" + numIndexes +
                    ", numShadowIndexesNoCaps=" + numShadowIndexesNoCaps +
                    ", numShadowIndexesNoFrontCaps=" + numShadowIndexesNoFrontCaps +
                    ", numVerts=" + numVerts +
                    ", shadowCapPlaneBits=" + shadowCapPlaneBits +
                    ", shadowVertexes=" + shadowVertexes.contentToString() +
                    ", silIndexes=" + silIndexes.contentToString() +
                    ", tangentsCalculated=" + tangentsCalculated +
                    ", verts=" + verts.contentToString() +
                    ", ambientCache=" + ambientCache +
                    ", ambientSurface=" + ambientSurface +
                    ", ambientViewCount=" + ambientViewCount +
                    ", deformedSurface=" + deformedSurface +
                    ", dominantTris=" + dominantTris.contentToString() +
                    ", dupVerts=" + dupVerts.contentToString() +
                    ", facePlanesCalculated=" + facePlanesCalculated +
                    ", generateNormals=" + generateNormals +
                    ", indexCache=" + indexCache +
                    ", lightingCache=" + lightingCache +
                    ", mirroredVerts=" + mirroredVerts.contentToString() +
                    ", nextDeferredFree=" + nextDeferredFree +
                    ", numDupVerts=" + numDupVerts +
                    ", numMirroredVerts=" + numMirroredVerts +
                    ", numSilEdges=" + numSilEdges +
                    ", perfectHull=" + perfectHull +
                    ", shadowCache=" + shadowCache +
                    ", silEdges=" + silEdges.contentToString() +
                    '}')
        }
    }

    internal class idTriList : idList<srfTriangles_s?>()
    class modelSurface_s {
        var geometry: srfTriangles_s? = null
        var id: Int = 0
        var shader: idMaterial? = null

        constructor()
        constructor(other: modelSurface_s?) {
            if (other != null) {
                geometry = other.geometry
                id = other.id
                shader = other.shader
            }
        }
    }

    class idMD5Joint {
        var name: idStr? = null
        var parent: idMD5Joint? = null
    }

    // the init methods may be called again on an already created model when
    // a reloadModels is issued
    abstract class idRenderModel : SERiAL {

        // Loads static models only, dynamic models must be loaded by the modelManager
        @Throws(idException::class)
        abstract fun InitFromFile(fileName: String?)

        // renderBump uses this to load the very high poly count models, skipping the
        // shadow and tangent generation, along with some surface cleanup to make it load faster
        abstract fun PartialInitFromFile(fileName: String?)

        // this is used for dynamically created surfaces, which are assumed to not be reloadable.
        // It can be called again to clear out the surfaces of a dynamic model for regeneration.
        abstract fun InitEmpty(name: String?)

        // dynamic model instantiations will be created with this
        // the geometry data will be owned by the model, and freed when it is freed
        // the geoemtry should be raw triangles, with no extra processing
        abstract fun AddSurface(surface: modelSurface_s?)

        // cleans all the geometry and performs cross-surface processing
        // like shadow hulls
        // Creates the duplicated back side geometry for two sided, alpha tested, lit materials
        // This does not need to be called if none of the surfaces added with AddSurface require
        // light interaction, and all the triangles are already well formed.
        abstract fun FinishSurfaces()

        // frees all the data, but leaves the class around for dangling references,
        // which can regenerate the data with LoadModel()
        abstract fun PurgeModel()

        // resets any model information that needs to be reset on a same level load etc.. 
        // currently only implemented for liquids
        abstract fun Reset()

        // used for initial loads, reloadModel, and reloading the data of purged models
        // Upon exit, the model will absolutely be valid, but possibly as a default model
        abstract fun LoadModel()

        // internal use
        abstract fun IsLoaded(): Boolean
        abstract fun SetLevelLoadReferenced(referenced: Boolean)
        abstract fun IsLevelLoadReferenced(): Boolean

        // models that are already loaded at level start time
        // will still touch their data to make sure they
        // are kept loaded
        abstract fun TouchData()

        // dump any ambient caches on the model surfaces
        abstract fun FreeVertexCache()

        // returns the name of the model
        abstract fun Name(): String

        // prints a detailed report on the model for printModel
        abstract fun Print()

        // prints a single line report for listModels
        abstract fun List()

        // reports the amount of memory (roughly) consumed by the model
        abstract fun Memory(): Int

        // for reloadModels
        abstract fun  /*ID_TIME_T*/Timestamp(): LongArray

        // returns the number of surfaces
        abstract fun NumSurfaces(): Int

        // NumBaseSurfaces will not count any overlays added to dynamic models
        abstract fun NumBaseSurfaces(): Int

        // get a pointer to a surface
        abstract fun Surface(surfaceNum: Int): modelSurface_s?

        // Allocates surface triangles.
        // Allocates memory for srfTriangles_t::verts and srfTriangles_t::indexes
        // The allocated memory is not initialized.
        // srfTriangles_t::numVerts and srfTriangles_t::numIndexes are set to zero.
        abstract fun AllocSurfaceTriangles(numVerts: Int, numIndexes: Int): srfTriangles_s

        // Frees surfaces triangles.
        abstract fun FreeSurfaceTriangles(tris: srfTriangles_s?)

        // created at load time by stitching together all surfaces and sharing
        // the maximum number of edges.  This may be incorrect if a skin file
        // remaps surfaces between shadow casting and non-shadow casting, or
        // if some surfaces are noSelfShadow and others aren't
        abstract fun ShadowHull(): srfTriangles_s?

        // models of the form "_area*" may have a prelight shadow model associated with it
        abstract fun IsStaticWorldModel(): Boolean

        // models parsed from inside map files or dynamically created cannot be reloaded by
        // reloadmodels
        abstract fun IsReloadable(): Boolean

        // md3, md5, particles, etc
        abstract fun IsDynamicModel(): dynamicModel_t

        // if the load failed for any reason, this will return true
        abstract fun IsDefaultModel(): Boolean

        // dynamic models should return a fast, conservative approximation
        // static models should usually return the exact value
        abstract fun Bounds(ent: renderEntity_s? /*= NULL*/): idBounds
        abstract fun Bounds(): idBounds

        // returns value != 0.0f if the model requires the depth hack
        abstract fun DepthHack(): Float

        // returns a static model based on the definition and view
        // currently, this will be regenerated for every view, even though
        // some models, like character meshes, could be used for multiple (mirror)
        // views in a frame, or may stay static for multiple frames (corpses)
        // The renderer will delete the returned dynamic model the next view
        // This isn't const, because it may need to reload a purged model if it
        // wasn't precached correctly.
        abstract fun InstantiateDynamicModel(
            ent: renderEntity_s?,
            view: viewDef_s?,
            cachedModel: idRenderModel?
        ): idRenderModel?

        // Returns the number of joints or 0 if the model is not an MD5
        abstract fun NumJoints(): Int

        // Returns the MD5 joints or NULL if the model is not an MD5
        abstract fun GetJoints(): Array<idMD5Joint?>?

        // Returns the handle for the joint with the given name.
        abstract /*jointHandle_t*/ fun GetJointHandle(name: String?): Int

        // Returns the name for the joint with the given handle.
        abstract fun GetJointName(jointHandle_t: Int): String

        // Returns the default animation pose or NULL if the model is not an MD5.
        abstract fun GetDefaultPose(): Array<idJointQuat?>?

        // Returns number of the joint nearest to the given triangle.
        abstract fun NearestJoint(surfaceNum: Int, a: Int, b: Int, c: Int): Int

        // Writing to and reading from a demo file.
        abstract fun ReadFromDemoFile(f: idDemoFile?)
        abstract fun WriteToDemoFile(f: idDemoFile)
        abstract fun oSet(FindModel: idRenderModel?)
    }
}

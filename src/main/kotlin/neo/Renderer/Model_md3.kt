/*
===========================================================================

Doom 3 GPL Source Code
Copyright (C) 1999-2011 id Software LLC, a ZeniMax Media company.

This file is part of the Doom 3 GPL Source Code ("Doom 3 Source Code").

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

Translated to Kotlin by Dr. Feederino with support of Claude Code.

===========================================================================
*/
package neo.Renderer

import neo.Renderer.Material.idMaterial
import neo.Renderer.Model.dynamicModel_t
import neo.Renderer.Model.idRenderModel
import neo.Renderer.Model.modelSurface_s
import neo.Renderer.Model.srfTriangles_s
import neo.Renderer.Model_local.idRenderModelStatic
import neo.Renderer.RenderWorld.renderEntity_s
import neo.framework.Common
import neo.framework.DeclManager
import neo.framework.FileSystem_h.fileSystem
import neo.idlib.BV.idBounds
import neo.idlib.LittleFloat
import neo.idlib.LittleLong
import neo.idlib.LittleShort
import neo.idlib.geometry.DrawVert.idDrawVert
import neo.idlib.math.idMath
import neo.idlib.math.idVec3
import java.nio.ByteBuffer
import java.util.*

object Model_md3 {
    val MAX_MD3PATH: Int = 64 // from quake3

    /*
     ========================================================================

     .MD3 triangle model file format

     Private structures used by the MD3 loader.

     ========================================================================
     */
    val MD3_IDENT: Int = (('3'.code shl 24) + ('P'.code shl 16) + ('D'.code shl 8) + 'I'.code)
    val MD3_MAX_FRAMES: Int = 1024 // per model

    //
    // limits
    val MD3_MAX_LODS: Int = 4
    val MD3_MAX_SHADERS: Int = 256 // per surface
    val MD3_MAX_SURFACES: Int = 32 // per model
    val MD3_MAX_TAGS: Int = 16 // per frame
    val MD3_MAX_TRIANGLES: Int = 8192 // per surface
    val MD3_MAX_VERTS: Int = 4096 // per surface
    val MD3_VERSION: Int = 15

    //
    // vertex scales
    val MD3_XYZ_SCALE: Float = (1.0f / 64)

    //
    // surface geometry should not exceed these limits
    val SHADER_MAX_VERTEXES: Int = 1000
    val SHADER_MAX_INDEXES: Int = (6 * SHADER_MAX_VERTEXES)
    fun LL(x: Int): Int {
        return LittleLong(x)
    }

    internal class md3Frame_s {
        val bounds: Array<idVec3> = idVec3.generateArray(2)
        val localOrigin: idVec3 = idVec3()
        var name: String? = null
        var radius: Float = 0.0f
    }

    internal class md3Tag_s {
        val axis: Array<idVec3> = idVec3.generateArray(3)
        var name: String? = null // tag name
        val origin: idVec3 = idVec3()
    }

    /*
     ** md3Surface_t
     **
     ** CHUNK			SIZE
     ** header			sizeof( md3Surface_t )
     ** shaders			sizeof( md3Shader_t ) * numShaders
     ** triangles[0]		sizeof( md3Triangle_t ) * numTriangles
     ** st				sizeof( md3St_t ) * numVerts
     ** XyzNormals		sizeof( md3XyzNormal_t ) * numVerts * numFrames
     */
    internal class md3Surface_s {
        var flags: Int = 0
        var ident: Int = 0
        var name: String? = null // polyset name
        var normals: Array<md3XyzNormal_t?>? = null
        var numFrames: Int = 0 // all surfaces in a model should have the same
        var numShaders: Int = 0 // all surfaces in a model should have the same
        var numTriangles: Int = 0
        var numVerts: Int = 0
        var ofsEnd: Int = 0 // next surface follows
        var ofsShaders: Int = 0 // offset from start of md3Surface_t
        var ofsSt: Int = 0 // texture coords are common for all frames
        var ofsTriangles: Int = 0
        var ofsXyzNormals: Int = 0 // numVerts * numFrames
        var shaders: Array<md3Shader_t?>? = null
        var triangles: Array<md3Triangle_t?>? = null
        var verts: Array<md3St_t?>? = null
    }

    internal class md3Shader_t {
        var name: String? = null
        var shader: idMaterial? = null // for in-game use
    }

    internal class md3Triangle_t {
        var indexes: IntArray = IntArray(3)
    }

    internal class md3St_t {
        var st: FloatArray = FloatArray(2)
    }

    internal class md3XyzNormal_t {
        var normal: Short = 0
        var xyz: ShortArray = ShortArray(3)
    }

    internal class md3Header_s {
        var flags: Int = 0
        var frames: Array<md3Frame_s?>? = null
        var ident: Int = 0
        var name: String? = null // model name
        var numFrames: Int = 0
        var numSkins: Int = 0
        var numSurfaces: Int = 0
        var numTags: Int = 0
        var ofsEnd: Int = 0 // end of file
        var ofsFrames: Int = 0 // offset for first frame
        var ofsSurfaces: Int = 0 // first surface, others follow
        var ofsTags: Int = 0 // numFrames * numTags
        var surfaces: Array<md3Surface_s?>? = null
        var tags: Array<md3Tag_s?>? = null
        var version: Int = 0

        fun readFrom(buffer: ByteBuffer) {
            buffer.order(java.nio.ByteOrder.LITTLE_ENDIAN)
            ident = buffer.int
            version = buffer.int
            val nameBytes = ByteArray(MAX_MD3PATH)
            buffer.get(nameBytes)
            name = String(nameBytes).trimEnd('\u0000')
            flags = buffer.int
            numFrames = buffer.int
            numTags = buffer.int
            numSurfaces = buffer.int
            numSkins = buffer.int
            ofsFrames = buffer.int
            ofsTags = buffer.int
            ofsSurfaces = buffer.int
            ofsEnd = buffer.int
        }

        fun writeTo(): ByteBuffer {
            val buffer = ByteBuffer.allocate(BYTES)
            buffer.order(java.nio.ByteOrder.LITTLE_ENDIAN)
            buffer.putInt(ident)
            buffer.putInt(version)
            val nameBytes = ByteArray(MAX_MD3PATH)
            if (name != null) {
                val src = name!!.toByteArray()
                System.arraycopy(src, 0, nameBytes, 0, minOf(src.size, MAX_MD3PATH))
            }
            buffer.put(nameBytes)
            buffer.putInt(flags)
            buffer.putInt(numFrames)
            buffer.putInt(numTags)
            buffer.putInt(numSurfaces)
            buffer.putInt(numSkins)
            buffer.putInt(ofsFrames)
            buffer.putInt(ofsTags)
            buffer.putInt(ofsSurfaces)
            buffer.putInt(ofsEnd)
            buffer.flip()
            return buffer
        }

        companion object {
            private const val MAX_MD3PATH = 64

            val BYTES = 108 // 2*int + char[64] + 9*int
        }
    }

    /*
     ===============================================================================

     MD3 animated model

     ===============================================================================
     */
    internal class idRenderModelMD3 : idRenderModelStatic() {
        private var dataSize: Int = 0 // just for listing purposes
        private val index: Int = 0 // model = tr.models[model->index]
        private var md3: md3Header_s? = null // only if type == MOD_MESH
        private val numLods: Int = 0

        override fun InitFromFile(fileName: String?) {
            var i: Int
            var j: Int
            val pinmodel: md3Header_s
            var frame: md3Frame_s
            var surf: md3Surface_s
            var shader: md3Shader_t
            var tri: md3Triangle_t
            var st: md3St_t
            var xyz: md3XyzNormal_t
            var tag: md3Tag_s
            val buffer: Array<ByteBuffer?> = (arrayOf(null))
            val version: Int
            var size: Int
            name.set(fileName)
            size = fileSystem.ReadFile(fileName!!, buffer, null)
            if (size <= md3Header_s.BYTES) {
                if (buffer[0] != null) {
                    fileSystem.FreeFile(buffer)
                }
                MakeDefaultModel()
                return
            }
            pinmodel = md3Header_s()
            pinmodel.readFrom(buffer[0]!!)
            version = LittleLong(pinmodel.version)
            if (version != MD3_VERSION) {
                fileSystem.FreeFile(buffer)
                Common.common.Warning(
                    "InitFromFile: %s has wrong version (%d should be %d)",
                    (fileName), version, MD3_VERSION
                )
                MakeDefaultModel()
                return
            }
            size = LittleLong(pinmodel.ofsEnd)
            dataSize += size

            md3 = md3Header_s()
            md3!!.readFrom(buffer[0]!!)
            md3!!.ident = LL(md3!!.ident)
            md3!!.version = LL(md3!!.version)
            md3!!.numFrames = LL(md3!!.numFrames)
            md3!!.numTags = LL(md3!!.numTags)
            md3!!.numSurfaces = LL(md3!!.numSurfaces)
            md3!!.ofsFrames = LL(md3!!.ofsFrames)
            md3!!.ofsTags = LL(md3!!.ofsTags)
            md3!!.ofsSurfaces = LL(md3!!.ofsSurfaces)
            md3!!.ofsEnd = LL(md3!!.ofsEnd)
            if (md3!!.numFrames < 1) {
                Common.common.Warning("InitFromFile: %s has no frames", (fileName))
                fileSystem.FreeFile(buffer)
                MakeDefaultModel()
                return
            }

            // swap all the frames
            md3!!.frames = arrayOfNulls(md3!!.numFrames)
            i = 0
            while (i < md3!!.numFrames) {
                frame = md3Frame_s()
                frame.radius = LittleFloat(frame.radius)
                j = 0
                while (j < 3) {
                    frame.bounds[0][j] = LittleFloat(frame.bounds[0][j])
                    frame.bounds[1][j] = LittleFloat(frame.bounds[1][j])
                    frame.localOrigin[j] = LittleFloat(frame.localOrigin[j])
                    j++
                }
                md3!!.frames!![i] = frame
                i++
            }

            // swap all the tags
            md3!!.tags = arrayOfNulls(md3!!.numTags * md3!!.numFrames)
            i = 0
            while (i < md3!!.numTags * md3!!.numFrames) {
                tag = md3Tag_s()
                j = 0
                while (j < 3) {
                    tag.origin[j] = LittleFloat(tag.origin[j])
                    tag.axis[0][j] = LittleFloat(tag.axis[0][j])
                    tag.axis[1][j] = LittleFloat(tag.axis[1][j])
                    tag.axis[2][j] = LittleFloat(tag.axis[2][j])
                    j++
                }
                md3!!.tags!![i] = tag
                i++
            }

            // swap all the surfaces
            md3!!.surfaces = arrayOfNulls(md3!!.numSurfaces)
            i = 0
            while (i < md3!!.numSurfaces) {
                surf = md3Surface_s()
                surf.ident = LL(surf.ident)
                surf.flags = LL(surf.flags)
                surf.numFrames = LL(surf.numFrames)
                surf.numShaders = LL(surf.numShaders)
                surf.numTriangles = LL(surf.numTriangles)
                surf.ofsTriangles = LL(surf.ofsTriangles)
                surf.numVerts = LL(surf.numVerts)
                surf.ofsShaders = LL(surf.ofsShaders)
                surf.ofsSt = LL(surf.ofsSt)
                surf.ofsXyzNormals = LL(surf.ofsXyzNormals)
                surf.ofsEnd = LL(surf.ofsEnd)
                if (surf.numVerts > SHADER_MAX_VERTEXES) {
                    Common.common.Error(
                        "InitFromFile: %s has more than %d verts on a surface (%d)",
                        (fileName), SHADER_MAX_VERTEXES, surf.numVerts
                    )
                }
                if (surf.numTriangles * 3 > SHADER_MAX_INDEXES) {
                    Common.common.Error(
                        "InitFromFile: %s has more than %d triangles on a surface (%d)",
                        (fileName), SHADER_MAX_INDEXES / 3, surf.numTriangles
                    )
                }

                // change to surface identifier
                surf.ident = 0 //SF_MD3;

                // lowercase the surface name so skin compares are faster
                surf.name = surf.name!!.lowercase(Locale.getDefault())

                // strip off a trailing _1 or _2
                // this is a crutch for q3data being a mess
                j = surf.name!!.length
                if (j > 2 && surf.name!![j - 2] == '_') {
                    surf.name = surf.name!!.substring(0, j - 2)
                }

                // register the shaders
                surf.shaders = arrayOfNulls(surf.numShaders)
                j = 0
                while (j < surf.numShaders) {
                    shader = md3Shader_t()
                    var sh: idMaterial?
                    sh = DeclManager.declManager.FindMaterial((shader.name)!!)
                    shader.shader = sh
                    surf.shaders!![j] = shader
                    j++
                }

                // swap all the triangles
                surf.triangles = arrayOfNulls(surf.numTriangles)
                j = 0
                while (j < surf.numTriangles) {
                    tri = md3Triangle_t()
                    tri.indexes[0] = LL(tri.indexes[0])
                    tri.indexes[1] = LL(tri.indexes[1])
                    tri.indexes[2] = LL(tri.indexes[2])
                    surf.triangles!![j] = tri
                    j++
                }

                // swap all the ST
                surf.verts = arrayOfNulls(surf.numVerts)
                j = 0
                while (j < surf.numVerts) {
                    st = md3St_t()
                    st.st[0] = LittleFloat(st.st[0])
                    st.st[1] = LittleFloat(st.st[1])
                    surf.verts!![j] = st
                    j++
                }

                // swap all the XyzNormals
                surf.normals = arrayOfNulls(surf.numVerts * surf.numFrames)
                j = 0
                while (j < surf.numVerts * surf.numFrames) {
                    xyz = md3XyzNormal_t()
                    xyz.xyz[0] = LittleShort(xyz.xyz[0])
                    xyz.xyz[1] = LittleShort(xyz.xyz[1])
                    xyz.xyz[2] = LittleShort(xyz.xyz[2])
                    xyz.normal = LittleShort(xyz.normal)
                    surf.normals!![j] = xyz
                    j++
                }

                md3!!.surfaces!![i] = surf
                i++
            }
            fileSystem.FreeFile(buffer)
        }

        override fun IsDynamicModel(): dynamicModel_t {
            return dynamicModel_t.DM_CACHED
        }

        override fun InstantiateDynamicModel(
            ent: renderEntity_s?,
            view: viewDef_s?,
            cachedModel: idRenderModel?
        ): idRenderModel {
            var cachedModel: idRenderModel? = cachedModel
            var j: Int
            val backlerp: Float
            var indexes: Int
            var numVerts: Int
            var surface: md3Surface_s
            val frame: Int
            val oldframe: Int
            val staticModel: idRenderModelStatic
            if (cachedModel != null) {
                cachedModel = null
            }
            staticModel = idRenderModelStatic()
            staticModel.InitEmpty("_MD3_Snapshot_")
            staticModel.bounds.Clear()

            // TODO: these need set by an entity
            frame = idMath.ClampInt(
                0, md3!!.numFrames - 1,
                ent!!.shaderParms[RenderWorld.SHADERPARM_MD3_FRAME].toInt()
            )
            oldframe = idMath.ClampInt(
                0, md3!!.numFrames - 1,
                ent.shaderParms[RenderWorld.SHADERPARM_MD3_LASTFRAME].toInt()
            )
            backlerp = ent.shaderParms[RenderWorld.SHADERPARM_MD3_BACKLERP]

            for (i in 0 until md3!!.numSurfaces) {
                surface = md3!!.surfaces!![i]!!
                val tri: srfTriangles_s = R_AllocStaticTriSurf()
                R_AllocStaticTriSurfVerts(tri, surface.numVerts)
                R_AllocStaticTriSurfIndexes(tri, surface.numTriangles * 3)
                tri.bounds.Clear()
                val surf = modelSurface_s()
                surf.geometry = tri

                val shaders: md3Shader_t? = surface.shaders!![0]
                surf.shader = shaders!!.shader
                LerpMeshVertexes(tri, surface, backlerp, frame, oldframe)
                indexes = surface.numTriangles * 3
                j = 0
                for (triangle in surface.triangles!!) {
                    tri.indexes!![j + 0] = triangle!!.indexes[0]
                    tri.indexes!![j + 1] = triangle.indexes[1]
                    tri.indexes!![j + 2] = triangle.indexes[2]
                    j += 3
                }
                tri.numIndexes += indexes
                numVerts = surface.numVerts
                for (k in 0 until surface.numVerts) {
                    val stri = tri.verts!![k]!!
                    stri.st[0] = surface.verts!![k]!!.st[0]
                    stri.st[1] = surface.verts!![k]!!.st[1]
                }
                R_BoundTriSurf(tri)
                surf.id = staticModel.NumSurfaces()
                staticModel.AddSurface(surf)
                staticModel.bounds.AddPoint(surf.geometry!!.bounds[0])
                staticModel.bounds.AddPoint(surf.geometry!!.bounds[1])
            }
            return staticModel
        }

        override fun Bounds(ent: renderEntity_s?): idBounds {
            val ret = idBounds()
            ret.Clear()
            if (null == ent || null == md3) {
                // just give it the editor bounds
                ret.AddPoint(idVec3(-10, -10, -10))
                ret.AddPoint(idVec3(10, 10, 10))
                return ret
            }

            val frameIdx = idMath.ClampInt(
                0, md3!!.numFrames - 1,
                ent.shaderParms[RenderWorld.SHADERPARM_MD3_FRAME].toInt()
            )
            val frame: md3Frame_s = md3!!.frames!![frameIdx]!!
            ret.AddPoint(frame.bounds[0])
            ret.AddPoint(frame.bounds[1])
            return ret
        }

        private fun LerpMeshVertexes(
            tri: srfTriangles_s, surf: md3Surface_s, backlerp: Float,
            frame: Int,
            oldframe: Int
        ) {
            var oldXyz: md3XyzNormal_t?
            var newXyz: md3XyzNormal_t?
            val oldXyzScale: Float
            val newXyzScale: Float
            var vertNum: Int
            val numVerts: Int

            // C++: newXyz = (short *)((byte *)surf + surf->ofsXyzNormals) + (frame * surf->numVerts * 4);
            var newXyzIdx = frame * surf.numVerts
            newXyz = surf.normals!![newXyzIdx]
            newXyzScale = (MD3_XYZ_SCALE * (1.0f - backlerp))
            numVerts = surf.numVerts
            if (backlerp == 0.0f) {
                //
                // just copy the vertexes
                //
                vertNum = 0
                while (vertNum < numVerts) {
                    val outvert: idDrawVert = tri.verts!![tri.numVerts]
                    outvert.xyz.x = newXyz!!.xyz[0] * newXyzScale
                    outvert.xyz.y = newXyz.xyz[1] * newXyzScale
                    outvert.xyz.z = newXyz.xyz[2] * newXyzScale
                    tri.numVerts++
                    vertNum++
                    newXyzIdx++
                    if (vertNum < numVerts) {
                        newXyz = surf.normals!![newXyzIdx]
                    }
                }
            } else {
                //
                // interpolate and copy the vertexes
                //
                var oldXyzIdx = oldframe * surf.numVerts
                oldXyz = surf.normals!![oldXyzIdx]
                oldXyzScale = (MD3_XYZ_SCALE * backlerp)
                vertNum = 0
                while (vertNum < numVerts) {
                    val outvert: idDrawVert = tri.verts!![tri.numVerts]

                    // interpolate the xyz
                    outvert.xyz.x = oldXyz!!.xyz[0] * oldXyzScale + newXyz!!.xyz[0] * newXyzScale
                    outvert.xyz.y = oldXyz.xyz[1] * oldXyzScale + newXyz.xyz[1] * newXyzScale
                    outvert.xyz.z = oldXyz.xyz[2] * oldXyzScale + newXyz.xyz[2] * newXyzScale
                    tri.numVerts++
                    vertNum++
                    oldXyzIdx++
                    newXyzIdx++
                    if (vertNum < numVerts) {
                        oldXyz = surf.normals!![oldXyzIdx]
                        newXyz = surf.normals!![newXyzIdx]
                    }
                }
            }
        }
    }
}

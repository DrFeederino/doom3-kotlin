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
import neo.framework.Common
import neo.framework.DemoFile.idDemoFile
import neo.idlib.containers.CInt
import neo.idlib.containers.List.idList
import neo.idlib.geometry.DrawVert
import neo.idlib.math.SIMDProcessor
import neo.idlib.math.idPlane
import neo.idlib.math.idVec2

object ModelOverlay {
    /*
     ===============================================================================

     Render model overlay for adding decals on top of dynamic models.

     ===============================================================================
     */
    val MAX_OVERLAY_SURFACES: Int = 16

    class overlayVertex_s {
        var st: FloatArray = FloatArray(2)
        var vertexNum: Int = 0

        constructor()
        constructor(`val`: overlayVertex_s?) {
            vertexNum = `val`!!.vertexNum
            System.arraycopy(`val`.st, 0, st, 0, st.size)
        }
    }

    class overlaySurface_s {
        var indexes: IntArray? = null
        var numIndexes: Int = 0
        var numVerts: Int = 0
        var surfaceId: Int = 0
        var surfaceNum: CInt = CInt()
        var verts: Array<overlayVertex_s?>? = null
    }

    internal class overlayMaterial_s {
        var material: idMaterial? = null
        val surfaces: idList<overlaySurface_s?> = idList<overlaySurface_s?>()
    }

    class idRenderModelOverlay {
        //
        private val materials: idList<overlayMaterial_s?> = idList<overlayMaterial_s?>()

        /*
         =====================
         idRenderModelOverlay::CreateOverlay

         This projects on both front and back sides to avoid seams
         The material should be clamped, because entire triangles are added, some of which
         may extend well past the 0.0f to 1.0f texture range
         =====================
         */
        // Projects an overlay onto deformable geometry and can be added to
        // a render entity to allow decals on top of dynamic models.
        // This does not generate tangent vectors, so it can't be used with
        // light interaction shaders. Materials for overlays should always
        // be clamped, because the projected texcoords can run well off the
        // texture since no new clip vertexes are generated.
        fun CreateOverlay(model: idRenderModel, localTextureAxis: Array<idPlane?> /*[2]*/, mtr: idMaterial) {
            var i: Int
            var maxVerts: Int
            var maxIndexes: Int
            var surfNum: Int

            // count up the maximum possible vertices and indexes per surface
            maxVerts = 0
            maxIndexes = 0
            surfNum = 0
            while (surfNum < model.NumSurfaces()) {
                val surf: modelSurface_s? = model.Surface(surfNum)
                if (surf!!.geometry!!.numVerts > maxVerts) {
                    maxVerts = surf.geometry!!.numVerts
                }
                if (surf.geometry!!.numIndexes > maxIndexes) {
                    maxIndexes = surf.geometry!!.numIndexes
                }
                surfNum++
            }

            // make temporary buffers for the building process
            val overlayVerts: Array<overlayVertex_s> = Array(maxVerts) { overlayVertex_s() }
            val  /*glIndex_t*/overlayIndexes = IntArray(maxIndexes)

            // pull out the triangles we need from the base surfaces
            surfNum = 0
            while (surfNum < model.NumBaseSurfaces()) {
                val surf: modelSurface_s? = model.Surface(surfNum)
                var d: Float
                if (null == surf!!.geometry || null == surf.shader) {
                    surfNum++
                    continue
                }

                // some surfaces can explicitly disallow overlays
                if (!surf.shader!!.AllowOverlays()) {
                    surfNum++
                    continue
                }
                val stri: srfTriangles_s? = surf.geometry

                // try to cull the whole surface along the first texture axis
                d = stri!!.bounds.PlaneDistance((localTextureAxis[0])!!)
                if (d < 0.0f || d > 1.0f) {
                    surfNum++
                    continue
                }

                // try to cull the whole surface along the second texture axis
                d = stri.bounds.PlaneDistance((localTextureAxis[1])!!)
                if (d < 0.0f || d > 1.0f) {
                    surfNum++
                    continue
                }
                val cullBits = ByteArray(stri.numVerts)
                val texCoords: Array<idVec2> = Array(stri.numVerts) { idVec2() }
                SIMDProcessor!!.OverlayPointCull(
                    cullBits,
                    texCoords as Array<idVec2>,
                    localTextureAxis as Array<idPlane>,
                    stri.verts as Array<DrawVert.idDrawVert>,
                    stri.numVerts
                )
                val  /*glIndex_t */vertexRemap = IntArray(stri.numVerts)
                SIMDProcessor!!.Memset(vertexRemap, -1, stri.numVerts)

                // find triangles that need the overlay
                var numVerts = 0
                var numIndexes = 0
                var triNum = 0
                var index = 0
                while (index < stri.numIndexes) {
                    val v1: Int = stri.indexes!![index + 0]
                    val v2: Int = stri.indexes!![index + 1]
                    val v3: Int = stri.indexes!![index + 2]

                    // skip triangles completely off one side
                    if ((cullBits[v1].toInt() and cullBits[v2].toInt() and cullBits[v3].toInt()) != 0) {
                        index += 3
                        triNum++
                        continue
                    }

                    // we could do more precise triangle culling, like the light interaction does, if desired
                    // keep this triangle
                    for (vnum in 0..2) {
                        val ind: Int = stri.indexes!![index + vnum]
                        if (vertexRemap[ind] == -1) {
                            vertexRemap[ind] = numVerts
                            overlayVerts[numVerts].vertexNum = ind
                            overlayVerts[numVerts].st[0] = texCoords[ind][0]
                            overlayVerts[numVerts].st[1] = texCoords[ind][1]
                            numVerts++
                        }
                        overlayIndexes[numIndexes++] = vertexRemap[ind]
                    }
                    index += 3
                    triNum++
                }
                if (0 == numIndexes) {
                    surfNum++
                    continue
                }
                val s = overlaySurface_s()
                s.surfaceNum.integerValue = surfNum
                s.surfaceId = surf.id
                s.verts = arrayOfNulls(numVerts)
                i = 0
                while (i < numVerts) {
                    s.verts!![i] = overlayVertex_s(overlayVerts[i])
                    i++
                }
                s.numVerts = numVerts
                s.indexes = IntArray(numIndexes)
                System.arraycopy(overlayIndexes, 0, s.indexes, 0, numIndexes)
                s.numIndexes = numIndexes
                i = 0
                while (i < materials.Num()) {
                    if (materials[i]!!.material === mtr) {
                        break
                    }
                    i++
                }
                if (i < materials.Num()) {
                    materials[i]!!.surfaces.Append(s)
                } else {
                    val mat = overlayMaterial_s()
                    mat.material = mtr
                    mat.surfaces.Append(s)
                    materials.Append(mat)
                }
                surfNum++
            }

            // remove the oldest overlay surfaces if there are too many per material
            i = 0
            while (i < materials.Num()) {
                while (materials[i]!!.surfaces.Num() > MAX_OVERLAY_SURFACES) {
                    FreeSurface(materials[i]!!.surfaces[0])
                    materials[i]!!.surfaces.RemoveIndex(0)
                }
                i++
            }
        }

        // Creates new model surfaces for baseModel, which should be a static instantiation of a dynamic model.
        fun AddOverlaySurfacesToModel(baseModel: idRenderModel?) {
            var i: Int
            var j: Int
            var k: Int
            var numVerts: Int
            var numIndexes: Int
            val surfaceNum = CInt()
            var baseSurf: modelSurface_s?
            val staticModel: idRenderModelStatic
            var surf: overlaySurface_s?
            var newTri: srfTriangles_s?
            var newSurf: modelSurface_s?
            if (baseModel == null || baseModel.IsDefaultModel()) {
                return
            }

            // md5 models won't have any surfaces when r_showSkel is set
            if (0 == baseModel.NumSurfaces()) {
                return
            }
            if (baseModel.IsDynamicModel() != dynamicModel_t.DM_STATIC) {
                Common.common.Error("idRenderModelOverlay::AddOverlaySurfacesToModel: baseModel is not a static model")
            }

            assert(baseModel is idRenderModelStatic)
            staticModel = baseModel as idRenderModelStatic
            staticModel.overlaysAdded = 0
            if (0 == materials.Num()) {
                staticModel.DeleteSurfacesWithNegativeId()
                return
            }
            k = 0
            while (k < materials.Num()) {
                numIndexes = 0
                numVerts = numIndexes
                i = 0
                while (i < materials[k]!!.surfaces.Num()) {
                    numVerts += materials[k]!!.surfaces[i]!!.numVerts
                    numIndexes += materials[k]!!.surfaces[i]!!.numIndexes
                    i++
                }
                if (staticModel.FindSurfaceWithId(-1 - k, surfaceNum)) {
                    newSurf = staticModel.surfaces[surfaceNum.integerValue]
                } else {
                    newSurf = staticModel.surfaces.Alloc()
                    newSurf!!.geometry = null
                    newSurf.shader = materials[k]!!.material
                    newSurf.id = -1 - k
                }
                if ((newSurf!!.geometry == null) || (newSurf.geometry!!.numVerts < numVerts) || (newSurf.geometry!!.numIndexes < numIndexes)) {
                    R_FreeStaticTriSurf(newSurf.geometry)
                    newSurf.geometry = R_AllocStaticTriSurf()
                    R_AllocStaticTriSurfVerts(newSurf.geometry!!, numVerts)
                    R_AllocStaticTriSurfIndexes(newSurf.geometry!!, numIndexes)
                    SIMDProcessor!!.Memset(newSurf.geometry!!.verts as Array<Any>, 0, numVerts)
                } else {
                    R_FreeStaticTriSurfVertexCaches(newSurf.geometry!!)
                }
                newTri = newSurf.geometry
                numIndexes = 0
                numVerts = numIndexes
                i = 0
                while (i < materials[k]!!.surfaces.Num()) {
                    surf = materials[k]!!.surfaces[i]

                    // get the model surface for this overlay surface
                    if (surf!!.surfaceNum.integerValue < staticModel.NumSurfaces()) {
                        baseSurf = staticModel.Surface(surf.surfaceNum.integerValue)
                    } else {
                        baseSurf = null
                    }

                    // if the surface ids no longer match
                    if (null == baseSurf || baseSurf.id != surf.surfaceId) {
                        // find the surface with the correct id
                        if (staticModel.FindSurfaceWithId(surf.surfaceId, surf.surfaceNum)) {
                            baseSurf = staticModel.Surface(surf.surfaceNum.integerValue)
                        } else {
                            // the surface with this id no longer exists
                            FreeSurface(surf)
                            materials[k]!!.surfaces.RemoveIndex(i)
                            i--
                            i++
                            continue
                        }
                    }

                    // copy indexes;
                    j = 0
                    while (j < surf.numIndexes) {
                        newTri!!.indexes!![numIndexes + j] = numVerts + surf.indexes!![j]
                        j++
                    }
                    numIndexes += surf.numIndexes

                    // copy vertices
                    j = 0
                    while (j < surf.numVerts) {
                        val overlayVert: overlayVertex_s? = surf.verts!![j]
                        newTri!!.verts!![numVerts]!!.st[0] = overlayVert!!.st[0]
                        newTri.verts!![numVerts]!!.st[1] = overlayVert.st[1]
                        if (overlayVert.vertexNum >= baseSurf!!.geometry!!.numVerts) {
                            // This can happen when playing a demofile and a model has been changed since it was recorded, so just issue a warning and go on.
                            Common.common.Warning("idRenderModelOverlay::AddOverlaySurfacesToModel: overlay vertex out of range.  Model has probably changed since generating the overlay.")
                            FreeSurface(surf)
                            materials[k]!!.surfaces.RemoveIndex(i)
                            staticModel.DeleteSurfaceWithId(newSurf.id)
                            return
                        }
                        newTri.verts!![numVerts]!!.xyz.set(baseSurf.geometry!!.verts!![overlayVert.vertexNum]!!.xyz)
                        numVerts++
                        j++
                    }
                    i++
                }
                newTri!!.numVerts = numVerts
                newTri.numIndexes = numIndexes
                R_BoundTriSurf(newTri)
                staticModel.overlaysAdded++ // so we don't create an overlay on an overlay surface
                k++
            }
        }

        fun ReadFromDemoFile(f: idDemoFile?) {
            // FIXME: implement
        }

        fun WriteToDemoFile(f: idDemoFile?) {
            // FIXME: implement
        }

        //
        private fun FreeSurface(surface: overlaySurface_s?) {
            if (surface!!.verts != null) {
                surface.verts = null
            }
            if (surface.indexes != null) {
                surface.indexes = null
            }
        }

        companion object {
            fun Alloc(): idRenderModelOverlay {
                return idRenderModelOverlay()
            }

            fun Free(overlay: idRenderModelOverlay?) {
            }

            // Removes overlay surfaces from the model.
            fun RemoveOverlaySurfacesFromModel(baseModel: idRenderModel) {
                val staticModel: idRenderModelStatic

                assert(baseModel is idRenderModelStatic)
                staticModel = baseModel as idRenderModelStatic
                staticModel.DeleteSurfacesWithNegativeId()
                staticModel.overlaysAdded = 0
            }
        }
    }
}

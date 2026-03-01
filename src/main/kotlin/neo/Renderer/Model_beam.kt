/*
===========================================================================

Doom 3 GPL Source Code
Copyright (C) 1999-2011 id Software LLC, a ZeniMax Media company.
Translated to Kotlin by Dr. Feederino with support of Claude Code

This file is part of the Doom 3 GPL Source Code ("Doom 3 Source Code").
Original source: neo/renderer/Model_beam.cpp

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

import neo.Renderer.Model.dynamicModel_t
import neo.Renderer.Model.idRenderModel
import neo.Renderer.Model.modelSurface_s
import neo.Renderer.Model.srfTriangles_s
import neo.Renderer.Model_local.idRenderModelStatic
import neo.idlib.BV.idBounds
import neo.idlib.math.idMath.FtoiFast
import neo.idlib.math.idVec3
import neo.Renderer.RenderWorld.renderEntity_s as renderEntity_s1

object Model_beam {
    /*

     This is a simple dynamic model that just creates a stretched quad between
     two points that faces the view, like a dynamic deform tube.

     */
    val beam_SnapshotName: String = "_beam_Snapshot_"

    /*
     ===============================================================================

     Beam model

     ===============================================================================
     */
    class idRenderModelBeam : idRenderModelStatic() {
        override fun IsDynamicModel(): dynamicModel_t {
            return dynamicModel_t.DM_CONTINUOUS // regenerate for every view
        }

        override fun IsLoaded(): Boolean {
            return true // don't ever need to load
        }

        override fun InstantiateDynamicModel(
            renderEntity: renderEntity_s1?,
            viewDef: viewDef_s?,
            cachedModel: idRenderModel?
        ): idRenderModel? {
            var cachedModel: idRenderModel? = cachedModel
            val staticModel: idRenderModelStatic?
            val tri: srfTriangles_s?
            var surf: modelSurface_s? = modelSurface_s()
            if (cachedModel != null) {
                cachedModel = null
            }
            if (renderEntity == null || viewDef == null) {
                return null
            }
            if (cachedModel != null) {
                staticModel = cachedModel
                surf = staticModel.Surface(0)
                tri = surf!!.geometry
            } else {
                staticModel = idRenderModelStatic()
                staticModel.InitEmpty(beam_SnapshotName)
                tri = R_AllocStaticTriSurf()
                R_AllocStaticTriSurfVerts(tri, 4)
                R_AllocStaticTriSurfIndexes(tri, 6)
                tri.verts!![0]!!.Clear()
                tri.verts!![0]!!.st[0] = 0.0f
                tri.verts!![0]!!.st[1] = 0.0f
                tri.verts!![1]!!.Clear()
                tri.verts!![1]!!.st[0] = 0.0f
                tri.verts!![1]!!.st[1] = 1.0f
                tri.verts!![2]!!.Clear()
                tri.verts!![2]!!.st[0] = 1.0f
                tri.verts!![2]!!.st[1] = 0.0f
                tri.verts!![3]!!.Clear()
                tri.verts!![3]!!.st[0] = 1.0f
                tri.verts!![3]!!.st[1] = 1.0f
                tri.indexes!![0] = 0
                tri.indexes!![1] = 2
                tri.indexes!![2] = 1
                tri.indexes!![3] = 2
                tri.indexes!![4] = 3
                tri.indexes!![5] = 1
                tri.numVerts = 4
                tri.numIndexes = 6
                surf!!.geometry = tri
                surf.id = 0
                surf.shader = tr.defaultMaterial
                staticModel.AddSurface(surf)
            }
            val target = idVec3(renderEntity.shaderParms, RenderWorld.SHADERPARM_BEAM_END_X)

            // we need the view direction to project the minor axis of the tube
            // as the view changes
            val localView = idVec3()
            val localTarget = idVec3()
            val modelMatrix = FloatArray(16)
            tr_main.R_AxisToModelMatrix(renderEntity.axis, renderEntity.origin, modelMatrix)
            tr_main.R_GlobalPointToLocal(modelMatrix, viewDef.renderView.vieworg, localView)
            tr_main.R_GlobalPointToLocal(modelMatrix, target, localTarget)
            val major = idVec3(localTarget)
            val minor = idVec3()
            val mid = idVec3(localTarget.times(0.5f))
            val dir = idVec3(mid.minus(localView))
            minor.Cross(major, dir)
            minor.Normalize()
            if (renderEntity.shaderParms[RenderWorld.SHADERPARM_BEAM_WIDTH] != 0.0f) {
                minor.timesAssign(renderEntity.shaderParms[RenderWorld.SHADERPARM_BEAM_WIDTH] * 0.5f)
            }
            val red: Byte = FtoiFast(renderEntity.shaderParms[RenderWorld.SHADERPARM_RED] * 255.0f).toByte()
            val green: Byte = FtoiFast(renderEntity.shaderParms[RenderWorld.SHADERPARM_GREEN] * 255.0f).toByte()
            val blue: Byte = FtoiFast(renderEntity.shaderParms[RenderWorld.SHADERPARM_BLUE] * 255.0f).toByte()
            val alpha: Byte = FtoiFast(renderEntity.shaderParms[RenderWorld.SHADERPARM_ALPHA] * 255.0f).toByte()
            tri!!.verts!![0]!!.xyz.set(minor)
            tri.verts!![0]!!.color[0] = red
            tri.verts!![0]!!.color[1] = green
            tri.verts!![0]!!.color[2] = blue
            tri.verts!![0]!!.color[3] = alpha
            tri.verts!![1]!!.xyz.set(minor.unaryMinus())
            tri.verts!![1]!!.color[0] = red
            tri.verts!![1]!!.color[1] = green
            tri.verts!![1]!!.color[2] = blue
            tri.verts!![1]!!.color[3] = alpha
            tri.verts!![2]!!.xyz.set(localTarget.plus(minor))
            tri.verts!![2]!!.color[0] = red
            tri.verts!![2]!!.color[1] = green
            tri.verts!![2]!!.color[2] = blue
            tri.verts!![2]!!.color[3] = alpha
            tri.verts!![3]!!.xyz.set(localTarget.minus(minor))
            tri.verts!![3]!!.color[0] = red
            tri.verts!![3]!!.color[1] = green
            tri.verts!![3]!!.color[2] = blue
            tri.verts!![3]!!.color[3] = alpha
            R_BoundTriSurf(tri)
            staticModel.bounds.set(tri.bounds)
            return staticModel
        }

        override fun Bounds(renderEntity: renderEntity_s1?): idBounds {
            val b = idBounds()
            b.Zero()
            if (null == renderEntity) {
                b.ExpandSelf(8.0f)
            } else {
                val target = idVec3(renderEntity.shaderParms, RenderWorld.SHADERPARM_BEAM_END_X)
                val localTarget = idVec3()
                val modelMatrix = FloatArray(16)
                tr_main.R_AxisToModelMatrix(renderEntity.axis, renderEntity.origin, modelMatrix)
                tr_main.R_GlobalPointToLocal(modelMatrix, target, localTarget)
                b.AddPoint(localTarget)
                if (renderEntity.shaderParms[RenderWorld.SHADERPARM_BEAM_WIDTH] != 0.0f) {
                    b.ExpandSelf(renderEntity.shaderParms[RenderWorld.SHADERPARM_BEAM_WIDTH] * 0.5f)
                }
            }
            return b
        }
    }
}

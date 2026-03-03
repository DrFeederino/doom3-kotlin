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

import neo.Renderer.Material.deform_t
import neo.Renderer.Model.srfTriangles_s
import neo.Renderer.RenderWorld.renderEntity_s
import neo.framework.Common
import neo.framework.DeclParticle.idDeclParticle
import neo.framework.DeclParticle.idParticleStage
import neo.framework.DeclParticle.particleGen_t
import neo.framework.DeclTable.idDeclTable
import neo.idlib.BV.idBounds
import neo.idlib.containers.idBinSearch_LessEqual
import neo.idlib.geometry.DrawVert.idDrawVert
import neo.idlib.geometry.Winding.idWinding.Companion.TriangleArea
import neo.idlib.math.Matrix.idMat3.Companion.getMat3_identity
import neo.idlib.math.Random.idRandom
import neo.idlib.math.idPlane
import neo.idlib.math.idVec3
import neo.idlib.math.idVec3.Companion.generateArray
import neo.idlib.math.vec3_origin
import java.util.*

object tr_deform {
    val MAX_EYEBALL_ISLANDS: Int = 6

    /*
     =====================
     AddTriangleToIsland_r

     =====================
     */
    val MAX_EYEBALL_TRIS: Int = 10

    /*
     =====================
     R_WindingFromTriangles

     =====================
     */
    val MAX_TRI_WINDING_INDEXES: Int = 16

    val triIndexes: IntArray = intArrayOf(
        0, 4, 5,
        0, 5, 6,
        0, 6, 7,
        0, 7, 1,
        1, 7, 8,
        1, 8, 9,
        15, 4, 0,
        15, 0, 3,
        3, 0, 1,
        3, 1, 2,
        2, 1, 9,
        2, 9, 10,
        14, 15, 3,
        14, 3, 13,
        13, 3, 2,
        13, 2, 12,
        12, 2, 11,
        11, 2, 10
    )

    /*
     =====================
     R_TubeDeform

     will pivot a rectangular quad along the center of its long axis

     Note that a geometric tube with even quite a few sides tube will almost certainly render much faster
     than this, so this should only be for faked volumetric tubes.
     Make sure this is used with twosided translucent shaders, because the exact side
     order may not be correct.
     =====================
     */
    private val edgeVerts /*[6][2]*/: Array<IntArray> = arrayOf(
        intArrayOf(0, 1),
        intArrayOf(1, 2),
        intArrayOf(2, 0),
        intArrayOf(3, 4),
        intArrayOf(4, 5),
        intArrayOf(5, 3)
    )

    /*
     =================
     R_FinishDeform

     The ambientCache is on the stack, so we don't want to leave a reference
     to it that would try to be freed later.  Create the ambientCache immediately.
     =================
     */
    fun R_FinishDeform(drawSurf: drawSurf_s, newTri: srfTriangles_s?, ac: Array<idDrawVert?>?) {
        if (null == newTri) {
            return
        }

        // generate current normals, tangents, and bitangents
        // We might want to support the possibility of deform functions generating
        // explicit normals, and we might also want to allow the cached deformInfo
        // optimization for these.
        // FIXME: this doesn't work, because the deformed surface is just the
        // ambient one, and there isn't an opportunity to generate light interactions
        if (drawSurf.material!!.ReceivesLighting()) {
            newTri.verts = ac as Array<idDrawVert>?
            R_DeriveTangents(newTri, false)
            newTri.verts = null
        }
        newTri.ambientCache =
            VertexCache.vertexCache.AllocFrameTemp(ac as Array<idDrawVert>, newTri.numVerts * idDrawVert.BYTES)
        // if we are out of vertex cache, leave it the way it is
        if (newTri.ambientCache != null) {
            drawSurf.geo = newTri
        }
    }

    /*
     =====================
     R_AutospriteDeform

     Assuming all the triangles for this shader are independant
     quads, rebuild them as forward facing sprites
     =====================
     */
    fun R_AutospriteDeform(surf: drawSurf_s) {
        var i: Int
        var v: idDrawVert
        val mid = idVec3()
        val delta = idVec3()
        var radius: Float
        val left = idVec3()
        val up = idVec3()
        val leftDir = idVec3()
        val upDir = idVec3()
        val tri: srfTriangles_s
        val newTri: srfTriangles_s
        tri = surf.geo!!
        if ((tri.numVerts and 3) != 0) {
            Common.common.Warning("R_AutospriteDeform: shader had odd vertex count")
            return
        }
        if (tri.numIndexes != (tri.numVerts shr 2) * 6) {
            Common.common.Warning("R_AutospriteDeform: autosprite had odd index count")
            return
        }
        tr_main.R_GlobalVectorToLocal(surf.space!!.modelMatrix, tr.viewDef!!.renderView.viewaxis[1], leftDir)
        tr_main.R_GlobalVectorToLocal(surf.space!!.modelMatrix, tr.viewDef!!.renderView.viewaxis[2], upDir)
        if (tr.viewDef!!.isMirror) {
            leftDir.set(vec3_origin.minus(leftDir))
        }

        // this srfTriangles_t and all its indexes and caches are in frame
        // memory, and will be automatically disposed of
        newTri = srfTriangles_s()
        newTri.numVerts = tri.numVerts
        newTri.numIndexes = tri.numIndexes
        newTri.indexes = IntArray(newTri.numIndexes)
        val ac: Array<idDrawVert> = Array(newTri.numVerts) { idDrawVert() }
        i = 0
        while (i < tri.numVerts) {

            // find the midpoint
            v = tri.verts!![i]!!
            val v1: idDrawVert = tri.verts!![i + 1]!!
            val v2: idDrawVert = tri.verts!![i + 2]!!
            val v3: idDrawVert = tri.verts!![i + 3]!!
            mid[0] = 0.25f * (v.xyz[0] + v1.xyz[0] + v2.xyz[0] + v3.xyz[0])
            mid[1] = 0.25f * (v.xyz[1] + v1.xyz[1] + v2.xyz[1] + v3.xyz[1])
            mid[2] = 0.25f * (v.xyz[2] + v1.xyz[2] + v2.xyz[2] + v3.xyz[2])
            delta.set(v.xyz.minus(mid))
            radius = delta.Length() * 0.707f // / sqrt(2)
            left.set(leftDir.times(radius))
            up.set(upDir.times(radius))
            ac[i + 0].xyz.set(mid + left + up)
            ac[i + 0].st[0] = 0.0f
            ac[i + 0].st[1] = 0.0f
            ac[i + 1].xyz.set(mid - left + up)
            ac[i + 1].st[0] = 1.0f
            ac[i + 1].st[1] = 0.0f
            ac[i + 2].xyz.set(mid - left - up)
            ac[i + 2].st[0] = 1.0f
            ac[i + 2].st[1] = 1.0f
            ac[i + 3].xyz.set(mid + left - up)
            ac[i + 3].st[0] = 0.0f
            ac[i + 3].st[1] = 1.0f
            newTri.indexes!![6 * (i shr 2) + 0] = i
            newTri.indexes!![6 * (i shr 2) + 1] = i + 1
            newTri.indexes!![6 * (i shr 2) + 2] = i + 2
            newTri.indexes!![6 * (i shr 2) + 3] = i
            newTri.indexes!![6 * (i shr 2) + 4] = i + 2
            newTri.indexes!![6 * (i shr 2) + 5] = i + 3
            i += 4
        }
        R_FinishDeform(surf, newTri, ac as Array<idDrawVert?>)
    }

    fun R_TubeDeform(surf: drawSurf_s) {
        var i: Int
        var j: Int
        var indexes: Int
        val tri: srfTriangles_s
        tri = surf.geo!!
        if ((tri.numVerts and 3) != 0) {
            Common.common.Error("R_AutospriteDeform: shader had odd vertex count")
        }
        if (tri.numIndexes != (tri.numVerts shr 2) * 6) {
            Common.common.Error("R_AutospriteDeform: autosprite had odd index count")
        }

        // we need the view direction to project the minor axis of the tube
        // as the view changes
        val localView = idVec3()
        tr_main.R_GlobalPointToLocal(surf.space!!.modelMatrix, tr.viewDef!!.renderView.vieworg, localView)

        // this srfTriangles_t and all its indexes and caches are in frame
        // memory, and will be automatically disposed of
        val newTri = srfTriangles_s()
        newTri.numVerts = tri.numVerts
        newTri.numIndexes = tri.numIndexes
        newTri.indexes = IntArray(newTri.numIndexes)
        System.arraycopy(
            tri.indexes,
            0,
            newTri.indexes,
            0,
            newTri.numIndexes
        )
        val ac: Array<idDrawVert> = Array(newTri.numVerts) { idDrawVert() }

        // this is a lot of work for two triangles...
        // we could precalculate a lot if it is an issue, but it would mess up
        // the shader abstraction
        i = 0
        indexes = 0
        while (i < tri.numVerts) {
            val lengths = FloatArray(2)
            val nums = IntArray(2)
            val mid: Array<idVec3> = generateArray(2)
            val major = idVec3()
            val minor = idVec3()
            var v1: idDrawVert
            var v2: idDrawVert

            // identify the two shortest edges out of the six defined by the indexes
            nums[1] = 0
            nums[0] = nums[1]
            lengths[1] = 999999.0f
            lengths[0] = lengths[1]
            j = 0
            while (j < 6) {
                var l: Float
                v1 = tri.verts!![tri.indexes!![i + edgeVerts[j][0]]]!!
                v2 = tri.verts!![tri.indexes!![i + edgeVerts[j][1]]]!!
                l = (v1.xyz.minus(v2.xyz)).Length()
                if (l < lengths[0]) {
                    nums[1] = nums[0]
                    lengths[1] = lengths[0]
                    nums[0] = j
                    lengths[0] = l
                } else if (l < lengths[1]) {
                    nums[1] = j
                    lengths[1] = l
                }
                j++
            }

            // find the midpoints of the two short edges, which
            // will give us the major axis in object coordinates
            j = 0
            while (j < 2) {
                v1 = tri.verts!![tri.indexes!![i + edgeVerts[nums[j]][0]]]!!
                v2 = tri.verts!![tri.indexes!![i + edgeVerts[nums[j]][1]]]!!
                mid[j].set(
                    idVec3(
                        0.5f * (v1.xyz[0] + v2.xyz[0]),
                        0.5f * (v1.xyz[1] + v2.xyz[1]),
                        0.5f * (v1.xyz[2] + v2.xyz[2])
                    )
                )
                j++
            }

            // find the vector of the major axis
            major.set(mid[1].minus(mid[0]))

            // re-project the points
            j = 0
            while (j < 2) {
                var l: Float
                val i1: Int = tri.indexes!![i + edgeVerts[nums[j]][0]]
                val i2: Int = tri.indexes!![i + edgeVerts[nums[j]][1]]
                ac[i1] = idDrawVert(tri.verts!![i1]!!)
                val av1: idDrawVert = ac[i1]
                ac[i2] = idDrawVert(tri.verts!![i2]!!)
                val av2: idDrawVert = ac[i2]
                l = 0.5f * lengths[j]

                // cross this with the view direction to get minor axis
                val dir = idVec3(mid[j].minus(localView))
                minor.Cross(major, dir)
                minor.Normalize()
                if (j != 0) {
                    av1.xyz.set(mid[j].minus(minor.times(l)))
                    av2.xyz.set(mid[j].plus(minor.times(l)))
                } else {
                    av1.xyz.set(mid[j].plus(minor.times(l)))
                    av2.xyz.set(mid[j].minus(minor.times(l)))
                }
                j++
            }
            i += 4
            indexes += 6
        }
        R_FinishDeform(surf, newTri, ac as Array<idDrawVert?>)
    }

    fun R_WindingFromTriangles(
        tri: srfTriangles_s,
        indexes: IntArray
    ): Int {
        var i: Int
        var j: Int
        var k: Int
        var l: Int
        indexes[0] = tri.indexes!![0]
        var numIndexes = 1
        val numTris: Int = tri.numIndexes / 3
        do {
            // find an edge that goes from the current index to another
            // index that isn't already used, and isn't an internal edge
            i = 0
            while (i < numTris) {
                j = 0
                while (j < 3) {
                    if (tri.indexes!![i * 3 + j] != indexes[numIndexes - 1]) {
                        j++
                        continue
                    }
                    val next: Int = tri.indexes!![i * 3 + (j + 1) % 3]

                    // make sure it isn't already used
                    if (numIndexes == 1) {
                        if (next == indexes[0]) {
                            j++
                            continue
                        }
                    } else {
                        k = 1
                        while (k < numIndexes) {
                            if (indexes[k] == next) {
                                break
                            }
                            k++
                        }
                        if (k != numIndexes) {
                            j++
                            continue
                        }
                    }

                    // make sure it isn't an interior edge
                    k = 0
                    while (k < numTris) {
                        if (k == i) {
                            k++
                            continue
                        }
                        l = 0
                        while (l < 3) {
                            var a: Int
                            var b: Int
                            a = tri.indexes!![k * 3 + l]
                            if (a != next) {
                                l++
                                continue
                            }
                            b = tri.indexes!![k * 3 + (l + 1) % 3]
                            if (b != indexes[numIndexes - 1]) {
                                l++
                                continue
                            }

                            // this is an interior edge
                            break
                        }
                        if (l != 3) {
                            break
                        }
                        k++
                    }
                    if (k != numTris) {
                        j++
                        continue
                    }

                    // add this to the list
                    indexes[numIndexes] = next
                    numIndexes++
                    break
                }
                if (j != 3) {
                    break
                }
                i++
            }
            if (numIndexes == tri.numVerts) {
                break
            }
        } while (i != numTris)
        return numIndexes
    }

    fun R_FlareDeform(surf: drawSurf_s) {
        val tri: srfTriangles_s
        val newTri: srfTriangles_s
        val plane = idPlane()
        val dot: Float
        val localViewer = idVec3()
        var j: Int
        tri = surf.geo!!
        if (tri.numVerts != 4 || tri.numIndexes != 6) {
            //FIXME: temp hack for flares on tripleted models
            Common.common.Warning("R_FlareDeform: not a single quad")
            return
        }

        // this srfTriangles_t and all its indexes and caches are in frame
        // memory, and will be automatically disposed of
        newTri = srfTriangles_s()
        newTri.numVerts = 16
        newTri.numIndexes = 18 * 3
        newTri.indexes = IntArray(newTri.numIndexes)
        val ac: Array<idDrawVert?> = arrayOfNulls(newTri.numVerts)

        // find the plane
        if (!plane.FromPoints(
                tri.verts!![tri.indexes!![0]]!!.xyz,
                tri.verts!![tri.indexes!![1]]!!.xyz,
                tri.verts!![tri.indexes!![2]]!!.xyz
            )
        ) {
            Common.common.Warning("R_FlareDeform: plane.FromPoints failed")
            return
        }

        // if viewer is behind the plane, draw nothing
        tr_main.R_GlobalPointToLocal(surf.space!!.modelMatrix, tr.viewDef!!.renderView.vieworg, localViewer)
        val distFromPlane: Float = localViewer.times(plane.Normal()) + plane[3]
        if (distFromPlane <= 0) {
            newTri.numIndexes = 0
            surf.geo = newTri
            return
        }
        val center = idVec3()
        center.set(tri.verts!![0]!!.xyz)
        j = 1
        while (j < tri.numVerts) {
            center.plusAssign(tri.verts!![j]!!.xyz)
            j++
        }
        center.timesAssign(1.0f / tri.numVerts)
        val dir = idVec3(localViewer.minus(center))
        dir.Normalize()
        dot = dir.times(plane.Normal())

        // set vertex colors based on plane angle
        var color: Int = (dot * 8 * 256).toInt()
        if (color > 255) {
            color = 255
        }
        j = 0
        while (j < newTri.numVerts) {
            ac[j] = idDrawVert()
            ac[j]!!.color[2] = color.toByte()
            ac[j]!!.color[1] = ac[j]!!.color[2]
            ac[j]!!.color[0] = ac[j]!!.color[1]
            ac[j]!!.color[3] = 255.toByte()
            j++
        }
        val spread: Float =
            surf.shaderRegisters!![surf.material!!.GetDeformRegister(0)] * r_flareSize!!.GetFloat()
        val edgeDir: Array<Array<idVec3>> = generateArray(4, 3)
        val indexes = IntArray(MAX_TRI_WINDING_INDEXES)
        val numIndexes: Int = R_WindingFromTriangles(tri, indexes)

        // only deal with quads
        if (numIndexes != 4) {
            return
        }
        var i: Int
        // calculate vector directions
        i = 0
        while (i < 4) {
            ac[i]!!.xyz.set(tri.verts!![indexes[i]]!!.xyz)
            ac[i]!!.st[0] = ac[i]!!.st.set(1, 0.5f)
            val toEye = idVec3(tri.verts!![indexes[i]]!!.xyz.minus(localViewer))
            toEye.Normalize()
            val d1 = idVec3(tri.verts!![indexes[(i + 1) % 4]]!!.xyz.minus(localViewer))
            d1.Normalize()
            edgeDir[i][1].Cross(toEye, d1)
            edgeDir[i][1].Normalize()
            edgeDir[i][1].set(vec3_origin.minus(edgeDir[i][1]))
            val d2 = idVec3(tri.verts!![indexes[(i + 3) % 4]]!!.xyz.minus(localViewer))
            d2.Normalize()
            edgeDir[i][0].Cross(toEye, d2)
            edgeDir[i][0].Normalize()
            edgeDir[i][2].set(edgeDir[i][0].plus(edgeDir[i][1]))
            edgeDir[i][2].Normalize()
            i++
        }

        // build all the points
        ac[4]!!.xyz.set(tri.verts!![indexes[0]]!!.xyz.plus(edgeDir[0][0].times(spread)))
        ac[4]!!.st[0] = 0.0f
        ac[4]!!.st[1] = 0.5f
        ac[5]!!.xyz.set(tri.verts!![indexes[0]]!!.xyz.plus(edgeDir[0][2].times(spread)))
        ac[5]!!.st[0] = 0.0f
        ac[5]!!.st[1] = 0.0f
        ac[6]!!.xyz.set(tri.verts!![indexes[0]]!!.xyz.plus(edgeDir[0][1].times(spread)))
        ac[6]!!.st[0] = 0.5f
        ac[6]!!.st[1] = 0.0f
        ac[7]!!.xyz.set(tri.verts!![indexes[1]]!!.xyz.plus(edgeDir[1][0].times(spread)))
        ac[7]!!.st[0] = 0.5f
        ac[7]!!.st[1] = 0.0f
        ac[8]!!.xyz.set(tri.verts!![indexes[1]]!!.xyz.plus(edgeDir[1][2].times(spread)))
        ac[8]!!.st[0] = 1.0f
        ac[8]!!.st[1] = 0.0f
        ac[9]!!.xyz.set(tri.verts!![indexes[1]]!!.xyz.plus(edgeDir[1][1].times(spread)))
        ac[9]!!.st[0] = 1.0f
        ac[9]!!.st[1] = 0.5f
        ac[10]!!.xyz.set(tri.verts!![indexes[2]]!!.xyz.plus(edgeDir[2][0].times(spread)))
        ac[10]!!.st[0] = 1.0f
        ac[10]!!.st[1] = 0.5f
        ac[11]!!.xyz.set(tri.verts!![indexes[2]]!!.xyz.plus(edgeDir[2][2].times(spread)))
        ac[11]!!.st[0] = 1.0f
        ac[11]!!.st[1] = 1.0f
        ac[12]!!.xyz.set(tri.verts!![indexes[2]]!!.xyz.plus(edgeDir[2][1].times(spread)))
        ac[12]!!.st[0] = 0.5f
        ac[12]!!.st[1] = 1.0f
        ac[13]!!.xyz.set(tri.verts!![indexes[3]]!!.xyz.plus(edgeDir[3][0].times(spread)))
        ac[13]!!.st[0] = 0.5f
        ac[13]!!.st[1] = 1.0f
        ac[14]!!.xyz.set(tri.verts!![indexes[3]]!!.xyz.plus(edgeDir[3][2].times(spread)))
        ac[14]!!.st[0] = 0.0f
        ac[14]!!.st[1] = 1.0f
        ac[15]!!.xyz.set(tri.verts!![indexes[3]]!!.xyz.plus(edgeDir[3][1].times(spread)))
        ac[15]!!.st[0] = 0.0f
        ac[15]!!.st[1] = 0.5f
        i = 4
        while (i < 16) {
            dir.set(ac[i]!!.xyz.minus(localViewer))
            val len: Float = dir.Normalize()
            val ang: Float = dir.times(plane.Normal())

//		ac[i].xyz -= dir * spread * 2;
            val newLen: Float = -(distFromPlane / ang)
            if (newLen > 0 && newLen < len) {
                ac[i]!!.xyz.set(localViewer.plus(dir.times(newLen)))
            }
            ac[i]!!.st[0] = 0.0f
            ac[i]!!.st[1] = 0.5f
            i++
        }

        System.arraycopy(triIndexes, 0, newTri.indexes, 0, triIndexes.size)
        R_FinishDeform(surf, newTri, ac)
    }

    //=====================================================================================
    /*
     =====================
     R_ExpandDeform

     Expands the surface along it's normals by a shader amount
     =====================
     */
    fun R_ExpandDeform(surf: drawSurf_s) {
        var i: Int
        val tri: srfTriangles_s
        val newTri: srfTriangles_s
        tri = surf.geo!!

        // this srfTriangles_t and all its indexes and caches are in frame
        // memory, and will be automatically disposed of
        newTri = srfTriangles_s()
        newTri.numVerts = tri.numVerts
        newTri.numIndexes = tri.numIndexes
        newTri.indexes = tri.indexes
        val ac: Array<idDrawVert?> = arrayOfNulls(newTri.numVerts)
        val dist: Float = surf.shaderRegisters!![surf.material!!.GetDeformRegister(0)]
        i = 0
        while (i < tri.numVerts) {
            ac[i] = idDrawVert(tri.verts!![i])
            ac[i]!!.xyz.set(tri.verts!![i]!!.xyz.plus(tri.verts!![i]!!.normal.times(dist)))
            i++
        }
        R_FinishDeform(surf, newTri, ac)
    }

    //=====================================================================================
    /*
     =====================
     R_MoveDeform

     Moves the surface along the X axis, mostly just for demoing the deforms
     =====================
     */
    fun R_MoveDeform(surf: drawSurf_s) {
        var i: Int
        val tri: srfTriangles_s
        val newTri: srfTriangles_s
        tri = surf.geo!!

        // this srfTriangles_t and all its indexes and caches are in frame
        // memory, and will be automatically disposed of
        newTri = srfTriangles_s()
        newTri.numVerts = tri.numVerts
        newTri.numIndexes = tri.numIndexes
        newTri.indexes = tri.indexes
        val ac: Array<idDrawVert?> = arrayOfNulls(newTri.numVerts)
        val dist: Float = surf.shaderRegisters!![surf.material!!.GetDeformRegister(0)]
        i = 0
        while (i < tri.numVerts) {
            ac[i] = idDrawVert(tri.verts!![i])
            ac[i]!!.xyz.plusAssign(0, dist)
            i++
        }
        R_FinishDeform(surf, newTri, ac)
    }

    /*
     =====================
     R_TurbulentDeform

     Turbulently deforms the XYZ, S, and T values
     =====================
     */
    fun R_TurbulentDeform(surf: drawSurf_s) {
        var i: Int
        val tri: srfTriangles_s
        val newTri: srfTriangles_s
        tri = surf.geo!!

        // this srfTriangles_t and all its indexes and caches are in frame
        // memory, and will be automatically disposed of
        newTri = srfTriangles_s()
        newTri.numVerts = tri.numVerts
        newTri.numIndexes = tri.numIndexes
        newTri.indexes = tri.indexes
        val ac: Array<idDrawVert?> = arrayOfNulls(newTri.numVerts)
        val table: idDeclTable = surf.material!!.GetDeformDecl() as idDeclTable
        val range: Float = surf.shaderRegisters!![surf.material!!.GetDeformRegister(0)]
        val timeOfs: Float = surf.shaderRegisters!![surf.material!!.GetDeformRegister(1)]
        val domain: Float = surf.shaderRegisters!![surf.material!!.GetDeformRegister(2)]
        val tOfs = 0.5f
        i = 0
        while (i < tri.numVerts) {
            var f: Float = (tri.verts!![i]!!.xyz[0] * 0.003f
                    ) + (tri.verts!![i]!!.xyz[1] * 0.007f
                    ) + (tri.verts!![i]!!.xyz[2] * 0.011f)
            f = timeOfs + domain * f
            f += timeOfs
            ac[i] = idDrawVert(tri.verts!![i])
            ac[i]!!.st.plusAssign(0, range * table.TableLookup(f))
            ac[i]!!.st.plusAssign(1, range * table.TableLookup(f + tOfs))
            i++
        }
        R_FinishDeform(surf, newTri, ac)
    }

    fun AddTriangleToIsland_r(tri: srfTriangles_s, triangleNum: Int, usedList: BooleanArray, island: eyeIsland_t) {
        val a: Int
        val b: Int
        val c: Int
        usedList[triangleNum] = true

        // add to the current island
        if (island.numTris == MAX_EYEBALL_TRIS) {
            Common.common.Error("MAX_EYEBALL_TRIS")
        }
        island.tris[island.numTris] = triangleNum
        island.numTris++

        // recurse into all neighbors
        a = tri.indexes!![triangleNum * 3]
        b = tri.indexes!![triangleNum * 3 + 1]
        c = tri.indexes!![triangleNum * 3 + 2]
        island.bounds.AddPoint(tri.verts!![a]!!.xyz)
        island.bounds.AddPoint(tri.verts!![b]!!.xyz)
        island.bounds.AddPoint(tri.verts!![c]!!.xyz)
        val numTri: Int = tri.numIndexes / 3
        for (i in 0 until numTri) {
            if (usedList[i]) {
                continue
            }
            if ((tri.indexes!![i * 3 + 0] == a
                        ) || (tri.indexes!![i * 3 + 1] == a
                        ) || (tri.indexes!![i * 3 + 2] == a
                        ) || (tri.indexes!![i * 3 + 0] == b
                        ) || (tri.indexes!![i * 3 + 1] == b
                        ) || (tri.indexes!![i * 3 + 2] == b
                        ) || (tri.indexes!![i * 3 + 0] == c
                        ) || (tri.indexes!![i * 3 + 1] == c
                        ) || (tri.indexes!![i * 3 + 2] == c)
            ) {
                AddTriangleToIsland_r(tri, i, usedList, island)
            }
        }
    }

    /*
     =====================
     R_EyeballDeform

     Each eyeball surface should have an separate upright triangle behind it, long end
     pointing out the eye, and another single triangle in front of the eye for the focus point.
     =====================
     */
    fun R_EyeballDeform(surf: drawSurf_s) {
        var i: Int
        var j: Int
        var k: Int
        val tri: srfTriangles_s
        val newTri: srfTriangles_s
        val islands: Array<eyeIsland_t?> = arrayOfNulls(MAX_EYEBALL_ISLANDS)
        var numIslands: Int
        val triUsed = BooleanArray(MAX_EYEBALL_ISLANDS * MAX_EYEBALL_TRIS)
        tri = surf.geo!!

        // separate all the triangles into islands
        val numTri: Int = tri.numIndexes / 3
        if (numTri > MAX_EYEBALL_ISLANDS * MAX_EYEBALL_TRIS) {
            Common.common.Printf("R_EyeballDeform: too many triangles in surface")
            return
        }
        numIslands = 0
        while (numIslands < MAX_EYEBALL_ISLANDS) {
            islands[numIslands] = eyeIsland_t()
            islands[numIslands]!!.numTris = 0
            islands[numIslands]!!.bounds.Clear()
            i = 0
            while (i < numTri) {
                if (!triUsed[i]) {
                    AddTriangleToIsland_r(tri, i, triUsed, islands[numIslands]!!)
                    break
                }
                i++
            }
            if (i == numTri) {
                break
            }
            numIslands++
        }

        // assume we always have two eyes, two origins, and two targets
        if (numIslands != 3) {
            Common.common.Printf("R_EyeballDeform: %d triangle islands\n", numIslands)
            return
        }

        // this srfTriangles_t and all its indexes and caches are in frame
        // memory, and will be automatically disposed of
        // the surface cannot have more indexes or verts than the original
        newTri = srfTriangles_s()
        newTri.numVerts = tri.numVerts
        newTri.numIndexes = tri.numIndexes
        newTri.indexes = IntArray(tri.numIndexes)
        val ac: Array<idDrawVert> = Array(tri.numVerts) { idDrawVert() }
        newTri.numIndexes = 0

        // decide which islands are the eyes and points
        i = 0
        while (i < numIslands) {
            islands[i]!!.mid.set(islands[i]!!.bounds.GetCenter())
            i++
        }
        i = 0
        while (i < numIslands) {
            val island: eyeIsland_t? = islands[i]
            if (island!!.numTris == 1) {
                i++
                continue
            }

            // the closest single triangle point will be the eye origin
            // and the next-to-farthest will be the focal point
            val origin = idVec3()
            val focus = idVec3()
            var originIsland = 0
            val dist = FloatArray(MAX_EYEBALL_ISLANDS)
            val sortOrder = IntArray(MAX_EYEBALL_ISLANDS)
            j = 0
            while (j < numIslands) {
                val dir = idVec3(islands[j]!!.mid.minus(island.mid))
                dist[j] = dir.Length()
                sortOrder[j] = j
                k = j - 1
                while (k >= 0) {
                    if (dist[k] > dist[k + 1]) {
                        val temp: Int = sortOrder[k]
                        sortOrder[k] = sortOrder[k + 1]
                        sortOrder[k + 1] = temp
                        val ftemp: Float = dist[k]
                        dist[k] = dist[k + 1]
                        dist[k + 1] = ftemp
                    }
                    k--
                }
                j++
            }
            originIsland = sortOrder[1]
            origin.set(islands[originIsland]!!.mid)
            focus.set(islands[sortOrder[2]]!!.mid)

            // determine the projection directions based on the origin island triangle
            val dir = idVec3(focus.minus(origin))
            dir.Normalize()
            val p1: idVec3 = tri.verts!![tri.indexes!![islands[originIsland]!!.tris[0] + 0]]!!.xyz
            val p2: idVec3 = tri.verts!![tri.indexes!![islands[originIsland]!!.tris[0] + 1]]!!.xyz
            val p3: idVec3 = tri.verts!![tri.indexes!![islands[originIsland]!!.tris[0] + 2]]!!.xyz
            val v1 = idVec3(p2.minus(p1))
            v1.Normalize()
            val v2 = idVec3(p3.minus(p1))
            v2.Normalize()

            // texVec[0] will be the normal to the origin triangle
            val texVec: Array<idVec3> = generateArray(2)
            texVec[0].Cross(v1, v2)
            texVec[1].Cross(texVec[0], dir)
            j = 0
            while (j < 2) {
                texVec[j].minusAssign(dir.times(texVec[j].times(dir)))
                texVec[j].Normalize()
                j++
            }

            // emit these triangles, generating the projected texcoords
            j = 0
            while (j < islands[i]!!.numTris) {
                k = 0
                while (k < 3) {
                    var index: Int = islands[i]!!.tris[j] * 3
                    index = tri.indexes!!.get(index + k)
                    newTri.indexes!![newTri.numIndexes++] = index
                    ac[index].xyz.set(tri.verts!![index]!!.xyz)
                    val local = idVec3(tri.verts!![index]!!.xyz.minus(origin))
                    ac[index].st[0] = 0.5f + local.times(texVec[0])
                    ac[index].st[1] = 0.5f + local.times(texVec[1])
                    k++
                }
                j++
            }
            i++
        }
        R_FinishDeform(surf, newTri, ac as Array<idDrawVert?>)
    }

    //==========================================================================================
    /*
     =====================
     R_ParticleDeform

     Emit particles from the surface instead of drawing it
     =====================
     */
    fun R_ParticleDeform(surf: drawSurf_s, useArea: Boolean) {
        val renderEntity: renderEntity_s = surf.space!!.entityDef!!.parms
        val viewDef: viewDef_s = tr.viewDef!!
        val particleSystem: idDeclParticle = surf.material!!.GetDeformDecl() as idDeclParticle
        if (r_skipParticles!!.GetBool()) {
            return
        }

        //
        // calculate the area of all the triangles
        //
        val numSourceTris: Int = surf.geo!!.numIndexes / 3
        var totalArea = 0.0f
        var sourceTriAreas: Array<Float?>? = null
        val srcTri: srfTriangles_s = surf.geo!!
        if (useArea) {
            sourceTriAreas = arrayOfNulls(numSourceTris)
            var triNum = 0
            var i = 0
            while (i < srcTri.numIndexes) {
                var area: Float
                area = TriangleArea(
                    srcTri.verts!![srcTri.indexes!![i]]!!.xyz,
                    srcTri.verts!![srcTri.indexes!![i + 1]]!!.xyz,
                    srcTri.verts!![srcTri.indexes!![i + 2]]!!.xyz
                )
                sourceTriAreas[triNum] = totalArea
                totalArea += area
                i += 3
                triNum++
            }
        }

        //
        // create the particles almost exactly the way idRenderModelPrt does
        //
        val g = particleGen_t()
        g.renderEnt = renderEntity
        g.renderView = viewDef!!.renderView
        g.origin.Zero()
        g.axis.set(getMat3_identity())
        for (currentTri in 0 until (if ((useArea)) 1 else numSourceTris)) {
            for (stageNum in 0 until particleSystem.stages.Num()) {
                val stage: idParticleStage = particleSystem.stages[stageNum]
                if (null == stage.material) {
                    continue
                }
                if (0 == stage.cycleMsec) {
                    continue
                }
                if (stage.hidden) {        // just for gui particle editor use
                    continue
                }

                // we interpret stage.totalParticles as "particles per map square area"
                // so the systems look the same on different size surfaces
                val totalParticles: Int =
                    (if ((useArea)) stage.totalParticles * totalArea / 4096.0f else (stage.totalParticles)).toInt()
                val count: Int = totalParticles * stage.NumQuadsPerParticle()

                // allocate a srfTriangles in temp memory that can hold all the particles
                var tri: srfTriangles_s
                tri = srfTriangles_s()
                tri.numVerts = 4 * count
                tri.numIndexes = 6 * count
                tri.verts = idDrawVert.generateArray(tri.numVerts)
                tri.indexes = IntArray(tri.numIndexes)

                // just always draw the particles
                tri.bounds.set(stage.bounds)
                tri.numVerts = 0
                val steppingRandom = idRandom()
                val steppingRandom2 = idRandom()
                val stageAge: Int =
                    (g.renderView.time + renderEntity.shaderParms[RenderWorld.SHADERPARM_TIMEOFFSET] * 1000 - stage.timeOffset * 1000).toInt()
                val stageCycle: Int = stageAge / stage.cycleMsec
                var inCycleTime: Int = stageAge - stageCycle * stage.cycleMsec

                // some particles will be in this cycle, some will be in the previous cycle
                steppingRandom.SetSeed(
                    ((stageCycle shl 10) and idRandom.MAX_RAND) xor (renderEntity.shaderParms[RenderWorld.SHADERPARM_DIVERSITY] * idRandom.MAX_RAND).toInt()
                )
                steppingRandom2.SetSeed(
                    (((stageCycle - 1) shl 10) and idRandom.MAX_RAND) xor (renderEntity.shaderParms[RenderWorld.SHADERPARM_DIVERSITY] * idRandom.MAX_RAND).toInt()
                )
                for (index in 0 until totalParticles) {
                    g.index = index

                    // bump the random
                    steppingRandom.RandomInt()
                    steppingRandom2.RandomInt()

                    // calculate local age for this index
                    val bunchOffset: Int =
                        (stage.particleLife * 1000 * stage.spawnBunching * index / totalParticles).toInt()
                    val particleAge: Int = stageAge - bunchOffset
                    val particleCycle: Int = particleAge / stage.cycleMsec
                    if (particleCycle < 0) {
                        // before the particleSystem spawned
                        continue
                    }
                    if (stage.cycles != 0.0f && particleCycle >= stage.cycles) {
                        // cycled systems will only run cycle times
                        continue
                    }
                    if (particleCycle == stageCycle) {
                        g.random = idRandom(steppingRandom)
                    } else {
                        g.random = idRandom(steppingRandom2)
                    }
                    inCycleTime = particleAge - particleCycle * stage.cycleMsec
                    if ((renderEntity.shaderParms[RenderWorld.SHADERPARM_PARTICLE_STOPTIME] != 0.0f
                                && g.renderView.time - inCycleTime >= renderEntity.shaderParms[RenderWorld.SHADERPARM_PARTICLE_STOPTIME] * 1000)
                    ) {
                        // don't fire any more particles
                        continue
                    }

                    // supress particles before or after the age clamp
                    g.frac = inCycleTime.toFloat() / (stage.particleLife * 1000)
                    if (g.frac < 0) {
                        // yet to be spawned
                        continue
                    }
                    if (g.frac > 1.0f) {
                        // this particle is in the deadTime band
                        continue
                    }

                    //---------------
                    // locate the particle origin and axis somewhere on the surface
                    //---------------
                    var pointTri: Int = currentTri
                    if (useArea) {
                        // select a triangle based on an even area distribution
                        pointTri = idBinSearch_LessEqual<Float>(
                            sourceTriAreas as Array<Float>,
                            numSourceTris,
                            g.random.RandomFloat() * totalArea
                        )
                    }

                    // now pick a random point inside pointTri
                    val v1: idDrawVert = srcTri.verts!![srcTri.indexes!![pointTri * 3 + 0]]
                    val v2: idDrawVert = srcTri.verts!![srcTri.indexes!![pointTri * 3 + 1]]
                    val v3: idDrawVert = srcTri.verts!![srcTri.indexes!![pointTri * 3 + 2]]
                    var f1: Float = g.random.RandomFloat()
                    var f2: Float = g.random.RandomFloat()
                    var f3: Float = g.random.RandomFloat()
                    val ft: Float = 1.0f / (f1 + f2 + f3 + 0.0001f)
                    f1 *= ft
                    f2 *= ft
                    f3 *= ft
                    g.origin.set(v1!!.xyz.times(f1).plus(v2!!.xyz.times(f2).plus(v3!!.xyz.times(f3))))
                    g.axis[0] = v1.tangents[0].times(f1)
                        .plus(v2.tangents[0].times(f2).plus(v3.tangents[0].times(f3)))
                    g.axis[1] = v1.tangents[1].times(f1)
                        .plus(v2.tangents[1].times(f2).plus(v3.tangents[1].times(f3)))
                    g.axis[2] = v1.normal.times(f1).plus(v2.normal.times(f2).plus(v3.normal.times(f3)))

                    //-----------------------
                    // this is needed so aimed particles can calculate origins at different times
                    g.originalRandom = idRandom(g.random)
                    g.age = g.frac * stage.particleLife

                    // if the particle doesn't get drawn because it is faded out or beyond a kill region,
                    // don't increment the verts
                    tri.numVerts += stage.CreateParticle(
                        g,
                        Arrays.copyOfRange<idDrawVert?>(tri.verts, tri.numVerts, tri.verts!!.size)
                    )
                }
                if (tri.numVerts > 0) {
                    // build the index list
                    var indexes = 0
                    var i = 0
                    while (i < tri.numVerts) {
                        tri.indexes!![indexes + 0] = i
                        tri.indexes!![indexes + 1] = i + 2
                        tri.indexes!![indexes + 2] = i + 3
                        tri.indexes!![indexes + 3] = i
                        tri.indexes!![indexes + 4] = i + 3
                        tri.indexes!![indexes + 5] = i + 1
                        indexes += 6
                        i += 4
                    }
                    tri.numIndexes = indexes
                    tri.ambientCache =
                        VertexCache.vertexCache.AllocFrameTemp(
                            tri.verts!!,
                            tri.numVerts * idDrawVert.BYTES
                        )
                    if (tri.ambientCache != null) {
                        // add the drawsurf
                        tr_light.R_AddDrawSurf(tri, surf.space!!, renderEntity, stage.material!!, surf.scissorRect)
                    }
                }
            }
        }
    }

    /*
     =================
     R_DeformDrawSurf
     =================
     */
    fun R_DeformDrawSurf(drawSurf: drawSurf_s) {
        if (null == drawSurf.material) {
            return
        }
        if (r_skipDeforms!!.GetBool()) {
            return
        }
        when (drawSurf.material!!.Deform()) {
            deform_t.DFRM_NONE -> return
            deform_t.DFRM_SPRITE -> R_AutospriteDeform(drawSurf)
            deform_t.DFRM_TUBE -> R_TubeDeform(drawSurf)
            deform_t.DFRM_FLARE -> R_FlareDeform(drawSurf)
            deform_t.DFRM_EXPAND -> R_ExpandDeform(drawSurf)
            deform_t.DFRM_MOVE -> R_MoveDeform(drawSurf)
            deform_t.DFRM_TURB -> R_TurbulentDeform(drawSurf)
            deform_t.DFRM_EYEBALL -> R_EyeballDeform(drawSurf)
            deform_t.DFRM_PARTICLE -> R_ParticleDeform(drawSurf, true)
            deform_t.DFRM_PARTICLE2 -> R_ParticleDeform(drawSurf, false)
            else -> return
        }
    }

    //========================================================================================
    class eyeIsland_t {
        val bounds: idBounds = idBounds()
        val mid: idVec3 = idVec3()
        var numTris: Int = 0
        var tris: IntArray = IntArray(MAX_EYEBALL_TRIS)
    }
}

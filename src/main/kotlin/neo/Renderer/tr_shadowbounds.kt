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

import neo.framework.Common
import neo.idlib.BV.idBounds
import neo.idlib.colorBlue
import neo.idlib.colorGreen
import neo.idlib.colorRed
import neo.idlib.colorYellow
import neo.idlib.math.Matrix.idMat4
import neo.idlib.math.idVec3
import neo.idlib.math.idVec4

object tr_shadowbounds {

    // Compute conservative shadow bounds as the intersection
    // of the object's bounds' shadow volume and the light's bounds.
    //
    // --cass

    private val lut: Array<polyhedron> = Array(64) { polyhedron() }
    private val p: polyhedron = polyhedron()

    fun four_ints(a: Int, b: Int, c: Int, d: Int): MyArrayInt {
        val vi = MyArrayInt()
        vi.push_back(a)
        vi.push_back(b)
        vi.push_back(c)
        vi.push_back(d)
        return vi
    }

    fun homogeneous_difference(a: idVec4, b: idVec4): idVec3 {
        val v = idVec3()
        v.x = b.x * a.w - a.x * b.w
        v.y = b.y * a.w - a.y * b.w
        v.z = b.z * a.w - a.z * b.w
        return v
    }

    // handles positive w only
    // C++ passes a, b, c by value — must copy to avoid mutating caller's data
    fun compute_homogeneous_plane(a_in: idVec4, b_in: idVec4, c_in: idVec4): idVec4 {
        val a = idVec4(a_in)
        val b = idVec4(b_in)
        val c = idVec4(c_in)

        val v = idVec4()
        val t = idVec4()

        if (a[3] == 0.0f) {
            t.set(a)
            a.set(b)
            b.set(c)
            c.set(t)
        }
        if (a[3] == 0.0f) {
            t.set(a)
            a.set(b)
            b.set(c)
            c.set(t)
        }

        // can't handle 3 infinite points
        if (a[3] == 0.0f) {
            return v
        }
        val vb = idVec3(homogeneous_difference(a, b))
        val vc = idVec3(homogeneous_difference(a, c))
        val n = idVec3(vb.Cross(vc))
        n.Normalize()
        v.x = n.x
        v.y = n.y
        v.z = n.z
        v.w = -n.times(idVec3(a.x, a.y, a.z)) / a.w
        return v
    }

    // make a unit cube
    fun PolyhedronFromBounds(b: idBounds): polyhedron {

//       3----------2
//       |\        /|
//       | \      / |
//       |   7--6   |
//       |   |  |   |
//       |   4--5   |
//       |  /    \  |
//       | /      \ |
//       0----------1
//
        if (p.e.size() == 0) {
            p.v.push_back(idVec4(-1, -1, 1, 1))
            p.v.push_back(idVec4(1, -1, 1, 1))
            p.v.push_back(idVec4(1, 1, 1, 1))
            p.v.push_back(idVec4(-1, 1, 1, 1))
            p.v.push_back(idVec4(-1, -1, -1, 1))
            p.v.push_back(idVec4(1, -1, -1, 1))
            p.v.push_back(idVec4(1, 1, -1, 1))
            p.v.push_back(idVec4(-1, 1, -1, 1))
            p.add_quad(0, 1, 2, 3)
            p.add_quad(7, 6, 5, 4)
            p.add_quad(1, 0, 4, 5)
            p.add_quad(2, 1, 5, 6)
            p.add_quad(3, 2, 6, 7)
            p.add_quad(0, 3, 7, 4)
            p.compute_neighbors()
            p.recompute_planes()
            p.v.empty() // no need to copy this data since it'll be replaced
        }
        val p2 = polyhedron(p)
        val min = idVec3(b[0])
        val max = idVec3(b[1])
        p2.v.empty()
        p2.v.push_back(idVec4(min.x, min.y, max.z, 1.0f))
        p2.v.push_back(idVec4(max.x, min.y, max.z, 1.0f))
        p2.v.push_back(idVec4(max.x, max.y, max.z, 1.0f))
        p2.v.push_back(idVec4(min.x, max.y, max.z, 1.0f))
        p2.v.push_back(idVec4(min.x, min.y, min.z, 1.0f))
        p2.v.push_back(idVec4(max.x, min.y, min.z, 1.0f))
        p2.v.push_back(idVec4(max.x, max.y, min.z, 1.0f))
        p2.v.push_back(idVec4(min.x, max.y, min.z, 1.0f))
        p2.recompute_planes()
        return p2
    }

    fun make_sv(oc: polyhedron, light: idVec4?): polyhedron {
        var index = 0
        for (i in 0..5) {
            if ((oc.p[i]!!.plane.times(light!!)) > 0) {
                index = index or (1 shl i)
            }
        }
        if (lut[index].e.size() == 0) {
            lut[index] = polyhedron(oc)
            val ph: polyhedron = lut[index]
            val V: Int = ph.v.size()
            for (j in 0 until V) {
                val proj = idVec3(homogeneous_difference(light!!, ph.v[j]!!))
                ph.v.push_back(idVec4(proj.x, proj.y, proj.z, 0.0f))
            }
            ph.p.empty()
            for (i in 0 until oc.p.size()) {
                if ((oc.p[i]!!.plane.times(light!!)) > 0) {
                    ph.p.push_back(poly(oc.p[i]!!))
                }
            }
            if (ph.p.size() == 0) {
                lut[index] = polyhedron()
                return lut[index]
            }
            ph.compute_neighbors()
            val vpg = MyArrayPoly()
            val I: Int = ph.p.size()
            for (i in 0 until I) {
                val vi: MyArrayInt = ph.p[i]!!.vi
                val ni: MyArrayInt = ph.p[i]!!.ni
                val S: Int = vi.size()
                for (j in 0 until S) {
                    if (ni[j] == -1) {
                        val pg = poly()
                        val a: Int = vi[(j + 1) % S]!!
                        val b: Int = vi[j]!!
                        pg.vi = four_ints(a, b, b + V, a + V)
                        pg.ni = four_ints(-1, -1, -1, -1)
                        vpg.push_back(pg)
                    }
                }
            }
            for (i in 0 until vpg.size()) {
                ph.p.push_back(vpg[i]!!)
            }
            ph.compute_neighbors()
            ph.v.empty() // no need to copy this data since it'll be replaced
        }
        val ph2 = polyhedron(lut[index])

        // initalize vertices
        ph2.v = MyArrayVec4()
        for (j in 0 until oc.v.size()) {
            ph2.v.push_back(idVec4(oc.v[j]!!))
        }
        val V: Int = ph2.v.size()
        for (j in 0 until V) {
            val proj = idVec3(homogeneous_difference(light!!, ph2.v[j]!!))
            ph2.v.push_back(idVec4(proj.x, proj.y, proj.z, 0.0f))
        }

        // need to compute planes for the shadow volume (sv)
        ph2.recompute_planes()
        return ph2
    }

    fun polyhedron_edges(a: polyhedron, e: MySegments) {
        e.empty()
        if (a.e.size() == 0 && a.p.size() != 0) {
            a.compute_neighbors()
        }
        for (i in 0 until a.e.size()) {
            e.push_back(a.v[a.e[i]!!.vi[0]]!!)
            e.push_back(a.v[a.e[i]!!.vi[1]]!!)
        }
    }

    // clip the segments of e by the planes of polyhedron a.
    fun clip_segments(ph: polyhedron, `is`: MySegments, os: MySegments) {
        val p: MyArrayPoly = ph.p
        var i = 0
        while (i < `is`.size()) {
            val a = idVec4()
            a.set(`is`[i]!!)
            val b = idVec4()
            b.set(`is`[i + 1]!!)
            val c = idVec4()
            var discard = false
            for (j in 0 until p.size()) {
                val da: Float = a.times(p[j]!!.plane)
                val db: Float = b.times(p[j]!!.plane)
                val rdw: Float = 1 / (da - db)
                var code = 0
                if (da > 0) {
                    code = 2
                }
                if (db > 0) {
                    code = code or 1
                }
                when (code) {
                    3 -> discard = true
                    2 -> {
                        c.set(a * rdw * -db + b * rdw * da)
                        a.set(c)
                    }

                    1 -> {
                        c.set(a * -db * rdw + b * da * rdw)
                        b.set(c)
                    }

                    0 -> {}
                    else -> Common.common.Printf("bad clip code!\n")
                }
                if (discard) {
                    break
                }
            }
            if (!discard) {
                os.push_back(a)
                os.push_back(b)
            }
            i += 2
        }
    }

    fun make_idMat4(m: FloatArray): idMat4 {
        return idMat4(
            m[0], m[4], m[8], m[12],
            m[1], m[5], m[9], m[13],
            m[2], m[6], m[10], m[14],
            m[3], m[7], m[11], m[15]
        )
    }

    fun v4to3(v: idVec4): idVec3 {
        return idVec3(v.x / v.w, v.y / v.w, v.z / v.w)
    }

    fun draw_polyhedron(viewDef: viewDef_s, p: polyhedron, color: idVec4) {
        for (i in 0 until p.e.size()) {
            viewDef.renderWorld!!.DebugLine(
                color, v4to3(p.v[p.e[i]!!.vi[0]]!!), v4to3(
                    p.v[p.e[i]!!.vi[1]]!!
                )
            )
        }
    }

    fun draw_segments(viewDef: viewDef_s, s: MySegments, color: idVec4) {
        var i = 0
        while (i < s.size()) {
            viewDef.renderWorld!!.DebugLine(color, v4to3(s[i]!!), v4to3(s[i + 1]!!))
            i += 2
        }
    }

    fun world_to_hclip(viewDef: viewDef_s, global: idVec4, clip: idVec4) {
        var i: Int
        val view = idVec4()
        i = 0
        while (i < 4) {
            view[i] = (global[0] * viewDef.worldSpace.modelViewMatrix[i + 0 * 4]
                    ) + (global[1] * viewDef.worldSpace.modelViewMatrix[i + 1 * 4]
                    ) + (global[2] * viewDef.worldSpace.modelViewMatrix[i + 2 * 4]
                    ) + (global[3] * viewDef.worldSpace.modelViewMatrix[i + 3 * 4])
            i++
        }
        i = 0
        while (i < 4) {
            clip[i] = (view[0] * viewDef.projectionMatrix[i + 0 * 4]
                    ) + (view[1] * viewDef.projectionMatrix[i + 1 * 4]
                    ) + (view[2] * viewDef.projectionMatrix[i + 2 * 4]
                    ) + (view[3] * viewDef.projectionMatrix[i + 3 * 4])
            i++
        }
    }

    fun R_CalcIntersectionScissor(
        lightDef: idRenderLightLocal,
        entityDef: idRenderEntityLocal,
        viewDef: viewDef_s
    ): idScreenRect {
        val omodel: idMat4 = make_idMat4(entityDef.modelMatrix)

        // compute light polyhedron
        val lvol: polyhedron = PolyhedronFromBounds(lightDef.frustumTris!!.bounds)
        // transform it into world space
        //lvol.transform( lmodel );

        // debug //
        if (r_useInteractionScissors.GetInteger() == -2) {
            draw_polyhedron(viewDef, lvol, colorRed)
        }

        // compute object polyhedron
        val vol: polyhedron = PolyhedronFromBounds(entityDef.referenceBounds)

        // transform it into world space
        vol.transform(omodel)

        // debug //
        if (r_useInteractionScissors.GetInteger() == -2) {
            draw_polyhedron(viewDef, vol, colorBlue)
        }

        // transform light position into world space
        val lightpos = idVec4(
            lightDef.globalLightOrigin.x,
            lightDef.globalLightOrigin.y,
            lightDef.globalLightOrigin.z,
            1.0f
        )

        // generate shadow volume "polyhedron"
        val sv: polyhedron = make_sv(vol, lightpos)
        val in_segs = MySegments()
        val out_segs = MySegments()

        // get shadow volume edges
        polyhedron_edges(sv, in_segs)
        // clip them against light bounds planes
        clip_segments(lvol, in_segs, out_segs)

        // get light bounds edges
        polyhedron_edges(lvol, in_segs)
        // clip them by the shadow volume
        clip_segments(sv, in_segs, out_segs)

        // debug //
        if (r_useInteractionScissors.GetInteger() == -2) {
            draw_segments(viewDef, out_segs, colorGreen)
        }
        val outbounds = idBounds()
        outbounds.Clear()
        for (i in 0 until out_segs.size()) {
            val v = idVec4()
            world_to_hclip(viewDef, out_segs[i]!!, v)
            if (v.w <= 0.0f) {
                return lightDef.viewLight!!.scissorRect!!
            }
            val rv = idVec3(v.x, v.y, v.z)
            rv.divAssign(v.w)
            outbounds.AddPoint(rv)
        }

        // limit the bounds to avoid an inside out scissor rectangle due to floating point to short conversion
        if (outbounds[0].x < -1.0f) {
            outbounds[0].x = -1.0f
        }
        if (outbounds[1].x > 1.0f) {
            outbounds[1].x = 1.0f
        }
        if (outbounds[0].y < -1.0f) {
            outbounds[0].y = -1.0f
        }
        if (outbounds[1].y > 1.0f) {
            outbounds[1].y = 1.0f
        }
        val w2: Float = (viewDef.viewport.x2 - viewDef.viewport.x1 + 1) / 2.0f
        val x: Float = viewDef.viewport.x1.toFloat()
        val h2: Float = (viewDef.viewport.y2 - viewDef.viewport.y1 + 1) / 2.0f
        val y: Float = viewDef.viewport.y1.toFloat()
        val rect = idScreenRect()
        rect.x1 = ((outbounds[0].x * w2) + w2 + x).toInt()
        rect.x2 = ((outbounds[1].x * w2) + w2 + x).toInt()
        rect.y1 = ((outbounds[0].y * h2) + h2 + y).toInt()
        rect.y2 = ((outbounds[1].y * h2) + h2 + y).toInt()
        rect.Expand()
        rect.Intersect(lightDef.viewLight!!.scissorRect!!)

        // debug //
        if (r_useInteractionScissors.GetInteger() == -2 && !rect.IsEmpty()) {
            viewDef.renderWorld!!.DebugScreenRect(colorYellow, rect, viewDef)
        }
        return rect
    }

    @Suppress("UNCHECKED_CAST")
    open class MyArray<T>(private val capacity: Int) {
        var s: Int = 0
        var v: Array<T?> = arrayOfNulls<Any>(capacity) as Array<T?>

        fun copyFrom(src: MyArray<T>) {
            s = src.s
            for (i in 0 until s) {
                v[i] = src.v[i]
            }
        }

        fun push_back(i: T) {
            v[s] = i
            s++
        }

        operator fun get(index: Int): T? {
            return v[index]
        }

        operator fun set(index: Int, value: T): T {
            return value.also { v[index] = it }
        }

        fun size(): Int {
            return s
        }

        fun empty() {
            s = 0
        }
    }

    class MyArrayInt : MyArray<Int> {
        constructor() : super(4)
        constructor(src: MyArrayInt) : super(4) {
            copyFrom(src)
        }
    }

    class MyArrayVec4 : MyArray<idVec4> {
        constructor() : super(16)
        constructor(src: MyArrayVec4) : super(16) {
            copyFrom(src)
        }
    }

    class poly {
        var vi: MyArrayInt = MyArrayInt()
        var ni: MyArrayInt = MyArrayInt()
        val plane: idVec4 = idVec4()

        constructor()

        constructor(src: poly) {
            vi = MyArrayInt(src.vi)
            ni = MyArrayInt(src.ni)
            plane.set(src.plane)
        }
    }

    class MyArrayPoly : MyArray<poly> {
        constructor() : super(9)
        constructor(src: MyArrayPoly) : super(9) {
            // Deep copy: each poly must be independently copied
            s = src.s
            for (i in 0 until s) {
                v[i] = poly(src.v[i]!!)
            }
        }
    }

    class edge {
        var pi: IntArray = IntArray(2)
        var vi: IntArray = IntArray(2)

        constructor()

        constructor(src: edge) {
            pi = src.pi.copyOf()
            vi = src.vi.copyOf()
        }
    }

    class MyArrayEdge : MyArray<edge> {
        constructor() : super(15)
        constructor(src: MyArrayEdge) : super(15) {
            s = src.s
            for (i in 0 until s) {
                v[i] = edge(src.v[i]!!)
            }
        }
    }

    class polyhedron {
        var e: MyArrayEdge = MyArrayEdge()
        var p: MyArrayPoly = MyArrayPoly()
        var v: MyArrayVec4 = MyArrayVec4()

        constructor()

        constructor(src: polyhedron) {
            // Deep copy v (idVec4 elements are mutable, need independent copies)
            v = MyArrayVec4()
            v.s = src.v.s
            for (i in 0 until src.v.s) {
                v.v[i] = if (src.v.v[i] != null) idVec4(src.v.v[i]!!) else null
            }
            // Deep copy p (poly elements contain mutable sub-arrays)
            p = MyArrayPoly(src.p)
            // Deep copy e (edge elements contain IntArrays)
            e = MyArrayEdge(src.e)
        }

        fun add_quad(va: Int, vb: Int, vc: Int, vd: Int) {
            val pg = poly()
            pg.vi = four_ints(va, vb, vc, vd)
            pg.ni = four_ints(-1, -1, -1, -1)
            pg.plane.set(compute_homogeneous_plane(v[va]!!, v[vb]!!, v[vc]!!))
            p.push_back(pg)
        }

        fun discard_neighbor_info() {
            for (i in 0 until p.size()) {
                val ni: MyArrayInt = p[i]!!.ni
                for (j in 0 until ni.size()) {
                    ni[j] = -1
                }
            }
        }

        fun compute_neighbors() {
            e.empty()
            discard_neighbor_info()
            var found: Boolean
            val P: Int = p.size()
            // for each polygon
            for (i in 0 until (P - 1)) {
                val vi: MyArrayInt = p[i]!!.vi
                val ni: MyArrayInt = p[i]!!.ni
                val Si: Int = vi.size()

                // for each edge of that polygon
                for (ii in 0 until Si) {
                    val ii0: Int = ii
                    val ii1: Int = (ii + 1) % Si

                    // continue if we've already found this neighbor
                    if (ni[ii] != -1) {
                        continue
                    }
                    found = false
                    // check all remaining polygons
                    for (j in i + 1 until P) {
                        val vj: MyArrayInt = p[j]!!.vi
                        val nj: MyArrayInt = p[j]!!.ni
                        val Sj: Int = vj.size()
                        for (jj in 0 until Sj) {
                            val jj0: Int = jj
                            val jj1: Int = (jj + 1) % Sj
                            if (vi[ii0] == vj[jj1] && vi[ii1] == vj[jj0]) {
                                val ed = edge()
                                ed.vi[0] = vi[ii0]!!
                                ed.vi[1] = vi[ii1]!!
                                ed.pi[0] = i
                                ed.pi[1] = j
                                e.push_back(ed)
                                ni[ii] = j
                                nj[jj] = i
                                found = true
                                break
                            } else if (vi[ii0] == vj[jj0] && vi[ii1] == vj[jj1]) {
                                System.err.printf("why am I here?\n")
                            }
                        }
                        if (found) {
                            break
                        }
                    }
                }
            }
        }

        fun recompute_planes() {
            // for each polygon
            for (i in 0 until p.size()) {
                p[i]!!.plane.set(
                    compute_homogeneous_plane(
                        v[p[i]!!.vi[0]!!]!!,
                        v[p[i]!!.vi[1]!!]!!,
                        v[p[i]!!.vi[2]!!]!!
                    )
                )
            }
        }

        fun transform(m: idMat4) {
            for (i in 0 until v.size()) {
                v[i] = m.times(v[i]!!)
            }
            recompute_planes()
        }
    }

    class MySegments : MyArray<idVec4>(36) {
    }
}

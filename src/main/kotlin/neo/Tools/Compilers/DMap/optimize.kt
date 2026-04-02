package neo.Tools.Compilers.DMap

import neo.Renderer.qgl
import neo.Tools.Compilers.DMap.dmap.mapTri_s
import neo.Tools.Compilers.DMap.dmap.optimizeGroup_s
import neo.Tools.Compilers.DMap.dmap.uEntity_t
import neo.framework.Common
import neo.idlib.BV.idBounds
import neo.idlib.containers.List.cmp_t
import neo.idlib.geometry.DrawVert.idDrawVert
import neo.idlib.math.*
import neo.idlib.math.Random.idRandom
import org.lwjgl.opengl.GL11
import java.util.*

object optimize {
    // optimize.cpp -- trianlge mesh reoptimization
    //
    // the shadow volume optimizer call internal optimizer routines, normal triangles
    // will just be done by OptimizeEntity()
    //
    const val COLINEAR_EPSILON = 0.1f

    //
    const val MAX_OPT_EDGES = 0x40000

    //
    const val MAX_OPT_VERTEXES = 0x10000
    var numOptEdges = 0
    var numOptVerts = 0
    var numOriginalEdges = 0
    val optBounds: idBounds = idBounds()
    var optEdges: Array<optEdge_s> = Array(MAX_OPT_EDGES) { optEdge_s() }
    var optVerts: Array<optVertex_s> = Array(MAX_OPT_VERTEXES) { optVertex_s() }

    //
    //static bool IsTriangleValid( const optVertex_s *v1, const optVertex_s *v2, const optVertex_s *v3 );
    //static bool IsTriangleDegenerate( const optVertex_s *v1, const optVertex_s *v2, const optVertex_s *v3 );
    //
    var orandom: idRandom = idRandom()
    var originalEdges: Array<originalEdges_t> = emptyArray()

    /*

     New vertexes will be created where edges cross.

     optimization requires an accurate t junction fixer.



     *//*
     ==============
     ValidateEdgeCounts
     ==============
     */
    fun ValidateEdgeCounts(island: optIsland_t) {
        var vert: optVertex_s?
        var e: optEdge_s?
        var c: Int
        vert = island.verts
        while (vert != null) {
            c = 0
            e = vert.edges
            while (e != null) {
                c++
                if (e.v1 === vert) {
                    e = e.v1link
                } else if (e.v2 === vert) {
                    e = e.v2link
                } else {
                    Common.common.Error("ValidateEdgeCounts: mislinked")
                }
            }
            if (c != 2 && c != 0) {
                // this can still happen at diamond intersections
//			common.Printf( "ValidateEdgeCounts: %i edges\n", c );
            }
            vert = vert.islandLink
        }
    }

    /*
     ====================
     AllocEdge
     ====================
     */
    fun AllocEdge(): optEdge_s {
        val e: optEdge_s
        if (numOptEdges == MAX_OPT_EDGES) {
            Common.common.Error("MAX_OPT_EDGES")
        }
        optEdges[numOptEdges] = optEdge_s()
        e = optEdges[numOptEdges]
        numOptEdges++
        //	memset( e, 0, sizeof( *e ) );
        return e
    }

    /*
     ====================
     RemoveEdgeFromVert
     ====================
     */
    fun RemoveEdgeFromVert(e1: optEdge_s, vert: optVertex_s?) {
        // C++ uses optEdge_t **prev = &vert->edges; ... *prev = e1->v1link; ... prev = &e->v1link;
        // Kotlin: track previous edge and which link field (v1link or v2link) it used, to update the correct field.
        if (null == vert) {
            return
        }
        var prevEdge: optEdge_s? = null
        var prevIsV1 = false // which link field of prevEdge leads to current edge
        var e: optEdge_s? = vert.edges
        while (e != null) {
            if (e == e1) {
                // determine what e1's next in this vert's chain is
                val next: optEdge_s? = if (e1.v1 == vert) {
                    e1.v1link
                } else if (e1.v2 == vert) {
                    e1.v2link
                } else {
                    Common.common.Error("RemoveEdgeFromVert: vert not found")
                    return
                }
                // unlink: *prev = next
                if (prevEdge == null) {
                    vert.edges = next
                } else {
                    if (prevIsV1) {
                        prevEdge.v1link = next
                    } else {
                        prevEdge.v2link = next
                    }
                }
                return
            }
            // advance: prev = &e->v1link or &e->v2link
            prevEdge = e
            if (e.v1 == vert) {
                prevIsV1 = true
                e = e.v1link
            } else if (e.v2 == vert) {
                prevIsV1 = false
                e = e.v2link
            } else {
                Common.common.Error("RemoveEdgeFromVert: vert not found")
                return
            }
        }
    }

    /*
     ====================
     UnlinkEdge
     ====================
     */
    fun UnlinkEdge(e: optEdge_s, island: optIsland_t) {
        RemoveEdgeFromVert(e, e.v1)
        RemoveEdgeFromVert(e, e.v2)
        // C++ uses optEdge_t **prev = &island->edges; for (prev; *prev; prev = &(*prev)->islandLink)
        var prevEdge: optEdge_s? = null
        var cur: optEdge_s? = island.edges
        while (cur != null) {
            if (cur == e) {
                if (prevEdge == null) {
                    island.edges = e.islandLink
                } else {
                    prevEdge.islandLink = e.islandLink
                }
                return
            }
            prevEdge = cur
            cur = cur.islandLink
        }
        Common.common.Error("RemoveEdgeFromIsland: couldn't free edge")
    }

    /*
     ====================
     LinkEdge
     ====================
     */
    fun LinkEdge(e: optEdge_s) {
        e.v1link = e.v1!!.edges
        e.v1!!.edges = e
        e.v2link = e.v2!!.edges
        e.v2!!.edges = e
    }

    /*
     ================
     FindOptVertex
     ================
     */
    fun FindOptVertex(v: idDrawVert, opt: optimizeGroup_s): optVertex_s? {
        var i: Int
        val x: Float
        val y: Float
        val vert: optVertex_s?

        // deal with everything strictly as 2D
        x = v.xyz.times(opt.axis[0])
        y = v.xyz.times(opt.axis[1])

        // should we match based on the t-junction fixing hash verts?
        i = 0
        while (i < numOptVerts) {
            if (optVerts[i].pv[0] == x && optVerts[i].pv[1] == y) {
                return optVerts[i]
            }
            i++
        }
        if (numOptVerts >= MAX_OPT_VERTEXES) {
            Common.common.Error("MAX_OPT_VERTEXES")
            return null
        }
        numOptVerts++
        optVerts[i] = optVertex_s()
        vert = optVerts[i]
        vert.v = v
        vert.pv[0] = x
        vert.pv[1] = y
        vert.pv[2] = 0.0f
        optBounds.AddPoint(vert.pv)
        return vert
    }

    /*
     ================
     DrawAllEdges
     ================
     */
    fun DrawAllEdges() {
        var i: Int
        if (!dmap.dmapGlobals.drawflag) {
            return
        }
        gldraw.Draw_ClearWindow()
        qgl.qglBegin(GL11.GL_LINES)
        i = 0
        while (i < numOptEdges) {
            if (optEdges[i].v1 == null) {
                i++
                continue
            }
            qgl.qglColor3f(1.0f, 0.0f, 0.0f)
            qgl.qglVertex3fv(optEdges[i].v1!!.pv.ToFloatPtr())
            qgl.qglColor3f(0.0f, 0.0f, 0.0f)
            qgl.qglVertex3fv(optEdges[i].v2!!.pv.ToFloatPtr())
            i++
        }
        qgl.qglEnd()
        qgl.qglFlush()

//	GLimp_SwapBuffers();
    }

    /*
     ================
     DrawVerts
     ================
     */
    fun DrawVerts(island: optIsland_t) {
        var vert: optVertex_s?
        if (!dmap.dmapGlobals.drawflag) {
            return
        }
        qgl.qglEnable(GL11.GL_BLEND)
        qgl.qglBlendFunc(GL11.GL_ONE, GL11.GL_ONE)
        qgl.qglColor3f(0.3f, 0.3f, 0.3f)
        qgl.qglPointSize(3f)
        qgl.qglBegin(GL11.GL_POINTS)
        vert = island.verts
        while (vert != null) {
            qgl.qglVertex3fv(vert.pv.ToFloatPtr())
            vert = vert.islandLink
        }
        qgl.qglEnd()
        qgl.qglDisable(GL11.GL_BLEND)
        qgl.qglFlush()
    }

    /*
     ================
     DrawEdges
     ================
     */
    fun DrawEdges(island: optIsland_t) {
        var edge: optEdge_s?
        if (!dmap.dmapGlobals.drawflag) {
            return
        }
        gldraw.Draw_ClearWindow()
        qgl.qglBegin(GL11.GL_LINES)
        edge = island.edges
        while (edge != null) {
            if (edge.v1 == null) {
                edge = edge.islandLink
                continue
            }
            qgl.qglColor3f(1.0f, 0.0f, 0.0f)
            qgl.qglVertex3fv(edge.v1!!.pv.ToFloatPtr())
            qgl.qglColor3f(0.0f, 0.0f, 0.0f)
            qgl.qglVertex3fv(edge.v2!!.pv.ToFloatPtr())
            edge = edge.islandLink
        }
        qgl.qglEnd()
        qgl.qglFlush()

//	GLimp_SwapBuffers();
    }

    /*
     =================
     VertexBetween
     =================
     */
    fun VertexBetween(p1: optVertex_s, v1: optVertex_s, v2: optVertex_s): Boolean {
        val d1 = idVec3()
        val d2 = idVec3()
        val d: Float
        d1.set(p1.pv.minus(v1.pv))
        d2.set(p1.pv.minus(v2.pv))
        d = d1.times(d2)
        return d < 0
    }

    //#ifdef __linux__
    //
    //optVertex_s *FindOptVertex( idDrawVert *v, optimizeGroup_s *opt );
    //
    //#else
    /*
     ====================
     EdgeIntersection

     Creates a new optVertex_s where the line segments cross.
     This should only be called if PointsStraddleLine returned true

     Will return NULL if the lines are colinear
     ====================
     */
    fun EdgeIntersection(
        p1: optVertex_s, p2: optVertex_s, l1: optVertex_s, l2: optVertex_s, opt: optimizeGroup_s
    ): optVertex_s? {
        val f: Float
        val v: idDrawVert
        val dir1 = idVec3()
        val dir2 = idVec3()
        val cross1 = idVec3()
        val cross2 = idVec3()
        dir1.set(p1.pv.minus(l1.pv))
        dir2.set(p1.pv.minus(l2.pv))
        cross1.set(dir1.Cross(dir2))
        dir1.set(p2.pv.minus(l1.pv))
        dir2.set(p2.pv.minus(l2.pv))
        cross2.set(dir1.Cross(dir2))
        if (cross1[2] - cross2[2] == 0.0f) {
            return null
        }
        f = cross1[2] / (cross1[2] - cross2[2])

        // FIXME: how are we freeing this, since it doesn't belong to a tri?
        v = idDrawVert() // Mem_Alloc(sizeof(v));
        //	memset( v, 0, sizeof( *v ) );
        v.xyz.set(p1.v.xyz.times(1.0f - f).plus(p2.v.xyz.times(f)))
        v.normal.set(p1.v.normal.times(1.0f - f).plus(p2.v.normal.times(f)))
        v.normal.Normalize()
        v.st[0] = p1.v.st[0] * (1.0f - f) + p2.v.st[0] * f
        v.st[1] = p1.v.st[1] * (1.0f - f) + p2.v.st[1] * f
        return FindOptVertex(v, opt)
    }

    //#endif
    /*
     ====================
     PointsStraddleLine

     Colinear is considdered crossing.
     ====================
     */
    fun PointsStraddleLine(p1: optVertex_s, p2: optVertex_s, l1: optVertex_s, l2: optVertex_s): Boolean {
        var t1: Boolean
        var t2: Boolean
        t1 = IsTriangleDegenerate(l1, l2, p1)
        t2 = IsTriangleDegenerate(l1, l2, p2)
        return if (t1 && t2) {
            // colinear case
            val s1: Float
            val s2: Float
            val s3: Float
            val s4: Float
            val positive: Boolean
            val negative: Boolean
            s1 = p1.pv.minus(l1.pv).times(l2.pv.minus(l1.pv))
            s2 = p2.pv.minus(l1.pv).times(l2.pv.minus(l1.pv))
            s3 = p1.pv.minus(l2.pv).times(l2.pv.minus(l1.pv))
            s4 = p2.pv.minus(l2.pv).times(l2.pv.minus(l1.pv))
            positive = s1 > 0 || s2 > 0 || s3 > 0 || s4 > 0
            negative = s1 < 0 || s2 < 0 || s3 < 0 || s4 < 0
            positive && negative
        } else if (p1 != l1 && p1 != l2 && p2 != l1 && p2 != l2) {
            // no shared verts
            t1 = IsTriangleValid(l1, l2, p1)
            t2 = IsTriangleValid(l1, l2, p2)
            if (t1 && t2) {
                return false
            }
            t1 = IsTriangleValid(l1, p1, l2)
            t2 = IsTriangleValid(l1, p2, l2)
            !t1 || !t2
        } else {
            // a shared vert, not colinear, so not crossing
            false
        }
    }

    /*
     ====================
     EdgesCross
     ====================
     */
    fun EdgesCross(a1: optVertex_s?, a2: optVertex_s?, b1: optVertex_s?, b2: optVertex_s?): Boolean {
        // if both verts match, consider it to be crossed
        if (a1 == b1 && a2 == b2) {
            return true
        }
        if (a1 == b2 && a2 == b1) {
            return true
        }
        // if only one vert matches, it might still be colinear, which
        // would be considered crossing

        // if both lines' verts are on opposite sides of the other
        // line, it is crossed
        return if (!PointsStraddleLine(a1!!, a2!!, b1!!, b2!!)) {
            false
        } else PointsStraddleLine(b1, b2, a1, a2)
    }

    /*
     ====================
     TryAddNewEdge

     ====================
     */
    fun TryAddNewEdge(v1: optVertex_s, v2: optVertex_s, island: optIsland_t): Boolean {
        var e: optEdge_s?

        // if the new edge crosses any other edges, don't add it
        e = island.edges
        while (e != null) {
            if (EdgesCross(e.v1, e.v2, v1, v2)) {
                return false
            }
            e = e.islandLink
        }
        if (dmap.dmapGlobals.drawflag) {
            qgl.qglBegin(GL11.GL_LINES)
            qgl.qglColor3f(0.0f, (128 + orandom.RandomInt(127)) / 255.0f, 0.0f)
            qgl.qglVertex3fv(v1.pv.ToFloatPtr())
            qgl.qglVertex3fv(v2.pv.ToFloatPtr())
            qgl.qglEnd()
            qgl.qglFlush()
        }
        // add it
        e = AllocEdge()
        e.islandLink = island.edges
        island.edges = e
        e.v1 = v1
        e.v2 = v2
        e.created = true

        // link the edge to its verts
        LinkEdge(e)
        return true
    }

    //=================================================================
    /*
     ==================
     AddInteriorEdges

     Add all possible edges between the verts
     ==================
     */
    fun AddInteriorEdges(island: optIsland_t) {
        var c_addedEdges: Int
        var vert: optVertex_s?
        var vert2: optVertex_s?
        var c_verts: Int
        var lengths: Array<edgeLength_t>
        var numLengths: Int
        var i: Int
        DrawVerts(island)

        // count the verts
        c_verts = 0
        vert = island.verts
        while (vert != null) {
            if (vert.edges == null) {
                vert = vert.islandLink
                continue
            }
            c_verts++
            vert = vert.islandLink
        }

        // allocate space for all the lengths
        lengths = Array(c_verts * c_verts / 2) { edgeLength_t() } // Mem_Alloc(c_verts * c_verts / 2);
        numLengths = 0
        vert = island.verts
        while (vert != null) {
            if (vert.edges == null) {
                vert = vert.islandLink
                continue
            }
            vert2 = vert.islandLink
            while (vert2 != null) {
                val dir = idVec3()
                if (vert2.edges == null) {
                    vert2 = vert2.islandLink
                    continue
                }
                lengths[numLengths].v1 = vert
                lengths[numLengths].v2 = vert2
                dir.set(vert.pv.minus(vert2.pv))
                lengths[numLengths].length = dir.Length()
                numLengths++
                vert2 = vert2.islandLink
            }
            vert = vert.islandLink
        }

        // sort by length, shortest first
//        qsort(lengths, numLengths, sizeof(lengths[0]), LengthSort);
        Arrays.sort(lengths, 0, numLengths, LengthSort())

        // try to create them in that order
        c_addedEdges = 0
        i = 0
        while (i < numLengths) {
            if (TryAddNewEdge(lengths[i].v1!!, lengths[i].v2!!, island)) {
                c_addedEdges++
            }
            i++
        }
        if (dmap.dmapGlobals.verbose) {
            Common.common.Printf("%6d tested segments\n", numLengths)
            Common.common.Printf("%6d added interior edges\n", c_addedEdges)
        }
    }

    /*
     ====================
     RemoveIfColinear

     ====================
     */
    fun RemoveIfColinear(ov: optVertex_s, island: optIsland_t) {
        var e: optEdge_s?
        var e1: optEdge_s?
        var e2: optEdge_s?
        var v1 = optVertex_s()
        val v2: optVertex_s?
        var v3 = optVertex_s()
        val dir1 = idVec3()
        val dir2 = idVec3()
        val len: Float
        var dist: Float
        val point = idVec3()
        val offset = idVec3()
        val off: Float
        v2 = ov

        // we must find exactly two edges before testing for colinear
        e1 = null
        e2 = null
        e = ov.edges
        while (e != null) {
            if (e1 == null) {
                e1 = e
            } else if (e2 == null) {
                e2 = e
            } else {
                return  // can't remove a vertex with three edges
            }
            if (e.v1 === v2) {
                e = e.v1link
            } else if (e.v2 === v2) {
                e = e.v2link
            } else {
                Common.common.Error("RemoveIfColinear: mislinked edge")
            }
        }

        // can't remove if no edges
        if (null == e1) {
            return
        }
        if (null == e2) {
            // this may still happen legally when a tiny triangle is
            // the only thing in a group
            Common.common.Printf("WARNING: vertex with only one edge\n")
            return
        }
        if (e1.v1 == v2) {
            v1 = e1.v2!!
        } else if (e1.v2 == v2) {
            v1 = e1.v1!!
        } else {
            Common.common.Error("RemoveIfColinear: mislinked edge")
        }
        if (e2.v1 == v2) {
            v3 = e2.v2!!
        } else if (e2.v2 == v2) {
            v3 = e2.v1!!
        } else {
            Common.common.Error("RemoveIfColinear: mislinked edge")
        }
        if (v1 == v3) {
            Common.common.Error("RemoveIfColinear: mislinked edge")
        }

        // they must point in opposite directions
        dist = v3.pv.minus(v2.pv).times(v1.pv.minus(v2.pv))
        if (dist >= 0) {
            return
        }

        // see if they are colinear
        VectorSubtract(v3.v.xyz, v1.v.xyz, dir1)
        len = dir1.Normalize()
        VectorSubtract(v2.v.xyz, v1.v.xyz, dir2)
        dist = DotProduct(dir2, dir1)
        VectorMA(v1.v.xyz, dist, dir1, point)
        VectorSubtract(point, v2.v.xyz, offset)
        off = offset.Length()
        if (off > COLINEAR_EPSILON) {
            return
        }
        if (dmap.dmapGlobals.drawflag) {
            qgl.qglBegin(GL11.GL_LINES)
            qgl.qglColor3f(1.0f, 1.0f, 0.0f)
            qgl.qglVertex3fv(v1.pv.ToFloatPtr())
            qgl.qglVertex3fv(v2.pv.ToFloatPtr())
            qgl.qglEnd()
            qgl.qglFlush()
            qgl.qglBegin(GL11.GL_LINES)
            qgl.qglColor3f(0.0f, 1.0f, 1.0f)
            qgl.qglVertex3fv(v2.pv.ToFloatPtr())
            qgl.qglVertex3fv(v3.pv.ToFloatPtr())
            qgl.qglEnd()
            qgl.qglFlush()
        }

        // replace the two edges with a single edge
        UnlinkEdge(e1, island)
        UnlinkEdge(e2, island)

        // v2 should have no edges now
        if (v2.edges != null) {
            Common.common.Error("RemoveIfColinear: didn't remove properly")
        }

        // if there is an existing edge that already
        // has these exact verts, we have just collapsed a
        // sliver triangle out of existance, and all the edges
        // can be removed
        e = island.edges
        while (e != null) {
            if (e.v1 === v1 && e.v2 === v3 || e.v1 === v3 && e.v2 === v1) {
                UnlinkEdge(e, island)
                RemoveIfColinear(v1, island)
                RemoveIfColinear(v3, island)
                return
            }
            e = e.islandLink
        }

        // if we can't add the combined edge, link
        // the originals back in
        if (!TryAddNewEdge(v1, v3, island)) {
            e1.islandLink = island.edges
            island.edges = e1
            LinkEdge(e1)
            e2.islandLink = island.edges
            island.edges = e2
            LinkEdge(e2)
            return
        }

        // recursively try to combine both verts now,
        // because things may have changed since the last combine test
        RemoveIfColinear(v1, island)
        RemoveIfColinear(v3, island)
    }

    /*
     ====================
     CombineColinearEdges
     ====================
     */
    fun CombineColinearEdges(island: optIsland_t) {
        var c_edges: Int
        var ov: optVertex_s?
        var e: optEdge_s?
        c_edges = 0
        e = island.edges
        while (e != null) {
            c_edges++
            e = e.islandLink
        }
        if (dmap.dmapGlobals.verbose) {
            Common.common.Printf("%6d original exterior edges\n", c_edges)
        }
        ov = island.verts
        while (ov != null) {
            RemoveIfColinear(ov, island)
            ov = ov.islandLink
        }
        c_edges = 0
        e = island.edges
        while (e != null) {
            c_edges++
            e = e.islandLink
        }
        if (dmap.dmapGlobals.verbose) {
            Common.common.Printf("%6d optimized exterior edges\n", c_edges)
        }
    }

    /*
     ===================
     FreeOptTriangles

     ===================
     */
    fun FreeOptTriangles(island: optIsland_t) {
        var opt: optTri_s?
        var next: optTri_s?
        opt = island.tris
        while (opt != null) {
            next = opt.next
            opt = null //Mem_Free(opt);
            opt = next
        }
        island.tris = null
    }

    /*
     =================
     IsTriangleValid

     empty area will be considered invalid.
     Due to some truly aweful epsilon issues, a triangle can switch between
     valid and invalid depending on which order you look at the verts, so
     consider it invalid if any one of the possibilities is invalid.
     =================
     */
    fun IsTriangleValid(v1: optVertex_s, v2: optVertex_s, v3: optVertex_s): Boolean {
        val d1 = idVec3()
        val d2 = idVec3()
        val normal = idVec3()
        d1.set(v2.pv.minus(v1.pv))
        d2.set(v3.pv.minus(v1.pv))
        normal.set(d1.Cross(d2))
        if (normal[2] <= 0) {
            return false
        }
        d1.set(v3.pv.minus(v2.pv))
        d2.set(v1.pv.minus(v2.pv))
        normal.set(d1.Cross(d2))
        if (normal[2] <= 0) {
            return false
        }
        d1.set(v1.pv.minus(v3.pv))
        d2.set(v2.pv.minus(v3.pv))
        normal.set(d1.Cross(d2))
        return normal[2] > 0
    }

    /*
     =================
     IsTriangleDegenerate

     Returns false if it is either front or back facing
     =================
     */
    fun IsTriangleDegenerate(v1: optVertex_s, v2: optVertex_s, v3: optVertex_s): Boolean {
//#if 1
        val d1 = idVec3()
        val d2 = idVec3()
        val normal = idVec3()
        d1.set(v2.pv.minus(v1.pv))
        d2.set(v3.pv.minus(v1.pv))
        normal.set(d1.Cross(d2))
        return normal[2] == 0.0f
        //#else
//	return (bool)!IsTriangleValid( v1, v2, v3 );
//#endif
    }

    /*
     ==================
     PointInTri

     Tests if a 2D point is inside an original triangle
     ==================
     */
    fun PointInTri(p: idVec3, tri: mapTri_s, island: optIsland_t): Boolean {
        val d1 = idVec3()
        val d2 = idVec3()
        val normal = idVec3()

        // the normal[2] == 0 case is not uncommon when a square is triangulated in
        // the opposite manner to the original
        d1.set(tri.optVert[0].pv.minus(p))
        d2.set(tri.optVert[1].pv.minus(p))
        normal.set(d1.Cross(d2))
        if (normal[2] < 0) {
            return false
        }
        d1.set(tri.optVert[1].pv.minus(p))
        d2.set(tri.optVert[2].pv.minus(p))
        normal.set(d1.Cross(d2))
        if (normal[2] < 0) {
            return false
        }
        d1.set(tri.optVert[2].pv.minus(p))
        d2.set(tri.optVert[0].pv.minus(p))
        normal.set(d1.Cross(d2))
        return normal[2] >= 0
    }

    //==================================================================
    /*
     ====================
     LinkTriToEdge

     ====================
     */
    fun LinkTriToEdge(optTri: optTri_s, edge: optEdge_s) {
        if (edge.v1 == optTri.v[0] && edge.v2 == optTri.v[1] || edge.v1 == optTri.v[1] && edge.v2 == optTri.v[2] || edge.v1 == optTri.v[2] && edge.v2 == optTri.v[0]) {
            if (edge.backTri != null) {
                Common.common.Printf("Warning: LinkTriToEdge: already in use\n")
                return
            }
            edge.backTri = optTri
            return
        }
        if (edge.v1 == optTri.v[1] && edge.v2 == optTri.v[0] || edge.v1 == optTri.v[2] && edge.v2 == optTri.v[1] || edge.v1 == optTri.v[0] && edge.v2 == optTri.v[2]) {
            if (edge.frontTri != null) {
                Common.common.Printf("Warning: LinkTriToEdge: already in use\n")
                return
            }
            edge.frontTri = optTri
            return
        }
        Common.common.Error("LinkTriToEdge: edge not found on tri")
    }

    /*
     ===============
     CreateOptTri
     ===============
     */
    fun CreateOptTri(first: optVertex_s, e1: optEdge_s, e2: optEdge_s, island: optIsland_t) {
        var opposite: optEdge_s?
        var second = optVertex_s()
        var third = optVertex_s()
        val optTri: optTri_s
        var tri: mapTri_s?
        if (e1.v1 == first) {
            second = e1.v2!!
        } else if (e1.v2 == first) {
            second = e1.v1!!
        } else {
            Common.common.Error("CreateOptTri: mislinked edge")
        }
        if (e2.v1 == first) {
            third = e2.v2!!
        } else if (e2.v2 == first) {
            third = e2.v1!!
        } else {
            Common.common.Error("CreateOptTri: mislinked edge")
        }
        if (!IsTriangleValid(first, second, third)) {
            Common.common.Error("CreateOptTri: invalid")
        }

//DrawEdges( island );
        // identify the third edge
        if (dmap.dmapGlobals.drawflag) {
            qgl.qglColor3f(1.0f, 1.0f, 0.0f)
            qgl.qglBegin(GL11.GL_LINES)
            qgl.qglVertex3fv(e1.v1!!.pv.ToFloatPtr())
            qgl.qglVertex3fv(e1.v2!!.pv.ToFloatPtr())
            qgl.qglEnd()
            qgl.qglFlush()
            qgl.qglColor3f(0.0f, 1.0f, 1.0f)
            qgl.qglBegin(GL11.GL_LINES)
            qgl.qglVertex3fv(e2.v1!!.pv.ToFloatPtr())
            qgl.qglVertex3fv(e2.v2!!.pv.ToFloatPtr())
            qgl.qglEnd()
            qgl.qglFlush()
        }
        opposite = second.edges
        while (opposite != null) {
            if (opposite != e1 && (opposite.v1 == third || opposite.v2 == third)) {
                break
            }
            if (opposite.v1 == second) {
                opposite = opposite.v1link
            } else if (opposite.v2 == second) {
                opposite = opposite.v2link
            } else {
                Common.common.Error("BuildOptTriangles: mislinked edge")
            }
        }
        if (opposite == null) {
            Common.common.Printf("Warning: BuildOptTriangles: couldn't locate opposite\n")
            return
        }
        if (dmap.dmapGlobals.drawflag) {
            qgl.qglColor3f(1.0f, 0.0f, 1.0f)
            qgl.qglBegin(GL11.GL_LINES)
            qgl.qglVertex3fv(opposite!!.v1!!.pv.ToFloatPtr())
            qgl.qglVertex3fv(opposite.v2!!.pv.ToFloatPtr())
            qgl.qglEnd()
            qgl.qglFlush()
        }

        // create new triangle
        optTri = optTri_s() // Mem_Alloc(sizeof(optTri));
        optTri.v[0] = first
        optTri.v[1] = second
        optTri.v[2] = third
        optTri.midpoint.set(
            optTri.v[0].pv.plus(optTri.v[1].pv.plus(optTri.v[2].pv)).times(1.0f / 3.0f)
        )
        optTri.next = island.tris
        island.tris = optTri
        if (dmap.dmapGlobals.drawflag) {
            qgl.qglColor3f(1.0f, 1.0f, 1.0f)
            qgl.qglPointSize(4f)
            qgl.qglBegin(GL11.GL_POINTS)
            qgl.qglVertex3fv(optTri.midpoint.ToFloatPtr())
            qgl.qglEnd()
            qgl.qglFlush()
        }

        // find the midpoint, and scan through all the original triangles to
        // see if it is inside any of them
        tri = island.group!!.triList
        while (tri != null) {
            if (PointInTri(optTri.midpoint, tri, island)) {
                break
            }
            tri = tri.next
        }
        optTri.filled = tri != null
        if (dmap.dmapGlobals.drawflag) {
            if (optTri.filled) {
                qgl.qglColor3f((128 + orandom.RandomInt(127)) / 255.0f, 0.0f, 0.0f)
            } else {
                qgl.qglColor3f(0.0f, (128 + orandom.RandomInt(127)) / 255.0f, 0.0f)
            }
            qgl.qglBegin(GL11.GL_TRIANGLES)
            qgl.qglVertex3fv(optTri.v[0].pv.ToFloatPtr())
            qgl.qglVertex3fv(optTri.v[1].pv.ToFloatPtr())
            qgl.qglVertex3fv(optTri.v[2].pv.ToFloatPtr())
            qgl.qglEnd()
            qgl.qglColor3f(1.0f, 1.0f, 1.0f)
            qgl.qglBegin(GL11.GL_LINE_LOOP)
            qgl.qglVertex3fv(optTri.v[0].pv.ToFloatPtr())
            qgl.qglVertex3fv(optTri.v[1].pv.ToFloatPtr())
            qgl.qglVertex3fv(optTri.v[2].pv.ToFloatPtr())
            qgl.qglEnd()
            qgl.qglFlush()
        }

        // link the triangle to it's edges
        LinkTriToEdge(optTri, e1)
        LinkTriToEdge(optTri, e2)
        LinkTriToEdge(optTri, opposite!!)
    }

    //==================================================================
    // debugging tool
    fun ReportNearbyVertexes(v: optVertex_s, island: optIsland_t) {
        var ov: optVertex_s?
        var d: Float
        val vec = idVec3()
        Common.common.Printf("verts near 0x%p (%f, %f)\n", v, v.pv[0], v.pv[1])
        ov = island.verts
        while (ov != null) {
            if (ov === v) {
                ov = ov.islandLink
                continue
            }
            vec.set(ov.pv.minus(v.pv))
            d = vec.Length()
            if (d < 1) {
                Common.common.Printf("0x%p = (%f, %f)\n", ov, ov.pv[0], ov.pv[1])
            }
            ov = ov.islandLink
        }
    }

    /*
     ====================
     BuildOptTriangles

     Generate a new list of triangles from the optEdeges
     ====================
     */
    fun BuildOptTriangles(island: optIsland_t) {
        var ov: optVertex_s?
        var second = optVertex_s()
        var third = optVertex_s()
        var middle = optVertex_s()
        var e1: optEdge_s?
        var e1Next: optEdge_s? = optEdge_s()
        var e2: optEdge_s?
        var e2Next: optEdge_s? = optEdge_s()
        var check: optEdge_s?
        var checkNext: optEdge_s? = optEdge_s()

        // free them
        FreeOptTriangles(island)

        // clear the vertex emitted flags
        ov = island.verts
        while (ov != null) {
            ov.emited = false
            ov = ov.islandLink
        }

        // clear the edge triangle links
        check = island.edges
        while (check != null) {
            check.backTri = null
            check.frontTri = check.backTri
            check = check.islandLink
        }

        // check all possible triangle made up out of the
        // edges coming off the vertex
        ov = island.verts
        while (ov != null) {
            if (ov.edges == null) {
                ov = ov.islandLink
                continue
            }

//#if 0
//if ( dmapGlobals.drawflag && ov == (optVertex_s *)0x1845a60 ) {
//for ( e1 = ov.edges ; e1 ; e1 = e1Next ) {
//	qglBegin( GL_LINES );
//	qglColor3f( 0,1,0 );
//	qglVertex3fv( e1.v1.pv.ToFloatPtr() );
//	qglVertex3fv( e1.v2.pv.ToFloatPtr() );
//	qglEnd();
//	qglFlush();
//	if ( e1.v1 == ov ) {
//		e1Next = e1.v1link;
//	} else if ( e1.v2 == ov ) {
//		e1Next = e1.v2link;
//	}
//}
//}
//#endif
            e1 = ov.edges
            while (e1 != null) {
                if (e1.v1 == ov) {
                    second = e1.v2!!
                    e1Next = e1.v1link
                } else if (e1.v2 == ov) {
                    second = e1.v1!!
                    e1Next = e1.v2link
                } else {
                    Common.common.Error("BuildOptTriangles: mislinked edge")
                }

                // if the vertex has already been used, it can't be used again
                if (second.emited) {
                    e1 = e1Next
                    continue
                }
                e2 = ov.edges
                while (e2 != null) {
                    if (e2.v1 == ov) {
                        third = e2.v2!!
                        e2Next = e2.v1link
                    } else if (e2.v2 == ov) {
                        third = e2.v1!!
                        e2Next = e2.v2link
                    } else {
                        Common.common.Error("BuildOptTriangles: mislinked edge")
                    }
                    if (e2 == e1) {
                        e2 = e2Next
                        continue
                    }

                    // if the vertex has already been used, it can't be used again
                    if (third.emited) {
                        e2 = e2Next
                        continue
                    }

                    // if the triangle is backwards or degenerate, don't use it
                    if (!IsTriangleValid(ov, second, third)) {
                        e2 = e2Next
                        continue
                    }

                    // see if any other edge bisects these two, which means
                    // this triangle shouldn't be used
                    check = ov.edges
                    while (check != null) {
                        if (check.v1 == ov) {
                            middle = check.v2!!
                            checkNext = check.v1link
                        } else if (check.v2 == ov) {
                            middle = check.v1!!
                            checkNext = check.v2link
                        } else {
                            Common.common.Error("BuildOptTriangles: mislinked edge")
                        }
                        if (check == e1 || check == e2) {
                            check = checkNext
                            continue
                        }
                        if (IsTriangleValid(ov, second, middle) && IsTriangleValid(ov, middle, third)) {
                            break // should use the subdivided ones
                        }
                        check = checkNext
                    }
                    if (check != null) {
                        e2 = e2Next
                        continue  // don't use it
                    }

                    // the triangle is valid
                    CreateOptTri(ov, e1, e2, island)
                    e2 = e2Next
                }
                e1 = e1Next
            }

            // later vertexes will not emit triangles that use an
            // edge that this vert has already used
            ov.emited = true
            ov = ov.islandLink
        }
    }

    /*
     ====================
     RegenerateTriangles

     Add new triangles to the group's regeneratedTris
     ====================
     */
    fun RegenerateTriangles(island: optIsland_t) {
        var optTri: optTri_s?
        var tri: mapTri_s?
        var c_out: Int
        c_out = 0
        optTri = island.tris
        while (optTri != null) {
            if (!optTri.filled) {
                optTri = optTri.next
                continue
            }

            // create a new mapTri_s
            tri = tritools.AllocTri()
            tri.material = island.group!!.material
            tri.mergeGroup = island.group!!.mergeGroup
            tri.v[0] = optTri.v[0].v
            tri.v[1] = optTri.v[1].v
            tri.v[2] = optTri.v[2].v
            val plane = idPlane()
            tritools.PlaneForTri(tri, plane)
            if (plane.Normal().times(dmap.dmapGlobals.mapPlanes[island.group!!.planeNum].Normal()) <= 0) {
                // this can happen reasonably when a triangle is nearly degenerate in
                // optimization planar space, and winds up being degenerate in 3D space
                Common.common.Printf("WARNING: backwards triangle generated!\n")
                // discard it
                tritools.FreeTri(tri)
                optTri = optTri.next
                continue
            }
            c_out++
            tri.next = island.group!!.regeneratedTris
            island.group!!.regeneratedTris = tri
            optTri = optTri.next
        }
        FreeOptTriangles(island)
        if (dmap.dmapGlobals.verbose) {
            Common.common.Printf("%6d tris out\n", c_out)
        }
    }

    /*
     ====================
     RemoveInteriorEdges

     Edges that have triangles of the same type (filled / empty)
     on both sides will be removed
     ====================
     */
    fun RemoveInteriorEdges(island: optIsland_t) {
        var c_interiorEdges: Int
        var c_exteriorEdges: Int
        var e: optEdge_s?
        var next: optEdge_s?
        var front: Boolean
        var back: Boolean
        c_exteriorEdges = 0
        c_interiorEdges = 0
        e = island.edges
        while (e != null) {

            // we might remove the edge, so get the next link now
            next = e.islandLink
            front = if (null == e.frontTri) {
                false
            } else {
                e.frontTri!!.filled
            }
            back = if (e.backTri == null) {
                false
            } else {
                e.backTri!!.filled
            }
            if (front == back) {
                // free the edge
                UnlinkEdge(e, island)
                c_interiorEdges++
                e = next
                continue
            }
            c_exteriorEdges++
            e = next
        }
        if (dmap.dmapGlobals.verbose) {
            Common.common.Printf("%6d original interior edges\n", c_interiorEdges)
            Common.common.Printf("%6d original exterior edges\n", c_exteriorEdges)
        }
    }

    /*
     =================
     AddEdgeIfNotAlready
     =================
     */
    fun AddEdgeIfNotAlready(v1: optVertex_s, v2: optVertex_s) {
        var e: optEdge_s?

        // make sure that there isn't an identical edge already added
        e = v1.edges
        while (e != null) {
            if (e.v1 == v1 && e.v2 == v2 || e.v1 == v2 && e.v2 == v1) {
                return  // already added
            }
            if (e.v1 == v1) {
                e = e.v1link
            } else if (e.v2 == v1) {
                e = e.v2link
            } else {
                Common.common.Error("SplitEdgeByList: bad edge link")
            }
        }

        // this edge is a keeper
        e = AllocEdge()
        e.v1 = v1
        e.v2 = v2
        e.islandLink = null

        // link the edge to its verts
        LinkEdge(e)
    }

    /*
     =================
     DrawOriginalEdges
     =================
     */
    fun DrawOriginalEdges(numOriginalEdges: Int, originalEdges: Array<originalEdges_t>) {
        var i: Int
        if (!dmap.dmapGlobals.drawflag) {
            return
        }
        gldraw.Draw_ClearWindow()
        qgl.qglBegin(GL11.GL_LINES)
        i = 0
        while (i < numOriginalEdges) {
            qgl.qglColor3f(1.0f, 0.0f, 0.0f)
            qgl.qglVertex3fv(originalEdges[i].v1.pv.ToFloatPtr())
            qgl.qglColor3f(0.0f, 0.0f, 0.0f)
            qgl.qglVertex3fv(originalEdges[i].v2.pv.ToFloatPtr())
            i++
        }
        qgl.qglEnd()
        qgl.qglFlush()
    }

    /*
     =================
     AddOriginalTriangle
     =================
     */
    fun AddOriginalTriangle(v: Array<optVertex_s> /*[3]*/) {
        var v1: optVertex_s?
        var v2: optVertex_s?

        // if this triangle is backwards (possible with epsilon issues)
        // ignore it completely
        if (!IsTriangleValid(v[0], v[1], v[2])) {
            Common.common.Printf("WARNING: backwards triangle in input!\n")
            return
        }
        for (i in 0..2) {
            v1 = v[i]
            v2 = v[(i + 1) % 3]
            if (v1 === v2) {
                // this probably shouldn't happen, because the
                // tri would be degenerate
                continue
            }
            var j: Int
            // see if there is an existing one
            j = 0
            while (j < numOriginalEdges) {
                if (originalEdges[j].v1 === v1 && originalEdges[j].v2 === v2) {
                    break
                }
                if (originalEdges[j].v2 === v1 && originalEdges[j].v1 === v2) {
                    break
                }
                j++
            }
            if (j == numOriginalEdges) {
                // add it
                originalEdges[j].v1 = v1
                originalEdges[j].v2 = v2
                numOriginalEdges++
            }
        }
    }

    /*
     =================
     AddOriginalEdges
     =================
     */
    fun AddOriginalEdges(opt: optimizeGroup_s) {
        var tri: mapTri_s?
        val v = Array(3) { optVertex_s() }
        val numTris: Int
        if (dmap.dmapGlobals.verbose) {
            Common.common.Printf("----\n")
            Common.common.Printf("%6d original tris\n", tritools.CountTriList(opt.triList!!))
        }
        optBounds.Clear()

        // allocate space for max possible edges
        numTris = tritools.CountTriList(opt.triList!!)
        originalEdges = Array(numTris * 3) { originalEdges_t() } // Mem_Alloc(numTris * 3);
        numOriginalEdges = 0

        // add all unique triangle edges
        numOptVerts = 0
        numOptEdges = 0
        tri = opt.triList
        while (tri != null) {
            tri.optVert[0] = FindOptVertex(tri.v[0], opt)!!
            v[0] = tri.optVert[0]
            tri.optVert[1] = FindOptVertex(tri.v[1], opt)!!
            v[1] = tri.optVert[1]
            tri.optVert[2] = FindOptVertex(tri.v[2], opt)!!
            v[2] = tri.optVert[2]
            AddOriginalTriangle(v)
            tri = tri.next
        }
    }

    /*
     =====================
     SplitOriginalEdgesAtCrossings
     =====================
     */
    fun SplitOriginalEdgesAtCrossings(opt: optimizeGroup_s) {
        var i: Int
        var j: Int
        var k: Int
        var l: Int
        val numOriginalVerts: Int
        var crossings: Array<edgeCrossing_s?>?
        numOriginalVerts = numOptVerts
        // now split any crossing edges and create optEdges
        // linked to the vertexes

        // debug drawing bounds
        dmap.dmapGlobals.drawBounds.set(optBounds)
        dmap.dmapGlobals.drawBounds[0].minusAssign(0, -2.0f)
        dmap.dmapGlobals.drawBounds[0].minusAssign(1, -2.0f)
        dmap.dmapGlobals.drawBounds[1].plusAssign(0, -2.0f)
        dmap.dmapGlobals.drawBounds[1].plusAssign(1, -2.0f)

        // generate crossing points between all the original edges
        crossings = arrayOfNulls<edgeCrossing_s?>(numOriginalEdges) // Mem_ClearedAlloc(numOriginalEdges);
        i = 0
        while (i < numOriginalEdges) {
            if (dmap.dmapGlobals.drawflag) {
                DrawOriginalEdges(numOriginalEdges, originalEdges)
                qgl.qglBegin(GL11.GL_LINES)
                qgl.qglColor3f(0.0f, 1.0f, 0.0f)
                qgl.qglVertex3fv(originalEdges[i].v1.pv.ToFloatPtr())
                qgl.qglColor3f(0.0f, 0.0f, 1.0f)
                qgl.qglVertex3fv(originalEdges[i].v2.pv.ToFloatPtr())
                qgl.qglEnd()
                qgl.qglFlush()
            }
            j = i + 1
            while (j < numOriginalEdges) {
                var v1: optVertex_s?
                var v2: optVertex_s?
                var v3: optVertex_s?
                var v4: optVertex_s?
                var newVert: optVertex_s?
                var cross: edgeCrossing_s
                v1 = originalEdges[i].v1
                v2 = originalEdges[i].v2
                v3 = originalEdges[j].v1
                v4 = originalEdges[j].v2
                if (!EdgesCross(v1, v2, v3, v4)) {
                    j++
                    continue
                }

                // this is the only point in optimization where
                // completely new points are created, and it only
                // happens if there is overlapping coplanar
                // geometry in the source triangles
                newVert = EdgeIntersection(v1, v2, v3, v4, opt)
                if (null == newVert) {
//common.Printf( "lines %i (%i to %i) and %i (%i to %i) are colinear\n", i, v1 - optVerts, v2 - optVerts,
//		   j, v3 - optVerts, v4 - optVerts );	// !@#
                    // colinear, so add both verts of each edge to opposite
                    if (VertexBetween(v3, v1, v2)) {
                        cross = edgeCrossing_s() // Mem_ClearedAlloc(sizeof(cross));
                        cross.ov = v3
                        cross.next = crossings[i]
                        crossings[i] = cross
                    }
                    if (VertexBetween(v4, v1, v2)) {
                        cross = edgeCrossing_s() // Mem_ClearedAlloc(sizeof(cross));
                        cross.ov = v4
                        cross.next = crossings[i]
                        crossings[i] = cross
                    }
                    if (VertexBetween(v1, v3, v4)) {
                        cross = edgeCrossing_s() // Mem_ClearedAlloc(sizeof(cross));
                        cross.ov = v1
                        cross.next = crossings[j]
                        crossings[j] = cross
                    }
                    if (VertexBetween(v2, v3, v4)) {
                        cross = edgeCrossing_s() //) Mem_ClearedAlloc(sizeof(cross));
                        cross.ov = v2
                        cross.next = crossings[j]
                        crossings[j] = cross
                    }
                    j++
                    continue
                }
                //#if 0
//if ( newVert && newVert != v1 && newVert != v2 && newVert != v3 && newVert != v4 ) {
//common.Printf( "lines %i (%i to %i) and %i (%i to %i) cross at new point %i\n", i, v1 - optVerts, v2 - optVerts,
//		   j, v3 - optVerts, v4 - optVerts, newVert - optVerts );
//} else if ( newVert ) {
//common.Printf( "lines %i (%i to %i) and %i (%i to %i) intersect at old point %i\n", i, v1 - optVerts, v2 - optVerts,
//		  j, v3 - optVerts, v4 - optVerts, newVert - optVerts );
//}
//#endif
                if (newVert != v1 && newVert != v2) {
                    cross = edgeCrossing_s() // Mem_ClearedAlloc(sizeof(cross));
                    cross.ov = newVert
                    cross.next = crossings[i]
                    crossings[i] = cross
                }
                if (newVert != v3 && newVert != v4) {
                    cross = edgeCrossing_s() // Mem_ClearedAlloc(sizeof(cross));
                    cross.ov = newVert
                    cross.next = crossings[j]
                    crossings[j] = cross
                }
                j++
            }
            i++
        }

        // now split each edge by its crossing points
        // colinear edges will have duplicated edges added, but it won't hurt anything
        i = 0
        while (i < numOriginalEdges) {
            var cross: edgeCrossing_s?
            var nextCross: edgeCrossing_s?
            var numCross: Int
            var sorted: Array<optVertex_s>
            numCross = 0
            cross = crossings[i]
            while (cross != null) {
                numCross++
                cross = cross.next
            }
            numCross += 2 // account for originals
            sorted = Array(numCross) { optVertex_s() } // Mem_Alloc(numCross);
            sorted[0] = originalEdges[i].v1
            sorted[1] = originalEdges[i].v2
            j = 2
            cross = crossings[i]
            while (cross != null) {
                nextCross = cross.next
                sorted[j] = cross.ov
                cross = null //Mem_Free(cross);
                j++
                cross = nextCross
            }

            // add all possible fragment combinations that aren't divided
            // by another point
            j = 0
            while (j < numCross) {
                k = j + 1
                while (k < numCross) {
                    l = 0
                    while (l < numCross) {
                        if (sorted[l] === sorted[j] || sorted[l] === sorted[k]) {
                            l++
                            continue
                        }
                        if (sorted[j] === sorted[k]) {
                            l++
                            continue
                        }
                        if (VertexBetween(sorted[l], sorted[j], sorted[k])) {
                            break
                        }
                        l++
                    }
                    if (l == numCross) {
//common.Printf( "line %i fragment from point %i to %i\n", i, sorted[j] - optVerts, sorted[k] - optVerts );
                        AddEdgeIfNotAlready(sorted[j], sorted[k])
                    }
                    k++
                }
                j++
            }
            i++
        }
        crossings = null //Mem_Free(crossings);
        originalEdges = emptyArray() //Mem_Free(originalEdges);

        // check for duplicated edges
        i = 0
        while (i < numOptEdges) {
            j = i + 1
            while (j < numOptEdges) {
                if (optEdges[i].v1 === optEdges[j].v1 && optEdges[i].v2 === optEdges[j].v2 || optEdges[i].v1 === optEdges[j].v2 && optEdges[i].v2 === optEdges[j].v1) {
                    Common.common.Printf("duplicated optEdge\n")
                }
                j++
            }
            i++
        }
        if (dmap.dmapGlobals.verbose) {
            Common.common.Printf("%6d original edges\n", numOriginalEdges)
            Common.common.Printf("%6d edges after splits\n", numOptEdges)
            Common.common.Printf("%6d original vertexes\n", numOriginalVerts)
            Common.common.Printf("%6d vertexes after splits\n", numOptVerts)
        }
    }

    //===========================================================================
    //=================================================================
    /*
     ===================
     CullUnusedVerts

     Unlink any verts with no edges, so they
     won't be used in the retriangulation
     ===================
     */
    fun CullUnusedVerts(island: optIsland_t) {
        var prev: optVertex_s?
        var vert: optVertex_s?
        var c_keep: Int
        var c_free: Int
        var edge: optEdge_s
        c_keep = 0
        c_free = 0
        prev = island.verts
        while (prev != null) {
            vert = prev
            if (vert.edges == null) {
                // free it
                prev = vert.islandLink
                c_free++
            } else {
                edge = vert.edges!!
                if (edge.v1 == vert && edge.v1link == null || edge.v2 == vert && edge.v2link == null) {
                    // is is occasionally possible to get a vert
                    // with only a single edge when colinear optimizations
                    // crunch down a complex sliver
                    UnlinkEdge(edge, island)
                    // free it
                    prev = vert.islandLink
                    c_free++
                } else {
                    prev = vert.islandLink
                    c_keep++
                }
            }
        }
        if (dmap.dmapGlobals.verbose) {
            Common.common.Printf("%6d verts kept\n", c_keep)
            Common.common.Printf("%6d verts freed\n", c_free)
        }
    }

    //==================================================================================
    /*
     ====================
     OptimizeIsland

     At this point, all needed vertexes are already in the
     list, including any that were added at crossing points.

     Interior and colinear vertexes will be removed, and
     a new triangulation will be created.
     ====================
     */
    fun OptimizeIsland(island: optIsland_t) {
        // add space-filling fake edges so we have a complete
        // triangulation of a convex hull before optimization
        AddInteriorEdges(island)
        DrawEdges(island)

        // determine all the possible triangles, and decide if
        // the are filled or empty
        BuildOptTriangles(island)

        // remove interior vertexes that have filled triangles
        // between all their edges
        RemoveInteriorEdges(island)
        DrawEdges(island)
        ValidateEdgeCounts(island)

        // remove vertexes that only have two colinear edges
        CombineColinearEdges(island)
        CullUnusedVerts(island)
        DrawEdges(island)

        // add new internal edges between the remaining exterior edges
        // to give us a full triangulation again
        AddInteriorEdges(island)
        DrawEdges(island)

        // determine all the possible triangles, and decide if
        // the are filled or empty
        BuildOptTriangles(island)

        // make mapTri_s out of the filled optTri_s
        RegenerateTriangles(island)
    }

    /*
     ================
     AddVertexToIsland_r
     ================
     */
    fun AddVertexToIsland_r(vert: optVertex_s, island: optIsland_t) {
        var e: optEdge_s?

        // we can't just check islandLink, because the
        // last vert will have a NULL
        if (vert.addedToIsland) {
            return
        }
        vert.addedToIsland = true
        vert.islandLink = island.verts
        island.verts = vert
        e = vert.edges
        while (e != null) {
            if (!e.addedToIsland) {
                e.addedToIsland = true
                e.islandLink = island.edges
                island.edges = e
            }
            if (e.v1 == vert) {
                AddVertexToIsland_r(e.v2!!, island)
                e = e.v1link
                continue
            }
            if (e.v2 == vert) {
                AddVertexToIsland_r(e.v1!!, island)
                e = e.v2link
                continue
            }
            Common.common.Error("AddVertexToIsland_r: mislinked vert")
        }
    }

    /*
     ====================
     SeparateIslands

     While the algorithm should theoretically handle any collection
     of triangles, there are speed and stability benefits to making
     it work on as small a list as possible, so separate disconnected
     collections of edges and process separately.

     FIXME: we need to separate the source triangles before
     doing this, because PointInSourceTris() can give a bad answer if
     the source list has triangles not used in the optimization
     ====================
     */
    fun SeparateIslands(opt: optimizeGroup_s) {
        var i: Int
        val island = optIsland_t()
        var numIslands: Int
        DrawAllEdges()
        numIslands = 0
        i = 0
        while (i < numOptVerts) {
            if (optVerts[i].addedToIsland) {
                i++
                continue
            }
            numIslands++
            //		memset( &island, 0, sizeof( island ) );
            island.group = opt
            AddVertexToIsland_r(optVerts[i], island)
            OptimizeIsland(island)
            i++
        }
        if (dmap.dmapGlobals.verbose) {
            Common.common.Printf("%6d islands\n", numIslands)
        }
    }

    fun DontSeparateIslands(opt: optimizeGroup_s) {
        var i: Int
        val island: optIsland_t
        DrawAllEdges()

//	memset( &island, 0, sizeof( island ) );
        island = optIsland_t()
        island.group = opt

        // link everything together
        i = 0
        while (i < numOptVerts) {
            optVerts[i].islandLink = island.verts
            island.verts = optVerts[i]
            i++
        }
        i = 0
        while (i < numOptEdges) {
            optEdges[i].islandLink = island.edges
            island.edges = optEdges[i]
            i++
        }
        OptimizeIsland(island)
    }

    /*
     ====================
     PointInSourceTris

     This is a sloppy bounding box check
     ====================
     */
    fun PointInSourceTris(x: Float, y: Float, z: Float, opt: optimizeGroup_s): Boolean {
        var tri: mapTri_s?
        val b = idBounds()
        val p = idVec3()
        if (!opt.material!!.IsDrawn()) {
            return false
        }
        p.set(idVec3(x, y, z))
        tri = opt.triList
        while (tri != null) {
            b.Clear()
            b.AddPoint(tri.v[0].xyz)
            b.AddPoint(tri.v[1].xyz)
            b.AddPoint(tri.v[2].xyz)
            if (b.ContainsPoint(p)) {
                return true
            }
            tri = tri.next
        }
        return false
    }

    /*
     ====================
     OptimizeOptList
     ====================
     */
    fun OptimizeOptList(opt: optimizeGroup_s) {
        val oldNext: optimizeGroup_s?

        // fix the t junctions among this single list
        // so we can match edges
        // can we avoid doing this if colinear vertexes break edges?
        oldNext = opt.nextGroup
        opt.nextGroup = null
        tritjunction.FixAreaGroupsTjunctions(opt)
        opt.nextGroup = oldNext

        // create the 2D vectors
        dmap.dmapGlobals.mapPlanes[opt.planeNum].Normal().NormalVectors(opt.axis[0], opt.axis[1])
        AddOriginalEdges(opt)
        SplitOriginalEdgesAtCrossings(opt)

//#if 0
//	// seperate any discontinuous areas for individual optimization
//	// to reduce the scope of the problem
//	SeparateIslands( opt );
//#else
        DontSeparateIslands(opt)
        //#endif

        // now free the hash verts
        tritjunction.FreeTJunctionHash()

        // free the original list and use the new one
        tritools.FreeTriList(opt.triList)
        opt.triList = opt.regeneratedTris
        opt.regeneratedTris = null
    }

    /*
     ==================
     SetGroupTriPlaneNums

     Copies the group planeNum to every triangle in each group
     ==================
     */
    fun SetGroupTriPlaneNums(groups: optimizeGroup_s?) {
        var tri: mapTri_s?
        var group: optimizeGroup_s?
        group = groups
        while (group != null) {
            tri = group.triList
            while (tri != null) {
                tri.planeNum = group.planeNum
                tri = tri.next
            }
            group = group.nextGroup
        }
    }

    /*
     ===================
     OptimizeGroupList

     This will also fix tjunctions

     ===================
     */
    fun OptimizeGroupList(groupList: optimizeGroup_s?) {
        val c_in: Int
        val c_edge: Int
        val c_tjunc2: Int
        var group: optimizeGroup_s?
        if (null == groupList) {
            return
        }
        c_in = tritjunction.CountGroupListTris(groupList)

        // optimize and remove colinear edges, which will
        // re-introduce some t junctions
        group = groupList
        while (group != null) {
            OptimizeOptList(group)
            group = group.nextGroup
        }
        c_edge = tritjunction.CountGroupListTris(groupList)

        // fix t junctions again
        tritjunction.FixAreaGroupsTjunctions(groupList)
        tritjunction.FreeTJunctionHash()
        c_tjunc2 = tritjunction.CountGroupListTris(groupList)
        SetGroupTriPlaneNums(groupList)
        Common.common.Printf("----- OptimizeAreaGroups Results -----\n")
        Common.common.Printf("%6d tris in\n", c_in)
        Common.common.Printf("%6d tris after edge removal optimization\n", c_edge)
        Common.common.Printf("%6d tris after final t junction fixing\n", c_tjunc2)
    }

    /*
     ==================
     OptimizeEntity
     ==================
     */
    fun OptimizeEntity(e: uEntity_t) {
        var i: Int
        Common.common.Printf("----- OptimizeEntity -----\n")
        i = 0
        while (i < e.numAreas) {
            OptimizeGroupList(e.areas[i].groups)
            i++
        }
    }

    class optVertex_s {
        var addedToIsland = false
        var edges: optEdge_s? = null
        var emited // when regenerating triangles
                = false
        var islandLink: optVertex_s? = null
        val pv: idVec3 = idVec3() // projected against planar axis, third value is 0
        var v: idDrawVert = idDrawVert()
    }

    class optEdge_s {
        var addedToIsland = false
        var combined // combined from two or more colinear edges
                = false
        var created // not one of the original edges
                = false
        var frontTri: optTri_s? = null
        var backTri: optTri_s? = null
        var islandLink: optEdge_s? = null
        var v1: optVertex_s? = null
        var v2: optVertex_s? = null
        var v1link: optEdge_s? = null
        var v2link: optEdge_s? = null
    }

    class optTri_s {
        var filled = false
        val midpoint: idVec3 = idVec3()
        var next: optTri_s? = null
        var v: Array<optVertex_s> = Array(3) { optVertex_s() }
    }

    class optIsland_t {
        var edges: optEdge_s? = null
        var group: optimizeGroup_s? = null
        var tris: optTri_s? = null
        var verts: optVertex_s? = null
    }

    internal class edgeLength_t {
        var length = 0.0f
        var v1: optVertex_s? = null
        var v2: optVertex_s? = null
    }

    class originalEdges_t {
        var v1: optVertex_s = optVertex_s()
        var v2: optVertex_s = optVertex_s()
    }

    internal class edgeCrossing_s {
        var next: edgeCrossing_s? = null
        var ov: optVertex_s = optVertex_s()
    }

    internal class LengthSort : cmp_t<edgeLength_t> {
        override fun compare(a: edgeLength_t, b: edgeLength_t): Int {
            if (a.length < b.length) {
                return -1
            }
            return if (a.length > b.length) {
                1
            } else 0
        }
    }
}
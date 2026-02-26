package neo.Tools.Compilers.DMap

import neo.Renderer.*
import neo.Renderer.Interaction.srfCullInfo_t
import neo.Renderer.Model.srfTriangles_s
import neo.Renderer.tr_stencilshadow.shadowGen_t
import neo.TempDump
import neo.Tools.Compilers.DMap.dmap.mapLight_t
import neo.Tools.Compilers.DMap.dmap.mapTri_s
import neo.Tools.Compilers.DMap.dmap.optimizeGroup_s
import neo.Tools.Compilers.DMap.dmap.shadowOptLevel_t
import neo.Tools.Compilers.DMap.map.FindFloatPlane
import neo.framework.Common
import neo.idlib.containers.List.cmp_t
import neo.idlib.geometry.Winding.idWinding
import neo.idlib.math.ON_EPSILON
import neo.idlib.math.idPlane
import neo.idlib.math.idVec3
import neo.idlib.math.idVec4
import java.util.*
import kotlin.math.abs

object shadowopt3 {
    //
    const val EDGE_EPSILON = 0.1f

    /*

     given a set of faces that are clipped to the required frustum

     make 2D projection for each vertex

     for each edge
     add edge, generating new points at each edge intersection

     ?add all additional edges to make a full triangulation

     make full triangulation

     for each triangle
     find midpoint
     find original triangle with midpoint closest to view
     annotate triangle with that data
     project all vertexes to that plane
     output the triangle as a front cap

     snap all vertexes
     make a back plane projection for all vertexes

     for each edge
     if one side doesn't have a triangle
     make a sil edge to back plane projection
     continue
     if triangles on both sides have two verts in common
     continue
     make a sil edge from one triangle to the other




     classify triangles on common planes, so they can be optimized

     what about interpenetrating triangles???

     a perfect shadow volume will have every edge exactly matched with
     an opposite, and no two triangles covering the same area on either
     the back projection or a silhouette edge.

     Optimizing the triangles on the projected plane can give a significant
     improvement, but the quadratic time nature of the optimization process
     probably makes it untenable.

     There exists some small room for further triangle count optimizations of the volumes
     by collapsing internal surface geometry in some cases, or allowing original triangles
     to extend outside the exactly light frustum without being clipped, but it probably
     isn't worth it.

     Triangle count optimizations at the expense of a slight fill rate cost
     may be apropriate in some cases.


     Perform the complete clipping on all triangles
     for each vertex
     project onto the apropriate plane and mark plane bit as in use
     for each triangle
     if points project onto different planes, clip
     */
    const val MAX_SHADOW_TRIS = 32768

    //
    const val MAX_SIL_EDGES = MAX_SHADOW_TRIS * 3
    var silEdges: Array<shadowOptEdge_s?> = arrayOfNulls(MAX_SIL_EDGES)

    //
    const val MAX_SIL_QUADS = MAX_SHADOW_TRIS * 3
    var silQuads: Array<silQuad_s?> = arrayOfNulls(MAX_SIL_QUADS)

    //
    var EDGE_PLANE_EPSILON = 0.1f
    var UNIQUE_EPSILON = 0.1f

    /*
     ===================
     ClipTriangle_r
     ===================
     */
    var c_removedFragments = 0
    var maxRetIndexes = 0
    var maxUniqued = 0
    var numOutputTris = 0
    var numSilEdges = 0

    //
    var numSilPlanes = 0
    var numSilQuads = 0

    //
    // the uniqued verts are still in projection centered space, not global space
    var numUniqued = 0
    var numUniquedBeforeProjection = 0

    //
    var outputTris: Array<shadowTri_t?> = arrayOfNulls(MAX_SHADOW_TRIS)

    //
    var ret: optimizedShadow_t? = null
    var silPlanes: Array<silPlane_t>? = null
    var uniqued: Array<idVec3>? = null

    /*
     =================
     CreateEdgesForTri
     =================
     */
    fun CreateEdgesForTri(tri: shadowTri_t) {
        for (j in 0..2) {
            val v1 = tri.v[j]
            val v2 = tri.v[(j + 1) % 3]
            tri.edge[j].Cross(v2, v1)
            tri.edge[j].Normalize()
        }
    }

    fun TriOutsideTri(a: shadowTri_t, b: shadowTri_t): Boolean {
//#if 0
//	if ( a.v[0] * b.edge[0] <= EDGE_EPSILON
//		&& a.v[1] * b.edge[0] <= EDGE_EPSILON
//		&& a.v[2] * b.edge[0] <= EDGE_EPSILON ) {
//			return true;
//		}
//	if ( a.v[0] * b.edge[1] <= EDGE_EPSILON
//		&& a.v[1] * b.edge[1] <= EDGE_EPSILON
//		&& a.v[2] * b.edge[1] <= EDGE_EPSILON ) {
//			return true;
//		}
//	if ( a.v[0] * b.edge[2] <= EDGE_EPSILON
//		&& a.v[1] * b.edge[2] <= EDGE_EPSILON
//		&& a.v[2] * b.edge[2] <= EDGE_EPSILON ) {
//			return true;
//		}
//#else
        for (i in 0..2) {
            var j: Int
            j = 0
            while (j < 3) {
                val d = a.v[j].times(b.edge[i])
                if (d > EDGE_EPSILON) {
                    break
                }
                j++
            }
            if (j == 3) {
                return true
            }
        }
        //#endif
        return false
    }

    fun TriBehindTri(a: shadowTri_t, b: shadowTri_t): Boolean {
        var d: Float
        d = b.plane.Distance(a.v[0])
        if (d > 0) {
            return true
        }
        d = b.plane.Distance(a.v[1])
        if (d > 0) {
            return true
        }
        d = b.plane.Distance(a.v[2])
        return d > 0
    }

    //static int FindUniqueVert( idVec3 v );
    //=====================================================================================
    fun ClipTriangle_r(tri: shadowTri_t, startTri: Int, skipTri: Int, numTris: Int, tris: Array<shadowTri_t>) {
        // create edge planes for this triangle

        // compare against all the other triangles
        for (i in startTri until numTris) {
            if (i == skipTri) {
                continue
            }
            val other = tris[i]
            if (TriOutsideTri(tri, other)) {
                continue
            }
            if (TriOutsideTri(other, tri)) {
                continue
            }
            // they overlap to some degree

            // if other is behind tri, it doesn't clip it
            if (!TriBehindTri(tri, other)) {
                continue
            }

            // clip it
            var w = idWinding(tri.v, 3)
            var j = 0
            while (j < 4 && !w.isNULL()) {
                val front = idWinding()
                val back = idWinding()

                // keep any portion in front of other's plane
                if (j == 0) {
                    w.Split(other.plane, ON_EPSILON, front, back)
                } else {
                    w.Split(idPlane(other.edge[j - 1], 0.0f), ON_EPSILON, front, back)
                }
                if (!back.isNULL()) {
                    // recursively clip these triangles to all subsequent triangles
                    for (k in 2 until back.GetNumPoints()) {
                        tri.v[0].set(back[0].ToVec3())
                        tri.v[1].set(back[k - 1].ToVec3())
                        tri.v[2].set(back[k].ToVec3())
                        CreateEdgesForTri(tri)
                        ClipTriangle_r(tri, i + 1, skipTri, numTris, tris)
                    }
                    //				delete back;
                }

//			delete w;
                w = front
                j++
            }
            //		if ( w ) {
//			delete w;
//		}
            c_removedFragments++
            // any fragments will have been added recursively
            return
        }

        // this fragment is frontmost, so add it to the output list
        if (numOutputTris == MAX_SHADOW_TRIS) {
            Common.common.Error("numOutputTris == MAX_SHADOW_TRIS")
        }
        outputTris[numOutputTris] = tri
        numOutputTris++
    }

    /*
     ====================
     ClipOccluders

     Generates outputTris by clipping all the triangles against each other,
     retaining only those closest to the projectionOrigin
     ====================
     */
    fun ClipOccluders(verts: Array<idVec4>, indexes: IntArray, numIndexes: Int, projectionOrigin: idVec3) {
        val numTris = numIndexes / 3
        var i: Int
        val tris = Array(numTris) { shadowTri_t() }
        var tri: shadowTri_t
        Common.common.Printf("ClipOccluders: %d triangles\n", numTris)
        i = 0
        while (i < numTris) {
            tri = tris[i]

            // the indexes are in reversed order from tr_stencilshadow
            tri.v[0].set(verts[indexes[i * 3 + 2]].ToVec3().minus(projectionOrigin))
            tri.v[1].set(verts[indexes[i * 3 + 1]].ToVec3().minus(projectionOrigin))
            tri.v[2].set(verts[indexes[i * 3 + 0]].ToVec3().minus(projectionOrigin))
            val d1 = idVec3(tri.v[1].minus(tri.v[0]))
            val d2 = idVec3(tri.v[2].minus(tri.v[0]))
            tri.plane.ToVec4_ToVec3_Cross(d2, d1)
            tri.plane.ToVec4_ToVec3_Normalize()
            tri.plane[3] = tri.v[0].times(tri.plane.ToVec4().ToVec3())

            // get the plane number before any clipping
            // we should avoid polluting the regular dmap planes with these
            // that are offset from the light origin...
            tri.planeNum = FindFloatPlane(tri.plane)
            CreateEdgesForTri(tri)
            i++
        }

        // clear our output buffer
        numOutputTris = 0

        // for each triangle, clip against all other triangles
        var numRemoved = 0
        var numComplete = 0
        var numFragmented = 0
        i = 0
        while (i < numTris) {
            val oldOutput = numOutputTris
            c_removedFragments = 0
            ClipTriangle_r(tris[i], 0, i, numTris, tris)
            if (numOutputTris == oldOutput) {
                numRemoved++ // completely unused
            } else if (c_removedFragments == 0) {
                // the entire triangle is visible
                numComplete++
                outputTris[oldOutput] = tris[i]
                outputTris[oldOutput]!!
                numOutputTris = oldOutput + 1
            } else {
                numFragmented++
                // we made at least one fragment

                // if we are at the low optimization level, just use a single
                // triangle if it produced any fragments
                if (dmap.dmapGlobals.shadowOptLevel == shadowOptLevel_t.SO_CULL_OCCLUDED) {
                    outputTris[oldOutput] = tris[i]
                    outputTris[oldOutput]!! //TODO:useless
                    numOutputTris = oldOutput + 1
                }
            }
            i++
        }
        Common.common.Printf("%d triangles completely invisible\n", numRemoved)
        Common.common.Printf("%d triangles completely visible\n", numComplete)
        Common.common.Printf("%d triangles fragmented\n", numFragmented)
        Common.common.Printf("%d shadowing fragments before optimization\n", numOutputTris)
    }

    /*
     ================
     OptimizeOutputTris
     ================
     */
    fun OptimizeOutputTris() {
        var i: Int

        // optimize the clipped surfaces
        var optGroups: optimizeGroup_s? = null
        var checkGroup: optimizeGroup_s?
        i = 0
        while (i < numOutputTris) {
            val tri: shadowTri_t = outputTris[i]!!
            val planeNum = tri.planeNum

            // add it to an optimize group
            checkGroup = optGroups
            while (checkGroup != null) {
                if (checkGroup.planeNum == planeNum) {
                    break
                }
                checkGroup = checkGroup.nextGroup
            }
            if (checkGroup == null) {
                // create a new optGroup
                checkGroup = optimizeGroup_s() // Mem_ClearedAlloc(sizeof(checkGroup));
                checkGroup.planeNum = planeNum
                checkGroup.nextGroup = optGroups
                optGroups = checkGroup
            }

            // create a mapTri for the optGroup
            val mtri = mapTri_s() // Mem_ClearedAlloc(sizeof(mtri));
            mtri.v[0].xyz.set(tri.v[0])
            mtri.v[1].xyz.set(tri.v[1])
            mtri.v[2].xyz.set(tri.v[2])
            mtri.next = checkGroup!!.triList
            checkGroup.triList = mtri
            i++
        }
        optimize.OptimizeGroupList(optGroups)
        numOutputTris = 0
        checkGroup = optGroups
        while (checkGroup != null) {
            var mtri = checkGroup.triList
            while (mtri != null) {
                val tri: shadowTri_t = outputTris[numOutputTris]!!
                numOutputTris++
                tri.v[0].set(mtri.v[0].xyz)
                tri.v[1].set(mtri.v[1].xyz)
                tri.v[2].set(mtri.v[2].xyz)
                mtri = mtri.next
            }
            checkGroup = checkGroup.nextGroup
        }
        map.FreeOptimizeGroupList(optGroups)
    }

    /*
     =====================
     GenerateSilEdges

     Output tris must be tjunction fixed and vertex uniqued
     A edge that is not exactly matched is a silhouette edge
     We could skip this and rely completely on the matched quad removal
     for all sil edges, but this will avoid the bulk of the checks.
     =====================
     */
    fun GenerateSilEdges() {
        var i: Int
        var j: Int

//	unsigned	*edges = (unsigned *)_alloca( (numOutputTris*3+1)*sizeof(*edges) );
        val edges = LongArray(numOutputTris * 3 + 1)
        var numEdges = 0
        numSilEdges = 0
        i = 0
        while (i < numOutputTris) {
            val a = outputTris[i]!!.index[0]
            val b = outputTris[i]!!.index[1]
            val c = outputTris[i]!!.index[2]
            if (a == b || a == c || b == c) {
                i++
                continue  // degenerate
            }
            j = 0
            while (j < 3) {
                var v1: Int
                var v2: Int
                v1 = outputTris[i]!!.index[j]
                v2 = outputTris[i]!!.index[(j + 1) % 3]
                if (v1 == v2) {
                    j++
                    continue  // degenerate
                }
                if (v1 > v2) {
                    edges[numEdges] = (v1 shl 16 or (v2 shl 1)).toLong()
                } else {
                    edges[numEdges] = (v2 shl 16 or (v1 shl 1) or 1).toLong()
                }
                numEdges++
                j++
            }
            i++
        }

//        qsort(edges, numEdges, sizeof(edges[0]), EdgeSort);
        Arrays.sort(edges, 0, numEdges) //, new EdgeSort());//TODO:check whether the default sort is enough
        edges[numEdges] = -1 // force the last to make an edge if no matched to previous
        i = 0
        while (i < numEdges) {
            if (edges[i] xor edges[i + 1] == 1L) {
                // skip the next one, because we matched and
                // removed both
                i++
                i++
                continue
            }
            // this is an unmatched edge, so we need to generate a sil plane
            var v1: Int
            var v2: Int
            if (edges[i] and 1 != 0L) {
                v2 = (edges[i] shr 16).toInt()
                v1 = (edges[i] shr 1 and 0x7fff).toInt()
            } else {
                v1 = (edges[i] shr 16).toInt()
                v2 = (edges[i] shr 1 and 0x7fff).toInt()
            }
            if (numSilEdges == MAX_SIL_EDGES) {
                Common.common.Error("numSilEdges == MAX_SIL_EDGES")
            }
            silEdges[numSilEdges]!!.index[0] = v1
            silEdges[numSilEdges]!!.index[1] = v2
            numSilEdges++
            i++
        }
    }

    /*
     =====================
     GenerateSilPlanes

     Groups the silEdges into common planes
     =====================
     */
    fun GenerateSilPlanes() {
        numSilPlanes = 0
        silPlanes = Array(numSilEdges) { silPlane_t() } // Mem_Alloc(numSilEdges);

        // identify the silPlanes
        numSilPlanes = 0
        for (i in 0 until numSilEdges) {
            if (silEdges[i]!!.index[0] == silEdges[i]!!.index[1]) {
                continue  // degenerate
            }
            val v1 = uniqued!![silEdges[i]!!.index[0]]
            val v2 = uniqued!![silEdges[i]!!.index[1]]

            // search for an existing plane
            var j: Int
            j = 0
            while (j < numSilPlanes) {
                val d = v1.times(silPlanes!![j].normal)
                val d2 = v2.times(silPlanes!![j].normal)
                if (abs(d) < EDGE_PLANE_EPSILON
                    && abs(d2) < EDGE_PLANE_EPSILON
                ) {
                    silEdges[i]!!.nextEdge = silPlanes!![j].edges
                    silPlanes!![j].edges = silEdges[i]
                    break
                }
                j++
            }
            if (j == numSilPlanes) {
                // create a new silPlane
                silPlanes!![j].normal.Cross(v2, v1)
                silPlanes!![j].normal.Normalize()
                silEdges[i]!!.nextEdge = null
                silPlanes!![j].edges = silEdges[i]
                silPlanes!![j].fragmentedQuads = null
                numSilPlanes++
            }
        }
    }

    /*
     =============
     SaveQuad
     =============
     */
    fun SaveQuad(silPlane: silPlane_t, quad: silQuad_s) {
        // this fragment is a final fragment
        if (numSilQuads == MAX_SIL_QUADS) {
            Common.common.Error("numSilQuads == MAX_SIL_QUADS")
        }
        silQuads[numSilQuads] = quad
        silQuads[numSilQuads]!!.nextQuad = silPlane.fragmentedQuads
        silPlane.fragmentedQuads = silQuads[numSilQuads]
        numSilQuads++
    }

    //=====================================================================================
    /*
     ===================
     FragmentSilQuad

     Clip quads, or reconstruct?
     Generate them T-junction free, or require another pass of fix-tjunc?
     Call optimizer on a per-sil-plane basis?
     will this ever introduce tjunctions with the front faces?
     removal of planes can allow the rear projection to be farther optimized

     For quad clipping
     PlaneThroughEdge

     quad clipping introduces new vertexes

     Cannot just fragment edges, must emit full indexes

     what is the bounds on max indexes?
     the worst case is that all edges but one carve an existing edge in the middle,
     giving twice the input number of indexes (I think)

     can we avoid knowing about projected positions and still optimize?

     Fragment all edges first
     Introduces T-junctions
     create additional silEdges, linked to silPlanes

     In theory, we should never have more than one edge clipping a given
     fragment, but it is more robust if we check them all
     ===================
     */
    fun FragmentSilQuad(
        quad: silQuad_s, silPlane: silPlane_t,
        startEdge: shadowOptEdge_s?, skipEdge: shadowOptEdge_s
    ) {
        if (quad.nearV[0] == quad.nearV[1]) {
            return
        }
        var check = startEdge
        while (check != null) {
            if (check == skipEdge) {
                // don't clip against self
                check = check.nextEdge
                continue
            }
            if (check.index[0] == check.index[1]) {
                check = check.nextEdge
                continue
            }

            // make planes through both points of check
            for (i in 0..1) {
                val plane = idVec3()
                plane.Cross(uniqued!![check.index[i]], silPlane.normal)
                plane.Normalize()
                if (plane.Length() < 0.9) {
                    continue
                }

                // if the other point on check isn't on the negative side of the plane,
                // flip the plane
                if (uniqued!![check.index[1 xor i]].times(plane) > 0) {
                    plane.set(-plane)
                }
                val d1 = uniqued!![quad.nearV[0]].times(plane)
                val d2 = uniqued!![quad.nearV[1]].times(plane)
                val d3 = uniqued!![quad.farV[0]].times(plane)
                val d4 = uniqued!![quad.farV[1]].times(plane)

                // it is better to conservatively NOT split the quad, which, at worst,
                // will leave some extra overdraw
                // if the plane divides the incoming edge, split it and recurse
                // with the outside fraction before continuing with the inside fraction
                if (d1 > EDGE_PLANE_EPSILON && d3 > EDGE_PLANE_EPSILON && d2 < -EDGE_PLANE_EPSILON && d4 < -EDGE_PLANE_EPSILON
                    || d2 > EDGE_PLANE_EPSILON && d4 > EDGE_PLANE_EPSILON && d1 < -EDGE_PLANE_EPSILON && d3 < -EDGE_PLANE_EPSILON
                ) {
                    var f = d1 / (d1 - d2)
                    val f2 = d3 / (d3 - d4)
                    f = f2
                    if (f <= 0.0001 || f >= 0.9999) {
                        Common.common.Error("Bad silQuad fraction")
                    }

                    // finding uniques may be causing problems here
                    val nearMid = idVec3(
                        uniqued!![quad.nearV[0]].times(1 - f)
                            .plus(uniqued!![quad.nearV[1]].times(f))
                    )
                    val nearMidIndex = FindUniqueVert(nearMid)
                    val farMid = idVec3(
                        uniqued!![quad.farV[0]].times(1 - f)
                            .plus(uniqued!![quad.farV[1]].times(f))
                    )
                    val farMidIndex = FindUniqueVert(farMid)
                    if (d1 > EDGE_PLANE_EPSILON) {
                        quad.nearV[1] = nearMidIndex
                        quad.farV[1] = farMidIndex
                        FragmentSilQuad(quad, silPlane, check.nextEdge, skipEdge)
                        quad.nearV[0] = nearMidIndex
                        quad.farV[0] = farMidIndex
                    } else {
                        quad.nearV[0] = nearMidIndex
                        quad.farV[0] = farMidIndex
                        FragmentSilQuad(quad, silPlane, check.nextEdge, skipEdge)
                        quad.nearV[1] = nearMidIndex
                        quad.farV[1] = farMidIndex
                    }
                }
            }

            // make a plane through the line of check
            val separate = idPlane()
            val dir = idVec3(uniqued!![check.index[1]].minus(uniqued!![check.index[0]]))
            separate.Normal().Cross(dir, silPlane.normal)
            separate.Normal().Normalize()
            separate[3] = -uniqued!![check.index[1]].times(separate.Normal())

            // this may miss a needed separation when the quad would be
            // clipped into a triangle and a quad
            var d1 = separate.Distance(uniqued!![quad.nearV[0]])
            var d2 = separate.Distance(uniqued!![quad.farV[0]])
            if (d1 < EDGE_PLANE_EPSILON && d2 < EDGE_PLANE_EPSILON
                || d1 > -EDGE_PLANE_EPSILON && d2 > -EDGE_PLANE_EPSILON
            ) {
                check = check.nextEdge
                continue
            }

            // split the quad at this plane
            var f = d1 / (d1 - d2)
            val mid0 = idVec3(
                uniqued!![quad.nearV[0]].times(1 - f)
                    .plus(uniqued!![quad.farV[0]].times(f))
            )
            val mid0Index = FindUniqueVert(mid0)
            d1 = separate.Distance(uniqued!![quad.nearV[1]])
            d2 = separate.Distance(uniqued!![quad.farV[1]])
            f = d1 / (d1 - d2)
            if (f < 0 || f > 1) {
                check = check.nextEdge
                continue
            }
            val mid1 = idVec3(
                uniqued!![quad.nearV[1]].times(1 - f)
                    .plus(uniqued!![quad.farV[1]].times(f))
            )
            val mid1Index = FindUniqueVert(mid1)
            quad.nearV[0] = mid0Index
            quad.nearV[1] = mid1Index
            FragmentSilQuad(quad, silPlane, check.nextEdge, skipEdge)
            quad.farV[0] = mid0Index
            quad.farV[1] = mid1Index
            check = check.nextEdge
        }
        SaveQuad(silPlane, quad)
    }

    /*
     ===============
     FragmentSilQuads
     ===============
     */
    fun FragmentSilQuads() {
        // group the edges into common planes
        GenerateSilPlanes()
        numSilQuads = 0

        // fragment overlapping edges
        for (i in 0 until numSilPlanes) {
            val sil: silPlane_t = silPlanes!![i]
            var e1 = sil.edges
            while (e1 != null) {
                val quad = silQuad_s()
                quad.nearV[0] = e1.index[0]
                quad.nearV[1] = e1.index[1]
                if (e1.index[0] == e1.index[1]) {
                    Common.common.Error("FragmentSilQuads: degenerate edge")
                }
                quad.farV[0] = e1.index[0] + numUniquedBeforeProjection
                quad.farV[1] = e1.index[1] + numUniquedBeforeProjection
                FragmentSilQuad(quad, sil, sil.edges, e1)
                e1 = e1.nextEdge
            }
        }
    }

    /*
     =====================
     EmitFragmentedSilQuads

     =====================
     */
    fun EmitFragmentedSilQuads() {
        var i: Int
        var j: Int
        var k: Int
        var mtri: mapTri_s?
        i = 0
        while (i < numSilPlanes) {
            val sil: silPlane_t = silPlanes!![i]

            // prepare for optimizing the sil quads on each side of the sil plane
            val groups = Array(2) { optimizeGroup_s() }
            //		memset( &groups, 0, sizeof( groups ) );
            val planes: Array<idPlane> = idPlane.generateArray(2)
            planes[0].SetNormal(sil.normal) //TODO:reinterpret cast
            planes[0][3] = 0.0f
            planes[1].set(planes[0].unaryMinus())
            groups[0].planeNum = FindFloatPlane(planes[0])
            groups[1].planeNum = FindFloatPlane(planes[1])

            // emit the quads that aren't matched
            var f1 = sil.fragmentedQuads
            while (f1 != null) {
                var f2: silQuad_s?
                f2 = sil.fragmentedQuads
                while (f2 != null) {
                    if (f2 == f1) {
                        f2 = f2.nextQuad
                        continue
                    }
                    // in theory, this is sufficient, but we might
                    // have some cases of tripple+ matching, or unclipped rear projections
                    if (f1.nearV[0] == f2.nearV[1] && f1.nearV[1] == f2.nearV[0]) {
                        break
                    }
                    f2 = f2.nextQuad
                }
                // if we went through all the quads without finding a match, emit the quad
                if (f2 == null) {
                    var gr: optimizeGroup_s?
                    val v1 = idVec3()
                    val v2 = idVec3()
                    val normal = idVec3()
                    mtri = mapTri_s() // Mem_ClearedAlloc(sizeof(mtri));
                    mtri.v[0].xyz.set(uniqued!![f1.nearV[0]])
                    mtri.v[1].xyz.set(uniqued!![f1.nearV[1]])
                    mtri.v[2].xyz.set(uniqued!![f1.farV[1]])
                    v1.set(mtri.v[1].xyz.minus(mtri.v[0].xyz))
                    v2.set(mtri.v[2].xyz.minus(mtri.v[0].xyz))
                    normal.Cross(v2, v1)
                    gr = if (normal.times(planes[0].Normal()) > 0) {
                        groups[0]
                    } else {
                        groups[1]
                    }
                    mtri.next = gr.triList
                    gr.triList = mtri
                    mtri = mapTri_s() // Mem_ClearedAlloc(sizeof(mtri));
                    mtri.v[0].xyz.set(uniqued!![f1.farV[0]])
                    mtri.v[1].xyz.set(uniqued!![f1.nearV[0]])
                    mtri.v[2].xyz.set(uniqued!![f1.farV[1]])
                    mtri.next = gr.triList
                    gr.triList = mtri

//#if 0
//				// emit a sil quad all the way to the projection plane
//				int index = ret!!.totalIndexes;
//				if ( index + 6 > maxRetIndexes ) {
//					common.Error( "maxRetIndexes exceeded" );
//				}
//				ret!!.indexes[index+0] = f1.nearV[0];
//				ret!!.indexes[index+1] = f1.nearV[1];
//				ret!!.indexes[index+2] = f1.farV[1];
//				ret!!.indexes[index+3] = f1.farV[0];
//				ret!!.indexes[index+4] = f1.nearV[0];
//				ret!!.indexes[index+5] = f1.farV[1];
//				ret!!.totalIndexes += 6;
//#endif
                }
                f1 = f1.nextQuad
            }

            // optimize
            j = 0
            while (j < 2) {
                if (groups[j].triList == null) {
                    j++
                    continue
                }
                if (dmap.dmapGlobals.shadowOptLevel == shadowOptLevel_t.SO_SIL_OPTIMIZE) {
                    optimize.OptimizeGroupList(groups[j])
                }
                // add as indexes
                mtri = groups[j].triList
                while (mtri != null) {
                    k = 0
                    while (k < 3) {
                        if (ret!!.totalIndexes == maxRetIndexes) {
                            Common.common.Error("maxRetIndexes exceeded")
                        }
                        ret!!.indexes!![ret!!.totalIndexes] = FindUniqueVert(mtri.v[k].xyz)
                        ret!!.totalIndexes++
                        k++
                    }
                    mtri = mtri.next
                }
                tritools.FreeTriList(groups[j].triList)
                j++
            }
            i++
        }

        // we don't need the silPlane grouping anymore
        silPlanes = null //Mem_Free(silPlanes);
    }

    //==================================================================================
    /*
     =================
     EmitUnoptimizedSilEdges
     =================
     */
    fun EmitUnoptimizedSilEdges() {
        var i: Int
        i = 0
        while (i < numSilEdges) {
            val v1 = silEdges[i]!!.index[0]
            val v2 = silEdges[i]!!.index[1]
            val index = ret!!.totalIndexes
            ret!!.indexes!![index + 0] = v1
            ret!!.indexes!![index + 1] = v2
            ret!!.indexes!![index + 2] = v2 + numUniquedBeforeProjection
            ret!!.indexes!![index + 3] = v1 + numUniquedBeforeProjection
            ret!!.indexes!![index + 4] = v1
            ret!!.indexes!![index + 5] = v2 + numUniquedBeforeProjection
            ret!!.totalIndexes += 6
            i++
        }
    }

    //==================================================================================
    /*
     ================
     FindUniqueVert
     ================
     */
    fun FindUniqueVert(v: idVec3): Int {
        var k: Int
        k = 0
        while (k < numUniqued) {
            val check = uniqued!![k]
            if (abs(v[0] - check[0]) < UNIQUE_EPSILON && abs(v[1] - check[1]) < UNIQUE_EPSILON && abs(
                    v[2] - check[2]
                ) < UNIQUE_EPSILON
            ) {
                return k
            }
            k++
        }
        if (numUniqued == maxUniqued) {
            Common.common.Error("FindUniqueVert: numUniqued == maxUniqued")
        }
        uniqued!![numUniqued] = v
        numUniqued++
        return k
    }

    /*
     ===================
     UniqueVerts

     Snaps all triangle verts together, setting tri.index[]
     and generating numUniqued and uniqued.
     These are still in projection-centered space, not global space
     ===================
     */
    fun UniqueVerts() {
        var i: Int
        var j: Int

        // we may add to uniqued later when splitting sil edges, so leave
        // some extra room
        maxUniqued = 100000 // numOutputTris * 10 + 1000;
        uniqued = idVec3.generateArray(maxUniqued) // Mem_Alloc(maxUniqued);
        numUniqued = 0
        i = 0
        while (i < numOutputTris) {
            j = 0
            while (j < 3) {
                outputTris[i]!!.index[j] = FindUniqueVert(outputTris[i]!!.v[j])
                j++
            }
            i++
        }
    }

    /*
     ======================
     ProjectUniqued
     ======================
     */
    fun ProjectUniqued(projectionOrigin: idVec3, projectionPlane: idPlane) {
        // calculate the projection
        val mat = idVec4.generateArray(4)
        tr_stencilshadow.R_LightProjectionMatrix(projectionOrigin, projectionPlane, mat)
        if (numUniqued * 2 > maxUniqued) {
            Common.common.Error("ProjectUniqued: numUniqued * 2 > maxUniqued")
        }

        // this is goofy going back and forth between the spaces,
        // but I don't want to change R_LightProjectionMatrix righ tnow...
        for (i in 0 until numUniqued) {
            // put the vert back in global space, instead of light centered space
            val `in` = idVec3(uniqued!![i].plus(projectionOrigin))

            // project to far plane
            var w: Float
            var oow: Float
            val out = idVec3()
            w = `in`.times(mat[3].ToVec3()) + mat[3][3]
            oow = 1.0f / w
            out.x = (`in`.times(mat[0].ToVec3()) + mat[0][3]) * oow
            out.y = (`in`.times(mat[1].ToVec3()) + mat[1][3]) * oow
            out.z = (`in`.times(mat[2].ToVec3()) + mat[2][3]) * oow
            uniqued!![numUniqued + i] = out.minus(projectionOrigin)
        }
        numUniqued *= 2
    }

    //=======================================================================
    /*
     ====================
     SuperOptimizeOccluders

     This is the callback from the renderer shadow generation routine, after
     verts have been culled against individual frustums of point lights

     ====================
     */

    fun SuperOptimizeOccluders(
        verts: Array<idVec4>,
        indexes: IntArray,
        numIndexes: Int,
        projectionPlane: idPlane,
        projectionOrigin: idVec3
    ): optimizedShadow_t {
//	memset( &ret, 0, sizeof( ret ) );
        ret = optimizedShadow_t()

        // generate outputTris, removing fragments that are occluded by closer fragments
        ClipOccluders(verts, indexes, numIndexes, projectionOrigin)
        if (TempDump.etoi(dmap.dmapGlobals.shadowOptLevel) >= TempDump.etoi(shadowOptLevel_t.SO_CULL_OCCLUDED)) {
            OptimizeOutputTris()
        }

        // match up common verts
        UniqueVerts()

        // now that we have uniqued the vertexes, we can find unmatched
        // edges, which are silhouette planes
        GenerateSilEdges()

        // generate the projected verts
        numUniquedBeforeProjection = numUniqued
        ProjectUniqued(projectionOrigin, projectionPlane)

        // fragment the sil edges where the overlap,
        // possibly generating some additional unique verts
        if (TempDump.etoi(dmap.dmapGlobals.shadowOptLevel) >= TempDump.etoi(shadowOptLevel_t.SO_CLIP_SILS)) {
            FragmentSilQuads()
        }

        // indexes for face and projection caps
        ret!!.numFrontCapIndexes = numOutputTris * 3
        ret!!.numRearCapIndexes = numOutputTris * 3
        if (TempDump.etoi(dmap.dmapGlobals.shadowOptLevel) >= TempDump.etoi(shadowOptLevel_t.SO_CLIP_SILS)) {
            ret!!.numSilPlaneIndexes = numSilQuads * 12 // this is the worst case with clipping
        } else {
            ret!!.numSilPlaneIndexes = numSilEdges * 6 // this is the worst case with clipping
        }
        ret!!.totalIndexes = 0
        maxRetIndexes =
            ret!!.numFrontCapIndexes + ret!!.numRearCapIndexes + ret!!.numSilPlaneIndexes
        ret!!.indexes = IntArray(maxRetIndexes) // Mem_Alloc(maxRetIndexes);
        for (i in 0 until numOutputTris) {
            // flip the indexes so the surface triangle faces outside the shadow volume
            ret!!.indexes!![i * 3 + 0] = outputTris[i]!!.index[2]
            ret!!.indexes!![i * 3 + 1] = outputTris[i]!!.index[1]
            ret!!.indexes!![i * 3 + 2] = outputTris[i]!!.index[0]
            ret!!.indexes!![(numOutputTris + i) * 3 + 0] =
                numUniquedBeforeProjection + outputTris[i]!!.index[0]
            ret!!.indexes!![(numOutputTris + i) * 3 + 1] =
                numUniquedBeforeProjection + outputTris[i]!!.index[1]
            ret!!.indexes!![(numOutputTris + i) * 3 + 2] =
                numUniquedBeforeProjection + outputTris[i]!!.index[2]
        }
        // emit the sil planes
        ret!!.totalIndexes = ret!!.numFrontCapIndexes + ret!!.numRearCapIndexes
        if (TempDump.etoi(dmap.dmapGlobals.shadowOptLevel) >= TempDump.etoi(shadowOptLevel_t.SO_CLIP_SILS)) {
            // re-optimize the sil planes, cutting
            EmitFragmentedSilQuads()
        } else {
            // indexes for silhouette edges
            EmitUnoptimizedSilEdges()
        }

        // we have all the verts now
        // create twice the uniqued verts
        ret!!.numVerts = numUniqued
        ret!!.verts = idVec3.generateArray(ret!!.numVerts) // Mem_Alloc(ret!!.numVerts);
        for (i in 0 until numUniqued) {
            // put the vert back in global space, instead of light centered space
            ret!!.verts!![i].set(uniqued!![i].plus(projectionOrigin))
        }

        // set the final index count
        ret!!.numSilPlaneIndexes =
            ret!!.totalIndexes - (ret!!.numFrontCapIndexes + ret!!.numRearCapIndexes)

        // free out local data
        uniqued = null //Mem_Free(uniqued);
        return ret!!
    }

    /*
     =================
     RemoveDegenerateTriangles
     =================
     */
    fun RemoveDegenerateTriangles(tri: srfTriangles_s) {
        var c_removed: Int
        var i: Int
        var a: Int
        var b: Int
        var c: Int

        // check for completely degenerate triangles
        c_removed = 0
        i = 0
        while (i < tri.numIndexes) {
            a = tri.indexes!![i]
            b = tri.indexes!![i + 1]
            c = tri.indexes!![i + 2]
            if (a == b || a == c || b == c) {
                c_removed++
                //			memmove( tri.indexes + i, tri.indexes + i + 3, ( tri.numIndexes - i - 3 ) * sizeof( tri.indexes!![0] ) );
                System.arraycopy(tri.indexes, i + 3, tri.indexes, i, tri.numIndexes - i - 3)
                tri.numIndexes -= 3
                if (i < tri.numShadowIndexesNoCaps) {
                    tri.numShadowIndexesNoCaps -= 3
                }
                if (i < tri.numShadowIndexesNoFrontCaps) {
                    tri.numShadowIndexesNoFrontCaps -= 3
                }
                i -= 3
            }
            i += 3
        }

        // this doesn't free the memory used by the unused verts
        if (c_removed != 0) {
            Common.common.Printf("removed %d degenerate triangles from shadow\n", c_removed)
        }
    }

    //==================================================================================
    /*
     ====================
     CleanupOptimizedShadowTris

     Uniques all verts across the frustums
     removes matched sil quads at frustum seams
     removes degenerate tris
     ====================
     */

    fun CleanupOptimizedShadowTris(tri: srfTriangles_s) {
        var i: Int

        // unique all the verts
        maxUniqued = tri.numVerts
        uniqued = idVec3.generateArray(maxUniqued) //new idVec3[maxUniqued];
        numUniqued = 0
        val remap = IntArray(tri.numVerts)
        i = 0
        while (i < tri.numIndexes) {
            if (tri.indexes!![i] > tri.numVerts || tri.indexes!![i] < 0) {
                Common.common.Error("CleanupOptimizedShadowTris: index out of range")
            }
            i++
        }
        i = 0
        while (i < tri.numVerts) {
            remap[i] = FindUniqueVert(tri.shadowVertexes!![i].xyz.ToVec3())
            i++
        }
        tri.numVerts = numUniqued
        i = 0
        while (i < tri.numVerts) {
            tri.shadowVertexes!![i].xyz.set(uniqued!![i])
            tri.shadowVertexes!![i].xyz[3] = 1.0f
            i++
        }
        i = 0
        while (i < tri.numIndexes) {
            tri.indexes!![i] = remap[tri.indexes!![i]]
            i++
        }

        // remove matched quads
        var numSilIndexes = tri.numShadowIndexesNoCaps
        var i2 = 0
        while (i2 < numSilIndexes) {
            var j: Int
            j = i2 + 6
            while (j < numSilIndexes) {

                // if there is a reversed quad match, we can throw both of them out
                // this is not a robust check, it relies on the exact ordering of
                // quad indexes
                if (tri.indexes!![i2 + 0] == tri.indexes!![j + 1] && tri.indexes!![i2 + 1] == tri.indexes!![j + 0] && tri.indexes!![i2 + 2] == tri.indexes!![j + 3] && tri.indexes!![i2 + 3] == tri.indexes!![j + 5] && tri.indexes!![i2 + 4] == tri.indexes!![j + 1] && tri.indexes!![i2 + 5] == tri.indexes!![j + 3]) {
                    break
                }
                j += 6
            }
            if (j == numSilIndexes) {
                i2 += 6
                continue
            }
            var k: Int
            // remove first quad
            k = i2 + 6
            while (k < j) {
                tri.indexes!![k - 6] = tri.indexes!![k]
                k++
            }
            // remove second quad
            k = j + 6
            while (k < tri.numIndexes) {
                tri.indexes!![k - 12] = tri.indexes!![k]
                k++
            }
            numSilIndexes -= 12
            i2 -= 6
            i2 += 6
        }
        val removed = tri.numShadowIndexesNoCaps - numSilIndexes
        tri.numIndexes -= removed
        tri.numShadowIndexesNoCaps -= removed
        tri.numShadowIndexesNoFrontCaps -= removed

        // remove degenerates after we have removed quads, so the double
        // triangle pairing isn't disturbed
        RemoveDegenerateTriangles(tri)
    }

    /*
     ========================
     CreateLightShadow

     This is called from dmap in util/surface.cpp
     shadowerGroups should be exactly clipped to the light frustum before calling.
     shadowerGroups is optimized by this function, but the contents can be freed, because the returned
     lightShadow_t list is a further culling and optimization of the data.
     ========================
     */
    fun CreateLightShadow(shadowerGroups: optimizeGroup_s?, light: mapLight_t): srfTriangles_s? {
        Common.common.Printf("----- CreateLightShadow %p -----\n", light)

        // optimize all the groups
        optimize.OptimizeGroupList(shadowerGroups)

        // combine all the triangles into one list
        var combined: mapTri_s?
        combined = null
        var group = shadowerGroups
        while (group != null) {
            combined = tritools.MergeTriLists(combined, tritools.CopyTriList(group.triList))
            group = group.nextGroup
        }
        if (null == combined) {
            return null
        }

        // find uniqued vertexes
        val occluders = output.ShareMapTriVerts(combined)
        tritools.FreeTriList(combined)

        // find silhouette information for the triSurf
        R_CleanupTriangles(occluders, false, true, false)

        // let the renderer build the shadow volume normally
        val space = idRenderEntityLocal()
        space.modelMatrix[0] = 1.0f
        space.modelMatrix[5] = 1.0f
        space.modelMatrix[10] = 1.0f
        space.modelMatrix[15] = 1.0f
        val cullInfo = srfCullInfo_t()
        //	memset( &cullInfo, 0, sizeof( cullInfo ) );

        // call the normal shadow creation, but with the superOptimize flag set, which will
        // call back to SuperOptimizeOccluders after clipping the triangles to each frustum
        val shadowTris: srfTriangles_s?
        shadowTris = if (dmap.dmapGlobals.shadowOptLevel == shadowOptLevel_t.SO_MERGE_SURFACES) {
            tr_stencilshadow.R_CreateShadowVolume(space, occluders, light.def, shadowGen_t.SG_STATIC, cullInfo)
        } else {
            tr_stencilshadow.R_CreateShadowVolume(space, occluders, light.def, shadowGen_t.SG_OFFLINE, cullInfo)
        }
        R_FreeStaticTriSurf(occluders)
        Interaction.R_FreeInteractionCullInfo(cullInfo)
        if (shadowTris != null) {
            dmap.dmapGlobals.totalShadowTriangles += shadowTris.numIndexes / 3
            dmap.dmapGlobals.totalShadowVerts += shadowTris.numVerts / 3
        }
        return shadowTris
    }

    class shadowTri_t {
        var edge: Array<idVec3> = idVec3.generateArray(3) // positive side is inside the triangle
        var index: IntArray = IntArray(3)
        val plane: idPlane = idPlane() // positive side is forward for the triangle, which is away from the light
        var planeNum // from original triangle, not calculated from the clipped verts
                = 0
        var v: Array<idVec3> = idVec3.generateArray(3)
    }

    class shadowOptEdge_s {
        var index: IntArray = IntArray(2)
        var nextEdge: shadowOptEdge_s? = null
    }

    class silQuad_s {
        var farV: IntArray = IntArray(2) // will always be a projection of near[]
        var nearV: IntArray = IntArray(2)
        var nextQuad: silQuad_s? = null
    }

    class silPlane_t {
        var edges: shadowOptEdge_s? = null
        var fragmentedQuads: silQuad_s? = null
        val normal: idVec3 = idVec3() // all sil planes go through the projection origin
    }

    @Deprecated("")
    internal class EdgeSort : cmp_t<Long> {
        override fun compare(a: Long, b: Long): Int {
            if (a < b) {
                return -1
            }
            return if (a > b) {
                1
            } else 0
        }
    }
}
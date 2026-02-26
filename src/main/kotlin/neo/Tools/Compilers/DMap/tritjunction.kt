package neo.Tools.Compilers.DMap

import neo.Renderer.ModelManager
import neo.Tools.Compilers.DMap.dmap.mapTri_s
import neo.Tools.Compilers.DMap.dmap.optimizeGroup_s
import neo.Tools.Compilers.DMap.dmap.uEntity_t
import neo.framework.Common
import neo.idlib.BV.idBounds
import neo.idlib.Text.Str.idStr
import neo.idlib.geometry.DrawVert.idDrawVert
import neo.idlib.math.*
import neo.idlib.math.Matrix.idMat3
import kotlin.math.ceil
import kotlin.math.floor

object tritjunction {
    const val HASH_BINS = 16

    /*

     T junction fixing never creates more xyz points, but
     new vertexes will be created when different surfaces
     cause a fix

     The vertex cleaning accomplishes two goals: removing extranious low order
     bits to avoid numbers like 1.000001233, and grouping nearby vertexes
     together.  Straight truncation accomplishes the first foal, but two vertexes
     only a tiny epsilon apart could still be spread to different snap points.
     To avoid this, we allow the merge test to group points together that
     snapped to neighboring integer coordinates.

     Snaping verts can drag some triangles backwards or collapse them to points,
     which will cause them to be removed.


     When snapping to ints, a point can move a maximum of sqrt(3)/2 distance
     Two points that were an epsilon apart can then become sqrt(3) apart

     A case that causes recursive overflow with point to triangle fixing:

     ///////////A
     C            D
     ///////////B

     Triangle ABC tests against point D and splits into triangles ADC and DBC
     Triangle DBC then tests against point A again and splits into ABC and ADB
     infinite recursive loop


     For a given source triangle
     init the no-check list to hold the three triangle hashVerts

     recursiveFixTriAgainstHash

     recursiveFixTriAgainstHashVert_r
     if hashVert is on the no-check list
     exit
     if the hashVert should split the triangle
     add to the no-check list
     recursiveFixTriAgainstHash(a)
     recursiveFixTriAgainstHash(b)

     */
    const val SNAP_FRACTIONS = 32

    //#define	SNAP_FRACTIONS	8
    //#define	SNAP_FRACTIONS	1
    //
    const val VERTEX_EPSILON = 1.0f / SNAP_FRACTIONS
    const val COLINEAR_EPSILON = 1.8 * VERTEX_EPSILON
    val hashIntMins: IntArray = IntArray(3)
    val hashIntScale: IntArray = IntArray(3)
    val hashVerts: Array<Array<Array<hashVert_s?>>> =
        Array(HASH_BINS) { Array(HASH_BINS) { arrayOfNulls<hashVert_s?>(HASH_BINS) } }
    val hashBounds: idBounds = idBounds()
    val hashScale: idVec3 = idVec3()
    var numHashVerts = 0
    var numTotalVerts = 0

    /*
     ===============
     GetHashVert

     Also modifies the original vert to the snapped value
     ===============
     */
    fun GetHashVert(v: idVec3): hashVert_s {
        val iv = IntArray(3)
        val block = IntArray(3)
        var i: Int
        var hv: hashVert_s?
        numTotalVerts++

        // snap the vert to integral values
        i = 0
        while (i < 3) {
            iv[i] = floor((v[i] + 0.5f / SNAP_FRACTIONS) * SNAP_FRACTIONS).toInt()
            block[i] = (iv[i] - hashIntMins[i]) / hashIntScale[i]
            if (block[i] < 0) {
                block[i] = 0
            } else if (block[i] >= HASH_BINS) {
                block[i] = HASH_BINS - 1
            }
            i++
        }

        // see if a vertex near enough already exists
        // this could still fail to find a near neighbor right at the hash block boundary
        hv = hashVerts[block[0]][block[1]][block[2]]
        while (hv != null) {

//#if 0
//		if ( hv.iv[0] == iv[0] && hv.iv[1] == iv[1] && hv.iv[2] == iv[2] ) {
//			VectorCopy( hv.v, v );
//			return hv;
//		}
//#else
            i = 0
            while (i < 3) {
                var d: Int
                d = hv.iv[i] - iv[i]
                if (d < -1 || d > 1) {
                    break
                }
                i++
            }
            if (i == 3) {
                VectorCopy(hv.v, v)
                return hv
            }
            hv = hv.next
        }

        // create a new one
        hv = hashVert_s() // Mem_Alloc(sizeof(hv));
        hv.next = hashVerts[block[0]][block[1]][block[2]]
        hashVerts[block[0]][block[1]][block[2]] = hv
        hv.iv[0] = iv[0]
        hv.iv[1] = iv[1]
        hv.iv[2] = iv[2]
        hv.v[0] = iv[0].toFloat() / SNAP_FRACTIONS
        hv.v[1] = iv[1].toFloat() / SNAP_FRACTIONS
        hv.v[2] = iv[2].toFloat() / SNAP_FRACTIONS
        VectorCopy(hv.v, v)
        numHashVerts++
        return hv
    }

    /*
     ==================
     HashBlocksForTri

     Returns an inclusive bounding box of hash
     bins that should hold the triangle
     ==================
     */
    fun HashBlocksForTri(tri: mapTri_s, blocks: Array<IntArray> /*[2][3]*/) {
        val bounds = idBounds()
        var i: Int
        bounds.Clear()
        bounds.AddPoint(tri.v[0].xyz)
        bounds.AddPoint(tri.v[1].xyz)
        bounds.AddPoint(tri.v[2].xyz)

        // add a 1.0f slop margin on each side
        i = 0
        while (i < 3) {
            blocks[0][i] = ((bounds[0, i] - 1.0f - hashBounds[0, i]) / hashScale[i]).toInt()
            if (blocks[0][i] < 0) {
                blocks[0][i] = 0
            } else if (blocks[0][i] >= HASH_BINS) {
                blocks[0][i] = HASH_BINS - 1
            }
            blocks[1][i] = ((bounds[1, i] + 1.0f - hashBounds[0, i]) / hashScale[i]).toInt()
            if (blocks[1][i] < 0) {
                blocks[1][i] = 0
            } else if (blocks[1][i] >= HASH_BINS) {
                blocks[1][i] = HASH_BINS - 1
            }
            i++
        }
    }

    /*
     =================
     HashTriangles

     Removes triangles that are degenerated or flipped backwards
     =================
     */
    fun HashTriangles(groupList: optimizeGroup_s) {
        var a: mapTri_s?
        var vert: Int
        var i: Int
        var group: optimizeGroup_s?

        // clear the hash tables
//        memset(hashVerts, 0, sizeof(hashVerts));
        hashVert_s.memset(hashVerts)
        numHashVerts = 0
        numTotalVerts = 0

        // bound all the triangles to determine the bucket size
        hashBounds.Clear()
        group = groupList
        while (group != null) {
            a = group.triList
            while (a != null) {
                hashBounds.AddPoint(a.v[0].xyz)
                hashBounds.AddPoint(a.v[1].xyz)
                hashBounds.AddPoint(a.v[2].xyz)
                a = a.next
            }
            group = group.nextGroup
        }

        // spread the bounds so it will never have a zero size
        i = 0
        while (i < 3) {
            hashBounds[0, i] = floor((hashBounds[0, i] - 1))
            hashBounds[1, i] = ceil((hashBounds[1, i] + 1))
            hashIntMins[i] = (hashBounds[0, i] * SNAP_FRACTIONS).toInt()
            hashScale[i] = (hashBounds[1, i] - hashBounds[0, i]) / HASH_BINS
            hashIntScale[i] = (hashScale[i] * SNAP_FRACTIONS).toInt()
            if (hashIntScale[i] < 1) {
                hashIntScale[i] = 1
            }
            i++
        }

        // add all the points to the hash buckets
        group = groupList
        while (group != null) {

            // don't create tjunctions against discrete surfaces (blood decals, etc)
            if (group.material != null && group.material!!.IsDiscrete()) {
                group = group.nextGroup
                continue
            }
            a = group.triList
            while (a != null) {
                vert = 0
                while (vert < 3) {
                    a.hashVert[vert] = GetHashVert(a.v[vert].xyz)
                    vert++
                }
                a = a.next
            }
            group = group.nextGroup
        }
    }

    /*
     =================
     FreeTJunctionHash

     The optimizer may add some more crossing verts
     after t junction processing
     =================
     */
    fun FreeTJunctionHash() {
        var i: Int
        var j: Int
        var k: Int
        var hv: hashVert_s?
        var next: hashVert_s?
        i = 0
        while (i < HASH_BINS) {
            j = 0
            while (j < HASH_BINS) {
                k = 0
                while (k < HASH_BINS) {
                    hv = hashVerts[i][j][k]
                    while (hv != null) {
                        next = hv.next
                        hashVerts[i][j][k] = null
                        hv = hashVerts[i][j][k] //Mem_Free(hv);
                        hv = next
                    }
                    k++
                }
                j++
            }
            i++
        }
        //        memset(hashVerts, 0, sizeof(hashVerts));
        hashVert_s.memset(hashVerts)
    }

    /*
     ==================
     FixTriangleAgainstHashVert

     Returns a list of two new mapTri if the hashVert is
     on an edge of the given mapTri, otherwise returns NULL.
     ==================
     */
    fun FixTriangleAgainstHashVert(a: mapTri_s, hv: hashVert_s): mapTri_s? {
        var i: Int
        var v1: idDrawVert?
        var v2: idDrawVert?
        var v3: idDrawVert?
        val split = idDrawVert()
        val dir = idVec3()
        var len: Float
        var frac: Float
        var new1: mapTri_s?
        var new2: mapTri_s?
        val temp = idVec3()
        var d: Float
        var off: Float
        val v = idVec3()
        val plane1 = idPlane()
        val plane2 = idPlane()
        v.set(hv.v)

        // if the triangle already has this hashVert as a vert,
        // it can't be split by it
        if (a.hashVert[0] == hv || a.hashVert[1] == hv || a.hashVert[2] == hv) {
            return null
        }

        // we probably should find the edge that the vertex is closest to.
        // it is possible to be < 1 unit away from multiple
        // edges, but we only want to split by one of them
        i = 0
        while (i < 3) {
            v1 = a.v[i]
            v2 = a.v[(i + 1) % 3]
            v3 = a.v[(i + 2) % 3]
            VectorSubtract(v2.xyz, v1.xyz, dir)
            len = dir.Normalize()

            // if it is close to one of the edge vertexes, skip it
            VectorSubtract(v, v1.xyz, temp)
            d = DotProduct(temp, dir)
            if (d <= 0 || d >= len) {
                i++
                continue
            }

            // make sure it is on the line
            VectorMA(v1.xyz, d, dir, temp)
            VectorSubtract(temp, v, temp)
            off = temp.Length()
            if (off <= -COLINEAR_EPSILON || off >= COLINEAR_EPSILON) {
                i++
                continue
            }

            // take the x/y/z from the splitter,
            // but interpolate everything else from the original tri
            VectorCopy(v, split.xyz)
            frac = d / len
            split.st[0] = v1.st[0] + frac * (v2.st[0] - v1.st[0])
            split.st[1] = v1.st[1] + frac * (v2.st[1] - v1.st[1])
            split.normal[0] = v1.normal[0] + frac * (v2.normal[0] - v1.normal[0])
            split.normal[1] = v1.normal[1] + frac * (v2.normal[1] - v1.normal[1])
            split.normal[2] = v1.normal[2] + frac * (v2.normal[2] - v1.normal[2])
            split.normal.Normalize()

            // split the tri
            new1 = tritools.CopyMapTri(a)
            new1.v[(i + 1) % 3] = split
            new1.hashVert[(i + 1) % 3] = hv
            new1.next = null
            new2 = tritools.CopyMapTri(a)
            new2.v[i] = split
            new2.hashVert[i] = hv
            new2.next = new1
            plane1.FromPoints(new1.hashVert[0].v, new1.hashVert[1].v, new1.hashVert[2].v)
            plane2.FromPoints(new2.hashVert[0].v, new2.hashVert[1].v, new2.hashVert[2].v)
            d = DotProduct(plane1, plane2)

            // if the two split triangle's normals don't face the same way,
            // it should not be split
            if (d <= 0) {
                tritools.FreeTriList(new2)
                i++
                continue
            }
            return new2
            i++
        }
        return null
    }

    /*
     ==================
     FixTriangleAgainstHash

     Potentially splits a triangle into a list of triangles based on tjunctions
     ==================
     */
    fun FixTriangleAgainstHash(tri: mapTri_s): mapTri_s? {
        var fixed: mapTri_s?
        var a: mapTri_s?
        var test: mapTri_s?
        var next: mapTri_s?
        val blocks = Array(2) { IntArray(3) }
        var i: Int
        var j: Int
        var k: Int
        var hv: hashVert_s?

        // if this triangle is degenerate after point snapping,
        // do nothing (this shouldn't happen, because they should
        // be removed as they are hashed)
        if (tri.hashVert[0] === tri.hashVert[1] || tri.hashVert[0] === tri.hashVert[2] || tri.hashVert[1] === tri.hashVert[2]) {
            return null
        }
        fixed = tritools.CopyMapTri(tri)
        fixed.next = null
        HashBlocksForTri(tri, blocks)
        i = blocks[0][0]
        while (i <= blocks[1][0]) {
            j = blocks[0][1]
            while (j <= blocks[1][1]) {
                k = blocks[0][2]
                while (k <= blocks[1][2]) {
                    hv = hashVerts[i][j][k]
                    while (hv != null) {

                        // fix all triangles in the list against this point
                        test = fixed
                        fixed = null
                        while (test != null) {
                            next = test.next
                            a = FixTriangleAgainstHashVert(test, hv)
                            if (a != null) {
                                // cut into two triangles
                                a.next!!.next = fixed
                                fixed = a
                                tritools.FreeTri(test)
                            } else {
                                test.next = fixed
                                fixed = test
                            }
                            test = next
                        }
                        hv = hv.next
                    }
                    k++
                }
                j++
            }
            i++
        }
        return fixed
    }

    /*
     ==================
     CountGroupListTris
     ==================
     */
    fun CountGroupListTris(groupList: optimizeGroup_s?): Int {
        var c: Int
        c = 0
        while (groupList != null) {
            c += tritools.CountTriList(groupList.triList!!)
            groupList.oSet(groupList.nextGroup)
        }
        return c
    }

    /*
     ==================
     FixAreaGroupsTjunctions
     ==================
     */
    fun FixAreaGroupsTjunctions(groupList: optimizeGroup_s?) {
        var tri: mapTri_s?
        var newList: mapTri_s?
        var fixed: mapTri_s?
        val startCount: Int
        val endCount: Int
        var group: optimizeGroup_s?
        if (dmap.dmapGlobals.noTJunc) {
            return
        }
        if (null == groupList) {
            return
        }
        startCount = CountGroupListTris(groupList)
        if (dmap.dmapGlobals.verbose) {
            Common.common.Printf("----- FixAreaGroupsTjunctions -----\n")
            Common.common.Printf("%6d triangles in\n", startCount)
        }
        HashTriangles(groupList)
        group = groupList
        while (group != null) {

            // don't touch discrete surfaces
            if (group.material != null && group.material!!.IsDiscrete()) {
                group = group.nextGroup
                continue
            }
            newList = null
            tri = group.triList
            while (tri != null) {
                fixed = FixTriangleAgainstHash(tri)
                newList = tritools.MergeTriLists(newList, fixed)
                tri = tri.next
            }
            tritools.FreeTriList(group.triList)
            group.triList = newList
            group = group.nextGroup
        }
        endCount = CountGroupListTris(groupList)
        if (dmap.dmapGlobals.verbose) {
            Common.common.Printf("%6d triangles out\n", endCount)
        }
    }

    /*
     ==================
     FixEntityTjunctions
     ==================
     */
    fun FixEntityTjunctions(e: uEntity_t) {
        var i: Int
        i = 0
        while (i < e.numAreas) {
            FixAreaGroupsTjunctions(e.areas[i].groups)
            FreeTJunctionHash()
            i++
        }
    }

    /*
     ==================
     FixGlobalTjunctions
     ==================
     */
    fun FixGlobalTjunctions(e: uEntity_t) {
        var a: mapTri_s?
        var vert: Int
        var i: Int
        var group: optimizeGroup_s?
        var areaNum: Int
        Common.common.Printf("----- FixGlobalTjunctions -----\n")

        // clear the hash tables
//        memset(hashVerts, 0, sizeof(hashVerts));
        hashVert_s.memset(hashVerts)
        numHashVerts = 0
        numTotalVerts = 0

        // bound all the triangles to determine the bucket size
        hashBounds.Clear()
        areaNum = 0
        while (areaNum < e.numAreas) {
            group = e.areas[areaNum].groups
            while (group != null) {
                a = group.triList
                while (a != null) {
                    hashBounds.AddPoint(a.v[0].xyz)
                    hashBounds.AddPoint(a.v[1].xyz)
                    hashBounds.AddPoint(a.v[2].xyz)
                    a = a.next
                }
                group = group.nextGroup
            }
            areaNum++
        }

        // spread the bounds so it will never have a zero size
        i = 0
        while (i < 3) {
            hashBounds[0, i] = floor((hashBounds[0, i] - 1))
            hashBounds[1, i] = ceil((hashBounds[1, i] + 1))
            hashIntMins[i] = (hashBounds[0, i] * SNAP_FRACTIONS).toInt()
            hashScale[i] = (hashBounds[1, i] - hashBounds[0, i]) / HASH_BINS
            hashIntScale[i] = (hashScale[i] * SNAP_FRACTIONS).toInt()
            if (hashIntScale[i] < 1) {
                hashIntScale[i] = 1
            }
            i++
        }

        // add all the points to the hash buckets
        areaNum = 0
        while (areaNum < e.numAreas) {
            group = e.areas[areaNum].groups
            while (group != null) {

                // don't touch discrete surfaces
                if (group.material != null && group.material!!.IsDiscrete()) {
                    group = group.nextGroup
                    continue
                }
                a = group.triList
                while (a != null) {
                    vert = 0
                    while (vert < 3) {
                        a.hashVert[vert] = GetHashVert(a.v[vert].xyz)
                        vert++
                    }
                    a = a.next
                }
                group = group.nextGroup
            }
            areaNum++
        }

        // add all the func_static model vertexes to the hash buckets
        // optionally inline some of the func_static models
        if (dmap.dmapGlobals.entityNum == 0) {
            for (eNum in 1 until dmap.dmapGlobals.num_entities) {
                val entity = dmap.dmapGlobals.uEntities[eNum]
                val className = entity.mapEntity.epairs.GetString("classname")
                if (idStr.Icmp(className, "func_static") != 0) {
                    continue
                }
                val modelName = entity.mapEntity.epairs.GetString("model")
                if (modelName.isEmpty()) {
                    continue
                }
                if (!modelName.contains(".lwo") && !modelName.contains(".ase") && !modelName.contains(".ma")) {
                    continue
                }
                val model = ModelManager.renderModelManager.FindModel(modelName)!!

//			common.Printf( "adding T junction verts for %s.\n", entity.mapEntity.epairs.GetString( "name" ) );
                val axis = idMat3()
                // get the rotation matrix in either full form, or single angle form
                if (!entity.mapEntity.epairs.GetMatrix("rotation", "1 0 0 0 1 0 0 0 1", axis)) {
                    val angle = entity.mapEntity.epairs.GetFloat("angle")
                    if (angle != 0.0f) {
                        axis.set(idAngles(0.0f, angle, 0.0f).ToMat3())
                    } else {
                        axis.Identity()
                    }
                }
                val origin = idVec3(entity.mapEntity.epairs.GetVector("origin"))
                i = 0
                while (i < model.NumSurfaces()) {
                    val surface = model.Surface(i)
                    val tri = surface!!.geometry!!
                    val mapTri = mapTri_s()
                    //				memset( &mapTri, 0, sizeof( mapTri ) );
                    mapTri.material = surface.shader
                    // don't let discretes (autosprites, etc) merge together
                    if (mapTri.material!!.IsDiscrete()) {
                        mapTri.mergeGroup = surface
                    }
                    var j = 0
                    while (j < tri.numVerts) {
                        val v = idVec3(tri.verts!![j]!!.xyz.times(axis).plus(origin))
                        GetHashVert(v)
                        j += 3
                    }
                    i++
                }
            }
        }

        // now fix each area
        areaNum = 0
        while (areaNum < e.numAreas) {
            group = e.areas[areaNum].groups
            while (group != null) {

                // don't touch discrete surfaces
                if (group.material != null && group.material!!.IsDiscrete()) {
                    group = group.nextGroup
                    continue
                }
                var newList: mapTri_s? = null
                var tri = group.triList
                while (tri != null) {
                    val fixed = FixTriangleAgainstHash(tri)
                    newList = tritools.MergeTriLists(newList, fixed)
                    tri = tri.next
                }
                tritools.FreeTriList(group.triList)
                group.triList = newList
                group = group.nextGroup
            }
            areaNum++
        }

        // done
        FreeTJunctionHash()
    }

    class hashVert_s {
        var iv: IntArray = IntArray(3)
        var next: hashVert_s? = null
        val v: idVec3 = idVec3()

        companion object {
            fun memset(hashVerts: Array<Array<Array<hashVert_s?>>>) {
                for (a in 0 until HASH_BINS) {
                    for (b in 0 until HASH_BINS) {
                        for (c in 0 until HASH_BINS) {
                            hashVerts[a][b][c] = null
                        }
                    }
                }
            }
        }
    }
}
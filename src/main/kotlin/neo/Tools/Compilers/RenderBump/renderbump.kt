package neo.Tools.Compilers.RenderBump

import neo.Renderer.*
import neo.Renderer.Model.idRenderModel
import neo.Renderer.Model.modelSurface_s
import neo.Renderer.Model.srfTriangles_s
import neo.TempDump
import neo.TempDump.TODO_Exception
import neo.Tools.Compilers.DMap.dmap.Dmap_f
import neo.framework.CmdSystem.cmdFunction_t
import neo.framework.Common
import neo.idlib.BV.idBounds
import neo.idlib.CmdArgs
import neo.idlib.Text.Str.idStr
import neo.idlib.geometry.Winding.idWinding
import neo.idlib.idException
import neo.idlib.math.*
import neo.idlib.math.Matrix.idMat3
import neo.sys.win_glimp
import neo.sys.win_shared
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11
import java.nio.ByteBuffer
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sqrt

object renderbump {
    const val DEFAULT_TRACE_FRACTION = 0.05

    /*
     =================
     TraceToMeshFace

     Returns the distance from the point to the intersection, or DIST_NO_INTERSECTION
     =================
     */
    const val DIST_NO_INTERSECTION = -999999999.0f
    const val HASH_AXIS_BINS = 100
    const val INITIAL_TRI_TO_LINK_EXPANSION = 16 // can grow as needed
    const val MAX_LINKS_PER_BLOCK = 0x100000
    const val MAX_LINK_BLOCKS = 0x100

    /*

     render a normalmap tga file from an ase model for bump mapping

     To make ray-tracing into the high poly mesh efficient, we preconstruct
     a 3D hash table of the triangles that need to be tested for a given source
     point.

     This task is easier than a general ray tracing optimization, because we
     known that all of the triangles are going to be "near" the source point.

     TraceFraction determines the maximum distance in any direction that
     a trace will go.  It is expressed as a fraction of the largest axis of
     the bounding box, so it doesn't matter what units are used for modeling.


     */
    const val MAX_QPATH = 256
    const val RAY_STEPS = 100
    private const val SKIP_MIRRORS = false //TODO:set default value
    var oldWidth = 0
    var oldHeight = 0
    var rayNumber // for avoiding retests of bins and faces
            = 0
    var traceFraction = 0.0f

    /*
     ===============
     SaveWindow
     ===============
     */
    fun SaveWindow() {
        oldWidth = glConfig.vidWidth
        oldHeight = glConfig.vidHeight
    }

    /*
     ===============
     ResizeWindow
     ===============
     */
    fun ResizeWindow(width: Int, height: Int) {
        throw TODO_Exception()
    }

    /*
     ===============
     RestoreWindow
     ===============
     */
    fun RestoreWindow() {
        throw TODO_Exception()
    }

    /*
     ================
     OutlineNormalMap

     Puts a single pixel border around all non-empty pixels
     Does NOT copy the alpha channel, so it can be used as
     an alpha test map.
     ================
     */
    fun OutlineNormalMap(data: ByteBuffer, width: Int, height: Int, emptyR: Int, emptyG: Int, emptyB: Int) {
        var orig: ByteArray
        var i: Int
        var j: Int
        var k: Int
        var l: Int
        val normal = idVec3()
        var out: Int
        orig = ByteArray(width * height * 4) // Mem_Alloc(width * height * 4);
        //	memcpy( orig, data, width * height * 4 );
        System.arraycopy(orig, 0, data.array(), 0, width * height * 4)
        i = 0
        while (i < width) {
            j = 0
            while (j < height) {
                out = (j * width + i) * 4
                if (data.get(out + 0).toInt() != emptyR || data.get(out + 1).toInt() != emptyG || data.get(out + 2)
                        .toInt() != emptyB
                ) {
                    j++
                    continue
                }
                normal.set(vec3_origin)
                k = -1
                while (k < 2) {
                    l = -1
                    while (l < 2) {
                        var `in`: Int

//					in = orig + ( ((j+l)&(height-1))*width + ((i+k)&(width-1)) ) * 4;
                        `in` = ((j + l and height - 1) * width + (i + k and width - 1)) * 4
                        if (orig[`in` + 0].toInt() == emptyR && orig[`in` + 1].toInt() == emptyG && orig[`in` + 2].toInt() == emptyB) {
                            l++
                            continue
                        }
                        normal.plusAssign(0, (orig[`in` + 0] - 128).toFloat())
                        normal.plusAssign(1, (orig[`in` + 1] - 128).toFloat())
                        normal.plusAssign(2, (orig[`in` + 2] - 128).toFloat())
                        l++
                    }
                    k++
                }
                if (normal.Normalize() < 0.5f) {
                    j++
                    continue  // no valid samples
                }
                data.put(out + 0, ((128 + 127 * normal[0]).toInt().toByte()))
                data.put(out + 1, (128 + 127 * normal[1]).toInt().toByte())
                data.put(out + 2, (128 + 127 * normal[2]).toInt().toByte())
                j++
            }
            i++
        }
    }

    /*
     ================
     OutlineColorMap

     Puts a single pixel border around all non-empty pixels
     Does NOT copy the alpha channel, so it can be used as
     an alpha test map.
     ================
     */
    fun OutlineColorMap(data: ByteBuffer, width: Int, height: Int, emptyR: Int, emptyG: Int, emptyB: Int) {
        var orig: ByteArray
        var i: Int
        var j: Int
        var k: Int
        var l: Int
        val normal = idVec3()
        var out: Int
        orig = ByteArray(width * height * 4) // Mem_Alloc(width * height * 4);
        //	memcpy( orig, data, width * height * 4 );
        System.arraycopy(orig, 0, data, 0, width * height * 4)
        i = 0
        while (i < width) {
            j = 0
            while (j < height) {
                out = (j * width + i) * 4
                if (data.get(out + 0).toInt() != emptyR || data.get(out + 1).toInt() != emptyG || data.get(out + 2)
                        .toInt() != emptyB
                ) {
                    j++
                    continue
                }
                normal.set(vec3_origin)
                var count = 0
                k = -1
                while (k < 2) {
                    l = -1
                    while (l < 2) {
                        var `in`: Int
                        `in` = ((j + l and height - 1) * width + (i + k and width - 1)) * 4
                        if (orig[`in` + 0].toInt() == emptyR && orig[`in` + 1].toInt() == emptyG && orig[`in` + 2].toInt() == emptyB) {
                            l++
                            continue
                        }
                        normal.plusAssign(0, orig[`in` + 0].toFloat())
                        normal.plusAssign(1, orig[`in` + 1].toFloat())
                        normal.plusAssign(2, orig[`in` + 2].toFloat())
                        count++
                        l++
                    }
                    k++
                }
                if (0 == count) {
                    j++
                    continue
                }
                normal.timesAssign(1.0f / count)
                data.put(out + 0, normal[0].toInt().toByte())
                data.put(out + 1, normal[1].toInt().toByte())
                data.put(out + 2, normal[2].toInt().toByte())
                j++
            }
            i++
        }
    }

    /*
     ================
     FreeTriHash
     ================
     */
    fun FreeTriHash(hash: triHash_t) {
        for (i in 0 until hash.numLinkBlocks) {
            hash.linkBlocks[i] = null //Mem_Free(hash.linkBlocks[i]);
        }
        hash.clear() //Mem_Free(hash);
    }

    /*
     ================
     CreateTriHash
     ================
     */
    fun CreateTriHash(highMesh: srfTriangles_s): triHash_t {
        val hash: triHash_t
        var i: Int
        var j: Int
        var k: Int
        var l: Int
        val bounds = idBounds()
        val triBounds = idBounds()
        val iBounds = Array(2) { IntArray(3) }
        var maxLinks: Int
        var numLinks: Int
        hash = triHash_t() //Mem_Alloc(sizeof(hash));
        //	memset( hash, 0, sizeof( *hash ) );

        // find the bounding volume for the mesh
        bounds.Clear()
        i = 0
        while (i < highMesh.numVerts) {
            bounds.AddPoint(highMesh.verts!![i]!!.xyz)
            i++
        }
        hash.bounds.set(bounds)

        // divide each axis as needed
        i = 0
        while (i < 3) {
            hash.binSize[i] = (bounds[1, i] - bounds[0, i]) / HASH_AXIS_BINS
            if (hash.binSize[i] <= 0) {
                Common.common.FatalError(
                    "CreateTriHash: bad bounds: (%f %f %f) to (%f %f %f)",
                    bounds[0, 0], bounds[0, 1], bounds[0, 2],
                    bounds[1, 0], bounds[1, 1], bounds[1, 2]
                )
            }
            i++
        }

        // a -1 link number terminated the link chain
//        memset(hash.binLinks, -1, sizeof(hash.binLinks));
        for (A in hash.binLinks) {
            for (B in A) {
                for (C in B) {
                    C.rayNumber = -1
                    C.triLink = -1
                }
            }
        }
        numLinks = 0
        hash.linkBlocks[hash.numLinkBlocks] =
            arrayOfNulls(MAX_LINKS_PER_BLOCK) // Mem_Alloc(MAX_LINKS_PER_BLOCK * sizeof(triLink_t));
        hash.numLinkBlocks++
        maxLinks = hash.numLinkBlocks * MAX_LINKS_PER_BLOCK

        // for each triangle, place a triLink in each bin that might reference it
        i = 0
        while (i < highMesh.numIndexes) {

            // determine which hash bins the triangle will need to be in
            triBounds.Clear()
            j = 0
            while (j < 3) {
                triBounds.AddPoint(highMesh.verts!![highMesh.indexes!![i + j]]!!.xyz)
                j++
            }
            j = 0
            while (j < 3) {
                iBounds[0][j] = ((triBounds[0, j] - hash.bounds[0, j]) / hash.binSize[j]).toInt()
                iBounds[0][j] -= 0.001.toInt() // epsilon
                if (iBounds[0][j] < 0) {
                    iBounds[0][j] = 0
                } else if (iBounds[0][j] >= HASH_AXIS_BINS) {
                    iBounds[0][j] = HASH_AXIS_BINS - 1
                }
                iBounds[1][j] = ((triBounds[1, j] - hash.bounds[0, j]) / hash.binSize[j]).toInt()
                iBounds[0][j] += 0.001.toInt() // epsilon
                if (iBounds[1][j] < 0) {
                    iBounds[1][j] = 0
                } else if (iBounds[1][j] >= HASH_AXIS_BINS) {
                    iBounds[1][j] = HASH_AXIS_BINS - 1
                }
                j++
            }

            // add the links
            j = iBounds[0][0]
            while (j <= iBounds[1][0]) {
                k = iBounds[0][1]
                while (k <= iBounds[1][1]) {
                    l = iBounds[0][2]
                    while (l <= iBounds[1][2]) {
                        if (numLinks == maxLinks) {
                            hash.linkBlocks[hash.numLinkBlocks] =
                                arrayOfNulls(MAX_LINKS_PER_BLOCK) // Mem_Alloc(MAX_LINKS_PER_BLOCK * sizeof(triLink_t));
                            hash.numLinkBlocks++
                            maxLinks = hash.numLinkBlocks * MAX_LINKS_PER_BLOCK
                        }
                        val link =
                            hash.linkBlocks[numLinks / MAX_LINKS_PER_BLOCK]!![numLinks % MAX_LINKS_PER_BLOCK]!! //TODO:pointer
                        link.faceNum = i / 3
                        link.nextLink = hash.binLinks[j][k][l].triLink
                        hash.binLinks[j][k][l].triLink = numLinks
                        numLinks++
                        l++
                    }
                    k++
                }
                j++
            }
            i += 3
        }
        Common.common.Printf("%d triangles made %d links\n", highMesh.numIndexes / 3, numLinks)
        return hash
    }

    fun TraceToMeshFace(
        highMesh: srfTriangles_s, faceNum: Int, minDist: Float, maxDist: Float,
        point: idVec3, normal: idVec3, sampledNormal: idVec3, sampledColor: ByteArray /*[4]*/
    ): Float {
        var j: Int
        var dist: Float
        val v: Array<idVec3> = idVec3.generateArray(3)
        val plane = idPlane()
        val edge = idVec3()
        var d: Float
        val dir: Array<idVec3> = idVec3.generateArray(3)
        val baseArea: Float
        val bary = FloatArray(3)
        val testVert = idVec3()
        v[0].set(highMesh.verts!![highMesh.indexes!![faceNum * 3 + 0]]!!.xyz)
        v[1].set(highMesh.verts!![highMesh.indexes!![faceNum * 3 + 1]]!!.xyz)
        v[2].set(highMesh.verts!![highMesh.indexes!![faceNum * 3 + 2]]!!.xyz)
        plane.set(highMesh.facePlanes!![faceNum]!!)

        // only test against planes facing the same direction as our normal
        d = plane.Normal().times(normal)
        if (d <= 0.0001) {
            return DIST_NO_INTERSECTION
        }

        // find the point of impact on the plane
        dist = plane.Distance(point)
        dist /= -d
        testVert.set(point.plus(normal.times(dist)))

        // if this would be beyond our requested trace distance,
        // don't even check it
        if (dist > maxDist) {
            return DIST_NO_INTERSECTION
        }
        if (dist < minDist) {
            return DIST_NO_INTERSECTION
        }

        // if normal is inside all edge planes, this face is hit
        VectorSubtract(v[0], point, dir[0])
        VectorSubtract(v[1], point, dir[1])
        edge.set(dir[0].Cross(dir[1]))
        d = DotProduct(normal, edge)
        if (d > 0.0f) {
            return DIST_NO_INTERSECTION
        }
        VectorSubtract(v[2], point, dir[2])
        edge.set(dir[1].Cross(dir[2]))
        d = DotProduct(normal, edge)
        if (d > 0.0f) {
            return DIST_NO_INTERSECTION
        }
        edge.set(dir[2].Cross(dir[0]))
        d = DotProduct(normal, edge)
        if (d > 0.0f) {
            return DIST_NO_INTERSECTION
        }

        // calculate barycentric coordinates of the impact point
        // on the high poly triangle
        bary[0] = idWinding.TriangleArea(testVert, v[1], v[2])
        bary[1] = idWinding.TriangleArea(v[0], testVert, v[2])
        bary[2] = idWinding.TriangleArea(v[0], v[1], testVert)
        baseArea = idWinding.TriangleArea(v[0], v[1], v[2])
        bary[0] /= baseArea
        bary[1] /= baseArea
        bary[2] /= baseArea
        if (bary[0] + bary[1] + bary[2] > 1.1) {
            bary[0] = bary[0]
            return DIST_NO_INTERSECTION
        }

        // triangularly interpolate the normals to the sample point
        sampledNormal.set(vec3_origin)
        j = 0
        while (j < 3) {
            sampledNormal.plusAssign(highMesh.verts!![highMesh.indexes!![faceNum * 3 + j]]!!.normal.times(bary[j]))
            j++
        }
        sampledNormal.Normalize()
        sampledColor[3] = 0
        sampledColor[2] = sampledColor[3]
        sampledColor[1] = sampledColor[2]
        sampledColor[0] = sampledColor[1]
        for (i in 0..3) {
            var color = 0.0f
            j = 0
            while (j < 3) {
                color += bary[j] * highMesh.verts!![highMesh.indexes!![faceNum * 3 + j]]!!.color[i]
                j++
            }
            sampledColor[i] = color.toInt().toByte()
        }
        return dist
    }

    /*
     ================
     SampleHighMesh

     Find the best surface normal in the high poly mesh
     for a ray coming from the surface of the low poly mesh

     Returns false if the trace doesn't hit anything
     ================
     */
    fun SampleHighMesh(
        rb: renderBump_t,
        point: idVec3,
        direction: idVec3,
        sampledNormal: idVec3,
        sampledColor: ByteArray /*[4]*/
    ): Boolean {
        val p = idVec3()
        var bl: binLink_t
        var linkNum: Int
        var faceNum: Int
        var dist: Float
        var bestDist: Float
        val block = IntArray(3)
        val maxDist: Float
        var c_hits: Int
        var i: Int
        val normal = idVec3()

        // we allow non-normalized directions on input
        normal.set(direction)
        normal.Normalize()

        // increment our uniqueness counter (FIXME: make thread safe?)
        rayNumber++

        // the max distance will be the traceFrac times the longest axis of the high poly model
        bestDist = -rb.traceDist
        maxDist = rb.traceDist
        sampledNormal.set(vec3_origin)
        c_hits = 0

        // this is a pretty damn lazy way to walk through a 3D grid, and has a (very slight)
        // chance of missing a triangle in a corner crossing case
        i = 0
        while (i < RAY_STEPS) {
            p.set(
                point.minus(
                    rb.hash.bounds[0]
                        .plus(normal.times(-1.0f + 2.0f * i / RAY_STEPS).times(rb.traceDist))
                )
            ) //TODO:check if downcasting from doubles to floats has any effect
            block[0] = floor((p[0] / rb.hash.binSize[0])).toInt()
            block[1] = floor((p[1] / rb.hash.binSize[1])).toInt()
            block[2] = floor((p[2] / rb.hash.binSize[2])).toInt()
            if (block[0] < 0 || block[0] >= HASH_AXIS_BINS) {
                i++
                continue
            }
            if (block[1] < 0 || block[1] >= HASH_AXIS_BINS) {
                i++
                continue
            }
            if (block[2] < 0 || block[2] >= HASH_AXIS_BINS) {
                i++
                continue
            }

            // FIXME: casting away const
            bl = rb.hash.binLinks[block[0]][block[1]][block[2]]
            if (bl.rayNumber == rayNumber) {
                i++
                continue  // already tested this block
            }
            bl.rayNumber = rayNumber
            linkNum = bl.triLink
            var link: triLink_t
            while (linkNum != -1) {
                link = rb.hash.linkBlocks[linkNum / MAX_LINKS_PER_BLOCK]!![linkNum % MAX_LINKS_PER_BLOCK]!!
                faceNum = link.faceNum
                dist = TraceToMeshFace(
                    rb.mesh, faceNum,
                    bestDist, maxDist, point, normal, sampledNormal, sampledColor
                )
                if (dist == DIST_NO_INTERSECTION) {
                    linkNum = link.nextLink
                    continue
                }
                c_hits++
                // continue looking for a better match
                bestDist = dist
                linkNum = link.nextLink
            }
            i++
        }
        return bestDist > -rb.traceDist
    }

    /*
     =============
     TriTextureArea

     This may be negatove
     =============
     */
    fun TriTextureArea(a: FloatArray /*[2]*/, b: FloatArray /*[2]*/, c: FloatArray /*[2]*/): Float {
        val d1 = idVec3()
        val d2 = idVec3()
        val cross = idVec3()
        val area: Float
        d1[0] = b[0] - a[0]
        d1[1] = b[1] - a[1]
        d1[2] = 0.0f
        d2[0] = c[0] - a[0]
        d2[1] = c[1] - a[1]
        d2[2] = 0.0f
        cross.set(d1.Cross(d2))
        area = 0.5f * cross.Length()
        return if (cross[2] < 0) {
            -area
        } else {
            area
        }
    }

    /*
     ================
     RasterizeTriangle

     It is ok for the texcoords to wrap around, the rasterization
     will deal with it properly.
     ================
     */
    fun RasterizeTriangle(
        lowMesh: srfTriangles_s,
        lowMeshNormals: Array<idVec3>,
        lowFaceNum: Int,
        rbs: Array<renderBump_t>
    ) {
        var i: Int
        var j: Int
        var k: Int
        var q: Int
        val bounds = Array(2) { FloatArray(2) }
        val ibounds = Array(2) { FloatArray(2) }
        val verts = Array(3) { FloatArray(2) }
        val testVert = FloatArray(2)
        val bary = FloatArray(3)
        var localDest: ByteBuffer
        var globalDest: ByteBuffer
        var colorDest: ByteBuffer
        val edge = Array(3) { FloatArray(3) }
        val sampledNormal = idVec3()
        val sampledColor = ByteArray(4)
        val point = idVec3()
        val normal = idVec3()
        val traceNormal = idVec3()
        val tangents: Array<idVec3> = idVec3.generateArray(2)
        var baseArea: Float
        var totalArea: Float
        var r: Int
        var g: Int
        var b: Int
        val localNormal = idVec3()

        // this is a brain-dead rasterizer, but compared to the ray trace,
        // nothing we do here is going to matter performance-wise
        // adjust for resolution and texel centers
        verts[0][0] = lowMesh.verts!![lowMesh.indexes!![lowFaceNum * 3 + 0]]!!.st[0] * rbs[0].width - 0.5f
        verts[1][0] = lowMesh.verts!![lowMesh.indexes!![lowFaceNum * 3 + 1]]!!.st[0] * rbs[0].width - 0.5f
        verts[2][0] = lowMesh.verts!![lowMesh.indexes!![lowFaceNum * 3 + 2]]!!.st[0] * rbs[0].width - 0.5f
        verts[0][1] = lowMesh.verts!![lowMesh.indexes!![lowFaceNum * 3 + 0]]!!.st[1] * rbs[0].width - 0.5f
        verts[1][1] = lowMesh.verts!![lowMesh.indexes!![lowFaceNum * 3 + 1]]!!.st[1] * rbs[0].width - 0.5f
        verts[2][1] = lowMesh.verts!![lowMesh.indexes!![lowFaceNum * 3 + 2]]!!.st[1] * rbs[0].width - 0.5f

        // find the texcoord bounding box
        bounds[0][0] = 99999.0f
        bounds[0][1] = 99999.0f
        bounds[1][0] = -99999.0f
        bounds[1][1] = -99999.0f
        i = 0
        while (i < 2) {
            j = 0
            while (j < 3) {
                if (verts[j][i] < bounds[0][i]) {
                    bounds[0][i] = verts[j][i]
                }
                if (verts[j][i] > bounds[1][i]) {
                    bounds[1][i] = verts[j][i]
                }
                j++
            }
            i++
        }

        // we intentionally rasterize somewhat outside the triangles, so
        // the bilerp support texels (which may be anti-aliased down)
        // are not just duplications of what is on the interior
        val edgeOverlap = 4.0f
        ibounds[0][0] = floor((bounds[0][0] - edgeOverlap))
        ibounds[1][0] = ceil((bounds[1][0] + edgeOverlap))
        ibounds[0][1] = floor((bounds[0][1] - edgeOverlap))
        ibounds[1][1] = ceil((bounds[1][1] + edgeOverlap))

        // calculate edge vectors
        i = 0
        while (i < 3) {
            var v1: FloatArray
            var v2: FloatArray
            v1 = verts[i]
            v2 = verts[(i + 1) % 3]
            edge[i][0] = v2[1] - v1[1]
            edge[i][1] = v1[0] - v2[0]
            val len =
                sqrt((edge[i][0] * edge[i][0] + edge[i][1] * edge[i][1]))
            edge[i][0] /= len
            edge[i][1] /= len
            edge[i][2] = -(v1[0] * edge[i][0] + v1[1] * edge[i][1])
            i++
        }

        // itterate over the bounding box, testing against edge vectors
        i = ibounds[0][1].toInt()
        q = 0
        while (i < ibounds[1][1]) {
            j = ibounds[0][0].toInt()
            while (j < ibounds[1][0]) {
                val dists = FloatArray(3)
                val rb =
                    rbs[q] //TODO: triple check the 'q' value against 'k', and make sure we don't go out of bounds.
                k = ((i and rb.height - 1) * rb.width + (j and rb.width - 1)) * 4
                colorDest = rb.colorPic!! //[k];
                localDest = rb.localPic //[k];
                globalDest = rb.globalPic //[k];

//                float[] edgeDistance = rb.edgeDistances[k / 4];
                if (SKIP_MIRRORS) {
                    // if this texel has already been filled by a true interior pixel, don't overwrite it
                    if (rb.edgeDistances[0 + k / 4] == 0.0f) {
                        j++
                        q++
                        continue
                    }
                }

                // check against the three edges to see if the pixel is inside the triangle
                k = 0
                while (k < 3) {
                    var v: Float
                    v = i * edge[k][1] + j * edge[k][0] + edge[k][2]
                    dists[k] = v
                    k++
                }

                // the edge polarities might be either way
                if (!(dists[0] >= -edgeOverlap && dists[1] >= -edgeOverlap && dists[2] >= -edgeOverlap
                            || dists[0] <= edgeOverlap && dists[1] <= edgeOverlap && dists[2] <= edgeOverlap)
                ) {
                    j++
                    q++
                    continue
                }
                var edgeTexel: Boolean
                if (dists[0] >= 0 && dists[1] >= 0 && dists[2] >= 0
                    || dists[0] <= 0 && dists[1] <= 0 && dists[2] <= 0
                ) {
                    edgeTexel = false
                } else {
                    edgeTexel = true
                    if (SKIP_MIRRORS) {
                        // if this texel has already been filled by another edge pixel, don't overwrite it
                        if (rb.edgeDistances[1 + k / 4] == 1.0f) {
                            j++
                            q++
                            continue
                        }
                    }
                }

                // calculate the barycentric coordinates in the triangle for this sample
                testVert[0] = j.toFloat()
                testVert[1] = i.toFloat()
                baseArea = TriTextureArea(verts[0], verts[1], verts[2])
                bary[0] = TriTextureArea(testVert, verts[1], verts[2]) / baseArea
                bary[1] = TriTextureArea(verts[0], testVert, verts[2]) / baseArea
                bary[2] = TriTextureArea(verts[0], verts[1], testVert) / baseArea
                totalArea = bary[0] + bary[1] + bary[2]
                if (totalArea < 0.99 || totalArea > 1.01) {
                    j++
                    q++
                    continue  // should never happen
                }

                // calculate the interpolated xyz, normal, and tangents of this sample
                point.set(vec3_origin)
                traceNormal.set(vec3_origin)
                normal.set(vec3_origin)
                tangents[0].set(vec3_origin)
                tangents[1].set(vec3_origin)
                k = 0
                while (k < 3) {
                    var index: Int
                    index = lowMesh.indexes!![lowFaceNum * 3 + k]
                    point.plusAssign(lowMesh.verts!![index]!!.xyz.times(bary[k]))

                    // traceNormal will differ from normal if the surface uses unsmoothedTangents
                    traceNormal.plusAssign(lowMeshNormals[index].times(bary[k]))
                    normal.plusAssign(lowMesh.verts!![index]!!.normal.times(bary[k]))
                    tangents[0].plusAssign(lowMesh.verts!![index]!!.tangents[0].times(bary[k]))
                    tangents[1].plusAssign(lowMesh.verts!![index]!!.tangents[1].times(bary[k]))
                    k++
                }

//#if 0
//			// this doesn't seem to make much difference
//			// an argument can be made that these should not be normalized, because the interpolation
//			// of the light position at rasterization time will be linear, not spherical
//			normal.Normalize();
//			tangents[0].Normalize();
//			tangents[1].Normalize();
//}
//
//			// find the best triangle in the high poly model for this
//			// sampledNormal will  normalized
//			if ( !SampleHighMesh( rb, point, traceNormal, sampledNormal, sampledColor ) ) {
//#if 0
//				// put bright red where all traces missed for debugging.
//				// for production use, it is better to leave it blank so
//				// the outlining fills it in
//				globalDest[0] = 255;
//				globalDest[1] = 0;
//				globalDest[2] = 0;
//				globalDest[3] = 255;
//
//				localDest[0] = 255;
//				localDest[1] = 0;
//				localDest[2] = 0;
//				localDest[3] = 255;
//}
//				continue;
//			}
                // mark whether this is an interior or edge texel
                rb.edgeDistances[0 + k / 4] = if (edgeTexel) 1.0f else 0.0f

                // fill the object space normal map spot
                r = (128 + 127 * sampledNormal[0]).toInt()
                g = (128 + 127 * sampledNormal[1]).toInt()
                b = (128 + 127 * sampledNormal[2]).toInt()
                globalDest.put(0, r.toByte())
                globalDest.put(1, g.toByte())
                globalDest.put(2, b.toByte())
                globalDest.put(3, 255.toByte())

                // transform to local tangent space
                val mat = idMat3(tangents[0], tangents[1], normal)
                mat.InverseSelf()
                localNormal.set(mat.times(sampledNormal))
                localNormal.Normalize()
                r = (128 + 127 * localNormal[0]).toInt()
                g = (128 + 127 * localNormal[1]).toInt()
                b = (128 + 127 * localNormal[2]).toInt()
                localDest.put(0, r.toByte())
                localDest.put(1, g.toByte())
                localDest.put(2, b.toByte())
                localDest.put(3, 255.toByte())
                colorDest.put(0, sampledColor[0])
                colorDest.put(1, sampledColor[1])
                colorDest.put(2, sampledColor[2])
                colorDest.put(3, sampledColor[3])
                j++
                q++
            }
            i++
        }
    }

    /*
     ================
     CombineModelSurfaces

     Frees the model and returns a new model with all triangles combined
     into one surface
     ================
     */
    fun CombineModelSurfaces(model: idRenderModel): idRenderModel {
        var totalVerts: Int
        var totalIndexes: Int
        var numIndexes: Int
        var numVerts: Int
        var i: Int
        var j: Int
        totalVerts = 0
        totalIndexes = 0
        i = 0
        while (i < model.NumSurfaces()) {
            val surf = model.Surface(i)
            totalVerts += surf!!.geometry!!.numVerts
            totalIndexes += surf.geometry!!.numIndexes
            i++
        }
        val newTri = R_AllocStaticTriSurf()
        R_AllocStaticTriSurfVerts(newTri, totalVerts)
        R_AllocStaticTriSurfIndexes(newTri, totalIndexes)
        newTri.numVerts = totalVerts
        newTri.numIndexes = totalIndexes
        newTri.bounds.Clear()
        val verts = newTri.verts
        val   /*glIndex_t*/indexes = newTri.indexes
        numIndexes = 0
        numVerts = 0
        i = 0
        while (i < model.NumSurfaces()) {
            val surf = model.Surface(i)
            val tri = surf!!.geometry!!

//            memcpy(verts + numVerts, tri.verts, tri.numVerts * sizeof(tri.verts[0]));
            for (k in 0 until tri.numVerts) {
                verts!![numVerts + k]!!.set(tri.verts!![k]!!)
            }
            j = 0
            while (j < tri.numIndexes) {
                indexes!![numIndexes + j] = numVerts + tri.indexes!![j]
                j++
            }
            newTri.bounds.AddBounds(tri.bounds)
            numIndexes += tri.numIndexes
            numVerts += tri.numVerts
            i++
        }
        val surf = modelSurface_s()
        surf.id = 0
        surf.geometry = newTri
        surf.shader = tr.defaultMaterial
        val newModel = ModelManager.renderModelManager.AllocModel()
        newModel.AddSurface(surf)
        ModelManager.renderModelManager.FreeModel(model)
        return newModel
    }

    /*
     ==============
     RenderBumpTriangles

     ==============
     */
    fun RenderBumpTriangles(lowMesh: srfTriangles_s, rb: renderBump_t) {
        throw TODO_Exception()
        //        int i, j;
//
//        RB_SetGL2D();
//
//        qglDisable(GL_CULL_FACE);
//
//        qglColor3f(1, 1, 1);
//
//        qglMatrixMode(GL_PROJECTION);
//        qglLoadIdentity();
//        qglOrtho(0, 1, 1, 0, -1, 1);
//        qglDisable(GL_BLEND);
//        qglMatrixMode(GL_MODELVIEW);
//        qglLoadIdentity();
//
//        qglDisable(GL_DEPTH_TEST);
//
//        qglClearColor(1, 0, 0, 1);
//        qglClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
//
//        qglColor3f(1, 1, 1);
//
//        // create smoothed normals for the surface, which might be
//        // different than the normals at the vertexes if the
//        // surface uses unsmoothedNormals, which only takes the
//        // normal from a single triangle.  We need properly smoothed
//        // normals to make sure that the traces always go off normal
//        // to the true surface.
//        idVec3[] lowMeshNormals = new idVec3[lowMesh.numVerts];// Mem_ClearedAlloc(lowMesh.numVerts /* sizeof( lowMeshNormals )*/);
//        R_DeriveFacePlanes(lowMesh);
//        R_CreateSilIndexes(lowMesh);	// recreate, merging the mirrored verts back together
//        idPlane plane = lowMesh.facePlanes[0];
//        int p;
//        for (i = 0, p = 0; i < lowMesh.numIndexes; i += 3, plane = lowMesh.facePlanes[++p]) {
//            for (j = 0; j < 3; j++) {
//                int index;
//
//                index = lowMesh.silIndexes[i + j];
//                lowMeshNormals[index].oPluSet(plane.Normal());
//            }
//        }
//        // normalize and replicate from silIndexes to all indexes
//        for (i = 0; i < lowMesh.numIndexes; i++) {
//            lowMeshNormals[lowMesh.indexes!![i]] = lowMeshNormals[lowMesh.silIndexes[i]];//TODO: create shuffle function that moves
//            lowMeshNormals[lowMesh.indexes!![i]].Normalize();
//        }
//
//        // rasterize each low poly face
//        for (j = 0; j < lowMesh.numIndexes; j += 3) {
//            // pump the event loop so the window can be dragged around
//            Sys_GenerateEvents();
//
//            RasterizeTriangle(lowMesh, lowMeshNormals, j / 3, rb);
//
//            qglClearColor(1, 0, 0, 1);
//            qglClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
//            qglRasterPos2f(0, 1);
//            qglPixelZoom(glConfig.vidWidth / (float) rb.width, glConfig.vidHeight / (float) rb.height);
//            qglDrawPixels(rb.width, rb.height, GL_RGBA, GL_UNSIGNED_BYTE, rb.localPic);
//            qglPixelZoom(1, 1);
//            qglFlush();
//            GLimp_SwapBuffers();
//        }
//
//        lowMeshNormals = null;//Mem_Free(lowMeshNormals);
    }

    /*
     ==============
     WriteRenderBump

     ==============
     */
    fun WriteRenderBump(rb: renderBump_t, outLinePixels: Int) {
        var width: Int
        var height: Int
        var i: Int
        val filename: idStr

//        renderModelManager.FreeModel(rb.highModel);
        rb.highModel = null
        FreeTriHash(rb.hash)
        width = rb.width
        height = rb.height

//#if 0
//	// save the non-outlined version
//	filename = source;
//	filename.setFileExtension();
//	filename.append( "_nooutline.tga" );
//	common.Printf( "writing %s\n", filename.c_str() );
//	WriteTGA( filename, globalPic, width, height );
//}
        // outline the image several times to help bilinear filtering across disconnected
        // edges, and mip-mapping
        i = 0
        while (i < outLinePixels) {
            OutlineNormalMap(rb.localPic, width, height, 128, 128, 128)
            OutlineNormalMap(rb.globalPic, width, height, 128, 128, 128)
            OutlineColorMap(rb.colorPic!!, width, height, 128, 128, 128)
            i++
        }

        // filter down if we are anti-aliasing
        i = 0
        while (i < rb.antiAlias) {
            var old: ByteBuffer
            old = rb.localPic
            rb.localPic = Image_process.R_MipMap(rb.localPic, width, height, false)
            //Mem_Free(old);
            old = rb.globalPic
            rb.globalPic = Image_process.R_MipMap(rb.globalPic, width, height, false)
            //Mem_Free(old);
            old = rb.colorPic!!
            rb.colorPic = Image_process.R_MipMap(rb.colorPic!!, width, height, false)
            width = width shr 1
            height = height shr 1
            i++
        }

        // write out the local map
        filename = idStr(rb.outputName)
        filename.SetFileExtension(".tga")
        Common.common.Printf("writing %s (%d,%d)\n", filename, width, height)
        Image_files.R_WriteTGA(filename, rb.localPic, width, height)
        if (rb.saveGlobalMap) {
            filename.set(rb.outputName)
            filename.StripFileExtension()
            filename.Append("_global.tga")
            Common.common.Printf("writing %s (%d,%d)\n", filename, width, height)
            Image_files.R_WriteTGA(filename, rb.globalPic, width, height)
        }
        if (rb.saveColorMap) {
            filename.set(rb.outputName)
            filename.StripFileExtension()
            filename.Append("_color.tga")
            Common.common.Printf("writing %s (%d,%d)\n", filename, width, height)
            Image_files.R_WriteTGA(filename, rb.colorPic!!, width, height)
        }
//        rb.localPic = null //Mem_Free(rb.localPic);
//        rb.globalPic = null //Mem_Free(rb.globalPic);
//        rb.colorPic = null //Mem_Free(rb.colorPic);
//        rb.edgeDistances = null //Mem_Free(rb.edgeDistances);
    }

    /*
     ===============
     InitRenderBump
     ===============
     */
    fun InitRenderBump(rb: renderBump_t) {
        val mesh: srfTriangles_s
        val bounds: idBounds
        var i: Int
        val c: Int

        // load the ase file
        Common.common.Printf("loading %s...\n", rb.highName)
        rb.highModel = ModelManager.renderModelManager.AllocModel()
        rb.highModel!!.PartialInitFromFile(rb.highName.toString())
        if (null == rb.highModel) {
            Common.common.Error("failed to load %s", rb.highName)
        }

        // combine the high poly model into a single polyset
        if (rb.highModel!!.NumSurfaces() != 1) {
            rb.highModel = CombineModelSurfaces(rb.highModel!!)
        }
        val surf = rb.highModel!!.Surface(0)
        mesh = surf!!.geometry!!
        rb.mesh = mesh
        R_DeriveFacePlanes(mesh)

        // create a face hash table to accelerate the tracing
        rb.hash = CreateTriHash(mesh)

        // bound the entire file
        R_BoundTriSurf(mesh)
        bounds = mesh.bounds

        // the traceDist will be the traceFrac times the larges bounds axis
        rb.traceDist = 0.0f
        i = 0
        while (i < 3) {
            var d: Float
            d = rb.traceFrac * (bounds[1, i] - bounds[0, i])
            if (d > rb.traceDist) {
                rb.traceDist = d
            }
            i++
        }
        Common.common.Printf("trace fraction %4.2f = %6.2f model units\n", rb.traceFrac, rb.traceDist)
        c = rb.width * rb.height * 4

        // local normal map
        rb.localPic = ByteBuffer.allocate(c) // Mem_Alloc(c);

        // global (object space, not surface space) normal map
        rb.globalPic = ByteBuffer.allocate(c) // Mem_Alloc(c);

        // color pic for artist reference
        rb.colorPic = ByteBuffer.allocate(c) // Mem_Alloc(c);

        // edgeDistance for marking outside-the-triangle traces
        rb.edgeDistances = FloatArray(c) // Mem_Alloc(c);
        i = 0
        while (i < c) {
            rb.localPic.put(i + 0, 128.toByte())
            rb.localPic.put(i + 1, 128.toByte())
            rb.localPic.put(i + 2, 128.toByte())
            rb.localPic.put(i + 3, 0.toByte()) // the artists use this for masking traced pixels sometimes
            rb.globalPic.put(i + 0, 128.toByte())
            rb.globalPic.put(i + 1, 128.toByte())
            rb.globalPic.put(i + 2, 128.toByte())
            rb.globalPic.put(i + 3, 0.toByte())
            rb.colorPic!!.put(i + 0, 128.toByte())
            rb.colorPic!!.put(i + 1, 128.toByte())
            rb.colorPic!!.put(i + 2, 128.toByte())
            rb.colorPic!!.put(i + 3, 0.toByte())
            rb.edgeDistances[i / 4] = -1.0f // not traced yet
            i += 4
        }
    }

    class triLink_t {
        var faceNum = 0
        var nextLink = 0
    }

    class binLink_t {
        var rayNumber // don't need to test again if still on same ray
                = 0
        var triLink = 0
    }

    class triHash_t {
        var binLinks: Array<Array<Array<binLink_t>>> =
            Array(HASH_AXIS_BINS) { Array(HASH_AXIS_BINS) { Array(HASH_AXIS_BINS) { binLink_t() } } }
        var binSize: FloatArray = FloatArray(3)
        val bounds: idBounds = idBounds()
        var linkBlocks: Array<Array<triLink_t?>?> = arrayOfNulls(MAX_LINK_BLOCKS)
        var numLinkBlocks = 0
        fun clear() {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }
    }

    class renderBump_t {
        var antiAlias = 0
        var colorPic: ByteBuffer? = null
        var edgeDistances // starts out -1 for untraced, for each texel, 0 = true interior, >0 = off-edge rasterization
                : FloatArray = FloatArray(0)
        lateinit var globalPic: ByteBuffer
        lateinit var hash: triHash_t
        var highModel: idRenderModel? = null
        var highName: CharArray = CharArray(MAX_QPATH)
        lateinit var localPic: ByteBuffer
        lateinit var mesh // high poly mesh
                : srfTriangles_s
        var outline = 0
        var outputName: CharArray = CharArray(MAX_QPATH)
        var saveColorMap = false
        var saveGlobalMap = false
        var traceDist = 0.0f
        var traceFrac = 0.0f
        var width = 0
        var height = 0
    }

    /*
     ==============
     RenderBump_f

     ==============
     */
    class RenderBump_f : cmdFunction_t() {
        @Throws(idException::class)
        override fun run(args: CmdArgs.idCmdArgs?) {
            val lowPoly: idRenderModel?
            val source: String
            var i: Int
            var j: Int
            var cmdLine: String
            var numRenderBumps: Int
            val renderBumps: Array<renderBump_t>
            var rb = renderBump_t()
            var opt = renderBump_t()
            val startTime: Int
            val endTime: Int

            // update the screen as we print
            Common.common.SetRefreshOnPrint(true)

            // there should be a single parameter, the filename for a game loadable low-poly model
            if (args!!.Argc() != 2) {
                Common.common.Error("Usage: renderbump <lowPolyModel>")
            }
            Common.common.Printf("----- Renderbump %s -----\n", args.Argv(1))
            startTime = win_shared.Sys_Milliseconds()

            // get the lowPoly model
            source = args.Argv(1)
            lowPoly = ModelManager.renderModelManager.CheckModel(source)
            if (null == lowPoly) {
                Common.common.Error("Can't load model %s", source)
                return
            }

//        renderBumps = (renderBump_t) R_StaticAlloc(lowPoly.NumSurfaces() * sizeof(renderBumps));
            renderBumps = Array(lowPoly.NumSurfaces()) { renderBump_t() }
            numRenderBumps = 0
            i = 0
            while (i < lowPoly.NumSurfaces()) {
                val ms = lowPoly.Surface(i)

                // default options
//            memset(opt, 0, sizeof(opt));
                opt = renderBump_t()
                opt.width = 512
                opt.height = 512
                opt.antiAlias = 1
                opt.outline = 8
                opt.traceFrac = 0.05f

                // parse the renderbump parameters for this surface
                cmdLine = ms!!.shader!!.GetRenderBump()
                Common.common.Printf(
                    "surface %d, shader %s\nrenderBump = %s ", i,
                    ms.shader!!.GetName(), cmdLine
                )
                if (ms.geometry == null) {
                    Common.common.Printf("(no geometry)\n")
                    i++
                    continue
                }
                val localArgs = CmdArgs.idCmdArgs()
                localArgs.TokenizeString(cmdLine, false)
                if (localArgs.Argc() < 2) {
                    Common.common.Printf("(no action)\n")
                    i++
                    continue
                }
                Common.common.Printf("(rendering)\n")
                j = 0
                while (j < localArgs.Argc() - 2) {
                    var s: String
                    s = localArgs.Argv(j)
                    if (s[0] == '-') {
                        j++
                        s = localArgs.Argv(j)
                        if (s[0] == '\u0000') {
                            j++
                            continue
                        }
                    }
                    if (0 == idStr.Icmp(s, "size")) {
                        if (j + 2 >= localArgs.Argc()) {
                            j = localArgs.Argc()
                            break
                        }
                        opt.width = localArgs.Argv(j + 1).toInt()
                        opt.height = localArgs.Argv(j + 2).toInt()
                        j += 2
                    } else if (0 == idStr.Icmp(s, "trace")) {
                        opt.traceFrac = localArgs.Argv(j + 1).toFloat()
                        j += 1
                    } else if (0 == idStr.Icmp(s, "globalMap")) {
                        opt.saveGlobalMap = true
                    } else if (0 == idStr.Icmp(s, "colorMap")) {
                        opt.saveColorMap = true
                    } else if (0 == idStr.Icmp(s, "outline")) {
                        opt.outline = localArgs.Argv(j + 1).toInt()
                        j += 1
                    } else if (0 == idStr.Icmp(s, "aa")) {
                        opt.antiAlias = localArgs.Argv(j + 1).toInt()
                        j += 1
                    } else {
                        Common.common.Printf("WARNING: Unknown option \"%s\"\n", s)
                        break
                    }
                    j++
                }
                if (j != localArgs.Argc() - 2) {
                    Common.common.Error("usage: renderBump [-size width height] [-aa <1-2>] [globalMap] [colorMap] [-trace <0.01 - 1.0f>] normalMapImageFile highPolyAseFile")
                }
                idStr.Copynz(opt.outputName, localArgs.Argv(j), localArgs.Argv(j).length)
                idStr.Copynz(opt.highName, localArgs.Argv(j + 1), localArgs.Argv(j + 1).length)

                // adjust size for anti-aliasing
                opt.width = opt.width shl opt.antiAlias
                opt.height = opt.height shl opt.antiAlias

                // see if we already have a renderbump going for another surface that this should use
                j = 0
                while (j < numRenderBumps) {
                    rb = renderBumps[j]
                    if (idStr.Icmp(rb.outputName, opt.outputName) != 0) {
                        j++
                        continue
                    }
                    // all the other parameters must match, or it is an error
                    if (idStr.Icmp(
                            rb.highName,
                            opt.highName
                        ) != 0 || rb.width != opt.width || rb.height != opt.height || rb.antiAlias != opt.antiAlias || rb.traceFrac != opt.traceFrac
                    ) {
                        Common.common.Error("mismatched renderbump parameters on image %s", rb.outputName)
                        j++
                        continue
                    }

                    // saveGlobalMap will be a sticky option
                    rb.saveGlobalMap = rb.saveGlobalMap or opt.saveGlobalMap
                    break
                    j++
                }

                // create a new renderbump if needed
                if (j == numRenderBumps) {
                    numRenderBumps++
                    renderBumps[j] = opt
                    rb = renderBumps[j]
                    InitRenderBump(rb)
                }

                // render the triangles for this surface
                RenderBumpTriangles(ms.geometry!!, rb)
                i++
            }

            //
            // anti-alias and write out all renderbumps that we have completed
            //
            i = 0
            while (i < numRenderBumps) {
                WriteRenderBump(renderBumps[i], opt.outline shl opt.antiAlias)
                i++
            }
            //
//            R_StaticFree(renderBumps);
            endTime = win_shared.Sys_Milliseconds()
            Common.common.Printf("%5.2f seconds for renderBump\n", (endTime - startTime) / 1000.0f)
            Common.common.Printf("---------- RenderBump Completed ----------\n")

            // stop updating the screen as we print
            Common.common.SetRefreshOnPrint(false)
        }

        companion object {
            private val instance: cmdFunction_t = Dmap_f()
            fun getInstance(): cmdFunction_t {
                return instance
            }
        }
    }

    /*
     ==================================================================================

     FLAT

     The flat case is trivial, and accomplished with hardware rendering

     ==================================================================================
     */
    /*
     ==============
     RenderBumpFlat_f

     ==============
     */
    class RenderBumpFlat_f : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            var width: Int
            var height: Int
            val source: String
            var i: Int
            val bounds = idBounds()
            var mesh: srfTriangles_s
            val boundsScale: Float

            // update the screen as we print
            Common.common.SetRefreshOnPrint(true)
            height = 256
            width = height
            boundsScale = 0.0f

            // check options
            i = 1
            while (i < args!!.Argc() - 1) {
                var s: String
                s = args.Argv(i)
                if (s[0] == '-') {
                    i++
                    s = args.Argv(i)
                }
                if (0 == idStr.Icmp(s, "size")) {
                    if (i + 2 >= args.Argc()) {
                        i = args.Argc()
                        break
                    }
                    width = args.Argv(i + 1).toInt()
                    height = args.Argv(i + 2).toInt()
                    i += 2
                } else {
                    Common.common.Printf("WARNING: Unknown option \"%s\"\n", s)
                    break
                }
                i++
            }
            if (i != args.Argc() - 1) {
                Common.common.Error("usage: renderBumpFlat [-size width height] asefile")
            }
            Common.common.Printf("Final image size: %d, %d\n", width, height)

            // load the source in "fastload" mode, because we don't
            // need tangent and shadow information
            source = args.Argv(i)
            var highPolyModel = ModelManager.renderModelManager.AllocModel()
            highPolyModel.PartialInitFromFile(source)
            if (highPolyModel.IsDefaultModel()) {
                Common.common.Error("failed to load %s", source)
            }

            // combine the high poly model into a single polyset
            if (highPolyModel.NumSurfaces() != 1) {
                highPolyModel = CombineModelSurfaces(highPolyModel)
            }

            // create normals if not present in file
            val surf = highPolyModel.Surface(0)
            mesh = surf!!.geometry!!

            // bound the entire file
            R_BoundTriSurf(mesh)
            bounds.set(mesh.bounds)
            SaveWindow()
            ResizeWindow(width, height)

            // for small images, the viewport may be less than the minimum window
            qgl.qglViewport(0, 0, width, height)
            qgl.qglEnable(GL11.GL_CULL_FACE)
            qgl.qglCullFace(GL11.GL_FRONT)
            qgl.qglDisable(GL11.GL_STENCIL_TEST)
            qgl.qglDisable(GL11.GL_SCISSOR_TEST)
            qgl.qglDisable(GL11.GL_ALPHA_TEST)
            qgl.qglDisable(GL11.GL_BLEND)
            qgl.qglEnable(GL11.GL_DEPTH_TEST)
            qgl.qglDisable(GL11.GL_TEXTURE_2D)
            qgl.qglDepthMask(TempDump.itob(GL11.GL_TRUE))
            qgl.qglDepthFunc(GL11.GL_LEQUAL)
            qgl.qglColor3f(1.0f, 1.0f, 1.0f)
            qgl.qglMatrixMode(GL11.GL_PROJECTION)
            qgl.qglLoadIdentity()
            qgl.qglOrtho(
                bounds[0, 0].toDouble(), bounds[1, 0].toDouble(), bounds[0, 2].toDouble(),
                bounds[1, 2].toDouble(), -(bounds[0, 1] - 1).toDouble(), -(bounds[1, 1] + 1).toDouble()
            )
            qgl.qglMatrixMode(GL11.GL_MODELVIEW)
            qgl.qglLoadIdentity()

            // flat maps are automatically anti-aliased
            val filename: idStr
            var j: Int
            var k: Int
            var c = 0
            var buffer: ByteBuffer
            var sumBuffer: IntArray
            var colorSumBuffer: IntArray
            val flat: Boolean
            var sample: Int
            sumBuffer = IntArray(width * height * 4 * 4) // Mem_Alloc(width * height * 4 * 4);
            //	memset( sumBuffer, 0, width * height * 4 * 4 );
            buffer = BufferUtils.createByteBuffer(width * height * 4) // Mem_Alloc(width * height * 4);
            colorSumBuffer = IntArray(width * height * 4 * 4) // Mem_Alloc(width * height * 4 * 4);
            //	memset( sumBuffer, 0, width * height * 4 * 4 );
            flat = false
            //flat = true;
            sample = 0
            while (sample < 16) {
                var xOff: Float
                var yOff: Float
                xOff =
                    (sample and 3) / 4.0f * (bounds[1, 0] - bounds[0, 0]) / width //TODO:loss of precision, float instead of double.
                yOff = sample / 4 / 4.0f * (bounds[1, 2] - bounds[0, 2]) / height
                for (colorPass in 0..1) {
                    qgl.qglClearColor(0.5f, 0.5f, 0.5f, 0.0f)
                    qgl.qglClear(GL11.GL_COLOR_BUFFER_BIT or GL11.GL_DEPTH_BUFFER_BIT)
                    qgl.qglBegin(GL11.GL_TRIANGLES)
                    i = 0
                    while (i < highPolyModel.NumSurfaces()) {
                        val surf2 = highPolyModel.Surface(i)
                        mesh = surf2!!.geometry!!
                        if (colorPass != 0) {
                            // just render the surface color for artist visualization
                            j = 0
                            while (j < mesh.numIndexes) {
                                k = 0
                                while (k < 3) {
                                    var v: Int
                                    var a: FloatArray
                                    v = mesh.indexes!![j + k]
                                    qgl.qglColor3ubv(mesh.verts!![v]!!.color)
                                    a = mesh.verts!![v]!!.xyz.ToFloatPtr()
                                    qgl.qglVertex3f(a[0] + xOff, a[2] + yOff, a[1])
                                    k++
                                }
                                j += 3
                            }
                        } else {
                            // render as normal map
                            // we can either flat shade from the plane,
                            // or smooth shade from the vertex normals
                            j = 0
                            while (j < mesh.numIndexes) {
                                if (flat) {
                                    val plane = idPlane()
                                    val a2 = idVec3()
                                    val b2 = idVec3()
                                    val c2 = idVec3()
                                    var v1: Int
                                    var v2: Int
                                    var v3: Int
                                    v1 = mesh.indexes!![j + 0]
                                    v2 = mesh.indexes!![j + 1]
                                    v3 = mesh.indexes!![j + 2]
                                    a2.set(mesh.verts!![v1]!!.xyz)
                                    b2.set(mesh.verts!![v2]!!.xyz)
                                    c2.set(mesh.verts!![v3]!!.xyz)
                                    plane.FromPoints(a2, b2, c2)

                                    // NULLNORMAL is used by the artists to force an area to reflect no
                                    // light at all
                                    if (surf2.shader!!.GetSurfaceFlags() and Material.SURF_NULLNORMAL != 0) {
                                        qgl.qglColor3f(0.5f, 0.5f, 0.5f)
                                    } else {
                                        qgl.qglColor3f(
                                            0.5f + 0.5f * plane[0],
                                            0.5f - 0.5f * plane[2],
                                            0.5f - 0.5f * plane[1]
                                        )
                                    }
                                    qgl.qglVertex3f(a2[0] + xOff, a2[2] + yOff, a2[1])
                                    qgl.qglVertex3f(b2[0] + xOff, b2[2] + yOff, b2[1])
                                    qgl.qglVertex3f(c2[0] + xOff, c2[2] + yOff, c2[1])
                                } else {
                                    k = 0
                                    while (k < 3) {
                                        var v: Int
                                        var n: FloatArray
                                        var a: FloatArray
                                        v = mesh.indexes!![j + k]
                                        n = mesh.verts!![v]!!.normal.ToFloatPtr()

                                        // NULLNORMAL is used by the artists to force an area to reflect no
                                        // light at all
                                        if (surf2.shader!!.GetSurfaceFlags() and Material.SURF_NULLNORMAL != 0) {
                                            qgl.qglColor3f(0.5f, 0.5f, 0.5f)
                                        } else {
                                            // we are going to flip the normal Z direction
                                            qgl.qglColor3f(0.5f + 0.5f * n[0], 0.5f - 0.5f * n[2], 0.5f - 0.5f * n[1])
                                        }
                                        a = mesh.verts!![v]!!.xyz.ToFloatPtr()
                                        qgl.qglVertex3f(a[0] + xOff, a[2] + yOff, a[1])
                                        k++
                                    }
                                }
                                j += 3
                            }
                        }
                        i++
                    }
                    qgl.qglEnd()
                    qgl.qglFlush()
                    win_glimp.GLimp_SwapBuffers()
                    qgl.qglReadPixels(0, 0, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer)
                    if (colorPass != 0) {
                        // add to the sum buffer
                        i = 0
                        while (i < c) {
                            colorSumBuffer[i * 4 + 0] += buffer[i * 4 + 0].toInt()
                            colorSumBuffer[i * 4 + 1] += buffer[i * 4 + 1].toInt()
                            colorSumBuffer[i * 4 + 2] += buffer[i * 4 + 2].toInt()
                            colorSumBuffer[i * 4 + 3] += buffer[i * 4 + 3].toInt()
                            i++
                        }
                    } else {
                        // normalize
                        c = width * height
                        i = 0
                        while (i < c) {
                            val v = idVec3()
                            v[0] = (buffer[i * 4 + 0] - 128) / 127.0f
                            v[1] = (buffer[i * 4 + 1] - 128) / 127.0f
                            v[2] = (buffer[i * 4 + 2] - 128) / 127.0f
                            v.Normalize()
                            buffer.put(i * 4 + 0, (128 + 127 * v[0]).toInt().toByte())
                            buffer.put(i * 4 + 1, (128 + 127 * v[1]).toInt().toByte())
                            buffer.put(i * 4 + 2, (128 + 127 * v[2]).toInt().toByte())
                            i++
                        }

                        // outline into non-drawn areas
                        i = 0
                        while (i < 8) {
                            OutlineNormalMap(buffer, width, height, 128, 128, 128)
                            i++
                        }

                        // add to the sum buffer
                        i = 0
                        while (i < c) {
                            sumBuffer[i * 4 + 0] += buffer[i * 4 + 0].toInt()
                            sumBuffer[i * 4 + 1] += buffer[i * 4 + 1].toInt()
                            sumBuffer[i * 4 + 2] += buffer[i * 4 + 2].toInt()
                            sumBuffer[i * 4 + 3] += buffer[i * 4 + 3].toInt()
                            i++
                        }
                    }
                }
                sample++
            }
            c = width * height

            // save out the color map
            i = 0
            while (i < c) {
                buffer.put(i * 4 + 0, (colorSumBuffer[i * 4 + 0] / 16).toByte())
                buffer.put(i * 4 + 1, (colorSumBuffer[i * 4 + 1] / 16).toByte())
                buffer.put(i * 4 + 2, (colorSumBuffer[i * 4 + 2] / 16).toByte())
                buffer.put(i * 4 + 3, (colorSumBuffer[i * 4 + 3] / 16).toByte())
                i++
            }
            filename = idStr(source)
            filename.StripFileExtension()
            filename.Append("_color.tga")
            Image_process.R_VerticalFlip(buffer, width, height)
            Image_files.R_WriteTGA(filename, buffer, width, height)

            // save out the local map
            // scale the sum buffer back down to the sample buffer
            // we allow this to denormalize
            i = 0
            while (i < c) {
                buffer.put(i * 4 + 0, (sumBuffer[i * 4 + 0] / 16).toByte())
                buffer.put(i * 4 + 1, (sumBuffer[i * 4 + 1] / 16).toByte())
                buffer.put(i * 4 + 2, (sumBuffer[i * 4 + 2] / 16).toByte())
                buffer.put(i * 4 + 3, (sumBuffer[i * 4 + 3] / 16).toByte())
                i++
            }
            filename.set(source)
            filename.StripFileExtension()
            filename.Append("_local.tga")
            Common.common.Printf("writing %s (%d,%d)\n", filename, width, height)
            Image_process.R_VerticalFlip(buffer, width, height)
            Image_files.R_WriteTGA(filename, buffer, width, height)

            // free the model
            ModelManager.renderModelManager.FreeModel(highPolyModel)

            // free our work buffer
            RestoreWindow()

            // stop updating the screen as we print
            Common.common.SetRefreshOnPrint(false)
            Common.common.Error("Completed.")
        }

        companion object {
            private val instance: cmdFunction_t = RenderBumpFlat_f()
            fun getInstance(): cmdFunction_t {
                return instance
            }
        }
    }
}
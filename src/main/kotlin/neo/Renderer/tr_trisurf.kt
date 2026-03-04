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

import neo.Game.GameSys.SysCvar.Companion._DEBUG
import neo.Renderer.Model.dominantTri_s
import neo.Renderer.Model.shadowCache_s
import neo.Renderer.Model.silEdge_t
import neo.Renderer.Model.srfTriangles_s
import neo.TempDump.btoi
import neo.framework.CmdSystem.cmdFunction_t
import neo.framework.Common
import neo.idlib.CmdArgs
import neo.idlib.containers.List.cmp_t
import neo.idlib.containers.List.idList
import neo.idlib.containers.idHashIndex
import neo.idlib.geometry.DrawVert.idDrawVert
import neo.idlib.math.SIMDProcessor
import neo.idlib.math.idMath.RSqrt
import neo.idlib.math.idPlane
import neo.idlib.math.idVec3
import neo.idlib.math.vec3_origin
import java.util.*
import kotlin.math.abs
import kotlin.math.min

// instead of using the texture T vector, cross the normal and S vector for an orthogonal axis
val DERIVE_UNSMOOTHED_BITANGENT: Boolean = true

val MAX_SIL_EDGES: Int = 0x10000
val SILEDGE_HASH_SIZE: Int = 1024

/*
 ==============================================================================

 TRIANGLE MESH PROCESSING

 The functions in this file have no vertex / index count limits.

 Truly identical vertexes that match in position, normal, and texcoord can
 be merged away.

 Vertexes that match in position and texcoord, but have distinct normals will
 remain distinct for all purposes.  This is usually a poor choice for models,
 as adding a bevel face will not add any more vertexes, and will tend to
 look better.

 Match in position and normal, but differ in texcoords are referenced together
 for calculating tangent vectors for bump mapping.
 Artists should take care to have identical texels in all maps (bump/diffuse/specular)
 in this case

 Vertexes that only match in position are merged for shadow edge finding.

 Degenerate triangles.

 Overlapped triangles, even if normals or texcoords differ, must be removed.
 for the silhoette based stencil shadow algorithm to function properly.
 Is this true???
 Is the overlapped triangle problem just an example of the trippled edge problem?

 Interpenetrating triangles are not currently clipped to surfaces.
 Do they effect the shadows?

 if vertexes are intended to deform apart, make sure that no vertexes
 are on top of each other in the base frame, or the sil edges may be
 calculated incorrectly.

 We might be able to identify this from topology.

 Dangling edges are acceptable, but three way edges are not.

 Are any combinations of two way edges unacceptable, like one facing
 the backside of the other?


 Topology is determined by a collection of triangle indexes.

 The edge list can be built up from this, and stays valid even under
 deformations.

 Somewhat non-intuitively, concave edges cannot be optimized away, or the
 stencil shadow algorithm miscounts.

 Face normals are needed for generating shadow volumes and for calculating
 the silhouette, but they will change with any deformation.

 Vertex normals and vertex tangents will change with each deformation,
 but they may be able to be transformed instead of recalculated.

 bounding volume, both box and sphere will change with deformation.

 silhouette indexes
 shade indexes
 texture indexes

 shade indexes will only be > silhouette indexes if there is facet shading present

 lookups from texture to sil and texture to shade?

 The normal and tangent vector smoothing is simple averaging, no attempt is
 made to better handle the cases where the distribution around the shared vertex
 is highly uneven.


 we may get degenerate triangles even with the uniquing and removal
 if the vertexes have different texcoords.

 ==============================================================================
 */
// this shouldn't change anything, but previously renderbumped models seem to need it
val USE_INVA: Boolean = true
private val ID_DEBUG_MEMORY: Boolean = false

/*
 =================
 R_DeriveTangentsWithoutNormals

 Build texture space tangents for bump mapping
 If a surface is deformed, this must be recalculated

 This assumes that any mirrored vertexes have already been duplicated, so
 any shared vertexes will have the tangent spaces smoothed across.

 Texture wrapping slightly complicates this, but as long as the normals
 are shared, and the tangent vectors are projected onto the normals, the
 separate vertexes should wind up with identical tangent spaces.

 mirroring a normalmap WILL cause a slightly visible seam unless the normals
 are completely flat around the edge's full bilerp support.

 Vertexes which are smooth shaded must have their tangent vectors
 in the same plane, which will allow a seamless
 rendering as long as the normal map is even on both sides of the
 seam.

 A smooth shaded surface may have multiple tangent vectors at a vertex
 due to texture seams or mirroring, but it should only have a single
 normal vector.

 Each triangle has a pair of tangent vectors in it's plane

 Should we consider having vertexes point at shared tangent spaces
 to save space or speed transforms?

 this version only handles bilateral symetry
 =================
 */
var DEBUG_R_DeriveTangentsWithoutNormals: Int = 0

/*
 =================
 R_IdentifySilEdges

 If the surface will not deform, coplanar edges (polygon interiors)
 can never create silhouette plains, and can be omited
 =================
 */
var c_coplanarSilEdges: Int = 0

/*
 ===============
 R_DefineEdge
 ===============
 */
var c_duplicatedEdges: Int = 0
var c_tripledEdges: Int = 0
var c_totalSilEdges: Int = 0

var numPlanes: Int = 0
var numSilEdges: Int = 0
var silEdgeHash: idHashIndex = idHashIndex(SILEDGE_HASH_SIZE, MAX_SIL_EDGES)
var silEdges: Array<silEdge_t>? = null

/*
 ==============
 R_AllocStaticTriSurf
 ==============
 */
private var DBG_R_AllocStaticTriSurf: Int = 0

/*
 =================
 R_CleanupTriangles

 FIXME: allow createFlat and createSmooth normals, as well as explicit
 =================
 */
private var DBG_R_CleanupTriangles: Int = 0

/*
 ===============
 R_InitTriSurfData
 ===============
 */
fun R_InitTriSurfData() {
    silEdges = silEdge_t.generateArray(MAX_SIL_EDGES)
}

/*
 ===============
 R_ShutdownTriSurfData
 ===============
 */
fun R_ShutdownTriSurfData() {
    silEdges = null
    silEdgeHash.Free()
}

/*
 ===============
 R_PurgeTriSurfData
 ===============
 */
fun R_PurgeTriSurfData(frame: frameData_t?) {
    // free deferred triangle surfaces
    R_FreeDeferredTriSurfs(frame)
}

/*
 =================
 R_TriSurfMemory

 For memory profiling
 =================
 */
fun R_TriSurfMemory(tri: srfTriangles_s?): Int {
    var total = 0
    if (null == tri) {
        return total
    }

    // used as a flag in interactions
    if (tri === Interaction.LIGHT_TRIS_DEFERRED) {
        return total
    }
    if (tri.shadowVertexes != null) {
        total += tri.numVerts //* sizeof( tri.shadowVertexes[0] );
    } else if (tri.verts != null) {
        if (tri.ambientSurface == null || !tri.verts.contentEquals(tri.ambientSurface!!.verts)) {
            total += tri.numVerts // * sizeof( tri.verts[0] );
        }
    }
    if (tri.facePlanes != null) {
        total += tri.numIndexes / 3 //* sizeof( tri.facePlanes[0] );
    }
    if (tri.indexes != null) {
        if (tri.ambientSurface == null || !tri.indexes.contentEquals(tri.ambientSurface!!.indexes)) {
            total += tri.numIndexes // * sizeof( tri.indexes[0] );
        }
    }
    if (tri.silIndexes != null) {
        total += tri.numIndexes //* sizeof( tri.silIndexes[0] );
    }
    if (tri.silEdges != null) {
        total += tri.numSilEdges * 4
    }
    if (tri.dominantTris != null) {
        total += tri.numVerts //* sizeof( tri.dominantTris[0] );
    }
    if (tri.mirroredVerts != null) {
        total += tri.numMirroredVerts //* sizeof( tri.mirroredVerts[0] );
    }
    if (tri.dupVerts != null) {
        total += tri.numDupVerts // * sizeof( tri.dupVerts[0] );
    }
    total += 4
    return total
}

fun R_TriSurfMemory(tri: Array<srfTriangles_s?>?): Int {
    throw UnsupportedOperationException()
}

/*
 ==============
 R_FreeStaticTriSurfVertexCaches
 ==============
 */
fun R_FreeStaticTriSurfVertexCaches(tri: srfTriangles_s) {
    if (tri.ambientSurface == null) {
        // this is a real model surface
        VertexCache.vertexCache.Free(tri.ambientCache)
        tri.ambientCache = null
    } else {
        // this is a light interaction surface that references
        // a different ambient model surface
        VertexCache.vertexCache.Free(tri.lightingCache)
        tri.lightingCache = null
    }
    if (tri.indexCache != null) {
        VertexCache.vertexCache.Free(tri.indexCache)
        tri.indexCache = null
    }
    if ((tri.shadowCache != null) && (tri.shadowVertexes != null || tri.verts != null)) {
        // if we don't have tri.shadowVertexes, these are a reference to a
        // shadowCache on the original surface, which a vertex program
        // will take care of making unique for each light
        VertexCache.vertexCache.Free(tri.shadowCache)
        tri.shadowCache = null
    }
}

/*
 ==============
 R_ReallyFreeStaticTriSurf

 This does the actual free
 ==============
 */
fun R_ReallyFreeStaticTriSurf(tri: srfTriangles_s?) {
    var tri: srfTriangles_s? = tri
    if (null == tri) {
        return
    }
    R_FreeStaticTriSurfVertexCaches(tri)

    if (_DEBUG) {
        tri = srfTriangles_s()
    }
}

/*
 ==============
 R_CheckStaticTriSurfMemory
 ==============
 */
fun R_CheckStaticTriSurfMemory(tri: srfTriangles_s?) {
    if (null == tri) {
        return
    }
    // Block/dynamic allocator memory checks not applicable — JVM manages memory
}

/*
 ==================
 R_FreeDeferredTriSurfs
 ==================
 */
fun R_FreeDeferredTriSurfs(frame: frameData_t?) {
    var tri: srfTriangles_s?
    var next: srfTriangles_s?
    if (null == frame) {
        return
    }
    tri = frame.firstDeferredFreeTriSurf
    while (tri != null) {
        next = tri.nextDeferredFree
        R_ReallyFreeStaticTriSurf(tri)
        tri = next
    }
    frame.firstDeferredFreeTriSurf = null
    frame.lastDeferredFreeTriSurf = null
}

/*
 ==============
 R_FreeStaticTriSurf

 This will defer the free until the current frame has run through the back end.
 ==============
 */
fun R_FreeStaticTriSurf(tri: srfTriangles_s?) {
    val frame: frameData_t?
    if (null == tri) {
        return
    }
    if (tri.nextDeferredFree != null) {
        Common.common.Error("R_FreeStaticTriSurf: freed a freed triangle")
    }
    frame = frameData
    if (frame == null) {
        // command line utility, or rendering in editor preview mode ( force )
        R_ReallyFreeStaticTriSurf(tri)
    } else {
        if (ID_DEBUG_MEMORY) {
            R_CheckStaticTriSurfMemory(tri)
        }
        tri.nextDeferredFree = null
        if (frame!!.lastDeferredFreeTriSurf != null) {
            frame.lastDeferredFreeTriSurf!!.nextDeferredFree = tri
        } else {
            frame.firstDeferredFreeTriSurf = tri
        }
        frame.lastDeferredFreeTriSurf = tri
    }
}

fun R_FreeStaticTriSurf(tri: Array<srfTriangles_s?>?) {
    throw UnsupportedOperationException()
}

@Deprecated("")
fun R_AllocStaticTriSurf(): srfTriangles_s {
    DBG_R_AllocStaticTriSurf++
    return srfTriangles_s()
}

/*
 =================
 R_CopyStaticTriSurf

 This only duplicates the indexes and verts, not any of the derived data.
 =================
 */
fun R_CopyStaticTriSurf(tri: srfTriangles_s): srfTriangles_s {
    val newTri: srfTriangles_s
    newTri = R_AllocStaticTriSurf()
    R_AllocStaticTriSurfVerts(newTri, tri.numVerts)
    R_AllocStaticTriSurfIndexes(newTri, tri.numIndexes)
    newTri.numVerts = tri.numVerts
    newTri.numIndexes = tri.numIndexes
    for (i in 0 until tri.numVerts) {
        newTri.verts!![i] = idDrawVert((tri.verts!![i])!!)
    }
    System.arraycopy(tri.indexes, 0, newTri.indexes, 0, tri.numIndexes)
    return newTri
}

/*
 =================
 R_AllocStaticTriSurfVerts
 =================
 */
fun R_AllocStaticTriSurfVerts(tri: srfTriangles_s, numVerts: Int) {
    assert((tri.verts == null))
    tri.verts = Array(numVerts) { idDrawVert() }
}

/*
 =================
 R_AllocStaticTriSurfIndexes
 =================
 */
fun R_AllocStaticTriSurfIndexes(tri: srfTriangles_s, numIndexes: Int) {
    assert((tri.indexes == null))
    tri.indexes = IntArray(numIndexes)
}

/*
 =================
 R_AllocStaticTriSurfShadowVerts
 =================
 */
fun R_AllocStaticTriSurfShadowVerts(tri: srfTriangles_s, numVerts: Int) {
    assert((tri.shadowVertexes == null))
    tri.shadowVertexes = shadowCache_s.generateArray(numVerts)
}

/*
 =================
 R_AllocStaticTriSurfPlanes
 =================
 */
fun R_AllocStaticTriSurfPlanes(tri: srfTriangles_s, numIndexes: Int) {
    tri.facePlanes =
        idPlane.generateArray(numIndexes / 3) as Array<idPlane?>
}

/*
 =================
 R_ResizeStaticTriSurfVerts
 =================
 */
fun R_ResizeStaticTriSurfVerts(tri: srfTriangles_s, numVerts: Int) {
    if (USE_TRI_DATA_ALLOCATOR) {
        tri.verts = Resize(tri.verts!!, numVerts) as Array<idDrawVert>?
    } else {
        assert((false))
    }
}

/*
 =================
 R_ResizeStaticTriSurfIndexes
 =================
 */
fun R_ResizeStaticTriSurfIndexes(tri: srfTriangles_s, numIndexes: Int) {
    if (USE_TRI_DATA_ALLOCATOR) {
        tri.indexes =  /*triIndexAllocator.*/Resize(tri.indexes, numIndexes)
    } else {
        assert((false))
    }
}

/*
 =================
 R_ResizeStaticTriSurfShadowVerts
 =================
 */
fun R_ResizeStaticTriSurfShadowVerts(tri: srfTriangles_s, numVerts: Int) {
    if (USE_TRI_DATA_ALLOCATOR) {
        tri.shadowVertexes =  /*triShadowVertexAllocator.*/
            Resize(tri.shadowVertexes as Array<idDrawVert>, numVerts) as Array<shadowCache_s>
    } else {
        assert((false))
    }
}

/*
 =================
 R_ReferenceStaticTriSurfVerts
 =================
 */
fun R_ReferenceStaticTriSurfVerts(tri: srfTriangles_s, reference: srfTriangles_s) {
    tri.verts = reference.verts
}

/*
 =================
 R_ReferenceStaticTriSurfIndexes
 =================
 */
fun R_ReferenceStaticTriSurfIndexes(tri: srfTriangles_s, reference: srfTriangles_s) {
    tri.indexes = reference.indexes
}

/*
 =================
 R_FreeStaticTriSurfSilIndexes
 =================
 */
fun R_FreeStaticTriSurfSilIndexes(tri: srfTriangles_s) {
    tri.silIndexes = null
}

/*
 ===============
 R_RangeCheckIndexes

 Check for syntactically incorrect indexes, like out of range values.
 Does not check for semantics, like degenerate triangles.

 No vertexes is acceptable if no indexes.
 No indexes is acceptable.
 More vertexes than are referenced by indexes are acceptable.
 ===============
 */
fun R_RangeCheckIndexes(tri: srfTriangles_s) {
    var i: Int
    if (tri.numIndexes < 0) {
        Common.common.Error("R_RangeCheckIndexes: numIndexes < 0")
    }
    if (tri.numVerts < 0) {
        Common.common.Error("R_RangeCheckIndexes: numVerts < 0")
    }

    // must specify an integral number of triangles
    if (tri.numIndexes % 3 != 0) {
        Common.common.Error("R_RangeCheckIndexes: numIndexes %% 3")
    }
    i = 0
    while (i < tri.numIndexes) {
        if (tri.indexes!![i] < 0 || tri.indexes!![i] >= tri.numVerts) {
            Common.common.Error("R_RangeCheckIndexes: index out of range")
        }
        i++
    }

    // this should not be possible unless there are unused verts
    if (tri.numVerts > tri.numIndexes) {
        // FIXME: find the causes of these
        // common.Printf( "R_RangeCheckIndexes: tri.numVerts > tri.numIndexes\n" );
    }
}

/*
 =================
 R_BoundTriSurf
 =================
 */
fun R_BoundTriSurf(tri: srfTriangles_s) {
    SIMDProcessor!!.MinMax(tri.bounds[0], tri.bounds[1], tri.verts as Array<idDrawVert>, tri.numVerts)
}

/*
 =================
 R_CreateSilRemap
 =================
 */
fun R_CreateSilRemap(tri: srfTriangles_s): IntArray {
    var c_removed: Int
    var c_unique: Int
    val remap: IntArray
    var i: Int
    var j: Int
    var hashKey: Int
    var v1: idDrawVert?
    var v2: idDrawVert?
    remap = IntArray(tri.numVerts)
    if (!r_useSilRemap!!.GetBool()) {
        i = 0
        while (i < tri.numVerts) {
            remap[i] = i
            i++
        }
        return remap
    }
    val hash = idHashIndex(1024, tri.numVerts)
    c_removed = 0
    c_unique = 0
    assert((tri.numVerts == tri.verts!!.size))
    i = 0
    while (i < tri.numVerts) {
        v1 = tri.verts!![i]

        // see if there is an earlier vert that it can map to
        hashKey = hash.GenerateKey(v1!!.xyz)
        j = hash.First(hashKey)
        while (j >= 0) {
            v2 = tri.verts!![j]
            if ((v2!!.xyz[0] == v1.xyz[0]
                        ) && (v2.xyz[1] == v1.xyz[1]
                        ) && (v2.xyz[2] == v1.xyz[2])
            ) {
                c_removed++
                remap[i] = j
                break
            }
            j = hash.Next(j)
        }
        if (j < 0) {
            c_unique++
            remap[i] = i
            hash.Add(hashKey, i)
        }
        i++
    }
    return remap
}

/*
 =================
 R_CreateSilIndexes

 Uniquing vertexes only on xyz before creating sil edges reduces
 the edge count by about 20% on Q3 models
 =================
 */
fun R_CreateSilIndexes(tri: srfTriangles_s) {
    var i: Int
    val remap: IntArray
    if (tri.silIndexes != null) {
        tri.silIndexes = null
    }
    remap = R_CreateSilRemap(tri)

    // remap indexes to the first one
    tri.silIndexes = IntArray(tri.numIndexes)
    i = 0
    while (i < tri.numIndexes) {
        tri.silIndexes!![i] = remap[tri.indexes!![i]]
        i++
    }
}

/*
 =====================
 R_CreateDupVerts
 =====================
 */
fun R_CreateDupVerts(tri: srfTriangles_s) {
    var i: Int
    val remap = IntArray(tri.numVerts)

    // initialize vertex remap in case there are unused verts
    i = 0
    while (i < tri.numVerts) {
        remap[i] = i
        i++
    }

    // set the remap based on how the silhouette indexes are remapped
    i = 0
    while (i < tri.numIndexes) {
        remap[tri.indexes!![i]] = tri.silIndexes!![i]
        i++
    }

    // create duplicate vertex index based on the vertex remap
    val tempDupVerts = IntArray(tri.numVerts * 2)
    tri.numDupVerts = 0
    i = 0
    while (i < tri.numVerts) {
        if (remap[i] != i) {
            tempDupVerts[tri.numDupVerts * 2 + 0] = i
            tempDupVerts[tri.numDupVerts * 2 + 1] = remap[i]
            tri.numDupVerts++
        }
        i++
    }
    tri.dupVerts = IntArray(tri.numDupVerts * 2)
    System.arraycopy(tempDupVerts, 0, tri.dupVerts, 0, tri.numDupVerts * 2)
}

/*
 =====================
 R_DeriveFacePlanes

 Writes the facePlanes values, overwriting existing ones if present
 =====================
 */
fun R_DeriveFacePlanes(tri: srfTriangles_s) {
    val planes: Array<idPlane?>?
    if (null == tri.facePlanes) {
        R_AllocStaticTriSurfPlanes(tri, tri.numIndexes)
    }
    planes = tri.facePlanes
    if (true) {
        SIMDProcessor!!.DeriveTriPlanes(
            planes as Array<idPlane>,
            tri.verts as Array<idDrawVert>,
            tri.numVerts,
            tri.indexes!!,
            tri.numIndexes
        )
    }
    tri.facePlanesCalculated = true
}

/*
 =====================
 R_CreateVertexNormals

 Averages together the contributions of all faces that are
 used by a vertex, creating drawVert.normal
 =====================
 */
fun R_CreateVertexNormals(tri: srfTriangles_s) {
    var i: Int
    var j: Int
    var p: Int
    var plane: idPlane?
    i = 0
    while (i < tri.numVerts) {
        tri.verts!![i]!!.normal.Zero()
        i++
    }
    if (null == tri.facePlanes || !tri.facePlanesCalculated) {
        R_DeriveFacePlanes(tri)
    }
    if (null == tri.silIndexes) {
        R_CreateSilIndexes(tri)
    }
    plane = tri.facePlanes!![0.also({ p = it })]
    i = 0
    while (i < tri.numIndexes) {
        plane = tri.facePlanes!![p]
        j = 0
        while (j < 3) {
            val index: Int = tri.silIndexes!![i + j]
            tri.verts!![index]!!.normal.plusAssign(plane!!.Normal())
            j++
        }
        i += 3
        p++
    }

    // normalize and replicate from silIndexes to all indexes
    i = 0
    while (i < tri.numIndexes) {
        tri.verts!![tri.indexes!![i]]!!.normal.set(tri.verts!![tri.silIndexes!![i]]!!.normal)
        tri.verts!![tri.indexes!![i]]!!.normal.Normalize()
        i++
    }
}

fun R_DefineEdge(v1: Int, v2: Int, planeNum: Int) {
    var i: Int
    val hashKey: Int

    // check for degenerate edge
    if (v1 == v2) {
        return
    }
    hashKey = silEdgeHash.GenerateKey(v1, v2)
    // search for a matching other side
    i = silEdgeHash.First(hashKey)
    while (i >= 0 && i < MAX_SIL_EDGES) {
        if (silEdges!![i].v1 == v1 && silEdges!![i].v2 == v2) {
            c_duplicatedEdges++
            i = silEdgeHash.Next(i)
            // allow it to still create a new edge
            continue
        }
        if (silEdges!![i].v2 == v1 && silEdges!![i].v1 == v2) {
            if (silEdges!![i].p2 != numPlanes) {
                c_tripledEdges++
                i = silEdgeHash.Next(i)
                // allow it to still create a new edge
                continue
            }
            // this is a matching back side
            silEdges!![i].p2 = planeNum
            return
        }
        i = silEdgeHash.Next(i)
    }

    // define the new edge
    if (numSilEdges == MAX_SIL_EDGES) {
        Common.common.DWarning("MAX_SIL_EDGES")
        return
    }
    silEdgeHash.Add(hashKey, numSilEdges)
    silEdges!![numSilEdges].p1 = planeNum
    silEdges!![numSilEdges].p2 = numPlanes
    silEdges!![numSilEdges].v1 = v1
    silEdges!![numSilEdges].v2 = v2
    numSilEdges++
}

fun R_IdentifySilEdges(tri: srfTriangles_s, omitCoplanarEdges: Boolean) {
    var omitCoplanarEdges: Boolean = omitCoplanarEdges
    var i: Int
    val numTris: Int
    var shared: Int
    var single: Int
    omitCoplanarEdges = false // optimization doesn't work for some reason
    numTris = tri.numIndexes / 3
    numSilEdges = 0
    silEdgeHash.Clear()
    numPlanes = numTris
    c_duplicatedEdges = 0
    c_tripledEdges = 0
    i = 0
    while (i < numTris) {
        var i1: Int
        var i2: Int
        var i3: Int
        i1 = tri.silIndexes!![i * 3 + 0]
        i2 = tri.silIndexes!![i * 3 + 1]
        i3 = tri.silIndexes!![i * 3 + 2]

        // create the edges
        R_DefineEdge(i1, i2, i)
        R_DefineEdge(i2, i3, i)
        R_DefineEdge(i3, i1, i)
        i++
    }
    if (c_duplicatedEdges != 0 || c_tripledEdges != 0) {
        Common.common.DWarning(
            "%d duplicated edge directions, %d tripled edges",
            c_duplicatedEdges,
            c_tripledEdges
        )
    }

    // if we know that the vertexes aren't going
    // to deform, we can remove interior triangulation edges
    // on otherwise planar polygons.
    // I earlier believed that I could also remove concave
    // edges, because they are never silhouettes in the conventional sense,
    // but they are still needed to balance out all the true sil edges
    // for the shadow algorithm to function
    var c_coplanarCulled: Int
    c_coplanarCulled = 0
    if (omitCoplanarEdges) {
        i = 0
        while (i < numSilEdges) {
            var i1: Int
            var i2: Int
            var i3: Int
            val plane = idPlane()
            var base: Int
            var j: Int
            var d: Float
            if (silEdges!![i].p2 == numPlanes) {    // the fake dangling edge
                i++
                continue
            }
            base = silEdges!![i].p1 * 3
            i1 = tri.silIndexes!![base + 0]
            i2 = tri.silIndexes!![base + 1]
            i3 = tri.silIndexes!![base + 2]
            plane.FromPoints(tri.verts!![i1]!!.xyz, tri.verts!![i2]!!.xyz, tri.verts!![i3]!!.xyz)

            // check to see if points of second triangle are not coplanar
            base = silEdges!![i].p2 * 3
            j = 0
            while (j < 3) {
                i1 = tri.silIndexes!![base + j]
                d = plane.Distance(tri.verts!![i1]!!.xyz)
                if (d != 0.0f) {        // even a small epsilon causes problems
                    break
                }
                j++
            }
            if (j == 3) {
                // we can cull this sil edge
//				memmove( &silEdges[i], &silEdges[i+1], (numSilEdges-i-1) * sizeof( silEdges[i] ) );
                for (k in i until numSilEdges - 1) {
                    silEdges!![k] = silEdge_t(silEdges!![k + 1])
                }
                c_coplanarCulled++
                numSilEdges--
                i--
            }
            i++
        }
        if (c_coplanarCulled != 0) { //TODO:should it be >0?
            c_coplanarSilEdges += c_coplanarCulled
            //			common.Printf( "%i of %i sil edges coplanar culled\n", c_coplanarCulled,
//				c_coplanarCulled + numSilEdges );
        }
    }
    c_totalSilEdges += numSilEdges

    // sort the sil edges based on plane number
    Arrays.sort(silEdges, 0, numSilEdges, SilEdgeSort())

    // count up the distribution.
    // a perfectly built model should only have shared
    // edges, but most models will have some interpenetration
    // and dangling edges
    shared = 0
    single = 0
    i = 0
    while (i < numSilEdges) {
        if (silEdges!![i].p2 == numPlanes) {
            single++
        } else {
            shared++
        }
        i++
    }
    tri.perfectHull = single == 0
    tri.numSilEdges = numSilEdges
    tri.silEdges = arrayOfNulls(numSilEdges)
    i = 0
    while (i < tri.numSilEdges) {
        tri.silEdges!![i] = silEdge_t(silEdges!![i])
        i++
    }
}

/*
 ===============
 R_FaceNegativePolarity

 Returns true if the texture polarity of the face is negative, false if it is positive or zero
 ===============
 */
fun R_FaceNegativePolarity(tri: srfTriangles_s, firstIndex: Int): Boolean {
    val a: idDrawVert?
    val b: idDrawVert?
    val c: idDrawVert?
    val area: Float
    val d0 = FloatArray(5)
    val d1 = FloatArray(5)
    a = tri.verts!![tri.indexes!![firstIndex + 0]]
    b = tri.verts!![tri.indexes!![firstIndex + 1]]
    c = tri.verts!![tri.indexes!![firstIndex + 2]]
    d0[3] = b!!.st[0] - a!!.st[0]
    d0[4] = b.st[1] - a.st[1]
    d1[3] = c!!.st[0] - a.st[0]
    d1[4] = c.st[1] - a.st[1]
    area = d0[3] * d1[4] - d0[4] * d1[3]
    return !(area >= 0)
}

fun R_DeriveFaceTangents(tri: srfTriangles_s, faceTangents: Array<faceTangents_t>) {
    var i: Int
    var c_textureDegenerateFaces: Int
    var c_positive: Int
    var c_negative: Int
    var ft: faceTangents_t
    var a: idDrawVert?
    var b: idDrawVert?
    var c: idDrawVert?

    //
    // calculate tangent vectors for each face in isolation
    //
    c_positive = 0
    c_negative = 0
    c_textureDegenerateFaces = 0
    i = 0
    while (i < tri.numIndexes) {
        var area: Float
        val temp = idVec3()
        val d0 = FloatArray(5)
        val d1 = FloatArray(5)
        ft = faceTangents[i / 3]
        a = tri.verts!![tri.indexes!![i + 0]]
        b = tri.verts!![tri.indexes!![i + 1]]
        c = tri.verts!![tri.indexes!![i + 2]]
        d0[0] = b!!.xyz[0] - a!!.xyz[0]
        d0[1] = b.xyz[1] - a.xyz[1]
        d0[2] = b.xyz[2] - a.xyz[2]
        d0[3] = b.st[0] - a.st[0]
        d0[4] = b.st[1] - a.st[1]
        d1[0] = c!!.xyz[0] - a.xyz[0]
        d1[1] = c.xyz[1] - a.xyz[1]
        d1[2] = c.xyz[2] - a.xyz[2]
        d1[3] = c.st[0] - a.st[0]
        d1[4] = c.st[1] - a.st[1]
        area = d0[3] * d1[4] - d0[4] * d1[3]
        if (abs(area.toFloat()) < 1e-20f) {
            ft.negativePolarity = false
            ft.degenerate = true
            ft.tangents[0].Zero()
            ft.tangents[1].Zero()
            c_textureDegenerateFaces++
            i += 3
            continue
        }
        if (area > 0.0f) {
            ft.negativePolarity = false
            c_positive++
        } else {
            ft.negativePolarity = true
            c_negative++
        }
        ft.degenerate = false
        if (USE_INVA) {
            val inva: Float = (if (area < .0f) -1 else 1).toFloat() // was = 1.0f / area;
            temp.set(
                idVec3(
                    (d0[0] * d1[4] - d0[4] * d1[0]) * inva,
                    (d0[1] * d1[4] - d0[4] * d1[1]) * inva,
                    (d0[2] * d1[4] - d0[4] * d1[2]) * inva
                )
            )
            temp.Normalize()
            ft.tangents[0].set(temp)
            temp.set(
                idVec3(
                    (d0[3] * d1[0] - d0[0] * d1[3]) * inva,
                    (d0[3] * d1[1] - d0[1] * d1[3]) * inva,
                    (d0[3] * d1[2] - d0[2] * d1[3]) * inva
                )
            )
            temp.Normalize()
            ft.tangents[1].set(temp)
        } else {
            temp.set(
                idVec3(
                    (d0[0] * d1[4] - d0[4] * d1[0]),
                    (d0[1] * d1[4] - d0[4] * d1[1]),
                    (d0[2] * d1[4] - d0[4] * d1[2])
                )
            )
            temp.Normalize()
            ft.tangents[0].set(temp)
            temp.set(
                idVec3(
                    (d0[3] * d1[0] - d0[0] * d1[3]),
                    (d0[3] * d1[1] - d0[1] * d1[3]),
                    (d0[3] * d1[2] - d0[2] * d1[3])
                )
            )
            temp.Normalize()
            ft.tangents[1].set(temp)
        }
        i += 3
    }
}

fun R_DuplicateMirroredVertexes(tri: srfTriangles_s) {
    val tVerts: Array<tangentVert_t?>
    var vert: tangentVert_t?
    var i: Int
    var j: Int
    var totalVerts: Int
    var numMirror: Int
    tVerts = arrayOfNulls(tri.numVerts)
    for (t in tVerts.indices) {
        tVerts[t] = tangentVert_t()
    }

    // determine texture polarity of each surface
    // mark each vert with the polarities it uses
    i = 0
    while (i < tri.numIndexes) {
        var polarity: Int
        polarity = btoi(R_FaceNegativePolarity(tri, i))
        j = 0
        while (j < 3) {
            tVerts[tri.indexes!![i + j]]!!.polarityUsed[polarity] = true
            j++
        }
        i += 3
    }

    // now create new verts as needed
    totalVerts = tri.numVerts
    i = 0
    while (i < tri.numVerts) {
        vert = tVerts[i]
        if (vert!!.polarityUsed[0] && vert.polarityUsed[1]) {
            vert.negativeRemap = totalVerts
            totalVerts++
        }
        i++
    }
    tri.numMirroredVerts = totalVerts - tri.numVerts

    // now create the new list
    if (totalVerts == tri.numVerts) {
        tri.mirroredVerts = null
        return
    }
    tri.mirroredVerts = IntArray(tri.numMirroredVerts)
    if (USE_TRI_DATA_ALLOCATOR) {
        tri.verts = Resize(tri.verts as Array<idDrawVert>, totalVerts) as Array<idDrawVert>?
    } else {
        val oldVerts: Array<idDrawVert>? = tri.verts
        R_AllocStaticTriSurfVerts(tri, totalVerts)
        i = 0
        while (i < tri.numVerts) {
            tri.verts!![i] = idDrawVert((oldVerts!![i]))
            i++
        }
    }

    // create the duplicates
    numMirror = 0
    i = 0
    while (i < tri.numVerts) {
        j = tVerts[i]!!.negativeRemap
        if (j != 0) {
            tri.verts!![j] = idDrawVert((tri.verts!![i])!!)
            tri.mirroredVerts!![numMirror] = i
            numMirror++
        }
        i++
    }
    tri.numVerts = totalVerts
    // change the indexes
    i = 0
    while (i < tri.numIndexes) {
        if ((tVerts[tri.indexes!![i]]!!.negativeRemap != 0
                    && R_FaceNegativePolarity(tri, 3 * (i / 3)))
        ) {
            tri.indexes!![i] = tVerts[tri.indexes!![i]]!!.negativeRemap
        }
        i++
    }
    tri.numVerts = totalVerts
}

fun R_DeriveTangentsWithoutNormals(tri: srfTriangles_s) {
    var i: Int
    var j: Int
    val faceTangents: Array<faceTangents_t>
    var ft: faceTangents_t
    var vert: idDrawVert?
    faceTangents = faceTangents_t.generateArray(tri.numIndexes / 3)
    R_DeriveFaceTangents(tri, faceTangents)

    // clear the tangents
    i = 0
    while (i < tri.numVerts) {
        tri.verts!![i]!!.tangents[0].Zero()
        tri.verts!![i]!!.tangents[1].Zero()
        i++
    }

    // sum up the neighbors
    i = 0
    while (i < tri.numIndexes) {
        ft = faceTangents[i / 3]

        // for each vertex on this face
        j = 0
        while (j < 3) {
            DEBUG_R_DeriveTangentsWithoutNormals++
            vert = tri.verts!![tri.indexes!![i + j]]

//                System.out.println("--" + System.identityHashCode(vert.tangents[0])
//                        + "--" + i + j
//                        + "--" + tri.indexes[i + j]);
            vert!!.tangents[0].plusAssign(ft.tangents[0])
            vert.tangents[1].plusAssign(ft.tangents[1])
            j++
        }
        i += 3
    }

//if (false){
//	// sum up both sides of the mirrored verts
//	// so the S vectors exactly mirror, and the T vectors are equal
//	for ( i = 0 ; i < tri.numMirroredVerts ; i++ ) {
//		idDrawVert	v1, v2;
//
//		v1 = tri.verts[ tri.numVerts - tri.numMirroredVerts + i ];
//		v2 = tri.verts[ tri.mirroredVerts[i] ];
//
//		v1.tangents[0] -= v2.tangents[0];
//		v1.tangents[1] += v2.tangents[1];
//
//		v2.tangents[0] = vec3_origin - v1.tangents[0];
//		v2.tangents[1] = v1.tangents[1];
//	}
//}
    // project the summed vectors onto the normal plane
    // and normalize.  The tangent vectors will not necessarily
    // be orthogonal to each other, but they will be orthogonal
    // to the surface normal.
    i = 0
    while (i < tri.numVerts) {
        vert = tri.verts!![i]
        j = 0
        while (j < 2) {
            var d: Float
            d = vert!!.tangents[j].times(vert.normal)
            vert.tangents[j] = vert.tangents[j].minus(vert.normal.times(d))
            vert.tangents[j].Normalize()
            j++
        }
        i++
    }
    tri.tangentsCalculated = true
}

fun  /*ID_INLINE*/VectorNormalizeFast2(v: idVec3, out: idVec3) {
    val length: Float
    length = RSqrt((v[0] * v[0]) + (v[1] * v[1]) + (v[2] * v[2]))
    out[0] = v[0] * length
    out[1] = v[1] * length
    out[2] = v[2] * length
}

fun R_BuildDominantTris(tri: srfTriangles_s) {
    var i: Int
    var j: Int
    val dt: Array<dominantTri_s?>
    val ind: Array<indexSort_t?> = arrayOfNulls(tri.numIndexes)
    i = 0
    while (i < tri.numIndexes) {
        ind[i] = indexSort_t()
        ind[i]!!.vertexNum = tri.indexes!![i]
        ind[i]!!.faceNum = i / 3
        i++
    }
    Arrays.sort(ind, 0, tri.numIndexes, IndexSort())
    dt = arrayOfNulls(tri.numVerts)
    tri.dominantTris = dt
    i = 0
    while (i < tri.numIndexes) {
        var maxArea = 0.0f
        val vertNum: Int = ind[i]!!.vertexNum
        j = 0
        while (i + j < tri.numIndexes && ind[i + j]!!.vertexNum == vertNum) {
            val d0 = FloatArray(5)
            val d1 = FloatArray(5)
            var a: idDrawVert?
            var b: idDrawVert?
            var c: idDrawVert?
            val normal = idVec3()
            val tangent = idVec3()
            val bitangent = idVec3()
            val i1: Int = tri.indexes!![ind[i + j]!!.faceNum * 3 + 0]
            val i2: Int = tri.indexes!![ind[i + j]!!.faceNum * 3 + 1]
            val i3: Int = tri.indexes!![ind[i + j]!!.faceNum * 3 + 2]
            a = tri.verts!![i1]
            b = tri.verts!![i2]
            c = tri.verts!![i3]
            d0[0] = b!!.xyz[0] - a!!.xyz[0]
            d0[1] = b.xyz[1] - a.xyz[1]
            d0[2] = b.xyz[2] - a.xyz[2]
            d0[3] = b.st[0] - a.st[0]
            d0[4] = b.st[1] - a.st[1]
            d1[0] = c!!.xyz[0] - a.xyz[0]
            d1[1] = c.xyz[1] - a.xyz[1]
            d1[2] = c.xyz[2] - a.xyz[2]
            d1[3] = c.st[0] - a.st[0]
            d1[4] = c.st[1] - a.st[1]
            normal[0] = (d1[1] * d0[2] - d1[2] * d0[1])
            normal[1] = (d1[2] * d0[0] - d1[0] * d0[2])
            normal[2] = (d1[0] * d0[1] - d1[1] * d0[0])
            var area: Float = normal.Length()

            // if this is smaller than what we already have, skip it
            if (area < maxArea) {
                j++
                continue
            }
            maxArea = area
            dt[vertNum] = dominantTri_s()
            if (i1 == vertNum) {
                dt[vertNum]!!.v2 = i2
                dt[vertNum]!!.v3 = i3
            } else if (i2 == vertNum) {
                dt[vertNum]!!.v2 = i3
                dt[vertNum]!!.v3 = i1
            } else {
                dt[vertNum]!!.v2 = i1
                dt[vertNum]!!.v3 = i2
            }
            var len: Float = area
            if (len < 0.001) {
                len = 0.001f
            }
            dt[vertNum]!!.normalizationScale[2] = 1.0f / len // normal

            // texture area
            area = d0[3] * d1[4] - d0[4] * d1[3]
            tangent[0] = (d0[0] * d1[4] - d0[4] * d1[0])
            tangent[1] = (d0[1] * d1[4] - d0[4] * d1[1])
            tangent[2] = (d0[2] * d1[4] - d0[4] * d1[2])
            len = tangent.Length()
            if (len < 0.001) {
                len = 0.001f
            }
            dt[vertNum]!!.normalizationScale[0] = (if (area > 0) 1 else -1) / len // tangents[0]
            bitangent[0] = (d0[3] * d1[0] - d0[0] * d1[3])
            bitangent[1] = (d0[3] * d1[1] - d0[1] * d1[3])
            bitangent[2] = (d0[3] * d1[2] - d0[2] * d1[3])
            len = bitangent.Length()
            if (len < 0.001) {
                len = 0.001f
            }
            if (DERIVE_UNSMOOTHED_BITANGENT) {
                dt[vertNum]!!.normalizationScale[1] = (if (area > 0) 1 else -1).toFloat()
            } else {
                dt[vertNum]!!.normalizationScale[1] = (if (area > 0) 1 else -1) / len // tangents[1]
            }
            j++
        }
        i += j
    }
}

/*
 ====================
 R_DeriveUnsmoothedTangents

 Uses the single largest area triangle for each vertex, instead of smoothing over all
 ====================
 */
fun R_DeriveUnsmoothedTangents(tri: srfTriangles_s) {
    if (tri.tangentsCalculated) {
        return
    }
    if (true) {
        SIMDProcessor!!.DeriveUnsmoothedTangents(
            tri.verts as Array<idDrawVert>,
            tri.dominantTris as Array<dominantTri_s>,
            tri.numVerts
        )
    }
    tri.tangentsCalculated = true
}

/*
 ==================
 R_DeriveTangents

 This is called once for static surfaces, and every frame for deforming surfaces

 Builds tangents, normals, and face planes
 ==================
 */

fun R_DeriveTangents(tri: srfTriangles_s, allocFacePlanes: Boolean = true) {
    var i: Int
    var planes: Array<idPlane?>?
    if (tri.dominantTris != null) {
        R_DeriveUnsmoothedTangents(tri)
        return
    }
    if (tri.tangentsCalculated) {
        return
    }
    tr.pc!!.c_tangentIndexes += tri.numIndexes
    if (null == tri.facePlanes && allocFacePlanes) {
        R_AllocStaticTriSurfPlanes(tri, tri.numIndexes)
    }
    planes = tri.facePlanes
    if (null == planes) {
        planes = idPlane.generateArray(tri.numIndexes / 3) as Array<idPlane?>
    }
    SIMDProcessor!!.DeriveTangents(
        planes as Array<idPlane>,
        tri.verts as Array<idDrawVert>,
        tri.numVerts,
        tri.indexes!!,
        tri.numIndexes
    )

    run({
        val dupVerts: IntArray? = tri.dupVerts
        val verts: Array<idDrawVert>? = tri.verts

        // add the normal of a duplicated vertex to the normal of the first vertex with the same XYZ
        i = 0
        while (i < tri.numDupVerts) {
            verts!![dupVerts!![i * 2 + 0]].normal.plusAssign(verts[dupVerts[i * 2 + 1]]!!.normal)
            i++
        }

        // copy vertex normals to duplicated vertices
        i = 0
        while (i < tri.numDupVerts) {
            verts!![dupVerts!![i * 2 + 1]]!!.normal.set(verts[dupVerts[i * 2 + 0]]!!.normal)
            i++
        }
    })

    // project the summed vectors onto the normal plane
    // and normalize.  The tangent vectors will not necessarily
    // be orthogonal to each other, but they will be orthogonal
    // to the surface normal.
    SIMDProcessor!!.NormalizeTangents(tri.verts as Array<idDrawVert>, tri.numVerts)

    tri.tangentsCalculated = true
    tri.facePlanesCalculated = true
}

fun R_RemoveDuplicatedTriangles(tri: srfTriangles_s) {
    var c_removed: Int
    var i: Int
    var j: Int
    var r: Int
    var a: Int
    var b: Int
    var c: Int
    c_removed = 0

    // check for completely duplicated triangles
    // any rotation of the triangle is still the same, but a mirroring
    // is considered different
    i = 0
    while (i < tri.numIndexes) {
        r = 0
        while (r < 3) {
            a = tri.silIndexes!![i + r]
            b = tri.silIndexes!![i + (r + 1) % 3]
            c = tri.silIndexes!![i + (r + 2) % 3]
            j = i + 3
            while (j < tri.numIndexes) {
                if ((tri.silIndexes!![j] == a) && (tri.silIndexes!![j + 1] == b) && (tri.silIndexes!![j + 2] == c)) {
                    c_removed++
                    //					memmove( tri.indexes + j, tri.indexes + j + 3, ( tri.numIndexes - j - 3 ) * sizeof( tri.indexes[0] ) );
                    System.arraycopy(tri.indexes, j + 3, tri.indexes, j, tri.numIndexes - j - 3)
                    //					memmove( tri.silIndexes + j, tri.silIndexes + j + 3, ( tri.numIndexes - j - 3 ) * sizeof( tri.silIndexes[0] ) );
                    System.arraycopy(tri.silIndexes, j + 3, tri.silIndexes, j, tri.numIndexes - j - 3)
                    tri.numIndexes -= 3
                    j -= 3
                }
                j += 3
            }
            r++
        }
        i += 3
    }
    if (c_removed != 0) {
        Common.common.Printf("removed %d duplicated triangles\n", c_removed)
    }
}

/*
 =================
 R_RemoveDegenerateTriangles

 silIndexes must have already been calculated
 =================
 */
fun R_RemoveDegenerateTriangles(tri: srfTriangles_s) {
    var c_removed: Int
    var i: Int
    var a: Int
    var b: Int
    var c: Int

    // check for completely degenerate triangles
    c_removed = 0
    i = 0
    while (i < tri.numIndexes) {
        a = tri.silIndexes!![i]
        b = tri.silIndexes!![i + 1]
        c = tri.silIndexes!![i + 2]
        if ((a == b) || (a == c) || (b == c)) {
            c_removed++
            //			memmove( tri.indexes + i, tri.indexes + i + 3, ( tri.numIndexes - i - 3 ) * sizeof( tri.indexes[0] ) );
            System.arraycopy(tri.indexes, i + 3, tri.indexes, i, tri.numIndexes - i - 3)
            if (tri.silIndexes != null) {
//				memmove( tri.silIndexes + i, tri.silIndexes + i + 3, ( tri.numIndexes - i - 3 ) * sizeof( tri.silIndexes[0] ) );
                System.arraycopy(tri.silIndexes, i + 3, tri.silIndexes, i, tri.numIndexes - i - 3)
            }
            tri.numIndexes -= 3
            i -= 3
        }
        i += 3
    }

    // this doesn't free the memory used by the unused verts
    if (c_removed != 0) {
        Common.common.Printf("removed %d degenerate triangles\n", c_removed)
    }
}

/*
 =================
 R_TestDegenerateTextureSpace
 =================
 */
fun R_TestDegenerateTextureSpace(tri: srfTriangles_s) {
    var c_degenerate: Int
    var i: Int

    // check for triangles with a degenerate texture space
    c_degenerate = 0
    i = 0
    while (i < tri.numIndexes) {
        val a: idDrawVert = tri.verts!![tri.indexes!![i + 0]]
        val b: idDrawVert = tri.verts!![tri.indexes!![i + 1]]
        val c: idDrawVert = tri.verts!![tri.indexes!![i + 2]]
        if ((a!!.st == b!!.st) || (b.st == c!!.st) || (c.st == a.st)) {
            c_degenerate++
        }
        i += 3
    }
    if (c_degenerate != 0) {
//		common.Printf( "%d triangles with a degenerate texture space\n", c_degenerate );
    }
}

/*
 =================
 R_RemoveUnusedVerts
 =================
 */
fun R_RemoveUnusedVerts(tri: srfTriangles_s) {
    var i: Int
    val mark: IntArray
    var index: Int
    var used: Int
    mark = IntArray(tri.numVerts)
    i = 0
    while (i < tri.numIndexes) {
        index = tri.indexes!![i]
        if (index < 0 || index >= tri.numVerts) {
            Common.common.Error("R_RemoveUnusedVerts: bad index")
        }
        mark[index] = 1
        if (tri.silIndexes != null) {
            index = tri.silIndexes!![i]
            if (index < 0 || index >= tri.numVerts) {
                Common.common.Error("R_RemoveUnusedVerts: bad index")
            }
            mark[index] = 1
        }
        i++
    }
    used = 0
    i = 0
    while (i < tri.numVerts) {
        if (0 == mark[i]) {
            i++
            continue
        }
        mark[i] = used + 1
        used++
        i++
    }
    if (used != tri.numVerts) {
        i = 0
        while (i < tri.numIndexes) {
            tri.indexes!![i] = mark[tri.indexes!![i]] - 1
            if (tri.silIndexes != null) {
                tri.silIndexes!![i] = mark[tri.silIndexes!![i]] - 1
            }
            i++
        }
        tri.numVerts = used
        i = 0
        while (i < tri.numVerts) {
            index = mark[i]
            if (0 == index) {
                i++
                continue
            }
            tri.verts!![index - 1] = tri.verts!![i]
            i++
        }

        // this doesn't realloc the arrays to save the memory used by the unused verts
    }

}

/*
 =================
 R_MergeSurfaceList

 Only deals with vertexes and indexes, not silhouettes, planes, etc.
 Does NOT perform a cleanup triangles, so there may be duplicated verts in the result.
 =================
 */
fun R_MergeSurfaceList(surfaces: Array<srfTriangles_s>, numSurfaces: Int): srfTriangles_s {
    val newTri: srfTriangles_s
    var tri: srfTriangles_s
    var i: Int
    var j: Int
    var totalVerts: Int
    var totalIndexes: Int
    totalVerts = 0
    totalIndexes = 0
    i = 0
    while (i < numSurfaces) {
        totalVerts += surfaces[i].numVerts
        totalIndexes += surfaces[i].numIndexes
        i++
    }
    newTri = R_AllocStaticTriSurf()
    newTri.numVerts = totalVerts
    newTri.numIndexes = totalIndexes
    R_AllocStaticTriSurfVerts(newTri, newTri.numVerts)
    R_AllocStaticTriSurfIndexes(newTri, newTri.numIndexes)
    totalVerts = 0
    totalIndexes = 0
    i = 0
    while (i < numSurfaces) {
        tri = surfaces[i]
        var k = 0
        var tv: Int = totalVerts
        while (k < tri.numVerts) {
            newTri.verts!![tv] = idDrawVert((tri.verts!![k])!!)
            k++
            tv++
        }
        j = 0
        while (j < tri.numIndexes) {
            newTri.indexes!![totalIndexes + j] = totalVerts + tri.indexes!![j]
            j++
        }
        totalVerts += tri.numVerts
        totalIndexes += tri.numIndexes
        i++
    }
    return newTri
}

/*
 =================
 R_RemoveDuplicatedTriangles

 silIndexes must have already been calculated

 silIndexes are used instead of indexes, because duplicated
 triangles could have different texture coordinates.
 =================
 */
/*
 =================
 R_MergeTriangles

 Only deals with vertexes and indexes, not silhouettes, planes, etc.
 Does NOT perform a cleanup triangles, so there may be duplicated verts in the result.
 =================
 */
fun R_MergeTriangles(tri1: srfTriangles_s?, tri2: srfTriangles_s?): srfTriangles_s {
    val tris: Array<srfTriangles_s?> = arrayOfNulls(2)
    tris[0] = tri1
    tris[1] = tri2
    return R_MergeSurfaceList(tris as Array<srfTriangles_s>, 2)
}

/*
 =================
 R_ReverseTriangles

 Lit two sided surfaces need to have the triangles actually duplicated,
 they can't just turn on two sided lighting, because the normal and tangents
 are wrong on the other sides.

 This should be called before R_CleanupTriangles
 =================
 */
fun R_ReverseTriangles(tri: srfTriangles_s) {
    var i: Int

    // flip the normal on each vertex
    // If the surface is going to have generated normals, this won't matter,
    // but if it has explicit normals, this will keep it on the correct side
    i = 0
    while (i < tri.numVerts) {
        tri.verts!![i]!!.normal.set(vec3_origin.minus(tri.verts!![i]!!.normal))
        i++
    }

    // flip the index order to make them back sided
    i = 0
    while (i < tri.numIndexes) {
        var  /*glIndex_t*/temp: Int
        temp = tri.indexes!![i + 0]
        tri.indexes!![i + 0] = tri.indexes!![i + 1]
        tri.indexes!![i + 1] = temp
        i += 3
    }
}

fun R_CleanupTriangles(
    tri: srfTriangles_s,
    createNormals: Boolean,
    identifySilEdges: Boolean,
    useUnsmoothedTangents: Boolean
) {
    DBG_R_CleanupTriangles++
    R_RangeCheckIndexes(tri)
    R_CreateSilIndexes(tri)

//	R_RemoveDuplicatedTriangles( tri );	// this may remove valid overlapped transparent triangles
    R_RemoveDegenerateTriangles(tri)
    R_TestDegenerateTextureSpace(tri)

//	R_RemoveUnusedVerts( tri );
    if (identifySilEdges) {
        R_IdentifySilEdges(tri, true) // assume it is non-deformable, and omit coplanar edges
    }

    // bust vertexes that share a mirrored edge into separate vertexes
    R_DuplicateMirroredVertexes(tri)

    // optimize the index order (not working?)
//	R_OrderIndexes( tri.numIndexes, tri.indexes );
    R_CreateDupVerts(tri)
    R_BoundTriSurf(tri)
    if (useUnsmoothedTangents) {
        R_BuildDominantTris(tri)
        R_DeriveUnsmoothedTangents(tri)
    } else if (!createNormals) {
        R_DeriveFacePlanes(tri)
        R_DeriveTangentsWithoutNormals(tri)
    } else {
        R_DeriveTangents(tri)
    }
}

/*
 ===================
 R_BuildDeformInfo
 ===================
 */
fun R_BuildDeformInfo(
    numVerts: Int,
    verts: idDrawVert?,
    numIndexes: Int,
    indexes: IntArray,
    useUnsmoothedTangents: Boolean
): deformInfo_s {
    val deform: deformInfo_s
    val tri: srfTriangles_s
    var i: Int
    tri = srfTriangles_s()
    tri.numVerts = numVerts
    R_AllocStaticTriSurfVerts(tri, tri.numVerts)
    SIMDProcessor!!.Memcpy(tri.verts as Array<idDrawVert>, verts as Array<idDrawVert>, tri.numVerts)
    tri.numIndexes = numIndexes
    R_AllocStaticTriSurfIndexes(tri, tri.numIndexes)

    // don't memcpy, so we can change the index type from int to short without changing the interface
    i = 0
    while (i < tri.numIndexes) {
        tri.indexes!![i] = indexes[i]
        i++
    }
    R_RangeCheckIndexes(tri)
    R_CreateSilIndexes(tri)

// should we order the indexes here?
//	R_RemoveDuplicatedTriangles( &tri );
//	R_RemoveDegenerateTriangles( &tri );
//	R_RemoveUnusedVerts( &tri );
    R_IdentifySilEdges(tri, false) // we cannot remove coplanar edges, because
    // they can deform to silhouettes
    R_DuplicateMirroredVertexes(tri) // split mirror points into multiple points
    R_CreateDupVerts(tri)
    if (useUnsmoothedTangents) {
        R_BuildDominantTris(tri)
    }
    deform = deformInfo_s()
    deform.numSourceVerts = numVerts
    deform.numOutputVerts = tri.numVerts
    deform.numIndexes = numIndexes
    deform.indexes = tri.indexes!!
    deform.silIndexes = tri.silIndexes!!
    deform.numSilEdges = tri.numSilEdges
    deform.silEdges = tri.silEdges as Array<silEdge_t>
    deform.dominantTris = tri.dominantTris as Array<dominantTri_s>
    deform.numMirroredVerts = tri.numMirroredVerts
    deform.mirroredVerts = tri.mirroredVerts!!
    deform.numDupVerts = tri.numDupVerts
    deform.dupVerts = tri.dupVerts!!
    if (tri.verts != null) {
        tri.verts = null
    }
    if (tri.facePlanes != null) {
        tri.facePlanes = null
    }
    return deform
}

fun R_BuildDeformInfo(
    numVerts: Int,
    verts: Array<idDrawVert>?,
    numIndexes: Int,
    indexes: idList<Int>,
    useUnsmoothedTangents: Boolean
): deformInfo_s {
    val deform: deformInfo_s
    val tri: srfTriangles_s
    var i: Int
    tri = srfTriangles_s()
    tri.numVerts = numVerts
    R_AllocStaticTriSurfVerts(tri, tri.numVerts)
    SIMDProcessor!!.Memcpy(tri.verts as Array<idDrawVert>, verts as Array<idDrawVert>, tri.numVerts)
    tri.numIndexes = numIndexes
    R_AllocStaticTriSurfIndexes(tri, tri.numIndexes)

    // don't memcpy, so we can change the index type from int to short without changing the interface
    i = 0
    while (i < tri.numIndexes) {
        tri.indexes!![i] = indexes[i]
        i++
    }
    R_RangeCheckIndexes(tri)
    R_CreateSilIndexes(tri)

    // should we order the indexes here?
//	R_RemoveDuplicatedTriangles( &tri );
//	R_RemoveDegenerateTriangles( &tri );
//	R_RemoveUnusedVerts( &tri );
    R_IdentifySilEdges(tri, false) // we cannot remove coplanar edges, because
    //                                              // they can deform to silhouettes
    R_DuplicateMirroredVertexes(tri) // split mirror points into multiple points
    R_CreateDupVerts(tri)
    if (useUnsmoothedTangents) {
        R_BuildDominantTris(tri)
    }
    deform = deformInfo_s()
    deform.numSourceVerts = numVerts
    deform.numOutputVerts = tri.numVerts
    deform.numIndexes = numIndexes
    deform.indexes = tri.indexes
    deform.silIndexes = tri.silIndexes
    deform.numSilEdges = tri.numSilEdges
    deform.silEdges = tri.silEdges as Array<silEdge_t>?
    deform.dominantTris = tri.dominantTris as Array<dominantTri_s>?
    deform.numMirroredVerts = tri.numMirroredVerts
    deform.mirroredVerts = tri.mirroredVerts
    deform.numDupVerts = tri.numDupVerts
    deform.dupVerts = tri.dupVerts
    return deform
}

/*
 ===================
 R_FreeDeformInfo
 ===================
 */
fun R_FreeDeformInfo(deformInfo: deformInfo_s?) {
}

/*
 ===================
 R_DeformInfoMemoryUsed
 ===================
 */
fun R_DeformInfoMemoryUsed(deformInfo: deformInfo_s): Int {
    var total = 0
    if (deformInfo.indexes != null) {
        total += deformInfo.numIndexes // * sizeof( deformInfo.indexes[0] );
    }
    if (deformInfo.silIndexes != null) {
        total += deformInfo.numIndexes // * sizeof( deformInfo.silIndexes[0] );
    }
    if (deformInfo.silEdges != null) {
        total += deformInfo.numSilEdges //* sizeof( deformInfo.silEdges[0] );
    }
    if (deformInfo.dominantTris != null) {
        total += deformInfo.numSourceVerts //* sizeof( deformInfo.dominantTris[0] );
    }
    if (deformInfo.mirroredVerts != null) {
        total += deformInfo.numMirroredVerts //* sizeof( deformInfo.mirroredVerts[0] );
    }
    if (deformInfo.dupVerts != null) {
        total += deformInfo.numDupVerts // * sizeof( deformInfo.dupVerts[0] );
    }
    total += 4
    return total
}

private fun Resize(verts: Array<idDrawVert>, totalVerts: Int): Array<idDrawVert?> {
    val newVerts: Array<idDrawVert?> = arrayOfNulls(totalVerts)
    for (i in verts.indices) {
        newVerts[i] = idDrawVert(verts[i])
    }
    return newVerts
}

private fun Resize(shadowVertexes: Array<shadowCache_s>, numVerts: Int): Array<shadowCache_s?> {
    val newArray: Array<shadowCache_s?> = arrayOfNulls(numVerts)
    val length: Int = min(shadowVertexes.size, numVerts)
    System.arraycopy(shadowVertexes, 0, newArray, 0, length)
    return newArray
}

/*
 ===================================================================================

 DEFORMED SURFACES

 ===================================================================================
 */
private fun Resize(indexes: IntArray?, numIndexes: Int): IntArray? {
    if (indexes == null) {
        return IntArray(numIndexes)
    }
    if (numIndexes <= 0) {
        return null
    }
    val size: Int = if (numIndexes > indexes.size) indexes.size else numIndexes
    val newIndexes = IntArray(numIndexes)
    System.arraycopy(indexes, 0, newIndexes, 0, size)
    return newIndexes
}

/*
 ===============
 R_ShowTriMemory_f
 ===============
 */
@Deprecated("")
class R_ShowTriSurfMemory_f private constructor() : cmdFunction_t() {
    override fun run(args: CmdArgs.idCmdArgs?) {
        // Block/dynamic allocator stats not applicable — JVM manages memory
    }

    companion object {
        val instance: cmdFunction_t = R_ShowTriSurfMemory_f()
    }
}

/*
 =================
 SilEdgeSort
 =================
 */
class SilEdgeSort : cmp_t<silEdge_t?> {
    override fun compare(a: silEdge_t?, b: silEdge_t?): Int {
        if (a!!.p1 < b!!.p1) {
            return -1
        }
        if (a.p1 > b.p1) {
            return 1
        }
        if (a.p2 < b.p2) {
            return -1
        }
        if (a.p2 > b.p2) {
            return 1
        }
        return 0
    }
}

/*
 ==================
 R_DeriveFaceTangents
 ==================
 */
class faceTangents_t {
    var degenerate: Boolean = false
    var negativePolarity: Boolean = false
    val tangents: Array<idVec3> = idVec3.generateArray(2)

    companion object {
        fun generateArray(length: Int): Array<faceTangents_t> {
            return Array(length) { faceTangents_t() }
        }
    }
}

/*
 ===================
 R_DuplicateMirroredVertexes

 Modifies the surface to bust apart any verts that are shared by both positive and
 negative texture polarities, so tangent space smoothing at the vertex doesn't
 degenerate.

 This will create some identical vertexes (which will eventually get different tangent
 vectors), so never optimize the resulting mesh, or it will get the mirrored edges back.

 Reallocates tri.verts and changes tri.indexes in place
 Silindexes are unchanged by this.

 sets mirroredVerts and mirroredVerts[]

 ===================
 */
internal class tangentVert_t {
    val polarityUsed: BooleanArray = BooleanArray(2)
    var negativeRemap: Int = 0
}

/*
 ===================
 R_BuildDominantTris

 Find the largest triangle that uses each vertex
 ===================
 */
internal class indexSort_t {
    var faceNum: Int = 0
    var vertexNum: Int = 0
}

internal class IndexSort : cmp_t<indexSort_t?> {
    override fun compare(a: indexSort_t?, b: indexSort_t?): Int {
        if (a!!.vertexNum < b!!.vertexNum) {
            return -1
        }
        if (a.vertexNum > b.vertexNum) {
            return 1
        }
        return 0
    }
}

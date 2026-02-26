package neo.Renderer

import neo.Renderer.Interaction.srfCullInfo_t
import neo.Renderer.Model.shadowCache_s
import neo.Renderer.Model.silEdge_t
import neo.Renderer.Model.srfTriangles_s
import neo.idlib.geometry.DrawVert
import neo.idlib.math.SIMDProcessor
import neo.idlib.math.idVec3
import neo.idlib.math.idVec4


object tr_turboshadow {
    var c_turboUnusedVerts: Int = 0

    /*
     ============================================================

     TR_TURBOSHADOW

     Fast, non-clipped overshoot shadow volumes

     "facing" should have one more element than tri->numIndexes / 3, which should be set to 1
     calling this function may modify "facing" based on culling

     ============================================================
     */
    var c_turboUsedVerts: Int = 0

    /*
     =====================
     R_CreateVertexProgramTurboShadowVolume

     are dangling edges that are outside the light frustum still making planes?
     =====================
     */
    fun R_CreateVertexProgramTurboShadowVolume(
        ent: idRenderEntityLocal?,
        tri: srfTriangles_s,
        light: idRenderLightLocal?,
        cullInfo: srfCullInfo_t
    ): srfTriangles_s? {
        var i: Int
        var j: Int
        val newTri: srfTriangles_s
        var sil: Int
        var indexes: IntArray?
        val facing: ByteArray?
        Interaction.R_CalcInteractionFacing(ent, tri, light, cullInfo)
        if (r_useShadowProjectedCull!!.GetBool()) {
            Interaction.R_CalcInteractionCullBits(ent, tri, light, cullInfo)
        }
        val numFaces: Int = tri.numIndexes / 3
        var numShadowingFaces = 0
        facing = cullInfo.facing

        // if all the triangles are inside the light frustum
        if (cullInfo.cullBits.contentEquals(Interaction.LIGHT_CULL_ALL_FRONT) || !r_useShadowProjectedCull!!.GetBool()) {

            // count the number of shadowing faces
            i = 0
            while (i < numFaces) {
                numShadowingFaces += facing!![i].toInt()
                i++
            }
            numShadowingFaces = numFaces - numShadowingFaces
        } else {

            // make all triangles that are outside the light frustum "facing", so they won't cast shadows
            indexes = tri.indexes
            val modifyFacing: ByteArray? = cullInfo.facing
            val cullBits: ByteArray? = cullInfo.cullBits
            j = 0.also({ i = it })
            while (i < tri.numIndexes) {
                if (0 == modifyFacing!![j].toInt()) {
                    val i1: Int = indexes!![i + 0]
                    val i2: Int = indexes[i + 1]
                    val i3: Int = indexes[i + 2]
                    if ((cullBits!![i1].toInt() and cullBits[i2].toInt() and cullBits[i3].toInt()) != 0) {
                        modifyFacing[j] = 1
                    } else {
                        numShadowingFaces++
                    }
                }
                i += 3
                j++
            }
        }
        if (0 == numShadowingFaces) {
            // no faces are inside the light frustum and still facing the right way
            return null
        }

        // shadowVerts will be NULL on these surfaces, so the shadowVerts will be taken from the ambient surface
        newTri = R_AllocStaticTriSurf()
        newTri.numVerts = tri.numVerts * 2

        // alloc the max possible size
        val tempIndexes: IntArray
        var shadowIndexes: IntArray
        if (USE_TRI_DATA_ALLOCATOR) {
            R_AllocStaticTriSurfIndexes(newTri, (numShadowingFaces + tri.numSilEdges) * 6)
            tempIndexes = newTri.indexes!!
            shadowIndexes = newTri.indexes!!
        } else {
            tempIndexes = IntArray(tri.numSilEdges * 6)
            shadowIndexes = tempIndexes
        }
        var shadowIndex = 0
        // create new triangles along sil planes
        sil = 0
        i = tri.numSilEdges
        while (i > 0) {
            val f1: Int = facing!![tri.silEdges!![sil]!!.p1].toInt()
            val f2: Int = facing[tri.silEdges!![sil]!!.p2].toInt()
            if (0 == (f1 xor f2)) {
                i--
                sil++
                continue
            }
            val v1: Int = tri.silEdges!![sil]!!.v1 shl 1
            val v2: Int = tri.silEdges!![sil]!!.v2 shl 1

            // set the two triangle winding orders based on facing
            // without using a poorly-predictable branch
            shadowIndexes[shadowIndex + 0] = v1
            shadowIndexes[shadowIndex + 1] = v2 xor f1
            shadowIndexes[shadowIndex + 2] = v2 xor f2
            shadowIndexes[shadowIndex + 3] = v1 xor f2
            shadowIndexes[shadowIndex + 4] = v1 xor f1
            shadowIndexes[shadowIndex + 5] = v2 xor 1
            shadowIndex += 6
            i--
            sil++
        }
        val numShadowIndexes: Int = shadowIndex //shadowIndexes - tempIndexes;

        // we aren't bothering to separate front and back caps on these
        newTri.numShadowIndexesNoFrontCaps = numShadowIndexes + numShadowingFaces * 6
        newTri.numIndexes = newTri.numShadowIndexesNoFrontCaps
        newTri.numShadowIndexesNoCaps = numShadowIndexes
        newTri.shadowCapPlaneBits = Model.SHADOW_CAP_INFINITE
        if (USE_TRI_DATA_ALLOCATOR) {
            // decrease the size of the memory block to only store the used indexes
            R_ResizeStaticTriSurfIndexes(newTri, newTri.numIndexes)
        } else {
            // allocate memory for the indexes
            R_AllocStaticTriSurfIndexes(newTri, newTri.numIndexes)
            // copy the indexes we created for the sil planes
            SIMDProcessor!!.Memcpy(newTri.indexes!!, tempIndexes, numShadowIndexes /* sizeof( tempIndexes[0] )*/)
        }

        // these have no effect, because they extend to infinity
        newTri.bounds.Clear()

        // put some faces on the model and some on the distant projection
        indexes = tri.indexes
        shadowIndex = numShadowIndexes
        shadowIndexes = newTri.indexes!!
        i = 0
        j = 0
        while (i < tri.numIndexes) {
            if (facing!![j].toInt() != 0) {
                i += 3
                j++
                continue
            }
            val i0: Int = indexes!![i + 0] shl 1
            shadowIndexes[shadowIndex + 2] = i0
            shadowIndexes[shadowIndex + 3] = i0 xor 1
            val i1: Int = indexes[i + 1] shl 1
            shadowIndexes[shadowIndex + 1] = i1
            shadowIndexes[shadowIndex + 4] = i1 xor 1
            val i2: Int = indexes[i + 2] shl 1
            shadowIndexes[shadowIndex + 0] = i2
            shadowIndexes[shadowIndex + 5] = i2 xor 1
            shadowIndex += 6
            i += 3
            j++
        }
        return newTri
    }

    /*
     =====================
     R_CreateTurboShadowVolume
     =====================
     */
    fun R_CreateTurboShadowVolume(
        ent: idRenderEntityLocal,
        tri: srfTriangles_s,
        light: idRenderLightLocal,
        cullInfo: srfCullInfo_t
    ): srfTriangles_s? {
        var i: Int
        var j: Int
        val localLightOrigin = idVec3()
        val newTri: srfTriangles_s
        var sil: silEdge_t?
        var indexes: IntArray?
        val facing: ByteArray?
        Interaction.R_CalcInteractionFacing(ent, tri, light, cullInfo)
        if (r_useShadowProjectedCull!!.GetBool()) {
            Interaction.R_CalcInteractionCullBits(ent, tri, light, cullInfo)
        }
        val numFaces: Int = tri.numIndexes / 3
        var numShadowingFaces = 0
        facing = cullInfo.facing

        // if all the triangles are inside the light frustum
        if (cullInfo.cullBits.contentEquals(Interaction.LIGHT_CULL_ALL_FRONT) || !r_useShadowProjectedCull!!.GetBool()) {

            // count the number of shadowing faces
            i = 0
            while (i < numFaces) {
                numShadowingFaces += facing!![i].toInt()
                i++
            }
            numShadowingFaces = numFaces - numShadowingFaces
        } else {

            // make all triangles that are outside the light frustum "facing", so they won't cast shadows
            indexes = tri.indexes
            val modifyFacing: ByteArray? = cullInfo.facing
            val cullBits: ByteArray? = cullInfo.cullBits
            j = 0.also({ i = it })
            while (i < tri.numIndexes) {
                if (0 == modifyFacing!![j].toInt()) {
                    val i1: Int = indexes!![i + 0]
                    val i2: Int = indexes[i + 1]
                    val i3: Int = indexes[i + 2]
                    if ((cullBits!![i1].toInt() and cullBits[i2].toInt() and cullBits[i3].toInt()) != 0) {
                        modifyFacing[j] = 1
                    } else {
                        numShadowingFaces++
                    }
                }
                i += 3
                j++
            }
        }
        if (0 == numShadowingFaces) {
            // no faces are inside the light frustum and still facing the right way
            return null
        }
        newTri = R_AllocStaticTriSurf()
        val shadowVerts: Array<shadowCache_s?>
        if (USE_TRI_DATA_ALLOCATOR) {
            R_AllocStaticTriSurfShadowVerts(newTri, tri.numVerts * 2)
            shadowVerts = newTri.shadowVertexes as Array<shadowCache_s?>
        } else {
            shadowVerts = shadowCache_s.generateArray(tri.numVerts * 2) as Array<shadowCache_s?>
        }
        tr_main.R_GlobalPointToLocal(ent.modelMatrix, light.globalLightOrigin, localLightOrigin)
        val vertRemap = IntArray(tri.numVerts)
        SIMDProcessor!!.Memset(vertRemap, -1, tri.numVerts /* sizeof(vertRemap[0])*/)
        i = 0
        j = 0
        while (i < tri.numIndexes) {
            if (facing!![j].toInt() != 0) {
                i += 3
                j++
                continue
            }
            // this may pull in some vertexes that are outside
            // the frustum, because they connect to vertexes inside
            vertRemap[tri.silIndexes!![i + 0]] = 0
            vertRemap[tri.silIndexes!![i + 1]] = 0
            vertRemap[tri.silIndexes!![i + 2]] = 0
            i += 3
            j++
        }
        run({
            val shadows: Array<idVec4?> = arrayOfNulls(shadowVerts.size)
            for (a in shadows.indices) {
                shadows[a] = shadowVerts[a]!!.xyz
            }
            newTri.numVerts =
                SIMDProcessor!!.CreateShadowCache(
                    shadows as Array<idVec4>,
                    vertRemap,
                    localLightOrigin,
                    tri.verts as Array<DrawVert.idDrawVert>,
                    tri.numVerts
                )
        })
        c_turboUsedVerts += newTri.numVerts
        c_turboUnusedVerts += tri.numVerts * 2 - newTri.numVerts
        if (USE_TRI_DATA_ALLOCATOR) {
            R_ResizeStaticTriSurfShadowVerts(newTri, newTri.numVerts)
        } else {
            R_AllocStaticTriSurfShadowVerts(newTri, newTri.numVerts)
            SIMDProcessor!!.Memcpy(newTri.shadowVertexes!!, shadowVerts, newTri.numVerts /* sizeof( shadowVerts[0] ) */)
        }

        // alloc the max possible size
        val tempIndexes: IntArray
        var shadowIndexes: IntArray
        if (USE_TRI_DATA_ALLOCATOR) {
            R_AllocStaticTriSurfIndexes(newTri, (numShadowingFaces + tri.numSilEdges) * 6)
            tempIndexes = newTri.indexes!!
            shadowIndexes = newTri.indexes!!
        } else {
            tempIndexes = IntArray(tri.numSilEdges * 6)
            shadowIndexes = tempIndexes
        }
        var sil_index = 0
        var shadowIndex = 0
        // create new triangles along sil planes
        sil = tri.silEdges!![sil_index]
        i = tri.numSilEdges
        while (i > 0) {
            val f1: Int = facing!![sil!!.p1].toInt()
            val f2: Int = facing[sil.p2].toInt()
            if (0 == (f1 xor f2)) {
                i--
                sil = tri.silEdges!![++sil_index]
                continue
            }
            val v1: Int = vertRemap[sil.v1]
            val v2: Int = vertRemap[sil.v2]

            // set the two triangle winding orders based on facing
            // without using a poorly-predictable branch
            shadowIndexes[shadowIndex + 0] = v1
            shadowIndexes[shadowIndex + 1] = v2 xor f1
            shadowIndexes[shadowIndex + 2] = v2 xor f2
            shadowIndexes[shadowIndex + 3] = v1 xor f2
            shadowIndexes[shadowIndex + 4] = v1 xor f1
            shadowIndexes[shadowIndex + 5] = v2 xor 1
            shadowIndex += 6
            i--
            sil = tri.silEdges!![++sil_index]
        }
        val numShadowIndexes: Int = shadowIndex

        // we aren't bothering to separate front and back caps on these
        newTri.numShadowIndexesNoFrontCaps = numShadowIndexes + numShadowingFaces * 6
        newTri.numIndexes = newTri.numShadowIndexesNoFrontCaps
        newTri.numShadowIndexesNoCaps = numShadowIndexes
        newTri.shadowCapPlaneBits = Model.SHADOW_CAP_INFINITE
        if (USE_TRI_DATA_ALLOCATOR) {
            // decrease the size of the memory block to only store the used indexes
            R_ResizeStaticTriSurfIndexes(newTri, newTri.numIndexes)
        } else {
            // allocate memory for the indexes
            R_AllocStaticTriSurfIndexes(newTri, newTri.numIndexes)
            // copy the indexes we created for the sil planes
            SIMDProcessor!!.Memcpy(newTri.indexes!!, tempIndexes, numShadowIndexes /* sizeof( tempIndexes[0] )*/)
        }

        // these have no effect, because they extend to infinity
        newTri.bounds.Clear()

        // put some faces on the model and some on the distant projection
        indexes = tri.silIndexes
        shadowIndex = numShadowIndexes
        shadowIndexes = newTri.indexes!!
        i = 0
        j = 0
        while (i < tri.numIndexes) {
            if (facing!![j].toInt() != 0) {
                i += 3
                j++
                continue
            }
            val i0: Int = vertRemap[indexes!![i + 0]]
            shadowIndexes[shadowIndex + 2] = i0
            shadowIndexes[shadowIndex + 3] = i0 xor 1
            val i1: Int = vertRemap[indexes[i + 1]]
            shadowIndexes[shadowIndex + 1] = i1
            shadowIndexes[shadowIndex + 4] = i1 xor 1
            val i2: Int = vertRemap[indexes[i + 2]]
            shadowIndexes[shadowIndex + 0] = i2
            shadowIndexes[shadowIndex + 5] = i2 xor 1
            shadowIndex += 6
            i += 3
            j++
        }
        return newTri
    }
}

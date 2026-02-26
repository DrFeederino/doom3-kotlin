package neo.Renderer

import neo.framework.Common
import neo.idlib.idException

object tr_orderIndexes {
    /*
     ===============
     R_MeshCost
     ===============
     */
    val CACHE_SIZE: Int = 24
    val STALL_SIZE: Int = 8
    fun R_MeshCost(numIndexes: Int, indexes: IntArray): Int {
        val inCache: IntArray = IntArray(CACHE_SIZE)
        var i: Int
        var j: Int
        var v: Int
        var c_stalls: Int
        var c_loads: Int
        var fifo: Int
        i = 0
        while (i < CACHE_SIZE) {
            inCache[i] = -1
            i++
        }
        c_loads = 0
        c_stalls = 0
        fifo = 0
        i = 0
        while (i < numIndexes) {
            v = indexes[i]
            j = 0
            while (j < CACHE_SIZE) {
                if (inCache[(fifo + j) % CACHE_SIZE] == v) {
                    break
                }
                j++
            }
            if (j == CACHE_SIZE) {
                c_loads++
                inCache[fifo % CACHE_SIZE] = v
                fifo++
            } else if (j < STALL_SIZE) {
                c_stalls++
            }
            i++
        }
        return c_loads
    }

    /*
     ====================
     R_OrderIndexes

     Reorganizes the indexes so they will take best advantage
     of the internal GPU vertex caches
     ====================
     */
    @Throws(idException::class)
    fun R_OrderIndexes(numIndexes: Int, indexes: IntArray) {
        var numIndexes: Int = numIndexes
        val triangleUsed: BooleanArray
        val numTris: Int
        val oldIndexes: IntArray
        var base: IntArray
        var base_index: Int
        val numOldIndexes: Int
        var tri: Int
        var i: Int
        var vref: vertRef_s?
        val vrefs: Array<vertRef_s?>
        val vrefTable: Array<vertRef_s?>
        var numVerts: Int
        var v1: Int
        var v2: Int
        var c_starts: Int
        val c_cost: Int
        if (!r_orderIndexes!!.GetBool()) {
            return
        }

        // save off the original indexes
        oldIndexes = IntArray(numIndexes)
        //	memcpy( oldIndexes, indexes, numIndexes * sizeof( *oldIndexes ) );
        System.arraycopy(indexes, 0, oldIndexes, 0, numIndexes)
        numOldIndexes = numIndexes

        // make a table to mark the triangles when they are emited
        numTris = numIndexes / 3
        triangleUsed = BooleanArray(numTris)
        //	memset( triangleUsed, 0, numTris * sizeof( *triangleUsed ) );

        // find the highest vertex number
        numVerts = 0
        i = 0
        while (i < numIndexes) {
            if (indexes[i] > numVerts) {
                numVerts = indexes[i]
            }
            i++
        }
        numVerts++

        // create a table of triangles used by each vertex
        vrefs = arrayOfNulls(numVerts)
        //	memset( vrefs, 0, numVerts * sizeof( *vrefs ) );
        vrefTable = Array(numIndexes) { vertRef_s() }
        i = 0
        while (i < numIndexes) {
            tri = i / 3
            vrefTable[i]!!.tri = tri
            vrefTable[i]!!.next = vrefs[oldIndexes[i]]
            vrefs[oldIndexes[i]] = vrefTable[i]
            i++
        }

        // generate new indexes
        numIndexes = 0
        c_starts = 0
        while (numIndexes != numOldIndexes) {
            // find a triangle that hasn't been used
            tri = 0
            while (tri < numTris) {
                if (!triangleUsed[tri]) {
                    break
                }
                tri++
            }
            if (tri == numTris) {
                Common.common.Error("R_OrderIndexes: ran out of unused tris")
            }
            c_starts++
            do {
                // emit this tri
                base = oldIndexes //[tri * 3];
                base_index = tri * 3
                indexes[numIndexes + 0] = base[base_index + 0]
                indexes[numIndexes + 1] = base[base_index + 1]
                indexes[numIndexes + 2] = base[base_index + 2]
                numIndexes += 3
                triangleUsed[tri] = true

                // try to find a shared edge to another unused tri
                i = 0
                while (i < 3) {
                    v1 = base[base_index + i]
                    v2 = base[base_index + ((i + 1) % 3)]
                    vref = vrefs[v1]
                    while (vref != null) {
                        tri = vref.tri
                        if (triangleUsed[tri]) {
                            vref = vref.next
                            continue
                        }

                        // if this triangle also uses v2, grab it
                        if ((oldIndexes[tri * 3 + 0] == v2
                                    ) || (oldIndexes[tri * 3 + 1] == v2
                                    ) || (oldIndexes[tri * 3 + 2] == v2)
                        ) {
                            break
                        }
                        vref = vref.next
                    }
                    if (vref != null) {
                        break
                    }
                    i++
                }

                // if we couldn't chain off of any verts, we need to find a new one
                if (i == 3) {
                    break
                }
            } while (true)
        }
        c_cost = R_MeshCost(numIndexes, indexes)
    }

    internal class vertRef_s {
        var next: vertRef_s? = null
        var tri: Int = 0
    } /*

     add all triangles that can be specified by the vertexes in the last 14 cache positions

     pick a new vert to add to the cache
     don't pick one in the 24 previous cache positions
     try to pick one that will enable the creation of as many triangles as possible

     look for a vert that shares an edge with the vert about to be evicted


     */
}

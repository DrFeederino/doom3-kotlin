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
import neo.idlib.idException

object tr_orderIndexes {

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
        if (!r_orderIndexes!!.GetBool()) {
            return
        }

        // save off the original indexes
        oldIndexes = IntArray(numIndexes)
        System.arraycopy(indexes, 0, oldIndexes, 0, numIndexes)
        numOldIndexes = numIndexes

        // make a table to mark the triangles when they are emited
        numTris = numIndexes / 3
        triangleUsed = BooleanArray(numTris)

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
                base = oldIndexes
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
    }

    internal class vertRef_s {
        var next: vertRef_s? = null
        var tri: Int = 0
    }
}

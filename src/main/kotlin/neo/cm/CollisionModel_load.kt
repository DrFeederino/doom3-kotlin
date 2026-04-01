/*
===========================================================================

Doom 3 GPL Source Code
Copyright (C) 1999-2011 id Software LLC, a ZeniMax Media company.

Translated to Kotlin by Dr. Feederino with support of Claude Code

This file is part of the Doom 3 Kotlin project.
Original source: neo/cm/CollisionModel_load.cpp

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

===========================================================================
*/

/*
===============================================================================

	Trace model vs. polygonal model collision detection.

	Standalone helper functions for collision model loading. The main loading
	methods are in CollisionModel_local.kt as methods of
	idCollisionModelManagerLocal.

===============================================================================
*/

package neo.cm

import neo.cm.AbstractCollisionModel_local.*
import neo.cm.AbstractCollisionModel_local.Companion.MAX_NODE_POLYGONS
import neo.cm.AbstractCollisionModel_local.Companion.MIN_NODE_SIZE
import neo.idlib.BV.idBounds
import neo.idlib.MapFile.idMapBrush
import neo.idlib.MapFile.idMapEntity
import neo.idlib.MapFile.idMapPatch
import neo.idlib.MapFile.idMapPrimitive
import neo.idlib.containers.CFloat
import neo.idlib.containers.CInt
import kotlin.math.abs

/*
===============================================================================

Spatial subdivision

===============================================================================
*/

/*
================
CM_FindSplitter
================
*/
fun CM_FindSplitter(node: cm_node_s, bounds: idBounds, planeType: CInt, planeDist: CFloat): Boolean {
    var i: Int
    var j: Int
    var type: Int
    var polyCount: Int
    val axis = IntArray(3)
    var dist: Float
    var t: Float
    var bestt: Float
    val size = FloatArray(3)
    var bref: cm_brushRef_s?
    var pref: cm_polygonRef_s?
    var n: cm_node_s?
    var forceSplit = false

    for (idx in 0 until 3) {
        size[idx] = bounds[1, idx] - bounds[0, idx]
        axis[idx] = idx
    }

    // sort on largest axis
    i = 0
    while (i < 2) {
        if (size[i] < size[i + 1]) {
            t = size[i]
            size[i] = size[i + 1]
            size[i + 1] = t
            j = axis[i]
            axis[i] = axis[i + 1]
            axis[i + 1] = j
            i = -1
        }
        i++
    }

    // if the node is too small for further splits
    if (size[0] < MIN_NODE_SIZE) {
        polyCount = 0
        pref = node.polygons
        while (pref != null) {
            polyCount++
            pref = pref.next
        }
        if (polyCount > MAX_NODE_POLYGONS) {
            forceSplit = true
        }
    }

    // find an axial aligned splitter
    for (idx in 0 until 3) {
        // start with the largest axis first
        type = axis[idx]
        bestt = size[idx]

        // if the node is small anough in this axis direction
        if (!forceSplit && bestt < MIN_NODE_SIZE) {
            break
        }

        // find an axial splitter from the brush bounding boxes
        // also try brushes from parent nodes
        n = node
        while (n != null) {
            bref = n.brushes
            while (bref != null) {
                for (jj in 0 until 2) {
                    dist = bref.b!!.bounds[jj, type]
                    // if the splitter is already used or outside node bounds
                    if (dist >= bounds[1, type] || dist <= bounds[0, type]) {
                        continue
                    }
                    // find the most centered splitter
                    t = abs((bounds[1, type] - dist) - (dist - bounds[0, type]))
                    if (t < bestt) {
                        bestt = t
                        planeType._val = type
                        planeDist._val = dist
                    }
                }
                bref = bref.next
            }
            n = n.parent
        }

        // find an axial splitter from the polygon bounding boxes
        // also try polygons from parent nodes
        n = node
        while (n != null) {
            pref = n.polygons
            while (pref != null) {
                for (jj in 0 until 2) {
                    dist = pref.p!!.bounds[jj, type]
                    // if the splitter is already used or outside node bounds
                    if (dist >= bounds[1, type] || dist <= bounds[0, type]) {
                        continue
                    }
                    // find the most centered splitter
                    t = abs((bounds[1, type] - dist) - (dist - bounds[0, type]))
                    if (t < bestt) {
                        bestt = t
                        planeType._val = type
                        planeDist._val = dist
                    }
                }
                pref = pref.next
            }
            n = n.parent
        }

        // if we found a splitter on the largest axis
        if (bestt < size[idx]) {
            // if forced split due to lots of polygons
            if (forceSplit) {
                return true
            }
            // don't create splitters real close to the bounds
            if (bounds[1, type] - planeDist._val > MIN_NODE_SIZE * 0.5f
                && planeDist._val - bounds[0, type] > MIN_NODE_SIZE * 0.5f
            ) {
                return true
            }
            // FIX: Removed erroneous `break` that was not in the C++ original.
            // C++ continues to next axis to try finding a better splitter.
        }
    }
    return false
}

/*
================
CM_R_InsideAllChildren
================
*/
fun CM_R_InsideAllChildren(node: cm_node_s, bounds: idBounds): Boolean {
    assert(node != null)
    if (node.planeType != -1) {
        if (bounds[0, node.planeType] >= node.planeDist) {
            return false
        }
        if (bounds[1, node.planeType] <= node.planeDist) {
            return false
        }
        if (!CM_R_InsideAllChildren(node.children[0]!!, bounds)) {
            return false
        }
        if (!CM_R_InsideAllChildren(node.children[1]!!, bounds)) {
            return false
        }
    }
    return true
}

/*
===============================================================================

Raw polygon and brush data

===============================================================================
*/

/*
=================
CM_EstimateVertsAndEdges
=================
*/
fun CM_EstimateVertsAndEdges(mapEnt: idMapEntity, numVerts: CInt, numEdges: CInt) {
    var width: Int
    var height: Int
    numVerts._val = 0
    numEdges._val = 0
    for (j in 0 until mapEnt.GetNumPrimitives()) {
        val mapPrim: idMapPrimitive = mapEnt.GetPrimitive(j)
        if (mapPrim.GetType() == idMapPrimitive.TYPE_PATCH) {
            // assume maximum tesselation without adding verts
            width = (mapPrim as idMapPatch).GetWidth()
            height = mapPrim.GetHeight()
            numVerts._val = width * height + numVerts._val
            numEdges._val =
                (width - 1) * height + width * (height - 1) + (width - 1) * (height - 1) + numEdges._val
            continue
        }
        if (mapPrim.GetType() == idMapPrimitive.TYPE_BRUSH) {
            // assume cylinder with a polygon with (numSides - 2) edges ontop and on the bottom
            numVerts._val = ((mapPrim as idMapBrush).GetNumSides() - 2) * 2 + numVerts._val
            numEdges._val = (mapPrim.GetNumSides() - 2) * 3 + numEdges._val
            continue
        }
    }
}

/*
================
CM_CountNodeBrushes
================
*/
fun CM_CountNodeBrushes(node: cm_node_s): Int {
    var count: Int
    var bref: cm_brushRef_s?
    count = 0
    bref = node.brushes
    while (bref != null) {
        count++
        bref = bref.next
    }
    return count
}

/*
================
CM_R_GetNodeBounds
================
*/
fun CM_R_GetNodeBounds(bounds: idBounds, node: cm_node_s) {
    var currentNode = node
    var pref: cm_polygonRef_s?
    var bref: cm_brushRef_s?
    while (true) {
        pref = currentNode.polygons
        while (pref != null) {
            bounds.AddPoint(pref.p!!.bounds[0])
            bounds.AddPoint(pref.p!!.bounds[1])
            pref = pref.next
        }
        bref = currentNode.brushes
        while (bref != null) {
            bounds.AddPoint(bref.b!!.bounds[0])
            bounds.AddPoint(bref.b!!.bounds[1])
            bref = bref.next
        }
        if (currentNode.planeType == -1) {
            break
        }
        CM_R_GetNodeBounds(bounds, currentNode.children[1]!!)
        currentNode = currentNode.children[0]!!
    }
}

/*
================
CM_GetNodeBounds
================
*/
fun CM_GetNodeBounds(bounds: idBounds, node: cm_node_s) {
    bounds.Clear()
    CM_R_GetNodeBounds(bounds, node)
    if (bounds.IsCleared()) {
        bounds.Zero()
    }
}

/*
================
CM_GetNodeContents
================
*/
fun CM_GetNodeContents(node: cm_node_s): Int {
    var currentNode = node
    var contents: Int
    var pref: cm_polygonRef_s?
    var bref: cm_brushRef_s?
    contents = 0
    while (true) {
        pref = currentNode.polygons
        while (pref != null) {
            contents = contents or pref.p!!.contents
            pref = pref.next
        }
        bref = currentNode.brushes
        while (bref != null) {
            contents = contents or bref.b!!.contents
            bref = bref.next
        }
        if (currentNode.planeType == -1) {
            break
        }
        contents = contents or CM_GetNodeContents(currentNode.children[1]!!)
        currentNode = currentNode.children[0]!!
    }
    return contents
}

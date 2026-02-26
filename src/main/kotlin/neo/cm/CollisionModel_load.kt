package neo.cm

import neo.cm.AbstractCollisionModel_local.*
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
    for (i in 0 until 3) {
        size[i] = bounds[1, i] - bounds[0, i]
        axis[i] = i
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
    if (size[0] < Companion.MIN_NODE_SIZE) {
        polyCount = 0
        pref = node.polygons
        while (pref != null) {
            polyCount++
            pref = pref.next
        }
        if (polyCount > Companion.MAX_NODE_POLYGONS) {
            forceSplit = true
        }
    }
    // find an axial aligned splitter
    for (i in 0 until 3) {
        // start with the largest axis first
        type = axis[i]
        bestt = size[i]
        // if the node is small anough in this axis direction
        if (!forceSplit && bestt < Companion.MIN_NODE_SIZE) {
            break
        }
        // find an axial splitter from the brush bounding boxes
        // also try brushes from parent nodes
        n = node
        while (n != null) {
            bref = n.brushes
            while (bref != null) {
                for (j in 0 until 2) {
                    dist = bref.b!!.bounds[j, type]
                    // if the splitter is already used or outside node bounds
                    if (dist >= bounds[1, type] || dist <= bounds[0, type]) {
                        continue
                    }
                    // find the most centered splitter
                    t = abs(bounds[1, type] - dist - (dist - bounds[0, type]))
                    if (t < bestt) {
                        bestt = t
                        planeType._val = (type)
                        planeDist._val = (dist)
                    }
                }
                bref = bref.next
            }
            n = n.parent
        }
        // find an axial splitter from the polygon bounding boxes
        // also try brushes from parent nodes
        n = node
        while (n != null) {
            pref = n.polygons
            while (pref != null) {
                j = 0
                while (j < 2) {
                    dist = pref.p!!.bounds[j, type]
                    // if the splitter is already used or outside node bounds
                    if (dist >= bounds[1, type] || dist <= bounds[0, type]) {
                        j++
                        continue
                    }
                    // find the most centered splitter
                    t = abs(bounds[1, type] - dist - (dist - bounds[0, type]))
                    if (t < bestt) {
                        bestt = t
                        planeType._val = (type)
                        planeDist._val = (dist)
                    }
                    j++
                }
                pref = pref.next
            }
            n = n.parent
        }
        // if we found a splitter on the largest axis
        if (bestt < size[i]) {
            // if forced split due to lots of polygons
            if (forceSplit) {
                return true
            }
            // don't create splitters real close to the bounds
            if (bounds[1, type] - planeDist._val > Companion.MIN_NODE_SIZE * 0.5f
                && planeDist._val - bounds[0, type] > Companion.MIN_NODE_SIZE * 0.5f
            ) {
                return true
            }
            // Add this: If we found a good splitter but didn't return, continue to next axis
            break  // Break out to try next axis
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
        return if (!CM_R_InsideAllChildren(node.children[0]!!, bounds)) {
            false
        } else CM_R_InsideAllChildren(node.children[1]!!, bounds)
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
 CM_R_GetModelBounds
 ================
 */
fun CM_R_GetNodeBounds(bounds: idBounds, node: cm_node_s) {
    var node = node
    var pref: cm_polygonRef_s?
    var bref: cm_brushRef_s?
    while (true) {
        pref = node.polygons
        while (pref != null) {
            bounds.AddPoint(pref.p!!.bounds[0])
            bounds.AddPoint(pref.p!!.bounds[1])
            pref = pref.next
        }
        bref = node.brushes
        while (bref != null) {
            bounds.AddPoint(bref.b!!.bounds[0])
            bounds.AddPoint(bref.b!!.bounds[1])
            bref = bref.next
        }
        if (node.planeType == -1) {
            break
        }
        CM_R_GetNodeBounds(bounds, node.children[1]!!)
        node = node.children[0]!!
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

package neo.Tools.Compilers.AAS

import neo.idlib.BV.idBounds
import neo.idlib.containers.idHashIndex

const val AAS_PLANE_DIST_EPSILON = 0.01f
const val AAS_PLANE_NORMAL_EPSILON = 0.00001f
const val EDGE_HASH_SIZE = 1 shl 14
const val INTEGRAL_EPSILON = 0.01f
const val VERTEX_EPSILON = 0.1f
const val VERTEX_HASH_BOXSIZE = 1 shl 6 // must be power of 2
const val VERTEX_HASH_SIZE = VERTEX_HASH_BOXSIZE * VERTEX_HASH_BOXSIZE
var aas_edgeHash: idHashIndex? = null
val aas_vertexBounds: idBounds = idBounds()
var aas_vertexHash: idHashIndex? = null
var aas_vertexShift = 0

internal class sizeEstimate_s {
    var numAreas = 0
    var numEdgeIndexes = 0
    var numFaceIndexes = 0
    var numNodes = 0
}

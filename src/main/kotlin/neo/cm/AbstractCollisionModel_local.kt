package neo.cm

import neo.Renderer.Material.idMaterial
import neo.idlib.BV.idBounds
import neo.idlib.Text.Str.idStr
import neo.idlib.geometry.TraceModel
import neo.idlib.geometry.Winding.idFixedWinding
import neo.idlib.math.Matrix.idMat3
import neo.idlib.math.idPlane
import neo.idlib.math.idPluecker
import neo.idlib.math.idRotation
import neo.idlib.math.idVec3

/*
    Represents CollisionModel.h
 */
abstract class AbstractCollisionModel_local {
    class cm_windingList_s {
        val bounds: idBounds = idBounds() // bounds of all windings in list
        var contents = 0 // winding surface contents
        val normal: idVec3 = idVec3() // normal for all windings
        var numWindings = 0 // number of windings
        val origin: idVec3 = idVec3() // origin for radius
        var primitiveNum = 0// number of primitive the windings came from
        var radius = 0.0f // radius relative to origin for all windings
        var w: Array<idFixedWinding?> = arrayOfNulls<idFixedWinding?>(MAX_WINDING_LIST) // windings
    }

    /*
     ===============================================================================

     Collision model

     ===============================================================================
     */
    class cm_vertex_s {
        var checkcount: Int = 0 // for multi-check avoidance
        val p: idVec3 = idVec3() // vertex point
        var side: Long = 0// each bit tells at which side this vertex passes one of the trace model edges
        var sideSet: Long = 0 // each bit tells if sidedness for the trace model edge has been calculated yet

        companion object {
            val SIZE: Int = idVec3.SIZE + Integer.SIZE + java.lang.Long.SIZE + java.lang.Long.SIZE
            val BYTES = SIZE / java.lang.Byte.SIZE

            fun generateArray(length: Int): Array<cm_vertex_s> {
                return Array(length) { cm_vertex_s() }
            }
        }
    }

    class cm_edge_s {
        var checkcount: Int = 0 // for multi-check avoidance
        var internal = false // a trace model can never collide with internal edges
        val normal: idVec3 = idVec3() // edge normal
        var numUsers: Short = 0 // number of polygons using this edge
        var side: Long = 0// each bit tells at which side of this edge one of the trace model vertices passes
        var sideSet: Long = 0// each bit tells if sidedness for the trace model vertex has been calculated yet
        var vertexNum: IntArray = IntArray(2) // start and end point of edge

        companion object {
            val SIZE: Int =
                Integer.SIZE + java.lang.Short.SIZE + java.lang.Short.SIZE + java.lang.Long.SIZE + java.lang.Long.SIZE + Integer.SIZE + idVec3.SIZE
            val BYTES = SIZE / java.lang.Byte.SIZE

            fun generateArray(length: Int): Array<cm_edge_s> {
                return Array(length) { cm_edge_s() }
            }
        }
    }

    class cm_polygonBlock_s {
        var bytesRemaining: Int = 0
        var next: cm_polygonBlock_s? = null
    }

    class cm_polygon_s {
        val bounds: idBounds = idBounds() // polygon bounds
        var checkcount: Int = 0 // for multi-check avoidance
        var contents: Int = 0 // contents behind polygon
        var edges: IntArray = IntArray(1) // variable sized, indexes into cm_edge_t list
        var material: idMaterial? = null// material
        var numEdges: Int = 0 // number of edges
        val plane: idPlane = idPlane() // polygon plane

        companion object {
            val BYTES: Int =
                (idBounds.BYTES + Integer.BYTES + Integer.BYTES + Integer.BYTES + idPlane.BYTES + Integer.BYTES + Integer.BYTES)
        }

        fun oSet(p: cm_polygon_s) {
            bounds.set(p.bounds)
            checkcount = p.checkcount
            contents = p.contents
            material = p.material
            plane.set(p.plane)
            numEdges = p.numEdges
            edges = p.edges.copyOf(numEdges)
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as cm_polygon_s

            if (bounds != other.bounds) return false
            if (checkcount != other.checkcount) return false
            if (contents != other.contents) return false
            if (!edges.contentEquals(other.edges)) return false
            if (material != other.material) return false
            if (numEdges != other.numEdges) return false
            if (plane != other.plane) return false

            return true
        }

        override fun hashCode(): Int {
            var result = bounds.hashCode()
            result = 31 * result + checkcount
            result = 31 * result + contents
            result = 31 * result + edges.contentHashCode()
            result = 31 * result + (material?.hashCode() ?: 0)
            result = 31 * result + numEdges
            result = 31 * result + plane.hashCode()
            return result
        }

    }

    class cm_polygonRef_s {
        var next: cm_polygonRef_s? = null // next polygon in chain
        var p: cm_polygon_s? = null // pointer to polygon

        companion object {
            val BYTES = cm_polygon_s.BYTES + Integer.BYTES
        }
    }

    class cm_polygonRefBlock_s {
        var next: cm_polygonRefBlock_s? = null// next block with polygon references
        var nextRef: cm_polygonRef_s? = null// next polygon reference in block
    }

    class cm_brushBlock_s {
        var bytesRemaining: Int = 0
        var next: cm_brushBlock_s? = null
    }

    class cm_brush_s {
        val bounds: idBounds = idBounds()// brush bounds
        var checkcount: Int = 0// for multi-check avoidance
        var contents: Int = 0// contents of brush
        var material: idMaterial? = null // material
        var numPlanes = 0 // number of bounding planes
        var planes: Array<idPlane> = idPlane.generateArray(1) // variable sized
        var primitiveNum = 0 // number of brush primitive

        companion object {
            val BYTES: Int =
                (Integer.BYTES + idBounds.BYTES + Integer.BYTES + Integer.BYTES + Integer.BYTES + Integer.BYTES + idPlane.BYTES)
        }
    }

    class cm_brushRef_s {
        var b: cm_brush_s? = null // pointer to brush
        var next: cm_brushRef_s? = null // next brush in chain

        companion object {
            val BYTES = cm_brush_s.BYTES + Integer.BYTES
        }
    }

    class cm_brushRefBlock_s {
        var next: cm_brushRefBlock_s? = null// next block with brush references
        var nextRef: cm_brushRef_s? = null // next brush reference in block
    }

    class cm_node_s {
        var brushes: cm_brushRef_s? = null// brushes in node
        var children: Array<cm_node_s?> = arrayOfNulls(2) // node children
        var parent: cm_node_s? = null // parent of this node
        var planeDist: Float = 0.0f // node plane distance
        var planeType: Int = 0// node axial plane type
        var polygons: cm_polygonRef_s? = null // polygons in node

        companion object {
            val BYTES =
                (Integer.BYTES + java.lang.Float.BYTES + cm_polygonRef_s.BYTES + cm_brushRef_s.BYTES + Integer.BYTES + Integer.BYTES)
        }
    }

    class cm_nodeBlock_s {
        var next: cm_nodeBlock_s? = null // next block with nodes
        var nextNode: cm_node_s? = null // next node in block
    }

    class cm_model_s internal constructor() {
        val bounds: idBounds = idBounds()// model bounds
        var brushBlock: cm_brushBlock_s? = null// memory block with all brushes
        var brushMemory = 0
        var brushRefBlocks: cm_brushRefBlock_s? = null // list with blocks of brush references
        var contents = 0 // all contents of the model ored together
        var edges: Array<cm_edge_s>? = null // array with all edges used by the model
        var isConvex = false// set if model is convex
        var maxEdges = 0 // size of edge array

        // model geometry
        var maxVertices = 0 // size of vertex array
        val name: idStr = idStr()// model name
        var node: cm_node_s? = null// first node of spatial subdivision

        // blocks with allocated memory
        var nodeBlocks: cm_nodeBlock_s? = null // list with blocks of nodes
        var numBrushRefs = 0
        var numBrushes = 0
        var numEdges = 0 // number of edges
        var numInternalEdges = 0
        var numMergedPolys = 0
        var numNodes = 0
        var numPolygonRefs = 0

        // statistics
        var numPolygons = 0
        var numRemovedPolys = 0
        var numSharpEdges = 0
        var numVertices = 0 // number of vertices
        var polygonBlock: cm_polygonBlock_s? = null // memory block with all polygons
        var polygonMemory = 0
        var polygonRefBlocks: cm_polygonRefBlock_s? = null// list with blocks of polygon references
        var usedMemory = 0
        var vertices: Array<cm_vertex_s>? = null // array with all vertices used by the model

        companion object {
            fun generateArray(length: Int): Array<cm_model_s> {
                return Array(length) { cm_model_s() }
            }
        }
    }

    /*
     ===============================================================================

     Data used during collision detection calculations

     ===============================================================================
     */
    class cm_trmVertex_s {
        val endp: idVec3 = idVec3() // end point of vertex after movement
        val p: idVec3 = idVec3() // vertex position
        val pl: idPluecker = idPluecker() // pluecker coordinate for vertex movement
        var polygonSide = 0 // side of polygon this vertex is on (rotational collision)
        val rotationBounds: idBounds = idBounds()  // rotation bounds for this vertex
        val rotationOrigin: idVec3 = idVec3() // rotation origin for this vertex
        var used = false // true if this vertex is used for collision detection

        companion object {
            fun generateArray(length: Int): Array<cm_trmVertex_s> {
                return Array(length) { cm_trmVertex_s() }
            }
        }
    }

    class cm_trmEdge_s {
        var bitNum: Short = 0// vertex bit number
        val cross: idVec3 = idVec3() // (z,-y,x) of cross product between edge dir and movement dir
        val end: idVec3 = idVec3()// end of edge
        var pl: idPluecker = idPluecker() // pluecker coordinate for edge
        var plzaxis: idPluecker = idPluecker() // pluecker coordinate for rotation about the z-axis
        val rotationBounds: idBounds = idBounds() // rotation bounds for this edge
        val start: idVec3 = idVec3() // start of edge
        var used = false// true when vertex is used for collision detection
        var vertexNum: IntArray = IntArray(2) // indexes into cm_sraceWork_s->vertices

        companion object {
            fun generateArray(length: Int): Array<cm_trmEdge_s> {
                return Array<cm_trmEdge_s>(length) { cm_trmEdge_s() }
            }
        }
    }

    class cm_trmPolygon_s {
        var edges: IntArray = IntArray(TraceModel.MAX_TRACEMODEL_POLYEDGES) // index into cm_sraceWork_s->edges
        var numEdges = 0 // number of edges
        val plane: idPlane = idPlane() // polygon plane
        val rotationBounds: idBounds = idBounds() // rotation bounds for this polygon
        var used = false

        companion object {
            fun generateArray(length: Int): Array<cm_trmPolygon_s> {
                return Array(length) { cm_trmPolygon_s() }
            }
        }
    }

    class cm_traceWork_s {
        var angle = 0.0f// angle for rotational collision
        val axis: idVec3 = idVec3() // rotation axis in model space
        var axisIntersectsTrm = false // true if the rotation axis intersects the trace model
        val bounds: idBounds = idBounds() // bounds of full trace
        var contacts: Array<contactInfo_t>? = null // array with contacts
        var contents = 0// ignore polygons that do not have any of these contents flags
        val dir: idVec3 = idVec3()// trace direction
        var edges: Array<cm_trmEdge_s> = cm_trmEdge_s.generateArray(TraceModel.MAX_TRACEMODEL_EDGES + 1) // trm edges
        val end: idVec3 = idVec3() // end of trace
        val extents: idVec3 = idVec3() // largest of abs(size[0]) and abs(size[1]) for BSP trace
        var getContacts = false // true if retrieving contacts
        val heartPlane1: idPlane = idPlane() // polygons should be near anough the trace heart planes
        val heartPlane2: idPlane = idPlane()
        var isConvex = false// true if the trace model is convex
        val matrix: idMat3 = idMat3() // rotates axis of rotation to the z-axis
        var maxContacts = 0 // max size of contact array
        var maxDistFromHeartPlane1 = 0.0f
        var maxDistFromHeartPlane2 = 0.0f
        var maxTan = 0.0f // max tangent of half the positive angle used instead of fraction
        var model: cm_model_s? = null// model colliding with
        val modelVertexRotation: idRotation = idRotation() // inverse rotation for model vertices
        var numContacts = 0 // number of contacts found
        var numEdges = 0
        var numPolys = 0
        var numVerts = 0
        val origin: idVec3 = idVec3()// origin of rotation in model space
        var pointTrace = false// true if only tracing a point
        var polygonEdgePlueckerCache: Array<idPluecker> = idPluecker.generateArray(CM_MAX_POLYGON_EDGES)
        val polygonRotationOriginCache: Array<idVec3> = idVec3.generateArray(CM_MAX_POLYGON_EDGES)
        var polygonVertexPlueckerCache: Array<idPluecker> = idPluecker.generateArray(CM_MAX_POLYGON_EDGES)
        val polys: Array<cm_trmPolygon_s> =
            cm_trmPolygon_s.generateArray(TraceModel.MAX_TRACEMODEL_POLYS) // trm polygons
        var positionTest = false // true if not tracing but doing a position test
        var quickExit = false// set to quickly stop the collision detection calculations
        var radius = 0.0f // rotation radius of trm start
        var rotation = false// true if calculating rotational collision
        val size: idBounds = idBounds()// bounds of transformed trm relative to start
        val start: idVec3 = idVec3() // start of trace
        val trace: trace_s = trace_s() // collision detection result
        val vertices: Array<cm_trmVertex_s> =
            cm_trmVertex_s.generateArray(TraceModel.MAX_TRACEMODEL_VERTS)// trm vertices

    }

    /*
     ===============================================================================

     Collision Map

     ===============================================================================
     */
    class cm_procNode_s {
        val children: IntArray = IntArray(2) // negative numbers are (-1 - areaNumber), 0 = solid
        val plane: idPlane = idPlane()
    }

    companion object {
        const val CHOP_EPSILON = 0.1f
        const val CIRCLE_APPROXIMATION_LENGTH = 64.0f
        const val CM_MAX_POLYGON_EDGES = 64
        const val EDGE_HASH_SIZE = 1 shl 14
        const val INTEGRAL_EPSILON = 0.01f
        const val MAX_NODE_POLYGONS = 128
        const val MAX_SUBMODELS = 2048
        const val MAX_WINDING_LIST = 128 // quite a few are generated at times
        const val MIN_NODE_SIZE = 64.0f
        const val NODE_BLOCK_SIZE_LARGE = 256
        const val NODE_BLOCK_SIZE_SMALL = 8
        const val REFERENCE_BLOCK_SIZE_LARGE = 256
        const val REFERENCE_BLOCK_SIZE_SMALL = 8
        const val TRACE_MODEL_HANDLE = MAX_SUBMODELS
        const val VERTEX_HASH_BOXSIZE = 1 shl 6 // must be power of 2
        const val VERTEX_HASH_SIZE = VERTEX_HASH_BOXSIZE * VERTEX_HASH_BOXSIZE
        const val VERTEX_EPSILON = 0.1f
    }
}
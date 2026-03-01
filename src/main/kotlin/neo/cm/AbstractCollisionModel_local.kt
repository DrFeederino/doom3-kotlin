/*
===========================================================================

Doom 3 GPL Source Code
Copyright (C) 1999-2011 id Software LLC, a ZeniMax Media company.

Translated to Kotlin by Dr. Feederino with support of Claude Code

This file is part of the Doom 3 Kotlin project.
Original source: neo/cm/CollisionModel_local.h

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
===============================================================================

	Trace model vs. polygonal model collision detection.

	Data structures from CollisionModel_local.h.
	The idCollisionModelManagerLocal class and methods are in CollisionModel_local.kt.

===============================================================================
*/
abstract class AbstractCollisionModel_local {

    class cm_windingList_s {
        var numWindings = 0 // number of windings
        var w: Array<idFixedWinding?> = arrayOfNulls(MAX_WINDING_LIST) // windings
        val normal: idVec3 = idVec3() // normal for all windings
        val bounds: idBounds = idBounds() // bounds of all windings in list
        val origin: idVec3 = idVec3() // origin for radius
        var radius = 0.0f // radius relative to origin for all windings
        var contents = 0 // winding surface contents
        var primitiveNum = 0 // number of primitive the windings came from
    }

    /*
     ===============================================================================

     Collision model

     ===============================================================================
     */
    class cm_vertex_s {
        val p: idVec3 = idVec3() // vertex point
        var checkcount: Int = 0 // for multi-check avoidance

        // NOTE: Differs from C++ - uses Long instead of unsigned int to avoid signed-bit issues
        // when bit 31 is set (MAX_TRACEMODEL_EDGES = 32). All bitwise ops use 1L shl consistently.
        var side: Long = 0 // each bit tells at which side this vertex passes one of the trace model edges
        var sideSet: Long = 0 // each bit tells if sidedness for the trace model edge has been calculated yet

        companion object {
            // NOTE: SIZE/BYTES approximate C++ struct size for memory estimation (usedMemory).
            // Uses Long sizes for side/sideSet to match Kotlin field types.
            val SIZE: Int = idVec3.SIZE + Integer.SIZE + java.lang.Long.SIZE + java.lang.Long.SIZE
            val BYTES = SIZE / java.lang.Byte.SIZE

            fun generateArray(length: Int): Array<cm_vertex_s> {
                return Array(length) { cm_vertex_s() }
            }
        }
    }

    class cm_edge_s {
        var checkcount: Int = 0 // for multi-check avoidance

        // NOTE: Differs from C++ - Boolean instead of unsigned short. Only used as 0/1 flag.
        // CAUTION: When writing to file with %d format, must convert: if (internal) 1 else 0
        var internal = false // a trace model can never collide with internal edges
        var numUsers: Short = 0 // number of polygons using this edge

        // NOTE: Differs from C++ - Long instead of unsigned int (same rationale as cm_vertex_s)
        var side: Long = 0 // each bit tells at which side of this edge one of the trace model vertices passes
        var sideSet: Long = 0 // each bit tells if sidedness for the trace model vertex has been calculated yet
        var vertexNum: IntArray = IntArray(2) // start and end point of edge
        val normal: idVec3 = idVec3() // edge normal

        companion object {
            // FIX: Original calculation was missing one Integer.SIZE for vertexNum[2] (needs 2 ints, not 1)
            val SIZE: Int =
                Integer.SIZE +                  // checkcount
                        java.lang.Short.SIZE +          // internal (stored as short in C++)
                        java.lang.Short.SIZE +          // numUsers
                        java.lang.Long.SIZE +           // side (Long in Kotlin, unsigned int in C++)
                        java.lang.Long.SIZE +           // sideSet (Long in Kotlin, unsigned int in C++)
                        Integer.SIZE * 2 +              // vertexNum[2]
                        idVec3.SIZE                     // normal
            val BYTES = SIZE / java.lang.Byte.SIZE

            fun generateArray(length: Int): Array<cm_edge_s> {
                return Array(length) { cm_edge_s() }
            }
        }
    }

    class cm_polygonBlock_s {
        var bytesRemaining: Int = 0

        // NOTE: Differs from C++ - C++ uses byte* for raw memory block pointer.
        // In Kotlin/JVM with GC, we just track the block metadata.
        var next: cm_polygonBlock_s? = null
    }

    class cm_polygon_s {
        val bounds: idBounds = idBounds() // polygon bounds
        var checkcount: Int = 0 // for multi-check avoidance
        var contents: Int = 0 // contents behind polygon
        var material: idMaterial? = null // material
        val plane: idPlane = idPlane() // polygon plane
        var numEdges: Int = 0 // number of edges
        var edges: IntArray = IntArray(1) // variable sized, indexes into cm_edge_t list

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
        var p: cm_polygon_s? = null // pointer to polygon
        var next: cm_polygonRef_s? = null // next polygon in chain

        companion object {
            val BYTES = cm_polygon_s.BYTES + Integer.BYTES
        }
    }

    class cm_polygonRefBlock_s {
        var nextRef: cm_polygonRef_s? = null // next polygon reference in block
        var next: cm_polygonRefBlock_s? = null // next block with polygon references
    }

    class cm_brushBlock_s {
        var bytesRemaining: Int = 0

        // NOTE: Differs from C++ - C++ uses byte* for raw memory block pointer.
        var next: cm_brushBlock_s? = null
    }

    class cm_brush_s {
        var checkcount: Int = 0 // for multi-check avoidance
        val bounds: idBounds = idBounds() // brush bounds
        var contents: Int = 0 // contents of brush
        var material: idMaterial? = null // material
        var primitiveNum = 0 // number of brush primitive
        var numPlanes = 0 // number of bounding planes
        var planes: Array<idPlane> = idPlane.generateArray(1) // variable sized

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
        var nextRef: cm_brushRef_s? = null // next brush reference in block
        var next: cm_brushRefBlock_s? = null // next block with brush references
    }

    class cm_node_s {
        var planeType: Int = 0 // node axial plane type
        var planeDist: Float = 0.0f // node plane distance
        var polygons: cm_polygonRef_s? = null // polygons in node
        var brushes: cm_brushRef_s? = null // brushes in node
        var parent: cm_node_s? = null // parent of this node
        var children: Array<cm_node_s?> = arrayOfNulls(2) // node children

        companion object {
            val BYTES =
                (Integer.BYTES + java.lang.Float.BYTES + cm_polygonRef_s.BYTES + cm_brushRef_s.BYTES + Integer.BYTES + Integer.BYTES)
        }
    }

    class cm_nodeBlock_s {
        var nextNode: cm_node_s? = null // next node in block
        var next: cm_nodeBlock_s? = null // next block with nodes
    }

    class cm_model_s internal constructor() {
        val name: idStr = idStr() // model name
        val bounds: idBounds = idBounds() // model bounds
        var contents = 0 // all contents of the model ored together
        var isConvex = false // set if model is convex
        // model geometry
        var maxVertices = 0 // size of vertex array
        var numVertices = 0 // number of vertices
        var vertices: Array<cm_vertex_s>? = null // array with all vertices used by the model
        var maxEdges = 0 // size of edge array
        var numEdges = 0 // number of edges
        var edges: Array<cm_edge_s>? = null // array with all edges used by the model
        var node: cm_node_s? = null // first node of spatial subdivision
        // blocks with allocated memory
        var nodeBlocks: cm_nodeBlock_s? = null // list with blocks of nodes
        var polygonRefBlocks: cm_polygonRefBlock_s? = null // list with blocks of polygon references
        var brushRefBlocks: cm_brushRefBlock_s? = null // list with blocks of brush references
        var polygonBlock: cm_polygonBlock_s? = null // memory block with all polygons
        var brushBlock: cm_brushBlock_s? = null // memory block with all brushes

        // statistics
        var numPolygons = 0
        var polygonMemory = 0
        var numBrushes = 0
        var brushMemory = 0
        var numNodes = 0
        var numBrushRefs = 0
        var numPolygonRefs = 0
        var numInternalEdges = 0
        var numSharpEdges = 0
        var numRemovedPolys = 0
        var numMergedPolys = 0
        var usedMemory = 0

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
        var used = false // true if this vertex is used for collision detection
        val p: idVec3 = idVec3() // vertex position
        val endp: idVec3 = idVec3() // end point of vertex after movement
        var polygonSide = 0 // side of polygon this vertex is on (rotational collision)
        val pl: idPluecker = idPluecker() // pluecker coordinate for vertex movement
        val rotationOrigin: idVec3 = idVec3() // rotation origin for this vertex
        val rotationBounds: idBounds = idBounds() // rotation bounds for this vertex

        companion object {
            fun generateArray(length: Int): Array<cm_trmVertex_s> {
                return Array(length) { cm_trmVertex_s() }
            }
        }
    }

    class cm_trmEdge_s {
        var used = false // true when vertex is used for collision detection
        val start: idVec3 = idVec3() // start of edge
        val end: idVec3 = idVec3() // end of edge
        var vertexNum: IntArray = IntArray(2) // indexes into cm_traceWork_s->vertices
        var pl: idPluecker = idPluecker() // pluecker coordinate for edge
        val cross: idVec3 = idVec3() // (z,-y,x) of cross product between edge dir and movement dir
        val rotationBounds: idBounds = idBounds() // rotation bounds for this edge
        var plzaxis: idPluecker = idPluecker() // pluecker coordinate for rotation about the z-axis
        var bitNum: Short = 0 // vertex bit number

        companion object {
            fun generateArray(length: Int): Array<cm_trmEdge_s> {
                return Array(length) { cm_trmEdge_s() }
            }
        }
    }

    class cm_trmPolygon_s {
        var used = false
        val plane: idPlane = idPlane() // polygon plane
        var numEdges = 0 // number of edges
        var edges: IntArray = IntArray(TraceModel.MAX_TRACEMODEL_POLYEDGES) // index into cm_traceWork_s->edges
        val rotationBounds: idBounds = idBounds() // rotation bounds for this polygon

        companion object {
            fun generateArray(length: Int): Array<cm_trmPolygon_s> {
                return Array(length) { cm_trmPolygon_s() }
            }
        }
    }

    class cm_traceWork_s {
        var numVerts = 0
        val vertices: Array<cm_trmVertex_s> =
            cm_trmVertex_s.generateArray(TraceModel.MAX_TRACEMODEL_VERTS) // trm vertices
        var numEdges = 0
        var edges: Array<cm_trmEdge_s> = cm_trmEdge_s.generateArray(TraceModel.MAX_TRACEMODEL_EDGES + 1) // trm edges
        var numPolys = 0
        val polys: Array<cm_trmPolygon_s> =
            cm_trmPolygon_s.generateArray(TraceModel.MAX_TRACEMODEL_POLYS) // trm polygons
        var model: cm_model_s? = null // model colliding with
        val start: idVec3 = idVec3() // start of trace
        val end: idVec3 = idVec3() // end of trace
        val dir: idVec3 = idVec3() // trace direction
        val bounds: idBounds = idBounds() // bounds of full trace
        val size: idBounds = idBounds() // bounds of transformed trm relative to start
        val extents: idVec3 = idVec3() // largest of abs(size[0]) and abs(size[1]) for BSP trace
        var contents = 0 // ignore polygons that do not have any of these contents flags
        val trace: trace_s = trace_s() // collision detection result

        var rotation = false // true if calculating rotational collision
        var pointTrace = false // true if only tracing a point
        var positionTest = false // true if not tracing but doing a position test
        var isConvex = false // true if the trace model is convex
        var axisIntersectsTrm = false // true if the rotation axis intersects the trace model
        var getContacts = false // true if retrieving contacts
        var quickExit = false // set to quickly stop the collision detection calculations

        val origin: idVec3 = idVec3() // origin of rotation in model space
        val axis: idVec3 = idVec3() // rotation axis in model space
        val matrix: idMat3 = idMat3() // rotates axis of rotation to the z-axis
        var angle = 0.0f // angle for rotational collision
        var maxTan = 0.0f // max tangent of half the positive angle used instead of fraction
        var radius = 0.0f // rotation radius of trm start
        val modelVertexRotation: idRotation = idRotation() // inverse rotation for model vertices

        var contacts: Array<contactInfo_t>? = null // array with contacts
        var maxContacts = 0 // max size of contact array
        var numContacts = 0 // number of contacts found

        val heartPlane1: idPlane = idPlane() // polygons should be near anough the trace heart planes
        var maxDistFromHeartPlane1 = 0.0f
        val heartPlane2: idPlane = idPlane()
        var maxDistFromHeartPlane2 = 0.0f
        var polygonEdgePlueckerCache: Array<idPluecker> = idPluecker.generateArray(CM_MAX_POLYGON_EDGES)
        var polygonVertexPlueckerCache: Array<idPluecker> = idPluecker.generateArray(CM_MAX_POLYGON_EDGES)
        val polygonRotationOriginCache: Array<idVec3> = idVec3.generateArray(CM_MAX_POLYGON_EDGES)
    }

    /*
     ===============================================================================

     Collision Map

     ===============================================================================
     */
    class cm_procNode_s {
        val plane: idPlane = idPlane()
        val children: IntArray = IntArray(2) // negative numbers are (-1 - areaNumber), 0 = solid
    }

    companion object {
        const val MIN_NODE_SIZE = 64.0f
        const val MAX_NODE_POLYGONS = 128
        const val CM_MAX_POLYGON_EDGES = 64
        const val CIRCLE_APPROXIMATION_LENGTH = 64.0f

        const val MAX_SUBMODELS = 2048
        const val TRACE_MODEL_HANDLE = MAX_SUBMODELS

        const val VERTEX_HASH_BOXSIZE = 1 shl 6 // must be power of 2
        const val VERTEX_HASH_SIZE = VERTEX_HASH_BOXSIZE * VERTEX_HASH_BOXSIZE
        const val EDGE_HASH_SIZE = 1 shl 14

        const val NODE_BLOCK_SIZE_SMALL = 8
        const val NODE_BLOCK_SIZE_LARGE = 256
        const val REFERENCE_BLOCK_SIZE_SMALL = 8
        const val REFERENCE_BLOCK_SIZE_LARGE = 256

        const val MAX_WINDING_LIST = 128 // quite a few are generated at times
        const val INTEGRAL_EPSILON = 0.01f
        const val VERTEX_EPSILON = 0.1f
        const val CHOP_EPSILON = 0.1f
    }
}

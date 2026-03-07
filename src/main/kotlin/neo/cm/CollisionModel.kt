/*
===========================================================================

Doom 3 GPL Source Code
Copyright (C) 1999-2011 id Software LLC, a ZeniMax Media company.

Translated to Kotlin by Dr. Feederino with support of Claude Code

This file is part of the Doom 3 Kotlin project.
Original source: neo/cm/CollisionModel.h

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
import neo.TempDump.SERiAL
import neo.idlib.BV.idBounds
import neo.idlib.MapFile.idMapEntity
import neo.idlib.MapFile.idMapFile
import neo.idlib.Text.Str
import neo.idlib.containers.CInt
import neo.idlib.geometry.TraceModel.idTraceModel
import neo.idlib.geometry.Winding.idFixedWinding
import neo.idlib.math.Matrix.idMat3
import neo.idlib.math.idRotation
import neo.idlib.math.idVec3
import neo.idlib.math.idVec6
import java.nio.ByteBuffer

/*
===============================================================================

	Trace model vs. polygonal model collision detection.

	Short translations are the least expensive. Retrieving contact points is
	about as cheap as a short translation. Position tests are more expensive
	and rotations are most expensive.

	There is no position test at the start of a translation or rotation. In other
	words if a translation with start != end or a rotation with angle != 0 starts
	in solid, this goes unnoticed and the collision result is undefined.

	A translation with start == end or a rotation with angle == 0 performs
	a position test and fills in the trace_t structure accordingly.

===============================================================================
*/

const val CM_CLIP_EPSILON = 0.25f // always stay this distance away from any model
const val CM_BOX_EPSILON = 1.0f // should always be larger than clip epsilon
const val CM_MAX_TRACE_DIST = 4096.0f // maximum distance a trace model may be traced, point traces are unlimited

// contact type
enum class contactType_t {
    CONTACT_NONE, // no contact
    CONTACT_EDGE, // trace model edge hits model edge
    CONTACT_MODELVERTEX, // model vertex hits trace model polygon
    CONTACT_TRMVERTEX // trace model vertex hits model polygon
}

// contact info
class contactInfo_t() {
    var type: contactType_t = contactType_t.CONTACT_NONE // contact type
    val point: idVec3 = idVec3() // point of contact
    val normal: idVec3 = idVec3() // contact plane normal
    var dist: Float = 0.0f // contact plane distance
    var contents: Int = 0 // contents at other side of surface
    var material: idMaterial? = null // surface material
    var modelFeature: Int = 0 // contact feature on model
    var trmFeature: Int = 0 // contact feature on trace model
    var entityNum: Int = 0 // entity the contact surface is a part of
    var id: Int = 0 // id of clip model the contact surface is part of

    constructor(c: contactInfo_t) : this() {
        type = c.type
        point.set(c.point)
        normal.set(c.normal)
        dist = c.dist
        contents = c.contents
        material = c.material
        modelFeature = c.modelFeature
        trmFeature = c.trmFeature
        entityNum = c.entityNum // FIX: was missing in original translation
        id = c.id
    }

    fun clear() {
        type = contactType_t.CONTACT_NONE
        point.Zero()
        normal.Zero()
        dist = 0.0f
        contents = 0
        material = null
        modelFeature = 0
        trmFeature = 0
        entityNum = 0
        id = 0
    }
}

// trace result
class trace_s : SERiAL {
    var fraction = 0.0f // fraction of movement completed, 1.0 = didn't hit anything
    val endpos: idVec3 = idVec3() // final position of trace model
    val endAxis: idMat3 = idMat3() // final axis of trace model
    var c: contactInfo_t = contactInfo_t() // contact information, only valid if fraction < 1.0

    override fun AllocBuffer(): ByteBuffer {
        return ByteBuffer.allocate(BYTES)
    }

    override fun Read(buffer: ByteBuffer) {
        buffer.order(java.nio.ByteOrder.LITTLE_ENDIAN)
        fraction = buffer.float
        endpos[0] = buffer.float
        endpos[1] = buffer.float
        endpos[2] = buffer.float
        for (i in 0 until 3) {
            for (j in 0 until 3) {
                endAxis[i][j] = buffer.float
            }
        }
        c.type = contactType_t.entries[buffer.int]
        c.point[0] = buffer.float
        c.point[1] = buffer.float
        c.point[2] = buffer.float
        c.normal[0] = buffer.float
        c.normal[1] = buffer.float
        c.normal[2] = buffer.float
        c.dist = buffer.float
        c.contents = buffer.int
        buffer.int // material pointer, skip
        c.modelFeature = buffer.int
        c.trmFeature = buffer.int
        c.entityNum = buffer.int
        c.id = buffer.int
    }

    override fun Write(): ByteBuffer {
        val buffer = AllocBuffer()
        buffer.order(java.nio.ByteOrder.LITTLE_ENDIAN)
        buffer.putFloat(fraction)
        buffer.putFloat(endpos[0])
        buffer.putFloat(endpos[1])
        buffer.putFloat(endpos[2])
        for (i in 0 until 3) {
            for (j in 0 until 3) {
                buffer.putFloat(endAxis[i][j])
            }
        }
        buffer.putInt(c.type.ordinal)
        buffer.putFloat(c.point[0])
        buffer.putFloat(c.point[1])
        buffer.putFloat(c.point[2])
        buffer.putFloat(c.normal[0])
        buffer.putFloat(c.normal[1])
        buffer.putFloat(c.normal[2])
        buffer.putFloat(c.dist)
        buffer.putInt(c.contents)
        buffer.putInt(0) // material pointer
        buffer.putInt(c.modelFeature)
        buffer.putInt(c.trmFeature)
        buffer.putInt(c.entityNum)
        buffer.putInt(c.id)
        buffer.flip()
        return buffer
    }

    companion object {
        @Transient
        val BYTES = 120
    }

    fun set(s: trace_s) {
        fraction = s.fraction
        endpos.set(s.endpos)
        endAxis.set(s.endAxis)
        c.type = s.c.type
        c.point.set(s.c.point)
        c.normal.set(s.c.normal)
        c.dist = s.c.dist
        c.contents = s.c.contents
        c.material = s.c.material
        c.modelFeature = s.c.modelFeature
        c.trmFeature = s.c.trmFeature
        c.entityNum = s.c.entityNum
        c.id = s.c.id
    }

    // Equivalent to C++ memset(results, 0, sizeof(*results))
    fun clear() {
        fraction = 0.0f
        endpos.Zero()
        endAxis.Zero()
        c.clear()
    }
}

abstract class idCollisionModelManager {

    // Loads collision models from a map file.
    abstract fun LoadMap(mapFile: idMapFile?)

    // Frees all the collision models.
    abstract fun FreeMap()

    // Gets the clip handle for a model.
    abstract fun LoadModel(modelName: Str.idStr, precache: Boolean): Int

    fun LoadModel(modelName: String, precache: Boolean): Int {
        return LoadModel(Str.idStr.parseStr(modelName), precache)
    }

    // Sets up a trace model for collision with other trace models.
    // NOTE: Differs from C++ - uses Array<idMaterial?> instead of idMaterial? to simulate
    // pass-by-reference semantics for the material parameter. C++ original takes const idMaterial*.
    abstract fun SetupTrmModel(trm: idTraceModel, material: Array<idMaterial?>): Int

    // Creates a trace model from a collision model, returns true if successful.
    abstract fun TrmFromModel(modelName: Str.idStr, trm: idTraceModel): Boolean

    // Gets the name of a model.
    abstract fun GetModelName(model: Int): String

    // Gets the bounds of a model.
    abstract fun GetModelBounds(model: Int, bounds: idBounds): Boolean

    // Gets all contents flags of brushes and polygons of a model ored together.
    abstract fun GetModelContents(model: Int, contents: CInt): Boolean

    // Gets a vertex of a model.
    abstract fun GetModelVertex(model: Int, vertexNum: Int, vertex: idVec3): Boolean

    // Gets an edge of a model.
    abstract fun GetModelEdge(model: Int, edgeNum: Int, start: idVec3, end: idVec3): Boolean

    // Gets a polygon of a model.
    abstract fun GetModelPolygon(model: Int, polygonNum: Int, winding: idFixedWinding): Boolean

    // Translates a trace model and reports the first collision if any.
    abstract fun Translation(
        results: trace_s, start: idVec3, end: idVec3,
        trm: idTraceModel?, trmAxis: idMat3, contentMask: Int,
        model: Int, modelOrigin: idVec3, modelAxis: idMat3
    )

    // Rotates a trace model and reports the first collision if any.
    abstract fun Rotation(
        results: trace_s, start: idVec3, rotation: idRotation,
        trm: idTraceModel, trmAxis: idMat3, contentMask: Int,
        model: Int, modelOrigin: idVec3, modelAxis: idMat3
    )

    // Returns the contents touched by the trace model or 0 if the trace model is in free space.
    abstract fun Contents(
        start: idVec3,
        trm: idTraceModel?, trmAxis: idMat3, contentMask: Int,
        model: Int, modelOrigin: idVec3, modelAxis: idMat3
    ): Int

    // Stores all contact points of the trace model with the model, returns the number of contacts.
    abstract fun Contacts(
        contacts: Array<contactInfo_t>, maxContacts: Int, start: idVec3, dir: idVec6, depth: Float,
        trm: idTraceModel, trmAxis: idMat3, contentMask: Int,
        model: Int, origin: idVec3, modelAxis: idMat3
    ): Int

    // Tests collision detection.
    abstract fun DebugOutput(origin: idVec3)

    // Draws a model.
    abstract fun DrawModel(model: Int, modelOrigin: idVec3, modelAxis: idMat3, viewOrigin: idVec3, radius: Float)

    // Prints model information, use -1 handle for accumulated model info.
    abstract fun ModelInfo(model: Int)

    // Lists all loaded models.
    abstract fun ListModels()

    // Writes a collision model file for the given map entity.
    abstract fun WriteCollisionModelForMapEntity(
        mapEnt: idMapEntity,
        filename: String,
        testTraceModel: Boolean /* = true*/
    ): Boolean
}

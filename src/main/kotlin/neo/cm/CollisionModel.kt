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

const val CM_BOX_EPSILON = 1.0f // should always be larger than clip epsilon
const val CM_CLIP_EPSILON = 0.25f // always stay this distance away from any model
const val CM_MAX_TRACE_DIST = 4096.0f // maximum distance a trace model may be traced, point traces are unlimited

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
enum class contactType_t {
    CONTACT_NONE,  // no contact
    CONTACT_EDGE,  // trace model edge hits model edge
    CONTACT_MODELVERTEX,  // model vertex hits trace model polygon
    CONTACT_TRMVERTEX // trace model vertex hits model polygon
}

class contactInfo_t() {
    var contents // contents at other side of surface
            = 0
    var dist // contact plane distance
            = 0.0f
    var entityNum // entity the contact surface is a part of
            = 0
    var id // id of clip model the contact surface is part of
            = 0
    var material // surface material
            : idMaterial? = null

    var modelFeature // contact feature on model
            = 0
    val normal // contact plane normal
            : idVec3 = idVec3()
    val point // point of contact
            : idVec3 = idVec3()
    var trmFeature: Int // contact feature on trace model
            = 0
    var type // contact type
            : contactType_t = contactType_t.CONTACT_NONE

    constructor(c: contactInfo_t) : this() {
        normal.set(c.normal)
        point.set(c.point)
        dist = c.dist
        contents = c.contents
        material = c.material
        id = c.id
        trmFeature = c.trmFeature
        modelFeature = c.modelFeature
        type = c.type
    }
}

// trace result
class trace_s : SERiAL {
    var c: contactInfo_t = contactInfo_t()// contact information, only valid if fraction < 1.0f
    val endAxis: idMat3 = idMat3() // final axis of trace model
    val endpos: idVec3 = idVec3() // final position of trace model
    var fraction = 0.0f // fraction of movement completed, 1.0f = didn't hit anything

    override fun AllocBuffer(): ByteBuffer {
        throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
    }

    override fun Read(buffer: ByteBuffer) {
        throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
    }

    override fun Write(): ByteBuffer {
        throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
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
        //c = s.c
    }
}

abstract class idCollisionModelManager {
    abstract fun LoadMap(mapFile: idMapFile?)

    // Frees all the collision models.
    abstract fun FreeMap()

    // Gets the clip handle for a model.
    abstract fun LoadModel(modelName: Str.idStr, precache: Boolean): Int

    fun LoadModel(modelName: String, precache: Boolean): Int {
        return LoadModel(Str.idStr.parseStr(modelName), precache)
    }

    // Sets up a trace model for collision with other trace models.
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

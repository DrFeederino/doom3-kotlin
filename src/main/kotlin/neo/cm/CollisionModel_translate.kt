package neo.cm

import neo.cm.AbstractCollisionModel_local.*
import neo.idlib.math.*

/*
 ===============================================================================

 Trace model vs. polygonal model collision detection.

 ===============================================================================
 */
/*
 ===============================================================================

 Collision detection for translational motion

 ===============================================================================
 */
/*
 ================
 CM_AddContact
 ================
 */
fun CM_AddContact(tw: cm_traceWork_s) {
    if (tw.numContacts >= tw.maxContacts) {
        return
    }
    // copy contact information from trace_t
    // re-creates contactInfo_t? In src code it's just a ref
    //tw.contacts[tw.numContacts] = new contactInfo_t(tw.trace.c);
    tw.contacts!![tw.numContacts] = contactInfo_t(tw.trace.c)
    tw.numContacts++
    // set fraction back to 1 to find all other contacts
    tw.trace.fraction = 1.0f
}

/*
 ================
 CM_SetVertexSidedness

 stores for the given model vertex at which side of one of the trm edges it passes
 ================
 */
fun CM_SetVertexSidedness(v: cm_vertex_s, vpl: idPluecker, epl: idPluecker, bitNum: Int) {
    if ((v.sideSet and (1L shl bitNum)) == 0L) {
        val fl = vpl.PermutedInnerProduct(epl)
        v.side = v.side and (1L shl bitNum).inv() or (FLOATSIGNBITSET(fl).toLong() shl bitNum)
        v.sideSet = v.sideSet or (1L shl bitNum)
    }
}

/*
 ================
 CM_SetEdgeSidedness

 stores for the given model edge at which side one of the trm vertices
 ================
 */
fun CM_SetEdgeSidedness(edge: cm_edge_s, vpl: idPluecker, epl: idPluecker, bitNum: Int) {
    if ((edge.sideSet and (1L shl bitNum)) == 0L) {
        val fl = vpl.PermutedInnerProduct(epl)
        edge.side = edge.side and (1L shl bitNum).inv() or (FLOATSIGNBITSET(fl).toLong() shl bitNum)
        edge.sideSet = edge.sideSet or (1L shl bitNum)
    }
}

/*
 ================
 CM_TranslationPlaneFraction
 Note: has two implementations, see #if 0 else #endif
 ================
 */
fun CM_TranslationPlaneFraction(plane: idPlane, start: idVec3, end: idVec3): Float {
    val d2eps: Float
    var d2: Float = plane.Distance(end)
    // if the end point is closer to the plane than an epsilon we still take it for a collision
    // if ( d2 >= CM_CLIP_EPSILON ) {
    d2eps = d2 - CM_CLIP_EPSILON
    if (FLOATSIGNBITNOTSET(d2eps) != 0) {
        return 1.0f
    }
    val d1: Float = plane.Distance(start)

    // if completely behind the polygon
    if (FLOATSIGNBITSET(d1) != 0) {
        return 1.0f
    }
    // if going towards the front of the plane and
    // the start and end point are not at equal distance from the plane
    // if ( d1 > d2 )
    d2 = d1 - d2
    return if (d2 <= 0.0f) {
        1.0f
    } else (d1 - CM_CLIP_EPSILON) / d2
}
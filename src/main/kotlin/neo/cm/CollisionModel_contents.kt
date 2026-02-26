package neo.cm

import neo.cm.AbstractCollisionModel_local.cm_edge_s
import neo.cm.AbstractCollisionModel_local.cm_vertex_s
import neo.idlib.math.FLOATSIGNBITSET
import neo.idlib.math.idPlane
import neo.idlib.math.idPluecker

/*
 ===============================================================================

 Contents test

 ===============================================================================
 */
/*
 ================
 CM_SetTrmEdgeSidedness
 ================
 */
fun CM_SetTrmEdgeSidedness(edge: cm_edge_s, bpl: idPluecker, epl: idPluecker, bitNum: Int) {
    if ((edge.sideSet and (1L shl bitNum)) == 0L) {
        val fl: Float
        fl = bpl.PermutedInnerProduct(epl)
        edge.side = edge.side and (1L shl bitNum).inv() or (FLOATSIGNBITSET(fl).toLong() shl bitNum)
        edge.sideSet = edge.sideSet or (1L shl bitNum)
    }
}

/*
 ================
 CM_SetTrmPolygonSidedness
 ================
 */
fun CM_SetTrmPolygonSidedness(v: cm_vertex_s, plane: idPlane, bitNum: Int) {
    if ((v.sideSet and (1L shl bitNum)) == 0L) {
        val fl: Float
        fl = plane.Distance(v.p)
        /* cannot use float sign bit because it is undetermined when fl == 0.0f */
        if (fl < 0.0f) {
            v.side = v.side or (1L shl bitNum)
        } else {
            v.side = v.side and (1L shl bitNum).inv()
        }
        v.sideSet = v.sideSet or (1L shl bitNum)
    }
}

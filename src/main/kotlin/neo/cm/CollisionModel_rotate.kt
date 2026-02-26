package neo.cm

import neo.idlib.containers.CFloat
import neo.idlib.math.idVec3

/*
 ===============================================================================

 Trace model vs. polygonal model collision detection.

 ===============================================================================
 */
/*
 ===============================================================================

 Collision detection for rotational motion

 ===============================================================================
 */
// epsilon for round-off errors in epsilon calculations
const val CM_PL_RANGE_EPSILON = 1e-4f

// if the collision point is this close to the rotation axis it is not considered a collision
const val ROTATION_AXIS_EPSILON = CM_CLIP_EPSILON * 0.25f

/*
 ================
 CM_RotatePoint

 rotates a point about an arbitrary axis using the tangent of half the rotation angle
 ================
 */
fun CM_RotatePoint(point: idVec3, origin: idVec3, axis: idVec3, tanHalfAngle: Float) {
    val proj = idVec3()
    val v1 = idVec3()
    val v2 = idVec3()

    point.minusAssign(origin)
    proj.set(axis * (point * axis))
    v1.set(point - proj)
    v2.set(axis.Cross(v1))

    // r = tan( a / 2 );
    // sin(a) = 2*r/(1+r*r);
    // cos(a) = (1-r*r)/(1+r*r);
    val t: Double = (tanHalfAngle * tanHalfAngle).toDouble()
    val d: Double = 1.0 / (1.0 + t)
    val s: Double = 2.0 * tanHalfAngle * d
    val c: Double = (1.0 - t) * d

    point.set(v1 * c.toFloat() - v2 * s.toFloat() + proj + origin)
}

/*
 ================
 CM_RotateEdge

 rotates an edge about an arbitrary axis using the tangent of half the rotation angle
 ================
 */
fun CM_RotateEdge(start: idVec3, end: idVec3, origin: idVec3, axis: idVec3, tanHalfAngle: CFloat) {
    val proj = idVec3()
    val v1 = idVec3()
    val v2 = idVec3()

    // r = tan( a / 2 );
    // sin(a) = 2*r/(1+r*r);
    // cos(a) = (1-r*r)/(1+r*r);
    val t: Double = (tanHalfAngle._val * tanHalfAngle._val).toDouble()
    val d: Double = 1.0 / (1.0 + t)
    val s: Double = 2.0 * tanHalfAngle._val * d
    val c: Double = (1.0 - t) * d

    start.minusAssign(origin)
    proj.set(axis * (start * axis))
    v1.set(start - proj)
    v2.set(axis.Cross(v1))
    start.set(v1 * c.toFloat() - v2 * s.toFloat() + proj + origin)

    end.minusAssign(origin)
    proj.set(axis * (end * axis))
    v1.set(end - proj)
    v2.set(axis.Cross(v1))
    end.set(v1 * c.toFloat() - v2 * s.toFloat() + proj + origin)
}
package neo.idlib.BV

import neo.TempDump.SERiAL
import neo.idlib.BV.Sphere.idSphere
import neo.idlib.Max
import neo.idlib.Min
import neo.idlib.containers.CFloat
import neo.idlib.math.*
import neo.idlib.math.Matrix.idMat3
import java.nio.ByteBuffer
import kotlin.math.abs

val bounds_zero: idBounds = idBounds()

/*
 ================
 BoundsForPointRotation

 only for rotations < 180 degrees
 ================
 */
fun BoundsForPointRotation(start: idVec3, rotation: idRotation): idBounds {
    var i: Int
    val radiusSqr: Float
    val v1 = idVec3()
    val v2 = idVec3()
    val origin = idVec3()
    val axis = idVec3()
    val end = idVec3()
    val bounds = idBounds()

    end.set(rotation * start)
    axis.set(rotation.GetVec())
    origin.set(rotation.GetOrigin() + axis * (axis * (start - rotation.GetOrigin())))
    radiusSqr = (start - origin).LengthSqr()
    v1.set((start - origin).Cross(axis))
    v2.set((end - origin).Cross(axis))

    i = 0
    while (i < 3) {

        // if the derivative changes sign along this axis during the rotation from start to end
        if ((v1[i] > 0.0f && v2[i] < 0.0f) || (v1[i] < 0.0f && v2[i] > 0.0f)) {
            if ((0.5f * (start[i] + end[i]) - origin[i]) > 0.0f) {
                bounds[0, i] = Min(start[i], end[i])
                bounds[1, i] = origin[i] + idMath.Sqrt(radiusSqr * (1.0f - axis[i] * axis[i]))
            } else {
                bounds[0, i] = origin[i] - idMath.Sqrt(radiusSqr * (1.0f - axis[i] * axis[i]))
                bounds[1, i] = Max(start[i], end[i])
            }
        } else if (start[i] > end[i]) {
            bounds[0, i] = end[i]
            bounds[1, i] = start[i]
        } else {
            bounds[0, i] = start[i]
            bounds[1, i] = end[i]
        }
        i++
    }
    return bounds
}

/*
 ===============================================================================

 Axis Aligned Bounding Box

 ===============================================================================
 */
class idBounds : SERiAL {
    private var b: Array<idVec3> = idVec3.generateArray(2)

    constructor() {
        b[0].Zero()
        b[1].Zero()
    }

    constructor(mins: idVec3, maxs: idVec3) {
        b[0].set(mins)
        b[1].set(maxs)
    }

    constructor(bounds: idBounds) {
        b[0].set(bounds.b[0])
        b[1].set(bounds.b[1])
    }

    constructor(point: idVec3) {
        b[0].set(point)
        b[1].set(point)
    }

    fun set(bounds: idBounds) {
        b[0].set(bounds.b[0])
        b[1].set(bounds.b[1])
    }


    fun set(v0: Float, v1: Float, v2: Float, v3: Float, v4: Float, v5: Float) {
        b[0] = idVec3(v0, v1, v2)
        b[1] = idVec3(v3, v4, v5)
    }

    operator fun get(index: Int): idVec3 {
        return b[index]
    }

    operator fun get(index1: Int, index2: Int): Float {
        return b[index1][index2]
    }

    operator fun set(index: Int, t: idVec3): idVec3 {
        return b[index].set(t)
    }

    operator fun set(x: Int, y: Int, value: Float): Float {
        return b[x].set(y, value)
    }

    operator fun plus(t: idVec3): idBounds {
        return idBounds(b[0] + t, b[1] + t)
    }

    fun timesAssign(t: idVec3): idBounds {
        b[0].plusAssign(t)
        b[1].plusAssign(t)
        return this
    }

    fun timesAssign(index: Int, t: idVec3): idVec3 {
        return b[index].plusAssign(t)
    }

    operator fun times(r: idMat3): idBounds {
        val bounds = idBounds()
        bounds.FromTransformedBounds(this, vec3_origin, r)
        return bounds
    }

    fun timesAssign(r: idMat3): idBounds {
        FromTransformedBounds(this, vec3_origin, r)
        return this
    }

    operator fun plus(a: idBounds): idBounds {
        val newBounds: idBounds
        newBounds = idBounds(this)
        newBounds.AddBounds(a)
        return newBounds
    }

    fun timesAssign(a: idBounds): idBounds {
        AddBounds(a)
        return this
    }

    operator fun minus(a: idBounds): idBounds {
        assert(
            b[1][0] - b[0][0] > a.b[1][0] - a.b[0][0] && b[1][1] - b[0][1] > a.b[1][1] - a.b[0][1] && b[1][2] - b[0][2] > a.b[1][2] - a.b[0][2]
        )
        return idBounds(
            idVec3(b[0][0] + a.b[1][0], b[0][1] + a.b[1][1], b[0][2] + a.b[1][2]),
            idVec3(b[1][0] + a.b[0][0], b[1][1] + a.b[0][1], b[1][2] + a.b[0][2])
        )
    }

    fun minusAssign(a: idBounds): idBounds {
        assert(
            b[1][0] - b[0][0] > a.b[1][0] - a.b[0][0] && b[1][1] - b[0][1] > a.b[1][1] - a.b[0][1] && b[1][2] - b[0][2] > a.b[1][2] - a.b[0][2]
        )
        b[0].plusAssign(a.b[1])
        b[1].plusAssign(a.b[0])
        return this
    }

    fun minusAssign(t: idVec3): idBounds {
        b[0].minusAssign(t)
        b[1].minusAssign(t)
        return this
    }

    fun minusAssign(index: Int, t: idVec3): idVec3 {
        return b[index].minusAssign(t)
    }

    fun plusAssign(t: idVec3): idBounds {
        b[0].plusAssign(t)
        b[1].plusAssign(t)

        return this
    }

    fun plusAssign(index: Int, t: idVec3): idVec3 {
        return b[index].minusAssign(t)
    }

    fun plusAssign(bounds: idBounds): idBounds {
        this.AddBounds(bounds)
        return this
    }

    fun Compare(a: idBounds): Boolean {
        return (b[0].Compare(a.b[0]) && b[1].Compare(a.b[1]))
    }

    fun Compare(a: idBounds, epsilon: Float): Boolean {
        return (b[0].Compare(a.b[0], epsilon) && b[1].Compare(a.b[1], epsilon))
    }

    fun equals(a: idBounds): Boolean { // exact compare, no epsilon
        return Compare(a)
    }

    fun notEquals(a: idBounds): Boolean { // exact compare, no epsilon
        return !Compare(a)
    }

    override fun hashCode(): Int {
        var hash = 5
        hash = 11 * hash + b.contentDeepHashCode()
        return hash
    }

    override fun equals(obj: Any?): Boolean {
        if (obj == null) {
            return false
        }
        if (javaClass != obj.javaClass) {
            return false
        }
        val other = obj as idBounds
        return Compare(other)
    }

    // inside out bounds
    fun Clear() {
        b[0].set(idMath.INFINITY, idMath.INFINITY, idMath.INFINITY)
        b[1].set(-idMath.INFINITY, -idMath.INFINITY, -idMath.INFINITY)
    }

    // single point at origin
    fun Zero() {
        b[0].set(0.0f, 0.0f, 0.0f)
        b[1].set(0.0f, 0.0f, 0.0f)
    }

    // returns center of bounds
    fun GetCenter(): idVec3 {
        return idVec3((b[1][0] + b[0][0]) * 0.5f, (b[1][1] + b[0][1]) * 0.5f, (b[1][2] + b[0][2]) * 0.5f)
    }

    // returns the radius relative to the bounds origin
    fun GetRadius(): Float {
        var i: Int
        var total: Float
        var b0: Float
        var b1: Float
        total = 0.0f
        i = 0
        while (i < 3) {
            b0 = abs(b[0][i])
            b1 = abs(b[1][i])
            total += if (b0 > b1) {
                b0 * b0
            } else {
                b1 * b1
            }
            i++
        }
        return idMath.Sqrt(total)
    }

    // returns the radius relative to the given center
    fun GetRadius(center: idVec3): Float {
        var i: Int
        var total: Float
        var b0: Float
        var b1: Float
        total = 0.0f
        i = 0
        while (i < 3) {
            b0 = abs(center[i] - b[0][i])
            b1 = abs(b[1][i] - center[i])
            total += if (b0 > b1) {
                b0 * b0
            } else {
                b1 * b1
            }
            i++
        }
        return idMath.Sqrt(total)
    }

    // returns the volume of the bounds
    fun GetVolume(): Float {
        if (b[0][0] >= b[1][0] || b[0][1] >= b[1][1] || b[0][2] >= b[1][2]) {
            return 0.0f
        }
        return ((b[1][0] - b[0][0]) * (b[1][1] - b[0][1]) * (b[1][2] - b[0][2]))
    }

    fun GetSize(): idVec3 {
        return idVec3(b[1][0] - b[0][0], b[1][1] - b[0][1], b[1][2] - b[0][2])
    }

    // returns true if bounds are inside out
    fun IsCleared(): Boolean {
        return b[0][0] > b[1][0]
    }

    // add the bounds, returns true if the bounds expanded
    fun AddBounds(a: idBounds): Boolean {
        var expanded = false
        if (a.b[0][0] < b[0][0]) {
            b[0][0] = a.b[0][0]
            expanded = true
        }
        if (a.b[0][1] < b[0][1]) {
            b[0][1] = a.b[0][1]
            expanded = true
        }
        if (a.b[0][2] < b[0][2]) {
            b[0][2] = a.b[0][2]
            expanded = true
        }
        if (a.b[1][0] > b[1][0]) {
            b[1][0] = a.b[1][0]
            expanded = true
        }
        if (a.b[1][1] > b[1][1]) {
            b[1][1] = a.b[1][1]
            expanded = true
        }
        if (a.b[1][2] > b[1][2]) {
            b[1][2] = a.b[1][2]
            expanded = true
        }
        return expanded
    }

    // add the point, returns true if the bounds expanded
    fun AddPoint(v: idVec3): Boolean {
        var expanded = false
        if (v[0] < b[0][0]) {
            b[0][0] = v[0]
            expanded = true
        }
        if (v[0] > b[1][0]) {
            b[1][0] = v[0]
            expanded = true
        }
        if (v[1] < b[0][1]) {
            b[0][1] = v[1]
            expanded = true
        }
        if (v[1] > b[1][1]) {
            b[1][1] = v[1]
            expanded = true
        }
        if (v[2] < b[0][2]) {
            b[0][2] = v[2]
            expanded = true
        }
        if (v[2] > b[1][2]) {
            b[1][2] = v[2]
            expanded = true
        }
        return expanded
    }

    // return intersection of this bounds with the given bounds
    fun Intersect(a: idBounds): idBounds {
        val n = idBounds()
        n.b[0][0] = if (a.b[0][0] > b[0][0]) a.b[0][0] else b[0][0]
        n.b[0][1] = if (a.b[0][1] > b[0][1]) a.b[0][1] else b[0][1]
        n.b[0][2] = if (a.b[0][2] > b[0][2]) a.b[0][2] else b[0][2]
        n.b[1][0] = if (a.b[1][0] < b[1][0]) a.b[1][0] else b[1][0]
        n.b[1][1] = if (a.b[1][1] < b[1][1]) a.b[1][1] else b[1][1]
        n.b[1][2] = if (a.b[1][2] < b[1][2]) a.b[1][2] else b[1][2]
        return n
    }

    // intersect this bounds with the given bounds
    fun IntersectSelf(a: idBounds): idBounds {
        if (a.b[0][0] > b[0][0]) {
            b[0][0] = a.b[0][0]
        }
        if (a.b[0][1] > b[0][1]) {
            b[0][1] = a.b[0][1]
        }
        if (a.b[0][2] > b[0][2]) {
            b[0][2] = a.b[0][2]
        }
        if (a.b[1][0] < b[1][0]) {
            b[1][0] = a.b[1][0]
        }
        if (a.b[1][1] < b[1][1]) {
            b[1][1] = a.b[1][1]
        }
        if (a.b[1][2] < b[1][2]) {
            b[1][2] = a.b[1][2]
        }
        return this
    }

    /**
     * @return bounds expanded in all directions with the given value
     */
    fun Expand(d: Float): idBounds {
        return idBounds(
            idVec3(b[0][0] - d, b[0][1] - d, b[0][2] - d),
            idVec3(b[1][0] + d, b[1][1] + d, b[1][2] + d)
        )
    }

    /**
     * expand bounds in all directions with the given value
     */
    fun ExpandSelf(d: Float): idBounds {
        b[0][0] -= d
        b[0][1] -= d
        b[0][2] -= d
        b[1][0] += d
        b[1][1] += d
        b[1][2] += d
        return this
    }

    fun Translate(translation: idVec3): idBounds { // return translated bounds
        return idBounds(b[0] + translation, b[1] + translation)
    }

    // translate this bounds
    fun TranslateSelf(translation: idVec3): idBounds {
        b[0].plusAssign(translation)
        b[1].plusAssign(translation)
        return this
    }

    // return rotated bounds
    fun Rotate(rotation: idMat3): idBounds {
        val bounds = idBounds()
        bounds.FromTransformedBounds(this, vec3_origin, rotation)
        return bounds
    }

    // rotate this bounds
    fun RotateSelf(rotation: idMat3): idBounds {
        FromTransformedBounds(this, vec3_origin, rotation)
        return this
    }

    fun PlaneDistance(plane: idPlane): Float {
        val center = idVec3()
        val d1: Float
        val d2: Float

        center.set((b[0] + b[1]) * 0.5f)

        d1 = plane.Distance(center)
        d2 =
            (abs((b[1][0] - center[0]) * plane.Normal()[0]) +
                    abs((b[1][1] - center[1]) * plane.Normal()[1]) +
                    abs((b[1][2] - center[2]) * plane.Normal()[2]))
        if (d1 - d2 > 0.0f) {
            return d1 - d2
        }
        return if (d1 + d2 < 0.0f) {
            d1 + d2
        } else 0.0f
    }


    fun PlaneSide(plane: idPlane, epsilon: Float = ON_EPSILON): Int {
        val center = idVec3()
        val d1: Float
        val d2: Float
        center.set((b[0] + b[1]) * 0.5f)
        d1 = plane.Distance(center)
        d2 =
            (abs((b[1][0] - center[0]) * plane.Normal()[0]) +
                    abs((b[1][1] - center[1]) * plane.Normal()[1]) +
                    abs((b[1][2] - center[2]) * plane.Normal()[2]))
        if (d1 - d2 > epsilon) {
            return PLANESIDE_FRONT
        }
        return if (d1 + d2 < -epsilon) {
            PLANESIDE_BACK
        } else PLANESIDE_CROSS
    }

    // includes touching
    fun ContainsPoint(p: idVec3): Boolean {
        return !(p[0] < b[0][0] || p[1] < b[0][1] || p[2] < b[0][2]
                || p[0] > b[1][0] || p[1] > b[1][1] || p[2] > b[1][2])
    }

    // includes touching
    fun IntersectsBounds(a: idBounds): Boolean {
        return !(a.b[1][0] < b[0][0] || a.b[1][1] < b[0][1] || a.b[1][2] < b[0][2]
                || a.b[0][0] > b[1][0] || a.b[0][1] > b[1][1] || a.b[0][2] > b[1][2])
    }

    /*
     ============
     idBounds::LineIntersection

     Returns true if the line intersects the bounds between the start and end point.
     ============
     */
    fun LineIntersection(start: idVec3, end: idVec3): Boolean {
        val ld = FloatArray(3)
        val center = (b[0] + b[1]) * 0.5f
        val extents = b[1] - center
        val lineDir = (end - start) * 0.5f
        val lineCenter = start + lineDir
        val dir = lineCenter - center

        ld[0] = abs(lineDir[0])

        if (abs(dir[0]) > extents[0] + ld[0]) {
            return false
        }

        ld[1] = abs(lineDir[1])
        if (abs(dir[1]) > extents[1] + ld[1]) {
            return false
        }

        ld[2] = abs(lineDir[2])
        if (abs(dir[2]) > extents[2] + ld[2]) {
            return false
        }
        val cross = lineDir.Cross(dir)

        if (abs(cross[0]) > extents[1] * ld[2] + extents[2] * ld[1]) {
            return false
        }

        return if (abs(cross[1]) > extents[0] * ld[2] + extents[2] * ld[0]) {
            false
        } else abs(cross[2]) <= extents[0] * ld[1] + extents[1] * ld[0]
    }

    /*
     ============
     idBounds::RayIntersection

     Returns true if the ray intersects the bounds.
     The ray can intersect the bounds in both directions from the start point.
     If start is inside the bounds it is considered an intersection with scale = 0
     ============
     */
    fun RayIntersection(
        start: idVec3, dir: idVec3, scale: CFloat
    ): Boolean { // intersection point is start + dir * scale
        var i: Int
        var ax0: Int
        val ax1: Int
        val ax2: Int
        var side: Int
        var inside: Int
        var f: Float
        val hit = idVec3()
        ax0 = -1
        inside = 0
        i = 0
        while (i < 3) {
            side = if (start[i] < b[0][i]) {
                0
            } else if (start[i] > b[1][i]) {
                1
            } else {
                inside++
                i++
                continue
            }
            if (dir[i] == 0.0f) {
                i++
                continue
            }
            f = start[i] - b[side][i]
            if (ax0 < 0 || abs(f) > abs(scale._val * dir[i])) {
                scale._val = -(f / dir[i])
                ax0 = i
            }
            i++
        }
        if (ax0 < 0) {
            scale._val = 0.0f
            // return true if the start point is inside the bounds
            return inside == 3
        }

        ax1 = (ax0 + 1) % 3
        ax2 = (ax0 + 2) % 3

        hit[ax1] = start[ax1] + scale._val * dir[ax1]
        hit[ax2] = start[ax2] + scale._val * dir[ax2]

        return hit[ax1] >= b[0][ax1] && hit[ax1] <= b[1][ax1] && hit[ax2] >= b[0][ax2] && hit[ax2] <= b[1][ax2]
    }

    // most tight bounds for the given transformed bounds
    fun FromTransformedBounds(bounds: idBounds, origin: idVec3, axis: idMat3) {
        var i: Int
        val center = idVec3()
        val extents = idVec3()
        val rotatedExtents = idVec3()
        center.set((bounds[0] + bounds[1]) * 0.5f)
        extents.set(bounds[1] - center)
        i = 0
        while (i < 3) {
            rotatedExtents[i] =
                (abs(extents[0] * axis[0][i]) +
                        abs(extents[1] * axis[1][i]) +
                        abs(extents[2] * axis[2][i]))
            i++
        }
        center.set(origin + center * axis)
        b[0].set(center - rotatedExtents)
        b[1].set(center + rotatedExtents)
    }

    /*
     ============
     idBounds::FromPoints

     Most tight bounds for a point set.
     ============
     */
    fun FromPoints(points: Array<idVec3>, numPoints: Int) { // most tight bounds for a point set
        SIMDProcessor!!.MinMax(b[0], b[1], points, numPoints)
    }

    /*
     ============
     idBounds::FromPointTranslation

     Most tight bounds for the translational movement of the given point.
     ============
     */
    fun FromPointTranslation(point: idVec3, translation: idVec3) { // most tight bounds for a translation
        var i: Int
        i = 0
        while (i < 3) {
            if (translation[i] < 0.0f) {
                b[0][i] = point[i] + translation[i]
                b[1][i] = point[i]
            } else {
                b[0][i] = point[i]
                b[1][i] = point[i] + translation[i]
            }
            i++
        }
    }

    /*
     ============
     idBounds::FromBoundsTranslation

     Most tight bounds for the translational movement of the given bounds.
     ============
     */
    fun FromBoundsTranslation(bounds: idBounds, origin: idVec3, axis: idMat3, translation: idVec3) {
        var i: Int
        if (axis.IsRotated()) {
            FromTransformedBounds(bounds, origin, axis)
        } else {
            b[0].set(bounds[0] + origin)
            b[1].set(bounds[1] + origin)
        }
        i = 0
        while (i < 3) {
            if (translation[i] < 0.0f) {
                b[0].plusAssign(i, translation[i])
            } else {
                b[1].plusAssign(i, translation[i])
            }
            i++
        }
    }

    /*
     ============
     idBounds::FromPointRotation

     Most tight bounds for the rotational movement of the given point.
     ============
     */
    fun FromPointRotation(point: idVec3, rotation: idRotation) { // most tight bounds for a rotation
        val radius: Float
        if (abs(rotation.GetAngle()) < 180.0f) {
            this.set(BoundsForPointRotation(point, rotation))
        } else {
            radius = (point - rotation.GetOrigin()).Length()

            // FIXME: these bounds are usually way larger
            b[0].set(-radius, -radius, -radius)
            b[1].set(radius, radius, radius)
        }
    }

    /*
     ============
     idBounds::FromBoundsRotation

     Most tight bounds for the rotational movement of the given bounds.
     ============
     */
    fun FromBoundsRotation(bounds: idBounds, origin: idVec3, axis: idMat3, rotation: idRotation) {
        var i: Int
        val radius: Float
        val point = idVec3()
        if (abs(rotation.GetAngle()) < 180.0f) {
            this.set(
                BoundsForPointRotation(
                    bounds[0] * axis + origin, rotation
                )
            )
            i = 1
            while (i < 8) {
                point[0] = bounds[i xor (i shr 1) and 1][0]
                point[1] = bounds[i shr 1 and 1][1]
                point[2] = bounds[i shr 2 and 1][2]
                this.plusAssign(BoundsForPointRotation(point * axis + origin, rotation))
                i++
            }
        } else {
            point.set((bounds[1] - bounds[0]) * 0.5f)
            radius = (bounds[1] - point).Length() + (point - rotation.GetOrigin()).Length()

            // FIXME: these bounds are usually way larger
            b[0].set(-radius, -radius, -radius)
            b[1].set(radius, radius, radius)
        }
    }

    fun ToPoints(points: Array<idVec3>) {
        for (i in 0..7) {
            points[i][0] = b[i xor (i shr 1) and 1][0]
            points[i][1] = b[i shr 1 and 1][1]
            points[i][2] = b[i shr 2 and 1][2]
        }
    }

    fun ToSphere(): idSphere {
        val sphere = idSphere()
        sphere.SetOrigin((b[0] + b[1]) * 0.5f)
        sphere.SetRadius((b[1] - sphere.GetOrigin()).Length())
        return sphere
    }

    fun AxisProjection(dir: idVec3, min: CFloat, max: CFloat) {
        val d1: Float
        val d2: Float
        val center = idVec3()
        val extents = idVec3()

        center.set((b[0] + b[1]) * 0.5f)
        extents.set(b[1] - center)

        d1 = dir * center
        d2 = (abs(extents[0] * dir[0]) + abs(extents[1] * dir[1]) + abs(extents[2] * dir[2]))
        min._val = d1 - d2
        max._val = d1 + d2
    }

    fun AxisProjection(origin: idVec3, axis: idMat3, dir: idVec3, min: CFloat, max: CFloat) {
        val d1: Float
        val d2: Float
        val center = idVec3()
        val extents = idVec3()

        center.set((b[0] + b[1]) * 0.5f)
        extents.set(b[1] - center)
        center.set(origin + center * axis)

        d1 = dir * center
        d2 = abs(extents[0] * (dir * axis[0])) + abs(extents[1] * (dir * axis[1])) + abs(extents[2] * (dir * axis[2]))

        min._val = d1 - d2
        max._val = d1 + d2
    }

    override fun toString(): String {
        return b.contentToString()
    }

    override fun AllocBuffer(): ByteBuffer {
        return ByteBuffer.allocate(BYTES)
    }

    override fun Read(buffer: ByteBuffer) {
        b[0].Read(buffer)
        b[1].Read(buffer)
    }

    override fun Write(): ByteBuffer {
        val buffer = AllocBuffer()
        buffer.put(b[0].Write()).put(b[1].Write()).flip()
        return buffer
    }

    companion object {
        val BYTES: Int = idVec3.BYTES * 2

        fun ClearBounds(): idBounds {
            val idBounds = idBounds()
            idBounds.Clear()
            return idBounds
        }
    }
}

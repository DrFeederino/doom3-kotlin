package neo.idlib.math

import neo.idlib.containers.CFloat
import neo.idlib.math.Matrix.idMat2
import neo.idlib.math.Matrix.idMat3
import kotlin.math.abs

const val DEGENERATE_DIST_EPSILON = 1e-4f
const val ON_EPSILON = 0.1f
const val PLANESIDE_BACK = 1
const val PLANESIDE_CROSS = 3

// plane sides
const val PLANESIDE_FRONT = 0
const val PLANETYPE_NEGX = 3
const val PLANETYPE_TRUEAXIAL = 6
const val SIDE_BACK = 1
const val SIDE_CROSS = 3

//
const val SIDE_FRONT = 0
const val SIDE_ON = 2
private const val PLANESIDE_ON = 2
private const val PLANETYPE_NEGY = 4
private const val PLANETYPE_NEGZ = 5
private const val PLANETYPE_NONAXIAL = 9

// plane types
private const val PLANETYPE_X = 0
private const val PLANETYPE_Y = 1
private const val PLANETYPE_Z = 2

// all types < 6 are true axial planes
private const val PLANETYPE_ZEROX = 6
private const val PLANETYPE_ZEROY = 7
private const val PLANETYPE_ZEROZ = 8

// Global plane constants
val plane_origin = idPlane(0.0f, 0.0f, 0.0f, 0.0f)
val plane_zero = plane_origin

/*
 ===============================================================================

 3D plane with equation: a * x + b * y + c * z + d = 0

 ===============================================================================
 */
class idPlane {
    private val abc: idVec3 = idVec3()
    private var d = 0.0f

    constructor()
    constructor(a: Float, b: Float, c: Float, d: Float) {
        abc.x = a
        abc.y = b
        abc.z = c
        this.d = d
    }

    constructor(a: Int, b: Int, c: Int, d: Int) {
        abc.x = a.toFloat()
        abc.y = b.toFloat()
        abc.z = c.toFloat()
        this.d = d.toFloat()
    }

    constructor(array: FloatArray) {
        abc.x = array[0]
        abc.y = array[1]
        abc.z = array[2]
        d = array[3]
    }

    constructor(normal: idVec3, dist: Float) {
        abc.set(normal)
        d = -dist
    }

    constructor(vec: idVec4) {
        abc.x = vec.x
        abc.y = vec.y
        abc.z = vec.z
        d = vec.w
    }

    constructor(v0: idVec3, v1: idVec3, v2: idVec3, fixDegenerate: Boolean = false) {
        FromPoints(v0, v1, v2, fixDegenerate)
    }

    constructor(plane: idPlane) {
        abc.x = plane.abc.x
        abc.y = plane.abc.y
        abc.z = plane.abc.z
        d = plane.d
    }

    operator fun get(index: Int): Float {
        return when (index) {
            0 -> abc.x
            1 -> abc.y
            2 -> abc.z
            else -> d
        }
    }

    operator fun set(index: Int, value: Float): Float {
        when (index) {
            0 -> value.also { abc.x = it }
            1 -> value.also { abc.y = it }
            2 -> value.also { abc.z = it }
            3 -> value.also { d = it }
        }
        return value
    }

    fun plusAssign(index: Int, value: Float): Float {
        return when (index) {
            0 -> value.let { abc.x += it; abc.x }
            1 -> value.let { abc.y += it; abc.y }
            2 -> value.let { abc.z += it; abc.z }
            else -> value.let { d += it; d }
        }
    }

    fun minusAssign(index: Int, value: Float): Float {
        return when (index) {
            0 -> value.let { abc.x -= it; abc.x }
            1 -> value.let { abc.y -= it; abc.y }
            2 -> value.let { abc.z -= it; abc.z }
            else -> value.let { d -= it; d }
        }
    }

    fun divAssign(index: Int, value: Float): Float {
        return when (index) {
            0 -> value.let { abc.x /= it; abc.x }
            1 -> value.let { abc.y /= it; abc.y }
            2 -> value.let { abc.z /= it; abc.z }
            else -> value.let { d /= it; d }
        }
    }

    operator fun unaryMinus(): idPlane {
        return idPlane(-abc.x, -abc.y, -abc.z, -d)
    }

    fun set(v: idVec3): idPlane {
        abc.set(v)
        d = 0.0f
        return this
    }

    fun set(p: idPlane): idPlane {
        abc.set(p.abc)
        d = p.d
        return this
    }

    operator fun plus(p: idPlane): idPlane {
        return idPlane(abc.x + p.abc.x, abc.y + p.abc.y, abc.z + p.abc.z, d + p.d)
    }

    operator fun minus(p: idPlane): idPlane {
        return idPlane(abc.x - p.abc.x, abc.y - p.abc.y, abc.z - p.abc.z, d - p.d)
    }

    operator fun times(s: Float): idPlane {
        return idPlane(abc.x * s, abc.y * s, abc.z * s, d * s)
    }


    fun timesAssign(m: idMat3): idPlane {
        Normal().timesAssign(m)
        return this
    }

    fun Compare(p: idPlane): Boolean {
        return abc.x == p.abc.x && abc.y == p.abc.y && abc.z == p.abc.z && d == p.d
    }

    fun Compare(p: idPlane, epsilon: Float): Boolean {
        if (abs(abc.x - p.abc.x) > epsilon) {
            return false
        }
        if (abs(abc.y - p.abc.y) > epsilon) {
            return false
        }
        if (abs(abc.z - p.abc.z) > epsilon) {
            return false
        }
        if (abs(d - p.d) > epsilon) {
            return false
        }
        return true
    }

    fun Compare(p: idPlane, normalEps: Float, distEps: Float): Boolean {
        if (abs(d - p.d) > distEps) {
            return false
        }
        return Normal().Compare(p.Normal(), normalEps)
    }

    override fun hashCode(): Int {
        var hash = 7
        hash = 23 * hash + abc.x.toBits()
        hash = 23 * hash + abc.y.toBits()
        hash = 23 * hash + abc.z.toBits()
        hash = 23 * hash + d.toBits()
        return hash
    }

    override fun equals(other: Any?): Boolean {
        if (other == null) {
            return false
        }
        if (javaClass != other.javaClass) {
            return false
        }
        val p = other as idPlane
        return Compare(p)
    }

    fun Zero() {
        d = 0.0f
        abc.z = d
        abc.y = abc.z
        abc.x = abc.y
    }

    // sets the normal
    fun SetNormal(normal: idVec3) {
        abc.set(normal)
    }

    // reference to const normal
    fun Normal(): idVec3 = abc

    fun NormalX(value: Float): Float {
        return value.also { abc.x = it }
    }

    fun NormalY(value: Float): Float {
        return value.also { abc.y = it }
    }

    fun NormalZ(value: Float): Float {
        return value.also { abc.z = it }
    }

    /**
     * sets the normal **ONLY**; a, b and c. d is ignored.
     */
    fun oNorSet(v: idVec3): idPlane {
        abc.set(v)
        return this
    }

    fun Normalize(fixDegenerate: Boolean = true): Float {
        val vec3 = idVec3(abc.x, abc.y, abc.z)
        val length = vec3.Normalize()
        val oldD = d //save old d
        this.set(vec3) //set normalized values
        d = oldD //replace the zeroed d with its original value
        if (fixDegenerate) {
            FixDegenerateNormal()
        }
        return length
    }

    // fix degenerate normal
    fun FixDegenerateNormal(): Boolean {
        val vec3 = idVec3(abc.x, abc.y, abc.z)
        val fixedNormal = vec3.FixDegenerateNormal()
        run {
            val oldD = d //save old d
            this.set(vec3) //set new values
            d = oldD //replace the zeroed d with its original value
        }
        return fixedNormal
    }

    // fix degenerate normal and dist
    fun FixDegeneracies(distEpsilon: Float): Boolean {
        val fixedNormal = FixDegenerateNormal()
        // only fix dist if the normal was degenerate
        if (fixedNormal) {
            if (abs(d - idMath.Rint(d)) < distEpsilon) {
                d = idMath.Rint(d)
            }
        }
        return fixedNormal
    }

    // returns: -d
    fun Dist(): Float {
        return -d
    }

    // sets: d = -dist
    fun SetDist(dist: Float) {
        d = -dist
    }

    // returns plane type
    fun Type(): Int {
        return if (Normal()[0] == 0.0f) {
            if (Normal()[1] == 0.0f) {
                if (Normal()[2] > 0.0f) PLANETYPE_Z else PLANETYPE_NEGZ
            } else if (Normal()[2] == 0.0f) {
                if (Normal()[1] > 0.0f) PLANETYPE_Y else PLANETYPE_NEGY
            } else {
                PLANETYPE_ZEROX
            }
        } else if (Normal()[1] == 0.0f) {
            if (Normal()[2] == 0.0f) {
                if (Normal()[0] > 0.0f) PLANETYPE_X else PLANETYPE_NEGX
            } else {
                PLANETYPE_ZEROY
            }
        } else if (Normal()[2] == 0.0f) {
            PLANETYPE_ZEROZ
        } else {
            PLANETYPE_NONAXIAL
        }
    }


    fun FromPoints(p1: idVec3, p2: idVec3, p3: idVec3, fixDegenerate: Boolean = true): Boolean {
        Normal().set((p1 - p2).Cross(p3 - p2))
        if (Normalize(fixDegenerate) == 0.0f) {
            return false
        }
        d = -(Normal() * p2)
        return true
    }


    fun FromVecs(dir1: idVec3, dir2: idVec3, p: idVec3, fixDegenerate: Boolean = true): Boolean {
        val vec3 = idVec3(Normal().set(dir1.Cross(dir2)))
        run {
            val oldD = d //save old d
            this.set(vec3) //set new values
            d = oldD //replace the zeroed d with its original value
        }
        if (Normalize(fixDegenerate) == 0.0f) {
            return false
        }
        d = -(Normal() * p)
        return true
    }

    // assumes normal is valid
    fun FitThroughPoint(p: idVec3) {
        d = -(Normal() * p)
    }

    fun HeightFit(points: Array<idVec3>, numPoints: Int): Boolean {
        var i: Int
        var sumXX = 0.0f
        var sumXY = 0.0f
        var sumXZ = 0.0f
        var sumYY = 0.0f
        var sumYZ = 0.0f
        val sum = idVec3()
        val average = idVec3()
        val dir = idVec3()

        if (numPoints == 1) {
            abc.x = 0.0f
            abc.y = 0.0f
            abc.z = 1.0f
            d = -points[0].z
            return true
        }
        if (numPoints == 2) {
            dir.set(points[1] - points[0])
            // Normal() = dir.Cross( idVec3( 0, 0, 1 ) ).Cross( dir );
            run {
                val oldD = d //save old d
                this.set(dir.Cross(idVec3(0.0f, 0.0f, 1.0f)).Cross(dir))
                d = oldD //replace the zeroed d with its original value
            }
            Normalize()
            d = -(Normal() * points[0])
            return true
        }

        sum.Zero()
        i = 0
        while (i < numPoints) {
            sum.plusAssign(points[i])
            i++
        }
        average.set(sum / numPoints)

        i = 0
        while (i < numPoints) {
            dir.set(points[i] - average)
            sumXX += dir.x * dir.x
            sumXY += dir.x * dir.y
            sumXZ += dir.x * dir.z
            sumYY += dir.y * dir.y
            sumYZ += dir.y * dir.z
            i++
        }

        val m = idMat2(sumXX, sumXY, sumXY, sumYY)
        if (!m.InverseSelf()) {
            return false
        }

        abc.x = -sumXZ * m[0][0] - sumYZ * m[0][1]
        abc.y = -sumXZ * m[1][0] - sumYZ * m[1][1]
        abc.z = 1.0f
        Normalize()
        d = -(abc.x * average.x + abc.y * average.y + abc.z * average.z)
        return true
    }

    fun Translate(translation: idVec3): idPlane {
        return idPlane(abc.x, abc.y, abc.z, d - translation * Normal())
    }

    fun TranslateSelf(translation: idVec3): idPlane {
        d -= translation * Normal()
        return this
    }

    fun Rotate(origin: idVec3, axis: idMat3): idPlane {
        val p = idPlane()
        p.Normal().set(Normal() * axis)
        p.d = d + origin * Normal() - origin * p.Normal()
        return p
    }

    fun RotateSelf(origin: idVec3, axis: idMat3): idPlane {
        d += origin * Normal()
        Normal().timesAssign(axis)
        d -= origin * Normal()

        return this
    }

    fun Distance(v: idVec3): Float {
        return abc.x * v.x + abc.y * v.y + abc.z * v.z + d
    }


    fun Side(v: idVec3, epsilon: Float = 0.0f): Int {
        val dist = Distance(v)
        return if (dist > epsilon) {
            PLANESIDE_FRONT
        } else if (dist < -epsilon) {
            PLANESIDE_BACK
        } else {
            PLANESIDE_ON
        }
    }

    fun LineIntersection(start: idVec3, end: idVec3): Boolean {
        val d1: Float
        val d2: Float
        val fraction: Float
        d1 = Normal() * start + d
        d2 = Normal() * end + d
        if (d1 == d2) {
            return false
        }
        if (d1 > 0.0f && d2 > 0.0f) {
            return false
        }
        if (d1 < 0.0f && d2 < 0.0f) {
            return false
        }
        fraction = d1 / (d1 - d2)
        return fraction in 0.0f..1.0f
    }

    // intersection point is start + dir * scale
    fun RayIntersection(start: idVec3, dir: idVec3, scale: CFloat): Boolean {
        val d1: Float
        val d2: Float
        d1 = Normal() * start + d
        d2 = Normal() * dir
        if (d2 == 0.0f) {
            return false
        }
        scale._val = (-(d1 / d2))
        return true
    }

    fun PlaneIntersection(plane: idPlane, start: idVec3, dir: idVec3): Boolean {
        val n00: Float
        val n01: Float
        val n11: Float
        val det: Float
        val invDet: Float
        val f0: Float
        val f1: Float

        n00 = Normal().LengthSqr()
        n01 = Normal() * plane.Normal()
        n11 = plane.Normal().LengthSqr()
        det = n00 * n11 - n01 * n01

        if (abs(det) < 1e-6f) {
            return false
        }
        invDet = 1.0f / det
        f0 = (n01 * plane.d - n11 * d) * invDet
        f1 = (n01 * d - n00 * plane.d) * invDet
        dir.set(Normal().Cross(plane.Normal()))
        start.set(Normal() * f0 + plane.Normal() * f1)
        return true
    }

    fun GetDimension(): Int {
        return 4
    }

    fun ToVec4(): idVec4 {
        return idVec4(abc.x, abc.y, abc.z, d)
    }

    fun ToString(precision: Int = 2): String {
        return String.format(
            "%." + precision + "f %." + precision + "f %." + precision + "f %." + precision + "f",
            abc.x, abc.y, abc.z, d
        )
    }

    fun ToVec4_oPluSet(v: idVec4) {
        abc.x += v.x
        abc.y += v.y
        abc.z += v.z
        d += v.w
    }

    fun ToVec4_ToVec3_Cross(a: idVec3, b: idVec3) {
        abc.Cross(a, b)
    }

    fun ToVec4_ToVec3_Normalize() {
        abc.Normalize()
    }

    fun ToFloatPtr(): FloatArray {
        return floatArrayOf(abc.x, abc.y, abc.z, d)
    }

    override fun toString(): String {
        return ("idPlane{"
                + "a=" + abc.x
                + ", b=" + abc.y
                + ", c=" + abc.z
                + ", d=" + d + "}")
    }

    companion object {
        val BYTES: Int = idVec3.BYTES + java.lang.Float.BYTES
        fun generateArray(length: Int): Array<idPlane> {
            return Array(length) { idPlane() }
        }
    }
}

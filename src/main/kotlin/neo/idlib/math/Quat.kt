package neo.idlib.math

import neo.idlib.Text.Str.idStr
import neo.idlib.math.Matrix.idMat3
import neo.idlib.math.Matrix.idMat4
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * ===============================================================================
 *
 *
 * Quaternion
 *
 *
 * ===============================================================================
 */
class idQuat {
    var x = 0.0f
    var y = 0.0f
    var z = 0.0f
    var w = 0.0f

    constructor()
    constructor(x: Float, y: Float, z: Float, w: Float) {
        this.x = x
        this.y = y
        this.z = z
        this.w = w
    }

    constructor(quat: idQuat) {
        x = quat.x
        y = quat.y
        z = quat.z
        w = quat.w
    }

    fun set(x: Float, y: Float, z: Float, w: Float) {
        this.x = x
        this.y = y
        this.z = z
        this.w = w
    }

    fun set(a: idQuat): idQuat {
        x = a.x
        y = a.y
        z = a.z
        w = a.w
        return this
    }

    operator fun get(index: Int): Float {
        require(index >= 0 && index < 4) { "Index out of bounds: $index" }
        return when (index) {
            0 -> x
            1 -> y
            2 -> z
            3 -> w
            else -> throw IndexOutOfBoundsException("Index out of bounds: $index")
        }
    }

    operator fun set(index: Int, value: Float) {
        require(index >= 0 && index < 4) { "Index out of bounds: $index" }
        when (index) {
            0 -> x = value
            1 -> y = value
            2 -> z = value
            3 -> w = value
        }
    }

    operator fun unaryMinus(): idQuat {
        return idQuat(-x, -y, -z, -w)
    }

    operator fun plus(a: idQuat): idQuat {
        return idQuat(x + a.x, y + a.y, z + a.z, w + a.w)
    }

    fun plusAssign(a: idQuat): idQuat {
        x += a.x
        y += a.y
        z += a.z
        w += a.w
        return this
    }

    operator fun minus(a: idQuat): idQuat {
        return idQuat(x - a.x, y - a.y, z - a.z, w - a.w)
    }

    fun minusAssign(a: idQuat): idQuat {
        x -= a.x
        y -= a.y
        z -= a.z
        w -= a.w
        return this
    }

    operator fun times(a: idQuat): idQuat {
        return idQuat(
            w * a.x + x * a.w + y * a.z - z * a.y,
            w * a.y + y * a.w + z * a.x - x * a.z,
            w * a.z + z * a.w + x * a.y - y * a.x,
            w * a.w - x * a.x - y * a.y - z * a.z
        )
    }

    operator fun times(a: idVec3): idVec3 {
        val xxzz = x * x - z * z
        val wwyy = w * w - y * y
        val xw2 = x * w * 2.0f
        val xy2 = x * y * 2.0f
        val xz2 = x * z * 2.0f
        val yw2 = y * w * 2.0f
        val yz2 = y * z * 2.0f
        val zw2 = z * w * 2.0f
        return idVec3(
            (xxzz + wwyy) * a.x + (xy2 + zw2) * a.y + (xz2 - yw2) * a.z,
            (xy2 - zw2) * a.x + (y * y + w * w - x * x - z * z) * a.y + (yz2 + xw2) * a.z,
            (xz2 + yw2) * a.x + (yz2 - xw2) * a.y + (wwyy - xxzz) * a.z
        )
    }

    operator fun times(a: Float): idQuat {
        return idQuat(x * a, y * a, z * a, w * a)
    }

    fun timesAssign(a: idQuat): idQuat {
        this.set(this * a)
        return this
    }

    fun timesAssign(a: Float): idQuat {
        x *= a
        y *= a
        z *= a
        w *= a
        return this
    }

    operator fun compareTo(a: idQuat): Int {
        return when {
            x < a.x -> -1
            x > a.x -> 1
            y < a.y -> -1
            y > a.y -> 1
            z < a.z -> -1
            z > a.z -> 1
            w < a.w -> -1
            w > a.w -> 1
            else -> 0
        }
    }

    fun Compare(a: idQuat): Boolean { // exact compare, no epsilon
        return x == a.x && y == a.y && z == a.z && w == a.w
    }

    fun Compare(a: idQuat, epsilon: Float): Boolean { // compare with epsilon
        if (abs(x - a.x) > epsilon) {
            return false
        }
        if (abs(y - a.y) > epsilon) {
            return false
        }
        if (abs(z - a.z) > epsilon) {
            return false
        }
        return abs(w - a.w) <= epsilon
    }

    fun Inverse(): idQuat {
        return idQuat(-x, -y, -z, w)
    }

    fun Length(): Float {
        val len: Float
        len = x * x + y * y + z * z + w * w
        return idMath.Sqrt(len)
    }

    fun Normalize(): idQuat {
        val len: Float
        val ilength: Float
        len = Length()
        if (len != 0.0f) {
            ilength = 1 / len
            x *= ilength
            y *= ilength
            z *= ilength
            w *= ilength
        }
        return this
    }

    fun CalcW(): Float { // take the absolute value because floating point rounding may cause the dot of x,y,z to be larger than 1
        return sqrt(abs(1.0f - (x * x + y * y + z * z)))
    }

    fun GetDimension(): Int {
        return 4
    }

    fun ToAngles(): idAngles {
        return ToMat3().ToAngles()
    }

    fun ToRotation(): idRotation {
        val vec = idVec3()
        var angle: Float
        vec.x = x
        vec.y = y
        vec.z = z
        angle = idMath.ACos(w)
        if (angle == 0.0f) {
            vec.set(0.0f, 0.0f, 1.0f)
        } else {
            vec.Normalize()
            vec.FixDegenerateNormal()
            angle *= 2.0f * idMath.M_RAD2DEG
        }
        return idRotation(vec3_origin, vec, angle)
    }

    fun ToMat3(): idMat3 {
        val mat = idMat3()
        val wx: Float
        val wy: Float
        val wz: Float
        val xx: Float
        val yy: Float
        val yz: Float
        val xy: Float
        val xz: Float
        val zz: Float
        val x2: Float
        val y2: Float
        val z2: Float
        x2 = x + x
        y2 = y + y
        z2 = z + z
        xx = x * x2
        xy = x * y2
        xz = x * z2
        yy = y * y2
        yz = y * z2
        zz = z * z2
        wx = w * x2
        wy = w * y2
        wz = w * z2
        mat.set(0, 0, 1.0f - (yy + zz))
        mat.set(0, 1, xy - wz)
        mat.set(0, 2, xz + wy)
        mat.set(1, 0, xy + wz)
        mat.set(1, 1, 1.0f - (xx + zz))
        mat.set(1, 2, yz - wx)
        mat.set(2, 0, xz - wy)
        mat.set(2, 1, yz + wx)
        mat.set(2, 2, 1.0f - (xx + yy))
        return mat
    }

    fun ToMat4(): idMat4 {
        return ToMat3().ToMat4()
    }

    fun ToCQuat(): idCQuat {
        return if (w < 0.0f) {
            idCQuat(-x, -y, -z)
        } else idCQuat(x, y, z)
    }

    fun ToAngularVelocity(): idVec3 {
        val vec = idVec3()
        vec.x = x
        vec.y = y
        vec.z = z
        vec.Normalize()
        return vec * idMath.ACos(w)
    }

    fun ToFloatArray(): FloatArray {
        return floatArrayOf(x, y, z, w)
    }

    fun ToFloatPtr(): FloatArray {
        return ToFloatArray()
    }

    fun ToString(precision: Int): String {
        return idStr.FloatArrayToString(ToFloatArray(), GetDimension(), precision)
    }

    /**
     * ===================== idQuat::Slerp
     *
     *
     * Spherical linear interpolation between two quaternions.
     * =====================
     */
    fun Slerp(from: idQuat, to: idQuat, t: Float): idQuat {
        val temp = idQuat()
        val omega: Float
        var cosom: Float
        val sinom: Float
        var scale0: Float
        val scale1: Float
        if (t <= 0.0f) {
            this.set(from)
            return this
        }
        if (t >= 1.0f) {
            this.set(to)
            return this
        }
        if (from == to) {
            this.set(to)
            return this
        }
        cosom = from.x * to.x + from.y * to.y + from.z * to.z + from.w * to.w
        if (cosom < 0.0f) {
            temp.set(-to)
            cosom = -cosom
        } else {
            temp.set(to)
        }
        if (1.0f - cosom > 1e-6f) {
            scale0 = 1.0f - cosom * cosom
            sinom = idMath.InvSqrt(scale0)
            omega = idMath.ATan16(scale0 * sinom, cosom)
            scale0 = idMath.Sin16((1.0f - t) * omega) * sinom
            scale1 = idMath.Sin16(t * omega) * sinom
        } else {
            scale0 = 1.0f - t
            scale1 = t
        }
        this.set((from * scale0) + (temp * scale1))
        return this
    }

    override fun hashCode(): Int {
        var hash = 5
        hash = 31 * hash + x.toBits()
        hash = 31 * hash + y.toBits()
        hash = 31 * hash + z.toBits()
        hash = 31 * hash + w.toBits()
        return hash
    }

    override fun equals(obj: Any?): Boolean {
        if (obj == null) {
            return false
        }
        if (javaClass != obj.javaClass) {
            return false
        }
        val other = obj as idQuat
        return Compare(other)
    }
}


/**
 * ===============================================================================
 *
 *
 * Compressed quaternion
 *
 *
 * ===============================================================================
 */
class idCQuat {
    var x = 0.0f
    var y = 0.0f
    var z = 0.0f

    constructor()
    constructor(x: Float, y: Float, z: Float) {
        this.x = x
        this.y = y
        this.z = z
    }

    fun set(x: Float, y: Float, z: Float) {
        this.x = x
        this.y = y
        this.z = z
    }

    operator fun get(index: Int): Float {
        require(index >= 0 && index < 3) { "Index out of bounds: $index" }
        return when (index) {
            0 -> x
            1 -> y
            2 -> z
            else -> throw IndexOutOfBoundsException("Index out of bounds: $index")
        }
    }

    operator fun set(index: Int, value: Float) {
        require(index >= 0 && index < 3) { "Index out of bounds: $index" }
        when (index) {
            0 -> x = value
            1 -> y = value
            2 -> z = value
        }
    }

    fun Compare(a: idCQuat): Boolean { // exact compare, no epsilon
        return x == a.x && y == a.y && z == a.z
    }

    fun Compare(a: idCQuat, epsilon: Float): Boolean { // compare with epsilon
        if (abs(x - a.x) > epsilon) {
            return false
        }
        if (abs(y - a.y) > epsilon) {
            return false
        }
        return abs(z - a.z) <= epsilon
    }

    override fun hashCode(): Int {
        var hash = 7
        hash = 37 * hash + x.toBits()
        hash = 37 * hash + y.toBits()
        hash = 37 * hash + z.toBits()
        return hash
    }

    override fun equals(obj: Any?): Boolean {
        if (obj == null) {
            return false
        }
        if (javaClass != obj.javaClass) {
            return false
        }
        val other = obj as idCQuat
        return Compare(other)
    }

    fun GetDimension(): Int {
        return 3
    }

    fun ToAngles(): idAngles {
        return ToQuat().ToAngles()
    }

    fun ToRotation(): idRotation {
        return ToQuat().ToRotation()
    }

    fun ToMat3(): idMat3 {
        return ToQuat().ToMat3()
    }

    fun ToMat4(): idMat4 {
        return ToQuat().ToMat4()
    }

    fun ToQuat(): idQuat { // take the absolute value because floating point rounding may cause the dot of x,y,z to be larger than 1
        return idQuat(x, y, z, sqrt(abs(1.0f - (x * x + y * y + z * z))))
    }

    fun ToFloatArray(): FloatArray {
        return floatArrayOf(x, y, z)
    }

    fun ToFloatPtr(): FloatArray {
        return ToFloatArray()
    }

    fun ToString(precision: Int): String {
        return idStr.FloatArrayToString(ToFloatArray(), GetDimension(), precision)
    }
}
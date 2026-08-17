package neo.idlib.math

import neo.framework.File_h.idFile
import neo.idlib.Text.Str.idStr
import neo.idlib.containers.CFloat
import neo.idlib.idSerializable
import neo.idlib.math.Matrix.idMat3
import neo.idlib.math.Matrix.idMat4
import neo.idlib.math.Matrix.idMatX
import neo.idlib.math.Random.idRandom
import org.lwjgl.BufferUtils
import java.nio.ByteBuffer
import java.util.*
import kotlin.math.*

private val vec2_origin: idVec2 get() = idVec2(0.0f, 0.0f)
val vec3_origin: idVec3 get() = idVec3(0.0f, 0.0f, 0.0f)
val vec3_zero: idVec3 get() = vec3_origin
val vec4_origin: idVec4 get() = idVec4(0.0f, 0.0f, 0.0f, 0.0f)
val vec4_zero: idVec4 get() = vec4_origin
val vec5_origin: idVec5 get() = idVec5(0.0f, 0.0f, 0.0f, 0.0f, 0.0f)
val vec6_infinity: idVec6
    get() = idVec6(
        idMath.INFINITY, idMath.INFINITY, idMath.INFINITY, idMath.INFINITY, idMath.INFINITY, idMath.INFINITY
    )
val vec6_origin: idVec6 get() = idVec6(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f)
val vec6_zero: idVec6 get() = vec6_origin

//fun getVec2_origin(): idVec2 {
//    return idVec2(vec2_origin)
//}
//
//fun getVec3Origin(): idVec3 {
//    return idVec3(0.0f, 0.0f, 0.0f)
//}
//
//
//fun vec3_zero: idVec3 {
//    return idVec3(vec3_zero)
//}
//
//fun getVec4_origin(): idVec4 {
//    return idVec4(vec4_origin)
//}
//
//fun getVec4_zero(): idVec4 {
//    return idVec4(vec4_zero)
//}
//
//fun getVec5_origin(): idVec5 {
//    return idVec5(vec5_origin)
//}
//
//fun getVec6_origin(): idVec6 {
//    return idVec6(vec6_origin.p)
//}
//
//fun getVec6_zero(): idVec6 {
//    return idVec6(vec6_zero.p)
//}
//
//fun getVec6_infinity(): idVec6 {
//    return idVec6(vec6_infinity.p)
//}

/*
 ===============================================================================

 Old 3D vector macros, should no longer be used.

 ===============================================================================
 */
fun DotProduct(a: FloatArray, b: FloatArray): Float {
    return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]
}

fun DotProduct(a: idVec3, b: idVec3): Float {
    return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]
}


fun DotProduct(a: idVec3, b: idVec4): Float {
    return DotProduct(a, b.ToVec3())
}

fun DotProduct(a: idVec3, b: idVec5): Float {
    return DotProduct(a, b.ToVec3())
}

fun DotProduct(a: idPlane, b: idPlane): Float {
    return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]
}

fun VectorSubtract(a: FloatArray, b: FloatArray, c: FloatArray): FloatArray {
    c[0] = a[0] - b[0]
    c[1] = a[1] - b[1]
    c[2] = a[2] - b[2]
    return c
}

fun VectorSubtract(a: idVec3, b: idVec3, c: FloatArray): FloatArray {
    c[0] = a[0] - b[0]
    c[1] = a[1] - b[1]
    c[2] = a[2] - b[2]
    return c
}

fun VectorSubtract(a: idVec3, b: idVec3, c: idVec3): idVec3 {
    c[0] = a[0] - b[0]
    c[1] = a[1] - b[1]
    c[2] = a[2] - b[2]
    return c
}

fun VectorAdd(a: FloatArray, b: FloatArray, c: Array<Float>) {
    c[0] = a[0] + b[0]
    c[1] = a[1] + b[1]
    c[2] = a[2] + b[2]
}

fun VectorScale(v: FloatArray, s: Float, o: Array<Float>) {
    o[0] = v[0] * s
    o[1] = v[1] * s
    o[2] = v[2] * s
}

fun VectorMA(v: FloatArray, s: Float, b: FloatArray, o: Array<Float>) {
    o[0] = v[0] + b[0] * s
    o[1] = v[1] + b[1] * s
    o[2] = v[2] + b[2] * s
}

fun VectorMA(v: idVec3, s: Float, b: idVec3, o: idVec3) {
    o[0] = v[0] + b[0] * s
    o[1] = v[1] + b[1] * s
    o[2] = v[2] + b[2] * s
}

fun VectorCopy(a: FloatArray, b: Array<Float>) {
    b[0] = a[0]
    b[1] = a[1]
    b[2] = a[2]
}

fun VectorCopy(a: idVec3, b: idVec3) {
    b.set(a)
}

fun VectorCopy(a: idVec3, b: idVec5) {
    b.set(a)
}

fun VectorCopy(a: idVec5, b: idVec3) {
    b.set(a.ToVec3())
}

interface idVec<T : idVec<T>> {
    operator fun get(index: Int): Float
    fun set(a: T): T
    operator fun set(index: Int, value: Float): Float
    operator fun plus(a: T): T
    operator fun minus(a: T): T
    operator fun div(a: Int): T // used in idlib/math/Plane
    operator fun times(a: T): Float
    operator fun times(a: Float): T
    operator fun times(a: Int): T // used in idlib/math/Curve
    operator fun div(a: Float): T
    fun plusAssign(a: T): T  // too bad kotlin's augmented assigns are Unit-only :(
    fun GetDimension(): Int
    fun Zero()
}

//===============================================================
//
//	idVec2 - 2D vector
//
//===============================================================
class idVec2 : idVec<idVec2>, idSerializable {
    var x = 0.0f
    var y = 0.0f

    constructor(x: Float, y: Float) {
        this.x = x
        this.y = y
    }

    constructor(v: idVec2) {
        x = v.x
        y = v.y
    }

    constructor()

    fun set(x: Float, y: Float) {
        this.x = x
        this.y = y
    }

    override fun Zero() {
        y = 0.0f
        x = y
    }

    override fun set(index: Int, value: Float): Float {
        when (index) {
            0 -> x = value
            1 -> y = value
        }
        return value
    }

    fun plusAssign(index: Int, value: Float): Float {
        return if (index == 1) {
            value.let { y += it; y }
        } else {
            value.let { x += it; x }
        }
    }

    override fun get(index: Int): Float {
        return if (index == 1) {
            y
        } else x
    }

    override fun times(a: idVec2): Float {
        return x * a.x + y * a.y
    }

    override fun times(a: Float): idVec2 {
        return idVec2(x * a, y * a)
    }

    override fun times(a: Int): idVec2 {
        return idVec2(x * a, y * a)
    }

    override fun div(a: Float): idVec2 {
        val inva = 1.0f / a
        return idVec2(x * inva, y * inva)
    }

    override fun plus(a: idVec2): idVec2 {
        return idVec2(x + a.x, y + a.y)
    }

    override fun minus(a: idVec2): idVec2 {
        return idVec2(x - a.x, y - a.y)
    }

    override fun div(a: Int): idVec2 {
        return div(a.toFloat())
    }

    override fun plusAssign(a: idVec2): idVec2 {
        x += a.x
        y += a.y
        return this
    }

    fun minusAssign(a: idVec2): idVec2 {
        x -= a.x
        y -= a.y
        return this
    }

    fun timesAssign(a: Float): idVec2 {
        x *= a
        y *= a
        return this
    }

    fun divAssign(a: idVec2): idVec2 {
        x /= a.x
        y /= a.y
        return this
    }

    override fun set(a: idVec2): idVec2 {
        x = a.x
        y = a.y
        return this
    }

    fun Compare(a: idVec2): Boolean { // exact compare, no epsilon
        return x == a.x && y == a.y
    }

    fun Compare(a: idVec2, epsilon: Float): Boolean { // compare with epsilon
        return if (abs(x - a.x) > epsilon) {
            false
        } else abs(y - a.y) <= epsilon
    }

    fun Length(): Float {
        return idMath.Sqrt(x * x + y * y)
    }

    fun LengthFast(): Float {
        val sqrLength: Float = x * x + y * y
        return sqrLength * idMath.RSqrt(sqrLength)
    }

    fun LengthSqr(): Float {
        return x * x + y * y
    }

    fun Normalize(): Float { // returns length
        val sqrLength: Float = x * x + y * y
        val invLength: Float = idMath.InvSqrt(sqrLength)
        x *= invLength
        y *= invLength
        return invLength * sqrLength
    }

    fun NormalizeFast(): Float { // returns length
        val lengthSqr: Float = x * x + y * y
        val invLength: Float = idMath.RSqrt(lengthSqr)
        x *= invLength
        y *= invLength
        return invLength * lengthSqr
    }

    fun Truncate(length: Float): idVec2 { // cap length
        val length2: Float
        val ilength: Float
        if (length == 0.0f) {
            Zero()
        } else {
            length2 = LengthSqr()
            if (length2 > length * length) {
                ilength = length * idMath.InvSqrt(length2)
                x *= ilength
                y *= ilength
            }
        }
        return this
    }

    fun Clamp(min: idVec2, max: idVec2) {
        if (x < min.x) {
            x = min.x
        } else if (x > max.x) {
            x = max.x
        }
        if (y < min.y) {
            y = min.y
        } else if (y > max.y) {
            y = max.y
        }
    }

    fun Snap() { // snap to closest integer value
        x = floor(x + 0.5f)
        y = floor(y + 0.5f)
    }

    fun SnapInt() { // snap towards integer (floor)
        x = x.toInt().toFloat()
        y = y.toInt().toFloat()
    }

    override fun GetDimension(): Int {
        return 2
    }

    fun ToFloatPtr(): FloatArray {
        return floatArrayOf(x, y)
    }

    fun ToString(precision: Int = 2): String {
        return idStr.FloatArrayToString(ToFloatPtr(), GetDimension(), precision)
    }

    override fun toString(): String {
        return "$x $y"
    }

    /*
     =============
     Lerp

     Linearly inperpolates one vector to another.
     =============
     */
    fun Lerp(v1: idVec2, v2: idVec2, l: Float) {
        if (l <= 0.0f) {
            this.set(v1) //( * this) = v1;
        } else if (l >= 1.0f) {
            this.set(v2) //( * this) = v2;
        } else {
            this.set(v1 + (v2 - v1) * l) //( * this) = v1 + l * (v2 - v1);
        }
    }

    override fun readFrom(file: idFile) {
        x = file.ReadFloat()
        y = file.ReadFloat()
    }

    override fun writeTo(file: idFile) {
        file.WriteFloat(x)
        file.WriteFloat(y)
    }

    companion object {
        val SIZE = 2 * java.lang.Float.SIZE

        val BYTES = SIZE / 8


        fun generateArray(length: Int): Array<idVec2> {
            return Array(length) { idVec2() }
        }
    }
}

//===============================================================
//
//	idVec3 - 3D vector
//
//===============================================================
open class idVec3 : idVec<idVec3>, idSerializable {
    var x = 0.0f
    var y = 0.0f
    var z = 0.0f

    constructor()
    constructor(x: Float, y: Float, z: Float) {
        this.x = x
        this.y = y
        this.z = z
    }

    constructor(x: Int, y: Int, z: Int) {
        this.x = x.toFloat()
        this.y = y.toFloat()
        this.z = z.toFloat()
    }

    constructor(v: idVec3) {
        x = v.x
        y = v.y
        z = v.z
    }

    constructor(xyz: FloatArray, offset: Int = 0) {
        x = xyz[offset + 0]
        y = xyz[offset + 1]
        z = xyz[offset + 2]
    }

    fun set(x: Float, y: Float, z: Float) {
        assert(!x.isNaN())
        this.x = x
        this.y = y
        this.z = z
    }

    override fun Zero() {
        z = 0.0f
        y = z
        x = y
    }

    operator fun unaryMinus(): idVec3 {
        return idVec3(-x, -y, -z)
    }

    override fun set(a: idVec3): idVec3 { //assert(!a.x.isNaN())
        x = a.x
        y = a.y
        z = a.z
        return this
    }

    fun set(a: idVec2): idVec3 {
        x = a.x
        y = a.y
        return this
    }

    override fun times(a: idVec3): Float { // I have no idea why this should return "float" instead of idVec3
        return a.x * x + a.y * y + a.z * z
    }

    override fun times(a: Int): idVec3 {
        return idVec3(x * a, y * a, z * a)
    }

    fun timesVec(a: idVec3): idVec3 {
        return idVec3(x * a.x, y * a.y, z * a.z)
    }

    override fun times(a: Float): idVec3 {
        return idVec3(x * a, y * a, z * a)
    }

    operator fun times(a: idMat3): idVec3 {
        return idVec3(
            a[0][0] * x + a[1][0] * y + a[2][0] * z,
            a[0][1] * x + a[1][1] * y + a[2][1] * z,
            a[0][2] * x + a[1][2] * y + a[2][2] * z
        )
    }

    operator fun times(a: idRotation): idVec3 {
        return a * this
    }

    operator fun times(a: idMat4): idVec3 {
        return a * this
    }

    override fun div(a: Float): idVec3 {
        val inva = 1.0f / a
        return idVec3(x * inva, y * inva, z * inva)
    }

    override fun plus(a: idVec3): idVec3 {
        return idVec3(x + a.x, y + a.y, z + a.z)
    }

    override fun minus(a: idVec3): idVec3 {
        return idVec3(x - a.x, y - a.y, z - a.z)
    }

    override fun div(a: Int): idVec3 {
        return div(a.toFloat())
    }

    override fun plusAssign(a: idVec3): idVec3 {
        x += a.x
        y += a.y
        z += a.z
        return this
    }

    fun minusAssign(a: idVec3): idVec3 {
        x -= a.x
        y -= a.y
        z -= a.z
        return this
    }

    fun minusAssign(a: Float): idVec3 {
        x -= a
        y -= a
        z -= a
        return this
    }

    fun divAssign(a: Float): idVec3 {
        val inva: Float = 1.0f / a
        x *= inva
        y *= inva
        z *= inva
        return this
    }

    fun divAssign(a: idVec3): idVec3 {
        x /= a.x
        y /= a.y
        z /= a.z
        return this
    }

    fun timesAssign(a: Float): idVec3 {
        x *= a
        y *= a
        z *= a
        return this
    }

    fun timesAssign(mat: idMat3): idVec3 {
        this.set(idMat3.timesAssign(this, mat))
        return this
    }

    fun timesAssign(rotation: idRotation): idVec3 {
        this.set(rotation * this)
        return this
    }

    // In-place composition helpers: avoid allocating temporaries in hot paths
    fun setTransform(v: idVec3, m: idMat3): idVec3 {
        x = m[0][0] * v.x + m[1][0] * v.y + m[2][0] * v.z
        y = m[0][1] * v.x + m[1][1] * v.y + m[2][1] * v.z
        z = m[0][2] * v.x + m[1][2] * v.y + m[2][2] * v.z
        return this
    }

    fun setAdd(a: idVec3, b: idVec3): idVec3 {
        x = a.x + b.x
        y = a.y + b.y
        z = a.z + b.z
        return this
    }

    fun setSub(a: idVec3, b: idVec3): idVec3 {
        x = a.x - b.x
        y = a.y - b.y
        z = a.z - b.z
        return this
    }

    fun setScale(a: idVec3, s: Float): idVec3 {
        x = a.x * s
        y = a.y * s
        z = a.z * s
        return this
    }

    fun setLerp(v1: idVec3, v2: idVec3, l: Float): idVec3 {
        x = v1.x + l * (v2.x - v1.x)
        y = v1.y + l * (v2.y - v1.y)
        z = v1.z + l * (v2.z - v1.z)
        return this
    }

    fun Compare(a: idVec3): Boolean { // exact compare, no epsilon
        return x == a.x && y == a.y && z == a.z
    }

    fun Compare(a: idVec3, epsilon: Float): Boolean { // compare with epsilon
        if (abs(x - a.x) > epsilon) {
            return false
        }
        return if (abs(y - a.y) > epsilon) {
            false
        } else abs(z - a.z) <= epsilon
    }

    operator fun plus(a: Float): idVec3 {
        return idVec3(x + a, y + a, z + a)
    }

    fun FixDegenerateNormal(): Boolean { // fix degenerate axial cases
        if (x == 0.0f) {
            if (y == 0.0f) {
                if (z > 0.0f) {
                    if (z != 1.0f) {
                        z = 1.0f
                        return true
                    }
                } else {
                    if (z != -1.0f) {
                        z = -1.0f
                        return true
                    }
                }
                return false
            } else if (z == 0.0f) {
                if (y > 0.0f) {
                    if (y != 1.0f) {
                        y = 1.0f
                        return true
                    }
                } else {
                    if (y != -1.0f) {
                        y = -1.0f
                        return true
                    }
                }
                return false
            }
        } else if (y == 0.0f) {
            if (z == 0.0f) {
                if (x > 0.0f) {
                    if (x != 1.0f) {
                        x = 1.0f
                        return true
                    }
                } else {
                    if (x != -1.0f) {
                        x = -1.0f
                        return true
                    }
                }
                return false
            }
        }
        if (abs(x) == 1.0f) {
            if (y != 0.0f || z != 0.0f) {
                z = 0.0f
                y = z
                return true
            }
            return false
        } else if (abs(y) == 1.0f) {
            if (x != 0.0f || z != 0.0f) {
                z = 0.0f
                x = z
                return true
            }
            return false
        } else if (abs(z) == 1.0f) {
            if (x != 0.0f || y != 0.0f) {
                y = 0.0f
                x = y
                return true
            }
            return false
        }
        return false
    }

    fun FixDenormals(): Boolean { // change tiny numbers to zero
        var denormal = false
        if (abs(x) < 1e-30f) {
            x = 0.0f
            denormal = true
        }
        if (abs(y) < 1e-30f) {
            y = 0.0f
            denormal = true
        }
        if (abs(z) < 1e-30f) {
            z = 0.0f
            denormal = true
        }
        return denormal
    }

    fun Cross(a: idVec3): idVec3 {
        return idVec3(y * a.z - z * a.y, z * a.x - x * a.z, x * a.y - y * a.x)
    }

    fun Cross(a: idVec3, b: idVec3): idVec3 {
        x = a.y * b.z - a.z * b.y
        y = a.z * b.x - a.x * b.z
        z = a.x * b.y - a.y * b.x
        return this
    }

    fun Length(): Float {
        return idMath.Sqrt(x * x + y * y + z * z)
    }

    fun LengthSqr(): Float {
        return x * x + y * y + z * z
    }

    fun LengthFast(): Float {
        val sqrLength: Float = x * x + y * y + z * z
        return sqrLength * idMath.RSqrt(sqrLength)
    }

    fun Normalize(): Float { // returns length
        val sqrLength: Float = x * x + y * y + z * z
        val invLength: Float = idMath.InvSqrt(sqrLength)
        x *= invLength
        y *= invLength
        z *= invLength
        return invLength * sqrLength
    }

    fun NormalizeFast(): Float { // returns length
        val sqrLength: Float = x * x + y * y + z * z
        val invLength: Float = idMath.RSqrt(sqrLength)
        x *= invLength
        y *= invLength
        z *= invLength
        return invLength * sqrLength
    }

    fun Truncate(length: Float): idVec3 { // cap length
        val length2: Float
        val ilength: Float
        if (length == 0.0f) {
            Zero()
        } else {
            length2 = LengthSqr()
            if (length2 > length * length) {
                ilength = length * idMath.InvSqrt(length2)
                x *= ilength
                y *= ilength
                z *= ilength
            }
        }
        return this
    }

    fun Clamp(min: idVec3, max: idVec3) {
        if (x < min.x) {
            x = min.x
        } else if (x > max.x) {
            x = max.x
        }
        if (y < min.y) {
            y = min.y
        } else if (y > max.y) {
            y = max.y
        }
        if (z < min.z) {
            z = min.z
        } else if (z > max.z) {
            z = max.z
        }
    }

    fun Snap() { // snap to closest integer value
        x = floor(x + 0.5f)
        y = floor(y + 0.5f)
        z = floor(z + 0.5f)
    }

    fun SnapInt() { // snap towards integer (floor)
        x = x.toInt().toFloat()
        y = y.toInt().toFloat()
        z = z.toInt().toFloat()
    }

    override fun GetDimension(): Int {
        return 3
    }

    fun ToYaw(): Float {
        var yaw: Float
        if (y == 0.0f && x == 0.0f) {
            yaw = 0.0f
        } else {
            yaw = RAD2DEG(atan2(y, x))
            if (yaw < 0.0f) {
                yaw += 360.0f
            }
        }
        return yaw
    }

    fun ToPitch(): Float {
        val forward: Float
        var pitch: Float
        if (x == 0.0f && y == 0.0f) {
            pitch = if (z > 0.0f) {
                90.0f
            } else {
                270.0f
            }
        } else {
            forward = idMath.Sqrt(x * x + y * y)
            pitch = RAD2DEG(atan2(z, forward))
            if (pitch < 0.0f) {
                pitch += 360.0f
            }
        }
        return pitch
    }

    fun ToAngles(): idAngles {
        val forward: Float
        var yaw: Float
        var pitch: Float
        if (x == 0.0f && y == 0.0f) {
            yaw = 0.0f
            pitch = if (z > 0.0f) {
                90.0f
            } else {
                270.0f
            }
        } else {
            yaw = RAD2DEG(atan2(y, x))
            if (yaw < 0.0f) {
                yaw += 360.0f
            }
            forward = idMath.Sqrt(x * x + y * y)
            pitch = RAD2DEG(atan2(z, forward))
            if (pitch < 0.0f) {
                pitch += 360.0f
            }
        }
        return idAngles(-pitch, yaw, 0.0f)
    }

    fun ToPolar(): idPolar3 {
        val forward: Float
        var yaw: Float
        var pitch: Float
        if (x == 0.0f && y == 0.0f) {
            yaw = 0.0f
            pitch = if (z > 0.0f) {
                90.0f
            } else {
                270.0f
            }
        } else {
            yaw = RAD2DEG(atan2(y, x))
            if (yaw < 0.0f) {
                yaw += 360.0f
            }
            forward = idMath.Sqrt(x * x + y * y)
            pitch = RAD2DEG(atan2(z, forward))
            if (pitch < 0.0f) {
                pitch += 360.0f
            }
        }
        return idPolar3(idMath.Sqrt(x * x + y * y + z * z), yaw, -pitch)
    }

    fun ToMat3(): idMat3 {
        val mat = idMat3()
        var d: Float

        mat[0] = this
        d = x * x + y * y

        if (d == 0.0f) {
            mat[1][0] = 1.0f
            mat[1][1] = 0.0f
            mat[1][2] = 0.0f
        } else {
            d = idMath.InvSqrt(d)
            mat[1][0] = -y * d
            mat[1][1] = x * d
            mat[1][2] = 0.0f
        }

        mat[2] = Cross(mat[1])
        return mat
    }

    fun ToVec2(): idVec2 {
        return idVec2(x, y)
    }

    fun ToFloatPtr(): FloatArray {
        return floatArrayOf(x, y, z)
    }


    fun ToString(precision: Int = 2): String {
        return idStr.FloatArrayToString(ToFloatPtr(), GetDimension(), precision)
    }

    override fun toString(): String {
        return "$x $y $z"
    }

    // vector should be normalized
    fun NormalVectors(left: idVec3, down: idVec3) {
        var d: Float
        d = x * x + y * y
        if (d == 0.0f) {
            left.x = 1.0f
            left.y = 0.0f
            left.z = 0.0f
        } else {
            d = idMath.InvSqrt(d)
            left.x = -y * d
            left.y = x * d
            left.z = 0.0f
        }
        down.set(left.Cross(this))
    }

    fun OrthogonalBasis(left: idVec3, up: idVec3) {
        val l: Float
        val s: Float
        if (abs(z) > 0.7f) {
            l = y * y + z * z
            s = idMath.InvSqrt(l)
            up.x = 0.0f
            up.y = z * s
            up.z = -y * s
            left.x = l * s
            left.y = -x * up.z
            left.z = x * up.y
        } else {
            l = x * x + y * y
            s = idMath.InvSqrt(l)
            left.x = -y * s
            left.y = x * s
            left.z = 0.0f
            up.x = -z * left.y
            up.y = z * left.x
            up.z = l * s
        }
    }

    /*
     =============
     ProjectSelfOntoSphere

     Projects the z component onto a sphere.
     =============
     */

    fun ProjectOntoPlane(normal: idVec3, overBounce: Float = 1.0f) { // x * a.x + y * a.y + z * a.z;
        var backoff: Float = this * normal //	backoff = this.x * normal.x;//TODO:normal.x???
        if (overBounce != 1.0f) {
            if (backoff < 0) {
                backoff *= overBounce
            } else {
                backoff /= overBounce
            }
        }
        this.minusAssign(normal * backoff) //	*this -= backoff * normal;
    }


    fun ProjectAlongPlane(normal: idVec3, epsilon: Float, overBounce: Float = 1.0f): Boolean {
        val cross = idVec3()
        cross.set(this.Cross(normal).Cross(this)) // normalize so a fixed epsilon can be used
        cross.Normalize()
        val len: Float = normal * cross
        if (abs(len) < epsilon) {
            return false
        }
        cross.timesAssign(overBounce * (normal * this) / len) //	cross *= overBounce * ( normal * (*this) ) / len;
        this.minusAssign(cross) //(*this) -= cross;
        return true
    }

    fun ProjectSelfOntoSphere(radius: Float) {
        val rsqr = radius * radius
        val len = Length()
        z = if (len < rsqr * 0.5f) {
            sqrt((rsqr - len))
        } else {
            (rsqr / (2.0f * sqrt(len)))
        }
    }

    /*
     =============
     Lerp

     Linearly inperpolates one vector to another.
     =============
     */
    fun Lerp(v1: idVec3, v2: idVec3, l: Float) {
        if (l <= 0.0f) {
            this.set(v1) //(*this) = v1;
        } else if (l >= 1.0f) {
            this.set(v2) //(*this) = v2;
        } else {
            this.set(v1 + (v2 - v1) * l) //(*this) = v1 + l * ( v2 - v1 );
        }
    }

    fun SLerp(v1: idVec3, v2: idVec3, t: Float) {
        val omega: Float
        val sinom: Float
        val scale0: Float
        val scale1: Float
        if (t <= 0.0f) {
            set(v1)
            return
        } else if (t >= 1.0f) {
            set(v2)
            return
        }
        val cosom: Float = v1 * v2
        if (1.0f - cosom > LERP_DELTA) {
            omega = acos(cosom)
            sinom = sin(omega)
            scale0 = (sin(((1.0f - t) * omega)) / sinom)
            scale1 = (sin((t * omega)) / sinom)
        } else {
            scale0 = 1.0f - t
            scale1 = t
        }

        set((v1 * scale0 + v2 * scale1))
    }

    override fun get(i: Int): Float {
        if (i == 1) {
            return y
        } else if (i == 2) {
            return z
        }
        return x
    }

    override fun set(i: Int, value: Float): Float { //`assert(!value.isNaN())
        when (i) {
            0 -> x = value
            1 -> y = value
            2 -> z = value
        }
        return value
    }

    fun plusAssign(i: Int, value: Float) {
        if (i == 1) {
            y += value
        } else if (i == 2) {
            z += value
        } else {
            x += value
        }
    }

    fun minusAssign(i: Int, value: Float) {
        if (i == 1) {
            y -= value
        } else if (i == 2) {
            z -= value
        } else {
            x -= value
        }
    }

    fun timesAssign(i: Int, value: Float) {
        if (i == 1) {
            y *= value
        } else if (i == 2) {
            z *= value
        } else {
            x *= value
        }
    }

    override fun readFrom(file: idFile) {
        x = file.ReadFloat()
        y = file.ReadFloat()
        z = file.ReadFloat()
    }

    override fun writeTo(file: idFile) {
        file.WriteFloat(x)
        file.WriteFloat(y)
        file.WriteFloat(z)
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) return true
        if (o !is idVec3) return false
        val other = o
        return Compare(other)
    }

    override fun hashCode(): Int {
        var result = if (x != +0.0f) x.toBits().toInt() else 0
        result = 31 * result + if (y != +0.0f) y.toBits().toInt() else 0
        result = 31 * result + if (z != +0.0f) z.toBits().toInt() else 0
        return result
    }

    fun ToVec2_oPluSet(v: idVec2) {
        x += v.x
        y += v.y
    }

    fun ToVec2_oMinSet(v: idVec2) {
        x -= v.x
        y -= v.y
    }

    fun ToVec2_oMulSet(a: Float) {
        x *= a
        y *= a
    }

    fun ToVec2_Normalize() {
        val v = ToVec2()
        v.Normalize()
        this.set(v)
    }

    fun ToVec2_NormalizeFast() {
        val v = ToVec2()
        v.NormalizeFast()
        this.set(v)
    }

    companion object {
        const val SIZE = 3 * java.lang.Float.SIZE

        const val BYTES = SIZE / 8
        private const val LERP_DELTA = 1e-6

        fun times(a: Float, b: idVec3): idVec3 {
            return idVec3(b.x * a, b.y * a, b.z * a)
        }

        fun generateArray(length: Int): Array<idVec3> {
            return Array(length) { idVec3() }
        }


        fun generateArray(firstDimensionSize: Int, secondDimensionSize: Int): Array<Array<idVec3>> {
            return Array(firstDimensionSize) { Array(secondDimensionSize) { idVec3() } }
        }

        fun copyVec(arr: Array<idVec3>): Array<idVec3> {
            val out = generateArray(arr.size)
            for (i in out.indices) {
                out[i].set(arr[i])
            }
            return out
        }

        fun toByteBuffer(vecs: Array<idVec3>): ByteBuffer {
            val data = BufferUtils.createByteBuffer(BYTES * vecs.size)
            for (vec in vecs) {
                data.putFloat(vec.x).putFloat(vec.y).putFloat(vec.z)
            }
            return data.flip()
        }
    }
}

//===============================================================
//
//	idVec4 - 4D vector
//
//===============================================================
class idVec4 : idVec<idVec4>, idSerializable {
    var x = 0.0f
    var y = 0.0f
    var z = 0.0f
    var w = 0.0f

    constructor()
    constructor(v: idVec4) {
        x = v.x
        y = v.y
        z = v.z
        w = v.w
    }

    constructor(x: Float, y: Float, z: Float, w: Float) {
        this.x = x
        this.y = y
        this.z = z
        this.w = w
    }

    constructor(x: Int, y: Int, z: Int, w: Int) {
        this.x = x.toFloat()
        this.y = y.toFloat()
        this.z = z.toFloat()
        this.w = w.toFloat()
    }

    fun set(x: Float, y: Float, z: Float, w: Float) {
        this.x = x
        this.y = y
        this.z = z
        this.w = w
    }

    override fun Zero() {
        w = 0.0f
        z = w
        y = z
        x = y
    }

    override fun times(a: idVec4): Float {
        return x * a.x + y * a.y + z * a.z + w * a.w
    }

    //public	idVec4			operator/( final  Float a ) final ;
    override fun times(a: Float): idVec4 {
        return idVec4(x * a, y * a, z * a, w * a)
    }

    override fun times(a: Int): idVec4 {
        return idVec4(x * a, y * a, z * a, w * a)
    }

    override fun plus(a: idVec4): idVec4 {
        return idVec4(x + a.x, y + a.y, z + a.z, w + a.w)
    }

    override fun minus(a: idVec4): idVec4 {
        return idVec4(x - a.x, y - a.y, z - a.z, w - a.w)
    }

    override fun div(a: Int): idVec4 {
        return div(a.toFloat())
    }

    operator fun unaryMinus(): idVec4 {
        return idVec4(-x, -y, -z, -w)
    }

    fun minusAssign(i: Int, value: Float) {
        when (i) {
            1 -> y -= value
            2 -> z -= value
            3 -> w -= value
            else -> x -= value
        }
    }

    fun timesAssign(i: Int, value: Float) {
        when (i) {
            1 -> y *= value
            2 -> z *= value
            3 -> w *= value
            else -> x *= value
        }
    }

    override fun plusAssign(a: idVec4): idVec4 {
        x += a.x
        y += a.y
        z += a.z
        w += a.w
        return this
    }

    fun Compare(a: idVec4): Boolean { // exact compare, no epsilon
        return x == a.x && y == a.y && z == a.z && w == a.w
    }

    fun Compare(a: idVec4, epsilon: Float): Boolean { // compare with epsilon
        if (abs(x - a.x) > epsilon) {
            return false
        }
        if (abs(y - a.y) > epsilon) {
            return false
        }
        return if (abs(z - a.z) > epsilon) {
            false
        } else abs(w - a.w) <= epsilon
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) return true
        if (o !is idVec4) return false
        val other = o
        return Compare(other)
    }

    override fun hashCode(): Int {
        var result = if (x != +0.0f) x.toBits().toInt() else 0
        result = 31 * result + if (y != +0.0f) y.toBits().toInt() else 0
        result = 31 * result + if (z != +0.0f) z.toBits().toInt() else 0
        result = 31 * result + if (w != +0.0f) w.toBits().toInt() else 0
        return result
    }

    fun Length(): Float {
        return idMath.Sqrt(x * x + y * y + z * z + w * w)
    }

    fun LengthSqr(): Float {
        return x * x + y * y + z * z + w * w
    }

    fun Normalize(): Float { // returns length
        val sqrLength: Float = x * x + y * y + z * z + w * w
        val invLength: Float = idMath.InvSqrt(sqrLength)
        x *= invLength
        y *= invLength
        z *= invLength
        w *= invLength
        return invLength * sqrLength
    }

    fun NormalizeFast(): Float { // returns length
        val sqrLength: Float = x * x + y * y + z * z + w * w
        val invLength: Float = idMath.RSqrt(sqrLength)
        x *= invLength
        y *= invLength
        z *= invLength
        w *= invLength
        return invLength * sqrLength
    }

    override fun GetDimension(): Int {
        return 4
    }

    @Deprecated("")
    fun ToVec2(): idVec2 {
        return idVec2(x, y)
    }

    @Deprecated("")
    fun ToVec3(): idVec3 {
        return idVec3(x, y, z)
    }

    fun ToFloatPtr(): FloatArray {
        return floatArrayOf(x, y, z, w)
    }


    fun ToString(precision: Int = 2): String {
        return idStr.FloatArrayToString(ToFloatPtr(), GetDimension(), precision)
    }

    override fun toString(): String {
        return "$x $y $z $w"
    }

    /*
     =============
     Lerp

     Linearly inperpolates one vector to another.
     =============
     */
    fun Lerp(v1: idVec4, v2: idVec4, l: Float) {
        if (l <= 0.0f) {
            this.set(v1)
        } else if (l >= 1.0f) {
            this.set(v2)
        } else {
            this.set(v1 + (v2 - v1) * l)
        }
    }

    override fun set(a: idVec4): idVec4 {
        x = a.x
        y = a.y
        z = a.z
        w = a.w
        return this
    }

    fun set(a: idVec3): idVec4 {
        x = a.x
        y = a.y
        z = a.z
        return this
    }

    override fun get(i: Int): Float {
        return when (i) {
            1 -> y
            2 -> z
            3 -> w
            else -> x
        }
    }

    override fun set(i: Int, value: Float): Float {
        when (i) {
            0 -> x = value
            1 -> y = value
            2 -> z = value
            3 -> w = value
        }

        return value
    }

    fun plusAssign(i: Int, value: Float): Float {
        return when (i) {
            1 -> value.let { y += it; y }
            2 -> value.let { z += it; z }
            3 -> value.let { w += it; w }
            else -> value.let { x += it; x }
        }
    }

    override fun readFrom(file: idFile) {
        x = file.ReadFloat()
        y = file.ReadFloat()
        z = file.ReadFloat()
        w = file.ReadFloat()
    }

    override fun writeTo(file: idFile) {
        file.WriteFloat(x)
        file.WriteFloat(y)
        file.WriteFloat(z)
        file.WriteFloat(w)
    }

    override fun div(a: Float): idVec4 {
        val inva: Float = 1.0f / a
        return idVec4(x * inva, y * inva, z * inva, w * inva)
    }

    fun divAssign(a: idVec4): idVec4 {
        x /= a.x
        y /= a.y
        z /= a.z
        w /= a.w
        return this
    }

    companion object {
        val SIZE = 4 * java.lang.Float.SIZE

        val BYTES = SIZE / 8
        private var DBG_counter = 0

        fun generateArray(length: Int): Array<idVec4> {
            return Array(length) { idVec4() }
        }

        fun toByteBuffer(vecs: Array<idVec4>): ByteBuffer {
            val data = BufferUtils.createByteBuffer(BYTES * vecs.size)
            for (vec in vecs) {
                data.putFloat(vec.x).putFloat(vec.y).putFloat(vec.z).putFloat(vec.w)
            }
            return data.flip()
        }
    }
}

//===============================================================
//
//	idVec5 - 5D vector
//
//===============================================================
class idVec5 : idVec<idVec5>, idSerializable {
    var x = 0.0f
    var y = 0.0f
    var z = 0.0f
    var s = 0.0f
    var t = 0.0f

    constructor()
    constructor(xyz: idVec3, st: idVec2) {
        x = xyz.x
        y = xyz.y
        z = xyz.z
        s = st[0]
        t = st[1]
    }

    constructor(x: Float, y: Float, z: Float, s: Float, t: Float) {
        this.x = x
        this.y = y
        this.z = z
        this.s = s
        this.t = t
    }

    constructor(a: idVec3) {
        x = a.x
        y = a.y
        z = a.z
    }

    constructor(a: idVec5) {
        x = a.x
        y = a.y
        z = a.z
        s = a.s
        t = a.t
    }

    override fun get(i: Int): Float {
        return when (i) {
            1 -> y
            2 -> z
            3 -> s
            4 -> t
            else -> x
        }
    }

    override fun set(i: Int, value: Float): Float {
        when (i) {
            0 -> x = value
            1 -> y = value
            2 -> z = value
            3 -> s = value
            4 -> t = value
        }
        return value
    }

    override fun set(a: idVec5): idVec5 {
        x = a.x
        y = a.y
        z = a.z
        s = a.s
        t = a.t
        return this
    }

    fun set(a: idVec3): idVec5 {
        x = a.x
        y = a.y
        z = a.z
        s = 0.0f
        t = 0.0f
        return this
    }

    override fun GetDimension(): Int {
        return 5
    }

    fun ToVec3(): idVec3 {
        return idVec3(x, y, z)
    }

    fun ToFloatPtr(): FloatArray {
        return floatArrayOf(x, y, z, s, t)
    }

    fun ToString(precision: Int = 2): String {
        return idStr.FloatArrayToString(ToFloatPtr(), GetDimension(), precision)
    }

    fun Lerp(v1: idVec5, v2: idVec5, l: Float) {
        if (l <= 0.0f) {
            this.set(v1) //(*this) = v1;
        } else if (l >= 1.0f) {
            this.set(v2) //(*this) = v2;
        } else {
            x = v1.x + l * (v2.x - v1.x)
            y = v1.y + l * (v2.y - v1.y)
            z = v1.z + l * (v2.z - v1.z)
            s = v1.s + l * (v2.s - v1.s)
            t = v1.t + l * (v2.t - v1.t)
        }
    }

    override fun readFrom(file: idFile) {
        x = file.ReadFloat()
        y = file.ReadFloat()
        z = file.ReadFloat()
        s = file.ReadFloat()
        t = file.ReadFloat()
    }

    override fun writeTo(file: idFile) {
        file.WriteFloat(x)
        file.WriteFloat(y)
        file.WriteFloat(z)
        file.WriteFloat(s)
        file.WriteFloat(t)
    }

    override fun plus(a: idVec5): idVec5 {
        return idVec5(x + a.x, y + a.y, z + a.z, s + a.s, t + a.t)
    }

    override fun minus(a: idVec5): idVec5 {
        return idVec5(x - a.x, y - a.y, z - a.z, s - a.s, t - a.t)
    }

    override fun div(a: Int): idVec5 {
        return div(a.toFloat())
    }

    override fun times(a: idVec5): Float {
        return x * a.x + y * a.y + z * a.z + s * a.s + t * a.t
    }

    override fun div(a: Float): idVec5 {
        val inva = 1.0f / a
        return idVec5(x * inva, y * inva, z * inva, s * inva, t * inva)
    }

    override fun times(a: Int): idVec5 {
        return idVec5(x * a, y * a, z * a, s * a, t * a)
    }

    fun ToVec3_oMulSet(axis: idMat3) {
        this.set(ToVec3().timesAssign(axis))
    }

    fun ToVec3_oPluSet(origin: idVec3) {
        this.set(ToVec3().plusAssign(origin))
    }


    companion object {
        val SIZE = 5 * java.lang.Float.SIZE

        val BYTES = SIZE / 8
        fun generateArray(length: Int): Array<idVec5> {
            return Array(length) { idVec5() }
        }
    }

    override fun times(a: Float): idVec5 {
        return idVec5(x * a, y * a, z * a, s * a, t * a)
    }

    override fun plusAssign(a: idVec5): idVec5 {
        x += a.x
        y += a.y
        z += a.z
        s += a.s
        t += a.t
        return this
    }

    override fun Zero() {
        t = 0.0f
        s = t
        z = s
        y = z
        x = y
    }
}

//===============================================================
//
//	idVec6 - 6D vector
//
//===============================================================
class idVec6 : idVec<idVec6>, idSerializable {
    var p: FloatArray = FloatArray(6)

    constructor()

    constructor(a: FloatArray) { //	memcpy( p, a, 6 * sizeof( Float ) );
        System.arraycopy(a, 0, p, 0, 6)
    }

    constructor(v: idVec6) {
        System.arraycopy(v.p, 0, p, 0, 6)
    }

    constructor(a1: Float, a2: Float, a3: Float, a4: Float, a5: Float, a6: Float) {
        p[0] = a1
        p[1] = a2
        p[2] = a3
        p[3] = a4
        p[4] = a5
        p[5] = a6
    }

    fun set(a1: Float, a2: Float, a3: Float, a4: Float, a5: Float, a6: Float) {
        p[0] = a1
        p[1] = a2
        p[2] = a3
        p[3] = a4
        p[4] = a5
        p[5] = a6
    }

    override fun Zero() {
        p[5] = 0.0f
        p[4] = p[5]
        p[3] = p[4]
        p[2] = p[3]
        p[1] = p[2]
        p[0] = p[1]
    }

    //public 	Float			operator[]( final  int index ) final ;
    //public 	Float &			operator[]( final  int index );
    operator fun unaryMinus(): idVec6 {
        return idVec6(-p[0], -p[1], -p[2], -p[3], -p[4], -p[5])
    }

    override fun times(a: Float): idVec6 {
        return idVec6(p[0] * a, p[1] * a, p[2] * a, p[3] * a, p[4] * a, p[5] * a)
    }

    //public 	idVec6			operator/( final  Float a ) final ;
    override fun times(a: idVec6): Float {
        return p[0] * a.p[0] + p[1] * a.p[1] + p[2] * a.p[2] + p[3] * a.p[3] + p[4] * a.p[4] + p[5] * a.p[5]
    }

    override fun times(a: Int): idVec6 {
        return idVec6(p[0] * a, p[1] * a, p[2] * a, p[3] * a, p[4] * a, p[5] * a)
    }

    //public 	idVec6			operator-( final  idVec6 &a ) final ;
    override fun plus(a: idVec6): idVec6 {
        return idVec6(
            p[0] + a.p[0], p[1] + a.p[1], p[2] + a.p[2], p[3] + a.p[3], p[4] + a.p[4], p[5] + a.p[5]
        )
    }

    //public 	idVec6 &		operator*=( final  Float a );
    //public 	idVec6 &		operator/=( final  Float a );
    override fun plusAssign(a: idVec6): idVec6 {
        p[0] += a.p[0]
        p[1] += a.p[1]
        p[2] += a.p[2]
        p[3] += a.p[3]
        p[4] += a.p[4]
        p[5] += a.p[5]
        return this
    }

    //public 	idVec6 &		operator-=( final  idVec6 &a );
    //
    //public 	friend idVec6	operator*( final  Float a, final  idVec6 b );
    fun Compare(a: idVec6): Boolean { // exact compare, no epsilon
        return (p[0] == a.p[0] && p[1] == a.p[1] && p[2] == a.p[2] && p[3] == a.p[3] && p[4] == a.p[4] && p[5] == a.p[5])
    }

    fun Compare(a: idVec6, epsilon: Float): Boolean { // compare with epsilon
        if (abs(p[0] - a.p[0]) > epsilon) {
            return false
        }
        if (abs(p[1] - a.p[1]) > epsilon) {
            return false
        }
        if (abs(p[2] - a.p[2]) > epsilon) {
            return false
        }
        if (abs(p[3] - a.p[3]) > epsilon) {
            return false
        }
        return if (abs(p[4] - a.p[4]) > epsilon) {
            false
        } else abs(p[5] - a.p[5]) <= epsilon
    }

    //public 	bool			operator==(	final  idVec6 &a ) final ;						// exact compare, no epsilon
    //public 	bool			operator!=(	final  idVec6 &a ) final ;						// exact compare, no epsilon
    fun Length(): Float {
        return idMath.Sqrt(
            p[0] * p[0] + p[1] * p[1] + p[2] * p[2] + p[3] * p[3] + p[4] * p[4] + p[5] * p[5]
        )
    }

    fun LengthSqr(): Float {
        return p[0] * p[0] + p[1] * p[1] + p[2] * p[2] + p[3] * p[3] + p[4] * p[4] + p[5] * p[5]
    }

    fun Normalize(): Float { // returns length
        val sqrLength: Float = p[0] * p[0] + p[1] * p[1] + p[2] * p[2] + p[3] * p[3] + p[4] * p[4] + p[5] * p[5]
        val invLength: Float = idMath.InvSqrt(sqrLength)
        p[0] *= invLength
        p[1] *= invLength
        p[2] *= invLength
        p[3] *= invLength
        p[4] *= invLength
        p[5] *= invLength
        return invLength * sqrLength
    }

    fun NormalizeFast(): Float { // returns length
        val sqrLength: Float = p[0] * p[0] + p[1] * p[1] + p[2] * p[2] + p[3] * p[3] + p[4] * p[4] + p[5] * p[5]
        val invLength: Float = idMath.RSqrt(sqrLength)
        p[0] *= invLength
        p[1] *= invLength
        p[2] *= invLength
        p[3] *= invLength
        p[4] *= invLength
        p[5] *= invLength
        return invLength * sqrLength
    }

    override fun GetDimension(): Int {
        return 6
    }

    fun SubVec3(index: Int): idVec3 { //	return *reinterpret_cast<const idVec3 *>(p + index * 3);
        val offset = index * 3
        return idVec3(p[offset + 0], p[offset + 1], p[offset + 2])
    }

    //public 	idVec3 &		SubVec3( int index );
    fun ToFloatPtr(): FloatArray {
        return p
    }

    //public 	Float *			ToFloatPtr( void );

    fun ToString(precision: Int = 2): String {
        return idStr.FloatArrayToString(ToFloatPtr(), GetDimension(), precision)
    }

    override fun set(a: idVec6): idVec6 {
        p[0] = a.p[0]
        p[1] = a.p[1]
        p[2] = a.p[2]
        p[3] = a.p[3]
        p[4] = a.p[4]
        p[5] = a.p[5]
        return this
    }

    override fun get(index: Int): Float {
        return p[index]
    }

    override fun set(index: Int, value: Float): Float {
        p[index] = value
        return value
    }

    //
    //        public void setP(final int index, final Float value) {
    //            p[index] = value;
    //        }
    override fun readFrom(file: idFile) {
        for (i in 0 until 6) {
            p[i] = file.ReadFloat()
        }
    }

    override fun writeTo(file: idFile) {
        for (i in 0 until 6) {
            file.WriteFloat(p[i])
        }
    }

    override fun minus(a: idVec6): idVec6 {
        return idVec6(p[0] - a.p[0], p[1] - a.p[1], p[2] - a.p[2], p[3] - a.p[3], p[4] - a.p[4], p[5] - a.p[5])
    }

    override fun div(a: Int): idVec6 {
        return div(a.toFloat())
    }

    override fun div(a: Float): idVec6 {
        val inva: Float

        assert(a != 0.0f)
        inva = 1.0f / a
        return idVec6(p[0] * inva, p[1] * inva, p[2] * inva, p[3] * inva, p[4] * inva, p[5] * inva)
    }

    fun SubVec3_oSet(i: Int, v: idVec3) {
        System.arraycopy(v.ToFloatPtr(), 0, p, i * 3, 3)
    }

    fun SubVec3_oPluSet(i: Int, v: idVec3): idVec3 {
        val off = i * 3
        p[off + 0] += v.x
        p[off + 1] += v.y
        p[off + 2] += v.z
        return idVec3(p, off)
    }

    fun SubVec3_oMinSet(i: Int, v: idVec3): idVec3 {
        return SubVec3_oPluSet(i, v.unaryMinus())
    }

    fun SubVec3_oMulSet(i: Int, v: Float) {
        val off = i * 3
        p[off + 0] *= v
        p[off + 1] *= v
        p[off + 2] *= v
    }

    fun SubVec3_Normalize(i: Int): Float {
        val v = SubVec3(i)
        val normalize = v.Normalize()
        SubVec3_oSet(i, v)
        return normalize
    }

    override fun hashCode(): Int {
        return Arrays.hashCode(p)
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) return true
        if (o == null || javaClass != o.javaClass) return false
        val other = o as idVec6
        return Compare(other)
    }

    override fun toString(): String {
        return "idVec6{" + "p=" + Arrays.toString(p) + '}'
    }

    companion object {
        val SIZE = 6 * java.lang.Float.SIZE

        val BYTES = SIZE / 8
        private var DBG_counter = 0
        private var DBG_idVec6 = 0
    }
}

//===============================================================
//
//	idVecX - arbitrary sized vector
//
//  The vector lives on 16 byte aligned and 16 byte padded memory.
//
//	NOTE: due to the temporary memory pool idVecX cannot be used by multiple threads
//
//===============================================================
class idVecX {
    private var size // size of the vector
            : Int = 0
    var p // memory the vector is stored
            : FloatArray = FloatArray(size)
    var VECX_SIMD = false
    private var alloced // if -1 p points to data set with SetData
            : Int = 1

    constructor()

    constructor(length: Int) {
        alloced = 0
        size = alloced
        SetSize(length)
    }

    constructor(length: Int, data: FloatArray) {
        alloced = 0
        size = alloced
        SetData(length, data)
    }

    fun VECX_CLEAREND() {
        var s = size
        while (s < ((s + 3) and 3.inv()) && s < p.size) {
            p[s++] = 0.0f
        }
    }

    //public					~idVecX( void );
    //public	Float			operator[]( const int index ) const;
    fun get(index: Int): Float {
        return p[index]
    }

    operator fun set(index: Int, value: Float): Float {
        return value.also { p[index] = it }
    }

    operator fun unaryMinus(): idVecX {
        val m = idVecX()
        m.SetTempSize(size)
        var i = 0
        while (i < size) {
            m.p[i] = -p[i]
            i++
        }
        return m
    }

    //public	idVecX &		operator=( const idVecX &a );
    fun set(a: idVecX): idVecX {
        SetSize(a.size)
        System.arraycopy(a.p, 0, p, 0, a.size)
        tempIndex = 0
        return this
    }

    operator fun times(a: Float): idVecX {
        val m = idVecX()
        m.SetTempSize(size)
        if (VECX_SIMD) {
            SIMDProcessor!!.Mul16(m.p, p, a, size)
        } else {
            var i = 0
            while (i < size) {
                m.p[i] = p[i] * a
                i++
            }
        }
        return m
    }

    operator fun times(a: idVecX): Float {
        var sum = 0.0f
        assert(size == a.size)
        var i = 0
        while (i < size) {
            sum += p[i] * a.p[i]
            i++
        }
        return sum
    }

    operator fun minus(a: idVecX): idVecX {
        val m = idVecX()
        assert(size == a.size)
        m.SetTempSize(size)
        for (i in 0 until size) {
            m.p[i] = p[i] - a.p[i]
        }
        return m
    }

    operator fun plus(a: idVecX): idVecX {
        val m = idVecX()
        assert(size == a.size)
        m.SetTempSize(size)
        var i = 0
        while (i < size) {
            m.p[i] = p[i] + a.p[i]
            i++
        } //#endif
        return m
    }

    fun timesAssign(a: Float): idVecX {
        var i = 0
        while (i < size) {
            p[i] *= a
            i++
        }
        return this
    }

    fun Compare(a: idVecX): Boolean { // exact compare, no epsilon
        assert(size == a.size)
        var i = 0
        while (i < size) {
            if (p[i] != a.p[i]) {
                return false
            }
            i++
        }
        return true
    }

    fun Compare(a: idVecX, epsilon: Float): Boolean { // compare with epsilon
        assert(size == a.size)
        var i = 0
        while (i < size) {
            if (abs(p[i] - a.p[i]) > epsilon) {
                return false
            }
            i++
        }
        return true
    }

    fun SetSize(newSize: Int) {
        val alloc = newSize + 3 and 3.inv()
        if (alloc > alloced && alloced != -1) {
            p = FloatArray(alloc)
            alloced = alloc
        }
        size = newSize
        VECX_CLEAREND()
    }


    fun ChangeSize(newSize: Int, makeZero: Boolean = false) {
        val alloc = newSize + 3 and 3.inv()
        if (alloc > alloced && alloced != -1) {
            val oldVec = p //		p = (Float *) Mem_Alloc16( alloc * sizeof( Float ) );
            p = FloatArray(alloc)
            alloced = alloc
            System.arraycopy(oldVec, 0, p, 0, size) //TODO:ifelse
            if (makeZero) { // zero any new elements
                for (i in size until newSize) {
                    p[i] = 0.0f
                }
            }
        }
        size = newSize
        VECX_CLEAREND()
    }

    fun GetSize(): Int {
        return size
    }

    fun SetData(
        length: Int, data: FloatArray
    ) { //	assert( ( ( (int) data ) & 15 ) == 0 ); // data must be 16 byte aligned
        p = data
        size = length
        alloced = -1
        VECX_CLEAREND()
    }

    fun Zero() {
        p.fill(0.0f, 0, size)
    }

    fun Zero(length: Int) {
        SetSize(length)
        p.fill(0.0f, 0, size)
    }

    fun Random(seed: Int, l: Float = 0.0f, u: Float = 1.0f) {
        val rnd = idRandom(seed)
        val c: Float = u - l
        var i = 0
        while (i < size) {
            p[i] = l + rnd.RandomFloat() * c
            i++
        }
    }

    fun Random(length: Int, seed: Int, l: Float = 0.0f, u: Float = 1.0f) {
        val rnd = idRandom(seed)
        SetSize(length)
        val c: Float = u - l
        var i = 0
        while (i < size) {
            if (idMatX.DISABLE_RANDOM_TEST) { //for testing.
                p[i] = i.toFloat()
            } else {
                p[i] = l + rnd.RandomFloat() * c
            }
            i++
        }
    }

    fun Negate() {
        var oGet = 0
        while (oGet < size) {
            p[oGet] = -p[oGet]
            oGet++
        }
    }

    fun Clamp(min: Float, max: Float) {
        var i = 0
        while (i < size) {
            if (p[i] < min) {
                p[i] = min
            } else if (p[i] > max) {
                p[i] = max
            }
            i++
        }
    }

    fun SwapElements(e1: Int, e2: Int): idVecX {
        val tmp: Float = p[e1]
        p[e1] = p[e2]
        p[e2] = tmp
        return this
    }

    fun Length(): Float {
        var sum = 0.0f
        var i = 0
        while (i < size) {
            sum += p[i] * p[i]
            i++
        }
        return idMath.Sqrt(sum)
    }

    fun LengthSqr(): Float {
        var sum = 0.0f
        var i = 0
        while (i < size) {
            sum += p[i] * p[i]
            i++
        }
        return sum
    }

    fun Normalize(): idVecX {
        val m = idVecX()
        val invSqrt: Float
        var sum = 0.0f
        m.SetTempSize(size)
        var i = 0
        while (i < size) {
            sum += p[i] * p[i]
            i++
        }
        invSqrt = idMath.InvSqrt(sum)
        i = 0
        while (i < size) {
            m.p[i] = p[i] * invSqrt
            i++
        }
        return m
    }

    fun NormalizeSelf(): Float {
        val invSqrt: Float
        var sum = 0.0f
        var i = 0
        while (i < size) {
            sum += p[i] * p[i]
            i++
        }
        invSqrt = idMath.InvSqrt(sum)
        i = 0
        while (i < size) {
            p[i] *= invSqrt
            i++
        }
        return invSqrt * sum
    }

    fun GetDimension(): Int {
        return size
    }

    @Deprecated("readonly")
    fun SubVec3(index: Int): idVec3 {
        val offset = index * 3
        assert(index >= 0 && offset + 3 <= size) //	return *reinterpret_cast<idVec3 *>(p + index * 3);
        return idVec3(p[offset + 0], p[offset + 1], p[offset + 2])
    } //public	idVec3 &		SubVec3( int index );

    @Deprecated("readonly")
    fun SubVec6(index: Int): idVec6 {
        val offset = index * 6
        assert(index >= 0 && offset + 6 <= size) //	return *reinterpret_cast<idVec6 *>(p + index * 6);
        return idVec6(
            p[offset + 0], p[offset + 1], p[offset + 2], p[offset + 3], p[offset + 4], p[offset + 5]
        )
    }

    fun ToFloatPtr(): FloatArray {
        return p
    }

    fun ToString(precision: Int = 2): String {
        return idStr.FloatArrayToString(ToFloatPtr(), GetDimension(), precision)
    }

    fun SetTempSize(newSize: Int) {
        size = newSize
        alloced = newSize + 3 and 3.inv()
        assert(alloced < VECX_MAX_TEMP)
        if (tempIndex + alloced > VECX_MAX_TEMP) {
            tempIndex = 0
        }
        if (p.size < alloced) {
            p = FloatArray(alloced)
        } //p = p.copyOf(alloced)
        tempIndex += alloced
        VECX_CLEAREND()
    }

    fun SubVec3_Normalize(i: Int) {
        val vec3 = idVec3(p, i * 3)
        vec3.Normalize()
        SubVec3_oSet(i, vec3)
    }

    fun SubVec3_oSet(i: Int, v: idVec3) {
        p[i * 3 + 0] = v[0]
        p[i * 3 + 1] = v[1]
        p[i * 3 + 2] = v[2]
    }

    fun SubVec6_oSet(i: Int, v: idVec6) {
        p[i * 6 + 0] = v[0]
        p[i * 6 + 1] = v[1]
        p[i * 6 + 2] = v[2]
        p[i * 6 + 3] = v[3]
        p[i * 6 + 4] = v[4]
        p[i * 6 + 5] = v[5]
    }

    fun SubVec6_oPluSet(i: Int, v: idVec6) {
        p[i * 6 + 0] += v[0]
        p[i * 6 + 1] += v[1]
        p[i * 6 + 2] += v[2]
        p[i * 6 + 3] += v[3]
        p[i * 6 + 4] += v[4]
        p[i * 6 + 5] += v[5]
    }

    companion object {
        const val VECX_MAX_TEMP = 1024
        private val temp: FloatArray = FloatArray(VECX_MAX_TEMP + 4) // used to store intermediate results
        private val tempPtr = temp // pointer to 16 byte aligned temporary memory
        private var tempIndex // index into memory pool, wraps around
                = 0

        fun VECX_QUAD(x: Int): Int {
            return x + 3 and 3.inv()
        }

        fun VECX_ALLOCA(n: Int): FloatArray {
            return FloatArray(VECX_QUAD(n))
        }
    }
}

//===============================================================
//
//	idPolar3
//
//===============================================================
class idPolar3 {
    var radius = 0.0f
    var theta = 0.0f
    var phi = 0.0f

    constructor()
    constructor(radius: Float, theta: Float, phi: Float) {
        assert(radius > 0)
        this.radius = radius
        this.theta = theta
        this.phi = phi
    }

    fun set(radius: Float, theta: Float, phi: Float) {
        assert(radius > 0)
        this.radius = radius
        this.theta = theta
        this.phi = phi
    }

    fun ToVec3(): idVec3 {
        val sp = CFloat()
        val cp = CFloat()
        val st = CFloat()
        val ct = CFloat()
        idMath.SinCos(phi, sp, cp)
        idMath.SinCos(theta, st, ct)
        return idVec3(cp._val * radius * ct._val, cp._val * radius * st._val, radius * sp._val)
    }
}

// Global scalar multiplication operators - equivalent to C++ friend operators
operator fun Float.times(vec: idVec2): idVec2 = idVec2(vec.x * this, vec.y * this)
operator fun Float.times(vec: idVec3): idVec3 = idVec3(vec.x * this, vec.y * this, vec.z * this)
operator fun Float.times(vec: idVec4): idVec4 = idVec4(vec.x * this, vec.y * this, vec.z * this, vec.w * this)
operator fun Float.times(vec: idVec6): idVec6 = vec * this
operator fun Float.times(vec: idVecX): idVecX = vec * this

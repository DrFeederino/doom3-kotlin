package neo.idlib.math.Matrix

import neo.idlib.math.idVec3
import neo.idlib.math.idVec5
import kotlin.math.abs

//===============================================================
//
//	idMat5 - 5x5 matrix
//
//===============================================================
class idMat5 {
    private val mat: Array<idVec5> = Array(5) { idVec5() }

    constructor()

    constructor(v0: idVec5, v1: idVec5, v2: idVec5, v3: idVec5, v4: idVec5) {
        mat[0].set(v0)
        mat[1].set(v1)
        mat[2].set(v2)
        mat[3].set(v3)
        mat[4].set(v4)
    }

    constructor(src: Array<FloatArray>) {
        mat[0].set(idVec5(src[0][0], src[0][1], src[0][2], src[0][3], src[0][4]))
        mat[1].set(idVec5(src[1][0], src[1][1], src[1][2], src[1][3], src[1][4]))
        mat[2].set(idVec5(src[2][0], src[2][1], src[2][2], src[2][3], src[2][4]))
        mat[3].set(idVec5(src[3][0], src[3][1], src[3][2], src[3][3], src[3][4]))
        mat[4].set(idVec5(src[4][0], src[4][1], src[4][2], src[4][3], src[4][4]))
    }

    constructor(m: idMat5) {
        set(m)
    }

    operator fun times(a: Float): idMat5 {
        return idMat5(
            idVec5(mat[0].x * a, mat[0].y * a, mat[0].z * a, mat[0].s * a, mat[0].t * a),
            idVec5(mat[1].x * a, mat[1].y * a, mat[1].z * a, mat[1].s * a, mat[1].t * a),
            idVec5(mat[2].x * a, mat[2].y * a, mat[2].z * a, mat[2].s * a, mat[2].t * a),
            idVec5(mat[3].x * a, mat[3].y * a, mat[3].z * a, mat[3].s * a, mat[3].t * a),
            idVec5(mat[4].x * a, mat[4].y * a, mat[4].z * a, mat[4].s * a, mat[4].t * a)
        )
    }

    operator fun times(vec: idVec5): idVec5 {
        return idVec5(
            mat[0].x * vec.x + mat[0].y * vec.y + mat[0].z * vec.z + mat[0].s * vec.s + mat[0].t * vec.t,
            mat[1].x * vec.x + mat[1].y * vec.y + mat[1].z * vec.z + mat[1].s * vec.s + mat[1].t * vec.t,
            mat[2].x * vec.x + mat[2].y * vec.y + mat[2].z * vec.z + mat[2].s * vec.s + mat[2].t * vec.t,
            mat[3].x * vec.x + mat[3].y * vec.y + mat[3].z * vec.z + mat[3].s * vec.s + mat[3].t * vec.t,
            mat[4].x * vec.x + mat[4].y * vec.y + mat[4].z * vec.z + mat[4].s * vec.s + mat[4].t * vec.t
        )
    }

    operator fun times(a: idMat5): idMat5 {
        var j: Int
        val m1Ptr = reinterpret_cast()
        val m2Ptr = a.reinterpret_cast()
        val dst = Array(5) { FloatArray(5) }
        var i = 0
        while (i < 5) {
            j = 0
            while (j < 5) {
                dst[i][j] =
                    m1Ptr[0] * m2Ptr[0 * 5 + j] + m1Ptr[1] * m2Ptr[1 * 5 + j] + m1Ptr[2] * m2Ptr[2 * 5 + j] + m1Ptr[3] * m2Ptr[3 * 5 + j] + m1Ptr[4] * m2Ptr[4 * 5 + j]
                j++
            }
            i++
        }
        return idMat5(dst)
    }

    operator fun plus(a: idMat5): idMat5 {
        return idMat5(
            idVec5(
                mat[0].x + a.mat[0].x,
                mat[0].y + a.mat[0].y,
                mat[0].z + a.mat[0].z,
                mat[0].s + a.mat[0].s,
                mat[0].t + a.mat[0].t
            ), idVec5(
                mat[1].x + a.mat[1].x,
                mat[1].y + a.mat[1].y,
                mat[1].z + a.mat[1].z,
                mat[1].s + a.mat[1].s,
                mat[1].t + a.mat[1].t
            ), idVec5(
                mat[2].x + a.mat[2].x,
                mat[2].y + a.mat[2].y,
                mat[2].z + a.mat[2].z,
                mat[2].s + a.mat[2].s,
                mat[2].t + a.mat[2].t
            ), idVec5(
                mat[3].x + a.mat[3].x,
                mat[3].y + a.mat[3].y,
                mat[3].z + a.mat[3].z,
                mat[3].s + a.mat[3].s,
                mat[3].t + a.mat[3].t
            ), idVec5(
                mat[4].x + a.mat[4].x,
                mat[4].y + a.mat[4].y,
                mat[4].z + a.mat[4].z,
                mat[4].s + a.mat[4].s,
                mat[4].t + a.mat[4].t
            )
        )
    }

    operator fun minus(a: idMat5): idMat5 {
        return idMat5(
            idVec5(
                mat[0].x - a.mat[0].x,
                mat[0].y - a.mat[0].y,
                mat[0].z - a.mat[0].z,
                mat[0].s - a.mat[0].s,
                mat[0].t - a.mat[0].t
            ), idVec5(
                mat[1].x - a.mat[1].x,
                mat[1].y - a.mat[1].y,
                mat[1].z - a.mat[1].z,
                mat[1].s - a.mat[1].s,
                mat[1].t - a.mat[1].t
            ), idVec5(
                mat[2].x - a.mat[2].x,
                mat[2].y - a.mat[2].y,
                mat[2].z - a.mat[2].z,
                mat[2].s - a.mat[2].s,
                mat[2].t - a.mat[2].t
            ), idVec5(
                mat[3].x - a.mat[3].x,
                mat[3].y - a.mat[3].y,
                mat[3].z - a.mat[3].z,
                mat[3].s - a.mat[3].s,
                mat[3].t - a.mat[3].t
            ), idVec5(
                mat[4].x - a.mat[4].x,
                mat[4].y - a.mat[4].y,
                mat[4].z - a.mat[4].z,
                mat[4].s - a.mat[4].s,
                mat[4].t - a.mat[4].t
            )
        )
    }

    fun timesAssign(a: Float): idMat5 {
        mat[0].x *= a
        mat[0].y *= a
        mat[0].z *= a
        mat[0].s *= a
        mat[0].t *= a
        mat[1].x *= a
        mat[1].y *= a
        mat[1].z *= a
        mat[1].s *= a
        mat[1].t *= a
        mat[2].x *= a
        mat[2].y *= a
        mat[2].z *= a
        mat[2].s *= a
        mat[2].t *= a
        mat[3].x *= a
        mat[3].y *= a
        mat[3].z *= a
        mat[3].s *= a
        mat[3].t *= a
        mat[4].x *= a
        mat[4].y *= a
        mat[4].z *= a
        mat[4].s *= a
        mat[4].t *= a
        return this
    }

    fun timesAssign(a: idMat5): idMat5 {
        set(times(a))
        return this
    }

    fun plusAssign(a: idMat5): idMat5 {
        mat[0].x += a.mat[0].x
        mat[0].y += a.mat[0].y
        mat[0].z += a.mat[0].z
        mat[0].s += a.mat[0].s
        mat[0].t += a.mat[0].t //
        mat[1].x += a.mat[1].x
        mat[1].y += a.mat[1].y
        mat[1].z += a.mat[1].z
        mat[1].s += a.mat[1].s
        mat[1].t += a.mat[1].t //
        mat[2].x += a.mat[2].x
        mat[2].y += a.mat[2].y
        mat[2].z += a.mat[2].z
        mat[2].s += a.mat[2].s
        mat[2].t += a.mat[2].t //
        mat[3].x += a.mat[3].x
        mat[3].y += a.mat[3].y
        mat[3].z += a.mat[3].z
        mat[3].s += a.mat[3].s
        mat[3].t += a.mat[3].t //
        mat[4].x += a.mat[4].x
        mat[4].y += a.mat[4].y
        mat[4].z += a.mat[4].z
        mat[4].s += a.mat[4].s
        mat[4].t += a.mat[4].t
        return this
    }

    fun minusAssign(a: idMat5): idMat5 {
        mat[0].x -= a.mat[0].x
        mat[0].y -= a.mat[0].y
        mat[0].z -= a.mat[0].z
        mat[0].s -= a.mat[0].s
        mat[0].t -= a.mat[0].t //
        mat[1].x -= a.mat[1].x
        mat[1].y -= a.mat[1].y
        mat[1].z -= a.mat[1].z
        mat[1].s -= a.mat[1].s
        mat[1].t -= a.mat[1].t //
        mat[2].x -= a.mat[2].x
        mat[2].y -= a.mat[2].y
        mat[2].z -= a.mat[2].z
        mat[2].s -= a.mat[2].s
        mat[2].t -= a.mat[2].t //
        mat[3].x -= a.mat[3].x
        mat[3].y -= a.mat[3].y
        mat[3].z -= a.mat[3].z
        mat[3].s -= a.mat[3].s
        mat[3].t -= a.mat[3].t //
        mat[4].x -= a.mat[4].x
        mat[4].y -= a.mat[4].y
        mat[4].z -= a.mat[4].z
        mat[4].s -= a.mat[4].s
        mat[4].t -= a.mat[4].t
        return this
    }

    fun Compare(a: idMat5): Boolean { // exact compare, no epsilon
        val ptr1: FloatArray = reinterpret_cast()
        val ptr2: FloatArray = a.reinterpret_cast()
        var i = 0
        while (i < 5 * 5) {
            if (ptr1[i] != ptr2[i]) {
                return false
            }
            i++
        }
        return true
    }

    fun Compare(a: idMat5, epsilon: Float): Boolean // compare with epsilon
    {
        val ptr1: FloatArray = reinterpret_cast()
        val ptr2: FloatArray = a.reinterpret_cast()
        var i = 0
        while (i < 5 * 5) {
            if (abs(ptr1[i] - ptr2[i]) > epsilon) {
                return false
            }
            i++
        }
        return true
    }

    override fun hashCode(): Int {
        var hash = 7
        hash = 29 * hash + mat.contentDeepHashCode()
        return hash
    }

    override fun equals(obj: Any?): Boolean {
        if (obj == null) {
            return false
        }
        if (javaClass != obj.javaClass) {
            return false
        }
        val other = obj as idMat5
        return Compare(other)
    }

    fun equals(a: idMat5): Boolean { // exact compare, no epsilon
        return Compare(a)
    }

    fun notEquals(a: idMat5): Boolean { // exact compare, no epsilon
        return !Compare(a)
    }

    fun Zero() {
        set(getMat5_zero())
    }

    fun Identity() {
        set(getMat5_identity())
    }


    fun IsIdentity(epsilon: Float): Boolean {
        return Compare(getMat5_identity(), epsilon)
    }


    fun IsSymmetric(epsilon: Float): Boolean {
        for (i in 1..4) {
            for (j in 0 until i) {
                if (abs(mat[i][j] - mat[j][i]) > epsilon) {
                    return false
                }
            }
        }
        return true
    }


    fun IsDiagonal(epsilon: Float): Boolean {
        for (i in 0..4) {
            for (j in 0..4) {
                if (i != j && abs(mat[i][j]) > epsilon) {
                    return false
                }
            }
        }
        return true
    }

    fun Trace(): Float {
        return mat[0].x + mat[1].y + mat[2].z + mat[3].s + mat[4].t
    }

    fun Determinant(): Float {

        // 2x2 sub-determinants required to calculate 5x5 determinant
        val det2_34_01 = mat[3].x * mat[4].y - mat[3].y * mat[4].x
        val det2_34_02 = mat[3].x * mat[4].z - mat[3].z * mat[4].x
        val det2_34_03 = mat[3].x * mat[4].s - mat[3].s * mat[4].x
        val det2_34_04 = mat[3].x * mat[4].t - mat[3].t * mat[4].x
        val det2_34_12 = mat[3].y * mat[4].z - mat[3].z * mat[4].y
        val det2_34_13 = mat[3].y * mat[4].s - mat[3].s * mat[4].y
        val det2_34_14 = mat[3].y * mat[4].t - mat[3].t * mat[4].y
        val det2_34_23 = mat[3].z * mat[4].s - mat[3].s * mat[4].z
        val det2_34_24 = mat[3].z * mat[4].t - mat[3].t * mat[4].z
        val det2_34_34 = mat[3].s * mat[4].t - mat[3].t * mat[4].s

        // 3x3 sub-determinants required to calculate 5x5 determinant
        val det3_234_012 = mat[2].x * det2_34_12 - mat[2].y * det2_34_02 + mat[2].z * det2_34_01
        val det3_234_013 = mat[2].x * det2_34_13 - mat[2].y * det2_34_03 + mat[2].s * det2_34_01
        val det3_234_014 = mat[2].x * det2_34_14 - mat[2].y * det2_34_04 + mat[2].t * det2_34_01
        val det3_234_023 = mat[2].x * det2_34_23 - mat[2].z * det2_34_03 + mat[2].s * det2_34_02
        val det3_234_024 = mat[2].x * det2_34_24 - mat[2].z * det2_34_04 + mat[2].t * det2_34_02
        val det3_234_034 = mat[2].x * det2_34_34 - mat[2].s * det2_34_04 + mat[2].t * det2_34_03
        val det3_234_123 = mat[2].y * det2_34_23 - mat[2].z * det2_34_13 + mat[2].s * det2_34_12
        val det3_234_124 = mat[2].y * det2_34_24 - mat[2].z * det2_34_14 + mat[2].t * det2_34_12
        val det3_234_134 = mat[2].y * det2_34_34 - mat[2].s * det2_34_14 + mat[2].t * det2_34_13
        val det3_234_234 = mat[2].z * det2_34_34 - mat[2].s * det2_34_24 + mat[2].t * det2_34_23

        // 4x4 sub-determinants required to calculate 5x5 determinant
        val det4_1234_0123 =
            mat[1].x * det3_234_123 - mat[1].y * det3_234_023 + mat[1].z * det3_234_013 - mat[1].s * det3_234_012
        val det4_1234_0124 =
            mat[1].x * det3_234_124 - mat[1].y * det3_234_024 + mat[1].z * det3_234_014 - mat[1].t * det3_234_012
        val det4_1234_0134 =
            mat[1].x * det3_234_134 - mat[1].y * det3_234_034 + mat[1].s * det3_234_014 - mat[1].t * det3_234_013
        val det4_1234_0234 =
            mat[1].x * det3_234_234 - mat[1].z * det3_234_034 + mat[1].s * det3_234_024 - mat[1].t * det3_234_023
        val det4_1234_1234 =
            mat[1].y * det3_234_234 - mat[1].z * det3_234_134 + mat[1].s * det3_234_124 - mat[1].t * det3_234_123

        // determinant of 5x5 matrix
        return mat[0].x * det4_1234_1234 - mat[0].y * det4_1234_0234 + mat[0].z * det4_1234_0134 - mat[0].s * det4_1234_0124 + mat[0].t * det4_1234_0123
    }

    fun Transpose(): idMat5 { // returns transpose
        val transpose = idMat5()
        var j: Int
        var i = 0
        while (i < 5) {
            j = 0
            while (j < 5) {
                transpose.mat[i][j] = mat[j][i]
                j++
            }
            i++
        }
        return transpose
    }

    fun TransposeSelf(): idMat5 {
        var temp: Float
        var j: Int
        var i = 0
        while (i < 5) {
            j = i + 1
            while (j < 5) {
                temp = mat[i][j]
                mat[i][j] = mat[j][i]
                mat[j][i] = temp
                j++
            }
            i++
        }
        return this
    }

    fun Inverse(): idMat5 { // returns the inverse ( m * m.Inverse() = identity )
        val invMat = idMat5(this)
        val r = invMat.InverseSelf()
        assert(r)
        return invMat
    }

    fun InverseSelf(): Boolean { // returns false if determinant is zero
        // 280+5+25 = 310 multiplications
        //				1 division
        val det: Float

        // 2x2 sub-determinants required to calculate 5x5 determinant
        val det2_34_01 = mat[3].x * mat[4].y - mat[3].y * mat[4].x
        val det2_34_02 = mat[3].x * mat[4].z - mat[3].z * mat[4].x
        val det2_34_03 = mat[3].x * mat[4].s - mat[3].s * mat[4].x
        val det2_34_04 = mat[3].x * mat[4].t - mat[3].t * mat[4].x
        val det2_34_12 = mat[3].y * mat[4].z - mat[3].z * mat[4].y
        val det2_34_13 = mat[3].y * mat[4].s - mat[3].s * mat[4].y
        val det2_34_14 = mat[3].y * mat[4].t - mat[3].t * mat[4].y
        val det2_34_23 = mat[3].z * mat[4].s - mat[3].s * mat[4].z
        val det2_34_24 = mat[3].z * mat[4].t - mat[3].t * mat[4].z
        val det2_34_34 = mat[3].s * mat[4].t - mat[3].t * mat[4].s

        // 3x3 sub-determinants required to calculate 5x5 determinant
        val det3_234_012 = mat[2].x * det2_34_12 - mat[2].y * det2_34_02 + mat[2].z * det2_34_01
        val det3_234_013 = mat[2].x * det2_34_13 - mat[2].y * det2_34_03 + mat[2].s * det2_34_01
        val det3_234_014 = mat[2].x * det2_34_14 - mat[2].y * det2_34_04 + mat[2].t * det2_34_01
        val det3_234_023 = mat[2].x * det2_34_23 - mat[2].z * det2_34_03 + mat[2].s * det2_34_02
        val det3_234_024 = mat[2].x * det2_34_24 - mat[2].z * det2_34_04 + mat[2].t * det2_34_02
        val det3_234_034 = mat[2].x * det2_34_34 - mat[2].s * det2_34_04 + mat[2].t * det2_34_03
        val det3_234_123 = mat[2].y * det2_34_23 - mat[2].z * det2_34_13 + mat[2].s * det2_34_12
        val det3_234_124 = mat[2].y * det2_34_24 - mat[2].z * det2_34_14 + mat[2].t * det2_34_12
        val det3_234_134 = mat[2].y * det2_34_34 - mat[2].s * det2_34_14 + mat[2].t * det2_34_13
        val det3_234_234 = mat[2].z * det2_34_34 - mat[2].s * det2_34_24 + mat[2].t * det2_34_23

        // 4x4 sub-determinants required to calculate 5x5 determinant
        val det4_1234_0123 =
            mat[1].x * det3_234_123 - mat[1].y * det3_234_023 + mat[1].z * det3_234_013 - mat[1].s * det3_234_012
        val det4_1234_0124 =
            mat[1].x * det3_234_124 - mat[1].y * det3_234_024 + mat[1].z * det3_234_014 - mat[1].t * det3_234_012
        val det4_1234_0134 =
            mat[1].x * det3_234_134 - mat[1].y * det3_234_034 + mat[1].s * det3_234_014 - mat[1].t * det3_234_013
        val det4_1234_0234 =
            mat[1].x * det3_234_234 - mat[1].z * det3_234_034 + mat[1].s * det3_234_024 - mat[1].t * det3_234_023
        val det4_1234_1234 =
            mat[1].y * det3_234_234 - mat[1].z * det3_234_134 + mat[1].s * det3_234_124 - mat[1].t * det3_234_123

        // determinant of 5x5 matrix
        det =
            (mat[0].x * det4_1234_1234 - mat[0].y * det4_1234_0234 + mat[0].z * det4_1234_0134 - mat[0].s * det4_1234_0124 + mat[0].t * det4_1234_0123)
        if (abs(det) < MATRIX_INVERSE_EPSILON) {
            return false
        }
        val invDet: Float = 1.0f / det

        // remaining 2x2 sub-determinants
        val det2_23_01 = mat[2].x * mat[3].y - mat[2].y * mat[3].x
        val det2_23_02 = mat[2].x * mat[3].z - mat[2].z * mat[3].x
        val det2_23_03 = mat[2].x * mat[3].s - mat[2].s * mat[3].x
        val det2_23_04 = mat[2].x * mat[3].t - mat[2].t * mat[3].x
        val det2_23_12 = mat[2].y * mat[3].z - mat[2].z * mat[3].y
        val det2_23_13 = mat[2].y * mat[3].s - mat[2].s * mat[3].y
        val det2_23_14 = mat[2].y * mat[3].t - mat[2].t * mat[3].y
        val det2_23_23 = mat[2].z * mat[3].s - mat[2].s * mat[3].z
        val det2_23_24 = mat[2].z * mat[3].t - mat[2].t * mat[3].z
        val det2_23_34 = mat[2].s * mat[3].t - mat[2].t * mat[3].s
        val det2_24_01 = mat[2].x * mat[4].y - mat[2].y * mat[4].x
        val det2_24_02 = mat[2].x * mat[4].z - mat[2].z * mat[4].x
        val det2_24_03 = mat[2].x * mat[4].s - mat[2].s * mat[4].x
        val det2_24_04 = mat[2].x * mat[4].t - mat[2].t * mat[4].x
        val det2_24_12 = mat[2].y * mat[4].z - mat[2].z * mat[4].y
        val det2_24_13 = mat[2].y * mat[4].s - mat[2].s * mat[4].y
        val det2_24_14 = mat[2].y * mat[4].t - mat[2].t * mat[4].y
        val det2_24_23 = mat[2].z * mat[4].s - mat[2].s * mat[4].z
        val det2_24_24 = mat[2].z * mat[4].t - mat[2].t * mat[4].z
        val det2_24_34 = mat[2].s * mat[4].t - mat[2].t * mat[4].s

        // remaining 3x3 sub-determinants
        val det3_123_012 = mat[1].x * det2_23_12 - mat[1].y * det2_23_02 + mat[1].z * det2_23_01
        val det3_123_013 = mat[1].x * det2_23_13 - mat[1].y * det2_23_03 + mat[1].s * det2_23_01
        val det3_123_014 = mat[1].x * det2_23_14 - mat[1].y * det2_23_04 + mat[1].t * det2_23_01
        val det3_123_023 = mat[1].x * det2_23_23 - mat[1].z * det2_23_03 + mat[1].s * det2_23_02
        val det3_123_024 = mat[1].x * det2_23_24 - mat[1].z * det2_23_04 + mat[1].t * det2_23_02
        val det3_123_034 = mat[1].x * det2_23_34 - mat[1].s * det2_23_04 + mat[1].t * det2_23_03
        val det3_123_123 = mat[1].y * det2_23_23 - mat[1].z * det2_23_13 + mat[1].s * det2_23_12
        val det3_123_124 = mat[1].y * det2_23_24 - mat[1].z * det2_23_14 + mat[1].t * det2_23_12
        val det3_123_134 = mat[1].y * det2_23_34 - mat[1].s * det2_23_14 + mat[1].t * det2_23_13
        val det3_123_234 = mat[1].z * det2_23_34 - mat[1].s * det2_23_24 + mat[1].t * det2_23_23
        val det3_124_012 = mat[1].x * det2_24_12 - mat[1].y * det2_24_02 + mat[1].z * det2_24_01
        val det3_124_013 = mat[1].x * det2_24_13 - mat[1].y * det2_24_03 + mat[1].s * det2_24_01
        val det3_124_014 = mat[1].x * det2_24_14 - mat[1].y * det2_24_04 + mat[1].t * det2_24_01
        val det3_124_023 = mat[1].x * det2_24_23 - mat[1].z * det2_24_03 + mat[1].s * det2_24_02
        val det3_124_024 = mat[1].x * det2_24_24 - mat[1].z * det2_24_04 + mat[1].t * det2_24_02
        val det3_124_034 = mat[1].x * det2_24_34 - mat[1].s * det2_24_04 + mat[1].t * det2_24_03
        val det3_124_123 = mat[1].y * det2_24_23 - mat[1].z * det2_24_13 + mat[1].s * det2_24_12
        val det3_124_124 = mat[1].y * det2_24_24 - mat[1].z * det2_24_14 + mat[1].t * det2_24_12
        val det3_124_134 = mat[1].y * det2_24_34 - mat[1].s * det2_24_14 + mat[1].t * det2_24_13
        val det3_124_234 = mat[1].z * det2_24_34 - mat[1].s * det2_24_24 + mat[1].t * det2_24_23
        val det3_134_012 = mat[1].x * det2_34_12 - mat[1].y * det2_34_02 + mat[1].z * det2_34_01
        val det3_134_013 = mat[1].x * det2_34_13 - mat[1].y * det2_34_03 + mat[1].s * det2_34_01
        val det3_134_014 = mat[1].x * det2_34_14 - mat[1].y * det2_34_04 + mat[1].t * det2_34_01
        val det3_134_023 = mat[1].x * det2_34_23 - mat[1].z * det2_34_03 + mat[1].s * det2_34_02
        val det3_134_024 = mat[1].x * det2_34_24 - mat[1].z * det2_34_04 + mat[1].t * det2_34_02
        val det3_134_034 = mat[1].x * det2_34_34 - mat[1].s * det2_34_04 + mat[1].t * det2_34_03
        val det3_134_123 = mat[1].y * det2_34_23 - mat[1].z * det2_34_13 + mat[1].s * det2_34_12
        val det3_134_124 = mat[1].y * det2_34_24 - mat[1].z * det2_34_14 + mat[1].t * det2_34_12
        val det3_134_134 = mat[1].y * det2_34_34 - mat[1].s * det2_34_14 + mat[1].t * det2_34_13
        val det3_134_234 = mat[1].z * det2_34_34 - mat[1].s * det2_34_24 + mat[1].t * det2_34_23

        // remaining 4x4 sub-determinants
        val det4_0123_0123 =
            mat[0].x * det3_123_123 - mat[0].y * det3_123_023 + mat[0].z * det3_123_013 - mat[0].s * det3_123_012
        val det4_0123_0124 =
            mat[0].x * det3_123_124 - mat[0].y * det3_123_024 + mat[0].z * det3_123_014 - mat[0].t * det3_123_012
        val det4_0123_0134 =
            mat[0].x * det3_123_134 - mat[0].y * det3_123_034 + mat[0].s * det3_123_014 - mat[0].t * det3_123_013
        val det4_0123_0234 =
            mat[0].x * det3_123_234 - mat[0].z * det3_123_034 + mat[0].s * det3_123_024 - mat[0].t * det3_123_023
        val det4_0123_1234 =
            mat[0].y * det3_123_234 - mat[0].z * det3_123_134 + mat[0].s * det3_123_124 - mat[0].t * det3_123_123
        val det4_0124_0123 =
            mat[0].x * det3_124_123 - mat[0].y * det3_124_023 + mat[0].z * det3_124_013 - mat[0].s * det3_124_012
        val det4_0124_0124 =
            mat[0].x * det3_124_124 - mat[0].y * det3_124_024 + mat[0].z * det3_124_014 - mat[0].t * det3_124_012
        val det4_0124_0134 =
            mat[0].x * det3_124_134 - mat[0].y * det3_124_034 + mat[0].s * det3_124_014 - mat[0].t * det3_124_013
        val det4_0124_0234 =
            mat[0].x * det3_124_234 - mat[0].z * det3_124_034 + mat[0].s * det3_124_024 - mat[0].t * det3_124_023
        val det4_0124_1234 =
            mat[0].y * det3_124_234 - mat[0].z * det3_124_134 + mat[0].s * det3_124_124 - mat[0].t * det3_124_123
        val det4_0134_0123 =
            mat[0].x * det3_134_123 - mat[0].y * det3_134_023 + mat[0].z * det3_134_013 - mat[0].s * det3_134_012
        val det4_0134_0124 =
            mat[0].x * det3_134_124 - mat[0].y * det3_134_024 + mat[0].z * det3_134_014 - mat[0].t * det3_134_012
        val det4_0134_0134 =
            mat[0].x * det3_134_134 - mat[0].y * det3_134_034 + mat[0].s * det3_134_014 - mat[0].t * det3_134_013
        val det4_0134_0234 =
            mat[0].x * det3_134_234 - mat[0].z * det3_134_034 + mat[0].s * det3_134_024 - mat[0].t * det3_134_023
        val det4_0134_1234 =
            mat[0].y * det3_134_234 - mat[0].z * det3_134_134 + mat[0].s * det3_134_124 - mat[0].t * det3_134_123
        val det4_0234_0123 =
            mat[0].x * det3_234_123 - mat[0].y * det3_234_023 + mat[0].z * det3_234_013 - mat[0].s * det3_234_012
        val det4_0234_0124 =
            mat[0].x * det3_234_124 - mat[0].y * det3_234_024 + mat[0].z * det3_234_014 - mat[0].t * det3_234_012
        val det4_0234_0134 =
            mat[0].x * det3_234_134 - mat[0].y * det3_234_034 + mat[0].s * det3_234_014 - mat[0].t * det3_234_013
        val det4_0234_0234 =
            mat[0].x * det3_234_234 - mat[0].z * det3_234_034 + mat[0].s * det3_234_024 - mat[0].t * det3_234_023
        val det4_0234_1234 =
            mat[0].y * det3_234_234 - mat[0].z * det3_234_134 + mat[0].s * det3_234_124 - mat[0].t * det3_234_123
        mat[0].x = (det4_1234_1234 * invDet)
        mat[0].y = (-det4_0234_1234 * invDet)
        mat[0].z = (det4_0134_1234 * invDet)
        mat[0].s = (-det4_0124_1234 * invDet)
        mat[0].t = (det4_0123_1234 * invDet)
        mat[1].x = (-det4_1234_0234 * invDet)
        mat[1].y = (det4_0234_0234 * invDet)
        mat[1].z = (-det4_0134_0234 * invDet)
        mat[1].s = (det4_0124_0234 * invDet)
        mat[1].t = (-det4_0123_0234 * invDet)
        mat[2].x = (det4_1234_0134 * invDet)
        mat[2].y = (-det4_0234_0134 * invDet)
        mat[2].z = (det4_0134_0134 * invDet)
        mat[2].s = (-det4_0124_0134 * invDet)
        mat[2].t = (det4_0123_0134 * invDet)
        mat[3].x = (-det4_1234_0124 * invDet)
        mat[3].y = (det4_0234_0124 * invDet)
        mat[3].z = (-det4_0134_0124 * invDet)
        mat[3].s = (det4_0124_0124 * invDet)
        mat[3].t = (-det4_0123_0124 * invDet)
        mat[4].x = (det4_1234_0123 * invDet)
        mat[4].y = (-det4_0234_0123 * invDet)
        mat[4].z = (det4_0134_0123 * invDet)
        mat[4].s = (-det4_0124_0123 * invDet)
        mat[4].t = (det4_0123_0123 * invDet)
        return true
    }

    fun InverseFast(): idMat5 { // returns the inverse ( m * m.Inverse() = identity )
        val invMat = idMat5(this)
        val r = invMat.InverseFastSelf()
        assert(r)
        return invMat
    }

    fun InverseFastSelf(): Boolean { // returns false if determinant is zero
        val r0: Array<idVec3> = idVec3.generateArray(3)
        val r1: Array<idVec3> = idVec3.generateArray(3)
        val r2: Array<idVec3> = idVec3.generateArray(3)
        val r3: Array<idVec3> = idVec3.generateArray(3)
        var c0: Float
        val c1: Float
        val c2: Float
        var det: Float
        val matt = reinterpret_cast()

        // r0 = m0.Inverse();	// 3x3
        c0 = matt[1 * 5 + 1] * matt[2 * 5 + 2] - matt[1 * 5 + 2] * matt[2 * 5 + 1]
        c1 = matt[1 * 5 + 2] * matt[2 * 5 + 0] - matt[1 * 5 + 0] * matt[2 * 5 + 2]
        c2 = matt[1 * 5 + 0] * matt[2 * 5 + 1] - matt[1 * 5 + 1] * matt[2 * 5 + 0]
        det = matt[0 * 5 + 0] * c0 + matt[0 * 5 + 1] * c1 + matt[0 * 5 + 2] * c2
        if (abs(det) < MATRIX_INVERSE_EPSILON) {
            return false
        }
        var invDet: Float = 1.0f / det
        r0[0].x = c0 * invDet
        r0[0].y = (matt[0 * 5 + 2] * matt[2 * 5 + 1] - matt[0 * 5 + 1] * matt[2 * 5 + 2]) * invDet
        r0[0].z = (matt[0 * 5 + 1] * matt[1 * 5 + 2] - matt[0 * 5 + 2] * matt[1 * 5 + 1]) * invDet
        r0[1].x = c1 * invDet
        r0[1].y = (matt[0 * 5 + 0] * matt[2 * 5 + 2] - matt[0 * 5 + 2] * matt[2 * 5 + 0]) * invDet
        r0[1].z = (matt[0 * 5 + 2] * matt[1 * 5 + 0] - matt[0 * 5 + 0] * matt[1 * 5 + 2]) * invDet
        r0[2].x = c2 * invDet
        r0[2].y = (matt[0 * 5 + 1] * matt[2 * 5 + 0] - matt[0 * 5 + 0] * matt[2 * 5 + 1]) * invDet
        r0[2].z = (matt[0 * 5 + 0] * matt[1 * 5 + 1] - matt[0 * 5 + 1] * matt[1 * 5 + 0]) * invDet

        // r1 = r0 * m1;		// 3x2 = 3x3 * 3x2
        r1[0].x = r0[0].x * matt[0 * 5 + 3] + r0[0].y * matt[1 * 5 + 3] + r0[0].z * matt[2 * 5 + 3]
        r1[0].y = r0[0].x * matt[0 * 5 + 4] + r0[0].y * matt[1 * 5 + 4] + r0[0].z * matt[2 * 5 + 4]
        r1[1].x = r0[1].x * matt[0 * 5 + 3] + r0[1].y * matt[1 * 5 + 3] + r0[1].z * matt[2 * 5 + 3]
        r1[1].y = r0[1].x * matt[0 * 5 + 4] + r0[1].y * matt[1 * 5 + 4] + r0[1].z * matt[2 * 5 + 4]
        r1[2].x = r0[2].x * matt[0 * 5 + 3] + r0[2].y * matt[1 * 5 + 3] + r0[2].z * matt[2 * 5 + 3]
        r1[2].y = r0[2].x * matt[0 * 5 + 4] + r0[2].y * matt[1 * 5 + 4] + r0[2].z * matt[2 * 5 + 4]

        // r2 = m2 * r1;		// 2x2 = 2x3 * 3x2
        r2[0].x = matt[3 * 5 + 0] * r1[0].x + matt[3 * 5 + 1] * r1[1].x + matt[3 * 5 + 2] * r1[2].x
        r2[0].y = matt[3 * 5 + 0] * r1[0].y + matt[3 * 5 + 1] * r1[1].y + matt[3 * 5 + 2] * r1[2].y
        r2[1].x = matt[4 * 5 + 0] * r1[0].x + matt[4 * 5 + 1] * r1[1].x + matt[4 * 5 + 2] * r1[2].x
        r2[1].y = matt[4 * 5 + 0] * r1[0].y + matt[4 * 5 + 1] * r1[1].y + matt[4 * 5 + 2] * r1[2].y

        // r3 = r2 - m3;		// 2x2 = 2x2 - 2x2
        r3[0].x = r2[0].x - matt[3 * 5 + 3]
        r3[0].y = r2[0].y - matt[3 * 5 + 4]
        r3[1].x = r2[1].x - matt[4 * 5 + 3]
        r3[1].y = r2[1].y - matt[4 * 5 + 4]

        // r3.InverseSelf();	// 2x2
        det = r3[0].x * r3[1].y - r3[0].y * r3[1].x
        if (abs(det) < MATRIX_INVERSE_EPSILON) {
            return false
        }
        invDet = 1.0f / det
        c0 = r3[0].x
        r3[0].x = r3[1].y * invDet
        r3[0].y = -r3[0].y * invDet
        r3[1].x = -r3[1].x * invDet
        r3[1].y = c0 * invDet

        // r2 = m2 * r0;		// 2x3 = 2x3 * 3x3
        r2[0].x = matt[3 * 5 + 0] * r0[0].x + matt[3 * 5 + 1] * r0[1].x + matt[3 * 5 + 2] * r0[2].x
        r2[0].y = matt[3 * 5 + 0] * r0[0].y + matt[3 * 5 + 1] * r0[1].y + matt[3 * 5 + 2] * r0[2].y
        r2[0].z = matt[3 * 5 + 0] * r0[0].z + matt[3 * 5 + 1] * r0[1].z + matt[3 * 5 + 2] * r0[2].z
        r2[1].x = matt[4 * 5 + 0] * r0[0].x + matt[4 * 5 + 1] * r0[1].x + matt[4 * 5 + 2] * r0[2].x
        r2[1].y = matt[4 * 5 + 0] * r0[0].y + matt[4 * 5 + 1] * r0[1].y + matt[4 * 5 + 2] * r0[2].y
        r2[1].z = matt[4 * 5 + 0] * r0[0].z + matt[4 * 5 + 1] * r0[1].z + matt[4 * 5 + 2] * r0[2].z

        // m2 = r3 * r2;		// 2x3 = 2x2 * 2x3
        mat[3][0] = r3[0].x * r2[0].x + r3[0].y * r2[1].x
        mat[3][1] = r3[0].x * r2[0].y + r3[0].y * r2[1].y
        mat[3][2] = r3[0].x * r2[0].z + r3[0].y * r2[1].z
        mat[4][0] = r3[1].x * r2[0].x + r3[1].y * r2[1].x
        mat[4][1] = r3[1].x * r2[0].y + r3[1].y * r2[1].y
        mat[4][2] = r3[1].x * r2[0].z + r3[1].y * r2[1].z

        // m0 = r0 - r1 * m2;	// 3x3 = 3x3 - 3x2 * 2x3
        mat[0][0] = r0[0].x - r1[0].x * mat[3][0] - r1[0].y * mat[4][0]
        mat[0][1] = r0[0].y - r1[0].x * mat[3][1] - r1[0].y * mat[4][1]
        mat[0][2] = r0[0].z - r1[0].x * mat[3][2] - r1[0].y * mat[4][2]
        mat[1][0] = r0[1].x - r1[1].x * mat[3][0] - r1[1].y * mat[4][0]
        mat[1][1] = r0[1].y - r1[1].x * mat[3][1] - r1[1].y * mat[4][1]
        mat[1][2] = r0[1].z - r1[1].x * mat[3][2] - r1[1].y * mat[4][2]
        mat[2][0] = r0[2].x - r1[2].x * mat[3][0] - r1[2].y * mat[4][0]
        mat[2][1] = r0[2].y - r1[2].x * mat[3][1] - r1[2].y * mat[4][1]
        mat[2][2] = r0[2].z - r1[2].x * mat[3][2] - r1[2].y * mat[4][2]

        // m1 = r1 * r3;		// 3x2 = 3x2 * 2x2
        mat[0][3] = r1[0].x * r3[0].x + r1[0].y * r3[1].x
        mat[0][4] = r1[0].x * r3[0].y + r1[0].y * r3[1].y
        mat[1][3] = r1[1].x * r3[0].x + r1[1].y * r3[1].x
        mat[1][4] = r1[1].x * r3[0].y + r1[1].y * r3[1].y
        mat[2][3] = r1[2].x * r3[0].x + r1[2].y * r3[1].x
        mat[2][4] = r1[2].x * r3[0].y + r1[2].y * r3[1].y

        // m3 = -r3;			// 2x2 = - 2x2
        mat[3][3] = -r3[0].x
        mat[3][4] = -r3[0].y
        mat[4][3] = -r3[1].x
        mat[4][4] = -r3[1].y
        return true
    }

    fun GetDimension(): Int {
        return 25
    }

    private fun set(mat5: idMat5) {
        mat[0].set(mat5.mat[0])
        mat[1].set(mat5.mat[1])
        mat[2].set(mat5.mat[2])
        mat[3].set(mat5.mat[3])
        mat[4].set(mat5.mat[4])
    }

    fun reinterpret_cast(): FloatArray {
        val size = 5
        val temp = FloatArray(size * size)
        for (x in 0 until size) {
            for (y in 0 until size) {
                temp[x * size + y] = mat[x][y]
            }
        }
        return temp
    }

    companion object {
        private val mat5_identity: idMat5 = idMat5(
            idVec5(1.0f, 0.0f, 0.0f, 0.0f, 0.0f),
            idVec5(0.0f, 1.0f, 0.0f, 0.0f, 0.0f),
            idVec5(0.0f, 0.0f, 1.0f, 0.0f, 0.0f),
            idVec5(0.0f, 0.0f, 0.0f, 1.0f, 0.0f),
            idVec5(0.0f, 0.0f, 0.0f, 0.0f, 1.0f)
        )
        private val mat5_zero: idMat5 = idMat5(
            idVec5(0.0f, 0.0f, 0.0f, 0.0f, 0.0f),
            idVec5(0.0f, 0.0f, 0.0f, 0.0f, 0.0f),
            idVec5(0.0f, 0.0f, 0.0f, 0.0f, 0.0f),
            idVec5(0.0f, 0.0f, 0.0f, 0.0f, 0.0f),
            idVec5(0.0f, 0.0f, 0.0f, 0.0f, 0.0f)
        )

        fun getMat5_zero(): idMat5 {
            return idMat5(mat5_zero)
        }

        fun getMat5_identity(): idMat5 {
            return idMat5(mat5_identity)
        }

        fun times(a: Float, mat: idMat5): idMat5 {
            return mat * a
        }

        fun times(vec: idVec5, mat: idMat5): idVec5 {
            return mat * vec
        }

        fun timesAssign(vec: idVec5, mat: idMat5): idVec5 {
            return vec.set(mat * vec)
        }
    }
}
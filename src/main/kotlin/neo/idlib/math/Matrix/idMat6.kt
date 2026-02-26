package neo.idlib.math.Matrix

import neo.idlib.math.idVec3
import neo.idlib.math.idVec6
import kotlin.math.abs

//===============================================================
//
//	idMat6 - 6x6 matrix
//
//===============================================================
class idMat6 {
    private val mat: Array<idVec6> = Array(6) { idVec6() }

    constructor()
    constructor(v0: idVec6, v1: idVec6, v2: idVec6, v3: idVec6, v4: idVec6, v5: idVec6) {
        mat[0].set(v0)
        mat[1].set(v1)
        mat[2].set(v2)
        mat[3].set(v3)
        mat[4].set(v4)
        mat[5].set(v5)
    }

    constructor(m0: idMat3, m1: idMat3, m2: idMat3, m3: idMat3) {
        mat[0].set(idVec6(m0.mat[0].x, m0.mat[0].y, m0.mat[0].z, m1.mat[0].x, m1.mat[0].y, m1.mat[0].z))
        mat[1].set(idVec6(m0.mat[1].x, m0.mat[1].y, m0.mat[1].z, m1.mat[1].x, m1.mat[1].y, m1.mat[1].z))
        mat[2].set(idVec6(m0.mat[2].x, m0.mat[2].y, m0.mat[2].z, m1.mat[2].x, m1.mat[2].y, m1.mat[2].z))
        mat[3].set(idVec6(m2.mat[0].x, m2.mat[0].y, m2.mat[0].z, m3.mat[0].x, m3.mat[0].y, m3.mat[0].z))
        mat[4].set(idVec6(m2.mat[1].x, m2.mat[1].y, m2.mat[1].z, m3.mat[1].x, m3.mat[1].y, m3.mat[1].z))
        mat[5].set(idVec6(m2.mat[2].x, m2.mat[2].y, m2.mat[2].z, m3.mat[2].x, m3.mat[2].y, m3.mat[2].z))
    }

    constructor(src: Array<FloatArray>) {
        mat[0].set(
            idVec6(
                src[0][0], src[0][1], src[0][2], src[0][3], src[0][4], src[0][5]
            )
        )
        mat[1].set(
            idVec6(
                src[1][0], src[1][1], src[1][2], src[1][3], src[1][4], src[1][5]
            )
        )
        mat[2].set(
            idVec6(
                src[2][0], src[2][1], src[2][2], src[2][3], src[2][4], src[2][5]
            )
        )
        mat[3].set(
            idVec6(
                src[3][0], src[3][1], src[3][2], src[3][3], src[3][4], src[3][5]
            )
        )
        mat[4].set(
            idVec6(
                src[4][0], src[4][1], src[4][2], src[4][3], src[4][4], src[4][5]
            )
        )
        mat[5].set(
            idVec6(
                src[5][0], src[5][1], src[5][2], src[5][3], src[5][4], src[5][5]
            )
        )
    }

    constructor(m: idMat6) {
        set(m)
    }

    operator fun times(a: Float): idMat6 {
        return idMat6(
            idVec6(
                mat[0].p[0] * a, mat[0].p[1] * a, mat[0].p[2] * a, mat[0].p[3] * a, mat[0].p[4] * a, mat[0].p[5] * a
            ), idVec6(
                mat[1].p[0] * a, mat[1].p[1] * a, mat[1].p[2] * a, mat[1].p[3] * a, mat[1].p[4] * a, mat[1].p[5] * a
            ), idVec6(
                mat[2].p[0] * a, mat[2].p[1] * a, mat[2].p[2] * a, mat[2].p[3] * a, mat[2].p[4] * a, mat[2].p[5] * a
            ), idVec6(
                mat[3].p[0] * a, mat[3].p[1] * a, mat[3].p[2] * a, mat[3].p[3] * a, mat[3].p[4] * a, mat[3].p[5] * a
            ), idVec6(
                mat[4].p[0] * a, mat[4].p[1] * a, mat[4].p[2] * a, mat[4].p[3] * a, mat[4].p[4] * a, mat[4].p[5] * a
            ), idVec6(
                mat[5].p[0] * a, mat[5].p[1] * a, mat[5].p[2] * a, mat[5].p[3] * a, mat[5].p[4] * a, mat[5].p[5] * a
            )
        )
    }

    operator fun times(vec: idVec6): idVec6 {
        return idVec6(
            mat[0].p[0] * vec.p[0] + mat[0].p[1] * vec.p[1] + mat[0].p[2] * vec.p[2] + mat[0].p[3] * vec.p[3] + mat[0].p[4] * vec.p[4] + mat[0].p[5] * vec.p[5],
            mat[1].p[0] * vec.p[0] + mat[1].p[1] * vec.p[1] + mat[1].p[2] * vec.p[2] + mat[1].p[3] * vec.p[3] + mat[1].p[4] * vec.p[4] + mat[1].p[5] * vec.p[5],
            mat[2].p[0] * vec.p[0] + mat[2].p[1] * vec.p[1] + mat[2].p[2] * vec.p[2] + mat[2].p[3] * vec.p[3] + mat[2].p[4] * vec.p[4] + mat[2].p[5] * vec.p[5],
            mat[3].p[0] * vec.p[0] + mat[3].p[1] * vec.p[1] + mat[3].p[2] * vec.p[2] + mat[3].p[3] * vec.p[3] + mat[3].p[4] * vec.p[4] + mat[3].p[5] * vec.p[5],
            mat[4].p[0] * vec.p[0] + mat[4].p[1] * vec.p[1] + mat[4].p[2] * vec.p[2] + mat[4].p[3] * vec.p[3] + mat[4].p[4] * vec.p[4] + mat[4].p[5] * vec.p[5],
            mat[5].p[0] * vec.p[0] + mat[5].p[1] * vec.p[1] + mat[5].p[2] * vec.p[2] + mat[5].p[3] * vec.p[3] + mat[5].p[4] * vec.p[4] + mat[5].p[5] * vec.p[5]
        )
    }

    fun times(a: idMat6): idMat6 {
        var i: Int
        var j: Int
        val m1Ptr: FloatArray
        val m2Ptr: FloatArray
        val dst = idMat6()
        m1Ptr = reinterpret_cast()
        m2Ptr = a.reinterpret_cast()
        i = 0
        while (i < 6) {
            j = 0
            while (j < 6) {
                dst.mat[i].p[i] =
                    m1Ptr[0] * m2Ptr[0 * 6 + j] + m1Ptr[1] * m2Ptr[1 * 6 + j] + m1Ptr[2] * m2Ptr[2 * 6 + j] + m1Ptr[3] * m2Ptr[3 * 6 + j] + m1Ptr[4] * m2Ptr[4 * 6 + j] + m1Ptr[5] * m2Ptr[5 * 6 + j]
                j++
            }
            i++
        }
        return dst
    }

    operator fun plus(a: idMat6): idMat6 {
        return idMat6(
            idVec6(
                mat[0].p[0] + a.mat[0].p[0],
                mat[0].p[1] + a.mat[0].p[1],
                mat[0].p[2] + a.mat[0].p[2],
                mat[0].p[3] + a.mat[0].p[3],
                mat[0].p[4] + a.mat[0].p[4],
                mat[0].p[5] + a.mat[0].p[5]
            ), idVec6(
                mat[1].p[0] + a.mat[1].p[0],
                mat[1].p[1] + a.mat[1].p[1],
                mat[1].p[2] + a.mat[1].p[2],
                mat[1].p[3] + a.mat[1].p[3],
                mat[1].p[4] + a.mat[1].p[4],
                mat[1].p[5] + a.mat[1].p[5]
            ), idVec6(
                mat[2].p[0] + a.mat[2].p[0],
                mat[2].p[1] + a.mat[2].p[1],
                mat[2].p[2] + a.mat[2].p[2],
                mat[2].p[3] + a.mat[2].p[3],
                mat[2].p[4] + a.mat[2].p[4],
                mat[2].p[5] + a.mat[2].p[5]
            ), idVec6(
                mat[3].p[0] + a.mat[3].p[0],
                mat[3].p[1] + a.mat[3].p[1],
                mat[3].p[2] + a.mat[3].p[2],
                mat[3].p[3] + a.mat[3].p[3],
                mat[3].p[4] + a.mat[3].p[4],
                mat[3].p[5] + a.mat[3].p[5]
            ), idVec6(
                mat[4].p[0] + a.mat[4].p[0],
                mat[4].p[1] + a.mat[4].p[1],
                mat[4].p[2] + a.mat[4].p[2],
                mat[4].p[3] + a.mat[4].p[3],
                mat[4].p[4] + a.mat[4].p[4],
                mat[4].p[5] + a.mat[4].p[5]
            ), idVec6(
                mat[5].p[0] + a.mat[5].p[0],
                mat[5].p[1] + a.mat[5].p[1],
                mat[5].p[2] + a.mat[5].p[2],
                mat[5].p[3] + a.mat[5].p[3],
                mat[5].p[4] + a.mat[5].p[4],
                mat[5].p[5] + a.mat[5].p[5]
            )
        )
    }

    operator fun minus(a: idMat6): idMat6 {
        return idMat6(
            idVec6(
                mat[0].p[0] - a.mat[0].p[0],
                mat[0].p[1] - a.mat[0].p[1],
                mat[0].p[2] - a.mat[0].p[2],
                mat[0].p[3] - a.mat[0].p[3],
                mat[0].p[4] - a.mat[0].p[4],
                mat[0].p[5] - a.mat[0].p[5]
            ), idVec6(
                mat[1].p[0] - a.mat[1].p[0],
                mat[1].p[1] - a.mat[1].p[1],
                mat[1].p[2] - a.mat[1].p[2],
                mat[1].p[3] - a.mat[1].p[3],
                mat[1].p[4] - a.mat[1].p[4],
                mat[1].p[5] - a.mat[1].p[5]
            ), idVec6(
                mat[2].p[0] - a.mat[2].p[0],
                mat[2].p[1] - a.mat[2].p[1],
                mat[2].p[2] - a.mat[2].p[2],
                mat[2].p[3] - a.mat[2].p[3],
                mat[2].p[4] - a.mat[2].p[4],
                mat[2].p[5] - a.mat[2].p[5]
            ), idVec6(
                mat[3].p[0] - a.mat[3].p[0],
                mat[3].p[1] - a.mat[3].p[1],
                mat[3].p[2] - a.mat[3].p[2],
                mat[3].p[3] - a.mat[3].p[3],
                mat[3].p[4] - a.mat[3].p[4],
                mat[3].p[5] - a.mat[3].p[5]
            ), idVec6(
                mat[4].p[0] - a.mat[4].p[0],
                mat[4].p[1] - a.mat[4].p[1],
                mat[4].p[2] - a.mat[4].p[2],
                mat[4].p[3] - a.mat[4].p[3],
                mat[4].p[4] - a.mat[4].p[4],
                mat[4].p[5] - a.mat[4].p[5]
            ), idVec6(
                mat[5].p[0] - a.mat[5].p[0],
                mat[5].p[1] - a.mat[5].p[1],
                mat[5].p[2] - a.mat[5].p[2],
                mat[5].p[3] - a.mat[5].p[3],
                mat[5].p[4] - a.mat[5].p[4],
                mat[5].p[5] - a.mat[5].p[5]
            )
        )
    }

    fun timesAssign(a: Float): idMat6 {
        mat[0].p[0] *= a
        mat[0].p[1] *= a
        mat[0].p[2] *= a
        mat[0].p[3] *= a
        mat[0].p[4] *= a
        mat[0].p[5] *= a
        mat[1].p[0] *= a
        mat[1].p[1] *= a
        mat[1].p[2] *= a
        mat[1].p[3] *= a
        mat[1].p[4] *= a
        mat[1].p[5] *= a
        mat[2].p[0] *= a
        mat[2].p[1] *= a
        mat[2].p[2] *= a
        mat[2].p[3] *= a
        mat[2].p[4] *= a
        mat[2].p[5] *= a
        mat[3].p[0] *= a
        mat[3].p[1] *= a
        mat[3].p[2] *= a
        mat[3].p[3] *= a
        mat[3].p[4] *= a
        mat[3].p[5] *= a
        mat[4].p[0] *= a
        mat[4].p[1] *= a
        mat[4].p[2] *= a
        mat[4].p[3] *= a
        mat[4].p[4] *= a
        mat[4].p[5] *= a
        mat[5].p[0] *= a
        mat[5].p[1] *= a
        mat[5].p[2] *= a
        mat[5].p[3] *= a
        mat[5].p[4] *= a
        mat[5].p[5] *= a
        return this
    }

    fun timesAssign(a: idMat6): idMat6 {
        set(times(a))
        return this
    }

    fun plusAssign(a: idMat6): idMat6 {
        mat[0].p[0] += a.mat[0].p[0]
        mat[0].p[1] += a.mat[0].p[1]
        mat[0].p[2] += a.mat[0].p[2]
        mat[0].p[3] += a.mat[0].p[3]
        mat[0].p[4] += a.mat[0].p[4]
        mat[0].p[5] += a.mat[0].p[5]
        mat[1].p[0] += a.mat[1].p[0]
        mat[1].p[1] += a.mat[1].p[1]
        mat[1].p[2] += a.mat[1].p[2]
        mat[1].p[3] += a.mat[1].p[3]
        mat[1].p[4] += a.mat[1].p[4]
        mat[1].p[5] += a.mat[1].p[5]
        mat[2].p[0] += a.mat[2].p[0]
        mat[2].p[1] += a.mat[2].p[1]
        mat[2].p[2] += a.mat[2].p[2]
        mat[2].p[3] += a.mat[2].p[3]
        mat[2].p[4] += a.mat[2].p[4]
        mat[2].p[5] += a.mat[2].p[5]
        mat[3].p[0] += a.mat[3].p[0]
        mat[3].p[1] += a.mat[3].p[1]
        mat[3].p[2] += a.mat[3].p[2]
        mat[3].p[3] += a.mat[3].p[3]
        mat[3].p[4] += a.mat[3].p[4]
        mat[3].p[5] += a.mat[3].p[5]
        mat[4].p[0] += a.mat[4].p[0]
        mat[4].p[1] += a.mat[4].p[1]
        mat[4].p[2] += a.mat[4].p[2]
        mat[4].p[3] += a.mat[4].p[3]
        mat[4].p[4] += a.mat[4].p[4]
        mat[4].p[5] += a.mat[4].p[5]
        mat[5].p[0] += a.mat[5].p[0]
        mat[5].p[1] += a.mat[5].p[1]
        mat[5].p[2] += a.mat[5].p[2]
        mat[5].p[3] += a.mat[5].p[3]
        mat[5].p[4] += a.mat[5].p[4]
        mat[5].p[5] += a.mat[5].p[5]
        return this
    }

    fun minusAssign(a: idMat6): idMat6 {
        mat[0].p[0] -= a.mat[0].p[0]
        mat[0].p[1] -= a.mat[0].p[1]
        mat[0].p[2] -= a.mat[0].p[2]
        mat[0].p[3] -= a.mat[0].p[3]
        mat[0].p[4] -= a.mat[0].p[4]
        mat[0].p[5] -= a.mat[0].p[5]
        mat[1].p[0] -= a.mat[1].p[0]
        mat[1].p[1] -= a.mat[1].p[1]
        mat[1].p[2] -= a.mat[1].p[2]
        mat[1].p[3] -= a.mat[1].p[3]
        mat[1].p[4] -= a.mat[1].p[4]
        mat[1].p[5] -= a.mat[1].p[5]
        mat[2].p[0] -= a.mat[2].p[0]
        mat[2].p[1] -= a.mat[2].p[1]
        mat[2].p[2] -= a.mat[2].p[2]
        mat[2].p[3] -= a.mat[2].p[3]
        mat[2].p[4] -= a.mat[2].p[4]
        mat[2].p[5] -= a.mat[2].p[5]
        mat[3].p[0] -= a.mat[3].p[0]
        mat[3].p[1] -= a.mat[3].p[1]
        mat[3].p[2] -= a.mat[3].p[2]
        mat[3].p[3] -= a.mat[3].p[3]
        mat[3].p[4] -= a.mat[3].p[4]
        mat[3].p[5] -= a.mat[3].p[5]
        mat[4].p[0] -= a.mat[4].p[0]
        mat[4].p[1] -= a.mat[4].p[1]
        mat[4].p[2] -= a.mat[4].p[2]
        mat[4].p[3] -= a.mat[4].p[3]
        mat[4].p[4] -= a.mat[4].p[4]
        mat[4].p[5] -= a.mat[4].p[5]
        mat[5].p[0] -= a.mat[5].p[0]
        mat[5].p[1] -= a.mat[5].p[1]
        mat[5].p[2] -= a.mat[5].p[2]
        mat[5].p[3] -= a.mat[5].p[3]
        mat[5].p[4] -= a.mat[5].p[4]
        mat[5].p[5] -= a.mat[5].p[5]
        return this
    }

    fun Compare(a: idMat6): Boolean { // exact compare, no epsilon
        var i: Int
        val ptr1: FloatArray
        val ptr2: FloatArray
        ptr1 = reinterpret_cast()
        ptr2 = a.reinterpret_cast()
        i = 0
        while (i < 6 * 6) {
            if (ptr1[i] != ptr2[i]) {
                return false
            }
            i++
        }
        return true
    }

    fun Compare(a: idMat6, epsilon: Float): Boolean { // compare with epsilon
        var i: Int
        val ptr1: FloatArray
        val ptr2: FloatArray
        ptr1 = reinterpret_cast()
        ptr2 = a.reinterpret_cast()
        i = 0
        while (i < 6 * 6) {
            if (abs(ptr1[i] - ptr2[i]) > epsilon) {
                return false
            }
            i++
        }
        return true
    }

    override fun hashCode(): Int {
        var hash = 3
        hash = 13 * hash + mat.contentDeepHashCode()
        return hash
    }

    override fun equals(obj: Any?): Boolean {
        if (obj == null) {
            return false
        }
        if (javaClass != obj.javaClass) {
            return false
        }
        val other = obj as idMat6
        return Compare(other)
    }

    fun equals(a: idMat6): Boolean { // exact compare, no epsilon
        return Compare(a)
    }

    fun notEquals(a: idMat6): Boolean { // exact compare, no epsilon
        return !Compare(a)
    }

    fun Zero() {
        set(getMat6_zero())
    }

    fun Identity() {
        set(getMat6_identity())
    }


    fun IsIdentity(epsilon: Float): Boolean {
        return Compare(getMat6_identity(), epsilon)
    }


    fun IsSymmetric(epsilon: Float): Boolean {
        for (i in 1..5) {
            for (j in 0 until i) {
                if (abs(mat[i].p[j] - mat[j].p[i]) > epsilon) {
                    return false
                }
            }
        }
        return true
    }

    fun IsDiagonal(epsilon: Float): Boolean {
        for (i in 0..5) {
            for (j in 0..5) {
                if (i != j && abs(mat[i].p[j]) > epsilon) {
                    return false
                }
            }
        }
        return true
    }

    fun SubMat3(n: Int): idMat3 {
        assert(n >= 0 && n < 4)
        val b0 = (n and 2 shr 1) * 3
        val b1 = (n and 1) * 3
        return idMat3(
            mat[b0 + 0].p[b1 + 0],
            mat[b0 + 0].p[b1 + 1],
            mat[b0 + 0].p[b1 + 2],
            mat[b0 + 1].p[b1 + 0],
            mat[b0 + 1].p[b1 + 1],
            mat[b0 + 1].p[b1 + 2],
            mat[b0 + 2].p[b1 + 0],
            mat[b0 + 2].p[b1 + 1],
            mat[b0 + 2].p[b1 + 2]
        )
    }

    fun Trace(): Float {
        return mat[0].p[0] + mat[1].p[1] + mat[2].p[2] + mat[3].p[3] + mat[4].p[4] + mat[5].p[5]
    }

    fun Determinant(): Float {
        // 2x2 sub-determinants required to calculate 6x6 determinant
        val det2_45_01 = mat[4].p[0] * mat[5].p[1] - mat[4].p[1] * mat[5].p[0]
        val det2_45_02 = mat[4].p[0] * mat[5].p[2] - mat[4].p[2] * mat[5].p[0]
        val det2_45_03 = mat[4].p[0] * mat[5].p[3] - mat[4].p[3] * mat[5].p[0]
        val det2_45_04 = mat[4].p[0] * mat[5].p[4] - mat[4].p[4] * mat[5].p[0]
        val det2_45_05 = mat[4].p[0] * mat[5].p[5] - mat[4].p[5] * mat[5].p[0]
        val det2_45_12 = mat[4].p[1] * mat[5].p[2] - mat[4].p[2] * mat[5].p[1]
        val det2_45_13 = mat[4].p[1] * mat[5].p[3] - mat[4].p[3] * mat[5].p[1]
        val det2_45_14 = mat[4].p[1] * mat[5].p[4] - mat[4].p[4] * mat[5].p[1]
        val det2_45_15 = mat[4].p[1] * mat[5].p[5] - mat[4].p[5] * mat[5].p[1]
        val det2_45_23 = mat[4].p[2] * mat[5].p[3] - mat[4].p[3] * mat[5].p[2]
        val det2_45_24 = mat[4].p[2] * mat[5].p[4] - mat[4].p[4] * mat[5].p[2]
        val det2_45_25 = mat[4].p[2] * mat[5].p[5] - mat[4].p[5] * mat[5].p[2]
        val det2_45_34 = mat[4].p[3] * mat[5].p[4] - mat[4].p[4] * mat[5].p[3]
        val det2_45_35 = mat[4].p[3] * mat[5].p[5] - mat[4].p[5] * mat[5].p[3]
        val det2_45_45 = mat[4].p[4] * mat[5].p[5] - mat[4].p[5] * mat[5].p[4]

        // 3x3 sub-determinants required to calculate 6x6 determinant
        val det3_345_012 = mat[3].p[0] * det2_45_12 - mat[3].p[1] * det2_45_02 + mat[3].p[2] * det2_45_01
        val det3_345_013 = mat[3].p[0] * det2_45_13 - mat[3].p[1] * det2_45_03 + mat[3].p[3] * det2_45_01
        val det3_345_014 = mat[3].p[0] * det2_45_14 - mat[3].p[1] * det2_45_04 + mat[3].p[4] * det2_45_01
        val det3_345_015 = mat[3].p[0] * det2_45_15 - mat[3].p[1] * det2_45_05 + mat[3].p[5] * det2_45_01
        val det3_345_023 = mat[3].p[0] * det2_45_23 - mat[3].p[2] * det2_45_03 + mat[3].p[3] * det2_45_02
        val det3_345_024 = mat[3].p[0] * det2_45_24 - mat[3].p[2] * det2_45_04 + mat[3].p[4] * det2_45_02
        val det3_345_025 = mat[3].p[0] * det2_45_25 - mat[3].p[2] * det2_45_05 + mat[3].p[5] * det2_45_02
        val det3_345_034 = mat[3].p[0] * det2_45_34 - mat[3].p[3] * det2_45_04 + mat[3].p[4] * det2_45_03
        val det3_345_035 = mat[3].p[0] * det2_45_35 - mat[3].p[3] * det2_45_05 + mat[3].p[5] * det2_45_03
        val det3_345_045 = mat[3].p[0] * det2_45_45 - mat[3].p[4] * det2_45_05 + mat[3].p[5] * det2_45_04
        val det3_345_123 = mat[3].p[1] * det2_45_23 - mat[3].p[2] * det2_45_13 + mat[3].p[3] * det2_45_12
        val det3_345_124 = mat[3].p[1] * det2_45_24 - mat[3].p[2] * det2_45_14 + mat[3].p[4] * det2_45_12
        val det3_345_125 = mat[3].p[1] * det2_45_25 - mat[3].p[2] * det2_45_15 + mat[3].p[5] * det2_45_12
        val det3_345_134 = mat[3].p[1] * det2_45_34 - mat[3].p[3] * det2_45_14 + mat[3].p[4] * det2_45_13
        val det3_345_135 = mat[3].p[1] * det2_45_35 - mat[3].p[3] * det2_45_15 + mat[3].p[5] * det2_45_13
        val det3_345_145 = mat[3].p[1] * det2_45_45 - mat[3].p[4] * det2_45_15 + mat[3].p[5] * det2_45_14
        val det3_345_234 = mat[3].p[2] * det2_45_34 - mat[3].p[3] * det2_45_24 + mat[3].p[4] * det2_45_23
        val det3_345_235 = mat[3].p[2] * det2_45_35 - mat[3].p[3] * det2_45_25 + mat[3].p[5] * det2_45_23
        val det3_345_245 = mat[3].p[2] * det2_45_45 - mat[3].p[4] * det2_45_25 + mat[3].p[5] * det2_45_24
        val det3_345_345 = mat[3].p[3] * det2_45_45 - mat[3].p[4] * det2_45_35 + mat[3].p[5] * det2_45_34

        // 4x4 sub-determinants required to calculate 6x6 determinant
        val det4_2345_0123 =
            mat[2].p[0] * det3_345_123 - mat[2].p[1] * det3_345_023 + mat[2].p[2] * det3_345_013 - mat[2].p[3] * det3_345_012
        val det4_2345_0124 =
            mat[2].p[0] * det3_345_124 - mat[2].p[1] * det3_345_024 + mat[2].p[2] * det3_345_014 - mat[2].p[4] * det3_345_012
        val det4_2345_0125 =
            mat[2].p[0] * det3_345_125 - mat[2].p[1] * det3_345_025 + mat[2].p[2] * det3_345_015 - mat[2].p[5] * det3_345_012
        val det4_2345_0134 =
            mat[2].p[0] * det3_345_134 - mat[2].p[1] * det3_345_034 + mat[2].p[3] * det3_345_014 - mat[2].p[4] * det3_345_013
        val det4_2345_0135 =
            mat[2].p[0] * det3_345_135 - mat[2].p[1] * det3_345_035 + mat[2].p[3] * det3_345_015 - mat[2].p[5] * det3_345_013
        val det4_2345_0145 =
            mat[2].p[0] * det3_345_145 - mat[2].p[1] * det3_345_045 + mat[2].p[4] * det3_345_015 - mat[2].p[5] * det3_345_014
        val det4_2345_0234 =
            mat[2].p[0] * det3_345_234 - mat[2].p[2] * det3_345_034 + mat[2].p[3] * det3_345_024 - mat[2].p[4] * det3_345_023
        val det4_2345_0235 =
            mat[2].p[0] * det3_345_235 - mat[2].p[2] * det3_345_035 + mat[2].p[3] * det3_345_025 - mat[2].p[5] * det3_345_023
        val det4_2345_0245 =
            mat[2].p[0] * det3_345_245 - mat[2].p[2] * det3_345_045 + mat[2].p[4] * det3_345_025 - mat[2].p[5] * det3_345_024
        val det4_2345_0345 =
            mat[2].p[0] * det3_345_345 - mat[2].p[3] * det3_345_045 + mat[2].p[4] * det3_345_035 - mat[2].p[5] * det3_345_034
        val det4_2345_1234 =
            mat[2].p[1] * det3_345_234 - mat[2].p[2] * det3_345_134 + mat[2].p[3] * det3_345_124 - mat[2].p[4] * det3_345_123
        val det4_2345_1235 =
            mat[2].p[1] * det3_345_235 - mat[2].p[2] * det3_345_135 + mat[2].p[3] * det3_345_125 - mat[2].p[5] * det3_345_123
        val det4_2345_1245 =
            mat[2].p[1] * det3_345_245 - mat[2].p[2] * det3_345_145 + mat[2].p[4] * det3_345_125 - mat[2].p[5] * det3_345_124
        val det4_2345_1345 =
            mat[2].p[1] * det3_345_345 - mat[2].p[3] * det3_345_145 + mat[2].p[4] * det3_345_135 - mat[2].p[5] * det3_345_134
        val det4_2345_2345 =
            mat[2].p[2] * det3_345_345 - mat[2].p[3] * det3_345_245 + mat[2].p[4] * det3_345_235 - mat[2].p[5] * det3_345_234

        // 5x5 sub-determinants required to calculate 6x6 determinant
        val det5_12345_01234 =
            mat[1].p[0] * det4_2345_1234 - mat[1].p[1] * det4_2345_0234 + mat[1].p[2] * det4_2345_0134 - mat[1].p[3] * det4_2345_0124 + mat[1].p[4] * det4_2345_0123
        val det5_12345_01235 =
            mat[1].p[0] * det4_2345_1235 - mat[1].p[1] * det4_2345_0235 + mat[1].p[2] * det4_2345_0135 - mat[1].p[3] * det4_2345_0125 + mat[1].p[5] * det4_2345_0123
        val det5_12345_01245 =
            mat[1].p[0] * det4_2345_1245 - mat[1].p[1] * det4_2345_0245 + mat[1].p[2] * det4_2345_0145 - mat[1].p[4] * det4_2345_0125 + mat[1].p[5] * det4_2345_0124
        val det5_12345_01345 =
            mat[1].p[0] * det4_2345_1345 - mat[1].p[1] * det4_2345_0345 + mat[1].p[3] * det4_2345_0145 - mat[1].p[4] * det4_2345_0135 + mat[1].p[5] * det4_2345_0134
        val det5_12345_02345 =
            mat[1].p[0] * det4_2345_2345 - mat[1].p[2] * det4_2345_0345 + mat[1].p[3] * det4_2345_0245 - mat[1].p[4] * det4_2345_0235 + mat[1].p[5] * det4_2345_0234
        val det5_12345_12345 =
            mat[1].p[1] * det4_2345_2345 - mat[1].p[2] * det4_2345_1345 + mat[1].p[3] * det4_2345_1245 - mat[1].p[4] * det4_2345_1235 + mat[1].p[5] * det4_2345_1234

        // determinant of 6x6 matrix
        return mat[0].p[0] * det5_12345_12345 - mat[0].p[1] * det5_12345_02345 + mat[0].p[2] * det5_12345_01345 - mat[0].p[3] * det5_12345_01245 + mat[0].p[4] * det5_12345_01235 - mat[0].p[5] * det5_12345_01234
    }

    fun Transpose(): idMat6 { // returns transpose
        val transpose = idMat6()
        var i: Int
        var j: Int
        i = 0
        while (i < 6) {
            j = 0
            while (j < 6) {
                transpose.mat[i].p[j] = mat[j].p[i]
                j++
            }
            i++
        }
        return transpose
    }

    fun TransposeSelf(): idMat6 {
        var temp: Float
        var i: Int
        var j: Int
        i = 0
        while (i < 6) {
            j = i + 1
            while (j < 6) {
                temp = mat[i].p[j]
                mat[i].p[j] = mat[j].p[i]
                mat[j].p[i] = temp
                j++
            }
            i++
        }
        return this
    }

    fun Inverse(): idMat6 { // returns the inverse ( m * m.Inverse() = identity )
        val invMat = idMat6(this)
        val r = invMat.InverseSelf()
        assert(r)
        return invMat
    }

    fun InverseSelf(): Boolean { // returns false if determinant is zero
        // 810+6+36 = 852 multiplications
        //				1 division
        val det: Float
        val invDet: Float

        // 2x2 sub-determinants required to calculate 6x6 determinant
        val det2_45_01 = mat[4].p[0] * mat[5].p[1] - mat[4].p[1] * mat[5].p[0]
        val det2_45_02 = mat[4].p[0] * mat[5].p[2] - mat[4].p[2] * mat[5].p[0]
        val det2_45_03 = mat[4].p[0] * mat[5].p[3] - mat[4].p[3] * mat[5].p[0]
        val det2_45_04 = mat[4].p[0] * mat[5].p[4] - mat[4].p[4] * mat[5].p[0]
        val det2_45_05 = mat[4].p[0] * mat[5].p[5] - mat[4].p[5] * mat[5].p[0]
        val det2_45_12 = mat[4].p[1] * mat[5].p[2] - mat[4].p[2] * mat[5].p[1]
        val det2_45_13 = mat[4].p[1] * mat[5].p[3] - mat[4].p[3] * mat[5].p[1]
        val det2_45_14 = mat[4].p[1] * mat[5].p[4] - mat[4].p[4] * mat[5].p[1]
        val det2_45_15 = mat[4].p[1] * mat[5].p[5] - mat[4].p[5] * mat[5].p[1]
        val det2_45_23 = mat[4].p[2] * mat[5].p[3] - mat[4].p[3] * mat[5].p[2]
        val det2_45_24 = mat[4].p[2] * mat[5].p[4] - mat[4].p[4] * mat[5].p[2]
        val det2_45_25 = mat[4].p[2] * mat[5].p[5] - mat[4].p[5] * mat[5].p[2]
        val det2_45_34 = mat[4].p[3] * mat[5].p[4] - mat[4].p[4] * mat[5].p[3]
        val det2_45_35 = mat[4].p[3] * mat[5].p[5] - mat[4].p[5] * mat[5].p[3]
        val det2_45_45 = mat[4].p[4] * mat[5].p[5] - mat[4].p[5] * mat[5].p[4]

        // 3x3 sub-determinants required to calculate 6x6 determinant
        val det3_345_012 = mat[3].p[0] * det2_45_12 - mat[3].p[1] * det2_45_02 + mat[3].p[2] * det2_45_01
        val det3_345_013 = mat[3].p[0] * det2_45_13 - mat[3].p[1] * det2_45_03 + mat[3].p[3] * det2_45_01
        val det3_345_014 = mat[3].p[0] * det2_45_14 - mat[3].p[1] * det2_45_04 + mat[3].p[4] * det2_45_01
        val det3_345_015 = mat[3].p[0] * det2_45_15 - mat[3].p[1] * det2_45_05 + mat[3].p[5] * det2_45_01
        val det3_345_023 = mat[3].p[0] * det2_45_23 - mat[3].p[2] * det2_45_03 + mat[3].p[3] * det2_45_02
        val det3_345_024 = mat[3].p[0] * det2_45_24 - mat[3].p[2] * det2_45_04 + mat[3].p[4] * det2_45_02
        val det3_345_025 = mat[3].p[0] * det2_45_25 - mat[3].p[2] * det2_45_05 + mat[3].p[5] * det2_45_02
        val det3_345_034 = mat[3].p[0] * det2_45_34 - mat[3].p[3] * det2_45_04 + mat[3].p[4] * det2_45_03
        val det3_345_035 = mat[3].p[0] * det2_45_35 - mat[3].p[3] * det2_45_05 + mat[3].p[5] * det2_45_03
        val det3_345_045 = mat[3].p[0] * det2_45_45 - mat[3].p[4] * det2_45_05 + mat[3].p[5] * det2_45_04
        val det3_345_123 = mat[3].p[1] * det2_45_23 - mat[3].p[2] * det2_45_13 + mat[3].p[3] * det2_45_12
        val det3_345_124 = mat[3].p[1] * det2_45_24 - mat[3].p[2] * det2_45_14 + mat[3].p[4] * det2_45_12
        val det3_345_125 = mat[3].p[1] * det2_45_25 - mat[3].p[2] * det2_45_15 + mat[3].p[5] * det2_45_12
        val det3_345_134 = mat[3].p[1] * det2_45_34 - mat[3].p[3] * det2_45_14 + mat[3].p[4] * det2_45_13
        val det3_345_135 = mat[3].p[1] * det2_45_35 - mat[3].p[3] * det2_45_15 + mat[3].p[5] * det2_45_13
        val det3_345_145 = mat[3].p[1] * det2_45_45 - mat[3].p[4] * det2_45_15 + mat[3].p[5] * det2_45_14
        val det3_345_234 = mat[3].p[2] * det2_45_34 - mat[3].p[3] * det2_45_24 + mat[3].p[4] * det2_45_23
        val det3_345_235 = mat[3].p[2] * det2_45_35 - mat[3].p[3] * det2_45_25 + mat[3].p[5] * det2_45_23
        val det3_345_245 = mat[3].p[2] * det2_45_45 - mat[3].p[4] * det2_45_25 + mat[3].p[5] * det2_45_24
        val det3_345_345 = mat[3].p[3] * det2_45_45 - mat[3].p[4] * det2_45_35 + mat[3].p[5] * det2_45_34

        // 4x4 sub-determinants required to calculate 6x6 determinant
        val det4_2345_0123 =
            mat[2].p[0] * det3_345_123 - mat[2].p[1] * det3_345_023 + mat[2].p[2] * det3_345_013 - mat[2].p[3] * det3_345_012
        val det4_2345_0124 =
            mat[2].p[0] * det3_345_124 - mat[2].p[1] * det3_345_024 + mat[2].p[2] * det3_345_014 - mat[2].p[4] * det3_345_012
        val det4_2345_0125 =
            mat[2].p[0] * det3_345_125 - mat[2].p[1] * det3_345_025 + mat[2].p[2] * det3_345_015 - mat[2].p[5] * det3_345_012
        val det4_2345_0134 =
            mat[2].p[0] * det3_345_134 - mat[2].p[1] * det3_345_034 + mat[2].p[3] * det3_345_014 - mat[2].p[4] * det3_345_013
        val det4_2345_0135 =
            mat[2].p[0] * det3_345_135 - mat[2].p[1] * det3_345_035 + mat[2].p[3] * det3_345_015 - mat[2].p[5] * det3_345_013
        val det4_2345_0145 =
            mat[2].p[0] * det3_345_145 - mat[2].p[1] * det3_345_045 + mat[2].p[4] * det3_345_015 - mat[2].p[5] * det3_345_014
        val det4_2345_0234 =
            mat[2].p[0] * det3_345_234 - mat[2].p[2] * det3_345_034 + mat[2].p[3] * det3_345_024 - mat[2].p[4] * det3_345_023
        val det4_2345_0235 =
            mat[2].p[0] * det3_345_235 - mat[2].p[2] * det3_345_035 + mat[2].p[3] * det3_345_025 - mat[2].p[5] * det3_345_023
        val det4_2345_0245 =
            mat[2].p[0] * det3_345_245 - mat[2].p[2] * det3_345_045 + mat[2].p[4] * det3_345_025 - mat[2].p[5] * det3_345_024
        val det4_2345_0345 =
            mat[2].p[0] * det3_345_345 - mat[2].p[3] * det3_345_045 + mat[2].p[4] * det3_345_035 - mat[2].p[5] * det3_345_034
        val det4_2345_1234 =
            mat[2].p[1] * det3_345_234 - mat[2].p[2] * det3_345_134 + mat[2].p[3] * det3_345_124 - mat[2].p[4] * det3_345_123
        val det4_2345_1235 =
            mat[2].p[1] * det3_345_235 - mat[2].p[2] * det3_345_135 + mat[2].p[3] * det3_345_125 - mat[2].p[5] * det3_345_123
        val det4_2345_1245 =
            mat[2].p[1] * det3_345_245 - mat[2].p[2] * det3_345_145 + mat[2].p[4] * det3_345_125 - mat[2].p[5] * det3_345_124
        val det4_2345_1345 =
            mat[2].p[1] * det3_345_345 - mat[2].p[3] * det3_345_145 + mat[2].p[4] * det3_345_135 - mat[2].p[5] * det3_345_134
        val det4_2345_2345 =
            mat[2].p[2] * det3_345_345 - mat[2].p[3] * det3_345_245 + mat[2].p[4] * det3_345_235 - mat[2].p[5] * det3_345_234

        // 5x5 sub-determinants required to calculate 6x6 determinant
        val det5_12345_01234 =
            mat[1].p[0] * det4_2345_1234 - mat[1].p[1] * det4_2345_0234 + mat[1].p[2] * det4_2345_0134 - mat[1].p[3] * det4_2345_0124 + mat[1].p[4] * det4_2345_0123
        val det5_12345_01235 =
            mat[1].p[0] * det4_2345_1235 - mat[1].p[1] * det4_2345_0235 + mat[1].p[2] * det4_2345_0135 - mat[1].p[3] * det4_2345_0125 + mat[1].p[5] * det4_2345_0123
        val det5_12345_01245 =
            mat[1].p[0] * det4_2345_1245 - mat[1].p[1] * det4_2345_0245 + mat[1].p[2] * det4_2345_0145 - mat[1].p[4] * det4_2345_0125 + mat[1].p[5] * det4_2345_0124
        val det5_12345_01345 =
            mat[1].p[0] * det4_2345_1345 - mat[1].p[1] * det4_2345_0345 + mat[1].p[3] * det4_2345_0145 - mat[1].p[4] * det4_2345_0135 + mat[1].p[5] * det4_2345_0134
        val det5_12345_02345 =
            mat[1].p[0] * det4_2345_2345 - mat[1].p[2] * det4_2345_0345 + mat[1].p[3] * det4_2345_0245 - mat[1].p[4] * det4_2345_0235 + mat[1].p[5] * det4_2345_0234
        val det5_12345_12345 =
            mat[1].p[1] * det4_2345_2345 - mat[1].p[2] * det4_2345_1345 + mat[1].p[3] * det4_2345_1245 - mat[1].p[4] * det4_2345_1235 + mat[1].p[5] * det4_2345_1234

        // determinant of 6x6 matrix
        det =
            (mat[0].p[0] * det5_12345_12345 - mat[0].p[1] * det5_12345_02345 + mat[0].p[2] * det5_12345_01345 - mat[0].p[3] * det5_12345_01245 + mat[0].p[4] * det5_12345_01235 - mat[0].p[5] * det5_12345_01234)
        if (abs(det) < MATRIX_INVERSE_EPSILON) {
            return false
        }
        invDet = 1.0f / det

        // remaining 2x2 sub-determinants
        val det2_34_01 = mat[3].p[0] * mat[4].p[1] - mat[3].p[1] * mat[4].p[0]
        val det2_34_02 = mat[3].p[0] * mat[4].p[2] - mat[3].p[2] * mat[4].p[0]
        val det2_34_03 = mat[3].p[0] * mat[4].p[3] - mat[3].p[3] * mat[4].p[0]
        val det2_34_04 = mat[3].p[0] * mat[4].p[4] - mat[3].p[4] * mat[4].p[0]
        val det2_34_05 = mat[3].p[0] * mat[4].p[5] - mat[3].p[5] * mat[4].p[0]
        val det2_34_12 = mat[3].p[1] * mat[4].p[2] - mat[3].p[2] * mat[4].p[1]
        val det2_34_13 = mat[3].p[1] * mat[4].p[3] - mat[3].p[3] * mat[4].p[1]
        val det2_34_14 = mat[3].p[1] * mat[4].p[4] - mat[3].p[4] * mat[4].p[1]
        val det2_34_15 = mat[3].p[1] * mat[4].p[5] - mat[3].p[5] * mat[4].p[1]
        val det2_34_23 = mat[3].p[2] * mat[4].p[3] - mat[3].p[3] * mat[4].p[2]
        val det2_34_24 = mat[3].p[2] * mat[4].p[4] - mat[3].p[4] * mat[4].p[2]
        val det2_34_25 = mat[3].p[2] * mat[4].p[5] - mat[3].p[5] * mat[4].p[2]
        val det2_34_34 = mat[3].p[3] * mat[4].p[4] - mat[3].p[4] * mat[4].p[3]
        val det2_34_35 = mat[3].p[3] * mat[4].p[5] - mat[3].p[5] * mat[4].p[3]
        val det2_34_45 = mat[3].p[4] * mat[4].p[5] - mat[3].p[5] * mat[4].p[4]
        val det2_35_01 = mat[3].p[0] * mat[5].p[1] - mat[3].p[1] * mat[5].p[0]
        val det2_35_02 = mat[3].p[0] * mat[5].p[2] - mat[3].p[2] * mat[5].p[0]
        val det2_35_03 = mat[3].p[0] * mat[5].p[3] - mat[3].p[3] * mat[5].p[0]
        val det2_35_04 = mat[3].p[0] * mat[5].p[4] - mat[3].p[4] * mat[5].p[0]
        val det2_35_05 = mat[3].p[0] * mat[5].p[5] - mat[3].p[5] * mat[5].p[0]
        val det2_35_12 = mat[3].p[1] * mat[5].p[2] - mat[3].p[2] * mat[5].p[1]
        val det2_35_13 = mat[3].p[1] * mat[5].p[3] - mat[3].p[3] * mat[5].p[1]
        val det2_35_14 = mat[3].p[1] * mat[5].p[4] - mat[3].p[4] * mat[5].p[1]
        val det2_35_15 = mat[3].p[1] * mat[5].p[5] - mat[3].p[5] * mat[5].p[1]
        val det2_35_23 = mat[3].p[2] * mat[5].p[3] - mat[3].p[3] * mat[5].p[2]
        val det2_35_24 = mat[3].p[2] * mat[5].p[4] - mat[3].p[4] * mat[5].p[2]
        val det2_35_25 = mat[3].p[2] * mat[5].p[5] - mat[3].p[5] * mat[5].p[2]
        val det2_35_34 = mat[3].p[3] * mat[5].p[4] - mat[3].p[4] * mat[5].p[3]
        val det2_35_35 = mat[3].p[3] * mat[5].p[5] - mat[3].p[5] * mat[5].p[3]
        val det2_35_45 = mat[3].p[4] * mat[5].p[5] - mat[3].p[5] * mat[5].p[4]

        // remaining 3x3 sub-determinants
        val det3_234_012 = mat[2].p[0] * det2_34_12 - mat[2].p[1] * det2_34_02 + mat[2].p[2] * det2_34_01
        val det3_234_013 = mat[2].p[0] * det2_34_13 - mat[2].p[1] * det2_34_03 + mat[2].p[3] * det2_34_01
        val det3_234_014 = mat[2].p[0] * det2_34_14 - mat[2].p[1] * det2_34_04 + mat[2].p[4] * det2_34_01
        val det3_234_015 = mat[2].p[0] * det2_34_15 - mat[2].p[1] * det2_34_05 + mat[2].p[5] * det2_34_01
        val det3_234_023 = mat[2].p[0] * det2_34_23 - mat[2].p[2] * det2_34_03 + mat[2].p[3] * det2_34_02
        val det3_234_024 = mat[2].p[0] * det2_34_24 - mat[2].p[2] * det2_34_04 + mat[2].p[4] * det2_34_02
        val det3_234_025 = mat[2].p[0] * det2_34_25 - mat[2].p[2] * det2_34_05 + mat[2].p[5] * det2_34_02
        val det3_234_034 = mat[2].p[0] * det2_34_34 - mat[2].p[3] * det2_34_04 + mat[2].p[4] * det2_34_03
        val det3_234_035 = mat[2].p[0] * det2_34_35 - mat[2].p[3] * det2_34_05 + mat[2].p[5] * det2_34_03
        val det3_234_045 = mat[2].p[0] * det2_34_45 - mat[2].p[4] * det2_34_05 + mat[2].p[5] * det2_34_04
        val det3_234_123 = mat[2].p[1] * det2_34_23 - mat[2].p[2] * det2_34_13 + mat[2].p[3] * det2_34_12
        val det3_234_124 = mat[2].p[1] * det2_34_24 - mat[2].p[2] * det2_34_14 + mat[2].p[4] * det2_34_12
        val det3_234_125 = mat[2].p[1] * det2_34_25 - mat[2].p[2] * det2_34_15 + mat[2].p[5] * det2_34_12
        val det3_234_134 = mat[2].p[1] * det2_34_34 - mat[2].p[3] * det2_34_14 + mat[2].p[4] * det2_34_13
        val det3_234_135 = mat[2].p[1] * det2_34_35 - mat[2].p[3] * det2_34_15 + mat[2].p[5] * det2_34_13
        val det3_234_145 = mat[2].p[1] * det2_34_45 - mat[2].p[4] * det2_34_15 + mat[2].p[5] * det2_34_14
        val det3_234_234 = mat[2].p[2] * det2_34_34 - mat[2].p[3] * det2_34_24 + mat[2].p[4] * det2_34_23
        val det3_234_235 = mat[2].p[2] * det2_34_35 - mat[2].p[3] * det2_34_25 + mat[2].p[5] * det2_34_23
        val det3_234_245 = mat[2].p[2] * det2_34_45 - mat[2].p[4] * det2_34_25 + mat[2].p[5] * det2_34_24
        val det3_234_345 = mat[2].p[3] * det2_34_45 - mat[2].p[4] * det2_34_35 + mat[2].p[5] * det2_34_34
        val det3_235_012 = mat[2].p[0] * det2_35_12 - mat[2].p[1] * det2_35_02 + mat[2].p[2] * det2_35_01
        val det3_235_013 = mat[2].p[0] * det2_35_13 - mat[2].p[1] * det2_35_03 + mat[2].p[3] * det2_35_01
        val det3_235_014 = mat[2].p[0] * det2_35_14 - mat[2].p[1] * det2_35_04 + mat[2].p[4] * det2_35_01
        val det3_235_015 = mat[2].p[0] * det2_35_15 - mat[2].p[1] * det2_35_05 + mat[2].p[5] * det2_35_01
        val det3_235_023 = mat[2].p[0] * det2_35_23 - mat[2].p[2] * det2_35_03 + mat[2].p[3] * det2_35_02
        val det3_235_024 = mat[2].p[0] * det2_35_24 - mat[2].p[2] * det2_35_04 + mat[2].p[4] * det2_35_02
        val det3_235_025 = mat[2].p[0] * det2_35_25 - mat[2].p[2] * det2_35_05 + mat[2].p[5] * det2_35_02
        val det3_235_034 = mat[2].p[0] * det2_35_34 - mat[2].p[3] * det2_35_04 + mat[2].p[4] * det2_35_03
        val det3_235_035 = mat[2].p[0] * det2_35_35 - mat[2].p[3] * det2_35_05 + mat[2].p[5] * det2_35_03
        val det3_235_045 = mat[2].p[0] * det2_35_45 - mat[2].p[4] * det2_35_05 + mat[2].p[5] * det2_35_04
        val det3_235_123 = mat[2].p[1] * det2_35_23 - mat[2].p[2] * det2_35_13 + mat[2].p[3] * det2_35_12
        val det3_235_124 = mat[2].p[1] * det2_35_24 - mat[2].p[2] * det2_35_14 + mat[2].p[4] * det2_35_12
        val det3_235_125 = mat[2].p[1] * det2_35_25 - mat[2].p[2] * det2_35_15 + mat[2].p[5] * det2_35_12
        val det3_235_134 = mat[2].p[1] * det2_35_34 - mat[2].p[3] * det2_35_14 + mat[2].p[4] * det2_35_13
        val det3_235_135 = mat[2].p[1] * det2_35_35 - mat[2].p[3] * det2_35_15 + mat[2].p[5] * det2_35_13
        val det3_235_145 = mat[2].p[1] * det2_35_45 - mat[2].p[4] * det2_35_15 + mat[2].p[5] * det2_35_14
        val det3_235_234 = mat[2].p[2] * det2_35_34 - mat[2].p[3] * det2_35_24 + mat[2].p[4] * det2_35_23
        val det3_235_235 = mat[2].p[2] * det2_35_35 - mat[2].p[3] * det2_35_25 + mat[2].p[5] * det2_35_23
        val det3_235_245 = mat[2].p[2] * det2_35_45 - mat[2].p[4] * det2_35_25 + mat[2].p[5] * det2_35_24
        val det3_235_345 = mat[2].p[3] * det2_35_45 - mat[2].p[4] * det2_35_35 + mat[2].p[5] * det2_35_34
        val det3_245_012 = mat[2].p[0] * det2_45_12 - mat[2].p[1] * det2_45_02 + mat[2].p[2] * det2_45_01
        val det3_245_013 = mat[2].p[0] * det2_45_13 - mat[2].p[1] * det2_45_03 + mat[2].p[3] * det2_45_01
        val det3_245_014 = mat[2].p[0] * det2_45_14 - mat[2].p[1] * det2_45_04 + mat[2].p[4] * det2_45_01
        val det3_245_015 = mat[2].p[0] * det2_45_15 - mat[2].p[1] * det2_45_05 + mat[2].p[5] * det2_45_01
        val det3_245_023 = mat[2].p[0] * det2_45_23 - mat[2].p[2] * det2_45_03 + mat[2].p[3] * det2_45_02
        val det3_245_024 = mat[2].p[0] * det2_45_24 - mat[2].p[2] * det2_45_04 + mat[2].p[4] * det2_45_02
        val det3_245_025 = mat[2].p[0] * det2_45_25 - mat[2].p[2] * det2_45_05 + mat[2].p[5] * det2_45_02
        val det3_245_034 = mat[2].p[0] * det2_45_34 - mat[2].p[3] * det2_45_04 + mat[2].p[4] * det2_45_03
        val det3_245_035 = mat[2].p[0] * det2_45_35 - mat[2].p[3] * det2_45_05 + mat[2].p[5] * det2_45_03
        val det3_245_045 = mat[2].p[0] * det2_45_45 - mat[2].p[4] * det2_45_05 + mat[2].p[5] * det2_45_04
        val det3_245_123 = mat[2].p[1] * det2_45_23 - mat[2].p[2] * det2_45_13 + mat[2].p[3] * det2_45_12
        val det3_245_124 = mat[2].p[1] * det2_45_24 - mat[2].p[2] * det2_45_14 + mat[2].p[4] * det2_45_12
        val det3_245_125 = mat[2].p[1] * det2_45_25 - mat[2].p[2] * det2_45_15 + mat[2].p[5] * det2_45_12
        val det3_245_134 = mat[2].p[1] * det2_45_34 - mat[2].p[3] * det2_45_14 + mat[2].p[4] * det2_45_13
        val det3_245_135 = mat[2].p[1] * det2_45_35 - mat[2].p[3] * det2_45_15 + mat[2].p[5] * det2_45_13
        val det3_245_145 = mat[2].p[1] * det2_45_45 - mat[2].p[4] * det2_45_15 + mat[2].p[5] * det2_45_14
        val det3_245_234 = mat[2].p[2] * det2_45_34 - mat[2].p[3] * det2_45_24 + mat[2].p[4] * det2_45_23
        val det3_245_235 = mat[2].p[2] * det2_45_35 - mat[2].p[3] * det2_45_25 + mat[2].p[5] * det2_45_23
        val det3_245_245 = mat[2].p[2] * det2_45_45 - mat[2].p[4] * det2_45_25 + mat[2].p[5] * det2_45_24
        val det3_245_345 = mat[2].p[3] * det2_45_45 - mat[2].p[4] * det2_45_35 + mat[2].p[5] * det2_45_34

        // remaining 4x4 sub-determinants
        val det4_1234_0123 =
            mat[1].p[0] * det3_234_123 - mat[1].p[1] * det3_234_023 + mat[1].p[2] * det3_234_013 - mat[1].p[3] * det3_234_012
        val det4_1234_0124 =
            mat[1].p[0] * det3_234_124 - mat[1].p[1] * det3_234_024 + mat[1].p[2] * det3_234_014 - mat[1].p[4] * det3_234_012
        val det4_1234_0125 =
            mat[1].p[0] * det3_234_125 - mat[1].p[1] * det3_234_025 + mat[1].p[2] * det3_234_015 - mat[1].p[5] * det3_234_012
        val det4_1234_0134 =
            mat[1].p[0] * det3_234_134 - mat[1].p[1] * det3_234_034 + mat[1].p[3] * det3_234_014 - mat[1].p[4] * det3_234_013
        val det4_1234_0135 =
            mat[1].p[0] * det3_234_135 - mat[1].p[1] * det3_234_035 + mat[1].p[3] * det3_234_015 - mat[1].p[5] * det3_234_013
        val det4_1234_0145 =
            mat[1].p[0] * det3_234_145 - mat[1].p[1] * det3_234_045 + mat[1].p[4] * det3_234_015 - mat[1].p[5] * det3_234_014
        val det4_1234_0234 =
            mat[1].p[0] * det3_234_234 - mat[1].p[2] * det3_234_034 + mat[1].p[3] * det3_234_024 - mat[1].p[4] * det3_234_023
        val det4_1234_0235 =
            mat[1].p[0] * det3_234_235 - mat[1].p[2] * det3_234_035 + mat[1].p[3] * det3_234_025 - mat[1].p[5] * det3_234_023
        val det4_1234_0245 =
            mat[1].p[0] * det3_234_245 - mat[1].p[2] * det3_234_045 + mat[1].p[4] * det3_234_025 - mat[1].p[5] * det3_234_024
        val det4_1234_0345 =
            mat[1].p[0] * det3_234_345 - mat[1].p[3] * det3_234_045 + mat[1].p[4] * det3_234_035 - mat[1].p[5] * det3_234_034
        val det4_1234_1234 =
            mat[1].p[1] * det3_234_234 - mat[1].p[2] * det3_234_134 + mat[1].p[3] * det3_234_124 - mat[1].p[4] * det3_234_123
        val det4_1234_1235 =
            mat[1].p[1] * det3_234_235 - mat[1].p[2] * det3_234_135 + mat[1].p[3] * det3_234_125 - mat[1].p[5] * det3_234_123
        val det4_1234_1245 =
            mat[1].p[1] * det3_234_245 - mat[1].p[2] * det3_234_145 + mat[1].p[4] * det3_234_125 - mat[1].p[5] * det3_234_124
        val det4_1234_1345 =
            mat[1].p[1] * det3_234_345 - mat[1].p[3] * det3_234_145 + mat[1].p[4] * det3_234_135 - mat[1].p[5] * det3_234_134
        val det4_1234_2345 =
            mat[1].p[2] * det3_234_345 - mat[1].p[3] * det3_234_245 + mat[1].p[4] * det3_234_235 - mat[1].p[5] * det3_234_234
        val det4_1235_0123 =
            mat[1].p[0] * det3_235_123 - mat[1].p[1] * det3_235_023 + mat[1].p[2] * det3_235_013 - mat[1].p[3] * det3_235_012
        val det4_1235_0124 =
            mat[1].p[0] * det3_235_124 - mat[1].p[1] * det3_235_024 + mat[1].p[2] * det3_235_014 - mat[1].p[4] * det3_235_012
        val det4_1235_0125 =
            mat[1].p[0] * det3_235_125 - mat[1].p[1] * det3_235_025 + mat[1].p[2] * det3_235_015 - mat[1].p[5] * det3_235_012
        val det4_1235_0134 =
            mat[1].p[0] * det3_235_134 - mat[1].p[1] * det3_235_034 + mat[1].p[3] * det3_235_014 - mat[1].p[4] * det3_235_013
        val det4_1235_0135 =
            mat[1].p[0] * det3_235_135 - mat[1].p[1] * det3_235_035 + mat[1].p[3] * det3_235_015 - mat[1].p[5] * det3_235_013
        val det4_1235_0145 =
            mat[1].p[0] * det3_235_145 - mat[1].p[1] * det3_235_045 + mat[1].p[4] * det3_235_015 - mat[1].p[5] * det3_235_014
        val det4_1235_0234 =
            mat[1].p[0] * det3_235_234 - mat[1].p[2] * det3_235_034 + mat[1].p[3] * det3_235_024 - mat[1].p[4] * det3_235_023
        val det4_1235_0235 =
            mat[1].p[0] * det3_235_235 - mat[1].p[2] * det3_235_035 + mat[1].p[3] * det3_235_025 - mat[1].p[5] * det3_235_023
        val det4_1235_0245 =
            mat[1].p[0] * det3_235_245 - mat[1].p[2] * det3_235_045 + mat[1].p[4] * det3_235_025 - mat[1].p[5] * det3_235_024
        val det4_1235_0345 =
            mat[1].p[0] * det3_235_345 - mat[1].p[3] * det3_235_045 + mat[1].p[4] * det3_235_035 - mat[1].p[5] * det3_235_034
        val det4_1235_1234 =
            mat[1].p[1] * det3_235_234 - mat[1].p[2] * det3_235_134 + mat[1].p[3] * det3_235_124 - mat[1].p[4] * det3_235_123
        val det4_1235_1235 =
            mat[1].p[1] * det3_235_235 - mat[1].p[2] * det3_235_135 + mat[1].p[3] * det3_235_125 - mat[1].p[5] * det3_235_123
        val det4_1235_1245 =
            mat[1].p[1] * det3_235_245 - mat[1].p[2] * det3_235_145 + mat[1].p[4] * det3_235_125 - mat[1].p[5] * det3_235_124
        val det4_1235_1345 =
            mat[1].p[1] * det3_235_345 - mat[1].p[3] * det3_235_145 + mat[1].p[4] * det3_235_135 - mat[1].p[5] * det3_235_134
        val det4_1235_2345 =
            mat[1].p[2] * det3_235_345 - mat[1].p[3] * det3_235_245 + mat[1].p[4] * det3_235_235 - mat[1].p[5] * det3_235_234
        val det4_1245_0123 =
            mat[1].p[0] * det3_245_123 - mat[1].p[1] * det3_245_023 + mat[1].p[2] * det3_245_013 - mat[1].p[3] * det3_245_012
        val det4_1245_0124 =
            mat[1].p[0] * det3_245_124 - mat[1].p[1] * det3_245_024 + mat[1].p[2] * det3_245_014 - mat[1].p[4] * det3_245_012
        val det4_1245_0125 =
            mat[1].p[0] * det3_245_125 - mat[1].p[1] * det3_245_025 + mat[1].p[2] * det3_245_015 - mat[1].p[5] * det3_245_012
        val det4_1245_0134 =
            mat[1].p[0] * det3_245_134 - mat[1].p[1] * det3_245_034 + mat[1].p[3] * det3_245_014 - mat[1].p[4] * det3_245_013
        val det4_1245_0135 =
            mat[1].p[0] * det3_245_135 - mat[1].p[1] * det3_245_035 + mat[1].p[3] * det3_245_015 - mat[1].p[5] * det3_245_013
        val det4_1245_0145 =
            mat[1].p[0] * det3_245_145 - mat[1].p[1] * det3_245_045 + mat[1].p[4] * det3_245_015 - mat[1].p[5] * det3_245_014
        val det4_1245_0234 =
            mat[1].p[0] * det3_245_234 - mat[1].p[2] * det3_245_034 + mat[1].p[3] * det3_245_024 - mat[1].p[4] * det3_245_023
        val det4_1245_0235 =
            mat[1].p[0] * det3_245_235 - mat[1].p[2] * det3_245_035 + mat[1].p[3] * det3_245_025 - mat[1].p[5] * det3_245_023
        val det4_1245_0245 =
            mat[1].p[0] * det3_245_245 - mat[1].p[2] * det3_245_045 + mat[1].p[4] * det3_245_025 - mat[1].p[5] * det3_245_024
        val det4_1245_0345 =
            mat[1].p[0] * det3_245_345 - mat[1].p[3] * det3_245_045 + mat[1].p[4] * det3_245_035 - mat[1].p[5] * det3_245_034
        val det4_1245_1234 =
            mat[1].p[1] * det3_245_234 - mat[1].p[2] * det3_245_134 + mat[1].p[3] * det3_245_124 - mat[1].p[4] * det3_245_123
        val det4_1245_1235 =
            mat[1].p[1] * det3_245_235 - mat[1].p[2] * det3_245_135 + mat[1].p[3] * det3_245_125 - mat[1].p[5] * det3_245_123
        val det4_1245_1245 =
            mat[1].p[1] * det3_245_245 - mat[1].p[2] * det3_245_145 + mat[1].p[4] * det3_245_125 - mat[1].p[5] * det3_245_124
        val det4_1245_1345 =
            mat[1].p[1] * det3_245_345 - mat[1].p[3] * det3_245_145 + mat[1].p[4] * det3_245_135 - mat[1].p[5] * det3_245_134
        val det4_1245_2345 =
            mat[1].p[2] * det3_245_345 - mat[1].p[3] * det3_245_245 + mat[1].p[4] * det3_245_235 - mat[1].p[5] * det3_245_234
        val det4_1345_0123 =
            mat[1].p[0] * det3_345_123 - mat[1].p[1] * det3_345_023 + mat[1].p[2] * det3_345_013 - mat[1].p[3] * det3_345_012
        val det4_1345_0124 =
            mat[1].p[0] * det3_345_124 - mat[1].p[1] * det3_345_024 + mat[1].p[2] * det3_345_014 - mat[1].p[4] * det3_345_012
        val det4_1345_0125 =
            mat[1].p[0] * det3_345_125 - mat[1].p[1] * det3_345_025 + mat[1].p[2] * det3_345_015 - mat[1].p[5] * det3_345_012
        val det4_1345_0134 =
            mat[1].p[0] * det3_345_134 - mat[1].p[1] * det3_345_034 + mat[1].p[3] * det3_345_014 - mat[1].p[4] * det3_345_013
        val det4_1345_0135 =
            mat[1].p[0] * det3_345_135 - mat[1].p[1] * det3_345_035 + mat[1].p[3] * det3_345_015 - mat[1].p[5] * det3_345_013
        val det4_1345_0145 =
            mat[1].p[0] * det3_345_145 - mat[1].p[1] * det3_345_045 + mat[1].p[4] * det3_345_015 - mat[1].p[5] * det3_345_014
        val det4_1345_0234 =
            mat[1].p[0] * det3_345_234 - mat[1].p[2] * det3_345_034 + mat[1].p[3] * det3_345_024 - mat[1].p[4] * det3_345_023
        val det4_1345_0235 =
            mat[1].p[0] * det3_345_235 - mat[1].p[2] * det3_345_035 + mat[1].p[3] * det3_345_025 - mat[1].p[5] * det3_345_023
        val det4_1345_0245 =
            mat[1].p[0] * det3_345_245 - mat[1].p[2] * det3_345_045 + mat[1].p[4] * det3_345_025 - mat[1].p[5] * det3_345_024
        val det4_1345_0345 =
            mat[1].p[0] * det3_345_345 - mat[1].p[3] * det3_345_045 + mat[1].p[4] * det3_345_035 - mat[1].p[5] * det3_345_034
        val det4_1345_1234 =
            mat[1].p[1] * det3_345_234 - mat[1].p[2] * det3_345_134 + mat[1].p[3] * det3_345_124 - mat[1].p[4] * det3_345_123
        val det4_1345_1235 =
            mat[1].p[1] * det3_345_235 - mat[1].p[2] * det3_345_135 + mat[1].p[3] * det3_345_125 - mat[1].p[5] * det3_345_123
        val det4_1345_1245 =
            mat[1].p[1] * det3_345_245 - mat[1].p[2] * det3_345_145 + mat[1].p[4] * det3_345_125 - mat[1].p[5] * det3_345_124
        val det4_1345_1345 =
            mat[1].p[1] * det3_345_345 - mat[1].p[3] * det3_345_145 + mat[1].p[4] * det3_345_135 - mat[1].p[5] * det3_345_134
        val det4_1345_2345 =
            mat[1].p[2] * det3_345_345 - mat[1].p[3] * det3_345_245 + mat[1].p[4] * det3_345_235 - mat[1].p[5] * det3_345_234

        // remaining 5x5 sub-determinants
        val det5_01234_01234 =
            mat[0].p[0] * det4_1234_1234 - mat[0].p[1] * det4_1234_0234 + mat[0].p[2] * det4_1234_0134 - mat[0].p[3] * det4_1234_0124 + mat[0].p[4] * det4_1234_0123
        val det5_01234_01235 =
            mat[0].p[0] * det4_1234_1235 - mat[0].p[1] * det4_1234_0235 + mat[0].p[2] * det4_1234_0135 - mat[0].p[3] * det4_1234_0125 + mat[0].p[5] * det4_1234_0123
        val det5_01234_01245 =
            mat[0].p[0] * det4_1234_1245 - mat[0].p[1] * det4_1234_0245 + mat[0].p[2] * det4_1234_0145 - mat[0].p[4] * det4_1234_0125 + mat[0].p[5] * det4_1234_0124
        val det5_01234_01345 =
            mat[0].p[0] * det4_1234_1345 - mat[0].p[1] * det4_1234_0345 + mat[0].p[3] * det4_1234_0145 - mat[0].p[4] * det4_1234_0135 + mat[0].p[5] * det4_1234_0134
        val det5_01234_02345 =
            mat[0].p[0] * det4_1234_2345 - mat[0].p[2] * det4_1234_0345 + mat[0].p[3] * det4_1234_0245 - mat[0].p[4] * det4_1234_0235 + mat[0].p[5] * det4_1234_0234
        val det5_01234_12345 =
            mat[0].p[1] * det4_1234_2345 - mat[0].p[2] * det4_1234_1345 + mat[0].p[3] * det4_1234_1245 - mat[0].p[4] * det4_1234_1235 + mat[0].p[5] * det4_1234_1234
        val det5_01235_01234 =
            mat[0].p[0] * det4_1235_1234 - mat[0].p[1] * det4_1235_0234 + mat[0].p[2] * det4_1235_0134 - mat[0].p[3] * det4_1235_0124 + mat[0].p[4] * det4_1235_0123
        val det5_01235_01235 =
            mat[0].p[0] * det4_1235_1235 - mat[0].p[1] * det4_1235_0235 + mat[0].p[2] * det4_1235_0135 - mat[0].p[3] * det4_1235_0125 + mat[0].p[5] * det4_1235_0123
        val det5_01235_01245 =
            mat[0].p[0] * det4_1235_1245 - mat[0].p[1] * det4_1235_0245 + mat[0].p[2] * det4_1235_0145 - mat[0].p[4] * det4_1235_0125 + mat[0].p[5] * det4_1235_0124
        val det5_01235_01345 =
            mat[0].p[0] * det4_1235_1345 - mat[0].p[1] * det4_1235_0345 + mat[0].p[3] * det4_1235_0145 - mat[0].p[4] * det4_1235_0135 + mat[0].p[5] * det4_1235_0134
        val det5_01235_02345 =
            mat[0].p[0] * det4_1235_2345 - mat[0].p[2] * det4_1235_0345 + mat[0].p[3] * det4_1235_0245 - mat[0].p[4] * det4_1235_0235 + mat[0].p[5] * det4_1235_0234
        val det5_01235_12345 =
            mat[0].p[1] * det4_1235_2345 - mat[0].p[2] * det4_1235_1345 + mat[0].p[3] * det4_1235_1245 - mat[0].p[4] * det4_1235_1235 + mat[0].p[5] * det4_1235_1234
        val det5_01245_01234 =
            mat[0].p[0] * det4_1245_1234 - mat[0].p[1] * det4_1245_0234 + mat[0].p[2] * det4_1245_0134 - mat[0].p[3] * det4_1245_0124 + mat[0].p[4] * det4_1245_0123
        val det5_01245_01235 =
            mat[0].p[0] * det4_1245_1235 - mat[0].p[1] * det4_1245_0235 + mat[0].p[2] * det4_1245_0135 - mat[0].p[3] * det4_1245_0125 + mat[0].p[5] * det4_1245_0123
        val det5_01245_01245 =
            mat[0].p[0] * det4_1245_1245 - mat[0].p[1] * det4_1245_0245 + mat[0].p[2] * det4_1245_0145 - mat[0].p[4] * det4_1245_0125 + mat[0].p[5] * det4_1245_0124
        val det5_01245_01345 =
            mat[0].p[0] * det4_1245_1345 - mat[0].p[1] * det4_1245_0345 + mat[0].p[3] * det4_1245_0145 - mat[0].p[4] * det4_1245_0135 + mat[0].p[5] * det4_1245_0134
        val det5_01245_02345 =
            mat[0].p[0] * det4_1245_2345 - mat[0].p[2] * det4_1245_0345 + mat[0].p[3] * det4_1245_0245 - mat[0].p[4] * det4_1245_0235 + mat[0].p[5] * det4_1245_0234
        val det5_01245_12345 =
            mat[0].p[1] * det4_1245_2345 - mat[0].p[2] * det4_1245_1345 + mat[0].p[3] * det4_1245_1245 - mat[0].p[4] * det4_1245_1235 + mat[0].p[5] * det4_1245_1234
        val det5_01345_01234 =
            mat[0].p[0] * det4_1345_1234 - mat[0].p[1] * det4_1345_0234 + mat[0].p[2] * det4_1345_0134 - mat[0].p[3] * det4_1345_0124 + mat[0].p[4] * det4_1345_0123
        val det5_01345_01235 =
            mat[0].p[0] * det4_1345_1235 - mat[0].p[1] * det4_1345_0235 + mat[0].p[2] * det4_1345_0135 - mat[0].p[3] * det4_1345_0125 + mat[0].p[5] * det4_1345_0123
        val det5_01345_01245 =
            mat[0].p[0] * det4_1345_1245 - mat[0].p[1] * det4_1345_0245 + mat[0].p[2] * det4_1345_0145 - mat[0].p[4] * det4_1345_0125 + mat[0].p[5] * det4_1345_0124
        val det5_01345_01345 =
            mat[0].p[0] * det4_1345_1345 - mat[0].p[1] * det4_1345_0345 + mat[0].p[3] * det4_1345_0145 - mat[0].p[4] * det4_1345_0135 + mat[0].p[5] * det4_1345_0134
        val det5_01345_02345 =
            mat[0].p[0] * det4_1345_2345 - mat[0].p[2] * det4_1345_0345 + mat[0].p[3] * det4_1345_0245 - mat[0].p[4] * det4_1345_0235 + mat[0].p[5] * det4_1345_0234
        val det5_01345_12345 =
            mat[0].p[1] * det4_1345_2345 - mat[0].p[2] * det4_1345_1345 + mat[0].p[3] * det4_1345_1245 - mat[0].p[4] * det4_1345_1235 + mat[0].p[5] * det4_1345_1234
        val det5_02345_01234 =
            mat[0].p[0] * det4_2345_1234 - mat[0].p[1] * det4_2345_0234 + mat[0].p[2] * det4_2345_0134 - mat[0].p[3] * det4_2345_0124 + mat[0].p[4] * det4_2345_0123
        val det5_02345_01235 =
            mat[0].p[0] * det4_2345_1235 - mat[0].p[1] * det4_2345_0235 + mat[0].p[2] * det4_2345_0135 - mat[0].p[3] * det4_2345_0125 + mat[0].p[5] * det4_2345_0123
        val det5_02345_01245 =
            mat[0].p[0] * det4_2345_1245 - mat[0].p[1] * det4_2345_0245 + mat[0].p[2] * det4_2345_0145 - mat[0].p[4] * det4_2345_0125 + mat[0].p[5] * det4_2345_0124
        val det5_02345_01345 =
            mat[0].p[0] * det4_2345_1345 - mat[0].p[1] * det4_2345_0345 + mat[0].p[3] * det4_2345_0145 - mat[0].p[4] * det4_2345_0135 + mat[0].p[5] * det4_2345_0134
        val det5_02345_02345 =
            mat[0].p[0] * det4_2345_2345 - mat[0].p[2] * det4_2345_0345 + mat[0].p[3] * det4_2345_0245 - mat[0].p[4] * det4_2345_0235 + mat[0].p[5] * det4_2345_0234
        val det5_02345_12345 =
            mat[0].p[1] * det4_2345_2345 - mat[0].p[2] * det4_2345_1345 + mat[0].p[3] * det4_2345_1245 - mat[0].p[4] * det4_2345_1235 + mat[0].p[5] * det4_2345_1234
        mat[0].p[0] = (det5_12345_12345 * invDet)
        mat[0].p[1] = (-det5_02345_12345 * invDet)
        mat[0].p[2] = (det5_01345_12345 * invDet)
        mat[0].p[3] = (-det5_01245_12345 * invDet)
        mat[0].p[4] = (det5_01235_12345 * invDet)
        mat[0].p[5] = (-det5_01234_12345 * invDet)
        mat[1].p[0] = (-det5_12345_02345 * invDet)
        mat[1].p[1] = (det5_02345_02345 * invDet)
        mat[1].p[2] = (-det5_01345_02345 * invDet)
        mat[1].p[3] = (det5_01245_02345 * invDet)
        mat[1].p[4] = (-det5_01235_02345 * invDet)
        mat[1].p[5] = (det5_01234_02345 * invDet)
        mat[2].p[0] = (det5_12345_01345 * invDet)
        mat[2].p[1] = (-det5_02345_01345 * invDet)
        mat[2].p[2] = (det5_01345_01345 * invDet)
        mat[2].p[3] = (-det5_01245_01345 * invDet)
        mat[2].p[4] = (det5_01235_01345 * invDet)
        mat[2].p[5] = (-det5_01234_01345 * invDet)
        mat[3].p[0] = (-det5_12345_01245 * invDet)
        mat[3].p[1] = (det5_02345_01245 * invDet)
        mat[3].p[2] = (-det5_01345_01245 * invDet)
        mat[3].p[3] = (det5_01245_01245 * invDet)
        mat[3].p[4] = (-det5_01235_01245 * invDet)
        mat[3].p[5] = (det5_01234_01245 * invDet)
        mat[4].p[0] = (det5_12345_01235 * invDet)
        mat[4].p[1] = (-det5_02345_01235 * invDet)
        mat[4].p[2] = (det5_01345_01235 * invDet)
        mat[4].p[3] = (-det5_01245_01235 * invDet)
        mat[4].p[4] = (det5_01235_01235 * invDet)
        mat[4].p[5] = (-det5_01234_01235 * invDet)
        mat[5].p[0] = (-det5_12345_01234 * invDet)
        mat[5].p[1] = (det5_02345_01234 * invDet)
        mat[5].p[2] = (-det5_01345_01234 * invDet)
        mat[5].p[3] = (det5_01245_01234 * invDet)
        mat[5].p[4] = (-det5_01235_01234 * invDet)
        mat[5].p[5] = (det5_01234_01234 * invDet)
        return true
    }

    fun InverseFast(): idMat6 { // returns the inverse ( m * m.Inverse() = identity )
        val invMat = idMat6(this)
        val r = invMat.InverseFastSelf()
        assert(r)
        return invMat
    }

    fun InverseFastSelf(): Boolean { // returns false if determinant is zero
        val r0: Array<idVec3> = idVec3.generateArray(3)
        val r1: Array<idVec3> = idVec3.generateArray(3)
        val r2: Array<idVec3> = idVec3.generateArray(3)
        val r3: Array<idVec3> = idVec3.generateArray(3)
        val c0: Float
        val c1: Float
        val c2: Float
        var det: Float
        var invDet: Float
        val mat = reinterpret_cast()

        // r0 = m0.Inverse();
        c0 = mat[1 * 6 + 1] * mat[2 * 6 + 2] - mat[1 * 6 + 2] * mat[2 * 6 + 1]
        c1 = mat[1 * 6 + 2] * mat[2 * 6 + 0] - mat[1 * 6 + 0] * mat[2 * 6 + 2]
        c2 = mat[1 * 6 + 0] * mat[2 * 6 + 1] - mat[1 * 6 + 1] * mat[2 * 6 + 0]
        det = mat[0 * 6 + 0] * c0 + mat[0 * 6 + 1] * c1 + mat[0 * 6 + 2] * c2
        if (abs(det) < MATRIX_INVERSE_EPSILON) {
            return false
        }
        invDet = 1.0f / det
        r0[0].x = c0 * invDet
        r0[0].y = (mat[0 * 6 + 2] * mat[2 * 6 + 1] - mat[0 * 6 + 1] * mat[2 * 6 + 2]) * invDet
        r0[0].z = (mat[0 * 6 + 1] * mat[1 * 6 + 2] - mat[0 * 6 + 2] * mat[1 * 6 + 1]) * invDet
        r0[1].x = c1 * invDet
        r0[1].y = (mat[0 * 6 + 0] * mat[2 * 6 + 2] - mat[0 * 6 + 2] * mat[2 * 6 + 0]) * invDet
        r0[1].z = (mat[0 * 6 + 2] * mat[1 * 6 + 0] - mat[0 * 6 + 0] * mat[1 * 6 + 2]) * invDet
        r0[2].x = c2 * invDet
        r0[2].y = (mat[0 * 6 + 1] * mat[2 * 6 + 0] - mat[0 * 6 + 0] * mat[2 * 6 + 1]) * invDet
        r0[2].z = (mat[0 * 6 + 0] * mat[1 * 6 + 1] - mat[0 * 6 + 1] * mat[1 * 6 + 0]) * invDet

        // r1 = r0 * m1;
        r1[0].x = r0[0].x * mat[0 * 6 + 3] + r0[0].y * mat[1 * 6 + 3] + r0[0].z * mat[2 * 6 + 3]
        r1[0].y = r0[0].x * mat[0 * 6 + 4] + r0[0].y * mat[1 * 6 + 4] + r0[0].z * mat[2 * 6 + 4]
        r1[0].z = r0[0].x * mat[0 * 6 + 5] + r0[0].y * mat[1 * 6 + 5] + r0[0].z * mat[2 * 6 + 5]
        r1[1].x = r0[1].x * mat[0 * 6 + 3] + r0[1].y * mat[1 * 6 + 3] + r0[1].z * mat[2 * 6 + 3]
        r1[1].y = r0[1].x * mat[0 * 6 + 4] + r0[1].y * mat[1 * 6 + 4] + r0[1].z * mat[2 * 6 + 4]
        r1[1].z = r0[1].x * mat[0 * 6 + 5] + r0[1].y * mat[1 * 6 + 5] + r0[1].z * mat[2 * 6 + 5]
        r1[2].x = r0[2].x * mat[0 * 6 + 3] + r0[2].y * mat[1 * 6 + 3] + r0[2].z * mat[2 * 6 + 3]
        r1[2].y = r0[2].x * mat[0 * 6 + 4] + r0[2].y * mat[1 * 6 + 4] + r0[2].z * mat[2 * 6 + 4]
        r1[2].z = r0[2].x * mat[0 * 6 + 5] + r0[2].y * mat[1 * 6 + 5] + r0[2].z * mat[2 * 6 + 5]

        // r2 = m2 * r1;
        r2[0].x = mat[3 * 6 + 0] * r1[0].x + mat[3 * 6 + 1] * r1[1].x + mat[3 * 6 + 2] * r1[2].x
        r2[0].y = mat[3 * 6 + 0] * r1[0].y + mat[3 * 6 + 1] * r1[1].y + mat[3 * 6 + 2] * r1[2].y
        r2[0].z = mat[3 * 6 + 0] * r1[0].z + mat[3 * 6 + 1] * r1[1].z + mat[3 * 6 + 2] * r1[2].z
        r2[1].x = mat[4 * 6 + 0] * r1[0].x + mat[4 * 6 + 1] * r1[1].x + mat[4 * 6 + 2] * r1[2].x
        r2[1].y = mat[4 * 6 + 0] * r1[0].y + mat[4 * 6 + 1] * r1[1].y + mat[4 * 6 + 2] * r1[2].y
        r2[1].z = mat[4 * 6 + 0] * r1[0].z + mat[4 * 6 + 1] * r1[1].z + mat[4 * 6 + 2] * r1[2].z
        r2[2].x = mat[5 * 6 + 0] * r1[0].x + mat[5 * 6 + 1] * r1[1].x + mat[5 * 6 + 2] * r1[2].x
        r2[2].y = mat[5 * 6 + 0] * r1[0].y + mat[5 * 6 + 1] * r1[1].y + mat[5 * 6 + 2] * r1[2].y
        r2[2].z = mat[5 * 6 + 0] * r1[0].z + mat[5 * 6 + 1] * r1[1].z + mat[5 * 6 + 2] * r1[2].z

        // r3 = r2 - m3;
        r3[0].x = r2[0].x - mat[3 * 6 + 3]
        r3[0].y = r2[0].y - mat[3 * 6 + 4]
        r3[0].z = r2[0].z - mat[3 * 6 + 5]
        r3[1].x = r2[1].x - mat[4 * 6 + 3]
        r3[1].y = r2[1].y - mat[4 * 6 + 4]
        r3[1].z = r2[1].z - mat[4 * 6 + 5]
        r3[2].x = r2[2].x - mat[5 * 6 + 3]
        r3[2].y = r2[2].y - mat[5 * 6 + 4]
        r3[2].z = r2[2].z - mat[5 * 6 + 5]

        // r3.InverseSelf();
        r2[0].x = r3[1].y * r3[2].z - r3[1].z * r3[2].y
        r2[1].x = r3[1].z * r3[2].x - r3[1].x * r3[2].z
        r2[2].x = r3[1].x * r3[2].y - r3[1].y * r3[2].x
        det = r3[0].x * r2[0].x + r3[0].y * r2[1].x + r3[0].z * r2[2].x
        if (abs(det) < MATRIX_INVERSE_EPSILON) {
            return false
        }
        invDet = 1.0f / det
        r2[0].y = r3[0].z * r3[2].y - r3[0].y * r3[2].z
        r2[0].z = r3[0].y * r3[1].z - r3[0].z * r3[1].y
        r2[1].y = r3[0].x * r3[2].z - r3[0].z * r3[2].x
        r2[1].z = r3[0].z * r3[1].x - r3[0].x * r3[1].z
        r2[2].y = r3[0].y * r3[2].x - r3[0].x * r3[2].y
        r2[2].z = r3[0].x * r3[1].y - r3[0].y * r3[1].x
        r3[0].x = r2[0].x * invDet
        r3[0].y = r2[0].y * invDet
        r3[0].z = r2[0].z * invDet
        r3[1].x = r2[1].x * invDet
        r3[1].y = r2[1].y * invDet
        r3[1].z = r2[1].z * invDet
        r3[2].x = r2[2].x * invDet
        r3[2].y = r2[2].y * invDet
        r3[2].z = r2[2].z * invDet

        // r2 = m2 * r0;
        r2[0].x = mat[3 * 6 + 0] * r0[0].x + mat[3 * 6 + 1] * r0[1].x + mat[3 * 6 + 2] * r0[2].x
        r2[0].y = mat[3 * 6 + 0] * r0[0].y + mat[3 * 6 + 1] * r0[1].y + mat[3 * 6 + 2] * r0[2].y
        r2[0].z = mat[3 * 6 + 0] * r0[0].z + mat[3 * 6 + 1] * r0[1].z + mat[3 * 6 + 2] * r0[2].z
        r2[1].x = mat[4 * 6 + 0] * r0[0].x + mat[4 * 6 + 1] * r0[1].x + mat[4 * 6 + 2] * r0[2].x
        r2[1].y = mat[4 * 6 + 0] * r0[0].y + mat[4 * 6 + 1] * r0[1].y + mat[4 * 6 + 2] * r0[2].y
        r2[1].z = mat[4 * 6 + 0] * r0[0].z + mat[4 * 6 + 1] * r0[1].z + mat[4 * 6 + 2] * r0[2].z
        r2[2].x = mat[5 * 6 + 0] * r0[0].x + mat[5 * 6 + 1] * r0[1].x + mat[5 * 6 + 2] * r0[2].x
        r2[2].y = mat[5 * 6 + 0] * r0[0].y + mat[5 * 6 + 1] * r0[1].y + mat[5 * 6 + 2] * r0[2].y
        r2[2].z = mat[5 * 6 + 0] * r0[0].z + mat[5 * 6 + 1] * r0[1].z + mat[5 * 6 + 2] * r0[2].z

        // m2 = r3 * r2;
        this.mat[3].p[0] = r3[0].x * r2[0].x + r3[0].y * r2[1].x + r3[0].z * r2[2].x
        this.mat[3].p[1] = r3[0].x * r2[0].y + r3[0].y * r2[1].y + r3[0].z * r2[2].y
        this.mat[3].p[2] = r3[0].x * r2[0].z + r3[0].y * r2[1].z + r3[0].z * r2[2].z
        this.mat[4].p[0] = r3[1].x * r2[0].x + r3[1].y * r2[1].x + r3[1].z * r2[2].x
        this.mat[4].p[1] = r3[1].x * r2[0].y + r3[1].y * r2[1].y + r3[1].z * r2[2].y
        this.mat[4].p[2] = r3[1].x * r2[0].z + r3[1].y * r2[1].z + r3[1].z * r2[2].z
        this.mat[5].p[0] = r3[2].x * r2[0].x + r3[2].y * r2[1].x + r3[2].z * r2[2].x
        this.mat[5].p[1] = r3[2].x * r2[0].y + r3[2].y * r2[1].y + r3[2].z * r2[2].y
        this.mat[5].p[2] = r3[2].x * r2[0].z + r3[2].y * r2[1].z + r3[2].z * r2[2].z

        // m0 = r0 - r1 * m2;
        this.mat[0].p[0] =
            r0[0].x - r1[0].x * this.mat[3].p[0] - r1[0].y * this.mat[4].p[0] - r1[0].z * this.mat[5].p[0]
        this.mat[0].p[1] =
            r0[0].y - r1[0].x * this.mat[3].p[1] - r1[0].y * this.mat[4].p[1] - r1[0].z * this.mat[5].p[1]
        this.mat[0].p[2] =
            r0[0].z - r1[0].x * this.mat[3].p[2] - r1[0].y * this.mat[4].p[2] - r1[0].z * this.mat[5].p[2]
        this.mat[1].p[0] =
            r0[1].x - r1[1].x * this.mat[3].p[0] - r1[1].y * this.mat[4].p[0] - r1[1].z * this.mat[5].p[0]
        this.mat[1].p[1] =
            r0[1].y - r1[1].x * this.mat[3].p[1] - r1[1].y * this.mat[4].p[1] - r1[1].z * this.mat[5].p[1]
        this.mat[1].p[2] =
            r0[1].z - r1[1].x * this.mat[3].p[2] - r1[1].y * this.mat[4].p[2] - r1[1].z * this.mat[5].p[2]
        this.mat[2].p[0] =
            r0[2].x - r1[2].x * this.mat[3].p[0] - r1[2].y * this.mat[4].p[0] - r1[2].z * this.mat[5].p[0]
        this.mat[2].p[1] =
            r0[2].y - r1[2].x * this.mat[3].p[1] - r1[2].y * this.mat[4].p[1] - r1[2].z * this.mat[5].p[1]
        this.mat[2].p[2] =
            r0[2].z - r1[2].x * this.mat[3].p[2] - r1[2].y * this.mat[4].p[2] - r1[2].z * this.mat[5].p[2]

        // m1 = r1 * r3;
        this.mat[0].p[3] = r1[0].x * r3[0].x + r1[0].y * r3[1].x + r1[0].z * r3[2].x
        this.mat[0].p[4] = r1[0].x * r3[0].y + r1[0].y * r3[1].y + r1[0].z * r3[2].y
        this.mat[0].p[5] = r1[0].x * r3[0].z + r1[0].y * r3[1].z + r1[0].z * r3[2].z
        this.mat[1].p[3] = r1[1].x * r3[0].x + r1[1].y * r3[1].x + r1[1].z * r3[2].x
        this.mat[1].p[4] = r1[1].x * r3[0].y + r1[1].y * r3[1].y + r1[1].z * r3[2].y
        this.mat[1].p[5] = r1[1].x * r3[0].z + r1[1].y * r3[1].z + r1[1].z * r3[2].z
        this.mat[2].p[3] = r1[2].x * r3[0].x + r1[2].y * r3[1].x + r1[2].z * r3[2].x
        this.mat[2].p[4] = r1[2].x * r3[0].y + r1[2].y * r3[1].y + r1[2].z * r3[2].y
        this.mat[2].p[5] = r1[2].x * r3[0].z + r1[2].y * r3[1].z + r1[2].z * r3[2].z

        // m3 = -r3;
        this.mat[3].p[3] = -r3[0].x
        this.mat[3].p[4] = -r3[0].y
        this.mat[3].p[5] = -r3[0].z
        this.mat[4].p[3] = -r3[1].x
        this.mat[4].p[4] = -r3[1].y
        this.mat[4].p[5] = -r3[1].z
        this.mat[5].p[3] = -r3[2].x
        this.mat[5].p[4] = -r3[2].y
        this.mat[5].p[5] = -r3[2].z
        return true
        //#endif
    }

    fun GetDimension(): Int {
        return 36
    }

    private fun set(mat6: idMat6) {
        mat[0].set(mat6.mat[0])
        mat[1].set(mat6.mat[1])
        mat[2].set(mat6.mat[2])
        mat[3].set(mat6.mat[3])
        mat[4].set(mat6.mat[4])
        mat[5].set(mat6.mat[5])
    }

    fun reinterpret_cast(): FloatArray {
        val size = 6
        val temp = FloatArray(size * size)
        for (x in 0 until size) {
            System.arraycopy(mat[x].p, 0, temp, x * 6 + 0, size)
        }
        return temp
    }

    companion object {
        private val mat6_identity: idMat6 = idMat6(
            idVec6(1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f),
            idVec6(0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 0.0f),
            idVec6(0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f),
            idVec6(0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f),
            idVec6(0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f),
            idVec6(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f)
        )
        private val mat6_zero: idMat6 = idMat6(
            idVec6(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f),
            idVec6(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f),
            idVec6(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f),
            idVec6(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f),
            idVec6(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f),
            idVec6(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f)
        )

        fun getMat6_zero(): idMat6 {
            return idMat6(mat6_zero)
        }

        fun getMat6_identity(): idMat6 {
            return idMat6(mat6_identity)
        }

        fun times(a: Float, mat: idMat6): idMat6 {
            return mat * a
        }

        fun times(vec: idVec6, mat: idMat6): idVec6 {
            return mat * vec
        }

        fun timesAssign(vec: idVec6, mat: idMat6): idVec6 {
            return mat * vec
        }
    }
}
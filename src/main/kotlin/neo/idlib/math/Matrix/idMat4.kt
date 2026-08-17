package neo.idlib.math.Matrix

import neo.idlib.math.idVec3
import neo.idlib.math.idVec4
import kotlin.math.abs

//===============================================================
//
//	idMat4 - 4x4 matrix
//
//===============================================================
class idMat4 {
    private val mat: Array<idVec4> = arrayOf(idVec4(), idVec4(), idVec4(), idVec4())

    constructor()
    constructor(x: idVec4, y: idVec4, z: idVec4, w: idVec4) {
        mat[0].set(x)
        mat[1].set(y)
        mat[2].set(z)
        mat[3].set(w)
    }

    constructor(
        xx: Float,
        xy: Float,
        xz: Float,
        xw: Float,
        yx: Float,
        yy: Float,
        yz: Float,
        yw: Float,
        zx: Float,
        zy: Float,
        zz: Float,
        zw: Float,
        wx: Float,
        wy: Float,
        wz: Float,
        ww: Float
    ) {
        mat[0].x = xx
        mat[0].y = xy
        mat[0].z = xz
        mat[0].w = xw //
        mat[1].x = yx
        mat[1].y = yy
        mat[1].z = yz
        mat[1].w = yw //
        mat[2].x = zx
        mat[2].y = zy
        mat[2].z = zz
        mat[2].w = zw //
        mat[3].x = wx
        mat[3].y = wy
        mat[3].z = wz
        mat[3].w = ww
    }

    constructor(rotation: idMat3, translation: idVec3) { // NOTE: idMat3 is transposed because it is column-major
        mat[0].x = rotation.mat[0].x
        mat[0].y = rotation.mat[1].x
        mat[0].z = rotation.mat[2].x
        mat[0].w = translation.x
        mat[1].x = rotation.mat[0].y
        mat[1].y = rotation.mat[1].y
        mat[1].z = rotation.mat[2].y
        mat[1].w = translation.y
        mat[2].x = rotation.mat[0].z
        mat[2].y = rotation.mat[1].z
        mat[2].z = rotation.mat[2].z
        mat[2].w = translation.z
        mat[3].x = 0.0f
        mat[3].y = 0.0f
        mat[3].z = 0.0f
        mat[3].w = 1.0f
    }

    constructor(src: Array<FloatArray>) { //	memcpy( mat, src, 4 * 4 * sizeof( float ) );
        mat[0].x = src[0][0]
        mat[0].y = src[0][1]
        mat[0].z = src[0][2]
        mat[0].w = src[0][3] //
        mat[1].x = src[1][0]
        mat[1].y = src[1][1]
        mat[1].z = src[1][2]
        mat[1].w = src[1][3] //
        mat[2].x = src[2][0]
        mat[2].y = src[2][1]
        mat[2].z = src[2][2]
        mat[2].w = src[2][3] //
        mat[3].x = src[3][0]
        mat[3].y = src[3][1]
        mat[3].z = src[3][2]
        mat[3].w = src[3][3]
    }

    constructor(m: idMat4) {
        this.set(m)
    }

    //public	idVec3			operator*( const idVec3 &vec ) const;
    //public	const idVec4 &	operator[]( int index ) const;
    operator fun get(index: Int): idVec4 {
        return mat[index]
    }

    //public	idMat4			operator*( const idMat4 &a ) const;
    operator fun set(index: Int, value: idVec4): idVec4 {
        return value.also { mat[index] = it }
    }

    //public	idMat4			operator+( const idMat4 &a ) const;
    operator fun set(index1: Int, index2: Int, value: Float): Float {
        return mat[index1].set(index2, value)
    }

    //public	idMat4			operator-( const idMat4 &a ) const;
    operator fun times(a: Float): idMat4 {
        return idMat4(
            mat[0].x * a,
            mat[0].y * a,
            mat[0].z * a,
            mat[0].w * a,
            mat[1].x * a,
            mat[1].y * a,
            mat[1].z * a,
            mat[1].w * a,
            mat[2].x * a,
            mat[2].y * a,
            mat[2].z * a,
            mat[2].w * a,
            mat[3].x * a,
            mat[3].y * a,
            mat[3].z * a,
            mat[3].w * a
        )
    }

    //public	idMat4 &		operator*=( const float a );
    operator fun times(vec: idVec4): idVec4 {
        return idVec4(
            mat[0].x * vec.x + mat[0].y * vec.y + mat[0].z * vec.z + mat[0].w * vec.w,
            mat[1].x * vec.x + mat[1].y * vec.y + mat[1].z * vec.z + mat[1].w * vec.w,
            mat[2].x * vec.x + mat[2].y * vec.y + mat[2].z * vec.z + mat[2].w * vec.w,
            mat[3].x * vec.x + mat[3].y * vec.y + mat[3].z * vec.z + mat[3].w * vec.w
        )
    }

    //public	idMat4 &		operator*=( const idMat4 &a );
    operator fun times(vec: idVec3): idVec3 {
        val s = mat[3].x * vec.x + mat[3].y * vec.y + mat[3].z * vec.z + mat[3].w
        if (s == 0.0f) {
            return idVec3(0.0f, 0.0f, 0.0f)
        }
        return if (s == 1.0f) {
            idVec3(
                mat[0].x * vec.x + mat[0].y * vec.y + mat[0].z * vec.z + mat[0].w,
                mat[1].x * vec.x + mat[1].y * vec.y + mat[1].z * vec.z + mat[1].w,
                mat[2].x * vec.x + mat[2].y * vec.y + mat[2].z * vec.z + mat[2].w
            )
        } else {
            val invS = 1.0f / s
            idVec3(
                (mat[0].x * vec.x + mat[0].y * vec.y + mat[0].z * vec.z + mat[0].w) * invS,
                (mat[1].x * vec.x + mat[1].y * vec.y + mat[1].z * vec.z + mat[1].w) * invS,
                (mat[2].x * vec.x + mat[2].y * vec.y + mat[2].z * vec.z + mat[2].w) * invS
            )
        }
    }

    //public	idMat4 &		operator+=( const idMat4 &a );
    operator fun times(a: idMat4): idMat4 {
        var i: Int
        var j: Int
        val dst = idMat4()
        i = 0
        while (i < 4) {
            j = 0
            while (j < 4) {
                val value =
                    mat[0] * a.mat[0 * 4 + j] + mat[1] * a.mat[1 * 4 + j] + mat[2] * a.mat[2 * 4 + j] + mat[3] * a.mat[3 * 4 + j]
                dst.setCell(i, j, value)
                j++
            }
            i++
        }
        return dst
    }

    operator fun plus(a: idMat4): idMat4 {
        return idMat4(
            mat[0].x + a.mat[0].x,
            mat[0].y + a.mat[0].y,
            mat[0].z + a.mat[0].z,
            mat[0].w + a.mat[0].w,
            mat[1].x + a.mat[1].x,
            mat[1].y + a.mat[1].y,
            mat[1].z + a.mat[1].z,
            mat[1].w + a.mat[1].w,
            mat[2].x + a.mat[2].x,
            mat[2].y + a.mat[2].y,
            mat[2].z + a.mat[2].z,
            mat[2].w + a.mat[2].w,
            mat[3].x + a.mat[3].x,
            mat[3].y + a.mat[3].y,
            mat[3].z + a.mat[3].z,
            mat[3].w + a.mat[3].w
        )
    }

    operator fun minus(a: idMat4): idMat4 {
        return idMat4(
            mat[0].x - a.mat[0].x,
            mat[0].y - a.mat[0].y,
            mat[0].z - a.mat[0].z,
            mat[0].w - a.mat[0].w,
            mat[1].x - a.mat[1].x,
            mat[1].y - a.mat[1].y,
            mat[1].z - a.mat[1].z,
            mat[1].w - a.mat[1].w,
            mat[2].x - a.mat[2].x,
            mat[2].y - a.mat[2].y,
            mat[2].z - a.mat[2].z,
            mat[2].w - a.mat[2].w,
            mat[3].x - a.mat[3].x,
            mat[3].y - a.mat[3].y,
            mat[3].z - a.mat[3].z,
            mat[3].w - a.mat[3].w
        )
    }

    fun timesAssign(a: Float): idMat4 {
        mat[0].x *= a
        mat[0].y *= a
        mat[0].z *= a
        mat[0].w *= a //
        mat[1].x *= a
        mat[1].y *= a
        mat[1].z *= a
        mat[1].w *= a //
        mat[2].x *= a
        mat[2].y *= a
        mat[2].z *= a
        mat[2].w *= a //
        mat[3].x *= a
        mat[3].y *= a
        mat[3].z *= a
        mat[3].w *= a
        return this
    }

    //public	friend idVec3	operator*( const idVec3 &vec, const idMat4 &mat );
    fun timesAssign(a: idMat4): idMat4 {
        this.set(this * a)
        return this
    }

    //public	friend idVec4 &	operator*=( idVec4 &vec, const idMat4 &mat );
    fun plusAssign(a: idMat4): idMat4 {
        mat[0].x += a.mat[0].x
        mat[0].y += a.mat[0].y
        mat[0].z += a.mat[0].z
        mat[0].w += a.mat[0].w //
        mat[1].x += a.mat[1].x
        mat[1].y += a.mat[1].y
        mat[1].z += a.mat[1].z
        mat[1].w += a.mat[1].w //
        mat[2].x += a.mat[2].x
        mat[2].y += a.mat[2].y
        mat[2].z += a.mat[2].z
        mat[2].w += a.mat[2].w //
        mat[3].x += a.mat[3].x
        mat[3].y += a.mat[3].y
        mat[3].z += a.mat[3].z
        mat[3].w += a.mat[3].w
        return this
    }

    //public	friend idVec3 &	operator*=( idVec3 &vec, const idMat4 &mat );
    //public	idMat4 &		operator-=( const idMat4 &a );
    fun minusAssign(a: idMat4): idMat4 {
        mat[0].x -= a.mat[0].x
        mat[0].y -= a.mat[0].y
        mat[0].z -= a.mat[0].z
        mat[0].w -= a.mat[0].w //
        mat[1].x -= a.mat[1].x
        mat[1].y -= a.mat[1].y
        mat[1].z -= a.mat[1].z
        mat[1].w -= a.mat[1].w //
        mat[2].x -= a.mat[2].x
        mat[2].y -= a.mat[2].y
        mat[2].z -= a.mat[2].z
        mat[2].w -= a.mat[2].w //
        mat[3].x -= a.mat[3].x
        mat[3].y -= a.mat[3].y
        mat[3].z -= a.mat[3].z
        mat[3].w -= a.mat[3].w
        return this
    }

    fun Compare(a: idMat4): Boolean { // exact compare, no epsilon
        var i: Int
        var j: Int
        val ptr1 = mat
        val ptr2 = a.mat
        i = 0
        while (i < 4) {
            j = 0
            while (j < 4) {
                if (ptr1[i][j] != ptr2[i][j]) {
                    return false
                }
                j++
            }
            i++
        }
        return true
    }

    fun Compare(a: idMat4, epsilon: Float): Boolean // compare with epsilon
    {
        var i: Int
        var j: Int
        val ptr1 = mat
        val ptr2 = a.mat
        i = 0
        while (i < 4) {
            j = 0
            while (j < 4) {
                if (abs(ptr1[i][j] - ptr2[i][j]) > epsilon) {
                    return false
                }
                j++
            }
            i++
        }
        return true
    }

    //public	bool			operator==( const idMat4 &a ) const;					// exact compare, no epsilon
    //public	bool			operator!=( const idMat4 &a ) const;					// exact compare, no epsilon
    override fun hashCode(): Int {
        var hash = 3
        hash = 89 * hash + mat.contentDeepHashCode()
        return hash
    }

    override fun equals(obj: Any?): Boolean {
        if (obj == null) {
            return false
        }
        if (javaClass != obj.javaClass) {
            return false
        }
        val other = obj as idMat4
        return Compare(other)
    }

    fun equals(a: idMat4): Boolean { // exact compare, no epsilon
        return Compare(a)
    }

    fun notEquals(a: idMat4): Boolean { // exact compare, no epsilon
        return !Compare(a)
    }

    fun Zero() {
        this.set(getMat4_zero())
    }

    fun Identity() {
        this.set(getMat4_identity())
    }


    fun IsIdentity(epsilon: Float): Boolean {
        return Compare(getMat4_identity(), epsilon)
    }


    fun IsSymmetric(epsilon: Float): Boolean {
        for (i in 1..3) {
            for (j in 0 until i) {
                if (abs(mat[i][j] - mat[j][i]) > epsilon) {
                    return false
                }
            }
        }
        return true
    }


    fun IsDiagonal(epsilon: Float): Boolean {
        for (i in 0..3) {
            for (j in 0..3) {
                if (i != j && abs(mat[i][j]) > epsilon) {
                    return false
                }
            }
        }
        return true
    }

    fun IsRotated(): Boolean {
        return !(mat[0][1] == 0.0f && mat[0][2] == 0.0f && mat[1][0] == 0.0f && mat[1][2] == 0.0f && mat[2][0] == 0.0f && mat[2][1] == 0.0f)
    }

    fun ProjectVector(src: idVec4, dst: idVec4) {
        dst.x = src * mat[0]
        dst.y = src * mat[1]
        dst.z = src * mat[2]
        dst.w = src * mat[3]
    }

    fun UnprojectVector(
        src: idVec4, dst: idVec4
    ) { //	dst = mat[ 0 ] * src.x + mat[ 1 ] * src.y + mat[ 2 ] * src.z + mat[ 3 ] * src.w;
        dst.set(
            mat[0] * src.x + mat[1] * src.y + mat[2] * src.z + mat[3] * src.w
        )
    }

    fun Trace(): Float {
        return mat[0][0] + mat[1][1] + mat[2][2] + mat[3][3]
    }

    fun Determinant(): Float {

        // 2x2 sub-determinants
        val det2_01_01 = mat[0][0] * mat[1][1] - mat[0][1] * mat[1][0]
        val det2_01_02 = mat[0][0] * mat[1][2] - mat[0][2] * mat[1][0]
        val det2_01_03 = mat[0][0] * mat[1][3] - mat[0][3] * mat[1][0]
        val det2_01_12 = mat[0][1] * mat[1][2] - mat[0][2] * mat[1][1]
        val det2_01_13 = mat[0][1] * mat[1][3] - mat[0][3] * mat[1][1]
        val det2_01_23 = mat[0][2] * mat[1][3] - mat[0][3] * mat[1][2]

        // 3x3 sub-determinants
        val det3_201_012 = mat[2][0] * det2_01_12 - mat[2][1] * det2_01_02 + mat[2][2] * det2_01_01
        val det3_201_013 = mat[2][0] * det2_01_13 - mat[2][1] * det2_01_03 + mat[2][3] * det2_01_01
        val det3_201_023 = mat[2][0] * det2_01_23 - mat[2][2] * det2_01_03 + mat[2][3] * det2_01_02
        val det3_201_123 = mat[2][1] * det2_01_23 - mat[2][2] * det2_01_13 + mat[2][3] * det2_01_12
        return -det3_201_123 * mat[3][0] + det3_201_023 * mat[3][1] - det3_201_013 * mat[3][2] + det3_201_012 * mat[3][3]
    }

    fun Transpose(): idMat4 { // returns transpose
        val transpose = idMat4()
        var i: Int
        var j: Int
        i = 0
        while (i < 4) {
            j = 0
            while (j < 4) {
                transpose.mat[i][j] = mat[j][i]
                j++
            }
            i++
        }
        return transpose
    }

    fun TransposeSelf(): idMat4 {
        var temp: Float
        var i: Int
        var j: Int
        i = 0
        while (i < 4) {
            j = i + 1
            while (j < 4) {
                temp = mat[i][j]
                mat[i][j] = mat[j][i]
                mat[j][i] = temp
                j++
            }
            i++
        }
        return this
    }

    fun Inverse(): idMat4 { // returns the inverse ( m * m.Inverse() = identity )
        val invMat = idMat4(this)
        val r = invMat.InverseSelf()
        assert(r)
        return invMat
    }

    fun InverseSelf(): Boolean // returns false if determinant is zero
    { // 84+4+16 = 104 multiplications
        //			   1 division
        val det: Float
        val invDet: Float

        // 2x2 sub-determinants required to calculate 4x4 determinant
        val det2_01_01 = mat[0].x * mat[1].y - mat[0].y * mat[1].x
        val det2_01_02 = mat[0].x * mat[1].z - mat[0].z * mat[1].x
        val det2_01_03 = mat[0].x * mat[1].w - mat[0].w * mat[1].x
        val det2_01_12 = mat[0].y * mat[1].z - mat[0].z * mat[1].y
        val det2_01_13 = mat[0].y * mat[1].w - mat[0].w * mat[1].y
        val det2_01_23 = mat[0].z * mat[1].w - mat[0].w * mat[1].z

        // 3x3 sub-determinants required to calculate 4x4 determinant
        val det3_201_012 = mat[2].x * det2_01_12 - mat[2].y * det2_01_02 + mat[2].z * det2_01_01
        val det3_201_013 = mat[2].x * det2_01_13 - mat[2].y * det2_01_03 + mat[2].w * det2_01_01
        val det3_201_023 = mat[2].x * det2_01_23 - mat[2].z * det2_01_03 + mat[2].w * det2_01_02
        val det3_201_123 = mat[2].y * det2_01_23 - mat[2].z * det2_01_13 + mat[2].w * det2_01_12
        det = -det3_201_123 * mat[3].x + det3_201_023 * mat[3].y - det3_201_013 * mat[3].z + det3_201_012 * mat[3].w
        if (abs(det) < MATRIX_INVERSE_EPSILON) {
            return false
        }
        invDet = 1.0f / det

        // remaining 2x2 sub-determinants
        val det2_03_01 = mat[0].x * mat[3].y - mat[0].y * mat[3].x
        val det2_03_02 = mat[0].x * mat[3].z - mat[0].z * mat[3].x
        val det2_03_03 = mat[0].x * mat[3].w - mat[0].w * mat[3].x
        val det2_03_12 = mat[0].y * mat[3].z - mat[0].z * mat[3].y
        val det2_03_13 = mat[0].y * mat[3].w - mat[0].w * mat[3].y
        val det2_03_23 = mat[0].z * mat[3].w - mat[0].w * mat[3].z
        val det2_13_01 = mat[1].x * mat[3].y - mat[1].y * mat[3].x
        val det2_13_02 = mat[1].x * mat[3].z - mat[1].z * mat[3].x
        val det2_13_03 = mat[1].x * mat[3].w - mat[1].w * mat[3].x
        val det2_13_12 = mat[1].y * mat[3].z - mat[1].z * mat[3].y
        val det2_13_13 = mat[1].y * mat[3].w - mat[1].w * mat[3].y
        val det2_13_23 = mat[1].z * mat[3].w - mat[1].w * mat[3].z

        // remaining 3x3 sub-determinants
        val det3_203_012 = mat[2].x * det2_03_12 - mat[2].y * det2_03_02 + mat[2].z * det2_03_01
        val det3_203_013 = mat[2].x * det2_03_13 - mat[2].y * det2_03_03 + mat[2].w * det2_03_01
        val det3_203_023 = mat[2].x * det2_03_23 - mat[2].z * det2_03_03 + mat[2].w * det2_03_02
        val det3_203_123 = mat[2].y * det2_03_23 - mat[2].z * det2_03_13 + mat[2].w * det2_03_12
        val det3_213_012 = mat[2].x * det2_13_12 - mat[2].y * det2_13_02 + mat[2].z * det2_13_01
        val det3_213_013 = mat[2].x * det2_13_13 - mat[2].y * det2_13_03 + mat[2].w * det2_13_01
        val det3_213_023 = mat[2].x * det2_13_23 - mat[2].z * det2_13_03 + mat[2].w * det2_13_02
        val det3_213_123 = mat[2].y * det2_13_23 - mat[2].z * det2_13_13 + mat[2].w * det2_13_12
        val det3_301_012 = mat[3].x * det2_01_12 - mat[3].y * det2_01_02 + mat[3].z * det2_01_01
        val det3_301_013 = mat[3].x * det2_01_13 - mat[3].y * det2_01_03 + mat[3].w * det2_01_01
        val det3_301_023 = mat[3].x * det2_01_23 - mat[3].z * det2_01_03 + mat[3].w * det2_01_02
        val det3_301_123 = mat[3].y * det2_01_23 - mat[3].z * det2_01_13 + mat[3].w * det2_01_12
        mat[0].x = -det3_213_123 * invDet
        mat[1].x = +det3_213_023 * invDet
        mat[2].x = -det3_213_013 * invDet
        mat[3].x = +det3_213_012 * invDet
        mat[0].y = +det3_203_123 * invDet
        mat[1].y = -det3_203_023 * invDet
        mat[2].y = +det3_203_013 * invDet
        mat[3].y = -det3_203_012 * invDet
        mat[0].z = +det3_301_123 * invDet
        mat[1].z = -det3_301_023 * invDet
        mat[2].z = +det3_301_013 * invDet
        mat[3].z = -det3_301_012 * invDet
        mat[0].w = -det3_201_123 * invDet
        mat[1].w = +det3_201_023 * invDet
        mat[2].w = -det3_201_013 * invDet
        mat[3].w = +det3_201_012 * invDet
        return true
    }

    fun InverseFast(): idMat4 { // returns the inverse ( m * m.Inverse() = identity )
        val invMat = idMat4(this)
        val r = invMat.InverseFastSelf()
        assert(r)
        return invMat
    }

    fun InverseFastSelf(): Boolean // returns false if determinant is zero
    {
        val r0 = Array(2) { FloatArray(2) }
        val r1 = Array(2) { FloatArray(2) }
        val r2 = Array(2) { FloatArray(2) }
        val r3 = Array(2) { FloatArray(2) }
        val a: Float
        var det: Float
        var invDet: Float

        // r0 = m0.Inverse();
        det = mat[0].x * mat[1].y - mat[0].y * mat[1].x
        if (abs(det) < MATRIX_INVERSE_EPSILON) {
            return false
        }
        invDet = 1.0f / det
        r0[0][0] = mat[1].y * invDet
        r0[0][1] = -mat[0].y * invDet
        r0[1][0] = -mat[1].x * invDet
        r0[1][1] = mat[0].x * invDet

        // r1 = r0 * m1;
        r1[0][0] = r0[0][0] * mat[0].z + r0[0][1] * mat[1].z
        r1[0][1] = r0[0][0] * mat[0].w + r0[0][1] * mat[1].w
        r1[1][0] = r0[1][0] * mat[0].z + r0[1][1] * mat[1].z
        r1[1][1] = r0[1][0] * mat[0].w + r0[1][1] * mat[1].w

        // r2 = m2 * r1;
        r2[0][0] = mat[2].x * r1[0][0] + mat[2].y * r1[1][0]
        r2[0][1] = mat[2].x * r1[0][1] + mat[2].y * r1[1][1]
        r2[1][0] = mat[3].x * r1[0][0] + mat[3].y * r1[1][0]
        r2[1][1] = mat[3].x * r1[0][1] + mat[3].y * r1[1][1]

        // r3 = r2 - m3;
        r3[0][0] = r2[0][0] - mat[2].z
        r3[0][1] = r2[0][1] - mat[2].w
        r3[1][0] = r2[1][0] - mat[3].z
        r3[1][1] = r2[1][1] - mat[3].w

        // r3.InverseSelf();
        det = r3[0][0] * r3[1][1] - r3[0][1] * r3[1][0]
        if (abs(det) < MATRIX_INVERSE_EPSILON) {
            return false
        }
        invDet = 1.0f / det
        a = r3[0][0]
        r3[0][0] = r3[1][1] * invDet
        r3[0][1] = -r3[0][1] * invDet
        r3[1][0] = -r3[1][0] * invDet
        r3[1][1] = a * invDet

        // r2 = m2 * r0;
        r2[0][0] = mat[2].x * r0[0][0] + mat[2].y * r0[1][0]
        r2[0][1] = mat[2].x * r0[0][1] + mat[2].y * r0[1][1]
        r2[1][0] = mat[3].x * r0[0][0] + mat[3].y * r0[1][0]
        r2[1][1] = mat[3].x * r0[0][1] + mat[3].y * r0[1][1]

        // m2 = r3 * r2;
        mat[2].x = r3[0][0] * r2[0][0] + r3[0][1] * r2[1][0]
        mat[2].y = r3[0][0] * r2[0][1] + r3[0][1] * r2[1][1]
        mat[3].x = r3[1][0] * r2[0][0] + r3[1][1] * r2[1][0]
        mat[3].y = r3[1][0] * r2[0][1] + r3[1][1] * r2[1][1]

        // m0 = r0 - r1 * m2;
        mat[0].x = r0[0][0] - r1[0][0] * mat[2].x - r1[0][1] * mat[3].x
        mat[0].y = r0[0][1] - r1[0][0] * mat[2].y - r1[0][1] * mat[3].y
        mat[1].x = r0[1][0] - r1[1][0] * mat[2].x - r1[1][1] * mat[3].x
        mat[1].y = r0[1][1] - r1[1][0] * mat[2].y - r1[1][1] * mat[3].y

        // m1 = r1 * r3;
        mat[0].z = r1[0][0] * r3[0][0] + r1[0][1] * r3[1][0]
        mat[0].w = r1[0][0] * r3[0][1] + r1[0][1] * r3[1][1]
        mat[1].z = r1[1][0] * r3[0][0] + r1[1][1] * r3[1][0]
        mat[1].w = r1[1][0] * r3[0][1] + r1[1][1] * r3[1][1]

        // m3 = -r3;
        mat[2].z = -r3[0][0]
        mat[2].w = -r3[0][1]
        mat[3].z = -r3[1][0]
        mat[3].w = -r3[1][1]
        return true
    }

    fun GetDimension(): Int {
        return 16
    }

    private fun setCell(x: Int, y: Int, value: Float) {
        when (y) {
            0 -> mat[x].x = value
            1 -> mat[x].y = value
            2 -> mat[x].z = value
            3 -> mat[x].w = value
        }
    }

    private fun set(mat4: idMat4) {
        mat[0].set(mat4.mat[0])
        mat[1].set(mat4.mat[1])
        mat[2].set(mat4.mat[2])
        mat[3].set(mat4.mat[3])
    }

    fun reinterpret_cast(): FloatArray {
        val size = 4
        val temp = FloatArray(size * size)
        for (x in 0 until size) {
            for (y in 0 until size) {
                temp[x * size + y] = mat[x][y]
            }
        }
        return temp
    }

    companion object {
        private val mat4_identity: idMat4 = idMat4(
            idVec4(1.0f, 0.0f, 0.0f, 0.0f),
            idVec4(0.0f, 1.0f, 0.0f, 0.0f),
            idVec4(0.0f, 0.0f, 1.0f, 0.0f),
            idVec4(0.0f, 0.0f, 0.0f, 1.0f)
        )
        private val mat4_zero: idMat4 = idMat4(
            idVec4(0.0f, 0.0f, 0.0f, 0.0f),
            idVec4(0.0f, 0.0f, 0.0f, 0.0f),
            idVec4(0.0f, 0.0f, 0.0f, 0.0f),
            idVec4(0.0f, 0.0f, 0.0f, 0.0f)
        )

        fun getMat4_zero(): idMat4 {
            return idMat4(mat4_zero)
        }

        fun getMat4_identity(): idMat4 {
            return idMat4(mat4_identity)
        }

        //public	friend idMat4	operator*( const float a, const idMat4 &mat );
        fun times(a: Float, mat: idMat4): idMat4 {
            return mat * a
        }

        //public	friend idVec4	operator*( const idVec4 &vec, const idMat4 &mat );
        fun times(vec: idVec4, mat: idMat4): idVec4 {
            return mat * vec
        }

        fun times(vec: idVec3, mat: idMat4): idVec3 {
            return mat * vec
        }

        //public	idVec4 &		operator[]( int index );
        //public	idMat4			operator*( const float a ) const;
        fun timesAssign(vec: idVec4, mat: idMat4): idVec4 {
            vec.set(mat * vec)
            return vec
        }

        //public	idVec4			operator*( const idVec4 &vec ) const;
        fun timesAssign(vec: idVec3, mat: idMat4): idVec3 {
            vec.set(mat * vec)
            return vec
        }
    }
}
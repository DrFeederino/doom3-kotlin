package neo.idlib.math.Matrix

import neo.idlib.Text.Str.idStr
import neo.idlib.math.idVec2
import kotlin.math.abs

/**
 * 1x1 is too complex, so we'll skip to 2x2.
 */
//===============================================================
//
//	idMat2 - 2x2 matrix
//
//===============================================================
class idMat2 {
    private val mat: Array<idVec2> = Array(2) { idVec2() }

    constructor()
    constructor(x: idVec2, y: idVec2) {
        mat[0].x = x.x
        mat[0].y = x.y
        mat[1].x = y.x
        mat[1].y = y.y
    }

    constructor(xx: Float, xy: Float, yx: Float, yy: Float) {
        mat[0].x = xx
        mat[0].y = xy
        mat[1].x = yx
        mat[1].y = yy
    }

    constructor(src: Array<FloatArray>) {
//	memcpy( mat, src, 2 * 2 * sizeof( float ) );
        mat[0] = idVec2(src[0][0], src[0][1])
        mat[1] = idVec2(src[1][0], src[1][1])
    }

    constructor(m: idMat2) : this(m.mat[0], m.mat[1])

    //public	const idVec2 &	operator[]( int index ) const;
    //public	idVec2 &		operator[]( int index );
    operator fun get(index: Int): idVec2 {
        return mat[index]
    }

    //public	idMat2			operator-() const;
    operator fun unaryMinus(): idMat2 {
        return idMat2(
            -mat[0].x, -mat[0].y,
            -mat[1].x, -mat[1].y
        )
    }

    //public	idMat2			operator*( const float a ) const;
    operator fun times(a: Float): idMat2 {
        return idMat2(
            mat[0].x * a, mat[0].y * a,
            mat[1].x * a, mat[1].y * a
        )
    }

    //public	idVec2			operator*( const idVec2 &vec ) const;
    operator fun times(vec: idVec2): idVec2 {
        return idVec2(
            mat[0].x * vec.x + mat[0].y * vec.y,
            mat[1].x * vec.x + mat[1].y * vec.y
        )
    }

    //public	idMat2			operator*( const idMat2 &a ) const;
    operator fun times(a: idMat2): idMat2 {
        return idMat2(
            mat[0].x * a.mat[0].x + mat[0].y * a.mat[1].x,
            mat[0].x * a.mat[0].y + mat[0].y * a.mat[1].y,
            mat[1].x * a.mat[0].x + mat[1].y * a.mat[1].x,
            mat[1].x * a.mat[0].y + mat[1].y * a.mat[1].y
        )
    }

    //public	idMat2			operator+( const idMat2 &a ) const;
    operator fun plus(a: idMat2): idMat2 {
        return idMat2(
            mat[0].x + a.mat[0].x, mat[0].y + a.mat[0].y,
            mat[1].x + a.mat[1].x, mat[1].y + a.mat[1].y
        )
    }

    operator fun minus(a: idMat2): idMat2 {
        return idMat2(
            mat[0].x - a.mat[0].x, mat[0].y - a.mat[0].y,
            mat[1].x - a.mat[1].x, mat[1].y - a.mat[1].y
        )
    }

    //public	idMat2 &		operator*=( const float a );
    fun timesAssign(a: Float): idMat2 {
        mat[0].x *= a
        mat[0].y *= a
        mat[1].x *= a
        mat[1].y *= a
        return this
    }

    //public	idMat2 &		operator*=( const idMat2 &a );
    fun timesAssign(a: idMat2): idMat2 {
        var x: Float
        var y: Float
        x = mat[0].x
        y = mat[0].y
        mat[0].x = x * a.mat[0].x + y * a.mat[1].x
        mat[0].y = x * a.mat[0].y + y * a.mat[1].y
        x = mat[1].x
        y = mat[1].y
        mat[1].x = x * a.mat[0].x + y * a.mat[1].x
        mat[1].y = x * a.mat[0].y + y * a.mat[1].y
        return this
    }

    //public	idMat2 &		operator+=( const idMat2 &a );
    fun plusAssign(a: idMat2): idMat2 {
        mat[0].x += a.mat[0].x
        mat[0].y += a.mat[0].y
        mat[1].x += a.mat[1].x
        mat[1].y += a.mat[1].y
        return this
    }

    //public	idMat2 &		operator-=( const idMat2 &a );
    fun minusAssign(a: idMat2): idMat2 {
        mat[0].x -= a.mat[0].x
        mat[0].y -= a.mat[0].y
        mat[1].x -= a.mat[1].x
        mat[1].y -= a.mat[1].y
        return this
    }

    //public	friend idMat2	operator*( const float a, const idMat2 &mat );
    //public	friend idVec2	operator*( const idVec2 &vec, const idMat2 &mat );
    //public	friend idVec2 &	operator*=( idVec2 &vec, const idMat2 &mat );
    //public	bool			Compare( const idMat2 &a ) const;						// exact compare, no epsilon
    fun Compare(a: idMat2): Boolean { // exact compare, no epsilon
        return (mat[0].Compare(a.mat[0])
                && mat[1].Compare(a.mat[1]))
    }

    //public	bool			Compare( const idMat2 &a, const float epsilon ) const;	// compare with epsilon
    fun Compare(a: idMat2, epsilon: Float): Boolean { // compare with epsilon
        return (mat[0].Compare(a.mat[0], epsilon)
                && mat[1].Compare(a.mat[1], epsilon))
    }

    //public	bool			operator==( const idMat2 &a ) const;					// exact compare, no epsilon
    //public	bool			operator!=( const idMat2 &a ) const;					// exact compare, no epsilon
    override fun hashCode(): Int {
        var hash = 7
        hash = 83 * hash + mat.contentDeepHashCode()
        return hash
    }

    override fun equals(obj: Any?): Boolean {
        if (obj == null) {
            return false
        }
        if (javaClass != obj.javaClass) {
            return false
        }
        val other = obj as idMat2
        return Compare(other)
    }

    fun equals(a: idMat2): Boolean { // exact compare, no epsilon
        return Compare(a)
    }

    fun notEquals(a: idMat2): Boolean { // exact compare, no epsilon
        return !Compare(a)
    }

    fun Zero() {
        mat[0].Zero()
        mat[1].Zero()
    }

    fun Identity() {
        mat[0] = getMat2_identity().mat[0]
        mat[1] = getMat2_identity().mat[1]
    }


    fun IsIdentity(epsilon: Float): Boolean {
        return Compare(getMat2_identity(), epsilon)
    }


    fun IsSymmetric(epsilon: Float): Boolean {
        return abs(mat[0].y - mat[1].x) < epsilon
    }


    fun IsDiagonal(epsilon: Float): Boolean {
        return (abs(mat[0].y) <= epsilon
                && abs(mat[1].x) <= epsilon)
    }

    fun Trace(): Float {
        return mat[0].x + mat[1].y
    }

    fun Determinant(): Float {
        return mat[0].x * mat[1].y - mat[0].y * mat[1].x
    }

    fun Transpose(): idMat2 { // returns transpose
        return idMat2(
            mat[0].x, mat[1].x,
            mat[0].y, mat[1].y
        )
    }

    fun TransposeSelf(): idMat2 {
        val tmp: Float
        tmp = mat[0].y
        mat[0].y = mat[1].x
        mat[1].x = tmp
        return this
    }

    fun Inverse(): idMat2 { // returns the inverse ( m * m.Inverse() = identity )
        val invMat = idMat2(this)
        val r = invMat.InverseSelf()
        assert(r)
        return invMat
    }

    fun InverseSelf(): Boolean { // returns false if determinant is zero
        // 2+4 = 6 multiplications
        //		 1 division
//	double det, invDet, a;
        val det: Float
        val invDet: Float
        val a: Float
        det = Determinant() //	det = mat[0][0] * mat[1][1] - mat[0][1] * mat[1][0];
        if (abs(det) < MATRIX_INVERSE_EPSILON) {
            return false
        }
        invDet = 1.0f / det
        a = mat[0].x
        mat[0].x = (mat[1].y * invDet)
        mat[0].y = (-mat[0].y * invDet)
        mat[1].x = (-mat[1].x * invDet)
        mat[1].y = (a * invDet)
        return true
    }

    fun InverseFast(): idMat2 { // returns the inverse ( m * m.Inverse() = identity )
        val invMat = idMat2(this)
        val r = invMat.InverseFastSelf()
        assert(r)
        return invMat
    }

    fun InverseFastSelf(): Boolean { // returns false if determinant is zero
//#if 1
        // 2+4 = 6 multiplications
        //		 1 division
        val det: Float
        val invDet: Float
        val a: Float
        det = Determinant() //	det = mat[0][0] * mat[1][1] - mat[0][1] * mat[1][0];
        if (abs(det) < MATRIX_INVERSE_EPSILON) {
            return false
        }
        invDet = 1.0f / det
        a = mat[0].x
        mat[0].x = (mat[1].y * invDet)
        mat[0].y = (-mat[0].y * invDet)
        mat[1].x = (-mat[1].x * invDet)
        mat[1].y = (a * invDet)
        return true
    }

    fun GetDimension(): Int {
        return 4
    }

    fun ToFloatPtr(): FloatArray {
        return floatArrayOf(
            mat[0][0], mat[0][1],
            mat[1][0], mat[1][1]
        )
    }

    fun ToString(precision: Int = 2): String {
        return idStr.FloatArrayToString(ToFloatPtr(), GetDimension(), precision)
    }

    fun reinterpret_cast(): FloatArray {
        val size = 2
        val temp = FloatArray(size * size)
        for (x in 0 until size) {
            for (y in 0 until size) {
                temp[x * size + y] = mat[x][y]
            }
        }
        return temp
    }

    companion object {
        private val mat2_identity: idMat2 = idMat2(idVec2(1.0f, 0.0f), idVec2(0.0f, 1.0f))
        private val mat2_zero: idMat2 = idMat2(idVec2(0.0f, 0.0f), idVec2(0.0f, 0.0f))
        fun getMat2_zero(): idMat2 {
            return idMat2(mat2_zero)
        }

        fun getMat2_identity(): idMat2 {
            return idMat2(mat2_identity)
        }
    }
}
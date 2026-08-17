package neo.idlib.math.Matrix

import neo.idlib.Text.Str.idStr
import neo.idlib.math.*
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos

//===============================================================
//
//	idMat3 - 3x3 matrix
//
//	NOTE:	matrix is column-major
//
//===============================================================
class idMat3 {
    val mat: Array<idVec3> = idVec3.generateArray(3)

    constructor()
    constructor(x: idVec3, y: idVec3, z: idVec3) {
        mat[0].x = x.x
        mat[0].y = x.y
        mat[0].z = x.z //
        mat[1].x = y.x
        mat[1].y = y.y
        mat[1].z = y.z //
        mat[2].x = z.x
        mat[2].y = z.y
        mat[2].z = z.z
    }

    constructor(
        xx: Float, xy: Float, xz: Float, yx: Float, yy: Float, yz: Float, zx: Float, zy: Float, zz: Float
    ) {
        mat[0].x = xx
        mat[0].y = xy
        mat[0].z = xz //
        mat[1].x = yx
        mat[1].y = yy
        mat[1].z = yz //
        mat[2].x = zx
        mat[2].y = zy
        mat[2].z = zz
    }

    constructor(m: idMat3) {
        mat[0].x = m.mat[0].x
        mat[0].y = m.mat[0].y
        mat[0].z = m.mat[0].z //
        mat[1].x = m.mat[1].x
        mat[1].y = m.mat[1].y
        mat[1].z = m.mat[1].z //
        mat[2].x = m.mat[2].x
        mat[2].y = m.mat[2].y
        mat[2].z = m.mat[2].z
    }

    constructor(src: Array<FloatArray>) { //	memcpy( mat, src, 3 * 3 * sizeof( Float ) );
        mat[0].set(idVec3(src[0][0], src[0][1], src[0][2]))
        mat[1].set(idVec3(src[1][0], src[1][1], src[1][2]))
        mat[2].set(idVec3(src[2][0], src[2][1], src[2][2]))
    }

    //public	idVec3			operator*( const idVec3 &vec ) const;
    operator fun get(index: Int): idVec3 {
        return mat[index]
    }

    //public	idMat3			operator*( const idMat3 &a ) const;
    operator fun get(index1: Int, index2: Int): Float {
        return mat[index1][index2]
    }

    //public	idMat3			operator+( const idMat3 &a ) const;
    operator fun set(index: Int, vec3: idVec3) {
        mat[index].set(vec3)
    }

    //public	idMat3			operator-( const idMat3 &a ) const;
    //public	idMat3			operator-() const;
    operator fun unaryMinus(): idMat3 {
        return idMat3(
            -mat[0].x, -mat[0].y, -mat[0].z, -mat[1].x, -mat[1].y, -mat[1].z, -mat[2].x, -mat[2].y, -mat[2].z
        )
    }

    //public	idMat3 &		operator*=( const Float a );
    operator fun times(a: Float): idMat3 {
        return idMat3(
            mat[0].x * a,
            mat[0].y * a,
            mat[0].z * a,
            mat[1].x * a,
            mat[1].y * a,
            mat[1].z * a,
            mat[2].x * a,
            mat[2].y * a,
            mat[2].z * a
        )
    }

    //public	idMat3 &		operator*=( const idMat3 &a );
    operator fun times(vec: idVec3): idVec3 {
        return idVec3(
            mat[0].x * vec.x + mat[1].x * vec.y + mat[2].x * vec.z,
            mat[0].y * vec.x + mat[1].y * vec.y + mat[2].y * vec.z,
            mat[0].z * vec.x + mat[1].z * vec.y + mat[2].z * vec.z
        )
    }

    //public	idMat3 &		operator+=( const idMat3 &a );
    operator fun times(a: idMat3): idMat3 {
        val dst = idMat3()
        dst.setMul(this, a)
        return dst
    }

    //public	idMat3 &		operator-=( const idMat3 &a );
    operator fun plus(a: idMat3): idMat3 {
        return idMat3(
            mat[0].x + a.mat[0].x,
            mat[0].y + a.mat[0].y,
            mat[0].z + a.mat[0].z,
            mat[1].x + a.mat[1].x,
            mat[1].y + a.mat[1].y,
            mat[1].z + a.mat[1].z,
            mat[2].x + a.mat[2].x,
            mat[2].y + a.mat[2].y,
            mat[2].z + a.mat[2].z
        )
    }

    //
    //public	friend idMat3	operator*( const Float a, const idMat3 &mat );
    operator fun minus(a: idMat3): idMat3 {
        return idMat3(
            mat[0].x - a.mat[0].x,
            mat[0].y - a.mat[0].y,
            mat[0].z - a.mat[0].z,
            mat[1].x - a.mat[1].x,
            mat[1].y - a.mat[1].y,
            mat[1].z - a.mat[1].z,
            mat[2].x - a.mat[2].x,
            mat[2].y - a.mat[2].y,
            mat[2].z - a.mat[2].z
        )
    }

    //public	friend idVec3	operator*( const idVec3 &vec, const idMat3 &mat );
    fun timesAssign(a: Float): idMat3 {
        mat[0].x *= a
        mat[0].y *= a
        mat[0].z *= a //
        mat[1].x *= a
        mat[1].y *= a
        mat[1].z *= a //
        mat[2].x *= a
        mat[2].y *= a
        mat[2].z *= a
        return this
    }

    //public	friend idVec3 &	operator*=( idVec3 &vec, const idMat3 &mat );
    fun timesAssign(a: idMat3): idMat3 {
        var j: Int
        val dst = FloatArray(3)
        var i = 0
        while (i < 3) {
            j = 0
            while (j < 3) {
                dst[j] = mat[i].x * a.mat[0][j] + mat[i].y * a.mat[1][j] + mat[i].z * a.mat[2][j]
                j++
            }
            this.set(i, 0, dst[0])
            this.set(i, 1, dst[1])
            this.set(i, 2, dst[2])
            i++
        }
        return this
    }

    //
    fun plusAssign(a: Float): idMat3 {
        mat[0].x += a
        mat[0].y += a
        mat[0].z += a //
        mat[1].x += a
        mat[1].y += a
        mat[1].z += a //
        mat[2].x += a
        mat[2].y += a
        mat[2].z += a
        return this
    }

    fun minusAssign(a: Float): idMat3 {
        mat[0].x -= a
        mat[0].y -= a
        mat[0].z -= a //
        mat[1].x -= a
        mat[1].y -= a
        mat[1].z -= a //
        mat[2].x -= a
        mat[2].y -= a
        mat[2].z -= a
        return this
    }

    //public	bool			operator==( const idMat3 &a ) const;					// exact compare, no epsilon
    //public	bool			operator!=( const idMat3 &a ) const;					// exact compare, no epsilon
    fun Compare(a: idMat3): Boolean { // exact compare, no epsilon
        return (mat[0].Compare(a.mat[0]) && mat[1].Compare(a.mat[1]) && mat[2].Compare(a.mat[2]))
    }

    fun Compare(a: idMat3, epsilon: Float): Boolean { // compare with epsilon
        return (mat[0].Compare(a.mat[0], epsilon) && mat[1].Compare(a.mat[1], epsilon) && mat[2].Compare(
            a.mat[2], epsilon
        ))
    }

    override fun hashCode(): Int {
        var hash = 7
        hash = 37 * hash + mat[0].hashCode()
        hash = 37 * hash + mat[1].hashCode()
        hash = 37 * hash + mat[2].hashCode()
        return hash
    }

    override fun equals(obj: Any?): Boolean {
        if (obj == null) {
            return false
        }
        if (javaClass != obj.javaClass) {
            return false
        }
        val other = obj as idMat3
        return Compare(other)
    }

    fun equals(a: idMat3): Boolean { // exact compare, no epsilon
        return Compare(a)
    }

    fun notEquals(a: idMat3): Boolean { // exact compare, no epsilon
        return !Compare(a)
    }

    fun Zero() {
        mat[0].Zero()
        mat[1].Zero()
        mat[2].Zero()
    }

    fun Identity() {
        setIdentity()
    }

    fun setIdentity(scale: Float = 1.0f): idMat3 {
        mat[0].set(scale, 0.0f, 0.0f)
        mat[1].set(0.0f, scale, 0.0f)
        mat[2].set(0.0f, 0.0f, scale)
        return this
    }


    fun IsIdentity(epsilon: Float = MATRIX_EPSILON): Boolean {
        return Compare(getMat3_identity(), epsilon)
    }


    fun IsSymmetric(epsilon: Float = MATRIX_EPSILON): Boolean {
        if (abs(mat[0].y - mat[1].x) > epsilon) {
            return false
        }
        return if (abs(mat[0].z - mat[2].x) > epsilon) {
            false
        } else abs(mat[1].z - mat[2].y) <= epsilon
    }


    fun IsDiagonal(epsilon: Float = MATRIX_EPSILON): Boolean {
        return (abs(mat[0].y) <= epsilon && abs(mat[0].z) <= epsilon && abs(mat[1].x) <= epsilon && abs(mat[1].z) <= epsilon && abs(
            mat[2].x
        ) <= epsilon && abs(mat[2].y) <= epsilon)
    }

    fun IsRotated(): Boolean {
        return !Compare(mat3_identity)
    }

    fun ProjectVector(src: idVec3, dst: idVec3) {
        dst.x = src * mat[0]
        dst.y = src * mat[1]
        dst.z = src * mat[2]
    }

    fun UnprojectVector(src: idVec3, dst: idVec3) {
        dst.set(
            mat[0] * src.x + mat[1] * src.y + mat[2] * src.z
        )
    }

    fun FixDegeneracies(): Boolean { // fix degenerate axial cases
        var r = mat[0].FixDegenerateNormal()
        r = r or mat[1].FixDegenerateNormal()
        r = r or mat[2].FixDegenerateNormal()
        return r
    }

    fun FixDenormals(): Boolean { // change tiny numbers to zero
        var r = mat[0].FixDenormals()
        r = r or mat[1].FixDenormals()
        r = r or mat[2].FixDenormals()
        return r
    }

    fun Trace(): Float {
        return mat[0].x + mat[1].y + mat[2].z
    }

    fun Determinant(): Float {
        val det2_12_01 = mat[1].x * mat[2].y - mat[1].y * mat[2].x
        val det2_12_02 = mat[1].x * mat[2].z - mat[1].z * mat[2].x
        val det2_12_12 = mat[1].y * mat[2].z - mat[1].z * mat[2].y
        return mat[0].x * det2_12_12 - mat[0].y * det2_12_02 + mat[0].z * det2_12_01
    }

    fun OrthoNormalize(): idMat3 {
        val ortho: idMat3 = idMat3(this)
        ortho.mat[0].Normalize()
        ortho.mat[2].Cross(mat[0], mat[1])
        ortho.mat[2].Normalize()
        ortho.mat[1].Cross(mat[2], mat[0])
        ortho.mat[1].Normalize()
        return ortho
    }

    fun OrthoNormalizeSelf(): idMat3 {
        mat[0].Normalize()
        mat[2].Cross(mat[0], mat[1])
        mat[2].Normalize()
        mat[1].Cross(mat[2], mat[0])
        mat[1].Normalize()
        return this
    }

    fun Transpose(): idMat3 { // returns transpose
        return idMat3(
            mat[0].x, mat[1].x, mat[2].x, mat[0].y, mat[1].y, mat[2].y, mat[0].z, mat[1].z, mat[2].z
        )
    }

    fun TransposeSelf(): idMat3 {
        val tmp0: Float = mat[0].y
        mat[0].y = mat[1].x
        mat[1].x = tmp0
        val tmp1: Float = mat[0].z
        mat[0].z = mat[2].x
        mat[2].x = tmp1
        val tmp2: Float = mat[1].z
        mat[1].z = mat[2].y
        mat[2].y = tmp2
        return this
    }

    fun setTranspose(src: idMat3): idMat3 {
        mat[0].set(src.mat[0].x, src.mat[1].x, src.mat[2].x)
        mat[1].set(src.mat[0].y, src.mat[1].y, src.mat[2].y)
        mat[2].set(src.mat[0].z, src.mat[1].z, src.mat[2].z)
        return this
    }

    fun setSkewSymmetric(src: idVec3): idMat3 {
        mat[0].set(0.0f, -src.z, src.y)
        mat[1].set(src.z, 0.0f, -src.x)
        mat[2].set(-src.y, src.x, 0.0f)
        return this
    }

    fun Inverse(): idMat3 { // returns the inverse ( m * m.Inverse() = identity )
        val invMat = idMat3(this)
        val r = invMat.InverseSelf()
        assert(r)
        return invMat
    }

    fun InverseSelf(): Boolean { // returns false if determinant is zero
        // 18+3+9 = 30 multiplications
        //			 1 division
        val inverse = idMat3()
        inverse.mat[0].x = mat[1].y * mat[2].z - mat[1].z * mat[2].y
        inverse.mat[1].x = mat[1].z * mat[2].x - mat[1].x * mat[2].z
        inverse.mat[2].x = mat[1].x * mat[2].y - mat[1].y * mat[2].x
        val det: Double =
            (mat[0].x * inverse.mat[0].x + mat[0].y * inverse.mat[1].x + mat[0].z * inverse.mat[2].x).toDouble()
        if (abs(det) < MATRIX_INVERSE_EPSILON) {
            return false
        }
        val invDet: Double = 1.0 / det
        inverse.mat[0].y = mat[0].z * mat[2].y - mat[0].y * mat[2].z
        inverse.mat[0].z = mat[0].y * mat[1].z - mat[0].z * mat[1].y
        inverse.mat[1].y = mat[0].x * mat[2].z - mat[0].z * mat[2].x
        inverse.mat[1].z = mat[0].z * mat[1].x - mat[0].x * mat[1].z
        inverse.mat[2].y = mat[0].y * mat[2].x - mat[0].x * mat[2].y
        inverse.mat[2].z = mat[0].x * mat[1].y - mat[0].y * mat[1].x
        mat[0].x = (inverse.mat[0].x * invDet).toFloat()
        mat[0].y = (inverse.mat[0].y * invDet).toFloat()
        mat[0].z = (inverse.mat[0].z * invDet).toFloat()
        mat[1].x = (inverse.mat[1].x * invDet).toFloat()
        mat[1].y = (inverse.mat[1].y * invDet).toFloat()
        mat[1].z = (inverse.mat[1].z * invDet).toFloat()
        mat[2].x = (inverse.mat[2].x * invDet).toFloat()
        mat[2].y = (inverse.mat[2].y * invDet).toFloat()
        mat[2].z = (inverse.mat[2].z * invDet).toFloat()
        return true
    }

    fun InverseFast(): idMat3 { // returns the inverse ( m * m.Inverse() = identity )
        val invMat = idMat3(this)
        val r = invMat.InverseFastSelf()
        assert(r)
        return invMat
    }

    //
    fun InverseFastSelf(): Boolean // returns false if determinant is zero
    { //TODO://#if 1
        return InverseSelf()
    }

    fun TransposeMultiply(b: idMat3): idMat3 {
        return idMat3(
            mat[0].x * b.mat[0].x + mat[1].x * b.mat[1].x + mat[2].x * b.mat[2].x,
            mat[0].x * b.mat[0].y + mat[1].x * b.mat[1].y + mat[2].x * b.mat[2].y,
            mat[0].x * b.mat[0].z + mat[1].x * b.mat[1].z + mat[2].x * b.mat[2].z,
            mat[0].y * b.mat[0].x + mat[1].y * b.mat[1].x + mat[2].y * b.mat[2].x,
            mat[0].y * b.mat[0].y + mat[1].y * b.mat[1].y + mat[2].y * b.mat[2].y,
            mat[0].y * b.mat[0].z + mat[1].y * b.mat[1].z + mat[2].y * b.mat[2].z,
            mat[0].z * b.mat[0].x + mat[1].z * b.mat[1].x + mat[2].z * b.mat[2].x,
            mat[0].z * b.mat[0].y + mat[1].z * b.mat[1].y + mat[2].z * b.mat[2].y,
            mat[0].z * b.mat[0].z + mat[1].z * b.mat[1].z + mat[2].z * b.mat[2].z
        )
    }

    fun InertiaTranslate(mass: Float, centerOfMass: idVec3, translation: idVec3): idMat3 {
        val m = idMat3()
        val newCenter = idVec3()
        newCenter.set(centerOfMass + translation)
        m.mat[0].x =
            mass * (centerOfMass.y * centerOfMass.y + centerOfMass.z * centerOfMass.z - (newCenter.y * newCenter.y + newCenter.z * newCenter.z))
        m.mat[1].y =
            mass * (centerOfMass.x * centerOfMass.x + centerOfMass.z * centerOfMass.z - (newCenter.x * newCenter.x + newCenter.z * newCenter.z))
        m.mat[2].z =
            mass * (centerOfMass.x * centerOfMass.x + centerOfMass.y * centerOfMass.y - (newCenter.x * newCenter.x + newCenter.y * newCenter.y))
        m.mat[1].x = mass * (newCenter.x * newCenter.y - centerOfMass.x * centerOfMass.y)
        m.mat[0].y = m.mat[1].x
        m.mat[2].y = mass * (newCenter.y * newCenter.z - centerOfMass.y * centerOfMass.z)
        m.mat[1].z = m.mat[2].y
        m.mat[2].x = mass * (newCenter.x * newCenter.z - centerOfMass.x * centerOfMass.z)
        m.mat[0].z = m.mat[2].x
        return plus(m)
    }

    fun InertiaTranslateSelf(mass: Float, centerOfMass: idVec3, translation: idVec3): idMat3 {
        val m = idMat3()
        val newCenter = idVec3()
        newCenter.set(centerOfMass + translation)
        m.mat[0].x =
            mass * (centerOfMass.y * centerOfMass.y + centerOfMass.z * centerOfMass.z - (newCenter.y * newCenter.y + newCenter.z * newCenter.z))
        m.mat[1].y =
            mass * (centerOfMass.x * centerOfMass.x + centerOfMass.z * centerOfMass.z - (newCenter.x * newCenter.x + newCenter.z * newCenter.z))
        m.mat[2].z =
            mass * (centerOfMass.x * centerOfMass.x + centerOfMass.y * centerOfMass.y - (newCenter.x * newCenter.x + newCenter.y * newCenter.y))
        m.mat[1].x = mass * (newCenter.x * newCenter.y - centerOfMass.x * centerOfMass.y)
        m.mat[0].y = m.mat[1].x
        m.mat[2].y = mass * (newCenter.y * newCenter.z - centerOfMass.y * centerOfMass.z)
        m.mat[1].z = m.mat[2].y
        m.mat[2].x = mass * (newCenter.x * newCenter.z - centerOfMass.x * centerOfMass.z)
        m.mat[0].z = m.mat[2].x
        return this.plusAssign(m)
    }

    fun InertiaRotate(rotation: idMat3): idMat3 { // NOTE: the rotation matrix is stored column-major
        //            return rotation.Transpose() * (*this) * rotation;
        return rotation.Transpose() * this * rotation
    }

    fun InertiaRotateSelf(rotation: idMat3): idMat3 { // NOTE: the rotation matrix is stored column-major
        //	*this = rotation.Transpose() * (*this) * rotation;
        this.set(rotation.Transpose() * this * rotation)
        return this
    }

    fun GetDimension(): Int {
        return 9
    }

    fun ToAngles(): idAngles {
        val angles = idAngles()
        val theta: Float
        var sp: Float
        sp = mat[0].z

        // cap off our sin value so that we don't get any NANs
        if (sp > 1.0f) {
            sp = 1.0f
        } else if (sp < -1.0f) {
            sp = -1.0f
        }
        theta = -asin(sp)
        val cp: Float = cos(theta)
        if (cp > 8192.0f * idMath.FLT_EPSILON) {
            angles.pitch = RAD2DEG(theta)
            angles.yaw = RAD2DEG(atan2(mat[0].y, mat[0].x))
            angles.roll = RAD2DEG(atan2(mat[1].z, mat[2].z))
        } else {
            angles.pitch = RAD2DEG(theta)
            angles.yaw = RAD2DEG(-atan2(mat[1].x, mat[1].y))
            angles.roll = 0.0f
        }
        return angles
    }

    fun ToQuat(): idQuat {
        val q = idQuat()
        val s: Float
        val t: Float
        var i: Int
        val j: Int
        val k: Int
        val next = intArrayOf(1, 2, 0)

        //	trace = mat[0 ][0 ] + mat[1 ][1 ] + mat[2 ][2 ];
        val trace: Float = Trace()
        if (trace > 0.0f) {
            t = trace + 1.0f
            s = idMath.InvSqrt(t) * 0.5f
            q[3] = s * t
            q[0] = (mat[2].y - mat[1].z) * s
            q[1] = (mat[0].z - mat[2].x) * s
            q[2] = (mat[1].x - mat[0].y) * s
        } else {
            i = 0
            if (mat[1].y > mat[0].x) {
                i = 1
            }
            if (mat[2].z > mat[i][i]) {
                i = 2
            }
            j = next[i]
            k = next[j]
            t = mat[i][i] - (mat[j][j] + mat[k][k]) + 1.0f
            s = idMath.InvSqrt(t) * 0.5f
            q[i] = s * t
            q[3] = (mat[k][j] - mat[j][k]) * s
            q[j] = (mat[j][i] + mat[i][j]) * s
            q[k] = (mat[k][i] + mat[i][k]) * s
        }
        return q
    }

    fun ToCQuat(): idCQuat {
        val q = ToQuat()
        return if (q.w < 0.0f) {
            idCQuat(-q.x, -q.y, -q.z)
        } else idCQuat(q.x, q.y, q.z)
    }

    fun ToRotation(): idRotation {
        val r = idRotation()
        val s: Float
        val t: Float
        var i: Int
        val j: Int
        val k: Int
        val next = intArrayOf(1, 2, 0)
        val trace: Float = mat[0].x + mat[1].y + mat[2].z
        if (trace > 0.0f) {
            t = trace + 1.0f
            s = idMath.InvSqrt(t) * 0.5f
            r.angle = s * t
            r.vec[0] = (mat[2].y - mat[1].z) * s
            r.vec[1] = (mat[0].z - mat[2].x) * s
            r.vec[2] = (mat[1].x - mat[0].y) * s
        } else {
            i = 0
            if (mat[1].y > mat[0].x) {
                i = 1
            }
            if (mat[2].z > mat[i][i]) {
                i = 2
            }
            j = next[i]
            k = next[j]
            t = mat[i][i] - (mat[j][j] + mat[k][k]) + 1.0f
            s = idMath.InvSqrt(t) * 0.5f
            r.vec[i] = s * t
            r.angle = (mat[k][j] - mat[j][k]) * s
            r.vec[j] = (mat[j][i] + mat[i][j]) * s
            r.vec[k] = (mat[k][i] + mat[i][k]) * s
        }
        r.angle = idMath.ACos(r.angle)
        if (abs(r.angle) < 1e-10f) {
            r.vec.set(0.0f, 0.0f, 1.0f)
            r.angle = 0.0f
        } else { //vec *= (1.0f / sin( angle ));
            r.vec.Normalize()
            r.vec.FixDegenerateNormal()
            r.angle *= 2.0f * idMath.M_RAD2DEG
        }
        r.origin.Zero()
        r.axis.set(this)
        r.axisValid = true
        return r
    }

    fun ToMat4(): idMat4 { // NOTE: idMat3 is transposed because it is column-major
        return idMat4(
            mat[0].x,
            mat[1].x,
            mat[2].x,
            0.0f,
            mat[0].y,
            mat[1].y,
            mat[2].y,
            0.0f,
            mat[0].z,
            mat[1].z,
            mat[2].z,
            0.0f,
            0.0f,
            0.0f,
            0.0f,
            1.0f
        )
    }

    //	public	Float *			ToFloatPtr( void );
    fun ToAngularVelocity(): idVec3 {
        val rotation = ToRotation()
        return rotation.GetVec() * DEG2RAD(rotation.GetAngle())
    }

    /**
     * Read-only array.
     */
    fun ToFloatPtr(): FloatArray {
        return floatArrayOf(
            mat[0].x, mat[0].y, mat[0].z, mat[1].x, mat[1].y, mat[1].z, mat[2].x, mat[2].y, mat[2].z
        )
    }

    fun ToString(precision: Int = 2): String {
        return idStr.FloatArrayToString(ToFloatPtr(), GetDimension(), precision)
    }

    fun set(x: Int, y: Int, value: Float): Float {
        return when (y) {
            1 -> value.also { mat[x].y = it }
            2 -> value.also { mat[x].z = it }
            else -> value.also { mat[x].x = it }
        }
    }

    fun set(m: idMat3): idMat3 {
        mat[0].set(m.mat[0])
        mat[1].set(m.mat[1])
        mat[2].set(m.mat[2])
        return this
    }

    fun setMul(m1: idMat3, m2: idMat3): idMat3 {
        val xx = m1.mat[0].x * m2.mat[0].x + m1.mat[0].y * m2.mat[1].x + m1.mat[0].z * m2.mat[2].x
        val xy = m1.mat[0].x * m2.mat[0].y + m1.mat[0].y * m2.mat[1].y + m1.mat[0].z * m2.mat[2].y
        val xz = m1.mat[0].x * m2.mat[0].z + m1.mat[0].y * m2.mat[1].z + m1.mat[0].z * m2.mat[2].z
        val yx = m1.mat[1].x * m2.mat[0].x + m1.mat[1].y * m2.mat[1].x + m1.mat[1].z * m2.mat[2].x
        val yy = m1.mat[1].x * m2.mat[0].y + m1.mat[1].y * m2.mat[1].y + m1.mat[1].z * m2.mat[2].y
        val yz = m1.mat[1].x * m2.mat[0].z + m1.mat[1].y * m2.mat[1].z + m1.mat[1].z * m2.mat[2].z
        val zx = m1.mat[2].x * m2.mat[0].x + m1.mat[2].y * m2.mat[1].x + m1.mat[2].z * m2.mat[2].x
        val zy = m1.mat[2].x * m2.mat[0].y + m1.mat[2].y * m2.mat[1].y + m1.mat[2].z * m2.mat[2].y
        val zz = m1.mat[2].x * m2.mat[0].z + m1.mat[2].y * m2.mat[1].z + m1.mat[2].z * m2.mat[2].z
        mat[0].set(xx, xy, xz)
        mat[1].set(yx, yy, yz)
        mat[2].set(zx, zy, zz)
        return this
    }

    fun setMulTransposeRight(m1: idMat3, m2: idMat3): idMat3 {
        val xx = m1.mat[0].x * m2.mat[0].x + m1.mat[0].y * m2.mat[0].y + m1.mat[0].z * m2.mat[0].z
        val xy = m1.mat[0].x * m2.mat[1].x + m1.mat[0].y * m2.mat[1].y + m1.mat[0].z * m2.mat[1].z
        val xz = m1.mat[0].x * m2.mat[2].x + m1.mat[0].y * m2.mat[2].y + m1.mat[0].z * m2.mat[2].z
        val yx = m1.mat[1].x * m2.mat[0].x + m1.mat[1].y * m2.mat[0].y + m1.mat[1].z * m2.mat[0].z
        val yy = m1.mat[1].x * m2.mat[1].x + m1.mat[1].y * m2.mat[1].y + m1.mat[1].z * m2.mat[1].z
        val yz = m1.mat[1].x * m2.mat[2].x + m1.mat[1].y * m2.mat[2].y + m1.mat[1].z * m2.mat[2].z
        val zx = m1.mat[2].x * m2.mat[0].x + m1.mat[2].y * m2.mat[0].y + m1.mat[2].z * m2.mat[0].z
        val zy = m1.mat[2].x * m2.mat[1].x + m1.mat[2].y * m2.mat[1].y + m1.mat[2].z * m2.mat[1].z
        val zz = m1.mat[2].x * m2.mat[2].x + m1.mat[2].y * m2.mat[2].y + m1.mat[2].z * m2.mat[2].z
        mat[0].set(xx, xy, xz)
        mat[1].set(yx, yy, yz)
        mat[2].set(zx, zy, zz)
        return this
    }

    fun minusAssign(x: Int, y: Int, value: Float) {
        when (y) {
            0 -> mat[x].x -= value
            1 -> mat[x].y -= value
            2 -> mat[x].z -= value
        }
    }

    fun plusAssign(x: Int, y: Int, value: Float) {
        when (y) {
            0 -> mat[x].x += value
            1 -> mat[x].y += value
            2 -> mat[x].z += value
        }
    }

    fun plusAssign(a: idMat3): idMat3 {
        mat[0].x += a.mat[0].x
        mat[0].y += a.mat[0].y
        mat[0].z += a.mat[0].z //
        mat[1].x += a.mat[1].x
        mat[1].y += a.mat[1].y
        mat[1].z += a.mat[1].z //
        mat[2].x += a.mat[2].x
        mat[2].y += a.mat[2].y
        mat[2].z += a.mat[2].z
        return this
    }

    fun reinterpret_cast(): FloatArray {
        val size = 3
        val temp = FloatArray(size * size)
        for (x in 0 until size) {
            for (y in 0 until size) {
                temp[x * size + y] = mat[x][y]
            }
        }
        return temp
    }

    override fun toString(): String {
        return """
            
            ${mat[0]},
            ${mat[1]},
            ${mat[2]}
            """.trimIndent()
    }

    companion object {
        val BYTES: Int = idVec3.BYTES * 3
        private val mat3_identity: idMat3 =
            idMat3(idVec3(1.0f, 0.0f, 0.0f), idVec3(0.0f, 1.0f, 0.0f), idVec3(0.0f, 0.0f, 1.0f))
        private val mat3_default: idMat3 = mat3_identity
        private val mat3_zero: idMat3 =
            idMat3(idVec3(0.0f, 0.0f, 0.0f), idVec3(0.0f, 0.0f, 0.0f), idVec3(0.0f, 0.0f, 0.0f))
        private const val DBG_counter = 0
        fun getMat3_zero(): idMat3 {
            return idMat3(mat3_zero)
        }


        fun getMat3_identity(): idMat3 {
            return idMat3(mat3_identity)
        }

        fun getMat3_default(): idMat3 {
            return idMat3(mat3_default)
        }

        //
        //public	const idVec3 &	operator[]( int index ) const;
        //public	idVec3 &		operator[]( int index );
        fun times(a: Float, mat: idMat3): idMat3 {
            return mat * a
        }

        fun times(vec: idVec3, mat: idMat3): idVec3 {
            return mat * vec
        }

        fun timesAssign(vec: idVec3, mat: idMat3): idVec3 {
            val x = mat.mat[0].x * vec.x + mat.mat[1].x * vec.y + mat.mat[2].x * vec.z
            val y = mat[0].y * vec.x + mat.mat[1].y * vec.y + mat.mat[2].y * vec.z
            vec.z = mat.mat[0].z * vec.x + mat.mat[1].z * vec.y + mat.mat[2].z * vec.z
            vec.x = x
            vec.y = y
            return vec
        }

        fun TransposeMultiply(transpose: idMat3, b: idMat3, dst: idMat3) {
            dst.mat[0].x =
                transpose.mat[0].x * b.mat[0].x + transpose.mat[1].x * b.mat[1].x + transpose.mat[2].x * b.mat[2].x
            dst.mat[0].y =
                transpose.mat[0].x * b.mat[0].y + transpose.mat[1].x * b.mat[1].y + transpose.mat[2].x * b.mat[2].y
            dst.mat[0].z =
                transpose.mat[0].x * b.mat[0].z + transpose.mat[1].x * b.mat[1].z + transpose.mat[2].x * b.mat[2].z
            dst.mat[1].x =
                transpose.mat[0].y * b.mat[0].x + transpose.mat[1].y * b.mat[1].x + transpose.mat[2].y * b.mat[2].x
            dst.mat[1].y =
                transpose.mat[0].y * b.mat[0].y + transpose.mat[1].y * b.mat[1].y + transpose.mat[2].y * b.mat[2].y
            dst.mat[1].z =
                transpose.mat[0].y * b.mat[0].z + transpose.mat[1].y * b.mat[1].z + transpose.mat[2].y * b.mat[2].z
            dst.mat[2].x =
                transpose.mat[0].z * b.mat[0].x + transpose.mat[1].z * b.mat[1].x + transpose.mat[2].z * b.mat[2].x
            dst.mat[2].y =
                transpose.mat[0].z * b.mat[0].y + transpose.mat[1].z * b.mat[1].y + transpose.mat[2].z * b.mat[2].y
            dst.mat[2].z =
                transpose.mat[0].z * b.mat[0].z + transpose.mat[1].z * b.mat[1].z + transpose.mat[2].z * b.mat[2].z
        }

        //public	idMat3			operator*( const Float a ) const;
        fun SkewSymmetric(src: idVec3): idMat3 {
            return idMat3(0.0f, -src.z, src.y, src.z, 0.0f, -src.x, -src.y, src.x, 0.0f)
        }
    }
}

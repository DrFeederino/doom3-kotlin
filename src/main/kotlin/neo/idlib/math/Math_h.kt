package neo.idlib.math

import neo.idlib.containers.CFloat
import kotlin.math.*

/**
 * ===============================================================================
 *
 *
 * Math
 *
 *
 * ===============================================================================
 */
private const val IEEE_DBL_MANTISSA_BITS = 52
private const val IEEE_DBL_EXPONENT_BITS = 11
private const val IEEE_DBL_EXPONENT_BIAS = 1023
private const val IEEE_DBL_SIGN_BIT = 63
private val IEEE_DBL_SIGN_MASK = 1UL shl IEEE_DBL_SIGN_BIT

private const val IEEE_DBLE_MANTISSA_BITS = 63
private const val IEEE_DBLE_EXPONENT_BITS = 15
private const val IEEE_DBLE_EXPONENT_BIAS = 0
private const val IEEE_DBLE_SIGN_BIT = 79
private const val IEEE_FLT_EXPONENT_BIAS = 127
private const val IEEE_FLT_EXPONENT_BITS = 8
private const val IEEE_FLT_MANTISSA_BITS = 23
private const val IEEE_FLT_SIGN_BIT = 31
private val IEEE_FLT_SIGN_MASK = 1UL shl IEEE_FLT_SIGN_BIT

// Integer sign bits
private const val INT8_SIGN_BIT = 7
private const val INT16_SIGN_BIT = 15
private const val INT32_SIGN_BIT = 31
private const val INT64_SIGN_BIT = 63

private const val INT8_SIGN_MASK = 1 shl INT8_SIGN_BIT
private const val INT16_SIGN_MASK = 1 shl INT16_SIGN_BIT
private val INT32_SIGN_MASK = 1UL shl INT32_SIGN_BIT
private val INT64_SIGN_MASK = 1UL shl INT64_SIGN_BIT


fun DEG2RAD(a: Float): Float {
    return a * idMath.M_DEG2RAD
}


fun RAD2DEG(a: Float): Float {
    return a * idMath.M_RAD2DEG
}


fun SEC2MS(t: Float): Int {
    return idMath.FtoiFast(t * idMath.M_SEC2MS)
}

fun MS2SEC(t: Float): Float {
    return t * idMath.M_MS2SEC
}

fun ANGLE2SHORT(x: Float): Short {
    return (idMath.FtoiFast(x * 65536.0f / 360.0f) and 65535).toShort()
}

fun SHORT2ANGLE(x: Short): Float {
    return x * (360.0f / 65536.0f)
}

fun ANGLE2BYTE(x: Float): Byte {
    return (idMath.FtoiFast(x * 256.0f / 360.0f) and 255).toByte()
}

fun BYTE2ANGLE(x: Byte): Float {
    return x * (360.0f / 256.0f)
}

fun FLOATSIGNBITSET(f: Float): Int {
    return if (
    /**
     * (const unsigned long *)&
     */
        f.toBits() and -0x80000000 == 0) 0 else 1
}

fun FLOATSIGNBITNOTSET(f: Float): Int {
    return if (f.toBits().inv() and -0x80000000 == 0) 0 else 1
}

fun FLOATNOTZERO(f: Float): Boolean {
    return f != 0.0f
}

fun INTSIGNBITSET(i: Int): Int {
    return i ushr 31
}

fun INTSIGNBITNOTSET(i: Int): Int {
    return i.inv() ushr 31
}

fun FLOAT_IS_NAN(x: Float): Boolean {
    val bits = x.toBits()
    return (bits and 0x7f800000) == 0x7f800000 && (bits and 0x007fffff) != 0x00000000
}

fun FLOAT_IS_INF(x: Float): Boolean {
    return (x.toBits() and 0x7fffffff) == 0x7f800000
}


fun FLOAT_IS_DENORMAL(x: Float): Boolean {
    return x.toBits() and 0x7f800000 == 0x00000000 && x.toBits() and 0x007fffff != 0x00000000
}

fun FLOAT_IS_IND(x: Float): Boolean {
    return x.toBits().toLong() == 0xffc00000
}

fun IsNAN(f: Float): Boolean {
    return FLOAT_IS_NAN(f) || FLOAT_IS_INF(f) || FLOAT_IS_IND(f)
}

fun IsValid(f: Float): Boolean {
    return !(FLOAT_IS_NAN(f) || FLOAT_IS_INF(f) || FLOAT_IS_IND(f) || FLOAT_IS_DENORMAL(f))
}

fun MaxIndex(x: Float, y: Float): Int {
    return if (x > y) 0 else 1
}

fun MinIndex(x: Float, y: Float): Int {
    return if (x < y) 0 else 1
}

fun Max3(x: Float, y: Float, z: Float): Float {
    return if (x > y) if (x > z) x else z else if (y > z) y else z
}

fun Min3(x: Float, y: Float, z: Float): Float {
    return if (x < y) if (x < z) x else z else if (y < z) y else z
}

fun Max3Index(x: Float, y: Float, z: Float): Int {
    return if (x > y) if (x > z) 0 else 2 else if (y > z) 1 else 2
}

fun Min3Index(x: Float, y: Float, z: Float): Int {
    return if (x < y) if (x < z) 0 else 2 else if (y < z) 1 else 2
}

fun Sign(f: Float): Int {
    return if (f > 0) 1 else if (f < 0) -1 else 0
}


fun Square(x: Float): Float {
    return x * x
}

fun Cube(x: Float): Float {
    return x * x * x
}

object idMath {
    const val EXP_BIAS = 127
    const val EXP_POS = 23
    const val FLT_EPSILON = 1.192092896e-07f // smallest positive number such that 1.0f+FLT_EPSILON != 1.0f
    const val INFINITY = 1e30f // huge number which should be larger than any valid number used

    //	enum {
    const val LOOKUP_BITS = 8
    const val LOOKUP_POS = EXP_POS - LOOKUP_BITS
    const val M_MS2SEC = 0.001f // milliseconds to seconds multiplier
    const val PI = 3.14159265358979323846f // pi

    //TODO:radians?
    const val M_DEG2RAD = PI / 180.0f // degrees to radians multiplier
    const val M_RAD2DEG = 180.0f / PI // radians to degrees multiplier
    const val SEED_POS = EXP_POS - 8
    const val SQRT_1OVER2 = 0.70710678118654752440f // sqrt( 1 / 2 )
    const val SQRT_TABLE_SIZE = 2 shl LOOKUP_BITS
    const val LOOKUP_MASK = SQRT_TABLE_SIZE - 1
    private val iSqrt: IntArray = IntArray(SQRT_TABLE_SIZE)
    const val TWO_PI = 2.0f * PI // pi * 2
    const val E = 2.71828182845904523536f // e
    const val HALF_PI = 0.5f * PI // pi / 2
    const val M_SEC2MS = 1000.0f // seconds to milliseconds multiplier
    const val ONEFOURTH_PI = 0.25f * PI // pi / 4
    const val SQRT_1OVER3 = 0.57735026918962576450f // sqrt( 1 / 3 )
    const val SQRT_THREE = 1.73205080756887729352f // sqrt( 3 )
    const val SQRT_TWO = 1.41421356237309504880f // sqrt( 2 )
    const val ONEOVER_PI = 0.31830988618379067154f // 1 / pi
    const val ONEOVER_TWOPI = 0.15915494309189533577f // 1 / (2 * pi)
    const val FLT_SMALLEST_NON_DENORMAL = Float.MIN_VALUE // smallest non-denormal 32-bit float
    private var initialized = false
    fun Init() {
        var fi: _flint
        var fo: _flint
        for (i in 0 until SQRT_TABLE_SIZE) {
            fi = _flint(EXP_BIAS - 1 shl EXP_POS or (i shl LOOKUP_POS))
            fo = _flint((1.0f / sqrt(fi.f)))
            iSqrt[i] = (fo.i + (1 shl SEED_POS - 2) shr SEED_POS and 0xFF) shl SEED_POS
        }
        iSqrt[SQRT_TABLE_SIZE / 2] = (0xFF shl SEED_POS)
        initialized = true
    }


    fun RSqrt(x: Float): Float { // reciprocal square root, returns huge number when x == 0.0f
        var i: Int
        val y: Float
        var r: Float
        y = x * 0.5f
        i = x.toBits()
        i = 0x5f3759df - (i shr 1)
        r = Float.fromBits(i)
        r = r * (1.5f - r * r * y)
        return r
    }

    // inverse square root with 32 bits precision, returns huge number when x == 0.0f

    fun InvSqrt(x: Float): Float {

//	long  a = ((union _flint*)(&x))->i;
        val seed = _flint(x)
        val a = seed.getInt()
        assert(initialized)
        val y = (x * 0.5f).toDouble()
        seed.setInt(
            3 * EXP_BIAS - 1 - (a shr EXP_POS and 0xFF) shr 1 shl EXP_POS
                    or iSqrt[a shr EXP_POS - LOOKUP_BITS and LOOKUP_MASK]
        )
        var r = seed.f.toDouble()
        r = r * (1.5f - r * r * y)
        r = r * (1.5f - r * r * y)
        return r.toFloat()
    }

    fun InvSqrt16(x: Float): Float { // inverse square root with 16 bits precision, returns huge number when x == 0.0f
        val seed = _flint(x)
        val a = seed.getInt()
        assert(initialized)
        val y = (x * 0.5f)
        seed.setInt(3 * EXP_BIAS - 1 - (a shr EXP_POS and 0xFF) shr 1 shl EXP_POS or iSqrt[a shr EXP_POS - LOOKUP_BITS and LOOKUP_MASK])
        var r = seed.f
        r = r * (1.5f - r * r * y)
        return r
    }

    fun InvSqrt64(x: Float): Float { // inverse square root with 64 bits precision, returns huge number when x == 0.0f
        val seed = _flint(x)
        val a = seed.getInt()
        assert(initialized)
        val y = (x * 0.5f)
        seed.setInt(3 * EXP_BIAS - 1 - (a shr EXP_POS and 0xFF) shr 1 shl EXP_POS or iSqrt[a shr EXP_POS - LOOKUP_BITS and LOOKUP_MASK])
        var r = seed.f
        r = r * (1.5f - r * r * y)
        r = r * (1.5f - r * r * y)
        r = r * (1.5f - r * r * y)
        return r
    }


    fun Sqrt(x: Float): Float { // square root with 32 bits precision
        return x * InvSqrt(x)
    }

    fun Sqrt16(x: Float): Float { // square root with 16 bits precision
        return x * InvSqrt16(x)
    }

    fun Sqrt64(x: Float): Float { // square root with 64 bits precision
        return x * InvSqrt64(x)
    }


    fun Sin(a: Float): Float {
        return sin(a)
    } // sine with 32 bits precision


    fun Sin16(a: Float): Float { // sine with 16 bits precision, maximum absolute error is 2.3082e-09
        var a = a
        val s: Float
        if (a < 0.0f || a >= TWO_PI) {
            a -= (floor((a / TWO_PI)) * TWO_PI)
        }
        if (a < PI) {
            if (a > HALF_PI) {
                a = PI - a
            }
        } else {
            a = if (a > PI + HALF_PI) {
                a - TWO_PI
            } else {
                PI - a
            }
        }

        s = a * a
        return a * (((((-2.39e-08f * s + 2.7526e-06f) * s - 1.98409e-04f) * s + 8.3333315e-03f) * s - 1.666666664e-01f) * s + 1.0f)
    }

    fun Sin64(a: Float): Float {
        return sin(a)
    } // sine with 64 bits precision


    fun Cos(a: Float): Float {
        return cos(a)
    } // cosine with 32 bits precision


    fun Cos16(a: Float): Float { // cosine with 16 bits precision, maximum absolute error is 2.3082e-09
        var a = a
        val s: Float
        val d: Float
        if (a < 0.0f || a >= TWO_PI) {
//		a -= floorf( a / TWO_PI ) * TWO_PI;
            a -= (floor((a / TWO_PI)) * TWO_PI)
        }
        //#if 1
        if (a < PI) {
            if (a > HALF_PI) {
                a = PI - a
                d = -1.0f
            } else {
                d = 1.0f
            }
        } else {
            if (a > PI + HALF_PI) {
                a = a - TWO_PI
                d = 1.0f
            } else {
                a = PI - a
                d = -1.0f
            }
        }
        s = a * a
        return d * (((((-2.605e-07f * s + 2.47609e-05f) * s - 1.3888397e-03f) * s + 4.16666418e-02f) * s - 4.999999963e-01f) * s + 1.0f)
    }

    fun Cos64(a: Float): Float {
        return cos(a)
    } // cosine with 64 bits precision


    fun SinCos(a: Float, s: CFloat, c: CFloat) { // sine and cosine with 32 bits precision
        s._val = sin(a)
        c._val = cos(a)
    }


    fun SinCos16(a: Float, s: CFloat, c: CFloat) { // sine and cosine with 16 bits precision
        var a = a
        val t: Float
        val d: Float
        if (a < 0.0f || a >= TWO_PI) {
            a -= (floor((a / TWO_PI)) * TWO_PI)
        }
        //#if 1
        if (a < PI) {
            if (a > HALF_PI) {
                a = PI - a
                d = -1.0f
            } else {
                d = 1.0f
            }
        } else {
            if (a > PI + HALF_PI) {
                a = a - TWO_PI
                d = 1.0f
            } else {
                a = PI - a
                d = -1.0f
            }
        }
        //#else
//	a = PI - a;
//	if ( fabs( a ) >= HALF_PI ) {
//		a = ( ( a < 0.0f ) ? -PI : PI ) - a;
//		d = 1.0f;
//	} else {
//		d = -1.0f;
//	}
//#endif
        t = a * a
        s._val =
            (a * (((((-2.39e-08f * t + 2.7526e-06f) * t - 1.98409e-04f) * t + 8.3333315e-03f) * t - 1.666666664e-01f) * t + 1.0f))
        c._val =
            (d * (((((-2.605e-07f * t + 2.47609e-05f) * t - 1.3888397e-03f) * t + 4.16666418e-02f) * t - 4.999999963e-01f) * t + 1.0f))
    }

    fun SinCos64(a: Float, s: CFloat, c: CFloat) { // sine and cosine with 64 bits precision
//#ifdef _WIN32
//	_asm {
//		fld		a
//		fsincos
//		mov		ecx, c
//		mov		edx, s
//		fstp	qword ptr [ecx]
//		fstp	qword ptr [edx]
//	}
//#else
        s._val = (sin(a))
        c._val = (cos(a))
        //#endif
    }


    fun Tan(a: Float): Float { // tangent with 32 bits precision
        return tan(a)
    }


    fun Tan16(a: Float): Float { // tangent with 16 bits precision, maximum absolute error is 1.8897e-08
        var a = a
        var s: Float
        val reciprocal: Boolean
        if (a < 0.0f || a >= PI) {
//		a -= floorf( a / PI ) * PI;
            a -= (floor((a / PI)) * PI)
        }
        //#if 1
        if (a < HALF_PI) {
            if (a > ONEFOURTH_PI) {
                a = HALF_PI - a
                reciprocal = true
            } else {
                reciprocal = false
            }
        } else {
            if (a > HALF_PI + ONEFOURTH_PI) {
                a = a - PI
                reciprocal = false
            } else {
                a = HALF_PI - a
                reciprocal = true
            }
        }
        //#else
//	a = HALF_PI - a;
//	if ( fabs( a ) >= ONEFOURTH_PI ) {
//		a = ( ( a < 0.0f ) ? -HALF_PI : HALF_PI ) - a;
//		reciprocal = false;
//	} else {
//		reciprocal = true;
//	}
//#endif
        s = a * a
        s =
            a * ((((((9.5168091e-03f * s + 2.900525e-03f) * s + 2.45650893e-02f) * s + 5.33740603e-02f) * s + 1.333923995e-01f) * s + 3.333314036e-01f) * s + 1.0f)
        return if (reciprocal) {
            1.0f / s
        } else {
            s
        }
    }

    fun Tan64(a: Float): Float { // tangent with 64 bits precision
        return tan(a)
    }

    fun ASin(a: Float): Float { // arc sine with 32 bits precision, input is clamped to [-1, 1] to avoid a silent NaN
        if (a <= -1.0f) {
            return -HALF_PI
        }
        return if (a >= 1.0f) {
            HALF_PI
        } else asin(a)
    }

    fun ASin16(a: Float): Float { // arc sine with 16 bits precision, maximum absolute error is 6.7626e-05
        var a = a
        return if (1 == FLOATSIGNBITSET(a)) {
            if (a <= -1.0f) {
                return -HALF_PI
            }
            a = abs(a)
            ((((-0.0187293f * a + 0.0742610f) * a - 0.2121144f) * a + 1.5707288f) * sqrt((1.0f - a)) - HALF_PI)
        } else {
            if (a >= 1.0f) {
                HALF_PI
            } else (HALF_PI - (((-0.0187293f * a + 0.0742610f) * a - 0.2121144f) * a + 1.5707288f) * sqrt((1.0f - a)))
        }
    }

    fun ASin64(a: Float): Float { // arc sine with 64 bits precision
        if (a <= -1.0f) {
            return -HALF_PI
        }
        return if (a >= 1.0f) {
            HALF_PI
        } else asin(a)
    }


    fun ACos(a: Float): Float { // arc cosine with 32 bits precision, input is clamped to [-1, 1] to avoid a silent NaN
        if (a <= -1.0f) {
            return PI
        }
        return if (a >= 1.0f) {
            0.0f
        } else acos(a)
    }

    fun ACos16(a: Float): Float { // arc cosine with 16 bits precision, maximum absolute error is 6.7626e-05
        var a = a
        return if (1 == FLOATSIGNBITSET(a)) {
            if (a <= -1.0f) {
                return PI
            }
            a = abs(a)
            (PI - (((-0.0187293f * a + 0.0742610f) * a - 0.2121144f) * a + 1.5707288f) * sqrt((1.0f - a)))
        } else {
            if (a >= 1.0f) {
                0.0f
            } else ((((-0.0187293f * a + 0.0742610f) * a - 0.2121144f) * a + 1.5707288f) * sqrt((1.0f - a)))
        }
    }

    fun ACos64(a: Float): Float { // arc cosine with 64 bits precision
        if (a <= -1.0f) {
            return PI
        }
        return if (a >= 1.0f) {
            0.0f
        } else acos(a)
    }

    fun ATan(a: Float): Float { // arc tangent with 32 bits precision
        return atan(a)
    }

    fun ATan16(a: Float): Float { // arc tangent with 16 bits precision, maximum absolute error is 1.3593e-08
        var a = a
        var s: Float
        return if (abs(a) > 1.0f) {
            a = 1.0f / a
            s = a * a
            s = -((((((((0.0028662257f * s - 0.0161657367f) * s + 0.0429096138f) * s - 0.0752896400f)
                    * s + 0.1065626393f) * s - 0.1420889944f) * s + 0.1999355085f) * s - 0.3333314528f) * s + 1.0f) * a
            if (1 == FLOATSIGNBITSET(a)) {
                s - HALF_PI
            } else {
                s + HALF_PI
            }
        } else {
            s = a * a
            ((((((((0.0028662257f * s - 0.0161657367f) * s + 0.0429096138f) * s - 0.0752896400f)
                    * s + 0.1065626393f) * s - 0.1420889944f) * s + 0.1999355085f) * s - 0.3333314528f) * s + 1.0f) * a
        }
    }

    fun ATan64(a: Float): Float { // arc tangent with 64 bits precision
        return atan(a)
    }

    fun ATan(y: Float, x: Float): Float { // arc tangent with 32 bits precision
        assert(abs(y) > FLT_SMALLEST_NON_DENORMAL || abs(x) > FLT_SMALLEST_NON_DENORMAL)
        return atan2(y, x)
    }

    fun ATan16(
        y: Float,
        x: Float
    ): Float { // arc tangent with 16 bits precision, maximum absolute error is 1.3593e-08
        assert(abs(y) > FLT_SMALLEST_NON_DENORMAL || abs(x) > FLT_SMALLEST_NON_DENORMAL)
        val a: Float
        var s: Float
        return if (abs(y) > abs(x)) {
            a = x / y
            s = a * a
            s = -((((((((0.0028662257f * s - 0.0161657367f) * s + 0.0429096138f) * s - 0.0752896400f)
                    * s + 0.1065626393f) * s - 0.1420889944f) * s + 0.1999355085f) * s - 0.3333314528f) * s + 1.0f) * a
            if (1 == FLOATSIGNBITSET(a)) {
                s - HALF_PI
            } else {
                s + HALF_PI
            }
        } else {
            a = y / x
            s = a * a
            ((((((((0.0028662257f * s - 0.0161657367f) * s + 0.0429096138f) * s - 0.0752896400f)
                    * s + 0.1065626393f) * s - 0.1420889944f) * s + 0.1999355085f) * s - 0.3333314528f) * s + 1.0f) * a
        }
    }

    fun ATan64(y: Float, x: Float): Float { // arc tangent with 64 bits precision
        return atan2(y, x)
    }

    fun Pow(x: Float, y: Float): Float { // x raised to the power y with 32 bits precision
        return x.pow(y)
    }

    fun Pow16(x: Float, y: Float): Float { // x raised to the power y with 16 bits precision
        return Exp16(y * Log16(x))
    }

    fun Pow64(x: Float, y: Float): Float { // x raised to the power y with 64 bits precision
        return x.pow(y)
    }

    fun Exp(f: Float): Float { // e raised to the power f with 32 bits precision
        return exp(f)
    }

    fun Exp16(f: Float): Float { // e raised to the power f with 16 bits precision
        var i: Int
        val s: Int
        val e: Int
        val m: Int
        val exponent: Int
        var x: Float
        val x2: Float
        var y: Float
        val p: Float
        val q: Float
        x = f * 1.44269504088896340f // multiply with ( 1 / log( 2 ) )
        i = x.toBits()
        s = i shr IEEE_FLT_SIGN_BIT
        e =
            (i shr IEEE_FLT_MANTISSA_BITS and (1 shl IEEE_FLT_EXPONENT_BITS) - 1) - IEEE_FLT_EXPONENT_BIAS
        m = i and (1 shl IEEE_FLT_MANTISSA_BITS) - 1 or (1 shl IEEE_FLT_MANTISSA_BITS)
        i = m shr IEEE_FLT_MANTISSA_BITS - e and (e shr 31).inv() xor s
        exponent = i + IEEE_FLT_EXPONENT_BIAS shl IEEE_FLT_MANTISSA_BITS
        y = Float.fromBits(exponent)
        x -= i
        if (x >= 0.5f) {
            x -= 0.5f
            y *= 1.4142135623730950488f // multiply with sqrt( 2 )
        }
        x2 = x * x
        p = x * (7.2152891511493f + x2 * 0.0576900723731f)
        q = 20.8189237930062f + x2
        x = y * (q + p) / (q - p)
        return x
    }

    fun Exp64(f: Float): Float { // e raised to the power f with 64 bits precision
        return exp(f)
    }

    fun Log(f: Float): Float { // natural logarithm with 32 bits precision
        return ln(f)
    }

    fun Log16(f: Float): Float { // natural logarithm with 16 bits precision
        var i: Int
        val exponent: Int
        var y: Float
        val y2: Float

        i = f.toBits()
        exponent =
            (i shr IEEE_FLT_MANTISSA_BITS and (1 shl IEEE_FLT_EXPONENT_BITS) - 1) - IEEE_FLT_EXPONENT_BIAS
        i -= exponent + 1 shl IEEE_FLT_MANTISSA_BITS // get value in the range [.5, 1>
        y = Float.fromBits(i)
        y *= 1.4142135623730950488f // multiply with sqrt( 2 )
        y = (y - 1.0f) / (y + 1.0f)
        y2 = y * y
        y =
            y * (2.000000000046727f + y2 * (0.666666635059382f + y2 * (0.4000059794795f + y2 * (0.28525381498f + y2 * 0.2376245609f))))
        y += 0.693147180559945f * (exponent + 0.5f)
        return y
    }

    fun Log64(f: Float): Float { // natural logarithm with 64 bits precision
        return ln(f)
    }

    fun IPow(x: Int, y: Int): Int { // integral x raised to the power y
        var y = y
        var r: Int
        r = x
        while (y > 1) {
            r *= x
            y--
        }
        return r
    }

    fun ILog2(f: Float): Int { // integral base-2 logarithm of the floating point value
        return (f.toBits() shr IEEE_FLT_MANTISSA_BITS and (1 shl IEEE_FLT_EXPONENT_BITS) - 1) - IEEE_FLT_EXPONENT_BIAS
    }

    fun ILog2(i: Int): Int { // integral base-2 logarithm of the integer value
        return ILog2(i.toFloat())
    }

    fun BitsForFloat(f: Float): Int { // minumum number of bits required to represent ceil( f )
        return ILog2(f) + 1
    }

    fun BitsForInteger(i: Int): Int { // minumum number of bits required to represent i
        return ILog2(i) + 1
    }

    fun MaskForFloatSign(f: Float): Int { // returns 0x00000000 if x >= 0.0f and returns 0xFFFFFFFF if x <= -0.0f
        return java.lang.Float.floatToIntBits(f) shr 31
    }

    fun MaskForIntegerSign(i: Int): Int { // returns 0x00000000 if x >= 0 and returns 0xFFFFFFFF if x < 0
        return i shr 31
    }

    fun FloorPowerOfTwo(x: Int): Int { // round x down to the nearest power of 2
        return CeilPowerOfTwo(x) shr 1
    }

    fun CeilPowerOfTwo(x: Int): Int { // round x up to the nearest power of 2
        var x = x
        x--
        x = x or (x shr 1)
        x = x or (x shr 2)
        x = x or (x shr 4)
        x = x or (x shr 8)
        x = x or (x shr 16)
        x++
        return x
    }

    fun IsPowerOfTwo(x: Int): Boolean { // returns true if x is a power of 2
        return x and x - 1 == 0 && x > 0
    }

    fun BitCount(x: Int): Int { // returns the number of 1 bits in x
        var x = x
        x -= x shr 1 and 0x55555555
        x = (x shr 2 and 0x33333333) + (x and 0x33333333)
        x = (x shr 4) + x and 0x0f0f0f0f
        x += x shr 8
        return x + (x shr 16) and 0x0000003f
    }

    fun BitReverse(x: Int): Int { // returns the bit reverse of x
        var x = x
        x = x shr 1 and 0x55555555 or (x and 0x55555555 shl 1)
        x = x shr 2 and 0x33333333 or (x and 0x33333333 shl 2)
        x = x shr 4 and 0x0f0f0f0f or (x and 0x0f0f0f0f shl 4)
        x = x shr 8 and 0x00ff00ff or (x and 0x00ff00ff shl 8)
        return x shr 16 or (x shl 16)
    }

    fun Abs(x: Int): Int { // returns the absolute value of the integer value (for reference only)
        val y = x shr 31
        return (x xor y) - y
    }

    fun Floor(f: Float): Float { // returns the largest integer that is less than or equal to the given value
        return floor(f)
    }

    fun Ceil(f: Float): Float { // returns the smallest integer that is greater than or equal to the given value
        return ceil(f)
    }

    fun Rint(f: Float): Float { // returns the nearest integer
        return floor((f + 0.5f))
    }


    fun Ftoi(f: Float): Int { // float to int conversion
        if (f.isNaN()) {
            return 0
        }
        return f.toInt()
    }

    // fast float to int conversion but uses current FPU round mode (default round nearest)
    fun FtoiFast(f: Float): Int {
        return if (f.isNaN()) 0 else f.toInt()
    }

    fun Ftol(f: Float): Long {
        if (f.isNaN()) {
            return 0L
        }
        return f.toLong()
    }


    fun ClampChar(i: Int): Char {
        if (i < -128) { //goddamn unsigned char!!
            return Char(-128)
        }
        return if (i > 127) {
            127.toChar()
        } else i.toChar()
    }

    fun ClampShort(i: Int): Short {
        if (i < -32768) {
            return -32768
        }
        return if (i > 32767) {
            32767
        } else i.toShort()
    }

    fun ClampInt(min: Int, max: Int, value: Int): Int {
        if (value < min) {
            return min
        }
        return if (value > max) {
            max
        } else value
    }


    fun ClampFloat(min: Float, max: Float, value: Float): Float {
        if (value < min) {
            return min
        }
        return if (value > max) {
            max
        } else value
    }

    fun AngleNormalize360(angle: Float): Float {
        var angle = angle
        if (angle >= 360.0f || angle < 0.0f) {
            angle -= (floor(angle / 360.0f) * 360.0f)
        }
        return angle
    }

    fun AngleNormalize180(angle: Float): Float {
        var angle = angle
        angle = AngleNormalize360(angle)
        if (angle > 180.0f) {
            angle -= 360.0f
        }
        return angle
    }

    fun AngleDelta(angle1: Float, angle2: Float): Float {
        return AngleNormalize180(angle1 - angle2)
    }

    fun FloatToBits(f: Float, exponentBits: Int, mantissaBits: Int): Int {
        var exponentBits = exponentBits
        val i: Int
        val sign: Int
        val exponent: Int
        val mantissa: Int
        var value: Int
        assert(exponentBits >= 2 && exponentBits <= 8)
        assert(mantissaBits >= 2 && mantissaBits <= 23)
        val maxBits = (1 shl exponentBits - 1) - 1 shl mantissaBits or (1 shl mantissaBits) - 1
        val minBits = (1 shl exponentBits) - 2 shl mantissaBits or 1
        val max = BitsToFloat(maxBits, exponentBits, mantissaBits)
        val min = BitsToFloat(minBits, exponentBits, mantissaBits)
        if (f >= 0.0f) {
            if (f >= max) {
                return maxBits
            } else if (f <= min) {
                return minBits
            }
        } else {
            if (f <= -max) {
                return maxBits or (1 shl exponentBits + mantissaBits)
            } else if (f >= -min) {
                return minBits or (1 shl exponentBits + mantissaBits)
            }
        }
        exponentBits--
        i = f.toBits() // TODO: Might be lossy conversion
        sign = i shr IEEE_FLT_SIGN_BIT and 1
        exponent =
            (i shr IEEE_FLT_MANTISSA_BITS and (1 shl IEEE_FLT_EXPONENT_BITS) - 1) - IEEE_FLT_EXPONENT_BIAS
        mantissa = i and (1 shl IEEE_FLT_MANTISSA_BITS) - 1
        value = sign shl 1 + exponentBits + mantissaBits
        value =
            value or (INTSIGNBITSET(exponent) shl exponentBits or (abs(exponent) and (1 shl exponentBits) - 1) shl mantissaBits)
        value = value or (mantissa shr IEEE_FLT_MANTISSA_BITS - mantissaBits)
        return value
    }

    fun BitsToFloat(i: Int, exponentBits: Int, mantissaBits: Int): Float {
        var exponentBits = exponentBits
        val exponentSign = intArrayOf(1, -1)
        val sign: Int
        val exponent: Int
        val mantissa: Int
        val value: Int
        assert(exponentBits >= 2 && exponentBits <= 8)
        assert(mantissaBits >= 2 && mantissaBits <= 23)
        exponentBits--
        sign = i shr 1 + exponentBits + mantissaBits
        exponent =
            (i shr mantissaBits and (1 shl exponentBits) - 1) * exponentSign[i shr exponentBits + mantissaBits and 1]
        mantissa = i and (1 shl mantissaBits) - 1 shl IEEE_FLT_MANTISSA_BITS - mantissaBits
        value =
            sign shl IEEE_FLT_SIGN_BIT or (exponent + IEEE_FLT_EXPONENT_BIAS shl IEEE_FLT_MANTISSA_BITS) or mantissa
        return Float.fromBits(value)
    }

    fun FloatHash(array: FloatArray, numFloats: Int): Int {
        var i: Int
        var hash = 0
        i = 0
        while (i < numFloats) {
            hash = hash xor array[i].toBits()
            i++
        }
        return hash
    }

    fun Fabs(f: Float): Float { // returns the absolute value of the floating point value
        return abs(f)
    }

    fun Frac(f: Float): Float { // f - Floor(f)
        return f - floor(f)
    }

    fun Ftoi8(f: Float): Byte { // float to char conversion
        val i = Ftoi(f)
        if (i < -128) {
            return -128
        }
        return if (i > 127) {
            127
        } else i.toByte()
    }

    fun Ftoi16(f: Float): Short { // float to short conversion
        val i = Ftoi(f)
        if (i < -32768) {
            return -32768
        }
        return if (i > 32767) {
            32767
        } else i.toShort()
    }

    fun Ftoui16(f: Float): Short { // float to unsigned short conversion
        val i = Ftoi(f)
        if (i < 0) {
            return 0
        }
        return if (i > 65535) {
            65535.toShort()
        } else i.toShort()
    }

    fun Ftob(f: Float): Byte { // float to byte conversion, the result is clamped to the range [0-255]
        val i = Ftoi(f)
        if (i < 0) {
            return 0
        }
        return if (i > 255) {
            255.toByte()
        } else i.toByte()
    }

    fun LerpToWithScale(
        cur: Float,
        dest: Float,
        scale: Float
    ): Float { // Lerps from cur to dest, scaling the delta to change by scale
        val delta = dest - cur
        if (delta > -1.0e-6f && delta < 1.0e-6f) {
            return dest
        }
        return cur + (dest - cur) * scale
    }

    internal class _flint {
        var f = 0.0f
        var i = 0

        constructor(i: Int) {
            setInt(i)
        }

        constructor(f: Float) {
            setFloat(f)
        }

        fun getInt(): Int {
            return i
        }

        fun setInt(i: Int) {
            this.i = i
            f = Float.fromBits(this.i)
        }

        fun getFloat(): Float {
            return f
        }

        fun setFloat(f: Float) {
            this.f = f
            i = f.toBits()
        }
    }
}

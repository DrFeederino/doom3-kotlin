package neo.idlib.math

/*
 ===============================================================================

 Complex number

 ===============================================================================
 */
class idComplex {
    var r = 0.0f // real part
    var i = 0.0f // imaginary part

    constructor()
    constructor(r: Float, i: Float) {
        this.r = r
        this.i = i
    }

    fun set(r: Float, i: Float) {
        this.r = r
        this.i = i
    }

    fun Zero() {
        r = 0.0f
        i = 0.0f
    }

    operator fun get(index: Int): Float {
        assert(index in 0..1)
        return if (0 == index) {
            r
        } else {
            i
        }
    }

    operator fun set(index: Int, value: Float) {
        assert(index in 0..1)
        when (index) {
            0 -> r = value
            1 -> i = value
        }
    }

    fun set(a: idComplex) {
        r = a.r
        i = a.i
    }

    operator fun unaryMinus(): idComplex {
        return idComplex(-r, -i)
    }

    operator fun times(a: idComplex): idComplex {
        return idComplex(r * a.r - i * a.i, i * a.r + r * a.i)
    }

    operator fun div(a: idComplex): idComplex {
        val s: Float
        val t: Float
        return if (idMath.Fabs(a.r) >= idMath.Fabs(a.i)) {
            s = a.i / a.r
            t = 1.0f / (a.r + s * a.i)
            idComplex((r + s * i) * t, (i - s * r) * t)
        } else {
            s = a.r / a.i
            t = 1.0f / (s * a.r + a.i)
            idComplex((r * s + i) * t, (i * s - r) * t)
        }
    }

    operator fun div(a: Float): idComplex {
        val s = 1.0f / a
        return idComplex(r * s, i * s)
    }

    operator fun plus(a: idComplex): idComplex {
        return idComplex(r + a.r, i + a.i)
    }

    operator fun minus(a: idComplex): idComplex {
        return idComplex(r - a.r, i - a.i)
    }

    operator fun timesAssign(a: idComplex) {
        this.set(idComplex(r * a.r - i * a.i, i * a.r + r * a.i))
    }

    operator fun divAssign(a: idComplex) {
        val s: Float
        val t: Float
        if (idMath.Fabs(a.r) >= idMath.Fabs(a.i)) {
            s = a.i / a.r
            t = 1.0f / (a.r + s * a.i)
            this.set(idComplex((r + s * i) * t, (i - s * r) * t))
        } else {
            s = a.r / a.i
            t = 1.0f / (s * a.r + a.i)
            this.set(idComplex((r * s + i) * t, (i * s - r) * t))
        }
    }

    operator fun plusAssign(a: idComplex) {
        r += a.r
        i += a.i
    }

    operator fun minusAssign(a: idComplex) {
        r -= a.r
        i -= a.i
    }

    operator fun times(a: Float): idComplex {
        return idComplex(r * a, i * a)
    }

    operator fun plus(a: Float): idComplex {
        return idComplex(r + a, i)
    }

    operator fun minus(a: Float): idComplex {
        return idComplex(r - a, i)
    }

    operator fun timesAssign(a: Float) {
        r *= a
        i *= a
    }

    operator fun divAssign(a: Float) {
        val s = 1.0f / a
        r *= s
        i *= s
    }

    operator fun plusAssign(a: Float) {
        r += a
    }

    operator fun minusAssign(a: Float) {
        r -= a
    }

    fun Compare(a: idComplex): Boolean { // exact compare, no epsilon
        return r == a.r && i == a.i
    }

    fun Compare(a: idComplex, epsilon: Float): Boolean { // compare with epsilon
        return if (idMath.Fabs(r - a.r) > epsilon) {
            false
        } else idMath.Fabs(i - a.i) <= epsilon
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) return true
        if (other !is idComplex) return false
        return Compare(other)
    }

    fun equals(a: idComplex): Boolean { // exact compare, no epsilon
        return Compare(a)
    }

    fun notEquals(a: idComplex): Boolean { // exact compare, no epsilon
        return !Compare(a)
    }

    override fun hashCode(): Int {
        var hash = 7
        hash = 29 * hash + r.toBits()
        hash = 29 * hash + i.toBits()
        return hash
    }

    fun Reciprocal(): idComplex {
        val s: Float
        val t: Float
        return if (idMath.Fabs(r) >= idMath.Fabs(i)) {
            s = i / r
            t = 1.0f / (r + s * i)
            idComplex(t, -s * t)
        } else {
            s = r / i
            t = 1.0f / (s * r + i)
            idComplex(s * t, -t)
        }
    }

    fun Sqrt(): idComplex {
        var w: Float
        if (r == 0.0f && i == 0.0f) {
            return idComplex(0.0f, 0.0f)
        }
        val x: Float = idMath.Fabs(r)
        val y: Float = idMath.Fabs(i)
        if (x >= y) {
            w = y / x
            w = idMath.Sqrt(x) * idMath.Sqrt(0.5f * (1.0f + idMath.Sqrt(1.0f + w * w)))
        } else {
            w = x / y
            w = idMath.Sqrt(y) * idMath.Sqrt(0.5f * (w + idMath.Sqrt(1.0f + w * w)))
        }
        if (w == 0.0f) {
            return idComplex(0.0f, 0.0f)
        }
        return if (r >= 0.0f) {
            idComplex(w, 0.5f * i / w)
        } else {
            idComplex(0.5f * y / w, if (i >= 0.0f) w else -w)
        }
    }

    fun Abs(): Float {
        val t: Float
        val x: Float = idMath.Fabs(r)
        val y: Float = idMath.Fabs(i)
        return if (x == 0.0f) {
            y
        } else if (y == 0.0f) {
            x
        } else if (x > y) {
            t = y / x
            x * idMath.Sqrt(1.0f + t * t)
        } else {
            t = x / y
            y * idMath.Sqrt(1.0f + t * t)
        }
    }

    fun GetDimension(): Int {
        return 2
    }

    fun ToFloatPtr(): FloatArray {
        return floatArrayOf(r, i)
    }

    fun ToString(precision: Int = 2): String {
        return String.format("(%." + precision + "f, %." + precision + "fi)", r, i)
    }
}

// Global variable to match C++ extern declaration
val complex_origin: idComplex = idComplex(0.0f, 0.0f)
val complex_zero: idComplex = complex_origin // Alias for compatibility

// Global operator functions to match C++ friend functions
operator fun Float.times(b: idComplex): idComplex {
    return idComplex(this * b.r, this * b.i)
}

operator fun Float.div(b: idComplex): idComplex {
    val s: Float
    val t: Float
    return if (idMath.Fabs(b.r) >= idMath.Fabs(b.i)) {
        s = b.i / b.r
        t = this / (b.r + s * b.i)
        idComplex(t, -s * t)
    } else {
        s = b.r / b.i
        t = this / (s * b.r + b.i)
        idComplex(s * t, -t)
    }
}

operator fun Float.plus(b: idComplex): idComplex {
    return idComplex(this + b.r, b.i)
}

operator fun Float.minus(b: idComplex): idComplex {
    return idComplex(this - b.r, -b.i)
}
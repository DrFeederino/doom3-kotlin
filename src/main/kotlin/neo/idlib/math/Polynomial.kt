package neo.idlib.math

import neo.idlib.math.idMath.Cos
import neo.idlib.math.idMath.Exp
import neo.idlib.math.idMath.Log
import neo.idlib.math.idMath.Sin
import kotlin.math.abs
import kotlin.math.max

const val EPSILON = 1e-6f

/*
 ===============================================================================

 Polynomial of arbitrary degree with real coefficients.

 ===============================================================================
 */
class idPolynomial {
    private var allocated: Int
    private var coefficient: FloatArray = FloatArray(0)
    private var degree: Int

    constructor() {
        degree = -1
        allocated = 0
    }

    constructor(d: Int) {
        degree = -1
        allocated = 0
        Resize(d, false)
    }

    constructor(a: Float, b: Float) {
        degree = -1
        allocated = 0
        Resize(1, false)
        coefficient[0] = b
        coefficient[1] = a
    }

    constructor(a: Float, b: Float, c: Float) {
        degree = -1
        allocated = 0
        Resize(2, false)
        coefficient[0] = c
        coefficient[1] = b
        coefficient[2] = a
    }

    constructor(a: Float, b: Float, c: Float, d: Float) {
        degree = -1
        allocated = 0
        Resize(3, false)
        coefficient[0] = d
        coefficient[1] = c
        coefficient[2] = b
        coefficient[3] = a
    }

    constructor(a: Float, b: Float, c: Float, d: Float, e: Float) {
        degree = -1
        allocated = 0
        Resize(4, false)
        coefficient[0] = e
        coefficient[1] = d
        coefficient[2] = c
        coefficient[3] = b
        coefficient[4] = a
    }

    constructor(p: idPolynomial) {
        allocated = p.allocated
        coefficient = FloatArray(p.coefficient.size)
        System.arraycopy(p.coefficient, 0, coefficient, 0, p.coefficient.size)
        degree = p.degree
    }

    operator fun get(index: Int): Float {
        assert(index in 0..degree)
        return coefficient[index]
    }

    operator fun set(index: Int, value: Float) {
        assert(index in 0..degree)
        coefficient[index] = value
    }

    operator fun unaryMinus(): idPolynomial {
        var i: Int
        val n = idPolynomial(this)
        i = 0
        while (i <= degree) {
            n.coefficient[i] = -n.coefficient[i]
            i++
        }
        return n
    }

    fun set(p: idPolynomial): idPolynomial {
        Resize(p.degree, false)
        System.arraycopy(p.coefficient, 0, coefficient, 0, degree + 1)
        return this
    }

    operator fun plus(p: idPolynomial): idPolynomial {
        var i: Int
        val n = idPolynomial()
        if (degree > p.degree) {
            n.Resize(degree, false)
            i = 0
            while (i <= p.degree) {
                n.coefficient[i] = coefficient[i] + p.coefficient[i]
                i++
            }
            while (i <= degree) {
                n.coefficient[i] = coefficient[i]
                i++
            }
            n.degree = degree
        } else if (p.degree > degree) {
            n.Resize(p.degree, false)
            i = 0
            while (i <= degree) {
                n.coefficient[i] = coefficient[i] + p.coefficient[i]
                i++
            }
            while (i <= p.degree) {
                n.coefficient[i] = p.coefficient[i]
                i++
            }
            n.degree = p.degree
        } else {
            n.Resize(degree, false)
            n.degree = 0
            i = 0
            while (i <= degree) {
                n.coefficient[i] = coefficient[i] + p.coefficient[i]
                if (n.coefficient[i] != 0.0f) {
                    n.degree = i
                }
                i++
            }
        }
        return n
    }

    operator fun minus(p: idPolynomial): idPolynomial {
        var i: Int
        val n = idPolynomial()
        if (degree > p.degree) {
            n.Resize(degree, false)
            i = 0
            while (i <= p.degree) {
                n.coefficient[i] = coefficient[i] - p.coefficient[i]
                i++
            }
            while (i <= degree) {
                n.coefficient[i] = coefficient[i]
                i++
            }
            n.degree = degree
        } else if (p.degree >= degree) {
            n.Resize(p.degree, false)
            i = 0
            while (i <= degree) {
                n.coefficient[i] = coefficient[i] - p.coefficient[i]
                i++
            }
            while (i <= p.degree) {
                n.coefficient[i] = -p.coefficient[i]
                i++
            }
            n.degree = p.degree
        } else {
            n.Resize(degree, false)
            n.degree = 0
            i = 0
            while (i <= degree) {
                n.coefficient[i] = coefficient[i] - p.coefficient[i]
                if (n.coefficient[i] != 0.0f) {
                    n.degree = i
                }
                i++
            }
        }
        return n
    }

    operator fun times(s: Float): idPolynomial {
        val n = idPolynomial()
        if (s == 0.0f) {
            n.degree = 0
        } else {
            n.Resize(degree, false)
            for (i in 0..degree) {
                n.coefficient[i] = coefficient[i] * s
            }
        }
        return n
    }

    operator fun div(s: Float): idPolynomial {
        val invs: Float
        val n = idPolynomial()
        assert(s != 0.0f)
        n.Resize(degree, false)
        invs = 1.0f / s
        for (i in 0..degree) {
            n.coefficient[i] = coefficient[i] * invs
        }
        return n
    }

    fun plusAssign(p: idPolynomial): idPolynomial {
        var i: Int
        if (degree > p.degree) {
            i = 0
            while (i <= p.degree) {
                coefficient[i] += p.coefficient[i]
                i++
            }
        } else if (p.degree > degree) {
            Resize(p.degree, true)
            i = 0
            while (i <= degree) {
                coefficient[i] += p.coefficient[i]
                i++
            }
            while (i <= p.degree) {
                coefficient[i] = p.coefficient[i]
                i++
            }
        } else {
            i = 0
            while (i <= degree) {
                coefficient[i] += p.coefficient[i]
                if (coefficient[i] != 0.0f) {
                    degree = i
                }
                i++
            }
        }
        return this
    }

    fun minusAssign(p: idPolynomial): idPolynomial {
        var i: Int
        if (degree > p.degree) {
            i = 0
            while (i <= p.degree) {
                coefficient[i] -= p.coefficient[i]
                i++
            }
        } else if (p.degree > degree) {
            Resize(p.degree, true)
            i = 0
            while (i <= degree) {
                coefficient[i] -= p.coefficient[i]
                i++
            }
            while (i <= p.degree) {
                coefficient[i] = -p.coefficient[i]
                i++
            }
        } else {
            i = 0
            while (i <= degree) {
                coefficient[i] -= p.coefficient[i]
                if (coefficient[i] != 0.0f) {
                    degree = i
                }
                i++
            }
        }
        return this
    }

    fun timesAssign(s: Float): idPolynomial {
        if (s == 0.0f) {
            degree = 0
        } else {
            for (i in 0..degree) {
                coefficient[i] *= s
            }
        }
        return this
    }

    fun divAssign(s: Float): idPolynomial {
        assert(s != 0.0f)
        val invs = 1.0f / s
        for (i in 0..degree) {
            coefficient[i] *= invs  // Fixed: was setting coefficient[i] = invs instead of multiplying
        }
        return this
    }

    fun Compare(p: idPolynomial): Boolean { // exact compare, no epsilon
        if (degree != p.degree) {
            return false
        }
        for (i in 0..degree) {
            if (coefficient[i] != p.coefficient[i]) {
                return false
            }
        }
        return true
    }

    fun Compare(p: idPolynomial, epsilon: Float): Boolean { // compare with epsilon
        if (degree != p.degree) {
            return false
        }
        for (i in 0..degree) {
            if (abs(coefficient[i] - p.coefficient[i]) > epsilon) {
                return false
            }
        }
        return true
    }

    fun equals(p: idPolynomial): Boolean { // exact compare, no epsilon
        return Compare(p)
    }

    fun notEquals(p: idPolynomial): Boolean { // exact compare, no epsilon
        return !Compare(p)
    }

    override fun hashCode(): Int {
        var hash = 7
        hash = 43 * hash + degree
        hash = 43 * hash + coefficient.contentHashCode()
        return hash
    }

    override fun equals(obj: Any?): Boolean {
        if (obj == null) {
            return false
        }
        if (javaClass != obj.javaClass) {
            return false
        }
        val other = obj as idPolynomial
        return Compare(other)
    }

    fun Zero() {
        degree = 0
    }

    fun Zero(d: Int) {
        Resize(d, false)
        for (i in 0..degree) {
            coefficient[i] = 0.0f
        }
    }

    fun GetDimension(): Int { // get the degree of the polynomial
        return degree
    }

    fun GetDegree(): Int { // get the degree of the polynomial
        return degree
    }

    fun GetValue(x: Float): Float { // evaluate the polynomial with the given real value
        var y: Float
        var z: Float
        y = coefficient[0]
        z = x
        for (i in 1..degree) {
            y += coefficient[i] * z
            z *= x
        }
        return y
    }

    fun GetValue(x: idComplex): idComplex { // evaluate the polynomial with the given complex value
        val y = idComplex()
        val z = idComplex()
        y.set(coefficient[0], 0.0f)
        z.set(x)
        for (i in 1..degree) {
            y.plusAssign(z * coefficient[i])
            z.timesAssign(x)
        }
        return y
    }

    fun GetDerivative(): idPolynomial { // get the first derivative of the polynomial
        val n = idPolynomial()
        if (degree == 0) {
            return n
        }
        n.Resize(degree - 1, false)
        for (i in 1..degree) {
            n.coefficient[i - 1] = i * coefficient[i]
        }
        return n
    }

    fun GetAntiDerivative(): idPolynomial { // get the anti derivative of the polynomial
        val n = idPolynomial()
        if (degree == 0) {
            return n
        }
        n.Resize(degree + 1, false)
        n.coefficient[0] = 0.0f
        for (i in 0..degree) {
            n.coefficient[i + 1] = coefficient[i] / (i + 1)
        }
        return n
    }

    fun ToFloatPtr(): FloatArray {
        return coefficient
    }

    fun ToString(precision: Int = 2): String {
        var result = ""
        for (i in 0..degree) {
            if (i > 0) result += " "
            result += String.format("%." + precision + "f", coefficient[i])
        }
        return result
    }

    fun GetRoots(roots: Array<idComplex>): Int { // get all roots
        var i: Int
        var j: Int
        val x = idComplex()
        val b = idComplex()
        val c = idComplex()
        val coef: Array<idComplex>
        coef = Array(degree + 1) { idComplex() }
        i = 0
        while (i <= degree) {
            coef[i] = idComplex(coefficient[i], 0.0f)
            i++
        }
        i = degree - 1
        while (i >= 0) {
            x.Zero()
            Laguer(coef, i + 1, x)
            if (abs(x.i) < 2.0f * EPSILON * abs(x.r)) {
                x.i = 0.0f
            }
            roots[i].set(x)
            b.set(coef[i + 1])
            j = i
            while (j >= 0) {
                c.set(coef[j])
                coef[j].set(b)
                b.set(x * b + c)
                j--
            }
            i--
        }
        i = 0
        while (i <= degree) {
            coef[i].set(coefficient[i], 0.0f)
            i++
        }
        i = 0
        while (i < degree) {
            Laguer(coef, degree, roots[i])
            i++
        }
        i = 1
        while (i < degree) {
            x.set(roots[i])
            j = i - 1
            while (j >= 0) {
                if (roots[j].r <= x.r) {
                    break
                }
                roots[j + 1].set(roots[j])
                j--
            }
            roots[j + 1].set(x)
            i++
        }
        return degree
    }

    fun GetRoots(roots: FloatArray): Int { // get the real roots
        var i: Int
        var num: Int
        val complexRoots: Array<idComplex>
        when (degree) {
            0 -> return 0
            1 -> return GetRoots1(coefficient[1], coefficient[0], roots)
            2 -> return GetRoots2(coefficient[2], coefficient[1], coefficient[0], roots)
            3 -> return GetRoots3(
                coefficient[3], coefficient[2], coefficient[1], coefficient[0], roots
            )

            4 -> return GetRoots4(
                coefficient[4], coefficient[3], coefficient[2], coefficient[1], coefficient[0], roots
            )
        }

        // The Abel-Ruffini theorem states that there is no general solution
        // in radicals to polynomial equations of degree five or higher.
        // A polynomial equation can be solved by radicals if and only if
        // its Galois group is a solvable group.

        complexRoots = Array(degree) { idComplex() }
        GetRoots(complexRoots)
        num = 0.also { i = it }
        while (i < degree) {
            if (complexRoots[i].i == 0.0f) {
                roots[i] = complexRoots[i].r
                num++
            }
            i++
        }
        return num
    }

    private fun Resize(d: Int, keep: Boolean) {
        val alloc = d + 1 + 3 and 3.inv()
        if (alloc > allocated) {
            val ptr = FloatArray(alloc)
            if (coefficient.isNotEmpty()) {
                if (keep) {
                    System.arraycopy(coefficient, 0, ptr, 0, degree + 1)
                }
            }
            allocated = alloc
            coefficient = ptr
        }
        degree = d
    }

    private fun Laguer(coef: Array<idComplex>, degree: Int, x: idComplex): Int {
        val MT = 10
        val MAX_ITERATIONS = MT * 8
        val frac = floatArrayOf(0.0f, 0.5f, 0.25f, 0.75f, 0.13f, 0.38f, 0.62f, 0.88f, 1.0f)
        var i: Int
        var j: Int
        var abx: Float
        var abp: Float
        var abm: Float
        var err: Float
        val dx = idComplex()
        val cx = idComplex()
        val b = idComplex()
        val d = idComplex()
        val f = idComplex()
        val g = idComplex()
        val s = idComplex()
        val gps = idComplex()
        val gms = idComplex()
        val g2 = idComplex()
        i = 1
        while (i <= MAX_ITERATIONS) {
            b.set(coef[degree])
            err = b.Abs()
            d.Zero()
            f.Zero()
            abx = x.Abs()
            j = degree - 1
            while (j >= 0) {
                f.set(x * f + d)
                d.set(x * d + b)
                b.set(x * b + coef[j])
                err = b.Abs() + abx * err
                j--
            }
            if (b.Abs() < err * EPSILON) {
                return i
            }
            g.set(d / b)
            g2.set(g * g) //s.set((((g2 - f * 2.0f / b) * degree - g2) * (degree - 1)).Sqrt())
            s.set(((degree.toFloat() - 1) * (degree.toFloat() * (g2 - 2.0f * f / b) - g2)).Sqrt())
            gps.set(g + s)
            gms.set(g - s)
            abp = gps.Abs()
            abm = gms.Abs()
            if (abp < abm) {
                gps.set(gms)
            }
            if (max(abp, abm) > 0.0f) {
                dx.set(degree.toFloat() / gps)
            } else {
                dx.set(idComplex(Cos(i.toFloat()), Sin(i.toFloat())) * Exp(Log(1.0f + abx)))
            }
            cx.set(x - dx)
            if (x == cx) {
                return i
            }
            if (i % MT == 0) {
                x.set(cx)
            } else {
                x.minusAssign(dx * frac[i / MT])
            }
            i++
        }
        return i
    }

    companion object {
        fun GetRoots1(a: Float, b: Float, roots: FloatArray): Int {
            assert(a != 0.0f)
            roots[0] = -b / a
            return 1
        }

        fun GetRoots2(a: Float, b: Float, c: Float, roots: FloatArray): Int {
            var b = b
            var c = c
            val inva: Float
            var ds: Float
            if (a != 1.0f) {
                assert(a != 0.0f)
                inva = 1.0f / a
                c *= inva
                b *= inva
            }
            ds = b * b - 4.0f * c
            return if (ds < 0.0f) {
                0
            } else if (ds > 0.0f) {
                ds = idMath.Sqrt(ds)
                roots[0] = 0.5f * (-b - ds)
                roots[1] = 0.5f * (-b + ds)
                2
            } else {
                roots[0] = 0.5f * -b
                1
            }
        }

        fun GetRoots3(a: Float, b: Float, c: Float, d: Float, roots: FloatArray): Int {
            var b = b
            var c = c
            var d = d
            val inva: Float
            val f: Float
            val g: Float
            val halfg: Float
            val ofs: Float
            var ds: Float
            val dist: Float
            val angle: Float
            val cs: Float
            val ss: Float
            var t: Float
            if (a != 1.0f) {
                assert(a != 0.0f)
                inva = 1.0f / a
                d *= inva
                c *= inva
                b *= inva
            }
            f = 1.0f / 3.0f * (3.0f * c - b * b)
            g = 1.0f / 27.0f * (2.0f * b * b * b - 9.0f * c * b + 27.0f * d)
            halfg = 0.5f * g
            ofs = 1.0f / 3.0f * b
            ds = 0.25f * g * g + 1.0f / 27.0f * f * f * f
            return if (ds < 0.0f) {
                dist = idMath.Sqrt(-1.0f / 3.0f * f)
                angle = 1.0f / 3.0f * idMath.ATan(idMath.Sqrt(-ds), -halfg)
                cs = Cos(angle)
                ss = Sin(angle)
                roots[0] = 2.0f * dist * cs - ofs
                roots[1] = -dist * (cs + idMath.SQRT_THREE * ss) - ofs
                roots[2] = -dist * (cs - idMath.SQRT_THREE * ss) - ofs
                3
            } else if (ds > 0.0f) {
                ds = idMath.Sqrt(ds)
                t = -halfg + ds
                if (t >= 0.0f) {
                    roots[0] = idMath.Pow(t, 1.0f / 3.0f)
                } else {
                    roots[0] = -idMath.Pow(-t, 1.0f / 3.0f)
                }
                t = -halfg - ds
                if (t >= 0.0f) {
                    roots[0] += idMath.Pow(t, 1.0f / 3.0f)
                } else {
                    roots[0] -= idMath.Pow(-t, 1.0f / 3.0f)
                }
                roots[0] -= ofs
                1
            } else {
                t = if (halfg >= 0.0f) {
                    -idMath.Pow(halfg, 1.0f / 3.0f)
                } else {
                    idMath.Pow(-halfg, 1.0f / 3.0f)
                }
                roots[0] = 2.0f * t - ofs
                roots[1] = -t - ofs
                roots[2] = roots[1]
                3
            }
        }

        fun GetRoots4(a: Float, b: Float, c: Float, d: Float, e: Float, roots: FloatArray): Int {
            var b = b
            var c = c
            var d = d
            var e = e
            var count: Int
            val inva: Float
            val y: Float
            val ds: Float
            val r: Float
            val s1: Float
            val s2: Float
            val t1: Float
            var t2: Float
            val tp: Float
            val tm: Float
            val roots3 = FloatArray(3)
            if (a != 1.0f) {
                assert(a != 0.0f)
                inva = 1.0f / a
                e *= inva
                d *= inva
                c *= inva
                b *= inva
            }
            count = 0
            GetRoots3(1.0f, -c, b * d - 4.0f * e, -b * b * e + 4.0f * c * e - d * d, roots3)
            y = roots3[0]
            ds = 0.25f * b * b - c + y
            return if (ds < 0.0f) {
                0
            } else if (ds > 0.0f) {
                r = idMath.Sqrt(ds)
                t1 = 0.75f * b * b - r * r - 2.0f * c
                t2 = (4.0f * b * c - 8.0f * d - b * b * b) / (4.0f * r)
                tp = t1 + t2
                tm = t1 - t2
                if (tp >= 0.0f) {
                    s1 = idMath.Sqrt(tp)
                    roots[count++] = -0.25f * b + 0.5f * (r + s1)
                    roots[count++] = -0.25f * b + 0.5f * (r - s1)
                }
                if (tm >= 0.0f) {
                    s2 = idMath.Sqrt(tm)
                    roots[count++] = -0.25f * b + 0.5f * (s2 - r)
                    roots[count++] = -0.25f * b - 0.5f * (s2 + r)
                }
                count
            } else {
                t2 = y * y - 4.0f * e
                if (t2 >= 0.0f) {
                    t2 = 2.0f * idMath.Sqrt(t2)
                    t1 = 0.75f * b * b - 2.0f * c
                    if (t1 + t2 >= 0.0f) {
                        s1 = idMath.Sqrt(t1 + t2)
                        roots[count++] = -0.25f * b + 0.5f * s1
                        roots[count++] = -0.25f * b - 0.5f * s1
                    }
                    if (t1 - t2 >= 0.0f) {
                        s2 = idMath.Sqrt(t1 - t2)
                        roots[count++] = -0.25f * b + 0.5f * s2
                        roots[count++] = -0.25f * b - 0.5f * s2
                    }
                }
                count
            }
        }

        fun Test() {
            var i: Int
            var num: Int
            val roots = FloatArray(4)
            var value: Float
            val complexRoots = Array(4) { idComplex() }
            val complexValue = idComplex()
            var p: idPolynomial
            p = idPolynomial(-5.0f, 4.0f)
            num = p.GetRoots(roots)
            i = 0
            while (i < num) {
                value = p.GetValue(roots[i])
                assert(abs(value) < 1e-4f)
                i++
            }
            p = idPolynomial(-5.0f, 4.0f, 3.0f)
            num = p.GetRoots(roots)
            i = 0
            while (i < num) {
                value = p.GetValue(roots[i])
                assert(abs(value) < 1e-4f)
                i++
            }
            p = idPolynomial(1.0f, 4.0f, 3.0f, -2.0f)
            num = p.GetRoots(roots)
            i = 0
            while (i < num) {
                value = p.GetValue(roots[i])
                assert(abs(value) < 1e-4f)
                i++
            }
            p = idPolynomial(5.0f, 4.0f, 3.0f, -2.0f)
            num = p.GetRoots(roots)
            i = 0
            while (i < num) {
                value = p.GetValue(roots[i])
                assert(abs(value) < 1e-4f)
                i++
            }
            p = idPolynomial(-5.0f, 4.0f, 3.0f, 2.0f, 1.0f)
            num = p.GetRoots(roots)
            i = 0
            while (i < num) {
                value = p.GetValue(roots[i])
                assert(abs(value) < 1e-4f)
                i++
            }
            p = idPolynomial(1.0f, 4.0f, 3.0f, -2.0f)
            num = p.GetRoots(complexRoots)
            i = 0
            while (i < num) {
                complexValue.set(p.GetValue(complexRoots[i]))
                assert(abs(complexValue.r) < 1e-4f && abs(complexValue.i) < 1e-4f)
                i++
            }
            p = idPolynomial(5.0f, 4.0f, 3.0f, -2.0f)
            num = p.GetRoots(complexRoots)
            i = 0
            while (i < num) {
                complexValue.set(p.GetValue(complexRoots[i]))
                assert(abs(complexValue.r) < 1e-4f && abs(complexValue.i) < 1e-4f)
                i++
            }
        }
    }
}

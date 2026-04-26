package neo.idlib.math

import neo.idlib.containers.List.idFloatList
import neo.idlib.containers.List.idList
import neo.idlib.math.Matrix.idMatX

/*
 ===============================================================================

 Curve base template.

 ===============================================================================
 */
open class idCurve<T : idVec<T>>(private val factory: () -> T) {
    protected var changed: Boolean
    protected var currentIndex: Int // cached index for fast lookup
    protected val times: idFloatList = idFloatList() // knots
    protected val values: idList<T> = idList() // knot values

    /*
     ====================
     idCurve::AddValue

     add a timed/value pair to the spline
     returns the index to the inserted pair
     ====================
     */
    open fun AddValue(time: Float, value: T): Int {
        val i: Int
        i = IndexForTime(time)
        times.Insert(time, i)
        values.Insert(value, i)
        changed = true
        return i
    }

    open fun RemoveIndex(index: Int) {
        values.RemoveIndex(index)
        times.RemoveIndex(index)
        changed = true
    }

    open fun Clear() {
        values.Clear()
        times.Clear()
        currentIndex = -1
        changed = true
    }

    /*
     ====================
     idCurve::GetCurrentValue

     get the value for the given time
     ====================
     */
    open fun GetCurrentValue(time: Float): T {
        val i: Int
        i = IndexForTime(time)
        return if (i >= values.Num()) {
            values[values.Num() - 1]
        } else {
            values[i]
        }
    }

    /*
     ====================
     idCurve::GetCurrentFirstDerivative

     get the first derivative for the given time
     ====================
     */
    open fun GetCurrentFirstDerivative(time: Float): T {
        return values[0] - values[0]
    }

    /*
     ====================
     idCurve::GetCurrentSecondDerivative

     get the second derivative for the given time
     ====================
     */
    open fun GetCurrentSecondDerivative(time: Float): T {
        return values[0] - values[0]
    }

    open fun IsDone(time: Float): Boolean {
        return time >= times[times.Num() - 1]
    }

    fun GetNumValues(): Int {
        return values.Num()
    }

    fun SetValue(index: Int, value: T) {
        values[index] = value
        changed = true
    }

    fun GetValue(index: Int): T {
        return values[index]
    }

    fun GetValueAddress(index: Int): T {
        return values[index]
    }

    fun GetTime(index: Int): Float {
        return times[index]
    }

    fun GetLengthForTime(time: Float): Float {
        var length = 0.0f
        val index = IndexForTime(time)
        for (i in 0 until index) {
            length += RombergIntegral(times[i], times[i + 1], 5)
        }
        length += RombergIntegral(times[index], time, 5)
        return length
    }


    fun GetTimeForLength(length: Float, epsilon: Float = 0.1f): Float {
        var i: Int
        var index: Int
        val accumLength: FloatArray
        var totalLength: Float
        val len0: Float
        val len1: Float
        var t: Float
        var diff: Float
        if (length <= 0.0f) {
            return times[0]
        }
        accumLength = FloatArray(values.Num())
        totalLength = 0.0f
        index = 0
        while (index < values.Num() - 1) {
            totalLength += GetLengthBetweenKnots(index, index + 1)
            accumLength[index] = totalLength
            if (length < accumLength[index]) {
                break
            }
            index++
        }
        if (index >= values.Num() - 1) {
            return times[times.Num() - 1]
        }
        if (index == 0) {
            len0 = length
            len1 = accumLength[0]
        } else {
            len0 = length - accumLength[index - 1]
            len1 = accumLength[index] - accumLength[index - 1]
        }

        // invert the arc length integral using Newton's method
        t = (times[index + 1] - times[index]) * len0 / len1
        i = 0
        while (i < 32) {
            diff = RombergIntegral(times[index], times[index] + t, 5) - len0
            if (Math.abs(diff) <= epsilon) {
                return times[index] + t
            }
            t -= diff / GetSpeed(times[index] + t)
            i++
        }
        return times[index] + t
    }

    fun GetLengthBetweenKnots(i0: Int, i1: Int): Float {
        var length = 0.0f
        for (i in i0 until i1) {
            length += RombergIntegral(times[i], times[i + 1], 5)
        }
        return length
    }

    fun MakeUniform(totalTime: Float) {
        var i: Int
        val n: Int
        n = times.Num() - 1
        i = 0
        while (i <= n) {
            times[i] = i * totalTime / n
            i++
        }
        changed = true
    }

    fun SetConstantSpeed(totalTime: Float) {
        var i: Int
        val length: FloatArray
        var totalLength: Float
        val scale: Float
        var t: Float
        length = FloatArray(values.Num())
        totalLength = 0.0f
        i = 0
        while (i < values.Num() - 1) {
            length[i] = GetLengthBetweenKnots(i, i + 1)
            totalLength += length[i]
            i++
        }
        scale = totalTime / totalLength
        t = 0.0f
        i = 0
        while (i < times.Num() - 1) {
            times[i] = t
            t += scale * length[i]
            i++
        }
        times[times.Num() - 1] = totalTime
        changed = true
    }

    fun ShiftTime(deltaTime: Float) {
        for (i in 0 until times.Num()) {
            times[i] = times[i] + deltaTime
        }
        changed = true
    }

    fun Translate(translation: T) {
        for (i in 0 until values.Num()) {
            values[i] = values[i] + translation

        }
        changed = true
    } // set whenever the curve changes

    /*
     ====================
     idCurve::IndexForTime

     find the index for the first time greater than or equal to the given time
     ====================
     */
    protected fun IndexForTime(time: Float): Int {
        var len: Int
        var mid: Int
        var offset: Int
        var res: Int
        if (currentIndex >= 0 && currentIndex <= times.Num()) {
            // use the cached index if it is still valid
            if (currentIndex == 0) {
                if (time <= times[currentIndex]) {
                    return currentIndex
                }
            } else if (currentIndex == times.Num()) {
                if (time > times[currentIndex - 1]) {
                    return currentIndex
                }
            } else if (time > times[currentIndex - 1] && time <= times[currentIndex]) {
                return currentIndex
            } else if (time > times[currentIndex] && (currentIndex + 1 == times.Num() || time <= times[currentIndex + 1])
            ) {
                // use the next index
                currentIndex++
                return currentIndex
            }
        }
        // use binary search to find the index for the given time
        len = times.Num()
        mid = len
        offset = 0
        res = 0
        while (mid
            > 0
        ) {
            mid = len shr 1
            if (time == times[offset + mid]) {
                return offset + mid
            } else if (time > times[offset + mid]) {
                offset += mid
                len -= mid
                res = 1
            } else {
                len -= mid
                res = 0
            }
        }
        currentIndex = offset + res
        return currentIndex
    }

    /*
     ====================
     idCurve::TimeForIndex

     get the value for the given time
     ====================
     */
    protected open fun TimeForIndex(index: Int): Float {
        val n = times.Num() - 1
        if (index < 0) {
            return (times[0]
                    + index * (times[1] - times[0]))
        } else if (index > n) {
            return times[n] + (index - n) * (times[n] - times[n - 1])
        }
        return times[index]
    }

    /*
     ====================
     idCurve::ValueForIndex

     get the value for the given time
     ====================
     */
    protected open fun ValueForIndex(index: Int): T {
        val n = values.Num() - 1
        if (index < 0) {
            return values[0] + (values[1] - values[0]) * index
        } else if (index > n) {
            return values[n] + (values[n] - values[n - 1]) * (index - n)
        }
        return values[index]
    }

    protected fun GetSpeed(time: Float): Float {
        var i: Int
        var speed: Float
        val value: T
        value = GetCurrentFirstDerivative(time)
        speed = 0.0f
        i = 0
        while (i < value.GetDimension()) {
            speed += value[i] * value[i]
            i++
        }
        return idMath.Sqrt(speed)
    }

    protected fun RombergIntegral(t0: Float, t1: Float, order: Int): Float {
        var i: Int
        var j: Int
        var k: Int
        var m: Int
        var n: Int
        var sum: Float
        var delta: Float
        val temp = Array(2) { FloatArray(order) }
        delta = t1 - t0
        temp[0][0] = 0.5f * delta * (GetSpeed(t0) + GetSpeed(t1))
        i = 2
        m = 1
        while (i <= order) {
            // approximate using the trapezoid rule
            sum = 0.0f
            j = 1
            while (j <= m) {
                sum += GetSpeed(t0 + delta * (j - 0.5f))
                j++
            }

            // Richardson extrapolation
            temp[1][0] = 0.5f * (temp[0][0] + delta * sum)
            k = 1
            n = 4
            while (k < i) {
                temp[1][k] = (n * temp[1][k - 1] - temp[0][k - 1]) / (n - 1)
                k++
                n *= 4
            }
            j = 0
            while (j < i) {
                temp[0][j] = temp[1][j]
                j++
            }
            i++
            m *= 2
            delta *= 0.5f
        }
        return temp[0][order - 1]
    }

    protected fun newInstance(): T {
        return factory()
    }

    init {
        currentIndex = -1
        changed = false
    }
}

/**
 * ===============================================================================
 *
 *
 * Bezier Curve template. The degree of the polynomial equals the number of
 * knots minus one.
 *
 *
 * ===============================================================================
 */
class idCurve_Bezier<T : idVec<T>>(factory: () -> T) : idCurve<T>(factory) {
    /*
     ====================
     idCurve_Bezier::GetCurrentValue

     get the value for the given time
     ====================
     */
    override fun GetCurrentValue(time: Float): T {
        val bvals = FloatArray(values.Num())
        val v = newInstance()
        Basis(values.Num(), time, bvals)
        v.set(values[0] * bvals[0])
        for (i in 1 until values.Num()) {
            v.plusAssign(values[i] * bvals[i])
        }
        return v
    }

    /*
     ====================
     idCurve_Bezier::GetCurrentFirstDerivative

     get the first derivative for the given time
     ====================
     */
    override fun GetCurrentFirstDerivative(time: Float): T {
        val bvals = FloatArray(values.Num())
        val d: Float
        val v = newInstance()
        BasisFirstDerivative(values.Num(), time, bvals)
        v.set(values[0] * bvals[0])
        for (i in 1 until values.Num()) {
            v.plusAssign(values[i] * bvals[i])
        }
        d = times[times.Num() - 1] - times[0]
        return v * ((values.Num() - 1) / d)
    }

    /*
     ====================
     idCurve_Bezier::GetCurrentSecondDerivative

     get the second derivative for the given time
     ====================
     */
    override fun GetCurrentSecondDerivative(time: Float): T {
        val bvals = FloatArray(values.Num())
        val d: Float
        val v = newInstance()
        BasisSecondDerivative(values.Num(), time, bvals)
        v.set(values[0] * bvals[0])
        for (i in 1 until values.Num()) {
            v.plusAssign(values[i] * bvals[i])
        }
        d = times[times.Num() - 1] - times[0]
        return v * ((values.Num() - 2) * (values.Num() - 1) / (d * d))
    }

    /*
     ====================
     idCurve_Bezier::Basis

     bezier basis functions
     ====================
     */
    protected fun Basis(order: Int, t: Float, bvals: FloatArray) {
        var i: Int
        var j: Int
        val d: Int
        val c: FloatArray
        var c1: Float
        var c2: Float
        val s: Float
        val o: Float
        var ps: Float
        var po: Float
        bvals[0] = 1.0f
        d = order - 1
        if (d <= 0) {
            return
        }
        c = FloatArray(d + 1)
        s = (t - times[0]) / (times[times.Num() - 1] - times[0])
        o = 1.0f - s
        ps = s
        po = o
        i = 1
        while (i < d) {
            c[i] = 1.0f
            i++
        }
        i = 1
        while (i < d) {
            c[i - 1] = 0.0f
            c1 = c[i]
            c[i] = 1.0f
            j = i + 1
            while (j <= d) {
                c2 = c[j]
                c[j] = c1 + c[j - 1]
                c1 = c2
                j++
            }
            bvals[i] = c[d] * ps
            ps *= s
            i++
        }
        i = d - 1
        while (i >= 0) {
            bvals[i] *= po
            po *= o
            i--
        }
        bvals[d] = ps
    }

    /*
     ====================
     idCurve_Bezier::BasisFirstDerivative

     first derivative of bezier basis functions
     ====================
     */
    protected fun BasisFirstDerivative(order: Int, t: Float, bvals: FloatArray) {
        // Store original bvals for the calculation
        val tempBvals = FloatArray(order)
        System.arraycopy(bvals, 0, tempBvals, 0, order)

        // Call Basis on order-1 and shift result by 1 (equivalent to C++ pointer arithmetic)
        Basis(order - 1, t, tempBvals)
        System.arraycopy(tempBvals, 0, bvals, 1, order - 1)
        bvals[0] = 0.0f

        // Calculate differences
        for (i in 0 until order - 1) {
            bvals[i] -= bvals[i + 1]
        }
    }

    /*
     ====================
     idCurve_Bezier::BasisSecondDerivative

     second derivative of bezier basis functions
     ====================
     */
    protected fun BasisSecondDerivative(order: Int, t: Float, bvals: FloatArray) {
        // Store original bvals for the calculation
        val tempBvals = FloatArray(order)
        System.arraycopy(bvals, 0, tempBvals, 0, order)

        // Call BasisFirstDerivative on order-1 and shift result by 1
        BasisFirstDerivative(order - 1, t, tempBvals)
        System.arraycopy(tempBvals, 0, bvals, 1, order - 1)
        bvals[0] = 0.0f

        // Calculate differences
        for (i in 0 until order - 1) {
            bvals[i] -= bvals[i + 1]
        }
    }
}

/*
 ===============================================================================

 Quadratic Bezier Curve template.
 Should always have exactly three knots.

 ===============================================================================
 */
class idCurve_QuadraticBezier<T : idVec<T>>(factory: () -> T) : idCurve<T>(factory) {
    /*
     ====================
     idCurve_QuadraticBezier::GetCurrentValue

     get the value for the given time
     ====================
     */
    override fun GetCurrentValue(time: Float): T {
        val bvals = FloatArray(3)
        assert(values.Num() == 3)
        Basis(time, bvals)
        return (values[0] * bvals[0] + values[1] * bvals[1] + values[2] * bvals[2])
    }

    /*
     ====================
     idCurve_QuadraticBezier::GetCurrentFirstDerivative

     get the first derivative for the given time
     ====================
     */
    override fun GetCurrentFirstDerivative(time: Float): T {
        val bvals = FloatArray(3)
        val d: Float
        assert(values.Num() == 3)
        BasisFirstDerivative(time, bvals)
        d = times[2] - times[0]
        return (values[0] * bvals[0] + values[1] * bvals[1] + values[2] * bvals[2]) / d
    }

    /*
     ====================
     idCurve_QuadraticBezier::GetCurrentSecondDerivative

     get the second derivative for the given time
     ====================
     */
    override fun GetCurrentSecondDerivative(time: Float): T {
        val bvals = FloatArray(3)
        val d: Float
        assert(values.Num() == 3)
        BasisSecondDerivative(time, bvals)
        d = times[2] - times[0]
        return (values[0] * bvals[0] + values[1] * bvals[1] + values[2] * bvals[2]) / (d * d)
    }

    /*
     ====================
     idCurve_QuadraticBezier::Basis

     quadratic bezier basis functions
     ====================
     */
    protected fun Basis(t: Float, bvals: FloatArray) {
        val s1 = (t - times[0]) / (times[2] - times[0])
        val s2 = s1 * s1
        bvals[0] = s2 - 2.0f * s1 + 1.0f
        bvals[1] = -2.0f * s2 + 2.0f * s1
        bvals[2] = s2
    }

    /*
     ====================
     idCurve_QuadraticBezier::BasisFirstDerivative

     first derivative of quadratic bezier basis functions
     ====================
     */
    protected fun BasisFirstDerivative(t: Float, bvals: FloatArray) {
        val s1 = (t - times[0]) / (times[2] - times[0])
        bvals[0] = 2.0f * s1 - 2.0f
        bvals[1] = -4.0f * s1 + 2.0f
        bvals[2] = 2.0f * s1
    }

    /*
     ====================
     idCurve_QuadraticBezier::BasisSecondDerivative

     second derivative of quadratic bezier basis functions
     ====================
     */
    protected fun BasisSecondDerivative(t: Float, bvals: FloatArray) {
        bvals[0] = 2.0f
        bvals[1] = -4.0f
        bvals[2] = 2.0f
    }
}

/**
 * ===============================================================================
 *
 *
 * Cubic Bezier Curve template. Should always have exactly four knots.
 *
 *
 * ===============================================================================
 */
class idCurve_CubicBezier<T : idVec<T>>(factory: () -> T) : idCurve<T>(factory) {
    /*
     ====================
     idCurve_CubicBezier::GetCurrentValue

     get the value for the given time
     ====================
     */
    override fun GetCurrentValue(time: Float): T {
        val bvals = FloatArray(4)
        assert(values.Num() == 4)
        Basis(time, bvals)
        return (values[0] * bvals[0] + values[1] * bvals[1] + values[2] * bvals[2] + values[3] * bvals[3])
    }

    /*
     ====================
     idCurve_CubicBezier::GetCurrentFirstDerivative

     get the first derivative for the given time
     ====================
     */
    override fun GetCurrentFirstDerivative(time: Float): T {
        val bvals = FloatArray(4)
        val d: Float
        assert(values.Num() == 4)
        BasisFirstDerivative(time, bvals)
        d = times[3] - times[0]
        return (values[0] * bvals[0] + values[1] * bvals[1] + values[2] * bvals[2] + values[3] * bvals[3]) / d
    }

    /*
     ====================
     idCurve_CubicBezier::GetCurrentSecondDerivative

     get the second derivative for the given time
     ====================
     */
    override fun GetCurrentSecondDerivative(time: Float): T {
        val bvals = FloatArray(4)
        val d: Float
        assert(values.Num() == 4)
        BasisSecondDerivative(time, bvals)
        d = times[3] - times[0]
        return (values[0] * bvals[0] + values[1] * bvals[1] + values[2] * bvals[2] + values[3] * bvals[3]) / (d * d)
    }

    /*
     ====================
     idCurve_CubicBezier::Basis

     cubic bezier basis functions
     ====================
     */
    protected fun Basis(t: Float, bvals: FloatArray) {
        val s1 = (t - times[0]) / (times[3] - times[0])
        val s2 = s1 * s1
        val s3 = s2 * s1
        bvals[0] = -s3 + 3.0f * s2 - 3.0f * s1 + 1.0f
        bvals[1] = 3.0f * s3 - 6.0f * s2 + 3.0f * s1
        bvals[2] = -3.0f * s3 + 3.0f * s2
        bvals[3] = s3
    }

    /*
     ====================
     idCurve_CubicBezier::BasisFirstDerivative

     first derivative of cubic bezier basis functions
     ====================
     */
    protected fun BasisFirstDerivative(t: Float, bvals: FloatArray) {
        val s1 = (t - times[0]) / (times[3] - times[0])
        val s2 = s1 * s1
        bvals[0] = -3.0f * s2 + 6.0f * s1 - 3.0f
        bvals[1] = 9.0f * s2 - 12.0f * s1 + 3.0f
        bvals[2] = -9.0f * s2 + 6.0f * s1
        bvals[3] = 3.0f * s2
    }

    /*
     ====================
     idCurve_CubicBezier::BasisSecondDerivative

     second derivative of cubic bezier basis functions
     ====================
     */
    protected fun BasisSecondDerivative(t: Float, bvals: FloatArray) {
        val s1 = (t - times[0]) / (times[3] - times[0])
        bvals[0] = -6.0f * s1 + 6.0f
        bvals[1] = 18.0f * s1 - 12.0f
        bvals[2] = -18.0f * s1 + 6.0f
        bvals[3] = 6.0f * s1
    }
}

/**
 * ===============================================================================
 *
 *
 * Spline base template.
 *
 *
 * ===============================================================================
 */
open class idCurve_Spline<T : idVec<T>>(factory: () -> T) : idCurve<T>(factory) {
    protected var boundaryT: Int
    protected var closeTime: Float
    override fun IsDone(time: Float): Boolean {
        return boundaryT != BT_CLOSED && time >= times[times.Num() - 1]
    }

    fun SetBoundaryType(boundary_t: Int) {
        boundaryT = boundary_t
        changed = true
    }

    fun GetBoundaryType(): Int {
        return boundaryT
    }

    fun SetCloseTime(t: Float) {
        closeTime = t
        changed = true
    }

    fun GetCloseTime(): Float {
        return if (boundaryT == BT_CLOSED) closeTime else 0.0f
    }

    /*
     ====================
     idCurve_Spline::ValueForIndex

     get the value for the given time
     ====================
     */
    override fun ValueForIndex(index: Int): T {
        val n = values.Num() - 1
        if (index < 0) {
            return if (boundaryT == BT_CLOSED) {
                values[values.Num() + index % values.Num()]
            } else {
                values[0] + (values[1] - values[0]) * index
            }
        } else if (index > n) {
            return if (boundaryT == BT_CLOSED) {
                values[index % values.Num()]
            } else {
                return values[n] + (values[n] - values[n - 1]) * (index - n)
            }
        }
        return values[index]
    }

    /*
     ====================
     idCurve_Spline::TimeForIndex

     get the value for the given time
     ====================
     */
    override fun TimeForIndex(index: Int): Float {
        val n = times.Num() - 1
        if (index < 0) {
            return if (boundaryT == BT_CLOSED) {
                index / times.Num() * (times[n] + closeTime) - (times[n] + closeTime - times[times.Num() + index % times.Num()])
            } else {
                times[0] + index * (times[1] - times[0])
            }
        } else if (index > n) {
            return if (boundaryT == BT_CLOSED) {
                index / times.Num() * (times[n] + closeTime) + times[index % times.Num()]
            } else {
                times[n] + (index - n) * (times[n] - times[n - 1])
            }
        }
        return times[index]
    }

    /*
     ====================
     idCurve_Spline::ClampedTime

     return the clamped time based on the boundary T
     ====================
     */
    protected fun ClampedTime(t: Float): Float {
        if (boundaryT == BT_CLAMPED) {
            if (t < times[0]) {
                return times[0]
            } else if (t >= times[times.Num() - 1]) {
                return times[times.Num() - 1]
            }
        }
        return t
    }

    companion object {
        /**
         * enum	boundary_t { BT_FREE, BT_CLAMPED, BT_CLOSED };
         */
        const val BT_FREE = 0
        const val BT_CLAMPED = 1
        const val BT_CLOSED = 2
    }

    init {
        boundaryT = BT_FREE
        closeTime = 0.0f
    }
}

/**
 * ===============================================================================
 *
 *
 * Cubic Interpolating Spline template. The curve goes through all the
 * knots.
 *
 *
 * ===============================================================================
 */
class idCurve_NaturalCubicSpline<T : idVec<T>>(factory: () -> T) : idCurve_Spline<T>(factory) {
    protected val b: idList<T> = idList()
    protected val c: idList<T> = idList()
    protected val d: idList<T> = idList()
    override fun Clear() {
        super.Clear()
        values.Clear()
        b.Clear()
        c.Clear()
        d.Clear()
    }

    /*
     ====================
     idCurve_NaturalCubicSpline::GetCurrentValue

     get the value for the given time
     ====================
     */
    override fun GetCurrentValue(time: Float): T {
        val clampedTime = ClampedTime(time)
        val i = IndexForTime(clampedTime)
        val s = time - TimeForIndex(i)
        Setup()
        return (values[i] + (b[i] + (c[i] + d[i] * s) * s) * s)
    }

    /*
     ====================
     idCurve_NaturalCubicSpline::GetCurrentFirstDerivative

     get the first derivative for the given time
     ====================
     */
    override fun GetCurrentFirstDerivative(time: Float): T {
        val clampedTime = ClampedTime(time)
        val i = IndexForTime(clampedTime)
        val s = time - TimeForIndex(i)
        Setup()
        return (b[i] + (c[i] * 2.0f + d[i] * 3.0f * s) * s)
    }

    /*
     ====================
     idCurve_NaturalCubicSpline::GetCurrentSecondDerivative

     get the second derivative for the given time
     ====================
     */
    override fun GetCurrentSecondDerivative(time: Float): T {
        val clampedTime = ClampedTime(time)
        val i = IndexForTime(clampedTime)
        val s = time - TimeForIndex(i)
        Setup()
        return (c[i] * 2.0f + d[i] * 6.0f * s)
    }

    protected fun Setup() {
        if (changed) {
            when (boundaryT) {
                BT_FREE -> SetupFree()
                BT_CLAMPED -> SetupClamped()
                BT_CLOSED -> SetupClosed()
            }
            changed = false
        }
    }

    protected fun SetupFree() {
        var i: Int
        var inv: Float
        val d0: FloatArray
        val d1: FloatArray
        val beta: FloatArray
        val gamma: FloatArray
        val alpha: Array<T>
        val delta: Array<T>
        d0 = FloatArray(values.Num() - 1)
        d1 = FloatArray(values.Num() - 1)
        alpha = arrayOfNulls<Any>(values.Num() - 1) as Array<T>
        beta = FloatArray(values.Num())
        gamma = FloatArray(values.Num() - 1)
        delta = arrayOfNulls<Any>(values.Num()) as Array<T>
        i = 0
        while (i < values.Num() - 1) {
            d0[i] = times[i + 1] - times[i]
            i++
        }
        i = 1
        while (i < values.Num() - 1) {
            d1[i] = times[i + 1] - times[i - 1]
            i++
        }
        i = 1
        while (i < values.Num() - 1) {
            val sum: T = (values[i + 1] * d0[i - 1] - values[i] * d1[i] + values[i - 1] * d0[i]) * 3.0f
            inv = 1.0f / (d0[i - 1] * d0[i])
            alpha[i] = sum * inv
            i++
        }
        beta[0] = 1.0f
        gamma[0] = 0.0f
        delta[0] = values[0] - values[0]
        i = 1
        while (i < values.Num() - 1) {
            beta[i] = 2.0f * d1[i] - d0[i - 1] * gamma[i - 1]
            inv = 1.0f / beta[i]
            gamma[i] = inv * d0[i]
            delta[i] = (alpha[i] - delta[i - 1] * d0[i - 1]) * inv
            i++
        }
        beta[values.Num() - 1] = 1.0f
        delta[values.Num() - 1] = values[0] - values[0]
        b.AssureSize(values.Num())
        c.AssureSize(values.Num())
        d.AssureSize(values.Num())
        c[values.Num() - 1] = values[0] - values[0]
        i = values.Num() - 2
        while (i >= 0) {
            c[i] = delta[i] - c[i + 1] * gamma[i]
            inv = 1.0f / d0[i]
            b[i] = (values[i + 1] - values[i]) * inv - (c[i + 1] + c[i] * 2.0f) * (1.0f / 3.0f) * d0[i]
            d[i] = (c[i + 1] - c[i]) * (1.0f / 3.0f) * inv
            i--
        }
    }

    protected fun SetupClamped() {
        var i: Int
        var inv: Float
        val d0: FloatArray
        val d1: FloatArray
        val beta: FloatArray
        val gamma: FloatArray
        val alpha: Array<T>
        val delta: Array<T>
        d0 = FloatArray(values.Num() - 1)
        d1 = FloatArray(values.Num() - 1)
        alpha = arrayOfNulls<Any>(values.Num() - 1) as Array<T>
        beta = FloatArray(values.Num())
        gamma = FloatArray(values.Num() - 1)
        delta = arrayOfNulls<Any>(values.Num()) as Array<T>
        i = 0
        while (i < values.Num() - 1) {
            d0[i] = times[i + 1] - times[i]
            i++
        }
        i = 1
        while (i < values.Num() - 1) {
            d1[i] = times[i + 1] - times[i - 1]
            i++
        }
        inv = 1.0f / d0[0]
        alpha[0] = (values[1] - values[0]) * 3.0f * (inv - 1.0f)
        inv = 1.0f / d0[values.Num() - 2]
        alpha[values.Num() - 1] = (values[values.Num() - 1] - values[values.Num() - 2]) * 3.0f * (1.0f - inv)
        i = 1
        while (i < values.Num() - 1) {
            val sum: T = (values[i + 1] * d0[i - 1] - values[i] * d1[i] + values[i - 1] * d0[i]) * 3.0f
            inv = 1.0f / (d0[i - 1] * d0[i])
            alpha[i] = sum * inv
            i++
        }
        beta[0] = 2.0f * d0[0]
        gamma[0] = 0.5f
        inv = 1.0f / beta[0]
        delta[0] = alpha[0] * inv
        i = 1
        while (i < values.Num() - 1) {
            beta[i] = 2.0f * d1[i] - d0[i - 1] * gamma[i - 1]
            inv = 1.0f / beta[i]
            gamma[i] = inv * d0[i]
            delta[i] = (alpha[i] - delta[i - 1] * d0[i - 1]) * inv
            i++
        }
        beta[values.Num() - 1] = d0[values.Num() - 2] * (2.0f - gamma[values.Num() - 2])
        inv = 1.0f / beta[values.Num() - 1]
        delta[values.Num() - 1] = (alpha[values.Num() - 1] - delta[values.Num() - 2] * d0[values.Num() - 2]) * inv
        b.AssureSize(values.Num())
        c.AssureSize(values.Num())
        d.AssureSize(values.Num())
        c[values.Num() - 1] = delta[values.Num() - 1]
        i = values.Num() - 2
        while (i >= 0) {
            c[i] = delta[i] - c[i + 1] * gamma[i]
            inv = 1.0f / d0[i]
            b[i] = (values[i + 1] - values[i]) * inv - (c[i + 1] + c[i] * 2.0f) * (1.0f / 3.0f) * d0[i]
            d[i] = (c[i + 1] - c[i]) * (1.0f / 3.0f) * inv
            i--
        }
    }

    protected fun SetupClosed() {
        var i: Int
        var j: Int
        var c0: Float
        var c1: Float
        val d0: FloatArray
        val mat = idMatX()
        val x = idVecX()
        d0 = FloatArray(values.Num() - 1)
        x.SetData(values.Num(), idVecX.VECX_ALLOCA(values.Num()))
        mat.SetData(values.Num(), values.Num(), idMatX.MATX_ALLOCA(values.Num() * values.Num()))
        b.AssureSize(values.Num())
        c.AssureSize(values.Num())
        d.AssureSize(values.Num())
        i = 0
        while (i < values.Num() - 1) {
            d0[i] = times[i + 1] - times[i]
            i++
        }

        // matrix of system
        mat[0, 0] = 1.0f
        mat[0, values.Num() - 1] = -1.0f
        i = 1
        while (i <= values.Num() - 2) {
            mat[i, i - 1] = d0[i - 1]
            mat[i, i] = 2.0f * (d0[i - 1] + d0[i])
            mat[i, i + 1] = d0[i]
            i++
        }
        mat[values.Num() - 1, values.Num() - 2] = d0[values.Num() - 2]
        mat[values.Num() - 1, 0] = 2.0f * (d0[values.Num() - 2] + d0[0])
        mat[values.Num() - 1, 1] = d0[0]

        // right-hand side
        c[0].Zero()
        i = 1
        while (i <= values.Num() - 2) {
            c0 = 1.0f / d0[i]
            c1 = 1.0f / d0[i - 1]
            c[i] = ((values[i + 1] - values[i]) * c0 - (values[i] - values[i - 1]) * c1) * 3.0f
            i++
        }
        c0 = 1.0f / d0[0]
        c1 = 1.0f / d0[values.Num() - 2]
        c[values.Num() - 1] = ((values[1] - values[0]) * c0 - (values[0] - values[values.Num() - 2]) * c1) * 3.0f

        // solve system for each dimension
        mat.LU_Factor(null)
        i = 0
        while (i < values[0].GetDimension()) {
            j = 0
            while (j < values.Num()) {
                x.p[j] = c[j][i]
                j++
            }
            mat.LU_Solve(x, x, null)
            j = 0
            while (j < values.Num()) {
                c[j][i] = x.get(j)
                j++
            }
            i++
        }
        i = 0
        while (i < values.Num() - 1) {
            c0 = 1.0f / d0[i]
            b[i] = (values[i + 1] - values[i]) * c0 - (c[i + 1] + c[i] * 2.0f) * (1.0f / 3.0f) * d0[i]
            d[i] = (c[i + 1] - c[i]) * (1.0f / 3.0f) * c0
            i++
        }
    }
}

/**
 * ===============================================================================
 *
 *
 * Uniform Cubic Interpolating Spline template. The curve goes through all
 * the knots.
 *
 *
 * ===============================================================================
 */
class idCurve_CatmullRomSpline<T : idVec<T>>(factory: () -> T) : idCurve_Spline<T>(factory) {
    /*
     ====================
     idCurve_CatmullRomSpline::GetCurrentValue

     get the value for the given time
     ====================
     */
    override fun GetCurrentValue(time: Float): T {
        val i: Int
        var j: Int
        var k: Int
        val bvals = FloatArray(4)
        val clampedTime: Float
        val v = newInstance()
        if (times.Num() == 1) {
            return values[0]
        }
        clampedTime = ClampedTime(time)
        i = IndexForTime(clampedTime)
        Basis(i - 1, clampedTime, bvals)
        v.set(values[0] - values[0])
        j = 0
        while (j < 4) {
            k = i + j - 2
            v.plusAssign(ValueForIndex(k) * bvals[j])
            j++
        }
        return v
    }

    /*
     ====================
     idCurve_CatmullRomSpline::GetCurrentFirstDerivative

     get the first derivative for the given time
     ====================
     */
    override fun GetCurrentFirstDerivative(time: Float): T {
        val i: Int
        var j: Int
        var k: Int
        val bvals = FloatArray(4)
        val d: Float
        val clampedTime: Float
        val v = newInstance()
        if (times.Num() == 1) {
            return values[0] - values[0]
        }
        clampedTime = ClampedTime(time)
        i = IndexForTime(clampedTime)
        BasisFirstDerivative(i - 1, clampedTime, bvals)
        v.set(values[0] - values[0])
        j = 0
        while (j < 4) {
            k = i + j - 2
            v.plusAssign(ValueForIndex(k) * bvals[j])
            j++
        }
        d = TimeForIndex(i) - TimeForIndex(i - 1)
        return v / d
    }

    /*
     ====================
     idCurve_CatmullRomSpline::GetCurrentSecondDerivative

     get the second derivative for the given time
     ====================
     */
    override fun GetCurrentSecondDerivative(time: Float): T {
        val i: Int
        var j: Int
        var k: Int
        val bvals = FloatArray(4)
        val d: Float
        val clampedTime: Float
        val v = newInstance()
        if (times.Num() == 1) {
            return values[0] - values[0]
        }
        clampedTime = ClampedTime(time)
        i = IndexForTime(clampedTime)
        BasisSecondDerivative(i - 1, clampedTime, bvals)
        v.set(values[0] - values[0])
        j = 0
        while (j < 4) {
            k = i + j - 2
            v.plusAssign(ValueForIndex(k) * bvals[j])
            j++
        }
        d = TimeForIndex(i) - TimeForIndex(i - 1)
        return v / (d * d)
    }

    /*
     ====================
     idCurve_CatmullRomSpline::Basis

     spline basis functions
     ====================
     */
    protected fun Basis(index: Int, t: Float, bvals: FloatArray) {
        val s = (t - TimeForIndex(index)) / (TimeForIndex(index + 1) - TimeForIndex(index))
        bvals[0] = ((-s + 2.0f) * s - 1.0f) * s * 0.5f // -0.5f s * s * s + s * s - 0.5f * s
        bvals[1] = ((3.0f * s - 5.0f) * s * s + 2.0f) * 0.5f // 1.5f * s * s * s - 2.5f * s * s + 1.0f
        bvals[2] = ((-3.0f * s + 4.0f) * s + 1.0f) * s * 0.5f // -1.5f * s * s * s - 2.0f * s * s + 0.5f s
        bvals[3] = (s - 1.0f) * s * s * 0.5f // 0.5f * s * s * s - 0.5f * s * s
    }

    /*
     ====================
     idCurve_CatmullRomSpline::BasisFirstDerivative

     first derivative of spline basis functions
     ====================
     */
    protected fun BasisFirstDerivative(index: Int, t: Float, bvals: FloatArray) {
        val s = (t - TimeForIndex(index)) / (TimeForIndex(index + 1) - TimeForIndex(index))
        bvals[0] = (-1.5f * s + 2.0f) * s - 0.5f // -1.5f * s * s + 2.0f * s - 0.5f
        bvals[1] = (4.5f * s - 5.0f) * s // 4.5f * s * s - 5.0f * s
        bvals[2] = (-4.5f * s + 4.0f) * s + 0.5f // -4.5 * s * s + 4.0f * s + 0.5f
        bvals[3] = 1.5f * s * s - s // 1.5f * s * s - s
    }

    /*
     ====================
     idCurve_CatmullRomSpline::BasisSecondDerivative

     second derivative of spline basis functions
     ====================
     */
    protected fun BasisSecondDerivative(index: Int, t: Float, bvals: FloatArray) {
        val s = (t - TimeForIndex(index)) / (TimeForIndex(index + 1) - TimeForIndex(index))
        bvals[0] = -3.0f * s + 2.0f
        bvals[1] = 9.0f * s - 5.0f
        bvals[2] = -9.0f * s + 4.0f
        bvals[3] = 3.0f * s - 1.0f
    }
}

/**
 * ===============================================================================
 *
 *
 * Cubic Interpolating Spline template. The curve goes through all the
 * knots. The curve becomes the Catmull-Rom spline if the tension,
 * continuity and bias are all set to zero.
 *
 *
 * ===============================================================================
 */
class idCurve_KochanekBartelsSpline<T : idVec<T>>(factory: () -> T) :
    idCurve_Spline<T>(factory) {
    protected val bias: idFloatList = idFloatList()
    protected val continuity: idFloatList = idFloatList()
    protected val tension: idFloatList = idFloatList()

    /*
     ====================
     idCurve_KochanekBartelsSpline::AddValue

     add a timed/value pair to the spline
     returns the index to the inserted pair
     ====================
     */
    override fun AddValue(time: Float, value: T): Int {
        val i: Int
        i = IndexForTime(time)
        times.Insert(time, i)
        values.Insert(value, i)
        tension.Insert(0.0f, i)
        continuity.Insert(0.0f, i)
        bias.Insert(0.0f, i)
        return i
    }

    /*
     ====================
     idCurve_KochanekBartelsSpline::AddValue

     add a timed/value pair to the spline
     returns the index to the inserted pair
     ====================
     */
    fun AddValue(time: Float, value: T, tension: Float, continuity: Float, bias: Float): Int {
        val i: Int
        i = IndexForTime(time)
        times.Insert(time, i)
        values.Insert(value, i)
        this.tension.Insert(tension, i)
        this.continuity.Insert(continuity, i)
        this.bias.Insert(bias, i)
        return i
    }

    override fun RemoveIndex(index: Int) {
        values.RemoveIndex(index)
        times.RemoveIndex(index)
        tension.RemoveIndex(index)
        continuity.RemoveIndex(index)
        bias.RemoveIndex(index)
    }

    override fun Clear() {
        values.Clear()
        times.Clear()
        tension.Clear()
        continuity.Clear()
        bias.Clear()
        currentIndex = -1
    }

    /*
     ====================
     idCurve_KochanekBartelsSpline::GetCurrentValue

     get the value for the given time
     ====================
     */
    override fun GetCurrentValue(time: Float): T {
        val i: Int
        val bvals = FloatArray(4)
        val clampedTime: Float
        val t0 = newInstance()
        val t1 = newInstance()
        val v = newInstance()
        if (times.Num() == 1) {
            return values[0]
        }
        clampedTime = ClampedTime(time)
        i = IndexForTime(clampedTime)
        TangentsForIndex(i - 1, t0, t1)
        Basis(i - 1, clampedTime, bvals)
        v.set(ValueForIndex(i - 1) * bvals[0])
        v.plusAssign(ValueForIndex(i) * bvals[1])
        v.plusAssign(t0 * bvals[2])
        v.plusAssign(t1 * bvals[3])
        return v
    }

    /*
     ====================
     idCurve_KochanekBartelsSpline::GetCurrentFirstDerivative

     get the first derivative for the given time
     ====================
     */
    override fun GetCurrentFirstDerivative(time: Float): T {
        val i: Int
        val bvals = FloatArray(4)
        val d: Float
        val clampedTime: Float
        val t0 = newInstance()
        val t1 = newInstance()
        val v = newInstance()
        if (times.Num() == 1) {
            return values[0] - values[0]
        }
        clampedTime = ClampedTime(time)
        i = IndexForTime(clampedTime)
        TangentsForIndex(i - 1, t0, t1)
        BasisFirstDerivative(i - 1, clampedTime, bvals)
        v.set(ValueForIndex(i - 1) * bvals[0])
        v.plusAssign(ValueForIndex(i) * bvals[1])
        v.plusAssign(t0 * bvals[2])
        v.plusAssign(t1 * bvals[3])
        d = TimeForIndex(i) - TimeForIndex(i - 1)
        return v / d
    }

    /*
     ====================
     idCurve_KochanekBartelsSpline::GetCurrentSecondDerivative

     get the second derivative for the given time
     ====================
     */
    override fun GetCurrentSecondDerivative(time: Float): T {
        val i: Int
        val bvals = FloatArray(4)
        val d: Float
        val clampedTime: Float
        val t0 = newInstance()
        val t1 = newInstance()
        val v = newInstance()
        if (times.Num() == 1) {
            return values[0] - values[0]
        }
        clampedTime = ClampedTime(time)
        i = IndexForTime(clampedTime)
        TangentsForIndex(i - 1, t0, t1)
        BasisSecondDerivative(i - 1, clampedTime, bvals)
        v.set(ValueForIndex(i - 1) * bvals[0])
        v.plusAssign(ValueForIndex(i) * bvals[1])
        v.plusAssign(t0 * bvals[2])
        v.plusAssign(t1 * bvals[3])
        d = TimeForIndex(i) - TimeForIndex(i - 1)
        return v / (d * d)
    }

    protected fun TangentsForIndex(index: Int, t0: T, t1: T) {
        val dt: Float
        var omt: Float
        var omc: Float
        var opc: Float
        var omb: Float
        var opb: Float
        var adj: Float
        var s0: Float
        var s1: Float
        val delta: T
        delta = ValueForIndex(index + 1) - ValueForIndex(index)
        dt = TimeForIndex(index + 1) - TimeForIndex(index)
        omt = 1.0f - tension[index]
        omc = 1.0f - continuity[index]
        opc = 1.0f + continuity[index]
        omb = 1.0f - bias[index]
        opb = 1.0f + bias[index]
        adj = 2.0f * dt / (TimeForIndex(index + 1) - TimeForIndex(index - 1))
        s0 = 0.5f * adj * omt * opc * opb
        s1 = 0.5f * adj * omt * omc * omb

        // outgoing tangent at first point
        t0.set(delta * s1 + (ValueForIndex(index) - ValueForIndex(index - 1)) * s0)
        omt = 1.0f - tension[index + 1]
        omc = 1.0f - continuity[index + 1]
        opc = 1.0f + continuity[index + 1]
        omb = 1.0f - bias[index + 1]
        opb = 1.0f + bias[index + 1]
        adj = 2.0f * dt / (TimeForIndex(index + 2) - TimeForIndex(index))
        s0 = 0.5f * adj * omt * omc * opb
        s1 = 0.5f * adj * omt * opc * omb

        // incoming tangent at second point
        t1.set((ValueForIndex(index + 2) - ValueForIndex(index + 1)) * s1 + delta * s0)
    }

    /*
     ====================
     idCurve_KochanekBartelsSpline::Basis

     spline basis functions
     ====================
     */
    protected fun Basis(index: Int, t: Float, bvals: FloatArray) {
        val s = (t - TimeForIndex(index)) / (TimeForIndex(index + 1) - TimeForIndex(index))
        bvals[0] = (2.0f * s - 3.0f) * s * s + 1.0f // 2.0f * s * s * s - 3.0f * s * s + 1.0f
        bvals[1] = (-2.0f * s + 3.0f) * s * s // -2.0f * s * s * s + 3.0f * s * s
        bvals[2] = (s - 2.0f) * s * s + s // s * s * s - 2.0f * s * s + s
        bvals[3] = (s - 1.0f) * s * s // s * s * s - s * s
    }

    /*
     ====================
     idCurve_KochanekBartelsSpline::BasisFirstDerivative

     first derivative of spline basis functions
     ====================
     */
    protected fun BasisFirstDerivative(index: Int, t: Float, bvals: FloatArray) {
        val s = (t - TimeForIndex(index)) / (TimeForIndex(index + 1) - TimeForIndex(index))
        bvals[0] = (6.0f * s - 6.0f) * s // 6.0f * s * s - 6.0f * s
        bvals[1] = (-6.0f * s + 6.0f) * s // -6.0f * s * s + 6.0f * s
        bvals[2] = (3.0f * s - 4.0f) * s + 1.0f // 3.0f * s * s - 4.0f * s + 1.0f
        bvals[3] = (3.0f * s - 2.0f) * s // 3.0f * s * s - 2.0f * s
    }

    /*
     ====================
     idCurve_KochanekBartelsSpline::BasisSecondDerivative

     second derivative of spline basis functions
     ====================
     */
    protected fun BasisSecondDerivative(index: Int, t: Float, bvals: FloatArray) {
        val s = (t - TimeForIndex(index)) / (TimeForIndex(index + 1) - TimeForIndex(index))
        bvals[0] = 12.0f * s - 6.0f
        bvals[1] = -12.0f * s + 6.0f
        bvals[2] = 6.0f * s - 4.0f
        bvals[3] = 6.0f * s - 2.0f
    }
}

/**
 * ===============================================================================
 *
 *
 * B-Spline base template. Uses recursive definition and is slow. Use
 * idCurve_UniformCubicBSpline or idCurve_NonUniformBSpline instead.
 *
 *
 * ===============================================================================
 */
open class idCurve_BSpline<T : idVec<T>>     // default to cubic
    (factory: () -> T) : idCurve_Spline<T>(factory) {
    protected var order = 4
    fun GetOrder(): Int {
        return order
    }

    fun SetOrder(i: Int) {
        assert(i > 0 && i < 10)
        order = i
    }

    /*
     ====================
     idCurve_BSpline::GetCurrentValue

     get the value for the given time
     ====================
     */
    override fun GetCurrentValue(time: Float): T {
        val i: Int
        var j: Int
        var k: Int
        val clampedTime: Float
        val v = newInstance()
        if (times.Num() == 1) {
            return values[0]
        }
        clampedTime = ClampedTime(time)
        i = IndexForTime(clampedTime)
        v.set(values[0] - values[0])
        j = 0
        while (j < order) {
            k = i + j - (order shr 1)
            v.plusAssign(ValueForIndex(k) * Basis(k - 2, order, clampedTime))
            j++
        }
        return v
    }

    /*
     ====================
     idCurve_BSpline::GetCurrentFirstDerivative

     get the first derivative for the given time
     ====================
     */
    override fun GetCurrentFirstDerivative(time: Float): T {
        val i: Int
        var j: Int
        var k: Int
        val clampedTime: Float
        val v = newInstance()
        if (times.Num() == 1) {
            return values[0]
        }
        clampedTime = ClampedTime(time)
        i = IndexForTime(clampedTime)
        v.set(values[0] - values[0])
        j = 0
        while (j < order) {
            k = i + j - (order shr 1)
            v.plusAssign(ValueForIndex(k) * BasisFirstDerivative(k - 2, order, clampedTime))
            j++
        }
        return v
    }

    /*
     ====================
     idCurve_BSpline::GetCurrentSecondDerivative

     get the second derivative for the given time
     ====================
     */
    override fun GetCurrentSecondDerivative(time: Float): T {
        val i: Int
        var j: Int
        var k: Int
        val clampedTime: Float
        val v = newInstance()
        if (times.Num() == 1) {
            return values[0]
        }
        clampedTime = ClampedTime(time)
        i = IndexForTime(clampedTime)
        v.set(values[0] - values[0])
        j = 0
        while (j < order) {
            k = i + j - (order shr 1)
            v.plusAssign(ValueForIndex(k) * BasisSecondDerivative(k - 2, order, clampedTime))
            j++
        }
        return v
    }

    /*
     ====================
     idCurve_BSpline::Basis

     spline basis function
     ====================
     */
    protected fun Basis(index: Int, order: Int, t: Float): Float {
        return if (order <= 1) {
            if (TimeForIndex(index) < t && t <= TimeForIndex(index + 1)) {
                1.0f
            } else {
                0.0f
            }
        } else {
            var sum = 0.0f
            val d1 = TimeForIndex(index + order - 1) - TimeForIndex(index)
            if (d1 != 0.0f) {
                sum += (t - TimeForIndex(index)) * Basis(index, order - 1, t) / d1
            }
            val d2 = TimeForIndex(index + order) - TimeForIndex(index + 1)
            if (d2 != 0.0f) {
                sum += (TimeForIndex(index + order) - t) * Basis(index + 1, order - 1, t) / d2
            }
            sum
        }
    }

    /*
     ====================
     idCurve_BSpline::BasisFirstDerivative

     first derivative of spline basis function
     ====================
     */
    protected fun BasisFirstDerivative(index: Int, order: Int, t: Float): Float {
        return Basis(index, order - 1, t) - Basis(index + 1, order - 1, t) * (order - 1).toFloat() / (TimeForIndex(
            index + (order - 1) - 2
        ) - TimeForIndex(index - 2))
    }

    /*
     ====================
     idCurve_BSpline::BasisSecondDerivative

     second derivative of spline basis function
     ====================
     */
    protected fun BasisSecondDerivative(index: Int, order: Int, t: Float): Float {
        return BasisFirstDerivative(index, order - 1, t) - BasisFirstDerivative(
            index + 1,
            order - 1,
            t
        ) * (order - 1).toFloat() / (TimeForIndex(index + (order - 1) - 2) - TimeForIndex(index - 2))
    }
}

/**
 * ===============================================================================
 *
 *
 * Uniform Non-Rational Cubic B-Spline template.
 *
 *
 * ===============================================================================
 */
class idCurve_UniformCubicBSpline<T : idVec<T>>(factory: () -> T) : idCurve_BSpline<T>(factory) {
    /*
     ====================
     idCurve_UniformCubicBSpline::GetCurrentValue

     get the value for the given time
     ====================
     */
    override fun GetCurrentValue(time: Float): T {
        val i: Int
        var j: Int
        var k: Int
        val bvals = FloatArray(4)
        val clampedTime: Float
        val v = newInstance()
        if (times.Num() == 1) {
            return values[0]
        }
        clampedTime = ClampedTime(time)
        i = IndexForTime(clampedTime)
        Basis(i - 1, clampedTime, bvals)
        v.set(values[0] - values[0])
        j = 0
        while (j < 4) {
            k = i + j - 2
            v.plusAssign(ValueForIndex(k) * bvals[j])
            j++
        }
        return v
    }

    /*
     ====================
     idCurve_UniformCubicBSpline::GetCurrentFirstDerivative

     get the first derivative for the given time
     ====================
     */
    override fun GetCurrentFirstDerivative(time: Float): T {
        val i: Int
        var j: Int
        var k: Int
        val bvals = FloatArray(4)
        val d: Float
        val clampedTime: Float
        val v = newInstance()
        if (times.Num() == 1) {
            return values[0] - values[0]
        }
        clampedTime = ClampedTime(time)
        i = IndexForTime(clampedTime)
        BasisFirstDerivative(i - 1, clampedTime, bvals)
        v.set(values[0] - values[0])
        j = 0
        while (j < 4) {
            k = i + j - 2
            v.plusAssign(ValueForIndex(k) * bvals[j])
            j++
        }
        d = TimeForIndex(i) - TimeForIndex(i - 1)
        return v / d
    }

    /*
     ====================
     idCurve_UniformCubicBSpline::GetCurrentSecondDerivative

     get the second derivative for the given time
     ====================
     */
    override fun GetCurrentSecondDerivative(time: Float): T {
        val i: Int
        var j: Int
        var k: Int
        val bvals = FloatArray(4)
        val d: Float
        val clampedTime: Float
        val v = newInstance()
        if (times.Num() == 1) {
            return values[0] - values[0]
        }
        clampedTime = ClampedTime(time)
        i = IndexForTime(clampedTime)
        BasisSecondDerivative(i - 1, clampedTime, bvals)
        v.set(values[0] - values[0])
        j = 0
        while (j < 4) {
            k = i + j - 2
            v.plusAssign(ValueForIndex(k) * bvals[j])
            j++
        }
        d = TimeForIndex(i) - TimeForIndex(i - 1)
        return v / (d * d)
    }

    /*
     ====================
     idCurve_UniformCubicBSpline::Basis

     spline basis functions
     ====================
     */
    protected fun Basis(index: Int, t: Float, bvals: FloatArray) {
        val s = (t - TimeForIndex(index)) / (TimeForIndex(index + 1) - TimeForIndex(index))
        bvals[0] = (((-s + 3.0f) * s - 3.0f) * s + 1.0f) * (1.0f / 6.0f)
        bvals[1] = ((3.0f * s - 6.0f) * s * s + 4.0f) * (1.0f / 6.0f)
        bvals[2] = (((-3.0f * s + 3.0f) * s + 3.0f) * s + 1.0f) * (1.0f / 6.0f)
        bvals[3] = s * s * s * (1.0f / 6.0f)
    }

    /*
     ====================
     idCurve_UniformCubicBSpline::BasisFirstDerivative

     first derivative of spline basis functions
     ====================
     */
    protected fun BasisFirstDerivative(index: Int, t: Float, bvals: FloatArray) {
        val s = (t - TimeForIndex(index)) / (TimeForIndex(index + 1) - TimeForIndex(index))
        bvals[0] = -0.5f * s * s + s - 0.5f
        bvals[1] = 1.5f * s * s - 2.0f * s
        bvals[2] = -1.5f * s * s + s + 0.5f
        bvals[3] = 0.5f * s * s
    }

    /*
     ====================
     idCurve_UniformCubicBSpline::BasisSecondDerivative

     second derivative of spline basis functions
     ====================
     */
    protected fun BasisSecondDerivative(index: Int, t: Float, bvals: FloatArray) {
        val s = (t - TimeForIndex(index)) / (TimeForIndex(index + 1) - TimeForIndex(index))
        bvals[0] = -s + 1.0f
        bvals[1] = 3.0f * s - 2.0f
        bvals[2] = -3.0f * s + 1.0f
        bvals[3] = s
    }

    init {
        order = 4 // always cubic
    }
}

/**
 * ===============================================================================
 *
 *
 * Non-Uniform Non-Rational B-Spline (NUBS) template.
 *
 *
 * ===============================================================================
 */
open class idCurve_NonUniformBSpline<T : idVec<T>>(factory: () -> T) : idCurve_BSpline<T>(factory) {
    /*
     ====================
     idCurve_NonUniformBSpline::GetCurrentValue

     get the value for the given time
     ====================
     */
    override fun GetCurrentValue(time: Float): T {
        val i: Int
        var j: Int
        var k: Int
        val clampedTime: Float
        val v = newInstance()
        val bvals = FloatArray(order) //	float *bvals = (float *) _alloca16( this.order * sizeof(float) );
        if (times.Num() == 1) {
            return values[0]
        }
        clampedTime = ClampedTime(time)
        i = IndexForTime(clampedTime)
        Basis(i - 1, order, clampedTime, bvals)
        v.set(values[0] - values[0])
        j = 0
        while (j < order) {
            k = i + j - (order shr 1)
            v.plusAssign(ValueForIndex(k) * bvals[j])
            j++
        }
        return v
    }

    /*
     ====================
     idCurve_NonUniformBSpline::GetCurrentFirstDerivative

     get the first derivative for the given time
     ====================
     */
    override fun GetCurrentFirstDerivative(time: Float): T {
        val i: Int
        var j: Int
        var k: Int
        val clampedTime: Float
        val v = newInstance()
        val bvals = FloatArray(order) //	float *bvals = (float *) _alloca16( this.order * sizeof(float) );
        if (times.Num() == 1) {
            return values[0] - values[0]
        }
        clampedTime = ClampedTime(time)
        i = IndexForTime(clampedTime)
        BasisFirstDerivative(i - 1, order, clampedTime, bvals)
        v.set(values[0] - values[0])
        j = 0
        while (j < order) {
            k = i + j - (order shr 1)
            v.plusAssign(ValueForIndex(k) * bvals[j])
            j++
        }
        return v
    }

    /*
     ====================
     idCurve_NonUniformBSpline::GetCurrentSecondDerivative

     get the second derivative for the given time
     ====================
     */
    override fun GetCurrentSecondDerivative(time: Float): T {
        val i: Int
        var j: Int
        var k: Int
        val clampedTime: Float
        val v = newInstance()
        val bvals = FloatArray(order) //	float *bvals = (float *) _alloca16( this.order * sizeof(float) );
        if (times.Num() == 1) {
            return values[0] - values[0]
        }
        clampedTime = ClampedTime(time)
        i = IndexForTime(clampedTime)
        BasisSecondDerivative(i - 1, order, clampedTime, bvals)
        v.set(values[0] - values[0])
        j = 0
        while (j < order) {
            k = i + j - (order shr 1)
            v.plusAssign(ValueForIndex(k) * bvals[j])
            j++
        }
        return v
    }

    /*
     ====================
     idCurve_NonUniformBSpline::Basis

     spline basis functions
     ====================
     */
    protected fun Basis(index: Int, order: Int, t: Float, bvals: FloatArray) {
        var r: Int
        var s: Int
        var i: Int
        var omega: Float
        bvals[order - 1] = 1.0f
        r = 2
        while (r <= order) {
            i = index - r + 1
            bvals[order - r] = 0.0f
            s = order - r + 1
            while (s < order) {
                i++
                omega = (t - TimeForIndex(i)) / (TimeForIndex(i + r - 1) - TimeForIndex(i))
                bvals[s - 1] += (1.0f - omega) * bvals[s]
                bvals[s] *= omega
                s++
            }
            r++
        }
    }

    /*
     ====================
     idCurve_NonUniformBSpline::BasisFirstDerivative

     first derivative of spline basis functions
     ====================
     */
    protected fun BasisFirstDerivative(index: Int, order: Int, t: Float, bvals: FloatArray) {
        // Store original bvals for the calculation
        val tempBvals = FloatArray(order)
        System.arraycopy(bvals, 0, tempBvals, 0, order)

        // Call Basis on order-1 and shift result by 1 (equivalent to C++ pointer arithmetic)
        Basis(index, order - 1, t, tempBvals)
        System.arraycopy(tempBvals, 0, bvals, 1, order - 1)
        bvals[0] = 0.0f

        // Calculate differences with scaling
        for (i in 0 until order - 1) {
            bvals[i] -= bvals[i + 1]
            bvals[i] *= (order - 1) / (TimeForIndex(index + i + (order - 1) - 2) - TimeForIndex(index + i - 2))
        }
        if (order - 1 < bvals.size) {
            bvals[order - 1] *= (order - 1) / (TimeForIndex(index + (order - 1) + (order - 1) - 2) - TimeForIndex(index + (order - 1) - 2))
        }
    }

    /*
     ====================
     idCurve_NonUniformBSpline::BasisSecondDerivative

     second derivative of spline basis functions
     ====================
     */
    protected fun BasisSecondDerivative(index: Int, order: Int, t: Float, bvals: FloatArray) {
        // Store original bvals for the calculation
        val tempBvals = FloatArray(order)
        System.arraycopy(bvals, 0, tempBvals, 0, order)

        // Call BasisFirstDerivative on order-1 and shift result by 1
        BasisFirstDerivative(index, order - 1, t, tempBvals)
        System.arraycopy(tempBvals, 0, bvals, 1, order - 1)
        bvals[0] = 0.0f

        // Calculate differences with scaling
        for (i in 0 until order - 1) {
            bvals[i] -= bvals[i + 1]
            bvals[i] *= (order - 1) / (TimeForIndex(index + i + (order - 1) - 2) - TimeForIndex(index + i - 2))
        }
        if (order - 1 < bvals.size) {
            bvals[order - 1] *= (order - 1) / (TimeForIndex(index + (order - 1) + (order - 1) - 2) - TimeForIndex(index + (order - 1) - 2))
        }
    }
}

/*
 ===============================================================================

 Non-Uniform Rational B-Spline (NURBS) template.

 ===============================================================================
 */
class idCurve_NURBS<T : idVec<T>>(factory: () -> T) : idCurve_NonUniformBSpline<T>(factory) {
    protected val weights: idFloatList = idFloatList()

    /*
     ====================
     idCurve_NURBS::AddValue

     add a timed/value pair to the spline
     returns the index to the inserted pair
     ====================
     */
    override fun AddValue(time: Float, value: T): Int {
        val i: Int
        i = IndexForTime(time)
        times.Insert(time, i)
        values.Insert(value, i)
        weights.Insert(1.0f, i)
        return i
    }

    /*
     ====================
     idCurve_NURBS::AddValue

     add a timed/value pair to the spline
     returns the index to the inserted pair
     ====================
     */
    fun AddValue(time: Float, value: T, weight: Float): Int {
        val i: Int
        i = IndexForTime(time)
        times.Insert(time, i)
        values.Insert(value, i)
        weights.Insert(weight, i)
        return i
    }

    override fun RemoveIndex(index: Int) {
        values.RemoveIndex(index)
        times.RemoveIndex(index)
        weights.RemoveIndex(index)
    }

    override fun Clear() {
        values.Clear()
        times.Clear()
        weights.Clear()
        currentIndex = -1
    }

    /*
     ====================
     idCurve_NURBS::GetCurrentValue

     get the value for the given time
     ====================
     */
    override fun GetCurrentValue(time: Float): T {
        val i: Int
        var j: Int
        var k: Int
        var w: Float
        var b: Float
        val clampedTime: Float
        val bvals: FloatArray
        val v = newInstance()
        if (times.Num() == 1) {
            return values[0]
        }
        bvals = FloatArray(order)
        clampedTime = ClampedTime(time)
        i = IndexForTime(clampedTime)
        this.Basis(i - 1, order, clampedTime, bvals)
        v.set(values[0] - values[0])
        w = 0.0f
        j = 0
        while (j < order) {
            k = i + j - (order shr 1)
            b = bvals[j] * WeightForIndex(k)
            w += b
            v.plusAssign(ValueForIndex(k) * b)
            j++
        }
        return v / w
    }

    /*
     ====================
     idCurve_NURBS::GetCurrentFirstDerivative

     get the first derivative for the given time
     ====================
     */
    override fun GetCurrentFirstDerivative(time: Float): T {
        val i: Int
        var j: Int
        var k: Int
        var w: Float
        var wb: Float
        var wd1: Float
        var b: Float
        var d1: Float
        val clampedTime: Float
        val bvals: FloatArray
        val d1vals: FloatArray
        val v = newInstance()
        val vb = newInstance()
        val vd1 = newInstance()
        if (times.Num() == 1) {
            return values[0]
        }
        bvals = FloatArray(order)
        d1vals = FloatArray(order)
        clampedTime = ClampedTime(time)
        i = IndexForTime(clampedTime)
        this.Basis(i - 1, order, clampedTime, bvals)
        this.BasisFirstDerivative(i - 1, order, clampedTime, d1vals)
        vb.set(vd1.set(values[0] - values[0]))
        wd1 = 0.0f
        wb = wd1
        j = 0
        while (j < order) {
            k = i + j - (order shr 1)
            w = WeightForIndex(k)
            b = bvals[j] * w
            d1 = d1vals[j] * w
            wb += b
            wd1 += d1
            v.set(ValueForIndex(k))
            vb.plusAssign(v * b)
            vd1.plusAssign(v * d1)
            j++
        }
        return (vd1 * wb - vb * wd1) / (wb * wb)
    }

    /*
     ====================
     idCurve_NURBS::GetCurrentSecondDerivative

     get the second derivative for the given time
     ====================
     */
    override fun GetCurrentSecondDerivative(time: Float): T {
        val i: Int
        var j: Int
        var k: Int
        var w: Float
        var wb: Float
        var wd1: Float
        var wd2: Float
        var b: Float
        var d1: Float
        var d2: Float
        val clampedTime: Float
        val bvals: FloatArray
        val d1vals: FloatArray
        val d2vals: FloatArray
        val v = newInstance()
        val vb = newInstance()
        val vd1 = newInstance()
        val vd2 = newInstance()
        if (times.Num() == 1) {
            return values[0]
        }
        bvals = FloatArray(order)
        d1vals = FloatArray(order)
        d2vals = FloatArray(order)
        clampedTime = ClampedTime(time)
        i = IndexForTime(clampedTime)
        this.Basis(i - 1, order, clampedTime, bvals)
        this.BasisFirstDerivative(i - 1, order, clampedTime, d1vals)
        this.BasisSecondDerivative(i - 1, order, clampedTime, d2vals)
        vb.set(vd1.set(vd2.set(values[0] - values[0])))
        wd2 = 0.0f
        wd1 = wd2
        wb = wd1
        j = 0
        while (j < order) {
            k = i + j - (order shr 1)
            w = WeightForIndex(k)
            b = bvals[j] * w
            d1 = d1vals[j] * w
            d2 = d2vals[j] * w
            wb += b
            wd1 += d1
            wd2 += d2
            v.set(ValueForIndex(k))
            vb.plusAssign(v * b)
            vd1.plusAssign(v * d1)
            vd2.plusAssign(v * d2)
            j++
        }

        return ((vd2 * wb - vb * wd2) * (wb * wb) - (vd1 * wb - vb * wd1) * 2.0f * wb * wd1) / (wb * wb * wb * wb)
    }

    protected fun WeightForIndex(index: Int): Float {
        val n = weights.Num() - 1
        if (index < 0) {
            return if (boundaryT == BT_CLOSED) {
                weights[weights.Num() + index % weights.Num()]
            } else {
                weights[0] + index * (weights[1] - weights[0])
            }
        } else if (index > n) {
            return if (boundaryT == BT_CLOSED) {
                weights[index % weights.Num()]
            } else {
                weights[n] + (index - n) * (weights[n] - weights[n - 1])
            }
        }
        return weights[index]
    }
}

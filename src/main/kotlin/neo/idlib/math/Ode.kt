package neo.idlib.math

import kotlin.math.abs

/*
 ===============================================================================

 Numerical solvers for ordinary differential equations.

 ===============================================================================
 */
class Ode {
    abstract class deriveFunction_t {
        abstract fun run(
            t: Float,
            userData: Any,
            state: FloatArray,
            derivatives: FloatArray
        )
    }

    //===============================================================
    //
    //	idODE
    //
    //===============================================================
    abstract class idODE {
        protected lateinit var derive // derive function
                : deriveFunction_t

        //public					~idODE( void ) {}
        protected var dimension // dimension in floats allocated for
                = 0
        protected lateinit var userData // client data
                : Any

        abstract fun Evaluate(state: FloatArray, newState: FloatArray, t0: Float, t1: Float): Float
    }

    //===============================================================
    //
    //	idODE_Euler
    //
    //===============================================================
    class idODE_Euler(dim: Int, dr: deriveFunction_t, ud: Any) : idODE() {
        protected var derivatives // space to store derivatives
                : FloatArray

        //	virtual				~idODE_Euler( void );
        override fun Evaluate(
            state: FloatArray,
            newState: FloatArray,
            t0: Float,
            t1: Float
        ): Float {
            val delta: Float
            var i: Int
            derive.run(t0, userData, state, derivatives)
            delta = t1 - t0
            i = 0
            while (i < dimension) {
                newState[i] = state[i] + delta * derivatives[i]
                i++
            }
            return delta
        }

        init {
            dimension = dim
            derivatives = FloatArray(dim)
            derive = dr
            userData = ud
        }
    }

    //===============================================================
    //
    //	idODE_Midpoint
    //
    //===============================================================
    class idODE_Midpoint(dim: Int, dr: deriveFunction_t, ud: Any) : idODE() {
        protected var derivatives // space to store derivatives
                : FloatArray
        protected var tmpState: FloatArray

        //public	virtual				~idODE_Midpoint( void );
        override fun Evaluate(state: FloatArray, newState: FloatArray, t0: Float, t1: Float): Float {
            val delta: Float
            val halfDelta: Float
            var i: Int
            delta = t1 - t0
            halfDelta = delta * 0.5f
            // first step
            derive.run(t0, userData, state, derivatives)
            i = 0
            while (i < dimension) {
                tmpState[i] = (state[i] + halfDelta * derivatives[i])
                i++
            }
            // second step
            derive.run(t0 + halfDelta, userData, tmpState, derivatives)
            i = 0
            while (i < dimension) {
                newState[i] = state[i] + delta * derivatives[i]
                i++
            }
            return delta
        }

        //
        //
        init {
            dimension = dim
            tmpState = FloatArray(dim)
            derivatives = FloatArray(dim)
            derive = dr
            userData = ud
        }
    }

    //===============================================================
    //
    //	idODE_RK4
    //
    //===============================================================
    class idODE_RK4(dim: Int, dr: deriveFunction_t, ud: Any) : idODE() {
        protected var d1 // derivatives
                : FloatArray
        protected var d2: FloatArray
        protected var d3: FloatArray
        protected var d4: FloatArray
        protected var tmpState: FloatArray

        //	virtual				~idODE_RK4( void );
        override fun Evaluate(state: FloatArray, newState: FloatArray, t0: Float, t1: Float): Float {
            val delta: Float
            val halfDelta: Float
            val sixthDelta: Float
            var i: Int
            delta = t1 - t0
            halfDelta = delta * 0.5f
            // first step
            derive.run(t0, userData, state, d1)
            i = 0
            while (i < dimension) {
                tmpState[i] = state[i] + halfDelta * d1[i]
                i++
            }
            // second step
            derive.run(t0 + halfDelta, userData, tmpState, d2)
            i = 0
            while (i < dimension) {
                tmpState[i] = state[i] + halfDelta * d2[i]
                i++
            }
            // third step
            derive.run(t0 + halfDelta, userData, tmpState, d3)
            i = 0
            while (i < dimension) {
                tmpState[i] = state[i] + delta * d3[i]
                i++
            }
            // fourth step
            derive.run(t0 + delta, userData, tmpState, d4)
            sixthDelta = delta * (1.0f / 6.0f)
            i = 0
            while (i < dimension) {
                newState[i] = state[i] + sixthDelta * (d1[i] + 2.0f * (d2[i] + d3[i]) + d4[i])
                i++
            }
            return delta
        }

        //
        //
        init {
            dimension = dim
            derive = dr
            userData = ud
            tmpState = FloatArray(dim)
            d1 = FloatArray(dim)
            d2 = FloatArray(dim)
            d3 = FloatArray(dim)
            d4 = FloatArray(dim)
        }
    }

    //===============================================================
    //
    //	idODE_RK4Adaptive
    //
    //===============================================================
    class idODE_RK4Adaptive(dim: Int, dr: deriveFunction_t, ud: Any) : idODE() {
        protected var d1 // derivatives
                : FloatArray
        protected var d1half: FloatArray
        protected var d2: FloatArray
        protected var d3: FloatArray
        protected var d4: FloatArray
        protected var maxError // maximum allowed error
                : Float
        protected var tmpState: FloatArray

        //	virtual				~idODE_RK4Adaptive( void );
        override fun Evaluate(state: FloatArray, newState: FloatArray, t0: Float, t1: Float): Float {
            var delta: Float
            var halfDelta: Float
            var fourthDelta: Float
            var sixthDelta: Float
            var error: Float
            var max: Float
            var i: Int
            var n: Int
            delta = t1 - t0
            n = 0
            while (n < 4) {
                halfDelta = delta * 0.5f
                fourthDelta = delta * 0.25f

                // first step of first half delta
                derive.run(t0, userData, state, d1)
                i = 0
                while (i < dimension) {
                    tmpState[i] = state[i] + fourthDelta * d1[i]
                    i++
                }
                // second step of first half delta
                derive.run(t0 + fourthDelta, userData, tmpState, d2)
                i = 0
                while (i < dimension) {
                    tmpState[i] = state[i] + fourthDelta * d2[i]
                    i++
                }
                // third step of first half delta
                derive.run(t0 + fourthDelta, userData, tmpState, d3)
                i = 0
                while (i < dimension) {
                    tmpState[i] = state[i] + halfDelta * d3[i]
                    i++
                }
                // fourth step of first half delta
                derive.run(t0 + halfDelta, userData, tmpState, d4)
                sixthDelta = halfDelta * (1.0f / 6.0f)
                i = 0
                while (i < dimension) {
                    tmpState[i] =
                        state[i] + sixthDelta * (d1[i] + 2.0f * (d2[i] + d3[i]) + d4[i])
                    i++
                }

                // first step of second half delta
                derive.run(t0 + halfDelta, userData, tmpState, d1half)
                i = 0
                while (i < dimension) {
                    tmpState[i] = state[i] + fourthDelta * d1half[i]
                    i++
                }
                // second step of second half delta
                derive.run(t0 + halfDelta + fourthDelta, userData, tmpState, d2)
                i = 0
                while (i < dimension) {
                    tmpState[i] = state[i] + fourthDelta * d2[i]
                    i++
                }
                // third step of second half delta
                derive.run(t0 + halfDelta + fourthDelta, userData, tmpState, d3)
                i = 0
                while (i < dimension) {
                    tmpState[i] = state[i] + halfDelta * d3[i]
                    i++
                }
                // fourth step of second half delta
                derive.run(t0 + delta, userData, tmpState, d4)
                sixthDelta = halfDelta * (1.0f / 6.0f)
                i = 0
                while (i < dimension) {
                    newState[i] =
                        state[i] + sixthDelta * (d1[i] + 2.0f * (d2[i] + d3[i]) + d4[i])
                    i++
                }

                // first step of full delta
                i = 0
                while (i < dimension) {
                    tmpState[i] = state[i] + halfDelta * d1[i]
                    i++
                }
                // second step of full delta
                derive.run(t0 + halfDelta, userData, tmpState, d2)
                i = 0
                while (i < dimension) {
                    tmpState[i] = state[i] + halfDelta * d2[i]
                    i++
                }
                // third step of full delta
                derive.run(t0 + halfDelta, userData, tmpState, d3)
                i = 0
                while (i < dimension) {
                    tmpState[i] = state[i] + delta * d3[i]
                    i++
                }
                // fourth step of full delta
                derive.run(t0 + delta, userData, tmpState, d4)
                sixthDelta = delta * (1.0f / 6.0f)
                i = 0
                while (i < dimension) {
                    tmpState[i] =
                        state[i] + sixthDelta * (d1[i] + 2.0f * (d2[i] + d3[i]) + d4[i])
                    i++
                }

                // get max estimated error
                max = 0.0f
                i = 0
                while (i < dimension) {
                    error = abs((newState[i] - tmpState[i]) / (delta * d1[i] + 1e-10f))
                    if (error > max) {
                        max = error
                    }
                    i++
                }
                error = max / maxError
                if (error <= 1.0f) {
                    return delta * 4.0f
                }
                if (delta <= 1e-7) {
                    return delta
                }
                delta *= 0.25f
                n++
            }
            return delta
        }

        fun SetMaxError(err: Float) {
            if (err > 0.0f) {
                maxError = err
            }
        }

        //
        //
        init {
            dimension = dim
            derive = dr
            userData = ud
            maxError = 0.01f
            tmpState = FloatArray(dim)
            d1 = FloatArray(dim)
            d1half = FloatArray(dim)
            d2 = FloatArray(dim)
            d3 = FloatArray(dim)
            d4 = FloatArray(dim)
        }
    }
}
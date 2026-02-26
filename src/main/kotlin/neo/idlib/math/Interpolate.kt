package neo.idlib.math

import neo.TempDump.SERiAL
import neo.idlib.math.Extrapolate.idExtrapolate
import java.nio.ByteBuffer

class Interpolate {
    /*
     ==============================================================================================

     Linear interpolation.

     ==============================================================================================
     */
    class idInterpolate<T> {
        private var currentTime: Float = 0.0f
        private var currentValue: T? = null
        private var duration = 0.0f
        private var endValue: T? = null
        private var startTime: Float = 0.0f
        private var startValue: T? = null
        fun Init(startTime: Float, duration: Float, startValue: T, endValue: T) {
            this.startTime = startTime
            this.duration = duration
            this.startValue = startValue
            this.endValue = endValue
            currentTime = startTime - 1
            currentValue = startValue
        }

        fun SetStartTime(time: Float) {
            startTime = time
        }

        fun SetDuration(duration: Float) {
            this.duration = duration
        }

        fun SetStartValue(startValue: T) {
            this.startValue = startValue
        }

        fun SetEndValue(endValue: T) {
            this.endValue = endValue
        }

        fun GetCurrentValue(time: Float): T {
            val deltaTime: Float = time - startTime
            if (time != currentTime) {
                currentTime = time
                when {
                    deltaTime <= 0 -> currentValue = startValue
                    deltaTime >= duration -> currentValue = endValue
                    else -> {
                        currentValue =
                            _Plus(startValue!!, _Multiply(_Minus(endValue!!, startValue!!), deltaTime / duration))
                    }
                }
            }
            return currentValue!!
        }

        fun IsDone(time: Float): Boolean {
            return time >= startTime + duration
        }

        fun GetStartTime(): Float {
            return startTime
        }

        fun GetEndTime(): Float {
            return startTime + duration
        }

        fun GetDuration(): Float {
            return duration
        }

        fun GetStartValue(): T {
            return startValue!!
        }

        fun GetEndValue(): T {
            return endValue!!
        }

        private fun _Multiply(t: T, f: Float): T {
            return when (t) {
                is idVec3 -> (t * f) as T
                is idVec4 -> (t * f) as T
                is idAngles -> (t * f) as T
                is Float -> (t * f) as T
                is Int -> (t * f).toInt() as T
                else -> t
            }
        }

        private fun _Plus(t1: T, t2: T): T {
            return when {
                t1 is idVec3 && t2 is idVec3 -> (t1 + t2) as T
                t1 is idVec4 && t2 is idVec4 -> (t1 + t2) as T
                t1 is idAngles && t2 is idAngles -> (t1 + t2) as T
                t1 is Float && t2 is Float -> (t1 + t2) as T
                t1 is Int && t2 is Int -> (t1 + t2) as T
                else -> t1
            }
        }

        private fun _Minus(t1: T, t2: T): T {
            return when {
                t1 is idVec3 && t2 is idVec3 -> (t1 - t2) as T
                t1 is idVec4 && t2 is idVec4 -> (t1 - t2) as T
                t1 is idAngles && t2 is idAngles -> (t1 - t2) as T
                t1 is Float && t2 is Float -> (t1 - t2) as T
                t1 is Int && t2 is Int -> (t1 - t2) as T
                else -> t1
            }
        }

        init {
            startTime = 0.0f
            duration = 0.0f
            currentTime = 0.0f
            currentValue = null
            startValue = null
            endValue = null
            // Initialize with default values based on common types
        }
    }

    /*
     ==============================================================================================

     Continuous interpolation with linear acceleration and deceleration phase.
     The velocity is continuous but the acceleration is not.

     ==============================================================================================
     */
    class idInterpolateAccelDecelLinear<T> : SERiAL {
        private var accelTime: Float = 0.0f
        private var decelTime = 0.0f
        private var endValue: T? = null
        private val extrapolate: idExtrapolate<T>
        private var linearTime: Float = 0.0f
        private var startTime: Float = 0.0f
        private var startValue: T? = null
        fun Init(
            startTime: Float,
            accelTime: Float,
            decelTime: Float,
            duration: Float,
            startValue: T,
            endValue: T
        ) {
            val speed: T
            this.startTime = startTime
            this.accelTime = accelTime
            this.decelTime = decelTime
            this.startValue = startValue
            this.endValue = endValue
            if (duration <= 0) {
                return
            }
            if (this.accelTime + this.decelTime > duration) {
                this.accelTime = this.accelTime * duration / (this.accelTime + this.decelTime)
                this.decelTime = duration - this.accelTime
            }
            linearTime = duration - this.accelTime - this.decelTime
            speed = _Multiply(
                _Minus(endValue, startValue),
                1000.0f / (linearTime + (this.accelTime + this.decelTime) * 0.5f)
            )
            if (this.accelTime != 0.0f) {
                extrapolate.Init(
                    startTime,
                    this.accelTime,
                    startValue,
                    _Minus(startValue, startValue),
                    speed,
                    Extrapolate.EXTRAPOLATION_ACCELLINEAR
                )
            } else if (linearTime != 0.0f) {
                extrapolate.Init(
                    startTime,
                    linearTime,
                    startValue,
                    _Minus(startValue, startValue),
                    speed,
                    Extrapolate.EXTRAPOLATION_LINEAR
                )
            } else {
                extrapolate.Init(
                    startTime,
                    this.decelTime,
                    startValue,
                    _Minus(startValue, startValue),
                    speed,
                    Extrapolate.EXTRAPOLATION_DECELLINEAR
                )
            }
        }

        fun SetStartTime(time: Float) {
            startTime = time
            Invalidate()
        }

        fun SetStartValue(startValue: T) {
            this.startValue = startValue
            Invalidate()
        }

        fun SetEndValue(endValue: T) {
            this.endValue = endValue
            Invalidate()
        }

        fun GetCurrentValue(time: Float): T {
            SetPhase(time)
            return extrapolate.GetCurrentValue(time)
        }

        fun GetCurrentSpeed(time: Float): T {
            SetPhase(time)
            return extrapolate.GetCurrentSpeed(time)
        }

        fun IsDone(time: Float): Boolean {
            return time >= startTime + accelTime + linearTime + decelTime
        }

        fun GetStartTime(): Float {
            return startTime
        }

        fun GetEndTime(): Float {
            return startTime + accelTime + linearTime + decelTime
        }

        fun GetDuration(): Float {
            return accelTime + linearTime + decelTime
        }

        fun GetAcceleration(): Float {
            return accelTime
        }

        fun GetDeceleration(): Float {
            return decelTime
        }

        fun GetStartValue(): T {
            return startValue!!
        }

        fun GetEndValue(): T {
            return endValue!!
        }

        private fun Invalidate() {
            extrapolate.Init(
                0f,
                0f,
                extrapolate.GetStartValue(),
                extrapolate.GetBaseSpeed(),
                extrapolate.GetSpeed(),
                Extrapolate.EXTRAPOLATION_NONE
            )
        }

        private fun SetPhase(time: Float) {
            val deltaTime: Float = time - startTime
            if (deltaTime < accelTime) {
                if (extrapolate.GetExtrapolationType() != Extrapolate.EXTRAPOLATION_ACCELLINEAR) {
                    extrapolate.Init(
                        startTime,
                        accelTime,
                        startValue!!,
                        extrapolate.GetBaseSpeed(),
                        extrapolate.GetSpeed(),
                        Extrapolate.EXTRAPOLATION_ACCELLINEAR
                    )
                }
            } else if (deltaTime < accelTime + linearTime) {
                if (extrapolate.GetExtrapolationType() != Extrapolate.EXTRAPOLATION_LINEAR) {
                    extrapolate.Init(
                        startTime + accelTime,
                        linearTime,
                        _Plus(startValue!!, _Multiply(extrapolate.GetSpeed()!!, (accelTime * 0.001f * 0.5f))),
                        extrapolate.GetBaseSpeed(),
                        extrapolate.GetSpeed(),
                        Extrapolate.EXTRAPOLATION_LINEAR
                    )
                }
            } else {
                if (extrapolate.GetExtrapolationType() != Extrapolate.EXTRAPOLATION_DECELLINEAR) {
                    extrapolate.Init(
                        startTime + accelTime + linearTime,
                        decelTime,
                        _Minus(endValue!!, _Multiply(extrapolate.GetSpeed()!!, (decelTime * 0.001f * 0.5f))),
                        extrapolate.GetBaseSpeed(),
                        extrapolate.GetSpeed(),
                        Extrapolate.EXTRAPOLATION_DECELLINEAR
                    )
                }
            }
        }

        override fun AllocBuffer(): ByteBuffer {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        override fun Read(buffer: ByteBuffer) {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        override fun Write(): ByteBuffer {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        private fun _Multiply(t: T, f: Float): T {
            return when (t) {
                is idVec3 -> (t * f) as T
                is idVec4 -> (t * f) as T
                is idAngles -> (t * f) as T
                is Float -> (f * t) as T
                is Int -> (f * t).toInt() as T
                else -> t
            }
        }

        private fun _Plus(t1: T, t2: T): T {
            return when {
                t1 is idVec3 && t2 is idVec3 -> (t1 + t2) as T
                t1 is idVec4 && t2 is idVec4 -> (t1 + t2) as T
                t1 is idAngles && t2 is idAngles -> (t1 + t2) as T
                t1 is Float && t2 is Float -> (t1 + t2) as T
                t1 is Int && t2 is Int -> (t1 + t2) as T
                else -> t1
            }
        }

        private fun _Minus(t1: T, t2: T): T {
            return when {
                t1 is idVec3 && t2 is idVec3 -> (t1 - t2) as T
                t1 is idVec4 && t2 is idVec4 -> (t1 - t2) as T
                t1 is idAngles && t2 is idAngles -> (t1 - t2) as T
                t1 is Float && t2 is Float -> (t1 - t2) as T
                t1 is Int && t2 is Int -> (t1 - t2) as T
                else -> t1
            }
        }

        init {
            linearTime = 0f
            decelTime = 0f
            accelTime = 0f
            startTime = 0f
            //	memset( &startValue, 0, sizeof( startValue ) );
            extrapolate = idExtrapolate()
        }
    }

    /*
     ==============================================================================================

     Continuous interpolation with sinusoidal acceleration and deceleration phase.
     Both the velocity and acceleration are continuous.

     ==============================================================================================
     */
    class idInterpolateAccelDecelSine<T> {
        private var accelTime: Float = 0.0f
        private var decelTime: Float = 0.0f
        private var endValue: T? = null
        private val extrapolate: idExtrapolate<T>
        private var linearTime: Float = 0f
        private var startTime: Float = 0f
        private var startValue: T? = null
        fun Init(
            startTime: Float,
            accelTime: Float,
            decelTime: Float,
            duration: Float,
            startValue: T,
            endValue: T
        ) {
            val speed: T
            this.startTime = startTime
            this.accelTime = accelTime
            this.decelTime = decelTime
            this.startValue = startValue
            this.endValue = endValue
            if (duration <= 0) {
                return
            }
            if (this.accelTime + this.decelTime > duration) {
                this.accelTime = this.accelTime * duration / (this.accelTime + this.decelTime)
                this.decelTime = duration - this.accelTime
            }
            linearTime = duration - this.accelTime - this.decelTime
            speed = _Multiply(
                _Minus(endValue!!, startValue!!),
                1000.0f / (linearTime + (this.accelTime + this.decelTime) * idMath.SQRT_1OVER2)
            )
            if (this.accelTime != 0.0f) {
                extrapolate.Init(
                    startTime,
                    this.accelTime,
                    startValue,
                    _Minus(startValue, startValue),
                    speed,
                    Extrapolate.EXTRAPOLATION_ACCELSINE
                )
            } else if (linearTime != 0.0f) {
                extrapolate.Init(
                    startTime,
                    linearTime,
                    startValue,
                    _Minus(startValue, startValue),
                    speed,
                    Extrapolate.EXTRAPOLATION_LINEAR
                )
            } else {
                extrapolate.Init(
                    startTime,
                    this.decelTime,
                    startValue,
                    _Minus(startValue, startValue),
                    speed,
                    Extrapolate.EXTRAPOLATION_DECELSINE
                )
            }
        }

        fun SetStartTime(time: Float) {
            startTime = time
            Invalidate()
        }

        fun SetStartValue(startValue: T) {
            this.startValue = startValue
            Invalidate()
        }

        fun SetEndValue(endValue: T) {
            this.endValue = endValue
            Invalidate()
        }

        fun GetCurrentValue(time: Float): T {
            SetPhase(time)
            return extrapolate.GetCurrentValue(time)
        }

        fun GetCurrentSpeed(time: Float): T {
            SetPhase(time)
            return extrapolate.GetCurrentSpeed(time)
        }

        fun IsDone(time: Float): Boolean {
            return time >= startTime + accelTime + linearTime + decelTime
        }

        fun GetStartTime(): Float {
            return startTime
        }

        fun GetEndTime(): Float {
            return startTime + accelTime + linearTime + decelTime
        }

        fun GetDuration(): Float {
            return accelTime + linearTime + decelTime
        }

        fun GetAcceleration(): Float {
            return accelTime
        }

        fun GetDeceleration(): Float {
            return decelTime
        }

        fun GetStartValue(): T {
            return startValue!!
        }

        fun GetEndValue(): T {
            return endValue!!
        }

        private fun Invalidate() {
            extrapolate.Init(
                0f,
                0f,
                extrapolate.GetStartValue(),
                extrapolate.GetBaseSpeed(),
                extrapolate.GetSpeed(),
                Extrapolate.EXTRAPOLATION_NONE
            )
        }

        private fun SetPhase(time: Float) {
            val deltaTime: Float = time - startTime
            if (deltaTime < accelTime) {
                if (extrapolate.GetExtrapolationType() != Extrapolate.EXTRAPOLATION_ACCELSINE) {
                    extrapolate.Init(
                        startTime,
                        accelTime,
                        startValue!!,
                        extrapolate.GetBaseSpeed(),
                        extrapolate.GetSpeed(),
                        Extrapolate.EXTRAPOLATION_ACCELSINE
                    )
                }
            } else if (deltaTime < accelTime + linearTime) {
                if (extrapolate.GetExtrapolationType() != Extrapolate.EXTRAPOLATION_LINEAR) {
                    extrapolate.Init(
                        startTime + accelTime,
                        linearTime,
                        _Plus(
                            startValue!!,
                            _Multiply(extrapolate.GetSpeed()!!, (accelTime * 0.001f * idMath.SQRT_1OVER2))
                        ),
                        extrapolate.GetBaseSpeed(),
                        extrapolate.GetSpeed(),
                        Extrapolate.EXTRAPOLATION_LINEAR
                    )
                }
            } else {
                if (extrapolate.GetExtrapolationType() != Extrapolate.EXTRAPOLATION_DECELSINE) {
                    extrapolate.Init(
                        startTime + accelTime + linearTime,
                        decelTime,
                        _Minus(
                            endValue!!,
                            _Multiply(extrapolate.GetSpeed()!!, (decelTime * 0.001f * idMath.SQRT_1OVER2))
                        ),
                        extrapolate.GetBaseSpeed(),
                        extrapolate.GetSpeed(),
                        Extrapolate.EXTRAPOLATION_DECELSINE
                    )
                }
            }
        }

        private fun _Multiply(t: T, f: Float): T {
            return when (t) {
                is idVec3 -> (t * f) as T
                is idVec4 -> (t * f) as T
                is idAngles -> (t * f) as T
                is Float -> (f * t) as T
                is Int -> (f * t).toInt() as T
                else -> t
            }
        }

        private fun _Plus(t1: T, t2: T): T {
            return when {
                t1 is idVec3 && t2 is idVec3 -> (t1 + t2) as T
                t1 is idVec4 && t2 is idVec4 -> (t1 + t2) as T
                t1 is idAngles && t2 is idAngles -> (t1 + t2) as T
                t1 is Float && t2 is Float -> (t1 + t2) as T
                t1 is Int && t2 is Int -> (t1 + t2) as T
                else -> t1
            }
        }

        private fun _Minus(t1: T, t2: T): T {
            return when {
                t1 is idVec3 && t2 is idVec3 -> (t1 - t2) as T
                t1 is idVec4 && t2 is idVec4 -> (t1 - t2) as T
                t1 is idAngles && t2 is idAngles -> (t1 - t2) as T
                t1 is Float && t2 is Float -> (t1 - t2) as T
                t1 is Int && t2 is Int -> (t1 - t2) as T
                else -> t1
            }
        }

        init {
            linearTime = 0f
            decelTime = 0f
            accelTime = 0f
            startTime = 0f
            //	memset( &startValue, 0, sizeof( startValue ) );
            extrapolate = idExtrapolate()
        }
    }
}
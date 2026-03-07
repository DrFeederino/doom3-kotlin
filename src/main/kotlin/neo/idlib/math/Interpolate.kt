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
            this.startValue = _Copy(startValue)
            this.endValue = _Copy(endValue)
            currentTime = startTime - 1
            currentValue = _Copy(startValue)
        }

        fun SetStartTime(time: Float) {
            startTime = time
        }

        fun SetDuration(duration: Float) {
            this.duration = duration
        }

        fun SetStartValue(startValue: T) {
            this.startValue = _Copy(startValue)
        }

        fun SetEndValue(endValue: T) {
            this.endValue = _Copy(endValue)
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

        private fun _Copy(t: T?): T? {
            if (t == null) return null
            return when (t) {
                is idVec3 -> idVec3(t) as T
                is idVec4 -> idVec4(t) as T
                is idAngles -> idAngles(t) as T
                else -> t // Float, Int are immutable
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
            this.startValue = _Copy(startValue)
            this.endValue = _Copy(endValue)
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
            this.startValue = _Copy(startValue)
            Invalidate()
        }

        fun SetEndValue(endValue: T) {
            this.endValue = _Copy(endValue)
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
            val typeSize = _sizeOfT()
            // 4 floats + 2*typeSize + extrapolate(int + 2*float + 3*typeSize + float + typeSize)
            // = 16 + 2*typeSize + 12 + 4*typeSize = 28 + 6*typeSize
            val bytes = 4 * java.lang.Float.BYTES + 2 * typeSize +
                    java.lang.Integer.BYTES + 2 * java.lang.Float.BYTES + 3 * typeSize +
                    java.lang.Float.BYTES + typeSize
            return ByteBuffer.allocate(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        }

        override fun Read(buffer: ByteBuffer) {
            buffer.order(java.nio.ByteOrder.LITTLE_ENDIAN)
            startTime = buffer.float
            accelTime = buffer.float
            linearTime = buffer.float
            decelTime = buffer.float
            startValue = _readValue(buffer)
            endValue = _readValue(buffer)
            // idExtrapolate fields
            val extType = buffer.int
            val extStartTime = buffer.float
            val extDuration = buffer.float
            val extStartValue = _readValue(buffer)
            val extBaseSpeed = _readValue(buffer)
            val extSpeed = _readValue(buffer)
            buffer.float // currentTime
            _readValue(buffer) // currentValue - discard
            extrapolate.Init(extStartTime, extDuration, extStartValue, extBaseSpeed, extSpeed, extType)
        }

        override fun Write(): ByteBuffer {
            val buffer = AllocBuffer()
            buffer.putFloat(startTime)
            buffer.putFloat(accelTime)
            buffer.putFloat(linearTime)
            buffer.putFloat(decelTime)
            _writeValue(buffer, startValue)
            _writeValue(buffer, endValue)
            // idExtrapolate fields
            buffer.putInt(extrapolate.GetExtrapolationType())
            buffer.putFloat(extrapolate.GetStartTime())
            buffer.putFloat(extrapolate.GetDuration())
            _writeValue(buffer, extrapolate.GetStartValue())
            _writeValue(buffer, extrapolate.GetBaseSpeed())
            _writeValue(buffer, extrapolate.GetSpeed())
            buffer.putFloat(-1.0f) // currentTime
            _writeValue(buffer, extrapolate.GetCurrentValue(extrapolate.GetStartTime())) // currentValue
            buffer.flip()
            return buffer
        }

        @Suppress("UNCHECKED_CAST")
        private fun _readValue(buffer: ByteBuffer): T {
            // Detect type from existing startValue or endValue, or from extrapolate
            val sample = startValue ?: endValue ?: extrapolate.GetStartValue()
            return when (sample) {
                is Int -> buffer.int as T
                is Float -> buffer.float as T
                is idVec3 -> idVec3(buffer.float, buffer.float, buffer.float) as T
                is idVec4 -> idVec4(buffer.float, buffer.float, buffer.float, buffer.float) as T
                is idAngles -> idAngles(buffer.float, buffer.float, buffer.float) as T
                else -> buffer.int as T // fallback
            }
        }

        private fun _writeValue(buffer: ByteBuffer, value: T?) {
            when (value) {
                is Int -> buffer.putInt(value)
                is Float -> buffer.putFloat(value)
                is idVec3 -> {
                    buffer.putFloat(value[0]); buffer.putFloat(value[1]); buffer.putFloat(value[2])
                }

                is idVec4 -> {
                    buffer.putFloat(value[0]); buffer.putFloat(value[1]); buffer.putFloat(value[2]); buffer.putFloat(
                        value[3]
                    )
                }

                is idAngles -> {
                    buffer.putFloat(value.pitch); buffer.putFloat(value.yaw); buffer.putFloat(value.roll)
                }

                else -> buffer.putInt(0) // fallback
            }
        }

        private fun _sizeOfT(): Int {
            val sample = startValue ?: endValue ?: extrapolate.GetStartValue()
            return when (sample) {
                is Int -> 4
                is Float -> 4
                is idVec3 -> 12
                is idVec4 -> 16
                is idAngles -> 12
                else -> 4
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

        @Suppress("UNCHECKED_CAST")
        private fun _Copy(t: T?): T? {
            if (t == null) return null
            return when (t) {
                is idVec3 -> idVec3(t) as T
                is idVec4 -> idVec4(t) as T
                is idAngles -> idAngles(t) as T
                else -> t // Float, Int are immutable
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
            this.startValue = _Copy(startValue)
            this.endValue = _Copy(endValue)
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
            this.startValue = _Copy(startValue)
            Invalidate()
        }

        fun SetEndValue(endValue: T) {
            this.endValue = _Copy(endValue)
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

        @Suppress("UNCHECKED_CAST")
        private fun _Copy(t: T?): T? {
            if (t == null) return null
            return when (t) {
                is idVec3 -> idVec3(t) as T
                is idVec4 -> idVec4(t) as T
                is idAngles -> idAngles(t) as T
                else -> t // Float, Int are immutable
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
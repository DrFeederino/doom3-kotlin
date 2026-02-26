package neo.idlib.math

object Extrapolate {
    const val EXTRAPOLATION_ACCELLINEAR =
        0x04 // linear acceleration, covered distance = duration * 0.001 * ( baseSpeed + 0.5f * speed )
    const val EXTRAPOLATION_ACCELSINE =
        0x10 // sinusoidal acceleration, covered distance = duration * 0.001 * ( baseSpeed + sqrt( 0.5f ) * speed )
    const val EXTRAPOLATION_DECELLINEAR =
        0x08 // linear deceleration, covered distance = duration * 0.001 * ( baseSpeed + 0.5f * speed )
    const val EXTRAPOLATION_DECELSINE =
        0x20 // sinusoidal deceleration, covered distance = duration * 0.001 * ( baseSpeed + sqrt( 0.5f ) * speed )
    const val EXTRAPOLATION_LINEAR =
        0x02 // linear extrapolation, covered distance = duration * 0.001 * ( baseSpeed + speed )
    const val EXTRAPOLATION_NONE = 0x01 // no extrapolation, covered distance = duration * 0.001 * ( baseSpeed )
    const val EXTRAPOLATION_NOSTOP = 0x40 // do not stop at startTime + duration

    /*
     ==============================================================================================

     Extrapolate

     ==============================================================================================
     */
    //where T: TempDump.Settable<T>
    class idExtrapolate<T> {
        private var baseSpeed: T? = null
        private var currentTime: Float = 0.0f
        private var currentValue: T? = null
        private var duration: Float = 0.0f
        private var extrapolationType: Int
        private var speed: T? = null
        private var startTime: Float = 0.0f
        private var startValue: T? = null

        fun Init(
            startTime: Float,
            duration: Float,
            startValue: T?,
            baseSpeed: T?,
            speed: T?,
            extrapolationType: Int
        ) {
            this.extrapolationType = extrapolationType
            this.startTime = startTime
            this.duration = duration
            this.startValue = startValue
            this.baseSpeed = baseSpeed
            this.speed = speed
            currentTime = -1.0f
            currentValue = startValue
        }

        fun GetCurrentValue(time: Float): T {
            var time = time
            val deltaTime: Float
            val s: Float

            if (time == currentTime) {
                return currentValue!!
            }

            currentTime = time

            if (time < startTime) {
                return startValue!!
            }

            if (0 == (extrapolationType and EXTRAPOLATION_NOSTOP) && (time > startTime + duration)) {
                time = startTime + duration
            }
            when (extrapolationType and EXTRAPOLATION_NOSTOP.inv()) {
                EXTRAPOLATION_NONE -> {
                    deltaTime = (time - startTime) * 0.001f
                    currentValue = _Plus(startValue!!, _Multiply(deltaTime, baseSpeed!!))
                }

                EXTRAPOLATION_LINEAR -> {
                    deltaTime = (time - startTime) * 0.001f
                    currentValue = _Plus(startValue!!, _Multiply(deltaTime, _Plus(baseSpeed!!, speed!!)))
                }

                EXTRAPOLATION_ACCELLINEAR -> {
                    if (0.0f == duration) {
                        currentValue = startValue
                    } else {
                        deltaTime = (time - startTime) / duration
                        s = (0.5f * deltaTime * deltaTime) * (duration * 0.001f)
                        currentValue =
                            _Plus(startValue!!, _Plus(_Multiply(deltaTime, baseSpeed!!), _Multiply(s, speed!!)))
                    }
                }

                EXTRAPOLATION_DECELLINEAR -> {
                    if (0.0f == duration) {
                        currentValue = startValue
                    } else {
                        deltaTime = (time - startTime) / duration
                        s = (deltaTime - (0.5f * deltaTime * deltaTime)) * (duration * 0.001f)
                        currentValue =
                            _Plus(startValue!!, _Plus(_Multiply(deltaTime, baseSpeed!!), _Multiply(s, speed!!)))
                    }
                }

                EXTRAPOLATION_ACCELSINE -> {
                    if (0.0f == duration) {
                        currentValue = startValue
                    } else {
                        deltaTime = (time - startTime) / duration
                        s = (1.0f - idMath.Cos(deltaTime * idMath.HALF_PI)) * duration * 0.001f * idMath.SQRT_1OVER2
                        currentValue =
                            _Plus(startValue!!, _Plus(_Multiply(deltaTime, baseSpeed!!), _Multiply(s, speed!!)))
                    }
                }

                EXTRAPOLATION_DECELSINE -> {
                    if (0.0f == duration) {
                        currentValue = startValue
                    } else {
                        deltaTime = (time - startTime) / duration
                        s = idMath.Sin(deltaTime * idMath.HALF_PI) * duration * 0.001f * idMath.SQRT_1OVER2
                        currentValue =
                            _Plus(startValue!!, _Plus(_Multiply(deltaTime, baseSpeed!!), _Multiply(s, speed!!)))
                    }
                }

                else -> {
                    currentValue = startValue
                }
            }
            return currentValue!!
        }

        fun GetCurrentSpeed(time: Float): T {
            val deltaTime: Float
            val s: Float
            if (time < startTime || 0.0f == duration) {
                return _Minus(startValue!!, startValue!!)
            }

            if ((extrapolationType and EXTRAPOLATION_NOSTOP) == 0 && (time > startTime + duration)) {
                return _Minus(startValue!!, startValue!!)
            }

            return when (extrapolationType and EXTRAPOLATION_NOSTOP.inv()) {
                EXTRAPOLATION_NONE -> {
                    return baseSpeed!!
                }

                EXTRAPOLATION_LINEAR -> {
                    _Plus(baseSpeed!!, speed!!)
                }

                EXTRAPOLATION_ACCELLINEAR -> {
                    deltaTime = (time - startTime) / duration
                    s = deltaTime
                    return _Plus(baseSpeed!!, _Multiply(s, speed!!))
                }

                EXTRAPOLATION_DECELLINEAR -> {
                    deltaTime = (time - startTime) / duration
                    s = 1.0f - deltaTime
                    return _Plus(baseSpeed!!, _Multiply(s, speed!!))
                }

                EXTRAPOLATION_ACCELSINE -> {
                    deltaTime = (time - startTime) / duration
                    s = idMath.Sin(deltaTime * idMath.HALF_PI)
                    return _Plus(baseSpeed!!, _Multiply(s, speed!!))
                }

                EXTRAPOLATION_DECELSINE -> {
                    deltaTime = (time - startTime) / duration
                    s = idMath.Cos(deltaTime * idMath.HALF_PI)
                    return _Plus(baseSpeed!!, _Multiply(s, speed!!))
                }

                else -> {
                    return baseSpeed!!
                }
            } as T
        }

        fun IsDone(time: Float): Boolean {
            return 0 == (extrapolationType and EXTRAPOLATION_NOSTOP) && time >= startTime + duration
        }

        fun SetStartTime(time: Float) {
            startTime = time
            currentTime = -1.0f
        }

        fun GetStartTime(): Float {
            return startTime
        }

        fun GetEndTime(): Float {
            return if (0 == (extrapolationType and EXTRAPOLATION_NOSTOP) && duration > 0) startTime + duration else 0.0f
        }

        fun GetDuration(): Float {
            return duration
        }

        fun SetStartValue(value: T?) {
            startValue = value
            currentTime = -1.0f
        }

        fun GetStartValue(): T? {
            return startValue
        }

        fun GetBaseSpeed(): T? {
            return baseSpeed
        }

        fun GetSpeed(): T? {
            return speed
        }

        fun GetExtrapolationType(): Int {
            return extrapolationType
        }

        private fun _Multiply(f: Float, t: T): T {
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
            extrapolationType = EXTRAPOLATION_NONE
            duration = 0.0f
            startTime = 0.0f
            currentTime = -1.0f
            startValue = null
            baseSpeed = null
            speed = null
            currentValue = null
        }
    }
}
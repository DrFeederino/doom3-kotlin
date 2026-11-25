/*
===========================================================================

Doom 3 GPL Source Code
Copyright (C) 1999-2011 id Software LLC, a ZeniMax Media company.

This file is part of the Doom 3 GPL Source Code ("Doom 3 Source Code").

Doom 3 Source Code is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

Doom 3 Source Code is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with Doom 3 Source Code.  If not, see <http://www.gnu.org/licenses/>.

In addition, the Doom 3 Source Code is also subject to certain additional terms. You should have received a copy of these additional terms immediately following the terms and conditions of the GNU General Public License which accompanied the Doom 3 Source Code.  If not, please request a copy in writing from id Software at the address below.

If you have questions concerning this license or the applicable additional terms, you may contact in writing id Software LLC, c/o ZeniMax Media Inc., Suite 120, Rockville, Maryland 20850 USA.

===========================================================================
*/

package neo.idlib.math

import neo.framework.File_h.idFile
import neo.idlib.idSerializable
import neo.idlib.math.Extrapolate.idExtrapolate

class Interpolate {
    /*
     ==============================================================================================

     Linear interpolation.

     ==============================================================================================
     */
    class idInterpolate<T>(value: T) {
        private var currentTime: Float = 0.0f
        private var currentValue: T
        private var duration = 0.0f
        private var endValue: T
        private var startTime: Float = 0.0f
        private var startValue: T
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
                    deltaTime <= 0 -> currentValue = _Copy(startValue)
                    deltaTime >= duration -> currentValue = endValue
                    else -> {
                        currentValue =
                            _Plus(startValue!!, _Multiply(_Minus(endValue!!, startValue!!), deltaTime / duration))
                    }
                }
            }
            return currentValue
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
            return startValue
        }

        fun GetEndValue(): T {
            return endValue
        }

        private fun _Multiply(t: T, f: Float): T {
            return when (t) {
                is idVec3 -> (t * f) as T
                is idVec4 -> (t * f) as T
                is idAngles -> (t * f) as T
                is Float -> (t * f) as T
                is Int -> (t * f).toInt() as T
                else -> throw UnsupportedOperationException("Illegal use of _Multiply().")
            }
        }

        private fun _Plus(t1: T, t2: T): T {
            return when {
                t1 is idVec3 && t2 is idVec3 -> (t1 + t2) as T
                t1 is idVec4 && t2 is idVec4 -> (t1 + t2) as T
                t1 is idAngles && t2 is idAngles -> (t1 + t2) as T
                t1 is Float && t2 is Float -> (t1 + t2) as T
                t1 is Int && t2 is Int -> (t1 + t2) as T
                else -> throw UnsupportedOperationException("Illegal use of _Plus().")
            }
        }

        private fun _Minus(t1: T, t2: T): T {
            return when {
                t1 is idVec3 && t2 is idVec3 -> (t1 - t2) as T
                t1 is idVec4 && t2 is idVec4 -> (t1 - t2) as T
                t1 is idAngles && t2 is idAngles -> (t1 - t2) as T
                t1 is Float && t2 is Float -> (t1 - t2) as T
                t1 is Int && t2 is Int -> (t1 - t2) as T
                else -> throw UnsupportedOperationException("Illegal use of _Minus().")
            }
        }

        private fun _Copy(t: T): T {
            return when (t) {
                is Int -> t
                is Float -> t
                is idVec3 -> idVec3(t) as T
                is idVec4 -> idVec4(t) as T
                is idAngles -> idAngles(t) as T
                else -> throw UnsupportedOperationException("Illegal use of _Copy().")
            }
        }

        init {
            startTime = 0.0f
            duration = 0.0f
            currentTime = 0.0f
            currentValue = _Copy(value)
            startValue = _Copy(value)
            endValue = _Copy(value) // Initialize with default values based on common types
        }
    }

    /*
     ==============================================================================================

     Continuous interpolation with linear acceleration and deceleration phase.
     The velocity is continuous but the acceleration is not.

     ==============================================================================================
     */
    class idInterpolateAccelDecelLinear<T>(val value: T) : idSerializable {
        private var accelTime: Float = 0.0f
        private var decelTime = 0.0f
        private var endValue: T = _Copy(value)
        private val extrapolate: idExtrapolate<T>
        private var linearTime: Float = 0.0f
        private var startTime: Float = 0.0f
        private var startValue: T = _Copy(value)
        fun Init(
            startTime: Float, accelTime: Float, decelTime: Float, duration: Float, startValue: T, endValue: T
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
                _Minus(endValue, startValue), 1000.0f / (linearTime + (this.accelTime + this.decelTime) * 0.5f)
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
            return startValue
        }

        fun GetEndValue(): T {
            return endValue
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

        override fun readFrom(file: idFile) {
            startTime = file.ReadFloat()
            accelTime = file.ReadFloat()
            linearTime = file.ReadFloat()
            decelTime = file.ReadFloat()
            startValue = _readValue(file)
            endValue = _readValue(file) // idExtrapolate fields
            val extType = file.ReadInt()
            val extStartTime = file.ReadFloat()
            val extDuration = file.ReadFloat()
            val extStartValue = _readValue(file)
            val extBaseSpeed = _readValue(file)
            val extSpeed = _readValue(file)
            file.ReadFloat() // currentTime
            _readValue(file) // currentValue - discard
            extrapolate.Init(extStartTime, extDuration, extStartValue, extBaseSpeed, extSpeed, extType)
        }

        override fun writeTo(file: idFile) {
            file.WriteFloat(startTime)
            file.WriteFloat(accelTime)
            file.WriteFloat(linearTime)
            file.WriteFloat(decelTime)
            _writeValue(file, startValue)
            _writeValue(file, endValue) // idExtrapolate fields
            file.WriteInt(extrapolate.GetExtrapolationType())
            file.WriteFloat(extrapolate.GetStartTime())
            file.WriteFloat(extrapolate.GetDuration())
            _writeValue(file, extrapolate.GetStartValue())
            _writeValue(file, extrapolate.GetBaseSpeed())
            _writeValue(file, extrapolate.GetSpeed())
            file.WriteFloat(-1.0f) // currentTime
            _writeValue(file, extrapolate.GetCurrentValue(extrapolate.GetStartTime())) // currentValue
        }

        private fun _readValue(file: idFile): T {
            return when (value) {
                is Int -> file.ReadInt() as T
                is Float -> file.ReadFloat() as T
                is idVec3 -> idVec3(file.ReadFloat(), file.ReadFloat(), file.ReadFloat()) as T
                is idVec4 -> idVec4(file.ReadFloat(), file.ReadFloat(), file.ReadFloat(), file.ReadFloat()) as T
                is idAngles -> idAngles(file.ReadFloat(), file.ReadFloat(), file.ReadFloat()) as T
                else -> file.ReadInt() as T
            }
        }

        private fun _writeValue(file: idFile, value: T?) {
            when (value) {
                is Int -> file.WriteInt(value)
                is Float -> file.WriteFloat(value)
                is idVec3 -> {
                    file.WriteFloat(value[0]); file.WriteFloat(value[1]); file.WriteFloat(value[2])
                }

                is idVec4 -> {
                    file.WriteFloat(value[0]); file.WriteFloat(value[1]); file.WriteFloat(value[2]); file.WriteFloat(
                        value[3]
                    )
                }

                is idAngles -> {
                    file.WriteFloat(value.pitch); file.WriteFloat(value.yaw); file.WriteFloat(value.roll)
                }

                else -> file.WriteInt(0)
            }
        }

        private fun _sizeOfT(): Int {
            return when (value) {
                is Int -> Int.SIZE_BYTES
                is Float -> Float.SIZE_BYTES
                is idVec3 -> idVec3.BYTES
                is idVec4 -> idVec4.BYTES
                is idAngles -> idAngles.BYTES
                else -> throw UnsupportedOperationException("Cannot tell the sizeOf.")
            }
        }

        private fun _Multiply(t: T, f: Float): T {
            return when (t) {
                is idVec3 -> (t * f) as T
                is idVec4 -> (t * f) as T
                is idAngles -> (t * f) as T
                is Float -> (f * t) as T
                is Int -> (f * t).toInt() as T
                else -> throw UnsupportedOperationException("Illegal use of _Multiply().")
            }
        }

        private fun _Plus(t1: T, t2: T): T {
            return when {
                t1 is idVec3 && t2 is idVec3 -> (t1 + t2) as T
                t1 is idVec4 && t2 is idVec4 -> (t1 + t2) as T
                t1 is idAngles && t2 is idAngles -> (t1 + t2) as T
                t1 is Float && t2 is Float -> (t1 + t2) as T
                t1 is Int && t2 is Int -> (t1 + t2) as T
                else -> throw UnsupportedOperationException("Illegal use of _Plus().")
            }
        }

        private fun _Minus(t1: T, t2: T): T {
            return when {
                t1 is idVec3 && t2 is idVec3 -> (t1 - t2) as T
                t1 is idVec4 && t2 is idVec4 -> (t1 - t2) as T
                t1 is idAngles && t2 is idAngles -> (t1 - t2) as T
                t1 is Float && t2 is Float -> (t1 - t2) as T
                t1 is Int && t2 is Int -> (t1 - t2) as T
                else -> throw UnsupportedOperationException("Illegal use of _Minus().")
            }
        }

        private fun _Copy(t: T): T {
            return when (t) {
                is Int -> t
                is Float -> t
                is idVec3 -> idVec3(t) as T
                is idVec4 -> idVec4(t) as T
                is idAngles -> idAngles(t) as T
                else -> throw UnsupportedOperationException("Illegal use of _Copy().")
            }
        }

        init {
            linearTime = 0f
            decelTime = 0f
            accelTime = 0f
            startTime = 0f //	memset( &startValue, 0, sizeof( startValue ) );
            extrapolate = idExtrapolate(value)
        }
    }

    /*
     ==============================================================================================

     Continuous interpolation with sinusoidal acceleration and deceleration phase.
     Both the velocity and acceleration are continuous.

     ==============================================================================================
     */
    class idInterpolateAccelDecelSine<T>(private val value: T) {
        private var accelTime: Float = 0.0f
        private var decelTime: Float = 0.0f
        private var endValue: T = _Copy(value)
        private val extrapolate: idExtrapolate<T>
        private var linearTime: Float = 0f
        private var startTime: Float = 0f
        private var startValue: T = _Copy(value)
        fun Init(
            startTime: Float, accelTime: Float, decelTime: Float, duration: Float, startValue: T, endValue: T
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
            return startValue
        }

        fun GetEndValue(): T {
            return endValue
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
                        startTime + accelTime, linearTime, _Plus(
                            startValue!!, _Multiply(extrapolate.GetSpeed()!!, (accelTime * 0.001f * idMath.SQRT_1OVER2))
                        ), extrapolate.GetBaseSpeed(), extrapolate.GetSpeed(), Extrapolate.EXTRAPOLATION_LINEAR
                    )
                }
            } else {
                if (extrapolate.GetExtrapolationType() != Extrapolate.EXTRAPOLATION_DECELSINE) {
                    extrapolate.Init(
                        startTime + accelTime + linearTime, decelTime, _Minus(
                            endValue!!, _Multiply(extrapolate.GetSpeed()!!, (decelTime * 0.001f * idMath.SQRT_1OVER2))
                        ), extrapolate.GetBaseSpeed(), extrapolate.GetSpeed(), Extrapolate.EXTRAPOLATION_DECELSINE
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

        private fun _Copy(t: T): T {
            return when (t) {
                is Int -> t
                is Float -> t
                is idVec3 -> idVec3(t) as T
                is idVec4 -> idVec4(t) as T
                is idAngles -> idAngles(t) as T
                else -> throw UnsupportedOperationException("Illegal use of _Copy().")
            }
        }

        init {
            linearTime = 0f
            decelTime = 0f
            accelTime = 0f
            startTime = 0f //	memset( &startValue, 0, sizeof( startValue ) );
            extrapolate = idExtrapolate(value)
        }
    }
}
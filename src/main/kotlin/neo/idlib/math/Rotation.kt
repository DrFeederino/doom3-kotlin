package neo.idlib.math

import neo.idlib.containers.CFloat
import neo.idlib.math.Matrix.idMat3
import neo.idlib.math.Matrix.idMat4
import kotlin.math.floor

class idRotation {
    val origin: idVec3 = idVec3() // origin of rotation
    val vec: idVec3 = idVec3()    // normalized vector to rotate around
    var angle: Float = 0.0f       // angle of rotation in degrees
    val axis: idMat3 = idMat3()   // rotation axis
    var axisValid: Boolean = false // true if rotation axis is valid

    constructor()

    constructor(rotationOrigin: idVec3, rotationVec: idVec3, rotationAngle: Float) : this() {
        origin.set(rotationOrigin)
        vec.set(rotationVec)
        angle = rotationAngle
        axisValid = false
    }

    fun Set(rotationOrigin: idVec3, rotationVec: idVec3, rotationAngle: Float) {
        origin.set(rotationOrigin)
        vec.set(rotationVec)
        angle = rotationAngle
        axisValid = false
    }

    fun SetOrigin(rotationOrigin: idVec3) {
        origin.set(rotationOrigin)
    }

    fun SetVec(rotationVec: idVec3) {
        vec.set(rotationVec)
        axisValid = false
    }

    fun SetVec(x: Float, y: Float, z: Float) {
        vec[0] = x
        vec[1] = y
        vec[2] = z
        axisValid = false
    }

    fun SetAngle(rotationAngle: Float) {
        angle = rotationAngle
        axisValid = false
    }

    fun Scale(s: Float) {
        angle *= s
        axisValid = false
    }

    fun ReCalculateMatrix() {
        axisValid = false
        ToMat3()
    }

    fun GetOrigin(): idVec3 {
        return origin
    }

    fun GetVec(): idVec3 {
        return vec
    }

    fun GetAngle(): Float {
        return angle
    }

    operator fun times(s: Float): idRotation { // scale rotation
        return idRotation(origin, vec, angle * s)
    }

    operator fun times(v: idVec3): idVec3 { // rotate vector
        if (!axisValid) {
            ToMat3()
        }
        return (v - origin) * axis + origin
    }

    fun ToAngles(): idAngles {
        return ToMat3().ToAngles()
    }

    //	idQuat				ToQuat( void ) const;
    fun ToMat3(): idMat3 {
        if (axisValid) {
            return axis
        }

        val a = angle * (idMath.M_DEG2RAD * 0.5f)
        val c = CFloat()
        val s = CFloat()
        idMath.SinCos(a, s, c)

        val x = vec[0] * s._val
        val y = vec[1] * s._val
        val z = vec[2] * s._val

        val x2 = x + x
        val y2 = y + y
        val z2 = z + z

        val xx = x * x2
        val xy = x * y2
        val xz = x * z2
        val yy = y * y2
        val yz = y * z2
        val zz = z * z2

        val wx = c._val * x2
        val wy = c._val * y2
        val wz = c._val * z2

        axis[0][0] = 1.0f - (yy + zz)
        axis[0][1] = xy - wz
        axis[0][2] = xz + wy

        axis[1][0] = xy + wz
        axis[1][1] = 1.0f - (xx + zz)
        axis[1][2] = yz - wx

        axis[2][0] = xz - wy
        axis[2][1] = yz + wx
        axis[2][2] = 1.0f - (xx + yy)

        axisValid = true
        return axis
    }

    fun ToAngularVelocity(): idVec3 {
        return vec * DEG2RAD(angle)
    }

    fun RotatePoint(point: idVec3) {
        if (!axisValid) {
            ToMat3()
        }
        point.set((point - origin) * axis + origin)
    }

    operator fun unaryMinus(): idRotation { // flips rotation
        return idRotation(origin, vec, -angle)
    }

    operator fun div(s: Float): idRotation { // scale rotation
        assert(s != 0.0f)
        return idRotation(origin, vec, angle / s)
    }

    operator fun timesAssign(s: Float) { // scale rotation
        angle *= s
        axisValid = false
    }

    operator fun divAssign(s: Float) { // scale rotation
        assert(s != 0.0f)
        angle /= s
        axisValid = false
    }

    fun ToMat4(): idMat4 {
        return ToMat3().ToMat4()
    }

    fun ToQuat(): idQuat {
        val a = angle * (idMath.M_DEG2RAD * 0.5f)
        val s = CFloat()
        val c = CFloat()
        idMath.SinCos(a, s, c)
        return idQuat(vec.x * s._val, vec.y * s._val, vec.z * s._val, c._val)
    }

    fun Normalize360() {
        angle -= floor(angle / 360.0f) * 360.0f
        if (angle > 360.0f) {
            angle -= 360.0f
        } else if (angle < 0.0f) {
            angle += 360.0f
        }
    }

    fun Normalize180() {
        angle -= floor(angle / 360.0f) * 360.0f
        if (angle > 180.0f) {
            angle -= 360.0f
        } else if (angle < -180.0f) {
            angle += 360.0f
        }
    }
}

// Global operator functions to match C++ friend functions
operator fun Float.times(r: idRotation): idRotation { // scale rotation
    return r * this
}

operator fun idVec3.times(r: idRotation): idVec3 { // rotate vector
    return r * this
}

operator fun idVec3.timesAssign(r: idRotation) { // rotate vector
    this.set(r * this)
}
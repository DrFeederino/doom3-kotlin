package neo.idlib.BV

import neo.idlib.containers.CFloat
import neo.idlib.math.*
import java.util.*

class Sphere {
    /*
     ===============================================================================

     Sphere

     ===============================================================================
     */
    class idSphere {
        private val origin: idVec3
        private var radius = 0.0f

        constructor() {
            origin = idVec3()
        }

        constructor(point: idVec3) {
            origin = idVec3(point)
            radius = 0.0f
        }

        constructor(point: idVec3, r: Float) {
            origin = idVec3(point)
            radius = r
        }

        operator fun get(index: Int): Float {
            return origin[index]
        }

        operator fun set(index: Int, value: Float): Float {
            return origin.set(index, value)
        }

        operator fun plus(t: idVec3): idSphere {                // returns tranlated sphere
            return idSphere(origin + t, radius)
        }

        fun plusAssign(t: idVec3): idSphere {                    // translate the sphere
            origin.plusAssign(t)
            return this
        }

        fun Compare(a: idSphere): Boolean {                            // exact compare, no epsilon
            return origin.Compare(a.origin) && radius == a.radius
        }

        fun Compare(a: idSphere, epsilon: Float): Boolean {    // compare with epsilon
            return origin.Compare(a.origin, epsilon) && Math.abs(radius - a.radius) <= epsilon
        }

        override fun hashCode(): Int {
            var hash = 7
            hash = 97 * hash + Objects.hashCode(origin)
            hash = 97 * hash + radius.toBits().toInt()
            return hash
        }

        override fun equals(other: Any?): Boolean {
            if (other == null) {
                return false
            }
            if (javaClass != other.javaClass) {
                return false
            }
            val other = other as idSphere
            return Compare(other)
        }

        fun Clear() {                                    // inside out sphere
            origin.Zero()
            radius = -1.0f
        }

        fun Zero() {                                    // single point at origin
            origin.Zero()
            radius = 0.0f
        }

        fun SetOrigin(o: idVec3) {                    // set origin of sphere
            origin.set(o)
        }

        fun SetRadius(r: Float) {                        // set square radius
            radius = r
        }

        fun GetOrigin(): idVec3 {                        // returns origin of sphere
            return origin
        }

        fun GetRadius(): Float {                        // returns sphere radius
            return radius
        }

        fun IsCleared(): Boolean {                    // returns true if sphere is inside out
            return radius < 0.0f
        }

        fun AddPoint(p: idVec3): Boolean {                    // add the point, returns true if the sphere expanded
            return if (radius < 0.0f) {
                origin.set(p)
                radius = 0.0f
                true
            } else {
                var r = (p - origin).LengthSqr()
                if (r > radius * radius) {
                    r = idMath.Sqrt(r)
                    origin.plusAssign((p - origin) * 0.5f * (1.0f - radius / r))
                    radius += 0.5f * (r - radius)
                    return true
                }
                false
            }
        }

        fun AddSphere(s: idSphere): Boolean {                    // add the sphere, returns true if the sphere expanded
            return if (radius < 0.0f) {
                origin.set(s.origin)
                radius = s.radius
                true
            } else {
                var r = (s.origin - origin).LengthSqr()
                if (r > (radius + s.radius) * (radius + s.radius)) {
                    r = idMath.Sqrt(r)
                    origin.plusAssign((s.origin - origin) * 0.5f * (1.0f - radius / (r + s.radius)))
                    radius += 0.5f * (r + s.radius - radius)
                    return true
                }
                false
            }
        }

        fun Expand(d: Float): idSphere {                    // return bounds expanded in all directions with the given value
            return idSphere(origin, radius + d)
        }

        fun ExpandSelf(d: Float): idSphere {                    // expand bounds in all directions with the given value
            radius += d
            return this
        }

        fun Translate(translation: idVec3): idSphere {
            return idSphere(origin + translation, radius)
        }

        fun TranslateSelf(translation: idVec3): idSphere {
            origin.plusAssign(translation)
            return this
        }

        fun PlaneDistance(plane: idPlane): Float {
            val d: Float
            d = plane.Distance(origin)
            if (d > radius) {
                return d - radius
            }
            return if (d < -radius) {
                d + radius
            } else 0.0f
        }


        fun PlaneSide(plane: idPlane, epsilon: Float = ON_EPSILON): Int {
            val d: Float
            d = plane.Distance(origin)
            if (d > radius + epsilon) {
                return PLANESIDE_FRONT
            }
            return if (d < -radius - epsilon) {
                PLANESIDE_BACK
            } else PLANESIDE_CROSS
        }

        fun ContainsPoint(p: idVec3): Boolean {            // includes touching
            if ((p - origin).LengthSqr() > radius * radius) {
                return false
            }
            return true
        }

        fun IntersectsSphere(s: idSphere): Boolean {    // includes touching
            val r = s.radius + radius
            if ((s.origin - origin).LengthSqr() > r * r) {
                return false
            }
            return true
        }

        /*
         ============
         idSphere::LineIntersection

         Returns true if the line intersects the sphere between the start and end point.
         ============
         */
        fun LineIntersection(start: idVec3, end: idVec3): Boolean {
            val r = idVec3()
            val s = idVec3()
            val e = idVec3()
            val a: Float

            s.set(start - origin)
            e.set(end - origin)
            r.set(e - s)
            a = -s * r
            if (a <= 0) {
                return (s * s < radius * radius)
            } else if (a >= r * r) {
                return (e * e < radius * radius)
            } else {
                r.set(s + r * (a / (r * r)))
                return (r * r < radius * radius)
            }
        }

        /*
         ============
         idSphere::RayIntersection

         Returns true if the ray intersects the sphere.
         The ray can intersect the sphere in both directions from the start point.
         If start is inside the sphere then scale1 < 0 and scale2 > 0.
         ============
         */
        // intersection points are (start + dir * scale1) and (start + dir * scale2)
        fun RayIntersection(start: idVec3, dir: idVec3, scale1: CFloat, scale2: CFloat): Boolean {
            var a: Float
            val b: Float
            val c: Float
            val d: Float
            val sqrtd: Float
            val p = idVec3()

            p.set(start - origin)
            a = dir * dir
            b = dir * p
            c = p * p - radius * radius
            d = b * b - c * a

            if (d < 0.0f) {
                return false
            }

            sqrtd = idMath.Sqrt(d)
            a = 1.0f / a

            scale1._val = (-b + sqrtd) * a
            scale2._val = (-b - sqrtd) * a

            return true
        }

        /*
         ============
         idSphere::FromPoints

         Tight sphere for a point set.
         ============
         */
        // Tight sphere for a point set.
        fun FromPoints(points: Array<idVec3>, numPoints: Int) {
            var radiusSqr: Float
            var dist: Float
            val mins = idVec3()
            val maxs = idVec3()

            SIMDProcessor!!.MinMax(mins, maxs, points, numPoints)

            origin.set((mins + maxs) * 0.5f)

            radiusSqr = 0.0f
            for (i in 0 until numPoints) {
                dist = (points[i] - origin).LengthSqr()
                if (dist > radiusSqr) {
                    radiusSqr = dist
                }
            }
            radius = idMath.Sqrt(radiusSqr)
        }

        // Most tight sphere for a translation.
        fun FromPointTranslation(point: idVec3, translation: idVec3) {
            origin.set(point + translation * 0.5f)
            radius = idMath.Sqrt(0.5f * translation.LengthSqr())
        }

        fun FromSphereTranslation(sphere: idSphere, start: idVec3, translation: idVec3) {
            origin.set(start + sphere.origin + translation * 0.5f)
            radius = idMath.Sqrt(0.5f * translation.LengthSqr()) + sphere.radius
        }

        // Most tight sphere for a rotation.
        fun FromPointRotation(point: idVec3, rotation: idRotation) {
            val end = rotation * point
            origin.set((point + end) * 0.5f)
            radius = idMath.Sqrt(0.5f * (end - point).LengthSqr())
        }

        fun FromSphereRotation(sphere: idSphere, start: idVec3, rotation: idRotation) {
            val end = rotation * sphere.origin
            origin.set(start + (sphere.origin + end) * 0.5f)
            radius = idMath.Sqrt(0.5f * (end - sphere.origin).LengthSqr()) + sphere.radius
        }

        fun AxisProjection(dir: idVec3, min: CFloat, max: CFloat) {
            val d: Float
            d = dir * origin
            min._val = d - radius
            max._val = d + radius
        }
    }
}
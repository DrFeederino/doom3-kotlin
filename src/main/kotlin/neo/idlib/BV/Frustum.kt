package neo.idlib.BV

import neo.idlib.BV.Box.idBox
import neo.idlib.BV.Sphere.idSphere
import neo.idlib.Max
import neo.idlib.Min
import neo.idlib.containers.CFloat
import neo.idlib.containers.CInt
import neo.idlib.containers.List.idSwap
import neo.idlib.geometry.Winding.idWinding
import neo.idlib.math.*
import neo.idlib.math.Matrix.idMat3
import kotlin.math.abs

object Frustum {
    /*
     bit 0 = min x
     bit 1 = max x
     bit 2 = min y
     bit 3 = max y
     bit 4 = min z
     bit 5 = max z
     */
    private val boxVertPlanes: IntArray = intArrayOf(
        1 shl 0 or (1 shl 2) or (1 shl 4),
        1 shl 1 or (1 shl 2) or (1 shl 4),
        1 shl 1 or (1 shl 3) or (1 shl 4),
        1 shl 0 or (1 shl 3) or (1 shl 4),
        1 shl 0 or (1 shl 2) or (1 shl 5),
        1 shl 1 or (1 shl 2) or (1 shl 5),
        1 shl 1 or (1 shl 3) or (1 shl 5),
        1 shl 0 or (1 shl 3) or (1 shl 5)
    )

    /*
     ============
     BoxToPoints
     ============
     */
    private fun BoxToPoints(center: idVec3, extents: idVec3, axis: idMat3, points: Array<idVec3>) {
        val ax = idMat3()
        val temp: Array<idVec3> = idVec3.generateArray(4)

        ax[0] = axis[0] * extents[0]
        ax[1] = axis[1] * extents[1]
        ax[2] = axis[2] * extents[2]
        temp[0] = center - ax[0]
        temp[1] = center + ax[0]
        temp[2] = ax[1] - ax[2]
        temp[3] = ax[1] + ax[2]
        points[0] = temp[0] - temp[3]
        points[1] = temp[1] - temp[3]
        points[2] = temp[1] + temp[2]
        points[3] = temp[0] + temp[2]
        points[4] = temp[0] - temp[2]
        points[5] = temp[1] - temp[2]
        points[6] = temp[1] + temp[3]
        points[7] = temp[0] + temp[3]
    }

    /*
     ===============================================================================

     Orthogonal Frustum

     ===============================================================================
     */
    class idFrustum {
        private val origin // frustum origin
                : idVec3
        private var axis // frustum orientation
                : idMat3
        private var dFar // distance of far plane, dFar > dNear
                : Float
        private var dLeft // half the width at the far plane
                = 0.0f
        private var dNear // distance of near plane, dNear >= 0.0f
                : Float
        private var dUp // half the height at the far plane
                = 0.0f
        private var invFar // 1.0f / dFar
                = 0.0f

        constructor() {
            origin = idVec3()
            axis = idMat3()
            dFar = 0.0f
            dNear = dFar
        }

        constructor(f: idFrustum) {
            origin = idVec3(f.origin)
            axis = idMat3(f.axis)
            dNear = f.dNear
            dFar = f.dFar
            dLeft = f.dLeft
            dUp = f.dUp
            invFar = f.invFar
        }

        fun SetOrigin(origin: idVec3) {
            this.origin.set(origin)
        }

        fun SetAxis(axis: idMat3) {
            this.axis = idMat3(axis)
        }

        fun SetSize(dNear: Float, dFar: Float, dLeft: Float, dUp: Float) {
            assert(dNear >= 0.0f && dFar > dNear && dLeft > 0.0f && dUp > 0.0f)
            this.dNear = dNear
            this.dFar = dFar
            this.dLeft = dLeft
            this.dUp = dUp
            invFar = 1.0f / dFar
        }

        fun SetPyramid(dNear: Float, dFar: Float) {
            assert(dNear >= 0.0f && dFar > dNear)
            this.dNear = dNear
            this.dFar = dFar
            dLeft = dFar
            dUp = dFar
            invFar = 1.0f / dFar
        }

        fun MoveNearDistance(dNear: Float) {
            assert(dNear >= 0.0f)
            this.dNear = dNear
        }

        fun MoveFarDistance(dFar: Float) {
            assert(dFar > dNear)
            val scale = dFar / this.dFar
            this.dFar = dFar
            dLeft *= scale
            dUp *= scale
            invFar = 1.0f / dFar
        }

        // returns frustum origin
        fun GetOrigin(): idVec3 {
            return origin
        }

        // returns frustum orientation
        fun GetAxis(): idMat3 {
            return axis
        }

        fun GetCenter(): idVec3 {                        // returns center of frustum
            return (origin + axis[0] * ((dFar - dNear) * 0.5f))
        }

        fun IsValid(): Boolean {                            // returns true if the frustum is valid
            return dFar > dNear
        }

        fun GetNearDistance(): Float {                    // returns distance to near plane
            return dNear
        }

        fun GetFarDistance(): Float {                    // returns distance to far plane
            return dFar
        }

        //
        fun GetLeft(): Float {                            // returns left vector length
            return dLeft
        }

        fun GetUp(): Float {                            // returns up vector length
            return dUp
        }

        fun Expand(d: Float): idFrustum {                    // returns frustum expanded in all directions with the given value
            val f = idFrustum(this)
            f.origin.minusAssign(f.axis[0] * d)
            f.dFar += 2.0f * d
            f.dLeft = f.dFar * dLeft * invFar
            f.dUp = f.dFar * dUp * invFar
            f.invFar = 1.0f / dFar
            return f
        }

        fun ExpandSelf(d: Float): idFrustum {                    // expands frustum in all directions with the given value
            origin.minusAssign(axis[0] * d)
            dFar += 2.0f * d
            dLeft = dFar * dLeft * invFar
            dUp = dFar * dUp * invFar
            invFar = 1.0f / dFar
            return this
        }

        fun Translate(translation: idVec3): idFrustum {    // returns translated frustum
            val f = idFrustum(this)
            f.origin.plusAssign(translation)
            return f
        }

        fun TranslateSelf(translation: idVec3): idFrustum {        // translates frustum
            origin.plusAssign(translation)
            return this
        }

        //
        fun Rotate(rotation: idMat3): idFrustum {            // returns rotated frustum
            val f = idFrustum(this)
            f.axis.timesAssign(rotation)
            return f
        }

        fun RotateSelf(rotation: idMat3): idFrustum {            // rotates frustum
            axis.timesAssign(rotation)
            return this
        }

        fun PlaneDistance(plane: idPlane): Float {
            val min = CFloat()
            val max = CFloat()
            AxisProjection(plane.Normal(), min, max)
            if (min._val + plane[3] > 0.0f) {
                return min._val + plane[3]
            }
            return if (max._val + plane[3] < 0.0f) {
                max._val + plane[3]
            } else 0.0f
        }

        //

        fun PlaneSide(plane: idPlane, epsilon: Float = ON_EPSILON): Int {
            val min = CFloat()
            val max = CFloat()
            AxisProjection(plane.Normal(), min, max)
            if (min._val + plane[3] > epsilon) {
                return PLANESIDE_FRONT
            }
            return if (max._val + plane[3] < epsilon) {
                PLANESIDE_BACK
            } else PLANESIDE_CROSS
        }

        // fast culling but might not cull everything outside the frustum
        fun CullPoint(point: idVec3): Boolean {
            val p = idVec3()
            val scale: Float

            // transform point to frustum space
            p.set((point - origin) * axis.Transpose())
            // test whether or not the point is within the frustum
            if (p.x < dNear || p.x > dFar) {
                return true
            }
            scale = p.x * invFar
            if (abs(p.y) > dLeft * scale) {
                return true
            }
            if (abs(p.z) > dUp * scale) {
                return true
            }
            return false
        }

        /*
         ============
         idFrustum::CullBounds

         Tests if any of the planes of the frustum can be used as a separating plane.

         24 muls best case
         37 muls worst case
         ============
         */
        fun CullBounds(bounds: idBounds): Boolean {
            val localOrigin = idVec3()
            val center = idVec3()
            val extents = idVec3()
            val localAxis: idMat3

            center.set((bounds[0] + bounds[1]) * 0.5f)
            extents.set(bounds[1] - center)

            // transform the bounds into the space of this frustum
            localOrigin.set((center - origin) * axis.Transpose())
            localAxis = axis.Transpose()

            return CullLocalBox(localOrigin, extents, localAxis)
        }

        /*
         ============
         idFrustum::CullBox

         Tests if any of the planes of the frustum can be used as a separating plane.

         39 muls best case
         61 muls worst case
         ============
         */
        fun CullBox(box: idBox): Boolean {
            val localOrigin = idVec3()
            val localAxis: idMat3

            // transform the box into the space of this frustum
            localOrigin.set((box.GetCenter() - origin) * axis.Transpose())
            localAxis = box.GetAxis() * axis.Transpose()

            return CullLocalBox(localOrigin, box.GetExtents(), localAxis)
        }

        /*
         ============
         idFrustum::CullSphere

         Tests if any of the planes of the frustum can be used as a separating plane.

         9 muls best case
         21 muls worst case
         ============
         */
        fun CullSphere(sphere: idSphere): Boolean {
            var d: Float
            val r: Float
            val rs: Float
            val sFar: Float
            val center: idVec3

            center = (sphere.GetOrigin() - origin) * axis.Transpose()
            r = sphere.GetRadius()

            // test near plane
            if (dNear - center.x > r) {
                return true
            }

            // test far plane
            if (center.x - dFar > r) {
                return true
            }

            rs = r * r
            sFar = dFar * dFar

            // test left/right planes
            d = dFar * abs(center.y) - dLeft * center.x
            if ((d * d) > rs * (sFar + dLeft * dLeft)) {
                return true
            }

            // test up/down planes
            d = dFar * abs(center.z) - dUp * center.x
            if ((d * d) > rs * (sFar + dUp * dUp)) {
                return true
            }

            return false
        }

        //
        /*
         ============
         idFrustum::CullFrustum

         Tests if any of the planes of this frustum can be used as a separating plane.

         58 muls best case
         88 muls worst case
         ============
         */
        fun CullFrustum(frustum: idFrustum): Boolean {
            val localFrustum: idFrustum
            val indexPoints: Array<idVec3> = idVec3.generateArray(8)
            val cornerVecs: Array<idVec3> = idVec3.generateArray(4)

            // transform the given frustum into the space of this frustum
            localFrustum = idFrustum(frustum)
            localFrustum.origin.set((frustum.origin - origin) * axis.Transpose())
            localFrustum.axis.set(frustum.axis * axis.Transpose())

            localFrustum.ToIndexPointsAndCornerVecs(indexPoints, cornerVecs)

            return CullLocalFrustum(localFrustum, indexPoints, cornerVecs)
        }

        fun CullWinding(winding: idWinding): Boolean {
            val pointCull: IntArray
            val localPoints: Array<idVec3> = idVec3.generateArray(winding.GetNumPoints())
            val transpose: idMat3

            pointCull = IntArray(winding.GetNumPoints())
            transpose = axis.Transpose()

            transpose.set(axis.Transpose())
            for (i in 0 until winding.GetNumPoints()) {
                localPoints[i] = (winding[i].ToVec3() - origin) * transpose
            }

            return CullLocalWinding(localPoints, winding.GetNumPoints(), pointCull)
        }

        // exact intersection tests
        fun ContainsPoint(point: idVec3): Boolean {
            return !CullPoint(point)
        }

        fun IntersectsBounds(bounds: idBounds): Boolean {
            val localOrigin: idVec3
            val center: idVec3
            val extents: idVec3
            val localAxis: idMat3

            center = (bounds[0] + bounds[1]) * 0.5f
            extents = bounds[1] - center

            localOrigin = (center - origin) * axis.Transpose()
            localAxis = axis.Transpose()

            if (CullLocalBox(localOrigin, extents, localAxis)) {
                return false
            }

            val indexPoints: Array<idVec3> = idVec3.generateArray(8)
            val cornerVecs: Array<idVec3> = idVec3.generateArray(4)

            ToIndexPointsAndCornerVecs(indexPoints, cornerVecs)

            if (BoundsCullLocalFrustum(bounds, this, indexPoints, cornerVecs)) {
                return false
            }

            idSwap(indexPoints[2], indexPoints[3])
            idSwap(indexPoints[6], indexPoints[7])

            if (LocalFrustumIntersectsBounds(indexPoints, bounds)) {
                return true
            }

            BoxToPoints(localOrigin, extents, localAxis, indexPoints)

            return LocalFrustumIntersectsFrustum(indexPoints, true)
        }

        fun IntersectsBox(box: idBox): Boolean {
            val localOrigin = idVec3()
            val localAxis: idMat3

            localOrigin.set((box.GetCenter() - origin) * axis.Transpose())
            localAxis = box.GetAxis() * axis.Transpose()

            if (CullLocalBox(localOrigin, box.GetExtents(), localAxis)) {
                return false
            }

            val indexPoints: Array<idVec3> = idVec3.generateArray(8)
            val cornerVecs: Array<idVec3> = idVec3.generateArray(4)
            val localFrustum = idFrustum(this)

            localFrustum.origin.set((origin - box.GetCenter()) * box.GetAxis().Transpose())
            localFrustum.axis.set(axis * box.GetAxis().Transpose())
            localFrustum.ToIndexPointsAndCornerVecs(indexPoints, cornerVecs)

            if (BoundsCullLocalFrustum(
                    idBounds(-box.GetExtents(), box.GetExtents()),
                    localFrustum,
                    indexPoints,
                    cornerVecs
                )
            ) {
                return false
            }

            idSwap(indexPoints[2], indexPoints[3])
            idSwap(indexPoints[6], indexPoints[7])

            if (LocalFrustumIntersectsBounds(indexPoints, idBounds(-box.GetExtents(), box.GetExtents()))) {
                return true
            }

            BoxToPoints(localOrigin, box.GetExtents(), localAxis, indexPoints)

            return LocalFrustumIntersectsFrustum(indexPoints, true)
        }

        private fun VORONOI_INDEX(x: Int, y: Int, z: Int): Int {
            return x + y * 3 + z * 9
        }

        fun IntersectsSphere(sphere: idSphere): Boolean {
            val index: Int
            var x: Int
            var y: Int
            var z: Int
            var scale: Float
            val r: Float
            val d: Float
            val p = idVec3()
            val dir = idVec3()
            val points: Array<idVec3> = idVec3.generateArray(8)

            if (CullSphere(sphere)) {
                return false
            }
            z = 0
            y = z
            x = y
            dir.Zero()
            p.set((sphere.GetOrigin() - origin) * axis.Transpose())

            if (p.x <= dNear) {
                scale = dNear * invFar
                dir.y = abs(p.y) - dLeft * scale
                dir.z = abs(p.z) - dUp * scale
            } else if (p.x >= dFar) {
                dir.y = abs(p.y) - dLeft
                dir.z = abs(p.z) - dUp
            } else {
                scale = p.x * invFar
                dir.y = abs(p.y) - dLeft * scale
                dir.z = abs(p.z) - dUp * scale
            }
            if (dir.y > 0.0f) {
                y = 1 + FLOATSIGNBITNOTSET(p.y)
            }
            if (dir.z > 0.0f) {
                z = 1 + FLOATSIGNBITNOTSET(p.z)
            }
            if (p.x < dNear) {
                scale = dLeft * dNear * invFar
                if (p.x < dNear + (scale - p.y) * scale * invFar) {
                    scale = dUp * dNear * invFar
                    if (p.x < dNear + (scale - p.z) * scale * invFar) {
                        x = 1
                    }
                }
            } else {
                if (p.x > dFar) {
                    x = 2
                } else if (p.x > dFar + (dLeft - p.y) * dLeft * invFar) {
                    x = 2
                } else if (p.x > dFar + (dUp - p.z) * dUp * invFar) {
                    x = 2
                }
            }
            r = sphere.GetRadius()
            index = VORONOI_INDEX(x, y, z)
            when (index) {
                VORONOI_INDEX_0_0_0 -> return true
                VORONOI_INDEX_1_0_0 -> return dNear - p.x < r
                VORONOI_INDEX_2_0_0 -> return p.x - dFar < r
                VORONOI_INDEX_0_1_0 -> {
                    d = dFar * p.y - dLeft * p.x
                    return d * d < r * r * (dFar * dFar + dLeft * dLeft)
                }

                VORONOI_INDEX_0_2_0 -> {
                    d = -dFar * p.z - dLeft * p.x
                    return d * d < r * r * (dFar * dFar + dLeft * dLeft)
                }

                VORONOI_INDEX_0_0_1 -> {
                    d = dFar * p.z - dUp * p.x
                    return d * d < r * r * (dFar * dFar + dUp * dUp)
                }

                VORONOI_INDEX_0_0_2 -> {
                    d = -dFar * p.z - dUp * p.x
                    return d * d < r * r * (dFar * dFar + dUp * dUp)
                }

                else -> {
                    ToIndexPoints(points)
                    when (index) {
                        VORONOI_INDEX_1_1_1 -> return sphere.ContainsPoint(points[0])
                        VORONOI_INDEX_2_1_1 -> return sphere.ContainsPoint(points[4])
                        VORONOI_INDEX_1_2_1 -> return sphere.ContainsPoint(points[1])
                        VORONOI_INDEX_2_2_1 -> return sphere.ContainsPoint(points[5])
                        VORONOI_INDEX_1_1_2 -> return sphere.ContainsPoint(points[2])
                        VORONOI_INDEX_2_1_2 -> return sphere.ContainsPoint(points[6])
                        VORONOI_INDEX_1_2_2 -> return sphere.ContainsPoint(points[3])
                        VORONOI_INDEX_2_2_2 -> return sphere.ContainsPoint(points[7])
                        VORONOI_INDEX_1_1_0 -> return sphere.LineIntersection(points[0], points[2])
                        VORONOI_INDEX_2_1_0 -> return sphere.LineIntersection(points[4], points[6])
                        VORONOI_INDEX_1_2_0 -> return sphere.LineIntersection(points[1], points[3])
                        VORONOI_INDEX_2_2_0 -> return sphere.LineIntersection(points[5], points[7])
                        VORONOI_INDEX_1_0_1 -> return sphere.LineIntersection(points[0], points[1])
                        VORONOI_INDEX_2_0_1 -> return sphere.LineIntersection(points[4], points[5])
                        VORONOI_INDEX_0_1_1 -> return sphere.LineIntersection(points[0], points[4])
                        VORONOI_INDEX_0_2_1 -> return sphere.LineIntersection(points[1], points[5])
                        VORONOI_INDEX_1_0_2 -> return sphere.LineIntersection(points[2], points[3])
                        VORONOI_INDEX_2_0_2 -> return sphere.LineIntersection(points[6], points[7])
                        VORONOI_INDEX_0_1_2 -> return sphere.LineIntersection(points[2], points[6])
                        VORONOI_INDEX_0_2_2 -> return sphere.LineIntersection(points[3], points[7])
                    }
                }
            }
            return false
        }

        fun IntersectsFrustum(frustum: idFrustum): Boolean {
            val indexPoints2: Array<idVec3> = idVec3.generateArray(8)
            val cornerVecs2: Array<idVec3> = idVec3.generateArray(4)
            val transpose = idMat3()

            val localFrustum2 = idFrustum(frustum)
            transpose.set(axis)
            transpose.TransposeSelf()
            localFrustum2.origin.set((frustum.origin - origin) * transpose)
            localFrustum2.axis.setMul(frustum.axis, transpose)
            localFrustum2.ToIndexPointsAndCornerVecs(indexPoints2, cornerVecs2)

            if (CullLocalFrustum(localFrustum2, indexPoints2, cornerVecs2)) {
                return false
            }

            val indexPoints1: Array<idVec3> = idVec3.generateArray(8)
            val cornerVecs1: Array<idVec3> = idVec3.generateArray(4)
            val localFrustum1 = idFrustum(this)

            transpose.set(frustum.axis)
            transpose.TransposeSelf()
            localFrustum1.origin.set((origin - frustum.origin) * transpose)
            localFrustum1.axis.setMul(axis, transpose)
            localFrustum1.ToIndexPointsAndCornerVecs(indexPoints1, cornerVecs1)

            if (frustum.CullLocalFrustum(localFrustum1, indexPoints1, cornerVecs1)) {
                return false
            }

            idSwap(indexPoints2[2], indexPoints2[3])
            idSwap(indexPoints2[6], indexPoints2[7])

            if (LocalFrustumIntersectsFrustum(indexPoints2, (localFrustum2.dNear > 0.0f))) {
                return true
            }

            idSwap(indexPoints1[2], indexPoints1[3])
            idSwap(indexPoints1[6], indexPoints1[7])

            return frustum.LocalFrustumIntersectsFrustum(indexPoints1, (localFrustum1.dNear > 0.0f))
        }

        fun IntersectsWinding(winding: idWinding): Boolean {
            var i: Int
            var j: Int
            val pointCull: IntArray
            val min = CFloat()
            val max = CFloat()
            val localPoints: Array<idVec3> = idVec3.generateArray(winding.GetNumPoints())
            val indexPoints: Array<idVec3> = idVec3.generateArray(8)
            val cornerVecs: Array<idVec3> = idVec3.generateArray(4)
            val transpose: idMat3
            val plane = idPlane()

            pointCull = IntArray(winding.GetNumPoints())
            transpose = axis.Transpose()
            i = 0
            while (i < winding.GetNumPoints()) {
                localPoints[i].set((winding[i].ToVec3() - origin) * transpose)
                i++
            }

            // if the winding is culled
            if (CullLocalWinding(localPoints, winding.GetNumPoints(), pointCull)) {
                return false
            }
            winding.GetPlane(plane)
            ToIndexPointsAndCornerVecs(indexPoints, cornerVecs)
            AxisProjection(indexPoints, cornerVecs, plane.Normal(), min, max)

            // if the frustum does not cross the winding plane
            if (min._val + plane[3] > 0.0f || max._val + plane[3] < 0.0f) {
                return false
            }

            // test if any of the winding edges goes through the frustum
            i = 0
            while (i < winding.GetNumPoints()) {
                j = (i + 1) % winding.GetNumPoints()
                if (0 == pointCull[i] and pointCull[j]) {
                    if (LocalLineIntersection(localPoints[i], localPoints[j])) {
                        return true
                    }
                }
                i++
            }
            idSwap(indexPoints, indexPoints, 2, 3)
            idSwap(indexPoints, indexPoints, 6, 7)

            // test if any edges of the frustum intersect the winding
            i = 0
            while (i < 4) {
                if (winding.LineIntersection(plane, indexPoints[i], indexPoints[4 + i])) {
                    return true
                }
                i++
            }
            if (dNear > 0.0f) {
                i = 0
                while (i < 4) {
                    if (winding.LineIntersection(plane, indexPoints[i], indexPoints[i + 1 and 3])) {
                        return true
                    }
                    i++
                }
            }
            i = 0
            while (i < 4) {
                if (winding.LineIntersection(plane, indexPoints[4 + i], indexPoints[4 + (i + 1 and 3)])) {
                    return true
                }
                i++
            }
            return false
        }

        /*
         ============
         idFrustum::LineIntersection

         Returns true if the line intersects the box between the start and end point.
         ============
         */
        fun LineIntersection(start: idVec3, end: idVec3): Boolean {
            return LocalLineIntersection((start - origin) * axis.Transpose(), (end - origin) * axis.Transpose())
        }

        /*
         ============
         idFrustum::RayIntersection

         Returns true if the ray intersects the bounds.
         The ray can intersect the bounds in both directions from the start point.
         If start is inside the frustum then scale1 < 0 and scale2 > 0.
         ============
         */
        fun RayIntersection(start: idVec3, dir: idVec3, scale1: CFloat, scale2: CFloat): Boolean {
            if (LocalRayIntersection((start - origin) * axis.Transpose(), dir * axis.Transpose(), scale1, scale2)) {
                return true
            }
            if (scale1._val <= scale2._val) {
                return true
            }
            return false
        }

        /*
         ============
         idFrustum::FromProjection

         Creates a frustum which contains the projection of the bounds.
         ============
         */
        // returns true if the projection origin is far enough away from the bounding volume to create a valid frustum
        fun FromProjection(bounds: idBounds, projectionOrigin: idVec3, dFar: Float): Boolean {
            return FromProjection(
                idBox(bounds, vec3_origin, idMat3.getMat3_identity()),
                projectionOrigin,
                dFar
            )
        }

        /*
         ============
         idFrustum::FromProjection

         Creates a frustum which contains the projection of the box.
         ============
         */
        fun FromProjection(box: idBox, projectionOrigin: idVec3, dFar: Float): Boolean {
            var i: Int
            var bestAxis: Int
            var value: Float
            var bestValue: Float
            val dir = idVec3()
            assert(dFar > 0.0f)
            invFar = 0.0f
            this.dFar = invFar
            dNear = this.dFar
            dir.set(box.GetCenter() - projectionOrigin)
            if (dir.Normalize() == 0.0f) {
                return false
            }
            bestAxis = 0
            bestValue = abs(box.GetAxis()[0] * dir)
            i = 1
            while (i < 3) {
                value = abs(box.GetAxis()[i] * dir)
                if (value * box.GetExtents()[bestAxis] * box.GetExtents()[bestAxis] < bestValue * box.GetExtents()[i] * box.GetExtents()[i]
                ) {
                    bestValue = value
                    bestAxis = i
                }
                i++
            }

//#if 1
            var j: Int
            var minX: Int
            var minY: Int
            var maxY: Int
            var minZ: Int
            var maxZ: Int
            val points: Array<idVec3> = idVec3.generateArray(8)
            maxZ = 0
            minZ = maxZ
            maxY = minZ
            minY = maxY
            minX = minY
            j = 0
            while (j < 2) {
                axis[0] = dir
                axis[1] = box.GetAxis()[bestAxis] - axis[0] * (box.GetAxis()[bestAxis] * axis[0])
                axis[1].Normalize()
                axis[2].Cross(axis[0], axis[1])
                BoxToPoints(
                    (box.GetCenter() - projectionOrigin) * axis.Transpose(),
                    box.GetExtents(),
                    box.GetAxis() * axis.Transpose(),
                    points
                )
                if (points[0].x <= 1.0f) {
                    return false
                }
                maxZ = 0
                minZ = maxZ
                maxY = minZ
                minY = maxY
                minX = minY
                i = 1
                while (i < 8) {
                    if (points[i].x <= 1.0f) {
                        return false
                    }
                    if (points[i].x < points[minX].x) {
                        minX = i
                    }
                    if (points[minY].x * points[i].y < points[i].x * points[minY].y) {
                        minY = i
                    } else if (points[maxY].x * points[i].y > points[i].x * points[maxY].y) {
                        maxY = i
                    }
                    if (points[minZ].x * points[i].z < points[i].x * points[minZ].z) {
                        minZ = i
                    } else if (points[maxZ].x * points[i].z > points[i].x * points[maxZ].z) {
                        maxZ = i
                    }
                    i++
                }
                if (j == 0) {
                    dir.plusAssign(
                        axis[1] * idMath.Tan16(
                            0.5f * (idMath.ATan16(points[minY].y, points[minY].x) + idMath.ATan16(
                                points[maxY].y,
                                points[maxY].x
                            ))
                        )
                    )
                    dir.plusAssign(
                        axis[2] * idMath.Tan16(
                            0.5f * (idMath.ATan16(points[minZ].z, points[minZ].x) + idMath.ATan16(
                                points[maxZ].z,
                                points[maxZ].x
                            ))
                        )
                    )
                    dir.Normalize()
                }
                j++
            }
            origin.set(projectionOrigin)
            dNear = points[minX].x
            this.dFar = dFar
            dLeft = Max(
                abs(points[minY].y / points[minY].x),
                abs(points[maxY].y / points[maxY].x)
            ) * dFar
            dUp = Max(
                abs(points[minZ].z / points[minZ].x),
                abs(points[maxZ].z / points[maxZ].x)
            ) * dFar
            invFar = 1.0f / dFar
            return true
        }

        //
        /*
         ============
         idFrustum::FromProjection

         Creates a frustum which contains the projection of the sphere.
         ============
         */
        fun FromProjection(sphere: idSphere, projectionOrigin: idVec3, dFar: Float): Boolean {
            val dir = idVec3()
            val d: Float
            val r: Float
            val s: Float
            val x: Float
            val y: Float

            assert(dFar > 0.0f)

            dir.set(sphere.GetOrigin() - projectionOrigin)
            d = dir.Normalize()
            r = sphere.GetRadius()

            if (d <= r + 1.0f) {
                invFar = 0.0f
                this.dFar = invFar
                dNear = this.dFar
                return false
            }
            origin.set(projectionOrigin)
            axis.set(dir.ToMat3())
            s = idMath.Sqrt(d * d - r * r)
            x = r / d * s
            y = idMath.Sqrt(s * s - x * x)
            dNear = d - r
            this.dFar = dFar
            dLeft = x / y * dFar
            dUp = dLeft
            invFar = 1.0f / dFar
            return true
        }

        /*
         ============
         idFrustum::ConstrainToBounds

         Returns false if no part of the bounds extends beyond the near plane.
         ============
         */
        // moves the far plane so it extends just beyond the bounding volume
        fun ConstrainToBounds(bounds: idBounds): Boolean {
            val min = CFloat()
            val max = CFloat()
            val newdFar: Float
            bounds.AxisProjection(axis[0], min, max)
            newdFar = max._val - axis[0] * origin
            if (newdFar <= dNear) {
                MoveFarDistance(dNear + 1.0f)
                return false
            }
            MoveFarDistance(newdFar)
            return true
        }

        /*
         ============
         idFrustum::ConstrainToBox

         Returns false if no part of the box extends beyond the near plane.
         ============
         */
        fun ConstrainToBox(box: idBox): Boolean {
            val min = CFloat()
            val max = CFloat()
            val newdFar: Float
            box.AxisProjection(axis[0], min, max)
            newdFar = max._val - axis[0] * origin
            if (newdFar <= dNear) {
                MoveFarDistance(dNear + 1.0f)
                return false
            }
            MoveFarDistance(newdFar)
            return true
        }

        /*
         ============
         idFrustum::ConstrainToSphere

         Returns false if no part of the sphere extends beyond the near plane.
         ============
         */
        fun ConstrainToSphere(sphere: idSphere): Boolean {
            val min = CFloat()
            val max = CFloat()
            val newdFar: Float
            sphere.AxisProjection(axis[0], min, max)
            newdFar = max._val - axis[0] * origin
            if (newdFar <= dNear) {
                MoveFarDistance(dNear + 1.0f)
                return false
            }
            MoveFarDistance(newdFar)
            return true
        }

        //
        /*
         ============
         idFrustum::ConstrainToFrustum

         Returns false if no part of the frustum extends beyond the near plane.
         ============
         */
        fun ConstrainToFrustum(frustum: idFrustum): Boolean {
            val min = CFloat()
            val max = CFloat()
            val newdFar: Float
            frustum.AxisProjection(axis[0], min, max)
            newdFar = max._val - axis[0] * origin
            if (newdFar <= dNear) {
                MoveFarDistance(dNear + 1.0f)
                return false
            }
            MoveFarDistance(newdFar)
            return true
        }

        /*
         ============
         idFrustum::ToPlanes

         planes point outwards
         ============
         */
        fun ToPlanes(planes: Array<idPlane>) {            // planes point outwards
            val scaled: Array<idVec3> = idVec3.generateArray(2)
            val points: Array<idVec3> = idVec3.generateArray(4)
            planes[0].Normal().set(-axis[0])
            planes[0].SetDist(-dNear)
            planes[1].Normal().set(axis[0])
            planes[1].SetDist(dFar)

            scaled[0] = axis[1] * dLeft
            scaled[1] = axis[2] * dUp
            points[0] = scaled[0] + scaled[1]
            points[1] = -scaled[0] + scaled[1]
            points[2] = -scaled[0] - scaled[1]
            points[3] = scaled[0] - scaled[1]

            for (i in 0 until 4) {
                planes[i + 2].Normal().set(points[i].Cross(points[(i + 1) and 3] - points[i]))
                planes[i + 2].Normalize()
                planes[i + 2].FitThroughPoint(points[i])
            }
        }

        //
        fun ToPoints(points: Array<idVec3>) {                // 8 corners of the frustum
            val scaled = idMat3()

            scaled[0] = origin + axis[0] * dNear
            scaled[1] = axis[1] * (dLeft * dNear * invFar)
            scaled[2] = axis[2] * (dUp * dNear * invFar)

            points[0] = scaled[0] + scaled[1]
            points[1] = scaled[0] - scaled[1]
            points[2] = points[1] - scaled[2]
            points[3] = points[0] - scaled[2]
            points[0] += scaled[2]
            points[1] += scaled[2]

            scaled[0] = origin + axis[0] * dFar
            scaled[1] = axis[1] * dLeft
            scaled[2] = axis[2] * dUp

            points[4] = scaled[0] + scaled[1]
            points[5] = scaled[0] - scaled[1]
            points[6] = points[5] - scaled[2]
            points[7] = points[4] - scaled[2]
            points[4] += scaled[2]
            points[5] += scaled[2]
        }

        /*
         ============
         idFrustum::AxisProjection

         40 muls
         ============
         */
        // calculates the projection of this frustum onto the given axis
        fun AxisProjection(dir: idVec3, min: CFloat, max: CFloat) {
            val indexPoints: Array<idVec3> = idVec3.generateArray(8)
            val cornerVecs: Array<idVec3> = idVec3.generateArray(4)
            ToIndexPointsAndCornerVecs(indexPoints, cornerVecs)
            AxisProjection(indexPoints, cornerVecs, dir, min, max)
        }

        //
        /*
         ============
         idFrustum::AxisProjection

         76 muls
         ============
         */
        fun AxisProjection(ax: idMat3, bounds: idBounds) {
            val indexPoints: Array<idVec3> = idVec3.generateArray(8)
            val cornerVecs: Array<idVec3> = idVec3.generateArray(4)
            // needed to bypass &float stuff for AxisProjection
            // Wrap it in CFloats and write to them
            val b00 = CFloat(bounds[0][0])
            val b01 = CFloat(bounds[0][1])
            val b02 = CFloat(bounds[0][2])
            val b10 = CFloat(bounds[1][0])
            val b11 = CFloat(bounds[1][1])
            val b12 = CFloat(bounds[1][2])
            ToIndexPointsAndCornerVecs(indexPoints, cornerVecs)
            AxisProjection(indexPoints, cornerVecs, ax[0], b00, b10)
            AxisProjection(indexPoints, cornerVecs, ax[1], b01, b11)
            AxisProjection(indexPoints, cornerVecs, ax[2], b02, b12)
            // Un-wrap and write to bounds
            bounds[0, 0] = b00._val
            bounds[0, 1] = b01._val
            bounds[0, 2] = b02._val
            bounds[1, 0] = b10._val
            bounds[1, 1] = b11._val
            bounds[1, 2] = b12._val
        }

        // calculates the bounds for the projection in this frustum
        fun ProjectionBounds(bounds: idBounds, projectionBounds: idBounds): Boolean {
            return ProjectionBounds(
                idBox(bounds, vec3_origin, idMat3.getMat3_identity()),
                projectionBounds
            )
        }

        fun ProjectionBounds(box: idBox, projectionBounds: idBounds): Boolean {
            var i: Int
            var p1: Int
            var p2: Int
            var culled: Int
            var outside: Int
            val pointCull = Array(8) { CInt() }
            val scale1 = CFloat()
            val scale2 = CFloat()
            val points: Array<idVec3> = idVec3.generateArray(8)
            val localOrigin = idVec3()
            val localAxis: idMat3
            val localScaled: idMat3
            val bounds = idBounds(-box.GetExtents(), box.GetExtents())

            // if the frustum origin is inside the bounds
            if (bounds.ContainsPoint((origin - box.GetCenter()) * box.GetAxis().Transpose())) {
                // bounds that cover the whole frustum
                val boxMin = CFloat()
                val boxMax = CFloat()
                val base: Float
                base = origin * axis[0]
                box.AxisProjection(axis[0], boxMin, boxMax)
                projectionBounds[0, 0] = boxMin._val - base
                projectionBounds[1, 0] = boxMax._val - base
                projectionBounds[0, 1] = -1.0f
                projectionBounds[0, 2] = -1.0f
                projectionBounds[1, 1] = 1.0f
                projectionBounds[1, 2] = 1.0f
                return true
            }
            projectionBounds.Clear()

            // transform the bounds into the space of this frustum
            localOrigin.set((box.GetCenter() - origin) * axis.Transpose())
            localAxis = box.GetAxis() * axis.Transpose()
            BoxToPoints(localOrigin, box.GetExtents(), localAxis, points)

            // test outer four edges of the bounds
            culled = -1
            outside = 0
            i = 0
            while (i < 4) {
                p1 = i
                p2 = 4 + i
                AddLocalLineToProjectionBoundsSetCull(
                    points[p1],
                    points[p2],
                    pointCull[p1],
                    pointCull[p2],
                    projectionBounds
                )
                culled = culled and (pointCull[p1]._val and pointCull[p2]._val)
                outside = outside or (pointCull[p1]._val or pointCull[p2]._val)
                i++
            }

            // if the bounds are completely outside this frustum
            if (culled != 0) {
                return false
            }

            // if the bounds are completely inside this frustum
            if (0 == outside) {
                return true
            }

            // test the remaining edges of the bounds
            i = 0
            while (i < 4) {
                p1 = i
                p2 = i + 1 and 3
                AddLocalLineToProjectionBoundsUseCull(
                    points[p1],
                    points[p2],
                    pointCull[p1]._val,
                    pointCull[p2]._val,
                    projectionBounds
                )
                i++
            }
            i = 0
            while (i < 4) {
                p1 = 4 + i
                p2 = 4 + (i + 1 and 3)
                AddLocalLineToProjectionBoundsUseCull(
                    points[p1],
                    points[p2],
                    pointCull[p1]._val,
                    pointCull[p2]._val,
                    projectionBounds
                )
                i++
            }

            // if the bounds extend beyond two or more boundaries of this frustum
            if (outside != 1 && outside != 2 && outside != 4 && outside != 8) {
                localOrigin.set((origin - box.GetCenter()) * box.GetAxis().Transpose())
                localScaled = axis * box.GetAxis().Transpose()
                localScaled[0].timesAssign(dFar)
                localScaled[1].timesAssign(dLeft)
                localScaled[2].timesAssign(dUp)

                // test the outer edges of this frustum for intersection with the bounds
                if (outside and 2 == 2 && outside and 8 == 8) {
                    BoundsRayIntersection(
                        bounds,
                        localOrigin,
                        localScaled[0] - localScaled[1] - localScaled[2],
                        scale1,
                        scale2
                    )
                    if (scale1._val <= scale2._val && scale1._val >= 0.0f) {
                        projectionBounds.AddPoint(idVec3(scale1._val * dFar, -1.0f, -1.0f))
                        projectionBounds.AddPoint(idVec3(scale2._val * dFar, -1.0f, -1.0f))
                    }
                }
                if (outside and 2 == 2 && outside and 4 == 4) {
                    BoundsRayIntersection(
                        bounds,
                        localOrigin,
                        localScaled[0] - localScaled[1] + localScaled[2],
                        scale1,
                        scale2
                    )
                    if (scale1._val <= scale2._val && scale1._val >= 0.0f) {
                        projectionBounds.AddPoint(idVec3(scale1._val * dFar, -1.0f, 1.0f))
                        projectionBounds.AddPoint(idVec3(scale2._val * dFar, -1.0f, 1.0f))
                    }
                }
                if (outside and 1 == 1 && outside and 8 == 8) {
                    BoundsRayIntersection(
                        bounds,
                        localOrigin,
                        localScaled[0] + localScaled[1] - localScaled[2],
                        scale1,
                        scale2
                    )
                    if (scale1._val <= scale2._val && scale1._val >= 0.0f) {
                        projectionBounds.AddPoint(idVec3(scale1._val * dFar, 1.0f, -1.0f))
                        projectionBounds.AddPoint(idVec3(scale2._val * dFar, 1.0f, -1.0f))
                    }
                }
                if (outside and 1 == 1 && outside and 2 == 2) {
                    BoundsRayIntersection(
                        bounds,
                        localOrigin,
                        localScaled[0] + localScaled[1] + localScaled[2],
                        scale1,
                        scale2
                    )
                    if (scale1._val <= scale2._val && scale1._val >= 0.0f) {
                        projectionBounds.AddPoint(idVec3(scale1._val * dFar, 1.0f, 1.0f))
                        projectionBounds.AddPoint(idVec3(scale2._val * dFar, 1.0f, 1.0f))
                    }
                }
            }
            return true
        }

        fun ProjectionBounds(sphere: idSphere, projectionBounds: idBounds): Boolean {
            var d: Float
            val r: Float
            val rs: Float
            val sFar: Float
            val center = idVec3()

            projectionBounds.Clear()

            center.set((sphere.GetOrigin() - origin) * axis.Transpose())
            r = sphere.GetRadius()
            rs = r * r
            sFar = dFar * dFar

            // test left/right planes
            d = dFar * abs(center.y) - dLeft * center.x
            if (d * d > rs * (sFar + dLeft * dLeft)) {
                return false
            }

            // test up/down planes
            d = dFar * abs(center.z) - dUp * center.x
            if (d * d > rs * (sFar + dUp * dUp)) {
                return false
            }

            // bounds that cover the whole frustum
            projectionBounds[0].x = 0.0f
            projectionBounds[1].x = dFar
            projectionBounds[0].z = -1.0f
            projectionBounds[0].y = projectionBounds[0].z
            projectionBounds[1].z = 1.0f
            projectionBounds[1].y = projectionBounds[1].z
            return true
        }

        fun ProjectionBounds(frustum: idFrustum, projectionBounds: idBounds): Boolean {
            var i: Int
            var p1: Int
            var p2: Int
            var culled: Int
            var outside: Int
            val pointCull = Array(8) { CInt() }
            val scale1 = CFloat()
            val scale2 = CFloat()
            val localFrustum: idFrustum
            val points: Array<idVec3> = idVec3.generateArray(8)
            val localOrigin = idVec3()
            val localScaled: idMat3

            // if the frustum origin is inside the other frustum
            if (frustum.ContainsPoint(origin)) {
                // bounds that cover the whole frustum
                val frustumMin = CFloat()
                val frustumMax = CFloat()
                val base: Float

                base = origin * axis[0]
                frustum.AxisProjection(axis[0], frustumMin, frustumMax)
                projectionBounds[0].x = frustumMin._val - base
                projectionBounds[1].x = frustumMax._val - base
                projectionBounds[0].z = -1.0f
                projectionBounds[0].y = projectionBounds[0].z
                projectionBounds[1].z = 1.0f
                projectionBounds[1].y = projectionBounds[1].z
                return true
            }
            projectionBounds.Clear()

            // transform the given frustum into the space of this frustum
            localFrustum = idFrustum(frustum)
            localFrustum.origin.set((frustum.origin - origin) * axis.Transpose())
            localFrustum.axis.set(frustum.axis * axis.Transpose())
            localFrustum.ToPoints(points)

            // test outer four edges of the other frustum
            culled = -1
            outside = 0
            i = 0
            while (i < 4) {
                p1 = i
                p2 = 4 + i
                AddLocalLineToProjectionBoundsSetCull(
                    points[p1],
                    points[p2],
                    pointCull[p1],
                    pointCull[p2],
                    projectionBounds
                )
                culled = culled and (pointCull[p1]._val and pointCull[p2]._val)
                outside = outside or (pointCull[p1]._val or pointCull[p2]._val)
                i++
            }

            // if the other frustum is completely outside this frustum
            if (culled != 0) {
                return false
            }

            // if the other frustum is completely inside this frustum
            if (0 == outside) {
                return true
            }

            // test the remaining edges of the other frustum
            if (localFrustum.dNear > 0.0f) {
                i = 0
                while (i < 4) {
                    p1 = i
                    p2 = i + 1 and 3
                    AddLocalLineToProjectionBoundsUseCull(
                        points[p1],
                        points[p2],
                        pointCull[p1]._val,
                        pointCull[p2]._val,
                        projectionBounds
                    )
                    i++
                }
            }
            i = 0
            while (i < 4) {
                p1 = 4 + i
                p2 = 4 + (i + 1 and 3)
                AddLocalLineToProjectionBoundsUseCull(
                    points[p1],
                    points[p2],
                    pointCull[p1]._val,
                    pointCull[p2]._val,
                    projectionBounds
                )
                i++
            }

            // if the other frustum extends beyond two or more boundaries of this frustum
            if (outside != 1 && outside != 2 && outside != 4 && outside != 8) {
                localOrigin.set((origin - frustum.origin) * frustum.axis.Transpose())
                localScaled = axis * frustum.axis.Transpose()
                localScaled[0].timesAssign(dFar)
                localScaled[1].timesAssign(dLeft)
                localScaled[2].timesAssign(dUp)

                // test the outer edges of this frustum for intersection with the other frustum
                if (outside and 2 == 2 && outside and 8 == 8) {
                    frustum.LocalRayIntersection(
                        localOrigin,
                        localScaled[0] - localScaled[1] - localScaled[2],
                        scale1,
                        scale2
                    )
                    if (scale1._val <= scale2._val && scale1._val >= 0.0f) {
                        projectionBounds.AddPoint(idVec3(scale1._val * dFar, -1.0f, -1.0f))
                        projectionBounds.AddPoint(idVec3(scale2._val * dFar, -1.0f, -1.0f))
                    }
                }
                if (outside and 2 == 2 && outside and 4 == 4) {
                    frustum.LocalRayIntersection(
                        localOrigin,
                        localScaled[0] - localScaled[1] + localScaled[2],
                        scale1,
                        scale2
                    )
                    if (scale1._val <= scale2._val && scale1._val >= 0.0f) {
                        projectionBounds.AddPoint(idVec3(scale1._val * dFar, -1.0f, 1.0f))
                        projectionBounds.AddPoint(idVec3(scale2._val * dFar, -1.0f, 1.0f))
                    }
                }
                if (outside and 1 == 1 && outside and 8 == 8) {
                    frustum.LocalRayIntersection(
                        localOrigin,
                        localScaled[0] + localScaled[1] - localScaled[2],
                        scale1,
                        scale2
                    )
                    if (scale1._val <= scale2._val && scale1._val >= 0.0f) {
                        projectionBounds.AddPoint(idVec3(scale1._val * dFar, 1.0f, -1.0f))
                        projectionBounds.AddPoint(idVec3(scale2._val * dFar, 1.0f, -1.0f))
                    }
                }
                if (outside and 1 == 1 && outside and 2 == 2) {
                    frustum.LocalRayIntersection(
                        localOrigin,
                        localScaled[0] + localScaled[1] + localScaled[2],
                        scale1,
                        scale2
                    )
                    if (scale1._val <= scale2._val && scale1._val >= 0.0f) {
                        projectionBounds.AddPoint(idVec3(scale1._val * dFar, 1.0f, 1.0f))
                        projectionBounds.AddPoint(idVec3(scale2._val * dFar, 1.0f, 1.0f))
                    }
                }
            }
            return true
        }

        fun ProjectionBounds(winding: idWinding, projectionBounds: idBounds): Boolean {
            var i: Int
            var p1: Int
            var p2: Int
            var culled: Int
            var outside: Int
            val scale = CFloat()
            val localPoints: Array<idVec3> = idVec3.generateArray(winding.GetNumPoints())
            val transpose: idMat3
            val scaled = idMat3()
            val plane = idPlane()

            projectionBounds.Clear()

            // transform the winding points into the space of this frustum
            transpose = axis.Transpose()
            i = 0
            while (i < winding.GetNumPoints()) {
                localPoints[i].set((winding[i].ToVec3() - origin) * transpose)
                i++
            }

            // test the winding edges
            culled = -1
            outside = 0
            val pointCull = Array(winding.GetNumPoints()) { CInt() }
            i = 0
            while (i < winding.GetNumPoints()) {
                p1 = i
                p2 = (i + 1) % winding.GetNumPoints()
                AddLocalLineToProjectionBoundsSetCull(
                    localPoints[p1],
                    localPoints[p2],
                    pointCull[p1],
                    pointCull[p2],
                    projectionBounds
                )
                culled = culled and (pointCull[p1]._val and pointCull[p2]._val)
                outside = outside or (pointCull[p1]._val or pointCull[p2]._val)
                i += 2
            }

            // if completely culled
            if (culled != 0) {
                return false
            }

            // if completely inside
            if (0 == outside) {
                return true
            }

            // test remaining winding edges
            i = 1
            while (i < winding.GetNumPoints()) {
                p1 = i
                p2 = (i + 1) % winding.GetNumPoints()
                AddLocalLineToProjectionBoundsUseCull(
                    localPoints[p1],
                    localPoints[p2],
                    pointCull[p1]._val,
                    pointCull[p2]._val,
                    projectionBounds
                )
                i += 2
            }

            // if the winding extends beyond two or more boundaries of this frustum
            if (outside != 1 && outside != 2 && outside != 4 && outside != 8) {
                winding.GetPlane(plane)
                scaled[0] = axis[0] * dFar
                scaled[1] = axis[1] * dLeft
                scaled[2] = axis[2] * dUp

                // test the outer edges of this frustum for intersection with the winding
                if (outside and 2 == 2 && outside and 8 == 8) {
                    if (winding.RayIntersection(plane, origin, scaled[0] - scaled[1] + scaled[2], scale)
                    ) {
                        projectionBounds.AddPoint(idVec3(scale._val * dFar, -1.0f, -1.0f))
                    }
                }
                if (outside and 2 == 2 && outside and 4 == 4) {
                    if (winding.RayIntersection(plane, origin, scaled[0] - scaled[1] + scaled[2], scale)) {
                        projectionBounds.AddPoint(idVec3(scale._val * dFar, -1.0f, 1.0f))
                    }
                }
                if (outside and 1 == 1 && outside and 8 == 8) {
                    if (winding.RayIntersection(plane, origin, scaled[0] + scaled[1] - scaled[2], scale)) {
                        projectionBounds.AddPoint(idVec3(scale._val * dFar, 1.0f, -1.0f))
                    }
                }
                if (outside and 1 == 1 && outside and 2 == 2) {
                    if (winding.RayIntersection(plane, origin, scaled[0] + scaled[1] + scaled[2], scale)) {
                        projectionBounds.AddPoint(idVec3(scale._val * dFar, 1.0f, 1.0f))
                    }
                }
            }
            return true
        }

        // calculates the bounds for the projection in this frustum of the given frustum clipped to the given box
        fun ClippedProjectionBounds(frustum: idFrustum, clipBox: idBox, projectionBounds: idBounds): Boolean {
            var i: Int
            var p1: Int
            var p2: Int
            val usedClipPlanes: Int
            val nearCull: Int
            val farCull: Int
            var outside: Int
            val clipPointCull = Array(8) { CInt() }
            val clipPlanes = Array(4) { CInt() }
            val pointCull = Array(2) { CInt() }
            val boxPointCull = Array(8) { CInt() }
            val startClip = CInt()
            val endClip = CInt()
            val leftScale: Float
            val upScale: Float
            val s1 = CFloat()
            val s2 = CFloat()
            val t1 = CFloat()
            val t2 = CFloat()
            val clipFractions = Array(4) { CFloat() }
            val localFrustum: idFrustum
            val localOrigin1 = idVec3()
            val localOrigin2 = idVec3()
            val start = idVec3()
            val end = idVec3()
            val clipPoints: Array<idVec3> = idVec3.generateArray(8)
            val localPoints1: Array<idVec3> = idVec3.generateArray(8)
            val localPoints2: Array<idVec3> = idVec3.generateArray(8)
            val localAxis1 = idMat3()
            val localAxis2 = idMat3()
            val transpose = idMat3()
            val clipBounds = idBounds()

            // if the frustum origin is inside the other frustum
            if (frustum.ContainsPoint(origin)) {
                // bounds that cover the whole frustum
                val clipBoxMin = CFloat()
                val clipBoxMax = CFloat()
                val frustumMin = CFloat()
                val frustumMax = CFloat()
                val base = CFloat()
                base._val = origin * axis[0]
                clipBox.AxisProjection(axis[0], clipBoxMin, clipBoxMax)
                frustum.AxisProjection(axis[0], frustumMin, frustumMax)
                projectionBounds[0].x = Max(clipBoxMin._val, frustumMin._val) - base._val
                projectionBounds[1].x = Min(clipBoxMax._val, frustumMax._val) - base._val
                projectionBounds[0].z = -1.0f
                projectionBounds[0].y = projectionBounds[0].z
                projectionBounds[1].z = 1.0f
                projectionBounds[1].y = projectionBounds[1].z
                return true
            }
            projectionBounds.Clear()

            // clip the outer edges of the given frustum to the clip bounds
            frustum.ClipFrustumToBox(clipBox, clipFractions, clipPlanes)
            usedClipPlanes =
                clipPlanes[0]._val or clipPlanes[1]._val or clipPlanes[2]._val or clipPlanes[3]._val

            // transform the clipped frustum to the space of this frustum
            transpose.set(axis)
            transpose.TransposeSelf()
            localFrustum = idFrustum(frustum)
            localFrustum.origin.set((frustum.origin - origin) * transpose)
            localFrustum.axis.setMul(frustum.axis, transpose)
            localFrustum.ToClippedPoints(clipFractions, clipPoints)

            // test outer four edges of the clipped frustum
            i = 0
            while (i < 4) {
                p1 = i
                p2 = 4 + i
                val clipPointCull_p1 = CInt()
                val clipPointCull_p2 = CInt()
                AddLocalLineToProjectionBoundsSetCull(
                    clipPoints[p1],
                    clipPoints[p2],
                    clipPointCull_p1,
                    clipPointCull_p2,
                    projectionBounds
                )
                clipPointCull[p1]._val = (clipPointCull_p1._val)
                clipPointCull[p2]._val = (clipPointCull_p2._val)
                i++
            }

            // get cull bits for the clipped frustum
            outside =
                (clipPointCull[0]._val or clipPointCull[1]._val or clipPointCull[2]._val or clipPointCull[3]._val
                        or clipPointCull[4]._val or clipPointCull[5]._val or clipPointCull[6]._val or clipPointCull[7]._val)
            nearCull =
                clipPointCull[0]._val and clipPointCull[1]._val and clipPointCull[2]._val and clipPointCull[3]._val
            farCull =
                clipPointCull[4]._val and clipPointCull[5]._val and clipPointCull[6]._val and clipPointCull[7]._val

            // if the clipped frustum is not completely inside this frustum
            if (outside != 0) {

                // test the remaining edges of the clipped frustum
                if (0 == nearCull && localFrustum.dNear > 0.0f) {
                    i = 0
                    while (i < 4) {
                        p1 = i
                        p2 = i + 1 and 3
                        AddLocalLineToProjectionBoundsUseCull(
                            clipPoints[p1],
                            clipPoints[p2],
                            clipPointCull[p1]._val,
                            clipPointCull[p2]._val,
                            projectionBounds
                        )
                        i++
                    }
                }
                if (0 == farCull) {
                    i = 0
                    while (i < 4) {
                        p1 = 4 + i
                        p2 = 4 + (i + 1 and 3)
                        AddLocalLineToProjectionBoundsUseCull(
                            clipPoints[p1],
                            clipPoints[p2],
                            clipPointCull[p1]._val,
                            clipPointCull[p2]._val,
                            projectionBounds
                        )
                        i++
                    }
                }
            }

            // if the clipped frustum far end points are inside this frustum
            if (!(farCull != 0 && 0 == nearCull and farCull)
                &&  // if the clipped frustum is not clipped to a single plane of the clip bounds
                (clipPlanes[0] !== clipPlanes[1] || clipPlanes[1] !== clipPlanes[2] || clipPlanes[2] !== clipPlanes[3])
            ) {

                // transform the clip box into the space of the other frustum
                transpose.set(frustum.axis)
                transpose.TransposeSelf()
                localOrigin1.set((clipBox.GetCenter() - frustum.origin) * transpose)
                localAxis1.setMul(clipBox.GetAxis(), transpose)
                BoxToPoints(localOrigin1, clipBox.GetExtents(), localAxis1, localPoints1)

                // cull the box corners with the other frustum
                leftScale = frustum.dLeft * frustum.invFar
                upScale = frustum.dUp * frustum.invFar
                i = 0
                while (i < 8) {
                    val p = localPoints1[i]
                    if (0 == boxVertPlanes[i] and usedClipPlanes || p.x <= 0.0f) {
                        boxPointCull[i]._val = (1 or 2 or 4 or 8)
                    } else {
                        boxPointCull[i]._val = (0)
                        if (abs(p.y) > p.x * leftScale) {
                            boxPointCull[i]._val = (boxPointCull[i]._val or 1 shl FLOATSIGNBITSET(p.y))
                        }
                        if (abs(p.z) > p.x * upScale) {
                            boxPointCull[i]._val = (boxPointCull[i]._val or 4 shl FLOATSIGNBITSET(p.z))
                        }
                    }
                    i++
                }

                // transform the clip box into the space of this frustum
                transpose.set(axis)
                transpose.TransposeSelf()
                localOrigin2.set((clipBox.GetCenter() - origin) * transpose)
                localAxis2.setMul(clipBox.GetAxis(), transpose)
                BoxToPoints(localOrigin2, clipBox.GetExtents(), localAxis2, localPoints2)

                // clip the edges of the clip bounds to the other frustum and add the clipped edges to the projection bounds
                i = 0
                while (i < 4) {
                    p1 = i
                    p2 = 4 + i
                    if (0 == boxPointCull[p1]._val and boxPointCull[p2]._val) {
                        if (frustum.ClipLine(localPoints1, localPoints2, p1, p2, start, end, startClip, endClip)) {
                            AddLocalLineToProjectionBoundsSetCull(start, end, pointCull[1], projectionBounds)
                            AddLocalCapsToProjectionBounds(
                                clipPoints,
                                4,
                                clipPointCull,
                                4,
                                start,
                                pointCull[0]._val,
                                startClip._val,
                                projectionBounds
                            )
                            AddLocalCapsToProjectionBounds(
                                clipPoints,
                                4,
                                clipPointCull,
                                4,
                                end,
                                pointCull[1]._val,
                                endClip._val,
                                projectionBounds
                            )
                            outside = outside or (pointCull[0]._val or pointCull[1]._val)
                        }
                    }
                    i++
                }
                i = 0
                while (i < 4) {
                    p1 = i
                    p2 = i + 1 and 3
                    if (0 == boxPointCull[p1]._val and boxPointCull[p2]._val) {
                        if (frustum.ClipLine(localPoints1, localPoints2, p1, p2, start, end, startClip, endClip)) {
                            AddLocalLineToProjectionBoundsSetCull(start, end, pointCull[1], projectionBounds)
                            AddLocalCapsToProjectionBounds(
                                clipPoints,
                                4,
                                clipPointCull,
                                4,
                                start,
                                pointCull[0]._val,
                                startClip._val,
                                projectionBounds
                            )
                            AddLocalCapsToProjectionBounds(
                                clipPoints,
                                4,
                                clipPointCull,
                                4,
                                end,
                                pointCull[1]._val,
                                endClip._val,
                                projectionBounds
                            )
                            outside = outside or (pointCull[0]._val or pointCull[1]._val)
                        }
                    }
                    i++
                }
                i = 0
                while (i < 4) {
                    p1 = 4 + i
                    p2 = 4 + (i + 1 and 3)
                    if (0 == boxPointCull[p1]._val and boxPointCull[p2]._val) {
                        if (frustum.ClipLine(localPoints1, localPoints2, p1, p2, start, end, startClip, endClip)) {
                            AddLocalLineToProjectionBoundsSetCull(start, end, pointCull[1], projectionBounds)
                            AddLocalCapsToProjectionBounds(
                                clipPoints,
                                4,
                                clipPointCull,
                                4,
                                start,
                                pointCull[0]._val,
                                startClip._val,
                                projectionBounds
                            )
                            AddLocalCapsToProjectionBounds(
                                clipPoints,
                                4,
                                clipPointCull,
                                4,
                                end,
                                pointCull[1]._val,
                                endClip._val,
                                projectionBounds
                            )
                            outside = outside or (pointCull[0]._val or pointCull[1]._val)
                        }
                    }
                    i++
                }
            }

            // if the clipped frustum extends beyond two or more boundaries of this frustum
            if (outside != 1 && outside != 2 && outside != 4 && outside != 8) {

                // transform this frustum into the space of the other frustum
                transpose.set(frustum.axis)
                transpose.TransposeSelf()
                localOrigin1.set((origin - frustum.origin) * transpose)
                localAxis1.setMul(axis, transpose)
                localAxis1[0].timesAssign(dFar)
                localAxis1[1].timesAssign(dLeft)
                localAxis1[2].timesAssign(dUp)

                // transform this frustum into the space of the clip bounds
                transpose.set(clipBox.GetAxis())
                transpose.TransposeSelf()
                localOrigin2.set((origin - clipBox.GetCenter()) * transpose)
                localAxis2.setMul(axis, transpose)
                localAxis2[0].timesAssign(dFar)
                localAxis2[1].timesAssign(dLeft)
                localAxis2[2].timesAssign(dUp)
                clipBounds[0] = -clipBox.GetExtents()
                clipBounds[1] = clipBox.GetExtents()

                // test the outer edges of this frustum for intersection with both the other frustum and the clip bounds
                if (outside and 2 != 0 && outside and 8 != 0) {
                    frustum.LocalRayIntersection(localOrigin1, localAxis1[0] - localAxis1[1] - localAxis1[2], s1, s2)
                    if (s1._val <= s2._val && s1._val >= 0.0f) {
                        BoundsRayIntersection(
                            clipBounds,
                            localOrigin2,
                            localAxis2[0] - localAxis2[1] - localAxis2[2],
                            t1,
                            t2
                        )
                        if (t1._val <= t2._val && t2._val > s1._val && t1._val < s2._val) {
                            projectionBounds.AddPoint(idVec3(s1._val * dFar, -1.0f, -1.0f))
                            projectionBounds.AddPoint(idVec3(s2._val * dFar, -1.0f, -1.0f))
                        }
                    }
                }
                if (outside and 2 != 0 && outside and 4 != 0) {
                    frustum.LocalRayIntersection(localOrigin1, localAxis1[0] - localAxis1[1] + localAxis1[2], s1, s2)
                    if (s1._val <= s2._val && s1._val >= 0.0f) {
                        BoundsRayIntersection(
                            clipBounds,
                            localOrigin2,
                            localAxis2[0] - localAxis2[1] + localAxis2[2],
                            t1,
                            t2
                        )
                        if (t1._val <= t2._val && t2._val > s1._val && t1._val < s2._val) {
                            projectionBounds.AddPoint(idVec3(s1._val * dFar, -1.0f, 1.0f))
                            projectionBounds.AddPoint(idVec3(s2._val * dFar, -1.0f, 1.0f))
                        }
                    }
                }
                if (outside and 1 != 0 && outside and 8 != 0) {
                    frustum.LocalRayIntersection(localOrigin1, localAxis1[0] + localAxis1[1] - localAxis1[2], s1, s2)
                    if (s1._val <= s2._val && s1._val >= 0.0f) {
                        BoundsRayIntersection(
                            clipBounds,
                            localOrigin2,
                            localAxis2[0] + localAxis2[1] - localAxis2[2],
                            t1,
                            t2
                        )
                        if (t1._val <= t2._val && t2._val > s1._val && t1._val < s2._val) {
                            projectionBounds.AddPoint(idVec3(s1._val * dFar, 1.0f, -1.0f))
                            projectionBounds.AddPoint(idVec3(s2._val * dFar, 1.0f, -1.0f))
                        }
                    }
                }
                if (outside and 1 != 0 && outside and 2 != 0) {
                    frustum.LocalRayIntersection(localOrigin1, localAxis1[0] + localAxis1[1] + localAxis1[2], s1, s2)
                    if (s1._val <= s2._val && s1._val >= 0.0f) {
                        BoundsRayIntersection(
                            clipBounds,
                            localOrigin2,
                            localAxis2[0] + localAxis2[1] + localAxis2[2],
                            t1,
                            t2
                        )
                        if (t1._val <= t2._val && t2._val > s1._val && t1._val < s2._val) {
                            projectionBounds.AddPoint(idVec3(s1._val * dFar, 1.0f, 1.0f))
                            projectionBounds.AddPoint(idVec3(s2._val * dFar, 1.0f, 1.0f))
                        }
                    }
                }
            }
            return true
        }

        /*
         ============
         idFrustum::CullLocalBox

         Tests if any of the planes of the frustum can be used as a separating plane.

         3 muls best case
         25 muls worst case
         ============
         */
        private fun CullLocalBox(localOrigin: idVec3, extents: idVec3, localAxis: idMat3): Boolean {
            var d1: Float
            var d2: Float
            val testOrigin = idVec3()
            val testAxis: idMat3

            // near plane
            d1 = dNear - localOrigin.x
            d2 = (abs(extents[0] * localAxis[0][0])
                    + abs(extents[1] * localAxis[1][0])
                    + abs(extents[2] * localAxis[2][0]))
            if (d1 - d2 > 0.0f) {
                return true
            }

            // far plane
            d1 = localOrigin.x - dFar
            if (d1 - d2 > 0.0f) {
                return true
            }
            testOrigin.set(localOrigin)
            testAxis = idMat3(localAxis)
            if (testOrigin.y < 0.0f) {
                testOrigin.y = -testOrigin.y
                testAxis[0][1] = -testAxis[0][1]
                testAxis[1][1] = -testAxis[1][1]
                testAxis[2][1] = -testAxis[2][1]  // Should be testAxis[2][1]
            }

            // test left/right planes
            d1 = dFar * testOrigin.y - dLeft * testOrigin.x
            d2 = (abs(extents[0] * (dFar * testAxis[0][1] - dLeft * testAxis[0][0]))
                    + abs(extents[1] * (dFar * testAxis[1][1] - dLeft * testAxis[1][0]))
                    + abs(extents[2] * (dFar * testAxis[2][1] - dLeft * testAxis[2][0])))
            if (d1 - d2 > 0.0f) {
                return true
            }
            if (testOrigin.z < 0.0f) {
                testOrigin.z = -testOrigin.z
                testAxis[0][2] = -testAxis[0][2]
                testAxis[1][2] = -testAxis[1][2]
                testAxis[2][2] = -testAxis[2][2]
            }

            // test up/down planes
            d1 = dFar * testOrigin.z - dUp * testOrigin.x
            d2 = (abs(extents[0] * (dFar * testAxis[0][2] - dUp * testAxis[0][0]))
                    + abs(extents[1] * (dFar * testAxis[1][2] - dUp * testAxis[1][0]))
                    + abs(extents[2] * (dFar * testAxis[2][2] - dUp * testAxis[2][0])))
            return d1 - d2 > 0.0f
        }

        /*
         ============
         idFrustum::CullLocalFrustum

         Tests if any of the planes of this frustum can be used as a separating plane.

         0 muls best case
         30 muls worst case
         ============
         */
        private fun CullLocalFrustum(
            localFrustum: idFrustum,
            indexPoints: Array<idVec3>,
            cornerVecs: Array<idVec3>
        ): Boolean {
            var index: Int
            var dx: Float
            var dy: Float
            var dz: Float
            val leftScale: Float
            val upScale: Float

            // test near plane
            dy = -localFrustum.axis[1].x
            dz = -localFrustum.axis[2].x
            index = FLOATSIGNBITSET(dy) shl 1 or FLOATSIGNBITSET(dz)
            dx = -cornerVecs[index].x
            index = index or (FLOATSIGNBITSET(dx) shl 2)
            if (indexPoints[index].x < dNear) {
                return true
            }

            // test far plane
            dy = localFrustum.axis[1].x
            dz = localFrustum.axis[2].x
            index = FLOATSIGNBITSET(dy) shl 1 or FLOATSIGNBITSET(dz)
            dx = cornerVecs[index].x
            index = index or (FLOATSIGNBITSET(dx) shl 2)
            if (indexPoints[index].x > dFar) {
                return true
            }
            leftScale = dLeft * invFar

            // test left plane
            dy = dFar * localFrustum.axis[1].y - dLeft * localFrustum.axis[1].x
            dz = dFar * localFrustum.axis[2].y - dLeft * localFrustum.axis[2].x
            index = FLOATSIGNBITSET(dy) shl 1 or FLOATSIGNBITSET(dz)
            dx = dFar * cornerVecs[index].y - dLeft * cornerVecs[index].x
            index = index or (FLOATSIGNBITSET(dx) shl 2)
            if (indexPoints[index].y > indexPoints[index].x * leftScale) {
                return true
            }

            // test right plane
            dy = -dFar * localFrustum.axis[1].y - dLeft * localFrustum.axis[1].x
            dz = -dFar * localFrustum.axis[2].y - dLeft * localFrustum.axis[2].x
            index = FLOATSIGNBITSET(dy) shl 1 or FLOATSIGNBITSET(dz)
            dx = -dFar * cornerVecs[index].y - dLeft * cornerVecs[index].x
            index = index or (FLOATSIGNBITSET(dx) shl 2)
            if (indexPoints[index].y < -indexPoints[index].x * leftScale) {
                return true
            }
            upScale = dUp * invFar

            // test up plane
            dy = dFar * localFrustum.axis[1].z - dUp * localFrustum.axis[1].x
            dz = dFar * localFrustum.axis[2].z - dUp * localFrustum.axis[2].x
            index = FLOATSIGNBITSET(dy) shl 1 or FLOATSIGNBITSET(dz)
            dx = dFar * cornerVecs[index].z - dUp * cornerVecs[index].x
            index = index or (FLOATSIGNBITSET(dx) shl 2)
            if (indexPoints[index].z > indexPoints[index].x * upScale) {
                return true
            }

            // test down plane
            dy = -dFar * localFrustum.axis[1].z - dUp * localFrustum.axis[1].x
            dz = -dFar * localFrustum.axis[2].z - dUp * localFrustum.axis[2].x
            index = FLOATSIGNBITSET(dy) shl 1 or FLOATSIGNBITSET(dz)
            dx = -dFar * cornerVecs[index].z - dUp * cornerVecs[index].x
            index = index or (FLOATSIGNBITSET(dx) shl 2)
            return indexPoints[index].z < -indexPoints[index].x * upScale
        }

        private fun CullLocalWinding(points: Array<idVec3>, numPoints: Int, pointCull: IntArray): Boolean {
            var i: Int
            var pCull: Int
            var culled: Int
            val leftScale: Float
            val upScale: Float
            leftScale = dLeft * invFar
            upScale = dUp * invFar
            culled = -1
            i = 0
            while (i < numPoints) {
                val p = points[i]
                pCull = 0
                if (p.x < dNear) {
                    pCull = 1
                } else if (p.x > dFar) {
                    pCull = 2
                }
                if (abs(p.y) > p.x * leftScale) {
                    pCull = pCull or (4 shl FLOATSIGNBITSET(p.y))
                }
                if (abs(p.z) > p.x * upScale) {
                    pCull = pCull or (16 shl FLOATSIGNBITSET(p.z))
                }
                culled = culled and pCull
                pointCull[i] = pCull
                i++
            }
            return culled != 0
        }

        /*
         ============
         idFrustum::BoundsCullLocalFrustum

         Tests if any of the bounding box planes can be used as a separating plane.
         ============
         */
        private fun BoundsCullLocalFrustum(
            bounds: idBounds,
            localFrustum: idFrustum,
            indexPoints: Array<idVec3>,
            cornerVecs: Array<idVec3>
        ): Boolean {
            var index: Int
            var dx: Float
            var dy: Float
            var dz: Float
            dy = -localFrustum.axis[1].x
            dz = -localFrustum.axis[2].x
            index = FLOATSIGNBITSET(dy) shl 1 or FLOATSIGNBITSET(dz)
            dx = -cornerVecs[index].x
            index = index or (FLOATSIGNBITSET(dx) shl 2)
            if (indexPoints[index].x < bounds[0].x) {
                return true
            }
            dy = localFrustum.axis[1].x
            dz = localFrustum.axis[2].x
            index = FLOATSIGNBITSET(dy) shl 1 or FLOATSIGNBITSET(dz)
            dx = cornerVecs[index].x
            index = index or (FLOATSIGNBITSET(dx) shl 2)
            if (indexPoints[index].x > bounds[1].x) {
                return true
            }
            dy = -localFrustum.axis[1].y
            dz = -localFrustum.axis[2].y
            index = FLOATSIGNBITSET(dy) shl 1 or FLOATSIGNBITSET(dz)
            dx = -cornerVecs[index].y
            index = index or (FLOATSIGNBITSET(dx) shl 2)
            if (indexPoints[index].y < bounds[0].y) {
                return true
            }
            dy = localFrustum.axis[1].y
            dz = localFrustum.axis[2].y
            index = FLOATSIGNBITSET(dy) shl 1 or FLOATSIGNBITSET(dz)
            dx = cornerVecs[index].y
            index = index or (FLOATSIGNBITSET(dx) shl 2)
            if (indexPoints[index].y > bounds[1].y) {
                return true
            }
            dy = -localFrustum.axis[1].z
            dz = -localFrustum.axis[2].z
            index = FLOATSIGNBITSET(dy) shl 1 or FLOATSIGNBITSET(dz)
            dx = -cornerVecs[index].z
            index = index or (FLOATSIGNBITSET(dx) shl 2)
            if (indexPoints[index].z < bounds[0].z) {
                return true
            }
            dy = localFrustum.axis[1].z
            dz = localFrustum.axis[2].z
            index = FLOATSIGNBITSET(dy) shl 1 or FLOATSIGNBITSET(dz)
            dx = cornerVecs[index].z
            index = index or (FLOATSIGNBITSET(dx) shl 2)
            return indexPoints[index].z > bounds[1].z
        }

        /*
         ============
         idFrustum::LocalLineIntersection

         7 divs
         30 muls
         ============
         */
        private fun LocalLineIntersection(start: idVec3, end: idVec3): Boolean {
            val dir = idVec3()
            var d1: Float
            var d2: Float
            var fstart: Float
            var fend: Float
            var lstart: Float
            var lend: Float
            var f: Float
            var x: Float
            val leftScale: Float
            val upScale: Float
            var startInside = 1
            leftScale = dLeft * invFar
            upScale = dUp * invFar
            dir.set(end - start)

            // test near plane
            if (dNear > 0.0f) {
                d1 = dNear - start.x
                startInside = startInside and FLOATSIGNBITSET(d1)
                if (FLOATNOTZERO(d1)) {
                    d2 = dNear - end.x
                    if (FLOATSIGNBITSET(d1) xor FLOATSIGNBITSET(d2) != 0) {
                        f = d1 / (d1 - d2)
                        if (abs(start.y + f * dir.y) <= dNear * leftScale) {
                            if (abs(start.z + f * dir.z) <= dNear * upScale) {
                                return true
                            }
                        }
                    }
                }
            }

            // test far plane
            d1 = start.x - dFar
            startInside = startInside and FLOATSIGNBITSET(d1)
            if (FLOATNOTZERO(d1)) {
                d2 = end.x - dFar
                if (FLOATSIGNBITSET(d1) xor FLOATSIGNBITSET(d2) != 0) {
                    f = d1 / (d1 - d2)
                    if (abs(start.y + f * dir.y) <= dFar * leftScale) {
                        if (abs(start.z + f * dir.z) <= dFar * upScale) {
                            return true
                        }
                    }
                }
            }
            fstart = dFar * start.y
            fend = dFar * end.y
            lstart = dLeft * start.x
            lend = dLeft * end.x

            // test left plane
            d1 = fstart - lstart
            startInside = startInside and FLOATSIGNBITSET(d1)
            if (FLOATNOTZERO(d1)) {
                d2 = fend - lend
                if (FLOATSIGNBITSET(d1) xor FLOATSIGNBITSET(d2) != 0) {
                    f = d1 / (d1 - d2)
                    x = start.x + f * dir.x
                    if (x >= dNear && x <= dFar) {
                        if (abs(start.z + f * dir.z) <= x * upScale) {
                            return true
                        }
                    }
                }
            }

            // test right plane
            d1 = -fstart - lstart
            startInside = startInside and FLOATSIGNBITSET(d1)
            if (FLOATNOTZERO(d1)) {
                d2 = -fend - lend
                if (FLOATSIGNBITSET(d1) xor FLOATSIGNBITSET(d2) != 0) {
                    f = d1 / (d1 - d2)
                    x = start.x + f * dir.x
                    if (x in dNear..dFar) {
                        if (abs(start.z + f * dir.z) <= x * upScale) {
                            return true
                        }
                    }
                }
            }
            fstart = dFar * start.z
            fend = dFar * end.z
            lstart = dUp * start.x
            lend = dUp * end.x

            // test up plane
            d1 = fstart - lstart
            startInside = startInside and FLOATSIGNBITSET(d1)
            if (FLOATNOTZERO(d1)) {
                d2 = fend - lend
                if (FLOATSIGNBITSET(d1) xor FLOATSIGNBITSET(d2) != 0) {
                    f = d1 / (d1 - d2)
                    x = start.x + f * dir.x
                    if (x in dNear..dFar) {
                        if (abs(start.y + f * dir.y) <= x * leftScale) {
                            return true
                        }
                    }
                }
            }

            // test down plane
            d1 = -fstart - lstart
            startInside = startInside and FLOATSIGNBITSET(d1)
            if (FLOATNOTZERO(d1)) {
                d2 = -fend - lend
                if (FLOATSIGNBITSET(d1) xor FLOATSIGNBITSET(d2) != 0) {
                    f = d1 / (d1 - d2)
                    x = start.x + f * dir.x
                    if (x in dNear..dFar) {
                        if (abs(start.y + f * dir.y) <= x * leftScale) {
                            return true
                        }
                    }
                }
            }
            return startInside != 0
        }

        /*
         ============
         idFrustum::LocalRayIntersection

         Returns true if the ray starts inside the frustum.
         If there was an intersection scale1 <= scale2
         ============
         */
        private fun LocalRayIntersection(start: idVec3, dir: idVec3, scale1: CFloat, scale2: CFloat): Boolean {
            val end = idVec3()
            var d1: Float
            var d2: Float
            var fstart: Float
            var fend: Float
            var lstart: Float
            var lend: Float
            var f: Float
            var x: Float
            val leftScale: Float
            val upScale: Float
            var startInside = 1

            leftScale = dLeft * invFar
            upScale = dUp * invFar
            end.set(start + dir)
            scale1._val = (idMath.INFINITY)
            scale2._val = (-idMath.INFINITY)

            // test near plane
            if (dNear > 0.0f) {
                d1 = dNear - start.x
                startInside = startInside and FLOATSIGNBITSET(d1)
                d2 = dNear - end.x
                if (d1 != d2) {
                    f = d1 / (d1 - d2)
                    if (abs(start.y + f * dir.y) <= dNear * leftScale) {
                        if (abs(start.z + f * dir.z) <= dNear * upScale) {
                            if (f < scale1._val) {
                                scale1._val = (f)
                            }
                            if (f > scale2._val) {
                                scale2._val = (f)
                            }
                        }
                    }
                }
            }

            // test far plane
            d1 = start.x - dFar
            startInside = startInside and FLOATSIGNBITSET(d1)
            d2 = end.x - dFar
            if (d1 != d2) {
                f = d1 / (d1 - d2)
                if (abs(start.y + f * dir.y) <= dFar * leftScale) {
                    if (abs(start.z + f * dir.z) <= dFar * upScale) {
                        if (f < scale1._val) {
                            scale1._val = (f)
                        }
                        if (f > scale2._val) {
                            scale2._val = (f)
                        }
                    }
                }
            }
            fstart = dFar * start.y
            fend = dFar * end.y
            lstart = dLeft * start.x
            lend = dLeft * end.x

            // test left plane
            d1 = fstart - lstart
            startInside = startInside and FLOATSIGNBITSET(d1)
            d2 = fend - lend
            if (d1 != d2) {
                f = d1 / (d1 - d2)
                x = start.x + f * dir.x
                if (x in dNear..dFar) {
                    if (abs(start.z + f * dir.z) <= x * upScale) {
                        if (f < scale1._val) {
                            scale1._val = (f)
                        }
                        if (f > scale2._val) {
                            scale2._val = (f)
                        }
                    }
                }
            }

            // test right plane
            d1 = -fstart - lstart
            startInside = startInside and FLOATSIGNBITSET(d1)
            d2 = -fend - lend
            if (d1 != d2) {
                f = d1 / (d1 - d2)
                x = start.x + f * dir.x
                if (x >= dNear && x <= dFar) {
                    if (abs(start.z + f * dir.z) <= x * upScale) {
                        if (f < scale1._val) {
                            scale1._val = (f)
                        }
                        if (f > scale2._val) {
                            scale2._val = (f)
                        }
                    }
                }
            }
            fstart = dFar * start.z
            fend = dFar * end.z
            lstart = dUp * start.x
            lend = dUp * end.x

            // test up plane
            d1 = fstart - lstart
            startInside = startInside and FLOATSIGNBITSET(d1)
            d2 = fend - lend
            if (d1 != d2) {
                f = d1 / (d1 - d2)
                x = start.x + f * dir.x
                if (x >= dNear && x <= dFar) {
                    if (abs(start.y + f * dir.y) <= x * leftScale) {
                        if (f < scale1._val) {
                            scale1._val = (f)
                        }
                        if (f > scale2._val) {
                            scale2._val = (f)
                        }
                    }
                }
            }

            // test down plane
            d1 = -fstart - lstart
            startInside = startInside and FLOATSIGNBITSET(d1)
            d2 = -fend - lend
            if (d1 != d2) {
                f = d1 / (d1 - d2)
                x = start.x + f * dir.x
                if (x >= dNear && x <= dFar) {
                    if (abs(start.y + f * dir.y) <= x * leftScale) {
                        if (f < scale1._val) {
                            scale1._val = (f)
                        }
                        if (f > scale2._val) {
                            scale2._val = (f)
                        }
                    }
                }
            }
            return startInside != 0
        }

        private fun LocalFrustumIntersectsFrustum(points: Array<idVec3>, testFirstSide: Boolean): Boolean {
            var i: Int

            // test if any edges of the other frustum intersect this frustum
            i = 0
            while (i < 4) {
                if (LocalLineIntersection(points[i], points[4 + i])) {
                    return true
                }
                i++
            }
            if (testFirstSide) {
                i = 0
                while (i < 4) {
                    if (LocalLineIntersection(points[i], points[i + 1 and 3])) {
                        return true
                    }
                    i++
                }
            }
            i = 0
            while (i < 4) {
                if (LocalLineIntersection(points[4 + i], points[4 + (i + 1 and 3)])) {
                    return true
                }
                i++
            }
            return false
        }

        private fun LocalFrustumIntersectsBounds(points: Array<idVec3>, bounds: idBounds): Boolean {
            var i: Int

            // test if any edges of the other frustum intersect this frustum
            i = 0
            while (i < 4) {
                if (bounds.LineIntersection(points[i], points[4 + i])) {
                    return true
                }
                i++
            }
            if (dNear > 0.0f) {
                i = 0
                while (i < 4) {
                    if (bounds.LineIntersection(points[i], points[i + 1 and 3])) {
                        return true
                    }
                    i++
                }
            }
            i = 0
            while (i < 4) {
                if (bounds.LineIntersection(points[4 + i], points[4 + (i + 1 and 3)])) {
                    return true
                }
                i++
            }
            return false
        }

        private fun ToClippedPoints(fractions: Array<CFloat>, points: Array<idVec3>) {
            val scaled = idMat3()

            scaled[0] = origin + axis[0] * dNear
            scaled[1] = axis[1] * (dLeft * dNear * invFar)
            scaled[2] = axis[2] * (dUp * dNear * invFar)

            points[0] = scaled[0] + scaled[1]
            points[1] = scaled[0] - scaled[1]
            points[2] = points[1] - scaled[2]
            points[3] = points[0] - scaled[2]
            points[0] += scaled[2]
            points[1] += scaled[2]

            scaled[0] = axis[0] * dFar
            scaled[1] = axis[1] * dLeft
            scaled[2] = axis[2] * dUp

            points[4] = scaled[0] + scaled[1]
            points[5] = scaled[0] - scaled[1]
            points[6] = points[5] - scaled[2]
            points[7] = points[4] - scaled[2]
            points[4] += scaled[2]
            points[5] += scaled[2]

            points[4] = origin + points[4] * fractions[0]._val
            points[5] = origin + points[5] * fractions[1]._val
            points[6] = origin + points[6] * fractions[2]._val
            points[7] = origin + points[7] * fractions[3]._val
        }

        private fun ToIndexPoints(indexPoints: Array<idVec3>) {
            val scaled = idMat3()

            scaled[0] = origin + axis[0] * dNear
            scaled[1] = axis[1] * (dLeft * dNear * invFar)
            scaled[2] = axis[2] * (dUp * dNear * invFar)

            indexPoints[0] = scaled[0] - scaled[1]
            indexPoints[2] = scaled[0] + scaled[1]
            indexPoints[1] = indexPoints[0] + scaled[2]
            indexPoints[3] = indexPoints[2] + scaled[2]
            indexPoints[0] -= scaled[2]
            indexPoints[2] -= scaled[2]

            scaled[0] = origin + axis[0] * dFar
            scaled[1] = axis[1] * dLeft
            scaled[2] = axis[2] * dUp

            indexPoints[4] = scaled[0] - scaled[1]
            indexPoints[6] = scaled[0] + scaled[1]
            indexPoints[5] = indexPoints[4] + scaled[2]
            indexPoints[7] = indexPoints[6] + scaled[2]
            indexPoints[4] -= scaled[2]
            indexPoints[6] -= scaled[2]
        }

        /*
         ============
         idFrustum::ToIndexPointsAndCornerVecs

         22 muls
         ============
         */
        private fun ToIndexPointsAndCornerVecs(indexPoints: Array<idVec3>, cornerVecs: Array<idVec3>) {
            val scaled = idMat3()

            scaled[0] = origin + axis[0] * dNear
            scaled[1] = axis[1] * (dLeft * dNear * invFar)
            scaled[2] = axis[2] * (dUp * dNear * invFar)

            indexPoints[0] = scaled[0] - scaled[1]
            indexPoints[2] = scaled[0] + scaled[1]
            indexPoints[1] = indexPoints[0] + scaled[2]
            indexPoints[3] = indexPoints[2] + scaled[2]
            indexPoints[0] -= scaled[2]
            indexPoints[2] -= scaled[2]

            scaled[0] = axis[0] * dFar
            scaled[1] = axis[1] * dLeft
            scaled[2] = axis[2] * dUp

            cornerVecs[0] = scaled[0] - scaled[1]
            cornerVecs[2] = scaled[0] + scaled[1]
            cornerVecs[1] = cornerVecs[0] + scaled[2]
            cornerVecs[3] = cornerVecs[2] + scaled[2]
            cornerVecs[0] -= scaled[2]
            cornerVecs[2] -= scaled[2]

            indexPoints[4] = cornerVecs[0] + origin
            indexPoints[5] = cornerVecs[1] + origin
            indexPoints[6] = cornerVecs[2] + origin
            indexPoints[7] = cornerVecs[3] + origin
        }

        /*
         ============
         idFrustum::AxisProjection

         18 muls
         ============
         */
        private fun AxisProjection(
            indexPoints: Array<idVec3>,
            cornerVecs: Array<idVec3>,
            dir: idVec3,
            min: CFloat,
            max: CFloat
        ) {
            var dx: Float
            val dy: Float
            val dz: Float
            var index: Int
            dy = dir.x * axis[1].x + dir.y * axis[1].y + dir.z * axis[1].z
            dz = dir.x * axis[2].x + dir.y * axis[2].y + dir.z * axis[2].z
            index = FLOATSIGNBITSET(dy) shl 1 or FLOATSIGNBITSET(dz)
            dx = dir.x * cornerVecs[index].x + dir.y * cornerVecs[index].y + dir.z * cornerVecs[index].z
            index = index or (FLOATSIGNBITSET(dx) shl 2)
            min._val = indexPoints[index] * dir
            index = index.inv() and 3
            dx = -dir.x * cornerVecs[index].x - dir.y * cornerVecs[index].y - dir.z * cornerVecs[index].z
            index = index or (FLOATSIGNBITSET(dx) shl 2)
            max._val = indexPoints[index] * dir
        }

        private fun AddLocalLineToProjectionBoundsSetCull(
            start: idVec3,
            end: idVec3,
            cull: CInt,
            bounds: idBounds
        ) {
            val cull2 = CInt()
            AddLocalLineToProjectionBoundsSetCull(start, end, cull, cull2, bounds)
            cull._val = (cull2._val)
        }

        private fun AddLocalLineToProjectionBoundsSetCull(
            start: idVec3,
            end: idVec3,
            startCull: CInt,
            endCull: CInt,
            bounds: idBounds
        ) {
            val dir = idVec3()
            val p = idVec3()
            var d1: Float
            var d2: Float
            var fstart: Float
            var fend: Float
            var lstart: Float
            var lend: Float
            var f: Float
            val leftScale: Float
            val upScale: Float
            var cull1: Int
            var cull2: Int

//#ifdef FRUSTUM_DEBUG
//	static idCVar r_showInteractionScissors( "r_showInteractionScissors", "0", CVAR_RENDERER | CVAR_INTEGER, "", 0, 2, idCmdSystem::ArgCompletion_Integer<0,2> )
//	if ( r_showInteractionScissors.GetInteger() > 1 ) {
//		session->rw->DebugLine( colorGreen, origin + start * axis, origin + end * axis )
//	}
//#endif
            leftScale = dLeft * invFar
            upScale = dUp * invFar
            dir.set(end - start)
            fstart = dFar * start.y
            fend = dFar * end.y
            lstart = dLeft * start.x
            lend = dLeft * end.x

            // test left plane
            d1 = -fstart + lstart
            d2 = -fend + lend
            cull1 = FLOATSIGNBITSET(d1)
            cull2 = FLOATSIGNBITSET(d2)
            if (FLOATNOTZERO(d1)) {
                if (FLOATSIGNBITSET(d1) xor FLOATSIGNBITSET(d2) != 0) {
                    f = d1 / (d1 - d2)
                    p.x = start.x + f * dir.x
                    if (p.x > 0.0f) {
                        p.z = start.z + f * dir.z
                        if (abs(p.z) <= p.x * upScale) {
                            p.y = 1.0f
                            p.z = p.z * dFar / (p.x * dUp)
                            bounds.AddPoint(p)
                        }
                    }
                }
            }

            // test right plane
            d1 = fstart + lstart
            d2 = fend + lend
            cull1 = cull1 or (FLOATSIGNBITSET(d1) shl 1)
            cull2 = cull2 or (FLOATSIGNBITSET(d2) shl 1)
            if (FLOATNOTZERO(d1)) {
                if (FLOATSIGNBITSET(d1) xor FLOATSIGNBITSET(d2) != 0) {
                    f = d1 / (d1 - d2)
                    p.x = start.x + f * dir.x
                    if (p.x > 0.0f) {
                        p.z = start.z + f * dir.z
                        if (abs(p.z) <= p.x * upScale) {
                            p.y = -1.0f
                            p.z = p.z * dFar / (p.x * dUp)
                            bounds.AddPoint(p)
                        }
                    }
                }
            }
            fstart = dFar * start.z
            fend = dFar * end.z
            lstart = dUp * start.x
            lend = dUp * end.x

            // test up plane
            d1 = -fstart + lstart
            d2 = -fend + lend
            cull1 = cull1 or (FLOATSIGNBITSET(d1) shl 2)
            cull2 = cull2 or (FLOATSIGNBITSET(d2) shl 2)
            if (FLOATNOTZERO(d1)) {
                if (FLOATSIGNBITSET(d1) xor FLOATSIGNBITSET(d2) != 0) {
                    f = d1 / (d1 - d2)
                    p.x = start.x + f * dir.x
                    if (p.x > 0.0f) {
                        p.y = start.y + f * dir.y
                        if (abs(p.y) <= p.x * leftScale) {
                            p.y = p.y * dFar / (p.x * dLeft)
                            p.z = 1.0f
                            bounds.AddPoint(p)
                        }
                    }
                }
            }

            // test down plane
            d1 = fstart + lstart
            d2 = fend + lend
            cull1 = cull1 or (FLOATSIGNBITSET(d1) shl 3)
            cull2 = cull2 or (FLOATSIGNBITSET(d2) shl 3)
            if (FLOATNOTZERO(d1)) {
                if (FLOATSIGNBITSET(d1) xor FLOATSIGNBITSET(d2) != 0) {
                    f = d1 / (d1 - d2)
                    p.x = start.x + f * dir.x
                    if (p.x > 0.0f) {
                        p.y = start.y + f * dir.y
                        if (abs(p.y) <= p.x * leftScale) {
                            p.y = p.y * dFar / (p.x * dLeft)
                            p.z = -1.0f
                            bounds.AddPoint(p)
                        }
                    }
                }
            }
            if (cull1 == 0 && start.x > 0.0f) {
                // add start point to projection bounds
                p.x = start.x
                p.y = start.y * dFar / (start.x * dLeft)
                p.z = start.z * dFar / (start.x * dUp)
                bounds.AddPoint(p)
            }
            if (cull2 == 0 && end.x > 0.0f) {
                // add end point to projection bounds
                p.x = end.x
                p.y = end.y * dFar / (end.x * dLeft)
                p.z = end.z * dFar / (end.x * dUp)
                bounds.AddPoint(p)
            }
            if (start.x < bounds[0].x) {
                bounds[0].x = if (start.x < 0.0f) 0.0f else start.x
            }
            if (end.x < bounds[0].x) {
                bounds[0].x = if (end.x < 0.0f) 0.0f else end.x
            }
            startCull._val = (cull1)
            endCull._val = (cull2)
        }

        private fun AddLocalLineToProjectionBoundsUseCull(
            start: idVec3,
            end: idVec3,
            startCull: Int,
            endCull: Int,
            bounds: idBounds
        ) {
            val dir = idVec3()
            val p = idVec3()
            var d1: Float
            var d2: Float
            var fstart: Float
            var fend: Float
            var lstart: Float
            var lend: Float
            var f: Float
            val leftScale: Float
            val upScale: Float
            val clip: Int
            clip = startCull xor endCull
            if (0 == clip) {
                return
            }

//#ifdef FRUSTUM_DEBUG
//	static idCVar r_showInteractionScissors( "r_showInteractionScissors", "0", CVAR_RENDERER | CVAR_INTEGER, "", 0, 2, idCmdSystem.ArgCompletion_Integer<0,2> )
//	if ( r_showInteractionScissors.GetInteger() > 1 ) {
//		session->rw->DebugLine( colorGreen, origin + start * axis, origin + end * axis )
//	}
//#endif
            leftScale = dLeft * invFar
            upScale = dUp * invFar
            dir.set(end - start)
            if (clip and (1 or 2) != 0) {
                fstart = dFar * start.y
                fend = dFar * end.y
                lstart = dLeft * start.x
                lend = dLeft * end.x
                if (clip and 1 != 0) {
                    // test left plane
                    d1 = -fstart + lstart
                    d2 = -fend + lend
                    if (FLOATNOTZERO(d1)) {
                        if (FLOATSIGNBITSET(d1) xor FLOATSIGNBITSET(d2) != 0) {
                            f = d1 / (d1 - d2)
                            p.x = start.x + f * dir.x
                            if (p.x > 0.0f) {
                                p.z = start.z + f * dir.z
                                if (abs(p.z) <= p.x * upScale) {
                                    p.y = 1.0f
                                    p.z = p.z * dFar / (p.x * dUp)
                                    bounds.AddPoint(p)
                                }
                            }
                        }
                    }
                }
                if (clip and 2 != 0) {
                    // test right plane
                    d1 = fstart + lstart
                    d2 = fend + lend
                    if (FLOATNOTZERO(d1)) {
                        if (FLOATSIGNBITSET(d1) xor FLOATSIGNBITSET(d2) != 0) {
                            f = d1 / (d1 - d2)
                            p.x = start.x + f * dir.x
                            if (p.x > 0.0f) {
                                p.z = start.z + f * dir.z
                                if (abs(p.z) <= p.x * upScale) {
                                    p.y = -1.0f
                                    p.z = p.z * dFar / (p.x * dUp)
                                    bounds.AddPoint(p)
                                }
                            }
                        }
                    }
                }
            }
            if (clip and (4 or 8) != 0) {
                fstart = dFar * start.z
                fend = dFar * end.z
                lstart = dUp * start.x
                lend = dUp * end.x
                if (clip and 4 != 0) {
                    // test up plane
                    d1 = -fstart + lstart
                    d2 = -fend + lend
                    if (FLOATNOTZERO(d1)) {
                        if (FLOATSIGNBITSET(d1) xor FLOATSIGNBITSET(d2) != 0) {
                            f = d1 / (d1 - d2)
                            p.x = start.x + f * dir.x
                            if (p.x > 0.0f) {
                                p.y = start.y + f * dir.y
                                if (abs(p.y) <= p.x * leftScale) {
                                    p.y = p.y * dFar / (p.x * dLeft)
                                    p.z = 1.0f
                                    bounds.AddPoint(p)
                                }
                            }
                        }
                    }
                }
                if (clip and 8 != 0) {
                    // test down plane
                    d1 = fstart + lstart
                    d2 = fend + lend
                    if (FLOATNOTZERO(d1)) {
                        if (FLOATSIGNBITSET(d1) xor FLOATSIGNBITSET(d2) != 0) {
                            f = d1 / (d1 - d2)
                            p.x = start.x + f * dir.x
                            if (p.x > 0.0f) {
                                p.y = start.y + f * dir.y
                                if (abs(p.y) <= p.x * leftScale) {
                                    p.y = p.y * dFar / (p.x * dLeft)
                                    p.z = -1.0f
                                    bounds.AddPoint(p)
                                }
                            }
                        }
                    }
                }
            }
        }

        private fun AddLocalCapsToProjectionBounds(
            endPoints: Array<idVec3>,
            endPointsOffset: Int,
            endPointCull: Array<CInt>,
            endPointCullOffset: Int,
            point: idVec3,
            pointCull: Int,
            pointClip: Int,
            projectionBounds: idBounds
        ): Boolean {
            val p: IntArray
            if (pointClip < 0) {
                return false
            }
            p = capPointIndex[pointClip]
            AddLocalLineToProjectionBoundsUseCull(
                endPoints[endPointsOffset + p[0]],
                point,
                endPointCull[endPointCullOffset + p[0]]._val,
                pointCull,
                projectionBounds
            )
            AddLocalLineToProjectionBoundsUseCull(
                endPoints[endPointsOffset + p[1]],
                point,
                endPointCull[endPointCullOffset + p[1]]._val,
                pointCull,
                projectionBounds
            )
            return true
        }

        private fun AddLocalCapsToProjectionBounds(
            endPoints: Array<idVec3>,
            endPointCull: Array<CInt>,
            point: idVec3,
            pointCull: Int,
            pointClip: Int,
            projectionBounds: idBounds
        ): Boolean {
            return AddLocalCapsToProjectionBounds(
                endPoints,
                0,
                endPointCull,
                0,
                point,
                pointCull,
                pointClip,
                projectionBounds
            )
        }

        /*
         ============
         idFrustum::BoundsRayIntersection

         Returns true if the ray starts inside the bounds.
         If there was an intersection scale1 <= scale2
         ============
         */
        private fun BoundsRayIntersection(
            bounds: idBounds,
            start: idVec3,
            dir: idVec3,
            scale1: CFloat,
            scale2: CFloat
        ): Boolean {
            val end = idVec3()
            val p = idVec3()
            var d1: Float
            var d2: Float
            var f: Float
            var i: Int
            var startInside = 1

            scale1._val = (idMath.INFINITY)
            scale2._val = (-idMath.INFINITY)

            end.set(start + dir)
            i = 0
            while (i < 2) {
                d1 = start.x - bounds[i].x
                startInside = startInside and (FLOATSIGNBITSET(d1) xor i)
                d2 = end.x - bounds[i].x
                if (d1 != d2) {
                    f = d1 / (d1 - d2)
                    p.y = start.y + f * dir.y
                    if (bounds[0].y <= p.y && p.y <= bounds[1].y) {
                        p.z = start.z + f * dir.z
                        if (bounds[0].z <= p.z && p.z <= bounds[1].z) {
                            if (f < scale1._val) {
                                scale1._val = (f)
                            }
                            if (f > scale2._val) {
                                scale2._val = (f)
                            }
                        }
                    }
                }
                d1 = start.y - bounds[i].y
                startInside = startInside and (FLOATSIGNBITSET(d1) xor i)
                d2 = end.y - bounds[i].y
                if (d1 != d2) {
                    f = d1 / (d1 - d2)
                    p.x = start.x + f * dir.x
                    if (bounds[0].x <= p.x && p.x <= bounds[1].x) {
                        p.z = start.z + f * dir.z
                        if (bounds[0].z <= p.z && p.z <= bounds[1].z) {
                            if (f < scale1._val) {
                                scale1._val = (f)
                            }
                            if (f > scale2._val) {
                                scale2._val = (f)
                            }
                        }
                    }
                }
                d1 = start.z - bounds[i].z
                startInside = startInside and (FLOATSIGNBITSET(d1) xor i)
                d2 = end.z - bounds[i].z
                if (d1 != d2) {
                    f = d1 / (d1 - d2)
                    p.x = start.x + f * dir.x
                    if (bounds[0].x <= p.x && p.x <= bounds[1].x) {
                        p.y = start.y + f * dir.y
                        if (bounds[0].y <= p.y && p.y <= bounds[1].y) {
                            if (f < scale1._val) {
                                scale1._val = (f)
                            }
                            if (f > scale2._val) {
                                scale2._val = (f)
                            }
                        }
                    }
                }
                i++
            }
            return startInside != 0
        }

        /*
         ============
         idFrustum::ClipFrustumToBox

         Clips the frustum far extents to the box.
         ============
         */
        private fun ClipFrustumToBox(box: idBox, clipFractions: Array<CFloat>, clipPlanes: Array<CInt>) {
            var i: Int
            var index: Int
            var f: Float
            val minf: Float
            val scaled = idMat3()
            val localAxis: idMat3
            val transpose: idMat3
            val localOrigin = idVec3()
            val cornerVecs: Array<idVec3> = idVec3.generateArray(4)
            val bounds = idBounds()

            transpose = box.GetAxis()
            transpose.TransposeSelf()
            localOrigin.set((origin - box.GetCenter()) * transpose)
            localAxis = axis * transpose

            scaled[0] = localAxis[0] * dFar
            scaled[1] = localAxis[1] * dLeft
            scaled[2] = localAxis[2] * dUp
            cornerVecs[0] = scaled[0] + scaled[1]
            cornerVecs[1] = scaled[0] - scaled[1]
            cornerVecs[2] = cornerVecs[1] - scaled[2]
            cornerVecs[3] = cornerVecs[0] - scaled[2]
            cornerVecs[0].plusAssign(scaled[2])
            cornerVecs[1].plusAssign(scaled[2])

            bounds[0] = -box.GetExtents()
            bounds[1] = box.GetExtents()

            minf = (dNear + 1.0f) * invFar
            i = 0
            while (i < 4) {
                index = FLOATSIGNBITNOTSET(cornerVecs[i].x)
                f = (bounds[index].x - localOrigin.x) / cornerVecs[i].x
                clipFractions[i]._val = (f)
                clipPlanes[i]._val = (1 shl index)
                index = FLOATSIGNBITNOTSET(cornerVecs[i].y)
                f = (bounds[index].y - localOrigin.y) / cornerVecs[i].y
                if (f < clipFractions[i]._val) {
                    clipFractions[i]._val = (f)
                    clipPlanes[i]._val = (4 shl index)
                }
                index = FLOATSIGNBITNOTSET(cornerVecs[i].z)
                f = (bounds[index].z - localOrigin.z) / cornerVecs[i].z
                if (f < clipFractions[i]._val) {
                    clipFractions[i]._val = (f)
                    clipPlanes[i]._val = (16 shl index)
                }

                // make sure the frustum is not clipped between the frustum origin and the near plane
                if (clipFractions[i]._val < minf) {
                    clipFractions[i]._val = (minf)
                }
                i++
            }
        }

        /*
         ============
         idFrustum::ClipLine

         Returns true if part of the line is inside the frustum.
         Does not clip to the near and far plane.
         ============
         */
        private fun ClipLine(
            localPoints: Array<idVec3>,
            points: Array<idVec3>,
            startIndex: Int,
            endIndex: Int,
            start: idVec3,
            end: idVec3,
            startClip: CInt,
            endClip: CInt
        ): Boolean {
            var d1: Float
            var d2: Float
            var fstart: Float
            var fend: Float
            var lstart: Float
            var lend: Float
            var f: Float
            var x: Float
            val leftScale: Float
            val upScale: Float
            var scale1: Float
            var scale2: Float
            var startCull: Int
            var endCull: Int
            val localStart = idVec3()
            val localEnd = idVec3()
            val localDir = idVec3()
            leftScale = dLeft * invFar
            upScale = dUp * invFar
            localStart.set(localPoints[startIndex])
            localEnd.set(localPoints[endIndex])
            localDir.set(localEnd - localStart)
            startClip._val = (endClip._val - 1)
            scale1 = idMath.INFINITY
            scale2 = -idMath.INFINITY
            fstart = dFar * localStart.y
            fend = dFar * localEnd.y
            lstart = dLeft * localStart.x
            lend = dLeft * localEnd.x

            // test left plane
            d1 = -fstart + lstart
            d2 = -fend + lend
            startCull = FLOATSIGNBITSET(d1)
            endCull = FLOATSIGNBITSET(d2)
            if (FLOATNOTZERO(d1)) {
                if (FLOATSIGNBITSET(d1) xor FLOATSIGNBITSET(d2) != 0) {
                    f = d1 / (d1 - d2)
                    x = localStart.x + f * localDir.x
                    if (x >= 0.0f) {
                        if (abs(localStart.z + f * localDir.z) <= x * upScale) {
                            if (f < scale1) {
                                scale1 = f
                                startClip._val = (0)
                            }
                            if (f > scale2) {
                                scale2 = f
                                endClip._val = (0)
                            }
                        }
                    }
                }
            }

            // test right plane
            d1 = fstart + lstart
            d2 = fend + lend
            startCull = startCull or (FLOATSIGNBITSET(d1) shl 1)
            endCull = endCull or (FLOATSIGNBITSET(d2) shl 1)
            if (FLOATNOTZERO(d1)) {
                if (FLOATSIGNBITSET(d1) xor FLOATSIGNBITSET(d2) != 0) {
                    f = d1 / (d1 - d2)
                    x = localStart.x + f * localDir.x
                    if (x >= 0.0f) {
                        if (abs(localStart.z + f * localDir.z) <= x * upScale) {
                            if (f < scale1) {
                                scale1 = f
                                startClip._val = (1)
                            }
                            if (f > scale2) {
                                scale2 = f
                                endClip._val = (1)
                            }
                        }
                    }
                }
            }
            fstart = dFar * localStart.z
            fend = dFar * localEnd.z
            lstart = dUp * localStart.x
            lend = dUp * localEnd.x

            // test up plane
            d1 = -fstart + lstart
            d2 = -fend + lend
            startCull = startCull or (FLOATSIGNBITSET(d1) shl 2)
            endCull = endCull or (FLOATSIGNBITSET(d2) shl 2)
            if (FLOATNOTZERO(d1)) {
                if (FLOATSIGNBITSET(d1) xor FLOATSIGNBITSET(d2) != 0) {
                    f = d1 / (d1 - d2)
                    x = localStart.x + f * localDir.x
                    if (x >= 0.0f) {
                        if (abs(localStart.y + f * localDir.y) <= x * leftScale) {
                            if (f < scale1) {
                                scale1 = f
                                startClip._val = (2)
                            }
                            if (f > scale2) {
                                scale2 = f
                                endClip._val = (2)
                            }
                        }
                    }
                }
            }

            // test down plane
            d1 = fstart + lstart
            d2 = fend + lend
            startCull = startCull or (FLOATSIGNBITSET(d1) shl 3)
            endCull = endCull or (FLOATSIGNBITSET(d2) shl 3)
            if (FLOATNOTZERO(d1)) {
                if (FLOATSIGNBITSET(d1) xor FLOATSIGNBITSET(d2) != 0) {
                    f = d1 / (d1 - d2)
                    x = localStart.x + f * localDir.x
                    if (x >= 0.0f) {
                        if (abs(localStart.y + f * localDir.y) <= x * leftScale) {
                            if (f < scale1) {
                                scale1 = f
                                startClip._val = (3)
                            }
                            if (f > scale2) {
                                scale2 = f
                                endClip._val = (3)
                            }
                        }
                    }
                }
            }

            // if completely inside
            if (0 == startCull or endCull) {
                start.set(points[startIndex])
                end.set(points[endIndex])
                return true
            } else if (scale1 <= scale2) {
                if (0 == startCull) {
                    start.set(points[startIndex])
                    startClip._val = (-1)
                } else {
                    start.set(
                        points[startIndex] + (points[endIndex] - points[startIndex]) * scale1
                    )
                }
                if (0 == endCull) {
                    end.set(points[endIndex])
                    endClip._val = (-1)
                } else {
                    end.set(points[startIndex] + (points[endIndex] - points[startIndex]) * scale2)
                }
                return true
            }
            return false
        }

        private fun set(f: idFrustum) {
            origin.set(f.origin)
            axis.set(f.axis)
            dNear = f.dNear
            dFar = f.dFar
            dLeft = f.dLeft
            dUp = f.dUp
            invFar = f.invFar
        }

        companion object {
            val capPointIndex: Array<IntArray> =
                arrayOf(intArrayOf(0, 3), intArrayOf(1, 2), intArrayOf(0, 1), intArrayOf(2, 3))
            private const val VORONOI_INDEX_0_0_0 = 0 + 0 * 3 + 0 * 9
            private const val VORONOI_INDEX_1_0_0 = 1 + 0 * 3 + 0 * 9
            private const val VORONOI_INDEX_2_0_0 = 2 + 0 * 3 + 0 * 9
            private const val VORONOI_INDEX_0_1_0 = 0 + 1 * 3 + 0 * 9
            private const val VORONOI_INDEX_0_2_0 = 0 + 2 * 3 + 0 * 9
            private const val VORONOI_INDEX_0_0_1 = 0 + 0 * 3 + 1 * 9
            private const val VORONOI_INDEX_0_0_2 = 0 + 0 * 3 + 2 * 9
            private const val VORONOI_INDEX_1_1_1 = 1 + 1 * 3 + 1 * 9
            private const val VORONOI_INDEX_2_1_1 = 2 + 1 * 3 + 1 * 9
            private const val VORONOI_INDEX_1_2_1 = 1 + 2 * 3 + 1 * 9
            private const val VORONOI_INDEX_2_2_1 = 2 + 2 * 3 + 1 * 9
            private const val VORONOI_INDEX_1_1_2 = 1 + 1 * 3 + 2 * 9
            private const val VORONOI_INDEX_2_1_2 = 2 + 1 * 3 + 2 * 9
            private const val VORONOI_INDEX_1_2_2 = 1 + 2 * 3 + 2 * 9
            private const val VORONOI_INDEX_2_2_2 = 2 + 2 * 3 + 2 * 9
            private const val VORONOI_INDEX_1_1_0 = 1 + 1 * 3 + 0 * 9
            private const val VORONOI_INDEX_2_1_0 = 2 + 1 * 3 + 0 * 9
            private const val VORONOI_INDEX_1_2_0 = 1 + 2 * 3 + 0 * 9
            private const val VORONOI_INDEX_2_2_0 = 2 + 2 * 3 + 0 * 9
            private const val VORONOI_INDEX_1_0_1 = 1 + 0 * 3 + 1 * 9
            private const val VORONOI_INDEX_2_0_1 = 2 + 0 * 3 + 1 * 9
            private const val VORONOI_INDEX_0_1_1 = 0 + 1 * 3 + 1 * 9
            private const val VORONOI_INDEX_0_2_1 = 0 + 2 * 3 + 1 * 9
            private const val VORONOI_INDEX_1_0_2 = 1 + 0 * 3 + 2 * 9
            private const val VORONOI_INDEX_2_0_2 = 2 + 0 * 3 + 2 * 9
            private const val VORONOI_INDEX_0_1_2 = 0 + 1 * 3 + 2 * 9
            private const val VORONOI_INDEX_0_2_2 = 0 + 2 * 3 + 2 * 9
        }
    }
}

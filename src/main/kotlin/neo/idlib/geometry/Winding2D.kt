package neo.idlib.geometry

import neo.idlib.MAX_WORLD_COORD
import neo.idlib.MIN_WORLD_COORD
import neo.idlib.containers.CFloat
import neo.idlib.containers.List.idSwap
import neo.idlib.math.*
import kotlin.math.abs

object Winding2D {
    /*
     ===============================================================================

     A 2D winding is an arbitrary convex 2D polygon defined by an array of points.

     ===============================================================================
     */
    const val MAX_POINTS_ON_WINDING_2D = 16
    fun GetAxialBevel(plane1: idVec3, plane2: idVec3, point: idVec2, bevel: idVec3): Boolean {
        if (FLOATSIGNBITSET(plane1.x) xor FLOATSIGNBITSET(plane2.x) != 0) {
            if (abs(plane1.x) > 0.1f && abs(plane2.x) > 0.1f) {
                bevel.x = 0.0f
                if (FLOATSIGNBITSET(plane1.y) != 0) {
                    bevel.y = -1.0f
                } else {
                    bevel.y = 1.0f
                }
                bevel.z = -(point.x * bevel.x + point.y * bevel.y)
                return true
            }
        }
        if (FLOATSIGNBITSET(plane1.y) xor FLOATSIGNBITSET(plane2.y) != 0) {
            if (abs(plane1.y) > 0.1f && abs(plane2.y) > 0.1f) {
                bevel.y = 0.0f
                if (FLOATSIGNBITSET(plane1.x) != 0) {
                    bevel.x = -1.0f
                } else {
                    bevel.x = 1.0f
                }
                bevel.z = -(point.x * bevel.x + point.y * bevel.y)
                return true
            }
        }
        return false
    }

    class idWinding2D {
        private var numPoints = 0
        private val p = idVec2.generateArray(MAX_POINTS_ON_WINDING_2D)

        fun set(winding: idWinding2D): idWinding2D {
            var i: Int
            i = 0
            while (i < winding.numPoints) {
                p[i].set(winding.p[i])
                i++
            }
            numPoints = winding.numPoints
            return this
        }

        operator fun get(index: Int): idVec2 {
            return p[index]
        }

        operator fun set(index: Int, value: idVec2): idVec2 {
            return value.also { p[index].set(it) }
        }

        fun minusAssign(index: Int, value: idVec2): idVec2 {
            return p[index].minusAssign(value)
        }

        fun plusAssign(index: Int, value: idVec2): idVec2 {
            return p[index].plusAssign(value)
        }

        fun Clear() {
            numPoints = 0
        }

        fun AddPoint(point: idVec2) {
            p[numPoints++].set(point)
        }

        fun GetNumPoints(): Int {
            return numPoints
        }

        fun Expand(d: Float) {
            var i: Int
            val edgeNormals = idVec2.generateArray(MAX_POINTS_ON_WINDING_2D)
            i = 0
            while (i < numPoints) {
                val start = p[i]
                val end = p[(i + 1) % numPoints]
                edgeNormals[i].x = start.y - end.y
                edgeNormals[i].y = end.x - start.x
                edgeNormals[i].Normalize()
                edgeNormals[i].timesAssign(d)
                i++
            }
            i = 0
            while (i < numPoints) {
                p[i].plusAssign(edgeNormals[i] + edgeNormals[(i + numPoints - 1) % numPoints])
                i++
            }
        }

        fun ExpandForAxialBox(bounds: Array<idVec2>) {
            var i: Int
            var j: Int
            var numPlanes: Int
            val v = idVec2()
            val planes: Array<idVec3> = idVec3.generateArray(MAX_POINTS_ON_WINDING_2D)
            val plane = idVec3()
            val bevel = idVec3()

            // get planes for the edges and add bevels
            numPlanes = 0.also { i = it }
            while (i < numPoints) {
                j = (i + 1) % numPoints
                if ((p[j] - p[i]).LengthSqr() < 0.01f) {
                    i++
                    continue
                }
                plane.set(Plane2DFromPoints(p[i], p[j], true))
                if (numPlanes > 0) {
                    if (GetAxialBevel(planes[numPlanes - 1], plane, p[i], bevel)) {
                        planes[numPlanes++].set(bevel)
                    }
                }
                assert(numPlanes < MAX_POINTS_ON_WINDING_2D)
                planes[numPlanes++].set(plane)
                i++
            }
            // DG: make sure planes[] isn't used uninitialized and with index -1 below
            if (numPlanes == 0) {
                return
            }
            if (GetAxialBevel(planes[numPlanes - 1], planes[0], p[0], bevel)) {
                planes[numPlanes++].set(bevel)
            }

            // expand the planes
            i = 0
            while (i < numPlanes) {
                v.x = bounds[FLOATSIGNBITSET(planes[i].x)].x
                v.y = bounds[FLOATSIGNBITSET(planes[i].y)].y
                planes[i].z += v.x * planes[i].x + v.y * planes[i].y
                i++
            }

            // get intersection points of the planes
            numPoints = 0.also { i = it }
            while (i < numPlanes) {
                if (Plane2DIntersection(planes[(i + numPlanes - 1) % numPlanes], planes[i], p[numPoints])) {
                    numPoints++
                }
                i++
            }
        }

        // splits the winding into a front and back winding, the winding itself stays unchanged
        // returns a SIDE_
        fun Split(
            plane: idVec3,
            epsilon: Float,
            front: Array<Array<idWinding2D>>,
            back: Array<Array<idWinding2D>>
        ): Int {
            val dists = FloatArray(MAX_POINTS_ON_WINDING_2D)
            val sides = IntArray(MAX_POINTS_ON_WINDING_2D)
            val counts = IntArray(3)
            var dot: Float
            var i: Int
            var j: Int
            val p1 = idVec2()
            val p2 = idVec2()
            val mid = idVec2()
            val f: idWinding2D
            val b: idWinding2D
            val maxpts: Int
            counts[2] = 0
            counts[1] = counts[2]
            counts[0] = counts[1]

            // determine sides for each point
            i = 0
            while (i < numPoints) {
                dot = plane.x * p[i].x + plane.y * p[i].y + plane.z
                dists[i] = dot
                if (dot > epsilon) {
                    sides[i] = SIDE_FRONT
                } else if (dot < -epsilon) {
                    sides[i] = SIDE_BACK
                } else {
                    sides[i] = SIDE_ON
                }
                counts[sides[i]]++
                i++
            }
            sides[i] = sides[0]
            dists[i] = dists[0]
            back[0] = Array(1) { idWinding2D() }
            front[0] = back[0] //TODO:check double pointers

            // if nothing at the front of the clipping plane
            if (0 == counts[SIDE_FRONT]) {
                back[0][0] = Copy()
                return SIDE_BACK
            }
            // if nothing at the back of the clipping plane
            if (0 == counts[SIDE_BACK]) {
                front[0][0] = Copy()
                return SIDE_FRONT
            }
            maxpts = numPoints + 4 // cant use counts[0]+2 because of fp grouping errors
            f = idWinding2D()
            front[0][0] = f
            b = idWinding2D()
            back[0][0] = b
            i = 0
            while (i < numPoints) {
                p1.set(p[i])
                if (sides[i] == SIDE_ON) {
                    f.p[f.numPoints] = p1
                    f.numPoints++
                    b.p[b.numPoints] = p1
                    b.numPoints++
                    i++
                    continue
                }
                if (sides[i] == SIDE_FRONT) {
                    f.p[f.numPoints] = p1
                    f.numPoints++
                }
                if (sides[i] == SIDE_BACK) {
                    b.p[b.numPoints] = p1
                    b.numPoints++
                }
                if (sides[i + 1] == SIDE_ON || sides[i + 1] == sides[i]) {
                    i++
                    continue
                }

                // generate a split point
                p2.set(p[(i + 1) % numPoints])

                // always calculate the split going from the same side
                // or minor epsilon issues can happen
                if (sides[i] == SIDE_FRONT) {
                    dot = dists[i] / (dists[i] - dists[i + 1])
                    j = 0
                    while (j < 2) {

                        // avoid round off error when possible
                        if (plane[j] == 1.0f) {
                            mid[j] = plane.z
                        } else if (plane[j] == -1.0f) {
                            mid[j] = -plane.z
                        } else {
                            mid[j] = p1[j] + dot * (p2[j] - p1[j])
                        }
                        j++
                    }
                } else {
                    dot = dists[i + 1] / (dists[i + 1] - dists[i])
                    j = 0
                    while (j < 2) {

                        // avoid round off error when possible
                        if (plane[j] == 1.0f) {
                            mid[j] = plane.z
                        } else if (plane[j] == -1.0f) {
                            mid[j] = -plane.z
                        } else {
                            mid[j] = p2[j] + dot * (p1[j] - p2[j])
                        }
                        j++
                    }
                }
                f.p[f.numPoints] = mid
                f.numPoints++
                b.p[b.numPoints] = mid
                b.numPoints++
                i++
            }
            return SIDE_CROSS
        }

        // cuts off the part at the back side of the plane, returns true if some part was at the front
        // if there is nothing at the front the number of points is set to zero
        fun ClipInPlace(plane: idVec3, epsilon: Float, keepOn: Boolean): Boolean {
            var i: Int
            var j: Int
            val maxpts: Int
            var newNumPoints: Int
            val sides = IntArray(MAX_POINTS_ON_WINDING_2D + 1)
            val counts = IntArray(3)
            var dot: Float
            val dists = FloatArray(MAX_POINTS_ON_WINDING_2D + 1)
            val p1 = idVec2()
            val p2 = idVec2()
            val mid = idVec2()
            val newPoints = idVec2.generateArray(MAX_POINTS_ON_WINDING_2D + 4)

            // DG: avoid all kinds of uninitialized usages below
            if (numPoints == 0) {
                return false
            }

            counts[SIDE_ON] = 0
            counts[SIDE_BACK] = counts[SIDE_ON]
            counts[SIDE_FRONT] = counts[SIDE_BACK]
            i = 0
            while (i < numPoints) {
                dot = plane.x * p[i].x + plane.y * p[i].y + plane.z
                dists[i] = dot
                if (dot > epsilon) {
                    sides[i] = SIDE_FRONT
                } else if (dot < -epsilon) {
                    sides[i] = SIDE_BACK
                } else {
                    sides[i] = SIDE_ON
                }
                counts[sides[i]]++
                i++
            }
            sides[i] = sides[0]
            dists[i] = dists[0]

            // if the winding is on the plane and we should keep it
            if (keepOn && 0 == counts[SIDE_FRONT] && 0 == counts[SIDE_BACK]) {
                return true
            }
            if (0 == counts[SIDE_FRONT]) {
                numPoints = 0
                return false
            }
            if (0 == counts[SIDE_BACK]) {
                return true
            }
            maxpts = numPoints + 4 // cant use counts[0]+2 because of fp grouping errors
            newNumPoints = 0
            i = 0
            while (i < numPoints) {
                p1.set(p[i])
                if (newNumPoints + 1 > maxpts) {
                    return true // can't split -- fall back to original
                }
                if (sides[i] == SIDE_ON) {
                    newPoints[newNumPoints].set(p1)
                    newNumPoints++
                    i++
                    continue
                }
                if (sides[i] == SIDE_FRONT) {
                    newPoints[newNumPoints].set(p1)
                    newNumPoints++
                }
                if (sides[i + 1] == SIDE_ON || sides[i + 1] == sides[i]) {
                    i++
                    continue
                }
                if (newNumPoints + 1 > maxpts) {
                    return true // can't split -- fall back to original
                }

                // generate a split point
                p2.set(p[(i + 1) % numPoints])
                dot = dists[i] / (dists[i] - dists[i + 1])
                j = 0
                while (j < 2) {

                    // avoid round off error when possible
                    if (plane[j] == 1.0f) {
                        mid[j] = plane.z
                    } else if (plane[j] == -1.0f) {
                        mid[j] = -plane.z
                    } else {
                        mid[j] = p1[j] + dot * (p2[j] - p1[j])
                    }
                    j++
                }
                newPoints[newNumPoints].set(mid)
                newNumPoints++
                i++
            }
            if (newNumPoints >= MAX_POINTS_ON_WINDING_2D) {
                return true
            }
            numPoints = newNumPoints
            //	memcpy( p, newPoints, newNumPoints * sizeof(idVec2) );
            System.arraycopy(newPoints, 0, p, 0, newNumPoints)

            return true
        }

        fun Copy(): idWinding2D {
            val w: idWinding2D
            w = idWinding2D()
            w.numPoints = numPoints
            //	memcpy( w->p, p, numPoints * sizeof( p[0] ) );
            System.arraycopy(p, 0, w.p, 0, numPoints)
            return w
        }

        fun Reverse(): idWinding2D {
            val w: idWinding2D
            var i: Int
            w = idWinding2D()
            w.numPoints = numPoints
            i = 0
            while (i < numPoints) {
                w.p[numPoints - i - 1] = p[i]
                i++
            }
            return w
        }

        fun GetArea(): Float {
            var i: Int
            val d1 = idVec2()
            val d2 = idVec2()
            var total: Float
            total = 0.0f
            i = 2
            while (i < numPoints) {
                d1.set(p[i - 1] - p[0])
                d2.set(p[i] - p[0])
                total += d1.x * d2.y - d1.y * d2.x
                i++
            }
            return total * 0.5f
        }

        fun GetCenter(): idVec2 {
            var i: Int
            val center = idVec2()
            center.Zero()
            i = 0
            while (i < numPoints) {
                center.plusAssign(p[i])
                i++
            }
            center.timesAssign(1.0f / numPoints)
            return center
        }

        fun GetRadius(center: idVec2): Float {
            var i: Int
            var radius: Float
            var r: Float
            val dir = idVec2()
            radius = 0.0f
            i = 0
            while (i < numPoints) {
                dir.set(p[i] - center)
                r = dir * dir
                if (r > radius) {
                    radius = r
                }
                i++
            }
            return idMath.Sqrt(radius)
        }

        fun GetBounds(bounds: Array<idVec2>) {
            var i: Int
            if (0 == numPoints) {
                bounds[0].y = idMath.INFINITY
                bounds[0].x = bounds[0].y
                bounds[1].y = -idMath.INFINITY
                bounds[1].x = bounds[1].y
                return
            }
            bounds[1] = p[0]
            bounds[0] = bounds[1]
            i = 1
            while (i < numPoints) {
                if (p[i].x < bounds[0].x) {
                    bounds[0].x = p[i].x
                } else if (p[i].x > bounds[1].x) {
                    bounds[1].x = p[i].x
                }
                if (p[i].y < bounds[0].y) {
                    bounds[0].y = p[i].y
                } else if (p[i].y > bounds[1].y) {
                    bounds[1].y = p[i].y
                }
                i++
            }
        }

        fun IsTiny(): Boolean {
            var i: Int
            var len: Float
            val delta = idVec2()
            var edges: Int
            edges = 0
            i = 0
            while (i < numPoints) {
                delta.set(p[(i + 1) % numPoints] - p[i])
                len = delta.Length()
                if (len > EDGE_LENGTH) {
                    if (++edges == 3) {
                        return false
                    }
                }
                i++
            }
            return true
        }

        fun IsHuge(): Boolean { // base winding for a plane is typically huge
            var i: Int
            var j: Int
            i = 0
            while (i < numPoints) {
                j = 0
                while (j < 2) {
                    if (p[i][j] <= MIN_WORLD_COORD || p[i][j] >= MAX_WORLD_COORD) {
                        return true
                    }
                    j++
                }
                i++
            }
            return false
        }

        fun Print() {
            var i: Int
            i = 0
            while (i < numPoints) {
                i++
            }
        }

        fun PlaneDistance(plane: idVec3): Float {
            var i: Int
            var d: Float
            var min: Float
            var max: Float
            min = idMath.INFINITY
            max = -min
            i = 0
            while (i < numPoints) {
                d = plane.x * p[i].x + plane.y * p[i].y + plane.z
                if (d < min) {
                    min = d
                    if (FLOATSIGNBITSET(min) and FLOATSIGNBITNOTSET(max) != 0) {
                        return 0.0f
                    }
                }
                if (d > max) {
                    max = d
                    if (FLOATSIGNBITSET(min) and FLOATSIGNBITNOTSET(max) != 0) {
                        return 0.0f
                    }
                }
                i++
            }
            if (FLOATSIGNBITNOTSET(min) != 0) {
                return min
            }
            return if (FLOATSIGNBITSET(max) != 0) {
                max
            } else 0.0f
        }

        //public	int				PlaneSide( final idVec3 plane, final float epsilon = ON_EPSILON ) ;
        fun PlaneSide(plane: idVec3, epsilon: Float = ON_EPSILON): Int {
            var front: Boolean
            var back: Boolean
            var i: Int
            var d: Float
            front = false
            back = false
            i = 0
            while (i < numPoints) {
                d = plane.x * p[i].x + plane.y * p[i].y + plane.z
                if (d < -epsilon) {
                    if (front) {
                        return SIDE_CROSS
                    }
                    back = true
                } else if (d > epsilon) {
                    if (back) {
                        return SIDE_CROSS
                    }
                    front = true
                }
                i++
            }
            if (back) {
                return SIDE_BACK
            }
            return if (front) {
                SIDE_FRONT
            } else SIDE_ON
        }

        fun PointInside(point: idVec2, epsilon: Float): Boolean {
            var i: Int
            var d: Float
            val plane = idVec3()
            i = 0
            while (i < numPoints) {
                plane.set(Plane2DFromPoints(p[i], p[(i + 1) % numPoints]))
                d = plane.x * point.x + plane.y * point.y + plane.z
                if (d > epsilon) {
                    return false
                }
                i++
            }
            return true
        }

        fun LineIntersection(start: idVec2, end: idVec2): Boolean {
            var i: Int
            var numEdges: Int
            val sides = IntArray(MAX_POINTS_ON_WINDING_2D + 1)
            val counts = IntArray(3)
            var d1: Float
            var d2: Float
            val epsilon = 0.1f
            val plane = idVec3()
            val edges: Array<idVec3> = idVec3.generateArray(2)
            counts[SIDE_ON] = 0
            counts[SIDE_BACK] = counts[SIDE_ON]
            counts[SIDE_FRONT] = counts[SIDE_BACK]
            plane.set(Plane2DFromPoints(start, end))
            i = 0
            while (i < numPoints) {
                d1 = plane.x * p[i].x + plane.y * p[i].y + plane.z
                if (d1 > epsilon) {
                    sides[i] = SIDE_FRONT
                } else if (d1 < -epsilon) {
                    sides[i] = SIDE_BACK
                } else {
                    sides[i] = SIDE_ON
                }
                counts[sides[i]]++
                i++
            }
            sides[i] = sides[0]
            if (0 == counts[SIDE_FRONT]) {
                return false
            }
            if (0 == counts[SIDE_BACK]) {
                return false
            }
            numEdges = 0
            i = 0
            while (i < numPoints) {
                if (sides[i] != sides[i + 1] && sides[i + 1] != SIDE_ON) {
                    edges[numEdges++].set(Plane2DFromPoints(p[i], p[(i + 1) % numPoints]))
                    if (numEdges >= 2) {
                        break
                    }
                }
                i++
            }
            if (numEdges < 2) {
                return false
            }
            d1 = edges[0].x * start.x + edges[0].y * start.y + edges[0].z
            d2 = edges[0].x * end.x + edges[0].y * end.y + edges[0].z
            if (FLOATSIGNBITNOTSET(d1) and FLOATSIGNBITNOTSET(d2) != 0) {
                return false
            }
            d1 = edges[1].x * start.x + edges[1].y * start.y + edges[1].z
            d2 = edges[1].x * end.x + edges[1].y * end.y + edges[1].z
            return FLOATSIGNBITNOTSET(d1) and FLOATSIGNBITNOTSET(d2) == 0
        }

        //public	boolean			RayIntersection( final idVec2 start, final idVec2 dir, float scale1, float scale2) ;
        fun RayIntersection(
            start: idVec2,
            dir: idVec2,
            scale1: CFloat,
            scale2: CFloat,
            edgeNums: IntArray?
        ): Boolean {
            var i: Int
            var numEdges: Int
            val localEdgeNums = IntArray(2)
            val sides = IntArray(MAX_POINTS_ON_WINDING_2D + 1)
            val counts = IntArray(3)
            var d1: Float
            var d2: Float
            val epsilon = 0.1f
            val plane = idVec3()
            val edges: Array<idVec3> = idVec3.generateArray(2)
            scale1._val = (0.0f)
            scale2._val = (0.0f)
            counts[SIDE_ON] = 0
            counts[SIDE_BACK] = counts[SIDE_ON]
            counts[SIDE_FRONT] = counts[SIDE_BACK]
            plane.set(Plane2DFromVecs(start, dir))
            i = 0
            while (i < numPoints) {
                d1 = plane.x * p[i].x + plane.y * p[i].y + plane.z
                if (d1 > epsilon) {
                    sides[i] = SIDE_FRONT
                } else if (d1 < -epsilon) {
                    sides[i] = SIDE_BACK
                } else {
                    sides[i] = SIDE_ON
                }
                counts[sides[i]]++
                i++
            }
            sides[i] = sides[0]
            if (0 == counts[SIDE_FRONT]) {
                return false
            }
            if (0 == counts[SIDE_BACK]) {
                return false
            }
            numEdges = 0
            i = 0
            while (i < numPoints) {
                if (sides[i] != sides[i + 1] && sides[i + 1] != SIDE_ON) {
                    localEdgeNums[numEdges] = i
                    edges[numEdges++].set(Plane2DFromPoints(p[i], p[(i + 1) % numPoints]))
                    if (numEdges >= 2) {
                        break
                    }
                }
                i++
            }
            if (numEdges < 2) {
                return false
            }
            d1 = edges[0].x * start.x + edges[0].y * start.y + edges[0].z
            d2 = -(edges[0].x * dir.x + edges[0].y * dir.y)
            if (d2 == 0.0f) {
                return false
            }
            scale1._val = (d1 / d2)
            d1 = edges[1].x * start.x + edges[1].y * start.y + edges[1].z
            d2 = -(edges[1].x * dir.x + edges[1].y * dir.y)
            if (d2 == 0.0f) {
                return false
            }
            scale2._val = (d1 / d2)
            if (abs(scale1._val) > abs(scale2._val)) {
                val scale3 = scale1._val
                scale1._val = (scale2._val)
                scale2._val = (scale3)
                idSwap(localEdgeNums, localEdgeNums, 0, 1)
            }
            if (edgeNums != null) {
                edgeNums[0] = localEdgeNums[0]
                edgeNums[1] = localEdgeNums[1]
            }
            return true
        }

        fun Plane2DFromVecs(start: idVec2, dir: idVec2, normalize: Boolean = false): idVec3 {
            val plane = idVec3()
            plane.x = -dir.y
            plane.y = dir.x
            if (normalize) {
                plane.ToVec2_Normalize()
            }
            plane.z = -(start.x * plane.x + start.y * plane.y)
            return plane
        }

        fun Plane2DIntersection(plane1: idVec3, plane2: idVec3, point: idVec2): Boolean {
            val n00: Float
            val n01: Float
            val n11: Float
            val det: Float
            val invDet: Float
            val f0: Float
            val f1: Float
            n00 = plane1.x * plane1.x + plane1.y * plane1.y
            n01 = plane1.x * plane2.x + plane1.y * plane2.y
            n11 = plane2.x * plane2.x + plane2.y * plane2.y
            det = n00 * n11 - n01 * n01
            if (abs(det) < 1e-6f) {
                return false
            }
            invDet = 1.0f / det
            f0 = (n01 * plane2.z - n11 * plane1.z) * invDet
            f1 = (n01 * plane1.z - n00 * plane2.z) * invDet
            point.x = f0 * plane1.x + f1 * plane2.x
            point.y = f0 * plane1.y + f1 * plane2.y
            return true
        }

        companion object {
            const val EDGE_LENGTH = 0.2f


            fun Plane2DFromPoints(start: idVec2, end: idVec2, normalize: Boolean = false): idVec3 {
                val plane = idVec3()
                plane.x = start.y - end.y
                plane.y = end.x - start.x
                if (normalize) {
                    plane.ToVec2_Normalize()
                }
                plane.z = -(start.x * plane.x + start.y * plane.y)
                return plane
            }
        }
    }
}
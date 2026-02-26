package neo.idlib.BV

import neo.idlib.BV.Sphere.idSphere
import neo.idlib.containers.CFloat
import neo.idlib.math.*
import neo.idlib.math.Matrix.idMat3
import neo.idlib.math.Matrix.idMatX
import java.util.*
import kotlin.math.abs

object Box {
    //                4---{4}---5
    //     +         /|        /|
    //     Z      {7} {8}   {5} |
    //     -     /    |    /    {9}
    //          7--{6}----6     |
    //          |     |   |     |
    //        {11}    0---|-{0}-1
    //          |    /    |    /       -
    //          | {3}  {10} {1}       Y
    //          |/        |/         +
    //          3---{2}---2
    //
    //            - X +
    //
    //      plane bits:
    //      0 = min x
    //      1 = max x
    //      2 = min y
    //      3 = max y
    //      4 = min z
    //      5 = max z
    /*
     static int boxVertPlanes[8] = {
     ( (1<<0) | (1<<2) | (1<<4) ),
     ( (1<<1) | (1<<2) | (1<<4) ),
     ( (1<<1) | (1<<3) | (1<<4) ),
     ( (1<<0) | (1<<3) | (1<<4) ),
     ( (1<<0) | (1<<2) | (1<<5) ),
     ( (1<<1) | (1<<2) | (1<<5) ),
     ( (1<<1) | (1<<3) | (1<<5) ),
     ( (1<<0) | (1<<3) | (1<<5) )
     };

     static int boxVertEdges[8][3] = {
     // bottom
     { 3, 0, 8 },
     { 0, 1, 9 },
     { 1, 2, 10 },
     { 2, 3, 11 },
     // top
     { 7, 4, 8 },
     { 4, 5, 9 },
     { 5, 6, 10 },
     { 6, 7, 11 }
     };

     static int boxEdgePlanes[12][2] = {
     // bottom
     { 4, 2 },
     { 4, 1 },
     { 4, 3 },
     { 4, 0 },
     // top
     { 5, 2 },
     { 5, 1 },
     { 5, 3 },
     { 5, 0 },
     // sides
     { 0, 2 },
     { 2, 1 },
     { 1, 3 },
     { 3, 0 }
     };

     static int boxEdgeVerts[12][2] = {
     // bottom
     { 0, 1 },
     { 1, 2 },
     { 2, 3 },
     { 3, 0 },
     // top
     { 4, 5 },
     { 5, 6 },
     { 6, 7 },
     { 7, 4 },
     // sides
     { 0, 4 },
     { 1, 5 },
     { 2, 6 },
     { 3, 7 }
     };
     */
    val boxPlaneBitsSilVerts: Array<IntArray> = arrayOf(
        intArrayOf(0, 0, 0, 0, 0, 0, 0),
        intArrayOf(4, 7, 4, 0, 3, 0, 0),
        intArrayOf(4, 5, 6, 2, 1, 0, 0),
        intArrayOf(0, 0, 0, 0, 0, 0, 0),
        intArrayOf(4, 4, 5, 1, 0, 0, 0),
        intArrayOf(6, 3, 7, 4, 5, 1, 0),
        intArrayOf(6, 4, 5, 6, 2, 1, 0),
        intArrayOf(0, 0, 0, 0, 0, 0, 0),
        intArrayOf(4, 6, 7, 3, 2, 0, 0),
        intArrayOf(6, 6, 7, 4, 0, 3, 2),
        intArrayOf(6, 5, 6, 7, 3, 2, 1),
        intArrayOf(0, 0, 0, 0, 0, 0, 0),
        intArrayOf(0, 0, 0, 0, 0, 0, 0),
        intArrayOf(0, 0, 0, 0, 0, 0, 0),
        intArrayOf(0, 0, 0, 0, 0, 0, 0),
        intArrayOf(0, 0, 0, 0, 0, 0, 0),
        intArrayOf(4, 0, 1, 2, 3, 0, 0),
        intArrayOf(6, 0, 1, 2, 3, 7, 4),
        intArrayOf(6, 3, 2, 6, 5, 1, 0),
        intArrayOf(0, 0, 0, 0, 0, 0, 0),
        intArrayOf(6, 1, 2, 3, 0, 4, 5),
        intArrayOf(6, 1, 2, 3, 7, 4, 5),
        intArrayOf(6, 2, 3, 0, 4, 5, 6),
        intArrayOf(0, 0, 0, 0, 0, 0, 0),
        intArrayOf(6, 0, 1, 2, 6, 7, 3),
        intArrayOf(6, 0, 1, 2, 6, 7, 4),
        intArrayOf(6, 0, 1, 5, 6, 7, 3),
        intArrayOf(0, 0, 0, 0, 0, 0, 0),
        intArrayOf(0, 0, 0, 0, 0, 0, 0),
        intArrayOf(0, 0, 0, 0, 0, 0, 0),
        intArrayOf(0, 0, 0, 0, 0, 0, 0),
        intArrayOf(0, 0, 0, 0, 0, 0, 0),
        intArrayOf(4, 7, 6, 5, 4, 0, 0),
        intArrayOf(6, 7, 6, 5, 4, 0, 3),
        intArrayOf(6, 5, 4, 7, 6, 2, 1),
        intArrayOf(0, 0, 0, 0, 0, 0, 0),
        intArrayOf(6, 4, 7, 6, 5, 1, 0),
        intArrayOf(6, 3, 7, 6, 5, 1, 0),
        intArrayOf(6, 4, 7, 6, 2, 1, 0),
        intArrayOf(0, 0, 0, 0, 0, 0, 0),
        intArrayOf(6, 6, 5, 4, 7, 3, 2),
        intArrayOf(6, 6, 5, 4, 0, 3, 2),
        intArrayOf(6, 5, 4, 7, 3, 2, 1),
        intArrayOf(0, 0, 0, 0, 0, 0, 0),
        intArrayOf(0, 0, 0, 0, 0, 0, 0),
        intArrayOf(0, 0, 0, 0, 0, 0, 0),
        intArrayOf(0, 0, 0, 0, 0, 0, 0),
        intArrayOf(0, 0, 0, 0, 0, 0, 0),
        intArrayOf(0, 0, 0, 0, 0, 0, 0),
        intArrayOf(0, 0, 0, 0, 0, 0, 0),
        intArrayOf(0, 0, 0, 0, 0, 0, 0),
        intArrayOf(0, 0, 0, 0, 0, 0, 0),
        intArrayOf(0, 0, 0, 0, 0, 0, 0),
        intArrayOf(0, 0, 0, 0, 0, 0, 0),
        intArrayOf(0, 0, 0, 0, 0, 0, 0),
        intArrayOf(0, 0, 0, 0, 0, 0, 0),
        intArrayOf(0, 0, 0, 0, 0, 0, 0),
        intArrayOf(0, 0, 0, 0, 0, 0, 0),
        intArrayOf(0, 0, 0, 0, 0, 0, 0),
        intArrayOf(0, 0, 0, 0, 0, 0, 0),
        intArrayOf(0, 0, 0, 0, 0, 0, 0),
        intArrayOf(0, 0, 0, 0, 0, 0, 0),
        intArrayOf(0, 0, 0, 0, 0, 0, 0),
        intArrayOf(0, 0, 0, 0, 0, 0, 0)
    )

    /*
     ============
     BoxPlaneClip
     ============
     */
    fun BoxPlaneClip(denom: Float, numer: Float, scale0: CFloat, scale1: CFloat): Boolean {
        return if (denom > 0.0f) {
            if (numer > denom * scale1._val) {
                return false
            }
            if (numer > denom * scale0._val) {
                scale0._val = (numer / denom)
            }
            true
        } else if (denom < 0.0f) {
            if (numer > denom * scale0._val) {
                return false
            }
            if (numer > denom * scale1._val) {
                scale1._val = (numer / denom)
            }
            true
        } else {
            numer <= 0.0f
        }
    }

    /*
     ===============================================================================

     Oriented Bounding Box

     ===============================================================================
     */
    class idBox {
        private val axis: idMat3 = idMat3()
        private val center: idVec3 = idVec3()
        private val extents: idVec3 = idVec3()

        //
        //
        constructor()
        constructor(center: idVec3, extents: idVec3, axis: idMat3) {
            this.center.set(center)
            this.extents.set(extents)
            this.axis.set(axis)
        }

        constructor(point: idVec3) {
            center.set(point)
            extents.Zero()
            axis.Identity()
        }

        constructor(bounds: idBounds) {
            center.set((bounds[0] + bounds[1]) * 0.5f)
            extents.set(bounds[1] - center)
            axis.Identity()
        }

        constructor(bounds: idBounds, origin: idVec3, axis: idMat3) {
            center.set((bounds[0] + bounds[1]) * 0.5f)
            extents.set(bounds[1] - center)
            center.set(origin + center * axis)
            this.axis.set(axis)
        }

        constructor(box: idBox) {
            center.set(box.center)
            extents.set(box.extents)
            axis.set(box.axis)
        }

        operator fun plus(t: idVec3): idBox {                // returns translated box
            return idBox(center + t, extents, axis)
        }

        fun plusAssign(t: idVec3): idBox {                    // translate the box
            center.plusAssign(t)
            return this
        }

        operator fun times(r: idMat3): idBox {                // returns rotated box
            return idBox(center * r, extents, axis * r)
        }

        fun timesAssign(r: idMat3): idBox {                    // rotate the box
            center.timesAssign(r)
            axis.timesAssign(r)
            return this
        }

        fun plus(a: idBox): idBox {
            val newBox: idBox
            newBox = idBox(this)
            newBox.AddBox(a)
            return newBox
        }

        fun plusAssign(a: idBox): idBox {
            AddBox(a)
            return this
        }

        operator fun minus(a: idBox): idBox {
            return idBox(center, extents - a.extents, axis)
        }

        fun minusAssign(a: idBox): idBox {
            extents.minusAssign(a.extents)
            return this
        }

        fun Compare(a: idBox): Boolean {                        // exact compare, no epsilon
            return center.Compare(a.center) && extents.Compare(a.extents) && axis.Compare(a.axis)
        }

        fun Compare(a: idBox, epsilon: Float): Boolean {    // compare with epsilon
            return center.Compare(a.center, epsilon) && extents.Compare(a.extents, epsilon) && axis.Compare(
                a.axis,
                epsilon
            )
        }

        //public	boolean			operator==(	final idBox &a ) ;						// exact compare, no epsilon
        //public	boolean			operator!=(	final idBox &a ) ;						// exact compare, no epsilon
        override fun hashCode(): Int {
            var hash = 7
            hash = 31 * hash + Objects.hashCode(center)
            hash = 31 * hash + Objects.hashCode(extents)
            hash = 31 * hash + Objects.hashCode(axis)
            return hash
        }

        override fun equals(obj: Any?): Boolean {
            if (obj == null) {
                return false
            }
            if (javaClass != obj.javaClass) {
                return false
            }
            val other = obj as idBox
            return Compare(other)
        }

        fun Clear() {                                    // inside out box
            center.Zero()
            extents[0] = extents.set(1, extents.set(2, -idMath.INFINITY))
            axis.Identity()
        }

        fun Zero() {                                    // single point at origin
            center.Zero()
            extents.Zero()
            axis.Identity()
        }

        // returns center of the box
        fun GetCenter(): idVec3 {
            return center
        }

        // returns extents of the box
        fun GetExtents(): idVec3 {
            return extents
        }

        // returns the axis of the box
        fun GetAxis(): idMat3 {
            return axis
        }

        fun GetVolume(): Float {                        // returns the volume of the box
            return (extents * 2.0f).LengthSqr()
        }

        fun IsCleared(): Boolean {                        // returns true if box are inside out
            return extents[0] < 0.0f
        }

        fun AddPoint(v: idVec3): Boolean {                    // add the point, returns true if the box expanded
            val axis2 = idMat3()
            val bounds1 = idBounds()
            val bounds2 = idBounds()
            if (extents[0] < 0.0f) {
                extents.Zero()
                center.set(v)
                axis.Identity()
                return true
            }

            bounds1[0, 0] = bounds1.set(1, 0, center * axis[0])
            bounds1[0, 1] = bounds1.set(1, 1, center * axis[1])
            bounds1[0, 2] = bounds1.set(1, 2, center * axis[2])
            bounds1[0].minusAssign(extents)
            bounds1[1].plusAssign(extents)

            if (!bounds1.AddPoint(idVec3(v * axis[0], v * axis[1], v * axis[2]))
            ) {
                // point is contained in the box
                return false
            }
            axis2[0] = v - center
            axis2[0].Normalize()
            axis2[1] = axis[Min3Index(axis2[0] * axis[0], axis2[0] * axis[1], axis2[0] * axis[2])]
            axis2[1] = axis2[1] - axis2[0] * (axis2[1] * axis2[0])
            axis2[1].Normalize()
            axis2[2].Cross(axis2[0], axis2[1])
            AxisProjection(axis2, bounds2)
            bounds2.AddPoint(idVec3(v * axis2[0], v * axis2[1], v * axis2[2]))

            // create new box based on the smallest bounds
            if (bounds1.GetVolume() < bounds2.GetVolume()) {
                center.set((bounds1[0] + bounds1[1]) * 0.5f)
                extents.set(bounds1[1] - center)
                center.timesAssign(axis)
            } else {
                center.set((bounds2[0] + bounds2[1]) * 0.5f)
                extents.set(bounds2[1] - center)
                center.timesAssign(axis2)
                axis.set(axis2)
            }
            return true
        }

        // add the box, returns true if the box expanded
        fun AddBox(a: idBox): Boolean {
            var i: Int
            var besti: Int
            var v: Float
            var bestv: Float
            val dir = idVec3()
            val ax = Array(4) { idMat3() }
            val bounds = Array(4) { idBounds() }
            val b = idBounds()
            if (a.extents[0] < 0.0f) {
                return false
            }
            if (extents[0] < 0.0f) {
                center.set(a.center)
                extents.set(a.extents)
                axis.set(a.axis)
                return true
            }

            // test axis of this box
            ax[0].set(axis)
            bounds[0][0, 0] = bounds[0].set(1, 0, center * ax[0][0])
            bounds[0][0, 1] = bounds[0].set(1, 1, center * ax[0][1])
            bounds[0][0, 2] = bounds[0].set(1, 2, center * ax[0][2])
            bounds[0][0].minusAssign(extents)
            bounds[0][1].plusAssign(extents)
            a.AxisProjection(ax[0], b)
            if (!bounds[0].AddBounds(b)) {
                // the other box is contained in this box
                return false
            }

            // test axis of other box
            ax[1].set(a.axis)
            bounds[1][0, 0] = bounds[1].set(1, 0, a.center * ax[1][0])
            bounds[1][0, 1] = bounds[1].set(1, 1, a.center * ax[1][1])
            bounds[1][0, 2] = bounds[1].set(1, 2, a.center * ax[1][2])
            bounds[1][0].minusAssign(a.extents)
            bounds[1][1].plusAssign(a.extents)
            AxisProjection(ax[1], b)
            if (!bounds[1].AddBounds(b)) {
                // this box is contained in the other box
                center.set(a.center)
                extents.set(a.extents)
                axis.set(a.axis)
                return true
            }

            // test axes aligned with the vector between the box centers and one of the box axis
            dir.set(a.center - center)
            dir.Normalize()
            i = 2
            while (i < 4) {
                ax[i][0] = dir
                ax[i][1] = ax[i - 2][Min3Index(dir * ax[i - 2][0], dir * ax[i - 2][1], dir * ax[i - 2][2])]
                ax[i][1] = ax[i][1] - dir * (ax[i][1] * dir)
                ax[i][1].Normalize()
                ax[i][2].Cross(dir, ax[i][1])
                AxisProjection(ax[i], bounds[i])
                a.AxisProjection(ax[i], b)
                bounds[i].AddBounds(b)
                i++
            }

            // get the bounds with the smallest volume
            bestv = idMath.INFINITY
            besti = 0
            i = 0
            while (i < 4) {
                v = bounds[i].GetVolume()
                if (v < bestv) {
                    bestv = v
                    besti = i
                }
                i++
            }

            // create a box from the smallest bounds axis pair
            center.set((bounds[besti][0] + bounds[besti][1]) * 0.5f)
            extents.set(bounds[besti][1] - center)
            center.timesAssign(ax[besti])
            axis.set(ax[besti])
            return false
        }

        fun Expand(d: Float): idBox {                    // return box expanded in all directions with the given value
            return idBox(center, extents + idVec3(d, d, d), axis)
        }

        fun ExpandSelf(d: Float): idBox {                    // expand box in all directions with the given value
            extents.plusAssign(0, d)
            extents.plusAssign(1, d)
            extents.plusAssign(2, d)
            return this
        }

        fun Translate(translation: idVec3): idBox {    // return translated box
            return idBox(center + translation, extents, axis)
        }

        fun TranslateSelf(translation: idVec3): idBox {        // translate this box
            center.plusAssign(translation)
            return this
        }

        fun Rotate(rotation: idMat3): idBox {            // return rotated box
            return idBox(center * rotation, extents, axis * rotation)
        }

        fun RotateSelf(rotation: idMat3): idBox {            // rotate this box
            center.timesAssign(rotation)
            axis.timesAssign(rotation)
            return this
        }

        //
        fun PlaneDistance(plane: idPlane): Float {
            val d1: Float
            val d2: Float
            d1 = plane.Distance(center)
            d2 = (abs(extents[0] * plane.Normal()[0])
                    + abs(extents[1] * plane.Normal()[1])
                    + abs(extents[2] * plane.Normal()[2]))
            if (d1 - d2 > 0.0f) {
                return d1 - d2
            }
            return if (d1 + d2 < 0.0f) {
                d1 + d2
            } else 0.0f
        }


        fun PlaneSide(plane: idPlane, epsilon: Float = ON_EPSILON): Int {
            val d1: Float
            val d2: Float
            d1 = plane.Distance(center)
            d2 = (abs(extents[0] * plane.Normal()[0])
                    + abs(extents[1] * plane.Normal()[1])
                    + abs(extents[2] * plane.Normal()[2]))
            if (d1 - d2 > epsilon) {
                return PLANESIDE_FRONT
            }
            return if (d1 + d2 < -epsilon) {
                PLANESIDE_BACK
            } else PLANESIDE_CROSS
        }

        fun ContainsPoint(p: idVec3): Boolean {            // includes touching
            val lp = p - center
            return !(abs(lp * axis[0]) > extents[0]
                    || abs(lp * axis[1]) > extents[1]
                    || abs(lp * axis[2]) > extents[2])
        }

        fun IntersectsBox(a: idBox): Boolean {            // includes touching
            val dir = idVec3() // vector between centers
            val c = Array(3) { FloatArray(3) } // matrix c = axis.Transpose() * a.axis
            val ac = Array(3) { FloatArray(3) } // absolute values of c
            val axisdir = FloatArray(3) // axis[i] * dir
            var d: Float
            var e0: Float
            var e1: Float // distance between centers and projected extents
            dir.set(a.center - center)

            // axis C0 + t * A0
            c[0][0] = axis[0] * a.axis[0]
            c[0][1] = axis[0] * a.axis[1]
            c[0][2] = axis[0] * a.axis[2]
            axisdir[0] = axis[0] * dir
            ac[0][0] = abs(c[0][0])
            ac[0][1] = abs(c[0][1])
            ac[0][2] = abs(c[0][2])
            d = abs(axisdir[0])
            e0 = extents[0]
            e1 = a.extents[0] * ac[0][0] + a.extents[1] * ac[0][1] + a.extents[2] * ac[0][2]
            if (d > e0 + e1) {
                return false
            }

            // axis C0 + t * A1
            c[1][0] = axis[1] * a.axis[0]
            c[1][1] = axis[1] * a.axis[1]
            c[1][2] = axis[1] * a.axis[2]
            axisdir[1] = axis[1] * dir
            ac[1][0] = abs(c[1][0])
            ac[1][1] = abs(c[1][1])
            ac[1][2] = abs(c[1][2])
            d = abs(axisdir[1])
            e0 = extents[1]
            e1 = a.extents[0] * ac[1][0] + a.extents[1] * ac[1][1] + a.extents[2] * ac[1][2]
            if (d > e0 + e1) {
                return false
            }

            // axis C0 + t * A2
            c[2][0] = axis[2] * a.axis[0]
            c[2][1] = axis[2] * a.axis[1]
            c[2][2] = axis[2] * a.axis[2]
            axisdir[2] = axis[2] * dir
            ac[2][0] = abs(c[2][0])
            ac[2][1] = abs(c[2][1])
            ac[2][2] = abs(c[2][2])
            d = abs(axisdir[2])
            e0 = extents[2]
            e1 = a.extents[0] * ac[2][0] + a.extents[1] * ac[2][1] + a.extents[2] * ac[2][2]
            if (d > e0 + e1) {
                return false
            }

            // axis C0 + t * B0
            d = abs(a.axis[0] * dir)
            e0 = extents[0] * ac[0][0] + extents[1] * ac[1][0] + extents[2] * ac[2][0]
            e1 = a.extents[0]
            if (d > e0 + e1) {
                return false
            }

            // axis C0 + t * B1
            d = abs(a.axis[1] * dir)
            e0 = extents[0] * ac[0][1] + extents[1] * ac[1][1] + extents[2] * ac[2][1]
            e1 = a.extents[1]
            if (d > e0 + e1) {
                return false
            }

            // axis C0 + t * B2
            d = abs(a.axis[2] * dir)
            e0 = extents[0] * ac[0][2] + extents[1] * ac[1][2] + extents[2] * ac[2][2]
            e1 = a.extents[2]
            if (d > e0 + e1) {
                return false
            }

            // axis C0 + t * A0xB0
            d = abs(axisdir[2] * c[1][0] - axisdir[1] * c[2][0])
            e0 = extents[1] * ac[2][0] + extents[2] * ac[1][0]
            e1 = a.extents[1] * ac[0][2] + a.extents[2] * ac[0][1]
            if (d > e0 + e1) {
                return false
            }

            // axis C0 + t * A0xB1
            d = abs(axisdir[2] * c[1][1] - axisdir[1] * c[2][1])
            e0 = extents[1] * ac[2][1] + extents[2] * ac[1][1]
            e1 = a.extents[0] * ac[0][2] + a.extents[2] * ac[0][0]
            if (d > e0 + e1) {
                return false
            }

            // axis C0 + t * A0xB2
            d = abs(axisdir[2] * c[1][2] - axisdir[1] * c[2][2])
            e0 = extents[1] * ac[2][2] + extents[2] * ac[1][2]
            e1 = a.extents[0] * ac[0][1] + a.extents[1] * ac[0][0]
            if (d > e0 + e1) {
                return false
            }

            // axis C0 + t * A1xB0
            d = abs(axisdir[0] * c[2][0] - axisdir[2] * c[0][0])
            e0 = extents[0] * ac[2][0] + extents[2] * ac[0][0]
            e1 = a.extents[1] * ac[1][2] + a.extents[2] * ac[1][1]
            if (d > e0 + e1) {
                return false
            }

            // axis C0 + t * A1xB1
            d = abs(axisdir[0] * c[2][1] - axisdir[2] * c[0][1])
            e0 = extents[0] * ac[2][1] + extents[2] * ac[0][1]
            e1 = a.extents[0] * ac[1][2] + a.extents[2] * ac[1][0]
            if (d > e0 + e1) {
                return false
            }

            // axis C0 + t * A1xB2
            d = abs(axisdir[0] * c[2][2] - axisdir[2] * c[0][2])
            e0 = extents[0] * ac[2][2] + extents[2] * ac[0][2]
            e1 = a.extents[0] * ac[1][1] + a.extents[1] * ac[1][0]
            if (d > e0 + e1) {
                return false
            }

            // axis C0 + t * A2xB0
            d = abs(axisdir[1] * c[0][0] - axisdir[0] * c[1][0])
            e0 = extents[0] * ac[1][0] + extents[1] * ac[0][0]
            e1 = a.extents[1] * ac[2][2] + a.extents[2] * ac[2][1]
            if (d > e0 + e1) {
                return false
            }

            // axis C0 + t * A2xB1
            d = abs(axisdir[1] * c[0][1] - axisdir[0] * c[1][1])
            e0 = extents[0] * ac[1][1] + extents[1] * ac[0][1]
            e1 = a.extents[0] * ac[2][2] + a.extents[2] * ac[2][0]
            if (d > e0 + e1) {
                return false
            }

            // axis C0 + t * A2xB2
            d = abs(axisdir[1] * c[0][2] - axisdir[0] * c[1][2])
            e0 = extents[0] * ac[1][2] + extents[1] * ac[0][2]
            e1 = a.extents[0] * ac[2][1] + a.extents[1] * ac[2][0]
            return d <= e0 + e1
        }

        /*
         ============
         idBox::LineIntersection

         Returns true if the line intersects the box between the start and end point.
         ============
         */
        fun LineIntersection(start: idVec3, end: idVec3): Boolean {
            val ld = FloatArray(3)
            val lineDir = (end - start) * 0.5f
            val lineCenter = start + lineDir
            val dir = lineCenter - center

            ld[0] = abs(lineDir * axis[0])
            if (abs(dir * axis[0]) > extents[0] + ld[0]) {
                return false
            }
            ld[1] = abs(lineDir * axis[1])
            if (abs(dir * axis[1]) > extents[1] + ld[1]) {
                return false
            }
            ld[2] = abs(lineDir * axis[2])
            if (abs(dir * axis[2]) > extents[2] + ld[2]) {
                return false
            }

            val cross = lineDir.Cross(dir)

            if (abs(cross * axis[0]) > extents[1] * ld[2] + extents[2] * ld[1]) {
                return false
            }
            if (abs(cross * axis[1]) > extents[0] * ld[2] + extents[2] * ld[0]) {
                return false
            }

            if (abs(cross * axis[2]) > extents[0] * ld[1] + extents[1] * ld[0]) {
                return false
            }

            return true
        }

        /*
         ============
         idBox::RayIntersection

         Returns true if the ray intersects the box.
         The ray can intersect the box in both directions from the start point.
         If start is inside the box then scale1 < 0 and scale2 > 0.
         ============
         */
        // intersection points are (start + dir * scale1) and (start + dir * scale2)
        fun RayIntersection(start: idVec3, dir: idVec3, scale1: CFloat, scale2: CFloat): Boolean {
            val localStart = idVec3()
            val localDir = idVec3()

            localStart.set((start - center) * axis.Transpose())
            localDir.set(dir * axis.Transpose())

            scale1._val = (-idMath.INFINITY)
            scale2._val = (idMath.INFINITY)

            return (BoxPlaneClip(localDir.x, -localStart.x - extents[0], scale1, scale2)
                    && BoxPlaneClip(-localDir.x, localStart.x - extents[0], scale1, scale2)
                    && BoxPlaneClip(localDir.y, -localStart.y - extents[1], scale1, scale2)
                    && BoxPlaneClip(-localDir.y, localStart.y - extents[1], scale1, scale2)
                    && BoxPlaneClip(localDir.z, -localStart.z - extents[2], scale1, scale2)
                    && BoxPlaneClip(-localDir.z, localStart.z - extents[2], scale1, scale2))
        }

        /*
         ============
         idBox::FromPoints

         Tight box for a collection of points.
         ============
         */
        // tight box for a collection of points
        fun FromPoints(points: Array<idVec3>, numPoints: Int) {
            var i: Int
            val invNumPoints: Float
            var sumXX: Float
            var sumXY: Float
            var sumXZ: Float
            var sumYY: Float
            var sumYZ: Float
            var sumZZ: Float
            val dir = idVec3()
            val bounds = idBounds()
            val eigenVectors = idMatX()
            val eigenValues = idVecX()

            // compute mean of points
            center.set(points[0])
            i = 1
            while (i < numPoints) {
                center.plusAssign(points[i])
                i++
            }
            invNumPoints = 1.0f / numPoints
            center.timesAssign(invNumPoints)

            // compute covariances of points
            sumXX = 0.0f
            sumXY = 0.0f
            sumXZ = 0.0f
            sumYY = 0.0f
            sumYZ = 0.0f
            sumZZ = 0.0f
            i = 0
            while (i < numPoints) {
                dir.set(points[i] - center)
                sumXX += dir.x * dir.x
                sumXY += dir.x * dir.y
                sumXZ += dir.x * dir.z
                sumYY += dir.y * dir.y
                sumYZ += dir.y * dir.z
                sumZZ += dir.z * dir.z
                i++
            }
            sumXX *= invNumPoints
            sumXY *= invNumPoints
            sumXZ *= invNumPoints
            sumYY *= invNumPoints
            sumYZ *= invNumPoints
            sumZZ *= invNumPoints

            // compute eigenvectors for covariance matrix
            eigenValues.SetData(3, idVecX.VECX_ALLOCA(3))
            eigenVectors.SetData(3, 3, idMatX.MATX_ALLOCA(3 * 3))
            eigenVectors[0, 0] = sumXX
            eigenVectors[0, 1] = sumXY
            eigenVectors[0, 2] = sumXZ
            eigenVectors[1, 0] = sumXY
            eigenVectors[1, 1] = sumYY
            eigenVectors[1, 2] = sumYZ
            eigenVectors[2, 0] = sumXZ
            eigenVectors[2, 1] = sumYZ
            eigenVectors[2, 2] = sumZZ
            eigenVectors.Eigen_SolveSymmetric(eigenValues)
            eigenVectors.Eigen_SortIncreasing(eigenValues)
            axis.set(0, 0, eigenVectors[0][0])
            axis.set(0, 1, eigenVectors[0][1])
            axis.set(0, 2, eigenVectors[0][2])
            axis.set(1, 0, eigenVectors[1][0])
            axis.set(1, 1, eigenVectors[1][1])
            axis.set(1, 2, eigenVectors[1][2])
            axis.set(2, 0, eigenVectors[2][0])
            axis.set(2, 1, eigenVectors[2][1])
            axis.set(2, 2, eigenVectors[2][2])
            extents[0] = eigenValues.p[0]
            extents[1] = eigenValues.p[0]
            extents[2] = eigenValues.p[0]

            // refine by calculating the bounds of the points projected onto the axis and adjusting the center and extents
            bounds.Clear()
            i = 0
            while (i < numPoints) {
                bounds.AddPoint(
                    idVec3(points[i] * axis[0], points[i] * axis[1], points[i] * axis[2])
                )
                i++
            }
            center.set((bounds[0] + bounds[1]) * 0.5f)
            extents.set(bounds[1] - center)
            center.timesAssign(axis)
        }

        fun ToPoints(points: Array<idVec3>) {
            val ax = idMat3()
            val temp: Array<idVec3> = idVec3.generateArray(4)
            ax[0] = axis[0] * extents[0]
            ax[1] = axis[1] * extents[1]
            ax[2] = axis[2] * extents[2]
            temp[0].set(center - ax[0])
            temp[1].set(center + ax[0])
            temp[2].set(ax[1] - ax[2])
            temp[3].set(ax[1] + ax[2])
            points[0].set(temp[0] - temp[3])
            points[1].set(temp[1] - temp[3])
            points[2].set(temp[1] + temp[2])
            points[3].set(temp[0] + temp[2])
            points[4].set(temp[0] - temp[2])
            points[5].set(temp[1] - temp[2])
            points[6].set(temp[1] + temp[3])
            points[7].set(temp[0] + temp[3])
        }

        fun ToSphere(): idSphere {
            return idSphere(center, extents.Length())
        }

        // calculates the projection of this box onto the given axis
        fun AxisProjection(dir: idVec3, min: CFloat, max: CFloat) {
            val d1 = dir * center
            val d2 = abs(extents[0] * (dir * axis[0])) +
                    abs(extents[1] * (dir * axis[1])) +
                    abs(extents[2] * (dir * axis[2]))
            min._val = (d1 - d2)
            max._val = (d1 + d2)
        }

        fun AxisProjection(ax: idMat3, bounds: idBounds) {
            for (i in 0..2) {
                val d1 = ax[i] * center
                val d2 = abs(extents[0] * (ax[i] * axis[0])) +
                        abs(extents[1] * (ax[i] * axis[1])) +
                        abs(extents[2] * (ax[i] * axis[2]))
                bounds[0, i] = d1 - d2
                bounds[1, i] = d1 + d2
            }
        }

        // calculates the silhouette of the box
        fun GetProjectionSilhouetteVerts(projectionOrigin: idVec3, silVerts: Array<idVec3>): Int {
            var f: Float
            var i: Int
            var planeBits: Int
            val index: IntArray?
            val points: Array<idVec3> = idVec3.generateArray(8)
            val dir1 = idVec3()
            val dir2 = idVec3()
            ToPoints(points)

            dir1.set(points[0] - projectionOrigin)
            dir2.set(points[6] - projectionOrigin)

            f = dir1 * axis[0]
            planeBits = FLOATSIGNBITNOTSET(f)
            f = dir2 * axis[0]
            planeBits = planeBits or (FLOATSIGNBITSET(f) shl 1)
            f = dir1 * axis[1]
            planeBits = planeBits or (FLOATSIGNBITNOTSET(f) shl 2)
            f = dir2 * axis[1]
            planeBits = planeBits or (FLOATSIGNBITSET(f) shl 3)
            f = dir1 * axis[2]
            planeBits = planeBits or (FLOATSIGNBITNOTSET(f) shl 4)
            f = dir2 * axis[2]
            planeBits = planeBits or (FLOATSIGNBITSET(f) shl 5)
            index = boxPlaneBitsSilVerts[planeBits]
            i = 0
            while (i < index[0]) {
                silVerts[i].set(points[index[i + 1]])
                i++
            }
            return index[0]
        }

        fun GetParallelProjectionSilhouetteVerts(projectionDir: idVec3, silVerts: Array<idVec3>): Int {
            var f: Float
            var i: Int
            var planeBits: Int
            val index: IntArray?
            val points: Array<idVec3> = idVec3.generateArray(8)

            ToPoints(points)

            planeBits = 0
            f = projectionDir * axis[0]
            if (FLOATNOTZERO(f)) {
                planeBits = 1 shl FLOATSIGNBITSET(f)
            }
            f = projectionDir * axis[1]
            if (FLOATNOTZERO(f)) {
                planeBits = planeBits or (4 shl FLOATSIGNBITSET(f))
            }
            f = projectionDir * axis[2]
            if (FLOATNOTZERO(f)) {
                planeBits = planeBits or (16 shl FLOATSIGNBITSET(f))
            }
            index = boxPlaneBitsSilVerts[planeBits]
            i = 0
            while (i < index[0]) {
                silVerts[i].set(points[index[i + 1]])
                i++
            }
            return index[0]
        }
    }
}
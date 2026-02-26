package neo.idlib.geometry

import neo.idlib.geometry.Surface.idSurface
import neo.idlib.math.*
import neo.idlib.math.Matrix.idMat3

class Surface_SweptSpline {
    /*
     ===============================================================================

     Swept Spline surface.

     ===============================================================================
     */
    internal inner class idSurface_SweptSpline : idSurface() {
        protected var spline: idCurve_Spline<idVec4>? = null
        protected var sweptSpline: idCurve_Spline<idVec4>? = null
        fun SetSpline(spline: idCurve_Spline<idVec4>) {
            this.spline = spline
        }

        fun SetSweptSpline(sweptSpline: idCurve_Spline<idVec4>) {
            this.sweptSpline = sweptSpline
        }

        /*
         ====================
         idSurface_SweptSpline::SetSweptCircle

         Sets the swept spline to a NURBS circle.
         ====================
         */
        fun SetSweptCircle(radius: Float) {
            val nurbs = idCurve_NURBS(idVec4::class.java)
            nurbs.Clear()
            nurbs.AddValue(0.0f, idVec4(radius, radius, 0.0f, 0.0f))
            nurbs.AddValue(100.0f, idVec4(-radius, radius, 0.0f, 0.25f))
            nurbs.AddValue(200.0f, idVec4(-radius, -radius, 0.0f, 0.50f))
            nurbs.AddValue(300.0f, idVec4(radius, -radius, 0.0f, 0.75f))
            nurbs.SetBoundaryType(idCurve_Spline.BT_CLOSED)
            nurbs.SetCloseTime(100.0f)
            sweptSpline = nurbs
        }

        /*
         ====================
         idSurface_SweptSpline::Tessellate

         tesselate the surface
         ====================
         */
        fun Tessellate(splineSubdivisions: Int, sweptSplineSubdivisions: Int) {
            var i: Int
            var j: Int
            var offset: Int
            val baseOffset: Int
            val splineDiv: Int
            val sweptSplineDiv: Int
            var i0: Int
            var i1: Int
            var j0: Int
            var j1: Int
            var totalTime: Float
            var t: Float
            val splinePos: idVec4 = idVec4()
            val splineD1: idVec4 = idVec4()
            val splineMat = idMat3()
            if (null == spline || null == sweptSpline) {
                super.Clear()
                return
            }
            verts.SetNum(splineSubdivisions * sweptSplineSubdivisions, false)

            // calculate the points and first derivatives for the swept spline
            totalTime =
                sweptSpline!!.GetTime(sweptSpline!!.GetNumValues() - 1) - sweptSpline!!.GetTime(0) + sweptSpline!!.GetCloseTime()
            sweptSplineDiv =
                if (sweptSpline!!.GetBoundaryType() == idCurve_Spline.BT_CLOSED) sweptSplineSubdivisions else sweptSplineSubdivisions - 1
            baseOffset = (splineSubdivisions - 1) * sweptSplineSubdivisions
            i = 0
            while (i < sweptSplineSubdivisions) {
                t = totalTime * i / sweptSplineDiv
                splinePos.set(sweptSpline!!.GetCurrentValue(t))
                splineD1.set(sweptSpline!!.GetCurrentFirstDerivative(t))
                verts[baseOffset + i].xyz.set(splinePos.ToVec3())
                verts[baseOffset + i].st[0] = splinePos.w
                verts[baseOffset + i].tangents[0] = splineD1.ToVec3()
                i++
            }

            // sweep the spline
            totalTime = spline!!.GetTime(spline!!.GetNumValues() - 1) - spline!!.GetTime(0) + spline!!.GetCloseTime()
            splineDiv =
                if (spline!!.GetBoundaryType() == idCurve_Spline.BT_CLOSED) splineSubdivisions else splineSubdivisions - 1
            splineMat.Identity()
            i = 0
            while (i < splineSubdivisions) {
                t = totalTime * i / splineDiv
                splinePos.set(spline!!.GetCurrentValue(t))
                splineD1.set(spline!!.GetCurrentFirstDerivative(t))
                GetFrame(splineMat, splineD1.ToVec3(), splineMat)
                offset = i * sweptSplineSubdivisions
                j = 0
                while (j < sweptSplineSubdivisions) {
                    val v = verts[offset + j]
                    v.xyz.set(splinePos.ToVec3() + verts[baseOffset + j].xyz * splineMat)
                    v.st[0] = verts[baseOffset + j].st[0]
                    v.st[1] = splinePos.w
                    v.tangents[0] = verts[baseOffset + j].tangents[0] * splineMat
                    v.tangents[1] = splineD1.ToVec3()
                    v.normal.set(v.tangents[1].Cross(v.tangents[0]))
                    v.normal.Normalize()
                    v.color[3] = 0
                    v.color[2] = v.color[3]
                    v.color[1] = v.color[2]
                    v.color[0] = v.color[1]
                    j++
                }
                i++
            }
            indexes.SetNum(splineDiv * sweptSplineDiv * 2 * 3, false)

            // create indexes for the triangles
            offset = 0.also { i = it }
            while (i < splineDiv) {
                i0 = (i + 0) * sweptSplineSubdivisions
                i1 = (i + 1) % splineSubdivisions * sweptSplineSubdivisions
                j = 0
                while (j < sweptSplineDiv) {
                    j0 = j + 0
                    j1 = (j + 1) % sweptSplineSubdivisions
                    indexes[offset++] = i0 + j0
                    indexes[offset++] = i0 + j1
                    indexes[offset++] = i1 + j1
                    indexes[offset++] = i1 + j1
                    indexes[offset++] = i1 + j0
                    indexes[offset++] = i0 + j0
                    j++
                }
                i++
            }
            GenerateEdgeIndexes()
        }

        override fun Clear() {
            super.Clear()
            spline = null
            spline = null
            sweptSpline = null
            sweptSpline = null
        }

        protected fun GetFrame(previousFrame: idMat3, dir: idVec3, newFrame: idMat3) {
            val wx: Float
            val wy: Float
            val wz: Float
            val xx: Float
            val yy: Float
            val yz: Float
            val xy: Float
            val xz: Float
            val zz: Float
            val x2: Float
            val y2: Float
            val z2: Float
            val a: Float
            val c: Float
            val s: Float
            val x: Float
            val y: Float
            val z: Float
            val d = idVec3()
            val v = idVec3()
            val axis = idMat3()
            d.set(dir)
            d.Normalize()
            v.set(d.Cross(previousFrame[2]))
            v.Normalize()
            a = idMath.ACos(previousFrame[2] * d) * 0.5f
            c = idMath.Cos(a)
            s = idMath.Sqrt(1.0f - c * c)
            x = v[0] * s
            y = v[1] * s
            z = v[2] * s
            x2 = x + x
            y2 = y + y
            z2 = z + z
            xx = x * x2
            xy = x * y2
            xz = x * z2
            yy = y * y2
            yz = y * z2
            zz = z * z2
            wx = c * x2
            wy = c * y2
            wz = c * z2

            axis.set(0, 0, 1.0f - (yy + zz))
            axis.set(0, 1, xy - wz)
            axis.set(0, 2, xz + wy)
            axis.set(1, 0, xy + wz)
            axis.set(1, 1, 1.0f - (xx + zz))
            axis.set(1, 2, yz - wx)
            axis.set(2, 0, xz - wy)
            axis.set(2, 1, yz + wx)
            axis.set(2, 2, 1.0f - (xx + yy))

            newFrame.set(previousFrame * axis)

            newFrame[2].set(dir)
            newFrame[2].Normalize() //TODO:check if this normalizes back ref
            newFrame[1].set(newFrame[1].Cross(newFrame[2], newFrame[0]))
            newFrame[1].Normalize()
            newFrame[0].set(newFrame[0].Cross(newFrame[1], newFrame[2]))
            newFrame[0].Normalize()
        }
    }
}
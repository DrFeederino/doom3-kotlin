package neo.Tools.Compilers.AAS

import neo.idlib.BV.idBounds
import neo.idlib.geometry.Winding.idWinding
import neo.idlib.math.ON_EPSILON
import neo.idlib.math.idPlane
import neo.idlib.math.idVec3
import neo.idlib.math.vec3_origin

const val LEDGE_EPSILON = 0.1f

//===============================================================
//
//	idLedge
//
//===============================================================
internal class idLedge {
    val end: idVec3 = idVec3()
    var node: idBrushBSPNode = idBrushBSPNode()
    var numExpandedPlanes = 0
    var numPlanes = 0
    var numSplitPlanes = 0
    val planes: Array<idPlane> = idPlane.generateArray(8)
    val start: idVec3 = idVec3()

    constructor()
    constructor(v1: idVec3, v2: idVec3, gravityDir: idVec3, n: idBrushBSPNode) {
        start.set(v1)
        end.set(v2)
        node = n
        numPlanes = 4
        planes[0].SetNormal(v1.minus(v2).Cross(gravityDir))
        planes[0].Normalize()
        planes[0].FitThroughPoint(v1)
        planes[1].SetNormal(v1.minus(v2).Cross(planes[0].Normal()))
        planes[1].Normalize()
        planes[1].FitThroughPoint(v1)
        planes[2].SetNormal(v1.minus(v2))
        planes[2].Normalize()
        planes[2].FitThroughPoint(v1)
        planes[3].SetNormal(v2.minus(v1))
        planes[3].Normalize()
        planes[3].FitThroughPoint(v2)
    }

    fun AddPoint(v: idVec3) {
        if (planes[2].Distance(v) > 0.0f) {
            start.set(v)
            planes[2].FitThroughPoint(start)
        }
        if (planes[3].Distance(v) > 0.0f) {
            end.set(v)
            planes[3].FitThroughPoint(end)
        }
    }

    /*
     ============
     idLedge::CreateBevels

     NOTE: this assumes the gravity is vertical
     ============
     */
    fun CreateBevels(gravityDir: idVec3) {
        val i: Int
        var j: Int
        val bounds = idBounds()
        val size = idVec3()
        val normal = idVec3()
        bounds.Clear()
        bounds.AddPoint(start)
        bounds.AddPoint(end)
        size.set(bounds[1].minus(bounds[0]))

        // plane through ledge
        planes[0].SetNormal(start.minus(end).Cross(gravityDir))
        planes[0].Normalize()
        planes[0].FitThroughPoint(start)
        // axial bevels at start and end point
        i = if (size[1] > size[0]) 1 else 0
        normal.set(vec3_origin)
        normal[i] = 1.0f
        j = if (end[i] > start[i]) 1 else 0
        planes[1 + j].SetNormal(normal)
        planes[1 +  /*!j*/(1 xor j)].SetNormal(-normal)
        planes[1].FitThroughPoint(start)
        planes[2].FitThroughPoint(end)
        numExpandedPlanes = 3
        // if additional bevels are required
        if (Math.abs(size[1 xor i]) > 0.01) {
            normal.set(vec3_origin)
            normal[1 xor i] = 1.0f
            j = if (end[1 xor i] > start[1 xor i]) 1 else 0
            planes[3 + j].SetNormal(normal)
            planes[3 + (1 xor j)].SetNormal(-normal)
            planes[3].FitThroughPoint(start)
            planes[4].FitThroughPoint(end)
            numExpandedPlanes = 5
        }
        // opposite of first
        planes[numExpandedPlanes + 0] = planes[0].unaryMinus()
        // number of planes used for splitting
        numSplitPlanes = numExpandedPlanes + 1
        // top plane
        planes[numSplitPlanes + 0].SetNormal(start.minus(end).Cross(planes[0].Normal()))
        planes[numSplitPlanes + 0].Normalize()
        planes[numSplitPlanes + 0].FitThroughPoint(start)
        // bottom plane
        planes[numSplitPlanes + 1] = planes[numSplitPlanes + 0].unaryMinus()
        // total number of planes
        numPlanes = numSplitPlanes + 2
    }

    fun Expand(bounds: idBounds, maxStepHeight: Float) {
        var i: Int
        var j: Int
        val v = idVec3()
        i = 0
        while (i < numExpandedPlanes) {
            j = 0
            while (j < 3) {
                if (planes[i].Normal()[j] > 0.0f) {
                    v[j] = bounds[0, j]
                } else {
                    v[j] = bounds[1, j]
                }
                j++
            }
            planes[i].SetDist(planes[i].Dist() + (-v.times(planes[i].Normal())))
            i++
        }
        planes[numSplitPlanes + 0].SetDist(planes[numSplitPlanes + 0].Dist() + maxStepHeight)
        planes[numSplitPlanes + 1].SetDist(planes[numSplitPlanes + 1].Dist() + 1.0f)
    }

    fun ChopWinding(winding: idWinding): idWinding? {
        var i: Int
        var w: idWinding?
        w = winding.Copy()
        i = 0
        while (i < numPlanes && w != null) {
            w = w.Clip(planes[i].unaryMinus(), ON_EPSILON, true)
            i++
        }
        return w
    }

    fun PointBetweenBounds(v: idVec3): Boolean {
        return planes[2].Distance(v) < LEDGE_EPSILON && planes[3]
            .Distance(v) < LEDGE_EPSILON
    }
}

package neo.idlib.geometry

import neo.idlib.BV.idBounds
import neo.idlib.containers.List.idSwap
import neo.idlib.geometry.DrawVert.idDrawVert
import neo.idlib.geometry.Surface.idSurface
import neo.idlib.geometry.Winding.idFixedWinding
import neo.idlib.math.*
import kotlin.math.abs

object Surface_Polytope {
    const val POLYTOPE_VERTEX_EPSILON = 0.1f

    /*
     ===============================================================================

     Polytope surface.

     NOTE: vertexes are not duplicated for texture coordinates.

     ===============================================================================
     */
    internal class idSurface_Polytope : idSurface() {
        fun FromPlanes(planes: Array<idPlane>, numPlanes: Int) {
            var i: Int
            var j: Int
            var k: Int
            val windingVerts: IntArray
            val w = idFixedWinding()
            val newVert = idDrawVert()
            windingVerts = IntArray(Winding.MAX_POINTS_ON_WINDING) //	memset( &newVert, 0, sizeof( newVert ) );
            i = 0
            while (i < numPlanes) {
                w.BaseForPlane(planes[i])
                j = 0
                while (j < numPlanes) {
                    if (j == i) {
                        j++
                        continue
                    }
                    if (!w.ClipInPlace(planes[j].unaryMinus(), ON_EPSILON, true)) {
                        break
                    }
                    j++
                }
                if (0 == w.GetNumPoints()) {
                    i++
                    continue
                }
                j = 0
                while (j < w.GetNumPoints()) {
                    k = 0
                    while (k < verts.Num()) {
                        if (verts[k].xyz.Compare(w[j].ToVec3(), POLYTOPE_VERTEX_EPSILON)) {
                            break
                        }
                        k++
                    }
                    if (k >= verts.Num()) {
                        newVert.xyz.set(w[j].ToVec3())
                        k = verts.Append(newVert)
                    }
                    windingVerts[j] = k
                    j++
                }
                j = 2
                while (j < w.GetNumPoints()) {
                    indexes.Append(windingVerts[0])
                    indexes.Append(windingVerts[j - 1])
                    indexes.Append(windingVerts[j])
                    j++
                }
                i++
            }
            GenerateEdgeIndexes()
        }

        fun SetupTetrahedron(bounds: idBounds) {
            val center = idVec3()
            val scale = idVec3()
            val c1: Float
            val c2: Float
            val c3: Float

            c1 = 0.4714045207f
            c2 = 0.8164965809f
            c3 = -0.3333333333f

            center.set(bounds.GetCenter())
            scale.set(bounds[1] - center)

            verts.SetNum(4)
            verts[0].xyz.set(center + idVec3(0.0f, 0.0f, scale.z))
            verts[1].xyz.set(center + idVec3(2.0f * c1 * scale.x, 0.0f, c3 * scale.z))
            verts[2].xyz.set(center + idVec3(-c1 * scale.x, c2 * scale.y, c3 * scale.z))
            verts[3].xyz.set(center + idVec3(-c1 * scale.x, -c2 * scale.y, c3 * scale.z))

            indexes.SetNum(4 * 3)
            indexes[0 * 3 + 0] = 0
            indexes[0 * 3 + 1] = 1
            indexes[0 * 3 + 2] = 2
            indexes[1 * 3 + 0] = 0
            indexes[1 * 3 + 1] = 2
            indexes[1 * 3 + 2] = 3
            indexes[2 * 3 + 0] = 0
            indexes[2 * 3 + 1] = 3
            indexes[2 * 3 + 2] = 1
            indexes[3 * 3 + 0] = 1
            indexes[3 * 3 + 1] = 3
            indexes[3 * 3 + 2] = 2

            GenerateEdgeIndexes()
        }

        fun SetupHexahedron(bounds: idBounds) {
            val center = idVec3()
            val scale = idVec3()
            center.set(bounds.GetCenter())
            scale.set(bounds[1] - center)

            verts.SetNum(8)
            verts[0].xyz.set(center + idVec3(-scale.x, -scale.y, -scale.z))
            verts[1].xyz.set(center + idVec3(scale.x, -scale.y, -scale.z))
            verts[2].xyz.set(center + idVec3(scale.x, scale.y, -scale.z))
            verts[3].xyz.set(center + idVec3(-scale.x, scale.y, -scale.z))
            verts[4].xyz.set(center + idVec3(-scale.x, -scale.y, scale.z))
            verts[5].xyz.set(center + idVec3(scale.x, -scale.y, scale.z))
            verts[6].xyz.set(center + idVec3(scale.x, scale.y, scale.z))
            verts[7].xyz.set(center + idVec3(-scale.x, scale.y, scale.z))

            indexes.SetNum(12 * 3)
            indexes[0 * 3 + 0] = 0
            indexes[0 * 3 + 1] = 3
            indexes[0 * 3 + 2] = 2
            indexes[1 * 3 + 0] = 0
            indexes[1 * 3 + 1] = 2
            indexes[1 * 3 + 2] = 1
            indexes[2 * 3 + 0] = 0
            indexes[2 * 3 + 1] = 1
            indexes[2 * 3 + 2] = 5
            indexes[3 * 3 + 0] = 0
            indexes[3 * 3 + 1] = 5
            indexes[3 * 3 + 2] = 4
            indexes[4 * 3 + 0] = 0
            indexes[4 * 3 + 1] = 4
            indexes[4 * 3 + 2] = 7
            indexes[5 * 3 + 0] = 0
            indexes[5 * 3 + 1] = 7
            indexes[5 * 3 + 2] = 3
            indexes[6 * 3 + 0] = 6
            indexes[6 * 3 + 1] = 5
            indexes[6 * 3 + 2] = 1
            indexes[7 * 3 + 0] = 6
            indexes[7 * 3 + 1] = 1
            indexes[7 * 3 + 2] = 2
            indexes[8 * 3 + 0] = 6
            indexes[8 * 3 + 1] = 2
            indexes[8 * 3 + 2] = 3
            indexes[9 * 3 + 0] = 6
            indexes[9 * 3 + 1] = 3
            indexes[9 * 3 + 2] = 7
            indexes[10 * 3 + 0] = 6
            indexes[10 * 3 + 1] = 7
            indexes[10 * 3 + 2] = 4
            indexes[11 * 3 + 0] = 6
            indexes[11 * 3 + 1] = 4
            indexes[11 * 3 + 2] = 5

            GenerateEdgeIndexes()
        }

        fun SetupOctahedron(bounds: idBounds) {
            val center = idVec3()
            val scale = idVec3()
            center.set(bounds.GetCenter())
            scale.set(bounds[1] - center)

            verts.SetNum(6)
            verts[0].xyz.set(center + idVec3(scale.x, 0.0f, 0.0f))
            verts[1].xyz.set(center + idVec3(-scale.x, 0.0f, 0.0f))
            verts[2].xyz.set(center + idVec3(0.0f, scale.y, 0.0f))
            verts[3].xyz.set(center + idVec3(0.0f, -scale.y, 0.0f))
            verts[4].xyz.set(center + idVec3(0.0f, 0.0f, scale.z))
            verts[5].xyz.set(center + idVec3(0.0f, 0.0f, -scale.z))

            indexes.SetNum(8 * 3)
            indexes[0 * 3 + 0] = 4
            indexes[0 * 3 + 1] = 0
            indexes[0 * 3 + 2] = 2
            indexes[1 * 3 + 0] = 4
            indexes[1 * 3 + 1] = 2
            indexes[1 * 3 + 2] = 1
            indexes[2 * 3 + 0] = 4
            indexes[2 * 3 + 1] = 1
            indexes[2 * 3 + 2] = 3
            indexes[3 * 3 + 0] = 4
            indexes[3 * 3 + 1] = 3
            indexes[3 * 3 + 2] = 0
            indexes[4 * 3 + 0] = 5
            indexes[4 * 3 + 1] = 2
            indexes[4 * 3 + 2] = 0
            indexes[5 * 3 + 0] = 5
            indexes[5 * 3 + 1] = 1
            indexes[5 * 3 + 2] = 2
            indexes[6 * 3 + 0] = 5
            indexes[6 * 3 + 1] = 3
            indexes[6 * 3 + 2] = 1
            indexes[7 * 3 + 0] = 5
            indexes[7 * 3 + 1] = 0
            indexes[7 * 3 + 2] = 3

            GenerateEdgeIndexes()
        }

        fun SplitPolytope(
            plane: idPlane, epsilon: Float, front: Array<idSurface_Polytope?>, back: Array<idSurface_Polytope?>
        ): Int {
            val side: Int
            var i: Int
            var j: Int
            var s: Int
            var v0: Int
            var v1: Int
            var v2: Int
            var edgeNum: Int
            val surface = Array<Array<Array<idSurface?>?>>(2) { Array(1) { arrayOfNulls<idSurface?>(1) } }
            val polytopeSurfaces = Array(2) { idSurface_Polytope() }
            var surf: idSurface_Polytope
            val onPlaneEdges = Array(2) { IntArray(indexes.Num() / 3) }

            side = Split(plane, epsilon, surface[0], surface[1], onPlaneEdges[0], onPlaneEdges[1])
            front[0] = polytopeSurfaces[0]
            back[0] = polytopeSurfaces[1]
            s = 0
            while (s < 2) {
                if (surface[s][1]!![1] != null) {
                    polytopeSurfaces[s] = idSurface_Polytope()
                    polytopeSurfaces[s].SwapTriangles(surface[s][1]!![1]!!) //                    delete surface[s];
                    surface[s][1]!![1] = null
                }
                s++
            }
            front[0] = polytopeSurfaces[0]
            back[0] = polytopeSurfaces[1]
            if (side != SIDE_CROSS) {
                return side
            }

            // add triangles to close off the front and back polytope
            s = 0
            while (s < 2) {
                surf = polytopeSurfaces[s]
                edgeNum = surf.edgeIndexes[onPlaneEdges[s][0]]
                v0 = surf.edges[abs(edgeNum)].verts[INTSIGNBITSET(edgeNum)]
                v1 = surf.edges[abs(edgeNum)].verts[INTSIGNBITNOTSET(edgeNum)]
                i = 1
                while (onPlaneEdges[s][i] >= 0) {
                    j = i + 1
                    while (onPlaneEdges[s][j] >= 0) {
                        edgeNum = surf.edgeIndexes[onPlaneEdges[s][j]]
                        if (v1 == surf.edges[abs(edgeNum)].verts[INTSIGNBITSET(edgeNum)]) {
                            v1 = surf.edges[abs(edgeNum)].verts[INTSIGNBITNOTSET(edgeNum)]
                            idSwap(onPlaneEdges, s, i, onPlaneEdges, s, j)
                            break
                        }
                        j++
                    }
                    i++
                }
                i = 2
                while (onPlaneEdges[s][i] >= 0) {
                    edgeNum = surf.edgeIndexes[onPlaneEdges[s][i]]
                    v1 = surf.edges[abs(edgeNum)].verts[INTSIGNBITNOTSET(edgeNum)]
                    v2 = surf.edges[abs(edgeNum)].verts[INTSIGNBITSET(edgeNum)]
                    surf.indexes.Append(v0)
                    surf.indexes.Append(v1)
                    surf.indexes.Append(v2)
                    i++
                }
                surf.GenerateEdgeIndexes()
                s++
            }
            return side
        }
    }
}
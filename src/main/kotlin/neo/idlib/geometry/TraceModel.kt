package neo.idlib.geometry

import neo.idlib.BV.idBounds
import neo.idlib.containers.CFloat
import neo.idlib.geometry.Winding.idWinding
import neo.idlib.idLib
import neo.idlib.math.*
import neo.idlib.math.Matrix.idMat3
import java.util.*
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

object TraceModel {
    const val MAX_TRACEMODEL_EDGES = 32
    const val MAX_TRACEMODEL_POLYEDGES = 16
    const val MAX_TRACEMODEL_POLYS = 16
    const val MAX_TRACEMODEL_VERTS = 32

    /*
     ===============================================================================

     A trace model is an arbitrary polygonal model which is used by the
     collision detection system to find collisions, contacts or the contents
     of a volume. For collision detection speed reasons the number of vertices
     and edges are limited. The trace model can have any shape. However convex
     models are usually preferred.

     ===============================================================================
     */
    // trace model type
    enum class traceModel_t {
        TRM_INVALID,  // invalid trm
        TRM_BOX,  // box
        TRM_OCTAHEDRON,  // octahedron
        TRM_DODECAHEDRON,  // dodecahedron
        TRM_CYLINDER,  // cylinder approximation
        TRM_CONE,  // cone approximation
        TRM_BONE,  // two tetrahedrons attached to each other
        TRM_POLYGON,  // arbitrary convex polygon
        TRM_POLYGONVOLUME,  // volume for arbitrary convex polygon
        TRM_CUSTOM // loaded from map model or ASE/LWO
    }

    class traceModelVert_t : idVec3()
    class traceModelEdge_t {
        val normal: idVec3 = idVec3()
        var v: IntArray = IntArray(2)
        fun set(t: traceModelEdge_t) {
            v[0] = t.v[0]
            v[1] = t.v[1]
            normal.set(t.normal)
        }
    }

    class traceModelPoly_t {
        val bounds: idBounds = idBounds()
        var dist = 0.0f
        val edges: IntArray = IntArray(MAX_TRACEMODEL_POLYEDGES)
        val normal: idVec3 = idVec3()
        var numEdges = 0
        fun set(t: traceModelPoly_t) {
            bounds.set(t.bounds)
            dist = t.dist
            System.arraycopy(t.edges, 0, edges, 0, MAX_TRACEMODEL_POLYEDGES)
            normal.set(t.normal)
            numEdges = t.numEdges
        }
    }

    class idTraceModel() {
        // bounds of model
        val bounds: idBounds = idBounds()
        var edges = Array(MAX_TRACEMODEL_EDGES + 1) { traceModelEdge_t() }
        var isConvex = false // true when model is convex
        var numPolys = 0
        var numEdges = numPolys
        var numVerts = numEdges

        // offset to center of model
        val offset: idVec3 = idVec3()
        var polys = Array(MAX_TRACEMODEL_POLYS) { traceModelPoly_t() }
        var type = traceModel_t.TRM_INVALID
        var verts = Array(MAX_TRACEMODEL_VERTS) { traceModelVert_t() }

        // axial bounding box
        constructor(boxBounds: idBounds) : this() {
            InitBox()
            SetupBox(boxBounds)
        }

        // cylinder approximation
        constructor(cylBounds: idBounds, numSides: Int) : this() {
            SetupCylinder(cylBounds, numSides)
        }

        // bone
        constructor(length: Float, width: Float) : this() {
            InitBone()
            SetupBone(length, width)
        }

        // axial box
        fun SetupBox(boxBounds: idBounds) {
            var i: Int
            if (type != traceModel_t.TRM_BOX) {
                InitBox()
            }
            // offset to center
            offset.set((boxBounds[0] + boxBounds[1]) * 0.5f)
            // set box vertices
            i = 0
            while (i < 8) {
                verts[i][0] = boxBounds[i xor (i shr 1) and 1][0]
                verts[i][1] = boxBounds[i shr 1 and 1][1]
                verts[i][2] = boxBounds[i shr 2 and 1][2]
                i++
            }
            // set polygon plane distances
            polys[0].dist = -boxBounds[0][2]
            polys[1].dist = boxBounds[1][2]
            polys[2].dist = -boxBounds[0][1]
            polys[3].dist = boxBounds[1][0]
            polys[4].dist = boxBounds[1][1]
            polys[5].dist = -boxBounds[0][0]
            // set polygon bounds
            i = 0
            while (i < 6) {
                polys[i].bounds.set(boxBounds)
                i++
            }
            polys[0].bounds[1, 2] = boxBounds[0][2]
            polys[1].bounds[0, 2] = boxBounds[1][2]
            polys[2].bounds[1, 1] = boxBounds[0][1]
            polys[3].bounds[0, 0] = boxBounds[1][0]
            polys[4].bounds[0, 1] = boxBounds[1][1]
            polys[5].bounds[1, 0] = boxBounds[0][0]
            bounds.set(boxBounds)
        }

        /*
         ============
         idTraceModel::SetupBox

         The origin is placed at the center of the cube.
         ============
         */
        fun SetupBox(size: Float) {
            val boxBounds = idBounds()
            val halfSize: Float
            halfSize = size * 0.5f
            boxBounds[0].set(-halfSize, -halfSize, -halfSize)
            boxBounds[1].set(halfSize, halfSize, halfSize)
            SetupBox(boxBounds)
        }

        // octahedron
        fun SetupOctahedron(octBounds: idBounds) {
            var i: Int
            var e0: Int
            var e1: Int
            var v0: Int
            var v1: Int
            var v2: Int
            val v = idVec3()
            if (type != traceModel_t.TRM_OCTAHEDRON) {
                InitOctahedron()
            }
            offset.set((octBounds[0] + octBounds[1]) * 0.5f)
            v[0] = octBounds[1][0] - offset[0]
            v[1] = octBounds[1][1] - offset[1]
            v[2] = octBounds[1][2] - offset[2]

            // set vertices
            verts[0].set(offset.x + v[0], offset.y, offset.z)
            verts[1].set(offset.x - v[0], offset.y, offset.z)
            verts[2].set(offset.x, offset.y + v[1], offset.z)
            verts[3].set(offset.x, offset.y - v[1], offset.z)
            verts[4].set(offset.x, offset.y, offset.z + v[2])
            verts[5].set(offset.x, offset.y, offset.z - v[2])

            // set polygons
            i = 0
            while (i < numPolys) {
                e0 = polys[i].edges[0]
                e1 = polys[i].edges[1]
                v0 = edges[abs(e0)].v[INTSIGNBITSET(e0)]
                v1 = edges[abs(e0)].v[INTSIGNBITNOTSET(e0)]
                v2 = edges[abs(e1)].v[INTSIGNBITNOTSET(e1)]
                // polygon plane
                polys[i].normal.set((verts[v1] - verts[v0]).Cross(verts[v2] - verts[v0]))
                polys[i].normal.Normalize()
                polys[i].dist = polys[i].normal * verts[v0]
                // polygon bounds
                polys[i].bounds[0].set(polys[i].bounds.set(0, verts[v0]))
                polys[i].bounds.AddPoint(verts[v1])
                polys[i].bounds.AddPoint(verts[v2])
                i++
            }

            // trm bounds
            bounds.set(octBounds)
            GenerateEdgeNormals()
        }

        /*
         ============
         idTraceModel::SetupOctahedron

         The origin is placed at the center of the octahedron.
         ============
         */
        fun SetupOctahedron(size: Float) {
            val octBounds = idBounds()
            val halfSize: Float
            halfSize = size * 0.5f
            octBounds[0].set(-halfSize, -halfSize, -halfSize)
            octBounds[1].set(halfSize, halfSize, halfSize)
            SetupOctahedron(octBounds)
        }

        // dodecahedron
        fun SetupDodecahedron(dodBounds: idBounds) {
            var i: Int
            var e0: Int
            var e1: Int
            var e2: Int
            var e3: Int
            var v0: Int
            var v1: Int
            var v2: Int
            var v3: Int
            var v4: Int
            var s: Float
            val d: Float
            val a = idVec3()
            val b = idVec3()
            val c = idVec3()
            if (type != traceModel_t.TRM_DODECAHEDRON) {
                InitDodecahedron()
            }
            a.z = 0.5773502691896257f
            a.y = a.z
            a.x = a.y // 1.0f / ( 3.0f ) ^ 0.5f;
            b.z = 0.3568220897730899f
            b.y = b.z
            b.x = b.y // ( ( 3.0f - ( 5.0f ) ^ 0.5f ) / 6.0f ) ^ 0.5f;
            c.z = 0.9341723589627156f
            c.y = c.z
            c.x = c.y // ( ( 3.0f + ( 5.0f ) ^ 0.5f ) / 6.0f ) ^ 0.5f;
            d = 0.5f / c[0]
            s = (dodBounds[1][0] - dodBounds[0][0]) * d
            a.x *= s
            b.x *= s
            c.x *= s
            s = (dodBounds[1][1] - dodBounds[0][1]) * d
            a.y *= s
            b.y *= s
            c.y *= s
            s = (dodBounds[1][2] - dodBounds[0][2]) * d
            a.z *= s
            b.z *= s
            c.z *= s
            offset.set((dodBounds[0] + dodBounds[1]) * 0.5f)

            // set vertices
            verts[0].set(offset.x + a[0], offset.y + a[1], offset.z + a[2])
            verts[1].set(offset.x + a[0], offset.y + a[1], offset.z - a[2])
            verts[2].set(offset.x + a[0], offset.y - a[1], offset.z + a[2])
            verts[3].set(offset.x + a[0], offset.y - a[1], offset.z - a[2])
            verts[4].set(offset.x - a[0], offset.y + a[1], offset.z + a[2])
            verts[5].set(offset.x - a[0], offset.y + a[1], offset.z - a[2])
            verts[6].set(offset.x - a[0], offset.y - a[1], offset.z + a[2])
            verts[7].set(offset.x - a[0], offset.y - a[1], offset.z - a[2])
            verts[8].set(offset.x + b[0], offset.y + c[1], offset.z /*        */)
            verts[9].set(offset.x - b[0], offset.y + c[1], offset.z /*        */)
            verts[10].set(offset.x + b[0], offset.y - c[1], offset.z /*        */)
            verts[11].set(offset.x - b[0], offset.y - c[1], offset.z /*        */)
            verts[12].set(offset.x + c[0], offset.y /*        */, offset.z + b[2])
            verts[13].set(offset.x + c[0], offset.y /*        */, offset.z - b[2])
            verts[14].set(offset.x - c[0], offset.y /*        */, offset.z + b[2])
            verts[15].set(offset.x - c[0], offset.y /*        */, offset.z - b[2])
            verts[16].set(offset.x /*        */, offset.y + b[1], offset.z + c[2])
            verts[17].set(offset.x /*        */, offset.y - b[1], offset.z + c[2])
            verts[18].set(offset.x /*        */, offset.y + b[1], offset.z - c[2])
            verts[19].set(offset.x /*        */, offset.y - b[1], offset.z - c[2])

            // set polygons
            i = 0
            while (i < numPolys) {
                e0 = polys[i].edges[0]
                e1 = polys[i].edges[1]
                e2 = polys[i].edges[2]
                e3 = polys[i].edges[3]
                v0 = edges[abs(e0)].v[INTSIGNBITSET(e0)]
                v1 = edges[abs(e0)].v[INTSIGNBITNOTSET(e0)]
                v2 = edges[abs(e1)].v[INTSIGNBITNOTSET(e1)]
                v3 = edges[abs(e2)].v[INTSIGNBITNOTSET(e2)]
                v4 = edges[abs(e3)].v[INTSIGNBITNOTSET(e3)]
                // polygon plane
                polys[i].normal.set((verts[v1] - verts[v0]).Cross(verts[v2] - verts[v0]))
                polys[i].normal.Normalize()
                polys[i].dist = polys[i].normal * verts[v0]
                // polygon bounds
                polys[i].bounds[0] = polys[i].bounds.set(1, verts[v0])
                polys[i].bounds.AddPoint(verts[v1])
                polys[i].bounds.AddPoint(verts[v2])
                polys[i].bounds.AddPoint(verts[v3])
                polys[i].bounds.AddPoint(verts[v4])
                i++
            }

            // trm bounds
            bounds.set(dodBounds)
            GenerateEdgeNormals()
        }

        /*
         ============
         idTraceModel::SetupDodecahedron

         The origin is placed at the center of the octahedron.
         ============
         */
        fun SetupDodecahedron(size: Float) {
            val dodBounds = idBounds()
            val halfSize: Float
            halfSize = size * 0.5f
            dodBounds[0].set(-halfSize, -halfSize, -halfSize)
            dodBounds[1].set(halfSize, halfSize, halfSize)
            SetupDodecahedron(dodBounds)
        }

        // cylinder approximation
        fun SetupCylinder(cylBounds: idBounds, numSides: Int) {
            var i: Int
            var n: Int
            var ii: Int
            var n2: Int
            var angle: Float
            val halfSize = idVec3()
            n = numSides
            if (n < 3) {
                n = 3
            }
            if (n * 2 > MAX_TRACEMODEL_VERTS) {
                idLib.common.Printf("WARNING: idTraceModel::SetupCylinder: too many vertices\n")
                n = MAX_TRACEMODEL_VERTS / 2
            }
            if (n * 3 > MAX_TRACEMODEL_EDGES) {
                idLib.common.Printf("WARNING: idTraceModel::SetupCylinder: too many sides\n")
                n = MAX_TRACEMODEL_EDGES / 3
            }
            if (n + 2 > MAX_TRACEMODEL_POLYS) {
                idLib.common.Printf("WARNING: idTraceModel::SetupCylinder: too many polygons\n")
                n = MAX_TRACEMODEL_POLYS - 2
            }
            type = traceModel_t.TRM_CYLINDER
            numVerts = n * 2
            numEdges = n * 3
            numPolys = n + 2
            offset.set((cylBounds[0] + cylBounds[1]) * 0.5f)
            halfSize.set(cylBounds[1] - offset)
            i = 0
            while (i < n) {

                // verts
                angle = idMath.TWO_PI * i / n
                verts[i].x = cos(angle) * halfSize.x + offset.x
                verts[i].y = sin(angle) * halfSize.y + offset.y
                verts[i].z = -halfSize.z + offset.z
                verts[n + i].x = verts[i].x
                verts[n + i].y = verts[i].y
                verts[n + i].z = halfSize.z + offset.z
                // edges
                ii = i + 1
                n2 = n shl 1
                edges[ii].v[0] = i
                edges[ii].v[1] = ii % n
                edges[n + ii].v[0] = edges[ii].v[0] + n
                edges[n + ii].v[1] = edges[ii].v[1] + n
                edges[n2 + ii].v[0] = i
                edges[n2 + ii].v[1] = n + i
                // vertical polygon edges
                polys[i].numEdges = 4
                polys[i].edges[0] = ii
                polys[i].edges[1] = n2 + (ii % n) + 1
                polys[i].edges[2] = -(n + ii)
                polys[i].edges[3] = -(n2 + ii)
                // bottom and top polygon edges
                polys[n].edges[i] = -(n - i)
                polys[n + 1].edges[i] = n + ii
                i++
            }
            // bottom and top polygon numEdges
            polys[n].numEdges = n
            polys[n + 1].numEdges = n
            // polygons
            i = 0
            while (i < n) {

                // vertical polygon plane
                polys[i].normal.set((verts[(i + 1) % n] - verts[i]).Cross(verts[n + i] - verts[i]))
                polys[i].normal.Normalize()
                polys[i].dist = polys[i].normal * verts[i]
                // vertical polygon bounds
                polys[i].bounds.Clear()
                polys[i].bounds.AddPoint(verts[i])
                polys[i].bounds.AddPoint(verts[(i + 1) % n])
                polys[i].bounds[0, 2] = -halfSize.z + offset.z
                polys[i].bounds[1, 2] = halfSize.z + offset.z
                i++
            }
            // bottom and top polygon plane
            polys[n].normal.set(0.0f, 0.0f, -1.0f)
            polys[n].dist = -cylBounds[0][2]
            polys[n + 1].normal.set(0.0f, 0.0f, 1.0f)
            polys[n + 1].dist = cylBounds[1][2]
            // trm bounds
            bounds.set(cylBounds)
            // bottom and top polygon bounds
            polys[n].bounds.set(bounds)
            polys[n].bounds[1, 2] = bounds[0][2]
            polys[n + 1].bounds.set(bounds)
            polys[n + 1].bounds[0, 2] = bounds[1][2]
            // convex model
            isConvex = true
            GenerateEdgeNormals()
        }

        /*
         ============
         idTraceModel::SetupCylinder

         The origin is placed at the center of the cylinder.
         ============
         */
        fun SetupCylinder(height: Float, width: Float, numSides: Int) {
            val cylBounds = idBounds()
            val halfHeight: Float
            val halfWidth: Float
            halfHeight = height * 0.5f
            halfWidth = width * 0.5f
            cylBounds[0].set(-halfWidth, -halfWidth, -halfHeight)
            cylBounds[1].set(halfWidth, halfWidth, halfHeight)
            SetupCylinder(cylBounds, numSides)
        }

        // cone approximation
        fun SetupCone(coneBounds: idBounds, numSides: Int) {
            var i: Int
            var n: Int
            var ii: Int
            var angle: Float
            val halfSize = idVec3()
            n = numSides
            if (n < 2) {
                n = 3
            }
            if (n + 1 > MAX_TRACEMODEL_VERTS) {
                idLib.common.Printf("WARNING: idTraceModel::SetupCone: too many vertices\n")
                n = MAX_TRACEMODEL_VERTS - 1
            }
            if (n * 2 > MAX_TRACEMODEL_EDGES) {
                idLib.common.Printf("WARNING: idTraceModel::SetupCone: too many edges\n")
                n = MAX_TRACEMODEL_EDGES / 2
            }
            if (n + 1 > MAX_TRACEMODEL_POLYS) {
                idLib.common.Printf("WARNING: idTraceModel::SetupCone: too many polygons\n")
                n = MAX_TRACEMODEL_POLYS - 1
            }
            type = traceModel_t.TRM_CONE
            numVerts = n + 1
            numEdges = n * 2
            numPolys = n + 1
            offset.set((coneBounds[0] + coneBounds[1]) * 0.5f)
            halfSize.set(coneBounds[1] - offset)
            verts[n].set(0.0f, 0.0f, halfSize.z + offset.z)
            i = 0
            while (i < n) {
                // verts
                angle = idMath.TWO_PI * i / n
                verts[i].x = cos(angle) * halfSize.x + offset.x
                verts[i].y = sin(angle) * halfSize.y + offset.y
                verts[i].z = -halfSize.z + offset.z
                // edges
                ii = i + 1
                edges[ii].v[0] = i
                edges[ii].v[1] = ii % n
                edges[n + ii].v[0] = i
                edges[n + ii].v[1] = n
                // vertical polygon edges
                polys[i].numEdges = 3
                polys[i].edges[0] = ii
                polys[i].edges[1] = n + (ii % n) + 1
                polys[i].edges[2] = -(n + ii)
                // bottom polygon edges
                polys[n].edges[i] = -(n - i)
                i++
            }
            // bottom polygon numEdges
            polys[n].numEdges = n

            // polygons
            i = 0
            while (i < n) {

                // polygon plane
                polys[i].normal.set((verts[(i + 1) % n] - verts[i]).Cross(verts[n] - verts[i]))
                polys[i].normal.Normalize()
                polys[i].dist = polys[i].normal * verts[i]
                // polygon bounds
                polys[i].bounds.Clear()
                polys[i].bounds.AddPoint(verts[i])
                polys[i].bounds.AddPoint(verts[(i + 1) % n])
                polys[i].bounds.AddPoint(verts[n])
                i++
            }
            // bottom polygon plane
            polys[n].normal.set(0.0f, 0.0f, -1.0f)
            polys[n].dist = -coneBounds[0][2]
            // trm bounds
            bounds.set(coneBounds)
            // bottom polygon bounds
            polys[n].bounds.set(bounds)
            polys[n].bounds[1, 2] = bounds[0][2]
            // convex model
            isConvex = true
            GenerateEdgeNormals()
        }

        /*
         ============
         idTraceModel::SetupCone

         The origin is placed at the apex of the cone.
         ============
         */
        fun SetupCone(height: Float, width: Float, numSides: Int) {
            val coneBounds = idBounds()
            val halfWidth: Float
            halfWidth = width * 0.5f
            coneBounds[0].set(-halfWidth, -halfWidth, -height)
            coneBounds[1].set(halfWidth, halfWidth, 0.0f)
            SetupCone(coneBounds, numSides)
        }

        /*
         ============
         idTraceModel::SetupBone

         The origin is placed at the center of the bone.
         ============
         */
        fun SetupBone(length: Float, width: Float) { // two tetrahedrons attached to each other
            var i: Int
            var j: Int
            var edgeNum: Int
            val halfLength = length * 0.5f
            if (type != traceModel_t.TRM_BONE) {
                InitBone()
            }
            // offset to center
            offset.set(0.0f, 0.0f, 0.0f)
            // set vertices
            verts[0].set(0.0f, 0.0f, -halfLength)
            verts[1].set(0.0f, width * -0.5f, 0.0f)
            verts[2].set(width * 0.5f, width * 0.25f, 0.0f)
            verts[3].set(width * -0.5f, width * 0.25f, 0.0f)
            verts[4].set(0.0f, 0.0f, halfLength)
            // set bounds
            bounds[0].set(width * -0.5f, width * -0.5f, -halfLength)
            bounds[1].set(width * 0.5f, width * 0.25f, halfLength)
            // poly plane normals
            polys[0].normal.set((verts[2] - verts[0]).Cross(verts[1] - verts[0]))
            polys[0].normal.Normalize()
            polys[2].normal.set(-polys[0].normal[0], polys[0].normal[1], polys[0].normal[2])
            polys[3].normal.set(polys[0].normal[0], polys[0].normal[1], -polys[0].normal[2])
            polys[5].normal.set(-polys[0].normal[0], polys[0].normal[1], -polys[0].normal[2])
            polys[1].normal.set((verts[3] - verts[0]).Cross(verts[2] - verts[0]))
            polys[1].normal.Normalize()
            polys[4].normal.set(polys[1].normal[0], polys[1].normal[1], -polys[1].normal[2])
            // poly plane distances
            i = 0
            while (i < 6) {
                polys[i].dist = polys[i].normal * verts[edges[abs(polys[i].edges[0])].v[0]]
                polys[i].bounds.Clear()
                j = 0
                while (j < 3) {
                    edgeNum = polys[i].edges[j]
                    polys[i].bounds.AddPoint(verts[edges[abs(edgeNum)].v[if (edgeNum < 0) 1 else 0]])
                    j++
                }
                i++
            }
            GenerateEdgeNormals()
        }

        // arbitrary convex polygon
        fun SetupPolygon(v: Array<idVec3>, count: Int) {
            var i: Int
            var j: Int
            val mid = idVec3()
            type = traceModel_t.TRM_POLYGON
            numVerts = count
            // times three because we need to be able to turn the polygon into a volume
            if (numVerts * 3 > MAX_TRACEMODEL_EDGES) {
                idLib.common.Printf("WARNING: idTraceModel::SetupPolygon: too many vertices\n")
                numVerts = MAX_TRACEMODEL_EDGES / 3
            }
            numEdges = numVerts
            numPolys = 2
            // set polygon planes
            polys[0].numEdges = numEdges
            polys[0].normal.set((v[1] - v[0]).Cross(v[2] - v[0]))
            polys[0].normal.Normalize()
            polys[0].dist = polys[0].normal * v[0]
            polys[1].numEdges = numEdges
            polys[1].normal.set(polys[0].normal.unaryMinus())
            polys[1].dist = -polys[0].dist
            // setup verts, edges and polygons
            polys[0].bounds.Clear()
            mid.set(vec3_origin)
            i = 0
            j = 1
            while (i < numVerts) {
                if (j >= numVerts) {
                    j = 0
                }
                verts[i].set(v[i])
                edges[i + 1].v[0] = i
                edges[i + 1].v[1] = j
                edges[i + 1].normal.set(polys[0].normal.Cross(v[i] - v[j]))
                edges[i + 1].normal.Normalize()
                polys[0].edges[i] = i + 1
                polys[1].edges[i] = -(numVerts - i)
                polys[0].bounds.AddPoint(verts[i])
                mid.plusAssign(v[i])
                i++
                j++
            }
            polys[1].bounds.set(polys[0].bounds)
            // offset to center
            offset.set(mid * (1.0f / numVerts))
            // total bounds
            bounds.set(polys[0].bounds)
            // considered non convex because the model has no volume
            isConvex = false
        }

        fun SetupPolygon(w: idWinding) {
            var i: Int
            val numPoints = w.GetNumPoints()

            val verts: Array<idVec3> = Array(numPoints) { idVec3() }
            i = 0
            while (i < numPoints) {
                verts[i].set(w[i].ToVec3())
                i++
            }
            SetupPolygon(verts, numPoints)
        }

        // generate edge normals
        fun GenerateEdgeNormals(): Int {
            var i: Int
            var j: Int
            var edgeNum: Int
            var numSharpEdges: Int
            var dot: Float
            val dir = idVec3()
            var poly: traceModelPoly_t?
            var edge: traceModelEdge_t?
            i = 0
            while (i <= numEdges) {
                edges[i].normal.Zero()
                i++
            }
            numSharpEdges = 0
            i = 0
            while (i < numPolys) {
                poly = polys[i]
                j = 0
                while (j < poly.numEdges) {
                    edgeNum = poly.edges[j]
                    edge = edges[abs(edgeNum)]
                    if (edge.normal[0] == 0.0f && edge.normal[1] == 0.0f && edge.normal[2] == 0.0f) {
                        edge.normal.set(poly.normal)
                    } else {
                        dot = edge.normal * poly.normal
                        // if the two planes make a very sharp edge
                        if (dot < SHARP_EDGE_DOT) {
                            // max length normal pointing outside both polygons
                            // verts[ edge->v[edgeNum > 0]] - verts[ edge->v[edgeNum < 0]]
                            dir.set(verts[edge.v[if (edgeNum > 0) 1 else 0]] - verts[edge.v[if (edgeNum < 0) 1 else 0]])
                            edge.normal.set(edge.normal.Cross(dir) + poly.normal.Cross(-dir))
                            edge.normal.timesAssign(0.5f / (0.5f + 0.5f * SHARP_EDGE_DOT) / edge.normal.Length())
                            numSharpEdges++
                        } else {
                            edge.normal.set((edge.normal + poly.normal) * (0.5f / (0.5f + 0.5f * dot)))
                        }
                    }
                    j++
                }
                i++
            }
            return numSharpEdges
        }

        // translate the trm
        fun Translate(translation: idVec3) {
            var i: Int
            i = 0
            while (i < numVerts) {
                verts[i].plusAssign(translation)
                i++
            }
            i = 0
            while (i < numPolys) {
                polys[i].dist += polys[i].normal * translation
                polys[i].bounds.timesAssign(translation)
                i++
            }
            offset.plusAssign(translation)
            bounds.timesAssign(translation)
        }

        // rotate the trm
        fun Rotate(rotation: idMat3) {
            var i: Int
            var j: Int
            var edgeNum: Int
            i = 0
            while (i < numVerts) {
                verts[i].timesAssign(rotation)
                i++
            }
            bounds.Clear()
            i = 0
            while (i < numPolys) {
                polys[i].normal.timesAssign(rotation)
                polys[i].bounds.Clear()
                edgeNum = 0
                j = 0
                while (j < polys[i].numEdges) {
                    edgeNum = polys[i].edges[j]
                    polys[i].bounds.AddPoint(verts[edges[abs(edgeNum)].v[INTSIGNBITSET(edgeNum)]])
                    j++
                }
                polys[i].dist = polys[i].normal * verts[edges[abs(edgeNum)].v[INTSIGNBITSET(edgeNum)]]
                bounds.timesAssign(polys[i].bounds)
                i++
            }
            GenerateEdgeNormals()
        }

        // shrink the model m units on all sides
        fun Shrink(m: Float) {
            var i: Int
            var j: Int
            var edgeNum: Int
            var edge: traceModelEdge_t?
            val dir = idVec3()
            if (type == traceModel_t.TRM_POLYGON) {
                i = 0
                while (i < numEdges) {
                    edgeNum = polys[0].edges[i]
                    edge = edges[abs(edgeNum)]
                    dir.set(
                        verts[edge.v[INTSIGNBITSET(edgeNum)]] - verts[edge.v[INTSIGNBITNOTSET(edgeNum)]]
                    )
                    if (dir.Normalize() < 2.0f * m) {
                        i++
                        continue
                    }
                    dir.timesAssign(m)
                    verts[edge.v[0]].minusAssign(dir)
                    verts[edge.v[1]].plusAssign(dir)
                    i++
                }
                return
            }
            i = 0
            while (i < numPolys) {
                polys[i].dist -= m
                j = 0
                while (j < polys[i].numEdges) {
                    edgeNum = polys[i].edges[j]
                    edge = edges[abs(edgeNum)]
                    verts[edge.v[INTSIGNBITSET(edgeNum)]].minusAssign(polys[i].normal * m)
                    j++
                }
                i++
            }
        }

        // compare
        fun Compare(trm: idTraceModel): Boolean {
            if (type != trm.type || numVerts != trm.numVerts || numEdges != trm.numEdges || numPolys != trm.numPolys) {
                return false
            }
            if (bounds !== trm.bounds || offset !== trm.offset) {
                return false
            }
            when (type) {
                traceModel_t.TRM_INVALID, traceModel_t.TRM_BOX, traceModel_t.TRM_OCTAHEDRON, traceModel_t.TRM_DODECAHEDRON, traceModel_t.TRM_CYLINDER, traceModel_t.TRM_CONE -> {}
                traceModel_t.TRM_BONE, traceModel_t.TRM_POLYGON, traceModel_t.TRM_POLYGONVOLUME, traceModel_t.TRM_CUSTOM -> {
                    for (i in 0 until trm.numVerts) {
                        if (verts[i] != trm.verts[i]) {
                            return false
                        }
                    }
                }
            }
            return true
        }

        override fun hashCode(): Int {
            var hash = 3
            hash = 19 * hash + type.hashCode()
            hash = 19 * hash + numVerts
            hash = 19 * hash + verts.contentDeepHashCode()
            hash = 19 * hash + numEdges
            hash = 19 * hash + numPolys
            hash = 19 * hash + Objects.hashCode(offset)
            hash = 19 * hash + Objects.hashCode(bounds)
            return hash
        }

        override fun equals(obj: Any?): Boolean {
            if (obj == null) {
                return false
            }
            if (javaClass != obj.javaClass) {
                return false
            }
            val other = obj as idTraceModel
            if (type != other.type) {
                return false
            }
            if (numVerts != other.numVerts) {
                return false
            }
            if (!verts.contentDeepEquals(other.verts)) {
                return false
            }
            if (numEdges != other.numEdges) {
                return false
            }
            if (numPolys != other.numPolys) {
                return false
            }
            return if (offset != other.offset) {
                false
            } else bounds == other.bounds
        }

        //public	bool				operator==(	const idTraceModel &trm ) const;
        //public	bool				operator!=(	const idTraceModel &trm ) const;
        //
        // get the area of one of the polygons
        fun GetPolygonArea(polyNum: Int): Float {
            var i: Int
            val base = idVec3()
            val v1 = idVec3()
            val v2 = idVec3()
            val cross = idVec3()
            var total: Float
            val poly: traceModelPoly_t?
            if (polyNum < 0 || polyNum >= numPolys) {
                return 0.0f
            }
            poly = polys[polyNum]
            total = 0.0f
            base.set(verts[edges[abs(poly.edges[0])].v[INTSIGNBITSET(poly.edges[0])]])
            i = 0
            while (i < poly.numEdges) {
                v1.set(
                    verts[edges[abs(poly.edges[i])].v[INTSIGNBITSET(poly.edges[i])]] - base
                )
                v2.set(
                    verts[edges[abs(poly.edges[i])].v[INTSIGNBITNOTSET(poly.edges[i])]] - base
                )
                cross.set(v1.Cross(v2))
                total += cross.Length()
                i++
            }
            return total * 0.5f
        }

        // get the silhouette edges
        fun GetProjectionSilhouetteEdges(projectionOrigin: idVec3, silEdges: IntArray): Int {
            var i: Int
            var j: Int
            var edgeNum: Int
            val edgeIsSilEdge = IntArray(MAX_TRACEMODEL_EDGES + 1)
            var poly: traceModelPoly_t?
            val dir = idVec3()

//	memset( edgeIsSilEdge, 0, sizeof( edgeIsSilEdge ) );
            i = 0
            while (i < numPolys) {
                poly = polys[i]
                edgeNum = poly.edges[0]
                dir.set(verts[edges[abs(edgeNum)].v[INTSIGNBITSET(edgeNum)]] - projectionOrigin)
                if (dir * poly.normal < 0.0f) {
                    j = 0
                    while (j < poly.numEdges) {
                        edgeNum = poly.edges[j]
                        edgeIsSilEdge[abs(edgeNum)] = edgeIsSilEdge[abs(edgeNum)] xor 1
                        j++
                    }
                }
                i++
            }
            return GetOrderedSilhouetteEdges(edgeIsSilEdge, silEdges)
        }

        fun GetParallelProjectionSilhouetteEdges(projectionDir: idVec3, silEdges: IntArray): Int {
            var i: Int
            var j: Int
            var edgeNum: Int
            val edgeIsSilEdge = IntArray(MAX_TRACEMODEL_EDGES + 1)
            var poly: traceModelPoly_t?

//	memset( edgeIsSilEdge, 0, sizeof( edgeIsSilEdge ) );
            i = 0
            while (i < numPolys) {
                poly = polys[i]
                if (projectionDir * poly.normal < 0.0f) {
                    j = 0
                    while (j < poly.numEdges) {
                        edgeNum = poly.edges[j]
                        edgeIsSilEdge[abs(edgeNum)] = edgeIsSilEdge[abs(edgeNum)] xor 1
                        j++
                    }
                }
                i++
            }
            return GetOrderedSilhouetteEdges(edgeIsSilEdge, silEdges)
        }

        // calculate mass properties assuming an uniform density
        fun GetMassProperties(density: Float, mass: CFloat, centerOfMass: idVec3, inertiaTensor: idMat3) {
            val integrals = volumeIntegrals_t()
            // if polygon trace model
            if (type == traceModel_t.TRM_POLYGON) {
                val trm = idTraceModel()
                VolumeFromPolygon(trm, 1.0f)
                trm.GetMassProperties(density, mass, centerOfMass, inertiaTensor)
                return
            }
            VolumeIntegrals(integrals)

            // if no volume
            if (integrals.T0 == 0.0f) {
                mass._val = (1.0f)
                centerOfMass.Zero()
                inertiaTensor.Identity()
                return
            }

            // mass of model
            mass._val = (density * integrals.T0)
            // center of mass
            centerOfMass.set(integrals.T1 / integrals.T0)
            // compute inertia tensor
            inertiaTensor.set(0, 0, density * (integrals.T2[1] + integrals.T2[2]))
            inertiaTensor.set(1, 1, density * (integrals.T2[2] + integrals.T2[0]))
            inertiaTensor.set(2, 2, density * (integrals.T2[0] + integrals.T2[1]))
            inertiaTensor.set(0, 1, -density * integrals.TP[0])
            inertiaTensor.set(1, 0, -density * integrals.TP[0])
            inertiaTensor.set(1, 2, -density * integrals.TP[1])
            inertiaTensor.set(2, 1, -density * integrals.TP[1])
            inertiaTensor.set(2, 0, -density * integrals.TP[2])
            inertiaTensor.set(0, 2, -density * integrals.TP[2])
            // translate inertia tensor to center of mass
            inertiaTensor.minusAssign(
                0,
                0,
                mass._val * (centerOfMass[1] * centerOfMass[1] + centerOfMass[2] * centerOfMass[2])
            )
            inertiaTensor.minusAssign(
                1,
                1,
                mass._val * (centerOfMass[2] * centerOfMass[2] + centerOfMass[0] * centerOfMass[0])
            )
            inertiaTensor.minusAssign(
                2,
                2,
                mass._val * (centerOfMass[0] * centerOfMass[0] + centerOfMass[1] * centerOfMass[1])
            )
            inertiaTensor.plusAssign(0, 1, mass._val * centerOfMass[0] * centerOfMass[1])
            inertiaTensor.plusAssign(1, 0, mass._val * centerOfMass[0] * centerOfMass[1])
            inertiaTensor.plusAssign(1, 2, mass._val * centerOfMass[1] * centerOfMass[2])
            inertiaTensor.plusAssign(2, 1, mass._val * centerOfMass[1] * centerOfMass[2])
            inertiaTensor.plusAssign(2, 0, mass._val * centerOfMass[2] * centerOfMass[0])
            inertiaTensor.plusAssign(0, 2, mass._val * centerOfMass[2] * centerOfMass[0])
        }

        //
        /*
         ============
         idTraceModel::InitBox

         Initialize size independent box.
         ============
         */
        private fun InitBox() {
            var i: Int
            type = traceModel_t.TRM_BOX
            numVerts = 8
            numEdges = 12
            numPolys = 6

            // set box edges
            i = 0
            while (i < 4) {
                edges[i + 1].v[0] = i
                edges[i + 1].v[1] = i + 1 and 3
                edges[i + 5].v[0] = 4 + i
                edges[i + 5].v[1] = 4 + (i + 1 and 3)
                edges[i + 9].v[0] = i
                edges[i + 9].v[1] = 4 + i
                i++
            }

            // all edges of a polygon go counter clockwise
            polys[0].numEdges = 4
            polys[0].edges[0] = -4
            polys[0].edges[1] = -3
            polys[0].edges[2] = -2
            polys[0].edges[3] = -1
            polys[0].normal.set(0.0f, 0.0f, -1.0f)
            polys[1].numEdges = 4
            polys[1].edges[0] = 5
            polys[1].edges[1] = 6
            polys[1].edges[2] = 7
            polys[1].edges[3] = 8
            polys[1].normal.set(0.0f, 0.0f, 1.0f)
            polys[2].numEdges = 4
            polys[2].edges[0] = 1
            polys[2].edges[1] = 10
            polys[2].edges[2] = -5
            polys[2].edges[3] = -9
            polys[2].normal.set(0.0f, -1.0f, 0.0f)
            polys[3].numEdges = 4
            polys[3].edges[0] = 2
            polys[3].edges[1] = 11
            polys[3].edges[2] = -6
            polys[3].edges[3] = -10
            polys[3].normal.set(1.0f, 0.0f, 0.0f)
            polys[4].numEdges = 4
            polys[4].edges[0] = 3
            polys[4].edges[1] = 12
            polys[4].edges[2] = -7
            polys[4].edges[3] = -11
            polys[4].normal.set(0.0f, 1.0f, 0.0f)
            polys[5].numEdges = 4
            polys[5].edges[0] = 4
            polys[5].edges[1] = 9
            polys[5].edges[2] = -8
            polys[5].edges[3] = -12
            polys[5].normal.set(-1.0f, 0.0f, 0.0f)

            // convex model
            isConvex = true
            GenerateEdgeNormals()
        }

        /*
         ============
         idTraceModel::InitOctahedron

         Initialize size independent octahedron.
         ============
         */
        private fun InitOctahedron() {
            type = traceModel_t.TRM_OCTAHEDRON
            numVerts = 6
            numEdges = 12
            numPolys = 8

            // set edges
            edges[1].v[0] = 4
            edges[1].v[1] = 0
            edges[2].v[0] = 0
            edges[2].v[1] = 2
            edges[3].v[0] = 2
            edges[3].v[1] = 4
            edges[4].v[0] = 2
            edges[4].v[1] = 1
            edges[5].v[0] = 1
            edges[5].v[1] = 4
            edges[6].v[0] = 1
            edges[6].v[1] = 3
            edges[7].v[0] = 3
            edges[7].v[1] = 4
            edges[8].v[0] = 3
            edges[8].v[1] = 0
            edges[9].v[0] = 5
            edges[9].v[1] = 2
            edges[10].v[0] = 0
            edges[10].v[1] = 5
            edges[11].v[0] = 5
            edges[11].v[1] = 1
            edges[12].v[0] = 5
            edges[12].v[1] = 3

            // all edges of a polygon go counter clockwise
            polys[0].numEdges = 3
            polys[0].edges[0] = 1
            polys[0].edges[1] = 2
            polys[0].edges[2] = 3
            polys[1].numEdges = 3
            polys[1].edges[0] = -3
            polys[1].edges[1] = 4
            polys[1].edges[2] = 5
            polys[2].numEdges = 3
            polys[2].edges[0] = -5
            polys[2].edges[1] = 6
            polys[2].edges[2] = 7
            polys[3].numEdges = 3
            polys[3].edges[0] = -7
            polys[3].edges[1] = 8
            polys[3].edges[2] = -1
            polys[4].numEdges = 3
            polys[4].edges[0] = 9
            polys[4].edges[1] = -2
            polys[4].edges[2] = 10
            polys[5].numEdges = 3
            polys[5].edges[0] = 11
            polys[5].edges[1] = -4
            polys[5].edges[2] = -9
            polys[6].numEdges = 3
            polys[6].edges[0] = 12
            polys[6].edges[1] = -6
            polys[6].edges[2] = -11
            polys[7].numEdges = 3
            polys[7].edges[0] = -10
            polys[7].edges[1] = -8
            polys[7].edges[2] = -12

            // convex model
            isConvex = true
        }

        /*
         ============
         idTraceModel::InitDodecahedron

         Initialize size independent dodecahedron.
         ============
         */
        private fun InitDodecahedron() {
            type = traceModel_t.TRM_DODECAHEDRON
            numVerts = 20
            numEdges = 30
            numPolys = 12

            // set edges
            edges[1].v[0] = 0
            edges[1].v[1] = 8
            edges[2].v[0] = 8
            edges[2].v[1] = 9
            edges[3].v[0] = 9
            edges[3].v[1] = 4
            edges[4].v[0] = 4
            edges[4].v[1] = 16
            edges[5].v[0] = 16
            edges[5].v[1] = 0
            edges[6].v[0] = 16
            edges[6].v[1] = 17
            edges[7].v[0] = 17
            edges[7].v[1] = 2
            edges[8].v[0] = 2
            edges[8].v[1] = 12
            edges[9].v[0] = 12
            edges[9].v[1] = 0
            edges[10].v[0] = 2
            edges[10].v[1] = 10
            edges[11].v[0] = 10
            edges[11].v[1] = 3
            edges[12].v[0] = 3
            edges[12].v[1] = 13
            edges[13].v[0] = 13
            edges[13].v[1] = 12
            edges[14].v[0] = 9
            edges[14].v[1] = 5
            edges[15].v[0] = 5
            edges[15].v[1] = 15
            edges[16].v[0] = 15
            edges[16].v[1] = 14
            edges[17].v[0] = 14
            edges[17].v[1] = 4
            edges[18].v[0] = 3
            edges[18].v[1] = 19
            edges[19].v[0] = 19
            edges[19].v[1] = 18
            edges[20].v[0] = 18
            edges[20].v[1] = 1
            edges[21].v[0] = 1
            edges[21].v[1] = 13
            edges[22].v[0] = 7
            edges[22].v[1] = 11
            edges[23].v[0] = 11
            edges[23].v[1] = 6
            edges[24].v[0] = 6
            edges[24].v[1] = 14
            edges[25].v[0] = 15
            edges[25].v[1] = 7
            edges[26].v[0] = 1
            edges[26].v[1] = 8
            edges[27].v[0] = 18
            edges[27].v[1] = 5
            edges[28].v[0] = 6
            edges[28].v[1] = 17
            edges[29].v[0] = 11
            edges[29].v[1] = 10
            edges[30].v[0] = 19
            edges[30].v[1] = 7

            // all edges of a polygon go counter clockwise
            polys[0].numEdges = 5
            polys[0].edges[0] = 1
            polys[0].edges[1] = 2
            polys[0].edges[2] = 3
            polys[0].edges[3] = 4
            polys[0].edges[4] = 5
            polys[1].numEdges = 5
            polys[1].edges[0] = -5
            polys[1].edges[1] = 6
            polys[1].edges[2] = 7
            polys[1].edges[3] = 8
            polys[1].edges[4] = 9
            polys[2].numEdges = 5
            polys[2].edges[0] = -8
            polys[2].edges[1] = 10
            polys[2].edges[2] = 11
            polys[2].edges[3] = 12
            polys[2].edges[4] = 13
            polys[3].numEdges = 5
            polys[3].edges[0] = 14
            polys[3].edges[1] = 15
            polys[3].edges[2] = 16
            polys[3].edges[3] = 17
            polys[3].edges[4] = -3
            polys[4].numEdges = 5
            polys[4].edges[0] = 18
            polys[4].edges[1] = 19
            polys[4].edges[2] = 20
            polys[4].edges[3] = 21
            polys[4].edges[4] = -12
            polys[5].numEdges = 5
            polys[5].edges[0] = 22
            polys[5].edges[1] = 23
            polys[5].edges[2] = 24
            polys[5].edges[3] = -16
            polys[5].edges[4] = 25
            polys[6].numEdges = 5
            polys[6].edges[0] = -9
            polys[6].edges[1] = -13
            polys[6].edges[2] = -21
            polys[6].edges[3] = 26
            polys[6].edges[4] = -1
            polys[7].numEdges = 5
            polys[7].edges[0] = -26
            polys[7].edges[1] = -20
            polys[7].edges[2] = 27
            polys[7].edges[3] = -14
            polys[7].edges[4] = -2
            polys[8].numEdges = 5
            polys[8].edges[0] = -4
            polys[8].edges[1] = -17
            polys[8].edges[2] = -24
            polys[8].edges[3] = 28
            polys[8].edges[4] = -6
            polys[9].numEdges = 5
            polys[9].edges[0] = -23
            polys[9].edges[1] = 29
            polys[9].edges[2] = -10
            polys[9].edges[3] = -7
            polys[9].edges[4] = -28
            polys[10].numEdges = 5
            polys[10].edges[0] = -25
            polys[10].edges[1] = -15
            polys[10].edges[2] = -27
            polys[10].edges[3] = -19
            polys[10].edges[4] = 30
            polys[11].numEdges = 5
            polys[11].edges[0] = -30
            polys[11].edges[1] = -18
            polys[11].edges[2] = -11
            polys[11].edges[3] = -29
            polys[11].edges[4] = -22

            // convex model
            isConvex = true
        }

        /*
         ============
         idTraceModel::InitBone

         Initialize size independent bone.
         ============
         */
        private fun InitBone() {
            var i: Int
            type = traceModel_t.TRM_BONE
            numVerts = 5
            numEdges = 9
            numPolys = 6

            // set bone edges
            i = 0
            while (i < 3) {
                edges[i + 1].v[0] = 0
                edges[i + 1].v[1] = i + 1
                edges[i + 4].v[0] = 1 + i
                edges[i + 4].v[1] = 1 + (i + 1) % 3
                edges[i + 7].v[0] = i + 1
                edges[i + 7].v[1] = 4
                i++
            }

            // all edges of a polygon go counter clockwise
            polys[0].numEdges = 3
            polys[0].edges[0] = 2
            polys[0].edges[1] = -4
            polys[0].edges[2] = -1
            polys[1].numEdges = 3
            polys[1].edges[0] = 3
            polys[1].edges[1] = -5
            polys[1].edges[2] = -2
            polys[2].numEdges = 3
            polys[2].edges[0] = 1
            polys[2].edges[1] = -6
            polys[2].edges[2] = -3
            polys[3].numEdges = 3
            polys[3].edges[0] = 4
            polys[3].edges[1] = 8
            polys[3].edges[2] = -7
            polys[4].numEdges = 3
            polys[4].edges[0] = 5
            polys[4].edges[1] = 9
            polys[4].edges[2] = -8
            polys[5].numEdges = 3
            polys[5].edges[0] = 6
            polys[5].edges[1] = 7
            polys[5].edges[2] = -9

            // convex model
            isConvex = true
        }

        // FIX: was private with reference-copy for edges/polys.
        // C++ operator= does deep struct copy; Kotlin needs explicit element-wise copy.
        fun set(trm: idTraceModel) {
            type = trm.type
            numVerts = trm.numVerts
            for (i in 0 until numVerts) {
                verts[i].set(trm.verts[i])
            }
            numEdges = trm.numEdges
            for (i in 0..numEdges) {
                edges[i].set(trm.edges[i])
            }
            numPolys = trm.numPolys
            for (i in 0 until numPolys) {
                polys[i].set(trm.polys[i])
            }
            offset.set(trm.offset)
            bounds.set(trm.bounds)
            isConvex = trm.isConvex
        }

        private fun ProjectionIntegrals(polyNum: Int, a: Int, b: Int, integrals: projectionIntegrals_t) {
            val poly: traceModelPoly_t?
            var i: Int
            var edgeNum: Int
            val v1 = idVec3()
            val v2 = idVec3()
            var a0: Float
            var a1: Float
            var da: Float
            var b0: Float
            var b1: Float
            var db: Float
            var a0_2: Float
            var a0_3: Float
            var a0_4: Float
            var b0_2: Float
            var b0_3: Float
            var b0_4: Float
            var a1_2: Float
            var a1_3: Float
            var b1_2: Float
            var b1_3: Float
            var C1: Float
            var Ca: Float
            var Caa: Float
            var Caaa: Float
            var Cb: Float
            var Cbb: Float
            var Cbbb: Float
            var Cab: Float
            var Kab: Float
            var Caab: Float
            var Kaab: Float
            var Cabb: Float
            var Kabb: Float

//	memset(&integrals, 0, sizeof(projectionIntegrals_t));
            poly = polys[polyNum]
            i = 0
            while (i < poly.numEdges) {
                edgeNum = poly.edges[i]
                v1.set(verts[edges[abs(edgeNum)].v[if (edgeNum < 0) 1 else 0]])
                v2.set(verts[edges[abs(edgeNum)].v[if (edgeNum > 0) 1 else 0]])
                a0 = v1[a]
                b0 = v1[b]
                a1 = v2[a]
                b1 = v2[b]
                da = a1 - a0
                db = b1 - b0
                a0_2 = a0 * a0
                a0_3 = a0_2 * a0
                a0_4 = a0_3 * a0
                b0_2 = b0 * b0
                b0_3 = b0_2 * b0
                b0_4 = b0_3 * b0
                a1_2 = a1 * a1
                a1_3 = a1_2 * a1
                b1_2 = b1 * b1
                b1_3 = b1_2 * b1
                C1 = a1 + a0
                Ca = a1 * C1 + a0_2
                Caa = a1 * Ca + a0_3
                Caaa = a1 * Caa + a0_4
                Cb = b1 * (b1 + b0) + b0_2
                Cbb = b1 * Cb + b0_3
                Cbbb = b1 * Cbb + b0_4
                Cab = 3 * a1_2 + 2 * a1 * a0 + a0_2
                Kab = a1_2 + 2 * a1 * a0 + 3 * a0_2
                Caab = a0 * Cab + 4 * a1_3
                Kaab = a1 * Kab + 4 * a0_3
                Cabb = 4 * b1_3 + 3 * b1_2 * b0 + 2 * b1 * b0_2 + b0_3
                Kabb = b1_3 + 2 * b1_2 * b0 + 3 * b1 * b0_2 + 4 * b0_3
                integrals.P1 += db * C1
                integrals.Pa += db * Ca
                integrals.Paa += db * Caa
                integrals.Paaa += db * Caaa
                integrals.Pb += da * Cb
                integrals.Pbb += da * Cbb
                integrals.Pbbb += da * Cbbb
                integrals.Pab += db * (b1 * Cab + b0 * Kab)
                integrals.Paab += db * (b1 * Caab + b0 * Kaab)
                integrals.Pabb += da * (a1 * Cabb + a0 * Kabb)
                i++
            }
            integrals.P1 *= 1.0f / 2.0f
            integrals.Pa *= 1.0f / 6.0f
            integrals.Paa *= 1.0f / 12.0f
            integrals.Paaa *= 1.0f / 20.0f
            integrals.Pb *= 1.0f / -6.0f
            integrals.Pbb *= 1.0f / -12.0f
            integrals.Pbbb *= 1.0f / -20.0f
            integrals.Pab *= 1.0f / 24.0f
            integrals.Paab *= 1.0f / 60.0f
            integrals.Pabb *= 1.0f / -60.0f
        }

        private fun PolygonIntegrals(polyNum: Int, a: Int, b: Int, c: Int, integrals: polygonIntegrals_t) {
            val pi = projectionIntegrals_t()
            val n = idVec3()
            val w: Float
            val k1: Float
            val k2: Float
            val k3: Float
            val k4: Float
            ProjectionIntegrals(polyNum, a, b, pi)
            n.set(polys[polyNum].normal)
            w = -polys[polyNum].dist
            k1 = 1 / n[c]
            k2 = k1 * k1
            k3 = k2 * k1
            k4 = k3 * k1
            integrals.Fa = k1 * pi.Pa
            integrals.Fb = k1 * pi.Pb
            integrals.Fc = -k2 * (n[a] * pi.Pa + n[b] * pi.Pb + w * pi.P1)
            integrals.Faa = k1 * pi.Paa
            integrals.Fbb = k1 * pi.Pbb
            integrals.Fcc =
                k3 * (Square(n[a]) * pi.Paa + 2 * n[a] * n[b] * pi.Pab + Square(n[b]) * pi.Pbb + w * (2 * (n[a] * pi.Pa + n[b] * pi.Pb) + w * pi.P1))
            integrals.Faaa = k1 * pi.Paaa
            integrals.Fbbb = k1 * pi.Pbbb
            integrals.Fccc =
                -k4 * (Cube(n[a]) * pi.Paaa + 3 * Square(n[a]) * n[b] * pi.Paab + 3 * n[a] * Square(
                    n[b]
                ) * pi.Pabb + Cube(n[b]) * pi.Pbbb + 3 * w * (Square(
                    n[a]
                ) * pi.Paa + 2 * n[a] * n[b] * pi.Pab + Square(n[b]) * pi.Pbb) + w * w * (3 * (n[a] * pi.Pa + n[b] * pi.Pb) + w * pi.P1))
            integrals.Faab = k1 * pi.Paab
            integrals.Fbbc = -k2 * (n[a] * pi.Pabb + n[b] * pi.Pbbb + w * pi.Pbb)
            integrals.Fcca =
                k3 * (Square(n[a]) * pi.Paaa + 2 * n[a] * n[b] * pi.Paab + Square(n[b]) * pi.Pabb + w * (2 * (n[a] * pi.Paa + n[b] * pi.Pab) + w * pi.Pa))
        }

        private fun VolumeIntegrals(integrals: volumeIntegrals_t) {
            var poly: traceModelPoly_t
            val pi = polygonIntegrals_t()
            var i: Int
            var a: Int
            var b: Int
            var c: Int
            var nx: Float
            var ny: Float
            var nz: Float
            var T0 = 0.0f
            val T1 = FloatArray(3)
            val T2 = FloatArray(3)
            val TP = FloatArray(3)

//	memset( &integrals, 0, sizeof(volumeIntegrals_t) );
            i = 0
            while (i < numPolys) {
                poly = polys[i]
                nx = abs(poly.normal[0])
                ny = abs(poly.normal[1])
                nz = abs(poly.normal[2])
                c = if (nx > ny && nx > nz) {
                    0
                } else {
                    if (ny > nz) 1 else 2
                }
                a = (c + 1) % 3
                b = (a + 1) % 3
                PolygonIntegrals(i, a, b, c, pi)
                T0 += poly.normal[0] * if (a == 0) pi.Fa else if (b == 0) pi.Fb else pi.Fc
                T1[a] += poly.normal[a] * pi.Faa
                T1[b] += poly.normal[b] * pi.Fbb
                T1[c] += poly.normal[c] * pi.Fcc
                T2[a] += poly.normal[a] * pi.Faaa
                T2[b] += poly.normal[b] * pi.Fbbb
                T2[c] += poly.normal[c] * pi.Fccc
                TP[a] += poly.normal[a] * pi.Faab
                TP[b] += poly.normal[b] * pi.Fbbc
                TP[c] += poly.normal[c] * pi.Fcca
                i++
            }
            integrals.T0 = T0
            integrals.T1[0] = T1[0] * 0.5f
            integrals.T1[1] = T1[1] * 0.5f
            integrals.T1[2] = T1[2] * 0.5f
            integrals.T2[0] = T2[0] * (1.0f / 3.0f)
            integrals.T2[1] = T2[1] * (1.0f / 3.0f)
            integrals.T2[2] = T2[2] * (1.0f / 3.0f)
            integrals.TP[0] = TP[0] * 0.5f
            integrals.TP[1] = TP[1] * 0.5f
            integrals.TP[2] = TP[2] * 0.5f
        }

        private fun VolumeFromPolygon(trm: idTraceModel, thickness: Float) {
            var i: Int
            trm.set(this)
            trm.type = traceModel_t.TRM_POLYGONVOLUME
            trm.numVerts = numVerts * 2
            trm.numEdges = numEdges * 3
            trm.numPolys = numEdges + 2
            i = 0
            while (i < numEdges) {
                trm.verts[numVerts + i].set(verts[i] - polys[0].normal * thickness)
                trm.edges[numEdges + i + 1].v[0] = numVerts + i
                trm.edges[numEdges + i + 1].v[1] = numVerts + (i + 1) % numVerts
                trm.edges[numEdges * 2 + i + 1].v[0] = i
                trm.edges[numEdges * 2 + i + 1].v[1] = numVerts + i
                trm.polys[1].edges[i] = -(numEdges + i + 1)
                trm.polys[2 + i].numEdges = 4
                trm.polys[2 + i].edges[0] = -(i + 1)
                trm.polys[2 + i].edges[1] = numEdges * 2 + i + 1
                trm.polys[2 + i].edges[2] = numEdges + i + 1
                trm.polys[2 + i].edges[3] = -(numEdges * 2 + (i + 1) % numEdges + 1)
                trm.polys[2 + i].normal.set((verts[(i + 1) % numVerts] - verts[i]).Cross(polys[0].normal))
                trm.polys[2 + i].normal.Normalize()
                trm.polys[2 + i].dist = trm.polys[2 + i].normal * verts[i]
                i++
            }
            trm.polys[1].dist = trm.polys[1].normal * trm.verts[numEdges]
            trm.GenerateEdgeNormals()
        }

        private fun GetOrderedSilhouetteEdges(edgeIsSilEdge: IntArray, silEdges: IntArray): Int {
            var i: Int
            var j: Int
            var edgeNum: Int
            var numSilEdges: Int
            var nextSilVert: Int
            val unsortedSilEdges = IntArray(MAX_TRACEMODEL_EDGES)
            numSilEdges = 0
            i = 1
            while (i <= numEdges) {
                if (edgeIsSilEdge[i] != 0) {
                    unsortedSilEdges[numSilEdges++] = i
                }
                i++
            }
            silEdges[0] = unsortedSilEdges[0]
            unsortedSilEdges[0] = -1
            nextSilVert = edges[silEdges[0]].v[0]
            i = 1
            while (i < numSilEdges) {
                j = 1
                while (j < numSilEdges) {
                    edgeNum = unsortedSilEdges[j]
                    if (edgeNum >= 0) {
                        if (edges[edgeNum].v[0] == nextSilVert) {
                            nextSilVert = edges[edgeNum].v[1]
                            silEdges[i] = edgeNum
                            break
                        }
                        if (edges[edgeNum].v[1] == nextSilVert) {
                            nextSilVert = edges[edgeNum].v[0]
                            silEdges[i] = -edgeNum
                            break
                        }
                    }
                    j++
                }
                if (j >= numSilEdges) {
                    silEdges[i] = 1 // shouldn't happen
                }
                unsortedSilEdges[j] = -1
                i++
            }
            return numSilEdges
        }

        internal class projectionIntegrals_t {
            var P1 = 0.0f
            var Pa = 0.0f
            var Pb = 0.0f
            var Paa = 0.0f
            var Pab = 0.0f
            var Pbb = 0.0f
            var Paaa = 0.0f
            var Paab = 0.0f
            var Pabb = 0.0f
            var Pbbb = 0.0f
        }

        internal class polygonIntegrals_t {
            var Fa = 0.0f
            var Fb = 0.0f
            var Fc = 0.0f
            var Faa = 0.0f
            var Fbb = 0.0f
            var Fcc = 0.0f
            var Faaa = 0.0f
            var Fbbb = 0.0f
            var Fccc = 0.0f
            var Faab = 0.0f
            var Fbbc = 0.0f
            var Fcca = 0.0f
        }

        internal class volumeIntegrals_t {
            var T0 = 0.0f
            val T1: idVec3 = idVec3()
            val T2: idVec3 = idVec3()
            val TP: idVec3 = idVec3()
        }

        companion object {
            const val SHARP_EDGE_DOT = -0.7f
        }
    }
}
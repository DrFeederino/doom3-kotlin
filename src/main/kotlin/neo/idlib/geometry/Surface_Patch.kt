package neo.idlib.geometry

import neo.idlib.Dict_h.idDict
import neo.idlib.MapFile.idMapPatch
import neo.idlib.MapFile.idMapPrimitive
import neo.idlib.geometry.DrawVert.idDrawVert
import neo.idlib.geometry.Surface.idSurface
import neo.idlib.idException
import neo.idlib.idLib
import neo.idlib.math.Square
import neo.idlib.math.idVec3
import neo.idlib.math.vec3_origin
import kotlin.math.abs

class Surface_Patch {
    /*
     ===============================================================================

     Bezier patch surface.

     ===============================================================================
     */
    class idSurface_Patch : idSurface {
        //
        //
        //
        //
        //
        //
        //
        //
        val epairs: idDict = idDict()
        protected var expanded // true if vertices are spaced out
                = false
        protected var height // height of patch
                = 0
        protected var maxHeight // maximum height allocated for
                = 0

        //
        //
        protected var maxWidth // maximum width allocated for
                = 0
        protected var type = 0
        protected var width // width of patch
                = 0

        constructor() {
            maxWidth = 0
            maxHeight = maxWidth
            width = maxHeight
            height = width
            expanded = false
        }

        //public						~idSurface_Patch( void );
        //
        constructor(maxPatchWidth: Int, maxPatchHeight: Int) {
            height = 0
            width = height
            maxWidth = maxPatchWidth
            maxHeight = maxPatchHeight
            verts.SetNum(maxWidth * maxHeight)
            expanded = false
        }

        constructor(patch: idSurface_Patch) {
            this.set(patch)
        }

        constructor(patch: idMapPrimitive) {
            this.set(patch)
        }

        constructor(patch: idMapPatch) {
            maxWidth = patch.GetWidth()
            maxHeight = patch.GetHeight()
            width = maxWidth
            height = maxHeight
            expanded = false
            verts.SetNum(width * height)
            for (i in 0 until width * height) {
                verts[i] = idDrawVert(patch.GetVert(i))
            }
        }

        @Throws(Exception::class)
        fun SetSize(patchWidth: Int, patchHeight: Int) {
            if (patchWidth < 1 || patchWidth > maxWidth) {
                idLib.common.FatalError("idSurface_Patch::SetSize: invalid patchWidth")
            }
            if (patchHeight < 1 || patchHeight > maxHeight) {
                idLib.common.FatalError("idSurface_Patch::SetSize: invalid patchHeight")
            }
            width = patchWidth
            height = patchHeight
            verts.SetNum(width * height, false)
        }

        fun GetWidth(): Int {
            return width
        }

        fun GetHeight(): Int {
            return height
        }

        // subdivide the patch mesh based on error

        @Throws(idException::class)
        fun Subdivide(
            maxHorizontalError: Float, maxVerticalError: Float, maxLength: Float, genNormals: Boolean = false
        ) {
            var i: Int
            var j: Int
            var k: Int
            var l: Int
            val prev = idDrawVert()
            val next = idDrawVert()
            val mid = idDrawVert()
            val prevxyz = idVec3()
            val nextxyz = idVec3()
            val midxyz = idVec3()
            val delta = idVec3()
            val maxHorizontalErrorSqr: Float
            val maxVerticalErrorSqr: Float
            val maxLengthSqr: Float

            // generate normals for the control mesh
            if (genNormals) {
                GenerateNormals()
            }
            maxHorizontalErrorSqr = Square(maxHorizontalError)
            maxVerticalErrorSqr = Square(maxVerticalError)
            maxLengthSqr = Square(maxLength)
            Expand()

            // horizontal subdivisions
            j = 0
            while (j + 2 < width) {

                // check subdivided midpoints against control points
                i = 0
                while (i < height) {
                    l = 0
                    while (l < 3) {
                        prevxyz[l] = verts[i * maxWidth + j + 1].xyz[l] - verts[i * maxWidth + j].xyz[l]
                        nextxyz[l] = verts[i * maxWidth + j + 2].xyz[l] - verts[i * maxWidth + j + 1].xyz[l]
                        midxyz[l] =
                            (verts[i * maxWidth + j].xyz[l] + verts[i * maxWidth + j + 1].xyz[l] * 2.0f + verts[i * maxWidth + j + 2].xyz[l]) * 0.25f
                        l++
                    }
                    if (maxLength > 0.0f) { // if the span length is too long, force a subdivision
                        if (prevxyz.LengthSqr() > maxLengthSqr || nextxyz.LengthSqr() > maxLengthSqr) {
                            break
                        }
                    } // see if this midpoint is off far enough to subdivide
                    delta.set(verts[i * maxWidth + j + 1].xyz - midxyz)
                    if (delta.LengthSqr() > maxHorizontalErrorSqr) {
                        break
                    }
                    i++
                }
                if (i == height) {
                    j += 2
                    continue  // didn't need subdivision
                }
                if (width + 2 >= maxWidth) {
                    ResizeExpanded(maxHeight, maxWidth + 4)
                }

                // insert two columns and replace the peak
                width += 2
                i = 0
                while (i < height) {
                    LerpVert(verts[i * maxWidth + j], verts[i * maxWidth + j + 1], prev)
                    LerpVert(verts[i * maxWidth + j + 1], verts[i * maxWidth + j + 2], next)
                    LerpVert(prev, next, mid)
                    k = width - 1
                    while (k > j + 3) {
                        verts[i * maxWidth + k] = idDrawVert(verts[i * maxWidth + k - 2])
                        k--
                    }
                    verts[i * maxWidth + j + 1] = idDrawVert(prev)
                    verts[i * maxWidth + j + 2] = idDrawVert(mid)
                    verts[i * maxWidth + j + 3] = idDrawVert(next)
                    i++
                }

                // back up and recheck this set again, it may need more subdivision
                j -= 2
                j += 2
            }

            // vertical subdivisions
            j = 0
            while (j + 2 < height) {

                // check subdivided midpoints against control points
                i = 0
                while (i < width) {
                    l = 0
                    while (l < 3) {
                        prevxyz[l] = verts[(j + 1) * maxWidth + i].xyz[l] - verts[j * maxWidth + i].xyz[l]
                        nextxyz[l] = verts[(j + 2) * maxWidth + i].xyz[l] - verts[(j + 1) * maxWidth + i].xyz[l]
                        midxyz[l] =
                            (verts[j * maxWidth + i].xyz[l] + verts[(j + 1) * maxWidth + i].xyz[l] * 2.0f + verts[(j + 2) * maxWidth + i].xyz[l]) * 0.25f
                        l++
                    }
                    if (maxLength > 0.0f) { // if the span length is too long, force a subdivision
                        if (prevxyz.LengthSqr() > maxLengthSqr || nextxyz.LengthSqr() > maxLengthSqr) {
                            break
                        }
                    } // see if this midpoint is off far enough to subdivide
                    delta.set(verts[(j + 1) * maxWidth + i].xyz - midxyz)
                    if (delta.LengthSqr() > maxVerticalErrorSqr) {
                        break
                    }
                    i++
                }
                if (i == width) {
                    j += 2
                    continue  // didn't need subdivision
                }
                if (height + 2 >= maxHeight) {
                    ResizeExpanded(maxHeight + 4, maxWidth)
                }

                // insert two columns and replace the peak
                height += 2
                i = 0
                while (i < width) {
                    LerpVert(verts[j * maxWidth + i], verts[(j + 1) * maxWidth + i], prev)
                    LerpVert(verts[(j + 1) * maxWidth + i], verts[(j + 2) * maxWidth + i], next)
                    LerpVert(prev, next, mid)
                    k = height - 1
                    while (k > j + 3) {
                        verts[k * maxWidth + i] = idDrawVert(verts[(k - 2) * maxWidth + i])
                        k--
                    }
                    verts[(j + 1) * maxWidth + i] = idDrawVert(prev)
                    verts[(j + 2) * maxWidth + i] = idDrawVert(mid)
                    verts[(j + 3) * maxWidth + i] = idDrawVert(next)
                    i++
                }

                // back up and recheck this set again, it may need more subdivision
                j -= 2
                j += 2
            }
            PutOnCurve()
            RemoveLinearColumnsRows()
            Collapse()

            // normalize all the lerped normals
            if (genNormals) {
                i = 0
                while (i < width * height) {
                    verts[i].normal.Normalize()
                    i++
                }
            }
            GenerateIndexes()
        }

        // subdivide the patch up to an explicit number of horizontal and vertical subdivisions
        //						
        //		

        @Throws(idException::class)
        fun SubdivideExplicit(
            horzSubdivisions: Int, vertSubdivisions: Int, genNormals: Boolean, removeLinear: Boolean = false
        ) {
            var i: Int
            var j: Int
            var k: Int
            var l: Int
            val sample = Array(3) { Array(3) { idDrawVert() } }
            val outWidth = (width - 1) / 2 * horzSubdivisions + 1
            val outHeight = (height - 1) / 2 * vertSubdivisions + 1
            val dv = Array(outWidth * outHeight) { idDrawVert() }

            // generate normals for the control mesh
            if (genNormals) {
                GenerateNormals()
            }
            var baseCol = 0
            i = 0
            while (i + 2 < width) {
                var baseRow = 0
                j = 0
                while (j + 2 < height) {
                    k = 0
                    while (k < 3) {
                        l = 0
                        while (l < 3) {
                            sample[k][l] = verts[(j + l) * width + i + k]
                            l++
                        }
                        k++
                    }
                    SampleSinglePatch(sample, baseCol, baseRow, outWidth, horzSubdivisions, vertSubdivisions, dv)
                    baseRow += vertSubdivisions
                    j += 2
                }
                baseCol += horzSubdivisions
                i += 2
            }
            verts.SetNum(outWidth * outHeight)
            i = 0
            while (i < outWidth * outHeight) {
                verts[i] = dv[i]
                i++
            }

            //	delete[] dv;
            maxWidth = outWidth
            width = maxWidth
            maxHeight = outHeight
            height = maxHeight
            expanded = false
            if (removeLinear) {
                Expand()
                RemoveLinearColumnsRows()
                Collapse()
            }

            // normalize all the lerped normals
            if (genNormals) {
                i = 0
                while (i < width * height) {
                    verts[i].normal.Normalize()
                    i++
                }
            }
            GenerateIndexes()
        }

        /*
         =================
         idSurface_Patch::PutOnCurve

         Expects an expanded patch.
         =================
         */
        private fun PutOnCurve() { // put the approximation points on the curve
            var i: Int
            var j: Int
            val prev = idDrawVert()
            val next = idDrawVert()
            assert(expanded == true) // put all the approximating points on the curve
            i = 0
            while (i < width) {
                j = 1
                while (j < height) {
                    LerpVert(verts[j * maxWidth + i], verts[(j + 1) * maxWidth + i], prev)
                    LerpVert(verts[j * maxWidth + i], verts[(j - 1) * maxWidth + i], next)
                    LerpVert(prev, next, verts[j * maxWidth + i])
                    j += 2
                }
                i++
            }
            j = 0
            while (j < height) {
                i = 1
                while (i < width) {
                    LerpVert(verts[j * maxWidth + i], verts[j * maxWidth + i + 1], prev)
                    LerpVert(verts[j * maxWidth + i], verts[j * maxWidth + i - 1], next)
                    LerpVert(prev, next, verts[j * maxWidth + i])
                    i += 2
                }
                j++
            }
        }

        /*
         ================
         idSurface_Patch::RemoveLinearColumnsRows

         Expects an expanded patch.
         ================
         */
        private fun RemoveLinearColumnsRows() { // remove columns and rows with all points on one line{
            var i: Int
            var j: Int
            var k: Int
            var len: Float
            var maxLength: Float
            val proj = idVec3()
            val dir = idVec3()
            assert(expanded == true)
            j = 1
            while (j < width - 1) {
                maxLength = 0.0f
                i = 0
                while (i < height) {
                    ProjectPointOntoVector(
                        verts[i * maxWidth + j].xyz,
                        verts[i * maxWidth + j - 1].xyz,
                        verts[i * maxWidth + j + 1].xyz,
                        proj
                    )
                    dir.set(verts[i * maxWidth + j].xyz - proj)
                    len = dir.LengthSqr()
                    if (len > maxLength) {
                        maxLength = len
                    }
                    i++
                }
                if (maxLength < Square(0.2f)) {
                    width--
                    i = 0
                    while (i < height) {
                        k = j
                        while (k < width) {
                            verts[i * maxWidth + k] = idDrawVert(verts[i * maxWidth + k + 1])
                            k++
                        }
                        i++
                    }
                    j--
                }
                j++
            }
            j = 1
            while (j < height - 1) {
                maxLength = 0.0f
                i = 0
                while (i < width) {
                    ProjectPointOntoVector(
                        verts[j * maxWidth + i].xyz,
                        verts[(j - 1) * maxWidth + i].xyz,
                        verts[(j + 1) * maxWidth + i].xyz,
                        proj
                    )
                    dir.set(verts[j * maxWidth + i].xyz - proj)
                    len = dir.LengthSqr()
                    if (len > maxLength) {
                        maxLength = len
                    }
                    i++
                }
                if (maxLength < Square(0.2f)) {
                    height--
                    i = 0
                    while (i < width) {
                        k = j
                        while (k < height) {
                            verts[k * maxWidth + i] = idDrawVert(verts[(k + 1) * maxWidth + i])
                            k++
                        }
                        i++
                    }
                    j--
                }
                j++
            }
        }

        // resize verts buffer
        private fun ResizeExpanded(newHeight: Int, newWidth: Int) {
            var i: Int
            var j: Int
            assert(expanded == true)
            if (newHeight <= maxHeight && newWidth <= maxWidth) {
                return
            }
            if (newHeight * newWidth > maxHeight * maxWidth) {
                verts.SetNum(newHeight * newWidth)
            } // space out verts for new height and width
            j = maxHeight - 1
            while (j >= 0) {
                i = maxWidth - 1
                while (i >= 0) {
                    if (verts[j * maxWidth + i] == null) {
                        verts[j * maxWidth + i] = idDrawVert()
                    }
                    verts[j * newWidth + i] = idDrawVert(verts[j * maxWidth + i])
                    i--
                }
                j--
            }
            maxHeight = newHeight
            maxWidth = newWidth
        }

        // space points out over maxWidth * maxHeight buffer
        @Throws(idException::class)
        private fun Expand() {
            var i: Int
            var j: Int
            if (expanded) {
                idLib.common.FatalError("idSurface_Patch::Expand: patch alread expanded")
            }
            expanded = true
            verts.SetNum(maxWidth * maxHeight, false)
            if (width != maxWidth) {
                j = height - 1
                while (j >= 0) {
                    i = width - 1
                    while (i >= 0) {
                        verts[j * maxWidth + i].set(verts[j * width + i])
                        i--
                    }
                    j--
                }
            }
        }

        // move all points to the start of the verts buffer
        @Throws(idException::class)
        private fun Collapse() {
            var i: Int
            var j: Int
            if (!expanded) {
                idLib.common.FatalError("idSurface_Patch::Collapse: patch not expanded")
            }
            expanded = false
            if (width != maxWidth) {
                j = 0
                while (j < height) {
                    i = 0
                    while (i < width) {
                        verts[j * width + i].set(verts[j * maxWidth + i])
                        i++
                    }
                    j++
                }
            }
            verts.SetNum(width * height, false)
        }

        // project a point onto a vector to calculate maximum curve error
        private fun ProjectPointOntoVector(point: idVec3, vStart: idVec3, vEnd: idVec3, vProj: idVec3) {
            val pVec = idVec3()
            val vec = idVec3()
            pVec.set(point - vStart)
            vec.set(vEnd - vStart)
            vec.Normalize() // project onto the directional vector for this segment
            vProj.set(vStart + vec * (pVec * vec))
        }

        /*
         =================
         idSurface_Patch::GenerateNormals

         Handles all the complicated wrapping and degenerate cases
         Expects a Not expanded patch.
         =================
         */
        private fun GenerateNormals() { // generate normals
            var i: Int
            var j: Int
            var k: Int
            var dist: Int
            val norm = idVec3()
            val sum = idVec3()
            var count: Int
            val base = idVec3()
            val delta = idVec3()
            var x: Int
            var y: Int
            val around: Array<idVec3> = idVec3.generateArray(8)
            val temp = idVec3()
            val good = BooleanArray(8)
            var wrapWidth: Boolean
            var wrapHeight: Boolean
            val neighbors = arrayOf(
                intArrayOf(0, 1),
                intArrayOf(1, 1),
                intArrayOf(1, 0),
                intArrayOf(1, -1),
                intArrayOf(0, -1),
                intArrayOf(-1, -1),
                intArrayOf(-1, 0),
                intArrayOf(-1, 1)
            )
            assert(expanded == false)

            //
            // if all points are coplanar, set all normals to that plane
            //
            val extent: Array<idVec3> = idVec3.generateArray(3)
            val offset: Float
            extent[0].set(verts[width - 1].xyz - verts[0].xyz)
            extent[1].set(verts[(height - 1) * width + width - 1].xyz - verts[0].xyz)
            extent[2].set(verts[(height - 1) * width].xyz - verts[0].xyz)
            norm.set(extent[0].Cross(extent[1]))
            if (norm.LengthSqr() == 0.0f) {
                norm.set(extent[0].Cross(extent[2]))
                if (norm.LengthSqr() == 0.0f) {
                    norm.set(extent[1].Cross(extent[2]))
                }
            }

            // wrapped patched may not get a valid normal here
            if (norm.Normalize() != 0.0f) {
                offset = verts[0].xyz * norm
                i = 1
                while (i < width * height) {
                    val d = verts[i].xyz * norm
                    if (abs(d - offset) > COPLANAR_EPSILON) {
                        break
                    }
                    i++
                }
                if (i == width * height) { // all are coplanar
                    i = 0
                    while (i < width * height) {
                        verts[i].normal.set(norm)
                        i++
                    }
                    return
                }
            }

            // check for wrapped edge cases, which should smooth across themselves
            wrapWidth = false
            i = 0
            while (i < height) {
                delta.set(verts[i * width].xyz - verts[i * width + width - 1].xyz)
                if (delta.LengthSqr() > Square(1.0f)) {
                    break
                }
                i++
            }
            if (i == height) {
                wrapWidth = true
            }
            wrapHeight = false
            i = 0
            while (i < width) {
                delta.set(verts[i].xyz - verts[(height - 1) * width + i].xyz)
                if (delta.LengthSqr() > Square(1.0f)) {
                    break
                }
                i++
            }
            if (i == width) {
                wrapHeight = true
            }
            i = 0
            while (i < width) {
                j = 0
                while (j < height) {
                    count = 0
                    base.set(verts[j * width + i].xyz)
                    k = 0
                    while (k < 8) {
                        around[k].set(vec3_origin)
                        good[k] = false
                        dist = 1
                        while (dist <= 3) {
                            x = i + neighbors[k][0] * dist
                            y = j + neighbors[k][1] * dist
                            if (wrapWidth) {
                                if (x < 0) {
                                    x = width - 1 + x
                                } else if (x >= width) {
                                    x = 1 + x - width
                                }
                            }
                            if (wrapHeight) {
                                if (y < 0) {
                                    y = height - 1 + y
                                } else if (y >= height) {
                                    y = 1 + y - height
                                }
                            }
                            if (x < 0 || x >= width || y < 0 || y >= height) {
                                break // edge of patch
                            }
                            temp.set(verts[y * width + x].xyz - base)
                            if (temp.Normalize() == 0.0f) {
                                dist++
                                continue  // degenerate edge, get more dist
                            } else {
                                good[k] = true
                                around[k].set(temp)
                                break // good edge
                            }
                            dist++
                        }
                        k++
                    }
                    sum.set(vec3_origin)
                    k = 0
                    while (k < 8) {
                        if (!good[k] || !good[k + 1 and 7]) {
                            k++
                            continue  // didn't get two points
                        }
                        norm.set(around[k + 1 and 7].Cross(around[k]))
                        if (norm.Normalize() == 0.0f) {
                            k++
                            continue
                        }
                        sum.plusAssign(norm)
                        count++
                        k++
                    }
                    if (count == 0) { //idLib::common->Printf("bad normal\n");
                        count = 1
                    }
                    verts[j * width + i].normal.set(sum)
                    verts[j * width + i].normal.Normalize()
                    j++
                }
                i++
            }
        }

        // generate triangle indexes
        private fun GenerateIndexes() {
            var i: Int
            var j: Int
            var v1: Int
            var v2: Int
            var v3: Int
            var v4: Int
            var index: Int
            indexes.SetNum((width - 1) * (height - 1) * 2 * 3, false)
            index = 0
            i = 0
            while (i < width - 1) {
                j = 0
                while (j < height - 1) {
                    v1 = j * width + i
                    v2 = v1 + 1
                    v3 = v1 + width + 1
                    v4 = v1 + width
                    indexes[index++] = v1
                    indexes[index++] = v3
                    indexes[index++] = v2
                    indexes[index++] = v1
                    indexes[index++] = v4
                    indexes[index++] = v3
                    j++
                }
                i++
            }
            GenerateEdgeIndexes()
        }

        // lerp point from two patch point
        private fun LerpVert(a: idDrawVert, b: idDrawVert, out: idDrawVert) {
            out.xyz[0] = 0.5f * (a.xyz[0] + b.xyz[0])
            out.xyz[1] = 0.5f * (a.xyz[1] + b.xyz[1])
            out.xyz[2] = 0.5f * (a.xyz[2] + b.xyz[2])
            out.normal[0] = 0.5f * (a.normal[0] + b.normal[0])
            out.normal[1] = 0.5f * (a.normal[1] + b.normal[1])
            out.normal[2] = 0.5f * (a.normal[2] + b.normal[2])
            out.st[0] = 0.5f * (a.st[0] + b.st[0])
            out.st[1] = 0.5f * (a.st[1] + b.st[1])
        }

        // sample a single 3x3 patch
        private fun SampleSinglePatchPoint(ctrl: Array<Array<idDrawVert>>, u: Float, v: Float, out: idDrawVert) {
            val vCtrl = Array<FloatArray>(3) { FloatArray(8) }
            var vPoint: Int
            var axis: Int

            // find the control points for the v coordinate
            vPoint = 0
            while (vPoint < 3) {
                axis = 0
                while (axis < 8) {
                    var a: Float
                    var b: Float
                    var c: Float
                    var qA: Float
                    var qB: Float
                    var qC: Float
                    if (axis < 3) {
                        a = ctrl[0][vPoint].xyz[axis]
                        b = ctrl[1][vPoint].xyz[axis]
                        c = ctrl[2][vPoint].xyz[axis]
                    } else if (axis < 6) {
                        a = ctrl[0][vPoint].normal[axis - 3]
                        b = ctrl[1][vPoint].normal[axis - 3]
                        c = ctrl[2][vPoint].normal[axis - 3]
                    } else {
                        a = ctrl[0][vPoint].st[axis - 6]
                        b = ctrl[1][vPoint].st[axis - 6]
                        c = ctrl[2][vPoint].st[axis - 6]
                    }
                    qA = a - 2.0f * b + c
                    qB = 2.0f * b - 2.0f * a
                    qC = a
                    vCtrl[vPoint][axis] = qA * u * u + qB * u + qC
                    axis++
                }
                vPoint++
            }

            // interpolate the v value
            axis = 0
            while (axis < 8) {
                var a: Float
                var b: Float
                var c: Float
                var qA: Float
                var qB: Float
                var qC: Float
                a = vCtrl[0][axis]
                b = vCtrl[1][axis]
                c = vCtrl[2][axis]
                qA = a - 2.0f * b + c
                qB = 2.0f * b - 2.0f * a
                qC = a
                if (axis < 3) {
                    out.xyz[axis] = qA * v * v + qB * v + qC
                } else if (axis < 6) {
                    out.normal[axis - 3] = qA * v * v + qB * v + qC
                } else {
                    out.st[axis - 6] = qA * v * v + qB * v + qC
                }
                axis++
            }
        }

        private fun SampleSinglePatch(
            ctrl: Array<Array<idDrawVert>>,
            baseCol: Int,
            baseRow: Int,
            width: Int,
            horzSub: Int,
            vertSub: Int,
            outVerts: Array<idDrawVert>
        ) {
            var horzSub = horzSub
            var vertSub = vertSub
            var i: Int
            var j: Int
            var u: Float
            var v: Float
            horzSub++
            vertSub++
            i = 0
            while (i < horzSub) {
                j = 0
                while (j < vertSub) {
                    u = i.toFloat() / (horzSub - 1)
                    v = j.toFloat() / (vertSub - 1)
                    SampleSinglePatchPoint(ctrl, u, v, outVerts[(baseRow + j) * width + i + baseCol])
                    j++
                }
                i++
            }
        }

        private fun set(patch: idSurface_Patch) {
            width = patch.width
            height = patch.height
            maxWidth = patch.maxWidth
            maxHeight = patch.maxHeight
            verts.SetNum(maxWidth * maxHeight)
            expanded = patch.expanded
        }

        private fun set(patch: idMapPrimitive) {
            type = patch.GetType()
            epairs.set(patch.epairs)
        }

        companion object {
            //
            const val COPLANAR_EPSILON = 0.1f
        }
    }
}
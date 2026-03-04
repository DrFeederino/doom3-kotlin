/*
===========================================================================

Doom 3 GPL Source Code
Copyright (C) 1999-2011 id Software LLC, a ZeniMax Media company.

This file is part of the Doom 3 GPL Source Code ("Doom 3 Source Code").

Doom 3 Source Code is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

Doom 3 Source Code is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with Doom 3 Source Code.  If not, see <http://www.gnu.org/licenses/>.

Translated to Kotlin by Dr. Feederino with support of Claude Code.

===========================================================================
*/
package neo.Renderer

import neo.framework.Common
import neo.framework.Session
import neo.idlib.*
import neo.idlib.BV.idBounds
import neo.idlib.containers.CFloat
import neo.idlib.containers.List.cmp_t
import neo.idlib.math.*
import neo.idlib.math.Matrix.idMat3
import neo.idlib.math.Random.idRandom
import neo.idlib.math.idMath.FtoiFast
import neo.idlib.math.idMath.SinCos
import java.nio.FloatBuffer
import java.util.*
import kotlin.math.tan

object tr_main {
    //====================================================================
    //=====================================================
    val MEMORY_BLOCK_SIZE: Int = 0x100000

    /*
     ======================
     R_ShowColoredScreenRect
     ======================
     */
    val colors /*[]*/: Array<idVec4> = arrayOf(
        colorRed,
        colorGreen,
        colorBlue,
        colorYellow,
        colorMagenta,
        colorCyan,
        colorWhite,
        colorPurple
    )

    /*
     =================
     R_SetViewMatrix

     Sets up the world to view matrix for a given viewParm
     =================
     */
    private val s_flipMatrix /*[16]*/: FloatArray =
        floatArrayOf( // convert from our coordinate system (looking down X)
            // to OpenGL's coordinate system (looking down -Z)
            -0.0f, 0.0f, -1.0f, 0.0f,
            -1.0f, 0.0f, -0.0f, 0.0f,
            -0.0f, 1.0f, -0.0f, 0.0f,
            -0.0f, 0.0f, -0.0f, 1.0f
        )

    /*
     ================
     R_RenderView

     A view may be either the actual camera view,
     a mirror / remote location, or a 3D view on a gui surface.

     Parms will typically be allocated with R_FrameAlloc
     ================
     */
    var DEBUG_R_RenderView: Int = 0

    /*
     =================
     R_CornerCullLocalBox

     Tests all corners against the frustum.
     Can still generate a few false positives when the box is outside a corner.
     Returns true if the box is outside the given global frustum, (positive sides are out)
     =================
     */
    private var DBG_R_CornerCullLocalBox: Int = 0

    /*
     ===============
     R_SetupProjection

     This uses the "infinite far z" trick
     ===============
     */
    private val random: idRandom = idRandom()

    /*
     ======================
     R_ScreenRectFromViewFrustumBounds
     ======================
     */
    fun R_ScreenRectFromViewFrustumBounds(bounds: idBounds): idScreenRect {
        val screenRect = idScreenRect()
        screenRect.x1 =
            FtoiFast(0.5f * (1.0f - bounds[1].y) * (tr.viewDef!!.viewport.x2 - tr.viewDef!!.viewport.x1))
        screenRect.x2 =
            FtoiFast(0.5f * (1.0f - bounds[0].y) * (tr.viewDef!!.viewport.x2 - tr.viewDef!!.viewport.x1))
        screenRect.y1 =
            FtoiFast(0.5f * (1.0f + bounds[0].z) * (tr.viewDef!!.viewport.y2 - tr.viewDef!!.viewport.y1))
        screenRect.y2 =
            FtoiFast(0.5f * (1.0f + bounds[1].z) * (tr.viewDef!!.viewport.y2 - tr.viewDef!!.viewport.y1))
        if (r_useDepthBoundsTest!!.GetInteger() != 0) {
            val zmin = CFloat(screenRect.zmin)
            val zmax = CFloat(screenRect.zmax)
            R_TransformEyeZToWin(-bounds[0].x, tr.viewDef!!.projectionMatrix, zmin)
            R_TransformEyeZToWin(-bounds[1].x, tr.viewDef!!.projectionMatrix, zmax)
            screenRect.zmin = zmin._val
            screenRect.zmax = zmax._val
        }
        return screenRect
    }

    fun R_ShowColoredScreenRect(rect: idScreenRect, colorIndex: Int) {
        if (!rect.IsEmpty()) {
            tr.viewDef!!.renderWorld!!.DebugScreenRect(
                colors[colorIndex and 7],
                rect,
                tr.viewDef!!
            )
        }
    }

    /*
     ====================
     R_ToggleSmpFrame
     ====================
     */
    fun R_ToggleSmpFrame() {
        R_FreeDeferredTriSurfs(frameData)

        // clear frame-temporary data
        var block: frameMemoryBlock_s?

        // update the highwater mark
        R_CountFrameData()
        val frame: frameData_t = frameData!!

        // reset the memory allocation to the first block
        frame.alloc = frame.memory

        // clear all the blocks
        block = frame.memory
        while (block != null) {
            block.used = 0
            block = block.next
        }
        RenderSystem.R_ClearCommandChain()
    }

    /*
     =====================
     R_ShutdownFrameData
     =====================
     */
    fun R_ShutdownFrameData() {
        var block: frameMemoryBlock_s?

        // free any current data
        var frame: frameData_t? = frameData
        if (null == frame) {
            return
        }
        R_FreeDeferredTriSurfs(frame)
        var nextBlock: frameMemoryBlock_s?
        block = frame.memory
        while (block != null) {
            nextBlock = block.next
            block = nextBlock
        }
        frame = null
        frameData = null
    }

    /*
     =====================
     R_InitFrameData
     =====================
     */
    fun R_InitFrameData() {
        val block: frameMemoryBlock_s?
        R_ShutdownFrameData()
        frameData = frameData_t()
        val frame: frameData_t = frameData!!
        val size: Int = MEMORY_BLOCK_SIZE
        block = frameMemoryBlock_s()
        if (null == block) {
            Common.common.FatalError("R_InitFrameData: Mem_Alloc() failed")
        }
        block.size = size
        block.used = 0
        block.next = null
        frame.memory = block
        frame.memoryHighwater = 0
        R_ToggleSmpFrame()
    }

    /*
     ================
     R_CountFrameData
     ================
     */
    @Deprecated("")
    fun R_CountFrameData(): Int {
        var block: frameMemoryBlock_s?
        var count = 0
        val frame: frameData_t = frameData!!
        block = frame.memory
        while (block != null) {
            count += block.used
            if (block === frame.alloc) {
                break
            }
            block = block.next
        }

        // note if this is a new highwater mark
        if (count > frame.memoryHighwater) {
            frame.memoryHighwater = count
        }
        return count
    }

    /*
     =================
     R_StaticAlloc
     =================
     */
    @Deprecated("")
    fun R_StaticAlloc(bytes: Int): Any {
        throw UnsupportedOperationException()
    }

    /*
     =================
     R_StaticFree
     =================
     */
    @Deprecated("")
    fun R_StaticFree(data: Any?) {
        throw UnsupportedOperationException()
    }

    /*
     ================
     R_FrameAlloc

     This data will be automatically freed when the
     current frame's back end completes.

     This should only be called by the front end.  The
     back end shouldn't need to allocate memory.

     If we passed smpFrame in, the back end could
     alloc memory, because it will always be a
     different frameData than the front end is using.

     All temporary data, like dynamic tesselations
     and local spaces are allocated here.

     The memory will not move, but it may not be
     contiguous with previous allocations even
     from this frame.

     The memory is NOT zero filled.
     Should part of this be inlined in a macro?
     ================
     */
    @Deprecated("")
    fun R_FrameAlloc(bytes: Int): Any {
        throw UnsupportedOperationException()
    }

    /*
     ==================
     R_ClearedFrameAlloc
     ==================
     */
    @Deprecated("")
    fun R_ClearedFrameAlloc(bytes: Int): Any {
        throw UnsupportedOperationException()
    }

    /*
     ==================
     R_FrameFree

     This does nothing at all, as the frame data is reused every frame
     and can only be stack allocated.

     The only reason for it's existance is so functions that can
     use either static or frame memory can set function pointers
     to both alloc and free.
     ==================
     */
    fun R_FrameFree(data: Any?) {}

    //==========================================================================
    fun R_AxisToModelMatrix(axis: idMat3, origin: idVec3, modelMatrix: FloatArray /*[16]*/) {
        modelMatrix[0] = axis[0][0]
        modelMatrix[4] = axis[1][0]
        modelMatrix[8] = axis[2][0]
        modelMatrix[12] = origin[0]

        modelMatrix[1] = axis[0][1]
        modelMatrix[5] = axis[1][1]
        modelMatrix[9] = axis[2][1]
        modelMatrix[13] = origin[1]

        modelMatrix[2] = axis[0][2]
        modelMatrix[6] = axis[1][2]
        modelMatrix[10] = axis[2][2]
        modelMatrix[14] = origin[2]

        modelMatrix[3] = 0.0f
        modelMatrix[7] = 0.0f
        modelMatrix[11] = 0.0f
        modelMatrix[15] = 1.0f
    }

    // FIXME: these assume no skewing or scaling transforms
    fun R_LocalPointToGlobal(modelMatrix: FloatArray /*[16]*/, `in`: idVec3): idVec3 {
        val out = idVec3()
        out.set(
            idVec3(
                ((`in`[0] * modelMatrix[0]) + (`in`[1] * modelMatrix[4]) + (`in`[2] * modelMatrix[8]) + modelMatrix[12]),
                ((`in`[0] * modelMatrix[1]) + (`in`[1] * modelMatrix[5]) + (`in`[2] * modelMatrix[9]) + modelMatrix[13]),
                ((`in`[0] * modelMatrix[2]) + (`in`[1] * modelMatrix[6]) + (`in`[2] * modelMatrix[10]) + modelMatrix[14])
            )
        )
        return out
    }

    fun R_PointTimesMatrix(modelMatrix: FloatArray /*[16]*/, `in`: idVec4, out: idVec4) {
        out[0] = (`in`[0] * modelMatrix[0]) + (`in`[1] * modelMatrix[4]) + (`in`[2] * modelMatrix[8]) + modelMatrix[12]
        out[1] = (`in`[0] * modelMatrix[1]) + (`in`[1] * modelMatrix[5]) + (`in`[2] * modelMatrix[9]) + modelMatrix[13]
        out[2] = (`in`[0] * modelMatrix[2]) + (`in`[1] * modelMatrix[6]) + (`in`[2] * modelMatrix[10]) + modelMatrix[14]
        out[3] = (`in`[0] * modelMatrix[3]) + (`in`[1] * modelMatrix[7]) + (`in`[2] * modelMatrix[11]) + modelMatrix[15]
    }

    fun R_GlobalPointToLocal(modelMatrix: FloatArray? /*[16]*/, `in`: idVec3, out: idVec<*>) {
        val temp = FloatArray(4)
        VectorSubtract(`in`.ToFloatPtr(), Arrays.copyOfRange(modelMatrix, 12, 16), temp)
        out[0] = DotProduct(temp, (modelMatrix)!!)
        out[1] = DotProduct(temp, modelMatrix.copyOfRange(4, 8))
        out[2] = DotProduct(temp, modelMatrix.copyOfRange(8, 12))
    }

    fun R_GlobalPointToLocal(modelMatrix: FloatArray? /*[16]*/, `in`: idVec3, out: FloatArray) {
        val temp = FloatArray(4)
        VectorSubtract(`in`.ToFloatPtr(), Arrays.copyOfRange(modelMatrix, 12, 16), temp)
        out[0] = DotProduct(temp, (modelMatrix)!!)
        out[1] = DotProduct(temp, Arrays.copyOfRange(modelMatrix, 4, 8))
        out[2] = DotProduct(temp, Arrays.copyOfRange(modelMatrix, 8, 12))
    }

    fun R_GlobalPointToLocal(modelMatrix: FloatArray? /*[16]*/, `in`: idVec3, out: FloatBuffer) {
        val temp = FloatArray(4)
        VectorSubtract(`in`.ToFloatPtr(), Arrays.copyOfRange(modelMatrix, 12, 16), temp)
        out.put(0, DotProduct(temp, (modelMatrix)!!))
        out.put(1, DotProduct(temp, Arrays.copyOfRange(modelMatrix, 4, 8)))
        out.put(2, DotProduct(temp, Arrays.copyOfRange(modelMatrix, 8, 12)))
    }

    fun R_LocalVectorToGlobal(modelMatrix: FloatArray /*[16]*/, `in`: idVec3, out: idVec3) {
        out[0] = (`in`[0] * modelMatrix[0]) + (`in`[1] * modelMatrix[4]) + (`in`[2] * modelMatrix[8])
        out[1] = (`in`[0] * modelMatrix[1]) + (`in`[1] * modelMatrix[5]) + (`in`[2] * modelMatrix[9])
        out[2] = (`in`[0] * modelMatrix[2]) + (`in`[1] * modelMatrix[6]) + (`in`[2] * modelMatrix[10])
    }

    fun R_GlobalVectorToLocal(modelMatrix: FloatArray? /*[16]*/, `in`: idVec3, out: idVec3) {
        out[0] = DotProduct(`in`.ToFloatPtr(), (modelMatrix)!!)
        out[1] = DotProduct(`in`.ToFloatPtr(), Arrays.copyOfRange(modelMatrix, 4, 8))
        out[2] = DotProduct(`in`.ToFloatPtr(), Arrays.copyOfRange(modelMatrix, 8, 12))
    }

    fun R_GlobalPlaneToLocal(modelMatrix: FloatArray /*[16]*/, `in`: idPlane, out: idPlane) {
        out[0] = DotProduct(`in`.ToFloatPtr(), modelMatrix)
        out[1] = DotProduct(`in`.ToFloatPtr(), Arrays.copyOfRange(modelMatrix, 4, 8))
        out[2] = DotProduct(`in`.ToFloatPtr(), Arrays.copyOfRange(modelMatrix, 8, 12))
        out[3] = `in`[3] + (modelMatrix[12] * `in`[0]) + (modelMatrix[13] * `in`[1]) + (modelMatrix[14] * `in`[2])
    }

    fun R_LocalPlaneToGlobal(modelMatrix: FloatArray /*[16]*/, `in`: idPlane, out: idPlane) {
        R_LocalVectorToGlobal(modelMatrix, `in`.Normal(), out.Normal())
        val offset: Float = (modelMatrix[12] * out[0]) + (modelMatrix[13] * out[1]) + (modelMatrix[14] * out[2])
        out[3] = `in`[3] - offset
    }

    // transform Z in eye coordinates to window coordinates
    fun R_TransformEyeZToWin(src_z: Float, projectionMatrix: FloatArray, dst_z: CFloat) {

        // projection
        val clip_z: Float = src_z * projectionMatrix[2 + 2 * 4] + projectionMatrix[2 + 3 * 4]
        val clip_w: Float = src_z * projectionMatrix[3 + 2 * 4] + projectionMatrix[3 + 3 * 4]
        if (clip_w <= 0.0f) {
            dst_z._val = 0.0f // clamp to near plane
        } else {
            dst_z._val = clip_z / clip_w
            dst_z._val = dst_z._val * 0.5f + 0.5f // convert to window coords
        }
    }

    /*
     =================
     R_RadiusCullLocalBox

     A fast, conservative center-to-corner culling test
     Returns true if the box is outside the given global frustum, (positive sides are out)
     =================
     */
    fun R_RadiusCullLocalBox(
        bounds: idBounds,
        modelMatrix: FloatArray? /*[16]*/,
        numPlanes: Int,
        planes: Array<idPlane>
    ): Boolean {
        var d: Float
        val worldOrigin = idVec3()
        val worldRadius: Float
        var frust: idPlane
        if (r_useCulling!!.GetInteger() == 0) {
            return false
        }

        // transform the surface bounds into world space
        val localOrigin = idVec3((bounds[0].plus(bounds[1])).times(0.5f))
        worldOrigin.set(R_LocalPointToGlobal(modelMatrix!!, localOrigin))
        worldRadius = (bounds[0].minus(localOrigin)).Length() // FIXME: won't be correct for scaled objects
        var i = 0
        while (i < numPlanes) {
            frust = planes[i]
            d = frust.Distance(worldOrigin)
            if (d > worldRadius) {
                return true // culled
            }
            i++
        }
        return false // not culled
    }

    fun R_CornerCullLocalBox(
        bounds: idBounds,
        modelMatrix: FloatArray? /*[16]*/,
        numPlanes: Int,
        planes: Array<idPlane>
    ): Boolean {
        var j: Int
        val transformed: Array<idVec3> = idVec3.generateArray(8)
        val dists = FloatArray(8)
        val v = idVec3()
        var frust: idPlane
        DBG_R_CornerCullLocalBox++

        // we can disable box culling for experimental timing purposes
        if (r_useCulling!!.GetInteger() < 2) {
            return false
        }

        // transform into world space
        var i = 0
        while (i < 8) {
            v[0] = bounds[(i shr 0) and 1, 0]
            v[1] = bounds[(i shr 1) and 1, 1]
            v[2] = bounds[(i shr 2) and 1, 2]
            transformed[i].set(R_LocalPointToGlobal(modelMatrix!!, v))
            i++
        }

        // check against frustum planes
        i = 0
        while (i < numPlanes) {
            frust = planes[i]
            j = 0
            while (j < 8) {
                dists[j] = frust.Distance(transformed[j])
                if (dists[j] < 0) {
                    break
                }
                j++
            }
            if (j == 8) {
                // all points were behind one of the planes
                tr.pc!!.c_box_cull_out++
                return true
            }
            i++
        }
        tr.pc!!.c_box_cull_in++
        return false // not culled
    }

    /*
     =================
     R_CullLocalBox

     Performs quick test before expensive test
     Returns true if the box is outside the given global frustum, (positive sides are out)
     =================
     */
    fun R_CullLocalBox(
        bounds: idBounds,
        modelMatrix: FloatArray? /*[16]*/,
        numPlanes: Int,
        planes: Array<idPlane?>?
    ): Boolean {
        if (R_RadiusCullLocalBox(bounds, modelMatrix, numPlanes, planes as Array<idPlane>)) {
            return true
        }
        return R_CornerCullLocalBox(bounds, modelMatrix, numPlanes, planes as Array<idPlane>)
    }

    /*
     ==========================
     R_TransformModelToClip
     ==========================
     */
    fun R_TransformModelToClip(
        src: idVec3,
        modelMatrix: FloatArray,
        projectionMatrix: FloatArray,
        eye: idPlane,
        dst: idPlane
    ) {
        var i = 0
        while (i < 4) {
            eye[i] = (src[0] * modelMatrix[i + 0 * 4]
                    ) + (src[1] * modelMatrix[i + 1 * 4]
                    ) + (src[2] * modelMatrix[i + 2 * 4]
                    ) + (1 * modelMatrix[i + 3 * 4])
            i++
        }
        i = 0
        while (i < 4) {
            dst[i] = (eye[0] * projectionMatrix[i + 0 * 4]
                    ) + (eye[1] * projectionMatrix[i + 1 * 4]
                    ) + (eye[2] * projectionMatrix[i + 2 * 4]
                    ) + (eye[3] * projectionMatrix[i + 3 * 4])
            i++
        }
    }

    /*
     ==========================
     R_GlobalToNormalizedDeviceCoordinates

     -1 to 1 range in x, y, and z
     ==========================
     */
    fun R_GlobalToNormalizedDeviceCoordinates(global: idVec3, ndc: idVec3) {
        var i: Int
        val view = idPlane()
        val clip = idPlane()

        // _D3XP added work on primaryView when no viewDef
        if (null == tr.viewDef) {
            i = 0
            while (i < 4) {
                view[i] = (global[0] * tr.primaryView!!.worldSpace.modelViewMatrix[i + 0 * 4]
                        ) + (global[1] * tr.primaryView!!.worldSpace.modelViewMatrix[i + 1 * 4]
                        ) + (global[2] * tr.primaryView!!.worldSpace.modelViewMatrix[i + 2 * 4]
                        ) + tr.primaryView!!.worldSpace.modelViewMatrix[i + 3 * 4]
                i++
            }
            i = 0
            while (i < 4) {
                clip[i] = (view[0] * tr.primaryView!!.projectionMatrix[i + 0 * 4]
                        ) + (view[1] * tr.primaryView!!.projectionMatrix[i + 1 * 4]
                        ) + (view[2] * tr.primaryView!!.projectionMatrix[i + 2 * 4]
                        ) + (view[3] * tr.primaryView!!.projectionMatrix[i + 3 * 4])
                i++
            }
        } else {
            i = 0
            while (i < 4) {
                view[i] = (global[0] * tr.viewDef!!.worldSpace.modelViewMatrix[i + 0 * 4]
                        ) + (global[1] * tr.viewDef!!.worldSpace.modelViewMatrix[i + 1 * 4]
                        ) + (global[2] * tr.viewDef!!.worldSpace.modelViewMatrix[i + 2 * 4]
                        ) + tr.viewDef!!.worldSpace.modelViewMatrix[i + 3 * 4]
                i++
            }
            i = 0
            while (i < 4) {
                clip[i] = (view[0] * tr.viewDef!!.projectionMatrix[i + 0 * 4]
                        ) + (view[1] * tr.viewDef!!.projectionMatrix[i + 1 * 4]
                        ) + (view[2] * tr.viewDef!!.projectionMatrix[i + 2 * 4]
                        ) + (view[3] * tr.viewDef!!.projectionMatrix[i + 3 * 4])
                i++
            }
        }
        ndc[0] = clip[0] / clip[3]
        ndc[1] = clip[1] / clip[3]
        ndc[2] = (clip[2] + clip[3]) / (2 * clip[3])
    }

    /*
     ==========================
     R_TransformClipToDevice

     Clip to normalized device coordinates
     ==========================
     */
    fun R_TransformClipToDevice(clip: idPlane, view: viewDef_s?, normalized: idVec3) {
        normalized[0] = clip[0] / clip[3]
        normalized[1] = clip[1] / clip[3]
        normalized[2] = clip[2] / clip[3]
    }

    /*
     ==========================
     myGlMultMatrix
     ==========================
     */
    fun myGlMultMatrix(a: FloatArray /*[16]*/, b: FloatArray /*[16]*/, out: FloatArray /*[16]*/) {
        out[0 * 4 + 0] =
            a[0 * 4 + 0] * b[0 * 4 + 0] + a[0 * 4 + 1] * b[1 * 4 + 0] + a[0 * 4 + 2] * b[2 * 4 + 0] + a[0 * 4 + 3] * b[3 * 4 + 0]
        out[0 * 4 + 1] =
            a[0 * 4 + 0] * b[0 * 4 + 1] + a[0 * 4 + 1] * b[1 * 4 + 1] + a[0 * 4 + 2] * b[2 * 4 + 1] + a[0 * 4 + 3] * b[3 * 4 + 1]
        out[0 * 4 + 2] =
            a[0 * 4 + 0] * b[0 * 4 + 2] + a[0 * 4 + 1] * b[1 * 4 + 2] + a[0 * 4 + 2] * b[2 * 4 + 2] + a[0 * 4 + 3] * b[3 * 4 + 2]
        out[0 * 4 + 3] =
            a[0 * 4 + 0] * b[0 * 4 + 3] + a[0 * 4 + 1] * b[1 * 4 + 3] + a[0 * 4 + 2] * b[2 * 4 + 3] + a[0 * 4 + 3] * b[3 * 4 + 3]
        out[1 * 4 + 0] =
            a[1 * 4 + 0] * b[0 * 4 + 0] + a[1 * 4 + 1] * b[1 * 4 + 0] + a[1 * 4 + 2] * b[2 * 4 + 0] + a[1 * 4 + 3] * b[3 * 4 + 0]
        out[1 * 4 + 1] =
            a[1 * 4 + 0] * b[0 * 4 + 1] + a[1 * 4 + 1] * b[1 * 4 + 1] + a[1 * 4 + 2] * b[2 * 4 + 1] + a[1 * 4 + 3] * b[3 * 4 + 1]
        out[1 * 4 + 2] =
            a[1 * 4 + 0] * b[0 * 4 + 2] + a[1 * 4 + 1] * b[1 * 4 + 2] + a[1 * 4 + 2] * b[2 * 4 + 2] + a[1 * 4 + 3] * b[3 * 4 + 2]
        out[1 * 4 + 3] =
            a[1 * 4 + 0] * b[0 * 4 + 3] + a[1 * 4 + 1] * b[1 * 4 + 3] + a[1 * 4 + 2] * b[2 * 4 + 3] + a[1 * 4 + 3] * b[3 * 4 + 3]
        out[2 * 4 + 0] =
            a[2 * 4 + 0] * b[0 * 4 + 0] + a[2 * 4 + 1] * b[1 * 4 + 0] + a[2 * 4 + 2] * b[2 * 4 + 0] + a[2 * 4 + 3] * b[3 * 4 + 0]
        out[2 * 4 + 1] =
            a[2 * 4 + 0] * b[0 * 4 + 1] + a[2 * 4 + 1] * b[1 * 4 + 1] + a[2 * 4 + 2] * b[2 * 4 + 1] + a[2 * 4 + 3] * b[3 * 4 + 1]
        out[2 * 4 + 2] =
            a[2 * 4 + 0] * b[0 * 4 + 2] + a[2 * 4 + 1] * b[1 * 4 + 2] + a[2 * 4 + 2] * b[2 * 4 + 2] + a[2 * 4 + 3] * b[3 * 4 + 2]
        out[2 * 4 + 3] =
            a[2 * 4 + 0] * b[0 * 4 + 3] + a[2 * 4 + 1] * b[1 * 4 + 3] + a[2 * 4 + 2] * b[2 * 4 + 3] + a[2 * 4 + 3] * b[3 * 4 + 3]
        out[3 * 4 + 0] =
            a[3 * 4 + 0] * b[0 * 4 + 0] + a[3 * 4 + 1] * b[1 * 4 + 0] + a[3 * 4 + 2] * b[2 * 4 + 0] + a[3 * 4 + 3] * b[3 * 4 + 0]
        out[3 * 4 + 1] =
            a[3 * 4 + 0] * b[0 * 4 + 1] + a[3 * 4 + 1] * b[1 * 4 + 1] + a[3 * 4 + 2] * b[2 * 4 + 1] + a[3 * 4 + 3] * b[3 * 4 + 1]
        out[3 * 4 + 2] =
            a[3 * 4 + 0] * b[0 * 4 + 2] + a[3 * 4 + 1] * b[1 * 4 + 2] + a[3 * 4 + 2] * b[2 * 4 + 2] + a[3 * 4 + 3] * b[3 * 4 + 2]
        out[3 * 4 + 3] =
            a[3 * 4 + 0] * b[0 * 4 + 3] + a[3 * 4 + 1] * b[1 * 4 + 3] + a[3 * 4 + 2] * b[2 * 4 + 3] + a[3 * 4 + 3] * b[3 * 4 + 3]
    }

    /*
     ================
     R_TransposeGLMatrix
     ================
     */
    fun R_TransposeGLMatrix(`in`: FloatArray /*[16]*/, out: FloatArray /*[16]*/) {
        var j: Int
        var i = 0
        while (i < 4) {
            j = 0
            while (j < 4) {
                out[i * 4 + j] = `in`[j * 4 + i]
                j++
            }
            i++
        }
    }

    fun R_SetViewMatrix(viewDef: viewDef_s) {
        val origin = idVec3()
        val viewerMatrix = FloatArray(16)
        viewDef.worldSpace = viewEntity_s()
        val world: viewEntity_s = viewDef.worldSpace

        // the model matrix is an identity
        world.modelMatrix[0 * 4 + 0] = 1.0f
        world.modelMatrix[1 * 4 + 1] = 1.0f
        world.modelMatrix[2 * 4 + 2] = 1.0f

        // transform by the camera placement
        origin.set(viewDef.renderView.vieworg)
        viewerMatrix[0] = viewDef.renderView.viewaxis[0, 0]
        viewerMatrix[4] = viewDef.renderView.viewaxis[0, 1]
        viewerMatrix[8] = viewDef.renderView.viewaxis[0, 2]
        viewerMatrix[12] =
            (-origin[0] * viewerMatrix[0]) + (-origin[1] * viewerMatrix[4]) + (-origin[2] * viewerMatrix[8])
        viewerMatrix[1] = viewDef.renderView.viewaxis[1, 0]
        viewerMatrix[5] = viewDef.renderView.viewaxis[1, 1]
        viewerMatrix[9] = viewDef.renderView.viewaxis[1, 2]
        viewerMatrix[13] =
            (-origin[0] * viewerMatrix[1]) + (-origin[1] * viewerMatrix[5]) + (-origin[2] * viewerMatrix[9])
        viewerMatrix[2] = viewDef.renderView.viewaxis[2, 0]
        viewerMatrix[6] = viewDef.renderView.viewaxis[2, 1]
        viewerMatrix[10] = viewDef.renderView.viewaxis[2, 2]
        viewerMatrix[14] =
            (-origin[0] * viewerMatrix[2]) + (-origin[1] * viewerMatrix[6]) + (-origin[2] * viewerMatrix[10])
        viewerMatrix[3] = 0.0f
        viewerMatrix[7] = 0.0f
        viewerMatrix[11] = 0.0f
        viewerMatrix[15] = 1.0f

        // convert from our coordinate system (looking down X)
        // to OpenGL's coordinate system (looking down -Z)
        myGlMultMatrix(viewerMatrix, s_flipMatrix, world.modelViewMatrix)
    }

    fun R_SetupProjection(viewDef: viewDef_s) {
        var xmin: Float
        var xmax: Float
        var ymin: Float
        var ymax: Float
        var jitterx: Float
        var jittery: Float

        // random jittering is usefull when multiple
        // frames are going to be blended together
        // for motion blurred anti-aliasing
        if (r_jitter!!.GetBool()) {
            jitterx = random.RandomFloat()
            jittery = random.RandomFloat()
        } else {
            jittery = 0.0f
            jitterx = jittery
        }

        //
        // set up projection matrix
        //
        var zNear: Float = r_znear!!.GetFloat()
        if (viewDef.renderView.cramZNear) {
            zNear *= 0.25f
        }
        ymax = (zNear * tan((viewDef.renderView.fov_y * idMath.PI / 360.0f)))
        ymin = -ymax
        xmax = (zNear * tan((viewDef.renderView.fov_x * idMath.PI / 360.0f)))
        xmin = -xmax
        val width: Float = xmax - xmin
        val height: Float = ymax - ymin
        jitterx = jitterx * width / (viewDef.viewport.x2 - viewDef.viewport.x1 + 1)
        xmin += jitterx
        xmax += jitterx
        jittery = jittery * height / (viewDef.viewport.y2 - viewDef.viewport.y1 + 1)
        ymin += jittery
        ymax += jittery
        viewDef.projectionMatrix[0] = 2 * zNear / width
        viewDef.projectionMatrix[4] = 0.0f
        viewDef.projectionMatrix[8] = (xmax + xmin) / width // normally 0
        viewDef.projectionMatrix[12] = 0.0f
        viewDef.projectionMatrix[1] = 0.0f
        viewDef.projectionMatrix[5] = 2 * zNear / height
        viewDef.projectionMatrix[9] = (ymax + ymin) / height // normally 0
        viewDef.projectionMatrix[13] = 0.0f

        // this is the far-plane-at-infinity formulation, and
        // crunches the Z range slightly so w=0 vertexes do not
        // rasterize right at the wraparound point
        viewDef.projectionMatrix[2] = 0.0f
        viewDef.projectionMatrix[6] = 0.0f
        viewDef.projectionMatrix[10] = -0.999f
        viewDef.projectionMatrix[14] = -2.0f * zNear
        viewDef.projectionMatrix[3] = 0.0f
        viewDef.projectionMatrix[7] = 0.0f
        viewDef.projectionMatrix[11] = -1.0f
        viewDef.projectionMatrix[15] = 0.0f
    }

    /*
     =================
     R_SetupViewFrustum

     Setup that culling frustum planes for the current view
     FIXME: derive from modelview matrix times projection matrix
     =================
     */
    fun R_SetupViewFrustum(viewDef: viewDef_s) {
        val xs = CFloat(0.0f)
        val xc = CFloat(0.0f)
        var ang: Float = DEG2RAD(viewDef.renderView.fov_x) * 0.5f
        SinCos(ang, xs, xc)
        viewDef.frustum[0].set(
            viewDef.renderView.viewaxis[0].times(xs._val)
                .plus(viewDef.renderView.viewaxis[1].times(xc._val))
        )
        viewDef.frustum[1].set(
            viewDef.renderView.viewaxis[0].times(xs._val)
                .minus(viewDef.renderView.viewaxis[1].times(xc._val))
        )
        ang = DEG2RAD(viewDef.renderView.fov_y) * 0.5f
        SinCos(ang, xs, xc)
        viewDef.frustum[2].set(
            viewDef.renderView.viewaxis[0].times(xs._val)
                .plus(viewDef.renderView.viewaxis[2].times(xc._val))
        )
        viewDef.frustum[3].set(
            viewDef.renderView.viewaxis[0].times(xs._val)
                .minus(viewDef.renderView.viewaxis[2].times(xc._val))
        )

        // plane four is the front clipping plane
        viewDef.frustum[4].set( /* vec3_origin - */viewDef.renderView.viewaxis[0])
        var i = 0
        while (i < 5) {

            // flip direction so positive side faces out (FIXME: globally unify this)
            viewDef.frustum[i].set(viewDef.frustum[i].Normal().unaryMinus())
            viewDef.frustum[i][3] =
                -(viewDef.renderView.vieworg.times(viewDef.frustum[i].Normal()))
            i++
        }

        // eventually, plane five will be the rear clipping plane for fog
        var dNear: Float = r_znear!!.GetFloat()
        if (viewDef.renderView.cramZNear) {
            dNear *= 0.25f
        }
        val dFar: Float = MAX_WORLD_SIZE.toFloat()
        val dLeft: Float = (dFar * tan(DEG2RAD(viewDef.renderView.fov_x * 0.5f)))
        val dUp: Float = (dFar * tan(DEG2RAD(viewDef.renderView.fov_y * 0.5f)))
        viewDef.viewFrustum.SetOrigin(viewDef.renderView.vieworg)
        viewDef.viewFrustum.SetAxis(viewDef.renderView.viewaxis)
        viewDef.viewFrustum.SetSize(dNear, dFar, dLeft, dUp)
    }

    /*
     ===================
     R_ConstrainViewFrustum
     ===================
     */
    fun R_ConstrainViewFrustum() {
        val bounds = idBounds()

        // constrain the view frustum to the total bounds of all visible lights and visible entities
        bounds.Clear()
        var vLight: viewLight_s? = tr.viewDef!!.viewLights
        while (vLight != null) {
            bounds.AddBounds(vLight.lightDef!!.frustumTris!!.bounds)
            vLight = vLight.next
        }
        var vEntity: viewEntity_s? = tr.viewDef!!.viewEntitys
        while (vEntity != null) {
            bounds.AddBounds(vEntity.entityDef!!.referenceBounds)
            vEntity = vEntity.next
        }
        tr.viewDef!!.viewFrustum.ConstrainToBounds(bounds)
        if (r_useFrustumFarDistance!!.GetFloat() > 0.0f) {
            tr.viewDef!!.viewFrustum.MoveFarDistance(r_useFrustumFarDistance!!.GetFloat())
        }
    }

    /*
     =================
     R_SortDrawSurfs
     =================
     */
    fun R_SortDrawSurfs() {
        // sort the drawsurfs by sort type, then orientation, then shader
        if (tr.viewDef!!.drawSurfs != null) {
            Arrays.sort(tr.viewDef!!.drawSurfs, 0, tr.viewDef!!.numDrawSurfs, R_QsortSurfaces())
        }
    }

    //========================================================================
    fun R_RenderView(parms: viewDef_s) {
        val oldView: viewDef_s?
        DEBUG_R_RenderView++
        if (parms.renderView.width <= 0 || parms.renderView.height <= 0) {
            return
        }
        tr.viewCount++

        // save view in case we are a subview
        oldView = tr.viewDef
        tr.viewDef = parms
        tr.sortOffset = 0.0f

        // set the matrix for world space to eye space
        R_SetViewMatrix(tr.viewDef!!)

        // the four sides of the view frustum are needed
        // for culling and portal visibility
        R_SetupViewFrustum(tr.viewDef!!)

        // we need to set the projection matrix before doing
        // portal-to-screen scissor box calculations
        R_SetupProjection(tr.viewDef!!)

        // identify all the visible portalAreas, and the entityDefs and
        // lightDefs that are in them and pass culling.
        parms.renderWorld!!.FindViewLightsAndEntities()

        // constrain the view frustum to the view lights and entities
        R_ConstrainViewFrustum()

        // make sure that interactions exist for all light / entity combinations
        // that are visible
        // add any pre-generated light shadows, and calculate the light shader values
        tr_light.R_AddLightSurfaces()

        // adds ambient surfaces and create any necessary interaction surfaces to add to the light
        // lists
        tr_light.R_AddModelSurfaces()

        // any viewLight that didn't have visible surfaces can have it's shadows removed
        tr_light.R_RemoveUnecessaryViewLights()

        // sort all the ambient surfaces for translucency ordering
        R_SortDrawSurfs()

        // generate any subviews (mirrors, cameras, etc) before adding this view
        if (tr_subview.R_GenerateSubViews()) {
            // if we are debugging subviews, allow the skipping of the
            // main view draw
            if (r_subviewOnly!!.GetBool()) {
                return
            }
        }

        // write everything needed to the demo file
        if (Session.session.writeDemo != null) {
            parms.renderWorld!!.WriteVisibleDefs(tr.viewDef!!)
        }

        // add the rendering commands for this viewDef
        RenderSystem.R_AddDrawViewCmd(parms)

        // restore view in case we are a subview
        tr.viewDef = oldView
    }

    /*
     ==========================================================================================

     DRAWSURF SORTING

     ==========================================================================================
     */
    /*
     =======================
     R_QsortSurfaces

     =======================
     */
    class R_QsortSurfaces : cmp_t<drawSurf_s?> {
        override fun compare(a: drawSurf_s?, b: drawSurf_s?): Int {

            //this check assumes that the array contains nothing but nulls from this point.
            if (null == a && null == b) {
                return 0
            }
            if (null == b || (null != a && a.sort < b.sort)) {
                return -1
            }
            if (null == a || (null != b && a.sort > b.sort)) {
                return 1
            }
            return 0
        }
    }
}

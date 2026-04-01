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

import neo.Renderer.Cinematic.cinData_t
import neo.Renderer.Image.idImage
import neo.Renderer.Material.cullType_t
import neo.Renderer.Material.idMaterial
import neo.Renderer.Model.idRenderModel
import neo.Renderer.Model.shadowCache_s
import neo.Renderer.Model.silEdge_t
import neo.Renderer.Model.srfTriangles_s
import neo.Renderer.RenderWorld.modelTrace_s
import neo.Renderer.tr_render.RB_T_RenderTriangleSurface
import neo.TempDump.allocArray
import neo.framework.Common
import neo.framework.DeclManager
import neo.idlib.BV.idBounds
import neo.idlib.Text.Str.idStr
import neo.idlib.Text.Str.va
import neo.idlib.colorCyan
import neo.idlib.geometry.DrawVert.idDrawVert
import neo.idlib.geometry.Winding.idWinding
import neo.idlib.math.Matrix.idMat3
import neo.idlib.math.VectorMA
import neo.idlib.math.VectorSubtract
import neo.idlib.math.idVec3
import neo.idlib.math.idVec4
import neo.ui.DeviceContext.idDeviceContext
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.max

object tr_rendertools {
    val BAR_HEIGHT: Int = 64

    //
    val G_HEIGHT: Int = 512

    /*
     ================
     RB_TestGamma
     ================
     */
    val G_WIDTH: Int = 512
    val MAX_DEBUG_LINES: Int = 16384

    //
    val MAX_DEBUG_POLYGONS: Int = 8192

    //
    val MAX_DEBUG_TEXT: Int = 512

    //
    //
    val rb_debugLines: Array<debugLine_s?> = arrayOfNulls(MAX_DEBUG_LINES)

    //
    val rb_debugPolygons: Array<debugPolygon_s>

    /*
     ===================
     R_ColorByStencilBuffer

     Sets the screen colors based on the contents of the
     stencil buffer.  Stencil of 0 = black, 1 = red, 2 = green,
     3 = blue, ..., 7+ = white
     ===================
     */
    private val colors /*[8][3]*/: Array<FloatArray> = arrayOf(
        floatArrayOf(0.0f, 0.0f, 0.0f),
        floatArrayOf(1.0f, 0.0f, 0.0f),
        floatArrayOf(0.0f, 1.0f, 0.0f),
        floatArrayOf(0.0f, 0.0f, 1.0f),
        floatArrayOf(0.0f, 1.0f, 1.0f),
        floatArrayOf(1.0f, 0.0f, 1.0f),
        floatArrayOf(1.0f, 1.0f, 0.0f),
        floatArrayOf(1.0f, 1.0f, 1.0f)
    )
    var rb_debugLineTime: Int = 0
    var rb_debugPolygonTime: Int = 0

    //
    //
    var rb_debugText: Array<debugText_s> = allocArray(debugText_s::class.java, MAX_DEBUG_TEXT)
    var rb_debugTextTime: Int = 0
    var rb_numDebugLines: Int = 0
    var rb_numDebugPolygons: Int = 0
    var rb_numDebugText: Int = 0

    //    
    //    
    /*
     ================
     RB_DrawBounds
     ================
     */
    init {
        rb_debugPolygons = Array<debugPolygon_s>(MAX_DEBUG_POLYGONS) { debugPolygon_s() }
    }

    fun RB_DrawBounds(bounds: idBounds) {
        if (bounds.IsCleared()) {
            return
        }
        qgl.qglBegin(GL11.GL_LINE_LOOP)
        qgl.qglVertex3f(bounds[0, 0], bounds[0, 1], bounds[0, 2])
        qgl.qglVertex3f(bounds[0, 0], bounds[1, 1], bounds[0, 2])
        qgl.qglVertex3f(bounds[1, 0], bounds[1, 1], bounds[0, 2])
        qgl.qglVertex3f(bounds[1, 0], bounds[0, 1], bounds[0, 2])
        qgl.qglEnd()
        qgl.qglBegin(GL11.GL_LINE_LOOP)
        qgl.qglVertex3f(bounds[0, 0], bounds[0, 1], bounds[1, 2])
        qgl.qglVertex3f(bounds[0, 0], bounds[1, 1], bounds[1, 2])
        qgl.qglVertex3f(bounds[1, 0], bounds[1, 1], bounds[1, 2])
        qgl.qglVertex3f(bounds[1, 0], bounds[0, 1], bounds[1, 2])
        qgl.qglEnd()
        qgl.qglBegin(GL11.GL_LINES)
        qgl.qglVertex3f(bounds[0, 0], bounds[0, 1], bounds[0, 2])
        qgl.qglVertex3f(bounds[0, 0], bounds[0, 1], bounds[1, 2])
        qgl.qglVertex3f(bounds[0, 0], bounds[1, 1], bounds[0, 2])
        qgl.qglVertex3f(bounds[0, 0], bounds[1, 1], bounds[1, 2])
        qgl.qglVertex3f(bounds[1, 0], bounds[0, 1], bounds[0, 2])
        qgl.qglVertex3f(bounds[1, 0], bounds[0, 1], bounds[1, 2])
        qgl.qglVertex3f(bounds[1, 0], bounds[1, 1], bounds[0, 2])
        qgl.qglVertex3f(bounds[1, 0], bounds[1, 1], bounds[1, 2])
        qgl.qglEnd()
    }

    /*
     ================
     RB_SimpleSurfaceSetup
     ================
     */
    fun RB_SimpleSurfaceSetup(drawSurf: drawSurf_s) {
        // change the matrix if needed
        if (drawSurf.space !== backEnd!!.currentSpace) {
            qgl.qglLoadMatrixf(drawSurf.space!!.modelViewMatrix)
            backEnd!!.currentSpace = drawSurf.space
        }

        // change the scissor if needed
        if (r_useScissor.GetBool() && !backEnd!!.currentScissor!!.Equals(drawSurf.scissorRect!!)) {
            backEnd!!.currentScissor = drawSurf.scissorRect
            qgl.qglScissor(
                backEnd!!.viewDef!!.viewport.x1 + backEnd!!.currentScissor!!.x1,
                backEnd!!.viewDef!!.viewport.y1 + backEnd!!.currentScissor!!.y1,
                backEnd!!.currentScissor!!.x2 + 1 - backEnd!!.currentScissor!!.x1,
                backEnd!!.currentScissor!!.y2 + 1 - backEnd!!.currentScissor!!.y1
            )
        }
    }

    /*
     ================
     RB_SimpleWorldSetup
     ================
     */
    fun RB_SimpleWorldSetup() {
        backEnd!!.currentSpace = backEnd!!.viewDef!!.worldSpace
        qgl.qglLoadMatrixf(backEnd!!.viewDef!!.worldSpace.modelViewMatrix)
        backEnd!!.currentScissor = backEnd!!.viewDef!!.scissor
        qgl.qglScissor(
            backEnd!!.viewDef!!.viewport.x1 + backEnd!!.currentScissor!!.x1,
            backEnd!!.viewDef!!.viewport.y1 + backEnd!!.currentScissor!!.y1,
            backEnd!!.currentScissor!!.x2 + 1 - backEnd!!.currentScissor!!.x1,
            backEnd!!.currentScissor!!.y2 + 1 - backEnd!!.currentScissor!!.y1
        )
    }

    /*
     =================
     RB_PolygonClear

     This will cover the entire screen with normal rasterization.
     Texturing is disabled, but the existing glColor, glDepthMask,
     glColorMask, and the enabled state of depth buffering and
     stenciling will matter.
     =================
     */
    fun RB_PolygonClear() {
        qgl.qglPushMatrix()
        qgl.qglPushAttrib(GL11.GL_ALL_ATTRIB_BITS)
        qgl.qglLoadIdentity()
        qgl.qglDisable(GL11.GL_TEXTURE_2D)
        qgl.qglDisable(GL11.GL_DEPTH_TEST)
        qgl.qglDisable(GL11.GL_CULL_FACE)
        qgl.qglDisable(GL11.GL_SCISSOR_TEST)
        qgl.qglBegin(GL11.GL_POLYGON)
        qgl.qglVertex3f(-20.0f, -20.0f, -10.0f)
        qgl.qglVertex3f(20.0f, -20.0f, -10.0f)
        qgl.qglVertex3f(20.0f, 20.0f, -10.0f)
        qgl.qglVertex3f(-20.0f, 20.0f, -10.0f)
        qgl.qglEnd()
        qgl.qglPopAttrib()
        qgl.qglPopMatrix()
    }

    /*
     ====================
     RB_ShowDestinationAlpha
     ====================
     */
    fun RB_ShowDestinationAlpha() {
        tr_backend.GL_State(GLS_SRCBLEND_DST_ALPHA or GLS_DSTBLEND_ZERO or GLS_DEPTHMASK or GLS_DEPTHFUNC_ALWAYS)
        qgl.qglColor3f(1.0f, 1.0f, 1.0f)
        RB_PolygonClear()
    }

    /*
     ===================
     RB_ScanStencilBuffer

     Debugging tool to see what values are in the stencil buffer
     ===================
     */
    fun RB_ScanStencilBuffer() {
        val counts = IntArray(256)
        var i: Int
        var stencilReadback: ByteBuffer?
        stencilReadback =
            ByteBuffer.allocate(glConfig.vidWidth * glConfig.vidHeight)
        qgl.qglReadPixels(
            0,
            0,
            glConfig.vidWidth,
            glConfig.vidHeight,
            GL11.GL_STENCIL_INDEX,
            GL11.GL_UNSIGNED_BYTE,
            stencilReadback
        )
        i = 0
        while (i < glConfig.vidWidth * glConfig.vidHeight) {
            counts[stencilReadback.get(i).toInt() and 0xFF]++
            i++
        }
        stencilReadback = null

        // print some stats (not supposed to do from back end in SMP...)
        Common.common.Printf("stencil values:\n")
        i = 0
        while (i < 255) {
            if (counts[i] != 0) {
                Common.common.Printf("%d: %d\n", i, counts[i])
            }
            i++
        }
    }

    /*
     ===================
     RB_CountStencilBuffer

     Print an overdraw count based on stencil index values
     ===================
     */
    fun RB_CountStencilBuffer() {
        var count: Int
        var i: Int
        var stencilReadback: ByteBuffer?
        stencilReadback =
            BufferUtils.createByteBuffer(glConfig.vidWidth * glConfig.vidHeight)
        qgl.qglReadPixels(
            0,
            0,
            glConfig.vidWidth,
            glConfig.vidHeight,
            GL11.GL_STENCIL_INDEX,
            GL11.GL_UNSIGNED_BYTE,
            stencilReadback
        )
        count = 0
        i = 0
        while (i < glConfig.vidWidth * glConfig.vidHeight) {
            count += stencilReadback.get(i).toInt() and 0xFF
            i++
        }
        stencilReadback = null

        // print some stats (not supposed to do from back end in SMP...)
        Common.common.Printf(
            "overdraw: %5.1f\n",
            count.toFloat() / (glConfig.vidWidth * glConfig.vidHeight)
        )
    }

    fun R_ColorByStencilBuffer() {
        var i: Int

        // clear color buffer to white (>6 passes)
        qgl.qglClearColor(1.0f, 1.0f, 1.0f, 1.0f)
        qgl.qglDisable(GL11.GL_SCISSOR_TEST)
        qgl.qglClear(GL11.GL_COLOR_BUFFER_BIT)

        // now draw color for each stencil value
        qgl.qglStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP)
        i = 0
        while (i < 6) {
            qgl.qglColor3fv(colors[i])
            qgl.qglStencilFunc(GL11.GL_EQUAL, i, 255)
            RB_PolygonClear()
            i++
        }
        qgl.qglStencilFunc(GL11.GL_ALWAYS, 0, 255)
    }

    //======================================================================
    /*
     ==================
     RB_ShowOverdraw
     ==================
     */
    fun RB_ShowOverdraw() {
        val material: idMaterial?
        var i: Int
        val drawSurfs: Array<drawSurf_s>
        var surf: drawSurf_s?
        val numDrawSurfs: Int
        var vLight: viewLight_s?
        if (r_showOverDraw!!.GetInteger() == 0) {
            return
        }
        material = DeclManager.declManager.FindMaterial("textures/common/overdrawtest", false)
        if (material == null) {
            return
        }
        drawSurfs = backEnd!!.viewDef!!.drawSurfs
        numDrawSurfs = backEnd!!.viewDef!!.numDrawSurfs
        var interactions = 0
        vLight = backEnd!!.viewDef!!.viewLights
        while (vLight != null) {
            surf = vLight.localInteractions[0]
            while (surf != null) {
                interactions++
                surf = surf.nextOnLight
            }
            surf = vLight.globalInteractions[0]
            while (surf != null) {
                interactions++
                surf = surf.nextOnLight
            }
            vLight = vLight.next
        }
        val newDrawSurfs: Array<drawSurf_s?> =
            drawSurf_s.generateArray(numDrawSurfs + interactions) as Array<drawSurf_s?>
        i = 0
        while (i < numDrawSurfs) {
            surf = drawSurfs[i]
            if (surf.material != null) {
                surf.material = material
            }
            newDrawSurfs[i] = surf
            i++
        }
        vLight = backEnd!!.viewDef!!.viewLights
        while (vLight != null) {
            surf = vLight.localInteractions[0]
            while (surf != null) {
                surf.material = material
                newDrawSurfs[i++] = surf
                surf = surf.nextOnLight
            }
            surf = vLight.globalInteractions[0]
            while (surf != null) {
                surf.material = material
                newDrawSurfs[i++] = surf
                surf = surf.nextOnLight
            }
            vLight.localInteractions[0] = null
            vLight.globalInteractions[0] = null
            vLight = vLight.next
        }
        when (r_showOverDraw!!.GetInteger()) {
            1 -> {
                backEnd!!.viewDef!!.drawSurfs = newDrawSurfs as Array<drawSurf_s>
                backEnd!!.viewDef!!.numDrawSurfs = numDrawSurfs
            }

            2 -> {
                backEnd!!.viewDef!!.drawSurfs =
                    newDrawSurfs.copyOfRange(numDrawSurfs, numDrawSurfs + interactions) as Array<drawSurf_s>
                backEnd!!.viewDef!!.numDrawSurfs = interactions
            }

            3 -> {
                backEnd!!.viewDef!!.drawSurfs = newDrawSurfs as Array<drawSurf_s>
                backEnd!!.viewDef!!.numDrawSurfs += interactions
            }
        }
    }

    /*
     ===================
     RB_ShowIntensity

     Debugging tool to see how much dynamic range a scene is using.
     The greatest of the rgb values at each pixel will be used, with
     the resulting color shading from red at 0 to green at 128 to blue at 255
     ===================
     */
    fun RB_ShowIntensity() {
        val colorReadback: ByteBuffer
        var i: Int
        var j: Int
        val c: Int
        if (!r_showIntensity!!.GetBool()) {
            return
        }
        colorReadback =
            ByteBuffer.allocate(glConfig.vidWidth * glConfig.vidHeight * 4)
        qgl.qglReadPixels(
            0,
            0,
            glConfig.vidWidth,
            glConfig.vidHeight,
            GL11.GL_RGBA,
            GL11.GL_UNSIGNED_BYTE,
            colorReadback
        )
        c = glConfig.vidWidth * glConfig.vidHeight * 4
        i = 0
        while (i < c) {
            j = colorReadback.get(i).toInt() and 0xFF
            if ((colorReadback.get(i + 1).toInt() and 0xFF) > j) {
                j = colorReadback.get(i + 1).toInt() and 0xFF
            }
            if ((colorReadback.get(i + 2).toInt() and 0xFF) > j) {
                j = colorReadback.get(i + 2).toInt() and 0xFF
            }
            if (j < 128) {
                colorReadback.put(i + 0, (2 * (128 - j)).toByte())
                colorReadback.put(i + 1, (2 * j).toByte())
                colorReadback.put(i + 2, 0.toByte())
            } else {
                colorReadback.put(i + 0, 0.toByte())
                colorReadback.put(i + 1, (2 * (255 - j)).toByte())
                colorReadback.put(i + 2, (2 * (j - 128)).toByte())
            }
            i += 4
        }

        // draw it back to the screen
        qgl.qglLoadIdentity()
        qgl.qglMatrixMode(GL11.GL_PROJECTION)
        tr_backend.GL_State(GLS_DEPTHFUNC_ALWAYS)
        qgl.qglPushMatrix()
        qgl.qglLoadIdentity()
        qgl.qglOrtho(0.0, 1.0, 0.0, 1.0, -1.0, 1.0)
        qgl.qglRasterPos2f(0.0f, 0.0f)
        qgl.qglPopMatrix()
        qgl.qglColor3f(1.0f, 1.0f, 1.0f)
        Image.globalImages.BindNull()
        qgl.qglMatrixMode(GL11.GL_MODELVIEW)
        qgl.qglDrawPixels(
            glConfig.vidWidth,
            glConfig.vidHeight,
            GL11.GL_RGBA,
            GL11.GL_UNSIGNED_BYTE,
            colorReadback
        )
    }

    /*
     ===================
     RB_ShowDepthBuffer

     Draw the depth buffer as colors
     ===================
     */
    fun RB_ShowDepthBuffer() {
        if (!r_showDepth!!.GetBool()) {
            return
        }
        qgl.qglPushMatrix()
        qgl.qglLoadIdentity()
        qgl.qglMatrixMode(GL11.GL_PROJECTION)
        qgl.qglPushMatrix()
        qgl.qglLoadIdentity()
        qgl.qglOrtho(0.0, 1.0, 0.0, 1.0, -1.0, 1.0)
        qgl.qglRasterPos2f(0.0f, 0.0f)

        tr_backend.GL_State(GLS_DEPTHFUNC_ALWAYS)
        qgl.qglColor3f(1.0f, 1.0f, 1.0f)

        val haveDepthCapture = r_enableDepthCapture.GetInteger() == 1
                || (r_enableDepthCapture.GetInteger() == -1 && r_useSoftParticles.GetBool())

        if (haveDepthCapture) {
            Image.globalImages.currentDepthImage?.Bind()

            val x = 0.0f
            val y = 0.0f
            val w = 1.0f
            val h = 1.0f
            val tx = 0.0f
            val ty = 0.0f
            // the actual texturesize of currentDepthImage is the next bigger power of two (POT),
            // so the normalized width/height of the part of it we actually wanna show is the following
            val tw = glConfig.vidWidth.toFloat() / Image.globalImages.currentDepthImage!!.uploadWidth._val.toFloat()
            val th = glConfig.vidHeight.toFloat() / Image.globalImages.currentDepthImage!!.uploadHeight._val.toFloat()

            qgl.qglBegin(GL11.GL_QUADS)
            qgl.qglTexCoord2f(tx, ty)
            qgl.qglVertex2f(x, y)
            qgl.qglTexCoord2f(tx, ty + th)
            qgl.qglVertex2f(x, y + h)
            qgl.qglTexCoord2f(tx + tw, ty + th)
            qgl.qglVertex2f(x + w, y + h)
            qgl.qglTexCoord2f(tx + tw, ty)
            qgl.qglVertex2f(x + w, y)
            qgl.qglEnd()

            qgl.qglPopMatrix()
            qgl.qglMatrixMode(GL11.GL_MODELVIEW)
            qgl.qglPopMatrix()
        } else {
            qgl.qglPopMatrix()
            qgl.qglMatrixMode(GL11.GL_MODELVIEW)
            qgl.qglPopMatrix()
            Image.globalImages.BindNull()

            val depthReadback = BufferUtils.createByteBuffer(glConfig.vidWidth * glConfig.vidHeight * 4)
            qgl.qglReadPixels(
                0, 0,
                glConfig.vidWidth, glConfig.vidHeight,
                GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT,
                depthReadback
            )
            qgl.qglDrawPixels(
                glConfig.vidWidth, glConfig.vidHeight,
                GL11.GL_LUMINANCE, GL11.GL_FLOAT,
                depthReadback
            )
        }
    }

    /*
     =================
     RB_ShowLightCount

     This is a debugging tool that will draw each surface with a color
     based on how many lights are effecting it
     =================
     */
    fun RB_ShowLightCount() {
        var i: Int
        var surf: drawSurf_s?
        var vLight: viewLight_s?
        if (!r_showLightCount!!.GetBool()) {
            return
        }
        tr_backend.GL_State(GLS_DEPTHFUNC_EQUAL)
        RB_SimpleWorldSetup()
        qgl.qglClearStencil(0)
        qgl.qglClear(GL11.GL_STENCIL_BUFFER_BIT)
        qgl.qglEnable(GL11.GL_STENCIL_TEST)

        // optionally count everything through walls
        if (r_showLightCount!!.GetInteger() >= 2) {
            qgl.qglStencilOp(GL11.GL_KEEP, GL11.GL_INCR, GL11.GL_INCR)
        } else {
            qgl.qglStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_INCR)
        }
        qgl.qglStencilFunc(GL11.GL_ALWAYS, 1, 255)
        Image.globalImages.defaultImage!!.Bind()
        var counter = 0
        vLight = backEnd!!.viewDef!!.viewLights
        while (vLight != null) {
            i = 0
            while (i < 2) {
                surf = (if (i != 0) vLight.localInteractions[0] else vLight.globalInteractions[0])
                while (surf != null) {
                    RB_SimpleSurfaceSetup(surf)
                    counter++
                    if (surf.geo!!.ambientCache == null) {
                        surf = surf.nextOnLight
                        continue
                    }
                    val ac =
                        idDrawVert(VertexCache.vertexCache.Position(surf.geo!!.ambientCache))
                    qgl.qglVertexPointer(3, GL11.GL_FLOAT, idDrawVert.BYTES, ac.xyzOffset().toLong())
                    tr_render.RB_DrawElementsWithCounters(surf.geo!!)
                    surf = surf.nextOnLight
                }
                i++
            }
            vLight = vLight.next
        }

        // display the results
        R_ColorByStencilBuffer()
        if (r_showLightCount!!.GetInteger() > 2) {
            RB_CountStencilBuffer()
        }
    }

    /*
     =================
     RB_ShowSilhouette

     Blacks out all edges, then adds color for each edge that a shadow
     plane extends from, allowing you to see doubled edges
     =================
     */
    fun RB_ShowSilhouette() {
        var i: Int
        var surf: drawSurf_s?
        var vLight: viewLight_s?
        if (!r_showSilhouette!!.GetBool()) {
            return
        }

        //
        // clear all triangle edges to black
        //
        qgl.qglDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY)
        Image.globalImages.BindNull()
        qgl.qglDisable(GL11.GL_TEXTURE_2D)
        qgl.qglDisable(GL11.GL_STENCIL_TEST)
        qgl.qglColor3f(0.0f, 0.0f, 0.0f)
        tr_backend.GL_State(GLS_POLYMODE_LINE)
        tr_backend.GL_Cull(cullType_t.CT_TWO_SIDED)
        qgl.qglDisable(GL11.GL_DEPTH_TEST)
        tr_render.RB_RenderDrawSurfListWithFunction(
            backEnd!!.viewDef!!.drawSurfs,
            backEnd!!.viewDef!!.numDrawSurfs,
            RB_T_RenderTriangleSurface.INSTANCE
        )

        //
        // now blend in edges that cast silhouettes
        //
        RB_SimpleWorldSetup()
        qgl.qglColor3f(0.5f, 0.0f, 0.0f)
        tr_backend.GL_State(GLS_SRCBLEND_ONE or GLS_DSTBLEND_ONE)
        vLight = backEnd!!.viewDef!!.viewLights
        while (vLight != null) {
            i = 0
            while (i < 2) {
                surf = (if (i != 0) vLight.localShadows[0] else vLight.globalShadows[0])
                while (surf != null) {
                    RB_SimpleSurfaceSetup(surf)
                    val tri: srfTriangles_s = surf.geo!!
                    qgl.qglVertexPointer(
                        3,
                        GL11.GL_FLOAT,
                        shadowCache_s.BYTES,
                        VertexCache.vertexCache.Position(tri.shadowCache)
                    )
                    qgl.qglBegin(GL11.GL_LINES)
                    var j = 0
                    while (j < tri.numIndexes) {
                        val i1: Int = tri.indexes!![j + 0]
                        val i2: Int = tri.indexes!![j + 1]
                        val i3: Int = tri.indexes!![j + 2]
                        if ((i1 and 1) + (i2 and 1) + (i3 and 1) == 1) {
                            if ((i1 and 1) + (i2 and 1) == 0) {
                                qgl.qglArrayElement(i1)
                                qgl.qglArrayElement(i2)
                            } else if ((i1 and 1) + (i3 and 1) == 0) {
                                qgl.qglArrayElement(i1)
                                qgl.qglArrayElement(i3)
                            }
                        }
                        j += 3
                    }
                    qgl.qglEnd()
                    surf = surf.nextOnLight
                }
                i++
            }
            vLight = vLight.next
        }
        qgl.qglEnable(GL11.GL_DEPTH_TEST)
        tr_backend.GL_State(GLS_DEFAULT)
        qgl.qglColor3f(1.0f, 1.0f, 1.0f)
        tr_backend.GL_Cull(cullType_t.CT_FRONT_SIDED)
    }

    /*
     =================
     RB_ShowShadowCount

     This is a debugging tool that will draw only the shadow volumes
     and count up the total fill usage
     =================
     */
    fun RB_ShowShadowCount() {
        var i: Int
        var surf: drawSurf_s?
        var vLight: viewLight_s?
        if (!r_showShadowCount!!.GetBool()) {
            return
        }
        tr_backend.GL_State(GLS_DEFAULT)
        qgl.qglClearStencil(0)
        qgl.qglClear(GL11.GL_STENCIL_BUFFER_BIT)
        qgl.qglEnable(GL11.GL_STENCIL_TEST)
        qgl.qglStencilOp(GL11.GL_KEEP, GL11.GL_INCR, GL11.GL_INCR)
        qgl.qglStencilFunc(GL11.GL_ALWAYS, 1, 255)
        Image.globalImages.defaultImage!!.Bind()

        // draw both sides
        tr_backend.GL_Cull(cullType_t.CT_TWO_SIDED)
        vLight = backEnd!!.viewDef!!.viewLights
        while (vLight != null) {
            i = 0
            while (i < 2) {
                surf = (if (i != 0) vLight.localShadows[0] else vLight.globalShadows[0])
                while (surf != null) {
                    RB_SimpleSurfaceSetup(surf)
                    val tri: srfTriangles_s = surf.geo!!
                    if (tri.shadowCache == null) {
                        surf = surf.nextOnLight
                        continue
                    }
                    if (r_showShadowCount!!.GetInteger() == 3) {
                        // only show turboshadows
                        if (tri.numShadowIndexesNoCaps != tri.numIndexes) {
                            surf = surf.nextOnLight
                            continue
                        }
                    }
                    if (r_showShadowCount!!.GetInteger() == 4) {
                        // only show static shadows
                        if (tri.numShadowIndexesNoCaps == tri.numIndexes) {
                            surf = surf.nextOnLight
                            continue
                        }
                    }
                    val cache: ByteBuffer =
                        VertexCache.vertexCache.Position(tri.shadowCache)
                    qgl.qglVertexPointer(4, GL11.GL_FLOAT, shadowCache_s.BYTES, cache)
                    tr_render.RB_DrawElementsWithCounters(tri)
                    surf = surf.nextOnLight
                }
                i++
            }
            vLight = vLight.next
        }

        // display the results
        R_ColorByStencilBuffer()
        if (r_showShadowCount!!.GetInteger() == 2) {
            Common.common.Printf("all shadows ")
        } else if (r_showShadowCount!!.GetInteger() == 3) {
            Common.common.Printf("turboShadows ")
        } else if (r_showShadowCount!!.GetInteger() == 4) {
            Common.common.Printf("static shadows ")
        }
        if (r_showShadowCount!!.GetInteger() >= 2) {
            RB_CountStencilBuffer()
        }
        tr_backend.GL_Cull(cullType_t.CT_FRONT_SIDED)
    }

    /*
     ===============
     RB_T_RenderTriangleSurfaceAsLines

     ===============
     */
    fun RB_T_RenderTriangleSurfaceAsLines(surf: drawSurf_s) {
        val tri: srfTriangles_s = surf.geo!!
        if (null == tri.verts) {
            return
        }
        qgl.qglBegin(GL11.GL_LINES)
        var i = 0
        while (i < tri.numIndexes) {
            for (j in 0..2) {
                val k: Int = (j + 1) % 3
                qgl.qglVertex3fv(tri.verts!![tri.silIndexes!![i + j]]!!.xyz.ToFloatPtr())
                qgl.qglVertex3fv(tri.verts!![tri.silIndexes!![i + k]]!!.xyz.ToFloatPtr())
            }
            i += 3
        }
        qgl.qglEnd()
    }

    /*
     =====================
     RB_ShowTris

     Debugging tool
     =====================
     */
    fun RB_ShowTris(drawSurfs: Array<drawSurf_s?>?, numDrawSurfs: Int) {
        if (0 == r_showTris!!.GetInteger()) {
            return
        }
        qgl.qglDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY)
        Image.globalImages.BindNull()
        qgl.qglDisable(GL11.GL_TEXTURE_2D)
        qgl.qglDisable(GL11.GL_STENCIL_TEST)
        qgl.qglColor3f(1.0f, 1.0f, 1.0f)
        tr_backend.GL_State(GLS_POLYMODE_LINE)
        when (r_showTris!!.GetInteger()) {
            1 -> {
                qgl.qglPolygonOffset(-1.0f, -2.0f)
                qgl.qglEnable(GL11.GL_POLYGON_OFFSET_LINE)
            }

            2 -> {
                tr_backend.GL_Cull(cullType_t.CT_FRONT_SIDED)
                qgl.qglDisable(GL11.GL_DEPTH_TEST)
            }

            3 -> {
                tr_backend.GL_Cull(cullType_t.CT_TWO_SIDED)
                qgl.qglDisable(GL11.GL_DEPTH_TEST)
            }

            else -> {
                tr_backend.GL_Cull(cullType_t.CT_FRONT_SIDED)
                qgl.qglDisable(GL11.GL_DEPTH_TEST)
            }
        }
        tr_render.RB_RenderDrawSurfListWithFunction(
            drawSurfs as Array<drawSurf_s>,
            numDrawSurfs,
            RB_T_RenderTriangleSurface.INSTANCE
        )
        qgl.qglEnable(GL11.GL_DEPTH_TEST)
        qgl.qglDisable(GL11.GL_POLYGON_OFFSET_LINE)
        qgl.qglDepthRange(0.0f, 1.0f)
        tr_backend.GL_State(GLS_DEFAULT)
        tr_backend.GL_Cull(cullType_t.CT_FRONT_SIDED)
    }

    /*
     =====================
     RB_ShowSurfaceInfo

     Debugging tool
     =====================
     */
    fun RB_ShowSurfaceInfo(drawSurfs: Array<drawSurf_s?>?, numDrawSurfs: Int) {
        val mt = modelTrace_s()
        val start = idVec3()
        val end = idVec3()
        if (!r_showSurfaceInfo!!.GetBool()) {
            return
        }

        // start far enough away that we don't hit the player model
        start.set(
            tr.primaryView!!.renderView.vieworg.plus(
                tr.primaryView!!.renderView.viewaxis[0].times(16)
            )
        )
        end.set(start.plus(tr.primaryView!!.renderView.viewaxis[0].times(1000.0f)))
        if (!tr.primaryWorld!!.Trace(mt, start, end, 0.0f, false)) {
            return
        }
        qgl.qglDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY)
        Image.globalImages.BindNull()
        qgl.qglDisable(GL11.GL_TEXTURE_2D)
        qgl.qglDisable(GL11.GL_STENCIL_TEST)
        qgl.qglColor3f(1.0f, 1.0f, 1.0f)
        tr_backend.GL_State(GLS_POLYMODE_LINE)
        qgl.qglPolygonOffset(-1.0f, -2.0f)
        qgl.qglEnable(GL11.GL_POLYGON_OFFSET_LINE)
        val matrix = FloatArray(16)

        // transform the object verts into global space
        tr_main.R_AxisToModelMatrix(mt.entity!!.axis, mt.entity!!.origin, matrix)
        tr.primaryWorld!!.DrawText(
            mt.entity!!.hModel!!.Name(), mt.point.plus(tr.primaryView!!.renderView.viewaxis[2].times(12)),
            0.35f, idDeviceContext.colorRed, tr.primaryView!!.renderView.viewaxis
        )
        tr.primaryWorld!!.DrawText(
            mt.material!!.GetName(), mt.point,
            0.35f, idDeviceContext.colorBlue, tr.primaryView!!.renderView.viewaxis
        )
        qgl.qglEnable(GL11.GL_DEPTH_TEST)
        qgl.qglDisable(GL11.GL_POLYGON_OFFSET_LINE)
        qgl.qglDepthRange(0.0f, 1.0f)
        tr_backend.GL_State(GLS_DEFAULT)
        tr_backend.GL_Cull(cullType_t.CT_FRONT_SIDED)
    }

    /*
     =====================
     RB_ShowViewEntitys

     Debugging tool
     =====================
     */
    fun RB_ShowViewEntitys(vModels: viewEntity_s?) {
        var vModels: viewEntity_s? = vModels
        if (!r_showViewEntitys!!.GetBool()) {
            return
        }
        if (r_showViewEntitys!!.GetInteger() == 2) {
            Common.common.Printf("view entities: ")
            while (vModels != null) {
                Common.common.Printf("%d ", vModels.entityDef!!.index)
                vModels = vModels.next
            }
            Common.common.Printf("\n")
            return
        }
        qgl.qglDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY)
        Image.globalImages.BindNull()
        qgl.qglDisable(GL11.GL_TEXTURE_2D)
        qgl.qglDisable(GL11.GL_STENCIL_TEST)
        qgl.qglColor3f(1.0f, 1.0f, 1.0f)
        tr_backend.GL_State(GLS_POLYMODE_LINE)
        tr_backend.GL_Cull(cullType_t.CT_TWO_SIDED)
        qgl.qglDisable(GL11.GL_DEPTH_TEST)
        qgl.qglDisable(GL11.GL_SCISSOR_TEST)
        while (vModels != null) {
            val b = idBounds()
            qgl.qglLoadMatrixf(vModels.modelViewMatrix)
            if (null == vModels.entityDef) {
                vModels = vModels.next
                continue
            }

            // draw the reference bounds in yellow
            qgl.qglColor3f(1.0f, 1.0f, 0.0f)
            RB_DrawBounds(vModels.entityDef!!.referenceBounds)

            // draw the model bounds in white
            qgl.qglColor3f(1.0f, 1.0f, 1.0f)
            val model: idRenderModel? = tr_light.R_EntityDefDynamicModel(vModels.entityDef!!)
            if (null == model) {
                vModels = vModels.next
                continue  // particles won't instantiate without a current view
            }
            b.set(model.Bounds(vModels.entityDef!!.parms))
            RB_DrawBounds(b)

            vModels = vModels.next
        }
        qgl.qglEnable(GL11.GL_DEPTH_TEST)
        qgl.qglDisable(GL11.GL_POLYGON_OFFSET_LINE)
        qgl.qglDepthRange(0.0f, 1.0f)
        tr_backend.GL_State(GLS_DEFAULT)
        tr_backend.GL_Cull(cullType_t.CT_FRONT_SIDED)
    }

    /*
     =====================
     RB_ShowTexturePolarity

     Shade triangle red if they have a positive texture area
     green if they have a negative texture area, or blue if degenerate area
     =====================
     */
    fun RB_ShowTexturePolarity(drawSurfs: Array<drawSurf_s>, numDrawSurfs: Int) {
        var i: Int
        var j: Int
        var drawSurf: drawSurf_s
        var tri: srfTriangles_s
        if (!r_showTexturePolarity!!.GetBool()) {
            return
        }
        qgl.qglDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY)
        Image.globalImages.BindNull()
        qgl.qglDisable(GL11.GL_STENCIL_TEST)
        tr_backend.GL_State(GLS_SRCBLEND_SRC_ALPHA or GLS_DSTBLEND_ONE_MINUS_SRC_ALPHA)
        qgl.qglColor3f(1.0f, 1.0f, 1.0f)
        i = 0
        while (i < numDrawSurfs) {
            drawSurf = drawSurfs[i]
            tri = drawSurf.geo!!
            if (tri.verts == null) {
                i++
                continue
            }
            RB_SimpleSurfaceSetup(drawSurf)
            qgl.qglBegin(GL11.GL_TRIANGLES)
            j = 0
            while (j < tri.numIndexes) {
                var a: idDrawVert
                var b: idDrawVert
                var c: idDrawVert
                val d0 = FloatArray(5)
                val d1 = FloatArray(5)
                var area: Float
                a = tri.verts!![tri.indexes!![j]]!!
                b = tri.verts!![tri.indexes!![j + 1]]!!
                c = tri.verts!![tri.indexes!![j + 2]]!!

                // VectorSubtract( b.xyz, a.xyz, d0 );
                d0[3] = b.st[0] - a.st[0]
                d0[4] = b.st[1] - a.st[1]
                // VectorSubtract( c.xyz, a.xyz, d1 );
                d1[3] = c.st[0] - a.st[0]
                d1[4] = c.st[1] - a.st[1]
                area = d0[3] * d1[4] - d0[4] * d1[3]
                if (abs(area) < 0.0001) {
                    qgl.qglColor4f(0.0f, 0.0f, 1.0f, 0.5f)
                } else if (area < 0) {
                    qgl.qglColor4f(1.0f, 0.0f, 0.0f, 0.5f)
                } else {
                    qgl.qglColor4f(0.0f, 1.0f, 0.0f, 0.5f)
                }
                qgl.qglVertex3fv(a.xyz.ToFloatPtr())
                qgl.qglVertex3fv(b.xyz.ToFloatPtr())
                qgl.qglVertex3fv(c.xyz.ToFloatPtr())
                j += 3
            }
            qgl.qglEnd()
            i++
        }
        tr_backend.GL_State(GLS_DEFAULT)
    }

    /*
     =====================
     RB_ShowUnsmoothedTangents

     Shade materials that are using unsmoothed tangents
     =====================
     */
    fun RB_ShowUnsmoothedTangents(drawSurfs: Array<drawSurf_s>, numDrawSurfs: Int) {
        var i: Int
        var j: Int
        var drawSurf: drawSurf_s
        var tri: srfTriangles_s
        if (!r_showUnsmoothedTangents!!.GetBool()) {
            return
        }
        qgl.qglDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY)
        Image.globalImages.BindNull()
        qgl.qglDisable(GL11.GL_STENCIL_TEST)
        tr_backend.GL_State(GLS_SRCBLEND_SRC_ALPHA or GLS_DSTBLEND_ONE_MINUS_SRC_ALPHA)
        qgl.qglColor4f(0.0f, 1.0f, 0.0f, 0.5f)
        i = 0
        while (i < numDrawSurfs) {
            drawSurf = drawSurfs[i]
            if (!drawSurf.material!!.UseUnsmoothedTangents()) {
                i++
                continue
            }
            RB_SimpleSurfaceSetup(drawSurf)
            tri = drawSurf.geo!!
            qgl.qglBegin(GL11.GL_TRIANGLES)
            j = 0
            while (j < tri.numIndexes) {
                var a: idDrawVert
                var b: idDrawVert
                var c: idDrawVert
                a = tri.verts!![tri.indexes!![j]]!!
                b = tri.verts!![tri.indexes!![j + 1]]!!
                c = tri.verts!![tri.indexes!![j + 2]]!!
                qgl.qglVertex3fv(a.xyz.ToFloatPtr())
                qgl.qglVertex3fv(b.xyz.ToFloatPtr())
                qgl.qglVertex3fv(c.xyz.ToFloatPtr())
                j += 3
            }
            qgl.qglEnd()
            i++
        }
        tr_backend.GL_State(GLS_DEFAULT)
    }

    /*
     =====================
     RB_ShowTangentSpace

     Shade a triangle by the RGB colors of its tangent space
     1 = tangents[0]
     2 = tangents[1]
     3 = normal
     =====================
     */
    fun RB_ShowTangentSpace(drawSurfs: Array<drawSurf_s>, numDrawSurfs: Int) {
        var i: Int
        var j: Int
        var drawSurf: drawSurf_s
        var tri: srfTriangles_s
        if (0 == r_showTangentSpace!!.GetInteger()) {
            return
        }
        qgl.qglDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY)
        Image.globalImages.BindNull()
        qgl.qglDisable(GL11.GL_STENCIL_TEST)
        tr_backend.GL_State(GLS_SRCBLEND_SRC_ALPHA or GLS_DSTBLEND_ONE_MINUS_SRC_ALPHA)
        i = 0
        while (i < numDrawSurfs) {
            drawSurf = drawSurfs[i]
            RB_SimpleSurfaceSetup(drawSurf)
            tri = drawSurf.geo!!
            if (null == tri.verts) {
                i++
                continue
            }
            qgl.qglBegin(GL11.GL_TRIANGLES)
            j = 0
            while (j < tri.numIndexes) {
                val v: idDrawVert
                v = tri.verts!![tri.indexes!![j]]!!
                if (r_showTangentSpace!!.GetInteger() == 1) {
                    qgl.qglColor4f(
                        0.5f + 0.5f * v.tangents[0][0],
                        0.5f + 0.5f * v.tangents[0][1],
                        0.5f + 0.5f * v.tangents[0][2],
                        0.5f
                    )
                } else if (r_showTangentSpace!!.GetInteger() == 2) {
                    qgl.qglColor4f(
                        0.5f + 0.5f * v.tangents[1][0],
                        0.5f + 0.5f * v.tangents[1][1],
                        0.5f + 0.5f * v.tangents[1][2],
                        0.5f
                    )
                } else {
                    qgl.qglColor4f(
                        0.5f + 0.5f * v.normal[0],
                        0.5f + 0.5f * v.normal[1],
                        0.5f + 0.5f * v.normal[2],
                        0.5f
                    )
                }
                qgl.qglVertex3fv(v.xyz.ToFloatPtr())
                j++
            }
            qgl.qglEnd()
            i++
        }
        tr_backend.GL_State(GLS_DEFAULT)
    }

    /*
     =====================
     RB_ShowVertexColor

     Draw each triangle with the solid vertex colors
     =====================
     */
    fun RB_ShowVertexColor(drawSurfs: Array<drawSurf_s>, numDrawSurfs: Int) {
        var i: Int
        var j: Int
        var drawSurf: drawSurf_s
        var tri: srfTriangles_s
        if (!r_showVertexColor!!.GetBool()) {
            return
        }
        qgl.qglDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY)
        Image.globalImages.BindNull()
        qgl.qglDisable(GL11.GL_STENCIL_TEST)
        tr_backend.GL_State(GLS_DEPTHFUNC_LESS)
        i = 0
        while (i < numDrawSurfs) {
            drawSurf = drawSurfs[i]
            RB_SimpleSurfaceSetup(drawSurf)
            tri = drawSurf.geo!!
            if (null == tri.verts) {
                i++
                continue
            }
            qgl.qglBegin(GL11.GL_TRIANGLES)
            j = 0
            while (j < tri.numIndexes) {
                val v: idDrawVert
                v = tri.verts!![tri.indexes!![j]]!!
                qgl.qglColor4ubv(v.color)
                qgl.qglVertex3fv(v.xyz.ToFloatPtr())
                j++
            }
            qgl.qglEnd()
            i++
        }
        tr_backend.GL_State(GLS_DEFAULT)
    }

    /*
     =====================
     RB_ShowNormals

     Debugging tool
     =====================
     */
    fun RB_ShowNormals(drawSurfs: Array<drawSurf_s>, numDrawSurfs: Int) {
        var i: Int
        var j: Int
        var drawSurf: drawSurf_s
        val end = idVec3()
        var tri: srfTriangles_s
        var size: Float
        val showNumbers: Boolean
        val pos = idVec3()
        if (r_showNormals!!.GetFloat() == 0.0f) {
            return
        }
        tr_backend.GL_State(GLS_POLYMODE_LINE)
        qgl.qglDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY)
        Image.globalImages.BindNull()
        qgl.qglDisable(GL11.GL_STENCIL_TEST)
        if (!r_debugLineDepthTest!!.GetBool()) {
            qgl.qglDisable(GL11.GL_DEPTH_TEST)
        } else {
            qgl.qglEnable(GL11.GL_DEPTH_TEST)
        }
        size = r_showNormals!!.GetFloat()
        if (size < 0.0f) {
            size = -size
            showNumbers = true
        } else {
            showNumbers = false
        }
        i = 0
        while (i < numDrawSurfs) {
            drawSurf = drawSurfs[i]
            RB_SimpleSurfaceSetup(drawSurf)
            tri = drawSurf.geo!!
            if (null == tri.verts) {
                i++
                continue
            }
            qgl.qglBegin(GL11.GL_LINES)
            j = 0
            while (j < tri.numVerts) {
                qgl.qglColor3f(0.0f, 0.0f, 1.0f)
                qgl.qglVertex3fv(tri.verts!![j]!!.xyz.ToFloatPtr())
                VectorMA(tri.verts!![j]!!.xyz, size, tri.verts!![j]!!.normal, end)
                qgl.qglVertex3fv(end.ToFloatPtr())
                qgl.qglColor3f(1.0f, 0.0f, 0.0f)
                qgl.qglVertex3fv(tri.verts!![j]!!.xyz.ToFloatPtr())
                VectorMA(tri.verts!![j]!!.xyz, size, tri.verts!![j]!!.tangents[0], end)
                qgl.qglVertex3fv(end.ToFloatPtr())
                qgl.qglColor3f(0.0f, 1.0f, 0.0f)
                qgl.qglVertex3fv(tri.verts!![j]!!.xyz.ToFloatPtr())
                VectorMA(tri.verts!![j]!!.xyz, size, tri.verts!![j]!!.tangents[1], end)
                qgl.qglVertex3fv(end.ToFloatPtr())
                j++
            }
            qgl.qglEnd()
            i++
        }
        if (showNumbers) {
            RB_SimpleWorldSetup()
            i = 0
            while (i < numDrawSurfs) {
                drawSurf = drawSurfs[i]
                tri = drawSurf.geo!!
                if (null == tri.verts) {
                    i++
                    continue
                }
                j = 0
                while (j < tri.numVerts) {
                    pos.set(
                        tr_main.R_LocalPointToGlobal(
                            drawSurf.space!!.modelMatrix,
                            tri.verts!![j]!!.xyz.plus(
                                tri.verts!![j]!!.tangents[0].plus(tri.verts!![j]!!.normal.times(0.2f))
                            )
                        )
                    )
                    RB_DrawText(
                        va("%d", j),
                        pos,
                        0.01f,
                        idDeviceContext.colorWhite,
                        backEnd!!.viewDef!!.renderView.viewaxis,
                        1
                    )
                    j++
                }
                j = 0
                while (j < tri.numIndexes) {
                    pos.set(
                        tr_main.R_LocalPointToGlobal(
                            drawSurf.space!!.modelMatrix,
                            (tri.verts!![tri.indexes!![j + 0]]!!.xyz.plus(
                                tri.verts!![tri.indexes!![j + 1]]!!.xyz.plus(
                                    tri.verts!![tri.indexes!![j + 2]]!!.xyz
                                )
                            )).times(1.0f / 3.0f).plus(tri.verts!![tri.indexes!![j + 0]]!!.normal.times(0.2f))
                        )
                    )
                    RB_DrawText(
                        va("%d", j / 3),
                        pos,
                        0.01f,
                        colorCyan,
                        backEnd!!.viewDef!!.renderView.viewaxis,
                        1
                    )
                    j += 3
                }
                i++
            }
        }
        qgl.qglEnable(GL11.GL_STENCIL_TEST)
    }

    /*
     =====================
     RB_ShowNormals

     Debugging tool
     =====================
     */
    fun RB_AltShowNormals(drawSurfs: Array<drawSurf_s>, numDrawSurfs: Int) {
        var i: Int
        var j: Int
        var k: Int
        var drawSurf: drawSurf_s
        val end = idVec3()
        var tri: srfTriangles_s
        if (r_showNormals!!.GetFloat() == 0.0f) {
            return
        }
        tr_backend.GL_State(GLS_DEFAULT)
        qgl.qglDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY)
        Image.globalImages.BindNull()
        qgl.qglDisable(GL11.GL_STENCIL_TEST)
        qgl.qglDisable(GL11.GL_DEPTH_TEST)
        i = 0
        while (i < numDrawSurfs) {
            drawSurf = drawSurfs[i]
            RB_SimpleSurfaceSetup(drawSurf)
            tri = drawSurf.geo!!
            qgl.qglBegin(GL11.GL_LINES)
            j = 0
            while (j < tri.numIndexes) {
                val v: Array<idDrawVert?> = arrayOfNulls(3)
                val mid = idVec3()
                v[0] = tri.verts!![tri.indexes!![j + 0]]
                v[1] = tri.verts!![tri.indexes!![j + 1]]
                v[2] = tri.verts!![tri.indexes!![j + 2]]

                // make the midpoint slightly above the triangle
                mid.set((v[0]!!.xyz.plus(v[1]!!.xyz).plus(v[2]!!.xyz)).times(1.0f / 3.0f))
                mid.plusAssign(tri.facePlanes!![j / 3]!!.Normal().times(0.1f))
                k = 0
                while (k < 3) {
                    val pos = idVec3()
                    pos.set((mid.plus(v[k]!!.xyz.times(3.0f))).times(0.25f))
                    qgl.qglColor3f(0.0f, 0.0f, 1.0f)
                    qgl.qglVertex3fv(pos.ToFloatPtr())
                    VectorMA(pos, r_showNormals!!.GetFloat(), v[k]!!.normal, end)
                    qgl.qglVertex3fv(end.ToFloatPtr())
                    qgl.qglColor3f(1.0f, 0.0f, 0.0f)
                    qgl.qglVertex3fv(pos.ToFloatPtr())
                    VectorMA(pos, r_showNormals!!.GetFloat(), v[k]!!.tangents[0], end)
                    qgl.qglVertex3fv(end.ToFloatPtr())
                    qgl.qglColor3f(0.0f, 1.0f, 0.0f)
                    qgl.qglVertex3fv(pos.ToFloatPtr())
                    VectorMA(pos, r_showNormals!!.GetFloat(), v[k]!!.tangents[1], end)
                    qgl.qglVertex3fv(end.ToFloatPtr())
                    qgl.qglColor3f(1.0f, 1.0f, 1.0f)
                    qgl.qglVertex3fv(pos.ToFloatPtr())
                    qgl.qglVertex3fv(v[k]!!.xyz.ToFloatPtr())
                    k++
                }
                j += 3
            }
            qgl.qglEnd()
            i++
        }
        qgl.qglEnable(GL11.GL_DEPTH_TEST)
        qgl.qglEnable(GL11.GL_STENCIL_TEST)
    }

    /*
     =====================
     RB_ShowTextureVectors

     Draw texture vectors in the center of each triangle
     =====================
     */
    fun RB_ShowTextureVectors(drawSurfs: Array<drawSurf_s>, numDrawSurfs: Int) {
        var i: Int
        var j: Int
        var drawSurf: drawSurf_s
        var tri: srfTriangles_s
        if (r_showTextureVectors!!.GetFloat() == 0.0f) {
            return
        }
        tr_backend.GL_State(GLS_DEPTHFUNC_LESS)
        qgl.qglDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY)
        Image.globalImages.BindNull()
        i = 0
        while (i < numDrawSurfs) {
            drawSurf = drawSurfs[i]
            tri = drawSurf.geo!!
            if (null == tri.verts) {
                i++
                continue
            }
            if (null == tri.facePlanes) {
                i++
                continue
            }
            RB_SimpleSurfaceSetup(drawSurf)

            // draw non-shared edges in yellow
            qgl.qglBegin(GL11.GL_LINES)
            j = 0
            while (j < tri.numIndexes) {
                val a: idDrawVert
                val b: idDrawVert
                val c: idDrawVert
                var area: Float
                var inva: Float
                val temp = idVec3()
                val d0 = FloatArray(5)
                val d1 = FloatArray(5)
                val mid = idVec3()
                val tangents: Array<idVec3> = idVec3.generateArray(2)
                a = tri.verts!![tri.indexes!![j + 0]]!!
                b = tri.verts!![tri.indexes!![j + 1]]!!
                c = tri.verts!![tri.indexes!![j + 2]]!!

                // make the midpoint slightly above the triangle
                mid.set((a.xyz.plus(b.xyz).plus(c.xyz)).times(1.0f / 3.0f))
                mid.plusAssign(tri.facePlanes!![j / 3]!!.Normal().times(0.1f))

                // calculate the texture vectors
                VectorSubtract(b.xyz, a.xyz, d0)
                d0[3] = b.st[0] - a.st[0]
                d0[4] = b.st[1] - a.st[1]
                VectorSubtract(c.xyz, a.xyz, d1)
                d1[3] = c.st[0] - a.st[0]
                d1[4] = c.st[1] - a.st[1]
                area = d0[3] * d1[4] - d0[4] * d1[3]
                if (area == 0.0f) {
                    j += 3
                    continue
                }
                inva = 1.0f / area
                temp[0] = (d0[0] * d1[4] - d0[4] * d1[0]) * inva
                temp[1] = (d0[1] * d1[4] - d0[4] * d1[1]) * inva
                temp[2] = (d0[2] * d1[4] - d0[4] * d1[2]) * inva
                temp.Normalize()
                tangents[0].set(temp)
                temp[0] = (d0[3] * d1[0] - d0[0] * d1[3]) * inva
                temp[1] = (d0[3] * d1[1] - d0[1] * d1[3]) * inva
                temp[2] = (d0[3] * d1[2] - d0[2] * d1[3]) * inva
                temp.Normalize()
                tangents[1].set(temp)

                // draw the tangents
                tangents[0].set(mid.plus(tangents[0].times(r_showTextureVectors!!.GetFloat())))
                tangents[1].set(mid.plus(tangents[1].times(r_showTextureVectors!!.GetFloat())))
                qgl.qglColor3f(1.0f, 0.0f, 0.0f)
                qgl.qglVertex3fv(mid.ToFloatPtr())
                qgl.qglVertex3fv(tangents[0].ToFloatPtr())
                qgl.qglColor3f(0.0f, 1.0f, 0.0f)
                qgl.qglVertex3fv(mid.ToFloatPtr())
                qgl.qglVertex3fv(tangents[1].ToFloatPtr())
                j += 3
            }
            qgl.qglEnd()
            i++
        }
    }

    /*
     =====================
     RB_ShowDominantTris

     Draw lines from each vertex to the dominant triangle center
     =====================
     */
    fun RB_ShowDominantTris(drawSurfs: Array<drawSurf_s>, numDrawSurfs: Int) {
        var i: Int
        var j: Int
        var drawSurf: drawSurf_s
        var tri: srfTriangles_s
        if (!r_showDominantTri!!.GetBool()) {
            return
        }
        tr_backend.GL_State(GLS_DEPTHFUNC_LESS)
        qgl.qglDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY)
        qgl.qglPolygonOffset(-1.0f, -2.0f)
        qgl.qglEnable(GL11.GL_POLYGON_OFFSET_LINE)
        Image.globalImages.BindNull()
        i = 0
        while (i < numDrawSurfs) {
            drawSurf = drawSurfs[i]
            tri = drawSurf.geo!!
            if (null == tri.verts) {
                i++
                continue
            }
            if (null == tri.dominantTris) {
                i++
                continue
            }
            RB_SimpleSurfaceSetup(drawSurf)
            qgl.qglColor3f(1.0f, 1.0f, 0.0f)
            qgl.qglBegin(GL11.GL_LINES)
            j = 0
            while (j < tri.numVerts) {
                val a: idDrawVert
                val b: idDrawVert
                val c: idDrawVert
                val mid = idVec3()

                // find the midpoint of the dominant tri
                a = tri.verts!![j]!!
                b = tri.verts!![tri.dominantTris!![j]!!.v2]!!
                c = tri.verts!![tri.dominantTris!![j]!!.v3]!!
                mid.set((a.xyz.plus(b.xyz.plus(c.xyz))).times(1.0f / 3.0f))
                qgl.qglVertex3fv(mid.ToFloatPtr())
                qgl.qglVertex3fv(a.xyz.ToFloatPtr())
                j++
            }
            qgl.qglEnd()
            i++
        }
        qgl.qglDisable(GL11.GL_POLYGON_OFFSET_LINE)
    }

    /*
     =====================
     RB_ShowEdges

     Debugging tool
     =====================
     */
    fun RB_ShowEdges(drawSurfs: Array<drawSurf_s>, numDrawSurfs: Int) {
        var i: Int
        var j: Int
        var k: Int
        var m: Int
        var n: Int
        var o: Int
        var drawSurf: drawSurf_s
        var tri: srfTriangles_s
        var edge: silEdge_t?
        var danglePlane: Int
        if (!r_showEdges!!.GetBool()) {
            return
        }
        tr_backend.GL_State(GLS_DEFAULT)
        qgl.qglDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY)
        Image.globalImages.BindNull()
        qgl.qglDisable(GL11.GL_DEPTH_TEST)
        i = 0
        while (i < numDrawSurfs) {
            drawSurf = drawSurfs[i]
            tri = drawSurf.geo!!
            val ac: Array<idDrawVert> = tri.verts as Array<idDrawVert>
            if (null == ac) {
                i++
                continue
            }
            RB_SimpleSurfaceSetup(drawSurf)

            // draw non-shared edges in yellow
            qgl.qglColor3f(1.0f, 1.0f, 0.0f)
            qgl.qglBegin(GL11.GL_LINES)
            j = 0
            while (j < tri.numIndexes) {
                k = 0
                while (k < 3) {
                    var l: Int
                    var i1: Int
                    var i2: Int
                    l = if ((k == 2)) 0 else k + 1
                    i1 = tri.indexes!![j + k]
                    i2 = tri.indexes!![j + l]

                    // if these are used backwards, the edge is shared
                    m = 0
                    while (m < tri.numIndexes) {
                        n = 0
                        while (n < 3) {
                            o = if ((n == 2)) 0 else n + 1
                            if (tri.indexes!![m + n] == i2 && tri.indexes!![m + o] == i1) {
                                break
                            }
                            n++
                        }
                        if (n != 3) {
                            break
                        }
                        m += 3
                    }

                    // if we didn't find a backwards listing, draw it in yellow
                    if (m == tri.numIndexes) {
                        qgl.qglVertex3fv(ac[i1].xyz.ToFloatPtr())
                        qgl.qglVertex3fv(ac[i2].xyz.ToFloatPtr())
                    }
                    k++
                }
                j += 3
            }
            qgl.qglEnd()

            // draw dangling sil edges in red
            if (null == tri.silEdges) {
                i++
                continue
            }

            // the plane number after all real planes
            // is the dangling edge
            danglePlane = tri.numIndexes / 3
            qgl.qglColor3f(1.0f, 0.0f, 0.0f)
            qgl.qglBegin(GL11.GL_LINES)
            j = 0
            while (j < tri.numSilEdges) {
                edge = tri.silEdges!![j]
                if (edge!!.p1 != danglePlane && edge.p2 != danglePlane) {
                    j++
                    continue
                }
                qgl.qglVertex3fv(ac[edge.v1].xyz.ToFloatPtr())
                qgl.qglVertex3fv(ac[edge.v2].xyz.ToFloatPtr())
                j++
            }
            qgl.qglEnd()
            i++
        }
        qgl.qglEnable(GL11.GL_DEPTH_TEST)
    }

    /*
     ==============
     RB_ShowLights

     Visualize all light volumes used in the current scene
     r_showLights 1	: just print volumes numbers, highlighting ones covering the view
     r_showLights 2	: also draw planes of each volume
     r_showLights 3	: also draw edges of each volume
     ==============
     */
    fun RB_ShowLights() {
        var light: idRenderLightLocal
        var count: Int
        var tri: srfTriangles_s?
        var vLight: viewLight_s?
        if (0 == r_showLights!!.GetInteger()) {
            return
        }

        // all volumes are expressed in world coordinates
        RB_SimpleWorldSetup()
        qgl.qglDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY)
        Image.globalImages.BindNull()
        qgl.qglDisable(GL11.GL_STENCIL_TEST)
        tr_backend.GL_Cull(cullType_t.CT_TWO_SIDED)
        qgl.qglDisable(GL11.GL_DEPTH_TEST)
        Common.common.Printf("volumes: ") // FIXME: not in back end!
        count = 0
        vLight = backEnd!!.viewDef!!.viewLights
        while (vLight != null) {
            light = vLight.lightDef!!
            count++
            tri = light.frustumTris

            // depth buffered planes
            if (r_showLights!!.GetInteger() >= 2) {
                tr_backend.GL_State(GLS_SRCBLEND_SRC_ALPHA or GLS_DSTBLEND_ONE_MINUS_SRC_ALPHA or GLS_DEPTHMASK)
                qgl.qglColor4f(0.0f, 0.0f, 1.0f, 0.25f)
                qgl.qglEnable(GL11.GL_DEPTH_TEST)
                tr_render.RB_RenderTriangleSurface(tri!!)
            }

            // non-hidden lines
            if (r_showLights!!.GetInteger() >= 3) {
                tr_backend.GL_State(GLS_POLYMODE_LINE or GLS_DEPTHMASK)
                qgl.qglDisable(GL11.GL_DEPTH_TEST)
                qgl.qglColor3f(1.0f, 1.0f, 1.0f)
                tr_render.RB_RenderTriangleSurface(tri!!)
            }
            var index: Int
            index = backEnd!!.viewDef!!.renderWorld!!.lightDefs.FindIndex(vLight.lightDef)
            if (vLight.viewInsideLight) {
                // view is in this volume
                Common.common.Printf("[%d] ", index)
            } else {
                Common.common.Printf("%d ", index)
            }
            vLight = vLight.next
        }
        qgl.qglEnable(GL11.GL_DEPTH_TEST)
        qgl.qglDisable(GL11.GL_POLYGON_OFFSET_LINE)
        qgl.qglDepthRange(0.0f, 1.0f)
        tr_backend.GL_State(GLS_DEFAULT)
        tr_backend.GL_Cull(cullType_t.CT_FRONT_SIDED)
        Common.common.Printf(" = %d total\n", count)
    }

    /*
     =====================
     RB_ShowPortals

     Debugging tool, won't work correctly with SMP or when mirrors are present
     =====================
     */
    fun RB_ShowPortals() {
        if (!r_showPortals!!.GetBool()) {
            return
        }

        // all portals are expressed in world coordinates
        RB_SimpleWorldSetup()
        Image.globalImages.BindNull()
        qgl.qglDisable(GL11.GL_DEPTH_TEST)
        tr_backend.GL_State(GLS_DEFAULT)
        backEnd!!.viewDef!!.renderWorld!!.ShowPortals()
        qgl.qglEnable(GL11.GL_DEPTH_TEST)
    }

    /*
     ================
     RB_ClearDebugText
     ================
     */
    fun RB_ClearDebugText(time: Int) {
        var i: Int
        var num: Int
        var text: debugText_s
        rb_debugTextTime = time
        if (0 == time) {
            // free up our strings
            rb_debugText = allocArray(debugText_s::class.java, rb_debugText.size)
            rb_numDebugText = 0
            return
        }

        // copy any text that still needs to be drawn
        i = 0.also({ num = it })
        while (i < rb_numDebugText) {
            text = rb_debugText[i]
            if (text.lifeTime > time) {
                if (num != i) {
                    rb_debugText[num] = text
                }
                num++
            }
            i++
        }
        rb_numDebugText = num
    }

    /*
     ================
     RB_AddDebugText
     ================
     */
    fun RB_AddDebugText(
        text: String?,
        origin: idVec3?,
        scale: Float,
        color: idVec4,
        viewAxis: idMat3,
        align: Int,
        lifetime: Int,
        depthTest: Boolean
    ) {
        val debugText: debugText_s
        if (rb_numDebugText < MAX_DEBUG_TEXT) {
            debugText = rb_debugText[rb_numDebugText++]
            debugText.text.set(text) //			= text;
            debugText.origin.set((origin)!!)
            debugText.scale = scale
            debugText.color.set(color)
            debugText.viewAxis.set(viewAxis)
            debugText.align = align
            debugText.lifeTime = rb_debugTextTime + lifetime
            debugText.depthTest = depthTest
        }
    }

    /*
     ================
     RB_DrawTextLength

     returns the length of the given text
     ================
     */
    fun RB_DrawTextLength(text: String?, scale: Float, len: Int): Float {
        var len: Int = len
        var i: Int
        var num: Int
        var index: Int
        var charIndex: Int
        var spacing: Float
        var textLen = 0.0f
        if (text != null && !text.isEmpty()) {
            if (0 == len) {
                len = text.length
            }
            i = 0
            while (i < len) {
                charIndex = text[i].code - 32
                if (charIndex < 0 || charIndex > simplex.NUM_SIMPLEX_CHARS) {
                    i++
                    continue
                }
                num = simplex.simplex[charIndex][0] * 2
                spacing = simplex.simplex[charIndex][1].toFloat()
                index = 2
                while (index - 2 < num) {
                    if (simplex.simplex[charIndex][index] < 0) {
                        index++
                        continue
                    }
                    index += 2
                    if (simplex.simplex[charIndex][index] < 0) {
                        index++
                        continue
                    }
                }
                textLen += spacing * scale
                i++
            }
        }
        return textLen
    }

    /*
     ================
     RB_DrawText

     oriented on the viewaxis
     align can be 0-left, 1-center (default), 2-right
     ================
     */
    fun RB_DrawText(text: String?, origin: idVec3, scale: Float, color: idVec4, viewAxis: idMat3, align: Int) {
        var i: Int
        var j: Int
        val len: Int
        var num: Int
        var index: Int
        var charIndex: Int
        var line: Int
        var textLen = 0.0f
        var spacing: Float
        val org = idVec3()
        val p1 = idVec3()
        val p2 = idVec3()
        if (text != null && !text.isEmpty()) {
            qgl.qglBegin(GL11.GL_LINES)
            qgl.qglColor3fv(color.ToFloatPtr())
            if (text[0] == '\n') {
                line = 1
            } else {
                line = 0
            }
            len = text.length
            i = 0
            while (i < len) {
                if (i == 0 || text[i] == '\n') {
                    org.set(origin.minus(viewAxis[2]).times(line * 36.0f * scale))
                    if (align != 0) {
                        j = 1
                        while (i + j <= len) {
                            if (i + j == len || text[i + j] == '\n') {
                                textLen = RB_DrawTextLength(text.substring(i), scale, j)
                                break
                            }
                            j++
                        }
                        if (align == 2) {
                            // right
                            org.plusAssign(viewAxis[1].times(textLen))
                        } else {
                            // center
                            org.plusAssign(viewAxis[1].times(textLen * 0.5f))
                        }
                    }
                    line++
                }
                charIndex = text[i].code - 32
                if (charIndex < 0 || charIndex > simplex.NUM_SIMPLEX_CHARS) {
                    i++
                    continue
                }
                num = simplex.simplex[charIndex][0] * 2
                spacing = simplex.simplex[charIndex][1].toFloat()
                index = 2
                while (index - 2 < num) {
                    if (simplex.simplex[charIndex][index] < 0) {
                        index++
                        continue
                    }
                    p1.set(
                        org.plus(
                            viewAxis[1].unaryMinus().times(scale * simplex.simplex[charIndex][index])
                        ).plus(viewAxis[2].times(scale * simplex.simplex[charIndex][index + 1]))
                    )
                    index += 2
                    if (simplex.simplex[charIndex][index] < 0) {
                        index++
                        continue
                    }
                    //				p2 = org + scale * simplex[charIndex][index] * -viewAxis[1] + scale * simplex[charIndex][index+1] * viewAxis[2];
                    p2.set(
                        org.plus(
                            viewAxis[1].unaryMinus().times(scale * simplex.simplex[charIndex][index])
                        ).plus(viewAxis[2].times(scale * simplex.simplex[charIndex][index + 1]))
                    )
                    qgl.qglVertex3fv(p1.ToFloatPtr())
                    qgl.qglVertex3fv(p2.ToFloatPtr())
                }
                org.minusAssign(viewAxis[1].times(spacing * scale))
                i++
            }
            qgl.qglEnd()
        }
    }

    /*
     ================
     RB_ShowDebugText
     ================
     */
    fun RB_ShowDebugText() {
        var i: Int
        var width: Int
        var text: debugText_s
        var text_index: Int
        if (0 == rb_numDebugText) {
            return
        }

        // all lines are expressed in world coordinates
        RB_SimpleWorldSetup()
        Image.globalImages.BindNull()
        width = r_debugLineWidth!!.GetInteger()
        if (width < 1) {
            width = 1
        } else if (width > 10) {
            width = 10
        }

        // draw lines
        tr_backend.GL_State(GLS_POLYMODE_LINE)
        qgl.qglLineWidth(width.toFloat())
        if (!r_debugLineDepthTest!!.GetBool()) {
            qgl.qglDisable(GL11.GL_DEPTH_TEST)
        }
        text = rb_debugText[0.also({ text_index = it })]
        i = 0
        while (i < rb_numDebugText) {
            if (!text.depthTest) {
                RB_DrawText(
                    text.text.toString(),
                    text.origin,
                    text.scale,
                    text.color,
                    text.viewAxis,
                    text.align
                )
            }
            i++
            text = rb_debugText[text_index++]
        }
        if (!r_debugLineDepthTest!!.GetBool()) {
            qgl.qglEnable(GL11.GL_DEPTH_TEST)
        }
        text = rb_debugText[0.also({ text_index = it })]
        i = 0
        while (i < rb_numDebugText) {
            if (text.depthTest) {
                RB_DrawText(
                    text.text.toString(),
                    text.origin,
                    text.scale,
                    text.color,
                    text.viewAxis,
                    text.align
                )
            }
            i++
            text = rb_debugText[text_index++]
        }
        qgl.qglLineWidth(1.0f)
        tr_backend.GL_State(GLS_DEFAULT)
    }

    /*
     ================
     RB_ClearDebugLines
     ================
     */
    fun RB_ClearDebugLines(time: Int) {
        var i: Int
        var num: Int
        var line: debugLine_s?
        var line_index: Int
        rb_debugLineTime = time
        if (0 == time) {
            rb_numDebugLines = 0
            return
        }

        // copy any lines that still need to be drawn
        num = 0
        line = rb_debugLines[0.also({ line_index = it })]
        i = 0
        while (i < rb_numDebugLines) {
            if (line!!.lifeTime > time) {
                if (num != i) {
                    rb_debugLines[num] = line
                }
                num++
            }
            i++
            line = rb_debugLines[line_index++]
        }
        rb_numDebugLines = num
    }

    /*
     ================
     RB_AddDebugLine
     ================
     */
    fun RB_AddDebugLine(color: idVec4, start: idVec3, end: idVec3, lifeTime: Int, depthTest: Boolean) {
        val line: debugLine_s
        if (rb_numDebugLines < MAX_DEBUG_LINES) {
            line = debugLine_s()
            rb_debugLines[rb_numDebugLines++] = line
            line.rgb.set(color)
            line.start.set(start)
            line.end.set(end)
            line.depthTest = depthTest
            line.lifeTime = rb_debugLineTime + lifeTime
        }
    }

    /*
     ================
     RB_ShowDebugLines
     ================
     */
    fun RB_ShowDebugLines() {
        var i: Int
        var width: Int
        var line: debugLine_s
        var line_index: Int
        if (0 == rb_numDebugLines) {
            return
        }

        // all lines are expressed in world coordinates
        RB_SimpleWorldSetup()
        Image.globalImages.BindNull()
        width = r_debugLineWidth!!.GetInteger()
        if (width < 1) {
            width = 1
        } else if (width > 10) {
            width = 10
        }

        // draw lines
        tr_backend.GL_State(GLS_POLYMODE_LINE) //| GLS_DEPTHMASK ); //| GLS_SRCBLEND_ONE | GLS_DSTBLEND_ONE );
        qgl.qglLineWidth(width.toFloat())
        if (!r_debugLineDepthTest!!.GetBool()) {
            qgl.qglDisable(GL11.GL_DEPTH_TEST)
        }
        qgl.qglBegin(GL11.GL_LINES)
        line = rb_debugLines[0.also({ line_index = it })]!!
        i = 0
        while (i < rb_numDebugLines) {
            if (!line.depthTest) {
                qgl.qglColor3fv(line.rgb.ToFloatPtr())
                qgl.qglVertex3fv(line.start.ToFloatPtr())
                qgl.qglVertex3fv(line.end.ToFloatPtr())
            }
            i++
            line = rb_debugLines[line_index++]!!
        }
        qgl.qglEnd()
        if (!r_debugLineDepthTest!!.GetBool()) {
            qgl.qglEnable(GL11.GL_DEPTH_TEST)
        }
        qgl.qglBegin(GL11.GL_LINES)
        line = rb_debugLines[0.also({ line_index = it })]!!
        i = 0
        while (i < rb_numDebugLines) {
            if (line.depthTest) {
                qgl.qglColor4fv(line.rgb.ToFloatPtr())
                qgl.qglVertex3fv(line.start.ToFloatPtr())
                qgl.qglVertex3fv(line.end.ToFloatPtr())
            }
            i++
            line = rb_debugLines[line_index++]!!
        }
        qgl.qglEnd()
        qgl.qglLineWidth(1.0f)
        tr_backend.GL_State(GLS_DEFAULT)
    }

    /*
     ================
     RB_ClearDebugPolygons
     ================
     */
    fun RB_ClearDebugPolygons(time: Int) {
        var i: Int
        var num: Int
        var poly: debugPolygon_s
        var poly_index: Int
        rb_debugPolygonTime = time
        if (0 == time) {
            rb_numDebugPolygons = 0
            return
        }

        // copy any polygons that still need to be drawn
        num = 0
        poly = rb_debugPolygons[0.also({ poly_index = it })]
        i = 0
        while (i < rb_numDebugPolygons) {
            if (poly.lifeTime > time) {
                if (num != i) {
                    rb_debugPolygons[num] = poly
                }
                num++
            }
            i++
            poly = rb_debugPolygons[++poly_index]
        }
        rb_numDebugPolygons = num
    }

    /*
     ================
     RB_AddDebugPolygon
     ================
     */
    fun RB_AddDebugPolygon(color: idVec4, winding: idWinding, lifeTime: Int, depthTest: Boolean) {
        val poly: debugPolygon_s
        if (rb_numDebugPolygons < MAX_DEBUG_POLYGONS) {
            poly = rb_debugPolygons[rb_numDebugPolygons++]
            poly.rgb.set(color)
            poly.winding = winding
            poly.depthTest = depthTest
            poly.lifeTime = rb_debugPolygonTime + lifeTime
        }
    }

    /*
     ================
     RB_ShowDebugPolygons
     ================
     */
    fun RB_ShowDebugPolygons() {
        var i: Int
        var j: Int
        var poly: debugPolygon_s
        var poly_index: Int
        if (0 == rb_numDebugPolygons) {
            return
        }

        // all lines are expressed in world coordinates
        RB_SimpleWorldSetup()
        Image.globalImages.BindNull()
        qgl.qglDisable(GL11.GL_TEXTURE_2D)
        qgl.qglDisable(GL11.GL_STENCIL_TEST)
        qgl.qglEnable(GL11.GL_DEPTH_TEST)
        if (r_debugPolygonFilled!!.GetBool()) {
            tr_backend.GL_State(GLS_SRCBLEND_SRC_ALPHA or GLS_DSTBLEND_ONE_MINUS_SRC_ALPHA or GLS_DEPTHMASK)
            qgl.qglPolygonOffset(-1.0f, -2.0f)
            qgl.qglEnable(GL11.GL_POLYGON_OFFSET_FILL)
        } else {
            tr_backend.GL_State(GLS_POLYMODE_LINE)
            qgl.qglPolygonOffset(-1.0f, -2.0f)
            qgl.qglEnable(GL11.GL_POLYGON_OFFSET_LINE)
        }
        poly = rb_debugPolygons[0.also({ poly_index = it })]
        i = 0
        while (i < rb_numDebugPolygons) {

//		if ( !poly.depthTest ) {
            qgl.qglColor4fv(poly.rgb.ToFloatPtr())
            qgl.qglBegin(GL11.GL_POLYGON)
            j = 0
            while (j < poly.winding.GetNumPoints()) {
                qgl.qglVertex3fv(poly.winding[j].ToFloatPtr())
                j++
            }
            qgl.qglEnd()
            i++
            poly = rb_debugPolygons[++poly_index]
        }
        tr_backend.GL_State(GLS_DEFAULT)
        if (r_debugPolygonFilled!!.GetBool()) {
            qgl.qglDisable(GL11.GL_POLYGON_OFFSET_FILL)
        } else {
            qgl.qglDisable(GL11.GL_POLYGON_OFFSET_LINE)
        }
        qgl.qglDepthRange(0.0f, 1.0f)
        tr_backend.GL_State(GLS_DEFAULT)
    }

    fun RB_TestGamma() {
        val image: Array<Array<ByteArray>> =
            Array(G_HEIGHT, { Array(G_WIDTH, { ByteArray(4) }) })
        var i: Int
        var j: Int
        var c: Int
        var comp: Int
        var v: Int
        var dither: Int
        var mask: Int
        var y: Int
        if (r_testGamma!!.GetInteger() <= 0) {
            return
        }
        v = r_testGamma!!.GetInteger()
        if (v <= 1 || v >= 196) {
            v = 128
        }
        mask = 0
        while (mask < 8) {
            y = mask * BAR_HEIGHT
            c = 0
            while (c < 4) {
                v = c * 64 + 32
                // solid color
                i = 0
                while (i < BAR_HEIGHT / 2) {
                    j = 0
                    while (j < G_WIDTH / 4) {
                        comp = 0
                        while (comp < 3) {
                            if ((mask and (1 shl comp)) != 0) {
                                image[y + i][c * G_WIDTH / 4 + j][comp] = v.toByte()
                            }
                            comp++
                        }
                        j++
                    }
                    // dithered color
                    j = 0
                    while (j < G_WIDTH / 4) {
                        if (((i xor j) and 1) != 0) {
                            dither = c * 64
                        } else {
                            dither = c * 64 + 63
                        }
                        comp = 0
                        while (comp < 3) {
                            if ((mask and (1 shl comp)) != 0) {
                                image[y + (BAR_HEIGHT / 2) + i][c * G_WIDTH / 4 + j][comp] = dither.toByte()
                            }
                            comp++
                        }
                        j++
                    }
                    i++
                }
                c++
            }
            mask++
        }

        // draw geometrically increasing steps in the bottom row
        y = 0 * BAR_HEIGHT
        var scale = 1.0f
        c = 0
        while (c < 4) {
            v = (64 * scale).toInt()
            if (v < 0) {
                v = 0
            } else if (v > 255) {
                v = 255
            }
            scale = scale * 1.5f
            i = 0
            while (i < BAR_HEIGHT) {
                j = 0
                while (j < G_WIDTH / 4) {
                    image[y + i][c * G_WIDTH / 4 + j][0] = v.toByte()
                    image[y + i][c * G_WIDTH / 4 + j][1] = v.toByte()
                    image[y + i][c * G_WIDTH / 4 + j][2] = v.toByte()
                    j++
                }
                i++
            }
            c++
        }
        qgl.qglLoadIdentity()
        qgl.qglMatrixMode(GL11.GL_PROJECTION)
        tr_backend.GL_State(GLS_DEPTHFUNC_ALWAYS)
        qgl.qglColor3f(1.0f, 1.0f, 1.0f)
        qgl.qglPushMatrix()
        qgl.qglLoadIdentity()
        qgl.qglDisable(GL11.GL_TEXTURE_2D)
        qgl.qglOrtho(0.0, 1.0, 0.0, 1.0, -1.0, 1.0)
        qgl.qglRasterPos2f(0.01f, 0.01f)
        qgl.qglDrawPixels(G_WIDTH, G_HEIGHT, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, image as Array<Array<ByteArray?>?>)
        qgl.qglPopMatrix()
        qgl.qglEnable(GL11.GL_TEXTURE_2D)
        qgl.qglMatrixMode(GL11.GL_MODELVIEW)
    }

    /*
     ==================
     RB_TestGammaBias
     ==================
     */
    fun RB_TestGammaBias() {
        val image: Array<Array<ByteArray>> =
            Array(G_HEIGHT, { Array(G_WIDTH, { ByteArray(4) }) })
        if (r_testGammaBias!!.GetInteger() <= 0) {
            return
        }
        var y = 0
        var bias: Int = -40
        while (bias < 40) {
            var scale = 1.0f
            for (c in 0..3) {
                var v: Int = (64 * scale + bias).toInt()
                scale = scale * 1.5f
                if (v < 0) {
                    v = 0
                } else if (v > 255) {
                    v = 255
                }
                for (i in 0 until BAR_HEIGHT) {
                    for (j in 0 until (G_WIDTH / 4)) {
                        image[y + i][c * G_WIDTH / 4 + j][0] = v.toByte()
                        image[y + i][c * G_WIDTH / 4 + j][1] = v.toByte()
                        image[y + i][c * G_WIDTH / 4 + j][2] = v.toByte()
                    }
                }
            }
            bias += 10
            y += BAR_HEIGHT
        }
        qgl.qglLoadIdentity()
        qgl.qglMatrixMode(GL11.GL_PROJECTION)
        tr_backend.GL_State(GLS_DEPTHFUNC_ALWAYS)
        qgl.qglColor3f(1.0f, 1.0f, 1.0f)
        qgl.qglPushMatrix()
        qgl.qglLoadIdentity()
        qgl.qglDisable(GL11.GL_TEXTURE_2D)
        qgl.qglOrtho(0.0, 1.0, 0.0, 1.0, -1.0, 1.0)
        qgl.qglRasterPos2f(0.01f, 0.01f)
        qgl.qglDrawPixels(G_WIDTH, G_HEIGHT, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, image as Array<Array<ByteArray?>?>)
        qgl.qglPopMatrix()
        qgl.qglEnable(GL11.GL_TEXTURE_2D)
        qgl.qglMatrixMode(GL11.GL_MODELVIEW)
    }

    /*
     ================
     RB_TestImage

     Display a single image over most of the screen
     ================
     */
    fun RB_TestImage() {
        val image: idImage?
        val max: Int
        var w: Float
        val h: Float
        image = tr.testImage
        if (null == image) {
            return
        }
        if (tr.testVideo != null) {
            val cin: cinData_t
            cin =
                tr.testVideo!!.ImageForTime((1000 * (backEnd!!.viewDef!!.floatTime - tr.testVideoStartTime)).toInt())
            if (cin.image != null) {
                image.UploadScratch(cin.image, cin.imageWidth, cin.imageHeight)
            } else {
                tr.testImage = null
                return
            }
            w = 0.25f
            h = 0.25f
        } else {
            max = max(image.uploadWidth._val.toFloat(), image.uploadHeight._val.toFloat()).toInt()
            w = 0.25f * image.uploadWidth._val / max
            h = 0.25f * image.uploadHeight._val / max
            w *= glConfig.vidHeight.toFloat() / glConfig.vidWidth
        }
        qgl.qglLoadIdentity()
        qgl.qglMatrixMode(GL11.GL_PROJECTION)
        tr_backend.GL_State(GLS_DEPTHFUNC_ALWAYS or GLS_SRCBLEND_SRC_ALPHA or GLS_DSTBLEND_ONE_MINUS_SRC_ALPHA)
        qgl.qglColor3f(1.0f, 1.0f, 1.0f)
        qgl.qglPushMatrix()
        qgl.qglLoadIdentity()
        qgl.qglOrtho(0.0, 1.0, 0.0, 1.0, -1.0, 1.0)
        tr.testImage!!.Bind()
        qgl.qglBegin(GL11.GL_QUADS)
        qgl.qglTexCoord2f(0.0f, 1.0f)
        qgl.qglVertex2f(0.5f - w, 0.0f)
        qgl.qglTexCoord2f(0.0f, 0.0f)
        qgl.qglVertex2f(0.5f - w, h * 2)
        qgl.qglTexCoord2f(1.0f, 0.0f)
        qgl.qglVertex2f(0.5f + w, h * 2)
        qgl.qglTexCoord2f(1.0f, 1.0f)
        qgl.qglVertex2f(0.5f + w, 0.0f)
        qgl.qglEnd()
        qgl.qglPopMatrix()
        qgl.qglMatrixMode(GL11.GL_MODELVIEW)
    }

    /*
     =================
     RB_RenderDebugTools
     =================
     */
    fun RB_RenderDebugTools(drawSurfs: Array<drawSurf_s?>?, numDrawSurfs: Int) {
        // don't do anything if this was a 2D rendering
        if (null == backEnd!!.viewDef!!.viewEntitys) {
            return
        }
        tr_backend.GL_State(GLS_DEFAULT)
        backEnd!!.currentScissor = backEnd!!.viewDef!!.scissor
        qgl.qglScissor(
            backEnd!!.viewDef!!.viewport.x1 + backEnd!!.currentScissor!!.x1,
            backEnd!!.viewDef!!.viewport.y1 + backEnd!!.currentScissor!!.y1,
            backEnd!!.currentScissor!!.x2 + 1 - backEnd!!.currentScissor!!.x1,
            backEnd!!.currentScissor!!.y2 + 1 - backEnd!!.currentScissor!!.y1
        )
        RB_ShowLightCount()
        RB_ShowShadowCount()
        RB_ShowTexturePolarity(drawSurfs as Array<drawSurf_s>, numDrawSurfs)
        RB_ShowTangentSpace(drawSurfs, numDrawSurfs)
        RB_ShowVertexColor(drawSurfs, numDrawSurfs)
        RB_ShowTris(drawSurfs, numDrawSurfs)
        RB_ShowUnsmoothedTangents(drawSurfs, numDrawSurfs)
        RB_ShowSurfaceInfo(drawSurfs, numDrawSurfs)
        RB_ShowEdges(drawSurfs, numDrawSurfs)
        RB_ShowNormals(drawSurfs, numDrawSurfs)
        RB_ShowViewEntitys(backEnd!!.viewDef!!.viewEntitys)
        RB_ShowLights()
        RB_ShowTextureVectors(drawSurfs, numDrawSurfs)
        RB_ShowDominantTris(drawSurfs, numDrawSurfs)
        if (r_testGamma!!.GetInteger() > 0) {    // test here so stack check isn't so damn slow on debug builds
            RB_TestGamma()
        }
        if (r_testGammaBias!!.GetInteger() > 0) {
            RB_TestGammaBias()
        }
        RB_TestImage()
        RB_ShowPortals()
        RB_ShowSilhouette()
        RB_ShowDepthBuffer()
        RB_ShowIntensity()
        RB_ShowDebugLines()
        RB_ShowDebugText()
        RB_ShowDebugPolygons()
        tr_trace.RB_ShowTrace(drawSurfs, numDrawSurfs)
    }

    /*
     =================
     RB_ShutdownDebugTools
     =================
     */
    fun RB_ShutdownDebugTools() {
        for (i in 0 until MAX_DEBUG_POLYGONS) {
            rb_debugPolygons[i].winding.Clear()
        }
    }

    class debugLine_s {
        var depthTest: Boolean = false
        val end: idVec3 = idVec3()
        var lifeTime: Int = 0
        val rgb: idVec4 = idVec4()
        val start: idVec3 = idVec3()
    }

    class debugText_s {
        var align: Int
        val color: idVec4 = idVec4()
        var depthTest: Boolean
        var lifeTime: Int
        val origin: idVec3
        var scale: Float
        var text: idStr
        val viewAxis: idMat3

        init {
            text = idStr()
            origin = idVec3()
            viewAxis = idMat3()
            lifeTime = 0
            align = lifeTime
            scale = align.toFloat()
            depthTest = false
        }
    }

    class debugPolygon_s {
        var depthTest: Boolean = false
        var lifeTime: Int = 0
        val rgb: idVec4 = idVec4()
        var winding: idWinding = idWinding()
    }
}

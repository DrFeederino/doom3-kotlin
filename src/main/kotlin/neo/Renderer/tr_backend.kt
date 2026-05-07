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

import neo.Renderer.*
import neo.Renderer.Image.idImage
import neo.Renderer.Material.cullType_t
import neo.framework.Common
import neo.idlib.containers.CInt
import neo.sys.win_glimp.GLimp_SwapBuffers
import neo.sys.win_shared.Sys_Milliseconds
import org.lwjgl.opengl.ARBMultitexture
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL12
import org.lwjgl.opengl.GL13
import java.nio.*

object tr_backend {
    /*
     ====================
     RB_ExecuteBackEndCommands

     This function will be called syncronously if running without
     smp extensions, or asyncronously by another thread.
     ====================
     */
    var backEndStartTime: Int = 0
    var backEndFinishTime: Int = 0

    /*
     ====================
     GL_State
     This routine is responsible for setting the most commonly changed state
     ====================
     */
    /*
     ======================
     RB_SetDefaultGLState

     This should initialize all GL state that any part of the entire program
     may touch, including the editor.
     ======================
     */
    fun RB_SetDefaultGLState() {
        var i: Int
        qgl.qglClearDepth(1.0)
        qgl.qglColor4f(1.0f, 1.0f, 1.0f, 1.0f)

        // the vertex array is always enabled
        qgl.qglEnableClientState(GL11.GL_VERTEX_ARRAY)
        qgl.qglEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY)
        qgl.qglDisableClientState(GL11.GL_COLOR_ARRAY)

        //
        // make sure our GL state vector is set correctly
        //
        backEnd!!.glState = glstate_t() //memset(backEnd.glState, 0, sizeof(backEnd.glState));
        backEnd!!.glState.forceGlState = true
        qgl.qglColorMask(1, 1, 1, 1)
        qgl.qglEnable(GL11.GL_DEPTH_TEST)
        qgl.qglEnable(GL11.GL_BLEND)
        qgl.qglEnable(GL11.GL_SCISSOR_TEST)
        qgl.qglEnable(GL11.GL_CULL_FACE)
        qgl.qglDisable(GL11.GL_LIGHTING)
        qgl.qglDisable(GL11.GL_LINE_STIPPLE)
        qgl.qglDisable(GL11.GL_STENCIL_TEST)
        qgl.qglPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL)
        qgl.qglDepthMask(qgl.qGL_TRUE)
        qgl.qglDepthFunc(GL11.GL_ALWAYS)
        qgl.qglCullFace(GL11.GL_FRONT_AND_BACK)
        qgl.qglShadeModel(GL11.GL_SMOOTH)
        if (r_useScissor!!.GetBool()) {
            qgl.qglScissor(0, 0, glConfig.vidWidth, glConfig.vidHeight)
        }
        i = glConfig.maxTextureUnits - 1
        while (i >= 0) {
            GL_SelectTexture(i)

            // object linear texgen is our default
            qgl.qglTexGenf(GL11.GL_S, GL11.GL_TEXTURE_GEN_MODE, GL11.GL_OBJECT_LINEAR.toFloat())
            qgl.qglTexGenf(GL11.GL_T, GL11.GL_TEXTURE_GEN_MODE, GL11.GL_OBJECT_LINEAR.toFloat())
            qgl.qglTexGenf(GL11.GL_R, GL11.GL_TEXTURE_GEN_MODE, GL11.GL_OBJECT_LINEAR.toFloat())
            qgl.qglTexGenf(GL11.GL_Q, GL11.GL_TEXTURE_GEN_MODE, GL11.GL_OBJECT_LINEAR.toFloat())
            GL_TexEnv(GL11.GL_MODULATE)
            qgl.qglDisable(GL11.GL_TEXTURE_2D)
            if (glConfig.texture3DAvailable) {
                qgl.qglDisable(GL12.GL_TEXTURE_3D)
            }
            if (glConfig.cubeMapAvailable) {
                qgl.qglDisable(GL13.GL_TEXTURE_CUBE_MAP /*_EXT*/)
            }
            i--
        }
    }

    //=============================================================================
    /*
     ====================
     GL_SelectTexture
     ====================
     */
    fun GL_SelectTexture(unit: Int) {
        if (backEnd!!.glState.currenttmu == unit) {
            return
        }
        if (unit < 0 || (unit >= glConfig.maxTextureUnits && unit >= glConfig.maxTextureImageUnits)) {
            Common.common.Warning("GL_SelectTexture: unit = %d", unit)
            return
        }
        qgl.qglActiveTextureARB(ARBMultitexture.GL_TEXTURE0_ARB + unit)
        qgl.qglClientActiveTextureARB(ARBMultitexture.GL_TEXTURE0_ARB + unit)
        backEnd!!.glState.currenttmu = unit
    }

    /*
     ====================
     GL_Cull
     This handles the flipping needed when the view being
     rendered is a mirored view.
     ====================
     */
    fun GL_Cull(cullType: Int) {
        if (backEnd!!.glState.faceCulling == cullType) {
            return
        }
        if (cullType == cullType_t.CT_TWO_SIDED.ordinal) {
            qgl.qglDisable(GL11.GL_CULL_FACE)
        } else {
            if (backEnd!!.glState.faceCulling == cullType_t.CT_TWO_SIDED.ordinal) {
                qgl.qglEnable(GL11.GL_CULL_FACE)
            }
            if (cullType == cullType_t.CT_BACK_SIDED.ordinal) {
                if (backEnd!!.viewDef!!.isMirror) {
                    qgl.qglCullFace(GL11.GL_FRONT)
                } else {
                    qgl.qglCullFace(GL11.GL_BACK)
                }
            } else {
                if (backEnd!!.viewDef!!.isMirror) {
                    qgl.qglCullFace(GL11.GL_BACK)
                } else {
                    qgl.qglCullFace(GL11.GL_FRONT)
                }
            }
        }
        backEnd!!.glState.faceCulling = cullType
    }

    fun GL_Cull(cullType: Enum<cullType_t>) {
        GL_Cull(cullType.ordinal)
    }

    /*
     ====================
     GL_TexEnv
     ====================
     */
    fun GL_TexEnv(env: Int) {
        val tmu: tmu_t
        tmu = backEnd!!.glState.tmu[backEnd!!.glState.currenttmu]!!
        if (env == tmu.texEnv) {
            return
        }
        tmu.texEnv = env
        when (env) {
            GL13.GL_COMBINE, GL11.GL_MODULATE, GL11.GL_REPLACE, GL11.GL_DECAL, GL11.GL_ADD -> qgl.qglTexEnvi(
                GL11.GL_TEXTURE_ENV,
                GL11.GL_TEXTURE_ENV_MODE,
                env
            )

            else -> Common.common.Error("GL_TexEnv: invalid env '%d' passed\n", env)
        }
    }

    /*
     =================
     GL_ClearStateDelta
     Clears the state delta bits, so the next GL_State
     will set every item
     =================
     */
    fun GL_ClearStateDelta() {
        backEnd!!.glState.forceGlState = true
    }

    /*
     ============================================================================

     RENDER BACK END THREAD FUNCTIONS

     ============================================================================
     */
    fun GL_State(stateBits: Int) {
        val diff: Int
        if (!r_useStateCaching!!.GetBool() || backEnd!!.glState.forceGlState) {
            // make sure everything is set all the time, so we
            // can see if our delta checking is screwing up
            diff = -1
            backEnd!!.glState.forceGlState = false
        } else {
            diff = stateBits xor backEnd!!.glState.glStateBits
            if (0 == diff) {
                return
            }
        }

        //
        // check depthFunc bits
        //
        if ((diff and (GLS_DEPTHFUNC_EQUAL or GLS_DEPTHFUNC_LESS or GLS_DEPTHFUNC_ALWAYS)) != 0) {
            if ((stateBits and GLS_DEPTHFUNC_EQUAL) != 0) {
                qgl.qglDepthFunc(GL11.GL_EQUAL)
            } else if ((stateBits and GLS_DEPTHFUNC_ALWAYS) != 0) {
                qgl.qglDepthFunc(GL11.GL_ALWAYS)
            } else {
                qgl.qglDepthFunc(GL11.GL_LEQUAL)
            }
        }

        //
        // check blend bits
        //
        if ((diff and (GLS_SRCBLEND_BITS or GLS_DSTBLEND_BITS)) != 0) {
            val  /*GLenum*/srcFactor: Int
            val dstFactor: Int
            when (stateBits and GLS_SRCBLEND_BITS) {
                GLS_SRCBLEND_ZERO -> srcFactor = GL11.GL_ZERO
                GLS_SRCBLEND_ONE -> srcFactor = GL11.GL_ONE
                GLS_SRCBLEND_DST_COLOR -> srcFactor = GL11.GL_DST_COLOR
                GLS_SRCBLEND_ONE_MINUS_DST_COLOR -> srcFactor = GL11.GL_ONE_MINUS_DST_COLOR
                GLS_SRCBLEND_SRC_ALPHA -> srcFactor = GL11.GL_SRC_ALPHA
                GLS_SRCBLEND_ONE_MINUS_SRC_ALPHA -> srcFactor = GL11.GL_ONE_MINUS_SRC_ALPHA
                GLS_SRCBLEND_DST_ALPHA -> srcFactor = GL11.GL_DST_ALPHA
                GLS_SRCBLEND_ONE_MINUS_DST_ALPHA -> srcFactor = GL11.GL_ONE_MINUS_DST_ALPHA
                GLS_SRCBLEND_ALPHA_SATURATE -> srcFactor = GL11.GL_SRC_ALPHA_SATURATE
                else -> {
                    srcFactor = GL11.GL_ONE
                    Common.common.Error("GL_State: invalid src blend state bits\n")
                }
            }
            when (stateBits and GLS_DSTBLEND_BITS) {
                GLS_DSTBLEND_ZERO -> dstFactor = GL11.GL_ZERO
                GLS_DSTBLEND_ONE -> dstFactor = GL11.GL_ONE
                GLS_DSTBLEND_SRC_COLOR -> dstFactor = GL11.GL_SRC_COLOR
                GLS_DSTBLEND_ONE_MINUS_SRC_COLOR -> dstFactor = GL11.GL_ONE_MINUS_SRC_COLOR
                GLS_DSTBLEND_SRC_ALPHA -> dstFactor = GL11.GL_SRC_ALPHA
                GLS_DSTBLEND_ONE_MINUS_SRC_ALPHA -> dstFactor = GL11.GL_ONE_MINUS_SRC_ALPHA
                GLS_DSTBLEND_DST_ALPHA -> dstFactor = GL11.GL_DST_ALPHA
                GLS_DSTBLEND_ONE_MINUS_DST_ALPHA -> dstFactor = GL11.GL_ONE_MINUS_DST_ALPHA
                else -> {
                    dstFactor = GL11.GL_ONE
                    Common.common.Error("GL_State: invalid dst blend state bits\n")
                }
            }
            qgl.qglBlendFunc(srcFactor, dstFactor)
        }

        //
        // check depthmask
        //
        if ((diff and GLS_DEPTHMASK) != 0) {
            if ((stateBits and GLS_DEPTHMASK) != 0) {
                qgl.qglDepthMask(qgl.qGL_FALSE)
            } else {
                qgl.qglDepthMask(qgl.qGL_TRUE)
            }
        }

        //
        // check colormask
        //
        if ((diff and (GLS_REDMASK or GLS_GREENMASK or GLS_BLUEMASK or GLS_ALPHAMASK)) != 0) {
            val r: Boolean = (stateBits and GLS_REDMASK) == 0
            val g: Boolean = (stateBits and GLS_GREENMASK) == 0
            val b: Boolean = (stateBits and GLS_BLUEMASK) == 0
            val a: Boolean = (stateBits and GLS_ALPHAMASK) == 0
            qgl.qglColorMask(r, g, b, a)
        }

        //
        // fill/line mode
        //
        if ((diff and GLS_POLYMODE_LINE) != 0) {
            if ((stateBits and GLS_POLYMODE_LINE) != 0) {
                qgl.qglPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_LINE)
            } else {
                qgl.qglPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL)
            }
        }

        //
        // alpha test
        //
        if ((diff and GLS_ATEST_BITS) != 0) {
            when (stateBits and GLS_ATEST_BITS) {
                0 -> qgl.qglDisable(GL11.GL_ALPHA_TEST)
                GLS_ATEST_EQ_255 -> {
                    qgl.qglEnable(GL11.GL_ALPHA_TEST)
                    qgl.qglAlphaFunc(GL11.GL_EQUAL, 1.0f)
                }

                GLS_ATEST_LT_128 -> {
                    qgl.qglEnable(GL11.GL_ALPHA_TEST)
                    qgl.qglAlphaFunc(GL11.GL_LESS, 0.5f)
                }

                GLS_ATEST_GE_128 -> {
                    qgl.qglEnable(GL11.GL_ALPHA_TEST)
                    qgl.qglAlphaFunc(GL11.GL_GEQUAL, 0.5f)
                }

                else -> assert((false))
            }
        }
        backEnd!!.glState.glStateBits = stateBits
    }

    /*
     =============
     RB_SetGL2D

     This is not used by the normal game paths, just by some tools
     =============
     */
    fun RB_SetGL2D() {
        // set 2D virtual screen size
        qgl.qglViewport(0, 0, glConfig.vidWidth, glConfig.vidHeight)
        if (r_useScissor!!.GetBool()) {
            qgl.qglScissor(0, 0, glConfig.vidWidth, glConfig.vidHeight)
        }
        qgl.qglMatrixMode(GL11.GL_PROJECTION)
        qgl.qglLoadIdentity()
        qgl.qglOrtho(0.0, 640.0, 480.0, 0.0, 0.0, 1.0) // always assume 640x480 virtual coordinates
        qgl.qglMatrixMode(GL11.GL_MODELVIEW)
        qgl.qglLoadIdentity()
        GL_State(
            (GLS_DEPTHFUNC_ALWAYS
                    or GLS_SRCBLEND_SRC_ALPHA
                    or GLS_DSTBLEND_ONE_MINUS_SRC_ALPHA)
        )
        GL_Cull(cullType_t.CT_TWO_SIDED)
        qgl.qglDisable(GL11.GL_DEPTH_TEST)
        qgl.qglDisable(GL11.GL_STENCIL_TEST)
    }

    private val clearColor = FloatArray(3)
    private var clearColorString = ""
    private var clearColorValid = false

    private fun ParseClearColor(value: String, out: FloatArray): Boolean {
        val tokens = value.trim().split(Regex("\\s+"))
        if (tokens.size != 3) {
            return false
        }
        val r = tokens[0].toFloatOrNull() ?: return false
        val g = tokens[1].toFloatOrNull() ?: return false
        val b = tokens[2].toFloatOrNull() ?: return false
        out[0] = r
        out[1] = g
        out[2] = b
        return true
    }

    /*
     =============
     RB_SetBuffer

     =============
     */
    fun RB_SetBuffer(data: Any) {
        val cmd: setBufferCommand_t

        // see which draw buffer we want to render the frame to
        cmd = data as setBufferCommand_t
        backEnd!!.frameCount = cmd.frameCount
        qgl.qglDrawBuffer(cmd.buffer)

        // clear screen for debugging
        // automatically enable this with several other debug tools
        // that might leave unrendered portions of the screen
        if ((r_clear!!.GetFloat() != 0.0f) || (r_clear!!.GetString()!!.length != 1) || r_lockSurfaces!!.GetBool() || r_singleArea!!.GetBool() || r_showOverDraw!!.GetBool()) {
            val clear = r_clear!!.GetString()!!
            if (clear.length != 1 && clear != clearColorString) {
                clearColorString = clear
                clearColorValid = ParseClearColor(clear, clearColor)
            }
            if (clear.length != 1 && clearColorValid) {
                qgl.qglClearColor(clearColor[0], clearColor[1], clearColor[2], 1.0f)
            } else {
                if (r_clear!!.GetInteger() == 2) {
                    qgl.qglClearColor(0.0f, 0.0f, 0.0f, 1.0f)
                } else if (r_showOverDraw!!.GetBool()) {
                    qgl.qglClearColor(1.0f, 1.0f, 1.0f, 1.0f)
                } else {
                    qgl.qglClearColor(0.4f, 0.0f, 0.25f, 1.0f)
                }
            }
            qgl.qglClear(GL11.GL_COLOR_BUFFER_BIT)
        }
    }

    /*
     ===============
     RB_ShowImages

     Draw all the images to the screen, on top of whatever
     was there.  This is used to test for texture thrashing.
     ===============
     */
    fun RB_ShowImages() {
        var i: Int
        var image: idImage?
        var x: Float
        var y: Float
        var w: Float
        var h: Float
        val start: Int
        val end: Int
        RB_SetGL2D()

        //qglClearColor( 0.2f, 0.2f, 0.2f, 1 );
        //qglClear( GL_COLOR_BUFFER_BIT );
        qgl.qglFinish()
        start = Sys_Milliseconds()
        i = 0
        while (i < Image.globalImages.images.Num()) {
            image = Image.globalImages.images[i]
            if (image!!.texNum == idImage.TEXTURE_NOT_LOADED && image.partialImage == null) {
                i++
                continue
            }
            w = (glConfig.vidWidth / 20).toFloat()
            h = (glConfig.vidHeight / 15).toFloat()
            x = i % 20 * w
            y = i / 20 * h

            // show in proportional size in mode 2
            if (r_showImages!!.GetInteger() == 2) {
                w *= image.uploadWidth._val / 512.0f
                h *= image.uploadHeight._val / 512.0f
            }
            image.Bind()
            qgl.qglBegin(GL11.GL_QUADS)
            qgl.qglTexCoord2f(0.0f, 0.0f)
            qgl.qglVertex2f(x, y)
            qgl.qglTexCoord2f(1.0f, 0.0f)
            qgl.qglVertex2f(x + w, y)
            qgl.qglTexCoord2f(1.0f, 1.0f)
            qgl.qglVertex2f(x + w, y + h)
            qgl.qglTexCoord2f(0.0f, 1.0f)
            qgl.qglVertex2f(x, y + h)
            qgl.qglEnd()
            i++
        }
        qgl.qglFinish()
        end = Sys_Milliseconds()
        Common.common.Printf("%d msec to draw all images\n", end - start)
    }

    /*
     =============
     RB_SwapBuffers

     =============
     */
    fun RB_SwapBuffers(data: Any?) {
        // texture swapping test
        if (r_showImages!!.GetInteger() != 0) {
            RB_ShowImages()
        }

        // force a gl sync if requested
        if (r_finish!!.GetBool()) {
            qgl.qglFinish()
        }
        // don't flip if drawing to front buffer
        if (!r_frontBuffer!!.GetBool()) {
            GLimp_SwapBuffers()
        }
    }

    /*
     =============
     RB_CopyRender

     Copy part of the current framebuffer to an image
     =============
     */
    fun RB_CopyRender(data: Any) {
        val cmd: copyRenderCommand_t
        cmd = data as copyRenderCommand_t
        if (r_skipCopyTexture!!.GetBool()) {
            return
        }
        if (cmd.image != null) {
            val imageWidth = CInt(cmd.imageWidth)
            val imageHeight = CInt(cmd.imageHeight)
            cmd.image!!.CopyFramebuffer(cmd.x, cmd.y, imageWidth, imageHeight, false)
            cmd.imageWidth = imageWidth._val
            cmd.imageHeight = imageHeight._val
        }
    }

    fun RB_ExecuteBackEndCommands(cmds: emptyCommand_t?) {
        // r_debugRenderToTexture
        var cmds: emptyCommand_t? = cmds
        var c_draw3d = 0
        var c_draw2d = 0
        var c_setBuffers = 0
        var c_swapBuffers = 0
        var c_copyRenders = 0
        if (renderCommand_t.RC_NOP == cmds!!.commandId && null == cmds.next) {
            return
        }
        backEndStartTime = Sys_Milliseconds()

        // needed for editor rendering
        RB_SetDefaultGLState()

        // upload any image loads that have completed
        Image.globalImages.CompleteBackgroundImageLoads()
        while (cmds != null) {
            when (cmds.commandId) {
                renderCommand_t.RC_NOP -> {}
                renderCommand_t.RC_DRAW_VIEW -> {
                    tr_render.RB_DrawView(cmds)
                    if ((cmds as drawSurfsCommand_t).viewDef!!.viewEntitys != null) {
                        c_draw3d++
                    } else {
                        c_draw2d++
                    }
                }

                renderCommand_t.RC_SET_BUFFER -> {
                    RB_SetBuffer(cmds)
                    c_setBuffers++
                }

                renderCommand_t.RC_SWAP_BUFFERS -> {
                    RB_SwapBuffers(cmds)
                    c_swapBuffers++
                }

                renderCommand_t.RC_COPY_RENDER -> {
                    RB_CopyRender(cmds)
                    c_copyRenders++
                }

                else -> Common.common.Error("RB_ExecuteBackEndCommands: bad commandId")
            }
            cmds = cmds.next
        }

        // go back to the default texture so the editor doesn't mess up a bound image
        qgl.qglBindTexture(GL11.GL_TEXTURE_2D, 0)
        backEnd!!.glState.tmu[0]!!.current2DMap = -1

        // stop rendering on this thread
        backEndFinishTime = Sys_Milliseconds()
        backEnd!!.pc.msec = backEndFinishTime - backEndStartTime
        if (r_debugRenderToTexture!!.GetInteger() == 1) {
            Common.common.Printf(
                "3d: %d, 2d: %d, SetBuf: %d, SwpBuf: %d, CpyRenders: %d, CpyFrameBuf: %d\n",
                c_draw3d,
                c_draw2d,
                c_setBuffers,
                c_swapBuffers,
                c_copyRenders,
                backEnd!!.c_copyFrameBuffer
            )
            backEnd!!.c_copyFrameBuffer = 0
        }
    }
}

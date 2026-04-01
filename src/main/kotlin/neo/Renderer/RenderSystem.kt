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

import neo.Renderer.Material.idMaterial
import neo.Renderer.RenderWorld.idRenderWorld
import neo.Renderer.RenderWorld.renderView_s
import neo.TempDump.CPP_class
import neo.TempDump.CPP_class.Char
import neo.framework.Common.Companion.common
import neo.framework.Common.MemInfo_t
import neo.idlib.CmdArgs
import neo.idlib.containers.CInt
import neo.idlib.geometry.DrawVert.idDrawVert
import neo.idlib.math.idVec2
import neo.idlib.math.idVec3
import neo.idlib.math.idVec4
import neo.sys.win_glimp.GLimp_ResetGamma
import java.nio.ByteBuffer

object RenderSystem {
    val BIGCHAR_HEIGHT: Int = 16
    val BIGCHAR_WIDTH: Int = 16
    val GLYPH_CHAREND: Int = 127
    val GLYPH_CHARSTART: Int = 32
    val GLYPH_END: Int = 255

    // font support
    val GLYPH_START: Int = 0
    val GLYPHS_PER_FONT: Int = GLYPH_END - GLYPH_START + 1
    val SCREEN_HEIGHT: Int = 480

    //
    // all drawing is done to a 640 x 480 virtual screen size
    // and will be automatically scaled to the real resolution
    val SCREEN_WIDTH: Int = 640
    val SMALLCHAR_HEIGHT: Int = 16
    val SMALLCHAR_WIDTH: Int = 8
    var renderSystem: idRenderSystem = tr

    /*
     =====================
     R_PerformanceCounters

     This prints both front and back end counters, so it should
     only be called when the back end thread is idle.
     =====================
     */
    fun R_PerformanceCounters() {
        if (r_showPrimitives.GetInteger() != 0) {
            val megaBytes: Float = Image.globalImages.SumOfUsedImages() / (1024 * 1024.0f)
            if (r_showPrimitives.GetInteger() > 1) {
                common.Printf(
                    "v:%d ds:%d t:%d/%d v:%d/%d st:%d sv:%d image:%5.1f MB\n",
                    tr.pc!!.c_numViews,
                    backEnd!!.pc.c_drawElements + backEnd!!.pc.c_shadowElements,
                    backEnd!!.pc.c_drawIndexes / 3,
                    (backEnd!!.pc.c_drawIndexes - backEnd!!.pc.c_drawRefIndexes) / 3,
                    backEnd!!.pc.c_drawVertexes,
                    (backEnd!!.pc.c_drawVertexes - backEnd!!.pc.c_drawRefVertexes),
                    backEnd!!.pc.c_shadowIndexes / 3,
                    backEnd!!.pc.c_shadowVertexes,
                    megaBytes
                )
            } else {
                common.Printf(
                    "views:%d draws:%d tris:%d (shdw:%d) (vbo:%d) image:%5.1f MB\n",
                    tr.pc!!.c_numViews,
                    backEnd!!.pc.c_drawElements + backEnd!!.pc.c_shadowElements,
                    (backEnd!!.pc.c_drawIndexes + backEnd!!.pc.c_shadowIndexes) / 3,
                    backEnd!!.pc.c_shadowIndexes / 3,
                    backEnd!!.pc.c_vboIndexes / 3,
                    megaBytes
                )
            }
        }
        if (r_showDynamic.GetBool()) {
            common.Printf(
                "callback:%d md5:%d dfrmVerts:%d dfrmTris:%d tangTris:%d guis:%d\n",
                tr.pc!!.c_entityDefCallbacks,
                tr.pc!!.c_generateMd5,
                tr.pc!!.c_deformedVerts,
                tr.pc!!.c_deformedIndexes / 3,
                tr.pc!!.c_tangentIndexes / 3,
                tr.pc!!.c_guiSurfs
            )
        }
        if (r_showCull.GetBool()) {
            common.Printf(
                "%d sin %d sclip  %d sout %d bin %d bout\n",
                tr.pc!!.c_sphere_cull_in,
                tr.pc!!.c_sphere_cull_clip,
                tr.pc!!.c_sphere_cull_out,
                tr.pc!!.c_box_cull_in,
                tr.pc!!.c_box_cull_out
            )
        }
        if (r_showAlloc.GetBool()) {
            common.Printf("alloc:%d free:%d\n", tr.pc!!.c_alloc, tr.pc!!.c_free)
        }
        if (r_showInteractions.GetBool()) {
            common.Printf(
                "createInteractions:%d createLightTris:%d createShadowVolumes:%d\n",
                tr.pc!!.c_createInteractions,
                tr.pc!!.c_createLightTris,
                tr.pc!!.c_createShadowVolumes
            )
        }
        if (r_showDefs.GetBool()) {
            common.Printf(
                "viewEntities:%d  shadowEntities:%d  viewLights:%d\n", tr.pc!!.c_visibleViewEntities,
                tr.pc!!.c_shadowViewEntities, tr.pc!!.c_viewLights
            )
        }
        if (r_showUpdates.GetBool()) {
            common.Printf(
                "entityUpdates:%d  entityRefs:%d  lightUpdates:%d  lightRefs:%d\n",
                tr.pc!!.c_entityUpdates, tr.pc!!.c_entityReferences,
                tr.pc!!.c_lightUpdates, tr.pc!!.c_lightReferences
            )
        }
        if (r_showMemory.GetBool()) {
            val m1: Int = if (frameData != null) frameData!!.memoryHighwater else 0
            common.Printf("frameData: %d (%d)\n", tr_main.R_CountFrameData(), m1)
        }
        if (r_showLightScale.GetBool()) {
            common.Printf("lightScale: %f\n", backEnd!!.pc.maxLightValue)
        }

        tr.pc = performanceCounters_t()
        backEnd!!.pc = backEndCounters_t()
    }

    /*
     ====================
     R_IssueRenderCommands

     Called by R_EndFrame each frame
     ====================
     */
    fun R_IssueRenderCommands() {
        if (renderCommand_t.RC_NOP == frameData!!.cmdHead!!.commandId && frameData!!.cmdHead!!.next == null) {
            // nothing to issue
            return
        }

        // r_skipBackEnd allows the entire time of the back end
        // to be removed from performance measurements, although
        // nothing will be drawn to the screen.  If the prints
        // are going to a file, or r_skipBackEnd is later disabled,
        // usefull data can be received.
        //
        // r_skipRender is usually more useful, because it will still
        // draw 2D graphics
        if (!r_skipBackEnd.GetBool()) {
            tr_backend.RB_ExecuteBackEndCommands(frameData!!.cmdHead)
        }
        R_ClearCommandChain()
    }

    /*
     ============
     R_GetCommandBuffer

     Returns memory for a command buffer (stretchPicCommand_t,
     drawSurfsCommand_t, etc) and links it to the end of the
     current command chain.
     ============
     */
    fun R_GetCommandBuffer(command_t: emptyCommand_t): emptyCommand_t {
        val cmd: emptyCommand_t

        cmd = command_t
        frameData!!.cmdTail!!.next = cmd
        frameData!!.cmdTail = cmd
        return cmd
    }

    /*
     ====================
     R_ClearCommandChain

     Called after every buffer submission
     and by R_ToggleSmpFrame
     ====================
     */
    fun R_ClearCommandChain() {
        // clear the command chain
        frameData!!.cmdTail = emptyCommand_t()
        frameData!!.cmdHead = frameData!!.cmdTail
        frameData!!.cmdHead!!.commandId = renderCommand_t.RC_NOP
        frameData!!.cmdHead!!.next = null
    }

    /*
     =================
     R_ViewStatistics
     =================
     */
    fun R_ViewStatistics(parms: viewDef_s) {
        // report statistics about this view
        if (!r_showSurfaces.GetBool()) {
            return
        }
        common.Printf("view:%s surfs:%d\n", parms, parms.numDrawSurfs)
    }

    /*
     =============
     R_AddDrawViewCmd

     This is the main 3D rendering command.  A single scene may
     have multiple views if a mirror, portal, or dynamic texture is present.
     =============
     */
    fun R_AddDrawViewCmd(parms: viewDef_s) {
        var cmd: drawSurfsCommand_t
        R_GetCommandBuffer(drawSurfsCommand_t().also({ cmd = it }))
        cmd.commandId = renderCommand_t.RC_DRAW_VIEW
        cmd.viewDef = parms
        tr.pc!!.c_numViews++
        R_ViewStatistics(parms)
    }

    //=================================================================================

    /*
     =============
     R_CheckCvars

     See if some cvars that we watch have changed
     =============
     */
    fun R_CheckCvars() {
        Image.globalImages.CheckCvars()

        // gamma stuff
        if (r_gamma.IsModified() || r_brightness.IsModified()) {
            r_gamma.ClearModified()
            r_brightness.ClearModified()
            R_SetColorMappings()
        }

        if (r_gammaInShader.IsModified()) {
            r_gammaInShader.ClearModified()

            // reload shaders so they either add or remove the code for setting gamma/brightness in shader
            draw_arb2.R_ReloadARBPrograms_f.instance.run(CmdArgs.idCmdArgs())

            if (r_gammaInShader.GetBool()) {
                common.Printf("Will apply r_gamma and r_brightness in shaders\n")
                GLimp_ResetGamma() // reset hardware gamma
            } else {
                common.Printf("Will apply r_gamma and r_brightness in hardware (possibly on all screens)\n")
                R_SetColorMappings()
            }
        }

        if (r_swapInterval.IsModified()) {
            r_swapInterval.ClearModified()
            org.lwjgl.glfw.GLFW.glfwSwapInterval(r_swapInterval.GetInteger())
        }
    }

    fun setRenderSystems(renderSystem: idRenderSystem?) {
        tr = renderSystem as idRenderSystemLocal
        RenderSystem.renderSystem = tr
    }

    /*
     ===============================================================================

     idRenderSystem is responsible for managing the screen, which can have
     multiple idRenderWorld and 2D drawing done on it.

     ===============================================================================
     */
    class glconfig_s {
        var ARBFragmentProgramAvailable: Boolean = false
        var ARBVertexBufferObjectAvailable: Boolean = false
        var ARBVertexProgramAvailable: Boolean = false
        var allowARB2Path: Boolean = true
        var anisotropicAvailable: Boolean = false

        var colorBits: Int = 0
        var depthBits: Int = 0
        var stencilBits: Int = 8
        var cubeMapAvailable: Boolean = false
        var depthBoundsTestAvailable: Boolean = false

        //
        var displayFrequency: Int = 0
        var envDot3Available: Boolean = false
        var extensions_string: String? = null

        //
        var glVersion: Float = 0.0f // atof( version_string )

        //
        var isFullscreen: Boolean = false

        //
        var isInitialized: Boolean = false
        var maxTextureAnisotropy: Float = 0.0f
        var maxTextureCoords: Int = 0
        var maxTextureImageUnits: Int = 0

        //
        //
        var maxTextureSize: Int = 0 // queried from GL
        var maxTextureUnits: Int = 0

        //
        var multitextureAvailable: Boolean = false
        var registerCombinersAvailable: Boolean = false
        var renderer_string: String? = null
        var sharedTexturePaletteAvailable: Boolean = false
        var texture3DAvailable: Boolean = false
        var textureCompressionAvailable: Boolean = false
        var bptcTextureCompressionAvailable: Boolean = false
        var textureEnvAddAvailable: Boolean = false
        var textureEnvCombineAvailable: Boolean = false
        var textureLODBiasAvailable: Boolean = false
        var textureNonPowerOfTwoAvailable: Boolean = false
        var twoSidedStencilAvailable: Boolean = false
        var vendor_string: String? = null
        var version_string: String? = null

        // macOS has different way of handling pixels and coordinates, and, thus, both X and Y needs to be scaled properly
        var scaleX: FloatArray = floatArrayOf(1.0f)
        var scaleY: FloatArray = floatArrayOf(1.0f)

        var vidWidth: Int = 0
        var vidHeight: Int = 0 // passed to R_BeginFrame
        // For some reason people decided that we need displays with ultra small pixels,
        // so everything rendered on them must be scaled up to be legible.
        // unfortunately, this bullshit feature was "improved" upon by deciding that the best
        // way to implement "High DPI" was to pretend that windows have fewer pixels than they
        // actually do, so the window size you get and mouse coordinates in them etc
        // are in e.g. 1024x768, while the physical window size is e.g. 1536x1152 pixels
        // (when the scaling factor is 1.5), and ideally the GL framebuffer has the physical
        // window size so things still look crisp.
        // Of course the reasonable solution would be to go back and time and nuke Cupertino,
        // where this nonsense scheme was invented, but as I lack the necessary funds,
        // I reluctantly add winWidth and winHeight and adjust the code that deals with window
        // coordinates, as far as that's possible..
        // (Isn't it fun that you have a 2256x1504 display, tell SDL to create a 1920x1080 window
        //  and you get one that's much bigger and doesn't fit on the screen?)

        var winWidth: Float = 0.0f
        var winHeight: Float = 0.0f   // logical window size (different to vidWidth/height in HighDPI cases)
    }

    class glyphInfo_t {
        var glyph: idMaterial? = null // shader with the glyph
        var height: Int = 0 // number of scan lines
        var imageHeight: Int = 0 // height of actual image
        var imageWidth: Int = 0 // width of actual image
        var s: Float = 0.0f // x offset in image where glyph starts
        var s2: Float = 0.0f
        var t: Float = 0.0f // y offset in image where glyph starts
        var t2: Float = 0.0f
        var top: Int = 0 // top of glyph in buffer
        var xSkip: Int = 0 // x adjustment
        var bottom: Int = 0 // bottom of glyph in buffer
        var pitch: Int = 0 // width for copying

        // char				shaderName[32];
        var shaderName: String? = null

        companion object {
            @Transient
            val SIZE: Int = (Integer.SIZE
                    + Integer.SIZE
                    + Integer.SIZE
                    + Integer.SIZE
                    + Integer.SIZE
                    + Integer.SIZE
                    + Integer.SIZE
                    + java.lang.Float.SIZE
                    + java.lang.Float.SIZE
                    + java.lang.Float.SIZE
                    + java.lang.Float.SIZE
                    + CPP_class.Pointer.SIZE //const idMaterial *	glyph
                    + (Char.SIZE * 32))
        }
    }

    class fontInfo_t {
        var glyphScale: Float = 0.0f
        var glyphs: Array<glyphInfo_t?> = arrayOfNulls(GLYPHS_PER_FONT)
        var name: StringBuilder = StringBuilder(64)

        init {
            for (g in glyphs.indices) {
                glyphs[g] = glyphInfo_t()
            }
        }

        companion object {
            @Transient
            val SIZE: Int = ((glyphInfo_t.SIZE * GLYPHS_PER_FONT)
                    + java.lang.Float.SIZE
                    + (Char.SIZE * 64))

            @Transient
            val BYTES: Int = SIZE / java.lang.Byte.SIZE
        }
    }

    class fontInfoEx_t {
        var fontInfoLarge: fontInfo_t = fontInfo_t()
        var fontInfoMedium: fontInfo_t = fontInfo_t()
        var fontInfoSmall: fontInfo_t = fontInfo_t()
        var maxHeight: Int = 0
        var maxHeightLarge: Int = 0
        var maxHeightMedium: Int = 0
        var maxHeightSmall: Int = 0
        var maxWidth: Int = 0
        var maxWidthLarge: Int = 0
        var maxWidthMedium: Int = 0
        var maxWidthSmall: Int = 0

        // char				name[64];
        var name: String? = null

        /**
         * memset(font, 0, sizeof(font));
         */
        fun clear() {
            fontInfoSmall = fontInfo_t()
            fontInfoMedium = fontInfo_t()
            fontInfoLarge = fontInfo_t()
            maxWidthLarge = 0
            maxHeightLarge = maxWidthLarge
            maxWidthMedium = maxHeightLarge
            maxHeightMedium = maxWidthMedium
            maxWidthSmall = maxHeightMedium
            maxHeightSmall = maxWidthSmall
            maxWidth = maxHeightSmall
            maxHeight = maxWidth
            name = null
        }
    }

    abstract class idRenderSystem {
        // set up cvars and basic data structures, but don't
        // init OpenGL, so it can also be used for dedicated servers
        abstract fun Init()

        // only called before quitting
        abstract fun Shutdown()
        abstract fun InitOpenGL()
        abstract fun ShutdownOpenGL()
        abstract fun IsOpenGLRunning(): Boolean
        abstract fun IsFullScreen(): Boolean
        abstract fun GetScreenWidth(): Int
        abstract fun GetScreenHeight(): Int

        // allocate a renderWorld to be used for drawing
        abstract fun AllocRenderWorld(): idRenderWorld
        abstract fun FreeRenderWorld(rw: idRenderWorld)

        // All data that will be used in a level should be
        // registered before rendering any frames to prevent disk hits,
        // but they can still be registered at a later time
        // if necessary.
        abstract fun BeginLevelLoad()
        abstract fun EndLevelLoad()

        // font support
        abstract fun RegisterFont(fontName: String?, font: fontInfoEx_t): Boolean

        // GUI drawing just involves shader parameter setting and axial image subsections
        abstract fun SetColor(rgba: idVec4)
        abstract fun SetColor4(r: Float, g: Float, b: Float, a: Float)
        abstract fun DrawStretchPic(
            verts: Array<idDrawVert>?,
            indexes: IntArray?,
            vertCount: Int,
            indexCount: Int,
            material: idMaterial?,
            clip: Boolean /*= true*/,
            min_x: Float /* = 0.0f*/,
            min_y: Float /*= 0.0f*/,
            max_x: Float /*= 640.0f*/,
            max_y: Float /*= 480.0f */
        )


        fun DrawStretchPic(
            verts: Array<idDrawVert>?,
            indexes: IntArray?,
            vertCount: Int,
            indexCount: Int,
            material: idMaterial?,
            clip: Boolean = true /*= true*/,
            min_x: Float = 0.0f /* = 0.0f*/,
            min_y: Float = 0.0f /*= 0.0f*/,
            max_x: Float = 640.0f /*= 640.0f*/
        ) {
            DrawStretchPic(verts, indexes, vertCount, indexCount, material, clip, min_x, min_y, max_x, 480.0f)
        }

        abstract fun DrawStretchPic(
            x: Float,
            y: Float,
            w: Float,
            h: Float,
            s1: Float,
            t1: Float,
            s2: Float,
            t2: Float,
            material: idMaterial?
        )

        abstract fun DrawStretchTri(
            p1: idVec2,
            p2: idVec2,
            p3: idVec2,
            t1: idVec2,
            t2: idVec2,
            t3: idVec2,
            material: idMaterial?
        )

        abstract fun GlobalToNormalizedDeviceCoordinates(global: idVec3?, ndc: idVec3?)
        abstract fun GetGLSettings(width: CInt, height: CInt)
        abstract fun PrintMemInfo(mi: MemInfo_t)
        abstract fun DrawSmallChar(x: Int, y: Int, ch: Int, material: idMaterial?)
        abstract fun DrawSmallStringExt(
            x: Int,
            y: Int,
            string: CharArray,
            setColor: idVec4,
            forceColor: Boolean,
            material: idMaterial?
        )

        abstract fun DrawBigChar(x: Int, y: Int, ch: Int, material: idMaterial?)
        abstract fun DrawBigStringExt(
            x: Int,
            y: Int,
            string: String,
            setColor: idVec4,
            forceColor: Boolean,
            material: idMaterial?
        )

        // dump all 2D drawing so far this frame to the demo file
        abstract fun WriteDemoPics()

        // draw the 2D pics that were saved out with the current demo frame
        abstract fun DrawDemoPics()

        // FIXME: add an interface for arbitrary point/texcoord drawing
        // a frame cam consist of 2D drawing and potentially multiple 3D scenes
        // window sizes are needed to convert SCREEN_WIDTH / SCREEN_HEIGHT values
        abstract fun BeginFrame(windowWidth: Int, windowHeight: Int)

        // if the pointers are not NULL, timing info will be returned
        abstract fun EndFrame(frontEndMsec: IntArray?, backEndMsec: IntArray?)

        // aviDemo uses this.
        // Will automatically tile render large screen shots if necessary
        // Samples is the number of jittered frames for anti-aliasing
        // If ref == NULL, session->updateScreen will be used
        // This will perform swapbuffers, so it is NOT an approppriate way to
        // generate image files that happen during gameplay, as for savegame
        // markers.  Use WriteRender() instead.
        abstract fun TakeScreenshot(width: Int, height: Int, fileName: String, samples: Int, ref: renderView_s?)

        // the render output can be cropped down to a subset of the real screen, as
        // for save-game reviews and split-screen multiplayer.  Users of the renderer
        // will not know the actual pixel size of the area they are rendering to
        // the x,y,width,height values are in public abstract SCREEN_WIDTH / SCREEN_HEIGHT coordinates
        // to render to a texture, first set the crop size with makePowerOfTwo = true,
        // then perform all desired rendering, then capture to an image
        // if the specified physical dimensions are larger than the current cropped region, they will be cut down to fit
        abstract fun CropRenderSize(
            width: Int,
            height: Int,
            makePowerOfTwo: Boolean /*= false*/,
            forceDimensions: Boolean /*= false */
        )


        fun CropRenderSize(width: Int, height: Int, makePowerOfTwo: Boolean = false /*= false*/) {
            CropRenderSize(width, height, makePowerOfTwo, false)
        }

        abstract fun CaptureRenderToImage(imageName: String?)

        // fixAlpha will set all the alpha channel values to 0xff, which allows screen captures
        // to use the default tga loading code without having dimmed down areas in many places
        abstract fun CaptureRenderToFile(fileName: String?, fixAlpha: Boolean /* = false */)
        fun CaptureRenderToFile(fileName: String?) {
            CaptureRenderToFile(fileName, false)
        }

        abstract fun UnCrop()

        // the image has to be already loaded ( most straightforward way would be through a FindMaterial )
        // texture filter / mipmapping / repeat won't be modified by the upload
        // returns false if the image wasn't found
        abstract fun UploadImage(imageName: String?, data: ByteBuffer?, width: Int, height: Int): Boolean
    }
}

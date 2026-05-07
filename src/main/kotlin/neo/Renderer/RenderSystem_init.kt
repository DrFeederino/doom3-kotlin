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
import neo.Renderer.Cinematic.idCinematic
import neo.Renderer.Image.cubeFiles_t
import neo.Renderer.Image.idImage
import neo.Renderer.Image.textureDepth_t
import neo.Renderer.Image_files.R_WriteTGA
import neo.Renderer.Image_program.R_LoadImageProgram
import neo.Renderer.Interaction.R_ShowInteractionMemory_f
import neo.Renderer.Material.idMaterial
import neo.Renderer.Material.textureFilter_t
import neo.Renderer.Material.textureRepeat_t
import neo.Renderer.MegaTexture.idMegaTexture.MakeMegaTexture_f
import neo.Renderer.RenderWorld.modelTrace_s
import neo.Renderer.RenderWorld.renderView_s
import neo.Renderer.draw_arb2.R_ReloadARBPrograms_f
import neo.Renderer.qgl.qglGetString
import neo.Renderer.tr_guisurf.R_ListGuis_f
import neo.Renderer.tr_guisurf.R_ReloadGuis_f
import neo.Sound.snd_system
import neo.framework.CVarSystem.CVAR_ARCHIVE
import neo.framework.CVarSystem.CVAR_BOOL
import neo.framework.CVarSystem.CVAR_CHEAT
import neo.framework.CVarSystem.CVAR_FLOAT
import neo.framework.CVarSystem.CVAR_INTEGER
import neo.framework.CVarSystem.CVAR_NOCHEAT
import neo.framework.CVarSystem.CVAR_RENDERER
import neo.framework.CVarSystem.cvarSystem
import neo.framework.CVarSystem.idCVar
import neo.framework.CmdSystem.CMD_FL_CHEAT
import neo.framework.CmdSystem.CMD_FL_RENDERER
import neo.framework.CmdSystem.cmdExecution_t
import neo.framework.CmdSystem.cmdFunction_t
import neo.framework.CmdSystem.cmdSystem
import neo.framework.CmdSystem.idCmdSystem.*
import neo.framework.Common.Companion.common
import neo.framework.Console
import neo.framework.DeclManager
import neo.framework.DeclManager.declType_t
import neo.framework.FileSystem_h.fileSystem
import neo.framework.Session
import neo.framework.WIN32
import neo.idlib.CmdArgs
import neo.idlib.Text.Str.idStr
import neo.idlib.Text.Str.idStr.Companion.FindText
import neo.idlib.Text.Str.idStr.Companion.Icmp
import neo.idlib.Text.Str.idStr.Companion.IsNumeric
import neo.idlib.Text.Str.idStr.Companion.snPrintf
import neo.idlib.Text.atof
import neo.idlib.Text.ctos
import neo.idlib.containers.CInt
import neo.idlib.containers.List.cmp_t
import neo.idlib.math.Matrix.idMat3
import neo.idlib.math.idVec3
import neo.idlib.toBoolean
import neo.idlib.toInt
import neo.sys.win_glimp.GLimp_Init
import neo.sys.win_glimp.GLimp_SetGamma
import neo.sys.win_glimp.GLimp_SetScreenParms
import neo.sys.win_glimp.GLimp_Shutdown
import neo.sys.win_glimp.glimpParms_t
import neo.sys.win_input.Sys_GrabMouseCursor
import neo.sys.win_input.Sys_InitInput
import neo.sys.win_input.Sys_ShutdownInput
import neo.sys.win_main.Sys_GetProcessorString
import neo.sys.win_shared.Sys_Milliseconds
import neo.ui.UserInterface.uiManager
import org.lwjgl.BufferUtils
import org.lwjgl.glfw.GLFWErrorCallback
import org.lwjgl.opengl.*
import java.nio.ByteBuffer
import java.nio.FloatBuffer
import java.nio.IntBuffer
import java.util.*
import kotlin.math.abs
import kotlin.math.pow


/*
 ==================
 R_BlendedScreenShot

 screenshot
 screenshot [filename]
 screenshot [width] [height]
 screenshot [width] [height] [samples]
 ==================
 */
val MAX_BLENDS: Int = 256 // to keep the accumulation in shorts

//============================================================================
val cubeAxis: Array<idMat3> = Array(6) { idMat3() }
val r_rendererArgs: Array<String?> = arrayOf("best", "arb2", null)
val r_vidModes: Array<vidmode_s> = arrayOf(
    vidmode_s("Mode  0: 320x240", 320, 240),
    vidmode_s("Mode  1: 400x300", 400, 300),
    vidmode_s("Mode  2: 512x384", 512, 384),
    vidmode_s("Mode  3: 640x480", 640, 480),
    vidmode_s("Mode  4: 800x600", 800, 600),
    vidmode_s("Mode  5: 1024x768", 1024, 768),
    vidmode_s("Mode  6: 1152x864", 1152, 864),
    vidmode_s("Mode  7: 1280x1024", 1280, 1024),
    vidmode_s("Mode  8: 1600x1200", 1600, 1200),
    vidmode_s("Mode  9: 1280x720", 1280, 720),
    vidmode_s("Mode 10: 1366x768", 1366, 768),
    vidmode_s("Mode 11: 1440x900", 1440, 900),
    vidmode_s("Mode 12: 1400x1050", 1400, 1050),
    vidmode_s("Mode 13: 1600x900", 1600, 900),
    vidmode_s("Mode 14: 1680x1050", 1680, 1050),
    vidmode_s("Mode 15: 1920x1080", 1920, 1080),
    vidmode_s("Mode 16: 1920x1200", 1920, 1200),
    vidmode_s("Mode 17: 2048x1152", 2048, 1152),
    vidmode_s("Mode 18: 2560x1600", 2560, 1600),
    vidmode_s("Mode 19: 3200x2400", 3200, 2400),
    vidmode_s("Mode 20: 3840x2160", 3840, 2160),
    vidmode_s("Mode 21: 4096x2304", 4096, 2304),
    vidmode_s("Mode 22: 2880x1800", 2880, 1800),
    vidmode_s("Mode 23: 2560x1440", 2560, 1440),
    vidmode_s("Mode 24: 1440x1080", 1440, 1080),
    vidmode_s("Mode 25: 1280x800", 1280, 800),
    // 21:9 resolutions
    vidmode_s("Mode 26: 2560x1080", 2560, 1080),
    vidmode_s("Mode 27: 3440x1440", 3440, 1440),
    vidmode_s("Mode 28: 3840x1600", 3840, 1600),
    vidmode_s("Mode 29: 5120x2160", 5120, 2160),
    // 32:9 resolutions
    vidmode_s("Mode 30: 3840x1080", 3840, 1080),
    vidmode_s("Mode 31: 5120x1440", 5120, 1440),
    vidmode_s("Mode 32: 7680x2160", 7680, 2160)
)

/*
 ==============================================================================

 THROUGHPUT BENCHMARKING

 ==============================================================================
 */
private val SAMPLE_MSEC: Int = 1000

/*
 ==================
 R_BlendedScreenShot

 screenshot
 screenshot [filename]
 screenshot [width] [height]
 screenshot [width] [height] [samples]
 ==================
 */
private val lastNumber: CInt = CInt()
var s_numVidModes: Int = r_vidModes.size

/*
 ==================
 R_InitOpenGL

 This function is responsible for initializing a valid OpenGL subsystem
 for rendering.  This is done by calling the system specific GLimp_Init,
 which gives us a working OGL subsystem, then setting all necessary openGL
 state, including images, vertex programs, and display lists.

 Changes to the vertex cache size or smp state require a vid_restart.

 If glConfig.isInitialized is false, no rendering can take place, but
 all renderSystem functions will still operate properly, notably the material
 and model information functions.
 ==================
 */
private var glCheck: Boolean = false

val r_inhibitFragmentProgram =
    idCVar("r_inhibitFragmentProgram", "0", CVAR_RENDERER or CVAR_BOOL, "ignore the fragment program extension")
val r_useLightPortalFlow = idCVar(
    "r_useLightPortalFlow", "1", CVAR_RENDERER or CVAR_BOOL, "use a more precise area reference determination"
)
val r_multiSamples = idCVar(
    "r_multiSamples", "0", CVAR_RENDERER or CVAR_ARCHIVE or CVAR_INTEGER, "number of antialiasing samples"
)
val r_mode = idCVar("r_mode", "5", CVAR_ARCHIVE or CVAR_RENDERER or CVAR_INTEGER, "video mode number")
val r_displayRefresh = idCVar(
    "r_displayRefresh",
    "0",
    CVAR_RENDERER or CVAR_INTEGER or CVAR_NOCHEAT,
    "optional display refresh rate option for vid mode",
    0.0f,
    200.0f
)
val r_fullscreen =
    idCVar("r_fullscreen", "0", CVAR_RENDERER or CVAR_ARCHIVE or CVAR_BOOL, "0 = windowed, 1 = full screen")
val r_customWidth = idCVar(
    "r_customWidth",
    "720",
    CVAR_RENDERER or CVAR_ARCHIVE or CVAR_INTEGER,
    "custom screen width. set r_mode to -1 to activate"
)
val r_customHeight = idCVar(
    "r_customHeight",
    "486",
    CVAR_RENDERER or CVAR_ARCHIVE or CVAR_INTEGER,
    "custom screen height. set r_mode to -1 to activate"
)

var r_gammaInShader: idCVar = idCVar(
    "r_gammaInShader",
    "1",
    CVAR_RENDERER or CVAR_ARCHIVE or CVAR_BOOL,
    "Set gamma and brightness in shaders instead using hardware gamma"
)

// DG: for soft particles (#3877, #3878)
var r_enableDepthCapture: idCVar = idCVar(
    "r_enableDepthCapture",
    "-1",
    CVAR_RENDERER or CVAR_INTEGER,
    "Enable capturing depth buffer for soft particles. -1 = auto (on if r_useSoftParticles), 0 = off, 1 = on",
    -1f,
    1f
)
var r_useSoftParticles: idCVar = idCVar(
    "r_useSoftParticles",
    "1",
    CVAR_RENDERER or CVAR_ARCHIVE or CVAR_BOOL,
    "Soften particle transitions when player walks through them or they cross solid geometry. Needs r_enableDepthCapture. Can slow down rendering!"
)

var r_supportNoSpecular: idCVar = idCVar(
    "r_supportNoSpecular",
    "-1",
    CVAR_RENDERER or CVAR_INTEGER or CVAR_ARCHIVE,
    "Support 'nospecular' parm on lights. Vanilla Doom3 didn't, so the original maps are probably expecting it to not do anything. -1: Only support in maps that have have \"allow_nospecular\" \"1\" set in worldspawn (default), 0: never respect 'nospecular' parm 1: support 'nospecular' in all maps",
    -1f,
    1f
)

val r_singleTriangle =
    idCVar("r_singleTriangle", "0", CVAR_RENDERER or CVAR_BOOL, "only draw a single triangle per primitive")
val r_checkBounds = idCVar(
    "r_checkBounds", "0", CVAR_RENDERER or CVAR_BOOL, "compare all surface bounds with precalculated ones"
)
val r_useConstantMaterials = idCVar(
    "r_useConstantMaterials", "1", CVAR_RENDERER or CVAR_BOOL, "use pre-calculated material registers if possible"
)
val r_useSilRemap = idCVar(
    "r_useSilRemap",
    "1",
    CVAR_RENDERER or CVAR_BOOL,
    "consider verts with the same XYZ, but different ST the same for shadows"
)
val r_useNodeCommonChildren = idCVar(
    "r_useNodeCommonChildren", "1", CVAR_RENDERER or CVAR_BOOL, "stop pushing reference bounds early when possible"
)
val r_useShadowProjectedCull = idCVar(
    "r_useShadowProjectedCull",
    "1",
    CVAR_RENDERER or CVAR_BOOL,
    "discard triangles outside light volume before shadowing"
)
val r_useShadowVertexProgram = idCVar(
    "r_useShadowVertexProgram",
    "1",
    CVAR_RENDERER or CVAR_BOOL,
    "do the shadow projection in the vertex program on capable cards"
)
val r_useShadowSurfaceScissor = idCVar(
    "r_useShadowSurfaceScissor",
    "1",
    CVAR_RENDERER or CVAR_BOOL,
    "scissor shadows by the scissor rect of the interaction surfaces"
)
val r_useInteractionTable = idCVar(
    "r_useInteractionTable",
    "1",
    CVAR_RENDERER or CVAR_BOOL,
    "create a full entityDefs * lightDefs table to make finding interactions faster"
)
val r_useTurboShadow = idCVar(
    "r_useTurboShadow",
    "1",
    CVAR_RENDERER or CVAR_BOOL,
    "use the infinite projection with W technique for dynamic shadows"
)
val r_useTwoSidedStencil = idCVar(
    "r_useTwoSidedStencil",
    "1",
    CVAR_RENDERER or CVAR_BOOL,
    "do stencil shadows in one pass with different ops on each side"
)
val r_useDeferredTangents =
    idCVar("r_useDeferredTangents", "1", CVAR_RENDERER or CVAR_BOOL, "defer tangents calculations after deform")
val r_useCachedDynamicModels =
    idCVar("r_useCachedDynamicModels", "1", CVAR_RENDERER or CVAR_BOOL, "cache snapshots of dynamic models")
val r_useVertexBuffers = idCVar(
    "r_useVertexBuffers",
    "1",
    CVAR_RENDERER or CVAR_INTEGER,
    "use ARB_vertex_buffer_object for vertexes",
    0.0f,
    1.0f,
    ArgCompletion_Integer(0, 1)
)
val r_useIndexBuffers = idCVar(
    "r_useIndexBuffers",
    "0",
    CVAR_RENDERER or CVAR_ARCHIVE or CVAR_INTEGER,
    "use ARB_vertex_buffer_object for indexes",
    0.0f,
    1.0f,
    ArgCompletion_Integer(0, 1)
)
val r_useStateCaching = idCVar(
    "r_useStateCaching", "1", CVAR_RENDERER or CVAR_BOOL, "avoid redundant state changes in GL_*=new idCVar() calls"
)
val r_useInfiniteFarZ = idCVar("r_useInfiniteFarZ", "1", CVAR_RENDERER or CVAR_BOOL, "use the no-far-clip-plane trick")
val r_znear = idCVar("r_znear", "3", CVAR_RENDERER or CVAR_FLOAT, "near Z clip plane distance", 0.001f, 200.0f)
val r_ignoreGLErrors = idCVar("r_ignoreGLErrors", "1", CVAR_RENDERER or CVAR_BOOL, "ignore GL errors")
val r_finish = idCVar("r_finish", "0", CVAR_RENDERER or CVAR_BOOL, "force a call to glFinish=new idCVar() every frame")
val r_swapInterval =
    idCVar("r_swapInterval", "0", CVAR_RENDERER or CVAR_ARCHIVE or CVAR_INTEGER, "changes the GL swap interval")
val r_gamma = idCVar("r_gamma", "1", CVAR_RENDERER or CVAR_ARCHIVE or CVAR_FLOAT, "changes gamma tables", 0.5f, 3.0f)
val r_brightness =
    idCVar("r_brightness", "1", CVAR_RENDERER or CVAR_ARCHIVE or CVAR_FLOAT, "changes gamma tables", 0.5f, 2.0f)
val r_renderer = idCVar(
    "r_renderer",
    "best",
    CVAR_RENDERER or CVAR_ARCHIVE,
    "arb2, etc",
    r_rendererArgs,
    ArgCompletion_String(r_rendererArgs)
)
val r_jitter = idCVar("r_jitter", "0", CVAR_RENDERER or CVAR_BOOL, "randomly subpixel jitter the projection matrix")
val r_skipSuppress = idCVar("r_skipSuppress", "0", CVAR_RENDERER or CVAR_BOOL, "ignore the per-view suppressions")
val r_skipPostProcess = idCVar("r_skipPostProcess", "0", CVAR_RENDERER or CVAR_BOOL, "skip all post-process renderings")
val r_skipLightScale = idCVar(
    "r_skipLightScale",
    "0",
    CVAR_RENDERER or CVAR_BOOL,
    "don't do any post-interaction light scaling, makes things dim on low-dynamic range cards"
)
val r_skipInteractions =
    idCVar("r_skipInteractions", "0", CVAR_RENDERER or CVAR_BOOL, "skip all light/surface interaction drawing")
val r_skipDynamicTextures =
    idCVar("r_skipDynamicTextures", "0", CVAR_RENDERER or CVAR_BOOL, "don't dynamically create textures")
val r_skipCopyTexture = idCVar(
    "r_skipCopyTexture", "0", CVAR_RENDERER or CVAR_BOOL, "do all rendering, but don't actually copyTexSubImage2D"
)
val r_skipBackEnd = idCVar("r_skipBackEnd", "0", CVAR_RENDERER or CVAR_BOOL, "don't draw anything")
val r_skipRender = idCVar("r_skipRender", "0", CVAR_RENDERER or CVAR_BOOL, "skip 3D rendering, but pass 2D")
val r_skipRenderContext = idCVar(
    "r_skipRenderContext", "0", CVAR_RENDERER or CVAR_BOOL, "NULL the rendering context during backend 3D rendering"
)
val r_skipTranslucent =
    idCVar("r_skipTranslucent", "0", CVAR_RENDERER or CVAR_BOOL, "skip the translucent interaction rendering")
val r_skipAmbient = idCVar("r_skipAmbient", "0", CVAR_RENDERER or CVAR_BOOL, "bypasses all non-interaction drawing")
val r_skipNewAmbient = idCVar(
    "r_skipNewAmbient",
    "0",
    CVAR_RENDERER or CVAR_BOOL or CVAR_ARCHIVE,
    "bypasses all vertex/fragment program ambient drawing"
)
val r_skipBlendLights = idCVar("r_skipBlendLights", "0", CVAR_RENDERER or CVAR_BOOL, "skip all blend lights")
val r_skipFogLights = idCVar("r_skipFogLights", "0", CVAR_RENDERER or CVAR_BOOL, "skip all fog lights")
val r_skipDeforms = idCVar(
    "r_skipDeforms", "0", CVAR_RENDERER or CVAR_BOOL, "leave all deform materials in their original state"
)
val r_skipFrontEnd = idCVar(
    "r_skipFrontEnd", "0", CVAR_RENDERER or CVAR_BOOL, "bypasses all front end work, but 2D gui rendering still draws"
)
val r_skipUpdates = idCVar(
    "r_skipUpdates",
    "0",
    CVAR_RENDERER or CVAR_BOOL,
    "1 = don't accept any entity or light updates, making everything static"
)
val r_skipOverlays = idCVar("r_skipOverlays", "0", CVAR_RENDERER or CVAR_BOOL, "skip overlay surfaces")
val r_skipSpecular = idCVar(
    "r_skipSpecular", "0", CVAR_RENDERER or CVAR_BOOL or CVAR_CHEAT or CVAR_ARCHIVE, "use black for specular1"
)
val r_skipBump = idCVar(
    "r_skipBump", "0", CVAR_RENDERER or CVAR_BOOL or CVAR_ARCHIVE, "uses a flat surface instead of the bump map"
)
val r_skipDiffuse = idCVar("r_skipDiffuse", "0", CVAR_RENDERER or CVAR_BOOL, "use black for diffuse")
val r_skipROQ = idCVar("r_skipROQ", "0", CVAR_RENDERER or CVAR_BOOL, "skip ROQ decoding")
val r_ignore = idCVar("r_ignore", "0", CVAR_RENDERER, "used for random debugging without defining new vars")
val r_ignore2 = idCVar("r_ignore2", "0", CVAR_RENDERER, "used for random debugging without defining new vars")
val r_usePreciseTriangleInteractions = idCVar(
    "r_usePreciseTriangleInteractions",
    "0",
    CVAR_RENDERER or CVAR_BOOL,
    "1 = do winding clipping to determine if each ambiguous tri should be lit"
)
val r_useCulling = idCVar(
    "r_useCulling",
    "2",
    CVAR_RENDERER or CVAR_INTEGER,
    "0 = none, 1 = sphere, 2 = sphere + box",
    0.0f,
    2.0f,
    ArgCompletion_Integer(0, 2)
)
val r_useLightCulling = idCVar(
    "r_useLightCulling",
    "3",
    CVAR_RENDERER or CVAR_INTEGER,
    "0 = none, 1 = box, 2 = exact clip of polyhedron faces, 3 = also areas",
    0.0f,
    3.0f,
    ArgCompletion_Integer(0, 3)
)
val r_useLightScissors = idCVar(
    "r_useLightScissors", "1", CVAR_RENDERER or CVAR_BOOL, "1 = use custom scissor rectangle for each light"
)
val r_useClippedLightScissors = idCVar(
    "r_useClippedLightScissors",
    "1",
    CVAR_RENDERER or CVAR_INTEGER,
    "0 = full screen when near clipped, 1 = exact when near clipped, 2 = exact always",
    0.0f,
    2.0f,
    ArgCompletion_Integer(0, 2)
)
val r_useEntityCulling = idCVar("r_useEntityCulling", "1", CVAR_RENDERER or CVAR_BOOL, "0 = none, 1 = box")
val r_useEntityScissors = idCVar(
    "r_useEntityScissors", "0", CVAR_RENDERER or CVAR_BOOL, "1 = use custom scissor rectangle for each entity"
)
val r_useInteractionCulling =
    idCVar("r_useInteractionCulling", "1", CVAR_RENDERER or CVAR_BOOL, "1 = cull interactions")
val r_useInteractionScissors = idCVar(
    "r_useInteractionScissors",
    "2",
    CVAR_RENDERER or CVAR_INTEGER,
    "1 = use a custom scissor rectangle for each shadow interaction, 2 = also crop using portal scissors",
    -2.0f,
    2.0f,
    ArgCompletion_Integer(-2, 2)
)
val r_useShadowCulling = idCVar(
    "r_useShadowCulling", "1", CVAR_RENDERER or CVAR_BOOL, "try to cull shadows from partially visible lights"
)
val r_useFrustumFarDistance = idCVar(
    "r_useFrustumFarDistance",
    "0",
    CVAR_RENDERER or CVAR_FLOAT,
    "if != 0 force the view frustum far distance to this distance"
)
val r_logFile = idCVar("r_logFile", "0", CVAR_RENDERER or CVAR_INTEGER, "number of frames to emit GL logs")
val r_clear = idCVar(
    "r_clear", "2", CVAR_RENDERER, "force screen clear every frame, 1 = purple, 2 = black, 'r g b' = custom"
)
val r_offsetFactor = idCVar("r_offsetfactor", "0", CVAR_RENDERER or CVAR_FLOAT, "polygon offset parameter")
val r_offsetUnits = idCVar("r_offsetunits", "-600", CVAR_RENDERER or CVAR_FLOAT, "polygon offset parameter")
val r_scaleMenusTo43 = idCVar(
    "r_scaleMenusTo43",
    "1",
    CVAR_RENDERER or CVAR_ARCHIVE or CVAR_BOOL,
    "Scale menus, fullscreen videos and PDA to 4:3 aspect ratio"
)

// DG: the fscking patent has finally expired
val r_useCarmacksReverse = idCVar(
    "r_useCarmacksReverse",
    "1",
    CVAR_RENDERER or CVAR_ARCHIVE or CVAR_BOOL, "Use Z-Fail (Carmack's Reverse) when rendering shadows"
)
val r_useStencilOpSeparate = idCVar(
    "r_useStencilOpSeparate",
    "1",
    CVAR_RENDERER or CVAR_ARCHIVE or CVAR_BOOL, "Use glStencilOpSeparate() (if available) when rendering shadows"
)


val r_shadowPolygonOffset = idCVar(
    "r_shadowPolygonOffset",
    "-1",
    CVAR_RENDERER or CVAR_FLOAT,
    "bias value added to depth test for stencil shadow drawing"
)
val r_shadowPolygonFactor =
    idCVar("r_shadowPolygonFactor", "0", CVAR_RENDERER or CVAR_FLOAT, "scale value for stencil shadow drawing")
val r_frontBuffer = idCVar("r_frontBuffer", "0", CVAR_RENDERER or CVAR_BOOL, "draw to front buffer for debugging")
val r_skipSubviews = idCVar(
    "r_skipSubviews", "0", CVAR_RENDERER or CVAR_INTEGER, "1 = don't render any gui elements on surfaces"
)
val r_skipGuiShaders = idCVar(
    "r_skipGuiShaders",
    "0",
    CVAR_RENDERER or CVAR_INTEGER,
    "1 = skip all gui elements on surfaces, 2 = skip drawing but still handle events, 3 = draw but skip events",
    0.0f,
    3.0f,
    ArgCompletion_Integer(0, 3)
)
val r_skipParticles = idCVar(
    "r_skipParticles",
    "0",
    CVAR_RENDERER or CVAR_INTEGER,
    "1 = skip all particle systems",
    0.0f,
    1.0f,
    ArgCompletion_Integer(0, 1)
)
val r_subviewOnly = idCVar(
    "r_subviewOnly", "0", CVAR_RENDERER or CVAR_BOOL, "1 = don't render main view, allowing subviews to be debugged"
)
val r_shadows = idCVar("r_shadows", "1", CVAR_RENDERER or CVAR_BOOL or CVAR_ARCHIVE, "enable shadows")
val r_testARBProgram =
    idCVar("r_testARBProgram", "0", CVAR_RENDERER or CVAR_BOOL, "experiment with vertex/fragment programs")
val r_testGamma = idCVar(
    "r_testGamma", "0", CVAR_RENDERER or CVAR_FLOAT, "if > 0 draw a grid pattern to test gamma levels", 0.0f, 195.0f
)
val r_testGammaBias = idCVar(
    "r_testGammaBias", "0", CVAR_RENDERER or CVAR_FLOAT, "if > 0 draw a grid pattern to test gamma levels"
)
val r_testStepGamma = idCVar(
    "r_testStepGamma", "0", CVAR_RENDERER or CVAR_FLOAT, "if > 0 draw a grid pattern to test gamma levels"
)
val r_lightScale =
    idCVar("r_lightScale", "2", CVAR_RENDERER or CVAR_FLOAT, "all light intensities are multiplied by this")
val r_lightSourceRadius = idCVar("r_lightSourceRadius", "0", CVAR_RENDERER or CVAR_FLOAT, "for soft-shadow sampling")
val r_flareSize =
    idCVar("r_flareSize", "1", CVAR_RENDERER or CVAR_FLOAT, "scale the flare deforms from the material def")
val r_useExternalShadows = idCVar(
    "r_useExternalShadows",
    "1",
    CVAR_RENDERER or CVAR_INTEGER,
    "1 = skip drawing caps when outside the light volume, 2 = force to no caps for testing",
    0.0f,
    2.0f,
    ArgCompletion_Integer(0, 2)
)
val r_useOptimizedShadows = idCVar(
    "r_useOptimizedShadows", "1", CVAR_RENDERER or CVAR_BOOL, "use the dmap generated static shadow volumes"
)
val r_useScissor =
    idCVar("r_useScissor", "1", CVAR_RENDERER or CVAR_BOOL, "scissor clip as portals and lights are processed")
val r_useCombinerDisplayLists = idCVar(
    "r_useCombinerDisplayLists",
    "1",
    CVAR_RENDERER or CVAR_BOOL or CVAR_NOCHEAT,
    "put all nvidia register combiner programming in display lists"
)
val r_useDepthBoundsTest = idCVar(
    "r_useDepthBoundsTest", "1", CVAR_RENDERER or CVAR_BOOL, "use depth bounds test to reduce shadow fill"
)
val r_screenFraction = idCVar(
    "r_screenFraction",
    "100",
    CVAR_RENDERER or CVAR_INTEGER,
    "for testing fill rate, the resolution of the entire screen can be changed"
)
val r_demonstrateBug = idCVar(
    "r_demonstrateBug", "0", CVAR_RENDERER or CVAR_BOOL, "used during development to show IHV's their problems"
)
val r_usePortals = idCVar(
    "r_usePortals",
    "1",
    CVAR_RENDERER or CVAR_BOOL,
    " 1 = use portals to perform area culling, otherwise draw everything"
)
val r_singleLight = idCVar("r_singleLight", "-1", CVAR_RENDERER or CVAR_INTEGER, "suppress all but one light")
val r_singleEntity = idCVar("r_singleEntity", "-1", CVAR_RENDERER or CVAR_INTEGER, "suppress all but one entity")
val r_singleSurface = idCVar(
    "r_singleSurface", "-1", CVAR_RENDERER or CVAR_INTEGER, "suppress all but one surface on each entity"
)
val r_singleArea =
    idCVar("r_singleArea", "0", CVAR_RENDERER or CVAR_BOOL, "only draw the portal area the view is actually in")
val r_forceLoadImages = idCVar(
    "r_forceLoadImages", "0", CVAR_RENDERER or CVAR_ARCHIVE or CVAR_BOOL, "draw all images to screen after registration"
)
val r_orderIndexes = idCVar(
    "r_orderIndexes", "1", CVAR_RENDERER or CVAR_BOOL, "perform index reorganization to optimize vertex use"
)
val r_lightAllBackFaces = idCVar(
    "r_lightAllBackFaces", "0", CVAR_RENDERER or CVAR_BOOL, "light all the back faces, even when they would be shadowed"
)

// visual debugging info
val r_showPortals = idCVar(
    "r_showPortals", "0", CVAR_RENDERER or CVAR_BOOL, "draw portal outlines in color based on passed / not passed"
)
val r_showUnsmoothedTangents = idCVar(
    "r_showUnsmoothedTangents",
    "0",
    CVAR_RENDERER or CVAR_BOOL,
    "if 1, put all nvidia register combiner programming in display lists"
)
val r_showSilhouette = idCVar(
    "r_showSilhouette", "0", CVAR_RENDERER or CVAR_BOOL, "highlight edges that are casting shadow planes"
)
val r_showVertexColor = idCVar(
    "r_showVertexColor", "0", CVAR_RENDERER or CVAR_BOOL, "draws all triangles with the solid vertex color"
)
val r_showUpdates =
    idCVar("r_showUpdates", "0", CVAR_RENDERER or CVAR_BOOL, "report entity and light updates and ref counts")
val r_showDemo = idCVar("r_showDemo", "0", CVAR_RENDERER or CVAR_BOOL, "report reads and writes to the demo file")
val r_showDynamic =
    idCVar("r_showDynamic", "0", CVAR_RENDERER or CVAR_BOOL, "report stats on dynamic surface generation")
val r_showLightScale = idCVar(
    "r_showLightScale", "0", CVAR_RENDERER or CVAR_BOOL, "report the scale factor applied to drawing for overbrights"
)
val r_showDefs =
    idCVar("r_showDefs", "0", CVAR_RENDERER or CVAR_BOOL, "report the number of modeDefs and lightDefs in view")
val r_showTrace = idCVar(
    "r_showTrace",
    "0",
    CVAR_RENDERER or CVAR_INTEGER,
    "show the intersection of an eye trace with the world",
    ArgCompletion_Integer(0, 2)
)
val r_showIntensity = idCVar(
    "r_showIntensity",
    "0",
    CVAR_RENDERER or CVAR_BOOL,
    "draw the screen colors based on intensity, red = 0, green = 128, blue = 255"
)
val r_showImages = idCVar(
    "r_showImages",
    "0",
    CVAR_RENDERER or CVAR_INTEGER,
    "1 = show all images instead of rendering, 2 = show in proportional size",
    0.0f,
    2.0f,
    ArgCompletion_Integer(0, 2)
)
val r_showSmp = idCVar("r_showSmp", "0", CVAR_RENDERER or CVAR_BOOL, "show which end (front or back) is blocking")
val r_showLights = idCVar(
    "r_showLights",
    "0",
    CVAR_RENDERER or CVAR_INTEGER,
    "1 = just print volumes numbers, highlighting ones covering the view, 2 = also draw planes of each volume, 3 = also draw edges of each volume",
    0.0f,
    3.0f,
    ArgCompletion_Integer(0, 3)
)
val r_showShadows = idCVar(
    "r_showShadows",
    "0",
    CVAR_RENDERER or CVAR_INTEGER,
    "1 = visualize the stencil shadow volumes, 2 = draw filled in",
    0.0f,
    3.0f,
    ArgCompletion_Integer(0, 3)
)
val r_showShadowCount = idCVar(
    "r_showShadowCount",
    "0",
    CVAR_RENDERER or CVAR_INTEGER,
    "colors screen based on shadow volume depth complexity, >= 2 = print overdraw count based on stencil index values, 3 = only show turboshadows, 4 = only show static shadows",
    0.0f,
    4.0f,
    ArgCompletion_Integer(0, 4)
)
val r_showLightScissors =
    idCVar("r_showLightScissors", "0", CVAR_RENDERER or CVAR_BOOL, "show light scissor rectangles")
val r_showEntityScissors =
    idCVar("r_showEntityScissors", "0", CVAR_RENDERER or CVAR_BOOL, "show entity scissor rectangles")
val r_showInteractionFrustums = idCVar(
    "r_showInteractionFrustums",
    "0",
    CVAR_RENDERER or CVAR_INTEGER,
    "1 = show a frustum for each interaction, 2 = also draw lines to light origin, 3 = also draw entity bbox",
    0.0f,
    3.0f,
    ArgCompletion_Integer(0, 3)
)
val r_showInteractionScissors = idCVar(
    "r_showInteractionScissors",
    "0",
    CVAR_RENDERER or CVAR_INTEGER,
    "1 = show screen rectangle which contains the interaction frustum, 2 = also draw construction lines",
    0.0f,
    2.0f,
    ArgCompletion_Integer(0, 2)
)
val r_showLightCount = idCVar(
    "r_showLightCount",
    "0",
    CVAR_RENDERER or CVAR_INTEGER,
    "1 = colors surfaces based on light count, 2 = also count everything through walls, 3 = also print overdraw",
    0.0f,
    3.0f,
    ArgCompletion_Integer(0, 3)
)
val r_showViewEntitys = idCVar(
    "r_showViewEntitys",
    "0",
    CVAR_RENDERER or CVAR_INTEGER,
    "1 = displays the bounding boxes of all view models, 2 = print index numbers"
)
val r_showTris = idCVar(
    "r_showTris",
    "0",
    CVAR_RENDERER or CVAR_INTEGER,
    "enables wireframe rendering of the world, 1 = only draw visible ones, 2 = draw all front facing, 3 = draw all",
    0.0f,
    3.0f,
    ArgCompletion_Integer(0, 3)
)
val r_showSurfaceInfo =
    idCVar("r_showSurfaceInfo", "0", CVAR_RENDERER or CVAR_BOOL, "show surface material name under crosshair")
val r_showNormals = idCVar("r_showNormals", "0", CVAR_RENDERER or CVAR_FLOAT, "draws wireframe normals")
val r_showMemory = idCVar("r_showMemory", "0", CVAR_RENDERER or CVAR_BOOL, "print frame memory utilization")
val r_showCull = idCVar("r_showCull", "0", CVAR_RENDERER or CVAR_BOOL, "report sphere and box culling stats")
val r_showInteractions =
    idCVar("r_showInteractions", "0", CVAR_RENDERER or CVAR_BOOL, "report interaction generation activity")
val r_showDepth = idCVar(
    "r_showDepth", "0", CVAR_RENDERER or CVAR_BOOL, "display the contents of the depth buffer and the depth range"
)
val r_showSurfaces = idCVar("r_showSurfaces", "0", CVAR_RENDERER or CVAR_BOOL, "report surface/light/shadow counts")
val r_showPrimitives =
    idCVar("r_showPrimitives", "0", CVAR_RENDERER or CVAR_INTEGER, "report drawsurf/index/vertex counts")
val r_showEdges = idCVar("r_showEdges", "0", CVAR_RENDERER or CVAR_BOOL, "draw the sil edges")
val r_showTexturePolarity =
    idCVar("r_showTexturePolarity", "0", CVAR_RENDERER or CVAR_BOOL, "shade triangles by texture area polarity")
val r_showTangentSpace = idCVar(
    "r_showTangentSpace",
    "0",
    CVAR_RENDERER or CVAR_INTEGER,
    "shade triangles by tangent space, 1 = use 1st tangent vector, 2 = use 2nd tangent vector, 3 = use normal vector",
    0.0f,
    3.0f,
    ArgCompletion_Integer(0, 3)
)
val r_showDominantTri = idCVar(
    "r_showDominantTri", "0", CVAR_RENDERER or CVAR_BOOL, "draw lines from vertexes to center of dominant triangles"
)
val r_showAlloc = idCVar("r_showAlloc", "0", CVAR_RENDERER or CVAR_BOOL, "report alloc/free counts")
val r_showTextureVectors = idCVar(
    "r_showTextureVectors",
    "0",
    CVAR_RENDERER or CVAR_FLOAT,
    " if > 0 draw each triangles texture =new idCVar(tangent) vectors"
)
val r_showOverDraw = idCVar(
    "r_showOverDraw",
    "0",
    CVAR_RENDERER or CVAR_INTEGER,
    "1 = geometry overdraw, 2 = light interaction overdraw, 3 = geometry and light interaction overdraw",
    0.0f,
    3.0f,
    ArgCompletion_Integer(0, 3)
)
val r_lockSurfaces = idCVar(
    "r_lockSurfaces",
    "0",
    CVAR_RENDERER or CVAR_BOOL,
    "allow moving the view point without changing the composition of the scene, including culling"
)
val r_useEntityCallbacks = idCVar(
    "r_useEntityCallbacks",
    "1",
    CVAR_RENDERER or CVAR_BOOL,
    "if 0, issue the callback immediately at update time, rather than defering"
)
val r_showSkel = idCVar(
    "r_showSkel",
    "0",
    CVAR_RENDERER or CVAR_INTEGER,
    "draw the skeleton when model animates, 1 = draw model with skeleton, 2 = draw skeleton only",
    0.0f,
    2.0f,
    ArgCompletion_Integer(0, 2)
)
val r_jointNameScale = idCVar(
    "r_jointNameScale", "0.02", CVAR_RENDERER or CVAR_FLOAT, "size of joint names when r_showskel is set to 1"
)
val r_jointNameOffset = idCVar(
    "r_jointNameOffset", "0.5", CVAR_RENDERER or CVAR_FLOAT, "offset of joint names when r_showskel is set to 1"
)
val r_debugLineDepthTest = idCVar(
    "r_debugLineDepthTest", "0", CVAR_RENDERER or CVAR_ARCHIVE or CVAR_BOOL, "perform depth test on debug lines"
)
val r_debugLineWidth =
    idCVar("r_debugLineWidth", "1", CVAR_RENDERER or CVAR_ARCHIVE or CVAR_BOOL, "width of debug lines")
val r_debugArrowStep = idCVar(
    "r_debugArrowStep",
    "120",
    CVAR_RENDERER or CVAR_ARCHIVE or CVAR_INTEGER,
    "step size of arrow cone line rotation in degrees",
    0.0f,
    120.0f
)
val r_debugPolygonFilled = idCVar("r_debugPolygonFilled", "1", CVAR_RENDERER or CVAR_BOOL, "draw a filled polygon")
val r_materialOverride = idCVar(
    "r_materialOverride", "", CVAR_RENDERER, "overrides all materials", ArgCompletion_Decl(declType_t.DECL_MATERIAL)
)
val r_debugRenderToTexture = idCVar("r_debugRenderToTexture", "0", CVAR_RENDERER or CVAR_INTEGER, "")

/*
 ==================
 GL_CheckErrors
 ==================
 */
fun GL_CheckErrors() {
    var err: Int
    var s: String?
    var i: Int

    // check for up to 10 errors pending
    i = 0
    while (i < 10) {
        err = qgl.qglGetError()
        if (err == GL11.GL_NO_ERROR) {
            return
        }
        when (err) {
            GL11.GL_INVALID_ENUM -> s = "GL_INVALID_ENUM"
            GL11.GL_INVALID_VALUE -> s = "GL_INVALID_VALUE"
            GL11.GL_INVALID_OPERATION -> s = "GL_INVALID_OPERATION"
            GL11.GL_STACK_OVERFLOW -> s = "GL_STACK_OVERFLOW"
            GL11.GL_STACK_UNDERFLOW -> s = "GL_STACK_UNDERFLOW"
            GL11.GL_OUT_OF_MEMORY -> s = "GL_OUT_OF_MEMORY"
            else -> {
                val ss = CharArray(64)
                snPrintf(ss, 64, "%d", err)
                s = ctos(ss)
            }
        }
        if (!r_ignoreGLErrors.GetBool()) {
            common.Printf("GL_CheckErrors: %s\n", s)
        }
        i++
    }
}

/*
 ==================
 R_ScreenshotFilename

 Returns a filename with digits appended
 if we have saved a previous screenshot, don't scan
 from the beginning, because recording demo avis can involve
 thousands of shots
 ==================
 */
fun R_ScreenshotFilename(lastNumber: CInt, base: String?, fileName: idStr) {
    var a: Int
    var b: Int
    var c: Int
    var d: Int
    var e: Int
    val restrict: Boolean = cvarSystem.GetCVarBool("fs_restrict")
    cvarSystem.SetCVarBool("fs_restrict", false)
    lastNumber.increment()
    if (lastNumber._val > 99999) {
        lastNumber._val = 99999
    }
    while (lastNumber._val < 99999) {
        var frac: Int = lastNumber._val
        a = frac / 10000
        frac -= a * 10000
        b = frac / 1000
        frac -= b * 1000
        c = frac / 100
        frac -= c * 100
        d = frac / 10
        frac -= d * 10
        e = frac
        fileName.set(String.format("%s%d%d%d%d%d.tga", base, a, b, c, d, e))
        if (lastNumber._val == 99999) {
            break
        }
        val len: Int = fileSystem.ReadFile(fileName.toString(), null, null)
        if (len <= 0) {
            break
        }
        lastNumber.increment()
    }
    cvarSystem.SetCVarBool("fs_restrict", restrict)
}

/*
 =================
 R_InitCvars
 =================
 */
fun R_InitCvars() {
    // update latched cvars here
}

/*
 =================
 R_InitCommands
 =================
 */
fun R_InitCommands() {
    cmdSystem.AddCommand(
        "MakeMegaTexture", MakeMegaTexture_f.instance, CMD_FL_RENDERER or CMD_FL_CHEAT, "processes giant images"
    )
    cmdSystem.AddCommand("sizeUp", R_SizeUp_f.instance, CMD_FL_RENDERER, "makes the rendered view larger")
    cmdSystem.AddCommand("sizeDown", R_SizeDown_f.instance, CMD_FL_RENDERER, "makes the rendered view smaller")
    cmdSystem.AddCommand("reloadGuis", R_ReloadGuis_f.instance, CMD_FL_RENDERER, "reloads guis")
    cmdSystem.AddCommand("listGuis", R_ListGuis_f.instance, CMD_FL_RENDERER, "lists guis")
    cmdSystem.AddCommand("touchGui", R_TouchGui_f.instance, CMD_FL_RENDERER, "touches a gui")
    cmdSystem.AddCommand("screenshot", R_ScreenShot_f.instance, CMD_FL_RENDERER, "takes a screenshot")
    cmdSystem.AddCommand("envshot", R_EnvShot_f.instance, CMD_FL_RENDERER, "takes an environment shot")
    cmdSystem.AddCommand(
        "makeAmbientMap", R_MakeAmbientMap_f.instance, CMD_FL_RENDERER or CMD_FL_CHEAT, "makes an ambient map"
    )
    cmdSystem.AddCommand("benchmark", R_Benchmark_f.instance, CMD_FL_RENDERER, "benchmark")
    cmdSystem.AddCommand("gfxInfo", GfxInfo_f.instance, CMD_FL_RENDERER, "show graphics info")
    cmdSystem.AddCommand(
        "modulateLights",
        tr_lightrun.R_ModulateLights_f.instance,
        CMD_FL_RENDERER or CMD_FL_CHEAT,
        "modifies shader parms on all lights"
    )
    cmdSystem.AddCommand(
        "testImage",
        R_TestImage_f.instance,
        CMD_FL_RENDERER or CMD_FL_CHEAT,
        "displays the given image centered on screen",
        ArgCompletion_ImageName.getInstance()
    )
    cmdSystem.AddCommand(
        "testVideo",
        R_TestVideo_f.instance,
        CMD_FL_RENDERER or CMD_FL_CHEAT,
        "displays the given cinematic",
        ArgCompletion_VideoName.getInstance()
    )
    cmdSystem.AddCommand(
        "reportSurfaceAreas",
        R_ReportSurfaceAreas_f.instance,
        CMD_FL_RENDERER,
        "lists all used materials sorted by surface area"
    )
    cmdSystem.AddCommand(
        "reportImageDuplication",
        R_ReportImageDuplication_f.instance,
        CMD_FL_RENDERER,
        "checks all referenced images for duplications"
    )
    cmdSystem.AddCommand(
        "regenerateWorld", tr_lightrun.R_RegenerateWorld_f.instance, CMD_FL_RENDERER, "regenerates all interactions"
    )
    cmdSystem.AddCommand(
        "showInteractionMemory",
        R_ShowInteractionMemory_f.instance,
        CMD_FL_RENDERER,
        "shows memory used by interactions"
    )
    cmdSystem.AddCommand(
        "showTriSurfMemory", R_ShowTriSurfMemory_f.instance, CMD_FL_RENDERER, "shows memory used by triangle surfaces"
    )
    cmdSystem.AddCommand("vid_restart", R_VidRestart_f.instance, CMD_FL_RENDERER, "restarts renderSystem")
    cmdSystem.AddCommand(
        "listRenderEntityDefs", RenderWorld.R_ListRenderEntityDefs_f.instance, CMD_FL_RENDERER, "lists the entity defs"
    )
    cmdSystem.AddCommand(
        "listRenderLightDefs", RenderWorld.R_ListRenderLightDefs_f.instance, CMD_FL_RENDERER, "lists the light defs"
    )
    cmdSystem.AddCommand("listModes", R_ListModes_f.instance, CMD_FL_RENDERER, "lists all video modes")
    cmdSystem.AddCommand(
        "reloadSurface", R_ReloadSurface_f.instance, CMD_FL_RENDERER, "reloads the decl and images for selected surface"
    )
}

/*
 =================
 R_InitMaterials
 =================
 */
fun R_InitMaterials() {
    tr.defaultMaterial = DeclManager.declManager.FindMaterial("_default", false)
    if (tr.defaultMaterial == null) {
        common.FatalError("_default material not found")
    }
    DeclManager.declManager.FindMaterial("_default", false)

    // needed by R_DeriveLightData
    DeclManager.declManager.FindMaterial("lights/defaultPointLight")
    DeclManager.declManager.FindMaterial("lights/defaultProjectedLight")
}

//#if MACOS_X
//bool R_GetModeInfo( int *width, int *height, int mode ) {
//#else
fun R_GetModeInfo(width: IntArray?, height: IntArray?, mode: Int): Boolean {
//#endif
    val vm: vidmode_s
    if (mode < -1) {
        return false
    }
    if (mode >= s_numVidModes) {
        return false
    }
    if (mode == -1) {
        width!![0] = r_customWidth.GetInteger()
        height!![0] = r_customHeight.GetInteger()
        return true
    }
    vm = r_vidModes[mode]
    if (width != null) {
        width[0] = vm.width
    }
    if (height != null) {
        height[0] = vm.height
    }
    return true
}

/*
 ==================
 R_CheckPortableExtensions

 ==================
 */
fun R_CheckPortableExtensions() {
    glConfig.glVersion = atof(
        glConfig.version_string!!.replace(
            "(\\d+).(\\d+).(\\d+)".toRegex(), "$1.$2$3"
        )
    ) // converts openGL version from 1.1.x to 1.1x, which we can parse to float.
    //
    // GL_ARB_multitexture
    glConfig.multitextureAvailable = R_CheckExtension("GL_ARB_multitexture")
    if (glConfig.multitextureAvailable) {
        glConfig.maxTextureUnits = qgl.qglGetInteger(ARBMultitexture.GL_MAX_TEXTURE_UNITS_ARB)
        if (glConfig.maxTextureUnits > MAX_MULTITEXTURE_UNITS) {
            glConfig.maxTextureUnits = MAX_MULTITEXTURE_UNITS
        }
        if (glConfig.maxTextureUnits < 2) {
            glConfig.multitextureAvailable = false // shouldn't ever happen
        }
        glConfig.maxTextureCoords = qgl.qglGetInteger(ARBFragmentProgram.GL_MAX_TEXTURE_COORDS_ARB)
        glConfig.maxTextureImageUnits = qgl.qglGetInteger(ARBFragmentProgram.GL_MAX_TEXTURE_IMAGE_UNITS_ARB)
    }
    //
    // GL_ARB_texture_env_combine
    glConfig.textureEnvCombineAvailable = R_CheckExtension("GL_ARB_texture_env_combine")

    // GL_ARB_texture_cube_map
    glConfig.cubeMapAvailable = R_CheckExtension("GL_ARB_texture_cube_map")

    // GL_ARB_texture_env_dot3
    glConfig.envDot3Available = R_CheckExtension("GL_ARB_texture_env_dot3")

    // GL_ARB_texture_env_add
    glConfig.textureEnvAddAvailable = R_CheckExtension("GL_ARB_texture_env_add")

    // GL_ARB_texture_non_power_of_two
    glConfig.textureNonPowerOfTwoAvailable = R_CheckExtension("GL_ARB_texture_non_power_of_two")
    //
    // GL_ARB_texture_compression + GL_S3_s3tc
    // DRI drivers may have GL_ARB_texture_compression but no GL_EXT_texture_compression_s3tc
    glConfig.textureCompressionAvailable =
        R_CheckExtension("GL_ARB_texture_compression") && R_CheckExtension("GL_EXT_texture_compression_s3tc")
    //
    // GL_ARB_texture_compression_bptc (BC7)
    glConfig.bptcTextureCompressionAvailable = R_CheckExtension("GL_ARB_texture_compression_bptc")
    //
    // GL_EXT_texture_filter_anisotropic
    glConfig.anisotropicAvailable = R_CheckExtension("GL_EXT_texture_filter_anisotropic")
    if (glConfig.anisotropicAvailable) {
        val maxTextureAnisotropy: FloatBuffer = BufferUtils.createFloatBuffer(16)
        qgl.qglGetFloatv(EXTTextureFilterAnisotropic.GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT, maxTextureAnisotropy)
        common.Printf(
            "   maxTextureAnisotropy: %f\n",
            (maxTextureAnisotropy.get().also({ glConfig.maxTextureAnisotropy = it.toFloat() }))
        )
    } else {
        glConfig.maxTextureAnisotropy = 1.0f
    }
    //
    // GL_EXT_texture_lod_bias
    // The actual extension is broken as specificed, storing the state in the texture unit instead
    // of the texture object.  The behavior in GL 1.4 is the behavior we use.
    if (glConfig.glVersion >= 1.4 || R_CheckExtension("GL_EXT_texture_lod")) {
        common.Printf("...using %s\n", "GL_1.4_texture_lod_bias")
        glConfig.textureLODBiasAvailable = true
    } else {
        common.Printf("X..%s not found\n", "GL_1.4_texture_lod_bias")
        glConfig.textureLODBiasAvailable = false
    }
    //
    // GL_EXT_shared_texture_palette
    glConfig.sharedTexturePaletteAvailable = R_CheckExtension("GL_EXT_shared_texture_palette")
    //
    // GL_EXT_texture3D (not currently used for anything)
    glConfig.texture3DAvailable = R_CheckExtension("GL_EXT_texture3D")
    //
    // EXT_stencil_wrap
    // This isn't very important, but some pathological case might cause a clamp error and give a shadow bug.
    // Nvidia also believes that future hardware may be able to run faster with this enabled to avoid the
    // serialization of clamping.
    if (R_CheckExtension("GL_EXT_stencil_wrap")) {
        tr.stencilIncr = EXTStencilWrap.GL_INCR_WRAP_EXT
        tr.stencilDecr = EXTStencilWrap.GL_DECR_WRAP_EXT
    } else {
        tr.stencilIncr = GL11.GL_INCR
        tr.stencilDecr = GL11.GL_DECR
    }
    //
    // GL_EXT_stencil_two_side
    glConfig.twoSidedStencilAvailable = R_CheckExtension("GL_EXT_stencil_two_side")
    //
    // ARB_vertex_buffer_object
    glConfig.ARBVertexBufferObjectAvailable = R_CheckExtension("GL_ARB_vertex_buffer_object")
    //
    // ARB_vertex_program
    glConfig.ARBVertexProgramAvailable = R_CheckExtension("GL_ARB_vertex_program")
    //
    // ARB_fragment_program
    if (r_inhibitFragmentProgram.GetBool()) {
        glConfig.ARBFragmentProgramAvailable = false
    } else {
        glConfig.ARBFragmentProgramAvailable = R_CheckExtension("GL_ARB_fragment_program")
    }

    if (!glConfig.multitextureAvailable || !glConfig.textureEnvCombineAvailable
        || !glConfig.cubeMapAvailable || !glConfig.envDot3Available
    ) {
        common.Error(common.GetLanguageDict().GetString("#str_06780"))
    }
    //
    // GL_EXT_depth_bounds_test
    glConfig.depthBoundsTestAvailable = R_CheckExtension("EXT_depth_bounds_test")
}

/*
 ==================
 R_SampleCubeMap
 ==================
 */
private fun R_SampleCubeMap(dir: idVec3, size: Int, buffers: Array<ByteBuffer> /*[6]*/, result: ByteArray /*[4]*/) {
    val adir = FloatArray(3)
    val axis: Int
    var x: Int
    var y: Int
    adir[0] = abs(dir[0])
    adir[1] = abs(dir[1])
    adir[2] = abs(dir[2])
    if (dir[0] >= adir[1] && dir[0] >= adir[2]) {
        axis = 0
    } else if (-dir[0] >= adir[1] && -dir[0] >= adir[2]) {
        axis = 1
    } else if (dir[1] >= adir[0] && dir[1] >= adir[2]) {
        axis = 2
    } else if (-dir[1] >= adir[0] && -dir[1] >= adir[2]) {
        axis = 3
    } else if (dir[2] >= adir[1] && dir[2] >= adir[2]) {
        axis = 4
    } else {
        axis = 5
    }
    var fx: Float = (dir.times(cubeAxis[axis][1])) / (dir.times(
        cubeAxis[axis][0]
    ))
    var fy: Float = (dir.times(cubeAxis[axis][2])) / (dir.times(
        cubeAxis[axis][0]
    ))
    fx = -fx
    fy = -fy
    x = (size * 0.5f * (fx + 1)).toInt()
    y = (size * 0.5f * (fy + 1)).toInt()
    if (x < 0) {
        x = 0
    } else if (x >= size) {
        x = size - 1
    }
    if (y < 0) {
        y = 0
    } else if (y >= size) {
        y = size - 1
    }
    result[0] = buffers[axis].get((y * size + x) * 4 + 0)
    result[1] = buffers[axis].get((y * size + x) * 4 + 1)
    result[2] = buffers[axis].get((y * size + x) * 4 + 2)
    result[3] = buffers[axis].get((y * size + x) * 4 + 3)
}

/*
 ================
 R_RenderingFPS
 ================
 */
fun R_RenderingFPS(renderView: renderView_s?): Float {
    qgl.qglFinish()
    val start: Int = Sys_Milliseconds()
    var end: Int
    var count = 0
    while (true) {
        // render
        RenderSystem.renderSystem.BeginFrame(glConfig.vidWidth, glConfig.vidHeight)
        tr.primaryWorld!!.RenderScene(renderView!!)
        RenderSystem.renderSystem.EndFrame(null, null)
        qgl.qglFinish()
        count++
        end = Sys_Milliseconds()
        if (end - start > SAMPLE_MSEC) {
            break
        }
    }
    val fps: Float = (count * 1000.0f / (end - start))
    return fps
}

fun R_InitOpenGL() {
    val temp: IntBuffer = BufferUtils.createIntBuffer(16)
    val parms = glimpParms_t()
    var i: Int
    common.Printf("----- R_InitOpenGL -----\n")
    if (glConfig.isInitialized) {
        common.FatalError("R_InitOpenGL called while active")
    }

    GLFWErrorCallback.createPrint(System.err).set()

    // in case we had an error while doing a tiled rendering
    tr.viewportOffset[0] = 0
    tr.viewportOffset[1] = 0

    //
    // initialize OS specific portions of the renderSystem
    //
    i = 0
    while (i < 2) {

        // set the parameters we are trying
        val vidWidth: IntArray = intArrayOf(0)
        val vidHeight: IntArray = intArrayOf(0)
        R_GetModeInfo(vidWidth, vidHeight, r_mode.GetInteger())
        glConfig.vidWidth = vidWidth[0]
        glConfig.vidHeight = vidHeight[0]
        parms.width = glConfig.vidWidth
        parms.height = glConfig.vidHeight
        parms.fullScreen = r_fullscreen.GetBool()
        parms.displayHz = r_displayRefresh.GetInteger()
        parms.multiSamples = r_multiSamples.GetInteger()
        parms.stereo = false
        if (GLimp_Init(parms)) {
            // it's ALIVE!
            break
        }
        if (i == 1) {
            common.FatalError("Unable to initialize OpenGL")
        }

        // if we failed, set everything back to "safe mode"
        // and try again
        r_mode.SetInteger(3)
        r_fullscreen.SetInteger(0)
        r_displayRefresh.SetInteger(0)
        r_multiSamples.SetInteger(0)
        i++
    }

    // input and sound systems need to be tied to the new window
    Sys_InitInput()
    snd_system.soundSystem.InitHW()

    // get our config strings
    glConfig.vendor_string = qglGetString(GL11.GL_VENDOR)
    glConfig.renderer_string = qglGetString(GL11.GL_RENDERER)
    glConfig.version_string = qglGetString(GL11.GL_VERSION)
    glConfig.extensions_string = qglGetString(GL11.GL_EXTENSIONS)

    // OpenGL driver constants
    qgl.qglGetIntegerv(GL11.GL_MAX_TEXTURE_SIZE, temp)
    glConfig.maxTextureSize = temp.get()

    // stubbed or broken drivers may have reported 0...
    if (glConfig.maxTextureSize <= 0) {
        glConfig.maxTextureSize = 256
    }
    glConfig.isInitialized = true

    // recheck all the extensions (FIXME: this might be dangerous)
    R_CheckPortableExtensions()

    // parse our vertex and fragment programs, possibly disable support for
    // one of the paths if there was an error
    draw_arb2.R_ARB2_Init()
    cmdSystem.AddCommand(
        "reloadARBprograms", R_ReloadARBPrograms_f.instance, CMD_FL_RENDERER, "reloads ARB programs"
    )
    R_ReloadARBPrograms_f.instance.run(null)

    // allocate the vertex array range or vertex objects
    VertexCache.vertexCache.Init()

    // select which renderSystem we are going to use
    r_renderer.SetModified()
    tr.SetBackEndRenderer()

    // allocate the frame data, which may be more if smp is enabled
    tr_main.R_InitFrameData()

    // Reset our gamma
    r_gammaInShader.ClearModified()
    if (r_gammaInShader.GetBool()) {
        common.Printf("Will apply r_gamma and r_brightness in shaders (r_gammaInShader 1)\n")
    } else {
        common.Printf("Will apply r_gamma and r_brightness in hardware (possibly on all screens; r_gammaInShader 0)\n")
        R_SetColorMappings()
    }

    if (WIN32) {
        if (!glCheck) {
            glCheck = true
            if (0 == Icmp(
                    glConfig.vendor_string!!, "Microsoft"
                ) && FindText(glConfig.renderer_string!!, "OpenGL-D3D") != -1
            ) {
                if (cvarSystem.GetCVarBool("r_fullscreen")) {
                    cmdSystem.BufferCommandText(cmdExecution_t.CMD_EXEC_NOW, "vid_restart partial windowed\n")
                    Sys_GrabMouseCursor(false)
                }
                if (cvarSystem.GetCVarBool("r_fullscreen")) {
                    cmdSystem.BufferCommandText(cmdExecution_t.CMD_EXEC_APPEND, "vid_restart\n")
                }
            }
        }
    }
}

/*
 ===============
 R_SetColorMappings
 ===============
 */
fun R_SetColorMappings() {

    if (r_gammaInShader.GetBool()) {
        // nothing to do here
        return
    }

    var j: Int
    var inf: Int
    var g: Float
    var b: Float

    var gammaTable = ShortArray(256)

    b = r_brightness.GetFloat()
    g = r_gamma.GetFloat()


    for (i in 0 until 256) {
        j = (i * b).toInt()
        if (j > 255) {
            j = 255
        }

        if (g == 1.0f) {
            inf = (j shl 8) or j
        } else {
            inf = (0xffff * (j / 255.0f).pow(1.0f / g) + 0.5f).toInt()
        }
        if (inf < 0) {
            inf = 0
        }
        if (inf > 0xffff) {
            inf = 0xffff
        }

        gammaTable[i] = inf.toShort()
    }

    GLimp_SetGamma(gammaTable, gammaTable, gammaTable)
}

/*
 ===============
 R_StencilShot
 Save out a screenshot showing the stencil buffer expanded by 16x range
 ===============
 */
fun R_StencilShot() {
    var buffer: ByteBuffer
    var i: Int
    val c: Int
    val width: Int = tr.GetScreenWidth()
    val height: Int = tr.GetScreenHeight()
    val pix: Int = width * height
    c = pix * 3 + 18
    buffer = ByteBuffer.allocate(c)
    var byteBuffer: ByteBuffer = ByteBuffer.allocate(pix)
    qgl.qglReadPixels(0, 0, width, height, GL11.GL_STENCIL_INDEX, GL11.GL_UNSIGNED_BYTE, byteBuffer)
    i = 0
    while (i < pix) {
        buffer.put(18 + i * 3, byteBuffer.get(i))
        buffer.put(18 + (i * 3) + 1, byteBuffer.get(i))
        buffer.put(18 + (i * 3) + 2, byteBuffer.get(i))
        i++
    }

    // fill in the header (this is vertically flipped, which qglReadPixels emits)
    buffer.put(2, 2.toByte()) // uncompressed type
    buffer.put(12, (width and 255).toByte())
    buffer.put(13, (width shr 8).toByte())
    buffer.put(14, (height and 255).toByte())
    buffer.put(15, (height shr 8).toByte())
    buffer.put(16, 24.toByte()) // pixel size
    fileSystem.WriteFile("screenshots/stencilShot.tga", buffer, c, "fs_savepath")
}

/*
 =================
 R_CheckExtension
 =================
 */
fun R_CheckExtension(name: String?): Boolean {
    if ((null == glConfig.extensions_string || !glConfig.extensions_string!!.contains((name)!!))) {
        common.Printf("X..%s not found\n", name!!)
        return false
    }
    common.Printf("...using %s\n", name)
    return true
}

/*
 ====================
 R_ReadTiledPixels

 Allows the rendering of an image larger than the actual window by
 tiling it into window-sized chunks and rendering each chunk separately

 If ref isn't specified, the full session UpdateScreen will be done.
 ====================
 */
fun R_ReadTiledPixels(width: Int, height: Int, buffer: ByteArray?, offset: Int, ref: renderView_s? /*= NULL*/) {
    val temp = BufferUtils.createByteBuffer(glConfig.vidWidth * glConfig.vidHeight * 3)
    val oldWidth: Int = glConfig.vidWidth
    val oldHeight: Int = glConfig.vidHeight
    tr.tiledViewport[0] = width
    tr.tiledViewport[1] = height

    // disable scissor, so we don't need to adjust all those rects
    r_useScissor.SetBool(false)
    var xo = 0
    while (xo < width) {
        var yo = 0
        while (yo < height) {
            tr.viewportOffset[0] = -xo
            tr.viewportOffset[1] = -yo
            if (ref != null) {
                tr.BeginFrame(oldWidth, oldHeight)
                tr.primaryWorld!!.RenderScene(ref)
                tr.EndFrame(null, null)
            } else {
                Session.session.UpdateScreen()
            }
            var w: Int = oldWidth
            if (xo + w > width) {
                w = width - xo
            }
            var h: Int = oldHeight
            if (yo + h > height) {
                h = height - yo
            }
            qgl.qglReadBuffer(GL11.GL_FRONT)
            qgl.qglPixelStorei(GL11.GL_PACK_ALIGNMENT, 1)
            temp.clear()
            qgl.qglReadPixels(0, 0, w, h, GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE, temp)
            val row = w * 3
            for (y in 0 until h) {
                temp.position(y * row)
                temp.get(buffer!!, offset + (((yo + y) * width + xo) * 3), w * 3)
            }
            yo += oldHeight
        }
        xo += oldWidth
    }
    r_useScissor.SetBool(true)
    tr.viewportOffset[0] = 0
    tr.viewportOffset[1] = 0
    tr.tiledViewport[0] = 0
    tr.tiledViewport[1] = 0
    glConfig.vidWidth = oldWidth
    glConfig.vidHeight = oldHeight
}

/*
 ====================
 R_GetModeInfo

 r_mode is normally a small non-negative integer that
 looks resolutions up in a table, but if it is set to -1,
 the values from r_customWidth, amd r_customHeight
 will be used instead.
 ====================
 */
class vidmode_s(var description: String, var width: Int, var height: Int)

/*
 =================
 R_SizeUp_f

 Keybinding command
 =================
 */
internal class R_SizeUp_f private constructor() : cmdFunction_t() {
    override fun run(args: CmdArgs.idCmdArgs?) {
        if (r_screenFraction.GetInteger() + 10 > 100) {
            r_screenFraction.SetInteger(100)
        } else {
            r_screenFraction.SetInteger(r_screenFraction.GetInteger() + 10)
        }
    }

    companion object {
        val instance: cmdFunction_t = R_SizeUp_f()
    }
}

/*
 =================
 R_SizeDown_f

 Keybinding command
 =================
 */
internal class R_SizeDown_f private constructor() : cmdFunction_t() {
    override fun run(args: CmdArgs.idCmdArgs?) {
        if (r_screenFraction.GetInteger() - 10 < 10) {
            r_screenFraction.SetInteger(10)
        } else {
            r_screenFraction.SetInteger(r_screenFraction.GetInteger() - 10)
        }
    }

    companion object {
        val instance: cmdFunction_t = R_SizeDown_f()
    }
}

/*
 ===============
 TouchGui_f

 this is called from the main thread
 ===============
 */
internal class R_TouchGui_f private constructor() : cmdFunction_t() {
    override fun run(args: CmdArgs.idCmdArgs?) {
        val gui: String = args!!.Argv(1)
        if (gui == null || gui.isEmpty()) {
            common.Printf("USAGE: touchGui <guiName>\n")
            return
        }
        common.Printf("touchGui %s\n", gui)
        Session.session.UpdateScreen()
        uiManager.Touch(gui)
    }

    companion object {
        val instance: cmdFunction_t = R_TouchGui_f()
    }
}

internal class R_ScreenShot_f private constructor() : cmdFunction_t() {
    override fun run(args: CmdArgs.idCmdArgs?) {
        val checkname = idStr()
        var width: Int = glConfig.vidWidth
        var height: Int = glConfig.vidHeight
        var blends = 0
        when (args!!.Argc()) {
            1 -> {
                width = glConfig.vidWidth
                height = glConfig.vidHeight
                blends = 1
                R_ScreenshotFilename(lastNumber, "screenshots/shot", checkname)
            }

            2 -> {
                width = glConfig.vidWidth
                height = glConfig.vidHeight
                blends = 1
                checkname.set(args.Argv(1))
            }

            3 -> {
                width = args.Argv(1).toInt()
                height = args.Argv(2).toInt()
                blends = 1
                R_ScreenshotFilename(lastNumber, "screenshots/shot", checkname)
            }

            4 -> {
                width = args.Argv(1).toInt()
                height = args.Argv(2).toInt()
                blends = args.Argv(3).toInt()
                if (blends < 1) {
                    blends = 1
                }
                if (blends > MAX_BLENDS) {
                    blends = MAX_BLENDS
                }
                R_ScreenshotFilename(lastNumber, "screenshots/shot", checkname)
            }

            else -> {
                common.Printf("usage: screenshot\n       screenshot <filename>\n       screenshot <width> <height>\n       screenshot <width> <height> <blends>\n")
                return
            }
        }

        // put the console away
        Console.console.Close()
        tr.TakeScreenshot(width, height, checkname.toString(), blends, null)
        common.Printf("Wrote %s\n", checkname)
    }

    companion object {
        val instance: cmdFunction_t = R_ScreenShot_f()
        private val lastNumber: CInt = CInt()
    }
}

/*
 ==================
 R_EnvShot_f

 envshot <basename>

 Saves out env/<basename>_ft.tga, etc
 ==================
 */
internal class R_EnvShot_f private constructor() : cmdFunction_t() {
    override fun run(args: CmdArgs.idCmdArgs?) {
        var fullname: String? = null
        val baseName: String
        var i: Int
        val axis: Array<idMat3> = Array(6) { idMat3() }
        var ref: renderView_s
        val primary: viewDef_s
        var blends: Int
        val extensions /*[6]*/: Array<String> =
            arrayOf("_px.tga", "_nx.tga", "_py.tga", "_ny.tga", "_pz.tga", "_nz.tga")
        val size: Int
        if ((args!!.Argc() != 2) && (args.Argc() != 3) && (args.Argc() != 4)) {
            common.Printf("USAGE: envshot <basename> [size] [blends]\n")
            return
        }
        baseName = args.Argv(1)
        blends = 1
        if (args.Argc() == 4) {
            size = args.Argv(2).toInt()
            blends = args.Argv(3).toInt()
        } else if (args.Argc() == 3) {
            size = args.Argv(2).toInt()
            blends = 1
        } else {
            size = 256
            blends = 1
        }
        if (tr.primaryView == null) {
            common.Printf("No primary view.\n")
            return
        }
        primary = viewDef_s(tr.primaryView!!)

        axis[0].set(0, 0, 1.0f)
        axis[0].set(1, 2, 1.0f)
        axis[0].set(2, 1, 1.0f)
        axis[1].set(0, 0, -1.0f)
        axis[1].set(1, 2, -1.0f)
        axis[1].set(2, 1, 1.0f)
        axis[2].set(0, 1, 1.0f)
        axis[2].set(1, 0, -1.0f)
        axis[2].set(2, 2, -1.0f)
        axis[3].set(0, 1, -1.0f)
        axis[3].set(1, 0, -1.0f)
        axis[3].set(2, 2, 1.0f)
        axis[4].set(0, 2, 1.0f)
        axis[4].set(1, 0, -1.0f)
        axis[4].set(2, 1, 1.0f)
        axis[5].set(0, 2, -1.0f)
        axis[5].set(1, 0, 1.0f)
        axis[5].set(2, 1, 1.0f)
        i = 0
        while (i < 6) {
            ref = renderView_s(primary.renderView)
            ref.y = 0
            ref.x = ref.y
            ref.fov_y = 90.0f
            ref.fov_x = ref.fov_y
            ref.width = glConfig.vidWidth
            ref.height = glConfig.vidHeight
            ref.viewaxis.set(idMat3((axis[i])))
            fullname = String.format("env/%s%s", baseName, extensions[i])
            tr.TakeScreenshot(size, size, fullname, blends, ref)
            i++
        }
        common.Printf("Wrote %s, etc\n", fullname!!)
    }

    companion object {
        val instance: cmdFunction_t = R_EnvShot_f()
    }
}

/*
 ==================
 R_MakeAmbientMap_f

 R_MakeAmbientMap_f <basename> [size]

 Saves out env/<basename>_amb_ft.tga, etc
 ==================
 */
internal class R_MakeAmbientMap_f private constructor() : cmdFunction_t() {
    override fun run(args: CmdArgs.idCmdArgs?) {
        var fullname: String?
        val baseName: String
        var i: Int
        val downSample: Int
        val extensions /*[6]*/: Array<String> = arrayOf(
            "_px.tga", "_nx.tga", "_py.tga", "_ny.tga", "_pz.tga", "_nz.tga"
        )
        val outSize: Int
        val buffers: Array<ByteBuffer?> = arrayOfNulls(6)
        val width: IntArray = intArrayOf(0)
        val height: IntArray = intArrayOf(0)
        if (args!!.Argc() != 2 && args.Argc() != 3) {
            common.Printf("USAGE: ambientshot <basename> [size]\n")
            return
        }
        baseName = args.Argv(1)
        downSample = 0
        if (args.Argc() == 3) {
            outSize = args.Argv(2).toInt()
        } else {
            outSize = 32
        }

        cubeAxis[0].set(0, 0, 1.0f)
        cubeAxis[0].set(1, 2, 1.0f)
        cubeAxis[0].set(2, 1, 1.0f)
        cubeAxis[1].set(0, 0, -1.0f)
        cubeAxis[1].set(1, 2, -1.0f)
        cubeAxis[1].set(2, 1, 1.0f)
        cubeAxis[2].set(0, 1, 1.0f)
        cubeAxis[2].set(1, 0, -1.0f)
        cubeAxis[2].set(2, 2, -1.0f)
        cubeAxis[3].set(0, 1, -1.0f)
        cubeAxis[3].set(1, 0, -1.0f)
        cubeAxis[3].set(2, 2, 1.0f)
        cubeAxis[4].set(0, 2, 1.0f)
        cubeAxis[4].set(1, 0, -1.0f)
        cubeAxis[4].set(2, 1, 1.0f)
        cubeAxis[5].set(0, 2, -1.0f)
        cubeAxis[5].set(1, 0, 1.0f)
        cubeAxis[5].set(2, 1, 1.0f)

        // read all of the images
        i = 0
        while (i < 6) {
            fullname = String.format("env/%s%s", baseName, extensions[i])
            common.Printf("loading %s\n", fullname)
            Session.session.UpdateScreen()
            buffers[i] = Image_files.R_LoadImage(fullname, width, height, null, true)
            if (buffers[i] == null) {
                common.Printf("failed.\n")
                i--
                while (i >= 0) {
                    buffers[i] = null
                    i--
                }
                return
            }
            i++
        }

        // resample with hemispherical blending
        val samples = 1000
        val outBuffer: ByteBuffer = ByteBuffer.allocate(outSize * outSize * 4)
        for (map in 0..1) {
            i = 0
            while (i < 6) {
                for (x in 0 until outSize) {
                    for (y in 0 until outSize) {
                        val dir = idVec3()
                        val total = FloatArray(3)
                        dir.set(
                            cubeAxis[i][0].plus(
                                cubeAxis[i][1].times(-(-1 + 2.0f * x / (outSize - 1)))
                            ).plus(
                                cubeAxis[i][2].times(-(-1 + 2.0f * y / (outSize - 1)))
                            )
                        )
                        dir.Normalize()
                        total[2] = 0.0f
                        total[1] = total[2]
                        total[0] = total[1]
                        //samples = 1;
                        val limit: Float =
                            if ((map).toBoolean()) 0.95f else 0.25f // small for specular, almost hemisphere for ambient
                        for (s in 0 until samples) {
                            // pick a random direction vector that is inside the unit sphere but not behind dir,
                            // which is a robust way to evenly sample a hemisphere
                            val test = idVec3()
                            while (true) {
                                for (j in 0..2) {
                                    test[j] = (-1 + 2 * (Random().nextInt() and 0x7fff) / 0x7fff).toFloat()
                                }
                                if (test.Length() > 1.0f) {
                                    continue
                                }
                                test.Normalize()
                                if (test.times(dir) > limit) {    // don't do a complete hemisphere
                                    break
                                }
                            }
                            val result = ByteArray(4)
                            //test = dir;
                            R_SampleCubeMap(test, width[0], buffers as Array<ByteBuffer>, result)
                            total[0] += result[0].toFloat()
                            total[1] += result[1].toFloat()
                            total[2] += result[2].toFloat()
                        }
                        outBuffer.put((y * outSize + x) * 4 + 0, (total[0] / samples).toInt().toByte())
                        outBuffer.put((y * outSize + x) * 4 + 1, (total[1] / samples).toInt().toByte())
                        outBuffer.put((y * outSize + x) * 4 + 2, (total[2] / samples).toInt().toByte())
                        outBuffer.put((y * outSize + x) * 4 + 3, 255.toByte())
                    }
                }
                if (map == 0) {
                    fullname = String.format("env/%s_amb%s", baseName, extensions[i])
                } else {
                    fullname = String.format("env/%s_spec%s", baseName, extensions[i])
                }
                common.Printf("writing %s\n", fullname)
                Session.session.UpdateScreen()
                R_WriteTGA(fullname, outBuffer, outSize, outSize)
                i++
            }
        }
    }

    companion object {
        private val cubeAxis: Array<idMat3> = Array(6) { idMat3() }
        val instance: cmdFunction_t = R_MakeAmbientMap_f()
    }
}

/*
 ================
 R_Benchmark_f
 ================
 */
internal class R_Benchmark_f private constructor() : cmdFunction_t() {
    override fun run(args: CmdArgs.idCmdArgs?) {
        var fps: Float
        var msec: Float
        val view: renderView_s
        if (tr.primaryView == null) {
            common.Printf("No primaryView for benchmarking\n")
            return
        }
        view = tr.primaryRenderView!!
        var size = 100
        while (size >= 10) {
            r_screenFraction.SetInteger(size)
            fps = R_RenderingFPS(view)
            val kpix: Int =
                (glConfig.vidWidth * glConfig.vidHeight * (size * 0.01) * (size * 0.01) * 0.001).toInt()
            msec = (1000.0f / fps)
            common.Printf("kpix: %4d  msec:%5.1f fps:%5.1f\n", kpix, msec, fps)
            size -= 10
        }

        // enable r_singleTriangle 1 while r_screenFraction is still at 10
        r_singleTriangle.SetBool(true)
        fps = R_RenderingFPS(view)
        msec = 1000.0f / fps
        common.Printf("single tri  msec:%5.1f fps:%5.1f\n", msec, fps)
        r_singleTriangle.SetBool(false)
        r_screenFraction.SetInteger(100)

        // enable r_skipRenderContext 1
        r_skipRenderContext.SetBool(true)
        fps = R_RenderingFPS(view)
        msec = 1000.0f / fps
        common.Printf("no context  msec:%5.1f fps:%5.1f\n", msec, fps)
        r_skipRenderContext.SetBool(false)
    }

    companion object {
        val instance: cmdFunction_t = R_Benchmark_f()
    }
}

/*
 ================
 GfxInfo_f
 ================
 */
internal class GfxInfo_f private constructor() : cmdFunction_t() {
    override fun run(args: CmdArgs.idCmdArgs?) {
        common.Printf("\nGL_VENDOR: %s\n", glConfig.vendor_string!!)
        common.Printf("GL_RENDERER: %s\n", glConfig.renderer_string!!)
        common.Printf("GL_VERSION: %s\n", glConfig.version_string!!)
        common.Printf("GL_EXTENSIONS: %s\n", glConfig.extensions_string!!)
        common.Printf("GL_MAX_TEXTURE_SIZE: %d\n", glConfig.maxTextureSize)
        common.Printf("GL_MAX_TEXTURE_UNITS_ARB: %d\n", glConfig.maxTextureUnits)
        common.Printf("GL_MAX_TEXTURE_COORDS_ARB: %d\n", glConfig.maxTextureCoords)
        common.Printf("GL_MAX_TEXTURE_IMAGE_UNITS_ARB: %d\n", glConfig.maxTextureImageUnits)
        common.Printf(
            "\nPIXELFORMAT: color(%d-bits) Z(%d-bit) stencil(%d-bits)\n",
            glConfig.colorBits,
            glConfig.depthBits,
            glConfig.stencilBits
        )
        common.Printf(
            "MODE: %d, %d x %d %s hz:",
            r_mode.GetInteger(),
            glConfig.vidWidth,
            glConfig.vidHeight,
            fsstrings[(r_fullscreen.GetBool()).toInt()]
        )
        if (glConfig.displayFrequency != 0) {
            common.Printf("%d\n", glConfig.displayFrequency)
        } else {
            common.Printf("N/A\n")
        }
        common.Printf("Logical Window size: %g x %g\n", glConfig.winWidth, glConfig.winHeight)


        common.Printf("CPU: %s\n", Sys_GetProcessorString())
        val active /*[2]*/: Array<String> = arrayOf("", " (ACTIVE)")

        if (glConfig.allowARB2Path) {
            common.Printf(
                "ARB2 path ENABLED%s\n", active[(tr.backEndRenderer == backEndName_t.BE_ARB2).toInt()]
            )
        } else {
            common.Printf("ARB2 path disabled\n")
        }

        //=============================
        common.Printf("-------\n")
        if (r_finish.GetBool()) {
            common.Printf("Forcing glFinish\n")
        } else {
            common.Printf("glFinish not forced\n")
        }
        if (WIN32) {
            if (r_swapInterval.GetInteger() != 0) { //)  && NativeLibrary.isFunctionAvailableGlobal("wglSwapIntervalEXT")) {
                common.Printf("Forcing swapInterval %d\n", r_swapInterval.GetInteger())
            } else {
                common.Printf("swapInterval not forced\n")
            }
        }
        val tss: Boolean = glConfig.twoSidedStencilAvailable
        if (!r_useTwoSidedStencil.GetBool() && tss) {
            common.Printf("Two sided stencil available but disabled\n")
        } else if (!tss) {
            common.Printf("Two sided stencil not available\n")
        } else if (tss) {
            common.Printf("Using two sided stencil\n")
        }
        if (VertexCache.vertexCache.IsFast()) {
            common.Printf("Vertex cache is fast\n")
        } else {
            common.Printf("Vertex cache is SLOW\n")
        }
    }

    companion object {
        private val fsstrings: Array<String> = arrayOf(
            "windowed", "fullscreen"
        )
        val instance: cmdFunction_t = GfxInfo_f()
    }
}

/*
 =============
 R_TestImage_f

 Display the given image centered on the screen.
 testimage <number>
 testimage <filename>
 =============
 */
internal class R_TestImage_f private constructor() : cmdFunction_t() {
    override fun run(args: CmdArgs.idCmdArgs?) {
        val imageNum: Int
        if (tr.testVideo != null) {
            tr.testVideo = null
        }
        tr.testImage = null
        if (args!!.Argc() != 2) {
            return
        }
        if (IsNumeric(args.Argv(1))) {
            imageNum = args.Argv(1).toInt()
            if (imageNum >= 0 && imageNum < Image.globalImages.images.Num()) {
                tr.testImage = Image.globalImages.images[imageNum]
            }
        } else {
            tr.testImage = Image.globalImages.ImageFromFile(
                args.Argv(1), textureFilter_t.TF_DEFAULT, false, textureRepeat_t.TR_REPEAT, textureDepth_t.TD_DEFAULT
            )
        }
    }

    companion object {
        val instance: cmdFunction_t = R_TestImage_f()
    }
}

/*
 =============
 R_TestVideo_f

 Plays the cinematic file in a testImage
 =============
 */
internal class R_TestVideo_f private constructor() : cmdFunction_t() {
    override fun run(args: CmdArgs.idCmdArgs?) {
        if (tr.testVideo != null) {
            tr.testVideo = null
        }
        tr.testImage = null
        if (args!!.Argc() < 2) {
            return
        }
        tr.testImage = Image.globalImages.ImageFromFile(
            "_scratch", textureFilter_t.TF_DEFAULT, false, textureRepeat_t.TR_REPEAT, textureDepth_t.TD_DEFAULT
        )
        tr.testVideo = idCinematic.Alloc()
        tr.testVideo!!.InitFromFile(args.Argv(1), true)
        val cin: cinData_t
        cin = tr.testVideo!!.ImageForTime(0)
        if (cin.image == null) {
            tr.testVideo = null
            tr.testImage = null
            return
        }
        common.Printf("%d x %d images\n", cin.imageWidth, cin.imageHeight)
        val len: Int = tr.testVideo!!.AnimationLength()
        common.Printf("%5.1f seconds of video\n", len * 0.001)
        tr.testVideoStartTime = (tr.primaryRenderView!!.time * 0.001f)

        // try to play the matching wav file
        val wavString = idStr(args.Argv(if ((args.Argc() == 2)) 1 else 2))
        wavString.StripFileExtension()
        wavString.plusAssign(".wav")
        Session.session.sw.PlayShaderDirectly(wavString.toString())
    }

    companion object {
        val instance: cmdFunction_t = R_TestVideo_f()
    }
}

/*
 ===================
 R_ReportSurfaceAreas_f

 Prints a list of the materials sorted by surface area
 ===================
 */
internal class R_ReportSurfaceAreas_f private constructor() : cmdFunction_t() {
    override fun run(args: CmdArgs.idCmdArgs?) {
        var i: Int
        val count: Int
        val list: Array<idMaterial?>
        count = DeclManager.declManager.GetNumDecls(declType_t.DECL_MATERIAL)
        list = arrayOfNulls(count)
        i = 0
        while (i < count) {
            list[i] = DeclManager.declManager.DeclByIndex(declType_t.DECL_MATERIAL, i, false) as idMaterial?
            i++
        }

//            qsort(list, count, sizeof(list[0]), new R_QsortSurfaceAreas());
        Arrays.sort(list, R_QsortSurfaceAreas())

        // skip over ones with 0 area
        i = 0
        while (i < count) {
            if (list[i]!!.GetSurfaceArea() > 0) {
                break
            }
            i++
        }
        while (i < count) {

            // report size in "editor blocks"
            val blocks: Int = (list[i]!!.GetSurfaceArea() / 4096.0f).toInt()
            common.Printf("%7d %s\n", blocks, list[i]!!.GetName())
            i++
        }
    }

    companion object {
        val instance: cmdFunction_t = R_ReportSurfaceAreas_f()
    }
}

/*
 ===================
 R_ReportImageDuplication_f

 Checks for images with the same hash value and does a better comparison
 ===================
 */
internal class R_ReportImageDuplication_f private constructor() : cmdFunction_t() {
    override fun run(args: CmdArgs.idCmdArgs?) {
        var i: Int
        var j: Int
        common.Printf("Images with duplicated contents:\n")
        var count = 0
        i = 0
        while (i < Image.globalImages.images.Num()) {
            val image1: idImage? = Image.globalImages.images[i]
            if (image1!!.isPartialImage) {
                // ignore background loading stubs
                i++
                continue
            }
            if (image1.generatorFunction != null) {
                // ignore procedural images
                i++
                continue
            }
            if (image1.cubeFiles != cubeFiles_t.CF_2D) {
                // ignore cube maps
                i++
                continue
            }
            if (image1.defaulted) {
                i++
                continue
            }
            val w1: IntArray = intArrayOf(0)
            val h1: IntArray = intArrayOf(0)
            val data1: ByteBuffer = R_LoadImageProgram(image1.imgName.toString(), w1, h1, null)!!
            j = 0
            while (j < i) {
                val image2: idImage? = Image.globalImages.images[j]
                if (image2!!.isPartialImage) {
                    j++
                    continue
                }
                if (image2.generatorFunction != null) {
                    j++
                    continue
                }
                if (image2.cubeFiles != cubeFiles_t.CF_2D) {
                    j++
                    continue
                }
                if (image2.defaulted) {
                    j++
                    continue
                }
                if (!(image1.imageHash == image2.imageHash)) {
                    j++
                    continue
                }
                if ((image2.uploadWidth != image1.uploadWidth || image2.uploadHeight != image1.uploadHeight)) {
                    j++
                    continue
                }
                if (Icmp(image1.imgName, image2.imgName) == 0) {
                    // ignore same image-with-different-parms
                    j++
                    continue
                }
                val w2: IntArray = intArrayOf(0)
                val h2: IntArray = intArrayOf(0)
                val data2: ByteBuffer = R_LoadImageProgram(image2.imgName.toString(), w2, h2, null)!!
                if (!w2.contentEquals(w1) || !h2.contentEquals(h1)) {
                    j++
                    continue
                }

                if (data1 != data2) {
                    j++
                    continue
                }

                common.Printf("%s == %s\n", image1.imgName, image2.imgName)
                Session.session.UpdateScreen(true)
                count++
                break
            }
            i++
        }
        common.Printf("%d / %d collisions\n", count, Image.globalImages.images.Num())
    }

    companion object {
        val instance: cmdFunction_t = R_ReportImageDuplication_f()
    }
}

/*
 =================
 R_VidRestart_f
 =================
 */
internal class R_VidRestart_f private constructor() : cmdFunction_t() {
    override fun run(args: CmdArgs.idCmdArgs?) {
        val err: Int

        // if OpenGL isn't started, do nothing
        if (!glConfig.isInitialized) {
            return
        }
        var full = true
        var forceWindow = false
        for (i in 1 until args!!.Argc()) {
            if (Icmp(args.Argv(i), "partial") == 0) {
                full = false
                continue
            }
            if (Icmp(args.Argv(i), "windowed") == 0) {
                forceWindow = true
                continue
            }
        }

        // this could take a while, so give them the cursor back ASAP
        Sys_GrabMouseCursor(false)

        // dump ambient caches
        ModelManager.renderModelManager.FreeModelVertexCaches()

        // free any current world interaction surfaces and vertex caches
        tr_lightrun.R_FreeDerivedData()

        // make sure the defered frees are actually freed
        tr_main.R_ToggleSmpFrame()
        tr_main.R_ToggleSmpFrame()

        // free the vertex caches so they will be regenerated again
        VertexCache.vertexCache.PurgeAll()

        // sound and input are tied to the window we are about to destroy
        if (full) {
            // free all of our texture numbers
            snd_system.soundSystem.ShutdownHW()
            Sys_ShutdownInput()
            Image.globalImages.PurgeAllImages()
            // free the context and close the window
            GLimp_Shutdown()
            glConfig.isInitialized = false

            // create the new context and vertex cache
            val latch: Boolean = cvarSystem.GetCVarBool("r_fullscreen")
            if (forceWindow) {
                cvarSystem.SetCVarBool("r_fullscreen", false)
            }
            R_InitOpenGL()
            cvarSystem.SetCVarBool("r_fullscreen", latch)

            // regenerate all images
            Image.globalImages.ReloadAllImages()
        } else {
            val parms = glimpParms_t()
            parms.width = glConfig.vidWidth
            parms.height = glConfig.vidHeight
            parms.fullScreen = !forceWindow && r_fullscreen.GetBool()
            parms.displayHz = r_displayRefresh.GetInteger()
            parms.multiSamples = r_multiSamples.GetInteger()
            parms.stereo = false
            GLimp_SetScreenParms(parms)
        }

        // make sure the regeneration doesn't use anything no longer valid
        tr.viewCount++
        tr.viewDef = null

        // regenerate all necessary interactions
        tr_lightrun.R_RegenerateWorld_f.instance.run(CmdArgs.idCmdArgs())

        // check for problems
        err = qgl.qglGetError()
        if (err != GL11.GL_NO_ERROR) {
            common.Printf("glGetError() = 0x%x\n", err)
        }

        // start sound playing again
        snd_system.soundSystem.SetMute(false)
    }

    companion object {
        val instance: cmdFunction_t = R_VidRestart_f()
    }
}

/*
 ==============
 R_ListModes_f
 ==============
 */
internal class R_ListModes_f private constructor() : cmdFunction_t() {
    override fun run(args: CmdArgs.idCmdArgs?) {
        var i: Int
        common.Printf("\n")
        i = 0
        while (i < s_numVidModes) {
            common.Printf("%s\n", r_vidModes[i].description)
            i++
        }
        common.Printf("\n")
    }

    companion object {
        val instance: cmdFunction_t = R_ListModes_f()
    }
}

/*
 =====================
 R_ReloadSurface_f

 Reload the material displayed by r_showSurfaceInfo
 =====================
 */
internal class R_ReloadSurface_f private constructor() : cmdFunction_t() {
    override fun run(args: CmdArgs.idCmdArgs?) {
        val mt = modelTrace_s()
        val start = idVec3()
        val end = idVec3()

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
        common.Printf("Reloading %s\n", mt.material!!.GetName())

        // reload the decl
        mt.material!!.base!!.Reload()

        // reload any images used by the decl
        mt.material!!.ReloadImages(false)
    }

    companion object {
        val instance: cmdFunction_t = R_ReloadSurface_f()
    }
}

/*
 ==============================================================================

 SCREEN SHOTS

 ==============================================================================
 */
internal class R_QsortSurfaceAreas : cmp_t<idMaterial?> {
    override fun compare(a: idMaterial?, b: idMaterial?): Int {
        val ac: Float
        val bc: Float
        if (!a!!.EverReferenced()) {
            ac = 0.0f
        } else {
            ac = a.GetSurfaceArea()
        }
        if (!b!!.EverReferenced()) {
            bc = 0.0f
        } else {
            bc = b.GetSurfaceArea()
        }
        if (ac < bc) {
            return -1
        }
        if (ac > bc) {
            return 1
        }
        return Icmp(a.GetName(), b.GetName())
    }
}

package neo.Renderer

import neo.Renderer.Cinematic.idCinematic
import neo.Renderer.GuiModel.idGuiModel
import neo.Renderer.Image.idImage
import neo.Renderer.Image.textureDepth_t
import neo.Renderer.Image.textureType_t
import neo.Renderer.Interaction.idInteraction
import neo.Renderer.Material.idMaterial
import neo.Renderer.Material.stageVertexColor_t
import neo.Renderer.Material.textureFilter_t
import neo.Renderer.Material.textureRepeat_t
import neo.Renderer.Model.dominantTri_s
import neo.Renderer.Model.idRenderModel
import neo.Renderer.Model.silEdge_t
import neo.Renderer.Model.srfTriangles_s
import neo.Renderer.ModelDecal.idRenderModelDecal
import neo.Renderer.ModelOverlay.idRenderModelOverlay
import neo.Renderer.RenderSystem.fontInfoEx_t
import neo.Renderer.RenderSystem.fontInfo_t
import neo.Renderer.RenderSystem.glconfig_s
import neo.Renderer.RenderSystem.glyphInfo_t
import neo.Renderer.RenderSystem.idRenderSystem
import neo.Renderer.RenderWorld.idRenderWorld
import neo.Renderer.RenderWorld.renderEntity_s
import neo.Renderer.RenderWorld.renderLight_s
import neo.Renderer.RenderWorld.renderView_s
import neo.Renderer.RenderWorld_local.doublePortal_s
import neo.Renderer.RenderWorld_local.idRenderWorldLocal
import neo.Renderer.RenderWorld_local.portalArea_s
import neo.Renderer.VertexCache.vertCache_s
import neo.TempDump.btoi
import neo.TempDump.ctos
import neo.framework.Common
import neo.framework.Common.MemInfo_t
import neo.framework.DeclManager
import neo.framework.DemoFile.demoSystem_t
import neo.framework.EventLoop
import neo.framework.FileSystem_h.fileSystem
import neo.framework.Session
import neo.idlib.BV.Frustum.idFrustum
import neo.idlib.BV.bounds_zero
import neo.idlib.BV.idBounds
import neo.idlib.Text.Str.C_COLOR_DEFAULT
import neo.idlib.Text.Str.idStr.Companion.ColorForIndex
import neo.idlib.Text.Str.idStr.Companion.Copynz
import neo.idlib.Text.Str.idStr.Companion.Icmp
import neo.idlib.Text.Str.idStr.Companion.IsColor
import neo.idlib.Text.Str.idStr.Companion.snPrintf
import neo.idlib.colorWhite
import neo.idlib.containers.List.idList
import neo.idlib.geometry.DrawVert.idDrawVert
import neo.idlib.geometry.Winding.idWinding
import neo.idlib.math.*
import neo.idlib.math.idMath.Ftoi
import neo.idlib.math.idMath.FtoiFast
import neo.sys.win_glimp.GLimp_Shutdown
import neo.sys.win_input.Sys_ShutdownInput
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL41
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.util.*
import kotlin.math.floor

// everything that is needed by the backend needs
// to be double buffered to allow it to run in
// parallel on a dual cpu machine
val GLS_ALPHAMASK: Int = 0x00001000
val GLS_ATEST_BITS: Int = 0x70000000

//
val GLS_ATEST_EQ_255: Int = 0x10000000
val GLS_ATEST_GE_128: Int = 0x40000000
val GLS_ATEST_LT_128: Int = 0x20000000

// picky to get the bilerp correct at terminator
val GLS_BLUEMASK: Int = 0x00000800

//
val GLS_DEPTHFUNC_ALWAYS: Int = 0x00010000

//
val GLS_DEFAULT: Int = GLS_DEPTHFUNC_ALWAYS
val GLS_DEPTHFUNC_EQUAL: Int = 0x00020000
val GLS_DEPTHFUNC_LESS: Int = 0x0

//
//
// these masks are the inverse, meaning when set the glColorMask value will be 0,
// preventing that channel from being written
val GLS_DEPTHMASK: Int = 0x00000100
val GLS_DSTBLEND_BITS: Int = 0x000000f0
val GLS_DSTBLEND_DST_ALPHA: Int = 0x00000070
val GLS_DSTBLEND_ONE: Int = 0x00000020
val GLS_DSTBLEND_ONE_MINUS_DST_ALPHA: Int = 0x00000080
val GLS_DSTBLEND_ONE_MINUS_SRC_ALPHA: Int = 0x00000060
val GLS_DSTBLEND_ONE_MINUS_SRC_COLOR: Int = 0x00000040
val GLS_DSTBLEND_SRC_ALPHA: Int = 0x00000050
val GLS_DSTBLEND_SRC_COLOR: Int = 0x00000030

//
val GLS_DSTBLEND_ZERO: Int = 0x0
val GLS_GREENMASK: Int = 0x00000400

//
val GLS_POLYMODE_LINE: Int = 0x00002000
val GLS_REDMASK: Int = 0x00000200
val GLS_COLORMASK: Int = (GLS_REDMASK or GLS_GREENMASK or GLS_BLUEMASK)
val GLS_SRCBLEND_ALPHA_SATURATE: Int = 0x00000009
val GLS_SRCBLEND_BITS: Int = 0x0000000f
val GLS_SRCBLEND_DST_ALPHA: Int = 0x00000007
val GLS_SRCBLEND_DST_COLOR: Int = 0x00000003
val GLS_SRCBLEND_ONE: Int = 0x0

//=======================================================================
val GLS_SRCBLEND_ONE_MINUS_DST_ALPHA: Int = 0x00000008
val GLS_SRCBLEND_ONE_MINUS_DST_COLOR: Int = 0x00000004
val GLS_SRCBLEND_ONE_MINUS_SRC_ALPHA: Int = 0x00000006
val GLS_SRCBLEND_SRC_ALPHA: Int = 0x00000005

/*
 ====================================================================

 GL wrapper/helper functions

 ====================================================================
 */
val GLS_SRCBLEND_ZERO: Int = 0x00000001

/*
 ============================================================

 TRISURF

 ============================================================
 */
val USE_TRI_DATA_ALLOCATOR: Boolean = true

//
val DEFAULT_FOG_DISTANCE: Float = 500.0f

/*
 ==============================================================================

 SURFACES

 ==============================================================================
 */
//
//
//
// drawSurf_t structures command the back end to render surfaces
// a given srfTriangles_t may be used with multiple viewEntity_t,
// as when viewed in a subview or multiple viewport render, or
// with multiple shaders when skinned, or, possibly with multiple
// lights, although currently each lighting interaction creates
// unique srfTriangles_t
// drawSurf_t are always allocated and freed every frame, they are never cached
val DSF_VIEW_INSIDE_SHADOW: Int = 1

//
val FALLOFF_TEXTURE_SIZE: Int = 64

//
val FOG_ENTER_SIZE: Int = 64
val FOG_ENTER: Float = (FOG_ENTER_SIZE + 1.0f) / (FOG_ENTER_SIZE * 2)

//=======================================================================
// this is the inital allocation for max number of drawsurfs
// in a given view, but it will automatically grow if needed
val INITIAL_DRAWSURFS: Int = 0x4000
val MAX_CLIP_PLANES: Int = 1 // we may expand this to six for some subview issues
val MAX_GUI_SURFACES: Int =
    1024 // default size of the drawSurfs list for guis, will be automatically expanded as needed
val MAX_MULTITEXTURE_UNITS: Int = 8
val MAX_RENDER_CROPS: Int = 8
val SMP_FRAMES: Int = 1
var backEnd: backEndState_t? = null
var frameData: frameData_t? = null
var glConfig: glconfig_s = glconfig_s() // outside of TR since it shouldn't be cleared during ref re-init
var tr: idRenderSystemLocal = idRenderSystemLocal()

enum class backEndName_t {
    BE_ARB2,
    BE_BAD
}

internal enum class demoCommand_t {
    DC_BAD,
    DC_RENDERVIEW,
    DC_UPDATE_ENTITYDEF,
    DC_DELETE_ENTITYDEF,
    DC_UPDATE_LIGHTDEF,
    DC_DELETE_LIGHTDEF,
    DC_LOADMAP,
    DC_CROP_RENDER,
    DC_UNCROP_RENDER,
    DC_CAPTURE_RENDER,
    DC_END_FRAME,
    DC_DEFINE_MODEL,
    DC_SET_PORTAL_STATE,
    DC_UPDATE_SOUNDOCCLUSION,
    DC_GUI_MODEL
}

/*

 All vertex programs use the same constant register layout:

 c[4]	localLightOrigin
 c[5]	localViewOrigin
 c[6]	lightProjection S
 c[7]	lightProjection T
 c[8]	lightProjection Q
 c[9]	lightFalloff	S
 c[10]	bumpMatrix S
 c[11]	bumpMatrix T
 c[12]	diffuseMatrix S
 c[13]	diffuseMatrix T
 c[14]	specularMatrix S
 c[15]	specularMatrix T


 c[20]	light falloff tq constant

 // texture 0 was cube map
 // texture 1 will be the per-surface bump map
 // texture 2 will be the light falloff texture
 // texture 3 will be the light projection texture
 // texture 4 is the per-surface diffuse map
 // texture 5 is the per-surface specular map
 // texture 6 is the specular half angle cube map

 */
enum class programParameter_t {
    _0_,
    _1_,
    _2_,
    _3_,

    //fillers
    PP_LIGHT_ORIGIN,
    PP_VIEW_ORIGIN,
    PP_LIGHT_PROJECT_S,
    PP_LIGHT_PROJECT_T,
    PP_LIGHT_PROJECT_Q,
    PP_LIGHT_FALLOFF_S,
    PP_BUMP_MATRIX_S,
    PP_BUMP_MATRIX_T,
    PP_DIFFUSE_MATRIX_S,
    PP_DIFFUSE_MATRIX_T,
    PP_SPECULAR_MATRIX_S,
    PP_SPECULAR_MATRIX_T,
    PP_COLOR_MODULATE,
    PP_COLOR_ADD,// 17
    _18_,
    _19_,
    PP_LIGHT_FALLOFF_TQ, // = 20,	// only for NV programs - DG: unused
    PP_GAMMA_BRIGHTNESS, // = 21, // DG: for gamma in shader: { r_brightness, r_brightness, r_brightness, 1/r_gamma }

    // DG: for soft particles from TDM: reciprocal of _currentDepth size.
    //     Lets us convert a screen position to a texcoord in _currentDepth
    PP_CURDEPTH_RECIPR,//  = 22,

    // DG: for soft particles from TDM: particle radius, given as { radius, 1/(fadeRange), 1/radius }
    //     fadeRange is the particle diameter for alpha blends (like smoke), but the particle radius for additive
    //     blends (light glares), because additive effects work differently. Fog is half as apparent when a wall
    //     is in the middle of it. Light glares lose no visibility when they have something to reflect off.
    PP_PARTICLE_RADIUS, //  = 23,

    // DG: for soft particles from TDM: color channel mask.
    //     Particles with additive blend need their RGB channels modifying to blend them out
    //     Particles with an alpha blend need their alpha channel modifying.
    PP_PARTICLE_COLCHAN_MASK // = 24,
}

//    public static void R_Init();
/*
 ============================================================

 DRAW_*

 ============================================================
 */
enum class program_t {
    PROG_INVALID,
    VPROG_INTERACTION,
    VPROG_ENVIRONMENT,
    VPROG_BUMPY_ENVIRONMENT,
    VPROG_STENCIL_SHADOW,
    VPROG_TEST,
    FPROG_INTERACTION,
    FPROG_ENVIRONMENT,
    FPROG_BUMPY_ENVIRONMENT,
    FPROG_TEST,
    VPROG_AMBIENT,
    FPROG_AMBIENT,
    VPROG_GLASSWARP,
    FPROG_GLASSWARP,
    PROG_USER
}

/*
 =============================================================

 RENDERER BACK END COMMAND QUEUE

 TR_CMDS

 =============================================================
 */
enum class renderCommand_t {
    RC_NOP,
    RC_DRAW_VIEW,
    RC_SET_BUFFER,
    RC_COPY_RENDER,
    RC_SWAP_BUFFERS // can't just assume swap at end of list because  of forced list submission before syncs
}

// idScreenRect gets carried around with each drawSurf, so it makes sense
// to keep it compact, instead of just using the idBounds class
class idScreenRect {
    private val DBG_count: Int = DBG_counter++
    var x1: Int = 0
    var y1: Int = 0
    var x2: Int = 0
    var y2: Int = 0 // inclusive pixel bounds inside viewport
    var zmin: Float = 0.0f
    var zmax: Float = 0.0f // for depth bounds test

    //
    //
    constructor()

    //copy constructor
    constructor(other: idScreenRect) {
        x1 = other.x1
        y1 = other.y1
        x2 = other.x2
        y2 = other.y2
        zmin = other.zmin
        zmax = other.zmax
    }

    // clear to backwards values
    fun Clear() {
        y1 = 32000
        x1 = y1
        y2 = -32000
        x2 = y2
        zmin = 0.0f
        zmax = 1.0f
    }

    fun AddPoint(x: Float, y: Float) {            // adds a point
        val ix: Short = FtoiFast(x).toShort()
        val iy: Short = FtoiFast(y).toShort()
        if (ix < x1) {
            x1 = ix.toInt()
        }
        if (ix > x2) {
            x2 = ix.toInt()
        }
        if (iy < y1) {
            y1 = iy.toInt()
        }
        if (iy > y2) {
            y2 = iy.toInt()
        }
    }

    fun Expand() {                                // expand by one pixel each way to fix roundoffs
        x1--
        y1--
        x2++
        y2++
    }

    fun Intersect(rect: idScreenRect) {
        if (rect.x1 > x1) {
            x1 = rect.x1
        }
        if (rect.x2 < x2) {
            x2 = rect.x2
        }
        if (rect.y1 > y1) {
            y1 = rect.y1
        }
        if (rect.y2 < y2) {
            y2 = rect.y2
        }
    }

    fun Union(rect: idScreenRect) {
        if (rect.x1 < x1) {
            x1 = rect.x1
        }
        if (rect.x2 > x2) {
            x2 = rect.x2
        }
        if (rect.y1 < y1) {
            y1 = rect.y1
        }
        if (rect.y2 > y2) {
            y2 = rect.y2
        }
    }

    override fun hashCode(): Int {
        var hash = 3
        hash = 47 * hash + x1
        hash = 47 * hash + y1
        hash = 47 * hash + x2
        hash = 47 * hash + y2
        return hash
    }

    override fun equals(obj: Any?): Boolean {
        if (obj == null) {
            return false
        }
        if (javaClass != obj.javaClass) {
            return false
        }
        val other: idScreenRect = obj as idScreenRect
        if (x1 != other.x1) {
            return false
        }
        if (y1 != other.y1) {
            return false
        }
        if (x2 != other.x2) {
            return false
        }
        return y2 == other.y2
    }

    @Deprecated("")
    fun Equals(rect: idScreenRect): Boolean {
        return ((x1 == rect.x1) && (x2 == rect.x2) && (y1 == rect.y1) && (y2 == rect.y2))
    }

    fun IsEmpty(): Boolean {
        return (x1 > x2 || y1 > y2)
    }

    override fun toString(): String {
        return "idScreenRect{" + "x1=" + x1 + ", y1=" + y1 + ", x2=" + x2 + ", y2=" + y2 + '}'
    }

    companion object {
        private var DBG_counter: Int = 0
        fun generateArray(length: Int): Array<idScreenRect> {
            return Array(length) { idScreenRect() }
        }
    }
}

class drawSurf_s {
    private val DBG_count: Int = DBG_counter++
    var dsFlags: Int = 0 // DSF_VIEW_INSIDE_SHADOW, etc
    var dynamicTexCoords: vertCache_s? = null // float * in vertex cache memory
    var geo: srfTriangles_s? = null
    var material: idMaterial? = null // may be NULL for shadow volumes
    var nextOnLight: drawSurf_s? = null // viewLight chains
    var scissorRect: idScreenRect? = null // for scissor clipping, local inside renderView viewport
    var shaderRegisters // evaluated and adjusted for referenceShaders
            : FloatArray? = null

    // specular directions for non vertex program cards, skybox texcoords, etc
    var sort: Float = 0.0f // material->sort, modified by gui / entity sort offsets
    var space: viewEntity_s? = null

    companion object {
        private var DBG_counter: Int = 0
        fun generateArray(length: Int): Array<drawSurf_s> {
            return Array(length) { drawSurf_s() }
        }
    }
}

class shadowFrustum_t {
    val planes: Array<idPlane> = idPlane.generateArray(6)

    // positive sides facing inward
    // plane 5 is always the plane the projection is going to, the
    // other planes are just clip planes
    // all planes are in global coordinates
    //
    var makeClippedPlanes: Boolean = false
    var numPlanes: Int = 0 // this is always 6 for now
    // a projected light with a single frustum needs to make sil planes
    // from triangles that clip against side planes, but a point light
    // that has adjacent frustums doesn't need to
}

// areas have references to hold all the lights and entities in them
class areaReference_s {
    var area: portalArea_s? = null // so owners can find all the areas they are in
    var areaNext: areaReference_s? = null // chain in the area
    var areaPrev: areaReference_s? = null
    var entity: idRenderEntityLocal? = null // only one of entity / light will be non-NULL
    var light: idRenderLightLocal? = null // only one of entity / light will be non-NULL
    var ownerNext: areaReference_s? = null // chain on either the entityDef or lightDef
}

// idRenderLight should become the new public interface replacing the qhandle_t to light defs in the idRenderWorld interface
abstract class idRenderLight {
    // virtual					~idRenderLight() {}
    abstract fun FreeRenderLight()
    abstract fun UpdateRenderLight(re: renderLight_s?, forceUpdate: Boolean /* = false*/)
    fun UpdateRenderLight(re: renderLight_s?) {
        UpdateRenderLight(re, false)
    }

    abstract fun GetRenderLight(re: renderLight_s?)
    abstract fun ForceUpdate()
    abstract fun GetIndex(): Int
}

// idRenderEntity should become the new public interface replacing the qhandle_t to entity defs in the idRenderWorld interface
abstract class idRenderEntity {
    // virtual					~idRenderEntity() {}
    abstract fun FreeRenderEntity()
    abstract fun UpdateRenderEntity(re: renderEntity_s?, forceUpdate: Boolean /*= false*/)
    fun UpdateRenderEntity(re: renderEntity_s?) {
        UpdateRenderEntity(re, false)
    }

    abstract fun GetRenderEntity(re: renderEntity_s?)
    abstract fun ForceUpdate()
    abstract fun GetIndex(): Int

    // overlays are extra polygons that deform with animating models for blood and damage marks
    abstract fun ProjectOverlay(localTextureAxis: Array<idPlane?>? /*[2]*/, material: idMaterial?)
    abstract fun RemoveDecals()
}

class idRenderLightLocal : idRenderLight() {
    //
    //
    val frustum: Array<idPlane> =
        idPlane.generateArray(6) // in global space, positive side facing out, last two are front/back

    //
    val globalLightOrigin // accounting for lightCenter and parallel
            : idVec3

    //
    //
    // derived information
    val lightProject: Array<idPlane> = idPlane.generateArray(4)

    //                                                      // and should go in the dynamic frame memory, or kept
    //                                                      // in the cached memory
    var archived // for demo writing
            : Boolean

    //
    var areaNum // if not -1, we may be able to cull all the light's
            : Int
    var falloffImage: idImage?
    var firstInteraction // doubly linked list
            : idInteraction?

    //
    var foggedPortals: doublePortal_s?
    var frustumTris // triangulated frustumWindings[]
            : srfTriangles_s?
    var frustumWindings: Array<idWinding?> = arrayOfNulls(6) // used for culling
    var index // in world lightdefs
            : Int
    var lastInteraction: idInteraction?

    //                                                      // interactions if !viewDef->connectedAreas[areaNum]
    //
    var lastModifiedFrameNum // to determine if it is constantly changing,
            : Int

    //
    var lightHasMoved // the light has changed its position since it was
            : Boolean

    //
    var lightShader // guaranteed to be valid, even if parms.shader isn't
            : idMaterial?

    //                                                      // first added, so the prelight model is not valid
    //
    var modelMatrix: FloatArray = FloatArray(16) // this is just a rearrangement of parms.axis and parms.origin

    //
    var numShadowFrustums // one for projected lights, usually six for point lights
            : Int
    var parms // specification
            : renderLight_s

    //
    var references // each area the light is present in will have a lightRef
            : areaReference_s?
    var shadowFrustums: Array<shadowFrustum_t?> = arrayOfNulls(6)

    //
    var viewCount // if == tr.viewCount, the light is on the viewDef->viewLights list
            : Int
    var viewLight: viewLight_s?

    //
    var world: idRenderWorldLocal?

    //
    //
    init {
        parms = renderLight_s() //memset( & parms, 0, sizeof(parms));
        //            memset(modelMatrix, 0, sizeof(modelMatrix));
//            memset(shadowFrustums, 0, sizeof(shadowFrustums));
        for (s in shadowFrustums.indices) {
            shadowFrustums[s] = shadowFrustum_t()
        }
        //            memset(lightProject, 0, sizeof(lightProject));
        for (l in lightProject.indices) {
            lightProject[l] = idPlane()
        }
        //            memset(frustum, 0, sizeof(frustum));
        for (f in frustum.indices) {
            frustum[f] = idPlane()
        }
        //            memset(frustumWindings, 0, sizeof(frustumWindings));
        for (f in frustumWindings.indices) {
            frustumWindings[f] = idWinding()
        }
        lightHasMoved = false
        world = null
        index = 0
        areaNum = 0
        lastModifiedFrameNum = 0
        archived = false
        lightShader = null
        falloffImage = null
        globalLightOrigin = getVec3_zero()
        frustumTris = null
        numShadowFrustums = 0
        viewCount = 0
        viewLight = null
        references = null
        foggedPortals = null
        firstInteraction = null
        lastInteraction = null
    }

    override fun FreeRenderLight() {}
    override fun UpdateRenderLight(re: renderLight_s?, forceUpdate: Boolean) {}
    override fun GetRenderLight(re: renderLight_s?) {}
    override fun ForceUpdate() {}
    override fun GetIndex(): Int {
        return index
    }
}

class idRenderEntityLocal : idRenderEntity() {
    private val DBG_count: Int = DBG_counter++

    // and should go in the dynamic frame memory, or kept
    // in the cached memory
    var archived // for demo writing
            : Boolean

    // dynamicModel if this doesn't == tr.viewCount
    var cachedDynamicModel: idRenderModel?

    // if tr.viewCount == visibleCount, at least one ambient
    // surface has actually been added by R_AddAmbientDrawsurfs
    // note that an entity could still be in the view frustum and not be visible due
    // to portal passing
    //
    var decals // chain of decals that have been projected on this model
            : idRenderModelDecal?

    //
    var dynamicModel // if parms.model->IsDynamicModel(), this is the generated data
            : idRenderModel?
    var dynamicModelFrameCount // continuously animating dynamic models will recreate
            : Int

    //
    var entityRefs // chain of all references
            : areaReference_s?
    var firstInteraction // doubly linked list
            : idInteraction?
    var index // in world entityDefs
            : Int
    var lastInteraction: idInteraction?

    //
    var lastModifiedFrameNum // to determine if it is constantly changing,
            : Int

    //
    var modelMatrix: FloatArray = FloatArray(16) // this is just a rearrangement of parms.axis and parms.origin
    var overlay // blood overlays on animated models
            : idRenderModelOverlay? = null
    var parms: renderEntity_s

    //
    val referenceBounds // the local bounds used to place entityRefs, either from parms or a model
            : idBounds = idBounds()

    //
    // a viewEntity_t is created whenever a idRenderEntityLocal is considered for inclusion
    // in a given view, even if it turns out to not be visible
    var viewCount // if tr.viewCount == viewCount, viewEntity is valid,
            : Int

    // but the entity may still be off screen
    var viewEntity // in frame temporary memory
            : viewEntity_s?

    //
    var visibleCount: Int

    //
    var world: idRenderWorldLocal?
    var needsPortalSky: Boolean

    init {
        parms = renderEntity_s() //memset( parms, 0, sizeof( parms ) );
        //	memset( modelMatrix, 0, sizeof( modelMatrix ) );
        world = null
        index = 0
        lastModifiedFrameNum = 0
        archived = false
        dynamicModel = null
        dynamicModelFrameCount = 0
        cachedDynamicModel = null
        referenceBounds.set(bounds_zero)//TODO:replace bounds_zero with something useful?
        viewCount = 0
        viewEntity = null
        visibleCount = 0
        decals = null
        overlay = null
        entityRefs = null
        firstInteraction = null
        lastInteraction = null
        needsPortalSky = false
    }

    override fun FreeRenderEntity() {
        throw UnsupportedOperationException("Not supported yet.")
    }

    override fun UpdateRenderEntity(re: renderEntity_s?, forceUpdate: Boolean) {
        throw UnsupportedOperationException("Not supported yet.")
    }

    override fun GetRenderEntity(re: renderEntity_s?) {
        throw UnsupportedOperationException("Not supported yet.")
    }

    override fun ForceUpdate() {
        throw UnsupportedOperationException("Not supported yet.")
    }

    override fun GetIndex(): Int {
        return index
    }

    // overlays are extra polygons that deform with animating models for blood and damage marks
    override fun ProjectOverlay(localTextureAxis: Array<idPlane?>?, material: idMaterial?) {
        throw UnsupportedOperationException("Not supported yet.")
    }

    override fun RemoveDecals() {
        throw UnsupportedOperationException("Not supported yet.")
    }

    companion object {
        //
        //
        private var DBG_counter: Int = 0
    }
}

// viewLights are allocated on the frame temporary stack memory
// a viewLight contains everything that the back end needs out of an idRenderLightLocal,
// which the front end may be modifying simultaniously if running in SMP mode.
// a viewLight may exist even without any surfaces, and may be relevent for fogging,
// but should never exist if its volume does not intersect the view frustum
class viewLight_s {
    val globalInteractions: Array<drawSurf_s?> = arrayOf(null) // get shadows from everything

    //
    val globalLightOrigin: idVec3 = idVec3() // global light origin used by backend

    //
    val globalShadows: Array<drawSurf_s?> = arrayOf(null) // shadow everything
    val lightProject: Array<idPlane> = idPlane.generateArray(4) // light project used by backend
    val localInteractions: Array<drawSurf_s?> = arrayOf(null) // don't get local shadows
    val localShadows: Array<drawSurf_s?> = arrayOf(null) // don't shadow local Surfaces
    val translucentInteractions: Array<drawSurf_s?> = arrayOf(null) // get shadows from everything
    var falloffImage: idImage? = null // falloff image used by backend
    var fogPlane: idPlane? = null // fog plane for backend fog volume rendering
    var frustumTris: srfTriangles_s? = null // light frustum for backend fog volume rendering

    //
    // back end should NOT reference the lightDef, because it can change when running SMP
    var lightDef: idRenderLightLocal? = null
    var lightShader: idMaterial? = null // light shader used by backend
    var next: viewLight_s? = null

    //
    // for scissor clipping, local inside renderView viewport
    // scissorRect.Empty() is true if the viewEntity_t was never actually
    // seen through any portals
    var scissorRect: idScreenRect? = null
    var shaderRegisters // shader registers used by backend
            : FloatArray? = null

    //
    // if the view isn't inside the light, we can use the non-reversed
    // shadow drawing, avoiding the draws of the front and rear caps
    var viewInsideLight: Boolean = false

    //
    // true if globalLightOrigin is inside the view frustum, even if it may
    // be obscured by geometry.  This allows us to skip shadows from non-visible objects
    var viewSeesGlobalLightOrigin: Boolean = false

    //
    // if !viewInsideLight, the corresponding bit for each of the shadowFrustum
    // projection planes that the view is on the negative side of will be set,
    // allowing us to skip drawing the projected caps of shadows if we can't see the face
    var viewSeesShadowPlaneBits: Int = 0
}

/**
 * a viewEntity is created whenever a idRenderEntityLocal is considered for
 * inclusion in the current view, but it may still turn out to be culled.
 * viewEntity are allocated on the frame temporary stack memory a viewEntity
 * contains everything that the back end needs out of a idRenderEntityLocal,
 * which the front end may be modifying simultaneously if running in SMP
 * mode. A single entityDef can generate multiple [viewEntity_s] in a
 * single frame, as when seen in a mirror
 */
class viewEntity_s {
    private val DBG_COUNT: Int = DBG_COUNTER++

    //
    // back end should NOT reference the entityDef, because it can change when running SMP
    var entityDef: idRenderEntityLocal? = null
    var modelDepthHack: Float = 0.0f

    //
    var modelMatrix: FloatArray = FloatArray(16) // local coords to global coords
    var modelViewMatrix: FloatArray = FloatArray(16) // local coords to eye coords
    var next: viewEntity_s? = null

    //
    // for scissor clipping, local inside renderView viewport
    // scissorRect.Empty() is true if the viewEntity_t was never actually
    // seen through any portals, but was created for shadow casting.
    // a viewEntity can have a non-empty scissorRect, meaning that an area
    // that it is in is visible, and still not be visible.
    var scissorRect: idScreenRect = idScreenRect()

    //
    var weaponDepthHack: Boolean = false

    constructor()
    constructor(v: viewEntity_s) {
        next = v.next
        entityDef = v.entityDef
        scissorRect = idScreenRect(v.scissorRect)
        weaponDepthHack = v.weaponDepthHack
        modelDepthHack = v.modelDepthHack
        System.arraycopy(v.modelMatrix, 0, modelMatrix, 0, 16)
        System.arraycopy(v.modelViewMatrix, 0, modelViewMatrix, 0, 16)
    }

    fun memSetZero() {
        next = viewEntity_s()
        entityDef = idRenderEntityLocal()
        scissorRect = idScreenRect()
        weaponDepthHack = false
        modelDepthHack = 0.0f
    }

    companion object {
        private var DBG_COUNTER: Int = 0
    }
}

// viewDefs are allocated on the frame temporary stack memory
class viewDef_s {
    // specified in the call to DrawScene()
    val clipPlanes // in world space, the positive side
            : Array<idPlane?>
    val frustum: Array<idPlane>

    //
    val initialViewAreaOrigin: idVec3 = idVec3()

    //
    var areaNum: Int = 0 // -1 = not in a valid area

    //
    var connectedAreas: BooleanArray? = null

    //
    // drawSurfs are the visible surfaces of the viewEntities, sorted
    // by the material sort parameter
    var drawSurfs: Array<drawSurf_s> = drawSurf_s.generateArray(0) // we don't use an idList for this, because

    //
    var floatTime: Float = 0.0f

    //
    var isEditor: Boolean = false
    var isMirror: Boolean = false // the portal is a mirror, invert the face culling

    // Used to find the portalArea that view flooding will take place from.
    // for a normal view, the initialViewOrigin will be renderView.viewOrg,
    // but a mirror may put the projection origin outside
    // of any valid area, or in an unconnected area of the map, so the view
    // area must be based on a point just off the surface of the mirror / subview.
    // It may be possible to get a failed portal pass if the plane of the
    // mirror intersects a portal, and the initialViewAreaOrigin is on
    // a different side than the renderView.viewOrg is.
    //
    var isSubview: Boolean = false // true if this view is not the main view
    var isXraySubview: Boolean = false
    var maxDrawSurfs: Int = 0 // may be resized

    //
    var numClipPlanes: Int = 0 // mirrors will often use a single clip plane
    var numDrawSurfs: Int = 0 // it is allocated in frame temporary memory
    var numViewEntitys: Int = 0

    //
    var projectionMatrix: FloatArray = FloatArray(16)
    var renderView: renderView_s

    //
    var renderWorld: idRenderWorldLocal? = null

    //
    var scissor: idScreenRect
    var subviewSurface: drawSurf_s? = null

    // for scissor clipping, local inside renderView viewport
    // subviews may only be rendering part of the main view
    // these are real physical pixel values, possibly scaled and offset from the
    // renderView x/y/width/height
    //
    var superView: viewDef_s? = null // never go into an infinite subview loop
    var viewEntitys: viewEntity_s? =
        null // chain of all viewEntities effecting view, including off screen ones casting shadows
    var viewFrustum: idFrustum = idFrustum()

    //
    var viewLights: viewLight_s? = null // chain of all viewLights effecting view

    // of the plane is the visible side
    var viewport // in real pixels and proper Y flip
            : idScreenRect
    var worldSpace: viewEntity_s

    // An array in frame temporary memory that lists if an area can be reached without
    // crossing a closed door.  This is used to avoid drawing interactions
    // when the light is behind a closed door.
    constructor() {
        renderView = renderView_s()
        worldSpace = viewEntity_s()
        clipPlanes = arrayOfNulls(MAX_CLIP_PLANES)
        viewport = idScreenRect()
        scissor = idScreenRect()
        viewFrustum = idFrustum()
        frustum = idPlane.generateArray(5)
    }

    constructor(v: viewDef_s) {
        renderView = renderView_s(v.renderView)
        System.arraycopy(v.projectionMatrix, 0, projectionMatrix, 0, 16)
        worldSpace = viewEntity_s(v.worldSpace)
        renderWorld = v.renderWorld
        floatTime = v.floatTime
        initialViewAreaOrigin.set(v.initialViewAreaOrigin)
        isSubview = v.isSubview
        isMirror = v.isMirror
        isXraySubview = v.isXraySubview
        isEditor = v.isEditor
        numClipPlanes = v.numClipPlanes
        clipPlanes = arrayOfNulls(MAX_CLIP_PLANES)
        for (i in 0 until MAX_CLIP_PLANES) {
            if (v.clipPlanes[i] != null) {
                clipPlanes[i] = idPlane()
                clipPlanes[i]!!.set(v.clipPlanes[i]!!)
            }
        }
        viewport = idScreenRect(v.viewport)
        scissor = idScreenRect(v.scissor)
        superView = v.superView
        subviewSurface = v.subviewSurface
        drawSurfs = v.drawSurfs
        numDrawSurfs = v.numDrawSurfs
        maxDrawSurfs = v.maxDrawSurfs
        viewLights = v.viewLights
        viewEntitys = v.viewEntitys
        frustum = v.frustum
        viewFrustum = idFrustum(v.viewFrustum)
        areaNum = v.areaNum
        if (v.connectedAreas != null) {
            connectedAreas = BooleanArray(v.connectedAreas!!.size)
            System.arraycopy(v.connectedAreas, 0, connectedAreas, 0, v.connectedAreas!!.size)
        }
    }
}

// complex light / surface interactions are broken up into multiple passes of a
// simple interaction shader
class drawInteraction_t {
    //
    val diffuseColor: idVec4 =
        idVec4() // may have a light color baked into it, will be < tr.backEndRendererMaxLight

    //                                                              // (not a bool just to avoid an uninitialized memory check of the pad region by valgrind)
    //
    // these are loaded into the vertex program
    val localLightOrigin: idVec4 = idVec4()
    val localViewOrigin: idVec4 = idVec4()
    val specularColor: idVec4 =
        idVec4() // may have a light color baked into it, will be < tr.backEndRendererMaxLight

    //
    var ambientLight: Int = 0 // use tr.ambientNormalMap instead of normalization cube map
    var bumpImage: idImage? = null
    var bumpMatrix: Array<idVec4> = idVec4.generateArray(2)
    var diffuseImage: idImage? = null
    var diffuseMatrix: Array<idVec4> = idVec4.generateArray(2)
    var lightFalloffImage: idImage? = null

    //
    var lightImage: idImage? = null
    var lightProjection: Array<idVec4?> =
        arrayOfNulls(4) // in local coordinates, possibly with a texture matrix baked in
    var specularImage: idImage? = null
    var specularMatrix: Array<idVec4> = idVec4.generateArray(2)
    var surf: drawSurf_s? = null
    var vertexColor: stageVertexColor_t = stageVertexColor_t.SVC_IGNORE // applies to both diffuse and specular
    fun set(d: drawInteraction_t?) {
        throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
    }
}

open class emptyCommand_t {
    var commandId: renderCommand_t? = null
    var next: emptyCommand_t? = null
    fun set(c: emptyCommand_t) {
        commandId = c.commandId
        next = c.next
    }

    fun set(next: renderCommand_t?) {
        throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
    }
}

internal class setBufferCommand_t : emptyCommand_t() {
    //        renderCommand_t commandId, next;
    var  /*GLenum*/buffer: Int = 0
    var frameCount: Int = 0
}

class drawSurfsCommand_t : emptyCommand_t() {
    //        renderCommand_t commandId, next;
    var viewDef: viewDef_s? = null
}

internal class copyRenderCommand_t : emptyCommand_t() {
    var cubeFace: Int = 0 // when copying to a cubeMap
    var image: idImage? = null

    //        renderCommand_t commandId, next;
    var x: Int = 0
    var y: Int = 0
    var imageWidth: Int = 0
    var imageHeight: Int = 0
}

// a request for frame memory will never fail
// (until malloc fails), but it may force the
// allocation of a new memory block that will
// be discontinuous with the existing memory
class frameMemoryBlock_s {
    var base: ByteArray = ByteArray(4) // dynamically allocated as [size]
    var next: frameMemoryBlock_s? = null
    var poop: Int = 0 // so that base is 16 byte aligned
    var size: Int = 0
    var used: Int = 0
}

// all of the information needed by the back end must be
// contained in a frameData_t.  This entire structure is
// duplicated so the front and back end can run in parallel
// on an SMP machine (OBSOLETE: this capability has been removed)
class frameData_t {
    // one or more blocks of memory for all frame
    // temporary allocations
    //
    // alloc will point somewhere into the memory chain
    var alloc: frameMemoryBlock_s? = null

    //
    // the currently building command list
    // commands can be inserted at the front if needed, as for required
    // dynamically generated textures
    var cmdHead: emptyCommand_t? = null
    var cmdTail: emptyCommand_t? = null // may be of other command type based on commandId

    //
    var firstDeferredFreeTriSurf: srfTriangles_s? = null
    var lastDeferredFreeTriSurf: srfTriangles_s? = null
    var memory: frameMemoryBlock_s? = null

    //
    var memoryHighwater: Int = 0 // max used on any frame
}

/*
 ** performanceCounters_t
 */
class performanceCounters_t {
    var c_alloc: Int = 0
    var c_free: Int = 0 // counts for R_StaticAllc/R_StaticFree
    var c_box_cull_in: Int = 0
    var c_box_cull_out: Int = 0
    var c_createInteractions: Int = 0 // number of calls to idInteraction::CreateInteraction
    var c_createLightTris: Int = 0
    var c_createShadowVolumes: Int = 0
    var c_deformedIndexes: Int = 0 // idMD5Mesh::GenerateSurface
    var c_deformedSurfaces: Int = 0 // idMD5Mesh::GenerateSurface
    var c_deformedVerts: Int = 0 // idMD5Mesh::GenerateSurface
    var c_entityDefCallbacks: Int = 0
    var c_entityUpdates: Int = 0
    var c_lightUpdates: Int = 0
    var c_entityReferences: Int = 0
    var c_lightReferences: Int = 0
    var c_generateMd5: Int = 0
    var c_guiSurfs: Int = 0
    var c_numViews: Int = 0 // number of total views rendered
    var c_shadowViewEntities: Int = 0
    var c_sphere_cull_in: Int = 0
    var c_sphere_cull_clip: Int = 0
    var c_sphere_cull_out: Int = 0
    var c_tangentIndexes: Int = 0 // R_DeriveTangents()
    var c_viewLights: Int = 0
    var c_visibleViewEntities: Int = 0
    var frontEndMsec = 0L // sum of time in all RE_RenderScene's in a frame
}

class tmu_t {
    var current2DMap: Int = 0
    var current3DMap: Int = 0
    var currentCubeMap: Int = 0
    var texEnv: Int = 0
    var textureType: textureType_t? = null
}

class glstate_t {
    var currenttmu: Int = 0

    //
    var faceCulling: Int = 0
    var forceGlState: Boolean = false // the next GL_State will ignore glStateBits and set everything
    var glStateBits: Int = 0
    var tmu: Array<tmu_t?>

    init {
        tmu = arrayOfNulls(MAX_MULTITEXTURE_UNITS)
        for (a in tmu.indices) {
            tmu[a] = tmu_t()
        }
    }
}

class backEndCounters_t {
    //
    var c_drawElements: Int = 0
    var c_drawIndexes: Int = 0
    var c_drawRefIndexes: Int = 0
    var c_drawRefVertexes: Int = 0
    var c_drawVertexes: Int = 0
    var c_indexes: Int = 0 // one set per pass
    var c_overDraw: Float = 0.0f
    var c_shaders: Int = 0

    //
    var c_shadowElements: Int = 0
    var c_shadowIndexes: Int = 0
    var c_shadowVertexes: Int = 0
    var c_surfaces: Int = 0
    var c_totalIndexes: Int = 0 // counting all passes

    //
    var c_vboIndexes: Int = 0
    var c_vertexes: Int = 0

    //
    var maxLightValue: Float = 0.0f // for light scale
    var msec: Int = 0 // total msec for backend run
}

//
// all state modified by the back end is separated
// from the front end state
class backEndState_t {
    //
    var c_copyFrameBuffer: Int = 0

    // with post processing to get the desired total light level.
    // A high dynamic range card will have this set to 1.0f.
    //
    var currentRenderCopied: Boolean = false // true if any material has already referenced _currentRender
    var currentScissor: idScreenRect? = null

    //
    var currentSpace: viewEntity_s? = null // for detecting when a matrix must change
    var depthFunc: Int = 0 // GLS_DEPTHFUNC_EQUAL, or GLS_DEPTHFUNC_LESS for translucent
    var frameCount: Int = 0 // used to track all images used in a frame

    //
    // our OpenGL state deltas
    var glState: glstate_t
    var lightColor: FloatArray = FloatArray(4) // evaluation of current light's color stage

    //
    var lightScale: Float = 0.0f // Every light color calaculation will be multiplied by this,
    var lightTextureMatrix: FloatArray = FloatArray(16) // only if lightStage->texture.hasMatrix

    // which will guarantee that the result is < tr.backEndRendererMaxLight
    // A card with high dynamic range will have this set to 1.0f
    var overBright: Float = 0.0f // The amount that all light interactions must be multiplied by
    var pc: backEndCounters_t

    // for scissor clipping, local inside renderView viewport
    //
    var vLight: viewLight_s? = null
    var viewDef: viewDef_s? = null

    init {
        pc = backEndCounters_t()
        glState = glstate_t()
    }
}

class renderCrop_t {
    var x: Int = 0
    var y: Int = 0
    var width: Int = 0
    var height: Int = 0 // these are in physical, OpenGL Y-at-bottom pixels
}

/*
 ** Most renderer globals are defined here.
 ** backend functions should never modify any of these fields,
 ** but may read fields that aren't dynamically modified
 ** by the frontend.
 */
class idRenderSystemLocal : idRenderSystem() {
    private var origUseScissor: Boolean = true

    val worlds: idList<idRenderWorldLocal>
    var DBG_viewCount: Int = 0 // incremented every view (twice a scene if subviewed)

    var ambientCubeImage: idImage? = null // hack for testing dependent ambient lighting

    // determines how much overbrighting needs
    // to be done post-process
    val ambientLightVector: idVec4 = idVec4() // used for "ambient bump mapping"

    // determines which back end to use, and if vertex programs are in use
    var backEndRenderer: backEndName_t? = null
    var backEndRendererHasVertexPrograms: Boolean = false
    var backEndRendererMaxLight: Float = 0.0f // 1.0f for standard, unlimited for floats
    var currentRenderCrop: Int = 0

    // many console commands need to know which world they should operate on
    //
    var defaultMaterial: idMaterial? = null
    var demoGuiModel: idGuiModel? = null

    var frameCount: Int = 0 // incremented every frame
    var frameShaderTime: Float = 0.0f // shader time for all non-world 2D rendering

    @Deprecated("")
    var gammaTable: ShortArray = ShortArray(256) // brightness / gamma modify this
    var guiModel: idGuiModel? = null

    //
    // GUI drawing variables for surface creation
    var guiRecursionLevel: Int = 0 // to prevent infinite overruns

    //
    var identitySpace: viewEntity_s? = null // can use if we don't know viewDef->worldSpace is valid

    //
    var lockSurfacesCmd: drawSurfsCommand_t? = null // use this when r_lockSurfaces = 1
    var lockSurfacesViewDef: viewDef_s? = null
    var lockSurfacesRealViewDef: viewDef_s? = null
    var  /*FILE*/logFile: FileChannel? = null // for logging GL calls and frame breaks

    //
    var pc: performanceCounters_t? = null // performance counters
    var primaryRenderView: renderView_s? = null
    var primaryView: viewDef_s? = null

    //
    var primaryWorld: idRenderWorldLocal? = null

    // renderer globals
    var registered: Boolean = false // cleared at shutdown, set at InitOpenGL

    //
    var renderCrops // = new renderCrop_t[MAX_RENDER_CROPS];
            : Array<renderCrop_t?> = arrayOfNulls(MAX_RENDER_CROPS)

    //
    var sortOffset: Float = 0.0f // for determinist sorting of equal sort materials

    // and every R_MarkFragments call
    //
    var staticAllocCount: Int = 0 // running total of bytes allocated

    //
    var stencilIncr: Int = 0
    var stencilDecr: Int = 0 // GL_INCR / INCR_WRAP_EXT, GL_DECR / GL_DECR_EXT

    //
    var takingScreenshot: Boolean = false
    var testImage: idImage? = null
    var testVideo: idCinematic? = null
    var testVideoStartTime: Float = 0.0f
    var tiledViewport: IntArray = IntArray(2)
    var viewCount: Int = 0 // incremented every view (twice a scene if subviewed)

    //
    var viewDef: viewDef_s? = null

    // DG: remember the original glConfig.vidWidth/Height values that get overwritten in BeginFrame()
    //     so they can be reset in EndFrame() (Editors tend to mess up the viewport by using BeginFrame())
    var origWidth: Int = 0
    var origHeight: Int = 0

    // DG: taken from the current map's worldspawn ("allow_nospecular")
    //     true if (unlike in Vanilla Doom3) the "nospecular" parm of a light should be respected
    var allowNoSpecular: Boolean = false

    //
    var viewportOffset: IntArray = IntArray(2) // for doing larger-than-window tiled renderings

    // ~idRenderSystemLocal( void );
    // external functions
    // virtual void			Init( void );
    // virtual void			Shutdown( void );
    // virtual void			InitOpenGL( void );
    // virtual void			ShutdownOpenGL( void );
    // virtual bool			IsOpenGLRunning( void ) const;
    // virtual bool			IsFullScreen( void ) const;
    // virtual int				GetScreenWidth( void ) const;
    // virtual int				GetScreenHeight( void ) const;
    // virtual idRenderWorld *	AllocRenderWorld( void );
    // virtual void			FreeRenderWorld( idRenderWorld *rw );
    // virtual void			BeginLevelLoad( void );
    // virtual void			EndLevelLoad( void );
    // virtual bool			RegisterFont( const char *fontName, fontInfoEx_t &font );
    // virtual void			SetColor( const idVec4 &rgba );
    // virtual void			SetColor4( float r, float g, float b, float a );
    // virtual void			DrawStretchPic ( const idDrawVert *verts, const glIndex_t *indexes, int vertCount, int indexCount, const idMaterial *material,
    // bool clip = true, float x = 0.0f.0f, float y = 0.0f.0f, float w = 640.0f.0f, float h = 0.0f.0f );
    // virtual void			DrawStretchPic ( float x, float y, float w, float h, float s1, float t1, float s2, float t2, const idMaterial *material );
    // virtual void			DrawStretchTri ( idVec2 p1, idVec2 p2, idVec2 p3, idVec2 t1, idVec2 t2, idVec2 t3, const idMaterial *material );
    // virtual void			GlobalToNormalizedDeviceCoordinates( const idVec3 &global, idVec3 &ndc );
    // virtual void			GetGLSettings( int& width, int& height );
    // virtual void			PrintMemInfo( MemInfo_t *mi );
    // virtual void			DrawSmallChar( int x, int y, int ch, const idMaterial *material );
    // virtual void			DrawSmallStringExt( int x, int y, const char *string, const idVec4 &setColor, bool forceColor, const idMaterial *material );
    // virtual void			DrawBigChar( int x, int y, int ch, const idMaterial *material );
    // virtual void			DrawBigStringExt( int x, int y, const char *string, const idVec4 &setColor, bool forceColor, const idMaterial *material );
    // virtual void			WriteDemoPics();
    // virtual void			DrawDemoPics();
    // virtual void			BeginFrame( int windowWidth, int windowHeight );
    // virtual void			EndFrame( int *frontEndMsec, int *backEndMsec );
    // virtual void			TakeScreenshot( int width, int height, const char *fileName, int downSample, renderView_t *ref );
    // virtual void			CropRenderSize( int width, int height, bool makePowerOfTwo = false, bool forceDimensions = false );
    // virtual void			CaptureRenderToImage( const char *imageName );
    // virtual void			CaptureRenderToFile( const char *fileName, bool fixAlpha );
    // virtual void			UnCrop();
    // virtual void			GetCardCaps( bool &oldCard, bool &nv10or20 );
    // virtual bool			UploadImage( const char *imageName, const byte *data, int width, int height );
    // internal functions
    init {
        worlds = idList()
        Clear()
    }

    fun Clear() {
        registered = false
        frameCount = 0
        viewCount = 0
        staticAllocCount = 0
        frameShaderTime = 0.0f
        viewportOffset[0] = 0
        viewportOffset[1] = 0
        tiledViewport[0] = 0
        tiledViewport[1] = 0
        backEndRenderer = backEndName_t.BE_BAD
        backEndRendererHasVertexPrograms = false
        backEndRendererMaxLight = 1.0f
        ambientLightVector.Zero()
        sortOffset = 0.0f
        worlds.Clear()
        primaryWorld = null
        primaryRenderView = renderView_s()
        primaryView = null
        defaultMaterial = null
        testImage = null
        ambientCubeImage = null
        viewDef = null
        //            memset(pc, 0, sizeof(pc));
        pc = performanceCounters_t()
        //            memset(lockSurfacesCmd, 0, sizeof(lockSurfacesCmd));
        lockSurfacesCmd = drawSurfsCommand_t()
        //            memset(identitySpace, 0, sizeof(identitySpace));
        identitySpace = viewEntity_s()
        stencilIncr = 0
        stencilDecr = 0
        //            memset(renderCrops, 0, sizeof(renderCrops));
        renderCrops = arrayOfNulls(MAX_RENDER_CROPS)
        for (r in renderCrops.indices) {
            renderCrops[r] = renderCrop_t()
        }
        currentRenderCrop = 0
        guiRecursionLevel = 0
        guiModel = null
        demoGuiModel = null
        //            memset(gammaTable, 0, sizeof(gammaTable));
        takingScreenshot = false
    }

    /*
     ==================
     SetBackEndRenderer

     Check for changes in the back end renderSystem, possibly invalidating cached data
     ==================
     */
    fun SetBackEndRenderer() {            // sets tr.backEndRenderer based on cvars
        if (!r_renderer!!.IsModified()) {
            return
        }
        val oldVPstate: Boolean = backEndRendererHasVertexPrograms
        backEndRenderer = backEndName_t.BE_BAD

        if (Icmp((r_renderer!!.GetString())!!, "arb2") == 0) {
            if (glConfig.allowARB2Path) {
                backEndRenderer = backEndName_t.BE_ARB2
            }
        }

        // fallback
        if (backEndRenderer == backEndName_t.BE_BAD) {
            // choose the best
            if (glConfig.allowARB2Path) {
                backEndRenderer = backEndName_t.BE_ARB2
            }
        }
        backEndRendererHasVertexPrograms = false
        backEndRendererMaxLight = 1.0f
        when (backEndRenderer) {
            backEndName_t.BE_ARB2 -> {
                Common.common.Printf("using ARB2 renderSystem\n")
                backEndRendererHasVertexPrograms = true
                backEndRendererMaxLight = 999.0f
            }

            else -> Common.common.FatalError("SetbackEndRenderer: bad back end")
        }

        // clear the vertex cache if we are changing between
        // using vertex programs and not, because specular and
        // shadows will be different data
        if (oldVPstate != backEndRendererHasVertexPrograms) {
            VertexCache.vertexCache.PurgeAll()
            if (primaryWorld != null) {
                primaryWorld!!.FreeInteractions()
            }
        }
        r_renderer!!.ClearModified()
    }

    /*
     =====================
     RenderViewToViewport

     Converts from SCREEN_WIDTH / SCREEN_HEIGHT coordinates to current cropped pixel coordinates
     =====================
     */
    fun RenderViewToViewport(renderView: renderView_s, viewport: idScreenRect) {
        val rc: renderCrop_t? = renderCrops[currentRenderCrop]
        val wRatio: Float = rc!!.width.toFloat() / RenderSystem.SCREEN_WIDTH
        val hRatio: Float = rc.height.toFloat() / RenderSystem.SCREEN_HEIGHT
        viewport.x1 = Ftoi(rc.x + renderView.x * wRatio)
        viewport.x2 = Ftoi(
            rc.x + floor(((renderView.x + renderView.width) * wRatio + 0.5f)) - 1
        )
        viewport.y1 = Ftoi(
            (rc.y + rc.height) - floor(((renderView.y + renderView.height) * hRatio + 0.5f))
        )
        viewport.y2 = Ftoi(
            (rc.y + rc.height) - (floor((renderView.y * hRatio + 0.5f))) - 1
        )
    }

    override fun Init() {
        Common.common.Printf("------- Initializing renderSystem --------\n")

        // clear all our internal state
        viewCount = 1 // so cleared structures never match viewCount
        // we used to memset tr, but now that it is a class, we can't, so
        // there may be other state we need to reset
        ambientLightVector[0] = 0.5f
        ambientLightVector[1] = 0.5f - 0.385f
        ambientLightVector[2] = 0.8925f
        ambientLightVector[3] = 1.0f

        backEnd = backEndState_t()

        R_InitCvars()

        R_InitCommands()

        guiModel = idGuiModel()
        guiModel!!.Clear()
        demoGuiModel = idGuiModel()
        demoGuiModel!!.Clear()
        R_InitTriSurfData()
        Image.globalImages.Init()
        idCinematic.InitCinematic()

        // build brightness translation tables
        R_InitMaterials()
        ModelManager.renderModelManager.Init()

        // set the identity space
        identitySpace!!.modelMatrix[0 * 4 + 0] = 1.0f
        identitySpace!!.modelMatrix[1 * 4 + 1] = 1.0f
        identitySpace!!.modelMatrix[2 * 4 + 2] = 1.0f

        // DG: for resetting width/height in EndFrame()
        origWidth = 0
        origHeight = 0
        Common.common.Printf("------------------renderSystem initialized--------------------\n")
    }

    override fun Shutdown() {
        Common.common.Printf("idRenderSystem::Shutdown()\n")

        Common.common.SetRefreshOnPrint(false)// without a renderer there's nothing to refresh


        tr_font.R_DoneFreeType()

        if (glConfig.isInitialized) {
            Image.globalImages.PurgeAllImages()
        }

        ModelManager.renderModelManager.Shutdown()

        idCinematic.ShutdownCinematic()

        Image.globalImages.Shutdown()

        // free frame memory
        tr_main.R_ShutdownFrameData()

        // free the vertex cache, which should have nothing allocated now
        VertexCache.vertexCache.Shutdown()
        R_ShutdownTriSurfData()
        tr_rendertools.RB_ShutdownDebugTools()

        guiModel = null
        demoGuiModel = null

        Clear()

        ShutdownOpenGL()
    }

    override fun InitOpenGL() {
        // if OpenGL isn't started, start it now
        if (!glConfig.isInitialized) {
            var err: Int

            R_InitOpenGL()

            Image.globalImages.ReloadAllImages()

            err = qgl.qglGetError()
            if (err != GL41.GL_NO_ERROR) {
                Common.common.Printf("glGetError() = 0x%x\n", err)
            }
        }
    }

    override fun ShutdownOpenGL() {
        // free the context and close the window
        tr_main.R_ShutdownFrameData()

        // as the input is tied to the window, it should be shut down when the window
        // is destroyed (relevant when starting a mod which also recreates window)
        Sys_ShutdownInput()

        GLimp_Shutdown()
        glConfig.isInitialized = false
    }

    override fun IsOpenGLRunning(): Boolean {
        return glConfig.isInitialized
    }

    override fun IsFullScreen(): Boolean {
        return glConfig.isFullscreen
    }

    override fun GetScreenWidth(): Int {
        return glConfig.vidWidth
    }

    override fun GetScreenHeight(): Int {
        return glConfig.vidHeight
    }

    override fun AllocRenderWorld(): idRenderWorld {
        val rw: idRenderWorldLocal
        rw = idRenderWorldLocal()
        worlds.Append(rw)
        return rw
    }

    override fun FreeRenderWorld(rw: idRenderWorld) {
        if (primaryWorld === rw) {
            primaryWorld = null
        }
        worlds.Remove(rw as idRenderWorldLocal)
        //	delete rw;
    }

    override fun BeginLevelLoad() {
        ModelManager.renderModelManager.BeginLevelLoad()
        Image.globalImages.BeginLevelLoad()
    }

    override fun EndLevelLoad() {
        ModelManager.renderModelManager.EndLevelLoad()
        Image.globalImages.EndLevelLoad()
        if (r_forceLoadImages!!.GetBool()) {
            tr_backend.RB_ShowImages()
        }
    }

    /*
     ============
     RegisterFont

     Loads 3 point sizes, 12, 24, and 48
     ============
     */
    override fun RegisterFont(fontName: String?, font: fontInfoEx_t): Boolean {
//if( BUILD_FREETYPE){
//            FT_Face face;
//            int j, k, xOut, yOut, lastStart, imageNumber;
//            int scaledSize, newSize, maxHeight, left, satLevels;
//            char[] out, imageBuff;
//            glyphInfo_t glyph;
//            idImage image;
//            idMaterial h;
//            float max;
//}
        val faceData: Array<ByteBuffer?> = arrayOf(null)
        val fTime: LongArray = longArrayOf(0)
        var i: Int
        var len: Int
        var fontCount: Int
        //	char name[1024];
        val name = StringBuffer(1024)
        var pointSize = 12
        /*
         if ( registeredFontCount >= MAX_FONTS ) {
         common.Warning( "RegisterFont: Too many fonts registered already." );
         return false;
         }

         int pointSize = 12;
         idStr::snPrintf( name, sizeof(name), "%s/fontImage_%d.dat", fontName, pointSize );
         for ( i = 0; i < registeredFontCount; i++ ) {
         if ( idStr::Icmp(name, registeredFont[i].fontInfoSmall.name) == 0 ) {
         memcpy( &font, &registeredFont[i], sizeof( fontInfoEx_t ) );
         return true;
         }
         }
         */

//            memset(font, 0, sizeof(font));
        font.clear()
        fontCount = 0
        while (fontCount < 3) {
            if (fontCount == 0) {
                pointSize = 12
            } else if (fontCount == 1) {
                pointSize = 24
            } else {
                pointSize = 48
            }
            // we also need to adjust the scale based on point size relative to 48 points as the ui scaling is based on a 48 point font
            var glyphScale =
                1.0f // change the scale to be relative to 1 based on 72 dpi ( so dpi of 144 means a scale of .5 )
            glyphScale *= 48.0f / pointSize
            snPrintf(name, name.capacity(), "%s/fontImage_%d.dat", (fontName)!!, pointSize)
            val outFont: fontInfo_t
            if (0 == fontCount) {
                font.fontInfoSmall = fontInfo_t()
                outFont = font.fontInfoSmall
            } else if (1 == fontCount) {
                font.fontInfoMedium = fontInfo_t()
                outFont = font.fontInfoMedium
            } else {
                font.fontInfoLarge = fontInfo_t()
                outFont = font.fontInfoLarge
            }
            Copynz(outFont.name, name.toString())
            len = fileSystem.ReadFile(name.toString(), null, fTime)
            if (len != fontInfo_t.BYTES) {
                Common.common.Warning("RegisterFont: couldn't find font: '%s'", name)
                return false
            }
            fileSystem.ReadFile(name.toString(), faceData, fTime)
            tr_font.fdOffset = 0
            tr_font.fdFile = faceData[0]!!.array()
            i = 0
            while (i < RenderSystem.GLYPHS_PER_FONT) {
                outFont.glyphs[i] = glyphInfo_t()
                outFont.glyphs[i]!!.height = tr_font.readInt()
                outFont.glyphs[i]!!.top = tr_font.readInt()
                outFont.glyphs[i]!!.bottom = tr_font.readInt()
                outFont.glyphs[i]!!.pitch = tr_font.readInt()
                outFont.glyphs[i]!!.xSkip = tr_font.readInt()
                outFont.glyphs[i]!!.imageWidth = tr_font.readInt()
                outFont.glyphs[i]!!.imageHeight = tr_font.readInt()
                outFont.glyphs[i]!!.s = tr_font.readFloat()
                outFont.glyphs[i]!!.t = tr_font.readFloat()
                outFont.glyphs[i]!!.s2 = tr_font.readFloat()
                outFont.glyphs[i]!!.t2 = tr_font.readFloat()
                /* font.glyphs[i].glyph */tr_font.readInt()
                //FIXME: the +6, -6 skips the embedded fonts/
//                    memcpy(outFont.glyphs[i].shaderName, fdFile[fdOffset + 6], 32 - 6);
                outFont.glyphs[i]!!.shaderName =
                    String(Arrays.copyOfRange(tr_font.fdFile, tr_font.fdOffset + 6, tr_font.fdOffset + 32))
                tr_font.fdOffset += 32
                i++
            }
            outFont.glyphScale = tr_font.readFloat()
            var mw = 0
            var mh = 0
            i = RenderSystem.GLYPH_START
            while (i < RenderSystem.GLYPH_END) {
                snPrintf(name, name.capacity(), "%s/%s", (fontName), outFont.glyphs[i]!!.shaderName!!)
                outFont.glyphs[i]!!.glyph = DeclManager.declManager.FindMaterial(name.toString())
                outFont.glyphs[i]!!.glyph!!.SetSort(Material.SS_GUI.toFloat())
                if (mh < outFont.glyphs[i]!!.height) {
                    mh = outFont.glyphs[i]!!.height
                }
                if (mw < outFont.glyphs[i]!!.xSkip) {
                    mw = outFont.glyphs[i]!!.xSkip
                }
                i++
            }
            if (fontCount == 0) {
                font.maxWidthSmall = mw
                font.maxHeightSmall = mh
            } else if (fontCount == 1) {
                font.maxWidthMedium = mw
                font.maxHeightMedium = mh
            } else {
                font.maxWidthLarge = mw
                font.maxHeightLarge = mh
            }
            fileSystem.FreeFile(faceData)
            fontCount++
        }

        //memcpy( &registeredFont[registeredFontCount++], &font, sizeof( fontInfoEx_t ) );
//            return true;
//
        if (tr_font.BUILD_FREETYPE) {
            Common.common.Warning("RegisterFont: couldn't load FreeType code %s", name)
            //            } else {
//
//                if (ftLibrary == null) {
//                    common.Warning("RegisterFont: FreeType not initialized.");
//                    return;
//                }
//
//                len = fileSystem.ReadFile(fontName, faceData, ftime);
//                if (len <= 0) {
//                    common.Warning("RegisterFont: Unable to read font file");
//                    return;
//                }
//
//                // allocate on the stack first in case we fail
//                if (FT_New_Memory_Face(ftLibrary, faceData, len, 0, face)) {
//                    common.Warning("RegisterFont: FreeType2, unable to allocate new face.");
//                    return;
//                }
//
//                if (FT_Set_Char_Size(face, pointSize << 6, pointSize << 6, dpi, dpi)) {
//                    common.Warning("RegisterFont: FreeType2, Unable to set face char size.");
//                    return;
//                }
//
//                // font = registeredFonts[registeredFontCount++];
//                // make a 256x256 image buffer, once it is full, register it, clean it and keep going
//                // until all glyphs are rendered
//                out = new char[1024 * 1024];// Mem_Alloc(1024 * 1024);
//                if (out == null) {//TODO:remove
//                    common.Warning("RegisterFont: Mem_Alloc failure during output image creation.");
//                    return;
//                }
////                memset(out, 0, 1024 * 1024);
//                out = new char[1024 * 1024];
//
//                maxHeight = 0;
//
//                for (i = GLYPH_START; i < GLYPH_END; i++) {
//                    glyph = RE_ConstructGlyphInfo(out, xOut, yOut, maxHeight, face, i, qtrue);
//                }
//
//                xOut = 0;
//                yOut = 0;
//                i = GLYPH_START;
//                lastStart = i;
//                imageNumber = 0;
//
//                while (i <= GLYPH_END) {
//
//                    glyph = RE_ConstructGlyphInfo(out, xOut, yOut, maxHeight, face, i, qfalse);
//
//                    if (xOut == -1 || yOut == -1 || i == GLYPH_END) {
//                        // ran out of room
//                        // we need to create an image from the bitmap, set all the handles in the glyphs to this point
//                        //
//
//                        scaledSize = 256 * 256;
//                        newSize = scaledSize * 4;
//                        imageBuff = new char[newSize];// Mem_Alloc(newSize);
//                        left = 0;
//                        max = 0;
//                        satLevels = 255;
//                        for (k = 0; k < (scaledSize); k++) {
//                            if (max < out[k]) {
//                                max = out[k];
//                            }
//                        }
//
//                        if (max > 0) {
//                            max = 255 / max;
//                        }
//
//                        for (k = 0; k < (scaledSize); k++) {
//                            imageBuff[left++] = 255;
//                            imageBuff[left++] = 255;
//                            imageBuff[left++] = 255;
//                            imageBuff[left++] = (char) ((float) out[k] * max);
//                        }
//
//                        idStr.snprintf(name[0], sizeof(name[0]), "fonts/fontImage_%i_%i.tga", imageNumber++, pointSize);
//                        if (r_saveFontData.integer) {
//                            R_WriteTGA(name[0], imageBuff, 256, 256);
//                        }
//
//                        //idStr::snprintf( name, sizeof(name), "fonts/fontImage_%i_%i", imageNumber++, pointSize );
//                        image = R_CreateImage(name[0], imageBuff, 256, 256, qfalse, qfalse, GL_CLAMP);
//                        h = RE_RegisterShaderFromImage(name[0], LIGHTMAP_2D, image, qfalse);
//                        for (j = lastStart; j < i; j++) {
//                            font.glyphs[j].glyph = h;
//                            idStr.Copynz(font.glyphs[j].shaderName, name[0], sizeof(font.glyphs[j].shaderName));
//                        }
//                        lastStart = i;
////                        memset(out, 0, 1024 * 1024);
//                        out = new char[1024 * 1024];
//                        xOut = 0;
//                        yOut = 0;
//                        imageBuff = null;
//                        i++;
//                    } else {
//                        memcpy(font.glyphs[i], glyph, sizeof(glyphInfo_t));
//                        i++;
//                    }
//                }
//
//                registeredFont[registeredFontCount].glyphScale = glyphScale;
//                font.glyphScale = glyphScale;
//                memcpy(registeredFont[registeredFontCount++], font, sizeof(fontInfo_t));
//
//                if (r_saveFontData.integer) {
//                    fileSystem.WriteFile(va("fonts/fontImage_%i.dat", pointSize), font, sizeof(fontInfo_t));
//                }
//
//                out = null;
//
//                fileSystem.FreeFile(faceData);
        }
        return true
    }

    /*
     =============
     SetColor

     This can be used to pass general information to the current material, not
     just colors
     =============
     */
    override fun SetColor(rgba: idVec4) {
        SetColor4(rgba[0], rgba[1], rgba[2], rgba[3])
    }

    override fun SetColor4(r: Float, g: Float, b: Float, a: Float) {
        guiModel!!.SetColor(r, g, b, a)
    }

    override fun DrawStretchPic(
        verts: Array<idDrawVert>?,
        indexes: IntArray?,
        vertCount: Int,
        indexCount: Int,
        material: idMaterial?,
        clip: Boolean,
        min_x: Float,
        min_y: Float,
        max_x: Float,
        max_y: Float
    ) {
        guiModel!!.DrawStretchPic(verts, indexes, vertCount, indexCount, material, clip, min_x, min_y, max_x, max_y)
    }

    /*
     =============
     DrawStretchPic

     x/y/w/h are in the 0,0 to 640,480 range
     =============
     */
    override fun DrawStretchPic(
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        s1: Float,
        t1: Float,
        s2: Float,
        t2: Float,
        material: idMaterial?
    ) {
        guiModel!!.DrawStretchPic(x, y, w, h, s1, t1, s2, t2, material)
    }

    /*
     =============
     DrawStretchTri

     x/y/w/h are in the 0,0 to 640,480 range
     =============
     */
    override fun DrawStretchTri(
        p1: idVec2,
        p2: idVec2,
        p3: idVec2,
        t1: idVec2,
        t2: idVec2,
        t3: idVec2,
        material: idMaterial?
    ) {
        tr.guiModel!!.DrawStretchTri(p1, p2, p3, t1, t2, t3, material)
    }

    override fun GlobalToNormalizedDeviceCoordinates(global: idVec3?, ndc: idVec3?) {
        tr_main.R_GlobalToNormalizedDeviceCoordinates(global!!, ndc!!)
    }

    override fun GetGLSettings(width: IntArray, height: IntArray) {
        width[0] = glConfig.vidWidth
        height[0] = glConfig.vidHeight
    }

    override fun PrintMemInfo(mi: MemInfo_t) {
        // sum up image totals
        Image.globalImages.PrintMemInfo(mi)

        // sum up model totals
        ModelManager.renderModelManager.PrintMemInfo(mi)

        // compute render totals
    }

    /*
     =====================
     idRenderSystemLocal::DrawSmallChar

     small chars are drawn at native screen resolution
     =====================
     */
    override fun DrawSmallChar(x: Int, y: Int, ch: Int, material: idMaterial?) {
        var ch: Int = ch
        val row: Int
        val col: Int
        val fRow: Float
        val fCol: Float
        val size: Float
        ch = ch and 255
        if (ch == ' '.code) {
            return
        }
        if (y < -RenderSystem.SMALLCHAR_HEIGHT) {
            return
        }
        row = ch shr 4
        col = ch and 15
        fRow = row * 0.0625f
        fCol = col * 0.0625f
        size = 0.0625f
        DrawStretchPic(
            x.toFloat(),
            y.toFloat(),
            RenderSystem.SMALLCHAR_WIDTH.toFloat(),
            RenderSystem.SMALLCHAR_HEIGHT.toFloat(),
            fCol,
            fRow,
            fCol + size,
            fRow + size,
            material
        )
    }

    /*
     ==================
     idRenderSystemLocal::DrawSmallString[Color]

     Draws a multi-colored string with a drop shadow, optionally forcing
     to a fixed color.

     Coordinates are at 640 by 480 virtual resolution
     ==================
     */
    override fun DrawSmallStringExt(
        x: Int,
        y: Int,
        string: CharArray,
        setColor: idVec4,
        forceColor: Boolean,
        material: idMaterial?
    ) {
        val color = idVec4()
        var s: Int
        var xx: Int

        // draw the colored text
        s = 0 //(const unsigned char*)string;
        xx = x
        SetColor(setColor)
        while (s < string.size && string[s] != '\u0000') {
            if (IsColor(ctos(string).substring(s))) {
                if (!forceColor) {
                    if (string[s + 1].code == C_COLOR_DEFAULT) {
                        SetColor(setColor)
                    } else {
                        color.set(ColorForIndex(string[s + 1].code))
                        color[3] = setColor[3]
                        SetColor(color)
                    }
                }
                s += 2
                continue
            }
            DrawSmallChar(xx, y, string[s].code, material)
            xx += RenderSystem.SMALLCHAR_WIDTH
            s++
        }
        SetColor(colorWhite)
    }

    override fun DrawBigChar(x: Int, y: Int, ch: Int, material: idMaterial?) {
        var ch: Int = ch
        val row: Int
        val col: Int
        val frow: Float
        val fcol: Float
        val size: Float
        ch = ch and 255
        if (ch == ' '.code) {
            return
        }
        if (y < -RenderSystem.BIGCHAR_HEIGHT) {
            return
        }
        row = ch shr 4
        col = ch and 15
        frow = row * 0.0625f
        fcol = col * 0.0625f
        size = 0.0625f
        DrawStretchPic(
            x.toFloat(),
            y.toFloat(),
            RenderSystem.BIGCHAR_WIDTH.toFloat(),
            RenderSystem.BIGCHAR_HEIGHT.toFloat(),
            fcol,
            frow,
            fcol + size,
            frow + size,
            material
        )
    }

    /*
     ==================
     idRenderSystemLocal::DrawBigString[Color]

     Draws a multi-colored string with a drop shadow, optionally forcing
     to a fixed color.

     Coordinates are at 640 by 480 virtual resolution
     ==================
     */
    override fun DrawBigStringExt(
        x: Int,
        y: Int,
        string: String,
        setColor: idVec4,
        forceColor: Boolean,
        material: idMaterial?
    ) {
        val color = idVec4()
        var s: Int
        var xx: Int

        // draw the colored text
        s = 0 //string;
        xx = x
        SetColor(setColor)
        while (s < string.length) {
            if (IsColor(string.substring(s))) {
                if (!forceColor) {
                    if ((string[s + 1].code == C_COLOR_DEFAULT)) {
                        SetColor(setColor)
                    } else {
                        color.set(ColorForIndex(string[s + 1].code))
                        color[3] = setColor[3]
                        SetColor(color)
                    }
                }
                s += 2
                continue
            }
            DrawBigChar(xx, y, string[s].code, material)
            xx += RenderSystem.BIGCHAR_WIDTH
            s++
        }
        SetColor(colorWhite)
    }

    override fun WriteDemoPics() {
        Session.session.writeDemo!!.WriteInt(demoSystem_t.DS_RENDER)
        Session.session.writeDemo!!.WriteInt(demoCommand_t.DC_GUI_MODEL)
        guiModel!!.WriteToDemo(Session.session.writeDemo)
    }

    override fun DrawDemoPics() {
        demoGuiModel!!.EmitFullScreen()
    }

    override fun BeginFrame(windowWidth: Int, windowHeight: Int) {
        var windowWidth: Int = windowWidth
        var windowHeight: Int = windowHeight
        var cmd: setBufferCommand_t
        if (!glConfig.isInitialized) {
            return
        }

        // DG: r_lockSurfaces only works properly if r_useScissor is disabled
        if (r_lockSurfaces.IsModified()) {
            r_lockSurfaces.ClearModified()
            if (r_lockSurfaces.GetBool()) {
                origUseScissor = r_useScissor.GetBool()
                r_useScissor.SetBool(false)
            } else {
                r_useScissor.SetBool(origUseScissor)
            }
        } // DG end

        // determine which back end we will use
        SetBackEndRenderer()
        guiModel!!.Clear()

        // for the larger-than-window tiled rendering screenshots
        if (tiledViewport[0] != 0) {
            windowWidth = tiledViewport[0]
            windowHeight = tiledViewport[1]
        }
        origWidth = glConfig.vidWidth
        origHeight = glConfig.vidHeight
        glConfig.vidWidth = windowWidth
        glConfig.vidHeight = windowHeight
        renderCrops[0]!!.x = 0
        renderCrops[0]!!.y = 0
        renderCrops[0]!!.width = windowWidth
        renderCrops[0]!!.height = windowHeight
        currentRenderCrop = 0

        // screenFraction is just for quickly testing fill rate limitations
        if (r_screenFraction!!.GetInteger() != 100) {
            val w: Int =
                (RenderSystem.SCREEN_WIDTH * r_screenFraction.GetInteger() / 100.0f).toInt()
            val h: Int =
                (RenderSystem.SCREEN_HEIGHT * r_screenFraction!!.GetInteger() / 100.0f).toInt()
            CropRenderSize(w, h)
        }

        // this is the ONLY place this is modified
        frameCount++

        // just in case we did a common.Error while this
        // was set
        guiRecursionLevel = 0

        // the first rendering will be used for commands like
        // screenshot, rather than a possible subsequent remote
        // or mirror render
//	primaryWorld = NULL;

        // set the time for shader effects in 2D rendering
        frameShaderTime = (EventLoop.eventLoop.Milliseconds() * 0.001f)

        //
        // draw buffer stuff
        //
        RenderSystem.R_GetCommandBuffer(setBufferCommand_t().also({ cmd = it /*sizeof(cmd)*/ }))
        cmd.commandId = renderCommand_t.RC_SET_BUFFER
        cmd.frameCount = frameCount
        if (r_frontBuffer!!.GetBool()) {
            cmd.buffer = GL11.GL_FRONT
        } else {
            cmd.buffer = GL11.GL_BACK
        }
    }

    override fun EndFrame(frontEndMsec: IntArray?, backEndMsec: IntArray?) {
        var cmd: emptyCommand_t
        DBG_EndFrame++
        if (!glConfig.isInitialized) {
            return
        }

        // close any gui drawing
        guiModel!!.EmitFullScreen()
        guiModel!!.Clear()

        // save out timing information
        if (frontEndMsec != null) {
            frontEndMsec[0] = pc!!.frontEndMsec.toInt()
        }
        if (backEndMsec != null) {
            backEndMsec[0] = backEnd!!.pc.msec
        }

        // print any other statistics and clear all of them
        RenderSystem.R_PerformanceCounters()

        // check for dynamic changes that require some initialization
        RenderSystem.R_CheckCvars()

        // check for errors
        GL_CheckErrors()

        // add the swapbuffers command
        RenderSystem.R_GetCommandBuffer(emptyCommand_t().also({ cmd = it /*sizeof(cmd)*/ }))
        cmd.commandId = renderCommand_t.RC_SWAP_BUFFERS

        // start the back end up again with the new command list
        RenderSystem.R_IssueRenderCommands()

        // use the other buffers next frame, because another CPU
        // may still be rendering into the current buffers
        tr_main.R_ToggleSmpFrame()

        // we can now release the vertexes used this frame
        VertexCache.vertexCache.EndFrame()
        if (Session.session.writeDemo != null) {
            Session.session.writeDemo!!.WriteInt(demoSystem_t.DS_RENDER)
            Session.session.writeDemo!!.WriteInt(demoCommand_t.DC_END_FRAME)
            if (r_showDemo!!.GetBool()) {
                Common.common.Printf("write DC_END_FRAME\n")
            }
        }

        // DG: restore the original size that was set before BeginFrame() overwrote it
        //     with its function-arguments, so editors don't mess up our viewport.
        //     (unsure why/how this at least *kinda* worked in original Doom3,
        //      maybe glConfig.vidWidth/Height was reset if the window gained focus or sth)
        glConfig.vidWidth = origWidth
        glConfig.vidHeight = origHeight
    }

    /*
     ==================
     TakeScreenshot

     Move to tr_imagefiles.c...

     Will automatically tile render large screen shots if necessary
     Downsample is the number of steps to mipmap the image before saving it
     If ref == NULL, session->updateScreen will be used
     ==================
     */
    override fun TakeScreenshot(width: Int, height: Int, fileName: String, blends: Int, ref: renderView_s?) {
        val buffer: ByteArray
        var i: Int
        var j: Int
        val c: Int
        var temp: Int
        takingScreenshot = true
        val pix: Int = width * height
        buffer = ByteArray(pix * 3 + 18) // R_StaticAlloc(pix * 3 + 18);
        //	memset (buffer, 0, 18);
        if (blends <= 1) {
            R_ReadTiledPixels(width, height, buffer, 18, ref)
        } else {
            val shortBuffer = ShortArray(pix * 2 * 3) // R_StaticAlloc(pix * 2 * 3);
            //		memset (shortBuffer, 0, pix*2*3);

            // enable anti-aliasing jitter
            r_jitter!!.SetBool(true)
            i = 0
            while (i < blends) {
                R_ReadTiledPixels(width, height, buffer, 18, ref)
                j = 0
                while (j < pix * 3) {
                    shortBuffer[j] = (shortBuffer[j] + buffer[18 + j]).toShort()
                    j++
                }
                i++
            }

            // divide back to bytes
            i = 0
            while (i < pix * 3) {
                buffer[18 + i] = (shortBuffer[i] / blends).toByte()
                i++
            }

//                R_StaticFree(shortBuffer);
            r_jitter!!.SetBool(false)
        }

        // fill in the header (this is vertically flipped, which qglReadPixels emits)
        buffer[2] = 2 // uncompressed type
        buffer[12] = (width and 255).toByte()
        buffer[13] = (width shr 8).toByte()
        buffer[14] = (height and 255).toByte()
        buffer[15] = (height shr 8).toByte()
        buffer[16] = 24 // pixel size

        // swap rgb to bgr
        c = 18 + width * height * 3
        i = 18
        while (i < c) {
            temp = buffer[i].toInt()
            buffer[i] = buffer[i + 2]
            buffer[i + 2] = temp.toByte()
            i += 3
        }

        // _D3XP adds viewnote screenie save to cdpath
        if (fileName.contains("viewnote")) {
            fileSystem.WriteFile(fileName, ByteBuffer.wrap(buffer), c, "fs_cdpath")
        } else {
            fileSystem.WriteFile(fileName, ByteBuffer.wrap(buffer), c)
        }
        //
//            R_StaticFree(buffer);
        takingScreenshot = false
    }

    /*
     ================
     CropRenderSize

     This automatically halves sizes until it fits in the current window size,
     so if you specify a power of two size for a texture copy, it may be shrunk
     down, but still valid.
     ================
     */
    override fun CropRenderSize(width: Int, height: Int, makePowerOfTwo: Boolean, forceDimensions: Boolean) {
        var width: Int = width
        var height: Int = height
        if (!glConfig.isInitialized) {
            return
        }

        // close any gui drawing before changing the size
        guiModel!!.EmitFullScreen()
        guiModel!!.Clear()
        if (width < 1 || height < 1) {
            Common.common.Error("CropRenderSize: bad sizes")
        }
        if (Session.session.writeDemo != null) {
            Session.session.writeDemo!!.WriteInt(demoSystem_t.DS_RENDER)
            Session.session.writeDemo!!.WriteInt(demoCommand_t.DC_CROP_RENDER)
            Session.session.writeDemo!!.WriteInt(width)
            Session.session.writeDemo!!.WriteInt(height)
            Session.session.writeDemo!!.WriteInt(btoi(makePowerOfTwo))
            if (r_showDemo!!.GetBool()) {
                Common.common.Printf("write DC_CROP_RENDER\n")
            }
        }

        // convert from virtual SCREEN_WIDTH/SCREEN_HEIGHT coordinates to physical OpenGL pixels
        val renderView = renderView_s()
        renderView.x = 0
        renderView.y = 0
        renderView.width = width
        renderView.height = height
        val r = idScreenRect()
        RenderViewToViewport(renderView, r)
        width = r.x2 - r.x1 + 1
        height = r.y2 - r.y1 + 1
        if (forceDimensions) {
            // just give exactly what we ask for
            width = renderView.width
            height = renderView.height
        }

        // if makePowerOfTwo, drop to next lower power of two after scaling to physical pixels
        if (makePowerOfTwo) {
            width = MegaTexture.RoundDownToPowerOfTwo(width)
            height = MegaTexture.RoundDownToPowerOfTwo(height)
            // FIXME: megascreenshots with offset viewports don't work right with this yet
        }
        val rc: renderCrop_t? = renderCrops[currentRenderCrop]

        // we might want to clip these to the crop window instead
        while (width > glConfig.vidWidth) {
            width = width shr 1
        }
        while (height > glConfig.vidHeight) {
            height = height shr 1
        }
        if (currentRenderCrop == MAX_RENDER_CROPS) {
            Common.common.Error("idRenderSystemLocal::CropRenderSize: currentRenderCrop == MAX_RENDER_CROPS")
        }
        currentRenderCrop++

//            rc = renderCrops[currentRenderCrop];
        renderCrops[currentRenderCrop - 1] = renderCrops[currentRenderCrop]
        rc!!.x = 0
        rc.y = 0
        rc.width = width
        rc.height = height
    }

    override fun CaptureRenderToImage(imageName: String?) {
        if (!glConfig.isInitialized) {
            return
        }
        guiModel!!.EmitFullScreen()
        guiModel!!.Clear()
        if (Session.session.writeDemo != null) {
            Session.session.writeDemo!!.WriteInt(demoSystem_t.DS_RENDER)
            Session.session.writeDemo!!.WriteInt(demoCommand_t.DC_CAPTURE_RENDER)
            Session.session.writeDemo!!.WriteHashString((imageName)!!)
            if (r_showDemo!!.GetBool()) {
                Common.common.Printf("write DC_CAPTURE_RENDER: %s\n", (imageName))
            }
        }

        // look up the image before we create the render command, because it
        // may need to sync to create the image
        val image: idImage? = Image.globalImages.ImageFromFile(
            imageName,
            textureFilter_t.TF_DEFAULT,
            true,
            textureRepeat_t.TR_REPEAT,
            textureDepth_t.TD_DEFAULT
        )
        val rc: renderCrop_t? = renderCrops[currentRenderCrop]
        var cmd: copyRenderCommand_t
        RenderSystem.R_GetCommandBuffer(copyRenderCommand_t().also({ cmd = it /*sizeof(cmd)*/ }))
        cmd.commandId = renderCommand_t.RC_COPY_RENDER
        cmd.x = rc!!.x
        cmd.y = rc.y
        cmd.imageWidth = rc.width
        cmd.imageHeight = rc.height
        cmd.image = image
        guiModel!!.Clear()
    }

    override fun CaptureRenderToFile(fileName: String?, fixAlpha: Boolean) {
        if (!glConfig.isInitialized) {
            return
        }
        val rc: renderCrop_t? = renderCrops[currentRenderCrop]
        guiModel!!.EmitFullScreen()
        guiModel!!.Clear()
        RenderSystem.R_IssueRenderCommands()
        qgl.qglReadBuffer(GL11.GL_BACK)

        // include extra space for OpenGL padding to word boundaries
        val c: Int = (rc!!.width + 3) * rc.height
        var data: ByteBuffer = BufferUtils.createByteBuffer(c * 3) // R_StaticAlloc(c * 3);
        qgl.qglReadPixels(rc.x, rc.y, rc.width, rc.height, GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE, data)
        var data2: ByteBuffer = ByteBuffer.allocate(c * 4) // R_StaticAlloc(c * 4);
        for (i in 0 until c) {
            data2.put(i * 4, data.get(i * 3))
            data2.put(i * 4 + 1, data.get(i * 3 + 1))
            data2.put(i * 4 + 2, data.get(i * 3 + 2))
            data2.put(i * 4 + 3, 0xff.toByte())
        }
        Image_files.R_WriteTGA(fileName, data2, rc.width, rc.height, true)
    }

    override fun UnCrop() {
        if (!glConfig.isInitialized) {
            return
        }
        if (currentRenderCrop < 1) {
            Common.common.Error("idRenderSystemLocal::UnCrop: currentRenderCrop < 1")
        }

        // close any gui drawing
        guiModel!!.EmitFullScreen()
        guiModel!!.Clear()
        currentRenderCrop--
        if (Session.session.writeDemo != null) {
            Session.session.writeDemo!!.WriteInt(demoSystem_t.DS_RENDER)
            Session.session.writeDemo!!.WriteInt(demoCommand_t.DC_UNCROP_RENDER)
            if (r_showDemo!!.GetBool()) {
                Common.common.Printf("write DC_UNCROP\n")
            }
        }
    }

    override fun GetCardCaps(oldCard: BooleanArray, nv10or20: BooleanArray) {
    }

    override fun UploadImage(imageName: String?, data: ByteBuffer?, width: Int, height: Int): Boolean {
        val image: idImage? = Image.globalImages.GetImage(imageName)
        if (null == image) {
            return false
        }
        image.UploadScratch(data, width, height)
        image.SetImageFilterAndRepeat()
        return true
    }

    companion object {
        /*
     =============
     EndFrame

     Returns the number of msec spent in the back end
     =============
     */
        private var DBG_EndFrame: Int = 0
    }
}

//optimizedShadow_t SuperOptimizeOccluders( idVec4 *verts, glIndex_t *indexes, int numIndexes,
//										 idPlane projectionPlane, idVec3 projectionOrigin );
//
//void CleanupOptimizedShadowTris( srfTriangles_t *tri );
/*
 ============================================================

 util/shadowopt3

 dmap time optimization of shadow volumes, called from R_CreateShadowVolume

 ============================================================
 */
class optimizedShadow_t {
    var indexes // caller should free
            : IntArray? = null

    //
    // indexes must be sorted frontCap, rearCap, silPlanes so the caps can be removed
    // when the viewer is in a position that they don't need to see them
    var numFrontCapIndexes: Int = 0
    var numRearCapIndexes: Int = 0
    var numSilPlaneIndexes: Int = 0
    var numVerts: Int = 0
    var totalIndexes: Int = 0
    var verts // includes both front and back projections, caller should free
            : Array<idVec3>? = null
}

// deformable meshes precalculate as much as possible from a base frame, then generate
// complete srfTriangles_t from just a new set of vertexes
class deformInfo_s {
    //
    var dominantTris: Array<dominantTri_s>? = null
    var dupVerts: IntArray? = null
    var  /*glIndex_t */indexes: IntArray? = null
    var mirroredVerts: IntArray? = null

    //
    var numDupVerts: Int = 0

    //
    var numIndexes: Int = 0

    //
    var numMirroredVerts: Int = 0

    // numOutputVerts may be smaller if the input had duplicated or degenerate triangles
    // it will often be larger if the input had mirrored texture seams that needed
    // to be busted for proper tangent spaces
    var numOutputVerts: Int = 0

    //
    var numSilEdges: Int = 0
    var numSourceVerts: Int = 0
    var silEdges: Array<silEdge_t>? = null

    //
    var  /*glIndex_t */silIndexes: IntArray? = null

    companion object {
        val BYTES: Int = Integer.BYTES * 11
    }
}

/*
 =============================================================

 TR_TRACE

 =============================================================
 */
class localTrace_t {
    val indexes: IntArray = IntArray(3)
    val normal: idVec3 = idVec3()

    // only valid if fraction < 1.0f
    val point: idVec3 = idVec3()
    var fraction: Float = 0.0f
}

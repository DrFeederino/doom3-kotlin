package neo.Renderer

import neo.Renderer.Cinematic.idCinematic
import neo.Renderer.Cinematic.idSndWindow
import neo.Renderer.Image.cubeFiles_t
import neo.Renderer.Image.idImage
import neo.Renderer.Image.idImageManager
import neo.Renderer.Image.textureDepth_t
import neo.Renderer.MegaTexture.idMegaTexture
import neo.Sound.sound.idSoundEmitter
import neo.TempDump.CPP_class
import neo.TempDump.SERiAL
import neo.TempDump.atoi
import neo.TempDump.ctos
import neo.TempDump.etoi
import neo.TempDump.strLen
import neo.framework.CVarSystem.cvarSystem
import neo.framework.Common
import neo.framework.DeclManager
import neo.framework.DeclManager.declType_t
import neo.framework.DeclManager.idDecl
import neo.framework.DeclTable.idDeclTable
import neo.idlib.BIT
import neo.idlib.Text.Lexer.LEXFL_ALLOWPATHNAMES
import neo.idlib.Text.Lexer.LEXFL_NOFATALERRORS
import neo.idlib.Text.Lexer.LEXFL_NOSTRINGCONCAT
import neo.idlib.Text.Lexer.LEXFL_NOSTRINGESCAPECHARS
import neo.idlib.Text.Lexer.idLexer
import neo.idlib.Text.Str.idStr
import neo.idlib.Text.Str.idStr.Companion.Copynz
import neo.idlib.Text.Str.idStr.Companion.snPrintf
import neo.idlib.Text.Token.TT_NUMBER
import neo.idlib.Text.Token.idToken
import neo.idlib.containers.List.idList
import neo.idlib.precompiled.MAX_EXPRESSION_OPS
import neo.idlib.precompiled.MAX_EXPRESSION_REGISTERS
import neo.ui.UserInterface.idUserInterface
import neo.ui.UserInterface.uiManager
import org.lwjgl.opengl.ARBFragmentProgram
import org.lwjgl.opengl.ARBVertexProgram
import java.nio.*
import java.util.*

/*
 ===============================================================================

 Material

 ===============================================================================
 */
object Material {
    val CONTENTS_AAS_OBSTACLE: Int = BIT(14) // used to compile an obstacle into AAS that can be enabled/disabled
    val CONTENTS_AAS_SOLID: Int = BIT(13) // solid for AAS

    //
    // contents used by utils
    val CONTENTS_AREAPORTAL: Int = BIT(20) // portal separating renderer areas
    val CONTENTS_BLOOD: Int = BIT(7) // used to detect blood decals
    val CONTENTS_BODY: Int = BIT(8) // used for actors
    val CONTENTS_CORPSE: Int = BIT(10) // used for dead bodies
    val CONTENTS_FLASHLIGHT_TRIGGER: Int = BIT(15) // used for triggers that are activated by the flashlight
    val CONTENTS_IKCLIP: Int = BIT(6) // solid to IK
    val CONTENTS_MONSTERCLIP: Int = BIT(4) // solid to monsters
    val CONTENTS_MOVEABLECLIP: Int = BIT(5) // solid to moveable entities
    val CONTENTS_NOCSG: Int = BIT(21) // don't cut this brush with CSG operations in the editor
    val CONTENTS_OPAQUE: Int = BIT(1) // blocks visibility (for ai)
    val CONTENTS_PLAYERCLIP: Int = BIT(3) // solid to players
    val CONTENTS_PROJECTILE: Int = BIT(9) // used for projectiles

    //
    val CONTENTS_REMOVE_UTIL: Int = (CONTENTS_AREAPORTAL or CONTENTS_NOCSG).inv()
    val CONTENTS_RENDERMODEL: Int = BIT(11) // used for render models for collision detection

    //} materialFlags_t;
    //
    //
    // contents flags; NOTE: make sure to keep the defines in doom_defs.script up to date with these!
    // typedef enum {
    val CONTENTS_SOLID: Int = BIT(0) // an eye is never valid in a solid
    val CONTENTS_TRIGGER: Int = BIT(12) // used for triggers
    val CONTENTS_WATER: Int = BIT(2) // used for water

    //
    val MAX_ENTITY_SHADER_PARMS: Int = 12

    //
    //
    // material flags
    //typedef enum {
    val MF_DEFAULTED: Int = BIT(0)
    val MF_EDITOR_VISIBLE: Int = BIT(6) // in use (visible) per editor
    val MF_FORCESHADOWS: Int = BIT(3)
    val MF_NOPORTALFOG: Int = BIT(5) // this fog volume won't ever consider a portal fogged out
    val MF_NOSELFSHADOW: Int = BIT(4)
    val MF_NOSHADOWS: Int = BIT(2)
    val MF_POLYGONOFFSET: Int = BIT(1)

    // } contentsFlags_t;
    //
    // surface types
    val NUM_SURFACE_BITS: Int = 4
    val MAX_SURFACE_TYPES: Int = 1 shl NUM_SURFACE_BITS
    val SS_GUI: Int = -2 // guis
    val SURF_COLLISION: Int = BIT(6) // collision surface

    //} materialSort_t;
    val SURF_DISCRETE: Int = BIT(10) // not clipped or merged by utilities
    val SURF_LADDER: Int = BIT(7) // player can climb up this surface

    //
    val SURF_NODAMAGE: Int = BIT(4) // never give falling damage
    val SURF_NOFRAGMENT: Int = BIT(11) // dmap won't cut surface at each bsp boundary
    val SURF_NOIMPACT: Int = BIT(8) // don't make missile explosions
    val SURF_NOSTEPS: Int = BIT(9) // no footstep sounds
    val SURF_NULLNORMAL: Int =
        BIT(12) // renderbump will draw this surface as 0x80 0x80 0x80; which won't collect light from any angle
    val SURF_SLICK: Int = BIT(5) // effects game physics

    //
    // surface flags
    // typedef enum {
    val SURF_TYPE_BIT0: Int = BIT(0) // encodes the material type (metal; flesh; concrete; etc.)
    val SURF_TYPE_BIT1: Int = BIT(1) // "
    val SURF_TYPE_BIT2: Int = BIT(2) // "
    val SURF_TYPE_BIT3: Int = BIT(3) // "
    val SURF_TYPE_MASK: Int = (1 shl NUM_SURFACE_BITS) - 1
    val MAX_FRAGMENT_IMAGES: Int = 8

    // these don't effect per-material storage, so they can be very large
    val MAX_SHADER_STAGES: Int = 256

    //
    val MAX_TEXGEN_REGISTERS: Int = 4
    val MAX_VERTEX_PARMS: Int = 4

    //
    val SS_ALMOST_NEAREST: Int = 6 // gun smoke puffs
    val SS_BAD: Int = -1
    val SS_CLOSE: Int = 5
    val SS_DECAL: Int = 2 // scorch marks, etc.

    //
    val SS_FAR: Int = 3
    val SS_MEDIUM: Int = 4 // normal translucent

    //
    val SS_NEAREST: Int = 7 // screen blood blobs
    val SS_OPAQUE: Int = 0 // opaque

    //
    val SS_PORTAL_SKY: Int = 1

    //
    val SS_POST_PROCESS: Int = 100 // after a screen copy to texture

    //typedef enum {
    val SS_SUBVIEW: Int = -3 // mirrors, viewscreens, etc

    @Deprecated("")
    val opNames: Array<String> = arrayOf(
        "OP_TYPE_ADD",
        "OP_TYPE_SUBTRACT",
        "OP_TYPE_MULTIPLY",
        "OP_TYPE_DIVIDE",
        "OP_TYPE_MOD",
        "OP_TYPE_TABLE",
        "OP_TYPE_GT",
        "OP_TYPE_GE",
        "OP_TYPE_LT",
        "OP_TYPE_LE",
        "OP_TYPE_EQ",
        "OP_TYPE_NE",
        "OP_TYPE_AND",
        "OP_TYPE_OR"
    )

    enum class cullType_t {
        CT_FRONT_SIDED,
        CT_BACK_SIDED,
        CT_TWO_SIDED
    }

    enum class deform_t {
        DFRM_NONE,
        DFRM_SPRITE,
        DFRM_TUBE,
        DFRM_FLARE,
        DFRM_EXPAND,
        DFRM_MOVE,
        DFRM_EYEBALL,
        DFRM_PARTICLE,
        DFRM_PARTICLE2,
        DFRM_TURB
    }

    enum class dynamicidImage_t {
        DI_STATIC,
        DI_SCRATCH,

        // video, screen wipe, etc
        DI_CUBE_RENDER,
        DI_MIRROR_RENDER,
        DI_XRAY_RENDER,
        DI_REMOTE_RENDER
    }

    // note: keep opNames[] in sync with changes
    internal enum class expOpType_t {
        OP_TYPE_ADD,
        OP_TYPE_SUBTRACT,
        OP_TYPE_MULTIPLY,
        OP_TYPE_DIVIDE,
        OP_TYPE_MOD,
        OP_TYPE_TABLE,
        OP_TYPE_GT,
        OP_TYPE_GE,
        OP_TYPE_LT,
        OP_TYPE_LE,
        OP_TYPE_EQ,
        OP_TYPE_NE,
        OP_TYPE_AND,
        OP_TYPE_OR,
        OP_TYPE_SOUND
    }

    internal enum class expRegister_t {
        EXP_REG_TIME,

        //
        EXP_REG_PARM0,
        EXP_REG_PARM1,
        EXP_REG_PARM2,
        EXP_REG_PARM3,
        EXP_REG_PARM4,
        EXP_REG_PARM5,
        EXP_REG_PARM6,
        EXP_REG_PARM7,
        EXP_REG_PARM8,
        EXP_REG_PARM9,
        EXP_REG_PARM10,
        EXP_REG_PARM11,

        //
        EXP_REG_GLOBAL0,
        EXP_REG_GLOBAL1,
        EXP_REG_GLOBAL2,
        EXP_REG_GLOBAL3,
        EXP_REG_GLOBAL4,
        EXP_REG_GLOBAL5,
        EXP_REG_GLOBAL6,
        EXP_REG_GLOBAL7,

        //
        EXP_REG_NUM_PREDEFINED
    }

    enum class materialCoverage_t {
        MC_BAD,
        MC_OPAQUE,

        // completely fills the triangle, will have black drawn on fillDepthBuffer
        MC_PERFORATED,

        // may have alpha tested holes
        MC_TRANSLUCENT // blended with background
    }

    // the order BUMP / DIFFUSE / SPECULAR is necessary for interactions to draw correctly on low end cards
    enum class stageLighting_t {
        SL_AMBIENT,

        // execute after lighting
        SL_BUMP,
        SL_DIFFUSE,
        SL_SPECULAR
    }

    // cross-blended terrain textures need to modulate the color by
    // the vertex color to smoothly blend between two textures
    enum class stageVertexColor_t {
        SVC_IGNORE,
        SVC_MODULATE,
        SVC_INVERSE_MODULATE
    }

    enum class surfTypes_t {
        SURFTYPE_NONE,

        // default type
        SURFTYPE_METAL,
        SURFTYPE_STONE,
        SURFTYPE_FLESH,
        SURFTYPE_WOOD,
        SURFTYPE_CARDBOARD,
        SURFTYPE_LIQUID,
        SURFTYPE_GLASS,
        SURFTYPE_PLASTIC,
        SURFTYPE_RICOCHET,
        SURFTYPE_10,
        SURFTYPE_11,
        SURFTYPE_12,
        SURFTYPE_13,
        SURFTYPE_14,
        SURFTYPE_15
    }

    enum class texgen_t {
        TG_EXPLICIT,
        TG_DIFFUSE_CUBE,
        TG_REFLECT_CUBE,
        TG_SKYBOX_CUBE,
        TG_WOBBLESKY_CUBE,
        TG_SCREEN,

        // screen aligned, for mirrorRenders and screen space temporaries
        TG_SCREEN2,
        TG_GLASSWARP
    }

    // moved from image.h for default parm
    enum class textureFilter_t {
        TF_LINEAR,
        TF_NEAREST,
        TF_DEFAULT // use the user-specified r_textureFilter
    }

    enum class textureRepeat_t {
        TR_REPEAT,
        TR_CLAMP,
        TR_CLAMP_TO_BORDER,

        // this should replace TR_CLAMP_TO_ZERO and TR_CLAMP_TO_ZERO_ALPHA, but I don't want to risk changing it right now
        //
        TR_CLAMP_TO_ZERO,

        // guarantee 0,0,0,255 edge for projected textures, set AFTER image format selection
        //
        TR_CLAMP_TO_ZERO_ALPHA // guarantee 0 alpha edge for projected textures, set AFTER image format selection
    }

    class decalInfo_t {
        val end: FloatArray =
            FloatArray(4) // vertex color at fade-out (possibly out of 0.0f - 1.0f range, will clamp after calc)
        val start: FloatArray =
            FloatArray(4) // vertex color at spawn (possibly out of 0.0f - 1.0f range, will clamp after calc)
        var fadeTime: Int = 0 // msec to fade vertex colors over
        var stayTime: Int = 0 // msec for no change

        companion object {
            @Transient
            val SIZE: Int = (Integer.SIZE
                    + Integer.SIZE
                    + (java.lang.Float.SIZE * 4)
                    + (java.lang.Float.SIZE * 4))
        }
    }

    internal class expOp_t {
        var a: Int = 0
        var b: Int = 0
        var c: Int = 0
        var opType: expOpType_t? = null

        constructor()
        private constructor(op: expOp_t) {
            opType = op.opType
            a = op.a
            b = op.b
            c = op.c
        }

        companion object {
            @Transient
            val SIZE: Int = (CPP_class.Enum.SIZE
                    + (Integer.SIZE * 3))
        }
    }

    class colorStage_t {
        val registers: IntArray = IntArray(4)

        constructor()
        private constructor(color: colorStage_t) {
            System.arraycopy(color.registers, 0, registers, 0, registers.size)
        }

        companion object {
            @Transient
            val SIZE: Int = 4 * Integer.SIZE
        }
    }

    class textureStage_t {
        val cinematic: Array<idCinematic?> = arrayOf(null)
        val image: Array<idImage?> = arrayOf(null)
        var matrix: Array<IntArray> = Array(2, { IntArray(3) }) // we only allow a subset of the full projection matrix

        // dynamic image variables
        var dynamic: dynamicidImage_t = dynamicidImage_t.entries[0]
        var dynamicFrameCount: Int = 0
        var hasMatrix: Boolean = false
        var texgen: texgen_t = texgen_t.entries[0]
        var width: Int = 0
        var height: Int = 0

        constructor() {
            blaCounter++
        }

        private constructor(texture: textureStage_t) {
            cinematic[0] = texture.cinematic[0] //pointer
            image!![0] = texture.image[0] //pointer
            texgen = texture.texgen
            hasMatrix = texture.hasMatrix
            System.arraycopy(texture.matrix[0], 0, matrix[0], 0, matrix[0]!!.size)
            System.arraycopy(texture.matrix[1], 0, matrix[1], 0, matrix[1]!!.size)
            dynamic = texture.dynamic
            width = texture.width
            height = texture.height
            dynamicFrameCount = texture.dynamicFrameCount
        }

        companion object {
            @Transient
            val SIZE: Int = (CPP_class.Pointer.SIZE //idCinematic
                    + idImage.SIZE
                    + CPP_class.Enum.SIZE //texgen_t
                    + CPP_class.Bool.SIZE
                    + (Integer.SIZE * 2 * 3)
                    + CPP_class.Enum.SIZE //dynamicidImage_t
                    + (Integer.SIZE * 2)
                    + Integer.SIZE)
            private var blaCounter: Int = 0
        }
    }

    class newShaderStage_t {
        val vertexParms: Array<IntArray?> = Array(MAX_VERTEX_PARMS, { IntArray(4) }) // evaluated register indexes
        var fragmentProgram: Int = 0
        var fragmentProgramImages: Array<idImage?> = arrayOfNulls(MAX_FRAGMENT_IMAGES)
        var megaTexture: idMegaTexture? = null // handles all the binding and parameter setting
        var numFragmentProgramImages: Int = 0
        var numVertexParms: Int = 0
        var vertexProgram: Int = 0

        companion object {
            @Transient
            val SIZE: Int = (Integer.SIZE
                    + Integer.SIZE
                    + (Integer.SIZE * MAX_VERTEX_PARMS * 4)
                    + Integer.SIZE
                    + Integer.SIZE
                    + (idImage.SIZE * MAX_FRAGMENT_IMAGES) //TODO:pointer
                    + idMegaTexture.SIZE)
        }
    }

    class shaderStage_t {
        val texture: textureStage_t
        private val DBG_count: Int = DBG_counter++
        var alphaTestRegister: Int = 0
        var color: colorStage_t
        var conditionRegister: Int = 0 // if registers[conditionRegister] == 0, skip stage
        var drawStateBits: Int = 0
        var hasAlphaTest: Boolean = false
        var ignoreAlphaTest: Boolean =
            false // this stage should act as translucent, even if the surface is alpha tested
        var lighting // determines which passes interact with lights
                : stageLighting_t

        //
        var newStage: newShaderStage_t? = null // vertex / fragment program based stage

        //
        var privatePolygonOffset: Float = 0.0f // a per-stage polygon offset
        var vertexColor: stageVertexColor_t = stageVertexColor_t.entries[0]

        init {
            lighting = stageLighting_t.entries.toTypedArray()[0]
            color = colorStage_t()
            texture = textureStage_t()
        }

        companion object {
            @Transient
            val SIZE: Int = (Integer.SIZE
                    + CPP_class.Pointer.SIZE //stageLighting_t
                    + Integer.SIZE
                    + colorStage_t.SIZE
                    + Integer.SIZE
                    + CPP_class.Bool.SIZE
                    + Integer.SIZE
                    + textureStage_t.SIZE
                    + CPP_class.Pointer.SIZE //stageVertexColor_t
                    + CPP_class.Bool.SIZE
                    + java.lang.Float.SIZE
                    + CPP_class.Pointer.SIZE) //newShaderStage_t
            private var DBG_counter: Int = 0
        }
    }

    // } surfaceFlags_t;
    // keep all of these on the stack, when they are static it makes material parsing non-reentrant
    internal class mtrParsingData_s {
        var forceOverlays: Boolean = false
        var parseStages: Array<shaderStage_t?> = arrayOfNulls(MAX_SHADER_STAGES)
        var registerIsTemporary: BooleanArray = BooleanArray(MAX_EXPRESSION_REGISTERS)

        //
        var registersAreConstant: Boolean = false
        var shaderOps: Array<expOp_t?> = arrayOfNulls<expOp_t>(MAX_EXPRESSION_OPS)
        var shaderRegisters: FloatArray = FloatArray(MAX_EXPRESSION_REGISTERS)

        init {
            for (s in shaderOps.indices) {
                shaderOps[s] = expOp_t()
            }
            for (p in parseStages.indices) {
                parseStages[p] = shaderStage_t()
            }
        }

        companion object {
            @Transient
            val SIZE: Int = ((CPP_class.Bool.SIZE * MAX_EXPRESSION_REGISTERS)
                    + (java.lang.Float.SIZE * MAX_EXPRESSION_REGISTERS)
                    + (expOp_t.SIZE * MAX_EXPRESSION_OPS)
                    + (shaderStage_t.SIZE * MAX_SHADER_STAGES)
                    + CPP_class.Bool.SIZE
                    + CPP_class.Bool.SIZE)
        }
    }

    class idMaterial : idDecl, SERiAL {
        //
        //
        private val deformRegisters: IntArray = IntArray(4) // numeric parameter for deforms

        //
        private val texGenRegisters: IntArray = IntArray(MAX_TEXGEN_REGISTERS) // for wobbleSky
        var DBG_BALLS: Int = 0

        //
        var stages: Array<shaderStage_t?>? = null
        private var allowOverlays: Boolean = false
        private var ambientLight: Boolean = false
        private var blendLight: Boolean = false

        //
        private var constantRegisters // NULL if ops ever reference globalParms or entityParms
                : FloatArray? = null

        //
        private var contentFlags: Int = 0 // content flags

        //
        private var coverage: materialCoverage_t = materialCoverage_t.MC_BAD
        private var cullType: cullType_t = cullType_t.CT_FRONT_SIDED // CT_FRONT_SIDED, CT_BACK_SIDED, or CT_TWO_SIDED

        //
        private var decalInfo: decalInfo_t = decalInfo_t()
        private var deform: deform_t? = null
        private var deformDecl: idDecl? = null // for surface emitted particle deforms and tables
        private var desc: idStr = idStr()// description
        private var editorAlpha: Float = 0.0f
        private var editorImage: idImage? = null // image used for non-shaded preview

        //
        // we defer loading of the editor image until it is asked for, so the game doesn't load up
        // all the invisible and uncompressed images.
        // If editorImage is NULL, it will atempt to load editorImageName, and set editorImage to that or defaultImage
        private var editorImageName: idStr? = null

        //
        private var entityGui: Int =
            0 // draw a gui with the idUserInterface from the renderEntity_t non zero will draw gui, gui2, or gui3 from renderEnitty_t
        private var expressionRegisters: FloatArray? = null

        //
        private var fogLight: Boolean = false

        //
        private var gui: idUserInterface? = null // non-custom guis are shared by all users of a material
        private var hasSubview: Boolean = false // mirror, remote render, etc

        //
        private var lightFalloffImage: idImage? = null
        private var materialFlags: Int = 0 // material flags

        //
        private var noFog: Boolean = false // surface does not create fog interactions
        private var numAmbientStages: Int = 0

        //
        private var numOps: Int = 0

        //
        private var numRegisters: Int = 0 //

        //
        private var numStages: Int = 0
        private var ops // evaluate to make expressionRegisters
                : Array<expOp_t?>? = null

        //
        private var pd: mtrParsingData_s? = null // only used during parsing

        //
        private var polygonOffset: Float = 0.0f
        private var portalSky: Boolean = false
        private var refCount: Int = 0

        //	virtual				~idMaterial();
        private var renderBump: idStr? = null // renderbump command options, without the "renderbump" at the start
        private var shouldCreateBackSides: Boolean = false

        //
        //
        private var sort: Float = 0.0f // lower numbered shaders draw before higher numbered

        //
        private var spectrum: Int = 0 // for invisible writing, used for both lights and surfaces

        //
        private var suppressInSubview: Boolean = false

        //
        private var surfaceArea // only for listSurfaceAreas
                : Float
        private var surfaceFlags: Int = 0 // surface flags
        private var unsmoothedTangents: Boolean = false

        constructor() {
            CommonInit()

            // we put this here instead of in CommonInit, because
            // we don't want it cleared when a material is purged
            surfaceArea = 0.0f
        }

        constructor(shader: idMaterial?) {
            desc = idStr(shader!!.desc)
            renderBump = shader.renderBump
            lightFalloffImage = shader.lightFalloffImage
            entityGui = shader.entityGui
            gui = shader.gui
            noFog = shader.noFog
            spectrum = shader.spectrum
            polygonOffset = shader.polygonOffset
            contentFlags = shader.contentFlags
            surfaceFlags = shader.surfaceFlags
            materialFlags = shader.materialFlags
            decalInfo = shader.decalInfo
            sort = shader.sort
            deform = shader.deform
            deformDecl = shader.deformDecl
            coverage = shader.coverage
            cullType = shader.cullType
            shouldCreateBackSides = shader.shouldCreateBackSides
            fogLight = shader.fogLight
            blendLight = shader.blendLight
            ambientLight = shader.ambientLight
            unsmoothedTangents = shader.unsmoothedTangents
            hasSubview = shader.hasSubview
            allowOverlays = shader.allowOverlays
            numOps = shader.numOps
            ops = shader.ops
            numRegisters = shader.numRegisters
            expressionRegisters = shader.expressionRegisters
            constantRegisters = shader.constantRegisters
            numStages = shader.numStages
            numAmbientStages = shader.numAmbientStages
            stages = shader.stages
            pd = shader.pd
            surfaceArea = shader.surfaceArea
            editorImageName = shader.editorImageName
            editorImage = shader.editorImage
            editorAlpha = shader.editorAlpha
            suppressInSubview = shader.suppressInSubview
            portalSky = shader.portalSky
            refCount = shader.refCount
        }

        override fun SetDefaultText(): Boolean {
            // if there exists an image with the same name
            if (true) { //fileSystem->ReadFile( GetName(), NULL ) != -1 ) {
                val generated = StringBuffer(2048)
                snPrintf(
                    generated, generated.capacity(),
                    ("material %s // IMPLICITLY GENERATED\n"
                            + "{\n"
                            + "{\n"
                            + "blend blend\n"
                            + "colored\n"
                            + "map \"%s\"\n"
                            + "clamp\n"
                            + "}\n"
                            + "}\n"), GetName(), GetName()
                )
                SetText(generated.toString())
                return true
            } else {
                return false
            }
        }

        override fun DefaultDefinition(): String {
            return ("{\n"
                    + "\t" + "{\n"
                    + "\t\t" + "blend\tblend\n"
                    + "\t\t" + "map\t\t_default\n"
                    + "\t" + "}\n"
                    + "}")
        }

        override fun Parse(text: String, textLength: Int): Boolean {
            DEBUG_Parse++
            val src = idLexer()
            //	idToken	token;
            val parsingData = mtrParsingData_s()
            src.LoadMemory(text, textLength, GetFileName(), GetLineNum())
            src.SetFlags(DeclManager.DECL_LEXER_FLAGS)
            src.SkipUntilString("{")

            // reset to the unparsed state
            CommonInit()

//	memset( &parsingData, 0, sizeof( parsingData ) );
            pd = parsingData // this is only valid during parse

            // parse it
            ParseMaterial(src)

            // if we are doing an fs_copyfiles, also reference the editorImage
            if (cvarSystem.GetCVarInteger("fs_copyFiles") != 0) {
                GetEditorImage()
            }

            //
            // count non-lit stages
            numAmbientStages = 0
            var i: Int
            i = 0
            while (i < numStages) {
                if (pd!!.parseStages[i]!!.lighting == stageLighting_t.SL_AMBIENT) {
                    numAmbientStages++
                }
                i++
            }

            // see if there is a subview stage
            if (sort == SS_SUBVIEW.toFloat()) {
                hasSubview = true
            } else {
                hasSubview = false
                i = 0
                while (i < numStages) {
                    if (etoi(pd!!.parseStages[i]!!.texture.dynamic) != 0) {
                        hasSubview = true
                    }
                    i++
                }
            }

            // automatically determine coverage if not explicitly set
            if (coverage == materialCoverage_t.MC_BAD) {
                // automatically set MC_TRANSLUCENT if we don't have any interaction stages and
                // the first stage is blended and not an alpha test mask or a subview
                if (0 == numStages) {
                    // non-visible
                    coverage = materialCoverage_t.MC_TRANSLUCENT
                } else if (numStages != numAmbientStages) {
                    // we have an interaction draw
                    coverage = materialCoverage_t.MC_OPAQUE
                } else if (((pd!!.parseStages[0]!!.drawStateBits and GLS_DSTBLEND_BITS) != GLS_DSTBLEND_ZERO
                            ) || ((pd!!.parseStages[0]!!.drawStateBits and GLS_SRCBLEND_BITS) == GLS_SRCBLEND_DST_COLOR
                            ) || ((pd!!.parseStages[0]!!.drawStateBits and GLS_SRCBLEND_BITS) == GLS_SRCBLEND_ONE_MINUS_DST_COLOR
                            ) || ((pd!!.parseStages[0]!!.drawStateBits and GLS_SRCBLEND_BITS) == GLS_SRCBLEND_DST_ALPHA
                            ) || ((pd!!.parseStages[0]!!.drawStateBits and GLS_SRCBLEND_BITS) == GLS_SRCBLEND_ONE_MINUS_DST_ALPHA)
                ) {
                    // blended with the destination
                    coverage = materialCoverage_t.MC_TRANSLUCENT
                } else {
                    coverage = materialCoverage_t.MC_OPAQUE
                }
            }

            // translucent automatically implies noshadows
            if (coverage == materialCoverage_t.MC_TRANSLUCENT) {
                SetMaterialFlag(MF_NOSHADOWS)
            } else {
                // mark the contents as opaque
                contentFlags = contentFlags or CONTENTS_OPAQUE
            }

            // if we are translucent, draw with an alpha in the editor
            if (coverage == materialCoverage_t.MC_TRANSLUCENT) {
                editorAlpha = 0.5f
            } else {
                editorAlpha = 1.0f
            }

            // the sorts can make reasonable defaults
            if (sort == SS_BAD.toFloat()) {
                if (TestMaterialFlag(MF_POLYGONOFFSET)) {
                    sort = SS_DECAL.toFloat()
                } else if (coverage == materialCoverage_t.MC_TRANSLUCENT) {
                    sort = SS_MEDIUM.toFloat()
                } else {
                    sort = SS_OPAQUE.toFloat()
                }
            }

            // anything that references _currentRender will automatically get sort = SS_POST_PROCESS
            // and coverage = MC_TRANSLUCENT
            i = 0
            while (i < numStages) {
                val pStage: shaderStage_t? = pd!!.parseStages[i]
                if (pStage!!.texture.image!![0] === Image.globalImages.currentRenderImage) {
                    if (sort != SS_PORTAL_SKY.toFloat()) {
                        sort = SS_POST_PROCESS.toFloat()
                        coverage = materialCoverage_t.MC_TRANSLUCENT
                    }
                    break
                }
                if (pStage.newStage != null) {
                    for (j in 0 until pStage.newStage!!.numFragmentProgramImages) {
                        if (pStage.newStage!!.fragmentProgramImages[j] === Image.globalImages.currentRenderImage) {
                            if (sort != SS_PORTAL_SKY.toFloat()) {
                                sort = SS_POST_PROCESS.toFloat()
                                coverage = materialCoverage_t.MC_TRANSLUCENT
                            }
                            i = numStages
                            break
                        }
                    }
                }
                i++
            }

            // set the drawStateBits depth flags
            i = 0
            while (i < numStages) {
                val pStage: shaderStage_t? = pd!!.parseStages[i]
                if (sort == SS_POST_PROCESS.toFloat()) {
                    // post-process effects fill the depth buffer as they draw, so only the
                    // topmost post-process effect is rendered
                    pStage!!.drawStateBits = pStage.drawStateBits or GLS_DEPTHFUNC_LESS
                } else if (coverage == materialCoverage_t.MC_TRANSLUCENT || pStage!!.ignoreAlphaTest) {
                    // translucent surfaces can extend past the exactly marked depth buffer
                    pStage!!.drawStateBits =
                        pStage.drawStateBits or (GLS_DEPTHFUNC_LESS or GLS_DEPTHMASK)
                } else {
                    // opaque and perforated surfaces must exactly match the depth buffer,
                    // which gets alpha test correct
                    pStage.drawStateBits =
                        pStage.drawStateBits or (GLS_DEPTHFUNC_EQUAL or GLS_DEPTHMASK)
                }
                i++
            }

            // determine if this surface will accept overlays / decals
            if (pd!!.forceOverlays) {
                // explicitly flaged in material definition
                allowOverlays = true
            } else {
                if (!IsDrawn()) {
                    allowOverlays = false
                }
                if (Coverage() != materialCoverage_t.MC_OPAQUE) {
                    allowOverlays = false
                }
                if ((GetSurfaceFlags() and SURF_NOIMPACT) != 0) {
                    allowOverlays = false
                }
            }

            // add a tiny offset to the sort orders, so that different materials
            // that have the same sort value will at least sort consistantly, instead
            // of flickering back and forth
            /* this messed up in-game guis
             if ( sort != SS_SUBVIEW ) {
             int	hash, l;

             l = name.Length();
             hash = 0;
             for ( int i = 0 ; i < l ; i++ ) {
             hash ^= name[i];
             }
             sort += hash * 0.01;
             }
             */
            if (numStages != 0) {
                stages = arrayOfNulls(numStages) // R_StaticAlloc(numStages* sizeof( stages[0] )
                //		memcpy( stages, pd.parseStages, numStages * sizeof( stages[0] ) );
                DEBUG_Parse++
                //                System.out.printf("%d-->%s\n", DEBUG_Parse, text);
                for (a in 0 until numStages) {
                    stages!![a] = pd!!.parseStages[a]
                }
            }
            if (numOps != 0) {
                ops = arrayOfNulls(numOps) // R_StaticAlloc(numOps * sizeof( ops[0] )
                //		memcpy( ops, pd.shaderOps, numOps * sizeof( ops[0] ) );
                for (a in ops!!.indices) {
                    ops!![a] = pd!!.shaderOps[a]
                }
            }
            if (numRegisters != 0) {
                expressionRegisters =
                    FloatArray(numRegisters) //R_StaticAlloc(numRegisters *sizeof( expressionRegisters[0] )
                //		memcpy( expressionRegisters, pd.shaderRegisters, numRegisters * sizeof( expressionRegisters[0] ) );
                System.arraycopy(pd!!.shaderRegisters, 0, expressionRegisters, 0, numRegisters)
            }

            // see if the registers are completely constant, and don't need to be evaluated
            // per-surface
            CheckForConstantRegisters()
            pd = null // the pointer will be invalid after exiting this function

            // finish things up
            if (TestMaterialFlag(MF_DEFAULTED)) {
                MakeDefault()
                return false
            }
            return true
        }

        override fun FreeData() {
            var i: Int
            if (stages != null) {
                // delete any idCinematic textures
                i = 0
                while (i < numStages) {
                    //TODO:for loop is unnecessary
                    if (stages!![i]!!.texture.cinematic[0] != null) {
//				delete stages[i].texture.cinematic;
                        stages!![i]!!.texture.cinematic[0]?.deconstruct()
                    }
                    if (stages!![i]!!.newStage != null) {
                        stages!![i]!!.newStage = null
                    }
                    i++
                }
                stages = null
            }
            if (expressionRegisters != null) {
//                R_StaticFree(expressionRegisters);
                expressionRegisters = null
            }
            if (constantRegisters != null) {
//                R_StaticFree(constantRegisters);
                constantRegisters = null
            }
            if (ops != null) {
//                R_StaticFree(ops);
                ops = null
            }
        }

        override fun Print() {
            var i: Int
            i = expRegister_t.EXP_REG_NUM_PREDEFINED.ordinal
            while (i < GetNumRegisters()) {
                Common.common.Printf("register %d: %f\n", i, expressionRegisters!![i])
                i++
            }
            Common.common.Printf("\n")
            i = 0
            while (i < numOps) {
                val op: expOp_t? = ops!![i]
                if (op!!.opType == expOpType_t.OP_TYPE_TABLE) {
                    Common.common.Printf(
                        "%d = %s[ %d ]\n", op.c, DeclManager.declManager.DeclByIndex(declType_t.DECL_TABLE, op.a)!!
                            .GetName(), op.b
                    )
                } else {
                    Common.common.Printf("%d = %d %s %d\n", op.c, op.a, op.opType.toString(), op.b)
                }
                i++
            }
        }

        //BSM Nerve: Added for material editor

        fun Save(fileName: String? = null /*= NULL*/): Boolean {
            return ReplaceSourceFileText()
        }

        // returns the internal image name for stage 0, which can be used
        // for the renderer CaptureRenderToImage() call
        // I'm not really sure why this needs to be virtual...
        fun ImageName(): String {
            if (numStages == 0) {
                return "_scratch"
            }
            val image: idImage? = stages!![0]!!.texture.image!![0]
            if (image != null) {
                return image.imgName.toString()
            }
            return "_scratch"
        }

        fun ReloadImages(force: Boolean) {
            for (i in 0 until numStages) {
                if (stages!![i]!!.newStage != null) {
                    for (j in 0 until stages!![i]!!.newStage!!.numFragmentProgramImages) {
                        if (stages!![i]!!.newStage!!.fragmentProgramImages[j] != null) {
                            stages!![i]!!.newStage!!.fragmentProgramImages[j]!!.Reload(false, force)
                        }
                    }
                } else if (stages!![i]!!.texture.image != null) {
                    stages!![i]!!.texture.image!![0]!!.Reload(false, force)
                }
            }
        }

        // returns number of stages this material contains
        fun GetNumStages(): Int {
            return numStages
        }

        // get a specific stage
        fun GetStage(index: Int): shaderStage_t? {
            assert((index >= 0 && index < numStages))
            return stages!![index]
        }

        // get the first bump map stage, or NULL if not present.
        // used for bumpy-specular
        fun GetBumpStage(): shaderStage_t? {
            for (i in 0 until numStages) {
                if (stages!![i]!!.lighting == stageLighting_t.SL_BUMP) {
                    return stages!![i]
                }
            }
            return null
        }

        // returns true if the material will draw anything at all.  Triggers, portals,
        // etc, will not have anything to draw.  A not drawn surface can still castShadow,
        // which can be used to make a simplified shadow hull for a complex object set
        // as noShadow
        fun IsDrawn(): Boolean {
            return ((numStages > 0) || (entityGui != 0) || (gui != null))
        }

        // returns true if the material will draw any non light interaction stages
        fun HasAmbient(): Boolean {
            return (numAmbientStages > 0)
        }

        // returns true if material has a gui
        fun HasGui(): Boolean {
            return (entityGui != 0 || gui != null)
        }

        // returns true if the material will generate another view, either as
        // a mirror or dynamic rendered image
        fun HasSubview(): Boolean {
            return hasSubview
        }

        // returns true if the material will generate shadows, not making a
        // distinction between global and no-self shadows
        fun SurfaceCastsShadow(): Boolean {
            return TestMaterialFlag(MF_FORCESHADOWS) || !TestMaterialFlag(MF_NOSHADOWS)
        }

        // returns true if the material will generate interactions with fog/blend lights
        // All non-translucent surfaces receive fog unless they are explicitly noFog
        fun ReceivesFog(): Boolean {
            return (IsDrawn() && !noFog && (coverage != materialCoverage_t.MC_TRANSLUCENT))
        }

        // returns true if the material will generate interactions with normal lights
        // Many special effect surfaces don't have any bump/diffuse/specular
        // stages, and don't interact with lights at all
        fun ReceivesLighting(): Boolean {
            return numAmbientStages != numStages
        }

        // returns true if the material should generate interactions on sides facing away
        // from light centers, as with noshadow and noselfshadow options
        fun ReceivesLightingOnBackSides(): Boolean {
            return (materialFlags and (MF_NOSELFSHADOW or MF_NOSHADOWS)) != 0
        }

        // Standard two-sided triangle rendering won't work with bump map lighting, because
        // the normal and tangent vectors won't be correct for the back sides.  When two
        // sided lighting is desired. typically for alpha tested surfaces, this is
        // addressed by having CleanupModelSurfaces() create duplicates of all the triangles
        // with apropriate order reversal.
        fun ShouldCreateBackSides(): Boolean {
            return shouldCreateBackSides
        }

        // characters and models that are created by a complete renderbump can use a faster
        // method of tangent and normal vector generation than surfaces which have a flat
        // renderbump wrapped over them.
        fun UseUnsmoothedTangents(): Boolean {
            return unsmoothedTangents
        }

        // by default, monsters can have blood overlays placed on them, but this can
        // be overrided on a per-material basis with the "noOverlays" material command.
        // This will always return false for translucent surfaces
        fun AllowOverlays(): Boolean {
            return allowOverlays
        }

        // MC_OPAQUE, MC_PERFORATED, or MC_TRANSLUCENT, for interaction list linking and
        // dmap flood filling
        // The depth buffer will not be filled for MC_TRANSLUCENT surfaces
        // FIXME: what do nodraw surfaces return?
        fun Coverage(): materialCoverage_t {
            return coverage
        }

        // returns true if this material takes precedence over other in coplanar cases
        fun HasHigherDmapPriority(other: idMaterial): Boolean {
            return ((IsDrawn() && !other.IsDrawn())
                    || (Coverage()!!.ordinal < other.Coverage()!!.ordinal))
        }

        // returns a idUserInterface if it has a global gui, or NULL if no gui
        fun GlobalGui(): idUserInterface? {
            return gui
        }

        // a discrete surface will never be merged with other surfaces by dmap, which is
        // necessary to prevent mutliple gui surfaces, mirrors, autosprites, and some other
        // special effects from being combined into a single surface
        // guis, merging sprites or other effects, mirrors and remote views are always discrete
        fun IsDiscrete(): Boolean {
            return ((entityGui != 0) || (gui != null) || (deform != deform_t.DFRM_NONE) || (sort == SS_SUBVIEW.toFloat()
                    ) || ((surfaceFlags and SURF_DISCRETE) != 0))
        }

        // Normally, dmap chops each surface by every BSP boundary, then reoptimizes.
        // For gigantic polygons like sky boxes, this can cause a huge number of planar
        // triangles that make the optimizer take forever to turn back into a single
        // triangle.  The "noFragment" option causes dmap to only break the polygons at
        // area boundaries, instead of every BSP boundary.  This has the negative effect
        // of not automatically fixing up interpenetrations, so when this is used, you
        // should manually make the edges of your sky box exactly meet, instead of poking
        // into each other.
        fun NoFragment(): Boolean {
            return (surfaceFlags and SURF_NOFRAGMENT) != 0
        }

        //------------------------------------------------------------------
        // light shader specific functions, only called for light entities
        // lightshader option to fill with fog from viewer instead of light from center
        fun IsFogLight(): Boolean {
            return fogLight
        }

        // perform simple blending of the projection, instead of interacting with bumps and textures
        fun IsBlendLight(): Boolean {
            return blendLight
        }

        // an ambient light has non-directional bump mapping and no specular
        fun IsAmbientLight(): Boolean {
            return ambientLight
        }

        // implicitly no-shadows lights (ambients, fogs, etc) will never cast shadows
        // but individual light entities can also override this value
        fun LightCastsShadows(): Boolean {
            return (TestMaterialFlag(MF_FORCESHADOWS)
                    || (!fogLight && !ambientLight && !blendLight && !TestMaterialFlag(MF_NOSHADOWS)))
        }

        // fog lights, blend lights, ambient lights, etc will all have to have interaction
        // triangles generated for sides facing away from the light as well as those
        // facing towards the light.  It is debatable if noshadow lights should effect back
        // sides, making everything "noSelfShadow", but that would make noshadow lights
        // potentially slower than normal lights, which detracts from their optimization
        // ability, so they currently do not.
        fun LightEffectsBackSides(): Boolean {
            return fogLight || ambientLight || blendLight
        }

        // NULL unless an image is explicitly specified in the shader with "lightFalloffShader <image>"
        fun LightFalloffImage(): idImage? {
            return lightFalloffImage
        }

        //------------------------------------------------------------------
        // returns the renderbump command line for this shader, or an empty string if not present
        fun GetRenderBump(): String {
            return renderBump.toString()
        }

        // set specific material flag(s)
        fun SetMaterialFlag(flag: Int) {
            materialFlags = materialFlags or flag
        }

        // clear specific material flag(s)
        fun ClearMaterialFlag(flag: Int) {
            materialFlags = materialFlags and flag.inv()
        }

        // test for existance of specific material flag(s)
        fun TestMaterialFlag(flag: Int): Boolean {
            return (materialFlags and flag) != 0
        }

        // get content flags
        fun GetContentFlags(): Int {
            return contentFlags
        }

        // get surface flags
        fun GetSurfaceFlags(): Int {
            return surfaceFlags
        }

        // gets name for surface type (stone, metal, flesh, etc.)
        fun GetSurfaceType(): surfTypes_t {
            return surfTypes_t.entries[surfaceFlags and SURF_TYPE_MASK]
        }

        // get material description
        fun GetDescription(): String {
            return desc.toString()
        }

        // get sort order
        fun GetSort(): Float {
            return sort
        }

        // this is only used by the gui system to force sorting order
        // on images referenced from tga's instead of materials.
        // this is done this way as there are 2000 tgas the guis use
        fun SetSort(s: Float) {
            sort = s
        }

        // DFRM_NONE, DFRM_SPRITE, etc
        fun Deform(): deform_t? {
            return deform
        }

        // flare size, expansion size, etc
        fun GetDeformRegister(index: Int): Int {
            return deformRegisters[index]
        }

        // particle system to emit from surface and table for turbulent
        fun GetDeformDecl(): idDecl? {
            return deformDecl
        }

        // currently a surface can only have one unique texgen for all the stages
        fun Texgen(): texgen_t {
            if (stages != null) {
                for (i in 0 until numStages) {
                    if (stages!![i]!!.texture.texgen != texgen_t.TG_EXPLICIT) {
                        return stages!![i]!!.texture.texgen
                    }
                }
            }
            return texgen_t.TG_EXPLICIT
        }

        // wobble sky parms
        fun GetTexGenRegisters(): IntArray {
            return texGenRegisters
        }

        // get cull type
        fun GetCullType(): cullType_t {
            return cullType
        }

        fun GetEditorAlpha(): Float {
            return editorAlpha
        }

        fun GetEntityGui(): Int {
            return entityGui
        }

        fun GetDecalInfo(): decalInfo_t {
            return decalInfo
        }

        //
        //	//------------------------------------------------------------------
        //
        // spectrums are used for "invisible writing" that can only be
        // illuminated by a light of matching spectrum
        fun Spectrum(): Int {
            return spectrum
        }

        fun GetPolygonOffset(): Float {
            return polygonOffset
        }

        fun GetSurfaceArea(): Float {
            return surfaceArea
        }

        fun AddToSurfaceArea(area: Float) {
            surfaceArea += area
        }

        //------------------------------------------------------------------
        // returns the length, in milliseconds, of the videoMap on this material,
        // or zero if it doesn't have one
        fun CinematicLength(): Int {
            if (stages == null || stages!![0]!!.texture.cinematic[0] == null) {
                return 0
            }
            return stages!![0]!!.texture.cinematic[0]!!.AnimationLength()
        }

        //------------------------------------------------------------------
        fun CloseCinematic() {
            for (i in 0 until numStages) {
                if (stages!![i]!!.texture.cinematic[0] != null) {
                    stages!![i]!!.texture.cinematic[0]!!.Close()
                    //			delete stages[i].texture.cinematic;
                    stages!![i]!!.texture.cinematic[0] = null
                }
            }
        }

        fun ResetCinematicTime(time: Int) {
            for (i in 0 until numStages) {
                if (stages!![i]!!.texture.cinematic[0] != null) {
                    stages!![i]!!.texture.cinematic[0]!!.ResetTime(time)
                }
            }
        }

        fun UpdateCinematic(time: Int) {
            if (stages == null || stages!![0]!!.texture.cinematic[0] == null || backEnd!!.viewDef == null) {
                return
            }
            stages!![0]!!.texture.cinematic[0]!!.ImageForTime(tr.primaryRenderView!!.time)
        }

        // gets an image for the editor to use
        fun GetEditorImage(): idImage? {
            if (editorImage != null) {
                return editorImage
            }

            // if we don't have an editorImageName, use the first stage image
            if (0 == editorImageName!!.Length()) {
                // _D3XP :: First check for a diffuse image, then use the first
                if (numStages != 0 && stages != null) {
                    var i: Int
                    i = 0
                    while (i < numStages) {
                        if (stages!![i]!!.lighting == stageLighting_t.SL_DIFFUSE) {
                            editorImage = stages!![i]!!.texture.image!![0]
                            break
                        }
                        i++
                    }
                    if (null == editorImage) {
                        editorImage = stages!![0]!!.texture.image!![0]
                    }
                } else {
                    editorImage = Image.globalImages.defaultImage
                }
            } else {
                // look for an explicit one
                editorImage = Image.globalImages.ImageFromFile(
                    editorImageName.toString(),
                    textureFilter_t.TF_DEFAULT,
                    true,
                    textureRepeat_t.TR_REPEAT,
                    textureDepth_t.TD_DEFAULT
                )
            }
            if (null == editorImage) {
                editorImage = Image.globalImages.defaultImage
            }
            return editorImage
        }

        fun GetImageWidth(): Int {
            assert((GetStage(0) != null && GetStage(0)!!.texture.image!![0] != null))
            return GetStage(0)!!.texture.image!![0]!!.uploadWidth._val
        }

        fun GetImageHeight(): Int {
            assert((GetStage(0) != null && GetStage(0)!!.texture.image!![0] != null))
            return GetStage(0)!!.texture.image!![0]!!.uploadHeight._val
        }

        fun SetGui(_gui: String?) {
            gui = uiManager.FindGui(_gui, true, false, true)
        }

        /*
         ===================
         idMaterial::SetImageClassifications

         Just for image resource tracking.
         ===================
         */
        fun SetImageClassifications(tag: Int) {
            for (i in 0 until numStages) {
                val image: idImage? = stages!![i]!!.texture.image!![0]
                if (image != null) {
                    image.SetClassification(tag)
                }
            }
        }

        // returns number of registers this material contains
        fun GetNumRegisters(): Int {
            return numRegisters
        }

        /*
         ===============
         idMaterial::EvaluateRegisters

         Parameters are taken from the localSpace and the renderView,
         then all expressions are evaluated, leaving the material registers
         set to their apropriate values.
         ===============
         */
        // regs should point to a float array large enough to hold GetNumRegisters() floats
        fun EvaluateRegisters(
            regs: FloatArray, shaderParms: FloatArray /*[MAX_ENTITY_SHADER_PARMS]*/,
            view: viewDef_s, soundEmitter: idSoundEmitter? /*= NULL*/
        ) {
            var i: Int
            var b: Int
            /*expOp_t*/
            var op: Int

            // copy the material constants
            i = etoi(expRegister_t.EXP_REG_NUM_PREDEFINED)
            while (i < numRegisters) {
                regs[i] = expressionRegisters!![i]
                i++
            }

            // copy the local and global parameters
            regs[etoi(expRegister_t.EXP_REG_TIME)] = view.floatTime
            regs[etoi(expRegister_t.EXP_REG_PARM0)] = shaderParms[0]
            regs[etoi(expRegister_t.EXP_REG_PARM1)] = shaderParms[1]
            regs[etoi(expRegister_t.EXP_REG_PARM2)] = shaderParms[2]
            regs[etoi(expRegister_t.EXP_REG_PARM3)] = shaderParms[3]
            regs[etoi(expRegister_t.EXP_REG_PARM4)] = shaderParms[4]
            regs[etoi(expRegister_t.EXP_REG_PARM5)] = shaderParms[5]
            regs[etoi(expRegister_t.EXP_REG_PARM6)] = shaderParms[6]
            regs[etoi(expRegister_t.EXP_REG_PARM7)] = shaderParms[7]
            regs[etoi(expRegister_t.EXP_REG_PARM8)] = shaderParms[8]
            regs[etoi(expRegister_t.EXP_REG_PARM9)] = shaderParms[9]
            regs[etoi(expRegister_t.EXP_REG_PARM10)] = shaderParms[10]
            regs[etoi(expRegister_t.EXP_REG_PARM11)] = shaderParms[11]
            regs[etoi(expRegister_t.EXP_REG_GLOBAL0)] = view.renderView.shaderParms.get(0)
            regs[etoi(expRegister_t.EXP_REG_GLOBAL1)] = view.renderView.shaderParms.get(1)
            regs[etoi(expRegister_t.EXP_REG_GLOBAL2)] = view.renderView.shaderParms.get(2)
            regs[etoi(expRegister_t.EXP_REG_GLOBAL3)] = view.renderView.shaderParms.get(3)
            regs[etoi(expRegister_t.EXP_REG_GLOBAL4)] = view.renderView.shaderParms.get(4)
            regs[etoi(expRegister_t.EXP_REG_GLOBAL5)] = view.renderView.shaderParms.get(5)
            regs[etoi(expRegister_t.EXP_REG_GLOBAL6)] = view.renderView.shaderParms.get(6)
            regs[etoi(expRegister_t.EXP_REG_GLOBAL7)] = view.renderView.shaderParms.get(7)
            op = 0 // = ops;
            i = 0
            while (i < numOps) {
                when (ops!![op]!!.opType) {
                    expOpType_t.OP_TYPE_ADD -> regs[ops!![op]!!.c] = regs[ops!![op]!!.a] + regs[ops!![op]!!.b]

                    expOpType_t.OP_TYPE_SUBTRACT -> regs[ops!![op]!!.c] =
                        regs[ops!![op]!!.a] - regs[ops!![op]!!.b]

                    expOpType_t.OP_TYPE_MULTIPLY -> regs[ops!![op]!!.c] =
                        regs[ops!![op]!!.a] * regs[ops!![op]!!.b]

                    expOpType_t.OP_TYPE_DIVIDE -> regs[ops!![op]!!.c] = regs[ops!![op]!!.a] / regs[ops!![op]!!.b]

                    expOpType_t.OP_TYPE_MOD -> {
                        b = regs[ops!![op]!!.b].toInt()
                        b = if (b != 0) b else 1
                        regs[ops!![op]!!.c] = (regs[ops!![op]!!.a].toInt() % b).toFloat()
                    }

                    expOpType_t.OP_TYPE_TABLE -> {
                        val table: idDeclTable? = (DeclManager.declManager.DeclByIndex(
                            declType_t.DECL_TABLE,
                            ops!![op]!!.a
                        )) as idDeclTable?
                        regs[ops!![op]!!.c] = table!!.TableLookup(regs[ops!![op]!!.b])
                    }

                    expOpType_t.OP_TYPE_SOUND -> if (soundEmitter != null) {
                        regs[ops!![op]!!.c] = soundEmitter.CurrentAmplitude()
                    } else {
                        regs[ops!![op]!!.c] = 0.0f
                    }

                    expOpType_t.OP_TYPE_GT -> regs[ops!![op]!!.c] = (if (regs[ops!![op]!!.a] > regs[ops!![op]!!.b]
                    ) 1 else 0).toFloat()

                    expOpType_t.OP_TYPE_GE -> regs[ops!![op]!!.c] =
                        (if (regs[ops!![op]!!.a] >= regs[ops!![op]!!.b]
                        ) 1 else 0).toFloat()

                    expOpType_t.OP_TYPE_LT -> regs[ops!![op]!!.c] = (if (regs[ops!![op]!!.a] < regs[ops!![op]!!.b]
                    ) 1 else 0).toFloat()

                    expOpType_t.OP_TYPE_LE -> regs[ops!![op]!!.c] =
                        (if (regs[ops!![op]!!.a] <= regs[ops!![op]!!.b]
                        ) 1 else 0).toFloat()

                    expOpType_t.OP_TYPE_EQ -> regs[ops!![op]!!.c] =
                        (if (regs[ops!![op]!!.a] == regs[ops!![op]!!.b]
                        ) 1 else 0).toFloat()

                    expOpType_t.OP_TYPE_NE -> regs[ops!![op]!!.c] =
                        (if (regs[ops!![op]!!.a] != regs[ops!![op]!!.b]
                        ) 1 else 0).toFloat()

                    expOpType_t.OP_TYPE_AND -> regs[ops!![op]!!.c] =
                        (if ((regs[ops!![op]!!.a] != 0.0f && regs[ops!![op]!!.b] != 0.0f)
                        ) 1 else 0).toFloat()

                    expOpType_t.OP_TYPE_OR -> regs[ops!![op]!!.c] =
                        (if ((regs[ops!![op]!!.a] != 0.0f || regs[ops!![op]!!.b] != 0.0f)
                        ) 1 else 0).toFloat()

                    else -> Common.common.FatalError("R_EvaluateExpression: bad opcode")
                }
                i++
                op++
            }
        }

        // if a material only uses constants (no entityParm or globalparm references), this
        // will return a pointer to an internal table, and EvaluateRegisters will not need
        // to be called.  If NULL is returned, EvaluateRegisters must be used.
        fun ConstantRegisters(): FloatArray? {
            if (!r_useConstantMaterials!!.GetBool()) {
                return null
            }
            return constantRegisters
        }

        fun SuppressInSubview(): Boolean {
            return suppressInSubview
        }

        fun IsPortalSky(): Boolean {
            return portalSky
        }

        fun AddReference() {
            refCount++
            for (i in 0 until numStages) {
                val s: shaderStage_t? = stages!![i]
                if (s!!.texture.image!![0] != null) {
                    s.texture.image[0]!!.AddReference()
                }
            }
        }

        // parse the entire material
        private fun CommonInit() {
            desc = idStr("<none>")
            renderBump = idStr("")
            contentFlags = CONTENTS_SOLID
            surfaceFlags = etoi(surfTypes_t.SURFTYPE_NONE)
            materialFlags = 0
            sort = SS_BAD.toFloat()
            coverage = materialCoverage_t.MC_BAD
            cullType = cullType_t.CT_FRONT_SIDED
            deform = deform_t.DFRM_NONE
            numOps = 0
            ops = null
            numRegisters = 0
            expressionRegisters = null
            constantRegisters = null
            numStages = 0
            numAmbientStages = 0
            stages = null
            editorImage = null
            lightFalloffImage = null
            shouldCreateBackSides = false
            entityGui = 0
            fogLight = false
            blendLight = false
            ambientLight = false
            noFog = false
            hasSubview = false
            allowOverlays = true
            unsmoothedTangents = false
            gui = null
            //	memset( deformRegisters, 0, sizeof( deformRegisters ) );
            Arrays.fill(deformRegisters, 0)
            editorAlpha = 1.0f
            spectrum = 0
            polygonOffset = 0.0f
            suppressInSubview = false
            refCount = 0
            portalSky = false
            decalInfo.stayTime = 10000
            decalInfo.fadeTime = 4000
            decalInfo.start[0] = 1.0f
            decalInfo.start[1] = 1.0f
            decalInfo.start[2] = 1.0f
            decalInfo.start[3] = 1.0f
            decalInfo.end[0] = 0.0f
            decalInfo.end[1] = 0.0f
            decalInfo.end[2] = 0.0f
            decalInfo.end[3] = 0.0f
        }

        /*
         =================
         idMaterial::ParseMaterial

         The current text pointer is at the explicit text definition of the
         Parse it into the global material variable. Later functions will optimize it.

         If there is any error during parsing, defaultShader will be set.
         =================
         */
        private fun ParseMaterial(src: idLexer) {
            val token = idToken()
            val s: Int
            val buffer = CharArray(1024)
            var str: String?
            val newSrc = idLexer()
            var i: Int
            s = 0
            numOps = 0
            numRegisters = expRegister_t.EXP_REG_NUM_PREDEFINED.ordinal // leave space for the parms to be copied in
            i = 0
            while (i < numRegisters) {
                pd!!.registerIsTemporary[i] = true // they aren't constants that can be folded
                i++
            }
            numStages = 0
            var trpDefault: textureRepeat_t = textureRepeat_t.TR_REPEAT // allow a global setting for repeat
            while (true) {
                if (TestMaterialFlag(MF_DEFAULTED)) { // we have a parse error
                    return
                }
                if (!src.ExpectAnyToken(token)) {
                    SetMaterialFlag(MF_DEFAULTED)
                    return
                }

                // end of material definition
                if (token.equals("}")) {
                    break
                } else if (0 == token.Icmp("qer_editorimage")) {
                    src.ReadTokenOnLine(token)
                    editorImageName = idStr(token.toString())
                    src.SkipRestOfLine()
                    continue
                } // description
                else if (0 == token.Icmp("description")) {
                    src.ReadTokenOnLine(token)
                    desc = idStr(token.toString())
                    continue
                } // check for the surface / content bit flags
                else if (CheckSurfaceParm(token)) {
                    continue
                } // polygonOffset
                else if (0 == token.Icmp("polygonOffset")) {
                    SetMaterialFlag(MF_POLYGONOFFSET)
                    if (!src.ReadTokenOnLine(token)) {
                        polygonOffset = 1.0f
                        continue
                    }
                    // explict larger (or negative) offset
                    polygonOffset = token.GetFloatValue()
                    continue
                } // noshadow
                else if (0 == token.Icmp("noShadows")) {
                    SetMaterialFlag(MF_NOSHADOWS)
                    continue
                } else if (0 == token.Icmp("suppressInSubview")) {
                    suppressInSubview = true
                    continue
                } else if (0 == token.Icmp("portalSky")) {
                    portalSky = true
                    continue
                } // noSelfShadow
                else if (0 == token.Icmp("noSelfShadow")) {
                    SetMaterialFlag(MF_NOSELFSHADOW)
                    continue
                } // noPortalFog
                else if (0 == token.Icmp("noPortalFog")) {
                    SetMaterialFlag(MF_NOPORTALFOG)
                    continue
                } // forceShadows allows nodraw surfaces to cast shadows
                else if (0 == token.Icmp("forceShadows")) {
                    SetMaterialFlag(MF_FORCESHADOWS)
                    continue
                } // overlay / decal suppression
                else if (0 == token.Icmp("noOverlays")) {
                    allowOverlays = false
                    continue
                } // moster blood overlay forcing for alpha tested or translucent surfaces
                else if (0 == token.Icmp("forceOverlays")) {
                    pd!!.forceOverlays = true
                    continue
                } // translucent
                else if (0 == token.Icmp("translucent")) {
                    coverage = materialCoverage_t.MC_TRANSLUCENT
                    continue
                } // global zero clamp
                else if (0 == token.Icmp("zeroclamp")) {
                    trpDefault = textureRepeat_t.TR_CLAMP_TO_ZERO
                    continue
                } // global clamp
                else if (0 == token.Icmp("clamp")) {
                    trpDefault = textureRepeat_t.TR_CLAMP
                    continue
                } // global clamp
                else if (0 == token.Icmp("alphazeroclamp")) {
                    trpDefault = textureRepeat_t.TR_CLAMP_TO_ZERO
                    continue
                } // forceOpaque is used for skies-behind-windows
                else if (0 == token.Icmp("forceOpaque")) {
                    coverage = materialCoverage_t.MC_OPAQUE
                    continue
                } // twoSided
                else if (0 == token.Icmp("twoSided")) {
                    cullType = cullType_t.CT_TWO_SIDED
                    // twoSided implies no-shadows, because the shadow
                    // volume would be coplanar with the surface, giving depth fighting
                    // we could make this no-self-shadows, but it may be more important
                    // to receive shadows from no-self-shadow monsters
                    SetMaterialFlag(MF_NOSHADOWS)
                } // backSided
                else if (0 == token.Icmp("backSided")) {
                    cullType = cullType_t.CT_BACK_SIDED
                    // the shadow code doesn't handle this, so just disable shadows.
                    // We could fix this in the future if there was a need.
                    SetMaterialFlag(MF_NOSHADOWS)
                } // foglight
                else if (0 == token.Icmp("fogLight")) {
                    fogLight = true
                    continue
                } // blendlight
                else if (0 == token.Icmp("blendLight")) {
                    blendLight = true
                    continue
                } // ambientLight
                else if (0 == token.Icmp("ambientLight")) {
                    ambientLight = true
                    continue
                } // mirror
                else if (0 == token.Icmp("mirror")) {
                    sort = SS_SUBVIEW.toFloat()
                    coverage = materialCoverage_t.MC_OPAQUE
                    continue
                } // noFog
                else if (0 == token.Icmp("noFog")) {
                    noFog = true
                    continue
                } // unsmoothedTangents
                else if (0 == token.Icmp("unsmoothedTangents")) {
                    unsmoothedTangents = true
                    continue
                } // lightFallofImage <imageprogram>
                else if (0 == token.Icmp("lightFalloffImage")) {
                    str = Image_program.R_ParsePastImageProgram(src)
                    var copy: String?
                    copy = str // so other things don't step on it
                    lightFalloffImage = Image.globalImages.ImageFromFile(
                        copy,
                        textureFilter_t.TF_DEFAULT,
                        false,
                        textureRepeat_t.TR_CLAMP,
                        textureDepth_t.TD_DEFAULT
                    )
                    continue
                } // guisurf <guifile> | guisurf entity
                else if (0 == token.Icmp("guisurf")) {
                    src.ReadTokenOnLine(token)
                    if (0 == token.Icmp("entity")) {
                        entityGui = 1
                    } else if (0 == token.Icmp("entity2")) {
                        entityGui = 2
                    } else if (0 == token.Icmp("entity3")) {
                        entityGui = 3
                    } else {
                        gui = uiManager.FindGui(token.toString(), true)
                    }
                    continue
                } // sort
                else if (0 == token.Icmp("sort")) {
                    ParseSort(src)
                    continue
                } // spectrum <integer>
                else if (0 == token.Icmp("spectrum")) {
                    src.ReadTokenOnLine(token)
                    spectrum = atoi(token.toString())
                    continue
                } // deform < sprite | tube | flare >
                else if (0 == token.Icmp("deform")) {
                    ParseDeform(src)
                    continue
                } // decalInfo <staySeconds> <fadeSeconds> ( <start rgb> ) ( <end rgb> )
                else if (0 == token.Icmp("decalInfo")) {
                    ParseDecalInfo(src)
                    continue
                } // renderbump <args...>
                else if (0 == token.Icmp("renderbump")) {
                    src.ParseRestOfLine((renderBump)!!)
                    continue
                } // diffusemap for stage shortcut
                else if (0 == token.Icmp("diffusemap")) {
                    str = Image_program.R_ParsePastImageProgram(src)
                    snPrintf(buffer, buffer.size, "blend diffusemap\nmap %s\n}\n", str)
                    newSrc.LoadMemory(ctos(buffer), strLen(buffer), "diffusemap")
                    newSrc.SetFlags(LEXFL_NOFATALERRORS or LEXFL_NOSTRINGCONCAT or LEXFL_NOSTRINGESCAPECHARS or LEXFL_ALLOWPATHNAMES)
                    ParseStage(newSrc, trpDefault)
                    newSrc.FreeSource()
                    continue
                } // specularmap for stage shortcut
                else if (0 == token.Icmp("specularmap")) {
                    str = Image_program.R_ParsePastImageProgram(src)
                    snPrintf(buffer, buffer.size, "blend specularmap\nmap %s\n}\n", str)
                    newSrc.LoadMemory(ctos(buffer), strLen(buffer), "specularmap")
                    newSrc.SetFlags(LEXFL_NOFATALERRORS or LEXFL_NOSTRINGCONCAT or LEXFL_NOSTRINGESCAPECHARS or LEXFL_ALLOWPATHNAMES)
                    ParseStage(newSrc, trpDefault)
                    newSrc.FreeSource()
                    continue
                } // normalmap for stage shortcut
                else if (0 == token.Icmp("bumpmap")) {
                    str = Image_program.R_ParsePastImageProgram(src)
                    snPrintf(buffer, buffer.size, "blend bumpmap\nmap %s\n}\n", str)
                    newSrc.LoadMemory(ctos(buffer), strLen(buffer), "bumpmap")
                    newSrc.SetFlags(LEXFL_NOFATALERRORS or LEXFL_NOSTRINGCONCAT or LEXFL_NOSTRINGESCAPECHARS or LEXFL_ALLOWPATHNAMES)
                    ParseStage(newSrc, trpDefault)
                    newSrc.FreeSource()
                    continue
                } // DECAL_MACRO for backwards compatibility with the preprocessor macros
                else if (0 == token.Icmp("DECAL_MACRO")) {
                    // polygonOffset
                    SetMaterialFlag(MF_POLYGONOFFSET)
                    polygonOffset = 1.0f

                    // discrete
                    surfaceFlags = surfaceFlags or SURF_DISCRETE
                    contentFlags = contentFlags and CONTENTS_SOLID.inv()

                    // sort decal
                    sort = SS_DECAL.toFloat()

                    // noShadows
                    SetMaterialFlag(MF_NOSHADOWS)
                    continue
                } else if (token.equals("{")) {
                    // create the new stage
                    DBG_ParseStage++
                    if (DBG_ParseStage == 41) { //
//                        continue;
                    }
                    ParseStage(src, trpDefault)
                    continue
                } else {
                    Common.common.Warning(
                        "unknown general material parameter '%s' in '%s'",
                        token.toString(),
                        GetName()
                    )
                    SetMaterialFlag(MF_DEFAULTED)
                    return
                }
            }

            // add _flat or _white stages if needed
            AddImplicitStages()

            // order the diffuse / bump / specular stages properly
            SortInteractionStages()

            // if we need to do anything with normals (lighting or environment mapping)
            // and two sided lighting was asked for, flag
            // shouldCreateBackSides() and change culling back to single sided,
            // so we get proper tangent vectors on both sides
            // we can't just call ReceivesLighting(), because the stages are still
            // in temporary form
            if (cullType == cullType_t.CT_TWO_SIDED) {
                i = 0
                while (i < numStages) {
                    if (pd!!.parseStages[i]!!.lighting != stageLighting_t.SL_AMBIENT || pd!!.parseStages[i]!!.texture.texgen != texgen_t.TG_EXPLICIT) {
                        if (cullType == cullType_t.CT_TWO_SIDED) {
                            cullType = cullType_t.CT_FRONT_SIDED
                            shouldCreateBackSides = true
                        }
                        break
                    }
                    i++
                }
            }

            // currently a surface can only have one unique texgen for all the stages on old hardware
            var firstGen: texgen_t = texgen_t.TG_EXPLICIT
            i = 0
            while (i < numStages) {
                if (pd!!.parseStages[i]!!.texture.texgen != texgen_t.TG_EXPLICIT) {
                    if (firstGen == texgen_t.TG_EXPLICIT) {
                        firstGen = pd!!.parseStages[i]!!.texture.texgen
                    } else if (firstGen != pd!!.parseStages[i]!!.texture.texgen) {
                        Common.common.Warning("material '%s' has multiple stages with a texgen", GetName())
                        break
                    }
                }
                i++
            }
        }

        /*
         ===============
         idMaterial::MatchToken

         Sets defaultShader and returns false if the next token doesn't match
         ===============
         */
        private fun MatchToken(src: idLexer, match: String): Boolean {
            if (!src.ExpectTokenString(match)) {
                SetMaterialFlag(MF_DEFAULTED)
                return false
            }
            return true
        }

        private fun ParseSort(src: idLexer) {
            val token = idToken()
            if (!src.ReadTokenOnLine(token)) {
                src.Warning("missing sort parameter")
                SetMaterialFlag(MF_DEFAULTED)
                return
            }
            if (0 == token.Icmp("subview")) {
                sort = SS_SUBVIEW.toFloat()
            } else if (0 == token.Icmp("opaque")) {
                sort = SS_OPAQUE.toFloat()
            } else if (0 == token.Icmp("decal")) {
                sort = SS_DECAL.toFloat()
            } else if (0 == token.Icmp("far")) {
                sort = SS_FAR.toFloat()
            } else if (0 == token.Icmp("medium")) {
                sort = SS_MEDIUM.toFloat()
            } else if (0 == token.Icmp("close")) {
                sort = SS_CLOSE.toFloat()
            } else if (0 == token.Icmp("almostNearest")) {
                sort = SS_ALMOST_NEAREST.toFloat()
            } else if (0 == token.Icmp("nearest")) {
                sort = SS_NEAREST.toFloat()
            } else if (0 == token.Icmp("postProcess")) {
                sort = SS_POST_PROCESS.toFloat()
            } else if (0 == token.Icmp("portalSky")) {
                sort = SS_PORTAL_SKY.toFloat()
            } else {
                sort = token.toString().toFloat()
            }
        }

        private fun ParseBlend(src: idLexer, stage: shaderStage_t?) {
            val token = idToken()
            val srcBlend: Int
            val dstBlend: Int

//            System.out.printf("ParseBlend(%d)\n", DBG_ParseBlend++);
            if (!src.ReadToken(token)) {
                return
            }

            // blending combinations
            if (0 == token.Icmp("blend")) {
                DBG_ParseBlend++
                stage!!.drawStateBits = GLS_SRCBLEND_SRC_ALPHA or GLS_DSTBLEND_ONE_MINUS_SRC_ALPHA
                return
            }
            if (0 == token.Icmp("add")) {
                stage!!.drawStateBits = GLS_SRCBLEND_ONE or GLS_DSTBLEND_ONE
                return
            }
            if (0 == token.Icmp("filter") || 0 == token.Icmp("modulate")) {
                stage!!.drawStateBits = GLS_SRCBLEND_DST_COLOR or GLS_DSTBLEND_ZERO
                return
            }
            if (0 == token.Icmp("none")) {
                // none is used when defining an alpha mask that doesn't draw
                stage!!.drawStateBits = GLS_SRCBLEND_ZERO or GLS_DSTBLEND_ONE
                return
            }
            if (0 == token.Icmp("bumpmap")) {
                stage!!.lighting = stageLighting_t.SL_BUMP
                return
            }
            if (0 == token.Icmp("diffusemap")) {
                stage!!.lighting = stageLighting_t.SL_DIFFUSE
                return
            }
            if (0 == token.Icmp("specularmap")) {
                stage!!.lighting = stageLighting_t.SL_SPECULAR
                return
            }
            srcBlend = NameToSrcBlendMode(token)
            MatchToken(src, ",")
            if (!src.ReadToken(token)) {
                return
            }
            dstBlend = NameToDstBlendMode(token)
            stage!!.drawStateBits = srcBlend or dstBlend
        }

        /*
         ================
         idMaterial::ParseVertexParm

         If there is a single value, it will be repeated across all elements
         If there are two values, 3 = 0.0f, 4 = 1.0f
         if there are three values, 4 = 1.0f
         ================
         */
        private fun ParseVertexParm(src: idLexer, newStage: newShaderStage_t) {
            val token = idToken()
            src.ReadTokenOnLine(token)
            val parm: Int = token.GetIntValue()
            if (!token.IsNumeric() || (parm < 0) || (parm >= MAX_VERTEX_PARMS)) {
                Common.common.Warning("bad vertexParm number\n")
                SetMaterialFlag(MF_DEFAULTED)
                return
            }
            if (parm >= newStage.numVertexParms) {
                newStage.numVertexParms = parm + 1
            }
            newStage.vertexParms[parm]!![0] = ParseExpression(src)
            src.ReadTokenOnLine(token)
            if (token.IsEmpty() || token.Icmp(",") != 0) {
                newStage.vertexParms[parm]!![3] = newStage.vertexParms[parm]!![0]
                newStage.vertexParms[parm]!![2] = newStage.vertexParms[parm]!![3]
                newStage.vertexParms[parm]!![1] = newStage.vertexParms[parm]!![2]
                return
            }
            newStage.vertexParms[parm]!![1] = ParseExpression(src)
            src.ReadTokenOnLine(token)
            if (token.IsEmpty() || token.Icmp(",") != 0) {
                newStage.vertexParms[parm]!![2] = GetExpressionConstant(0.0f)
                newStage.vertexParms[parm]!![3] = GetExpressionConstant(1.0f)
                return
            }
            newStage.vertexParms[parm]!![2] = ParseExpression(src)
            src.ReadTokenOnLine(token)
            if (token.IsEmpty() || token.Icmp(",") != 0) {
                newStage.vertexParms[parm]!![3] = GetExpressionConstant(1.0f)
                return
            }
            newStage.vertexParms[parm]!![3] = ParseExpression(src)
        }

        private fun ParseFragmentMap(src: idLexer, newStage: newShaderStage_t) {
            val str: String?
            var tf: textureFilter_t
            var trp: textureRepeat_t
            var td: textureDepth_t
            var cubeMap: cubeFiles_t
            var allowPicmip: Boolean
            val token = idToken()
            tf = textureFilter_t.TF_DEFAULT
            trp = textureRepeat_t.TR_REPEAT
            td = textureDepth_t.TD_DEFAULT
            allowPicmip = true
            cubeMap = cubeFiles_t.CF_2D
            src.ReadTokenOnLine(token)
            val unit: Int = token.GetIntValue()
            if (!token.IsNumeric() || (unit < 0) || (unit >= MAX_FRAGMENT_IMAGES)) {
                Common.common.Warning("bad fragmentMap number\n")
                SetMaterialFlag(MF_DEFAULTED)
                return
            }

            // unit 1 is the normal map.. make sure it gets flagged as the proper depth
            if (unit == 1) {
                td = textureDepth_t.TD_BUMP
            }
            if (unit >= newStage.numFragmentProgramImages) {
                newStage.numFragmentProgramImages = unit + 1
            }
            while (true) {
                src.ReadTokenOnLine(token)
                if (0 == token.Icmp("cubeMap")) {
                    cubeMap = cubeFiles_t.CF_NATIVE
                    continue
                }
                if (0 == token.Icmp("cameraCubeMap")) {
                    cubeMap = cubeFiles_t.CF_CAMERA
                    continue
                }
                if (0 == token.Icmp("nearest")) {
                    tf = textureFilter_t.TF_NEAREST
                    continue
                }
                if (0 == token.Icmp("linear")) {
                    tf = textureFilter_t.TF_LINEAR
                    continue
                }
                if (0 == token.Icmp("clamp")) {
                    trp = textureRepeat_t.TR_CLAMP
                    continue
                }
                if (0 == token.Icmp("noclamp")) {
                    trp = textureRepeat_t.TR_REPEAT
                    continue
                }
                if (0 == token.Icmp("zeroclamp")) {
                    trp = textureRepeat_t.TR_CLAMP_TO_ZERO
                    continue
                }
                if (0 == token.Icmp("alphazeroclamp")) {
                    trp = textureRepeat_t.TR_CLAMP_TO_ZERO_ALPHA
                    continue
                }
                if (0 == token.Icmp("forceHighQuality")) {
                    td = textureDepth_t.TD_HIGH_QUALITY
                    continue
                }
                if (0 == token.Icmp("uncompressed") || 0 == token.Icmp("highquality")) {
                    if (0 == idImageManager.image_ignoreHighQuality.GetInteger()) {
                        td = textureDepth_t.TD_HIGH_QUALITY
                    }
                    continue
                }
                if (0 == token.Icmp("nopicmip")) {
                    allowPicmip = false
                    continue
                }

                // assume anything else is the image name
                src.UnreadToken(token)
                break
            }
            str = Image_program.R_ParsePastImageProgram(src)
            newStage.fragmentProgramImages[unit] =
                Image.globalImages.ImageFromFile(str, tf, allowPicmip, trp, td, cubeMap)
            if (null == newStage.fragmentProgramImages[unit]) {
                newStage.fragmentProgramImages[unit] = Image.globalImages.defaultImage
            }
        }

        private fun ParseStage(src: idLexer, trpDefault: textureRepeat_t = textureRepeat_t.TR_REPEAT /*= TR_REPEAT */) {
            DEBUG_imageName++
            val token = idToken()
            var str: String?
            val ss: shaderStage_t?
            val ts: textureStage_t
            var tf: textureFilter_t
            var trp: textureRepeat_t
            var td: textureDepth_t
            var cubeMap: cubeFiles_t
            var allowPicmip: Boolean
            val imageName = CharArray(Image.MAX_IMAGE_NAME)
            var a: Int
            var b: Int
            val matrix: Array<IntArray> = Array(2, { IntArray(3) })
            val newStage = newShaderStage_t()
            if (numStages >= MAX_SHADER_STAGES) {
                SetMaterialFlag(MF_DEFAULTED)
                Common.common.Warning("material '%s' exceeded %d stages", GetName(), MAX_SHADER_STAGES)
            }
            tf = textureFilter_t.TF_DEFAULT
            trp = trpDefault
            td = textureDepth_t.TD_DEFAULT
            allowPicmip = true
            cubeMap = cubeFiles_t.CF_2D
            imageName[0] = 0.toChar()

//	memset( &newStage, 0, sizeof( newStage ) );
            ss = pd!!.parseStages[numStages]
            ts = ss!!.texture
            ClearStage(ss)
            var asdasdasdasd = 0
            if (DBG_ParseStage == 41) {
                asdasdasdasd = 0
            }
            while (true) {
                if (TestMaterialFlag(MF_DEFAULTED)) {    // we have a parse error
                    return
                }
                if (!src.ExpectAnyToken(token)) {
                    SetMaterialFlag(MF_DEFAULTED)
                    return
                }

                // the close brace for the entire material ends the draw block
                if (token.equals("}")) {
                    break
                }

                //BSM Nerve: Added for stage naming in the material editor
                if (0 == token.Icmp("name")) {
                    src.SkipRestOfLine()
                    continue
                }

                // image options
                if (0 == token.Icmp("blend")) {
                    ParseBlend(src, ss)
                    continue
                }
                if (0 == token.Icmp("map")) {
                    str = Image_program.R_ParsePastImageProgram(src)
                    Copynz(imageName, str, imageName.size)
                    continue
                }
                if (0 == token.Icmp("remoteRenderMap")) {
                    ts.dynamic = dynamicidImage_t.DI_REMOTE_RENDER
                    ts.width = src.ParseInt()
                    ts.height = src.ParseInt()
                    continue
                }
                if (0 == token.Icmp("mirrorRenderMap")) {
                    ts.dynamic = dynamicidImage_t.DI_MIRROR_RENDER
                    ts.width = src.ParseInt()
                    ts.height = src.ParseInt()
                    ts.texgen = texgen_t.TG_SCREEN
                    continue
                }
                if (0 == token.Icmp("xrayRenderMap")) {
                    ts.dynamic = dynamicidImage_t.DI_XRAY_RENDER
                    ts.width = src.ParseInt()
                    ts.height = src.ParseInt()
                    ts.texgen = texgen_t.TG_SCREEN
                    continue
                }
                if (0 == token.Icmp("screen")) {
                    ts.texgen = texgen_t.TG_SCREEN
                    continue
                }
                if (0 == token.Icmp("screen2")) {
                    ts.texgen = texgen_t.TG_SCREEN2
                    continue
                }
                if (0 == token.Icmp("glassWarp")) {
                    ts.texgen = texgen_t.TG_GLASSWARP
                    continue
                }
                if (0 == token.Icmp("videomap")) {
                    // note that videomaps will always be in clamp mode, so texture
                    // coordinates had better be in the 0 to 1 range
                    if (!src.ReadToken(token)) {
                        Common.common.Warning("missing parameter for 'videoMap' keyword in material '%s'", GetName())
                        continue
                    }
                    var loop = false
                    if (0 == token.Icmp("loop")) {
                        loop = true
                        if (!src.ReadToken(token)) {
                            Common.common.Warning(
                                "missing parameter for 'videoMap' keyword in material '%s'",
                                GetName()
                            )
                            continue
                        }
                    }
                    ts.cinematic[0] = idCinematic.Alloc()
                    ts.cinematic[0]!!.InitFromFile(token.toString(), loop)
                    continue
                }
                if (0 == token.Icmp("soundmap")) {
                    if (!src.ReadToken(token)) {
                        Common.common.Warning("missing parameter for 'soundmap' keyword in material '%s'", GetName())
                        continue
                    }
                    ts.cinematic[0] = idSndWindow()
                    ts.cinematic[0]!!.InitFromFile(token.toString(), true)
                    continue
                }
                if (0 == token.Icmp("cubeMap")) {
                    str = Image_program.R_ParsePastImageProgram(src)
                    Copynz(imageName, str, imageName.size)
                    cubeMap = cubeFiles_t.CF_NATIVE
                    continue
                }
                if (0 == token.Icmp("cameraCubeMap")) {
                    str = Image_program.R_ParsePastImageProgram(src)
                    Copynz(imageName, str, imageName.size)
                    cubeMap = cubeFiles_t.CF_CAMERA
                    continue
                }
                if (0 == token.Icmp("ignoreAlphaTest")) {
                    ss.ignoreAlphaTest = true
                    continue
                }
                if (0 == token.Icmp("nearest")) {
                    tf = textureFilter_t.TF_NEAREST
                    continue
                }
                if (0 == token.Icmp("linear")) {
                    tf = textureFilter_t.TF_LINEAR
                    continue
                }
                if (0 == token.Icmp("clamp")) {
                    trp = textureRepeat_t.TR_CLAMP
                    continue
                }
                if (0 == token.Icmp("noclamp")) {
                    trp = textureRepeat_t.TR_REPEAT
                    continue
                }
                if (0 == token.Icmp("zeroclamp")) {
                    trp = textureRepeat_t.TR_CLAMP_TO_ZERO
                    continue
                }
                if (0 == token.Icmp("alphazeroclamp")) {
                    trp = textureRepeat_t.TR_CLAMP_TO_ZERO_ALPHA
                    continue
                }
                if (0 == token.Icmp("uncompressed") || 0 == token.Icmp("highquality")) {
                    if (0 == idImageManager.image_ignoreHighQuality.GetInteger()) {
                        td = textureDepth_t.TD_HIGH_QUALITY
                    }
                    continue
                }
                if (0 == token.Icmp("forceHighQuality")) {
                    td = textureDepth_t.TD_HIGH_QUALITY
                    continue
                }
                if (0 == token.Icmp("nopicmip")) {
                    allowPicmip = false
                    continue
                }
                if (0 == token.Icmp("vertexColor")) {
                    ss.vertexColor = stageVertexColor_t.SVC_MODULATE
                    continue
                }
                if (0 == token.Icmp("inverseVertexColor")) {
                    ss.vertexColor = stageVertexColor_t.SVC_INVERSE_MODULATE
                    continue
                } // privatePolygonOffset
                else if (0 == token.Icmp("privatePolygonOffset")) {
                    if (!src.ReadTokenOnLine(token)) {
                        ss.privatePolygonOffset = 1.0f
                        continue
                    }
                    // explict larger (or negative) offset
                    src.UnreadToken(token)
                    ss.privatePolygonOffset = src.ParseFloat()
                    continue
                }

                // texture coordinate generation
                if (0 == token.Icmp("texGen")) {
                    src.ExpectAnyToken(token)
                    if (0 == token.Icmp("normal")) {
                        ts.texgen = texgen_t.TG_DIFFUSE_CUBE
                    } else if (0 == token.Icmp("reflect")) {
                        ts.texgen = texgen_t.TG_REFLECT_CUBE
                    } else if (0 == token.Icmp("skybox")) {
                        ts.texgen = texgen_t.TG_SKYBOX_CUBE
                    } else if (0 == token.Icmp("wobbleSky")) {
                        ts.texgen = texgen_t.TG_WOBBLESKY_CUBE
                        texGenRegisters[0] = ParseExpression(src)
                        texGenRegisters[1] = ParseExpression(src)
                        texGenRegisters[2] = ParseExpression(src)
                    } else {
                        Common.common.Warning("bad texGen '%s' in material %s", token.toString(), GetName())
                        SetMaterialFlag(MF_DEFAULTED)
                    }
                    continue
                }
                if (0 == token.Icmp("scroll") || 0 == token.Icmp("translate")) {
                    a = ParseExpression(src)
                    MatchToken(src, ",")
                    if (DBG_ParseStage == 41) {
                        b = ParseExpression(src)
                    } else {
                        b = ParseExpression(src)
                    }
                    matrix[0][0] = GetExpressionConstant(1.0f)
                    matrix[0][1] = GetExpressionConstant(0.0f)
                    matrix[0][2] = a
                    matrix[1][0] = GetExpressionConstant(0.0f)
                    matrix[1][1] = GetExpressionConstant(1.0f)
                    matrix[1][2] = b
                    MultiplyTextureMatrix(ts, matrix) //HACKME::3:scrolling screws up our beloved logo. For now.
                    continue
                }
                if (0 == token.Icmp("scale")) {
                    a = ParseExpression(src)
                    MatchToken(src, ",")
                    b = ParseExpression(src)
                    // this just scales without a centering
                    matrix[0][0] = a
                    matrix[0][1] = GetExpressionConstant(0.0f)
                    matrix[0][2] = GetExpressionConstant(0.0f)
                    matrix[1][0] = GetExpressionConstant(0.0f)
                    matrix[1][1] = b
                    matrix[1][2] = GetExpressionConstant(0.0f)
                    MultiplyTextureMatrix(ts, matrix)
                    continue
                }
                if (0 == token.Icmp("centerScale")) {
                    a = ParseExpression(src)
                    MatchToken(src, ",")
                    b = ParseExpression(src)
                    // this subtracts 0.5f, then scales, then adds 0.5f
                    matrix[0][0] = a
                    matrix[0][1] = GetExpressionConstant(0.0f)
                    matrix[0][2] = EmitOp(
                        GetExpressionConstant(0.5f),
                        EmitOp(GetExpressionConstant(0.5f), a, expOpType_t.OP_TYPE_MULTIPLY),
                        expOpType_t.OP_TYPE_SUBTRACT
                    )
                    matrix[1][0] = GetExpressionConstant(0.0f)
                    matrix[1][1] = b
                    matrix[1][2] = EmitOp(
                        GetExpressionConstant(0.5f),
                        EmitOp(GetExpressionConstant(0.5f), b, expOpType_t.OP_TYPE_MULTIPLY),
                        expOpType_t.OP_TYPE_SUBTRACT
                    )
                    MultiplyTextureMatrix(ts, matrix)
                    continue
                }
                if (0 == token.Icmp("shear")) {
                    a = ParseExpression(src)
                    MatchToken(src, ",")
                    b = ParseExpression(src)
                    // this subtracts 0.5f, then shears, then adds 0.5f
                    matrix[0][0] = GetExpressionConstant(1.0f)
                    matrix[0][1] = a
                    matrix[0][2] = EmitOp(GetExpressionConstant(-0.5f), a, expOpType_t.OP_TYPE_MULTIPLY)
                    matrix[1][0] = b
                    matrix[1][1] = GetExpressionConstant(1.0f)
                    matrix[1][2] = EmitOp(GetExpressionConstant(-0.5f), b, expOpType_t.OP_TYPE_MULTIPLY)
                    MultiplyTextureMatrix(ts, matrix)
                    continue
                }
                if (0 == token.Icmp("rotate")) {
                    var table: idDeclTable?
                    var sinReg: Int
                    var cosReg: Int

                    // in cycles
                    a = ParseExpression(src)
                    table = DeclManager.declManager.FindType(declType_t.DECL_TABLE, "sinTable", false) as idDeclTable?
                    if (null == table) {
                        Common.common.Warning("no sinTable for rotate defined")
                        SetMaterialFlag(MF_DEFAULTED)
                        return
                    }
                    sinReg = EmitOp(table.Index(), a, expOpType_t.OP_TYPE_TABLE)
                    table = DeclManager.declManager.FindType(declType_t.DECL_TABLE, "cosTable", false) as idDeclTable?
                    if (null == table) {
                        Common.common.Warning("no cosTable for rotate defined")
                        SetMaterialFlag(MF_DEFAULTED)
                        return
                    }
                    cosReg = EmitOp(table.Index(), a, expOpType_t.OP_TYPE_TABLE)

                    // this subtracts 0.5f, then rotates, then adds 0.5f
                    matrix[0][0] = cosReg
                    matrix[0][1] = EmitOp(GetExpressionConstant(0.0f), sinReg, expOpType_t.OP_TYPE_SUBTRACT)
                    matrix[0][2] = EmitOp(
                        EmitOp(
                            EmitOp(GetExpressionConstant(-0.5f), cosReg, expOpType_t.OP_TYPE_MULTIPLY),
                            EmitOp(GetExpressionConstant(0.5f), sinReg, expOpType_t.OP_TYPE_MULTIPLY),
                            expOpType_t.OP_TYPE_ADD
                        ),
                        GetExpressionConstant(0.5f), expOpType_t.OP_TYPE_ADD
                    )

                    matrix[1][0] = sinReg
                    matrix[1][1] = cosReg
                    matrix[1][2] = EmitOp(
                        EmitOp(
                            EmitOp(GetExpressionConstant(-0.5f), sinReg, expOpType_t.OP_TYPE_MULTIPLY),
                            EmitOp(GetExpressionConstant(-0.5f), cosReg, expOpType_t.OP_TYPE_MULTIPLY),
                            expOpType_t.OP_TYPE_ADD
                        ),
                        GetExpressionConstant(0.5f), expOpType_t.OP_TYPE_ADD
                    )
                    MultiplyTextureMatrix(ts, matrix)
                    continue
                }

                // color mask options
                if (0 == token.Icmp("maskRed")) {
                    ss.drawStateBits = ss.drawStateBits or GLS_REDMASK
                    continue
                }
                if (0 == token.Icmp("maskGreen")) {
                    ss.drawStateBits = ss.drawStateBits or GLS_GREENMASK
                    continue
                }
                if (0 == token.Icmp("maskBlue")) {
                    ss.drawStateBits = ss.drawStateBits or GLS_BLUEMASK
                    continue
                }
                if (0 == token.Icmp("maskAlpha")) {
                    ss.drawStateBits = ss.drawStateBits or GLS_ALPHAMASK
                    continue
                }
                if (0 == token.Icmp("maskColor")) {
                    ss.drawStateBits = ss.drawStateBits or GLS_COLORMASK
                    continue
                }
                if (0 == token.Icmp("maskDepth")) {
                    ss.drawStateBits = ss.drawStateBits or GLS_DEPTHMASK
                    continue
                }
                if (0 == token.Icmp("alphaTest")) {
                    ss.hasAlphaTest = true
                    ss.alphaTestRegister = ParseExpression(src)
                    coverage = materialCoverage_t.MC_PERFORATED
                    continue
                }

                // shorthand for 2D modulated
                if (0 == token.Icmp("colored")) {
                    ss.color.registers[0] = etoi(expRegister_t.EXP_REG_PARM0)
                    ss.color.registers[1] = etoi(expRegister_t.EXP_REG_PARM1)
                    ss.color.registers[2] = etoi(expRegister_t.EXP_REG_PARM2)
                    ss.color.registers[3] = etoi(expRegister_t.EXP_REG_PARM3)
                    pd!!.registersAreConstant = false
                    continue
                }
                if (0 == token.Icmp("color")) {
                    ss.color.registers[0] = ParseExpression(src)
                    MatchToken(src, ",")
                    ss.color.registers[1] = ParseExpression(src)
                    MatchToken(src, ",")
                    ss.color.registers[2] = ParseExpression(src)
                    MatchToken(src, ",")
                    ss.color.registers[3] = ParseExpression(src)
                    continue
                }
                if (0 == token.Icmp("red")) {
                    ss.color.registers[0] = ParseExpression(src)
                    continue
                }
                if (0 == token.Icmp("green")) {
                    ss.color.registers[1] = ParseExpression(src)
                    continue
                }
                if (0 == token.Icmp("blue")) {
                    ss.color.registers[2] = ParseExpression(src)
                    continue
                }
                if (0 == token.Icmp("alpha")) {
                    DEBUG_ParseStage++
                    ss.color.registers[3] = ParseExpression(src)
                    //                    System.out.printf("alpha=>%d\n", ss.color.registers[3]);
                    ss.color.registers[3]
                    continue
                }
                if (0 == token.Icmp("rgb")) {
                    ss.color.registers[2] = ParseExpression(src)
                    ss.color.registers[1] = ss.color.registers[2]
                    ss.color.registers[0] = ss.color.registers[1]
                    continue
                }
                if (0 == token.Icmp("rgba")) {
                    ss.color.registers[3] = ParseExpression(src)
                    ss.color.registers[2] = ss.color.registers[3]
                    ss.color.registers[1] = ss.color.registers[2]
                    ss.color.registers[0] = ss.color.registers[1]
                    continue
                }
                if (0 == token.Icmp("if")) {
                    ss.conditionRegister = ParseExpression(src)
                    continue
                }
                if (0 == token.Icmp("program")) {
                    if (src.ReadTokenOnLine(token)) {
                        newStage.vertexProgram =
                            draw_arb2.R_FindARBProgram(ARBVertexProgram.GL_VERTEX_PROGRAM_ARB, token.toString())
                        newStage.fragmentProgram =
                            draw_arb2.R_FindARBProgram(ARBFragmentProgram.GL_FRAGMENT_PROGRAM_ARB, token.toString())
                    }
                    continue
                }
                if (0 == token.Icmp("fragmentProgram")) {
                    if (src.ReadTokenOnLine(token)) {
                        newStage.fragmentProgram =
                            draw_arb2.R_FindARBProgram(ARBFragmentProgram.GL_FRAGMENT_PROGRAM_ARB, token.toString())
                    }
                    continue
                }
                if (0 == token.Icmp("vertexProgram")) {
                    if (src.ReadTokenOnLine(token)) {
                        newStage.vertexProgram =
                            draw_arb2.R_FindARBProgram(ARBVertexProgram.GL_VERTEX_PROGRAM_ARB, token.toString())
                    }
                    continue
                }
                if (0 == token.Icmp("megaTexture")) {
                    if (src.ReadTokenOnLine(token)) {
                        newStage.megaTexture = idMegaTexture()
                        if (!newStage.megaTexture!!.InitFromMegaFile(token.toString())) {
//					delete newStage.megaTexture;
                            newStage.megaTexture = null
                            SetMaterialFlag(MF_DEFAULTED)
                            continue
                        }
                        newStage.vertexProgram =
                            draw_arb2.R_FindARBProgram(ARBVertexProgram.GL_VERTEX_PROGRAM_ARB, "megaTexture.vfp")
                        newStage.fragmentProgram =
                            draw_arb2.R_FindARBProgram(ARBFragmentProgram.GL_FRAGMENT_PROGRAM_ARB, "megaTexture.vfp")
                        continue
                    }
                }
                if (0 == token.Icmp("vertexParm")) {
                    ParseVertexParm(src, newStage)
                    continue
                }
                if (0 == token.Icmp("fragmentMap")) {
                    ParseFragmentMap(src, newStage)
                    continue
                }
                Common.common.Warning("unknown token '%s' in material '%s'", token.toString(), GetName())
                SetMaterialFlag(MF_DEFAULTED)
                return
            }

            // if we are using newStage, allocate a copy of it
            if (newStage.fragmentProgram != 0 || newStage.vertexProgram != 0) {
///		ss.newStage = (newShaderStage_t )Mem_Alloc( sizeof( newStage ) );
                ss.newStage = newStage
            }

            // successfully parsed a stage
            numStages++

            // select a compressed depth based on what the stage is
            if (td == textureDepth_t.TD_DEFAULT) {
                when (ss.lighting) {
                    stageLighting_t.SL_BUMP -> td = textureDepth_t.TD_BUMP
                    stageLighting_t.SL_DIFFUSE -> td = textureDepth_t.TD_DIFFUSE
                    stageLighting_t.SL_SPECULAR -> td = textureDepth_t.TD_SPECULAR
                    else -> {}
                }
            }

            // now load the image with all the parms we parsed
            if (strLen(imageName) > 0) {
                DEBUG_imageName += 0
                ts.image!![0] = Image.globalImages.ImageFromFile(ctos(imageName), tf, allowPicmip, trp, td, cubeMap)
                if (null == ts.image[0]) {
                    ts.image[0] = Image.globalImages.defaultImage
                }
            } else if (ts.cinematic[0] == null && ts.dynamic == dynamicidImage_t.DI_STATIC && ss.newStage == null) {
                Common.common.Warning("material '%s' had stage with no image", GetName())
                ts.image!![0] = Image.globalImages.defaultImage
            }
        }

        private fun ParseDeform(src: idLexer) {
            val token = idToken()
            if (!src.ExpectAnyToken(token)) {
                return
            }
            if (0 == token.Icmp("sprite")) {
                deform = deform_t.DFRM_SPRITE
                cullType = cullType_t.CT_TWO_SIDED
                SetMaterialFlag(MF_NOSHADOWS)
                return
            }
            if (0 == token.Icmp("tube")) {
                deform = deform_t.DFRM_TUBE
                cullType = cullType_t.CT_TWO_SIDED
                SetMaterialFlag(MF_NOSHADOWS)
                return
            }
            if (0 == token.Icmp("flare")) {
                deform = deform_t.DFRM_FLARE
                cullType = cullType_t.CT_TWO_SIDED
                deformRegisters[0] = ParseExpression(src)
                SetMaterialFlag(MF_NOSHADOWS)
                return
            }
            if (0 == token.Icmp("expand")) {
                deform = deform_t.DFRM_EXPAND
                deformRegisters[0] = ParseExpression(src)
                return
            }
            if (0 == token.Icmp("move")) {
                deform = deform_t.DFRM_MOVE
                deformRegisters[0] = ParseExpression(src)
                return
            }
            if (0 == token.Icmp("turbulent")) {
                deform = deform_t.DFRM_TURB
                if (!src.ExpectAnyToken(token)) {
                    src.Warning("deform particle missing particle name")
                    SetMaterialFlag(MF_DEFAULTED)
                    return
                }
                deformDecl = DeclManager.declManager.FindType(declType_t.DECL_TABLE, token, true)
                deformRegisters[0] = ParseExpression(src)
                deformRegisters[1] = ParseExpression(src)
                deformRegisters[2] = ParseExpression(src)
                return
            }
            if (0 == token.Icmp("eyeBall")) {
                deform = deform_t.DFRM_EYEBALL
                return
            }
            if (0 == token.Icmp("particle")) {
                deform = deform_t.DFRM_PARTICLE
                if (!src.ExpectAnyToken(token)) {
                    src.Warning("deform particle missing particle name")
                    SetMaterialFlag(MF_DEFAULTED)
                    return
                }
                deformDecl = DeclManager.declManager.FindType(declType_t.DECL_PARTICLE, token, true)
                return
            }
            if (0 == token.Icmp("particle2")) {
                deform = deform_t.DFRM_PARTICLE2
                if (!src.ExpectAnyToken(token)) {
                    src.Warning("deform particle missing particle name")
                    SetMaterialFlag(MF_DEFAULTED)
                    return
                }
                deformDecl = DeclManager.declManager.FindType(declType_t.DECL_PARTICLE, token, true)
                return
            }
            src.Warning("Bad deform type '%s'", token.toString())
            SetMaterialFlag(MF_DEFAULTED)
        }

        private fun ParseDecalInfo(src: idLexer) {
//	idToken token;
            decalInfo.stayTime = (src.ParseFloat() * 1000).toInt()
            decalInfo.fadeTime = (src.ParseFloat() * 1000).toInt()
            val start = FloatArray(4)
            val end = FloatArray(4)
            src.Parse1DMatrix(4, start)
            src.Parse1DMatrix(4, end)
            for (i in 0..3) {
                decalInfo.start[i] = start[i]
                decalInfo.end[i] = end[i]
            }
        }

        fun oSet(FindMaterial: idMaterial?) {
            throw UnsupportedOperationException("Not supported yet.")
        }

        /*
         ===============
         idMaterial::CheckSurfaceParm

         See if the current token matches one of the surface parm bit flags
         ===============
         */
        private fun CheckSurfaceParm(token: idToken): Boolean {
            for (i in 0 until numInfoParms) {
                if (0 == token.Icmp(infoParms[i].name)) {
                    if ((infoParms[i].surfaceFlags and SURF_TYPE_MASK) != 0) {
                        // ensure we only have one surface type set
                        surfaceFlags = surfaceFlags and SURF_TYPE_MASK.inv()
                    }
                    surfaceFlags = surfaceFlags or infoParms[i].surfaceFlags
                    contentFlags = contentFlags or infoParms[i].contents
                    if (infoParms[i].clearSolid != 0) {
                        contentFlags = contentFlags and CONTENTS_SOLID.inv()
                    }
                    return true
                }
            }
            return false
        }

        private fun GetExpressionConstant(f: Float): Int {
            var i: Int
            i = expRegister_t.EXP_REG_NUM_PREDEFINED.ordinal
            while (i < numRegisters) {
                if (!pd!!.registerIsTemporary[i] && pd!!.shaderRegisters[i] == f) {
                    return i
                }
                i++
            }
            if (numRegisters == MAX_EXPRESSION_REGISTERS) {
                Common.common.Warning("GetExpressionConstant: material '%s' hit MAX_EXPRESSION_REGISTERS", GetName())
                SetMaterialFlag(MF_DEFAULTED)
                return 0
            }
            pd!!.registerIsTemporary[i] = false
            pd!!.shaderRegisters[i] = f
            //            if(dbg_count==131)
//            TempDump.printCallStack(dbg_count + "****************************" + numRegisters);
            numRegisters++
            return i
        }

        private fun GetExpressionTemporary(): Int {
            if (numRegisters == MAX_EXPRESSION_REGISTERS) {
                Common.common.Warning("GetExpressionTemporary: material '%s' hit MAX_EXPRESSION_REGISTERS", GetName())
                SetMaterialFlag(MF_DEFAULTED)
                return 0
            }
            //            if(dbg_count==131)
//            TempDump.printCallStack(dbg_count + "****************************" + numRegisters);
            pd!!.registerIsTemporary[numRegisters] = true
            numRegisters++
            return numRegisters - 1
        }

        private fun GetExpressionOp(): expOp_t? {
            if (numOps == MAX_EXPRESSION_OPS) {
                Common.common.Warning("GetExpressionOp: material '%s' hit MAX_EXPRESSION_OPS", GetName())
                SetMaterialFlag(MF_DEFAULTED)
                return pd!!.shaderOps[0]
            }
            return pd!!.shaderOps[numOps++]
        }

        private fun EmitOp(a: Int, b: Int, opType: expOpType_t): Int {
            val op: expOp_t?

            // optimize away identity operations
            if (opType == expOpType_t.OP_TYPE_ADD) {
                if (!pd!!.registerIsTemporary[a] && pd!!.shaderRegisters[a] == 0.0f) {
                    return b
                }
                if (!pd!!.registerIsTemporary[b] && pd!!.shaderRegisters[b] == 0.0f) {
                    return a
                }
                if (!pd!!.registerIsTemporary[a] && !pd!!.registerIsTemporary[b]) {
                    return GetExpressionConstant(pd!!.shaderRegisters[a] + pd!!.shaderRegisters[b])
                }
            }
            if (opType == expOpType_t.OP_TYPE_MULTIPLY) {
                if (!pd!!.registerIsTemporary[a] && pd!!.shaderRegisters[a] == 1.0f) {
                    return b
                }
                if (!pd!!.registerIsTemporary[a] && pd!!.shaderRegisters[a] == 0.0f) {
                    return a
                }
                if (!pd!!.registerIsTemporary[b] && pd!!.shaderRegisters[b] == 1.0f) {
                    return a
                }
                if (!pd!!.registerIsTemporary[b] && pd!!.shaderRegisters[b] == 0.0f) {
                    return b
                }
                if (!pd!!.registerIsTemporary[a] && !pd!!.registerIsTemporary[b]) {
                    return GetExpressionConstant(pd!!.shaderRegisters[a] * pd!!.shaderRegisters[b])
                }
            }
            op = GetExpressionOp()
            op!!.opType = opType
            op.a = a
            op.b = b
            op.c = GetExpressionTemporary()
            return op.c
        }

        private fun ParseEmitOp(src: idLexer, a: Int, opType: expOpType_t, priority: Int): Int {
            val b: Int
            b = ParseExpressionPriority(src, priority)
            return EmitOp(a, b, opType)
        }

        /*
         =================
         idMaterial::ParseTerm

         Returns a register index
         =================
         */
        private fun ParseTerm(src: idLexer): Int {
            val token = idToken()
            val a: Int
            val b: Int
            src.ReadToken(token)
            if (token.equals("(")) {
                a = ParseExpression(src)
                MatchToken(src, ")")
                return a
            }
            if (0 == token.Icmp("time")) {
                pd!!.registersAreConstant = false
                return expRegister_t.EXP_REG_TIME.ordinal
            }
            if (0 == token.Icmp("parm0")) {
                pd!!.registersAreConstant = false
                return expRegister_t.EXP_REG_PARM0.ordinal
            }
            if (0 == token.Icmp("parm1")) {
                pd!!.registersAreConstant = false
                return expRegister_t.EXP_REG_PARM1.ordinal
            }
            if (0 == token.Icmp("parm2")) {
                pd!!.registersAreConstant = false
                return expRegister_t.EXP_REG_PARM2.ordinal
            }
            if (0 == token.Icmp("parm3")) {
                pd!!.registersAreConstant = false
                return expRegister_t.EXP_REG_PARM3.ordinal
            }
            if (0 == token.Icmp("parm4")) {
                pd!!.registersAreConstant = false
                return expRegister_t.EXP_REG_PARM4.ordinal
            }
            if (0 == token.Icmp("parm5")) {
                pd!!.registersAreConstant = false
                return expRegister_t.EXP_REG_PARM5.ordinal
            }
            if (0 == token.Icmp("parm6")) {
                pd!!.registersAreConstant = false
                return expRegister_t.EXP_REG_PARM6.ordinal
            }
            if (0 == token.Icmp("parm7")) {
                pd!!.registersAreConstant = false
                return expRegister_t.EXP_REG_PARM7.ordinal
            }
            if (0 == token.Icmp("parm8")) {
                pd!!.registersAreConstant = false
                return expRegister_t.EXP_REG_PARM8.ordinal
            }
            if (0 == token.Icmp("parm9")) {
                pd!!.registersAreConstant = false
                return expRegister_t.EXP_REG_PARM9.ordinal
            }
            if (0 == token.Icmp("parm10")) {
                pd!!.registersAreConstant = false
                return expRegister_t.EXP_REG_PARM10.ordinal
            }
            if (0 == token.Icmp("parm11")) {
                pd!!.registersAreConstant = false
                return expRegister_t.EXP_REG_PARM11.ordinal
            }
            if (0 == token.Icmp("global0")) {
                pd!!.registersAreConstant = false
                return expRegister_t.EXP_REG_GLOBAL0.ordinal
            }
            if (0 == token.Icmp("global1")) {
                pd!!.registersAreConstant = false
                return expRegister_t.EXP_REG_GLOBAL1.ordinal
            }
            if (0 == token.Icmp("global2")) {
                pd!!.registersAreConstant = false
                return expRegister_t.EXP_REG_GLOBAL2.ordinal
            }
            if (0 == token.Icmp("global3")) {
                pd!!.registersAreConstant = false
                return expRegister_t.EXP_REG_GLOBAL3.ordinal
            }
            if (0 == token.Icmp("global4")) {
                pd!!.registersAreConstant = false
                return expRegister_t.EXP_REG_GLOBAL4.ordinal
            }
            if (0 == token.Icmp("global5")) {
                pd!!.registersAreConstant = false
                return expRegister_t.EXP_REG_GLOBAL5.ordinal
            }
            if (0 == token.Icmp("global6")) {
                pd!!.registersAreConstant = false
                return expRegister_t.EXP_REG_GLOBAL6.ordinal
            }
            if (0 == token.Icmp("global7")) {
                pd!!.registersAreConstant = false
                return expRegister_t.EXP_REG_GLOBAL7.ordinal
            }
            if (0 == token.Icmp("fragmentPrograms")) {
                return GetExpressionConstant((if (glConfig.ARBFragmentProgramAvailable) 1 else 0).toFloat())
            }
            if (0 == token.Icmp("sound")) {
                pd!!.registersAreConstant = false
                return EmitOp(0, 0, expOpType_t.OP_TYPE_SOUND)
            }

            // parse negative numbers
            if (token.equals("-")) {
                src.ReadToken(token)
                if (token.type == TT_NUMBER || token.equals(".")) {
                    return GetExpressionConstant(-token.GetFloatValue())
                }
                src.Warning("Bad negative number '%s'", token)
                SetMaterialFlag(MF_DEFAULTED)
                return 0
            }
            if ((token.type == TT_NUMBER) || token.equals(".") || token.equals("-")) {
                val dbg_bla: Int = GetExpressionConstant(token.GetFloatValue())
                //                System.out.printf("TT_NUMBER = %d\n", dbg_bla);
                return dbg_bla
            }

            // see if it is a table name
            val table: idDeclTable? =
                DeclManager.declManager.FindType(declType_t.DECL_TABLE, token, false) as idDeclTable?
            if (null == table) {
                src.Warning("Bad term '%s'", token)
                SetMaterialFlag(MF_DEFAULTED)
                return 0
            }

            // parse a table expression
            MatchToken(src, "[")
            b = ParseExpression(src)
            MatchToken(src, "]")
            return EmitOp(table.Index(), b, expOpType_t.OP_TYPE_TABLE)
        }

        private fun ParseExpressionPriority(src: idLexer, priority: Int): Int {
            val token = idToken()
            val a: Int
            DBG_ParseExpressionPriority++
            //            if(DBG_ParseExpressionPriority==101)return 0;
            if (priority == 0) {
                return ParseTerm(src)
            }
            a = ParseExpressionPriority(src, priority - 1)
            if (TestMaterialFlag(MF_DEFAULTED)) {    // we have a parse error
                return 0
            }
            if (!src.ReadToken(token)) {
                // we won't get EOF in a real file, but we can
                // when parsing from generated strings
                return a
            }
            if (priority == 1 && token.equals("*")) {
                return ParseEmitOp(src, a, expOpType_t.OP_TYPE_MULTIPLY, priority)
            }
            if (priority == 1 && token.equals("/")) {
                return ParseEmitOp(src, a, expOpType_t.OP_TYPE_DIVIDE, priority)
            }
            if (priority == 1 && token.equals("%")) {    // implied truncate both to integer
                return ParseEmitOp(src, a, expOpType_t.OP_TYPE_MOD, priority)
            }
            if (priority == 2 && token.equals("+")) {
                return ParseEmitOp(src, a, expOpType_t.OP_TYPE_ADD, priority)
            }
            if (priority == 2 && token.equals("-")) {
                return ParseEmitOp(src, a, expOpType_t.OP_TYPE_SUBTRACT, priority)
            }
            if (priority == 3 && token.equals(">")) {
                return ParseEmitOp(src, a, expOpType_t.OP_TYPE_GT, priority)
            }
            if (priority == 3 && token.equals(">=")) {
                return ParseEmitOp(src, a, expOpType_t.OP_TYPE_GE, priority)
            }
            if (priority == 3 && token.equals("<")) {
                return ParseEmitOp(src, a, expOpType_t.OP_TYPE_LT, priority)
            }
            if (priority == 3 && token.equals("<=")) {
                return ParseEmitOp(src, a, expOpType_t.OP_TYPE_LE, priority)
            }
            if (priority == 3 && token.equals("==")) {
                return ParseEmitOp(src, a, expOpType_t.OP_TYPE_EQ, priority)
            }
            if (priority == 3 && token.equals("!=")) {
                return ParseEmitOp(src, a, expOpType_t.OP_TYPE_NE, priority)
            }
            if (priority == 4 && token.equals("&&")) {
                return ParseEmitOp(src, a, expOpType_t.OP_TYPE_AND, priority)
            }
            if (priority == 4 && token.equals("||")) {
                return ParseEmitOp(src, a, expOpType_t.OP_TYPE_OR, priority)
            }

            // assume that anything else terminates the expression
            // not too robust error checking...
            src.UnreadToken(token)
            return a
        }

        private fun ParseExpression(src: idLexer): Int {
            return ParseExpressionPriority(src, TOP_PRIORITY)
        }

        private fun ClearStage(ss: shaderStage_t?) {
            ss!!.drawStateBits = 0
            ss.conditionRegister = GetExpressionConstant(1.0f)
            val expressionConstant = GetExpressionConstant(1.0f)
            ss.color.registers[0] = expressionConstant
            ss.color.registers[1] = expressionConstant
            ss.color.registers[2] = expressionConstant
            ss.color.registers[3] = expressionConstant
        }

        private fun NameToSrcBlendMode(name: idStr): Int {
            if (0 == name.Icmp("GL_ONE")) {
                return GLS_SRCBLEND_ONE
            } else if (0 == name.Icmp("GL_ZERO")) {
                return GLS_SRCBLEND_ZERO
            } else if (0 == name.Icmp("GL_DST_COLOR")) {
                return GLS_SRCBLEND_DST_COLOR
            } else if (0 == name.Icmp("GL_ONE_MINUS_DST_COLOR")) {
                return GLS_SRCBLEND_ONE_MINUS_DST_COLOR
            } else if (0 == name.Icmp("GL_SRC_ALPHA")) {
                return GLS_SRCBLEND_SRC_ALPHA
            } else if (0 == name.Icmp("GL_ONE_MINUS_SRC_ALPHA")) {
                return GLS_SRCBLEND_ONE_MINUS_SRC_ALPHA
            } else if (0 == name.Icmp("GL_DST_ALPHA")) {
                return GLS_SRCBLEND_DST_ALPHA
            } else if (0 == name.Icmp("GL_ONE_MINUS_DST_ALPHA")) {
                return GLS_SRCBLEND_ONE_MINUS_DST_ALPHA
            } else if (0 == name.Icmp("GL_SRC_ALPHA_SATURATE")) {
                return GLS_SRCBLEND_ALPHA_SATURATE
            }
            Common.common.Warning("unknown blend mode '%s' in material '%s'", name, GetName())
            SetMaterialFlag(MF_DEFAULTED)
            return GLS_SRCBLEND_ONE
        }

        private fun NameToDstBlendMode(name: idStr): Int {
            if (0 == name.Icmp("GL_ONE")) {
                return GLS_DSTBLEND_ONE
            } else if (0 == name.Icmp("GL_ZERO")) {
                return GLS_DSTBLEND_ZERO
            } else if (0 == name.Icmp("GL_SRC_ALPHA")) {
                return GLS_DSTBLEND_SRC_ALPHA
            } else if (0 == name.Icmp("GL_ONE_MINUS_SRC_ALPHA")) {
                return GLS_DSTBLEND_ONE_MINUS_SRC_ALPHA
            } else if (0 == name.Icmp("GL_DST_ALPHA")) {
                return GLS_DSTBLEND_DST_ALPHA
            } else if (0 == name.Icmp("GL_ONE_MINUS_DST_ALPHA")) {
                return GLS_DSTBLEND_ONE_MINUS_DST_ALPHA
            } else if (0 == name.Icmp("GL_SRC_COLOR")) {
                return GLS_DSTBLEND_SRC_COLOR
            } else if (0 == name.Icmp("GL_ONE_MINUS_SRC_COLOR")) {
                return GLS_DSTBLEND_ONE_MINUS_SRC_COLOR
            }
            Common.common.Warning("unknown blend mode '%s' in material '%s'", name, GetName())
            SetMaterialFlag(MF_DEFAULTED)
            return GLS_DSTBLEND_ONE
        }

        private fun MultiplyTextureMatrix(
            ts: textureStage_t,
            registers: Array<IntArray> /*[2][3]*/
        ) {    // FIXME: for some reason the const is bad for gcc and Mac
            var old: Array<IntArray> = Array(2, { IntArray(3) })
            if (!ts.hasMatrix) {
                ts.hasMatrix = true
                //		memcpy( ts.matrix, registers, sizeof( ts.matrix ) );
                ts.matrix = registers.copyOf()
                return
            }

//	memcpy( old, ts.matrix, sizeof( old ) );
            old = ts.matrix.copyOf()

            // multiply the two maticies
            ts.matrix[0][0] = EmitOp(
                EmitOp(old[0][0], registers[0][0], expOpType_t.OP_TYPE_MULTIPLY),
                EmitOp(old[0][1], registers[1][0], expOpType_t.OP_TYPE_MULTIPLY), expOpType_t.OP_TYPE_ADD
            )
            ts.matrix[0][1] = EmitOp(
                EmitOp(old[0][0], registers[0][1], expOpType_t.OP_TYPE_MULTIPLY),
                EmitOp(old[0][1], registers[1][1], expOpType_t.OP_TYPE_MULTIPLY), expOpType_t.OP_TYPE_ADD
            )
            ts.matrix[0][2] = EmitOp(
                EmitOp(
                    EmitOp(old[0][0], registers[0][2], expOpType_t.OP_TYPE_MULTIPLY),
                    EmitOp(old[0][1], registers[1][2], expOpType_t.OP_TYPE_MULTIPLY), expOpType_t.OP_TYPE_ADD
                ),
                old[0][2], expOpType_t.OP_TYPE_ADD
            )

            ts.matrix[1][0] = EmitOp(
                EmitOp(old[1][0], registers[0][0], expOpType_t.OP_TYPE_MULTIPLY),
                EmitOp(old[1][1], registers[1][0], expOpType_t.OP_TYPE_MULTIPLY), expOpType_t.OP_TYPE_ADD
            )
            ts.matrix[1][1] = EmitOp(
                EmitOp(old[1][0], registers[0][1], expOpType_t.OP_TYPE_MULTIPLY),
                EmitOp(old[1][1], registers[1][1], expOpType_t.OP_TYPE_MULTIPLY), expOpType_t.OP_TYPE_ADD
            )
            ts.matrix[1][2] = EmitOp(
                EmitOp(
                    EmitOp(old[1][0], registers[0][2], expOpType_t.OP_TYPE_MULTIPLY),
                    EmitOp(old[1][1], registers[1][2], expOpType_t.OP_TYPE_MULTIPLY), expOpType_t.OP_TYPE_ADD
                ),
                old[1][2], expOpType_t.OP_TYPE_ADD
            )
        }

        /*
         ===============
         idMaterial::SortInteractionStages

         The renderer expects bump, then diffuse, then specular
         There can be multiple bump maps, followed by additional
         diffuse and specular stages, which allows cross-faded bump mapping.

         Ambient stages can be interspersed anywhere, but they are
         ignored during interactions, and all the interaction
         stages are ignored during ambient drawing.
         ===============
         */
        private fun SortInteractionStages() {
            var j: Int
            var i = 0
            while (i < numStages) {

                // find the next bump map
                j = i + 1
                while (j < numStages) {
                    if (pd!!.parseStages[j]!!.lighting == stageLighting_t.SL_BUMP) {
                        // if the very first stage wasn't a bumpmap,
                        // this bumpmap is part of the first group
                        if (pd!!.parseStages[i]!!.lighting != stageLighting_t.SL_BUMP) {
                            j++
                            continue
                        }
                        break
                    }
                    j++
                }

                // bubble sort everything bump / diffuse / specular
                for (l in 1 until (j - i)) {
                    for (k in i until (j - l)) {
                        if (pd!!.parseStages[k]!!.lighting.ordinal > pd!!.parseStages[k + 1]!!.lighting.ordinal) {
                            var temp: shaderStage_t?
                            temp = pd!!.parseStages[k]
                            pd!!.parseStages[k] = pd!!.parseStages[k + 1]
                            pd!!.parseStages[k + 1] = temp
                        }
                    }
                }
                i = j
            }
        }

        /*
         ==============
         idMaterial::AddImplicitStages

         If a material has diffuse or specular stages without any
         bump stage, add an implicit _flat bumpmap stage.

         If a material has a bump stage but no diffuse or specular
         stage, add a _white diffuse stage.

         It is valid to have either a diffuse or specular without the other.

         It is valid to have a reflection map and a bump map for bumpy reflection
         ==============
         */
        private fun AddImplicitStages(trpDefault: textureRepeat_t = textureRepeat_t.TR_REPEAT /*= TR_REPEAT*/) {
            val buffer = CharArray(1024)
            val newSrc = idLexer()
            var hasDiffuse = false
            var hasSpecular = false
            var hasBump = false
            var hasReflection = false
            for (i in 0 until numStages) {
                if (pd!!.parseStages[i]!!.lighting == stageLighting_t.SL_BUMP) {
                    hasBump = true
                }
                if (pd!!.parseStages[i]!!.lighting == stageLighting_t.SL_DIFFUSE) {
                    hasDiffuse = true
                }
                if (pd!!.parseStages[i]!!.lighting == stageLighting_t.SL_SPECULAR) {
                    hasSpecular = true
                }
                if (pd!!.parseStages[i]!!.texture.texgen == texgen_t.TG_REFLECT_CUBE) {
                    hasReflection = true
                }
            }

            // if it doesn't have an interaction at all, don't add anything
            if (!hasBump && !hasDiffuse && !hasSpecular) {
                return
            }
            if (numStages == MAX_SHADER_STAGES) {
                return
            }
            if (!hasBump) {
                snPrintf(buffer, buffer.size, "blend bumpmap\nmap _flat\n}\n")
                newSrc.LoadMemory(ctos(buffer), strLen(buffer), "bumpmap")
                newSrc.SetFlags(LEXFL_NOFATALERRORS or LEXFL_NOSTRINGCONCAT or LEXFL_NOSTRINGESCAPECHARS or LEXFL_ALLOWPATHNAMES)
                ParseStage(newSrc, trpDefault)
                newSrc.FreeSource()
            }
            if (!hasDiffuse && !hasSpecular && !hasReflection) {
                snPrintf(buffer, buffer.size, "blend diffusemap\nmap _white\n}\n")
                newSrc.LoadMemory(ctos(buffer), strLen(buffer), "diffusemap")
                newSrc.SetFlags(LEXFL_NOFATALERRORS or LEXFL_NOSTRINGCONCAT or LEXFL_NOSTRINGESCAPECHARS or LEXFL_ALLOWPATHNAMES)
                ParseStage(newSrc, trpDefault)
                newSrc.FreeSource()
            }
        }

        /*
         ==================
         idMaterial::CheckForConstantRegisters

         As of 5/2/03, about half of the unique materials loaded on typical
         maps are constant, but 2/3 of the surface references are.
         This is probably an optimization of dubious value.
         ==================
         */
        private fun CheckForConstantRegisters() {
            if (!pd!!.registersAreConstant) {
                return
            }

            // evaluate the registers once, and save them
            constantRegisters =
                FloatArray(GetNumRegisters()) // R_ClearedStaticAlloc(GetNumRegisters() /* sizeof( float )*/);
            val shaderParms = FloatArray(MAX_ENTITY_SHADER_PARMS)
            //	memset( shaderParms, 0, sizeof( shaderParms ) );
            val viewDef = viewDef_s()
            //	memset( &viewDef, 0, sizeof( viewDef ) );
            EvaluateRegisters(constantRegisters!!, shaderParms, viewDef, null)
        }

        override fun AllocBuffer(): ByteBuffer {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        override fun Read(buffer: ByteBuffer) {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        override fun Write(): ByteBuffer {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        override fun toString(): String {
            return this.toString() + " idMaterial{" + "desc=" + desc + ", renderBump=" + renderBump + ", lightFalloffImage=" + lightFalloffImage + ", entityGui=" + entityGui + ", gui=" + gui + ", noFog=" + noFog + ", spectrum=" + spectrum + ", polygonOffset=" + polygonOffset + ", contentFlags=" + contentFlags + ", surfaceFlags=" + surfaceFlags + ", materialFlags=" + materialFlags + ", decalInfo=" + decalInfo + ", sort=" + sort + ", deform=" + deform + ", deformRegisters=" + deformRegisters + ", deformDecl=" + deformDecl + ", texGenRegisters=" + texGenRegisters + ", coverage=" + coverage + ", cullType=" + cullType + ", shouldCreateBackSides=" + shouldCreateBackSides + ", fogLight=" + fogLight + ", blendLight=" + blendLight + ", ambientLight=" + ambientLight + ", unsmoothedTangents=" + unsmoothedTangents + ", hasSubview=" + hasSubview + ", allowOverlays=" + allowOverlays + ", numOps=" + numOps + ", ops=" + ops + ", numRegisters=" + numRegisters + ", expressionRegisters=" + expressionRegisters + ", constantRegisters=" + constantRegisters + ", numStages=" + numStages + ", numAmbientStages=" + numAmbientStages + ", stages=" + stages + ", pd=" + pd + ", surfaceArea=" + surfaceArea + ", editorImageName=" + editorImageName + ", editorImage=" + editorImage + ", editorAlpha=" + editorAlpha + ", suppressInSubview=" + suppressInSubview + ", portalSky=" + portalSky + ", refCount=" + refCount + '}'
        }

        // info parms
        class infoParm_t {
            var clearSolid: Int
            var surfaceFlags: Int
            var contents: Int
            var name: String

            constructor(name: String, clearSolid: Int, surfaceFlags: Int, contents: Int) {
                this.name = name
                this.clearSolid = clearSolid
                this.surfaceFlags = surfaceFlags
                this.contents = contents
            }

            constructor(name: String, clearSolid: Int, surfaceFlags: surfTypes_t, contents: Int) {
                this.name = name
                this.clearSolid = clearSolid
                this.surfaceFlags = surfaceFlags.ordinal
                this.contents = contents
            }
        }

        companion object {
            @Transient
            val SIZE: Int = (idStr.SIZE
                    + idStr.SIZE
                    + CPP_class.Pointer.SIZE //idImage.SIZE //pointer
                    + Integer.SIZE
                    + 1 //boolean
                    + Integer.SIZE
                    + java.lang.Float.SIZE
                    + Integer.SIZE
                    + Integer.SIZE
                    + Integer.SIZE
                    + decalInfo_t.SIZE
                    + java.lang.Float.SIZE
                    + CPP_class.Enum.SIZE // deform_t.SIZE
                    + (Integer.SIZE * 4)
                    + idDecl.SIZE //TODO:what good is a pointer in serialization?
                    + (Integer.SIZE * MAX_TEXGEN_REGISTERS)
                    + CPP_class.Enum.SIZE //materialCoverage_t.SIZE
                    + CPP_class.Enum.SIZE //cullType_t.SIZE
                    + 7 //7 booleans
                    + Integer.SIZE
                    + CPP_class.Pointer.SIZE //expOp_t.SIZE//pointer
                    + Integer.SIZE
                    + java.lang.Float.SIZE //point
                    + java.lang.Float.SIZE //point
                    + Integer.SIZE
                    + Integer.SIZE
                    + CPP_class.Pointer.SIZE //shaderStage_t.SIZE//pointer
                    + mtrParsingData_s.SIZE
                    + java.lang.Float.SIZE
                    + idStr.SIZE
                    + CPP_class.Pointer.SIZE //idImage.SIZE//pointer
                    + java.lang.Float.SIZE
                    + 2 //2 booleans
                    + Integer.SIZE)

            /*
         =================
         idMaterial::ParseExpressionPriority

         Returns a register index
         =================
         */
            val TOP_PRIORITY: Int = 4
            val infoParms: Array<infoParm_t> = arrayOf( // game relevant attributes
                infoParm_t("solid", 0, 0, CONTENTS_SOLID),  // may need to override a clearSolid
                infoParm_t("water", 1, 0, CONTENTS_WATER),  // used for water
                infoParm_t("playerclip", 0, 0, CONTENTS_PLAYERCLIP),  // solid to players
                infoParm_t("monsterclip", 0, 0, CONTENTS_MONSTERCLIP),  // solid to monsters
                infoParm_t("moveableclip", 0, 0, CONTENTS_MOVEABLECLIP),  // solid to moveable entities
                infoParm_t("ikclip", 0, 0, CONTENTS_IKCLIP),  // solid to IK
                infoParm_t("blood", 0, 0, CONTENTS_BLOOD),  // used to detect blood decals
                infoParm_t("trigger", 0, 0, CONTENTS_TRIGGER),  // used for triggers
                infoParm_t("aassolid", 0, 0, CONTENTS_AAS_SOLID),  // solid for AAS
                infoParm_t(
                    "aasobstacle",
                    0,
                    0,
                    CONTENTS_AAS_OBSTACLE
                ),  // used to compile an obstacle into AAS that can be enabled/disabled
                infoParm_t(
                    "flashlight_trigger",
                    0,
                    0,
                    CONTENTS_FLASHLIGHT_TRIGGER
                ),  // used for triggers that are activated by the flashlight
                infoParm_t("nonsolid", 1, 0, 0),  // clears the solid flag
                infoParm_t("nullNormal", 0, SURF_NULLNORMAL, 0),  // renderbump will draw as 0x80 0x80 0x80
                //
                // utility relevant attributes
                infoParm_t("areaportal", 1, 0, CONTENTS_AREAPORTAL),  // divides areas
                infoParm_t("qer_nocarve", 1, 0, CONTENTS_NOCSG),  // don't cut brushes in editor
                //
                infoParm_t("discrete", 1, SURF_DISCRETE, 0),  // surfaces should not be automatically merged together or
                /////////////////////////////////////////////////// clipped to the world,
                /////////////////////////////////////////////////// because they represent discrete objects like gui shaders
                /////////////////////////////////////////////////// mirrors, or autosprites
                infoParm_t("noFragment", 0, SURF_NOFRAGMENT, 0),  //
                infoParm_t("slick", 0, SURF_SLICK, 0),
                infoParm_t("collision", 0, SURF_COLLISION, 0),
                infoParm_t("noimpact", 0, SURF_NOIMPACT, 0),  // don't make impact explosions or marks
                infoParm_t("nodamage", 0, SURF_NODAMAGE, 0),  // no falling damage when hitting
                infoParm_t("ladder", 0, SURF_LADDER, 0),  // climbable
                infoParm_t("nosteps", 0, SURF_NOSTEPS, 0),  // no footsteps
                //
                // material types for particle, sound, footstep feedback
                infoParm_t("metal", 0, surfTypes_t.SURFTYPE_METAL, 0),  // metal
                infoParm_t("stone", 0, surfTypes_t.SURFTYPE_STONE, 0),  // stone
                infoParm_t("flesh", 0, surfTypes_t.SURFTYPE_FLESH, 0),  // flesh
                infoParm_t("wood", 0, surfTypes_t.SURFTYPE_WOOD, 0),  // wood
                infoParm_t("cardboard", 0, surfTypes_t.SURFTYPE_CARDBOARD, 0),  // cardboard
                infoParm_t("liquid", 0, surfTypes_t.SURFTYPE_LIQUID, 0),  // liquid
                infoParm_t("glass", 0, surfTypes_t.SURFTYPE_GLASS, 0),  // glass
                infoParm_t("plastic", 0, surfTypes_t.SURFTYPE_PLASTIC, 0),  // plastic
                infoParm_t(
                    "ricochet",
                    0,
                    surfTypes_t.SURFTYPE_RICOCHET,
                    0
                ),  // behaves like metal but causes a ricochet sound
                //
                // unassigned surface types
                infoParm_t("surftype10", 0, surfTypes_t.SURFTYPE_10, 0),
                infoParm_t("surftype11", 0, surfTypes_t.SURFTYPE_11, 0),
                infoParm_t("surftype12", 0, surfTypes_t.SURFTYPE_12, 0),
                infoParm_t("surftype13", 0, surfTypes_t.SURFTYPE_13, 0),
                infoParm_t("surftype14", 0, surfTypes_t.SURFTYPE_14, 0),
                infoParm_t("surftype15", 0, surfTypes_t.SURFTYPE_15, 0)
            )
            val numInfoParms: Int = infoParms.size
            var DBG_ParseStage: Int = 0

            /*
         =================
         idMaterial::ParseStage

         An open brace has been parsed


         {
         if <expression>
         map <imageprogram>
         "nearest" "linear" "clamp" "zeroclamp" "uncompressed" "highquality" "nopicmip"
         scroll, scale, rotate
         }

         =================
         */
            var DEBUG_imageName: Int = 0
            private var DBG_ParseBlend: Int = 0
            private var DBG_ParseExpressionPriority: Int = 0

            /*
         =========================
         idMaterial::Parse

         Parses the current material definition and finds all necessary images.
         =========================
         */
            private var DEBUG_Parse: Int = 0
            private var DEBUG_ParseStage: Int = 0
        }
    }

    class idMatList : idList<idMaterial?>()
}

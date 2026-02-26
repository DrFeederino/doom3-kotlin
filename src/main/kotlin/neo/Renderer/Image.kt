package neo.Renderer

import neo.Renderer.Image_files.R_WritePalTGA
import neo.Renderer.Image_init.R_CombineCubeImages_f
import neo.Renderer.Image_init.R_CreateNoFalloffImage
import neo.Renderer.Image_init.R_DefaultImage
import neo.Renderer.Image_init.R_ListImages_f
import neo.Renderer.Image_init.R_RGBA8Image
import neo.Renderer.Image_init.R_ReloadImages_f
import neo.Renderer.Image_init.R_WhiteImage
import neo.Renderer.Image_init.makeNormalizeVectorCubeMap
import neo.Renderer.Image_program.R_LoadImageProgram
import neo.Renderer.Material.idMaterial
import neo.Renderer.Material.textureFilter_t
import neo.Renderer.Material.textureRepeat_t
import neo.TempDump.CPP_class
import neo.TempDump.SERiAL
import neo.TempDump.ctos
import neo.TempDump.flatten
import neo.framework.Async.AsyncNetwork.idAsyncNetwork
import neo.framework.CVarSystem.CVAR_ARCHIVE
import neo.framework.CVarSystem.CVAR_BOOL
import neo.framework.CVarSystem.CVAR_INTEGER
import neo.framework.CVarSystem.CVAR_RENDERER
import neo.framework.CVarSystem.cvarSystem
import neo.framework.CVarSystem.idCVar
import neo.framework.CmdSystem.CMD_FL_RENDERER
import neo.framework.CmdSystem.cmdSystem
import neo.framework.CmdSystem.idCmdSystem.ArgCompletion_String
import neo.framework.Common
import neo.framework.Common.MemInfo_t
import neo.framework.DeclManager
import neo.framework.FileSystem_h.FILE_HASH_SIZE
import neo.framework.FileSystem_h.FILE_NOT_FOUND_TIMESTAMP
import neo.framework.FileSystem_h.backgroundDownload_s
import neo.framework.FileSystem_h.dlType_t
import neo.framework.FileSystem_h.fileSystem
import neo.framework.File_h.idFile
import neo.framework.Session
import neo.idlib.CmdArgs
import neo.idlib.LittleLong
import neo.idlib.Text.Str.idStr
import neo.idlib.Text.Str.idStr.Companion.FormatNumber
import neo.idlib.Text.Str.idStr.Companion.Icmp
import neo.idlib.Text.Str.va
import neo.idlib.containers.CInt
import neo.idlib.containers.List.idList
import neo.idlib.containers.idHashIndex
import neo.idlib.containers.idStrList
import neo.idlib.idException
import neo.idlib.math.idMath.Sqrt
import neo.idlib.math.idVec3
import neo.sys.win_shared.Sys_Milliseconds
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.*
import java.nio.*
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max

object Image {
    //
    val MAX_IMAGE_NAME = 256

    //
    // pixel format flags
    val DDSF_ALPHAPIXELS = 0x00000001

    //
    // surface description flags
    val DDSF_CAPS = 0x00000001

    //
    // dwCaps1 flags
    val DDSF_COMPLEX = 0x00000008
    val DDSF_DEPTH = 0x00800000
    val DDSF_FOURCC = 0x00000004
    val DDSF_HEIGHT = 0x00000002

    //
    // our extended flags
    val DDSF_ID_INDEXCOLOR = 0x10000000
    val DDSF_ID_MONOCHROME = 0x20000000
    val DDSF_LINEARSIZE = 0x00080000
    val DDSF_MIPMAP = 0x00400000
    val DDSF_MIPMAPCOUNT = 0x00020000
    val DDSF_PITCH = 0x00000008
    val DDSF_PIXELFORMAT = 0x00001000
    val DDSF_RGB = 0x00000040
    val DDSF_RGBA = 0x00000041
    val DDSF_TEXTURE = 0x00001000
    val DDSF_WIDTH = 0x00000004

    //
    val MAX_TEXTURE_LEVELS = 14
    private val DDS_MAKEFOURCC_DXT1 = 'D'.code shl 0 or ('X'.code shl 8) or ('T'.code shl 16) or ('1'.code shl 24)
    private val DDS_MAKEFOURCC_DXT3 = 'D'.code shl 0 or ('X'.code shl 8) or ('T'.code shl 16) or ('3'.code shl 24)

    //
    private val DDS_MAKEFOURCC_DXT5 = 'D'.code shl 0 or ('X'.code shl 8) or ('T'.code shl 16) or ('5'.code shl 24)
    private val DDS_MAKEFOURCC_RXGB = 'R'.code shl 0 or ('X'.code shl 8) or ('G'.code shl 16) or ('B'.code shl 24)

    // do this with a pointer, in case we want to make the actual manager
    // a private virtual subclass
    private val imageManager = idImageManager()

    // pointer to global list for the rest of the system
    var globalImages = imageManager
    fun DDS_MAKEFOURCC(a: Int, b: Int, c: Int, d: Int): Int {
        return a shl 0 or (b shl 8) or (c shl 16) or (d shl 24)
    }

    //
    enum class cubeFiles_t {
        CF_2D, // not a cube map
        CF_NATIVE, // _px, _nx, _py, etc, directly sent to GL
        CF_CAMERA // _forward, _back, etc, rotated and flipped as needed before sending to GL
    }

    /*
     ====================================================================

     IMAGE

     idImage have a one to one correspondance with OpenGL textures.

     No texture is ever used that does not have a corresponding idImage.

     no code outside this unit should call any of these OpenGL functions:

     qglGenTextures
     qglDeleteTextures
     qglBindTexture

     qglTexParameter

     qglTexImage
     qglTexSubImage

     qglCopyTexImage
     qglCopyTexSubImage

     qglEnable( GL_TEXTURE_* )
     qglDisable( GL_TEXTURE_* )

     ====================================================================
     */
    internal enum class imageState_t {
        IS_UNLOADED,

        // no gl texture number
        IS_PARTIAL,

        // has a texture number and the low mip levels loaded
        IS_LOADED // has a texture number and the full mip hierarchy
    }

    // increasing numeric values imply more information is stored
    enum class textureDepth_t {
        TD_SPECULAR,

        // may be compressed, and always zeros the alpha channel
        TD_DIFFUSE,

        // may be compressed
        TD_DEFAULT,

        // will use compressed formats when possible
        TD_BUMP,

        // may be compressed with 8 bit lookup
        TD_HIGH_QUALITY // either 32 bit or a component format, no loss at all
    }

    enum class textureType_t {
        TT_DISABLED,
        TT_2D,
        TT_3D,
        TT_CUBIC,
        TT_RECT
    }

    internal class ddsFilePixelFormat_t(/*long*/var dwSize: Int, /*long*/
                                        var dwFlags: Int, /*long*/
                                        var dwFourCC: Int, /*long*/
                                        var dwRGBBitCount: Int, /*long*/
                                        var dwRBitMask: Int, /*long*/
                                        var dwGBitMask: Int, /*long*/
                                        var dwBBitMask: Int, /*long*/
                                        var dwABitMask: Int
    ) {
        companion object {
            @Transient
            val SIZE = (CPP_class.Long.SIZE
                    + CPP_class.Long.SIZE
                    + CPP_class.Long.SIZE
                    + CPP_class.Long.SIZE
                    + CPP_class.Long.SIZE
                    + CPP_class.Long.SIZE
                    + CPP_class.Long.SIZE
                    + CPP_class.Long.SIZE)
        }
    }

    internal class ddsFileHeader_t : SERiAL {
        var ddspf: ddsFilePixelFormat_t? = null
        var  /*long*/dwCaps1 = 0
        var  /*long*/dwCaps2 = 0
        var  /*long*/dwDepth = 0
        var  /*long*/dwFlags = 0
        var  /*long*/dwHeight = 0
        var  /*long*/dwMipMapCount = 0
        var  /*long*/dwPitchOrLinearSize = 0
        var dwReserved1 = IntArray(11)
        var dwReserved2 = IntArray(3)
        var  /*long*/dwSize = 0
        var  /*long*/dwWidth = 0

        constructor()
        constructor(data: ByteBuffer) {
            Read(data)
        }

        override fun AllocBuffer(): ByteBuffer {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        override fun Read(buffer: ByteBuffer) {
            dwSize = buffer.getInt()
            dwFlags = buffer.getInt()
            dwHeight = buffer.getInt()
            dwWidth = buffer.getInt()
            dwPitchOrLinearSize = buffer.getInt()
            dwDepth = buffer.getInt()
            dwMipMapCount = buffer.getInt()
            for (a in dwReserved1.indices) {
                dwReserved1[a] = buffer.getInt()
            }
            ddspf = ddsFilePixelFormat_t(
                buffer.getInt(), buffer.getInt(), buffer.getInt(), buffer.getInt(),
                buffer.getInt(), buffer.getInt(), buffer.getInt(), buffer.getInt()
            )
            dwCaps1 = buffer.getInt()
            dwCaps2 = buffer.getInt()
            for (b in dwReserved2.indices) {
                dwReserved2[b] = buffer.getInt()
            }
        }

        override fun Write(): ByteBuffer {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        companion object {
            @Transient
            private val SIZE = (CPP_class.Long.SIZE
                    + CPP_class.Long.SIZE
                    + CPP_class.Long.SIZE
                    + CPP_class.Long.SIZE
                    + CPP_class.Long.SIZE
                    + CPP_class.Long.SIZE
                    + CPP_class.Long.SIZE
                    + (CPP_class.Long.SIZE * 11)
                    + ddsFilePixelFormat_t.SIZE
                    + CPP_class.Long.SIZE
                    + CPP_class.Long.SIZE
                    + (CPP_class.Long.SIZE * 3))

            @Transient
            val BYTES = SIZE / 8
        }
    }

    abstract class GeneratorFunction {
        abstract fun run(image: idImage)
    }

    class idImage {
        var allowDownSize // this also doubles as a don't-partially-load flag
                : Boolean
        var backgroundLoadInProgress // true if another thread is reading the complete d3t file
                : Boolean
        var bgl: backgroundDownload_s
        var bglNext // linked from tr.backgroundImageLoads
                : idImage?
        var bindCount // incremented each bind
                : Int

        //
        var cacheUsagePrev: idImage?
        var cacheUsageNext // for dynamic cache purging of old images
                : idImage?

        //
        var classification // just for resource profiling
                : Int
        var cubeFiles // determines the naming and flipping conventions for the six images
                : cubeFiles_t
        var defaulted // true if the default image was generated because a file couldn't be loaded
                : Boolean
        var depth: textureDepth_t
        var filter: textureFilter_t
        var frameUsed // for texture usage in frame statistics
                : Int
        var generatorFunction // NULL for files           //public void (        *generatorFunction)( idImage *image );	// NULL for files
                : GeneratorFunction?

        //
        var hashNext // for hash chains to speed lookup
                : idImage?

        //
        var imageHash: String? = null // for identical-image checking

        //
        // parameters that define this image
        var imgName // game path, including extension (except for cube maps), may be an image program
                : idStr
        var internalFormat: Int
        var isMonochrome = booleanArrayOf(false) // so the NV20 path can use a reduced pass count
        var isPartialImage // true if this is pointed to by another image
                : Boolean
        var levelLoadReferenced // for determining if it needs to be purged
                : Boolean

        //
        // background loading information
        var partialImage // shrunken, space-saving version
                : idImage?
        var precompressedFile // true when it was loaded from a .d3t file
                : Boolean

        //
        var refCount // overall ref count
                : Int

        //
        var referencedOutsideLevelLoad: Boolean
        var repeat: textureRepeat_t
        /*GLuint*/ var texNum // gl texture binding, will be TEXTURE_NOT_LOADED if not loaded
                : Int
        /*ID_TIME_T*/ var timestamp =
            longArrayOf(0) // the most recent of all images used in creation, for reloadImages command
        var type: textureType_t

        //
        // data for listImages
        var uploadWidth: CInt
        var uploadHeight: CInt
        var uploadDepth // after power of two, downsample, and MAX_TEXTURE_SIZE
                : CInt
        private var _COUNTER = 0

        //
        constructor() {
            _COUNTER = DEBUG_COUNTER++
            texNum = TEXTURE_NOT_LOADED
            partialImage = null
            type = textureType_t.TT_DISABLED
            isPartialImage = false
            frameUsed = 0
            classification = 0
            backgroundLoadInProgress = false
            bgl = backgroundDownload_s()
            bgl.opcode = dlType_t.DLTYPE_FILE
            bgl.f = null
            bglNext = null
            imgName = idStr()
            generatorFunction = null
            allowDownSize = false
            filter = textureFilter_t.TF_DEFAULT
            repeat = textureRepeat_t.TR_REPEAT
            depth = textureDepth_t.TD_DEFAULT
            cubeFiles = cubeFiles_t.CF_2D
            referencedOutsideLevelLoad = false
            levelLoadReferenced = false
            precompressedFile = false
            defaulted = false
            //            timestamp[0] = 0;
            bindCount = 0
            uploadWidth = CInt()
            uploadHeight = CInt()
            uploadDepth = CInt()
            internalFormat = 0
            cacheUsageNext = null
            cacheUsagePrev = cacheUsageNext
            hashNext = null
            //            isMonochrome[0] = false;
            refCount = 0
        }

        /**
         * copy constructor
         *
         * @param image
         */
        internal constructor(image: idImage) {
            texNum = image.texNum
            type = image.type
            frameUsed = image.frameUsed
            bindCount = image.bindCount
            partialImage = image.partialImage //pointer
            isPartialImage = image.isPartialImage
            backgroundLoadInProgress = image.backgroundLoadInProgress
            bgl = image.bgl
            bglNext = image.bglNext //pointer
            imgName = idStr(image.imgName)
            generatorFunction = image.generatorFunction
            allowDownSize = image.allowDownSize
            filter = image.filter
            repeat = image.repeat
            depth = image.depth
            cubeFiles = image.cubeFiles
            referencedOutsideLevelLoad = image.referencedOutsideLevelLoad
            levelLoadReferenced = image.levelLoadReferenced
            precompressedFile = image.precompressedFile
            defaulted = image.defaulted
            isMonochrome[0] = image.isMonochrome[0]
            timestamp[0] = image.timestamp[0]
            imageHash = image.imageHash
            classification = image.classification
            uploadWidth = image.uploadWidth
            uploadHeight = image.uploadHeight
            uploadDepth = image.uploadDepth
            internalFormat = image.internalFormat
            cacheUsagePrev = image.cacheUsagePrev //pointer
            cacheUsageNext = image.cacheUsageNext //pointer
            hashNext = image.hashNext //pointer
            refCount = image.refCount
        }

        /*
         ==============
         Bind

         Automatically enables 2D mapping, cube mapping, or 3D texturing if needed
         ==============
         */
        // Makes this image active on the current GL texture unit.
        // automatically enables or disables cube mapping or texture3D
        // May perform file loading if the image was not preloaded.
        // May start a background image read.
        fun Bind() {
            if (tr.logFile != null) {
                tr_backend.RB_LogComment("idImage::Bind( %s )\n", imgName.toString())
            }

            // if this is an image that we are caching, move it to the front of the LRU chain
            if (partialImage != null) {
                if (cacheUsageNext != null) {
                    // unlink from old position
                    cacheUsageNext!!.cacheUsagePrev = cacheUsagePrev
                    cacheUsagePrev!!.cacheUsageNext = cacheUsageNext
                }
                // link in at the head of the list
                cacheUsageNext = globalImages.cacheLRU.cacheUsageNext
                cacheUsagePrev = globalImages.cacheLRU
                cacheUsageNext!!.cacheUsagePrev = this
                cacheUsagePrev!!.cacheUsageNext = this
            }

            // load the image if necessary (FIXME: not SMP safe!)
            if (texNum == TEXTURE_NOT_LOADED) {
                if (partialImage != null) {
                    // if we have a partial image, go ahead and use that
                    partialImage!!.Bind()

                    // start a background load of the full thing if it isn't already in the queue
                    if (!backgroundLoadInProgress) {
                        StartBackgroundImageLoad()
                    }
                    return
                }

                // load the image on demand here, which isn't our normal game operating mode
                ActuallyLoadImage(true, true) // check for precompressed, load is from back end
            }

            // bump our statistic counters
            frameUsed = backEnd!!.frameCount
            bindCount++
            val tmu = backEnd!!.glState.tmu[backEnd!!.glState.currenttmu]

            // enable or disable apropriate texture modes
            if (tmu!!.textureType != type && (backEnd!!.glState.currenttmu < glConfig.maxTextureUnits)) {
                if (tmu.textureType == textureType_t.TT_CUBIC) {
                    qgl.qglDisable(GL13.GL_TEXTURE_CUBE_MAP /*_EXT*/)
                } else if (tmu.textureType == textureType_t.TT_3D) {
                    qgl.qglDisable(GL12.GL_TEXTURE_3D)
                } else if (tmu.textureType == textureType_t.TT_2D) {
                    qgl.qglDisable(GL11.GL_TEXTURE_2D)
                }
                if (type == textureType_t.TT_CUBIC) {
                    qgl.qglEnable(GL13.GL_TEXTURE_CUBE_MAP /*_EXT*/)
                } else if (type == textureType_t.TT_3D) {
                    qgl.qglEnable(GL12.GL_TEXTURE_3D)
                } else if (type == textureType_t.TT_2D) {
                    qgl.qglEnable(GL11.GL_TEXTURE_2D)
                }
                tmu.textureType = type
            }

            // bind the texture
            if (type == textureType_t.TT_2D) {
                if (tmu.current2DMap != texNum) {
                    tmu.current2DMap = texNum
                    qgl.qglBindTexture(GL11.GL_TEXTURE_2D, texNum)
                    if (texNum == 25) {
                        println("Blaaaaaaasphemy!")
                    }
                }
            } else if (type == textureType_t.TT_CUBIC) {
                if (tmu.currentCubeMap != texNum) {
                    tmu.currentCubeMap = texNum
                    qgl.qglBindTexture(GL13.GL_TEXTURE_CUBE_MAP /*_EXT*/, texNum)
                }
            } else if (type == textureType_t.TT_3D) {
                if (tmu.current3DMap != texNum) {
                    tmu.current3DMap = texNum
                    qgl.qglBindTexture(GL12.GL_TEXTURE_3D, texNum)
                }
            }
            if (Common.com_purgeAll.GetBool()) {
                val  /*GLclampf*/priority = 1.0f
                qgl.qglPrioritizeTextures(1, texNum, priority)
            }
        }

        /*
         ==============
         BindFragment

         Fragment programs explicitly say which type of map they want, so we don't need to
         do any enable / disable changes
         ==============
         */
        // for use with fragment programs, doesn't change any enable2D/3D/cube states
        fun BindFragment() {
            if (tr.logFile != null) {
                tr_backend.RB_LogComment("idImage::BindFragment %s )\n", imgName.toString())
            }

            // if this is an image that we are caching, move it to the front of the LRU chain
            if (partialImage != null) {
                if (cacheUsageNext != null) {
                    // unlink from old position
                    cacheUsageNext!!.cacheUsagePrev = cacheUsagePrev
                    cacheUsagePrev!!.cacheUsageNext = cacheUsageNext
                }
                // link in at the head of the list
                cacheUsageNext = globalImages.cacheLRU.cacheUsageNext
                cacheUsagePrev = globalImages.cacheLRU
                cacheUsageNext!!.cacheUsagePrev = this
                cacheUsagePrev!!.cacheUsageNext = this
            }

            // load the image if necessary (FIXME: not SMP safe!)
            if (texNum == TEXTURE_NOT_LOADED) {
                if (partialImage != null) {
                    // if we have a partial image, go ahead and use that
                    partialImage!!.BindFragment()

                    // start a background load of the full thing if it isn't already in the queue
                    if (!backgroundLoadInProgress) {
                        StartBackgroundImageLoad()
                    }
                    return
                }

                // load the image on demand here, which isn't our normal game operating mode
                ActuallyLoadImage(true, true) // check for precompressed, load is from back end
            }

            // bump our statistic counters
            frameUsed = backEnd!!.frameCount
            bindCount++

            // bind the texture
            if (type == textureType_t.TT_2D) {
                qgl.qglBindTexture(GL11.GL_TEXTURE_2D, texNum)
            } else if (type == textureType_t.TT_RECT) {
                qgl.qglBindTexture(GL31.GL_TEXTURE_RECTANGLE, texNum)
            } else if (type == textureType_t.TT_CUBIC) {
                qgl.qglBindTexture(GL13.GL_TEXTURE_CUBE_MAP /*_EXT*/, texNum)
            } else if (type == textureType_t.TT_3D) {
                qgl.qglBindTexture(GL12.GL_TEXTURE_3D, texNum)
            }
        }

        //
        // deletes the texture object, but leaves the structure so it can be reloaded
        fun PurgeImage() {
            if (texNum != TEXTURE_NOT_LOADED) {
                // sometimes is NULL when exiting with an error
//                if (qglDeleteTextures) {
                try {
                    qgl.qglDeleteTextures(1, texNum) // this should be the ONLY place it is ever called!
                } catch (e: RuntimeException) { //TODO:deal with this.
//                    e.printStackTrace();
                }
                //                }
                texNum = TEXTURE_NOT_LOADED
            }

            // clear all the current binding caches, so the next bind will do a real one
            for (i in 0 until MAX_MULTITEXTURE_UNITS) {
                backEnd!!.glState.tmu[i]!!.current2DMap = -1
                backEnd!!.glState.tmu[i]!!.current3DMap = -1
                backEnd!!.glState.tmu[i]!!.currentCubeMap = -1
            }
        }

        /*
         ================
         GenerateImage

         The alpha channel bytes should be 255 if you don't
         want the channel.

         We need a material characteristic to ask for specific texture modes.

         Designed limitations of flexibility:

         No support for texture borders.

         No support for texture border color.

         No support for texture environment colors or GL_BLEND or GL_DECAL
         texture environments, because the automatic optimization to single
         or dual component textures makes those modes potentially undefined.

         No non-power-of-two images.

         No palettized textures.

         There is no way to specify separate wrap/clamp values for S and T

         There is no way to specify explicit mip map levels

         ================
         */
        // used by callback functions to specify the actual data
        // data goes from the bottom to the top line of the image, as OpenGL expects it
        // These perform an implicit Bind() on the current texture unit
        // FIXME: should we implement cinematics this way, instead of with explicit calls?
        fun GenerateImage(
            pic: ByteBuffer, width: Int, height: Int,
            filterParm: textureFilter_t, allowDownSizeParm: Boolean,
            repeatParm: textureRepeat_t, depthParm: textureDepth_t
        ) {
            var width = width
            var height = height
            val preserveBorder: Boolean
            val scaledBuffer: ByteBuffer?
            val scaled_width = CInt()
            val scaled_height = CInt()
            var shrunk: ByteBuffer?
            PurgeImage()
            filter = filterParm
            allowDownSize = allowDownSizeParm
            repeat = repeatParm
            depth = depthParm

            // if we don't have a rendering context, just return after we
            // have filled in the parms.  We must have the values set, or
            // an image match from a shader before OpenGL starts would miss
            // the generated texture
            if (!glConfig.isInitialized) {
                return
            }

            // don't let mip mapping smear the texture into the clamped border
            preserveBorder = repeat == textureRepeat_t.TR_CLAMP_TO_ZERO

            // make sure it is a power of 2
            scaled_width._val = MakePowerOfTwo(width)
            scaled_height._val = MakePowerOfTwo(height)
            if (scaled_width._val != width || scaled_height._val != height) {
                Common.common.Error("R_CreateImage: not a power of 2 image")
            }

            // Optionally modify our width/height based on options/hardware
            GetDownsize(scaled_width, scaled_height)

//            scaledBuffer = null;
            // generate the texture number
            texNum = qgl.qglGenTextures()
            println(">>>>$imgName: $texNum")

            // select proper internal format before we resample
            internalFormat = SelectInternalFormat(pic, 1, width, height, depth, isMonochrome)

            // copy or resample data as appropriate for first MIP level
            if ((scaled_width._val == width) && (scaled_height._val == height)) {
                // we must copy even if unchanged, because the border zeroing
                // would otherwise modify const data
                scaledBuffer =
                    BufferUtils.createByteBuffer(width * height * 4) // R_StaticAlloc(scaled_width[0] * scaled_height[0]);
                val temp = ByteArray(width * height * 4)
                //		memcpy (scaledBuffer, pic, width*height*4);
                pic.rewind()
                pic[temp]
                scaledBuffer.put(temp) //System.arraycopy(pic.array(), 0, scaledBuffer, 0, width * height * 4);
            } else {
                // resample down as needed (FIXME: this doesn't seem like it resamples anymore!)
                //scaledBuffer = R_ResampleTexture( pic, width, height, width >>= 1, height >>= 1 );
                scaledBuffer = Image_process.R_MipMap(pic, width, height, preserveBorder)
                width = width shr 1
                height = height shr 1
                if (width < 1) {
                    width = 1
                }
                if (height < 1) {
                    height = 1
                }
                while (width > scaled_width._val || height > scaled_height._val) {
                    shrunk = Image_process.R_MipMap(scaledBuffer, width, height, preserveBorder)
                    scaledBuffer.clear() //R_StaticFree(scaledBuffer);
                    scaledBuffer.put(shrunk)
                    width = width shr 1
                    height = height shr 1
                    if (width < 1) {
                        width = 1
                    }
                    if (height < 1) {
                        height = 1
                    }
                }

                // one might have shrunk down below the target size
                scaled_width._val = width
                scaled_height._val = height
            }
            uploadHeight._val = scaled_height._val
            uploadWidth._val = scaled_width._val
            type = textureType_t.TT_2D

            // zero the border if desired, allowing clamped projection textures
            // even after picmip resampling or careless artists.
            if (repeat == textureRepeat_t.TR_CLAMP_TO_ZERO) {
                val rgba = ByteArray(4)
                rgba[2] = 0
                rgba[1] = rgba[2]
                rgba[0] = rgba[1]
                rgba[3] = 255.toByte()
                Image_process.R_SetBorderTexels(scaledBuffer, width, height, rgba)
            }
            if (repeat == textureRepeat_t.TR_CLAMP_TO_ZERO_ALPHA) {
                val rgba = ByteArray(4)
                rgba[2] = 255.toByte()
                rgba[1] = rgba[2]
                rgba[0] = rgba[1]
                rgba[3] = 0
                Image_process.R_SetBorderTexels(scaledBuffer, width, height, rgba)
            }
            if (generatorFunction == null && (depth == textureDepth_t.TD_BUMP && idImageManager.image_writeNormalTGA.GetBool() || depth != textureDepth_t.TD_BUMP && idImageManager.image_writeTGA.GetBool())) {
                // Optionally write out the texture to a .tga
//                String[] filename = {null};
                val filename = arrayOfNulls<String>(1)
                ImageProgramStringToCompressedFileName(imgName.toString(), filename)
                val ext = filename[0]!!.lastIndexOf('.')
                if (ext > -1) {
//			strcpy( ext, ".tga" );
                    filename[0] = filename[0]!!.substring(0, ext) + ".tga" // + filename[0].substring(ext);
                    // swap the red/alpha for the write
                    /*
                    if ( depth == TD_BUMP ) {
                        for ( int i = 0; i < scaled_width * scaled_height * 4; i += 4 ) {
                            scaledBuffer[ i ] = scaledBuffer[ i + 3 ];
                            scaledBuffer[ i + 3 ] = 0;
                        }
                    }
                    */Image_files.R_WriteTGA(filename[0], scaledBuffer, scaled_width._val, scaled_height._val, false)

                    // put it back
                    /*
                    if ( depth == TD_BUMP ) {
                        for ( int i = 0; i < scaled_width * scaled_height * 4; i += 4 ) {
                            scaledBuffer[ i + 3 ] = scaledBuffer[ i ];
                            scaledBuffer[ i ] = 0;
                        }
                    }
                    */
                }
            }

            // swap the red and alpha for rxgb support
            // do this even on tga normal maps so we only have to use
            // one fragment program
            // if the image is precompressed ( either in palletized mode or true rxgb mode )
            // then it is loaded above and the swap never happens here
            if (depth == textureDepth_t.TD_BUMP && idImageManager.image_useNormalCompression.GetInteger() != 1) {
                var i = 0
                while (i < scaled_width._val * scaled_height._val * 4) {
                    scaledBuffer.put(i + 3, scaledBuffer[i])
                    scaledBuffer.put(i, 0.toByte())
                    i += 4
                }
            }
            // upload the main image level
            Bind()
            if (internalFormat == 0x80E5) {
                /*
                 if ( depth == TD_BUMP ) {
                 for ( int i = 0; i < scaled_width * scaled_height * 4; i += 4 ) {
                 scaledBuffer[ i ] = scaledBuffer[ i + 3 ];
                 scaledBuffer[ i + 3 ] = 0;
                 }
                 }
                 */
                UploadCompressedNormalMap(scaled_width._val, scaled_height._val, scaledBuffer.array(), 0)
            } else {
                scaledBuffer.rewind()
                qgl.qglTexImage2D(
                    GL11.GL_TEXTURE_2D,
                    0,
                    internalFormat,
                    scaled_width._val,
                    scaled_height._val,
                    0,
                    GL11.GL_RGBA,
                    GL11.GL_UNSIGNED_BYTE,
                    scaledBuffer
                )
            }

            // create and upload the mip map levels, which we do in all cases, even if we don't think they are needed
            var miplevel: Int
            miplevel = 0
            while (scaled_width._val > 1 || scaled_height._val > 1) {
                // preserve the border after mip map unless repeating
                shrunk = Image_process.R_MipMap(scaledBuffer, scaled_width._val, scaled_height._val, preserveBorder)
                scaledBuffer.clear() //R_StaticFree(scaledBuffer);
                scaledBuffer.put(shrunk).flip()
                scaled_width.rightShift(1)
                scaled_height.rightShift(1)
                if (scaled_width._val < 1) {
                    scaled_width._val = 1
                }
                if (scaled_height._val < 1) {
                    scaled_height._val = 1
                }
                miplevel++

                // this is a visualization tool that shades each mip map
                // level with a different color so you can see the
                // rasterizer's texture level selection algorithm
                // Changing the color doesn't help with lumminance/alpha/intensity formats...
                if (depth == textureDepth_t.TD_DIFFUSE && idImageManager.image_colorMipLevels.GetBool()) {
                    Image_process.R_BlendOverTexture(
                        scaledBuffer,
                        scaled_width._val * scaled_height._val,
                        mipBlendColors[miplevel]
                    )
                }

                // upload the mip map
                if (internalFormat == 0x80E5) {
                    UploadCompressedNormalMap(scaled_width._val, scaled_height._val, scaledBuffer.array(), miplevel)
                } else {
                    qgl.qglTexImage2D(
                        GL11.GL_TEXTURE_2D, miplevel, internalFormat, scaled_width._val, scaled_height._val,
                        0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, scaledBuffer
                    )
                }
            }
            //
//            if (scaledBuffer != null) {
//                R_StaticFree(scaledBuffer);
//            }
            SetImageFilterAndRepeat()

            // see if we messed anything up
            GL_CheckErrors()
        }

        //
        fun Generate3DImage(
            pic: ByteBuffer?, width: Int, height: Int, picDepth: Int,
            filterParm: textureFilter_t, allowDownSizeParm: Boolean,
            repeatParm: textureRepeat_t, minDepthParm: textureDepth_t
        ) {
            var scaled_width: Int
            var scaled_height: Int
            var scaled_depth: Int
            PurgeImage()
            filter = filterParm
            allowDownSize = allowDownSizeParm
            repeat = repeatParm
            depth = minDepthParm

            // if we don't have a rendering context, just return after we
            // have filled in the parms.  We must have the values set, or
            // an image match from a shader before OpenGL starts would miss
            // the generated texture
            if (!glConfig.isInitialized) {
                return
            }

            // make sure it is a power of 2
            scaled_width = MakePowerOfTwo(width)
            scaled_height = MakePowerOfTwo(height)
            scaled_depth = MakePowerOfTwo(picDepth)
            if ((scaled_width != width) || (scaled_height != height) || (scaled_depth != picDepth)) {
                Common.common.Error("R_Create3DImage: not a power of 2 image")
            }

            // FIXME: allow picmip here
            // generate the texture number
            texNum = qgl.qglGenTextures()
            //            System.out.println(imgName + ": " + texNum);

            // select proper internal format before we resample
            // this function doesn't need to know it is 3D, so just make it very "tall"
            internalFormat = SelectInternalFormat(pic, 1, width, height * picDepth, minDepthParm, isMonochrome)
            uploadHeight._val = scaled_height
            uploadWidth._val = scaled_width
            uploadDepth._val = scaled_depth
            type = textureType_t.TT_3D

            // upload the main image level
            Bind()
            qgl.qglTexImage3D(
                GL12.GL_TEXTURE_3D, 0, internalFormat, scaled_width, scaled_height, scaled_depth,
                0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pic
            )

            // create and upload the mip map levels
            var miplevel: Int
            val scaledBuffer: ByteBuffer
            var shrunk: ByteBuffer?
            scaledBuffer = BufferUtils.createByteBuffer(scaled_width * scaled_height * scaled_depth * 4)
            scaledBuffer.put(pic) // memcpy( scaledBuffer, pic, scaled_width * scaled_height * scaled_depth * 4 );
            miplevel = 0
            while ((scaled_width > 1) || (scaled_height > 1) || (scaled_depth > 1)) {
                // preserve the border after mip map unless repeating
                shrunk = Image_process.R_MipMap3D(
                    scaledBuffer,
                    scaled_width,
                    scaled_height,
                    scaled_depth,
                    (repeat != textureRepeat_t.TR_REPEAT)
                )
                scaledBuffer.clear() // R_StaticFree(scaledBuffer);
                scaledBuffer.put(shrunk)
                scaled_width = scaled_width shr 1
                scaled_height = scaled_height shr 1
                scaled_depth = scaled_depth shr 1
                if (scaled_width < 1) {
                    scaled_width = 1
                }
                if (scaled_height < 1) {
                    scaled_height = 1
                }
                if (scaled_depth < 1) {
                    scaled_depth = 1
                }
                miplevel++

                // upload the mip map
                qgl.qglTexImage3D(
                    GL12.GL_TEXTURE_3D, miplevel, internalFormat, scaled_width, scaled_height, scaled_depth,
                    0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, scaledBuffer
                )
            }
            scaledBuffer.clear() // R_StaticFree(scaledBuffer);
            when (filter) {
                textureFilter_t.TF_DEFAULT -> {
                    qgl.qglTexParameterf(
                        GL12.GL_TEXTURE_3D,
                        GL11.GL_TEXTURE_MIN_FILTER,
                        globalImages.textureMinFilter.toFloat()
                    )
                    qgl.qglTexParameterf(
                        GL12.GL_TEXTURE_3D,
                        GL11.GL_TEXTURE_MAG_FILTER,
                        globalImages.textureMaxFilter.toFloat()
                    )
                }

                textureFilter_t.TF_LINEAR -> {
                    qgl.qglTexParameterf(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR.toFloat())
                    qgl.qglTexParameterf(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR.toFloat())
                }

                textureFilter_t.TF_NEAREST -> {
                    qgl.qglTexParameterf(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST.toFloat())
                    qgl.qglTexParameterf(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST.toFloat())
                }

                else -> Common.common.FatalError("R_CreateImage: bad texture filter")
            }
            when (repeat) {
                textureRepeat_t.TR_REPEAT -> {
                    qgl.qglTexParameterf(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT.toFloat())
                    qgl.qglTexParameterf(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT.toFloat())
                    qgl.qglTexParameterf(GL12.GL_TEXTURE_3D, GL12.GL_TEXTURE_WRAP_R, GL11.GL_REPEAT.toFloat())
                }

                textureRepeat_t.TR_CLAMP_TO_BORDER -> {
                    qgl.qglTexParameterf(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_WRAP_S, GL13.GL_CLAMP_TO_BORDER.toFloat())
                    qgl.qglTexParameterf(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_WRAP_T, GL13.GL_CLAMP_TO_BORDER.toFloat())
                }

                textureRepeat_t.TR_CLAMP_TO_ZERO, textureRepeat_t.TR_CLAMP_TO_ZERO_ALPHA, textureRepeat_t.TR_CLAMP -> {
                    qgl.qglTexParameterf(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE.toFloat())
                    qgl.qglTexParameterf(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE.toFloat())
                    qgl.qglTexParameterf(GL12.GL_TEXTURE_3D, GL12.GL_TEXTURE_WRAP_R, GL12.GL_CLAMP_TO_EDGE.toFloat())
                }

                else -> Common.common.FatalError("R_CreateImage: bad texture repeat")
            }

            // see if we messed anything up
            GL_CheckErrors()
        }

        /*
         ====================
         GenerateCubeImage

         Non-square cube sides are not allowed
         ====================
         */
        fun GenerateCubeImage(
            pics: Array<ByteBuffer?> /*[6]*/, size: Int,
            filterParm: textureFilter_t, allowDownSizeParm: Boolean,
            depthParm: textureDepth_t
        ) {
            var scaled_width: Int
            var scaled_height: Int
            val width: Int
            val height: Int
            var i: Int
            PurgeImage()
            filter = filterParm
            allowDownSize = allowDownSizeParm
            depth = depthParm
            type = textureType_t.TT_CUBIC

            // if we don't have a rendering context, just return after we
            // have filled in the parms.  We must have the values set, or
            // an image match from a shader before OpenGL starts would miss
            // the generated texture
            if (!glConfig.isInitialized) {
                return
            }
            if (!glConfig.cubeMapAvailable) {
                return
            }
            height = size
            width = height

            // generate the texture number
            texNum = qgl.qglGenTextures()
            //            System.out.println(imgName + ": " + texNum);

            // select proper internal format before we resample
            internalFormat = SelectInternalFormat(pics, 6, width, height, depth, isMonochrome)

            // don't bother with downsample for now
            scaled_width = width
            scaled_height = height
            uploadHeight._val = scaled_height
            uploadWidth._val = scaled_width
            Bind()

            // no other clamp mode makes sense
            qgl.qglTexParameteri(GL13.GL_TEXTURE_CUBE_MAP /*_EXT*/, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE)
            qgl.qglTexParameteri(GL13.GL_TEXTURE_CUBE_MAP /*_EXT*/, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE)
            when (filter) {
                textureFilter_t.TF_DEFAULT -> {
                    qgl.qglTexParameterf(
                        GL13.GL_TEXTURE_CUBE_MAP /*_EXT*/,
                        GL11.GL_TEXTURE_MIN_FILTER,
                        globalImages.textureMinFilter.toFloat()
                    )
                    qgl.qglTexParameterf(
                        GL13.GL_TEXTURE_CUBE_MAP /*_EXT*/,
                        GL11.GL_TEXTURE_MAG_FILTER,
                        globalImages.textureMaxFilter.toFloat()
                    )
                }

                textureFilter_t.TF_LINEAR -> {
                    qgl.qglTexParameterf(
                        GL13.GL_TEXTURE_CUBE_MAP /*_EXT*/,
                        GL11.GL_TEXTURE_MIN_FILTER,
                        GL11.GL_LINEAR.toFloat()
                    )
                    qgl.qglTexParameterf(
                        GL13.GL_TEXTURE_CUBE_MAP /*_EXT*/,
                        GL11.GL_TEXTURE_MAG_FILTER,
                        GL11.GL_LINEAR.toFloat()
                    )
                }

                textureFilter_t.TF_NEAREST -> {
                    qgl.qglTexParameterf(
                        GL13.GL_TEXTURE_CUBE_MAP /*_EXT*/,
                        GL11.GL_TEXTURE_MIN_FILTER,
                        GL11.GL_NEAREST.toFloat()
                    )
                    qgl.qglTexParameterf(
                        GL13.GL_TEXTURE_CUBE_MAP /*_EXT*/,
                        GL11.GL_TEXTURE_MAG_FILTER,
                        GL11.GL_NEAREST.toFloat()
                    )
                }

                else -> Common.common.FatalError("R_CreateImage: bad texture filter")
            }

            // upload the base level
            // FIXME: support 0x80E5?
            i = 0
            while (i < 6) {
                pics[i]!!.rewind()
                qgl.qglTexImage2D(
                    GL13.GL_TEXTURE_CUBE_MAP_POSITIVE_X /*_EXT*/ + i, 0, internalFormat, scaled_width, scaled_height, 0,
                    GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pics[i]
                )
                i++
            }

            // create and upload the mip map levels
            var miplevel: Int
            val shrunk = arrayOfNulls<ByteBuffer>(6)
            i = 0
            while (i < 6) {
                shrunk[i] = Image_process.R_MipMap(pics[i], scaled_width, scaled_height, false)
                i++
            }
            miplevel = 1
            while (scaled_width > 1) {
                i = 0
                while (i < 6) {
                    var shrunken: ByteBuffer?
                    qgl.qglTexImage2D(
                        GL13.GL_TEXTURE_CUBE_MAP_POSITIVE_X /*_EXT*/ + i, miplevel, internalFormat,
                        scaled_width / 2, scaled_height / 2, 0,
                        GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, shrunk[i]
                    )
                    if (scaled_width > 2) {
                        shrunken = Image_process.R_MipMap(shrunk[i], scaled_width / 2, scaled_height / 2, false)
                        // R_StaticFree(shrunk[i]);
                        shrunk[i] = shrunken
                    } else {
                        shrunk[i]!!.clear() // R_StaticFree(shrunk[i]);
                        //                        shrunken = null;
                    }
                    i++
                }
                scaled_width = scaled_width shr 1
                scaled_height = scaled_height shr 1
                miplevel++
            }

            // see if we messed anything up
            GL_CheckErrors()
        }

        //
        fun CopyFramebuffer(x: Int, y: Int, imageWidth: CInt, imageHeight: CInt, useOversizedBuffer: Boolean) {
            Bind()
            if (cvarSystem.GetCVarBool("g_lowresFullscreenFX")) {
                imageWidth._val = 512
                imageHeight._val = 512
            }

            // if the size isn't a power of 2, the image must be increased in size
            val potWidth = CInt()
            val potHeight = CInt()
            potWidth._val = MakePowerOfTwo(imageWidth._val)
            potHeight._val = MakePowerOfTwo(imageHeight._val)
            GetDownsize(imageWidth, imageHeight)
            GetDownsize(potWidth, potHeight)
            qgl.qglReadBuffer(GL11.GL_BACK)

            // only resize if the current dimensions can't hold it at all,
            // otherwise subview renderings could thrash this
            if (((useOversizedBuffer && (uploadWidth._val < potWidth._val || uploadHeight._val < potHeight._val))
                        || (!useOversizedBuffer && (uploadWidth._val != potWidth._val || uploadHeight._val != potHeight._val)))
            ) {
                uploadWidth._val = potWidth._val
                uploadHeight._val = potHeight._val
                if (potWidth._val == imageWidth._val && potHeight._val == imageHeight._val) {
                    qgl.qglCopyTexImage2D(
                        GL11.GL_TEXTURE_2D,
                        0,
                        GL11.GL_RGB8,
                        x,
                        y,
                        imageWidth._val,
                        imageHeight._val,
                        0
                    )
                } else {
                    var junk: ByteBuffer?
                    // we need to create a dummy image with power of two dimensions,
                    // then do a qglCopyTexSubImage2D of the data we want
                    // this might be a 16+ meg allocation, which could fail on _alloca
                    junk =
                        BufferUtils.createByteBuffer(potWidth._val * potHeight._val * 4) // Mem_Alloc(potWidth[0] * potHeight[0] * 4);
                    //			memset( junk, 0, potWidth * potHeight * 4 );		//!@#
//                    if (false) { // Disabling because it's unnecessary and introduces a green strip on edge of _currentRender
//			for ( int i = 0 ; i < potWidth * potHeight * 4 ; i+=4 ) {
//				junk[i+1] = 255;
//			}
//                    }
                    qgl.qglTexImage2D(
                        GL11.GL_TEXTURE_2D,
                        0,
                        GL11.GL_RGB,
                        potWidth._val,
                        potHeight._val,
                        0,
                        GL11.GL_RGBA,
                        GL11.GL_UNSIGNED_BYTE,
                        junk
                    )
                    //                    Mem_Free(junk);
                    junk = null
                    qgl.qglCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, x, y, imageWidth._val, imageHeight._val)
                }
            } else {
                // otherwise, just subimage upload it so that drivers can tell we are going to be changing
                // it and don't try and do a texture compression or some other silliness
                qgl.qglCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, x, y, imageWidth._val, imageHeight._val)
            }

            // if the image isn't a full power of two, duplicate an extra row and/or column to fix bilerps
            if (imageWidth._val != potWidth._val) {
                qgl.qglCopyTexSubImage2D(
                    GL11.GL_TEXTURE_2D,
                    0,
                    imageWidth._val,
                    0,
                    x + imageWidth._val - 1,
                    y,
                    1,
                    imageHeight._val
                )
            }
            if (imageHeight._val != potHeight._val) {
                qgl.qglCopyTexSubImage2D(
                    GL11.GL_TEXTURE_2D,
                    0,
                    0,
                    imageHeight._val,
                    x,
                    y + imageHeight._val - 1,
                    imageWidth._val,
                    1
                )
            }
            qgl.qglTexParameterf(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR.toFloat())
            qgl.qglTexParameterf(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR.toFloat())
            qgl.qglTexParameterf(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE.toFloat())
            qgl.qglTexParameterf(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE.toFloat())
            backEnd!!.c_copyFrameBuffer++
        }

        //
        /*
         ====================
         CopyDepthbuffer

         This should just be part of copyFramebuffer once we have a proper image type field
         ====================
         */
        fun CopyDepthbuffer(x: Int, y: Int, imageWidth: Int, imageHeight: Int) {
            Bind()

            // if the size isn't a power of 2, the image must be increased in size
            val potWidth: Int
            val potHeight: Int
            potWidth = MakePowerOfTwo(imageWidth)
            potHeight = MakePowerOfTwo(imageHeight)
            if (uploadWidth._val != potWidth || uploadHeight._val != potHeight) {
                uploadWidth._val = potWidth
                uploadHeight._val = potHeight
                if (potWidth == imageWidth && potHeight == imageHeight) {
                    qgl.qglCopyTexImage2D(
                        GL11.GL_TEXTURE_2D,
                        0,
                        GL11.GL_DEPTH_COMPONENT,
                        x,
                        y,
                        imageWidth,
                        imageHeight,
                        0
                    )
                } else {
                    // we need to create a dummy image with power of two dimensions,
                    // then do a qglCopyTexSubImage2D of the data we want
                    qgl.qglTexImage2D(
                        GL11.GL_TEXTURE_2D,
                        0,
                        GL11.GL_DEPTH_COMPONENT,
                        potWidth,
                        potHeight,
                        0,
                        GL11.GL_DEPTH_COMPONENT,
                        GL11.GL_UNSIGNED_BYTE,
                        null as ByteArray?
                    )
                    qgl.qglCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, x, y, imageWidth, imageHeight)
                }
            } else {
                // otherwise, just subimage upload it so that drivers can tell we are going to be changing
                // it and don't try and do a texture compression or some other silliness
                qgl.qglCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, x, y, imageWidth, imageHeight)
            }

//	qglTexParameterf( GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST );
//	qglTexParameterf( GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST );
            qgl.qglTexParameterf(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE.toFloat())
            qgl.qglTexParameterf(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE.toFloat())
        }

        /*
         =============
         RB_UploadScratchImage

         if rows = cols * 6, assume it is a cube map animation
         =============
         */
        fun UploadScratch(pic: ByteBuffer?, cols: Int, rows: Int) {
            var rows = rows
            var i: Int
            val pos = pic!!.position()

            // if rows = cols * 6, assume it is a cube map animation
            if (rows == cols * 6) {
                if (type != textureType_t.TT_CUBIC) {
                    type = textureType_t.TT_CUBIC
                    uploadWidth._val = -1 // for a non-sub upload
                }
                Bind()
                rows /= 6
                // if the scratchImage isn't in the format we want, specify it as a new texture
                if (cols != uploadWidth._val || rows != uploadHeight._val) {
                    uploadWidth._val = cols
                    uploadHeight._val = rows

                    // upload the base level
                    i = 0
                    while (i < 6) {
                        val offset = cols * rows * 4 * i
                        qgl.qglTexImage2D(
                            GL13.GL_TEXTURE_CUBE_MAP_POSITIVE_X /*_EXT*/ + i, 0, GL11.GL_RGB8, cols, rows, 0,
                            GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pic.position(pos + offset)
                        )
                        i++
                    }
                } else {
                    // otherwise, just subimage upload it so that drivers can tell we are going to be changing
                    // it and don't try and do a texture compression
                    i = 0
                    while (i < 6) {
                        val offset = cols * rows * 4 * i
                        qgl.qglTexSubImage2D(
                            GL13.GL_TEXTURE_CUBE_MAP_POSITIVE_X /*_EXT*/ + i, 0, 0, 0, cols, rows,
                            GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pic.position(pos + offset)
                        )
                        i++
                    }
                }
                pic.position(pos) //reset position.
                qgl.qglTexParameterf(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR.toFloat())
                qgl.qglTexParameterf(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR.toFloat())
                // no other clamp mode makes sense
                qgl.qglTexParameteri(GL13.GL_TEXTURE_CUBE_MAP /*_EXT*/, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE)
                qgl.qglTexParameteri(GL13.GL_TEXTURE_CUBE_MAP /*_EXT*/, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE)
            } else {
                // otherwise, it is a 2D image
                if (type != textureType_t.TT_2D) {
                    type = textureType_t.TT_2D
                    uploadWidth._val = -1 // for a non-sub upload
                }
                Bind()

                // if the scratchImage isn't in the format we want, specify it as a new texture
                if (cols != uploadWidth._val || rows != uploadHeight._val) {
                    uploadWidth._val = cols
                    uploadHeight._val = rows
                    qgl.qglTexImage2D(
                        GL11.GL_TEXTURE_2D,
                        0,
                        GL11.GL_RGB8,
                        cols,
                        rows,
                        0,
                        GL11.GL_RGBA,
                        GL11.GL_UNSIGNED_BYTE,
                        pic
                    )
                } else {
                    // otherwise, just subimage upload it so that drivers can tell we are going to be changing
                    // it and don't try and do a texture compression
                    qgl.qglTexSubImage2D(
                        GL11.GL_TEXTURE_2D,
                        0,
                        0,
                        0,
                        cols,
                        rows,
                        GL11.GL_RGBA,
                        GL11.GL_UNSIGNED_BYTE,
                        pic
                    )
                }
                qgl.qglTexParameterf(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR.toFloat())
                qgl.qglTexParameterf(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR.toFloat())
                // these probably should be clamp, but we have a lot of issues with editor
                // geometry coming out with texcoords slightly off one side, resulting in
                // a smear across the entire polygon
                if (true) {
                    qgl.qglTexParameterf(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT.toFloat())
                    qgl.qglTexParameterf(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT.toFloat())
                } else {
                    qgl.qglTexParameterf(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE.toFloat())
                    qgl.qglTexParameterf(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE.toFloat())
                }
            }
        }

        // just for resource tracking
        fun SetClassification(tag: Int) {
            classification = tag
        }

        //
        ////==========================================================
        //
        // estimates size of the GL image based on dimensions and storage type
        fun StorageSize(): Int {
            var baseSize: Int
            if (texNum == TEXTURE_NOT_LOADED) {
                return 0
            }
            when (type) {
                textureType_t.TT_2D -> baseSize = uploadWidth._val * uploadHeight._val
                textureType_t.TT_3D -> baseSize = uploadWidth._val * uploadHeight._val * uploadDepth._val
                textureType_t.TT_CUBIC -> baseSize = 6 * uploadWidth._val * uploadHeight._val
                else -> baseSize = uploadWidth._val * uploadHeight._val
            }
            baseSize *= BitsForInternalFormat(internalFormat)
            baseSize /= 8

            // account for mip mapping
            baseSize = baseSize * 4 / 3
            return baseSize
        }

        // print a one line summary of the image
        fun Print() {
            if (precompressedFile) {
                Common.common.Printf("P")
            } else if (generatorFunction != null) {
                Common.common.Printf("F")
            } else {
                Common.common.Printf(" ")
            }
            when (type) {
                textureType_t.TT_2D -> Common.common.Printf(" ")
                textureType_t.TT_3D -> Common.common.Printf("3")
                textureType_t.TT_CUBIC -> Common.common.Printf("C")
                textureType_t.TT_RECT -> Common.common.Printf("R")
                else -> Common.common.Printf("<BAD TYPE:%d>", type)
            }
            Common.common.Printf("%4d %4d ", uploadWidth, uploadHeight)
            when (filter) {
                textureFilter_t.TF_DEFAULT -> Common.common.Printf("dflt ")
                textureFilter_t.TF_LINEAR -> Common.common.Printf("linr ")
                textureFilter_t.TF_NEAREST -> Common.common.Printf("nrst ")
                else -> Common.common.Printf("<BAD FILTER:%d>", filter)
            }
            when (internalFormat) {
                GL11.GL_INTENSITY8, 1 -> Common.common.Printf("I     ")
                2, GL11.GL_LUMINANCE8_ALPHA8 -> Common.common.Printf("LA    ")
                3 -> Common.common.Printf("RGB   ")
                4 -> Common.common.Printf("RGBA  ")
                GL11.GL_LUMINANCE8 -> Common.common.Printf("L     ")
                GL11.GL_ALPHA8 -> Common.common.Printf("A     ")
                GL11.GL_RGBA8 -> Common.common.Printf("RGBA8 ")
                GL11.GL_RGB8 -> Common.common.Printf("RGB8  ")
                EXTTextureCompressionS3TC.GL_COMPRESSED_RGB_S3TC_DXT1_EXT -> Common.common.Printf("DXT1  ")
                EXTTextureCompressionS3TC.GL_COMPRESSED_RGBA_S3TC_DXT1_EXT -> Common.common.Printf("DXT1A ")
                EXTTextureCompressionS3TC.GL_COMPRESSED_RGBA_S3TC_DXT3_EXT -> Common.common.Printf("DXT3  ")
                EXTTextureCompressionS3TC.GL_COMPRESSED_RGBA_S3TC_DXT5_EXT -> Common.common.Printf("DXT5  ")
                GL11.GL_RGBA4 -> Common.common.Printf("RGBA4 ")
                GL11.GL_RGB5 -> Common.common.Printf("RGB5  ")
                0x80E5 -> Common.common.Printf("CI8   ")
                GL11.GL_COLOR_INDEX -> Common.common.Printf("CI    ")
                ARBTextureCompression.GL_COMPRESSED_RGB_ARB -> Common.common.Printf("RGBC  ")
                ARBTextureCompression.GL_COMPRESSED_RGBA_ARB -> Common.common.Printf("RGBAC ")
                0 -> Common.common.Printf("      ")
                else -> Common.common.Printf("<BAD FORMAT:%d>", internalFormat)
            }
            when (repeat) {
                textureRepeat_t.TR_REPEAT -> Common.common.Printf("rept ")
                textureRepeat_t.TR_CLAMP_TO_ZERO -> Common.common.Printf("zero ")
                textureRepeat_t.TR_CLAMP_TO_ZERO_ALPHA -> Common.common.Printf("azro ")
                textureRepeat_t.TR_CLAMP -> Common.common.Printf("clmp ")
                else -> Common.common.Printf("<BAD REPEAT:%d>", repeat)
            }
            Common.common.Printf("%4dk ", StorageSize() / 1024)
            Common.common.Printf(" %s\n", imgName.toString())
        }

        //
        // check for changed timestamp on disk and reload if necessary
        fun Reload(checkPrecompressed: Boolean, force: Boolean) {
            // always regenerate functional images
            if (generatorFunction != null) {
                Common.common.DPrintf("regenerating %s.\n", imgName)
                generatorFunction!!.run(this)
                return
            }

            // check file times
            if (!force) {
                val  /*ID_TIME_T*/current = longArrayOf(0)
                if (cubeFiles != cubeFiles_t.CF_2D) {
                    Image_files.R_LoadCubeImages(imgName.toString(), cubeFiles, null, null, current)
                } else {
                    // get the current values
                    R_LoadImageProgram(imgName.toString(), null, null, current)
                }
                if (current[0] <= timestamp[0]) {
                    return
                }
            }
            Common.common.DPrintf("reloading %s.\n", imgName.toString())
            PurgeImage()

            // force no precompressed image check, which will cause it to be reloaded
            // from source, and another precompressed file generated.
            // Load is from the front end, so the back end must be synced
            ActuallyLoadImage(checkPrecompressed, false)
        }

        fun AddReference() {
            refCount++
        }

        /*
         ================
         idImage::Downsize
         helper function that takes the current width/height and might make them smaller
         ================
         */
        fun GetDownsize(scaled_width: CInt, scaled_height: CInt) {
            var size = 0

            // perform optional picmip operation to save texture memory
            if (depth == textureDepth_t.TD_SPECULAR && idImageManager.image_downSizeSpecular.GetInteger() != 0) {
                size = idImageManager.image_downSizeSpecularLimit.GetInteger()
                if (size == 0) {
                    size = 64
                }
            } else if (depth == textureDepth_t.TD_BUMP && idImageManager.image_downSizeBump.GetInteger() != 0) {
                size = idImageManager.image_downSizeBumpLimit.GetInteger()
                if (size == 0) {
                    size = 64
                }
            } else if ((allowDownSize || idImageManager.image_forceDownSize.GetBool()) && idImageManager.image_downSize.GetInteger() != 0) {
                size = idImageManager.image_downSizeLimit.GetInteger()
                if (size == 0) {
                    size = 256
                }
            }
            if (size > 0) {
                while (scaled_width._val > size || scaled_height._val > size) {
                    if (scaled_width._val > 1) {
                        scaled_width.rightShift(1)
                    }
                    if (scaled_height._val > 1) {
                        scaled_height.rightShift(1)
                    }
                }
            }

            // clamp to minimum size
            if (scaled_width._val < 1) {
                scaled_width._val = 1
            }
            if (scaled_height._val < 1) {
                scaled_height._val = 1
            }

            // clamp size to the hardware specific upper limit
            // scale both axis down equally so we don't have to
            // deal with a half mip resampling
            // This causes a 512*256 texture to sample down to
            // 256*128 on a voodoo3, even though it could be 256*256
            while ((scaled_width._val > glConfig.maxTextureSize
                        || scaled_height._val > glConfig.maxTextureSize)
            ) {
                scaled_width.rightShift(1)
                scaled_height.rightShift(1)
            }
        }

        fun MakeDefault() {    // fill with a grid pattern
            var x: Int
            var y: Int
            val data = Array(DEFAULT_SIZE) { Array(DEFAULT_SIZE) { ByteArray(4) } }
            if (Common.com_developer.GetBool()) {
                // grey center
                y = 0
                while (y < DEFAULT_SIZE) {
                    x = 0
                    while (x < DEFAULT_SIZE) {
                        data[y][x][0] = 32
                        data[y][x][1] = 32
                        data[y][x][2] = 32
                        data[y][x][3] = 255.toByte()
                        x++
                    }
                    y++
                }

                // white border
                x = 0
                while (x < DEFAULT_SIZE) {
                    data[0][x][3] = 255.toByte()
                    data[0][x][2] = data[0][x][3]
                    data[0][x][1] = data[0][x][2]
                    data[0][x][0] = data[0][x][1]
                    data[x][0][3] = 255.toByte()
                    data[x][0][2] = data[x][0][3]
                    data[x][0][1] = data[x][0][2]
                    data[x][0][0] = data[x][0][1]
                    data[DEFAULT_SIZE - 1][x][3] = 255.toByte()
                    data[DEFAULT_SIZE - 1][x][2] = data[DEFAULT_SIZE - 1][x][3]
                    data[DEFAULT_SIZE - 1][x][1] = data[DEFAULT_SIZE - 1][x][2]
                    data[DEFAULT_SIZE - 1][x][0] = data[DEFAULT_SIZE - 1][x][1]
                    data[x][DEFAULT_SIZE - 1][3] = 255.toByte()
                    data[x][DEFAULT_SIZE - 1][2] = data[x][DEFAULT_SIZE - 1][3]
                    data[x][DEFAULT_SIZE - 1][1] = data[x][DEFAULT_SIZE - 1][2]
                    data[x][DEFAULT_SIZE - 1][0] = data[x][DEFAULT_SIZE - 1][1]
                    x++
                }
            } else {
                y = 0
                while (y < DEFAULT_SIZE) {
                    x = 0
                    while (x < DEFAULT_SIZE) {
                        data[y][x][0] = 0
                        data[y][x][1] = 0
                        data[y][x][2] = 0
                        data[y][x][3] = 0
                        x++
                    }
                    y++
                }
            }
            GenerateImage(
                ByteBuffer.wrap(flatten(data)),
                DEFAULT_SIZE,
                DEFAULT_SIZE,
                textureFilter_t.TF_DEFAULT,
                true,
                textureRepeat_t.TR_REPEAT,
                textureDepth_t.TD_DEFAULT
            )
            defaulted = true
        }

        fun SetImageFilterAndRepeat() {
            // set the minimize / maximize filtering
            when (filter) {
                textureFilter_t.TF_DEFAULT -> {
                    qgl.qglTexParameterf(
                        GL11.GL_TEXTURE_2D,
                        GL11.GL_TEXTURE_MIN_FILTER,
                        globalImages.textureMinFilter.toFloat()
                    )
                    qgl.qglTexParameterf(
                        GL11.GL_TEXTURE_2D,
                        GL11.GL_TEXTURE_MAG_FILTER,
                        globalImages.textureMaxFilter.toFloat()
                    )
                }

                textureFilter_t.TF_LINEAR -> {
                    qgl.qglTexParameterf(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR.toFloat())
                    qgl.qglTexParameterf(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR.toFloat())
                }

                textureFilter_t.TF_NEAREST -> {
                    qgl.qglTexParameterf(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST.toFloat())
                    qgl.qglTexParameterf(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST.toFloat())
                }

                else -> Common.common.FatalError("R_CreateImage: bad texture filter")
            }
            if (glConfig.anisotropicAvailable) {
                // only do aniso filtering on mip mapped images
                if (filter == textureFilter_t.TF_DEFAULT) {
                    qgl.qglTexParameterf(
                        GL11.GL_TEXTURE_2D,
                        EXTTextureFilterAnisotropic.GL_TEXTURE_MAX_ANISOTROPY_EXT,
                        globalImages.textureAnisotropy
                    )
                } else {
                    qgl.qglTexParameterf(
                        GL11.GL_TEXTURE_2D,
                        EXTTextureFilterAnisotropic.GL_TEXTURE_MAX_ANISOTROPY_EXT,
                        1.0f
                    )
                }
            }
            if (glConfig.textureLODBiasAvailable) {
                qgl.qglTexParameterf(GL11.GL_TEXTURE_2D, GL14.GL_TEXTURE_LOD_BIAS, globalImages.textureLODBias)
            }
            when (repeat) {
                textureRepeat_t.TR_REPEAT -> {
                    qgl.qglTexParameterf(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT.toFloat())
                    qgl.qglTexParameterf(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT.toFloat())
                }

                textureRepeat_t.TR_CLAMP_TO_BORDER -> {
                    qgl.qglTexParameterf(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL13.GL_CLAMP_TO_BORDER.toFloat())
                    qgl.qglTexParameterf(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL13.GL_CLAMP_TO_BORDER.toFloat())
                }

                textureRepeat_t.TR_CLAMP_TO_ZERO, textureRepeat_t.TR_CLAMP_TO_ZERO_ALPHA, textureRepeat_t.TR_CLAMP -> {
                    qgl.qglTexParameterf(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE.toFloat())
                    qgl.qglTexParameterf(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE.toFloat())
                }

                else -> Common.common.FatalError("R_CreateImage: bad texture repeat")
            }
        }

        /*
         ================
         ShouldImageBePartialCached

         Returns true if there is a precompressed image, and it is large enough
         to be worth caching
         ================
         */
        fun ShouldImageBePartialCached(): Boolean {
            if (!glConfig.textureCompressionAvailable) {
                return false
            }
            if (!idImageManager.image_useCache.GetBool()) {
                return false
            }

            // the allowDownSize flag does double-duty as don't-partial-load
            if (!allowDownSize) {
                return false
            }
            if (idImageManager.image_cacheMinK.GetInteger() <= 0) {
                return false
            }

            // if we are doing a copyFiles, make sure the original images are referenced
            if (fileSystem.PerformingCopyFiles()) {
                return false
            }
            val filename = arrayOf<String?>(null)
            val filename1: String?
            ImageProgramStringToCompressedFileName(imgName.toString(), filename)
            filename1 = filename[0]

            // get the file timestamp
            fileSystem.ReadFile(filename1!!, null, timestamp)
            if (timestamp[0] == FILE_NOT_FOUND_TIMESTAMP.toLong()) {
                return false
            }

            // open it and get the file size
            val f: idFile?
            f = fileSystem.OpenFileRead(filename1)
            if (null == f) {
                return false
            }
            val len = f.Length()
            fileSystem.CloseFile(f)
            return len > idImageManager.image_cacheMinK.GetInteger() * 1024

            // we do want to do a partial load
        }

        /*
         ================
         WritePrecompressedImage

         When we are happy with our source data, we can write out precompressed
         versions of everything to speed future load times.
         ================
         */
        fun WritePrecompressedImage() {

            // Always write the precompressed image if we're making a build
            if (!Common.com_makingBuild.GetBool()) {
                if (!idImageManager.image_writePrecompressedTextures.GetBool() || !idImageManager.image_usePrecompressedTextures.GetBool()) {
                    return
                }
            }
            if (!glConfig.isInitialized) {
                return
            }
            val filename0 = arrayOf<String?>(null)
            ImageProgramStringToCompressedFileName(imgName.toString(), filename0)
            val filename = filename0[0]
            val numLevels = NumLevelsForImageSize(uploadWidth._val, uploadHeight._val)
            if (numLevels > MAX_TEXTURE_LEVELS) {
                Common.common.Warning(
                    "R_WritePrecompressedImage: level > MAX_TEXTURE_LEVELS for image %s",
                    (filename)!!
                )
                return
            }

            // glGetTexImage only supports a small subset of all the available internal formats
            // We have to use BGRA because DDS is a windows based format
            var altInternalFormat = 0
            var bitSize = 0
            when (internalFormat) {
                0x80E5, GL11.GL_COLOR_INDEX -> {
                    // this will not work with dds viewers but we need it in this format to save disk
                    // load speed ( i.e. size )
                    altInternalFormat = GL11.GL_COLOR_INDEX
                    bitSize = 24
                }

                1, GL11.GL_INTENSITY8, GL11.GL_LUMINANCE8, 3, GL11.GL_RGB8 -> {
                    altInternalFormat = EXTBGRA.GL_BGR_EXT
                    bitSize = 24
                }

                GL11.GL_LUMINANCE8_ALPHA8, 4, GL11.GL_RGBA8 -> {
                    altInternalFormat = EXTBGRA.GL_BGRA_EXT
                    bitSize = 32
                }

                GL11.GL_ALPHA8 -> {
                    altInternalFormat = GL11.GL_ALPHA
                    bitSize = 8
                }

                else -> if (FormatIsDXT(internalFormat)) {
                    altInternalFormat = internalFormat
                } else {
                    Common.common.Warning("Unknown or unsupported format for %s", (filename)!!)
                    return
                }
            }
            if (idImageManager.image_useOffLineCompression.GetBool() && FormatIsDXT(altInternalFormat)) {
                val outFile: String = fileSystem.RelativePathToOSPath(filename!!, "fs_basepath")
                val inFile = idStr(outFile)
                inFile.StripFileExtension()
                inFile.SetFileExtension("tga")
                var format: String? = null
                if (depth == textureDepth_t.TD_BUMP) {
                    format = "RXGB +red 0.0f +green 0.5f +blue 0.5f"
                } else {
                    when (altInternalFormat) {
                        EXTTextureCompressionS3TC.GL_COMPRESSED_RGB_S3TC_DXT1_EXT -> format = "DXT1"
                        EXTTextureCompressionS3TC.GL_COMPRESSED_RGBA_S3TC_DXT1_EXT -> format = "DXT1 -alpha_threshold"
                        EXTTextureCompressionS3TC.GL_COMPRESSED_RGBA_S3TC_DXT3_EXT -> format = "DXT3"
                        EXTTextureCompressionS3TC.GL_COMPRESSED_RGBA_S3TC_DXT5_EXT -> format = "DXT5"
                    }
                }
                globalImages.AddDDSCommand(
                    va(
                        "z:/d3xp/compressonator/thecompressonator -convert \"%s\" \"%s\" %s -mipmaps\n",
                        inFile.toString(),
                        outFile,
                        format
                    )
                )
                return
            }
            val header: ddsFileHeader_t
            //	memset( &header, 0, sizeof(header) );
            header = ddsFileHeader_t()
            //            header.dwSize = sizeof(header);
            header.dwFlags = DDSF_CAPS or DDSF_PIXELFORMAT or DDSF_WIDTH or DDSF_HEIGHT
            header.dwHeight = uploadHeight._val
            header.dwWidth = uploadWidth._val

            // hack in our monochrome flag for the NV20 optimization
            if (isMonochrome[0]) {
                header.dwFlags = header.dwFlags or DDSF_ID_MONOCHROME
            }
            if (FormatIsDXT(altInternalFormat)) {
                // size (in bytes) of the compressed base image
                header.dwFlags = header.dwFlags or DDSF_LINEARSIZE
                header.dwPitchOrLinearSize = (((uploadWidth._val + 3) / 4) * ((uploadHeight._val + 3) / 4)
                        * (if (altInternalFormat <= EXTTextureCompressionS3TC.GL_COMPRESSED_RGBA_S3TC_DXT1_EXT) 8 else 16))
            } else {
                // 4 Byte aligned line width (from nv_dds)
                header.dwFlags = header.dwFlags or DDSF_PITCH
                header.dwPitchOrLinearSize = ((uploadWidth._val * bitSize + 31) and -32) shr 3
            }
            header.dwCaps1 = DDSF_TEXTURE
            if (numLevels > 1) {
                header.dwMipMapCount = numLevels
                header.dwFlags = header.dwFlags or DDSF_MIPMAPCOUNT
                header.dwCaps1 = header.dwCaps1 or (DDSF_MIPMAP or DDSF_COMPLEX)
            }

//            header.ddspf.dwSize = sizeof(header.ddspf);
            if (FormatIsDXT(altInternalFormat)) {
                header.ddspf!!.dwFlags = DDSF_FOURCC
                when (altInternalFormat) {
                    EXTTextureCompressionS3TC.GL_COMPRESSED_RGB_S3TC_DXT1_EXT -> header.ddspf!!.dwFourCC =
                        DDS_MAKEFOURCC('D'.code, 'X'.code, 'T'.code, '1'.code)

                    EXTTextureCompressionS3TC.GL_COMPRESSED_RGBA_S3TC_DXT1_EXT -> {
                        header.ddspf!!.dwFlags = header.ddspf!!.dwFlags or DDSF_ALPHAPIXELS
                        header.ddspf!!.dwFourCC = DDS_MAKEFOURCC('D'.code, 'X'.code, 'T'.code, '1'.code)
                    }

                    EXTTextureCompressionS3TC.GL_COMPRESSED_RGBA_S3TC_DXT3_EXT -> header.ddspf!!.dwFourCC =
                        DDS_MAKEFOURCC('D'.code, 'X'.code, 'T'.code, '3'.code)

                    EXTTextureCompressionS3TC.GL_COMPRESSED_RGBA_S3TC_DXT5_EXT -> header.ddspf!!.dwFourCC =
                        DDS_MAKEFOURCC('D'.code, 'X'.code, 'T'.code, '5'.code)
                }
            } else {
                header.ddspf!!.dwFlags = if ((internalFormat == 0x80E5)) DDSF_RGB or DDSF_ID_INDEXCOLOR else DDSF_RGB
                header.ddspf!!.dwRGBBitCount = bitSize
                when (altInternalFormat) {
                    EXTBGRA.GL_BGRA_EXT, GL11.GL_LUMINANCE_ALPHA -> {
                        header.ddspf!!.dwFlags = header.ddspf!!.dwFlags or DDSF_ALPHAPIXELS
                        header.ddspf!!.dwABitMask = -0x1000000
                        header.ddspf!!.dwRBitMask = 0x00FF0000
                        header.ddspf!!.dwGBitMask = 0x0000FF00
                        header.ddspf!!.dwBBitMask = 0x000000FF
                    }

                    EXTBGRA.GL_BGR_EXT, GL11.GL_LUMINANCE, GL11.GL_COLOR_INDEX -> {
                        header.ddspf!!.dwRBitMask = 0x00FF0000
                        header.ddspf!!.dwGBitMask = 0x0000FF00
                        header.ddspf!!.dwBBitMask = 0x000000FF
                    }

                    GL11.GL_ALPHA -> {
                        header.ddspf!!.dwFlags = DDSF_ALPHAPIXELS
                        header.ddspf!!.dwABitMask = -0x1000000
                    }

                    else -> {
                        Common.common.Warning("Unknown or unsupported format for %s", (filename)!!)
                        return
                    }
                }
            }
            val f: idFile? = fileSystem.OpenFileWrite(filename!!)
            if (f == null) {
                Common.common.Warning("Could not open %s trying to write precompressed image", (filename))
                return
            }
            Common.common.Printf("Writing precompressed image: %s\n", (filename))
            f.WriteString("DDS ") //, 4);
            f.Write(header.Write() /*, sizeof(header) */)

            // bind to the image so we can read back the contents
            Bind()
            qgl.qglPixelStorei(GL11.GL_PACK_ALIGNMENT, 1) // otherwise small rows get padded to 32 bits
            var uw = uploadWidth._val
            var uh = uploadHeight._val

            // Will be allocated first time through the loop
            var data: ByteBuffer? = null
            for (level in 0 until numLevels) {
                var size = 0
                if (FormatIsDXT(altInternalFormat)) {
                    size = (((uw + 3) / 4) * ((uh + 3) / 4)
                            * (if (altInternalFormat <= EXTTextureCompressionS3TC.GL_COMPRESSED_RGBA_S3TC_DXT1_EXT) 8 else 16))
                } else {
                    size = uw * uh * (bitSize / 8)
                }
                if (data == null) {
                    data = ByteBuffer.allocate(size) // R_StaticAlloc(size);
                }
                if (FormatIsDXT(altInternalFormat)) {
                    qgl.qglGetCompressedTexImageARB(GL11.GL_TEXTURE_2D, level, data)
                } else {
                    qgl.qglGetTexImage(GL11.GL_TEXTURE_2D, level, altInternalFormat, GL11.GL_UNSIGNED_BYTE, data)
                }
                f.Write((data)!!, size)
                uw /= 2
                uh /= 2
                if (uw < 1) {
                    uw = 1
                }
                if (uh < 1) {
                    uh = 1
                }
            }

//            if (data != null) {
//                R_StaticFree(data);
//            }
//
            fileSystem.CloseFile(f)
        }

        fun CheckPrecompressedImage(fullLoad: Boolean): Boolean {
            if (!glConfig.isInitialized || !glConfig.textureCompressionAvailable) {
                return false
            }
            if (true) { // ( _D3XP had disabled ) - Allow grabbing of DDS's from original Doom pak files
                // if we are doing a copyFiles, make sure the original images are referenced
                if (fileSystem.PerformingCopyFiles()) {
                    return false
                }
            }
            if (depth == textureDepth_t.TD_BUMP && idImageManager.image_useNormalCompression.GetInteger() != 2) {
                return false
            }

            // god i love last minute hacks :-)
            // me too.
            if ((Common.com_machineSpec.GetInteger() >= 1) && (Common.com_videoRam.GetInteger() >= 128) && (imgName.Icmpn(
                    "lights/",
                    7
                ) == 0)
            ) {
                return false //TODO:enable this by using openCL for the values above.
            }
            if ((imgName.toString().contains("mars")
                        || imgName.toString().contains("planet"))
            ) {
//                System.out.println(">>>>>>>>>>>" + DEBUG_CheckPrecompressedImage);
//                return true;
            }
            DEBUG_CheckPrecompressedImage++
            val filename = arrayOf<String?>(null)
            ImageProgramStringToCompressedFileName(imgName.toString(), filename)
            //            System.out.println("====" + filename[0]);

            // get the file timestamp
            val precompTimestamp/*ID_TIME_T */ = longArrayOf(0)
            fileSystem.ReadFile(filename[0]!!, null, precompTimestamp)
            if (precompTimestamp[0] == FILE_NOT_FOUND_TIMESTAMP.toLong()) {
                return false
            }
            if (null == generatorFunction && timestamp[0] != FILE_NOT_FOUND_TIMESTAMP.toLong()) {
                if (precompTimestamp[0] < timestamp[0]) {
                    // The image has changed after being precompressed
                    return false
                }
            }
            timestamp[0] = precompTimestamp[0]

            // open it and just read the header
            val f: idFile?
            f = fileSystem.OpenFileRead(filename[0]!!)
            if (null == f) {
                return false
            }
            var len = f.Length()
            if (len < ddsFileHeader_t.BYTES) {
                fileSystem.CloseFile(f)
                return false
            }
            if (!fullLoad && len > idImageManager.image_cacheMinK.GetInteger() * 1024) {
                len = idImageManager.image_cacheMinK.GetInteger() * 1024
            }
            val data = ByteBuffer.allocate(len) // R_StaticAlloc(len);
            f.Read(data)
            fileSystem.CloseFile(f)
            data.order(ByteOrder.LITTLE_ENDIAN)
            val magic = LittleLong(data.getInt()).toLong()
            data.position(4) //, 4);
            val _header = ddsFileHeader_t(data)
            val ddspf_dwFlags = LittleLong(_header.ddspf!!.dwFlags)
            if (magic != DDS_MAKEFOURCC('D'.code, 'D'.code, 'S'.code, ' '.code).toLong()) {
                Common.common.Printf("CheckPrecompressedImage( %s ): magic != 'DDS '\n", imgName.toString())
                //                R_StaticFree(data);
                return false
            }

            // if we don't support color index textures, we must load the full image
            // should we just expand the 256 color image to 32 bit for upload?
            if (((ddspf_dwFlags and DDSF_ID_INDEXCOLOR) != 0) && !glConfig.sharedTexturePaletteAvailable) {
//                R_StaticFree(daDta);
                return false
            }

            // upload all the levels
            UploadPrecompressedImage(data, len) //TODO:disables all pictures, also makes shit blocky.

//            R_StaticFree(data);
            return true
        }

        fun UploadPrecompressedImage(data: ByteBuffer?, len: Int) {
            data!!.position(4) //, 4)
            val header = ddsFileHeader_t((data))

            // ( not byte swapping dwReserved1 dwReserved2 )
            header.dwSize = LittleLong(header.dwSize)
            header.dwFlags = LittleLong(header.dwFlags)
            header.dwHeight = LittleLong(header.dwHeight)
            header.dwWidth = LittleLong(header.dwWidth)
            header.dwPitchOrLinearSize = LittleLong(header.dwPitchOrLinearSize)
            header.dwDepth = LittleLong(header.dwDepth)
            header.dwMipMapCount = LittleLong(header.dwMipMapCount)
            header.dwCaps1 = LittleLong(header.dwCaps1)
            header.dwCaps2 = LittleLong(header.dwCaps2)
            header.ddspf!!.dwSize = LittleLong(header.ddspf!!.dwSize)
            header.ddspf!!.dwFlags = LittleLong(header.ddspf!!.dwFlags)
            header.ddspf!!.dwFourCC = LittleLong(header.ddspf!!.dwFourCC)
            header.ddspf!!.dwRGBBitCount = LittleLong(header.ddspf!!.dwRGBBitCount)
            header.ddspf!!.dwRBitMask = LittleLong(header.ddspf!!.dwRBitMask)
            header.ddspf!!.dwGBitMask = LittleLong(header.ddspf!!.dwGBitMask)
            header.ddspf!!.dwBBitMask = LittleLong(header.ddspf!!.dwBBitMask)
            header.ddspf!!.dwABitMask = LittleLong(header.ddspf!!.dwABitMask)

            // generate the texture number
            texNum = qgl.qglGenTextures()
            //            System.out.println(imgName + ": " + texNum);

//            if (texNum == 58) {
//                DBG_UploadPrecompressedImage = data.duplicate();
//                DBG_UploadPrecompressedImage.order(data.order());
//            } else
//            if (texNum == 59) {
//                texNum = null;
//                final int pos = data.position();
//                data = DBG_UploadPrecompressedImage.duplicate();
//                data.order(DBG_UploadPrecompressedImage.order());
//                UploadPrecompressedImage(data, len);
//                return;
//            }
            var externalFormat = 0
            precompressedFile = true
            uploadWidth._val = header.dwWidth
            uploadHeight._val = header.dwHeight
            if ((header.ddspf!!.dwFlags and DDSF_FOURCC) != 0) {
//                System.out.printf("%d\n", header.ddspf.dwFourCC);
//                switch (bla[DEBUG_dwFourCC++]) {
                when (header.ddspf!!.dwFourCC) {
                    DDS_MAKEFOURCC_DXT1 -> if ((header.ddspf!!.dwFlags and DDSF_ALPHAPIXELS) != 0) {
                        internalFormat = EXTTextureCompressionS3TC.GL_COMPRESSED_RGBA_S3TC_DXT1_EXT
                        //                            System.out.printf("GL_COMPRESSED_RGBA_S3TC_DXT1_EXT\n");
                    } else {
                        internalFormat = EXTTextureCompressionS3TC.GL_COMPRESSED_RGB_S3TC_DXT1_EXT
                        //                            System.out.printf("GL_COMPRESSED_RGB_S3TC_DXT1_EXT\n");
                    }

                    DDS_MAKEFOURCC_DXT3 -> internalFormat = EXTTextureCompressionS3TC.GL_COMPRESSED_RGBA_S3TC_DXT3_EXT
                    DDS_MAKEFOURCC_DXT5 -> internalFormat = EXTTextureCompressionS3TC.GL_COMPRESSED_RGBA_S3TC_DXT5_EXT
                    DDS_MAKEFOURCC_RXGB -> internalFormat = EXTTextureCompressionS3TC.GL_COMPRESSED_RGBA_S3TC_DXT5_EXT
                    else -> {
                        Common.common.Warning("Invalid compressed internal format\n")
                        return
                    }
                }
            } else if (((header.ddspf!!.dwFlags and DDSF_RGBA) != 0) && header.ddspf!!.dwRGBBitCount == 32) {
                externalFormat = EXTBGRA.GL_BGRA_EXT
                internalFormat = GL11.GL_RGBA8
            } else if (((header.ddspf!!.dwFlags and DDSF_RGB) != 0) && header.ddspf!!.dwRGBBitCount == 32) {
                externalFormat = EXTBGRA.GL_BGRA_EXT
                internalFormat = GL11.GL_RGBA8
            } else if (((header.ddspf!!.dwFlags and DDSF_RGB) != 0) && header.ddspf!!.dwRGBBitCount == 24) {
                if ((header.ddspf!!.dwFlags and DDSF_ID_INDEXCOLOR) != 0) {
                    externalFormat = GL11.GL_COLOR_INDEX
                    internalFormat = 0x80E5
                } else {
                    externalFormat = EXTBGRA.GL_BGR_EXT
                    internalFormat = GL11.GL_RGB8
                }
            } else if (header.ddspf!!.dwRGBBitCount == 8) {
                externalFormat = GL11.GL_ALPHA
                internalFormat = GL11.GL_ALPHA8
            } else {
                Common.common.Warning("Invalid uncompressed internal format\n")
                return
            }

            // we need the monochrome flag for the NV20 optimized path
            if ((header.dwFlags and DDSF_ID_MONOCHROME) != 0) {
                isMonochrome[0] = true
            }
            type = textureType_t.TT_2D // FIXME: we may want to support pre-compressed cube maps in the future
            Bind()
            var numMipmaps = 1
            if ((header.dwFlags and DDSF_MIPMAPCOUNT) != 0) {
                numMipmaps = header.dwMipMapCount
            }
            var uw = uploadWidth._val
            var uh = uploadHeight._val

            // We may skip some mip maps if we are downsizing
            var skipMip = 0
            GetDownsize(uploadWidth, uploadHeight)
            var offset = ddsFileHeader_t.BYTES + 4 // + sizeof(ddsFileHeader_t) + 4;
            for (i in 0 until numMipmaps) {
                val size: Int
                if (FormatIsDXT(internalFormat)) {
                    size = (((uw + 3) / 4) * ((uh + 3) / 4)
                            * (if (internalFormat <= EXTTextureCompressionS3TC.GL_COMPRESSED_RGBA_S3TC_DXT1_EXT) 8 else 16))
                } else {
                    size = uw * uh * (header.ddspf!!.dwRGBBitCount / 8)
                }
                if (uw > uploadWidth._val || uh > uploadHeight._val) {
                    skipMip++
                } else {
                    val imageData = BufferUtils.createByteBuffer(size)
                    imageData.put(data.array(), offset, size)
                    imageData.order(ByteOrder.LITTLE_ENDIAN) //TODO: should ByteOrder be reverted? <data> uses LITTLE_ENDIAN.
                    imageData.flip() //FUCKME: the lwjgl version of <glCompressedTexImage2DARB> uses bytebuffer.remaining() as size.
                    if (FormatIsDXT(internalFormat)) { //TODO: remove blocky crap!
                        qgl.qglCompressedTexImage2DARB(
                            GL11.GL_TEXTURE_2D,
                            i - skipMip,
                            internalFormat,
                            uw,
                            uh,
                            0,
                            size,
                            imageData
                        )
                        //                        System.out.printf("qglCompressedTexImage2DARB(%d)\n", imageData.get(0) & 0xFF);
                    } else {
                        qgl.qglTexImage2D(
                            GL11.GL_TEXTURE_2D,
                            i - skipMip,
                            internalFormat,
                            uw,
                            uh,
                            0,
                            externalFormat,
                            GL11.GL_UNSIGNED_BYTE,
                            imageData
                        )
                    }
                }
                offset += size
                uw /= 2
                uh /= 2
                if (uw < 1) {
                    uw = 1
                }
                if (uh < 1) {
                    uh = 1
                }
            }
            SetImageFilterAndRepeat()
        }

        fun ActuallyLoadImage(checkForPrecompressed: Boolean, fromBackEnd: Boolean) {
            val width = intArrayOf(0)
            val height = intArrayOf(0)
            var pic: ByteBuffer? = null
            if (imgName.equals("guis/assets/splash/launch")) {
//                return;
            }
            //            System.out.println((DBG_ActuallyLoadImage++) + " " + imgName);

            // this is the ONLY place generatorFunction will ever be called
            if (generatorFunction != null) {
                generatorFunction!!.run(this)
                return
            }

            // if we are a partial image, we are only going to load from a compressed file
            if (isPartialImage) {
                if (CheckPrecompressedImage(false)) {
                    return
                }
                // this is an error -- the partial image failed to load
                MakeDefault()
                return
            }

            //
            // load the image from disk
            //
            if (cubeFiles != cubeFiles_t.CF_2D) {
                val pics = arrayOfNulls<ByteBuffer>(6) //TODO:FIXME!

                // we don't check for pre-compressed cube images currently
                Image_files.R_LoadCubeImages(imgName.toString(), cubeFiles, pics, width, timestamp)
                if (pics[0] == null) {
                    Common.common.Warning("Couldn't load cube image: %s", imgName.toString())
                    MakeDefault()
                    return
                }
                GenerateCubeImage( /*(const byte **)*/pics, width[0], filter, allowDownSize, depth)
                precompressedFile = false
                //
//                for (int i = 0; i < 6; i++) {
//                    if (pics[0][i] != 0) {
//                        R_StaticFree(pics[i]);
//                    }
//                }
            } else {
                // see if we have a pre-generated image file that is
                // already image processed and compressed
                if (checkForPrecompressed && idImageManager.image_usePrecompressedTextures.GetBool()) {
                    if (CheckPrecompressedImage(true)) {
                        // we got the precompressed image
                        return
                    }
                    // fall through to load the normal image
                }
                run {
                    val depth: Array<textureDepth_t> = arrayOf(this.depth)
                    pic = R_LoadImageProgram(imgName.toString(), width, height, timestamp, depth)
                    this.depth = depth.get(0)
                }
                if (pic == null) {
                    Common.common.Warning("Couldn't load image: %s", imgName)
                    MakeDefault()
                    return
                }
                /*
                 // swap the red and alpha for rxgb support
                 // do this even on tga normal maps so we only have to use
                 // one fragment program
                 // if the image is precompressed ( either in palletized mode or true rxgb mode )
                 // then it is loaded above and the swap never happens here
                 if ( depth == TD_BUMP && globalImages.image_useNormalCompression.GetInteger() != 1 ) {
                 for ( int i = 0; i < width * height * 4; i += 4 ) {
                 pic[ i + 3 ] = pic[ i ];
                 pic[ i ] = 0;
                 }
                 }
                 */
                // build a hash for checking duplicate image files
                // NOTE: takes about 10% of image load times (SD)
                // may not be strictly necessary, but some code uses it, so let's leave it in
                //imageHash = MD4_BlockChecksum(pic, width[0] * height[0] * 4);
                GenerateImage(pic, width[0], height[0], filter, allowDownSize, repeat, depth)
                //why, because we rock!
                precompressedFile = false

//                R_StaticFree(pic);
                // write out the precompressed version of this file if needed
                WritePrecompressedImage()
            }
        }

        fun StartBackgroundImageLoad() {
            if (imageManager.numActiveBackgroundImageLoads >= idImageManager.MAX_BACKGROUND_IMAGE_LOADS) {
                return
            }
            if (idImageManager.image_showBackgroundLoads.GetBool()) {
                Common.common.Printf("idImage::StartBackgroundImageLoad: %s\n", imgName.toString())
            }
            backgroundLoadInProgress = true
            if (!precompressedFile) {
                Common.common.Warning(
                    "idImageManager::StartBackgroundImageLoad: %s wasn't a precompressed file",
                    imgName.toString()
                )
                return
            }
            bglNext = globalImages.backgroundImageLoads
            globalImages.backgroundImageLoads = this
            val filename = arrayOf<String?>(null)
            ImageProgramStringToCompressedFileName(imgName, filename)
            bgl.completed = false
            bgl.f = fileSystem.OpenFileRead(filename[0]!!)
            if (null == bgl.f) {
                Common.common.Warning("idImageManager::StartBackgroundImageLoad: Couldn't load %s", imgName.toString())
                return
            }
            bgl.file.position = 0
            bgl.file.length = bgl.f!!.Length()
            if (bgl.file.length < ddsFileHeader_t.BYTES) {
                Common.common.Warning(
                    "idImageManager::StartBackgroundImageLoad: %s had a bad file length",
                    imgName.toString()
                )
                return
            }
            bgl.file.buffer = ByteBuffer.allocate(bgl.file.length)
            fileSystem.BackgroundDownload(bgl)
            imageManager.numActiveBackgroundImageLoads++

            // purge some images if necessary
            var totalSize = 0
            var check = globalImages.cacheLRU.cacheUsageNext
            while (check !== globalImages.cacheLRU) {
                totalSize += check!!.StorageSize()
                check = check.cacheUsageNext
            }
            val needed = StorageSize()
            while ((totalSize + needed) > idImageManager.image_cacheMegs.GetFloat() * 1024 * 1024) {
                // purge the least recently used
                val check = globalImages.cacheLRU.cacheUsagePrev
                if (check!!.texNum != TEXTURE_NOT_LOADED) {
                    totalSize -= check.StorageSize()
                    if (idImageManager.image_showBackgroundLoads.GetBool()) {
                        Common.common.Printf("purging %s\n", check.imgName.toString())
                    }
                    check.PurgeImage()
                }
                // remove it from the cached list
                check.cacheUsageNext!!.cacheUsagePrev = check.cacheUsagePrev
                check.cacheUsagePrev!!.cacheUsageNext = check.cacheUsageNext
                check.cacheUsageNext = null
                check.cacheUsagePrev = null
            }
        }

        /*
         ================
         BitsForInternalFormat

         Used for determining memory utilization
         ================
         */
        fun BitsForInternalFormat(internalFormat: Int): Int {
            when (internalFormat) {
                GL11.GL_INTENSITY8, 1 -> return 8
                2, GL11.GL_LUMINANCE8_ALPHA8 -> return 16
                3 -> return 32 // on some future hardware, this may actually be 24, but be conservative
                4 -> return 32
                GL11.GL_LUMINANCE8 -> return 8
                GL11.GL_ALPHA8 -> return 8
                GL11.GL_RGBA8 -> return 32
                GL11.GL_RGB8 -> return 32 // on some future hardware, this may actually be 24, but be conservative
                EXTTextureCompressionS3TC.GL_COMPRESSED_RGB_S3TC_DXT1_EXT -> return 4
                EXTTextureCompressionS3TC.GL_COMPRESSED_RGBA_S3TC_DXT1_EXT -> return 4
                EXTTextureCompressionS3TC.GL_COMPRESSED_RGBA_S3TC_DXT3_EXT -> return 8
                EXTTextureCompressionS3TC.GL_COMPRESSED_RGBA_S3TC_DXT5_EXT -> return 8
                GL11.GL_RGBA4 -> return 16
                GL11.GL_RGB5 -> return 16
                0x80E5 -> return 8
                GL11.GL_COLOR_INDEX -> return 8
                ARBTextureCompression.GL_COMPRESSED_RGB_ARB -> return 4 // not sure
                ARBTextureCompression.GL_COMPRESSED_RGBA_ARB -> return 8 // not sure
                else -> Common.common.Error("R_BitsForInternalFormat: BAD FORMAT:%d", internalFormat)
            }
            return 0
        }

        /*
         ==================
         UploadCompressedNormalMap

         Create a 256 color palette to be used by compressed normal maps
         ==================
         */
        fun UploadCompressedNormalMap(width: Int, height: Int, rgba: ByteArray, mipLevel: Int) {
            val normals: ByteArray
            var `in`: Int
            var out: Int
            var i: Int
            var j: Int
            var x: Int
            var y: Int
            var z: Int
            val row: Int

            // OpenGL's pixel packing rule
            row = max(width.toFloat(), 4.0f).toInt()
            normals = ByteArray(row * height)
            if (normals == null) {
                Common.common.Error("R_UploadCompressedNormalMap: _alloca failed")
            }
            `in` = 0
            out = 0
            i = 0
            while (i < height) {
                j = 0
                while (j < width) {
                    x = rgba[`in` + (j * 4) + 0].toInt()
                    y = rgba[`in` + (j * 4) + 1].toInt()
                    z = rgba[`in` + (j * 4) + 2].toInt()
                    var c: Int
                    if ((x == 128) && (y == 128) && (z == 128)) {
                        // the "nullnormal" color
                        c = 255
                    } else {
                        c =
                            (globalImages.originalToCompressed[x].toInt() shl 4) or globalImages.originalToCompressed[y].toInt()
                        if (c == 255) {
                            c = 254 // don't use the nullnormal color
                        }
                    }
                    normals[out + j] = c.toByte()
                    j++
                }
                i++
                out += row
                `in` += width * 4
            }
            if (mipLevel == 0) {
                // Optionally write out the paletized normal map to a .tga
                if (idImageManager.image_writeNormalTGAPalletized.GetBool()) {
                    val filename = arrayOf<String?>(null)
                    ImageProgramStringToCompressedFileName(imgName, filename)
                    val ext = filename[0]!!.lastIndexOf('.')
                    if (ext != -1) {
                        filename[0] = filename[0]!!.substring(0, ext) + "_pal.tga" //strcpy(ext, "_pal.tga");
                        R_WritePalTGA(filename[0], normals, globalImages.compressedPalette, width, height)
                    }
                }
            }
            if (glConfig.sharedTexturePaletteAvailable) {
                qgl.qglTexImage2D(
                    GL11.GL_TEXTURE_2D,
                    mipLevel,
                    0x80E5,
                    width,
                    height,
                    0,
                    GL11.GL_COLOR_INDEX,
                    GL11.GL_UNSIGNED_BYTE,
                    normals
                )
            }
        }

        fun  /*GLenum*/SelectInternalFormat(
            dataPtrs: ByteBuffer?, numDataPtrs: Int, width: Int, height: Int,
            minimumDepth: textureDepth_t, monochromeResult: BooleanArray
        ): Int {
            return SelectInternalFormat(arrayOf(dataPtrs), numDataPtrs, width, height, minimumDepth, monochromeResult)
        }

        /*
         ===============
         SelectInternalFormat

         This may need to scan six cube map images
         ===============
         */
        fun  /*GLenum*/SelectInternalFormat(
            dataPtrs: Array<ByteBuffer?>, numDataPtrs: Int, width: Int, height: Int,
            minimumDepth: textureDepth_t, monochromeResult: BooleanArray
        ): Int {
            var minimumDepth = minimumDepth
            var i: Int
            val c: Int
            var pos: Int
            var scan: ByteBuffer?
            var rgbOr: Int
            var rgbAnd: Int
            var aOr: Int
            var aAnd: Int
            var rgbDiffer: Int
            var rgbaDiffer: Int

            // determine if the rgb channels are all the same
            // and if either all rgb or all alpha are 255
            c = width * height
            rgbDiffer = 0
            rgbaDiffer = 0
            rgbOr = 0
            rgbAnd = -1
            aOr = 0
            aAnd = -1
            monochromeResult[0] = true // until shown otherwise
            for (side in 0 until numDataPtrs) {
                scan = dataPtrs[side]
                i = 0
                pos = 0
                while (i < c) {
                    var cOr: Int
                    var cAnd: Int
                    aOr = aOr or scan!![pos + 3].toInt()
                    aAnd = aAnd and scan[pos + 3].toInt()
                    cOr = scan[pos + 0].toInt() or scan[pos + 1].toInt() or scan[pos + 2].toInt()
                    cAnd = scan[pos + 0].toInt() and scan[pos + 1].toInt() and scan[pos + 2].toInt()

                    // if rgb are all the same, the or and and will match
                    rgbDiffer = rgbDiffer or (cOr xor cAnd)

                    // our "isMonochrome" test is more lax than rgbDiffer,
                    // allowing the values to be off by several units and
                    // still use the NV20 mono path
                    if (monochromeResult[0]) {
                        if ((abs((scan[pos + 0] - scan[pos + 1]).toFloat()) > 16
                                    || abs((scan[pos + 0] - scan[pos + 2]).toFloat()) > 16)
                        ) {
                            monochromeResult[0] = false
                        }
                    }
                    rgbOr = rgbOr or cOr
                    rgbAnd = rgbAnd and cAnd
                    cOr = cOr or scan[pos + 3].toInt()
                    cAnd = cAnd and scan[pos + 3].toInt()
                    rgbaDiffer = rgbaDiffer or (cOr xor cAnd)
                    i++
                    pos += 4
                }
            }

            // we assume that all 0 implies that the alpha channel isn't needed,
            // because some tools will spit out 32 bit images with a 0 alpha instead
            // of 255 alpha, but if the alpha actually is referenced, there will be
            // different behavior in the compressed vs uncompressed states.
            val needAlpha: Boolean
            needAlpha = aAnd != 255 && aOr != 0

            // catch normal maps first
            if (minimumDepth == textureDepth_t.TD_BUMP) {
                if (idImageManager.image_useCompression.GetBool() && (idImageManager.image_useNormalCompression.GetInteger() == 1) && glConfig.sharedTexturePaletteAvailable) {
                    // image_useNormalCompression should only be set to 1 on nv_10 and nv_20 paths
                    return 0x80E5
                } else return if (idImageManager.image_useCompression.GetBool() && (idImageManager.image_useNormalCompression.GetInteger() != 0) && glConfig.textureCompressionAvailable) {
                    // image_useNormalCompression == 2 uses rxgb format which produces really good quality for medium settings
                    EXTTextureCompressionS3TC.GL_COMPRESSED_RGBA_S3TC_DXT5_EXT
                } else {
                    // we always need the alpha channel for bump maps for swizzling
                    GL11.GL_RGBA8
                }
            }

            // allow a complete override of image compression with a cvar
            if (!idImageManager.image_useCompression.GetBool()) {
                minimumDepth = textureDepth_t.TD_HIGH_QUALITY
            }
            if (minimumDepth == textureDepth_t.TD_SPECULAR) {
                // we are assuming that any alpha channel is unintentional
                return if (glConfig.textureCompressionAvailable) {
                    EXTTextureCompressionS3TC.GL_COMPRESSED_RGB_S3TC_DXT1_EXT
                } else {
                    GL11.GL_RGB5
                }
            }
            if (minimumDepth == textureDepth_t.TD_DIFFUSE) {
                // we might intentionally have an alpha channel for alpha tested textures
                if (glConfig.textureCompressionAvailable) {
                    return if (!needAlpha) {
                        EXTTextureCompressionS3TC.GL_COMPRESSED_RGB_S3TC_DXT1_EXT
                    } else {
                        EXTTextureCompressionS3TC.GL_COMPRESSED_RGBA_S3TC_DXT3_EXT
                    }
                } else return if ((aAnd == 255 || aOr == 0)) {
                    GL11.GL_RGB5
                } else {
                    GL11.GL_RGBA4
                }
            }

            // there will probably be some drivers that don't
            // correctly handle the intensity/alpha/luminance/luminance+alpha
            // formats, so provide a fallback that only uses the rgb/rgba formats
            if (!idImageManager.image_useAllFormats.GetBool()) {
                // pretend rgb is varying and inconsistant, which
                // prevents any of the more compact forms
                rgbDiffer = 1
                rgbaDiffer = 1
                rgbAnd = 0
            }

            // cases without alpha
            if (!needAlpha) {
                if (minimumDepth == textureDepth_t.TD_HIGH_QUALITY) {
                    return GL11.GL_RGB8 // four bytes
                }
                return if (glConfig.textureCompressionAvailable) {
                    EXTTextureCompressionS3TC.GL_COMPRESSED_RGB_S3TC_DXT1_EXT // half byte
                } else GL11.GL_RGB5
                // two bytes
            }

            // cases with alpha
            if (rgbaDiffer == 0) {
                return if (minimumDepth != textureDepth_t.TD_HIGH_QUALITY && glConfig.textureCompressionAvailable) {
                    EXTTextureCompressionS3TC.GL_COMPRESSED_RGBA_S3TC_DXT3_EXT // one byte
                } else GL11.GL_INTENSITY8
                // single byte for all channels
            }
            if (false) {
                // we don't support alpha textures any more, because there
                // is a discrepancy in the definition of TEX_ENV_COMBINE that
                // causes them to be treated as 0 0 0 A, instead of 1 1 1 A as
                // normal texture modulation treats them
                if (rgbAnd == 255) {
                    return GL11.GL_ALPHA8 // single byte, only alpha
                }
            }
            if (minimumDepth == textureDepth_t.TD_HIGH_QUALITY) {
                return GL11.GL_RGBA8 // four bytes
            }
            if (glConfig.textureCompressionAvailable) {
                return EXTTextureCompressionS3TC.GL_COMPRESSED_RGBA_S3TC_DXT3_EXT // one byte
            }
            return if (rgbDiffer == 0) {
                GL11.GL_LUMINANCE8_ALPHA8 // two bytes, max quality
            } else GL11.GL_RGBA4
            // two bytes
        }

        fun ImageProgramStringToCompressedFileName(imageProg: String, fileName: Array<String?>) {
//            char s;
            var f: Int
            var i: Int
            val ff = CharArray(imageProg.length + 10)

//	strcpy( fileName, "dds/" );
            fileName[0] = "dds/"
            f = fileName[0]!!.length
            //            ff = fileName[0].toCharArray();
            System.arraycopy(fileName[0]!!.toCharArray(), 0, ff, 0, f)
            var depth = 0

            // convert all illegal characters to underscores
            // this could conceivably produce a duplicated mapping, but we aren't going to worry about it
            i = 0
            while (i < imageProg.length) {
                val s: Char = imageProg[i]
                if ((s == '/') || (s == '\\') || (s == '(')) {
                    if (depth < 4) {
                        ff[f] = '/'
                        depth++
                    } else {
                        ff[f] = ' '
                    }
                    f++
                } else if ((s == '<') || (s == '>') || (s == ':') || (s == '|') || (s == '"') || (s == '.')) {
                    ff[f] = '_'
                    f++
                } else if (s == ' ' && ff[f - 1] == '/') {    // ignore a space right after a slash
                } else if (s == ')' || s == ',') {        // always ignore these
                } else {
                    ff[f] = s
                    f++
                }
                i++
            }
            ff[f++] = 0.toChar()
            //	strcat( fileName, ".dds" );
            fileName[0] = ctos(ff) + ".dds"
        }

        fun ImageProgramStringToCompressedFileName(imageProg: idStr, fileName: Array<String?>) {
            ImageProgramStringToCompressedFileName(imageProg.toString(), fileName)
        }

        fun NumLevelsForImageSize(width: Int, height: Int): Int {
            var width = width
            var height = height
            var numLevels = 1
            while (width > 1 || height > 1) {
                numLevels++
                width = width shr 1
                height = height shr 1
            }
            return numLevels
        }

        companion object {
            /*
         ==================
         R_CreateDefaultImage

         the default image will be grey with a white box outline
         to allow you to see the mapping coordinates on a surface
         ==================
         */
            val DEFAULT_SIZE = 16

            @Transient
            val SIZE = (Integer.SIZE
                    + CPP_class.Enum.SIZE
                    + Integer.SIZE
                    + Integer.SIZE
                    + CPP_class.Pointer.SIZE //idImage
                    + CPP_class.Bool.SIZE
                    + CPP_class.Bool.SIZE
                    + backgroundDownload_s.SIZE
                    + CPP_class.Pointer.SIZE //idImage
                    + idStr.SIZE
                    + CPP_class.Pointer.SIZE //idImage.//TODO:does a function pointer have size?
                    + CPP_class.Bool.SIZE
                    + CPP_class.Enum.SIZE
                    + CPP_class.Enum.SIZE
                    + CPP_class.Enum.SIZE
                    + CPP_class.Enum.SIZE
                    + CPP_class.Bool.SIZE
                    + CPP_class.Bool.SIZE
                    + CPP_class.Bool.SIZE
                    + CPP_class.Bool.SIZE
                    + CPP_class.Bool.SIZE
                    + java.lang.Long.SIZE //ID_TIME_T timestamp
                    + Integer.SIZE //char * imageHash
                    + Integer.SIZE
                    + (Integer.SIZE * 3)
                    + Integer.SIZE
                    + (CPP_class.Pointer.SIZE * 2) //idImage
                    + CPP_class.Pointer.SIZE //idImage
                    + Integer.SIZE)

            // data commonly accessed is grouped here
            val TEXTURE_NOT_LOADED = -1
            val mipBlendColors /*[16][4]*/ = arrayOf(
                intArrayOf(0, 0, 0, 0),
                intArrayOf(255, 0, 0, 128),
                intArrayOf(0, 255, 0, 128),
                intArrayOf(0, 0, 255, 128),
                intArrayOf(255, 0, 0, 128),
                intArrayOf(0, 255, 0, 128),
                intArrayOf(0, 0, 255, 128),
                intArrayOf(255, 0, 0, 128),
                intArrayOf(0, 255, 0, 128),
                intArrayOf(0, 0, 255, 128),
                intArrayOf(255, 0, 0, 128),
                intArrayOf(0, 255, 0, 128),
                intArrayOf(0, 0, 255, 128),
                intArrayOf(255, 0, 0, 128),
                intArrayOf(0, 255, 0, 128),
                intArrayOf(0, 0, 255, 128)
            )

            /*
         ================
         CheckPrecompressedImage

         If fullLoad is false, only the small mip levels of the image will be loaded
         ================
         */
            var DEBUG_CheckPrecompressedImage = 0

            /*
         ===============
         ActuallyLoadImage

         Absolutely every image goes through this path
         On exit, the idImage will have a valid OpenGL texture number that can be bound
         ===============
         */
            private val DBG_ActuallyLoadImage = 0
            private val DBG_Bind = 0

            /*
         ===================
         UploadPrecompressedImage

         This can be called by the front end during normal loading,
         or by the backend after a background read of the file
         has completed
         ===================
         */
            private val DBG_UploadPrecompressedImage: ByteBuffer? = null

            //
            //
            private var DEBUG_COUNTER = 0
        }
    }

    class idImageManager {
        var accumImage: idImage? = null
        var alphaNotchImage: idImage? = null // 2x1 texture with just 1110 and 1111 with point sampling
        var alphaRampImage: idImage? = null // 0-255 in alpha, 255 in RGB
        var ambientNormalMap: idImage? = null // tr.ambientLightVector encoded in all pixels

        //
        var backgroundImageLoads: idImage? = null // chain of images that have background file loads active
        var blackImage: idImage? = null // full of 0x00
        var borderClampImage: idImage? = null // white inside, black outside
        var cacheLRU // head/tail of doubly linked list
                : idImage
        var cinematicImage: idImage? = null
        var compressedPalette = ByteArray(768) // the palette that normal maps use
        var currentRenderImage: idImage? = null // for SS_POST_PROCESS shaders
        var ddsHash: idHashIndex? = null
        var ddsList: idStrList? = null

        //
        // built-in images
        var defaultImage: idImage? = null
        var flatNormalMap: idImage? = null // 128 128 255 in all pixels
        var fogEnterImage: idImage? = null // adjust fogImage alpha based on terminator plane
        var fogImage: idImage? = null // increasing alpha is denser fog

        //
        var imageHashTable = arrayOfNulls<idImage>(FILE_HASH_SIZE)
        val images = idList<idImage?>()

        //
        var insideLevelLoad = false // don't actually load images now
        var noFalloffImage: idImage? = null // all 255, but zero clamped
        var normalCubeMapImage: idImage? = null // cube map to normalize STR into RGB

        //
        var numActiveBackgroundImageLoads = 0

        //
        var originalToCompressed = ByteArray(256) // maps normal maps to 8 bit textures
        var rampImage: idImage? = null // 0-255 in RGBA in S
        var scratchCubeMapImage: idImage? = null
        var scratchImage: idImage? = null
        var scratchImage2: idImage? = null
        var specular2DTableImage: idImage? =
            null // 2D intensity texture with our specular function with variable specularity
        var specularTableImage: idImage? = null // 1D intensity texture with our specular function
        var textureAnisotropy = 0.0f
        var textureLODBias = 0.0f
        /*GLenum*/ var textureMaxFilter = 0

        //
        // default filter modes for images
        /*GLenum*/ var textureMinFilter = 0
        var totalCachedImageSize = 0 // for determining when something should be purged
        var whiteImage: idImage? = null // full of 0xff

        init {
            cacheLRU = idImage()
        }

        @Throws(idException::class)
        fun Init() {

//	memset(imageHashTable, 0, sizeof(imageHashTable));
            imageHashTable = arrayOfNulls(imageHashTable.size)
            images.Resize(1024, 1024)

            // clear the cached LRU
            cacheLRU.cacheUsageNext = cacheLRU
            cacheLRU.cacheUsagePrev = cacheLRU

            // set default texture filter modes
            ChangeTextureFilter()

            // create built in images
            defaultImage = ImageFromFunction("_default", R_DefaultImage.instance)
            whiteImage = ImageFromFunction("_white", R_WhiteImage.instance)
            blackImage = ImageFromFunction("_black", Image_init.R_BlackImage.instance)
            borderClampImage = ImageFromFunction("_borderClamp", Image_init.R_BorderClampImage.instance)
            flatNormalMap = ImageFromFunction("_flat", Image_init.R_FlatNormalImage.instance)
            ambientNormalMap = ImageFromFunction("_ambient", Image_init.R_AmbientNormalImage.instance)
            specularTableImage = ImageFromFunction("_specularTable", Image_init.R_SpecularTableImage.instance)
            specular2DTableImage = ImageFromFunction("_specular2DTable", Image_init.R_Specular2DTableImage.instance)
            rampImage = ImageFromFunction("_ramp", Image_init.R_RampImage.instance)
            alphaRampImage = ImageFromFunction("_alphaRamp", Image_init.R_RampImage.instance)
            alphaNotchImage = ImageFromFunction("_alphaNotch", Image_init.R_AlphaNotchImage.instance)
            fogImage = ImageFromFunction("_fog", Image_init.R_FogImage.instance)
            fogEnterImage = ImageFromFunction("_fogEnter", Image_init.R_FogEnterImage.instance)
            normalCubeMapImage = ImageFromFunction("_normalCubeMap", makeNormalizeVectorCubeMap.instance)
            noFalloffImage = ImageFromFunction("_noFalloff", R_CreateNoFalloffImage.instance)
            ImageFromFunction("_quadratic", Image_init.R_QuadraticImage.instance)

            // cinematicImage is used for cinematic drawing
            // scratchImage is used for screen wipes/doublevision etc..
            cinematicImage = ImageFromFunction("_cinematic", R_RGBA8Image.instance)
            scratchImage = ImageFromFunction("_scratch", R_RGBA8Image.instance)
            scratchImage2 = ImageFromFunction("_scratch2", R_RGBA8Image.instance)
            accumImage = ImageFromFunction("_accum", R_RGBA8Image.instance)
            scratchCubeMapImage =
                ImageFromFunction("_scratchCubeMap", makeNormalizeVectorCubeMap.instance)
            currentRenderImage = ImageFromFunction("_currentRender", R_RGBA8Image.instance)
            cmdSystem.AddCommand(
                "reloadImages",
                R_ReloadImages_f.instance,
                CMD_FL_RENDERER,
                "reloads images"
            )
            cmdSystem.AddCommand("listImages", R_ListImages_f.instance, CMD_FL_RENDERER, "lists images")
            cmdSystem.AddCommand(
                "combineCubeImages",
                R_CombineCubeImages_f.instance,
                CMD_FL_RENDERER,
                "combines six images for roq compression"
            )

            // should forceLoadImages be here?
        }

        fun Shutdown() {
            images.DeleteContents(true)
        }

        // If the exact combination of parameters has been asked for already, an existing
        // image will be returned, otherwise a new image will be created.
        // Be careful not to use the same image file with different filter / repeat / etc parameters
        // if possible, because it will cause a second copy to be loaded.
        // If the load fails for any reason, the image will be filled in with the default
        // grid pattern.
        // Will automatically resample non-power-of-two images and execute image programs if needed.
        fun ImageFromFile(
            _name: String?, filter: textureFilter_t, allowDownSize: Boolean,
            repeat: textureRepeat_t, depth: textureDepth_t, cubeMap: cubeFiles_t /* = CF_2D */
        ): idImage? {
            var allowDownSize = allowDownSize
            var depth = depth
            val name: idStr
            var image: idImage?
            val hash: Int
            if ((null == _name) || _name.isEmpty() || (Icmp(_name, "default") == 0) || (Icmp(_name, "_default") == 0)) {
                DeclManager.declManager.MediaPrint("DEFAULTED\n")
                return globalImages.defaultImage
            }

            // strip any .tga file extensions from anywhere in the _name, including image program parameters
            name = idStr(_name)
            name.Replace(".tga", "")
            name.BackSlashesToSlashes()

            //
            // see if the image is already loaded, unless we
            // are in a reloadImages call
            //
            hash = name.FileNameHash()
            image = imageHashTable[hash]
            while (image != null) {
                if (name.Icmp(image.imgName.toString()) == 0) {
                    // the built in's, like _white and _flat always match the other options
                    if (name[0] == '_') {
                        return image
                    }
                    if (image.cubeFiles != cubeMap) {
                        Common.common.Error("Image '%s' has been referenced with conflicting cube map states", _name)
                    }
                    if (image.filter != filter || image.repeat != repeat) {
                        // we might want to have the system reset these parameters on every bind and
                        // share the image data
                        image = image.hashNext
                        continue
                    }
                    if (image.allowDownSize == allowDownSize && image.depth == depth) {
                        // note that it is used this level load
                        image.levelLoadReferenced = true
                        if (image.partialImage != null) {
                            image.partialImage!!.levelLoadReferenced = true
                        }
                        return image
                    }

                    // the same image is being requested, but with a different allowDownSize or depth
                    // so pick the highest of the two and reload the old image with those parameters
                    if (!image.allowDownSize) {
                        allowDownSize = false
                    }
                    if (image.depth.ordinal > depth.ordinal) {
                        depth = image.depth
                    }
                    if (image.allowDownSize == allowDownSize && image.depth == depth) {
                        // the already created one is already the highest quality
                        image.levelLoadReferenced = true
                        if (image.partialImage != null) {
                            image.partialImage!!.levelLoadReferenced = true
                        }
                        return image
                    }
                    image.allowDownSize = allowDownSize
                    image.depth = depth
                    image.levelLoadReferenced = true
                    if (image.partialImage != null) {
                        image.partialImage!!.levelLoadReferenced = true
                    }
                    if (image_preload.GetBool() && !insideLevelLoad) {
                        image.referencedOutsideLevelLoad = true
                        image.ActuallyLoadImage(true, false) // check for precompressed, load is from front end
                        DeclManager.declManager.MediaPrint(
                            "%dx%d %s (reload for mixed referneces)\n",
                            image.uploadWidth,
                            image.uploadHeight,
                            image.imgName.toString()
                        )
                    }
                    return image
                }
                image = image.hashNext
            }

            //
            // create a new image
            //
            image = AllocImage(name.toString())

            // HACK: to allow keep fonts from being mip'd, as new ones will be introduced with localization
            // this keeps us from having to make a material for each font tga
            if (name.Find("fontImage_") >= 0) {
                allowDownSize = false
            }
            image.allowDownSize = allowDownSize
            image.repeat = repeat
            image.depth = depth
            image.type = textureType_t.TT_2D
            image.cubeFiles = cubeMap
            image.filter = filter
            image.levelLoadReferenced = true

            // also create a shrunken version if we are going to dynamically cache the full size image
            if (image.ShouldImageBePartialCached()) {
                // if we only loaded part of the file, create a new idImage for the shrunken version
                image.partialImage = idImage()
                image.partialImage!!.allowDownSize = allowDownSize
                image.partialImage!!.repeat = repeat
                image.partialImage!!.depth = depth
                image.partialImage!!.type = textureType_t.TT_2D
                image.partialImage!!.cubeFiles = cubeMap
                image.partialImage!!.filter = filter
                image.partialImage!!.levelLoadReferenced = true

                // we don't bother hooking this into the hash table for lookup, but we do add it to the manager
                // list for listImages
                globalImages.images.Append(image.partialImage)
                image.partialImage!!.imgName.set(image.imgName)
                image.partialImage!!.isPartialImage = true

                // let the background file loader know that we can load
                image.precompressedFile = true
                if (image_preload.GetBool() && !insideLevelLoad) {
                    image.partialImage!!.ActuallyLoadImage(
                        true,
                        false
                    ) // check for precompressed, load is from front end
                    DeclManager.declManager.MediaPrint(
                        "%dx%d %s\n",
                        image.partialImage!!.uploadWidth,
                        image.partialImage!!.uploadHeight,
                        image.imgName.toString()
                    )
                } else {
                    DeclManager.declManager.MediaPrint("%s\n", image.imgName.toString())
                }
                return image
            }

            // load it if we aren't in a level preload
            if (image_preload.GetBool() && !insideLevelLoad) {
                image.referencedOutsideLevelLoad = true
                if (idMaterial.DBG_ParseStage == 41) {
//                    return null;
                }
                image.ActuallyLoadImage(true, false) // check for precompressed, load is from front end
                DeclManager.declManager.MediaPrint(
                    "%dx%d %s\n",
                    image.uploadWidth,
                    image.uploadHeight,
                    image.imgName.toString()
                )
            } else {
                DeclManager.declManager.MediaPrint("%s\n", image.imgName.toString())
            }
            return image
        }

        /*
         ===============
         ImageFromFile

         Finds or loads the given image, always returning a valid image pointer.
         Loading of the image may be deferred for dynamic loading.
         ==============
         */
        fun ImageFromFile(
            name: String?, filter: textureFilter_t, allowDownSize: Boolean,
            repeat: textureRepeat_t, depth: textureDepth_t
        ): idImage? {
            return ImageFromFile(name, filter, allowDownSize, repeat, depth, cubeFiles_t.CF_2D)
        }

        // look for a loaded image, whatever the parameters
        fun GetImage(_name: String?): idImage? {
            val name: idStr
            var image: idImage?
            val hash: Int
            if ((null == _name) || _name.isEmpty() || (Icmp(_name, "default") == 0) || (Icmp(_name, "_default") == 0)) {
                DeclManager.declManager.MediaPrint("DEFAULTED\n")
                return globalImages.defaultImage
            }

            // strip any .tga file extensions from anywhere in the _name, including image program parameters
            name = idStr(_name)
            name.Replace(".tga", "")
            name.BackSlashesToSlashes()

            //
            // look in loaded images
            //
            hash = name.FileNameHash()
            image = imageHashTable[hash]
            while (image != null) {
                if (name.Icmp(image.imgName.toString()) == 0) {
                    return image
                }
                image = image.hashNext
            }
            return null
        }

        /*
         ==================
         ImageFromFunction

         Images that are procedurally generated are allways specified
         with a callback which must work at any time, allowing the OpenGL
         system to be completely regenerated if needed.
         ==================
         */
        // The callback will be issued immediately, and later if images are reloaded or vid_restart
        // The callback function should call one of the idImage::Generate* functions to fill in the data
        fun ImageFromFunction(_name: String?, generatorFunction: GeneratorFunction): idImage {
            val name: idStr
            var image: idImage?
            val hash: Int
            if (null == _name) { //tut tut tut
                Common.common.FatalError("idImageManager::ImageFromFunction: NULL name")
            }

            // strip any .tga file extensions from anywhere in the _name
            name = idStr((_name)!!)
            name.Replace(".tga", "")
            name.BackSlashesToSlashes()

            // see if the image already exists
            hash = name.FileNameHash()
            image = imageHashTable[hash]
            while (image != null) {
                if (name.Icmp(image.imgName.toString()) == 0) {
                    if (image.generatorFunction !== generatorFunction) {
                        Common.common.DPrintf("WARNING: reused image %s with mixed generators\n", name)
                    }
                    return image
                }
                image = image.hashNext
            }

            // create the image and issue the callback
            image = AllocImage(name.toString())
            image.generatorFunction = generatorFunction
            if (image_preload.GetBool()) {
                // check for precompressed, load is from the front end
                image.referencedOutsideLevelLoad = true
                image.ActuallyLoadImage(true, false)
            }
            return image
        }

        /*
         ==================
         R_CompleteBackgroundImageLoads

         Do we need to worry about vid_restarts here?//TODO:do we indeed?
         ==================
         */
        // called once a frame to allow any background loads that have been completed
        // to turn into textures.
        fun CompleteBackgroundImageLoads() {
            var remainingList: idImage? = null
            var next: idImage
            run {
                var image: idImage? = backgroundImageLoads
                while (image != null) {
                    next = (image.bglNext)!!
                    if (image.bgl.completed) {
                        numActiveBackgroundImageLoads--
                        fileSystem.CloseFile(image.bgl.f!!)
                        // upload the image
                        image.UploadPrecompressedImage(image.bgl.file.buffer, image.bgl.file.length)
                        image.bgl.file.buffer = null //R_StaticFree(image.bgl.file.buffer);
                        if (image_showBackgroundLoads.GetBool()) {
                            Common.common.Printf("R_CompleteBackgroundImageLoad: %s\n", image.imgName)
                        }
                    } else {
                        image.bglNext = remainingList
                        remainingList = image
                    }
                    image = next
                }
            }
            if (image_showBackgroundLoads.GetBool()) {
                if (numActiveBackgroundImageLoads != prev) {
                    prev = numActiveBackgroundImageLoads
                    Common.common.Printf("background Loads: %d\n", numActiveBackgroundImageLoads)
                }
            }
            backgroundImageLoads = remainingList
        }

        // returns the number of bytes of image data bound in the previous frame
        fun SumOfUsedImages(): Int {
            var total: Int
            var i: Int
            var image: idImage?
            total = 0
            i = 0
            while (i < images.Num()) {
                image = images[i]
                if (image!!.frameUsed == backEnd!!.frameCount) {
                    total += image.StorageSize()
                }
                i++
            }
            return total
        }

        // called each frame to allow some cvars to automatically force changes
        fun CheckCvars() {
            // textureFilter stuff
            if (image_filter.IsModified() || image_anisotropy.IsModified() || image_lodbias.IsModified()) {
                ChangeTextureFilter()
                image_filter.ClearModified()
                image_anisotropy.ClearModified()
                image_lodbias.ClearModified()
            }
        }

        // purges all the images before a vid_restart
        fun PurgeAllImages() {
            var i: Int
            var image: idImage?
            i = 0
            while (i < images.Num()) {
                image = images[i]
                image!!.PurgeImage()
                i++
            }
        }

        // reloads all apropriate images after a vid_restart
        fun ReloadAllImages() {
            val args = CmdArgs.idCmdArgs()

            // build the compressed normal map palette
            SetNormalPalette()
            args.TokenizeString("reloadImages reload", false)
            R_ReloadImages_f.instance.run(args)
        }

        // disable the active texture unit
        fun BindNull() {
            val tmu: tmu_t
            tmu = backEnd!!.glState.tmu[backEnd!!.glState.currenttmu]!!
            tr_backend.RB_LogComment("BindNull()\n")
            if (tmu.textureType == textureType_t.TT_CUBIC) {
                qgl.qglDisable(GL13.GL_TEXTURE_CUBE_MAP /*_EXT*/)
            } else if (tmu.textureType == textureType_t.TT_3D) {
                qgl.qglDisable(GL12.GL_TEXTURE_3D)
            } else if (tmu.textureType == textureType_t.TT_2D) {
                qgl.qglDisable(GL11.GL_TEXTURE_2D)
            }
            tmu.textureType = textureType_t.TT_DISABLED
        }

        /*
         ====================
         BeginLevelLoad

         Mark all file based images as currently unused,
         but don't free anything.  Calls to ImageFromFile() will
         either mark the image as used, or create a new image without
         loading the actual data.
         ====================
         */
        // Mark all file based images as currently unused,
        // but don't free anything.  Calls to ImageFromFile() will
        // either mark the image as used, or create a new image without
        // loading the actual data.
        // Called only by renderSystem::BeginLevelLoad
        fun BeginLevelLoad() {
            insideLevelLoad = true
            for (i in 0 until images.Num()) {
                val image = images[i]

                // generator function images are always kept around
                if (image!!.generatorFunction != null) {
                    continue
                }
                if (Common.com_purgeAll.GetBool()) {
                    image.PurgeImage()
                }
                image.levelLoadReferenced = false
            }
        }

        /*
         ====================
         EndLevelLoad

         Free all images marked as unused, and load all images that are necessary.
         This architecture prevents us from having the union of two level's
         worth of data present at one time.

         preload everything, never free
         preload everything, free unused after level load
         blocking load on demand
         preload low mip levels, background load remainder on demand
         ====================
         */
        // Free all images marked as unused, and load all images that are necessary.
        // This architecture prevents us from having the union of two level's
        // worth of data present at one time.
        // Called only by renderSystem::EndLevelLoad
        fun EndLevelLoad() {
            val start = Sys_Milliseconds()
            insideLevelLoad = false
            if (idAsyncNetwork.serverDedicated.GetInteger() != 0) {
                return
            }
            Common.common.Printf("----- idImageManager.EndLevelLoad -----\n")
            var purgeCount = 0
            var keepCount = 0
            var loadCount = 0

            // purge the ones we don't need
            for (i in 0 until images.Num()) {
                val image = images[i]
                if (image!!.generatorFunction != null) {
                    continue
                }
                if (!image.levelLoadReferenced && !image.referencedOutsideLevelLoad) {
//			common.Printf( "Purging %s\n", image.imgName.c_str() );
                    purgeCount++
                    image.PurgeImage()
                } else if (image.texNum != idImage.TEXTURE_NOT_LOADED) {
//			common.Printf( "Keeping %s\n", image.imgName.c_str() );
                    keepCount++
                }
            }

            // load the ones we do need, if we are preloading
            for (i in 0 until images.Num()) {
                val image = images[i]
                if (image!!.generatorFunction != null) {
                    continue
                }
                if (image.levelLoadReferenced && (image.texNum == idImage.TEXTURE_NOT_LOADED) && (null == image.partialImage)) {
//			common.Printf( "Loading %s\n", image.imgName.c_str() );
                    loadCount++
                    image.ActuallyLoadImage(true, false)
                    if ((loadCount and 15) == 0) {
                        Session.session.PacifierUpdate()
                    }
                }
            }
            val end = Sys_Milliseconds()
            Common.common.Printf("%5d purged from previous\n", purgeCount)
            Common.common.Printf("%5d kept from previous\n", keepCount)
            Common.common.Printf("%5d new loaded\n", loadCount)
            Common.common.Printf("all images loaded in %5.1f seconds\n", (end - start) * 0.001)
            Common.common.Printf("----------------------------------------\n")
        }

        // used to clear and then write the dds conversion batch file
        fun StartBuild() {
            ddsList!!.clear()
            ddsHash!!.Free()
        }


        fun FinishBuild(removeDups: Boolean = false /*= false */) {
            val batchFile: idFile?
            if (removeDups) {
                ddsList!!.clear()
                val buffer = arrayOf<ByteBuffer?>(null)
                fileSystem.ReadFile("makedds.bat", buffer)
                if (buffer[0] != null) {
                    var str = idStr(String(buffer[0]!!.array()))
                    while (str.Length() != 0) {
                        val n = str.Find('\n')
                        if (n > 0) {
                            val line = str.Left(n + 1)
                            val right = idStr()
                            str.Right(str.Length() - n - 1, right)
                            str = right
                            ddsList!!.addUnique(line)
                        } else {
                            break
                        }
                    }
                }
            }
            batchFile = fileSystem.OpenFileWrite(if ((removeDups)) "makedds2.bat" else "makedds.bat")
            if (batchFile != null) {
                var i: Int
                val ddsNum = ddsList!!.size()
                i = 0
                while (i < ddsNum) {
                    batchFile.WriteFloatString("%s", ddsList!![i].toString())
                    batchFile.Printf(
                        "@echo Finished compressing %d of %d.  %.1f percent done.\n",
                        i + 1,
                        ddsNum,
                        ((i + 1).toFloat() / ddsNum.toFloat()) * 100.0f
                    )
                    i++
                }
                fileSystem.CloseFile(batchFile)
            }
            ddsList!!.clear()
            ddsHash!!.Free()
        }

        fun AddDDSCommand(cmd: String?) {
            var i: Int
            val key: Int
            if (!(cmd != null && !cmd.isEmpty())) { //TODO:WdaF?
                return
            }
            key = ddsHash!!.GenerateKey(cmd, false)
            i = ddsHash!!.First(key)
            while (i != -1) {
                if (ddsList!![i].Icmp(cmd) == 0) {
                    break
                }
                i = ddsHash!!.Next(i)
            }
            if (i == -1) {
                ddsList!!.add(idStr(cmd))
            }
        }

        //
        //--------------------------------------------------------
        fun PrintMemInfo(mi: MemInfo_t) {
            var i: Int
            var j: Int
            var total = 0
            val sortIndex: IntArray
            val f: idFile?
            f = fileSystem.OpenFileWrite(mi.filebase.toString() + "_images.txt")
            if (null == f) {
                return
            }

            // sort first
            sortIndex = IntArray(images.Num())
            i = 0
            while (i < images.Num()) {
                sortIndex[i] = i
                i++
            }
            i = 0
            while (i < images.Num() - 1) {
                j = i + 1
                while (j < images.Num()) {
                    if (images[sortIndex[i]]!!.StorageSize() < images[sortIndex[j]]!!.StorageSize()) {
                        val temp = sortIndex[i]
                        sortIndex[i] = sortIndex[j]
                        sortIndex[j] = temp
                    }
                    j++
                }
                i++
            }

            // print next
            i = 0
            while (i < images.Num()) {
                val im = images[sortIndex[i]]
                var size: Int
                size = im!!.StorageSize()
                total += size
                f.Printf("%s %3d %s\n", FormatNumber(size), im.refCount, im.imgName)
                i++
            }

//	delete sortIndex;
            mi.imageAssetsTotal = total
            f.Printf("\nTotal image bytes allocated: %s\n", FormatNumber(total))
            fileSystem.CloseFile(f)
        }

        /*
         ==============
         AllocImage

         Allocates an idImage, adds it to the list,
         copies the name, and adds it to the hash chain.
         ==============
         */
        fun AllocImage(name: String): idImage {
            val image: idImage
            val hash: Int
            if (name.length >= MAX_IMAGE_NAME) {
                Common.common.Error("idImageManager::AllocImage: \"%s\" is too long\n", name)
            }
            hash = idStr(name).FileNameHash()
            //            System.out.printf(">>>>>>>>>>>>>>%d--%s\n", hash, name);
//            System.out.printf(">>>>>>>>>>>>>>%d--%s\n", idStr.IHash(name.toCharArray()), name);
            image = idImage()
            images.Append(image)
            image.hashNext = imageHashTable[hash]
            imageHashTable[hash] = image
            image.imgName.set(name)
            return image
        }

        /*
         ==================
         SetNormalPalette

         Create a 256 color palette to be used by compressed normal maps
         ==================
         */
        fun SetNormalPalette() {
            var i: Int
            var j: Int
            val v = idVec3()
            var t: Float
            //byte temptable[768];
            val temptable = compressedPalette
            val compressedToOriginal = IntArray(16)

            // make an ad-hoc separable compression mapping scheme
            i = 0
            while (i < 8) {
                var f: Float
                var y: Float
                f = (i + 1) / 8.5f
                y = Sqrt(1.0f - f * f)
                y = 1.0f - y
                compressedToOriginal[7 - i] = 127 - (y * 127 + 0.5f).toInt()
                compressedToOriginal[8 + i] = 128 + (y * 127 + 0.5f).toInt()
                i++
            }
            i = 0
            while (i < 256) {
                if (i <= compressedToOriginal[0]) {
                    originalToCompressed[i] = 0
                } else if (i >= compressedToOriginal[15]) {
                    originalToCompressed[i] = 15
                } else {
                    j = 0
                    while (j < 14) {
                        if (i <= compressedToOriginal[j + 1]) {
                            break
                        }
                        j++
                    }
                    if (i - compressedToOriginal[j] < compressedToOriginal[j + 1] - i) {
                        originalToCompressed[i] = j.toByte()
                    } else {
                        originalToCompressed[i] = (j + 1).toByte()
                    }
                }
                i++
            }
            if (false) {
//	for ( i = 0; i < 16; i++ ) {
//		for ( j = 0 ; j < 16 ; j++ ) {
//
//			v.set(0,  ( i - 7.5 ) / 8);
//			v.set(1,  ( j - 7.5 ) / 8);
//
//			t = 1.0f - ( v.get(0)*v.get(0) + v.get(1)*v.get(1) );
//			if ( t < 0 ) {
//				t = 0;
//			}
//			v.set(2,  idMath.Sqrt( t ));
//
//			temptable[(i*16+j)*3+0] = 128 + floor( 127 * v.get(0) + 0.5f );
//			temptable[(i*16+j)*3+1] = 128 + floor( 127 * v.get(1) );
//			temptable[(i*16+j)*3+2] = 128 + floor( 127 * v.get(2) );
//		}
//	}
            } else {
                i = 0
                while (i < 16) {
                    j = 0
                    while (j < 16) {
                        v[0] = (compressedToOriginal[i] - 127.5f) / 128.0f
                        v[1] = (compressedToOriginal[j] - 127.5f) / 128.0f
                        t = 1.0f - (v[0] * v[0] + v[1] * v[1])
                        if (t < 0) {
                            t = 0.0f
                        }
                        v[2] = Sqrt(t)
                        temptable[(i * 16 + j) * 3 + 0] = (128 + floor(127 * v[0] + 0.5f)).toInt().toByte()
                        temptable[(i * 16 + j) * 3 + 1] = (128 + floor((127 * v[1]))).toInt().toByte()
                        temptable[(i * 16 + j) * 3 + 2] = (128 + floor((127 * v[2]))).toInt().toByte()
                        j++
                    }
                    i++
                }
            }

            // color 255 will be the "nullnormal" color for no reflection
            temptable[255 * 3 + 2] = 128.toByte()
            temptable[255 * 3 + 1] = temptable[255 * 3 + 2]
            temptable[255 * 3 + 0] = temptable[255 * 3 + 1]
            if (!glConfig.sharedTexturePaletteAvailable) {
                return
            }
            qgl.qglColorTableEXT(
                EXTSharedTexturePalette.GL_SHARED_TEXTURE_PALETTE_EXT,
                GL11.GL_RGB,
                256,
                GL11.GL_RGB,
                GL11.GL_UNSIGNED_BYTE,
                temptable
            )
            qgl.qglEnable(EXTSharedTexturePalette.GL_SHARED_TEXTURE_PALETTE_EXT)
        }

        /*
         ===============
         ChangeTextureFilter

         This resets filtering on all loaded images
         New images will automatically pick up the current values.
         ===============
         */
        fun ChangeTextureFilter() {
            var i: Int
            var glt: idImage?
            val string: String?

            // if these are changed dynamically, it will force another ChangeTextureFilter
            image_filter.ClearModified()
            image_anisotropy.ClearModified()
            image_lodbias.ClearModified()
            string = image_filter.GetString()
            i = 0
            while (i < 6) {
                if (0 == Icmp(textureFilters[i].name, (string)!!)) {
                    break
                }
                i++
            }
            if (i == 6) {
                Common.common.Warning("bad r_textureFilter: '%s'", (string)!!)
                // default to LINEAR_MIPMAP_NEAREST
                i = 0
            }

            // set the values for future images
            textureMinFilter = textureFilters[i].minimize
            textureMaxFilter = textureFilters[i].maximize
            textureAnisotropy = image_anisotropy.GetFloat()
            if (textureAnisotropy < 1) {
                textureAnisotropy = 1.0f
            } else if (textureAnisotropy > glConfig.maxTextureAnisotropy) {
                textureAnisotropy = glConfig.maxTextureAnisotropy
            }
            textureLODBias = image_lodbias.GetFloat()

            // change all the existing mipmap texture objects with default filtering
            i = 0
            while (i < images.Num()) {
                var texEnum = GL11.GL_TEXTURE_2D
                glt = images[i]
                when (glt!!.type) {
                    textureType_t.TT_2D -> texEnum = GL11.GL_TEXTURE_2D
                    textureType_t.TT_3D -> texEnum = GL12.GL_TEXTURE_3D
                    textureType_t.TT_CUBIC -> texEnum = GL13.GL_TEXTURE_CUBE_MAP /*_EXT*/
                    else -> {}
                }

                // make sure we don't start a background load
                if (glt.texNum == idImage.TEXTURE_NOT_LOADED) {
                    i++
                    continue
                }
                glt.Bind()
                if (glt.filter == textureFilter_t.TF_DEFAULT) {
                    qgl.qglTexParameterf(texEnum, GL11.GL_TEXTURE_MIN_FILTER, globalImages.textureMinFilter.toFloat())
                    qgl.qglTexParameterf(texEnum, GL11.GL_TEXTURE_MAG_FILTER, globalImages.textureMaxFilter.toFloat())
                }
                if (glConfig.anisotropicAvailable) {
                    qgl.qglTexParameterf(
                        texEnum,
                        EXTTextureFilterAnisotropic.GL_TEXTURE_MAX_ANISOTROPY_EXT,
                        globalImages.textureAnisotropy
                    )
                }
                if (glConfig.textureLODBiasAvailable) {
                    qgl.qglTexParameterf(texEnum, GL14.GL_TEXTURE_LOD_BIAS, globalImages.textureLODBias)
                }
                i++
            }
        }

        internal class filterName_t(var name: String, var minimize: Int, var maximize: Int)
        companion object {
            val MAX_BACKGROUND_IMAGE_LOADS = 8
            private val textureFilters = arrayOf(
                filterName_t("GL_LINEAR_MIPMAP_NEAREST", GL11.GL_LINEAR_MIPMAP_NEAREST, GL11.GL_LINEAR),
                filterName_t("GL_LINEAR_MIPMAP_LINEAR", GL11.GL_LINEAR_MIPMAP_LINEAR, GL11.GL_LINEAR),
                filterName_t("GL_NEAREST", GL11.GL_NEAREST, GL11.GL_NEAREST),
                filterName_t("GL_LINEAR", GL11.GL_LINEAR, GL11.GL_LINEAR),
                filterName_t("GL_NEAREST_MIPMAP_NEAREST", GL11.GL_NEAREST_MIPMAP_NEAREST, GL11.GL_NEAREST),
                filterName_t("GL_NEAREST_MIPMAP_LINEAR", GL11.GL_NEAREST_MIPMAP_LINEAR, GL11.GL_NEAREST)
            )
            var image_anisotropy = idCVar(
                "image_anisotropy",
                "1",
                CVAR_RENDERER or CVAR_ARCHIVE,
                "set the maximum texture anisotropy if available"
            )

            //
            var image_cacheMegs = idCVar(
                "image_cacheMegs",
                "20",
                CVAR_RENDERER or CVAR_ARCHIVE,
                "maximum MB set aside for temporary loading of full-sized precompressed images"
            )
            var image_cacheMinK = idCVar(
                "image_cacheMinK",
                "200",
                CVAR_RENDERER or CVAR_ARCHIVE or CVAR_INTEGER,
                "maximum KB of precompressed files to read at specification time"
            )
            var image_colorMipLevels = idCVar(
                "image_colorMipLevels",
                "0",
                CVAR_RENDERER or CVAR_BOOL,
                "development aid to see texture mip usage"
            )
            var image_downSize =
                idCVar("image_downSize", "0", CVAR_RENDERER or CVAR_ARCHIVE, "controls texture downsampling")
            var image_downSizeBump =
                idCVar("image_downSizeBump", "0", CVAR_RENDERER or CVAR_ARCHIVE, "controls normal map downsampling")
            var image_downSizeBumpLimit = idCVar(
                "image_downSizeBumpLimit",
                "128",
                CVAR_RENDERER or CVAR_ARCHIVE,
                "controls normal map downsample limit"
            )
            var image_downSizeLimit = idCVar(
                "image_downSizeLimit",
                "256",
                CVAR_RENDERER or CVAR_ARCHIVE,
                "controls diffuse map downsample limit"
            )
            var image_downSizeSpecular =
                idCVar("image_downSizeSpecular", "0", CVAR_RENDERER or CVAR_ARCHIVE, "controls specular downsampling")
            var image_downSizeSpecularLimit = idCVar(
                "image_downSizeSpecularLimit",
                "64",
                CVAR_RENDERER or CVAR_ARCHIVE,
                "controls specular downsampled limit"
            )
            var image_filter = idCVar(
                "image_filter",
                (Image_init.imageFilter[1])!!,
                CVAR_RENDERER or CVAR_ARCHIVE,
                "changes texture filtering on mipmapped images",
                Image_init.imageFilter,
                ArgCompletion_String(Image_init.imageFilter)
            )
            var image_forceDownSize = idCVar("image_forceDownSize", "0", CVAR_RENDERER or CVAR_ARCHIVE or CVAR_BOOL, "")
            var image_ignoreHighQuality = idCVar(
                "image_ignoreHighQuality",
                "0",
                CVAR_RENDERER or CVAR_ARCHIVE,
                "ignore high quality setting on materials"
            )
            var image_lodbias =
                idCVar("image_lodbias", "0", CVAR_RENDERER or CVAR_ARCHIVE, "change lod bias on mipmapped images")

            //
            //
            var image_preload = idCVar(
                "image_preload",
                "1",
                CVAR_RENDERER or CVAR_BOOL or CVAR_ARCHIVE,
                "if 0, dynamically load all images"
            )

            //
            // cvars
            var image_roundDown = idCVar(
                "image_roundDown",
                "1",
                CVAR_RENDERER or CVAR_ARCHIVE or CVAR_BOOL,
                "round bad sizes down to nearest power of two"
            )
            var image_showBackgroundLoads = idCVar(
                "image_showBackgroundLoads",
                "0",
                CVAR_RENDERER or CVAR_BOOL,
                "1 = print number of outstanding background loads"
            )

            //		
            var image_useAllFormats = idCVar(
                "image_useAllFormats",
                "1",
                CVAR_RENDERER or CVAR_ARCHIVE or CVAR_BOOL,
                "allow alpha/intensity/luminance/luminance+alpha"
            )
            var image_useCache = idCVar(
                "image_useCache",
                "0",
                CVAR_RENDERER or CVAR_ARCHIVE or CVAR_BOOL,
                "1 = do background load image caching"
            )
            var image_useCompression = idCVar(
                "image_useCompression",
                "1",
                CVAR_RENDERER or CVAR_ARCHIVE or CVAR_BOOL,
                "0 = force everything to high quality"
            )

            //
            var image_useNormalCompression = idCVar(
                "image_useNormalCompression",
                "2",
                CVAR_RENDERER or CVAR_ARCHIVE or CVAR_INTEGER,
                "2 = use rxgb compression for normal maps, 1 = use 256 color compression for normal maps if available"
            )
            var image_useOffLineCompression = idCVar(
                "image_useOfflineCompression",
                "0",
                CVAR_RENDERER or CVAR_BOOL,
                "write a batch file for offline compression of DDS files"
            )
            var image_usePrecompressedTextures = idCVar(
                "image_usePrecompressedTextures",
                "1",
                CVAR_RENDERER or CVAR_ARCHIVE or CVAR_BOOL,
                "use .dds files if present"
            )
            var image_writeNormalTGA = idCVar(
                "image_writeNormalTGA",
                "0",
                CVAR_RENDERER or CVAR_BOOL,
                "write .tgas of the final normal maps for debugging"
            )
            var image_writeNormalTGAPalletized = idCVar(
                "image_writeNormalTGAPalletized",
                "0",
                CVAR_RENDERER or CVAR_BOOL,
                "write .tgas of the final palletized normal maps for debugging"
            )
            var image_writePrecompressedTextures = idCVar(
                "image_writePrecompressedTextures",
                "0",
                CVAR_RENDERER or CVAR_BOOL,
                "write .dds files if necessary"
            )
            var image_writeTGA = idCVar(
                "image_writeTGA",
                "0",
                CVAR_RENDERER or CVAR_BOOL,
                "write .tgas of the non normal maps for debugging"
            )
            private var prev = 0
        }
    }
}

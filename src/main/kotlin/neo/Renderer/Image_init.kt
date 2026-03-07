/*
===========================================================================

Doom 3 GPL Source Code
Copyright (C) 1999-2011 id Software LLC, a ZeniMax Media company.
Translated to Kotlin by Dr. Feederino with support of Claude Code

This file is part of the Doom 3 GPL Source Code ("Doom 3 Source Code").
Original source: neo/renderer/Image_init.cpp

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

===========================================================================
*/

package neo.Renderer

import neo.Renderer.Image.GeneratorFunction
import neo.Renderer.Image.idImage
import neo.Renderer.Image.idImageManager
import neo.Renderer.Image.textureDepth_t
import neo.Renderer.Image_files.R_WriteTGA
import neo.Renderer.Material.textureFilter_t
import neo.Renderer.Material.textureRepeat_t
import neo.TempDump.flatten
import neo.TempDump.wrapToNativeBuffer
import neo.framework.CmdSystem.cmdFunction_t
import neo.framework.Common.Companion.common
import neo.idlib.CmdArgs
import neo.idlib.Text.Str.idStr
import neo.idlib.Text.Str.idStr.Companion.Icmp
import neo.idlib.containers.List.cmp_t
import neo.idlib.containers.List.idList
import neo.idlib.math.idMath.InvSqrt
import neo.idlib.math.idMath.Sqrt
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.EXTTextureCompressionS3TC
import org.lwjgl.opengl.GL11
import java.nio.ByteBuffer
import java.nio.FloatBuffer
import java.util.*
import kotlin.math.abs
import kotlin.math.pow

object Image_init {
    // the size determines how far away from the edge the blocks start fading
    val BORDER_CLAMP_SIZE: Int = 32
    val DEEP_RANGE: Float = -30.0f

    /*
     ================
     R_FogImage

     We calculate distance correctly in two planes, but the
     third will still be projection based
     ================
     */
    val FOG_SIZE: Int = 128
    val IC_Info: Array<imageClassificate_t> = arrayOf(
        imageClassificate_t("models/characters", "Characters", IMAGE_CLASSIFICATION.IC_NPC, 512, 512),
        imageClassificate_t("models/weapons", "Weapons", IMAGE_CLASSIFICATION.IC_WEAPON, 512, 512),
        imageClassificate_t("models/monsters", "Monsters", IMAGE_CLASSIFICATION.IC_MONSTER, 512, 512),
        imageClassificate_t("models/mapobjects", "Model Geometry", IMAGE_CLASSIFICATION.IC_MODELGEOMETRY, 512, 512),
        imageClassificate_t("models/items", "Items", IMAGE_CLASSIFICATION.IC_ITEMS, 512, 512),
        imageClassificate_t("models", "Other model textures", IMAGE_CLASSIFICATION.IC_MODELSOTHER, 512, 512),
        imageClassificate_t("guis/assets", "Guis", IMAGE_CLASSIFICATION.IC_GUIS, 256, 256),
        imageClassificate_t("textures", "World Geometry", IMAGE_CLASSIFICATION.IC_WORLDGEOMETRY, 256, 256),
        imageClassificate_t("", "Other", IMAGE_CLASSIFICATION.IC_OTHER, 256, 256)
    )
    val NORMAL_MAP_SIZE: Int = 32
    val QUADRATIC_HEIGHT: Int = 4

    /*
     ================
     R_QuadraticImage

     ================
     */
    val QUADRATIC_WIDTH: Int = 32

    /*
     ================
     FogFraction

     Height values below zero are inside the fog volume
     ================
     */
    val RAMP_RANGE: Float = 8.0f
    val imageFilter: Array<String?> = arrayOf(
        "GL_LINEAR_MIPMAP_NEAREST",
        "GL_LINEAR_MIPMAP_LINEAR",
        "GL_NEAREST",
        "GL_LINEAR",
        "GL_NEAREST_MIPMAP_NEAREST",
        "GL_NEAREST_MIPMAP_LINEAR",
        null
    )

    fun ClassifyImage(name: String?): Int {
        val str: idStr
        str = idStr((name)!!)
        for (i in 0 until IMAGE_CLASSIFICATION.IC_COUNT) {
            if (str.Find(IC_Info[i].rootPath, false) == 0) {
                return IC_Info[i].type
            }
        }
        return IMAGE_CLASSIFICATION.IC_OTHER
    }

    /**
     * * NORMALIZATION CUBE MAP CONSTRUCTION **
     */
    /* Given a cube map face index, cube map size, and integer 2D face position,
     * return the cooresponding normalized vector.
     */
    fun getCubeVector(i: Int, cubesize: Int, x: Int, y: Int, vector: FloatArray) {
        val s: Float
        val t: Float
        val sc: Float
        val tc: Float
        val mag: Float
        s = (x.toFloat() + 0.5f) / cubesize.toFloat()
        t = (y.toFloat() + 0.5f) / cubesize.toFloat()
        sc = s * 2.0f - 1.0f
        tc = t * 2.0f - 1.0f
        when (i) {
            0 -> {
                vector[0] = 1.0f
                vector[1] = -tc
                vector[2] = -sc
            }

            1 -> {
                vector[0] = -1.0f
                vector[1] = -tc
                vector[2] = sc
            }

            2 -> {
                vector[0] = sc
                vector[1] = 1.0f
                vector[2] = tc
            }

            3 -> {
                vector[0] = sc
                vector[1] = -1.0f
                vector[2] = -tc
            }

            4 -> {
                vector[0] = sc
                vector[1] = -tc
                vector[2] = 1.0f
            }

            5 -> {
                vector[0] = -sc
                vector[1] = -tc
                vector[2] = -1.0f
            }

            else -> {
                common.Error("getCubeVector: invalid cube map face index")
                return
            }
        }
        mag =
            InvSqrt((vector[0] * vector[0]) + (vector[1] * vector[1]) + (vector[2] * vector[2]))
        vector[0] *= mag
        vector[1] *= mag
        vector[2] *= mag
    }

    fun FogFraction(viewHeight: Float, targetHeight: Float): Float {
        val total: Float = abs((targetHeight - viewHeight))

//	return targetHeight >= 0 ? 0 : 1.0f;
        // only ranges that cross the ramp range are special
        if (targetHeight > 0 && viewHeight > 0) {
            return 0.0f
        }
        if (targetHeight < -RAMP_RANGE && viewHeight < -RAMP_RANGE) {
            return 1.0f
        }
        val above: Float
        if (targetHeight > 0) {
            above = targetHeight
        } else if (viewHeight > 0) {
            above = viewHeight
        } else {
            above = 0.0f
        }
        var rampTop: Float
        var rampBottom: Float
        if (viewHeight > targetHeight) {
            rampTop = viewHeight
            rampBottom = targetHeight
        } else {
            rampTop = targetHeight
            rampBottom = viewHeight
        }
        if (rampTop > 0) {
            rampTop = 0.0f
        }
        if (rampBottom < -RAMP_RANGE) {
            rampBottom = -RAMP_RANGE
        }
        val rampSlope: Float = 1.0f / RAMP_RANGE
        if (0.0f == total) {
            return -viewHeight * rampSlope
        }
        val ramp: Float = (1.0f - (rampTop * rampSlope + rampBottom * rampSlope) * -0.5f) * (rampTop - rampBottom)
        var frac: Float = (total - above - ramp) / total

        // after it gets moderately deep, always use full value
        val deepest: Float = if (viewHeight < targetHeight) viewHeight else targetHeight
        val deepFrac: Float = deepest / DEEP_RANGE
        if (deepFrac >= 1.0f) {
            return 1.0f
        }
        frac = frac * (1.0f - deepFrac) + deepFrac
        return frac
    }

    internal object IMAGE_CLASSIFICATION {
        val IC_COUNT: Int = 9
        val IC_GUIS: Int = 6
        val IC_ITEMS: Int = 4
        val IC_MODELGEOMETRY: Int = 3
        val IC_MODELSOTHER: Int = 5
        val IC_MONSTER: Int = 2
        val IC_NPC: Int = 0
        val IC_OTHER: Int = 8
        val IC_WEAPON: Int = 1
        val IC_WORLDGEOMETRY: Int = 7
    }

    class imageClassificate_t(
        var rootPath: String,
        var desc: String,
        var type: Int,
        var maxWidth: Int,
        var maxHeight: Int
    )

    internal class intList : idList<Int?>()

    /*
     ===============
     R_ListImages_f
     ===============
     */
    internal class R_ListImages_f private constructor() : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            var i: Int
            var j: Int
            var partialSize: Int
            var image: idImage?
            var totalSize: Int
            var count = 0
            var matchTag = 0
            var uncompressedOnly = false
            var unloaded = false
            var partial = false
            var cached = false
            var uncached = false
            var failed = false
            var touched = false
            var sorted = false
            var duplicated = false
            var byClassification = false
            var overSized = false
            if (args!!.Argc() == 1) {
            } else if (args.Argc() == 2) {
                if (Icmp(args.Argv(1), "uncompressed") == 0) {
                    uncompressedOnly = true
                } else if (Icmp(args.Argv(1), "sorted") == 0) {
                    sorted = true
                } else if (Icmp(args.Argv(1), "partial") == 0) {
                    partial = true
                } else if (Icmp(args.Argv(1), "unloaded") == 0) {
                    unloaded = true
                } else if (Icmp(args.Argv(1), "cached") == 0) {
                    cached = true
                } else if (Icmp(args.Argv(1), "uncached") == 0) {
                    uncached = true
                } else if (Icmp(args.Argv(1), "tagged") == 0) {
                    matchTag = 1
                } else if (Icmp(args.Argv(1), "duplicated") == 0) {
                    duplicated = true
                } else if (Icmp(args.Argv(1), "touched") == 0) {
                    touched = true
                } else if (Icmp(args.Argv(1), "classify") == 0) {
                    byClassification = true
                    sorted = true
                } else if (Icmp(args.Argv(1), "oversized") == 0) {
                    byClassification = true
                    sorted = true
                    overSized = true
                } else {
                    failed = true
                }
            } else {
                failed = true
            }
            if (failed) {
                common.Printf("usage: listImages [ sorted | partial | unloaded | cached | uncached | tagged | duplicated | touched | classify | showOverSized ]\n")
                return
            }
            val header = "       -w-- -h-- filt -fmt-- wrap  size --name-------\n"
            common.Printf("\n%s", header)
            totalSize = 0

            val sortedArray: Array<sortedImage_t> = Array(Image.globalImages.images.Num()) { sortedImage_t() }
            i = 0
            while (i < Image.globalImages.images.Num()) {
                image = Image.globalImages.images[i]
                if (uncompressedOnly) {
                    if (((image!!.internalFormat >= EXTTextureCompressionS3TC.GL_COMPRESSED_RGB_S3TC_DXT1_EXT && image.internalFormat <= EXTTextureCompressionS3TC.GL_COMPRESSED_RGBA_S3TC_DXT5_EXT)
                                || image.internalFormat == 0x80E5)
                    ) {
                        i++
                        continue
                    }
                }
                if (matchTag != 0 && image!!.classification != matchTag) {
                    i++
                    continue
                }
                if (unloaded && image!!.texNum != idImage.TEXTURE_NOT_LOADED) {
                    i++
                    continue
                }
                if (partial && !image!!.isPartialImage) {
                    i++
                    continue
                }
                if (cached && (null == image!!.partialImage || image.texNum == idImage.TEXTURE_NOT_LOADED)) {
                    i++
                    continue
                }
                if (uncached && (null == image!!.partialImage || image.texNum != idImage.TEXTURE_NOT_LOADED)) {
                    i++
                    continue
                }

                // only print duplicates (from mismatched wrap / clamp, etc)
                if (duplicated) {
                    j = i + 1
                    while (j < Image.globalImages.images.Num()) {
                        if (Icmp(image!!.imgName, Image.globalImages.images[j]!!.imgName) == 0) {
                            break
                        }
                        j++
                    }
                    if (j == Image.globalImages.images.Num()) {
                        i++
                        continue
                    }
                }

                // "listimages touched" will list only images bound since the last "listimages touched" call
                if (touched) {
                    if (image!!.bindCount == 0) {
                        i++
                        continue
                    }
                    image.bindCount = 0
                }
                if (sorted) {
                    sortedArray[count].image = image
                    sortedArray[count].size = image!!.StorageSize()
                } else {
                    common.Printf("%4d:", i)
                    image!!.Print()
                }
                totalSize += image.StorageSize()
                count++
                i++
            }
            if (sorted) {
                Arrays.sort(
                    sortedArray,
                    0,
                    count,
                    R_QsortImageSizes()
                )
                partialSize = 0
                i = 0
                while (i < count) {
                    common.Printf("%4d:", i)
                    sortedArray[i].image!!.Print()
                    partialSize += sortedArray[i].image!!.StorageSize()
                    if (((i + 1) % 10) == 0) {
                        common.Printf(
                            "-------- %5.1f of %5.1f megs --------\n",
                            partialSize / (1024 * 1024.0f), totalSize / (1024 * 1024.0f)
                        )
                    }
                    i++
                }
            }
            common.Printf("%s", header)
            common.Printf(" %d images (%d total)\n", count, Image.globalImages.images.Num())
            common.Printf(" %5.1f total megabytes of images\n\n\n", totalSize / (1024 * 1024.0f))
            if (byClassification) {
                val classifications: Array<idList<Int>> = Array<idList<Int>>(IMAGE_CLASSIFICATION.IC_COUNT) { idList() }
                i = 0
                while (i < count) {
                    val cl: Int = ClassifyImage(sortedArray[i]!!.image!!.imgName.toString())
                    classifications[cl].Append(i)
                    i++
                }
                i = 0
                while (i < IMAGE_CLASSIFICATION.IC_COUNT) {
                    partialSize = 0
                    val overSizedList: idList<Int> = idList()
                    j = 0
                    while (j < classifications[i].Num()) {
                        partialSize += sortedArray[classifications[i][j]]!!.image!!.StorageSize()
                        if (overSized) {
                            if (sortedArray[classifications[i][j]]!!.image!!.uploadWidth.integerValue > IC_Info[i].maxWidth && sortedArray[classifications[i][j]]!!.image!!.uploadHeight.integerValue > IC_Info[i].maxHeight
                            ) {
                                overSizedList.Append(classifications[i][j])
                            }
                        }
                        j++
                    }
                    common.Printf(
                        " Classification %s contains %d images using %5.1f megabytes\n",
                        IC_Info[i].desc,
                        classifications[i].Num(),
                        partialSize / (1024 * 1024.0f)
                    )
                    if (overSized && overSizedList.Num() != 0) {
                        common.Printf("  The following images may be oversized\n")
                        j = 0
                        while (j < overSizedList.Num()) {
                            common.Printf("    ")
                            sortedArray[overSizedList[j]]!!.image!!.Print()
                            common.Printf("\n")
                            j++
                        }
                    }
                    i++
                }
            }
        }

        companion object {
            val instance: cmdFunction_t = R_ListImages_f()
        }
    }

    /*
     ===============
     R_CombineCubeImages_f

     Used to combine animations of six separate tga files into
     a serials of 6x taller tga files, for preparation to roq compress
     ===============
     */
    internal class R_CombineCubeImages_f private constructor() : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            if (args!!.Argc() != 2) {
                common.Printf("usage: combineCubeImages <baseName>\n")
                common.Printf(" combines basename[1-6][0001-9999].tga to basenameCM[0001-9999].tga\n")
                common.Printf(" 1: forward 2:right 3:back 4:left 5:up 6:down\n")
                return
            }
            val baseName = idStr(args.Argv(1))
            common.SetRefreshOnPrint(true)
            for (frameNum in 1..9999) {
                var filename: String?
                val pics: Array<ByteBuffer?> = arrayOfNulls(6)
                val width: IntArray = intArrayOf(0)
                val height: IntArray = intArrayOf(0)
                var side: Int
                val orderRemap: IntArray = intArrayOf(1, 3, 4, 2, 5, 6)
                side = 0
                while (side < 6) {
                    filename = String.format("%s%d%04i.tga", baseName, orderRemap[side], frameNum)
                    common.Printf("reading %s\n", filename)
                    pics[side] = Image_files.R_LoadImage(filename, width, height, null, true)
                    if (pics[side] == null) {
                        common.Printf("not found.\n")
                        break
                    }
                    // convert from "camera" images to native cube map images
                    when (side) {
                        0 -> Image_process.R_RotatePic(pics[side], width[0])        // forward
                        1 -> {                                                       // back
                            Image_process.R_RotatePic(pics[side], width[0])
                            Image_process.R_HorizontalFlip(pics[side], width[0], height[0])
                            Image_process.R_VerticalFlip(pics[side], width[0], height[0])
                        }

                        2 -> Image_process.R_VerticalFlip(pics[side], width[0], height[0])   // left
                        3 -> Image_process.R_HorizontalFlip(pics[side], width[0], height[0]) // right
                        4 -> Image_process.R_RotatePic(pics[side], width[0])        // up
                        5 -> Image_process.R_RotatePic(pics[side], width[0])        // down
                    }
                    side++
                }
                if (side != 6) {
                    // C++ original has same bug (uses side++ instead of i++); fixed here
                    var j = 0
                    while (j < side) {
                        pics[j] = null
                        j++
                    }
                    break
                }
                var combined: ByteBuffer? =
                    ByteBuffer.allocate(width[0] * height[0] * 6 * 4)
                val length: Int = width[0] * height[0] * 4
                side = 0
                while (side < 6) {
                    combined!!.position(length * side)
                    combined.put(pics[side]!!.array(), 0, length)
                    pics[side] = null
                    side++
                }
                filename = String.format("%sCM%04i.tga", baseName, frameNum)
                common.Printf("writing %s\n", filename)
                R_WriteTGA(filename, combined, width[0], height[0] * 6)
                combined = null
            }
            common.SetRefreshOnPrint(false)
        }

        companion object {
            val instance: cmdFunction_t = R_CombineCubeImages_f()
        }
    }

    /*
     ===============
     R_ReloadImages_f

     Regenerate all images that came directly from files that have changed, so
     any saved changes will show up in place.

     New r_texturesize/r_texturedepth variables will take effect on reload

     reloadImages <all>
     ===============
     */
    internal class R_ReloadImages_f private constructor() : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            var i: Int
            var image: idImage?
            var all: Boolean
            var checkPrecompressed: Boolean

            // this probably isn't necessary...
            Image.globalImages.ChangeTextureFilter()
            all = false
            checkPrecompressed = false // if we are doing this as a vid_restart, look for precompressed like normal
            if (args!!.Argc() == 2) {
                if (0 == Icmp(args.Argv(1), "all")) {
                    all = true
                } else if (0 == Icmp(args.Argv(1), "reload")) {
                    all = true
                    checkPrecompressed = true
                } else {
                    common.Printf("USAGE: reloadImages <all>\n")
                    return
                }
            }
            i = 0
            while (i < Image.globalImages.images.Num()) {
                image = Image.globalImages.images[i]
                image!!.Reload(checkPrecompressed, all)
                i++
            }
        }

        companion object {
            val instance: cmdFunction_t = R_ReloadImages_f()
        }
    }

    internal class sortedImage_t {
        var image: idImage? = null
        var size: Int = 0
    }

    /*

     ================
     R_RampImage

     Creates a 0-255 ramp image
     ================
     */
    internal class R_RampImage private constructor() : GeneratorFunction() {
        override fun run(image: idImage) {
            var x: Int
            val data: ByteBuffer = ByteBuffer.allocate(256 * 4)
            x = 0
            while (x < 256) {
                // Write 4 identical bytes per entry (data[x][0..3] = x)
                val b = x.toByte()
                data.put(x * 4 + 0, b)
                data.put(x * 4 + 1, b)
                data.put(x * 4 + 2, b)
                data.put(x * 4 + 3, b)
                x++
            }
            image.GenerateImage(
                data,
                256,
                1,
                textureFilter_t.TF_NEAREST,
                false,
                textureRepeat_t.TR_CLAMP,
                textureDepth_t.TD_HIGH_QUALITY
            )
        }

        companion object {
            val instance: GeneratorFunction = R_RampImage()
        }
    }

    /*
     ================
     R_SpecularTableImage

     Creates a ramp that matches our fudged specular calculation
     ================
     */
    internal class R_SpecularTableImage private constructor() : GeneratorFunction() {
        override fun run(image: idImage) {
            var x: Int
            val data: ByteBuffer = ByteBuffer.allocate(256 * 4)
            x = 0
            while (x < 256) {
                var f: Float = x / 255.0f
                // this is the behavior of the hacked up fragment programs that
                // can't really do a power function
                f = (f - 0.75f) * 4
                if (f < 0) {
                    f = 0.0f
                }
                f = f * f
                val b: Int = (f * 255).toInt()
                // Write 4 identical bytes per entry (data[x][0..3] = b)
                val bb = b.toByte()
                data.put(x * 4 + 0, bb)
                data.put(x * 4 + 1, bb)
                data.put(x * 4 + 2, bb)
                data.put(x * 4 + 3, bb)
                x++
            }
            image.GenerateImage(
                data,
                256,
                1,
                textureFilter_t.TF_LINEAR,
                false,
                textureRepeat_t.TR_CLAMP,
                textureDepth_t.TD_HIGH_QUALITY
            )
        }

        companion object {
            val instance: GeneratorFunction = R_SpecularTableImage()
        }
    }

    /*
     ================
     R_Specular2DTableImage

     Create a 2D table that calculates ( reflection dot , specularity )
     ================
     */
    internal class R_Specular2DTableImage private constructor() : GeneratorFunction() {
        override fun run(image: idImage) {
            var x: Int
            var y: Int
            val data: ByteBuffer = ByteBuffer.allocate(256 * 256 * 4)

            x = 0
            while (x < 256) {
                val f: Float = x / 255.0f
                y = 0
                while (y < 256) {
                    val b: Int = (f.pow(y.toFloat()) * 255.0f).toInt()
                    if (b == 0) {
                        // as soon as b equals zero all remaining values in this column are going to be zero
                        // we early out to avoid pow() underflows
                        break
                    }
                    // data[y][x][0..3] = b; offset = (y*256+x)*4
                    val off = (y * 256 + x) * 4
                    val bb = b.toByte()
                    data.put(off + 0, bb)
                    data.put(off + 1, bb)
                    data.put(off + 2, bb)
                    data.put(off + 3, bb)
                    y++
                }
                x++
            }
            image.GenerateImage(
                data,
                256,
                256,
                textureFilter_t.TF_LINEAR,
                false,
                textureRepeat_t.TR_CLAMP,
                textureDepth_t.TD_HIGH_QUALITY
            )
        }

        companion object {
            val instance: GeneratorFunction = R_Specular2DTableImage()
        }
    }

    internal class R_DefaultImage private constructor() : GeneratorFunction() {
        override fun run(image: idImage) {
            image.MakeDefault()
        }

        companion object {
            val instance: GeneratorFunction = R_DefaultImage()
        }
    }

    internal class R_WhiteImage private constructor() : GeneratorFunction() {
        override fun run(image: idImage) {
            val data: ByteBuffer =
                ByteBuffer.allocate(idImage.DEFAULT_SIZE * idImage.DEFAULT_SIZE * 4)

            // solid white texture
            Arrays.fill(data.array(), 255.toByte())
            image.GenerateImage(
                data,
                idImage.DEFAULT_SIZE,
                idImage.DEFAULT_SIZE,
                textureFilter_t.TF_DEFAULT,
                false,
                textureRepeat_t.TR_REPEAT,
                textureDepth_t.TD_DEFAULT
            )
        }

        companion object {
            val instance: GeneratorFunction = R_WhiteImage()
        }
    }

    internal class R_BlackImage private constructor() : GeneratorFunction() {
        override fun run(image: idImage) {
            val data: ByteBuffer =
                ByteBuffer.allocate(idImage.DEFAULT_SIZE * idImage.DEFAULT_SIZE * 4)

            // solid black texture
            image.GenerateImage(
                data,
                idImage.DEFAULT_SIZE,
                idImage.DEFAULT_SIZE,
                textureFilter_t.TF_DEFAULT,
                false,
                textureRepeat_t.TR_REPEAT,
                textureDepth_t.TD_DEFAULT
            )
        }

        companion object {
            val instance: GeneratorFunction = R_BlackImage()
        }
    }

    internal class R_BorderClampImage private constructor() : GeneratorFunction() {
        override fun run(image: idImage) {
            val data: Array<Array<ByteArray>> = Array(BORDER_CLAMP_SIZE, { Array(BORDER_CLAMP_SIZE, { ByteArray(4) }) })

            // solid white texture with a single pixel black border
            for (a in data.indices) {
                for (b in data[a].indices) {
                    data[a][b] = byteArrayOf(-1, -1, -1, -1)
                }
            }
            for (i in 0 until BORDER_CLAMP_SIZE) {
                data[BORDER_CLAMP_SIZE - 1][i][3] = 0
                data[BORDER_CLAMP_SIZE - 1][i][2] = data[BORDER_CLAMP_SIZE - 1][i][3]
                data[BORDER_CLAMP_SIZE - 1][i][1] = data[BORDER_CLAMP_SIZE - 1][i][2]
                data[BORDER_CLAMP_SIZE - 1][i][0] = data[BORDER_CLAMP_SIZE - 1][i][1]
                data[0][i][3] = data[BORDER_CLAMP_SIZE - 1][i][0]
                data[0][i][2] = data[0][i][3]
                data[0][i][1] = data[0][i][2]
                data[0][i][0] = data[0][i][1]
                data[i][BORDER_CLAMP_SIZE - 1][3] = data[0][i][0]
                data[i][BORDER_CLAMP_SIZE - 1][2] = data[i][BORDER_CLAMP_SIZE - 1][3]
                data[i][BORDER_CLAMP_SIZE - 1][1] = data[i][BORDER_CLAMP_SIZE - 1][2]
                data[i][BORDER_CLAMP_SIZE - 1][0] = data[i][BORDER_CLAMP_SIZE - 1][1]
                data[i][0][3] = data[i][BORDER_CLAMP_SIZE - 1][0]
                data[i][0][2] = data[i][0][3]
                data[i][0][1] = data[i][0][2]
                data[i][0][0] = data[i][0][1]
            }
            image.GenerateImage(
                ByteBuffer.wrap(flatten(data)),
                BORDER_CLAMP_SIZE,
                BORDER_CLAMP_SIZE,
                textureFilter_t.TF_LINEAR,
                false,
                textureRepeat_t.TR_CLAMP_TO_BORDER,
                textureDepth_t.TD_DEFAULT
            )
            if (!glConfig.isInitialized) {
                // can't call qglTexParameterfv yet
                return
            }
            // explicit zero border
            val color: FloatBuffer = BufferUtils.createFloatBuffer(4)
            qgl.qglTexParameterfv(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_BORDER_COLOR, color)
        }

        companion object {
            val instance: GeneratorFunction = R_BorderClampImage()
        }
    }

    internal class R_RGBA8Image private constructor() : GeneratorFunction() {
        override fun run(image: idImage) {
            val data: ByteBuffer =
                ByteBuffer.allocate(idImage.DEFAULT_SIZE * idImage.DEFAULT_SIZE * 4)

            data.put(0, 16.toByte())
            data.put(1, 32.toByte())
            data.put(2, 48.toByte())
            data.put(3, 96.toByte())
            image.GenerateImage(
                data,
                idImage.DEFAULT_SIZE,
                idImage.DEFAULT_SIZE,
                textureFilter_t.TF_DEFAULT,
                false,
                textureRepeat_t.TR_REPEAT,
                textureDepth_t.TD_HIGH_QUALITY
            )
        }

        companion object {
            val instance: GeneratorFunction = R_RGBA8Image()
        }
    }

    internal class R_AlphaNotchImage private constructor() : GeneratorFunction() {
        override fun run(image: idImage) {
            val data: ByteBuffer = ByteBuffer.allocate(2 * 4)

            // this is used for alpha test clip planes
            data.put(0, 255.toByte())
            data.put(1, 255.toByte())
            data.put(2, 255.toByte())
            data.put(3, 0.toByte())
            data.put(4, 255.toByte())
            data.put(5, 255.toByte())
            data.put(6, 255.toByte())
            data.put(7, 255.toByte())
            image.GenerateImage(
                data,
                2,
                1,
                textureFilter_t.TF_NEAREST,
                false,
                textureRepeat_t.TR_CLAMP,
                textureDepth_t.TD_HIGH_QUALITY
            )
        }

        companion object {
            val instance: GeneratorFunction = R_AlphaNotchImage()
        }
    }

    internal class R_FlatNormalImage private constructor() : GeneratorFunction() {
        override fun run(image: idImage) {
            val data: Array<Array<ByteArray>> = Array<Array<ByteArray>>(
                idImage.DEFAULT_SIZE,
                { Array<ByteArray>(idImage.DEFAULT_SIZE, { ByteArray(4) }) })
            var i: Int
            val red: Int = if ((idImageManager.image_useNormalCompression.GetInteger() == 1)) 0 else 3
            val alpha: Int = if ((red == 0)) 3 else 0
            // flat normal map for default bunp mapping
            i = 0
            while (i < 4) {
                data[0][i][red] = 128.toByte()
                data[0][i][1] = 128.toByte()
                data[0][i][2] = 255.toByte()
                data[0][i][alpha] = 255.toByte()
                i++
            }
            image.GenerateImage(
                ByteBuffer.wrap(flatten(data)),
                2,
                2,
                textureFilter_t.TF_DEFAULT,
                true,
                textureRepeat_t.TR_REPEAT,
                textureDepth_t.TD_HIGH_QUALITY
            )
        }

        companion object {
            val instance: GeneratorFunction = R_FlatNormalImage()
        }
    }

    internal class R_AmbientNormalImage private constructor() : GeneratorFunction() {
        override fun run(image: idImage) {
            val data = ByteArray(idImage.DEFAULT_SIZE)
            var i: Int
            val red: Int = if ((idImageManager.image_useNormalCompression.GetInteger() == 1)) 0 else 3
            val alpha: Int = if ((red == 0)) 3 else 0
            // flat normal map for default bunp mapping
            i = 0
            while (i < idImage.DEFAULT_SIZE) {
                data[i + red] = (255 * tr.ambientLightVector.get(0)).toInt().toByte()
                data[i + 1] = (255 * tr.ambientLightVector.get(1)).toInt().toByte()
                data[i + 2] = (255 * tr.ambientLightVector.get(2)).toInt().toByte()
                data[i + alpha] = 255.toByte()
                i += 4
            }
            val pics: Array<ByteBuffer?> = arrayOfNulls(6)
            i = 0
            while (i < 6) {
                pics[i] = wrapToNativeBuffer(data)
                i++
            }
            // this must be a cube map for fragment programs to simply substitute for the normalization cube map
            image.GenerateCubeImage(pics, 2, textureFilter_t.TF_DEFAULT, true, textureDepth_t.TD_HIGH_QUALITY)
        }

        companion object {
            val instance: GeneratorFunction = R_AmbientNormalImage()
        }
    }

    /* Initialize a cube map texture object that generates RGB values
     * that when expanded to a [-1,1] range in the register combiners
     * form a normalized vector matching the per-pixel vector used to
     * access the cube map.
     */
    internal class makeNormalizeVectorCubeMap private constructor() : GeneratorFunction() {
        override fun run(image: idImage) {
            val vector = FloatArray(3)
            var i: Int
            var x: Int
            var y: Int
            val pixels: Array<ByteBuffer?> = arrayOfNulls(6)
            val size: Int
            size = NORMAL_MAP_SIZE

            i = 0
            while (i < 6) {
                pixels[i] = BufferUtils.createByteBuffer(size * size * 4)
                y = 0
                while (y < size) {
                    x = 0
                    while (x < size) {
                        getCubeVector(i, size, x, y, vector)
                        pixels[i]!!.put(4 * (y * size + x) + 0, (128 + 127 * vector[0]).toInt().toByte())
                        pixels[i]!!.put(4 * (y * size + x) + 1, (128 + 127 * vector[1]).toInt().toByte())
                        pixels[i]!!.put(4 * (y * size + x) + 2, (128 + 127 * vector[2]).toInt().toByte())
                        pixels[i]!!.put(4 * (y * size + x) + 3, 255.toByte())
                        x++
                    }
                    y++
                }
                i++
            }
            image.GenerateCubeImage(pixels, size, textureFilter_t.TF_LINEAR, false, textureDepth_t.TD_HIGH_QUALITY)
        }

        companion object {
            val instance: GeneratorFunction = makeNormalizeVectorCubeMap()
        }
    }

    /*
     ================
     R_CreateNoFalloffImage

     This is a solid white texture that is zero clamped.
     ================
     */
    internal class R_CreateNoFalloffImage private constructor() : GeneratorFunction() {
        override fun run(image: idImage) {
            var x: Int
            var y: Int
            val data: Array<Array<ByteArray>> = Array(16, { Array(FALLOFF_TEXTURE_SIZE, { ByteArray(4) }) })

            x = 1
            while (x < FALLOFF_TEXTURE_SIZE - 1) {
                y = 1
                while (y < 15) {
                    data[y][x][0] = 255.toByte()
                    data[y][x][1] = 255.toByte()
                    data[y][x][2] = 255.toByte()
                    data[y][x][3] = 255.toByte()
                    y++
                }
                x++
            }
            image.GenerateImage(
                ByteBuffer.wrap(flatten(data)),
                FALLOFF_TEXTURE_SIZE,
                16,
                textureFilter_t.TF_DEFAULT,
                false,
                textureRepeat_t.TR_CLAMP_TO_ZERO,
                textureDepth_t.TD_HIGH_QUALITY
            )
        }

        companion object {
            val instance: GeneratorFunction = R_CreateNoFalloffImage()
        }
    }

    internal class R_FogImage private constructor() : GeneratorFunction() {
        override fun run(image: idImage) {
            var x: Int
            var y: Int
            val data: Array<Array<ByteArray>> = Array(FOG_SIZE, { Array(FOG_SIZE, { ByteArray(4) }) })
            var b: Int
            val step = FloatArray(256)
            var i: Int
            var remaining = 1.0f
            i = 0
            while (i < 256) {
                step[i] = remaining
                remaining *= 0.982f
                i++
            }
            x = 0
            while (x < FOG_SIZE) {
                y = 0
                while (y < FOG_SIZE) {
                    var d: Float
                    d = Sqrt(
                        ((x - FOG_SIZE / 2) * (x - FOG_SIZE / 2)
                                + (y - FOG_SIZE / 2) * (y - FOG_SIZE / 2)).toFloat()
                    )
                    d /= (FOG_SIZE / 2 - 1).toFloat()
                    b = (d * 255).toInt()
                    if (b <= 0) {
                        b = 0
                    } else if (b > 255) {
                        b = 255
                    }
                    b = (255 * (1.0f - step[b])).toInt()
                    if (b <= 0) {
                        b = 0
                    } else if (b > 255) {
                        b = 255
                    }
                    if ((x == 0) || (x == FOG_SIZE - 1) || (y == 0) || (y == FOG_SIZE - 1)) {
                        b = 255 // avoid clamping issues
                    }
                    data[y][x][2] = 255.toByte()
                    data[y][x][1] = data[y][x][2]
                    data[y][x][0] = data[y][x][1]
                    data[y][x][3] = b.toByte()
                    y++
                }
                x++
            }
            image.GenerateImage(
                ByteBuffer.wrap(flatten(data)),
                FOG_SIZE,
                FOG_SIZE,
                textureFilter_t.TF_LINEAR,
                false,
                textureRepeat_t.TR_CLAMP,
                textureDepth_t.TD_HIGH_QUALITY
            )
        }

        companion object {
            val instance: GeneratorFunction = R_FogImage()
        }
    }

    /*
     ================
     R_FogEnterImage

     Modulate the fog alpha density based on the distance of the
     start and end points to the terminator plane
     ================
     */
    internal class R_FogEnterImage private constructor() : GeneratorFunction() {
        override fun run(image: idImage) {
            var x: Int
            var y: Int
            val data: Array<Array<ByteArray>> =
                Array(FOG_ENTER_SIZE, { Array(FOG_ENTER_SIZE, { ByteArray(4) }) })
            var b: Int
            x = 0
            while (x < FOG_ENTER_SIZE) {
                y = 0
                while (y < FOG_ENTER_SIZE) {
                    var d: Float
                    d = FogFraction(
                        (x - (FOG_ENTER_SIZE / 2)).toFloat(),
                        (y - (FOG_ENTER_SIZE / 2)).toFloat()
                    )
                    b = (d * 255).toInt()
                    if (b <= 0) {
                        b = 0
                    } else if (b > 255) {
                        b = 255
                    }
                    data[y][x][2] = 255.toByte()
                    data[y][x][1] = data[y][x][2]
                    data[y][x][0] = data[y][x][1]
                    data[y][x][3] = b.toByte()
                    y++
                }
                x++
            }

            // if mipmapped, acutely viewed surfaces fade wrong
            image.GenerateImage(
                ByteBuffer.wrap(flatten(data)),
                FOG_ENTER_SIZE,
                FOG_ENTER_SIZE,
                textureFilter_t.TF_LINEAR,
                false,
                textureRepeat_t.TR_CLAMP,
                textureDepth_t.TD_HIGH_QUALITY
            )
        }

        companion object {
            val instance: GeneratorFunction = R_FogEnterImage()
        }
    }

    class R_QuadraticImage private constructor() : GeneratorFunction() {
        override fun run(image: idImage) {
            var x: Int
            var y: Int
            val data: Array<Array<ByteArray>> = Array(QUADRATIC_HEIGHT, { Array(QUADRATIC_WIDTH, { ByteArray(4) }) })
            var b: Int
            x = 0
            while (x < QUADRATIC_WIDTH) {
                y = 0
                while (y < QUADRATIC_HEIGHT) {
                    var d: Float
                    d = x - (QUADRATIC_WIDTH / 2 - 0.5f)
                    d = abs(d)
                    d -= 0.5f
                    d /= (QUADRATIC_WIDTH / 2).toFloat()
                    d = (1.0f - d)
                    d = (d * d)
                    b = (d * 255).toInt()
                    if (b <= 0) {
                        b = 0
                    } else if (b > 255) {
                        b = 255
                    }
                    data[y][x][2] = b.toByte()
                    data[y][x][1] = data[y][x][2]
                    data[y][x][0] = data[y][x][1]
                    data[y][x][3] = 255.toByte()
                    y++
                }
                x++
            }
            image.GenerateImage(
                ByteBuffer.wrap(flatten(data)),
                QUADRATIC_WIDTH,
                QUADRATIC_HEIGHT,
                textureFilter_t.TF_DEFAULT,
                false,
                textureRepeat_t.TR_CLAMP,
                textureDepth_t.TD_HIGH_QUALITY
            )
        }

        companion object {
            val instance: GeneratorFunction = R_QuadraticImage()
        }
    }

    /*
     =======================
     R_QsortImageSizes

     =======================
     */
    internal class R_QsortImageSizes : cmp_t<sortedImage_t?> {
        override fun compare(ea: sortedImage_t?, eb: sortedImage_t?): Int {
            if (ea!!.size > eb!!.size) {
                return -1
            }
            if (ea.size < eb.size) {
                return 1
            }
            return Icmp(ea.image!!.imgName, eb.image!!.imgName)
        }
    }
}

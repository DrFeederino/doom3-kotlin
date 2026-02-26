package neo.Renderer

import neo.framework.Common.Companion.common
import org.lwjgl.BufferUtils
import java.nio.ByteBuffer

object Image_process {
    private val MAX_DIMENSION: Int = 4096

    /*
     ================
     R_ResampleTexture

     Used to resample images in a more general than quartering fashion.

     This will only have filter coverage if the resampled size
     is greater than half the original size.

     If a larger shrinking is needed, use the mipmap function 
     after resampling to the next lower power of two.
     ================
     */
    fun R_ResampleTexture(`in`: ByteBuffer, inwidth: Int, inheight: Int, outwidth: Int, outheight: Int): ByteBuffer {
        var outwidth: Int = outwidth
        var outheight: Int = outheight
        var i: Int
        var j: Int
        var inrow: ByteBuffer
        var inrow2: ByteBuffer
        /*unsigned*/
        var frac: Int
        val fracstep: Int
        /*unsigned*/
        val p1 = IntArray(MAX_DIMENSION)
        val p2 = IntArray(MAX_DIMENSION)
        var pix1: ByteBuffer
        var pix2: ByteBuffer
        var pix3: ByteBuffer
        var pix4: ByteBuffer
        val out: ByteBuffer
        val out_p: ByteBuffer
        if (outwidth > MAX_DIMENSION) {
            outwidth = MAX_DIMENSION
        }
        if (outheight > MAX_DIMENSION) {
            outheight = MAX_DIMENSION
        }
        out = ByteBuffer.allocate(outwidth * outheight * 4) //(byte *)R_StaticAlloc( outwidth * outheight * 4 );
        out_p = out
        fracstep = inwidth * 0x10000 / outwidth
        frac = fracstep shr 2
        i = 0
        while (i < outwidth) {
            p1[i] = 4 * (frac shr 16)
            frac += fracstep
            i++
        }
        frac = 3 * (fracstep shr 2)
        i = 0
        while (i < outwidth) {
            p2[i] = 4 * (frac shr 16)
            frac += fracstep
            i++
        }
        i = 0
        while (i < outheight) {
            inrow = `in`.duplicate()
            inrow.position(4 * inwidth * (((i + 0.25f) * inheight / outheight).toInt()))
            inrow2 = `in`.duplicate() //
            inrow2.position(4 * inwidth * (((i + 0.75f) * inheight / outheight).toInt()))
            frac = fracstep shr 1
            j = 0
            while (j < outwidth) {
                pix1 = inrow.duplicate().position(p1[j])
                pix2 = inrow.duplicate().position(p2[j])
                pix3 = inrow2.duplicate().position(p1[j])
                pix4 = inrow2.duplicate().position(p2[j])
                out_p.put((addUnsignedBytes(pix1.get(), pix2.get(), pix3.get(), pix4.get()) shr 2).toByte())
                out_p.put((addUnsignedBytes(pix1.get(), pix2.get(), pix3.get(), pix4.get()) shr 2).toByte())
                out_p.put((addUnsignedBytes(pix1.get(), pix2.get(), pix3.get(), pix4.get()) shr 2).toByte())
                out_p.put((addUnsignedBytes(pix1.get(), pix2.get(), pix3.get(), pix4.get()) shr 2).toByte())
                j++
            }
            i++
        }
        return out
    }

    /*
     ================
     R_Dropsample

     Used to resample images in a more general than quartering fashion.
     Normal maps and such should not be bilerped.
     ================
     */
    fun R_Dropsample(`in`: ByteBuffer?, inwidth: Int, inheight: Int, outwidth: Int, outheight: Int): ByteArray {
        var i: Int
        var j: Int
        var k: Int
        var inrow: Int
        var pix1: Int
        val out: ByteArray
        var out_p: Int
        out = ByteArray(outwidth * outheight * 4) // R_StaticAlloc(outwidth * outheight * 4);
        out_p = 0
        i = 0
        while (i < outheight) {
            inrow =  /*in +*/(4 * inwidth * (((i + 0.25f) * inheight / outheight).toInt()))
            j = 0
            while (j < outwidth) {
                k = j * inwidth / outwidth
                pix1 = inrow + k * 4
                out[out_p + (j * 4) + 0] = `in`!!.get(pix1 + 0)
                out[out_p + (j * 4) + 1] = `in`.get(pix1 + 1)
                out[out_p + (j * 4) + 2] = `in`.get(pix1 + 2)
                out[out_p + (j * 4) + 3] = `in`.get(pix1 + 3)
                j++
            }
            i++
            out_p += outwidth * 4
        }
        return out
    }

    /*
     ===============
     R_SetBorderTexels

     ===============
     */
    fun R_SetBorderTexels(inBase: ByteBuffer?, width: Int, height: Int, border: ByteArray /*[4]*/) {
        var i: Int
        var out: Int
        out = 0 //inBase;
        i = 0
        while (i < height) {
            inBase!!.put(out + 0, border[0])
            inBase.put(out + 1, border[1])
            inBase.put(out + 2, border[2])
            inBase.put(out + 3, border[3])
            i++
            out += width * 4
        }
        out =  /*inBase+*/(width - 1) * 4
        i = 0
        while (i < height) {
            inBase!!.put(out + 0, border[0])
            inBase.put(out + 1, border[1])
            inBase.put(out + 2, border[2])
            inBase.put(out + 3, border[3])
            i++
            out += width * 4
        }
        out = 0 //inBase;
        i = 0
        while (i < width) {
            inBase!!.put(out + 0, border[0])
            inBase.put(out + 1, border[1])
            inBase.put(out + 2, border[2])
            inBase.put(out + 3, border[3])
            i++
            out += 4
        }
        out =  /*inBase+*/width * 4 * (height - 1)
        i = 0
        while (i < width) {
            inBase!!.put(out + 0, border[0])
            inBase.put(out + 1, border[1])
            inBase.put(out + 2, border[2])
            inBase.put(out + 3, border[3])
            i++
            out += 4
        }
    }

    /*
     ===============
     R_SetBorderTexels3D

     ===============
     */
    fun R_SetBorderTexels3D(inBase: ByteBuffer, width: Int, height: Int, depth: Int, border: ByteArray /*[4]*/) {
        var i: Int
        var j: Int
        var out: Int
        val row: Int
        val plane: Int
        row = width * 4
        plane = row * depth
        j = 1
        while (j < depth - 1) {
            out =  /*inBase +*/j * plane
            i = 0
            while (i < height) {
                inBase.put(out + 0, border[0])
                inBase.put(out + 1, border[1])
                inBase.put(out + 2, border[2])
                inBase.put(out + 3, border[3])
                i++
                out += row
            }
            out =  /*inBase+*/(width - 1) * 4 + j * plane
            i = 0
            while (i < height) {
                inBase.put(out + 0, border[0])
                inBase.put(out + 1, border[1])
                inBase.put(out + 2, border[2])
                inBase.put(out + 3, border[3])
                i++
                out += row
            }
            out =  /*inBase +*/j * plane
            i = 0
            while (i < width) {
                inBase.put(out + 0, border[0])
                inBase.put(out + 1, border[1])
                inBase.put(out + 2, border[2])
                inBase.put(out + 3, border[3])
                i++
                out += 4
            }
            out =  /*inBase+*/width * 4 * (height - 1) + j * plane
            i = 0
            while (i < width) {
                inBase.put(out + 0, border[0])
                inBase.put(out + 1, border[1])
                inBase.put(out + 2, border[2])
                inBase.put(out + 3, border[3])
                i++
                out += 4
            }
            j++
        }
        out = 0 //inBase;
        i = 0
        while (i < plane) {
            inBase.put(out + 0, border[0])
            inBase.put(out + 1, border[1])
            inBase.put(out + 2, border[2])
            inBase.put(out + 3, border[3])
            i += 4
            out += 4
        }
        out =  /*inBase+*/(depth - 1) * plane
        i = 0
        while (i < plane) {
            inBase.put(out + 0, border[0])
            inBase.put(out + 1, border[1])
            inBase.put(out + 2, border[2])
            inBase.put(out + 3, border[3])
            i += 4
            out += 4
        }
    }

    /*
     ================
     R_MipMap

     Returns a new copy of the texture, quartered in size and filtered.

     If a texture is intended to be used in GL_CLAMP or GL_CLAMP_TO_EDGE mode with
     a completely transparent border, we must prevent any blurring into the outer
     ring of texels by filling it with the border from the previous level.  This
     will result in a slight shrinking of the texture as it mips, but better than
     smeared clamps...
     ================
     */
    fun R_MipMap(`in`: ByteBuffer?, width: Int, height: Int, preserveBorder: Boolean): ByteBuffer {
        var width: Int = width
        var height: Int = height
        var i: Int
        var j: Int
        var in_p: Int
        val out: ByteBuffer
        var out_p: Int
        val row: Int
        val border = ByteArray(4)
        var newWidth: Int
        var newHeight: Int
        if ((width < 1) || (height < 1) || (width + height == 2)) {
            common.FatalError("R_MipMap called with size %d,%d", width, height)
        }
        border[0] = `in`!!.get(0)
        border[1] = `in`.get(1)
        border[2] = `in`.get(2)
        border[3] = `in`.get(3)
        row = width * 4
        newWidth = width shr 1
        newHeight = height shr 1
        if (0 == newWidth) {
            newWidth = 1
        }
        if (0 == newHeight) {
            newHeight = 1
        }
        out = BufferUtils.createByteBuffer(newWidth * newHeight * 4) // R_StaticAlloc(newWidth * newHeight * 4);
        out_p = 0 //out;
        in_p = 0 //in;
        width = width shr 1
        height = height shr 1
        if (width == 0 || height == 0) {
            width += height // get largest
            if (preserveBorder) {
                i = 0
                while (i < width) {
                    out.put(out_p + 0, border[0])
                    out.put(out_p + 1, border[1])
                    out.put(out_p + 2, border[2])
                    out.put(out_p + 3, border[3])
                    i++
                    out_p += 4
                }
            } else {
                i = 0
                while (i < width) {
                    out.put(out_p + 0, (addUnsignedBytes(`in`.get(in_p + 0), `in`.get(in_p + 4)) shr 1).toByte())
                    out.put(out_p + 1, (addUnsignedBytes(`in`.get(in_p + 1), `in`.get(in_p + 5)) shr 1).toByte())
                    out.put(out_p + 2, (addUnsignedBytes(`in`.get(in_p + 2), `in`.get(in_p + 6)) shr 1).toByte())
                    out.put(out_p + 3, (addUnsignedBytes(`in`.get(in_p + 3), `in`.get(in_p + 7)) shr 1).toByte())
                    i++
                    out_p += 4
                    in_p += 8
                }
            }
            return out
        }
        i = 0
        while (i < height) {
            j = 0
            while (j < width) {
                out.put(
                    out_p + 0,
                    (addUnsignedBytes(
                        `in`.get(in_p + 0),
                        `in`.get(in_p + 4),
                        `in`.get(in_p + row + 0),
                        `in`.get(in_p + row + 4)
                    ) shr 2).toByte()
                )
                out.put(
                    out_p + 1,
                    (addUnsignedBytes(
                        `in`.get(in_p + 1),
                        `in`.get(in_p + 5),
                        `in`.get(in_p + row + 1),
                        `in`.get(in_p + row + 5)
                    ) shr 2).toByte()
                )
                out.put(
                    out_p + 2,
                    (addUnsignedBytes(
                        `in`.get(in_p + 2),
                        `in`.get(in_p + 6),
                        `in`.get(in_p + row + 2),
                        `in`.get(in_p + row + 6)
                    ) shr 2).toByte()
                )
                out.put(
                    out_p + 3,
                    (addUnsignedBytes(
                        `in`.get(in_p + 3),
                        `in`.get(in_p + 7),
                        `in`.get(in_p + row + 3),
                        `in`.get(in_p + row + 7)
                    ) shr 2).toByte()
                )
                j++
                out_p += 4
                in_p += 8
            }
            i++
            in_p += row
        }

        // copy the old border texel back around if desired
        if (preserveBorder) {
            R_SetBorderTexels(out, width, height, border)
        }
        return out
    }

    fun addUnsignedBytes(vararg bytes: Byte): Int {
        var result = 0
        for (b: Byte in bytes) {
            result += b.toInt() and 0xFF
        }
        return result
    }

    /*
     ================
     R_MipMap3D

     Returns a new copy of the texture, eigthed in size and filtered.

     If a texture is intended to be used in GL_CLAMP or GL_CLAMP_TO_EDGE mode with
     a completely transparent border, we must prevent any blurring into the outer
     ring of texels by filling it with the border from the previous level.  This
     will result in a slight shrinking of the texture as it mips, but better than
     smeared clamps...
     ================
     */
    fun R_MipMap3D(`in`: ByteBuffer, width: Int, height: Int, depth: Int, preserveBorder: Boolean): ByteBuffer {
        var width: Int = width
        var height: Int = height
        var depth: Int = depth
        var i: Int
        var j: Int
        var k: Int
        var in_p: Int
        val out: ByteBuffer
        var out_p: Int
        val row: Int
        val plane: Int
        val border = ByteArray(4)
        val newWidth: Int
        val newHeight: Int
        val newDepth: Int
        if (depth == 1) {
            return R_MipMap(`in`, width, height, preserveBorder)
        }

        // assume symetric for now
        if ((width < 2) || (height < 2) || (depth < 2)) {
            common.FatalError("R_MipMap3D called with size %d,%d,%d", width, height, depth)
        }
        border[0] = `in`.get(0)
        border[1] = `in`.get(1)
        border[2] = `in`.get(2)
        border[3] = `in`.get(3)
        row = width * 4
        plane = row * height
        newWidth = width shr 1
        newHeight = height shr 1
        newDepth = depth shr 1
        out =
            ByteBuffer.allocate(newWidth * newHeight * newDepth * 4) // R_StaticAlloc(newWidth * newHeight * newDepth * 4);
        out_p = 0 //out;
        in_p = 0 //in;
        width = width shr 1
        height = height shr 1
        depth = depth shr 1
        k = 0
        while (k < depth) {
            i = 0
            while (i < height) {
                j = 0
                while (j < width) {
                    out.put(
                        out_p + 0, (addUnsignedBytes(
                            `in`.get(in_p + 0),
                            `in`.get(in_p + 4),
                            `in`.get(in_p + row + 0),
                            `in`.get(in_p + row + 4),
                            `in`.get(in_p + plane + 0),
                            `in`.get(in_p + plane + 4),
                            `in`.get(in_p + plane + row + 0),
                            `in`.get(in_p + plane + row + 4)
                        ) shr 3).toByte()
                    )
                    out.put(
                        out_p + 1, (addUnsignedBytes(
                            `in`.get(in_p + 1),
                            `in`.get(in_p + 5),
                            `in`.get(in_p + row + 1),
                            `in`.get(in_p + row + 5),
                            `in`.get(in_p + plane + 1),
                            `in`.get(in_p + plane + 5),
                            `in`.get(in_p + plane + row + 1),
                            `in`.get(in_p + plane + row + 5)
                        ) shr 3).toByte()
                    )
                    out.put(
                        out_p + 2, (addUnsignedBytes(
                            `in`.get(in_p + 2),
                            `in`.get(in_p + 6),
                            `in`.get(in_p + row + 2),
                            `in`.get(in_p + row + 6),
                            `in`.get(in_p + plane + 2),
                            `in`.get(in_p + plane + 6),
                            `in`.get(in_p + plane + row + 2),
                            `in`.get(in_p + plane + row + 6)
                        ) shr 3).toByte()
                    )
                    out.put(
                        out_p + 3, (addUnsignedBytes(
                            `in`.get(in_p + 3),
                            `in`.get(in_p + 7),
                            `in`.get(in_p + row + 3),
                            `in`.get(in_p + row + 7),
                            `in`.get(in_p + plane + 3),
                            `in`.get(in_p + plane + 6),
                            `in`.get(in_p + plane + row + 3),
                            `in`.get(in_p + plane + row + 6)
                        ) shr 3).toByte()
                    )
                    j++
                    out_p += 4
                    in_p += 8
                }
                i++
                in_p += row
            }
            k++
            in_p += plane
        }

        // copy the old border texel back around if desired
        if (preserveBorder) {
            R_SetBorderTexels3D(out, width, height, depth, border)
        }
        return out
    }

    /*
     ==================
     R_BlendOverTexture

     Apply a color blend over a set of pixels
     ==================
     */
    fun R_BlendOverTexture(data: ByteBuffer?, pixelCount: Int, blend: IntArray /*[4]*/) {
        val inverseAlpha: Int
        val premult = IntArray(3)
        inverseAlpha = 255 - blend[3]

        premult[0] = blend[0] * blend[3]
        premult[1] = blend[1] * blend[3]
        premult[2] = blend[2] * blend[3]

        for (i in 0 until pixelCount step 4) {
            data?.put(
                i + 0,
                ((data.get(i + 0) * inverseAlpha + premult[0]) shr 9).toByte()
            )
            data?.put(
                i + 1,
                ((data.get(i + 0) * inverseAlpha + premult[1]) shr 9).toByte()
            )
            data?.put(
                i + 2,
                ((data.get(i + 0) * inverseAlpha + premult[2]) shr 9).toByte()
            )
        }
    }

    /*
     ==================
     R_HorizontalFlip

     Flip the image in place
     ==================
     */
    fun R_HorizontalFlip(data: ByteBuffer?, width: Int, height: Int) {
        var i: Int
        var j: Int
        var temp: Int
        i = 0
        while (i < height) {
            j = 0
            while (j < width / 2) {
                temp = data!!.getInt(i * width + j)
                data.putInt(i * width + j, data.getInt((i * width + width) - 1 - j))
                data.putInt((i * width + width) - 1 - j, temp)
                j++
            }
            i++
        }
    }

    fun R_VerticalFlip(data: ByteBuffer?, width: Int, height: Int) {
        var i: Int
        var j: Int
        var temp: Int
        i = 0
        while (i < width) {
            j = 0
            while (j < height / 2) {
                temp = data!!.getInt(j * width + i)
                val index: Int = (height - 1 - j) * width + i
                data.putInt(j * width + i, data.getInt(index))
                data.putInt(index, temp)
                j++
            }
            i++
        }
    }

    fun R_RotatePic(data: ByteBuffer?, width: Int) {
        var i: Int
        var j: Int
        val temp: ByteBuffer
        temp = ByteBuffer.allocate(width * width * 4) // R_StaticAlloc(width * width * 4);
        i = 0
        while (i < width) {
            j = 0
            while (j < width) {
                temp.putInt((i * width + j), data!!.getInt((j * width + i)))
                j++
            }
            i++
        }
//	memcpy( data, temp, width * width * 4 );
//        System.arraycopy(temp, 0, data, 0, width * width * 4);
        data!!.put(temp)
//        R_StaticFree(temp);
    }
}

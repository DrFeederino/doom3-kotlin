/*
===========================================================================

Doom 3 GPL Source Code
Copyright (C) 1999-2011 id Software LLC, a ZeniMax Media company.
Translated to Kotlin by Dr. Feederino with support of Claude Code

This file is part of the Doom 3 GPL Source Code ("Doom 3 Source Code").
Original source: neo/renderer/Image_process.cpp

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

import neo.framework.Common.Companion.common
import neo.idlib.containers.CInt
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
    fun R_ResampleTexture(
        `in`: ByteBuffer,
        inwidth: Int,
        inheight: Int,
        _outwidth: CInt,
        _outheight: CInt
    ): ByteBuffer {
        var outwidth: Int = _outwidth._val
        var outheight: Int = _outheight._val
        var i: Int
        var j: Int
        var frac: Int
        val fracstep: Int
        val p1 = IntArray(MAX_DIMENSION)
        val p2 = IntArray(MAX_DIMENSION)
        val out: ByteBuffer
        if (outwidth > MAX_DIMENSION) {
            outwidth = MAX_DIMENSION
        }
        if (outheight > MAX_DIMENSION) {
            outheight = MAX_DIMENSION
        }
        // write clamped values back
        _outwidth._val = outwidth
        _outheight._val = outheight
        // FIX: was ByteBuffer.allocate() (heap) — must be direct for potential OpenGL upload
        out = BufferUtils.createByteBuffer(outwidth * outheight * 4)
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
            // Use absolute byte offsets for row/column addressing
            val inrowOff = 4 * inwidth * (((i + 0.25f) * inheight / outheight).toInt())
            val inrow2Off = 4 * inwidth * (((i + 0.75f) * inheight / outheight).toInt())
            j = 0
            while (j < outwidth) {
                val pix1Off = inrowOff + p1[j]
                val pix2Off = inrowOff + p2[j]
                val pix3Off = inrow2Off + p1[j]
                val pix4Off = inrow2Off + p2[j]
                out.put(
                    (addUnsignedBytes(
                        `in`.get(pix1Off),
                        `in`.get(pix2Off),
                        `in`.get(pix3Off),
                        `in`.get(pix4Off)
                    ) shr 2).toByte()
                )
                out.put(
                    (addUnsignedBytes(
                        `in`.get(pix1Off + 1),
                        `in`.get(pix2Off + 1),
                        `in`.get(pix3Off + 1),
                        `in`.get(pix4Off + 1)
                    ) shr 2).toByte()
                )
                out.put(
                    (addUnsignedBytes(
                        `in`.get(pix1Off + 2),
                        `in`.get(pix2Off + 2),
                        `in`.get(pix3Off + 2),
                        `in`.get(pix4Off + 2)
                    ) shr 2).toByte()
                )
                out.put(
                    (addUnsignedBytes(
                        `in`.get(pix1Off + 3),
                        `in`.get(pix2Off + 3),
                        `in`.get(pix3Off + 3),
                        `in`.get(pix4Off + 3)
                    ) shr 2).toByte()
                )
                j++
            }
            i++
        }
        return out.also { it.position(0) }
    }

    /*
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
        out = ByteArray(outwidth * outheight * 4)
        out_p = 0
        i = 0
        while (i < outheight) {
            inrow = 4 * inwidth * (((i + 0.25f) * inheight / outheight).toInt())
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
        out = 0
        i = 0
        while (i < height) {
            inBase!!.put(out + 0, border[0])
            inBase.put(out + 1, border[1])
            inBase.put(out + 2, border[2])
            inBase.put(out + 3, border[3])
            i++
            out += width * 4
        }
        out = (width - 1) * 4
        i = 0
        while (i < height) {
            inBase!!.put(out + 0, border[0])
            inBase.put(out + 1, border[1])
            inBase.put(out + 2, border[2])
            inBase.put(out + 3, border[3])
            i++
            out += width * 4
        }
        out = 0
        i = 0
        while (i < width) {
            inBase!!.put(out + 0, border[0])
            inBase.put(out + 1, border[1])
            inBase.put(out + 2, border[2])
            inBase.put(out + 3, border[3])
            i++
            out += 4
        }
        out = width * 4 * (height - 1)
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
            out = j * plane
            i = 0
            while (i < height) {
                inBase.put(out + 0, border[0])
                inBase.put(out + 1, border[1])
                inBase.put(out + 2, border[2])
                inBase.put(out + 3, border[3])
                i++
                out += row
            }
            out = (width - 1) * 4 + j * plane
            i = 0
            while (i < height) {
                inBase.put(out + 0, border[0])
                inBase.put(out + 1, border[1])
                inBase.put(out + 2, border[2])
                inBase.put(out + 3, border[3])
                i++
                out += row
            }
            out = j * plane
            i = 0
            while (i < width) {
                inBase.put(out + 0, border[0])
                inBase.put(out + 1, border[1])
                inBase.put(out + 2, border[2])
                inBase.put(out + 3, border[3])
                i++
                out += 4
            }
            out = width * 4 * (height - 1) + j * plane
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
        out = 0
        i = 0
        while (i < plane) {
            inBase.put(out + 0, border[0])
            inBase.put(out + 1, border[1])
            inBase.put(out + 2, border[2])
            inBase.put(out + 3, border[3])
            i += 4
            out += 4
        }
        out = (depth - 1) * plane
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
        var newWidth = width shr 1
        var newHeight = height shr 1
        if (0 == newWidth) {
            newWidth = 1
        }
        if (0 == newHeight) {
            newHeight = 1
        }
        return R_MipMapInto(`in`, width, height, preserveBorder, BufferUtils.createByteBuffer(newWidth * newHeight * 4))
    }

    fun R_MipMapInto(`in`: ByteBuffer?, width: Int, height: Int, preserveBorder: Boolean, out: ByteBuffer): ByteBuffer {
        var width: Int = width
        var height: Int = height
        var i: Int
        var j: Int
        var in_p: Int
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
        val requiredBytes = newWidth * newHeight * 4
        if (out.capacity() < requiredBytes) {
            common.FatalError("R_MipMapInto called with output buffer too small")
        }
        out.clear()
        out.limit(requiredBytes)
        out_p = 0
        in_p = 0
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
            return out.also { it.position(0) }
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
        return out.also { it.position(0) }
    }

    fun addUnsignedBytes(b0: Byte, b1: Byte): Int {
        return (b0.toInt() and 0xFF) + (b1.toInt() and 0xFF)
    }

    fun addUnsignedBytes(b0: Byte, b1: Byte, b2: Byte, b3: Byte): Int {
        return (b0.toInt() and 0xFF) +
                (b1.toInt() and 0xFF) +
                (b2.toInt() and 0xFF) +
                (b3.toInt() and 0xFF)
    }

    fun addUnsignedBytes(
        b0: Byte, b1: Byte, b2: Byte, b3: Byte,
        b4: Byte, b5: Byte, b6: Byte, b7: Byte
    ): Int {
        return (b0.toInt() and 0xFF) +
                (b1.toInt() and 0xFF) +
                (b2.toInt() and 0xFF) +
                (b3.toInt() and 0xFF) +
                (b4.toInt() and 0xFF) +
                (b5.toInt() and 0xFF) +
                (b6.toInt() and 0xFF) +
                (b7.toInt() and 0xFF)
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
            ByteBuffer.allocate(newWidth * newHeight * newDepth * 4)
        out_p = 0
        in_p = 0
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

        // C++ iterates pixelCount times with data+=4 per pixel
        for (i in 0 until pixelCount) {
            val off = i * 4
            data?.put(
                off + 0,
                (((data.get(off + 0).toInt() and 0xFF) * inverseAlpha + premult[0]) shr 9).toByte()
            )
            data?.put(
                off + 1,
                (((data.get(off + 1).toInt() and 0xFF) * inverseAlpha + premult[1]) shr 9).toByte()
            )
            data?.put(
                off + 2,
                (((data.get(off + 2).toInt() and 0xFF) * inverseAlpha + premult[2]) shr 9).toByte()
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
                // ByteBuffer getInt/putInt uses byte offset (C++ *(int*)data uses 4-byte elements)
                temp = data!!.getInt((i * width + j) * 4)
                data.putInt((i * width + j) * 4, data.getInt((i * width + width - 1 - j) * 4))
                data.putInt((i * width + width - 1 - j) * 4, temp)
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
                // ByteBuffer getInt/putInt uses byte offset (C++ *(int*)data uses 4-byte elements)
                temp = data!!.getInt((j * width + i) * 4)
                val index: Int = ((height - 1 - j) * width + i) * 4
                data.putInt((j * width + i) * 4, data.getInt(index))
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
        temp = BufferUtils.createByteBuffer(width * width * 4)
        i = 0
        while (i < width) {
            j = 0
            while (j < width) {
                // ByteBuffer getInt/putInt uses byte offset (C++ *(int*)data uses 4-byte elements)
                temp.putInt((i * width + j) * 4, data!!.getInt((j * width + i) * 4))
                j++
            }
            i++
        }
        data!!.position(0)
        temp.position(0)
        data.put(temp)
        data.position(0)
    }
}

/*
===========================================================================

Doom 3 GPL Source Code
Copyright (C) 1999-2011 id Software LLC, a ZeniMax Media company.
Translated to Kotlin by Dr. Feederino with support of Claude Code

This file is part of the Doom 3 GPL Source Code ("Doom 3 Source Code").
Original source: neo/renderer/Image_files.cpp

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

import neo.Renderer.Image.cubeFiles_t
import neo.Renderer.Image.idImageManager
import neo.Renderer.Image_program.R_LoadImageProgram
import neo.framework.Common.Companion.common
import neo.framework.FileSystem_h.FILE_NOT_FOUND_TIMESTAMP
import neo.framework.FileSystem_h.fileSystem
import neo.framework.File_h.idFile
import neo.idlib.LittleLong
import neo.idlib.LittleShort
import neo.idlib.Text.Str.idStr
import neo.idlib.Text.Str.idStr.Companion.snPrintf
import neo.idlib.Text.ctos
import neo.idlib.containers.CInt
import org.lwjgl.BufferUtils
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.imageio.ImageIO

object Image_files {

    /*
     ================
     R_WritePalTGA
     ================
     */
    // data is an 8 bit index into palette, which is RGB (no A)
    fun R_WritePalTGA(
        filename: String?,
        data: ByteArray?,
        palette: ByteArray?,
        width: Int,
        height: Int,
        flipVertical: Boolean = false
    ) {
        val bufferSize = (width * height) + (256 * 3) + 18
        val palStart = 18
        val imgStart = 18 + (256 * 3)

        val buffer = ByteBuffer.allocate(bufferSize)
        buffer.put(1, 1.toByte())       // color map type
        buffer.put(2, 1.toByte())       // uncompressed color mapped image
        buffer.put(5, 0.toByte())       // number of palette entries (lo)
        buffer.put(6, 1.toByte())       // number of palette entries (hi)
        buffer.put(7, 24.toByte())      // color map bpp
        buffer.put(12, (width and 255).toByte())
        buffer.put(13, (width shr 8).toByte())
        buffer.put(14, (height and 255).toByte())
        buffer.put(15, (height shr 8).toByte())
        buffer.put(16, 8.toByte())      // pixel size
        if (!flipVertical) {
            buffer.put(17, (1 shl 5).toByte()) // flip bit, for normal top to bottom raster order
        }

        // store palette, swapping rgb to bgr
        var i = palStart
        while (i < imgStart) {
            buffer.put(i, palette!![i - palStart + 2])     // blue
            buffer.put(i + 1, palette[i - palStart + 1])   // green
            buffer.put(i + 2, palette[i - palStart + 0])   // red
            i += 3
        }

        // store the image data
        i = imgStart
        while (i < bufferSize) {
            buffer.put(i, data!![i - imgStart])
            i++
        }

        fileSystem.WriteFile(filename!!, buffer, bufferSize)
    }

    /*
     ================
     R_WriteTGA
     ================
     */
    fun R_WriteTGA(filename: String?, data: ByteBuffer?, width: Int, height: Int, flipVertical: Boolean = false) {
        val buffer: ByteBuffer
        var i: Int
        val bufferSize: Int = width * height * 4 + 18
        val imgStart = 18
        buffer = ByteBuffer.allocate(bufferSize)
        buffer.put(2, 2.toByte()) // uncompressed type
        buffer.put(12, (width and 255).toByte())
        buffer.put(13, (width shr 8).toByte())
        buffer.put(14, (height and 255).toByte())
        buffer.put(15, (height shr 8).toByte())
        buffer.put(16, 32.toByte()) // pixel size
        if (!flipVertical) {
            buffer.put(17, (1 shl 5).toByte()) // flip bit, for normal top to bottom raster order
        }

        // swap rgb to bgr
        i = imgStart
        while (i < bufferSize) {
            buffer.put(i, data!!.get(i - imgStart + 2)) // blue
            buffer.put(i + 1, data.get(i - imgStart + 1)) // green
            buffer.put(i + 2, data.get(i - imgStart + 0)) // red
            buffer.put(i + 3, data.get(i - imgStart + 3)) // alpha
            i += 4
        }
        fileSystem.WriteFile(filename!!, buffer, bufferSize)
    }

    fun R_WriteTGA(filename: idStr, data: ByteBuffer?, width: Int, height: Int) {
        R_WriteTGA(filename.toString(), data, width, height)
    }

    /*
     ========================================================================================================

     BMP LOADING

     ========================================================================================================
     */
    /*
     ==============
     LoadBMP
     ==============
     */
    fun LoadBMP(name: String?, width: IntArray?, height: IntArray?, timestamp: LongArray?): ByteBuffer? {
        val columns: Int
        var rows: Int
        val numPixels: Int
        var pixbuf: ByteBuffer
        var row: Int
        var column: Int
        val buf_p: ByteBuffer
        val buffer: Array<ByteBuffer?> = arrayOf(null)
        val length: Int
        val bmpHeader = BMPHeader_t()
        val bmpRGBA: ByteBuffer
        if (width == null || height == null) {
            fileSystem.ReadFile(name!!, null, timestamp)
            return null // just getting timestamp
        }

        //
        // load the file
        //
        length = fileSystem.ReadFile(name!!, buffer, timestamp)
        if (buffer[0] == null) {
            return null
        }
        buf_p = buffer[0]!!.duplicate()

        // C++ reads *(int*)buf_p (4 bytes); Kotlin getInt() matches
        bmpHeader.id[0] = Char(buf_p.get().toUShort())
        bmpHeader.id[1] = Char(buf_p.get().toUShort())
        bmpHeader.fileSize = LittleLong(buf_p.getInt())
        bmpHeader.reserved0 = LittleLong(buf_p.getInt())
        bmpHeader.bitmapDataOffset = LittleLong(buf_p.getInt())
        bmpHeader.bitmapHeaderSize = LittleLong(buf_p.getInt())
        bmpHeader.width = LittleLong(buf_p.getInt())
        bmpHeader.height = LittleLong(buf_p.getInt())
        bmpHeader.planes = LittleShort(buf_p.getShort())
        bmpHeader.bitsPerPixel = LittleShort(buf_p.getShort())
        bmpHeader.compression = LittleLong(buf_p.getInt())
        bmpHeader.bitmapDataSize = LittleLong(buf_p.getInt())
        bmpHeader.hRes = LittleLong(buf_p.getInt())
        bmpHeader.vRes = LittleLong(buf_p.getInt())
        bmpHeader.colors = LittleLong(buf_p.getInt())
        bmpHeader.importantColors = LittleLong(buf_p.getInt())

        // C++ memcpy doesn't advance buf_p; read palette as bytes (not chars)
        val palPos = buf_p.position()
        for (entry in bmpHeader.palette) {
            for (a in entry.indices) {
                entry[a] = buf_p.get()
            }
        }
        buf_p.position(palPos) // memcpy doesn't advance pointer in C++

        if (bmpHeader.bitsPerPixel.toInt() == 8) {
            buf_p.position(buf_p.position() + 1024)
        }

        // NOTE: Original C++ has same bug: uses && instead of ||.
        // Preserved for compatibility with original behavior.
        if (bmpHeader.id[0] != 'B' && bmpHeader.id[1] != 'M') {
            common.Error("LoadBMP: only Windows-style BMP files supported (%s)\n", name)
        }
        if (bmpHeader.fileSize != length) {
            common.Error(
                "LoadBMP: header size does not match file size (%d vs. %d) (%s)\n",
                bmpHeader.fileSize,
                length,
                name
            )
        }
        if (bmpHeader.compression != 0) {
            common.Error("LoadBMP: only uncompressed BMP files supported (%s)\n", name)
        }
        if (bmpHeader.bitsPerPixel < 8) {
            common.Error("LoadBMP: monochrome and 4-bit BMP files not supported (%s)\n", name)
        }
        columns = bmpHeader.width
        rows = bmpHeader.height
        if (rows < 0) {
            rows = -rows
        }
        numPixels = columns * rows
        width[0] = columns
        height[0] = rows

        bmpRGBA = BufferUtils.createByteBuffer(numPixels * 4)
        row = rows - 1
        while (row >= 0) {
            pixbuf = bmpRGBA.duplicate()
            pixbuf.position(row * columns * 4)
            column = 0
            while (column < columns) {
                var red: Byte
                var green: Byte
                var blue: Byte
                var alpha: Byte
                var palIndex: Int
                var shortPixel: Short
                when (bmpHeader.bitsPerPixel.toInt()) {
                    8 -> {
                        // mask with 0xFF to get unsigned palette index (Kotlin bytes are signed)
                        palIndex = buf_p.get().toInt() and 0xFF
                        pixbuf.put(bmpHeader.palette[palIndex][2])
                        pixbuf.put(bmpHeader.palette[palIndex][1])
                        pixbuf.put(bmpHeader.palette[palIndex][0])
                        pixbuf.put(0xff.toByte())
                    }

                    16 -> {
                        // C++ reads shortPixel from pixbuf (output buffer), advances 2
                        shortPixel = pixbuf.getShort()
                        pixbuf.put(((shortPixel.toInt() and (31 shl 10)) shr 7).toByte())
                        pixbuf.put(((shortPixel.toInt() and (31 shl 5)) shr 2).toByte())
                        pixbuf.put(((shortPixel.toInt() and (31)) shl 3).toByte())
                        pixbuf.put(0xff.toByte())
                    }

                    24 -> {
                        blue = buf_p.get()
                        green = buf_p.get()
                        red = buf_p.get()
                        pixbuf.put(red)
                        pixbuf.put(green)
                        pixbuf.put(blue)
                        pixbuf.put(255.toByte())
                    }

                    32 -> {
                        blue = buf_p.get()
                        green = buf_p.get()
                        red = buf_p.get()
                        alpha = buf_p.get()
                        pixbuf.put(red)
                        pixbuf.put(green)
                        pixbuf.put(blue)
                        pixbuf.put(alpha)
                    }

                    else -> common.Error(
                        "LoadBMP: illegal pixel_size '%d' in file '%s'\n",
                        bmpHeader.bitsPerPixel,
                        name
                    )
                }
                column++
            }
            row--
        }
        return bmpRGBA
    }

    /*
     ========================================================================================================

     PCX LOADING

     ========================================================================================================
     */
    /*
     ==============
     LoadPCX
     ==============
     */
    private fun LoadPCX(
        filename: String,
        pic: Array<ByteBuffer?>,
        palette: Array<ByteBuffer?>?,
        width: IntArray?,
        height: IntArray?,
        timestamp: LongArray?
    ) {
        val raw: Array<ByteBuffer?> = arrayOf(null)
        val pcx: pcx_t
        var x: Int
        var y: Int
        val len: Int
        var runLength: Int
        var dataByte: Byte
        val out: ByteBuffer
        val xmax: Int
        val ymax: Int

        // C++ checks if(!pic) for timestamp-only queries; use width/height null check
        if (width == null || height == null) {
            fileSystem.ReadFile(filename, null, timestamp)
            return // just getting timestamp
        }

        pic[0] = null
        palette?.set(0, null)

        //
        // load the file
        //
        len = fileSystem.ReadFile(filename, raw, timestamp)
        if (raw[0] == null) {
            return
        }

        //
        // parse the PCX file
        //
        pcx = pcx_t(raw[0])
        raw[0]!!.position(pcx.dataPosition)

        xmax = LittleShort(pcx.xmax).toInt()
        ymax = LittleShort(pcx.ymax).toInt()
        if ((pcx.manufacturer.code != 0x0a
                    ) || (pcx.version.code != 5
                    ) || (pcx.encoding.code != 1
                    ) || (pcx.bits_per_pixel.code != 8
                    ) || (xmax >= 1024
                    ) || (ymax >= 1024)
        ) {
            common.Printf("Bad pcx file %s (%d x %d) (%d x %d)\n", filename, xmax + 1, ymax + 1, pcx.xmax, pcx.ymax)
            return
        }
        out = ByteBuffer.allocate((ymax + 1) * (xmax + 1))
        pic[0] = out

        // Copy 768-byte palette from end of file (C++ memcpy)
        if (palette != null) {
            palette[0] = ByteBuffer.allocate(768)
            val savedPos = raw[0]!!.position()
            raw[0]!!.position(len - 768)
            val palData = ByteArray(768)
            raw[0]!!.get(palData)
            palette[0]!!.put(palData).rewind()
            raw[0]!!.position(savedPos)
        }

        if (width != null) {
            width[0] = xmax + 1
        }
        if (height != null) {
            height[0] = ymax + 1
        }

        // FIXME: use bytes_per_line here?
        y = 0
        while (y <= ymax) {
            x = 0
            while (x <= xmax) {
                dataByte = raw[0]!!.get()
                if ((dataByte.toInt() and 0xC0) == 0xC0) {
                    runLength = dataByte.toInt() and 0x3F
                    dataByte = raw[0]!!.get()
                } else {
                    runLength = 1
                }
                // Use absolute index for correct row positioning (pix[x++] in C++)
                while (runLength-- > 0) {
                    out.put(y * (xmax + 1) + x++, dataByte)
                }
            }
            y++
        }
        if (raw[0]!!.position() > len) {
            common.Printf("PCX file %s was malformed", filename)
            pic[0] = null
        }
    }

    /*
     ==============
     LoadPCX32
     ==============
     */
    fun LoadPCX32(filename: String, width: IntArray?, height: IntArray?, timestamp: LongArray?): ByteBuffer? {
        val palette: Array<ByteBuffer?> = arrayOf(null)
        val pic8: Array<ByteBuffer?> = arrayOf(null)
        var pic: ByteBuffer? = null
        var i: Int
        val c: Int
        var p: Int
        if (width == null || height == null) {
            fileSystem.ReadFile(filename, null, timestamp)
            return null // just getting timestamp
        }
        LoadPCX(filename, pic8, palette, width, height, timestamp)
        if (pic8[0] == null) {
            return null
        }
        c = width[0] * height[0]
        pic = BufferUtils.createByteBuffer(4 * c)
        i = 0
        while (i < c) {
            val offset = i * 4
            // mask with 0xFF to get unsigned palette index (Kotlin bytes are signed)
            p = pic8[0]!!.get(i).toInt() and 0xFF
            pic.put(offset, palette[0]!!.get(p * 3))
            pic.put(offset + 1, palette[0]!!.get(p * 3 + 1))
            pic.put(offset + 2, palette[0]!!.get(p * 3 + 2))
            pic.put(offset + 3, 255.toByte())
            i++
        }
        return pic
    }

    /*
     =============
     LoadTGA
     =============
     */
    private fun LoadTGA(name: String, width: IntArray?, height: IntArray?, timestamp: LongArray?): ByteBuffer? {
        val columns: Int
        val rows: Int
        val numPixels: Int
        val fileSize: Int
        val numBytes: Int
        var pixbuf: ByteBuffer
        var row: Int
        var column: Int
        val buf_p: ByteBuffer?
        val buffer: Array<ByteBuffer?> = arrayOf(null)
        val targa_header = TargaHeader()
        val targa_rgba: ByteBuffer
        if (width == null || height == null) {
            fileSystem.ReadFile(name, null, timestamp)
            return null // just getting timestamp
        }

        //
        // load the file
        //
        fileSize = fileSystem.ReadFile(name, buffer, timestamp)
        if (buffer[0] == null) {
            return null
        }
        buf_p = buffer[0]
        buf_p!!.order(ByteOrder.LITTLE_ENDIAN).rewind()
        targa_header.id_length = buf_p.get()
        targa_header.colormap_type = buf_p.get()
        targa_header.image_type = buf_p.get()
        targa_header.colormap_index = LittleShort(buf_p.getShort())
        targa_header.colormap_length = LittleShort(buf_p.getShort())
        targa_header.colormap_size = buf_p.get()
        targa_header.x_origin = LittleShort(buf_p.getShort())
        targa_header.y_origin = LittleShort(buf_p.getShort())
        targa_header.width = LittleShort(buf_p.getShort())
        targa_header.height = LittleShort(buf_p.getShort())
        targa_header.pixel_size = buf_p.get()
        targa_header.attributes = buf_p.get()
        if ((targa_header.image_type.toInt() != 2) && (targa_header.image_type.toInt() != 10) && (targa_header.image_type.toInt() != 3)) {
            common.Error("LoadTGA( %s ): Only type 2 (RGB), 3 (gray), and 10 (RGB) TGA images supported\n", name)
        }
        if (targa_header.colormap_type.toInt() != 0) {
            common.Error("LoadTGA( %s ): colormaps not supported\n", name)
        }
        if ((targa_header.pixel_size.toInt() != 32 && targa_header.pixel_size.toInt() != 24) && targa_header.image_type.toInt() != 3) {
            common.Error("LoadTGA( %s ): Only 32 or 24 bit images supported (no colormaps)\n", name)
        }
        if (targa_header.image_type.toInt() == 2 || targa_header.image_type.toInt() == 3) {
            numBytes = targa_header.width * targa_header.height * (targa_header.pixel_size.toInt() shr 3)
            if (numBytes > fileSize - 18 - targa_header.id_length) {
                common.Error("LoadTGA( %s ): incomplete file\n", name)
            }
        }
        columns = targa_header.width.toInt()
        rows = targa_header.height.toInt()
        numPixels = columns * rows
        width[0] = columns
        height[0] = rows

        targa_rgba = BufferUtils.createByteBuffer(numPixels * 4)
        if (targa_header.id_length.toInt() != 0) {
            buf_p.position(buf_p.position() + targa_header.id_length) // skip TARGA image comment
        }
        if (targa_header.image_type.toInt() == 2 || targa_header.image_type.toInt() == 3) {
            // Uncompressed RGB or gray scale image
            row = rows - 1
            while (row >= 0) {
                pixbuf = targa_rgba.duplicate()
                pixbuf.position(row * columns * 4)
                column = 0
                while (column < columns) {
                    var red: Byte
                    var green: Byte
                    var blue: Byte
                    var alphabyte: Byte
                    when (targa_header.pixel_size.toInt()) {
                        8 -> {
                            blue = buf_p.get()
                            green = blue
                            red = blue
                            pixbuf.put(red)
                            pixbuf.put(green)
                            pixbuf.put(blue)
                            pixbuf.put(255.toByte())
                        }

                        24 -> {
                            blue = buf_p.get()
                            green = buf_p.get()
                            red = buf_p.get()
                            pixbuf.put(red)
                            pixbuf.put(green)
                            pixbuf.put(blue)
                            pixbuf.put(255.toByte())
                        }

                        32 -> {
                            blue = buf_p.get()
                            green = buf_p.get()
                            red = buf_p.get()
                            alphabyte = buf_p.get()
                            pixbuf.put(red)
                            pixbuf.put(green)
                            pixbuf.put(blue)
                            pixbuf.put(alphabyte)
                        }

                        else -> common.Error("LoadTGA( %s ): illegal pixel_size '%d'\n", name, targa_header.pixel_size)
                    }
                    column++
                }
                row--
            }
        } else if (targa_header.image_type.toInt() == 10) {   // Runlength encoded RGB images
            var red: Byte
            var green: Byte
            var blue: Byte
            var alphabyte: Byte
            var packetHeader: Int
            var packetSize: Int
            var j: Int
            red = 0
            green = 0
            blue = 0
            alphabyte = 0xff.toByte()
            row = rows - 1
            breakOut@ while (row >= 0) {
                pixbuf = targa_rgba.duplicate()
                pixbuf.position(row * columns * 4)
                column = 0
                while (column < columns) {
                    packetHeader = buf_p.get().toInt()
                    packetSize = 1 + (packetHeader and 0x7f)
                    if ((packetHeader and 0x80) != 0) {        // run-length packet
                        when (targa_header.pixel_size.toInt()) {
                            24 -> {
                                blue = buf_p.get()
                                green = buf_p.get()
                                red = buf_p.get()
                                alphabyte = 255.toByte()
                            }

                            32 -> {
                                blue = buf_p.get()
                                green = buf_p.get()
                                red = buf_p.get()
                                alphabyte = buf_p.get()
                            }

                            else -> common.Error(
                                "LoadTGA( %s ): illegal pixel_size '%d'\n",
                                name,
                                targa_header.pixel_size
                            )
                        }
                        j = 0
                        while (j < packetSize) {
                            pixbuf.put(red)
                            pixbuf.put(green)
                            pixbuf.put(blue)
                            pixbuf.put(alphabyte)
                            column++
                            if (column == columns) { // run spans across rows
                                column = 0
                                if (row > 0) {
                                    row--
                                } else {
                                    break@breakOut
                                }
                                pixbuf = targa_rgba.duplicate()
                                pixbuf.position(row * columns * 4)
                            }
                            j++
                        }
                    } else {                            // non run-length packet
                        j = 0
                        while (j < packetSize) {
                            when (targa_header.pixel_size.toInt()) {
                                24 -> {
                                    blue = buf_p.get()
                                    green = buf_p.get()
                                    red = buf_p.get()
                                    pixbuf.put(red)
                                    pixbuf.put(green)
                                    pixbuf.put(blue)
                                    pixbuf.put(255.toByte())
                                }

                                32 -> {
                                    blue = buf_p.get()
                                    green = buf_p.get()
                                    red = buf_p.get()
                                    alphabyte = buf_p.get()
                                    pixbuf.put(red)
                                    pixbuf.put(green)
                                    pixbuf.put(blue)
                                    pixbuf.put(alphabyte)
                                }

                                else -> common.Error(
                                    "LoadTGA( %s ): illegal pixel_size '%d'\n",
                                    name,
                                    targa_header.pixel_size
                                )
                            }
                            column++
                            if (column == columns) { // pixel packet run spans across rows
                                column = 0
                                if (row > 0) {
                                    row--
                                } else {
                                    break@breakOut
                                }
                                pixbuf = targa_rgba.duplicate()
                                pixbuf.position(row * columns * 4)
                            }
                            j++
                        }
                    }
                }
                row--
            }
        }
        if ((targa_header.attributes.toInt() and (1 shl 5)) != 0) {            // image flp bit
            Image_process.R_VerticalFlip(targa_rgba, width[0], height[0])
        }
        return targa_rgba
    }

    /*
     ========================================================================================================

     JPG LOADING

     Uses Java ImageIO (replaces original libjpeg / dhewm3 stb_image)

     ========================================================================================================
     */
    /*
     =============
     LoadJPG
     =============
     */
    private fun LoadJPG(filename: String, width: IntArray?, height: IntArray?, timestamp: LongArray?): ByteBuffer? {
        val len: Int
        val f: idFile?

        f = fileSystem.OpenFileRead(filename)
        if (f == null) {
            return null
        }
        len = f.Length()
        if (timestamp != null) {
            timestamp[0] = f.Timestamp()
        }
        if (width == null || height == null) {
            fileSystem.CloseFile(f)
            return null // just getting timestamp
        }

        // Use heap ByteBuffer (direct buffers don't support .array())
        val buf = ByteBuffer.allocate(len)
        f.Read(buf)
        fileSystem.CloseFile(f)

        val image: BufferedImage
        try {
            image = ImageIO.read(ByteArrayInputStream(buf.array()))
        } catch (ex: IOException) {
            common.Warning("Failed to load JPEG %s: %s", filename, ex.message ?: "unknown error")
            return null
        }

        // Use getRGB() for known ARGB format, convert to RGBA (replaces stb_image)
        val w = image.width
        val h = image.height
        width[0] = w
        height[0] = h
        val out = BufferUtils.createByteBuffer(w * h * 4)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val argb = image.getRGB(x, y)
                out.put(((argb shr 16) and 0xFF).toByte())  // R
                out.put(((argb shr 8) and 0xFF).toByte())   // G
                out.put((argb and 0xFF).toByte())            // B
                out.put(0xFF.toByte())                        // A (JPEG has no alpha)
            }
        }
        out.rewind()
        return out
    }

    //===================================================================
    /*
     =================
     R_LoadImage

     Loads any of the supported image types into a cannonical
     32 bit format.

     Automatically attempts to load .jpg files if .tga files fail to load.

     *pic will be NULL if the load failed.

     Anything that is going to make this into a texture would use
     makePowerOf2 = true, but something loading an image as a lookup
     table of some sort would leave it in identity form.

     It is important to do this at image load time instead of texture load
     time for bump maps.

     Timestamp may be NULL if the value is going to be ignored

     If pic is NULL, the image won't actually be loaded, it will just find the
     timestamp.
     =================
     */
    fun R_LoadImage(
        cname: String?,
        width: IntArray?,
        height: IntArray?,
        timestamp: LongArray?,
        makePowerOf2: Boolean
    ): ByteBuffer? {
        val name = idStr((cname)!!)
        var pic: ByteBuffer? = null
        if (timestamp != null) {
            timestamp[0] = FILE_NOT_FOUND_TIMESTAMP.toLong()
        }
        if (width != null) {
            width[0] = 0
        }
        if (height != null) {
            height[0] = 0
        }
        name.DefaultFileExtension(".tga")
        if (name.Length() < 5) {
            return null
        }
        name.ToLower()
        val ext = idStr()
        name.ExtractFileExtension(ext)
        if (ext.equals("tga")) {
            pic = LoadTGA(name.toString(), width, height, timestamp) // try tga first
            if ((pic == null) || (timestamp != null && timestamp[0] == FILE_NOT_FOUND_TIMESTAMP.toLong())) {
                name.StripFileExtension()
                name.DefaultFileExtension(".jpg")
                pic = LoadJPG(name.toString(), width, height, timestamp)
            }
        } else if (ext.equals("pcx")) {
            pic = LoadPCX32(name.toString(), width, height, timestamp)
        } else if (ext.equals("bmp")) {
            pic = LoadBMP(name.toString(), width, height, timestamp)
        } else if (ext.equals("jpg")) {
            pic = LoadJPG(name.toString(), width, height, timestamp)
        }
        if (((width != null && width[0] < 1)
                    || (height != null && height[0] < 1))
        ) {
            if (pic != null) {
                pic = null
            }
        }

        //
        // convert to exact power of 2 sizes
        //
        if (pic != null && makePowerOf2) {
            val w: Int
            val h: Int
            var scaled_width: Int
            var scaled_height: Int
            val resampledBuffer: ByteBuffer?
            w = width!![0]
            h = height!![0]
            scaled_width = 1
            while (scaled_width < w) {
                scaled_width = scaled_width shl 1
            }
            scaled_height = 1
            while (scaled_height < h) {
                scaled_height = scaled_height shl 1
            }
            if (scaled_width != w || scaled_height != h) {
                if (idImageManager.image_roundDown.GetBool() && scaled_width > w) {
                    scaled_width = scaled_width shr 1
                }
                if (idImageManager.image_roundDown.GetBool() && scaled_height > h) {
                    scaled_height = scaled_height shr 1
                }
                val outWidth = CInt(scaled_width)
                val outHeight = CInt(scaled_height)
                resampledBuffer = Image_process.R_ResampleTexture(pic, w, h, outWidth, outHeight)
                if (outWidth._val != scaled_width || outHeight._val != scaled_height) {
                    common.Warning(
                        "Texture '%s' didn't have power-of-two size *and* was too big, scaled from %dx%d to %dx%d",
                        name.toString(), w, h, outWidth._val, outHeight._val
                    )
                }
                pic = resampledBuffer
                width[0] = outWidth._val
                height[0] = outHeight._val
            }
        }
        return pic
    }

    /*
     =======================
     R_LoadCubeImages

     Loads six files with proper extensions
     =======================
     */
    fun R_LoadCubeImages(
        imgName: String?,
        extensions: cubeFiles_t,
        pics: Array<ByteBuffer?>? /*[6]*/,
        outSize: IntArray?,
        timestamp: LongArray?
    ): Boolean {
        var i: Int
        var j: Int
        val cameraSides: Array<String> =
            arrayOf("_forward.tga", "_back.tga", "_left.tga", "_right.tga", "_up.tga", "_down.tga")
        val axisSides: Array<String> = arrayOf("_px.tga", "_nx.tga", "_py.tga", "_ny.tga", "_pz.tga", "_nz.tga")
        val sides: Array<String>
        val fullName = CharArray(Image.MAX_IMAGE_NAME)
        val width: IntArray = intArrayOf(0)
        val height: IntArray = intArrayOf(0)
        var size = 0
        if (extensions == cubeFiles_t.CF_CAMERA) {
            sides = cameraSides
        } else {
            sides = axisSides
        }

        // FIXME: precompressed cube map files
        if (pics != null) {
            for (k in pics.indices) {
                pics[k] = null
            }
        }
        if (timestamp != null) {
            timestamp[0] = 0
        }
        i = 0
        while (i < 6) {
            snPrintf(fullName, fullName.size, "%s%s", (imgName)!!, sides[i])
            val thisTime = LongArray(1)
            if (null == pics) {
                // just checking timestamps
                R_LoadImageProgram(ctos(fullName), width, height, thisTime)
            } else {
                pics[i] = R_LoadImageProgram(ctos(fullName), width, height, thisTime)
            }
            if (thisTime[0] == FILE_NOT_FOUND_TIMESTAMP.toLong()) {
                break
            }
            if (i == 0) {
                size = width[0]
            }
            if (width[0] != size || height[0] != size) {
                common.Warning("Mismatched sizes on cube map '%s'", imgName)
                break
            }
            if (timestamp!![0] != 0L) {
                if (thisTime[0] > timestamp[0]) {
                    timestamp[0] = thisTime[0]
                }
            }
            if (pics != null && extensions == cubeFiles_t.CF_CAMERA) {
                // convert from "camera" images to native cube map images
                when (i) {
                    0 -> Image_process.R_RotatePic(pics[i], width[0])
                    1 -> {
                        Image_process.R_RotatePic(pics[i], width[0])
                        Image_process.R_HorizontalFlip(pics[i], width[0], height[0])
                        Image_process.R_VerticalFlip(pics[i], width[0], height[0])
                    }

                    2 -> Image_process.R_VerticalFlip(pics[i], width[0], height[0])
                    3 -> Image_process.R_HorizontalFlip(pics[i], width[0], height[0])
                    4 -> Image_process.R_RotatePic(pics[i], width[0])
                    5 -> Image_process.R_RotatePic(pics[i], width[0])
                }
            }
            i++
        }
        if (i != 6) {
            // we had an error, so free everything
            if (pics != null) {
                j = 0
                while (j < i) {
                    pics[j] = null
                    j++
                }
            }
            if (timestamp != null) {
                timestamp[0] = 0
            }
            return false
        }
        if (outSize != null) {
            outSize[0] = size
        }
        return true
    }

    /*
     ========================================================================

     PCX files are used for 8 bit images

     ========================================================================
     */
    private class pcx_t(byteBuffer: ByteBuffer?) {
        var manufacturer: Char = 0.toChar()
        var version: Char = 0.toChar()
        var encoding: Char = 0.toChar()
        var bits_per_pixel: Char = 0.toChar()
        var xmin: Short = 0
        var ymin: Short = 0
        var xmax: Short = 0
        var ymax: Short = 0
        var hres: Short = 0
        var vres: Short = 0
        var palette: ByteArray = ByteArray(48)
        var reserved: Char = 0.toChar()
        var color_planes: Char = 0.toChar()
        var bytes_per_line: Short = 0
        var palette_type: Short = 0
        var filler: ByteArray = ByteArray(58)
        var dataPosition: Int = 0 // offset to first data byte (replaces C++ unbounded data member)

        init {
            if (byteBuffer != null) {
                byteBuffer.order(ByteOrder.LITTLE_ENDIAN)
                byteBuffer.rewind()
                manufacturer = (byteBuffer.get().toInt() and 0xFF).toChar()
                version = (byteBuffer.get().toInt() and 0xFF).toChar()
                encoding = (byteBuffer.get().toInt() and 0xFF).toChar()
                bits_per_pixel = (byteBuffer.get().toInt() and 0xFF).toChar()
                xmin = byteBuffer.getShort()
                ymin = byteBuffer.getShort()
                xmax = byteBuffer.getShort()
                ymax = byteBuffer.getShort()
                hres = byteBuffer.getShort()
                vres = byteBuffer.getShort()
                for (i in palette.indices) {
                    palette[i] = byteBuffer.get()
                }
                reserved = (byteBuffer.get().toInt() and 0xFF).toChar()
                color_planes = (byteBuffer.get().toInt() and 0xFF).toChar()
                bytes_per_line = byteBuffer.getShort()
                palette_type = byteBuffer.getShort()
                for (i in filler.indices) {
                    filler[i] = byteBuffer.get()
                }
                dataPosition = byteBuffer.position() // C++: raw = &pcx->data
            }
        }
    }

    /*
     ========================================================================

     TGA files are used for 24/32 bit images

     ========================================================================
     */
    private class TargaHeader {
        var id_length: Byte = 0
        var colormap_type: Byte = 0
        var image_type: Byte = 0
        var colormap_index: Short = 0
        var colormap_length: Short = 0
        var colormap_size: Byte = 0
        var x_origin: Short = 0
        var y_origin: Short = 0
        var width: Short = 0
        var height: Short = 0
        var pixel_size: Byte = 0
        var attributes: Byte = 0
    }

    /*
     ========================================================================

     BMP files

     ========================================================================
     */
    private class BMPHeader_t {
        var id: CharArray = CharArray(2)

        // C++ unsigned int fields = 4 bytes = Kotlin Int
        var fileSize: Int = 0
        var reserved0: Int = 0
        var bitmapDataOffset: Int = 0
        var bitmapHeaderSize: Int = 0
        var width: Int = 0
        var height: Int = 0
        var planes: Short = 0
        var bitsPerPixel: Short = 0
        var compression: Int = 0
        var bitmapDataSize: Int = 0
        var hRes: Int = 0
        var vRes: Int = 0
        var colors: Int = 0
        var importantColors: Int = 0

        // C++ unsigned char palette[256][4] = Kotlin ByteArray
        var palette: Array<ByteArray> = Array(256) { ByteArray(4) }
    }
}

package neo.Renderer

import neo.Renderer.Image.cubeFiles_t
import neo.Renderer.Image.idImageManager
import neo.Renderer.Image_program.R_LoadImageProgram
import neo.TempDump.TODO_Exception
import neo.TempDump.ctos
import neo.framework.Common.Companion.common
import neo.framework.FileSystem_h.FILE_NOT_FOUND_TIMESTAMP
import neo.framework.FileSystem_h.fileSystem
import neo.framework.File_h.idFile
import neo.idlib.LittleLong
import neo.idlib.LittleShort
import neo.idlib.Text.Str.idStr
import neo.idlib.Text.Str.idStr.Companion.snPrintf
import org.lwjgl.BufferUtils
import java.awt.image.BufferedImage
import java.awt.image.DataBufferByte
import java.io.ByteArrayInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.logging.Level
import java.util.logging.Logger
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
        throw TODO_Exception()
        //	byte	*buffer;
//	int		i;
//	int		bufferSize = (width * height) + (256 * 3) + 18;
//	int     palStart = 18;
//	int     imgStart = 18 + (256 * 3);
//
//	buffer = (byte *)Mem_Alloc( bufferSize );
//	memset( buffer, 0, 18 );
//	buffer[1] = 1;		// color map type
//	buffer[2] = 1;		// uncompressed color mapped image
//	buffer[5] = 0;		// number of palette entries (lo)
//	buffer[6] = 1;		// number of palette entries (hi)
//	buffer[7] = 24;		// color map bpp
//	buffer[12] = width&255;
//	buffer[13] = width>>8;
//	buffer[14] = height&255;
//	buffer[15] = height>>8;
//	buffer[16] = 8;	// pixel size
//	if ( !flipVertical ) {
//		buffer[17] = (1<<5);	// flip bit, for normal top to bottom raster order
//	}
//
//	// store palette, swapping rgb to bgr
//	for ( i=palStart ; i<imgStart ; i+=3 ) {
//		buffer[i] = palette[i-palStart+2];		// blue
//		buffer[i+1] = palette[i-palStart+1];		// green
//		buffer[i+2] = palette[i-palStart+0];		// red
//	}
//
//	// store the image data
//	for ( i=imgStart ; i<bufferSize ; i++ ) {
//		buffer[i] = data[i-imgStart];
//	}
//
//	fileSystem->WriteFile( filename, buffer, bufferSize );
//
//	Mem_Free (buffer);
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
        buffer = ByteBuffer.allocate(bufferSize) // Mem_Alloc(bufferSize);
        //	memset( buffer, 0, 18 );
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

//        Mem_Free(buffer);
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
        bmpHeader.id[0] = Char(buf_p.get().toUShort()) //*buf_p++;
        bmpHeader.id[1] = Char(buf_p.get().toUShort()) //*buf_p++;
        bmpHeader.fileSize = LittleLong( /* ( long * )*/buf_p.getLong()).toLong() //	buf_p += 4;
        bmpHeader.reserved0 = LittleLong(buf_p.getLong()).toLong() //	buf_p += 4;
        bmpHeader.bitmapDataOffset = LittleLong(buf_p.getLong()).toLong() //	buf_p += 4;
        bmpHeader.bitmapHeaderSize = LittleLong(buf_p.getLong()).toLong() //	buf_p += 4;
        bmpHeader.width = LittleLong(buf_p.getLong()).toLong() //	buf_p += 4;
        bmpHeader.height = LittleLong(buf_p.getLong()).toLong() //	buf_p += 4;
        bmpHeader.planes = LittleShort(buf_p.getShort()) //	buf_p += 2;
        bmpHeader.bitsPerPixel = LittleShort(buf_p.getShort()) //	buf_p += 2;
        bmpHeader.compression = LittleLong(buf_p.getLong()).toLong() //	buf_p += 4;
        bmpHeader.bitmapDataSize = LittleLong(buf_p.getLong()).toLong() //	buf_p += 4;
        bmpHeader.hRes = LittleLong(buf_p.getLong()).toLong() //	buf_p += 4;
        bmpHeader.vRes = LittleLong(buf_p.getLong()).toLong() //	buf_p += 4;
        bmpHeader.colors = LittleLong(buf_p.getLong()).toLong() //	buf_p += 4;
        bmpHeader.importantColors = LittleLong(buf_p.getLong()).toLong() //	buf_p += 4;
        for (palette: CharArray in bmpHeader.palette) {
            for (a in bmpHeader.palette[0].indices) {
//	memcpy( bmpHeader.palette, buf_p, sizeof( bmpHeader.palette ) );
                palette[a] = buf_p.getChar() //TODO:should this be getByte()????
            }
        }
        if (bmpHeader.bitsPerPixel.toInt() == 8) {
            buf_p.position(buf_p.position() + 1024)
        }
        if (bmpHeader.id[0] != 'B' && bmpHeader.id[1] != 'M') {
            common.Error("LoadBMP: only Windows-style BMP files supported (%s)\n", name)
        }
        if (bmpHeader.fileSize != length.toLong()) {
            common.Error(
                "LoadBMP: header size does not match file size (%lu vs. %d) (%s)\n",
                bmpHeader.fileSize,
                length,
                name
            )
        }
        if (bmpHeader.compression != 0L) {
            common.Error("LoadBMP: only uncompressed BMP files supported (%s)\n", name)
        }
        if (bmpHeader.bitsPerPixel < 8) {
            common.Error("LoadBMP: monochrome and 4-bit BMP files not supported (%s)\n", name)
        }
        columns = bmpHeader.width.toInt()
        rows = bmpHeader.height.toInt()
        if (rows < 0) {
            rows = -rows
        }
        numPixels = columns * rows
        if (width != null) {
            width[0] = columns
        }
        if (height != null) {
            height[0] = rows
        }
        bmpRGBA = BufferUtils.createByteBuffer(numPixels * 4) //byte *)R_StaticAlloc( numPixels * 4 );
        row = rows - 1
        while (row >= 0) {
            pixbuf = bmpRGBA.duplicate()
            pixbuf.position(row * columns * 4)
            column = 0
            while (column < columns) {

                /*unsigned*/
                var red: Byte
                var green: Byte
                var blue: Byte
                var alpha: Byte
                var palIndex: Int
                /*unsigned*/
                var shortPixel: Short
                when (bmpHeader.bitsPerPixel.toInt()) {
                    8 -> {
                        palIndex = buf_p.get().toInt()
                        pixbuf.put(bmpHeader.palette[palIndex][2].code.toByte())
                        pixbuf.put(bmpHeader.palette[palIndex][1].code.toByte())
                        pixbuf.put(bmpHeader.palette[palIndex][0].code.toByte())
                        pixbuf.put(0xff.toByte())
                    }

                    16 -> {
                        shortPixel = pixbuf.getShort()
                        pixbuf.getShort() // += 2;
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
        //	fileSystem->FreeFile( buffer );
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
        val pix: ByteBuffer
        val xmax: Int
        val ymax: Int
        if (pic[0] == null) {
            fileSystem.ReadFile(filename, null, timestamp)
            return  // just getting timestamp
        }
        pic[0] = null
        palette!![0] = null

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
        out = ByteBuffer.allocate((ymax + 1) * (xmax + 1)) //(byte *)R_StaticAlloc( (ymax+1) * (xmax+1) );
        pic[0] = out
        pix = out
        if (palette != null) {
            palette[0] = ByteBuffer.allocate(768) //(byte *)R_StaticAlloc(768);
            //		memcpy (*palette, (byte *)pcx + len - 768, 768);
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
                while (runLength-- > 0) {
                    pix.put(x++, dataByte)
                }
            }
            y++
            pix.position(pix.position() + xmax + 1)
        }
        if (raw[0]!!.position() > len) //TODO: is this even possible?
        {
            common.Printf("PCX file %s was malformed", filename)
            //		R_StaticFree (*pic);
            pic[0] = null
        }

//	fileSystem->FreeFile( pcx );
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
        pic = BufferUtils.createByteBuffer(4 * c) //(byte *)R_StaticAlloc(4 * c );
        i = 0
        while (i < c) {
            val offset = i * 4
            p = pic8[0]!!.get(i).toInt()
            pic.put(offset, palette[0]!!.get(p * 3))
            pic.put(offset + 1, palette[0]!!.get(p * 3 + 1))
            pic.put(offset + 2, palette[0]!!.get(p * 3 + 2))
            pic.put(offset + 3, 255.toByte())
            i++
        }

//	R_StaticFree( pic8 );
//	R_StaticFree( palette );
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
        targa_header.colormap_index = LittleShort(buf_p.getShort()) //	buf_p += 2;
        targa_header.colormap_length = LittleShort(buf_p.getShort()) //	buf_p += 2;
        targa_header.colormap_size = buf_p.get()
        targa_header.x_origin = LittleShort(buf_p.getShort()) //	buf_p += 2;
        targa_header.y_origin = LittleShort(buf_p.getShort()) //	buf_p += 2;
        targa_header.width = LittleShort(buf_p.getShort()) //	buf_p += 2;
        targa_header.height = LittleShort(buf_p.getShort()) //	buf_p += 2;
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
        if (width != null) {
            width[0] = columns
        }
        if (height != null) {
            height[0] = rows
        }
        targa_rgba = BufferUtils.createByteBuffer(numPixels * 4) // (byte *)R_StaticAlloc(numPixels*4);
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
                    var  /*unsigned char*/red: Byte
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
            var  /*unsigned char*/red: Byte
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

//	fileSystem->FreeFile( buffer );
        return targa_rgba
    }

    /*
     =============
     LoadJPG
     =============
     */
    private fun LoadJPG(filename: String, width: IntArray?, height: IntArray?, timestamp: LongArray?): ByteBuffer? {
        /* This struct contains the JPEG decompression parameters and pointers to
         * working space (which is allocated as needed by the JPEG library).
         */
//  struct jpeg_decompress_struct cinfo;
        /* We use our private extension JPEG error handler.
         * Note that this struct must live as long as the main JPEG parameter
         * struct, to avoid dangling-pointer problems.
         */
        /* This struct represents a JPEG error handler.  It is declared separately
         * because applications often want to supply a specialized error handler
         * (see the second half of this file for an example).  But here we just
         * take the easy way out and use the standard error handler, which will
         * print a message on stderr and call exit() if compression fails.
         * Note that this struct must live as long as the main JPEG parameter
         * struct, to avoid dangling-pointer problems.
         */
//  struct jpeg_error_mgr jerr;
        /* More stuff */
        val  /*JSAMPARRAY*/buffer: BufferedImage // Output row buffer
        //  int row_stride;		// physical row width in output buffer
//  unsigned char *out;
        var fbuffer: ByteBuffer
        //  byte  *bbuf;

        /* In this example we want to open the input file before doing anything else,
         * so that the setjmp() error recovery below can assume the file is open.
         * VERY IMPORTANT: use "b" option to fopen() if you are on a machine that
         * requires it in order to read binary files.
         */
        // JDC: because fill_input_buffer() blindly copies INPUT_BUF_SIZE bytes,
        // we need to make sure the file buffer is padded or it may crash
//        if ( pic ) {
//            *pic = NULL;		// until proven otherwise
//        }
        run({
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
            fbuffer = BufferUtils.createByteBuffer(len + 4096) //(byte *)Mem_ClearedAlloc( len + 4096 );
            f.Read(fbuffer /*, len*/)
            fileSystem.CloseFile(f)
        })
        try {
            buffer = ImageIO.read(ByteArrayInputStream(fbuffer.array()))
        } catch (ex: IOException) {
            Logger.getLogger(Image_files::class.java.getName()).log(Level.SEVERE, null, ex)
            common.Error("Failed to load JPEG ", filename)
            return null
        }

//
//  /* Step 1: allocate and initialize JPEG decompression object */
//
//  /* We have to set up the error handler first, in case the initialization
//   * step fails.  (Unlikely, but it could happen if you are out of memory.)
//   * This routine fills in the contents of struct jerr, and returns jerr's
//   * address which we place into the link field in cinfo.
//   */
////  cinfo.err = jpeg_std_error(&jerr);
//
//  /* Now we can initialize the JPEG decompression object. */
////  jpeg_create_decompress(&cinfo);
//
//  /* Step 2: specify data source (eg, a file) */
//
////  jpeg_stdio_src(&cinfo, fbuffer);
//
//  /* Step 3: read file parameters with jpeg_read_header() */
//
////  (void) jpeg_read_header(&cinfo, true );
//  /* We can ignore the return value from jpeg_read_header since
//   *   (a) suspension is not possible with the stdio data source, and
//   *   (b) we passed TRUE to reject a tables-only JPEG file as an error.
//   * See libjpeg.doc for more info.
//   */
//
//  /* Step 4: set parameters for decompression */
//
//  /* In this example, we don't need to change any of the defaults set by
//   * jpeg_read_header(), so we do nothing here.
//   */
//
//  /* Step 5: Start decompressor */
//
////  (void) jpeg_start_decompress(&cinfo);
//  /* We can ignore the return value since suspension is not possible
//   * with the stdio data source.
//   */
//
//  /* We may need to do some setup of our own at this point before reading
//   * the data.  After jpeg_start_decompress() we have the correct scaled
//   * output image dimensions available, as well as the output colormap
//   * if we asked for color quantization.
//   * In this example, we need to make an output work buffer of the right size.
//   */
//  /* JSAMPLEs per row in output buffer */
//  row_stride = cinfo.output_width * cinfo.output_components;
//
//  if (cinfo.output_components!=4) {
//		common.DWarning( "JPG %s is unsupported color depth (%d)",
//			filename, cinfo.output_components);
//  }
//  out = (byte *)R_StaticAlloc(cinfo.output_width*cinfo.output_height*4);
//
        val out: ByteArray = (buffer.raster.getDataBuffer() as DataBufferByte).getData()
        width!![0] = buffer.width //cinfo.output_width;
        height!![0] = buffer.height //cinfo.output_height;
        return ByteBuffer.wrap(out)
        //
//  /* Step 6: while (scan lines remain to be read) */
//  /*           jpeg_read_scanlines(...); */
//
//  /* Here we use the library's state variable cinfo.output_scanline as the
//   * loop counter, so that we don't have to keep track ourselves.
//   */
//  while (cinfo.output_scanline < cinfo.output_height) {
//    /* jpeg_read_scanlines expects an array of pointers to scanlines.
//     * Here the array is only one element long, but you could ask for
//     * more than one scanline at a time if that's more convenient.
//     */
//	bbuf = ((out+(row_stride*cinfo.output_scanline)));
//	buffer = &bbuf;
//    (void) jpeg_read_scanlines(&cinfo, buffer, 1);
//  }
//
//  // clear all the alphas to 255
//        {//TODO:should this be enabled?
//	  int	i, j;
//		byte	*buf;
//
//		buf = *pic;
//
//	  j = cinfo.output_width * cinfo.output_height * 4;
//	  for ( i = 3 ; i < j ; i+=4 ) {
//		  buf[i] = 255;
//	  }
//        }
//
//  /* Step 7: Finish decompression */
//
//  (void) jpeg_finish_decompress(&cinfo);
//  /* We can ignore the return value since suspension is not possible
//   * with the stdio data source.
//   */
//
//  /* Step 8: Release JPEG decompression object */
//
//  /* This is an important step since it will release a good deal of memory. */
//  jpeg_destroy_decompress(&cinfo);
//
//  /* After finish_decompress, we can close the input file.
//   * Here we postpone it until after no more JPEG errors are possible,
//   * so as to simplify the setjmp error logic above.  (Actually, I don't
//   * think that jpeg_destroy can do an error exit, but why assume anything...)
//   */
//  Mem_Free( fbuffer );
//
//  /* At this point you may want to check to see whether any corrupt-data
//   * warnings occurred (test whether jerr.pub.num_warnings is nonzero).
//   */
//
//  /* And we're done! */
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
            timestamp[0] = -0x1
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
                pic.clear() //R_StaticFree( *pic );
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
                resampledBuffer = Image_process.R_ResampleTexture(pic, w, h, scaled_width, scaled_height)
                pic.clear() //R_StaticFree( *pic );
                pic.put(resampledBuffer)
                width[0] = scaled_width
                height[0] = scaled_height
            }
        }
        return pic
    }

    /*
     ========================================================================================================

     TARGA LOADING

     ========================================================================================================
     */
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
        outSize: IntArray?,  /*ID_TIME_T */
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
                    pics[j] = null // R_StaticFree(pics[j]);
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
     ========================================================================================================

     JPG LOADING

     Interfaces with the huge libjpeg
     EDIT: not anymore    
     ========================================================================================================
     */
    @Deprecated("")
    fun R_LoadImage(
        cname: String?,
        pic: ByteArray?,
        width: IntArray?,
        height: IntArray?,
        timestamp: LongArray?,
        makePowerOf2: Boolean
    ) {
        throw TODO_Exception()
    }

    /*
     ========================================================================

     PCX files are used for 8 bit images

     ========================================================================
     */
    private class pcx_t(byteBuffer: ByteBuffer?) {
        var bits_per_pixel: Char = 0.toChar()

        /*unsigned*/
        var bytes_per_line: Short = 0
        var color_planes: Char = 0.toChar()

        //        unsigned char data;			// unbounded
        var dataPosition: Int = 0 // unbounded
        var encoding: Char = 0.toChar()
        var filler: CharArray = CharArray(58)

        /*unsigned*/
        var hres: Short = 0
        var vres: Short = 0
        var manufacturer: Char = 0.toChar()

        /*unsigned*/
        var palette: CharArray = CharArray(48)

        /*unsigned*/
        var palette_type: Short = 0
        var reserved: Char = 0.toChar()
        var version: Char = 0.toChar()

        /*unsigned*/
        var xmin: Short = 0
        var ymin: Short = 0
        var xmax: Short = 0
        var ymax: Short = 0

        init {
            throw TODO_Exception()
        }
    }

    /*
     ========================================================================

     TGA files are used for 24/32 bit images

     ========================================================================
     */
    private class TargaHeader {
        /*unsigned*/
        var colormap_index: Short = 0
        var colormap_length: Short = 0

        /*unsigned*/
        var colormap_size: Byte = 0

        /*unsigned*/
        var id_length: Byte = 0
        var colormap_type: Byte = 0
        var image_type: Byte = 0

        /*unsigned*/
        var pixel_size: Byte = 0
        var attributes: Byte = 0

        /*unsigned*/
        var x_origin: Short = 0
        var y_origin: Short = 0
        var width: Short = 0
        var height: Short = 0
    }

    private class BMPHeader_t {
        /*unsigned*/
        var bitmapDataOffset: Long = 0

        /*unsigned*/
        var bitmapDataSize: Long = 0

        /*unsigned*/
        var bitmapHeaderSize: Long = 0

        /*unsigned*/
        var bitsPerPixel: Short = 0

        /*unsigned*/
        var colors: Long = 0

        /*unsigned*/
        var compression: Long = 0

        /*unsigned*/
        var fileSize: Long = 0

        /*unsigned*/
        var hRes: Long = 0

        /*unsigned*/
        var height: Long = 0
        var id: CharArray = CharArray(2)

        /*unsigned*/
        var importantColors: Long = 0

        /*unsigned*/
        var palette: Array<CharArray> = Array(256, { CharArray(4) })

        /*unsigned*/
        var planes: Short = 0

        /*unsigned*/
        var reserved0: Long = 0

        /*unsigned*/
        var vRes: Long = 0

        /*unsigned*/
        var width: Long = 0
    }
}

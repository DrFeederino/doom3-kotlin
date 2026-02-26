package neo.Tools.Compilers.RoqVQ

import neo.TempDump.TODO_Exception
import neo.Tools.Compilers.RoqVQ.Codec.codec
import neo.Tools.Compilers.RoqVQ.RoqParam.roqParam
import neo.framework.CmdSystem.cmdFunction_t
import neo.framework.Common
import neo.framework.FileSystem_h
import neo.framework.File_h.idFile
import neo.framework.Session
import neo.idlib.CmdArgs
import neo.idlib.Text.Str.idStr
import neo.idlib.idException
import neo.sys.win_shared
import java.nio.ByteBuffer

object Roq {
    var theRoQ // current roq
            : roq = roq()

    class roq     //0;
    //0;
    {
        private var RoQFile: idFile? = null
        private val codes: ByteArray = ByteArray(4096)
        private val currentFile: idStr = idStr()
        private var dataStuff = false
        private var encoder: codec? = null
        private var image: NSBitmapImageRep? = null
        private val lastFrame = false
        private var numQuadCels = 0
        private var numberOfFrames = 0
        private var paramFile: roqParam = roqParam()
        private var previousSize = 0
        private var quietMode = false
        private val roqOutfile: idStr = idStr()

        fun WriteLossless() {
            throw TODO_Exception()
            //
//            int/*word*/ direct;
//            int/*uint*/ directdw;
//
//            if (!dataStuff) {
//                InitRoQPatterns();
//                dataStuff = true;
//            }
//            direct = RoQ_QUAD_JPEG;
//            Write16Word(direct, RoQFile);
//
//            /* This struct contains the JPEG compression parameters and pointers to
//             * working space (which is allocated as needed by the JPEG library).
//             * It is possible to have several such structures, representing multiple
//             * compression/decompression processes, in existence at once.  We refer
//             * to any one struct (and its associated working data) as a "JPEG object".
//             */
//            jpeg_compress_struct cinfo;
//            /* This struct represents a JPEG error handler.  It is declared separately
//             * because applications often want to supply a specialized error handler
//             * (see the second half of this file for an example).  But here we just
//             * take the easy way out and use the standard error handler, which will
//             * print a message on stderr and call exit() if compression fails.
//             * Note that this struct must live as long as the main JPEG parameter
//             * struct, to avoid dangling-pointer problems.
//             */
//            jpeg_error_mgr jerr;
//            /* More stuff */
//            JSAMPROW[] row_pointer = new JSAMPROW[1];	/* pointer to JSAMPLE row[s] */
//
//            int row_stride;		/* physical row width in image buffer */
//
//            ByteBuffer out;
//
//            /* Step 1: allocate and initialize JPEG compression object */
//
//            /* We have to set up the error handler first, in case the initialization
//             * step fails.  (Unlikely, but it could happen if you are out of memory.)
//             * This routine fills in the contents of struct jerr, and returns jerr's
//             * address which we place into the link field in cinfo.
//             */
//            cinfo.err = jpeg_std_error(jerr);
//            /* Now we can initialize the JPEG compression object. */
//            jpeg_create_compress(cinfo);
//
//            /* Step 2: specify data destination (eg, a file) */
//            /* Note: steps 2 and 3 can be done in either order. */
//
//            /* Here we use the library-supplied code to send compressed data to a
//             * stdio stream.  You can also write your own code to do something else.
//             * VERY IMPORTANT: use "b" option to fopen() if you are on a machine that
//             * requires it in order to write binary files.
//             */
//            out = ByteBuffer.allocate(image.pixelsWide() * image.pixelsHigh() * 4);// Mem_Alloc(image.pixelsWide() * image.pixelsHigh() * 4);
//            JPEGDest(cinfo, out, image.pixelsWide() * image.pixelsHigh() * 4);
//
//            /* Step 3: set parameters for compression */
//
//            /* First we supply a description of the input image.
//             * Four fields of the cinfo struct must be filled in:
//             */
//            cinfo.image_width = image.pixelsWide(); 	/* image width and height, in pixels */
//
//            cinfo.image_height = image.pixelsHigh();
//            cinfo.input_components = 4;		/* # of color components per pixel */
//
//            cinfo.in_color_space = JCS_RGB; 	/* colorspace of input image */
//            /* Now use the library's routine to set default compression parameters.
//             * (You must set at least cinfo.in_color_space before calling this,
//             * since the defaults depend on the source color space.)
//             */
//
//            jpeg_set_defaults(cinfo);
//            /* Now you can set any non-default parameters you wish to.
//             * Here we just illustrate the use of quality (quantization table) scaling:
//             */
//            jpeg_set_quality(cinfo, paramFile.JpegQuality(), true /* limit to baseline-JPEG values */);
//
//            /* Step 4: Start compressor */
//
//            /* true ensures that we will write a complete interchange-JPEG file.
//             * Pass true unless you are very sure of what you're doing.
//             */
//            JPEGStartCompress(cinfo, true);
//
//            /* Step 5: while (scan lines remain to be written) */
//            /*           jpeg_write_scanlines(...); */
//
//            /* Here we use the library's state variable cinfo.next_scanline as the
//             * loop counter, so that we don't have to keep track ourselves.
//             * To keep things simple, we pass one scanline per call; you can pass
//             * more if you wish, though.
//             */
//            row_stride = image.pixelsWide() * 4;	/* JSAMPLEs per row in image_buffer */
//
//            byte[] pixbuf = image.bitmapData();
//            while (cinfo.next_scanline < cinfo.image_height) {
//                /* jpeg_write_scanlines expects an array of pointers to scanlines.
//                 * Here the array is only one element long, but you could pass
//                 * more than one scanline at a time if that's more convenient.
//                 */
//                row_pointer[0] = pixbuf[((cinfo.image_height - 1) * row_stride) - cinfo.next_scanline * row_stride];
//                /*(void)*/ JPEGWriteScanlines(cinfo, row_pointer, 1);
//            }
//
//            /* Step 6: Finish compression */
//            jpeg_finish_compress(cinfo);
//            /* After finish_compress, we can close the output file. */
//
//            directdw = hackSize;
//            common.Printf("writeLossless: writing %d bytes to RoQ_QUAD_JPEG\n", hackSize);
//            Write32Word(directdw, RoQFile);
//            direct = 0;		// flags
//            Write16Word(direct, RoQFile);
//
//            RoQFile.Write(out, hackSize);
//            out = null;//Mem_Free(out);
//
//            /* Step 7: release JPEG compression object */
//
//            /* This is an important step since it will release a good deal of memory. */
//            jpeg_destroy_compress(cinfo);
//
//            /* And we're done! */
//            encoder.SetPreviousImage("first frame", image);
        }

        //
        // load a frame, create a window (if neccesary) and display the frame
        //
        fun LoadAndDisplayImage(filename: String) {
//	if (image) delete image;
            Common.common.Printf("loadAndDisplayImage: %s\n", filename)
            currentFile.set(filename)
            image = NSBitmapImageRep(filename)
            numQuadCels =
                (image!!.pixelsWide() and 0xfff0) * (image!!.pixelsHigh() and 0xfff0) / (MINSIZE * MINSIZE)
            numQuadCels += numQuadCels / 4 + numQuadCels / 16

//	if (paramFile->deltaFrames] == true && cleared == false && [image isPlanar] == false) {
//		cleared = true;
//		imageData = [image data];
//		memset( imageData, 0, image->pixelsWide()*image->pixelsHigh()*[image samplesPerPixel]);
//	}
            if (!quietMode) {
                Common.common.Printf("loadAndDisplayImage: %dx%d\n", image!!.pixelsWide(), image!!.pixelsHigh())
            }
        }

        fun CloseRoQFile(which: Boolean) {
            Common.common.Printf("closeRoQFile: closing RoQ file\n")
            FileSystem_h.fileSystem.CloseFile(RoQFile!!)
        }

        fun InitRoQFile(RoQFilename: String) {
            var   /*word*/i: Int
            if (0 == finit) {
                finit++
                Common.common.Printf("initRoQFile: %s\n", RoQFilename)
                RoQFile = FileSystem_h.fileSystem.OpenFileWrite(RoQFilename)
                //		chmod(RoQFilename, S_IREAD|S_IWRITE|S_ISUID|S_ISGID|0070|0007 );
                if (null == RoQFile) {
                    Common.common.Error("Unable to open output file %s.\n", RoQFilename)
                    return
                }
                i = RoQ_ID
                Write16Word(i, RoQFile!!)
                i = 0xffff
                Write16Word(i, RoQFile!!)
                Write16Word(i, RoQFile!!)

                // to retain exact file format write out 32 for new roq's
                // on loading this will be noted and converted to 1000 / 30
                // as with any new sound dump avi demos we need to playback
                // at the speed the sound engine dumps the audio
                i = 30 // framerate
                Write16Word(i, RoQFile!!)
            }
            roqOutfile.set(RoQFilename)
        }

        fun InitRoQPatterns() {
            val   /*uint*/j: Int
            var   /*word*/direct: Int
            direct = RoQ_QUAD_INFO
            Write16Word(direct, RoQFile!!)
            j = 8
            Write32Word(j, RoQFile!!)
            Common.common.Printf("initRoQPatterns: outputting %d bytes to RoQ_INFO\n", j)
            direct = if (image!!.hasAlpha()) 1 else 0
            if (ParamNoAlpha() == true) {
                direct = 0
            }
            Write16Word(direct, RoQFile!!)
            direct = image!!.pixelsWide()
            Write16Word(direct, RoQFile!!)
            direct = image!!.pixelsHigh()
            Write16Word(direct, RoQFile!!)
            direct = 8
            Write16Word(direct, RoQFile!!)
            direct = 4
            Write16Word(direct, RoQFile!!)
        }

        fun EncodeStream(paramInputFile: String) {
            var onFrame: Int
            var f0: String
            var f1: String
            var f2 = ""
            var morestuff: Int
            onFrame = 1
            encoder = codec()
            paramFile = roqParam()
            paramFile.numInputFiles = 0
            paramFile.InitFromFile(paramInputFile)
            if (paramFile.NumberOfFrames() == 0) {
                return
            }
            InitRoQFile(paramFile.outputFilename.toString())
            numberOfFrames = paramFile.NumberOfFrames()
            if (paramFile.NoAlpha() == true) {
                Common.common.Printf("encodeStream: eluding alpha\n")
            }
            f0 = ""
            f1 = paramFile.GetNextImageFilename()
            if (paramFile.MoreFrames() == true) {
                f2 = paramFile.GetNextImageFilename()
            }
            morestuff = numberOfFrames
            while (morestuff != 0) {
                LoadAndDisplayImage(f1)
                if (onFrame == 1) {
                    encoder!!.SparseEncode()
                    //			WriteLossless();
                } else {
                    if (f0 == f1 && f1 != f2) {
                        WriteHangFrame()
                    } else {
                        encoder!!.SparseEncode()
                    }
                }
                onFrame++
                f0 = f1
                f1 = f2
                if (paramFile.MoreFrames() == true) {
                    f2 = paramFile.GetNextImageFilename()
                }
                morestuff--
                Session.session.UpdateScreen()
            }

//	if (numberOfFrames != 1) {
//		if (image->hasAlpha() && paramFile->NoAlpha()==false) {
//			lastFrame = true;
//			encoder->SparseEncode();
//		} else {
//			WriteLossless();
//		}
//	}
            CloseRoQFile()
        }

        fun EncodeQuietly(which: Boolean) {
            quietMode = which
        }

        //
        //        public void WritePuzzleFrame(quadcel pquad);
        //
        fun IsQuiet(): Boolean {
            return quietMode
        }

        fun IsLastFrame(): Boolean {
            return lastFrame
        }

        fun CurrentImage(): NSBitmapImageRep? {
            return image
        }

        fun MarkQuadx(xat: Int, yat: Int, size: Int, cerror: Float, choice: Int) {}
        fun WriteFrame(pquad: Array<quadcel>) {
            var   /*word*/action: Int
            var direct: Int
            var onCCC: Int
            var onAction: Int
            var i: Int
            var code: Int
            var   /*uint*/j: Int
            var cccList: ByteArray
            var use2: BooleanArray
            var use4: BooleanArray
            var dx: Int
            var dy: Int
            val dxMean: Int
            val dyMean: Int
            val dimension: Int
            val index2 = IntArray(256)
            val index4 = IntArray(256)
            cccList = ByteArray(numQuadCels * 8) // Mem_Alloc(numQuadCels * 8);					// maximum length
            use2 = BooleanArray(256) // Mem_Alloc(256);
            use4 = BooleanArray(256) // Mem_Alloc(256);
            i = 0
            while (i < 256) {
                use2[i] = false
                use4[i] = false
                i++
            }
            action = 0
            onAction = 0
            j = onAction
            onCCC = 2 // onAction going to go at zero
            dxMean = encoder!!.MotMeanX()
            dyMean = encoder!!.MotMeanY()
            dimension = if (image!!.hasAlpha()) {
                10
            } else {
                6
            }
            i = 0
            while (i < numQuadCels) {
                if (pquad[i].size.toInt() != 0 && pquad[i].size < 16) {
                    when (pquad[i].status) {
                        SLD -> {
                            use4[pquad[i].patten[0]] = true
                            use2[codes[dimension * 256 + pquad[i].patten[0] * 4 + 0].toInt()] = true
                            use2[codes[dimension * 256 + pquad[i].patten[0] * 4 + 1].toInt()] = true
                            use2[codes[dimension * 256 + pquad[i].patten[0] * 4 + 2].toInt()] = true
                            use2[codes[dimension * 256 + pquad[i].patten[0] * 4 + 3].toInt()] = true
                        }

                        PAT -> {
                            use4[pquad[i].patten[0]] = true
                            use2[codes[dimension * 256 + pquad[i].patten[0] * 4 + 0].toInt()] = true
                            use2[codes[dimension * 256 + pquad[i].patten[0] * 4 + 1].toInt()] = true
                            use2[codes[dimension * 256 + pquad[i].patten[0] * 4 + 2].toInt()] = true
                            use2[codes[dimension * 256 + pquad[i].patten[0] * 4 + 3].toInt()] = true
                        }

                        CCC -> {
                            use2[pquad[i].patten[1]] = true
                            use2[pquad[i].patten[2]] = true
                            use2[pquad[i].patten[3]] = true
                            use2[pquad[i].patten[4]] = true
                        }
                    }
                }
                i++
            }
            if (!dataStuff) {
                dataStuff = true
                InitRoQPatterns()
                i = if (image!!.hasAlpha()) {
                    3584
                } else {
                    2560
                }
                WriteCodeBookToStream(codes, i, 0)
                i = 0
                while (i < 256) {
                    index2[i] = i
                    index4[i] = i
                    i++
                }
            } else {
                j = 0
                i = 0
                while (i < 256) {
                    if (use2[i]) {
                        index2[i] = j
                        dx = 0
                        while (dx < dimension) {
                            cccList[j * dimension + dx] = codes[i * dimension + dx]
                            dx++
                        }
                        j++
                    }
                    i++
                }
                code = j * dimension
                direct = j
                Common.common.Printf("writeFrame: really used %d 2x2 cels\n", j)
                j = 0
                i = 0
                while (i < 256) {
                    if (use4[i]) {
                        index4[i] = j
                        dx = 0
                        while (dx < 4) {
                            cccList[j * 4 + code + dx] = index2[codes[i * 4 + dimension * 256 + dx].toInt()].toByte()
                            dx++
                        }
                        j++
                    }
                    i++
                }
                code += j * 4
                direct = (direct shl 8) + j
                Common.common.Printf("writeFrame: really used %d 4x4 cels\n", j)
                i = if (image!!.hasAlpha()) {
                    3584
                } else {
                    2560
                }
                if (code == i || j == 256) {
                    WriteCodeBookToStream(codes, i, 0)
                } else {
                    WriteCodeBookToStream(cccList, code, direct)
                }
            }
            action = 0
            onAction = 0
            j = onAction
            i = 0
            while (i < numQuadCels) {
                if (pquad[i].size.toInt() != 0 && pquad[i].size < 16) {
                    code = -1
                    when (pquad[i].status) {
                        DEP -> code = 3
                        SLD -> {
                            code = 2
                            cccList[onCCC++] = index4[pquad[i].patten[0]].toByte()
                        }

                        MOT -> code = 0
                        FCC -> {
                            code = 1
                            dx = (pquad[i].domain shr 8) - 128 - dxMean + 8
                            dy = (pquad[i].domain and 0xff) - 128 - dyMean + 8
                            if (dx > 15 || dx < 0 || dy > 15 || dy < 0) {
                                Common.common.Error(
                                    "writeFrame: FCC error %d,%d mean %d,%d at %d,%d,%d rmse %f\n",
                                    dx,
                                    dy,
                                    dxMean,
                                    dyMean,
                                    pquad[i].xat,
                                    pquad[i].yat,
                                    pquad[i].size,
                                    pquad[i].snr[FCC]
                                )
                            }
                            cccList[onCCC++] = ((dx shl 4) + dy).toByte()
                        }

                        PAT -> {
                            code = 2
                            cccList[onCCC++] = index4[pquad[i].patten[0]].toByte()
                        }

                        CCC -> {
                            code = 3
                            cccList[onCCC++] = index2[pquad[i].patten[1]].toByte()
                            cccList[onCCC++] = index2[pquad[i].patten[2]].toByte()
                            cccList[onCCC++] = index2[pquad[i].patten[3]].toByte()
                            cccList[onCCC++] = index2[pquad[i].patten[4]].toByte()
                        }

                        DEAD -> Common.common.Error("dead cels in picture\n")
                    }
                    if (code == -1) {
                        Common.common.Error("writeFrame: an error occurred writing the frame\n")
                    }
                    action = action shl 2 or code
                    j++
                    if (j == 8) {
                        j = 0
                        cccList[onAction + 0] = (action and 0xff).toByte()
                        cccList[onAction + 1] = (action shr 8 and 0xff).toByte()
                        onAction = onCCC
                        onCCC += 2
                    }
                }
                i++
            }
            if (j != 0) {
                action = action shl (8 - j) * 2
                cccList[onAction + 0] = (action and 0xff).toByte()
                cccList[onAction + 1] = (action shr 8 and 0xff).toByte()
            }
            direct = RoQ_QUAD_VQ
            Write16Word(direct, RoQFile!!)
            j = onCCC
            Write32Word(j, RoQFile!!)
            direct = dyMean
            direct = direct and 0xff
            direct += dxMean shl 8 // flags
            Write16Word(direct, RoQFile!!)
            Common.common.Printf("writeFrame: outputting %d bytes to RoQ_QUAD_VQ\n", j)
            previousSize = j
            RoQFile!!.Write(ByteBuffer.wrap(cccList), onCCC)
        }

        fun WriteCodeBook(codebook: ByteArray) {
//	memcpy( codes, codebook, 4096 );
            System.arraycopy(codebook, 0, codes, 0, 4096)
        }

        fun WriteCodeBookToStream(codebook: ByteArray, csize: Int,    /*word*/cflags: Int) {
            val   /*uint*/j: Int
            var   /*word*/direct: Int
            if (0 == csize) {
                Common.common.Printf("writeCodeBook: false VQ DATA!!!!\n")
                return
            }
            direct = RoQ_QUAD_CODEBOOK
            Write16Word(direct, RoQFile!!)
            j = csize
            Write32Word(j, RoQFile!!)
            Common.common.Printf("writeCodeBook: outputting %d bytes to RoQ_QUAD_CODEBOOK\n", j)
            direct = cflags
            Write16Word(direct, RoQFile!!)
            RoQFile!!.Write(ByteBuffer.wrap(codebook), j)
        }

        fun PreviousFrameSize(): Int {
            return previousSize
        }

        fun MakingVideo(): Boolean {
            return true //paramFile->timecode];
        }

        fun ParamNoAlpha(): Boolean {
            return paramFile.NoAlpha()
        }

        fun SearchType(): Boolean {
            return paramFile.SearchType()
        }

        fun HasSound(): Boolean {
            return paramFile.HasSound()
        }

        fun CurrentFilename(): String {
            return currentFile.toString()
        }

        fun NormalFrameSize(): Int {
            return paramFile.NormalFrameSize()
        }

        fun FirstFrameSize(): Int {
            return paramFile.FirstFrameSize()
        }

        fun Scaleable(): Boolean {
            return paramFile.IsScaleable()
        }

        fun WriteHangFrame() {
            val   /*uint*/j: Int
            var   /*word*/direct: Int
            Common.common.Printf("*******************************************************************\n")
            direct = RoQ_QUAD_HANG
            Write16Word(direct, RoQFile!!)
            j = 0
            Write32Word(j, RoQFile!!)
            direct = 0
            Write16Word(direct, RoQFile!!)
        }

        fun NumberOfFrames(): Int {
            return numberOfFrames
        }

        /*
         * Initialize destination --- called by jpeg_start_compress
         * before any data is actually written.
         */
        private fun Write16Word(   /*word*/aWord: Int, stream: idFile) {
//            byte a, b;
//
//            a = (byte) (aWord & 0xff);
//            b = (byte) (aWord >> 8);
//
//            stream.Write(a, 1);
//            stream.Write(b, 1);
            stream.WriteInt(aWord)
        }

        private fun Write32Word(   /*unsigned int*/aWord: Int, stream: idFile) {
//            byte a, b, c, d;
//
//            a = (byte) (aWord & 0xff);
//            b = (byte) ((aWord >> 8) & 0xff);
//            c = (byte) ((aWord >> 16) & 0xff);
//            d = (byte) ((aWord >> 24) & 0xff);
//
//            stream.Write(a, 1);
//            stream.Write(b, 1);
//            stream.Write(c, 1);
//            stream.Write(d, 1);
//
            val buffer = ByteBuffer.allocate(8)
            buffer.putInt(aWord)
            stream.Write(buffer)
        }

        private fun SizeFile(ftoSize: idFile): Int {
            return ftoSize.Length()
        }

        private fun CloseRoQFile() {
            Common.common.Printf("closeRoQFile: closing RoQ file\n")
            FileSystem_h.fileSystem.CloseFile(RoQFile!!)
        }

        /*
         * Compression initialization.
         * Before calling this, all parameters and a data destination must be set up.
         *
         * We require a write_all_tables parameter as a failsafe check when writing
         * multiple datastreams from the same compression object.  Since prior runs
         * will have left all the tables marked sent_table=true, a subsequent run
         * would emit an abbreviated stream (no tables) by default.  This may be what
         * is wanted, but for safety's sake it should not be the default behavior:
         * programmers should have to make a deliberate choice to emit abbreviated
         * images.  Therefore the documentation and examples should encourage people
         * to pass write_all_tables=true; then it will take active thought to do the
         * wrong thing.
         */
        private fun JPEGStartCompress(cinfo: j_compress_ptr, write_all_tables: Boolean) {
            throw TODO_Exception()
            //            if (cinfo.global_state != CSTATE_START) {
//                ERREXIT1(cinfo, JERR_BAD_STATE, cinfo.global_state);
//            }
//
//            if (write_all_tables) {
//                jpeg_suppress_tables(cinfo, false);	/* mark all tables to be written */
//
//            }
//            /* (Re)initialize error mgr and destination modules */
//            (cinfo.err.reset_error_mgr) ((j_common_ptr) cinfo);
//            (cinfo.dest.init_destination) (cinfo);
//            /* Perform master selection of active modules */
//            jinit_compress_master(cinfo);
//            /* Set up for the first pass */
//            (cinfo.master.prepare_for_pass) (cinfo);
//            /* Ready for application to drive first pass through jpeg_write_scanlines
//             * or jpeg_write_raw_data.
//             */
//            cinfo.next_scanline = 0;
//            cinfo.global_state = (cinfo.raw_data_in ? CSTATE_RAW_OK : CSTATE_SCANNING);
        }

        /*
         * Write some scanlines of data to the JPEG compressor.
         *
         * The return value will be the number of lines actually written.
         * This should be less than the supplied num_lines only in case that
         * the data destination module has requested suspension of the compressor,
         * or if more than image_height scanlines are passed in.
         *
         * Note: we warn about excess calls to jpeg_write_scanlines() since
         * this likely signals an application programmer error.  However,
         * excess scanlines passed in the last valid call are *silently* ignored,
         * so that the application need not adjust num_lines for end-of-image
         * when using a multiple-scanline buffer.
         */
        private fun  /*JDIMENSION*/JPEGWriteScanlines(
            cinfo: j_compress_ptr,    /*JSAMPARRAY*/
            scanlines: CharArray,    /*JDIMENSION*/
            num_lines: Int
        ): Int {
            throw TODO_Exception()
            //            JDIMENSION row_ctr, rows_left;
//
//            if (cinfo.global_state != CSTATE_SCANNING) {
//                ERREXIT1(cinfo, JERR_BAD_STATE, cinfo.global_state);
//            }
//            if (cinfo.next_scanline >= cinfo.image_height) {
//                WARNMS(cinfo, JWRN_TOO_MUCH_DATA);
//            }
//
//            /* Call progress monitor hook if present */
//            if (cinfo.progress != null) {
//                cinfo.progress.pass_counter = (long) cinfo.next_scanline;
//                cinfo.progress.pass_limit = (long) cinfo.image_height;
//                (cinfo.progress.progress_monitor) ((j_common_ptr) cinfo);
//            }
//
//            /* Give master control module another chance if this is first call to
//             * jpeg_write_scanlines.  This lets output of the frame/scan headers be
//             * delayed so that application can write COM, etc, markers between
//             * jpeg_start_compress and jpeg_write_scanlines.
//             */
//            if (cinfo.master.call_pass_startup) {
//                (cinfo.master.pass_startup) (cinfo);
//            }
//
//            /* Ignore any extra scanlines at bottom of image. */
//            rows_left = cinfo.image_height - cinfo.next_scanline;
//            if (num_lines > rows_left) {
//                num_lines = rows_left;
//            }
//
//            row_ctr = 0;
//            cinfo.main.process_data(cinfo, scanlines, row_ctr, num_lines);
//            cinfo.next_scanline += row_ctr;
//            return row_ctr;
        }

        /*
         * Prepare for output to a stdio stream.
         * The caller must have already opened the stream, and is responsible
         * for closing it after finishing compression.
         */
        private fun JPEGDest(cinfo: j_compress_ptr, outfile: ByteBuffer, size: Int) {
            throw TODO_Exception()
            //            my_dest_ptr dest;
//
//            /* The destination object is made permanent so that multiple JPEG images
//             * can be written to the same file without re-executing jpeg_stdio_dest.
//             * This makes it dangerous to use this manager and a different destination
//             * manager serially with the same JPEG object, because their private object
//             * sizes may be different.  Caveat programmer.
//             */
//            if (cinfo.dest == null) {	/* first time for this JPEG object? */
//
//                cinfo.dest = (jpeg_destination_mgr) cinfo.mem.alloc_small((j_common_ptr) cinfo, JPOOL_PERMANENT, sizeof(my_destination_mgr));
//            }
//
//            dest = (my_dest_ptr) cinfo.dest;
//            dest.pub.init_destination = JPEGInitDestination;
//            dest.pub.empty_output_buffer = JPEGEmptyOutputBuffer;
//            dest.pub.term_destination = JPEGTermDestination;
//            dest.outfile = outfile;
//            dest.size = size;
        } //        private void JPEGSave(String[] filename, int quality, int image_width, int image_height, /*unsigned*/ char[] image_buffer);

        companion object {
            /*
         * Terminate destination --- called by jpeg_finish_compress
         * after all data has been written.  Usually needs to flush buffer.
         *
         * NB: *not* called by jpeg_abort or jpeg_destroy; surrounding
         * application must deal with any cleanup that should happen even
         * for error exit.
         */
            var hackSize = 0
            private var finit = 0
            private fun JPEGInitDestination(cinfo: j_compress_ptr) {
                throw TODO_Exception()
                //            my_dest_ptr dest = (my_dest_ptr) cinfo.dest;
//
//            dest.pub.next_output_byte = dest.outfile;
//            dest.pub.free_in_buffer = dest.size;
            }

            /*
         * Empty the output buffer --- called whenever buffer fills up.
         *
         * In typical applications, this should write the entire output buffer
         * (ignoring the current state of next_output_byte & free_in_buffer),
         * reset the pointer & count to the start of the buffer, and return true
         * indicating that the buffer has been dumped.
         *
         * In applications that need to be able to suspend compression due to output
         * overrun, a FALSE return indicates that the buffer cannot be emptied now.
         * In this situation, the compressor will return to its caller (possibly with
         * an indication that it has not accepted all the supplied scanlines).  The
         * application should resume compression after it has made more room in the
         * output buffer.  Note that there are substantial restrictions on the use of
         * suspension --- see the documentation.
         *
         * When suspending, the compressor will back up to a convenient restart point
         * (typically the start of the current MCU). next_output_byte & free_in_buffer
         * indicate where the restart point will be if the current call returns FALSE.
         * Data beyond this point will be regenerated after resumption, so do not
         * write it out when emptying the buffer externally.
         */
            private fun JPEGEmptyOutputBuffer(cinfo: j_compress_ptr): Boolean {
                return true
            }

            private fun JPEGTermDestination(cinfo: j_compress_ptr) {
                throw TODO_Exception()
                //            my_dest_ptr dest = (my_dest_ptr) cinfo.dest;
//            size_t datacount = dest.size - dest.pub.free_in_buffer;
//            hackSize = datacount;
            }
        }
    }

    class RoQFileEncode_f : cmdFunction_t() {
        @Throws(idException::class)
        override fun run(args: CmdArgs.idCmdArgs?) {
            if (args!!.Argc() != 2) {
                Common.common.Printf("Usage: roq <paramfile>\n")
                return
            }
            theRoQ = roq()
            val startMsec = win_shared.Sys_Milliseconds()
            theRoQ.EncodeStream(args.Argv(1))
            val stopMsec = win_shared.Sys_Milliseconds()
            Common.common.Printf("total encoding time: %d second\n", (stopMsec - startMsec) / 1000)
        }

        companion object {
            private val instance: cmdFunction_t = RoQFileEncode_f()
            fun getInstance(): cmdFunction_t {
                return instance
            }
        }
    }

    private class j_compress_ptr
}
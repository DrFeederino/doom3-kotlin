package neo.Renderer

import java.nio.ByteBuffer
import java.nio.ByteOrder

object tr_font {
    val BUILD_FREETYPE: Boolean = false
    var fdFile: ByteArray? = null

    //    static FT_Library ftLibrary = null;
    var fdOffset: Int = 0
    fun _FLOOR(x: Int): Int {
        return (x and -64)
    }

    fun _CEIL(x: Int): Int {
        return ((x + 63) and -64)
    }

    fun _TRUNC(x: Int): Int {
        return (x shr 6)
    }

    //
    //    /*
    //     ============
    //     R_GetGlyphInfo
    //     ============
    //     */
    //    public static void R_GetGlyphInfo(FT_GlyphSlot glyph, int[] left, int[] right, int[] width, int[] top, int[] bottom, int[] height, int[] pitch) {
    //
    //        left[0] = _FLOOR(glyph.metrics.horiBearingX);
    //        right[0] = _CEIL(glyph.metrics.horiBearingX + glyph.metrics.width);
    //        width[0] = _TRUNC(right[0] - left[0]);
    //
    //        top[0] = _CEIL(glyph.metrics.horiBearingY);
    //        bottom[0] = _FLOOR(glyph.metrics.horiBearingY - glyph.metrics.height);
    //        height[0] = _TRUNC(top[0] - bottom[0]);
    //        pitch[0] = (qtrue ? (width[0] + 3) & -4 : (width[0] + 7) >> 3);
    //    }
    //
    //    /*
    //     ============
    //     R_RenderGlyph
    //     ============
    //     */
    //    public static FT_Bitmap R_RenderGlyph(FT_GlyphSlot glyph, glyphInfo_t glyphOut) {
    //        FT_Bitmap bit2;
    //        int[] left = new int[1], right = new int[1], width = new int[1],
    //                top = new int[1], bottom = new int[1], height = new int[1], pitch = new int[1];
    //        int size;
    //
    //        R_GetGlyphInfo(glyph, left, right, width, top, bottom, height, pitch);
    //
    //        if (glyph.format == ft_glyph_format_outline) {
    //            size = pitchheight[0];
    //
    //            bit2 = Mem_Alloc(sizeof(FT_Bitmap));
    //
    //            bit2.width = width;
    //            bit2.rows = height;
    //            bit2.pitch = pitch;
    //            bit2.pixel_mode = ft_pixel_mode_grays;
    //            //bit2.pixel_mode = ft_pixel_mode_mono;
    //            bit2.buffer = Mem_Alloc(pitchheight[0]);
    //            bit2.num_grays = 256;
    //
    //            memset(bit2.buffer, 0, size);
    //
    //            FT_Outline_Translate(glyph.outline, -left[0], -bottom[0]);
    //
    //            FT_Outline_Get_Bitmap(ftLibrary, glyph.outline, bit2);
    //
    //            glyphOut.height = height[0];
    //            glyphOut.pitch = pitch[0];
    //            glyphOut.top = (glyph.metrics.horiBearingY >> 6) + 1;
    //            glyphOut.bottom = bottom[0];
    //
    //            return bit2;
    //        } else {
    //            common.Printf("Non-outline fonts are not supported\n");
    //        }
    //        return null;
    //    }
    //
    //    /*
    //     ============
    //     RE_ConstructGlyphInfo
    //     ============
    //     */
    //    private static glyphInfo_t glyph;
    //
    //    public static glyphInfo_t RE_ConstructGlyphInfo(String[] imageOut, int[] xOut, int[] yOut, int[] maxHeight, FT_Face face, final char c, boolean calcHeight) {
    //        int i;
    //        String src, dst;
    //        float scaled_width, scaled_height;
    //        FT_Bitmap bitmap = null;
    //
    ////        memset(glyph, 0, sizeof(glyphInfo_t));
    //        glyph = new glyphInfo_t();
    //        // make sure everything is here
    //        if (face != null) {
    //            FT_Load_Glyph(face, FT_Get_Char_Index(face, c), FT_LOAD_DEFAULT);
    //            bitmap = R_RenderGlyph(face.glyph, glyph);
    //            if (bitmap) {
    //                glyph.xSkip = (face.glyph.metrics.horiAdvance >> 6) + 1;
    //            } else {
    //                return glyph;
    //            }
    //
    //            if (glyph.height > maxHeight[0]) {
    //                maxHeight[0] = glyph.height;
    //            }
    //
    //            if (calcHeight) {
    //                Mem_Free(bitmap.buffer);
    //                Mem_Free(bitmap);
    //                return glyph;
    //            }
    //
    //            /*
    //             // need to convert to power of 2 sizes so we do not get 
    //             // any scaling from the gl upload
    //             for (scaled_width = 1 ; scaled_width < glyph.pitch ; scaled_width<<=1)
    //             ;
    //             for (scaled_height = 1 ; scaled_height < glyph.height ; scaled_height<<=1)
    //             ;
    //             */
    //            scaled_width = glyph.pitch;
    //            scaled_height = glyph.height;
    //
    //            // we need to make sure we fit
    //            if (xOut[0] + scaled_width + 1 >= 255) {
    //                if (yOut[0] + maxHeight[0] + 1 >= 255) {
    //                    yOut[0] = -1;
    //                    xOut[0] = -1;
    //                    Mem_Free(bitmap.buffer);
    //                    Mem_Free(bitmap);
    //                    return glyph;
    //                } else {
    //                    xOut[0] = 0;
    //                    yOut[0] += maxHeight[0] + 1;
    //                }
    //            } else if (yOut[0] + maxHeight[0] + 1 >= 255) {
    //                yOut[0] = -1;
    //                xOut[0] = -1;
    //                Mem_Free(bitmap.buffer);
    //                Mem_Free(bitmap);
    //                return glyph;
    //            }
    //
    //            src = bitmap.buffer;
    //            dst = imageOut[0].substring((yOut[0] * 256) + xOut[0]);//TODO:copy back?
    //
    //            int _src = 0;//src;
    //            int _dst = 0;//dst;
    //            if (bitmap.pixel_mode == ft_pixel_mode_mono) {
    //                for (i = 0; i < glyph.height; i++) {
    //                    int j;
    //                    _src = 0;//src;
    //                    _dst = 0;//dst;
    //                    char mask = 0x80;
    //                    int val = src.charAt(_src);
    //                    final char[] tempDst = dst.toCharArray();
    //                    for (j = 0; j < glyph.pitch; j++) {
    //                        if (mask == 0x80) {
    //                            val = src.charAt(_src++);
    //                        }
    //                        if ((val & mask) != 0) {
    //                            tempDst[_dst] = 0xff;
    //                        }
    //                        mask >>= 1;
    //
    //                        if (mask == 0) {
    //                            mask = 0x80;
    //                        }
    //                        _dst++;
    //                    }
    //
    //                    _src += glyph.pitch;
    //                    _dst += 256;
    //
    //                }
    //            } else {
    //                for (i = 0; i < glyph.height; i++) {
    //                    memcpy(dst, src, glyph.pitch);
    //                    _src += glyph.pitch;
    //                    _dst += 256;
    //                }
    //            }
    //
    //            // we now have an 8 bit per pixel grey scale bitmap 
    //            // that is width wide and pf.ftSize.metrics.y_ppem tall
    //            glyph.imageHeight = (int) scaled_height;
    //            glyph.imageWidth = (int) scaled_width;
    //            glyph.s = (float) xOut[0] / 256;
    //            glyph.t = (float) yOut[0] / 256;
    //            glyph.s2 = glyph.s + (float) scaled_width / 256;
    //            glyph.t2 = glyph.t + (float) scaled_height / 256;
    //
    //            xOut[0] += scaled_width + 1;
    //        }
    //
    //        Mem_Free(bitmap.buffer);
    //        Mem_Free(bitmap);
    //
    //        return glyph;
    //    }
    //
    /*
     ============
     readInt
     ============
     */
    fun readInt(): Int {
        val i: Int = fdFile!!.get(fdOffset) + (fdFile!!.get(fdOffset + 1)
            .toInt() shl 8) + (fdFile!!.get(fdOffset + 2)
            .toInt() shl 16) + (fdFile!!.get(fdOffset + 3).toInt() shl 24)
        fdOffset += 4
        return i
    }

    /*
     ============
     readFloat
     ============
     */
    fun readFloat(): Float {
        val me: poor = poor()
        me.setFfred(fdFile, fdOffset)
        fdOffset += 4
        return me.ffred
    }

    /*
     ============
     R_InitFreeType
     ============
     */
    fun R_InitFreeType() {
        if (BUILD_FREETYPE) {
//            if (FT_Init_FreeType(ftLibrary)) {
//                common.Printf("R_InitFreeType: Unable to initialize FreeType.\n");
//            }
        }
        //	registeredFontCount = 0;
    }

    /*
     ============
     R_DoneFreeType
     ============
     */
    fun R_DoneFreeType() {
        if (BUILD_FREETYPE) {
//            if (ftLibrary) {
//                FT_Done_FreeType(ftLibrary);
//                ftLibrary = null;
//            }
        }
        //	registeredFontCount = 0;
    }

    private class poor {
        //mistreated me.
        private val fred: ByteBuffer = ByteBuffer.allocate(4)

        init {
            fred.order(ByteOrder.LITTLE_ENDIAN)
        }

        val ffred: Float
            get() {
                return fred.getFloat(0)
            }

        fun setFfred(fred: ByteArray?, offset: Int) {
            this.fred.put(fred, offset, 4).flip()
        }
    }
}

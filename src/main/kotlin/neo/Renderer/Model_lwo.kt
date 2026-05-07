/*
===========================================================================

Doom 3 GPL Source Code
Copyright (C) 1999-2011 id Software LLC, a ZeniMax Media company.
Translated to Kotlin by Dr. Feederino with support of Claude Code

This file is part of the Doom 3 GPL Source Code ("Doom 3 Source Code").
Original source: neo/renderer/Model_lwo.cpp + neo/renderer/Model_lwo.h

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

import neo.framework.FileSystem_h
import neo.framework.File_h.fsOrigin_t
import neo.framework.File_h.idFile
import neo.idlib.BigRevBytes
import neo.idlib.Text.strLen
import neo.idlib.containers.List.cmp_t
import neo.idlib.containers.bbtocb
import neo.idlib.math.FLOAT_IS_DENORMAL
import neo.idlib.math.idMath
import neo.idlib.toInt
import neo.idlib.toUnsignedInt
import java.nio.ByteBuffer
import java.util.*
import java.util.stream.Collectors
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.ln

object Model_lwo {
    /*
     ======================================================================

     LWO2 loader. (LightWave Object)

     Ernie Wright  17 Sep 00

     ======================================================================
     */
    const val BEH_CONSTANT = 1
    const val BEH_LINEAR = 5
    const val BEH_OFFSET = 4
    const val BEH_OSCILLATE = 3
    const val BEH_REPEAT = 2
    const val BEH_RESET = 0

    /*
     ======================================================================
     flen

     This accumulates a count of the number of bytes read.  Callers can set
     it at the beginning of a sequence of reads and then retrieve it to get
     the number of bytes actually read.  If one of the I/O functions fails,
     flen is set to an error code, after which the I/O functions ignore
     read requests until flen is reset.
     ====================================================================== */
    const val FLEN_ERROR = -9999

    const val ID_AAST = 'A'.code shl 24 or ('A'.code shl 16) or ('S'.code shl 8) or 'T'.code
    const val ID_ADTR = 'A'.code shl 24 or ('D'.code shl 16) or ('T'.code shl 8) or 'R'.code
    const val ID_ALPH = 'A'.code shl 24 or ('L'.code shl 16) or ('P'.code shl 8) or 'H'.code
    const val ID_ANIM = 'A'.code shl 24 or ('N'.code shl 16) or ('I'.code shl 8) or 'M'.code
    const val ID_AVAL = 'A'.code shl 24 or ('V'.code shl 16) or ('A'.code shl 8) or 'L'.code
    const val ID_AXIS = 'A'.code shl 24 or ('X'.code shl 16) or ('I'.code shl 8) or 'S'.code
    const val ID_BBOX = 'B'.code shl 24 or ('B'.code shl 16) or ('O'.code shl 8) or 'X'.code
    const val ID_BEZ2 = 'B'.code shl 24 or ('E'.code shl 16) or ('Z'.code shl 8) or '2'.code
    const val ID_BEZI = 'B'.code shl 24 or ('E'.code shl 16) or ('Z'.code shl 8) or 'I'.code
    const val ID_BLOK = 'B'.code shl 24 or ('L'.code shl 16) or ('O'.code shl 8) or 'K'.code
    const val ID_BONE = 'B'.code shl 24 or ('O'.code shl 16) or ('N'.code shl 8) or 'E'.code
    const val ID_BRIT = 'B'.code shl 24 or ('R'.code shl 16) or ('I'.code shl 8) or 'T'.code
    const val ID_BUMP = 'B'.code shl 24 or ('U'.code shl 16) or ('M'.code shl 8) or 'P'.code
    const val ID_CHAN = 'C'.code shl 24 or ('H'.code shl 16) or ('A'.code shl 8) or 'N'.code
    const val ID_CLIP = 'C'.code shl 24 or ('L'.code shl 16) or ('I'.code shl 8) or 'P'.code
    const val ID_CLRF = 'C'.code shl 24 or ('L'.code shl 16) or ('R'.code shl 8) or 'F'.code
    const val ID_CLRH = 'C'.code shl 24 or ('L'.code shl 16) or ('R'.code shl 8) or 'H'.code
    const val ID_CNTR = 'C'.code shl 24 or ('N'.code shl 16) or ('T'.code shl 8) or 'R'.code

    /* surfaces */
    const val ID_COLR = 'C'.code shl 24 or ('O'.code shl 16) or ('L'.code shl 8) or 'R'.code
    const val ID_CONT = 'C'.code shl 24 or ('O'.code shl 16) or ('N'.code shl 8) or 'T'.code
    const val ID_CSYS = 'C'.code shl 24 or ('S'.code shl 16) or ('Y'.code shl 8) or 'S'.code
    const val ID_CURV = 'C'.code shl 24 or ('U'.code shl 16) or ('R'.code shl 8) or 'V'.code
    const val ID_DATA = 'D'.code shl 24 or ('A'.code shl 16) or ('T'.code shl 8) or 'A'.code
    const val ID_DESC = 'D'.code shl 24 or ('E'.code shl 16) or ('S'.code shl 8) or 'C'.code
    const val ID_DIFF = 'D'.code shl 24 or ('I'.code shl 16) or ('F'.code shl 8) or 'F'.code
    const val ID_ENAB = 'E'.code shl 24 or ('N'.code shl 16) or ('A'.code shl 8) or 'B'.code
    const val ID_ENVL = 'E'.code shl 24 or ('N'.code shl 16) or ('V'.code shl 8) or 'L'.code
    const val ID_ETPS = 'E'.code shl 24 or ('T'.code shl 16) or ('P'.code shl 8) or 'S'.code

    /* polygon types */
    const val ID_FACE = 'F'.code shl 24 or ('A'.code shl 16) or ('C'.code shl 8) or 'E'.code
    const val ID_FALL = 'F'.code shl 24 or ('A'.code shl 16) or ('L'.code shl 8) or 'L'.code
    const val ID_FKEY = 'F'.code shl 24 or ('K'.code shl 16) or ('E'.code shl 8) or 'Y'.code
    const val ID_FLAG = 'F'.code shl 24 or ('L'.code shl 16) or ('A'.code shl 8) or 'G'.code
    const val ID_FORM = 'F'.code shl 24 or ('O'.code shl 16) or ('R'.code shl 8) or 'M'.code
    const val ID_FTPS = 'F'.code shl 24 or ('T'.code shl 16) or ('P'.code shl 8) or 'S'.code
    const val ID_FUNC = 'F'.code shl 24 or ('U'.code shl 16) or ('N'.code shl 8) or 'C'.code
    const val ID_GAMM = 'G'.code shl 24 or ('A'.code shl 16) or ('M'.code shl 8) or 'M'.code
    const val ID_GLOS = 'G'.code shl 24 or ('L'.code shl 16) or ('O'.code shl 8) or 'S'.code

    /* gradient */
    const val ID_GRAD = 'G'.code shl 24 or ('R'.code shl 16) or ('A'.code shl 8) or 'D'.code
    const val ID_GREN = 'G'.code shl 24 or ('R'.code shl 16) or ('E'.code shl 8) or 'N'.code
    const val ID_GRPT = 'G'.code shl 24 or ('R'.code shl 16) or ('P'.code shl 8) or 'T'.code
    const val ID_GRST = 'G'.code shl 24 or ('R'.code shl 16) or ('S'.code shl 8) or 'T'.code
    const val ID_GVAL = 'G'.code shl 24 or ('V'.code shl 16) or ('A'.code shl 8) or 'L'.code
    const val ID_HERM = 'H'.code shl 24 or ('E'.code shl 16) or ('R'.code shl 8) or 'M'.code
    const val ID_HUE = 'H'.code shl 24 or ('U'.code shl 16) or ('E'.code shl 8) or ' '.code
    const val ID_ICON = 'I'.code shl 24 or ('C'.code shl 16) or ('O'.code shl 8) or 'N'.code
    const val ID_IFLT = 'I'.code shl 24 or ('F'.code shl 16) or ('L'.code shl 8) or 'T'.code
    const val ID_IKEY = 'I'.code shl 24 or ('K'.code shl 16) or ('E'.code shl 8) or 'Y'.code
    const val ID_IMAG = 'I'.code shl 24 or ('M'.code shl 16) or ('A'.code shl 8) or 'G'.code

    /* image map */
    const val ID_IMAP = 'I'.code shl 24 or ('M'.code shl 16) or ('A'.code shl 8) or 'P'.code
    const val ID_INAM = 'I'.code shl 24 or ('N'.code shl 16) or ('A'.code shl 8) or 'M'.code
    const val ID_ISEQ = 'I'.code shl 24 or ('S'.code shl 16) or ('E'.code shl 8) or 'Q'.code
    const val ID_ITPS = 'I'.code shl 24 or ('T'.code shl 16) or ('P'.code shl 8) or 'S'.code
    const val ID_KEY = 'K'.code shl 24 or ('E'.code shl 16) or ('Y'.code shl 8) or ' '.code

    /* top-level chunks */
    const val ID_LAYR = 'L'.code shl 24 or ('A'.code shl 16) or ('Y'.code shl 8) or 'R'.code
    const val ID_LINE = 'L'.code shl 24 or ('I'.code shl 16) or ('N'.code shl 8) or 'E'.code
    const val ID_LSIZ = 'L'.code shl 24 or ('S'.code shl 16) or ('I'.code shl 8) or 'Z'.code
    const val ID_LUMI = 'L'.code shl 24 or ('U'.code shl 16) or ('M'.code shl 8) or 'I'.code
    const val ID_LWO2 = 'L'.code shl 24 or ('W'.code shl 16) or ('O'.code shl 8) or '2'.code
    const val ID_LWOB = 'L'.code shl 24 or ('W'.code shl 16) or ('O'.code shl 8) or 'B'.code
    const val ID_MBAL = 'M'.code shl 24 or ('B'.code shl 16) or ('A'.code shl 8) or 'L'.code
    const val ID_NAME = 'N'.code shl 24 or ('A'.code shl 16) or ('M'.code shl 8) or 'E'.code
    const val ID_NEGA = 'N'.code shl 24 or ('E'.code shl 16) or ('G'.code shl 8) or 'A'.code
    const val ID_OPAC = 'O'.code shl 24 or ('P'.code shl 16) or ('A'.code shl 8) or 'C'.code
    const val ID_OREF = 'O'.code shl 24 or ('R'.code shl 16) or ('E'.code shl 8) or 'F'.code

    /* polygon tags */
    const val ID_PART = 'P'.code shl 24 or ('A'.code shl 16) or ('R'.code shl 8) or 'T'.code
    const val ID_PFLT = 'P'.code shl 24 or ('F'.code shl 16) or ('L'.code shl 8) or 'T'.code
    const val ID_PIXB = 'P'.code shl 24 or ('I'.code shl 16) or ('X'.code shl 8) or 'B'.code
    const val ID_PNAM = 'P'.code shl 24 or ('N'.code shl 16) or ('A'.code shl 8) or 'M'.code
    const val ID_PNTS = 'P'.code shl 24 or ('N'.code shl 16) or ('T'.code shl 8) or 'S'.code
    const val ID_POLS = 'P'.code shl 24 or ('O'.code shl 16) or ('L'.code shl 8) or 'S'.code
    const val ID_POST = 'P'.code shl 24 or ('O'.code shl 16) or ('S'.code shl 8) or 'T'.code

    /* envelopes */
    const val ID_PRE = 'P'.code shl 24 or ('R'.code shl 16) or ('E'.code shl 8) or ' '.code

    /* procedural */
    const val ID_PROC = 'P'.code shl 24 or ('R'.code shl 16) or ('O'.code shl 8) or 'C'.code
    const val ID_PROJ = 'P'.code shl 24 or ('R'.code shl 16) or ('O'.code shl 8) or 'J'.code
    const val ID_PTAG = 'P'.code shl 24 or ('T'.code shl 16) or ('A'.code shl 8) or 'G'.code
    const val ID_PTCH = 'P'.code shl 24 or ('T'.code shl 16) or ('C'.code shl 8) or 'H'.code
    const val ID_REFL = 'R'.code shl 24 or ('E'.code shl 16) or ('F'.code shl 8) or 'L'.code
    const val ID_RFOP = 'R'.code shl 24 or ('F'.code shl 16) or ('O'.code shl 8) or 'P'.code
    const val ID_RIMG = 'R'.code shl 24 or ('I'.code shl 16) or ('M'.code shl 8) or 'G'.code
    const val ID_RIND = 'R'.code shl 24 or ('I'.code shl 16) or ('N'.code shl 8) or 'D'.code
    const val ID_ROTA = 'R'.code shl 24 or ('O'.code shl 16) or ('T'.code shl 8) or 'A'.code
    const val ID_RSAN = 'R'.code shl 24 or ('S'.code shl 16) or ('A'.code shl 8) or 'N'.code
    const val ID_SATR = 'S'.code shl 24 or ('A'.code shl 16) or ('T'.code shl 8) or 'R'.code

    /* shader */
    const val ID_SHDR = 'S'.code shl 24 or ('H'.code shl 16) or ('D'.code shl 8) or 'R'.code
    const val ID_SHRP = 'S'.code shl 24 or ('H'.code shl 16) or ('R'.code shl 8) or 'P'.code
    const val ID_SIDE = 'S'.code shl 24 or ('I'.code shl 16) or ('D'.code shl 8) or 'E'.code
    const val ID_SIZE = 'S'.code shl 24 or ('I'.code shl 16) or ('Z'.code shl 8) or 'E'.code
    const val ID_SMAN = 'S'.code shl 24 or ('M'.code shl 16) or ('A'.code shl 8) or 'N'.code
    const val ID_SMGP = 'S'.code shl 24 or ('M'.code shl 16) or ('G'.code shl 8) or 'P'.code
    const val ID_SPAN = 'S'.code shl 24 or ('P'.code shl 16) or ('A'.code shl 8) or 'N'.code
    const val ID_SPEC = 'S'.code shl 24 or ('P'.code shl 16) or ('E'.code shl 8) or 'C'.code
    const val ID_STCC = 'S'.code shl 24 or ('T'.code shl 16) or ('C'.code shl 8) or 'C'.code
    const val ID_STCK = 'S'.code shl 24 or ('T'.code shl 16) or ('C'.code shl 8) or 'K'.code
    const val ID_STEP = 'S'.code shl 24 or ('T'.code shl 16) or ('E'.code shl 8) or 'P'.code

    /* clips */
    const val ID_STIL = 'S'.code shl 24 or ('T'.code shl 16) or ('I'.code shl 8) or 'L'.code
    const val ID_SURF = 'S'.code shl 24 or ('U'.code shl 16) or ('R'.code shl 8) or 'F'.code
    const val ID_TAGS = 'T'.code shl 24 or ('A'.code shl 16) or ('G'.code shl 8) or 'S'.code
    const val ID_TAMP = 'T'.code shl 24 or ('A'.code shl 16) or ('M'.code shl 8) or 'P'.code
    const val ID_TCB = 'T'.code shl 24 or ('C'.code shl 16) or ('B'.code shl 8) or ' '.code
    const val ID_TEXT = 'T'.code shl 24 or ('E'.code shl 16) or ('X'.code shl 8) or 'T'.code
    const val ID_TIME = 'T'.code shl 24 or ('I'.code shl 16) or ('M'.code shl 8) or 'E'.code
    const val ID_TIMG = 'T'.code shl 24 or ('I'.code shl 16) or ('M'.code shl 8) or 'G'.code

    /* texture coordinates */
    const val ID_TMAP = 'T'.code shl 24 or ('M'.code shl 16) or ('A'.code shl 8) or 'P'.code
    const val ID_TRAN = 'T'.code shl 24 or ('R'.code shl 16) or ('A'.code shl 8) or 'N'.code
    const val ID_TRNL = 'T'.code shl 24 or ('R'.code shl 16) or ('N'.code shl 8) or 'L'.code
    const val ID_TROP = 'T'.code shl 24 or ('R'.code shl 16) or ('O'.code shl 8) or 'P'.code

    /* texture layer */
    const val ID_TYPE = 'T'.code shl 24 or ('Y'.code shl 16) or ('P'.code shl 8) or 'E'.code
    const val ID_VALU = 'V'.code shl 24 or ('A'.code shl 16) or ('L'.code shl 8) or 'U'.code
    const val ID_VMAD = 'V'.code shl 24 or ('M'.code shl 16) or ('A'.code shl 8) or 'D'.code
    const val ID_VMAP = 'V'.code shl 24 or ('M'.code shl 16) or ('A'.code shl 8) or 'P'.code
    const val ID_WRAP = 'W'.code shl 24 or ('R'.code shl 16) or ('A'.code shl 8) or 'P'.code
    const val ID_WRPH = 'W'.code shl 24 or ('R'.code shl 16) or ('P'.code shl 8) or 'H'.code
    const val ID_WRPW = 'W'.code shl 24 or ('R'.code shl 16) or ('P'.code shl 8) or 'W'.code
    const val ID_XREF = 'X'.code shl 24 or ('R'.code shl 16) or ('E'.code shl 8) or 'F'.code
    const val PROJ_CUBIC = 3
    const val PROJ_CYLINDRICAL = 1
    const val PROJ_FRONT = 4
    const val PROJ_PLANAR = 0
    const val PROJ_SPHERICAL = 2
    const val WRAP_EDGE = 1
    const val WRAP_MIRROR = 3
    const val WRAP_NONE = 0
    const val WRAP_REPEAT = 2
    const val ID_BTEX = 'B'.code shl 24 or ('T'.code shl 16) or ('E'.code shl 8) or 'X'.code
    const val ID_CTEX = 'C'.code shl 24 or ('T'.code shl 16) or ('E'.code shl 8) or 'X'.code
    const val ID_DTEX = 'D'.code shl 24 or ('T'.code shl 16) or ('E'.code shl 8) or 'X'.code
    const val ID_LTEX = 'L'.code shl 24 or ('T'.code shl 16) or ('E'.code shl 8) or 'X'.code
    const val ID_RFLT = 'R'.code shl 24 or ('F'.code shl 16) or ('L'.code shl 8) or 'T'.code
    const val ID_RTEX = 'R'.code shl 24 or ('T'.code shl 16) or ('E'.code shl 8) or 'X'.code
    const val ID_SDAT = 'S'.code shl 24 or ('D'.code shl 16) or ('A'.code shl 8) or 'T'.code

    /* IDs specific to LWOB */
    const val ID_SRFS = 'S'.code shl 24 or ('R'.code shl 16) or ('F'.code shl 8) or 'S'.code
    const val ID_STEX = 'S'.code shl 24 or ('T'.code shl 16) or ('E'.code shl 8) or 'X'.code
    const val ID_TAAS = 'T'.code shl 24 or ('A'.code shl 16) or ('A'.code shl 8) or 'S'.code
    const val ID_TCLR = 'T'.code shl 24 or ('C'.code shl 16) or ('L'.code shl 8) or 'R'.code
    const val ID_TCTR = 'T'.code shl 24 or ('C'.code shl 16) or ('T'.code shl 8) or 'R'.code
    const val ID_TFAL = 'T'.code shl 24 or ('F'.code shl 16) or ('A'.code shl 8) or 'L'.code
    const val ID_TFLG = 'T'.code shl 24 or ('F'.code shl 16) or ('L'.code shl 8) or 'G'.code
    const val ID_TFP0 = 'T'.code shl 24 or ('F'.code shl 16) or ('P'.code shl 8) or '0'.code
    const val ID_TFP1 = 'T'.code shl 24 or ('F'.code shl 16) or ('P'.code shl 8) or '1'.code
    const val ID_TOPC = 'T'.code shl 24 or ('O'.code shl 16) or ('P'.code shl 8) or 'C'.code
    const val ID_TREF = 'T'.code shl 24 or ('R'.code shl 16) or ('E'.code shl 8) or 'F'.code
    const val ID_TSIZ = 'T'.code shl 24 or ('S'.code shl 16) or ('I'.code shl 8) or 'Z'.code
    const val ID_TTEX = 'T'.code shl 24 or ('T'.code shl 16) or ('E'.code shl 8) or 'X'.code
    const val ID_TVAL = 'T'.code shl 24 or ('V'.code shl 16) or ('A'.code shl 8) or 'L'.code
    const val ID_TVEL = 'T'.code shl 24 or ('V'.code shl 16) or ('E'.code shl 8) or 'L'.code
    const val ID_VDIF = 'V'.code shl 24 or ('D'.code shl 16) or ('I'.code shl 8) or 'F'.code

    const val ID_VLUM = 'V'.code shl 24 or ('L'.code shl 16) or ('U'.code shl 8) or 'M'.code
    const val ID_VSPC = 'V'.code shl 24 or ('S'.code shl 16) or ('P'.code shl 8) or 'C'.code
    var flen = 0

    /* chunk and subchunk IDs */
    fun LWID_(a: Char, b: Char, c: Char, d: Char): Int {
        return a.code shl 24 or (b.code shl 16) or (c.code shl 8) or (d.code shl 0)
    }

    /*
     ======================================================================
     lwGetClip()

     Read image references from a CLIP chunk in an LWO2 file.
     ====================================================================== */
    fun lwGetClip(fp: idFile, cksize: Int): lwClip? {
        val clip = lwClip()
        var filt: lwPlugin
        var id: Int
        var sz: Int
        val pos: Int
        var rlen: Int

        /* allocate the Clip structure */
        clip.contrast.`val` = 1.0f
        clip.brightness.`val` = 1.0f
        clip.saturation.`val` = 1.0f
        clip.gamma.`val` = 1.0f

        /* remember where we started */set_flen(0)
        pos = fp.Tell()

        /* index */clip.index = getI4(fp)

        /* first subchunk header */clip.type = getU4(fp)
        sz = getU2(fp)
        if (0 > get_flen()) {
            return gotoFail(clip)
        }
        sz += sz and 1
        set_flen(0)
        when (clip.type) {
            ID_STIL -> clip.source.still.name = getS0(fp)
            ID_ISEQ -> {
                clip.source.seq.digits = getU1(fp).code
                clip.source.seq.flags = getU1(fp).code
                clip.source.seq.offset = getI2(fp).toInt()
                clip.source.seq.start = getI2(fp).toInt()
                clip.source.seq.end = getI2(fp).toInt()
                clip.source.seq.prefix = getS0(fp)
                clip.source.seq.suffix = getS0(fp)
            }

            ID_ANIM -> {
                clip.source.anim.name = getS0(fp)
                clip.source.anim.server = getS0(fp)
                rlen = get_flen()
                clip.source.anim.data = getbytes(fp, sz - rlen)
            }

            ID_XREF -> {
                clip.source.xref.index = getI4(fp)
                clip.source.xref.string = getS0(fp)
            }

            ID_STCC -> {
                clip.source.cycle.lo = getI2(fp).toInt()
                clip.source.cycle.hi = getI2(fp).toInt()
                clip.source.cycle.name = getS0(fp)
            }

            else -> {}
        }

        /* error while reading current subchunk? */rlen = get_flen()
        if (rlen < 0 || rlen > sz) {
            return gotoFail(clip)
        }

        /* skip unread parts of the current subchunk */if (rlen < sz) {
            fp.Seek((sz - rlen).toLong(), fsOrigin_t.FS_SEEK_CUR)
        }

        /* end of the CLIP chunk? */rlen = fp.Tell() - pos
        if (cksize < rlen) {
            return gotoFail(clip)
        }
        if (cksize == rlen) {
            return clip
        }

        /* process subchunks as they're encountered */id = getU4(fp)
        sz = getU2(fp)
        if (0 > get_flen()) {
            return gotoFail(clip)
        }
        while (true) {
            sz += sz and 1
            set_flen(0)
            when (id) {
                ID_TIME -> {
                    clip.start_time = getF4(fp)
                    clip.duration = getF4(fp)
                    clip.frame_rate = getF4(fp)
                }

                ID_CONT -> {
                    clip.contrast.`val` = getF4(fp)
                    clip.contrast.eindex = getVX(fp)
                }

                ID_BRIT -> {
                    clip.brightness.`val` = getF4(fp)
                    clip.brightness.eindex = getVX(fp)
                }

                ID_SATR -> {
                    clip.saturation.`val` = getF4(fp)
                    clip.saturation.eindex = getVX(fp)
                }

                ID_HUE -> {
                    clip.hue.`val` = getF4(fp)
                    clip.hue.eindex = getVX(fp)
                }

                ID_GAMM -> {
                    clip.gamma.`val` = getF4(fp)
                    clip.gamma.eindex = getVX(fp)
                }

                ID_NEGA -> clip.negative = getU2(fp)
                ID_IFLT, ID_PFLT -> {
                    filt = lwPlugin()
                    filt.name = getS0(fp)
                    filt.flags = getU2(fp)
                    rlen = get_flen()
                    filt.data = getbytes(fp, sz - rlen)
                    if (id == ID_IFLT) {
                        clip.ifilter = lwListAdd(clip.ifilter, filt)!!
                        clip.nifilters++
                    } else {
                        clip.pfilter = lwListAdd(clip.pfilter, filt)!!
                        clip.npfilters++
                    }
                }

                else -> {}
            }

            /* error while reading current subchunk? */rlen = get_flen()
            if (rlen < 0 || rlen > sz) {
                return gotoFail(clip)
            }

            /* skip unread parts of the current subchunk */if (rlen < sz) {
                fp.Seek((sz - rlen).toLong(), fsOrigin_t.FS_SEEK_CUR)
            }

            /* end of the CLIP chunk? */rlen = fp.Tell() - pos
            if (cksize < rlen) {
                return gotoFail(clip)
            }
            if (cksize == rlen) {
                break
            }

            /* get the next chunk header */set_flen(0)
            id = getU4(fp)
            sz = getU2(fp)
            if (6 != get_flen()) {
                return gotoFail(clip)
            }
        }
        return clip
    }

    fun gotoFail(clip: lwClip): lwClip? {
        lwFreeClip.getInstance().run(clip)
        return null
    }

    /*
     ======================================================================
     lwFindClip()

     Returns an lwClip pointer, given a clip index.
     ====================================================================== */
    fun lwFindClip(list: lwClip?, index: Int): lwClip? {
        var clip: lwClip?
        clip = list
        while (clip != null) {
            if (clip.index == index) {
                break
            }
            clip = clip.next
        }
        return clip
    }

    /*
     ======================================================================
     lwGetEnvelope()

     Read an ENVL chunk from an LWO2 file.
     ====================================================================== */
    fun lwGetEnvelope(fp: idFile, cksize: Int): lwEnvelope? {
        val env: lwEnvelope
        var key: lwKey? = null
        var plug: lwPlugin
        var id: Int
        var sz: Int
        val f = FloatArray(4)
        var i: Int
        var nparams: Int
        val pos: Int
        var rlen: Int


        /* allocate the Envelope structure */
        env = lwEnvelope()

        /* remember where we started */set_flen(0)
        pos = fp.Tell()

        /* index */env.index = getVX(fp)

        /* first subchunk header */id = getU4(fp)
        sz = getU2(fp)
        if (0 > get_flen()) {
            return gotoFailEnvelope(env)
        }

        /* process subchunks as they're encountered */while (true) {
            sz = (sz + (sz and 1).toShort())
            set_flen(0)
            when (id) {
                ID_TYPE -> env.type = getU2(fp)
                ID_NAME -> env.name = getS0(fp)
                ID_PRE -> env.behavior[0] = getU2(fp)
                ID_POST -> env.behavior[1] = getU2(fp)
                ID_KEY -> {
                    key = lwKey()
                    key.time = getF4(fp)
                    key.value = getF4(fp)
                    lwListInsert(env.key, key)
                    env.nkeys++
                }

                ID_SPAN -> {
                    if (null == key) {
                        return gotoFailEnvelope(env)
                    }
                    key.shape = getU4(fp).toLong()
                    nparams = (sz - 4) / 4
                    if (nparams > 4) {
                        nparams = 4
                    }
                    i = 0
                    while (i < nparams) {
                        f[i] = getF4(fp)
                        i++
                    }
                    when (key.shape.toInt()) {
                        ID_TCB -> {
                            key.tension = f[0]
                            key.continuity = f[1]
                            key.bias = f[2]
                        }

                        ID_BEZI, ID_HERM, ID_BEZ2 -> {
                            i = 0
                            while (i < nparams) {
                                key.param[i] = f[i]
                                i++
                            }
                        }
                    }
                }

                ID_CHAN -> {
                    plug = lwPlugin()
                    plug.name = getS0(fp)
                    plug.flags = getU2(fp)
                    plug.data = getbytes(fp, sz - get_flen())
                    env.cfilter = lwListAdd(env.cfilter, plug)!!
                    env.ncfilters++
                }

                else -> {}
            }

            /* error while reading current subchunk? */rlen = get_flen()
            if (rlen < 0 || rlen > sz) {
                return gotoFailEnvelope(env)
            }

            /* skip unread parts of the current subchunk */if (rlen < sz) {
                fp.Seek((sz - rlen).toLong(), fsOrigin_t.FS_SEEK_CUR)
            }

            /* end of the ENVL chunk? */rlen = fp.Tell() - pos
            if (cksize < rlen) {
                return gotoFailEnvelope(env)
            }
            if (cksize == rlen) {
                break
            }

            /* get the next subchunk header */set_flen(0)
            id = getU4(fp)
            sz = getU2(fp)
            if (6 != get_flen()) {
                return gotoFailEnvelope(env)
            }
        }
        return env

    }

    fun gotoFailEnvelope(envelope: lwEnvelope): lwEnvelope? {
        lwFreeEnvelope.getInstance().run(envelope)
        return null
    }

    /*
     ======================================================================
     lwFindEnvelope()

     Returns an lwEnvelope pointer, given an envelope index.
     ====================================================================== */
    fun lwFindEnvelope(list: lwEnvelope?, index: Int): lwEnvelope? {
        var env: lwEnvelope?
        env = list
        while (env != null) {
            if (env.index == index) {
                break
            }
            env = env.next
        }
        return env
    }

    /*
     ======================================================================
     range()

     Given the value v of a periodic function, returns the equivalent value
     v2 in the principal interval [lo, hi].  If i isn't null, it receives
     the number of wavelengths between v and v2.

     v2 = v - i * (hi - lo)

     For example, range( 3 pi, 0, 2 pi, i ) returns pi, with i = 1.
     ====================================================================== */
    fun range(v: Float, lo: Float, hi: Float, i: IntArray?): Float {
        val v2: Float
        val r = hi - lo
        if (r == 0.0f) {
            if (i != null) {
                i[0] = 0
            }
            return lo
        }
        v2 = lo + v - r * floor(v / r)
        if (i != null) {
            i[0] = -((v2 - v) / r + if (v2 > v) 0.5f else -0.5f).toInt()
        }
        return v2
    }

    /*
     ======================================================================
     hermite()

     Calculate the Hermite coefficients.
     ====================================================================== */
    fun hermite(t: Float, h1: FloatArray, h2: FloatArray, h3: FloatArray, h4: FloatArray) {
        val t2: Float
        val t3: Float
        t2 = t * t
        t3 = t * t2
        h2[0] = 3.0f * t2 - t3 - t3
        h1[0] = 1.0f - h2[0]
        h4[0] = t3 - t2
        h3[0] = h4[0] - t2 + t
    }

    /*
     ======================================================================
     bezier()

     Interpolate the value of a 1D Bezier curve.
     ====================================================================== */
    fun bezier(x0: Float, x1: Float, x2: Float, x3: Float, t: Float): Float {
        val a: Float
        val b: Float
        val c: Float
        val t2: Float
        val t3: Float
        t2 = t * t
        t3 = t2 * t
        c = 3.0f * (x1 - x0)
        b = 3.0f * (x2 - x1) - c
        a = x3 - x0 - c - b
        return a * t3 + b * t2 + c * t + x0
    }

    /*
     ======================================================================
     bez2_time()

     Find the t for which bezier() returns the input time.  The handle
     endpoints of a BEZ2 curve represent the control points, and these have
     (time, value) coordinates, so time is used as both a coordinate and a
     parameter for this curve type.
     ====================================================================== */
    fun bez2_time(
        x0: Float, x1: Float, x2: Float, x3: Float, time: Float,
        t0: FloatArray, t1: FloatArray
    ): Float {
        val v: Float
        val t: Float
        t = t0[0] + (t1[0] - t0[0]) * 0.5f
        v = bezier(x0, x1, x2, x3, t)
        return if (abs(time - v) > 0.0001) {
            if (v > time) {
                t1[0] = t
            } else {
                t0[0] = t
            }
            bez2_time(x0, x1, x2, x3, time, t0, t1)
        } else {
            t
        }
    }

    /*
     ======================================================================
     bez2()

     Interpolate the value of a BEZ2 curve.
     ====================================================================== */
    fun bez2(key0: lwKey, key1: lwKey, time: Float): Float {
        val x: Float
        val y: Float
        val t: Float
        val t0 = floatArrayOf(0.0f)
        val t1 = floatArrayOf(1.0f)
        x = if (key0.shape == ID_BEZ2.toLong()) {
            key0.time + key0.param[2]
        } else {
            key0.time + (key1.time - key0.time) / 3.0f
        }
        t = bez2_time(key0.time, x, key1.time + key1.param[0], key1.time, time, t0, t1)
        y = if (key0.shape == ID_BEZ2.toLong()) {
            key0.value + key0.param[3]
        } else {
            key0.value + key0.param[1] / 3.0f
        }
        return bezier(key0.value, y, key1.param[1] + key1.value, key1.value, t)
    }

    /*
     ======================================================================
     outgoing()

     Return the outgoing tangent to the curve at key0.  The value returned
     for the BEZ2 case is used when extrapolating a linear pre behavior and
     when interpolating a non-BEZ2 span.
     ====================================================================== */
    fun outgoing(key0: lwKey, key1: lwKey): Float {
        val a: Float
        val b: Float
        val d: Float
        val t: Float
        var out: Float
        when (key0.shape.toInt()) {
            ID_TCB -> {
                a = ((1.0f - key0.tension)
                        * (1.0f + key0.continuity)
                        * (1.0f + key0.bias))
                b = ((1.0f - key0.tension)
                        * (1.0f - key0.continuity)
                        * (1.0f - key0.bias))
                d = key1.value - key0.value
                if (key0.prev != null) {
                    t = (key1.time - key0.time) / (key1.time - key0.prev!!.time)
                    out = t * (a * (key0.value - key0.prev!!.value) + b * d)
                } else {
                    out = b * d
                }
            }

            ID_LINE -> {
                d = key1.value - key0.value
                if (key0.prev != null) {
                    t = (key1.time - key0.time) / (key1.time - key0.prev!!.time)
                    out = t * (key0.value - key0.prev!!.value + d)
                } else {
                    out = d
                }
            }

            ID_BEZI, ID_HERM -> {
                out = key0.param[1]
                if (key0.prev != null) {
                    out *= (key1.time - key0.time) / (key1.time - key0.prev!!.time)
                }
            }

            ID_BEZ2 -> {
                out = key0.param[3] * (key1.time - key0.time)
                if (abs(key0.param[2]) > 1e-5) {
                    out /= key0.param[2]
                } else {
                    out *= 1e5f
                }
            }

            ID_STEP -> out = 0.0f
            else -> out = 0.0f
        }
        return out
    }

    /*
     ======================================================================
     incoming()

     Return the incoming tangent to the curve at key1.  The value returned
     for the BEZ2 case is used when extrapolating a linear post behavior.
     ====================================================================== */
    fun incoming(key0: lwKey, key1: lwKey): Float {
        val a: Float
        val b: Float
        val d: Float
        val t: Float
        var `in`: Float
        when (key1.shape.toInt()) {
            ID_LINE -> {
                d = key1.value - key0.value
                if (key1.next != null) {
                    t = (key1.time - key0.time) / (key1.next!!.time - key0.time)
                    `in` = t * (key1.next!!.value - key1.value + d)
                } else {
                    `in` = d
                }
            }

            ID_TCB -> {
                a = ((1.0f - key1.tension)
                        * (1.0f - key1.continuity)
                        * (1.0f + key1.bias))
                b = ((1.0f - key1.tension)
                        * (1.0f + key1.continuity)
                        * (1.0f - key1.bias))
                d = key1.value - key0.value
                if (key1.next != null) {
                    t = (key1.time - key0.time) / (key1.next!!.time - key0.time)
                    `in` = t * (b * (key1.next!!.value - key1.value) + a * d)
                } else {
                    `in` = a * d
                }
            }

            ID_BEZI, ID_HERM -> {
                `in` = key1.param[0]
                if (key1.next != null) {
                    `in` *= (key1.time - key0.time) / (key1.next!!.time - key0.time)
                }
            }

            ID_BEZ2 -> {
                `in` = key1.param[1] * (key1.time - key0.time)
                if (abs(key1.param[0]) > 1e-5) {
                    `in` /= key1.param[0]
                } else {
                    `in` *= 1e5f
                }
            }

            ID_STEP -> `in` = 0.0f
            else -> `in` = 0.0f
        }
        return `in`
    }

    /*
     ======================================================================
     evalEnvelope()

     Given a list of keys and a time, returns the interpolated value of the
     envelope at that time.
     ====================================================================== */
    fun evalEnvelope(env: lwEnvelope, time: Float): Float {
        var time = time
        var key0: lwKey
        val key1: lwKey
        val skey: lwKey
        var ekey: lwKey
        val t: Float
        val `in`: Float
        val out: Float
        var offset = 0.0f
        val h1 = FloatArray(1)
        val h2 = FloatArray(1)
        val h3 = FloatArray(1)
        val h4 = FloatArray(1)
        val noff = IntArray(1)


        /* if there's no key, the value is 0 */if (env.nkeys == 0) {
            return 0.0f
        }

        /* if there's only one key, the value is constant */if (env.nkeys == 1) {
            return env.key!!.value
        }

        /* find the first and last keys */
        ekey = env.key!!
        skey = ekey
        while (ekey.next != null) {
            ekey = ekey.next!!
        }

        /* use pre-behavior if time is before first key time */if (time < skey.time) {
            when (env.behavior[0]) {
                BEH_RESET -> return 0.0f
                BEH_CONSTANT -> return skey.value
                BEH_REPEAT -> time = range(time, skey.time, ekey.time, null)
                BEH_OSCILLATE -> {
                    time = range(time, skey.time, ekey.time, noff)
                    if (noff[0] % 2 != 0) {
                        time = ekey.time - skey.time - time
                    }
                }

                BEH_OFFSET -> {
                    time = range(time, skey.time, ekey.time, noff)
                    offset = noff[0] * (ekey.value - skey.value)
                }

                BEH_LINEAR -> {
                    out = (outgoing(skey, skey.next!!)
                            / (skey.next!!.time - skey.time))
                    return out * (time - skey.time) + skey.value
                }
            }
        } /* use post-behavior if time is after last key time */ else if (time > ekey.time) {
            when (env.behavior[1]) {
                BEH_RESET -> return 0.0f
                BEH_CONSTANT -> return ekey.value
                BEH_REPEAT -> time = range(time, skey.time, ekey.time, null)
                BEH_OSCILLATE -> {
                    time = range(time, skey.time, ekey.time, noff)
                    if (noff[0] % 2 != 0) {
                        time = ekey.time - skey.time - time
                    }
                }

                BEH_OFFSET -> {
                    time = range(time, skey.time, ekey.time, noff)
                    offset = noff[0] * (ekey.value - skey.value)
                }

                BEH_LINEAR -> {
                    `in` = (incoming(ekey.prev!!, ekey)
                            / (ekey.time - ekey.prev!!.time))
                    return `in` * (time - ekey.time) + ekey.value
                }
            }
        }

        /* get the endpoints of the interval being evaluated */key0 = env.key!!
        while (time > key0.next!!.time) {
            key0 = key0.next!!
        }
        key1 = key0.next!!

        /* check for singularities first */if (time == key0.time) {
            return key0.value + offset
        } else if (time == key1.time) {
            return key1.value + offset
        }

        /* get interval length, time in [0, 1] */t = (time - key0.time) / (key1.time - key0.time)
        return when (key1.shape.toInt()) {
            ID_TCB, ID_BEZI, ID_HERM -> {
                out = outgoing(key0, key1)
                `in` = incoming(key0, key1)
                hermite(t, h1, h2, h3, h4)
                h1[0] * key0.value + h2[0] * key1.value + h3[0] * out + h4[0] * `in` + offset
            }

            ID_BEZ2 -> bez2(key0, key1, time) + offset
            ID_LINE -> key0.value + t * (key1.value - key0.value) + offset
            ID_STEP -> key0.value + offset
            else -> offset
        }
    }

    /*
     ======================================================================
     lwListFree()

     Free the items in a list.
     ====================================================================== */
    @Deprecated("")
    fun lwListFree(list: Any?, freeNode: LW) {
        var node: lwNode?
        var next: lwNode?
        node = list as lwNode?
        while (node != null) {
            next = node.getNext()
            freeNode.run(node as lwNode)
            node = next
        }
    }

    /*
     ======================================================================
     lwListAdd()

     Append a node to a list.
     ====================================================================== */
    fun <T> lwListAdd(list: T?, node: lwNode?): T? {
        var head: lwNode?
        var tail: lwNode? = null
        head = list as lwNode?
        if (null == head) {
            return node as T?
        }
        while (head != null) {
            tail = head
            head = head.getNext()
        }
        tail!!.setNext(node)
        node!!.setPrev(tail)
        return list
    }

    /*
     ======================================================================
     lwListInsert()

     Insert a node into a list in sorted order.
     ====================================================================== */
    fun lwListInsert(vList: lwNode?, vItem: lwNode) {
        val list: lwNode?
        val item: lwNode?
        var node: lwNode?
        var prev: lwNode?
        if (vList == null) {
            return  // maybe re-init?
        }
        if (vList.isNULL()) {
            vList.oSet(vItem)
            return
        }
        list = vList
        item = vItem
        node = list
        prev = null
        while (node != null) {
            if (node is lwKey && item is lwKey) {
                if (0 < compare_keys.compare(node, item)) {
                    break
                }
            } else if (node is lwTexture && item is lwTexture) {
                if (0 < compare_textures.compare(node, item)) {
                    break
                }
            } else if (node is lwPlugin && item is lwPlugin) {
                if (0 < compare_shaders.compare(node, item)) {
                    break
                }
            }
            prev = node
            node = node.getNext()
        }
        if (null == prev) {
            vList.oSet(item)
            node!!.setPrev(item)
            item.setNext(node)
        } else if (null == node) {
            prev.setNext(item)
            item.setPrev(prev)
        } else {
            item.setNext(node)
            item.setPrev(prev)
            prev.setNext(item)
            node.setPrev(item)
        }
    }

    fun get_flen(): Int {
        return flen
    }

    fun set_flen(i: Int) {
        flen = i
    }

    fun getbytes(fp: idFile, size: Int): ByteArray? {
        var data: ByteBuffer?
        if (flen == FLEN_ERROR) {
            return null
        }
        if (size < 0) {
            flen = FLEN_ERROR
            return null
        }
        data = ByteBuffer.allocate(size)
        if (null == data) {
            flen = FLEN_ERROR
            return null
        }
        if (size != fp.Read(data, size)) {
            flen = FLEN_ERROR
            data = null
            return null
        }
        flen += size
        return data.array()
    }

    fun skipbytes(fp: idFile, n: Int) {
        if (flen == FLEN_ERROR) {
            return
        }
        if (!fp.Seek(n.toLong(), fsOrigin_t.FS_SEEK_CUR)) {
            flen = FLEN_ERROR
        } else {
            flen += n
        }
    }

    fun getI1(fp: idFile): Int {
        val i: Int
        val c = byteArrayOf(0)
        if (flen == FLEN_ERROR) {
            return 0
        }
        i = fp.Read(ByteBuffer.wrap(c))
        if (i < 0) {
            flen = FLEN_ERROR
            return 0
        }
        flen += 1
        return c[0].toInt()
    }

    fun getI2(fp: idFile): Short {
        val i = ByteBuffer.allocate(2)
        if (flen == FLEN_ERROR) {
            return 0
        }
        if (2 != fp.Read(i)) {
            flen = FLEN_ERROR
            return 0
        }
        BigRevBytes(i, 1)
        flen += 2
        return i.short
    }

    fun getI4(fp: idFile): Int {
        val i = ByteBuffer.allocate(4)
        if (flen == FLEN_ERROR) {
            return 0
        }
        if (4 != fp.Read(i, 4)) {
            flen = FLEN_ERROR
            return 0
        }
        BigRevBytes(i, 1)
        flen += 4
        return i.int
    }

    fun getU1(fp: idFile): Char {
        val i: Int
        val c = byteArrayOf(0)
        if (flen == FLEN_ERROR) {
            return Char(0)
        }
        i = fp.Read(ByteBuffer.wrap(c), 1)
        if (i < 0) {
            flen = FLEN_ERROR
            return Char(0)
        }
        flen += 1
        return (c[0].toInt() and 0xFF).toChar()
    }

    fun getU2(fp: idFile): Int {
        val i = ByteBuffer.allocate(2)
        if (flen == FLEN_ERROR) {
            return 0
        }
        if (2 != fp.Read(i)) {
            flen = FLEN_ERROR
            return 0
        }
        BigRevBytes(i, 1)
        flen += 2
        return i.short.toInt() and 0xFFFF
    }

    fun getU4(fp: idFile): Int {
        val i = ByteBuffer.allocate(4)
        if (flen == FLEN_ERROR) {
            return 0
        }
        if (4 != fp.Read(i)) {
            flen = FLEN_ERROR
            return 0
        }
        BigRevBytes(i, 1)
        flen += 4
        return i.int
    }

    fun getVX(fp: idFile): Int {
        val c = ByteBuffer.allocate(1)
        var i: Int
        if (flen == FLEN_ERROR) {
            return 0
        }
        c.clear()
        if (fp.Read(c) == -1) {
            return 0
        }
        if ((c[0].toInt() and 0xFF) != 0xFF) {
            i = c.get(0).toUnsignedInt() shl 8
            c.clear()
            if (fp.Read(c) == -1) {
                return 0
            }
            i = i or c.get(0).toUnsignedInt()
            flen += 2
        } else {
            c.clear()
            if (fp.Read(c) == -1) {
                return 0
            }
            i = c.get(0).toUnsignedInt() shl 16
            c.clear()
            if (fp.Read(c) == -1) {
                return 0
            }
            i = i or (c.get(0).toUnsignedInt() shl 8)
            c.clear()
            if (fp.Read(c) == -1) {
                return 0
            }
            i = i or c.get(0).toUnsignedInt()
            flen += 4
        }
        return i
    }

    fun getF4(fp: idFile): Float {
        val f = ByteBuffer.allocate(4)
        if (flen == FLEN_ERROR) {
            return 0.0f
        }
        if (4 != fp.Read(f)) {
            flen = FLEN_ERROR
            return 0.0f
        }
        BigRevBytes(f, 1)
        flen += 4
        return if (FLOAT_IS_DENORMAL(f.getFloat(0))) {
            0.0f
        } else f.getFloat(0)
    }

    fun getS0(fp: idFile): String? {
        val s: ByteBuffer?
        var i: Int
        val len: Int
        val pos: Int
        val c = ByteBuffer.allocate(1)
        if (flen == FLEN_ERROR) {
            return null
        }
        pos = fp.Tell()
        i = 1
        while (true) {
            c.clear()
            if (fp.Read(c) == -1) {
                flen = FLEN_ERROR
                return null
            }
            if (c[0].toInt() == 0) {
                break
            }
            i++
        }
        if (i == 1) {
            if (!fp.Seek((pos + 2).toLong(), fsOrigin_t.FS_SEEK_SET)) {
                flen = FLEN_ERROR
            } else {
                flen += 2
            }
            return null
        }
        len = i + (i and 1)
        s = ByteBuffer.allocate(len)
        if (s == null) {
            flen = FLEN_ERROR
            return null
        }
        if (!fp.Seek(pos.toLong(), fsOrigin_t.FS_SEEK_SET)) {
            flen = FLEN_ERROR
            return null
        }
        if (len != fp.Read(s, len)) {
            flen = FLEN_ERROR
            return null
        }
        flen += len
        return bbtocb(s).toString().trim { it <= ' ' }
    }

    @Deprecated("")
    fun sgetI1(bp: Array<String>): Int {
        var i: Int
        if (flen == FLEN_ERROR) {
            return 0
        }
        i = bp[0][0].code
        if (i > 127) {
            i -= 256
        }
        flen += 1
        bp[0] = bp[0].substring(1)
        return i
    }

    fun sgetI2(bp: ByteBuffer): Short {
        val i: Short
        if (flen == FLEN_ERROR) {
            return 0
        }
        BigRevBytes(bp, 1)
        flen += 2
        i = bp.getShort()
        return i
    }

    @Deprecated("")
    fun sgetI4(bp: Array<String?>?): Int {
        throw UnsupportedOperationException()
    }

    @Deprecated("")
    fun sgetU1(bp: Array<String>): Char {
        val c: Char
        if (flen == FLEN_ERROR) {
            return Char(0)
        }
        c = bp[0][0]
        flen += 1
        bp[0] = bp[0].substring(1)
        return c
    }

    fun sgetU2(bp: ByteBuffer): Int {
        if (flen == FLEN_ERROR) {
            return 0
        }

        flen += 2
        return bp.short.toInt() and 0xFFFF
    }

    fun sgetU4(bp: ByteBuffer): Int {
        if (flen == FLEN_ERROR) {
            return 0
        }
        BigRevBytes(bp, 1)
        flen += 4
        return bp.int
    }

    fun sgetVX(bp: ByteBuffer): Int {
        val i: Int
        val pos = bp.position()
        if (flen == FLEN_ERROR) {
            return 0
        }
        if ((bp.get(pos).toInt() and 0xFF) != 0xFF) {
            i = bp.get(pos).toUnsignedInt() shl 8 or bp.get(pos + 1).toUnsignedInt()
            flen += 2
            bp.position(pos + 2)
        } else {
            i = bp.get(pos + 1).toUnsignedInt() shl 16 or (bp.get(pos + 2).toUnsignedInt() shl 8) or bp.get(pos + 3)
                .toUnsignedInt()
            flen += 4
            bp.position(pos + 4)
        }
        return i
    }

    fun sgetF4(bp: ByteBuffer): Float {
        var f: Float
        if (flen == FLEN_ERROR) {
            return 0.0f
        }
        BigRevBytes(bp, 1)
        flen += 4
        f = bp.float
        if (FLOAT_IS_DENORMAL(f)) {
            f = 0.0f
        }
        return f
    }

    fun sgetS0(bp: ByteBuffer): String {
        var s = ""
        var len: Int
        val pos = bp.position()
        if (flen == FLEN_ERROR) {
            return ""
        }

        s = String(bp.array()).substring(pos)
        len = strLen(s) + 1
        if (1 == len) {
            flen += 2
            bp.position(pos + 2)
            return ""
        }
        len += len and 1

        s = s.substring(0, len)
        flen += s.length
        bp.position(pos + s.length)
        return s
    }

    /*
     ======================================================================
     lwFreeObject()

     Free memory used by an lwObject.
     ====================================================================== */
    @Deprecated("")
    fun lwFreeObject(`object`: lwObject?) {
        var `object` = `object`
        if (`object` != null) {
            `object` = null
        }
    }

    /*
     ======================================================================
     lwGetObject()

     Returns the contents of a LightWave object, given its filename, or
     null if the file couldn't be loaded.  On failure, failID and failpos
     can be used to diagnose the cause.

     1.  If the file isn't an LWO2 or an LWOB, failpos will contain 12 and
     failID will be unchanged.

     2.  If an error occurs while reading, failID will contain the most
     recently read IFF chunk ID, and failpos will contain the value
     returned by fp.Tell() at the time of the failure.

     3.  If the file couldn't be opened, or an error occurs while reading
     the first 12 bytes, both failID and failpos will be unchanged.

     If you don't need this information, failID and failpos can be null.
     ====================================================================== */
    fun lwGetObject(filename: String, failID: IntArray, failpos: IntArray): lwObject? {
        var fp: idFile? // = null;
        val `object`: lwObject
        var layer: lwLayer?
        var node: lwNode?
        var id: Int
        val formsize: Int
        val type: Int
        var cksize: Int
        var i: Int
        var rlen: Int
        fp = FileSystem_h.fileSystem.OpenFileRead(filename)
        if (null == fp) {
            return null
        }

        /* read the first 12 bytes */set_flen(0)
        id = getU4(fp)
        formsize = getU4(fp)
        type = getU4(fp)
        if (12 != get_flen()) {
            FileSystem_h.fileSystem.CloseFile(fp)
            return null
        }

        /* is this a LW object? */if (id != ID_FORM) {
            FileSystem_h.fileSystem.CloseFile(fp)
            if (failpos.isNotEmpty()) {
                failpos[0] = 12
            }
            return null
        }
        if (type != ID_LWO2) {
            FileSystem_h.fileSystem.CloseFile(fp)
            return if (type == ID_LWOB) {
                lwGetObject5(filename, failID, failpos)
            } else {
                if (failpos.isNotEmpty()) {
                    failpos[0] = 12
                }
                null
            }
        }
        /* allocate an object and a default layer */
        `object` = lwObject()
        layer = lwLayer()
        `object`.layer = layer
        `object`.timeStamp[0] = fp.Timestamp()

        /* get the first chunk header */id = getU4(fp)
        cksize = getU4(fp)
        if (0 > get_flen()) {
            if (failID != null) {
                failID[0] = id
            }
            if (fp != null) {
                if (failpos != null) {
                    failpos[0] = fp.Tell()
                }
                FileSystem_h.fileSystem.CloseFile(fp)
            }
            return null
        }

        /* process chunks as they're encountered */
        var j = 0
        while (true) {
            j++
            cksize += (cksize and 1)
            when (id) {
                ID_LAYR -> {
                    if (`object`.nlayers > 0) {
                        layer = lwLayer()
                        `object`.layer = lwListAdd(`object`.layer, layer)!!
                    }
                    `object`.nlayers++
                    set_flen(0)
                    layer!!.index = getU2(fp)
                    layer.flags = getU2(fp)
                    layer.pivot[0] = getF4(fp)
                    layer.pivot[1] = getF4(fp)
                    layer.pivot[2] = getF4(fp)
                    layer.name = getS0(fp)
                    rlen = get_flen()
                    if (rlen < 0 || rlen > cksize) {
                        if (failID != null) {
                            failID[0] = id
                        }
                        if (fp != null) {
                            if (failpos != null) {
                                failpos[0] = fp.Tell()
                            }
                            FileSystem_h.fileSystem.CloseFile(fp)
                        }
                        return null
                    }
                    if (rlen <= cksize - 2) {
                        layer.parent = getU2(fp)
                    }
                    rlen = get_flen()
                    if (rlen < cksize) {
                        fp.Seek((cksize - rlen).toLong(), fsOrigin_t.FS_SEEK_CUR)
                    }
                }

                ID_PNTS -> if (!lwGetPoints(fp, cksize, layer!!.point)) {
                    if (failID != null) {
                        failID[0] = id
                    }
                    if (fp != null) {
                        if (failpos != null) {
                            failpos[0] = fp.Tell()
                        }
                        FileSystem_h.fileSystem.CloseFile(fp)
                    }
                    return null
                }

                ID_POLS -> if (!lwGetPolygons(fp, cksize, layer!!.polygon, layer.point.offset)) {
                    if (failID != null) {
                        failID[0] = id
                    }
                    if (fp != null) {
                        if (failpos != null) {
                            failpos[0] = fp.Tell()
                        }
                        FileSystem_h.fileSystem.CloseFile(fp)
                    }
                    return null
                }

                ID_VMAP, ID_VMAD -> {
                    node = lwGetVMap(
                        fp,
                        cksize,
                        layer!!.point.offset,
                        layer.polygon.offset,
                        if (id == ID_VMAD) 1 else 0
                    )
                    if (null == node) {
                        if (failID != null) {
                            failID[0] = id
                        }
                        if (fp != null) {
                            if (failpos != null) {
                                failpos[0] = fp.Tell()
                            }
                            FileSystem_h.fileSystem.CloseFile(fp)
                        }
                        return null
                    }
                    layer.vmap = lwListAdd(layer.vmap, node)!!
                    layer.nvmaps++
                }

                ID_PTAG -> if (!lwGetPolygonTags(fp, cksize, `object`.taglist, layer!!.polygon)) {
                    if (failID != null) {
                        failID[0] = id
                    }
                    if (fp != null) {
                        if (failpos != null) {
                            failpos[0] = fp.Tell()
                        }
                        FileSystem_h.fileSystem.CloseFile(fp)
                    }
                    return null
                }

                ID_BBOX -> {
                    set_flen(0)
                    i = 0
                    while (i < 6) {
                        layer!!.bbox[i] = getF4(fp)
                        i++
                    }
                    rlen = get_flen()
                    if (rlen < 0 || rlen > cksize) {
                        if (failID != null) {
                            failID[0] = id
                        }
                        if (fp != null) {
                            if (failpos != null) {
                                failpos[0] = fp.Tell()
                            }
                            FileSystem_h.fileSystem.CloseFile(fp)
                        }
                        return null
                    }
                    if (rlen < cksize) {
                        fp.Seek((cksize - rlen).toLong(), fsOrigin_t.FS_SEEK_CUR)
                    }
                }

                ID_TAGS -> if (!lwGetTags(fp, cksize, `object`.taglist)) {
                    if (failID != null) {
                        failID[0] = id
                    }
                    if (fp != null) {
                        if (failpos != null) {
                            failpos[0] = fp.Tell()
                        }
                        FileSystem_h.fileSystem.CloseFile(fp)
                    }
                    return null
                }

                ID_ENVL -> {
                    node = lwGetEnvelope(fp, cksize)
                    if (null == node) {
                        if (failID != null) {
                            failID[0] = id
                        }
                        if (fp != null) {
                            if (failpos != null) {
                                failpos[0] = fp.Tell()
                            }
                            FileSystem_h.fileSystem.CloseFile(fp)
                        }
                        return null
                    }
                    `object`.env = lwListAdd(`object`.env, node)!!
                    `object`.nenvs++
                }

                ID_CLIP -> {
                    node = lwGetClip(fp, cksize)
                    if (null == node) {
                        if (failID != null) {
                            failID[0] = id
                        }
                        if (fp != null) {
                            if (failpos != null) {
                                failpos[0] = fp.Tell()
                            }
                            FileSystem_h.fileSystem.CloseFile(fp)
                        }
                        return null
                    }
                    `object`.clip = lwListAdd(`object`.clip, node)!!
                    `object`.nclips++
                }

                ID_SURF -> {
                    node = lwGetSurface(fp, cksize)
                    if (null == node) {
                        if (failID != null) {
                            failID[0] = id
                        }
                        if (fp != null) {
                            if (failpos != null) {
                                failpos[0] = fp.Tell()
                            }
                            FileSystem_h.fileSystem.CloseFile(fp)
                        }
                        return null
                    }
                    `object`.surf = lwListAdd(`object`.surf, node)
                    `object`.nsurfs++
                }

                ID_DESC, ID_TEXT, ID_ICON -> fp.Seek(
                    cksize.toLong(),
                    fsOrigin_t.FS_SEEK_CUR
                )

                else -> fp.Seek(cksize.toLong(), fsOrigin_t.FS_SEEK_CUR)
            }

            /* end of the file? */
            if (formsize <= fp.Tell() - 8) {
                break
            }

            /* get the next chunk header */
            set_flen(0)
            id = getU4(fp)
            cksize = getU4(fp)
            if (8 != get_flen()) {
                if (failID != null) {
                    failID[0] = id
                }
                if (fp != null) {
                    if (failpos != null) {
                        failpos[0] = fp.Tell()
                    }
                    FileSystem_h.fileSystem.CloseFile(fp)
                }
                return null
            }
        }
        FileSystem_h.fileSystem.CloseFile(fp)
        fp = null
        if (`object`.nlayers == 0) {
            `object`.nlayers = 1
        }
        layer = `object`.layer
        while (layer != null) {
            lwGetBoundingBox(layer.point, layer.bbox)
            lwGetPolyNormals(layer.point, layer.polygon)
            if (!lwGetPointPolygons(layer.point, layer.polygon)) {
                if (failID != null) {
                    failID[0] = id
                }
                if (fp != null) {
                    if (failpos != null) {
                        failpos[0] = fp.Tell()
                    }
                    FileSystem_h.fileSystem.CloseFile(fp)
                }
                return null
            }
            if (!lwResolvePolySurfaces(layer.polygon, `object`)) {
                if (failID != null) {
                    failID[0] = id
                }
                if (fp != null) {
                    if (failpos != null) {
                        failpos[0] = fp.Tell()
                    }
                    FileSystem_h.fileSystem.CloseFile(fp)
                }
                return null
            }
            lwGetVertNormals(layer.point, layer.polygon)
            if (!lwGetPointVMaps(layer.point, layer.vmap)) {
                if (failID != null) {
                    failID[0] = id
                }
                if (fp != null) {
                    if (failpos != null) {
                        failpos[0] = fp.Tell()
                    }
                    FileSystem_h.fileSystem.CloseFile(fp)
                }
                return null
            }
            if (!lwGetPolyVMaps(layer.polygon, layer.vmap)) {
                if (failID != null) {
                    failID[0] = id
                }
                if (fp != null) {
                    if (failpos != null) {
                        failpos[0] = fp.Tell()
                    }
                    FileSystem_h.fileSystem.CloseFile(fp)
                }
                return null
            }
            layer = layer.next
        }
        return `object`

    }

    /*
     ======================================================================
     add_clip()

     Add a clip to the clip list.  Used to store the contents of an RIMG or
     TIMG surface subchunk.
     ====================================================================== */
    fun add_clip(s: Array<String?>, clist: lwClip?, nclips: IntArray): Int {
        var clist = clist
        val clip: lwClip
        var p: Int
        clip = lwClip()
        clip.contrast.`val` = 1.0f
        clip.brightness.`val` = 1.0f
        clip.saturation.`val` = 1.0f
        clip.gamma.`val` = 1.0f
        if (s[0]!!.indexOf("(sequence)").also { p = it } != -1) {
            s[0] = s[0]!!.substring(0, p) + '\u0000' + s[0]!!.substring(p + 1)
            clip.type = ID_ISEQ
            clip.source.seq.prefix = s[0]
            clip.source.seq.digits = 3
        } else {
            clip.type = ID_STIL
            clip.source.still.name = s[0]
        }
        nclips[0]++
        clip.index = nclips[0]
        clist = lwListAdd(clist, clip)
        return clip.index
    }

    /*
     ======================================================================
     add_tvel()

     Add a triple of envelopes to simulate the old texture velocity
     parameters.
     ====================================================================== */
    fun add_tvel(pos: FloatArray, vel: FloatArray, elist: lwEnvelope?, nenvs: IntArray): Int {
        var elist = elist
        var env = lwEnvelope()
        var key0: lwKey
        var key1: lwKey
        var i: Int
        i = 0
        while (i < 3) {
            env = lwEnvelope()
            key0 = lwKey()
            key1 = lwKey()
            key0.next = key1
            key0.value = pos[i]
            key0.time = 0.0f
            key1.prev = key0
            key1.value = pos[i] + vel[i] * 30.0f
            key1.time = 1.0f
            key1.shape = ID_LINE.toLong()
            key0.shape = key1.shape
            env.index = nenvs[0] + i + 1
            env.type = 0x0301 + i
            env.name = ""
            if (env.name != null) {
                env.name = "Position." + ('X' + i)
            }
            env.key = key0
            env.nkeys = 2
            env.behavior[0] = BEH_LINEAR
            env.behavior[1] = BEH_LINEAR
            elist = lwListAdd(elist, env)
            i++
        }
        nenvs[0] += 3
        return env.index - 2
    }

    /*
     ======================================================================
     get_texture()

     Create a new texture for BTEX, CTEX, etc. subchunks.
     ====================================================================== */
    fun get_texture(s: String): lwTexture {
        val tex: lwTexture
        tex = lwTexture()
        tex.tmap.size.`val`[2] = 1.0f
        tex.tmap.size.`val`[1] = tex.tmap.size.`val`[2]
        tex.tmap.size.`val`[0] = tex.tmap.size.`val`[1]
        tex.opacity.`val` = 1.0f
        tex.enabled = 1
        if (s.contains("Image Map")) {
            tex.type = ID_IMAP.toLong()
            if (s.contains("Planar")) {
                tex.param.imap.projection = 0
            } else if (s.contains("Cylindrical")) {
                tex.param.imap.projection = 1
            } else if (s.contains("Spherical")) {
                tex.param.imap.projection = 2
            } else if (s.contains("Cubic")) {
                tex.param.imap.projection = 3
            } else if (s.contains("Front")) {
                tex.param.imap.projection = 4
            }
            tex.param.imap.aa_strength = 1.0f
            tex.param.imap.amplitude.`val` = 1.0f
        } else {
            tex.type = ID_PROC.toLong()
            tex.param.proc.name = s
        }
        return tex
    }

    /*
     ======================================================================
     lwGetSurface5()

     Read an lwSurface from an LWOB file.
     ====================================================================== */
    fun lwGetSurface5(fp: idFile, cksize: Int, obj: lwObject): lwSurface? {
        val surf: lwSurface
        var tex = lwTexture()
        var shdr = lwPlugin()
        val s = arrayOfNulls<String?>(1)
        val v = FloatArray(3)
        var id: Int
        var flags: Int
        var sz: Int
        val pos: Int
        var rlen: Int
        var i = 0


        /* allocate the Surface structure */surf = lwSurface()

        /* non-zero defaults */surf.color.rgb[0] = 0.78431f
        surf.color.rgb[1] = 0.78431f
        surf.color.rgb[2] = 0.78431f
        surf.diffuse.`val` = 1.0f
        surf.glossiness.`val` = 0.4f
        surf.bump.`val` = 1.0f
        surf.eta.`val` = 1.0f
        surf.sideflags = 1

        /* remember where we started */set_flen(0)
        pos = fp.Tell()

        /* name */surf.name = getS0(fp)

        /* first subchunk header */id = getU4(fp)
        sz = getU2(fp)
        if (0 > get_flen()) {
            if (surf != null) {
                lwFreeSurface.getInstance().run(surf)
            }
            return null
        }

        /* process subchunks as they're encountered */while (true) {
            sz = (sz + (sz and 1))
            set_flen(0)
            when (id) {
                ID_COLR -> {
                    surf.color.rgb[0] = getU1(fp).code.toFloat() / 255.0f
                    surf.color.rgb[1] = getU1(fp).code.toFloat() / 255.0f
                    surf.color.rgb[2] = getU1(fp).code.toFloat() / 255.0f
                }

                ID_FLAG -> {
                    flags = getU2(fp)
                    if (flags and 4 == 4) {
                        surf.smooth = 1.56207f
                    }
                    if (flags and 8 == 8) {
                        surf.color_hilite.`val` = 1.0f
                    }
                    if (flags and 16 == 16) {
                        surf.color_filter.`val` = 1.0f
                    }
                    if (flags and 128 == 128) {
                        surf.dif_sharp.`val` = 0.5f
                    }
                    if (flags and 256 == 256) {
                        surf.sideflags = 3
                    }
                    if (flags and 512 == 512) {
                        surf.add_trans.`val` = 1.0f
                    }
                }

                ID_LUMI -> surf.luminosity.`val` = getI2(fp) / 256.0f
                ID_VLUM -> surf.luminosity.`val` = getF4(fp)
                ID_DIFF -> surf.diffuse.`val` = getI2(fp) / 256.0f
                ID_VDIF -> surf.diffuse.`val` = getF4(fp)
                ID_SPEC -> surf.specularity.`val` = getI2(fp) / 256.0f
                ID_VSPC -> surf.specularity.`val` = getF4(fp)
                ID_GLOS -> surf.glossiness.`val` =
                    ln(getU2(fp).toFloat()) / 20.7944f

                ID_SMAN -> surf.smooth = getF4(fp)
                ID_REFL -> surf.reflection.`val`.`val` = getI2(fp) / 256.0f
                ID_RFLT -> surf.reflection.options = getU2(fp)
                ID_RIMG -> {
                    s[0] = getS0(fp)
                    run {
                        val nclips = intArrayOf(obj.nclips)
                        surf.reflection.cindex = add_clip(s, obj.clip, nclips)
                        obj.nclips = nclips[0]
                    }
                    surf.reflection.options = 3
                }

                ID_RSAN -> surf.reflection.seam_angle = getF4(fp)
                ID_TRAN -> surf.transparency.`val`.`val` = getI2(fp) / 256.0f
                ID_RIND -> surf.eta.`val` = getF4(fp)
                ID_BTEX -> {
                    s[0] = String(getbytes(fp, sz)!!)
                    tex = get_texture(s[0]!!)
                    surf.bump.tex = lwListAdd(surf.bump.tex, tex)!!
                }

                ID_CTEX -> {
                    s[0] = String(getbytes(fp, sz)!!)
                    tex = get_texture(s[0]!!)
                    surf.color.tex = lwListAdd(surf.color.tex, tex)!!
                }

                ID_DTEX -> {
                    s[0] = String(getbytes(fp, sz)!!)
                    tex = get_texture(s[0]!!)
                    surf.diffuse.tex = lwListAdd(surf.diffuse.tex, tex)!!
                }

                ID_LTEX -> {
                    s[0] = String(getbytes(fp, sz)!!)
                    tex = get_texture(s[0]!!)
                    surf.luminosity.tex = lwListAdd(surf.luminosity.tex, tex)!!
                }

                ID_RTEX -> {
                    s[0] = String(getbytes(fp, sz)!!)
                    tex = get_texture(s[0]!!)
                    surf.reflection.`val`.tex = lwListAdd(surf.reflection.`val`.tex, tex)!!
                }

                ID_STEX -> {
                    s[0] = String(getbytes(fp, sz)!!)
                    tex = get_texture(s[0]!!)
                    surf.specularity.tex = lwListAdd(surf.specularity.tex, tex)!!
                }

                ID_TTEX -> {
                    s[0] = String(getbytes(fp, sz)!!)
                    tex = get_texture(s[0]!!)
                    surf.transparency.`val`.tex = lwListAdd(surf.transparency.`val`.tex, tex)!!
                }

                ID_TFLG -> {
                    flags = getU2(fp)
                    i = 0
                    if (flags and 2 == 2) {
                        i = 1
                    }
                    if (flags and 4 == 4) {
                        i = 2
                    }
                    tex.axis = i
                    if (tex.type == ID_IMAP.toLong()) {
                        tex.param.imap.axis = i
                    } else {
                        tex.param.proc.axis = i
                    }
                    if (flags and 8 == 8) {
                        tex.tmap.coord_sys = 1
                    }
                    if (flags and 16 == 16) {
                        tex.negative = 1
                    }
                    if (flags and 32 == 32) {
                        tex.param.imap.pblend = 1
                    }
                    if (flags and 64 == 64) {
                        tex.param.imap.aa_strength = 1.0f
                        tex.param.imap.aas_flags = 1
                    }
                }

                ID_TSIZ -> {
                    i = 0
                    while (i < 3) {
                        tex.tmap.size.`val`[i] = getF4(fp)
                        i++
                    }
                }

                ID_TCTR -> {
                    i = 0
                    while (i < 3) {
                        tex.tmap.center.`val`[i] = getF4(fp)
                        i++
                    }
                }

                ID_TFAL -> {
                    i = 0
                    while (i < 3) {
                        tex.tmap.falloff.`val`[i] = getF4(fp)
                        i++
                    }
                }

                ID_TVEL -> {
                    i = 0
                    while (i < 3) {
                        v[i] = getF4(fp)
                        i++
                    }
                    run {
                        val nenvs = intArrayOf(obj.nenvs)
                        tex.tmap.center.eindex = add_tvel(tex.tmap.center.`val`, v, obj.env, nenvs)
                        obj.nenvs = nenvs[0]
                    }
                }

                ID_TCLR -> if (tex.type == ID_PROC.toLong()) {
                    i = 0
                    while (i < 3) {
                        tex.param.proc.value[i] = getU1(fp).code.toFloat() / 255.0f
                        i++
                    }
                }

                ID_TVAL -> tex.param.proc.value[0] = getI2(fp) / 256.0f
                ID_TAMP -> if (tex.type == ID_IMAP.toLong()) {
                    tex.param.imap.amplitude.`val` = getF4(fp)
                }

                ID_TIMG -> {
                    s[0] = getS0(fp)
                    run {
                        val nClips = intArrayOf(obj.nclips)
                        tex.param.imap.cindex = add_clip(s, obj.clip, nClips)
                        obj.nclips = nClips[0]
                    }
                }

                ID_TAAS -> {
                    tex.param.imap.aa_strength = getF4(fp)
                    tex.param.imap.aas_flags = 1
                }

                ID_TREF -> tex.tmap.ref_object = String(getbytes(fp, sz)!!)
                ID_TOPC -> tex.opacity.`val` = getF4(fp)
                ID_TFP0 -> if (tex.type == ID_IMAP.toLong()) {
                    tex.param.imap.wrapw.`val` = getF4(fp)
                }

                ID_TFP1 -> if (tex.type == ID_IMAP.toLong()) {
                    tex.param.imap.wraph.`val` = getF4(fp)
                }

                ID_SHDR -> {
                    shdr = lwPlugin()
                    shdr.name = String(getbytes(fp, sz)!!)
                    surf.shader = lwListAdd(surf.shader, shdr)!!
                    surf.nshaders++
                }

                ID_SDAT -> shdr.data = getbytes(fp, sz)
                else -> {}
            }

            /* error while reading current subchunk? */rlen = get_flen()
            if (rlen < 0 || rlen > sz) {
                if (surf != null) {
                    lwFreeSurface.getInstance().run(surf)
                }
                return null
            }

            /* skip unread parts of the current subchunk */if (rlen < sz) {
                fp.Seek((sz - rlen).toLong(), fsOrigin_t.FS_SEEK_CUR)
            }

            /* end of the SURF chunk? */if (cksize <= fp.Tell() - pos) {
                break
            }

            /* get the next subchunk header */set_flen(0)
            id = getU4(fp)
            sz = getU2(fp)
            if (6 != get_flen()) {
                if (surf != null) {
                    lwFreeSurface.getInstance().run(surf)
                }
                return null
            }
        }
        return surf

    }

    /*
     ======================================================================
     lwGetPolygons5()

     Read polygon records from a POLS chunk in an LWOB file.  The polygons
     are added to the array in the lwPolygonList.
     ====================================================================== */
    fun lwGetPolygons5(fp: idFile, cksize: Int, plist: lwPolygonList, ptoffset: Int): Boolean {
        var pp: lwPolygon
        val buf: ByteBuffer?
        var i: Int
        var j: Int
        var nv: Int
        var nverts: Int
        var npols: Int
        var p: Int
        var v: Int
        if (cksize == 0) {
            return true
        }

        /* read the whole chunk */set_flen(0)
        buf = ByteBuffer.wrap(getbytes(fp, cksize))

        if (null == buf) {
            lwFreePolygons(plist)
            return false
        }

        /* count the polygons and vertices */nverts = 0
        npols = 0
        while (buf.position() < cksize) {
            nv = sgetU2(buf)
            nverts += nv
            npols++
            buf.position(buf.position() + 2 * nv)
            i = sgetI2(buf).toInt()
            if (i < 0) {
                buf.position(buf.position() + 2) // detail polygons
            }
        }
        if (!lwAllocPolygons(plist, npols, nverts)) {
            lwFreePolygons(plist)
            return false
        }

        /* fill in the new polygons */
        pp = plist.pol!![plist.offset.also { p = it }]!!
        v = plist.voffset
        i = 0
        while (i < npols) {
            nv = sgetU2(buf)
            pp.nverts = nv
            pp.type = ID_FACE.toLong()
            if (null == pp.v) {
                pp.setV(plist.pol!![0]!!.v, v)
            }
            j = 0
            while (j < nv) {
                plist.pol!![0]!!.getV(v + j)!!.index = sgetU2(buf) + ptoffset
                j++
            }
            j = sgetI2(buf).toInt()
            if (j < 0) {
                j = -j
                buf.position(buf.position() + 2)
            }
            j -= 1
            pp.surf = lwNode.getPosition(pp.surf, j) as lwSurface?
            pp = plist.pol!![p++]!!
            v += nv
            i++
        }

        return true

    }

    /*
     ======================================================================
     getLWObject5()

     Returns the contents of an LWOB, given its filename, or null if the
     file couldn't be loaded.  On failure, failID and failpos can be used
     to diagnose the cause.

     1.  If the file isn't an LWOB, failpos will contain 12 and failID will
     be unchanged.

     2.  If an error occurs while reading an LWOB, failID will contain the
     most recently read IFF chunk ID, and failpos will contain the
     value returned by fp.Tell() at the time of the failure.

     3.  If the file couldn't be opened, or an error occurs while reading
     the first 12 bytes, both failID and failpos will be unchanged.

     If you don't need this information, failID and failpos can be null.
     ====================================================================== */
    fun lwGetObject5(filename: String, failID: IntArray, failpos: IntArray): lwObject? {
        var fp: idFile?
        val `object`: lwObject
        val layer: lwLayer
        var node: lwNode?
        var id: Int
        val formsize: Int
        val type: Int
        var cksize: Int


        /* open the file */

        /* read the first 12 bytes */fp = FileSystem_h.fileSystem.OpenFileRead(filename)
        if (null == fp) {
            return null
        }
        set_flen(0)
        id = getU4(fp)
        formsize = getU4(fp)
        type = getU4(fp)
        if (12 != get_flen()) {
            FileSystem_h.fileSystem.CloseFile(fp)
            return null
        }

        /* LWOB? */if (id != ID_FORM || type != ID_LWOB) {
            FileSystem_h.fileSystem.CloseFile(fp)
            if (failpos != null) {
                failpos[0] = 12
            }
            return null
        }
        /* allocate an object and a default layer */
        `object` = lwObject()
        layer = lwLayer()
        `object`.layer = layer
        `object`.nlayers = 1

        /* get the first chunk header */id = getU4(fp)
        cksize = getU4(fp)
        if (0 > get_flen()) {
            return gotoFail2(failID, id, fp, failpos)
        }

        /* process chunks as they're encountered */while (true) {
            cksize += cksize and 1
            when (id) {
                ID_PNTS -> if (!lwGetPoints(fp, cksize, layer.point)) {
                    return gotoFail2(failID, id, fp, failpos)
                }

                ID_POLS -> if (!lwGetPolygons5(
                        fp, cksize, layer.polygon,
                        layer.point.offset
                    )
                ) {
                    return gotoFail2(failID, id, fp, failpos)
                }

                ID_SRFS -> if (!lwGetTags(fp, cksize, `object`.taglist)) {
                    return gotoFail2(failID, id, fp, failpos)
                }

                ID_SURF -> {
                    node = lwGetSurface5(fp, cksize, `object`)
                    if (null == node) {
                        return gotoFail2(failID, id, fp, failpos)
                    }
                    `object`.surf = lwListAdd(`object`.surf, node)
                    `object`.nsurfs++
                }

                else -> fp.Seek(cksize.toLong(), fsOrigin_t.FS_SEEK_CUR)
            }

            /* end of the file? */if (formsize <= fp.Tell() - 8) {
                break
            }

            /* get the next chunk header */set_flen(0)
            id = getU4(fp)
            cksize = getU4(fp)
            if (8 != get_flen()) {
                return gotoFail2(failID, id, fp, failpos)
            }
        }
        FileSystem_h.fileSystem.CloseFile(fp)
        fp = null
        lwGetBoundingBox(layer.point, layer.bbox)
        lwGetPolyNormals(layer.point, layer.polygon)
        if (!lwGetPointPolygons(layer.point, layer.polygon)) {
            return gotoFail2(failID, id, fp, failpos)
        }
        if (!lwResolvePolySurfaces(layer.polygon, `object`)) {
            return gotoFail2(failID, id, fp, failpos)
        }
        lwGetVertNormals(layer.point, layer.polygon)
        return `object`
    }

    fun gotoFail2(failID: IntArray, id: Int, fp: idFile?, failpos: IntArray): lwObject? {
        if (failID != null) {
            failID[0] = id
        }
        if (fp != null) {
            if (failpos != null) {
                failpos[0] = fp.Tell()
            }
            FileSystem_h.fileSystem.CloseFile(fp)
        }
        return null
    }

    /*
     ======================================================================
     lwFreePoints()

     Free the memory used by an lwPointList.
     ====================================================================== */
    fun lwFreePoints(point: lwPointList?) {
        if (point != null) {
            if (point.pt != null) {
                point.pt = null
            }
        }
    }

    /*
     ======================================================================
     lwFreePolygons()

     Free the memory used by an lwPolygonList.
     ====================================================================== */
    fun lwFreePolygons(plist: lwPolygonList?) {
        if (plist != null) {
            if (plist.pol != null) {
                plist.pol = null
            }
        }
    }

    /*
     ======================================================================
     lwGetPoints()

     Read point records from a PNTS chunk in an LWO2 file.  The points are
     added to the array in the lwPointList.
     ====================================================================== */
    fun lwGetPoints(fp: idFile, cksize: Int, point: lwPointList): Boolean {
        val f: ByteBuffer?
        val np: Int
        var i: Int
        var j: Int
        if (cksize == 1) {
            return true
        }

        /* extend the point array to hold the new points */np = cksize / 12
        point.offset = point.count
        point.count += np
        var oldpt = point.pt
        point.pt = arrayOfNulls(point.count)

        if (null == point.pt) {
            return false
        }
        if (oldpt != null) {
            System.arraycopy(oldpt, 0, point.pt, 0, point.offset)
            oldpt = null
        }
        for (n in point.offset until point.offset + np) {
            point.pt!![n] = lwPoint()
        }

        /* read the whole chunk */f = ByteBuffer.wrap(getbytes(fp, cksize))
        if (null == f) {
            return false
        }
        BigRevBytes(f, np * 3)

        /* assign position values */i = 0
        j = 0
        while (i < np) {
            point.pt!![i]!!.pos[0] = f.float
            point.pt!![i]!!.pos[1] = f.float
            point.pt!![i]!!.pos[2] = f.float
            i++
            j += 3
        }

        return true
    }

    /*
     ======================================================================
     lwGetBoundingBox()

     Calculate the bounding box for a point list, but only if the bounding
     box hasn't already been initialized.
     ====================================================================== */
    fun lwGetBoundingBox(point: lwPointList, bbox: FloatArray) {
        var i: Int
        var j: Int
        if (point.count == 0) {
            return
        }
        i = 0
        while (i < 6) {
            if (bbox[i] != 0.0f) {
                return
            }
            i++
        }
        bbox[2] = 1e20f
        bbox[1] = bbox[2]
        bbox[0] = bbox[1]
        bbox[5] = -1e20f
        bbox[4] = bbox[5]
        bbox[3] = bbox[4]
        i = 0
        while (i < point.count) {
            j = 0
            while (j < 3) {
                if (bbox[j] > point.pt!![i]!!.pos[j]) {
                    bbox[j] = point.pt!![i]!!.pos[j]
                }
                if (bbox[j + 3] < point.pt!![i]!!.pos[j]) {
                    bbox[j + 3] = point.pt!![i]!!.pos[j]
                }
                j++
            }
            i++
        }
    }

    /*
     ======================================================================
     lwAllocPolygons()

     Allocate or extend the polygon arrays to hold new records.
     ====================================================================== */
    fun lwAllocPolygons(plist: lwPolygonList, npols: Int, nverts: Int): Boolean {
        var i: Int
        plist.offset = plist.count
        plist.count += npols
        var oldpol = plist.pol
        plist.pol = arrayOfNulls(plist.count)
        if (oldpol != null) {
            System.arraycopy(oldpol, 0, plist.pol, 0, plist.offset)
            oldpol = null
        }
        i = 0
        while (i < npols) {
            plist.pol!![plist.offset + i] = lwPolygon()
            i++
        }
        plist.voffset = plist.vcount
        plist.vcount += nverts
        var oldpolv = plist.pol!![0]!!.v
        plist.pol!![0]!!.v = arrayOfNulls(plist.vcount)
        if (oldpolv != null) {
            System.arraycopy(oldpolv, 0, plist.pol!![0]!!.v, 0, plist.voffset)
            oldpolv = null
        }
        i = 0
        while (i < nverts) {
            plist.pol!![0]!!.v!![plist.voffset + i] = lwPolVert()
            i++
        }

        /* fix up the old vertex pointers */i = 1
        while (i < plist.offset) {
            for (j in plist.pol!![i]!!.v!!.indices) {
                plist.pol!![i]!!.v!![j] = lwPolVert()
            }
            i++
        }
        return true
    }

    /*
     ======================================================================
     lwGetPolygons()

     Read polygon records from a POLS chunk in an LWO2 file.  The polygons
     are added to the array in the lwPolygonList.
     ====================================================================== */
    fun lwGetPolygons(fp: idFile, cksize: Int, plist: lwPolygonList, ptoffset: Int): Boolean {
        var pp: lwPolygon?
        var buf: ByteBuffer?
        var i: Int
        var j: Int
        var flags: Int
        var nv: Int
        var nverts: Int
        var npols: Int
        var p: Int
        var v: Int
        val type: Int
        if (cksize == 0) {
            return true
        }

        /* read the whole chunk */set_flen(0)
        type = getU4(fp)
        buf = ByteBuffer.wrap(getbytes(fp, cksize - 4))

        if (cksize != get_flen()) {
            return gotoFreePolygon(plist)
        }

        /* count the polygons and vertices */nverts = 0
        npols = 0
        while (buf.hasRemaining()) {
            nv = sgetU2(buf)
            nv = nv and 0x03FF
            nverts += nv
            npols++
            i = 0
            while (i < nv) {
                j = sgetVX(buf)
                i++
            }
        }
        if (!lwAllocPolygons(plist, npols, nverts)) {
            return gotoFreePolygon(plist)
        }

        /* fill in the new polygons */buf.rewind()
        p = plist.offset
        v = plist.voffset
        i = 0
        while (i < npols) {
            nv = sgetU2(buf)
            flags = nv and 0xFC00
            nv = nv and 0x03FF
            pp = plist.pol!![p++]!!
            pp.nverts = nv
            pp.flags = flags
            pp.type = type.toLong()
            if (null == pp.v) {
                pp.setV(plist.pol!![0]!!.v, v)
            }
            j = 0
            while (j < nv) {
                pp.getV(j)!!.index = sgetVX(buf) + ptoffset
                j++
            }

            v += nv
            i++
        }
        buf = null
        return true
    }

    fun gotoFreePolygon(list: lwPolygonList): Boolean {
        lwFreePolygons(list)
        return false
    }

    /*
     ======================================================================
     lwGetPolyNormals()

     Calculate the polygon normals.  By convention, LW's polygon normals
     are found as the cross product of the first and last edges.  It's
     undefined for one- and two-point polygons.
     ====================================================================== */
    fun lwGetPolyNormals(point: lwPointList, polygon: lwPolygonList) {
        var i: Int
        var j: Int
        val p1 = FloatArray(3)
        val p2 = FloatArray(3)
        val pn = FloatArray(3)
        val v1 = FloatArray(3)
        val v2 = FloatArray(3)
        i = 0
        while (i < polygon.count) {
            if (polygon.pol!![i]!!.nverts < 3) {
                i++
                continue
            }
            j = 0
            while (j < 3) {


                // FIXME: track down why indexes are way out of range
                p1[j] = point.pt!![polygon.pol!![i]!!.getV(0)!!.index]!!.pos[j]
                p2[j] = point.pt!![polygon.pol!![i]!!.getV(1)!!.index]!!.pos[j]
                pn[j] = point.pt!![polygon.pol!![i]!!.getV(polygon.pol!![i]!!.nverts - 1)!!.index]!!.pos[j]
                j++
            }
            j = 0
            while (j < 3) {
                v1[j] = p2[j] - p1[j]
                v2[j] = pn[j] - p1[j]
                j++
            }
            cross(v1, v2, polygon.pol!![i]!!.norm)
            normalize(polygon.pol!![i]!!.norm)
            i++
        }
    }

    /*
     ======================================================================
     lwGetPointPolygons()

     For each point, fill in the indexes of the polygons that share the
     point.  Returns 0 if any of the memory allocations fail, otherwise
     returns 1.
     ====================================================================== */
    fun lwGetPointPolygons(point: lwPointList, polygon: lwPolygonList): Boolean {
        var i: Int
        var j: Int
        var k: Int

        /* count the number of polygons per point */i = 0
        while (i < polygon.count) {
            j = 0
            while (j < polygon.pol!![i]!!.nverts) {
                ++point.pt!![polygon.pol!![i]!!.getV(j)!!.index]!!.npols
                j++
            }
            i++
        }

        /* alloc per-point polygon arrays */i = 0
        while (i < point.count) {
            if (point.pt!![i]!!.npols == 0) {
                i++
                continue
            }
            point.pt!![i]!!.pol = IntArray(point.pt!![i]!!.npols)
            point.pt!![i]!!.npols = 0
            i++
        }

        /* fill in polygon array for each point */i = 0
        while (i < polygon.count) {
            j = 0
            while (j < polygon.pol!![i]!!.nverts) {
                k = polygon.pol!![i]!!.getV(j)!!.index
                point.pt!![k]!!.pol!![point.pt!![k]!!.npols] = i
                ++point.pt!![k]!!.npols
                j++
            }
            i++
        }
        return true
    }

    /*
     ======================================================================
     lwResolvePolySurfaces()

     Convert tag indexes into actual lwSurface pointers.  If any polygons
     point to tags for which no corresponding surface can be found, a
     default surface is created.
     ====================================================================== */
    fun lwResolvePolySurfaces(polygon: lwPolygonList, `object`: lwObject): Boolean {
        val s: Array<lwSurface?>
        var st: lwSurface?
        var i: Int
        var index: Int
        val tlist = `object`.taglist
        var surf = `object`.surf
        if (tlist.count == 0) {
            return true
        }
        s = arrayOfNulls(tlist.count)
        i = 0
        while (i < tlist.count) {
            st = surf
            while (st != null) {
                if (st.name != null && st.name == tlist.tag!![i]) {
                    s[i] = st
                    break
                }
                st = st.next
            }
            i++
        }
        i = 0
        while (i < polygon.count) {
            index = polygon.pol!![i]!!.part
            if (index < 0 || index > tlist.count) {
                return false
            }
            if (null == s[index]) {
                s[index] = lwDefaultSurface()
                if (null == s.getOrNull(index)) {
                    return false
                }
                s[index]!!.name = ""
                s[index]!!.name = tlist.tag!![index]
                surf = lwListAdd(surf, s[index])
                `object`.nsurfs++
            }
            polygon.pol!![i]!!.surf = s[index]
            i++
        }
        return true
    }

    /*
     ======================================================================
     lwGetVertNormals()

     Calculate the vertex normals.  For each polygon vertex, sum the
     normals of the polygons that share the point.  If the normals of the
     current and adjacent polygons form an angle greater than the max
     smoothing angle for the current polygon's surface, the normal of the
     adjacent polygon is excluded from the sum.  It's also excluded if the
     polygons aren't in the same smoothing group.

     Assumes that lwGetPointPolygons(), lwGetPolyNormals() and
     lwResolvePolySurfaces() have already been called.
     ====================================================================== */
    fun lwGetVertNormals(point: lwPointList, polygon: lwPolygonList) {
        var j: Int
        var k: Int
        var n: Int
        var g: Int
        var h: Int
        var p: Int
        var a: Float
        j = 0
        while (j < polygon.count) {
            n = 0
            while (n < polygon.pol!![j]!!.nverts) {
                k = 0
                while (k < 3) {
                    polygon.pol!![j]!!.getV(n)!!.norm[k] = polygon.pol!![j]!!.norm[k]
                    k++
                }
                if (polygon.pol!![j]!!.surf!!.smooth <= 0) {
                    n++
                    continue
                }
                p = polygon.pol!![j]!!.getV(n)!!.index
                g = 0
                while (g < point.pt!![p]!!.npols) {
                    h = point.pt!![p]!!.pol!![g]
                    if (h == j) {
                        g++
                        continue
                    }
                    if (polygon.pol!![j]!!.smoothgrp != polygon.pol!![h]!!.smoothgrp) {
                        g++
                        continue
                    }
                    a = idMath.ACos(dot(polygon.pol!![j]!!.norm, polygon.pol!![h]!!.norm))
                    if (a > polygon.pol!![j]!!.surf!!.smooth) {
                        g++
                        continue
                    }
                    k = 0
                    while (k < 3) {
                        polygon.pol!![j]!!.getV(n)!!.norm[k] += polygon.pol!![h]!!.norm[k]
                        k++
                    }
                    g++
                }
                normalize(polygon.pol!![j]!!.getV(n)!!.norm)
                n++
            }
            j++
        }
    }

    /*
     ======================================================================
     lwFreeTags()

     Free memory used by an lwTagList.
     ====================================================================== */
    fun lwFreeTags(tlist: lwTagList?) {
        if (tlist != null) {
            if (tlist.tag != null) {
                tlist.tag = null
            }
        }
    }

    /*
     ======================================================================
     lwGetTags()

     Read tag strings from a TAGS chunk in an LWO2 file.  The tags are
     added to the lwTagList array.
     ====================================================================== */
    fun lwGetTags(fp: idFile, ckSize: Int, tList: lwTagList): Boolean {
        val buf: ByteBuffer?
        val nTags: Int
        val bp: String
        val tags: Array<String?>
        if (ckSize == 0) {
            return true
        }

        /* read the whole chunk */
        set_flen(0)
        buf = ByteBuffer.wrap(getbytes(fp, ckSize))
        if (null == buf) {
            return false
        }

        /* count the strings */
        bp = String(buf.array())
        val split = bp.split("\u0000").stream().filter { it.isNotEmpty() }.collect(Collectors.toList())
        tags = split.toTypedArray()
        nTags = tags.size

        /* expand the string array to hold the new tags */tList.offset = tList.count
        tList.count += nTags
        val oldtag = tList.tag
        tList.tag = arrayOfNulls(tList.count)
        if (tList.count == 0) {
            return false
        }
        if (oldtag != null) {
            System.arraycopy(oldtag, 0, tList.tag, 0, tList.offset)
        }

        /* copy the new tags to the tag array */

        /* copy the new tags to the tag array */
        System.arraycopy(tags, 0, tList.tag, tList.offset, nTags)

        return true
    }

    /*
     ======================================================================
     lwGetPolygonTags()

     Read polygon tags from a PTAG chunk in an LWO2 file.
     ====================================================================== */
    fun lwGetPolygonTags(fp: idFile, cksize: Int, tlist: lwTagList, plist: lwPolygonList): Boolean {
        val type: Int
        var rlen = 0
        var i: Int
        var j: Int
        val nodeMap: MutableMap<Int, lwNode> = HashMap(2)
        set_flen(0)
        type = getU4(fp)
        rlen = get_flen()
        if (rlen < 0) {
            return false
        }
        if (type != ID_SURF && type != ID_PART && type != ID_SMGP) {
            fp.Seek((cksize - 4).toLong(), fsOrigin_t.FS_SEEK_CUR)
            return true
        }
        while (rlen < cksize) {
            i = getVX(fp) + plist.offset
            j = getVX(fp) + tlist.offset
            rlen = get_flen()
            if (rlen < 0 || rlen > cksize) {
                return false
            }

            //add static reference if it doesthnt exist.
            if (!nodeMap.containsKey(j)) {
                nodeMap[j] = lwSurface()
            }
            when (type) {
                ID_SURF -> {
                    plist.pol!![i]!!.surf = nodeMap[j] as lwSurface?
                    plist.pol!![i]!!.part = j
                }

                ID_PART -> plist.pol!![i]!!.part = j
                ID_SMGP -> plist.pol!![i]!!.smoothgrp = j
            }
        }
        return true
    }

    /*
     ======================================================================
     lwGetTHeader()

     Read a texture map header from a SURF.BLOK in an LWO2 file.  This is
     the first subchunk in a BLOK, and its contents are common to all three
     texture types.
     ====================================================================== */
    fun lwGetTHeader(fp: idFile, hsz: Int, tex: lwTexture): Int {
        var id: Int
        var sz: Int
        val pos: Int
        var rlen: Int


        /* remember where we started */set_flen(0)
        pos = fp.Tell()

        /* ordinal string */tex.ord = getS0(fp)

        /* first subchunk header */id = getU4(fp)
        sz = getU2(fp)
        if (0 > get_flen()) {
            return 0
        }

        /* process subchunks as they're encountered */while (true) {
            sz = (sz + (sz and 1))
            set_flen(0)
            when (id) {
                ID_CHAN -> tex.chan = getU4(fp).toLong()
                ID_OPAC -> {
                    tex.opac_type = getU2(fp)
                    tex.opacity.`val` = getF4(fp)
                    tex.opacity.eindex = getVX(fp)
                }

                ID_ENAB -> tex.enabled = getU2(fp)
                ID_NEGA -> tex.negative = getU2(fp)
                ID_AXIS -> tex.axis = getU2(fp)
                else -> {}
            }

            /* error while reading current subchunk? */rlen = get_flen()
            if (rlen < 0 || rlen > sz) {
                return 0
            }

            /* skip unread parts of the current subchunk */if (rlen < sz) {
                fp.Seek((sz - rlen).toLong(), fsOrigin_t.FS_SEEK_CUR)
            }

            /* end of the texture header subchunk? */if (hsz <= fp.Tell() - pos) {
                break
            }

            /* get the next subchunk header */set_flen(0)
            id = getU4(fp)
            sz = getU2(fp)
            if (6 != get_flen()) {
                return 0
            }
        }
        set_flen(fp.Tell() - pos)
        return 1
    }

    /*
     ======================================================================
     lwGetTMap()

     Read a texture map from a SURF.BLOK in an LWO2 file.  The TMAP
     defines the mapping from texture to world or object coordinates.
     ====================================================================== */
    fun lwGetTMap(fp: idFile, tmapsz: Int, tmap: lwTMap): Int {
        var id: Int
        var sz: Int
        var rlen: Int
        val pos: Int
        var i: Int
        pos = fp.Tell()
        id = getU4(fp)
        sz = getU2(fp)
        if (0 > get_flen()) {
            return 0
        }
        while (true) {
            sz = (sz + (sz and 1))
            set_flen(0)
            when (id) {
                ID_SIZE -> {
                    i = 0
                    while (i < 3) {
                        tmap.size.`val`[i] = getF4(fp)
                        i++
                    }
                    tmap.size.eindex = getVX(fp)
                }

                ID_CNTR -> {
                    i = 0
                    while (i < 3) {
                        tmap.center.`val`[i] = getF4(fp)
                        i++
                    }
                    tmap.center.eindex = getVX(fp)
                }

                ID_ROTA -> {
                    i = 0
                    while (i < 3) {
                        tmap.rotate.`val`[i] = getF4(fp)
                        i++
                    }
                    tmap.rotate.eindex = getVX(fp)
                }

                ID_FALL -> {
                    tmap.fall_type = getU2(fp)
                    i = 0
                    while (i < 3) {
                        tmap.falloff.`val`[i] = getF4(fp)
                        i++
                    }
                    tmap.falloff.eindex = getVX(fp)
                }

                ID_OREF -> tmap.ref_object = getS0(fp)
                ID_CSYS -> tmap.coord_sys = getU2(fp)
                else -> {}
            }

            /* error while reading the current subchunk? */rlen = get_flen()
            if (rlen < 0 || rlen > sz) {
                return 0
            }

            /* skip unread parts of the current subchunk */if (rlen < sz) {
                fp.Seek((sz - rlen).toLong(), fsOrigin_t.FS_SEEK_CUR)
            }

            /* end of the TMAP subchunk? */if (tmapsz <= fp.Tell() - pos) {
                break
            }

            /* get the next subchunk header */set_flen(0)
            id = getU4(fp)
            sz = getU2(fp)
            if (6 != get_flen()) {
                return 0
            }
        }
        set_flen(fp.Tell() - pos)
        return 1
    }

    /*
     ======================================================================
     lwGetImageMap()

     Read an lwImageMap from a SURF.BLOK in an LWO2 file.
     ====================================================================== */
    fun lwGetImageMap(fp: idFile, rsz: Int, tex: lwTexture): Int {
        var id: Int
        var sz: Int
        var rlen: Int
        val pos: Int
        pos = fp.Tell()
        id = getU4(fp)
        sz = getU2(fp)
        if (0 > get_flen()) {
            return 0
        }
        while (true) {
            sz = (sz + (sz and 1))
            set_flen(0)
            when (id) {
                ID_TMAP -> if (0 == lwGetTMap(fp, sz, tex.tmap)) {
                    return 0
                }

                ID_PROJ -> tex.param.imap.projection = getU2(fp)
                ID_VMAP -> tex.param.imap.vmap_name = getS0(fp)
                ID_AXIS -> tex.param.imap.axis = getU2(fp)
                ID_IMAG -> tex.param.imap.cindex = getVX(fp)
                ID_WRAP -> {
                    tex.param.imap.wrapw_type = getU2(fp)
                    tex.param.imap.wraph_type = getU2(fp)
                }

                ID_WRPW -> {
                    tex.param.imap.wrapw.`val` = getF4(fp)
                    tex.param.imap.wrapw.eindex = getVX(fp)
                }

                ID_WRPH -> {
                    tex.param.imap.wraph.`val` = getF4(fp)
                    tex.param.imap.wraph.eindex = getVX(fp)
                }

                ID_AAST -> {
                    tex.param.imap.aas_flags = getU2(fp)
                    tex.param.imap.aa_strength = getF4(fp)
                }

                ID_PIXB -> tex.param.imap.pblend = getU2(fp)
                ID_STCK -> {
                    tex.param.imap.stck.`val` = getF4(fp)
                    tex.param.imap.stck.eindex = getVX(fp)
                }

                ID_TAMP -> {
                    tex.param.imap.amplitude.`val` = getF4(fp)
                    tex.param.imap.amplitude.eindex = getVX(fp)
                }

                else -> {}
            }

            /* error while reading the current subchunk? */rlen = get_flen()
            if (rlen < 0 || rlen > sz) {
                return 0
            }

            /* skip unread parts of the current subchunk */if (rlen < sz) {
                fp.Seek((sz - rlen).toLong(), fsOrigin_t.FS_SEEK_CUR)
            }

            /* end of the image map? */if (rsz <= fp.Tell() - pos) {
                break
            }

            /* get the next subchunk header */set_flen(0)
            id = getU4(fp)
            sz = getU2(fp)
            if (6 != get_flen()) {
                return 0
            }
        }
        set_flen(fp.Tell() - pos)
        return 1
    }

    /*
     ======================================================================
     lwGetProcedural()

     Read an lwProcedural from a SURF.BLOK in an LWO2 file.
     ====================================================================== */
    fun lwGetProcedural(fp: idFile, rsz: Int, tex: lwTexture): Int {
        var id: Int
        var sz: Int
        var rlen: Int
        val pos: Int
        pos = fp.Tell()
        id = getU4(fp)
        sz = getU2(fp)
        if (0 > get_flen()) {
            return 0
        }
        while (true) {
            sz = (sz + (sz and 1))
            set_flen(0)
            when (id) {
                ID_TMAP -> if (0 == lwGetTMap(fp, sz, tex.tmap)) {
                    return 0
                }

                ID_AXIS -> tex.param.proc.axis = getU2(fp)
                ID_VALU -> {
                    tex.param.proc.value[0] = getF4(fp)
                    if (sz >= 8) {
                        tex.param.proc.value[1] = getF4(fp)
                    }
                    if (sz >= 12) {
                        tex.param.proc.value[2] = getF4(fp)
                    }
                }

                ID_FUNC -> {
                    tex.param.proc.name = getS0(fp)
                    rlen = get_flen()
                    tex.param.proc.data = getbytes(fp, sz - rlen)
                }

                else -> {}
            }

            /* error while reading the current subchunk? */rlen = get_flen()
            if (rlen < 0 || rlen > sz) {
                return 0
            }

            /* skip unread parts of the current subchunk */if (rlen < sz) {
                fp.Seek((sz - rlen).toLong(), fsOrigin_t.FS_SEEK_CUR)
            }

            /* end of the procedural block? */if (rsz <= fp.Tell() - pos) {
                break
            }

            /* get the next subchunk header */set_flen(0)
            id = getU4(fp)
            sz = getU2(fp)
            if (6 != get_flen()) {
                return 0
            }
        }
        set_flen(fp.Tell() - pos)
        return 1
    }

    /*
     ======================================================================
     lwGetGradient()

     Read an lwGradient from a SURF.BLOK in an LWO2 file.
     ====================================================================== */
    fun lwGetGradient(fp: idFile, rsz: Int, tex: lwTexture): Int {
        var id: Int
        var sz: Int
        var rlen: Int
        val pos: Int
        var i: Int
        var j: Int
        var nkeys: Int
        pos = fp.Tell()
        id = getU4(fp)
        sz = getU2(fp)
        if (0 > get_flen()) {
            return 0
        }
        while (true) {
            sz = (sz + (sz and 1))
            set_flen(0)
            when (id) {
                ID_TMAP -> if (0 == lwGetTMap(fp, sz, tex.tmap)) {
                    return 0
                }

                ID_PNAM -> tex.param.grad.paramname = getS0(fp)
                ID_INAM -> tex.param.grad.itemname = getS0(fp)
                ID_GRST -> tex.param.grad.start = getF4(fp)
                ID_GREN -> tex.param.grad.end = getF4(fp)
                ID_GRPT -> tex.param.grad.repeat = getU2(fp)
                ID_FKEY -> {
                    nkeys = sz
                    tex.param.grad.key = Array(nkeys) { lwGradKey() }
                    if (tex.param.grad.key!!.isEmpty()) {
                        return 0
                    }
                    i = 0
                    while (i < nkeys) {
                        tex.param.grad.key!![i].value = getF4(fp)
                        j = 0
                        while (j < 4) {
                            tex.param.grad.key!![i].rgba[j] = getF4(fp)
                            j++
                        }
                        i++
                    }
                }

                ID_IKEY -> {
                    nkeys = sz / 2
                    tex.param.grad.ikey = IntArray(nkeys)
                    if (null == tex.param.grad.ikey) {
                        return 0
                    }
                    i = 0
                    while (i < nkeys) {
                        tex.param.grad.ikey!![i] = getU2(fp)
                        i++
                    }
                }

                else -> {}
            }

            /* error while reading the current subchunk? */rlen = get_flen()
            if (rlen < 0 || rlen > sz) {
                return 0
            }

            /* skip unread parts of the current subchunk */if (rlen < sz) {
                fp.Seek((sz - rlen).toLong(), fsOrigin_t.FS_SEEK_CUR)
            }

            /* end of the gradient? */if (rsz <= fp.Tell() - pos) {
                break
            }

            /* get the next subchunk header */set_flen(0)
            id = getU4(fp)
            sz = getU2(fp)
            if (6 != get_flen()) {
                return 0
            }
        }
        set_flen(fp.Tell() - pos)
        return 1
    }

    /*
     ======================================================================
     lwGetTexture()

     Read an lwTexture from a SURF.BLOK in an LWO2 file.
     ====================================================================== */
    fun lwGetTexture(fp: idFile, bloksz: Int, type: Int): lwTexture? {
        var tex: lwTexture?
        var sz: Int
        val ok: Int
        tex = lwTexture()
        tex.type = type.toLong()
        tex.tmap.size.`val`[2] = 1.0f
        tex.tmap.size.`val`[1] = tex.tmap.size.`val`[2]
        tex.tmap.size.`val`[0] = tex.tmap.size.`val`[1]
        tex.opacity.`val` = 1.0f
        tex.enabled = 1
        sz = getU2(fp)
        if (0 == lwGetTHeader(fp, sz, tex)) {
            tex = null
            return null
        }
        sz = (bloksz - sz - 6)
        ok = when (type) {
            ID_IMAP -> lwGetImageMap(fp, sz, tex)
            ID_PROC -> lwGetProcedural(fp, sz, tex)
            ID_GRAD -> lwGetGradient(fp, sz, tex)
            else -> (fp.Seek(sz.toLong(), fsOrigin_t.FS_SEEK_CUR)).toInt()
        }
        if (0 == ok) {
            lwFreeTexture.getInstance().run(tex)
            return null
        }
        set_flen(bloksz)
        return tex
    }

    /*
     ======================================================================
     lwGetShader()

     Read a shader record from a SURF.BLOK in an LWO2 file.
     ====================================================================== */
    fun lwGetShader(fp: idFile, bloksz: Int): lwPlugin? {
        val shdr = lwPlugin()
        var id: Int
        var sz: Int
        var hsz: Int
        var rlen: Int
        val pos: Int
        pos = fp.Tell()
        set_flen(0)
        hsz = getU2(fp)
        shdr.ord = getS0(fp)
        id = getU4(fp)
        sz = getU2(fp)
        if (0 > get_flen()) {
            return gotoFreePlugin(shdr)
        }
        while (hsz > 0) {
            sz = (sz + (sz and 1))
            hsz -= sz
            if (id == ID_ENAB) {
                shdr.flags = getU2(fp)
                break
            } else {
                fp.Seek(sz.toLong(), fsOrigin_t.FS_SEEK_CUR)
                id = getU4(fp)
                sz = getU2(fp)
            }
        }
        id = getU4(fp)
        sz = getU2(fp)
        if (0 > get_flen()) {
            return gotoFreePlugin(shdr)
        }
        while (true) {
            sz = (sz + (sz and 1))
            set_flen(0)
            when (id) {
                ID_FUNC -> {
                    shdr.name = getS0(fp)
                    rlen = get_flen()
                    shdr.data = getbytes(fp, sz - rlen)
                }

                else -> {}
            }

            /* error while reading the current subchunk? */rlen = get_flen()
            if (rlen < 0 || rlen > sz) {
                return gotoFreePlugin(shdr)
            }

            /* skip unread parts of the current subchunk */if (rlen < sz) {
                fp.Seek((sz - rlen).toLong(), fsOrigin_t.FS_SEEK_CUR)
            }

            /* end of the shader block? */if (bloksz <= fp.Tell() - pos) {
                break
            }

            /* get the next subchunk header */set_flen(0)
            id = getU4(fp)
            sz = getU2(fp)
            if (6 != get_flen()) {
                return gotoFreePlugin(shdr)
            }
        }
        set_flen(fp.Tell() - pos)
        return shdr

    }

    fun gotoFreePlugin(shdr: lwPlugin): lwPlugin? {
        lwFreePlugin.getInstance().run(shdr)
        return null
    }

    /*
     ======================================================================
     add_texture()

     Finds the surface channel (lwTParam or lwCParam) to which a texture is
     applied, then calls lwListInsert().
     ====================================================================== */
    fun add_texture(surf: lwSurface, tex: lwTexture): Int {
        val list: lwTexture?
        list = when (tex.chan.toInt()) {
            ID_COLR -> surf.color.tex
            ID_LUMI -> surf.luminosity.tex
            ID_DIFF -> surf.diffuse.tex
            ID_SPEC -> surf.specularity.tex
            ID_GLOS -> surf.glossiness.tex
            ID_REFL -> surf.reflection.`val`.tex
            ID_TRAN -> surf.transparency.`val`.tex
            ID_RIND -> surf.eta.tex
            ID_TRNL -> surf.translucency.tex
            ID_BUMP -> surf.bump.tex
            else -> return 0
        }
        lwListInsert(list, tex)
        return 1
    }

    /*
     ======================================================================
     lwDefaultSurface()

     Allocate and initialize a surface.
     ====================================================================== */
    fun lwDefaultSurface(): lwSurface {
        val surf: lwSurface
        surf = lwSurface()
        surf.color.rgb[0] = 0.78431f
        surf.color.rgb[1] = 0.78431f
        surf.color.rgb[2] = 0.78431f
        surf.diffuse.`val` = 1.0f
        surf.glossiness.`val` = 0.4f
        surf.bump.`val` = 1.0f
        surf.eta.`val` = 1.0f
        surf.sideflags = 1
        return surf
    }

    /*
     ======================================================================
     lwGetSurface()

     Read an lwSurface from an LWO2 file.
     ====================================================================== */
    fun lwGetSurface(fp: idFile, cksize: Int): lwSurface? {
        val surf: lwSurface
        var tex: lwTexture?
        var shdr: lwPlugin?
        var id: Int
        var type: Int
        var sz: Int
        val pos: Int
        var rlen: Int


        /* allocate the Surface structure */surf = lwSurface()

        /* non-zero defaults */
        surf.color.rgb[0] = 0.78431f
        surf.color.rgb[1] = 0.78431f
        surf.color.rgb[2] = 0.78431f
        surf.diffuse.`val` = 1.0f
        surf.glossiness.`val` = 0.4f
        surf.bump.`val` = 1.0f
        surf.eta.`val` = 1.0f
        surf.sideflags = 1

        /* remember where we started */set_flen(0)
        pos = fp.Tell()

        /* names */surf.name = getS0(fp)
        surf.srcname = getS0(fp)

        /* first subchunk header */id = getU4(fp)
        sz = getU2(fp)
        if (0 > get_flen()) {
            return gotoFreeSurfFail(surf)
        }

        /* process subchunks as they're encountered */while (true) {
            sz = (sz + (sz and 1))
            set_flen(0)
            when (id) {
                ID_COLR -> {
                    surf.color.rgb[0] = getF4(fp)
                    surf.color.rgb[1] = getF4(fp)
                    surf.color.rgb[2] = getF4(fp)
                    surf.color.eindex = getVX(fp)
                }

                ID_LUMI -> {
                    surf.luminosity.`val` = getF4(fp)
                    surf.luminosity.eindex = getVX(fp)
                }

                ID_DIFF -> {
                    surf.diffuse.`val` = getF4(fp)
                    surf.diffuse.eindex = getVX(fp)
                }

                ID_SPEC -> {
                    surf.specularity.`val` = getF4(fp)
                    surf.specularity.eindex = getVX(fp)
                }

                ID_GLOS -> {
                    surf.glossiness.`val` = getF4(fp)
                    surf.glossiness.eindex = getVX(fp)
                }

                ID_REFL -> {
                    surf.reflection.`val`.`val` = getF4(fp)
                    surf.reflection.`val`.eindex = getVX(fp)
                }

                ID_RFOP -> surf.reflection.options = getU2(fp)
                ID_RIMG -> surf.reflection.cindex = getVX(fp)
                ID_RSAN -> surf.reflection.seam_angle = getF4(fp)
                ID_TRAN -> {
                    surf.transparency.`val`.`val` = getF4(fp)
                    surf.transparency.`val`.eindex = getVX(fp)
                }

                ID_TROP -> surf.transparency.options = getU2(fp)
                ID_TIMG -> surf.transparency.cindex = getVX(fp)
                ID_RIND -> {
                    surf.eta.`val` = getF4(fp)
                    surf.eta.eindex = getVX(fp)
                }

                ID_TRNL -> {
                    surf.translucency.`val` = getF4(fp)
                    surf.translucency.eindex = getVX(fp)
                }

                ID_BUMP -> {
                    surf.bump.`val` = getF4(fp)
                    surf.bump.eindex = getVX(fp)
                }

                ID_SMAN -> surf.smooth = getF4(fp)
                ID_SIDE -> surf.sideflags = getU2(fp)
                ID_CLRH -> {
                    surf.color_hilite.`val` = getF4(fp)
                    surf.color_hilite.eindex = getVX(fp)
                }

                ID_CLRF -> {
                    surf.color_filter.`val` = getF4(fp)
                    surf.color_filter.eindex = getVX(fp)
                }

                ID_ADTR -> {
                    surf.add_trans.`val` = getF4(fp)
                    surf.add_trans.eindex = getVX(fp)
                }

                ID_SHRP -> {
                    surf.dif_sharp.`val` = getF4(fp)
                    surf.dif_sharp.eindex = getVX(fp)
                }

                ID_GVAL -> {
                    surf.glow.`val` = getF4(fp)
                    surf.glow.eindex = getVX(fp)
                }

                ID_LINE -> {
                    surf.line.enabled = 1
                    if (sz >= 2) {
                        surf.line.flags = getU2(fp)
                    }
                    if (sz >= 6) {
                        surf.line.size.`val` = getF4(fp)
                    }
                    if (sz >= 8) {
                        surf.line.size.eindex = getVX(fp)
                    }
                }

                ID_ALPH -> {
                    surf.alpha_mode = getU2(fp)
                    surf.alpha = getF4(fp)
                }

                ID_AVAL -> surf.alpha = getF4(fp)
                ID_BLOK -> {
                    type = getU4(fp)
                    when (type) {
                        ID_IMAP, ID_PROC, ID_GRAD -> {
                            tex = lwGetTexture(fp, sz - 4, type)
                            if (null == tex) {
                                return gotoFreeSurfFail(surf)
                            }
                            if (0 == add_texture(surf, tex)) {
                                lwFreeTexture.getInstance().run(tex)
                            }
                            set_flen(4 + get_flen())
                        }

                        ID_SHDR -> {
                            shdr = lwGetShader(fp, sz - 4)
                            if (null == shdr) {
                                return gotoFreeSurfFail(surf)
                            }
                            lwListInsert(surf.shader, shdr)
                            ++surf.nshaders
                            set_flen(4 + get_flen())
                        }
                    }
                }

                else -> {}
            }

            /* error while reading current subchunk? */rlen = get_flen()
            if (rlen < 0 || rlen > sz) {
                return gotoFreeSurfFail(surf)
            }

            /* skip unread parts of the current subchunk */if (rlen < sz) {
                fp.Seek((sz - rlen).toLong(), fsOrigin_t.FS_SEEK_CUR)
            }

            /* end of the SURF chunk? */if (cksize <= fp.Tell() - pos) {
                break
            }

            /* get the next subchunk header */set_flen(0)
            id = getU4(fp)
            sz = getU2(fp)
            if (6 != get_flen()) {
                return gotoFreeSurfFail(surf)
            }
        }
        return surf

    }

    fun gotoFreeSurfFail(surf: lwSurface): lwSurface? {
        lwFreeSurface.getInstance().run(surf)
        return null
    }

    fun dot(a: FloatArray, b: FloatArray): Float {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]
    }

    fun cross(a: FloatArray, b: FloatArray, c: FloatArray) {
        c[0] = a[1] * b[2] - a[2] * b[1]
        c[1] = a[2] * b[0] - a[0] * b[2]
        c[2] = a[0] * b[1] - a[1] * b[0]
    }

    fun normalize(v: FloatArray) {
        val r: Float
        r = idMath.Sqrt(dot(v, v))
        if (r > 0) {
            v[0] /= r
            v[1] /= r
            v[2] /= r
        }
    }

    /*
     ======================================================================
     lwGetVMap()

     Read an lwVMap from a VMAP or VMAD chunk in an LWO2.
     ====================================================================== */
    fun lwGetVMap(fp: idFile, cksize: Int, ptoffset: Int, poloffset: Int, perpoly: Int): lwVMap? {
        var buf: ByteBuffer?
        val vmap: lwVMap
        var i: Int
        var j: Int
        var npts: Int
        val rlen: Int


        /* read the whole chunk */set_flen(0)
        buf = ByteBuffer.wrap(getbytes(fp, cksize))
        if (null == buf) {
            return null
        }
        vmap = lwVMap()

        /* initialize the vmap */vmap.perpoly = perpoly

        set_flen(0)
        vmap.type = sgetU4(buf).toLong()
        vmap.dim = sgetU2(buf)
        vmap.name = sgetS0(buf)
        rlen = get_flen()

        /* count the vmap records */npts = 0
        while (buf.hasRemaining()) { //( bp < buf + cksize ) {
            i = sgetVX(buf)
            if (perpoly != 0) {
                i = sgetVX(buf)
            }
            buf.position(buf.position() + vmap.dim * (java.lang.Float.SIZE / java.lang.Byte.SIZE))
            ++npts
        }

        /* allocate the vmap */
        vmap.nverts = npts
        vmap.vindex = IntArray(npts)
        if (perpoly != 0) {
            vmap.pindex = IntArray(npts)
        }
        if (vmap.dim > 0) {
            vmap.value = Array(npts) { FloatArray(vmap.dim) }

        }

        /* fill in the vmap values */buf.position(rlen)
        i = 0
        while (i < npts) {
            vmap.vindex!![i] = sgetVX(buf)
            if (perpoly != 0) {
                vmap.pindex!![i] = sgetVX(buf)
            }
            j = 0
            while (j < vmap.dim) {
                vmap.value!![i][j] = sgetF4(buf)
                j++
            }
            i++
        }
        buf = null
        return vmap

    }

    fun gotoFailVmap(vmap: lwVMap): lwVMap? {
        lwFreeVMap.getInstance().run(vmap)
        return null
    }

    /*
     ======================================================================
     lwGetPointVMaps()

     Fill in the lwVMapPt structure for each point.
     ====================================================================== */
    fun lwGetPointVMaps(point: lwPointList, vmap: lwVMap?): Boolean {
        var vm: lwVMap?
        var i: Int
        var j: Int
        var n: Int

        /* count the number of vmap values for each point */vm = vmap
        while (vm != null) {
            if (0 == vm.perpoly) {
                i = 0
                while (i < vm.nverts) {
                    ++point.pt!![vm.vindex!![i]]!!.nvmaps
                    i++
                }
            }
            vm = vm.next
        }

        /* allocate vmap references for each mapped point */i = 0
        while (i < point.count) {
            if (point.pt!![i]!!.nvmaps != 0) {
                point.pt!![i]!!.vm =
                    Array(point.pt!![i]!!.nvmaps) { lwVMapPt() }
                if (point.pt!![i]!!.vm == null) {
                    return false
                }
                point.pt!![i]!!.nvmaps = 0
            }
            i++
        }

        /* fill in vmap references for each mapped point */vm = vmap
        while (vm != null) {
            if (0 == vm.perpoly) {
                i = 0
                while (i < vm.nverts) {
                    j = vm.vindex!![i]
                    n = point.pt!![j]!!.nvmaps
                    point.pt!![j]!!.vm!![n].vmap = vm
                    point.pt!![j]!!.vm!![n].index = i
                    ++point.pt!![j]!!.nvmaps
                    i++
                }
            }
            vm = vm.next
        }
        return true
    }

    /*
     ======================================================================
     lwGetPolyVMaps()

     Fill in the lwVMapPt structure for each polygon vertex.
     ====================================================================== */
    fun lwGetPolyVMaps(polygon: lwPolygonList, vmap: lwVMap?): Boolean {
        var vm: lwVMap?
        var pv: lwPolVert?
        var i: Int
        var j: Int

        /* count the number of vmap values for each polygon vertex */vm = vmap
        while (vm != null) {
            if (vm.perpoly != 0) {
                i = 0
                while (i < vm.nverts) {
                    j = 0
                    while (j < polygon.pol!![vm.pindex!![i]]!!.nverts) {
                        pv = polygon.pol!![vm.pindex!![i]]!!.getV(j)
                        if (vm.vindex!![i] == pv!!.index) {
                            ++pv.nvmaps
                            break
                        }
                        j++
                    }
                    i++
                }
            }
            vm = vm.next
        }

        /* allocate vmap references for each mapped vertex */i = 0
        while (i < polygon.count) {
            j = 0
            while (j < polygon.pol!![i]!!.nverts) {
                pv = polygon.pol!![i]!!.getV(j)!!
                if (pv.nvmaps != 0) {
                    pv.vm = lwVMapPt.generateArray(pv.nvmaps)
                    if (pv.vm == null) {
                        return false
                    }
                    pv.nvmaps = 0
                }
                j++
            }
            i++
        }

        /* fill in vmap references for each mapped point */vm = vmap
        while (vm != null) {
            if (vm.perpoly != 0) {
                i = 0
                while (i < vm.nverts) {
                    j = 0
                    while (j < polygon.pol!![vm.pindex!![i]]!!.nverts) {
                        pv = polygon.pol!![vm.pindex!![i]]!!.getV(j)!!
                        if (vm.vindex!![i] == pv.index) {
                            pv.vm!![pv.nvmaps].vmap = vm
                            pv.vm!![pv.nvmaps].index = i
                            ++pv.nvmaps
                            break
                        }
                        j++
                    }
                    i++
                }
            }
            vm = vm.next
        }
        return true
    }

    /* generic linked list */
    abstract class lwNode {
        var NULL = false
        var data: Any? = null
        open fun oSet(node: lwNode): lwNode {
            throw UnsupportedOperationException("Not supported yet.")
        }

        open fun isNULL(): Boolean {
            return NULL
        }

        abstract fun getNext(): lwNode?
        abstract fun setNext(next: lwNode?)
        abstract fun getPrev(): lwNode?
        abstract fun setPrev(prev: lwNode?)

        companion object {
            fun getPosition(n: lwNode?): Int {
                var n = n
                var position = 0
                while (n != null) {
                    position++
                    n = n.getPrev()
                }
                return position
            }

            fun getPosition(n: lwNode?, pos: Int): lwNode? {
                var n = n
                var position = getPosition(n)
                if (position > pos) {
                    while (position != pos) {
                        if (null == n) {
                            break
                        }
                        position--
                        n = n.getPrev()
                    }
                } else {
                    while (position != pos) {
                        if (null == n) {
                            break
                        }
                        position--
                        n = n.getNext()
                    }
                }
                return n
            }
        }
    }

    /* plug-in reference */
    class lwPlugin : lwNode() {
        var flags = 0
        var name: String? = null
        var next: lwPlugin? = null
        var prev: lwPlugin? = null
        var ord: String? = null

        override fun getNext(): lwNode? {
            return next
        }

        override fun setNext(next: lwNode?) {
            this.next = next as lwPlugin?
        }

        override fun getPrev(): lwNode? {
            return prev
        }

        override fun setPrev(prev: lwNode?) {
            this.prev = prev as lwPlugin?
        }
    }

    /* envelopes */
    class lwKey : lwNode() {
        var bias = 0.0f
        var continuity = 0.0f
        var next: lwKey? = null
        var prev: lwKey? = null
        var param: FloatArray = FloatArray(4)
        var shape // ID_TCB, ID_BEZ2, etc.
                : Long = 0
        var tension = 0.0f
        var time = 0.0f
        var value = 0.0f
        override fun getNext(): lwNode? {
            return next
        }

        override fun setNext(next: lwNode?) {
            this.next = next as lwKey?
        }

        override fun getPrev(): lwNode? {
            return prev
        }

        override fun setPrev(prev: lwNode?) {
            this.prev = prev as lwKey?
        }
    }

    class lwEnvelope : lwNode() {
        var behavior: IntArray = IntArray(2) // pre and post (extrapolation)
        var cfilter // linked list of channel filters
                : lwPlugin? = null
        var index = 0
        var key // linked list of keys
                : lwKey? = null
        var name: String? = null
        var ncfilters = 0
        var next: lwEnvelope? = null
        var prev: lwEnvelope? = null
        var nkeys = 0
        var type = 0
        override fun getNext(): lwNode? {
            return next
        }

        override fun setNext(next: lwNode?) {
            this.next = next as lwEnvelope?
        }

        override fun getPrev(): lwNode? {
            return prev
        }

        override fun setPrev(prev: lwNode?) {
            this.prev = prev as lwEnvelope?
        }
    }

    /* values that can be enveloped */
    class lwEParam {
        var eindex = 0
        var `val` = 0.0f
    }

    class lwVParam {
        var eindex = 0
        var `val`: FloatArray = FloatArray(3)
    }

    /* clips */
    class lwClipStill {
        var name: String? = null
    }

    class lwClipSeq {
        var digits = 0
        var end = 0
        var flags = 0
        var offset = 0
        var prefix // filename before sequence digits
                : String? = null
        var start = 0
        var suffix // after digits, e.g. extensions
                : String? = null
    }

    class lwClipAnim {
        var data: Any? = null
        var name: String? = null
        var server // anim loader plug-in
                : String? = null
    }

    class lwClipXRef {
        var clip: lwClip? = null
        var index = 0
        var string: String? = null
    }

    class lwClipCycle {
        var hi = 0
        var lo = 0
        var name: String? = null
    }

    class lwClip : lwNode() {
        var brightness: lwEParam = lwEParam()
        var contrast: lwEParam = lwEParam()
        var duration = 0.0f
        var frame_rate = 0.0f
        var gamma: lwEParam = lwEParam()
        var hue: lwEParam = lwEParam()
        var ifilter // linked list of image filters
                : lwPlugin = lwPlugin()
        var index = 0
        var negative = 0
        var next: lwClip? = null
        var prev: lwClip? = null
        var nifilters = 0
        var npfilters = 0
        var pfilter // linked list of pixel filters
                : lwPlugin? = null
        var saturation: lwEParam = lwEParam()
        var source: Source = Source()
        var start_time = 0.0f
        var type // ID_STIL, ID_ISEQ, etc.
                = 0

        override fun getNext(): lwNode? {
            return next
        }

        override fun setNext(next: lwNode?) {
            this.next = next as lwClip?
        }

        override fun getPrev(): lwNode? {
            return prev
        }

        override fun setPrev(prev: lwNode?) {
            this.prev = prev as lwClip?
        }

        class Source {
            var anim: lwClipAnim = lwClipAnim()
            var cycle: lwClipCycle = lwClipCycle()
            var seq: lwClipSeq = lwClipSeq()
            var still: lwClipStill = lwClipStill()
            var xref: lwClipXRef = lwClipXRef()
        }
    }

    /* textures */
    class lwTMap {
        var center: lwVParam = lwVParam()
        var coord_sys = 0
        var fall_type = 0
        var falloff: lwVParam = lwVParam()
        var ref_object: String? = null
        var rotate: lwVParam = lwVParam()
        var size: lwVParam = lwVParam()
    }

    class lwImageMap {
        var aa_strength = 0.0f
        var aas_flags = 0
        var amplitude: lwEParam = lwEParam()
        var axis = 0
        var cindex = 0
        var pblend = 0
        var projection = 0
        var stck: lwEParam = lwEParam()
        var vmap_name: String? = null
        var wraph: lwEParam = lwEParam()
        var wraph_type = 0
        var wrapw: lwEParam = lwEParam()
        var wrapw_type = 0
    }

    class lwProcedural {
        var axis = 0
        var data: Any? = null
        var name: String? = null
        var value: FloatArray = FloatArray(3)
    }

    class lwGradKey {
        var next: lwGradKey? = null
        var prev: lwGradKey? = null
        var rgba: FloatArray = FloatArray(4)
        var value = 0.0f
    }

    class lwGradient {
        var end = 0.0f
        var ikey // array of interpolation codes
                : IntArray? = null
        var itemname: String? = null
        var key // array of gradient keys
                : Array<lwGradKey>? = null
        var paramname: String? = null
        var repeat = 0
        var start = 0.0f
    }

    class lwTexture : lwNode() {

        var axis: Int = 0
        var chan: Long = 0
        var enabled: Int = 0
        var negative: Int = 0
        var next: lwTexture? = null
        var prev: lwTexture? = null
        var opac_type: Int = 0
        var opacity: lwEParam = lwEParam()
        var ord: String? = null
        var param: Param = Param()
        var tmap: lwTMap = lwTMap()
        var type: Long = 0

        override fun oSet(node: lwNode): lwNode {
            val tempNode = node as lwTexture
            NULL = false

            axis = tempNode.axis
            chan = tempNode.chan
            enabled = tempNode.enabled
            negative = tempNode.negative
            next = tempNode.next
            prev = tempNode.prev
            opac_type = tempNode.opac_type
            opacity = tempNode.opacity
            ord = tempNode.ord
            param = tempNode.param
            tmap = tempNode.tmap
            type = tempNode.type

            return this
        }

        override fun getNext(): lwNode? {
            return next
        }

        override fun setNext(next: lwNode?) {
            this.next = next as lwTexture?
        }

        override fun getPrev(): lwNode? {
            return prev
        }

        override fun setPrev(prev: lwNode?) {
            this.prev = prev as lwTexture?
        }

        class Param {
            var grad: lwGradient = lwGradient()
            var imap: lwImageMap = lwImageMap()
            var proc: lwProcedural = lwProcedural()
        }

        init {
            NULL = true
        }
    }

    /* values that can be textured */
    class lwTParam {
        var eindex = 0
        var tex: lwTexture = lwTexture() // linked list of texture layers
        var `val` = 0.0f
    }

    class lwCParam {
        var eindex = 0
        var rgb: FloatArray = FloatArray(3)
        var tex: lwTexture = lwTexture() // linked list of texture layers
    }

    /* surfaces */
    class lwGlow {
        var enabled: Short = 0
        var intensity: lwEParam = lwEParam()
        var size: lwEParam = lwEParam()
        var type: Short = 0
    }

    class lwRMap {
        var cindex = 0
        var options = 0
        var seam_angle = 0.0f
        var `val`: lwTParam = lwTParam()
    }

    class lwLine {
        var enabled: Short = 0
        var flags = 0
        var size: lwEParam = lwEParam()
    }

    class lwSurface : lwNode() {
        var add_trans: lwEParam = lwEParam()
        var alpha = 0.0f
        var alpha_mode = 0
        var bump: lwTParam = lwTParam()
        var color: lwCParam = lwCParam()
        var color_filter: lwEParam = lwEParam()
        var color_hilite: lwEParam = lwEParam()
        var dif_sharp: lwEParam = lwEParam()
        var diffuse: lwTParam = lwTParam()
        var eta: lwTParam = lwTParam()
        var glossiness: lwTParam = lwTParam()
        var glow: lwEParam = lwEParam()
        var line: lwLine = lwLine()
        var luminosity: lwTParam = lwTParam()
        var name: String? = null
        var next: lwSurface? = null
        var prev: lwSurface? = null
        var nshaders = 0
        var reflection: lwRMap = lwRMap()
        var shader: lwPlugin = lwPlugin() // linked list of shaders
        var sideflags = 0
        var smooth = 0.0f
        var specularity: lwTParam = lwTParam()
        var srcname: String? = null
        var translucency: lwTParam = lwTParam()
        var transparency: lwRMap = lwRMap()
        override fun oSet(node: lwNode): lwNode {
            val surface = node as lwSurface
            next = surface.next
            prev = surface.prev
            name = surface.name
            srcname = surface.srcname
            color = surface.color
            luminosity = surface.luminosity
            diffuse = surface.diffuse
            specularity = surface.specularity
            glossiness = surface.glossiness
            reflection = surface.reflection
            transparency = surface.transparency
            eta = surface.eta
            translucency = surface.translucency
            bump = surface.bump
            smooth = surface.smooth
            sideflags = surface.sideflags
            alpha = surface.alpha
            alpha_mode = surface.alpha_mode
            color_hilite = surface.color_hilite
            color_filter = surface.color_filter
            add_trans = surface.add_trans
            dif_sharp = surface.dif_sharp
            glow = surface.glow
            line = surface.line
            shader = surface.shader
            nshaders = surface.nshaders

            return this
        }

        override fun getNext(): lwNode? {
            return next
        }

        override fun setNext(next: lwNode?) {
            this.next = next as lwSurface?
        }

        override fun getPrev(): lwNode? {
            return prev
        }

        override fun setPrev(prev: lwNode?) {
            this.prev = prev as lwSurface?
        }

        override fun hashCode(): Int {
            var hash = 5
            hash = 41 * hash + Objects.hashCode(name)
            return hash
        }

        override fun equals(obj: Any?): Boolean {
            if (obj == null) {
                return false
            }
            if (obj !is lwSurface) {
                return false
            }
            return name == obj.name
        }
    }

    /* vertex maps */
    class lwVMap : lwNode() {
        var dim = 0
        var name: String? = null
        var next: lwVMap? = null
        var prev: lwVMap? = null
        var nverts = 0

        // added by duffy
        var offset = 0
        var perpoly = 0
        var pindex // array of polygon indexes
                : IntArray? = null
        var type: Long = 0L
        var value: Array<FloatArray>? = null
        var vindex // array of point indexes
                : IntArray? = null

        fun clear() {
            next = null
            prev = null
            name = null
            type = 0
            dim = 0
            nverts = 0
            perpoly = 0
            vindex = null
            pindex = null
            value = null
            offset = 0
        }

        override fun getNext(): lwNode? {
            return next
        }

        override fun setNext(next: lwNode?) {
            this.next = next as lwVMap?
        }

        override fun getPrev(): lwNode? {
            return prev
        }

        override fun setPrev(prev: lwNode?) {
            this.prev = prev as lwVMap?
        }
    }

    class lwVMapPt {
        var index // vindex or pindex element
                = 0
        lateinit var vmap: lwVMap

        companion object {
            fun generateArray(length: Int): Array<lwVMapPt> {
                return Array(length) { lwVMapPt() }
            }
        }
    }

    /* points and polygons */
    class lwPoint {
        var npols // number of polygons sharing the point
                = 0
        var nvmaps = 0
        var pol // array of polygon indexes
                : IntArray? = null
        var pos: FloatArray = FloatArray(3)
        var vm // array of vmap references
                : Array<lwVMapPt>? = null

    }

    class lwPolVert {
        var index // index into the point array
                = 0
        var norm: FloatArray = FloatArray(3)
        var nvmaps = 0
        var vm // array of vmap references
                : Array<lwVMapPt>? = null
    }

    class lwPolygon {
        var flags = 0
        var norm: FloatArray = FloatArray(3)
        var nverts = 0
        var part // part index
                = 0
        var smoothgrp // smoothing group
                = 0
        var surf: lwSurface? = null
        var type: Long = 0
        var v // array of vertex records
                : Array<lwPolVert?>? = null
        private var vOffset // the offset from the start of v to point towards.
                = 0

        fun getV(index: Int): lwPolVert? {
            return v!![vOffset + index]
        }

        fun setV(v: Array<lwPolVert?>?, vOffset: Int) {
            this.v = v
            this.vOffset = vOffset
        }

    }

    class lwPointList {
        var count = 0
        var offset // only used during reading
                = 0
        var pt // array of points
                : Array<lwPoint?>? = null
    }

    class lwPolygonList {
        var count = 0
        var offset // only used during reading
                = 0
        var pol // array of polygons
                : Array<lwPolygon?>? = null
        var vcount // total number of vertices
                = 0
        var voffset // only used during reading
                = 0
    }

    /* geometry layers */
    class lwLayer : lwNode() {
        var bbox: FloatArray = FloatArray(6)
        var flags = 0
        var index = 0
        var name: String? = null
        var next: lwLayer? = null
        var prev: lwLayer? = null
        var nvmaps = 0
        var parent = 0
        var pivot: FloatArray = FloatArray(3)
        var point: lwPointList = lwPointList()
        var polygon: lwPolygonList = lwPolygonList()
        var vmap // linked list of vmaps
                : lwVMap? = null

        override fun getNext(): lwNode? {
            return next
        }

        override fun setNext(next: lwNode?) {
            this.next = next as lwLayer?
        }

        override fun getPrev(): lwNode? {
            return prev
        }

        override fun setPrev(prev: lwNode?) {
            this.prev = prev as lwLayer?
        }
    }

    /* tag strings */
    class lwTagList {
        var count = 0
        var offset // only used during reading
                = 0
        var tag // array of strings
                : Array<String?>? = null
    }

    /* an object */
    class lwObject {
        val taglist: lwTagList = lwTagList()
        var clip // linked list of clips
                : lwClip? = null
        var env // linked list of envelopes
                : lwEnvelope? = null
        var layer // linked list of layers
                : lwLayer? = null
        var nclips = 0
        var nenvs = 0
        var nlayers = 0
        var nsurfs = 0
        var surf // linked list of surfaces
                : lwSurface? = null
        var timeStamp: LongArray = LongArray(1)
    }

    /*
     ======================================================================

     Converted from lwobject sample prog from LW 6.5 SDK.

     ======================================================================
     */
    abstract class LW {
        abstract fun run(p: Any?)
    }

    /*
     ======================================================================
     lwFreeClip()

     Free memory used by an lwClip.
     ====================================================================== */
    @Deprecated("")
    class lwFreeClip private constructor() : LW() {
        @Deprecated("")
        override fun run(p: Any?) {
            var clip = p as lwClip?
            if (clip != null) {
                when (clip.type) {
                    ID_STIL -> {
                        if (clip.source.still.name != null) {
                            clip.source.still.name = null
                        }
                    }

                    ID_ISEQ -> {
                        if (clip.source.seq.suffix != null) {
                            clip.source.seq.suffix = null
                        }
                        if (clip.source.seq.prefix != null) {
                            clip.source.seq.prefix = null
                        }
                    }

                    ID_ANIM -> {
                        if (clip.source.anim.server != null) {
                            clip.source.anim.server = null
                        }
                        if (clip.source.anim.name != null) {
                            clip.source.anim.name = null
                        }
                    }

                    ID_XREF -> {
                        if (clip.source.xref.string != null) {
                            clip.source.xref.string = null
                        }
                    }

                    ID_STCC -> {
                        if (clip.source.cycle.name != null) {
                            clip.source.cycle.name = null
                        }
                    }
                }
                clip = null
            }
        }

        companion object {
            private val instance: LW = lwFreeClip()
            fun getInstance(): LW {
                return instance
            }
        }
    }

    @Deprecated("")
    class lwFree private constructor() : LW() {
        override fun run(p: Any?) {
        }

        companion object {
            private val instance: LW = lwFree()
            fun getInstance(): LW {
                return instance
            }
        }
    }

    /*
     ======================================================================
     lwFreeEnvelope()

     Free the memory used by an lwEnvelope.
     ====================================================================== */
    @Deprecated("")
    class lwFreeEnvelope private constructor() : LW() {
        override fun run(p: Any?) {
            var env = p as lwEnvelope?
            if (env != null) {
                if (env.name != null) {
                    env.name = null
                }
                env = null
            }
        }

        companion object {
            private val instance: LW = lwFreeEnvelope()
            fun getInstance(): LW {
                return instance
            }
        }
    }

    object compare_keys : cmp_t<lwKey> {
        override fun compare(k1: lwKey, k2: lwKey): Int {
            return if (k1.time > k2.time) 1 else if (k1.time < k2.time) -1 else 0
        }
    }

    /*
     ======================================================================
     lwFreeLayer()

     Free memory used by an lwLayer.
     ====================================================================== */
    class lwFreeLayer private constructor() : LW() {
        override fun run(p: Any?) {
            var layer = p as lwLayer?
            if (layer != null) {
                if (layer.name != null) {
                    layer.name = null
                }
                lwFreePoints(layer.point)
                lwFreePolygons(layer.polygon)
                layer = null
            }
        }

        companion object {
            private val instance: LW = lwFreeLayer()
            fun getInstance(): LW {
                return instance
            }
        }
    }

    /*
     ======================================================================
     lwFreePlugin()

     Free the memory used by an lwPlugin.
     ====================================================================== */
    class lwFreePlugin private constructor() : LW() {
        override fun run(o: Any?) {
            // C++ freed shader plugin memory; on JVM the GC handles this.
        }

        companion object {
            private val instance: LW = lwFreePlugin()
            fun getInstance(): LW {
                return instance
            }
        }
    }

    /*
     ======================================================================
     lwFreeTexture()

     Free the memory used by an lwTexture.
     ====================================================================== */
    class lwFreeTexture private constructor() : LW() {
        override fun run(p: Any?) {
            var t = p as lwTexture?
            if (t != null) {
                if (t.ord != null) {
                    t.ord = null
                }
                when (t.type.toInt()) {
                    ID_IMAP -> if (t.param.imap.vmap_name != null) {
                        t.param.imap.vmap_name = null
                    }

                    ID_PROC -> {
                        if (t.param.proc.name != null) {
                            t.param.proc.name = null
                        }
                        if (t.param.proc.data != null) {
                            t.param.proc.data = null
                        }
                    }

                    ID_GRAD -> {
                        if (t.param.grad.key != null) {
                            t.param.grad.key = null
                        }
                        if (t.param.grad.ikey != null) {
                            t.param.grad.ikey = null
                        }
                    }
                }
                if (t.tmap.ref_object != null) {
                    t.tmap.ref_object = null
                }
                t = null
            }
        }

        companion object {
            private val instance: LW = lwFreeTexture()
            fun getInstance(): LW {
                return instance
            }
        }
    }

    /*
     ======================================================================
     lwFreeSurface()

     Free the memory used by an lwSurface.
     ====================================================================== */
    class lwFreeSurface private constructor() : LW() {
        override fun run(p: Any?) {
            var surf = p as lwSurface?
            if (surf != null) {
                if (surf.name != null) {
                    surf.name = null
                }
                if (surf.srcname != null) {
                    surf.srcname = null
                }
                surf = null
            }
        }

        companion object {
            private val instance: LW = lwFreeSurface()
            fun getInstance(): LW {
                return instance
            }
        }
    }

    /*
     ======================================================================
     compare_textures()
     compare_shaders()

     Callbacks for the lwListInsert() function, which is called to add
     textures to surface channels and shaders to surfaces.
     ====================================================================== */
    object compare_textures : cmp_t<lwTexture> {
        override fun compare(a: lwTexture, b: lwTexture): Int {
            return a.ord!!.compareTo(b.ord!!)
        }
    }

    object compare_shaders : cmp_t<lwPlugin> {
        override fun compare(a: lwPlugin, b: lwPlugin): Int {
            return a.ord!!.compareTo(b.ord!!)
        }
    }

    /*
     ======================================================================
     lwFreeVMap()

     Free memory used by an lwVMap.
     ====================================================================== */
    class lwFreeVMap private constructor() : LW() {
        override fun run(p: Any?) {
            val vmap = p as lwVMap?
            if (vmap != null) {
                if (vmap.name != null) {
                    vmap.name = null
                }
                if (vmap.vindex != null) {
                    vmap.vindex = null
                }
                if (vmap.pindex != null) {
                    vmap.pindex = null
                }
                if (vmap.value != null) {
                    vmap.value = null
                }
                vmap.clear()
            }
        }

        companion object {
            private val instance: LW = lwFreeVMap()
            fun getInstance(): LW {
                return instance
            }
        }
    }
}

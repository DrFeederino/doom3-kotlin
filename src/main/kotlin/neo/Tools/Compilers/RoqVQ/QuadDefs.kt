package neo.Tools.Compilers.RoqVQ

const val CCC = 2
const val CCCBITMAP = 0
const val COLA = 0
const val COLB = 1
const val COLC = 2
const val COLPATA = 4
const val COLPATB = 5
const val COLPATS = 6
const val COLS = 3
const val DEAD = 6
const val DEP = 0
const val FCC = 1
const val FCCDOMAIN = 1
const val GENERATION = 7
const val MAXSIZE = 16
const val MINSIZE = 4
const val MOT = 5
const val PAT = 4
const val PATNUMBE2 = 3
const val PATNUMBE3 = 4
const val PATNUMBE4 = 5
const val PATNUMBE5 = 6
const val PATNUMBER = 2
const val RoQ_ID = 0x1084
const val RoQ_PUZZLE_QUAD = 0x1003
const val RoQ_QUAD = 0x1000
const val RoQ_QUAD_CODEBOOK = 0x1002
const val RoQ_QUAD_HANG = 0x1013
const val RoQ_QUAD_INFO = 0x1001
const val RoQ_QUAD_JPEG = 0x1012
const val RoQ_QUAD_SMALL = 0x1010
const val RoQ_QUAD_VQ = 0x1011
const val SLD = 3

internal class shortQuadCel {
    var size //  32, 16, 8, or 4
            : Byte = 0
    var   /*word*/xat // where is it at on the screen
            = 0
    var   /*word*/yat //
            = 0
}

class quadcel {
    var   /*unsigned int*/bitmap // ccc bitmap
            : Long = 0

    //
    var cccsnr // ccc bitmap snr to actual image
            = 0f

    //
    var   /*unsigned int*/cola // color a for ccc
            : Long = 0
    var   /*unsigned int*/colb // color b for ccc
            : Long = 0
    var   /*unsigned int*/colc // color b for ccc
            : Long = 0
    var   /*unsigned int*/colpata: Long = 0
    var   /*unsigned int*/colpatb: Long = 0
    var   /*unsigned int*/colpats: Long = 0
    var dctsnr = 0.0f
    var   /*word*/domain // where to copy from for fcc
            = 0
    var fccsnr // fcc bitmap snr to actual image
            = 0.0f
    var mark = false
    var motsnr // delta snr to previous image
            = 0.0f
    var patsnr = 0.0f
    var patten: IntArray = IntArray(5) // which pattern
    var rsnr // what's the current snr
            = 0.0f
    var size //  32, 16, 8, or 4
            : Byte = 0
    var   /*unsigned int*/sldcol // sold color
            : Long = 0
    var sldsnr // solid color snr
            = 0.0f
    var snr: FloatArray = FloatArray(DEAD + 1) // snrssss

    //
    var status = 0
    var   /*word*/xat // where is it at on the screen
            = 0
    var   /*word*/yat //
            = 0
}

internal class dataQuadCel {
    var bitmaps: LongArray = LongArray(7) // ccc bitmap
    var cols: LongArray = LongArray(8)
    var snr: FloatArray = FloatArray(DEAD + 1) // snrssss
}

internal class norm {
    /*unsigned short*/
    var index = 0
    var normal = 0.0f
}

internal class dtlCel {
    var a: IntArray = IntArray(4)
    var b: IntArray = IntArray(4)

    /*unsigned*/
    var dtlMap: CharArray = CharArray(256)
    var g: IntArray = IntArray(4)
    var r: IntArray = IntArray(4)
    var ymean = 0f
}

internal class pPixel {
    var r: Byte = 0
    var g: Byte = 0
    var b: Byte = 0
    var a: Byte = 0
}

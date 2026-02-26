package neo.Tools.Compilers.RoqVQ

const val BIEMULT = 0.50000f
const val BMULT = 0.1140f
const val BQEMULT = -0.08131f
const val GIEMULT = -0.33126f
const val GMULT = 0.5870f
const val GQEMULT = -0.41869f
const val RIEMULT = -0.16874f
const val RMULT = 0.2990f // use these for televisions
const val RQEMULT = 0.50000f

fun RGBDIST(src0: IntArray, src1: IntArray, i0: Int, i1: Int): Int {
    return (src0[i0 + 0] - src1[i1 + 0]) * (src0[i0 + 0] - src1[i1 + 0]) + (src0[i0 + 1] - src1[i1 + 1]) * (src0[i0 + 1] - src1[i1 + 1]) + (src0[i0 + 2] - src1[i1 + 2]) * (src0[i0 + 2] - src1[i1 + 2])
}

fun RGBADIST(src0: ByteArray, src1: ByteArray, i0: Int, i1: Int): Int {
    return (src0[i0 + 0] - src1[i1 + 0]) * (src0[i0 + 0] - src1[i1 + 0]) + (src0[i0 + 1] - src1[i1 + 1]) * (src0[i0 + 1] - src1[i1 + 1]) + (src0[i0 + 2] - src1[i1 + 2]) * (src0[i0 + 2] - src1[i1 + 2]) + (src0[i0 + 3] - src1[i1 + 3]) * (src0[i0 + 3] - src1[i1 + 3])
}

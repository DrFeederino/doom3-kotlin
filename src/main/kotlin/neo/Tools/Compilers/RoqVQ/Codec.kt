package neo.Tools.Compilers.RoqVQ

import neo.TempDump
import neo.framework.Common
import neo.framework.FileSystem_h
import neo.framework.File_h.idFile
import neo.framework.Session
import neo.idlib.math.idMath
import neo.sys.win_shared
import java.nio.ByteBuffer
import java.util.*
import kotlin.math.sqrt

object Codec {
    //
    const val FULLFRAME = 0
    const val JUSTMOTION = 1
    const val MAXERRORMAX = 200
    const val MIN_SNR = 3.0f

    //#define IPSIZE int
    const val MOTION_MIN = 1.0f

    //
    //#define VQDATA		double
    fun glimit(`val`: Float): Float {
        if (`val` < 0) {
            return 0.0f
        }
        return if (`val` > 255) {
            255.0f
        } else `val`
    }

    internal class codec {
        private val codebook2: Array<FloatArray>
        private val codebook4: Array<FloatArray>
        private val codebookmade: Int
        private val codebooksize: Int
        private var detail = false
        private var dimension2 = 0
        private var dimension4 = 0
        private var dxMean = 0
        private var dyMean = 0
        private var image: NSBitmapImageRep? = null
        private val index2: IntArray = IntArray(256)
        private var initRGBtab = 0
        private var luti: ByteArray? = null

        //
        private val luty: ByteArray = ByteArray(256)
        private var newImage: NSBitmapImageRep? = NSBitmapImageRep()
        private var numQuadCels = 0
        private var onQuad = 0
        private val overAmount: Int
        private var pixelsHigh = 0
        private var pixelsWide = 0
        private val previousImage: Array<NSBitmapImageRep?> =
            arrayOfNulls(2) // the ones in video ram and offscreen ram
        private var qStatus: Array<quadcel>? = null
        private var slop: Int
        private val used2: BooleanArray = BooleanArray(256)
        private val used4: BooleanArray = BooleanArray(256)

        //
        //
        private var whichFrame: Int
        fun SparseEncode() {
            var i: Int
            var j: Int
            var osize: Int
            var fsize: Int
            var onf: Int
            var ong: Int
            val wtype: Int
            var temp: Int
            val num = IntArray(DEAD + 1)
            var ilist: IntArray
            val sRMSE: Float
            val numredo: Float
            var flist: FloatArray
            var idataA: ByteArray
            var idataB: ByteArray
            osize = 8
            image = Roq.theRoQ.CurrentImage()
            newImage = null //0;
            pixelsHigh = image!!.pixelsHigh()
            pixelsWide = image!!.pixelsWide()
            dimension2 = 12
            dimension4 = 48
            if (image!!.hasAlpha() && Roq.theRoQ.ParamNoAlpha() == false) {
                dimension2 = 16
                dimension4 = 64
            }
            idataA = ByteArray(16 * 16 * 4) // Mem_Alloc(16 * 16 * 4);
            idataB = ByteArray(16 * 16 * 4) // Mem_Alloc(16 * 16 * 4);
            if (previousImage[0] == null) {
                Common.common.Printf("sparseEncode: sparsely encoding a %d,%d image\n", pixelsWide, pixelsHigh)
            }
            InitImages()
            flist = FloatArray(numQuadCels + 1) // Mem_ClearedAlloc((numQuadCels + 1));
            ilist = IntArray(numQuadCels + 1) // Mem_ClearedAlloc((numQuadCels + 1));
            fsize = 56 * 1024
            if (Roq.theRoQ.NumberOfFrames() > 2) {
                fsize = if (previousImage[0] != null) {
                    Roq.theRoQ.NormalFrameSize()
                } else {
                    Roq.theRoQ.FirstFrameSize()
                }
                if (Roq.theRoQ.HasSound() && fsize > 6000 && previousImage[0] != null) {
                    fsize = 6000
                }
            }
            fsize += slop / 50
            if (fsize > 64000) {
                fsize = 64000
            }
            if (previousImage[0] != null && fsize > Roq.theRoQ.NormalFrameSize() * 2) {
                fsize = Roq.theRoQ.NormalFrameSize() * 2
            }
            dyMean = 0
            dxMean = dyMean
            wtype = if (previousImage[0] != null) {
                1
            } else {
                0
            }
            i = 0
            while (i < numQuadCels) {
                j = 0
                while (j < DEAD) {
                    qStatus!![i].snr[j] = 9999.0f
                    j++
                }
                qStatus!![i].mark = false
                if (qStatus!![i].size.toInt() == osize) {
                    if (previousImage[0] != null) {
                        GetData(idataA, qStatus!![i].size.toInt(), qStatus!![i].xat, qStatus!![i].yat, image!!)
                        GetData(
                            idataB,
                            qStatus!![i].size.toInt(),
                            qStatus!![i].xat,
                            qStatus!![i].yat,
                            previousImage[whichFrame and 1]!!
                        )
                        qStatus!![i].snr[MOT] = Snr(idataA, idataB, qStatus!![i].size.toInt())
                        if (ComputeMotionBlock(
                                idataA,
                                idataB,
                                qStatus!![i].size.toInt()
                            ) && !Roq.theRoQ.IsLastFrame()
                        ) {
                            qStatus!![i].mark = true
                        }
                        if (!qStatus!![i].mark) {
                            FvqData(
                                idataA,
                                qStatus!![i].size.toInt(),
                                qStatus!![i].xat,
                                qStatus!![i].yat,
                                qStatus!![i],
                                false
                            )
                        }
                    }
                    run {
                        val rsnr = floatArrayOf(0.0f)
                        val status = intArrayOf(0)
                        LowestQuad(qStatus!![i], status, rsnr, wtype)
                        qStatus!![i].status = status[0]
                        qStatus!![i].rsnr = rsnr[0]
                    }
                    if (qStatus!![i].rsnr < 9999) {
                        Roq.theRoQ.MarkQuadx(
                            qStatus!![i].xat,
                            qStatus!![i].yat,
                            qStatus!![i].size.toInt(),
                            qStatus!![i].rsnr,
                            qStatus!![i].status
                        )
                    }
                } else {
                    if (qStatus!![i].size < osize) {
                        qStatus!![i].status = 0
                        qStatus!![i].size = 0
                    } else {
                        qStatus!![i].status = DEP
                        qStatus!![i].rsnr = 0.0f
                    }
                }
                i++
            }
            //
// the quad is complete, so status can now be used for quad decomposition
// the first thing to do is to set it up for all the 4x4 cels to get output
// and then recurse from there to see what's what
//
            sRMSE = GetCurrentRMSE(qStatus!!)
            if (Roq.theRoQ.IsQuiet() == false) {
                Common.common.Printf(
                    "sparseEncode: rmse of quad0 is %f, size is %d (meant to be %d)\n",
                    sRMSE,
                    GetCurrentQuadOutputSize(qStatus!!),
                    fsize
                )
            }
            onf = 0
            i = 0
            while (i < numQuadCels) {
                if (qStatus!![i].size.toInt() != 0 && qStatus!![i].status != DEP) {
                    flist[onf] = qStatus!![i].rsnr
                    ilist[onf] = i
                    onf++
                }
                i++
            }
            Sort(flist, ilist, onf)
            Segment(ilist, flist, onf, GetCurrentRMSE(qStatus!!))
            dyMean = 0
            dxMean = dyMean
            temp = dxMean
            /*
             for( i=0; i<numQuadCels; i++ ) {
             if (qStatus[i].size && qStatus[i].status == FCC) {
             dxMean += (qStatus[i].domain >> 8  ) - 128;
             dyMean += (qStatus[i].domain & 0xff) - 128;
             temp++;
             }
             }
             if (temp) { dxMean /= temp; dyMean /= temp; }
             */Common.common.Printf("sparseEncode: dx/dy mean is %d,%d\n", dxMean, dyMean)
            numredo = 0.0f
            detail = false
            if (codebookmade != 0 && whichFrame > 4) {
                fsize -= 256
            }
            temp = 0
            i = 0
            while (i < numQuadCels) {
                if (qStatus!![i].size.toInt() == osize && qStatus!![i].mark == false && qStatus!![i].snr[MOT] > 0) {
                    GetData(idataA, qStatus!![i].size.toInt(), qStatus!![i].xat, qStatus!![i].yat, image!!)
                    if (osize == 8) {
                        VqData8(idataA, qStatus!![i])
                    }
                    if (previousImage[0] != null) {
                        var dx: Int
                        var dy: Int
                        dx = (qStatus!![i].domain shr 8) - 128 - dxMean + 8
                        dy = (qStatus!![i].domain and 0xff) - 128 - dyMean + 8
                        if (dx < 0 || dx > 15 || dy < 0 || dy > 15) {
                            qStatus!![i].snr[FCC] = 9999.0f
                            temp++
                            FvqData(
                                idataA,
                                qStatus!![i].size.toInt(),
                                qStatus!![i].xat,
                                qStatus!![i].yat,
                                qStatus!![i],
                                true
                            )
                            dx = (qStatus!![i].domain shr 8) - 128 - dxMean + 8
                            dy = (qStatus!![i].domain and 0xff) - 128 - dyMean + 8
                            if ((dx < 0 || dx > 15 || dy < 0 || dy > 15) && qStatus!![i].snr[FCC] != 9999.0f && qStatus!![i].status == FCC
                            ) {
                                Common.common.Printf(
                                    "sparseEncode: something is wrong here, dx/dy is %d,%d after being clamped\n",
                                    dx,
                                    dy
                                )
                                Common.common.Printf("xat:    %d\n", qStatus!![i].xat)
                                Common.common.Printf("yat:    %d\n", qStatus!![i].yat)
                                Common.common.Printf("size    %d\n", qStatus!![i].size)
                                Common.common.Printf("type:   %d\n", qStatus!![i].status)
                                Common.common.Printf("mot:    %04x\n", qStatus!![i].domain)
                                Common.common.Printf("motsnr: %0f\n", qStatus!![i].snr[FCC])
                                Common.common.Printf("rmse:   %0f\n", qStatus!![i].rsnr)
                                Common.common.Error("need to go away now\n")
                            }
                        }
                    }
                    run {
                        val rsnr = floatArrayOf(0.0f)
                        val status = intArrayOf(0)
                        LowestQuad(qStatus!![i], status, rsnr, wtype)
                        qStatus!![i].status = status[0]
                        qStatus!![i].rsnr = rsnr[0]
                    }
                    Roq.theRoQ.MarkQuadx(
                        qStatus!![i].xat,
                        qStatus!![i].yat,
                        qStatus!![i].size.toInt(),
                        qStatus!![i].rsnr,
                        qStatus!![i].status
                    )
                    /*
                     if (qStatus[i].status==FCC && qStatus[i].snr[FCC]>qStatus[i].snr[SLD]) {
                     common.Printf("sparseEncode: something is wrong here\n");
                     common.Printf("xat:    %d\n", qStatus[i].xat);
                     common.Printf("yat:    %d\n", qStatus[i].yat);
                     common.Printf("size    %d\n", qStatus[i].size);
                     common.Printf("type:   %d\n", qStatus[i].status);
                     common.Printf("mot:    %04x\n", qStatus[i].domain);
                     common.Printf("motsnr: %0f\n", qStatus[i].snr[FCC]);
                     common.Printf("sldsnr: %0f\n", qStatus[i].snr[SLD]);
                     common.Printf("rmse:   %0f\n", qStatus[i].rsnr);
                     //common.Error("need to go away now\n");
                     }
                     */
                }
                i++
            }
            if (Roq.theRoQ.IsQuiet() == false) {
                Common.common.Printf(
                    "sparseEncode: rmse of quad0 is %f, size is %d (meant to be %d)\n",
                    GetCurrentRMSE(qStatus!!),
                    GetCurrentQuadOutputSize(qStatus!!),
                    fsize
                )
                Common.common.Printf("sparseEncode: %d outside fcc limits\n", temp)
            }
            onf = 0
            i = 0
            while (i < numQuadCels) {
                if (qStatus!![i].size.toInt() != 0 && qStatus!![i].status != DEP) {
                    flist[onf] = qStatus!![i].rsnr
                    ilist[onf] = i
                    onf++
                }
                i++
            }
            Sort(flist, ilist, onf)
            ong = 0
            detail = false
            while (GetCurrentQuadOutputSize(qStatus!!) < fsize && ong < onf && flist[ong] > 0 && qStatus!![ilist[ong]].mark == false) {
//		badsnr = [self getCurrentRMSE: qStatus!!];
                osize = AddQuad(qStatus!!, ilist[ong++])
                //		if ([self getCurrentRMSE: qStatus] >= badsnr) {
//		    break;
//		}
            }
            if (GetCurrentQuadOutputSize(qStatus!!) < fsize) {
                ong = 0
                while (GetCurrentQuadOutputSize(qStatus!!) < fsize && ong < onf) {
//			badsnr = [self getCurrentRMSE: qStatus!!];
                    i = ilist[ong++]
                    if (qStatus!![i].mark) {
                        detail = false
                        qStatus!![i].mark = false
                        GetData(idataA, qStatus!![i].size.toInt(), qStatus!![i].xat, qStatus!![i].yat, image!!)
                        if (qStatus!![i].size.toInt() == 8) {
                            VqData8(idataA, qStatus!![i])
                        }
                        if (qStatus!![i].size.toInt() == 4) {
                            VqData4(idataA, qStatus!![i])
                        }
                        if (qStatus!![i].size.toInt() == 4) {
                            VqData2(idataA, qStatus!![i])
                        }
                        if (previousImage[0] != null) {
                            FvqData(
                                idataA,
                                qStatus!![i].size.toInt(),
                                qStatus!![i].xat,
                                qStatus!![i].yat,
                                qStatus!![i],
                                true
                            )
                        }
                        run {
                            val rsnr = floatArrayOf(0.0f)
                            val status = intArrayOf(0)
                            LowestQuad(qStatus!![i], status, rsnr, wtype)
                            qStatus!![i].status = status[0]
                            qStatus!![i].rsnr = rsnr[0]
                        }
                        if (qStatus!![i].rsnr <= MIN_SNR) {
                            break
                        }
                        Roq.theRoQ.MarkQuadx(
                            qStatus!![i].xat,
                            qStatus!![i].yat,
                            qStatus!![i].size.toInt(),
                            qStatus!![i].rsnr,
                            qStatus!![i].status
                        )
                    }
                }
                ong = 0
                while (GetCurrentQuadOutputSize(qStatus!!) < fsize && ong < onf && flist[ong] > 0) {
                    i = ilist[ong++]
                    detail = true
                    osize = AddQuad(qStatus!!, i)
                }
            }
            Common.common.Printf(
                "sparseEncode: rmse of frame %d is %f, size is %d\n",
                whichFrame,
                GetCurrentRMSE(qStatus!!),
                GetCurrentQuadOutputSize(qStatus!!)
            )
            fsize = if (previousImage[0] != null) {
                Roq.theRoQ.NormalFrameSize()
            } else {
                Roq.theRoQ.FirstFrameSize()
            }
            slop += fsize - GetCurrentQuadOutputSize(qStatus!!)
            if (Roq.theRoQ.IsQuiet() == false) {
                i = 0
                while (i < DEAD) {
                    num[i] = 0
                    i++
                }
                j = 0
                i = 0
                while (i < numQuadCels) {
                    if (qStatus!![i].size.toInt() == 8 && qStatus!![i].status != 0) {
                        if (qStatus!![i].status < DEAD) {
                            num[qStatus!![i].status]++
                        }
                        j++
                    }
                    i++
                }
                Common.common.Printf(
                    "sparseEncode: for 08x08 CCC = %d, FCC = %d, MOT = %d, SLD = %d, PAT = %d\n",
                    num[CCC],
                    num[FCC],
                    num[MOT],
                    num[SLD],
                    num[PAT]
                )
                i = 0
                while (i < DEAD) {
                    num[i] = 0
                    i++
                }
                i = 0
                while (i < numQuadCels) {
                    if (qStatus!![i].size.toInt() == 4 && qStatus!![i].status != 0) {
                        if (qStatus!![i].status < DEAD) {
                            num[qStatus!![i].status]++
                        }
                        j++
                    }
                    i++
                }
                Common.common.Printf(
                    "sparseEncode: for 04x04 CCC = %d, FCC = %d, MOT = %d, SLD = %d, PAT = %d\n",
                    num[CCC],
                    num[FCC],
                    num[MOT],
                    num[SLD],
                    num[PAT]
                )
                Common.common.Printf(
                    "sparseEncode: average RMSE = %f, numActiveQuadCels = %d, estSize = %d, slop = %d \n",
                    GetCurrentRMSE(qStatus!!),
                    j,
                    GetCurrentQuadOutputSize(qStatus!!),
                    slop
                )
            }
            Roq.theRoQ.WriteFrame(qStatus!!)
            MakePreviousImage(qStatus!!)

//            Mem_Free(idataA);
//            Mem_Free(idataB);
            //            Mem_Free(flist);
//            Mem_Free(ilist);
            if (newImage != null) {
//                delete
                newImage = null
            }
            whichFrame++
        }

        fun EncodeNothing() {
            var i: Int
            var j: Int
            val osize: Int
            var fsize: Int
            val wtype: Int
            val num = IntArray(DEAD + 1)
            var ilist: IntArray
            val sRMSE: Float
            var flist: FloatArray
            var idataA: ByteArray
            var idataB: ByteArray
            osize = 8
            image = Roq.theRoQ.CurrentImage()
            newImage = null //0;
            pixelsHigh = image!!.pixelsHigh()
            pixelsWide = image!!.pixelsWide()
            dimension2 = 12
            dimension4 = 48
            if (image!!.hasAlpha() && Roq.theRoQ.ParamNoAlpha() == false) {
                dimension2 = 16
                dimension4 = 64
            }
            idataA = ByteArray(16 * 16 * 4) // Mem_Alloc(16 * 16 * 4);
            idataB = ByteArray(16 * 16 * 4) // Mem_Alloc(16 * 16 * 4);
            if (previousImage[0] == null) {
                Common.common.Printf("sparseEncode: sparsely encoding a %d,%d image\n", pixelsWide, pixelsHigh)
            }
            InitImages()
            flist = FloatArray(numQuadCels + 1) // Mem_ClearedAlloc((numQuadCels + 1));
            ilist = IntArray(numQuadCels + 1) // Mem_ClearedAlloc((numQuadCels + 1));
            fsize = 56 * 1024
            if (Roq.theRoQ.NumberOfFrames() > 2) {
                fsize = if (previousImage[0] != null) {
                    Roq.theRoQ.NormalFrameSize()
                } else {
                    Roq.theRoQ.FirstFrameSize()
                }
                if (Roq.theRoQ.HasSound() && fsize > 6000 && previousImage[0] != null) {
                    fsize = 6000
                }
            }
            dyMean = 0
            dxMean = dyMean
            wtype = if (previousImage[0] != null) {
                1
            } else {
                0
            }
            i = 0
            while (i < numQuadCels) {
                j = 0
                while (j < DEAD) {
                    qStatus!![i].snr[j] = 9999.0f
                    j++
                }
                qStatus!![i].mark = false
                if (qStatus!![i].size.toInt() == osize) {
                    if (previousImage[0] != null) {
                        GetData(idataA, qStatus!![i].size.toInt(), qStatus!![i].xat, qStatus!![i].yat, image!!)
                        GetData(
                            idataB,
                            qStatus!![i].size.toInt(),
                            qStatus!![i].xat,
                            qStatus!![i].yat,
                            previousImage[whichFrame and 1]!!
                        )
                        qStatus!![i].snr[MOT] = Snr(idataA, idataB, qStatus!![i].size.toInt())
                    }
                    run {
                        val rsnr = floatArrayOf(0.0f)
                        val status = intArrayOf(0)
                        LowestQuad(qStatus!![i], status, rsnr, wtype)
                        qStatus!![i].status = status[0]
                        qStatus!![i].rsnr = rsnr[0]
                    }
                    if (qStatus!![i].rsnr < 9999) {
                        Roq.theRoQ.MarkQuadx(
                            qStatus!![i].xat,
                            qStatus!![i].yat,
                            qStatus!![i].size.toInt(),
                            qStatus!![i].rsnr,
                            qStatus!![i].status
                        )
                    }
                } else {
                    if (qStatus!![i].size < osize) {
                        qStatus!![i].status = 0
                        qStatus!![i].size = 0
                    } else {
                        qStatus!![i].status = DEP
                        qStatus!![i].rsnr = 0.0f
                    }
                }
                i++
            }
            //
// the quad is complete, so status can now be used for quad decomposition
// the first thing to do is to set it up for all the 4x4 cels to get output
// and then recurse from there to see what's what
//
            sRMSE = GetCurrentRMSE(qStatus!!)
            Common.common.Printf(
                "sparseEncode: rmse of frame %d is %f, size is %d\n",
                whichFrame,
                sRMSE,
                GetCurrentQuadOutputSize(qStatus!!)
            )
            if (Roq.theRoQ.IsQuiet() == false) {
                i = 0
                while (i < DEAD) {
                    num[i] = 0
                    i++
                }
                j = 0
                i = 0
                while (i < numQuadCels) {
                    if (qStatus!![i].size.toInt() == 8 && qStatus!![i].status != 0) {
                        if (qStatus!![i].status < DEAD) {
                            num[qStatus!![i].status]++
                        }
                        j++
                    }
                    i++
                }
                Common.common.Printf(
                    "sparseEncode: for 08x08 CCC = %d, FCC = %d, MOT = %d, SLD = %d, PAT = %d\n",
                    num[CCC],
                    num[FCC],
                    num[MOT],
                    num[SLD],
                    num[PAT]
                )
                i = 0
                while (i < DEAD) {
                    num[i] = 0
                    i++
                }
                i = 0
                while (i < numQuadCels) {
                    if (qStatus!![i].size.toInt() == 4 && qStatus!![i].status != 0) {
                        if (qStatus!![i].status < DEAD) {
                            num[qStatus!![i].status]++
                        }
                        j++
                    }
                    i++
                }
                Common.common.Printf(
                    "sparseEncode: for 04x04 CCC = %d, FCC = %d, MOT = %d, SLD = %d, PAT = %d\n",
                    num[CCC],
                    num[FCC],
                    num[MOT],
                    num[SLD],
                    num[PAT]
                )
                Common.common.Printf(
                    "sparseEncode: average RMSE = %f, numActiveQuadCels = %d, estSize = %d \n",
                    GetCurrentRMSE(qStatus!!),
                    j,
                    GetCurrentQuadOutputSize(qStatus!!)
                )
            }
            Roq.theRoQ.WriteFrame(qStatus!!)
            MakePreviousImage(qStatus!!)

//            Mem_Free(idataA);
//            Mem_Free(idataB);
            //            Mem_Free(flist);
//            Mem_Free(ilist);
            if (newImage != null) {
                //delete newImage;
                newImage = null
            }
            whichFrame++
        }

        fun IRGBtab() {
            initRGBtab++
        }

        fun InitImages() {
            var x: Int
            var y: Int
            var index0: Int
            var index1: Int
            var temp: Int
            var ftemp: Float
            val lutimage: ByteArray
            numQuadCels = (pixelsWide and 0xfff0) * (pixelsHigh and 0xfff0) / (MINSIZE * MINSIZE)
            numQuadCels += numQuadCels / 4 + numQuadCels / 16

//            if (qStatus != null) {
//                Mem_Free(qStatus);
//            }
            qStatus = Array(numQuadCels) { quadcel() } // Mem_ClearedAlloc(numQuadCels);
            InitQStatus()
            //
            if (previousImage[0] != null) {
                pixelsWide = previousImage[0]!!.pixelsWide()
                pixelsHigh = previousImage[0]!!.pixelsHigh()
                temp = whichFrame + 1 and 1
                if (luti == null) {
                    luti = ByteArray(pixelsWide * pixelsHigh) // Mem_Alloc(pixelsWide * pixelsHigh);
                }
                lutimage = previousImage[temp]!!.bitmapData()
                if (Roq.theRoQ.IsQuiet() == false) {
                    Common.common.Printf("initImage: remaking lut image using buffer %d\n", temp)
                }
                index1 = 0
                index0 = index1
                y = 0
                while (y < pixelsHigh) {
                    x = 0
                    while (x < pixelsWide) {
                        ftemp =
                            RMULT * lutimage[index0 + 0] + GMULT * lutimage[index0 + 1] + BMULT * lutimage[index0 + 2]
                        temp = ftemp.toInt()
                        luti!![index1] = temp.toByte()
                        index0 += previousImage[0]!!.samplesPerPixel()
                        index1++
                        x++
                    }
                    y++
                }
            }
        }

        fun QuadX(startX: Int, startY: Int, quadSize: Int) {
            val startSize: Int
            val bigx: Int
            val bigy: Int
            val lowx: Int
            val lowy: Int
            lowy = 0
            lowx = lowy
            bigx = pixelsWide and 0xfff0
            bigy = pixelsHigh and 0xfff0
            if (startX >= lowx && startX + quadSize <= bigx && startY + quadSize <= bigy && startY >= lowy && quadSize <= MAXSIZE) {
                qStatus!![onQuad].size = quadSize.toByte()
                qStatus!![onQuad].xat = startX
                qStatus!![onQuad].yat = startY
                qStatus!![onQuad].rsnr = 999999.0f
                onQuad++
            }
            if (quadSize != MINSIZE) {
                startSize = quadSize shr 1
                QuadX(startX, startY, startSize)
                QuadX(startX + startSize, startY, startSize)
                QuadX(startX, startY + startSize, startSize)
                QuadX(startX + startSize, startY + startSize, startSize)
            }
        }

        fun InitQStatus() {
            var i: Int
            var x: Int
            var y: Int
            i = 0
            while (i < numQuadCels) {
                qStatus!![i].size = 0
                i++
            }
            onQuad = 0
            y = 0
            while (y < pixelsHigh) {
                x = 0
                while (x < pixelsWide) {
                    QuadX(x, y, 16)
                    x += 16
                }
                y += 16
            }
        }

        fun Snr(old: ByteArray, bnew: ByteArray, size: Int): Float {
            var i: Int
            var j: Int
            var fsnr: Float
            /*register*/
            var ind: Int
            var o_p: Int
            var n_p: Int
            ind = 0
            o_p = 0.also { i = it }.also { n_p = it }
            while (i < size) {
                j = 0
                while (j < size) {
                    if (old[o_p + 3].toInt() != 0 || bnew[n_p + 3].toInt() != 0) {
                        ind += RGBADIST(old, bnew, o_p, n_p)
                    }
                    o_p += 4
                    n_p += 4
                    j++
                }
                i++
            }
            fsnr = ind.toFloat()
            fsnr /= (size * size).toFloat()
            fsnr = sqrt(fsnr)
            return fsnr
        }

        fun FvqData(bitmap: ByteArray, size: Int, realx: Int, realy: Int, pquad: quadcel, clamp: Boolean) {
            var x: Int
            var y: Int
            val xLen: Int
            val yLen: Int
            var mblur0: Int
            var ripl: Int
            var bpp: Int
            val fabort: Int
            var temp1: Int
            var lowX: Int
            var lowY: Int
            val onx: Int
            val ony: Int
            var sX: Int
            var sY: Int
            val depthx: Int
            val depthy: Int
            var breakHigh: Int
            var lowestSNR: Float
            var fmblur0: Float
            var scale1: ByteArray
            var bitma2: ByteArray
            var searchY: Int
            var searchX: Int
            val xxMean: Int
            val yyMean: Int
            if (previousImage[0] == null || dimension4 == 64) {
                return
            }
            x = 0
            while (x < size * size) {
                fmblur0 =
                    RMULT * bitmap[x * 4 + 0] + GMULT * bitmap[x * 4 + 1] + BMULT * bitmap[x * 4 + 2]
                luty[x] = fmblur0.toInt().toByte()
                x++
            }
            if (luti == null) {
                pquad.domain = 0x8080
                pquad.snr[FCC] = 9999.0f
                return
            }
            ony = realy - (realy and 0xfff0)
            onx = realx - (realx and 0xfff0)
            xLen = previousImage[0]!!.pixelsWide()
            yLen = previousImage[0]!!.pixelsHigh()
            ripl = xLen - size
            breakHigh = 99999999
            fabort = 0
            lowY = -1
            lowX = lowY
            depthy = 1
            depthx = depthy
            searchY = 8 //16;
            searchX = 8 //32;
            //if (xLen == (yLen*4)) depthx = 2;
            //if (theRoQ.Scaleable()) depthx = depthy = 2;
            if (clamp) {
                searchY = 8
                searchX = searchY
            }
            searchX = searchX * depthx
            searchY = searchY * depthy
            xxMean = dxMean * depthx
            yyMean = dyMean * depthy
            if (realx - xxMean + searchX < 0 || realx - xxMean - searchX + depthx + size > xLen || realy - yyMean + searchY < 0 || realy - yyMean - searchY + depthy + size > yLen) {
                pquad.snr[FCC] = 9999.0f
                return
            }
            -1
            var b_p: Int
            var s_p: Int
            sX = realx - xxMean - searchX + depthx
            b_p = 0.also { s_p = it }
            while (sX <= realx - xxMean + searchX && 0 == fabort) {
                sY = realy - yyMean - searchY + depthy
                while (sY <= realy - yyMean + searchY && breakHigh != 0) {
                    temp1 = xLen * sY + sX
                    if (sX >= 0 && sX + size <= xLen && sY >= 0 && sY + size <= yLen) {
                        bpp = previousImage[0]!!.samplesPerPixel()
                        ripl = (xLen - size) * bpp
                        mblur0 = 0
                        bitma2 = bitmap
                        scale1 = previousImage[whichFrame + 1 and 1]!!.bitmapData()
                        scale1 = scale1.copyOfRange(temp1 * bpp, scale1.size)
                        //		mblur0 = 0;
//		bitma2 = luty;
//		scale1 = luti + temp1;
                        y = 0
                        while (y < size) {
                            x = 0
                            while (x < size) {
                                mblur0 += RGBADIST(bitma2, scale1, b_p, s_p)
                                b_p += 4
                                s_p += 4
                                x++
                            }
                            if (mblur0 > breakHigh) {
                                break
                            }
                            s_p += ripl
                            y++
                        }
                        if (breakHigh > mblur0) {
                            breakHigh = mblur0
                            lowX = sX
                            lowY = sY
                        }
                    }
                    sY += depthy
                }
                sX += depthx
            }
            if (lowX != -1 && lowY != -1) {
                bpp = previousImage[0]!!.samplesPerPixel()
                ripl = (xLen - size) * bpp
                mblur0 = 0
                bitma2 = bitmap
                scale1 = previousImage[whichFrame + 1 and 1]!!.bitmapData()
                scale1 = scale1.copyOfRange((xLen * lowY + lowX) * bpp, scale1.size)
                y = 0
                while (y < size) {
                    x = 0
                    while (x < size) {
                        mblur0 += RGBADIST(bitma2, scale1, b_p, s_p)
                        s_p += 4
                        b_p += 4
                        x++
                    }
                    s_p += ripl
                    y++
                }
                lowestSNR = mblur0.toFloat()
                lowestSNR /= (size * size)
                lowestSNR = sqrt(lowestSNR)
                sX = realx - lowX + 128
                sY = realy - lowY + 128
                if (depthx == 2) {
                    sX = (realx - lowX) / 2 + 128
                }
                if (depthy == 2) {
                    sY = (realy - lowY) / 2 + 128
                }
                pquad.domain = (sX shl 8) + sY
                pquad.snr[FCC] = lowestSNR
            }
        }

        fun GetData( /*unsigned*/
                     iData: ByteArray, qSize: Int, startX: Int, startY: Int, bitmap: NSBitmapImageRep
        ) {
            var x: Int
            var y: Int
            var yoff: Int
            val bpp: Int
            var yend: Int
            var xend: Int
            val iPlane = Array(5) { ByteArray(0) }
            var r: Int
            var g: Int
            var b: Int
            var a: Int
            var data_p = -0
            yend = qSize + startY
            xend = qSize + startX
            if (startY > bitmap.pixelsHigh()) {
                return
            }
            if (yend > bitmap.pixelsHigh()) {
                yend = bitmap.pixelsHigh()
            }
            if (xend > bitmap.pixelsWide()) {
                xend = bitmap.pixelsWide()
            }
            bpp = bitmap.samplesPerPixel()
            if (bitmap.hasAlpha()) {
                iPlane[0] = bitmap.bitmapData()
                y = startY
                while (y < yend) {
                    yoff = y * bitmap.pixelsWide() * bpp
                    x = startX
                    while (x < xend) {
                        r = iPlane[0][yoff + x * bpp + 0].toInt()
                        g = iPlane[0][yoff + x * bpp + 1].toInt()
                        b = iPlane[0][yoff + x * bpp + 2].toInt()
                        a = iPlane[0][yoff + x * bpp + 3].toInt()
                        iData[data_p++] = r.toByte()
                        iData[data_p++] = g.toByte()
                        iData[data_p++] = b.toByte()
                        iData[data_p++] = a.toByte() //TODO:decide on either byte or char.
                        x++
                    }
                    y++
                }
            } else {
                iPlane[0] = bitmap.bitmapData()
                y = startY
                while (y < yend) {
                    yoff = y * bitmap.pixelsWide() * bpp
                    x = startX
                    while (x < xend) {
                        r = iPlane[0][yoff + x * bpp + 0].toInt()
                        g = iPlane[0][yoff + x * bpp + 1].toInt()
                        b = iPlane[0][yoff + x * bpp + 2].toInt()
                        iData[data_p++] = r.toByte()
                        iData[data_p++] = g.toByte()
                        iData[data_p++] = b.toByte()
                        iData[data_p++] = 255.toByte()
                        x++
                    }
                    y++
                }
            }
        }

        fun ComputeMotionBlock(old: ByteArray, bnew: ByteArray, size: Int): Boolean {
            var i: Int
            var j: Int
            var snr: Int
            var o_p: Int
            var n_p: Int
            if (dimension4 == 64) {
//                return 0;	// do not use this for alpha pieces
                return false // this either!
            }
            snr = 0
            i = 0.also { n_p = it }.also { o_p = it }
            while (i < size) {
                j = 0
                while (j < size) {
                    snr += RGBADIST(old, bnew, o_p, n_p)
                    o_p += 4
                    n_p += 4
                    j++
                }
                i++
            }
            snr /= size * size
            return snr <= MOTION_MIN
        }

        fun VqData8(cel: ByteArray, pquad: quadcel) {
            val tempImage = ByteArray(8 * 8 * 4)
            var x: Int
            var y: Int
            var i: Int
            val best: Int
            var temp: Int
            i = 0
            y = 0
            while (y < 4) {
                x = 0
                while (x < 4) {
                    temp = y * 64 + x * 8
                    tempImage[i++] =
                        ((cel[temp + 0] + cel[temp + 4] + cel[temp + 32] + cel[temp + 36]) / 4).toByte()
                    tempImage[i++] =
                        ((cel[temp + 1] + cel[temp + 5] + cel[temp + 33] + cel[temp + 37]) / 4).toByte()
                    tempImage[i++] =
                        ((cel[temp + 2] + cel[temp + 6] + cel[temp + 34] + cel[temp + 38]) / 4).toByte()
                    if (dimension4 == 64) {
                        tempImage[i++] =
                            ((cel[temp + 3] + cel[temp + 7] + cel[temp + 35] + cel[temp + 39]) / 4).toByte()
                    }
                    x++
                }
                y++
            }
            best = BestCodeword(tempImage, dimension4, codebook4)
            pquad.patten[0] = best
            y = 0
            while (y < 8) {
                x = 0
                while (x < 8) {
                    temp = y * 32 + x * 4
                    i = y / 2 * 4 * (dimension2 / 4) + x / 2 * (dimension2 / 4)
                    tempImage[temp + 0] = codebook4[best][i + 0].toInt().toByte()
                    tempImage[temp + 1] = codebook4[best][i + 1].toInt().toByte()
                    tempImage[temp + 2] = codebook4[best][i + 2].toInt().toByte()
                    if (dimension4 == 64) {
                        tempImage[temp + 3] = codebook4[best][i + 3].toInt().toByte()
                    } else {
                        tempImage[temp + 3] = 255.toByte()
                    }
                    x++
                }
                y++
            }
            pquad.snr[SLD] = Snr(cel, tempImage, 8) + 1.0f
        }

        fun VqData4(cel: ByteArray, pquad: quadcel) {
            val tempImage = ByteArray(64)
            var i: Int
            val best: Int
            val bpp: Int

//	if (theRoQ.makingVideo] && previousImage[0]) return self;
            bpp = if (dimension4 == 64) {
                4
            } else {
                3
            }
            i = 0
            while (i < 16) {
                tempImage[i * bpp + 0] = cel[i * 4 + 0]
                tempImage[i * bpp + 1] = cel[i * 4 + 1]
                tempImage[i * bpp + 2] = cel[i * 4 + 2]
                if (dimension4 == 64) {
                    tempImage[i * bpp + 3] = cel[i * 4 + 3]
                }
                i++
            }
            best = BestCodeword(tempImage, dimension4, codebook4)
            pquad.patten[0] = best
            i = 0
            while (i < 16) {
                tempImage[i * 4 + 0] = codebook4[best][i * bpp + 0].toInt().toByte()
                tempImage[i * 4 + 1] = codebook4[best][i * bpp + 1].toInt().toByte()
                tempImage[i * 4 + 2] = codebook4[best][i * bpp + 2].toInt().toByte()
                if (dimension4 == 64) {
                    tempImage[i * 4 + 3] = codebook4[best][i * bpp + 3].toInt().toByte()
                } else {
                    tempImage[i * 4 + 3] = 255.toByte()
                }
                i++
            }
            pquad.snr[PAT] = Snr(cel, tempImage, 4)
        }

        fun VqData2(cel: ByteArray, pquad: quadcel) {
            val tempImage = ByteArray(16)
            val tempOut = ByteArray(64)
            var i: Int
            var j: Int
            var best: Int
            var x: Int
            var y: Int
            var xx: Int
            var yy: Int
            val bpp: Int
            bpp = if (dimension4 == 64) {
                4
            } else {
                3
            }
            j = 1
            yy = 0
            while (yy < 4) {
                xx = 0
                while (xx < 4) {
                    i = 0
                    y = yy
                    while (y < yy + 2) {
                        x = xx
                        while (x < xx + 2) {
                            tempImage[i++] = cel[y * 16 + x * 4 + 0]
                            tempImage[i++] = cel[y * 16 + x * 4 + 1]
                            tempImage[i++] = cel[y * 16 + x * 4 + 2]
                            if (dimension4 == 64) {
                                tempImage[i++] = cel[y * 16 + x * 4 + 3]
                            }
                            x++
                        }
                        y++
                    }
                    best = BestCodeword(tempImage, dimension2, codebook2)
                    pquad.patten[j++] = best
                    i = 0
                    y = yy
                    while (y < yy + 2) {
                        x = xx
                        while (x < xx + 2) {
                            tempOut[y * 16 + x * 4 + 0] = codebook2[best][i++].toInt().toByte()
                            tempOut[y * 16 + x * 4 + 1] = codebook2[best][i++].toInt().toByte()
                            tempOut[y * 16 + x * 4 + 2] = codebook2[best][i++].toInt().toByte()
                            if (dimension4 == 64) {
                                tempOut[y * 16 + x * 4 + 3] = codebook2[best][i++].toInt().toByte()
                            } else {
                                tempOut[y * 16 + x * 4 + 3] = 255.toByte()
                            }
                            x++
                        }
                        y++
                    }
                    xx += 2
                }
                yy += 2
            }
            pquad.snr[CCC] = Snr(cel, tempOut, 4)
        }

        fun MotMeanY(): Int {
            return dyMean
        }

        fun MotMeanX(): Int {
            return dxMean
        }

        fun SetPreviousImage(filename: String, timage: NSBitmapImageRep) {
//	if (previousImage[0]) {
//		delete previousImage[0];
//	}
//	if (previousImage[1]) {
//		delete previousImage[1];
//	}
            Common.common.Printf("setPreviousImage:%s\n", filename)

//	previousImage[0] = new NSBitmapImageRep( );//TODO:remove unimportant stuff.
//	previousImage[1] = new NSBitmapImageRep( );
            whichFrame = 1
            previousImage[0] = timage
            previousImage[1] = timage
            pixelsHigh = previousImage[0]!!.pixelsHigh()
            pixelsWide = previousImage[0]!!.pixelsWide()
            Common.common.Printf("setPreviousImage: %dx%d\n", pixelsWide, pixelsHigh)
        }

        fun BestCodeword( /*unsigned*/
                          tempvector: ByteArray, dimension: Int, codebook: Array<FloatArray>
        ): Int {
            var   /*VQDATA*/dist: Float
            var   /*VQDATA*/bestDist = Float.MAX_VALUE //HUGE;
            val tempvq = FloatArray(64)
            var bestIndex = -1
            for (i in 0 until dimension) {
                tempvq[i] = (tempvector[i].toInt() and 0xFF).toFloat() //unsign
            }
            for (i in 0..255) {
                dist = 0.0f
                var x = 0
                while (x < dimension) {
                    val   /*VQDATA*/r0 = codebook[i][x]
                    val   /*VQDATA*/r1 = tempvq[x]
                    val   /*VQDATA*/g0 = codebook[i][x + 1]
                    val   /*VQDATA*/g1 = tempvq[x + 1]
                    val   /*VQDATA*/b0 = codebook[i][x + 2]
                    val   /*VQDATA*/b1 = tempvq[x + 2]
                    dist += (r0 - r1) * (r0 - r1)
                    if (dist >= bestDist) {
                        x += 3
                        continue
                    }
                    dist += (g0 - g1) * (g0 - g1)
                    if (dist >= bestDist) {
                        x += 3
                        continue
                    }
                    dist += (b0 - b1) * (b0 - b1)
                    if (dist >= bestDist) {
                        x += 3
                        continue
                    }
                    x += 3
                }
                if (dist < bestDist) {
                    bestDist = dist
                    bestIndex = i
                }
            }
            return bestIndex
        }

        private fun VQ(
            numEntries: Int,
            dimension: Int,
            vectors: ByteArray,
            snr: FloatArray,
            codebook: Array<FloatArray>,
            optimize: Boolean
        ) {
            val startMsec = win_shared.Sys_Milliseconds()
            if (numEntries <= 256) {
                //
                // copy the entries into the codebooks
                //
                for (i in 0 until numEntries) {
                    for (j in 0 until dimension) {
                        codebook[i][j] = vectors[j + i * dimension].toFloat()
                    }
                }
                return
            }
            //
            // okay, we need to wittle this down to less than 256 entries
            //

            // get rid of identical entries
            var i: Int
            var j: Int
            var x: Int
            var ibase: Int
            var jbase: Int
            val inuse = BooleanArray(numEntries)
            val snrs = FloatArray(numEntries)
            val indexes = IntArray(numEntries)
            val indexet = IntArray(numEntries)
            var numFinalEntries = numEntries
            i = 0
            while (i < numEntries) {
                inuse[i] = true
                snrs[i] = -1.0f
                indexes[i] = -1
                indexet[i] = -1
                i++
            }
            i = 0
            while (i < numEntries - 1) {
                j = i + 1
                while (j < numEntries) {
                    if (inuse[i] && inuse[j]) {
//				if (!memcmp( &vectors[i*dimension], &vectors[j*dimension], dimension)) {
                        if (vectors.copyOfRange(i * dimension, dimension)
                                .contentEquals(vectors.copyOfRange(j * dimension, dimension))
                        ) {
                            inuse[j] = false
                            numFinalEntries--
                            snr[i] += snr[j]
                        }
                    }
                    j++
                }
                i++
            }
            Common.common.Printf("VQ: has %d entries to process\n", numFinalEntries)

            //
            // are we done?
            //
            var end: Int
            if (numFinalEntries > 256) {
                //
                // find the closest two and eliminate one
                //
                var bestDist = Float.MAX_VALUE //HUGE;
                var dist: Float
                var simport: Float
                var bestIndex = -1
                var bestOtherIndex = 0
                var aentries = 0
                i = 0
                while (i < numEntries - 1) {
                    if (inuse[i]) {
                        end = numEntries
                        if (optimize) {
                            if (numFinalEntries > 8192) {
                                end = i + 32
                            } else if (numFinalEntries > 4096) {
                                end = i + 64
                            } else if (numFinalEntries > 2048) {
                                end = i + 128
                            } else if (numFinalEntries > 1024) {
                                end = i + 256
                            } else if (numFinalEntries > 512) {
                                end = i + 512
                            }
                            if (end > numEntries) {
                                end = numEntries
                            }
                        }
                        ibase = i * dimension
                        j = i + 1
                        while (j < end) {
                            if (inuse[j]) {
                                dist = 0.0f
                                jbase = j * dimension
                                x = 0
                                while (x < dimension) {

// #if 0
                                    // r0 = (float)vectors[ibase+x];
                                    // r1 = (float)vectors[jbase+x];
                                    // g0 = (float)vectors[ibase+x+1];
                                    // g1 = (float)vectors[jbase+x+1];
                                    // b0 = (float)vectors[ibase+x+2];
                                    // b1 = (float)vectors[jbase+x+2];
                                    // dist += idMath::Sqrt16( (r0-r1)*(r0-r1) + (g0-g1)*(g0-g1) + (b0-b1)*(b0-b1) );
// #else
                                    // JDC: optimization
                                    val dr = vectors[ibase + x] - vectors[jbase + x] and 0xFFFF
                                    val dg = vectors[ibase + x + 1] - vectors[jbase + x + 1] and 0xFFFF
                                    val db = vectors[ibase + x + 2] - vectors[jbase + x + 2] and 0xFFFF
                                    dist += idMath.Sqrt16((dr * dr + dg * dg + db * db).toFloat())
                                    x += 3
                                }
                                simport = (snr[i] * snr[j])
                                dist *= simport
                                if (dist < bestDist) {
                                    bestDist = dist
                                    bestIndex = i
                                    bestOtherIndex = j
                                }
                            }
                            j++
                        }
                        snrs[aentries] = bestDist
                        indexes[aentries] = bestIndex
                        indexet[aentries] = bestOtherIndex
                        aentries++
                    }
                    i++
                }

                //
                // until we have reduced it to 256 entries, find one to toss
                //
                do {
                    bestDist = Float.MAX_VALUE //HUGE;
                    bestIndex = -1
                    bestOtherIndex = -1
                    if (optimize) {
                        i = 0
                        while (i < aentries) {
                            if (inuse[indexes[i]] && inuse[indexet[i]]) {
                                if (snrs[i] < bestDist) {
                                    bestDist = snrs[i]
                                    bestIndex = indexes[i]
                                    bestOtherIndex = indexet[i]
                                }
                            }
                            i++
                        }
                    }
                    if (bestIndex == -1 || !optimize) {
                        bestDist = Float.MAX_VALUE //HUGE;
                        bestIndex = -1
                        bestOtherIndex = 0
                        aentries = 0
                        i = 0
                        while (i < numEntries - 1) {
                            if (!inuse[i]) {
                                i++
                                continue
                            }
                            end = numEntries
                            if (optimize) {
                                if (numFinalEntries > 8192) {
                                    end = i + 32
                                } else if (numFinalEntries > 4096) {
                                    end = i + 64
                                } else if (numFinalEntries > 2048) {
                                    end = i + 128
                                } else if (numFinalEntries > 1024) {
                                    end = i + 256
                                } else if (numFinalEntries > 512) {
                                    end = i + 512
                                }
                            }
                            if (end > numEntries) {
                                end = numEntries
                            }
                            ibase = i * dimension
                            j = i + 1
                            while (j < end) {
                                if (!inuse[j]) {
                                    j++
                                    continue
                                }
                                dist = 0.0f
                                jbase = j * dimension
                                simport = (snr[i] * snr[j])
                                val scaledBestDist = (bestDist / simport)
                                x = 0
                                while (x < dimension) {

// #if 0
                                    // r0 = (float)vectors[ibase+x];
                                    // r1 = (float)vectors[jbase+x];
                                    // g0 = (float)vectors[ibase+x+1];
                                    // g1 = (float)vectors[jbase+x+1];
                                    // b0 = (float)vectors[ibase+x+2];
                                    // b1 = (float)vectors[jbase+x+2];
                                    // dist += idMath::Sqrt16( (r0-r1)*(r0-r1) + (g0-g1)*(g0-g1) + (b0-b1)*(b0-b1) );
// #else
                                    // JDC: optimization
                                    val dr = vectors[ibase + x] - vectors[jbase + x] and 0xFFFF
                                    val dg = vectors[ibase + x + 1] - vectors[jbase + x + 1] and 0xFFFF
                                    val db = vectors[ibase + x + 2] - vectors[jbase + x + 2] and 0xFFFF
                                    dist += idMath.Sqrt16((dr * dr + dg * dg + db * db).toFloat())
                                    if (dist > scaledBestDist) {
                                        break
                                    }
                                    x += 3
                                }
                                dist *= simport
                                if (dist < bestDist) {
                                    bestDist = dist
                                    bestIndex = i
                                    bestOtherIndex = j
                                }
                                j++
                            }
                            snrs[aentries] = bestDist
                            indexes[aentries] = bestIndex
                            indexet[aentries] = bestOtherIndex
                            aentries++
                            i++
                        }
                    }
                    //
                    // and lose one
                    //
                    inuse[bestIndex] = false
                    numFinalEntries--
                    snr[bestOtherIndex] += snr[bestIndex]
                    if (numFinalEntries and 511 == 0) {
                        Common.common.Printf("VQ: has %d entries to process\n", numFinalEntries)
                        Session.session.UpdateScreen()
                    }
                } while (numFinalEntries > 256)
            }
            //
            // copy the entries into the codebooks
            //
            var onEntry = 0
            i = 0
            while (i < numEntries) {
                if (inuse[i]) {
                    ibase = i * dimension
                    x = 0
                    while (x < dimension) {
                        codebook[onEntry][x] = (vectors[ibase + x].toInt() and 0xFF).toFloat()
                        x++
                    }
                    if (onEntry == 0) {
                        Common.common.Printf("First vq = %d\n ", i)
                    }
                    if (onEntry == 255) {
                        Common.common.Printf("last vq = %d\n", i)
                    }
                    onEntry++
                }
                i++
            }
            val endMsec = win_shared.Sys_Milliseconds()
            Common.common.Printf("VQ took %d msec\n", endMsec - startMsec)
        }

        private fun Sort(list: FloatArray, intIndex: IntArray, numElements: Int) {
            // 3 is a fairly good choice (Sedgewick)
            var c: Int
            var d: Int
            var stride: Int
            var found: Boolean
            stride = 1
            while (stride <= numElements) {
                stride = stride * STRIDE_FACTOR + 1
            }
            while (stride > STRIDE_FACTOR - 1) { // loop to sort for each value of stride
                stride = stride / STRIDE_FACTOR
                c = stride
                while (c < numElements) {
                    found = false
                    d = c - stride
                    while (d >= 0 && !found) { // move to left until correct place
                        if (list[d] < list[d + stride]) {
                            var ftemp: Float
                            var itemp: Int
                            ftemp = list[d]
                            list[d] = list[d + stride]
                            list[d + stride] = ftemp
                            itemp = intIndex[d]
                            intIndex[d] = intIndex[d + stride]
                            intIndex[d + stride] = itemp
                            d -= stride // jump by stride factor
                        } else {
                            found = true
                        }
                    }
                    c++
                }
            }
        }

        private fun Segment(alist: IntArray, flist: FloatArray, numElements: Int, rmse: Float) {
            var x: Int
            var y: Int
            var yy: Int
            var xx: Int
            var numc: Int
            var onf: Int
            var index: Int
            var temp: Int
            var best: Int
            val a0: Int
            val a1: Int
            val a2: Int
            val a3: Int
            val bpp: Int
            var i: Int
            val len: Int
            val find = ByteArray(16)
            var lineout: ByteArray
            var cbook: ByteArray
            var src: ByteArray
            var dst: ByteArray
            var fy: Float
            var fcr: Float
            var fcb: Float
            var fpcb: idFile
            //	char []cbFile = new char[256], tempcb= new char[256], temptb= new char[256];
            var cbFile: String = ""
            val tempcb: String
            val temptb: String
            var doopen: Boolean
            var y0: Float
            var y1: Float
            var y2: Float
            var y3: Float
            var cr: Float
            var cb: Float
            doopen = false
            a3 = 0
            a2 = a3
            a1 = a2
            a0 = a1
            tempcb = String.format("%s.cb", Roq.theRoQ.CurrentFilename())
            temptb = String.format("%s.tb", Roq.theRoQ.CurrentFilename())
            onf = 0
            //	len = (int)strlen(tempcb);
            len = tempcb.length
            x = 0
            while (x < len) {
                if (tempcb[x] == '\n') {
                    y = x
                    while (y < len) {
                        if (tempcb[y] == '/') {
                            x = y + 1
                            onf++
                            cbFile += tempcb[x]
                        }
                        y++
                    }
                }
                //		cbFile[onf++] = tempcb[x];
                cbFile = TempDump.replaceByIndex(tempcb[x], onf++, cbFile)
                x++
            }
            //	cbFile[onf] = 0;
            cbFile = TempDump.replaceByIndex('0', onf, cbFile)
            lineout = ByteArray(4 * 1024) // Mem_ClearedAlloc(4 * 1024);
            Common.common.Printf("trying %s\n", cbFile)
            fpcb = FileSystem_h.fileSystem.OpenFileRead(cbFile)!!
            if (fpcb == null) {
                doopen = true
                Common.common.Printf("failed....\n")
            } else {
                x = if (dimension2 == 16) {
                    3584
                } else {
                    2560
                }
                if (fpcb.Read(ByteBuffer.wrap(lineout), x) != x) {
                    doopen = true
                    Common.common.Printf("failed....\n")
                }
                FileSystem_h.fileSystem.CloseFile(fpcb)
            }
            if (doopen) {
                Common.common.Printf("segment: making %s\n", cbFile)
                numc = numElements
                if (numElements > numc) {
                    numc = numElements
                }
                onf = 0
                x = 0
                while (x < 256) {
                    y = 0
                    while (y < dimension2) {
                        codebook2[x][y] = 0.0f
                        y++
                    }
                    y = 0
                    while (y < dimension4) {
                        codebook4[x][y] = 0.0f
                        y++
                    }
                    x++
                }
                bpp = image!!.samplesPerPixel()
                cbook =
                    ByteArray(3 * image!!.pixelsWide() * image!!.pixelsHigh()) // Mem_ClearedAlloc(3 * image.pixelsWide() * image.pixelsHigh());
                var snrBook: FloatArray =
                    FloatArray(image!!.pixelsWide() * image!!.pixelsHigh()) // Mem_ClearedAlloc(image.pixelsWide() * image.pixelsHigh());
                dst = cbook
                var numEntries = 0
                var s_p = 0
                var d_p = 0
                i = 0
                while (i < numQuadCels) {
                    if (qStatus!![i].size.toInt() == 8 && qStatus!![i].rsnr >= MIN_SNR * 4) {
                        y = qStatus!![i].yat
                        while (y < qStatus!![i].yat + 8) {
                            x = qStatus!![i].xat
                            while (x < qStatus!![i].xat + 8) {
                                if (qStatus!![i].rsnr == 9999.0f) {
                                    snrBook[numEntries] = 1.0f
                                } else {
                                    snrBook[numEntries] = qStatus!![i].rsnr
                                }
                                numEntries++
                                src = image!!.bitmapData()
                                yy = y
                                while (yy < y + 4) {
                                    xx = x
                                    while (xx < x + 4) {
                                        s_p = yy * (bpp * image!!.pixelsWide()) + xx * bpp
                                        //						memcpy( dst, src, 3); 
                                        System.arraycopy(src, s_p, dst, d_p, 3)
                                        //                                                dst += 3;
                                        d_p += 3
                                        xx++
                                    }
                                    yy++
                                }
                                x += 4
                            }
                            y += 4
                        }
                    }
                    i++
                }
                Common.common.Printf("segment: %d 4x4 cels to vq\n", numEntries)
                VQ(numEntries, dimension4, cbook, snrBook, codebook4, true)
                dst = cbook
                numEntries = 0
                i = 0
                while (i < 256) {
                    y = 0
                    while (y < 4) {
                        x = 0
                        while (x < 4) {
                            snrBook[numEntries] = 1.0f
                            numEntries++
                            yy = y
                            while (yy < y + 2) {
                                xx = x
                                while (xx < x + 2) {
                                    dst[d_p + 0] = codebook4[i][yy * 12 + xx * 3 + 0].toInt().toByte()
                                    dst[d_p + 1] = codebook4[i][yy * 12 + xx * 3 + 1].toInt().toByte()
                                    dst[d_p + 2] = codebook4[i][yy * 12 + xx * 3 + 2].toInt().toByte()
                                    d_p += 3
                                    xx++
                                }
                                yy++
                            }
                            x += 2
                        }
                        y += 2
                    }
                    i++
                }
                Common.common.Printf("segment: %d 2x2 cels to vq\n", numEntries)
                VQ(numEntries, dimension2, cbook, snrBook, codebook2, false)
                index = 0
                onf = 0
                while (onf < 256) {
                    numc = 0
                    fcb = 0.0f
                    fcr = fcb
                    x = 0
                    while (x < 4) {
                        fy =
                            RMULT * codebook2[onf][numc + 0] as Float + GMULT * codebook2[onf][numc + 1] as Float + BMULT * codebook2[onf][numc + 2] as Float + 0.5f
                        if (fy < 0) {
                            fy = 0.0f
                        }
                        if (fy > 255) {
                            fy = 255.0f
                        }
                        fcr += RIEMULT * codebook2[onf][numc + 0] as Float
                        fcr += GIEMULT * codebook2[onf][numc + 1] as Float
                        fcr += BIEMULT * codebook2[onf][numc + 2] as Float
                        fcb += RQEMULT * codebook2[onf][numc + 0] as Float
                        fcb += GQEMULT * codebook2[onf][numc + 1] as Float
                        fcb += BQEMULT * codebook2[onf][numc + 2] as Float
                        lineout[index++] = fy.toInt().toByte()
                        numc += 3
                        x++
                    }
                    fcr = fcr / 4 + 128.5f
                    if (fcr < 0) {
                        fcr = 0.0f
                    }
                    if (fcr > 255) {
                        fcr = 255.0f
                    }
                    fcb = fcb / 4 + 128.5f
                    if (fcb < 0) {
                        fcb = 0.0f
                    }
                    if (fcb > 255) {
                        fcr = 255.0f
                    }
                    //common.Printf(" fcr == %f, fcb == %f\n", fcr, fcb );
                    lineout[index++] = fcr.toInt().toByte()
                    lineout[index++] = fcb.toInt().toByte()
                    onf++
                }
                onf = 0
                while (onf < 256) {
                    y = 0
                    while (y < 4) {
                        x = 0
                        while (x < 4) {
                            numc = 0
                            yy = y
                            while (yy < y + 2) {
                                temp = yy * dimension2 + x * (dimension2 / 4)
                                find[numc++] = (codebook4[onf][temp + 0] + 0.50f).toInt().toByte()
                                find[numc++] = (codebook4[onf][temp + 1] + 0.50f).toInt().toByte()
                                find[numc++] = (codebook4[onf][temp + 2] + 0.50f).toInt().toByte()
                                find[numc++] = (codebook4[onf][temp + 3] + 0.50f).toInt().toByte()
                                find[numc++] = (codebook4[onf][temp + 4] + 0.50f).toInt().toByte()
                                find[numc++] = (codebook4[onf][temp + 5] + 0.50f).toInt().toByte()
                                yy++
                            }
                            lineout[index++] = BestCodeword(find, dimension2, codebook2).toByte()
                            x += 2
                        }
                        y += 2
                    }
                    onf++
                }
                fpcb = FileSystem_h.fileSystem.OpenFileWrite(cbFile)!!
                Common.common.Printf("made up %d entries\n", index)
                fpcb.Write(ByteBuffer.wrap(lineout), index)
                FileSystem_h.fileSystem.CloseFile(fpcb)
                Common.common.Printf("finished write\n")
            }
            y = 0
            while (y < 256) {
                x = y * 6
                y0 = lineout[x++].toFloat()
                y1 = lineout[x++].toFloat()
                y2 = lineout[x++].toFloat()
                y3 = lineout[x++].toFloat()
                cb = lineout[x++].toFloat()
                cb -= 128f
                cr = lineout[x].toFloat()
                cr -= 128f
                x = 0
                codebook2[y][x++] = glimit(y0 + 1.40200f * cr)
                codebook2[y][x++] = glimit(y0 - 0.34414f * cb - 0.71414f * cr)
                codebook2[y][x++] = glimit(y0 + 1.77200f * cb)
                codebook2[y][x++] = glimit(y1 + 1.40200f * cr)
                codebook2[y][x++] = glimit(y1 - 0.34414f * cb - 0.71414f * cr)
                codebook2[y][x++] = glimit(y1 + 1.77200f * cb)
                codebook2[y][x++] = glimit(y2 + 1.40200f * cr)
                codebook2[y][x++] = glimit(y2 - 0.34414f * cb - 0.71414f * cr)
                codebook2[y][x++] = glimit(y2 + 1.77200f * cb)
                codebook2[y][x++] = glimit(y3 + 1.40200f * cr)
                codebook2[y][x++] = glimit(y3 - 0.34414f * cb - 0.71414f * cr)
                codebook2[y][x++] = glimit(y3 + 1.77200f * cb)
                y++
            }
            index = 6 * 256
            onf = 0
            while (onf < 256) {
                y = 0
                while (y < 4) {
                    x = 0
                    while (x < 4) {
                        best = lineout[index++].toInt()
                        numc = 0
                        yy = y
                        while (yy < y + 2) {
                            temp = yy * dimension2 + x * (dimension2 / 4)
                            codebook4[onf][temp + 0] = codebook2[best][numc++] //r
                            codebook4[onf][temp + 1] = codebook2[best][numc++] //g
                            codebook4[onf][temp + 2] = codebook2[best][numc++] //b
                            codebook4[onf][temp + 3] = codebook2[best][numc++] //r a
                            codebook4[onf][temp + 4] = codebook2[best][numc++] //g r
                            codebook4[onf][temp + 5] = codebook2[best][numc++] //b g
                            yy++
                        }
                        x += 2
                    }
                    y += 2
                }
                onf++
            }
            Roq.theRoQ.WriteCodeBook(lineout)
            //PrepareCodeBook();
        }

        private fun LowestQuad(qtemp: quadcel, status: IntArray, snr: FloatArray, bweigh: Int) {
            var wtemp: Float
            val quickadd = FloatArray(DEAD)
            var i: Int
            quickadd[CCC] = 1.0f
            quickadd[SLD] = 1.0f
            quickadd[MOT] = 1.0f
            quickadd[FCC] = 1.0f
            quickadd[PAT] = 1.0f
            /*
             if (slop > theRoQ->NormalFrameSize()) {
             quickadd[CCC] = 0.5f;
             quickadd[PAT] = 1.0f;
             }
             */wtemp = 99999.0f
            i = DEAD - 1
            while (i > 0) {
                if (qtemp.snr[i] * quickadd[i] < wtemp) {
                    status[0] = i
                    snr[0] = qtemp.snr[i]
                    wtemp = qtemp.snr[i] * quickadd[i]
                }
                i--
            }
            if (qtemp.mark) {
                status[0] = MOT
            }
        }

        private fun MakePreviousImage(pquad: Array<quadcel>) {
            var i: Int
            var dy: Int
            var dx: Int
            var pluck: Int
            var size: Int
            var ind: Int
            var xx: Int
            var yy: Int
            val pWide: Int
            var x: Int
            var y: Int
            val rgbmap: ByteArray
            var idataA: ByteArray
            val fccdictionary: ByteArray
            var diff: Boolean
            i = 0
            while (i < 256) {
                used4[i] = false
                used2[i] = used4[i]
                i++
            }
            pWide = pixelsWide and 0xfff0
            if (previousImage[0] == null) {
                previousImage[0] = NSBitmapImageRep(pWide, pixelsHigh and 0xfff0)
                previousImage[1] = NSBitmapImageRep(pWide, pixelsHigh and 0xfff0)
            }
            rgbmap = previousImage[whichFrame and 1]!!.bitmapData()
            fccdictionary = if (whichFrame and 1 == 1) {
                previousImage[0]!!.bitmapData()
            } else {
                previousImage[1]!!.bitmapData()
            }
            idataA = ByteArray(16 * 16 * 4) // Mem_Alloc(16 * 16 * 4);
            i = 0
            while (i < numQuadCels) {
                diff = false
                size = pquad[i].size.toInt()
                if (size != 0) {
                    when (pquad[i].status) {
                        DEP -> {}
                        SLD -> {
                            ind = pquad[i].patten[0]
                            used4[ind] = true
                            dy = 0
                            while (dy < size) {
                                pluck = ((dy + pquad[i].yat) * pWide + pquad[i].xat) * 4
                                dx = 0
                                while (dx < size) {
                                    xx = (dy shr 1) * dimension2 + (dx shr 1) * (dimension2 / 4)
                                    if (rgbmap[pluck + 0].toFloat() != codebook4[ind][xx + 0]) {
                                        diff = true
                                    }
                                    if (rgbmap[pluck + 1].toFloat() != codebook4[ind][xx + 1]) {
                                        diff = true
                                    }
                                    if (rgbmap[pluck + 2].toFloat() != codebook4[ind][xx + 2]) {
                                        diff = true
                                    }
                                    if (dimension4 == 64 && rgbmap[pluck + 3].toFloat() != codebook4[ind][xx + 3]) {
                                        diff = true
                                    }
                                    rgbmap[pluck + 0] = codebook4[ind][xx + 0].toInt().toByte()
                                    rgbmap[pluck + 1] = codebook4[ind][xx + 1].toInt().toByte()
                                    rgbmap[pluck + 2] = codebook4[ind][xx + 2].toInt().toByte()
                                    if (dimension4 == 64) {
                                        rgbmap[pluck + 3] = codebook4[ind][xx + 3].toInt().toByte()
                                    } else {
                                        rgbmap[pluck + 3] = 255.toByte()
                                    }
                                    pluck += 4
                                    dx++
                                }
                                dy++
                            }
                            if (diff == false && whichFrame != 0) {
                                Common.common.Printf("drawImage: SLD just changed the same thing\n")
                            }
                        }

                        PAT -> {
                            ind = pquad[i].patten[0]
                            used4[ind] = true
                            dy = 0
                            while (dy < size) {
                                pluck = ((dy + pquad[i].yat) * pWide + pquad[i].xat) * 4
                                dx = 0
                                while (dx < size) {
                                    xx = dy * size * (dimension2 / 4) + dx * (dimension2 / 4)
                                    if (rgbmap[pluck + 0].toFloat() != codebook4[ind][xx + 0]) {
                                        diff = true
                                    }
                                    if (rgbmap[pluck + 1].toFloat() != codebook4[ind][xx + 1]) {
                                        diff = true
                                    }
                                    if (rgbmap[pluck + 2].toFloat() != codebook4[ind][xx + 2]) {
                                        diff = true
                                    }
                                    if (dimension4 == 64 && rgbmap[pluck + 3].toFloat() != codebook4[ind][xx + 3]) {
                                        diff = true
                                    }
                                    rgbmap[pluck + 0] = codebook4[ind][xx + 0].toInt().toByte()
                                    rgbmap[pluck + 1] = codebook4[ind][xx + 1].toInt().toByte()
                                    rgbmap[pluck + 2] = codebook4[ind][xx + 2].toInt().toByte()
                                    if (dimension4 == 64) {
                                        rgbmap[pluck + 3] = codebook4[ind][xx + 3].toInt().toByte()
                                    } else {
                                        rgbmap[pluck + 3] = 255.toByte()
                                    }
                                    pluck += 4
                                    dx++
                                }
                                dy++
                            }
                            if (diff == false && whichFrame != 0) {
                                Common.common.Printf("drawImage: PAT just changed the same thing\n")
                            }
                        }

                        CCC -> {
                            dx = 1
                            yy = 0
                            while (yy < 4) {
                                xx = 0
                                while (xx < 4) {
                                    ind = pquad[i].patten[dx++]
                                    used2[ind] = true
                                    dy = 0
                                    y = yy
                                    while (y < yy + 2) {
                                        x = xx
                                        while (x < xx + 2) {
                                            pluck = ((y + pquad[i].yat) * pWide + (pquad[i].xat + x)) * 4
                                            if (rgbmap[pluck + 0].toFloat() != codebook2[ind][dy + 0]) {
                                                diff = true
                                            }
                                            if (rgbmap[pluck + 1].toFloat() != codebook2[ind][dy + 1]) {
                                                diff = true
                                            }
                                            if (rgbmap[pluck + 2].toFloat() != codebook2[ind][dy + 2]) {
                                                diff = true
                                            }
                                            if (dimension4 == 64 && rgbmap[pluck + 3].toFloat() != codebook2[ind][dy + 3]
                                            ) {
                                                diff = true
                                            }
                                            rgbmap[pluck + 0] = codebook2[ind][dy + 0].toInt().toByte()
                                            rgbmap[pluck + 1] = codebook2[ind][dy + 1].toInt().toByte()
                                            rgbmap[pluck + 2] = codebook2[ind][dy + 2].toInt().toByte()
                                            if (dimension4 == 64) {
                                                rgbmap[pluck + 3] = codebook2[ind][dy + 3].toInt().toByte()
                                                dy += 4
                                            } else {
                                                rgbmap[pluck + 3] = 255.toByte()
                                                dy += 3
                                            }
                                            x++
                                        }
                                        y++
                                    }
                                    xx += 2
                                }
                                yy += 2
                            }
                            if (diff == false && whichFrame != 0) {
                                /*
                                 common->Printf("drawImage: CCC just changed the same thing\n");
                                 common->Printf("sparseEncode: something is wrong here\n");
                                 common->Printf("xat:    %d\n", pquad[i].xat);
                                 common->Printf("yat:    %d\n", pquad[i].yat);
                                 common->Printf("size    %d\n", pquad[i].size);
                                 common->Printf("type:   %d\n", pquad[i].status);
                                 common->Printf("motsnr: %0f\n", pquad[i].snr[FCC]);
                                 common->Printf("cccsnr: %0f\n", pquad[i].snr[CCC]);
                                 common->Printf("rmse:   %0f\n", pquad[i].rsnr);
                                 common->Printf("pat0:   %0d\n", pquad[i].patten[1]);
                                 common->Printf("pat1:   %0d\n", pquad[i].patten[2]);
                                 common->Printf("pat2:   %0d\n", pquad[i].patten[3]);
                                 common->Printf("pat3:   %0d\n", pquad[i].patten[4]);
                                 //exit(1);
                                 */
                            }
                        }

                        FCC -> {
                            dx = pquad[i].xat - ((pquad[i].domain shr 8) - 128)
                            dy = pquad[i].yat - ((pquad[i].domain and 0xff) - 128)
                            if (image!!.pixelsWide() == image!!.pixelsHigh() * 4) {
                                dx = pquad[i].xat - ((pquad[i].domain shr 8) - 128) * 2
                            }
                            if (Roq.theRoQ.Scaleable()) {
                                dx = pquad[i].xat - ((pquad[i].domain shr 8) - 128) * 2
                                dy = pquad[i].yat - ((pquad[i].domain and 0xff) - 128) * 2
                            }
                            //				if (pquad[i].yat == 0) common->Printf("dx = %d, dy = %d, xat = %d\n", dx, dy, pquad[i].xat);
                            ind = (dy * pWide + dx) * 4
                            dy = 0
                            while (dy < size) {
                                pluck = ((dy + pquad[i].yat) * pWide + pquad[i].xat) * 4
                                dx = 0
                                while (dx < size) {
                                    if (rgbmap[pluck + 0] != fccdictionary[ind + 0]) {
                                        diff = true
                                    }
                                    if (rgbmap[pluck + 1] != fccdictionary[ind + 1]) {
                                        diff = true
                                    }
                                    if (rgbmap[pluck + 2] != fccdictionary[ind + 2]) {
                                        diff = true
                                    }
                                    rgbmap[pluck + 0] = fccdictionary[ind + 0]
                                    rgbmap[pluck + 1] = fccdictionary[ind + 1]
                                    rgbmap[pluck + 2] = fccdictionary[ind + 2]
                                    rgbmap[pluck + 3] = fccdictionary[ind + 3]
                                    pluck += 4
                                    ind += 4
                                    dx++
                                }
                                ind += (pWide - size) * 4
                                dy++
                            }
                        }

                        MOT -> {}
                        else -> Common.common.Error("bad code!!\n")
                    }
                }
                i++
            }
            if (whichFrame == 0) {
//			memcpy( previousImage[1].bitmapData(), previousImage[0].bitmapData(), pWide*(pixelsHigh & 0xfff0)*4);
                System.arraycopy(
                    previousImage[0]!!.bitmapData(),
                    0,
                    previousImage[1]!!.bitmapData(),
                    0,
                    pWide * (pixelsHigh and 0xfff0) * 4
                )
            }
            x = 0
            y = 0
            i = 0
            while (i < 256) {
                if (used4[i]) {
                    x++
                }
                if (used2[i]) {
                    y++
                }
                i++
            }
            if (Roq.theRoQ.IsQuiet() == false) {
                Common.common.Printf("drawImage: used %d 4x4 and %d 2x2 VQ cels\n", x, y)
            }
        }

        private fun GetCurrentRMSE(pquad: Array<quadcel>): Float {
            var i: Int
            var j: Int
            var totalbits: Float
            totalbits = 0.0f
            j = 0
            i = 0
            while (i < numQuadCels) {
                if (pquad[i].size.toInt() != 0 && pquad[i].status != 0 && pquad[i].status != DEAD) {
                    if (pquad[i].size.toInt() == 8) {
                        totalbits += (pquad[i].rsnr * 4)
                        j += 4
                    }
                    if (pquad[i].size.toInt() == 4) {
                        totalbits += (pquad[i].rsnr * 1)
                        j += 1
                    }
                }
                i++
            }
            totalbits /= j.toFloat()
            return totalbits
        }

        private fun GetCurrentQuadOutputSize(pquad: Array<quadcel>): Int {
            var totalbits: Int
            var i: Int
            val totalbytes: Int
            val quickadd = IntArray(DEAD + 1)
            totalbits = 0
            quickadd[DEP] = 2
            quickadd[SLD] = 10
            quickadd[PAT] = 10
            quickadd[CCC] = 34
            quickadd[MOT] = 2
            quickadd[FCC] = 10
            quickadd[DEAD] = 0
            i = 0
            while (i < numQuadCels) {
                if (pquad[i].size.toInt() != 0 && pquad[i].size < 16) {
                    totalbits += quickadd[pquad[i].status]
                }
                i++
            }
            totalbytes = (totalbits shr 3) + 2
            return totalbytes
        }

        private fun AddQuad(pquad: Array<quadcel>, lownum: Int): Int {
            var lownum = lownum
            var i: Int
            val nx: Int
            val nsize: Int
            var newsnr: Float
            val cmul: Float
            var idataA: ByteArray
            var idataB: ByteArray
            if (lownum != -1) {
                if (pquad[lownum].size.toInt() == 8) {
                    nx = 1
                    nsize = 4
                    cmul = 1.0f
                } else {
                    nx = 5
                    nsize = 8
                    cmul = 4.0f
                }
                newsnr = 0.0f
                idataA = ByteArray(8 * 8 * 4) // Mem_Alloc(8 * 8 * 4);
                idataB = ByteArray(8 * 8 * 4) // Mem_Alloc(8 * 8 * 4);
                i = lownum + 1
                while (i < lownum + nx * 4 + 1) {
                    pquad[i].size = nsize.toByte()
                    GetData(idataA, pquad[i].size.toInt(), pquad[i].xat, pquad[i].yat, image!!)
                    VqData4(idataA, pquad[i])
                    VqData2(idataA, pquad[i])
                    if (previousImage[0] != null) {
                        FvqData(
                            idataA,
                            pquad[i].size.toInt(),
                            pquad[i].xat,
                            pquad[i].yat,
                            pquad[i],
                            true
                        )
                        GetData(
                            idataB,
                            pquad[i].size.toInt(),
                            pquad[i].xat,
                            pquad[i].yat,
                            previousImage[whichFrame and 1]!!
                        )
                        pquad[i].snr[MOT] = Snr(idataA, idataB, pquad[i].size.toInt())
                        if (ComputeMotionBlock(
                                idataA,
                                idataB,
                                pquad[i].size.toInt()
                            ) && !Roq.theRoQ.IsLastFrame() && !detail
                        ) {
                            pquad[i].mark = true
                        }
                    }
                    run {
                        val rsnr = floatArrayOf(0.0f)
                        val status = intArrayOf(0)
                        LowestQuad(pquad[i], status, rsnr, 1) //true);
                        pquad[i].status = status[0]
                        pquad[i].rsnr = rsnr[0]
                    }
                    newsnr += pquad[i].rsnr
                    i += nx
                }
                //                Mem_Free(idataA);
                newsnr /= 4f
                run {
                    val rsnr = floatArrayOf(0.0f)
                    val status = intArrayOf(0)
                    LowestQuad(pquad[lownum], status, rsnr, 0) //false);
                    pquad[lownum].status = status[0]
                    pquad[lownum].rsnr = rsnr[0]
                }
                if (pquad[lownum + nx * 0 + 1].status == MOT && pquad[lownum + nx * 1 + 1].status == MOT && pquad[lownum + nx * 2 + 1].status == MOT && pquad[lownum + nx * 3 + 1].status == MOT && nsize == 4
                ) {
                    newsnr = 9999.0f
                    pquad[lownum].status = MOT
                }
                if (pquad[lownum].rsnr > newsnr) {
                    pquad[lownum].status = DEP
                    pquad[lownum].rsnr = 0.0f
                    i = lownum + 1
                    while (i < lownum + nx * 4 + 1) {
                        Roq.theRoQ.MarkQuadx(
                            pquad[i].xat,
                            pquad[i].yat,
                            nsize,
                            pquad[i].rsnr,
                            qStatus!![i].status
                        )
                        i += nx
                    }
                } else {
                    Roq.theRoQ.MarkQuadx(
                        pquad[lownum].xat,
                        pquad[lownum].yat,
                        nsize * 2,
                        pquad[lownum].rsnr,
                        qStatus!![lownum].status
                    )
                    pquad[lownum + nx * 0 + 1].status = 0
                    pquad[lownum + nx * 1 + 1].status = 0
                    pquad[lownum + nx * 2 + 1].status = 0
                    pquad[lownum + nx * 3 + 1].status = 0
                    pquad[lownum + nx * 0 + 1].size = 0
                    pquad[lownum + nx * 1 + 1].size = 0
                    pquad[lownum + nx * 2 + 1].size = 0
                    pquad[lownum + nx * 3 + 1].size = 0
                }
            } else {
                lownum = -1
            }
            return lownum
        }

        companion object {
            /* Because Shellsort is a variation on Insertion Sort, it has the same
         * inconsistency that I noted in the InsertionSort class.  Notice where I
         * subtract a move to compensate for calling a swap for visual purposes.
         */
            private const val STRIDE_FACTOR = 3 // good value for stride factor is not well-understood
        }

        // ~codec();
        init {
            var i: Int
            Common.common.Printf("init: initing.....\n")
            codebooksize = 256
            codebook2 = Array(256) { FloatArray(16) } // Mem_ClearedAlloc(256);
            codebook4 = Array(256) { FloatArray(64) } // Mem_ClearedAlloc(256);
            previousImage[0] = null //0;
            previousImage[1] = null //0;
            image = null //0;
            whichFrame = 0
            qStatus = null //0;
            luti = null //0;
            overAmount = 0
            codebookmade = 0
            slop = 0
        }
    }
}
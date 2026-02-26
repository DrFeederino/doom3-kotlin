package neo.Tools.Compilers.RoqVQ

import neo.Renderer.Image_files
import neo.framework.Common

class NSBitmapImageRep {
    //    static class NSBitmapImageRep {
    private var bmap: ByteArray = ByteArray(0)
    private var height: Int
    private var   /*ID_TIME_T*/timestamp: Long = 0
    private var width: Int

    //
    //
    constructor() {
        height = 0
        width = height
        timestamp = 0
    }

    constructor(filename: String) {
        val w = intArrayOf(0)
        val h = intArrayOf(0)
        val t = longArrayOf(0)
        Image_files.R_LoadImage(filename, bmap, w, h, t, false)
        width = w[0]
        height = h[0]
        timestamp = t[0]
        if (0 == width || 0 == height) {
            Common.common.FatalError("roqvq: unable to load image %s\n", filename)
        }
    }

    constructor(wide: Int, high: Int) {
        bmap = ByteArray(wide * high * 4) // Mem_ClearedAlloc(wide * high * 4);
        width = wide
        height = high
    }

    // ~NSBitmapImageRep();
    // NSBitmapImageRep &	operator=( const NSBitmapImageRep &a );
    fun set(a: NSBitmapImageRep): NSBitmapImageRep {

        // check for assignment to self
        if (this == a) {
            return this
        }
        if (bmap.isNotEmpty()) {
            bmap = ByteArray(0) //Mem_Free(bmap);
        }
        bmap = ByteArray(a.width * a.height * 4) // Mem_Alloc(a.width * a.height * 4);
        //        System.arraycopy(a.bmap, 0, this.bmap, 0, a.width * a.height * 4);
        for (i in 0 until a.width * a.height * 4) {
            bmap[i] = a.bmap[i]
        }
        width = a.width
        height = a.height
        timestamp = a.timestamp
        return this
    }

    fun samplesPerPixel(): Int {
        return 4
    }

    fun pixelsWide(): Int {
        return width
    }

    fun pixelsHigh(): Int {
        return height
    }

    fun bitmapData(): ByteArray {
        return bmap
    }

    fun hasAlpha(): Boolean {
        return false
    }

    fun isPlanar(): Boolean {
        return false
    } //    };
}
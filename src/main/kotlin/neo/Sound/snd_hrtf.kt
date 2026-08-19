package neo.Sound

import neo.framework.FileSystem_h
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import kotlin.math.atan2
import kotlin.math.roundToInt

object snd_hrtf {
    /*
    ===============================================================================

    E3-ALPHA HRTF

    The 2002 alpha leak ships precomputed head-related impulse responses in
    sound/hrtf/H0e%03da.dat: 128 taps per channel, interleaved 16-bit
    little-endian stereo, no header, at 10-degree azimuth steps.
    0 = front, positive = right, 180 = back; the left hemisphere is the
    L/R swap of the mirrored azimuth (verified from the data: the right
    channel carries the strong direct-path taps for azimuths 10..130).

    A spatialized mono channel's streaming blocks are convolved with the
    IR for its current listener direction, producing a binaural stereo
    pair that is played on a relative (listener-locked) AL source, so
    openal-soft applies no further spatialization to it.

    ===============================================================================
    */
    const val TAPS = 128
    const val NUM_AZIMUTHS = 19
    const val TAIL = TAPS - 1

    private val left = Array(NUM_AZIMUTHS) { FloatArray(TAPS) }
    private val right = Array(NUM_AZIMUTHS) { FloatArray(TAPS) }
    private var loaded = false
    private var loadFailed = false

    // the cvar check is done by the caller-side Enabled() consumers
    fun IsActive(): Boolean {
        if (!loaded && !loadFailed) {
            if (Load()) {
                loaded = true
            } else {
                loadFailed = true
            }
        }
        return loaded
    }

    private fun Load(): Boolean {
        for (az in 0 until NUM_AZIMUTHS) {
            val path = "sound/hrtf/H0e" + (az * 10).toString().padStart(3, '0') + "a.dat"
            val f = FileSystem_h.fileSystem.OpenFileRead(path) ?: return false
            if (f.Length() != TAPS * 4) {
                return false
            }
            val bb = ByteBuffer.allocate(TAPS * 4).order(ByteOrder.LITTLE_ENDIAN)
            if (f.Read(bb) != TAPS * 4) {
                return false
            }
            bb.rewind()
            for (i in 0 until TAPS) {
                left[az][i] = bb.getShort(i * 4).toInt() / 32768.0f
                right[az][i] = bb.getShort(i * 4 + 2).toInt() / 32768.0f
            }
        }
        return true
    }

    /*
    ===================
    snd_hrtf::ConvolveBlock

    Convolve the mono input block (n samples, read from inBuf) with the
    impulse response for the direction (dx, dy) from the listener to the
    source in doom world space (x = forward, y = left, z = up) and write
    the binaural stereo pair (2*n shorts) to out.

    tail holds the last TAIL input samples across blocks (tail[0] = newest)
    so the filter state stays continuous between streamed blocks.
    ===================
    */
    fun ConvolveBlock(tail: FloatArray, dx: Float, dy: Float, inBuf: FloatBuffer, n: Int, out: ShortBuffer) {
        var azDeg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
        var swap = false
        if (azDeg < 0.0f) { // left hemisphere: mirror azimuth with L/R swapped
            azDeg = -azDeg
            swap = true
        }
        var idx = (azDeg / 10.0f + 0.5f).roundToInt()
        if (idx < 0) {
            idx = 0
        } else if (idx >= NUM_AZIMUTHS) {
            idx = NUM_AZIMUTHS - 1
        }
        val hl = if (swap) right[idx] else left[idx]
        val hr = if (swap) left[idx] else right[idx]

        val arrN = FloatArray(n)
        for (i in 0 until n) {
            arrN[i] = inBuf.get(i)
        }

        for (o in 0 until n) {
            var l = 0.0f
            var r = 0.0f
            for (k in 0 until TAPS) {
                val t = o - k
                val x = if (t >= 0) arrN[t] else tail[-t - 1]
                l += hl[k] * x
                r += hr[k] * x
            }
            out.put(o * 2, clamp16(l))
            out.put(o * 2 + 1, clamp16(r))
        }

        // the newest TAIL samples of the stream become the new history
        if (n >= TAIL) {
            System.arraycopy(arrN, n - TAIL, tail, 0, TAIL)
        } else {
            System.arraycopy(tail, 0, tail, n, TAIL - n)
            System.arraycopy(arrN, 0, tail, 0, n)
        }
    }

    private fun clamp16(v: Float): Short {
        return if (v < -32768.0f) Short.MIN_VALUE else if (v > 32767.0f) Short.MAX_VALUE else v.roundToInt().toShort()
    }
}

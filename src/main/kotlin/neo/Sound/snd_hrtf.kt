package neo.Sound

import neo.framework.FileSystem_h
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import kotlin.math.atan2
import kotlin.math.roundToInt
import kotlin.math.sqrt

object snd_hrtf {
    /*
    ===============================================================================

    E3-ALPHA HRTF

    The 2002 alpha leak ships precomputed head-related impulse responses in
    sound/hrtf/H0e%03da.dat (its doom.exe carries the loader format string
    "/sound/hrtf/H0e%02d0a.dat" and the log lines "A - adding HRTF filter %s
    (%d taps)" / "? - maximum HRTF sample was %d"): 19 files at 10-degree
    azimuth steps, 512 B = 128 taps per channel, interleaved 16-bit
    little-endian stereo, no header.

    The set is the MIT KEMAR compact measurements (Gardner & Martin 1994,
    128 taps per ear at 44.1 kHz): their report pins the convention —
    "H0e090a.dat contains the left and right ear impulse responses for a
    source directly to the RIGHT of the listener" — so 0 = front, positive
    azimuth = right, 180 = back, and the interleave is [left, right]
    (confirmed against the data: the second stream carries the direct-path
    tap for every azimuth 10..170 and the first stream's onset delay peaks
    at ~29 taps for 90 deg, the contralateral ITD; both streams are
    identical at 0/180). The left hemisphere is the L/R swap of the
    mirrored azimuth. Applied only for s_numberOfSpeakers 2 — this is
    headphone material, as in the 2002 engine, which had no s_hrtf cvar.

    The leak ships the taps at full scale (per-ear L2 norm ~6, sum|tap| ~60):
    convolving at face value boosts stationary content ~16 dB and hard-clips
    it. The 2002 engine (doom.exe, decompiled 2026-09-10) never convolves the
    raw taps either: it FFTs each ear to 8192 bins, builds a magnitude-
    compressed cross-ear relative response, and reduces it to a sparse <=32
    tap filter normalized by its leading-tap sum. This port keeps the natural
    per-ear pair but applies the same load-time taming: each azimuth's pair
    is scaled by 1/max(L2L, L2R) — power-controlled like the original's
    normalization, and unlike per-ear unit-L2 it PRESERVES the interaural
    level difference — and at runtime the two IRs bracketing the azimuth are
    linearly blended per block (the 2002 BlendFilter behavior), with a
    soft-knee limiter on the output instead of a bare clamp.

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
    private val blendL = FloatArray(TAPS) // per-block interpolated IR pair,
    private val blendR = FloatArray(TAPS) // like the 2002 BlendFilter
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
            normalizePair(left[az], right[az])
        }
        return true
    }

    /*
    Scale a filter pair by 1/max(L2L, L2R): power-neutral for the louder ear
    (the leak's taps carry ~16 dB of gain at face value, which clipped every
    spatialized mono sound), while the quieter ear keeps its natural deficit —
    the interaural level difference is a primary azimuth cue and must NOT be
    equalized away (per-ear unit-L2 did exactly that and flattened the image).
    */
    private fun normalizePair(l: FloatArray, r: FloatArray) {
        var e = 0.0f
        for (i in 0 until TAPS) {
            e += l[i] * l[i]
        }
        var maxL2 = sqrt(e)
        e = 0.0f
        for (i in 0 until TAPS) {
            e += r[i] * r[i]
        }
        if (sqrt(e) > maxL2) {
            maxL2 = sqrt(e)
        }
        val g = 1.0f / maxL2
        for (i in 0 until TAPS) {
            l[i] *= g
            r[i] *= g
        }
    }

    /*
    ===================
    snd_hrtf::ConvolveBlock

    Convolve the mono input block (n samples, read from inBuf) with the
    impulse response for the source direction (dx, dy) in LISTENER space —
    dx = forward component, dy = rightward component; the caller projects
    the world-space offset onto the listener axis rows (azimuth 0 = front,
    90 = right) — and write the binaural stereo pair (2*n shorts) to out.

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
        } // fractional azimuth index: blend the two bracketing IR pairs for this
        // block (the 2002 engine interpolated its sparse filters the same way,
        // per mix update, instead of snapping between azimuth tables)
        val f = azDeg / 10.0f
        var i0 = f.toInt()
        if (i0 < 0) {
            i0 = 0
        } else if (i0 > NUM_AZIMUTHS - 2) {
            i0 = NUM_AZIMUTHS - 2
        }
        var frac = f - i0
        if (frac < 0.0f) {
            frac = 0.0f
        } else if (frac > 1.0f) {
            frac = 1.0f
        }
        val l0 = if (swap) right[i0] else left[i0]
        val r0 = if (swap) left[i0] else right[i0]
        val l1 = if (swap) right[i0 + 1] else left[i0 + 1]
        val r1 = if (swap) left[i0 + 1] else right[i0 + 1]
        val hl = blendL
        val hr = blendR
        for (i in 0 until TAPS) {
            hl[i] = l0[i] + (l1[i] - l0[i]) * frac
            hr[i] = r0[i] + (r1[i] - r0[i]) * frac
        }

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
            out.put(o * 2, clamp16(softKnee(l)))
            out.put(o * 2 + 1, clamp16(softKnee(r)))
        }

        // the newest TAIL samples of the stream become the new history
        if (n >= TAIL) {
            System.arraycopy(arrN, n - TAIL, tail, 0, TAIL)
        } else {
            System.arraycopy(tail, 0, tail, n, TAIL - n)
            System.arraycopy(arrN, 0, tail, 0, n)
        }
    }

    /*
    Soft-knee limiter with a 0.7 knee reaching unity asymptotically: dense
    HRIR tails can push transient peaks past full scale (worst-case gain is
    the L1 norm, ~10x even after L2 scaling) and a bare clamp turns that into
    audible hard clipping — the "clips and distorts" report of 2026-09-10.
    */
    private fun softKnee(v: Float): Float {
        val a = if (v < 0.0f) -v else v
        if (a <= 0.7f) {
            return v
        }
        val over = (a - 0.7f) / 0.3f
        val shaped = 0.7f + 0.3f * over / (1.0f + over)
        return if (v < 0.0f) -shaped else shaped
    }

    private fun clamp16(v: Float): Short {
        return if (v < -32768.0f) Short.MIN_VALUE else if (v > 32767.0f) Short.MAX_VALUE else v.roundToInt().toShort()
    }
}

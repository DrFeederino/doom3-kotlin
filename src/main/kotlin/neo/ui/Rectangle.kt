package neo.ui

import neo.TempDump.SERiAL
import neo.idlib.containers.List.idList
import neo.idlib.math.DEG2RAD
import neo.idlib.math.idVec3
import neo.idlib.math.idVec4
import java.nio.ByteBuffer
import kotlin.math.cos
import kotlin.math.sin

object Rectangle {
    /*
     ================
     RotateVector
     ================
     */
    fun RotateVector(v: idVec3, origin: idVec3, a: Float, c: Float, s: Float) {
        var x = v[0]
        var y = v[1]
        if (a != 0.0f) {
            val x2 = (x - origin[0]) * c - (y - origin[1]) * s + origin[0]
            val y2 = (x - origin[0]) * s + (y - origin[1]) * c + origin[1]
            x = x2
            y = y2
        }
        v[0] = x
        v[1] = y
    }

    //
    // simple rectangle
    //
    //extern void RotateVector(idVec3 &v, idVec3 origin, float a, float c, float s);
    class idRectangle : SERiAL {
        var h // height;
                : Float
        var w // width
                : Float

        //
        var x // horiz position
                : Float
        var y // vert position
                : Float

        constructor() {
            h = 0.0f
            w = h
            y = w
            x = y
        }

        constructor(ix: Float, iy: Float, iw: Float, ih: Float) {
            x = ix
            y = iy
            w = iw
            h = ih
        }

        //copy constructor
        constructor(rectangle: idRectangle?) : this(rectangle!!.x, rectangle.y, rectangle.w, rectangle.h)

        fun Bottom(): Float {
            return y + h
        }

        fun Right(): Float {
            return x + w
        }

        fun Offset(x: Float, y: Float) {
            this.x += x
            this.y += y
        }

        fun Contains(xt: Float, yt: Float): Boolean {
            return if (w == 0.0f && h == 0.0f) {
                false
            } else xt >= x && xt <= Right() && yt >= y && yt <= Bottom()
        }

        fun Empty() {
            h = 0.0f
            w = h
            y = w
            x = y
        }

        fun ClipAgainst(r: idRectangle, sizeOnly: Boolean) {
            if (!sizeOnly) {
                if (x < r.x) {
                    x = r.x
                }
                if (y < r.y) {
                    y = r.y
                }
            }
            if (x + w > r.x + r.w) {
                w = r.x + r.w - x
            }
            if (y + h > r.y + r.h) {
                h = r.y + r.h - y
            }
        }

        fun Rotate(a: Float, out: idRectangle) {
            val p1 = idVec3()
            val p2 = idVec3()
            idVec3()
            val p4 = idVec3()
            idVec3()
            val c: Float
            val s: Float
            val center = idVec3((x + w) / 2.0f, (y + h) / 2.0f, 0.0f)
            p1.set(x, y, 0.0f)
            p2.set(Right(), y, 0.0f)
            p4.set(x, Bottom(), 0.0f)
            if (a != 0.0f) {
                s = sin(DEG2RAD(a))
                c = cos(DEG2RAD(a))
            } else {
                c = 0.0f
                s = c
            }
            RotateVector(p1, center, a, c, s)
            RotateVector(p2, center, a, c, s)
            RotateVector(p4, center, a, c, s)
            out.x = p1.x
            out.y = p1.y
            out.w = p2.minus(p1).Length()
            out.h = p4.minus(p1).Length()
        }

        fun oPluset(a: idRectangle): idRectangle {
            x += a.x
            y += a.y
            w += a.w
            h += a.h
            return this
        }

        fun oMinset(a: idRectangle): idRectangle {
            x -= a.x
            y -= a.y
            w -= a.w
            h -= a.h
            return this
        }

        fun oDivset(a: idRectangle): idRectangle {
            x /= a.x
            y /= a.y
            w /= a.w
            h /= a.h
            return this
        }

        fun oDivset(a: Float): idRectangle {
            val inva = 1.0f / a
            x *= inva
            y *= inva
            w *= inva
            h *= inva
            return this
        }

        fun oMulset(a: Float): idRectangle {
            x *= a
            y *= a
            w *= a
            h *= a
            return this
        }

        fun set(v: idVec4?): idRectangle {
            x = v!!.x
            y = v.y
            w = v.z
            h = v.w
            return this
        }

        fun set(r: idRectangle?): idRectangle {
            x = r!!.x
            y = r.y
            w = r.w
            h = r.h
            return this
        }

        //	int operator==(const idRectangle &a) const;
        override fun hashCode(): Int {
            var hash = 7
            hash = 19 * hash + java.lang.Float.floatToIntBits(x)
            hash = 19 * hash + java.lang.Float.floatToIntBits(y)
            hash = 19 * hash + java.lang.Float.floatToIntBits(w)
            hash = 19 * hash + java.lang.Float.floatToIntBits(h)
            return hash
        }

        override fun equals(obj: Any?): Boolean {
            if (obj == null) {
                return false
            }
            if (javaClass != obj.javaClass) {
                return false
            }
            val other = obj as idRectangle
            if (java.lang.Float.floatToIntBits(x) != java.lang.Float.floatToIntBits(other.x)) {
                return false
            }
            if (java.lang.Float.floatToIntBits(y) != java.lang.Float.floatToIntBits(other.y)) {
                return false
            }
            return if (java.lang.Float.floatToIntBits(w) != java.lang.Float.floatToIntBits(other.w)) {
                false
            } else java.lang.Float.floatToIntBits(h) == java.lang.Float.floatToIntBits(other.h)
        }

        operator fun get(index: Int): Float {
            return when (index) {
                1 -> y
                2 -> w
                3 -> h
                else -> x
            }
        }

        override fun toString(): String {
            val s: String
            val temp: CharArray

            // use an array so that multiple toString's won't collide
            s = String.format("%.2f %.2f %.2f %.2f", x, y, w, h)
            temp = s.toCharArray()
            System.arraycopy(temp, 0, str[index], 0, temp.size)
            index = index + 1 and 7
            return s
        }

        fun ToVec4(): idVec4 {
            return idVec4(x, y, w, h)
        }

        override fun AllocBuffer(): ByteBuffer {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        override fun Read(buffer: ByteBuffer) {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        override fun Write(): ByteBuffer {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        companion object {
            private val str = Array(8) { CharArray(48) }
            private var index = 0
        }
    }

    class idRegion  //
    //
    {
        protected val rects = idList<idRectangle>()
        fun Empty() {
            rects.Clear()
        }

        fun Contains(xt: Float, yt: Float): Boolean {
            val c = rects.Num()
            for (i in 0 until c) {
                if (rects[i].Contains(xt, yt)) {
                    return true
                }
            }
            return false
        }

        fun AddRect(x: Float, y: Float, w: Float, h: Float) {
            rects.Append(idRectangle(x, y, w, h))
        }

        fun GetRectCount(): Int {
            return rects.Num()
        }

        fun GetRect(index: Int): idRectangle? {
            return if (index >= 0 && index < rects.Num()) {
                rects[index]
            } else null
        }
    }
}

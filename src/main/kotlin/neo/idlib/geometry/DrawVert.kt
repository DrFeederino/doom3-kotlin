/*
===========================================================================

Doom 3 GPL Source Code
Copyright (C) 1999-2011 id Software LLC, a ZeniMax Media company.

This file is part of the Doom 3 GPL Source Code ("Doom 3 Source Code").

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

In addition, the Doom 3 Source Code is also subject to certain additional terms. You should have received a copy of these additional terms immediately following the terms and conditions of the GNU General Public License which accompanied the Doom 3 Source Code.  If not, please request a copy in writing from id Software at the address below.

If you have questions concerning this license or the applicable additional terms, you may contact in writing id Software LLC, c/o ZeniMax Media Inc., Suite 120, Rockville, Maryland 20850 USA.

===========================================================================
*/

package neo.idlib.geometry

import neo.idlib.math.idVec2
import neo.idlib.math.idVec3
import org.lwjgl.BufferUtils
import java.nio.ByteBuffer
import java.nio.ByteOrder


object DrawVert {

    fun toByteBuffer(verts: Array<idDrawVert>): ByteBuffer {
        val data = BufferUtils.createByteBuffer(idDrawVert.BYTES * verts.size)
        data.order(ByteOrder.LITTLE_ENDIAN)
        for (vert in verts) {
            if (vert != null) {
                vert.WriteTo(data)
            }
        }
        return data.flip()
    }

    /*
     ===============================================================================

     Draw Vertex.

     ===============================================================================
     */
    class idDrawVert {
        private val DBG_count = DBG_counter++
        var color: ByteArray = ByteArray(4)
        val normal: idVec3 = idVec3()
        val st: idVec2 = idVec2()
        var tangents: Array<idVec3>
        val xyz: idVec3 = idVec3()

        private var VBO_OFFSET = 0

        constructor() {
            tangents = idVec3.generateArray(2)
        }

        // Cast-from-VBO-offset constructor: vertexCache.Position() returns a 4-byte buffer
        // holding the integer VBO offset when the cache lives in a VBO. Mirrors the C++
        // pointer-cast pattern used by the renderer to derive xyzOffset()/stOffset()/etc.
        constructor(buffer: ByteBuffer) : this() {
            if (buffer.capacity() == Integer.BYTES) {
                VBO_OFFSET = buffer.getInt(0)
            }
        }

        constructor (dv: idDrawVert) {
            xyz.set(dv.xyz)
            st.set(dv.st)
            normal.set(dv.normal)
            tangents = arrayOf(idVec3(dv.tangents[0]), idVec3(dv.tangents[1]))
            System.arraycopy(dv.color, 0, color, 0, 4)
        }

        fun set(dv: idDrawVert) {
            xyz.set(dv.xyz)
            st.set(dv.st)
            normal.set(dv.normal)
            tangents[0].set(dv.tangents[0])
            tangents[1].set(dv.tangents[1])
            System.arraycopy(dv.color, 0, color, 0, 4)
        }

        operator fun get(index: Int): Float {
            when (index) {
                0, 1, 2 -> return xyz[index]
                3, 4 -> return st[index - 3]
                5, 6, 7 -> return normal[index - 5]
                8, 9, 10 -> return tangents[0][index - 8]
                11, 12, 13 -> return tangents[1][index - 11]
                14, 15, 16, 17 -> return color[index - 14].toFloat()
            }
            return -1.0f
        }

        fun Clear() {
            xyz.Zero()
            st.Zero()
            normal.Zero()
            tangents[0].Zero()
            tangents[1].Zero()
            color[3] = 0
            color[2] = color[3]
            color[1] = color[2]
            color[0] = color[1]
        }

        fun Lerp(a: idDrawVert, b: idDrawVert, f: Float) {
            xyz.set(a.xyz + (b.xyz - a.xyz) * f)
            st.set(a.st + (b.st - a.st) * f)
        }

        fun LerpAll(a: idDrawVert, b: idDrawVert, f: Float) {
            xyz.set(a.xyz + (b.xyz - a.xyz) * f)
            st.set(a.st + (b.st - a.st) * f)
            normal.set(a.normal + (b.normal - a.normal) * f)
            tangents[0].set(a.tangents[0] + (b.tangents[0] - a.tangents[0]) * f)
            tangents[1].set(a.tangents[1] + (b.tangents[1] - a.tangents[1]) * f)
            color[0] = (a.color[0] + f * (b.color[0] - a.color[0])).toInt().toByte()
            color[1] = (a.color[1] + f * (b.color[1] - a.color[1])).toInt().toByte()
            color[2] = (a.color[2] + f * (b.color[2] - a.color[2])).toInt().toByte()
            color[3] = (a.color[3] + f * (b.color[3] - a.color[3])).toInt().toByte()
        }

        fun Normalize() {
            normal.Normalize()
            tangents[1].Cross(normal, tangents[0])
            tangents[1].Normalize()
            tangents[0].Cross(tangents[1], normal)
            tangents[0].Normalize()
        }

        fun SetColor(color: Int) {
            this.color[0] = (color and 0xFF).toByte()
            this.color[1] = (color ushr 8 and 0xFF).toByte()
            this.color[2] = (color ushr 16 and 0xFF).toByte()
            this.color[3] = (color ushr 24 and 0xFF).toByte()
        }

        fun GetColor(): Int {
            return get_reinterpret_cast()
        }

        private fun get_reinterpret_cast(): Int { //            return color[0].toInt() and 0x000000FF or (color[1].toInt() and 0x0000FF00
            //                    ) or (color[2].toInt() and 0x00FF0000
            //                    ) or (color[3].toInt() and -0x1000000)
            return (color[0].toInt() and 0xFF) or ((color[1].toInt() and 0xFF) shl 8) or ((color[2].toInt() and 0xFF) shl 16) or ((color[3].toInt() and 0xFF) shl 24)
        }

        private fun set_reinterpret_cast(color: Long): ShortArray {
            return shortArrayOf(
                (color and 0x000000FF).toShort(),
                (color and 0x0000FF00).toShort(),
                (color and 0x00FF0000).toShort(),
                (color and -0x1000000).toShort()
            )
        }

        fun WriteTo(data: ByteBuffer) {
            data.putFloat(xyz[0])
            data.putFloat(xyz[1])
            data.putFloat(xyz[2])
            data.putFloat(st[0])
            data.putFloat(st[1])
            data.putFloat(normal[0])
            data.putFloat(normal[1])
            data.putFloat(normal[2])
            for (tan in tangents) {
                data.putFloat(tan[0])
                data.putFloat(tan[1])
                data.putFloat(tan[2])
            }
            for (colour in color) {
                data.put(colour)
            }
        }

        fun xyzOffset(): Int {
            return VBO_OFFSET
        }

        fun stOffset(): Int {
            return xyzOffset() + idVec3.BYTES //+xyz
        }

        fun normalOffset(): Int {
            return stOffset() + idVec2.BYTES //+xyz+st
        }

        fun tangentsOffset_0(): Int {
            return normalOffset() + idVec3.BYTES //+xyz+st+normal
        }

        fun tangentsOffset_1(): Int {
            return tangentsOffset_0() + idVec3.BYTES //+xyz+st+normal
        }

        fun colorOffset(): Int {
            return tangentsOffset_1() + idVec3.BYTES //+xyz+st+normal+tangents
        }

        companion object {
            val SIZE: Int =
                (idVec3.SIZE + idVec2.SIZE + idVec3.SIZE + 2 * idVec3.SIZE + 4 * java.lang.Byte.SIZE) //color

            val BYTES = SIZE / java.lang.Byte.SIZE
            private var DBG_counter = 0

            fun generateArray(length: Int): Array<idDrawVert> {
                return Array(length) { idDrawVert() }
            }
        }
    }
}

package neo.idlib.geometry

import neo.TempDump.SERiAL
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
    class idDrawVert : SERiAL {
        private val DBG_count = DBG_counter++
        var color: ByteArray = ByteArray(4)
        val normal: idVec3 = idVec3()
        val st: idVec2 = idVec2()
        var tangents: Array<idVec3>
        val xyz: idVec3 = idVec3()

        @Transient
        private var VBO_OFFSET = 0

        constructor() {
            tangents = idVec3.generateArray(2)
        }

        /**
         * cast constructor
         *
         * @param buffer
         */
        constructor(buffer: ByteBuffer) : this() {
            Read(buffer)
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

        private fun get_reinterpret_cast(): Int {
//            return color[0].toInt() and 0x000000FF or (color[1].toInt() and 0x0000FF00
//                    ) or (color[2].toInt() and 0x00FF0000
//                    ) or (color[3].toInt() and -0x1000000)
            return (color[0].toInt() and 0xFF) or
                    ((color[1].toInt() and 0xFF) shl 8) or
                    ((color[2].toInt() and 0xFF) shl 16) or
                    ((color[3].toInt() and 0xFF) shl 24)
        }

        private fun set_reinterpret_cast(color: Long): ShortArray {
            return shortArrayOf(
                (color and 0x000000FF).toShort(),
                (color and 0x0000FF00).toShort(),
                (color and 0x00FF0000).toShort(),
                (color and -0x1000000).toShort()
            )
        }

        override fun AllocBuffer(): ByteBuffer {
            return ByteBuffer.allocate(BYTES).order(ByteOrder.LITTLE_ENDIAN)
        }

        override fun Read(buffer: ByteBuffer) {
            if (buffer.capacity() == 0) {
                return
            }
            if (buffer.capacity() == Integer.SIZE / java.lang.Byte.SIZE) {
                VBO_OFFSET = buffer.getInt(0)
                return
            }
            xyz[0] = buffer.float
            xyz[1] = buffer.float
            xyz[2] = buffer.float
            st[0] = buffer.float
            st[1] = buffer.float
            normal[0] = buffer.float
            normal[1] = buffer.float
            normal[2] = buffer.float
            for (tan in tangents) {
                tan[0] = buffer.float
                tan[1] = buffer.float
                tan[2] = buffer.float
            }
            for (c in color.indices) {
                color[c] = buffer.get()
            }
        }

        override fun Write(): ByteBuffer {
            val data = ByteBuffer.allocate(BYTES)
            data.order(ByteOrder.LITTLE_ENDIAN) //very importante.
            WriteTo(data)
            return data
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
            @Transient
            val SIZE: Int = (idVec3.SIZE
                    + idVec2.SIZE
                    + idVec3.SIZE
                    + 2 * idVec3.SIZE
                    + 4 * java.lang.Byte.SIZE) //color

            @Transient
            val BYTES = SIZE / java.lang.Byte.SIZE
            private var DBG_counter = 0

            fun generateArray(length: Int): Array<idDrawVert> {
                return Array(length) { idDrawVert() }
            }
        }
    }
}

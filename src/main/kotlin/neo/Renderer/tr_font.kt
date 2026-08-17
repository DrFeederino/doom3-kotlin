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

Translated to Kotlin by Dr. Feederino with support of Claude Code.

===========================================================================
*/
package neo.Renderer

import java.nio.ByteBuffer
import java.nio.ByteOrder

object tr_font {
    val BUILD_FREETYPE: Boolean = false
    var fdFile: ByteArray? = null
    var fdOffset: Int = 0

    fun _FLOOR(x: Int): Int {
        return (x and -64)
    }

    fun _CEIL(x: Int): Int {
        return ((x + 63) and -64)
    }

    fun _TRUNC(x: Int): Int {
        return (x shr 6)
    }

    /*
     ============
     readInt
     ============
     */
    fun readInt(): Int {
        val i: Int =
            (fdFile!![fdOffset].toInt() and 0xFF) + ((fdFile!![fdOffset + 1].toInt() and 0xFF) shl 8) + ((fdFile!![fdOffset + 2].toInt() and 0xFF) shl 16) + ((fdFile!![fdOffset + 3].toInt() and 0xFF) shl 24)
        fdOffset += 4
        return i
    }

    /*
     ============
     readFloat
     ============
     */
    fun readFloat(): Float {
        val me: poor = poor()
        me.setFfred(fdFile, fdOffset)
        fdOffset += 4
        return me.ffred
    }

    /*
     ============
     R_InitFreeType
     ============
     */
    fun R_InitFreeType() {
    }

    /*
     ============
     R_DoneFreeType
     ============
     */
    fun R_DoneFreeType() {
    }

    private class poor {
        //mistreated me.
        private val fred: ByteBuffer = ByteBuffer.allocate(4)

        init {
            fred.order(ByteOrder.LITTLE_ENDIAN)
        }

        val ffred: Float
            get() {
                return fred.getFloat(0)
            }

        fun setFfred(fred: ByteArray?, offset: Int) {
            this.fred.put(fred, offset, 4).flip()
        }
    }
}

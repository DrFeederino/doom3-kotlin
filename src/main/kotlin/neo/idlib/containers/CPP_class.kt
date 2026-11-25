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

package neo.idlib.containers

/**
 * Sizes (in bits) of fundamental C++ types, used to compute on-disk struct
 * sizes for direct file/byte-buffer reads and writes that mirror the original
 * 32-bit C++ memory layout.
 */
object CPP_class {
    /** A 32-bit C++ pointer. */
    const val POINTER_SIZE = Integer.SIZE

    /** A C++ bool (8 bits). */
    const val BOOL_SIZE = java.lang.Byte.SIZE

    /** A C++ char (8 bits). */
    const val CHAR_SIZE = java.lang.Byte.SIZE

    /** A C++ enum is the size of an int. */
    const val ENUM_SIZE = Integer.SIZE

    /** A C++ long is 4 bytes on the platforms targeted by the original game. */
    const val LONG_SIZE = Integer.SIZE
}

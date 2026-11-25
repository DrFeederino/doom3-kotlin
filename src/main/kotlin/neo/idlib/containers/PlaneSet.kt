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

import neo.idlib.containers.List.idList
import neo.idlib.math.PLANETYPE_NEGX
import neo.idlib.math.PLANETYPE_TRUEAXIAL
import neo.idlib.math.idPlane
import kotlin.math.abs

class PlaneSet {
    /*
     ===============================================================================

     Plane Set

     ===============================================================================
     */
    class idPlaneSet : idList<idPlane>() {
        private val hash: idHashIndex = idHashIndex()
        override fun Clear() {
            super.Clear()
            hash.Free()
        }

        //
        fun FindPlane(plane: idPlane, normalEps: Float, distEps: Float): Int {
            var i: Int
            var border: Int
            val hashKey: Int
            assert(distEps <= 0.125f)
            hashKey = (abs(plane.Dist()) * 0.125f).toInt()
            border = -1
            while (border <= 1) {
                i = hash.First(hashKey + border)
                while (i >= 0) {
                    if (get(i).Compare(plane, normalEps, distEps)) {
                        return i
                    }
                    i = hash.Next(i)
                }
                border++
            }
            return if (plane.Type() >= PLANETYPE_NEGX && plane.Type() < PLANETYPE_TRUEAXIAL) {
                Append(plane.unaryMinus())
                hash.Add(hashKey, Num() - 1)
                Append(plane)
                hash.Add(hashKey, Num() - 1)
                Num() - 1
            } else {
                Append(plane)
                hash.Add(hashKey, Num() - 1)
                Append(plane.unaryMinus())
                hash.Add(hashKey, Num() - 1)
                Num() - 2
            }
        }

    }
}
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

package neo.idlib.math

class Random {
    /*
     ===============================================================================

     Random number generator

     ===============================================================================
     */
    class idRandom {
        private var seed: Int

        constructor() {
            seed = 0
        }

        constructor(seed: Int) {
            this.seed = seed
        }

        constructor(random: idRandom) {
            seed = random.seed
        }

        fun SetSeed(seed: Int) {
            this.seed = seed
        }

        fun GetSeed(): Int {
            return seed
        }

        fun RandomInt(): Int { // random integer in the range [0, MAX_RAND]
            seed = 69069 * seed + 1
            return seed and MAX_RAND
        }

        fun RandomInt(max: Int): Int { // random integer in the range [0, max[
            return if (max == 0) {
                0 // avoid divide by zero error
            } else RandomInt() % max
        }

        fun RandomFloat(): Float { // random number in the range [0.0f, 1.0f]
            return RandomInt() / (MAX_RAND + 1).toFloat()
        }

        fun CRandomFloat(): Float { // random number in the range [-1.0f, 1.0f]
            return 2.0f * (RandomFloat() - 0.5f)
        }

        companion object {
            const val MAX_RAND = 0x7fff
        }
    }

    /*
     ===============================================================================

     Random number generator

     ===============================================================================
     */
    internal class idRandom2 {
        private var seed: Long

        constructor() {
            seed = 0
        }

        constructor(seed: Long) {
            this.seed = seed
        }

        fun SetSeed(seed: Long) {
            this.seed = seed
        }

        fun GetSeed(): Long {
            return seed
        }

        fun RandomInt(): Int { // random integer in the range [0, MAX_RAND]
            seed = 1664525L * seed + 1013904223L
            return seed.toInt() and MAX_RAND
        }

        fun RandomInt(max: Int): Int { // random integer in the range [0, max]
            return if (max == 0) {
                0 // avoid divide by zero error
            } else (RandomInt() shr (16 - idMath.BitsForInteger(max))) % max
        }

        fun RandomFloat(): Float { // random number in the range [0.0f, 1.0f]
            seed = 1664525L * seed + 1013904223L
            val i = (IEEE_ONE or (seed and IEEE_MASK)).toInt()
            val floatBits = java.lang.Float.intBitsToFloat(i)
            return floatBits - 1.0f
        }

        fun CRandomFloat(): Float { // random number in the range [-1.0f, 1.0f]
            seed = 1664525L * seed + 1013904223L
            val i = (IEEE_ONE or (seed and IEEE_MASK)).toInt()
            val floatBits = java.lang.Float.intBitsToFloat(i)
            return 2.0f * floatBits - 3.0f
        }

        companion object {
            const val MAX_RAND = 0x7fff
            private const val IEEE_MASK: Long = 0x007fffff
            private const val IEEE_ONE: Long = 0x3f800000
        }
    }
}
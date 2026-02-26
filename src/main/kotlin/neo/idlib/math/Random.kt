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
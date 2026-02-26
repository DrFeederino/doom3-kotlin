package neo.idlib.containers

import neo.idlib.containers.List.idList
import neo.idlib.math.idMath
import neo.idlib.math.idVec
import kotlin.math.abs

class VectorSet {
    /*
     ===============================================================================

     Vector Set

     Creates a set of vectors without duplicates.

     ===============================================================================
     */
    class idVectorSet<T> : idList<T> {
        private var dimension: Int = 0
        private var boxHalfSize: FloatArray = FloatArray(dimension)
        private var boxHashSize = 0
        private var boxInvSize: FloatArray = FloatArray(dimension)
        private val hash: idHashIndex = idHashIndex()
        private var maxs: idVec<*>? = null
        private var mins: idVec<*>? = null

        constructor(dimension: Int) {
            this.dimension = dimension
            boxInvSize = FloatArray(dimension)
            boxHalfSize = FloatArray(dimension)
            hash.Clear(idMath.IPow(boxHashSize, dimension), 128)
            boxHashSize = 16
        }

        constructor(mins: idVec<*>, maxs: idVec<*>, boxHashSize: Int, initialSize: Int, dimension: Int) {
            this.dimension = dimension
            Init(mins, maxs, boxHashSize, initialSize)
        }

        fun Init(mins: idVec<*>, maxs: idVec<*>, boxHashSize: Int, initialSize: Int) {
            var i: Int
            var boxSize: Float
            super.AssureSize(initialSize)
            super.SetNum(0, false)
            hash.Clear(idMath.IPow(boxHashSize, dimension), initialSize)
            this.mins = mins
            this.maxs = maxs
            this.boxHashSize = boxHashSize
            i = 0
            while (i < dimension) {
                boxSize = (maxs[i] - mins[i]) / boxHashSize
                boxInvSize[i] = 1.0f / boxSize
                boxHalfSize[i] = boxSize * 0.5f
                i++
            }
        }

        fun ResizeIndex(newSize: Int) {
            super.Resize(newSize)
            hash.ResizeIndex(newSize)
        }

        override fun Clear() {
            super.Clear()
            hash.Clear()
        }

        fun FindVector(v: idVec<*>, epsilon: Float): Int {
            var i: Int
            var j: Int
            var k: Int
            var hashKey: Int
            val partialHashKey = IntArray(dimension)
            i = 0
            while (i < dimension) {
                assert(epsilon <= boxHalfSize[i])
                partialHashKey[i] = ((v[i] - mins!![i] - boxHalfSize[i]) * boxInvSize[i]).toInt()
                i++
            }
            i = 0
            while (i < 1 shl dimension) {
                hashKey = 0
                j = 0
                while (j < dimension) {
                    hashKey *= boxHashSize
                    hashKey += partialHashKey[j] + (i shr j and 1)
                    j++
                }
                j = hash.First(hashKey)
                while (j >= 0) {
                    val lv = get(j) as idVec<*>
                    k = 0
                    while (k < dimension) {
                        if (abs(lv[k] - v[k]) > epsilon) {
                            break
                        }
                        k++
                    }
                    if (k >= dimension) {
                        return j
                    }
                    j = hash.Next(j)
                }
                i++
            }
            hashKey = 0
            i = 0
            while (i < dimension) {
                hashKey *= boxHashSize
                hashKey += ((v[i] - mins!![i]) * boxInvSize[i]).toInt()
                i++
            }
            hash.Add(hashKey, super.Num())
            this.Append(v as T)
            return super.Num() - 1
        }
    }

    /*
     ===============================================================================

     Vector Subset

     Creates a subset without duplicates from an existing list with vectors.

     ===============================================================================
     */
    class idVectorSubset<type> {
        private var dimension: Int = 0
        private lateinit var boxHalfSize: FloatArray /*= new float[dimension]*/
        private var boxHashSize = 0
        private lateinit var boxInvSize: FloatArray /*= new float[dimension]*/
        private val hash: idHashIndex = idHashIndex()
        private var maxs: idVec<*>? = null
        private var mins: idVec<*>? = null

        private constructor()

        constructor(dimension: Int) {
            this.dimension = dimension
            boxInvSize = FloatArray(dimension)
            boxHalfSize = FloatArray(dimension)
            hash.Clear(idMath.IPow(boxHashSize, dimension), 128)
            boxHashSize = 16
        }

        constructor(mins: idVec<*>, maxs: idVec<*>, boxHashSize: Int, initialSize: Int, dimension: Int) {
            this.dimension = dimension
            Init(mins, maxs, boxHashSize, initialSize)
        }

        fun Init(mins: idVec<*>, maxs: idVec<*>, boxHashSize: Int, initialSize: Int) {
            var i: Int
            var boxSize: Float
            hash.Clear(idMath.IPow(boxHashSize, dimension), initialSize)
            this.mins = mins
            this.maxs = maxs
            this.boxHashSize = boxHashSize
            i = 0
            while (i < dimension) {
                boxSize = (maxs[i] - mins[i]) / boxHashSize.toFloat()
                boxInvSize[i] = 1.0f / boxSize
                boxHalfSize[i] = boxSize * 0.5f
                i++
            }
        }

        fun Clear() {
            hash.Clear()
        }

        // returns either vectorNum or an index to a previously found vector
        fun FindVector(vectorList: Array<idVec<*>>, vectorNum: Int, epsilon: Float): Int {
            var i: Int
            var j: Int
            var k: Int
            var hashKey: Int
            val partialHashKey = IntArray(dimension)
            val v = vectorList[vectorNum]
            i = 0
            while (i < dimension) {
                assert(epsilon <= boxHalfSize[i])
                partialHashKey[i] = ((v[i] - mins!![i] - boxHalfSize[i]) * boxInvSize[i]).toInt()
                i++
            }
            i = 0
            while (i < 1 shl dimension) {
                hashKey = 0
                j = 0
                while (j < dimension) {
                    hashKey *= boxHashSize
                    hashKey += partialHashKey[j] + (i shr j and 1)
                    j++
                }
                j = hash.First(hashKey)
                while (j >= 0) {
                    val lv = vectorList[j]
                    k = 0
                    while (k < dimension) {
                        if (abs(lv[k] - v[k]) > epsilon) {
                            break
                        }
                        k++
                    }
                    if (k >= dimension) {
                        return j
                    }
                    j = hash.Next(j)
                }
                i++
            }
            hashKey = 0
            i = 0
            while (i < dimension) {
                hashKey *= boxHashSize
                hashKey += ((v[i] - mins!![i]) * boxInvSize[i]).toInt()
                i++
            }
            hash.Add(hashKey, vectorNum)
            return vectorNum
        }
    }
}
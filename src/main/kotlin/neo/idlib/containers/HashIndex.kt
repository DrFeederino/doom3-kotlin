package neo.idlib.containers

import neo.idlib.Text.Str.idStr
import neo.idlib.math.Math_h.idMath
import neo.idlib.math.Vector.idVec3
import java.util.*

/**
 *
 */
object HashIndex {
    const val DEFAULT_HASH_GRANULARITY = 1024

    /*
     ===============================================================================

     Fast hash table for indexes and arrays.
     Does not allocate memory until the first key/index pair is added.

     ===============================================================================
     */
    const val DEFAULT_HASH_SIZE = 1024

    class idHashIndex {
        private val DBG_count: Int
        private var granularity = 0
        private var hash: IntArray?
        private var hashMask = 0
        private var hashSize = 0
        private var indexChain: IntArray?
        private var indexSize = 0
        private var lookupMask = 0

        constructor() {
            DBG_count = DBG_counter++
            Init(HashIndex.DEFAULT_HASH_SIZE, HashIndex.DEFAULT_HASH_SIZE)
        }

        constructor(initialHashSize: Int, initialIndexSize: Int) {
            DBG_count = DBG_counter++
            Init(initialHashSize, initialIndexSize)
        }

        /*size_t*/   fun Allocated(): Int {
            return hashSize + indexSize
        }

        /*size_t*/   fun Size(): Int {
            return Allocated()
        }

        fun oSet(other: idHashIndex?): idHashIndex? {
            granularity = other.granularity
            hashMask = other.hashMask
            lookupMask = other.lookupMask
            if (other.lookupMask == 0) {
                hashSize = other.hashSize
                indexSize = other.indexSize
                Free()
            } else {
                if (other.hashSize != hashSize || hash == INVALID_INDEX) {
                    if (hash != INVALID_INDEX) {
//				delete[] hash;
                    }
                    hashSize = other.hashSize
                    hash = IntArray(hashSize)
                }
                if (other.indexSize != indexSize || indexChain == INVALID_INDEX) {
                    if (indexChain != INVALID_INDEX) {
//				delete[] indexChain;
                    }
                    indexSize = other.indexSize
                    indexChain = IntArray(indexSize)
                }
                System.arraycopy(other.hash, 0, hash, 0, hashSize)
                System.arraycopy(other.indexChain, 0, indexChain, 0, indexSize)
            }
            return this
        }

        // add an index to the hash, assumes the index has not yet been added to the hash
        fun Add(key: Int, index: Int) {
            val h: Int
            assert(index >= 0)
            if (hash == INVALID_INDEX) {
                Allocate(hashSize, if (index >= indexSize) index + 1 else indexSize)
            } else if (index >= indexSize) {
                ResizeIndex(index + 1)
            }
            h = key and hashMask
            indexChain.get(index) = hash.get(h)
            hash.get(h) = index
        }

        // remove an index from the hash
        fun Remove(key: Int, index: Int) {
            val k = key and hashMask
            if (hash == INVALID_INDEX) {
                return
            }
            if (hash.get(k) == index) {
                hash.get(k) = indexChain.get(index)
            } else {
                var i = hash.get(k)
                while (i != -1) {
                    if (indexChain.get(i) == index) {
                        indexChain.get(i) = indexChain.get(index)
                        break
                    }
                    i = indexChain.get(i)
                }
            }
            indexChain.get(index) = -1
        }

        // get the first index from the hash, returns -1 if empty hash entry
        fun First(key: Int): Int {
            return if (null == hash) {
                -1
            } else hash.get(key and hashMask and lookupMask)
        }

        // get the next index from the hash, returns -1 if at the end of the hash chain
        fun Next(index: Int): Int {
            assert(index >= 0 && index < indexSize)
            return indexChain.get(index and lookupMask)
        }

        // insert an entry into the index and add it to the hash, increasing all indexes >= index
        fun InsertIndex(key: Int, index: Int) {
            var i: Int
            var max: Int
            if (hash != INVALID_INDEX) {
                max = index
                i = 0
                while (i < hashSize) {
                    if (hash.get(i) >= index) {
                        hash.get(i)++
                        if (hash.get(i) > max) {
                            max = hash.get(i)
                        }
                    }
                    i++
                }
                i = 0
                while (i < indexSize) {
                    if (indexChain.get(i) >= index) {
                        indexChain.get(i)++
                        if (indexChain.get(i) > max) {
                            max = indexChain.get(i)
                        }
                    }
                    i++
                }
                if (max >= indexSize) {
                    ResizeIndex(max + 1)
                }
                i = max
                while (i > index) {
                    indexChain.get(i) = indexChain.get(i - 1)
                    i--
                }
                indexChain.get(index) = -1
            }
            Add(key, index)
        }

        // remove an entry from the index and remove it from the hash, decreasing all indexes >= index
        fun RemoveIndex(key: Int, index: Int) {
            var i: Int
            var max: Int
            Remove(key, index)
            if (hash != INVALID_INDEX) {
                max = index
                i = 0
                while (i < hashSize) {
                    if (hash.get(i) >= index) {
                        if (hash.get(i) > max) {
                            max = hash.get(i)
                        }
                        hash.get(i)--
                    }
                    i++
                }
                i = 0
                while (i < indexSize) {
                    if (indexChain.get(i) >= index) {
                        if (indexChain.get(i) > max) {
                            max = indexChain.get(i)
                        }
                        indexChain.get(i)--
                    }
                    i++
                }
                i = index
                while (i < max) {
                    indexChain.get(i) = indexChain.get(i + 1)
                    i++
                }
                indexChain.get(max) = -1
            }
        }

        // clear the hash
        fun Clear() {
            // only clear the hash table because clearing the indexChain is not really needed
            if (hash != INVALID_INDEX) {
//		memset( hash, 0xff, hashSize * sizeof( hash[0] ) );
                Arrays.fill(hash, -1) //0xff);
            }
        }

        // clear and resize
        fun Clear(newHashSize: Int, newIndexSize: Int) {
            Free()
            hashSize = newHashSize
            indexSize = newIndexSize
        }

        // free allocated memory
        fun Free() {
//            if (hash != INVALID_INDEX) {
//                hash = null;//delete[] hash;
            hash = INVALID_INDEX
            //            }
//            if (indexChain != INVALID_INDEX) {
//                indexChain = null;//delete[] indexChain;
            indexChain = INVALID_INDEX
            //            }
            lookupMask = 0
            //            TempDump.printCallStack("----" + DBG_count);
        }

        // get size of hash table
        fun GetHashSize(): Int {
            return hashSize
        }

        // get size of the index
        fun GetIndexSize(): Int {
            return indexSize
        }

        // set granularity
        fun SetGranularity(newGranularity: Int) {
            assert(newGranularity > 0)
            granularity = newGranularity
        }

        // force resizing the index, current hash table stays intact
        fun ResizeIndex(newIndexSize: Int) {
            val oldIndexChain: IntArray?
            val mod: Int
            val newSize: Int
            if (newIndexSize <= indexSize) {
                return
            }
            mod = newIndexSize % granularity
            newSize = if (0 == mod) {
                newIndexSize
            } else {
                newIndexSize + granularity - mod
            }
            if (indexChain == INVALID_INDEX) {
                indexSize = newSize
                return
            }
            oldIndexChain = indexChain
            indexChain = IntArray(newSize)
            //	memcpy( indexChain, oldIndexChain, indexSize * sizeof(int) );
            System.arraycopy(oldIndexChain, 0, indexChain, 0, indexSize)
            //	memset( indexChain + indexSize, 0xff, (newSize - indexSize) * sizeof(int) );
            Arrays.fill(indexChain, indexSize, newSize, -1) //0xff);
            //	delete[] oldIndexChain;
            indexSize = newSize
        }

        // returns number in the range [0-100] representing the spread over the hash table
        fun GetSpread(): Int {
            var i: Int
            var index: Int
            var totalItems: Int
            val average: Int
            var error: Int
            var e: Int
            val numHashItems: IntArray
            if (hash == INVALID_INDEX) {
                return 100
            }
            totalItems = 0
            numHashItems = IntArray(hashSize)
            i = 0
            while (i < hashSize) {
                numHashItems[i] = 0
                index = hash.get(i)
                while (index >= 0) {
                    numHashItems[i]++
                    index = indexChain.get(index)
                }
                totalItems += numHashItems[i]
                i++
            }
            // if no items in hash
            if (totalItems <= 1) {
//		delete[] numHashItems;
                return 100
            }
            average = totalItems / hashSize
            error = 0
            i = 0
            while (i < hashSize) {
                e = Math.abs(numHashItems[i] - average)
                if (e > 1) {
                    error += e - 1
                }
                i++
            }
            //	delete[] numHashItems;
            return 100 - error * 100 / totalItems
        }

        // returns a key for a string
        @JvmOverloads
        fun GenerateKey(string: CharArray?, caseSensitive: Boolean = true): Int {
            return if (caseSensitive) {
                idStr.Companion.Hash(string) and hashMask
            } else {
                idStr.Companion.IHash(string) and hashMask
            }
        }

        @JvmOverloads
        fun GenerateKey(string: String?, caseSensitive: Boolean = true): Int {
            return GenerateKey(string.toCharArray(), caseSensitive)
        }

        // returns a key for a vector
        fun GenerateKey(v: idVec3?): Int {
            return v.oGet(0).toInt() + v.oGet(1).toInt() + v.oGet(2).toInt() and hashMask
        }

        // returns a key for two integers
        fun GenerateKey(n1: Int, n2: Int): Int {
            return n1 + n2 and hashMask
        }

        private fun Init(initialHashSize: Int, initialIndexSize: Int) {
            assert(idMath.IsPowerOfTwo(initialHashSize))
            hashSize = initialHashSize
            hash = INVALID_INDEX
            indexSize = initialIndexSize
            indexChain = INVALID_INDEX
            granularity = HashIndex.DEFAULT_HASH_GRANULARITY
            hashMask = hashSize - 1
            lookupMask = 0
        }

        private fun Allocate(newHashSize: Int, newIndexSize: Int) {
            assert(idMath.IsPowerOfTwo(newHashSize))
            Free()
            hashSize = newHashSize
            hash = IntArray(hashSize)
            //            memset(hash, 0xff, hashSize * sizeof(hash[0]));
            Arrays.fill(hash, -1) //0xff);
            indexSize = newIndexSize
            indexChain = IntArray(indexSize)
            //            memset(indexChain, 0xff, indexSize * sizeof(indexChain[0]));
            Arrays.fill(indexChain, -1) //0xff);
            hashMask = hashSize - 1
            lookupMask = -1
        }

        companion object {
            //
            private val INVALID_INDEX: IntArray? = intArrayOf(-1)

            //
            //
            private var DBG_counter = 0
        }
    }
}
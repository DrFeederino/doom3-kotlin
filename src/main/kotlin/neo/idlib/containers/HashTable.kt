package neo.idlib.containers

import neo.idlib.Text.Str.idStr
import neo.idlib.Text.Str.idStr.Companion.Hash
import neo.idlib.math.idMath.IsPowerOfTwo
import kotlin.math.abs

class HashTable {
    /*
     ===============================================================================

     General hash table. Slower than idHashIndex but it can also be used for
     linked lists and other data structures than just indexes or arrays.

     ===============================================================================
     */
    class idHashTable<Type> {
        private val heads: Array<hashnode_s<*>?>

        //
        private val tablesize: Int
        private val tablesizemask: Int
        private var numentries: Int

        //
        //
        constructor() {
            val newtablesize = 256
            tablesize = newtablesize
            assert(tablesize > 0)
            heads = arrayOfNulls(tablesize)
            numentries = 0
            tablesizemask = tablesize - 1
        }

        constructor(newtablesize: Int) {
            assert(IsPowerOfTwo(newtablesize))
            tablesize = newtablesize
            assert(tablesize > 0)
            heads = arrayOfNulls(tablesize)
            numentries = 0
            tablesizemask = tablesize - 1
        }

        constructor(map: idHashTable<Type>) {
            var i: Int
            var node: hashnode_s<*>?
            var prev: Int = 0

            assert(map.tablesize > 0)

            tablesize = map.tablesize
            heads = arrayOfNulls(tablesize)
            numentries = map.numentries
            tablesizemask = map.tablesizemask

            for (i in 0 until tablesize) {
                if (null != map.heads[i]) {
                    heads[i] = null
                    continue
                }

                node = map.heads[i + prev]
                while (node != null) {
                    map.heads[i + prev] = hashnode_s(node.key, node.value, null)
                    prev++
                    node = node.next
                }

            }
        }

        //public					~idHashTable( void );
        //
        //					// returns total size of allocated memory
        //public	size_t			Allocated( void ) const;
        //					// returns total size of allocated memory including size of hash table type
        //public	size_t			Size( void ) const;
        //
        fun Set(key: String?, value: Type?) {
            var node: hashnode_s<*>?
            var nextPtr: hashnode_s<*>?
            val hash: Int
            var s: Int
            hash = GetHash(key)
            nextPtr = heads[hash]
            node = nextPtr
            while (node != null) {
                //TODO:what moves us?
                s = node.key.Cmp(key!!)
                if (s == 0) {
                    node.value = value as Nothing?
                    return
                }
                if (s > 0) {
                    break
                }
                nextPtr = node.next
                node = nextPtr
            }
            numentries++
            nextPtr = hashnode_s(key, value, heads[hash])
            nextPtr.next = node
        }


        fun Get(key: String?, value: Array<Type?>? = null): Boolean {
            var node: hashnode_s<*>?
            val hash: Int
            var s: Int
            hash = GetHash(key)
            node = heads[hash]
            while (node != null) {
                s = node.key.Cmp(key!!)
                if (s == 0) {
                    if (value != null) {
                        value[0] = node.value as Type
                    }
                    return true
                }
                if (s > 0) {
                    break
                }
                node = node.next
            }
            if (value != null) {
                value[0] = null
            }
            return false
        }

        fun Remove(key: String?): Boolean {
            val head: hashnode_s<*>?
            var node: hashnode_s<*>?
            var prev: hashnode_s<*>?
            val hash: Int
            hash = GetHash(key)
            head = heads[hash]
            if (head != null) {
                prev = null
                node = head
                while (node != null) {
                    //TODO:fuck me if any of this shit works.
                    if (node.key.Cmp(key!!) == 0) {
                        if (prev != null) {
                            prev.next = node.next
                        } else {
                            heads[hash] = node.next //TODO:double check these pointers.
                        }

//				delete node;
                        numentries--
                        return true
                    }
                    prev = node
                    node = node.next
                }
            }
            return false
        }

        fun Clear() {
            var i: Int
            var node: hashnode_s<*>?
            var next: hashnode_s<*>?
            i = 0
            while (i < tablesize) {
                next = heads[i]
                while (next != null) {
                    node = next
                    next = next.next
                    //			delete node;
                }
                heads[i] = null
                i++
            }
            numentries = 0
        }

        fun DeleteContents() {
            var i: Int
            var node: hashnode_s<*>?
            var next: hashnode_s<*>?
            i = 0
            while (i < tablesize) {
                next = heads[i]
                while (next != null) {
                    node = next
                    next = next.next
                    //			delete node->value;
//			delete node;
                }
                heads[i] = null
                i++
            }
            numentries = 0
        }

        // the entire contents can be itterated over, but note that the
        // exact index for a given element may change when new elements are added
        fun Num(): Int {
            return numentries
        }

        /*
         ================
         idHashTable<Type>::GetIndex

         the entire contents can be itterated over, but note that the
         exact index for a given element may change when new elements are added
         ================
         */
        fun GetIndex(index: Int): Type? {
            var node: hashnode_s<*>?
            var count: Int
            var i: Int
            if (index < 0 || index > numentries) {
                assert(false)
                return null
            }
            count = 0
            i = 0
            while (i < tablesize) {
                node = heads[i]
                while (node != null) {
                    if (count == index) {
                        return node.value as Type
                    }
                    count++
                    node = node.next
                }
                i++
            }
            return null
        }

        fun GetSpread(): Int {
            var i: Int
            val average: Int
            var error: Int
            var e: Int
            var node: hashnode_s<*>?

            // if no items in hash
            if (0 == numentries) {
                return 100
            }
            average = numentries / tablesize
            error = 0
            i = 0
            while (i < tablesize) {
                var numItems = 0
                node = heads[i]
                while (node != null) {
                    numItems++
                    node = node.next
                }
                e = abs((numItems - average).toFloat()).toInt()
                if (e > 1) {
                    error += e - 1
                }
                i++
            }
            return 100 - error * 100 / numentries
        }

        fun GetHash(key: String?): Int {
            return Hash(key!!) and tablesizemask
        }

        private inner class hashnode_s<Type> {
            var key: idStr
            var next: hashnode_s<*>? = null
            var value: Type? = null

            //
            //
            constructor() {
                key = idStr()
            }

            constructor(k: idStr?, v: Type, n: hashnode_s<*>?) {
                key = idStr(k!!)
                value = v
                next = n
            }

            constructor(k: String?, v: Type, n: hashnode_s<*>?) : this(idStr(k!!), v, n)
        }
    }
}

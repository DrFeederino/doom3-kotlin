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

import neo.idlib.Text.Str.idStr
import neo.idlib.containers.List.idList

class StrPool {
    /*
     ===============================================================================

     idStrPool

     ===============================================================================
     */
    class idPoolStr     //
    //
        : idStr() {
        //	friend class idStrPool;
        //
        var numUsers = 0
        lateinit var pool: idStrPool

        // returns total size of allocated memory including size of string pool type
        override fun Size(): Int {
            return  /*sizeof( *this ) + */Allocated()
        }

        // returns a pointer to the pool this string was allocated from
        fun GetPool(): idStrPool {
            return pool
        }
    }

    class idStrPool {
        private var caseSensitive = true
        private val pool: idList<idPoolStr>
        private val poolHash: idHashIndex
        fun SetCaseSensitive(caseSensitive: Boolean) {
            this.caseSensitive = caseSensitive
        }

        fun Num(): Int {
            return pool.Num()
        }

        fun Allocated(): Int {
            var i: Int
            var size: Int
            size = pool.Allocated() + poolHash.Allocated()
            i = 0
            while (i < pool.Num()) {
                size += pool[i].Allocated()
                i++
            }
            return size
        }

        fun Size(): Int {
            var i: Int
            var size: Int
            size = pool.Size() + poolHash.Size()
            i = 0
            while (i < pool.Num()) {
                size += pool[i].Size()
                i++
            }
            return size
        }

        operator fun get(index: Int): idPoolStr {
            return pool[index]
        }

        fun AllocString(string: String): idPoolStr {
            var i: Int
            val hash: Int
            val poolStr: idPoolStr
            hash = poolHash.GenerateKey(string, caseSensitive)
            if (caseSensitive) {
                i = poolHash.First(hash)
                while (i != -1) {
                    if (pool[i].Cmp(string) == 0) {
                        pool[i].numUsers++
                        return pool[i]
                    }
                    i = poolHash.Next(i)
                }
            } else {
                i = poolHash.First(hash)
                while (i != -1) {
                    if (pool[i].Icmp(string) == 0) {
                        pool[i].numUsers++ //                        System.out.printf("AllocString, i = %d\n", i);
                        return pool[i]
                    }
                    i = poolHash.Next(i)
                }
            }
            poolStr = idPoolStr()
            poolStr.set(string) //TODO:*static_cast<idStr *>(poolStr) = string;
            poolStr.pool = this
            poolStr.numUsers = 1
            poolHash.Add(hash, pool.Append(poolStr))
            return poolStr
        }

        fun FreeString(poolStr: idPoolStr) {
            var i: Int
            val hash: Int
            assert(poolStr.numUsers >= 1)
            assert(poolStr.pool === this)
            poolStr.numUsers--
            if (poolStr.numUsers <= 0) {
                hash = poolHash.GenerateKey(poolStr.toString(), caseSensitive)
                if (caseSensitive) {
                    i = poolHash.First(hash)
                    while (i != -1) {
                        if (pool[i].Cmp(poolStr.toString()) == 0) {
                            break
                        }
                        i = poolHash.Next(i)
                    }
                } else {
                    i = poolHash.First(hash)
                    while (i != -1) {
                        if (pool[i].Icmp(poolStr.toString()) == 0) {
                            break
                        }
                        i = poolHash.Next(i)
                    }
                }
                assert(i != -1)
                assert(pool[i] === poolStr) //		delete pool[i];
                pool.RemoveIndex(i)
                poolHash.RemoveIndex(hash, i)
            }
        }

        fun CopyString(poolStr: idPoolStr): idPoolStr {
            assert(poolStr.numUsers >= 1)
            return if (poolStr.pool === this) { // the string is from this pool so just increase the user count
                poolStr.numUsers++
                poolStr
            } else { // the string is from another pool so it needs to be re-allocated from this pool.
                AllocString(poolStr.toString())
            }
        }

        fun Clear() {
            var i: Int
            i = 0
            while (i < pool.Num()) {
                pool[i].numUsers = 0
                i++
            }
            pool.DeleteContents(true)
            poolHash.Free()
        }

        init {
            pool = idList()
            poolHash = idHashIndex()
        }
    }
}
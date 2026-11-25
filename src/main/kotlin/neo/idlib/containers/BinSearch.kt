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

/*
 ===============================================================================

 Binary Search templates

 The array elements have to be ordered in increasing order.

 ===============================================================================
 *//*
 ====================
 idBinSearch_GreaterEqual

 Finds the last array element which is smaller than the given value.
 ====================
 */
fun <T> idBinSearch_Less(array: Array<T>, arraySize: Int, value: T): Int {
    var len = arraySize
    var mid = len
    var offset = 0
    while (mid > 0) {
        mid = len shr 1
        if (LT(array[offset + mid],  /*<*/value)) {
            offset += mid
        }
        len -= mid
    }
    return offset
}

/*
 ====================
 idBinSearch_GreaterEqual

 Finds the last array element which is smaller than or equal to the given value.
 ====================
 */

fun <T> idBinSearch_LessEqual(array: Array<T>, arraySize: Int, value: T): Int {
    var len = arraySize
    var mid = len
    var offset = 0
    while (mid > 0) {
        mid = len shr 1
        if (LTE(array[offset + mid],  /*<=*/value)) {
            offset += mid
        }
        len -= mid
    }
    return offset
}

fun idBinSearch_LessEqual(array: FloatArray, arraySize: Int, value: Float): Int {
    var len = arraySize
    var mid = len
    var offset = 0
    while (mid > 0) {
        mid = len shr 1
        if (array[offset + mid] <= value) {
            offset += mid
        }
        len -= mid
    }
    return offset
}

/*
 ====================
 idBinSearch_Greater

 Finds the first array element which is greater than the given value.
 ====================
 */
fun <T> idBinSearch_Greater(array: Array<T>, arraySize: Int, value: T): Int {
    var len = arraySize
    var mid = len
    var offset = 0
    var res = 0
    while (mid > 0) {
        mid = len shr 1
        if (GT(array[offset + mid],  /*>*/value)) {
            res = 0
        } else {
            offset += mid
            res = 1
        }
        len -= mid
    }
    return offset + res
}

/*
 ====================
 idBinSearch_GreaterEqual

 Finds the first array element which is greater than or equal to the given value.
 ====================
 */
fun <T> idBinSearch_GreaterEqual(array: Array<T>, arraySize: Int, value: T): Int {
    var len = arraySize
    var mid = len
    var offset = 0
    var res = 0
    while (mid > 0) {
        mid = len shr 1
        if (GTE(array[offset + mid],  /*>=*/value)) {
            res = 0
        } else {
            offset += mid
            res = 1
        }
        len -= mid
    }
    return offset + res
}

fun <T> GT(object1: T, object2: T): Boolean {
    return (object1 as Number).toFloat() > (object2 as Number).toFloat()
}

//Greater Than or Equal
fun <T> GTE(object1: T, object2: T): Boolean {
    return (object1 as Number).toFloat() >= (object2 as Number).toFloat()
}

//Less Than
fun <T> LT(object1: T, object2: T): Boolean {
    return (object1 as Number).toFloat() < (object2 as Number).toFloat()
}

//Less Than or Equal
fun <T> LTE(object1: T, object2: T): Boolean {
    return (object1 as Number).toFloat() <= (object2 as Number).toFloat()
}

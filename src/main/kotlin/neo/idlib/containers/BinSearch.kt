package neo.idlib.containers

/*
 ===============================================================================

 Binary Search templates

 The array elements have to be ordered in increasing order.

 ===============================================================================
 */
/*
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

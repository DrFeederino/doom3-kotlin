package neo.idlib.containers

import neo.idlib.Text.Str.idStr

/*
===============================================================================

idStrList re-implementation with ArrayLists

===============================================================================
*/
open class idStrList : Comparator<idStr> {
    private var stringsList: MutableList<idStr>

    constructor() {
        stringsList = ArrayList()
    }

    constructor(size: Int) {
        stringsList = ArrayList(size)
    }

    fun getStringsList(): MutableList<idStr> {
        return stringsList
    }

    /*
     ================
     idStrList::Sort

     Sorts the list of strings alphabetically. Creates a list of pointers to the actual strings and sorts the
     pointer list. Then copies the strings into another list using the ordered list of pointers.
     ================
     */
    fun sort() {
        if (stringsList.isEmpty()) {
            return
        }
        stringsList.sortWith(this)
    }

    /*
     ================
     idStrList::SortSubSection

     Sorts a subsection of the list of strings alphabetically.
     ================
     */
    fun sortSubList(startIndex: Int, endIndex: Int) {
        var startIndex = startIndex
        var endIndex = endIndex
        if (stringsList.isEmpty() || startIndex >= endIndex) {
            return
        }
        if (startIndex < 0) {
            startIndex = 0
        }
        if (endIndex > stringsList.size) {
            endIndex = stringsList.size - 1
        }
        stringsList.subList(startIndex, endIndex).sortWith(this)
    }

    fun sizeStrings(): Int {
        return stringsList.stream().mapToInt { obj: idStr -> obj.Size() }.sum()
    }

    fun addUnique(obj: String): Int {
        val newIdStr = idStr(obj)
        val indexOfObj = stringsList.lastIndexOf(newIdStr)
        return if (indexOfObj == -1) {
            add(newIdStr) // assuming it's added to the end
        } else indexOfObj // already in the array
    }

    fun addUnique(obj: idStr): Int {
        val indexOfObj = stringsList.lastIndexOf(obj)
        return if (indexOfObj == -1) {
            add(obj) // assuming it's added to the end
        } else indexOfObj // already in the array
    }

    fun add(obj: String): Int {
        val newIdStr = idStr(obj)
        stringsList.add(newIdStr)
        return stringsList.size - 1
    }

    fun add(obj: idStr): Int { // FIX: C++ idList<idStr>::Append copies by value. Must create a new idStr
        // to avoid all elements sharing the same reference (e.g. ListWindow.UpdateList
        // reuses one idStr in a loop — all list items ended up pointing to the same object).
        stringsList.add(idStr(obj))
        return stringsList.size - 1
    }

    fun resize(newSize: Int) { // free up the list if no data is being reserved
        if (newSize <= 0) {
            clear()
            return
        }
        if (newSize == stringsList.size) { // not changing the size, so just exit
            return
        }
        if (newSize < stringsList.size) { // shrink
            stringsList = stringsList.subList(0, newSize).toMutableList()
        } else { // grow — fill new slots with empty idStr (C++ default-constructs elements)
            for (i in stringsList.size until newSize) {
                stringsList.add(idStr())
            }
        }
    }

    open fun clear() {
        this.stringsList.clear()
    }

    fun insert(obj: idStr): Int {            // insert the element at the given index
        return insert(obj, 0)
    }

    fun insert(obj: idStr, i: Int): Int { // FIX: copy by value, not reference (C++ value semantics)
        stringsList.add(i, idStr(obj))
        return i
    }

    /*
     ================
     idListSortCompare<idStrPtr>

     Compares two pointers to strings. Used to sort a list of string pointers alphabetically in idList<idStr>::Sort.
     ================
     */
    override fun compare(a: idStr, b: idStr): Int {
        return a.Icmp(b)
    }

    fun Find(testVal: idStr): Int? {
        val result = stringsList.indexOf(testVal)
        return if (result == -1) null else result
    }

    operator fun get(i: Int): idStr {
        var i = i
        if (i >= stringsList.size) {
            i = if (stringsList.isEmpty()) 0 else stringsList.size - 1
        }
        return stringsList[i]
    }

    fun set(associatedModels: idStrList) {
        stringsList = ArrayList(associatedModels.getStringsList())
    }

    fun SetGranularity(i: Int) { // ah yes granularity my favourite friend
        return
    }

    fun size(): Int {
        return stringsList.size
    }

    fun addEmptyStr(): idStr {
        val idStr = idStr()
        stringsList.add(idStr)
        return idStr
    }

    fun setSize(newNum: Int, resize: Boolean) {
        if (resize || newNum > stringsList.size) {
            resize(newNum)
        }
    }

    operator fun set(i: Int, value: String) {
        var i = i
        if (i >= stringsList.size) {
            i = if (stringsList.isEmpty()) 0 else stringsList.size - 1
        }

        if (stringsList.getOrNull(i) == null) {
            stringsList.add(i, idStr(value))

        } else {
            stringsList[i] = idStr(value)
        }
    }

    operator fun set(i: Int, obj: idStr) {
        var i = i
        if (i >= stringsList.size) {
            i = if (stringsList.isEmpty()) 0 else stringsList.size - 1
        } // FIX: copy by value, not reference (C++ value semantics)
        stringsList[i] = idStr(obj)
    }

    fun setSize(num: Int) {
        setSize(num, true)
    }

    fun ensureSize(num_files: Int, empty: idStr) {
        val sizeDiff = num_files - stringsList.size // e.g. num_files > list_size
        if (sizeDiff <= 0) {
            return  // it's ok
        }
        for (i in 0 until sizeDiff) {
            stringsList.add(empty)
        }
    }

    fun removeAtIndex(i: Int) {
        var i = i
        if (i >= stringsList.size) {
            i = if (stringsList.isEmpty()) 0 else stringsList.size - 1
        }
        stringsList.removeAt(i)
    }

    fun remove(idStr: idStr) {
        stringsList.remove(idStr)
    }

    companion object {
        /*
     ================
     idStrListSortPaths

     Sorts the list of path strings alphabetically and makes sure folders come first.
     (a, b) -> a.IcmpPath(b.toString()) is idStrList path sorting.
     ================
     */
        fun idStrListSortPaths(strings: idStrList) {
            if (strings.stringsList.isEmpty()) {
                return
            }
            strings.getStringsList().sortWith { a: idStr, b: idStr -> a.IcmpPath(b.toString()) }
        }
    }
}
package neo.idlib.containers

import neo.TempDump
import neo.TempDump.reflects
import neo.framework.CVarSystem
import neo.framework.CVarSystem.idInternalCVar
import neo.framework.CmdSystem
import neo.framework.CmdSystem.commandDef_s
import neo.idlib.Text.Str.idStr
import neo.idlib.containers.StrPool.idPoolStr
import neo.idlib.math.idVec3
import java.util.*
import java.util.logging.Level
import java.util.logging.Logger

object List {

    fun <T> idSwap(a1: Array<T>, a2: Array<T>, p1: Int, p2: Int) {
        val c = a1[p1]
        a1[p1] = a2[p2]
        a2[p2] = c
    }

    //    @Deprecated
    //    public static <T> void idSwap(T a, T b) {
    //        T c = a;
    //        a = b;
    //        b = c;
    //    }
    //
    fun idSwap(array: IntArray, p1: Int, p2: Int) {
        idSwap(array, array, p1, p2)
    }

    fun idSwap(a1: IntArray, a2: IntArray, p1: Int, p2: Int) {
        val c = a1[p1]
        a1[p1] = a2[p2]
        a2[p2] = c
    }

    fun idSwap(a1: Array<IntArray>, p11: Int, p12: Int, a2: Array<IntArray>, p21: Int, p22: Int) {
        val c = a1[p11][p12]
        a1[p11][p12] = a2[p21][p22]
        a2[p21][p22] = c
    }

    fun idSwap(a: idVec3, b: idVec3) {
        val c = idVec3(a)
        a.set(b)
        b.set(c)
    }


    fun idSwap(a1: FloatArray, a2: FloatArray, p1: Int, p2: Int) {
        val c = a1[p1]
        a1[p1] = a2[p2]
        a2[p2] = c
    }


    fun idSwap(a: FloatArray, b: FloatArray) {
        val length = a.size
        val c = FloatArray(length)
        System.arraycopy(a, 0, c, 0, length)
        System.arraycopy(b, 0, a, 0, length)
        System.arraycopy(c, 0, b, 0, length)
    }


    fun idSwap(a: Array<Float>, b: Array<Float>) {
        val length = a.size
        val c = FloatArray(length)
        System.arraycopy(a, 0, c, 0, length)
        System.arraycopy(b, 0, a, 0, length)
        System.arraycopy(c, 0, b, 0, length)
    }

    interface cmp_t<T> : Comparator<T>

    /*
     ===============================================================================

     List template
     Does not allocate memory until the first item is added.

     ===============================================================================
     */
    open class idList<T> {
        private val DBG_count = DBG_counter++
        protected var granularity = 16
        protected var num = 0
        private var list: Array<T>? = null
        private var size = 0
        private var type: Class<T>? = null

        //
        //public	typedef int		cmp_t( const T *, const T * );
        //public	typedef T	new_t( );
        //
        constructor() {
            //            this(16);//disabled to prevent inherited constructors from calling the overridden clear function.
        }

        constructor(type: Class<T>) : this() {
            this.type = type
        }

        constructor(newgranularity: Int) {
            assert(newgranularity > 0)
            list = null
            granularity = newgranularity
            Clear()
        }

        constructor(newgranularity: Int, type: Class<T>) : this(newgranularity) {
            this.type = type
        }

        constructor(other: idList<T>) {
            list = null
            this.set(other)
        }

        //public					~idList<T>( );
        //
        /*
         ================
         idList<T>::Clear

         Frees up the memory allocated by the list.  Assumes that T automatically handles freeing up memory.
         ================
         */
        open fun Clear() {                                        // clear the list
//            if (list) {
//                delete[] list;
//            }
            list = null
            num = 0
            size = 0
        }

        /*
         ================
         idList<T>::Num

         Returns the number of elements currently contained in the list.
         Note that this is NOT an indication of the memory allocated.
         ================
         */
        open fun Num(): Int {                                    // returns number of elements in list
            return num
        }

        /*
         ================
         idList<T>::NumAllocated

         Returns the number of elements currently allocated for.
         ================
         */
        fun NumAllocated(): Int {                            // returns number of elements allocated for
            return size
        }

        /*
         ================
         idList<T>::SetGranularity

         Sets the base size of the array and resizes the array to match.
         ================
         */
        fun SetGranularity(newgranularity: Int) {            // set new granularity
            var newsize: Int
            assert(newgranularity > 0)
            granularity = newgranularity
            if (list != null) {
                // resize it to the closest level of granularity
                newsize = num + granularity - 1
                newsize -= newsize % granularity
                if (newsize != size) {
                    Resize(newsize)
                }
            }
        }

        /*
         ================
         idList<T>::GetGranularity

         Get the current granularity.
         ================
         */
        fun GetGranularity(): Int {                        // get the current granularity
            return granularity
        }

        //
        /*
         ================
         idList<T>::Allocated

         return total memory allocated for the list in bytes, but doesn't take into account additional memory allocated by T
         ================
         */
        fun Allocated(): Int {                        // returns total size of allocated memory
            return size
        }

        /*size_t*/   fun Size(): Int {                        // returns total size of allocated memory including size of list T
            return Allocated()
        }

        /*size_t*/   fun MemoryUsed(): Int {                    // returns size of the used elements in the list
            return num /* sizeof( *list )*/
        }

        /*
         ================
         idList<T>::operator=

         Copies the contents and size attributes of another list.
         ================
         */
        fun set(other: idList<T>): idList<T> {
            var i: Int
            Clear()
            num = other.num
            size = other.size
            granularity = other.granularity
            type = other.type
            if (size != 0) {
                list = arrayOfNulls<Any>(size) as Array<T>
                i = 0
                while (i < num) {
                    list!![i] = other.list!![i]
                    i++
                }
            }
            return this
        }

        /*
         ================
         idList<T>::operator[] const

         Access operator.  Index must be within range or an assert will be issued in debug builds.
         Release builds do no range checking.
         ================
         */
        operator fun get(index: Int): T {
            assert(index >= 0)
            assert(index < num)
            return list!![index]
        }

        //public	T &			operator[]( int index );
        //
        //        public T set(int index, T value) {
        //            assert (index >= 0);
        //            assert (index < num);
        //
        //            return list[index] = value;
        //        }
        operator fun set(index: Int, value: T): T {
            assert(index >= 0)
            assert(index < num)
            list!![index] = value
            return list!![index]
        }

        //
        /*
         ================
         idList<T>::Condense

         Resizes the array to exactly the number of elements it contains or frees up memory if empty.
         ================
         */
        fun Condense() {                                    // resizes list to exactly the number of elements it contains
            if (list != null) {
                if (num != 0) {
                    Resize(num)
                } else {
                    Clear()
                }
            }
        }

        /*
         ================
         idList<T>::Resize

         Allocates memory for the amount of elements requested while keeping the contents intact.
         Contents are copied using their = operator so that data is correnctly instantiated.
         ================
         */
        fun Resize(newsize: Int) {                                // resizes list to the given number of elements
            val temp: Array<T>?
            var i: Int
            assert(newsize >= 0)

            // free up the list if no data is being reserved
            if (newsize <= 0) {
                Clear()
                return
            }
            if (newsize == size) {
                // not changing the size, so just exit
                return
            }
            temp = list
            size = newsize
            if (size < num) {
                num = size
            }

            // copy the old list into our new one
            list = arrayOfNulls<Any>(size) as Array<T>
            i = 0
            while (i < num) {
                list!![i] = temp!![i]
                i++
            }

            // delete the old list if it exists
//	if ( temp ) {
//		delete[] temp;
//	}
        }

        /*
         ================
         idList<T>::Resize

         Allocates memory for the amount of elements requested while keeping the contents intact.
         Contents are copied using their = operator so that data is correnctly instantiated.
         ================
         */
        fun Resize(newsize: Int, newgranularity: Int) {            // resizes list and sets new granularity
            val temp: Array<T>?
            var i: Int
            assert(newsize >= 0)
            assert(newgranularity > 0)
            granularity = newgranularity

            // free up the list if no data is being reserved
            if (newsize <= 0) {
                Clear()
                return
            }
            temp = list
            size = newsize
            if (size < num) {
                num = size
            }

            // copy the old list into our new one
            list = arrayOfNulls<Any>(size) as Array<T>
            i = 0
            while (i < num) {
                list!![i] = temp!![i]
                i++
            }

            // delete the old list if it exists
//	if ( temp ) {
//		delete[] temp;
//	}
        }

        /*
         ================
         idList<T>::SetNum

         Resize to the exact size specified irregardless of granularity
         ================
         */

        fun SetNum(
            newnum: Int,
            resize: Boolean = true
        ) {            // set number of elements in list and resize to exactly this number if necessary
            assert(newnum >= 0)
            if (resize || newnum > size) {
                Resize(newnum)
            }
            num = newnum
        }

        /*
         ================
         idList<T>::AssureSize

         Makes sure the list has at least the given number of elements.
         ================
         */
        fun AssureSize(newSize: Int) {                            // assure list has given number of elements, but leave them uninitialized
            var newSize = newSize
            val newNum = newSize
            if (newSize > size) {
                if (granularity == 0) {    // this is a hack to fix our memset classes
                    granularity = 16
                }
                newSize += granularity - 1
                newSize -= newSize % granularity
                Resize(newSize)
            }
            num = newNum
        }

        /*
         ================
         idList<T>::AssureSize

         Makes sure the list has at least the given number of elements and initialize any elements not yet initialized.
         ================
         */
        fun AssureSize(
            newSize: Int,
            initValue: T
        ) {    // assure list has given number of elements and initialize any new elements
            var newSize = newSize
            val newNum = newSize
            if (newSize > size) {
                if (granularity == 0) {    // this is a hack to fix our memset classes
                    granularity = 16
                }
                newSize += granularity - 1
                newSize -= newSize % granularity
                num = size
                Resize(newSize)
                for (i in num until newSize) {
                    list!![i] = initValue
                }
            }
            num = newNum
        }

        /*
         ================
         idList<T>::AssureSizeAlloc

         Makes sure the list has at least the given number of elements and allocates any elements using the allocator.

         NOTE: This function can only be called on lists containing pointers. Calling it
         on non-pointer lists will cause a compiler error.
         ================
         */
        fun AssureSizeAlloc(
            newSize: Int,  /*new_t*/
            allocator: Class<T>
        ) {    // assure the pointer list has the given number of elements and allocate any new elements
            var newSize = newSize
            val newNum = newSize
            if (newSize > size) {
                if (granularity == 0) {    // this is a hack to fix our memset classes
                    granularity = 16
                }
                newSize += granularity - 1
                newSize -= newSize % granularity
                num = size
                Resize(newSize)
                for (i in num until newSize) {
                    try {
                        list!![i] =  /*( * allocator) ()*/
                            allocator.newInstance() as T //TODO: check if any of this is necessary?
                    } catch (ex: InstantiationException) {
                        Logger.getLogger(List::class.java.name).log(Level.SEVERE, null, ex)
                    } catch (ex: IllegalAccessException) {
                        Logger.getLogger(List::class.java.name).log(Level.SEVERE, null, ex)
                    }
                }
            }
            num = newNum
        }

        //
        /*
         ================
         idList<T>::Ptr

         Returns a pointer to the begining of the array.  Useful for iterating through the list in loops.

         Note: may return NULL if the list is empty.

         FIXME: Create an iterator template for this kind of thing.
         ================
         */
        @Deprecated("")
        fun getList(): Array<T> {                                        // returns a pointer to the list
            return list!!
        }

        fun <T> getList(type: Class<out Array<T>>): Array<T>? {
            if (num == 0) {
                return null
            }
            return Arrays.copyOf(list, num, type)

            // returns a pointer to the list
        }

        fun Ptr(): Array<T> {
            return list as Array<T>
        }

        //public	const T *	Ptr( ) const;									// returns a pointer to the list
        /*
         ================
         idList<T>::Alloc

         Returns a reference to a new data element at the end of the list.
         ================
         */
        fun Alloc(): T? {                                    // returns reference to a new data element at the end of the list
            if (list == null) {
                Resize(granularity)
            }
            if (num == size) {
                Resize(size + granularity)
            }
            try {
                return type!!.newInstance().also { list!![num++] = it }
            } catch (ex: InstantiationException) {
//                Logger.getLogger(List.class.getName()).log(Level.SEVERE, null, ex);
            } catch (ex: IllegalAccessException) {
            }
            return null
        }

        /*
         ================
         idList<T>::Append

         Increases the size of the list by one element and copies the supplied data into it.

         Returns the index of the new element.
         ================
         */
        fun Append(obj: T): Int { // append element
            if (list == null) {
                Resize(granularity)
            }
            if (num == size) {
                val newsize: Int
                if (granularity == 0) {    // this is a hack to fix our memset classes
                    granularity = 16
                }
                newsize = size + granularity
                Resize(newsize - newsize % granularity)
            }
            list!![num] = obj
            num++
            return num - 1
        }

        /*
         ================
         idList<T>::Append

         adds the other list to this one

         Returns the size of the new combined list
         ================
         */
        fun Append(other: idList<T>): Int {                // append list
            if (list == null) {
                if (granularity == 0) {    // this is a hack to fix our memset classes
                    granularity = 16
                }
                Resize(granularity)
            }
            val n = other.Num()
            for (i in 0 until n) {
                Append(other[i])
            }
            return Num()
        }

        /*
         ================
         idList<T>::AddUnique

         Adds the data to the list if it doesn't already exist.  Returns the index of the data in the list.
         ================
         */
        fun AddUnique(obj: T): Int {            // add unique element
            var index: Int
            index = FindIndex(obj)
            if (index < 0) {
                index = Append(obj)
            }
            return index
        }

        /*
         ================
         idList<T>::Insert

         Increases the size of the list by at leat one element if necessary
         and inserts the supplied data into it.

         Returns the index of the new element.
         ================
         */

        fun Insert(obj: T, index: Int = 0): Int {            // insert the element at the given index
            var index = index
            if (list == null) {
                Resize(granularity)
            }
            if (num == size) {
                val newsize: Int
                if (granularity == 0) {    // this is a hack to fix our memset classes
                    granularity = 16
                }
                newsize = size + granularity
                Resize(newsize - newsize % granularity)
            }
            if (index < 0) {
                index = 0
            } else if (index > num) {
                index = num
            }
            for (i in num downTo index + 1) {
                list!![i] = list!![i - 1]
            }
            num++
            list!![index] = obj
            return index
        }

        /*
         ================
         idList<T>::FindIndex

         Searches for the specified data in the list and returns it's index.  Returns -1 if the data is not found.
         ================
         */
        fun FindIndex(obj: T?): Int {                // find the index for the given element
            var i: Int
            i = 0
            while (i < num) {
                if (list!![i] == obj) {
                    return i
                }
                i++
            }

            // Not found
            return -1
        }

        /*
         ================
         idList<T>::Find

         Searches for the specified data in the list and returns it's address. Returns NULL if the data is not found.
         ================
         */
        fun Find(obj: T?): Int? {                        // find pointer to the given element
            val i: Int
            i = FindIndex(obj)
            return if (i >= 0) {
                i //TODO:test whether returning the index instead of the address works!!!
            } else null
        }

        /*
         ================
         idList<T>::FindNull

         Searches for a NULL pointer in the list.  Returns -1 if NULL is not found.

         NOTE: This function can only be called on lists containing pointers. Calling it
         on non-pointer lists will cause a compiler error.
         ================
         */
        fun FindNull(): Int {                                // find the index for the first NULL pointer in the list
            var i: Int
            i = 0
            while (i < num) {
                if (list?.get(i) == null) {
                    return i
                }
                i++
            }

            // Not found
            return -1
        }

        /*
         ================
         idList<T>::IndexOf

         Takes a pointer to an element in the list and returns the index of the element.
         This is NOT a guarantee that the object is really in the list.
         Function will assert in debug builds if pointer is outside the bounds of the list,
         but remains silent in release builds.
         ================
         */
        fun IndexOf(objptr: T): Int {                    // returns the index for the pointer to an element in the list
            val index: Int

//            index = objptr - list;
            index = FindIndex(objptr)
            assert(index >= 0)
            assert(index < num)
            return index
        }

        /*
         ================
         idList<T>::RemoveIndex

         Removes the element at the specified index and moves all data following the element down to fill in the gap.
         The number of elements in the list is reduced by one.  Returns false if the index is outside the bounds of the list.
         Note that the element is not destroyed, so any memory used by it may not be freed until the destruction of the list.
         ================
         */
        fun RemoveIndex(index: Int): Boolean {                            // remove the element at the given index
            var i: Int
            assert(list != null)
            assert(index >= 0)
            assert(index < num)
            if (index < 0 || index >= num) {
                return false
            }
            num--
            i = index
            while (i < num) {
                list!![i] = list!![i + 1]
                i++
            }
            return true
        }

        /*
         ================
         idList<T>::Remove

         Removes the element if it is found within the list and moves all data following the element down to fill in the gap.
         The number of elements in the list is reduced by one.  Returns false if the data is not found in the list.  Note that
         the element is not destroyed, so any memory used by it may not be freed until the destruction of the list.
         ================
         */
        fun Remove(obj: T): Boolean {                            // remove the element
            val index: Int
            index = FindIndex(obj)
            return if (index >= 0) {
                RemoveIndex(index)
            } else false
        }

        /*
         ================
         idList<T>::Sort

         Performs a qsort on the list using the supplied comparison function.  Note that the data is merely moved around the
         list, so any pointers to data within the list may no longer be valid.
         ================
         */
        fun Sort() {
            if (list == null) {
                return
            }
            if (list!![0] is idPoolStr) {
                this.Sort(object : cmp_t<idStr> {
                    override fun compare(a: idStr, b: idStr): Int {
                        return a.Icmp(b)
                    }
                } as cmp_t<T>)
            } else if (list!![0] is idInternalCVar) {
                this.Sort(CVarSystem.idListSortCompare() as cmp_t<T>) // lol i hope it works
            } else if (list!![0] is commandDef_s) {
                this.Sort(CmdSystem.idListSortCompare() as cmp_t<T>)
            } else {
                this.Sort(idListSortCompare())
            }
        }

        fun Sort(sortCompareFun: cmp_t<T> /*= ( cmp_t * )&idListSortCompare<T> */) {

//	typedef int cmp_c(const void *, const void *);
//
//	cmp_c *vCompare = (cmp_c *)compare;
//	qsort( ( void * )list, ( size_t )num, sizeof( T ), vCompare );
            if (list != null) {
                Arrays.sort(list, sortCompareFun)
            }
        }

        /*
         ================
         idList<T>::SortSubSection

         Sorts a subsection of the list.
         ================
         */

        fun SortSubSection(
            startIndex: Int,
            endIndex: Int,
            compare: cmp_t<T> = idListSortCompare<T>() /*= ( cmp_t * )&idListSortCompare<T>*/
        ) {
            var startIndex = startIndex
            var endIndex = endIndex
            if (list == null) {
                return
            }
            if (startIndex < 0) {
                startIndex = 0
            }
            if (endIndex >= num) {
                endIndex = num - 1
            }
            if (startIndex >= endIndex) {
                return
            }
            //	typedef int cmp_c(const void *, const void *);
            Arrays.sort(list, startIndex, endIndex + 1, compare)
        }

        /*
         ================
         idList<T>::Swap

         Swaps the contents of two lists
         ================
         */
        fun Swap(other: idList<T>) {                        // swap the contents of the lists
            val swap_num: Int
            val swap_size: Int
            val swap_granularity: Int
            val swap_list: Array<T>
            swap_num = num
            swap_size = size
            swap_granularity = granularity
            swap_list = list!!
            num = other.num
            size = other.size
            granularity = other.granularity
            list = other.list
            other.num = swap_num
            other.size = swap_size
            other.granularity = swap_granularity
            other.list = swap_list
        }

        /*
         ================
         idList<T>::DeleteContents

         Calls the destructor of all elements in the list.  Conditionally frees up memory used by the list.
         Note that this only works on lists containing pointers to objects and will cause a compiler error
         if called with non-pointers.  Since the list was not responsible for allocating the object, it has
         no information on whether the object still exists or not, so care must be taken to ensure that
         the pointers are still valid when this function is called.  Function will set all pointers in the
         list to NULL.
         ================
         */
        fun DeleteContents(clear: Boolean) {                        // delete the contents of the list
            if (clear) {
                Clear()
            } else {
                list = arrayOfNulls<Any>(list!!.size) as Array<T>
            }
        }

        companion object {
            //TODO: implement java.util.List
            val SIZE = (Integer.SIZE
                    + Integer.SIZE
                    + Integer.SIZE
                    + TempDump.CPP_class.Pointer.SIZE) //T

            //
            private var DBG_counter = 0
        }
    }

    private class idListSortCompare<T> : cmp_t<T> {
        override fun compare(a: T, b: T): Int {
            return reflects._Minus(a as Any, b as Any) as Int
        }
    }
}
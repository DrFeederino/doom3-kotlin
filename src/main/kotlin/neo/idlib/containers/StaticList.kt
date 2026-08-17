package neo.idlib.containers

import java.util.logging.Level
import java.util.logging.Logger

class StaticList {
    /*
     ===============================================================================

     Static list template
     A non-growing, memset-able list using no memory allocation.

     ===============================================================================
     */
    class idStaticList<T>(private val size: Int) {
        private var list: Array<T> = arrayOfNulls<Any>(size) as Array<T>
        private var num = 0
        private lateinit var type: Class<T>

        constructor(size: Int, type: Class<T>) : this(size) {
            this.type = type
        }

        constructor(size: Int, newList: idStaticList<T>) : this(size) {
            list = newList.list
        }

        //	public					idStaticList( const idStaticList<T,size> &other );
        //	public					~idStaticList<T,size>( void );
        //
        /*
         ================
         idStaticList<T,size>::Clear

         Sets the number of elements in the list to 0.  Assumes that T automatically handles freeing up memory.
         ================
         */
        fun Clear() {                                        // marks the list as empty.  does not deallocate or intialize data.
            num = 0
        }

        /*
         ================
         idStaticList<T,size>::Num

         Returns the number of elements currently contained in the list.
         ================
         */
        fun Num(): Int {                                    // returns number of elements in list
            return num
        }

        /*
         ================
         idStaticList<T,size>::Max

         Returns the maximum number of elements in the list.
         ================
         */
        fun Max(): Int {                                    // returns the maximum number of elements in the list
            return size
        }

        /*
         ================
         idStaticList<T,size>::SetNum

         Set number of elements in list.
         ================
         */
        fun SetNum(newnum: Int) {                                // set number of elements in list
            assert(newnum >= 0)
            assert(newnum <= size)
            num = newnum
        }

        //
        //public		size_t				Allocated( void ) const;							// returns total size of allocated memory
        //public		size_t				Size( void ) const;									// returns total size of allocated memory including size of list T
        // returns size of the used elements in the list
        fun  /*size_t*/MemoryUsed(): Int {
            return num * Integer.BYTES //TODO: * sizeof(list[0]);
        }

        //
        /*
         ================
         idStaticList<T,size>::operator[] const

         Access operator.  Index must be within range or an assert will be issued in debug builds.
         Release builds do no range checking.
         ================
         */
        operator fun get(index: Int): T {
            assert(index >= 0)
            assert(index < num)
            return list[index]
        }

        operator fun set(index: Int, value: T): T {
            assert(index >= 0)
            assert(index < num)
            return value.also { list[index] = it }
        }

        //public		T &				operator[]( int index );
        //
        /*
         ================
         idStaticList<T,size>::Ptr

         Returns a pointer to the begining of the array.  Useful for iterating through the list in loops.

         Note: may return NULL if the list is empty.

         FIXME: Create an iterator template for this kind of thing.
         ================
         */
        fun Ptr(): Array<T> {                                        // returns a pointer to the list
            return list
        }

        //public		const T *		Ptr( void ) const;									// returns a pointer to the list
        /*
         ================
         idStaticList<T,size>::Alloc

         Returns a pointer to a new data element at the end of the list.
         ================
         */
        fun Alloc(): T? {                                        // returns reference to a new data element at the end of the list.  returns NULL when full.
            if (num >= size) {
                return null
            }
            try {
                return type.newInstance().also {
                    list[num++] = it //TODO:init value before sending back. EDIT:ugly, but working.
                }
            } catch (ex: InstantiationException) {
                Logger.getLogger(StaticList::class.java.name).log(Level.SEVERE, null, ex)
            } catch (ex: IllegalAccessException) {
                Logger.getLogger(StaticList::class.java.name).log(Level.SEVERE, null, ex)
            }
            return null
        }

        /*
         ================
         idStaticList<T,size>::Append

         Increases the size of the list by one element and copies the supplied data into it.

         Returns the index of the new element, or -1 when list is full.
         ================
         */
        fun Append(obj: T): Int { // append element
            assert(num < size)
            if (num < size) {
                list[num] = obj
                num++
                return num - 1
            }
            return -1
        }

        /*
         ================
         idStaticList<T,size>::Append

         adds the other list to this one

         Returns the size of the new combined list
         ================
         */
        fun Append(other: idStaticList<T>): Int {        // append list
            var n = other.Num()
            if (num + n > size) { //TODO:which size??
                n = size - num
            }
            for (i in 0 until n) {
                list[num + i] = other.list[i]
            }
            num += n
            return Num()
        }

        /*
         ================
         idStaticList<T,size>::AddUnique

         Adds the data to the list if it doesn't already exist.  Returns the index of the data in the list.
         ================
         */
        fun AddUnique(obj: T): Int {                        // add unique element
            var index: Int
            index = FindIndex(obj)
            if (index < 0) {
                index = Append(obj)
            }
            return index
        }

        /*
         ================
         idStaticList<T,size>::Insert

         Increases the size of the list by at leat one element if necessary 
         and inserts the supplied data into it.

         Returns the index of the new element, or -1 when list is full.
         ================
         */
        fun Insert(obj: T, index: Int): Int {                // insert the element at the given index
            var index = index
            var i: Int
            assert(num < size)
            if (num >= size) {
                return -1
            }
            assert(index >= 0)
            if (index < 0) {
                index = 0
            } else if (index > num) {
                index = num
            }
            i = num
            while (i > index) {
                list[i] = list[i - 1]
                --i
            }
            num++
            list[index] = obj
            return index
        }

        /*
         ================
         idStaticList<T,size>::FindIndex

         Searches for the specified data in the list and returns it's index.  Returns -1 if the data is not found.
         ================
         */
        fun FindIndex(obj: T): Int {                // find the index for the given element
            var i: Int
            i = 0
            while (i < num) {
                if (list[i] === obj) {
                    return i
                }
                i++
            }

            // Not found
            return -1
        }

        //public		T *				Find( T const & obj ) const;						// find pointer to the given element
        /*
         ================
         idStaticList<T,size>::FindNull

         Searches for a NULL pointer in the list.  Returns -1 if NULL is not found.

         NOTE: This function can only be called on lists containing pointers. Calling it
         on non-pointer lists will cause a compiler error.
         ================
         */
        fun FindNull(): Int {                                // find the index for the first NULL pointer in the list
            var i: Int
            i = 0
            while (i < num) {
                if (list[i] == null) {
                    return i
                }
                i++
            }

            // Not found
            return -1
        }

        /*
         ================
         idStaticList<T,size>::IndexOf

         Takes a pointer to an element in the list and returns the index of the element.
         This is NOT a guarantee that the object is really in the list. 
         Function will assert in debug builds if pointer is outside the bounds of the list,
         but remains silent in release builds.
         ================
         */
        fun IndexOf(obj: T): Int {                    // returns the index for the pointer to an element in the list
            //    int index;
            //
            //	index = objptr - list;
            //
            //	assert( index >= 0 );
            //	assert( index < num );
            //
            //	return index;
            return FindIndex(obj)
        }

        /*
         ================
         idStaticList<T,size>::RemoveIndex

         Removes the element at the specified index and moves all data following the element down to fill in the gap.
         The number of elements in the list is reduced by one.  Returns false if the index is outside the bounds of the list.
         Note that the element is not destroyed, so any memory used by it may not be freed until the destruction of the list.
         ================
         */
        fun RemoveIndex(index: Int): Boolean {                            // remove the element at the given index
            var i: Int
            assert(index >= 0)
            assert(index < num)
            if (index < 0 || index >= num) {
                return false
            }
            num--
            i = index
            while (i < num) {
                list[i] = list[i + 1]
                i++
            }
            return true
        }

        /*
         ================
         idStaticList<T,size>::Remove

         Removes the element if it is found within the list and moves all data following the element down to fill in the gap.
         The number of elements in the list is reduced by one.  Returns false if the data is not found in the list.  Note that
         the element is not destroyed, so any memory used by it may not be freed until the destruction of the list.
         ================
         */
        fun Remove(obj: T): Boolean {                            // remove the element
            val index: Int
            index = FindIndex(obj)
            return index >= 0 && RemoveIndex(index)
        }

        //public		void				Swap( idStaticList<T,size> &other );				// swap the contents of the lists
        /*
         ================
         idStaticList<T,size>::DeleteContents

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
                list = arrayOfNulls<Any>(size) as Array<T>
            }
        }
    }
}
package neo.idlib.containers

import java.util.*

class Queue {
    /*
     ===============================================================================

     Queue template

     ===============================================================================
     */
    class idQueueTemplate<T> : LinkedList<T>() {
        fun Add(element: T): Boolean {
            return super.add(element)
        }

        fun Get(): T? {
            return if (super.isEmpty()) {
                null
            } else super.pop()
        }
    }
}
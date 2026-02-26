package neo.idlib.containers

import java.util.*

class Queue {
    //TODO:test this
    /*
     ===============================================================================

     Queue template

     ===============================================================================
     */
    //#define idQueue( T, next )		idQueueTemplate<T, (int)&(((T*)NULL)->next)>
    class idQueueTemplate<T>  //TODO:fix the nextOffset part.
    //        private final static int QUEUE_BLOCK_SIZE = 10;
    //        //
    //        private int first;
    //        private int last;
    //        private T[] queue      = (T[]) new Object[10];
    //        private int    nextOffset = 0;
    //        //
    //        //
        : LinkedList<T>() {
        fun Add(element: T): Boolean {
//            if (nextOffset >= queue.length) {
////		QUEUE_NEXT_PTR(last) = element;
//                expandQueue();
//            }
//            queue[nextOffset] = element;
////            last = element;
//            nextOffset++;
            return super.add(element)
        }

        fun Get(): T? {
            return if (super.isEmpty()) {
                null
            } else super.pop()
            //
//            if (nextOffset <= queue.length - QUEUE_BLOCK_SIZE) {
//                shrinkQueue();
//            }
//
//            return queue[--nextOffset];
        } //        private void expandQueue() {
        //            final T[] tempQueue = queue;
        //            queue = (T[]) new Object[queue.length + QUEUE_BLOCK_SIZE];
        //
        //            System.arraycopy(tempQueue, 0, queue, 0, tempQueue.length);
        //        }
        //
        //        private void shrinkQueue() {
        //            final T[] tempQueue = queue;
        //            queue = (T[]) new Object[queue.length - QUEUE_BLOCK_SIZE];
        //
        //            System.arraycopy(tempQueue, 0, queue, 0, queue.length);
        //        }
    }
}
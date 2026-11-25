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

/**
 * //TODO:implement starcraft linked list
 */
class LinkList {
    /*
     ==============================================================================

     idLinkList

     Circular linked list template

     ==============================================================================
     */
    class idLinkList<T>() {
        private val DBG_count = DBG_counter++
        private var head: idLinkList<T>
        private var next: idLinkList<T>?
        private var owner: T? = null
        private var prev: idLinkList<T>?

        constructor(owner: T?) : this() {
            this.owner = owner
        }

        //public						~idLinkList();
        //
        /*
         ================
         idLinkList<T>::IsListEmpty

         Returns true if the list is empty.
         ================
         */
        fun IsListEmpty(): Boolean {
            return head.next === head
        }

        /*
         ================
         idLinkList<T>::InList

         Returns true if the node is in a list.  If called on the head of a list, will always return false.
         ================
         */
        fun InList(): Boolean {
            return head !== this
        }

        /*
         ================
         idLinkList<T>::Num

         Returns the number of nodes in the list.
         ================
         */
        fun Num(): Int {
            var node: idLinkList<T>
            var num: Int
            num = 0
            node = head.next!!
            while (node !== head) {
                num++
                node = node.next!!
            }
            return num
        }

        /*
         ================
         idLinkList<T>::Clear

         If node is the head of the list, clears the list.  Otherwise it just removes the node from the list.
         ================
         */
        fun Clear() {
            if (head === this) {
                while (next !== this) {
                    next!!.Remove()
                }
            } else {
                Remove()
            }
        }

        /*
         ================
         idLinkList<T>::InsertBefore

         Places the node before the existing node in the list.  If the existing node is the head,
         then the new node is placed at the end of the list.
         ================
         */
        fun InsertBefore(node: idLinkList<T>) {
            Remove()
            next = node
            prev = node.prev
            node.prev = this
            prev!!.next = this
            head = node.head
        }

        /*
         ================
         idLinkList<T>::InsertAfter

         Places the node after the existing node in the list.  If the existing node is the head,
         then the new node is placed at the beginning of the list.
         ================
         */
        fun InsertAfter(node: idLinkList<T>) {
            Remove()
            prev = node
            next = node.next
            node.next = this
            next!!.prev = this
            head = node.head
        }

        /*
         ================
         idLinkList<T>::AddToEnd

         Adds node at the end of the list
         ================
         */
        fun AddToEnd(node: idLinkList<T>) {
            InsertBefore(node.head)
        }

        /*
         ================
         idLinkList<T>::AddToFront

         Adds node at the beginning of the list
         ================
         */
        fun AddToFront(node: idLinkList<T>) {
            InsertAfter(node.head)
        }

        /*
         ================
         idLinkList<T>::Remove

         Removes node from list
         ================
         */
        fun Remove() {
            prev!!.next = next
            next!!.prev = prev
            next = this
            prev = this
            head = this
        }

        /*
         ================
         idLinkList<T>::Next

         Returns the next object in the list, or NULL if at the end.
         ================
         */
        fun Next(): T? {
            return if (null == next || next === head) {
                null
            } else next!!.owner
        }

        /*
         ================
         idLinkList<T>::Prev

         Returns the previous object in the list, or NULL if at the beginning.
         ================
         */
        fun Prev(): T? {
            return if (null == prev || prev === head) {
                null
            } else prev!!.owner
        }

        //
        /*
         ================
         idLinkList<T>::Owner

         Gets the object that is associated with this node.
         ================
         */
        fun Owner(): T? {
            return owner
        }

        /*
         ================
         idLinkList<T>::SetOwner

         Sets the object that this node is associated with.
         ================
         */
        fun SetOwner(newOwner: T?) {
            owner = newOwner
        }

        //
        /*
         ================
         idLinkList<T>::ListHead

         Returns the head of the list.  If the node isn't in a list, it returns
         a pointer to itself.
         ================
         */
        fun ListHead(): idLinkList<T> {
            return head
        }

        /*
         ================
         idLinkList<T>::NextNode

         Returns the next node in the list, or NULL if at the end.
         ================
         */
        fun NextNode(): idLinkList<T>? {
            return if (next === head) {
                null
            } else next
        }

        /*
         ================
         idLinkList<T>::PrevNode

         Returns the previous node in the list, or NULL if at the beginning.
         ================
         */
        fun PrevNode(): idLinkList<T>? {
            return if (prev === head) {
                null
            } else prev
        }

        companion object {
            //
            //
            private var DBG_counter = 0
        }

        /*
         ================
         idLinkList<T>::idLinkList

         Node is initialized to be the head of an empty list
         ================
         */
        init {
            head = this
            next = this
            prev = this
        }
    }
}
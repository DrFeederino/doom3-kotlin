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

import neo.idlib.idLib

class Hierarchy {
    /*
     ==============================================================================

     idHierarchy

     ==============================================================================
     */
    class idHierarchy<T>     //
    //
    {
        private var child: idHierarchy<T>? = null
        private var owner: T? = null
        private var parent: idHierarchy<T>? = null
        private var sibling: idHierarchy<T>? = null

        //public						~idHierarchy();
        //	
        /*
         ================
         idHierarchy<T>::SetOwner

         Sets the object that this node is associated with.
         ================
         */
        fun SetOwner(`object`: T?) {
            owner = `object`
        }

        /*
         ================
         idHierarchy<T>::Owner

         Gets the object that is associated with this node.
         ================
         */
        fun Owner(): T? {
            return owner
        }

        /*
         ================
         idHierarchy<T>::ParentTo

         Makes the given node the parent.
         ================
         */
        fun ParentTo(node: idHierarchy<T>) {
            RemoveFromParent()
            parent = node
            sibling = node.child
            node.child = this
        }

        /*
         ================
         idHierarchy<T>::MakeSiblingAfter

         Makes the given node a sibling after the passed in node.
         ================
         */
        fun MakeSiblingAfter(node: idHierarchy<T>) {
            RemoveFromParent()
            parent = node.parent
            sibling = node.sibling
            node.sibling = this
        }

        fun ParentedBy(node: idHierarchy<T>): Boolean {
            if (parent === node) {
                return true
            } else if (parent != null) {
                return parent!!.ParentedBy(node)
            }
            return false
        }

        fun RemoveFromParent() {
            val prev: idHierarchy<T>?
            if (parent != null) {
                prev = GetPriorSiblingNode()
                if (prev != null) {
                    prev.sibling = sibling
                } else {
                    parent!!.child = sibling
                }
            }
            parent = null
            sibling = null
        }

        /*
         ================
         idHierarchy<T>::RemoveFromHierarchy

         Removes the node from the hierarchy and adds it's children to the parent.
         ================
         */
        fun RemoveFromHierarchy() {
            val parentNode: idHierarchy<T>?
            var node: idHierarchy<T>
            parentNode = parent
            RemoveFromParent()
            if (parentNode != null) {
                while (child != null) {
                    node = child!!
                    node.RemoveFromParent()
                    node.ParentTo(parentNode)
                }
            } else {
                while (child != null) {
                    child!!.RemoveFromParent()
                }
            }
        }

        // parent of this node
        fun GetParent(): T? {
            return if (parent != null) {
                parent!!.owner
            } else null
        }

        // first child of this node
        fun GetChild(): T? {
            return if (child != null) {
                child!!.owner
            } else null
        }

        // next node with the same parent
        fun GetSibling(): T? {
            return if (sibling != null) {
                sibling!!.owner
            } else null
        }

        /*
         ================
         idHierarchy<T>::GetPriorSiblingNode

         Returns NULL if no parent, or if it is the first child.
         ================
         */
        fun GetPriorSibling(): T? {        // previous node with the same parent
            if (null == parent || parent!!.child === this) {
                return null
            }
            var prev: idHierarchy<T>?
            var node: idHierarchy<T>?
            node = parent!!.child
            prev = null
            while (node !== this && node != null) {
                prev = node
                node = node.sibling
            }
            if (node !== this) {
                idLib.Error("idHierarchy::GetPriorSibling: could not find node in parent's list of children")
            }
            return prev?.owner
        }

        /*
         ================
         idHierarchy<T>::GetNext

         Goes through all nodes of the hierarchy.
         ================
         */
        fun GetNext(): T? {            // goes through all nodes of the hierarchy
            var node: idHierarchy<T>?
            return if (child != null) {
                child!!.owner
            } else {
                node = this
                while (node != null && null == node.sibling) {
                    node = node.parent
                }
                if (node != null) {
                    node.sibling!!.owner
                } else {
                    null
                }
            }
        }

        /*
         ================
         idHierarchy<T>::GetNextLeaf

         Goes through all leaf nodes of the hierarchy.
         ================
         */
        fun GetNextLeaf(): T? {        // goes through all leaf nodes of the hierarchy
            var node: idHierarchy<T>?
            return if (child != null) {
                node = child
                while (node!!.child != null) {
                    node = node.child
                }
                node.owner
            } else {
                node = this
                while (node != null && null == node.sibling) {
                    node = node.parent
                }
                if (node != null) {
                    node = node.sibling
                    while (node!!.child != null) {
                        node = node.child
                    }
                    node.owner
                } else {
                    null
                }
            }
        }

        /*
         ================
         idHierarchy<T>::GetPriorSibling

         Returns NULL if no parent, or if it is the first child.
         ================
         */
        private fun GetPriorSiblingNode(): idHierarchy<T>? { // previous node with the same parent
            if (parent == null || (parent?.child == this)) {
                return null
            }

            var prev: idHierarchy<T>?
            var node: idHierarchy<T>?

            node = parent?.child
            prev = null
            while ((node != this) && (node != null)) {
                prev = node
                node = node.sibling
            }

            if (node != this) {
                idLib.Error("idHierarchy::GetPriorSibling: could not find node in parent's list of children")
            }

            return prev
        }
    }
}
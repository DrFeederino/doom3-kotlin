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

//package neo.idlib.containers
//
//
//class Stack {
//    /*
//     ===============================================================================
//
//     Stack template
//
//     ===============================================================================
//     */
//    class idStackTemplate<T>  //
//    //
//    {
//        private var stack: Array<T?> = arrayOfNulls<Any?>(10) as Array<T?>
//        private var top = 0
//
//        //
//        fun Add(element: T) { //push
//            if (top >= stack.size) { //reached top of stack
//                expand()
//            }
//            stack[top++] = element
//        }
//
//        fun Get(): T? { //pop
//            if (top < 0) { //reached bottom
//                return null
//            }
//            if (stack.size - STACK_BLOCK_SIZE > 0 && top < stack.size - STACK_BLOCK_SIZE) { //reached block threshold
//                shrink()
//            }
//            return stack[top--]
//        }
//
//        private fun expand() {
//            val temp = stack
//            stack = arrayOfNulls<Any?>(stack.size + STACK_BLOCK_SIZE) as Array<T?>
//            System.arraycopy(temp, 0, stack, 0, temp.size)
//        }
//
//        private fun shrink() {
//            val temp = stack
//            stack = arrayOfNulls<Any?>(stack.size - STACK_BLOCK_SIZE) as Array<T?>
//            System.arraycopy(temp, 0, stack, 0, stack.size)
//        }
//
//        private val STACK_BLOCK_SIZE = 10
//    }
//}
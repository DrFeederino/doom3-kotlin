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

Translated to Kotlin by Dr. Feederino with support of Claude Code.

===========================================================================
*/
package neo.Renderer

import neo.Renderer.Model.lightingCache_s
import neo.Renderer.Model.shadowCache_s
import neo.TempDump.Deprecation_Exception
import neo.framework.CVarSystem.CVAR_INTEGER
import neo.framework.CVarSystem.CVAR_RENDERER
import neo.framework.CVarSystem.idCVar
import neo.framework.CmdSystem.CMD_FL_RENDERER
import neo.framework.CmdSystem.cmdFunction_t
import neo.framework.CmdSystem.cmdSystem
import neo.framework.Common
import neo.idlib.CmdArgs
import neo.idlib.geometry.DrawVert.idDrawVert
import neo.idlib.geometry.DrawVert.toByteBuffer
import neo.idlib.math.idVec3
import neo.idlib.math.idVec4
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.ARBVertexBufferObject
import java.nio.ByteBuffer


object VertexCache {
    val vertexCache: idVertexCache = idVertexCache()
    val EXPAND_HEADERS: Int = 1024

    //
    val FRAME_MEMORY_BYTES: Int = 0x200000

    // vertex cache calls should only be made by the front end
    val NUM_VERTEX_FRAMES: Int = 2

    enum class vertBlockTag_t {
        TAG_FREE,
        TAG_USED,
        TAG_FIXED,

        // for the temp buffers
        TAG_TEMP // in frame temp area, not static area
    }

    class vertCache_s : Iterable<vertCache_s?> {
        var frameUsed: Int = 0 // it can't be purged if near the current frame
        var indexBuffer: Boolean = false // holds indexes instead of vertexes
        var next: vertCache_s? = this
        var prev: vertCache_s? = null // may be on the static list or one of the frame lists
        var offset: Int = 0
        var size: Int = 0 // may be larger than the amount asked for, due to round up and minimum fragment sizes
        var tag: vertBlockTag_t? = null // a tag of 0 is a free block
        var user: vertCache_s? = null // will be set to zero when purged
        var  /*GLuint*/vbo: Int = 0
        var virtMem: ByteBuffer? = null // only one of vbo / virtMem will be set
        override fun iterator(): MutableIterator<vertCache_s> {
            val i: MutableIterator<vertCache_s> = object : MutableIterator<vertCache_s> {
                override fun hasNext(): Boolean {
                    return next != null
                }

                override fun next(): vertCache_s {
                    return (next)!!
                }

                override fun remove() {
                    throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
                }
            }
            return i
        }

        companion object {
            /**
             * Creates an array starting at the current object, till it reaches
             * NULL.
             */
            fun toArray(cache_s: vertCache_s?): Array<vertCache_s> {
                val array: MutableList<vertCache_s> = ArrayList(10)
                val iterator: Iterator<vertCache_s>
                if (cache_s != null) {
                    iterator = cache_s.iterator()
                    array.add(cache_s)
                    while (iterator.hasNext()) {
                        array.add(iterator.next())
                    }
                }
                return array.toTypedArray()
            }
        }
    }

    //
    /*
     ==============
     R_ListVertexCache_f
     ==============
     */
    internal class R_ListVertexCache_f private constructor() : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            vertexCache.List()
        }

        companion object {
            val instance: cmdFunction_t = R_ListVertexCache_f()
        }
    }

    //================================================================================
    class idVertexCache {
        //
        private var tempBuffers // allocated at startup
                : Array<vertCache_s?> = arrayOfNulls(NUM_VERTEX_FRAMES)

        //
        private var allocatingTempBuffer: Boolean = false // force GL_STREAM_DRAW_ARB

        //
        private var currentFrame: Int = 0 // for purgable block tracking
        private val deferredFreeList // head of doubly linked list
                : vertCache_s
        private var dynamicAllocThisFrame: Int = 0
        private var dynamicCountThisFrame: Int = 0
        private val dynamicHeaders // head of doubly linked list
                : vertCache_s

        // staticHeaders.next is most recently used
        //
        private var frameBytes: Int = 0 // for each of NUM_VERTEX_FRAMES frames
        private val freeDynamicHeaders // head of doubly linked list
                : vertCache_s

        //
        private val freeStaticHeaders // head of doubly linked list
                : vertCache_s
        private var listNum: Int = 0 // currentFrame % NUM_VERTEX_FRAMES, determines which tempBuffers to use

        //
        private var staticAllocThisFrame: Int = 0 // debug counter
        private var staticAllocTotal: Int = 0 // for end of frame purging
        private var staticCountThisFrame: Int = 0

        //
        private var staticCountTotal: Int = 0
        private val staticHeaders // head of doubly linked list in MRU order,
                : vertCache_s
        private var tempOverflow: Boolean = false // had to alloc a temp in static memory

        //
        private var virtualMemory: Boolean = false // not fast stuff

        //
        //
        init {
            freeStaticHeaders = vertCache_s()
            freeDynamicHeaders = vertCache_s()
            dynamicHeaders = vertCache_s()
            deferredFreeList = vertCache_s()
            staticHeaders = vertCache_s()
        }

        fun Init() {
            cmdSystem.AddCommand("listVertexCache", R_ListVertexCache_f.instance, CMD_FL_RENDERER, "lists vertex cache")
            if (r_vertexBufferMegs.GetInteger() < 8) {
                r_vertexBufferMegs.SetInteger(8)
            }
            virtualMemory = false

            // use ARB_vertex_buffer_object unless explicitly disabled
            if (r_useVertexBuffers!!.GetInteger() != 0 && glConfig.ARBVertexBufferObjectAvailable) {
                Common.common.Printf("using ARB_vertex_buffer_object memory\n")
            } else {
                virtualMemory = true
                r_useIndexBuffers!!.SetBool(false)
                Common.common.Printf("WARNING: vertex array range in virtual memory (SLOW)\n")
            }

            // initialize the cache memory blocks
            freeStaticHeaders.prev = freeStaticHeaders
            freeStaticHeaders.next = freeStaticHeaders.prev
            staticHeaders.prev = staticHeaders
            staticHeaders.next = staticHeaders.prev
            freeDynamicHeaders.prev = freeDynamicHeaders
            freeDynamicHeaders.next = freeDynamicHeaders.prev
            dynamicHeaders.prev = dynamicHeaders
            dynamicHeaders.next = dynamicHeaders.prev
            deferredFreeList.prev = deferredFreeList
            deferredFreeList.next = deferredFreeList.prev

            // set up the dynamic frame memory
            frameBytes = FRAME_MEMORY_BYTES
            staticAllocTotal = 0
            var junk: ByteBuffer = BufferUtils.createByteBuffer(frameBytes) // Mem_Alloc(frameBytes);
            tempBuffers = arrayOfNulls(NUM_VERTEX_FRAMES)
            for (i in 0 until NUM_VERTEX_FRAMES) {
                allocatingTempBuffer = true // force the alloc to use GL_STREAM_DRAW_ARB
                tempBuffers[i] = Alloc(junk, frameBytes)
                allocatingTempBuffer = false
                tempBuffers[i]!!.tag = vertBlockTag_t.TAG_FIXED
                // unlink these from the static list, so they won't ever get purged
                tempBuffers[i]!!.next!!.prev = tempBuffers[i]!!.prev
                tempBuffers[i]!!.prev!!.next = tempBuffers[i]!!.next
            }
            EndFrame()
        }

        fun Shutdown() {
        }

        /*
         =============
         idVertexCache::IsFast

         just for gfxinfo printing
         =============
         */
        fun IsFast(): Boolean {
            return !virtualMemory
        }

        /*
         ===========
         idVertexCache::PurgeAll

         Used when toggling vertex programs on or off, because
         the cached data isn't valid
         ===========
         */
        // called when vertex programs are enabled or disabled, because
        // the cached data is no longer valid
        fun PurgeAll() {
            while (staticHeaders.next !== staticHeaders) {
                ActuallyFree(staticHeaders.next)
            }
        }

        // Tries to allocate space for the given data in fast vertex
        // memory, and copies it over.
        // Alloc does NOT do a touch, which allows purging of things
        // created at level load time even if a frame hasn't passed yet.
        // These allocations can be purged, which will zero the pointer.

        fun Alloc(
            data: ByteBuffer,
            size: Int,
            buffer: vertCache_s? = null,
            indexBuffer: Boolean = false /*= false*/
        ): vertCache_s {
            var buffer: vertCache_s? = buffer
            var block: vertCache_s?
            if (size <= 0) {
                Common.common.Error("idVertexCache::Alloc: size = %d\n", size)
            }

            // if we can't find anything, it will be NULL
            buffer = null

            // if we don't have any remaining unused headers, allocate some more
            if (freeStaticHeaders.next === freeStaticHeaders) {
                for (i in 0 until EXPAND_HEADERS) {
                    block = vertCache_s()
                    block.next = freeStaticHeaders.next
                    block.prev = freeStaticHeaders
                    block.next!!.prev = block
                    block.prev!!.next = block
                    if (!virtualMemory) {
                        block.vbo = qgl.qglGenBuffersARB()
                    }
                }
            }

            // move it from the freeStaticHeaders list to the staticHeaders list
            block = freeStaticHeaders.next
            block!!.next!!.prev = block.prev
            block.prev!!.next = block.next
            block.next = staticHeaders.next
            block.prev = staticHeaders
            block.next!!.prev = block
            block.prev!!.next = block
            block.size = size
            block.offset = 0
            block.tag = vertBlockTag_t.TAG_USED

            // save data for debugging
            staticAllocThisFrame += block.size
            staticCountThisFrame++
            staticCountTotal++
            staticAllocTotal += block.size

            // this will be set to zero when it is purged
            block.user = buffer
            buffer = block

            // allocation doesn't imply used-for-drawing, because at level
            // load time lots of things may be created, but they aren't
            // referenced by the GPU yet, and can be purged if needed.
            block.frameUsed = currentFrame - NUM_VERTEX_FRAMES
            block.indexBuffer = indexBuffer

            // copy the data
            if (block.vbo != 0) {
                if (indexBuffer) {
                    qgl.qglBindBufferARB(ARBVertexBufferObject.GL_ELEMENT_ARRAY_BUFFER_ARB, block.vbo)
                    qgl.qglBufferDataARB(
                        ARBVertexBufferObject.GL_ELEMENT_ARRAY_BUFFER_ARB,
                        size,
                        data,
                        ARBVertexBufferObject.GL_STATIC_DRAW_ARB
                    )
                } else {
                    qgl.qglBindBufferARB(ARBVertexBufferObject.GL_ARRAY_BUFFER_ARB, block.vbo)
                    if (allocatingTempBuffer) {
                        qgl.qglBufferDataARB(
                            ARBVertexBufferObject.GL_ARRAY_BUFFER_ARB,
                            size,
                            data,
                            ARBVertexBufferObject.GL_STREAM_DRAW_ARB
                        )
                    } else {
                        qgl.qglBufferDataARB(
                            ARBVertexBufferObject.GL_ARRAY_BUFFER_ARB,
                            size,
                            data,
                            ARBVertexBufferObject.GL_STATIC_DRAW_ARB
                        )
                    }
                }
            } else {
                block.virtMem = data.duplicate()
            }
            return buffer
        }

        @Deprecated("")
        fun Alloc(data: IntArray, size: Int, buffer: vertCache_s?, indexBuffer: Boolean /*= false*/) {
            val byteData: ByteBuffer = ByteBuffer.allocate(data.size * 4)
            byteData.asIntBuffer().put(data)
            throw Deprecation_Exception()
        }

        fun Alloc(data: IntArray?, size: Int, indexBuffer: Boolean): vertCache_s {
            val byteData: ByteBuffer = BufferUtils.createByteBuffer(size)
            byteData.asIntBuffer().put(data)
            return Alloc(byteData, size, indexBuffer)
        }

        fun Alloc(data: ByteBuffer, size: Int, indexBuffer: Boolean): vertCache_s {
            return Alloc(data, size, null, indexBuffer)
        }

        fun Alloc(data: Array<idDrawVert>?, size: Int, buffer: vertCache_s?): vertCache_s {
            return Alloc(toByteBuffer((data)!!), size, buffer, false)
        }

        fun Alloc(data: Array<idDrawVert>?, size: Int): vertCache_s {
            return Alloc(toByteBuffer((data)!!), size, null)
        }

        fun Alloc(data: Array<lightingCache_s>, size: Int): vertCache_s {
            return Alloc(lightingCache_s.toByteBuffer(data), size, null)
        }

        fun Alloc(data: Array<shadowCache_s>, size: Int): vertCache_s {
            return Alloc(shadowCache_s.toByteBuffer(data), size, null)
        }

        /*
         ==============
         idVertexCache::Position

         this will be a real pointer with virtual memory,
         but it will be an int offset cast to a pointer with
         ARB_vertex_buffer_object

         The ARB_vertex_buffer_object will be bound
         ==============
         */
        // This will be a real pointer with virtual memory,
        // but it will be an int offset cast to a pointer of ARB_vertex_buffer_object
        fun Position(buffer: vertCache_s?): ByteBuffer {
            if (null == buffer || buffer.tag == vertBlockTag_t.TAG_FREE) {
                Common.common.FatalError("idVertexCache::Position: bad vertCache_t")
            }

            // the ARB vertex object just uses an offset
            if (buffer!!.vbo != 0) {
                if (r_showVertexCache.GetInteger() == 2) {
                    if (buffer!!.tag == vertBlockTag_t.TAG_TEMP) {
                        Common.common.Printf(
                            "GL_ARRAY_BUFFER_ARB = %d + %d (%d bytes)\n",
                            buffer.vbo,
                            buffer.offset,
                            buffer.size
                        )
                    } else {
                        Common.common.Printf("GL_ARRAY_BUFFER_ARB = %d (%d bytes)\n", buffer.vbo, buffer.size)
                    }
                }
                if (buffer!!.indexBuffer) {
                    qgl.qglBindBufferARB(ARBVertexBufferObject.GL_ELEMENT_ARRAY_BUFFER_ARB, buffer.vbo)
                } else {
                    qgl.qglBindBufferARB(ARBVertexBufferObject.GL_ARRAY_BUFFER_ARB, buffer.vbo)
                }
                return ByteBuffer.allocate(Integer.BYTES).putInt(buffer.offset).flip()
            }

            // virtual memory is a real pointer
            return buffer!!.virtMem!!.position(buffer.offset).flip()
        }

        // if r_useIndexBuffers is enabled, but you need to draw something without
        // an indexCache, this must be called to reset GL_ELEMENT_ARRAY_BUFFER_ARB
        fun UnbindIndex() {
            qgl.qglBindBufferARB(ARBVertexBufferObject.GL_ELEMENT_ARRAY_BUFFER_ARB, 0)
        }

        /*
         ===========
         idVertexCache::AllocFrameTemp

         A frame temp allocation must never be allowed to fail due to overflow.
         We can't simply sync with the GPU and overwrite what we have, because
         there may still be future references to dynamically created surfaces.
         ===========
         */
        // automatically freed at the end of the next frame
        // used for specular texture coordinates and gui drawing, which
        // will change every frame.
        // will return NULL if the vertex cache is completely full
        // As with Position(), this may not actually be a pointer you can access.
        fun AllocFrameTemp(data: ByteBuffer, size: Int): vertCache_s {
            var block: vertCache_s? = null
            if (size <= 0) {
                Common.common.Error("idVertexCache::AllocFrameTemp: size = %d\n", size)
            }
            if (dynamicAllocThisFrame + size > frameBytes) {
                // if we don't have enough room in the temp block, allocate a static block,
                // but immediately free it so it will get freed at the next frame
                tempOverflow = true
                block = Alloc(data, size)
                Free(block)
                return block
            }

            // this data is just going on the shared dynamic list
            // if we don't have any remaining unused headers, allocate some more
            if (freeDynamicHeaders.next === freeDynamicHeaders) {
                for (i in 0 until EXPAND_HEADERS) {
                    block = vertCache_s()
                    block.next = freeDynamicHeaders.next
                    block.prev = freeDynamicHeaders
                    block.next!!.prev = block
                    block.prev!!.next = block
                }
            }

            // move it from the freeDynamicHeaders list to the dynamicHeaders list
            block = freeDynamicHeaders.next
            block!!.next!!.prev = block.prev
            block.prev!!.next = block.next
            block.next = dynamicHeaders.next
            block.prev = dynamicHeaders
            block.next!!.prev = block
            block.prev!!.next = block
            block.size = size
            block.tag = vertBlockTag_t.TAG_TEMP
            block.indexBuffer = false
            block.offset = dynamicAllocThisFrame
            dynamicAllocThisFrame += block.size
            dynamicCountThisFrame++
            block.user = null
            block.frameUsed = 0

            // copy the data
            block.virtMem = tempBuffers[listNum]!!.virtMem
            block.vbo = tempBuffers[listNum]!!.vbo
            if (block.vbo != 0) {
                qgl.qglBindBufferARB(ARBVertexBufferObject.GL_ARRAY_BUFFER_ARB, block.vbo)
                qgl.qglBufferSubDataARB(
                    ARBVertexBufferObject.GL_ARRAY_BUFFER_ARB,
                    block.offset.toLong(),
                    size.toLong(),
                    data
                )
            } else {
                val position: ByteBuffer = block.virtMem!!.position(block.offset)
                for (i in 0 until size) {
                    position.put(data.get(i))
                }
            }
            return block
        }

        fun AllocFrameTemp(data: Array<idDrawVert>, size: Int): vertCache_s {
            return AllocFrameTemp(toByteBuffer((data)!!), size)
        }

        fun AllocFrameTemp(data: Array<idVec3>, size: Int): vertCache_s {
            return AllocFrameTemp(idVec3.toByteBuffer(data!!), size)
        }

        fun AllocFrameTemp(data: Array<idVec4>, size: Int): vertCache_s {
            return AllocFrameTemp(idVec4.toByteBuffer(data), size)
        }

        // notes that a buffer is used this frame, so it can't be purged
        // out from under the GPU
        fun Touch(block: vertCache_s?) {
            if (null == block) {
                Common.common.Error("idVertexCache Touch: NULL pointer")
            }
            if (block!!.tag == vertBlockTag_t.TAG_FREE) {
                Common.common.FatalError("idVertexCache Touch: freed pointer")
            }
            if (block.tag == vertBlockTag_t.TAG_TEMP) {
                Common.common.FatalError("idVertexCache Touch: temporary pointer")
            }
            block.frameUsed = currentFrame

            // move to the head of the LRU list
            block.next!!.prev = block.prev
            block.prev!!.next = block.next
            block.next = staticHeaders.next
            block.prev = staticHeaders
            staticHeaders.next!!.prev = block
            staticHeaders.next = block
        }

        // this block won't have to zero a buffer pointer when it is purged,
        // but it must still wait for the frames to pass, in case the GPU
        // is still referencing it
        fun Free(block: vertCache_s?) {
            if (null == block) {
                return
            }
            if (block.tag == vertBlockTag_t.TAG_FREE) {
                Common.common.FatalError("idVertexCache Free: freed pointer")
            }
            if (block.tag == vertBlockTag_t.TAG_TEMP) {
                Common.common.FatalError("idVertexCache Free: temporary pointer")
            }

            // this block still can't be purged until the frame count has expired,
            // but it won't need to clear a user pointer when it is
            block.user = null
            block.next!!.prev = block.prev
            block.prev!!.next = block.next
            block.next = deferredFreeList.next
            block.prev = deferredFreeList
            deferredFreeList.next!!.prev = block
            deferredFreeList.next = block
        }

        // updates the counter for determining which temp space to use
        // and which blocks can be purged
        // Also prints debugging info when enabled
        fun EndFrame() {
            // display debug information
            if (r_showVertexCache.GetBool()) {
                var staticUseCount = 0
                var staticUseSize = 0
                var block: vertCache_s? = staticHeaders.next
                while (block !== staticHeaders) {
                    if (block!!.frameUsed == currentFrame) {
                        staticUseCount++
                        staticUseSize += block.size
                    }
                    block = block.next
                }
                val frameOverflow: String = if (tempOverflow) "(OVERFLOW)" else ""
                Common.common.Printf(
                    "vertex dynamic:%d=%dk%s, static alloc:%d=%dk used:%d=%dk total:%d=%dk\n",
                    dynamicCountThisFrame, dynamicAllocThisFrame / 1024, frameOverflow,
                    staticCountThisFrame, staticAllocThisFrame / 1024,
                    staticUseCount, staticUseSize / 1024,
                    staticCountTotal, staticAllocTotal / 1024
                )
            }

            if (!virtualMemory) {
                // unbind vertex buffers so normal virtual memory will be used in case
                // r_useVertexBuffers / r_useIndexBuffers
                qgl.qglBindBufferARB(ARBVertexBufferObject.GL_ARRAY_BUFFER_ARB, 0)
                qgl.qglBindBufferARB(ARBVertexBufferObject.GL_ELEMENT_ARRAY_BUFFER_ARB, 0)
            }
            currentFrame = tr.frameCount
            listNum = currentFrame % NUM_VERTEX_FRAMES
            staticAllocThisFrame = 0
            staticCountThisFrame = 0
            dynamicAllocThisFrame = 0
            dynamicCountThisFrame = 0
            tempOverflow = false

            // free all the deferred free headers
            while (deferredFreeList.next !== deferredFreeList) {
                ActuallyFree(deferredFreeList.next)
            }

            // free all the frame temp headers
            val block: vertCache_s? = dynamicHeaders.next
            if (block !== dynamicHeaders) {
                block!!.prev = freeDynamicHeaders
                dynamicHeaders.prev!!.next = freeDynamicHeaders.next
                freeDynamicHeaders.next!!.prev = dynamicHeaders.prev
                freeDynamicHeaders.next = block
                dynamicHeaders.prev = dynamicHeaders
                dynamicHeaders.next = dynamicHeaders.prev
            }
        }

        // listVertexCache calls this
        fun List() {
            var numActive = 0
            var frameStatic = 0
            var totalStatic = 0
            var block: vertCache_s?
            block = staticHeaders.next
            while (block !== staticHeaders) {
                numActive++
                totalStatic += block!!.size
                if (block.frameUsed == currentFrame) {
                    frameStatic += block.size
                }
                block = block.next
            }
            var numFreeStaticHeaders = 0
            block = freeStaticHeaders.next
            while (block !== freeStaticHeaders) {
                numFreeStaticHeaders++
                block = block!!.next
            }
            var numFreeDynamicHeaders = 0
            block = freeDynamicHeaders.next
            while (block !== freeDynamicHeaders) {
                numFreeDynamicHeaders++
                block = block!!.next
            }
            Common.common.Printf("%d megs working set\n", r_vertexBufferMegs.GetInteger())
            Common.common.Printf("%d dynamic temp buffers of %dk\n", NUM_VERTEX_FRAMES, frameBytes / 1024)
            Common.common.Printf("%5d active static headers\n", numActive)
            Common.common.Printf("%5d free static headers\n", numFreeStaticHeaders)
            Common.common.Printf("%5d free dynamic headers\n", numFreeDynamicHeaders)
            if (!virtualMemory) {
                Common.common.Printf("Vertex cache is in ARB_vertex_buffer_object memory (FAST).\n")
            } else {
                Common.common.Printf("Vertex cache is in virtual memory (SLOW)\n")
            }
            if (r_useIndexBuffers!!.GetBool()) {
                Common.common.Printf("Index buffers are accelerated.\n")
            } else {
                Common.common.Printf("Index buffers are not used.\n")
            }
        }

        private fun ActuallyFree(block: vertCache_s?) {
            if (null == block) {
                Common.common.Error("idVertexCache Free: NULL pointer")
            }
            if (block!!.user != null) {
                // let the owner know we have purged it
                block.user = null
            }

            // temp blocks are in a shared space that won't be freed
            if (block.tag != vertBlockTag_t.TAG_TEMP) {
                staticAllocTotal -= block.size
                staticCountTotal--
                if (block.vbo != 0) {
                    // VBO will be reused, no need to free
                } else if (block.virtMem != null) {
                    block.virtMem = null
                }
            }
            block.tag = vertBlockTag_t.TAG_FREE // mark as free

            // unlink stick it back on the free list
            block.next!!.prev = block.prev
            block.prev!!.next = block.next

            // stick it on the front of the free list so it will be reused immediately
            block.next = freeStaticHeaders.next
            block.prev = freeStaticHeaders

            block.next!!.prev = block
            block.prev!!.next = block
        }

        companion object {
            private val r_showVertexCache: idCVar = idCVar("r_showVertexCache", "0", CVAR_INTEGER or CVAR_RENDERER, "")
            private val r_vertexBufferMegs: idCVar =
                idCVar("r_vertexBufferMegs", "32", CVAR_INTEGER or CVAR_RENDERER, "")
        }
    }
}

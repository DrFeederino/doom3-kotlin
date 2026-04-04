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

import neo.TempDump.TODO_Exception
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.*
import java.nio.ByteBuffer
import java.nio.DoubleBuffer
import java.nio.FloatBuffer
import java.nio.IntBuffer

/**
 * so yeah, it's easier to use this class as an interface. rather than refactor
 * all the qwgl stuff to gl and such.
 */
object qgl {
    val qGL_FALSE: Boolean = false
    val qGL_TRUE: Boolean = true
    private val GL_DEBUG: Boolean = false

    init {
        if (GL_DEBUG) qglEnable(GL43.GL_DEBUG_OUTPUT)
    }

    //
    //    // multitexture
    fun qglActiveTextureARB(texture: Int) {
        DEBUG_printName("glActiveTextureARB")
        ARBMultitexture.glActiveTextureARB(texture)
    }

    fun qglClientActiveTextureARB(texture: Int) {
        DEBUG_printName("glClientActiveTextureARB")
        ARBMultitexture.glClientActiveTextureARB(texture)
    }

    // ARB_vertex_buffer_object
    fun qglBindBufferARB(target: Int, buffer: Int) {
        DEBUG_printName("glBindBufferARB")
        ARBVertexBufferObject.glBindBufferARB(target, buffer)
    }

    //extern PFNGLDELETEBUFFERSARBPROC qglDeleteBuffersARB;
    fun qglGenBuffersARB(n: Int, buffers: Array<IntBuffer?>) {
        DEBUG_printName("glGenBuffersARB")
        ARBVertexBufferObject.glGenBuffersARB(BufferUtils.createIntBuffer(n).also { buffers[0] = it })
    }

    fun qglGenBuffersARB(): Int {
        DEBUG_printName("glGenBuffersARB")
        return ARBVertexBufferObject.glGenBuffersARB()
    }

    //extern PFNGLISBUFFERARBPROC qglIsBufferARB;
    fun qglBufferDataARB(target: Int, size: Int, data: ByteBuffer, usage: Int) {
        DEBUG_printName("glBufferDataARB")
        val slice = data.slice().limit(size)
        ARBVertexBufferObject.glBufferDataARB(target, slice, usage)
    }

    fun  /*PFNGLBUFFERSUBDATAARBPROC*/qglBufferSubDataARB(target: Int, offset: Long, size: Long, data: ByteBuffer) {
        DEBUG_printName("glBufferSubDataARB")
        ARBVertexBufferObject.glBufferSubDataARB(target, offset, data)
    }


    // NV_register_combiners
    fun qglCombinerParameterfvNV(pName: Int, params: FloatArray?) {
        throw UnsupportedOperationException()
    }

    //extern	void ( APIENTRY *qglCombinerParameterivNV )( GLenum pName, const GLint *params );
    fun qglCombinerParameteriNV(pName: Int, param: Int) {
        throw UnsupportedOperationException()
    }

    fun qglCombinerInputNV(stage: Int, portion: Int, variable: Int, input: Int, mapping: Int, componentUsage: Int) {
        throw UnsupportedOperationException()
    }

    fun qglCombinerOutputNV(
        stage: Int, portion: Int, abOutput: Int, cdOutput: Int, sumOutput: Int, scale: Int, bias: Int,
        abDotProduct: Boolean, cdDotProduct: Boolean, muxSum: Boolean
    ) {
        throw UnsupportedOperationException()
    }

    fun qglFinalCombinerInputNV(variable: Int, input: Int, mapping: Int, componentUsage: Int) {
        throw UnsupportedOperationException()
    }

    // 3D textures
    fun qglTexImage3D(
        GLenum1: Int,
        GLint1: Int,
        GLint2: Int,
        GLsizei1: Int,
        GLsizei2: Int,
        GLsizei3: Int,
        GLint4: Int,
        GLenum2: Int,
        GLenum3: Int,
        GLvoid: ByteBuffer?
    ) {
        DEBUG_printName("glTexImage3D")
        GL12.glTexImage3D(GLenum1, GLint1, GLint2, GLsizei1, GLsizei2, GLsizei3, GLint4, GLenum2, GLenum3, GLvoid)
    }

    //
    // shared texture palette
    fun qglColorTableEXT(target: Int, internalFormat: Int, width: Int, format: Int, type: Int, data: ByteArray?) {
        DEBUG_printName("glColorTableEXT")
        ARBImaging.glColorTable(target, internalFormat, width, format, type, ByteBuffer.wrap(data))
    }

    //// EXT_stencil_two_side
    //extern	PFNGLACTIVESTENCILFACEEXTPROC	qglActiveStencilFaceEXT;
    //
    //
    //// ATI_separate_stencil
    //extern	PFNGLSTENCILOPSEPARATEATIPROC		qglStencilOpSeparateATI;
    //extern	PFNGLSTENCILFUNCSEPARATEATIPROC		qglStencilFuncSeparateATI;
    //
    // ARB_texture_compression
    fun  /*PFNGLCOMPRESSEDTEXIMAGE2DARBPROC*/qglCompressedTexImage2DARB(
        target: Int, level: Int, internalformat: Int,
        width: Int, height: Int, border: Int, imageSize: Int, data: ByteBuffer?
    ) {
        DEBUG_printName("glCompressedTexImage2DARB")
        GL13.glCompressedTexImage2D(target, level, internalformat, width, height, border, data)
    }

    @Deprecated("")
    fun qglCompressedTexImage2DARB(
        target: Int,
        level: Int,
        internalformat: Int,
        width: Int,
        height: Int,
        border: Int,
        imageSize: Int,
        pData_buffer_offset: Long
    ) {
        GL13.glCompressedTexImage2D(
            target,
            level,
            internalformat,
            width,
            height,
            border,
            imageSize,
            pData_buffer_offset
        )
    }

    fun  /*PFNGLGETCOMPRESSEDTEXIMAGEARBPROC*/qglGetCompressedTexImageARB(target: Int, index: Int, img: ByteBuffer) {
        DEBUG_printName("glGetCompressedTexImageARB")
        ARBTextureCompression.glGetCompressedTexImageARB(target, index, img)
    }

    //
    // ARB_vertex_program / ARB_fragment_program
    @Deprecated("")
    fun  /*PFNGLVERTEXATTRIBPOINTERARBPROC*/qglVertexAttribPointerARB(
        index: Int,
        size: Int,
        type: Int,
        normalized: Boolean,
        stride: Int,
        pointer: FloatArray?
    ) {
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglVertexAttribPointerARB(index: Int, size: Int, type: Int, normalized: Boolean, stride: Int, pointer: Long) {
        DEBUG_printName("glVertexAttribPointerARB")
        ARBVertexShader.glVertexAttribPointerARB(index, size, type, normalized, stride, pointer)
    }

    fun  /*PFNGLENABLEVERTEXATTRIBARRAYARBPROC*/qglEnableVertexAttribArrayARB(index: Int) {
        DEBUG_printName("glEnableVertexAttribArrayARB")
        ARBVertexShader.glEnableVertexAttribArrayARB(index)
    }

    fun  /*PFNGLDISABLEVERTEXATTRIBARRAYARBPROC*/qglDisableVertexAttribArrayARB(index: Int) {
        DEBUG_printName("glDisableVertexAttribArrayARB")
        ARBVertexShader.glDisableVertexAttribArrayARB(index)
    }

    fun  /*PFNGLPROGRAMSTRINGARBPROC*/qglProgramStringARB(target: Int, format: Int, len: Int, string: ByteBuffer) {
        DEBUG_printName("glProgramStringARB")
        ARBVertexProgram.glProgramStringARB(target, format, string)
    }

    fun  /*PFNGLBINDPROGRAMARBPROC*/qglBindProgramARB(target: Int, program: Enum<*>) {
        DEBUG_printName("glBindProgramARB")
        qglBindProgramARB(target, program.ordinal)
    }

    fun  /*PFNGLBINDPROGRAMARBPROC*/qglBindProgramARB(target: Int, program: Int) {
        DEBUG_printName("glBindProgramARB")
        ARBVertexProgram.glBindProgramARB(target, program)
    }

    //extern PFNGLGENPROGRAMSARBPROC				qglGenProgramsARB;
    //
    fun  /*PFNGLPROGRAMENVPARAMETER4FVARBPROC*/qglProgramEnvParameter4fvARB(
        target: Int,
        index: Enum<*>,
        params: FloatBuffer
    ) {
        DEBUG_printName("glProgramEnvParameter4fvARB")
        ARBVertexProgram.glProgramEnvParameter4fvARB(target, index.ordinal, params)
    }

    //    @Deprecated
    fun  /*PFNGLPROGRAMENVPARAMETER4FVARBPROC*/qglProgramEnvParameter4fvARB(
        target: Int,
        index: Enum<*>,
        params: FloatArray
    ) {
        DEBUG_printName("glProgramEnvParameter4fvARB")
        qglProgramEnvParameter4fvARB(target, index.ordinal, params)
    }

    //    @Deprecated
    fun  /*PFNGLPROGRAMENVPARAMETER4FVARBPROC*/qglProgramEnvParameter4fvARB(
        target: Int,
        index: Int,
        params: FloatArray
    ) {
        DEBUG_printName("glProgramEnvParameter4fvARB")
        ARBVertexProgram.glProgramEnvParameter4fvARB(target, index, params)
    }

    fun  /*PFNGLPROGRAMENVPARAMETER4FVARBPROC*/qglProgramEnvParameter4fvARB(
        target: Int,
        index: Int,
        params: FloatBuffer
    ) {
        DEBUG_printName("glProgramEnvParameter4fvARB")
        ARBVertexProgram.glProgramEnvParameter4fvARB(target, index, params)
    }

    fun  /*PFNGLPROGRAMLOCALPARAMETER4FVARBPROC*/qglProgramLocalParameter4fvARB(
        target: Int,
        index: Int,
        params: FloatBuffer
    ) {
        DEBUG_printName("glProgramLocalParameter4fvARB")
        ARBVertexProgram.glProgramLocalParameter4fvARB(target, index, params)
    }

    // GL_EXT_depth_bounds_test
    fun  /*PFNGLDEPTHBOUNDSEXTPROC*/qglDepthBoundsEXT(zmin: Float, zmax: Float) {
        DEBUG_printName("glDepthBoundsEXT")
        EXTDepthBoundsTest.glDepthBoundsEXT(zmin.toDouble(), zmax.toDouble())
    }

    fun qglAccum(op: Int, value: Float) {
        DEBUG_printName("glAccum")
        GL43.glAccum(op, value)
    }

    fun qglAlphaFunc(func: Int, ref: Float) {
        DEBUG_printName("glAlphaFunc")
        GL43.glAlphaFunc(func, ref)
    }

    fun qglAreTexturesResident(n: Int, textures: IntBuffer, residences: ByteBuffer): Boolean {
        DEBUG_printName("glAreTexturesResident")
        return GL43.glAreTexturesResident(textures, residences)
    }

    fun qglArrayElement(i: Int) {
        DEBUG_printName("glArrayElement")
        GL43.glArrayElement(i)
    }

    fun qglBegin(mode: Int) {
        DEBUG_printName("glBegin")
        GL43.glBegin(mode)
    }

    fun qglBindTexture(target: Int, texture: Int) {
        DEBUG_printName("glBindTexture")
        GL43.glBindTexture(target, texture)
    }

    fun qglBitmap(
        width: Int,
        height: Int,
        xorig: Float,
        yorig: Float,
        xmove: Float,
        ymove: Float,
        bitmap: ByteBuffer?
    ) {
        DEBUG_printName("glBitmap")
        GL43.glBitmap(width, height, xorig, yorig, xmove, ymove, bitmap)
    }

    fun qglBlendFunc(sFactor: Int, dFactor: Int) {
        DEBUG_printName("glBlendFunc")
        GL43.glBlendFunc(sFactor, dFactor)
    }

    fun qglCallList(list: Int) {
        DEBUG_printName("glCallList")
        GL43.glCallList(list)
    }

    fun qglCallLists(n: Int, type: Int, lists: Any?) {
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglClear(mask: Int) {
        DEBUG_printName("glClear")
        GL43.glClear(mask)
    }

    fun qglClearAccum(red: Float, green: Float, blue: Float, alpha: Float) {
        DEBUG_printName("glClearAccum")
        GL43.glClearAccum(red, green, blue, alpha)
    }

    fun qglClearColor(red: Float, green: Float, blue: Float, alpha: Float) {
        DEBUG_printName("glClearColor")
        GL43.glClearColor(red, green, blue, alpha)
    }

    fun qglClearDepth(depth: Double) {
        DEBUG_printName("glClearDepth")
        GL43.glClearDepth(depth)
    }

    fun qglClearIndex(c: Float) {
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglClearStencil(s: Int) {
        DEBUG_printName("glClearStencil")
        GL43.glClearStencil(s)
    }

    fun qglClipPlane(plane: Int, equation: DoubleBuffer) {
        DEBUG_printName("glClipPlane")
        GL43.glClipPlane(plane, equation)
    }

    fun qglColor3b(red: Byte, green: Byte, blue: Byte) {
        DEBUG_printName("glColor3b")
        GL43.glColor3b(red, green, blue)
    }

    fun qglColor3bv(v: ByteArray?) {
        DEBUG_printName("glColor3bv")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglColor3d(red: Double, green: Double, blue: Double) {
        DEBUG_printName("glColor3d")
        GL43.glColor3d(red, green, blue)
    }

    fun qglColor3dv(v: DoubleArray?) {
        DEBUG_printName("glColor3dv")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglColor3f(red: Float, green: Float, blue: Float) {
        DEBUG_printName("glColor3f")
        GL43.glColor3f(red, green, blue)
    }

    fun qglColor3fv(v: FloatArray) {
        DEBUG_printName("glColor3fv")
        qglColor3f(v[0], v[1], v[2])
    }

    fun qglColor3i(red: Int, green: Int, blue: Int) {
        DEBUG_printName("glColor3i")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglColor3iv(v: IntArray?) {
        DEBUG_printName("glColor3iv")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglColor3s(red: Short, green: Short, blue: Short) {
        DEBUG_printName("glColor3s")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglColor3sv(v: ShortArray?) {
        DEBUG_printName("glColor3sv")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglColor3ub(red: Byte, green: Byte, blue: Byte) {
        DEBUG_printName("glColor3ub")
        GL43.glColor3ub(red, green, blue)
    }

    fun qglColor3ubv(v: ByteArray?) {
        DEBUG_printName("glColor3ubv")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglColor3ui(red: Int, green: Int, blue: Int) {
        DEBUG_printName("glColor3ui")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglColor3uiv(v: IntArray?) {
        DEBUG_printName("glColor3uiv")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglColor3us(red: Short, green: Short, blue: Short) {
        DEBUG_printName("glColor3us")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglColor3usv(v: ShortArray?) {
        DEBUG_printName("glColor3usv")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglColor4b(red: Byte, green: Byte, blue: Byte, alpha: Byte) {
        DEBUG_printName("glColor4b")
        GL43.glColor4b(red, green, blue, alpha)
    }

    fun qglColor4bv(v: ByteArray?) {
        DEBUG_printName("glColor4bv")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglColor4d(red: Double, green: Double, blue: Double, alpha: Double) {
        DEBUG_printName("glColor4d")
        GL43.glColor4d(red, green, blue, alpha)
    }

    fun qglColor4dv(v: FloatArray?) {
        DEBUG_printName("glColor4dv")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglColor4f(red: Float, green: Float, blue: Float, alpha: Float) {
        DEBUG_printName("glColor4f")
        GL43.glColor4f(red, green, blue, alpha)
    }

    fun qglColor4fv(v: FloatArray) {
        DEBUG_printName("glColor4fv")
        qglColor4f(v[0], v[1], v[2], v[3])
    }

    fun qglColor4i(red: Int, green: Int, blue: Int, alpha: Int) {
        DEBUG_printName("glColor4i")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglColor4iv(v: IntArray?) {
        DEBUG_printName("glColor4iv")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglColor4s(red: Short, green: Short, blue: Short, alpha: Short) {
        DEBUG_printName("glColor4s")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglColor4sv(v: ShortArray?) {
        DEBUG_printName("glColor4sv")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglColor4ub(red: Byte, green: Byte, blue: Byte, alpha: Byte) {
        DEBUG_printName("glColor4ub")
        GL43.glColor4ub(red, green, blue, alpha)
    }

    fun qglColor4ubv(v: ByteArray) {
        DEBUG_printName("glColor4ubv")
        GL43.glColor4ub(v[0], v[1], v[2], v[3])
    }

    fun qglColor4ui(red: Int, green: Int, blue: Int, alpha: Int) {
        DEBUG_printName("glColor4ui")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglColor4uiv(v: IntArray?) {
        DEBUG_printName("glColor4uiv")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglColor4usv(v: ShortArray) {
        DEBUG_printName("glColor4usv")
        GL43.glColor4usv(v)
    }

    fun qglColor4us(red: Short, green: Short, blue: Short, alpha: Short) {
        DEBUG_printName("glColor4us")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglColorMask(red: Int, green: Int, blue: Int, alpha: Int) {
        qglColorMask(red != 0, green != 0, blue != 0, alpha != 0)
    }

    fun qglColorMask(red: Boolean, green: Boolean, blue: Boolean, alpha: Boolean) {
        DEBUG_printName("glColorMask")
        GL43.glColorMask(red, green, blue, alpha)
    }

    fun qglColorMaterial(face: Int, mode: Int) {
        DEBUG_printName("glColorMaterial")
        GL43.glColorMaterial(face, mode)
    }

    fun qglColorPointer(size: Int, type: Int, stride: Int, pointer: Long) {
        DEBUG_printName("glColorPointer")
        GL43.glColorPointer(size, type, stride, pointer)
    }

    @Deprecated("")
    fun qglColorPointer(size: Int, type: Int, stride: Int, pointer: Any?) {
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglCopyPixels(x: Int, y: Int, width: Int, height: Int, type: Int) {
        DEBUG_printName("glCopyPixels")
        GL43.glCopyPixels(x, y, width, height, type)
    }

    fun qglCopyTexImage1D(target: Int, level: Int, internalFormat: Int, x: Int, y: Int, width: Int, border: Int) {
        DEBUG_printName("glCopyTexImage1D")
        GL43.glCopyTexImage1D(target, level, internalFormat, x, y, width, border)
    }

    fun qglCopyTexImage2D(
        target: Int,
        level: Int,
        internalFormat: Int,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        border: Int
    ) {
        DEBUG_printName("glCopyTexImage2D")
        GL43.glCopyTexImage2D(target, level, internalFormat, x, y, width, height, border)
    }

    fun qglCopyTexSubImage1D(target: Int, level: Int, xoffset: Int, x: Int, y: Int, width: Int) {
        DEBUG_printName("glCopyTexSubImage1D")
        GL43.glCopyTexSubImage1D(target, level, xoffset, x, y, width)
    }

    fun qglCopyTexSubImage2D(
        target: Int,
        level: Int,
        xoffset: Int,
        yoffset: Int,
        x: Int,
        y: Int,
        width: Int,
        height: Int
    ) {
        DEBUG_printName("glCopyTexSubImage2D")
        GL43.glCopyTexSubImage2D(target, level, xoffset, yoffset, x, y, width, height)
    }

    fun qglCullFace(mode: Int) {
        DEBUG_printName("glCullFace")
        GL43.glCullFace(mode)
    }

    fun qglDeleteLists(list: Int, range: Int) {
        DEBUG_printName("glDeleteLists")
        GL43.glDeleteLists(list, range)
    }

    fun qglDeleteTextures(n: Int, texture: Int) {
        DEBUG_printName("glDeleteTextures")
        GL43.glDeleteTextures(texture)
    }

    fun qglDeleteTextures(n: Int, textures: IntArray?) {
        DEBUG_printName("glDeleteTextures")
        //        GL43.glDeleteTextures();
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglDepthFunc(func: Int) {
        DEBUG_printName("glDepthFunc")
        GL43.glDepthFunc(func)
    }

    fun qglDepthMask(flag: Boolean) {
        DEBUG_printName("glDepthMask")
        GL43.glDepthMask(flag)
    }

    fun qglDepthRange(zNear: Float, zFar: Float) {
        DEBUG_printName("glDepthRange")
        GL43.glDepthRange(zNear.toDouble(), zFar.toDouble())
    }

    fun qglDisable(cap: Int) {
        DEBUG_printName("glDisable")
        GL43.glDisable(cap)
    }

    fun qglDisableClientState(array: Int) {
        DEBUG_printName("glDisableClientState")
        GL43.glDisableClientState(array)
    }

    fun qglDrawArrays(mode: Int, first: Int, count: Int) {
        DEBUG_printName("glDrawArrays")
        GL43.glDrawArrays(mode, first, count)
    }

    fun qglDrawBuffer(mode: Int) {
        DEBUG_printName("glDrawBuffer")
        GL43.glDrawBuffer(mode)
    }

    fun qglDrawElements(mode: Int, count: Int, type: Int, indices: ByteBuffer) {
        DEBUG_printName("glDrawElements1")
        val bytesPerIndex = when (type) {
            GL11.GL_UNSIGNED_INT -> 4
            GL11.GL_UNSIGNED_SHORT -> 2
            else -> 1
        }
        val limited = indices.slice().limit(count * bytesPerIndex)
        GL43.glDrawElements(mode, type, limited)
    }

    fun qglDrawElements(mode: Int, count: Int, type: Int, indices: Long) {
        DEBUG_printName("glDrawElements_VBO")
        GL43.glDrawElements(mode, count, type, indices)
    }

    fun qglDrawElements(mode: Int, count: Int, type: Int, indices: IntArray?) {
        DEBUG_printName("glDrawElements2")
        GL43.glDrawElements(mode, wrap(indices!!).position(count).flip())
    }

    fun qglDrawPixels(width: Int, height: Int, format: Int, type: Int, pixels: ByteBuffer) {
        DEBUG_printName("glDrawPixels")
        GL43.glDrawPixels(width, height, format, type, pixels)
    }

    fun qglDrawPixels(width: Int, height: Int, format: Int, type: Int, pixels: Array<Array<ByteArray?>?>?) {
        DEBUG_printName("glDrawPixels")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglEdgeFlag(flag: Boolean) {
        DEBUG_printName("glEdgeFlag")
        GL43.glEdgeFlag(flag)
    }

    fun qglEdgeFlagPointer(stride: Int, pointer: Any?) {
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglEdgeFlagv(flag: Boolean) {
        DEBUG_printName("glEdgeFlagv")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglEnable(cap: Int) {
        DEBUG_printName("glEnable")
        GL43.glEnable(cap)
    }

    fun qglEnableClientState(array: Int) {
        DEBUG_printName("glEnableClientState")
        GL43.glEnableClientState(array)
    }

    fun qglEnd() {
        DEBUG_printName("glEnd")
        GL43.glEnd()
    }

    fun qglEndList() {
        DEBUG_printName("glEndList")
        GL43.glEndList()
    }

    fun qglEvalCoord1d(u: Double) {
        DEBUG_printName("glEvalCoord1d")
        GL43.glEvalCoord1d(u)
    }

    fun qglEvalCoord1dv(u: FloatArray?) {
        DEBUG_printName("glEvalCoord1dv")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglEvalCoord1f(u: Float) {
        DEBUG_printName("glEvalCoord1f")
        GL43.glEvalCoord1f(u)
    }

    fun qglEvalCoord1fv(u: FloatArray?) {
        DEBUG_printName("glEvalCoord1fv")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglEvalCoord2d(u: Double, v: Double) {
        DEBUG_printName("glEvalCoord2d")
        GL43.glEvalCoord2d(u, v)
    }

    fun qglEvalCoord2dv(u: DoubleArray?) {
        DEBUG_printName("glEvalCoord2dv")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglEvalCoord2f(u: Float, v: Float) {
        DEBUG_printName("glEvalCoord2f")
        GL43.glEvalCoord2f(u, v)
    }

    fun qglEvalCoord2fv(u: FloatArray?) {
        DEBUG_printName("glEvalCoord2fv")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglEvalMesh1(mode: Int, i1: Int, i2: Int) {
        DEBUG_printName("glEvalMesh1")
        GL43.glEvalMesh1(mode, i1, i2)
    }

    fun qglEvalMesh2(mode: Int, i1: Int, i2: Int, j1: Int, j2: Int) {
        DEBUG_printName("glEvalMesh2")
        GL43.glEvalMesh2(mode, i1, i2, j1, j2)
    }

    fun qglEvalPoint1(i: Int) {
        DEBUG_printName("glEvalPoint1")
        GL43.glEvalPoint1(i)
    }

    fun qglEvalPoint2(i: Int, j: Int) {
        DEBUG_printName("glEvalPoint2")
        GL43.glEvalPoint2(i, j)
    }

    fun qglFeedbackBuffer(size: Int, type: Int, buffer: FloatBuffer) {
        DEBUG_printName("glFeedbackBuffer")
        GL43.glFeedbackBuffer(type, buffer)
    }

    fun qglFinish() {
        DEBUG_printName("glFinish")
        GL43.glFinish()
    }

    fun qglFlush() {
        DEBUG_printName("glFlush")
        GL43.glFlush()
    }

    fun qglFogf(pName: Int, param: Float) {
        DEBUG_printName("glFogf")
        GL43.glFogf(pName, param)
    }

    fun qglFogfv(pName: Int, params: FloatArray?) {
        DEBUG_printName("glFogfv")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglFogi(pName: Int, param: Int) {
        DEBUG_printName("glFogi")
        GL43.glFogi(pName, param)
    }

    fun qglFogiv(pName: Int, params: IntArray?) {
        DEBUG_printName("glFogiv")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglFrontFace(mode: Int) {
        DEBUG_printName("glFrontFace")
        GL43.glFrontFace(mode)
    }

    fun qglFrustum(left: Double, right: Double, bottom: Double, top: Double, zNear: Double, zFar: Double) {
        DEBUG_printName("glFrustum")
        GL43.glFrustum(left, right, bottom, top, zNear, zFar)
    }

    fun qglGenLists(range: Enum<*>): Int {
        return qglGenLists(range.ordinal)
    }

    fun qglGenLists(range: Int): Int {
        DEBUG_printName("glGenLists")
        return GL43.glGenLists(range)
    }

    fun qglGenTextures(): Int {
        DEBUG_printName("glGenTextures")
        return GL43.glGenTextures()
    }

    fun qglGenTextures(n: Int, textures: IntArray?) {
        DEBUG_printName("glGenTextures")
        GL43.glGenTextures()
    }

    fun qglGetBooleanv(pName: Int, params: BooleanArray?) {
        DEBUG_printName("glGetBooleanv")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglGetClipPlane(plane: Int, equation: DoubleBuffer) {
        DEBUG_printName("glGetClipPlane")
        GL43.glGetClipPlane(plane, equation)
    }

    fun qglGetFloatv(pName: Int, params: FloatArray) {
        DEBUG_printName("glGetFloatv")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglGetError(): Int { //DEBUG_printName("glGetError");
        checkGLError()
        return GL43.glGetError()
    }

    fun qglGetFloatv(pName: Int, params: FloatBuffer) {
        DEBUG_printName("glGetFloatv")
        GL43.glGetFloatv(pName, params)
    }

    fun qglGetInteger(pName: Int): Int {
        DEBUG_printName("glGetInteger")
        return GL43.glGetInteger(pName)
    }

    fun qglGetIntegerv(pName: Int, params: IntBuffer) {
        DEBUG_printName("glGetIntegerv")
        GL43.glGetIntegerv(pName, params)
    }

    fun qglGetLightfv(light: Int, pName: Int, params: FloatArray?) {
        DEBUG_printName("glGetLightfv")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglGetLightiv(light: Int, pName: Int, params: IntArray?) {
        DEBUG_printName("glGetLightiv")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglGetMapdv(target: Int, query: Int, v: FloatArray?) {
        DEBUG_printName("glGetMapdv")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglGetMapfv(target: Int, query: Int, v: FloatArray?) {
        DEBUG_printName("glGetMapfv")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglGetMapiv(target: Int, query: Int, v: IntArray?) {
        DEBUG_printName("glGetMapiv")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglGetMaterialfv(face: Int, pName: Int, params: FloatArray?) {
        DEBUG_printName("glGetMaterialfv")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglGetMaterialiv(face: Int, pName: Int, params: IntArray?) {
        DEBUG_printName("glGetMaterialiv")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglGetPixelMapfv(map: Int, values: FloatArray?) {
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglGetPixelMapuiv(map: Int, values: IntArray?) {
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglGetPixelMapusv(map: Int, values: ShortArray?) {
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglGetPointerv(pName: Int, params: Array<Any?>?) {
        DEBUG_printName("glGetPointerv")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglGetPolygonStipple(mask: Byte) {
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglGetString(name: Int): String? {
        DEBUG_printName("glGetString")
        return GL43.glGetString(name)
    }

    fun qglGetStringi(name: Int, index: Int): String? {
        DEBUG_printName("glGetStringi")
        return GL30.glGetStringi(name, index)
    }

    fun qglGetTexEnvfv(target: Int, pName: Int, params: FloatArray?) {
        DEBUG_printName("glGetTexEnvfv")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglGetTexEnviv(target: Int, pName: Int, params: IntArray?) {
        DEBUG_printName("glGetTexEnviv")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglGetTexGendv(coord: Int, pName: Int, params: FloatArray?) {
        DEBUG_printName("glGetTexGendv")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglGetTexGenfv(coord: Int, pName: Int, params: FloatArray?) {
        DEBUG_printName("glGetTexGenfv")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglGetTexGeniv(coord: Int, pName: Int, params: IntArray?) {
        DEBUG_printName("glGetTexGeniv")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglGetTexImage(target: Int, level: Int, format: Int, type: Int, pixels: ByteBuffer) {
        DEBUG_printName("glGetTexImage")
        GL43.glGetTexImage(target, level, format, type, pixels)
    }

    fun qglGetTexLevelParameterfv(target: Int, level: Int, pName: Int, params: FloatArray?) {
        DEBUG_printName("glGetTexLevelParameterfv")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglGetTexLevelParameteriv(target: Int, level: Int, pName: Int, params: IntArray?) {
        DEBUG_printName("glGetTexLevelParameteriv")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglGetTexParameterfv(target: Int, pName: Int, params: FloatArray?) {
        DEBUG_printName("glGetTexParameterfv")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglGetTexParameteriv(target: Int, pName: Int, params: IntArray?) {
        DEBUG_printName("glGetTexParameteriv")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglHint(target: Int, mode: Int) {
        DEBUG_printName("glHint")
        GL43.glHint(target, mode)
    }

    fun qglIndexMask(mask: Int) {
        DEBUG_printName("glIndexMask")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglIndexPointer(type: Int, stride: Int, pointer: Any?) {
        DEBUG_printName("glIndexPointer")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglIndexd(c: Float) {
        DEBUG_printName("glIndexd")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglIndexdv(c: FloatArray?) {
        DEBUG_printName("glIndexdv")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglIndexf(c: Float) {
        DEBUG_printName("glIndexf")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglIndexfv(c: FloatArray?) {
        DEBUG_printName("glIndexfv")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglIndexi(c: Int) {
        DEBUG_printName("glIndexi")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglIndexiv(c: IntArray?) {
        DEBUG_printName("glIndexiv")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglIndexs(c: Short) {
        DEBUG_printName("glIndexs")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglIndexsv(c: ShortArray?) {
        DEBUG_printName("glIndexsv")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglIndexub(c: Byte) {
        DEBUG_printName("glIndexub")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglIndexubv(c: ByteArray?) {
        DEBUG_printName("glIndexubv")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglInitNames() {
        DEBUG_printName("glInitNames")
        GL43.glInitNames()
    }

    fun qglInterleavedArrays(format: Int, stride: Int, pointer: ByteBuffer) {
        DEBUG_printName("glInterleavedArrays")
        GL43.glInterleavedArrays(format, stride, pointer)
    }

    fun qglIsEnabled(cap: Int): Boolean {
        DEBUG_printName("glIsEnabled")
        return GL43.glIsEnabled(cap)
    }

    fun qglIsList(list: Int): Boolean {
        DEBUG_printName("glIsList")
        return GL43.glIsList(list)
    }

    fun qglIsTexture(texture: Int): Boolean {
        DEBUG_printName("glIsTexture")
        return GL43.glIsTexture(texture)
    }

    fun qglLightModelf(pName: Int, param: Float) {
        DEBUG_printName("glLightModelf")
        GL43.glLightModelf(pName, param)
    }

    fun qglLightModelfv(pName: Int, params: FloatBuffer) {
        DEBUG_printName("glLightModelfv")
        GL43.glLightModelfv(pName, params)
    }

    fun qglLightModeli(pName: Int, param: Int) {
        DEBUG_printName("glLightModeli")
        GL43.glLightModeli(pName, param)
    }

    fun qglLightModeliv(pName: Int, params: IntBuffer) {
        DEBUG_printName("glLightModeliv")
        GL43.glLightModeliv(pName, params)
    }

    fun qglLightf(light: Int, pName: Int, param: Float) {
        DEBUG_printName("glLightf")
        GL43.glLightf(light, pName, param)
    }

    fun qglLightfv(light: Int, pName: Int, params: FloatBuffer) {
        DEBUG_printName("glLightfv")
        GL43.glLightfv(light, pName, params)
    }

    fun qglLighti(light: Int, pName: Int, param: Int) {
        DEBUG_printName("glLighti")
        GL43.glLighti(light, pName, param)
    }

    fun qglLightiv(light: Int, pName: Int, params: IntBuffer) {
        DEBUG_printName("glLightiv")
        GL43.glLightiv(light, pName, params)
    }

    fun qglLineStipple(factor: Int, pattern: Short) {
        DEBUG_printName("glLineStipple")
        GL43.glLineStipple(factor, pattern)
    }

    fun qglLineWidth(width: Float) {
        DEBUG_printName("glLineWidth")
        GL43.glLineWidth(width)
    }

    fun qglListBase(base: Int) {
        DEBUG_printName("glListBase")
        GL43.glListBase(base)
    }

    fun qglLoadIdentity() {
        DEBUG_printName("glLoadIdentity")
        GL43.glLoadIdentity()
    }

    fun qglLoadMatrixd(m: DoubleBuffer) {
        DEBUG_printName("glLoadMatrixd")
        GL43.glLoadMatrixd(m)
    }

    fun qglLoadMatrixf(m: FloatArray) {
        DEBUG_printName("glLoadMatrixf")
        GL43.glLoadMatrixf(m)
    }

    fun qglLoadName(name: Int) {
        DEBUG_printName("glLoadName")
        GL43.glLoadName(name)
    }

    fun qglLogicOp(opcode: Int) {
        DEBUG_printName("glLogicOp")
        GL43.glLogicOp(opcode)
    }

    fun qglMap1d(target: Int, u1: Double, u2: Double, stride: Int, order: Int, points: DoubleBuffer) {
        DEBUG_printName("glMap1d")
        GL43.glMap1d(target, u1, u2, stride, order, points)
    }

    fun qglMap1f(target: Int, u1: Float, u2: Float, stride: Int, order: Int, points: FloatBuffer) {
        DEBUG_printName("glMap1f")
        GL43.glMap1f(target, u1, u2, stride, order, points)
    }

    fun qglMap2d(
        target: Int,
        u1: Double,
        u2: Double,
        ustride: Int,
        uorder: Int,
        v1: Double,
        v2: Double,
        vstride: Int,
        vorder: Int,
        points: DoubleBuffer
    ) {
        DEBUG_printName("glMap2d")
        GL43.glMap2d(target, u1, u2, ustride, uorder, v1, v2, vstride, vorder, points)
    }

    fun qglMap2f(
        target: Int,
        u1: Float,
        u2: Float,
        ustride: Int,
        uorder: Int,
        v1: Float,
        v2: Float,
        vstride: Int,
        vorder: Int,
        points: FloatBuffer
    ) {
        DEBUG_printName("glMap2f")
        GL43.glMap2f(target, u1, u2, ustride, uorder, v1, v2, vstride, vorder, points)
    }

    fun qglMapGrid1d(un: Int, u1: Double, u2: Double) {
        DEBUG_printName("glMapGrid1d")
        GL43.glMapGrid1d(un, u1, u2)
    }

    fun qglMapGrid1f(un: Int, u1: Float, u2: Float) {
        DEBUG_printName("glMapGrid1f")
        GL43.glMapGrid1f(un, u1, u2)
    }

    fun qglMapGrid2d(un: Int, u1: Double, u2: Double, vn: Int, v1: Double, v2: Double) {
        DEBUG_printName("glMapGrid2d")
        GL43.glMapGrid2d(un, u1, u2, vn, v1, v2)
    }

    fun qglMapGrid2f(un: Int, u1: Float, u2: Float, vn: Int, v1: Float, v2: Float) {
        DEBUG_printName("glMapGrid2f")
        GL43.glMapGrid2f(un, u1, u2, vn, v1, v2)
    }

    fun qglMaterialf(face: Int, pName: Int, param: Float) {
        DEBUG_printName("glMaterialf")
        GL43.glMaterialf(face, pName, param)
    }

    fun qglMaterialfv(face: Int, pName: Int, params: FloatArray?) {
        DEBUG_printName("glMaterialfv")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglMateriali(face: Int, pName: Int, param: Int) {
        DEBUG_printName("glMateriali")
        GL43.glMateriali(face, pName, param)
    }

    fun qglMaterialiv(face: Int, pName: Int, params: IntArray?) {
        DEBUG_printName("glMaterialiv")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglMatrixMode(mode: Int) {
        DEBUG_printName("glMatrixMode")
        GL43.glMatrixMode(mode)
    }

    fun qglMultMatrixd(m: FloatArray?) {
        DEBUG_printName("glMultMatrixd")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglMultMatrixf(m: FloatArray?) {
        DEBUG_printName("glMultMatrixf")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglNewList(list: Int, mode: Int) {
        DEBUG_printName("glNewList")
        GL43.glNewList(list, mode)
    }

    fun qglNormal3b(nx: Byte, ny: Byte, nz: Byte) {
        DEBUG_printName("glNormal3b")
        GL43.glNormal3b(nx, ny, nz)
    }

    fun qglNormal3bv(v: ByteArray?) {
        DEBUG_printName("glNormal3bv")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglNormal3d(nx: Double, ny: Double, nz: Double) {
        DEBUG_printName("glNormal3d")
        GL43.glNormal3d(nx, ny, nz)
    }

    fun qglNormal3dv(v: DoubleArray?) {
        DEBUG_printName("glNormal3dv")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglNormal3f(nx: Float, ny: Float, nz: Float) {
        DEBUG_printName("glNormal3f")
        GL43.glNormal3f(nx, ny, nz)
    }

    fun qglNormal3fv(v: FloatArray?) {
        DEBUG_printName("glNormal3fv")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglNormal3i(nx: Int, ny: Int, nz: Int) {
        DEBUG_printName("glNormal3i")
        GL43.glNormal3i(nx, ny, nz)
    }

    fun qglNormal3iv(v: IntArray?) {
        DEBUG_printName("glNormal3iv")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglNormal3s(nx: Short, ny: Short, nz: Short) {
        DEBUG_printName("glNormal3s")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglNormal3sv(v: ShortArray?) {
        DEBUG_printName("glNormal3sv")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglNormalPointer(type: Int, stride: Int, pointer: Long) {
        DEBUG_printName("glNormalPointer")
        GL43.glNormalPointer(type, stride, pointer)
    }

    fun qglOrtho(left: Double, right: Double, bottom: Double, top: Double, zNear: Double, zFar: Double) {
        DEBUG_printName("glOrtho")
        GL43.glOrtho(left, right, bottom, top, zNear, zFar)
    }

    fun qglPassThrough(token: Float) {
        DEBUG_printName("glPassThrough")
        GL43.glPassThrough(token)
    }

    fun qglPixelMapfv(map: Int, mapsize: Int, values: FloatArray?) {
        DEBUG_printName("glPixelMapfv")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglPixelMapuiv(map: Int, mapsize: Int, values: IntArray?) {
        DEBUG_printName("glPixelMapuiv")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglPixelMapusv(map: Int, mapsize: Int, values: ShortArray?) {
        DEBUG_printName("glPixelMapusv")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglPixelStoref(pName: Int, param: Float) {
        DEBUG_printName("glPixelStoref")
        GL43.glPixelStoref(pName, param)
    }

    fun qglPixelStorei(pName: Int, param: Int) {
        DEBUG_printName("glPixelStorei")
        GL43.glPixelStorei(pName, param)
    }

    fun qglPixelTransferf(pName: Int, param: Float) {
        DEBUG_printName("glPixelTransferf")
        GL43.glPixelTransferf(pName, param)
    }

    fun qglPixelTransferi(pName: Int, param: Int) {
        DEBUG_printName("glPixelTransferi")
        GL43.glPixelTransferi(pName, param)
    }

    fun qglPixelZoom(xfactor: Float, yfactor: Float) {
        DEBUG_printName("glPixelZoom")
        GL43.glPixelZoom(xfactor, yfactor)
    }

    fun qglPointSize(size: Float) {
        DEBUG_printName("glPointSize")
        GL43.glPointSize(size)
    }

    fun qglPolygonMode(face: Int, mode: Int) {
        DEBUG_printName("glPolygonMode")
        GL43.glPolygonMode(face, mode)
    }

    fun qglPolygonOffset(factor: Float, units: Float) {
        DEBUG_printName("glPolygonOffset")
        GL43.glPolygonOffset(factor, units)
    }

    fun qglPolygonStipple(mask: ByteBuffer) {
        DEBUG_printName("glPolygonStipple")
        GL43.glPolygonStipple(mask)
    }

    fun qglPopAttrib() {
        DEBUG_printName("glPopAttrib")
        GL43.glPopAttrib()
    }

    fun qglPopClientAttrib() {
        DEBUG_printName("glPopClientAttrib")
        GL43.glPopClientAttrib()
    }

    fun qglPopMatrix() {
        DEBUG_printName("glPopMatrix")
        GL43.glPopMatrix()
    }

    fun qglPopName() {
        DEBUG_printName("glPopName")
        GL43.glPopName()
    }

    fun qglPrioritizeTextures(n: Int, textures: Int, priorities: Float) {
        DEBUG_printName("glPrioritizeTextures")
        throw TODO_Exception()
    }

    fun qglPrioritizeTextures(n: Int, textures: IntBuffer, priorities: FloatBuffer) {
        DEBUG_printName("glPrioritizeTextures")
        GL43.glPrioritizeTextures(textures, priorities)
    }

    fun qglPushAttrib(mask: Int) {
        DEBUG_printName("glPushAttrib")
        GL43.glPushAttrib(mask)
    }

    fun qglPushClientAttrib(mask: Int) {
        DEBUG_printName("glPushClientAttrib")
        GL43.glPushClientAttrib(mask)
    }

    fun qglPushMatrix() {
        DEBUG_printName("glPushMatrix")
        GL43.glPushMatrix()
    }

    fun qglPushName(name: Int) {
        DEBUG_printName("glPushName")
        GL43.glPushName(name)
    }

    fun qglRasterPos2d(x: Double, y: Double) {
        DEBUG_printName("glRasterPos2d")
        GL43.glRasterPos2d(x, y)
    }

    fun qglRasterPos2dv(v: DoubleArray) {
        DEBUG_printName("glRasterPos2dv")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglRasterPos2f(x: Float, y: Float) {
        DEBUG_printName("glRasterPos2f")
        GL43.glRasterPos2f(x, y)
    }

    fun qglRasterPos2fv(v: FloatArray) {
        DEBUG_printName("glRasterPos2fv")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglRasterPos2i(x: Int, y: Int) {
        DEBUG_printName("glRasterPos2i")
        GL43.glRasterPos2i(x, y)
    }

    fun qglRasterPos2iv(v: IntArray) {
        DEBUG_printName("glRasterPos2iv")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglRasterPos2s(x: Short, y: Short) {
        DEBUG_printName("glRasterPos2s")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglRasterPos2sv(v: ShortArray) {
        DEBUG_printName("glRasterPos2sv")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglRasterPos3d(x: Double, y: Double, z: Double) {
        DEBUG_printName("glRasterPos3d")
        GL43.glRasterPos3d(x, y, z)
    }

    fun qglRasterPos3dv(v: DoubleArray?) {
        DEBUG_printName("glRasterPos3dv")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglRasterPos3f(x: Float, y: Float, z: Float) {
        DEBUG_printName("glRasterPos3f")
        GL43.glRasterPos3f(x, y, z)
    }

    fun qglRasterPos3fv(v: FloatArray?) {
        DEBUG_printName("glRasterPos3fv")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglRasterPos3i(x: Int, y: Int, z: Int) {
        DEBUG_printName("glRasterPos3i")
        GL43.glRasterPos3i(x, y, z)
    }

    fun qglRasterPos3iv(v: IntArray?) {
        DEBUG_printName("glRasterPos3iv")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglRasterPos3s(x: Short, y: Short, z: Short) {
        DEBUG_printName("glRasterPos3s")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglRasterPos3sv(v: ShortArray?) {
        DEBUG_printName("glRasterPos3sv")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglRasterPos4d(x: Double, y: Double, z: Double, w: Double) {
        DEBUG_printName("glRasterPos4d")
        GL43.glRasterPos4d(x, y, z, w)
    }

    fun qglRasterPos4dv(v: DoubleArray?) {
        DEBUG_printName("glRasterPos4dv")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglRasterPos4f(x: Float, y: Float, z: Float, w: Float) {
        DEBUG_printName("glRasterPos4f")
        GL43.glRasterPos4f(x, y, z, w)
    }

    fun qglRasterPos4fv(v: FloatArray?) {
        DEBUG_printName("glRasterPos4fv")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglRasterPos4i(x: Int, y: Int, z: Int, w: Int) {
        DEBUG_printName("glRasterPos4i")
        GL43.glRasterPos4i(x, y, z, w)
    }

    fun qglRasterPos4iv(v: IntArray?) {
        DEBUG_printName("glRasterPos4iv")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglRasterPos4s(x: Short, y: Short, z: Short, w: Short) {
        DEBUG_printName("glRasterPos4s")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglRasterPos4sv(v: ShortArray?) {
        DEBUG_printName("glRasterPos4sv")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglReadBuffer(mode: Int) {
        DEBUG_printName("glReadBuffer")
        GL43.glReadBuffer(mode)
    }

    fun qglReadPixels(x: Int, y: Int, width: Int, height: Int, format: Int, type: Int, pixels: ByteBuffer) {
        DEBUG_printName("glReadPixels")
        GL43.glReadPixels(x, y, width, height, format, type, pixels)
    }

    fun qglRectd(x1: Double, y1: Double, x2: Double, y2: Double) {
        DEBUG_printName("glRectd")
        GL43.glRectd(x1, y1, x2, y2)
    }

    fun qglRectdv(v1: FloatArray?, v2: FloatArray?) {
        DEBUG_printName("glRectdv")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglRectf(x1: Float, y1: Float, x2: Float, y2: Float) {
        DEBUG_printName("glRectf")
        GL43.glRectf(x1, y1, x2, y2)
    }

    fun qglRectfv(v1: FloatArray?, v2: FloatArray?) {
        DEBUG_printName("glRectfv")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglRecti(x1: Int, y1: Int, x2: Int, y2: Int) {
        DEBUG_printName("glRecti")
        GL43.glRecti(x1, y1, x2, y2)
    }

    fun qglRectiv(v1: IntArray?, v2: IntArray?) {
        DEBUG_printName("glRectiv")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglRects(x1: Short, y1: Short, x2: Short, y2: Short) {
        DEBUG_printName("glRects")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglRectsv(v1: ShortArray?, v2: ShortArray?) {
        DEBUG_printName("glRectsv")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglRenderMode(mode: Int): Int {
        DEBUG_printName("glRenderMode")
        return GL43.glRenderMode(mode)
    }

    fun qglRotated(angle: Double, x: Double, y: Double, z: Double) {
        DEBUG_printName("glRotated")
        GL43.glRotated(angle, x, y, z)
    }

    fun qglRotatef(angle: Float, x: Float, y: Float, z: Float) {
        DEBUG_printName("glRotatef")
        GL43.glRotatef(angle, x, y, z)
    }

    fun qglScaled(x: Double, y: Double, z: Double) {
        DEBUG_printName("glScaled")
        GL43.glScaled(x, y, z)
    }

    fun qglScalef(x: Float, y: Float, z: Float) {
        DEBUG_printName("glScalef")
        GL43.glScalef(x, y, z)
    }

    fun qglScissor(x: Int, y: Int, width: Int, height: Int) {
        DEBUG_printName("glScissor")
        GL43.glScissor(x, y, width, height)
    }

    fun qglSelectBuffer(size: Int, buffer: IntBuffer) {
        DEBUG_printName("glSelectBuffer")
        GL43.glSelectBuffer(buffer)
    }

    fun qglShadeModel(mode: Int) {
        DEBUG_printName("glShadeModel")
        GL43.glShadeModel(mode)
    }

    fun qglStencilFunc(func: Int, ref: Int, mask: Int) {
        DEBUG_printName("glStencilFunc")
        GL43.glStencilFunc(func, ref, mask)
    }

    fun qglStencilMask(mask: Int) {
        DEBUG_printName("glStencilMask")
        GL43.glStencilMask(mask)
    }

    fun qglStencilOp(fail: Int, zfail: Int, zpass: Int) {
        DEBUG_printName("glStencilOp")
        GL43.glStencilOp(fail, zfail, zpass)
    }

    fun qglTexCoord1d(s: Double) {
        DEBUG_printName("glTexCoord1d")
        GL43.glTexCoord1d(s)
    }

    fun qglTexCoord1dv(v: DoubleArray?) {
        DEBUG_printName("glTexCoord1dv")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglTexCoord1f(s: Float) {
        DEBUG_printName("glTexCoord1f")
        GL43.glTexCoord1f(s)
    }

    fun qglTexCoord1fv(v: FloatArray?) {
        DEBUG_printName("glTexCoord1fv")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglTexCoord1i(s: Int) {
        DEBUG_printName("glTexCoord1i")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglTexCoord1iv(v: IntArray?) {
        DEBUG_printName("glTexCoord1iv")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglTexCoord1s(s: Short) {
        DEBUG_printName("glTexCoord1s")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglTexCoord1sv(v: ShortArray?) {
        DEBUG_printName("glTexCoord1sv")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglTexCoord2d(s: Double, t: Double) {
        DEBUG_printName("glTexCoord2d")
        GL43.glTexCoord2d(s, t)
    }

    fun qglTexCoord2dv(v: FloatArray?) {
        DEBUG_printName("glTexCoord2dv")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglTexCoord2f(s: Float, t: Float) {
        DEBUG_printName("glTexCoord2f")
        GL43.glTexCoord2f(s, t)
    }

    fun qglTexCoord2fv(v: FloatArray) {
        DEBUG_printName("glTexCoord2fv")
        qglTexCoord2f(v[0], v[1])
    }

    fun qglTexCoord2i(s: Int, t: Int) {
        DEBUG_printName("glTexCoord2i")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglTexCoord2iv(v: IntArray?) {
        DEBUG_printName("glTexCoord2iv")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglTexCoord2s(s: Short, t: Short) {
        DEBUG_printName("glTexCoord2s")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglTexCoord2sv(v: ShortArray?) {
        DEBUG_printName("glTexCoord2sv")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglTexCoord3d(s: Double, t: Double, r: Double) {
        DEBUG_printName("glTexCoord3d")
        GL43.glTexCoord3d(s, t, r)
    }

    fun qglTexCoord3dv(v: DoubleArray?) {
        DEBUG_printName("glTexCoord3dv")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglTexCoord3f(s: Float, t: Float, r: Float) {
        DEBUG_printName("glTexCoord3f")
        GL43.glTexCoord3f(s, t, r)
    }

    fun qglTexCoord3fv(v: FloatArray?) {
        DEBUG_printName("glTexCoord3fv")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglTexCoord3i(s: Int, t: Int, r: Int) {
        DEBUG_printName("glTexCoord3i")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglTexCoord3iv(v: IntArray?) {
        DEBUG_printName("glTexCoord3iv")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglTexCoord3s(s: Short, t: Short, r: Short) {
        DEBUG_printName("glTexCoord3s")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglTexCoord3sv(v: ShortArray?) {
        DEBUG_printName("glTexCoord3sv")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglTexCoord4d(s: Double, t: Double, r: Double, q: Double) {
        DEBUG_printName("glTexCoord4d")
        GL43.glTexCoord4d(s, t, r, q)
    }

    fun qglTexCoord4dv(v: DoubleArray?) {
        DEBUG_printName("glTexCoord4dv")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglTexCoord4f(s: Float, t: Float, r: Float, q: Float) {
        DEBUG_printName("glTexCoord4f")
        GL43.glTexCoord4f(s, t, r, q)
    }

    fun qglTexCoord4fv(v: FloatArray?) {
        DEBUG_printName("glTexCoord4fv")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglTexCoord4i(s: Int, t: Int, r: Int, q: Int) {
        DEBUG_printName("glTexCoord4i")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglTexCoord4iv(v: IntArray?) {
        DEBUG_printName("glTexCoord4iv")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglTexCoord4s(s: Short, t: Short, r: Short, q: Short) {
        DEBUG_printName("glTexCoord4s")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglTexCoord4sv(v: ShortArray?) {
        DEBUG_printName("glTexCoord4sv")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglTexCoordPointer(size: Int, type: Int, stride: Int, pointer: Long) {
        DEBUG_printName("glTexCoordPointer")
        GL43.glTexCoordPointer(size, type, stride, pointer)
    }

    @Deprecated("")
    fun qglTexCoordPointer(size: Int, type: Int, stride: Int, pointer: FloatArray?) {
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglTexCoordPointer(size: Int, type: Int, stride: Int, pointer: ByteBuffer) {
        DEBUG_printName("glTexCoordPointer")
        GL43.glTexCoordPointer(size, type, stride, pointer)
    }

    fun qglTexEnvf(target: Int, pName: Int, param: Float) {
        DEBUG_printName("glTexEnvf")
        GL43.glTexEnvf(target, pName, param)
    }

    fun qglTexEnvfv(target: Int, pName: Int, params: FloatBuffer) {
        DEBUG_printName("glTexEnvfv")
        GL43.glTexEnvfv(target, pName, params)
    }

    fun qglTexEnvi(target: Int, pName: Int, param: Int) {
        DEBUG_printName("glTexEnvi")
        GL43.glTexEnvi(target, pName, param)
    }

    fun qglTexEnviv(target: Int, pName: Int, params: IntArray?) {
        DEBUG_printName("glTexEnviv")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglTexGend(coord: Int, pName: Int, param: Double) {
        DEBUG_printName("glTexGend")
        GL43.glTexGend(coord, pName, param)
    }

    fun qglTexGendv(coord: Int, pName: Int, params: FloatArray?) {
        DEBUG_printName("glTexGendv")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglTexGenf(coord: Int, pName: Int, param: Float) {
        DEBUG_printName("glTexGenf")
        GL43.glTexGenf(coord, pName, param)
    }

    fun qglTexGenfv(coord: Int, pName: Int, params: FloatArray) {
        DEBUG_printName("glTexGenfv")
        GL43.glTexGenfv(coord, pName, params)
    }

    fun qglTexGeni(coord: Int, pName: Int, param: Int) {
        DEBUG_printName("glTexGeni")
        GL43.glTexGeni(coord, pName, param)
    }

    fun qglTexGeniv(coord: Int, pName: Int, params: IntArray?) {
        DEBUG_printName("glTexGeniv")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglTexImage1D(
        target: Int,
        level: Int,
        internalformat: Int,
        width: Int,
        border: Int,
        format: Int,
        type: Int,
        pixels: ByteBuffer?
    ) {
        DEBUG_printName("glTexImage1D")
        GL43.glTexImage1D(target, level, internalformat, width, border, format, type, pixels)
    }

    @Deprecated("")
    fun qglTexImage2D(
        target: Int,
        level: Int,
        internalformat: Int,
        width: Int,
        height: Int,
        border: Int,
        format: Int,
        type: Int,
        pixels: ByteArray?
    ) {
        DEBUG_printName("glTexImage2D")
        qglTexImage2D(
            target,
            level,
            internalformat,
            width,
            height,
            border,
            format,
            type,
            if (pixels != null) wrap(pixels) else null as ByteBuffer?
        )
    }

    fun qglTexImage2D(
        target: Int,
        level: Int,
        internalformat: Int,
        width: Int,
        height: Int,
        border: Int,
        format: Int,
        type: Int,
        pixels: ByteBuffer?
    ) {
        DEBUG_printName("glTexImage2D")
        GL43.glTexImage2D(target, level, internalformat, width, height, border, format, type, pixels)
    }

    fun qglTexParameterf(target: Int, pName: Int, param: Float) {
        DEBUG_printName("glTexParameterf")
        GL43.glTexParameterf(target, pName, param)
    }

    fun qglTexParameterfv(target: Int, pName: Int, params: FloatBuffer) {
        DEBUG_printName("glTexParameterfv")
        GL43.glTexParameterfv(target, pName, params)
    }

    fun qglTexParameteri(target: Int, pName: Int, param: Int) {
        DEBUG_printName("glTexParameteri")
        GL43.glTexParameteri(target, pName, param)
    }

    fun qglTexParameteriv(target: Int, pName: Int, params: IntArray?) {
        DEBUG_printName("glTexParameteriv")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglTexSubImage1D(
        target: Int,
        level: Int,
        xoffset: Int,
        width: Int,
        format: Int,
        type: Int,
        pixels: ByteBuffer
    ) {
        DEBUG_printName("glTexSubImage1D")
        GL43.glTexSubImage1D(target, level, xoffset, width, format, type, pixels)
    }

    fun qglTexSubImage2D(
        target: Int,
        level: Int,
        xoffset: Int,
        yoffset: Int,
        width: Int,
        height: Int,
        format: Int,
        type: Int,
        pixels: ByteBuffer
    ) {
        DEBUG_printName("glTexSubImage2D")
        GL43.glTexSubImage2D(target, level, xoffset, yoffset, width, height, format, type, pixels)
    }

    fun qglTranslated(x: Double, y: Double, z: Double) {
        DEBUG_printName("glTranslated")
        GL43.glTranslated(x, y, z)
    }

    fun qglTranslatef(x: Float, y: Float, z: Float) {
        DEBUG_printName("glTranslatef")
        GL43.glTranslatef(x, y, z)
    }

    fun qglVertex2d(x: Double, y: Double) {
        DEBUG_printName("glVertex2d")
        GL43.glVertex2d(x, y)
    }

    fun qglVertex2dv(v: DoubleArray?) {
        DEBUG_printName("glVertex2dv")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglVertex2f(x: Float, y: Float) {
        DEBUG_printName("glVertex2f")
        GL43.glVertex2f(x, y)
    }

    fun qglVertex2fv(v: FloatArray?) {
        DEBUG_printName("glVertex2fv")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglVertex2i(x: Int, y: Int) {
        DEBUG_printName("glVertex2i")
        GL43.glVertex2i(x, y)
    }

    fun qglVertex2iv(v: IntArray?) {
        DEBUG_printName("glVertex2iv")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglVertex2s(x: Short, y: Short) {
        DEBUG_printName("glVertex2s")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglVertex2sv(v: ShortArray?) {
        DEBUG_printName("glVertex2sv")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglVertex3d(x: Double, y: Double, z: Double) {
        DEBUG_printName("glVertex3d")
        GL43.glVertex3d(x, y, z)
    }

    fun qglVertex3dv(v: FloatArray?) {
        DEBUG_printName("glVertex3dv")
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglVertex3f(x: Float, y: Float, z: Float) {
        DEBUG_printName("glVertex3f")
        GL43.glVertex3f(x, y, z)
    }

    fun qglVertex3fv(v: FloatArray) {
        DEBUG_printName("glVertex3fv")
        qglVertex3f(v[0], v[1], v[2])
    }

    fun qglVertex3i(x: Int, y: Int, z: Int) {
        DEBUG_printName("glVertex3i")
        GL43.glVertex3i(x, y, z)
    }

    fun qglVertex3iv(v: IntArray?) {
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglVertex3s(x: Short, y: Short, z: Short) {
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglVertex3sv(v: ShortArray?) {
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglVertex4d(x: Double, y: Double, z: Double, w: Double) {
        DEBUG_printName("glVertex4d")
        GL43.glVertex4d(x, y, z, w)
    }

    fun qglVertex4dv(v: DoubleArray?) {
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglVertex4f(x: Float, y: Float, z: Float, w: Float) {
        DEBUG_printName("glVertex4f")
        GL43.glVertex4f(x, y, z, w)
    }

    fun qglVertex4fv(v: FloatArray?) {
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglVertex4i(x: Int, y: Int, z: Int, w: Int) {
        DEBUG_printName("glVertex4i")
        GL43.glVertex4i(x, y, z, w)
    }

    fun qglVertex4iv(v: IntArray?) {
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglVertex4s(x: Short, y: Short, z: Short, w: Short) {
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglVertex4sv(v: ShortArray?) {
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglVertexPointer(size: Int, type: Int, stride: Int, pointer: Long) {
        DEBUG_printName("glVertexPointer")
        GL43.glVertexPointer(size, type, stride, pointer)
    }

    @Deprecated("")
    fun qglVertexPointer(size: Int, type: Int, stride: Int, pointer: FloatArray?) {
        throw UnsupportedOperationException("Not supported yet.")
    }

    fun qglVertexPointer(size: Int, type: Int, stride: Int, pointer: ByteBuffer) {
        DEBUG_printName("glVertexPointer")
        GL43.glVertexPointer(size, type, stride, pointer)
    }

    fun qglViewport(x: Int, y: Int, width: Int, height: Int) {
        DEBUG_printName("glViewport")
        GL43.glViewport(x, y, width, height)
    }

    private fun DEBUG_printName(functionName: String) {
        if (GL_DEBUG) {
            println(functionName)
        }
    }

    private fun checkGLError() {
        // Debug error checking is a no-op when GL_DEBUG is false
    }

    @Deprecated("the calling functions should send ByteBuffers instead.")
    private fun wrap(byteArray: ByteArray): ByteBuffer {
        return BufferUtils.createByteBuffer(byteArray.size or 16).put(byteArray).flip()
    }

    @Deprecated("the calling functions should send IntBuffers instead.")
    private fun wrap(intArray: IntArray): IntBuffer {
        return BufferUtils.createIntBuffer(intArray.size).put(intArray).flip()
    }

    @Deprecated("the calling functions should send FloatBuffers instead.")
    private fun wrap(doubleArray: FloatArray): FloatBuffer {
        return BufferUtils.createFloatBuffer(doubleArray.size or 16).put(doubleArray).flip()
    }

    // ATI_fragment_shader
    internal object ATI_fragment_shader {
        fun  /*PFNGLGENFRAGMENTSHADERSATIPROC*/qglGenFragmentShadersATI(range: Int) {}
        fun  /*PFNGLBINDFRAGMENTSHADERATIPROC*/qglBindFragmentShaderATI(id: Int) {}
        fun  /*PFNGLDELETEFRAGMENTSHADERATIPROC*/qglDeleteFragmentShaderATI(id: Int) {}
        fun  /*PFNGLBEGINFRAGMENTSHADERATIPROC*/qglBeginFragmentShaderATI() {}
        fun  /*PFNGLENDFRAGMENTSHADERATIPROC*/qglEndFragmentShaderATI() {}
        fun  /*PFNGLPASSTEXCOORDATIPROC*/qglPassTexCoordATI(dst: Int, coord: Int, swizzle: Int) {}
        fun  /*PFNGLSAMPLEMAPATIPROC*/qglSampleMapATI(dst: Int, interp: Int, swizzle: Int) {}
        fun  /*PFNGLCOLORFRAGMENTOP1ATIPROC*/qglColorFragmentOp1ATI(
            op: Int,
            dst: Int,
            dstMask: Int,
            dstMod: Int,
            arg1: Int,
            arg1Rep: Int,
            arg1Mod: Int
        ) {
        }

        fun  /*PFNGLCOLORFRAGMENTOP2ATIPROC*/qglColorFragmentOp2ATI(
            op: Int,
            dst: Int,
            dstMask: Int,
            dstMod: Int,
            arg1: Int,
            arg1Rep: Int,
            arg1Mod: Int,
            arg2: Int,
            arg2Rep: Int,
            arg2Mod: Int
        ) {
        }

        fun  /*PFNGLCOLORFRAGMENTOP3ATIPROC*/qglColorFragmentOp3ATI(
            op: Int,
            dst: Int,
            dstMask: Int,
            dstMod: Int,
            arg1: Int,
            arg1Rep: Int,
            arg1Mod: Int,
            arg2: Int,
            arg2Rep: Int,
            arg2Mod: Int,
            arg3: Int,
            arg3Rep: Int,
            arg3Mod: Int
        ) {
        }

        fun  /*PFNGLALPHAFRAGMENTOP1ATIPROC*/qglAlphaFragmentOp1ATI(
            op: Int,
            dst: Int,
            dstMod: Int,
            arg1: Int,
            arg1Rep: Int,
            arg1Mod: Int
        ) {
        }

        fun  /*PFNGLALPHAFRAGMENTOP2ATIPROC*/qglAlphaFragmentOp2ATI(
            op: Int,
            dst: Int,
            dstMod: Int,
            arg1: Int,
            arg1Rep: Int,
            arg1Mod: Int,
            arg2: Int,
            arg2Rep: Int,
            arg2Mod: Int
        ) {
        }

        fun  /*PFNGLALPHAFRAGMENTOP3ATIPROC*/qglAlphaFragmentOp3ATI(
            op: Int,
            dst: Int,
            dstMod: Int,
            arg1: Int,
            arg1Rep: Int,
            arg1Mod: Int,
            arg2: Int,
            arg2Rep: Int,
            arg2Mod: Int,
            arg3: Int,
            arg3Rep: Int,
            arg3Mod: Int
        ) {
        }

        fun  /*PFNGLSETFRAGMENTSHADERCONSTANTATIPROC*/qglSetFragmentShaderConstantATI(dst: Int, value: FloatArray?) {}
    }
}

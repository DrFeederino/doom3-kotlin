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

import neo.Renderer.*
import neo.Renderer.Cinematic.cinData_t
import neo.Renderer.Image.idImage
import neo.Renderer.Material.idMaterial
import neo.Renderer.Material.shaderStage_t
import neo.Renderer.Material.stageLighting_t
import neo.Renderer.Material.texgen_t
import neo.Renderer.Material.textureStage_t
import neo.Renderer.Model.srfTriangles_s
import neo.Renderer.qgl.qglClear
import neo.Renderer.qgl.qglClearStencil
import neo.Renderer.qgl.qglDisable
import neo.Renderer.qgl.qglEnable
import neo.Renderer.qgl.qglLoadMatrixf
import neo.Renderer.qgl.qglMatrixMode
import neo.Renderer.qgl.qglScissor
import neo.Renderer.qgl.qglStencilMask
import neo.Renderer.qgl.qglViewport
import neo.Renderer.tr_backend.GL_Cull
import neo.Renderer.tr_backend.GL_State
import neo.Renderer.RenderWorld.renderView_s
import neo.Renderer.tr_main.myGlMultMatrix
import neo.TempDump.btoi
import neo.idlib.geometry.DrawVert.idDrawVert
import neo.idlib.math.idPlane
import neo.idlib.math.idVec3
import neo.idlib.math.idVec4
import neo.sys.win_glimp.GLimp_ActivateContext
import neo.sys.win_glimp.GLimp_DeactivateContext
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL13

object tr_render {
    /*

     back end scene + lights rendering functions

     */

    /*
     =================
     RB_DrawElementsImmediate

     Draws with immediate mode commands, which is going to be very slow.
     This should never happen if the vertex cache is operating properly.
     =================
     */
    fun RB_DrawElementsImmediate(tri: srfTriangles_s) {
        backEnd!!.pc.c_drawElements++
        backEnd!!.pc.c_drawIndexes += tri.numIndexes
        backEnd!!.pc.c_drawVertexes += tri.numVerts
        if (tri.ambientSurface != null) {
            if (tri.indexes === tri.ambientSurface!!.indexes) {
                backEnd!!.pc.c_drawRefIndexes += tri.numIndexes
            }
            if (tri.verts === tri.ambientSurface!!.verts) {
                backEnd!!.pc.c_drawRefVertexes += tri.numVerts
            }
        }
        qgl.qglBegin(GL11.GL_TRIANGLES)
        for (i in 0 until tri.numIndexes) {
            qgl.qglTexCoord2fv(tri.verts!![tri.indexes!![i]]!!.st.ToFloatPtr())
            qgl.qglVertex3fv(tri.verts!![tri.indexes!![i]]!!.xyz.ToFloatPtr())
        }
        qgl.qglEnd()
    }

    /*
     ================
     RB_DrawElementsWithCounters
     ================
     */
    fun RB_DrawElementsWithCounters(tri: srfTriangles_s) {
        backEnd!!.pc.c_drawElements++
        backEnd!!.pc.c_drawIndexes += tri.numIndexes
        backEnd!!.pc.c_drawVertexes += tri.numVerts
        if (tri.ambientSurface != null) {
            if (tri.indexes === tri.ambientSurface!!.indexes) {
                backEnd!!.pc.c_drawRefIndexes += tri.numIndexes
            }
            if (tri.verts === tri.ambientSurface!!.verts) {
                backEnd!!.pc.c_drawRefVertexes += tri.numVerts
            }
        }
        val count: Int = if (r_singleTriangle.GetBool()) 3 else tri.numIndexes
        if (tri.indexCache != null && r_useIndexBuffers!!.GetBool()) {
            qgl.qglDrawElements(
                GL11.GL_TRIANGLES,
                count,
                Model.GL_INDEX_TYPE,
                VertexCache.vertexCache.Position(tri.indexCache)
            )
            backEnd!!.pc.c_vboIndexes += tri.numIndexes
        } else {
            if (r_useIndexBuffers.GetBool()) {
                VertexCache.vertexCache.UnbindIndex()
            }
            qgl.qglDrawElements(GL11.GL_TRIANGLES, count, Model.GL_INDEX_TYPE, tri.indexes)
        }
    }

    /*
     ================
     RB_DrawShadowElementsWithCounters

     May not use all the indexes in the surface if caps are skipped
     ================
     */
    fun RB_DrawShadowElementsWithCounters(tri: srfTriangles_s, numIndexes: Int) {
        backEnd!!.pc.c_shadowElements++
        backEnd!!.pc.c_shadowIndexes += numIndexes
        backEnd!!.pc.c_shadowVertexes += tri.numVerts
        if (tri.indexCache != null && r_useIndexBuffers!!.GetBool()) {
            qgl.qglDrawElements(
                GL11.GL_TRIANGLES,
                if (r_singleTriangle!!.GetBool()) 3 else numIndexes,
                Model.GL_INDEX_TYPE,
                VertexCache.vertexCache.Position(tri.indexCache)
            )
            backEnd!!.pc.c_vboIndexes += numIndexes
        } else {
            if (r_useIndexBuffers!!.GetBool()) {
                VertexCache.vertexCache.UnbindIndex()
            }
            qgl.qglDrawElements(
                GL11.GL_TRIANGLES,
                if (r_singleTriangle!!.GetBool()) 3 else numIndexes,
                Model.GL_INDEX_TYPE,
                tri.indexes
            )
        }
    }

    /*
     ===============
     RB_RenderTriangleSurface

     Sets texcoord and vertex pointers
     ===============
     */
    fun RB_RenderTriangleSurface(tri: srfTriangles_s) {
        if (tri.ambientCache == null) {
            RB_DrawElementsImmediate(tri)
            return
        }
        val ac =
            idDrawVert(VertexCache.vertexCache.Position(tri.ambientCache))
        qgl.qglVertexPointer(3, GL11.GL_FLOAT, idDrawVert.BYTES, ac.xyzOffset().toLong())
        qgl.qglTexCoordPointer(2, GL11.GL_FLOAT, idDrawVert.BYTES, ac.stOffset().toLong())
        RB_DrawElementsWithCounters(tri)
    }

    /*
     ===============
     RB_EnterWeaponDepthHack
     ===============
     */
    fun RB_EnterWeaponDepthHack() {
        qgl.qglDepthRange(0.0f, 0.5f)
        val matrix = FloatArray(16)
        System.arraycopy(backEnd!!.viewDef!!.projectionMatrix, 0, matrix, 0, matrix.size)
        matrix[14] *= 0.25f
        qgl.qglMatrixMode(GL11.GL_PROJECTION)
        qgl.qglLoadMatrixf(matrix)
        qgl.qglMatrixMode(GL11.GL_MODELVIEW)
    }

    /*
     ===============
     RB_EnterModelDepthHack
     ===============
     */
    fun RB_EnterModelDepthHack(depth: Float) {
        qgl.qglDepthRange(0.0f, 1.0f)
        val matrix = FloatArray(16)
        System.arraycopy(backEnd!!.viewDef!!.projectionMatrix, 0, matrix, 0, matrix.size)
        matrix[14] -= depth
        qgl.qglMatrixMode(GL11.GL_PROJECTION)
        qgl.qglLoadMatrixf(matrix)
        qgl.qglMatrixMode(GL11.GL_MODELVIEW)
    }

    /*
     ===============
     RB_LeaveDepthHack
     ===============
     */
    fun RB_LeaveDepthHack() {
        qgl.qglDepthRange(0.0f, 1.0f)
        qgl.qglMatrixMode(GL11.GL_PROJECTION)
        qgl.qglLoadMatrixf(backEnd!!.viewDef!!.projectionMatrix)
        qgl.qglMatrixMode(GL11.GL_MODELVIEW)
    }

    /*
     ====================
     RB_RenderDrawSurfListWithFunction

     The triangle functions can check backEnd!!.currentSpace != surf.space
     to see if they need to perform any new matrix setup.  The modelview
     matrix will already have been loaded, and backEnd!!.currentSpace will
     be updated after the triangle function completes.
     ====================
     */
    fun RB_RenderDrawSurfListWithFunction(drawSurfs: Array<drawSurf_s>, numDrawSurfs: Int, triFunc_: triFunc) {
        var i: Int
        var drawSurf: drawSurf_s
        backEnd!!.currentSpace = null
        i = 0
        while (i < numDrawSurfs) {
            drawSurf = drawSurfs[i]

            // change the matrix if needed
            if (drawSurf.space !== backEnd!!.currentSpace) {
                qgl.qglLoadMatrixf(drawSurf.space!!.modelViewMatrix)
            }
            if (drawSurf.space!!.weaponDepthHack) {
                RB_EnterWeaponDepthHack()
            }
            if (drawSurf.space!!.modelDepthHack != 0.0f) {
                RB_EnterModelDepthHack(drawSurf.space!!.modelDepthHack)
            }

            // change the scissor if needed
            if (r_useScissor!!.GetBool() && !backEnd!!.currentScissor!!.Equals(drawSurf.scissorRect!!)) {
                backEnd!!.currentScissor = drawSurf.scissorRect
                qgl.qglScissor(
                    backEnd!!.viewDef!!.viewport.x1 + backEnd!!.currentScissor!!.x1,
                    backEnd!!.viewDef!!.viewport.y1 + backEnd!!.currentScissor!!.y1,
                    backEnd!!.currentScissor!!.x2 + 1 - backEnd!!.currentScissor!!.x1,
                    backEnd!!.currentScissor!!.y2 + 1 - backEnd!!.currentScissor!!.y1
                )
            }

            // render it
            triFunc_.run(drawSurf)
            if (drawSurf.space!!.weaponDepthHack || drawSurf.space!!.modelDepthHack != 0.0f) {
                RB_LeaveDepthHack()
            }
            backEnd!!.currentSpace = drawSurf.space
            i++
        }
    }

    fun RB_RenderDrawSurfChainWithFunction(drawSurfs: drawSurf_s?, triFunc_: triFunc) {
        var drawSurf: drawSurf_s?
        backEnd!!.currentSpace = null
        drawSurf = drawSurfs
        while (drawSurf != null) {

            // change the matrix if needed
            if (drawSurf.space !== backEnd!!.currentSpace) {
                qgl.qglLoadMatrixf(drawSurf.space!!.modelViewMatrix)
            }
            if (drawSurf.space!!.weaponDepthHack) {
                RB_EnterWeaponDepthHack()
            }
            if (drawSurf.space!!.modelDepthHack != 0.0f) {
                RB_EnterModelDepthHack(drawSurf.space!!.modelDepthHack)
            }

            // change the scissor if needed
            if (r_useScissor!!.GetBool() && !backEnd!!.currentScissor!!.Equals(drawSurf.scissorRect!!)) {
                backEnd!!.currentScissor = idScreenRect(drawSurf.scissorRect!!)
                qgl.qglScissor(
                    backEnd!!.viewDef!!.viewport.x1 + backEnd!!.currentScissor!!.x1,
                    backEnd!!.viewDef!!.viewport.y1 + backEnd!!.currentScissor!!.y1,
                    backEnd!!.currentScissor!!.x2 + 1 - backEnd!!.currentScissor!!.x1,
                    backEnd!!.currentScissor!!.y2 + 1 - backEnd!!.currentScissor!!.y1
                )
            }

            // render it
            triFunc_.run(drawSurf)
            if (drawSurf.space!!.weaponDepthHack || drawSurf.space!!.modelDepthHack != 0.0f) {
                RB_LeaveDepthHack()
            }
            backEnd!!.currentSpace = drawSurf.space
            drawSurf = drawSurf.nextOnLight
        }
    }

    fun RB_GetShaderTextureMatrix(shaderRegisters: FloatArray, texture: textureStage_t, matrix: FloatArray /*[16]*/) {
        matrix[0] = shaderRegisters[texture.matrix[0]!![0]]
        matrix[4] = shaderRegisters[texture.matrix[0]!![1]]
        matrix[8] = 0.0f
        matrix[12] = shaderRegisters[texture.matrix[0]!![2]]

        // we attempt to keep scrolls from generating incredibly large texture values, but
        // center rotations and center scales can still generate offsets that need to be > 1
        if (matrix[12] < -40 || matrix[12] > 40) {
            matrix[12] -= (matrix[12].toInt()).toFloat()
        }
        matrix[1] = shaderRegisters[texture.matrix[1]!![0]]
        matrix[5] = shaderRegisters[texture.matrix[1]!![1]]
        matrix[9] = 0.0f
        matrix[13] = shaderRegisters[texture.matrix[1]!![2]]
        if (matrix[13] < -40 || matrix[13] > 40) {
            matrix[13] -= (matrix[13].toInt()).toFloat()
        }
        matrix[2] = 0.0f
        matrix[6] = 0.0f
        matrix[10] = 1.0f
        matrix[14] = 0.0f
        matrix[3] = 0.0f
        matrix[7] = 0.0f
        matrix[11] = 0.0f
        matrix[15] = 1.0f
    }

    /*
     ======================
     RB_LoadShaderTextureMatrix
     ======================
     */
    fun RB_LoadShaderTextureMatrix(shaderRegisters: FloatArray?, texture: textureStage_t?) {
        val matrix = FloatArray(16)
        RB_GetShaderTextureMatrix(shaderRegisters!!, texture!!, matrix)
        qgl.qglMatrixMode(GL11.GL_TEXTURE)
        qgl.qglLoadMatrixf(matrix)
        qgl.qglMatrixMode(GL11.GL_MODELVIEW)
    }

    fun RB_BindVariableStageImage(texture: textureStage_t, shaderRegisters: FloatArray?) {
        if (texture.cinematic[0] != null) {
            val cin: cinData_t?
            if (r_skipDynamicTextures!!.GetBool()) {
                Image.globalImages.defaultImage!!.Bind()
                return
            }

            // offset time by shaderParm[7] (FIXME: make the time offset a parameter of the shader?)
            // We make no attempt to optimize for multiple identical cinematics being in view, or
            // for cinematics going at a lower framerate than the renderer.
            cin = texture.cinematic[0]!!.ImageForTime(
                (1000 * (backEnd!!.viewDef!!.floatTime + backEnd!!.viewDef!!.renderView.shaderParms[11])).toInt()
            )
            if (cin.image != null) {
                Image.globalImages.cinematicImage!!.UploadScratch(cin.image, cin.imageWidth, cin.imageHeight)
            } else {
                Image.globalImages.blackImage!!.Bind()
            }
        } else {
            if (texture.image!![0] != null) {
                texture.image[0]!!.Bind()
            }
        }
    }

    /*
     ======================
     RB_BindStageTexture
     ======================
     */
    fun RB_BindStageTexture(shaderRegisters: FloatArray?, texture: textureStage_t, surf: drawSurf_s) {
        // image
        RB_BindVariableStageImage(texture, shaderRegisters)

        // texgens
        if (texture.texgen == texgen_t.TG_DIFFUSE_CUBE) {
            val vert =
                idDrawVert(VertexCache.vertexCache.Position(surf.geo!!.ambientCache))
            qgl.qglTexCoordPointer(3, GL11.GL_FLOAT, idDrawVert.BYTES, vert.normal.ToFloatPtr())
        }
        if (texture.texgen == texgen_t.TG_SKYBOX_CUBE || texture.texgen == texgen_t.TG_WOBBLESKY_CUBE) {
            qgl.qglTexCoordPointer(3, GL11.GL_FLOAT, 0, VertexCache.vertexCache.Position(surf.dynamicTexCoords))
        }
        if (texture.texgen == texgen_t.TG_REFLECT_CUBE) {
            qgl.qglEnable(GL11.GL_TEXTURE_GEN_S)
            qgl.qglEnable(GL11.GL_TEXTURE_GEN_T)
            qgl.qglEnable(GL11.GL_TEXTURE_GEN_R)
            qgl.qglTexGenf(GL11.GL_S, GL11.GL_TEXTURE_GEN_MODE, GL13.GL_REFLECTION_MAP.toFloat())
            qgl.qglTexGenf(GL11.GL_T, GL11.GL_TEXTURE_GEN_MODE, GL13.GL_REFLECTION_MAP.toFloat())
            qgl.qglTexGenf(GL11.GL_R, GL11.GL_TEXTURE_GEN_MODE, GL13.GL_REFLECTION_MAP.toFloat())
            qgl.qglEnableClientState(GL11.GL_NORMAL_ARRAY)
            val vert =
                idDrawVert(VertexCache.vertexCache.Position(surf.geo!!.ambientCache))
            qgl.qglNormalPointer(GL11.GL_FLOAT, idDrawVert.BYTES, vert.normalOffset().toLong())
            qgl.qglMatrixMode(GL11.GL_TEXTURE)
            val mat = FloatArray(16)
            tr_main.R_TransposeGLMatrix(backEnd!!.viewDef!!.worldSpace.modelViewMatrix, mat)
            qgl.qglLoadMatrixf(mat)
            qgl.qglMatrixMode(GL11.GL_MODELVIEW)
        }

        // matrix
        if (texture.hasMatrix) {
            RB_LoadShaderTextureMatrix(shaderRegisters, texture)
        }
    }

    /*
     ======================
     RB_FinishStageTexture
     ======================
     */
    fun RB_FinishStageTexture(texture: textureStage_t, surf: drawSurf_s) {
        if ((texture.texgen == texgen_t.TG_DIFFUSE_CUBE) || (texture.texgen == texgen_t.TG_SKYBOX_CUBE
                    ) || (texture.texgen == texgen_t.TG_WOBBLESKY_CUBE)
        ) {
            val vert =
                idDrawVert(VertexCache.vertexCache.Position(surf.geo!!.ambientCache))
            qgl.qglTexCoordPointer(
                2,
                GL11.GL_FLOAT,
                idDrawVert.BYTES,
                vert.st.ToFloatPtr()
            )
        }
        if (texture.texgen == texgen_t.TG_REFLECT_CUBE) {
            qgl.qglDisable(GL11.GL_TEXTURE_GEN_S)
            qgl.qglDisable(GL11.GL_TEXTURE_GEN_T)
            qgl.qglDisable(GL11.GL_TEXTURE_GEN_R)
            qgl.qglTexGenf(GL11.GL_S, GL11.GL_TEXTURE_GEN_MODE, GL11.GL_OBJECT_LINEAR.toFloat())
            qgl.qglTexGenf(GL11.GL_T, GL11.GL_TEXTURE_GEN_MODE, GL11.GL_OBJECT_LINEAR.toFloat())
            qgl.qglTexGenf(GL11.GL_R, GL11.GL_TEXTURE_GEN_MODE, GL11.GL_OBJECT_LINEAR.toFloat())
            qgl.qglDisableClientState(GL11.GL_NORMAL_ARRAY)
            qgl.qglMatrixMode(GL11.GL_TEXTURE)
            qgl.qglLoadIdentity()
            qgl.qglMatrixMode(GL11.GL_MODELVIEW)
        }
        if (texture.hasMatrix) {
            qgl.qglMatrixMode(GL11.GL_TEXTURE)
            qgl.qglLoadIdentity()
            qgl.qglMatrixMode(GL11.GL_MODELVIEW)
        }
    }

    //=============================================================================================
    /*
     =================
     RB_DetermineLightScale

     Sets:
     backEnd!!.lightScale
     backEnd!!.overBright

     Find out how much we are going to need to overscale the lighting, so we
     can down modulate the pre-lighting passes.

     We only look at light calculations, but an argument could be made that
     we should also look at surface evaluations, which would let surfaces
     overbright past 1.0f
     =================
     */
    fun RB_DetermineLightScale() {
        var vLight: viewLight_s?
        var shader: idMaterial
        var max: Float
        var i: Int
        var j: Int
        var numStages: Int
        var stage: shaderStage_t?

        // the light scale will be based on the largest color component of any surface
        // that will be drawn.
        // should we consider separating rgb scales?
        // if there are no lights, this will remain at 1.0f, so GUI-only
        // rendering will not lose any bits of precision
        max = 1.0f
        vLight = backEnd!!.viewDef!!.viewLights
        while (vLight != null) {

            // lights with no surfaces or shaderparms may still be present
            // for debug display
            if ((null == vLight.localInteractions[0]) && (null == vLight.globalInteractions[0]
                        ) && (null == vLight.translucentInteractions[0])
            ) {
                vLight = vLight.next
                continue
            }
            shader = vLight.lightShader!!
            numStages = shader.GetNumStages()
            i = 0
            while (i < numStages) {
                stage = shader.GetStage(i)
                j = 0
                while (j < 3) {
                    val v: Float =
                        r_lightScale!!.GetFloat() * vLight.shaderRegisters!![stage!!.color.registers[j]]
                    if (v > max) {
                        max = v
                    }
                    j++
                }
                i++
            }
            vLight = vLight.next
        }
        backEnd!!.pc.maxLightValue = max
        if (max <= tr.backEndRendererMaxLight) {
            backEnd!!.lightScale = r_lightScale!!.GetFloat()
            backEnd!!.overBright = 1.0f
        } else {
            backEnd!!.lightScale =
                r_lightScale!!.GetFloat() * tr.backEndRendererMaxLight / max
            backEnd!!.overBright = max / tr.backEndRendererMaxLight
        }
    }

    /*
     =================
     RB_BeginDrawingView

     Any mirrored or portaled views have already been drawn, so prepare
     to actually render the visible surfaces for this view
     =================
     */
    fun RB_BeginDrawingView() {

        val viewDef = backEnd!!.viewDef!!

        // set the modelview matrix for the viewer
        qglMatrixMode(GL_PROJECTION)
        qglLoadMatrixf(viewDef.projectionMatrix)
        qglMatrixMode(GL_MODELVIEW)

        // set the window clipping
        qglViewport(
            tr.viewportOffset[0] + viewDef.viewport.x1,
            tr.viewportOffset[1] + viewDef.viewport.y1,
            viewDef.viewport.x2 + 1 - viewDef.viewport.x1,
            viewDef.viewport.y2 + 1 - viewDef.viewport.y1
        )

        // the scissor may be smaller than the viewport for subviews
        qglScissor(
            tr.viewportOffset[0] + viewDef.viewport.x1 + viewDef.scissor.x1,
            tr.viewportOffset[1] + viewDef.viewport.y1 + viewDef.scissor.y1,
            viewDef.scissor.x2 + 1 - viewDef.scissor.x1,
            viewDef.scissor.y2 + 1 - viewDef.scissor.y1
        )
        backEnd?.currentScissor = idScreenRect(viewDef.scissor)

        // ensures that depth writes are enabled for the depth clear
        GL_State(GLS_DEFAULT)

        // we don't have to clear the depth / stencil buffer for 2D rendering
        if (backEnd!!.viewDef!!.viewEntitys != null) {
            qglStencilMask(0xff)
            // some cards may have 7 bit stencil buffers, so don't assume this
            // should be 128
            qglClearStencil(1 shl (glConfig.stencilBits - 1))
            qglClear(GL_DEPTH_BUFFER_BIT or GL_STENCIL_BUFFER_BIT)
            qglEnable(GL_DEPTH_TEST)
        } else {
            qglDisable(GL_DEPTH_TEST)
            qglDisable(GL_STENCIL_TEST)
        }

        backEnd!!.glState.faceCulling = -1        // force face culling to set next time
        GL_Cull(Material.cullType_t.CT_FRONT_SIDED)
    }

    /*
     ==================
     R_SetDrawInteractions
     ==================
     */
    fun R_SetDrawInteraction(
        surfaceStage: shaderStage_t,
        surfaceRegs: FloatArray,
        image: Array<idImage?>,
        matrix: Array<idVec4> /*[2]*/,
        color: idVec4? /*[4]*/
    ) {
        image[0] = surfaceStage.texture.image!![0]
        if (surfaceStage.texture.hasMatrix) {
            matrix[0][0] = surfaceRegs[surfaceStage.texture.matrix[0]!![0]]
            matrix[0][1] = surfaceRegs[surfaceStage.texture.matrix[0]!![1]]
            matrix[0][2] = 0.0f
            matrix[0][3] = surfaceRegs[surfaceStage.texture.matrix[0]!![2]]
            matrix[1][0] = surfaceRegs[surfaceStage.texture.matrix[1]!![0]]
            matrix[1][1] = surfaceRegs[surfaceStage.texture.matrix[1]!![1]]
            matrix[1][2] = 0.0f
            matrix[1][3] = surfaceRegs[surfaceStage.texture.matrix[1]!![2]]

            // we attempt to keep scrolls from generating incredibly large texture values, but
            // center rotations and center scales can still generate offsets that need to be > 1
            if (matrix[0][3] < -40 || matrix[0][3] > 40) {
                matrix[0].minusAssign(3, (matrix[0][3].toInt()).toFloat())
            }
            if (matrix[1][3] < -40 || matrix[1][3] > 40) {
                matrix[1].minusAssign(3, (matrix[1][3].toInt()).toFloat())
            }
        } else {
            matrix[0][0] = 1.0f
            matrix[0][1] = 0.0f
            matrix[0][2] = 0.0f
            matrix[0][3] = 0.0f
            matrix[1][0] = 0.0f
            matrix[1][1] = 1.0f
            matrix[1][2] = 0.0f
            matrix[1][3] = 0.0f
        }
        if (color != null) {
            for (i in 0..3) {
                color[i] = surfaceRegs[surfaceStage.color.registers[i]]
                // clamp here, so card with greater range don't look different.
                // we could perform overbrighting like we do for lights, but
                // it doesn't currently look worth it.
                if (color[i] < 0) {
                    color[i] = 0.0f
                } else if (color[i] > 1.0f) {
                    color[i] = 1.0f
                }
            }
        }
    }

    fun RB_SubmittInteraction(din: drawInteraction_t, drawInteraction: DrawInteraction) {
        if (null == din.bumpImage) {
            return
        }
        if (null == din.diffuseImage || r_skipDiffuse!!.GetBool()) {
            din.diffuseImage = Image.globalImages.blackImage
        }
        if ((null == din.specularImage) || r_skipSpecular!!.GetBool() || (din.ambientLight != 0)) {
            din.specularImage = Image.globalImages.blackImage
        }
        if (null == din.bumpImage || r_skipBump!!.GetBool()) {
            din.bumpImage = Image.globalImages.flatNormalMap
        }

        // if we wouldn't draw anything, don't call the Draw function
        if (((((din.diffuseColor[0] > 0
                    ) || (din.diffuseColor[1] > 0
                    ) || (din.diffuseColor[2] > 0)) && din.diffuseImage !== Image.globalImages.blackImage)
                    || (((din.specularColor[0] > 0
                    ) || (din.specularColor[1] > 0
                    ) || (din.specularColor[2] > 0)) && din.specularImage !== Image.globalImages.blackImage))
        ) {
            drawInteraction.run(din)
        }
    }

    /*
     =============
     RB_CreateSingleDrawInteractions

     This can be used by different draw_* backends to decompose a complex light / surface
     interaction into primitive interactions
     =============
     */
    fun RB_CreateSingleDrawInteractions(surf: drawSurf_s, drawInteraction: DrawInteraction?) {
        val surfaceShader: idMaterial = surf.material!!
        val surfaceRegs: FloatArray = surf.shaderRegisters!!
        val vLight: viewLight_s = backEnd!!.vLight!!
        val lightShader: idMaterial = vLight.lightShader!!
        val lightRegs: FloatArray = vLight.shaderRegisters!!
        val inter = drawInteraction_t()
        inter.diffuseMatrix[0].Zero()
        inter.diffuseMatrix[1].Zero()
        inter.specularMatrix[0].Zero()
        inter.specularMatrix[1].Zero()

        if (r_skipInteractions.GetBool() || surf.geo == null || surf.geo!!.ambientCache == null) {
            return
        }

        // DG: support lights nospecular parm, if desired by mapper and/or user
        var noSpecVar = r_supportNoSpecular.GetInteger()
        var allowNoSpecular = (noSpecVar == 1)
        if (noSpecVar == -1) {
            // r_supportNoSpecular -1 only allows nospecular if the map enables
            // it in the worldspawn by setting "allow_nospecular" "1"
            // the value of that is saved in tr.allowNoSpecular by idRenderSystemLocal::EndLevelLoad()
            allowNoSpecular = tr.allowNoSpecular
        }

        if (tr.logFile != null) {
            tr_backend.RB_LogComment(
                "---------- RB_CreateSingleDrawInteractions %s on %s ----------\n",
                lightShader.GetName(),
                surfaceShader.GetName()
            )
        }

        // change the matrix and light projection vectors if needed
        if (surf.space !== backEnd!!.currentSpace) {
            backEnd!!.currentSpace = surf.space
            qgl.qglLoadMatrixf(surf.space!!.modelViewMatrix)
        }

        // change the scissor if needed
        if (r_useScissor!!.GetBool() && !backEnd!!.currentScissor!!.Equals(surf.scissorRect!!)) {
            backEnd!!.currentScissor = surf.scissorRect
            qgl.qglScissor(
                backEnd!!.viewDef!!.viewport.x1 + backEnd!!.currentScissor!!.x1,
                backEnd!!.viewDef!!.viewport.y1 + backEnd!!.currentScissor!!.y1,
                backEnd!!.currentScissor!!.x2 + 1 - backEnd!!.currentScissor!!.x1,
                backEnd!!.currentScissor!!.y2 + 1 - backEnd!!.currentScissor!!.y1
            )
        }

        // hack depth range if needed
        if (surf.space!!.weaponDepthHack) {
            RB_EnterWeaponDepthHack()
        }
        if (surf.space!!.modelDepthHack != 0.0f) {
            RB_EnterModelDepthHack(surf.space!!.modelDepthHack)
        }
        inter.surf = surf
        inter.lightFalloffImage = vLight.falloffImage
        tr_main.R_GlobalPointToLocal(surf.space!!.modelMatrix, vLight.globalLightOrigin, inter.localLightOrigin)
        tr_main.R_GlobalPointToLocal(
            surf.space!!.modelMatrix,
            backEnd!!.viewDef!!.renderView.vieworg,
            inter.localViewOrigin
        )
        inter.localLightOrigin[3] = 0.0f
        inter.localViewOrigin[3] = 1.0f
        inter.ambientLight = btoi(lightShader.IsAmbientLight())

        // the base projections may be modified by texture matrix on light stages
        val lightProject: Array<idPlane> = idPlane.generateArray(4)
        for (i in 0..3) {
            tr_main.R_GlobalPlaneToLocal(
                surf.space!!.modelMatrix,
                backEnd!!.vLight!!.lightProject[i],
                lightProject[i]
            )
        }
        for (lightStageNum in 0 until lightShader.GetNumStages()) {
            val lightStage: shaderStage_t? = lightShader.GetStage(lightStageNum)

            // ignore stages that fail the condition
            if (0.0f == lightRegs[lightStage!!.conditionRegister]) {
                continue
            }
            inter.lightImage = lightStage.texture.image!![0]

            for (i in inter.lightProjection.indices) {
                inter.lightProjection[i] = lightProject[i].ToVec4()
            }
            // now multiply the texgen by the light texture matrix
            if (lightStage.texture.hasMatrix) {
                RB_GetShaderTextureMatrix(lightRegs, lightStage.texture, backEnd!!.lightTextureMatrix)
                draw_common.RB_BakeTextureMatrixIntoTexgen(
                    inter.lightProjection as Array<idVec4>,
                    backEnd!!.lightTextureMatrix
                )
            }
            inter.bumpImage = null
            inter.specularImage = null
            inter.diffuseImage = null
            inter.diffuseColor.set(0.0f, 0.0f, 0.0f, 0.0f)
            inter.specularColor.set(0.0f, 0.0f, 0.0f, 0.0f)
            val lightColor = FloatArray(4)

            // backEnd!!.lightScale is calculated so that lightColor[] will never exceed
            // tr.backEndRendererMaxLight
            lightColor[0] = backEnd!!.lightScale * lightRegs[lightStage.color.registers[0]]
            lightColor[1] = backEnd!!.lightScale * lightRegs[lightStage.color.registers[1]]
            lightColor[2] = backEnd!!.lightScale * lightRegs[lightStage.color.registers[2]]
            lightColor[3] = lightRegs[lightStage.color.registers[3]]

            // go through the individual stages
            for (surfaceStageNum in 0 until surfaceShader.GetNumStages()) {
                val surfaceStage: shaderStage_t? = surfaceShader.GetStage(surfaceStageNum)
                when (surfaceStage!!.lighting) {
                    stageLighting_t.SL_AMBIENT -> {
                        // ignore ambient stages while drawing interactions
                        continue
                    }

                    stageLighting_t.SL_BUMP -> {

                        // ignore stage that fails the condition
                        if (0.0f == surfaceRegs[surfaceStage.conditionRegister]) {
                            continue
                        }
                        // draw any previous interaction
                        RB_SubmittInteraction(inter, drawInteraction!!)
                        inter.diffuseImage = null
                        inter.specularImage = null
                        val bumpImage: Array<idImage?> = arrayOf(null)
                        R_SetDrawInteraction(surfaceStage, surfaceRegs, bumpImage, inter.bumpMatrix, null)
                        inter.bumpImage = bumpImage[0]
                        continue
                    }

                    stageLighting_t.SL_DIFFUSE -> {

                        // ignore stage that fails the condition
                        if (0.0f == surfaceRegs[surfaceStage.conditionRegister]) {
                            continue
                        }
                        if (inter.diffuseImage != null) {
                            RB_SubmittInteraction(inter, drawInteraction!!)
                        }
                        val diffuseImage: Array<idImage?> = arrayOf(null)
                        R_SetDrawInteraction(
                            surfaceStage,
                            surfaceRegs,
                            diffuseImage,
                            inter.diffuseMatrix,
                            inter.diffuseColor
                        )
                        inter.diffuseImage = diffuseImage[0]
                        inter.diffuseColor.timesAssign(0, lightColor[0])
                        inter.diffuseColor.timesAssign(1, lightColor[1])
                        inter.diffuseColor.timesAssign(2, lightColor[2])
                        inter.diffuseColor.timesAssign(3, lightColor[3])
                        inter.vertexColor = surfaceStage.vertexColor
                        continue
                    }

                    stageLighting_t.SL_SPECULAR -> {

                        // ignore stage that fails the condition
                        if (0.0f == surfaceRegs[surfaceStage.conditionRegister]) {
                            continue
                        }
                        if (inter.specularImage != null) {
                            RB_SubmittInteraction(inter, drawInteraction!!)
                        }
                        // jmarshall - add no specular support(great for fill lighting).
                        if (!allowNoSpecular || !vLight.lightDef!!.parms.noSpecular._val) {
                            val specularImage: Array<idImage?> = arrayOf(null)
                            R_SetDrawInteraction(
                                surfaceStage,
                                surfaceRegs,
                                specularImage,
                                inter.specularMatrix,
                                inter.specularColor
                            )
                            inter.specularImage = specularImage[0]
                            inter.specularColor.timesAssign(0, lightColor[0])
                            inter.specularColor.timesAssign(1, lightColor[1])
                            inter.specularColor.timesAssign(2, lightColor[2])
                            inter.specularColor.timesAssign(3, lightColor[3])
                            inter.vertexColor = surfaceStage.vertexColor
                        }
                        // jmarshall end
                        continue
                    }
                }
            }

            // draw the final interaction
            RB_SubmittInteraction(inter, drawInteraction!!)
        }

        // unhack depth range if needed
        if (surf.space!!.weaponDepthHack || surf.space!!.modelDepthHack != 0.0f) {
            RB_LeaveDepthHack()
        }
    }

    /*
     =============
     RB_DrawView
     =============
     */
    fun RB_DrawView(data: Any) {
        val cmd: drawSurfsCommand_t
        cmd = data as drawSurfsCommand_t

        // with r_lockSurfaces enabled, we set the locked render view
        // for the primary viewDef for all the "what should be drawn" calculations.
        // now it must be reverted to the real render view so the scene gets rendered
        // from the actual current players point of view
        if (r_lockSurfaces.GetBool() && tr.primaryView == cmd.viewDef) {
            val parms = cmd.viewDef!!
            val origParms = viewDef_s(parms)
            val real = tr.lockSurfacesRealViewDef!!

            // C++: *parms = tr.lockSurfacesRealViewDef — copy ALL camera-related fields from real view
            parms.renderView = renderView_s(real.renderView)
            parms.projectionMatrix = real.projectionMatrix.copyOf()
            parms.worldSpace = viewEntity_s(real.worldSpace)
            parms.viewport = idScreenRect(real.viewport)
            parms.scissor = idScreenRect(real.scissor)
            parms.isSubview = real.isSubview
            parms.isMirror = real.isMirror
            parms.areaNum = real.areaNum
            parms.initialViewAreaOrigin.set(real.initialViewAreaOrigin)
            for (i in real.frustum.indices) {
                parms.frustum[i] = idPlane(real.frustum[i])
            }

            // restore draw-data from original (locked) view
            parms.renderWorld = origParms.renderWorld
            parms.floatTime = origParms.floatTime
            parms.drawSurfs = origParms.drawSurfs
            parms.numDrawSurfs = origParms.numDrawSurfs
            parms.maxDrawSurfs = origParms.maxDrawSurfs
            parms.viewLights = origParms.viewLights
            parms.viewEntitys = origParms.viewEntitys
            parms.connectedAreas = origParms.connectedAreas

            var vModel = parms.viewEntitys
            while (vModel != null) {
                myGlMultMatrix(vModel.modelMatrix, parms.worldSpace.modelViewMatrix, vModel.modelViewMatrix)
                vModel = vModel.next
            }
        }

        backEnd!!.viewDef = cmd.viewDef

        // we will need to do a new copyTexSubImage of the screen
        // when a SS_POST_PROCESS material is used
        backEnd!!.currentRenderCopied = false

        // if there aren't any drawsurfs, do nothing
        if (0 == backEnd!!.viewDef!!.numDrawSurfs) {
            return
        }

        // skip render bypasses everything that has models, assuming
        // them to be 3D views, but leaves 2D rendering visible
        if (r_skipRender!!.GetBool() && backEnd!!.viewDef!!.viewEntitys != null) {
            return
        }

        // skip render context sets the wgl context to NULL,
        // which should factor out the API cost, under the assumption
        // that all gl calls just return if the context isn't valid
        if (r_skipRenderContext!!.GetBool() && backEnd!!.viewDef!!.viewEntitys != null) {
            GLimp_DeactivateContext()
        }
        backEnd!!.pc.c_surfaces += backEnd!!.viewDef!!.numDrawSurfs
        tr_rendertools.RB_ShowOverdraw()

        // render the scene, jumping to the hardware specific interaction renderers
        draw_common.RB_STD_DrawView()

        // restore the context for 2D drawing if we were stubbing it out
        if (r_skipRenderContext!!.GetBool() && backEnd!!.viewDef!!.viewEntitys != null) {
            GLimp_ActivateContext()
            tr_backend.RB_SetDefaultGLState()
        }
    }

    abstract class DrawInteraction {
        abstract fun run(din: drawInteraction_t)
    }

    abstract class triFunc {
        abstract fun run(surf: drawSurf_s)
    }

    /*
     ===============
     RB_T_RenderTriangleSurface

     ===============
     */
    class RB_T_RenderTriangleSurface private constructor() : triFunc() {
        override fun run(surf: drawSurf_s) {
            RB_RenderTriangleSurface(surf.geo!!)
        }

        companion object {
            val INSTANCE: triFunc = RB_T_RenderTriangleSurface()
        }
    }
}

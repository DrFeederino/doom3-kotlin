/*
===========================================================================

Doom 3 GPL Source Code
Copyright (C) 1999-2011 id Software LLC, a ZeniMax Media company.
Translated to Kotlin by Dr. Feederino with support of Claude Code

This file is part of the Doom 3 GPL Source Code ("Doom 3 Source Code").
Original source: neo/renderer/draw_common.cpp

===========================================================================
*/

package neo.Renderer

import neo.Renderer.Material.cullType_t
import neo.Renderer.Material.idMaterial
import neo.Renderer.Material.materialCoverage_t
import neo.Renderer.Material.shaderStage_t
import neo.Renderer.Material.stageLighting_t
import neo.Renderer.Material.stageVertexColor_t
import neo.Renderer.Material.texgen_t
import neo.Renderer.Model.SHADOW_CAP_INFINITE
import neo.Renderer.Model.shadowCache_s
import neo.Renderer.Model.srfTriangles_s
import neo.Renderer.VertexCache.vertexCache
import neo.Renderer.qgl.qglColor3f
import neo.Renderer.qgl.qglDepthBoundsEXT
import neo.Renderer.qgl.qglDisable
import neo.Renderer.qgl.qglEnable
import neo.Renderer.qgl.qglProgramEnvParameter4fvARB
import neo.Renderer.qgl.qglStencilOp
import neo.Renderer.qgl.qglVertexPointer
import neo.Renderer.tr_backend.GL_Cull
import neo.Renderer.tr_main.R_GlobalPointToLocal
import neo.Renderer.tr_render.RB_DrawShadowElementsWithCounters
import neo.Renderer.tr_render.triFunc
import neo.framework.Common
import neo.idlib.containers.CInt
import neo.idlib.geometry.DrawVert.idDrawVert
import neo.idlib.math.idPlane
import neo.idlib.math.idVec3
import neo.idlib.math.idVec4
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.ARBFragmentProgram
import org.lwjgl.opengl.ARBTextureEnvCombine
import org.lwjgl.opengl.ARBVertexProgram.GL_VERTEX_PROGRAM_ARB
import org.lwjgl.opengl.EXTDepthBoundsTest
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL13
import org.lwjgl.opengl.GL20C.glStencilOpSeparate
import java.util.*
import kotlin.math.abs

object draw_common {
    /*
     =====================
     RB_BakeTextureMatrixIntoTexgen
     =====================
     */
    //========================================================================
    private val fogPlanes = idPlane.generateArray(4)

    /*
     ================
     RB_FinishStageTexturing
     ================
     */
    private var DBG_RB_FinishStageTexturing = 0

    /*
     ==================
     RB_STD_T_RenderShaderPasses

     This is also called for the generated 2D rendering
     ==================
     */
    private var DBG_RB_STD_T_RenderShaderPasses = 0
    private const val DBG_hasMatrix = 0
    fun RB_BakeTextureMatrixIntoTexgen( /*idPlane[]*/lightProject: Array<idVec4> /*[3]*/, textureMatrix: FloatArray?) {
        val genMatrix = FloatArray(16)
        val finale = FloatArray(16)
        genMatrix[0] = lightProject[0][0]
        genMatrix[4] = lightProject[0][1]
        genMatrix[8] = lightProject[0][2]
        genMatrix[12] = lightProject[0][3]
        genMatrix[1] = lightProject[1][0]
        genMatrix[5] = lightProject[1][1]
        genMatrix[9] = lightProject[1][2]
        genMatrix[13] = lightProject[1][3]
        genMatrix[2] = 0.0f
        genMatrix[6] = 0.0f
        genMatrix[10] = 0.0f
        genMatrix[14] = 0.0f
        genMatrix[3] = lightProject[2][0]
        genMatrix[7] = lightProject[2][1]
        genMatrix[11] = lightProject[2][2]
        genMatrix[15] = lightProject[2][3]
        tr_main.myGlMultMatrix(genMatrix, backEnd!!.lightTextureMatrix, finale)
        lightProject[0][0] = finale[0]
        lightProject[0][1] = finale[4]
        lightProject[0][2] = finale[8]
        lightProject[0][3] = finale[12]
        lightProject[1][0] = finale[1]
        lightProject[1][1] = finale[5]
        lightProject[1][2] = finale[9]
        lightProject[1][3] = finale[13]
    }

    /*
     ================
     RB_PrepareStageTexturing
     ================
     */
    fun RB_PrepareStageTexturing(pStage: shaderStage_t?, surf: drawSurf_s, ac: idDrawVert) {
        // set privatePolygonOffset if necessary
        if (pStage!!.privatePolygonOffset != 0.0f) {
            qglEnable(GL_POLYGON_OFFSET_FILL)
            qgl.qglPolygonOffset(
                r_offsetFactor.GetFloat(),
                r_offsetUnits.GetFloat() * pStage.privatePolygonOffset
            )
        }

        // set the texture matrix if needed
        if (pStage.texture.hasMatrix) {
            tr_render.RB_LoadShaderTextureMatrix(surf.shaderRegisters, pStage.texture)
        }

        // texgens
        if (pStage.texture.texgen == texgen_t.TG_DIFFUSE_CUBE) {
            qgl.qglTexCoordPointer(3, GL_FLOAT, idDrawVert.BYTES, ac.normalOffset().toLong())
        }
        if (pStage.texture.texgen == texgen_t.TG_SKYBOX_CUBE || pStage.texture.texgen == texgen_t.TG_WOBBLESKY_CUBE) {
            val texPos = vertexCache.Position(surf.dynamicTexCoords)
            if (vertexCache.IsVBOOffset(texPos)) {
                qgl.qglTexCoordPointer(3, GL_FLOAT, 0, vertexCache.GetVBOOffset(texPos))
            } else {
                qgl.qglTexCoordPointer(3, GL_FLOAT, 0, texPos)
            }
        }
        if (pStage.texture.texgen == texgen_t.TG_SCREEN) {
            qglEnable(GL_TEXTURE_GEN_S)
            qglEnable(GL_TEXTURE_GEN_T)
            qglEnable(GL_TEXTURE_GEN_Q)
            val mat = FloatArray(16)
            val plane = FloatArray(4)
            tr_main.myGlMultMatrix(surf.space!!.modelViewMatrix, backEnd!!.viewDef!!.projectionMatrix, mat)
            plane[0] = mat[0]
            plane[1] = mat[4]
            plane[2] = mat[8]
            plane[3] = mat[12]
            qgl.qglTexGenfv(GL_S, GL_OBJECT_PLANE, plane)
            plane[0] = mat[1]
            plane[1] = mat[5]
            plane[2] = mat[9]
            plane[3] = mat[13]
            qgl.qglTexGenfv(GL_T, GL_OBJECT_PLANE, plane)
            plane[0] = mat[3]
            plane[1] = mat[7]
            plane[2] = mat[11]
            plane[3] = mat[15]
            qgl.qglTexGenfv(GL_Q, GL_OBJECT_PLANE, plane)
        }
        if (pStage.texture.texgen == texgen_t.TG_SCREEN2) {
            qglEnable(GL_TEXTURE_GEN_S)
            qglEnable(GL_TEXTURE_GEN_T)
            qglEnable(GL_TEXTURE_GEN_Q)
            val mat = FloatArray(16)
            val plane = FloatArray(4)
            tr_main.myGlMultMatrix(surf.space!!.modelViewMatrix, backEnd!!.viewDef!!.projectionMatrix, mat)
            plane[0] = mat[0]
            plane[1] = mat[4]
            plane[2] = mat[8]
            plane[3] = mat[12]
            qgl.qglTexGenfv(GL_S, GL_OBJECT_PLANE, plane)
            plane[0] = mat[1]
            plane[1] = mat[5]
            plane[2] = mat[9]
            plane[3] = mat[13]
            qgl.qglTexGenfv(GL_T, GL_OBJECT_PLANE, plane)
            plane[0] = mat[3]
            plane[1] = mat[7]
            plane[2] = mat[11]
            plane[3] = mat[15]
            qgl.qglTexGenfv(GL_Q, GL_OBJECT_PLANE, plane)
        }
        if (pStage.texture.texgen == texgen_t.TG_GLASSWARP) {
            if (tr.backEndRenderer == backEndName_t.BE_ARB2 /*|| tr.backEndRenderer == BE_NV30*/) {
                qgl.qglBindProgramARB(ARBFragmentProgram.GL_FRAGMENT_PROGRAM_ARB, program_t.FPROG_GLASSWARP)
                qglEnable(ARBFragmentProgram.GL_FRAGMENT_PROGRAM_ARB)
                tr_backend.GL_SelectTexture(2)
                Image.globalImages.scratchImage!!.Bind()
                tr_backend.GL_SelectTexture(1)
                Image.globalImages.scratchImage2!!.Bind()
                qglEnable(GL_TEXTURE_GEN_S)
                qglEnable(GL_TEXTURE_GEN_T)
                qglEnable(GL_TEXTURE_GEN_Q)
                val mat = FloatArray(16)
                val plane = FloatArray(4)
                tr_main.myGlMultMatrix(surf.space!!.modelViewMatrix, backEnd!!.viewDef!!.projectionMatrix, mat)
                plane[0] = mat[0]
                plane[1] = mat[4]
                plane[2] = mat[8]
                plane[3] = mat[12]
                qgl.qglTexGenfv(GL_S, GL_OBJECT_PLANE, plane)
                plane[0] = mat[1]
                plane[1] = mat[5]
                plane[2] = mat[9]
                plane[3] = mat[13]
                qgl.qglTexGenfv(GL_T, GL_OBJECT_PLANE, plane)
                plane[0] = mat[3]
                plane[1] = mat[7]
                plane[2] = mat[11]
                plane[3] = mat[15]
                qgl.qglTexGenfv(GL_Q, GL_OBJECT_PLANE, plane)
                tr_backend.GL_SelectTexture(0)
            }
        }
        if (pStage.texture.texgen == texgen_t.TG_REFLECT_CUBE) {
            if (tr.backEndRenderer == backEndName_t.BE_ARB2) {
                // see if there is also a bump map specified
                val bumpStage = surf.material!!.GetBumpStage()
                if (bumpStage != null) {
                    // per-pixel reflection mapping with bump mapping
                    tr_backend.GL_SelectTexture(1)
                    bumpStage.texture.image!![0]!!.Bind()
                    tr_backend.GL_SelectTexture(0)
                    qgl.qglNormalPointer(GL_FLOAT, idDrawVert.BYTES, ac.normalOffset().toLong())
                    qgl.qglVertexAttribPointerARB(
                        10,
                        3,
                        GL_FLOAT,
                        false,
                        idDrawVert.BYTES,
                        ac.tangentsOffset_1().toLong()
                    )
                    qgl.qglVertexAttribPointerARB(
                        9,
                        3,
                        GL_FLOAT,
                        false,
                        idDrawVert.BYTES,
                        ac.tangentsOffset_0().toLong()
                    )
                    qgl.qglEnableVertexAttribArrayARB(9)
                    qgl.qglEnableVertexAttribArrayARB(10)
                    qgl.qglEnableClientState(GL_NORMAL_ARRAY)

                    // Program env 5, 6, 7, 8 have been set in RB_SetProgramEnvironmentSpace
                    qgl.qglBindProgramARB(ARBFragmentProgram.GL_FRAGMENT_PROGRAM_ARB, program_t.FPROG_BUMPY_ENVIRONMENT)
                    qglEnable(ARBFragmentProgram.GL_FRAGMENT_PROGRAM_ARB)
                    qgl.qglBindProgramARB(GL_VERTEX_PROGRAM_ARB, program_t.VPROG_BUMPY_ENVIRONMENT)
                    qglEnable(GL_VERTEX_PROGRAM_ARB)
                } else {
                    // per-pixel reflection mapping without a normal map
                    qgl.qglNormalPointer(GL_FLOAT, idDrawVert.BYTES, ac.normalOffset().toLong())
                    qgl.qglEnableClientState(GL_NORMAL_ARRAY)
                    qgl.qglBindProgramARB(ARBFragmentProgram.GL_FRAGMENT_PROGRAM_ARB, program_t.FPROG_ENVIRONMENT)
                    qglEnable(ARBFragmentProgram.GL_FRAGMENT_PROGRAM_ARB)
                    qgl.qglBindProgramARB(GL_VERTEX_PROGRAM_ARB, program_t.VPROG_ENVIRONMENT)
                    qglEnable(GL_VERTEX_PROGRAM_ARB)
                }
            } else {
                qglEnable(GL_TEXTURE_GEN_S)
                qglEnable(GL_TEXTURE_GEN_T)
                qglEnable(GL_TEXTURE_GEN_R)
                qgl.qglTexGenf(GL_S, GL_TEXTURE_GEN_MODE, GL13.GL_REFLECTION_MAP /*_EXT*/.toFloat())
                qgl.qglTexGenf(GL_T, GL_TEXTURE_GEN_MODE, GL13.GL_REFLECTION_MAP /*_EXT*/.toFloat())
                qgl.qglTexGenf(GL_R, GL_TEXTURE_GEN_MODE, GL13.GL_REFLECTION_MAP /*_EXT*/.toFloat())
                qgl.qglEnableClientState(GL_NORMAL_ARRAY)
                qgl.qglNormalPointer(GL_FLOAT, idDrawVert.BYTES, ac.normalOffset().toLong())
                qgl.qglMatrixMode(GL_TEXTURE)
                val mat = FloatArray(16)
                tr_main.R_TransposeGLMatrix(backEnd!!.viewDef!!.worldSpace.modelViewMatrix, mat)
                qgl.qglLoadMatrixf(mat)
                qgl.qglMatrixMode(GL_MODELVIEW)
            }
        }
    }

    fun RB_FinishStageTexturing(pStage: shaderStage_t?, surf: drawSurf_s, ac: idDrawVert) {
        DBG_RB_FinishStageTexturing++
        // unset privatePolygonOffset if necessary
        if (pStage!!.privatePolygonOffset != 0.0f && !surf.material!!.TestMaterialFlag(Material.MF_POLYGONOFFSET)) {
            qglDisable(GL_POLYGON_OFFSET_FILL)
        }
        if (pStage.texture.texgen == texgen_t.TG_DIFFUSE_CUBE || pStage.texture.texgen == texgen_t.TG_SKYBOX_CUBE || pStage.texture.texgen == texgen_t.TG_WOBBLESKY_CUBE) {
            qgl.qglTexCoordPointer(2, GL_FLOAT, idDrawVert.BYTES, ac.stOffset().toLong())
        }
        if (pStage.texture.texgen == texgen_t.TG_SCREEN) {
            qglDisable(GL_TEXTURE_GEN_S)
            qglDisable(GL_TEXTURE_GEN_T)
            qglDisable(GL_TEXTURE_GEN_Q)
        }
        if (pStage.texture.texgen == texgen_t.TG_SCREEN2) {
            qglDisable(GL_TEXTURE_GEN_S)
            qglDisable(GL_TEXTURE_GEN_T)
            qglDisable(GL_TEXTURE_GEN_Q)
        }
        if (pStage.texture.texgen == texgen_t.TG_GLASSWARP) {
            if (tr.backEndRenderer == backEndName_t.BE_ARB2 /*|| tr.backEndRenderer == BE_NV30*/) {
                tr_backend.GL_SelectTexture(2)
                Image.globalImages.BindNull()
                tr_backend.GL_SelectTexture(1)
                if (pStage.texture.hasMatrix) {
                    tr_render.RB_LoadShaderTextureMatrix(surf.shaderRegisters, pStage.texture)
                }
                qglDisable(GL_TEXTURE_GEN_S)
                qglDisable(GL_TEXTURE_GEN_T)
                qglDisable(GL_TEXTURE_GEN_Q)
                qglDisable(ARBFragmentProgram.GL_FRAGMENT_PROGRAM_ARB)
                Image.globalImages.BindNull()
                tr_backend.GL_SelectTexture(0)
            }
        }
        if (pStage.texture.texgen == texgen_t.TG_REFLECT_CUBE) {
            if (tr.backEndRenderer == backEndName_t.BE_ARB2) {
                // see if there is also a bump map specified
                val bumpStage = surf.material!!.GetBumpStage()
                if (bumpStage != null) {
                    // per-pixel reflection mapping with bump mapping
                    tr_backend.GL_SelectTexture(1)
                    Image.globalImages.BindNull()
                    tr_backend.GL_SelectTexture(0)
                    qgl.qglDisableVertexAttribArrayARB(9)
                    qgl.qglDisableVertexAttribArrayARB(10)
                } else {
                    // per-pixel reflection mapping without bump mapping
                }
                qgl.qglDisableClientState(GL_NORMAL_ARRAY)
                qglDisable(ARBFragmentProgram.GL_FRAGMENT_PROGRAM_ARB)
                qglDisable(GL_VERTEX_PROGRAM_ARB)
                // Fixme: Hack to get around an apparent bug in ATI drivers.  Should remove as soon as it gets fixed.
                qgl.qglBindProgramARB(GL_VERTEX_PROGRAM_ARB, 0)
            } else {
                qglDisable(GL_TEXTURE_GEN_S)
                qglDisable(GL_TEXTURE_GEN_T)
                qglDisable(GL_TEXTURE_GEN_R)
                qgl.qglTexGenf(GL_S, GL_TEXTURE_GEN_MODE, GL_OBJECT_LINEAR.toFloat())
                qgl.qglTexGenf(GL_T, GL_TEXTURE_GEN_MODE, GL_OBJECT_LINEAR.toFloat())
                qgl.qglTexGenf(GL_R, GL_TEXTURE_GEN_MODE, GL_OBJECT_LINEAR.toFloat())
                qgl.qglDisableClientState(GL_NORMAL_ARRAY)
                qgl.qglMatrixMode(GL_TEXTURE)
                qgl.qglLoadIdentity()
                qgl.qglMatrixMode(GL_MODELVIEW)
            }
        }
        if (pStage.texture.hasMatrix) {
            qgl.qglMatrixMode(GL_TEXTURE)
            qgl.qglLoadIdentity()
            qgl.qglMatrixMode(GL_MODELVIEW)
        }
    }

    /*
     =============================================================================================

     SHADER PASSES

     =============================================================================================
     */
    /*
     =====================
     RB_STD_FillDepthBuffer

     If we are rendering a subview with a near clip plane, use a second texture
     to force the alpha test to fail when behind that clip plane
     =====================
     */
    fun RB_STD_FillDepthBuffer(drawSurfs: Array<drawSurf_s>?, numDrawSurfs: Int) {
        // if we are just doing 2D rendering, no need to fill the depth buffer
        if (backEnd!!.viewDef!!.viewEntitys == null) {
            return
        }

        // enable the second texture for mirror plane clipping if needed
        if (backEnd!!.viewDef!!.numClipPlanes != 0) {
            tr_backend.GL_SelectTexture(1)
            Image.globalImages.alphaNotchImage!!.Bind()
            qgl.qglDisableClientState(GL_TEXTURE_COORD_ARRAY)
            qglEnable(GL_TEXTURE_GEN_S)
            qgl.qglTexCoord2f(1.0f, 0.5f)
        }

        // the first texture will be used for alpha tested surfaces
        tr_backend.GL_SelectTexture(0)
        qgl.qglEnableClientState(GL_TEXTURE_COORD_ARRAY)

        // decal surfaces may enable polygon offset
        qgl.qglPolygonOffset(
            r_offsetFactor!!.GetFloat(),
            r_offsetUnits!!.GetFloat()
        )
        tr_backend.GL_State(GLS_DEPTHFUNC_LESS)

        // Enable stencil test if we are going to be using it for shadows.
        // If we didn't do this, it would be legal behavior to get z fighting
        // from the ambient pass and the light passes.
        qglEnable(GL_STENCIL_TEST)
        qgl.qglStencilFunc(GL_ALWAYS, 1, 255)
        tr_render.RB_RenderDrawSurfListWithFunction(drawSurfs!!, numDrawSurfs, RB_T_FillDepthBuffer.INSTANCE)

        // DG: #3877 capture depth buffer for soft particles
        val getDepthCapture = r_enableDepthCapture.GetInteger() == 1
                || (r_enableDepthCapture.GetInteger() == -1 && r_useSoftParticles.GetBool())
        if (getDepthCapture) {
            Image.globalImages.currentDepthImage?.CopyDepthbuffer(
                backEnd!!.viewDef!!.viewport.x1,
                backEnd!!.viewDef!!.viewport.y1,
                backEnd!!.viewDef!!.viewport.x2 - backEnd!!.viewDef!!.viewport.x1 + 1,
                backEnd!!.viewDef!!.viewport.y2 - backEnd!!.viewDef!!.viewport.y1 + 1
            )
        }

        if (backEnd!!.viewDef!!.numClipPlanes != 0) {
            tr_backend.GL_SelectTexture(1)
            Image.globalImages.BindNull()
            qglDisable(GL_TEXTURE_GEN_S)
            tr_backend.GL_SelectTexture(0)
        }
    }

    /*
     ==================
     RB_SetProgramEnvironment

     Sets variables that can be used by all vertex programs
     ==================
     */
    fun RB_SetProgramEnvironment(isPostProcess: Boolean = false) {
        val parm = BufferUtils.createFloatBuffer(4)
        var pot: Int
        if (!glConfig.ARBVertexProgramAvailable) {
            return
        }

//if (false){
//	// screen power of two correction factor, one pixel in so we don't get a bilerp
//	// of an uncopied pixel
//	int	 w = backEnd!!.viewDef!!.viewport.x2 - backEnd!!.viewDef!!.viewport.x1 + 1;
//	pot = globalImages.currentRenderImage.uploadWidth;
//	if ( w == pot ) {
//		parm0[0] = 1.0f;
//	} else {
//		parm0[0] = (float)(w-1) / pot;
//	}
//
//	int	 h = backEnd!!.viewDef!!.viewport.y2 - backEnd!!.viewDef!!.viewport.y1 + 1;
//	pot = globalImages.currentRenderImage.uploadHeight;
//	if ( h == pot ) {
//		parm0[1] = 1.0f;
//	} else {
//		parm0[1] = (float)(h-1) / pot;
//	}
//
//	parm0[2] = 0;
//	parm0[3] = 1;
//	qglProgramEnvParameter4fvARB( GL_VERTEX_PROGRAM_ARB, 0, parm0 );
//}else{
        // screen power of two correction factor, assuming the copy to _currentRender
        // also copied an extra row and column for the bilerp
        val w = backEnd!!.viewDef!!.viewport.x2 - backEnd!!.viewDef!!.viewport.x1 + 1
        pot = Image.globalImages.currentRenderImage!!.uploadWidth._val
        parm.put(0, w.toFloat() / pot)
        val h = backEnd!!.viewDef!!.viewport.y2 - backEnd!!.viewDef!!.viewport.y1 + 1
        pot = Image.globalImages.currentRenderImage!!.uploadHeight._val
        parm.put(1, h.toFloat() / pot)
        parm.put(2, 0.0f)
        parm.put(3, 1.0f)
        qglProgramEnvParameter4fvARB(GL_VERTEX_PROGRAM_ARB, 0, parm)
        //}
        qglProgramEnvParameter4fvARB(ARBFragmentProgram.GL_FRAGMENT_PROGRAM_ARB, 0, parm)

        // window coord to 0.0f to 1.0f conversion
        parm.put(0, 1.0f / w)
        parm.put(1, 1.0f / h)
        parm.put(2, 0.0f)
        parm.put(3, 1.0f)
        qglProgramEnvParameter4fvARB(ARBFragmentProgram.GL_FRAGMENT_PROGRAM_ARB, 1, parm)

        // DG: brightness and gamma in shader as program.env[PP_GAMMA_BRIGHTNESS] (= 21)
        if (r_gammaInShader.GetBool()) {
            // program.env[PP_GAMMA_BRIGHTNESS].xyz are all r_brightness, .w is 1.0/r_gamma
            if (!isPostProcess) {
                val brightness = r_brightness.GetFloat()
                parm.put(0, brightness)
                parm.put(1, brightness)
                parm.put(2, brightness)
                parm.put(3, 1.0f / r_gamma.GetFloat())
            } else {
                // don't apply gamma/brightness in postprocess passes to avoid applying them twice
                // (setting them to 1.0 makes them no-ops)
                parm.put(0, 1.0f)
                parm.put(1, 1.0f)
                parm.put(2, 1.0f)
                parm.put(3, 1.0f)
            }
            qglProgramEnvParameter4fvARB(
                ARBFragmentProgram.GL_FRAGMENT_PROGRAM_ARB,
                programParameter_t.PP_GAMMA_BRIGHTNESS,
                parm
            )
        }

        // DG: #3877 depth image reciprocal for soft particles
        val depthImg = Image.globalImages.currentDepthImage
        if (depthImg != null) {
            val dw = depthImg.uploadWidth._val
            val dh = depthImg.uploadHeight._val
            val crw = Image.globalImages.currentRenderImage!!.uploadWidth._val
            val crh = Image.globalImages.currentRenderImage!!.uploadHeight._val
            if (dw > 0 && dh > 0) {
                parm.put(0, 1.0f / dw)
                parm.put(1, 1.0f / dh)
                parm.put(2, dw.toFloat() / crw.toFloat())
                parm.put(3, dh.toFloat() / crh.toFloat())
                qglProgramEnvParameter4fvARB(
                    ARBFragmentProgram.GL_FRAGMENT_PROGRAM_ARB,
                    programParameter_t.PP_CURDEPTH_RECIPR,
                    parm
                )
            }
        }

        //
        // set eye position in global space
        //
        parm.put(0, backEnd!!.viewDef!!.renderView.vieworg[0])
        parm.put(1, backEnd!!.viewDef!!.renderView.vieworg[1])
        parm.put(2, backEnd!!.viewDef!!.renderView.vieworg[2])
        parm.put(3, 1.0f)
        qglProgramEnvParameter4fvARB(GL_VERTEX_PROGRAM_ARB, 1, parm)
    }

    /*
     ==================
     RB_SetProgramEnvironmentSpace

     Sets variables related to the current space that can be used by all vertex programs
     ==================
     */
    fun RB_SetProgramEnvironmentSpace() {
        if (!glConfig.ARBVertexProgramAvailable) {
            return
        }
        val space = backEnd!!.currentSpace!!
        val parm = BufferUtils.createFloatBuffer(4)

        // set eye position in local space
        R_GlobalPointToLocal(space.modelMatrix, backEnd!!.viewDef!!.renderView.vieworg, parm)
        parm.put(3, 1.0f)
        qglProgramEnvParameter4fvARB(GL_VERTEX_PROGRAM_ARB, 5, parm)

        // we need the model matrix without it being combined with the view matrix
        // so we can transform local vectors to global coordinates
        parm.put(0, space.modelMatrix[0])
        parm.put(1, space.modelMatrix[4])
        parm.put(2, space.modelMatrix[8])
        parm.put(3, space.modelMatrix[12])
        qglProgramEnvParameter4fvARB(GL_VERTEX_PROGRAM_ARB, 6, parm)
        parm.put(0, space.modelMatrix[1])
        parm.put(1, space.modelMatrix[5])
        parm.put(2, space.modelMatrix[9])
        parm.put(3, space.modelMatrix[13])
        qglProgramEnvParameter4fvARB(GL_VERTEX_PROGRAM_ARB, 7, parm)
        parm.put(0, space.modelMatrix[2])
        parm.put(1, space.modelMatrix[6])
        parm.put(2, space.modelMatrix[10])
        parm.put(3, space.modelMatrix[14])
        qglProgramEnvParameter4fvARB(GL_VERTEX_PROGRAM_ARB, 8, parm)
    }

    fun RB_STD_T_RenderShaderPasses(surf: drawSurf_s) {
        var stage: Int
        DBG_RB_STD_T_RenderShaderPasses++
        val shader: idMaterial
        var pStage: shaderStage_t?
        val regs: FloatArray
        val color = BufferUtils.createFloatBuffer(4)
        val tri: srfTriangles_s
        tri = surf.geo!!
        shader = surf.material!!
        if (!shader.HasAmbient()) {
            return
        }
        if (shader.IsPortalSky()) {
            return
        }

        // change the matrix if needed
        if (surf.space !== backEnd!!.currentSpace) {
            qgl.qglLoadMatrixf(surf.space!!.modelViewMatrix)
            backEnd!!.currentSpace = surf.space
            RB_SetProgramEnvironmentSpace()
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

        // some deforms may disable themselves by setting numIndexes = 0
        if (0 == tri.numIndexes) {
            return
        }
        if (tri.ambientCache == null) {
            Common.common.Printf("RB_T_RenderShaderPasses: !tri.ambientCache\n")
            return
        }

        // get the expressions for conditionals / color / texcoords
        regs = surf.shaderRegisters!!

        // DG: #3878 soft particles
        val soft_particle = (surf.dsFlags and DSF_SOFT_PARTICLE) != 0

        // set face culling appropriately
        GL_Cull(shader.GetCullType()!!)

        // set polygon offset if necessary
        if (shader.TestMaterialFlag(Material.MF_POLYGONOFFSET)) {
            qglEnable(GL_POLYGON_OFFSET_FILL)
            qgl.qglPolygonOffset(
                r_offsetFactor!!.GetFloat(),
                r_offsetUnits!!.GetFloat() * shader.GetPolygonOffset()
            )
        }
        if (surf.space!!.weaponDepthHack) {
            tr_render.RB_EnterWeaponDepthHack()
        }
        if (surf.space!!.modelDepthHack != 0.0f && !soft_particle) { // #3878 soft particles don't want modelDepthHack
            tr_render.RB_EnterModelDepthHack(surf.space!!.modelDepthHack)
        }
        val ac =
            idDrawVert(vertexCache.Position(tri.ambientCache))
        qglVertexPointer(3, GL_FLOAT, idDrawVert.BYTES, ac.xyzOffset().toLong())
        qgl.qglTexCoordPointer(2, GL_FLOAT, idDrawVert.BYTES, ac.stOffset().toLong())
        stage = 0
        while (stage < shader.GetNumStages()) {
            pStage = shader.GetStage(stage)

            // check the enable condition
            if (regs[pStage!!.conditionRegister] == 0.0f) {
                stage++
                continue
            }

            // skip the stages involved in lighting
            if (pStage.lighting != stageLighting_t.SL_AMBIENT) {
                stage++
                continue
            }

            // skip if the stage is ( GL_ZERO, GL_ONE ), which is used for some alpha masks
            if (pStage.drawStateBits and (GLS_SRCBLEND_BITS or GLS_DSTBLEND_BITS) == (GLS_SRCBLEND_ZERO or GLS_DSTBLEND_ONE)) {
                stage++
                continue
            }

            // DG: #3878 extract src_blend for soft particle check
            val src_blend = pStage.drawStateBits and GLS_SRCBLEND_BITS

            // see if we are a new-style stage
            val newStage = pStage.newStage
            if (newStage != null) {
                //--------------------------
                //
                // new style stages
                //
                //--------------------------

                // completely skip the stage if we don't have the capability
                if (tr.backEndRenderer != backEndName_t.BE_ARB2) {
                    stage++
                    continue
                }
                if (r_skipNewAmbient!!.GetBool()) {
                    stage++
                    continue
                }
                qgl.qglColorPointer(4, GL_UNSIGNED_BYTE, idDrawVert.BYTES, ac.colorOffset().toLong())
                qgl.qglVertexAttribPointerARB(
                    9,
                    3,
                    GL_FLOAT,
                    false,
                    idDrawVert.BYTES,
                    ac.tangentsOffset_0().toLong()
                )
                qgl.qglVertexAttribPointerARB(
                    10,
                    3,
                    GL_FLOAT,
                    false,
                    idDrawVert.BYTES,
                    ac.tangentsOffset_1().toLong()
                )
                qgl.qglNormalPointer(GL_FLOAT, idDrawVert.BYTES, ac.normalOffset().toLong())
                qgl.qglEnableClientState(GL_COLOR_ARRAY)
                qgl.qglEnableVertexAttribArrayARB(9)
                qgl.qglEnableVertexAttribArrayARB(10)
                qgl.qglEnableClientState(GL_NORMAL_ARRAY)
                tr_backend.GL_State(pStage.drawStateBits)
                qgl.qglBindProgramARB(GL_VERTEX_PROGRAM_ARB, newStage.vertexProgram)
                qglEnable(GL_VERTEX_PROGRAM_ARB)

                // megaTextures bind a lot of images and set a lot of parameters
                if (newStage.megaTexture != null) {
                    newStage.megaTexture!!.SetMappingForSurface(tri)
                    val localViewer = idVec3()
                    R_GlobalPointToLocal(
                        surf.space!!.modelMatrix,
                        backEnd!!.viewDef!!.renderView.vieworg,
                        localViewer
                    )
                    newStage.megaTexture!!.BindForViewOrigin(localViewer)
                }
                for (i in 0 until newStage.numVertexParms) {
                    val parm = BufferUtils.createFloatBuffer(4)
                    parm.put(0, regs[newStage.vertexParms[i]!![0]])
                    parm.put(1, regs[newStage.vertexParms[i]!![1]])
                    parm.put(2, regs[newStage.vertexParms[i]!![2]])
                    parm.put(3, regs[newStage.vertexParms[i]!![3]])
                    qgl.qglProgramLocalParameter4fvARB(GL_VERTEX_PROGRAM_ARB, i, parm)
                }
                for (i in 0 until newStage.numFragmentProgramImages) {
                    if (newStage.fragmentProgramImages[i] != null) {
                        tr_backend.GL_SelectTexture(i)
                        newStage.fragmentProgramImages[i]!!.Bind()
                    }
                }
                qgl.qglBindProgramARB(ARBFragmentProgram.GL_FRAGMENT_PROGRAM_ARB, newStage.fragmentProgram)
                qglEnable(ARBFragmentProgram.GL_FRAGMENT_PROGRAM_ARB)

                // draw it
                tr_render.RB_DrawElementsWithCounters(tri)
                for (i in 1 until newStage.numFragmentProgramImages) {
                    if (newStage.fragmentProgramImages[i] != null) {
                        tr_backend.GL_SelectTexture(i)
                        Image.globalImages.BindNull()
                    }
                }
                if (newStage.megaTexture != null) {
                    newStage.megaTexture!!.Unbind()
                }
                tr_backend.GL_SelectTexture(0)
                qglDisable(GL_VERTEX_PROGRAM_ARB)
                qglDisable(ARBFragmentProgram.GL_FRAGMENT_PROGRAM_ARB)
                // Fixme: Hack to get around an apparent bug in ATI drivers.  Should remove as soon as it gets fixed.
                qgl.qglBindProgramARB(GL_VERTEX_PROGRAM_ARB, 0)
                qgl.qglDisableClientState(GL_COLOR_ARRAY)
                qgl.qglDisableVertexAttribArrayARB(9)
                qgl.qglDisableVertexAttribArrayARB(10)
                qgl.qglDisableClientState(GL_NORMAL_ARRAY)
                stage++
                continue
            }

            // DG: #3878 Soft particle rendering path
            // Particles are automatically softened by the engine, unless they have shader programs
            // of their own (i.e. are "newstages" handled above).
            if (soft_particle
                && surf.particle_radius > 0.0f
                && (src_blend == GLS_SRCBLEND_ONE || src_blend == GLS_SRCBLEND_SRC_ALPHA)
                && !r_skipNewAmbient!!.GetBool()
            ) {
                if (pStage.vertexColor == stageVertexColor_t.SVC_IGNORE) {
                    // Ignoring vertexColor is not recommended for particles. Default to material color.
                    color.put(0, regs[pStage.color.registers[0]])
                    color.put(1, regs[pStage.color.registers[1]])
                    color.put(2, regs[pStage.color.registers[2]])
                    color.put(3, regs[pStage.color.registers[3]])
                    glColor4fv(color)
                } else {
                    // A properly set-up particle shader
                    qgl.qglColorPointer(4, GL_UNSIGNED_BYTE, idDrawVert.BYTES, ac.colorOffset().toLong())
                    qgl.qglEnableClientState(GL_COLOR_ARRAY)
                }

                // Disable depth clipping. The fragment program will handle it to allow overdraw.
                tr_backend.GL_State(pStage.drawStateBits or GLS_DEPTHFUNC_ALWAYS)

                qgl.qglBindProgramARB(GL_VERTEX_PROGRAM_ARB, program_t.VPROG_SOFT_PARTICLE.ordinal)
                qglEnable(GL_VERTEX_PROGRAM_ARB)

                // Bind image and _currentDepth
                tr_backend.GL_SelectTexture(0)
                pStage.texture.image[0]!!.Bind()
                tr_backend.GL_SelectTexture(1)
                Image.globalImages.currentDepthImage!!.Bind()

                qgl.qglBindProgramARB(ARBFragmentProgram.GL_FRAGMENT_PROGRAM_ARB, program_t.FPROG_SOFT_PARTICLE.ordinal)
                qglEnable(ARBFragmentProgram.GL_FRAGMENT_PROGRAM_ARB)

                // Texture matrix support
                val texMatrix = Array(2) { idVec4() }
                if (pStage.texture.hasMatrix) {
                    texMatrix[0][0] = regs[pStage.texture.matrix[0][0]]
                    texMatrix[0][1] = regs[pStage.texture.matrix[0][1]]
                    texMatrix[0][2] = 0.0f
                    texMatrix[0][3] = regs[pStage.texture.matrix[0][2]]

                    texMatrix[1][0] = regs[pStage.texture.matrix[1][0]]
                    texMatrix[1][1] = regs[pStage.texture.matrix[1][1]]
                    texMatrix[1][2] = 0.0f
                    texMatrix[1][3] = regs[pStage.texture.matrix[1][2]]

                    if (texMatrix[0][3] < -40 || texMatrix[0][3] > 40) {
                        texMatrix[0][3] -= texMatrix[0][3].toInt()
                    }
                    if (texMatrix[1][3] < -40 || texMatrix[1][3] > 40) {
                        texMatrix[1][3] -= texMatrix[1][3].toInt()
                    }
                } else {
                    texMatrix[0].set(1f, 0f, 0f, 0f)
                    texMatrix[1].set(0f, 1f, 0f, 0f)
                }
                val parm4 = BufferUtils.createFloatBuffer(4)
                parm4.put(0, texMatrix[0][0]); parm4.put(1, texMatrix[0][1]); parm4.put(
                    2,
                    texMatrix[0][2]
                ); parm4.put(3, texMatrix[0][3])
                qglProgramEnvParameter4fvARB(GL_VERTEX_PROGRAM_ARB, programParameter_t.PP_DIFFUSE_MATRIX_S, parm4)
                parm4.put(0, texMatrix[1][0]); parm4.put(1, texMatrix[1][1]); parm4.put(
                    2,
                    texMatrix[1][2]
                ); parm4.put(3, texMatrix[1][3])
                qglProgramEnvParameter4fvARB(GL_VERTEX_PROGRAM_ARB, programParameter_t.PP_DIFFUSE_MATRIX_T, parm4)

                // program.env[23] is the particle radius, given as { radius, 1/(fadeRange), 1/radius }
                var fadeRange = 1.0f
                if (src_blend == GLS_SRCBLEND_SRC_ALPHA) { // alpha blend
                    fadeRange = surf.particle_radius * 2.0f
                } else if (src_blend == GLS_SRCBLEND_ONE) { // additive blend
                    fadeRange = surf.particle_radius
                }
                parm4.put(0, surf.particle_radius)
                parm4.put(1, 1.0f / fadeRange)
                parm4.put(2, 1.0f / surf.particle_radius)
                parm4.put(3, 0.0f)
                qglProgramEnvParameter4fvARB(
                    ARBFragmentProgram.GL_FRAGMENT_PROGRAM_ARB,
                    programParameter_t.PP_PARTICLE_RADIUS,
                    parm4
                )

                // program.env[24] is the color channel mask
                if (src_blend == GLS_SRCBLEND_SRC_ALPHA) { // alpha blend
                    parm4.put(0, 1.0f); parm4.put(1, 1.0f); parm4.put(2, 1.0f); parm4.put(3, 0.0f)
                } else if (src_blend == GLS_SRCBLEND_ONE) { // additive blend
                    parm4.put(0, 0.0f); parm4.put(1, 0.0f); parm4.put(2, 0.0f); parm4.put(3, 1.0f)
                }
                qglProgramEnvParameter4fvARB(
                    ARBFragmentProgram.GL_FRAGMENT_PROGRAM_ARB,
                    programParameter_t.PP_PARTICLE_COLCHAN_MASK,
                    parm4
                )

                // draw it
                tr_render.RB_DrawElementsWithCounters(tri)

                // Clean up GL state
                tr_backend.GL_SelectTexture(1)
                Image.globalImages.BindNull()
                tr_backend.GL_SelectTexture(0)
                Image.globalImages.BindNull()

                qglDisable(GL_VERTEX_PROGRAM_ARB)
                qglDisable(ARBFragmentProgram.GL_FRAGMENT_PROGRAM_ARB)

                if (pStage.vertexColor != stageVertexColor_t.SVC_IGNORE) {
                    qgl.qglDisableClientState(GL_COLOR_ARRAY)
                }
                stage++
                continue
            }

            //--------------------------
            //
            // old style stages
            //
            //--------------------------
            // set the color
            color.put(0, regs[pStage.color.registers[0]])
            color.put(1, regs[pStage.color.registers[1]])
            color.put(2, regs[pStage.color.registers[2]])
            color.put(3, regs[pStage.color.registers[3]])

            // skip the entire stage if an add would be black
            if (pStage.drawStateBits and (GLS_SRCBLEND_BITS or GLS_DSTBLEND_BITS) == (GLS_SRCBLEND_ONE or GLS_DSTBLEND_ONE) && color[0] <= 0 && color[1] <= 0 && color[2] <= 0) {
                stage++
                continue
            }

            // skip the entire stage if a blend would be completely transparent
            if (pStage.drawStateBits and (GLS_SRCBLEND_BITS or GLS_DSTBLEND_BITS) == (GLS_SRCBLEND_SRC_ALPHA or GLS_DSTBLEND_ONE_MINUS_SRC_ALPHA)
                && color[3] <= 0
            ) {
                stage++
                continue
            }

            // select the vertex color source
            if (pStage.vertexColor == stageVertexColor_t.SVC_IGNORE) {
                qgl.qglColor4f(color[0], color[1], color[2], color[3])
            } else {
                qgl.qglColorPointer(4, GL_UNSIGNED_BYTE, idDrawVert.BYTES, ac.colorOffset().toLong())
                qgl.qglEnableClientState(GL_COLOR_ARRAY)
                if (pStage.vertexColor == stageVertexColor_t.SVC_INVERSE_MODULATE) {
                    tr_backend.GL_TexEnv(ARBTextureEnvCombine.GL_COMBINE_ARB)
                    qgl.qglTexEnvi(GL_TEXTURE_ENV, ARBTextureEnvCombine.GL_COMBINE_RGB_ARB, GL_MODULATE)
                    qgl.qglTexEnvi(GL_TEXTURE_ENV, ARBTextureEnvCombine.GL_SOURCE0_RGB_ARB, GL_TEXTURE)
                    qgl.qglTexEnvi(
                        GL_TEXTURE_ENV,
                        ARBTextureEnvCombine.GL_SOURCE1_RGB_ARB,
                        ARBTextureEnvCombine.GL_PRIMARY_COLOR_ARB
                    )
                    qgl.qglTexEnvi(GL_TEXTURE_ENV, ARBTextureEnvCombine.GL_OPERAND0_RGB_ARB, GL_SRC_COLOR)
                    qgl.qglTexEnvi(
                        GL_TEXTURE_ENV,
                        ARBTextureEnvCombine.GL_OPERAND1_RGB_ARB,
                        GL_ONE_MINUS_SRC_COLOR
                    )
                    qgl.qglTexEnvi(GL_TEXTURE_ENV, ARBTextureEnvCombine.GL_RGB_SCALE_ARB, 1)
                }

                // for vertex color and modulated color, we need to enable a second
                // texture stage
                if (color[0] != 1.0f || color[1] != 1.0f || color[2] != 1.0f || color[3] != 1.0f) {
                    tr_backend.GL_SelectTexture(1)
                    Image.globalImages.whiteImage!!.Bind()
                    tr_backend.GL_TexEnv(ARBTextureEnvCombine.GL_COMBINE_ARB)
                    qgl.qglTexEnvfv(GL_TEXTURE_ENV, GL_TEXTURE_ENV_COLOR, color)
                    qgl.qglTexEnvi(GL_TEXTURE_ENV, ARBTextureEnvCombine.GL_COMBINE_RGB_ARB, GL_MODULATE)
                    qgl.qglTexEnvi(
                        GL_TEXTURE_ENV,
                        ARBTextureEnvCombine.GL_SOURCE0_RGB_ARB,
                        ARBTextureEnvCombine.GL_PREVIOUS_ARB
                    )
                    qgl.qglTexEnvi(
                        GL_TEXTURE_ENV,
                        ARBTextureEnvCombine.GL_SOURCE1_RGB_ARB,
                        ARBTextureEnvCombine.GL_CONSTANT_ARB
                    )
                    qgl.qglTexEnvi(GL_TEXTURE_ENV, ARBTextureEnvCombine.GL_OPERAND0_RGB_ARB, GL_SRC_COLOR)
                    qgl.qglTexEnvi(GL_TEXTURE_ENV, ARBTextureEnvCombine.GL_OPERAND1_RGB_ARB, GL_SRC_COLOR)
                    qgl.qglTexEnvi(GL_TEXTURE_ENV, ARBTextureEnvCombine.GL_RGB_SCALE_ARB, 1)
                    qgl.qglTexEnvi(GL_TEXTURE_ENV, ARBTextureEnvCombine.GL_COMBINE_ALPHA_ARB, GL_MODULATE)
                    qgl.qglTexEnvi(
                        GL_TEXTURE_ENV,
                        ARBTextureEnvCombine.GL_SOURCE0_ALPHA_ARB,
                        ARBTextureEnvCombine.GL_PREVIOUS_ARB
                    )
                    qgl.qglTexEnvi(
                        GL_TEXTURE_ENV,
                        ARBTextureEnvCombine.GL_SOURCE1_ALPHA_ARB,
                        ARBTextureEnvCombine.GL_CONSTANT_ARB
                    )
                    qgl.qglTexEnvi(GL_TEXTURE_ENV, ARBTextureEnvCombine.GL_OPERAND0_ALPHA_ARB, GL_SRC_ALPHA)
                    qgl.qglTexEnvi(GL_TEXTURE_ENV, ARBTextureEnvCombine.GL_OPERAND1_ALPHA_ARB, GL_SRC_ALPHA)
                    qgl.qglTexEnvi(GL_TEXTURE_ENV, GL_ALPHA_SCALE, 1)
                    tr_backend.GL_SelectTexture(0)
                }
            }

            // bind the texture
            tr_render.RB_BindVariableStageImage(pStage.texture, regs)

            // set the state
            tr_backend.GL_State(pStage.drawStateBits)
            RB_PrepareStageTexturing(pStage, surf, ac)

            // draw it
            tr_render.RB_DrawElementsWithCounters(tri)
            RB_FinishStageTexturing(pStage, surf, ac)
            if (pStage.vertexColor != stageVertexColor_t.SVC_IGNORE) {
                qgl.qglDisableClientState(GL_COLOR_ARRAY)
                tr_backend.GL_SelectTexture(1)
                tr_backend.GL_TexEnv(GL_MODULATE)
                Image.globalImages.BindNull()
                tr_backend.GL_SelectTexture(0)
                tr_backend.GL_TexEnv(GL_MODULATE)
            }
            stage++
        }

        // reset polygon offset
        if (shader.TestMaterialFlag(Material.MF_POLYGONOFFSET)) {
            qglDisable(GL_POLYGON_OFFSET_FILL)
        }
        if (surf.space!!.weaponDepthHack || (!soft_particle && surf.space!!.modelDepthHack != 0.0f)) { // #3878 soft particles
            tr_render.RB_LeaveDepthHack()
        }
    }

    /*
     =====================
     RB_STD_DrawShaderPasses

     Draw non-light dependent passes
     =====================
     */
    fun RB_STD_DrawShaderPasses(drawSurfs: Array<drawSurf_s>, numDrawSurfs: Int): Int {
        var i: Int

        // only obey skipAmbient if we are rendering a view
        if (backEnd!!.viewDef!!.viewEntitys != null && r_skipAmbient!!.GetBool()) {
            return numDrawSurfs
        }

        var isPostProcess = false

        // if we are about to draw the first surface that needs
        // the rendering in a texture, copy it over
        if (drawSurfs[0].material!!.GetSort() >= Material.SS_POST_PROCESS) {
            if (r_skipPostProcess!!.GetBool()) {
                return 0
            }
            isPostProcess = true

            // only dump if in a 3d view
            if (backEnd!!.viewDef!!.viewEntitys != null && tr.backEndRenderer == backEndName_t.BE_ARB2) {
                val imageWidth =
                    CInt(backEnd!!.viewDef!!.viewport.x2 - backEnd!!.viewDef!!.viewport.x1 + 1)
                val imageHeight =
                    CInt(backEnd!!.viewDef!!.viewport.y2 - backEnd!!.viewDef!!.viewport.y1 + 1)
                Image.globalImages.currentRenderImage!!.CopyFramebuffer(
                    backEnd!!.viewDef!!.viewport.x1, backEnd!!.viewDef!!.viewport.y1,
                    imageWidth, imageHeight, true
                )
            }
            backEnd!!.currentRenderCopied = true
        }
        tr_backend.GL_SelectTexture(1)
        Image.globalImages.BindNull()
        tr_backend.GL_SelectTexture(0)
        qgl.qglEnableClientState(GL_TEXTURE_COORD_ARRAY)
        RB_SetProgramEnvironment(isPostProcess)

        // we don't use RB_RenderDrawSurfListWithFunction()
        // because we want to defer the matrix load because many
        // surfaces won't draw any ambient passes
        backEnd!!.currentSpace = null
        i = 0
        while (i < numDrawSurfs /*&& numDrawSurfs == 5*/) {
            if (drawSurfs[i].material!!.SuppressInSubview()) {
                i++
                continue
            }
            if (backEnd!!.viewDef!!.isXraySubview && drawSurfs[i].space!!.entityDef != null) {
                if (drawSurfs[i].space!!.entityDef!!.parms.xrayIndex != 2) {
                    i++
                    continue
                }
            }

            // we need to draw the post process shaders after we have drawn the fog lights
            if (drawSurfs[i].material!!.GetSort() >= Material.SS_POST_PROCESS
                && !backEnd!!.currentRenderCopied
            ) {
                break
            }
            RB_STD_T_RenderShaderPasses(drawSurfs[i])
            i++
        }
        GL_Cull(cullType_t.CT_FRONT_SIDED)
        qglColor3f(1.0f, 1.0f, 1.0f)
        return i
    }

    /*
     ==============================================================================

     BACK END RENDERING OF STENCIL SHADOWS

     ==============================================================================
     */
    /*
     =====================
     RB_StencilShadowPass

     Stencil test should already be enabled, and the stencil buffer should have
     been set to 128 on any surfaces that might receive shadows
     =====================
     */
    fun RB_StencilShadowPass(drawSurfs: drawSurf_s?) {
        if (!r_shadows!!.GetBool()) {
            return
        }
        if (drawSurfs == null) {
            return
        }
        Image.globalImages.BindNull()
        qgl.qglDisableClientState(GL_TEXTURE_COORD_ARRAY)

        // for visualizing the shadows
        if (r_showShadows!!.GetInteger() != 0) {
            if (r_showShadows!!.GetInteger() == 2) {
                // draw filled in
                tr_backend.GL_State(GLS_DEPTHMASK or GLS_SRCBLEND_ONE or GLS_DSTBLEND_ONE or GLS_DEPTHFUNC_LESS)
            } else {
                // draw as lines, filling the depth buffer
                tr_backend.GL_State(GLS_SRCBLEND_ONE or GLS_DSTBLEND_ZERO or GLS_POLYMODE_LINE or GLS_DEPTHFUNC_ALWAYS)
            }
        } else {
            // don't write to the color buffer, just the stencil buffer
            tr_backend.GL_State(GLS_DEPTHMASK or GLS_COLORMASK or GLS_ALPHAMASK or GLS_DEPTHFUNC_LESS)
        }
        if (r_shadowPolygonFactor!!.GetFloat() != 0.0f || r_shadowPolygonOffset!!.GetFloat() != 0.0f) {
            qgl.qglPolygonOffset(
                r_shadowPolygonFactor!!.GetFloat(),
                -r_shadowPolygonOffset!!.GetFloat()
            )
            qglEnable(GL_POLYGON_OFFSET_FILL)
        }
        qgl.qglStencilFunc(GL_ALWAYS, 1, 255)
        if (glConfig.depthBoundsTestAvailable && r_useDepthBoundsTest!!.GetBool()) {
            qglEnable(EXTDepthBoundsTest.GL_DEPTH_BOUNDS_TEST_EXT)
        }
        tr_render.RB_RenderDrawSurfChainWithFunction(drawSurfs, RB_T_Shadow.INSTANCE)
        GL_Cull(cullType_t.CT_FRONT_SIDED)
        if (r_shadowPolygonFactor!!.GetFloat() != 0.0f || r_shadowPolygonOffset!!.GetFloat() != 0.0f) {
            qglDisable(GL_POLYGON_OFFSET_FILL)
        }
        if (glConfig.depthBoundsTestAvailable && r_useDepthBoundsTest!!.GetBool()) {
            qglDisable(EXTDepthBoundsTest.GL_DEPTH_BOUNDS_TEST_EXT)
        }
        qgl.qglEnableClientState(GL_TEXTURE_COORD_ARRAY)
        qgl.qglStencilFunc(GL_GEQUAL, 128, 255)
        qglStencilOp(GL_KEEP, GL_KEEP, GL_KEEP)
    }

    /*
     =====================
     RB_BlendLight

     Dual texture together the falloff and projection texture with a blend
     mode to the framebuffer, instead of interacting with the surface texture
     =====================
     */
    fun RB_BlendLight(drawSurfs: drawSurf_s?, drawSurfs2: drawSurf_s?) {
        val lightShader: idMaterial
        var stage: shaderStage_t?
        var i: Int
        val regs: FloatArray
        if (drawSurfs == null) {
            return
        }
        if (r_skipBlendLights!!.GetBool()) {
            return
        }
        lightShader = backEnd!!.vLight!!.lightShader!!
        regs = backEnd!!.vLight!!.shaderRegisters!!

        // texture 1 will get the falloff texture
        tr_backend.GL_SelectTexture(1)
        qgl.qglDisableClientState(GL_TEXTURE_COORD_ARRAY)
        qglEnable(GL_TEXTURE_GEN_S)
        qgl.qglTexCoord2f(0.0f, 0.5f)
        backEnd!!.vLight!!.falloffImage!!.Bind()

        // texture 0 will get the projected texture
        tr_backend.GL_SelectTexture(0)
        qgl.qglDisableClientState(GL_TEXTURE_COORD_ARRAY)
        qglEnable(GL_TEXTURE_GEN_S)
        qglEnable(GL_TEXTURE_GEN_T)
        qglEnable(GL_TEXTURE_GEN_Q)
        i = 0
        while (i < lightShader.GetNumStages()) {
            stage = lightShader.GetStage(i)
            if (0.0f == regs[stage!!.conditionRegister]) {
                i++
                continue
            }
            tr_backend.GL_State(GLS_DEPTHMASK or stage.drawStateBits or GLS_DEPTHFUNC_EQUAL)
            tr_backend.GL_SelectTexture(0)
            stage.texture.image!![0]!!.Bind()
            if (stage.texture.hasMatrix) {
                tr_render.RB_LoadShaderTextureMatrix(regs, stage.texture)
            }

            // get the modulate values from the light, including alpha, unlike normal lights
            backEnd!!.lightColor[0] = regs[stage.color.registers[0]]
            backEnd!!.lightColor[1] = regs[stage.color.registers[1]]
            backEnd!!.lightColor[2] = regs[stage.color.registers[2]]
            backEnd!!.lightColor[3] = regs[stage.color.registers[3]]
            qgl.qglColor4fv(backEnd!!.lightColor)
            tr_render.RB_RenderDrawSurfChainWithFunction(drawSurfs, RB_T_BlendLight.INSTANCE)
            tr_render.RB_RenderDrawSurfChainWithFunction(drawSurfs2, RB_T_BlendLight.INSTANCE)
            if (stage.texture.hasMatrix) {
                tr_backend.GL_SelectTexture(0)
                qgl.qglMatrixMode(GL_TEXTURE)
                qgl.qglLoadIdentity()
                qgl.qglMatrixMode(GL_MODELVIEW)
            }
            i++
        }
        tr_backend.GL_SelectTexture(1)
        qglDisable(GL_TEXTURE_GEN_S)
        Image.globalImages.BindNull()
        tr_backend.GL_SelectTexture(0)
        qglDisable(GL_TEXTURE_GEN_S)
        qglDisable(GL_TEXTURE_GEN_T)
        qglDisable(GL_TEXTURE_GEN_Q)
    }

    /*
     =============================================================================================

     BLEND LIGHT PROJECTION

     =============================================================================================
     */
    /*
     ==================
     RB_FogPass
     ==================
     */
    fun RB_FogPass(drawSurfs: drawSurf_s?, drawSurfs2: drawSurf_s?) {
        val frustumTris: srfTriangles_s
        val ds = drawSurf_s()
        val lightShader: idMaterial
        val stage: shaderStage_t?
        val regs: FloatArray

        // create a surface for the light frustom triangles, which are oriented drawn side out
        frustumTris = backEnd!!.vLight!!.frustumTris!!

        // if we ran out of vertex cache memory, skip it
        if (frustumTris.ambientCache == null) {
            return
        }
        ds.space = backEnd!!.viewDef!!.worldSpace
        ds.geo = frustumTris
        ds.scissorRect = idScreenRect(backEnd!!.viewDef!!.scissor)

        // find the current color and density of the fog
        lightShader = backEnd!!.vLight!!.lightShader!!
        regs = backEnd!!.vLight!!.shaderRegisters!!
        // assume fog shaders have only a single stage
        stage = lightShader.GetStage(0)
        backEnd!!.lightColor[0] = regs[stage!!.color.registers[0]]
        backEnd!!.lightColor[1] = regs[stage.color.registers[1]]
        backEnd!!.lightColor[2] = regs[stage.color.registers[2]]
        backEnd!!.lightColor[3] = regs[stage.color.registers[3]]
        qgl.qglColor3fv(backEnd!!.lightColor)

        // calculate the falloff planes
        val a: Float

        // if they left the default value on, set a fog distance of 500
        a = if (backEnd!!.lightColor[3] <= 1.0f) {
            -0.5f / DEFAULT_FOG_DISTANCE
        } else {
            // otherwise, distance = alpha color
            -0.5f / backEnd!!.lightColor[3]
        }
        tr_backend.GL_State(GLS_DEPTHMASK or GLS_SRCBLEND_SRC_ALPHA or GLS_DSTBLEND_ONE_MINUS_SRC_ALPHA or GLS_DEPTHFUNC_EQUAL)

        // texture 0 is the falloff image
        tr_backend.GL_SelectTexture(0)
        Image.globalImages.fogImage!!.Bind()
        //GL_Bind( tr.whiteImage );
        qgl.qglDisableClientState(GL_TEXTURE_COORD_ARRAY)
        qglEnable(GL_TEXTURE_GEN_S)
        qglEnable(GL_TEXTURE_GEN_T)
        qgl.qglTexCoord2f(0.5f, 0.5f) // make sure Q is set
        fogPlanes[0][0] = a * backEnd!!.viewDef!!.worldSpace.modelViewMatrix[2]
        fogPlanes[0][1] = a * backEnd!!.viewDef!!.worldSpace.modelViewMatrix[6]
        fogPlanes[0][2] = a * backEnd!!.viewDef!!.worldSpace.modelViewMatrix[10]
        fogPlanes[0][3] = a * backEnd!!.viewDef!!.worldSpace.modelViewMatrix[14]
        fogPlanes[1][0] = a * backEnd!!.viewDef!!.worldSpace.modelViewMatrix[0]
        fogPlanes[1][1] = a * backEnd!!.viewDef!!.worldSpace.modelViewMatrix[4]
        fogPlanes[1][2] = a * backEnd!!.viewDef!!.worldSpace.modelViewMatrix[8]
        fogPlanes[1][3] = a * backEnd!!.viewDef!!.worldSpace.modelViewMatrix[12]

        // texture 1 is the entering plane fade correction
        tr_backend.GL_SelectTexture(1)
        Image.globalImages.fogEnterImage!!.Bind()
        qgl.qglDisableClientState(GL_TEXTURE_COORD_ARRAY)
        qglEnable(GL_TEXTURE_GEN_S)
        qglEnable(GL_TEXTURE_GEN_T)

        // T will get a texgen for the fade plane, which is always the "top" plane on unrotated lights
        fogPlanes[2][0] = 0.001f * backEnd!!.vLight!!.fogPlane!![0]
        fogPlanes[2][1] = 0.001f * backEnd!!.vLight!!.fogPlane!![1]
        fogPlanes[2][2] = 0.001f * backEnd!!.vLight!!.fogPlane!![2]
        fogPlanes[2][3] = 0.001f * backEnd!!.vLight!!.fogPlane!![3]

        // S is based on the view origin
        val s = backEnd!!.viewDef!!.renderView.vieworg.times(fogPlanes[2].Normal()) + fogPlanes[2][3]
        fogPlanes[3][0] = 0.0f
        fogPlanes[3][1] = 0.0f
        fogPlanes[3][2] = 0.0f
        fogPlanes[3][3] = FOG_ENTER + s
        qgl.qglTexCoord2f(FOG_ENTER + s, FOG_ENTER)

        // draw it
        tr_render.RB_RenderDrawSurfChainWithFunction(drawSurfs, RB_T_BasicFog.INSTANCE)
        tr_render.RB_RenderDrawSurfChainWithFunction(drawSurfs2, RB_T_BasicFog.INSTANCE)

        // the light frustum bounding planes aren't in the depth buffer, so use depthfunc_less instead
        // of depthfunc_equal
        tr_backend.GL_State(GLS_DEPTHMASK or GLS_SRCBLEND_SRC_ALPHA or GLS_DSTBLEND_ONE_MINUS_SRC_ALPHA or GLS_DEPTHFUNC_LESS)
        GL_Cull(cullType_t.CT_BACK_SIDED)
        tr_render.RB_RenderDrawSurfChainWithFunction(ds, RB_T_BasicFog.INSTANCE)
        GL_Cull(cullType_t.CT_FRONT_SIDED)
        tr_backend.GL_SelectTexture(1)
        qglDisable(GL_TEXTURE_GEN_S)
        qglDisable(GL_TEXTURE_GEN_T)
        Image.globalImages.BindNull()
        tr_backend.GL_SelectTexture(0)
        qglDisable(GL_TEXTURE_GEN_S)
        qglDisable(GL_TEXTURE_GEN_T)
    }

    /*
     ==================
     RB_STD_FogAllLights
     ==================
     */
    fun RB_STD_FogAllLights() {
        var vLight: viewLight_s?
        if (r_skipFogLights!!.GetBool() || r_showOverDraw!!.GetInteger() != 0 || backEnd!!.viewDef!!.isXraySubview /* dont fog in xray mode*/) {
            return
        }
        qglDisable(GL_STENCIL_TEST)
        vLight = backEnd!!.viewDef!!.viewLights
        while (vLight != null) {
            backEnd!!.vLight = vLight
            if (!vLight.lightShader!!.IsFogLight() && !vLight.lightShader!!.IsBlendLight()) {
                vLight = vLight.next
                continue
            }

//if(false){ // _D3XP disabled that
//		if ( r_ignore.GetInteger() ) {
//			// we use the stencil buffer to guarantee that no pixels will be
//			// double fogged, which happens in some areas that are thousands of
//			// units from the origin
//			backEnd!!.currentScissor = vLight.scissorRect;
//			if ( r_useScissor.GetBool() ) {
//				qglScissor( backEnd!!.viewDef!!.viewport.x1 + backEnd!!.currentScissor.x1,
//					backEnd!!.viewDef!!.viewport.y1 + backEnd!!.currentScissor.y1,
//					backEnd!!.currentScissor.x2 + 1 - backEnd!!.currentScissor.x1,
//					backEnd!!.currentScissor.y2 + 1 - backEnd!!.currentScissor.y1 );
//			}
//			qglClear( GL_STENCIL_BUFFER_BIT );
//
//			qglEnable( GL_STENCIL_TEST );
//
//			// only pass on the cleared stencil values
//			qglStencilFunc( GL_EQUAL, 128, 255 );
//
//			// when we pass the stencil test and depth test and are going to draw,
//			// increment the stencil buffer so we don't ever draw on that pixel again
//			qglStencilOp( GL_KEEP, GL_KEEP, GL_INCR );
//		}
//}
            if (vLight.lightShader!!.IsFogLight()) {
                RB_FogPass(vLight.globalInteractions[0], vLight.localInteractions[0])
            } else if (vLight.lightShader!!.IsBlendLight()) {
                RB_BlendLight(vLight.globalInteractions[0], vLight.localInteractions[0])
            }
            qglDisable(GL_STENCIL_TEST)
            vLight = vLight.next
        }
        qglEnable(GL_STENCIL_TEST)
    }

    /*
     ==================
     RB_STD_LightScale

     Perform extra blending passes to multiply the entire buffer by
     a floating point value
     ==================
     */
    fun RB_STD_LightScale() {
        var v: Float
        var f: Float
        if (1.0f == backEnd!!.overBright) {
            return
        }
        if (r_skipLightScale!!.GetBool()) {
            return
        }

        // the scissor may be smaller than the viewport for subviews
        if (r_useScissor!!.GetBool()) {
            qgl.qglScissor(
                backEnd!!.viewDef!!.viewport.x1 + backEnd!!.viewDef!!.scissor.x1,
                backEnd!!.viewDef!!.viewport.y1 + backEnd!!.viewDef!!.scissor.y1,
                backEnd!!.viewDef!!.scissor.x2 - backEnd!!.viewDef!!.scissor.x1 + 1,
                backEnd!!.viewDef!!.scissor.y2 - backEnd!!.viewDef!!.scissor.y1 + 1
            )
            backEnd!!.currentScissor = backEnd!!.viewDef!!.scissor
        }

        // full screen blends
        qgl.qglLoadIdentity()
        qgl.qglMatrixMode(GL_PROJECTION)
        qgl.qglPushMatrix()
        qgl.qglLoadIdentity()
        qgl.qglOrtho(0.0, 1.0, 0.0, 1.0, -1.0, 1.0)
        tr_backend.GL_State(GLS_SRCBLEND_DST_COLOR or GLS_DSTBLEND_SRC_COLOR)
        GL_Cull(cullType_t.CT_TWO_SIDED) // so mirror views also get it
        Image.globalImages.BindNull()
        qglDisable(GL_DEPTH_TEST)
        qglDisable(GL_STENCIL_TEST)
        v = 1.0f
        while (abs((v - backEnd!!.overBright)) > 0.01) {    // a little extra slop
            f = backEnd!!.overBright / v
            f /= 2.0f
            if (f > 1) {
                f = 1.0f
            }
            qglColor3f(f, f, f)
            v = v * f * 2
            qgl.qglBegin(GL_QUADS)
            qgl.qglVertex2f(0.0f, 0.0f)
            qgl.qglVertex2f(0.0f, 1.0f)
            qgl.qglVertex2f(1.0f, 1.0f)
            qgl.qglVertex2f(1.0f, 0.0f)
            qgl.qglEnd()
        }
        qgl.qglPopMatrix()
        qglEnable(GL_DEPTH_TEST)
        qgl.qglMatrixMode(GL_MODELVIEW)
        GL_Cull(cullType_t.CT_FRONT_SIDED)
    }

    /*
     =============
     RB_STD_DrawView

     =============
     */
    fun RB_STD_DrawView() {
        val drawSurfs: Array<drawSurf_s>
        val numDrawSurfs: Int
        backEnd!!.depthFunc = GLS_DEPTHFUNC_EQUAL
        drawSurfs = backEnd!!.viewDef!!.drawSurfs
        numDrawSurfs = backEnd!!.viewDef!!.numDrawSurfs

        // clear the z buffer, set the projection matrix, etc
        tr_render.RB_BeginDrawingView()

        // decide how much overbrighting we are going to do
        tr_render.RB_DetermineLightScale()

        // fill the depth buffer and clear color buffer to black except on
        // subviews
        RB_STD_FillDepthBuffer(drawSurfs, numDrawSurfs)
        when (tr.backEndRenderer) {
            backEndName_t.BE_ARB2 -> draw_arb2.RB_ARB2_DrawInteractions()
            else -> {}
        }

        // disable stencil shadow test
        qgl.qglStencilFunc(GL_ALWAYS, 128, 255)

        // uplight the entire screen to crutch up not having better blending range
        RB_STD_LightScale()

        // now draw any non-light dependent shading passes
        val processed = RB_STD_DrawShaderPasses(drawSurfs, numDrawSurfs)

        // fob and blend lights
        RB_STD_FogAllLights()

        // now draw any post-processing effects using _currentRender
        if (processed < numDrawSurfs) {
            RB_STD_DrawShaderPasses(Arrays.copyOfRange(drawSurfs, processed, numDrawSurfs), numDrawSurfs - processed)
        }
        tr_rendertools.RB_RenderDebugTools(drawSurfs as Array<drawSurf_s?>, numDrawSurfs)
    }

    /*
     =============================================================================================

     FILL DEPTH BUFFER

     =============================================================================================
     */
    /*
     ==================
     RB_T_FillDepthBuffer
     ==================
     */
    class RB_T_FillDepthBuffer private constructor() : triFunc() {
        override fun run(surf: drawSurf_s) {
            var stage: Int
            val shader: idMaterial
            var pStage: shaderStage_t?
            val regs: FloatArray
            val color = FloatArray(4)
            val tri: srfTriangles_s
            tri = surf.geo!!
            shader = surf.material!!

            // update the clip plane if needed
            if (backEnd!!.viewDef!!.numClipPlanes != 0 && surf.space !== backEnd!!.currentSpace) {
                tr_backend.GL_SelectTexture(1)
                val plane = idPlane()
                tr_main.R_GlobalPlaneToLocal(
                    surf.space!!.modelMatrix,
                    backEnd!!.viewDef!!.clipPlanes[0]!!,
                    plane
                )
                plane.plusAssign(3, 0.5f) // the notch is in the middle
                qgl.qglTexGenfv(GL_S, GL_OBJECT_PLANE, plane.ToFloatPtr())
                tr_backend.GL_SelectTexture(0)
            }
            if (!shader.IsDrawn()) {
                return
            }

            // some deforms may disable themselves by setting numIndexes = 0
            if (0 == tri.numIndexes) {
                return
            }

            // translucent surfaces don't put anything in the depth buffer and don't
            // test against it, which makes them fail the mirror clip plane operation
            if (shader.Coverage() == materialCoverage_t.MC_TRANSLUCENT) {
                return
            }
            if (tri.ambientCache == null) {
                Common.common.Printf("RB_T_FillDepthBuffer: !tri.ambientCache\n")
                return
            }

            // get the expressions for conditionals / color / texcoords
            regs = surf.shaderRegisters!!

            // if all stages of a material have been conditioned off, don't do anything
            stage = 0
            while (stage < shader.GetNumStages()) {
                pStage = shader.GetStage(stage)
                // check the stage enable condition
                if (regs[pStage!!.conditionRegister] != 0.0f) {
                    break
                }
                stage++
            }
            if (stage == shader.GetNumStages()) {
                return
            }

            // set polygon offset if necessary
            if (shader.TestMaterialFlag(Material.MF_POLYGONOFFSET)) {
                qglEnable(GL_POLYGON_OFFSET_FILL)
                qgl.qglPolygonOffset(
                    r_offsetFactor!!.GetFloat(),
                    r_offsetUnits!!.GetFloat() * shader.GetPolygonOffset()
                )
            }

            // subviews will just down-modulate the color buffer by overbright
            if (shader.GetSort() == Material.SS_SUBVIEW.toFloat()) {
                tr_backend.GL_State(GLS_SRCBLEND_DST_COLOR or GLS_DSTBLEND_ZERO or GLS_DEPTHFUNC_LESS)
                color[2] = 1.0f / backEnd!!.overBright
                color[1] = color[2]
                color[0] = color[1]
                color[3] = 1.0f
            } else {
                // others just draw black
                color[2] = 0.0f
                color[1] = color[2]
                color[0] = color[1]
                color[3] = 1.0f
            }
            val ac =
                idDrawVert(vertexCache.Position(tri.ambientCache))
            qglVertexPointer(3, GL_FLOAT, idDrawVert.BYTES, ac.xyzOffset().toLong())
            qgl.qglTexCoordPointer(
                2,
                GL_FLOAT,
                idDrawVert.BYTES,
                ac.stOffset().toLong()
            )
            var drawSolid = shader.Coverage() == materialCoverage_t.MC_OPAQUE

            // we may have multiple alpha tested stages
            if (shader.Coverage() == materialCoverage_t.MC_PERFORATED) {
                // if the only alpha tested stages are condition register omitted,
                // draw a normal opaque surface
                var didDraw = false
                qglEnable(GL_ALPHA_TEST)
                // perforated surfaces may have multiple alpha tested stages
                stage = 0
                while (stage < shader.GetNumStages()) {
                    pStage = shader.GetStage(stage)
                    if (!pStage!!.hasAlphaTest) {
                        stage++
                        continue
                    }

                    // check the stage enable condition
                    if (regs[pStage.conditionRegister] == 0.0f) {
                        stage++
                        continue
                    }

                    // if we at least tried to draw an alpha tested stage,
                    // we won't draw the opaque surface
                    didDraw = true

                    // set the alpha modulate
                    color[3] = regs[pStage.color.registers[3]]

                    // skip the entire stage if alpha would be black
                    if (color[3] <= 0) {
                        stage++
                        continue
                    }
                    qgl.qglColor4fv(color)
                    qgl.qglAlphaFunc(GL_GREATER, regs[pStage.alphaTestRegister])

                    // bind the texture
                    pStage.texture.image!![0]!!.Bind()

                    // set texture matrix and texGens
                    RB_PrepareStageTexturing(pStage, surf, ac)

                    // draw it
                    tr_render.RB_DrawElementsWithCounters(tri)
                    RB_FinishStageTexturing(pStage, surf, ac)
                    stage++
                }
                qglDisable(GL_ALPHA_TEST)
                if (!didDraw) {
                    drawSolid = true
                }
            }

            // draw the entire surface solid
            if (drawSolid) {
                qgl.qglColor4fv(color)
                Image.globalImages.whiteImage!!.Bind()

                // draw it
                tr_render.RB_DrawElementsWithCounters(tri)
            }

            // reset polygon offset
            if (shader.TestMaterialFlag(Material.MF_POLYGONOFFSET)) {
                qglDisable(GL_POLYGON_OFFSET_FILL)
            }

            // reset blending
            if (shader.GetSort() == Material.SS_SUBVIEW.toFloat()) {
                tr_backend.GL_State(GLS_DEPTHFUNC_LESS)
            }
        }

        companion object {
            val INSTANCE: triFunc = RB_T_FillDepthBuffer()
        }
    }

    /*
     =====================
     RB_T_Shadow

     the shadow volumes face INSIDE
     =====================
     */
    class RB_T_Shadow private constructor() : triFunc() {
        override fun run(surf: drawSurf_s) {
            val tri: srfTriangles_s

            // set the light position if we are using a vertex program to project the rear surfaces
            if (tr.backEndRendererHasVertexPrograms && r_useShadowVertexProgram.GetBool()
                && surf.space != backEnd!!.currentSpace
            ) {
                val localLight = idVec4()
                val tempLight = idVec3()

                R_GlobalPointToLocal(
                    surf.space!!.modelMatrix,
                    backEnd!!.vLight!!.globalLightOrigin,
                    tempLight
                )
                localLight.x = tempLight.x
                localLight.y = tempLight.y
                localLight.z = tempLight.z
                localLight.w = 0.0f
                qglProgramEnvParameter4fvARB(
                    GL_VERTEX_PROGRAM_ARB,
                    programParameter_t.PP_LIGHT_ORIGIN,
                    localLight.ToFloatPtr()
                )
            }

            tri = surf.geo!!

            if (tri.shadowCache == null) {
                return
            }

            val shadowPos = vertexCache.Position(tri.shadowCache)
            if (vertexCache.IsVBOOffset(shadowPos)) {
                qglVertexPointer(4, GL_FLOAT, shadowCache_s.BYTES, vertexCache.GetVBOOffset(shadowPos))
            } else {
                qglVertexPointer(4, GL_FLOAT, shadowCache_s.BYTES, shadowPos)
            }

            // we always draw the sil planes, but we may not need to draw the front or rear caps
            var numIndexes = 0
            var external = false

            if (r_useExternalShadows.GetInteger() == 0) {
                numIndexes = tri.numIndexes
            } else if (r_useExternalShadows.GetInteger() == 2) { // force to no caps for testing
                numIndexes = tri.numShadowIndexesNoCaps
            } else if ((surf.dsFlags and DSF_VIEW_INSIDE_SHADOW) == 0) {
                // if we aren't inside the shadow projection, no caps are ever needed needed
                numIndexes = tri.numShadowIndexesNoCaps
                external = true
            } else if (!backEnd!!.vLight!!.viewInsideLight && (surf.geo!!.shadowCapPlaneBits and SHADOW_CAP_INFINITE) == 0) {
                // if we are inside the shadow projection, but outside the light, and drawing
                // a non-infinite shadow, we can skip some caps
                if ((backEnd!!.vLight!!.viewSeesShadowPlaneBits and surf.geo!!.shadowCapPlaneBits) != 0) {
                    // we can see through a rear cap, so we need to draw it, but we can skip the
                    // caps on the actual surface
                    numIndexes = tri.numShadowIndexesNoFrontCaps
                } else {
                    // we don't need to draw any caps
                    numIndexes = tri.numShadowIndexesNoCaps
                }
                external = true
            } else {
                // must draw everything
                numIndexes = tri.numIndexes
            }

            // set depth bounds
            if (glConfig.depthBoundsTestAvailable && r_useDepthBoundsTest.GetBool()) {
                qglDepthBoundsEXT(surf.scissorRect!!.zmin, surf.scissorRect!!.zmax)
            }

            // debug visualization
            if (r_showShadows.GetInteger() != 0) {
                if (r_showShadows.GetInteger() == 3) {
                    if (external) {
                        qglColor3f(0.1f / backEnd!!.overBright, 1 / backEnd!!.overBright, 0.1f / backEnd!!.overBright)
                    } else {
                        // these are the surfaces that require the reverse
                        qglColor3f(1 / backEnd!!.overBright, 0.1f / backEnd!!.overBright, 0.1f / backEnd!!.overBright)
                    }
                } else {
                    // draw different color for turboshadows
                    if (surf.geo!!.shadowCapPlaneBits and SHADOW_CAP_INFINITE != 0) {
                        if (numIndexes == tri.numIndexes) {
                            qglColor3f(
                                1 / backEnd!!.overBright,
                                0.1f / backEnd!!.overBright,
                                0.1f / backEnd!!.overBright
                            )
                        } else {
                            qglColor3f(
                                1 / backEnd!!.overBright,
                                0.4f / backEnd!!.overBright,
                                0.1f / backEnd!!.overBright
                            )
                        }
                    } else {
                        if (numIndexes == tri.numIndexes) {
                            qglColor3f(
                                0.1f / backEnd!!.overBright,
                                1 / backEnd!!.overBright,
                                0.1f / backEnd!!.overBright
                            )
                        } else if (numIndexes == tri.numShadowIndexesNoFrontCaps) {
                            qglColor3f(
                                0.1f / backEnd!!.overBright,
                                1 / backEnd!!.overBright,
                                0.6f / backEnd!!.overBright
                            )
                        } else {
                            qglColor3f(
                                0.6f / backEnd!!.overBright,
                                1 / backEnd!!.overBright,
                                0.1f / backEnd!!.overBright
                            )
                        }
                    }
                }

                qglStencilOp(GL_KEEP, GL_KEEP, GL_KEEP)
                qglDisable(GL_STENCIL_TEST)
                GL_Cull(cullType_t.CT_TWO_SIDED)
                RB_DrawShadowElementsWithCounters(tri, numIndexes)
                GL_Cull(cullType_t.CT_FRONT_SIDED)
                qglEnable(GL_STENCIL_TEST)

                return
            }

            // DG: that bloody patent on depth-fail stencil shadows has finally expired on 2019-10-13,
            //     so use them (see https://patents.google.com/patent/US6384822B1/en for expiration status)
            var useStencilOpSeperate = r_useStencilOpSeparate.GetBool()
            if (!r_useCarmacksReverse.GetBool()) {
                if (useStencilOpSeperate) {
                    // not using z-fail, but using qglStencilOpSeparate()
                    val firstFace = if (backEnd!!.viewDef!!.isMirror) GL_FRONT else GL_BACK
                    val secondFace = if (backEnd!!.viewDef!!.isMirror) GL_BACK else GL_FRONT
                    GL_Cull(cullType_t.CT_TWO_SIDED)
                    if (!external) {
                        glStencilOpSeparate(firstFace, GL_KEEP, tr.stencilDecr, tr.stencilDecr)
                        glStencilOpSeparate(secondFace, GL_KEEP, tr.stencilIncr, tr.stencilIncr)
                        RB_DrawShadowElementsWithCounters(tri, numIndexes)
                    }

                    glStencilOpSeparate(firstFace, GL_KEEP, GL_KEEP, tr.stencilIncr)
                    glStencilOpSeparate(secondFace, GL_KEEP, GL_KEEP, tr.stencilDecr)

                    RB_DrawShadowElementsWithCounters(tri, numIndexes)

                } else { // DG: this is the original code:
                    // patent-free work around
                    if (!external) {
                        // "preload" the stencil buffer with the number of volumes
                        // that get clipped by the near or far clip plane
                        qglStencilOp(GL_KEEP, tr.stencilDecr, tr.stencilDecr)
                        GL_Cull(cullType_t.CT_FRONT_SIDED)
                        RB_DrawShadowElementsWithCounters(tri, numIndexes)
                        qglStencilOp(GL_KEEP, tr.stencilIncr, tr.stencilIncr)
                        GL_Cull(cullType_t.CT_BACK_SIDED)
                        RB_DrawShadowElementsWithCounters(tri, numIndexes)
                    }

                    // traditional depth-pass stencil shadows
                    qglStencilOp(GL_KEEP, GL_KEEP, tr.stencilIncr)
                    GL_Cull(cullType_t.CT_FRONT_SIDED)
                    RB_DrawShadowElementsWithCounters(tri, numIndexes)

                    qglStencilOp(GL_KEEP, GL_KEEP, tr.stencilDecr)
                    GL_Cull(cullType_t.CT_BACK_SIDED)
                    RB_DrawShadowElementsWithCounters(tri, numIndexes)
                }
            } else { // use the formerly patented "Carmack's Reverse" Z-Fail code
                if (useStencilOpSeperate) {
                    // Z-Fail with glStencilOpSeparate() which will reduce draw calls
                    val firstFace = if (backEnd!!.viewDef!!.isMirror) GL_FRONT else GL_BACK
                    val secondFace = if (backEnd!!.viewDef!!.isMirror) GL_BACK else GL_FRONT
                    if (!external) { // z-fail
                        glStencilOpSeparate(firstFace, GL_KEEP, tr.stencilDecr, GL_KEEP)
                        glStencilOpSeparate(secondFace, GL_KEEP, tr.stencilIncr, GL_KEEP)
                    } else { // depth-pass
                        glStencilOpSeparate(firstFace, GL_KEEP, GL_KEEP, tr.stencilIncr)
                        glStencilOpSeparate(secondFace, GL_KEEP, GL_KEEP, tr.stencilDecr)
                    }
                    GL_Cull(cullType_t.CT_TWO_SIDED)
                    RB_DrawShadowElementsWithCounters(tri, numIndexes)

                } else { // Z-Fail without glStencilOpSeparate()

                    // LEITH: the (formerly patented) "Carmack's Reverse" code

                    // depth-fail/Z-Fail stencil shadows
                    if (!external) {
                        qglStencilOp(GL_KEEP, tr.stencilDecr, GL_KEEP)
                        GL_Cull(cullType_t.CT_FRONT_SIDED)
                        RB_DrawShadowElementsWithCounters(tri, numIndexes)
                        qglStencilOp(GL_KEEP, tr.stencilIncr, GL_KEEP)
                        GL_Cull(cullType_t.CT_BACK_SIDED)
                        RB_DrawShadowElementsWithCounters(tri, numIndexes)
                    }
                    // traditional depth-pass stencil shadows
                    else {
                        qglStencilOp(GL_KEEP, GL_KEEP, tr.stencilIncr)
                        GL_Cull(cullType_t.CT_FRONT_SIDED)
                        RB_DrawShadowElementsWithCounters(tri, numIndexes)

                        qglStencilOp(GL_KEEP, GL_KEEP, tr.stencilDecr)
                        GL_Cull(cullType_t.CT_BACK_SIDED)
                        RB_DrawShadowElementsWithCounters(tri, numIndexes)
                    }
                }
            }
        }

        companion object {
            val INSTANCE: triFunc = RB_T_Shadow()
        }
    }

    //=========================================================================================
    /*
     =====================
     RB_T_BlendLight

     =====================
     */
    class RB_T_BlendLight private constructor() : triFunc() {
        override fun run(surf: drawSurf_s) {
            val tri: srfTriangles_s
            tri = surf.geo!!
            if (backEnd!!.currentSpace !== surf.space) {
                val lightProject = idPlane.generateArray(4)
                var i: Int
                i = 0
                while (i < 4) {
                    tr_main.R_GlobalPlaneToLocal(
                        surf.space!!.modelMatrix,
                        backEnd!!.vLight!!.lightProject[i],
                        lightProject[i]
                    )
                    i++
                }
                tr_backend.GL_SelectTexture(0)
                qgl.qglTexGenfv(GL_S, GL_OBJECT_PLANE, lightProject[0].ToFloatPtr())
                qgl.qglTexGenfv(GL_T, GL_OBJECT_PLANE, lightProject[1].ToFloatPtr())
                qgl.qglTexGenfv(GL_Q, GL_OBJECT_PLANE, lightProject[2].ToFloatPtr())
                tr_backend.GL_SelectTexture(1)
                qgl.qglTexGenfv(GL_S, GL_OBJECT_PLANE, lightProject[3].ToFloatPtr())
            }

            // this gets used for both blend lights and shadow draws
            if (tri.ambientCache != null) {
                val ac =
                    idDrawVert(vertexCache.Position(tri.ambientCache))
                qglVertexPointer(3, GL_FLOAT, idDrawVert.BYTES, ac.xyzOffset().toLong())
            } else if (tri.shadowCache != null) {
                val shadowPos = vertexCache.Position(tri.shadowCache)
                if (vertexCache.IsVBOOffset(shadowPos)) {
                    qglVertexPointer(3, GL_FLOAT, shadowCache_s.BYTES, vertexCache.GetVBOOffset(shadowPos))
                } else {
                    qglVertexPointer(3, GL_FLOAT, shadowCache_s.BYTES, shadowPos)
                }
            }
            tr_render.RB_DrawElementsWithCounters(tri)
        }

        companion object {
            val INSTANCE: triFunc = RB_T_BlendLight()
        }
    }

    //=========================================================================================
    /*
     =====================
     RB_T_BasicFog

     =====================
     */
    class RB_T_BasicFog private constructor() : triFunc() {
        override fun run(surf: drawSurf_s) {
            if (backEnd!!.currentSpace !== surf.space) {
                val local = idPlane()

                tr_backend.GL_SelectTexture(0)

                tr_main.R_GlobalPlaneToLocal(surf.space!!.modelMatrix, fogPlanes[0], local)
                local.plusAssign(3, 0.5f)
                qgl.qglTexGenfv(GL_S, GL_OBJECT_PLANE, local.ToFloatPtr())

                local.set(3, 0.5f)
                local[0] = local.set(1, local.set(2, 0.0f))
                qgl.qglTexGenfv(GL_T, GL_OBJECT_PLANE, local.ToFloatPtr())

                tr_backend.GL_SelectTexture(1)

                // GL_S is constant per viewer
                tr_main.R_GlobalPlaneToLocal(surf.space!!.modelMatrix, fogPlanes[2], local)
                local.plusAssign(3, FOG_ENTER)
                qgl.qglTexGenfv(GL_T, GL_OBJECT_PLANE, local.ToFloatPtr())

                tr_main.R_GlobalPlaneToLocal(surf.space!!.modelMatrix, fogPlanes[3], local)
                qgl.qglTexGenfv(GL_S, GL_OBJECT_PLANE, local.ToFloatPtr())
            }
            tr_render.RB_T_RenderTriangleSurface.INSTANCE.run(surf)
        }

        companion object {
            val INSTANCE: triFunc = RB_T_BasicFog()
        }
    }
}

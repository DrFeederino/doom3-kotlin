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

import neo.Game.Game_local
import neo.Renderer.*
import neo.Renderer.Interaction.idInteraction
import neo.Renderer.Material.idMaterial
import neo.Renderer.Material.shaderStage_t
import neo.Renderer.Material.texgen_t
import neo.Renderer.Model.dynamicModel_t
import neo.Renderer.Model.idRenderModel
import neo.Renderer.Model.lightingCache_s
import neo.Renderer.Model.modelSurface_s
import neo.Renderer.Model.shadowCache_s
import neo.Renderer.Model.srfTriangles_s
import neo.Renderer.ModelDecal.idRenderModelDecal
import neo.Renderer.ModelOverlay.idRenderModelOverlay
import neo.Renderer.RenderWorld.renderEntity_s
import neo.framework.Common
import neo.idlib.BV.Box.idBox
import neo.idlib.BV.idBounds
import neo.idlib.CmdArgs
import neo.idlib.geometry.DrawVert.idDrawVert
import neo.idlib.geometry.Winding.idFixedWinding
import neo.idlib.geometry.Winding.idWinding
import neo.idlib.idException
import neo.idlib.math.*
import neo.idlib.precompiled.MAX_EXPRESSION_REGISTERS
import neo.ui.UserInterface.idUserInterface
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object tr_light {
    val CHECK_BOUNDS_EPSILON: Float = 1.0f

    /*
     ====================
     R_TestPointInViewLight
     ====================
     */
    val INSIDE_LIGHT_FRUSTUM_SLOP: Float = 32.0f

    /*
     =================
     R_AddDrawSurf
     =================
     */
    private val refRegs: FloatArray =
        FloatArray(MAX_EXPRESSION_REGISTERS)

    //==================================================================================================================================================================================================
    /*
     =============
     R_SetEntityDefViewEntity

     If the entityDef isn't already on the viewEntity list, create
     a viewEntity and add it to the list with an empty scissor rect.

     This does not instantiate dynamic models for the entity yet.
     =============
     */

    /*
     ==================
     R_CalcLightScissorRectangle

     The light screen bounds will be used to crop the scissor rect during
     stencil clears and interaction drawing
     ==================
     */
    var c_clippedLight: Int = 0
    var c_unclippedLight: Int = 0

    /*
     ======================================================================================================================================================================================

     VERTEX CACHE GENERATORS

     ======================================================================================================================================================================================
     */
    /*
     ==================
     R_CreateAmbientCache

     Create it if needed
     ==================
     */
    fun R_CreateAmbientCache(tri: srfTriangles_s, needsLighting: Boolean): Boolean {
        if (tri.ambientCache != null) {
            return true
        }
        // we are going to use it for drawing, so make sure we have the tangents and normals
        if (needsLighting && !tri.tangentsCalculated) {
            R_DeriveTangents(tri)
        }
        tri.ambientCache = VertexCache.vertexCache.Alloc(tri.verts!!, tri.numVerts * idDrawVert.BYTES)
        return tri.ambientCache != null
    }

    /*
     ==================
     R_CreateLightingCache

     Returns false if the cache couldn't be allocated, in which case the surface should be skipped.
     ==================
     */
    fun R_CreateLightingCache(ent: idRenderEntityLocal, light: idRenderLightLocal, tri: srfTriangles_s): Boolean {
        val localLightOrigin = idVec3()

        // fogs and blends don't need light vectors
        if (light.lightShader!!.IsFogLight() || light.lightShader!!.IsBlendLight()) {
            return true
        }

        // not needed if we have vertex programs
        if (tr.backEndRendererHasVertexPrograms) {
            return true
        }
        tr_main.R_GlobalPointToLocal(ent.modelMatrix, light.globalLightOrigin, localLightOrigin)
        val numVerts: Int = tri.ambientSurface!!.numVerts
        val size: Int = numVerts * lightingCache_s.BYTES
        val cache: Array<lightingCache_s> = Array(numVerts) { lightingCache_s() }
        SIMDProcessor!!.CreateTextureSpaceLightVectors(
            cache[0].localLightVector as Array<idVec3>,
            localLightOrigin,
            tri.ambientSurface!!.verts as Array<idDrawVert>,
            numVerts,
            tri.indexes!!,
            tri.numIndexes
        )
        tri.lightingCache = VertexCache.vertexCache.Alloc(cache as Array<idDrawVert>, size)
        return tri.lightingCache != null
    }

    /*
     ==================
     R_CreatePrivateShadowCache

     This is used only for a specific light
     ==================
     */
    fun R_CreatePrivateShadowCache(tri: srfTriangles_s) {
        if (null == tri.shadowVertexes) {
            return
        }
        tri.shadowCache =
            VertexCache.vertexCache.Alloc(tri.shadowVertexes!!, tri.numVerts * shadowCache_s.BYTES)
    }

    /*
     ==================
     R_CreateVertexProgramShadowCache

     This is constant for any number of lights, the vertex program
     takes care of projecting the verts to infinity.
     ==================
     */
    fun R_CreateVertexProgramShadowCache(tri: srfTriangles_s) {
        if (tri.verts == null) {
            return
        }
        val temp: Array<shadowCache_s> = Array(tri.numVerts * 2) { shadowCache_s() }

        for (i in 0 until tri.numVerts) {
            val v: FloatArray = tri.verts!![i]!!.xyz.ToFloatPtr()
            temp[i * 2 + 0].xyz[0] = v[0]
            temp[i * 2 + 1].xyz[0] = v[0]
            temp[i * 2 + 0].xyz[1] = v[1]
            temp[i * 2 + 1].xyz[1] = v[1]
            temp[i * 2 + 0].xyz[2] = v[2]
            temp[i * 2 + 1].xyz[2] = v[2]
            temp[i * 2 + 0].xyz[3] = 1.0f // on the model surface
            temp[i * 2 + 1].xyz[3] = 0.0f // will be projected to infinity
        }
        tri.shadowCache = VertexCache.vertexCache.Alloc(temp, tri.numVerts * 2 * shadowCache_s.BYTES)
    }

    /*
     ==================
     R_SkyboxTexGen
     ==================
     */
    fun R_SkyboxTexGen(surf: drawSurf_s, viewOrg: idVec3?) {
        val localViewOrigin = idVec3()
        tr_main.R_GlobalPointToLocal(surf.space!!.modelMatrix, viewOrg!!, localViewOrigin)
        val numVerts: Int = surf.geo!!.numVerts
        val texCoords: Array<idVec3> = idVec3.generateArray(numVerts)
        val verts: Array<idDrawVert> = surf.geo!!.verts as Array<idDrawVert>
        for (i in 0 until numVerts) {
            texCoords[i].set(verts[i].xyz.minus(localViewOrigin))
        }
        surf.dynamicTexCoords = VertexCache.vertexCache.AllocFrameTemp(texCoords, numVerts * idVec3.BYTES)
    }

    // this needs to be greater than the dist from origin to corner of near clip plane
    /*
     ==================
     R_WobbleskyTexGen
     ==================
     */
    fun R_WobbleskyTexGen(surf: drawSurf_s, viewOrg: idVec3?) {
        var i: Int
        val localViewOrigin = idVec3()
        val parms: IntArray = surf.material!!.GetTexGenRegisters()
        var wobbleDegrees: Float = surf.shaderRegisters!![parms[0]]
        var wobbleSpeed: Float = surf.shaderRegisters!![parms[1]]
        var rotateSpeed: Float = surf.shaderRegisters!![parms[2]]
        wobbleDegrees = wobbleDegrees * idMath.PI / 180
        wobbleSpeed = wobbleSpeed * 2 * idMath.PI / 60
        rotateSpeed = rotateSpeed * 2 * idMath.PI / 60

        // very ad-hoc "wobble" transform
        val transform = FloatArray(16)
        val a: Float = tr.viewDef!!.floatTime * wobbleSpeed
        var s: Float = (sin(a) * sin(wobbleDegrees))
        var c: Float = (cos(a) * sin(wobbleDegrees))
        val z: Float = cos(wobbleDegrees)
        val axis: Array<idVec3> = idVec3.generateArray(3)
        axis[2][0] = c
        axis[2][1] = s
        axis[2][2] = z
        axis[1][0] = (-sin((a * 2)) * sin(wobbleDegrees))
        axis[1][2] = (-s * sin(wobbleDegrees))
        axis[1][1] = sqrt(
            (1.0f - (axis[1][0] * axis[1][0] + axis[1][2] * axis[1][2]))
        )

        // make the second vector exactly perpendicular to the first
        axis[1].minusAssign(axis[2].times((axis[2].times(axis[1]))))
        axis[1].Normalize()

        // construct the third with a cross
        axis[0].Cross(axis[1], axis[2])

        // add the rotate
        s = sin((rotateSpeed * tr.viewDef!!.floatTime))
        c = cos((rotateSpeed * tr.viewDef!!.floatTime))
        transform[0] = axis[0][0] * c + axis[1][0] * s
        transform[4] = axis[0][1] * c + axis[1][1] * s
        transform[8] = axis[0][2] * c + axis[1][2] * s
        transform[1] = axis[1][0] * c - axis[0][0] * s
        transform[5] = axis[1][1] * c - axis[0][1] * s
        transform[9] = axis[1][2] * c - axis[0][2] * s
        transform[2] = axis[2][0]
        transform[6] = axis[2][1]
        transform[10] = axis[2][2]
        transform[11] = 0.0f
        transform[7] = transform[11]
        transform[3] = transform[7]
        transform[14] = 0.0f
        transform[13] = transform[14]
        transform[12] = transform[13]
        tr_main.R_GlobalPointToLocal(surf.space!!.modelMatrix, viewOrg!!, localViewOrigin)
        val numVerts: Int = surf.geo!!.numVerts
        val texCoords: Array<idVec3> = idVec3.generateArray(numVerts)
        val verts: Array<idDrawVert> = surf.geo!!.verts as Array<idDrawVert>
        i = 0
        while (i < numVerts) {
            val v = idVec3()
            v[0] = verts[i].xyz[0] - localViewOrigin[0]
            v[1] = verts[i].xyz[1] - localViewOrigin[1]
            v[2] = verts[i].xyz[2] - localViewOrigin[2]
            texCoords[i].set(tr_main.R_LocalPointToGlobal(transform, v))
            i++
        }
        surf.dynamicTexCoords = VertexCache.vertexCache.AllocFrameTemp(texCoords, numVerts * idVec3.BYTES)
    }

    /*
     =================
     R_SpecularTexGen

     Calculates the specular coordinates for cards without vertex programs.
     =================
     */
    fun R_SpecularTexGen(surf: drawSurf_s, globalLightOrigin: idVec3?, viewOrg: idVec3?) {
        val tri: srfTriangles_s
        val localLightOrigin = idVec3()
        val localViewOrigin = idVec3()
        tr_main.R_GlobalPointToLocal(surf.space!!.modelMatrix, globalLightOrigin!!, localLightOrigin)
        tr_main.R_GlobalPointToLocal(surf.space!!.modelMatrix, viewOrg!!, localViewOrigin)
        tri = surf.geo!!

        val texCoords: Array<idVec4> = idVec4.generateArray(tri.numVerts)
        SIMDProcessor!!.CreateSpecularTextureCoords(
            texCoords, localLightOrigin, localViewOrigin,
            tri.verts as Array<idDrawVert>, tri.numVerts, tri.indexes!!, tri.numIndexes
        )
        surf.dynamicTexCoords = VertexCache.vertexCache.AllocFrameTemp(texCoords, tri.numVerts * idVec4.BYTES)
    }

    fun R_SetEntityDefViewEntity(def: idRenderEntityLocal): viewEntity_s {
        val vModel: viewEntity_s
        if (def.viewCount == tr.viewCount) {
            return def.viewEntity!!
        }
        def.viewCount = tr.viewCount

        // set the model and modelview matricies
        vModel = viewEntity_s()
        vModel.entityDef = def

        // the scissorRect will be expanded as the model bounds is accepted into visible portal chains
        vModel.scissorRect.Clear()


        // copy the model and weapon depth hack for back-end use
        vModel.modelDepthHack = def.parms.modelDepthHack
        vModel.weaponDepthHack = def.parms.weaponDepthHack
        tr_main.R_AxisToModelMatrix(def.parms.axis, def.parms.origin, vModel.modelMatrix)

        // we may not have a viewDef if we are just creating shadows at entity creation time
        if (tr.viewDef != null) {
            tr_main.myGlMultMatrix(
                vModel.modelMatrix,
                tr.viewDef!!.worldSpace.modelViewMatrix,
                vModel.modelViewMatrix
            )
            vModel.next = tr.viewDef!!.viewEntitys
            tr.viewDef!!.viewEntitys = vModel
        }
        def.viewEntity = vModel
        return vModel
    }

    //=============================================================================================================================================================================================
    fun R_TestPointInViewLight(org: idVec3?, light: idRenderLightLocal): Boolean {
        var i: Int
        i = 0
        while (i < 6) {
            val d: Float = light.frustum[i].Distance((org)!!)
            if (d > INSIDE_LIGHT_FRUSTUM_SLOP) {
                return false
            }
            i++
        }
        return true
    }

    /*
     ===================
     R_PointInFrustum

     Assumes positive sides face outward
     ===================
     */
    fun R_PointInFrustum(p: idVec3?, planes: Array<idPlane>, numPlanes: Int): Boolean {
        for (i in 0 until numPlanes) {
            val d: Float = planes[i].Distance((p)!!)
            if (d > 0) {
                return false
            }
        }
        return true
    }

    /*
     =============
     R_SetLightDefViewLight

     If the lightDef isn't already on the viewLight list, create
     a viewLight and add it to the list with an empty scissor rect.
     =============
     */
    fun R_SetLightDefViewLight(light: idRenderLightLocal): viewLight_s {
        val vLight: viewLight_s
        if (light.viewCount == tr.viewCount) {
            return light.viewLight!!
        }
        light.viewCount = tr.viewCount

        // add to the view light chain
        vLight = viewLight_s()
        vLight.lightDef = light

        // the scissorRect will be expanded as the light bounds is accepted into visible portal chains
        vLight.scissorRect = idScreenRect()
        vLight.scissorRect!!.Clear()

        // calculate the shadow cap optimization states
        vLight.viewInsideLight = R_TestPointInViewLight(tr.viewDef!!.renderView.vieworg, light)
        if (!vLight.viewInsideLight) {
            vLight.viewSeesShadowPlaneBits = 0
            for (i in 0 until light.numShadowFrustums) {
                val d: Float =
                    light.shadowFrustums[i]!!.planes[5].Distance(tr.viewDef!!.renderView.vieworg)
                if (d < INSIDE_LIGHT_FRUSTUM_SLOP) {
                    vLight.viewSeesShadowPlaneBits = vLight.viewSeesShadowPlaneBits or (1 shl i)
                }
            }
        } else {
            // this should not be referenced in this case
            vLight.viewSeesShadowPlaneBits = 63
        }

        // see if the light center is in view, which will allow us to cull invisible shadows
        vLight.viewSeesGlobalLightOrigin =
            R_PointInFrustum(light.globalLightOrigin, tr.viewDef!!.frustum, 4)

        // copy data used by backend
        vLight.globalLightOrigin.set(light.globalLightOrigin)
        vLight.lightProject[0] = idPlane(light.lightProject[0])
        vLight.lightProject[1] = idPlane(light.lightProject[1])
        vLight.lightProject[2] = idPlane(light.lightProject[2])
        vLight.lightProject[3] = idPlane(light.lightProject[3])
        vLight.fogPlane = idPlane(light.frustum[5])
        vLight.frustumTris = light.frustumTris
        vLight.falloffImage = light.falloffImage
        vLight.lightShader = light.lightShader
        vLight.shaderRegisters = null

        // link the view light
        vLight.next = tr.viewDef!!.viewLights
        tr.viewDef!!.viewLights = vLight
        light.viewLight = vLight
        return vLight
    }

    /*
     =================
     R_LinkLightSurf
     =================
     */
    fun R_LinkLightSurf(
        link: Array<drawSurf_s?>, tri: srfTriangles_s?, spaceView: viewEntity_s?,
        light: idRenderLightLocal, shader: idMaterial?, scissor: idScreenRect?, viewInsideShadow: Boolean
    ) {
        val drawSurf: drawSurf_s
        var space: viewEntity_s? = spaceView
        if (null == space) {
            space = tr.viewDef!!.worldSpace
        }
        drawSurf = drawSurf_s()
        drawSurf.geo = tri
        drawSurf.space = space
        drawSurf.material = shader
        drawSurf.scissorRect = idScreenRect(scissor!!)
        drawSurf.dsFlags = 0
        if (viewInsideShadow) {
            drawSurf.dsFlags = drawSurf.dsFlags or DSF_VIEW_INSIDE_SHADOW
        }
        if (null == shader) {
            // shadows won't have a shader
            drawSurf.shaderRegisters = null
        } else {
            // process the shader expressions for conditionals / color / texcoords
            val constRegs: FloatArray? = shader.ConstantRegisters()
            if (constRegs != null) {
                // this shader has only constants for parameters
                drawSurf.shaderRegisters = constRegs.clone()
            } else {
                val regs = FloatArray(shader.GetNumRegisters())
                drawSurf.shaderRegisters = regs
                shader.EvaluateRegisters(
                    regs,
                    space.entityDef!!.parms.shaderParms,
                    tr.viewDef!!,
                    space.entityDef!!.parms.referenceSound
                )
            }

            // calculate the specular coordinates if we aren't using vertex programs
            if (!tr.backEndRendererHasVertexPrograms && !r_skipSpecular!!.GetBool()) {
                R_SpecularTexGen(drawSurf, light.globalLightOrigin, tr.viewDef!!.renderView.vieworg)
                // if we failed to allocate space for the specular calculations, drop the surface
                if (drawSurf.dynamicTexCoords == null) {
                    return
                }
            }
        }

        // actually link it in
        drawSurf.nextOnLight = link[0]
        link[0] = drawSurf
    }

    /*
     ======================
     R_ClippedLightScissorRectangle
     ======================
     */
    fun R_ClippedLightScissorRectangle(vLight: viewLight_s): idScreenRect {
        var i: Int
        var j: Int
        val light: idRenderLightLocal = vLight.lightDef!!
        val r = idScreenRect()
        val w = idFixedWinding()
        r.Clear()
        i = 0
        while (i < 6) {
            val ow: idWinding? = light.frustumWindings[i]

            // projected lights may have one of the frustums degenerated
            if (null == ow) {
                i++
                continue
            }

            // the light frustum planes face out from the light,
            // so the planes that have the view origin on the negative
            // side will be the "back" faces of the light, which must have
            // some fragment inside the portalStack to be visible
            if (light.frustum[i].Distance(tr.viewDef!!.renderView.vieworg) >= 0) {
                i++
                continue
            }
            w.set(ow)

            // now check the winding against each of the frustum planes
            j = 0
            while (j < 5) {
                if (!w.ClipInPlace(tr.viewDef!!.frustum[j].unaryMinus())) {
                    break
                }
                j++
            }

            // project these points to the screen and add to bounds
            j = 0
            while (j < w.GetNumPoints()) {
                val eye = idPlane()
                val clip = idPlane()
                val ndc = idVec3()
                tr_main.R_TransformModelToClip(
                    w[j].ToVec3(),
                    tr.viewDef!!.worldSpace.modelViewMatrix,
                    tr.viewDef!!.projectionMatrix,
                    eye,
                    clip
                )
                if (clip[3] <= 0.01f) {
                    clip[3] = 0.01f
                }
                tr_main.R_TransformClipToDevice(clip, tr.viewDef, ndc)
                var windowX: Float =
                    0.5f * (1.0f + ndc[0]) * (tr.viewDef!!.viewport.x2 - tr.viewDef!!.viewport.x1)
                var windowY: Float =
                    0.5f * (1.0f + ndc[1]) * (tr.viewDef!!.viewport.y2 - tr.viewDef!!.viewport.y1)
                if (windowX > tr.viewDef!!.scissor.x2) {
                    windowX = tr.viewDef!!.scissor.x2.toFloat()
                } else if (windowX < tr.viewDef!!.scissor.x1) {
                    windowX = tr.viewDef!!.scissor.x1.toFloat()
                }
                if (windowY > tr.viewDef!!.scissor.y2) {
                    windowY = tr.viewDef!!.scissor.y2.toFloat()
                } else if (windowY < tr.viewDef!!.scissor.y1) {
                    windowY = tr.viewDef!!.scissor.y1.toFloat()
                }
                r.AddPoint(windowX, windowY)
                j++
            }
            i++
        }

        // add the fudge boundary
        r.Expand()
        return r
    }

    //================================================================================================================================================================================================
    fun R_CalcLightScissorRectangle(vLight: viewLight_s): idScreenRect {
        val r = idScreenRect()
        val tri: srfTriangles_s
        val eye = idPlane()
        val clip = idPlane()
        val ndc = idVec3()
        if (vLight.lightDef!!.parms.pointLight._val) {
            val bounds = idBounds()
            val lightDef: idRenderLightLocal = vLight.lightDef!!
            tr.viewDef!!.viewFrustum.ProjectionBounds(
                idBox(
                    lightDef.parms.origin,
                    lightDef.parms.lightRadius,
                    lightDef.parms.axis
                ), bounds
            )
            return tr_main.R_ScreenRectFromViewFrustumBounds(bounds)
        }
        if (r_useClippedLightScissors!!.GetInteger() == 2) {
            return R_ClippedLightScissorRectangle(vLight)
        }
        r.Clear()
        tri = vLight.lightDef!!.frustumTris!!
        for (i in 0 until tri.numVerts) {
            tr_main.R_TransformModelToClip(
                tri.verts!![i]!!.xyz, tr.viewDef!!.worldSpace.modelViewMatrix,
                tr.viewDef!!.projectionMatrix, eye, clip
            )

            // if it is near clipped, clip the winding polygons to the view frustum
            if (clip[3] <= 1) {
                c_clippedLight++
                if (r_useClippedLightScissors!!.GetInteger() != 0) {
                    return R_ClippedLightScissorRectangle(vLight)
                } else {
                    r.y1 = 0
                    r.x1 = r.y1
                    r.x2 = (tr.viewDef!!.viewport.x2 - tr.viewDef!!.viewport.x1) - 1
                    r.y2 = (tr.viewDef!!.viewport.y2 - tr.viewDef!!.viewport.y1) - 1
                    return r
                }
            }
            tr_main.R_TransformClipToDevice(clip, tr.viewDef, ndc)
            var windowX: Float =
                0.5f * (1.0f + ndc[0]) * (tr.viewDef!!.viewport.x2 - tr.viewDef!!.viewport.x1)
            var windowY: Float =
                0.5f * (1.0f + ndc[1]) * (tr.viewDef!!.viewport.y2 - tr.viewDef!!.viewport.y1)
            if (windowX > tr.viewDef!!.scissor.x2) {
                windowX = tr.viewDef!!.scissor.x2.toFloat()
            } else if (windowX < tr.viewDef!!.scissor.x1) {
                windowX = tr.viewDef!!.scissor.x1.toFloat()
            }
            if (windowY > tr.viewDef!!.scissor.y2) {
                windowY = tr.viewDef!!.scissor.y2.toFloat()
            } else if (windowY < tr.viewDef!!.scissor.y1) {
                windowY = tr.viewDef!!.scissor.y1.toFloat()
            }
            r.AddPoint(windowX, windowY)
        }

        // add the fudge boundary
        r.Expand()
        c_unclippedLight++
        return r
    }

    /*
     =================
     R_AddLightSurfaces

     Calc the light shader values, removing any light from the viewLight list
     if it is determined to not have any visible effect due to being flashed off or turned off.

     Adds entities to the viewEntity list if they are needed for shadow casting.

     Add any precomputed shadow volumes.

     Removes lights from the viewLights list if they are completely
     turned off, or completely off screen.

     Create any new interactions needed between the viewLights
     and the viewEntitys due to game movement
     =================
     */
    @Throws(idException::class)
    fun R_AddLightSurfaces() {
        var vLight: viewLight_s
        var light: idRenderLightLocal
        var ptr: viewLight_s?
        var prevPtr: viewLight_s?
        var z = 0

        // go through each visible light, possibly removing some from the list
        ptr = tr.viewDef!!.viewLights
        prevPtr = null
        while (ptr != null) {
            z++
            vLight = ptr
            light = vLight.lightDef!!
            val lightShader: idMaterial? = light.lightShader
            if (null == lightShader) {
                Common.common.Error("R_AddLightSurfaces: NULL lightShader")
            }

            // see if we are suppressing the light in this view
            if (!r_skipSuppress!!.GetBool()) {
                if ((light.parms.suppressLightInViewID._val != 0
                            && light.parms.suppressLightInViewID._val == tr.viewDef!!.renderView.viewID)
                ) {
                    if (vLight === tr.viewDef!!.viewLights) {
                        ptr = vLight.next
                        tr.viewDef!!.viewLights = ptr
                    } else {
                        ptr = vLight.next
                        prevPtr!!.next = ptr
                    }
                    light.viewCount = -1
                    continue
                }
                if ((light.parms.allowLightInViewID._val != 0
                            && light.parms.allowLightInViewID._val != tr.viewDef!!.renderView.viewID)
                ) {
                    if (vLight === tr.viewDef!!.viewLights) {
                        ptr = vLight.next
                        tr.viewDef!!.viewLights = ptr
                    } else {
                        ptr = vLight.next
                        prevPtr!!.next = ptr
                    }
                    light.viewCount = -1
                    continue
                }
            }

            // evaluate the light shader registers
            val lightRegs =
                FloatArray(lightShader!!.GetNumRegisters())
            vLight.shaderRegisters = lightRegs
            lightShader.EvaluateRegisters(
                lightRegs,
                light.parms.shaderParms,
                tr.viewDef!!,
                light.parms.referenceSound
            )

            // if this is a purely additive light and no stage in the light shader evaluates
            // to a positive light value, we can completely skip the light
            if (!lightShader.IsFogLight() && !lightShader.IsBlendLight()) {
                var lightStageNum: Int
                lightStageNum = 0
                while (lightStageNum < lightShader.GetNumStages()) {
                    val lightStage: shaderStage_t? = lightShader.GetStage(lightStageNum)

                    // ignore stages that fail the condition
                    if (0.0f == lightRegs[lightStage!!.conditionRegister]) {
                        lightStageNum++
                        continue
                    }
                    val registers: IntArray = lightStage.color.registers

                    // snap tiny values to zero to avoid lights showing up with the wrong color
                    if (lightRegs[registers!![0]] < 0.001) {
                        lightRegs[registers[0]] = 0.0f
                    }
                    if (lightRegs[registers[1]] < 0.001) {
                        lightRegs[registers[1]] = 0.0f
                    }
                    if (lightRegs[registers[2]] < 0.001) {
                        lightRegs[registers[2]] = 0.0f
                    }

                    if ((lightRegs[registers[0]] > 0.0f
                                ) || (lightRegs[registers[1]] > 0.0f
                                ) || (lightRegs[registers[2]] > 0.0f)
                    ) {
                        break
                    }
                    lightStageNum++
                }
                if (lightStageNum == lightShader.GetNumStages()) {
                    // we went through all the stages and didn't find one that adds anything
                    // remove the light from the viewLights list, and change its frame marker
                    // so interaction generation doesn't think the light is visible and
                    // create a shadow for it
                    if (vLight === tr.viewDef!!.viewLights) {
                        ptr = vLight.next
                        tr.viewDef!!.viewLights = ptr
                    } else {
                        ptr = vLight.next
                        prevPtr!!.next = ptr
                    }
                    light.viewCount = -1
                    continue
                }
            }
            if (r_useLightScissors!!.GetBool()) {
                // calculate the screen area covered by the light frustum
                // which will be used to crop the stencil cull
                val scissorRect: idScreenRect = R_CalcLightScissorRectangle(vLight)
                // intersect with the portal crossing scissor rectangle
                vLight.scissorRect!!.Intersect(scissorRect)
                if (r_showLightScissors!!.GetBool()) {
                    tr_main.R_ShowColoredScreenRect(vLight.scissorRect!!, light.index)
                }
            }

            // this one stays on the list
            prevPtr = ptr
            ptr = vLight.next

            // if we are doing a soft-shadow novelty test, regenerate the light with
            // a random offset every time
            if (r_lightSourceRadius!!.GetFloat() != 0.0f) {
                for (i in 0..2) {
                    light.globalLightOrigin[i] += r_lightSourceRadius!!.GetFloat() * (-1f + 2f * ((Math.random() * 0x1000).toInt() and 0xfff) / 0xfff.toFloat())
                }
            }

            // create interactions with all entities the light may touch, and add viewEntities
            // that may cast shadows, even if they aren't directly visible.  Any real work
            // will be deferred until we walk through the viewEntities
            tr.viewDef!!.renderWorld!!.CreateLightDefInteractions(light)
            tr.pc!!.c_viewLights++

            // fog lights will need to draw the light frustum triangles, so make sure they
            // are in the vertex cache
            if (lightShader.IsFogLight()) {
                if (light.frustumTris!!.ambientCache == null) {
                    if (!R_CreateAmbientCache(light.frustumTris!!, false)) {
                        // skip if we are out of vertex memory
                        continue
                    }
                }
                // touch the surface so it won't get purged
                VertexCache.vertexCache.Touch(light.frustumTris!!.ambientCache)
            }

            // add the prelight shadows for the static world geometry
            if (light.parms.prelightModel != null && r_useOptimizedShadows!!.GetBool()) {
                if (0 == light.parms.prelightModel!!.NumSurfaces()) {
                    Common.common.Error("no surfs in prelight model '%s'", light.parms.prelightModel!!.Name())
                }
                val tri: srfTriangles_s = light.parms.prelightModel!!.Surface(0)!!.geometry!!
                if (null == tri.shadowVertexes) {
                    Common.common.Error(
                        "R_AddLightSurfaces: prelight model '%s' without shadowVertexes",
                        light.parms.prelightModel!!.Name()
                    )
                }

                // these shadows will all have valid bounds, and can be culled normally
                if (r_useShadowCulling!!.GetBool()) {
                    if (tr_main.R_CullLocalBox(
                            tri.bounds,
                            tr.viewDef!!.worldSpace.modelMatrix,
                            5,
                            tr.viewDef!!.frustum as Array<idPlane?>
                        )
                    ) {
                        continue
                    }
                }

                // if we have been purged, re-upload the shadowVertexes
                if (tri.shadowCache == null) {
                    R_CreatePrivateShadowCache(tri)
                    if (tri.shadowCache == null) {
                        continue
                    }
                }

                // touch the shadow surface so it won't get purged
                VertexCache.vertexCache.Touch(tri.shadowCache)
                if (tri.indexCache == null && r_useIndexBuffers!!.GetBool()) {
                    tri.indexCache = VertexCache.vertexCache.Alloc(tri.indexes, tri.numIndexes * Integer.BYTES, true)
                }
                if (tri.indexCache != null) {
                    VertexCache.vertexCache.Touch(tri.indexCache)
                }
                R_LinkLightSurf(
                    vLight.globalShadows,
                    tri,
                    null,
                    light,
                    null,
                    vLight.scissorRect,
                    true /* FIXME? */
                )
            }
        }
    }

    /*
     ==================
     R_IssueEntityDefCallback
     ==================
     */
    fun R_IssueEntityDefCallback(def: idRenderEntityLocal): Boolean {
        val update: Boolean
        val oldBounds = idBounds()
        val checkBounds = r_checkBounds!!.GetBool()
        if (checkBounds) {
            oldBounds.set(def.referenceBounds)
        }
        def.archived = false
        tr.pc!!.c_entityDefCallbacks++
        if (tr.viewDef != null) {
            update = def.parms.callback!!.run(def.parms, tr.viewDef!!.renderView)
        } else {
            update = def.parms.callback!!.run(def.parms, null)
        }
        if (null == def.parms.hModel) {
            Common.common.Error("R_IssueEntityDefCallback: dynamic entity callback didn't set model")
            return false
        }
        if (checkBounds) {
            if ((oldBounds[0, 0] > def.referenceBounds[0, 0] + CHECK_BOUNDS_EPSILON
                        ) || (oldBounds[0, 1] > def.referenceBounds[0, 1] + CHECK_BOUNDS_EPSILON
                        ) || (oldBounds[0, 2] > def.referenceBounds[0, 2] + CHECK_BOUNDS_EPSILON
                        ) || (oldBounds[1, 0] < def.referenceBounds[1, 0] - CHECK_BOUNDS_EPSILON
                        ) || (oldBounds[1, 1] < def.referenceBounds[1, 1] - CHECK_BOUNDS_EPSILON
                        ) || (oldBounds[1, 2] < def.referenceBounds[1, 2] - CHECK_BOUNDS_EPSILON)
            ) {
                Common.common.Printf("entity %d callback extended reference bounds\n", def.index)
            }
        }
        return update
    }

    /*
     ===================
     R_EntityDefDynamicModel

     Issues a deferred entity callback if necessary.
     If the model isn't dynamic, it returns the original.
     Returns the cached dynamic model if present, otherwise creates
     it and any necessary overlays
     ===================
     */
    fun R_EntityDefDynamicModel(def: idRenderEntityLocal): idRenderModel? {
        val callbackUpdate: Boolean

        // allow deferred entities to construct themselves
        if (def.parms.callback != null) {
            callbackUpdate = R_IssueEntityDefCallback(def)
        } else {
            callbackUpdate = false
        }
        val model: idRenderModel? = def.parms.hModel
        if (null == model) {
            Common.common.Error("R_EntityDefDynamicModel: NULL model")
        }
        if (model!!.IsDynamicModel() == dynamicModel_t.DM_STATIC) {
            def.dynamicModel = null
            def.dynamicModelFrameCount = 0
            return model
        }

        // continously animating models (particle systems, etc) will have their snapshot updated every single view
        if (callbackUpdate || (model.IsDynamicModel() == dynamicModel_t.DM_CONTINUOUS && def.dynamicModelFrameCount != tr.frameCount)) {
            tr_lightrun.R_ClearEntityDefDynamicModel(def)
        }

        // if we don't have a snapshot of the dynamic model, generate it now
        if (null == def.dynamicModel) {

            // instantiate the snapshot of the dynamic model, possibly reusing memory from the cached snapshot
            def.cachedDynamicModel =
                model.InstantiateDynamicModel(def.parms, tr.viewDef, def.cachedDynamicModel)
            if (def.cachedDynamicModel != null) {

                // add any overlays to the snapshot of the dynamic model
                if (def.overlay != null && !r_skipOverlays!!.GetBool()) {
                    def.overlay!!.AddOverlaySurfacesToModel(def.cachedDynamicModel)
                } else {
                    idRenderModelOverlay.RemoveOverlaySurfacesFromModel(def.cachedDynamicModel!!)
                }
                if (r_checkBounds!!.GetBool()) {
                    val b: idBounds = def.cachedDynamicModel!!.Bounds()
                    if ((b[0, 0] < def.referenceBounds[0, 0] - CHECK_BOUNDS_EPSILON
                                ) || (b[0, 1] < def.referenceBounds[0, 1] - CHECK_BOUNDS_EPSILON
                                ) || (b[0, 2] < def.referenceBounds[0, 2] - CHECK_BOUNDS_EPSILON
                                ) || (b[1, 0] > def.referenceBounds[1, 0] + CHECK_BOUNDS_EPSILON
                                ) || (b[1, 1] > def.referenceBounds[1, 1] + CHECK_BOUNDS_EPSILON
                                ) || (b[1, 2] > def.referenceBounds[1, 2] + CHECK_BOUNDS_EPSILON)
                    ) {
                        Common.common.Printf("entity %d dynamic model exceeded reference bounds\n", def.index)
                    }
                }
            }
            def.dynamicModel = def.cachedDynamicModel
            def.dynamicModelFrameCount = tr.frameCount
        }

        // set model depth hack value
        if ((def.dynamicModel != null) && (model.DepthHack() != 0.0f) && (tr.viewDef != null)) {
            val eye = idPlane()
            val clip = idPlane()
            val ndc = idVec3()
            tr_main.R_TransformModelToClip(
                def.parms.origin,
                tr.viewDef!!.worldSpace.modelViewMatrix,
                tr.viewDef!!.projectionMatrix,
                eye,
                clip
            )
            tr_main.R_TransformClipToDevice(clip, tr.viewDef, ndc)
            def.parms.modelDepthHack = model.DepthHack() * (1.0f - ndc.z)
        }

        return def.dynamicModel
    }

    fun R_AddDrawSurf(
        tri: srfTriangles_s?, space: viewEntity_s, renderEntity: renderEntity_s,
        shader: idMaterial, scissor: idScreenRect?,
        soft_particle_radius: Float = -1.0f
    ) {
        val drawSurf: drawSurf_s
        val shaderParms: FloatArray
        val generatedShaderParms = FloatArray(Material.MAX_ENTITY_SHADER_PARMS)
        drawSurf = drawSurf_s()
        drawSurf.geo = tri
        drawSurf.space = space
        drawSurf.material = shader
        drawSurf.scissorRect = idScreenRect(scissor!!)
        drawSurf.sort = shader.GetSort() + tr.sortOffset

        if (soft_particle_radius != -1.0f) { // #3878
            drawSurf.dsFlags = DSF_SOFT_PARTICLE
            drawSurf.particle_radius = soft_particle_radius
        } else {
            drawSurf.dsFlags = 0
            drawSurf.particle_radius = 0.0f
        }

        // bumping this offset each time causes surfaces with equal sort orders to still
        // deterministically draw in the order they are added
        tr.sortOffset += 0.000001f

        // if it doesn't fit, resize the list
        if (tr.viewDef!!.numDrawSurfs == tr.viewDef!!.maxDrawSurfs) {
            val old: Array<drawSurf_s> = tr.viewDef!!.drawSurfs
            val count: Int
            if (tr.viewDef!!.maxDrawSurfs == 0) {
                tr.viewDef!!.maxDrawSurfs = INITIAL_DRAWSURFS
                count = 0
            } else {
                count = tr.viewDef!!.maxDrawSurfs
                tr.viewDef!!.maxDrawSurfs *= 2
            }
            tr.viewDef!!.drawSurfs =
                drawSurf_s.generateArray(tr.viewDef!!.maxDrawSurfs)
            System.arraycopy(old, 0, tr.viewDef!!.drawSurfs, 0, count)
        }
        tr.viewDef!!.drawSurfs[tr.viewDef!!.numDrawSurfs++] = drawSurf

        // process the shader expressions for conditionals / color / texcoords
        val constRegs: FloatArray? = shader.ConstantRegisters()
        if (constRegs != null) {
            // shader only uses constant values
            drawSurf.shaderRegisters = constRegs.clone()
        } else {
            val regs = FloatArray(shader.GetNumRegisters())
            drawSurf.shaderRegisters = regs

            // a reference shader will take the calculated stage color value from another shader
            // and use that for the parm0-parm3 of the current shader, which allows a stage of
            // a light model and light flares to pick up different flashing tables from
            // different light shaders
            if (renderEntity.referenceShader != null) {
                // evaluate the reference shader to find our shader parms
                val pStage: shaderStage_t
                renderEntity.referenceShader!!.EvaluateRegisters(
                    refRegs,
                    renderEntity.shaderParms,
                    tr.viewDef!!,
                    renderEntity.referenceSound
                )
                pStage = renderEntity.referenceShader!!.GetStage(0)!!

                System.arraycopy(renderEntity.shaderParms, 0, generatedShaderParms, 0, renderEntity.shaderParms.size)
                generatedShaderParms[0] = refRegs[pStage.color.registers[0]]
                generatedShaderParms[1] = refRegs[pStage.color.registers[1]]
                generatedShaderParms[2] = refRegs[pStage.color.registers[2]]
                shaderParms = generatedShaderParms
            } else {
                // evaluate with the entityDef's shader parms
                shaderParms = renderEntity.shaderParms
            }
            var oldFloatTime = 0.0f
            var oldTime = 0
            if (space.entityDef != null && space.entityDef!!.parms.timeGroup != 0) {
                oldFloatTime = tr.viewDef!!.floatTime
                oldTime = tr.viewDef!!.renderView.time
                tr.viewDef!!.floatTime =
                    Game_local.game.GetTimeGroupTime(space.entityDef!!.parms.timeGroup) * 0.001f
                tr.viewDef!!.renderView.time =
                    Game_local.game.GetTimeGroupTime(space.entityDef!!.parms.timeGroup)
            }
            shader.EvaluateRegisters(regs, shaderParms, tr.viewDef!!, renderEntity.referenceSound)
            if (space.entityDef != null && space.entityDef!!.parms.timeGroup != 0) {
                tr.viewDef!!.floatTime = oldFloatTime
                tr.viewDef!!.renderView.time = oldTime
            }
        }

        // check for deformations
        tr_deform.R_DeformDrawSurf(drawSurf)
        when (shader.Texgen()) {
            texgen_t.TG_SKYBOX_CUBE -> R_SkyboxTexGen(drawSurf, tr.viewDef!!.renderView.vieworg)
            texgen_t.TG_WOBBLESKY_CUBE -> R_WobbleskyTexGen(drawSurf, tr.viewDef!!.renderView.vieworg)
            else -> {}
        }

        // check for gui surfaces
        var gui: idUserInterface? = null
        if (null == space.entityDef) {
            gui = shader.GlobalGui()
        } else {
            val guiNum: Int = shader.GetEntityGui() - 1
            if (guiNum >= 0 && guiNum < RenderWorld.MAX_RENDERENTITY_GUI) {
                gui = renderEntity.gui[guiNum]
            }
            if (gui == null) {
                gui = shader.GlobalGui()
            }
        }
        if (gui != null) {
            // force guis on the fast time
            val oldFloatTime: Float
            val oldTime: Int
            oldFloatTime = tr.viewDef!!.floatTime
            oldTime = tr.viewDef!!.renderView.time
            tr.viewDef!!.floatTime = Game_local.game.GetTimeGroupTime(1) * 0.001f
            tr.viewDef!!.renderView.time = Game_local.game.GetTimeGroupTime(1)
            val ndcBounds = idBounds()
            if (!tr_subview.R_PreciseCullSurface(drawSurf, ndcBounds)) {
                tr_guisurf.R_RenderGuiSurf(gui, drawSurf)
            }
            tr.viewDef!!.floatTime = oldFloatTime
            tr.viewDef!!.renderView.time = oldTime
        }

        // we can't add subviews at this point, because that would
        // increment tr.viewCount, messing up the rest of the surface
        // adds for this view
    }

    /*
     ===============
     R_AddAmbientDrawsurfs

     Adds surfaces for the given viewEntity
     Walks through the viewEntitys list and creates drawSurf_t for each surface of
     each viewEntity that has a non-empty scissorRect
     ===============
     */
    fun R_AddAmbientDrawsurfs(vEntity: viewEntity_s) {
        var i: Int
        val total: Int
        val def: idRenderEntityLocal
        var tri: srfTriangles_s?
        val model: idRenderModel
        val shader: Array<idMaterial?> = arrayOf(null)
        def = vEntity.entityDef!!
        if (def.dynamicModel != null) {
            model = def.dynamicModel!!
        } else {
            model = def.parms.hModel!!
        }

        // add all the surfaces
        total = model.NumSurfaces()
        i = 0
        while (i < total) {
            val surf: modelSurface_s? = model.Surface(i)

            // for debugging, only show a single surface at a time
            if (r_singleSurface!!.GetInteger() >= 0 && i != r_singleSurface!!.GetInteger()) {
                i++
                continue
            }
            tri = surf!!.geometry
            if (null == tri) {
                i++
                continue
            }
            if (0 == tri.numIndexes) {
                i++
                continue
            }
            shader[0] = RenderWorld.R_RemapShaderBySkin(surf.shader, def.parms.customSkin, def.parms.customShader)
            RenderWorld.R_GlobalShaderOverride(shader)
            if (null == shader[0]) {
                i++
                continue
            }
            if (!shader[0]!!.IsDrawn()) {
                i++
                continue
            }

            // debugging tool to make sure we are have the correct pre-calculated bounds
            if (r_checkBounds!!.GetBool()) {
                var j: Int
                var k: Int
                j = 0
                while (j < tri.numVerts) {
                    k = 0
                    while (k < 3) {
                        if ((tri.verts!![j]!!.xyz[k] > tri.bounds[1, k] + CHECK_BOUNDS_EPSILON
                                    || tri.verts!![j]!!.xyz[k] < tri.bounds[0, k] - CHECK_BOUNDS_EPSILON)
                        ) {
                            Common.common.Printf(
                                "bad tri.bounds on %s:%s\n", def.parms.hModel!!.Name(), shader[0]!!
                                    .GetName()
                            )
                            break
                        }
                        if ((tri.verts!![j]!!.xyz[k] > def.referenceBounds[1, k] + CHECK_BOUNDS_EPSILON
                                    || tri.verts!![j]!!.xyz[k] < def.referenceBounds[0, k] - CHECK_BOUNDS_EPSILON)
                        ) {
                            Common.common.Printf(
                                "bad referenceBounds on %s:%s\n", def.parms.hModel!!.Name(), shader[0]!!
                                    .GetName()
                            )
                            break
                        }
                        k++
                    }
                    if (k != 3) {
                        break
                    }
                    j++
                }
            }
            if (!tr_main.R_CullLocalBox(
                    tri.bounds,
                    vEntity.modelMatrix,
                    5,
                    tr.viewDef!!.frustum as Array<idPlane?>
                )
            ) {
                def.visibleCount = tr.viewCount

                // make sure we have an ambient cache
                if (!R_CreateAmbientCache(tri, shader[0]!!.ReceivesLighting())) {
                    // don't add anything if the vertex cache was too full to give us an ambient cache
                    return
                }
                // touch it so it won't get purged
                VertexCache.vertexCache.Touch(tri.ambientCache)
                if (r_useIndexBuffers!!.GetBool() && tri.indexCache == null) {
                    tri.indexCache = VertexCache.vertexCache.Alloc(tri.indexes, tri.numIndexes * Integer.BYTES, true)
                }
                if (tri.indexCache != null) {
                    VertexCache.vertexCache.Touch(tri.indexCache)
                }

                // Soft Particles -- SteveL #3878
                var particle_radius = -1.0f
                if (r_useSoftParticles.GetBool() && r_enableDepthCapture.GetInteger() != 0
                    && !shader[0]!!.ReceivesLighting()
                    && tr.viewDef!!.renderView.viewID >= 0
                ) {
                    val prt = def.parms.hModel as? Model_prt.idRenderModelPrt
                    if (prt != null) {
                        particle_radius = prt.SofteningRadius(surf!!.id)
                    }
                }

                // add the surface for drawing
                R_AddDrawSurf(
                    tri,
                    vEntity,
                    vEntity.entityDef!!.parms,
                    shader[0]!!,
                    vEntity.scissorRect,
                    particle_radius
                )

                // ambientViewCount is used to allow light interactions to be rejected
                // if the ambient surface isn't visible at all
                tri.ambientViewCount = tr.viewCount
            }
            i++
        }

        // add the lightweight decal surfaces
        var decal: idRenderModelDecal? = def.decals
        while (decal != null) {
            decal.AddDecalDrawSurf(vEntity)
            decal = decal.Next()
        }
    }

    /*
     ==================
     R_CalcEntityScissorRectangle
     ==================
     */
    fun R_CalcEntityScissorRectangle(vEntity: viewEntity_s): idScreenRect {
        val bounds = idBounds()
        val def: idRenderEntityLocal = vEntity.entityDef!!
        tr.viewDef!!.viewFrustum.ProjectionBounds(
            idBox(def.referenceBounds, def.parms.origin, def.parms.axis),
            bounds
        )
        return tr_main.R_ScreenRectFromViewFrustumBounds(bounds)
    }

    /*
     ===================
     R_ListRenderLightDefs_f
     ===================
     */
    fun R_ListRenderLightDefs_f(args: CmdArgs.idCmdArgs?) {
        var i: Int
        var ldef: idRenderLightLocal?
        if (null == tr.primaryWorld) {
            return
        }
        var active = 0
        var totalRef = 0
        var totalIntr = 0
        i = 0
        while (i < tr.primaryWorld!!.lightDefs.Num()) {
            ldef = tr.primaryWorld!!.lightDefs[i]
            if (null == ldef) {
                Common.common.Printf("%4d: FREED\n", i)
                i++
                continue
            }

            // count up the interactions
            var iCount = 0
            var inter: idInteraction? = ldef.firstInteraction
            while (inter != null) {
                iCount++
                inter = inter.lightNext
            }
            totalIntr += iCount

            // count up the references
            var rCount = 0
            var ref: areaReference_s? = ldef.references
            while (ref != null) {
                rCount++
                ref = ref.ownerNext
            }
            totalRef += rCount
            Common.common.Printf("%4d: %3d intr %2d refs %s\n", i, iCount, rCount, ldef.lightShader!!.GetName())
            active++
            i++
        }
        Common.common.Printf("%d lightDefs, %d interactions, %d areaRefs\n", active, totalIntr, totalRef)
    }

    /*
     ===================
     R_ListRenderEntityDefs_f
     ===================
     */
    fun R_ListRenderEntityDefs_f(args: CmdArgs.idCmdArgs?) {
        var i: Int
        var mdef: idRenderEntityLocal?
        if (null == tr.primaryWorld) {
            return
        }
        var active = 0
        var totalRef = 0
        var totalIntr = 0
        i = 0
        while (i < tr.primaryWorld!!.entityDefs.Num()) {
            mdef = tr.primaryWorld!!.entityDefs[i]
            if (null == mdef) {
                Common.common.Printf("%4d: FREED\n", i)
                i++
                continue
            }

            // count up the interactions
            var iCount = 0
            var inter: idInteraction? = mdef.firstInteraction
            while (inter != null) {
                iCount++
                inter = inter.entityNext
            }
            totalIntr += iCount

            // count up the references
            var rCount = 0
            var ref: areaReference_s? = mdef.entityRefs
            while (ref != null) {
                rCount++
                ref = ref.ownerNext
            }
            totalRef += rCount
            Common.common.Printf("%4d: %3d intr %2d refs %s\n", i, iCount, rCount, mdef.parms.hModel!!.Name())
            active++
            i++
        }
        Common.common.Printf("total active: %d\n", active)
    }

    /*
     ===================
     R_AddModelSurfaces

     Here is where dynamic models actually get instantiated, and necessary
     interactions get created.  This is all done on a sort-by-model basis
     to keep source data in cache (most likely L2) as any interactions and
     shadows are generated, since dynamic models will typically be lit by
     two or more lights.
     ===================
     */
    fun R_AddModelSurfaces() {
        var vEntity: viewEntity_s?
        var inter: idInteraction?
        var next: idInteraction?
        var model: idRenderModel?
        var i = 0

        // clear the ambient surface list
        tr.viewDef!!.numDrawSurfs = 0
        tr.viewDef!!.maxDrawSurfs = 0

        // go through each entity that is either visible to the view, or to
        // any light that intersects the view (for shadows)
        vEntity = tr.viewDef!!.viewEntitys
        while (vEntity != null) {
            if (r_useEntityScissors!!.GetBool()) {
                // calculate the screen area covered by the entity
                val scissorRect: idScreenRect = R_CalcEntityScissorRectangle(vEntity)
                // intersect with the portal crossing scissor rectangle
                vEntity.scissorRect.Intersect(scissorRect)
                if (r_showEntityScissors!!.GetBool()) {
                    tr_main.R_ShowColoredScreenRect(vEntity.scissorRect, vEntity.entityDef!!.index)
                }
            }
            var oldFloatTime = 0.0f
            var oldTime = 0
            Game_local.game.SelectTimeGroup(vEntity.entityDef!!.parms.timeGroup)
            if (vEntity.entityDef!!.parms.timeGroup != 0) {
                oldFloatTime = tr.viewDef!!.floatTime
                oldTime = tr.viewDef!!.renderView.time
                tr.viewDef!!.floatTime =
                    Game_local.game.GetTimeGroupTime(vEntity.entityDef!!.parms.timeGroup) * 0.001f
                tr.viewDef!!.renderView.time =
                    Game_local.game.GetTimeGroupTime(vEntity.entityDef!!.parms.timeGroup)
            }
            if (tr.viewDef!!.isXraySubview && vEntity.entityDef!!.parms.xrayIndex == 1) {
                if (vEntity.entityDef!!.parms.timeGroup != 0) {
                    tr.viewDef!!.floatTime = oldFloatTime
                    tr.viewDef!!.renderView.time = oldTime
                }
                vEntity = vEntity.next
                i++
                continue
            } else if (!tr.viewDef!!.isXraySubview && vEntity.entityDef!!.parms.xrayIndex == 2) {
                if (vEntity.entityDef!!.parms.timeGroup != 0) {
                    tr.viewDef!!.floatTime = oldFloatTime
                    tr.viewDef!!.renderView.time = oldTime
                }
                vEntity = vEntity.next
                i++
                continue
            }

            // Don't let particle entities re-instantiate their dynamic model during
            // non-visible views -- SteveL #3970
            if (tr.viewDef!!.renderView.viewID < 0
                && vEntity.entityDef!!.parms.hModel is Model_prt.idRenderModelPrt
            ) {
                if (vEntity.entityDef!!.parms.timeGroup != 0) {
                    tr.viewDef!!.floatTime = oldFloatTime
                    tr.viewDef!!.renderView.time = oldTime
                }
                vEntity = vEntity.next
                i++
                continue
            }

            // add the ambient surface if it has a visible rectangle
            if (!vEntity.scissorRect.IsEmpty()) {
                model = R_EntityDefDynamicModel(vEntity.entityDef!!)
                if (model == null || model.NumSurfaces() <= 0) {
                    if (vEntity.entityDef!!.parms.timeGroup != 0) {
                        tr.viewDef!!.floatTime = oldFloatTime
                        tr.viewDef!!.renderView.time = oldTime
                    }
                    vEntity = vEntity.next
                    i++
                    continue
                }
                R_AddAmbientDrawsurfs(vEntity)
                tr.pc!!.c_visibleViewEntities++
            } else {
                tr.pc!!.c_shadowViewEntities++
            }

            //
            // for all the entity / light interactions on this entity, add them to the view
            //
            if (tr.viewDef!!.isXraySubview) {
                if (vEntity.entityDef!!.parms.xrayIndex == 2) {
                    inter = vEntity.entityDef!!.firstInteraction
                    while (inter != null && !inter.IsEmpty()) {
                        next = inter.entityNext
                        if (inter.lightDef!!.viewCount != tr.viewCount) {
                            inter = next
                            continue
                        }
                        inter.AddActiveInteraction()
                        inter = next
                    }
                }
            } else {
                // all empty interactions are at the end of the list so once the
                // first is encountered all the remaining interactions are empty
                inter = vEntity.entityDef!!.firstInteraction
                while (inter != null && !inter.IsEmpty()) {
                    next = inter.entityNext

                    // skip any lights that aren't currently visible
                    // this is run after any lights that are turned off have already
                    // been removed from the viewLights list, and had their viewCount cleared
                    if (inter.lightDef!!.viewCount != tr.viewCount) {
                        inter = next
                        continue
                    }
                    inter.AddActiveInteraction()
                    inter = next
                }
            }
            if (vEntity.entityDef!!.parms.timeGroup != 0) {
                tr.viewDef!!.floatTime = oldFloatTime
                tr.viewDef!!.renderView.time = oldTime
            }
            vEntity = vEntity.next
            i++
        }
    }

    /*
     =====================
     R_RemoveUnecessaryViewLights
     =====================
     */
    fun R_RemoveUnecessaryViewLights() {
        var vLight: viewLight_s?

        // go through each visible light
        vLight = tr.viewDef!!.viewLights
        while (vLight != null) {

            // if the light didn't have any lit surfaces visible, there is no need to
            // draw any of the shadows.  We still keep the vLight for debugging
            // draws
            if (vLight.localInteractions[0] == null && vLight.globalInteractions[0] == null &&
                vLight.translucentInteractions[0] == null
            ) {
                vLight.localShadows[0] = null
                vLight.globalShadows[0] = null
            }
            vLight = vLight.next
        }
        if (r_useShadowSurfaceScissor!!.GetBool()) {
            // shrink the light scissor rect to only intersect the surfaces that will actually be drawn.
            // This doesn't seem to actually help, perhaps because the surface scissor
            // rects aren't actually the surface, but only the portal clippings.
            vLight = tr.viewDef!!.viewLights
            while (vLight != null) {
                var surf: drawSurf_s?
                val surfRect = idScreenRect()
                if (!vLight.lightShader!!.LightCastsShadows()) {
                    vLight = vLight.next
                    continue
                }
                surfRect.Clear()
                surf = vLight.globalInteractions[0]
                while (surf != null) {
                    surfRect.Union(surf.scissorRect!!)
                    surf = surf.nextOnLight
                }
                surf = vLight.localShadows[0]
                while (surf != null) {
                    surf.scissorRect!!.Intersect(surfRect)
                    surf = surf.nextOnLight
                }
                surf = vLight.localInteractions[0]
                while (surf != null) {
                    surfRect.Union(surf.scissorRect!!)
                    surf = surf.nextOnLight
                }
                surf = vLight.globalShadows[0]
                while (surf != null) {
                    surf.scissorRect!!.Intersect(surfRect)
                    surf = surf.nextOnLight
                }
                surf = vLight.translucentInteractions[0]
                while (surf != null) {
                    surfRect.Union(surf.scissorRect!!)
                    surf = surf.nextOnLight
                }
                vLight.scissorRect!!.Intersect(surfRect)
                vLight = vLight.next
            }
        }
    }
}

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
import neo.Renderer.Interaction.idInteraction
import neo.Renderer.Material.idMaterial
import neo.Renderer.Model.idRenderModel
import neo.Renderer.Model.srfTriangles_s
import neo.Renderer.ModelDecal.idRenderModelDecal
import neo.Renderer.ModelOverlay.idRenderModelOverlay
import neo.Renderer.RenderWorld.renderLight_s
import neo.Renderer.RenderWorld_local.doublePortal_s
import neo.Renderer.RenderWorld_local.idRenderWorldLocal
import neo.Renderer.RenderWorld_local.portalArea_s
import neo.Renderer.RenderWorld_local.portal_s
import neo.framework.CmdSystem.cmdFunction_t
import neo.framework.Common
import neo.framework.DeclManager
import neo.framework.Session
import neo.idlib.CmdArgs
import neo.idlib.geometry.Winding.idWinding
import neo.idlib.idException
import neo.idlib.math.getVec3Origin
import neo.idlib.math.idPlane
import neo.idlib.math.idVec3
import neo.idlib.math.idVec4

object tr_lightrun {
    /*


     Prelight models

     "_prelight_<lightname>", ie "_prelight_light1"

     Static surfaces available to dmap will be processed to optimized
     shadow and lit surface geometry

     Entity models are never prelighted.

     Light entity can have a "noPrelight 1" key set to avoid the preprocessing
     and carving of the world.  A light that will move should usually have this
     set.

     Prelight models will usually have multiple surfaces

     Shadow volume surfaces will have the material "_shadowVolume"

     The exact same vertexes as the ambient surfaces will be used for the
     non-shadow surfaces, so there is opportunity to share


     Reference their parent surfaces?
     Reference their parent area?


     If we don't track parts that are in different areas, there will be huge
     losses when an areaportal closed door has a light poking slightly
     through it.

     There is potential benefit to splitting even the shadow volumes
     at area boundaries, but it would involve the possibility of an
     extra plane of shadow drawing at the area boundary.


     interaction	lightName	numIndexes

     Shadow volume surface

     Surfaces in the world cannot have "no self shadow" properties, because all
     the surfaces are considered together for the optimized shadow volume.  If
     you want no self shadow on a static surface, you must still make it into an
     entity so it isn't considered in the prelight.


     r_hidePrelights
     r_hideNonPrelights



     each surface could include prelight indexes

     generation procedure in dmap:

     carve original surfaces into areas

     for each light
     build shadow volume and beam tree
     cut all potentially lit surfaces into the beam tree
     move lit fragments into a new optimize group

     optimize groups

     build light models




     */
    /*
     =================
     R_CreateLightRefs
     =================
     */
    val MAX_LIGHT_VERTS: Int = 40

    //======================================================================================
    /*
     ===============
     R_CreateEntityRefs

     Creates all needed model references in portal areas,
     chaining them to both the area and the entityDef.

     Bumps tr.viewCount.
     ===============
     */
    fun R_CreateEntityRefs(def: idRenderEntityLocal) {
        var i: Int
        val transformed: Array<idVec3> = idVec3.generateArray(8)
        val v = idVec3()
        if (null == def.parms.hModel) {
            def.parms.hModel = ModelManager.renderModelManager.DefaultModel()
        }

        // if the entity hasn't been fully specified due to expensive animation calcs
        // for md5 and particles, use the provided conservative bounds.
        if (def.parms.callback != null) {
            def.referenceBounds.set(def.parms.bounds)
        } else {
            def.referenceBounds.set(def.parms.hModel!!.Bounds(def.parms))
        }

        // some models, like empty particles, may not need to be added at all
        if (def.referenceBounds.IsCleared()) {
            return
        }
        if ((r_showUpdates!!.GetBool()
                    && (def.referenceBounds[1, 0] - def.referenceBounds[0, 0] > 1024
                    || def.referenceBounds[1, 1] - def.referenceBounds[0, 1] > 1024))
        ) {
            Common.common.Printf(
                "big entityRef: %f,%f\n", def.referenceBounds[1, 0] - def.referenceBounds[0, 0],
                def.referenceBounds[1, 1] - def.referenceBounds[0, 1]
            )
        }
        i = 0
        while (i < 8) {
            v[0] = def.referenceBounds[(i shr 0) and 1, 0]
            v[1] = def.referenceBounds[(i shr 1) and 1, 1]
            v[2] = def.referenceBounds[(i shr 2) and 1, 2]
            transformed[i].set(tr_main.R_LocalPointToGlobal(def.modelMatrix, v))
            i++
        }

        // bump the view count so we can tell if an
        // area already has a reference
        tr.viewCount++

        // push these points down the BSP tree into areas
        def.world!!.PushVolumeIntoTree(def, null, 8, transformed)
    }

    /*
     =================================================================================

     CREATE LIGHT REFS

     =================================================================================
     */
    /*
     =====================
     R_SetLightProject

     All values are reletive to the origin
     Assumes that right and up are not normalized
     This is also called by dmap during map processing.
     =====================
     */
    fun R_SetLightProject(
        lightProject: Array<idPlane> /*[4]*/, origin: idVec3, target: idVec3,
        rightVector: idVec3?, upVector: idVec3?, start: idVec3, stop: idVec3
    ) {
        var dist: Float
        var scale: Float
        val rLen: Float
        val uLen: Float
        val normal = idVec3()
        var ofs: Float
        val right = idVec3()
        val up = idVec3()
        val startGlobal = idVec3()
        val targetGlobal = idVec4()
        right.set((rightVector)!!)
        rLen = right.Normalize()
        up.set((upVector)!!)
        uLen = up.Normalize()
        normal.set(up.Cross(right))
        normal.Normalize()
        dist = target.times(normal)
        if (dist < 0) {
            dist = -dist
            normal.set(normal.unaryMinus())
        }
        scale = (0.5f * dist) / rLen
        right.timesAssign(scale)
        scale = -(0.5f * dist) / uLen
        up.timesAssign(scale)
        lightProject[2].set(normal)
        lightProject[2][3] = -(origin.times(lightProject[2].Normal()))
        lightProject[0].set(right)
        lightProject[0][3] = -(origin.times(lightProject[0].Normal()))
        lightProject[1].set(up)
        lightProject[1][3] = -(origin.times(lightProject[1].Normal()))

        // now offset to center
        targetGlobal.set(target + origin)
        targetGlobal[3] = 1.0f
        ofs = 0.5f - (targetGlobal * lightProject[0].ToVec4()) / (targetGlobal * lightProject[2].ToVec4())
        lightProject[0].ToVec4_oPluSet(lightProject[2].ToVec4() * ofs)
        ofs = 0.5f - (targetGlobal * lightProject[1].ToVec4()) / (targetGlobal * lightProject[2].ToVec4())
        lightProject[1].ToVec4_oPluSet(lightProject[2].ToVec4() * ofs)

        // set the falloff vector
        normal.set(stop.minus(start))
        dist = normal.Normalize()
        if (dist <= 0) {
            dist = 1.0f
        }
        lightProject[3].set(normal.times(1.0f / dist))
        startGlobal.set(start.plus(origin))
        lightProject[3][3] = -(startGlobal.times(lightProject[3].Normal()))
    }

    /*
     ===================
     R_SetLightFrustum

     Creates plane equations from the light projection, positive sides
     face out of the light
     ===================
     */
    fun R_SetLightFrustum(lightProject: Array<idPlane> /*[4]*/, frustum: Array<idPlane> /*[6]*/) {
        var i: Int

        // we want the planes of s=0, s=q, t=0, and t=q
        frustum[0] = idPlane(lightProject[0])
        frustum[1] = idPlane(lightProject[1])
        frustum[2] = idPlane(lightProject[2].minus(lightProject[0]))
        frustum[3] = idPlane(lightProject[2].minus(lightProject[1]))

        // we want the planes of s=0 and s=1 for front and rear clipping planes
        frustum[4] = idPlane(lightProject[3])
        frustum[5] = idPlane(lightProject[3])
        frustum[5].minusAssign(3, 1.0f)
        frustum[5] = idPlane(frustum[5].unaryMinus())
        i = 0
        while (i < 6) {
            var f: Float
            frustum[i] = idPlane(frustum[i].unaryMinus())
            f = frustum[i].Normalize()
            frustum[i].divAssign(3, f)
            i++
        }
    }

    /*
     ====================
     R_FreeLightDefFrustum
     ====================
     */
    fun R_FreeLightDefFrustum(ldef: idRenderLightLocal) {
        var i: Int

        // free the frustum tris
        if (ldef.frustumTris != null) {
            R_FreeStaticTriSurf(ldef.frustumTris)
            ldef.frustumTris = null
        }
        // free frustum windings
        i = 0
        while (i < 6) {
            if (ldef.frustumWindings[i] != null) {
                ldef.frustumWindings[i] = null
            }
            i++
        }
    }

    /*
     =================
     R_DeriveLightData

     Fills everything in based on light.parms
     =================
     */
    @Throws(idException::class)
    fun R_DeriveLightData(light: idRenderLightLocal) {
        var i: Int

        // decide which light shader we are going to use
        if (light.parms.shader != null) {
            light.lightShader = light.parms.shader
        }
        if (null == light.lightShader) {
            if (light.parms.pointLight._val) {
                light.lightShader = DeclManager.declManager.FindMaterial("lights/defaultPointLight")
            } else {
                light.lightShader = DeclManager.declManager.FindMaterial("lights/defaultProjectedLight")
            }
        }

        // get the falloff image
        light.falloffImage = light.lightShader!!.LightFalloffImage()
        if (null == light.falloffImage) {
            // use the falloff from the default shader of the correct type
            val defaultShader: idMaterial?
            if (light.parms.pointLight._val) {
                defaultShader = DeclManager.declManager.FindMaterial("lights/defaultPointLight")
                light.falloffImage = defaultShader!!.LightFalloffImage()
            } else {
                // projected lights by default don't diminish with distance
                defaultShader = DeclManager.declManager.FindMaterial("lights/defaultProjectedLight")
                light.falloffImage = defaultShader!!.LightFalloffImage()
            }
        }

        // set the projection
        if (!light.parms.pointLight._val) {
            // projected light
            R_SetLightProject(
                light.lightProject, getVec3Origin() /* light.parms.origin */, light.parms.target,
                light.parms.right, light.parms.up, light.parms.start, light.parms.end
            )
        } else {
            // point light
            for (l in light.lightProject.indices) {
                light.lightProject[l] = idPlane()
            }
            light.lightProject[0][0] = 0.5f / light.parms.lightRadius[0]
            light.lightProject[1][1] = 0.5f / light.parms.lightRadius[1]
            light.lightProject[3][2] = 0.5f / light.parms.lightRadius[2]
            light.lightProject[0][3] = 0.5f
            light.lightProject[1][3] = 0.5f
            light.lightProject[2][3] = 1.0f
            light.lightProject[3][3] = 0.5f
        }

        // set the frustum planes
        R_SetLightFrustum(light.lightProject, light.frustum)

        // rotate the light planes and projections by the axis
        tr_main.R_AxisToModelMatrix(light.parms.axis, light.parms.origin, light.modelMatrix)
        i = 0
        while (i < 6) {
            val temp = idPlane()
            temp.set(light.frustum[i])
            tr_main.R_LocalPlaneToGlobal(light.modelMatrix, temp, light.frustum[i])
            i++
        }
        i = 0
        while (i < 4) {
            val temp = idPlane()
            temp.set(light.lightProject[i])
            tr_main.R_LocalPlaneToGlobal(light.modelMatrix, temp, light.lightProject[i])
            i++
        }

        // adjust global light origin for off center projections and parallel projections
        // we are just faking parallel by making it a very far off center for now
        if (light.parms.parallel._val) {
            val dir = idVec3()
            dir.set(light.parms.lightCenter)
            if (0.0f == dir.Normalize()) {
                // make point straight up if not specified
                dir[2] = 1.0f
            }
            light.globalLightOrigin.set(light.parms.origin.plus(dir.times(100000)))
        } else {
            light.globalLightOrigin.set(light.parms.origin.plus(light.parms.axis.times(light.parms.lightCenter)))
        }
        R_FreeLightDefFrustum(light)
        light.frustumTris = tr_polytope.R_PolytopeSurface(6, light.frustum, light.frustumWindings)

        // a projected light will have one shadowFrustum, a point light will have
        // six unless the light center is outside the box
        tr_stencilshadow.R_MakeShadowFrustums(light)
    }

    fun R_CreateLightRefs(light: idRenderLightLocal) {
        val points: Array<idVec3> = idVec3.generateArray(MAX_LIGHT_VERTS)
        var i: Int
        val tri: srfTriangles_s
        tri = light.frustumTris!!

        // because a light frustum is made of only six intersecting planes,
        // we should never be able to get a stupid number of points...
        if (tri.numVerts > MAX_LIGHT_VERTS) {
            Common.common.Error("R_CreateLightRefs: %d points in frustumTris!", tri.numVerts)
        }
        i = 0
        while (i < tri.numVerts) {
            points[i].set(tri.verts!!.get(i)!!.xyz)
            i++
        }
        if (r_showUpdates!!.GetBool() && ((tri.bounds[1, 0] - tri.bounds[0, 0] > 1024
                    || tri.bounds[1, 1] - tri.bounds[0, 1] > 1024))
        ) {
            Common.common.Printf(
                "big lightRef: %f,%f\n",
                tri.bounds[1, 0] - tri.bounds[0, 0],
                tri.bounds[1, 1] - tri.bounds[0, 1]
            )
        }

        // determine the areaNum for the light origin, which may let us
        // cull the light if it is behind a closed door
        // it is debatable if we want to use the entity origin or the center offset origin,
        // but we definitely don't want to use a parallel offset origin
        light.areaNum = light.world!!.PointInArea(light.globalLightOrigin)
        if (light.areaNum == -1) {
            light.areaNum = light.world!!.PointInArea(light.parms.origin)
        }

        // bump the view count so we can tell if an
        // area already has a reference
        tr.viewCount++

        // if we have a prelight model that includes all the shadows for the major world occluders,
        // we can limit the area references to those visible through the portals from the light center.
        // We can't do this in the normal case, because shadows are cast from back facing triangles, which
        // may be in areas not directly visible to the light projection center.
        if ((light.parms.prelightModel != null) && r_useLightPortalFlow!!.GetBool() && light.lightShader!!.LightCastsShadows()) {
            light.world!!.FlowLightThroughPortals(light)
        } else {
            // push these points down the BSP tree into areas
            light.world!!.PushVolumeIntoTree(null, light, tri.numVerts, points)
        }
    }

    /*
     ===============
     R_RenderLightFrustum

     Called by the editor and dmap to operate on light volumes
     ===============
     */
    fun R_RenderLightFrustum(renderLight: renderLight_s?, lightFrustum: Array<idPlane?> /*[6]*/) {
        val fakeLight = idRenderLightLocal()
        fakeLight.parms = renderLight!!
        R_DeriveLightData(fakeLight)
        R_FreeStaticTriSurf(fakeLight.frustumTris)
        for (i in 0..5) {
            lightFrustum[i] = fakeLight.frustum[i]
        }
    }

    /*
     ===============
     WindingCompletelyInsideLight
     ===============
     */
    fun WindingCompletelyInsideLight(w: idWinding, ldef: idRenderLightLocal): Boolean {
        var i: Int
        var j: Int
        i = 0
        while (i < w.GetNumPoints()) {
            j = 0
            while (j < 6) {
                var d: Float
                d = w[i].ToVec3().times(ldef.frustum[j].Normal()) + ldef.frustum[j][3]
                if (d > 0) {
                    return false
                }
                j++
            }
            i++
        }
        return true
    }

    //=================================================================================
    /*
     ======================
     R_CreateLightDefFogPortals

     When a fog light is created or moved, see if it completely
     encloses any portals, which may allow them to be fogged closed.
     ======================
     */
    fun R_CreateLightDefFogPortals(ldef: idRenderLightLocal) {
        var lref: areaReference_s?
        var area: portalArea_s
        ldef.foggedPortals = null
        if (!ldef.lightShader!!.IsFogLight()) {
            return
        }

        // some fog lights will explicitly disallow portal fogging
        if (ldef.lightShader!!.TestMaterialFlag(Material.MF_NOPORTALFOG)) {
            return
        }
        lref = ldef.references
        while (lref != null) {

            // check all the models in this area
            area = lref.area!!
            var prt: portal_s?
            var dp: doublePortal_s
            prt = area.portals
            while (prt != null) {
                dp = prt.doublePortal!!

                // we only handle a single fog volume covering a portal
                // this will never cause incorrect drawing, but it may
                // fail to cull a portal
                if (dp.fogLight != null) {
                    prt = prt.next
                    continue
                }
                if (WindingCompletelyInsideLight(prt.w!!, ldef)) {
                    dp.fogLight = ldef
                    dp.nextFoggedPortal = ldef.foggedPortals
                    ldef.foggedPortals = dp
                }
                prt = prt.next
            }
            lref = lref.ownerNext
        }
    }

    /*
     ====================
     R_FreeLightDefDerivedData

     Frees all references and lit surfaces from the light
     ====================
     */
    fun R_FreeLightDefDerivedData(ldef: idRenderLightLocal) {
        var lref: areaReference_s?
        var nextRef: areaReference_s?

        // rmove any portal fog references
        var dp: doublePortal_s? = ldef.foggedPortals
        while (dp != null) {
            dp.fogLight = null
            dp = dp.nextFoggedPortal
        }

        // free all the interactions
        while (ldef.firstInteraction != null) {
            ldef.firstInteraction!!.UnlinkAndFree()
        }

        // free all the references to the light
        lref = ldef.references
        while (lref != null) {
            nextRef = lref.ownerNext

            // unlink from the area
            lref.areaNext!!.areaPrev = lref.areaPrev
            lref.areaPrev!!.areaNext = lref.areaNext
            lref = nextRef
        }
        ldef.references = null
        R_FreeLightDefFrustum(ldef)
    }

    /*
     ===================
     R_FreeEntityDefDerivedData

     Used by both RE_FreeEntityDef and RE_UpdateEntityDef
     Does not actually free the entityDef.
     ===================
     */
    fun R_FreeEntityDefDerivedData(def: idRenderEntityLocal, keepDecals: Boolean, keepCachedDynamicModel: Boolean) {
        var i: Int
        var ref: areaReference_s?
        var next: areaReference_s?

        // demo playback needs to free the joints, while normal play
        // leaves them in the control of the game
        if (Session.session.readDemo != null) {
            if (def.parms.joints != null) {
                def.parms.joints = null
            }
            if (def.parms.callbackData != null) {
                def.parms.callbackData = null
            }
            i = 0
            while (i < RenderWorld.MAX_RENDERENTITY_GUI) {
                if (def.parms.gui[i] != null) {
                    def.parms.gui[i] = null
                }
                i++
            }
        }

        // free all the interactions
        while (def.firstInteraction != null) {
            def.firstInteraction!!.UnlinkAndFree()
        }

        // clear the dynamic model if present
        if (def.dynamicModel != null) {
            def.dynamicModel = null
        }
        if (!keepDecals) {
            R_FreeEntityDefDecals(def)
            R_FreeEntityDefOverlay(def)
        }
        if (!keepCachedDynamicModel) {
            def.cachedDynamicModel = null
        }

        // free the entityRefs from the areas
        ref = def.entityRefs
        while (ref != null) {
            next = ref.ownerNext

            // unlink from the area
            ref.areaNext!!.areaPrev = ref.areaPrev
            ref.areaPrev!!.areaNext = ref.areaNext
            ref = next
        }
        def.entityRefs = null
    }

    /*
     ==================
     R_ClearEntityDefDynamicModel

     If we know the reference bounds stays the same, we
     only need to do this on entity update, not the full
     R_FreeEntityDefDerivedData
     ==================
     */
    fun R_ClearEntityDefDynamicModel(def: idRenderEntityLocal) {
        // free all the interaction surfaces
        var inter: idInteraction? = def.firstInteraction
        while (inter != null && !inter.IsEmpty()) {
            inter.FreeSurfaces()
            inter = inter.entityNext
        }

        // clear the dynamic model if present
        if (def.dynamicModel != null) {
            def.dynamicModel = null
        }
    }

    /*
     ===================
     R_FreeEntityDefDecals
     ===================
     */
    fun R_FreeEntityDefDecals(def: idRenderEntityLocal) {
        while (def.decals != null) {
            val next: idRenderModelDecal? = def.decals!!.Next()
            idRenderModelDecal.Free(def.decals)
            def.decals = next
        }
    }

    /*
     ===================
     R_FreeEntityDefFadedDecals
     ===================
     */
    fun R_FreeEntityDefFadedDecals(def: idRenderEntityLocal, time: Int) {
        def.decals = idRenderModelDecal.RemoveFadedDecals(def.decals, time)
    }

    /*
     ===================
     R_FreeEntityDefOverlay
     ===================
     */
    fun R_FreeEntityDefOverlay(def: idRenderEntityLocal) {
        if (def.overlay != null) {
            idRenderModelOverlay.Free(def.overlay)
            def.overlay = null
        }
    }

    /*
     ===================
     R_FreeDerivedData

     ReloadModels and RegenerateWorld call this
     ===================
     */
    fun R_FreeDerivedData() {
        var i: Int
        var j: Int
        var rw: idRenderWorldLocal
        var def: idRenderEntityLocal?
        var light: idRenderLightLocal?
        j = 0
        while (j < tr.worlds.Num()) {
            rw = tr.worlds[j]
            i = 0
            while (i < rw.entityDefs.Num()) {
                def = rw.entityDefs.get(i)
                if (null == def) {
                    i++
                    continue
                }
                R_FreeEntityDefDerivedData(def, false, false)
                i++
            }
            i = 0
            while (i < rw.lightDefs.Num()) {
                light = rw.lightDefs.get(i)
                if (null == light) {
                    i++
                    continue
                }
                R_FreeLightDefDerivedData(light)
                i++
            }
            j++
        }
    }

    /*
     ===================
     R_CheckForEntityDefsUsingModel
     ===================
     */
    fun R_CheckForEntityDefsUsingModel(model: idRenderModel) {
        var i: Int
        var j: Int
        var rw: idRenderWorldLocal
        var def: idRenderEntityLocal?
        j = 0
        while (j < tr.worlds.Num()) {
            rw = tr.worlds[j]
            i = 0
            while (i < rw.entityDefs.Num()) {
                def = rw.entityDefs.get(i)
                if (null == def) {
                    i++
                    continue
                }
                if (def.parms.hModel === model) {
                    // this should never happen but Radiant messes it up all the time so just free the derived data
                    R_FreeEntityDefDerivedData(def, false, false)
                }
                i++
            }
            j++
        }
    }

    /*
     ===================
     R_ReCreateWorldReferences

     ReloadModels and RegenerateWorld call this
     ===================
     */
    fun R_ReCreateWorldReferences() {
        var i: Int
        var j: Int
        var rw: idRenderWorldLocal
        var def: idRenderEntityLocal?
        var light: idRenderLightLocal?

        // let the interaction generation code know this shouldn't be optimized for
        // a particular view
        tr.viewDef = null
        j = 0
        while (j < tr.worlds.Num()) {
            rw = tr.worlds[j]
            i = 0
            while (i < rw.entityDefs.Num()) {
                def = rw.entityDefs.get(i)
                if (null == def) {
                    i++
                    continue
                }
                // the world model entities are put specifically in a single
                // area, instead of just pushing their bounds into the tree
                if (i < rw.numPortalAreas) {
                    rw.AddEntityRefToArea(def, rw.portalAreas!!.get(i))
                } else {
                    R_CreateEntityRefs(def)
                }
                i++
            }
            i = 0
            while (i < rw.lightDefs.Num()) {
                light = rw.lightDefs.get(i)
                if (null == light) {
                    i++
                    continue
                }
                val parms: renderLight_s = light.parms
                light.world!!.FreeLightDef(i)
                rw.UpdateLightDef(i, parms)
                i++
            }
            j++
        }
    }

    /*
     =================================================================================

     LIGHT TESTING

     =================================================================================
     */
    /*
     ====================
     R_ModulateLights_f

     Modifies the shaderParms on all the lights so the level
     designers can easily test different color schemes
     ====================
     */
    class R_ModulateLights_f private constructor() : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            if (null == tr.primaryWorld) {
                return
            }
            if (args!!.Argc() != 4) {
                Common.common.Printf("usage: modulateLights <redFloat> <greenFloat> <blueFloat>\n")
                return
            }
            val modulate = FloatArray(3)
            var i: Int
            i = 0
            while (i < 3) {
                modulate[i] = args.Argv(i + 1).toFloat()
                i++
            }
            var count = 0
            i = 0
            while (i < tr.primaryWorld!!.lightDefs.Num()) {
                var light: idRenderLightLocal?
                light = tr.primaryWorld!!.lightDefs[i]
                if (light != null) {
                    count++
                    for (j in 0..2) {
                        light.parms.shaderParms[j] *= modulate[j]
                    }
                }
                i++
            }
            Common.common.Printf("modulated %d lights\n", count)
        }

        companion object {
            val instance: cmdFunction_t = R_ModulateLights_f()
        }
    }

    /*
     ===================
     R_RegenerateWorld_f

     Frees and regenerates all references and interactions, which
     must be done when switching between display list mode and immediate mode
     ===================
     */
    class R_RegenerateWorld_f private constructor() : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            R_FreeDerivedData()

            // watch how much memory we allocate
            tr.staticAllocCount = 0
            R_ReCreateWorldReferences()
            Common.common.Printf("Regenerated world, staticAllocCount = %d.\n", tr.staticAllocCount)
        }

        companion object {
            val instance: cmdFunction_t = R_RegenerateWorld_f()
        }
    }
}

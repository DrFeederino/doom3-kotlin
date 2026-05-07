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

import neo.Renderer.Interaction.idInteraction
import neo.Renderer.Material.idMaterial
import neo.Renderer.Model.idRenderModel
import neo.Sound.sound.idSoundEmitter
import neo.framework.CmdSystem.cmdFunction_t
import neo.framework.Common.Companion.common
import neo.framework.DeclManager
import neo.framework.DeclSkin.idDeclSkin
import neo.framework.DemoFile.idDemoFile
import neo.framework.File_h.idFile
import neo.idlib.BV.Box.idBox
import neo.idlib.BV.Frustum.idFrustum
import neo.idlib.BV.Sphere.idSphere
import neo.idlib.BV.idBounds
import neo.idlib.CmdArgs
import neo.idlib.containers.CBool
import neo.idlib.containers.CFloat
import neo.idlib.containers.CInt
import neo.idlib.geometry.JointTransform.idJointMat
import neo.idlib.geometry.Winding.idFixedWinding
import neo.idlib.geometry.Winding.idWinding
import neo.idlib.idException
import neo.idlib.idSerializable
import neo.idlib.math.Matrix.idMat3
import neo.idlib.math.idPlane
import neo.idlib.math.idVec3
import neo.idlib.math.idVec4
import neo.idlib.math.vec3_origin
import neo.ui.UserInterface.idUserInterface
import java.nio.*
import java.util.*

object RenderWorld {
    //
    // shader parms
    val MAX_GLOBAL_SHADER_PARMS: Int = 12

    //
    // guis
    val MAX_RENDERENTITY_GUI: Int = 3

    /*
     ===============================================================================

     Render World

     ===============================================================================
     */
    val PROC_FILE_EXT: String = "proc"
    val PROC_FILE_ID: String = "mapProcFile003"
    val SHADERPARM_ALPHA: Int = 3

    //
    val SHADERPARM_BEAM_END_X: Int = 8 // for _beam models
    val SHADERPARM_BEAM_END_Y: Int = 9
    val SHADERPARM_BEAM_END_Z: Int = 10
    val SHADERPARM_BEAM_WIDTH: Int = 11
    val SHADERPARM_BLUE: Int = 2
    val SHADERPARM_DIVERSITY: Int = 5 // random between 0.0f and 1.0f for some effects (muzzle flashes, etc)
    val SHADERPARM_GREEN: Int = 1
    val SHADERPARM_MD3_BACKLERP: Int = 10

    //
    val SHADERPARM_MD3_FRAME: Int = 8
    val SHADERPARM_MD3_LASTFRAME: Int = 9

    //
    // model parms
    val SHADERPARM_MD5_SKINSCALE: Int = 8 // for scaling vertex offsets on md5 models (jack skellington effect)
    val SHADERPARM_MODE: Int = 7 // for selecting which shader passes to enable

    //
    val SHADERPARM_PARTICLE_STOPTIME: Int = 8 // don't spawn any more particles after this time

    //
    val SHADERPARM_RED: Int = 0
    val SHADERPARM_SPRITE_HEIGHT: Int = 9

    //
    val SHADERPARM_SPRITE_WIDTH: Int = 8
    val SHADERPARM_TIMEOFFSET: Int = 4
    val SHADERPARM_TIMESCALE: Int = 3
    val SHADERPARM_TIME_OF_DEATH: Int = 7 // for the monster skin-burn-away effect enable and time offset

    //    
    val NUM_PORTAL_ATTRIBUTES: Int = 3 //PS_BLOCK_ALL needs to be changed manually if this value is changed.

    /*
     ===============
     R_GlobalShaderOverride
     ===============
     */
    @Throws(idException::class)
    fun R_GlobalShaderOverride(shader: Array<idMaterial?>): Boolean {
        if (shader[0] == null || !shader[0]!!.IsDrawn()) {
            return false
        }
        if (tr.primaryRenderView!!.globalMaterial != null) {
            shader[0] = tr.primaryRenderView!!.globalMaterial
            return true
        }
        if (r_materialOverride!!.GetString() != null && !r_materialOverride!!.GetString()!!
                .isEmpty()
        ) {
            shader[0] = DeclManager.declManager.FindMaterial(r_materialOverride!!.GetString()!!)
            return true
        }
        return false
    }

    /*
     ===============
     R_RemapShaderBySkin
     ===============
     */
    fun R_RemapShaderBySkin(shader: idMaterial?, skin: idDeclSkin?, customShader: idMaterial?): idMaterial? {
        if (null == shader) {
            return null
        }

        // never remap surfaces that were originally nodraw, like collision hulls
        if (!shader.IsDrawn()) {
            return shader
        }
        if (customShader != null) {
            // this is sort of a hack, but cause deformed surfaces to map to empty surfaces,
            // so the item highlight overlay doesn't highlight the autosprite surface
            if (shader.Deform() != null) {
                return null
            }
            return customShader
        }
        if (null == skin) {
            return shader
        }
        return skin.RemapShaderBySkin(shader)
    }

    enum class portalConnection_t {
        PS_BLOCK_NONE,

        // = 0,
        //
        PS_BLOCK_VIEW,

        // = 1,
        PS_BLOCK_LOCATION,
        // = 2,  // game map location strings often stop in hallways
        /**
         * padding
         */
        __3,
        PS_BLOCK_AIR,
        // = 4,       // windows between pressurized and unpresurized areas
        //
        /**
         * padding
         */
        __5,

        /**
         * padding
         */
        __6,
        PS_BLOCK_ALL //= (1 << NUM_PORTAL_ATTRIBUTES) - 1
    }

    abstract class deferredEntityCallback_t {
        abstract fun run(e: renderEntity_s?, v: renderView_s?): Boolean
    }

    class renderViewShadow {
        var cramZNear: CBool = CBool(false)
        var forceUpdate: CBool = CBool(false)

        var fov_x: CFloat = CFloat()
        var fov_y: CFloat = CFloat()

        var globalMaterial: idMaterial? = null
        var shaderParms = Array(MAX_GLOBAL_SHADER_PARMS) { CFloat() }
        var time: CInt = CInt()
        var viewID: CInt = CInt()

        val viewaxis: idMat3 = idMat3()
        val vieworg: idVec3 = idVec3()

        var x: CInt = CInt()
        var y: CInt = CInt()

        var width: CInt = CInt()
        var height: CInt = CInt()
    }

    class renderEntityShadow {
        var allowSurfaceInViewID: CInt = CInt()
        val axis: idMat3 = idMat3()
        var bodyId: CInt = CInt()
        val bounds: idBounds = idBounds()
        var callback: deferredEntityCallback_t? = null
        var callbackData: ByteBuffer? = null
        var customShader: idMaterial? = null
        var customSkin: idDeclSkin? = null
        var entityNum: CInt = CInt()
        var forceUpdate: CInt = CInt()
        var gui: Array<idUserInterface?> = arrayOfNulls(MAX_RENDERENTITY_GUI)
        var hModel: idRenderModel? = null
        var joints: Array<idJointMat>? = null
        var modelDepthHack: CFloat = CFloat()
        var noDynamicInteractions: CBool = CBool()
        var noSelfShadow: CBool = CBool()
        var noShadow: CBool = CBool()
        var numJoints: CInt = CInt()
        val origin: idVec3 = idVec3()
        var referenceShader: idMaterial? = null
        var referenceSound: idSoundEmitter? = null
        var remoteRenderView: renderView_s? = null
        var shaderParms = Array(Material.MAX_ENTITY_SHADER_PARMS) { CFloat() }
        var suppressShadowInLightID: CInt = CInt()
        var suppressShadowInViewID: CInt = CInt()
        var suppressSurfaceInViewID: CInt = CInt()
        var timeGroup: CInt = CInt()
        var weaponDepthHack: CBool = CBool()
        var xrayIndex: CInt = CInt()
    }

    class renderEntity_s {
        val shaderParms: FloatArray =
            FloatArray(Material.MAX_ENTITY_SHADER_PARMS) // can be used in any way by shader or model generation
        private val DBG_count: Int = DBG_counter++

        //
        // if non-zero, the surface and shadow (if it casts one)
        // will only show up in the specific view, ie: player weapons
        var allowSurfaceInViewID: Int = 0
        val axis: idMat3 = idMat3()
        var bodyId: Int = 0

        //
        // Entities that are expensive to generate, like skeletal models, can be
        // deferred until their bounds are found to be in view, in the frustum
        // of a shadowing light that is in view, or contacted by a trace / overlay test.
        // This is also used to do visual cueing on items in the view
        // The renderView may be NULL if the callback is being issued for a non-view related
        // source.
        // The callback function should clear renderEntity->callback if it doesn't
        // want to be called again next time the entity is referenced (ie, if the
        // callback has now made the entity valid until the next updateEntity)
        val bounds // only needs to be set for deferred models and md5s
                : idBounds = idBounds()
        var callback: deferredEntityCallback_t? = null

        //
        var callbackData: ByteBuffer? = null // used for whatever the callback wants

        //
        // texturing
        var customShader: idMaterial? = null // if non-0, all surfaces will use this
        var customSkin: idDeclSkin? = null // 0 for no remappings

        //
        var entityNum: Int = 0

        // this automatically implies noShadow
        var forceUpdate: Int = 0 // force an update (NOTE: not a bool to keep this struct a multiple of 4 bytes)

        // networking: see WriteGUIToSnapshot / ReadGUIFromSnapshot
        var gui: Array<idUserInterface?> = arrayOfNulls(MAX_RENDERENTITY_GUI)
        var hModel: idRenderModel? = null // this can only be null if callback is set
        var joints // array of joints that will modify vertices.
                : Array<idJointMat?>? = null

        // NULL if non-deformable model.  NOT freed by renderer
        //
        var modelDepthHack: Float = 0.0f // squash depth range so particle effects don't clip into walls

        //
        var noDynamicInteractions: Boolean = false // don't create any light / shadow interactions after

        //
        // options to override surface shader flags (replace with material parameters?)
        var noSelfShadow: Boolean = false // cast shadows onto other objects,but not self
        var noShadow: Boolean = false // no shadow at all

        //
        var numJoints: Int = 0

        //
        // positioning
        // axis rotation vectors must be unit length for many
        // R_LocalToGlobal functions to work, so don't scale models!
        // axis vectors are [0] = forward, [1] = left, [2] = up
        val origin: idVec3 = idVec3()
        var referenceShader: idMaterial? = null // used so flares can reference the proper light shader
        var referenceSound: idSoundEmitter? = null // for shader sound tables, allowing effects to vary with sounds

        //
        var remoteRenderView: renderView_s? = null // any remote camera surfaces will use this

        //
        // world models for the player and weapons will not cast shadows from view weapon
        // muzzle flashes
        var suppressShadowInLightID: Int = 0
        var suppressShadowInViewID: Int = 0

        //
        // player bodies and possibly player shadows should be suppressed in views from
        // that player's eyes, but will show up in mirrors and other subviews
        // security cameras could suppress their model in their subviews if we add a way
        // of specifying a view number for a remoteRenderMap view
        var suppressSurfaceInViewID: Int = 0
        var timeGroup: Int = 0

        // the level load is completed.  This is a performance hack
        // for the gigantic outdoor meshes in the monorail map, so
        // all the lights in the moving monorail don't touch the meshes
        //
        var weaponDepthHack: Boolean = false // squash depth range so view weapons don't poke into walls
        var xrayIndex: Int = 0

        constructor()

        constructor(newEntity: renderEntity_s) {
            hModel = newEntity.hModel
            entityNum = newEntity.entityNum
            bodyId = newEntity.bodyId
            bounds.set(newEntity.bounds)
            callback = newEntity.callback
            callbackData = newEntity.callbackData
            suppressSurfaceInViewID = newEntity.suppressSurfaceInViewID
            suppressShadowInViewID = newEntity.suppressShadowInViewID
            suppressShadowInLightID = newEntity.suppressShadowInLightID
            allowSurfaceInViewID = newEntity.allowSurfaceInViewID
            origin.set(newEntity.origin)
            axis.set(newEntity.axis)
            customShader = newEntity.customShader
            referenceShader = newEntity.referenceShader
            customSkin = newEntity.customSkin
            referenceSound = newEntity.referenceSound
            System.arraycopy(newEntity.shaderParms, 0, shaderParms, 0, shaderParms.size)
            for (i in gui.indices) {
                gui[i] = newEntity.gui[i]
            }
            remoteRenderView = newEntity.remoteRenderView
            numJoints = newEntity.numJoints
            joints = newEntity.joints
            modelDepthHack = newEntity.modelDepthHack
            noSelfShadow = newEntity.noSelfShadow
            noShadow = newEntity.noShadow
            noDynamicInteractions = newEntity.noDynamicInteractions
            weaponDepthHack = newEntity.weaponDepthHack
            forceUpdate = newEntity.forceUpdate
            timeGroup = newEntity.timeGroup
            xrayIndex = newEntity.xrayIndex
        }

        fun atomicSet(shadow: renderEntityShadow) {
            hModel = shadow.hModel
            entityNum = shadow.entityNum._val
            bodyId = shadow.bodyId._val
            bounds.set(shadow.bounds)
            callback = shadow.callback
            callbackData = shadow.callbackData
            suppressSurfaceInViewID = shadow.suppressSurfaceInViewID._val
            suppressShadowInViewID = shadow.suppressShadowInViewID._val
            suppressShadowInLightID = shadow.suppressShadowInLightID._val
            allowSurfaceInViewID = shadow.allowSurfaceInViewID._val
            origin.set(shadow.origin)
            axis.set(shadow.axis)
            customShader = shadow.customShader
            referenceShader = shadow.referenceShader
            customSkin = shadow.customSkin
            referenceSound = shadow.referenceSound
            remoteRenderView = shadow.remoteRenderView
            numJoints = shadow.numJoints._val
            joints = shadow.joints as Array<idJointMat?>
            modelDepthHack = shadow.modelDepthHack._val
            noSelfShadow = shadow.noSelfShadow._val
            noShadow = shadow.noShadow._val
            noDynamicInteractions = shadow.noDynamicInteractions._val
            weaponDepthHack = shadow.weaponDepthHack._val
            forceUpdate = shadow.forceUpdate._val
            timeGroup = shadow.timeGroup._val
            xrayIndex = shadow.xrayIndex._val
        }

        fun clear() {
            val newEntity = renderEntity_s()
            hModel = newEntity.hModel
            entityNum = newEntity.entityNum
            bodyId = newEntity.bodyId
            bounds.set(newEntity.bounds)
            callback = newEntity.callback
            callbackData = newEntity.callbackData
            suppressSurfaceInViewID = newEntity.suppressSurfaceInViewID
            suppressShadowInViewID = newEntity.suppressShadowInViewID
            suppressShadowInLightID = newEntity.suppressShadowInLightID
            allowSurfaceInViewID = newEntity.allowSurfaceInViewID
            origin.set(newEntity.origin)
            axis.set(newEntity.axis)
            customShader = newEntity.customShader
            referenceShader = newEntity.referenceShader
            customSkin = newEntity.customSkin
            referenceSound = newEntity.referenceSound
            remoteRenderView = newEntity.remoteRenderView
            numJoints = newEntity.numJoints
            joints = newEntity.joints
            modelDepthHack = newEntity.modelDepthHack
            noSelfShadow = newEntity.noSelfShadow
            noShadow = newEntity.noShadow
            noDynamicInteractions = newEntity.noDynamicInteractions
            weaponDepthHack = newEntity.weaponDepthHack
            forceUpdate = newEntity.forceUpdate
            timeGroup = newEntity.timeGroup
            xrayIndex = newEntity.xrayIndex
        }

        override fun hashCode(): Int {
            var hash = 7
            hash = 71 * hash + Objects.hashCode(hModel)
            hash = 71 * hash + entityNum
            hash = 71 * hash + bodyId
            hash = 71 * hash + Objects.hashCode(bounds)
            hash = 71 * hash + Objects.hashCode(callback)
            hash = 71 * hash + Objects.hashCode(callbackData)
            hash = 71 * hash + suppressSurfaceInViewID
            hash = 71 * hash + suppressShadowInViewID
            hash = 71 * hash + suppressShadowInLightID
            hash = 71 * hash + allowSurfaceInViewID
            hash = 71 * hash + Objects.hashCode(origin)
            hash = 71 * hash + Objects.hashCode(axis)
            hash = 71 * hash + Objects.hashCode(customShader)
            hash = 71 * hash + Objects.hashCode(referenceShader)
            hash = 71 * hash + Objects.hashCode(customSkin)
            hash = 71 * hash + Objects.hashCode(referenceSound)
            hash = 71 * hash + shaderParms.contentHashCode()
            hash = 71 * hash + gui.contentDeepHashCode()
            hash = 71 * hash + Objects.hashCode(remoteRenderView)
            hash = 71 * hash + numJoints
            hash = 71 * hash + joints.contentDeepHashCode()
            hash = 71 * hash + modelDepthHack.toBits().toInt()
            hash = 71 * hash + (if (noSelfShadow) 1 else 0)
            hash = 71 * hash + (if (noShadow) 1 else 0)
            hash = 71 * hash + (if (noDynamicInteractions) 1 else 0)
            hash = 71 * hash + (if (weaponDepthHack) 1 else 0)
            hash = 71 * hash + forceUpdate
            hash = 71 * hash + timeGroup
            hash = 71 * hash + xrayIndex
            return hash
        }

        override fun equals(obj: Any?): Boolean {
            if (obj == null) {
                return false
            }
            if (javaClass != obj.javaClass) {
                return false
            }
            val other: renderEntity_s = obj as renderEntity_s
            if (!Objects.equals(hModel, other.hModel)) {
                return false
            }
            if (entityNum != other.entityNum) {
                return false
            }
            if (bodyId != other.bodyId) {
                return false
            }
            if (!Objects.equals(bounds, other.bounds)) {
                return false
            }
            if (!Objects.equals(callback, other.callback)) {
                return false
            }
            if (!Objects.equals(callbackData, other.callbackData)) {
                return false
            }
            if (suppressSurfaceInViewID != other.suppressSurfaceInViewID) {
                return false
            }
            if (suppressShadowInViewID != other.suppressShadowInViewID) {
                return false
            }
            if (suppressShadowInLightID != other.suppressShadowInLightID) {
                return false
            }
            if (allowSurfaceInViewID != other.allowSurfaceInViewID) {
                return false
            }
            if (!Objects.equals(origin, other.origin)) {
                return false
            }
            if (!Objects.equals(axis, other.axis)) {
                return false
            }
            if (!Objects.equals(customShader, other.customShader)) {
                return false
            }
            if (!Objects.equals(referenceShader, other.referenceShader)) {
                return false
            }
            if (!Objects.equals(customSkin, other.customSkin)) {
                return false
            }
            if (!Objects.equals(referenceSound, other.referenceSound)) {
                return false
            }
            if (!shaderParms.contentEquals(other.shaderParms)) {
                return false
            }
            if (!gui.contentDeepEquals(other.gui)) {
                return false
            }
            if (!Objects.equals(remoteRenderView, other.remoteRenderView)) {
                return false
            }
            if (numJoints != other.numJoints) {
                return false
            }
            if (!joints.contentDeepEquals(other.joints)) {
                return false
            }
            if (modelDepthHack.toBits() != other.modelDepthHack.toBits()) {
                return false
            }
            if (noSelfShadow != other.noSelfShadow) {
                return false
            }
            if (noShadow != other.noShadow) {
                return false
            }
            if (noDynamicInteractions != other.noDynamicInteractions) {
                return false
            }
            if (weaponDepthHack != other.weaponDepthHack) {
                return false
            }
            if (forceUpdate != other.forceUpdate) {
                return false
            }
            if (timeGroup != other.timeGroup) {
                return false
            }
            return xrayIndex == other.xrayIndex
        }

        companion object {
            private var DBG_counter: Int = 0
        }
    }

    class renderLight_s {
        val end: idVec3 = idVec3()
        val lightCenter: idVec3 = idVec3() // offset the lighting direction for shading and
        val lightRadius: idVec3 = idVec3() // xyz radius for point lights
        val origin: idVec3 = idVec3()
        val right: idVec3 = idVec3()
        val shaderParms: FloatArray = FloatArray(Material.MAX_ENTITY_SHADER_PARMS) // can be used in any way by shader
        val start: idVec3 = idVec3()

        // shadows, relative to origin
        //
        // frustum definition for projected lights, all reletive to origin
        // FIXME: we should probably have real plane equations here, and offer
        // a helper function for conversion from this format
        val target: idVec3 = idVec3()
        val up: idVec3 = idVec3()

        //
        // if non-zero, the light will only show up in the specific view
        // which can allow player gun gui lights and such to not effect everyone
        var allowLightInViewID: CInt = CInt()
        val axis: idMat3 = idMat3() // rotation vectors, must be unit length

        //
        // muzzle flash lights will not cast shadows from player and weapon world models
        var lightId: CInt = CInt()

        //
        // I am sticking the four bools together so there are no unused gaps in
        // the padded structure, which could confuse the memcmp that checks for redundant
        // updates
        var noShadows: CBool = CBool() // (should we replace this with material parameters on the shader?)
        var noSpecular: CBool = CBool() // (should we replace this with material parameters on the shader?)
        var parallel: CBool = CBool() // lightCenter gives the direction to the light at infinity

        //
        var pointLight: CBool =
            CBool() // otherwise a projection light (should probably invert the sense of this, because points are way more common)

        //
        // Dmap will generate an optimized shadow volume named _prelight_<lightName>
        // for the light against all the _area* models in the map.  The renderer will
        // ignore this value if the light has been moved after initial creation
        var prelightModel: idRenderModel? = null
        var referenceSound: idSoundEmitter? = null // for shader sound tables, allowing effects to vary with sounds

        //
        //
        var shader: idMaterial? = null // NULL = either lights/defaultPointLight or lights/defaultProjectedLight

        //
        // if non-zero, the light will not show up in the specific view,
        // which may be used if we want to have slightly different muzzle
        // flash lights for the player and other views
        var suppressLightInViewID: CInt = CInt()

        constructor()

        constructor(other: renderLight_s) {
            origin.set(other.origin)
            axis.set(other.axis)
            lightCenter.set(other.lightCenter)
            lightRadius.set(other.lightRadius)
            target.set(other.target)
            right.set(other.right)
            up.set(other.up)
            start.set(other.start)
            end.set(other.end)
            System.arraycopy(other.shaderParms, 0, shaderParms, 0, shaderParms.size)
            pointLight._val = other.pointLight._val
            noShadows._val = other.noShadows._val
            noSpecular._val = other.noSpecular._val
            parallel._val = other.parallel._val
            lightId._val = other.lightId._val
            allowLightInViewID._val = other.allowLightInViewID._val
            suppressLightInViewID._val = other.suppressLightInViewID._val
            shader = other.shader
            prelightModel = other.prelightModel
            referenceSound = other.referenceSound
        }

    }

    class renderView_s : idSerializable {
        val vieworg: idVec3 = idVec3()
        private val DBG_count: Int = DBG_counter++
        var cramZNear: Boolean = false // for cinematics, we want to set ZNear much lower
        var forceUpdate: Boolean = false // for an update
        var fov_x: Float = 0.0f
        var fov_y: Float = 0.0f
        var globalMaterial: idMaterial? = null // used to override everything draw
        var shaderParms: FloatArray =
            FloatArray(MAX_GLOBAL_SHADER_PARMS) // can be used in any way by shader

        // time in milliseconds for shader effects and other time dependent rendering issues
        var time: Int = 0
        var viewID: Int = 0
        val viewaxis: idMat3 = idMat3() // transformation matrix, view looks down the positive X axis

        //
        // sized from 0 to SCREEN_WIDTH / SCREEN_HEIGHT (640/480), not actual resolution
        var x: Int = 0
        var y: Int = 0
        var width: Int = 0
        var height: Int = 0

        constructor()
        constructor(renderView: renderView_s) {
            viewID = renderView.viewID
            x = renderView.x
            y = renderView.y
            width = renderView.width
            height = renderView.height
            fov_x = renderView.fov_x
            fov_y = renderView.fov_y
            vieworg.set((renderView.vieworg))
            viewaxis.set(renderView.viewaxis)
            cramZNear = renderView.cramZNear
            forceUpdate = renderView.forceUpdate
            time = renderView.time
            renderView.shaderParms.copyInto(shaderParms)
            globalMaterial = renderView.globalMaterial
        }

        fun atomicSet(shadow: renderViewShadow) {
            viewID = shadow.viewID._val
            x = shadow.x._val
            y = shadow.y._val
            width = shadow.width._val
            height = shadow.height._val
            fov_x = shadow.fov_x._val
            fov_y = shadow.fov_y._val
            vieworg.set(shadow.vieworg)
            viewaxis.set(shadow.viewaxis)
            cramZNear = shadow.cramZNear._val
            forceUpdate = shadow.forceUpdate._val
            time = shadow.time._val
            for (a in 0 until MAX_GLOBAL_SHADER_PARMS) {
                shaderParms[a] = shadow.shaderParms[a]._val
            }
            globalMaterial = shadow.globalMaterial
        }

        override fun readFrom(file: idFile) {
            viewID = file.ReadInt()
            x = file.ReadInt()
            y = file.ReadInt()
            width = file.ReadInt()
            height = file.ReadInt()
            fov_x = file.ReadFloat()
            fov_y = file.ReadFloat()
            vieworg[0] = file.ReadFloat()
            vieworg[1] = file.ReadFloat()
            vieworg[2] = file.ReadFloat()
            for (i in 0 until 3) {
                for (j in 0 until 3) {
                    viewaxis[i][j] = file.ReadFloat()
                }
            }
            cramZNear = file.ReadInt() != 0
            forceUpdate = file.ReadInt() != 0
            time = file.ReadInt()
            for (i in shaderParms.indices) {
                shaderParms[i] = file.ReadFloat()
            }
            file.ReadInt() // globalMaterial pointer, skip
        }

        override fun writeTo(file: idFile) {
            file.WriteInt(viewID)
            file.WriteInt(x)
            file.WriteInt(y)
            file.WriteInt(width)
            file.WriteInt(height)
            file.WriteFloat(fov_x)
            file.WriteFloat(fov_y)
            file.WriteFloat(vieworg[0])
            file.WriteFloat(vieworg[1])
            file.WriteFloat(vieworg[2])
            for (i in 0 until 3) {
                for (j in 0 until 3) {
                    file.WriteFloat(viewaxis[i][j])
                }
            }
            file.WriteInt(if (cramZNear) 1 else 0)
            file.WriteInt(if (forceUpdate) 1 else 0)
            file.WriteInt(time)
            for (p in shaderParms) {
                file.WriteFloat(p)
            }
            file.WriteInt(0) // globalMaterial pointer
        }

        companion object {
            val BYTES = 144

            // player views will set this to a non-zero integer for model suppress / allow
            // subviews (mirrors, cameras, etc) will always clear it to zero
            private var DBG_counter: Int = 0
        }
    }

    // exitPortal_t is returned by idRenderWorld::GetPortal()
    class exitPortal_t {
        var areas: IntArray = IntArray(2) // areas connected by this portal
        var blockingBits: Int = 0 // PS_BLOCK_VIEW, PS_BLOCK_AIR, etc
        var  /*qhandle_t */portalHandle: Int = 0
        var w: idWinding? = null // winding points have counter clockwise ordering seen from areas[0]
    }

    // guiPoint_t is returned by idRenderWorld::GuiTrace()
    class guiPoint_t {
        var guiId: Int = 0 // id of gui ( 0, 1, or 2 ) that the trace happened against
        var x: Float = 0.0f
        var y: Float = 0.0f // 0.0f to 1.0f range if trace hit a gui, otherwise -1
    }

    // modelTrace_t is for tracing vs. visual geometry
    class modelTrace_s {
        var entity: renderEntity_s? = null // render entity that was hit
        var fraction: Float = 0.0f // fraction of trace completed
        var jointNumber: Int = 0 // md5 joint nearest to the hit triangle
        var material: idMaterial? = null // material of hit surface
        val normal: idVec3 = idVec3() // hit triangle normal vector in global space
        val point: idVec3 = idVec3() // end point of trace in global space
    }

    abstract class idRenderWorld {
        //	virtual					~idRenderWorld() {};
        // The same render world can be reinitialized as often as desired
        // a NULL or empty mapName will create an empty, single area world
        @Throws(idException::class)
        abstract fun InitFromMap(mapName: String?): Boolean

        //-------------- Entity and Light Defs -----------------
        // entityDefs and lightDefs are added to a given world to determine
        // what will be drawn for a rendered scene.  Most update work is defered
        // until it is determined that it is actually needed for a given view.
        abstract fun AddEntityDef(re: renderEntity_s): Int
        abstract fun UpdateEntityDef(entityHandle: Int, re: renderEntity_s)
        abstract fun FreeEntityDef(entityHandle: Int)
        abstract fun GetRenderEntity(entityHandle: Int): renderEntity_s?
        abstract fun AddLightDef(rlight: renderLight_s): Int
        abstract fun UpdateLightDef(lightHandle: Int, rlight: renderLight_s)
        abstract fun FreeLightDef(lightHandle: Int)
        abstract fun GetRenderLight(lightHandle: Int): renderLight_s?

        // Force the generation of all light / surface interactions at the start of a level
        // If this isn't called, they will all be dynamically generated
        abstract fun GenerateAllInteractions()

        // returns true if this area model needs portal sky to draw
        abstract fun CheckAreaForPortalSky(areaNum: Int): Boolean

        //-------------- Decals and Overlays  -----------------
        // Creates decals on all world surfaces that the winding projects onto.
        // The projection origin should be infront of the winding plane.
        // The decals are projected onto world geometry between the winding plane and the projection origin.
        // The decals are depth faded from the winding plane to a certain distance infront of the
        // winding plane and the same distance from the projection origin towards the winding.
        abstract fun ProjectDecalOntoWorld(
            winding: idFixedWinding,
            projectionOrigin: idVec3,
            parallel: Boolean,
            fadeDepth: Float,
            material: idMaterial?,
            startTime: Int
        )

        // Creates decals on static models.
        abstract fun ProjectDecal(
            entityHandle: Int,
            winding: idFixedWinding,
            projectionOrigin: idVec3,
            parallel: Boolean,
            fadeDepth: Float,
            material: idMaterial?,
            startTime: Int
        )

        // Creates overlays on dynamic models.
        abstract fun ProjectOverlay(
            entityHandle: Int,
            localTextureAxis: Array<idPlane?>? /*[2]*/,
            material: idMaterial?
        )

        // Removes all decals and overlays from the given entity def.
        abstract fun RemoveDecals(entityHandle: Int)

        //-------------- Scene Rendering -----------------
        // some calls to material functions use the current renderview time when servicing cinematics.  this function
        // ensures that any parms accessed (such as time) are properly set.
        abstract fun SetRenderView(renderView: renderView_s?)

        // rendering a scene may actually render multiple subviews for mirrors and portals, and
        // may render composite textures for gui console screens and light projections
        // It would also be acceptable to render a scene multiple times, for "rear view mirrors", etc
        abstract fun RenderScene(renderView: renderView_s)

        //-------------- Portal Area Information -----------------
        // returns the number of portals
        abstract fun NumPortals(): Int

        // returns 0 if no portal contacts the bounds
        // This is used by the game to identify portals that are contained
        // inside doors, so the connection between areas can be topologically
        // terminated when the door shuts.
        abstract fun FindPortal(b: idBounds): Int

        // doors explicitly close off portals when shut
        // multiple bits can be set to block multiple things, ie: ( PS_VIEW | PS_LOCATION | PS_AIR )
        abstract fun SetPortalState(portal: Int, blockingBits: Int)
        abstract fun GetPortalState(portal: Int): Int

        // returns true only if a chain of portals without the given connection bits set
        // exists between the two areas (a door doesn't separate them, etc)
        abstract fun AreasAreConnected(areaNum1: Int, areaNum2: Int, connection: portalConnection_t): Boolean

        // returns the number of portal areas in a map, so game code can build information
        // tables for the different areas
        abstract fun NumAreas(): Int

        // Will return -1 if the point is not in an area, otherwise
        // it will return 0 <= value < NumAreas()
        abstract fun PointInArea(point: idVec3): Int

        // fills the *areas array with the numbers of the areas the bounds cover
        // returns the total number of areas the bounds cover
        abstract fun BoundsInAreas(bounds: idBounds, areas: IntArray?, maxAreas: Int): Int

        // Used by the sound system to do area flowing
        abstract fun NumPortalsInArea(areaNum: Int): Int

        // returns one portal from an area
        abstract fun GetPortal(areaNum: Int, portalNum: Int): exitPortal_t

        //-------------- Tracing  -----------------
        // Checks a ray trace against any gui surfaces in an entity, returning the
        // fraction location of the trace on the gui surface, or -1,-1 if no hit.
        // This doesn't do any occlusion testing, simply ignoring non-gui surfaces.
        // start / end are in global world coordinates.
        abstract fun GuiTrace(entityHandle: Int, start: idVec3, end: idVec3): guiPoint_t

        // Traces vs the render model, possibly instantiating a dynamic version, and returns true if something was hit
        abstract fun ModelTrace(
            trace: modelTrace_s,
            entityHandle: Int,
            start: idVec3,
            end: idVec3,
            radius: Float
        ): Boolean

        // Traces vs the whole rendered world. FIXME: we need some kind of material flags.
        abstract fun Trace(
            trace: modelTrace_s,
            start: idVec3,
            end: idVec3,
            radius: Float,
            skipDynamic: Boolean,
            skipPlayer: Boolean
        ): Boolean


        fun Trace(
            trace: modelTrace_s,
            start: idVec3,
            end: idVec3,
            radius: Float,
            skipDynamic: Boolean = true
        ): Boolean {
            return Trace(trace, start, end, radius, skipDynamic, false)
        }

        // Traces vs the world model bsp tree.
        abstract fun FastWorldTrace(trace: modelTrace_s, start: idVec3, end: idVec3): Boolean

        //-------------- Demo Control  -----------------
        // Writes a loadmap command to the demo, and clears archive counters.
        abstract fun StartWritingDemo(demo: idDemoFile?)
        abstract fun StopWritingDemo()

        // Returns true when demoRenderView has been filled in.
        // adds/updates/frees entityDefs and lightDefs based on the current demo file
        // and returns the renderView to be used to render this frame.
        // a demo file may need to be advanced multiple times if the framerate
        // is less than 30hz
        // demoTimeOffset will be set if a new map load command was processed before
        // the next renderScene
        abstract fun ProcessDemoCommand(
            readDemo: idDemoFile?,
            demoRenderView: renderView_s,
            demoTimeOffset: CInt
        ): Boolean

        // this is used to regenerate all interactions ( which is currently only done during influences ), there may be a less
        // expensive way to do it
        abstract fun RegenerateWorld()

        //-------------- Debug Visualization  -----------------
        // Line drawing for debug visualization
        abstract fun DebugClearLines(time: Int) // a time of 0 will clear all lines and text
        abstract fun DebugLine(
            color: idVec4,
            start: idVec3,
            end: idVec3,
            lifetime: Int,
            depthTest: Boolean
        )


        fun DebugLine(color: idVec4, start: idVec3, end: idVec3, lifetime: Int = 0) {
            DebugLine(color, start, end, lifetime, false)
        }

        abstract fun DebugArrow(color: idVec4, start: idVec3, end: idVec3, size: Int, lifetime: Int)
        fun DebugArrow(color: idVec4, start: idVec3, end: idVec3, size: Int) {
            DebugArrow(color, start, end, size, 0)
        }

        abstract fun DebugWinding(
            color: idVec4,
            w: idWinding,
            origin: idVec3,
            axis: idMat3,
            lifetime: Int,
            depthTest: Boolean
        )


        fun DebugWinding(color: idVec4, w: idWinding, origin: idVec3, axis: idMat3, lifetime: Int = 0) {
            DebugWinding(color, w, origin, axis, lifetime, false)
        }

        abstract fun DebugCircle(
            color: idVec4,
            origin: idVec3,
            dir: idVec3,
            radius: Float,
            numSteps: Int,
            lifetime: Int,
            depthTest: Boolean
        )


        fun DebugCircle(
            color: idVec4,
            origin: idVec3,
            dir: idVec3,
            radius: Float,
            numSteps: Int,
            lifetime: Int = 0
        ) {
            DebugCircle(color, origin, dir, radius, numSteps, lifetime, false)
        }

        abstract fun DebugSphere(
            color: idVec4,
            sphere: idSphere,
            lifetime: Int,
            depthTest: Boolean
        )


        fun DebugSphere(color: idVec4, sphere: idSphere, lifetime: Int = 0) {
            DebugSphere(color, sphere, lifetime, false)
        }

        abstract fun DebugBounds(
            color: idVec4,
            bounds: idBounds,
            org: idVec3,
            lifetime: Int
        )


        fun DebugBounds(color: idVec4, bounds: idBounds, org: idVec3 = vec3_origin) {
            DebugBounds(color, bounds, org, 0)
        }

        abstract fun DebugBox(color: idVec4, box: idBox, lifetime: Int)
        fun DebugBox(color: idVec4, box: idBox) {
            DebugBox(color, box, 0)
        }

        abstract fun DebugFrustum(
            color: idVec4,
            frustum: idFrustum,
            showFromOrigin: Boolean,
            lifetime: Int
        )


        fun DebugFrustum(color: idVec4, frustum: idFrustum, showFromOrigin: Boolean = false) {
            DebugFrustum(color, frustum, showFromOrigin, 0)
        }

        abstract fun DebugCone(
            color: idVec4,
            apex: idVec3,
            dir: idVec3,
            radius1: Float,
            radius2: Float,
            lifetime: Int
        )

        fun DebugCone(color: idVec4, apex: idVec3, dir: idVec3, radius1: Float, radius2: Float) {
            DebugCone(color, apex, dir, radius1, radius2, 0)
        }

        abstract fun DebugAxis(origin: idVec3, axis: idMat3)

        // Polygon drawing for debug visualization.
        abstract fun DebugClearPolygons(time: Int) // a time of 0 will clear all polygons
        abstract fun DebugPolygon(
            color: idVec4,
            winding: idWinding?,
            lifeTime: Int,
            depthTest: Boolean
        )


        fun DebugPolygon(color: idVec4, winding: idWinding?, lifeTime: Int = 0) {
            DebugPolygon(color, winding, lifeTime, false)
        }

        // Text drawing for debug visualization.
        abstract fun DrawText(
            text: String?,
            origin: idVec3,
            scale: Float,
            color: idVec4,
            viewAxis: idMat3,
            align: Int,
            lifetime: Int,
            depthTest: Boolean
        )


        fun DrawText(
            text: String?,
            origin: idVec3,
            scale: Float,
            color: idVec4,
            viewAxis: idMat3,
            align: Int = 1,
            lifetime: Int = 0
        ) {
            DrawText(text, origin, scale, color, viewAxis, align, lifetime, false)
        }
    }

    /*
     ===================
     R_ListRenderLightDefs_f
     ===================
     */
    class R_ListRenderLightDefs_f private constructor() : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
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
                    common.Printf("%4d: FREED\n", i)
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
                common.Printf("%4d: %3d intr %2d refs %s\n", i, iCount, rCount, ldef.lightShader!!.GetName())
                active++
                i++
            }
            common.Printf("%d lightDefs, %d interactions, %d areaRefs\n", active, totalIntr, totalRef)
        }

        companion object {
            val instance: cmdFunction_t = R_ListRenderLightDefs_f()
        }
    }

    /*
     ===================
     R_ListRenderEntityDefs_f
     ===================
     */
    class R_ListRenderEntityDefs_f private constructor() : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
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
                    common.Printf("%4d: FREED\n", i)
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
                common.Printf("%4d: %3d intr %2d refs %s\n", i, iCount, rCount, mdef.parms.hModel!!.Name())
                active++
                i++
            }
            common.Printf("total active: %d\n", active)
        }

        companion object {
            val instance: cmdFunction_t = R_ListRenderEntityDefs_f()
        }
    }
}

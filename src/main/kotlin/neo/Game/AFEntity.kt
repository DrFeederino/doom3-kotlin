/*
 * Copyright (C) 1999-2011 id Software LLC, a ZeniMax Media company.
 * Translated to Kotlin by Dr. Feederino with support of Claude Code
 *
 * This file is part of the Doom 3 Kotlin project.
 * Original source: neo/Game/AFEntity.cpp, neo/Game/AFEntity.h
 *
 * Doom 3 Source Code is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Doom 3 Source Code is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 */

package neo.Game

import neo.Game.Animation.Anim
import neo.Game.Animation.Anim.jointModTransform_t
import neo.Game.Animation.idDeclModelDef
import neo.Game.GameSys.Class.*
import neo.Game.GameSys.EV_Remove
import neo.Game.GameSys.Event.idEventDef
import neo.Game.GameSys.SaveGame.idRestoreGame
import neo.Game.GameSys.SaveGame.idSaveGame
import neo.Game.GameSys.SysCvar
import neo.Game.Game_local.*
import neo.Game.Physics.Clip
import neo.Game.Physics.Clip.idClipModel
import neo.Game.Physics.Force_Constant.idForce_Constant
import neo.Game.Physics.Physics.impactInfo_s
import neo.Game.Physics.Physics_AF.idAFBody
import neo.Game.Physics.Physics_AF.idAFConstraint_BallAndSocketJoint
import neo.Game.Physics.Physics_AF.idAFConstraint_Contact
import neo.Game.Physics.Physics_AF.idAFConstraint_Hinge
import neo.Game.Physics.Physics_AF.idAFConstraint_Suspension
import neo.Game.Physics.Physics_AF.idAFConstraint_UniversalJoint
import neo.Game.Physics.Physics_AF.idPhysics_AF
import neo.Game.Player.idPlayer
import neo.Renderer.Material
import neo.Renderer.Model
import neo.Renderer.Model.idMD5Joint
import neo.Renderer.Model.idRenderModel
import neo.Renderer.ModelManager
import neo.Renderer.RenderWorld
import neo.Renderer.RenderWorld.renderEntity_s
import neo.TempDump
import neo.cm.trace_s
import neo.framework.Common
import neo.framework.DeclAF.getJointTransform_t
import neo.framework.DeclManager
import neo.framework.DeclManager.declType_t
import neo.framework.DeclParticle.idDeclParticle
import neo.framework.DeclSkin.idDeclSkin
import neo.idlib.Dict_h.idDict
import neo.idlib.Dict_h.idKeyValue
import neo.idlib.Text.Str
import neo.idlib.Text.Str.idStr
import neo.idlib.containers.CBool
import neo.idlib.containers.CFloat
import neo.idlib.containers.CInt
import neo.idlib.containers.List.idList
import neo.idlib.geometry.JointTransform.idJointMat
import neo.idlib.geometry.TraceModel.idTraceModel
import neo.idlib.idLib
import neo.idlib.math.*
import neo.idlib.math.Matrix.idMat3
import java.util.*
import kotlin.math.abs

//
val EV_Gib: idEventDef = idEventDef("gib", "s")
val EV_Gibbed: idEventDef = idEventDef("<gibbed>")
val EV_SetConstraintPosition: idEventDef = idEventDef("SetConstraintPosition", "sv")

//
val EV_SetFingerAngle: idEventDef = idEventDef("setFingerAngle", "f")
val EV_StopFingers: idEventDef = idEventDef("stopFingers")

const val BOUNCE_SOUND_MAX_VELOCITY = 200.0f

/*
 ===============================================================================

 idAFEntity_Base

 ===============================================================================
 */
const val BOUNCE_SOUND_MIN_VELOCITY = 80.0f


/*
 ===============================================================================

 idMultiModelAF

 Entity using multiple separate visual models animated with a single
 articulated figure. Only used for debugging!

 ===============================================================================
 */
const val GIB_DELAY = 200 // only gib this often to keep performace hits when blowing up several mobs

//
val clawConstraintNames: Array<String> = arrayOf(
    "claw1", "claw2", "claw3", "claw4"
)

/*
 ================
 GetArgString
 ================
 */
fun GetArgString(args: idDict, defArgs: idDict?, key: String?): String {
    var s: String
    s = args.GetString(key)
    //	if ( !s[0] && defArgs ) {
    if (s.isEmpty() && defArgs != null) {
        s = defArgs.GetString(key)
    }
    return s
}

open class idMultiModelAF : idEntity() {
    companion object {
        val Type = idTypeInfo("idMultiModelAF", "idEntity") { idMultiModelAF() }
    }

    override fun GetType(): idTypeInfo = Type

    //        public CLASS_PROTOTYPE(idMultiModelAF );//TODO:include this?
    protected var physicsObj: idPhysics_AF = idPhysics_AF()
    private val modelDefHandles: idList<Int> = idList()

    //
    private val modelHandles: idList<idRenderModel?> = idList()

    //
    //
    override fun Spawn() {
        super.Spawn()
        physicsObj.SetSelf(this)
    }

    //							~idMultiModelAF( void );
    // FIX: Added missing destructor - C++ frees entity defs for all model handles
    override fun _deconstructor() {
        var i: Int
        i = 0
        while (i < modelDefHandles.Num()) {
            if (modelDefHandles[i] != -1) {
                Game_local.gameRenderWorld!!.FreeEntityDef(modelDefHandles[i])
                modelDefHandles[i] = -1
            }
            i++
        }
        super._deconstructor()
    }

    override fun Think() {
        RunPhysics()
        Present()
    }

    override fun Present() {
        var i: Int

        // don't present to the renderer if the entity hasn't changed
        if ((thinkFlags and TH_UPDATEVISUALS) == 0) {
            return
        }
        BecomeInactive(TH_UPDATEVISUALS)
        i = 0
        while (i < modelHandles.Num()) {
            if (null == modelHandles[i]) {
                i++
                continue
            }
            renderEntity!!.origin.set(physicsObj.GetOrigin(i))
            renderEntity!!.axis.set(physicsObj.GetAxis(i))
            renderEntity!!.hModel = modelHandles[i]
            renderEntity!!.bodyId = i

            // add to refresh list
            if (modelDefHandles[i] == -1) {
                modelDefHandles[i] = Game_local.gameRenderWorld!!.AddEntityDef(renderEntity!!)
            } else {
                Game_local.gameRenderWorld!!.UpdateEntityDef(modelDefHandles[i], renderEntity!!)
            }
            i++
        }
    }

    protected fun SetModelForId(id: Int, modelName: String) {
        modelHandles.AssureSize(id + 1, null)
        modelDefHandles.AssureSize(id + 1, -1)
        modelHandles[id] = ModelManager.renderModelManager.FindModel(modelName)
    }

    override fun CreateInstance(): idClass = idMultiModelAF()
}

//
//
/*
 ===============================================================================

 idChain

 Chain hanging down from the ceiling. Only used for debugging!

 ===============================================================================
 */
class idChain : idMultiModelAF() {
    companion object {
        val Type = idTypeInfo("idChain", "idMultiModelAF") { idChain() }
    }

    override fun GetType(): idTypeInfo = Type
    override fun CreateInstance(): idClass = idChain()

    //public	CLASS_PROTOTYPE( idChain );
    override fun Spawn() {
        super.Spawn()
        val numLinks = CInt()
        val length = CFloat()
        val linkWidth = CFloat()
        val density = CFloat()
        val linkLength: Float
        val drop = CBool(false)
        val origin = idVec3()
        spawnArgs.GetBool("drop", "0", drop)
        spawnArgs.GetInt("links", "3", numLinks)
        spawnArgs.GetFloat("length", "" + numLinks.integerValue * 32.0f, length)
        spawnArgs.GetFloat("width", "8", linkWidth)
        spawnArgs.GetFloat("density", "0.2f", density)
        linkLength = length._val / numLinks.integerValue
        origin.set(GetPhysics().GetOrigin())

        // initialize physics
        physicsObj.SetSelf(this)
        physicsObj.SetGravity(Game_local.gameLocal.GetGravity())
        physicsObj.SetClipMask(Game_local.MASK_SOLID or Material.CONTENTS_BODY)
        SetPhysics(physicsObj)
        BuildChain("link", origin, linkLength, linkWidth._val, density._val, numLinks.integerValue, !drop._val)
    }

    /*
     ================
     idChain::BuildChain

     builds a chain hanging down from the ceiling
     the highest link is a child of the link below it etc.
     this allows an object to be attached to multiple chains while keeping a single tree structure
     ================
     */
    protected fun BuildChain(
        name: String?,
        origin: idVec3,
        linkLength: Float,
        linkWidth: Float,
        density: Float,
        numLinks: Int,
        bindToWorld: Boolean /*= true*/
    ) {
        var i: Int
        val halfLinkLength = linkLength * 0.5f
        val trm: idTraceModel
        var clip: idClipModel
        var body: idAFBody
        var lastBody: idAFBody?
        var bsj: idAFConstraint_BallAndSocketJoint
        var uj: idAFConstraint_UniversalJoint
        val org = idVec3()

        // create a trace model
        trm = idTraceModel(linkLength, linkWidth)
        trm.Translate(trm.offset.unaryMinus())
        org.set(origin.minus(idVec3(0.0f, 0.0f, halfLinkLength)))
        lastBody = null
        i = 0
        while (i < numLinks) {


            // add body
            clip = idClipModel(trm)
            clip.SetContents(Material.CONTENTS_SOLID)
            clip.Link(Game_local.gameLocal.clip, this, 0, org, idMat3.getMat3_identity())
            body = idAFBody(idStr(name + i), clip, density)
            physicsObj.AddBody(body)

            // visual model for body
            SetModelForId(physicsObj.GetBodyId(body), spawnArgs.GetString("model"))

            // add constraint
            if (bindToWorld) {
                if (lastBody == null) {
                    uj = idAFConstraint_UniversalJoint(idStr(name + i), body, lastBody)
                    uj.SetShafts(idVec3(0, 0, -1), idVec3(0, 0, 1))
                    //uj.SetConeLimit( idVec3( 0, 0, -1 ), 30 );
                    //uj.SetPyramidLimit( idVec3( 0, 0, -1 ), idVec3( 1, 0, 0 ), 90, 30 );
                } else {
                    uj = idAFConstraint_UniversalJoint(idStr(name + i), lastBody, body)
                    uj.SetShafts(idVec3(0, 0, 1), idVec3(0, 0, -1))
                    //uj.SetConeLimit( idVec3( 0, 0, 1 ), 30 );
                }
                uj.SetAnchor(org.plus(idVec3(0.0f, 0.0f, halfLinkLength)))
                uj.SetFriction(0.9f)
                physicsObj.AddConstraint(uj)
            } else {
                if (lastBody != null) {
                    bsj = idAFConstraint_BallAndSocketJoint(idStr("joint$i"), lastBody, body)
                    bsj.SetAnchor(org.plus(idVec3(0.0f, 0.0f, halfLinkLength)))
                    bsj.SetConeLimit(idVec3(0, 0, 1), 60.0f, idVec3(0, 0, 1))
                    physicsObj.AddConstraint(bsj)
                }
            }
            org.minusAssign(2, linkLength)
            lastBody = body
            i++
        }
    }
}

/*
 ===============================================================================

 idAFEntity_Gibbable

 ===============================================================================
 */
/*
 ===============================================================================

 idAFAttachment

 ===============================================================================
 */
class idAFAttachment : idAnimatedEntity() {
    companion object {
        val Type = idTypeInfo("idAFAttachment", "idAnimatedEntity") { idAFAttachment() }
    }

    override fun GetType(): idTypeInfo = Type
    override fun CreateInstance(): idClass = idAFAttachment()

    // public	CLASS_PROTOTYPE( idAFAttachment );
    protected var   /*jointHandle_t*/attachJoint: Int
    protected var body: idEntity? = null
    protected var combatModel // render model for hit detection of head
            : idClipModel? = null
    protected var idleAnim = 0

    // virtual					~idAFAttachment( void );
    // FIX: Added missing destructor - C++ stops sounds and deletes combatModel
    override fun _deconstructor() {
        StopSound(TempDump.etoi(gameSoundChannel_t.SND_CHANNEL_ANY), false)
        combatModel = null
        super._deconstructor()
    }

    override fun Spawn() {
        super.Spawn()
        idleAnim = animator.GetAnim("idle")
    }

    /*
     ================
     idAFAttachment::Save

     archive object for savegame file
     ================
     */
    override fun Save(savefile: idSaveGame) {
        super.Save(savefile)
        savefile.WriteObject(body as idClass?)
        savefile.WriteInt(idleAnim)
        savefile.WriteJoint(attachJoint)
    }

    /*
     ================
     idAFAttachment::Restore

     unarchives object from save game file
     ================
     */
    override fun Restore(savefile: idRestoreGame) {
        super.Restore(savefile)
        body = savefile.ReadObject() as idEntity?
        idleAnim = savefile.ReadInt()
        attachJoint = savefile.ReadJoint()
        SetCombatModel()
        LinkCombat()
    }

    fun SetBody(bodyEnt: idEntity?, headModel: String,    /*jointHandle_t*/attachJoint: Int) {
        val bleed: Boolean
        body = bodyEnt
        this.attachJoint = attachJoint
        SetModel(headModel)
        fl.takedamage = true
        bleed = body!!.spawnArgs.GetBool("bleed")
        spawnArgs.SetBool("bleed", bleed)
    }

    fun ClearBody() {
        body = null
        attachJoint = Model.INVALID_JOINT
        Hide()
    }

    fun GetBody(): idEntity? {
        return body
    }

    override fun Think() {
        super.Think()
        if ((thinkFlags and TH_UPDATEPARTICLES) != 0) {
            UpdateDamageEffects()
        }
    }

    override fun Hide() {
        idEntity_Hide()
        UnlinkCombat()
    }

    override fun Show() {
        idEntity_Show()
        LinkCombat()
    }

    fun PlayIdleAnim(blendTime: Int) {
        if (idleAnim != 0 && idleAnim != animator.CurrentAnim(Anim.ANIMCHANNEL_ALL).AnimNum()) {
            animator.CycleAnim(Anim.ANIMCHANNEL_ALL, idleAnim, Game_local.gameLocal.time, blendTime)
        }
    }

    override fun GetImpactInfo(ent: idEntity?, id: Int, point: idVec3): impactInfo_s {
        return if (body != null) {
            body!!.GetImpactInfo(ent, Clip.JOINT_HANDLE_TO_CLIPMODEL_ID(attachJoint), point)
        } else {
            idEntity_GetImpactInfo(ent, id, point)
        }
    }

    override fun ApplyImpulse(ent: idEntity?, id: Int, point: idVec3, impulse: idVec3) {
        if (body != null) {
            body!!.ApplyImpulse(ent, Clip.JOINT_HANDLE_TO_CLIPMODEL_ID(attachJoint), point, impulse)
        } else {
            idEntity_ApplyImpulse(ent, id, point, impulse)
        }
    }

    override fun AddForce(ent: idEntity?, id: Int, point: idVec3, force: idVec3) {
        if (body != null) {
            body!!.AddForce(ent, Clip.JOINT_HANDLE_TO_CLIPMODEL_ID(attachJoint), point, force)
        } else {
            idEntity_AddForce(ent, id, point, force)
        }
    }

    /*
     ============
     idAFAttachment::Damage

     Pass damage to body at the bindjoint
     ============
     */
    override fun Damage(
        inflictor: idEntity?,
        attacker: idEntity?,
        dir: idVec3,
        damageDefName: String,
        damageScale: Float,
        location: Int
    ) {
        if (body != null) {
            body!!.Damage(inflictor, attacker, dir, damageDefName, damageScale, attachJoint)
        }
    }

    override fun AddDamageEffect(collision: trace_s, velocity: idVec3, damageDefName: String) {
        if (body != null) {
            val c = trace_s()
            c.set(collision)
            c.c.id = Clip.JOINT_HANDLE_TO_CLIPMODEL_ID(attachJoint)
            body!!.AddDamageEffect(c, velocity, damageDefName)
        }
    }

    fun SetCombatModel() {
        if (combatModel != null) {
            combatModel!!.Unlink()
            combatModel!!.LoadModel(modelDefHandle)
        } else {
            combatModel = idClipModel(modelDefHandle)
        }
        combatModel!!.SetOwner(body)
    }

    fun GetCombatModel(): idClipModel? {
        return combatModel
    }

    fun LinkCombat() {
        if (fl.hidden) {
            return
        }
        if (combatModel != null) {
            combatModel!!.Link(
                Game_local.gameLocal.clip,
                this,
                0,
                renderEntity!!.origin,
                renderEntity!!.axis,
                modelDefHandle
            )
        }
    }

    fun UnlinkCombat() {
        if (combatModel != null) {
            combatModel!!.Unlink()
        }
    }

    //
    //
    init {
        attachJoint = Model.INVALID_JOINT
    }
}

open class idAFEntity_Base : idAnimatedEntity() {
    companion object {
        val Type = idTypeInfo("idAFEntity_Base", "idAnimatedEntity") { idAFEntity_Base() }

        // public	CLASS_PROTOTYPE( idAFEntity_Base );
        private val eventCallbacks: MutableMap<idEventDef, eventCallback_t<*>> = HashMap()

        // virtual					~idAFEntity_Base( void );
        fun DropAFs(ent: idEntity, type: String, list: idList<idEntity>?) {
            var kv: idKeyValue?
            val skinName: String?
            val newEnt = arrayOfNulls<idEntity>(1)
            var af: idAFEntity_Base
            val args = idDict()
            val skin: idDeclSkin?

            // drop the articulated figures
            kv = ent.spawnArgs.MatchPrefix(Str.va("def_drop%sAF", type), null)
            while (kv != null) {
                args.Set("classname", kv.GetValue())
                Game_local.gameLocal.SpawnEntityDef(args, newEnt)
                if (newEnt.isNotEmpty() && newEnt[0] is idAFEntity_Base) {
                    af = newEnt[0] as idAFEntity_Base
                    af.GetPhysics().SetOrigin(ent.GetPhysics().GetOrigin())
                    af.GetPhysics().SetAxis(ent.GetPhysics().GetAxis())
                    af.af.SetupPose(ent, Game_local.gameLocal.time)
                    list?.Append(af)
                }
                kv = ent.spawnArgs.MatchPrefix(Str.va("def_drop%sAF", type), kv)
            }

            // change the skin to hide all the dropped articulated figures
            skinName = ent.spawnArgs.GetString(Str.va("skin_drop%s", type))
            if (skinName.isNotEmpty()) {
                skin = DeclManager.declManager.FindSkin(skinName)
                ent.SetSkin(skin)
            }
        }

        fun getEventCallBacks(): MutableMap<idEventDef, eventCallback_t<*>> {
            return eventCallbacks
        }

        init {
            eventCallbacks.putAll(idAnimatedEntity.getEventCallBacks())
            eventCallbacks[EV_SetConstraintPosition] =
                eventCallback_t2<idAFEntity_Base> { obj: idAFEntity_Base, name: idEventArg<*>?, pos: idEventArg<*>? ->
                    obj.Event_SetConstraintPosition(name as idEventArg<String>, pos as idEventArg<idVec3>)
                }
        }
    }

    protected val spawnOrigin // spawn origin
            : idVec3
    protected var af // articulated figure
            : idAF
    protected var combatModel // render model for hit detection
            : idClipModel?
    protected var combatModelContents: Int
    protected var nextSoundTime // next time this can make a sound
            : Int
    protected val spawnAxis // rotation axis used when spawned
            : idMat3

    // FIX: Added missing destructor - C++ deletes combatModel
    override fun _deconstructor() {
        combatModel = null
        super._deconstructor()
    }

    override fun Spawn() {
        super.Spawn()
        spawnOrigin.set(GetPhysics().GetOrigin())
        spawnAxis.set(GetPhysics().GetAxis())
        nextSoundTime = 0
    }

    override fun Save(savefile: idSaveGame) {
        super.Save(savefile)
        savefile.WriteInt(combatModelContents)
        savefile.WriteClipModel(combatModel)
        savefile.WriteVec3(spawnOrigin)
        savefile.WriteMat3(spawnAxis)
        savefile.WriteInt(nextSoundTime)
        af.Save(savefile)
    }

    override fun Restore(savefile: idRestoreGame) {
        super.Restore(savefile)
        combatModelContents = savefile.ReadInt()
        combatModel = savefile.ReadClipModel()
        savefile.ReadVec3(spawnOrigin)
        savefile.ReadMat3(spawnAxis)
        nextSoundTime = savefile.ReadInt()
        LinkCombat()
        af.Restore(savefile)
    }

    override fun Think() {
        RunPhysics()
        UpdateAnimation()
        if ((thinkFlags and TH_UPDATEVISUALS) != 0) {
            Present()
            LinkCombat()
        }
    }

    override fun GetImpactInfo(ent: idEntity?, id: Int, point: idVec3): impactInfo_s {
        return if (af.IsActive()) {
            af.GetImpactInfo(ent, id, point)
        } else {
            idEntity_GetImpactInfo(ent, id, point)
        }
    }

    override fun ApplyImpulse(ent: idEntity?, id: Int, point: idVec3, impulse: idVec3) {
        if (af.IsLoaded()) {
            af.ApplyImpulse(ent, id, point, impulse)
        }
        if (!af.IsActive()) {
            idEntity_ApplyImpulse(ent, id, point, impulse)
        }
    }

    override fun AddForce(ent: idEntity?, id: Int, point: idVec3, force: idVec3) {
        if (af.IsLoaded()) {
            af.AddForce(ent, id, point, force)
        }
        if (!af.IsActive()) {
            idEntity_AddForce(ent, id, point, force)
        }
    }

    override fun Collide(collision: trace_s, velocity: idVec3): Boolean {
        val v: Float
        val f: Float
        if (af.IsActive()) {
            v = -velocity.times(collision.c.normal)
            if (v > BOUNCE_SOUND_MIN_VELOCITY && Game_local.gameLocal.time > nextSoundTime) {
                f =
                    if (v > BOUNCE_SOUND_MAX_VELOCITY) 1.0f else idMath.Sqrt(v - BOUNCE_SOUND_MIN_VELOCITY) * (1.0f / idMath.Sqrt(
                        BOUNCE_SOUND_MAX_VELOCITY - BOUNCE_SOUND_MIN_VELOCITY
                    ))
                if (StartSound("snd_bounce", gameSoundChannel_t.SND_CHANNEL_ANY, 0, false)) {
                    // don't set the volume unless there is a bounce sound as it overrides the entire channel
                    // which causes footsteps on ai's to not honor their shader parms
                    SetSoundVolume(f)
                }
                nextSoundTime = Game_local.gameLocal.time + 500
            }
        }
        return false
    }

    override fun GetPhysicsToVisualTransform(origin: idVec3, axis: idMat3): Boolean {
        if (af.IsActive()) {
            af.GetPhysicsToVisualTransform(origin, axis)
            return true
        }
        return idEntity_GetPhysicsToVisualTransform(origin, axis)
    }

    override fun UpdateAnimationControllers(): Boolean {
        return if (af.IsActive()) {
            af.UpdateAnimation()
        } else false
    }

    override fun FreeModelDef() {
        UnlinkCombat()
        idEntity_FreeModelDef()
    }

    open fun LoadAF(): Boolean {
        val fileName = arrayOfNulls<String>(1)
        if (!spawnArgs.GetString("articulatedFigure", "*unknown*", fileName)) {
            return false
        }
        af.SetAnimator(GetAnimator())
        if (!af.Load(this, fileName[0]!!)) {
            idGameLocal.Error(
                "idAFEntity_Base::LoadAF: Couldn't load af file '%s' on entity '%s'",
                fileName[0],
                name
            )
        }
        af.Start()
        af.GetPhysics().Rotate(spawnAxis.ToRotation())
        af.GetPhysics().Translate(spawnOrigin)
        LoadState(spawnArgs)
        af.UpdateAnimation()
        animator.CreateFrame(Game_local.gameLocal.time, true)
        UpdateVisuals()
        return true
    }

    fun IsActiveAF(): Boolean {
        return af.IsActive()
    }

    fun GetAFName(): String {
        return af.GetName()
    }

    fun GetAFPhysics(): idPhysics_AF {
        return af.GetPhysics()
    }

    open fun SetCombatModel() {
        if (combatModel != null) {
            combatModel!!.Unlink()
            combatModel!!.LoadModel(modelDefHandle)
        } else {
            combatModel = idClipModel(modelDefHandle)
        }
    }

    open fun GetCombatModel(): idClipModel? {
        return combatModel
    }

    // contents of combatModel can be set to 0 or re-enabled (mp)
    fun SetCombatContents(enable: Boolean) {
        assert(combatModel != null)
        if (enable && combatModelContents != 0) {
            assert(0 == combatModel!!.GetContents())
            combatModel!!.SetContents(combatModelContents)
            combatModelContents = 0
        } else if (!enable && combatModel!!.GetContents() != 0) {
            assert(0 == combatModelContents)
            combatModelContents = combatModel!!.GetContents()
            combatModel!!.SetContents(0)
        }
    }

    open fun LinkCombat() {
        if (fl.hidden) {
            return
        }
        if (combatModel != null) {
            combatModel!!.Link(
                Game_local.gameLocal.clip,
                this,
                0,
                renderEntity!!.origin,
                renderEntity!!.axis,
                modelDefHandle
            )
        }
    }

    open fun UnlinkCombat() {
        if (combatModel != null) {
            combatModel!!.Unlink()
        }
    }

    fun BodyForClipModelId(id: Int): Int {
        return af.BodyForClipModelId(id)
    }

    fun SaveState(args: idDict) {
        var kv: idKeyValue?

        // save the ragdoll pose
        af.SaveState(args)

        // save all the bind constraints
        kv = spawnArgs.MatchPrefix("bindConstraint ", null)
        while (kv != null) {
            args.Set(kv.GetKey(), kv.GetValue())
            kv = spawnArgs.MatchPrefix("bindConstraint ", kv)
        }

        // save the bind if it exists
        kv = spawnArgs.FindKey("bind")
        if (kv != null) {
            args.Set(kv.GetKey(), kv.GetValue())
        }
        kv = spawnArgs.FindKey("bindToJoint")
        if (kv != null) {
            args.Set(kv.GetKey(), kv.GetValue())
        }
        kv = spawnArgs.FindKey("bindToBody")
        if (kv != null) {
            args.Set(kv.GetKey(), kv.GetValue())
        }
    }

    fun LoadState(args: idDict) {
        af.LoadState(args)
    }

    fun AddBindConstraints() {
        af.AddBindConstraints()
    }

    fun RemoveBindConstraints() {
        af.RemoveBindConstraints()
    }

    override fun ShowEditingDialog() {
        idLib.common.InitTool(Common.EDITOR_AF, spawnArgs)
    }

    protected fun Event_SetConstraintPosition(name: idEventArg<String>, pos: idEventArg<idVec3>) {
        af.SetConstraintPosition(name.value, pos.value)
    }

    override fun GetType(): idTypeInfo = Type
    override fun CreateInstance(): idClass = idAFEntity_Base()

    override fun getEventCallBack(event: idEventDef): eventCallback_t<*>? {
        return eventCallbacks[event]
    }

    init {
        af = idAF()
        combatModel = null
        combatModelContents = 0
        nextSoundTime = 0
        spawnOrigin = idVec3()
        spawnOrigin.Zero()
        spawnAxis = idMat3.getMat3_identity()
        spawnAxis.Identity()
    }
}

open class idAFEntity_Gibbable : idAFEntity_Base() {
    companion object {
        val Type = idTypeInfo("idAFEntity_Gibbable", "idAFEntity_Base") { idAFEntity_Gibbable() }

        // CLASS_PROTOTYPE( idAFEntity_Gibbable );
        private val eventCallbacks: MutableMap<idEventDef, eventCallback_t<*>> = HashMap()

        // ~idAFEntity_Gibbable( void );
        fun getEventCallBacks(): MutableMap<idEventDef, eventCallback_t<*>> {
            return eventCallbacks
        }

        init {
            eventCallbacks.putAll(idAFEntity_Base.getEventCallBacks())
            eventCallbacks[EV_Gib] =
                eventCallback_t1<idAFEntity_Gibbable> { obj: idAFEntity_Gibbable, damageDefName: idEventArg<*>? ->
                    obj.Event_Gib(damageDefName as idEventArg<String>)
                }
            eventCallbacks[EV_Gibbed] =
                eventCallback_t0<idAFEntity_Gibbable> { obj: idAFEntity_Gibbable -> obj.Event_Remove() }
        }
    }

    protected var gibbed: Boolean
    protected var skeletonModel: idRenderModel? = null
    protected var skeletonModelDefHandle: Int
    override fun Spawn() {
        super.Spawn()
        InitSkeletonModel()
        gibbed = false
    }

    override fun Save(savefile: idSaveGame) {
        super.Save(savefile)
        savefile.WriteBool(gibbed)
        savefile.WriteBool(combatModel != null)
    }

    override fun Restore(savefile: idRestoreGame) {
        super.Restore(savefile)
        val hasCombatModel = CBool(false)
        val gibbed = CBool(false)
        savefile.ReadBool(gibbed)
        savefile.ReadBool(hasCombatModel)
        this.gibbed = gibbed._val
        InitSkeletonModel()
        if (hasCombatModel._val) {
            SetCombatModel()
            LinkCombat()
        }
    }

    override fun Present() {
        if (!Game_local.gameLocal.isNewFrame) {
            return
        }

        // don't present to the renderer if the entity hasn't changed
        if ((thinkFlags and TH_UPDATEVISUALS) == 0) {
            return
        }

        // update skeleton model
        if (gibbed && !IsHidden() && skeletonModel != null) {
            val skeleton = renderEntity_s(renderEntity!!)
            skeleton.hModel = skeletonModel
            // add to refresh list
            if (skeletonModelDefHandle == -1) {
                skeletonModelDefHandle = Game_local.gameRenderWorld!!.AddEntityDef(skeleton)
            } else {
                Game_local.gameRenderWorld!!.UpdateEntityDef(skeletonModelDefHandle, skeleton)
            }
        }
        idEntity_Present()
    }

    override fun Damage(
        inflictor: idEntity?,
        attacker: idEntity?,
        dir: idVec3,
        damageDefName: String,
        damageScale: Float,
        location: Int
    ) {
        if (!fl.takedamage) {
            return
        }
        super.Damage(inflictor, attacker, dir, damageDefName, damageScale, location)
        if (health < -20 && spawnArgs.GetBool("gib")) {
            Gib(dir, damageDefName)
        }
    }

    open fun SpawnGibs(dir: idVec3, damageDefName: String) {
        var i: Int
        val gibNonSolid: Boolean
        val entityCenter = idVec3()
        val velocity = idVec3()
        val list = idList<idEntity>()
        assert(!Game_local.gameLocal.isClient)
        val damageDef = Game_local.gameLocal.FindEntityDefDict(damageDefName)
        if (null == damageDef) {
            idGameLocal.Error("Unknown damageDef '%s'", damageDefName)
            return
        }

        // spawn gib articulated figures
        DropAFs(this, "gib", list)

        // spawn gib items
        idMoveableItem.DropItems(this, "gib", list)

        // blow out the gibs in the given direction away from the center of the entity
        entityCenter.set(GetPhysics().GetAbsBounds().GetCenter())
        gibNonSolid = damageDef.GetBool("gibNonSolid")
        i = 0
        while (i < list.Num()) {
            if (gibNonSolid) {
                list[i].GetPhysics().SetContents(0)
                list[i].GetPhysics().SetClipMask(0)
                list[i].GetPhysics().UnlinkClip()
                list[i].GetPhysics().PutToRest()
            } else {
                list[i].GetPhysics().SetContents(Material.CONTENTS_CORPSE)
                list[i].GetPhysics().SetClipMask(Material.CONTENTS_SOLID)
                velocity.set(list[i].GetPhysics().GetAbsBounds().GetCenter().minus(entityCenter))
                velocity.NormalizeFast()
                velocity.plusAssign(if ((i and 1) == 1) dir else dir.unaryMinus())
                list[i].GetPhysics().SetLinearVelocity(velocity.times(75.0f))
            }
            list[i].GetRenderEntity()!!.noShadow = true
            list[i].GetRenderEntity()!!.shaderParms[RenderWorld.SHADERPARM_TIME_OF_DEATH] =
                Game_local.gameLocal.time * 0.001f
            list[i].PostEventSec(EV_Remove, 4.0f)
            i++
        }
    }

    protected open fun Gib(dir: idVec3, damageDefName: String) {
        // only gib once
        if (gibbed) {
            return
        }
        val damageDef = Game_local.gameLocal.FindEntityDefDict(damageDefName)
        if (null == damageDef) {
            idGameLocal.Error("Unknown damageDef '%s'", damageDefName)
            return
        }
        if (damageDef.GetBool("gibNonSolid")) {
            GetAFPhysics().SetContents(0)
            GetAFPhysics().SetClipMask(0)
            GetAFPhysics().UnlinkClip()
            GetAFPhysics().PutToRest()
        } else {
            GetAFPhysics().SetContents(Material.CONTENTS_CORPSE)
            GetAFPhysics().SetClipMask(Material.CONTENTS_SOLID)
        }
        UnlinkCombat()
        if (SysCvar.g_bloodEffects.GetBool()) {
            if (Game_local.gameLocal.time > Game_local.gameLocal.GetGibTime()) {
                Game_local.gameLocal.SetGibTime(Game_local.gameLocal.time + GIB_DELAY)
                SpawnGibs(dir, damageDefName)
                renderEntity!!.noShadow = true
                renderEntity!!.shaderParms[RenderWorld.SHADERPARM_TIME_OF_DEATH] =
                    Game_local.gameLocal.time * 0.001f
                StartSound("snd_gibbed", gameSoundChannel_t.SND_CHANNEL_ANY, 0, false)
                gibbed = true
            }
        } else {
            gibbed = true
        }
        PostEventSec(EV_Gibbed, 4.0f)
    }

    protected fun InitSkeletonModel() {
        val modelName: String?
        val modelDef: idDeclModelDef?
        skeletonModel = null
        skeletonModelDefHandle = -1
        modelName = spawnArgs.GetString("model_gib")
        if (!modelName.isEmpty()) { //[0] != '\0' ) {
            modelDef =
                DeclManager.declManager.FindType(declType_t.DECL_MODELDEF, modelName, false) as idDeclModelDef?
            skeletonModel = if (modelDef != null) {
                modelDef.ModelHandle()
            } else {
                ModelManager.renderModelManager.FindModel(modelName)
            }
            if (skeletonModel != null && renderEntity!!.hModel != null) {
                if (skeletonModel!!.NumJoints() != renderEntity!!.hModel!!.NumJoints()) {
                    idGameLocal.Error(
                        "gib model '%s' has different number of joints than model '%s'",
                        skeletonModel!!.Name(), renderEntity!!.hModel!!.Name()
                    )
                }
            }
        }
    }

    protected open fun Event_Gib(damageDefName: idEventArg<String>) {
        Gib(idVec3(0, 0, 1), damageDefName.value)
    }

    /**
     * inherited grandfather functions.
     */
    fun idAFEntity_Base_Think() {
        super.Think()
    }

    fun idAFEntity_Base_Hide() {
        super.Hide()
    }

    fun idAFEntity_Base_Show() {
        super.Show()
    }

    fun idAFEntity_Base_UpdateAnimationControllers(): Boolean {
        return super.UpdateAnimationControllers()
    }

    override fun GetType(): idTypeInfo = Type
    override fun CreateInstance(): idClass = idAFEntity_Gibbable()

    override fun getEventCallBack(event: idEventDef): eventCallback_t<*>? {
        return eventCallbacks[event]
    }

    override fun _deconstructor() {
        if (skeletonModelDefHandle != -1) {
            Game_local.gameRenderWorld!!.FreeEntityDef(skeletonModelDefHandle)
            skeletonModelDefHandle = -1
        }
        super._deconstructor()
    }

    //
    //
    init {
        skeletonModelDefHandle = -1
        gibbed = false
    }
}

/*
 ===============================================================================

 idAFEntity_Generic

 ===============================================================================
 */
class idAFEntity_Generic : idAFEntity_Gibbable() {
    companion object {
        val Type = idTypeInfo("idAFEntity_Generic", "idAFEntity_Gibbable") { idAFEntity_Generic() }

        // CLASS_PROTOTYPE( idAFEntity_Generic );
        private val eventCallbacks: MutableMap<idEventDef, eventCallback_t<*>> = HashMap()

        // ~idAFEntity_Generic( void );
        fun getEventCallBacks(): MutableMap<idEventDef, eventCallback_t<*>> {
            return eventCallbacks
        }

        init {
            eventCallbacks.putAll(idAFEntity_Gibbable.getEventCallBacks())
            eventCallbacks[EV_Activate] =
                eventCallback_t1<idAFEntity_Generic> { obj: idAFEntity_Generic, activator: idEventArg<*>? ->
                    obj.Event_Activate(activator as idEventArg<idEntity?>)
                }
        }
    }

    private val keepRunningPhysics: CBool = CBool(false)
    override fun Spawn() {
        super.Spawn()
        if (!LoadAF()) {
            idGameLocal.Error("Couldn't load af file on entity '%s'", name)
        }
        SetCombatModel()
        SetPhysics(af.GetPhysics())
        af.GetPhysics().PutToRest()
        if (!spawnArgs.GetBool("nodrop", "0")) {
            af.GetPhysics().Activate()
        }
        fl.takedamage = true
    }

    override fun Save(savefile: idSaveGame) {
        super.Save(savefile)
        savefile.WriteBool(keepRunningPhysics._val)
    }

    override fun Restore(savefile: idRestoreGame) {
        super.Restore(savefile)
        savefile.ReadBool(keepRunningPhysics)
    }

    override fun Think() {
        idAFEntity_Base_Think()
        if (keepRunningPhysics._val) {
            BecomeActive(TH_PHYSICS)
        }
    }

    fun KeepRunningPhysics() {
        keepRunningPhysics._val = true
    }

    private fun Event_Activate(activator: idEventArg<idEntity?>) {
        var delay: Float
        val init_velocity = idVec3()
        val init_avelocity = idVec3()
        Show()
        af.GetPhysics().EnableImpact()
        af.GetPhysics().Activate()
        spawnArgs.GetVector("init_velocity", "0 0 0", init_velocity)
        spawnArgs.GetVector("init_avelocity", "0 0 0", init_avelocity)
        delay = spawnArgs.GetFloat("init_velocityDelay", "0")
        if (delay == 0.0f) {
            af.GetPhysics().SetLinearVelocity(init_velocity)
        } else {
            PostEventSec(EV_SetLinearVelocity, delay, init_velocity)
        }
        delay = spawnArgs.GetFloat("init_avelocityDelay", "0")
        if (delay == 0.0f) {
            af.GetPhysics().SetAngularVelocity(init_avelocity)
        } else {
            PostEventSec(EV_SetAngularVelocity, delay, init_avelocity)
        }
    }

    override fun GetType(): idTypeInfo = Type
    override fun CreateInstance(): idClass = idAFEntity_Generic()

    override fun getEventCallBack(event: idEventDef): eventCallback_t<*>? {
        return eventCallbacks[event]
    }

    //
    //
    init {
        keepRunningPhysics._val = false
    }
}

/*
 ===============================================================================

 idAFEntity_WithAttachedHead

 ===============================================================================
 */
class idAFEntity_WithAttachedHead : idAFEntity_Gibbable() {
    companion object {
        val Type = idTypeInfo("idAFEntity_WithAttachedHead", "idAFEntity_Gibbable") { idAFEntity_WithAttachedHead() }

        // CLASS_PROTOTYPE( idAFEntity_WithAttachedHead );
        private val eventCallbacks: MutableMap<idEventDef, eventCallback_t<*>> = HashMap()
        fun getEventCallBacks(): MutableMap<idEventDef, eventCallback_t<*>> {
            return eventCallbacks
        }

        init {
            eventCallbacks.putAll(idAFEntity_Gibbable.getEventCallBacks())
            eventCallbacks[EV_Gib] =
                eventCallback_t1<idAFEntity_WithAttachedHead> { obj: idAFEntity_WithAttachedHead, damageDefName: idEventArg<*>? ->
                    obj.Event_Gib(damageDefName as idEventArg<String>)
                }
            eventCallbacks[EV_Activate] =
                eventCallback_t1<idAFEntity_WithAttachedHead> { obj: idAFEntity_WithAttachedHead, activator: idEventArg<*>? ->
                    obj.Event_Activate(activator as idEventArg<idEntity?>)
                }
        }
    }

    private val head: idEntityPtr<idAFAttachment>

    // ~idAFEntity_WithAttachedHead();
    override fun _deconstructor() {
        if (head.GetEntity() != null) {
            head.GetEntity()!!.ClearBody()
            head.GetEntity()!!.PostEventMS(EV_Remove, 0)
        }
        super._deconstructor()
    }

    override fun Spawn() {
        super.Spawn()
        SetupHead()
        LoadAF()
        SetCombatModel()
        SetPhysics(af.GetPhysics())
        af.GetPhysics().PutToRest()
        if (!spawnArgs.GetBool("nodrop", "0")) {
            af.GetPhysics().Activate()
        }
        fl.takedamage = true
        if (head.GetEntity() != null) {
            val anim = head.GetEntity()!!.GetAnimator().GetAnim("dead")
            if (anim != 0) {
                head.GetEntity()!!.GetAnimator()
                    .SetFrame(Anim.ANIMCHANNEL_ALL, anim, 0, Game_local.gameLocal.time, 0)
            }
        }
    }

    override fun Save(savefile: idSaveGame) {
        super.Save(savefile)
        head.Save(savefile)
    }

    override fun Restore(savefile: idRestoreGame) {
        super.Restore(savefile)
        head.Restore(savefile)
    }

    fun SetupHead() {
        val headEnt: idAFAttachment
        val jointName: String?
        val headModel: String?
        val   /*jointHandle_t*/joint: Int
        val origin = idVec3()
        val axis = idMat3()
        headModel = spawnArgs.GetString("def_head", "")!!
        if (!headModel.isEmpty()) { //[ 0 ] ) {
            jointName = spawnArgs.GetString("head_joint")
            joint = animator.GetJointHandle(jointName)
            if (joint == Model.INVALID_JOINT) {
                idGameLocal.Error(
                    "Joint '%s' not found for 'head_joint' on '%s'",
                    jointName,
                    name.toString()
                )
            }
            headEnt = Game_local.gameLocal.SpawnEntityType(idAFAttachment.Type, null) as idAFAttachment
            headEnt.SetName(Str.va("%s_head", name))
            headEnt.SetBody(this, headModel, joint)
            headEnt.SetCombatModel()
            head.oSet(headEnt)
            animator.GetJointTransform(joint, Game_local.gameLocal.time, origin, axis)
            origin.set(renderEntity!!.origin + origin * renderEntity!!.axis)
            headEnt.SetOrigin(origin)
            headEnt.SetAxis(renderEntity!!.axis)
            headEnt.BindToJoint(this, joint, true)
        }
    }

    override fun Think() {
        idAFEntity_Base_Think()
    }

    override fun Hide() {
        idAFEntity_Base_Hide()
        if (head.GetEntity() != null) {
            head.GetEntity()!!.Hide()
        }
        UnlinkCombat()
    }

    override fun Show() {
        idAFEntity_Base_Show()
        if (head.GetEntity() != null) {
            head.GetEntity()!!.Show()
        }
        LinkCombat()
    }

    override fun ProjectOverlay(origin: idVec3, dir: idVec3, size: Float, material: String) {
        idEntity_ProjectOverlay(origin, dir, size, material)
        if (head.GetEntity() != null) {
            head.GetEntity()!!.ProjectOverlay(origin, dir, size, material)
        }
    }

    override fun LinkCombat() {
        val headEnt: idAFAttachment?
        if (fl.hidden) {
            return
        }
        if (combatModel != null) {
            combatModel!!.Link(
                Game_local.gameLocal.clip,
                this,
                0,
                renderEntity!!.origin,
                renderEntity!!.axis,
                modelDefHandle
            )
        }
        headEnt = head.GetEntity()
        headEnt?.LinkCombat()
    }

    override fun UnlinkCombat() {
        val headEnt: idAFAttachment?
        if (combatModel != null) {
            combatModel!!.Unlink()
        }
        headEnt = head.GetEntity()
        headEnt?.UnlinkCombat()
    }

    override fun Gib(dir: idVec3, damageDefName: String) {
        // only gib once
        if (gibbed) {
            return
        }
        super.Gib(dir, damageDefName)
        if (head.GetEntity() != null) {
            head.GetEntity()!!.Hide()
        }
    }

    override fun Event_Gib(damageDefName: idEventArg<String>) {
        Gib(idVec3(0, 0, 1), damageDefName.value)
    }

    private fun Event_Activate(activator: idEventArg<idEntity?>) {
        var delay: Float
        val init_velocity = idVec3()
        val init_avelocity = idVec3()
        Show()
        af.GetPhysics().EnableImpact()
        af.GetPhysics().Activate()
        spawnArgs.GetVector("init_velocity", "0 0 0", init_velocity)
        spawnArgs.GetVector("init_avelocity", "0 0 0", init_avelocity)
        delay = spawnArgs.GetFloat("init_velocityDelay", "0")
        if (delay == 0.0f) {
            af.GetPhysics().SetLinearVelocity(init_velocity)
        } else {
            PostEventSec(EV_SetLinearVelocity, delay, init_velocity)
        }
        delay = spawnArgs.GetFloat("init_avelocityDelay", "0")
        if (delay == 0.0f) {
            af.GetPhysics().SetAngularVelocity(init_avelocity)
        } else {
            PostEventSec(EV_SetAngularVelocity, delay, init_avelocity)
        }
    }

    override fun GetType(): idTypeInfo = Type
    override fun CreateInstance(): idClass = idAFEntity_WithAttachedHead()

    override fun getEventCallBack(event: idEventDef): eventCallback_t<*>? {
        return eventCallbacks[event]
    }

    //
    //
    init {
        head = idEntityPtr()
    }
}

/*
 ===============================================================================

 idAFEntity_Vehicle

 ===============================================================================
 */
open class idAFEntity_Vehicle : idAFEntity_Base() {
    companion object {
        val Type = idTypeInfo("idAFEntity_Vehicle", "idAFEntity_Base") { idAFEntity_Vehicle() }
    }

    override fun GetType(): idTypeInfo = Type
    override fun CreateInstance(): idClass = idAFEntity_Vehicle()

    // CLASS_PROTOTYPE( idAFEntity_Vehicle );
    protected var dustSmoke: idDeclParticle?
    protected var   /*jointHandle_t*/eyesJoint: Int
    protected var player: idPlayer? = null
    protected var steerAngle: Float
    protected var steerSpeed: Float
    protected var   /*jointHandle_t*/steeringWheelJoint: Int
    protected var wheelRadius: Float
    override fun Spawn() {
        super.Spawn()
        val eyesJointName = spawnArgs.GetString("eyesJoint", "eyes")!!
        val steeringWheelJointName = spawnArgs.GetString("steeringWheelJoint", "steeringWheel")!!
        val wheel = CFloat()
        val steer = CFloat()
        LoadAF()
        SetCombatModel()
        SetPhysics(af.GetPhysics())
        fl.takedamage = true

//	if ( !eyesJointName[0] ) {
        if (eyesJointName.isEmpty()) {
            idGameLocal.Error("idAFEntity_Vehicle '%s' no eyes joint specified", name)
        }
        eyesJoint = animator.GetJointHandle(eyesJointName)
        //	if ( !steeringWheelJointName[0] ) {
        if (steeringWheelJointName.isEmpty()) {
            idGameLocal.Error("idAFEntity_Vehicle '%s' no steering wheel joint specified", name)
        }
        steeringWheelJoint = animator.GetJointHandle(steeringWheelJointName)
        spawnArgs.GetFloat("wheelRadius", "20", wheel)
        spawnArgs.GetFloat("steerSpeed", "5", steer)
        wheelRadius = wheel._val
        steerSpeed = steer._val
        player = null
        steerAngle = 0.0f
        val smokeName = spawnArgs.GetString("smoke_vehicle_dust", "muzzlesmoke")!!
        if (!smokeName.isEmpty()) { // != '\0' ) {
            dustSmoke = DeclManager.declManager.FindType(declType_t.DECL_PARTICLE, smokeName) as idDeclParticle
        }
    }

    fun Use(other: idPlayer) {
        val origin = idVec3()
        val axis = idMat3()
        if (player != null) {
            if (player == other) {
                other.Unbind()
                player = null
                af.GetPhysics().SetComeToRest(true)
            }
        } else {
            player = other
            animator.GetJointTransform(eyesJoint, Game_local.gameLocal.time, origin, axis)
            origin.set(renderEntity!!.origin.plus(origin.times(renderEntity!!.axis)))
            player!!.GetPhysics().SetOrigin(origin)
            player!!.BindToBody(this, 0, true)
            af.GetPhysics().SetComeToRest(false)
            af.GetPhysics().Activate()
        }
    }

    protected fun GetSteerAngle(): Float {
        val idealSteerAngle: Float
        val angleDelta: Float
        idealSteerAngle = player!!.usercmd.rightmove * (30 / 128.0f)
        angleDelta = idealSteerAngle - steerAngle
        if (angleDelta > steerSpeed) {
            steerAngle += steerSpeed
        } else if (angleDelta < -steerSpeed) {
            steerAngle -= steerSpeed
        } else {
            steerAngle = idealSteerAngle
        }
        return steerAngle
    }

    //
    //
    init {
        eyesJoint = Model.INVALID_JOINT
        steeringWheelJoint = Model.INVALID_JOINT
        wheelRadius = 0.0f
        steerAngle = 0.0f
        steerSpeed = 0.0f
        dustSmoke = null
    }
}

/*
 ===============================================================================
 idAFEntity_VehicleSimple
 ===============================================================================
 */
class idAFEntity_VehicleSimple : idAFEntity_Vehicle() {
    protected val suspension: Array<idAFConstraint_Suspension?> = arrayOfNulls(4)
    protected val wheelAngles: FloatArray = FloatArray(4)
    protected val wheelJoints: IntArray = IntArray(4)
    protected lateinit var wheelModel: idClipModel

    override fun Spawn() {
        super.Spawn()
        var i: Int
        val origin = idVec3()
        val axis = idMat3()
        val trm = idTraceModel()
        trm.SetupPolygon(wheelPoly, 4)
        trm.Translate(idVec3(0.0f, 0.0f, -wheelRadius))
        wheelModel = idClipModel(trm)
        i = 0
        while (i < 4) {
            val wheelJointName = spawnArgs.GetString(wheelJointKeys[i], "")!!
            //		if ( !wheelJointName[0] ) {
            if (wheelJointName.isEmpty()) {
                idGameLocal.Error(
                    "idAFEntity_VehicleSimple '%s' no '%s' specified",
                    name,
                    wheelJointKeys[i]
                )
            }
            wheelJoints[i] = animator.GetJointHandle(wheelJointName)
            if (wheelJoints[i] == Model.INVALID_JOINT) {
                idGameLocal.Error(
                    "idAFEntity_VehicleSimple '%s' can't find wheel joint '%s'",
                    name,
                    wheelJointName
                )
            }
            GetAnimator().GetJointTransform(wheelJoints[i], 0, origin, axis)
            origin.set(renderEntity!!.origin.plus(origin.times(renderEntity!!.axis)))
            suspension[i] = idAFConstraint_Suspension()
            suspension[i]!!.Setup(
                Str.va("suspension%d", i),
                af.GetPhysics().GetBody(0),
                origin,
                af.GetPhysics().GetAxis(0),
                wheelModel
            )
            suspension[i]!!.SetSuspension(
                SysCvar.g_vehicleSuspensionUp.GetFloat(),
                SysCvar.g_vehicleSuspensionDown.GetFloat(),
                SysCvar.g_vehicleSuspensionKCompress.GetFloat(),
                SysCvar.g_vehicleSuspensionDamping.GetFloat(),
                SysCvar.g_vehicleTireFriction.GetFloat()
            )
            af.GetPhysics().AddConstraint(suspension[i]!!)
            i++
        }

//            memset(wheelAngles, 0, sizeof(wheelAngles));
        Arrays.fill(wheelAngles, 0.0f)
        BecomeActive(TH_THINK)
    }

    override fun Think() {
        var i: Int
        var force = 0.0f
        var velocity = 0.0f
        var steerAngle = 0.0f
        val origin = idVec3()
        val wheelRotation = idRotation()
        val steerRotation = idRotation()
        if ((thinkFlags and TH_THINK) != 0) {
            if (player != null) {
                // capture the input from a player
                velocity = SysCvar.g_vehicleVelocity.GetFloat()
                if (player!!.usercmd.forwardmove < 0) {
                    velocity = -velocity
                }
                force = abs(player!!.usercmd.forwardmove * SysCvar.g_vehicleForce.GetFloat()) * (1.0f / 128.0f)
                steerAngle = GetSteerAngle()
            }

            // update the wheel motor force and steering
            i = 0
            while (i < 2) {


                // front wheel drive
                suspension[i]!!.EnableMotor(velocity != 0.0f)
                suspension[i]!!.SetMotorVelocity(velocity)
                suspension[i]!!.SetMotorForce(force)

                // update the wheel steering
                suspension[i]!!.SetSteerAngle(steerAngle)
                i++
            }

            // adjust wheel velocity for better steering because there are no differentials between the wheels
            if (steerAngle < 0) {
                suspension[0]!!.SetMotorVelocity(velocity * 0.5f)
            } else if (steerAngle > 0) {
                suspension[1]!!.SetMotorVelocity(velocity * 0.5f)
            }

            // update suspension with latest cvar settings
            i = 0
            while (i < 4) {
                suspension[i]!!.SetSuspension(
                    SysCvar.g_vehicleSuspensionUp.GetFloat(),
                    SysCvar.g_vehicleSuspensionDown.GetFloat(),
                    SysCvar.g_vehicleSuspensionKCompress.GetFloat(),
                    SysCvar.g_vehicleSuspensionDamping.GetFloat(),
                    SysCvar.g_vehicleTireFriction.GetFloat()
                )
                i++
            }

            // run the physics
            RunPhysics()

            // move and rotate the wheels visually
            i = 0
            while (i < 4) {
                val body = af.GetPhysics().GetBody(0)
                origin.set(suspension[i]!!.GetWheelOrigin())
                velocity = body!!.GetPointVelocity(origin).times(body.GetWorldAxis()[0])
                wheelAngles[i] += velocity * MS2SEC(idGameLocal.msec.toFloat()) / wheelRadius

                // additional rotation about the wheel axis
                wheelRotation.SetAngle(RAD2DEG(wheelAngles[i]))
                wheelRotation.SetVec(0.0f, -1.0f, 0.0f)
                if (i < 2) {
                    // rotate the wheel for steering
                    steerRotation.SetAngle(steerAngle)
                    steerRotation.SetVec(0.0f, 0.0f, 1.0f)
                    // set wheel rotation
                    animator.SetJointAxis(
                        wheelJoints[i],
                        jointModTransform_t.JOINTMOD_WORLD,
                        wheelRotation.ToMat3().times(steerRotation.ToMat3())
                    )
                } else {
                    // set wheel rotation
                    animator.SetJointAxis(
                        wheelJoints[i],
                        jointModTransform_t.JOINTMOD_WORLD,
                        wheelRotation.ToMat3()
                    )
                }

                // set wheel position for suspension
                origin.set(origin.minus(renderEntity!!.origin).times(renderEntity!!.axis.Transpose()))
                GetAnimator().SetJointPos(wheelJoints[i], jointModTransform_t.JOINTMOD_WORLD_OVERRIDE, origin)
                i++
            }
            /*
             // spawn dust particle effects
             if ( force != 0 && !( gameLocal.framenum & 7 ) ) {
             int numContacts;
             idAFConstraint_Contact *contacts[2];
             for ( i = 0; i < 4; i++ ) {
             numContacts = af.GetPhysics().GetBodyContactConstraints( wheels[i].GetClipModel().GetId(), contacts, 2 );
             for ( int j = 0; j < numContacts; j++ ) {
             gameLocal.smokeParticles.EmitSmoke( dustSmoke, gameLocal.time, gameLocal.random.RandomFloat(), contacts[j].GetContact().point, contacts[j].GetContact().normal.ToMat3() );
             }
             }
             }
             */
        }
        UpdateAnimation()
        if ((thinkFlags and TH_UPDATEVISUALS) != 0) {
            Present()
            LinkCombat()
        }
    }

    companion object {
        val Type = idTypeInfo("idAFEntity_VehicleSimple", "idAFEntity_Vehicle") { idAFEntity_VehicleSimple() }

        // ~idAFEntity_VehicleSimple();
        private val wheelJointKeys: Array<String> = arrayOf(
            "wheelJointFrontLeft",
            "wheelJointFrontRight",
            "wheelJointRearLeft",
            "wheelJointRearRight"
        )
        private val wheelPoly /*[4]*/: Array<idVec3> = arrayOf(
            idVec3(2, 2, 0),
            idVec3(2, -2, 0),
            idVec3(-2, -2, 0),
            idVec3(-2, 2, 0)
        )
    }

    // public:
    // CLASS_PROTOTYPE( idAFEntity_VehicleSimple );

    override fun GetType(): idTypeInfo = Type
    override fun CreateInstance(): idClass = idAFEntity_VehicleSimple()
}

/*
 ===============================================================================
 idAFEntity_VehicleFourWheels
 ===============================================================================
 */
class idAFEntity_VehicleFourWheels : idAFEntity_Vehicle() {
    protected val steering: Array<idAFConstraint_Hinge?> = arrayOfNulls(2)
    protected val wheelAngles: FloatArray = FloatArray(4)
    protected val wheelJoints: IntArray = IntArray(4)
    protected val wheels: Array<idAFBody?> = arrayOfNulls(4)
    override fun Spawn() {
        super.Spawn()
        var i: Int
        var wheelBodyName: String?
        var wheelJointName: String?
        var steeringHingeName: String?
        i = 0
        while (i < 4) {
            wheelBodyName = spawnArgs.GetString(wheelBodyKeys[i], "")!!
            //		if ( !wheelBodyName[0] ) {
            if (wheelBodyName.isEmpty()) {
                idGameLocal.Error(
                    "idAFEntity_VehicleFourWheels '%s' no '%s' specified",
                    name,
                    wheelBodyKeys[i]
                )
            }
            // FIX: Removed !! that would NPE before null check could execute
            wheels[i] = af.GetPhysics().GetBody(wheelBodyName)
            if (wheels[i] == null) {
                idGameLocal.Error(
                    "idAFEntity_VehicleFourWheels '%s' can't find wheel body '%s'",
                    name,
                    wheelBodyName
                )
            }
            wheelJointName = spawnArgs.GetString(wheelJointKeys[i], "")!!
            //		if ( !wheelJointName[0] ) {
            if (wheelJointName.isEmpty()) {
                idGameLocal.Error(
                    "idAFEntity_VehicleFourWheels '%s' no '%s' specified",
                    name,
                    wheelJointKeys[i]
                )
            }
            wheelJoints[i] = animator.GetJointHandle(wheelJointName)
            if (wheelJoints[i] == Model.INVALID_JOINT) {
                idGameLocal.Error(
                    "idAFEntity_VehicleFourWheels '%s' can't find wheel joint '%s'",
                    name,
                    wheelJointName
                )
            }
            i++
        }
        i = 0
        while (i < 2) {
            steeringHingeName = spawnArgs.GetString(steeringHingeKeys[i], "")!!
            //		if ( !steeringHingeName[0] ) {
            if (steeringHingeName.isEmpty()) {
                idGameLocal.Error(
                    "idAFEntity_VehicleFourWheels '%s' no '%s' specified",
                    name,
                    steeringHingeKeys[i]
                )
            }
            steering[i] = af.GetPhysics().GetConstraint(steeringHingeName) as idAFConstraint_Hinge
            if (steering[i] == null) {
                idGameLocal.Error(
                    "idAFEntity_VehicleFourWheels '%s': can't find steering hinge '%s'",
                    name,
                    steeringHingeName
                )
            }
            i++
        }

//	memset( wheelAngles, 0, sizeof( wheelAngles ) );
        Arrays.fill(wheelAngles, 0.0f)
        BecomeActive(TH_THINK)
    }

    override fun Think() {
        var i: Int
        var force = 0.0f
        var velocity = 0.0f
        var steerAngle = 0.0f
        val origin = idVec3()
        val axis = idMat3()
        val rotation = idRotation()
        if ((thinkFlags and TH_THINK) != 0) {
            if (player != null) {
                // capture the input from a player
                velocity = SysCvar.g_vehicleVelocity.GetFloat()
                if (player!!.usercmd.forwardmove < 0) {
                    velocity = -velocity
                }
                force = abs(player!!.usercmd.forwardmove * SysCvar.g_vehicleForce.GetFloat()) * (1.0f / 128.0f)
                steerAngle = GetSteerAngle()
            }

            // update the wheel motor force
            i = 0
            while (i < 2) {
                wheels[2 + i]!!.SetContactMotorVelocity(velocity)
                wheels[2 + i]!!.SetContactMotorForce(force)
                i++
            }

            // adjust wheel velocity for better steering because there are no differentials between the wheels
            if (steerAngle < 0) {
                wheels[2]!!.SetContactMotorVelocity(velocity * 0.5f)
            } else if (steerAngle > 0) {
                wheels[3]!!.SetContactMotorVelocity(velocity * 0.5f)
            }

            // update the wheel steering
            steering[0]!!.SetSteerAngle(steerAngle)
            steering[1]!!.SetSteerAngle(steerAngle)
            i = 0
            while (i < 2) {
                steering[i]!!.SetSteerSpeed(3.0f)
                i++
            }

            // update the steering wheel
            animator.GetJointTransform(steeringWheelJoint, Game_local.gameLocal.time, origin, axis)
            rotation.SetVec(axis[2])
            rotation.SetAngle(-steerAngle)
            animator.SetJointAxis(steeringWheelJoint, jointModTransform_t.JOINTMOD_WORLD, rotation.ToMat3())

            // run the physics
            RunPhysics()

            // rotate the wheels visually
            i = 0
            while (i < 4) {
                if (force == 0.0f) {
                    velocity = wheels[i]!!.GetLinearVelocity().times(wheels[i]!!.GetWorldAxis()[0])
                }
                wheelAngles[i] += velocity * MS2SEC(idGameLocal.msec.toFloat()) / wheelRadius
                // give the wheel joint an additional rotation about the wheel axis
                rotation.SetAngle(RAD2DEG(wheelAngles[i]))
                axis.set(af.GetPhysics().GetAxis(0))
                rotation.SetVec(wheels[i]!!.GetWorldAxis().times(axis.Transpose())[2])
                animator.SetJointAxis(wheelJoints[i], jointModTransform_t.JOINTMOD_WORLD, rotation.ToMat3())
                i++
            }

            // spawn dust particle effects
            if (force != 0.0f && (Game_local.gameLocal.framenum and 7) == 0) {
                var numContacts: Int
                val contacts = arrayOfNulls<idAFConstraint_Contact>(2)
                i = 0
                while (i < 4) {
                    numContacts =
                        af.GetPhysics().GetBodyContactConstraints(wheels[i]!!.GetClipModel()!!.GetId(), contacts, 2)
                    for (j in 0 until numContacts) {
                        Game_local.gameLocal.smokeParticles!!.EmitSmoke(
                            dustSmoke,
                            Game_local.gameLocal.time,
                            Game_local.gameLocal.random.RandomFloat(),
                            contacts[j]!!.GetContact().point,
                            contacts[j]!!.GetContact().normal.ToMat3()
                        )
                    }
                    i++
                }
            }
        }
        UpdateAnimation()
        if ((thinkFlags and TH_UPDATEVISUALS) != 0) {
            Present()
            LinkCombat()
        }
    }

    companion object {
        val Type = idTypeInfo("idAFEntity_VehicleFourWheels", "idAFEntity_Vehicle") { idAFEntity_VehicleFourWheels() }

        private val steeringHingeKeys: Array<String> = arrayOf(
            "steeringHingeFrontLeft",
            "steeringHingeFrontRight"
        )
        private val wheelBodyKeys: Array<String> = arrayOf(
            "wheelBodyFrontLeft",
            "wheelBodyFrontRight",
            "wheelBodyRearLeft",
            "wheelBodyRearRight"
        )
        private val wheelJointKeys: Array<String> = arrayOf(
            "wheelJointFrontLeft",
            "wheelJointFrontRight",
            "wheelJointRearLeft",
            "wheelJointRearRight"
        )
    }

    // public:
    // CLASS_PROTOTYPE( idAFEntity_VehicleFourWheels );
    init {
        var i: Int
        i = 0
        while (i < 4) {
            wheelJoints[i] = Model.INVALID_JOINT
            wheelAngles[i] = 0.0f
            i++
        }
    }

    override fun GetType(): idTypeInfo = Type
    override fun CreateInstance(): idClass = idAFEntity_VehicleFourWheels()
}

/*
 ===============================================================================
 idAFEntity_VehicleSixWheels
 ===============================================================================
 */
class idAFEntity_VehicleSixWheels : idAFEntity_Vehicle() {
    private val steering: Array<idAFConstraint_Hinge?> = arrayOfNulls(4)
    private val wheelAngles: FloatArray = FloatArray(6)
    private val wheelJoints: IntArray = IntArray(6)
    private val wheels: Array<idAFBody?> = arrayOfNulls(6)
    override fun Spawn() {
        super.Spawn()
        var i: Int
        var wheelBodyName: String?
        var wheelJointName: String?
        var steeringHingeName: String?
        i = 0
        while (i < 6) {
            wheelBodyName = spawnArgs.GetString(wheelBodyKeys[i], "")!!
            //		if ( !wheelBodyName[0] ) {
            if (wheelBodyName.isEmpty()) {
                idGameLocal.Error(
                    "idAFEntity_VehicleSixWheels '%s' no '%s' specified",
                    name,
                    wheelBodyKeys[i]
                )
            }
            // FIX: Removed !! that would NPE before null check could execute
            wheels[i] = af.GetPhysics().GetBody(wheelBodyName)
            if (wheels[i] == null) {
                idGameLocal.Error(
                    "idAFEntity_VehicleSixWheels '%s' can't find wheel body '%s'",
                    name,
                    wheelBodyName
                )
            }
            wheelJointName = spawnArgs.GetString(wheelJointKeys[i], "")!!
            //		if ( !wheelJointName[0] ) {
            if (wheelJointName.isEmpty()) {
                idGameLocal.Error(
                    "idAFEntity_VehicleSixWheels '%s' no '%s' specified",
                    name,
                    wheelJointKeys[i]
                )
            }
            wheelJoints[i] = animator.GetJointHandle(wheelJointName)
            if (wheelJoints[i] == Model.INVALID_JOINT) {
                idGameLocal.Error(
                    "idAFEntity_VehicleSixWheels '%s' can't find wheel joint '%s'",
                    name,
                    wheelJointName
                )
            }
            i++
        }
        i = 0
        while (i < 4) {
            steeringHingeName = spawnArgs.GetString(steeringHingeKeys[i], "")!!
            //		if ( !steeringHingeName[0] ) {
            if (steeringHingeName.isEmpty()) {
                idGameLocal.Error(
                    "idAFEntity_VehicleSixWheels '%s' no '%s' specified",
                    name,
                    steeringHingeKeys[i]
                )
            }
            steering[i] = af.GetPhysics().GetConstraint(steeringHingeName) as idAFConstraint_Hinge
            if (steering[i] == null) {
                idGameLocal.Error(
                    "idAFEntity_VehicleSixWheels '%s': can't find steering hinge '%s'",
                    name,
                    steeringHingeName
                )
            }
            i++
        }

//	memset( wheelAngles, 0, sizeof( wheelAngles ) );
        Arrays.fill(wheelAngles, 0.0f)
        BecomeActive(TH_THINK)
    }

    override fun Think() {
        var i: Int
        var force = 0.0f
        var velocity = 0.0f
        var steerAngle = 0.0f
        val origin = idVec3()
        val axis = idMat3()
        val rotation = idRotation()
        if ((thinkFlags and TH_THINK) != 0) {
            if (player != null) {
                // capture the input from a player
                velocity = SysCvar.g_vehicleVelocity.GetFloat()
                if (player!!.usercmd.forwardmove < 0) {
                    velocity = -velocity
                }
                force = abs(player!!.usercmd.forwardmove * SysCvar.g_vehicleForce.GetFloat()) * (1.0f / 128.0f)
                steerAngle = GetSteerAngle()
            }

            // update the wheel motor force
            i = 0
            while (i < 6) {
                wheels[i]!!.SetContactMotorVelocity(velocity)
                wheels[i]!!.SetContactMotorForce(force)
                i++
            }

            // adjust wheel velocity for better steering because there are no differentials between the wheels
            if (steerAngle < 0) {
                i = 0
                while (i < 3) {
                    wheels[i shl 1]!!.SetContactMotorVelocity(velocity * 0.5f)
                    i++
                }
            } else if (steerAngle > 0) {
                i = 0
                while (i < 3) {
                    wheels[1 + (i shl 1)]!!.SetContactMotorVelocity(velocity * 0.5f)
                    i++
                }
            }

            // update the wheel steering
            steering[0]!!.SetSteerAngle(steerAngle)
            steering[1]!!.SetSteerAngle(steerAngle)
            steering[2]!!.SetSteerAngle(-steerAngle)
            steering[3]!!.SetSteerAngle(-steerAngle)
            i = 0
            while (i < 4) {
                steering[i]!!.SetSteerSpeed(3.0f)
                i++
            }

            // update the steering wheel
            animator.GetJointTransform(steeringWheelJoint, Game_local.gameLocal.time, origin, axis)
            rotation.SetVec(axis[2])
            rotation.SetAngle(-steerAngle)
            animator.SetJointAxis(steeringWheelJoint, jointModTransform_t.JOINTMOD_WORLD, rotation.ToMat3())

            // run the physics
            RunPhysics()

            // rotate the wheels visually
            i = 0
            while (i < 6) {
                if (force == 0.0f) {
                    velocity = wheels[i]!!.GetLinearVelocity().times(wheels[i]!!.GetWorldAxis()[0])
                }
                wheelAngles[i] += velocity * MS2SEC(idGameLocal.msec.toFloat()) / wheelRadius
                // give the wheel joint an additional rotation about the wheel axis
                rotation.SetAngle(RAD2DEG(wheelAngles[i]))
                axis.set(af.GetPhysics().GetAxis(0))
                rotation.SetVec(wheels[i]!!.GetWorldAxis().times(axis.Transpose())[2])
                animator.SetJointAxis(wheelJoints[i], jointModTransform_t.JOINTMOD_WORLD, rotation.ToMat3())
                i++
            }

            // spawn dust particle effects
            if (force != 0.0f && (Game_local.gameLocal.framenum and 7) == 0) {
                var numContacts: Int
                val contacts = arrayOfNulls<idAFConstraint_Contact>(2)
                i = 0
                while (i < 6) {
                    numContacts =
                        af.GetPhysics().GetBodyContactConstraints(wheels[i]!!.GetClipModel()!!.GetId(), contacts, 2)
                    for (j in 0 until numContacts) {
                        Game_local.gameLocal.smokeParticles!!.EmitSmoke(
                            dustSmoke,
                            Game_local.gameLocal.time,
                            Game_local.gameLocal.random.RandomFloat(),
                            contacts[j]!!.GetContact().point,
                            contacts[j]!!.GetContact().normal.ToMat3()
                        )
                    }
                    i++
                }
            }
        }
        UpdateAnimation()
        if ((thinkFlags and TH_UPDATEVISUALS) != 0) {
            Present()
            LinkCombat()
        }
    }

    companion object {
        val Type = idTypeInfo("idAFEntity_VehicleSixWheels", "idAFEntity_Vehicle") { idAFEntity_VehicleSixWheels() }

        private val steeringHingeKeys: Array<String> = arrayOf(
            "steeringHingeFrontLeft",
            "steeringHingeFrontRight",
            "steeringHingeRearLeft",
            "steeringHingeRearRight"
        )
        private val wheelBodyKeys: Array<String> = arrayOf(
            "wheelBodyFrontLeft",
            "wheelBodyFrontRight",
            "wheelBodyMiddleLeft",
            "wheelBodyMiddleRight",
            "wheelBodyRearLeft",
            "wheelBodyRearRight"
        )
        private val wheelJointKeys: Array<String> = arrayOf(
            "wheelJointFrontLeft",
            "wheelJointFrontRight",
            "wheelJointMiddleLeft",
            "wheelJointMiddleRight",
            "wheelJointRearLeft",
            "wheelJointRearRight"
        )
    }

    // public:
    // CLASS_PROTOTYPE( idAFEntity_VehicleSixWheels );
    init {
        var i = 0
        while (i < 6) {
            wheels[i] = null
            wheelJoints[i] = Model.INVALID_JOINT
            wheelAngles[i] = 0.0f
            i++
        }
        steering[0] = null
        steering[1] = null
        steering[2] = null
        steering[3] = null
    }

    override fun GetType(): idTypeInfo = Type
    override fun CreateInstance(): idClass = idAFEntity_VehicleSixWheels()
}

/*
 ===============================================================================
 idAFEntity_SteamPipe
 ===============================================================================
 */
class idAFEntity_SteamPipe : idAFEntity_Base() {
    companion object {
        val Type = idTypeInfo("idAFEntity_SteamPipe", "idAFEntity_Base") { idAFEntity_SteamPipe() }
    }

    override fun GetType(): idTypeInfo = Type
    override fun CreateInstance(): idClass = idAFEntity_SteamPipe()

    // CLASS_PROTOTYPE( idAFEntity_SteamPipe );
    private val force: idForce_Constant = idForce_Constant()
    private var steamBody = 0
    private var steamForce = 0.0f
    private var   /*qhandle_t*/steamModelDefHandle: Int
    private var steamRenderEntity: renderEntity_s
    private var steamUpForce = 0.0f

    // ~idAFEntity_SteamPipe();
    // FIX: Added missing destructor - C++ frees steamModelDefHandle entity def
    override fun _deconstructor() {
        if (steamModelDefHandle >= 0) {
            Game_local.gameRenderWorld!!.FreeEntityDef(steamModelDefHandle)
        }
        super._deconstructor()
    }

    override fun Spawn() {
        super.Spawn()
        val steamDir = idVec3()
        val steamBodyName: String?
        LoadAF()
        SetCombatModel()
        SetPhysics(af.GetPhysics())
        fl.takedamage = true
        steamBodyName = spawnArgs.GetString("steamBody", "")!!
        steamForce = spawnArgs.GetFloat("steamForce", "2000")
        steamUpForce = spawnArgs.GetFloat("steamUpForce", "10")
        steamDir.set(af.GetPhysics().GetAxis(steamBody)[2]) //[2];
        steamBody = af.GetPhysics().GetBodyId(steamBodyName)
        force.SetPosition(af.GetPhysics(), steamBody, af.GetPhysics().GetOrigin(steamBody))
        force.SetForce(steamDir.times(-steamForce))
        InitSteamRenderEntity()
        BecomeActive(TH_THINK)
    }

    override fun Restore(savefile: idRestoreGame) {
        super.Restore(savefile)
        Spawn()
    }

    override fun Think() {
        val steamDir = idVec3()
        if ((thinkFlags and TH_THINK) != 0) {
            steamDir.x = Game_local.gameLocal.random.CRandomFloat() * steamForce
            steamDir.y = Game_local.gameLocal.random.CRandomFloat() * steamForce
            steamDir.z = steamUpForce
            force.SetForce(steamDir)
            force.Evaluate(Game_local.gameLocal.time)
            //gameRenderWorld!!.DebugArrow( colorWhite, af.GetPhysics().GetOrigin( steamBody ), af.GetPhysics().GetOrigin( steamBody ) - 10 * steamDir, 4 );
        }
        if (steamModelDefHandle >= 0) {
            steamRenderEntity.origin.set(af.GetPhysics().GetOrigin(steamBody))
            steamRenderEntity.axis.set(af.GetPhysics().GetAxis(steamBody))
            Game_local.gameRenderWorld!!.UpdateEntityDef(steamModelDefHandle, steamRenderEntity)
        }
        super.Think()
    }

    private fun InitSteamRenderEntity() {
        val temp: String?
        val modelDef: idDeclModelDef?

//	memset( steamRenderEntity, 0, sizeof( steamRenderEntity ) );
        steamRenderEntity = renderEntity_s()
        steamRenderEntity.shaderParms[RenderWorld.SHADERPARM_RED] = 1.0f
        steamRenderEntity.shaderParms[RenderWorld.SHADERPARM_GREEN] = 1.0f
        steamRenderEntity.shaderParms[RenderWorld.SHADERPARM_BLUE] = 1.0f
        //            modelDef = null;
        temp = spawnArgs.GetString("model_steam")
        if (!temp.isEmpty()) { // != '\0' ) {
//		if ( !strstr( temp, "." ) ) {
            if (!temp.contains(".")) {
                modelDef =
                    DeclManager.declManager.FindType(declType_t.DECL_MODELDEF, temp, false) as idDeclModelDef?
                if (modelDef != null) {
                    steamRenderEntity.hModel = modelDef.ModelHandle()
                }
            }
            if (null == steamRenderEntity.hModel) {
                steamRenderEntity.hModel = ModelManager.renderModelManager.FindModel(temp)
            }
            if (steamRenderEntity.hModel != null) {
                steamRenderEntity.bounds.set(steamRenderEntity.hModel!!.Bounds(steamRenderEntity))
            } else {
                steamRenderEntity.bounds.Zero()
            }
            steamRenderEntity.origin.set(af.GetPhysics().GetOrigin(steamBody))
            steamRenderEntity.axis.set(af.GetPhysics().GetAxis(steamBody))
            steamModelDefHandle = Game_local.gameRenderWorld!!.AddEntityDef(steamRenderEntity)
        }
    }

    //
    //
    init {
        steamModelDefHandle = -1
        //	memset( &steamRenderEntity, 0, sizeof( steamRenderEntity ) );
        steamRenderEntity = renderEntity_s()
    }
}

/*
 ===============================================================================
 idAFEntity_ClawFourFingers
 ===============================================================================
 */
class idAFEntity_ClawFourFingers : idAFEntity_Base() {
    companion object {
        val Type = idTypeInfo("idAFEntity_ClawFourFingers", "idAFEntity_Base") { idAFEntity_ClawFourFingers() }

        // public:
        // CLASS_PROTOTYPE( idAFEntity_ClawFourFingers );
        private val eventCallbacks: MutableMap<idEventDef, eventCallback_t<*>> = HashMap()
        fun getEventCallBacks(): MutableMap<idEventDef, eventCallback_t<*>> {
            return eventCallbacks
        }

        init {
            eventCallbacks.putAll(idAFEntity_Base.getEventCallBacks())
            eventCallbacks[EV_SetFingerAngle] =
                eventCallback_t1<idAFEntity_ClawFourFingers> { obj: idAFEntity_ClawFourFingers, angle: idEventArg<*>? ->
                    obj.Event_SetFingerAngle(angle as idEventArg<Float>)
                }
            eventCallbacks[EV_StopFingers] =
                eventCallback_t0<idAFEntity_ClawFourFingers> { obj: idAFEntity_ClawFourFingers -> obj.Event_StopFingers() }
        }
    }

    //
    //
    private val fingers = arrayOfNulls<idAFConstraint_Hinge?>(4)
    override fun Spawn() {
        super.Spawn()
        var i: Int
        LoadAF()
        SetCombatModel()
        af.GetPhysics().LockWorldConstraints(true)
        af.GetPhysics().SetForcePushable(true)
        SetPhysics(af.GetPhysics())
        fl.takedamage = true
        i = 0
        while (i < 4) {
            fingers[i] = af.GetPhysics().GetConstraint(clawConstraintNames[i]) as idAFConstraint_Hinge
            if (fingers[i] == null) {
                idGameLocal.Error(
                    "idClaw_FourFingers '%s': can't find claw constraint '%s'",
                    name,
                    clawConstraintNames[i]
                )
            }
            i++
        }
    }

    override fun Save(savefile: idSaveGame) {
        super.Save(savefile)
        var i: Int
        i = 0
        while (i < 4) {
            fingers[i]!!.Save(savefile)
            i++
        }
    }

    //
    //
    override fun Restore(savefile: idRestoreGame) {
        super.Restore(savefile)
        var i: Int
        i = 0
        while (i < 4) {
            fingers[i] = af.GetPhysics().GetConstraint(clawConstraintNames[i]) as idAFConstraint_Hinge
            fingers[i]!!.Restore(savefile)
            i++
        }
        SetCombatModel()
        LinkCombat()
    }

    private fun Event_SetFingerAngle(angle: idEventArg<Float>) {
        var i: Int
        i = 0
        while (i < 4) {
            fingers[i]!!.SetSteerAngle(angle.value)
            fingers[i]!!.SetSteerSpeed(0.5f)
            i++
        }
        af.GetPhysics().Activate()
    }

    private fun Event_StopFingers() {
        var i: Int
        i = 0
        while (i < 4) {
            fingers[i]!!.SetSteerAngle(fingers[i]!!.GetAngle())
            i++
        }
    }

    override fun GetType(): idTypeInfo = Type
    override fun CreateInstance(): idClass = idAFEntity_ClawFourFingers()

    override fun getEventCallBack(event: idEventDef): eventCallback_t<*>? {
        return eventCallbacks[event]
    }

    init {
        fingers[0] = null
        fingers[1] = null
        fingers[2] = null
        fingers[3] = null
    }
}

/*
 ===============================================================================

 editor support routines

 ===============================================================================
 */
/*
 ================
 GetJointTransform
 ================
 */
class jointTransformData_t {
    var ent: renderEntity_s? = null
    var joints: Array<idMD5Joint>? = null
}

internal class GetJointTransform private constructor() : getJointTransform_t() {
    override fun run(
        model: Any,
        frame: Array<idJointMat>,
        jointName: idStr,
        origin: idVec3,
        axis: idMat3
    ): Boolean {
        var i = 0
        val data = model as jointTransformData_t

        while (i < data!!.ent!!.numJoints) {
            if (data.joints!![i].name!!.Icmp(jointName) == 0) {
                break
            }
            i++
        }
        if (i >= data.ent!!.numJoints) {
            return false
        }
        origin.set(frame[i].ToVec3())
        axis.set(frame[i].ToMat3())
        return true
    }

    companion object {
        val INSTANCE: getJointTransform_t = GetJointTransform()
    }
}

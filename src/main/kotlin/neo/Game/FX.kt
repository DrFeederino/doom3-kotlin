/*
 * Copyright (C) 1999-2011 id Software LLC, a ZeniMax Media company.
 * Translated to Kotlin by Dr. Feederino with support of Claude Code
 *
 * This file is part of the Doom 3 Kotlin project.
 * Original source: neo/Game/FX.cpp, neo/Game/FX.h
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

import neo.Game.GameSys.Class.*
import neo.Game.GameSys.EV_Remove
import neo.Game.GameSys.Event.idEventDef
import neo.Game.GameSys.SaveGame.idRestoreGame
import neo.Game.GameSys.SaveGame.idSaveGame
import neo.Game.GameSys.SysCvar
import neo.Game.Game_local.gameSoundChannel_t
import neo.Game.Game_local.idGameLocal
import neo.Game.Projectile.idProjectile
import neo.Renderer.ModelManager
import neo.Renderer.RenderWorld
import neo.Renderer.RenderWorld.renderEntity_s
import neo.Renderer.RenderWorld.renderLight_s
import neo.framework.DeclFX.*
import neo.framework.DeclManager
import neo.framework.DeclManager.declType_t
import neo.idlib.BitMsg.idBitMsgDelta
import neo.idlib.Dict_h.idDict
import neo.idlib.Text.Str.idStr
import neo.idlib.containers.CBool
import neo.idlib.containers.CInt
import neo.idlib.containers.List
import neo.idlib.math.*
import neo.idlib.math.Matrix.idMat3

val EV_Fx_Action: idEventDef = idEventDef("_fxAction", "e") // implemented by subclasses
val EV_Fx_KillFx: idEventDef = idEventDef("_killfx")


/*
 ===============================================================================

 idEntityFx

 ===============================================================================
 */

/*
 ===============================================================================

 Special effects.

 ===============================================================================
 */
class idFXLocalAction {
    var decalDropped = false
    var delay = 0.0f
    var launched = false
    var   /*qhandle_t*/lightDefHandle // handle to renderer light def
            = 0
    var modelDefHandle // handle to static renderer model
            = 0
    var particleSystem = 0
    var renderEntity // used to present a model to the renderer
            : renderEntity_s = renderEntity_s()
    var renderLight // light presented to the renderer
            : renderLight_s = renderLight_s()
    var shakeStarted = false
    var soundStarted = false
    var start = 0
}

open class idEntityFx : idEntity() {
    companion object {
        val Type = idTypeInfo("idEntityFx", "idEntity") { idEntityFx() }

        private val eventCallbacks: MutableMap<idEventDef, eventCallback_t<*>> = HashMap()

        //	virtual					~idEntityFx();
        fun StartFx(fx: String?, useOrigin: idVec3?, useAxis: idMat3?, ent: idEntity?, bind: Boolean): idEntityFx? {
            if (SysCvar.g_skipFX.GetBool() || null == fx || fx.isEmpty()) {
                return null
            }
            val args = idDict()
            args.SetBool("start", true)
            args.Set("fx", fx)
            val nfx = Game_local.gameLocal.SpawnEntityType(idEntityFx.Type, args) as idEntityFx
            if (nfx.Joint() != null && !nfx.Joint()!!.isEmpty()) {
                nfx.BindToJoint(ent!!, nfx.Joint()!!, true)
                nfx.SetOrigin(vec3_origin)
            } else {
                nfx.SetOrigin(if (useOrigin != null) useOrigin else ent!!.GetPhysics().GetOrigin())
                nfx.SetAxis(if (useAxis != null) useAxis else ent!!.GetPhysics().GetAxis())
            }
            if (bind) {
                // never bind to world spawn
                if (ent !== Game_local.gameLocal.world) {
                    nfx.Bind(ent, true)
                }
            }
            nfx.Show()
            return nfx
        }

        fun StartFx(fx: idStr, useOrigin: idVec3?, useAxis: idMat3?, ent: idEntity?, bind: Boolean): idEntityFx? {
            return StartFx(fx.toString(), useOrigin, useAxis, ent, bind)
        }

        fun getEventCallBacks(): MutableMap<idEventDef, eventCallback_t<*>> {
            return eventCallbacks
        }

        init {
            eventCallbacks.putAll(idEntity.getEventCallBacks())
            eventCallbacks[EV_Activate] =
                eventCallback_t1<idEntityFx> { obj: idEntityFx, activator: idEventArg<*>? ->
                    obj.Event_Trigger(
                        activator as idEventArg<idEntity>
                    )
                }
            eventCallbacks[EV_Fx_KillFx] =
                eventCallback_t0<idEntityFx> { obj: idEntityFx -> obj.Event_ClearFx() }
        }
    }

    protected val actions: List.idList<idFXLocalAction>
    protected var fxEffect // GetFX() should be called before using fxEffect as a pointer
            : idDeclFX? = null
    protected var nextTriggerTime: Int
    protected var started: Int
    protected var systemName: idStr
    override fun Spawn() {
        super.Spawn()
        if (SysCvar.g_skipFX.GetBool()) {
            return
        }
        val fx = arrayOfNulls<String>(1)
        nextTriggerTime = 0
        fxEffect = null
        if (spawnArgs.GetString("fx", "", fx)) {
            systemName.set(fx[0])
        }
        if (!spawnArgs.GetBool("triggered")) {
            Setup(fx[0]!!)
            if (spawnArgs.GetBool("test") || spawnArgs.GetBool("start") || spawnArgs.GetFloat("restart") != 0.0f) {
                PostEventMS(EV_Activate, 0, this)
            }
        }
    }

    override fun Save(savefile: idSaveGame) {
        super.Save(savefile)
        var i: Int
        savefile.WriteInt(started)
        savefile.WriteInt(nextTriggerTime)
        savefile.WriteFX(fxEffect)
        savefile.WriteString(systemName)
        savefile.WriteInt(actions.Num())
        i = 0
        while (i < actions.Num()) {
            if (actions[i].lightDefHandle >= 0) {
                savefile.WriteBool(true)
                savefile.WriteRenderLight(actions[i].renderLight)
            } else {
                savefile.WriteBool(false)
            }
            if (actions[i].modelDefHandle >= 0) {
                savefile.WriteBool(true)
                savefile.WriteRenderEntity(actions[i].renderEntity)
            } else {
                savefile.WriteBool(false)
            }
            savefile.WriteFloat(actions[i].delay)
            savefile.WriteInt(actions[i].start)
            savefile.WriteBool(actions[i].soundStarted)
            savefile.WriteBool(actions[i].shakeStarted)
            savefile.WriteBool(actions[i].decalDropped)
            savefile.WriteBool(actions[i].launched)
            i++
        }
    }

    override fun Restore(savefile: idRestoreGame) {
        super.Restore(savefile)
        var i: Int
        val num = CInt()
        val hasObject = CBool(false)
        started = savefile.ReadInt()
        nextTriggerTime = savefile.ReadInt()
        // Inline ReadFX logic: C++ ReadFX sets the pointer directly, but Kotlin's
        // ReadFX requires a non-null object and calls oSet() which is unimplemented.
        val fxName = idStr()
        savefile.ReadString(fxName)
        if (fxName.Length() > 0) {
            fxEffect = DeclManager.declManager.FindType(declType_t.DECL_FX, fxName) as? idDeclFX
        } else {
            fxEffect = null
        }
        savefile.ReadString(systemName)
        savefile.ReadInt(num)
        actions.SetNum(num.integerValue)
        i = 0
        while (i < num.integerValue) {
            savefile.ReadBool(hasObject)
            if (hasObject._val) {
                savefile.ReadRenderLight(actions[i].renderLight)
                actions[i].lightDefHandle = Game_local.gameRenderWorld!!.AddLightDef(actions[i].renderLight)
            } else {
//			memset( actions.oGet(i).renderLight, 0, sizeof( renderLight_t ) );
                actions[i].renderLight = renderLight_s()
                actions[i].lightDefHandle = -1
            }
            savefile.ReadBool(hasObject)
            if (hasObject._val) {
                actions[i].renderEntity = savefile.ReadRenderEntity()
                actions[i].modelDefHandle =
                    Game_local.gameRenderWorld!!.AddEntityDef(actions[i].renderEntity)
            } else {
//			memset( &actions[i].renderEntity, 0, sizeof( renderEntity_t ) );
                actions[i].renderEntity = renderEntity_s()
                actions[i].modelDefHandle = -1
            }
            actions[i].delay = savefile.ReadFloat()

            // let the FX regenerate the particleSystem
            actions[i].particleSystem = -1
            actions[i].start = savefile.ReadInt()
            actions[i].soundStarted = savefile.ReadBool()
            actions[i].shakeStarted = savefile.ReadBool()
            actions[i].decalDropped = savefile.ReadBool()
            actions[i].launched = savefile.ReadBool()
            i++
        }
    }

    /*
     ================
     idEntityFx::Think

     Clears any visual fx started when {item,mob,player} was spawned
     ================
     */
    override fun Think() {
        if (SysCvar.g_skipFX.GetBool()) {
            return
        }
        if ((thinkFlags and TH_THINK) != 0) {
            Run(Game_local.gameLocal.time)
        }
        RunPhysics()
        Present()
    }

    fun Setup(fx: String) {
        if (started >= 0) {
            return  // already started
        }

        // early during MP Spawn() with no information. wait till we ReadFromSnapshot for more
        if (Game_local.gameLocal.isClient && (null == fx || fx.isEmpty())) { //[0] == '\0' ) ) {
            return
        }
        systemName.set(fx)
        started = 0
        fxEffect = DeclManager.declManager.FindType(declType_t.DECL_FX, systemName) as idDeclFX
        if (fxEffect != null) {
            val localAction = idFXLocalAction()

//		memset( &localAction, 0, sizeof( idFXLocalAction ) );

//		memset( &localAction, 0, sizeof( idFXLocalAction ) );
            actions.AssureSize(fxEffect!!.events.Num(), localAction)
            for (i in 0 until fxEffect!!.events.Num()) {
                // Each action needs its own independent object (AssureSize shares one reference)
                actions[i] = idFXLocalAction()
                val fxaction = fxEffect!!.events[i]
                val laction = actions[i]
                if (fxaction.random1 != 0.0f || fxaction.random2 != 0.0f) {
                    laction.delay =
                        fxaction.random1 + Game_local.gameLocal.random.RandomFloat() * (fxaction.random2 - fxaction.random1)
                } else {
                    laction.delay = fxaction.delay
                }
                laction.start = -1
                laction.lightDefHandle = -1
                laction.modelDefHandle = -1
                laction.particleSystem = -1
                laction.shakeStarted = false
                laction.decalDropped = false
                laction.launched = false
            }
        }
    }

    fun Run(time: Int) {
        var ieff: Int
        var j: Int
        val ent = arrayOfNulls<idEntity>(1)
        var projectileDef: idDict?
        var projectile: idProjectile?
        if (fxEffect == null) {
            return
        }
        ieff = 0
        while (ieff < fxEffect!!.events.Num()) {
            val fxaction = fxEffect!!.events[ieff]
            val laction = actions[ieff]

            //
            // if we're currently done with this one
            //
            if (laction.start == -1) {
                ieff++
                continue
            }

            //
            // see if it's delayed
            //
            if (laction.delay != 0.0f) {
                if (laction.start + (time - laction.start) < laction.start + laction.delay * 1000) {
                    ieff++
                    continue
                }
            }

            //
            // each event can have it's own delay and restart
            //
            val actualStart =
                if (laction.delay != 0.0f) laction.start + (laction.delay * 1000).toInt() else laction.start
            val pct = (time - actualStart).toFloat() / (1000 * fxaction.duration)
            if (pct >= 1.0f) {
                laction.start = -1
                var totalDelay: Float
                if (fxaction.restart != 0.0f) {
                    totalDelay = if (fxaction.random1 != 0.0f || fxaction.random2 != 0.0f) {
                        fxaction.random1 + Game_local.gameLocal.random.RandomFloat() * (fxaction.random2 - fxaction.random1)
                    } else {
                        fxaction.delay
                    }
                    laction.delay = totalDelay
                    laction.start = time
                }
                ieff++
                continue
            }
            if (fxaction.fire.Length() != 0) {
                j = 0
                while (j < fxEffect!!.events.Num()) {
                    if (fxEffect!!.events[j].name.Icmp(fxaction.fire) == 0) {
                        actions[j].delay = 0.0f
                    }
                    j++
                }
            }
            var useAction: idFXLocalAction?
            useAction = if (fxaction.sibling == -1) {
                laction
            } else {
                actions[fxaction.sibling]
            }
            assert(useAction != null)
            when (fxaction.type) {
                fx_enum.FX_ATTACHLIGHT, fx_enum.FX_LIGHT -> {
                    if (useAction.lightDefHandle == -1) {
                        if (fxaction.type == fx_enum.FX_LIGHT) {
                            useAction.renderLight =
                                renderLight_s() //memset( &useAction.renderLight, 0, sizeof( renderLight_t ) );
                            useAction.renderLight.origin.set(GetPhysics().GetOrigin().plus(fxaction.offset))
                            useAction.renderLight.axis.set(GetPhysics().GetAxis())
                            useAction.renderLight.lightRadius[0] = fxaction.lightRadius
                            useAction.renderLight.lightRadius[1] = fxaction.lightRadius
                            useAction.renderLight.lightRadius[2] = fxaction.lightRadius
                            useAction.renderLight.shader =
                                DeclManager.declManager.FindMaterial(fxaction.data, false)
                            useAction.renderLight.shaderParms[RenderWorld.SHADERPARM_RED] = fxaction.lightColor.x
                            useAction.renderLight.shaderParms[RenderWorld.SHADERPARM_GREEN] = fxaction.lightColor.y
                            useAction.renderLight.shaderParms[RenderWorld.SHADERPARM_BLUE] = fxaction.lightColor.z
                            useAction.renderLight.shaderParms[RenderWorld.SHADERPARM_TIMESCALE] = 1.0f
                            useAction.renderLight.shaderParms[RenderWorld.SHADERPARM_TIMEOFFSET] =
                                -MS2SEC(time.toFloat())
                            useAction.renderLight.referenceSound = refSound.referenceSound
                            useAction.renderLight.pointLight._val = true
                            if (fxaction.noshadows) {
                                useAction.renderLight.noShadows._val = true
                            }
                            useAction.lightDefHandle =
                                Game_local.gameRenderWorld!!.AddLightDef(useAction.renderLight)
                        }
                        if (fxaction.noshadows) {
                            j = 0
                            while (j < fxEffect!!.events.Num()) {
                                val laction2 = actions[j]
                                if (laction2.modelDefHandle != -1) {
                                    laction2.renderEntity.noShadow = true
                                }
                                j++
                            }
                        }
                    }
                    ApplyFade(fxaction, useAction, time, actualStart)
                }

                fx_enum.FX_SOUND -> {
                    if (!useAction.soundStarted) {
                        useAction.soundStarted = true
                        val shader = DeclManager.declManager.FindSound(fxaction.data)
                        StartSoundShader(shader, gameSoundChannel_t.SND_CHANNEL_ANY.ordinal, 0, false)
                        j = 0
                        while (j < fxEffect!!.events.Num()) {
                            val laction2 = actions[j]
                            if (laction2.lightDefHandle != -1) {
                                laction2.renderLight.referenceSound = refSound.referenceSound
                                Game_local.gameRenderWorld!!.UpdateLightDef(
                                    laction2.lightDefHandle,
                                    laction2.renderLight
                                )
                            }
                            j++
                        }
                    }
                }

                fx_enum.FX_DECAL -> {
                    if (!useAction.decalDropped) {
                        useAction.decalDropped = true
                        Game_local.gameLocal.ProjectDecal(
                            GetPhysics().GetOrigin(),
                            GetPhysics().GetGravity(),
                            8.0f,
                            true,
                            fxaction.size,
                            fxaction.data.toString()
                        )
                    }
                }

                fx_enum.FX_SHAKE -> {
                    if (!useAction.shakeStarted) {
                        val args = idDict()
                        args.Clear()
                        args.SetFloat("kick_time", fxaction.shakeTime)
                        args.SetFloat("kick_amplitude", fxaction.shakeAmplitude)
                        j = 0
                        while (j < Game_local.gameLocal.numClients) {
                            val player = Game_local.gameLocal.GetClientByNum(j)
                            if (player != null && player.GetPhysics().GetOrigin().minus(GetPhysics().GetOrigin())
                                    .LengthSqr() < Square(fxaction.shakeDistance)
                            ) {
                                if (!Game_local.gameLocal.isMultiplayer || !fxaction.shakeIgnoreMaster || GetBindMaster() !== player) {
                                    player.playerView.DamageImpulse(fxaction.offset, args)
                                }
                            }
                            j++
                        }
                        if (fxaction.shakeImpulse != 0.0f && fxaction.shakeDistance != 0.0f) {
                            var ignore_ent: idEntity? = null
                            if (Game_local.gameLocal.isMultiplayer) {
                                ignore_ent = this
                                if (fxaction.shakeIgnoreMaster) {
                                    ignore_ent = GetBindMaster()
                                }
                            }
                            // lookup the ent we are bound to?
                            Game_local.gameLocal.RadiusPush(
                                GetPhysics().GetOrigin(),
                                fxaction.shakeDistance,
                                fxaction.shakeImpulse,
                                this,
                                ignore_ent,
                                1.0f,
                                true
                            )
                        }
                        useAction.shakeStarted = true
                    }
                }

                fx_enum.FX_ATTACHENTITY, fx_enum.FX_PARTICLE, fx_enum.FX_MODEL -> {
                    if (useAction.modelDefHandle == -1) {
//					memset( &useAction.renderEntity, 0, sizeof( renderEntity_t ) );
                        useAction.renderEntity = renderEntity_s()
                        useAction.renderEntity.origin.set(GetPhysics().GetOrigin().plus(fxaction.offset))
                        useAction.renderEntity.axis.set(if (fxaction.explicitAxis) fxaction.axis else GetPhysics().GetAxis())
                        useAction.renderEntity.hModel =
                            ModelManager.renderModelManager.FindModel(fxaction.data.toString())
                        useAction.renderEntity.shaderParms[RenderWorld.SHADERPARM_RED] = 1.0f
                        useAction.renderEntity.shaderParms[RenderWorld.SHADERPARM_GREEN] = 1.0f
                        useAction.renderEntity.shaderParms[RenderWorld.SHADERPARM_BLUE] = 1.0f
                        useAction.renderEntity.shaderParms[RenderWorld.SHADERPARM_TIMEOFFSET] =
                            -MS2SEC(time.toFloat())
                        useAction.renderEntity.shaderParms[3] = 1.0f
                        useAction.renderEntity.shaderParms[5] = 0.0f
                        if (useAction.renderEntity.hModel != null) {
                            useAction.renderEntity.bounds.set(useAction.renderEntity.hModel!!.Bounds(useAction.renderEntity))
                        }
                        useAction.modelDefHandle = Game_local.gameRenderWorld!!.AddEntityDef(useAction.renderEntity)
                    } else if (fxaction.trackOrigin) {
                        useAction.renderEntity.origin.set(GetPhysics().GetOrigin().plus(fxaction.offset))
                        useAction.renderEntity.axis.set(if (fxaction.explicitAxis) fxaction.axis else GetPhysics().GetAxis())
                    }
                    ApplyFade(fxaction, useAction, time, actualStart)
                }

                fx_enum.FX_LAUNCH -> {
                    if (Game_local.gameLocal.isClient) {
                        // client never spawns entities outside of ClientReadSnapshot
                        useAction.launched = true
                    } else if (!useAction.launched) {
                        useAction.launched = true
                        projectile = null
                        // FIXME: may need to cache this if it is slow
                        projectileDef = Game_local.gameLocal.FindEntityDefDict(fxaction.data.toString(), false)
                        if (null == projectileDef) {
                            Game_local.gameLocal.Warning("projectile '%s' not found", fxaction.data)
                        } else {
                            Game_local.gameLocal.SpawnEntityDef(projectileDef, ent, false)
                            if (ent.isNotEmpty() && ent[0] is idProjectile) {
                                projectile = ent[0] as idProjectile
                                projectile.Create(this, GetPhysics().GetOrigin(), GetPhysics().GetAxis()[0])
                                projectile.Launch(
                                    GetPhysics().GetOrigin(),
                                    GetPhysics().GetAxis()[0],
                                    vec3_origin
                                )
                            }
                        }
                    }
                }

                else -> {}
            }
            ieff++
        }
    }

    fun Start(time: Int) {
        if (fxEffect == null) {
            return
        }
        started = time
        for (i in 0 until fxEffect!!.events.Num()) {
            val laction = actions[i]
            laction.start = time
            laction.soundStarted = false
            laction.shakeStarted = false
            laction.particleSystem = -1
            laction.decalDropped = false
            laction.launched = false
        }
    }

    fun Stop() {
        CleanUp()
        started = -1
    }

    fun Duration(): Int {
        var max = 0
        if (fxEffect == null) {
            return max
        }
        for (i in 0 until fxEffect!!.events.Num()) {
            val fxaction = fxEffect!!.events[i]
            val d = ((fxaction.delay + fxaction.duration) * 1000.0f).toInt()
            if (d > max) {
                max = d
            }
        }
        return max
    }

    fun EffectName(): String? {
        return if (fxEffect != null) fxEffect!!.GetName() else null
    }

    fun Joint(): String? {
        return if (fxEffect != null) fxEffect!!.joint.toString() else null
    }

    fun Done(): Boolean {
        return started > 0 && Game_local.gameLocal.time > started + Duration()
    }

    override fun WriteToSnapshot(msg: idBitMsgDelta) {
        GetPhysics().WriteToSnapshot(msg)
        WriteBindToSnapshot(msg)
        msg.WriteLong(
            if (fxEffect != null) Game_local.gameLocal.ServerRemapDecl(
                -1,
                declType_t.DECL_FX,
                fxEffect!!.Index()
            ) else -1
        )
        msg.WriteLong(started)
    }

    override fun ReadFromSnapshot(msg: idBitMsgDelta) {
        val fx_index: Int
        val start_time: Int
        val max_lapse = CInt()
        GetPhysics().ReadFromSnapshot(msg)
        ReadBindFromSnapshot(msg)
        fx_index = Game_local.gameLocal.ClientRemapDecl(declType_t.DECL_FX, msg.ReadLong())
        start_time = msg.ReadLong()
        if (fx_index != -1 && start_time > 0 && fxEffect == null && started < 0) {
            spawnArgs.GetInt("effect_lapse", "1000", max_lapse)
            if (Game_local.gameLocal.time - start_time > max_lapse.integerValue) {
                // too late, skip the effect completely
                started = 0
                return
            }
            val fx = DeclManager.declManager.DeclByIndex(declType_t.DECL_FX, fx_index) as? idDeclFX
            if (null == fx) {
                idGameLocal.Error("FX at index %d not found", fx_index)
                return
            }
            fxEffect = fx
            Setup(fx.GetName())
            Start(start_time)
        }
    }

    override fun ClientPredictionThink() {
        if (Game_local.gameLocal.isNewFrame) {
            Run(Game_local.gameLocal.time)
        }
        RunPhysics()
        Present()
    }

    protected fun Event_Trigger(activator: idEventArg<idEntity>) {
        if (SysCvar.g_skipFX.GetBool()) {
            return
        }
        val fxActionDelay: Float
        val fx = arrayOfNulls<String>(1)
        if (Game_local.gameLocal.time < nextTriggerTime) {
            return
        }
        if (spawnArgs.GetString("fx", "", fx)) {
            Setup(fx[0]!!)
            Start(Game_local.gameLocal.time)
            PostEventMS(EV_Fx_KillFx, Duration())
            BecomeActive(TH_THINK)
        }
        fxActionDelay = spawnArgs.GetFloat("fxActionDelay")
        nextTriggerTime = if (fxActionDelay != 0.0f) {
            (Game_local.gameLocal.time + SEC2MS(fxActionDelay))
        } else {
            // prevent multiple triggers on same frame
            Game_local.gameLocal.time + 1
        }
        PostEventSec(EV_Fx_Action, fxActionDelay, activator.value)
    }

    /*
     ================
     idEntityFx::Event_ClearFx

     Clears any visual fx started when item(mob) was spawned
     ================
     */
    protected fun Event_ClearFx() {
        if (SysCvar.g_skipFX.GetBool()) {
            return
        }
        Stop()
        CleanUp()
        BecomeInactive(TH_THINK)
        if (spawnArgs.GetBool("test")) {
            PostEventMS(EV_Activate, 0, this)
        } else {
            if (spawnArgs.GetFloat("restart") != 0.0f || !spawnArgs.GetBool("triggered")) {
                var rest = spawnArgs.GetFloat("restart", "0")
                if (rest == 0.0f) {
                    PostEventSec(EV_Remove, 0.1f)
                } else {
                    rest *= Game_local.gameLocal.random.RandomFloat()
                    PostEventSec(EV_Activate, rest, this)
                }
            }
        }
    }

    protected fun CleanUp() {
        if (null == fxEffect) {
            return
        }
        for (i in 0 until fxEffect!!.events.Num()) {
            val fxaction = fxEffect!!.events[i]
            val laction = actions[i]
            CleanUpSingleAction(fxaction, laction)
        }
    }

    protected fun CleanUpSingleAction(fxaction: idFXSingleAction, laction: idFXLocalAction) {
        if (laction.lightDefHandle != -1 && fxaction.sibling == -1 && fxaction.type != fx_enum.FX_ATTACHLIGHT) {
            Game_local.gameRenderWorld!!.FreeLightDef(laction.lightDefHandle)
            laction.lightDefHandle = -1
        }
        if (laction.modelDefHandle != -1 && fxaction.sibling == -1 && fxaction.type != fx_enum.FX_ATTACHENTITY) {
            Game_local.gameRenderWorld!!.FreeEntityDef(laction.modelDefHandle)
            laction.modelDefHandle = -1
        }
        laction.start = -1
    }

    protected fun ApplyFade(fxaction: idFXSingleAction, laction: idFXLocalAction, time: Int, actualStart: Int) {
        if (fxaction.fadeInTime != 0.0f || fxaction.fadeOutTime != 0.0f) {
            var fadePct =
                (time - actualStart).toFloat() / (1000.0f * if (fxaction.fadeInTime != 0.0f) fxaction.fadeInTime else fxaction.fadeOutTime)
            if (fadePct > 1.0f) {
                fadePct = 1.0f
            }
            if (laction.modelDefHandle != -1) {
                laction.renderEntity.shaderParms[RenderWorld.SHADERPARM_RED] =
                    if (fxaction.fadeInTime != 0.0f) fadePct else 1.0f - fadePct
                laction.renderEntity.shaderParms[RenderWorld.SHADERPARM_GREEN] =
                    if (fxaction.fadeInTime != 0.0f) fadePct else 1.0f - fadePct
                laction.renderEntity.shaderParms[RenderWorld.SHADERPARM_BLUE] =
                    if (fxaction.fadeInTime != 0.0f) fadePct else 1.0f - fadePct
                Game_local.gameRenderWorld!!.UpdateEntityDef(laction.modelDefHandle, laction.renderEntity)
            }
            if (laction.lightDefHandle != -1) {
                laction.renderLight.shaderParms[RenderWorld.SHADERPARM_RED] =
                    fxaction.lightColor.x * if (fxaction.fadeInTime != 0.0f) fadePct else 1.0f - fadePct
                laction.renderLight.shaderParms[RenderWorld.SHADERPARM_GREEN] =
                    fxaction.lightColor.y * if (fxaction.fadeInTime != 0.0f) fadePct else 1.0f - fadePct
                laction.renderLight.shaderParms[RenderWorld.SHADERPARM_BLUE] =
                    fxaction.lightColor.z * if (fxaction.fadeInTime != 0.0f) fadePct else 1.0f - fadePct
                Game_local.gameRenderWorld!!.UpdateLightDef(laction.lightDefHandle, laction.renderLight)
            }
        }
    }

    override fun GetType(): idTypeInfo = Type
    override fun CreateInstance(): idClass = idEntityFx()

    override fun getEventCallBack(event: idEventDef): eventCallback_t<*>? {
        return eventCallbacks[event]
    }

    //
    //
    //        public 	CLASS_PROTOTYPE( idEntityFx );
    init {
        started = -1
        nextTriggerTime = -1
        fl.networkSync = true
        actions = List.idList()
        systemName = idStr()
    }
}

/*
 ===============================================================================

 idTeleporter

 ===============================================================================
 */
class idTeleporter : idEntityFx() {
    companion object {
        val Type = idTypeInfo("idTeleporter", "idEntityFx") { idTeleporter() }

        //        public 	CLASS_PROTOTYPE( idTeleporter );
        private val eventCallbacks: MutableMap<idEventDef, eventCallback_t<*>> = HashMap()
        fun getEventCallBacks(): MutableMap<idEventDef, eventCallback_t<*>> {
            return eventCallbacks
        }

        init {
            eventCallbacks.putAll(idEntityFx.getEventCallBacks())
            eventCallbacks[EV_Fx_Action] =
                eventCallback_t1<idTeleporter> { obj: idTeleporter, activator: idEventArg<*>? ->
                    obj.Event_DoAction(activator as idEventArg<idEntity>)
                }
        }
    }

    // teleporters to this location
    private fun Event_DoAction(activator: idEventArg<idEntity>) {
        val a = idAngles(0.0f, spawnArgs.GetFloat("angle"), 0.0f)
        activator.value.Teleport(GetPhysics().GetOrigin(), a, null)
    }

    override fun GetType(): idTypeInfo = Type
    override fun CreateInstance(): idClass = idTeleporter()

    override fun getEventCallBack(event: idEventDef): eventCallback_t<*>? {
        return eventCallbacks[event]
    }
}

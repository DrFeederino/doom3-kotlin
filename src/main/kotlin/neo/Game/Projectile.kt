/*
 * Copyright (C) 1999-2011 id Software LLC, a ZeniMax Media company.
 * Translated to Kotlin by Dr. Feederino with support of Claude Code
 *
 * This file is part of the Doom 3 Kotlin project.
 * Original source: neo/Game/Projectile.cpp, neo/Game/Projectile.h
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

import neo.Game.AI.idAI
import neo.Game.GameSys.Class.*
import neo.Game.GameSys.EV_Remove
import neo.Game.GameSys.Event.idEventDef
import neo.Game.GameSys.SaveGame.idRestoreGame
import neo.Game.GameSys.SaveGame.idSaveGame
import neo.Game.GameSys.SysCvar
import neo.Game.Game_local.*
import neo.Game.Mover.idDoor
import neo.Game.Physics.Clip
import neo.Game.Physics.Clip.idClipModel
import neo.Game.Physics.Force_Constant.idForce_Constant
import neo.Game.Physics.Physics_RigidBody
import neo.Game.Physics.Physics_RigidBody.idPhysics_RigidBody
import neo.Game.Player.idPlayer
import neo.Game.Script.Script_Thread.idThread
import neo.Renderer.Material
import neo.Renderer.Material.surfTypes_t
import neo.Renderer.Model
import neo.Renderer.ModelManager
import neo.Renderer.RenderWorld
import neo.Renderer.RenderWorld.renderEntity_s
import neo.Renderer.RenderWorld.renderLight_s
import neo.Sound.snd_shader.idSoundShader
import neo.TempDump
import neo.TempDump.SERiAL
import neo.cm.collisionModelManager
import neo.cm.trace_s
import neo.framework.DeclManager
import neo.framework.DeclManager.declType_t
import neo.framework.DeclParticle.idDeclParticle
import neo.framework.UsercmdGen
import neo.idlib.BV.idBounds
import neo.idlib.BitMsg.idBitMsg
import neo.idlib.BitMsg.idBitMsgDelta
import neo.idlib.Dict_h.idDict
import neo.idlib.LittleBitField
import neo.idlib.Text.Str
import neo.idlib.Text.Str.idStr
import neo.idlib.containers.CFloat
import neo.idlib.containers.CInt
import neo.idlib.containers.List
import neo.idlib.geometry.TraceModel.idTraceModel
import neo.idlib.idLib
import neo.idlib.math.*
import neo.idlib.math.Matrix.idMat3
import java.nio.ByteBuffer


//
val EV_Explode: idEventDef = idEventDef("<explode>", null)
val EV_Fizzle: idEventDef = idEventDef("<fizzle>", null)
val EV_GetProjectileState: idEventDef = idEventDef("getProjectileState", null, 'd')
val EV_RadiusDamage: idEventDef = idEventDef("<radiusdmg>", "e")

//
val EV_RemoveBeams: idEventDef = idEventDef("<removeBeams>", null)

object Projectile {
    /*
     ===============================================================================

     idProjectile
	
     ===============================================================================
     */
    const val BFG_DAMAGE_FREQUENCY = 333
    const val BOUNCE_SOUND_MAX_VELOCITY = 400.0f
    const val BOUNCE_SOUND_MIN_VELOCITY = 200.0f


    open class idProjectile : idEntity() {
        companion object {
            // enum {
            val EVENT_DAMAGE_EFFECT: Int = idEntity.EVENT_MAXEVENTS
            val EVENT_MAXEVENTS = EVENT_DAMAGE_EFFECT
            private val eventCallbacks: MutableMap<idEventDef, eventCallback_t<*>> = HashMap()
            fun GetVelocity(projectile: idDict): idVec3 {
                val velocity = idVec3()
                projectile.GetVector("velocity", "0 0 0", velocity)
                return velocity
            }

            fun GetGravity(projectile: idDict): idVec3 {
                val gravity: Float
                gravity = projectile.GetFloat("gravity")
                return idVec3(0.0f, 0.0f, -gravity)
            }

            fun DefaultDamageEffect(
                soundEnt: idEntity,
                projectileDef: idDict,
                collision: trace_s,
                velocity: idVec3
            ) {
                var decal: String?
                var sound: String?
                val typeName: String?
                val materialType: surfTypes_t?
                materialType = if (collision.c.material != null) {
                    collision.c.material!!.GetSurfaceType()
                } else {
                    surfTypes_t.SURFTYPE_METAL
                }

                // get material type name
                typeName = Game_local.gameLocal.sufaceTypeNames[materialType.ordinal]

                // play impact sound
                sound = projectileDef.GetString(Str.va("snd_%s", typeName))
                if (sound.isEmpty()) { // == '\0' ) {
                    sound = projectileDef.GetString("snd_metal")
                }
                if (sound.isEmpty()) { // == '\0' ) {
                    sound = projectileDef.GetString("snd_impact")
                }
                if (sound.isNotEmpty()) { // == '\0' ) {
                    soundEnt.StartSoundShader(
                        DeclManager.declManager.FindSound(sound),
                        gameSoundChannel_t.SND_CHANNEL_BODY.ordinal,
                        0,
                        false
                    )
                }

                // project decal
                decal = projectileDef.GetString(Str.va("mtr_detonate_%s", typeName))
                if (decal.isEmpty()) { // == '\0' ) {
                    decal = projectileDef.GetString("mtr_detonate")
                }
                if (decal.isNotEmpty()) { // == '\0' ) {
                    Game_local.gameLocal.ProjectDecal(
                        collision.c.point,
                        collision.c.normal.unaryMinus(),
                        8.0f,
                        true,
                        projectileDef.GetFloat("decal_size", "6.0f"),
                        decal
                    )
                }
            }

            fun ClientPredictionCollide(
                soundEnt: idEntity,
                projectileDef: idDict,
                collision: trace_s,
                velocity: idVec3,
                addDamageEffect: Boolean
            ): Boolean {
                val ent: idEntity?

                // remove projectile when a 'noimpact' surface is hit
                if (collision.c.material != null && (collision.c.material!!.GetSurfaceFlags() and Material.SURF_NOIMPACT) != 0) {
                    return false
                }

                // get the entity the projectile collided with
                ent = Game_local.gameLocal.entities[collision.c.entityNum]
                if (ent == null) {
                    return false
                }

                // don't do anything if hitting a noclip player
                if (ent is idPlayer && ent.noclip) {
                    return false
                }
                if (ent is idActor || ent is idAFAttachment && ent.GetBody() is idActor) {
                    if (!projectileDef.GetBool("detonate_on_actor")) {
                        return false
                    }
                } else {
                    if (!projectileDef.GetBool("detonate_on_world")) {
                        return false
                    }
                }

                // if the projectile causes a damage effect
                if (addDamageEffect && projectileDef.GetBool("impact_damage_effect")) {
                    // if the hit entity does not have a special damage effect
                    if (!ent.spawnArgs.GetBool("bleed")) {
                        // predict damage effect
                        DefaultDamageEffect(soundEnt, projectileDef, collision, velocity)
                    }
                }
                return true
            }

            fun getEventCallBacks(): MutableMap<idEventDef, eventCallback_t<*>> {
                return eventCallbacks
            }

            //
            init {
                eventCallbacks.putAll(idEntity.getEventCallBacks())
                eventCallbacks[EV_Explode] =
                    eventCallback_t0<idProjectile> { obj: idProjectile -> obj.Event_Explode() }
                eventCallbacks[EV_Fizzle] =
                    eventCallback_t0<idProjectile> { obj: idProjectile -> obj.Event_Fizzle() }
                eventCallbacks[EV_Touch] =
                    eventCallback_t2<idProjectile> { obj: idProjectile, other: idEventArg<*>?, trace: idEventArg<*>? ->
                        obj.Event_Touch(
                            other as idEventArg<idEntity>,
                            trace as idEventArg<trace_s>
                        )
                    }
                eventCallbacks[EV_RadiusDamage] =
                    eventCallback_t1<idProjectile> { obj: idProjectile, ignore: idEventArg<*>? ->
                        obj.Event_RadiusDamage(ignore as idEventArg<idEntity?>)
                    }
                eventCallbacks[EV_GetProjectileState] =
                    eventCallback_t0<idProjectile> { obj: idProjectile -> obj.Event_GetProjectileState() }
            }
        }

        protected var damagePower: Float
        protected val lightColor: idVec3
        protected var   /*qhandle_t*/lightDefHandle // handle to renderer light def
                : Int
        protected var lightEndTime: Int
        protected val lightOffset: idVec3
        protected var lightStartTime: Int
        protected val owner: idEntityPtr<idEntity?>
        protected var physicsObj: idPhysics_RigidBody
        protected var projectileFlags: projectileFlags_s
        protected var renderLight: renderLight_s
        protected var smokeFly: idDeclParticle?
        protected var smokeFlyTime: Int
        protected var state: projectileState_t
        protected var thrust: Float
        protected var thrust_end: Int
        protected var thruster: idForce_Constant
        private var netSyncPhysics: Boolean

        override fun _deconstructor() {
            StopSound(gameSoundChannel_t.SND_CHANNEL_ANY.ordinal, false)
            FreeLightDef()
            super._deconstructor()
        }

        override fun Spawn() {
            super.Spawn()
            physicsObj.SetSelf(this)
            physicsObj.SetClipModel(idClipModel(GetPhysics().GetClipModel()!!), 1.0f)
            physicsObj.SetContents(0)
            physicsObj.SetClipMask(0)
            physicsObj.PutToRest()
            SetPhysics(physicsObj)
        }

        override fun Save(savefile: idSaveGame) {
            owner.Save(savefile)
            val flags = projectileFlags
            LittleBitField(flags)
            savefile.Write(flags)
            savefile.WriteFloat(thrust)
            savefile.WriteInt(thrust_end)
            savefile.WriteRenderLight(renderLight)
            savefile.WriteInt(lightDefHandle)
            savefile.WriteVec3(lightOffset)
            savefile.WriteInt(lightStartTime)
            savefile.WriteInt(lightEndTime)
            savefile.WriteVec3(lightColor)
            savefile.WriteParticle(smokeFly)
            savefile.WriteInt(smokeFlyTime)
            savefile.WriteInt(TempDump.etoi(state))
            savefile.WriteFloat(damagePower)
            savefile.WriteStaticObject(physicsObj)
            savefile.WriteStaticObject(thruster)
        }

        override fun Restore(savefile: idRestoreGame) {
            owner.Restore(savefile)
            savefile.Read(projectileFlags)
            LittleBitField(projectileFlags)
            thrust = savefile.ReadFloat()
            thrust_end = savefile.ReadInt()
            savefile.ReadRenderLight(renderLight)
            lightDefHandle = savefile.ReadInt()
            if (lightDefHandle != -1) {
                lightDefHandle = Game_local.gameRenderWorld!!.AddLightDef(renderLight)
            }
            savefile.ReadVec3(lightOffset)
            lightStartTime = savefile.ReadInt()
            lightEndTime = savefile.ReadInt()
            savefile.ReadVec3(lightColor)
            savefile.ReadParticle(smokeFly!!)
            smokeFlyTime = savefile.ReadInt()
            state = projectileState_t.values()[savefile.ReadInt()]
            damagePower = savefile.ReadFloat()
            savefile.ReadStaticObject(physicsObj)
            RestorePhysics(physicsObj)
            savefile.ReadStaticObject(thruster)
            thruster.SetPhysics(physicsObj)
            if (smokeFly != null) {
                val dir = idVec3(physicsObj.GetLinearVelocity())
                dir.NormalizeFast()
                Game_local.gameLocal.smokeParticles!!.EmitSmoke(
                    smokeFly,
                    Game_local.gameLocal.time,
                    Game_local.gameLocal.random.RandomFloat(),
                    GetPhysics().GetOrigin(),
                    GetPhysics().GetAxis()
                )
            }
        }

        fun Create(owner: idEntity?, start: idVec3, dir: idVec3) {
            val shaderName: String?
            val light_color = idVec3()
            val tmp = idVec3()
            val axis: idMat3
            Unbind()

            // align z-axis of model with the direction
            axis = dir.ToMat3()
            tmp.set(axis[2])
            axis[2] = axis[0]
            axis[0] = tmp.unaryMinus()
            physicsObj.SetOrigin(start)
            physicsObj.SetAxis(axis)
            physicsObj.GetClipModel()!!.SetOwner(owner)
            this.owner.oSet(owner)

//	memset( &renderLight, 0, sizeof( renderLight ) );
            renderLight = renderLight_s()
            shaderName = spawnArgs.GetString("mtr_light_shader")
            if (!shaderName.isEmpty()) {
                renderLight.shader = DeclManager.declManager.FindMaterial(shaderName, false)
                renderLight.pointLight._val = true
                renderLight.lightRadius[0] = renderLight.lightRadius.set(
                    1,
                    renderLight.lightRadius.set(2, spawnArgs.GetFloat("light_radius"))
                )
                spawnArgs.GetVector("light_color", "1 1 1", light_color)
                renderLight.shaderParms[0] = light_color[0]
                renderLight.shaderParms[1] = light_color[1]
                renderLight.shaderParms[2] = light_color[2]
                renderLight.shaderParms[3] = 1.0f
            }
            spawnArgs.GetVector("light_offset", "0 0 0", lightOffset)
            lightStartTime = 0
            lightEndTime = 0
            smokeFlyTime = 0
            damagePower = 1.0f
            UpdateVisuals()
            state = projectileState_t.CREATED
            if (spawnArgs.GetBool("net_fullphysics")) {
                netSyncPhysics = true
            }
        }

        open fun Launch(
            start: idVec3,
            dir: idVec3,
            pushVelocity: idVec3,
            timeSinceFire: Float /*= 0.0f*/,
            launchPower: Float /*= 1.0f*/,
            dmgPower: Float /*= 1.0f*/
        ) {
            var fuse: Float
            val endthrust: Float
            val velocity = idVec3()
            val angular_velocity = idAngles()
            val linear_friction: Float
            val angular_friction: Float
            val contact_friction: Float
            val bounce: Float
            val mass: Float
            val speed: Float
            val gravity: Float
            val gravVec = idVec3()
            val tmp = idVec3()
            val axis: idMat3
            var contents: Int
            var clipMask: Int

            // allow characters to throw projectiles during cinematics, but not the player
            cinematic = if (owner.GetEntity() != null && owner.GetEntity() !is idPlayer) {
                owner.GetEntity()!!.cinematic
            } else {
                false
            }
            thrust = spawnArgs.GetFloat("thrust")
            endthrust = spawnArgs.GetFloat("thrust_end")
            spawnArgs.GetVector("velocity", "0 0 0", velocity)
            speed = velocity.Length() * launchPower
            damagePower = dmgPower
            spawnArgs.GetAngles("angular_velocity", "0 0 0", angular_velocity)
            linear_friction = spawnArgs.GetFloat("linear_friction")
            angular_friction = spawnArgs.GetFloat("angular_friction")
            contact_friction = spawnArgs.GetFloat("contact_friction")
            bounce = spawnArgs.GetFloat("bounce")
            mass = spawnArgs.GetFloat("mass")
            gravity = spawnArgs.GetFloat("gravity")
            fuse = spawnArgs.GetFloat("fuse")
            projectileFlags.detonate_on_world = spawnArgs.GetBool("detonate_on_world")
            projectileFlags.detonate_on_actor = spawnArgs.GetBool("detonate_on_actor")
            projectileFlags.randomShaderSpin = spawnArgs.GetBool("random_shader_spin")
            if (mass <= 0) {
                idGameLocal.Error("Invalid mass on '%s'\n", GetEntityDefName())
            }
            thrust *= mass
            thrust_end = (SEC2MS(endthrust) + Game_local.gameLocal.time).toInt()
            lightStartTime = 0
            lightEndTime = 0
            if (health != 0) {
                fl.takedamage = true
            }
            gravVec.set(Game_local.gameLocal.GetGravity())
            gravVec.NormalizeFast()
            Unbind()

            // align z-axis of model with the direction
            axis = dir.ToMat3()
            tmp.set(axis[2])
            axis[2] = axis[0]
            axis[0] = tmp.unaryMinus()
            contents = 0
            clipMask = Game_local.MASK_SHOT_RENDERMODEL
            if (spawnArgs.GetBool("detonate_on_trigger")) {
                contents = contents or Material.CONTENTS_TRIGGER
            }
            if (!spawnArgs.GetBool("no_contents")) {
                contents = contents or Material.CONTENTS_PROJECTILE
                clipMask = clipMask or Material.CONTENTS_PROJECTILE
            }

            // don't do tracers on client, we don't know origin and direction
            if (spawnArgs.GetBool("tracers") && Game_local.gameLocal.random.RandomFloat() > 0.5f) {
                SetModel(spawnArgs.GetString("model_tracer"))
                projectileFlags.isTracer = true
            }
            physicsObj.SetMass(mass)
            physicsObj.SetFriction(linear_friction, angular_friction, contact_friction)
            if (contact_friction == 0.0f) {
                physicsObj.NoContact()
            }
            physicsObj.SetBouncyness(bounce)
            physicsObj.SetGravity(gravVec.times(gravity))
            physicsObj.SetContents(contents)
            physicsObj.SetClipMask(clipMask)
            physicsObj.SetLinearVelocity(pushVelocity.plus(axis[2].times(speed)))
            physicsObj.SetAngularVelocity(angular_velocity.ToAngularVelocity().times(axis))
            physicsObj.SetOrigin(start)
            physicsObj.SetAxis(axis)
            thruster.SetPosition(physicsObj, 0, idVec3(GetPhysics().GetBounds()[0].x, 0.0f, 0.0f))
            if (!Game_local.gameLocal.isClient) {
                if (fuse <= 0) {
                    // run physics for 1 second
                    RunPhysics()
                    PostEventMS(EV_Remove, spawnArgs.GetInt("remove_time", "1500"))
                } else if (spawnArgs.GetBool("detonate_on_fuse")) {
                    fuse -= timeSinceFire
                    if (fuse < 0.0f) {
                        fuse = 0.0f
                    }
                    PostEventSec(EV_Explode, fuse)
                } else {
                    fuse -= timeSinceFire
                    if (fuse < 0.0f) {
                        fuse = 0.0f
                    }
                    PostEventSec(EV_Fizzle, fuse)
                }
            }
            if (projectileFlags.isTracer) {
                StartSound("snd_tracer", gameSoundChannel_t.SND_CHANNEL_BODY, 0, false)
            } else {
                StartSound("snd_fly", gameSoundChannel_t.SND_CHANNEL_BODY, 0, false)
            }
            smokeFlyTime = 0
            val smokeName = spawnArgs.GetString("smoke_fly")
            if (!smokeName.isEmpty()) { // != '\0' ) {
                smokeFly = DeclManager.declManager.FindType(declType_t.DECL_PARTICLE, smokeName) as idDeclParticle
                smokeFlyTime = Game_local.gameLocal.time
            }

            // used for the plasma bolts but may have other uses as well
            if (projectileFlags.randomShaderSpin) {
                var f = Game_local.gameLocal.random.RandomFloat()
                f *= 0.5f
                renderEntity!!.shaderParms[RenderWorld.SHADERPARM_DIVERSITY] = f
            }
            UpdateVisuals()
            state = projectileState_t.LAUNCHED
        }


        fun Launch(
            start: idVec3,
            dir: idVec3,
            pushVelocity: idVec3,
            timeSinceFire: Float = 0.0f /*= 0.0f*/,
            launchPower: Float = 1.0f /*= 1.0f*/
        ) {
            Launch(start, dir, pushVelocity, timeSinceFire, launchPower, 1.0f)
        }

        override fun FreeLightDef() {
            if (lightDefHandle != -1) {
                Game_local.gameRenderWorld!!.FreeLightDef(lightDefHandle)
                lightDefHandle = -1
            }
        }

        fun GetOwner(): idEntity? {
            return owner.GetEntity()
        }

        override fun Think() {
            if ((thinkFlags and TH_THINK) != 0) {
                if (thrust != 0.0f && Game_local.gameLocal.time < thrust_end) {
                    // evaluate force
                    thruster.SetForce(GetPhysics().GetAxis()[0] * thrust)
                    thruster.Evaluate(Game_local.gameLocal.time)
                }
            }

            // run physics
            RunPhysics()
            Present()

            // add the particles
            if (smokeFly != null && smokeFlyTime != 0 && !IsHidden()) {
                val dir = idVec3(GetPhysics().GetLinearVelocity().unaryMinus())
                dir.Normalize()
                if (!Game_local.gameLocal.smokeParticles!!.EmitSmoke(
                        smokeFly,
                        smokeFlyTime,
                        Game_local.gameLocal.random.RandomFloat(),
                        GetPhysics().GetOrigin(),
                        dir.ToMat3()
                    )
                ) {
                    smokeFlyTime = Game_local.gameLocal.time
                }
            }

            // add the light
            if (renderLight.lightRadius.x > 0.0f && SysCvar.g_projectileLights.GetBool()) {
                renderLight.origin.set(GetPhysics().GetOrigin() + GetPhysics().GetAxis() * lightOffset)
                renderLight.axis.set(GetPhysics().GetAxis())
                if (lightDefHandle != -1) {
                    if (lightEndTime > 0 && Game_local.gameLocal.time <= lightEndTime + Game_local.gameLocal.GetMSec()) {
                        val color = idVec3(0, 0, 0)
                        if (Game_local.gameLocal.time < lightEndTime) {
                            val frac: Float =
                                ((Game_local.gameLocal.time - lightStartTime).toFloat() / (lightEndTime - lightStartTime).toFloat())
                            color.Lerp(lightColor, color, frac)
                        }
                        renderLight.shaderParms[RenderWorld.SHADERPARM_RED] = color.x
                        renderLight.shaderParms[RenderWorld.SHADERPARM_GREEN] = color.y
                        renderLight.shaderParms[RenderWorld.SHADERPARM_BLUE] = color.z
                    }
                    Game_local.gameRenderWorld!!.UpdateLightDef(lightDefHandle, renderLight)
                } else {
                    lightDefHandle = Game_local.gameRenderWorld!!.AddLightDef(renderLight)
                }
            }
        }

        override fun Killed(inflictor: idEntity?, attacker: idEntity?, damage: Int, dir: idVec3, location: Int) {
            if (spawnArgs.GetBool("detonate_on_death")) {
                val collision: trace_s

//		memset( &collision, 0, sizeof( collision ) );
                collision = trace_s()
                collision.endAxis.set(GetPhysics().GetAxis())
                collision.endpos.set(GetPhysics().GetOrigin())
                collision.c.point.set(GetPhysics().GetOrigin())
                collision.c.normal.set(0.0f, 0.0f, 1.0f)
                Explode(collision, null)
                physicsObj.ClearContacts()
                physicsObj.PutToRest()
            } else {
                Fizzle()
            }
        }

        override fun Collide(collision: trace_s, velocity: idVec3): Boolean {
            val ent: idEntity?
            var ignore: idEntity?
            val damageDefName: String?
            val dir = idVec3()
            val push = CFloat()
            var damageScale: Float
            if (state == projectileState_t.EXPLODED || state == projectileState_t.FIZZLED) {
                return true
            }

            // predict the explosion
            if (Game_local.gameLocal.isClient) {
                if (ClientPredictionCollide(
                        this,
                        spawnArgs,
                        collision,
                        velocity,
                        !spawnArgs.GetBool("net_instanthit")
                    )
                ) {
                    Explode(collision, null)
                    return true
                }
                return false
            }

            // remove projectile when a 'noimpact' surface is hit
            if (collision.c.material != null && (collision.c.material!!.GetSurfaceFlags() and Material.SURF_NOIMPACT) != 0) {
                PostEventMS(EV_Remove, 0)
                idLib.common.DPrintf("Projectile collision no impact\n")
                return true
            }

            // get the entity the projectile collided with
            ent = Game_local.gameLocal.entities[collision.c.entityNum]
            if (ent == owner.GetEntity()) {
                assert(false)
                return true
            }

            // just get rid of the projectile when it hits a player in noclip
            if (ent is idPlayer && ent.noclip) {
                PostEventMS(EV_Remove, 0)
                return true
            }

            // direction of projectile
            dir.set(velocity)
            dir.Normalize()

            // projectiles can apply an additional impulse next to the rigid body physics impulse
            if (spawnArgs.GetFloat("push", "0", push) && push._val > 0) {
                ent!!.ApplyImpulse(this, collision.c.id, collision.c.point, dir.times(push._val))
            }

            // MP: projectiles open doors
            if (Game_local.gameLocal.isMultiplayer && ent is idDoor && !(ent as idDoor).IsOpen() && !ent.spawnArgs.GetBool(
                    "no_touch"
                )
            ) {
                ent.ProcessEvent(EV_Activate, this)
            }
            if (ent is idActor || ent is idAFAttachment && (ent as idAFAttachment).GetBody() is idActor) {
                if (!projectileFlags.detonate_on_actor) {
                    return false
                }
            } else {
                if (!projectileFlags.detonate_on_world) {
                    if (!StartSound("snd_ricochet", gameSoundChannel_t.SND_CHANNEL_ITEM, 0, true)) {
                        val len = velocity.Length()
                        if (len > BOUNCE_SOUND_MIN_VELOCITY) {
                            SetSoundVolume(
                                if (len > BOUNCE_SOUND_MAX_VELOCITY) 1.0f else idMath.Sqrt(len - BOUNCE_SOUND_MIN_VELOCITY) * (1.0f / idMath.Sqrt(
                                    BOUNCE_SOUND_MAX_VELOCITY - BOUNCE_SOUND_MIN_VELOCITY
                                ))
                            )
                            StartSound("snd_bounce", gameSoundChannel_t.SND_CHANNEL_ANY, 0, true)
                        }
                    }
                    return false
                }
            }
            SetOrigin(collision.endpos)
            SetAxis(collision.endAxis)

            // unlink the clip model because we no longer need it
            GetPhysics().UnlinkClip()
            damageDefName = spawnArgs.GetString("def_damage")
            ignore = null

            // if the hit entity takes damage
            if (ent!!.fl.takedamage) {
                damageScale = if (damagePower != 0.0f) {
                    damagePower
                } else {
                    1.0f
                }

                // if the projectile owner is a player
                if (owner.GetEntity() != null && owner.GetEntity() is idPlayer) {
                    // if the projectile hit an actor
                    if (ent is idActor) {
                        val player = owner.GetEntity() as idPlayer
                        player.AddProjectileHits(1)
                        damageScale *= player.PowerUpModifier(Player.PROJECTILE_DAMAGE)
                    }
                }
                if (!damageDefName.isEmpty()) { //[0] != '\0') {
                    ent.Damage(
                        this,
                        owner.GetEntity(),
                        dir,
                        damageDefName,
                        damageScale,
                        Clip.CLIPMODEL_ID_TO_JOINT_HANDLE(collision.c.id)
                    )
                    ignore = ent
                }
            }

            // if the projectile causes a damage effect
            if (spawnArgs.GetBool("impact_damage_effect")) {
                // if the hit entity has a special damage effect
                if (ent.spawnArgs.GetBool("bleed")) {
                    ent.AddDamageEffect(collision, velocity, damageDefName)
                } else {
                    AddDefaultDamageEffect(collision, velocity)
                }
            }
            Explode(collision, ignore)
            return true
        }

        open fun Explode(collision: trace_s, ignore: idEntity?) {
            var fxname: String?
            val light_shader: String?
            val sndExplode: String
            val light_fadetime: Float
            val normal = idVec3()
            var removeTime: Int
            if (state == projectileState_t.EXPLODED || state == projectileState_t.FIZZLED) {
                return
            }

            // stop sound
            StopSound(TempDump.etoi(gameSoundChannel_t.SND_CHANNEL_BODY2), false)
            sndExplode = when (damagePower.toInt()) {
                2 -> "snd_explode2"
                3 -> "snd_explode3"
                4 -> "snd_explode4"
                else -> "snd_explode"
            }
            StartSound(sndExplode, gameSoundChannel_t.SND_CHANNEL_BODY, 0, true)

            // we need to work out how long the effects last and then remove them at that time
            // for example, bullets have no real effects
            if (smokeFly != null && smokeFlyTime != 0) {
                smokeFlyTime = 0
            }
            Hide()
            FreeLightDef()
            if (spawnArgs.GetVector("detonation_axis", "", normal)) {
                GetPhysics().SetAxis(normal.ToMat3())
            }
            GetPhysics().SetOrigin(collision.endpos.plus(collision.c.normal.times(2.0f)))

            // default remove time
            removeTime = spawnArgs.GetInt("remove_time", "1500")

            // change the model, usually to a PRT
            fxname = if (SysCvar.g_testParticle.GetInteger() == Game.TEST_PARTICLE_IMPACT) {
                SysCvar.g_testParticleName.GetString()
            } else {
                spawnArgs.GetString("model_detonate")
            }
            val surfaceType =
                (if (collision.c.material != null) collision.c.material!!.GetSurfaceType() else surfTypes_t.SURFTYPE_METAL).ordinal
            if (!(fxname != null && !fxname.isEmpty())) {
                fxname = if (surfaceType == TempDump.etoi(surfTypes_t.SURFTYPE_NONE)
                    || surfaceType == TempDump.etoi(surfTypes_t.SURFTYPE_METAL)
                    || surfaceType == TempDump.etoi(surfTypes_t.SURFTYPE_STONE)
                ) {
                    spawnArgs.GetString("model_smokespark")
                } else if (surfaceType == TempDump.etoi(surfTypes_t.SURFTYPE_RICOCHET)) {
                    spawnArgs.GetString("model_ricochet")
                } else {
                    spawnArgs.GetString("model_smoke")
                }
            }
            if (fxname != null && !fxname.isEmpty()) {
                SetModel(fxname)
                renderEntity!!.shaderParms[RenderWorld.SHADERPARM_ALPHA] = 1.0f
                renderEntity!!.shaderParms[RenderWorld.SHADERPARM_BLUE] =
                    renderEntity!!.shaderParms[RenderWorld.SHADERPARM_ALPHA]
                renderEntity!!.shaderParms[RenderWorld.SHADERPARM_GREEN] =
                    renderEntity!!.shaderParms[RenderWorld.SHADERPARM_BLUE]
                renderEntity!!.shaderParms[RenderWorld.SHADERPARM_RED] =
                    renderEntity!!.shaderParms[RenderWorld.SHADERPARM_GREEN]
                renderEntity!!.shaderParms[RenderWorld.SHADERPARM_TIMEOFFSET] =
                    -MS2SEC(Game_local.gameLocal.time.toFloat())
                renderEntity!!.shaderParms[RenderWorld.SHADERPARM_DIVERSITY] =
                    Game_local.gameLocal.random.CRandomFloat()
                Show()
                removeTime = if (removeTime > 3000) removeTime else 3000
            }

            // explosion light
            light_shader = spawnArgs.GetString("mtr_explode_light_shader")
            if (light_shader.isNotEmpty()) {
                renderLight.shader = DeclManager.declManager.FindMaterial(light_shader, false)
                renderLight.pointLight._val = true
                val r = spawnArgs.GetFloat("explode_light_radius")
                renderLight.lightRadius[0] = r
                renderLight.lightRadius[1] = r
                renderLight.lightRadius[2] = r
                spawnArgs.GetVector("explode_light_color", "1 1 1", lightColor)
                renderLight.shaderParms[RenderWorld.SHADERPARM_RED] = lightColor.x
                renderLight.shaderParms[RenderWorld.SHADERPARM_GREEN] = lightColor.y
                renderLight.shaderParms[RenderWorld.SHADERPARM_BLUE] = lightColor.z
                renderLight.shaderParms[RenderWorld.SHADERPARM_ALPHA] = 1.0f
                renderLight.shaderParms[RenderWorld.SHADERPARM_TIMEOFFSET] =
                    -MS2SEC(Game_local.gameLocal.time.toFloat())
                light_fadetime = spawnArgs.GetFloat("explode_light_fadetime", "0.5f")
                lightStartTime = Game_local.gameLocal.time
                lightEndTime = (Game_local.gameLocal.time + SEC2MS(light_fadetime)).toInt()
                BecomeActive(TH_THINK)
            }
            fl.takedamage = false
            physicsObj.SetContents(0)
            physicsObj.PutToRest()
            state = projectileState_t.EXPLODED
            if (Game_local.gameLocal.isClient) {
                return
            }

            // alert the ai
            Game_local.gameLocal.AlertAI(owner.GetEntity())

            // bind the projectile to the impact entity if necesary
            if (Game_local.gameLocal.entities[collision.c.entityNum] != null && spawnArgs.GetBool("bindOnImpact")) {
                Bind(Game_local.gameLocal.entities[collision.c.entityNum], true)
            }

            // splash damage
            if (!projectileFlags.noSplashDamage) {
                val delay = spawnArgs.GetFloat("delay_splash")
                if (delay != 0.0f) {
                    if (removeTime < delay * 1000) {
                        removeTime = ((delay + 0.10) * 1000).toInt()
                    }
                    PostEventSec(EV_RadiusDamage, delay, ignore)
                } else {
                    Event_RadiusDamage(idEventArg.toArg(ignore))
                }
            }

            // spawn debris entities
            val fxdebris = spawnArgs.GetInt("debris_count")
            if (fxdebris != 0) {
                var debris = Game_local.gameLocal.FindEntityDefDict("projectile_debris", false)
                if (debris != null) {
                    val amount = Game_local.gameLocal.random.RandomInt(fxdebris)
                    for (i in 0 until amount) {
                        val ent = arrayOfNulls<idEntity>(1)
                        val dir = idVec3()
                        dir.x = Game_local.gameLocal.random.CRandomFloat() * 4.0f
                        dir.y = Game_local.gameLocal.random.CRandomFloat() * 4.0f
                        dir.z = Game_local.gameLocal.random.RandomFloat() * 8.0f
                        dir.Normalize()
                        Game_local.gameLocal.SpawnEntityDef(debris, ent, false)
                        if (null == ent[0] || ent[0] !is idDebris) {
                            idGameLocal.Error("'projectile_debris' is not an idDebris")
                        }
                        val debris2 = ent[0] as idDebris
                        debris2.Create(owner.GetEntity(), physicsObj.GetOrigin(), dir.ToMat3())
                        debris2.Launch()
                    }
                }
                debris = Game_local.gameLocal.FindEntityDefDict("projectile_shrapnel", false)
                if (debris != null) {
                    val amount = Game_local.gameLocal.random.RandomInt(fxdebris)
                    for (i in 0 until amount) {
                        val ent = arrayOfNulls<idEntity>(1)
                        val dir = idVec3()
                        dir.x = Game_local.gameLocal.random.CRandomFloat() * 8.0f
                        dir.y = Game_local.gameLocal.random.CRandomFloat() * 8.0f
                        dir.z = Game_local.gameLocal.random.RandomFloat() * 8.0f + 8.0f
                        dir.Normalize()
                        Game_local.gameLocal.SpawnEntityDef(debris, ent, false)
                        if (null == ent[0] || ent[0] !is idDebris) {
                            idGameLocal.Error("'projectile_shrapnel' is not an idDebris")
                        }
                        val debris2 = ent[0] as idDebris
                        debris2.Create(owner.GetEntity(), physicsObj.GetOrigin(), dir.ToMat3())
                        debris2.Launch()
                    }
                }
            }
            CancelEvents(EV_Explode)
            PostEventMS(EV_Remove, removeTime)
        }

        // };
        fun Fizzle() {
            if (state == projectileState_t.EXPLODED || state == projectileState_t.FIZZLED) {
                return
            }
            StopSound(TempDump.etoi(gameSoundChannel_t.SND_CHANNEL_BODY), false)
            StartSound("snd_fizzle", gameSoundChannel_t.SND_CHANNEL_BODY, 0, false)

            // fizzle FX
            val psystem = spawnArgs.GetString("smoke_fuse")
            if (psystem != null && !psystem.isEmpty()) {
//FIXME:SMOKE		gameLocal.particles.SpawnParticles( GetPhysics().GetOrigin(), vec3_origin, psystem );
            }

            // we need to work out how long the effects last and then remove them at that time
            // for example, bullets have no real effects
            if (smokeFly != null && smokeFlyTime != 0) {
                smokeFlyTime = 0
            }
            fl.takedamage = false
            physicsObj.SetContents(0)
            physicsObj.GetClipModel()!!.Unlink()
            physicsObj.PutToRest()
            Hide()
            FreeLightDef()
            state = projectileState_t.FIZZLED
            if (Game_local.gameLocal.isClient) {
                return
            }
            CancelEvents(EV_Fizzle)
            PostEventMS(EV_Remove, spawnArgs.GetInt("remove_time", "1500"))
        }

        override fun ClientPredictionThink() {
            if (null == renderEntity!!.hModel) {
                return
            }
            Think()
        }

        override fun WriteToSnapshot(msg: idBitMsgDelta) {
            msg.WriteBits(owner.GetSpawnId(), 32)
            msg.WriteBits(TempDump.etoi(state), 3)
            msg.WriteBits(TempDump.btoi(fl.hidden), 1)
            if (netSyncPhysics) {
                msg.WriteBits(1, 1)
                physicsObj.WriteToSnapshot(msg)
            } else {
                msg.WriteBits(0, 1)
                val origin = physicsObj.GetOrigin()
                val velocity = physicsObj.GetLinearVelocity()
                msg.WriteFloat(origin.x)
                msg.WriteFloat(origin.y)
                msg.WriteFloat(origin.z)
                msg.WriteDeltaFloat(
                    0.0f,
                    velocity[0],
                    Physics_RigidBody.RB_VELOCITY_EXPONENT_BITS,
                    Physics_RigidBody.RB_VELOCITY_MANTISSA_BITS
                )
                msg.WriteDeltaFloat(
                    0.0f,
                    velocity[1],
                    Physics_RigidBody.RB_VELOCITY_EXPONENT_BITS,
                    Physics_RigidBody.RB_VELOCITY_MANTISSA_BITS
                )
                msg.WriteDeltaFloat(
                    0.0f,
                    velocity[2],
                    Physics_RigidBody.RB_VELOCITY_EXPONENT_BITS,
                    Physics_RigidBody.RB_VELOCITY_MANTISSA_BITS
                )
            }
        }

        override fun ReadFromSnapshot(msg: idBitMsgDelta) {
            val newState: projectileState_t
            owner.SetSpawnId(msg.ReadBits(32))
            newState = projectileState_t.values()[msg.ReadBits(3)]
            if (msg.ReadBits(1) != 0) {
                Hide()
            } else {
                Show()
            }
            while (state != newState) {
                when (state) {
                    projectileState_t.SPAWNED -> {
                        Create(owner.GetEntity(), getVec3Origin(), idVec3(1, 0, 0))
                    }

                    projectileState_t.CREATED -> {

                        // the right origin and direction are required if you want bullet traces
                        Launch(getVec3Origin(), idVec3(1, 0, 0), getVec3Origin())
                    }

                    projectileState_t.LAUNCHED -> {
                        if (newState == projectileState_t.FIZZLED) {
                            Fizzle()
                        } else {
                            var collision: trace_s
                            //					memset( &collision, 0, sizeof( collision ) );
                            collision = trace_s()
                            collision.endAxis.set(GetPhysics().GetAxis())
                            collision.endpos.set(GetPhysics().GetOrigin())
                            collision.c.point.set(GetPhysics().GetOrigin())
                            collision.c.normal.set(0.0f, 0.0f, 1.0f)
                            Explode(collision, null)
                        }
                    }

                    projectileState_t.FIZZLED, projectileState_t.EXPLODED -> {
                        StopSound(TempDump.etoi(gameSoundChannel_t.SND_CHANNEL_BODY2), false)
                        GameEdit.gameEdit.ParseSpawnArgsToRenderEntity(spawnArgs, renderEntity!!)
                        state = projectileState_t.SPAWNED
                    }
                }
            }
            if (msg.ReadBits(1) != 0) {
                physicsObj.ReadFromSnapshot(msg)
            } else {
                val origin = idVec3()
                val velocity = idVec3()
                val tmp = idVec3()
                val axis: idMat3
                origin.x = msg.ReadFloat()
                origin.y = msg.ReadFloat()
                origin.z = msg.ReadFloat()
                velocity.x = msg.ReadDeltaFloat(
                    0.0f,
                    Physics_RigidBody.RB_VELOCITY_EXPONENT_BITS,
                    Physics_RigidBody.RB_VELOCITY_MANTISSA_BITS
                )
                velocity.y = msg.ReadDeltaFloat(
                    0.0f,
                    Physics_RigidBody.RB_VELOCITY_EXPONENT_BITS,
                    Physics_RigidBody.RB_VELOCITY_MANTISSA_BITS
                )
                velocity.z = msg.ReadDeltaFloat(
                    0.0f,
                    Physics_RigidBody.RB_VELOCITY_EXPONENT_BITS,
                    Physics_RigidBody.RB_VELOCITY_MANTISSA_BITS
                )
                physicsObj.SetOrigin(origin)
                physicsObj.SetLinearVelocity(velocity)

                // align z-axis of model with the direction
                velocity.NormalizeFast()
                axis = velocity.ToMat3()
                tmp.set(axis[2])
                axis[2] = axis[0]
                axis[0] = tmp.unaryMinus()
                physicsObj.SetAxis(axis)
            }
            if (msg.HasChanged()) {
                UpdateVisuals()
            }
        }

        override fun ClientReceiveEvent(event: Int, time: Int, msg: idBitMsg): Boolean {
            val collision: trace_s
            val velocity = idVec3()
            return when (event) {
                EVENT_DAMAGE_EFFECT -> {

//			memset( &collision, 0, sizeof( collision ) );
                    collision = trace_s()
                    collision.c.point[0] = msg.ReadFloat()
                    collision.c.point[1] = msg.ReadFloat()
                    collision.c.point[2] = msg.ReadFloat()
                    collision.c.normal.set(msg.ReadDir(24))
                    val index = Game_local.gameLocal.ClientRemapDecl(declType_t.DECL_MATERIAL, msg.ReadLong())
                    collision.c.material = if (index != -1) DeclManager.declManager.DeclByIndex(
                        declType_t.DECL_MATERIAL,
                        index
                    ) as Material.idMaterial else null
                    velocity[0] = msg.ReadFloat(5, 10)
                    velocity[1] = msg.ReadFloat(5, 10)
                    velocity[2] = msg.ReadFloat(5, 10)
                    DefaultDamageEffect(this, spawnArgs, collision, velocity)
                    true
                }

                else -> {
                    super.ClientReceiveEvent(event, time, msg)
                }
            }
            //            return false;
        }

        private fun AddDefaultDamageEffect(collision: trace_s, velocity: idVec3) {
            DefaultDamageEffect(this, spawnArgs, collision, velocity)
            if (Game_local.gameLocal.isServer && fl.networkSync) {
                val msg = idBitMsg()
                val msgBuf = ByteBuffer.allocate(Game_local.MAX_EVENT_PARAM_SIZE)
                val excludeClient: Int
                excludeClient = if (spawnArgs.GetBool("net_instanthit")) {
                    owner.GetEntityNum()
                } else {
                    -1
                }
                msg.Init(msgBuf, Game_local.MAX_EVENT_PARAM_SIZE)
                msg.BeginWriting()
                msg.WriteFloat(collision.c.point[0])
                msg.WriteFloat(collision.c.point[1])
                msg.WriteFloat(collision.c.point[2])
                msg.WriteDir(collision.c.normal, 24)
                msg.WriteLong(
                    if (collision.c.material != null) Game_local.gameLocal.ServerRemapDecl(
                        -1,
                        declType_t.DECL_MATERIAL,
                        collision.c.material!!.Index()
                    ) else -1
                )
                msg.WriteFloat(velocity[0], 5, 10)
                msg.WriteFloat(velocity[1], 5, 10)
                msg.WriteFloat(velocity[2], 5, 10)
                ServerSendEvent(EVENT_DAMAGE_EFFECT, msg, false, excludeClient)
            }
        }

        private fun Event_Explode() {
            val collision: trace_s

//	memset( &collision, 0, sizeof( collision ) );
            collision = trace_s()
            collision.endAxis.set(GetPhysics().GetAxis())
            collision.endpos.set(GetPhysics().GetOrigin())
            collision.c.point.set(GetPhysics().GetOrigin())
            collision.c.normal.set(0.0f, 0.0f, 1.0f)
            AddDefaultDamageEffect(collision, collision.c.normal)
            Explode(collision, null)
        }

        private fun Event_Fizzle() {
            Fizzle()
        }

        private fun Event_RadiusDamage(ignore: idEventArg<idEntity?>) {
            val splash_damage = spawnArgs.GetString("def_splash_damage")
            if (!splash_damage.isEmpty()) { //[0] != '\0' ) {
                Game_local.gameLocal.RadiusDamage(
                    physicsObj.GetOrigin(),
                    this,
                    owner.GetEntity(),
                    ignore.value,
                    this,
                    splash_damage,
                    damagePower
                )
            }
        }

        private fun Event_Touch(other: idEventArg<idEntity>, trace: idEventArg<trace_s>) {
            if (IsHidden()) {
                return
            }
            if (other.value != owner.GetEntity()) {
                val collision: trace_s
                collision = trace_s() //memset( &collision, 0, sizeof( collision ) );
                collision.endAxis.set(GetPhysics().GetAxis())
                collision.endpos.set(GetPhysics().GetOrigin())
                collision.c.point.set(GetPhysics().GetOrigin())
                collision.c.normal.set(0.0f, 0.0f, 1.0f)
                AddDefaultDamageEffect(collision, collision.c.normal)
                Explode(collision, null)
            }
        }

        private fun Event_GetProjectileState() {
            idThread.ReturnInt(TempDump.etoi(state))
        }

        override fun getEventCallBack(event: idEventDef): eventCallback_t<*>? {
            return eventCallbacks[event]
        }

        enum class projectileState_t {
            // must update these in script/doom_defs.script if changed
            SPAWNED,  //= 0,
            CREATED,  //= 1,
            LAUNCHED,  //= 2,
            FIZZLED,  //= 3,
            EXPLODED
            //= 4
        }

        class projectileFlags_s : SERiAL {
            var detonate_on_actor //: 1;
                    = false
            var detonate_on_world //: 1;
                    = false
            var isTracer //: 1;
                    = false
            var noSplashDamage //: 1;
                    = false
            var randomShaderSpin //: 1;
                    = false

            override fun AllocBuffer(): ByteBuffer {
                throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
            }

            override fun Read(buffer: ByteBuffer) {
                throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
            }

            override fun Write(): ByteBuffer {
                throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
            }
        }

        //
        //
        // public :
        // CLASS_PROTOTYPE( idProjectile );
        init {
            owner = idEntityPtr()
            lightDefHandle = -1
            thrust = 0.0f
            thrust_end = 0
            smokeFly = null
            smokeFlyTime = 0
            state = projectileState_t.SPAWNED
            lightOffset = getVec3_zero()
            lightStartTime = 0
            lightEndTime = 0
            lightColor = getVec3_zero()
            state = projectileState_t.SPAWNED
            damagePower = 1.0f
            projectileFlags = projectileFlags_s() //memset( &projectileFlags, 0, sizeof( projectileFlags ) );
            renderLight = renderLight_s() //memset( &renderLight, 0, sizeof( renderLight ) );

            // note: for net_instanthit projectiles, we will force this back to false at spawn time
            fl.networkSync = true
            netSyncPhysics = false
            physicsObj = idPhysics_RigidBody()
            thruster = idForce_Constant()
        }
    }

    /*
     ===============================================================================

     idGuidedProjectile

     ===============================================================================
     */
    open class idGuidedProjectile : idProjectile() {
        // CLASS_PROTOTYPE( idGuidedProjectile );
        protected val enemy: idEntityPtr<idEntity>

        //
        protected var speed: Float
        private val angles: idAngles = idAngles()
        private var burstDist: Float
        private var burstMode: Boolean
        private var burstVelocity: Float
        private var clamp_dist: Float
        private val rndAng: idAngles = idAngles()
        private val rndScale: idAngles = idAngles()
        private var rndUpdateTime: Int
        private var turn_max: Float
        private var unGuided: Boolean

        // ~idGuidedProjectile( void );
        override fun Save(savefile: idSaveGame) {
            enemy.Save(savefile)
            savefile.WriteFloat(speed)
            savefile.WriteAngles(rndScale)
            savefile.WriteAngles(rndAng)
            savefile.WriteInt(rndUpdateTime)
            savefile.WriteFloat(turn_max)
            savefile.WriteFloat(clamp_dist)
            savefile.WriteAngles(angles)
            savefile.WriteBool(burstMode)
            savefile.WriteBool(unGuided)
            savefile.WriteFloat(burstDist)
            savefile.WriteFloat(burstVelocity)
        }

        override fun Restore(savefile: idRestoreGame) {
            enemy.Restore(savefile)
            speed = savefile.ReadFloat()
            savefile.ReadAngles(rndScale)
            savefile.ReadAngles(rndAng)
            rndUpdateTime = savefile.ReadInt()
            turn_max = savefile.ReadFloat()
            clamp_dist = savefile.ReadFloat()
            savefile.ReadAngles(angles)
            burstMode = savefile.ReadBool()
            unGuided = savefile.ReadBool()
            burstDist = savefile.ReadFloat()
            burstVelocity = savefile.ReadFloat()
        }

        override fun Think() {
            val dir = idVec3()
            val seekPos = idVec3()
            val velocity = idVec3()
            val nose = idVec3()
            val tmp = idVec3()
            val axis: idMat3
            val dirAng: idAngles?
            val diff: idAngles?
            val dist: Float
            var frac: Float
            var i: Int
            if (state == projectileState_t.LAUNCHED && !unGuided) {
                GetSeekPos(seekPos)
                if (rndUpdateTime < Game_local.gameLocal.time) {
                    rndAng.set(0, rndScale.get(0) * Game_local.gameLocal.random.CRandomFloat())
                    rndAng.set(1, rndScale.get(1) * Game_local.gameLocal.random.CRandomFloat())
                    rndAng.set(2, rndScale.get(2) * Game_local.gameLocal.random.CRandomFloat())
                    rndUpdateTime = Game_local.gameLocal.time + 200
                }
                nose.set(physicsObj.GetOrigin().plus(physicsObj.GetAxis()[0].times(10.0f)))
                dir.set(seekPos.minus(nose))
                dist = dir.Normalize()
                dirAng = dir.ToAngles()

                // make it more accurate as it gets closer
                frac = dist / clamp_dist
                if (frac > 1.0f) {
                    frac = 1.0f
                }
                diff = dirAng.minus(angles).plus(rndAng.times(frac))

                // clamp the to the max turn rate
                diff.Normalize180()
                i = 0
                while (i < 3) {
                    if (diff[i] > turn_max) {
                        diff[i] = turn_max
                    } else if (diff[i] < -turn_max) {
                        diff[i] = -turn_max
                    }
                    i++
                }
                angles.plusAssign(diff)

                // make the visual model always points the dir we're traveling
                dir.set(angles.ToForward())
                velocity.set(dir.times(speed))
                if (burstMode && dist < burstDist) {
                    unGuided = true
                    velocity.timesAssign(burstVelocity)
                }
                physicsObj.SetLinearVelocity(velocity)

                // align z-axis of model with the direction
                axis = dir.ToMat3()
                tmp.set(axis[2])
                axis[2] = axis[0]
                axis[0] = tmp.unaryMinus()
                GetPhysics().SetAxis(axis)
            }
            super.Think()
        }

        override fun Launch(
            start: idVec3,
            dir: idVec3,
            pushVelocity: idVec3,
            timeSinceFire: Float /*= 0.0f*/,
            launchPower: Float /*= 1.0f*/,
            dmgPower: Float /*= 1.0f*/
        ) {
            super.Launch(start, dir, pushVelocity, timeSinceFire, launchPower, dmgPower)
            if (owner.GetEntity() != null) {
                if (owner.GetEntity() is idAI) {
                    enemy.oSet((owner.GetEntity() as idAI).GetEnemy())
                } else if (owner.GetEntity() is idPlayer) {
                    val tr = trace_s()
                    val player = owner.GetEntity() as idPlayer
                    val start2 = idVec3(player.GetEyePosition())
                    val end2 = idVec3(start2.plus(player.viewAxis[0].times(1000.0f)))
                    Game_local.gameLocal.clip.TracePoint(
                        tr,
                        start2,
                        end2,
                        Game_local.MASK_SHOT_RENDERMODEL or Material.CONTENTS_BODY,
                        owner.GetEntity()
                    )
                    if (tr.fraction < 1.0f) {
                        enemy.oSet(Game_local.gameLocal.GetTraceEntity(tr))
                    }
                    // ignore actors on the player's team
                    if (enemy.GetEntity() == null || enemy.GetEntity() !is idActor || (enemy.GetEntity() as idActor).team == player.team) {
                        enemy.oSet(player.EnemyWithMostHealth())
                    }
                }
            }
            val vel = physicsObj.GetLinearVelocity()
            angles.set(vel.ToAngles())
            speed = vel.Length()
            rndScale.set(spawnArgs.GetAngles("random", "15 15 0"))
            turn_max = spawnArgs.GetFloat("turn_max", "180") / UsercmdGen.USERCMD_HZ.toFloat()
            clamp_dist = spawnArgs.GetFloat("clamp_dist", "256")
            burstMode = spawnArgs.GetBool("burstMode")
            unGuided = false
            burstDist = spawnArgs.GetFloat("burstDist", "64")
            burstVelocity = spawnArgs.GetFloat("burstVelocity", "1.25")
            UpdateVisuals()
        }

        protected open fun GetSeekPos(out: idVec3) {
            val enemyEnt = enemy.GetEntity()
            if (enemyEnt != null) {
                if (enemyEnt is idActor) {
                    out.set(enemyEnt.GetEyePosition())
                    out.z -= 12.0f
                } else {
                    out.set(enemyEnt.GetPhysics().GetOrigin())
                }
            } else {
                out.set(GetPhysics().GetOrigin().plus(physicsObj.GetLinearVelocity().times(2.0f)))
            }
        }

        //
        //
        init {
            enemy = idEntityPtr()
            speed = 0.0f
            turn_max = 0.0f
            clamp_dist = 0.0f
            rndScale.set(ang_zero)
            rndAng.set(ang_zero)
            rndUpdateTime = 0
            angles.set(ang_zero)
            burstMode = false
            burstDist = 0.0f
            burstVelocity = 0.0f
            unGuided = false
        }
    }

    /*
     ===============================================================================

     idSoulCubeMissile

     ===============================================================================
     */
    class idSoulCubeMissile : idGuidedProjectile() {
        // CLASS_PROTOTYPE ( idSoulCubeMissile );
        private var accelTime = 0.0f
        private val destOrg: idVec3 = idVec3()
        private val endingVelocity: idVec3 = idVec3()
        private var killPhase = false
        private var launchTime = 0
        private val orbitOrg: idVec3 = idVec3()
        private var orbitTime = 0
        private var returnPhase = false
        private var smokeKill: idDeclParticle? = null
        private var smokeKillTime = 0
        private val startingVelocity: idVec3 = idVec3()

        //
        //
        // ~idSoulCubeMissile();
        override fun Save(savefile: idSaveGame) {
            savefile.WriteVec3(startingVelocity)
            savefile.WriteVec3(endingVelocity)
            savefile.WriteFloat(accelTime)
            savefile.WriteInt(launchTime)
            savefile.WriteBool(killPhase)
            savefile.WriteBool(returnPhase)
            savefile.WriteVec3(destOrg)
            savefile.WriteInt(orbitTime)
            savefile.WriteVec3(orbitOrg)
            savefile.WriteInt(smokeKillTime)
            savefile.WriteParticle(smokeKill)
        }

        override fun Restore(savefile: idRestoreGame) {
            savefile.ReadVec3(startingVelocity)
            savefile.ReadVec3(endingVelocity)
            accelTime = savefile.ReadFloat()
            launchTime = savefile.ReadInt()
            killPhase = savefile.ReadBool()
            returnPhase = savefile.ReadBool()
            savefile.ReadVec3(destOrg)
            orbitTime = savefile.ReadInt()
            savefile.ReadVec3(orbitOrg)
            smokeKillTime = savefile.ReadInt()
            savefile.ReadParticle(smokeKill!!)
        }

        override fun Spawn() {
            super.Spawn()
            startingVelocity.Zero()
            endingVelocity.Zero()
            accelTime = 0.0f
            launchTime = 0
            killPhase = false
            returnPhase = false
            smokeKillTime = 0
            smokeKill = null
        }

        override fun Think() {
            val pct: Float
            val seekPos = idVec3()
            val ownerEnt: idEntity?
            if (state == projectileState_t.LAUNCHED) {
                if (killPhase) {
                    // orbit the mob, cascading down
                    if (Game_local.gameLocal.time < orbitTime + 1500) {
                        if (!Game_local.gameLocal.smokeParticles!!.EmitSmoke(
                                smokeKill,
                                smokeKillTime,
                                Game_local.gameLocal.random.CRandomFloat(),
                                orbitOrg,
                                idMat3.getMat3_identity()
                            )
                        ) {
                            smokeKillTime = Game_local.gameLocal.time
                        }
                    }
                } else {
                    if (accelTime != 0.0f && Game_local.gameLocal.time < launchTime + accelTime * 1000) {
                        pct = (Game_local.gameLocal.time - launchTime) / (accelTime * 1000)
                        speed = startingVelocity.plus(startingVelocity.plus(endingVelocity).times(pct)).Length()
                    }
                }
                super.Think()
                GetSeekPos(seekPos)
                if (seekPos.minus(physicsObj.GetOrigin()).Length() < 32.0f) {
                    if (returnPhase) {
                        StopSound(TempDump.etoi(gameSoundChannel_t.SND_CHANNEL_ANY), false)
                        StartSound("snd_return", gameSoundChannel_t.SND_CHANNEL_BODY2, 0, false)
                        Hide()
                        PostEventSec(EV_Remove, 2.0f)
                        ownerEnt = owner.GetEntity()
                        if (ownerEnt != null && ownerEnt is idPlayer) {
                            (ownerEnt as idPlayer).SetSoulCubeProjectile(null)
                        }
                        state = projectileState_t.FIZZLED
                    } else if (!killPhase) {
                        KillTarget(physicsObj.GetAxis()[0])
                    }
                }
            }
        }

        override fun Launch(
            start: idVec3,
            dir: idVec3,
            pushVelocity: idVec3,
            timeSinceFire: Float /*= 0.0f*/,
            launchPower: Float /*= 1.0f*/,
            dmgPower: Float /*= 1.0f*/
        ) {
            val newStart = idVec3()
            val offs = idVec3()
            val ownerEnt: idEntity?

            // push it out a little
            newStart.set(start.plus(dir.times(spawnArgs.GetFloat("launchDist"))))
            offs.set(spawnArgs.GetVector("launchOffset", "0 0 -4"))
            newStart.plusAssign(offs)
            super.Launch(newStart, dir, pushVelocity, timeSinceFire, launchPower, dmgPower)
            if (enemy.GetEntity() == null || enemy.GetEntity() !is idActor) {
                destOrg.set(start.plus(dir.times(256.0f)))
            } else {
                destOrg.Zero()
            }
            physicsObj.SetClipMask(0) // never collide.. think routine will decide when to detonate
            startingVelocity.set(spawnArgs.GetVector("startingVelocity", "15 0 0"))
            endingVelocity.set(spawnArgs.GetVector("endingVelocity", "1500 0 0"))
            accelTime = spawnArgs.GetFloat("accelTime", "5")
            physicsObj.SetLinearVelocity(physicsObj.GetAxis()[2].times(startingVelocity.Length()))
            launchTime = Game_local.gameLocal.time
            killPhase = false
            UpdateVisuals()
            ownerEnt = owner.GetEntity()
            if (ownerEnt != null && ownerEnt is idPlayer) {
                ownerEnt.SetSoulCubeProjectile(this)
            }
        }

        override fun GetSeekPos(out: idVec3) {
            if (returnPhase && owner.GetEntity() != null && owner.GetEntity() is idActor) {
                val act = owner.GetEntity() as idActor
                out.set(act.GetEyePosition())
                return
            }
            if (destOrg != getVec3_zero()) {
                out.set(destOrg)
                return
            }
            super.GetSeekPos(out)
        }

        protected fun ReturnToOwner() {
            speed *= 0.65f
            killPhase = false
            returnPhase = true
            smokeFlyTime = 0
        }

        protected fun KillTarget(dir: idVec3) {
            val ownerEnt: idEntity?
            val smokeName: String?
            val act: idActor?
            ReturnToOwner()
            if (enemy.GetEntity() != null && enemy.GetEntity() is idActor) {
                act = enemy.GetEntity() as idActor
                killPhase = true
                orbitOrg.set(act.GetPhysics().GetAbsBounds().GetCenter())
                orbitTime = Game_local.gameLocal.time
                smokeKillTime = 0
                smokeName = spawnArgs.GetString("smoke_kill")
                if (!smokeName.isEmpty()) { // != '\0' ) {
                    smokeKill = DeclManager.declManager.FindType(declType_t.DECL_PARTICLE, smokeName) as idDeclParticle
                    smokeKillTime = Game_local.gameLocal.time
                }
                ownerEnt = owner.GetEntity()
                if (act.health > 0 && ownerEnt != null && ownerEnt is idPlayer && ownerEnt.health > 0 && !act.spawnArgs.GetBool(
                        "boss"
                    )
                ) {
                    ownerEnt.GiveHealthPool(act.health.toFloat())
                }
                act.Damage(this, owner.GetEntity(), dir, spawnArgs.GetString("def_damage"), 1.0f, Model.INVALID_JOINT)
                act.GetAFPhysics().SetTimeScale(0.25f)
                StartSound("snd_explode", gameSoundChannel_t.SND_CHANNEL_BODY, 0, false)
            }
        }
    }

    class beamTarget_t {
        var   /*qhandle_t*/modelDefHandle = 0
        var renderEntity: renderEntity_s = renderEntity_s()
        val target: idEntityPtr<idEntity> = idEntityPtr()
    }

    /*
     ===============================================================================

     idBFGProjectile

     ===============================================================================
     */
    class idBFGProjectile : idProjectile() {
        companion object {
            // CLASS_PROTOTYPE( idBFGProjectile );
            private val eventCallbacks: MutableMap<idEventDef, eventCallback_t<*>> = HashMap()
            fun getEventCallBacks(): MutableMap<idEventDef, eventCallback_t<*>> {
                return eventCallbacks
            }

            init {
                eventCallbacks.putAll(idProjectile.getEventCallBacks())
                eventCallbacks[EV_RemoveBeams] =
                    eventCallback_t0<idBFGProjectile> { obj: idBFGProjectile -> obj.Event_RemoveBeams() }
            }
        }

        private val beamTargets: List.idList<beamTarget_t>
        private val damageFreq: idStr
        private var nextDamageTime: Int
        private var secondModel: renderEntity_s
        private var   /*qhandle_t*/secondModelDefHandle: Int
        override fun _deconstructor() {
            FreeBeams()
            if (secondModelDefHandle >= 0) {
                Game_local.gameRenderWorld!!.FreeEntityDef(secondModelDefHandle)
                secondModelDefHandle = -1
            }
            super._deconstructor()
        }

        override fun Save(savefile: idSaveGame) {
            var i: Int
            savefile.WriteInt(beamTargets.Num())
            i = 0
            while (i < beamTargets.Num()) {
                beamTargets[i].target.Save(savefile)
                savefile.WriteRenderEntity(beamTargets[i].renderEntity)
                savefile.WriteInt(beamTargets[i].modelDefHandle)
                i++
            }
            savefile.WriteRenderEntity(secondModel)
            savefile.WriteInt(secondModelDefHandle)
            savefile.WriteInt(nextDamageTime)
            savefile.WriteString(damageFreq)
        }

        override fun Restore(savefile: idRestoreGame) {
            var i: Int
            val num = CInt()
            savefile.ReadInt(num)
            beamTargets.SetNum(num._val)
            i = 0
            while (i < num._val) {
                beamTargets[i].target.Restore(savefile)
                savefile.ReadRenderEntity(beamTargets[i].renderEntity)
                beamTargets[i].modelDefHandle = savefile.ReadInt()
                if (beamTargets[i].modelDefHandle >= 0) {
                    beamTargets[i].modelDefHandle =
                        Game_local.gameRenderWorld!!.AddEntityDef(beamTargets[i].renderEntity)
                }
                i++
            }
            savefile.ReadRenderEntity(secondModel)
            secondModelDefHandle = savefile.ReadInt()
            nextDamageTime = savefile.ReadInt()
            savefile.ReadString(damageFreq)
            if (secondModelDefHandle >= 0) {
                secondModelDefHandle = Game_local.gameRenderWorld!!.AddEntityDef(secondModel)
            }
        }

        override fun Spawn() {
            super.Spawn()
            beamTargets.Clear()
            secondModel = renderEntity_s() //memset( &secondModel, 0, sizeof( secondModel ) );
            secondModelDefHandle = -1
            val temp = spawnArgs.GetString("model_two")
            if (temp != null && !temp.isEmpty()) {
                secondModel.hModel = ModelManager.renderModelManager.FindModel(temp)
                secondModel.bounds.set(secondModel.hModel!!.Bounds(secondModel))
                secondModel.shaderParms[RenderWorld.SHADERPARM_ALPHA] = 1.0f
                secondModel.shaderParms[RenderWorld.SHADERPARM_BLUE] =
                    secondModel.shaderParms[RenderWorld.SHADERPARM_ALPHA]
                secondModel.shaderParms[RenderWorld.SHADERPARM_GREEN] =
                    secondModel.shaderParms[RenderWorld.SHADERPARM_BLUE]
                secondModel.shaderParms[RenderWorld.SHADERPARM_RED] =
                    secondModel.shaderParms[RenderWorld.SHADERPARM_GREEN]
                secondModel.noSelfShadow = true
                secondModel.noShadow = true
            }
            nextDamageTime = 0
            damageFreq.Clear()
        }

        override fun Think() {
            if (state == projectileState_t.LAUNCHED) {

                // update beam targets
                for (i in 0 until beamTargets.Num()) {
                    if (beamTargets[i].target.GetEntity() == null) {
                        continue
                    }
                    val player =
                        if (beamTargets[i].target.GetEntity() is idPlayer) beamTargets[i].target.GetEntity() as idPlayer? else null
                    val org = idVec3(beamTargets[i].target.GetEntity()!!.GetPhysics().GetAbsBounds().GetCenter())
                    beamTargets[i].renderEntity.origin.set(GetPhysics().GetOrigin())
                    beamTargets[i].renderEntity.shaderParms[RenderWorld.SHADERPARM_BEAM_END_X] = org.x
                    beamTargets[i].renderEntity.shaderParms[RenderWorld.SHADERPARM_BEAM_END_Y] = org.y
                    beamTargets[i].renderEntity.shaderParms[RenderWorld.SHADERPARM_BEAM_END_Z] = org.z
                    beamTargets[i].renderEntity.shaderParms[RenderWorld.SHADERPARM_ALPHA] = 1.0f
                    beamTargets[i].renderEntity.shaderParms[RenderWorld.SHADERPARM_BLUE] =
                        beamTargets[i].renderEntity.shaderParms[RenderWorld.SHADERPARM_ALPHA]
                    beamTargets[i].renderEntity.shaderParms[RenderWorld.SHADERPARM_GREEN] =
                        beamTargets[i].renderEntity.shaderParms[RenderWorld.SHADERPARM_BLUE]
                    beamTargets[i].renderEntity.shaderParms[RenderWorld.SHADERPARM_RED] =
                        beamTargets[i].renderEntity.shaderParms[RenderWorld.SHADERPARM_GREEN]
                    if (Game_local.gameLocal.time > nextDamageTime) {
                        var bfgVision = true
                        if (damageFreq != null &&  /*(const char *)*/!damageFreq.IsEmpty() && beamTargets[i].target.GetEntity() != null && beamTargets[i].target.GetEntity()!!
                                .CanDamage(GetPhysics().GetOrigin(), org)
                        ) {
                            org.set(
                                beamTargets[i].target.GetEntity()!!.GetPhysics().GetOrigin()
                                    .minus(GetPhysics().GetOrigin())
                            )
                            org.Normalize()
                            beamTargets[i].target.GetEntity()!!.Damage(
                                this,
                                owner.GetEntity(),
                                org,
                                damageFreq.toString(),
                                if (damagePower != 0.0f) damagePower else 1.0f,
                                Model.INVALID_JOINT
                            )
                        } else {
                            beamTargets[i].renderEntity.shaderParms[RenderWorld.SHADERPARM_ALPHA] = 0.0f
                            beamTargets[i].renderEntity.shaderParms[RenderWorld.SHADERPARM_BLUE] =
                                beamTargets[i].renderEntity.shaderParms[RenderWorld.SHADERPARM_ALPHA]
                            beamTargets[i].renderEntity.shaderParms[RenderWorld.SHADERPARM_GREEN] =
                                beamTargets[i].renderEntity.shaderParms[RenderWorld.SHADERPARM_BLUE]
                            beamTargets[i].renderEntity.shaderParms[RenderWorld.SHADERPARM_RED] =
                                beamTargets[i].renderEntity.shaderParms[RenderWorld.SHADERPARM_GREEN]
                            bfgVision = false
                        }
                        player?.playerView?.EnableBFGVision(bfgVision)
                        nextDamageTime = Game_local.gameLocal.time + BFG_DAMAGE_FREQUENCY
                    }
                    Game_local.gameRenderWorld!!.UpdateEntityDef(
                        beamTargets[i].modelDefHandle,
                        beamTargets[i].renderEntity
                    )
                }
                if (secondModelDefHandle >= 0) {
                    secondModel.origin.set(GetPhysics().GetOrigin())
                    Game_local.gameRenderWorld!!.UpdateEntityDef(secondModelDefHandle, secondModel)
                }
                val ang = idAngles()
                ang.pitch = (Game_local.gameLocal.time and 4095) * 360.0f / -4096.0f
                ang.yaw = ang.pitch
                ang.roll = 0.0f
                SetAngles(ang)
                ang.pitch = (Game_local.gameLocal.time and 2047) * 360.0f / -2048.0f
                ang.yaw = ang.pitch
                ang.roll = 0.0f
                secondModel.axis.set(ang.ToMat3())
                UpdateVisuals()
            }
            super.Think()
        }

        override fun Launch(
            start: idVec3,
            dir: idVec3,
            pushVelocity: idVec3,
            timeSinceFire: Float /*= 0.0f*/,
            power: Float /*= 1.0f*/,
            dmgPower: Float /*= 1.0f*/
        ) {
            super.Launch(start, dir, pushVelocity, 0.0f, power, dmgPower)

            // dmgPower * radius is the target acquisition area
            // acquisition should make sure that monsters are not dormant
            // which will cut down on hitting monsters not actively fighting
            // but saves on the traces making sure they are visible
            // damage is not applied until the projectile explodes
            var ent: idEntity?
            val entityList = arrayOfNulls<idEntity>(Game_local.MAX_GENTITIES)
            val numListedEntities: Int
            val bounds: idBounds
            val damagePoint = idVec3()
            val radius = CFloat()
            spawnArgs.GetFloat("damageRadius", "512", radius)
            bounds = idBounds(GetPhysics().GetOrigin()).Expand(radius._val)
            val beamWidth = spawnArgs.GetFloat("beam_WidthFly")
            val skin = spawnArgs.GetString("skin_beam")

//	memset( &secondModel, 0, sizeof( secondModel ) );
            secondModel = renderEntity_s()
            secondModelDefHandle = -1
            val temp = spawnArgs.GetString("model_two")
            if (temp.isNotEmpty()) {
                secondModel.hModel = ModelManager.renderModelManager.FindModel(temp)
                secondModel.bounds.set(secondModel.hModel!!.Bounds(secondModel))
                secondModel.shaderParms[RenderWorld.SHADERPARM_ALPHA] = 1.0f
                secondModel.shaderParms[RenderWorld.SHADERPARM_BLUE] =
                    secondModel.shaderParms[RenderWorld.SHADERPARM_ALPHA]
                secondModel.shaderParms[RenderWorld.SHADERPARM_GREEN] =
                    secondModel.shaderParms[RenderWorld.SHADERPARM_BLUE]
                secondModel.shaderParms[RenderWorld.SHADERPARM_RED] =
                    secondModel.shaderParms[RenderWorld.SHADERPARM_GREEN]
                secondModel.noSelfShadow = true
                secondModel.noShadow = true
                secondModel.origin.set(GetPhysics().GetOrigin())
                secondModel.axis.set(GetPhysics().GetAxis())
                secondModelDefHandle = Game_local.gameRenderWorld!!.AddEntityDef(secondModel)
            }
            idVec3(15.0f, 15.0f, 15.0f)
            //physicsObj.SetAngularExtrapolation( extrapolation_t(EXTRAPOLATION_LINEAR|EXTRAPOLATION_NOSTOP), gameLocal.time, 0, physicsObj.GetAxis().ToAngles(), delta, ang_zero );

            // get all entities touching the bounds
            numListedEntities = Game_local.gameLocal.clip.EntitiesTouchingBounds(
                bounds,
                Material.CONTENTS_BODY,
                entityList,
                Game_local.MAX_GENTITIES
            )
            for (e in 0 until numListedEntities) {
                ent = entityList[e]
                assert(ent != null)
                if (ent === this || ent === owner.GetEntity() || ent!!.IsHidden() || !ent.IsActive() || !ent.fl.takedamage || ent.health <= 0 || ent !is idActor) {
                    continue
                }
                if (!ent.CanDamage(GetPhysics().GetOrigin(), damagePoint)) {
                    continue
                }
                if (ent is idPlayer) {
                    val player = ent
                    player.playerView.EnableBFGVision(true)
                }
                val bt = beamTarget_t() //memset( &bt.renderEntity, 0, sizeof( renderEntity_t ) );
                bt.renderEntity.origin.set(GetPhysics().GetOrigin())
                bt.renderEntity.axis.set(GetPhysics().GetAxis())
                bt.renderEntity.shaderParms[RenderWorld.SHADERPARM_BEAM_WIDTH] = beamWidth
                bt.renderEntity.shaderParms[RenderWorld.SHADERPARM_RED] = 1.0f
                bt.renderEntity.shaderParms[RenderWorld.SHADERPARM_GREEN] = 1.0f
                bt.renderEntity.shaderParms[RenderWorld.SHADERPARM_BLUE] = 1.0f
                bt.renderEntity.shaderParms[RenderWorld.SHADERPARM_ALPHA] = 1.0f
                bt.renderEntity.shaderParms[RenderWorld.SHADERPARM_DIVERSITY] =
                    Game_local.gameLocal.random.CRandomFloat() * 0.75f
                bt.renderEntity.hModel = ModelManager.renderModelManager.FindModel("_beam")
                bt.renderEntity.callback = null
                bt.renderEntity.numJoints = 0
                bt.renderEntity.joints = null
                bt.renderEntity.bounds.Clear()
                bt.renderEntity.customSkin = DeclManager.declManager.FindSkin(skin)
                bt.target.oSet(ent)
                bt.modelDefHandle = Game_local.gameRenderWorld!!.AddEntityDef(bt.renderEntity)
                beamTargets.Append(bt)
            }
            if (numListedEntities != 0) {
                StartSound("snd_beam", gameSoundChannel_t.SND_CHANNEL_BODY2, 0, false)
            }
            damageFreq.set(spawnArgs.GetString("def_damageFreq"))
            nextDamageTime = Game_local.gameLocal.time + BFG_DAMAGE_FREQUENCY
            UpdateVisuals()
        }

        override fun Explode(collision: trace_s, ignore: idEntity?) {
            var i: Int
            val dmgPoint = idVec3()
            val dir = idVec3()
            val beamWidth: Float
            var damageScale: Float
            val damage: String?
            val player: idPlayer?
            val ownerEnt: idEntity?
            ownerEnt = owner.GetEntity()
            player = if (ownerEnt != null && ownerEnt is idPlayer) {
                ownerEnt
            } else {
                null
            }
            beamWidth = spawnArgs.GetFloat("beam_WidthExplode")
            damage = spawnArgs.GetString("def_damage")
            i = 0
            while (i < beamTargets.Num()) {
                if (beamTargets[i].target.GetEntity() == null || ownerEnt == null) {
                    i++
                    continue
                }
                if (!beamTargets[i].target.GetEntity()!!.CanDamage(GetPhysics().GetOrigin(), dmgPoint)) {
                    i++
                    continue
                }
                beamTargets[i].renderEntity.shaderParms[RenderWorld.SHADERPARM_BEAM_WIDTH] = beamWidth

                // if the hit entity takes damage
                damageScale = if (damagePower != 0.0f) {
                    damagePower
                } else {
                    1.0f
                }

                // if the projectile owner is a player
                if (player != null) {
                    // if the projectile hit an actor
                    if (beamTargets[i].target.GetEntity() is idActor) {
                        player.SetLastHitTime(Game_local.gameLocal.time)
                        player.AddProjectileHits(1)
                        damageScale *= player.PowerUpModifier(Player.PROJECTILE_DAMAGE)
                    }
                }
                if (!damage.isEmpty() && beamTargets[i].target.GetEntity()!!.entityNumber > Game_local.gameLocal.numClients - 1) {
                    dir.set(
                        beamTargets[i].target.GetEntity()!!.GetPhysics().GetOrigin().minus(GetPhysics().GetOrigin())
                    )
                    dir.Normalize()
                    beamTargets[i].target.GetEntity()!!.Damage(
                        this,
                        ownerEnt,
                        dir,
                        damage,
                        damageScale,
                        if (collision.c.id < 0) Clip.CLIPMODEL_ID_TO_JOINT_HANDLE(collision.c.id) else Model.INVALID_JOINT
                    )
                }
                i++
            }
            if (secondModelDefHandle >= 0) {
                Game_local.gameRenderWorld!!.FreeEntityDef(secondModelDefHandle)
                secondModelDefHandle = -1
            }
            if (ignore == null) {
                projectileFlags.noSplashDamage = true
            }
            if (!Game_local.gameLocal.isClient) {
                if (ignore != null) {
                    PostEventMS(EV_RemoveBeams, 750)
                } else {
                    PostEventMS(EV_RemoveBeams, 0)
                }
            }
            super.Explode(collision, ignore)
        }

        private fun FreeBeams() {
            for (i in 0 until beamTargets.Num()) {
                if (beamTargets[i].modelDefHandle >= 0) {
                    Game_local.gameRenderWorld!!.FreeEntityDef(beamTargets[i].modelDefHandle)
                    beamTargets[i].modelDefHandle = -1
                }
            }
            val player = Game_local.gameLocal.GetLocalPlayer()
            player?.playerView?.EnableBFGVision(false)
        }

        private fun Event_RemoveBeams() {
            FreeBeams()
            UpdateVisuals()
        }

        override fun getEventCallBack(event: idEventDef): eventCallback_t<*>? {
            return eventCallbacks[event]
        } //        private void ApplyDamage();

        //
        //
        init {
            beamTargets = List.idList()
            secondModel = renderEntity_s()
            secondModelDefHandle = -1
            nextDamageTime = 0
            damageFreq = idStr()
        }
    }

    /*
     ===============================================================================

     idDebris
	
     ===============================================================================
     */
    class idDebris : idEntity() {
        companion object {
            // CLASS_PROTOTYPE( idDebris );
            private val eventCallbacks: MutableMap<idEventDef, eventCallback_t<*>> = HashMap()
            fun getEventCallBacks(): MutableMap<idEventDef, eventCallback_t<*>> {
                return eventCallbacks
            }

            init {
                eventCallbacks.putAll(idEntity.getEventCallBacks())
                eventCallbacks[EV_Explode] =
                    eventCallback_t0<idDebris> { obj: idDebris -> obj.Event_Explode() }
                eventCallbacks[EV_Fizzle] =
                    eventCallback_t0<idDebris> { obj: idDebris -> obj.Event_Fizzle() }
            }
        }

        private val owner: idEntityPtr<idEntity?>
        private val physicsObj: idPhysics_RigidBody
        private var smokeFly: idDeclParticle?
        private var smokeFlyTime: Int
        private var sndBounce: idSoundShader?

        // ~idDebris();
        // save games
        override fun Save(savefile: idSaveGame) {                    // archives object for save game file
            owner.Save(savefile)
            savefile.WriteStaticObject(physicsObj)
            savefile.WriteParticle(smokeFly)
            savefile.WriteInt(smokeFlyTime)
            savefile.WriteSoundShader(sndBounce)
        }

        override fun Restore(savefile: idRestoreGame) {                    // unarchives object from save game file
            owner.Restore(savefile)
            savefile.ReadStaticObject(physicsObj)
            RestorePhysics(physicsObj)
            savefile.ReadParticle(smokeFly!!)
            smokeFlyTime = savefile.ReadInt()
            savefile.ReadSoundShader(sndBounce!!)
        }

        override fun Spawn() {
            super.Spawn()
            owner.oSet(null)
            smokeFly = null
            smokeFlyTime = 0
        }

        fun Create(owner: idEntity?, start: idVec3, axis: idMat3) {
            Unbind()
            GetPhysics().SetOrigin(start)
            GetPhysics().SetAxis(axis)
            GetPhysics().SetContents(0)
            this.owner.oSet(owner)
            smokeFly = null
            smokeFlyTime = 0
            sndBounce = null
            UpdateVisuals()
        }

        fun Launch() {
            var fuse: Float
            val velocity = idVec3()
            val angular_velocity = idAngles()
            val linear_friction: Float
            val angular_friction: Float
            val contact_friction: Float
            val bounce: Float
            val mass: Float
            val gravity: Float
            val gravVec = idVec3()
            val randomVelocity: Boolean
            val axis: idMat3 = idMat3()
            renderEntity!!.shaderParms[RenderWorld.SHADERPARM_TIMEOFFSET] =
                -MS2SEC(Game_local.gameLocal.time.toFloat())
            spawnArgs.GetVector("velocity", "0 0 0", velocity)
            spawnArgs.GetAngles("angular_velocity", "0 0 0", angular_velocity)
            linear_friction = spawnArgs.GetFloat("linear_friction")
            angular_friction = spawnArgs.GetFloat("angular_friction")
            contact_friction = spawnArgs.GetFloat("contact_friction")
            bounce = spawnArgs.GetFloat("bounce")
            mass = spawnArgs.GetFloat("mass")
            gravity = spawnArgs.GetFloat("gravity")
            fuse = spawnArgs.GetFloat("fuse")
            randomVelocity = spawnArgs.GetBool("random_velocity")
            if (mass <= 0) {
                idGameLocal.Error("Invalid mass on '%s'\n", GetEntityDefName())
            }
            if (randomVelocity) {
                velocity.x *= Game_local.gameLocal.random.RandomFloat() + 0.5f
                velocity.y *= Game_local.gameLocal.random.RandomFloat() + 0.5f
                velocity.z *= Game_local.gameLocal.random.RandomFloat() + 0.5f
            }
            if (health != 0) {
                fl.takedamage = true
            }
            gravVec.set(Game_local.gameLocal.GetGravity())
            gravVec.NormalizeFast()
            axis.set(GetPhysics().GetAxis())
            Unbind()
            physicsObj.SetSelf(this)

            // check if a clip model is set
            val clipModelName = idStr()
            val trm = idTraceModel()
            spawnArgs.GetString("clipmodel", "", clipModelName)
            if (clipModelName.IsEmpty()) {
                clipModelName.set(spawnArgs.GetString("model")) // use the visual model
            }

            // load the trace model
            if (!collisionModelManager.TrmFromModel(clipModelName, trm)) {
                // default to a box
                physicsObj.SetClipBox(renderEntity!!.bounds, 1.0f)
            } else {
                physicsObj.SetClipModel(idClipModel(trm), 1.0f)
            }
            physicsObj.GetClipModel()!!.SetOwner(owner.GetEntity())
            physicsObj.SetMass(mass)
            physicsObj.SetFriction(linear_friction, angular_friction, contact_friction)
            if (contact_friction == 0.0f) {
                physicsObj.NoContact()
            }
            physicsObj.SetBouncyness(bounce)
            physicsObj.SetGravity(gravVec.times(gravity))
            physicsObj.SetContents(0)
            physicsObj.SetClipMask(Game_local.MASK_SOLID or Material.CONTENTS_MOVEABLECLIP)
            physicsObj.SetLinearVelocity(
                axis[0].times(velocity[0])
                    .plus(axis[1].times(velocity[1]).plus(axis[2].times(velocity[2])))
            )
            physicsObj.SetAngularVelocity(angular_velocity.ToAngularVelocity().times(axis))
            physicsObj.SetOrigin(GetPhysics().GetOrigin())
            physicsObj.SetAxis(axis)
            SetPhysics(physicsObj)
            if (!Game_local.gameLocal.isClient) {
                if (fuse <= 0) {
                    // run physics for 1 second
                    RunPhysics()
                    PostEventMS(EV_Remove, 0)
                } else if (spawnArgs.GetBool("detonate_on_fuse")) {
                    if (fuse < 0.0f) {
                        fuse = 0.0f
                    }
                    RunPhysics()
                    PostEventSec(EV_Explode, fuse)
                } else {
                    if (fuse < 0.0f) {
                        fuse = 0.0f
                    }
                    PostEventSec(EV_Fizzle, fuse)
                }
            }
            StartSound("snd_fly", gameSoundChannel_t.SND_CHANNEL_BODY, 0, false)
            smokeFly = null
            smokeFlyTime = 0
            val smokeName = spawnArgs.GetString("smoke_fly")
            if (smokeName.isNotEmpty()) { //smokeName != '\0' ) {
                smokeFly = DeclManager.declManager.FindType(declType_t.DECL_PARTICLE, smokeName) as idDeclParticle
                smokeFlyTime = Game_local.gameLocal.time
                Game_local.gameLocal.smokeParticles!!.EmitSmoke(
                    smokeFly,
                    smokeFlyTime,
                    Game_local.gameLocal.random.CRandomFloat(),
                    GetPhysics().GetOrigin(),
                    GetPhysics().GetAxis()
                )
            }
            val sndName = spawnArgs.GetString("snd_bounce")
            if (sndName.isNotEmpty()) { //sndName != '\0' ) {
                sndBounce = DeclManager.declManager.FindSound(sndName)
            }
            UpdateVisuals()
        }

        override fun Think() {

            // run physics
            RunPhysics()
            Present()
            if (smokeFly != null && smokeFlyTime != 0) {
                if (!Game_local.gameLocal.smokeParticles!!.EmitSmoke(
                        smokeFly,
                        smokeFlyTime,
                        Game_local.gameLocal.random.CRandomFloat(),
                        GetPhysics().GetOrigin(),
                        GetPhysics().GetAxis()
                    )
                ) {
                    smokeFlyTime = 0
                }
            }
        }

        override fun Killed(inflictor: idEntity?, attacker: idEntity?, damage: Int, dir: idVec3, location: Int) {
            if (spawnArgs.GetBool("detonate_on_death")) {
                Explode()
            } else {
                Fizzle()
            }
        }

        fun Explode() {
            if (IsHidden()) {
                // already exploded
                return
            }
            StopSound(TempDump.etoi(gameSoundChannel_t.SND_CHANNEL_ANY), false)
            StartSound("snd_explode", gameSoundChannel_t.SND_CHANNEL_BODY, 0, false)
            Hide()

            // these must not be "live forever" particle systems
            smokeFly = null
            smokeFlyTime = 0
            val smokeName = spawnArgs.GetString("smoke_detonate")
            if (smokeName.isNotEmpty()) { //smokeName != '\0' ) {
                smokeFly = DeclManager.declManager.FindType(declType_t.DECL_PARTICLE, smokeName) as idDeclParticle
                smokeFlyTime = Game_local.gameLocal.time
                Game_local.gameLocal.smokeParticles!!.EmitSmoke(
                    smokeFly,
                    smokeFlyTime,
                    Game_local.gameLocal.random.CRandomFloat(),
                    GetPhysics().GetOrigin(),
                    GetPhysics().GetAxis()
                )
            }
            fl.takedamage = false
            physicsObj.SetContents(0)
            physicsObj.PutToRest()
            CancelEvents(EV_Explode)
            PostEventMS(EV_Remove, 0)
        }

        fun Fizzle() {
            if (IsHidden()) {
                // already exploded
                return
            }
            StopSound(TempDump.etoi(gameSoundChannel_t.SND_CHANNEL_ANY), false)
            StartSound("snd_fizzle", gameSoundChannel_t.SND_CHANNEL_BODY, 0, false)

            // fizzle FX
            val smokeName = spawnArgs.GetString("smoke_fuse")
            if (smokeName.isNotEmpty()) { //smokeName != '\0' ) {
                smokeFly = DeclManager.declManager.FindType(declType_t.DECL_PARTICLE, smokeName) as idDeclParticle
                smokeFlyTime = Game_local.gameLocal.time
                Game_local.gameLocal.smokeParticles!!.EmitSmoke(
                    smokeFly,
                    smokeFlyTime,
                    Game_local.gameLocal.random.CRandomFloat(),
                    GetPhysics().GetOrigin(),
                    GetPhysics().GetAxis()
                )
            }
            fl.takedamage = false
            physicsObj.SetContents(0)
            physicsObj.PutToRest()
            Hide()
            if (Game_local.gameLocal.isClient) {
                return
            }
            CancelEvents(EV_Fizzle)
            PostEventMS(EV_Remove, 0)
        }

        override fun Collide(collision: trace_s, velocity: idVec3): Boolean {
            if (sndBounce != null) {
                StartSoundShader(sndBounce, gameSoundChannel_t.SND_CHANNEL_BODY.ordinal, 0, false)
            }
            sndBounce = null
            return false
        }

        private fun Event_Explode() {
            Explode()
        }

        private fun Event_Fizzle() {
            Fizzle()
        }

        override fun CreateInstance(): idClass {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        override fun getEventCallBack(event: idEventDef): eventCallback_t<*>? {
            return eventCallbacks[event]
        }

        init {
            owner = idEntityPtr()
            physicsObj = idPhysics_RigidBody()
            smokeFly = null
            smokeFlyTime = 0
            sndBounce = null
        }
    }
}
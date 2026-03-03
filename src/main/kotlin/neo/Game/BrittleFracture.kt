/*
 * Copyright (C) 1999-2011 id Software LLC, a ZeniMax Media company.
 * Translated to Kotlin by Dr. Feederino with support of Claude Code
 *
 * This file is part of the Doom 3 Kotlin project.
 * Original source: neo/Game/BrittleFracture.cpp, neo/Game/BrittleFracture.h
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
import neo.Game.Game_local.gameSoundChannel_t
import neo.Game.Game_local.idGameLocal
import neo.Game.Physics.Clip.idClipModel
import neo.Game.Physics.Physics_RigidBody.idPhysics_RigidBody
import neo.Game.Physics.Physics_StaticMulti.idPhysics_StaticMulti
import neo.Renderer.Material
import neo.Renderer.Model.idRenderModel
import neo.Renderer.Model.modelSurface_s
import neo.Renderer.Model.srfTriangles_s
import neo.Renderer.ModelManager
import neo.Renderer.RenderWorld
import neo.Renderer.RenderWorld.deferredEntityCallback_t
import neo.Sound.snd_shader.idSoundShader
import neo.TempDump
import neo.cm.CM_CLIP_EPSILON
import neo.cm.trace_s
import neo.framework.DeclEntityDef.idDeclEntityDef
import neo.framework.DeclManager
import neo.idlib.BV.idBounds
import neo.idlib.BitMsg.idBitMsg
import neo.idlib.LittleBitField
import neo.idlib.PackColor
import neo.idlib.Text.Str.idStr
import neo.idlib.containers.CFloat
import neo.idlib.containers.CInt
import neo.idlib.containers.List.idList
import neo.idlib.geometry.DrawVert.idDrawVert
import neo.idlib.geometry.TraceModel.idTraceModel
import neo.idlib.geometry.Winding
import neo.idlib.geometry.Winding.idFixedWinding
import neo.idlib.geometry.Winding.idWinding
import neo.idlib.math.*
import neo.idlib.math.Matrix.idMat3
import java.nio.ByteBuffer
import kotlin.math.cos
import kotlin.math.sin

object BrittleFracture {
    /*
     ===============================================================================

     B-rep Brittle Fracture - Static entity using the boundary representation
     of the render model which can fracture.

     ===============================================================================
     */
    //
    const val SHARD_ALIVE_TIME = 5000
    const val SHARD_FADE_START = 2000

    //
    val brittleFracture_SnapshotName: String = "_BrittleFracture_Snapshot_"

    class shard_s {
        var atEdge = false
        var clipModel: idClipModel? = null
        val decals: idList<idFixedWinding> = idList()
        var droppedTime = -1
        val edgeHasNeighbour: idList<Boolean> = idList()
        var islandNum = 0
        val neighbours: idList<shard_s?> = idList()
        val physicsObj: idPhysics_RigidBody = idPhysics_RigidBody()
        val winding: idFixedWinding = idFixedWinding()
    }

    //
    class idBrittleFracture : idEntity() {
        companion object {
            //
            // enum {
            val EVENT_PROJECT_DECAL: Int = idEntity.EVENT_MAXEVENTS
            val EVENT_MAXEVENTS = 2 + EVENT_PROJECT_DECAL
            val EVENT_SHATTER = 1 + EVENT_PROJECT_DECAL

            // public CLASS_PROTOTYPE( idBrittleFracture );
            private val eventCallbacks: MutableMap<idEventDef, eventCallback_t<*>> = HashMap()
            fun getEventCallBacks(): MutableMap<idEventDef, eventCallback_t<*>> {
                return eventCallbacks
            }

            init {
                eventCallbacks.putAll(idEntity.getEventCallBacks())
                eventCallbacks[EV_Activate] =
                    eventCallback_t1<idBrittleFracture> { obj: idBrittleFracture, activator: idEventArg<*>? ->
                        obj.Event_Activate(activator as idEventArg<idEntity>)
                    }
                eventCallbacks[EV_Touch] =
                    eventCallback_t2<idBrittleFracture> { obj: idBrittleFracture, _other: idEventArg<*>?, _trace: idEventArg<*>? ->
                        obj.Event_Touch(_other as idEventArg<idEntity>, _trace as idEventArg<trace_s>)
                    }
            }
        }

        // };
        //        
        private var angularVelocityScale: Float
        private var bouncyness: Float
        private val bounds: idBounds
        private var changed: Boolean
        private var decalMaterial: Material.idMaterial?
        private var decalSize: Float
        private var density: Float
        private var disableFracture: Boolean
        private var friction: Float
        private val fxFracture: idStr

        //
        // for rendering
        private var lastRenderEntityUpdate: Int
        private var linearVelocityScale: Float

        //
        // setttings
        private var material: Material.idMaterial?
        private var maxShardArea: Float
        private var maxShatterRadius: Float
        private var minShatterRadius: Float

        //
        // state
        private var physicsObj: idPhysics_StaticMulti = idPhysics_StaticMulti()
        private var shardMass: Float
        private val shards: idList<shard_s?>
        override fun Save(savefile: idSaveGame) {
            var i: Int
            var j: Int
            savefile.WriteInt(health)
            val flags = fl
            LittleBitField(flags)
            savefile.Write(flags)

            // setttings
            savefile.WriteMaterial(material)
            savefile.WriteMaterial(decalMaterial)
            savefile.WriteFloat(decalSize)
            savefile.WriteFloat(maxShardArea)
            savefile.WriteFloat(maxShatterRadius)
            savefile.WriteFloat(minShatterRadius)
            savefile.WriteFloat(linearVelocityScale)
            savefile.WriteFloat(angularVelocityScale)
            savefile.WriteFloat(shardMass)
            savefile.WriteFloat(density)
            savefile.WriteFloat(friction)
            savefile.WriteFloat(bouncyness)
            savefile.WriteString(fxFracture)

            // state
            savefile.WriteBounds(bounds)
            savefile.WriteBool(disableFracture)
            savefile.WriteInt(lastRenderEntityUpdate)
            savefile.WriteBool(changed)
            savefile.WriteStaticObject(physicsObj)
            savefile.WriteInt(shards.Num())
            i = 0
            while (i < shards.Num()) {
                savefile.WriteWinding(shards[i]!!.winding)
                savefile.WriteInt(shards[i]!!.decals.Num())
                j = 0
                while (j < shards[i]!!.decals.Num()) {
                    savefile.WriteWinding(shards[i]!!.decals[j])
                    j++
                }
                savefile.WriteInt(shards[i]!!.neighbours.Num())
                j = 0
                while (j < shards[i]!!.neighbours.Num()) {
                    val index = shards.FindIndex(shards[i]!!.neighbours[j])
                    assert(index != -1)
                    savefile.WriteInt(index)
                    j++
                }
                savefile.WriteInt(shards[i]!!.edgeHasNeighbour.Num())
                j = 0
                while (j < shards[i]!!.edgeHasNeighbour.Num()) {
                    savefile.WriteBool(shards[i]!!.edgeHasNeighbour[j])
                    j++
                }
                savefile.WriteInt(shards[i]!!.droppedTime)
                savefile.WriteInt(shards[i]!!.islandNum)
                savefile.WriteBool(shards[i]!!.atEdge)
                savefile.WriteStaticObject(shards[i]!!.physicsObj)
                i++
            }
        }

        override fun Restore(savefile: idRestoreGame) {
            var i: Int
            var j: Int
            val num = CInt()
            renderEntity!!.hModel = ModelManager.renderModelManager.AllocModel()
            renderEntity!!.hModel!!.InitEmpty(brittleFracture_SnapshotName)
            renderEntity!!.callback = ModelCallback.getInstance()
            renderEntity!!.noShadow = true
            renderEntity!!.noSelfShadow = true
            renderEntity!!.noDynamicInteractions = false
            health = savefile.ReadInt()
            savefile.Read(fl)
            LittleBitField(fl)

            // setttings
            savefile.ReadMaterial(material!!)
            savefile.ReadMaterial(decalMaterial!!)
            decalSize = savefile.ReadFloat()
            maxShardArea = savefile.ReadFloat()
            maxShatterRadius = savefile.ReadFloat()
            minShatterRadius = savefile.ReadFloat()
            linearVelocityScale = savefile.ReadFloat()
            angularVelocityScale = savefile.ReadFloat()
            shardMass = savefile.ReadFloat()
            density = savefile.ReadFloat()
            friction = savefile.ReadFloat()
            bouncyness = savefile.ReadFloat()
            savefile.ReadString(fxFracture)

            // state
            savefile.ReadBounds(bounds)
            disableFracture = savefile.ReadBool()
            lastRenderEntityUpdate = savefile.ReadInt()
            changed = savefile.ReadBool()
            savefile.ReadStaticObject(physicsObj)
            RestorePhysics(physicsObj)
            savefile.ReadInt(num)
            shards.SetNum(num._val)
            i = 0
            while (i < num._val) {
                shards[i] = shard_s()
                i++
            }
            i = 0
            while (i < num._val) {
                savefile.ReadWinding(shards[i]!!.winding)
                j = savefile.ReadInt()
                shards[i]!!.decals.SetNum(j)
                j = 0
                while (j < shards[i]!!.decals.Num()) {
                    shards[i]!!.decals[j] = idFixedWinding()
                    savefile.ReadWinding(shards[i]!!.decals[j])
                    j++
                }
                j = savefile.ReadInt()
                shards[i]!!.neighbours.SetNum(j)
                j = 0
                while (j < shards[i]!!.neighbours.Num()) {
                    val index = CInt()
                    savefile.ReadInt(index)
                    assert(index._val != -1)
                    shards[i]!!.neighbours[j] = shards[index._val]
                    j++
                }
                j = savefile.ReadInt()
                shards[i]!!.edgeHasNeighbour.SetNum(j)
                j = 0
                while (j < shards[i]!!.edgeHasNeighbour.Num()) {
                    shards[i]!!.edgeHasNeighbour[j] = savefile.ReadBool()
                    j++
                }
                shards[i]!!.droppedTime = savefile.ReadInt()
                shards[i]!!.islandNum = savefile.ReadInt()
                shards[i]!!.atEdge = savefile.ReadBool()
                savefile.ReadStaticObject(shards[i]!!.physicsObj)
                if (shards[i]!!.droppedTime < 0) {
                    shards[i]!!.clipModel = physicsObj.GetClipModel(i)!!
                } else {
                    shards[i]!!.clipModel = shards[i]!!.physicsObj.GetClipModel()!!
                }
                i++
            }
        }

        override fun Spawn() {
            super.Spawn()
            val d = CFloat()
            val f = CFloat()
            val b = CFloat()

            // get shard properties
            decalMaterial = DeclManager.declManager.FindMaterial(spawnArgs.GetString("mtr_decal"))
            decalSize = spawnArgs.GetFloat("decalSize", "40")
            maxShardArea = spawnArgs.GetFloat("maxShardArea", "200")
            maxShardArea = idMath.ClampFloat(100.0f, 10000.0f, maxShardArea)
            maxShatterRadius = spawnArgs.GetFloat("maxShatterRadius", "40")
            minShatterRadius = spawnArgs.GetFloat("minShatterRadius", "10")
            linearVelocityScale = spawnArgs.GetFloat("linearVelocityScale", "0.1f")
            angularVelocityScale = spawnArgs.GetFloat("angularVelocityScale", "40")
            fxFracture.set(spawnArgs.GetString("fx"))

            // get rigid body properties
            shardMass = spawnArgs.GetFloat("shardMass", "20")
            shardMass = idMath.ClampFloat(0.001f, 1000.0f, shardMass)
            spawnArgs.GetFloat("density", "0.1f", d)
            density = idMath.ClampFloat(0.001f, 1000.0f, d._val)
            spawnArgs.GetFloat("friction", "0.4", f)
            friction = idMath.ClampFloat(0.0f, 1.0f, f._val)
            spawnArgs.GetFloat("bouncyness", "0.01", b)
            bouncyness = idMath.ClampFloat(0.0f, 1.0f, b._val)
            disableFracture = spawnArgs.GetBool("disableFracture", "0")
            health = spawnArgs.GetInt("health", "40")
            fl.takedamage = true

            // FIXME: set "bleed" so idProjectile calls AddDamageEffect
            spawnArgs.SetBool("bleed", true)
            CreateFractures(renderEntity!!.hModel)
            FindNeighbours()
            renderEntity!!.hModel = ModelManager.renderModelManager.AllocModel()
            renderEntity!!.hModel!!.InitEmpty(brittleFracture_SnapshotName)
            renderEntity!!.callback = ModelCallback.getInstance()
            renderEntity!!.noShadow = true
            renderEntity!!.noSelfShadow = true
            renderEntity!!.noDynamicInteractions = false
        }

        override fun Present() {

            // don't present to the renderer if the entity hasn't changed
            if (0 == (thinkFlags and TH_UPDATEVISUALS)) {
                return
            }
            BecomeInactive(TH_UPDATEVISUALS)
            renderEntity!!.bounds.set(bounds)
            renderEntity!!.origin.Zero()
            renderEntity!!.axis.Identity()

            // force an update because the bounds/origin/axis may stay the same while the model changes
            renderEntity!!.forceUpdate = 1 //true;

            // add to refresh list
            if (modelDefHandle == -1) {
                modelDefHandle = Game_local.gameRenderWorld!!.AddEntityDef(renderEntity!!)
            } else {
                Game_local.gameRenderWorld!!.UpdateEntityDef(modelDefHandle, renderEntity!!)
            }
            changed = true
        }

        override fun Think() {
            var i: Int
            val startTime: Int
            val endTime: Int
            var droppedTime: Int
            var shard: shard_s?
            var atRest = true
            var fading = false

            // remove overdue shards
            i = 0
            while (i < shards.Num()) {
                droppedTime = shards[i]!!.droppedTime
                if (droppedTime != -1) {
                    if (Game_local.gameLocal.time - droppedTime > SHARD_ALIVE_TIME) {
                        RemoveShard(i)
                        i--
                    }
                    fading = true
                }
                i++
            }

            // remove the entity when nothing is visible
            if (0 == shards.Num()) {
                PostEventMS(EV_Remove, 0)
                return
            }
            if ((thinkFlags and TH_PHYSICS) != 0) {
                startTime = Game_local.gameLocal.previousTime
                endTime = Game_local.gameLocal.time

                // run physics on shards
                i = 0
                while (i < shards.Num()) {
                    shard = shards[i]!!
                    if (shard.droppedTime == -1) {
                        i++
                        continue
                    }
                    shard.physicsObj.Evaluate(endTime - startTime, endTime)
                    if (!shard.physicsObj.IsAtRest()) {
                        atRest = false
                    }
                    i++
                }
                if (atRest) {
                    BecomeInactive(TH_PHYSICS)
                } else {
                    BecomeActive(TH_PHYSICS)
                }
            }
            if (!atRest || bounds.IsCleared()) {
                bounds.Clear()
                i = 0
                while (i < shards.Num()) {
                    bounds.AddBounds(shards[i]!!.clipModel!!.GetAbsBounds())
                    i++
                }
            }
            if (fading) {
                BecomeActive(TH_UPDATEVISUALS or TH_THINK)
            } else {
                BecomeInactive(TH_THINK)
            }
            RunPhysics()
            Present()
        }

        override fun ApplyImpulse(ent: idEntity?, id: Int, point: idVec3, impulse: idVec3) {
            if (id < 0 || id >= shards.Num()) {
                return
            }
            if (shards[id]!!.droppedTime != -1) {
                shards[id]!!.physicsObj.ApplyImpulse(0, point, impulse)
            } else if (health <= 0 && !disableFracture) {
                Shatter(point, impulse, Game_local.gameLocal.time)
            }
        }

        override fun AddForce(ent: idEntity?, id: Int, point: idVec3, force: idVec3) {
            if (id < 0 || id >= shards.Num()) {
                return
            }
            if (shards[id]!!.droppedTime != -1) {
                shards[id]!!.physicsObj.AddForce(0, point, force)
            } else if (health <= 0 && !disableFracture) {
                Shatter(point, force, Game_local.gameLocal.time)
            }
        }

        override fun AddDamageEffect(collision: trace_s, velocity: idVec3, damageDefName: String) {
            if (!disableFracture) {
                ProjectDecal(collision.c.point, collision.c.normal, Game_local.gameLocal.time, damageDefName)
            }
        }

        override fun Killed(inflictor: idEntity?, attacker: idEntity?, damage: Int, dir: idVec3, location: Int) {
            if (!disableFracture) {
                ActivateTargets(this)
                Break()
            }
        }

        fun ProjectDecal(point: idVec3, dir: idVec3, time: Int, damageDefName: String?) {
            var i: Int
            var j: Int
            var bits: Int
            var clipBits: Int
            val a: Float
            val c: Float
            val s: Float
            val st: Array<idVec2> = idVec2.generateArray(Winding.MAX_POINTS_ON_WINDING)
            val origin = idVec3()
            val axis: idMat3 = idMat3()
            val axisTemp = idMat3()
            val textureAxis: Array<idPlane> = idPlane.generateArray(2)
            if (Game_local.gameLocal.isServer) {
                val msg = idBitMsg()
                val msgBuf = ByteBuffer.allocate(Game_local.MAX_EVENT_PARAM_SIZE)
                msg.Init(msgBuf, Game_local.MAX_EVENT_PARAM_SIZE)
                msg.BeginWriting()
                msg.WriteFloat(point[0])
                msg.WriteFloat(point[1])
                msg.WriteFloat(point[2])
                msg.WriteFloat(dir[0])
                msg.WriteFloat(dir[1])
                msg.WriteFloat(dir[2])
                ServerSendEvent(EVENT_PROJECT_DECAL, msg, true, -1)
            }
            if (time >= Game_local.gameLocal.time) {
                // try to get the sound from the damage def
                var damageDef: idDeclEntityDef? = null
                var sndShader: idSoundShader? = null
                if (damageDefName != null) {
                    damageDef = Game_local.gameLocal.FindEntityDef(damageDefName, false)
                    if (damageDef != null) {
                        sndShader = DeclManager.declManager.FindSound(damageDef.dict.GetString("snd_shatter", "")!!)
                    }
                }
                if (sndShader != null) {
                    StartSoundShader(sndShader, TempDump.etoi(gameSoundChannel_t.SND_CHANNEL_ANY), 0, false)
                } else {
                    StartSound("snd_bullethole", gameSoundChannel_t.SND_CHANNEL_ANY, 0, false)
                }
            }
            a = Game_local.gameLocal.random.RandomFloat() * idMath.TWO_PI
            c = cos(a)
            s = -sin(a)
            axis[2] = dir.unaryMinus()
            axis[2].Normalize()
            axis[2].NormalVectors(axisTemp[0], axisTemp[1])
            axis[0] = axisTemp[0].times(c).plus(axisTemp[1].times(s))
            axis[1] = axisTemp[0].times(s).plus(axisTemp[1].times(-c))
            textureAxis[0].set(axis[0].times(1.0f / decalSize))
            textureAxis[0][3] = -point.times(textureAxis[0].Normal()) + 0.5f
            textureAxis[1].set(axis[1].times(1.0f / decalSize))
            textureAxis[1][3] = -point.times(textureAxis[1].Normal()) + 0.5f
            i = 0
            while (i < shards.Num()) {
                val winding = shards[i]!!.winding
                origin.set(shards[i]!!.clipModel!!.GetOrigin())
                axis.set(shards[i]!!.clipModel!!.GetAxis())
                var d0: Float
                var d1: Float
                clipBits = -1
                j = 0
                while (j < winding.GetNumPoints()) {
                    val p = idVec3(origin.plus(winding[j].ToVec3().times(axis)))
                    d0 = textureAxis[0].Distance(p)
                    st[j].x = d0
                    d1 = textureAxis[1].Distance(p)
                    st[j].y = d1
                    bits = FLOATSIGNBITSET(d0)
                    d0 = 1.0f - d0
                    bits = bits or (FLOATSIGNBITSET(d1) shl 2)
                    d1 = 1.0f - d1
                    bits = bits or (FLOATSIGNBITSET(d0) shl 1)
                    bits = bits or (FLOATSIGNBITSET(d1) shl 3)
                    clipBits = clipBits and bits
                    j++
                }
                if (clipBits != 0) {
                    i++
                    continue
                }
                val decal = idFixedWinding()
                shards[i]!!.decals.Append(decal)
                decal.SetNumPoints(winding.GetNumPoints())
                j = 0
                while (j < winding.GetNumPoints()) {
                    decal[j].set(winding[j].ToVec3())
                    decal[j].s = st[j].x
                    decal[j].t = st[j].y
                    j++
                }
                i++
            }
            BecomeActive(TH_UPDATEVISUALS)
        }

        fun IsBroken(): Boolean {
            return fl.takedamage == false
        }

        override fun ClientPredictionThink() {
            // only think forward because the state is not synced through snapshots
            if (!Game_local.gameLocal.isNewFrame) {
                return
            }
            Think()
        }

        override fun ClientReceiveEvent(event: Int, time: Int, msg: idBitMsg): Boolean {
            val point = idVec3()
            val dir = idVec3()
            return when (event) {
                EVENT_PROJECT_DECAL -> {
                    point[0] = msg.ReadFloat()
                    point[1] = msg.ReadFloat()
                    point[2] = msg.ReadFloat()
                    dir[0] = msg.ReadFloat()
                    dir[1] = msg.ReadFloat()
                    dir[2] = msg.ReadFloat()
                    ProjectDecal(point, dir, time, null)
                    true
                }

                EVENT_SHATTER -> {
                    point[0] = msg.ReadFloat()
                    point[1] = msg.ReadFloat()
                    point[2] = msg.ReadFloat()
                    dir[0] = msg.ReadFloat()
                    dir[1] = msg.ReadFloat()
                    dir[2] = msg.ReadFloat()
                    Shatter(point, dir, time)
                    true
                }

                else -> {
                    super.ClientReceiveEvent(event, time, msg)
                }
            }
        }

        override fun UpdateRenderEntity(
            renderEntity: RenderWorld.renderEntity_s,
            renderView: RenderWorld.renderView_s?
        ): Boolean {
            var i: Int
            var j: Int
            var k: Int
            var n: Int
            var msec: Int
            var numTris: Int
            var numDecalTris: Int
            var fade: Float
            var   /*dword*/packedColor: Int
            val tris: srfTriangles_s
            val decalTris: srfTriangles_s?
            var surface: modelSurface_s
            var v: idDrawVert?
            val plane = idPlane()
            val tangents: idMat3 = idMat3()

            // this may be triggered by a model trace or other non-view related source,
            // to which we should look like an empty model
            if (null == renderView) {
                return false
            }

            // don't regenerate it if it is current
            if (lastRenderEntityUpdate == Game_local.gameLocal.time || !changed) {
                return false
            }
            lastRenderEntityUpdate = Game_local.gameLocal.time
            changed = false
            numTris = 0
            numDecalTris = 0
            i = 0
            while (i < shards.Num()) {
                n = shards[i]!!.winding.GetNumPoints()
                if (n > 2) {
                    numTris += n - 2
                }
                k = 0
                while (k < shards[i]!!.decals.Num()) {
                    n = shards[i]!!.decals[k].GetNumPoints()
                    if (n > 2) {
                        numDecalTris += n - 2
                    }
                    k++
                }
                i++
            }

            // FIXME: re-use model surfaces
            renderEntity.hModel!!.InitEmpty(brittleFracture_SnapshotName)

            // allocate triangle surfaces for the fractures and decals
            tris = renderEntity.hModel!!.AllocSurfaceTriangles(
                numTris * 3,
                if (material!!.ShouldCreateBackSides()) numTris * 6 else numTris * 3
            )
            decalTris = renderEntity.hModel!!.AllocSurfaceTriangles(
                numDecalTris * 3,
                if (decalMaterial!!.ShouldCreateBackSides()) numDecalTris * 6 else numDecalTris * 3
            )
            i = 0
            while (i < shards.Num()) {
                val origin = shards[i]!!.clipModel!!.GetOrigin()
                val axis = shards[i]!!.clipModel!!.GetAxis()
                fade = 1.0f
                if (shards[i]!!.droppedTime >= 0) {
                    msec = Game_local.gameLocal.time - shards[i]!!.droppedTime - SHARD_FADE_START
                    if (msec > 0) {
                        fade =
                            1.0f - msec.toFloat() / (SHARD_ALIVE_TIME - SHARD_FADE_START)
                    }
                }
                packedColor = PackColor(
                    idVec4(
                        renderEntity.shaderParms[RenderWorld.SHADERPARM_RED] * fade,
                        renderEntity.shaderParms[RenderWorld.SHADERPARM_GREEN] * fade,
                        renderEntity.shaderParms[RenderWorld.SHADERPARM_BLUE] * fade,
                        fade
                    )
                ).toInt()
                val winding: idWinding = shards[i]!!.winding
                winding.GetPlane(plane)
                tangents.set(plane.Normal().times(axis).ToMat3())
                j = 2
                while (j < winding.GetNumPoints()) {
                    v = tris.verts!![tris.numVerts++]!!
                    v.Clear()
                    v.xyz.set(origin.plus(winding[0].ToVec3().times(axis)))
                    v.st[0] = winding[0].s
                    v.st[1] = winding[0].t
                    v.normal.set(tangents[0])
                    v.tangents[0].set(tangents[1])
                    v.tangents[1].set(tangents[2])
                    v.SetColor(packedColor)
                    v = tris.verts!![tris.numVerts++]!!
                    v.Clear()
                    v.xyz.set(origin.plus(winding[j - 1].ToVec3().times(axis)))
                    v.st[0] = winding[j - 1].s
                    v.st[1] = winding[j - 1].t
                    v.normal.set(tangents[0])
                    v.tangents[0].set(tangents[1])
                    v.tangents[1].set(tangents[2])
                    v.SetColor(packedColor)
                    v = tris.verts!![tris.numVerts++]!!
                    v.Clear()
                    v.xyz.set(origin.plus(winding[j].ToVec3().times(axis)))
                    v.st[0] = winding[j].s
                    v.st[1] = winding[j].t
                    v.normal.set(tangents[0])
                    v.tangents[0].set(tangents[1])
                    v.tangents[1].set(tangents[2])
                    v.SetColor(packedColor)
                    tris.indexes!![tris.numIndexes++] = tris.numVerts - 3
                    tris.indexes!![tris.numIndexes++] = tris.numVerts - 2
                    tris.indexes!![tris.numIndexes++] = tris.numVerts - 1
                    if (material!!.ShouldCreateBackSides()) {
                        tris.indexes!![tris.numIndexes++] = tris.numVerts - 2
                        tris.indexes!![tris.numIndexes++] = tris.numVerts - 3
                        tris.indexes!![tris.numIndexes++] = tris.numVerts - 1
                    }
                    j++
                }
                k = 0
                while (k < shards[i]!!.decals.Num()) {
                    val decalWinding: idWinding = shards[i]!!.decals[k]
                    j = 2
                    while (j < decalWinding.GetNumPoints()) {
                        v = decalTris.verts!![decalTris.numVerts++]!!
                        v.Clear()
                        v.xyz.set(origin.plus(decalWinding[0].ToVec3().times(axis)))
                        v.st[0] = decalWinding[0].s
                        v.st[1] = decalWinding[0].t
                        v.normal.set(tangents[0])
                        v.tangents[0].set(tangents[1])
                        v.tangents[1].set(tangents[2])
                        v.SetColor(packedColor)
                        v = decalTris.verts!![decalTris.numVerts++]!!
                        v.Clear()
                        v.xyz.set(origin.plus(decalWinding[j - 1].ToVec3().times(axis)))
                        v.st[0] = decalWinding[j - 1].s
                        v.st[1] = decalWinding[j - 1].t
                        v.normal.set(tangents[0])
                        v.tangents[0].set(tangents[1])
                        v.tangents[1].set(tangents[2])
                        v.SetColor(packedColor)
                        v = decalTris.verts!![decalTris.numVerts++]!!
                        v.Clear()
                        v.xyz.set(origin.plus(decalWinding[j].ToVec3().times(axis)))
                        v.st[0] = decalWinding[j].s
                        v.st[1] = decalWinding[j].t
                        v.normal.set(tangents[0])
                        v.tangents[0].set(tangents[1])
                        v.tangents[1].set(tangents[2])
                        v.SetColor(packedColor)
                        decalTris.indexes!![decalTris.numIndexes++] = decalTris.numVerts - 3
                        decalTris.indexes!![decalTris.numIndexes++] = decalTris.numVerts - 2
                        decalTris.indexes!![decalTris.numIndexes++] = decalTris.numVerts - 1
                        if (decalMaterial!!.ShouldCreateBackSides()) {
                            decalTris.indexes!![decalTris.numIndexes++] = decalTris.numVerts - 2
                            decalTris.indexes!![decalTris.numIndexes++] = decalTris.numVerts - 3
                            decalTris.indexes!![decalTris.numIndexes++] = decalTris.numVerts - 1
                        }
                        j++
                    }
                    k++
                }
                i++
            }
            tris.tangentsCalculated = true
            decalTris.tangentsCalculated = true
            SIMDProcessor!!.MinMax(tris.bounds[0], tris.bounds[1], tris.verts!! as Array<idDrawVert>, tris.numVerts)
            SIMDProcessor!!.MinMax(
                decalTris.bounds[0],
                decalTris.bounds[1],
                decalTris.verts!! as Array<idDrawVert>,
                decalTris.numVerts
            )

            // C++: memset( &surface, 0, sizeof( surface ) );
            surface = modelSurface_s()
            surface.shader = material
            surface.id = 0
            surface.geometry = tris
            renderEntity.hModel!!.AddSurface(surface)

            // C++: memset( &surface, 0, sizeof( surface ) );
            surface = modelSurface_s()
            surface.shader = decalMaterial
            surface.id = 1
            surface.geometry = decalTris
            renderEntity.hModel!!.AddSurface(surface)
            return true
        }

        private fun AddShard(clipModel: idClipModel, w: idFixedWinding) {
            val shard = shard_s()
            shard.clipModel = clipModel
            shard.droppedTime = -1
            shard.winding.set(w)
            shard.decals.Clear()
            shard.edgeHasNeighbour.AssureSize(w.GetNumPoints(), false)
            shard.neighbours.Clear()
            shard.atEdge = false
            shards.Append(shard)
        }

        private fun RemoveShard(index: Int) {
            var i: Int

            shards[index] = null
            shards.RemoveIndex(index)
            physicsObj.RemoveIndex(index)
            i = index
            while (i < shards.Num()) {
                shards[i]!!.clipModel!!.SetId(i)
                i++
            }
        }

        private fun DropShard(shard: shard_s, point: idVec3, dir: idVec3, impulse: Float, time: Int) {
            var i: Int
            var j: Int
            val clipModelId: Int
            val dist: Float
            val f: Float
            val dir2 = idVec3()
            val origin = idVec3()
            val axis: idMat3
            var neighbour: shard_s?

            // don't display decals on dropped shards
            shard.decals.DeleteContents(true)

            // remove neighbour pointers of neighbours pointing to this shard
            i = 0
            while (i < shard.neighbours.Num()) {
                neighbour = shard.neighbours[i]
                j = 0
                while (j < neighbour!!.neighbours.Num()) {
                    if (neighbour.neighbours[j] == shard) {
                        neighbour.neighbours.RemoveIndex(j)
                        break
                    }
                    j++
                }
                i++
            }

            // remove neighbour pointers
            shard.neighbours.Clear()

            // remove the clip model from the static physics object
            clipModelId = shard.clipModel!!.GetId()
            physicsObj.SetClipModel(null, 1.0f, clipModelId, false)
            origin.set(shard.clipModel!!.GetOrigin())
            axis = shard.clipModel!!.GetAxis()

            // set the dropped time for fading
            shard.droppedTime = time
            dir2.set(origin.minus(point))
            dist = dir2.Normalize()
            f = if (dist > maxShatterRadius) 1.0f else idMath.Sqrt(dist - minShatterRadius) * (1.0f / idMath.Sqrt(
                maxShatterRadius - minShatterRadius
            ))

            // setup the physics
            shard.physicsObj.SetSelf(this)
            shard.physicsObj.SetClipModel(shard.clipModel, density)
            shard.physicsObj.SetMass(shardMass)
            shard.physicsObj.SetOrigin(origin)
            shard.physicsObj.SetAxis(axis)
            shard.physicsObj.SetBouncyness(bouncyness)
            shard.physicsObj.SetFriction(0.6f, 0.6f, friction)
            shard.physicsObj.SetGravity(Game_local.gameLocal.GetGravity())
            shard.physicsObj.SetContents(Material.CONTENTS_RENDERMODEL)
            shard.physicsObj.SetClipMask(Game_local.MASK_SOLID or Material.CONTENTS_MOVEABLECLIP)
            shard.physicsObj.ApplyImpulse(0, origin, dir.times(impulse * linearVelocityScale))
            shard.physicsObj.SetAngularVelocity(dir.Cross(dir2).times(f * angularVelocityScale))
            shard.clipModel!!.SetId(clipModelId)
            BecomeActive(TH_PHYSICS)
        }

        private fun Shatter(point: idVec3, impulse: idVec3, time: Int) {
            var i: Int
            val dir = idVec3()
            var shard: shard_s?
            val m: Float
            if (Game_local.gameLocal.isServer) {
                val msg = idBitMsg()
                val msgBuf = ByteBuffer.allocate(Game_local.MAX_EVENT_PARAM_SIZE)
                msg.Init(msgBuf, Game_local.MAX_EVENT_PARAM_SIZE)
                msg.BeginWriting()
                msg.WriteFloat(point[0])
                msg.WriteFloat(point[1])
                msg.WriteFloat(point[2])
                msg.WriteFloat(impulse[0])
                msg.WriteFloat(impulse[1])
                msg.WriteFloat(impulse[2])
                ServerSendEvent(EVENT_SHATTER, msg, true, -1)
            }
            if (time > Game_local.gameLocal.time - SHARD_ALIVE_TIME) {
                StartSound("snd_shatter", gameSoundChannel_t.SND_CHANNEL_ANY, 0, false)
            }
            if (!IsBroken()) {
                Break()
            }
            if (fxFracture.Length() != 0) {
                idEntityFx.StartFx(fxFracture, point, GetPhysics().GetAxis(), this, true)
            }
            dir.set(impulse)
            m = dir.Normalize()
            i = 0
            while (i < shards.Num()) {
                shard = shards[i]!!
                if (shard.droppedTime != -1) {
                    i++
                    continue
                }
                if (shard.clipModel!!.GetOrigin().minus(point).LengthSqr() > Square(maxShatterRadius)) {
                    i++
                    continue
                }
                DropShard(shard, point, dir, m, time)
                i++
            }
            DropFloatingIslands(point, impulse, time)
        }

        private fun DropFloatingIslands(point: idVec3, impulse: idVec3, time: Int) {
            var i: Int
            var j: Int
            var numIslands: Int
            var queueStart: Int
            var queueEnd: Int
            var curShard: shard_s
            var nextShard: shard_s?
            val queue: Array<shard_s?>
            var touchesEdge: Boolean
            val dir = idVec3()
            dir.set(impulse)
            dir.Normalize()
            numIslands = 0
            queue = arrayOfNulls(shards.Num())
            i = 0
            while (i < shards.Num()) {
                shards[i]!!.islandNum = 0
                i++
            }
            i = 0
            while (i < shards.Num()) {
                if (shards[i]!!.droppedTime != -1) {
                    i++
                    continue
                }
                if (shards[i]!!.islandNum != 0) {
                    i++
                    continue
                }
                queueStart = 0
                queueEnd = 1
                queue[0] = shards[i]!!
                shards[i]!!.islandNum = numIslands + 1
                touchesEdge = shards[i]!!.atEdge
                while (queueStart < queueEnd) {
                    curShard = queue[queueStart]!!
                    j = 0
                    while (j < curShard.neighbours.Num()) {
                        nextShard = curShard.neighbours[j]!!
                        if (nextShard.droppedTime != -1) {
                            j++; continue
                        }
                        if (nextShard.islandNum != 0) {
                            j++
                            continue
                        }
                        queue[queueEnd++] = nextShard
                        nextShard.islandNum = numIslands + 1
                        if (nextShard.atEdge) {
                            touchesEdge = true
                        }
                        j++
                    }
                    queueStart++
                }
                numIslands++

                // if the island is not connected to the world at any edges
                if (!touchesEdge) {
                    j = 0
                    while (j < queueEnd) {
                        DropShard(queue[j]!!, point, dir, 0.0f, time)
                        j++
                    }
                }
                i++
            }
        }

        private fun Break() {
            fl.takedamage = false
            physicsObj.SetContents(Material.CONTENTS_RENDERMODEL or Material.CONTENTS_TRIGGER)
        }

        private fun Fracture_r(w: idFixedWinding) {
            var i: Int
            var j: Int
            var bestPlane: Int
            var a: Float
            var c: Float
            var s: Float
            var dist: Float
            var bestDist: Float
            val origin = idVec3()
            val windingPlane = idPlane()
            val splitPlanes: Array<idPlane> = idPlane.generateArray(2)
            val axis = idMat3()
            val axistemp = idMat3()
            val back = idFixedWinding()
            val trm = idTraceModel()
            val clipModel: idClipModel
            while (true) {
                origin.set(w.GetCenter())
                w.GetPlane(windingPlane)
                if (w.GetArea() < maxShardArea) {
                    break
                }

                // randomly create a split plane
                a = Game_local.gameLocal.random.RandomFloat() * idMath.TWO_PI
                c = cos(a)
                s = -sin(a)
                axis[2] = windingPlane.Normal()
                axis[2].NormalVectors(axistemp[0], axistemp[1])
                axis[0] = axistemp[0].times(c).plus(axistemp[1].times(s))
                axis[1] = axistemp[0].times(s).plus(axistemp[1].times(-c))

                // get the best split plane
                bestDist = 0.0f
                bestPlane = 0
                i = 0
                while (i < 2) {
                    splitPlanes[i].SetNormal(axis[i])
                    splitPlanes[i].FitThroughPoint(origin)
                    j = 0
                    while (j < w.GetNumPoints()) {
                        dist = splitPlanes[i].Distance(w[j].ToVec3())
                        if (dist > bestDist) {
                            bestDist = dist
                            bestPlane = i
                        }
                        j++
                    }
                    i++
                }

                // split the winding
                if (0 == w.Split(back, splitPlanes[bestPlane])) {
                    break
                }

                // recursively create shards for the back winding
                Fracture_r(back)
            }

            // translate the winding to it's center
            origin.set(w.GetCenter())
            j = 0
            while (j < w.GetNumPoints()) {
                w[j][0] = w[j][0] - origin[0]
                w[j][1] = w[j][1] - origin[1]
                w[j][2] = w[j][2] - origin[2]
                j++
            }
            w.RemoveEqualPoints()
            trm.SetupPolygon(w)
            trm.Shrink(CM_CLIP_EPSILON)
            clipModel = idClipModel(trm)
            physicsObj.SetClipModel(clipModel, 1.0f, shards.Num())
            physicsObj.SetOrigin(GetPhysics().GetOrigin().plus(origin), shards.Num())
            physicsObj.SetAxis(GetPhysics().GetAxis(), shards.Num())
            AddShard(clipModel, w)
        }

        private fun CreateFractures(renderModel: idRenderModel?) {
            var i: Int
            var j: Int
            var k: Int
            var surf: modelSurface_s?
            var v: idDrawVert?
            val w = idFixedWinding()
            if (null == renderModel) {
                return
            }
            physicsObj.SetSelf(this)
            physicsObj.SetOrigin(GetPhysics().GetOrigin(), 0)
            physicsObj.SetAxis(GetPhysics().GetAxis(), 0)
            i = 0
            while (i < 1 /*renderModel.NumSurfaces()*/) {
                surf = renderModel.Surface(i)
                material = surf!!.shader
                j = 0
                while (j < surf.geometry!!.numIndexes) {
                    w.Clear()
                    k = 0
                    while (k < 3) {
                        v = surf.geometry!!.verts!![surf.geometry!!.indexes!![j + 2 - k]]!!
                        w.AddPoint(v.xyz)
                        w[k].s = v.st[0]
                        w[k].t = v.st[1]
                        k++
                    }
                    Fracture_r(w)
                    j += 3
                }
                i++
            }
            physicsObj.SetContents(material!!.GetContentFlags())
            SetPhysics(physicsObj)
        }

        private fun FindNeighbours() {
            var i: Int
            var j: Int
            var k: Int
            var l: Int
            val p1 = idVec3()
            val p2 = idVec3()
            val dir = idVec3()
            val axis: idMat3 = idMat3()
            val plane: Array<idPlane> = idPlane.generateArray(4)
            i = 0
            while (i < shards.Num()) {
                val shard1 = shards[i]!!
                val w1: idWinding = shard1.winding
                val origin1 = shard1.clipModel!!.GetOrigin()
                val axis1 = shard1.clipModel!!.GetAxis()
                k = 0
                while (k < w1.GetNumPoints()) {
                    p1.set(origin1.plus(w1[k].ToVec3().times(axis1)))
                    p2.set(origin1.plus(w1[(k + 1) % w1.GetNumPoints()].ToVec3().times(axis1)))
                    dir.set(p2.minus(p1))
                    dir.Normalize()
                    axis.set(dir.ToMat3())
                    plane[0].SetNormal(dir)
                    plane[0].FitThroughPoint(p1)
                    plane[1].SetNormal(dir.unaryMinus())
                    plane[1].FitThroughPoint(p2)
                    plane[2].SetNormal(axis[1])
                    plane[2].FitThroughPoint(p1)
                    plane[3].SetNormal(axis[2])
                    plane[3].FitThroughPoint(p1)
                    j = 0
                    while (j < shards.Num()) {
                        if (i == j) {
                            j++
                            continue
                        }
                        val shard2 = shards[j]!!
                        l = 0
                        while (l < shard1.neighbours.Num()) {
                            if (shard1.neighbours[l] == shard2) {
                                break
                            }
                            l++
                        }
                        if (l < shard1.neighbours.Num()) {
                            j++
                            continue
                        }
                        val w2: idWinding = shard2.winding
                        val origin2 = shard2.clipModel!!.GetOrigin()
                        val axis2 = shard2.clipModel!!.GetAxis()
                        l = w2.GetNumPoints() - 1
                        while (l >= 0) {
                            p1.set(origin2.plus(w2[l].ToVec3().times(axis2)))
                            p2.set(
                                origin2.plus(
                                    w2[(l - 1 + w2.GetNumPoints()) % w2.GetNumPoints()].ToVec3().times(axis2)
                                )
                            )
                            if (plane[0].Side(p2, 0.1f) == SIDE_FRONT && plane[1].Side(
                                    p1,
                                    0.1f
                                ) == SIDE_FRONT
                            ) {
                                if (plane[2].Side(p1, 0.1f) == SIDE_ON && plane[3].Side(
                                        p1,
                                        0.1f
                                    ) == SIDE_ON
                                ) {
                                    if (plane[2].Side(p2, 0.1f) == SIDE_ON && plane[3].Side(
                                            p2,
                                            0.1f
                                        ) == SIDE_ON
                                    ) {
                                        shard1.neighbours.Append(shard2)
                                        shard1.edgeHasNeighbour[k] = true
                                        shard2.neighbours.Append(shard1)
                                        shard2.edgeHasNeighbour[(l - 1 + w2.GetNumPoints()) % w2.GetNumPoints()] = true
                                        break
                                    }
                                }
                            }
                            l--
                        }
                        j++
                    }
                    k++
                }
                k = 0
                while (k < w1.GetNumPoints()) {
                    if (!shard1.edgeHasNeighbour[k]) {
                        break
                    }
                    k++
                }
                shard1.atEdge = k < w1.GetNumPoints()
                i++
            }
        }

        private fun Event_Activate(activator: idEventArg<idEntity>) {
            disableFracture = false
            if (health <= 0) {
                Break()
            }
        }

        private fun Event_Touch(_other: idEventArg<idEntity>, _trace: idEventArg<trace_s>) {
            val other = _other.value
            val trace = _trace.value
            val point = idVec3()
            val impulse = idVec3()
            if (!IsBroken()) {
                return
            }
            if (trace.c.id < 0 || trace.c.id >= shards.Num()) {
                return
            }
            point.set(shards[trace.c.id]!!.clipModel!!.GetOrigin())
            impulse.set(other.GetPhysics().GetLinearVelocity().times(other.GetPhysics().GetMass()))
            Shatter(point, impulse, Game_local.gameLocal.time)
        }

        override fun getEventCallBack(event: idEventDef): eventCallback_t<*>? {
            return eventCallbacks[event]
        }

        // virtual ~idBrittleFracture( void );
        override fun _deconstructor() {
            var i: Int
            i = 0
            while (i < shards.Num()) {
                shards[i]!!.decals.DeleteContents(true)
                shards[i] = null
                i++
            }

            // make sure the render entity is freed before the model is freed
            FreeModelDef()
            ModelManager.renderModelManager.FreeModel(renderEntity!!.hModel!!)
            super._deconstructor()
        }

        class ModelCallback private constructor() : deferredEntityCallback_t() {
            override fun run(e: RenderWorld.renderEntity_s?, v: RenderWorld.renderView_s?): Boolean {
                val ent: idBrittleFracture?
                ent = Game_local.gameLocal.entities[e!!.entityNum] as idBrittleFracture?
                if (null == ent) {
                    idGameLocal.Error("idBrittleFracture::ModelCallback: callback with NULL game entity")
                }
                return ent!!.UpdateRenderEntity(e, v)
            }

            override fun AllocBuffer(): ByteBuffer {
                throw UnsupportedOperationException("Not supported for ModelCallback")
            }

            override fun Read(buffer: ByteBuffer) {
                throw UnsupportedOperationException("Not supported for ModelCallback")
            }

            override fun Write(): ByteBuffer {
                throw UnsupportedOperationException("Not supported for ModelCallback")
            }

            companion object {
                private val instance: deferredEntityCallback_t = ModelCallback()
                fun getInstance(): deferredEntityCallback_t {
                    return instance
                }
            }
        }

        //
        //
        init {
            material = null
            decalMaterial = null
            decalSize = 0.0f
            maxShardArea = 0.0f
            maxShatterRadius = 0.0f
            minShatterRadius = 0.0f
            linearVelocityScale = 0.0f
            angularVelocityScale = 0.0f
            shardMass = 0.0f
            density = 0.0f
            friction = 0.0f
            bouncyness = 0.0f
            fxFracture = idStr()
            shards = idList()
            bounds = idBounds.ClearBounds()
            disableFracture = false
            lastRenderEntityUpdate = -1
            changed = false
            fl.networkSync = true
        }
    }
}
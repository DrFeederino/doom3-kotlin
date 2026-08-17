/*
===========================================================================

Doom 3 GPL Source Code
Copyright (C) 1999-2011 id Software LLC, a ZeniMax Media company.
Translated to Kotlin by Dr. Feederino with support of Claude Code

This file is part of the Doom 3 GPL Source Code ("Doom 3 Source Code").
Original source: neo/game/SmokeParticles.cpp, neo/game/SmokeParticles.h

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

===========================================================================
*/

package neo.Game

import neo.Game.Game_local.Companion.isD3XP
import neo.Game.Game_local.idGameLocal
import neo.Renderer.Model.modelSurface_s
import neo.Renderer.ModelManager
import neo.Renderer.RenderWorld
import neo.Renderer.RenderWorld.deferredEntityCallback_t
import neo.Renderer.RenderWorld.renderEntity_s
import neo.Renderer.RenderWorld.renderView_s
import neo.framework.DeclParticle.idDeclParticle
import neo.framework.DeclParticle.idParticleStage
import neo.framework.DeclParticle.particleGen_t
import neo.framework.UsercmdGen
import neo.idlib.containers.List.idList
import neo.idlib.geometry.DrawVert
import neo.idlib.math.Matrix.idMat3
import neo.idlib.math.Random.idRandom
import neo.idlib.math.idVec3
import kotlin.math.floor

object SmokeParticles {
    val smokeParticle_SnapshotName: String = "_SmokeParticle_Snapshot_"

    /*
     ===============================================================================

     Smoke systems are for particles that are emitted off of things that are
     constantly changing position and orientation, like muzzle smoke coming
     from a bone on a weapon, blood spurting from a wound, or particles
     trailing from a monster limb.

     The smoke particles are always evaluated and rendered each tic, so there
     is a performance cost with using them for continuous effects. The general
     particle systems are completely parametric, and have no performance
     overhead when not in view.

     All smoke systems share the same shaderparms, so any coloration must be
     done in the particle definition.

     Each particle model has its own shaderparms, which can be used by the
     particle materials.

     ===============================================================================
     */
    class singleSmoke_t {
        var next: singleSmoke_t? = null
        var privateStartTime: Int = 0  // start time for this particular particle
        var index: Int = 0             // particle index in system, 0 <= index < stage->totalParticles
        var random: idRandom = idRandom()
        val origin: idVec3 = idVec3()
        val axis: idMat3 = idMat3()
        var timeGroup: Int = 0         // D3XP: which timeline this particle belongs to
    }

    class activeSmokeStage_t {
        var stage: idParticleStage = idParticleStage()
        var smokes: singleSmoke_t? = null
    }

    class idSmokeParticles {

        private var initialized: Boolean = false

        private var renderEntity: renderEntity_s    // used to present a model to the renderer
        private var renderEntityHandle: Int          // handle to static renderer model

        private val smokes: Array<singleSmoke_t>

        private val activeStages: idList<activeSmokeStage_t>
        private var freeSmokes: singleSmoke_t?
        private var numActiveSmokes: Int
        private var currentParticleTime: Int         // don't need to recalculate if == view time

        companion object {
            private const val MAX_SMOKE_PARTICLES = 10000
        }

        /*
         ================
         idSmokeParticles::idSmokeParticles
         ================
         */
        init {
            initialized = false
            renderEntity = renderEntity_s()
            renderEntityHandle = -1
            smokes = Array(MAX_SMOKE_PARTICLES) { singleSmoke_t() }
            activeStages = idList()
            freeSmokes = null
            numActiveSmokes = 0
            currentParticleTime = -1
        }

        /*
         ================
         idSmokeParticles::Init
         ================
         */ // creats an entity covering the entire world that will call back each rendering
        fun Init() {
            if (initialized) {
                Shutdown()
            }

            // set up the free list
            for (i in 0 until MAX_SMOKE_PARTICLES - 1) {
                smokes[i].next = smokes[i + 1]
            }
            smokes[MAX_SMOKE_PARTICLES - 1].next = null
            freeSmokes = smokes[0]
            numActiveSmokes = 0

            activeStages.Clear()

            renderEntity = renderEntity_s()

            renderEntity.bounds.Clear()
            renderEntity.axis.set(idMat3.getMat3_identity())
            renderEntity.shaderParms[RenderWorld.SHADERPARM_RED] = 1.0f
            renderEntity.shaderParms[RenderWorld.SHADERPARM_GREEN] = 1.0f
            renderEntity.shaderParms[RenderWorld.SHADERPARM_BLUE] = 1.0f
            renderEntity.shaderParms[3] = 1.0f

            renderEntity.hModel = ModelManager.renderModelManager.AllocModel()
            renderEntity.hModel!!.InitEmpty(smokeParticle_SnapshotName)

            // we certainly don't want particle shadows
            renderEntity.noShadow = true

            // huge bounds, so it will be present in every world area
            renderEntity.bounds.AddPoint(idVec3(-100000f, -100000f, -100000f))
            renderEntity.bounds.AddPoint(idVec3(100000f, 100000f, 100000f))

            renderEntity.callback = ModelCallback.getInstance()

            // add to renderer list
            renderEntityHandle = Game_local.gameRenderWorld!!.AddEntityDef(renderEntity)

            currentParticleTime = -1

            initialized = true
        }

        /*
         ================
         idSmokeParticles::Shutdown
         ================
         */
        fun Shutdown() { // make sure the render entity is freed before the model is freed
            if (renderEntityHandle != -1) {
                Game_local.gameRenderWorld!!.FreeEntityDef(renderEntityHandle)
                renderEntityHandle = -1
            }
            if (renderEntity.hModel != null) {
                ModelManager.renderModelManager.FreeModel(renderEntity.hModel!!)
                renderEntity.hModel = null
            }
            initialized = false
        }

        /*
         ================
         idSmokeParticles::FreeSmokes
         ================
         */ // free old smokes
        fun FreeSmokes() {
            var activeStageNum = 0
            while (activeStageNum < activeStages.Num()) {
                var smoke: singleSmoke_t?
                var next: singleSmoke_t?
                var last: singleSmoke_t?

                val active = activeStages[activeStageNum]
                val stage = active.stage

                last = null
                smoke = active.smokes
                while (smoke != null) {
                    next = smoke.next

                    val frac = if (isD3XP) { // D3XP: use correct timeline based on particle's timeGroup
                        if (smoke.timeGroup != 0) {
                            (Game_local.gameLocal.fast.time - smoke.privateStartTime).toFloat() / (stage.particleLife * 1000)
                        } else {
                            (Game_local.gameLocal.slow.time - smoke.privateStartTime).toFloat() / (stage.particleLife * 1000)
                        }
                    } else {
                        (Game_local.gameLocal.time - smoke.privateStartTime).toFloat() / (stage.particleLife * 1000)
                    }
                    if (frac >= 1.0f) { // remove the particle from the stage list
                        if (last != null) {
                            last.next = smoke.next
                        } else {
                            active.smokes = smoke.next
                        } // put the particle on the free list
                        smoke.next = freeSmokes
                        freeSmokes = smoke
                        numActiveSmokes--
                        smoke = next
                        continue
                    }

                    last = smoke
                    smoke = next
                }

                if (null == active.smokes) { // remove this from the activeStages list
                    activeStages.RemoveIndex(activeStageNum)
                    activeStageNum--
                }
                activeStageNum++
            }
        }

        /*
         ================
         idSmokeParticles::EmitSmoke

         Called by game code to drop another particle into the list
         ================
         */ // spits out a particle, returning false if the system will not emit any more particles in the future
        fun EmitSmoke(
            smoke: idDeclParticle?,
            systemStartTime: Int,
            diversity: Float,
            origin: idVec3,
            axis: idMat3,
            timeGroup: Int = 0 // D3XP: which timeline this smoke belongs to
        ): Boolean {
            var continues = false

            // D3XP: switch to the correct timeline for the duration of this call
            val ts = if (isD3XP) SetTimeState(timeGroup) else null
            try {

                if (null == smoke) {
                    return false
                }

                if (!Game_local.gameLocal.isNewFrame) {
                    return false
                }

                // dedicated doesn't smoke. No UpdateRenderEntity, so they would not be freed
                if (Game_local.gameLocal.localClientNum < 0) {
                    return false
                }

                assert(Game_local.gameLocal.time == 0 || systemStartTime <= Game_local.gameLocal.time)
                if (systemStartTime > Game_local.gameLocal.time) {
                    return false
                }

                val steppingRandom = idRandom((0xffff * diversity).toInt())

                // for each stage in the smoke that is still emitting particles, emit a new singleSmoke_t
                for (stageNum in 0 until smoke.stages.Num()) {
                    val stage = smoke.stages[stageNum]

                    if (0 == stage.cycleMsec) {
                        continue
                    }

                    if (null == stage.material) {
                        continue
                    }

                    if (stage.particleLife <= 0) {
                        continue
                    }

                    // see how many particles we should emit this tic
                    // FIXME: 			smoke.privateStartTime += stage.timeOffset;
                    val finalParticleTime = (stage.cycleMsec * stage.spawnBunching).toInt()
                    val deltaMsec = Game_local.gameLocal.time - systemStartTime

                    var nowCount = 0
                    var prevCount: Int
                    if (finalParticleTime == 0) { // if spawnBunching is 0, they will all come out at once
                        if (Game_local.gameLocal.time == systemStartTime) {
                            prevCount = -1
                            nowCount = stage.totalParticles - 1
                        } else {
                            prevCount = stage.totalParticles
                        }
                    } else {
                        nowCount = floor((deltaMsec.toFloat() / finalParticleTime * stage.totalParticles)).toInt()
                        if (nowCount >= stage.totalParticles) {
                            nowCount = stage.totalParticles - 1
                        }
                        prevCount =
                            floor(((deltaMsec - if (isD3XP) Game_local.gameLocal.msec else UsercmdGen.USERCMD_MSEC).toFloat() / finalParticleTime * stage.totalParticles)).toInt()
                        if (prevCount < -1) {
                            prevCount = -1
                        }
                    }

                    if (prevCount >= stage.totalParticles) { // no more particles from this stage
                        continue
                    }

                    if (nowCount < stage.totalParticles - 1) { // the system will need to emit particles next frame as well
                        continues = true
                    }

                    // find an activeSmokeStage that matches this
                    var active: activeSmokeStage_t? = null
                    var i: Int
                    i = 0
                    while (i < activeStages.Num()) {
                        active = activeStages[i]
                        if (active.stage === stage) {
                            break
                        }
                        i++
                    }
                    if (i == activeStages.Num()) { // add a new one
                        val newActive = activeSmokeStage_t()
                        newActive.smokes = null
                        newActive.stage = stage
                        i = activeStages.Append(newActive)
                        active = activeStages[i]
                    }

                    // add all the required particles
                    prevCount++
                    while (prevCount <= nowCount) {
                        if (null == freeSmokes) {
                            Game_local.gameLocal.Printf(
                                "idSmokeParticles::EmitSmoke: no free smokes with %d active stages\n",
                                activeStages.Num()
                            )
                            return true
                        }
                        val newSmoke = freeSmokes!!
                        freeSmokes = freeSmokes!!.next
                        numActiveSmokes++

                        if (isD3XP) {
                            newSmoke.timeGroup = timeGroup
                        }
                        newSmoke.index = prevCount
                        newSmoke.axis.set(axis)
                        newSmoke.origin.set(origin)
                        newSmoke.random = idRandom(steppingRandom)
                        newSmoke.privateStartTime =
                            systemStartTime + prevCount * finalParticleTime / stage.totalParticles
                        newSmoke.next = active!!.smokes
                        active.smokes = newSmoke

                        steppingRandom.RandomInt() // advance the random
                        prevCount++
                    }
                }

                return continues
            } finally {
                ts?.close()
            }
        }

        /*
         ================
         idSmokeParticles::UpdateRenderEntity
         ================
         */
        private fun UpdateRenderEntity(renderEntity: renderEntity_s, renderView: renderView_s?): Boolean {

            // FIXME: re-use model surfaces
            renderEntity.hModel!!.InitEmpty(smokeParticle_SnapshotName)

            // this may be triggered by a model trace or other non-view related source,
            // to which we should look like an empty model
            if (null == renderView) {
                return false
            }

            // don't regenerate it if it is current
            if (renderView.time == currentParticleTime && !renderView.forceUpdate) {
                return false
            }
            currentParticleTime = renderView.time

            val g = particleGen_t()

            g.renderEnt = renderEntity
            g.renderView = renderView

            var activeStageNum = 0
            while (activeStageNum < activeStages.Num()) {
                var smoke: singleSmoke_t?
                var next: singleSmoke_t?
                var last: singleSmoke_t?

                val active = activeStages[activeStageNum]
                val stage = active.stage

                if (null == stage.material) {
                    activeStageNum++
                    continue
                }

                // allocate a srfTriangles that can hold all the particles
                var count = 0
                smoke = active.smokes
                while (smoke != null) {
                    count++
                    smoke = smoke.next
                }
                val quads = count * stage.NumQuadsPerParticle()
                val tri = renderEntity.hModel!!.AllocSurfaceTriangles(quads * 4, quads * 6)
                tri.numIndexes = quads * 6
                tri.numVerts = quads * 4

                // just always draw the particles
                tri.bounds[0, 0] = -99999.0f
                tri.bounds[0, 1] = -99999.0f
                tri.bounds[0, 2] = -99999.0f
                tri.bounds[1, 0] = 99999.0f
                tri.bounds[1, 1] = 99999.0f
                tri.bounds[1, 2] = 99999.0f

                tri.numVerts = 0
                val particleVerts = Array(4 * stage.NumQuadsPerParticle()) { DrawVert.idDrawVert() }
                last = null
                smoke = active.smokes
                while (smoke != null) {
                    next = smoke.next

                    g.frac = if (isD3XP && smoke.timeGroup != 0) {
                        (Game_local.gameLocal.fast.time - smoke.privateStartTime).toFloat() / (stage.particleLife * 1000)
                    } else {
                        (Game_local.gameLocal.time - smoke.privateStartTime).toFloat() / (stage.particleLife * 1000)
                    }
                    if (g.frac >= 1.0f) { // remove the particle from the stage list
                        if (last != null) {
                            last.next = smoke.next
                        } else {
                            active.smokes = smoke.next
                        } // put the particle on the free list
                        smoke.next = freeSmokes
                        freeSmokes = smoke
                        numActiveSmokes--
                        smoke = next
                        continue
                    }

                    g.index = smoke.index
                    g.random = idRandom(smoke.random)

                    g.origin.set(smoke.origin)
                    g.axis.set(smoke.axis)

                    g.originalRandom = idRandom(g.random)
                    g.age = g.frac * stage.particleLife

                    val createdVerts = stage.CreateParticle(g, particleVerts)
                    for (i in 0 until createdVerts) {
                        tri.verts!![tri.numVerts + i].set(particleVerts[i])
                    }
                    tri.numVerts += createdVerts

                    last = smoke
                    smoke = next
                }
                if (tri.numVerts > quads * 4) {
                    idGameLocal.Error("idSmokeParticles::UpdateRenderEntity: miscounted verts")
                }

                if (tri.numVerts == 0) {

                    // they were all removed
                    renderEntity.hModel!!.FreeSurfaceTriangles(tri)

                    if (null == active.smokes) { // remove this from the activeStages list
                        activeStages.RemoveIndex(activeStageNum)
                        activeStageNum--
                    }
                } else { // build the index list
                    var indexes = 0
                    var i = 0
                    while (i < tri.numVerts) {
                        tri.indexes!![indexes + 0] = i
                        tri.indexes!![indexes + 1] = i + 2
                        tri.indexes!![indexes + 2] = i + 3
                        tri.indexes!![indexes + 3] = i
                        tri.indexes!![indexes + 4] = i + 3
                        tri.indexes!![indexes + 5] = i + 1
                        indexes += 6
                        i += 4
                    }
                    tri.numIndexes = indexes

                    val surf = modelSurface_s()
                    surf.geometry = tri
                    surf.shader = stage.material
                    surf.id = 0

                    renderEntity.hModel!!.AddSurface(surf)
                }
                activeStageNum++
            }
            return true
        }

        /*
         ================
         idSmokeParticles::ModelCallback
         ================
         */ // NOTE: Differs from C++ — C++ uses a static function pointer; Kotlin uses a singleton
        // implementing the deferredEntityCallback_t interface.
        private class ModelCallback private constructor() : deferredEntityCallback_t() {
            override fun run(e: renderEntity_s?, v: renderView_s?): Boolean { // update the particles
                return Game_local.gameLocal.smokeParticles == null || Game_local.gameLocal.smokeParticles!!.UpdateRenderEntity(
                    e!!, v
                )
            }

            companion object {
                private val instance: deferredEntityCallback_t = ModelCallback()
                fun getInstance(): deferredEntityCallback_t {
                    return instance
                }
            }
        }
    }
}

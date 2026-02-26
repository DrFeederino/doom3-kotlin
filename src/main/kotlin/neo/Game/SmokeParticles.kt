package neo.Game

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
import neo.idlib.math.Matrix.idMat3
import neo.idlib.math.Random.idRandom
import neo.idlib.math.idVec3
import java.nio.ByteBuffer
import java.util.*
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
        val axis: idMat3 = idMat3()
        var index // particle index in system, 0 <= index < stage->totalParticles
                = 0
        var next: singleSmoke_t? = null
        val origin: idVec3 = idVec3()
        var privateStartTime // start time for this particular particle
                = 0
        var random: idRandom = idRandom()
    }

    class activeSmokeStage_t {
        var smokes: singleSmoke_t? = null
        var stage: idParticleStage = idParticleStage()
    }

    class idSmokeParticles {
        //
        private val activeStages: idList<activeSmokeStage_t>
        private var currentParticleTime // don't need to recalculate if == view time
                : Int
        private var freeSmokes: singleSmoke_t?
        private var initialized = false
        private var numActiveSmokes: Int

        //
        private var renderEntity // used to present a model to the renderer
                : renderEntity_s
        private var renderEntityHandle // handle to static renderer model
                : Int
        private val smokes: Array<singleSmoke_t>

        // creats an entity covering the entire world that will call back each rendering
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
            renderEntity = renderEntity_s() //memset( &renderEntity, 0, sizeof( renderEntity ) );
            renderEntity.bounds.Clear()
            renderEntity.axis.set(idMat3.getMat3_identity())
            renderEntity.shaderParms[RenderWorld.SHADERPARM_RED] = 1.0f
            renderEntity.shaderParms[RenderWorld.SHADERPARM_GREEN] = 1.0f
            renderEntity.shaderParms[RenderWorld.SHADERPARM_BLUE] = 1.0f
            renderEntity.shaderParms[3] = 1.0f
            renderEntity.hModel = ModelManager.renderModelManager.AllocModel()
            renderEntity.hModel!!.InitEmpty(smokeParticle_SnapshotName)

            // we certainly don't want particle shadows
            renderEntity.noShadow = true //1;

            // huge bounds, so it will be present in every world area
            renderEntity.bounds.AddPoint(idVec3(-100000, -100000, -100000))
            renderEntity.bounds.AddPoint(idVec3(100000, 100000, 100000))
            renderEntity.callback = ModelCallback.getInstance()
            // add to renderer list
            renderEntityHandle = Game_local.gameRenderWorld!!.AddEntityDef(renderEntity)
            currentParticleTime = -1
            initialized = true
        }

        fun Shutdown() {
            // make sure the render entity is freed before the model is freed
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
         idSmokeParticles::EmitSmoke

         Called by game code to drop another particle into the list
         ================
         */
        // spits out a particle, returning false if the system will not emit any more particles in the future
        fun EmitSmoke(
            smoke: idDeclParticle?,
            systemStartTime: Int,
            diversity: Float,
            origin: idVec3,
            axis: idMat3
        ): Boolean {
            var continues = false
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
                if (finalParticleTime == 0) {
                    // if spawnBunching is 0, they will all come out at once
                    if (Game_local.gameLocal.time == systemStartTime) {
                        prevCount = -1
                        nowCount = stage.totalParticles - 1
                    } else {
                        prevCount = stage.totalParticles
                    }
                } else {
                    nowCount =
                        floor((deltaMsec.toFloat() / finalParticleTime * stage.totalParticles)).toInt()
                    if (nowCount >= stage.totalParticles) {
                        nowCount = stage.totalParticles - 1
                    }
                    prevCount =
                        floor(((deltaMsec - UsercmdGen.USERCMD_MSEC).toFloat() / finalParticleTime * stage.totalParticles))
                            .toInt()
                    if (prevCount < -1) {
                        prevCount = -1
                    }
                }
                if (prevCount >= stage.totalParticles) {
                    // no more particles from this stage
                    continue
                }
                if (nowCount < stage.totalParticles - 1) {
                    // the system will need to emit particles next frame as well
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
                if (i == activeStages.Num()) {
                    // add a new one
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
                    newSmoke.index = prevCount
                    newSmoke.axis.set(axis)
                    newSmoke.origin.set(origin)
                    newSmoke.random = steppingRandom
                    newSmoke.privateStartTime = systemStartTime + prevCount * finalParticleTime / stage.totalParticles
                    newSmoke.next = active!!.smokes
                    active.smokes = newSmoke
                    steppingRandom.RandomInt() // advance the random
                    prevCount++
                }
            }
            return continues
        }

        // free old smokes
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
                    val frac =
                        (Game_local.gameLocal.time - smoke.privateStartTime).toFloat() / (stage.particleLife * 1000)
                    if (frac >= 1.0f) {
                        // remove the particle from the stage list
                        if (last != null) {
                            last.next = smoke.next
                        } else {
                            active.smokes = smoke.next
                        }
                        // put the particle on the free list
                        smoke.next = freeSmokes
                        freeSmokes = smoke
                        numActiveSmokes--
                        smoke = next
                        continue
                    }
                    last = smoke
                    smoke = next
                }
                if (null == active.smokes) {
                    // remove this from the activeStages list
                    activeStages.RemoveIndex(activeStageNum)
                    activeStageNum--
                }
                activeStageNum++
            }
        }

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
                last = null
                smoke = active.smokes
                while (smoke != null) {
                    next = smoke.next
                    g.frac =
                        (Game_local.gameLocal.time - smoke.privateStartTime).toFloat() / (stage.particleLife * 1000)
                    if (g.frac >= 1.0f) {
                        // remove the particle from the stage list
                        if (last != null) {
                            last.next = smoke.next
                        } else {
                            active.smokes = smoke.next
                        }
                        // put the particle on the free list
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
                    tri.numVerts += stage.CreateParticle(
                        g,
                        Arrays.copyOfRange(tri.verts, tri.numVerts, tri.verts!!.size)
                    )
                    last = smoke
                    smoke = next
                }
                if (tri.numVerts > quads * 4) {
                    idGameLocal.Error("idSmokeParticles::UpdateRenderEntity: miscounted verts")
                }
                if (tri.numVerts == 0) {

                    // they were all removed
                    renderEntity.hModel!!.FreeSurfaceTriangles(tri)
                    if (null == active.smokes) {
                        // remove this from the activeStages list
                        activeStages.RemoveIndex(activeStageNum)
                        activeStageNum--
                    }
                } else {
                    // build the index list
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

        private class ModelCallback private constructor() : deferredEntityCallback_t() {
            override fun run(e: renderEntity_s?, v: renderView_s?): Boolean {
                // update the particles
                return if (Game_local.gameLocal.smokeParticles != null) {
                    Game_local.gameLocal.smokeParticles!!.UpdateRenderEntity(e!!, v)
                } else true
            }

            override fun AllocBuffer(): ByteBuffer {
                throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
            }

            override fun Read(buffer: ByteBuffer) {
                throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
            }

            override fun Write(): ByteBuffer {
                throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
            }

            companion object {
                private val instance: deferredEntityCallback_t = ModelCallback()
                fun getInstance(): deferredEntityCallback_t {
                    return instance
                }
            }
        }

        companion object {
            //
            private const val MAX_SMOKE_PARTICLES = 10000
        }

        //
        //
        init {
            renderEntity = renderEntity_s() //memset( &renderEntity, 0, sizeof( renderEntity ) );
            renderEntityHandle = -1
            smokes = Array(MAX_SMOKE_PARTICLES) { singleSmoke_t() }
            activeStages = idList()
            freeSmokes = null
            numActiveSmokes = 0
            currentParticleTime = -1
        }
    }
}
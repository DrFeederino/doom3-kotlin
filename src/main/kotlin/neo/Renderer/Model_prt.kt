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

import neo.Renderer.Model.dynamicModel_t
import neo.Renderer.Model.idRenderModel
import neo.Renderer.Model.modelSurface_s
import neo.Renderer.Model.srfTriangles_s
import neo.Renderer.Model_local.idRenderModelStatic
import neo.Renderer.RenderWorld.renderEntity_s
import neo.framework.DeclManager
import neo.framework.DeclManager.declType_t
import neo.framework.DeclParticle.idDeclParticle
import neo.framework.DeclParticle.idParticleStage
import neo.framework.DeclParticle.particleGen_t
import neo.framework.DeclParticle.prtOrientation_t
import neo.idlib.BV.idBounds
import neo.idlib.Text.Str.idStr
import neo.idlib.Text.Str.idStr.Companion.Icmp
import neo.idlib.containers.CInt
import neo.idlib.containers.List.idFloatList
import neo.idlib.geometry.DrawVert.idDrawVert
import neo.idlib.math.Random.idRandom

object Model_prt {
    val parametricParticle_SnapshotName: String = "_ParametricParticle_Snapshot_"

    /*
     ===============================================================================

     PRT model

     ===============================================================================
     */
    class idRenderModelPrt : idRenderModelStatic() {
        private var particleSystem: idDeclParticle? = null
        private val softeningRadii: idFloatList = idFloatList()

        override fun InitFromFile(fileName: String?) {
            name = idStr((fileName)!!)
            particleSystem = DeclManager.declManager.FindType(declType_t.DECL_PARTICLE, (fileName)) as idDeclParticle?
            SetSofteningRadii()
        }

        override fun TouchData() { // Ensure our particle system is added to the list of referenced decls
            particleSystem = DeclManager.declManager.FindType(declType_t.DECL_PARTICLE, name) as idDeclParticle?
        }

        override fun IsDynamicModel(): dynamicModel_t {
            return dynamicModel_t.DM_CONTINUOUS
        }

        override fun InstantiateDynamicModel(
            renderEntity: renderEntity_s?, viewDef: viewDef_s?, cachedModel: idRenderModel?
        ): idRenderModel? {
            var cachedModel: idRenderModel? = cachedModel
            val staticModel: idRenderModelStatic
            if (cachedModel != null && !r_useCachedDynamicModels.GetBool()) {
                cachedModel = null
            }

            // this may be triggered by a model trace or other non-view related source, to which we should look like an empty model
            if (renderEntity == null || viewDef == null) {
                return null
            }
            if (r_skipParticles.GetBool()) {
                return null
            }

            if (cachedModel != null) {
                assert((cachedModel is idRenderModelStatic))
                assert((Icmp(cachedModel.Name(), parametricParticle_SnapshotName) == 0))
                staticModel = cachedModel as idRenderModelStatic
            } else {
                staticModel = idRenderModelStatic()
                staticModel.InitEmpty(parametricParticle_SnapshotName)
            }
            val g = particleGen_t()
            g.renderEnt = renderEntity
            g.renderView = viewDef.renderView
            g.origin.Zero()
            g.axis.Identity()
            for (stageNum in 0 until particleSystem!!.stages.Num()) {
                val stage: idParticleStage = particleSystem!!.stages[stageNum]
                if (null == stage.material) {
                    continue
                }
                if (0 == stage.cycleMsec) {
                    continue
                }
                if (stage.hidden) {        // just for gui particle editor use
                    staticModel.DeleteSurfaceWithId(stageNum)
                    continue
                }
                val steppingRandom = idRandom()
                val steppingRandom2 = idRandom()
                val stageAge: Int =
                    (g.renderView.time + renderEntity.shaderParms[RenderWorld.SHADERPARM_TIMEOFFSET] * 1000 - stage.timeOffset * 1000).toInt()
                val stageCycle: Int = stageAge / stage.cycleMsec

                // some particles will be in this cycle, some will be in the previous cycle
                steppingRandom.SetSeed(
                    ((stageCycle shl 10) and idRandom.MAX_RAND) xor (renderEntity.shaderParms[RenderWorld.SHADERPARM_DIVERSITY] * idRandom.MAX_RAND).toInt()
                )
                steppingRandom2.SetSeed(
                    (((stageCycle - 1) shl 10) and idRandom.MAX_RAND) xor (renderEntity.shaderParms[RenderWorld.SHADERPARM_DIVERSITY] * idRandom.MAX_RAND).toInt()
                )
                val count: Int = stage.totalParticles * stage.NumQuadsPerParticle()
                val surfaceNum = CInt()
                var surf: modelSurface_s?
                if (staticModel.FindSurfaceWithId(stageNum, surfaceNum)) {
                    surf = staticModel.surfaces[surfaceNum._val]
                    R_FreeStaticTriSurfVertexCaches(surf!!.geometry!!)
                } else {
                    surf = modelSurface_s()
                    staticModel.surfaces.Append(surf)
                    surf.id = stageNum
                    surf.shader = stage.material
                    surf.geometry = srfTriangles_s()
                    R_AllocStaticTriSurfVerts(surf.geometry!!, 4 * count)
                    R_AllocStaticTriSurfIndexes(surf.geometry!!, 6 * count)
                    R_AllocStaticTriSurfPlanes(surf.geometry!!, 6 * count)
                }
                var numVerts = 0
                val verts: Array<idDrawVert>? = surf.geometry!!.verts
                val particleVerts = Array(4 * stage.NumQuadsPerParticle()) { idDrawVert() }
                for (index in 0 until stage.totalParticles) {
                    g.index = index

                    // bump the random
                    steppingRandom.RandomInt()
                    steppingRandom2.RandomInt()

                    // calculate local age for this index
                    val bunchOffset: Int =
                        (stage.particleLife * 1000 * stage.spawnBunching * index / stage.totalParticles).toInt()
                    val particleAge: Int = stageAge - bunchOffset
                    val particleCycle: Int = particleAge / stage.cycleMsec
                    if (particleCycle < 0) { // before the particleSystem spawned
                        continue
                    }
                    if (stage.cycles != 0.0f && particleCycle >= stage.cycles) { // cycled systems will only run cycle times
                        continue
                    }
                    if (particleCycle == stageCycle) {
                        g.random = idRandom(steppingRandom)
                    } else {
                        g.random = idRandom(steppingRandom2)
                    }
                    val inCycleTime: Int = particleAge - particleCycle * stage.cycleMsec
                    if ((renderEntity.shaderParms[RenderWorld.SHADERPARM_PARTICLE_STOPTIME] != 0.0f && g.renderView.time - inCycleTime >= renderEntity.shaderParms[RenderWorld.SHADERPARM_PARTICLE_STOPTIME] * 1000)) { // don't fire any more particles
                        continue
                    }

                    // supress particles before or after the age clamp
                    g.frac = inCycleTime.toFloat() / (stage.particleLife * 1000)
                    if (g.frac < 0.0f) { // yet to be spawned
                        continue
                    }
                    if (g.frac > 1.0f) { // this particle is in the deadTime band
                        continue
                    }

                    // this is needed so aimed particles can calculate origins at different times
                    g.originalRandom = idRandom(g.random)
                    g.age = g.frac * stage.particleLife

                    // if the particle doesn't get drawn because it is faded out or beyond a kill region, don't increment the verts
                    val createdVerts = stage.CreateParticle(g, particleVerts)
                    for (i in 0 until createdVerts) {
                        verts!![numVerts + i].set(particleVerts[i])
                    }
                    numVerts += createdVerts
                }
                assert(((numVerts and 3) == 0 && numVerts <= 4 * count))

                // build the indexes
                var numIndexes = 0
                val indexes: IntArray? = surf.geometry!!.indexes
                var i = 0
                while (i < numVerts) {
                    indexes!![numIndexes + 0] = i
                    indexes[numIndexes + 1] = i + 2
                    indexes[numIndexes + 2] = i + 3
                    indexes[numIndexes + 3] = i
                    indexes[numIndexes + 4] = i + 3
                    indexes[numIndexes + 5] = i + 1
                    numIndexes += 6
                    i += 4
                }
                surf.geometry!!.tangentsCalculated = false
                surf.geometry!!.facePlanesCalculated = false
                surf.geometry!!.numVerts = numVerts
                surf.geometry!!.numIndexes = numIndexes
                surf.geometry!!.bounds.set(stage.bounds) // just always draw the particles
            }
            return staticModel
        }

        fun SofteningRadius(stage: Int): Float {
            assert(particleSystem != null)
            assert(stage > -1 && stage < softeningRadii.Num())
            return softeningRadii[stage]
        }

        private fun SetSofteningRadii() {
            val ps = particleSystem ?: return
            softeningRadii.Clear()
            softeningRadii.SetGranularity(ps.stages.Num().coerceAtLeast(1))
            for (i in 0 until ps.stages.Num()) {
                val stage = ps.stages[i]
                if (stage.orientation == prtOrientation_t.POR_VIEW) {
                    var diameter = maxOf(stage.size.from, stage.size.to)
                    val scale = maxOf(stage.aspect.from, stage.aspect.to)
                    diameter *= maxOf(scale, 1.0f)
                    if (diameter > 2.0f) {
                        softeningRadii.Append(diameter * 0.8f / 2.0f)
                    } else {
                        softeningRadii.Append(0.0f)
                    }
                } else {
                    softeningRadii.Append(-1.0f)
                }
            }
        }

        override fun Bounds(ent: renderEntity_s?): idBounds {
            return particleSystem!!.bounds
        }

        override fun DepthHack(): Float {
            return particleSystem!!.depthHack
        }

        override fun Memory(): Int {
            var total = 0
            total += super.Memory()
            return total
        }
    }
}

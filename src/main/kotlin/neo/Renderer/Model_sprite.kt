package neo.Renderer

import neo.Renderer.Model.dynamicModel_t
import neo.Renderer.Model.idRenderModel
import neo.Renderer.Model.modelSurface_s
import neo.Renderer.Model.srfTriangles_s
import neo.Renderer.Model_local.idRenderModelStatic
import neo.Renderer.RenderWorld.renderEntity_s
import neo.idlib.BV.idBounds
import neo.idlib.Max
import neo.idlib.math.idMath.FtoiFast
import neo.idlib.math.idVec3

object Model_sprite {
    /*

     A simple sprite model that always faces the view axis.

     */
    val sprite_SnapshotName: String = "_sprite_Snapshot_"

    /*
     ================================================================================

     idRenderModelSprite 

     ================================================================================
     */
    class idRenderModelSprite : idRenderModelStatic() {
        override fun IsDynamicModel(): dynamicModel_t {
            return dynamicModel_t.DM_CONTINUOUS
        }

        override fun IsLoaded(): Boolean {
            return true
        }

        override fun InstantiateDynamicModel(
            renderEntity: renderEntity_s?,
            viewDef: viewDef_s?,
            cachedModel: idRenderModel?
        ): idRenderModel? {
            var cachedModel: idRenderModel? = cachedModel
            val staticModel: idRenderModelStatic
            val tri: srfTriangles_s?
            var surf: modelSurface_s? = modelSurface_s()
            if (cachedModel != null && !r_useCachedDynamicModels!!.GetBool()) {
//		delete cachedModel;
                cachedModel = null
            }
            if (renderEntity == null || viewDef == null) {
//		delete cachedModel;
                return null
            }
            if (cachedModel != null) {

//		assert( dynamic_cast<idRenderModelStatic *>( cachedModel ) != null );
//		assert( idStr.Icmp( cachedModel.Name(), sprite_SnapshotName ) == 0 );
                staticModel = cachedModel as idRenderModelStatic
                surf = staticModel.Surface(0)
                tri = surf!!.geometry
            } else {
                staticModel = idRenderModelStatic()
                staticModel.InitEmpty(sprite_SnapshotName)
                tri = R_AllocStaticTriSurf()
                R_AllocStaticTriSurfVerts(tri, 4)
                R_AllocStaticTriSurfIndexes(tri, 6)
                tri.verts!![0]!!.Clear()
                tri.verts!![0]!!.normal.set(1.0f, 0.0f, 0.0f)
                tri.verts!![0]!!.tangents[0].set(0.0f, 1.0f, 0.0f)
                tri.verts!![0]!!.tangents[1].set(0.0f, 0.0f, 1.0f)
                tri.verts!![0]!!.st[0] = 0.0f
                tri.verts!![0]!!.st[1] = 0.0f
                tri.verts!![1]!!.Clear()
                tri.verts!![1]!!.normal.set(1.0f, 0.0f, 0.0f)
                tri.verts!![1]!!.tangents[0].set(0.0f, 1.0f, 0.0f)
                tri.verts!![1]!!.tangents[1].set(0.0f, 0.0f, 1.0f)
                tri.verts!![1]!!.st[0] = 1.0f
                tri.verts!![1]!!.st[1] = 0.0f
                tri.verts!![2]!!.Clear()
                tri.verts!![2]!!.normal.set(1.0f, 0.0f, 0.0f)
                tri.verts!![2]!!.tangents[0].set(0.0f, 1.0f, 0.0f)
                tri.verts!![2]!!.tangents[1].set(0.0f, 0.0f, 1.0f)
                tri.verts!![2]!!.st[0] = 1.0f
                tri.verts!![2]!!.st[1] = 1.0f
                tri.verts!![3]!!.Clear()
                tri.verts!![3]!!.normal.set(1.0f, 0.0f, 0.0f)
                tri.verts!![3]!!.tangents[0].set(0.0f, 1.0f, 0.0f)
                tri.verts!![3]!!.tangents[1].set(0.0f, 0.0f, 1.0f)
                tri.verts!![3]!!.st[0] = 0.0f
                tri.verts!![3]!!.st[1] = 1.0f
                tri.indexes!![0] = 0
                tri.indexes!![1] = 1
                tri.indexes!![2] = 3
                tri.indexes!![3] = 1
                tri.indexes!![4] = 2
                tri.indexes!![5] = 3
                tri.numVerts = 4
                tri.numIndexes = 6
                surf!!.geometry = tri
                surf.id = 0
                surf.shader = tr.defaultMaterial
                staticModel.AddSurface(surf)
            }
            val red: Byte = FtoiFast(renderEntity.shaderParms[RenderWorld.SHADERPARM_RED] * 255.0f).toByte()
            val green: Byte = FtoiFast(renderEntity.shaderParms[RenderWorld.SHADERPARM_GREEN] * 255.0f).toByte()
            val blue: Byte = FtoiFast(renderEntity.shaderParms[RenderWorld.SHADERPARM_BLUE] * 255.0f).toByte()
            val alpha: Byte = FtoiFast(renderEntity.shaderParms[RenderWorld.SHADERPARM_ALPHA] * 255.0f).toByte()
            val right: idVec3 =
                idVec3(0.0f, renderEntity.shaderParms[RenderWorld.SHADERPARM_SPRITE_WIDTH] * 0.5f, 0.0f)
            val up: idVec3 =
                idVec3(0.0f, 0.0f, renderEntity.shaderParms[RenderWorld.SHADERPARM_SPRITE_HEIGHT] * 0.5f)
            tri!!.verts!![0]!!.xyz.set(up.plus(right))
            tri.verts!![0]!!.color[0] = red
            tri.verts!![0]!!.color[1] = green
            tri.verts!![0]!!.color[2] = blue
            tri.verts!![0]!!.color[3] = alpha
            tri.verts!![1]!!.xyz.set(up.minus(right))
            tri.verts!![1]!!.color[0] = red
            tri.verts!![1]!!.color[1] = green
            tri.verts!![1]!!.color[2] = blue
            tri.verts!![1]!!.color[3] = alpha
            tri.verts!![2]!!.xyz.set(right.unaryMinus().minus(up))
            tri.verts!![2]!!.color[0] = red
            tri.verts!![2]!!.color[1] = green
            tri.verts!![2]!!.color[2] = blue
            tri.verts!![2]!!.color[3] = alpha
            tri.verts!![3]!!.xyz.set(right.minus(up))
            tri.verts!![3]!!.color[0] = red
            tri.verts!![3]!!.color[1] = green
            tri.verts!![3]!!.color[2] = blue
            tri.verts!![3]!!.color[3] = alpha
            R_BoundTriSurf(tri)
            staticModel.bounds.set(tri.bounds)
            return staticModel
        }

        override fun Bounds(renderEntity: renderEntity_s?): idBounds {
            val b: idBounds = idBounds()
            b.Zero()
            if (renderEntity == null) {
                b.ExpandSelf(8.0f)
            } else {
                b.ExpandSelf(
                    Max(
                        renderEntity.shaderParms[RenderWorld.SHADERPARM_SPRITE_WIDTH],
                        renderEntity.shaderParms[RenderWorld.SHADERPARM_SPRITE_HEIGHT]
                    ) * 0.5f
                )
            }
            return b
        }
    }
}

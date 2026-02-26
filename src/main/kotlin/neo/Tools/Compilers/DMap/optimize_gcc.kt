package neo.Tools.Compilers.DMap

import neo.Tools.Compilers.DMap.dmap.optimizeGroup_s
import neo.Tools.Compilers.DMap.optimize.optVertex_s
import neo.framework.Common
import neo.idlib.BV.idBounds
import neo.idlib.geometry.DrawVert.idDrawVert

object optimize_gcc {
    //
    const val MAX_OPT_VERTEXES = 0x10000
    val optVerts: Array<optVertex_s> = Array(MAX_OPT_VERTEXES) { optVertex_s() }
    var numOptVerts = 0

    /*
     crazy gcc 3.3.5 optimization bug
     happens even at -O1
     if you remove the 'return NULL;' after Error(), it only happens at -O3 / release
     see dmap.gcc.zip test map and .proc outputs
     */
    val optBounds: idBounds = idBounds()

    /*
     ================
     FindOptVertex
     ================
     */
    fun FindOptVertex(v: idDrawVert, opt: optimizeGroup_s): optVertex_s? {
        var i: Int
        val x: Float
        val y: Float
        val vert: optVertex_s?

        // deal with everything strictly as 2D
        x = v.xyz.times(opt.axis[0])
        y = v.xyz.times(opt.axis[1])

        // should we match based on the t-junction fixing hash verts?
        i = 0
        while (i < numOptVerts) {
            if (optVerts[i].pv[0] == x && optVerts[i].pv[1] == y) {
                return optVerts[i]
            }
            i++
        }
        if (numOptVerts >= MAX_OPT_VERTEXES) {
            Common.common.Error("MAX_OPT_VERTEXES")
            return null
        }
        numOptVerts++
        optVerts[i] = optVertex_s()
        vert = optVerts[i]
        //	memset( vert, 0, sizeof( *vert ) );
        vert.v = v
        vert.pv[0] = x
        vert.pv[1] = y
        vert.pv[2] = 0.0f
        optBounds.AddPoint(vert.pv)
        return vert
    }
}
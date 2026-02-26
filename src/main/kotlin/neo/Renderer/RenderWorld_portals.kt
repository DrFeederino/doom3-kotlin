package neo.Renderer

import neo.Renderer.RenderWorld_local.portal_s
import neo.idlib.math.idPlane

object RenderWorld_portals {
    /*


     All that is done in these functions is the creation of viewLights
     and viewEntitys for the lightDefs and entityDefs that are visible
     in the portal areas that can be seen from the current viewpoint.

     */
    //
    //
    // if we hit this many planes, we will just stop cropping the
    // view down, which is still correct, just conservative
    val MAX_PORTAL_PLANES: Int = 20

    class portalStack_s {
        var next: portalStack_s? = null

        //
        var numPortalPlanes: Int = 0
        var p: portal_s? = null
        val portalPlanes: Array<idPlane> = idPlane.generateArray(MAX_PORTAL_PLANES + 1)

        //
        var rect: idScreenRect

        // positive side is outside the visible frustum
        constructor() {
            rect = idScreenRect()
        }

        constructor(p: portalStack_s) {
            this.p = p.p
            next = p.next
            rect = idScreenRect(p.rect)
            numPortalPlanes = p.numPortalPlanes
            for (i in portalPlanes.indices) {
                portalPlanes.get(i).set(p.portalPlanes.get(i))
            }
        }
    }
}

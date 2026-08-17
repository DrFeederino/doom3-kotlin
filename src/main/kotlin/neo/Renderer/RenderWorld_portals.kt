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

import neo.Renderer.RenderWorld_local.portal_s
import neo.idlib.math.idPlane

object RenderWorld_portals {/*
     All that is done in these functions is the creation of viewLights
     and viewEntitys for the lightDefs and entityDefs that are visible
     in the portal areas that can be seen from the current viewpoint.
     */

    // if we hit this many planes, we will just stop cropping the
    // view down, which is still correct, just conservative
    val MAX_PORTAL_PLANES: Int = 20

    class portalStack_s {
        var next: portalStack_s? = null
        var numPortalPlanes: Int = 0
        var p: portal_s? = null
        val portalPlanes: Array<idPlane> = idPlane.generateArray(MAX_PORTAL_PLANES + 1)
        var rect: idScreenRect // positive side is outside the visible frustum

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

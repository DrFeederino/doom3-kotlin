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

import neo.Renderer.Model.srfTriangles_s
import neo.framework.CmdSystem.cmdFunction_t
import neo.framework.Common
import neo.idlib.CmdArgs
import neo.idlib.Text.Str.idStr.Companion.Icmp
import neo.idlib.geometry.DrawVert.idDrawVert
import neo.idlib.math.VectorMA
import neo.idlib.math.VectorSubtract
import neo.idlib.math.idPlane
import neo.idlib.math.idVec3
import neo.ui.UserInterface.idUserInterface
import neo.ui.UserInterface.uiManager
import kotlin.math.floor

object tr_guisurf {
    /*
     ==========================================================================================

     GUI SHADERS

     ==========================================================================================
     *//*
     ================
     R_SurfaceToTextureAxis

     Calculates two axis for the surface sutch that a point dotted against
     the axis will give a 0.0f to 1.0f range in S and T when inside the gui surface
     ================
     */
    fun R_SurfaceToTextureAxis(tri: srfTriangles_s, origin: idVec3?, axis: Array<idVec3> /*[3]*/) {
        val area: Float
        val inva: Float
        val d0 = FloatArray(5)
        val d1 = FloatArray(5)
        val a: idDrawVert?
        val b: idDrawVert?
        val c: idDrawVert?
        val bounds: Array<FloatArray> = Array(2, { FloatArray(2) })
        val boundsOrg = FloatArray(2)
        var i: Int
        var j: Int
        var v: Float

        // find the bounds of the texture
        bounds[0][1] = 999999.0f
        bounds[0][0] = bounds[0][1]
        bounds[1][1] = -999999.0f
        bounds[1][0] = bounds[1][1]
        i = 0
        while (i < tri.numVerts) {
            j = 0
            while (j < 2) {
                v = tri.verts!![i].st[j]
                if (v < bounds[0][j]) {
                    bounds[0][j] = v
                }
                if (v > bounds[1][j]) {
                    bounds[1][j] = v
                }
                j++
            }
            i++
        }

        // use the floor of the midpoint as the origin of the
        // surface, which will prevent a slight misalignment
        // from throwing it an entire cycle off
        boundsOrg[0] = floor((bounds[0][0] + bounds[1][0]) * 0.5f)
        boundsOrg[1] = floor((bounds[0][1] + bounds[1][1]) * 0.5f)

        // determine the world S and T vectors from the first drawSurf triangle
        a = tri.verts!![tri.indexes!![0]]
        b = tri.verts!![tri.indexes!![1]]
        c = tri.verts!![tri.indexes!![2]]
        VectorSubtract(b.xyz, a.xyz, d0)
        d0[3] = b.st[0] - a.st[0]
        d0[4] = b.st[1] - a.st[1]
        VectorSubtract(c.xyz, a.xyz, d1)
        d1[3] = c.st[0] - a.st[0]
        d1[4] = c.st[1] - a.st[1]
        area = d0[3] * d1[4] - d0[4] * d1[3]
        if (area == 0.0f) {
            origin!!.Zero()
            axis[0].Zero()
            axis[1].Zero()
            axis[2].Zero()
            return  // degenerate
        }
        inva = 1.0f / area
        axis[0][0] = (d0[0] * d1[4] - d0[4] * d1[0]) * inva
        axis[0][1] = (d0[1] * d1[4] - d0[4] * d1[1]) * inva
        axis[0][2] = (d0[2] * d1[4] - d0[4] * d1[2]) * inva
        axis[1][0] = (d0[3] * d1[0] - d0[0] * d1[3]) * inva
        axis[1][1] = (d0[3] * d1[1] - d0[1] * d1[3]) * inva
        axis[1][2] = (d0[3] * d1[2] - d0[2] * d1[3]) * inva
        val plane = idPlane()
        plane.FromPoints(a.xyz, b.xyz, c.xyz)
        axis[2][0] = plane[0]
        axis[2][1] = plane[1]
        axis[2][2] = plane[2]

        // take point 0 and project the vectors to the texture origin
        VectorMA(a.xyz, boundsOrg[0] - a.st[0], axis[0], (origin)!!)
        VectorMA((origin), boundsOrg[1] - a.st[1], axis[1], (origin))
    }

    /*
     =================
     R_RenderGuiSurf

     Create a texture space on the given surface and
     call the GUI generator to create quads for it.
     =================
     */
    fun R_RenderGuiSurf(gui: idUserInterface, drawSurf: drawSurf_s) {
        val origin = idVec3()
        val axis: Array<idVec3> = idVec3.generateArray(3)

        // for testing the performance hit
        if (r_skipGuiShaders.GetInteger() == 1) {
            return
        }

        // don't allow an infinite recursion loop
        if (tr.guiRecursionLevel == 4) {
            return
        }
        tr.pc!!.c_guiSurfs++

        // create the new matrix to draw on this surface
        R_SurfaceToTextureAxis(drawSurf.geo!!, origin, axis)
        val guiModelMatrix = FloatArray(16)
        val modelMatrix = FloatArray(16)
        guiModelMatrix[0] = axis[0][0] / 640.0f
        guiModelMatrix[4] = axis[1][0] / 480.0f
        guiModelMatrix[8] = axis[2][0]
        guiModelMatrix[12] = origin[0]
        guiModelMatrix[1] = axis[0][1] / 640.0f
        guiModelMatrix[5] = axis[1][1] / 480.0f
        guiModelMatrix[9] = axis[2][1]
        guiModelMatrix[13] = origin[1]
        guiModelMatrix[2] = axis[0][2] / 640.0f
        guiModelMatrix[6] = axis[1][2] / 480.0f
        guiModelMatrix[10] = axis[2][2]
        guiModelMatrix[14] = origin[2]

        guiModelMatrix[3] = 0.0f
        guiModelMatrix[7] = 0.0f
        guiModelMatrix[11] = 0.0f
        guiModelMatrix[15] = 1.0f

        tr_main.myGlMultMatrix(
            guiModelMatrix, drawSurf.space!!.modelMatrix, modelMatrix
        )
        tr.guiRecursionLevel++

        // call the gui, which will call the 2D drawing functions
        tr.guiModel!!.Clear()
        gui.Redraw(tr.viewDef!!.renderView.time)
        tr.guiModel!!.EmitToCurrentView(modelMatrix, drawSurf.space!!.weaponDepthHack)
        tr.guiModel!!.Clear()
        tr.guiRecursionLevel--
    }

    /*
     ================,
     R_ReloadGuis_f

     Reloads any guis that have had their file timestamps changed.
     An optional "all" parameter will cause all models to reload, even
     if they are not out of date.

     Should we also reload the map models?
     ================
     */
    class R_ReloadGuis_f private constructor() : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            val all: Boolean
            if (0 == Icmp(args!!.Argv(1), "all")) {
                all = true
                Common.common.Printf("Reloading all gui files...\n")
            } else {
                all = false
                Common.common.Printf("Checking for changed gui files...\n")
            }
            uiManager.Reload(all)
        }

        companion object {
            val instance: cmdFunction_t = R_ReloadGuis_f()
        }
    }

    /*
     ================,
     R_ListGuis_f

     ================
     */
    class R_ListGuis_f private constructor() : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            uiManager.ListGuis()
        }

        companion object {
            val instance: cmdFunction_t = R_ListGuis_f()
        }
    }
}

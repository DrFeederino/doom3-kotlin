/*
===========================================================================

Doom 3 GPL Source Code
Copyright (C) 1999-2011 id Software LLC, a ZeniMax Media company.
Translated to Kotlin by Dr. Feederino with support of Claude Code

This file is part of the Doom 3 GPL Source Code ("Doom 3 Source Code").
Original source: neo/game/gamesys/DebugGraph.h, neo/game/gamesys/DebugGraph.cpp

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

In addition, the Doom 3 Source Code is also subject to certain additional terms.
You should have received a copy of these additional terms immediately following
the terms and conditions of the GNU General Public License which accompanied
the Doom 3 Source Code.  If not, please request a copy in writing from
id Software at the address below.

If you have questions concerning this license or the applicable additional terms,
you may contact in writing id Software LLC, c/o ZeniMax Media Inc., Suite 120,
Rockville, Maryland 20850 USA.

===========================================================================
*/

package neo.Game.GameSys

import neo.Game.Game_local
import neo.Game.Game_local.Companion.gameLocal
import neo.Game.Game_local.idGameLocal
import neo.idlib.containers.List.idList
import neo.idlib.math.idVec3
import neo.idlib.math.idVec4

class DebugGraph {

    /*
    ================
    idDebugGraph
    ================
    */
    class idDebugGraph {

        private val samples: idList<Float> = idList()
        private var index: Int = 0

        /*
        ================
        idDebugGraph::SetNumSamples
        ================
        */
        fun SetNumSamples(num: Int) {
            index = 0
            samples.Clear()
            samples.SetNum(num)
            // NOTE: Differs from C++ — C++ uses memset(samples.Ptr(), 0, samples.MemoryUsed())
            // to zero-initialize. idList.Resize uses arrayOfNulls internally, so unwritten
            // entries are null and would NPE when unboxed to Float. Explicitly zero-initialize.
            for (i in 0 until samples.Num()) {
                samples[i] = 0.0f
            }
        }

        /*
        ================
        idDebugGraph::AddValue
        ================
        */
        fun AddValue(value: Float) {
            samples[index] = value
            index++
            if (index >= samples.Num()) {
                index = 0
            }
        }

        /*
        ================
        idDebugGraph::Draw
        ================
        */
        fun Draw(color: idVec4, scale: Float) {
            var value1: Float
            var value2: Float
            val vec1 = idVec3()
            val vec2 = idVec3()

            val axis = gameLocal.GetLocalPlayer()!!.viewAxis
            val pos = gameLocal.GetLocalPlayer()!!.GetPhysics().GetOrigin() + axis[1] * samples.Num() * 0.5f

            value1 = samples[index] * scale
            for (i in 1 until samples.Num()) {
                value2 = samples[(i + index) % samples.Num()] * scale

                vec1.set(pos + axis[2] * value1 - axis[1] * (i - 1) + axis[0] * samples.Num())
                vec2.set(pos + axis[2] * value2 - axis[1] * i + axis[0] * samples.Num())

                Game_local.gameRenderWorld!!.DebugLine(color, vec1, vec2, idGameLocal.msec, false)
                value1 = value2
            }
        }
    }
}

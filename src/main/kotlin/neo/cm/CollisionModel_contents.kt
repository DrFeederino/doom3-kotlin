/*
===========================================================================

Doom 3 GPL Source Code
Copyright (C) 1999-2011 id Software LLC, a ZeniMax Media company.

Translated to Kotlin by Dr. Feederino with support of Claude Code

This file is part of the Doom 3 Kotlin project.
Original source: neo/cm/CollisionModel_contents.cpp

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

/*
===============================================================================

	Trace model vs. polygonal model collision detection.

===============================================================================
*/

package neo.cm

import neo.cm.AbstractCollisionModel_local.cm_edge_s
import neo.cm.AbstractCollisionModel_local.cm_vertex_s
import neo.idlib.math.FLOATSIGNBITSET
import neo.idlib.math.idPlane
import neo.idlib.math.idPluecker

/*
===============================================================================

Contents test

NOTE: The C++ original defines CM_SetTrmEdgeSidedness and CM_SetTrmPolygonSidedness
as preprocessor macros (#define). In Kotlin these are translated to standalone
functions. The actual content-testing methods (TestTrmVertsInBrush, TestTrmInPolygon,
PointNode, PointContents, TransformedPointContents, ContentsTrm, Contents) are
implemented in CollisionModel_local.kt as methods of idCollisionModelManagerLocal.

===============================================================================
*/

/*
================
CM_SetTrmEdgeSidedness

Original C++ macro. Sets the sidedness of a trace model edge relative to a
polygon edge using Pluecker coordinates. Uses cached results to avoid
redundant calculations.
================
*/
fun CM_SetTrmEdgeSidedness(edge: cm_edge_s, bpl: idPluecker, epl: idPluecker, bitNum: Int) {
    if ((edge.sideSet and (1L shl bitNum)) == 0L) {
        val fl: Float = bpl.PermutedInnerProduct(epl)
        // NOTE: Operator precedence relies on left-to-right evaluation of infix functions.
        // `and` evaluates before `or` here because it appears first, matching C++ where & > |
        edge.side = edge.side and (1L shl bitNum).inv() or (FLOATSIGNBITSET(fl).toLong() shl bitNum)
        edge.sideSet = edge.sideSet or (1L shl bitNum)
    }
}

/*
================
CM_SetTrmPolygonSidedness

Original C++ macro. Sets the sidedness of a model vertex relative to a trace
model polygon plane. Uses explicit comparison instead of FLOATSIGNBITSET
because the sign bit is undetermined when the distance equals 0.0f.
================
*/
fun CM_SetTrmPolygonSidedness(v: cm_vertex_s, plane: idPlane, bitNum: Int) {
    if ((v.sideSet and (1L shl bitNum)) == 0L) {
        val fl: Float = plane.Distance(v.p)
        /* cannot use float sign bit because it is undetermined when fl == 0.0f */
        if (fl < 0.0f) {
            v.side = v.side or (1L shl bitNum)
        } else {
            v.side = v.side and (1L shl bitNum).inv()
        }
        v.sideSet = v.sideSet or (1L shl bitNum)
    }
}

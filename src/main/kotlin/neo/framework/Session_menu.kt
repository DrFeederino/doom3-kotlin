/*
===========================================================================

Doom 3 GPL Source Code
Copyright (C) 1999-2011 id Software LLC, a ZeniMax Media company.
Translated to Kotlin by Dr. Feederino with support of Claude Code

This file is part of the Doom 3 GPL Source Code ("Doom 3 Source Code").
Original source: neo/framework/Session_menu.cpp

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

// NOTE: Most methods from the original Session_menu.cpp are implemented in
// Session_local.kt. This file only contains the idListSaveGameCompare comparator.

package neo.framework

import neo.framework.Session_local.fileTIME_T
import neo.idlib.containers.List.cmp_t

class Session_menu {

    /*
    ===============
    idListSaveGameCompare
    ===============
    */
    internal class idListSaveGameCompare : cmp_t<fileTIME_T> {
        override fun compare(
            a: fileTIME_T?, b: fileTIME_T?
        ): Int { // NOTE: Kotlin adaptation - null handling added for safety (C++ original does not handle null)
            if (a == null) {
                return if (b == null) 0 else 1
            }
            if (b == null) {
                return -1
            }
            return (b.timeStamp - a.timeStamp).toInt()
        }
    }
}

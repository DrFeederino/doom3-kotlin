/*
===========================================================================

Doom 3 GPL Source Code
Copyright (C) 1999-2011 id Software LLC, a ZeniMax Media company.

Translated to Kotlin by Dr. Feederino with support of Claude Code

This file is part of the Doom 3 Kotlin project.
Original source: neo/cm/CollisionModel_files.cpp

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

	File I/O constants. The actual file reading/writing methods are in
	CollisionModel_local.kt as methods of idCollisionModelManagerLocal.

===============================================================================
*/

package neo.cm

const val CM_FILE_EXT: String = "cm"
const val CM_FILEID: String = "CM"
const val CM_FILEVERSION: String = "1.00"

/*
 * Copyright (C) 1999-2011 id Software LLC, a ZeniMax Media company.
 * Translated to Kotlin by Dr. Feederino with support of Claude Code
 *
 * This file is part of the Doom 3 Kotlin project.
 * Original source: neo/framework/DemoChecksum.h
 *
 * Doom 3 Source Code is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Doom 3 Source Code is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Doom 3 Source Code. If not, see <http://www.gnu.org/licenses/>.
 */

package neo.framework

/*
===============================================================================

    Pak file checksum for demo build.

===============================================================================
*/

object DemoChecksum {
    // every time a new demo pk4 file is built, this checksum must be updated.
    // the easiest way to get it is to just run the game and see what it spits out
    const val DEMO_PAK_CHECKSUM: Long = -0x018A4411 // FIX: Was -0x55873742 (wrong value). C++ original: 0xFE75BBEF
}

/*
===========================================================================

Doom 3 GPL Source Code
Copyright (C) 1999-2011 id Software LLC, a ZeniMax Media company.

Translated to Kotlin by Dr. Feederino with support of Claude Code

This file is part of the Doom 3 Kotlin project.
Original source: neo/cm/CollisionModel_debug.cpp

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

import neo.Renderer.Material
import neo.framework.CVarSystem
import neo.framework.CVarSystem.idCVar
import neo.framework.CmdSystem.idCmdSystem.ArgCompletion_String
import neo.idlib.math.idVec3
import neo.idlib.math.idVec4

/*
===============================================================================

Visualisation code

===============================================================================
*/

val cm_contentsNameByIndex: Array<String?> = arrayOf(
    "none", // 0
    "solid", // 1
    "opaque", // 2
    "water", // 3
    "playerclip", // 4
    "monsterclip", // 5
    "moveableclip", // 6
    "ikclip", // 7
    "blood", // 8
    "body", // 9
    "corpse", // 10
    "trigger", // 11
    "aas_solid", // 12
    "aas_obstacle", // 13
    "flashlight_trigger", // 14
    null
)

val cm_contentsFlagByIndex: IntArray = intArrayOf(
    -1, // 0
    Material.CONTENTS_SOLID, // 1
    Material.CONTENTS_OPAQUE, // 2
    Material.CONTENTS_WATER, // 3
    Material.CONTENTS_PLAYERCLIP, // 4
    Material.CONTENTS_MONSTERCLIP, // 5
    Material.CONTENTS_MOVEABLECLIP, // 6
    Material.CONTENTS_IKCLIP, // 7
    Material.CONTENTS_BLOOD, // 8
    Material.CONTENTS_BODY, // 9
    Material.CONTENTS_CORPSE, // 10
    Material.CONTENTS_TRIGGER, // 11
    Material.CONTENTS_AAS_SOLID, // 12
    Material.CONTENTS_AAS_OBSTACLE, // 13
    Material.CONTENTS_FLASHLIGHT_TRIGGER, // 14
    0
)

val cm_drawMask: idCVar = idCVar(
    "cm_drawMask",
    "none",
    CVarSystem.CVAR_GAME,
    "collision mask",
    cm_contentsNameByIndex,
    ArgCompletion_String(cm_contentsNameByIndex)
)
val cm_drawColor: idCVar =
    idCVar("cm_drawColor", "1 0 0 .5", CVarSystem.CVAR_GAME, "color used to draw the collision models")
val cm_drawFilled: idCVar =
    idCVar("cm_drawFilled", "0", CVarSystem.CVAR_GAME or CVarSystem.CVAR_BOOL, "draw filled polygons")
val cm_drawInternal: idCVar =
    idCVar("cm_drawInternal", "1", CVarSystem.CVAR_GAME or CVarSystem.CVAR_BOOL, "draw internal edges green")
val cm_drawNormals: idCVar =
    idCVar("cm_drawNormals", "0", CVarSystem.CVAR_GAME or CVarSystem.CVAR_BOOL, "draw polygon and edge normals")
val cm_backFaceCull: idCVar =
    idCVar("cm_backFaceCull", "0", CVarSystem.CVAR_GAME or CVarSystem.CVAR_BOOL, "cull back facing polygons")
val cm_debugCollision: idCVar =
    idCVar("cm_debugCollision", "0", CVarSystem.CVAR_GAME or CVarSystem.CVAR_BOOL, "debug the collision detection")

val cm_color: idVec4 = idVec4()

/*
===============================================================================

Speed test code

===============================================================================
*/

val cm_testCollision: idCVar = idCVar("cm_testCollision", "0", CVarSystem.CVAR_GAME or CVarSystem.CVAR_BOOL, "")
val cm_testRotation: idCVar = idCVar("cm_testRotation", "1", CVarSystem.CVAR_GAME or CVarSystem.CVAR_BOOL, "")
val cm_testModel: idCVar = idCVar("cm_testModel", "0", CVarSystem.CVAR_GAME or CVarSystem.CVAR_INTEGER, "")
val cm_testTimes: idCVar = idCVar("cm_testTimes", "1000", CVarSystem.CVAR_GAME or CVarSystem.CVAR_INTEGER, "")
val cm_testRandomMany: idCVar = idCVar("cm_testRandomMany", "0", CVarSystem.CVAR_GAME or CVarSystem.CVAR_BOOL, "")
val cm_testOrigin: idCVar = idCVar("cm_testOrigin", "0 0 0", CVarSystem.CVAR_GAME, "")
val cm_testReset: idCVar = idCVar("cm_testReset", "0", CVarSystem.CVAR_GAME or CVarSystem.CVAR_BOOL, "")
val cm_testBox: idCVar = idCVar("cm_testBox", "-16 -16 0 16 16 64", CVarSystem.CVAR_GAME, "")
val cm_testBoxRotation: idCVar = idCVar("cm_testBoxRotation", "0 0 0", CVarSystem.CVAR_GAME, "")
val cm_testWalk: idCVar = idCVar("cm_testWalk", "1", CVarSystem.CVAR_GAME or CVarSystem.CVAR_BOOL, "")
val cm_testLength: idCVar = idCVar("cm_testLength", "1024", CVarSystem.CVAR_GAME or CVarSystem.CVAR_FLOAT, "")
val cm_testRadius: idCVar = idCVar("cm_testRadius", "64", CVarSystem.CVAR_GAME or CVarSystem.CVAR_FLOAT, "")
val cm_testAngle: idCVar = idCVar("cm_testAngle", "60", CVarSystem.CVAR_GAME or CVarSystem.CVAR_FLOAT, "")

// NOTE: C++ uses unsigned int for timing stats. Kotlin uses signed Int which is
// sufficient since these are millisecond timings that won't exceed Int.MAX_VALUE.
var total_translation = 0
var min_translation = 999999
var max_translation = 0
var num_translation = 0
var total_rotation = 0
var min_rotation = 999999
var max_rotation = 0
var num_rotation = 0
val start: idVec3 = idVec3()
var testend: Array<idVec3>? = null

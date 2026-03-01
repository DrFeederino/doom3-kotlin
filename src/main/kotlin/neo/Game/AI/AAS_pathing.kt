/*
 * Copyright (C) 1999-2011 id Software LLC, a ZeniMax Media company.
 * Translated to Kotlin by Dr. Feederino with support of Claude Code
 *
 * This file is part of the Doom 3 Kotlin project.
 * Original source: neo/Game/ai/AAS_pathing.cpp
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
 */

package neo.Game.AI

const val SUBSAMPLE_WALK_PATH = 1
const val SUBSAMPLE_FLY_PATH = 0

const val maxWalkPathIterations = 10
const val maxWalkPathDistance = 500.0f
const val walkPathSampleDistance = 8.0f

const val maxFlyPathIterations = 10
const val maxFlyPathDistance = 500.0f
const val flyPathSampleDistance = 8.0f

/*
 wallEdge_s - used by SortWallEdges to build continuous wall edge sequences
 C++ original: typedef struct wallEdge_s { ... } wallEdge_t;
 */
class wallEdge_s {
    var edgeNum: Int = 0
    var verts: IntArray = IntArray(2)
    var next: wallEdge_s? = null
}

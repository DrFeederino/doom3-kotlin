/*
 * Copyright (C) 1999-2011 id Software LLC, a ZeniMax Media company.
 * Translated to Kotlin by Dr. Feederino with support of Claude Code
 *
 * This file is part of the Doom 3 Kotlin project.
 * Original source: neo/Game/ai/AAS.h, neo/Game/ai/AAS.cpp
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

import neo.Game.AI.AASFile.aasTrace_s
import neo.Game.AI.AASFile.idAASSettings
import neo.Game.AI.AASFile.idReachability
import neo.idlib.BV.idBounds
import neo.idlib.Text.Str.idStr
import neo.idlib.containers.CInt
import neo.idlib.math.idPlane
import neo.idlib.math.idVec3

/*
 ===============================================================================

     Area Awareness System

 ===============================================================================
 */

object AAS {

    // enum {
    const val PATHTYPE_WALK = 0
    const val PATHTYPE_WALKOFFLEDGE = 1
    const val PATHTYPE_BARRIERJUMP = 2
    const val PATHTYPE_JUMP = 3
    // };

    class aasPath_s {
        var type: Int = 0                               // path type
        val moveGoal: idVec3 = idVec3()                 // point the AI should move towards
        var moveAreaNum: Int = 0                         // number of the area the AI should move towards
        val secondaryGoal: idVec3 = idVec3()            // secondary move goal for complex navigation
        var reachability: idReachability? = null         // reachability used for navigation
    }

    class aasGoal_s {
        var areaNum: Int = 0                            // area the goal is in
        val origin: idVec3 = idVec3()                   // position of goal
    }

    class aasObstacle_s {
        val absBounds: idBounds = idBounds()            // absolute bounds of obstacle
        val expAbsBounds: idBounds = idBounds()         // expanded absolute bounds of obstacle
    }

    abstract class idAASCallback {
        abstract fun TestArea(aas: idAAS, areaNum: Int): Boolean
    }

    abstract class idAAS {

        // Initialize for the given map.
        abstract fun Init(mapName: idStr, /*unsigned int*/ mapFileCRC: Long): Boolean

        // Print AAS stats.
        abstract fun Stats()

        // Test from the given origin.
        abstract fun Test(origin: idVec3)

        // Get the AAS settings.
        abstract fun GetSettings(): idAASSettings?

        // Returns the number of the area the origin is in.
        abstract fun PointAreaNum(origin: idVec3): Int

        // Returns the number of the nearest reachable area for the given point.
        abstract fun PointReachableAreaNum(origin: idVec3, bounds: idBounds, areaFlags: Int): Int

        // Returns the number of the first reachable area in or touching the bounds.
        abstract fun BoundsReachableAreaNum(bounds: idBounds, areaFlags: Int): Int

        // Push the point into the area.
        abstract fun PushPointIntoAreaNum(areaNum: Int, origin: idVec3)

        // Returns a reachable point inside the given area.
        abstract fun AreaCenter(areaNum: Int): idVec3

        // Returns the area flags.
        abstract fun AreaFlags(areaNum: Int): Int

        // Returns the travel flags for traveling through the area.
        abstract fun AreaTravelFlags(areaNum: Int): Int

        // Trace through the areas and report the first collision.
        abstract fun Trace(trace: aasTrace_s, start: idVec3, end: idVec3): Boolean

        // Get a plane for a trace.
        abstract fun GetPlane(planeNum: Int): idPlane

        // Get wall edges.
        abstract fun GetWallEdges(
            areaNum: Int,
            bounds: idBounds,
            travelFlags: Int,
            edges: IntArray,
            maxEdges: Int
        ): Int

        // Sort the wall edges to create continuous sequences of walls.
        abstract fun SortWallEdges(edges: IntArray, numEdges: Int)

        // Get the vertex numbers for an edge.
        abstract fun GetEdgeVertexNumbers(edgeNum: Int, verts: IntArray /*[2]*/)

        // Get an edge.
        abstract fun GetEdge(edgeNum: Int, start: idVec3, end: idVec3)

        // Find all areas within or touching the bounds with the given contents and disable/enable them for routing.
        abstract fun SetAreaState(bounds: idBounds, areaContents: Int, disabled: Boolean): Boolean

        // Add an obstacle to the routing system.
        abstract fun /*aasHandle_t*/ AddObstacle(bounds: idBounds): Int

        // Remove an obstacle from the routing system.
        abstract fun RemoveObstacle(/*aasHandle_t*/ handle: Int)

        // Remove all obstacles from the routing system.
        abstract fun RemoveAllObstacles()

        // Returns the travel time towards the goal area in 100th of a second.
        abstract fun TravelTimeToGoalArea(areaNum: Int, origin: idVec3, goalAreaNum: Int, travelFlags: Int): Int

        // Get the travel time and first reachability to be used towards the goal, returns true if there is a path.
        abstract fun RouteToGoalArea(
            areaNum: Int,
            origin: idVec3,
            goalAreaNum: Int,
            travelFlags: Int,
            travelTime: CInt,
            reach: Array<idReachability?>
        ): Boolean

        // Creates a walk path towards the goal.
        abstract fun WalkPathToGoal(
            path: aasPath_s,
            areaNum: Int,
            origin: idVec3,
            goalAreaNum: Int,
            goalOrigin: idVec3,
            travelFlags: Int
        ): Boolean

        // Returns true if one can walk along a straight line from the origin to the goal origin.
        abstract fun WalkPathValid(
            areaNum: Int,
            origin: idVec3,
            goalAreaNum: Int,
            goalOrigin: idVec3,
            travelFlags: Int,
            endPos: idVec3,
            endAreaNum: CInt
        ): Boolean

        // Creates a fly path towards the goal.
        abstract fun FlyPathToGoal(
            path: aasPath_s,
            areaNum: Int,
            origin: idVec3,
            goalAreaNum: Int,
            goalOrigin: idVec3,
            travelFlags: Int
        ): Boolean

        // Returns true if one can fly along a straight line from the origin to the goal origin.
        abstract fun FlyPathValid(
            areaNum: Int,
            origin: idVec3,
            goalAreaNum: Int,
            goalOrigin: idVec3,
            travelFlags: Int,
            endPos: idVec3,
            endAreaNum: CInt
        ): Boolean

        // Show the walk path from the origin towards the area.
        abstract fun ShowWalkPath(origin: idVec3, goalAreaNum: Int, goalOrigin: idVec3)

        // Show the fly path from the origin towards the area.
        abstract fun ShowFlyPath(origin: idVec3, goalAreaNum: Int, goalOrigin: idVec3)

        // Find the nearest goal which satisfies the callback.
        abstract fun FindNearestGoal(
            goal: aasGoal_s,
            areaNum: Int,
            origin: idVec3,
            target: idVec3,
            travelFlags: Int,
            obstacles: Array<aasObstacle_s>,
            numObstacles: Int,
            callback: idAASCallback
        ): Boolean

    }
}

package neo.Game.AI

import neo.Game.AI.AAS_local.idAASLocal
import neo.Tools.Compilers.AAS.AASFile.aasTrace_s
import neo.Tools.Compilers.AAS.AASFile.idAASSettings
import neo.Tools.Compilers.AAS.AASFile.idReachability
import neo.idlib.BV.idBounds
import neo.idlib.Text.Str.idStr
import neo.idlib.containers.CInt
import neo.idlib.math.idPlane
import neo.idlib.math.idVec3

object AAS {
    const val PATHTYPE_BARRIERJUMP = 2
    const val PATHTYPE_JUMP = 3

    /*
     ===============================================================================

     Area Awareness System

     ===============================================================================
     */
    // enum {
    const val PATHTYPE_WALK = 0
    const val PATHTYPE_WALKOFFLEDGE = 1

    // };
    class aasPath_s {
        var moveAreaNum // number of the area the AI should move towards
                = 0
        val moveGoal: idVec3 = idVec3() // point the AI should move towards
        var reachability // reachability used for navigation
                : idReachability? = null
        val secondaryGoal: idVec3 = idVec3() // secondary move goal for complex navigation
        var type // path type
                = 0
    }

    class aasGoal_s {
        var areaNum // area the goal is in
                = 0
        val origin: idVec3 = idVec3() // position of goal
    }

    class aasObstacle_s {
        val absBounds: idBounds = idBounds() // absolute bounds of obstacle
        val expAbsBounds: idBounds = idBounds() // expanded absolute bounds of obstacle
    }

    abstract class idAASCallback {
        // virtual						~idAASCallback() {};
        abstract fun TestArea(aas: idAAS, areaNum: Int): Boolean
    }

    abstract class idAAS {
        // virtual						~idAAS() = 0;
        // Initialize for the given map.
        abstract fun Init(
            mapName: idStr,  /*unsigned int*/
            mapFileCRC: Long
        ): Boolean

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
        abstract fun  /*aasHandle_t*/AddObstacle(bounds: idBounds): Int

        // Remove an obstacle from the routing system.
        abstract fun RemoveObstacle(   /*aasHandle_t*/handle: Int)

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

        companion object {
            fun Alloc(): idAAS {
                return idAASLocal()
            }
        }
    }
}
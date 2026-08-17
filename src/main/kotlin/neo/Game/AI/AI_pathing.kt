/*
 * Copyright (C) 1999-2011 id Software LLC, a ZeniMax Media company.
 * Translated to Kotlin by Dr. Feederino with support of Claude Code
 *
 * This file is part of the Doom 3 Kotlin project.
 * Original source: neo/Game/ai/AI_pathing.cpp
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

import neo.Game.AI.AAS.idAAS
import neo.Game.AI.AASFile.aasTrace_s
import neo.Game.GameSys.SysCvar
import neo.Game.Game_local
import neo.Game.Moveable.idMoveable
import neo.Game.Physics.Clip.idClipModel
import neo.Game.Physics.Physics.idPhysics
import neo.Game.idActor
import neo.Game.idEntity
import neo.cm.CM_BOX_EPSILON
import neo.cm.trace_s
import neo.idlib.BV.Box.idBox
import neo.idlib.BV.idBounds
import neo.idlib.colorCyan
import neo.idlib.containers.CFloat
import neo.idlib.containers.CInt
import neo.idlib.containers.Queue.idQueueTemplate
import neo.idlib.geometry.Winding2D.idWinding2D
import neo.idlib.geometry.Winding2D.idWinding2D.Companion.Plane2DFromPoints
import neo.idlib.math.*
import neo.ui.DeviceContext.idDeviceContext
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos

const val CLIP_BOUNDS_EPSILON = 10.0f
const val MAX_AAS_WALL_EDGES = 256
const val MAX_FRAME_SLIDE = 5
const val MAX_OBSTACLES = 256
const val MAX_OBSTACLE_PATH = 64

/*
 ===============================================================================

 Dynamic Obstacle Avoidance

 - assumes the AI lives inside a bounding box aligned with the gravity direction
 - obstacles in proximity of the AI are gathered
 - if obstacles are found the AAS walls are also considered as obstacles
 - every obstacle is represented by an oriented bounding box (OBB)
 - an OBB is projected onto a 2D plane orthogonal to AI's gravity direction
 - the 2D windings of the projections are expanded for the AI bbox
 - a path tree is build using clockwise and counter clockwise edge walks along the winding edges
 - the path tree is pruned and optimized
 - the shortest path is chosen for navigation

 ===============================================================================
 */
const val MAX_OBSTACLE_RADIUS = 256.0f
const val MAX_PATH_NODES = 256

//
const val OVERCLIP = 1.001f
const val PUSH_OUTSIDE_OBSTACLES = 0.5f

//
//    static idBlockAlloc<pathNode_s> pathNodeAllocator = new Heap.idBlockAlloc<>(128);
var pathNodeAllocator = 0

//
/*
 ============
 LineIntersectsPath
 ============
 */
fun LineIntersectsPath(start: idVec2, end: idVec2, node: pathNode_s): Boolean {
    var node = node
    var d0: Float
    var d1: Float
    var d2: Float
    var d3: Float
    val plane1 = idVec3()
    val plane2 = idVec3()
    plane1.set(Plane2DFromPoints(start, end))
    d0 = plane1.x * node.pos.x + plane1.y * node.pos.y + plane1.z
    while (node.parent != null) {
        d1 = plane1.x * node.parent!!.pos.x + plane1.y * node.parent!!.pos.y + plane1.z
        if ((FLOATSIGNBITSET(d0) xor FLOATSIGNBITSET(d1)) != 0) {
            plane2.set(Plane2DFromPoints(node.pos, node.parent!!.pos))
            d2 = plane2.x * start.x + plane2.y * start.y + plane2.z
            d3 = plane2.x * end.x + plane2.y * end.y + plane2.z
            if ((FLOATSIGNBITSET(d2) xor FLOATSIGNBITSET(d3)) != 0) {
                return true
            }
        }
        d0 = d1
        node = node.parent!!
    }
    return false
}

/*
 ============
 PointInsideObstacle
 ============
 */
fun PointInsideObstacle(obstacles: Array<obstacle_s>, numObstacles: Int, point: idVec2): Int {
    var i: Int
    i = 0
    while (i < numObstacles) {
        val bounds = obstacles[i].bounds
        if (point.x < bounds[0].x || point.y < bounds[0].y || point.x > bounds[1].x || point.y > bounds[1].y) {
            i++
            continue
        }
        if (!obstacles[i].winding.PointInside(point, 0.1f)) {
            i++
            continue
        }
        return i
        i++
    }
    return -1
}

/*
 ============
 GetPointOutsideObstacles
 ============
 */
fun GetPointOutsideObstacles(
    obstacles: Array<obstacle_s>, numObstacles: Int, point: idVec2, obstacle: CInt, edgeNum: CInt
) {
    var i: Int
    var j: Int
    var k: Int
    var n: Int
    var bestObstacle: Int
    var bestEdgeNum: Int
    var queueStart: Int
    var queueEnd: Int
    val edgeNums = IntArray(2)
    var d: Float
    var bestd: Float
    val scale = Array(2) { CFloat() }
    val plane = idVec3()
    val bestPlane = idVec3()
    val newPoint = idVec2()
    val dir = idVec2()
    val bestPoint = idVec2()
    val queue: IntArray
    val obstacleVisited: BooleanArray
    val w1 = idWinding2D()
    val w2 = idWinding2D()
    obstacle._val = -1
    edgeNum._val = -1
    bestObstacle = PointInsideObstacle(obstacles, numObstacles, point)
    if (bestObstacle == -1) {
        return
    }
    val w = obstacles[bestObstacle].winding
    bestd = idMath.INFINITY
    bestEdgeNum = 0
    i = 0
    while (i < w.GetNumPoints()) {
        plane.set(Plane2DFromPoints(w[(i + 1) % w.GetNumPoints()], w[i], true))
        d = plane.x * point.x + plane.y * point.y + plane.z
        if (d < bestd) {
            bestd = d
            bestPlane.set(plane)
            bestEdgeNum = i
        } // if this is a wall always try to pop out at the first edge
        if (obstacles[bestObstacle].entity == null) {
            break
        }
        i++
    }

    if (i == 0) {
        return
    }

    newPoint.set(point - bestPlane.ToVec2() * (bestd + PUSH_OUTSIDE_OBSTACLES))
    if (PointInsideObstacle(obstacles, numObstacles, newPoint) == -1) {
        point.set(newPoint)
        obstacle._val = bestObstacle
        edgeNum._val = bestEdgeNum
        return
    }
    queue = IntArray(numObstacles)
    obstacleVisited = BooleanArray(numObstacles)
    queueStart = 0
    queueEnd = 1
    queue[0] = bestObstacle

    //	memset( obstacleVisited, 0, numObstacles * sizeof( obstacleVisited[0] ) );
    obstacleVisited[bestObstacle] = true
    bestd = idMath.INFINITY // FIX: Restructured from C++ for-loop pattern to avoid OOB on queue array.
    // C++ for(i=queue[0]; queueStart<queueEnd; i=queue[++queueStart]) reads past
    // array end in the update clause when loop terminates (benign UB in C++, OOB crash in Kotlin).
    while (queueStart < queueEnd) {
        i = queue[queueStart]
        w1.set(obstacles[i].winding)
        w1.Expand(PUSH_OUTSIDE_OBSTACLES)
        j = 0
        while (j < numObstacles) {

            // if the obstacle has been visited already
            if (obstacleVisited[j]) {
                j++
                continue
            } // if the bounds do not intersect
            if (obstacles[j].bounds[0].x > obstacles[i].bounds[1].x || obstacles[j].bounds[0].y > obstacles[i].bounds[1].y || obstacles[j].bounds[1].x < obstacles[i].bounds[0].x || obstacles[j].bounds[1].y < obstacles[i].bounds[0].y) {
                j++
                continue
            }
            queue[queueEnd++] = j
            obstacleVisited[j] = true
            w2.set(obstacles[j].winding)
            w2.Expand(0.2f)
            k = 0
            while (k < w1.GetNumPoints()) {
                dir.set(w1[(k + 1) % w1.GetNumPoints()] - w1[k])
                if (!w2.RayIntersection(w1[k], dir, scale[0], scale[1], edgeNums)) {
                    k++
                    continue
                }
                n = 0
                while (n < 2) {
                    newPoint.set(w1[k] + dir * scale[n]._val)
                    if (PointInsideObstacle(obstacles, numObstacles, newPoint) == -1) {
                        d = (newPoint - point).LengthSqr()
                        if (d < bestd) {
                            bestd = d
                            bestPoint.set(newPoint)
                            bestEdgeNum = edgeNums[n]
                            bestObstacle = j
                        }
                    }
                    n++
                }
                k++
            }
            j++
        }
        if (bestd < idMath.INFINITY) {
            point.set(bestPoint)
            obstacle._val = bestObstacle
            edgeNum._val = bestEdgeNum
            return
        }
        queueStart++
    }
    Game_local.gameLocal.Warning("GetPointOutsideObstacles: no valid point found")
}

/*
 ============
 GetFirstBlockingObstacle
 ============
 */
fun GetFirstBlockingObstacle(
    obstacles: Array<obstacle_s>,
    numObstacles: Int,
    skipObstacle: Int,
    startPos: idVec2,
    delta: idVec2,
    blockingScale: CFloat,
    blockingObstacle: CInt,
    blockingEdgeNum: CInt
): Boolean {
    var i: Int
    val edgeNums = IntArray(2)
    val dist: Float
    val scale1 = CFloat()
    val scale2 = CFloat()
    val bounds: Array<idVec2> = idVec2.generateArray(2)

    // get bounds for the current movement delta
    bounds[0] = startPos - idVec2(CM_BOX_EPSILON, CM_BOX_EPSILON)
    bounds[1] = startPos + idVec2(CM_BOX_EPSILON, CM_BOX_EPSILON)
    bounds[FLOATSIGNBITNOTSET(delta.x)].x += delta.x
    bounds[FLOATSIGNBITNOTSET(delta.y)].y += delta.y

    // test for obstacles blocking the path
    blockingScale._val = idMath.INFINITY
    dist = delta.Length()
    i = 0
    while (i < numObstacles) {
        if (i == skipObstacle) {
            i++
            continue
        }
        if (bounds[0].x > obstacles[i].bounds[1].x || bounds[0].y > obstacles[i].bounds[1].y || bounds[1].x < obstacles[i].bounds[0].x || bounds[1].y < obstacles[i].bounds[0].y) {
            i++
            continue
        }
        if (obstacles[i].winding.RayIntersection(startPos, delta, scale1, scale2, edgeNums)) {
            if (scale1._val < blockingScale._val && scale1._val * dist > -0.01f && scale2._val * dist > 0.01f) {
                blockingScale._val = scale1._val
                blockingObstacle._val = i
                blockingEdgeNum._val = edgeNums[0]
            }
        }
        i++
    }
    return blockingScale._val < 1.0f
}

/*
 ============
 GetObstacles
 ============
 */
fun GetObstacles(
    physics: idPhysics,
    aas: idAAS,
    ignore: idEntity?,
    areaNum: Int,
    startPos: idVec3,
    seekPos: idVec3,
    obstacles: Array<obstacle_s>,
    maxObstacles: Int,
    clipBounds: idBounds
): Int {
    var i: Int
    var j: Int
    val numListedClipModels: Int
    var numObstacles: Int
    var numVerts: Int
    val clipMask: Int
    val blockingObstacle = CInt()
    val blockingEdgeNum = CInt()
    val wallEdges = IntArray(MAX_AAS_WALL_EDGES)
    val verts = IntArray(2)
    val lastVerts = IntArray(2)
    val nextVerts = IntArray(2)
    val numWallEdges: Int
    val stepHeight = CFloat()
    val headHeight = CFloat()
    val blockingScale = CFloat()
    val min = CFloat()
    val max = CFloat()
    val seekDelta = idVec3()
    val start = idVec3()
    val end = idVec3()
    val nextStart = idVec3()
    val nextEnd = idVec3()
    val silVerts: Array<idVec3> = idVec3.generateArray(32)
    val edgeDir = idVec2()
    val edgeNormal = idVec2()
    val nextEdgeDir = idVec2()
    val nextEdgeNormal = idVec2()
    val lastEdgeNormal = idVec2()
    val expBounds: Array<idVec2> = idVec2.generateArray(2)
    idVec2()
    var obPhys: idPhysics
    var box: idBox
    var obEnt: idEntity
    var clipModel: idClipModel
    val clipModelList = arrayOfNulls<idClipModel>(Game_local.MAX_GENTITIES)

    numObstacles = 0

    seekDelta.set(seekPos - startPos)
    expBounds[0].set(physics.GetBounds()[0].ToVec2() - idVec2(CM_BOX_EPSILON, CM_BOX_EPSILON))
    expBounds[1].set(physics.GetBounds()[1].ToVec2() + idVec2(CM_BOX_EPSILON, CM_BOX_EPSILON))

    physics.GetAbsBounds().AxisProjection(physics.GetGravityNormal().unaryMinus(), stepHeight, headHeight)
    stepHeight._val += aas.GetSettings()!!.maxStepHeight._val

    // clip bounds for the obstacle search space
    clipBounds[0].set(clipBounds[1].set(startPos))
    clipBounds.AddPoint(seekPos)
    clipBounds.ExpandSelf(MAX_OBSTACLE_RADIUS)
    clipMask = physics.GetClipMask()

    // find all obstacles touching the clip bounds
    numListedClipModels = Game_local.gameLocal.clip.ClipModelsTouchingBounds(
        clipBounds, clipMask, clipModelList, Game_local.MAX_GENTITIES
    )
    i = 0
    while (i < numListedClipModels && numObstacles < MAX_OBSTACLES) {
        clipModel = clipModelList[i]!!
        obEnt = clipModel.GetEntity()!!
        if (!clipModel.IsTraceModel()) {
            i++
            continue
        }
        if (obEnt is idActor) {
            obPhys = obEnt.GetPhysics() // ignore myself, my enemy, and dead bodies
            if (obPhys === physics || obEnt === ignore || obEnt.health <= 0) {
                i++
                continue
            } // if the actor is moving
            val v1 = idVec3(obPhys.GetLinearVelocity())
            if (v1.LengthSqr() > Square(10.0f)) {
                val v2 = idVec3(physics.GetLinearVelocity())
                if (v2.LengthSqr() > Square(10.0f)) { // if moving in about the same direction
                    if (v1.times(v2) > 0.0f) {
                        i++
                        continue
                    }
                }
            }
        } else if (obEnt is idMoveable) { // moveables are considered obstacles
        } else { // ignore everything else
            i++
            continue
        }

        // check if we can step over the object
        clipModel.GetAbsBounds().AxisProjection(physics.GetGravityNormal().unaryMinus(), min, max)
        if (max._val < stepHeight._val || min._val > headHeight._val) { // can step over this one
            i++
            continue
        }

        // project a box containing the obstacle onto the floor plane
        box = idBox(clipModel.GetBounds(), clipModel.GetOrigin(), clipModel.GetAxis())
        numVerts = box.GetParallelProjectionSilhouetteVerts(physics.GetGravityNormal(), silVerts)

        // create a 2D winding for the obstacle;
        val obstacle = obstacles[numObstacles++]
        obstacle.winding.Clear()
        j = 0
        while (j < numVerts) {
            obstacle.winding.AddPoint(silVerts[j].ToVec2())
            j++
        }
        if (SysCvar.ai_showObstacleAvoidance.GetBool()) {
            j = 0
            while (j < numVerts) {
                silVerts[j].z = startPos.z
                j++
            }
            j = 0
            while (j < numVerts) {
                Game_local.gameRenderWorld!!.DebugArrow(
                    idDeviceContext.colorWhite, silVerts[j], silVerts[(j + 1) % numVerts], 4
                )
                j++
            }
        }

        // expand the 2D winding for collision with a 2D box
        obstacle.winding.ExpandForAxialBox(expBounds)
        obstacle.winding.GetBounds(obstacle.bounds)
        obstacle.entity = obEnt
        i++
    }

    // if there are no dynamic obstacles the path should be through valid AAS space
    if (numObstacles == 0) {
        return 0
    }

    // if the current path doesn't intersect any dynamic obstacles the path should be through valid AAS space
    if (PointInsideObstacle(obstacles, numObstacles, startPos.ToVec2()) == -1) {
        if (!GetFirstBlockingObstacle(
                obstacles,
                numObstacles,
                -1,
                startPos.ToVec2(),
                seekDelta.ToVec2(),
                blockingScale,
                blockingObstacle,
                blockingEdgeNum
            )
        ) {
            return 0
        }
    }

    // create obstacles for AAS walls
    if (aas != null) {
        val halfBoundsSize = (expBounds[1].x - expBounds[0].x) * 0.5f
        numWallEdges = aas.GetWallEdges(areaNum, clipBounds, AASFile.TFL_WALK, wallEdges, MAX_AAS_WALL_EDGES)
        aas.SortWallEdges(wallEdges, numWallEdges)
        lastVerts[1] = 0
        lastVerts[0] = lastVerts[1]
        lastEdgeNormal.Zero()
        nextVerts[1] = 0
        nextVerts[0] = nextVerts[1]
        i = 0
        while (i < numWallEdges && numObstacles < MAX_OBSTACLES) {
            aas.GetEdge(wallEdges[i], start, end)
            aas.GetEdgeVertexNumbers(wallEdges[i], verts)
            edgeDir.set(end.ToVec2() - start.ToVec2())
            edgeDir.Normalize()
            edgeNormal.x = edgeDir.y
            edgeNormal.y = -edgeDir.x
            if (i < numWallEdges - 1) {
                aas.GetEdge(wallEdges[i + 1], nextStart, nextEnd)
                aas.GetEdgeVertexNumbers(wallEdges[i + 1], nextVerts)
                nextEdgeDir.set(nextEnd.ToVec2() - nextStart.ToVec2())
                nextEdgeDir.Normalize()
                nextEdgeNormal.x = nextEdgeDir.y
                nextEdgeNormal.y = -nextEdgeDir.x
            }
            val obstacle = obstacles[numObstacles++]
            obstacle.winding.Clear()
            obstacle.winding.AddPoint(end.ToVec2())
            obstacle.winding.AddPoint(start.ToVec2())
            obstacle.winding.AddPoint(start.ToVec2() - edgeDir - edgeNormal * halfBoundsSize)
            obstacle.winding.AddPoint(end.ToVec2() + edgeDir - edgeNormal * halfBoundsSize)
            if (lastVerts[1] == verts[0]) {
                obstacle.winding.minusAssign(2, lastEdgeNormal * halfBoundsSize)
            } else {
                obstacle.winding.minusAssign(1, edgeDir)
            }
            if (verts[1] == nextVerts[0]) {
                obstacle.winding.minusAssign(3, nextEdgeNormal * halfBoundsSize)
            } else {
                obstacle.winding.plusAssign(0, edgeDir)
            }
            obstacle.winding.GetBounds(obstacle.bounds)
            obstacle.entity = null

            //			memcpy( lastVerts, verts, sizeof( lastVerts ) );
            System.arraycopy(verts, 0, lastVerts, 0, lastVerts.size)
            lastEdgeNormal.set(edgeNormal)
            i++
        }
    }

    // show obstacles
    if (SysCvar.ai_showObstacleAvoidance.GetBool()) {
        i = 0
        while (i < numObstacles) {
            val obstacle = obstacles[i]
            j = 0
            while (j < obstacle.winding.GetNumPoints()) {
                silVerts[j].set(obstacle.winding[j])
                silVerts[j].z = startPos.z
                j++
            }
            j = 0
            while (j < obstacle.winding.GetNumPoints()) {
                Game_local.gameRenderWorld!!.DebugArrow(
                    idDeviceContext.colorGreen, silVerts[j], silVerts[(j + 1) % obstacle.winding.GetNumPoints()], 4
                )
                j++
            }
            i++
        }
    }
    return numObstacles
}

/*
 ============
 FreePathTree_r
 ============
 */
fun FreePathTree_r(node: pathNode_s) {
    if (node.children[0] != null) {
        FreePathTree_r(node.children[0]!!)
    }
    if (node.children[1] != null) {
        FreePathTree_r(node.children[1]!!)
    }
    pathNodeAllocator--
}

/*
 ============
 DrawPathTree
 ============
 */
fun DrawPathTree(root: pathNode_s, height: Float) {
    var i: Int
    val start = idVec3()
    val end = idVec3()
    var node: pathNode_s?
    node = root
    while (node != null) {
        i = 0
        while (i < 2) {
            if (node.children[i] != null) {
                start.set(node.pos)
                start.z = height
                end.set(node.children[i]!!.pos)
                end.z = height
                Game_local.gameRenderWorld!!.DebugArrow(
                    if (node.edgeNum == -1) idDeviceContext.colorYellow else if (i != 0) idDeviceContext.colorBlue else idDeviceContext.colorRed,
                    start,
                    end,
                    1
                )
                break
            }
            i++
        }
        node = node.next
    }
}

/*
 ============
 GetPathNodeDelta
 ============
 */
fun GetPathNodeDelta(
    node: pathNode_s, obstacles: Array<obstacle_s>, seekPos: idVec2, blocked: Boolean
): Boolean {
    val numPoints: Int
    var edgeNum: Int
    val facing: Boolean
    val seekDelta = idVec2()
    var n: pathNode_s?
    numPoints = obstacles[node.obstacle].winding.GetNumPoints()

    // get delta along the current edge
    while (true) {
        edgeNum = (node.edgeNum + node.dir) % numPoints
        node.delta.set(obstacles[node.obstacle].winding[edgeNum] - node.pos)
        if (node.delta.LengthSqr() > 0.01f) {
            break
        }
        node.edgeNum = (node.edgeNum + numPoints + (2 * node.dir - 1)) % numPoints
    }

    // if not blocked
    if (!blocked) {

        // test if the current edge faces the goal
        seekDelta.set(seekPos - node.pos)
        facing = (2 * node.dir - 1) * (node.delta.x * seekDelta.y - node.delta.y * seekDelta.x) >= 0.0f

        // if the current edge faces goal and the line from the current
        // position to the goal does not intersect the current path
        if (facing && !LineIntersectsPath(node.pos, seekPos, node.parent!!)) {
            node.delta.set(seekPos - node.pos)
            node.edgeNum = -1
        }
    }

    // if the delta is along the obstacle edge
    if (node.edgeNum != -1) { // if the edge is found going from this node to the root node
        n = node.parent
        while (n != null) {
            if (node.obstacle != n.obstacle || node.edgeNum != n.edgeNum) {
                n = n.parent
                continue
            }

            // test whether or not the edge segments actually overlap
            if (n.pos * node.delta > (node.pos + node.delta) * node.delta) {
                n = n.parent
                continue
            }
            if (node.pos * node.delta > (n.pos + n.delta) * node.delta) {
                n = n.parent
                continue
            }
            break
        }
        return n == null
    }
    return true
}

/*
 ============
 BuildPathTree
 ============
 */
fun BuildPathTree(
    obstacles: Array<obstacle_s>,
    numObstacles: Int,
    clipBounds: idBounds,
    startPos: idVec2,
    seekPos: idVec2,
    path: obstaclePath_s
): pathNode_s {
    var obstaclePoints: Int
    var bestNumNodes = MAX_OBSTACLE_PATH
    val blockingEdgeNum = CInt()
    val blockingObstacle = CInt()
    val blockingScale = CFloat()
    val root: pathNode_s
    var node: pathNode_s?
    var child: pathNode_s // gcc 4.0f
    val pathNodeQueue = idQueueTemplate<pathNode_s?>()
    val treeQueue = idQueueTemplate<pathNode_s?>()
    root = pathNode_s() //pathNodeAllocator.Alloc();
    root.Init()
    root.pos.set(startPos)
    root.delta.set(seekPos - root.pos)
    root.numNodes = 0
    pathNodeQueue.Add(root)
    node = pathNodeQueue.Get()
    while (node != null && pathNodeAllocator < MAX_PATH_NODES) {
        treeQueue.Add(node)

        // if this path has more than twice the number of nodes than the best path so far
        if (node.numNodes > bestNumNodes * 2) {
            node = pathNodeQueue.Get()
            continue
        }

        // don't move outside of the clip bounds
        val endPos = node.pos + node.delta
        if (endPos.x - CLIP_BOUNDS_EPSILON < clipBounds[0].x || endPos.x + CLIP_BOUNDS_EPSILON > clipBounds[1].x || endPos.y - CLIP_BOUNDS_EPSILON < clipBounds[0].y || endPos.y + CLIP_BOUNDS_EPSILON > clipBounds[1].y) {
            node = pathNodeQueue.Get()
            continue
        }

        // if an obstacle is blocking the path
        if (GetFirstBlockingObstacle(
                obstacles,
                numObstacles,
                node.obstacle,
                node.pos,
                node.delta,
                blockingScale,
                blockingObstacle,
                blockingEdgeNum
            )
        ) {
            if (path.firstObstacle == null) {
                path.firstObstacle = obstacles[blockingObstacle._val].entity
            }
            node.delta.timesAssign(blockingScale._val)
            if (node.edgeNum == -1) {
                node.children[0] = pathNode_s() // pathNodeAllocator.Alloc();
                node.children[0]!!.Init()
                node.children[1] = pathNode_s() //pathNodeAllocator.Alloc();
                node.children[1]!!.Init()
                node.children[0]!!.dir = 0
                node.children[1]!!.dir = 1
                node.children[1]!!.parent = node
                node.children[0]!!.parent = node.children[1]!!.parent
                node.children[1]!!.pos.set(node.pos + node.delta)
                node.children[0]!!.pos.set(node.children[1]!!.pos)
                node.children[1]!!.obstacle = blockingObstacle._val
                node.children[0]!!.obstacle = node.children[1]!!.obstacle
                node.children[1]!!.edgeNum = blockingEdgeNum._val
                node.children[0]!!.edgeNum = node.children[1]!!.edgeNum
                node.children[1]!!.numNodes = node.numNodes + 1
                node.children[0]!!.numNodes = node.children[1]!!.numNodes
                if (GetPathNodeDelta(node.children[0]!!, obstacles, seekPos, true)) {
                    pathNodeQueue.Add(node.children[0])
                }
                if (GetPathNodeDelta(node.children[1]!!, obstacles, seekPos, true)) {
                    pathNodeQueue.Add(node.children[1])
                }
            } else {
                child = pathNode_s()
                node.children[node.dir] = child //pathNodeAllocator.Alloc();
                child.Init()
                child.dir = node.dir
                child.parent = node
                child.pos.set(node.pos + node.delta)
                child.obstacle = blockingObstacle._val
                child.edgeNum = blockingEdgeNum._val
                child.numNodes = node.numNodes + 1
                if (GetPathNodeDelta(child, obstacles, seekPos, true)) {
                    pathNodeQueue.Add(child)
                }
            }
        } else {
            child = pathNode_s()
            node.children[node.dir] = child //pathNodeAllocator.Alloc();
            child.Init()
            child.dir = node.dir
            child.parent = node
            child.pos.set(node.pos + node.delta)
            child.numNodes = node.numNodes + 1

            // there is a free path towards goal
            if (node.edgeNum == -1) {
                if (node.numNodes < bestNumNodes) {
                    bestNumNodes = node.numNodes
                }
                node = pathNodeQueue.Get()
                continue
            }
            child.obstacle = node.obstacle
            obstaclePoints = obstacles[node.obstacle].winding.GetNumPoints()
            child.edgeNum = (node.edgeNum + obstaclePoints + (2 * node.dir - 1)) % obstaclePoints
            if (GetPathNodeDelta(child, obstacles, seekPos, false)) {
                pathNodeQueue.Add(child)
            }
        }
        node = pathNodeQueue.Get()
    }
    return root
}

/*
 ============
 PrunePathTree
 ============
 */
fun PrunePathTree(root: pathNode_s, seekPos: idVec2) {
    var i: Int
    var bestDist: Float
    var node: pathNode_s?
    var lastNode: pathNode_s?
    var n: pathNode_s?
    var bestNode: pathNode_s
    node = root
    while (node != null) {
        node.dist = (seekPos - node.pos).LengthSqr()
        if (node.children[0] != null) {
            node = node.children[0]
        } else if (node.children[1] != null) {
            node = node.children[1]
        } else {

            // find the node closest to the goal along this path
            bestDist = idMath.INFINITY
            bestNode = node
            n = node
            while (n != null) {
                if (n.children[0] != null && n.children[1] != null) {
                    break
                }
                if (n.dist < bestDist) {
                    bestDist = n.dist
                    bestNode = n
                }
                n = n.parent
            }

            // free tree down from the best node
            i = 0
            while (i < 2) {
                if (bestNode.children[i] != null) {
                    FreePathTree_r(bestNode.children[i]!!)
                    bestNode.children[i] = null
                }
                i++
            }
            lastNode = bestNode
            node = bestNode.parent
            while (node != null) {
                if (node.children[1] != null && node.children[1] !== lastNode) {
                    node = node.children[1]
                    break
                }
                lastNode = node
                node = node.parent
            }
        }
    }
}

/*
 ============
 OptimizePath
 ============
 */
fun OptimizePath(
    root: pathNode_s,
    leafNode: pathNode_s,
    obstacles: Array<obstacle_s>,
    numObstacles: Int,
    optimizedPath: Array<idVec2> /*[MAX_OBSTACLE_PATH]*/
): Int {
    var i: Int
    var numPathPoints: Int
    val edgeNums = IntArray(2)
    var curNode: pathNode_s
    var nextNode: pathNode_s
    val curPos = idVec2()
    val curDelta = idVec2()
    val bounds: Array<idVec2> = idVec2.generateArray(2)
    var curLength: Float
    val scale1 = CFloat()
    val scale2 = CFloat()
    optimizedPath[0].set(root.pos)
    numPathPoints = 1
    nextNode = root.also { curNode = it }
    while (curNode !== leafNode) {
        nextNode = leafNode
        while (nextNode.parent !== curNode) {


            // can only take shortcuts when going from one object to another
            if (nextNode.obstacle == curNode.obstacle) {
                nextNode = nextNode.parent!!
                continue
            }
            curPos.set(curNode.pos)
            curDelta.set(nextNode.pos - curPos)
            curLength = curDelta.Length()

            // get bounds for the current movement delta
            bounds[0] = curPos - idVec2(CM_BOX_EPSILON, CM_BOX_EPSILON)
            bounds[1] = curPos + idVec2(CM_BOX_EPSILON, CM_BOX_EPSILON)
            bounds[FLOATSIGNBITNOTSET(curDelta.x)].x += curDelta.x
            bounds[FLOATSIGNBITNOTSET(curDelta.y)].y += curDelta.y

            // test if the shortcut intersects with any obstacles
            i = 0
            while (i < numObstacles) {
                if (bounds[0].x > obstacles[i].bounds[1].x || bounds[0].y > obstacles[i].bounds[1].y || bounds[1].x < obstacles[i].bounds[0].x || bounds[1].y < obstacles[i].bounds[0].y) {
                    i++
                    continue
                }
                if (obstacles[i].winding.RayIntersection(curPos, curDelta, scale1, scale2, edgeNums)) {
                    if (scale1._val >= 0.0f && scale1._val <= 1.0f && (i != nextNode.obstacle || scale1._val * curLength < curLength - 0.5f)) {
                        break
                    }
                    if (scale2._val >= 0.0f && scale2._val <= 1.0f && (i != nextNode.obstacle || scale2._val * curLength < curLength - 0.5f)) {
                        break
                    }
                }
                i++
            }
            if (i >= numObstacles) {
                break
            }
            nextNode = nextNode.parent!!
        }

        // store the next position along the optimized path
        // FIX: Was `optimizedPath[numPathPoints++] = nextNode.pos` which stores a reference.
        // C++ copies the value via idVec2 assignment operator.
        optimizedPath[numPathPoints].set(nextNode.pos)
        numPathPoints++
        curNode = nextNode
    }
    return numPathPoints
}

/*
 ============
 PathLength
 ============
 */
fun PathLength(optimizedPath: Array<idVec2> /*[MAX_OBSTACLE_PATH]*/, numPathPoints: Int, curDir: idVec2): Float {
    var i: Int
    var pathLength: Float

    // calculate the path length
    pathLength = 0.0f
    i = 0
    while (i < numPathPoints - 1) {
        pathLength += (optimizedPath[i + 1] - optimizedPath[i]).LengthFast()
        i++
    }

    // add penalty if this path does not go in the current direction
    if (curDir * (optimizedPath[1] - optimizedPath[0]) < 0.0f) {
        pathLength += 100.0f
    }
    return pathLength
}

/*
 ============
 FindOptimalPath

 Returns true if there is a path all the way to the goal.
 ============
 */
fun FindOptimalPath(
    root: pathNode_s, obstacles: Array<obstacle_s>, numObstacles: Int, height: Float, curDir: idVec3, seekPos: idVec3
): Boolean {
    var i: Int
    var numPathPoints: Int
    var bestNumPathPoints: Int
    var node: pathNode_s?
    var lastNode: pathNode_s
    var bestNode: pathNode_s
    val optimizedPath: Array<idVec2> = idVec2.generateArray(MAX_OBSTACLE_PATH)
    var pathLength: Float
    var bestPathLength: Float
    var pathToGoalExists: Boolean
    var optimizedPathCalculated: Boolean
    optimizedPath[1] = idVec2(-107374176.0f, -107374176.0f)
    seekPos.Zero()
    seekPos.z = height
    pathToGoalExists = false
    optimizedPathCalculated = false
    bestNode = root
    bestPathLength = idMath.INFINITY
    node = root
    while (node != null) {
        pathToGoalExists = pathToGoalExists or (node.dist < 0.1f)
        if (node.dist <= bestNode.dist) {
            if (abs(node.dist - bestNode.dist) < 0.1f) {
                if (!optimizedPathCalculated) {
                    bestNumPathPoints = OptimizePath(root, bestNode, obstacles, numObstacles, optimizedPath)
                    bestPathLength = PathLength(optimizedPath, bestNumPathPoints, curDir.ToVec2())
                    seekPos.set(optimizedPath[1])
                }
                numPathPoints = OptimizePath(root, node, obstacles, numObstacles, optimizedPath)
                pathLength = PathLength(optimizedPath, numPathPoints, curDir.ToVec2())
                if (pathLength < bestPathLength) {
                    bestNode = node
                    bestNumPathPoints = numPathPoints
                    bestPathLength = pathLength
                    seekPos.set(optimizedPath[1])
                }
                optimizedPathCalculated = true
            } else {
                bestNode = node
                optimizedPathCalculated = false
            }
        }
        if (node.children[0] != null) {
            node = node.children[0]!!
        } else if (node.children[1] != null) {
            node = node.children[1]!!
        } else {
            lastNode = node
            node = node.parent
            while (node != null) {
                if (node.children[1] != null && node.children[1] != lastNode) {
                    node = node.children[1]!!
                    break
                }
                lastNode = node
                node = node.parent
            }
        }
    }
    if (!pathToGoalExists) {
        if (root.children[0] != null) {
            seekPos.set(root.children[0]!!.pos)
        } else {
            seekPos.set(root.pos)
        }
    } else if (!optimizedPathCalculated) {
        OptimizePath(root, bestNode, obstacles, numObstacles, optimizedPath)
        seekPos.set(optimizedPath[1])
    }
    if (SysCvar.ai_showObstacleAvoidance.GetBool()) {
        val start = idVec3()
        val end = idVec3()
        end.z = height + 4.0f
        start.z = end.z
        numPathPoints = OptimizePath(root, bestNode, obstacles, numObstacles, optimizedPath)
        i = 0
        while (i < numPathPoints - 1) {
            start.set(optimizedPath[i])
            end.set(optimizedPath[i + 1])
            Game_local.gameRenderWorld!!.DebugArrow(colorCyan, start, end, 1)
            i++
        }
    }
    return pathToGoalExists
}

/*
 ===============================================================================

 Path Prediction

 Uses the AAS to quickly and accurately predict a path for a certain
 period of time based on an initial position and velocity.

 ===============================================================================
 *//*
 ============
 PathTrace

 Returns true if a stop event was triggered.
 ============
 */
fun PathTrace(
    ent: idEntity, aas: idAAS?, start: idVec3, end: idVec3, stopEvent: Int, trace: pathTrace_s, path: predictedPath_s
): Boolean {
    val clipTrace = trace_s()
    val aasTrace = aasTrace_s()

    //	memset( &trace, 0, sizeof( trace ) );
    if (null == aas || aas.GetSettings() == null) {
        Game_local.gameLocal.clip.Translation(
            clipTrace,
            start,
            end,
            ent.GetPhysics().GetClipModel(),
            ent.GetPhysics().GetClipModel()!!.GetAxis(),
            Game_local.MASK_MONSTERSOLID,
            ent
        )

        // NOTE: could do (expensive) ledge detection here for when there is no AAS file
        trace.fraction = clipTrace.fraction
        trace.endPos.set(clipTrace.endpos)
        trace.normal.set(clipTrace.c.normal)
        trace.blockingEntity = Game_local.gameLocal.entities[clipTrace.c.entityNum]
    } else {
        aasTrace.getOutOfSolid = 1 //true;
        if ((stopEvent and SE_ENTER_LEDGE_AREA) != 0) {
            aasTrace.flags = aasTrace.flags or AASFile.AREA_LEDGE
        }
        if ((stopEvent and SE_ENTER_OBSTACLE) != 0) {
            aasTrace.travelFlags = aasTrace.travelFlags or AASFile.TFL_INVALID
        }
        aas.Trace(aasTrace, start, end)
        Game_local.gameLocal.clip.TranslationEntities(
            clipTrace,
            start,
            aasTrace.endpos,
            ent.GetPhysics().GetClipModel(),
            ent.GetPhysics().GetClipModel()!!.GetAxis(),
            Game_local.MASK_MONSTERSOLID,
            ent
        )
        if (clipTrace.fraction >= 1.0f) {
            trace.fraction = aasTrace.fraction
            trace.endPos.set(aasTrace.endpos)
            trace.normal.set(aas.GetPlane(aasTrace.planeNum).Normal())
            trace.blockingEntity = Game_local.gameLocal.world
            if (aasTrace.fraction < 1.0f) {
                if ((stopEvent and SE_ENTER_LEDGE_AREA) != 0) {
                    if ((aas.AreaFlags(aasTrace.blockingAreaNum) and AASFile.AREA_LEDGE) != 0) {
                        path.endPos.set(trace.endPos)
                        path.endNormal.set(trace.normal)
                        path.endEvent = SE_ENTER_LEDGE_AREA
                        path.blockingEntity = trace.blockingEntity
                        if (SysCvar.ai_debugMove.GetBool()) {
                            Game_local.gameRenderWorld!!.DebugLine(
                                idDeviceContext.colorRed, start, aasTrace.endpos
                            )
                        }
                        return true
                    }
                }
                if ((stopEvent and SE_ENTER_OBSTACLE) != 0) {
                    if ((aas.AreaTravelFlags(aasTrace.blockingAreaNum) and AASFile.TFL_INVALID) != 0) {
                        path.endPos.set(trace.endPos)
                        path.endNormal.set(trace.normal)
                        path.endEvent = SE_ENTER_OBSTACLE
                        path.blockingEntity = trace.blockingEntity
                        if (SysCvar.ai_debugMove.GetBool()) {
                            Game_local.gameRenderWorld!!.DebugLine(
                                idDeviceContext.colorRed, start, aasTrace.endpos
                            )
                        }
                        return true
                    }
                }
            }
        } else {
            trace.fraction = clipTrace.fraction
            trace.endPos.set(clipTrace.endpos)
            trace.normal.set(clipTrace.c.normal)
            trace.blockingEntity = Game_local.gameLocal.entities[clipTrace.c.entityNum]
        }
    }
    if (trace.fraction >= 1.0f) {
        trace.blockingEntity = null
    }
    return false
}

/*
 =====================
 Ballistics

 get the ideal aim pitch angle in order to hit the target
 also get the time it takes for the projectile to arrive at the target
 =====================
 */
fun Ballistics(
    start: idVec3, end: idVec3, speed: Float, gravity: Float, bal: Array<ballistics_s> /*[2]*/
): Int {
    var n: Int
    var i: Int
    val x: Float
    val y: Float
    val a: Float
    val b: Float
    val c: Float
    var d: Float
    val sqrtd: Float
    val inva: Float
    val p = FloatArray(2)

    x = (end.ToVec2() - start.ToVec2()).Length()
    y = end[2] - start[2]

    a = 4.0f * y * y + 4.0f * x * x
    b = -4.0f * speed * speed - 4.0f * y * gravity
    c = gravity * gravity

    d = b * b - 4.0f * a * c
    if (d <= 0.0f || a == 0.0f) {
        return 0
    }
    sqrtd = idMath.Sqrt(d)
    inva = 0.5f / a
    p[0] = (-b + sqrtd) * inva
    p[1] = (-b - sqrtd) * inva
    n = 0
    i = 0
    while (i < 2) {
        if (p[i] <= 0.0f) {
            i++
            continue
        }
        d = idMath.Sqrt(p[i])
        bal[n].angle = atan2((0.5f * (2.0f * y * p[i] - gravity) / d).toDouble(), (d * x).toDouble()).toFloat()
        bal[n].time = (x / (cos(bal[n].angle) * speed))
        bal[n].angle = idMath.AngleNormalize180(RAD2DEG(bal[n].angle))
        n++
        i++
    }
    return n
}

/*
 =====================
 HeightForTrajectory

 Returns the maximum hieght of a given trajectory
 =====================
 */
fun HeightForTrajectory(start: idVec3, zVel: Float, gravity: Float): Float {
    val maxHeight: Float
    val t: Float
    t = zVel / gravity // maximum height of projectile
    maxHeight = start.z - 0.5f * gravity * (t * t)
    return maxHeight
}

/*
 ===============================================================================

 Path Prediction

 Uses the AAS to quickly and accurately predict a path for a certain
 period of time based on an initial position and velocity.

 ===============================================================================
 */
class pathTrace_s {
    var blockingEntity: idEntity? = null
    val endPos: idVec3 = idVec3()
    var fraction = 0.0f
    val normal: idVec3 = idVec3()
}

class obstacle_s {
    val bounds: Array<idVec2> = idVec2.generateArray(2)
    var entity: idEntity? = null
    var winding: idWinding2D = idWinding2D()
}

/*
 ===============================================================================

 Trajectory Prediction

 Finds the best collision free trajectory for a clip model based on an
 initial position, target position and speed.

 ===============================================================================
 */
class pathNode_s {
    var children: Array<pathNode_s?> = arrayOfNulls<pathNode_s?>(2)
    val delta: idVec2 = idVec2()
    var dir = 0
    var dist = 0.0f
    var edgeNum = 0
    var next: pathNode_s? = null
    var numNodes = 0
    var obstacle = 0
    var parent: pathNode_s? = null
    val pos: idVec2 = idVec2()
    fun Init() {
        dir = 0
        pos.Zero()
        delta.Zero()
        obstacle = -1
        edgeNum = -1
        numNodes = 0
        next = null
        children[1] = next
        children[0] = children[1]
        parent = children[0]
    }

    fun oSet(parent: pathNode_s) {
        dir = parent.dir
        pos.set(parent.pos)
        delta.set(parent.delta)
        dist = parent.dist
        obstacle = parent.obstacle
        edgeNum = parent.edgeNum
        numNodes = parent.numNodes
        this.parent = parent.parent
        children = parent.children.copyOf() // FIX: was reference copy; C++ copies array by value
        next = parent.next
    }

    init {
        pathNodeAllocator++
    }
}

/*
 =====================
 Ballistics

 get the ideal aim pitch angle in order to hit the target
 also get the time it takes for the projectile to arrive at the target
 =====================
 */
class ballistics_s {
    var angle // angle in degrees in the range [-180, 180]
            = 0.0f
    var time // time it takes before the projectile arrives
            = 0.0f
}

package neo.Tools.Compilers.AAS

import neo.TempDump
import neo.Tools.Compilers.AAS.AASFile.aasArea_s
import neo.Tools.Compilers.AAS.AASFile.aasEdge_s
import neo.Tools.Compilers.AAS.AASFile.aasFace_s
import neo.Tools.Compilers.AAS.AASFile.aasTrace_s
import neo.Tools.Compilers.AAS.AASFile.idReachability
import neo.Tools.Compilers.AAS.AASFile.idReachability_BarrierJump
import neo.Tools.Compilers.AAS.AASFile.idReachability_Fly
import neo.Tools.Compilers.AAS.AASFile.idReachability_Swim
import neo.Tools.Compilers.AAS.AASFile.idReachability_Walk
import neo.Tools.Compilers.AAS.AASFile.idReachability_WalkOffLedge
import neo.Tools.Compilers.AAS.AASFile.idReachability_WaterJump
import neo.Tools.Compilers.AAS.AASFile_local.idAASFileLocal
import neo.framework.Common
import neo.idlib.MapFile.idMapFile
import neo.idlib.math.INTSIGNBITNOTSET
import neo.idlib.math.INTSIGNBITSET
import neo.idlib.math.idPlane
import neo.idlib.math.idVec3
import kotlin.math.abs

const val INSIDEUNITS = 2.0f
const val INSIDEUNITS_FLYEND = 0.5f
const val INSIDEUNITS_SWIMEND = 0.5f
const val INSIDEUNITS_WALKEND = 0.5f
const val INSIDEUNITS_WALKSTART = 0.1f
const val INSIDEUNITS_WATERJUMP = 15.0f

/*
 ===============================================================================

 Reachabilities

 ===============================================================================
 */
internal class idAASReach {
    private val allowFlyReachabilities = false
    private val allowSwimReachabilities = false
    private var file: idAASFileLocal = idAASFileLocal()
    private var mapFile: idMapFile = idMapFile()
    private var numReachabilities = 0

    //
    //
    fun Build(mapFile: idMapFile, file: idAASFileLocal): Boolean {
        var i: Int
        var j: Int
        var lastPercent: Int
        var percent: Int
        this.mapFile = mapFile
        this.file = file
        numReachabilities = 0
        Common.common.Printf("[Reachability]\n")

        // delete all existing reachabilities
        file.DeleteReachabilities()
        FlagReachableAreas(file)
        i = 1
        while (i < file.areas.Num()) {
            if (0 == file.areas[i].flags and AASFile.AREA_REACHABLE_WALK) {
                i++
                continue
            }
            if (file.GetSettings().allowSwimReachabilities._val) {
                Reachability_Swim(i)
            }
            Reachability_EqualFloorHeight(i)
            i++
        }
        lastPercent = -1
        i = 1
        while (i < file.areas.Num()) {
            if (0 == file.areas[i].flags and AASFile.AREA_REACHABLE_WALK) {
                i++
                continue
            }
            j = 0
            while (j < file.areas.Num()) {
                if (i == j) {
                    j++
                    continue
                }
                if (0 == file.areas[j].flags and AASFile.AREA_REACHABLE_WALK) {
                    j++
                    continue
                }
                if (ReachabilityExists(i, j)) {
                    j++
                    continue
                }
                Reachability_Step_Barrier_WaterJump_WalkOffLedge(i, j)
                j++
            }

            //Reachability_WalkOffLedge( i );
            percent = 100 * i / file.areas.Num()
            if (percent > lastPercent) {
                Common.common.Printf("\r%6d%%", percent)
                lastPercent = percent
            }
            i++
        }
        if (file.GetSettings().allowFlyReachabilities._val) {
            i = 1
            while (i < file.areas.Num()) {
                Reachability_Fly(i)
                i++
            }
        }
        file.LinkReversedReachability()
        Common.common.Printf("\r%6d reachabilities\n", numReachabilities)
        return true
    }

    // reachability
    private fun FlagReachableAreas(file: idAASFileLocal) {
        var i: Int
        var numReachableAreas: Int
        numReachableAreas = 0
        i = 1
        while (i < file.areas.Num()) {
            if (file.areas[i].flags and (AASFile.AREA_FLOOR or AASFile.AREA_LADDER) != 0
                || file.areas[i].contents and AASFile.AREACONTENTS_WATER != 0
            ) {
                file.areas[i].flags = file.areas[i].flags or AASFile.AREA_REACHABLE_WALK
            }
            if (file.GetSettings().allowFlyReachabilities._val) {
                file.areas[i].flags = file.areas[i].flags or AASFile.AREA_REACHABLE_FLY
            }
            numReachableAreas++
            i++
        }
        Common.common.Printf("%6d reachable areas\n", numReachableAreas)
    }

    private fun ReachabilityExists(fromAreaNum: Int, toAreaNum: Int): Boolean {
        val area: aasArea_s
        var reach: idReachability?
        area = file.areas[fromAreaNum]
        reach = area.reach
        while (reach != null) {
            if (reach.toAreaNum.toInt() == toAreaNum) {
                return true
            }
            reach = reach.next
        }
        return false
    }

    private fun CanSwimInArea(areaNum: Int): Boolean {
        return file.areas[areaNum].contents and AASFile.AREACONTENTS_WATER != 0
    }

    private fun AreaHasFloor(areaNum: Int): Boolean {
        return file.areas[areaNum].flags and AASFile.AREA_FLOOR != 0
    }

    private fun AreaIsClusterPortal(areaNum: Int): Boolean {
        return file.areas[areaNum].flags and AASFile.AREACONTENTS_CLUSTERPORTAL != 0
    }

    private fun AddReachabilityToArea(reach: idReachability, areaNum: Int) {
        val area: aasArea_s?
        area = file.areas[areaNum]
        reach.next = area.reach
        area.reach = reach
        numReachabilities++
    }

    private fun Reachability_Fly(areaNum: Int) {
        var i: Int
        var faceNum: Int
        var otherAreaNum: Int
        val area: aasArea_s
        var face: aasFace_s
        var reach: idReachability_Fly
        area = file.areas[areaNum]
        i = 0
        while (i < area.numFaces) {
            faceNum = file.faceIndex[area.firstFace + i]
            face = file.faces[abs(faceNum)]
            otherAreaNum = face.areas[INTSIGNBITNOTSET(faceNum)].toInt()
            if (otherAreaNum == 0) {
                i++
                continue
            }
            if (ReachabilityExists(areaNum, otherAreaNum)) {
                i++
                continue
            }

            // create reachability going through this face
            reach = idReachability_Fly()
            reach.travelType = AASFile.TFL_FLY
            reach.toAreaNum = otherAreaNum.toShort()
            reach.fromAreaNum = areaNum.toShort()
            reach.edgeNum = 0
            reach.travelTime = 1
            reach.start.set(file.FaceCenter(abs(faceNum)))
            if (faceNum < 0) {
                reach.end.set(
                    reach.start +
                            file.planeList[face.planeNum].Normal() * INSIDEUNITS_FLYEND
                )
            } else {
                reach.end.set(
                    reach.start.minus(
                        file.planeList[face.planeNum].Normal().times(INSIDEUNITS_FLYEND)
                    )
                )
            }
            AddReachabilityToArea(reach, areaNum)
            i++
        }
    }

    private fun Reachability_Swim(areaNum: Int) {
        var i: Int
        var faceNum: Int
        var otherAreaNum: Int
        val area: aasArea_s
        var face: aasFace_s
        var reach: idReachability_Swim
        if (!CanSwimInArea(areaNum)) {
            return
        }
        area = file.areas[areaNum]
        i = 0
        while (i < area.numFaces) {
            faceNum = file.faceIndex[area.firstFace + i]
            face = file.faces[abs(faceNum)]
            otherAreaNum = face.areas[INTSIGNBITNOTSET(faceNum)].toInt()
            if (otherAreaNum == 0) {
                i++
                continue
            }
            if (!CanSwimInArea(otherAreaNum)) {
                i++
                continue
            }
            if (ReachabilityExists(areaNum, otherAreaNum)) {
                i++
                continue
            }

            // create reachability going through this face
            reach = idReachability_Swim()
            reach.travelType = AASFile.TFL_SWIM
            reach.toAreaNum = otherAreaNum.toShort()
            reach.fromAreaNum = areaNum.toShort()
            reach.edgeNum = 0
            reach.travelTime = 1
            reach.start.set(file.FaceCenter(abs(faceNum)))
            if (faceNum < 0) {
                reach.end.set(
                    reach.start.plus(
                        file.planeList[face.planeNum].Normal().times(INSIDEUNITS_SWIMEND)
                    )
                )
            } else {
                reach.end.set(
                    reach.start.minus(
                        file.planeList[face.planeNum].Normal().times(INSIDEUNITS_SWIMEND)
                    )
                )
            }
            AddReachabilityToArea(reach, areaNum)
            i++
        }
    }

    private fun Reachability_EqualFloorHeight(areaNum: Int) {
        var i: Int
        var k: Int
        var l: Int
        var m: Int
        var n: Int
        var faceNum: Int
        var face1Num: Int
        var face2Num: Int
        var otherAreaNum: Int
        var edge1Num = 0
        var edge2Num: Int
        val area: aasArea_s?
        var otherArea: aasArea_s?
        var face: aasFace_s?
        var face1: aasFace_s?
        var face2: aasFace_s?
        var reach: idReachability_Walk
        if (!AreaHasFloor(areaNum)) {
            return
        }
        area = file.areas[areaNum]
        i = 0
        while (i < area.numFaces) {
            faceNum = file.faceIndex[area.firstFace + i]
            face = file.faces[abs(faceNum)]
            otherAreaNum = face.areas[INTSIGNBITNOTSET(faceNum)].toInt()
            if (!AreaHasFloor(otherAreaNum)) {
                i++
                continue
            }
            otherArea = file.areas[otherAreaNum]
            k = 0
            while (k < area.numFaces) {
                face1Num = file.faceIndex[area.firstFace + k]
                face1 = file.faces[abs(face1Num)]
                if (0 == face1.flags and AASFile.FACE_FLOOR) {
                    k++
                    continue
                }
                l = 0
                while (l < otherArea.numFaces) {
                    face2Num = file.faceIndex[otherArea.firstFace + l]
                    face2 = file.faces[abs(face2Num)]
                    if (0 == face2.flags and AASFile.FACE_FLOOR) {
                        l++
                        continue
                    }
                    m = 0
                    while (m < face1.numEdges) {
                        edge1Num = abs(file.edgeIndex[face1.firstEdge + m])
                        n = 0
                        while (n < face2.numEdges) {
                            edge2Num = abs(file.edgeIndex[face2.firstEdge + n])
                            if (edge1Num == edge2Num) {
                                break
                            }
                            n++
                        }
                        if (n < face2.numEdges) {
                            break
                        }
                        m++
                    }
                    if (m < face1.numEdges) {
                        break
                    }
                    l++
                }
                if (l < otherArea.numFaces) {
                    break
                }
                k++
            }
            if (k < area.numFaces) {
                // create reachability
                reach = idReachability_Walk()
                reach.travelType = AASFile.TFL_WALK
                reach.toAreaNum = otherAreaNum.toShort()
                reach.fromAreaNum = areaNum.toShort()
                reach.edgeNum = abs(edge1Num)
                reach.travelTime = 1
                reach.start.set(file.EdgeCenter(edge1Num))
                if (faceNum < 0) {
                    reach.end.set(
                        reach.start.plus(
                            file.planeList[face.planeNum].Normal().times(INSIDEUNITS_WALKEND)
                        )
                    )
                } else {
                    reach.end.set(
                        reach.start.minus(
                            file.planeList[face.planeNum].Normal().times(INSIDEUNITS_SWIMEND)
                        )
                    )
                }
                AddReachabilityToArea(reach, areaNum)
            }
            i++
        }
    }

    private fun Reachability_Step_Barrier_WaterJump_WalkOffLedge(fromAreaNum: Int, toAreaNum: Int): Boolean {
        var i: Int
        var j: Int
        var k: Int
        var l: Int
        var edge1Num: Int
        var edge2Num: Int
        val areas = IntArray(10)
        var floor_bestArea1FloorEdgeNum = 0
        var floor_bestArea2FloorEdgeNum: Int
        var floor_foundReach: Int
        var water_bestArea1FloorEdgeNum: Int
        var water_bestArea2FloorEdgeNum: Int
        var water_foundReach: Int
        var side1: Int
        var floorFace1Num: Int
        var faceSide1: Boolean
        var dist: Float
        var dist1: Float
        var dist2: Float
        var diff: Float
        var invGravityDot: Float
        var orthogonalDot: Float
        var x1: Float
        var x2: Float
        var x3: Float
        var x4: Float
        var y1: Float
        var y2: Float
        var y3: Float
        var y4: Float
        var tmp: Float
        var y: Float
        var length: Float
        var floor_bestLength: Float
        var water_bestLength: Float
        var floor_bestDist: Float
        var water_bestDist: Float
        val v1 = idVec3()
        val v2 = idVec3()
        val v3 = idVec3()
        val v4 = idVec3()
        val tmpv = idVec3()
        val p1area1 = idVec3()
        val p1area2 = idVec3()
        val p2area1 = idVec3()
        val p2area2 = idVec3()
        val normal = idVec3()
        val orthogonal = idVec3()
        val edgeVec = idVec3()
        val start = idVec3()
        val end = idVec3()
        val floor_bestStart = idVec3()
        val floor_bestEnd = idVec3()
        val floor_bestNormal = idVec3()
        val water_bestStart = idVec3()
        val water_bestEnd = idVec3()
        val water_bestNormal = idVec3()
        val testPoint = idVec3()
        var plane: idPlane
        val area1: aasArea_s?
        val area2: aasArea_s?
        var floorFace1: aasFace_s?
        var floorFace2: aasFace_s?
        var floor_bestFace1: aasFace_s?
        var water_bestFace1: aasFace_s?
        var edge1: aasEdge_s?
        var edge2: aasEdge_s?
        val walkReach: idReachability_Walk
        val barrierJumpReach: idReachability_BarrierJump
        val waterJumpReach: idReachability_WaterJump
        val walkOffLedgeReach: idReachability_WalkOffLedge
        val trace = aasTrace_s()

        // must be able to walk or swim in the first area
        if (!AreaHasFloor(fromAreaNum) && !CanSwimInArea(fromAreaNum)) {
            return false
        }
        if (!AreaHasFloor(toAreaNum) && !CanSwimInArea(toAreaNum)) {
            return false
        }
        area1 = file.areas[fromAreaNum]
        area2 = file.areas[toAreaNum]

        // if the areas are not near anough in the x-y direction
        i = 0
        while (i < 2) {
            if (area1.bounds[0, i] > area2.bounds[1, i] + 2.0f) {
                return false
            }
            if (area1.bounds[1, i] < area2.bounds[0, i] - 2.0f) {
                return false
            }
            i++
        }
        floor_foundReach = 0 //false;
        floor_bestDist = 99999.0f
        floor_bestLength = 0.0f
        floor_bestArea2FloorEdgeNum = 0
        water_foundReach = 0 //false;
        water_bestDist = 99999.0f
        water_bestLength = 0.0f
        water_bestArea2FloorEdgeNum = 0
        i = 0
        while (i < area1.numFaces) {
            floorFace1Num = file.faceIndex[area1.firstFace + i]
            faceSide1 = floorFace1Num < 0
            floorFace1 = file.faces[abs(floorFace1Num)]

            // if this isn't a floor face
            if (0 == floorFace1.flags and AASFile.FACE_FLOOR) {

                // if we can swim in the first area
                if (CanSwimInArea(fromAreaNum)) {

                    // face plane must be more or less horizontal
                    plane = file.planeList[floorFace1.planeNum xor if (!faceSide1) 1 else 0]
                    if (plane.Normal()
                            .times(file.settings.invGravityDir) < file.settings.minFloorCos._val
                    ) {
                        i++
                        continue
                    }
                } else {
                    // if we can't swim in the area it must be a ground face
                    i++
                    continue
                }
            }
            k = 0
            while (k < floorFace1.numEdges) {
                edge1Num = file.edgeIndex[floorFace1.firstEdge + k]
                side1 = TempDump.btoi(edge1Num < 0)
                // NOTE: for water faces we must take the side area 1 is on into
                // account because the face is shared and doesn't have to be oriented correctly
                if (0 == floorFace1.flags and AASFile.FACE_FLOOR) {
                    side1 = TempDump.btoi(TempDump.itob(side1) == faceSide1)
                }
                edge1Num = abs(edge1Num)
                edge1 = file.edges[edge1Num]
                // vertices of the edge
                v1.set(file.vertices[edge1.vertexNum[TempDump.SNOT(side1.toDouble())]])
                v2.set(file.vertices[edge1.vertexNum[side1]])
                // get a vertical plane through the edge
                // NOTE: normal is pointing into area 2 because the face edges are stored counter clockwise
                edgeVec.set(v2.minus(v1))
                normal.set(edgeVec.Cross(file.settings.invGravityDir))
                normal.Normalize()
                dist = normal.times(v1)

                // check the faces from the second area
                j = 0
                while (j < area2.numFaces) {
                    floorFace2 = file.faces[abs(file.faceIndex[area2.firstFace + j])]
                    // must be a ground face
                    if (0 == floorFace2.flags and AASFile.FACE_FLOOR) {
                        j++
                        continue
                    }
                    // check the edges of this ground face
                    l = 0
                    while (l < floorFace2.numEdges) {
                        edge2Num = abs(file.edgeIndex[floorFace2.firstEdge + l])
                        edge2 = file.edges[edge2Num]
                        // vertices of the edge
                        v3.set(file.vertices[edge2.vertexNum[0]])
                        v4.set(file.vertices[edge2.vertexNum[1]])
                        // check the distance between the two points and the vertical plane through the edge of area1
                        diff = normal.times(v3) - dist
                        if (diff < -0.2f || diff > 0.2f) {
                            l++
                            continue
                        }
                        diff = normal.times(v4) - dist
                        if (diff < -0.2f || diff > 0.2f) {
                            l++
                            continue
                        }

                        // project the two ground edges into the step side plane
                        // and calculate the shortest distance between the two
                        // edges if they overlap in the direction orthogonal to
                        // the gravity direction
                        orthogonal.set(file.settings.invGravityDir.Cross(normal))
                        invGravityDot = file.settings.invGravityDir.times(file.settings.invGravityDir)
                        orthogonalDot = orthogonal.times(orthogonal)
                        // projection into the step plane
                        // NOTE: since gravity is vertical this is just the z coordinate
                        y1 = v1[2] //(v1 * file->settings.invGravity) / invGravityDot;
                        y2 = v2[2] //(v2 * file->settings.invGravity) / invGravityDot;
                        y3 = v3[2] //(v3 * file->settings.invGravity) / invGravityDot;
                        y4 = v4[2] //(v4 * file->settings.invGravity) / invGravityDot;
                        x1 = v1.times(orthogonal) / orthogonalDot
                        x2 = v2.times(orthogonal) / orthogonalDot
                        x3 = v3.times(orthogonal) / orthogonalDot
                        x4 = v4.times(orthogonal) / orthogonalDot
                        if (x1 > x2) {
                            tmp = x1
                            x1 = x2
                            x2 = tmp
                            tmp = y1
                            y1 = y2
                            y2 = tmp
                            tmpv.set(v1)
                            v1.set(v2)
                            v2.set(tmpv)
                        }
                        if (x3 > x4) {
                            tmp = x3
                            x3 = x4
                            x4 = tmp
                            tmp = y3
                            y3 = y4
                            y4 = tmp
                            tmpv.set(v3)
                            v3.set(v4)
                            v4.set(tmpv)
                        }
                        // if the two projected edge lines have no overlap
                        if (x2 <= x3 || x4 <= x1) {
                            l++
                            continue
                        }
                        // if the two lines fully overlap
                        if (x1 - 0.5f < x3 && x4 < x2 + 0.5f && x3 - 0.5f < x1 && x2 < x4 + 0.5f) {
                            dist1 = y3 - y1
                            dist2 = y4 - y2
                            p1area1.set(v1)
                            p2area1.set(v2)
                            p1area2.set(v3)
                            p2area2.set(v4)
                        } else {
                            // if the points are equal
                            if (x1 > x3 - 0.1f && x1 < x3 + 0.1f) {
                                dist1 = y3 - y1
                                p1area1.set(v1)
                                p1area2.set(v3)
                            } else if (x1 < x3) {
                                y = y1 + (x3 - x1) * (y2 - y1) / (x2 - x1)
                                dist1 = y3 - y
                                p1area1.set(v3)
                                p1area1[2] = y
                                p1area2.set(v3)
                            } else {
                                y = y3 + (x1 - x3) * (y4 - y3) / (x4 - x3)
                                dist1 = y - y1
                                p1area1.set(v1)
                                p1area2.set(v1)
                                p1area2[2] = y
                            }
                            // if the points are equal
                            if (x2 > x4 - 0.1f && x2 < x4 + 0.1f) {
                                dist2 = y4 - y2
                                p2area1.set(v2)
                                p2area2.set(v4)
                            } else if (x2 < x4) {
                                y = y3 + (x2 - x3) * (y4 - y3) / (x4 - x3)
                                dist2 = y - y2
                                p2area1.set(v2)
                                p2area2.set(v2)
                                p2area2[2] = y
                            } else {
                                y = y1 + (x4 - x1) * (y2 - y1) / (x2 - x1)
                                dist2 = y4 - y
                                p2area1.set(v4)
                                p2area1[2] = y
                                p2area2.set(v4)
                            }
                        }

                        // if both distances are pretty much equal then we take the middle of the points
                        if (dist1 > dist2 - 1.0f && dist1 < dist2 + 1.0f) {
                            dist = dist1
                            start.set(p1area1 + p2area1 * 0.5f)
                            end.set(p1area2 + p2area2 * 0.5f)
                        } else if (dist1 < dist2) {
                            dist = dist1
                            start.set(p1area1)
                            end.set(p1area2)
                        } else {
                            dist = dist2
                            start.set(p2area1)
                            end.set(p2area2)
                        }

                        // get the length of the overlapping part of the edges of the two areas
                        length = p2area2.minus(p1area2).Length()
                        if (floorFace1.flags and AASFile.FACE_FLOOR != 0) {
                            // if the vertical distance is smaller
                            if (dist < floor_bestDist
                                ||  // or the vertical distance is pretty much the same
                                // but the overlapping part of the edges is longer
                                dist < floor_bestDist + 1.0f && length > floor_bestLength
                            ) {
                                floor_bestDist = dist
                                floor_bestLength = length
                                floor_foundReach = 1 //true;
                                floor_bestArea1FloorEdgeNum = edge1Num
                                floor_bestArea2FloorEdgeNum = edge2Num
                                floor_bestFace1 = floorFace1
                                floor_bestStart.set(start)
                                floor_bestNormal.set(normal)
                                floor_bestEnd.set(end)
                            }
                        } else {
                            // if the vertical distance is smaller
                            if (dist < water_bestDist
                                ||  //or the vertical distance is pretty much the same
                                //but the overlapping part of the edges is longer
                                dist < water_bestDist + 1.0f && length > water_bestLength
                            ) {
                                water_bestDist = dist
                                water_bestLength = length
                                water_foundReach = 1 //true;
                                water_bestArea1FloorEdgeNum = edge1Num
                                water_bestArea2FloorEdgeNum = edge2Num
                                water_bestFace1 = floorFace1
                                water_bestStart.set(start) // best start point in area1
                                water_bestNormal.set(normal) // normal is pointing into area2
                                water_bestEnd.set(end) // best point towards area2
                            }
                        }
                        l++
                    }
                    j++
                }
                k++
            }
            i++
        }
        //
        // NOTE: swim reachabilities should already be filtered out
        //
        // Steps
        //
        //         ---------
        //         |          step height -> TFL_WALK
        // --------|
        //
        //         ---------
        // ~~~~~~~~|          step height and low water -> TFL_WALK
        // --------|
        //
        // ~~~~~~~~~~~~~~~~~~
        //         ---------
        //         |          step height and low water up to the step -> TFL_WALK
        // --------|
        //
        // check for a step reachability
        if (floor_foundReach != 0) {
            // if area2 is higher but lower than the maximum step height
            // NOTE: floor_bestDist >= 0 also catches equal floor reachabilities
            if (floor_bestDist >= 0 && floor_bestDist < file.settings.maxStepHeight._val) {
                // create walk reachability from area1 to area2
                walkReach = idReachability_Walk()
                walkReach.travelType = AASFile.TFL_WALK
                walkReach.toAreaNum = toAreaNum.toShort()
                walkReach.fromAreaNum = fromAreaNum.toShort()
                walkReach.start.set(floor_bestStart.plus(floor_bestNormal.times(INSIDEUNITS_WALKSTART)))
                walkReach.end.set(floor_bestEnd.plus(floor_bestNormal.times(INSIDEUNITS_WALKEND)))
                walkReach.edgeNum = abs(floor_bestArea1FloorEdgeNum)
                walkReach.travelTime = 0
                if (area2.flags and AASFile.AREA_CROUCH != 0) {
                    walkReach.travelTime += file.settings.tt_startCrouching._val
                }
                AddReachabilityToArea(walkReach, fromAreaNum)
                return true
            }
        }
        //
        // Water Jumps
        //
        //         ---------
        //         |
        // ~~~~~~~~|
        //         |
        //         |          higher than step height and water up to waterjump height -> TFL_WATERJUMP
        // --------|
        //
        // ~~~~~~~~~~~~~~~~~~
        //         ---------
        //         |
        //         |
        //         |
        //         |          higher than step height and low water up to the step -> TFL_WATERJUMP
        // --------|
        //
        // check for a waterjump reachability
        if (water_foundReach != 0) {
            // get a test point a little bit towards area1
            testPoint.set(water_bestEnd.minus(water_bestNormal.times(INSIDEUNITS)))
            // go down the maximum waterjump height
            testPoint.minusAssign(2, file.settings.maxWaterJumpHeight._val)
            // if there IS water the sv_maxwaterjump height below the bestend point
            if (area1.flags and AASFile.AREA_LIQUID != 0) {
                // don't create rediculous water jump reachabilities from areas very far below the water surface
                if (water_bestDist < file.settings.maxWaterJumpHeight._val + 24) {
                    // water jumping from or towards a crouch only areas is not possible
                    if (0 == area1.flags and AASFile.AREA_CROUCH && 0 == area2.flags and AASFile.AREA_CROUCH) {
                        // create water jump reachability from area1 to area2
                        waterJumpReach = idReachability_WaterJump()
                        waterJumpReach.travelType = AASFile.TFL_WATERJUMP
                        waterJumpReach.toAreaNum = toAreaNum.toShort()
                        waterJumpReach.fromAreaNum = fromAreaNum.toShort()
                        waterJumpReach.start.set(water_bestStart)
                        waterJumpReach.end.set(water_bestEnd.plus(water_bestNormal.times(INSIDEUNITS_WATERJUMP)))
                        waterJumpReach.edgeNum = abs(floor_bestArea1FloorEdgeNum)
                        waterJumpReach.travelTime = file.settings.tt_waterJump._val
                        AddReachabilityToArea(waterJumpReach, fromAreaNum)
                        return true
                    }
                }
            }
        }
        //
        // Barrier Jumps
        //
        //         ---------
        //         |
        //         |
        //         |
        //         |         higher than max step height lower than max barrier height -> TFL_BARRIERJUMP
        // --------|
        //
        //         ---------
        //         |
        //         |
        //         |
        // ~~~~~~~~|         higher than max step height lower than max barrier height
        // --------|         and a thin layer of water in the area to jump from -> TFL_BARRIERJUMP
        //
        // check for a barrier jump reachability
        if (floor_foundReach != 0) {
            //if area2 is higher but lower than the maximum barrier jump height
            if (floor_bestDist > 0 && floor_bestDist < file.settings.maxBarrierHeight._val) {
                //if no water in area1 or a very thin layer of water on the ground
                if (0 == water_foundReach || floor_bestDist - water_bestDist < 16) {
                    // cannot perform a barrier jump towards or from a crouch area
                    if (0 == area1.flags and AASFile.AREA_CROUCH && 0 == area2.flags and AASFile.AREA_CROUCH) {
                        // create barrier jump reachability from area1 to area2
                        barrierJumpReach = idReachability_BarrierJump()
                        barrierJumpReach.travelType = AASFile.TFL_BARRIERJUMP
                        barrierJumpReach.toAreaNum = toAreaNum.toShort()
                        barrierJumpReach.fromAreaNum = fromAreaNum.toShort()
                        barrierJumpReach.start.set(
                            floor_bestStart.plus(
                                floor_bestNormal.times(
                                    INSIDEUNITS_WALKSTART
                                )
                            )
                        )
                        barrierJumpReach.end.set(floor_bestEnd.plus(floor_bestNormal.times(INSIDEUNITS_WALKEND)))
                        barrierJumpReach.edgeNum = abs(floor_bestArea1FloorEdgeNum)
                        barrierJumpReach.travelTime = file.settings.tt_barrierJump._val
                        AddReachabilityToArea(barrierJumpReach, fromAreaNum)
                        return true
                    }
                }
            }
        }
        //
        // Walk and Walk Off Ledge
        //
        // --------|
        //         |          can walk or step back -> TFL_WALK
        //         ---------
        //
        // --------|
        //         |
        //         |
        //         |
        //         |          cannot walk/step back -> TFL_WALKOFFLEDGE
        //         ---------
        //
        // --------|
        //         |
        //         |~~~~~~~~
        //         |
        //         |          cannot step back but can waterjump back -> TFL_WALKOFFLEDGE
        //         ---------  FIXME: create TFL_WALK reach??
        //
        // check for a walk or walk off ledge reachability
        if (floor_foundReach != 0) {
            if (floor_bestDist < 0) {
                if (floor_bestDist > -file.settings.maxStepHeight._val) {
                    // create walk reachability from area1 to area2
                    walkReach = idReachability_Walk()
                    walkReach.travelType = AASFile.TFL_WALK
                    walkReach.toAreaNum = toAreaNum.toShort()
                    walkReach.fromAreaNum = fromAreaNum.toShort()
                    walkReach.start.set(floor_bestStart.plus(floor_bestNormal.times(INSIDEUNITS_WALKSTART)))
                    walkReach.end.set(floor_bestEnd.plus(floor_bestNormal.times(INSIDEUNITS_WALKEND)))
                    walkReach.edgeNum = abs(floor_bestArea1FloorEdgeNum)
                    walkReach.travelTime = 1
                    AddReachabilityToArea(walkReach, fromAreaNum)
                    return true
                }
                // if no maximum fall height set or less than the max
                if (0.0f == file.settings.maxFallHeight._val || abs(floor_bestDist) < file.settings.maxFallHeight._val) {
                    // trace a bounding box vertically to check for solids
                    floor_bestEnd.plusAssign(floor_bestNormal.times(INSIDEUNITS))
                    start.set(floor_bestEnd)
                    start[2] = floor_bestStart[2]
                    end.set(floor_bestEnd)
                    end.plusAssign(2, 4.0f)
                    trace.areas = areas
                    trace.maxAreas = areas.size
                    file.Trace(trace, start, end)
                    // if the trace didn't start in solid and nothing was hit
                    if (trace.lastAreaNum != 0 && trace.fraction >= 1.0f) {
                        // the trace end point must be in the goal area
                        if (trace.lastAreaNum == toAreaNum) {
                            // don't create reachability if going through a cluster portal
                            i = 0
                            while (i < trace.numAreas) {
                                if (AreaIsClusterPortal(trace.areas!![i])) {
                                    break
                                }
                                i++
                            }
                            if (i >= trace.numAreas) {
                                // create a walk off ledge reachability from area1 to area2
                                walkOffLedgeReach = idReachability_WalkOffLedge()
                                walkOffLedgeReach.travelType = AASFile.TFL_WALKOFFLEDGE
                                walkOffLedgeReach.toAreaNum = toAreaNum.toShort()
                                walkOffLedgeReach.fromAreaNum = fromAreaNum.toShort()
                                walkOffLedgeReach.start.set(floor_bestStart)
                                walkOffLedgeReach.end.set(floor_bestEnd)
                                walkOffLedgeReach.edgeNum = abs(floor_bestArea1FloorEdgeNum)
                                walkOffLedgeReach.travelTime =
                                    (file.settings.tt_startWalkOffLedge._val + abs(floor_bestDist) * 50 / file.settings.gravityValue).toInt()
                                AddReachabilityToArea(walkOffLedgeReach, fromAreaNum)
                                return true
                            }
                        }
                    }
                }
            }
        }
        return false
    }

    private fun Reachability_WalkOffLedge(areaNum: Int) {
        var i: Int
        var j: Int
        var faceNum: Int
        var edgeNum: Int
        var side: Int
        var reachAreaNum: Int
        var p: Int
        val areas = IntArray(10)
        val area: aasArea_s?
        var face: aasFace_s?
        var edge: aasEdge_s?
        var plane: idPlane
        val v1 = idVec3()
        val v2 = idVec3()
        val mid = idVec3()
        val dir = idVec3()
        val testEnd = idVec3()
        var reach: idReachability_WalkOffLedge
        val trace = aasTrace_s()
        if (!AreaHasFloor(areaNum) || CanSwimInArea(areaNum)) {
            return
        }
        area = file.areas[areaNum]
        i = 0
        while (i < area.numFaces) {
            faceNum = file.faceIndex[area.firstFace + i]
            face = file.faces[abs(faceNum)]

            // face must be a floor face
            if (0 == face.flags and AASFile.FACE_FLOOR) {
                i++
                continue
            }
            j = 0
            while (j < face.numEdges) {
                edgeNum = file.edgeIndex[face.firstEdge + j]
                edge = file.edges[abs(edgeNum)]

                //if ( !(edge.flags & EDGE_LEDGE) ) {
                //	continue;
                //}
                side = TempDump.btoi(edgeNum < 0)
                v1.set(file.vertices[edge.vertexNum[side]])
                v2.set(file.vertices[edge.vertexNum[TempDump.SNOT(side.toDouble())]])
                plane = file.planeList[face.planeNum xor INTSIGNBITSET(faceNum)]

                // get the direction into the other area
                dir.set(plane.Normal().Cross(v2.minus(v1)))
                dir.Normalize()
                mid.set(v1 + v2 * 0.5f)
                testEnd.set(mid.plus(dir.times(INSIDEUNITS_WALKEND)))
                testEnd.minusAssign(2, file.settings.maxFallHeight._val + 1.0f)
                trace.areas = areas
                trace.maxAreas = areas.size
                file.Trace(trace, mid, testEnd)
                reachAreaNum = trace.lastAreaNum
                if (0 == reachAreaNum || reachAreaNum == areaNum) {
                    j++
                    continue
                }
                if (abs(mid[2] - trace.endpos[2]) > file.settings.maxFallHeight._val) {
                    j++
                    continue
                }
                if (!AreaHasFloor(reachAreaNum) && !CanSwimInArea(reachAreaNum)) {
                    j++
                    continue
                }
                if (ReachabilityExists(areaNum, reachAreaNum)) {
                    j++
                    continue
                }
                // if not going through a cluster portal
                p = 0
                while (p < trace.numAreas) {
                    if (AreaIsClusterPortal(trace.areas!![p])) {
                        break
                    }
                    p++
                }
                if (p < trace.numAreas) {
                    j++
                    continue
                }
                reach = idReachability_WalkOffLedge()
                reach.travelType = AASFile.TFL_WALKOFFLEDGE
                reach.toAreaNum = reachAreaNum.toShort()
                reach.fromAreaNum = areaNum.toShort()
                reach.start.set(mid)
                reach.end.set(trace.endpos)
                reach.edgeNum = abs(edgeNum)
                reach.travelTime =
                    (file.settings.tt_startWalkOffLedge._val + abs(mid[2] - trace.endpos[2]) * 50 / file.settings.gravityValue).toInt()
                AddReachabilityToArea(reach, areaNum)
                j++
            }
            i++
        }
    }
}

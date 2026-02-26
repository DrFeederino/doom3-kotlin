package neo.Tools.Compilers.AAS

import neo.Tools.Compilers.AAS.AASFile.aasArea_s
import neo.Tools.Compilers.AAS.AASFile.aasCluster_s
import neo.Tools.Compilers.AAS.AASFile.aasFace_s
import neo.Tools.Compilers.AAS.AASFile.aasPortal_s
import neo.Tools.Compilers.AAS.AASFile.idReachability
import neo.Tools.Compilers.AAS.AASFile_local.idAASFileLocal
import neo.framework.Common
import kotlin.math.abs

class AASCluster {
    /*
     ===============================================================================

     Area Clustering

     ===============================================================================
     */
    internal class idAASCluster {
        private var file: idAASFileLocal = idAASFileLocal()
        private var noFaceFlood = false

        //
        //
        fun Build(file: idAASFileLocal): Boolean {
            Common.common.Printf("[Clustering]\n")
            this.file = file
            noFaceFlood = true
            RemoveInvalidPortals()
            while (true) {

                // delete all existing clusters
                file.DeleteClusters()

                // create the portals from the portal areas
                CreatePortals()
                Common.common.Printf("\r%6d", file.portals.Num())

                // find the clusters
                if (!FindClusters()) {
                    continue
                }

                // test the portals
                if (!TestPortals()) {
                    continue
                }
                break
            }
            Common.common.Printf("\r%6d portals\n", file.portals.Num())
            Common.common.Printf("%6d clusters\n", file.clusters.Num())
            for (i in 0 until file.clusters.Num()) {
                Common.common.Printf("%6d reachable areas in cluster %d\n", file.clusters[i].numReachableAreas, i)
            }
            file.ReportRoutingEfficiency()
            return true
        }

        fun BuildSingleCluster(file: idAASFileLocal): Boolean {
            var i: Int
            var numAreas: Int
            val cluster = aasCluster_s()
            Common.common.Printf("[Clustering]\n")
            this.file = file

            // delete all existing clusters
            file.DeleteClusters()
            cluster.firstPortal = 0
            cluster.numPortals = 0
            cluster.numAreas = file.areas.Num()
            cluster.numReachableAreas = 0
            // give all reachable areas in the cluster a number
            i = 0
            while (i < file.areas.Num()) {
                file.areas[i].cluster = file.clusters.Num().toShort()
                if (file.areas[i].flags and (AASFile.AREA_REACHABLE_WALK or AASFile.AREA_REACHABLE_FLY) != 0) {
                    file.areas[i].clusterAreaNum = cluster.numReachableAreas++.toShort()
                }
                i++
            }
            // give the remaining areas a number within the cluster
            numAreas = cluster.numReachableAreas
            i = 0
            while (i < file.areas.Num()) {
                if (file.areas[i].flags and (AASFile.AREA_REACHABLE_WALK or AASFile.AREA_REACHABLE_FLY) != 0) {
                    i++
                    continue
                }
                file.areas[i].clusterAreaNum = numAreas++.toShort()
                i++
            }
            file.clusters.Append(cluster)
            Common.common.Printf("%6d portals\n", file.portals.Num())
            Common.common.Printf("%6d clusters\n", file.clusters.Num())
            i = 0
            while (i < file.clusters.Num()) {
                Common.common.Printf("%6d reachable areas in cluster %d\n", file.clusters[i].numReachableAreas, i)
                i++
            }
            file.ReportRoutingEfficiency()
            return true
        }

        private fun UpdatePortal(areaNum: Int, clusterNum: Int): Boolean {
            var portalNum: Int
            val portal: aasPortal_s

            // find the portal for this area
            portalNum = 1
            while (portalNum < file.portals.Num()) {
                if (file.portals[portalNum].areaNum.toInt() == areaNum) {
                    break
                }
                portalNum++
            }
            if (portalNum >= file.portals.Num()) {
                Common.common.Error("no portal for area %d", areaNum)
                return true
            }
            portal = file.portals[portalNum]

            // if the portal is already fully updated
            if (portal.clusters[0].toInt() == clusterNum) {
                return true
            }
            if (portal.clusters[1].toInt() == clusterNum) {
                return true
            }
            // if the portal has no front cluster yet
            if (0 == portal.clusters[0].toInt()) {
                portal.clusters[0] = clusterNum.toShort()
            } // if the portal has no back cluster yet
            else if (0 == portal.clusters[1].toInt()) {
                portal.clusters[1] = clusterNum.toShort()
            } else {
                // remove the cluster portal flag contents
                file.areas[areaNum].contents =
                    file.areas[areaNum].contents and AASFile.AREACONTENTS_CLUSTERPORTAL.inv()
                return false
            }

            // set the area cluster number to the negative portal number
            file.areas[areaNum].cluster = (-portalNum).toShort()

            // add the portal to the cluster using the portal index
            file.portalIndex.Append(portalNum)
            file.clusters[clusterNum].numPortals++
            return true
        }

        private fun FloodClusterAreas_r(areaNum: Int, clusterNum: Int): Boolean {
            val area: aasArea_s
            var face: aasFace_s
            var faceNum: Int
            var i: Int
            var reach: idReachability?
            area = file.areas[areaNum]

            // if the area is already part of a cluster
            if (area.cluster > 0) {
                if (area.cluster.toInt() == clusterNum) {
                    return true
                }
                // there's a reachability going from one cluster to another only in one direction
                Common.common.Error(
                    "cluster %d touched cluster %d at area %d\r\n",
                    clusterNum,
                    file.areas[areaNum].cluster,
                    areaNum
                )
                return false
            }

            // if this area is a cluster portal
            if (area.contents and AASFile.AREACONTENTS_CLUSTERPORTAL != 0) {
                return UpdatePortal(areaNum, clusterNum)
            }

            // set the area cluster number
            area.cluster = clusterNum.toShort()
            if (!noFaceFlood) {
                // use area faces to flood into adjacent areas
                i = 0
                while (i < area.numFaces) {
                    faceNum = abs(file.faceIndex[area.firstFace + i])
                    face = file.faces[faceNum]
                    if (face.areas[0].toInt() == areaNum) {
                        if (face.areas[1].toInt() != 0) {
                            if (!FloodClusterAreas_r(face.areas[1].toInt(), clusterNum)) {
                                return false
                            }
                        }
                    } else {
                        if (face.areas[0].toInt() != 0) {
                            if (!FloodClusterAreas_r(face.areas[0].toInt(), clusterNum)) {
                                return false
                            }
                        }
                    }
                    i++
                }
            }

            // use the reachabilities to flood into other areas
            reach = file.areas[areaNum].reach
            while (reach != null) {
                if (!FloodClusterAreas_r(reach.toAreaNum.toInt(), clusterNum)) {
                    return false
                }
                reach = reach.next
            }

            // use the reversed reachabilities to flood into other areas
            reach = file.areas[areaNum].rev_reach
            while (reach != null) {
                if (!FloodClusterAreas_r(reach.fromAreaNum.toInt(), clusterNum)) {
                    return false
                }
                reach = reach.rev_next
            }
            return true
        }

        private fun RemoveAreaClusterNumbers() {
            var i: Int
            i = 1
            while (i < file.areas.Num()) {
                file.areas[i].cluster = 0
                i++
            }
        }

        private fun NumberClusterAreas(clusterNum: Int) {
            var i: Int
            var portalNum: Int
            val cluster: aasCluster_s
            var portal: aasPortal_s
            cluster = file.clusters[clusterNum]
            cluster.numAreas = 0
            cluster.numReachableAreas = 0

            // number all areas in this cluster WITH reachabilities
            i = 1
            while (i < file.areas.Num()) {
                if (file.areas[i].cluster.toInt() != clusterNum) {
                    i++
                    continue
                }
                if (0 == file.areas[i].flags and (AASFile.AREA_REACHABLE_WALK or AASFile.AREA_REACHABLE_FLY)) {
                    i++
                    continue
                }
                file.areas[i].clusterAreaNum = cluster.numAreas++.toShort()
                cluster.numReachableAreas++
                i++
            }

            // number all portals in this cluster WITH reachabilities
            i = 0
            while (i < cluster.numPortals) {
                portalNum = file.portalIndex[cluster.firstPortal + i]
                portal = file.portals[portalNum]
                if (0 == file.areas[portal.areaNum.toInt()].flags and (AASFile.AREA_REACHABLE_WALK or AASFile.AREA_REACHABLE_FLY)) {
                    i++
                    continue
                }
                if (portal.clusters[0].toInt() == clusterNum) {
                    portal.clusterAreaNum[0] = cluster.numAreas++.toShort()
                } else {
                    portal.clusterAreaNum[1] = cluster.numAreas++.toShort()
                }
                cluster.numReachableAreas++
                i++
            }

            // number all areas in this cluster WITHOUT reachabilities
            i = 1
            while (i < file.areas.Num()) {
                if (file.areas[i].cluster.toInt() != clusterNum) {
                    i++
                    continue
                }
                if (file.areas[i].flags and (AASFile.AREA_REACHABLE_WALK or AASFile.AREA_REACHABLE_FLY) != 0) {
                    i++
                    continue
                }
                file.areas[i].clusterAreaNum = cluster.numAreas++.toShort()
                i++
            }

            // number all portals in this cluster WITHOUT reachabilities
            i = 0
            while (i < cluster.numPortals) {
                portalNum = file.portalIndex[cluster.firstPortal + i]
                portal = file.portals[portalNum]
                if (file.areas[portal.areaNum.toInt()].flags and (AASFile.AREA_REACHABLE_WALK or AASFile.AREA_REACHABLE_FLY) != 0) {
                    i++
                    continue
                }
                if (portal.clusters[0].toInt() == clusterNum) {
                    portal.clusterAreaNum[0] = cluster.numAreas++.toShort()
                } else {
                    portal.clusterAreaNum[1] = cluster.numAreas++.toShort()
                }
                i++
            }
        }

        private fun FindClusters(): Boolean {
            var i: Int
            var clusterNum: Int
            val cluster = aasCluster_s()
            RemoveAreaClusterNumbers()
            i = 1
            while (i < file.areas.Num()) {

                // if the area is already part of a cluster
                if (file.areas[i].cluster.toInt() != 0) {
                    i++
                    continue
                }

                // if not flooding through faces only use areas that have reachabilities
                if (noFaceFlood) {
                    if (0 == file.areas[i].flags and (AASFile.AREA_REACHABLE_WALK or AASFile.AREA_REACHABLE_FLY)) {
                        i++
                        continue
                    }
                }

                // if the area is a cluster portal
                if (file.areas[i].contents and AASFile.AREACONTENTS_CLUSTERPORTAL != 0) {
                    i++
                    continue
                }
                cluster.numAreas = 0
                cluster.numReachableAreas = 0
                cluster.firstPortal = file.portalIndex.Num()
                cluster.numPortals = 0
                clusterNum = file.clusters.Num()
                file.clusters.Append(cluster)

                // flood the areas in this cluster
                if (!FloodClusterAreas_r(i, clusterNum)) {
                    return false
                }

                // number the cluster areas
                NumberClusterAreas(clusterNum)
                i++
            }
            return true
        }

        private fun CreatePortals() {
            var i: Int
            val portal: aasPortal_s = aasPortal_s()
            i = 1
            while (i < file.areas.Num()) {

                // if the area is a cluster portal
                if (file.areas[i].contents and AASFile.AREACONTENTS_CLUSTERPORTAL != 0) {
                    portal.areaNum = i.toShort()
                    portal.clusters[1] = 0
                    portal.clusters[0] = portal.clusters[1]
                    portal.maxAreaTravelTime = 0
                    file.portals.Append(portal)
                }
                i++
            }
        }

        private fun TestPortals(): Boolean {
            var i: Int
            var portal: aasPortal_s
            var portal2: aasPortal_s
            var area: aasArea_s
            var area2: aasArea_s
            var reach: idReachability?
            var ok: Boolean
            ok = true
            i = 1
            while (i < file.portals.Num()) {
                portal = file.portals[i]
                area = file.areas[portal.areaNum.toInt()]

                // if this portal was already removed
                if (0 == area.contents and AASFile.AREACONTENTS_CLUSTERPORTAL) {
                    i++
                    continue
                }

                // may not removed this portal if it has a reachability to a removed portal
                reach = area.reach
                while (reach != null) {
                    area2 = file.areas[reach.toAreaNum.toInt()]
                    if (area2.contents and AASFile.AREACONTENTS_CLUSTERPORTAL != 0) {
                        reach = reach.next
                        continue
                    }
                    if (area2.cluster < 0) {
                        break
                    }
                    reach = reach.next
                }
                if (reach != null) {
                    i++
                    continue
                }

                // may not removed this portal if it has a reversed reachability to a removed portal
                reach = area.rev_reach
                while (reach != null) {
                    area2 = file.areas[reach.toAreaNum.toInt()]
                    if (area2.contents and AASFile.AREACONTENTS_CLUSTERPORTAL != 0) {
                        reach = reach.rev_next
                        continue
                    }
                    if (area2.cluster < 0) {
                        break
                    }
                    reach = reach.rev_next
                }
                if (reach != null) {
                    i++
                    continue
                }

                // portal should have two clusters set
                if (0 == portal.clusters[0].toInt()) {
                    area.contents = area.contents and AASFile.AREACONTENTS_CLUSTERPORTAL.inv()
                    ok = false
                    i++
                    continue
                }
                if (0 == portal.clusters[1].toInt()) {
                    area.contents = area.contents and AASFile.AREACONTENTS_CLUSTERPORTAL.inv()
                    ok = false
                    i++
                    continue
                }

                // this portal may not have reachabilities to a portal that doesn't seperate the same clusters
                reach = area.reach
                while (reach != null) {
                    area2 = file.areas[reach.toAreaNum.toInt()]
                    if (0 == area2.contents and AASFile.AREACONTENTS_CLUSTERPORTAL) {
                        reach = reach.next
                        continue
                    }
                    if (area2.cluster > 0) {
                        area2.contents = area2.contents and AASFile.AREACONTENTS_CLUSTERPORTAL.inv()
                        ok = false
                        reach = reach.next
                        continue
                    }
                    portal2 = file.portals[-file.areas[reach.toAreaNum.toInt()].cluster.toInt()]
                    if (portal2.clusters[0] != portal.clusters[0] && portal2.clusters[0] != portal.clusters[1]
                        || portal2.clusters[1] != portal.clusters[0] && portal2.clusters[1] != portal.clusters[1]
                    ) {
                        area2.contents = area2.contents and AASFile.AREACONTENTS_CLUSTERPORTAL.inv()
                        ok = false
                        //                        continue;
                    }
                    reach = reach.next
                }
                i++
            }
            return ok
        }

        //        private void ReportEfficiency();
        private fun RemoveInvalidPortals() {
            var i: Int
            var j: Int
            var k: Int
            var face1Num: Int
            var face2Num: Int
            var otherAreaNum: Int
            var numOpenAreas: Int
            var numInvalidPortals: Int
            var face1: aasFace_s
            var face2: aasFace_s
            numInvalidPortals = 0
            i = 0
            while (i < file.areas.Num()) {
                if (0 == file.areas[i].contents and AASFile.AREACONTENTS_CLUSTERPORTAL) {
                    i++
                    continue
                }
                numOpenAreas = 0
                j = 0
                while (j < file.areas[i].numFaces) {
                    face1Num = file.faceIndex[file.areas[i].firstFace + j]
                    face1 = file.faces[abs(face1Num)]
                    otherAreaNum = face1.areas.get(if (face1Num < 0) 1 else 0).toInt()
                    if (0 == otherAreaNum) {
                        j++
                        continue
                    }
                    k = 0
                    while (k < j) {
                        face2Num = file.faceIndex[file.areas[i].firstFace + k]
                        face2 = file.faces[abs(face2Num)]
                        if (otherAreaNum == face2.areas[if (face2Num < 0) 1 else 0].toInt()) {
                            break
                        }
                        k++
                    }
                    if (k < j) {
                        j++
                        continue
                    }
                    if (0 == file.areas[otherAreaNum].contents and AASFile.AREACONTENTS_CLUSTERPORTAL) {
                        numOpenAreas++
                    }
                    j++
                }
                if (numOpenAreas <= 1) {
                    file.areas[i].contents = file.areas[i].contents and AASFile.AREACONTENTS_CLUSTERPORTAL
                    numInvalidPortals++
                }
                i++
            }
            Common.common.Printf("\r%6d invalid portals removed\n", numInvalidPortals)
        }
    }
}
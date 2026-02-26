package neo.Tools.Compilers.DMap

import neo.Renderer.*
import neo.Renderer.Model.srfTriangles_s
import neo.Tools.Compilers.DMap.dmap.mapLight_t
import neo.Tools.Compilers.DMap.dmap.mapTri_s
import neo.Tools.Compilers.DMap.dmap.node_s
import neo.Tools.Compilers.DMap.dmap.optimizeGroup_s
import neo.Tools.Compilers.DMap.dmap.uArea_t
import neo.Tools.Compilers.DMap.dmap.uEntity_t
import neo.Tools.Compilers.DMap.portals.interAreaPortal_t
import neo.framework.Common
import neo.framework.FileSystem_h
import neo.framework.File_h.idFile
import neo.idlib.MapFile.idMapEntity
import neo.idlib.geometry.DrawVert.idDrawVert
import neo.idlib.geometry.Winding.idWinding
import neo.idlib.math.DotProduct
import neo.idlib.math.idMath
import kotlin.math.abs

object output {
    //
    const val AREANUM_DIFFERENT = -2
    const val COSINE_EPSILON = 0.999
    const val ST_EPSILON = 0.001

    //
    const val XYZ_EPSILON = 0.01

    //=================================================================================
    //#if 0
    //
    //should we try and snap values very close to 0.5f, 0.25f, 0.125, etc?
    //
    //  do we write out normals, or just a "smooth shade" flag?
    //resolved: normals.  otherwise adjacent facet shaded surfaces get their
    //		  vertexes merged, and they would have to be split apart before drawing
    //
    //  do we save out "wings" for shadow silhouette info?
    //
    //
    //#endif
    var procFile: idFile? = null

    /*
     =============
     PruneNodes_r

     Any nodes that have all children with the same
     area can be combined into a single leaf node

     Returns the area number of all children, or
     AREANUM_DIFFERENT if not the same.
     =============
     */
    fun PruneNodes_r(node: node_s): Int {
        val a1: Int
        val a2: Int
        if (node.planenum == dmap.PLANENUM_LEAF) {
            return node.area
        }
        a1 = PruneNodes_r(node.children[0])
        a2 = PruneNodes_r(node.children[1])
        if (a1 != a2 || a1 == AREANUM_DIFFERENT) {
            return AREANUM_DIFFERENT
        }

        // free all the nodes below this point
        facebsp.FreeTreePortals_r(node.children[0])
        facebsp.FreeTreePortals_r(node.children[1])
        facebsp.FreeTree_r(node.children[0])
        facebsp.FreeTree_r(node.children[1])

        // change this node to a leaf
        node.planenum = dmap.PLANENUM_LEAF
        node.area = a1
        return a1
    }

    fun WriteFloat(f: idFile, v: Float) {
        if (abs(v - idMath.Rint(v)) < 0.001) {
            f.WriteFloatString("%d ", idMath.Rint(v).toInt())
        } else {
            f.WriteFloatString("%f ", v)
        }
    }

    fun Write1DMatrix(f: idFile, x: Int, m: FloatArray) {
        var i: Int
        f.WriteFloatString("( ")
        i = 0
        while (i < x) {
            WriteFloat(f, m[i])
            i++
        }
        f.WriteFloatString(") ")
    }

    fun CountUniqueShaders(groups: optimizeGroup_s?): Int {
        var a: optimizeGroup_s?
        var b: optimizeGroup_s?
        var count: Int
        count = 0
        a = groups
        while (a != null) {
            if (null == a.triList) {    // ignore groups with no tris
                a = a.nextGroup
                continue
            }
            b = groups
            while (b !== a) {
                if (null == b!!.triList) {
                    b = b.nextGroup
                    continue
                }
                if (a.material !== b.material) {
                    b = b.nextGroup
                    continue
                }
                if (a.mergeGroup !== b.mergeGroup) {
                    b = b.nextGroup
                    continue
                }
                break
                b = b.nextGroup // why tf is there break above?
            }
            if (a === b) {
                count++
            }
            a = a.nextGroup
        }
        return count
    }

    /*
     ==============
     MatchVert
     ==============
     */
    fun MatchVert(a: idDrawVert, b: idDrawVert): Boolean {
        if (abs(a.xyz[0] - b.xyz[0]) > XYZ_EPSILON) {
            return false
        }
        if (abs(a.xyz[1] - b.xyz[1]) > XYZ_EPSILON) {
            return false
        }
        if (abs(a.xyz[2] - b.xyz[2]) > XYZ_EPSILON) {
            return false
        }
        if (abs(a.st[0] - b.st[0]) > ST_EPSILON) {
            return false
        }
        if (abs(a.st[1] - b.st[1]) > ST_EPSILON) {
            return false
        }

        // if the normal is 0 (smoothed normals), consider it a match
        return if (a.normal[0] == 0.0f && a.normal[1] == 0.0f && a.normal[2] == 0.0f && b.normal[0] == 0.0f && b.normal[1] == 0.0f && b.normal[2] == 0.0f
        ) {
            true
        } else DotProduct(a.normal, b.normal) >= COSINE_EPSILON

        // otherwise do a dot-product cosine check
    }

    /*
     ====================
     ShareMapTriVerts

     Converts independent triangles to shared vertex triangles
     ====================
     */
    fun ShareMapTriVerts(tris: mapTri_s): srfTriangles_s {
        var step: mapTri_s?
        val count: Int
        var i: Int
        var j: Int
        var numVerts: Int
        var numIndexes: Int
        val uTri: srfTriangles_s?

        // unique the vertexes
        count = tritools.CountTriList(tris)
        uTri = R_AllocStaticTriSurf()
        R_AllocStaticTriSurfVerts(uTri, count * 3)
        R_AllocStaticTriSurfIndexes(uTri, count * 3)
        numVerts = 0
        numIndexes = 0
        step = tris
        while (step != null) {
            i = 0
            while (i < 3) {
                var dv: idDrawVert?
                dv = step.v[i]

                // search for a match
                j = 0
                while (j < numVerts) {
                    if (MatchVert(uTri.verts!![j]!!, dv)) {
                        break
                    }
                    j++
                }
                if (j == numVerts) {
                    numVerts++
                    uTri.verts!![j]!!.xyz.set(dv.xyz)
                    uTri.verts!![j]!!.normal.set(dv.normal)
                    uTri.verts!![j]!!.st[0] = dv.st[0]
                    uTri.verts!![j]!!.st[1] = dv.st[1]
                }
                uTri.indexes!![numIndexes++] = j
                i++
            }
            step = step.next
        }
        uTri.numVerts = numVerts
        uTri.numIndexes = numIndexes
        return uTri
    }

    /*
     ==================
     CleanupUTriangles
     ==================
     */
    fun CleanupUTriangles(tri: srfTriangles_s) {
        // perform cleanup operations
        R_RangeCheckIndexes(tri)
        R_CreateSilIndexes(tri)
        //	R_RemoveDuplicatedTriangles( tri );	// this may remove valid overlapped transparent triangles
        R_RemoveDegenerateTriangles(tri)
        //	R_RemoveUnusedVerts( tri );
        R_FreeStaticTriSurfSilIndexes(tri)
    }

    /*
     ====================
     WriteUTriangles

     Writes text verts and indexes to procfile
     ====================
     */
    fun WriteUTriangles(uTris: srfTriangles_s) {
        var col: Int
        var i: Int

        // emit this chain
        procFile!!.WriteFloatString(
            "/* numVerts = */ %d /* numIndexes = */ %d\n",
            uTris.numVerts, uTris.numIndexes
        )

        // verts
        col = 0
        i = 0
        while (i < uTris.numVerts) {
            val vec = FloatArray(8)
            val dv: idDrawVert
            dv = uTris.verts!![i]!!
            vec[0] = dv.xyz[0]
            vec[1] = dv.xyz[1]
            vec[2] = dv.xyz[2]
            vec[3] = dv.st[0]
            vec[4] = dv.st[1]
            vec[5] = dv.normal[0]
            vec[6] = dv.normal[1]
            vec[7] = dv.normal[2]
            Write1DMatrix(procFile!!, 8, vec)
            if (++col == 3) {
                col = 0
                procFile!!.WriteFloatString("\n")
            }
            i++
        }
        if (col != 0) {
            procFile!!.WriteFloatString("\n")
        }

        // indexes
        col = 0
        i = 0
        while (i < uTris.numIndexes) {
            procFile!!.WriteFloatString("%d ", uTris.indexes!![i])
            if (++col == 18) {
                col = 0
                procFile!!.WriteFloatString("\n")
            }
            i++
        }
        if (col != 0) {
            procFile!!.WriteFloatString("\n")
        }
    }

    /*
     ====================
     WriteShadowTriangles

     Writes text verts and indexes to procfile
     ====================
     */
    fun WriteShadowTriangles(tri: srfTriangles_s) {
        var col: Int
        var i: Int

        // emit this chain
        procFile!!.WriteFloatString(
            "/* numVerts = */ %d /* noCaps = */ %d /* noFrontCaps = */ %d /* numIndexes = */ %d /* planeBits = */ %d\n",
            tri.numVerts,
            tri.numShadowIndexesNoCaps,
            tri.numShadowIndexesNoFrontCaps,
            tri.numIndexes,
            tri.shadowCapPlaneBits
        )

        // verts
        col = 0
        i = 0
        while (i < tri.numVerts) {
            Write1DMatrix(procFile!!, 3, tri.shadowVertexes!![i].xyz.ToFloatPtr())
            if (++col == 5) {
                col = 0
                procFile!!.WriteFloatString("\n")
            }
            i++
        }
        if (col != 0) {
            procFile!!.WriteFloatString("\n")
        }

        // indexes
        col = 0
        i = 0
        while (i < tri.numIndexes) {
            procFile!!.WriteFloatString("%d ", tri.indexes!![i])
            if (++col == 18) {
                col = 0
                procFile!!.WriteFloatString("\n")
            }
            i++
        }
        if (col != 0) {
            procFile!!.WriteFloatString("\n")
        }
    }

    /*
     =======================
     GroupsAreSurfaceCompatible

     Planes, texcoords, and groupLights can differ,
     but the material and mergegroup must match
     =======================
     */
    fun GroupsAreSurfaceCompatible(a: optimizeGroup_s, b: optimizeGroup_s): Boolean {
        return if (a.material != b.material) {
            false
        } else a.mergeGroup == b.mergeGroup
    }

    /*
     ====================
     WriteOutputSurfaces
     ====================
     */
    fun WriteOutputSurfaces(entityNum: Int, areaNum: Int) {
        var ambient: mapTri_s?
        var copy: mapTri_s?
        var surfaceNum: Int
        val numSurfaces: Int
        val entity: idMapEntity?
        val area: uArea_t?
        var group: optimizeGroup_s?
        var groupStep: optimizeGroup_s?
        var i: Int // , j;
        //	int			col;
        var uTri: srfTriangles_s?

        //	mapTri_s	*tri;
        class interactionTris_s {
            var light: mapLight_t? = null
            var next: interactionTris_s? = null
            var triList: mapTri_s? = null
        }

        var interactions: interactionTris_s?
        var checkInter: interactionTris_s? //, *nextInter;
        area = dmap.dmapGlobals.uEntities[entityNum].areas[areaNum]
        entity = dmap.dmapGlobals.uEntities[entityNum].mapEntity
        numSurfaces = CountUniqueShaders(area.groups)
        if (entityNum == 0) {
            procFile!!.WriteFloatString(
                "model { /* name = */ \"_area%d\" /* numSurfaces = */ %d\n\n",
                areaNum, numSurfaces
            )
        } else {
            val name = arrayOfNulls<String>(1)
            entity.epairs.GetString("name", "", name)
            if (!name[0].isNullOrEmpty()) {
                Common.common.Error("Entity %d has surfaces, but no name key", entityNum)
            }
            procFile!!.WriteFloatString(
                "model { /* name = */ \"%s\" /* numSurfaces = */ %d\n\n",
                name, numSurfaces
            )
        }
        surfaceNum = 0
        group = area.groups
        while (group != null) {
            if (group.surfaceEmited) {
                group = group.nextGroup
                continue
            }

            // combine all groups compatible with this one
            // usually several optimizeGroup_s can be combined into a single
            // surface, even though they couldn't be merged together to save
            // vertexes because they had different planes, texture coordinates, or lights.
            // Different mergeGroups will stay in separate surfaces.
            ambient = null

            // each light that illuminates any of the groups in the surface will
            // get its own list of indexes out of the original surface
            interactions = null
            groupStep = group
            while (groupStep != null) {
                if (groupStep.surfaceEmited) {
                    groupStep = groupStep.nextGroup
                    continue
                }
                if (!GroupsAreSurfaceCompatible(group, groupStep)) {
                    groupStep = groupStep.nextGroup
                    continue
                }

                // copy it out to the ambient list
                copy = tritools.CopyTriList(groupStep.triList)
                ambient = tritools.MergeTriLists(ambient, copy)
                groupStep.surfaceEmited = true

                // duplicate it into an interaction for each groupLight
                i = 0
                while (i < groupStep.numGroupLights) {
                    checkInter = interactions
                    while (checkInter != null) {
                        if (checkInter.light === groupStep.groupLights[i]) {
                            break
                        }
                        checkInter = checkInter.next
                    }
                    if (null == checkInter) {
                        // create a new interaction
                        checkInter = interactionTris_s() // Mem_ClearedAlloc(sizeof(checkInter));
                        checkInter.light = groupStep.groupLights[i]
                        checkInter.next = interactions
                        interactions = checkInter
                    }
                    copy = tritools.CopyTriList(groupStep.triList)
                    checkInter.triList = tritools.MergeTriLists(checkInter.triList, copy)
                    i++
                }
                groupStep = groupStep.nextGroup
            }
            if (ambient == null) {
                group = group.nextGroup
                continue
            }
            if (surfaceNum >= numSurfaces) {
                Common.common.Error("WriteOutputSurfaces: surfaceNum >= numSurfaces")
            }
            procFile!!.WriteFloatString("/* surface %d */ { ", surfaceNum)
            surfaceNum++
            procFile!!.WriteFloatString("\"%s\" ", ambient!!.material!!.GetName())
            uTri = ShareMapTriVerts(ambient)
            tritools.FreeTriList(ambient)
            CleanupUTriangles(uTri)
            WriteUTriangles(uTri)
            R_FreeStaticTriSurf(uTri)
            procFile!!.WriteFloatString("}\n\n")
            group = group.nextGroup
        }
        procFile!!.WriteFloatString("}\n\n")
    }

    /*
     ===============
     WriteNode_r

     ===============
     */
    fun WriteNode_r(node: node_s) {
        val child = IntArray(2)
        var i: Int
        if (node.planenum == dmap.PLANENUM_LEAF) {
            // we shouldn't get here unless the entire world
            // was a single leaf
            procFile!!.WriteFloatString("/* node 0 */ ( 0 0 0 0 ) -1 -1\n")
            return
        }
        i = 0
        while (i < 2) {
            if (node.children[i].planenum == dmap.PLANENUM_LEAF) {
                child[i] = -1 - node.children[i].area
            } else {
                child[i] = node.children[i].nodeNumber
            }
            i++
        }
        val plane = dmap.dmapGlobals.mapPlanes[node.planenum]
        procFile!!.WriteFloatString("/* node %d */ ", node.nodeNumber)
        Write1DMatrix(procFile!!, 4, plane.ToFloatPtr())
        procFile!!.WriteFloatString("%d %d\n", child[0], child[1])
        if (child[0] > 0) {
            WriteNode_r(node.children[0])
        }
        if (child[1] > 0) {
            WriteNode_r(node.children[1])
        }
    }

    fun NumberNodes_r(node: node_s, nextNumber: Int): Int {
        var nextNumber = nextNumber
        if (node.planenum == dmap.PLANENUM_LEAF) {
            return nextNumber
        }
        node.nodeNumber = nextNumber
        nextNumber++
        nextNumber = NumberNodes_r(node.children[0], nextNumber)
        nextNumber = NumberNodes_r(node.children[1], nextNumber)
        return nextNumber
    }

    /*
     ====================
     WriteOutputNodes
     ====================
     */
    fun WriteOutputNodes(node: node_s) {
        val numNodes: Int

        // prune unneeded nodes and count
        PruneNodes_r(node)
        numNodes = NumberNodes_r(node, 0)

        // output
        procFile!!.WriteFloatString("nodes { /* numNodes = */ %d\n\n", numNodes)
        procFile!!.WriteFloatString("/* node format is: ( planeVector ) positiveChild negativeChild */\n")
        procFile!!.WriteFloatString("/* a child number of 0 is an opaque, solid area */\n")
        procFile!!.WriteFloatString("/* negative child numbers are areas: (-1-child) */\n")
        WriteNode_r(node)
        procFile!!.WriteFloatString("}\n\n")
    }

    /*
     ====================
     WriteOutputPortals
     ====================
     */
    fun WriteOutputPortals(e: uEntity_t) {
        var i: Int
        var j: Int
        var iap: interAreaPortal_t?
        var w: idWinding
        procFile!!.WriteFloatString(
            "interAreaPortals { /* numAreas = */ %d /* numIAP = */ %d\n\n",
            e.numAreas, portals.numInterAreaPortals
        )
        procFile!!.WriteFloatString("/* interAreaPortal format is: numPoints positiveSideArea negativeSideArea ( point) ... */\n")
        i = 0
        while (i < portals.numInterAreaPortals) {
            iap = portals.interAreaPortals[i]
            w = iap.side!!.winding!!
            procFile!!.WriteFloatString("/* iap %d */ %d %d %d ", i, w.GetNumPoints(), iap.area0, iap.area1)
            j = 0
            while (j < w.GetNumPoints()) {
                Write1DMatrix(procFile!!, 3, w.get(j).ToFloatPtr())
                j++
            }
            procFile!!.WriteFloatString("\n")
            i++
        }
        procFile!!.WriteFloatString("}\n\n")
    }

    /*
     ====================
     WriteOutputEntity
     ====================
     */
    fun WriteOutputEntity(entityNum: Int) {
        var i: Int
        val e: uEntity_t?
        e = dmap.dmapGlobals.uEntities[entityNum]
        if (entityNum != 0) {
            // entities may have enclosed, empty areas that we don't need to write out
            if (e.numAreas > 1) {
                e.numAreas = 1
            }
        }
        i = 0
        while (i < e.numAreas) {
            WriteOutputSurfaces(entityNum, i)
            i++
        }

        // we will completely skip the portals and nodes if it is a single area
        if (entityNum == 0 && e.numAreas > 1) {
            // output the area portals
            WriteOutputPortals(e)

            // output the nodes
            WriteOutputNodes(e.tree.headnode)
        }
    }

    /*
     ====================
     WriteOutputFile
     ====================
     */
    fun WriteOutputFile() {
        var i: Int
        var entity: uEntity_t?
        val qpath: String?

        // write the file
        Common.common.Printf("----- WriteOutputFile -----\n")
        qpath = String.format("%s." + RenderWorld.PROC_FILE_EXT, dmap.dmapGlobals.mapFileBase)
        Common.common.Printf("writing %s\n", qpath)
        // _D3XP used fs_cdpath
        procFile = FileSystem_h.fileSystem.OpenFileWrite(qpath, "fs_devpath")
        if (null == procFile) {
            Common.common.Error("Error opening %s", qpath)
            return
        }
        procFile!!.WriteFloatString("%s\n\n", RenderWorld.PROC_FILE_ID)

        // write the entity models and information, writing entities first
        i = dmap.dmapGlobals.num_entities - 1
        while (i >= 0) {
            entity = dmap.dmapGlobals.uEntities[i]
            if (entity.primitives == null) {
                i--
                continue
            }
            WriteOutputEntity(i)
            i--
        }

        // write the shadow volumes
        i = 0
        while (i < dmap.dmapGlobals.mapLights.Num()) {
            val light = dmap.dmapGlobals.mapLights[i]
            if (light.shadowTris == null) {
                i++
                continue
            }
            procFile!!.WriteFloatString("shadowModel { /* name = */ \"_prelight_%s\"\n\n", light.name)
            WriteShadowTriangles(light.shadowTris!!)
            procFile!!.WriteFloatString("}\n\n")
            R_FreeStaticTriSurf(light.shadowTris)
            light.shadowTris = null
            i++
        }
        FileSystem_h.fileSystem.CloseFile(procFile!!)
    }
}
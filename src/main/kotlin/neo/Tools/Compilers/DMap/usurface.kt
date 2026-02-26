package neo.Tools.Compilers.DMap

import neo.Renderer.Material
import neo.Renderer.Material.materialCoverage_t
import neo.Renderer.ModelManager
import neo.TempDump
import neo.Tools.Compilers.DMap.dmap.mapLight_t
import neo.Tools.Compilers.DMap.dmap.mapTri_s
import neo.Tools.Compilers.DMap.dmap.node_s
import neo.Tools.Compilers.DMap.dmap.optimizeGroup_s
import neo.Tools.Compilers.DMap.dmap.primitive_s
import neo.Tools.Compilers.DMap.dmap.side_s
import neo.Tools.Compilers.DMap.dmap.textureVectors_t
import neo.Tools.Compilers.DMap.dmap.uArea_t
import neo.Tools.Compilers.DMap.dmap.uBrush_t
import neo.Tools.Compilers.DMap.dmap.uEntity_t
import neo.Tools.Compilers.DMap.map.FindFloatPlane
import neo.framework.Common
import neo.idlib.Text.Str.idStr
import neo.idlib.geometry.DrawVert.idDrawVert
import neo.idlib.geometry.Winding.idWinding
import neo.idlib.math.*
import neo.idlib.math.Matrix.idMat3
import neo.sys.win_shared
import kotlin.math.abs

object usurface {
    //
    const val SNAP_FLOAT_TO_INT = 256
    const val SNAP_INT_TO_FLOAT = 1.0f / SNAP_FLOAT_TO_INT
    const val TEXTURE_OFFSET_EQUAL_EPSILON = 0.005
    const val TEXTURE_VECTOR_EQUAL_EPSILON = 0.001

    /*
     ===============
     AddTriListToArea

     The triList is appended to the apropriate optimzeGroup_t,
     creating a new one if needed.
     The entire list is assumed to come from the same planar primitive
     ===============
     */
    fun AddTriListToArea(e: uEntity_t, triList: mapTri_s?, planeNum: Int, areaNum: Int, texVec: textureVectors_t) {
        val area: uArea_t?
        var group: optimizeGroup_s?
        var i: Int
        var j: Int
        if (triList == null) {
            return
        }
        area = e.areas[areaNum]
        group = area.groups
        while (group != null) {
            if (group.material === triList.material && group.planeNum == planeNum && group.mergeGroup === triList.mergeGroup) {
                // check the texture vectors
                i = 0
                while (i < 2) {
                    j = 0
                    while (j < 3) {
                        if (abs(texVec.v[i][j] - group.texVec.v[i][j]) > TEXTURE_VECTOR_EQUAL_EPSILON) {
                            break
                        }
                        j++
                    }
                    if (j != 3) {
                        break
                    }
                    if (abs(texVec.v[i][3] - group.texVec.v[i][3]) > TEXTURE_OFFSET_EQUAL_EPSILON) {
                        break
                    }
                    i++
                }
                i = if (i == 2) {
                    break // exact match
                } else {
                    // different texture offsets
                    1 // just for debugger breakpoint
                }
            }
            group = group.nextGroup
        }
        if (null == group) {
            group = optimizeGroup_s() // Mem_Alloc(sizeof(group));
            //		memset( group, 0, sizeof( *group ) );
            group.planeNum = planeNum
            group.mergeGroup = triList.mergeGroup
            group.material = triList.material
            group.nextGroup = area.groups
            group.texVec = texVec
            area.groups = group
        }
        group.triList = tritools.MergeTriLists(group.triList, triList)
    }

    /*
     ===================
     TexVecForTri
     ===================
     */
    fun TexVecForTri(texVec: textureVectors_t, tri: mapTri_s) {
        val area: Float
        val inva: Float
        val temp = idVec3()
        val d0 = idVec5()
        val d1 = idVec5()
        val a: idDrawVert
        val b: idDrawVert
        val c: idDrawVert
        a = tri.v[0]
        b = tri.v[1]
        c = tri.v[2]
        d0[0] = b.xyz[0] - a.xyz[0]
        d0[1] = b.xyz[1] - a.xyz[1]
        d0[2] = b.xyz[2] - a.xyz[2]
        d0[3] = b.st[0] - a.st[0]
        d0[4] = b.st[1] - a.st[1]
        d1[0] = c.xyz[0] - a.xyz[0]
        d1[1] = c.xyz[1] - a.xyz[1]
        d1[2] = c.xyz[2] - a.xyz[2]
        d1[3] = c.st[0] - a.st[0]
        d1[4] = c.st[1] - a.st[1]
        area = d0[3] * d1[4] - d0[4] * d1[3]
        inva = 1.0f / area
        temp[0] = (d0[0] * d1[4] - d0[4] * d1[0]) * inva
        temp[1] = (d0[1] * d1[4] - d0[4] * d1[1]) * inva
        temp[2] = (d0[2] * d1[4] - d0[4] * d1[2]) * inva
        temp.Normalize()
        texVec.v[0].set(temp)
        texVec.v[0][3] = tri.v[0].xyz.times(texVec.v[0].ToVec3()) - tri.v[0].st[0]
        temp[0] = (d0[3] * d1[0] - d0[0] * d1[3]) * inva
        temp[1] = (d0[3] * d1[1] - d0[1] * d1[3]) * inva
        temp[2] = (d0[3] * d1[2] - d0[2] * d1[3]) * inva
        temp.Normalize()
        texVec.v[1].set(temp)
        texVec.v[1][3] = tri.v[0].xyz.times(texVec.v[0].ToVec3()) - tri.v[0].st[1]
    }

    /*
     =================
     TriListForSide
     =================
     */
    //#define	SNAP_FLOAT_TO_INT	8
    fun TriListForSide(s: side_s, w: idWinding): mapTri_s? {
        var i: Int
        var j: Int
        var dv: idDrawVert
        var tri: mapTri_s?
        var triList: mapTri_s?
        val vec = idVec3()
        val si: Material.idMaterial?
        si = s.material

        // skip any generated faces
        if (null == si) {
            return null
        }

        // don't create faces for non-visible sides
        if (!si.SurfaceCastsShadow() && !si.IsDrawn()) {
            return null
        }

        //	if ( 1 ) {
        // triangle fan using only the outer verts
        // this gives the minimum triangle count,
        // but may have some very distended triangles
        triList = null
        i = 2
        while (i < w.GetNumPoints()) {
            tri = tritools.AllocTri()
            tri.material = si
            tri.next = triList
            triList = tri
            j = 0
            while (j < 3) {
                if (j == 0) {
                    vec.set(w[0].ToVec3())
                } else if (j == 1) {
                    vec.set(w[i - 1].ToVec3())
                } else {
                    vec.set(w[i].ToVec3())
                }
                dv = tri.v[j]
                //#if 0
                //				// round the xyz to a given precision
                //				for ( k = 0 ; k < 3 ; k++ ) {
                //					dv.xyz[k] = SNAP_INT_TO_FLOAT * floor( vec[k] * SNAP_FLOAT_TO_INT + 0.5f );
                //				}
                //#else
                VectorCopy(vec, dv.xyz) //TODO:copy range?????
                //#endif

                // calculate texture s/t from brush primitive texture matrix
                dv.st[0] = DotProduct(dv.xyz, s.texVec.v[0]) + s.texVec.v[0][3]
                dv.st[1] = DotProduct(dv.xyz, s.texVec.v[1]) + s.texVec.v[1][3]

                // copy normal
                dv.normal.set(dmap.dmapGlobals.mapPlanes[s.planenum].Normal())
                if (dv.normal.Length() < 0.9 || dv.normal.Length() > 1.1) {
                    Common.common.Error("Bad normal in TriListForSide")
                }
                j++
            }
            i++
        }
        //	} else {
        //		// triangle fan from central point, more verts and tris, but less distended
        //		// I use this when debugging some tjunction problems
        //		triList = NULL;
        //		for ( i = 0 ; i < w.GetNumPoints() ; i++ ) {
        //			idVec3	midPoint;
        //
        //			tri = AllocTri();
        //			tri.material = si;	
        //			tri.next = triList;
        //			triList = tri;
        //
        //			for ( j = 0 ; j < 3 ; j++ ) {
        //				if ( j == 0 ) {
        //					vec = &midPoint;
        //					midPoint = w.GetCenter();
        //				} else if ( j == 1 ) {
        //					vec = &((*w)[i]).ToVec3();
        //				} else {
        //					vec = &((*w)[(i+1)%w.GetNumPoints()]).ToVec3();
        //				}
        //
        //				dv = tri.v + j;
        //
        //				VectorCopy( *vec, dv.xyz );
        //				
        //				// calculate texture s/t from brush primitive texture matrix
        //				dv.st[0] = DotProduct( dv.xyz, s.texVec.v[0] ) + s.texVec.v[0][3];
        //				dv.st[1] = DotProduct( dv.xyz, s.texVec.v[1] ) + s.texVec.v[1][3];
        //
        //				// copy normal
        //				dv.normal = dmapGlobals.mapPlanes[s.planenum].Normal();
        //				if ( dv.normal.Length() < 0.9f || dv.normal.Length() > 1.1f ) {
        //					common.Error( "Bad normal in TriListForSide" );
        //				}
        //			}
        //		}
        //	}

        // set merge groups if needed, to prevent multiple sides from being
        // merged into a single surface in the case of gui shaders, mirrors, and autosprites
        if (s.material!!.IsDiscrete()) {
            tri = triList
            while (tri != null) {
                tri.mergeGroup = s
                tri = tri.next
            }
        }
        return triList
    }

    //=================================================================================
    /*
     ====================
     ClipSideByTree_r

     Adds non-opaque leaf fragments to the convex hull
     ====================
     */
    fun ClipSideByTree_r(w: idWinding?, side: side_s, node: node_s) {
        val front = idWinding()
        val back = idWinding()
        if (null == w) {
            return
        }
        if (node.planenum != dmap.PLANENUM_LEAF) {
            if (side.planenum == node.planenum) {
                ClipSideByTree_r(w, side, node.children[0])
                return
            }
            if (side.planenum == node.planenum xor 1) {
                ClipSideByTree_r(w, side, node.children[1])
                return
            }
            w.Split(dmap.dmapGlobals.mapPlanes[node.planenum], ON_EPSILON, front, back)
            //		delete w;
            ClipSideByTree_r(front, side, node.children[0])
            ClipSideByTree_r(back, side, node.children[1])
            return
        }

        // if opaque leaf, don't add
        if (!node.opaque) {
            if (null == side.visibleHull) {
                side.visibleHull = w.Copy()
            } else {
                side.visibleHull!!.AddToConvexHull(w, dmap.dmapGlobals.mapPlanes[side.planenum].Normal())
            }
        }

        //	delete w;
//        return;
    }

    /*
     =====================
     ClipSidesByTree

     Creates side.visibleHull for all visible sides

     The visible hull for a side will consist of the convex hull of
     all points in non-opaque clusters, which allows overlaps
     to be trimmed off automatically.
     =====================
     */
    fun ClipSidesByTree(e: uEntity_t) {
        var b: uBrush_t?
        var i: Int
        var w: idWinding
        var side: side_s?
        var prim: primitive_s?
        Common.common.Printf("----- ClipSidesByTree -----\n")
        prim = e.primitives
        while (prim != null) {
            b = prim.brush as uBrush_t?
            if (b == null) {
                // FIXME: other primitives!
                prim = prim.next
                continue
            }
            i = 0
            while (i < b.numsides) {
                side = b.sides[i]
                if (null == side.winding) {
                    i++
                    continue
                }
                w = side.winding!!.Copy()
                side.visibleHull = null
                ClipSideByTree_r(w, side, e.tree.headnode)
                // for debugging, we can choose to use the entire original side
                // but we skip this if the side was completely clipped away
                if (side.visibleHull != null && dmap.dmapGlobals.noClipSides) {
                    //				delete side.visibleHull;
                    side.visibleHull = side.winding!!.Copy()
                }
                i++
            }
            prim = prim.next
        }
    }

    //=================================================================================
    /*
     ====================
     ClipTriIntoTree_r

     This is used for adding curve triangles
     The winding will be freed before it returns
     ====================
     */
    fun ClipTriIntoTree_r(w: idWinding?, originalTri: mapTri_s, e: uEntity_t, node: node_s) {
        val front = idWinding()
        val back = idWinding()
        if (null == w) {
            return
        }
        if (node.planenum != dmap.PLANENUM_LEAF) {
            w.Split(dmap.dmapGlobals.mapPlanes[node.planenum], ON_EPSILON, front, back)
            //		delete w;
            ClipTriIntoTree_r(front, originalTri, e, node.children[0])
            ClipTriIntoTree_r(back, originalTri, e, node.children[1])
            return
        }

        // if opaque leaf, don't add
        if (!node.opaque && node.area >= 0) {
            val list: mapTri_s?
            val planeNum: Int
            val plane = idPlane()
            val texVec = textureVectors_t()
            list = tritools.WindingToTriList(w, originalTri)
            tritools.PlaneForTri(originalTri, plane)
            planeNum = FindFloatPlane(plane)
            TexVecForTri(texVec, originalTri)
            AddTriListToArea(e, list, planeNum, node.area, texVec)
        }

        //	delete w;
//        return;
    }

    //=============================================================
    /*
     ====================
     CheckWindingInAreas_r

     Returns the area number that the winding is in, or
     -2 if it crosses multiple areas.

     ====================
     */
    fun CheckWindingInAreas_r(w: idWinding?, node: node_s): Int {
        val front = idWinding()
        val back = idWinding()
        if (null == w) {
            return -1
        }
        if (node.planenum != dmap.PLANENUM_LEAF) {
            val a1: Int
            val a2: Int
            //#if 0
            //		if ( side.planenum == node.planenum ) {
            //			return CheckWindingInAreas_r( w, node.children[0] );
            //		}
            //		if ( side.planenum == ( node.planenum ^ 1) ) {
            //			return CheckWindingInAreas_r( w, node.children[1] );
            //		}
            //#endif
            w.Split(dmap.dmapGlobals.mapPlanes[node.planenum], ON_EPSILON, front, back)
            a1 = CheckWindingInAreas_r(front, node.children[0])
            //		delete front;
            a2 = CheckWindingInAreas_r(back, node.children[1])
            //		delete back;
            if (a1 == -2 || a2 == -2) {
                return -2 // different
            }
            if (a1 == -1) {
                return a2 // one solid
            }
            if (a2 == -1) {
                return a1 // one solid
            }
            return if (a1 != a2) {
                -2 // cross areas
            } else a1
        }
        return node.area
    }

    /*
     ====================
     PutWindingIntoAreas_r

     Clips a winding down into the bsp tree, then converts
     the fragments to triangles and adds them to the area lists
     ====================
     */
    fun PutWindingIntoAreas_r(e: uEntity_t, w: idWinding?, side: side_s, node: node_s) {
        val front = idWinding()
        val back = idWinding()
        val area: Int
        if (null == w) {
            return
        }
        if (node.planenum != dmap.PLANENUM_LEAF) {
            if (side.planenum == node.planenum) {
                PutWindingIntoAreas_r(e, w, side, node.children[0])
                return
            }
            if (side.planenum == node.planenum xor 1) {
                PutWindingIntoAreas_r(e, w, side, node.children[1])
                return
            }

            // see if we need to split it
            // adding the "noFragment" flag to big surfaces like sky boxes
            // will avoid potentially dicing them up into tons of triangles
            // that take forever to optimize back together
            if (!dmap.dmapGlobals.fullCarve || side.material!!.NoFragment()) {
                area = CheckWindingInAreas_r(w, node)
                if (area >= 0) {
                    val tri: mapTri_s?

                    // put in single area
                    tri = TriListForSide(side, w)
                    AddTriListToArea(e, tri, side.planenum, area, side.texVec)
                    return
                }
            }
            w.Split(dmap.dmapGlobals.mapPlanes[node.planenum], ON_EPSILON, front, back)
            PutWindingIntoAreas_r(e, front, side, node.children[0])
            //		if ( front ) {
            //			delete front;
            //		}
            PutWindingIntoAreas_r(e, back, side, node.children[1])
            //		if ( back ) {
            //			delete back;
            //		}
            return
        }

        // if opaque leaf, don't add
        if (node.area >= 0 && !node.opaque) {
            val tri: mapTri_s?
            tri = TriListForSide(side, w)
            AddTriListToArea(e, tri, side.planenum, node.area, side.texVec)
        }
    }

    /*
     ==================
     AddMapTriToAreas

     Used for curves and inlined models
     ==================
     */
    fun AddMapTriToAreas(tri: mapTri_s, e: uEntity_t) {
        val area: Int
        var w: idWinding?

        // skip degenerate triangles from pinched curves
        if (tritools.MapTriArea(tri) <= 0) {
            return
        }
        if (dmap.dmapGlobals.fullCarve) {
            // always fragment into areas
            w = tritools.WindingForTri(tri)
            ClipTriIntoTree_r(w, tri, e, e.tree.headnode)
            return
        }
        w = tritools.WindingForTri(tri)
        area = CheckWindingInAreas_r(w, e.tree.headnode)
        //	delete w;
        if (area == -1) {
            return
        }
        if (area >= 0) {
            val newTri: mapTri_s?
            val plane = idPlane()
            val planeNum: Int
            val texVec = textureVectors_t()

            // put in single area
            newTri = tritools.CopyMapTri(tri)
            newTri.next = null
            tritools.PlaneForTri(tri, plane)
            planeNum = FindFloatPlane(plane)
            TexVecForTri(texVec, newTri)
            AddTriListToArea(e, newTri, planeNum, area, texVec)
        } else {
            // fragment into areas
            w = tritools.WindingForTri(tri)
            ClipTriIntoTree_r(w, tri, e, e.tree.headnode)
        }
    }

    /*
     =====================
     PutPrimitivesInAreas

     =====================
     */
    fun PutPrimitivesInAreas(e: uEntity_t) {
        var b: uBrush_t?
        var i: Int
        var side: side_s?
        var prim: primitive_s?
        var tri: mapTri_s?
        Common.common.Printf("----- PutPrimitivesInAreas -----\n")

        // allocate space for surface chains for each area
        e.areas = Array(e.numAreas) { uArea_t() } // Mem_Alloc(e.numAreas);
        //	memset( e.areas, 0, e.numAreas * sizeof( e.areas[0] ) );

        // for each primitive, clip it to the non-solid leafs
        // and divide it into different areas
        prim = e.primitives
        while (prim != null) {
            b = prim.brush as uBrush_t?
            if (b == null) {
                // add curve triangles
                tri = prim.tris
                while (tri != null) {
                    AddMapTriToAreas(tri, e)
                    tri = tri.next
                }
                prim = prim.next
                continue
            }

            // clip in brush sides
            i = 0
            while (i < b.numsides) {
                side = b.sides[i]
                if (side.visibleHull == null) {
                    i++
                    continue
                }
                PutWindingIntoAreas_r(e, side.visibleHull, side, e.tree.headnode)
                i++
            }
            prim = prim.next
        }

        // optionally inline some of the func_static models
        if (dmap.dmapGlobals.entityNum == 0) {
            val inlineAll = dmap.dmapGlobals.uEntities[0].mapEntity.epairs.GetBool("inlineAllStatics")
            for (eNum in 1 until dmap.dmapGlobals.num_entities) {
                val entity = dmap.dmapGlobals.uEntities[eNum]
                val className = entity.mapEntity.epairs.GetString("classname")
                if (idStr.Icmp(className, "func_static") != 0) {
                    continue
                }
                if (!entity.mapEntity.epairs.GetBool("inline") && !inlineAll) {
                    continue
                }
                val modelName = entity.mapEntity.epairs.GetString("model")
                if (modelName.isNotEmpty()) {
                    continue
                }
                val model = ModelManager.renderModelManager.FindModel(modelName)!!
                Common.common.Printf("inlining %s.\n", entity.mapEntity.epairs.GetString("name"))
                val axis = idMat3()
                // get the rotation matrix in either full form, or single angle form
                if (!entity.mapEntity.epairs.GetMatrix("rotation", "1 0 0 0 1 0 0 0 1", axis)) {
                    val angle = entity.mapEntity.epairs.GetFloat("angle")
                    if (angle != 0.0f) {
                        axis.set(idAngles(0.0f, angle, 0.0f).ToMat3())
                    } else {
                        axis.Identity()
                    }
                }
                val origin = idVec3(entity.mapEntity.epairs.GetVector("origin"))
                i = 0
                while (i < model.NumSurfaces()) {
                    val surface = model.Surface(i)
                    val tri2 = surface!!.geometry
                    val mapTri = mapTri_s()
                    //				memset( &mapTri, 0, sizeof( mapTri ) );
                    mapTri.material = surface.shader
                    // don't let discretes (autosprites, etc) merge together
                    if (mapTri.material!!.IsDiscrete()) {
                        mapTri.mergeGroup = surface
                    }
                    var j = 0
                    while (j < tri2!!.numIndexes) {
                        for (k in 0..2) {
                            val v = idVec3(tri2.verts!![tri2.indexes!![j + k]]!!.xyz)
                            mapTri.v[k].xyz.set(v.times(axis).plus(origin))
                            mapTri.v[k].normal.set(tri2.verts!![tri2.indexes!![j + k]]!!.normal.times(axis))
                            mapTri.v[k].st.set(tri2.verts!![tri2.indexes!![j + k]]!!.st)
                        }
                        AddMapTriToAreas(mapTri, e)
                        j += 3
                    }
                    i++
                }
            }
        }
    }

    //============================================================================
    /*
     =================
     ClipTriByLight

     Carves a triangle by the frustom planes of a light, producing
     a (possibly empty) list of triangles on the inside and outside.

     The original triangle is not modified.

     If no clipping is required, the result will be a copy of the original.

     If clipping was required, the outside fragments will be planar clips, which
     will benefit from re-optimization.
     =================
     */
    fun ClipTriByLight(light: mapLight_t, tri: mapTri_s, `in`: mapTri_s, out: mapTri_s) {
        var inside: idWinding?
        var oldInside: idWinding? = null
        val outside = arrayOfNulls<idWinding?>(6)
        var hasOutside: Boolean
        var i: Int

//        in[0] = null;
//        out[0] = null;
        // clip this winding to the light
        inside = tritools.WindingForTri(tri)
        hasOutside = false
        i = 0
        while (i < 6) {
            if (oldInside != null) {
                oldInside.Split(light.def.frustum[i], 0.0f, outside[i]!!, inside)
                oldInside = null
            } else {
                outside[i] = null
            }
            if (outside[i] != null) {
                hasOutside = true
            }
            i++
        }
        if (inside == null) {
            // the entire winding is outside this light

            // free the clipped fragments
            i = 0
            while (i < 6) {
                if (outside[i] != null) {
                    //				delete outside[i];
                    outside[i] = null
                }
                i++
            }
            out.oSet(tritools.CopyMapTri(tri))
            out.next = null
            return
        }
        if (!hasOutside) {
            // the entire winding is inside this light

            // free the inside copy
            //		delete inside;
            inside = null
            `in`.oSet(tritools.CopyMapTri(tri))
            `in`.next = null
            return
        }

        // the winding is split
        `in`.oSet(tritools.WindingToTriList(inside, tri))
        //	delete inside;

        // combine all the outside fragments
        i = 0
        while (i < 6) {
            if (outside[i] != null) {
                var list: mapTri_s?
                list = tritools.WindingToTriList(outside[i], tri)
                //			delete outside[i];
                out.oSet(tritools.MergeTriLists(out, list))
            }
            i++
        }
    }

    /*
     =================
     BoundOptimizeGroup
     =================
     */
    fun BoundOptimizeGroup(group: optimizeGroup_s) {
        group.bounds.Clear()
        var tri = group.triList
        while (tri != null) {
            group.bounds.AddPoint(tri.v[0].xyz)
            group.bounds.AddPoint(tri.v[1].xyz)
            group.bounds.AddPoint(tri.v[2].xyz)
            tri = tri.next
        }
    }

    /*
     ====================
     BuildLightShadows

     Build the beam tree and shadow volume surface for a light
     ====================
     */
    fun BuildLightShadows(e: uEntity_t, light: mapLight_t) {
        var i: Int
        var group: optimizeGroup_s?
        var tri: mapTri_s?
        var shadowers: mapTri_s?
        var shadowerGroups: optimizeGroup_s?
        val lightOrigin: idVec3
        var hasPerforatedSurface = false

        //
        // build a group list of all the triangles that will contribute to
        // the optimized shadow volume, leaving the original triangles alone
        //
        // shadowers will contain all the triangles that will contribute to the
        // shadow volume
        shadowerGroups = null
        lightOrigin = idVec3(light.def.globalLightOrigin)

        // if the light is no-shadows, don't add any surfaces
        // to the beam tree at all
        if (!light.def.parms.noShadows._val
            && light.def.lightShader!!.LightCastsShadows()
        ) {
            i = 0
            while (i < e.numAreas) {
                group = e.areas[i].groups
                while (group != null) {

                    // if the surface doesn't cast shadows, skip it
                    if (!group.material!!.SurfaceCastsShadow()) {
                        group = group.nextGroup
                        continue
                    }

                    // if the group doesn't face away from the light, it
                    // won't contribute to the shadow volume
                    if (dmap.dmapGlobals.mapPlanes[group.planeNum].Distance(lightOrigin) > 0) {
                        group = group.nextGroup
                        continue
                    }

                    // if the group bounds doesn't intersect the light bounds,
                    // skip it
                    if (!group.bounds.IntersectsBounds(light.def.frustumTris!!.bounds)) {
                        group = group.nextGroup
                        continue
                    }

                    // build up a list of the triangle fragments inside the
                    // light frustum
                    shadowers = null
                    tri = group.triList
                    while (tri != null) {
                        val `in` = mapTri_s()
                        val out = mapTri_s()

                        // clip it to the light frustum
                        ClipTriByLight(light, tri, `in`, out)
                        tritools.FreeTriList(out)
                        shadowers = tritools.MergeTriLists(shadowers, `in`)
                        tri = tri.next
                    }

                    // if we didn't get any out of this group, we don't
                    // need to create a new group in the shadower list
                    if (shadowers == null) {
                        group = group.nextGroup
                        continue
                    }

                    // find a group in shadowerGroups to add these to
                    // we will ignore everything but planenum, and we
                    // can merge across areas
                    var check: optimizeGroup_s?
                    check = shadowerGroups
                    while (check != null) {
                        if (check.planeNum == group.planeNum) {
                            break
                        }
                        check = check.nextGroup
                    }
                    if (check == null) {
//                        check = (optimizeGroup_s) Mem_Alloc(sizeof(check));
                        check = group
                        check.triList = null
                        check.nextGroup = shadowerGroups
                        shadowerGroups = check
                    }

                    // if any surface is a shadow-casting perforated or translucent surface, we
                    // can't use the face removal optimizations because we can see through
                    // some of the faces
                    if (group.material!!.Coverage() != materialCoverage_t.MC_OPAQUE) {
                        hasPerforatedSurface = true
                    }
                    check!!.triList = tritools.MergeTriLists(check.triList, shadowers)
                    group = group.nextGroup
                }
                i++
            }
        }

        // take the shadower group list and create a beam tree and shadow volume
        light.shadowTris = shadowopt3.CreateLightShadow(shadowerGroups, light)
        if (light.shadowTris != null && hasPerforatedSurface) {
            // can't ever remove front faces, because we can see through some of them
            light.shadowTris!!.numShadowIndexesNoFrontCaps = light.shadowTris!!.numIndexes
            light.shadowTris!!.numShadowIndexesNoCaps = light.shadowTris!!.numShadowIndexesNoFrontCaps
        }

        // we don't need the original shadower triangles for anything else
        map.FreeOptimizeGroupList(shadowerGroups)
    }

    /*
     ====================
     CarveGroupsByLight

     Divide each group into an inside group and an outside group, based
     on which fragments are illuminated by the light's beam tree
     ====================
     */
    fun CarveGroupsByLight(e: uEntity_t, light: mapLight_t) {
        var i: Int
        var group: optimizeGroup_s?
        var newGroup: optimizeGroup_s?
        var carvedGroups: optimizeGroup_s?
        var nextGroup: optimizeGroup_s?
        var tri: mapTri_s?
        var inside: mapTri_s?
        var outside: mapTri_s?
        var area: uArea_t?
        i = 0
        while (i < e.numAreas) {
            area = e.areas[i]
            carvedGroups = null

            // we will be either freeing or reassigning the groups as we go
            group = area.groups
            while (group != null) {
                nextGroup = group.nextGroup
                // if the surface doesn't get lit, don't carve it up
                if (light.def.lightShader!!.IsFogLight() && !group.material!!.ReceivesFog()
                    || !light.def.lightShader!!.IsFogLight() && !group.material!!.ReceivesLighting()
                    || !group.bounds.IntersectsBounds(light.def.frustumTris!!.bounds)
                ) {
                    group.nextGroup = carvedGroups
                    carvedGroups = group
                    group = nextGroup
                    continue
                }
                if (group.numGroupLights == dmap.MAX_GROUP_LIGHTS) {
                    Common.common.Error(
                        "MAX_GROUP_LIGHTS around %f %f %f",
                        group.triList!!.v[0].xyz[0], group.triList!!.v[0].xyz[1], group.triList!!.v[0].xyz[2]
                    )
                }

                // if the group doesn't face the light,
                // it won't get carved at all
                if (light.def.lightShader!!.LightEffectsBackSides()
                    && !group.material!!.ReceivesLightingOnBackSides()
                    && dmap.dmapGlobals.mapPlanes[group.planeNum].Distance(light.def.parms.origin) <= 0
                ) {
                    group.nextGroup = carvedGroups
                    carvedGroups = group
                    group = nextGroup
                    continue
                }

                // split into lists for hit-by-light, and not-hit-by-light
                inside = null
                outside = null
                tri = group.triList
                while (tri != null) {
                    val `in` = mapTri_s()
                    val out = mapTri_s()
                    ClipTriByLight(light, tri, `in`, out)
                    inside = tritools.MergeTriLists(inside, `in`)
                    outside = tritools.MergeTriLists(outside, out)
                    tri = tri.next
                }
                if (inside != null) {
//                    newGroup = (optimizeGroup_s) Mem_Alloc(sizeof(newGroup));
                    newGroup = group
                    newGroup.groupLights[newGroup.numGroupLights] = light
                    newGroup.numGroupLights++
                    newGroup.triList = inside
                    newGroup.nextGroup = carvedGroups
                    carvedGroups = newGroup
                }
                if (outside != null) {
//                    newGroup = (optimizeGroup_s) Mem_Alloc(sizeof(newGroup));
                    newGroup = group
                    newGroup.triList = outside
                    newGroup.nextGroup = carvedGroups
                    carvedGroups = newGroup
                }

                // free the original
                group.nextGroup = null
                map.FreeOptimizeGroupList(group)
                group = nextGroup
            }

            // replace this area's group list with the new one
            area.groups = carvedGroups
            i++
        }
    }

    /*
     =====================
     Prelight

     Break optimize groups up into additional groups at light boundaries, so
     optimization won't cross light bounds
     =====================
     */
    fun Prelight(e: uEntity_t) {
        var i: Int
        var start: Int
        var end: Int
        var light: mapLight_t?

        // don't prelight anything but the world entity
        if (dmap.dmapGlobals.entityNum != 0) {
            return
        }
        if (TempDump.etoi(dmap.dmapGlobals.shadowOptLevel) > 0) {
            Common.common.Printf("----- BuildLightShadows -----\n")
            start = win_shared.Sys_Milliseconds()

            // calc bounds for all the groups to speed things up
            i = 0
            while (i < e.numAreas) {
                val area = e.areas[i]
                var group = area.groups
                while (group != null) {
                    BoundOptimizeGroup(group)
                    group = group.nextGroup
                }
                i++
            }
            i = 0
            while (i < dmap.dmapGlobals.mapLights.Num()) {
                light = dmap.dmapGlobals.mapLights[i]
                BuildLightShadows(e, light)
                i++
            }
            end = win_shared.Sys_Milliseconds()
            Common.common.Printf("%5.1f seconds for BuildLightShadows\n", (end - start) / 1000.0f)
        }
        if (!dmap.dmapGlobals.noLightCarve) {
            Common.common.Printf("----- CarveGroupsByLight -----\n")
            start = win_shared.Sys_Milliseconds()
            // now subdivide the optimize groups into additional groups for
            // each light that illuminates them
            i = 0
            while (i < dmap.dmapGlobals.mapLights.Num()) {
                light = dmap.dmapGlobals.mapLights[i]
                CarveGroupsByLight(e, light)
                i++
            }
            end = win_shared.Sys_Milliseconds()
            Common.common.Printf("%5.1f seconds for CarveGroupsByLight\n", (end - start) / 1000.0f)
        }
    }
}
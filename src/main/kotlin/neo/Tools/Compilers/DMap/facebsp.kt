package neo.Tools.Compilers.DMap

import neo.Renderer.Material
import neo.Tools.Compilers.DMap.dmap.bspface_s
import neo.Tools.Compilers.DMap.dmap.node_s
import neo.Tools.Compilers.DMap.dmap.primitive_s
import neo.Tools.Compilers.DMap.dmap.side_s
import neo.Tools.Compilers.DMap.dmap.tree_s
import neo.Tools.Compilers.DMap.dmap.uBrush_t
import neo.Tools.Compilers.DMap.dmap.uPortal_s
import neo.Tools.Compilers.DMap.map.FindFloatPlane
import neo.framework.Common
import neo.idlib.geometry.Winding.idWinding
import neo.idlib.math.*
import neo.sys.win_shared
import kotlin.math.abs
import kotlin.math.floor

object facebsp {
    //
    const val BLOCK_SIZE = 1024

    //
    //
    var c_nodes = 0
    var c_faceLeafs = 0

    //void RemovePortalFromNode( uPortal_s *portal, node_s *l );
    fun NodeForPoint(node: node_s, origin: idVec3): node_s {
        var node = node
        var d: Float
        while (node.planenum != dmap.PLANENUM_LEAF) {
            val plane = dmap.dmapGlobals.mapPlanes[node.planenum]
            d = plane.Distance(origin)
            node = if (d >= 0) {
                node.children[0]
            } else {
                node.children[1]
            }
        }
        return node
    }

    /*
     =============
     FreeTreePortals_r
     =============
     */
    fun FreeTreePortals_r(node: node_s) {
        var p: uPortal_s?
        var nextp: uPortal_s?
        var s: Int

        // free children
        if (node.planenum != dmap.PLANENUM_LEAF) {
            FreeTreePortals_r(node.children[0])
            FreeTreePortals_r(node.children[1])
        }

        // free portals
        p = node.portals
        while (p != null) {
            s = if (p.nodes[1] == node) 1 else 0
            nextp = p.next[s]
            portals.RemovePortalFromNode(p, p.nodes[1 xor s]!!)
            portals.FreePortal(p)
            p = nextp
        }
        node.portals = null
    }

    /*
     =============
     FreeTree_r
     =============
     */
    fun FreeTree_r(node: node_s) {
        // free children
        if (node.planenum != dmap.PLANENUM_LEAF) {
            FreeTree_r(node.children[0])
            FreeTree_r(node.children[1])
        }

        // free brushes
        ubrush.FreeBrushList(node.brushlist)

        // free the node
        c_nodes--
        node.clear() //Mem_Free(node);
    }

    /*
     =============
     FreeTree
     =============
     */
    fun FreeTree(tree: tree_s?) {
        if (tree == null) {
            return
        }
        FreeTreePortals_r(tree.headnode)
        FreeTree_r(tree.headnode)
        tree.clear() //Mem_Free(tree);
    }

    //===============================================================
    fun PrintTree_r(node: node_s, depth: Int) {
        var i: Int
        var bb: uBrush_t?
        i = 0
        while (i < depth) {
            Common.common.Printf("  ")
            i++
        }
        if (node.planenum == dmap.PLANENUM_LEAF) {
            if (node.brushlist == null) {
                Common.common.Printf("NULL\n")
            } else {
                bb = node.brushlist
                while (bb != null) {
                    Common.common.Printf("%d ", bb.original.brushnum)
                    bb = bb.next as uBrush_t
                }
                Common.common.Printf("\n")
            }
            return
        }
        val plane = dmap.dmapGlobals.mapPlanes[node.planenum]
        Common.common.Printf(
            "#%d (%5.2f %5.2f %5.2f %5.2f)\n", node.planenum,
            plane[0], plane[1], plane[2], plane[3]
        )
        PrintTree_r(node.children[0], depth + 1)
        PrintTree_r(node.children[1], depth + 1)
    }

    /*
     ================
     AllocBspFace
     ================
     */
    fun AllocBspFace(): bspface_s {
        val f: bspface_s
        f = bspface_s() // Mem_Alloc(sizeof(f));
        //	memset( f, 0, sizeof(*f) );
        return f
    }

    /*
     ================
     FreeBspFace
     ================
     */
    fun FreeBspFace(f: bspface_s) {
        if (f.w != null) {
            //		delete f.w;
            f.w = null
        }
        f.clear() //Mem_Free(f);
    }

    /*
     ================
     SelectSplitPlaneNum
     ================
     */
    fun SelectSplitPlaneNum(node: node_s, list: bspface_s): Int {
        var split: bspface_s?
        var check: bspface_s?
        var bestSplit: bspface_s
        var splits: Int
        var facing: Int
        var front: Int
        var back: Int
        var side: Int
        var value: Int
        var bestValue: Int
        val plane = idPlane()
        val planenum: Int
        var havePortals: Boolean
        var dist: Float
        val halfSize = idVec3()

        // if it is crossing a 1k block boundary, force a split
        // this prevents epsilon problems from extending an
        // arbitrary distance across the map
        halfSize.set(node.bounds[1].minus(node.bounds[0]).times(0.5f))
        for (axis in 0..2) {
            dist = if (halfSize[axis] > BLOCK_SIZE) {
                (BLOCK_SIZE * (floor(
                    ((node.bounds[0, axis] + halfSize[axis]) / BLOCK_SIZE)
                ) + 1.0f))
            } else {
                (BLOCK_SIZE * (floor(
                    (node.bounds[0, axis] / BLOCK_SIZE)
                ) + 1.0f))
            }
            if (dist > node.bounds[0, axis] + 1.0f && dist < node.bounds[1, axis] - 1.0f) {
                plane[0] = plane.set(1, plane.set(2, 0.0f))
                plane[axis] = 1.0f
                plane[3] = -dist
                planenum = FindFloatPlane(plane)
                return planenum
            }
        }

        // pick one of the face planes
        // if we have any portal faces at all, only
        // select from them, otherwise select from
        // all faces
        bestValue = -999999
        bestSplit = list
        havePortals = false
        split = list
        while (split != null) {
            split.checked = false
            if (split.portal) {
                havePortals = true
            }
            split = split.next
        }
        split = list
        while (split != null) {
            if (split.checked) {
                split = split.next
                continue
            }
            if (havePortals != split.portal) {
                split = split.next
                continue
            }
            val mapPlane = dmap.dmapGlobals.mapPlanes[split.planenum]
            splits = 0
            facing = 0
            front = 0
            back = 0
            check = list
            while (check != null) {
                if (check.planenum == split.planenum) {
                    facing++
                    check.checked = true // won't need to test this plane again
                    check = check.next
                    continue
                }
                side = check.w!!.PlaneSide(mapPlane)
                if (side == SIDE_CROSS) {
                    splits++
                } else if (side == SIDE_FRONT) {
                    front++
                } else if (side == SIDE_BACK) {
                    back++
                }
                check = check.next
            }
            value = 5 * facing - 5 * splits // - abs(front-back);
            if (mapPlane.Type() < PLANETYPE_TRUEAXIAL) {
                value += 5 // axial is better
            }
            if (value > bestValue) {
                bestValue = value
                bestSplit = split
            }
            split = split.next
        }
        return if (bestValue == -999999) {
            -1
        } else bestSplit.planenum
    }

    /*
     ================
     BuildFaceTree_r
     ================
     */
    fun BuildFaceTree_r(node: node_s, list: bspface_s) {
        var split: bspface_s?
        var next: bspface_s?
        var side: Int
        var newFace: bspface_s?
        val childLists = arrayOfNulls<bspface_s?>(2)
        val frontWinding = idWinding()
        val backWinding = idWinding()
        var i: Int
        val splitPlaneNum: Int
        splitPlaneNum = SelectSplitPlaneNum(node, list)
        // if we don't have any more faces, this is a node
        if (splitPlaneNum == -1) {
            node.planenum = dmap.PLANENUM_LEAF
            c_faceLeafs++
            return
        }

        // partition the list
        node.planenum = splitPlaneNum
        val plane = dmap.dmapGlobals.mapPlanes[splitPlaneNum]
        childLists[0] = null
        childLists[1] = null
        split = list
        while (split != null) {
            next = split.next
            if (split.planenum == node.planenum) {
                FreeBspFace(split)
                split = next
                continue
            }
            side = split.w!!.PlaneSide(plane)
            if (side == SIDE_CROSS) {
                split.w!!.Split(plane, ubrush.CLIP_EPSILON * 2, frontWinding, backWinding)
                if (!frontWinding.isNULL()) {
                    newFace = AllocBspFace()
                    newFace.w = frontWinding
                    newFace.next = childLists[0]
                    newFace.planenum = split.planenum
                    childLists[0] = newFace
                }
                if (!backWinding.isNULL()) {
                    newFace = AllocBspFace()
                    newFace.w = backWinding
                    newFace.next = childLists[1]
                    newFace.planenum = split.planenum
                    childLists[1] = newFace
                }
                FreeBspFace(split)
            } else if (side == SIDE_FRONT) {
                split.next = childLists[0]
                childLists[0] = split
            } else if (side == SIDE_BACK) {
                split.next = childLists[1]
                childLists[1] = split
            }
            split = next
        }

        // recursively process children
        i = 0
        while (i < 2) {
            node.children[i] = ubrush.AllocNode()
            node.children[i].parent = node
            node.children[i].bounds.set(node.bounds)
            i++
        }

        // split the bounds if we have a nice axial plane
        i = 0
        while (i < 3) {
            if (abs(plane[i] - 1.0f) < 0.001) {
                node.children[0].bounds[0, i] = plane.Dist()
                node.children[1].bounds[1, i] = plane.Dist()
                break
            }
            i++
        }
        i = 0
        while (i < 2) {
            BuildFaceTree_r(node.children[i], childLists[i]!!)
            i++
        }
    }

    /*
     ================
     FaceBSP

     List will be freed before returning
     ================
     */
    fun FaceBSP(list: bspface_s): tree_s {
        val tree: tree_s
        var face: bspface_s?
        var i: Int
        var count: Int
        val start: Int
        val end: Int
        start = win_shared.Sys_Milliseconds()
        Common.common.Printf("--- FaceBSP ---\n")
        tree = ubrush.AllocTree()
        count = 0
        tree.bounds.Clear()
        face = list
        while (face != null) {
            count++
            i = 0
            while (i < face.w!!.GetNumPoints()) {
                tree.bounds.AddPoint(face.w!!.get(i).ToVec3())
                i++
            }
            face = face.next
        }
        Common.common.Printf("%5d faces\n", count)
        tree.headnode = ubrush.AllocNode()
        tree.headnode.bounds.set(tree.bounds)
        c_faceLeafs = 0
        BuildFaceTree_r(tree.headnode, list)
        Common.common.Printf("%5d leafs\n", c_faceLeafs)
        end = win_shared.Sys_Milliseconds()
        Common.common.Printf("%5.1f seconds faceBsp\n", (end - start) / 1000.0f)
        return tree
    }

    //==========================================================================
    /*
     =================
     MakeStructuralBspFaceList
     =================
     */
    fun MakeStructuralBspFaceList(list: primitive_s?): bspface_s {
        var list = list
        var b: uBrush_t?
        var i: Int
        var s: side_s?
        var w: idWinding?
        var f: bspface_s?
        var flist: bspface_s? = null
        while (list != null) {
            b = list.brush as uBrush_t?
            if (b == null) {
                list = list.next
                continue
            }
            if (!b.opaque && 0 == b.contents and Material.CONTENTS_AREAPORTAL) {
                list = list.next
                continue
            }
            i = 0
            while (i < b.numsides) {
                s = b.sides[i]
                w = s.winding
                if (w == null) {
                    i++
                    continue
                }
                if (b.contents and Material.CONTENTS_AREAPORTAL != 0 && 0 == s.material!!.GetContentFlags() and Material.CONTENTS_AREAPORTAL) {
                    i++
                    continue
                }
                f = AllocBspFace()
                if (s.material!!.GetContentFlags() and Material.CONTENTS_AREAPORTAL != 0) {
                    f.portal = true
                }
                f.w = w.Copy()
                f.planenum = s.planenum and 1.inv()
                f.next = flist
                flist = f
                i++
            }
            list = list.next
        }
        return flist!!
    }

    /*
     =================
     MakeVisibleBspFaceList
     =================
     */
    fun MakeVisibleBspFaceList(list: primitive_s?): bspface_s {
        var list = list
        var b: uBrush_t?
        var i: Int
        var s: side_s?
        var w: idWinding?
        var f: bspface_s?
        var flist: bspface_s?
        flist = null
        while (list != null) {
            b = list.brush as uBrush_t?
            if (b == null) {
                list = list.next
                continue
            }
            if (!b.opaque && 0 == b.contents and Material.CONTENTS_AREAPORTAL) {
                list = list.next
                continue
            }
            i = 0
            while (i < b.numsides) {
                s = b.sides[i]
                w = s.visibleHull
                if (w == null) {
                    i++
                    continue
                }
                f = AllocBspFace()
                if (s.material!!.GetContentFlags() and Material.CONTENTS_AREAPORTAL != 0) {
                    f.portal = true
                }
                f.w = w.Copy()
                f.planenum = s.planenum and 1.inv()
                f.next = flist
                flist = f
                i++
            }
            list = list.next
        }
        return flist!!
    }
}
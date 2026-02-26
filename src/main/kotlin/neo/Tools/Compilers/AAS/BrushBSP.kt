package neo.Tools.Compilers.AAS

import neo.TempDump
import neo.Tools.Compilers.AAS.AASBuild.Allowance
import neo.Tools.Compilers.AAS.Brush.idBrush
import neo.Tools.Compilers.AAS.Brush.idBrushList
import neo.Tools.Compilers.AAS.Brush.idBrushMap
import neo.Tools.Compilers.AAS.Brush.idBrushSide
import neo.framework.Common
import neo.framework.FileSystem_h
import neo.framework.File_h.idFile
import neo.idlib.BIT
import neo.idlib.BV.idBounds
import neo.idlib.MAX_WORLD_COORD
import neo.idlib.MIN_WORLD_COORD
import neo.idlib.MapFile.idMapEntity
import neo.idlib.MapFile.idMapFile
import neo.idlib.Text.Str.idStr
import neo.idlib.containers.List.idList
import neo.idlib.containers.PlaneSet.idPlaneSet
import neo.idlib.containers.VectorSet.idVectorSet
import neo.idlib.containers.idStrList
import neo.idlib.geometry.Winding.idWinding
import neo.idlib.math.*
import java.util.*
import kotlin.math.abs
import kotlin.math.floor

const val BASE_WINDING_EPSILON = 0.001f
const val BSP_GRID_SIZE = 512.0f
val NODE_DONE: Int = BIT(31)
val NODE_VISITED: Int = BIT(30)
const val OUPUT_BSP_STATS_PER_GRID_CELL = false
const val PORTAL_PLANE_DIST_EPSILON = 0.01f
const val PORTAL_PLANE_NORMAL_EPSILON = 0.00001f
const val SPLITTER_EPSILON = 0.1f
const val SPLIT_WINDING_EPSILON = 0.001f
const val VERTEX_MELT_EPSILON = 0.1f
const val VERTEX_MELT_HASH_SIZE = 32.0f

//===============================================================
//
//	idBrushBSPPortal
//
//===============================================================
internal class idBrushBSPPortal {
    private var faceNum // number of the face created for this portal
            : Int
    private var flags // portal flags
            : Int
    val next: Array<idBrushBSPPortal?> =
        arrayOfNulls<idBrushBSPPortal?>(2) // next portal in list for both nodes
    val nodes: Array<idBrushBSPNode?> = arrayOfNulls<idBrushBSPNode?>(2) // nodes this portal seperates
    val plane: idPlane = idPlane() // portal plane
    var planeNum // number of plane this portal is on
            : Int
    var winding // portal winding
            : idWinding?

    // ~idBrushBSPPortal();
    fun AddToNodes(front: idBrushBSPNode, back: idBrushBSPNode) {
        if (nodes[0] != null || nodes[1] != null) {
            Common.common.Error("AddToNode: allready included")
        }
        assert(front != null && back != null)
        nodes[0] = front
        next[0] = front.portals
        front.portals = this
        nodes[1] = back
        next[1] = back.portals
        back.portals = this
    }

    fun RemoveFromNode(l: idBrushBSPNode) {
        val pp: idBrushBSPPortal?
        var t: idBrushBSPPortal?

        // remove reference to the current portal
        pp = l.portals
        while (true) {
            t = pp
            if (t == null) {
                Common.common.Error("idBrushBSPPortal::RemoveFromNode: portal not in node")
                return
            }
            if (t === this) {
                break
            }
            if (t.nodes[0] === l) {
                pp.set(t.next[0]!!)
            } else if (t.nodes[1] === l) {
                pp.set(t.next[1]!!)
            } else {
                Common.common.Error("idBrushBSPPortal::RemoveFromNode: portal not bounding node")
            }
        }
        if (nodes[0] === l) {
            pp.set(next[0]!!)
            nodes[0] = null
        } else if (nodes[1] === l) {
            pp.set(next[1]!!)
            nodes[1] = null
        } else {
            Common.common.Error("idBrushBSPPortal::RemoveFromNode: mislinked portal")
        }
    }

    fun Flip() {
        val frontNode: idBrushBSPNode?
        val backNode: idBrushBSPNode?
        frontNode = nodes[0]
        backNode = nodes[1]
        frontNode?.let { RemoveFromNode(it) }
        backNode?.let { RemoveFromNode(it) }
        AddToNodes(frontNode!!, backNode!!)
        plane.set(plane.unaryMinus())
        planeNum = planeNum xor 1
        winding!!.ReverseSelf()
    }

    fun Split(splitPlane: idPlane, front: idBrushBSPPortal, back: idBrushBSPPortal): Int {
        val frontWinding = idWinding()
        val backWinding = idWinding()

        winding!!.Split(splitPlane, 0.1f, frontWinding, backWinding)
        if (!frontWinding.isNULL()) {
            front.set(idBrushBSPPortal())
            front.plane.set(plane)
            front.planeNum = planeNum
            front.flags = flags
            front.winding = frontWinding
        }
        if (!backWinding.isNULL()) {
            back.set(idBrushBSPPortal())
            back.plane.set(plane)
            back.planeNum = planeNum
            back.flags = flags
            back.winding = backWinding
        }
        return if (!frontWinding.isNULL() && !backWinding.isNULL()) {
            PLANESIDE_CROSS
        } else if (!frontWinding.isNULL()) {
            PLANESIDE_FRONT
        } else {
            PLANESIDE_BACK
        }
    }

    fun GetWinding(): idWinding {
        return winding!!
    }

    fun GetPlane(): idPlane {
        return plane
    }

    fun SetFaceNum(num: Int) {
        faceNum = num
    }

    fun GetFaceNum(): Int {
        return faceNum
    }

    fun GetFlags(): Int {
        return flags
    }

    fun SetFlag(flag: Int) {
        flags = flags or flag
    }

    fun RemoveFlag(flag: Int) {
        flags = flags and flag.inv()
    }

    fun Next(side: Int): idBrushBSPPortal? {
        return next[side]
    }

    fun GetNode(side: Int): idBrushBSPNode? {
        return nodes[side]
    }

    private fun set(idBrushBSPPortal: idBrushBSPPortal) {
        throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
    }

    //
    //
    // friend class idBrushBSP;
    // friend class idBrushBSPNode;
    init {
        planeNum = -1
        winding = null
        nodes[1] = null
        nodes[0] = nodes[1]
        next[1] = null
        next[0] = next[1]
        faceNum = 0
        flags = 0
    }
}

//===============================================================
//
//	idBrushBSPNode
//
//===============================================================
internal class idBrushBSPNode {
    private var areaNum // number of the area created for this node
            : Int
    var brushList // list with brushes for this node
            : idBrushList = idBrushList()
    val children: Array<idBrushBSPNode?> =
        arrayOfNulls<idBrushBSPNode?>(2) // both are NULL if this is a leaf node
    var contents // node contents
            : Int
    private var flags // node flags
            : Int
    var occupied // true when portal is occupied
            : Int
    var parent // parent of this node
            : idBrushBSPNode? = null

    // friend class idBrushBSP;
    // friend class idBrushBSPPortal;
    val plane: idPlane = idPlane() // split plane if this is not a leaf node
    var portals // portals of this node
            : idBrushBSPPortal?
    var volume // node volume
            : idBrush?

    // ~idBrushBSPNode();
    fun SetContentsFromBrushes() {
        var brush: idBrush?
        contents = 0
        brush = brushList.Head()
        while (brush != null) {
            contents = contents or brush.GetContents()
            brush = brush.Next()
        }
    }

    fun GetPortalBounds(): idBounds {
        var s: Int
        var i: Int
        var p: idBrushBSPPortal?
        val bounds = idBounds()
        bounds.Clear()
        p = portals
        while (p != null) {
            s = if (p.nodes[1] == this) 1 else 0
            i = 0
            while (i < p.winding!!.GetNumPoints()) {
                bounds.AddPoint(p.winding!![i].ToVec3())
                i++
            }
            p = p.next[s]
        }
        return bounds
    }

    fun GetChild(index: Int): idBrushBSPNode? {
        return children[index]
    }

    fun GetParent(): idBrushBSPNode? {
        return parent
    }

    fun SetContents(contents: Int) {
        this.contents = contents
    }

    fun GetContents(): Int {
        return contents
    }

    fun GetPlane(): idPlane {
        return plane
    }

    fun GetPortals(): idBrushBSPPortal? {
        return portals
    }

    fun SetAreaNum(num: Int) {
        areaNum = num
    }

    fun GetAreaNum(): Int {
        return areaNum
    }

    fun GetFlags(): Int {
        return flags
    }

    fun SetFlag(flag: Int) {
        flags = flags or flag
    }

    fun RemoveFlag(flag: Int) {
        flags = flags and flag.inv()
    }

    fun TestLeafNode(): Boolean {
        var s: Int
        var n: Int
        var d: Float
        var p: idBrushBSPPortal?
        val center = idVec3()
        var plane: idPlane
        n = 0
        center.set(getVec3Origin())
        p = portals
        while (p != null) {
            s = if (p.nodes[1] === this) 1 else 0
            center.plusAssign(p.winding!!.GetCenter())
            n++
            p = p.next[s]
        }
        center.divAssign(n.toFloat())
        p = portals
        while (p != null) {
            s = if (p.nodes[1] == this) 1 else 0
            plane = if (s != 0) {
                p.GetPlane().unaryMinus()
            } else {
                p.GetPlane()
            }
            d = plane.Distance(center)
            if (d < 0.0f) {
                return false
            }
            p = p.next[s]
        }
        return true
    }

    // remove the flag from nodes found by flooding through portals to nodes with the flag set
    fun RemoveFlagFlood(flag: Int) {
        var s: Int
        var p: idBrushBSPPortal?
        RemoveFlag(flag)
        p = GetPortals()
        while (p != null) {
            s = if (p.GetNode(1) === this) 1 else 0
            if (0 == p.GetNode( /*!s*/TempDump.SNOT(s.toDouble()))!!.GetFlags() and flag) {
                p = p.Next(s)
                continue
            }
            p.GetNode( /*!s*/TempDump.SNOT(s.toDouble()))!!.RemoveFlagFlood(flag)
            p = p.Next(s)
        }
    }

    // recurse down the tree and remove the flag from all visited nodes
    fun RemoveFlagRecurse(flag: Int) {
        RemoveFlag(flag)
        children[0]?.RemoveFlagRecurse(flag)
        children[1]?.RemoveFlagRecurse(flag)
    }

    // first recurse down the tree and flood from there
    fun RemoveFlagRecurseFlood(flag: Int) {
        RemoveFlag(flag)
        if (children[0] == null && children[1] == null) {
            RemoveFlagFlood(flag)
        } else {
            children[0]?.RemoveFlagRecurseFlood(flag)
            children[1]?.RemoveFlagRecurseFlood(flag)
        }
    }

    // returns side of the plane the node is on
    fun PlaneSide(plane: idPlane, epsilon: Float = ON_EPSILON): Int {
        var s: Int
        var side: Int
        var p: idBrushBSPPortal?
        var front: Boolean
        var back: Boolean
        back = false
        front = back
        p = portals
        while (p != null) {
            s = if (p.nodes[1] == this) 1 else 0
            side = p.winding!!.PlaneSide(plane, epsilon)
            if (side == SIDE_CROSS || side == SIDE_ON) {
                return side
            }
            if (side == SIDE_FRONT) {
                if (back) {
                    return SIDE_CROSS
                }
                front = true
            }
            if (side == SIDE_BACK) {
                if (front) {
                    return SIDE_CROSS
                }
                back = true
            }
            p = p.next[s]
        }
        return if (front) {
            SIDE_FRONT
        } else SIDE_BACK
    }

    // split the leaf node with a plane
    fun Split(splitPlane: idPlane, splitPlaneNum: Int): Boolean {
        var s: Int
        var i: Int
        var mid: idWinding?
        var p: idBrushBSPPortal?
        val midPortal: idBrushBSPPortal
        val newPortals = Array(2) { idBrushBSPPortal() }
        val newNodes = Array(2) { idBrushBSPNode() }
        mid = idWinding(splitPlane.Normal(), splitPlane.Dist())
        p = portals
        while (p != null && mid != null) {
            s = if (p.nodes[1] == this) 1 else 0
            mid = if (s != 0) {
                mid.Clip(p.plane.unaryMinus(), 0.1f, false)
            } else {
                mid.Clip(p.plane, 0.1f, false)
            }
            p = p.next[s]
        }
        if (mid == null) {
            return false
        }

        // allocate two new nodes
        i = 0
        while (i < 2) {
            newNodes[i].flags = flags
            newNodes[i].contents = contents
            newNodes[i].parent = this
            i++
        }

        // split all portals of the node
        p = portals
        while (p != null) {
            s = if (p.nodes[1] == this) 1 else 0
            p.Split(splitPlane, newPortals[0], newPortals[1])
            i = 0
            while (i < 2) {
                if (newPortals[i] != null) {
                    if (s != 0) {
                        newPortals[i].AddToNodes(p.nodes[0]!!, newNodes[i])
                    } else {
                        newPortals[i].AddToNodes(newNodes[i], p.nodes[1]!!)
                    }
                }
                i++
            }
            p.RemoveFromNode(p.nodes[0]!!)
            p.RemoveFromNode(p.nodes[1]!!)
            p = portals
        }

        // add seperating portal
        midPortal = idBrushBSPPortal()
        midPortal.plane.set(splitPlane)
        midPortal.planeNum = splitPlaneNum
        midPortal.winding = mid
        midPortal.AddToNodes(newNodes[0], newNodes[1])

        // set new child nodes
        children[0] = newNodes[0]
        children[1] = newNodes[1]
        plane.set(splitPlane)
        return true
    }

    //
    //
    init {
        brushList.Clear()
        contents = 0
        flags = 0
        volume = null
        portals = null
        children[1] = null
        children[0] = children[1]
        areaNum = 0
        occupied = 0
    }
}

//===============================================================
//
//	idBrushBSP
//
//===============================================================
internal class idBrushBSP {
    //
    private var BrushChopAllowed: Allowance? = null
    private var BrushMergeAllowed: Allowance? = null
    private var brushMap: idBrushMap?
    private var brushMapContents: Int
    private var insideLeafNodes = 0
    private val leakOrigin: idVec3 = idVec3()
    private var numGridCellSplits = 0
    private var numGridCells = 0
    private var numInsertedPoints = 0
    private var numMergedPortals = 0
    private var numPortals = 0
    private var numPrunedSplits: Int
    private var numSplits: Int
    private var outside: idBrushBSPNode = idBrushBSPNode()
    private var outsideLeafNodes = 0
    private val portalPlanes: idPlaneSet = idPlaneSet()
    private var root: idBrushBSPNode = idBrushBSPNode()
    private var solidLeafNodes = 0
    private val treeBounds: idBounds = idBounds()

    // build a bsp tree from a set of brushes
    fun Build(
        brushList: idBrushList, skipContents: Int,
        ChopAllowed: Allowance /*boolean (*ChopAllowed)( idBrush *b1, idBrush *b2 )*/,
        MergeAllowed: Allowance
    ) /*boolean (*MergeAllowed)( idBrush *b1, idBrush *b2 ) )*/ {
        var i: Int
        val gridCells = idList<idBrushBSPNode>()
        Common.common.Printf("[Brush BSP]\n")
        Common.common.Printf("%6d brushes\n", brushList.Num())
        BrushChopAllowed = ChopAllowed
        BrushMergeAllowed = MergeAllowed
        numGridCells = 0
        treeBounds.set(brushList.GetBounds())
        root = idBrushBSPNode()
        root.brushList = brushList
        root.volume = idBrush()
        root.volume!!.FromBounds(treeBounds)
        root.parent = null
        BuildGrid_r(gridCells, root)
        Common.common.Printf("\r%6d grid cells\n", gridCells.Num())
        if (OUPUT_BSP_STATS_PER_GRID_CELL) {
            i = 0
            while (i < gridCells.Num()) {
                ProcessGridCell(gridCells[i], skipContents)
                i++
            }
        } else {
            Common.common.Printf("\r%6d %%", 0)
            i = 0
            while (i < gridCells.Num()) {
                Brush.DisplayRealTimeString("\r%6d", i * 100 / gridCells.Num())
                ProcessGridCell(gridCells[i], skipContents)
                i++
            }
            Common.common.Printf("\r%6d %%\n", 100)
        }
        Common.common.Printf("\r%6d splits\n", numSplits)
        if (brushMap != null) {
//		delete brushMap;
            brushMap = null
        }
    }

    // ~idBrushBSP();
    // remove splits in subspaces with the given contents
    fun PruneTree(contents: Int) {
        numPrunedSplits = 0
        Common.common.Printf("[Prune BSP]\n")
        PruneTree_r(root, contents)
        Common.common.Printf("%6d splits pruned\n", numPrunedSplits)
    }

    // portalize the bsp tree
    fun Portalize() {
        Common.common.Printf("[Portalize BSP]\n")
        Common.common.Printf("%6d nodes\n", (numSplits - numPrunedSplits) * 2 + 1)
        numPortals = 0
        MakeOutsidePortals()
        MakeTreePortals_r(root)
        Common.common.Printf("\r%6d nodes portalized\n", numPortals)
    }

    // remove subspaces outside the map not reachable by entities
    fun RemoveOutside(mapFile: idMapFile, contents: Int, classNames: idStrList): Boolean {
        Common.common.Printf("[Remove Outside]\n")
        insideLeafNodes = 0
        outsideLeafNodes = insideLeafNodes
        solidLeafNodes = outsideLeafNodes
        if (!FloodFromEntities(mapFile, contents, classNames)) {
            return false
        }
        RemoveOutside_r(root, contents)
        Common.common.Printf("%6d solid leaf nodes\n", solidLeafNodes)
        Common.common.Printf("%6d outside leaf nodes\n", outsideLeafNodes)
        Common.common.Printf("%6d inside leaf nodes\n", insideLeafNodes)

        //PruneTree( contents );
        return true
    }

    /*
     =============
     LeakFile

     Finds the shortest possible chain of portals that
     leads from the outside leaf to a specific occupied leaf.
     // write file with a trace going through a leak
     =============
     */
    fun LeakFile(fileName: idStr) {
        var count: Int
        var next: Int
        var s: Int
        val mid = idVec3()
        val lineFile: idFile?
        var node: idBrushBSPNode?
        var nextNode = idBrushBSPNode()
        var p: idBrushBSPPortal?
        var nextPortal = idBrushBSPPortal()
        val qpath: idStr
        if (0 == outside.occupied) {
            return
        }
        qpath = fileName
        qpath.SetFileExtension("lin")
        Common.common.Printf("writing %s...\n", qpath)
        lineFile = FileSystem_h.fileSystem.OpenFileWrite(qpath.toString(), "fs_devpath")
        if (null == lineFile) {
            Common.common.Error("Couldn't open %s\n", qpath)
            return
        }
        count = 0
        node = outside
        while (node!!.occupied > 1) {

            // find the best portal exit
            next = node.occupied
            p = node.portals
            while (p != null) {
                s = if (p.nodes[0] == node) 1 else 0
                if (p.nodes[s]!!.occupied != 0 && p.nodes[s]!!.occupied < next) {
                    nextPortal = p
                    nextNode = p.nodes[s]!!
                    next = nextNode.occupied
                }
                p = p.next[TempDump.SNOT(s.toDouble())]
            }
            node = nextNode
            mid.set(nextPortal.winding!!.GetCenter())
            lineFile.Printf("%f %f %f\n", mid[0], mid[1], mid[2])
            count++
        }

        // add the origin of the entity from which the leak was found
        lineFile.Printf("%f %f %f\n", leakOrigin[0], leakOrigin[1], leakOrigin[2])
        FileSystem_h.fileSystem.CloseFile(lineFile)
    }

    // try to merge portals
    fun MergePortals(skipContents: Int) {
        numMergedPortals = 0
        Common.common.Printf("[Merge Portals]\n")
        SetPortalPlanes()
        MergePortals_r(root, skipContents)
        Common.common.Printf("%6d portals merged\n", numMergedPortals)
    }

    /*
     ============
     idBrushBSP::TryMergeLeafNodes

     NOTE: multiple brances of the BSP tree might point to the same leaf node after merging
     // try to merge the two leaf nodes at either side of the portal
     ============
     */
    fun TryMergeLeafNodes(portal: idBrushBSPPortal, side: Int): Boolean {
        var i: Int
        var j: Int
        var k: Int
        var s1: Int
        var s2: Int
        var s: Int
        val node1: idBrushBSPNode
        val node2: idBrushBSPNode
        var p1: idBrushBSPPortal?
        var p2: idBrushBSPPortal?
        var p: idBrushBSPPortal?
        var nextp: idBrushBSPPortal?
        var plane: idPlane
        var w: idWinding
        val bounds = idBounds()
        val b = idBounds()
        node1 = portal.nodes[side]!!
        node2 = portal.nodes[TempDump.SNOT(side.toDouble())]!!
        val nodes = arrayOf(node1, node2)


        // check if the merged node would still be convex
        i = 0
        while (i < 2) {
            j =  /*!i*/1 xor i
            p1 = nodes[i].portals
            while (p1 != null) {
                s1 = if (p1.nodes[1] == nodes[i]) 1 else 0
                if (p1.nodes[TempDump.SNOT(s1.toDouble())] == nodes[j]) {
                    p1 = p1.next[s1]
                    continue
                }
                plane = if (s1 != 0) {
                    p1.plane.unaryMinus()
                } else {
                    p1.plane
                }

                // all the non seperating portals of the other node should be at the front or on the plane
                p2 = nodes[j].portals
                while (p2 != null) {
                    s2 = if (p2.nodes[1] == nodes[j]) 1 else 0
                    if (p2.nodes[TempDump.SNOT(s2.toDouble())] == nodes[i]) {
                        p2 = p2.next[s2]
                        continue
                    }
                    w = p2.winding!!
                    k = 0
                    while (k < w.GetNumPoints()) {
                        if (plane.Distance(w[k].ToVec3()) < -0.1f) {
                            return false
                        }
                        k++
                    }
                    p2 = p2.next[s2]
                }
                p1 = p1.next[s1]
            }
            i++
        }

        // remove all portals that seperate the two nodes
        p = node1.portals
        while (p != null) {
            s = if (p.nodes[1] == node1) 1 else 0
            nextp = p.next[s]
            if (p.nodes[TempDump.SNOT(s.toDouble())] == node2) {
                p.RemoveFromNode(p.nodes[0]!!)
                p.RemoveFromNode(p.nodes[1]!!)
                //			delete p;
            }
            p = nextp
        }

        // move all portals of node2 to node1
        p = node2.portals
        while (p != null) {
            s = if (p.nodes[1] == node2) 1 else 0
            nodes[s] = node1
            nodes[TempDump.SNOT(s.toDouble())] = p.nodes[TempDump.SNOT(s.toDouble())]!!
            p.RemoveFromNode(p.nodes[0]!!)
            p.RemoveFromNode(p.nodes[1]!!)
            p.AddToNodes(nodes[0], nodes[1])
            p = node2.portals
        }

        // get bounds for the new node
        bounds.Clear()
        p = node1.portals
        while (p != null) {
            s = if (p.nodes[1] == node1) 1 else 0
            p.GetWinding().GetBounds(b)
            bounds.timesAssign(b)
            p = p.next[s]
        }

        // replace every reference to node2 by a reference to node1
        UpdateTreeAfterMerge_r(root, bounds, node2, node1)

//	delete node2;
        return true
    }

    fun PruneMergedTree_r(node: idBrushBSPNode?) {
        var i: Int
        var leafNode: idBrushBSPNode?
        if (node == null) {
            return
        }
        PruneMergedTree_r(node.children[0])
        PruneMergedTree_r(node.children[1])
        i = 0
        while (i < 2) {
            if (node.children[i] != null) {
                leafNode = node.children[i]!!.children[0]
                if (leafNode != null && leafNode == node.children[i]!!.children[1]) {
                    if (leafNode.parent == node.children[i]) {
                        leafNode.parent = node
                    }
                    //				delete node.children[i];
                    node.children[i] = leafNode
                }
            }
            i++
        }
    }

    // melt portal windings
    fun MeltPortals(skipContents: Int) {
        val vertexList: idVectorSet<idVec3> = idVectorSet(3)
        numInsertedPoints = 0
        Common.common.Printf("[Melt Portals]\n")
        RemoveColinearPoints_r(root, skipContents)
        MeltPortals_r(root, skipContents, vertexList)
        root.RemoveFlagRecurse(NODE_DONE)
        Common.common.Printf("\r%6d points inserted\n", numInsertedPoints)
    }

    // write a map file with a brush for every leaf node that has the given contents
    fun WriteBrushMap(fileName: idStr, ext: idStr, contents: Int) {
        brushMap = idBrushMap(fileName, ext)
        brushMapContents = contents
    }

    // bounds for the whole tree
    fun GetTreeBounds(): idBounds {
        return treeBounds
    }

    // root node of the tree
    fun GetRootNode(): idBrushBSPNode {
        return root
    }

    private fun RemoveMultipleLeafNodeReferences_r(node: idBrushBSPNode?) {
        if (node == null) {
            return
        }
        if (node.children[0] != null) {
            if (node.children[0]!!.parent != node) {
                node.children[0] = null
            } else {
                RemoveMultipleLeafNodeReferences_r(node.children[0])
            }
        }
        if (node.children[1] != null) {
            if (node.children[1]!!.parent !== node) {
                node.children[1] = null
            } else {
                RemoveMultipleLeafNodeReferences_r(node.children[1])
            }
        }
    }

    private fun Free_r(node: idBrushBSPNode?) {
        if (node == null) {
            return
        }
        Free_r(node.children[0])
        Free_r(node.children[1])

//	delete node;
    }

    private fun IsValidSplitter(side: idBrushSide): Boolean {
        return (side.GetFlags() and (Brush.SFL_SPLIT or Brush.SFL_USED_SPLITTER)) == 0
    }

    //
    //        private void IncreaseNumSplits();
    //
    private fun BrushSplitterStats(
        brush: idBrush,
        planeNum: Int,
        planeList: idPlaneSet,
        testedPlanes: BooleanArray,
        stats: splitterStats_s
    ): Int {
        var i: Int
        var j: Int
        var num: Int
        val s: Int
        val lastNumSplits: Int
        val plane: idPlane
        var w: idWinding?
        var d: Float
        var d_front: Float
        var d_back: Float
        var brush_front: Float
        var brush_back: Float
        plane = planeList[planeNum]

        // get the plane side for the brush bounds
        s = brush.GetBounds().PlaneSide(plane, SPLITTER_EPSILON)
        if (s == PLANESIDE_FRONT) {
            stats.numFront++
            return Brush.BRUSH_PLANESIDE_FRONT
        }
        if (s == PLANESIDE_BACK) {
            stats.numBack++
            return Brush.BRUSH_PLANESIDE_BACK
        }

        // if the brush actually uses the planenum, we can tell the side for sure
        i = 0
        while (i < brush.GetNumSides()) {
            num = brush.GetSide(i).GetPlaneNum()
            if (0 == num xor planeNum shr 1) {
                if (num == planeNum) {
                    stats.numBack++
                    stats.numFacing++
                    return Brush.BRUSH_PLANESIDE_BACK or Brush.BRUSH_PLANESIDE_FACING
                }
                if (num == planeNum xor 1) {
                    stats.numFront++
                    stats.numFacing++
                    return Brush.BRUSH_PLANESIDE_FRONT or Brush.BRUSH_PLANESIDE_FACING
                }
            }
            i++
        }
        lastNumSplits = stats.numSplits
        brush_back = 0.0f
        brush_front = brush_back
        i = 0
        while (i < brush.GetNumSides()) {
            if (!IsValidSplitter(brush.GetSide(i))) {
                i++
                continue
            }
            j = brush.GetSide(i).GetPlaneNum()
            if (testedPlanes[j] || testedPlanes[j xor 1]) {
                i++
                continue
            }
            w = brush.GetSide(i).GetWinding()
            if (w == null) {
                i++
                continue
            }
            d_back = 0.0f
            d_front = d_back
            j = 0
            while (j < w.GetNumPoints()) {
                d = plane.Distance(w[j].ToVec3())
                if (d > d_front) {
                    d_front = d
                } else if (d < d_back) {
                    d_back = d
                }
                j++
            }
            if (d_front > SPLITTER_EPSILON && d_back < -SPLITTER_EPSILON) {
                stats.numSplits++
            }
            if (d_front > brush_front) {
                brush_front = d_front
            } else if (d_back < brush_back) {
                brush_back = d_back
            }
            i++
        }

        // if brush sides are split and the brush only pokes one unit through the plane
        if (stats.numSplits > lastNumSplits && (brush_front < 1.0f || brush_back > -1.0f)) {
            stats.epsilonBrushes++
        }
        return Brush.BRUSH_PLANESIDE_BOTH
    }

    private fun FindSplitter(
        node: idBrushBSPNode,
        planeList: idPlaneSet,
        testedPlanes: BooleanArray,
        bestStats: Array<splitterStats_s>
    ): Int {
        var i: Int
        var planeNum: Int
        var bestSplitter: Int
        var value: Int
        var bestValue: Int
        var f: Int
        var numBrushSides: Int
        var brush: idBrush?
        var b: idBrush?
        var stats: splitterStats_s
        Arrays.fill(testedPlanes, false) //	memset( testedPlanes, 0, planeList.Num() * sizeof( bool ) );
        bestSplitter = -1
        bestValue = -99999999
        brush = node.brushList.Head()
        while (brush != null) {
            if (brush.GetFlags() and Brush.BFL_NO_VALID_SPLITTERS != 0) {
                brush = brush.Next()
                continue
            }
            i = 0
            while (i < brush.GetNumSides()) {
                if (!IsValidSplitter(brush.GetSide(i))) {
                    i++
                    continue
                }
                planeNum = brush.GetSide(i).GetPlaneNum()
                if (testedPlanes[planeNum] || testedPlanes[planeNum xor 1]) {
                    i++
                    continue
                }
                testedPlanes[planeNum xor 1] = true
                testedPlanes[planeNum] = testedPlanes[planeNum xor 1]
                if (node.volume!!.Split(planeList[planeNum], planeNum, null, null) != PLANESIDE_CROSS) {
                    i++
                    continue
                }
                stats = splitterStats_s() //memset( &stats, 0, sizeof( stats ) );
                f = if (brush.GetSide(i).GetPlane().Type() < PLANETYPE_TRUEAXIAL) 15 + 5 else 0
                numBrushSides = node.brushList.NumSides()
                b = node.brushList.Head()
                while (b != null) {


                    // if the brush has no valid splitters left
                    if (b.GetFlags() and Brush.BFL_NO_VALID_SPLITTERS != 0) {
                        b.SetPlaneSide(Brush.BRUSH_PLANESIDE_BOTH)
                    } else {
                        b.SetPlaneSide(BrushSplitterStats(b, planeNum, planeList, testedPlanes, stats))
                    }
                    numBrushSides -= b.GetNumSides()
                    // best value we can get using this plane as a splitter
                    value =
                        f * (stats.numFacing + numBrushSides) - 10 * stats.numSplits - stats.epsilonBrushes * 1000
                    // if the best value for this plane can't get any better than the best value we have
                    if (value < bestValue) {
                        break
                    }
                    b = b.Next()
                }
                if (b != null) {
                    i++
                    continue
                }
                value =
                    f * stats.numFacing - 10 * stats.numSplits - abs(stats.numFront - stats.numBack) - stats.epsilonBrushes * 1000
                if (value > bestValue) {
                    bestValue = value
                    bestSplitter = planeNum
                    bestStats[0] = stats
                    b = node.brushList.Head()
                    while (b != null) {
                        b.SavePlaneSide()
                        b = b.Next()
                    }
                }
                i++
            }
            brush = brush.Next()
        }
        return bestSplitter
    }

    private fun SetSplitterUsed(node: idBrushBSPNode, planeNum: Int) {
        var i: Int
        var numValidBrushSplitters: Int
        var brush: idBrush?
        brush = node.brushList.Head()
        while (brush != null) {
            if (0 == brush.GetSavedPlaneSide() and Brush.BRUSH_PLANESIDE_FACING) {
                brush = brush.Next()
                continue
            }
            numValidBrushSplitters = 0
            i = 0
            while (i < brush.GetNumSides()) {
                if (0 == brush.GetSide(i).GetPlaneNum() xor planeNum shr 1) {
                    brush.GetSide(i).SetFlag(Brush.SFL_USED_SPLITTER)
                } else if (IsValidSplitter(brush.GetSide(i))) {
                    numValidBrushSplitters++
                }
                i++
            }
            if (numValidBrushSplitters == 0) {
                brush.SetFlag(Brush.BFL_NO_VALID_SPLITTERS)
            }
            brush = brush.Next()
        }
    }

    private fun BuildBrushBSP_r(
        node: idBrushBSPNode,
        planeList: idPlaneSet,
        testedPlanes: BooleanArray,
        skipContents: Int
    ): idBrushBSPNode {
        val planeNum: Int
        val bestStats = arrayOf(splitterStats_s())
        planeNum = FindSplitter(node, planeList, testedPlanes, bestStats)

        // if no split plane found this is a leaf node
        if (planeNum == -1) {
            node.SetContentsFromBrushes()
            if (brushMap != null && node.contents and brushMapContents != 0) {
                brushMap!!.WriteBrush(node.volume!!)
            }

            // free node memory
            node.brushList.Free()
            node.volume = null //delete node.volume;
            node.children[1] = null
            node.children[0] = node.children[1]
            return node
        }
        numSplits++
        numGridCellSplits++

        // mark all brush sides on the split plane as used
        SetSplitterUsed(node, planeNum)

        // set node split plane
        node.plane.set(planeList[planeNum])

        // allocate children
        node.children[0] = idBrushBSPNode()
        node.children[1] = idBrushBSPNode()

        // split node volume and brush list for children
        node.volume!!.Split(node.plane, -1, node.children[0]!!.volume, node.children[1]!!.volume)
        node.brushList.Split(node.plane, -1, node.children[0]!!.brushList, node.children[1]!!.brushList, true)
        node.children[1]!!.parent = node
        node.children[0]!!.parent = node.children[1]!!.parent

        // free node memory
        node.brushList.Free()
        node.volume = null //delete node.volume;

        // process children
        node.children[0] = BuildBrushBSP_r(node.children[0]!!, planeList, testedPlanes, skipContents)
        node.children[1] = BuildBrushBSP_r(node.children[1]!!, planeList, testedPlanes, skipContents)

        // if both children contain the skip contents
        if (node.children[0]!!.contents and node.children[1]!!.contents and skipContents != 0) {
            node.contents = node.children[0]!!.contents or node.children[1]!!.contents
            node.children[1] = null
            node.children[0] = node.children[1] //delete node.children[0];delete node.children[1];
            numSplits--
            numGridCellSplits--
        }
        return node
    }

    private fun ProcessGridCell(node: idBrushBSPNode, skipContents: Int): idBrushBSPNode {
        val planeList = idPlaneSet()
        val testedPlanes: BooleanArray
        if (OUPUT_BSP_STATS_PER_GRID_CELL) {
            Common.common.Printf("[Grid Cell %d]\n", ++numGridCells)
            Common.common.Printf("%6d brushes\n", node.brushList.Num())
        }
        numGridCellSplits = 0

        // chop away all brush overlap
        node.brushList.Chop(BrushChopAllowed)

        // merge brushes if possible
        //node->brushList.Merge( BrushMergeAllowed );
        // create a list with planes for this grid cell
        node.brushList.CreatePlaneList(planeList)
        if (OUPUT_BSP_STATS_PER_GRID_CELL) {
            Common.common.Printf("[Grid Cell BSP]\n")
        }
        testedPlanes = BooleanArray(planeList.Num())
        BuildBrushBSP_r(node, planeList, testedPlanes, skipContents)

//            testedPlanes = null;//delete testedPlanes;
        if (OUPUT_BSP_STATS_PER_GRID_CELL) {
            Common.common.Printf("\r%6d splits\n", numGridCellSplits)
        }
        return node
    }

    private fun BuildGrid_r(gridCells: idList<idBrushBSPNode>, node: idBrushBSPNode) {
        var axis: Int
        var dist = 0.0f
        val bounds: idBounds
        val normal = idVec3()
        val halfSize = idVec3()
        if (0 == node.brushList.Num()) {
//		delete node.volume;
            node.volume = null
            node.children[1] = null
            node.children[0] = node.children[1]
            return
        }
        bounds = node.volume!!.GetBounds()
        halfSize.set(bounds[1].minus(bounds[0]).times(0.5f))
        axis = 0
        while (axis < 3) {
            dist = if (halfSize[axis] > BSP_GRID_SIZE) {
                (BSP_GRID_SIZE * (floor(
                    ((bounds[0, axis] + halfSize[axis]) / BSP_GRID_SIZE)
                ) + 1))
            } else {
                (BSP_GRID_SIZE * (floor(
                    (bounds[0, axis] / BSP_GRID_SIZE)
                ) + 1))
            }
            if (dist > bounds[0, axis] + 1.0f && dist < bounds[1, axis] - 1.0f) {
                break
            }
            axis++
        }
        if (axis >= 3) {
            gridCells.Append(node)
            return
        }
        numSplits++
        normal.set(getVec3Origin())
        normal[axis] = 1.0f
        node.plane.SetNormal(normal)
        node.plane.SetDist(dist)

        // allocate children
        node.children[0] = idBrushBSPNode()
        node.children[1] = idBrushBSPNode()

        // split volume and brush list for children
        node.volume!!.Split(node.plane, -1, node.children[0]!!.volume, node.children[1]!!.volume)
        node.brushList.Split(node.plane, -1, node.children[0]!!.brushList, node.children[1]!!.brushList)
        node.children[0]!!.brushList.SetFlagOnFacingBrushSides(node.plane, Brush.SFL_USED_SPLITTER)
        node.children[1]!!.brushList.SetFlagOnFacingBrushSides(node.plane, Brush.SFL_USED_SPLITTER)
        node.children[1]!!.parent = node
        node.children[0]!!.parent = node.children[1]!!.parent

        // free node memory
        node.brushList.Free()
        //	delete node.volume;
        node.volume = null

        // process children
        BuildGrid_r(gridCells, node.children[0]!!)
        BuildGrid_r(gridCells, node.children[1]!!)
    }

    private fun PruneTree_r(node: idBrushBSPNode, contents: Int) {
        var i: Int
        var s: Int
        val nodes = arrayOfNulls<idBrushBSPNode?>(2)
        var p: idBrushBSPPortal?
        var nextp: idBrushBSPPortal?
        if (node.children[0] == null || node.children[1] == null) {
            return
        }
        PruneTree_r(node.children[0]!!, contents)
        PruneTree_r(node.children[1]!!, contents)
        if (node.children[0]!!.contents and node.children[1]!!.contents and contents != 0) {
            node.contents = node.children[0]!!.contents or node.children[1]!!.contents
            // move all child portals to parent
            i = 0
            while (i < 2) {
                p = node.children[i]!!.portals
                while (p != null) {
                    s = if (p.nodes[1] == node.children[i]) 1 else 0
                    nextp = p.next[s]
                    nodes[s] = node
                    nodes[TempDump.SNOT(s.toDouble())] = p.nodes[TempDump.SNOT(s.toDouble())]
                    p.RemoveFromNode(p.nodes[0]!!)
                    p.RemoveFromNode(p.nodes[1]!!)
                    if (nodes[TempDump.SNOT(s.toDouble())] == node.children[TempDump.SNOT(i.toDouble())]) {
//					delete p;	// portal seperates both children
//                            p = null;
                    } else {
                        p.AddToNodes(nodes[0]!!, nodes[1]!!)
                    }
                    p = nextp
                }
                i++
            }

//		delete node.children[0];
//		delete node.children[1];
            node.children[1] = null
            node.children[0] = node.children[1]
            numPrunedSplits++
        }
    }

    private fun MakeOutsidePortals() {
        var i: Int
        var j: Int
        var n: Int
        val bounds: idBounds
        var p: idBrushBSPPortal
        val portals = arrayOfNulls<idBrushBSPPortal?>(6)
        val normal = idVec3()
        //            idPlane[] planes = new idPlane[6];

        // pad with some space so there will never be null volume leaves
        bounds = treeBounds.Expand(32.0f)
        i = 0
        while (i < 3) {
            if (bounds[0, i] > bounds[1, i]) {
                Common.common.Error("empty BSP tree")
            }
            i++
        }
        outside = idBrushBSPNode()
        outside.children[1] = null
        outside.children[0] = outside.children[1]
        outside.parent = outside.children[0]
        outside.brushList.Clear()
        outside.portals = null
        outside.contents = 0
        i = 0
        while (i < 3) {
            j = 0
            while (j < 2) {
                p = idBrushBSPPortal()
                normal.set(getVec3Origin())
                normal.set(i, if (j != 0) -1.0f else 1.0f)
                p.plane.SetNormal(normal)
                p.plane.SetDist(if (j != 0) -bounds[j, i] else bounds[j, i])
                p.winding = idWinding(p.plane.Normal(), p.plane.Dist())
                p.AddToNodes(root, outside)
                n = j * 3 + i
                portals[n] = p
                j++
            }
            i++
        }

        // clip the base windings with all the other planes
        i = 0
        while (i < 6) {
            j = 0
            while (j < 6) {
                if (j == i) {
                    j++
                    continue
                }
                portals[i]!!.winding = portals[i]!!.winding!!.Clip(portals[j]!!.plane, ON_EPSILON)
                j++
            }
            i++
        }
    }

    private fun BaseWindingForNode(node: idBrushBSPNode): idWinding? {
        var node = node
        var w: idWinding?
        var n: idBrushBSPNode?
        w = idWinding(node.plane.Normal(), node.plane.Dist())

        // clip by all the parents
        n = node.parent
        while (n != null && w != null) {
            w = if (n.children[0] == node) {
                // take front
                w.Clip(n.plane, BASE_WINDING_EPSILON)
            } else {
                // take back
                w.Clip(n.plane.unaryMinus(), BASE_WINDING_EPSILON)
            }
            node = n
            n = n.parent
        }
        return w
    }

    /*
     ============
     idBrushBSP::MakeNodePortal

     create the new portal by taking the full plane winding for the cutting
     plane and clipping it by all of parents of this node
     ============
     */
    private fun MakeNodePortal(node: idBrushBSPNode) {
        val newPortal: idBrushBSPPortal
        var p: idBrushBSPPortal?
        var w: idWinding?
        var side = 0
        w = BaseWindingForNode(node)

        // clip the portal by all the other portals in the node
        p = node.portals
        while (p != null && w != null) {
            if (p.nodes[0] === node) {
                side = 0
                w = w.Clip(p.plane, 0.1f)
            } else if (p.nodes[1] === node) {
                side = 1
                w = w.Clip(p.plane.unaryMinus(), 0.1f)
            } else {
                Common.common.Error("MakeNodePortal: mislinked portal")
            }
            p = p.next[side]
        }
        if (w == null) {
            return
        }
        if (w.IsTiny()) {
//		delete w;
            return
        }
        newPortal = idBrushBSPPortal()
        newPortal.plane.set(node.plane)
        newPortal.winding = w
        newPortal.AddToNodes(node.children[0]!!, node.children[1]!!)
    }

    /*
     ============
     idBrushBSP::SplitNodePortals

     Move or split the portals that bound the node so that the node's children have portals instead of node.
     ============
     */
    private fun SplitNodePortals(node: idBrushBSPNode) {
        var side = 0
        var p: idBrushBSPPortal?
        var nextPortal: idBrushBSPPortal?
        var newPortal: idBrushBSPPortal?
        val f: idBrushBSPNode?
        val b: idBrushBSPNode?
        var otherNode: idBrushBSPNode?
        var frontWinding: idWinding? = idWinding()
        var backWinding: idWinding? = idWinding()
        val plane = node.plane
        f = node.children[0]
        b = node.children[1]
        p = node.portals
        while (p != null) {
            if (p.nodes[0] == node) {
                side = 0
            } else if (p.nodes[1] == node) {
                side = 1
            } else {
                Common.common.Error("idBrushBSP::SplitNodePortals: mislinked portal")
            }
            nextPortal = p.next[side]
            otherNode = p.nodes[TempDump.SNOT(side.toDouble())]
            p.RemoveFromNode(p.nodes[0]!!)
            p.RemoveFromNode(p.nodes[1]!!)

            // cut the portal into two portals, one on each side of the cut plane
            p.winding!!.Split(plane, SPLIT_WINDING_EPSILON, frontWinding!!, backWinding!!)
            if (!frontWinding.isNULL() && frontWinding.IsTiny()) {
//			delete frontWinding;
                frontWinding = null
                //tinyportals++;
            }
            if (!backWinding.isNULL() && backWinding.IsTiny()) {
//			delete backWinding;
                backWinding = null
                //tinyportals++;
            }
            if (frontWinding == null && backWinding == null) {
                // tiny windings on both sides
                p = nextPortal
                continue
            }
            if (frontWinding == null) {
//			delete backWinding;
                if (side == 0) {
                    p.AddToNodes(b!!, otherNode!!)
                } else {
                    p.AddToNodes(otherNode!!, b!!)
                }
                p = nextPortal
                continue
            }
            if (backWinding == null) {
//			delete frontWinding;
                if (side == 0) {
                    p.AddToNodes(f!!, otherNode!!)
                } else {
                    p.AddToNodes(otherNode!!, f!!)
                }
                p = nextPortal
                continue
            }

            // the winding is split
//		newPortal = new idBrushBSPPortal();
            newPortal = p
            newPortal.winding = backWinding
            //		delete p.winding;
            p.winding = frontWinding
            if (side == 0) {
                p.AddToNodes(f!!, otherNode!!)
                newPortal.AddToNodes(b!!, otherNode)
            } else {
                p.AddToNodes(otherNode!!, f!!)
                newPortal.AddToNodes(otherNode, b!!)
            }
            p = nextPortal
        }
        node.portals = null
    }

    private fun MakeTreePortals_r(node: idBrushBSPNode) {
        var i: Int
        val bounds: idBounds
        numPortals++
        Brush.DisplayRealTimeString("\r%6d", numPortals)
        bounds = node.GetPortalBounds()

//	if ( bounds[0][0] >= bounds[1][0] ) {
//		//common.Warning( "node without volume" );
//	}
        i = 0
        while (i < 3) {
            if (bounds[0, i] < MIN_WORLD_COORD || bounds[1, i] > MAX_WORLD_COORD
            ) {
                Common.common.Warning("node with unbounded volume")
                break
            }
            i++
        }
        if (node.children[0] == null || node.children[1] == null) {
            return
        }
        MakeNodePortal(node)
        SplitNodePortals(node)
        MakeTreePortals_r(node.children[0]!!)
        MakeTreePortals_r(node.children[1]!!)
    }

    private fun FloodThroughPortals_r(node: idBrushBSPNode?, contents: Int, depth: Int) {
        var p: idBrushBSPPortal?
        var s: Int
        if (node == null) {
            Common.common.Error("FloodThroughPortals_r: NULL node\n")
            return
        }
        if (node.occupied != 0) {
            Common.common.Error("FloodThroughPortals_r: node already occupied\n")
        }
        node.occupied = depth
        p = node.portals
        while (p != null) {
            s = if (p.nodes[1] == node) 1 else 0

            // if the node at the other side of the portal is removed
            if (p.nodes[TempDump.SNOT(s.toDouble())] == null) {
                p = p.next[s]
                continue
            }

            // if the node at the other side of the portal is occupied already
            if (p.nodes[TempDump.SNOT(s.toDouble())]!!.occupied != 0) {
                p = p.next[s]
                continue
            }

            // can't flood through the portal if it has the seperating contents at the other side
            if (p.nodes[TempDump.SNOT(s.toDouble())]!!.contents and contents != 0) {
                p = p.next[s]
                continue
            }

            // flood recursively through the current portal
            FloodThroughPortals_r(p.nodes[TempDump.SNOT(s.toDouble())], contents, depth + 1)
            p = p.next[s]
        }
    }

    private fun FloodFromOrigin(origin: idVec3, contents: Int): Boolean {
        var node: idBrushBSPNode?

        //find the leaf to start in
        node = root
        while (node!!.children[0] != null && node.children[1] != null) {
            node = if (node.plane.Side(origin) == PLANESIDE_BACK) {
                node.children[1]
            } else {
                node.children[0]
            }
        }
        if (node == null) {
            return false
        }

        // if inside the inside/outside seperating contents
        if (node.contents and contents != 0) {
            return false
        }

        // if the node is already occupied
        if (node.occupied != 0) {
            return false
        }
        FloodThroughPortals_r(node, contents, 1)
        return true
    }

    /*
     ============
     idBrushBSP::FloodFromEntities

     Marks all nodes that can be reached by entites.
     ============
     */
    private fun FloodFromEntities(mapFile: idMapFile, contents: Int, classNames: idStrList): Boolean {
        var i: Int
        var j: Int
        var inside: Boolean
        val origin = idVec3()
        var mapEnt: idMapEntity
        val classname = idStr()
        inside = false
        outside.occupied = 0

        // skip the first entity which is assumed to be the worldspawn
        i = 1
        while (i < mapFile.GetNumEntities()) {
            mapEnt = mapFile.GetEntity(i)
            if (!mapEnt.epairs.GetVector("origin", "", origin)) {
                i++
                continue
            }
            if (!mapEnt.epairs.GetString("classname", "", classname)) {
                i++
                continue
            }
            j = 0
            while (j < classNames.size()) {
                if (classname.Icmp(classNames[j]) == 0) {
                    break
                }
                j++
            }
            if (j >= classNames.size()) {
                i++
                continue
            }
            origin.plusAssign(2, 1.0f)

            // nudge around a little
            if (FloodFromOrigin(origin, contents)) {
                inside = true
            }
            if (outside.occupied != 0) {
                leakOrigin.set(origin)
                break
            }
            i++
        }
        if (!inside) {
            Common.common.Warning("no entities inside")
        } else if (outside.occupied != 0) {
            Common.common.Warning("reached outside from entity %d (%s)", i, classname)
        }
        return inside && 0 == outside.occupied
    }

    private fun RemoveOutside_r(node: idBrushBSPNode?, contents: Int) {
        if (node == null) {
            return
        }
        if (node.children[0] != null || node.children[1] != null) {
            RemoveOutside_r(node.children[0], contents)
            RemoveOutside_r(node.children[1], contents)
            return
        }
        if (0 == node.occupied) {
            if (0 == node.contents and contents) {
                outsideLeafNodes++
                node.contents = node.contents or contents
            } else {
                solidLeafNodes++
            }
        } else {
            insideLeafNodes++
        }
    }

    private fun SetPortalPlanes_r(node: idBrushBSPNode?, planeList: idPlaneSet) {
        var s: Int
        var p: idBrushBSPPortal?
        if (node == null) {
            return
        }
        p = node.portals
        while (p != null) {
            s = if (p.nodes[1] == node) 1 else 0
            if (p.planeNum == -1) {
                p.planeNum = planeList.FindPlane(
                    p.plane,
                    PORTAL_PLANE_NORMAL_EPSILON,
                    PORTAL_PLANE_DIST_EPSILON
                )
            }
            p = p.next[s]
        }
        SetPortalPlanes_r(node.children[0], planeList)
        SetPortalPlanes_r(node.children[1], planeList)
    }

    /*
     ============
     idBrushBSP::SetPortalPlanes

     give all portals a plane number
     ============
     */
    private fun SetPortalPlanes() {
        SetPortalPlanes_r(root, portalPlanes)
    }

    private fun MergePortals_r(node: idBrushBSPNode?, skipContents: Int) {
        if (node == null) {
            return
        }
        if (node.contents and skipContents != 0) {
            return
        }
        if (node.children[0] == null && node.children[1] == null) {
            MergeLeafNodePortals(node, skipContents)
            return
        }
        MergePortals_r(node.children[0], skipContents)
        MergePortals_r(node.children[1], skipContents)
    }

    private fun MergeLeafNodePortals(node: idBrushBSPNode, skipContents: Int) {
        var s1: Int
        var s2: Int
        var foundPortal: Boolean
        var p1: idBrushBSPPortal?
        var p2: idBrushBSPPortal?
        var nextp1: idBrushBSPPortal?
        var nextp2: idBrushBSPPortal?
        var newWinding: idWinding?
        var reverse: idWinding?

        // pass 1: merge all portals that seperate the same leaf nodes
        p1 = node.GetPortals()
        while (p1 != null) {
            s1 = if (p1.GetNode(1) == node) 1 else 0
            nextp1 = p1.Next(s1)
            p2 = nextp1
            while (p2 != null) {
                s2 = if (p2.GetNode(1) == node) 1 else 0
                nextp2 = p2.Next(s2)

                // if both portals seperate the same leaf nodes
                if (p1.nodes[TempDump.SNOT(s1.toDouble())] == p2.nodes[TempDump.SNOT(s2.toDouble())]) {

                    // add the winding of p2 to the winding of p1
                    p1.winding!!.AddToConvexHull(p2.winding, p1.plane.Normal())

                    // delete p2
                    p2.RemoveFromNode(p2.nodes[0]!!)
                    p2.RemoveFromNode(p2.nodes[1]!!)
                    //				delete p2;
                    numMergedPortals++
                    nextp1 = node.GetPortals()
                    break
                }
                p2 = nextp2
            }
            p1 = nextp1
        }

        // pass 2: merge all portals in the same plane if they all have the skip contents at the other side
        p1 = node.GetPortals()
        while (p1 != null) {
            s1 = if (p1.GetNode(1) == node) 1 else 0
            nextp1 = p1.Next(s1)
            if (0 == p1.nodes[TempDump.SNOT(s1.toDouble())]!!.contents and skipContents) {
                p1 = nextp1
                continue
            }

            // test if all portals in this plane have the skip contents at the other side
            foundPortal = false
            p2 = node.GetPortals()
            while (p2 != null) {
                s2 = if (p2.GetNode(1) == node) 1 else 0
                nextp2 = p2.Next(s2)
                if (p2 === p1 || p2.planeNum and 1.inv() != p1.planeNum and 1.inv()) {
                    p2 = nextp2
                    continue
                }
                foundPortal = true
                if (0 == p2.nodes[TempDump.SNOT(s2.toDouble())]!!.contents and skipContents) {
                    break
                }
                p2 = nextp2
            }

            // if all portals in this plane have the skip contents at the other side
            if (p2 == null && foundPortal) {
                p2 = node.GetPortals()
                while (p2 != null) {
                    s2 = if (p2.GetNode(1) == node) 1 else 0
                    nextp2 = p2.Next(s2)
                    if (p2 === p1 || p2.planeNum and 1.inv() != p1.planeNum and 1.inv()) {
                        p2 = nextp2
                        continue
                    }

                    // add the winding of p2 to the winding of p1
                    p1.winding!!.AddToConvexHull(p2.winding, p1.plane.Normal())

                    // delete p2
                    p2.RemoveFromNode(p2.nodes[0]!!)
                    p2.RemoveFromNode(p2.nodes[1]!!)
                    //				delete p2;
                    numMergedPortals++
                    p2 = nextp2
                }
                nextp1 = node.GetPortals()
            }
            p1 = nextp1
        }

        // pass 3: try to merge portals in the same plane that have the skip contents at the other side
        p1 = node.GetPortals()
        while (p1 != null) {
            s1 = if (p1.GetNode(1) == node) 1 else 0
            nextp1 = p1.Next(s1)
            if (0 == p1.nodes[TempDump.SNOT(s1.toDouble())]!!.contents and skipContents) {
                p1 = nextp1
                continue
            }
            p2 = nextp1
            while (p2 != null) {
                s2 = if (p2.GetNode(1) == node) 1 else 0
                nextp2 = p2.Next(s2)
                if (0 == p2.nodes[TempDump.SNOT(s2.toDouble())]!!.contents and skipContents) {
                    p2 = nextp2
                    continue
                }
                if (p2.planeNum and 1.inv() != p1.planeNum and 1.inv()) {
                    p2 = nextp2
                    continue
                }

                // try to merge the two portal windings
                if (p2.planeNum == p1.planeNum) {
                    newWinding = p1.winding!!.TryMerge(p2.winding!!, p1.plane.Normal())
                } else {
                    reverse = p2.winding!!.Reverse()
                    newWinding = p1.winding!!.TryMerge(reverse, p1.plane.Normal())
                    //				delete reverse;
                }

                // if successfully merged
                if (newWinding != null) {

                    // replace the winding of the first portal
//				delete p1.winding;
                    p1.winding = newWinding

                    // delete p2
                    p2.RemoveFromNode(p2.nodes[0]!!)
                    p2.RemoveFromNode(p2.nodes[1]!!)
                    //				delete p2;
                    numMergedPortals++
                    nextp1 = node.GetPortals()
                    break
                }
                p2 = nextp2
            }
            p1 = nextp1
        }
    }

    private fun UpdateTreeAfterMerge_r(
        node: idBrushBSPNode?,
        bounds: idBounds,
        oldNode: idBrushBSPNode?,
        newNode: idBrushBSPNode?
    ) {
        if (node == null) {
            return
        }
        if (node.children[0] == null && node.children[1] == null) {
            return
        }
        if (node.children[0] == oldNode) {
            node.children[0] = newNode
        }
        if (node.children[1] == oldNode) {
            node.children[1] = newNode
        }
        when (bounds.PlaneSide(node.plane, 2.0f)) {
            PLANESIDE_FRONT -> UpdateTreeAfterMerge_r(node.children[0], bounds, oldNode, newNode)
            PLANESIDE_BACK -> UpdateTreeAfterMerge_r(node.children[1], bounds, oldNode, newNode)
            else -> {
                UpdateTreeAfterMerge_r(node.children[0], bounds, oldNode, newNode)
                UpdateTreeAfterMerge_r(node.children[1], bounds, oldNode, newNode)
            }
        }
    }

    private fun RemoveLeafNodeColinearPoints(node: idBrushBSPNode) {
        var s1: Int
        var p1: idBrushBSPPortal?

        // remove colinear points
        p1 = node.GetPortals()
        while (p1 != null) {
            s1 = if (p1.GetNode(1) == node) 1 else 0
            p1.winding!!.RemoveColinearPoints(p1.plane.Normal(), 0.1f)
            p1 = p1.Next(s1)
        }
    }

    private fun RemoveColinearPoints_r(node: idBrushBSPNode?, skipContents: Int) {
        if (null == node) {
            return
        }
        if (node.contents and skipContents != 0) {
            return
        }
        if (node.children[0] == null && node.children[1] == null) {
            RemoveLeafNodeColinearPoints(node)
            return
        }
        RemoveColinearPoints_r(node.children[0], skipContents)
        RemoveColinearPoints_r(node.children[1], skipContents)
    }

    /*
     ============
     idBrushBSP::MeltFloor_r

     flood through portals touching the bounds to find all vertices that might be inside the bounds
     ============
     */
    private fun MeltFlood_r(
        node: idBrushBSPNode,
        skipContents: Int,
        bounds: idBounds,
        vertexList: idVectorSet<idVec3>
    ) {
        var s1: Int
        var i: Int
        var p1: idBrushBSPPortal?
        val b = idBounds()
        var w: idWinding?
        node.SetFlag(NODE_VISITED)
        p1 = node.GetPortals()
        while (p1 != null) {
            s1 = if (p1.GetNode(1) == node) 1 else 0
            if (p1.GetNode( /*!s1*/TempDump.SNOT(s1.toDouble()))!!.GetFlags() and NODE_VISITED != 0) {
                p1 = p1.Next(s1)
                continue
            }
            w = p1.GetWinding()
            i = 0
            while (i < w.GetNumPoints()) {
                if (bounds.ContainsPoint(w[i].ToVec3())) {
                    vertexList.FindVector(w[i].ToVec3(), VERTEX_MELT_EPSILON)
                }
                i++
            }
            p1 = p1.Next(s1)
        }
        p1 = node.GetPortals()
        while (p1 != null) {
            s1 = if (p1.GetNode(1) == node) 1 else 0
            if (p1.GetNode( /*!s1*/TempDump.SNOT(s1.toDouble()))!!.GetFlags() and NODE_VISITED != 0) {
                p1 = p1.Next(s1)
                continue
            }
            if (p1.GetNode( /*!s1*/TempDump.SNOT(s1.toDouble()))!!.GetContents() and skipContents != 0) {
                p1 = p1.Next(s1)
                continue
            }
            w = p1.GetWinding()
            w.GetBounds(b)
            if (!bounds.IntersectsBounds(b)) {
                p1 = p1.Next(s1)
                continue
            }
            MeltFlood_r(
                p1.GetNode( /*!s1*/TempDump.SNOT(s1.toDouble()))!!, skipContents, bounds, vertexList
            )
            p1 = p1.Next(s1)
        }
    }

    private fun MeltLeafNodePortals(node: idBrushBSPNode, skipContents: Int, vertexList: idVectorSet<idVec3>) {
        var s1: Int
        var i: Int
        var p1: idBrushBSPPortal?
        val bounds = idBounds()
        if (node.GetFlags() and NODE_DONE != 0) {
            return
        }
        node.SetFlag(NODE_DONE)

        // melt things together
        p1 = node.GetPortals()
        while (p1 != null) {
            s1 = if (p1.GetNode(1) == node) 1 else 0
            if (p1.GetNode( /*!s1*/TempDump.SNOT(s1.toDouble()))!!.GetFlags() and NODE_DONE != 0) {
                p1 = p1.Next(s1)
                continue
            }
            p1.winding!!.GetBounds(bounds)
            bounds.ExpandSelf(2 * VERTEX_MELT_HASH_SIZE * VERTEX_MELT_EPSILON)
            vertexList.Init(bounds[0], bounds[1], VERTEX_MELT_HASH_SIZE.toInt(), 128)

            // get all vertices to be considered
            MeltFlood_r(node, skipContents, bounds, vertexList)
            node.RemoveFlagFlood(NODE_VISITED)
            i = 0
            while (i < vertexList.Num()) {
                if (p1.winding!!.InsertPointIfOnEdge(vertexList[i], p1.plane, 0.1f)) {
                    numInsertedPoints++
                }
                i++
            }
            p1 = p1.Next(s1)
        }
        Brush.DisplayRealTimeString("\r%6d", numInsertedPoints)
    }

    private fun MeltPortals_r(node: idBrushBSPNode?, skipContents: Int, vertexList: idVectorSet<idVec3>) {
        if (node == null) {
            return
        }
        if (node.contents and skipContents != 0) {
            return
        }
        if (node.children[0] == null && node.children[1] == null) {
            MeltLeafNodePortals(node, skipContents, vertexList)
            return
        }
        MeltPortals_r(node.children[0], skipContents, vertexList)
        MeltPortals_r(node.children[1], skipContents, vertexList)
    }

    /*
     ============
     idBrushBSP::BrushSplitterStats
     ============
     */
    private class splitterStats_s {
        var epsilonBrushes // number of tiny brushes this splitter would create
                = 0
        var numBack // number of brushes at the back of the splitter
                = 0
        var numFacing // number of brushes facing this splitter
                = 0
        var numFront // number of brushes at the front of the splitter
                = 0
        var numSplits // number of brush sides split by the splitter
                = 0
    }

    init {
        root = outside
        numPrunedSplits = 0
        numSplits = numPrunedSplits
        brushMapContents = 0
        brushMap = null
    }
}

package neo.Tools.Compilers.DMap

import neo.TempDump
import neo.Tools.Compilers.DMap.dmap.node_s
import neo.Tools.Compilers.DMap.dmap.tree_s
import neo.Tools.Compilers.DMap.dmap.uPortal_s
import neo.framework.Common
import neo.framework.FileSystem_h
import neo.idlib.math.idVec3
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Paths
import java.util.logging.Level
import java.util.logging.Logger

object leakfile {
    /*
     ==============================================================================

     LEAF FILE GENERATION

     Save out name.line for qe3 to read
     ==============================================================================
     */
    /*
     =============
     LeakFile

     Finds the shortest possible chain of portals
     that leads from the outside leaf to a specifically
     occupied leaf
     =============
     */
    fun LeakFile(tree: tree_s) {
        val mid = idVec3()
        //        FILE linefile;
        val filename: String?
        val ospath: String?
        var fprintf: ByteArray
        var node: node_s
        var count: Int
        if (tree.outside_node.occupied == 0) {
            return
        }
        Common.common.Printf("--- LeakFile ---\n")

        //
        // write the points to the file
        //
        filename = String.format("%s.lin", dmap.dmapGlobals.mapFileBase)
        ospath = FileSystem_h.fileSystem.RelativePathToOSPath(filename)
        try {
            FileChannel.open(Paths.get(ospath), TempDump.fopenOptions("w")).use { linefile ->
//             linefile = fopen(ospath, "w");
                if (linefile == null) {
                    Common.common.Error("Couldn't open %s\n", filename)
                }
                count = 0
                node = tree.outside_node
                while (node.occupied > 1) {
                    var next: Int
                    var p: uPortal_s?
                    var nextportal: uPortal_s? = uPortal_s()
                    var nextnode: node_s? = node_s()
                    var s: Int

                    // find the best portal exit
                    next = node.occupied
                    p = node.portals
                    while (p != null) {
                        s = if (p.nodes[0] == node) 1 else 0
                        if (p.nodes[s]!!.occupied != 0
                            && p.nodes[s]!!.occupied < next
                        ) {
                            nextportal = p
                            nextnode = p.nodes[s]
                            next = nextnode!!.occupied
                        }
                        p = p.next[1 xor s]
                    }
                    node = nextnode!!
                    mid.set(nextportal!!.winding!!.GetCenter())
                    fprintf = String.format("%f %f %f\n", mid[0], mid[1], mid[2]).toByteArray()
                    linefile.write(ByteBuffer.wrap(fprintf))
                    count++
                }
                // add the occupant center
                node.occupant!!.mapEntity.epairs.GetVector("origin", "", mid)
                fprintf = String.format("%f %f %f\n", mid[0], mid[1], mid[2]).toByteArray()
                linefile.write(ByteBuffer.wrap(fprintf))
                Common.common.Printf("%5d point linefile\n", count + 1)
            }
        } catch (ex: IOException) {
            Logger.getLogger(leakfile::class.java.name).log(Level.SEVERE, null, ex)
        }
    }
}
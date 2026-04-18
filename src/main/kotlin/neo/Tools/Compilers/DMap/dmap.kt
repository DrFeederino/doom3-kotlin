package neo.Tools.Compilers.DMap

import neo.Renderer.Material
import neo.Renderer.Model.srfTriangles_s
import neo.Renderer.idRenderLightLocal
import neo.TempDump
import neo.TempDump.TODO_Exception
import neo.Tools.Compilers.AAS.AASBuild.RunAAS_f
import neo.Tools.Compilers.DMap.optimize.optVertex_s
import neo.Tools.Compilers.DMap.tritjunction.hashVert_s
import neo.cm.collisionModelManager
import neo.framework.CmdSystem
import neo.framework.CmdSystem.cmdExecution_t
import neo.framework.CmdSystem.cmdFunction_t
import neo.framework.Common
import neo.framework.FileSystem_h
import neo.framework.WIN32
import neo.idlib.BV.idBounds
import neo.idlib.CmdArgs
import neo.idlib.MapFile.idMapEntity
import neo.idlib.MapFile.idMapFile
import neo.idlib.Text.Str.idStr
import neo.idlib.containers.List.idList
import neo.idlib.containers.PlaneSet.idPlaneSet
import neo.idlib.geometry.DrawVert.idDrawVert
import neo.idlib.geometry.Winding.idWinding
import neo.idlib.idLib
import neo.idlib.math.idPlane
import neo.idlib.math.idVec3
import neo.idlib.math.idVec4
import neo.sys.win_shared

object dmap {
    //
    const val MAX_GROUP_LIGHTS = 16
    const val MAX_PATCH_SIZE = 32

    //
    const val MAX_QPATH = 256 // max length of a game pathname

    //
    const val PLANENUM_LEAF = -1

    //
    var dmapGlobals: dmapGlobals_t = dmapGlobals_t()

    /*
     ============
     ProcessModel
     ============
     */
    fun ProcessModel(e: uEntity_t, floodFill: Boolean): Boolean {
        val faces: bspface_s?

        // build a bsp tree using all of the sides
        // of all of the structural brushes
        faces = facebsp.MakeStructuralBspFaceList(e.primitives)
        e.tree = facebsp.FaceBSP(faces)

        // create portals at every leaf intersection
        // to allow flood filling
        portals.MakeTreePortals(e.tree)

        // classify the leafs as opaque or areaportal
        ubrush.FilterBrushesIntoTree(e)

        // see if the bsp is completely enclosed
        if (floodFill && !dmapGlobals.noFlood) {
            if (portals.FloodEntities(e.tree)) {
                // set the outside leafs to opaque
                portals.FillOutside(e)
            } else {
                idLib.common.Printf("**********************\n")
                idLib.common.Warning("******* leaked *******")
                idLib.common.Printf("**********************\n")
                leakfile.LeakFile(e.tree)
                // bail out here.  If someone really wants to
                // process a map that leaks, they should use
                // -noFlood
                return false
            }
        }

        // get minimum convex hulls for each visible side
        // this must be done before creating area portals,
        // because the visible hull is used as the portal
        usurface.ClipSidesByTree(e)

        // determine areas before clipping tris into the
        // tree, so tris will never cross area boundaries
        portals.FloodAreas(e)

        // we now have a BSP tree with solid and non-solid leafs marked with areas
        // all primitives will now be clipped into this, throwing away
        // fragments in the solid areas
        usurface.PutPrimitivesInAreas(e)

        // now build shadow volumes for the lights and split
        // the optimize lists by the light beam trees
        // so there won't be unneeded overdraw in the static
        // case
        usurface.Prelight(e)

        // optimizing is a superset of fixing tjunctions
        if (!dmapGlobals.noOptimize) {
            optimize.OptimizeEntity(e)
        } else if (!dmapGlobals.noTJunc) {
            tritjunction.FixEntityTjunctions(e)
        }

        // now fix t junctions across areas
        tritjunction.FixGlobalTjunctions(e)
        return true
    }

    /*
     ============
     ProcessModels
     ============
     */
    fun ProcessModels(): Boolean {
        val oldVerbose: Boolean
        var entity: uEntity_t
        oldVerbose = dmapGlobals.verbose
        dmapGlobals.entityNum = 0
        while (dmapGlobals.entityNum < dmapGlobals.num_entities) {
            entity = dmapGlobals.uEntities[dmapGlobals.entityNum]
            if (entity.primitives == null) {
                dmapGlobals.entityNum++
                continue
            }
            idLib.common.Printf("############### entity %d ###############\n", dmapGlobals.entityNum)

            // if we leaked, stop without any more processing
            if (!ProcessModel(entity, dmapGlobals.entityNum == 0)) {
                return false
            }

            // we usually don't want to see output for submodels unless
            // something strange is going on
            if (!dmapGlobals.verboseentities) {
                dmapGlobals.verbose = false
            }
            dmapGlobals.entityNum++
        }
        dmapGlobals.verbose = oldVerbose
        return true
    }

    /*
     ============
     DmapHelp
     ============
     */
    fun DmapHelp() {
        idLib.common.Printf(
            """
                    Usage: dmap [options] mapfile
                    Options:
                    noCurves          = don't process curves
                    noCM              = don't create collision map
                    noAAS             = don't create AAS files
                    
                    """.trimIndent()
        )
    }

    /*
     ============
     ResetDmapGlobals
     ============
     */
    fun ResetDmapGlobals() {
        dmapGlobals.mapFileBase[0] = '\u0000'
        dmapGlobals.dmapFile = null
        dmapGlobals.mapPlanes.Clear()
        dmapGlobals.num_entities = 0
        dmapGlobals.uEntities = emptyArray()
        dmapGlobals.entityNum = 0
        dmapGlobals.mapLights.Clear()
        dmapGlobals.verbose = false
        dmapGlobals.glview = false
        dmapGlobals.noOptimize = false
        dmapGlobals.verboseentities = false
        dmapGlobals.noCurves = false
        dmapGlobals.fullCarve = false
        dmapGlobals.noModelBrushes = false
        dmapGlobals.noTJunc = false
        dmapGlobals.nomerge = false
        dmapGlobals.noFlood = false
        dmapGlobals.noClipSides = false
        dmapGlobals.noLightCarve = false
        dmapGlobals.noShadow = false
        dmapGlobals.shadowOptLevel = shadowOptLevel_t.SO_NONE
        dmapGlobals.drawBounds.Clear()
        dmapGlobals.drawflag = false
        dmapGlobals.totalShadowTriangles = 0
        dmapGlobals.totalShadowVerts = 0
    }

    /*
     ============
     Dmap
     ============
     */
    fun Dmap(args: CmdArgs.idCmdArgs?) {
        var i: Int
        var start: Int
        var end: Int
        var path: String //= new char[1024];
        var passedName = idStr()
        var leaked = false
        var noCM = false
        var noAAS = false
        ResetDmapGlobals()
        if (args!!.Argc() < 2) {
            DmapHelp()
            return
        }
        idLib.common.Printf("---- dmap ----\n")
        dmapGlobals.fullCarve = true
        dmapGlobals.shadowOptLevel =
            shadowOptLevel_t.SO_MERGE_SURFACES // create shadows by merging all surfaces, but no super optimization
        //	dmapGlobals.shadowOptLevel = SO_CLIP_OCCLUDERS;		// remove occluders that are completely covered
//	dmapGlobals.shadowOptLevel = SO_SIL_OPTIMIZE;
//	dmapGlobals.shadowOptLevel = SO_CULL_OCCLUDED;
        dmapGlobals.noLightCarve = true
        i = 1
        while (i < args.Argc()) {
            var s: String?
            s = args.Argv(i)
            if (s.isNotEmpty() && s.startsWith("-")) {
                s = s.substring(1)
                if (s.isEmpty() || s.startsWith("\u0000")) {
                    i++
                    continue
                }
            }
            if (idStr.Icmp(s, "glview") == 0) {
                dmapGlobals.glview = true
            } else if (idStr.Icmp(s, "v") == 0) {
                idLib.common.Printf("verbose = true\n")
                dmapGlobals.verbose = true
            } else if (idStr.Icmp(s, "draw") == 0) {
                idLib.common.Printf("drawflag = true\n")
                dmapGlobals.drawflag = true
            } else if (idStr.Icmp(s, "noFlood") == 0) {
                idLib.common.Printf("noFlood = true\n")
                dmapGlobals.noFlood = true
            } else if (idStr.Icmp(s, "noLightCarve") == 0) {
                idLib.common.Printf("noLightCarve = true\n")
                dmapGlobals.noLightCarve = true
            } else if (idStr.Icmp(s, "lightCarve") == 0) {
                idLib.common.Printf("noLightCarve = false\n")
                dmapGlobals.noLightCarve = false
            } else if (idStr.Icmp(s, "noOpt") == 0) {
                idLib.common.Printf("noOptimize = true\n")
                dmapGlobals.noOptimize = true
            } else if (idStr.Icmp(s, "verboseentities") == 0) {
                idLib.common.Printf("verboseentities = true\n")
                dmapGlobals.verboseentities = true
            } else if (idStr.Icmp(s, "noCurves") == 0) {
                idLib.common.Printf("noCurves = true\n")
                dmapGlobals.noCurves = true
            } else if (idStr.Icmp(s, "noModels") == 0) {
                idLib.common.Printf("noModels = true\n")
                dmapGlobals.noModelBrushes = true
            } else if (idStr.Icmp(s, "noClipSides") == 0) {
                idLib.common.Printf("noClipSides = true\n")
                dmapGlobals.noClipSides = true
            } else if (idStr.Icmp(s, "noCarve") == 0) {
                idLib.common.Printf("noCarve = true\n")
                dmapGlobals.fullCarve = false
            } else if (idStr.Icmp(s, "shadowOpt") == 0) {
                dmapGlobals.shadowOptLevel = shadowOptLevel_t.values()[TempDump.atoi(args.Argv(i + 1))]
                idLib.common.Printf("shadowOpt = %d\n", dmapGlobals.shadowOptLevel)
                i += 1
            } else if (idStr.Icmp(s, "noTjunc") == 0) {
                // triangle optimization won't work properly without tjunction fixing
                idLib.common.Printf("noTJunc = true\n")
                dmapGlobals.noTJunc = true
                dmapGlobals.noOptimize = true
                idLib.common.Printf("forcing noOptimize = true\n")
            } else if (idStr.Icmp(s, "noCM") == 0) {
                noCM = true
                idLib.common.Printf("noCM = true\n")
            } else if (idStr.Icmp(s, "noAAS") == 0) {
                noAAS = true
                idLib.common.Printf("noAAS = true\n")
            } else if (idStr.Icmp(s, "editorOutput") == 0) {
                if (WIN32) {
                    Common.com_outputMsg = true
                }
            } else {
                break
            }
            i++
        }
        if (i >= args.Argc()) {
            idLib.common.Error("usage: dmap [options] mapfile")
        }
        passedName.set(args.Argv(i)) // may have an extension
        passedName.BackSlashesToSlashes()
        if (passedName.Icmpn("maps/", 4) != 0) {
            passedName.set("maps/$passedName")
        }
        val stripped = passedName
        stripped.StripFileExtension()
        idStr.Copynz(dmapGlobals.mapFileBase, stripped.data, dmapGlobals.mapFileBase.size)
        var region = false
        // if this isn't a regioned map, delete the last saved region map
        if (passedName.Right(4).toString() != ".reg") {
            path = String.format("%s.reg", dmapGlobals.mapFileBase)
            FileSystem_h.fileSystem.RemoveFile(path)
        } else {
            region = true
        }
        passedName = stripped

        // delete any old line leak files
        path = String.format("%s.lin", dmapGlobals.mapFileBase)
        FileSystem_h.fileSystem.RemoveFile(path)

        //
        // start from scratch
        //
        start = win_shared.Sys_Milliseconds()
        if (!map.LoadDMapFile(passedName.toString())) {
            return
        }
        if (ProcessModels()) {
            output.WriteOutputFile()
        } else {
            leaked = true
        }
        map.FreeDMapFile()
        idLib.common.Printf("%d total shadow triangles\n", dmapGlobals.totalShadowTriangles)
        idLib.common.Printf("%d total shadow verts\n", dmapGlobals.totalShadowVerts)
        end = win_shared.Sys_Milliseconds()
        idLib.common.Printf("-----------------------\n")
        idLib.common.Printf("%5.0f seconds for dmap\n", (end - start) * 0.001f)
        if (!leaked) {
            if (!noCM) {

                // make sure the collision model manager is not used by the game
                CmdSystem.cmdSystem.BufferCommandText(cmdExecution_t.CMD_EXEC_NOW, "disconnect")

                // create the collision map
                start = win_shared.Sys_Milliseconds()
                collisionModelManager.LoadMap(dmapGlobals.dmapFile)
                collisionModelManager.FreeMap()
                end = win_shared.Sys_Milliseconds()
                idLib.common.Printf("-------------------------------------\n")
                idLib.common.Printf("%5.0f seconds to create collision map\n", (end - start) * 0.001f)
            }
            if (!noAAS && !region) {
                // create AAS files
                RunAAS_f.getInstance().run(args)
            }
        }

        // free the common .map representation
//        delete dmapGlobals.dmapFile;
        dmapGlobals.dmapFile = null

        // clear the map plane list
        dmapGlobals.mapPlanes.Clear()
        if (WIN32) {
            throw TODO_Exception()
            //            if (com_outputMsg && com_hwndMsg != 0) {
//                long msg = RegisterWindowMessage(DMAP_DONE);
//                PostMessage(com_hwndMsg, msg, 0, 0);
//            }
        }
    }

    // all primitives from the map are added to optimzeGroups, creating new ones as needed
    // each optimizeGroup is then split into the map areas, creating groups in each area
    // each optimizeGroup is then divided by each light, creating more groups
    // the final list of groups is then tjunction fixed against all groups, then optimized internally
    // multiple optimizeGroups will be merged together into .proc surfaces, but no further optimization
    // is done on them
    //=============================================================================
    enum class shadowOptLevel_t {
        SO_NONE,  // 0
        SO_MERGE_SURFACES,  // 1
        SO_CULL_OCCLUDED,  // 2
        SO_CLIP_OCCLUDERS,  // 3
        SO_CLIP_SILS,  // 4
        SO_SIL_OPTIMIZE // 5
    }

    class primitive_s {
        //
        // only one of these will be non-NULL
        var brush: bspbrush_s? = null
        var next: primitive_s? = null
        var tris: mapTri_s? = null
    }

    class uArea_t {
        var groups: optimizeGroup_s? = null // we might want to add other fields later
    }

    class uEntity_t {
        var areas: Array<uArea_t> = emptyArray()
        var mapEntity // points into mapFile_t data
                : idMapEntity = idMapEntity()

        //
        var numAreas = 0

        //
        val origin: idVec3 = idVec3()
        var primitives: primitive_s? = null
        var tree: tree_s = tree_s()
    }

    // chains of mapTri_t are the general unit of processing
    class mapTri_s {
        var hashVert: Array<hashVert_s> = Array(3) { hashVert_s() }
        var material: Material.idMaterial? = null
        var mergeGroup // we want to avoid merging triangles
                : Any? = null
        var next: mapTri_s? = null
        var optVert: Array<optVertex_s> = Array(3) { optVertex_s() }

        // from different fixed groups, like guiSurfs and mirrors
        var planeNum // not set universally, just in some areas
                = 0

        //
        var v: Array<idDrawVert> = Array(3) { idDrawVert() }
        fun clear() {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        fun oSet(next: mapTri_s?) {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }
    }

    internal class mesh_t {
        var verts: idDrawVert? = null
        var width = 0
        var height = 0
    }

    internal class parseMesh_s {
        var material: Material.idMaterial? = null
        var mesh: mesh_t? = null
        var next: parseMesh_s? = null
    }

    class bspface_s {
        // any non-portals
        var checked // used by SelectSplitPlaneNum()
                = false
        var next: bspface_s? = null
        var planenum = 0
        var portal // all portals will be selected before
                = false
        var w: idWinding? = null
        fun clear() {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }
    }

    class textureVectors_t {
        var v: Array<idVec4> =
            idVec4.generateArray(2) // the offset value will always be in the 0.0f to 1.0f range
    }

    class side_s {
        //
        var material: Material.idMaterial? = null
        var planenum = 0
        var texVec: textureVectors_t = textureVectors_t()
        var visibleHull // also clipped to the solid parts of the world
                : idWinding? = null

        //
        var winding // only clipped to the other sides of the brush
                : idWinding? = null
    }

    open class bspbrush_s {
        //
        val bounds: idBounds = idBounds()
        var brushnum // editor numbering for messages
                = 0
        var contentShader // one face's shader will determine the volume attributes
                : Material.idMaterial? = null

        //
        var contents = 0

        //
        var entitynum // editor numbering for messages
                = 0
        var next: bspbrush_s? = null
        var numsides = 0
        var opaque = false
        var original // chopped up brushes will reference the originals
                : bspbrush_s = bspbrush_s()
        var outputNumber // set when the brush is written to the file list
                = 0
        var sides: Array<side_s> = Array(6) { side_s() } // variably sized
    }

    class uBrush_t : bspbrush_s() {
        fun clear() {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        fun set(CopyBrush: uBrush_t) {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }
    }

    internal class drawSurfRef_s {
        var nextRef: drawSurfRef_s? = null
        var outputNumber = 0
    }

    class node_s {
        // both leafs and nodes
        // needed for FindSideForPortal
        //
        var area // determined by flood filling up to areaportals
                = 0
        val bounds // valid after portalization
                : idBounds = idBounds()

        //
        var brushlist // fragments of all brushes in this leaf
                : uBrush_t? = null
        var children: Array<node_s> = Array(2) { node_s() }
        var nodeNumber // set after pruning
                = 0
        var occupant // for leak file testing
                : uEntity_t? = null
        var occupied // 1 or greater can reach entity
                = 0

        //
        // leafs only
        var opaque // view can never be inside
                = false
        var parent: node_s? = null
        var planenum // -1 = leaf node
                = 0

        //
        var portals // also on nodes during construction
                : uPortal_s? = null

        //
        // nodes only
        var side // the side that created the node
                : side_s? = null

        fun clear() {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }
    }

    //=============================================================================
    class uPortal_s {
        var next: Array<uPortal_s?> = arrayOfNulls<uPortal_s?>(2)
        var nodes: Array<node_s?> = arrayOfNulls<node_s?>(2) // [0] = front side of plane
        var onnode // NULL = outside box
                : node_s? = null
        val plane: idPlane = idPlane()
        var winding: idWinding? = null
        fun clear() {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }
    }

    // a tree_t is created by FaceBSP()
    class tree_s {
        val bounds: idBounds = idBounds()
        var headnode: node_s = node_s()
        var outside_node: node_s = node_s()
        fun clear() {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }
    }

    class mapLight_t {
        var def: idRenderLightLocal = idRenderLightLocal()
        var name: CharArray = CharArray(MAX_QPATH) // for naming the shadow volume surface and interactions
        var shadowTris: srfTriangles_s? = null
    }

    class optimizeGroup_s {
        var areaNum = 0
        val axis: Array<idVec3> =
            idVec3.generateArray(2) // orthogonal to the plane, so optimization can be 2D

        //
        val bounds // set in CarveGroupsByLight
                : idBounds = idBounds()
        var groupLights: Array<mapLight_t> =
            Array(MAX_GROUP_LIGHTS) { mapLight_t() } // lights effecting this list
        var material: Material.idMaterial? = null
        var mergeGroup // if this differs (guiSurfs, mirrors, etc), the
                : Any? = null
        var nextGroup: optimizeGroup_s? = null
        var numGroupLights = 0
        var planeNum = 0
        var regeneratedTris // after each island optimization
                : mapTri_s? = null

        //
        // all of these must match to add a triangle to the triList
        var smoothed // curves will never merge with brushes
                = false

        //
        var surfaceEmited = false

        // groups will not be combined into model surfaces
        // after optimization
        var texVec: textureVectors_t = textureVectors_t()

        //
        var triList: mapTri_s? = null
        fun clear() {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        fun oSet(nextGroup: optimizeGroup_s?) {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }
    }

    class dmapGlobals_t {
        // mapFileBase will contain the qpath without any extension: "maps/test_box"
        var dmapFile: idMapFile? = null
        val drawBounds: idBounds = idBounds()
        var drawflag = false
        var entityNum = 0
        var fullCarve = false
        var glview = false
        var mapFileBase: CharArray = CharArray(1024)
        val mapLights: idList<mapLight_t> = idList()
        var mapPlanes: idPlaneSet = idPlaneSet()
        var noClipSides // don't cut sides by solid leafs, use the entire thing
                = false
        var noCurves = false
        var noFlood = false
        var noLightCarve // extra triangle subdivision by light frustums
                = false
        var noModelBrushes = false
        var noOptimize = false
        var noShadow // don't create optimized shadow volumes
                = false
        var noTJunc = false
        var nomerge = false

        //
        var num_entities = 0
        var shadowOptLevel: shadowOptLevel_t = shadowOptLevel_t.SO_NONE

        //
        var totalShadowTriangles = 0
        var totalShadowVerts = 0
        var uEntities: Array<uEntity_t> = emptyArray()

        //
        var verbose = false
        var verboseentities = false
    }

    /*
     ============
     Dmap_f
     ============
     */
    class Dmap_f : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            idLib.common.ClearWarnings("running dmap")

            // refresh the screen each time we print so it doesn't look
            // like it is hung
            idLib.common.SetRefreshOnPrint(true)
            Dmap(args)
            idLib.common.SetRefreshOnPrint(false)
            idLib.common.PrintWarnings()
        }

        companion object {
            private val instance: cmdFunction_t = Dmap_f()
            fun getInstance(): cmdFunction_t {
                return instance
            }
        }
    }
}
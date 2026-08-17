package neo.Game.AI

import neo.framework.Common
import neo.framework.DeclEntityDef.idDeclEntityDef
import neo.framework.DeclManager
import neo.framework.DeclManager.declType_t
import neo.framework.File_h.idFile
import neo.idlib.BIT
import neo.idlib.BV.idBounds
import neo.idlib.Dict_h.idDict
import neo.idlib.Dict_h.idKeyValue
import neo.idlib.Text.Lexer
import neo.idlib.Text.Lexer.idLexer
import neo.idlib.Text.Str.idStr
import neo.idlib.Text.Token
import neo.idlib.Text.Token.idToken
import neo.idlib.containers.CBool
import neo.idlib.containers.CFloat
import neo.idlib.containers.CInt
import neo.idlib.containers.List.idList
import neo.idlib.containers.PlaneSet.idPlaneSet
import neo.idlib.idException
import neo.idlib.math.idPlane
import neo.idlib.math.idVec3
import java.nio.IntBuffer

object AASFile {
    /*
     ===============================================================================

     AAS File

     ===============================================================================
     */
    const val AAS_FILEID: String = "DewmAAS"
    const val AAS_FILEVERSION: String = "1.07"

    //
    // bits for different bboxes
    const val AREACONTENTS_BBOX_BIT = 24
    val AREACONTENTS_CLUSTERPORTAL: Int = BIT(2) // area is a cluster portal
    val AREACONTENTS_OBSTACLE: Int = BIT(3) // area contains (part of) a dynamic obstacle

    //
    // area contents flags
    val AREACONTENTS_SOLID: Int = BIT(0) // solid, not a valid area
    val AREACONTENTS_TELEPORTER: Int = BIT(4) // area contains (part of) a teleporter trigger
    val AREACONTENTS_WATER: Int = BIT(1) // area contains water
    val AREA_CROUCH: Int = BIT(5) // AI cannot walk but can only crouch in this area

    //
    // area flags
    val AREA_FLOOR: Int = BIT(0) // AI can stand on the floor in this area
    val AREA_GAP: Int = BIT(1) // area has a gap
    val AREA_LADDER: Int = BIT(3) // area contains one or more ladder faces
    val AREA_LEDGE: Int = BIT(2) // if entered the AI bbox partly floats above a ledge
    val AREA_LIQUID: Int = BIT(4) // area contains a liquid
    val AREA_REACHABLE_FLY: Int = BIT(7) // area is reachable by flying
    val AREA_REACHABLE_WALK: Int = BIT(6) // area is reachable by walking or swimming
    val FACE_FLOOR: Int = BIT(2) // standing on floor when on this face
    val FACE_LADDER: Int = BIT(1) // ladder surface
    val FACE_LIQUID: Int = BIT(3) // face seperating two areas with liquid
    val FACE_LIQUIDSURFACE: Int = BIT(4) // face seperating liquid and air

    //
    // face flags
    val FACE_SOLID: Int = BIT(0) // solid at the other side

    //
    const val MAX_AAS_BOUNDING_BOXES = 4
    const val MAX_AAS_TREE_DEPTH = 128

    //
    const val MAX_REACH_PER_AREA = 256
    val TFL_AIR: Int = BIT(22) // travel through air
    const val TFL_BARRIERJUMP = 1 shl 4 //BIT(4); // jumping onto a barrier
    val TFL_CROUCH: Int = BIT(2) // crouching
    val TFL_ELEVATOR: Int = BIT(10) // travel by elevator
    val TFL_FLY: Int = BIT(11) // fly

    //
    // travel flags
    val TFL_INVALID: Int = BIT(0) // not valid
    const val TFL_JUMP = 1 shl 5 //BIT(5);        // jumping
    val TFL_LADDER: Int = BIT(6) // climbing a ladder
    val TFL_SPECIAL: Int = BIT(12) // special
    val TFL_SWIM: Int = BIT(7) // swimming
    val TFL_TELEPORT: Int = BIT(9) // teleportation
    val TFL_WALK: Int = BIT(1) // walking
    const val TFL_WALKOFFLEDGE = 1 shl 3 //BIT(3);// walking of a ledge
    val TFL_WATER: Int = BIT(21) // travel through water
    val TFL_WATERJUMP: Int = BIT(8) // jump out of the water

    //
    /*
     ================
     Reachability_Write
     ================
     */
    fun Reachability_Write(fp: idFile, reach: idReachability): Boolean {
        fp.WriteFloatString(
            "\t\t%d %d (%f %f %f) (%f %f %f) %d %d",
            reach.travelType,
            reach.toAreaNum.toInt(),
            reach.start.x,
            reach.start.y,
            reach.start.z,
            reach.end.x,
            reach.end.y,
            reach.end.z,
            reach.edgeNum,
            reach.travelTime
        )
        return true
    }

    /*
     ================
     Reachability_Read
     ================
     */
    fun Reachability_Read(src: idLexer, reach: idReachability): Boolean {
        reach.travelType = src.ParseInt()
        reach.toAreaNum = src.ParseInt().toShort()
        src.Parse1DMatrix(3, reach.start)
        src.Parse1DMatrix(3, reach.end)
        reach.edgeNum = src.ParseInt()
        reach.travelTime = src.ParseInt()
        return true
    }

    /*
     ================
     Reachability_Special_Write
     ================
     */
    fun Reachability_Special_Write(fp: idFile, reach: idReachability_Special): Boolean {
        var i: Int
        var keyValue: idKeyValue
        fp.WriteFloatString("\n\t\t{\n")
        i = 0
        while (i < reach.dict.GetNumKeyVals()) {
            keyValue = reach.dict.GetKeyVal(i)!!
            fp.WriteFloatString("\t\t\t\"%s\" \"%s\"\n", keyValue.GetKey().toString(), keyValue.GetValue().toString())
            i++
        }
        fp.WriteFloatString("\t\t}\n")
        return true
    }

    /*
     ================
     Reachability_Special_Read
     ================
     */
    fun Reachability_Special_Read(src: idLexer, reach: idReachability_Special): Boolean {
        val key = idToken()
        val value = idToken()
        src.ExpectTokenString("{")
        while (src.ReadToken(key)) {
            if (key.toString() == "}") {
                return true
            }
            src.ExpectTokenType(Token.TT_STRING, 0, value)
            reach.dict.Set(key, value)
        }
        return false
    }

    // reachability to another area
    open class idReachability {
        var areaTravelTimes // travel times within the fromAreaNum from reachabilities that lead towards this area
                : IntBuffer? = null
        var disableCount // number of times this reachability has been disabled
                : Byte = 0
        var edgeNum // edge crossed by this reachability
                = 0
        val end // end point of inter area movement
                : idVec3
        var fromAreaNum // number of area the reachability starts
                : Short = 0
        var next // next reachability in list
                : idReachability? = null
        var number // reachability number within the fromAreaNum (must be < 256)
                : Byte = 0
        var rev_next // next reachability in reversed list
                : idReachability? = null
        val start // start point of inter area movement
                : idVec3
        var toAreaNum // number of the reachable area
                : Short = 0
        /*unsigned short*/  var travelTime // travel time of the inter area movement
                = 0
        var travelType // type of travel required to get to the area
                = 0

        fun CopyBase(reach: idReachability) {
            travelType = reach.travelType
            toAreaNum = reach.toAreaNum
            start.set(reach.start)
            end.set(reach.end)
            edgeNum = reach.edgeNum
            travelTime = reach.travelTime
        }

        //
        //
        init {
            start = idVec3()
            end = idVec3()
        }
    }

    class idReachability_Walk : idReachability()
    class idReachability_BarrierJump : idReachability()
    class idReachability_WaterJump : idReachability()
    class idReachability_WalkOffLedge : idReachability()
    class idReachability_Swim : idReachability()
    class idReachability_Fly : idReachability()
    class idReachability_Special : idReachability() {
        var dict: idDict = idDict()
    }

    // edge
    class aasEdge_s {
        var vertexNum: IntArray = IntArray(2) // numbers of the vertexes of this edge
    }

    // area boundary face
    class aasFace_s {
        var areas: ShortArray = ShortArray(2) // area at the front and back of this face
        var firstEdge // first edge in the edge index
                = 0
        var flags // face flags
                = 0
        var numEdges // number of edges in the boundary of the face
                = 0
        var planeNum // number of the plane this face is on
                = 0
    }

    // area with a boundary of faces
    class aasArea_s {
        val bounds // bounds of the area
                : idBounds = idBounds()
        val center: idVec3 = idVec3() // center of the area an AI can move towards
        var cluster // cluster the area belongs to, if negative it's a portal
                : Short = 0
        var clusterAreaNum // number of the area in the cluster
                : Short = 0
        var contents // contents of the area
                = 0
        var firstFace // first face in the face index used for the boundary of the area
                = 0
        var flags // several area flags
                = 0
        var numFaces // number of faces used for the boundary of the area
                = 0
        var reach // reachabilities that start from this area
                : idReachability? = null
        var rev_reach // reachabilities that lead to this area
                : idReachability? = null
        var travelFlags // travel flags for traveling through this area
                = 0
    }

    // nodes of the bsp tree
    class aasNode_s {
        /*unsigned short*/
        var children: IntArray = IntArray(2) // child nodes, zero is solid, negative is -(area number)
        var planeNum // number of the plane that splits the subspace at this node
                = 0
    }

    // cluster portal
    class aasPortal_s {
        var areaNum // number of the area that is the actual portal
                : Short = 0
        var clusterAreaNum: ShortArray = ShortArray(2) // number of this portal area in the front and back cluster
        var clusters: ShortArray = ShortArray(2) // number of cluster at the front and back of the portal
        var maxAreaTravelTime // maximum travel time through the portal area
                = 0
    }

    // cluster
    class aasCluster_s {
        var firstPortal // first cluster portal in the index
                = 0
        var numAreas // number of areas in the cluster
                = 0
        var numPortals // number of cluster portals
                = 0
        var numReachableAreas // number of areas with reachabilities
                = 0
    }

    // trace through the world
    class aasTrace_s {
        // parameters
        var areas // array to store areas the trace went through
                : IntArray?
        var blockingAreaNum // area that could not be entered
                = 0
        val endpos // end position of trace
                : idVec3
        var flags // areas with these flags block the trace
                : Int

        // output
        var fraction // fraction of trace completed
                = 0.0f
        var getOutOfSolid // trace out of solid if the trace starts in solid
                : Int
        var lastAreaNum // number of last area the trace went through
                = 0
        var maxAreas // size of the 'areas' array
                : Int
        var numAreas // number of areas the trace went through
                = 0
        var planeNum // plane hit
                = 0
        var points // points where the trace entered each new area
                : Array<idVec3>?
        var travelFlags // areas with these travel flags block the trace
                : Int

        init {
            endpos = idVec3()
            areas = null
            points = null
            maxAreas = 0
            travelFlags = maxAreas //false;
            flags = travelFlags
            getOutOfSolid = flags
        }
    }

    /*
     ===============================================================================

     idAASSettings

     ===============================================================================
     */
    class idAASSettings {
        var allowFlyReachabilities: CBool = CBool(false)
        var allowSwimReachabilities: CBool = CBool(false)
        var boundingBoxes: Array<idBounds> = Array(MAX_AAS_BOUNDING_BOXES) { idBounds() }
        var fileExtension: idStr

        // physics settings
        val gravity: idVec3
        val gravityDir: idVec3
        var gravityValue: Float
        val invGravityDir: idVec3
        var maxBarrierHeight: CFloat = CFloat()
        var maxFallHeight: CFloat = CFloat()
        var maxStepHeight: CFloat = CFloat()
        var maxWaterJumpHeight: CFloat = CFloat()
        var minFloorCos: CFloat = CFloat()
        var noOptimize: Boolean

        // collision settings
        var numBoundingBoxes = 1
        var playerFlood: CBool = CBool(false)

        // fixed travel times
        var tt_barrierJump: CInt = CInt()
        var tt_startCrouching: CInt = CInt()
        var tt_startWalkOffLedge: CInt = CInt()
        var tt_waterJump: CInt = CInt()
        var usePatches: CBool = CBool(false)
        var writeBrushMap: CBool = CBool(false)

        @Throws(idException::class)
        fun FromFile(fileName: idStr): Boolean {
            val src =
                idLexer(Lexer.LEXFL_ALLOWPATHNAMES or Lexer.LEXFL_NOSTRINGESCAPECHARS or Lexer.LEXFL_NOSTRINGCONCAT)
            val name: idStr
            name = fileName
            Common.common.Printf("loading %s\n", name)
            if (!src.LoadFile(name.toString())) {
                Common.common.Error("WARNING: couldn't load %s\n", name)
                return false
            }
            if (!src.ExpectTokenString("settings")) {
                Common.common.Error("%s is not a settings file", name)
                return false
            }
            if (!FromParser(src)) {
                Common.common.Error("failed to parse %s", name)
                return false
            }
            return true
        }

        @Throws(idException::class)
        fun FromParser(src: idLexer): Boolean {
            val token = idToken()
            if (!src.ExpectTokenString("{")) {
                return false
            }

            // parse the file
            while (true) {
                if (!src.ReadToken(token)) {
                    break
                }
                if (token.toString() == "}") {
                    break
                }
                if (token.toString() == "bboxes") {
                    if (!ParseBBoxes(src)) {
                        return false
                    }
                } else if (token.toString() == "usePatches") {
                    if (!ParseBool(src, usePatches)) {
                        return false
                    }
                } else if (token.toString() == "writeBrushMap") {
                    if (!ParseBool(src, writeBrushMap)) {
                        return false
                    }
                } else if (token.toString() == "playerFlood") {
                    if (!ParseBool(src, playerFlood)) {
                        return false
                    }
                } else if (token.toString() == "allowSwimReachabilities") {
                    if (!ParseBool(src, allowSwimReachabilities)) {
                        return false
                    }
                } else if (token.toString() == "allowFlyReachabilities") {
                    if (!ParseBool(src, allowFlyReachabilities)) {
                        return false
                    }
                } else if (token.toString() == "fileExtension") {
                    src.ExpectTokenString("=")
                    src.ExpectTokenType(Token.TT_STRING, 0, token)
                    fileExtension = token
                } else if (token.toString() == "gravity") {
                    ParseVector(src, gravity)
                    gravityDir.set(gravity)
                    gravityValue = gravityDir.Normalize()
                    invGravityDir.set(-gravityDir)
                } else if (token.toString() == "maxStepHeight") {
                    if (!ParseFloat(src, maxStepHeight)) {
                        return false
                    }
                } else if (token.toString() == "maxBarrierHeight") {
                    if (!ParseFloat(src, maxBarrierHeight)) {
                        return false
                    }
                } else if (token.toString() == "maxWaterJumpHeight") {
                    if (!ParseFloat(src, maxWaterJumpHeight)) {
                        return false
                    }
                } else if (token.toString() == "maxFallHeight") {
                    if (!ParseFloat(src, maxFallHeight)) {
                        return false
                    }
                } else if (token.toString() == "minFloorCos") {
                    if (!ParseFloat(src, minFloorCos)) {
                        return false
                    }
                } else if (token.toString() == "tt_barrierJump") {
                    if (!ParseInt(src, tt_barrierJump)) {
                        return false
                    }
                } else if (token.toString() == "tt_startCrouching") {
                    if (!ParseInt(src, tt_startCrouching)) {
                        return false
                    }
                } else if (token.toString() == "tt_waterJump") {
                    if (!ParseInt(src, tt_waterJump)) {
                        return false
                    }
                } else if (token.toString() == "tt_startWalkOffLedge") {
                    if (!ParseInt(src, tt_startWalkOffLedge)) {
                        return false
                    }
                } else {
                    src.Error("invalid token '%s'", token)
                }
            }
            if (numBoundingBoxes <= 0) {
                src.Error("no valid bounding box")
            }
            return true
        }

        fun FromDict(name: String, dict: idDict): Boolean {
            val bounds = idBounds()
            if (!dict.GetVector("mins", "0 0 0", bounds[0])) {
                Common.common.Error("Missing 'mins' in entityDef '%s'", name)
            }
            if (!dict.GetVector("maxs", "0 0 0", bounds[1])) {
                Common.common.Error("Missing 'maxs' in entityDef '%s'", name)
            }
            numBoundingBoxes = 1
            boundingBoxes[0] = bounds
            if (!dict.GetBool("usePatches", "0", usePatches)) {
                Common.common.Error("Missing 'usePatches' in entityDef '%s'", name)
            }
            if (!dict.GetBool("writeBrushMap", "0", writeBrushMap)) {
                Common.common.Error("Missing 'writeBrushMap' in entityDef '%s'", name)
            }
            if (!dict.GetBool("playerFlood", "0", playerFlood)) {
                Common.common.Error("Missing 'playerFlood' in entityDef '%s'", name)
            }
            if (!dict.GetBool("allowSwimReachabilities", "0", allowSwimReachabilities)) {
                Common.common.Error("Missing 'allowSwimReachabilities' in entityDef '%s'", name)
            }
            if (!dict.GetBool("allowFlyReachabilities", "0", allowFlyReachabilities)) {
                Common.common.Error("Missing 'allowFlyReachabilities' in entityDef '%s'", name)
            }
            if (!dict.GetString("fileExtension", "", fileExtension)) {
                Common.common.Error("Missing 'fileExtension' in entityDef '%s'", name)
            }
            if (!dict.GetVector("gravity", "0 0 -1066", gravity)) {
                Common.common.Error("Missing 'gravity' in entityDef '%s'", name)
            }
            gravityDir.set(gravity)
            gravityValue = gravityDir.Normalize()
            invGravityDir.set(-gravityDir)
            if (!dict.GetFloat("maxStepHeight", "0", maxStepHeight)) {
                Common.common.Error("Missing 'maxStepHeight' in entityDef '%s'", name)
            }
            if (!dict.GetFloat("maxBarrierHeight", "0", maxBarrierHeight)) {
                Common.common.Error("Missing 'maxBarrierHeight' in entityDef '%s'", name)
            }
            if (!dict.GetFloat("maxWaterJumpHeight", "0", maxWaterJumpHeight)) {
                Common.common.Error("Missing 'maxWaterJumpHeight' in entityDef '%s'", name)
            }
            if (!dict.GetFloat("maxFallHeight", "0", maxFallHeight)) {
                Common.common.Error("Missing 'maxFallHeight' in entityDef '%s'", name)
            }
            if (!dict.GetFloat("minFloorCos", "0", minFloorCos)) {
                Common.common.Error("Missing 'minFloorCos' in entityDef '%s'", name)
            }
            if (!dict.GetInt("tt_barrierJump", "0", tt_barrierJump)) {
                Common.common.Error("Missing 'tt_barrierJump' in entityDef '%s'", name)
            }
            if (!dict.GetInt("tt_startCrouching", "0", tt_startCrouching)) {
                Common.common.Error("Missing 'tt_startCrouching' in entityDef '%s'", name)
            }
            if (!dict.GetInt("tt_waterJump", "0", tt_waterJump)) {
                Common.common.Error("Missing 'tt_waterJump' in entityDef '%s'", name)
            }
            if (!dict.GetInt("tt_startWalkOffLedge", "0", tt_startWalkOffLedge)) {
                Common.common.Error("Missing 'tt_startWalkOffLedge' in entityDef '%s'", name)
            }
            return true
        }

        fun WriteToFile(fp: idFile): Boolean {
            var i: Int
            fp.WriteFloatString("{\n")
            fp.WriteFloatString("\tbboxes\n\t{\n")
            i = 0
            while (i < numBoundingBoxes) {
                fp.WriteFloatString(
                    "\t\t(%f %f %f)-(%f %f %f)\n",
                    boundingBoxes[i][0].x,
                    boundingBoxes[i][0].y,
                    boundingBoxes[i][0].z,
                    boundingBoxes[i][1].x,
                    boundingBoxes[i][1].y,
                    boundingBoxes[i][1].z
                )
                i++
            }
            fp.WriteFloatString("\t}\n")
            fp.WriteFloatString("\tusePatches = %d\n", usePatches._val)
            fp.WriteFloatString("\twriteBrushMap = %d\n", writeBrushMap._val)
            fp.WriteFloatString("\tplayerFlood = %d\n", playerFlood._val)
            fp.WriteFloatString("\tallowSwimReachabilities = %d\n", allowSwimReachabilities._val)
            fp.WriteFloatString("\tallowFlyReachabilities = %d\n", allowFlyReachabilities._val)
            fp.WriteFloatString("\tfileExtension = \"%s\"\n", fileExtension)
            fp.WriteFloatString("\tgravity = (%f %f %f)\n", gravity.x, gravity.y, gravity.z)
            fp.WriteFloatString("\tmaxStepHeight = %f\n", maxStepHeight._val)
            fp.WriteFloatString("\tmaxBarrierHeight = %f\n", maxBarrierHeight._val)
            fp.WriteFloatString("\tmaxWaterJumpHeight = %f\n", maxWaterJumpHeight._val)
            fp.WriteFloatString("\tmaxFallHeight = %f\n", maxFallHeight._val)
            fp.WriteFloatString("\tminFloorCos = %f\n", minFloorCos._val)
            fp.WriteFloatString("\ttt_barrierJump = %d\n", tt_barrierJump._val)
            fp.WriteFloatString("\ttt_startCrouching = %d\n", tt_startCrouching._val)
            fp.WriteFloatString("\ttt_waterJump = %d\n", tt_waterJump._val)
            fp.WriteFloatString("\ttt_startWalkOffLedge = %d\n", tt_startWalkOffLedge._val)
            fp.WriteFloatString("}\n")
            return true
        }

        fun ValidForBounds(bounds: idBounds): Boolean {
            var i: Int
            i = 0
            while (i < 3) {
                if (bounds[0, i] < boundingBoxes[0][0, i]) {
                    return false
                }
                if (bounds[1, i] > boundingBoxes[0][1, i]) {
                    return false
                }
                i++
            }
            return true
        }

        fun ValidEntity(classname: String): Boolean {
            val use_aas = idStr()
            val size = idVec3()
            val bounds = idBounds()
            if (playerFlood._val) {
                if (classname == "info_player_start" || classname == "info_player_deathmatch" || classname == "func_teleporter") {
                    return true
                }
            }
            val decl = DeclManager.declManager.FindType(declType_t.DECL_ENTITYDEF, classname, false) as idDeclEntityDef
            if (decl != null && decl.dict.GetString(
                    "use_aas", null, use_aas
                ) && fileExtension.Icmp(use_aas) == 0
            ) {
                if (decl.dict.GetVector("mins", null, bounds[0])) {
                    decl.dict.GetVector("maxs", null, bounds[1])
                } else if (decl.dict.GetVector("size", null, size)) {
                    bounds[0].set(size.x * -0.5f, size.y * -0.5f, 0.0f)
                    bounds[1].set(size.x * 0.5f, size.y * 0.5f, size.z)
                }
                if (!ValidForBounds(bounds)) {
                    Common.common.Error("%s cannot use %s\n", classname, fileExtension)
                }
                return true
            }
            return false
        }

        private fun ParseBool(src: idLexer, b: CBool): Boolean {
            if (!src.ExpectTokenString("=")) {
                return false
            }
            b._val = (src.ParseBool())
            return true
        }

        private fun ParseInt(src: idLexer, i: CInt): Boolean {
            if (!src.ExpectTokenString("=")) {
                return false
            }
            i._val = (src.ParseInt())
            return true
        }

        private fun ParseFloat(src: idLexer, f: CFloat): Boolean {
            if (!src.ExpectTokenString("=")) {
                return false
            }
            f._val = (src.ParseFloat())
            return true
        }

        private fun ParseVector(src: idLexer, vec: idVec3): Boolean {
            return src.ExpectTokenString("=") && src.Parse1DMatrix(3, vec)
        }

        private fun ParseBBoxes(src: idLexer): Boolean {
            val token = idToken()
            val bounds = idBounds()
            numBoundingBoxes = 0
            if (!src.ExpectTokenString("{")) {
                return false
            }
            while (src.ReadToken(token)) {
                if (token.toString() == "}") {
                    return true
                }
                src.UnreadToken(token)
                src.Parse1DMatrix(3, bounds[0])
                if (!src.ExpectTokenString("-")) {
                    return false
                }
                src.Parse1DMatrix(3, bounds[1])
                boundingBoxes[numBoundingBoxes++] = bounds
            }
            return false
        }

        //
        //
        init {
            boundingBoxes[0] = idBounds(idVec3(-16.0f, -16.0f, 0.0f), idVec3(16.0f, 16.0f, 72.0f))
            usePatches._val = (false)
            writeBrushMap._val = (false)
            playerFlood._val = (false)
            noOptimize = false
            allowSwimReachabilities._val = (false)
            allowFlyReachabilities._val = (false)
            fileExtension = idStr("aas48") // physics settings
            gravity = idVec3(0.0f, 0.0f, -1066.0f)
            gravityDir = gravity
            gravityValue = gravityDir.Normalize()
            invGravityDir = -gravityDir
            maxStepHeight._val = 14.0f
            maxBarrierHeight._val = 32.0f
            maxWaterJumpHeight._val = 20.0f
            maxFallHeight._val = 64.0f
            minFloorCos._val = 0.7f // fixed travel times
            tt_barrierJump._val = 100
            tt_startCrouching._val = 100
            tt_waterJump._val = 100
            tt_startWalkOffLedge._val = 100
        }
    }

    /*

     -	when a node child is a solid leaf the node child number is zero
     -	two adjacent areas (sharing a plane at opposite sides) share a face
     this face is a portal between the areas
     -	when an area uses a face from the faceindex with a positive index
     then the face plane normal points into the area
     -	the face edges are stored counter clockwise using the edgeindex
     -	two adjacent convex areas (sharing a face) only share One face
     this is a simple result of the areas being convex
     -	the areas can't have a mixture of ground and gap faces
     other mixtures of faces in one area are allowed
     -	areas with the AREACONTENTS_CLUSTERPORTAL in the settings have
     the cluster number set to the negative portal number
     -	edge zero is a dummy
     -	face zero is a dummy
     -	area zero is a dummy
     -	node zero is a dummy
     -	portal zero is a dummy
     -	cluster zero is a dummy

     */
    abstract class idAASFile protected constructor() {
        val areas: idList<aasArea_s>
        val clusters: idList<aasCluster_s>
        protected var   /*unsigned int*/crc: Long = 0
        val edgeIndex: idList<Int>
        val edges: idList<aasEdge_s>
        val faceIndex: idList<Int>
        val faces: idList<aasFace_s>
        protected val name: idStr
        val nodes: idList<aasNode_s>

        //
        var planeList: idPlaneSet
        val portalIndex: idList<Int>
        val portals: idList<aasPortal_s>
        var settings: idAASSettings
        val vertices: idList<idVec3>

        // virtual 					~idAASFile() {}
        fun GetName(): String {
            return name.toString()
        }

        fun  /*unsigned int*/GetCRC(): Long {
            return crc
        }

        fun GetNumPlanes(): Int {
            return planeList.Num()
        }

        fun GetPlane(index: Int): idPlane {
            return planeList[index]
        }

        fun GetNumVertices(): Int {
            return vertices.Num()
        }

        fun  /*aasVertex_t*/GetVertex(index: Int): idVec3 {
            return vertices[index]
        }

        fun GetNumEdges(): Int {
            return edges.Num()
        }

        fun GetEdge(index: Int): aasEdge_s {
            return edges[index]
        }

        fun GetNumEdgeIndexes(): Int {
            return edgeIndex.Num()
        }

        fun  /*aasIndex_t*/GetEdgeIndex(index: Int): Int {
            return edgeIndex[index]
        }

        fun GetNumFaces(): Int {
            return faces.Num()
        }

        fun GetFace(index: Int): aasFace_s {
            return faces[index]
        }

        fun GetNumFaceIndexes(): Int {
            return faceIndex.Num()
        }

        fun  /*aasIndex_t*/GetFaceIndex(index: Int): Int {
            return faceIndex[index]
        }

        fun GetNumAreas(): Int {
            return areas.Num()
        }

        fun GetArea(index: Int): aasArea_s {
            return areas[index]
        }

        fun GetNumNodes(): Int {
            return nodes.Num()
        }

        fun GetNode(index: Int): aasNode_s {
            return nodes[index]
        }

        fun GetNumPortals(): Int {
            return portals.Num()
        }

        fun GetPortal(index: Int): aasPortal_s {
            return portals[index]
        }

        fun GetNumPortalIndexes(): Int {
            return portalIndex.Num()
        }

        fun  /*aasIndex_t*/GetPortalIndex(index: Int): Int {
            return portalIndex[index]
        }

        fun GetNumClusters(): Int {
            return clusters.Num()
        }

        fun GetCluster(index: Int): aasCluster_s {
            return clusters[index]
        }

        fun GetSettings(): idAASSettings {
            return settings
        }

        fun SetPortalMaxTravelTime(index: Int, time: Int) {
            portals[index].maxAreaTravelTime = time
        }

        fun SetAreaTravelFlag(index: Int, flag: Int) {
            areas[index].travelFlags = areas[index].travelFlags or flag
        }

        fun RemoveAreaTravelFlag(index: Int, flag: Int) {
            areas[index].travelFlags = areas[index].travelFlags and flag.inv()
        }

        abstract fun EdgeCenter(edgeNum: Int): idVec3
        abstract fun FaceCenter(faceNum: Int): idVec3
        abstract fun AreaCenter(areaNum: Int): idVec3

        //
        abstract fun EdgeBounds(edgeNum: Int): idBounds
        abstract fun FaceBounds(faceNum: Int): idBounds
        abstract fun AreaBounds(areaNum: Int): idBounds

        //
        abstract fun PointAreaNum(origin: idVec3): Int
        abstract fun PointReachableAreaNum(
            origin: idVec3, searchBounds: idBounds, areaFlags: Int, excludeTravelFlags: Int
        ): Int

        abstract fun BoundsReachableAreaNum(bounds: idBounds, areaFlags: Int, excludeTravelFlags: Int): Int
        abstract fun PushPointIntoAreaNum(areaNum: Int, point: idVec3)
        abstract fun Trace(trace: aasTrace_s, start: idVec3, end: idVec3): Boolean
        abstract fun PrintInfo()

        //
        //
        init {
            name = idStr()
            planeList = idPlaneSet()
            vertices = idList()
            edges = idList()
            edgeIndex = idList()
            faces = idList()
            faceIndex = idList()
            areas = idList()
            nodes = idList()
            portals = idList()
            portalIndex = idList()
            clusters = idList()
            settings = idAASSettings()
        }
    }
}
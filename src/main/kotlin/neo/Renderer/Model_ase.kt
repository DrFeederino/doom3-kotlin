/*
===========================================================================

Doom 3 GPL Source Code
Copyright (C) 1999-2011 id Software LLC, a ZeniMax Media company.
Translated to Kotlin by Dr. Feederino with support of Claude Code

This file is part of the Doom 3 GPL Source Code ("Doom 3 Source Code").
Original source: neo/renderer/Model_ase.cpp

Doom 3 Source Code is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

Doom 3 Source Code is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with Doom 3 Source Code.  If not, see <http://www.gnu.org/licenses/>.

===========================================================================
*/

package neo.Renderer

import neo.TempDump.bbtocb
import neo.framework.Common
import neo.framework.FileSystem_h.fileSystem
import neo.idlib.Text.Str.idStr
import neo.idlib.Text.Str.idStr.Companion.Copynz
import neo.idlib.containers.List.idList
import neo.idlib.math.idVec2
import neo.idlib.math.idVec3
import java.nio.ByteBuffer
import java.nio.CharBuffer

object Model_ase {
    var ase: ase_t? = null

    /*
     =================
     ASE_Load
     =================
     */
    fun ASE_Load(fileName: String?): aseModel_s? {
        val buf: Array<ByteBuffer?> = arrayOf(null)
        val timeStamp = LongArray(1)
        val ase: aseModel_s?
        fileSystem.ReadFile(fileName!!, buf, timeStamp)
        if (null == buf) {
            return null
        }
        ase = ASE_Parse(buf[0], false)
        ase!!.timeStamp[0] = timeStamp[0]
        fileSystem.FreeFile(buf)
        return ase
    }

    /*
     =================
     ASE_Free
     =================
     */
    fun ASE_Free(ase: aseModel_s?) {
        var i: Int
        var j: Int
        var obj: aseObject_t?
        var mesh: aseMesh_t?
        if (null == ase) {
            return
        }
        i = 0
        while (i < ase.objects.Num()) {
            obj = ase.objects[i]
            j = 0
            while (j < obj!!.frames.Num()) {
                mesh = obj.frames[j]
                if (mesh.vertexes != null) {
                    mesh.vertexes = null
                }
                if (mesh.tvertexes != null) {
                    mesh.tvertexes = null
                }
                if (mesh.cvertexes != null) {
                    mesh.cvertexes = null
                }
                if (mesh.faces != null) {
                    mesh.faces = null
                }
                mesh = null
                j++
            }
            obj.frames.Clear()

            // free the base nesh
            mesh = obj.mesh
            if (mesh.vertexes != null) {
                mesh.vertexes = null
            }
            if (mesh.tvertexes != null) {
                mesh.tvertexes = null
            }
            if (mesh.cvertexes != null) {
                mesh.cvertexes = null
            }
            if (mesh.faces != null) {
                mesh.faces = null
            }
            obj = null
            i++
        }
        ase.objects.Clear()
        i = 0
        while (i < ase.materials.Num()) {
            ase.materials[i] = null
            i++
        }
        ase.materials.Clear()
    }

    /*
     ======================================================================

     Parses 3D Studio Max ASCII export files.
     The goal is to parse the information into memory exactly as it is
     represented in the file.  Users of the data will then move it
     into a form that is more convenient for them.

     ======================================================================
     */
    fun VERBOSE(fmt: String?, vararg x: Any) {
        if (ase!!.verbose) {
            Common.common.Printf((fmt)!!, *x)
        }
    }

    fun ASE_GetCurrentMesh(): aseMesh_t? {
        return ase!!.currentMesh
    }

    fun CharIsTokenDelimiter(ch: Int): Boolean {
        return ch <= 32
    }

    fun ASE_GetToken(restOfLine: Boolean): Boolean {
        var i = 0
        ase!!.token = ""
        if (ase!!.buffer == null) {
            return false
        }
        if (ase!!.curpos == ase!!.len) {
            return false
        }

        // skip over crap
        while ((ase!!.curpos < ase!!.len) && (ase!!.buffer!!.get(ase!!.curpos).code <= 32)) {
            ase!!.curpos++
        }
        while (ase!!.curpos < ase!!.len) {
            ase!!.token += ase!!.buffer!!.get(ase!!.curpos) //ase.token[i] = *ase.curpos;
            ase!!.curpos++
            i++
            val c: Char = ase!!.token!![i - 1]
            if ((CharIsTokenDelimiter(c.code) && !restOfLine) || ((c == '\n') || (c == '\r'))) {
                ase!!.token = ase!!.token!!.substring(0, i - 1)
                break
            }
        }

        return true
    }

    /**
     *
     */
    fun ASE_ParseBracedBlock(parser: ASE?) {
        var indent = 0
        while (ASE_GetToken(false)) {
            if (("{" == ase!!.token)) {
                indent++
            } else if (("}" == ase!!.token)) {
                --indent
                if (indent == 0) {
                    break
                } else if (indent < 0) {
                    Common.common.Error("Unexpected '}'")
                }
            } else {
                if (parser != null) {
                    parser.run(ase!!.token)
                }
            }
        }
    }

    fun ASE_SkipEnclosingBraces() {
        var indent = 0
        while (ASE_GetToken(false)) {
            if (("{" == ase!!.token)) {
                indent++
            } else if (("}" == ase!!.token)) {
                indent--
                if (indent == 0) {
                    break
                } else if (indent < 0) {
                    Common.common.Error("Unexpected '}'")
                }
            }
        }
    }

    fun ASE_SkipRestOfLine() {
        ASE_GetToken(true)
    }

    fun ASE_ParseGeomObject() {
        val `object`: aseObject_t
        VERBOSE(("GEOMOBJECT"))

        `object` = aseObject_t()
        ase!!.model!!.objects.Append(`object`)
        ase!!.currentObject = `object`
        `object`.frames.Resize(32, 32)
        ASE_ParseBracedBlock(ASE_KeyGEOMOBJECT.instance)
    }

    /*
     =================
     ASE_Parse
     =================
     */
    fun ASE_Parse(buffer: ByteBuffer?, verbose: Boolean): aseModel_s? {
        ase = ase_t()
        ase!!.verbose = verbose
        ase!!.buffer = bbtocb((buffer)!!)
        ase!!.len = ase!!.buffer!!.length
        ase!!.curpos = 0
        ase!!.currentObject = null

        // NOTE: using new operator because aseModel_t contains idList class objects
        ase!!.model = aseModel_s()
        ase!!.model!!.objects.Resize(32, 32)
        ase!!.model!!.materials.Resize(32, 32)
        while (ASE_GetToken(false)) {
            when (ase!!.token) {
                "*3DSMAX_ASCIIEXPORT", "*COMMENT" -> ASE_SkipRestOfLine()
                "*SCENE" -> ASE_SkipEnclosingBraces()
                "*GROUP" -> {
                    ASE_GetToken(false) // group name
                    ASE_ParseBracedBlock(ASE_KeyGROUP.instance)
                }

                "*SHAPEOBJECT" -> ASE_SkipEnclosingBraces()
                "*CAMERAOBJECT" -> ASE_SkipEnclosingBraces()
                "*MATERIAL_LIST" -> {
                    VERBOSE(("MATERIAL_LIST\n"))
                    ASE_ParseBracedBlock(ASE_KeyMATERIAL_LIST.instance)
                }

                "*GEOMOBJECT" -> ASE_ParseGeomObject()
                else -> if (ase!!.token != null && !ase!!.token!!.isEmpty()) {
                    Common.common.Printf("Unknown token '%s'\n", ase!!.token!!)
                }
            }
        }
        return ase!!.model
    }

    fun atof(str: String): Float {
        try {
            return str.toFloat()
        } catch (exc: NumberFormatException) {
            return 0.0f
        }
    }

    /*
     ===============================================================================

     ASE loader. (3D Studio Max ASCII Export)

     ===============================================================================
     */
    class aseFace_t {
        var tVertexNum: IntArray = IntArray(3)
        var vertexColors: Array<ByteArray> = Array(3) { ByteArray(4) }
        val faceNormal: idVec3 = idVec3()
        var vertexNum: IntArray = IntArray(3)
        val vertexNormals: Array<idVec3> = idVec3.generateArray(3)
    }

    class aseMesh_t {
        val transform: Array<idVec3> = idVec3.generateArray(4) // applied to normals
        var colorsParsed: Boolean = false
        var cvertexes: Array<idVec3>? = null
        var faces: Array<aseFace_t?>? = null
        var normalsParsed: Boolean = false
        var numCVFaces: Int = 0
        var numCVertexes: Int = 0
        var numFaces: Int = 0
        var numTVFaces: Int = 0
        var numTVertexes: Int = 0
        var numVertexes: Int = 0
        var timeValue: Int = 0
        var tvertexes: Array<idVec2>? = null
        var vertexes: Array<idVec3>? = null
    }

    class aseMaterial_t {
        val name: CharArray = CharArray(128)
        var angle: Float = 0.0f // in clockwise radians
        var uOffset: Float = 0.0f
        var vOffset: Float = 0.0f // max lets you offset by material without changing texCoords
        var uTiling: Float = 0.0f
        var vTiling: Float = 0.0f // multiply tex coords by this
    }

    class aseObject_t {
        val frames: idList<aseMesh_t>
        var materialRef: Int = 0
        var mesh: aseMesh_t
        var name: CharArray

        init {
            name = CharArray(128)
            mesh = aseMesh_t()
            frames = idList()
        }
    }

    class aseModel_s {
        val timeStamp: LongArray = LongArray(1)
        val materials: idList<aseMaterial_t?>
        val objects: idList<aseObject_t?>

        init {
            materials = idList()
            objects = idList()
        }
    }

    // working variables used during parsing
    class ase_t {
        var buffer: CharBuffer? = null
        var curpos: Int = 0
        var currentFace: Int = 0
        var currentMaterial: aseMaterial_t? = null
        var currentMesh: aseMesh_t? = null
        var currentObject: aseObject_t? = null
        var currentVertex: Int = 0
        var len: Int = 0
        var model: aseModel_s? = null
        var token: String? = null
        var verbose: Boolean = false
    }

    abstract class ASE {
        abstract fun run(token: String?)
    }

    class ASE_KeyMAP_DIFFUSE private constructor() : ASE() {
        override fun run(token: String?) {
            val material: aseMaterial_t?
            when ("" + token) {
                "*BITMAP" -> {
                    val qpath: idStr
                    val matname: idStr
                    ASE_GetToken(false)
                    // remove the quotes
                    val s: Int = ase!!.token!!.substring(1).indexOf('\"')
                    if (s > 0) {
                        ase!!.token = ase!!.token!!.substring(0, s + 1)
                    }
                    matname = idStr(ase!!.token!!.substring(1))
                    // convert the 3DSMax material pathname to a qpath
                    matname.BackSlashesToSlashes()
                    qpath = idStr(fileSystem.OSPathToRelativePath(matname.toString()))
                    Copynz(ase!!.currentMaterial!!.name, qpath.toString(), ase!!.currentMaterial!!.name.size)
                }

                "*UVW_U_OFFSET" -> {
                    material = ase!!.model!!.materials[ase!!.model!!.materials.Num() - 1]
                    ASE_GetToken(false)
                    material!!.uOffset = ase!!.token!!.toFloat()
                }

                "*UVW_V_OFFSET" -> {
                    material = ase!!.model!!.materials[ase!!.model!!.materials.Num() - 1]
                    ASE_GetToken(false)
                    material!!.vOffset = ase!!.token!!.toFloat()
                }

                "*UVW_U_TILING" -> {
                    material = ase!!.model!!.materials[ase!!.model!!.materials.Num() - 1]
                    ASE_GetToken(false)
                    material!!.uTiling = ase!!.token!!.toFloat()
                }

                "*UVW_V_TILING" -> {
                    material = ase!!.model!!.materials[ase!!.model!!.materials.Num() - 1]
                    ASE_GetToken(false)
                    material!!.vTiling = ase!!.token!!.toFloat()
                }

                "*UVW_ANGLE" -> {
                    material = ase!!.model!!.materials[ase!!.model!!.materials.Num() - 1]
                    ASE_GetToken(false)
                    material!!.angle = ase!!.token!!.toFloat()
                }

                else -> {}
            }
        }

        companion object {
            val instance: ASE = ASE_KeyMAP_DIFFUSE()
        }
    }

    class ASE_KeyMATERIAL private constructor() : ASE() {
        override fun run(token: String?) {
            run({
                if (("*MAP_DIFFUSE" == token)) {
                    ASE_ParseBracedBlock(ASE_KeyMAP_DIFFUSE.instance)
                } else {
                }
            })
        }

        companion object {
            val instance: ASE = ASE_KeyMATERIAL()
        }
    }

    class ASE_KeyMATERIAL_LIST private constructor() : ASE() {
        override fun run(token: String?) {
            if (("*MATERIAL_COUNT" == token)) {
                ASE_GetToken(false)
                VERBOSE("..num materials: %s\n", ase!!.token!!)
            } else if (("*MATERIAL" == token)) {
                VERBOSE("..material %d\n", ase!!.model!!.materials.Num())

                ase!!.currentMaterial = aseMaterial_t()
                ase!!.currentMaterial!!.uTiling = 1.0f
                ase!!.currentMaterial!!.vTiling = 1.0f
                ase!!.model!!.materials.Append(ase!!.currentMaterial)
                ASE_ParseBracedBlock(ASE_KeyMATERIAL.instance)
            }
        }

        companion object {
            val instance: ASE = ASE_KeyMATERIAL_LIST()
        }
    }

    class ASE_KeyNODE_TM private constructor() : ASE() {
        override fun run(token: String?) {
            var i: Int
            val j: Int
            when ("" + token) {
                "*TM_ROW0" -> j = 0
                "*TM_ROW1" -> j = 1
                "*TM_ROW2" -> j = 2
                "*TM_ROW3" -> j = 3
                else -> j = -1
            }
            i = 0
            while (i < 3 && j != -1) {
                ASE_GetToken(false)
                ase!!.currentObject!!.mesh.transform[j][i] = ase!!.token!!.toFloat()
                i++
            }
        }

        companion object {
            val instance: ASE = ASE_KeyNODE_TM()
        }
    }

    class ASE_KeyMESH_VERTEX_LIST private constructor() : ASE() {
        override fun run(token: String?) {
            run({
                val pMesh: aseMesh_t? = ASE_GetCurrentMesh()
                if (("*MESH_VERTEX" == token)) {
                    ASE_GetToken(false) // skip number
                    ASE_GetToken(false)
                    pMesh!!.vertexes!![ase!!.currentVertex].x = ase!!.token!!.toFloat()
                    ASE_GetToken(false)
                    pMesh.vertexes!![ase!!.currentVertex].y = ase!!.token!!.toFloat()
                    ASE_GetToken(false)
                    pMesh.vertexes!![ase!!.currentVertex].z = ase!!.token!!.toFloat()
                    ase!!.currentVertex++
                    if (ase!!.currentVertex > pMesh.numVertexes) {
                        Common.common.Error("ase.currentVertex >= pMesh.numVertexes")
                    }
                } else {
                    Common.common.Error("Unknown token '%s' while parsing MESH_VERTEX_LIST", (token)!!)
                }
            })
        }

        companion object {
            val instance: ASE = ASE_KeyMESH_VERTEX_LIST()
        }
    }

    class ASE_KeyMESH_FACE_LIST private constructor() : ASE() {
        override fun run(token: String?) {
            val pMesh: aseMesh_t? = ASE_GetCurrentMesh()
            if (("*MESH_FACE" == token)) {
                ASE_GetToken(false) // skip face number
                pMesh!!.faces!![ase!!.currentFace] = aseFace_t()

                // we are flipping the order here to change the front/back facing
                // from 3DS to our standard (clockwise facing out)
                ASE_GetToken(false) // skip label
                ASE_GetToken(false) // first vertex
                pMesh.faces!![ase!!.currentFace]!!.vertexNum[0] = ase!!.token!!.toInt()
                ASE_GetToken(false) // skip label
                ASE_GetToken(false) // second vertex
                pMesh.faces!![ase!!.currentFace]!!.vertexNum[2] = ase!!.token!!.toInt()
                ASE_GetToken(false) // skip label
                ASE_GetToken(false) // third vertex
                pMesh.faces!![ase!!.currentFace]!!.vertexNum[1] = ase!!.token!!.toInt()
                ASE_GetToken(true)

                // we could parse material id and smoothing groups here
                /*
                 if ( ( p = strstr( ase.token, "*MESH_MTLID" ) ) != 0 )
                 {
                 p += strlen( "*MESH_MTLID" ) + 1;
                 mtlID = Integer.parseInt( p );
                 }
                 else
                 {
                 common.Error( "No *MESH_MTLID found for face!" );
                 }
                 */ase!!.currentFace++
            } else {
                Common.common.Error("Unknown token '%s' while parsing MESH_FACE_LIST", (token)!!)
            }
        }

        companion object {
            val instance: ASE = ASE_KeyMESH_FACE_LIST()
        }
    }

    class ASE_KeyTFACE_LIST private constructor() : ASE() {
        override fun run(token: String?) {
            val pMesh: aseMesh_t? = ASE_GetCurrentMesh()
            if (("*MESH_TFACE" == token)) {
                val a: Int
                val b: Int
                val c: Int
                ASE_GetToken(false)
                ASE_GetToken(false)
                a = ase!!.token!!.toInt()
                ASE_GetToken(false)
                c = ase!!.token!!.toInt()
                ASE_GetToken(false)
                b = ase!!.token!!.toInt()
                pMesh!!.faces!![ase!!.currentFace]!!.tVertexNum[0] = a
                pMesh.faces!![ase!!.currentFace]!!.tVertexNum[1] = b
                pMesh.faces!![ase!!.currentFace]!!.tVertexNum[2] = c
                ase!!.currentFace++
            } else {
                Common.common.Error("Unknown token '%s' in MESH_TFACE", (token)!!)
            }
        }

        companion object {
            val instance: ASE = ASE_KeyTFACE_LIST()
        }
    }

    class ASE_KeyCFACE_LIST private constructor() : ASE() {
        override fun run(token: String?) {
            val pMesh: aseMesh_t? = ASE_GetCurrentMesh()
            if (("*MESH_CFACE" == token)) {
                ASE_GetToken(false)
                for (i in 0..2) {
                    ASE_GetToken(false)
                    val a: Int = ase!!.token!!.toInt()

                    // we flip the vertex order to change the face direction to our style
                    pMesh!!.faces!![ase!!.currentFace]!!.vertexColors[remap[i]][0] =
                        (pMesh.cvertexes!![a][0] * 255).toInt().toByte()
                    pMesh.faces!![ase!!.currentFace]!!.vertexColors[remap[i]][1] =
                        (pMesh.cvertexes!![a][1] * 255).toInt().toByte()
                    pMesh.faces!![ase!!.currentFace]!!.vertexColors[remap[i]][2] =
                        (pMesh.cvertexes!![a][2] * 255).toInt().toByte()
                }
                ase!!.currentFace++
            } else {
                Common.common.Error("Unknown token '%s' in MESH_CFACE", (token)!!)
            }
        }

        companion object {
            val instance: ASE = ASE_KeyCFACE_LIST()
            private val remap /*[3]*/: IntArray = intArrayOf(0, 2, 1)
        }
    }

    class ASE_KeyMESH_TVERTLIST private constructor() : ASE() {
        override fun run(token: String?) {
            val pMesh: aseMesh_t? = ASE_GetCurrentMesh()
            if (("*MESH_TVERT" == token)) {
                val u: String?
                val v: String?
                val w: String?
                ASE_GetToken(false)
                pMesh!!.tvertexes!![ase!!.currentVertex] = idVec2()
                ASE_GetToken(false)
                u = ase!!.token
                ASE_GetToken(false)
                v = ase!!.token
                ASE_GetToken(false)
                w = ase!!.token
                pMesh.tvertexes!![ase!!.currentVertex].x = u!!.toFloat()
                // our OpenGL second texture axis is inverted from MAX's sense
                pMesh.tvertexes!![ase!!.currentVertex].y = 1.0f - v!!.toFloat()
                ase!!.currentVertex++
                if (ase!!.currentVertex > pMesh.numTVertexes) {
                    Common.common.Error("ase.currentVertex > pMesh.numTVertexes")
                }
            } else {
                Common.common.Error("Unknown token '%s' while parsing MESH_TVERTLIST", (token)!!)
            }
        }

        companion object {
            val instance: ASE = ASE_KeyMESH_TVERTLIST()
        }
    }

    class ASE_KeyMESH_CVERTLIST private constructor() : ASE() {
        override fun run(token: String?) {
            val pMesh: aseMesh_t? = ASE_GetCurrentMesh()
            pMesh!!.colorsParsed = true
            if (("*MESH_VERTCOL" == token)) {
                ASE_GetToken(false)
                ASE_GetToken(false)
                if (pMesh.cvertexes == null) {
                    pMesh.cvertexes = idVec3.generateArray(pMesh.numCVertexes)
                }
                pMesh.cvertexes!![ase!!.currentVertex][0] = atof(ase!!.token!!)
                ASE_GetToken(false)
                pMesh.cvertexes!![ase!!.currentVertex][1] = atof(ase!!.token!!)
                ASE_GetToken(false)
                pMesh.cvertexes!![ase!!.currentVertex][2] = atof(ase!!.token!!)
                ase!!.currentVertex++
                if (ase!!.currentVertex > pMesh.numCVertexes) {
                    Common.common.Error("ase.currentVertex > pMesh.numCVertexes")
                }
            } else {
                Common.common.Error("Unknown token '%s' while parsing MESH_CVERTLIST", (token)!!)
            }
        }

        companion object {
            val instance: ASE = ASE_KeyMESH_CVERTLIST()
        }
    }

    class ASE_KeyMESH_NORMALS private constructor() : ASE() {
        override fun run(token: String?) {
            val pMesh: aseMesh_t? = ASE_GetCurrentMesh()
            val f: aseFace_t?
            val n = idVec3()
            pMesh!!.normalsParsed = true
            if (("*MESH_FACENORMAL" == token)) {
                val num: Int
                ASE_GetToken(false)
                num = ase!!.token!!.toInt()
                if (num >= pMesh.numFaces || num < 0) {
                    Common.common.Error("MESH_NORMALS face index out of range: %d", num)
                }
                f = pMesh.faces!![ase!!.currentFace]
                if (num != ase!!.currentFace) {
                    Common.common.Error("MESH_NORMALS face index != currentFace")
                }
                ASE_GetToken(false)
                n[0] = ase!!.token!!.toFloat()
                ASE_GetToken(false)
                n[1] = ase!!.token!!.toFloat()
                ASE_GetToken(false)
                n[2] = ase!!.token!!.toFloat()
                f!!.faceNormal[0] =
                    (n[0] * pMesh.transform[0][0]) + (n[1] * pMesh.transform[1][0]) + (n[2] * pMesh.transform[2][0])
                f.faceNormal[1] =
                    (n[0] * pMesh.transform[0][1]) + (n[1] * pMesh.transform[1][1]) + (n[2] * pMesh.transform[2][1])
                f.faceNormal[2] =
                    (n[0] * pMesh.transform[0][2]) + (n[1] * pMesh.transform[1][2]) + (n[2] * pMesh.transform[2][2])
                f.faceNormal.Normalize()
                ase!!.currentFace++
            } else if (("*MESH_VERTEXNORMAL" == token)) {
                val num: Int
                var v: Int
                ASE_GetToken(false)
                num = ase!!.token!!.toInt()
                if (num >= pMesh.numVertexes || num < 0) {
                    Common.common.Error("MESH_NORMALS vertex index out of range: %d", num)
                }
                f = pMesh.faces!![ase!!.currentFace - 1]
                v = 0
                while (v < 3) {
                    if (num == f!!.vertexNum[v]) {
                        break
                    }
                    v++
                }
                if (v == 3) {
                    Common.common.Error("MESH_NORMALS vertex index doesn't match face")
                }
                ASE_GetToken(false)
                n[0] = ase!!.token!!.toFloat()
                ASE_GetToken(false)
                n[1] = ase!!.token!!.toFloat()
                ASE_GetToken(false)
                n[2] = ase!!.token!!.toFloat()

                f!!.vertexNormals[v][0] =
                    n[0] * pMesh.transform[0][0] + n[1] * pMesh.transform[1][0] + n[2] * pMesh.transform[2][0]
                f.vertexNormals[v][1] =
                    n[0] * pMesh.transform[0][1] + n[1] * pMesh.transform[1][1] + n[2] * pMesh.transform[2][1]
                f.vertexNormals[v][2] =
                    n[0] * pMesh.transform[0][2] + n[1] * pMesh.transform[1][2] + n[2] * pMesh.transform[2][2]

                f.vertexNormals[v].Normalize()
            }
        }

        companion object {
            val instance: ASE = ASE_KeyMESH_NORMALS()
        }
    }

    class ASE_KeyMESH private constructor() : ASE() {
        override fun run(token: String?) {
            val pMesh: aseMesh_t? = ASE_GetCurrentMesh()
            if (null != token) {
                when (token) {
                    "*TIMEVALUE" -> {
                        ASE_GetToken(false)
                        pMesh!!.timeValue = ase!!.token!!.toInt()
                        VERBOSE(".....timevalue: %d\n", pMesh.timeValue)
                    }

                    "*MESH_NUMVERTEX" -> {
                        ASE_GetToken(false)
                        pMesh!!.numVertexes = ase!!.token!!.toInt()
                        VERBOSE(".....num vertexes: %d\n", pMesh.numVertexes)
                    }

                    "*MESH_NUMTVERTEX" -> {
                        ASE_GetToken(false)
                        pMesh!!.numTVertexes = ase!!.token!!.toInt()
                        VERBOSE(".....num tvertexes: %d\n", pMesh.numTVertexes)
                    }

                    "*MESH_NUMCVERTEX" -> {
                        ASE_GetToken(false)
                        pMesh!!.numCVertexes = ase!!.token!!.toInt()
                        VERBOSE(".....num cvertexes: %d\n", pMesh.numCVertexes)
                    }

                    "*MESH_NUMFACES" -> {
                        ASE_GetToken(false)
                        pMesh!!.numFaces = ase!!.token!!.toInt()
                        VERBOSE(".....num faces: %d\n", pMesh.numFaces)
                    }

                    "*MESH_NUMTVFACES" -> {
                        ASE_GetToken(false)
                        pMesh!!.numTVFaces = ase!!.token!!.toInt()
                        VERBOSE(".....num tvfaces: %d\n", pMesh.numTVFaces)
                        if (pMesh.numTVFaces != pMesh.numFaces) {
                            Common.common.Error("MESH_NUMTVFACES != MESH_NUMFACES")
                        }
                    }

                    "*MESH_NUMCVFACES" -> {
                        ASE_GetToken(false)
                        pMesh!!.numCVFaces = ase!!.token!!.toInt()
                        VERBOSE(".....num cvfaces: %d\n", pMesh.numCVFaces)
                        if (pMesh.numTVFaces != pMesh.numFaces) {
                            Common.common.Error("MESH_NUMCVFACES != MESH_NUMFACES")
                        }
                    }

                    "*MESH_VERTEX_LIST" -> {
                        pMesh!!.vertexes =
                            idVec3.generateArray(pMesh.numVertexes)
                        ase!!.currentVertex = 0
                        VERBOSE((".....parsing MESH_VERTEX_LIST\n"))
                        ASE_ParseBracedBlock(ASE_KeyMESH_VERTEX_LIST.instance)
                    }

                    "*MESH_TVERTLIST" -> {
                        ase!!.currentVertex = 0
                        pMesh!!.tvertexes = Array(pMesh.numTVertexes) { idVec2() }
                        VERBOSE((".....parsing MESH_TVERTLIST\n"))
                        ASE_ParseBracedBlock(ASE_KeyMESH_TVERTLIST.instance)
                    }

                    "*MESH_CVERTLIST" -> {
                        ase!!.currentVertex = 0
                        pMesh!!.cvertexes = idVec3.generateArray(pMesh.numCVertexes)
                        VERBOSE((".....parsing MESH_CVERTLIST\n"))
                        ASE_ParseBracedBlock(ASE_KeyMESH_CVERTLIST.instance)
                    }

                    "*MESH_FACE_LIST" -> {
                        pMesh!!.faces = arrayOfNulls(pMesh.numFaces)
                        ase!!.currentFace = 0
                        VERBOSE((".....parsing MESH_FACE_LIST\n"))
                        ASE_ParseBracedBlock(ASE_KeyMESH_FACE_LIST.instance)
                    }

                    "*MESH_TFACELIST" -> {
                        if (null == pMesh!!.faces) {
                            Common.common.Error("*MESH_TFACELIST before *MESH_FACE_LIST")
                        }
                        ase!!.currentFace = 0
                        VERBOSE((".....parsing MESH_TFACE_LIST\n"))
                        ASE_ParseBracedBlock(ASE_KeyTFACE_LIST.instance)
                    }

                    "*MESH_CFACELIST" -> {
                        if (null == pMesh!!.faces) {
                            Common.common.Error("*MESH_CFACELIST before *MESH_FACE_LIST")
                        }
                        ase!!.currentFace = 0
                        VERBOSE((".....parsing MESH_CFACE_LIST\n"))
                        ASE_ParseBracedBlock(ASE_KeyCFACE_LIST.instance)
                    }

                    "*MESH_NORMALS" -> {
                        if (null == pMesh!!.faces) {
                            Common.common.Warning("*MESH_NORMALS before *MESH_FACE_LIST")
                        }
                        ase!!.currentFace = 0
                        VERBOSE((".....parsing MESH_NORMALS\n"))
                        ASE_ParseBracedBlock(ASE_KeyMESH_NORMALS.instance)
                    }
                }
            }
        }

        companion object {
            val instance: ASE = ASE_KeyMESH()
        }
    }

    class ASE_KeyMESH_ANIMATION private constructor() : ASE() {
        override fun run(token: String?) {
            val mesh: aseMesh_t

            // loads a single animation frame
            if (("*MESH" == token)) {
                VERBOSE(("...found MESH\n"))

                mesh = aseMesh_t()
                ase!!.currentMesh = mesh
                ase!!.currentObject!!.frames.Append(mesh)
                ASE_ParseBracedBlock(ASE_KeyMESH.instance)
            } else {
                Common.common.Error("Unknown token '%s' while parsing MESH_ANIMATION", (token)!!)
            }
        }

        companion object {
            val instance: ASE = ASE_KeyMESH_ANIMATION()
        }
    }

    class ASE_KeyGEOMOBJECT private constructor() : ASE() {
        override fun run(token: String?) {
            val `object`: aseObject_t?
            `object` = ase!!.currentObject
            when ("" + token) {
                "*NODE_NAME" -> {
                    ASE_GetToken(true)
                    VERBOSE(" %s\n", ase!!.token!!)
                    Copynz(`object`!!.name, (ase!!.token)!!, `object`.name.size)
                }

                "*NODE_PARENT" -> ASE_SkipRestOfLine()
                "*NODE_TM", "*TM_ANIMATION" -> ASE_ParseBracedBlock(ASE_KeyNODE_TM.instance)
                "*MESH" -> {
                    ase!!.currentMesh = ase!!.currentObject!!.mesh
                    ASE_ParseBracedBlock(ASE_KeyMESH.instance)
                }

                "*MATERIAL_REF" -> {
                    ASE_GetToken(false)
                    `object`!!.materialRef = ase!!.token!!.toInt()
                }

                "*MESH_ANIMATION" -> {
                    VERBOSE(("..found MESH_ANIMATION\n"))
                    ASE_ParseBracedBlock(ASE_KeyMESH_ANIMATION.instance)
                }

                "*PROP_MOTIONBLUR", "*PROP_CASTSHADOW", "*PROP_RECVSHADOW" -> ASE_SkipRestOfLine()
            }
        }

        companion object {
            val instance: ASE = ASE_KeyGEOMOBJECT()
        }
    }

    class ASE_KeyGROUP private constructor() : ASE() {
        override fun run(token: String?) {
            if (("*GEOMOBJECT" == token)) {
                ASE_ParseGeomObject()
            }
        }

        companion object {
            val instance: ASE = ASE_KeyGROUP()
        }
    }
}

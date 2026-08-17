/*
===========================================================================

Doom 3 GPL Source Code
Copyright (C) 1999-2011 id Software LLC, a ZeniMax Media company.

This file is part of the Doom 3 GPL Source Code ("Doom 3 Source Code").

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

Translated to Kotlin by Dr. Feederino with support of Claude Code.

===========================================================================
*/
package neo.Renderer

import neo.framework.Common
import neo.framework.FileSystem_h.fileSystem
import neo.idlib.Text.Lexer.LEXFL_NOSTRINGCONCAT
import neo.idlib.Text.Parser.idParser
import neo.idlib.Text.Str.idStr
import neo.idlib.Text.Str.va
import neo.idlib.Text.Token.idToken
import neo.idlib.containers.HashTable.idHashTable
import neo.idlib.containers.List.idList
import neo.idlib.containers.bbtocb
import neo.idlib.idException
import neo.idlib.math.DEG2RAD
import neo.idlib.math.Matrix.idMat4
import neo.idlib.math.idMath.Cos
import neo.idlib.math.idMath.Sin
import neo.idlib.math.idVec2
import neo.idlib.math.idVec3
import neo.idlib.math.idVec4
import java.nio.ByteBuffer
import java.nio.CharBuffer
import kotlin.math.abs

object Model_ma {
    var maGlobal: ma_t? = null

    /*
     ======================================================================

     Parses Maya ASCII files.

     ======================================================================
     */
    fun MA_VERBOSE(fmt: String?, vararg x: Any) {
        if (maGlobal!!.verbose) {
            Common.common.Printf((fmt)!!, *x)
        }
    }

    @Throws(idException::class)
    fun MA_ParseNodeHeader(parser: idParser, header: maNodeHeader_t) {
        val token = idToken()
        while (parser.ReadToken(token)) {
            if (0 == token.Icmp("-")) {
                parser.ReadToken(token)
                if (0 == token.Icmp("n")) {
                    parser.ReadToken(token)
                    header.name = token.toString()
                } else if (0 == token.Icmp("p")) {
                    parser.ReadToken(token)
                    header.parent = token.toString()
                }
            } else if (0 == token.Icmp(";")) {
                break
            }
        }
    }

    @Throws(idException::class)
    fun MA_ParseHeaderIndex(
        header: maAttribHeader_t, minIndex: IntArray, maxIndex: IntArray, headerType: String?, skipString: String?
    ): Boolean {
        val miniParse = idParser()
        val token = idToken()
        miniParse.LoadMemory((header.name)!!, header.name!!.length, (headerType)!!)
        if (skipString != null) {
            miniParse.SkipUntilString(skipString)
        }
        if (!miniParse.SkipUntilString("[")) { //This was just a header
            return false
        }
        minIndex[0] = miniParse.ParseInt()
        miniParse.ReadToken(token)
        if (0 == token.Icmp("]")) {
            maxIndex[0] = minIndex[0]
        } else {
            maxIndex[0] = miniParse.ParseInt()
        }
        return true
    }

    @Throws(idException::class)
    fun MA_ParseAttribHeader(parser: idParser, header: maAttribHeader_t): Boolean {
        val token = idToken()

        parser.ReadToken(token)
        if (0 == token.Icmp("-")) {
            parser.ReadToken(token)
            if (0 == token.Icmp("s")) {
                header.size = parser.ParseInt()
                parser.ReadToken(token)
            }
        }
        header.name = token.toString()
        return true
    }

    @Throws(idException::class)
    fun MA_ReadVec3(parser: idParser, vec: idVec3): Boolean {
        if (!parser.SkipUntilString("double3")) {
            throw idException(va("Maya Loader '%s': Invalid Vec3", parser.GetFileName()))
        }

        //We need to flip y and z because of the maya coordinate system
        vec.x = parser.ParseFloat()
        vec.z = parser.ParseFloat()
        vec.y = parser.ParseFloat()
        return true
    }

    fun IsNodeComplete(token: idToken): Boolean {
        return (0 == token.Icmp("createNode")) || (0 == token.Icmp("connectAttr")) || (0 == token.Icmp("select"))
    }

    @Throws(idException::class)
    fun MA_ParseTransform(parser: idParser): Boolean {
        val header = maNodeHeader_t()
        val transform: maTransform_s

        //Allocate room for the transform
        transform = maTransform_s()
        transform.scale.z = 1.0f
        transform.scale.y = transform.scale.z
        transform.scale.x = transform.scale.y

        //Get the header info from the transform
        MA_ParseNodeHeader(parser, header)

        //Read the transform attributes
        val token = idToken()
        while (parser.ReadToken(token)) {
            if (IsNodeComplete(token)) {
                parser.UnreadToken(token)
                break
            }
            if (0 == token.Icmp("setAttr")) {
                parser.ReadToken(token)
                if (0 == token.Icmp(".t")) {
                    if (!MA_ReadVec3(parser, transform.translate)) {
                        return false
                    }
                    transform.translate.y *= -1.0f
                } else if (0 == token.Icmp(".r")) {
                    if (!MA_ReadVec3(parser, transform.rotate)) {
                        return false
                    }
                } else if (0 == token.Icmp(".s")) {
                    if (!MA_ReadVec3(parser, transform.scale)) {
                        return false
                    }
                } else {
                    parser.SkipRestOfLine()
                }
            }
        }
        if (header.parent.isNotEmpty()) { //Find the parent
            val parent: Array<maTransform_s?> = arrayOfNulls(1)
            maGlobal!!.model!!.transforms.Get(header.parent, parent)
            if (parent != null) {
                transform.parent = parent[0]
            }
        }

        //Add this transform to the list
        maGlobal!!.model!!.transforms.Set(header.name, transform)
        return true
    }

    @Throws(idException::class)
    fun MA_ParseVertex(parser: idParser, header: maAttribHeader_t): Boolean {
        val pMesh: maMesh_t = maGlobal!!.currentObject!!.mesh

        if (null == pMesh.vertexes) {
            pMesh.numVertexes = header.size
            pMesh.vertexes = idVec3.generateArray(pMesh.numVertexes)
        }

        //Get the start and end index for this attribute
        val minIndex = IntArray(1)
        val maxIndex = IntArray(1)
        if (!MA_ParseHeaderIndex(header, minIndex, maxIndex, "VertexHeader", null)) { //This was just a header
            return true
        }

        //Read each vert
        for (i in minIndex[0]..maxIndex[0]) {
            pMesh.vertexes!![i].x = parser.ParseFloat()
            pMesh.vertexes!![i].z = parser.ParseFloat()
            pMesh.vertexes!![i].y = -parser.ParseFloat()
        }
        return true
    }

    @Throws(idException::class)
    fun MA_ParseVertexTransforms(parser: idParser, header: maAttribHeader_t): Boolean {
        val pMesh: maMesh_t = maGlobal!!.currentObject!!.mesh
        val token = idToken()

        //Allocate enough space for all the verts if this is the first attribute for verticies
        if (null == pMesh.vertTransforms) {
            if (header.size == 0) {
                header.size = 1
            }
            pMesh.numVertTransforms = header.size
            pMesh.vertTransforms = Array(pMesh.numVertTransforms) { idVec4() }
            pMesh.nextVertTransformIndex = 0
        }

        //Get the start and end index for this attribute
        val minIndex = IntArray(1)
        val maxIndex = IntArray(1)
        if (!MA_ParseHeaderIndex(header, minIndex, maxIndex, "VertexTransformHeader", null)) { //This was just a header
            return true
        }
        parser.ReadToken(token)
        if (0 == token.Icmp("-")) {
            val tk2 = idToken()
            parser.ReadToken(tk2)
            if (0 == tk2.Icmp("type")) {
                parser.SkipUntilString("float3")
            } else {
                parser.UnreadToken(tk2)
                parser.UnreadToken(token)
            }
        } else {
            parser.UnreadToken(token)
        }

        //Read each vert
        for (i in minIndex[0]..maxIndex[0]) {
            pMesh.vertTransforms!![pMesh.nextVertTransformIndex].x = parser.ParseFloat()
            pMesh.vertTransforms!![pMesh.nextVertTransformIndex].z = parser.ParseFloat()
            pMesh.vertTransforms!![pMesh.nextVertTransformIndex].y = -parser.ParseFloat()

            //w hold the vert index
            pMesh.vertTransforms!![pMesh.nextVertTransformIndex].w = i.toFloat()
            pMesh.nextVertTransformIndex++
        }
        return true
    }

    @Throws(idException::class)
    fun MA_ParseEdge(parser: idParser, header: maAttribHeader_t): Boolean {
        val pMesh: maMesh_t = maGlobal!!.currentObject!!.mesh

        if (null == pMesh.edges) {
            pMesh.numEdges = header.size
            pMesh.edges = idVec3.generateArray(pMesh.numEdges)
        }

        //Get the start and end index for this attribute
        val minIndex = IntArray(1)
        val maxIndex = IntArray(1)
        if (!MA_ParseHeaderIndex(header, minIndex, maxIndex, "EdgeHeader", null)) { //This was just a header
            return true
        }

        //Read each vert
        for (i in minIndex[0]..maxIndex[0]) {
            pMesh.edges!![i].x = parser.ParseFloat()
            pMesh.edges!![i].y = parser.ParseFloat()
            pMesh.edges!![i].z = parser.ParseFloat()
        }
        return true
    }

    @Throws(idException::class)
    fun MA_ParseNormal(parser: idParser, header: maAttribHeader_t): Boolean {
        val pMesh: maMesh_t = maGlobal!!.currentObject!!.mesh
        val token = idToken()

        //Allocate enough space for all the verts if this is the first attribute for verticies
        if (null == pMesh.normals) {
            pMesh.numNormals = header.size
            pMesh.normals = idVec3.generateArray(pMesh.numNormals)
        }

        //Get the start and end index for this attribute
        val minIndex = IntArray(1)
        val maxIndex = IntArray(1)
        if (!MA_ParseHeaderIndex(header, minIndex, maxIndex, "NormalHeader", null)) { //This was just a header
            return true
        }
        parser.ReadToken(token)
        if (0 == token.Icmp("-")) {
            val tk2 = idToken()
            parser.ReadToken(tk2)
            if (0 == tk2.Icmp("type")) {
                parser.SkipUntilString("float3")
            } else {
                parser.UnreadToken(tk2)
                parser.UnreadToken(token)
            }
        } else {
            parser.UnreadToken(token)
        }

        //Read each vert
        for (i in minIndex[0]..maxIndex[0]) {
            pMesh.normals!![i].x = parser.ParseFloat()

            //Adjust the normals for the change in coordinate systems
            pMesh.normals!![i].z = parser.ParseFloat()
            pMesh.normals!![i].y = -parser.ParseFloat()
            pMesh.normals!![i].Normalize()
        }
        pMesh.normalsParsed = true
        pMesh.nextNormal = 0
        return true
    }

    @Throws(idException::class)
    fun MA_ParseFace(parser: idParser, header: maAttribHeader_t): Boolean {
        val pMesh: maMesh_t = maGlobal!!.currentObject!!.mesh
        val token = idToken()

        //Allocate enough space for all the verts if this is the first attribute for verticies
        if (null == pMesh.faces) {
            pMesh.numFaces = header.size
            pMesh.faces = Array(pMesh.numFaces) { maFace_t() }
        }

        //Get the start and end index for this attribute
        val minIndex = IntArray(1)
        val maxIndex = IntArray(1)
        if (!MA_ParseHeaderIndex(header, minIndex, maxIndex, "FaceHeader", null)) { //This was just a header
            return true
        }

        //Read the face data
        var currentFace: Int = minIndex[0] - 1
        while (parser.ReadToken(token)) {
            if (IsNodeComplete(token)) {
                parser.UnreadToken(token)
                break
            }
            if (0 == token.Icmp("f")) {
                val count: Int = parser.ParseInt()
                if (count != 3) {
                    throw idException(va("Maya Loader '%s': Face is not a triangle.", parser.GetFileName()))
                } //Increment the face number because a new face always starts with an "f" token
                currentFace++

                //We cannot reorder edges until later because the normal processing
                //assumes the edges are in the original order
                pMesh.faces!![currentFace]!!.edge[0] = parser.ParseInt()
                pMesh.faces!![currentFace]!!.edge[1] = parser.ParseInt()
                pMesh.faces!![currentFace]!!.edge[2] = parser.ParseInt()

                //Some more init stuff
                pMesh.faces!![currentFace]!!.vertexColors[2] = -1
                pMesh.faces!![currentFace]!!.vertexColors[1] = pMesh.faces!![currentFace]!!.vertexColors[2]
                pMesh.faces!![currentFace]!!.vertexColors[0] = pMesh.faces!![currentFace]!!.vertexColors[1]
            } else if (0 == token.Icmp("mu")) {
                parser.ParseInt()
                val count: Int = parser.ParseInt()
                if (count != 3) {
                    throw idException(va("Maya Loader '%s': Invalid texture coordinates.", parser.GetFileName()))
                }
                pMesh.faces!![currentFace]!!.tVertexNum[0] = parser.ParseInt()
                pMesh.faces!![currentFace]!!.tVertexNum[1] = parser.ParseInt()
                pMesh.faces!![currentFace]!!.tVertexNum[2] = parser.ParseInt()
            } else if (0 == token.Icmp("mf")) {
                val count: Int = parser.ParseInt()
                if (count != 3) {
                    throw idException(va("Maya Loader '%s': Invalid texture coordinates.", parser.GetFileName()))
                }
                pMesh.faces!![currentFace]!!.tVertexNum[0] = parser.ParseInt()
                pMesh.faces!![currentFace]!!.tVertexNum[1] = parser.ParseInt()
                pMesh.faces!![currentFace]!!.tVertexNum[2] = parser.ParseInt()
            } else if (0 == token.Icmp("fc")) {
                val count: Int = parser.ParseInt()
                if (count != 3) {
                    throw idException(va("Maya Loader '%s': Invalid vertex color.", parser.GetFileName()))
                }
                pMesh.faces!![currentFace]!!.vertexColors[0] = parser.ParseInt()
                pMesh.faces!![currentFace]!!.vertexColors[1] = parser.ParseInt()
                pMesh.faces!![currentFace]!!.vertexColors[2] = parser.ParseInt()
            }
        }
        return true
    }

    @Throws(idException::class)
    fun MA_ParseColor(parser: idParser, header: maAttribHeader_t): Boolean {
        val pMesh: maMesh_t = maGlobal!!.currentObject!!.mesh

        if (null == pMesh.colors) {
            pMesh.numColors = header.size
            pMesh.colors = ByteArray(pMesh.numColors * 4)
        }

        //Get the start and end index for this attribute
        val minIndex = IntArray(1)
        val maxIndex = IntArray(1)
        if (!MA_ParseHeaderIndex(header, minIndex, maxIndex, "ColorHeader", null)) { //This was just a header
            return true
        }

        //Read each vert
        for (i in minIndex[0]..maxIndex[0]) {
            pMesh.colors!![i * 4 + 0] = (parser.ParseFloat() * 255).toInt().toByte()
            pMesh.colors!![i * 4 + 1] = (parser.ParseFloat() * 255).toInt().toByte()
            pMesh.colors!![i * 4 + 2] = (parser.ParseFloat() * 255).toInt().toByte()
            pMesh.colors!![i * 4 + 3] = (parser.ParseFloat() * 255).toInt().toByte()
        }
        return true
    }

    @Throws(idException::class)
    fun MA_ParseTVert(parser: idParser, header: maAttribHeader_t): Boolean {
        val pMesh: maMesh_t = maGlobal!!.currentObject!!.mesh
        val token = idToken()

        //This is not the texture coordinates. It is just the name so ignore it
        if (header.name!!.contains("uvsn")) {
            return true
        }

        //Allocate enough space for all the data
        if (null == pMesh.tvertexes) {
            pMesh.numTVertexes = header.size
            pMesh.tvertexes = Array(pMesh.numTVertexes) { idVec2() }
        }

        //Get the start and end index for this attribute
        val minIndex = IntArray(1)
        val maxIndex = IntArray(1)
        if (!MA_ParseHeaderIndex(header, minIndex, maxIndex, "TextureCoordHeader", "uvsp")) { //This was just a header
            return true
        }
        parser.ReadToken(token)
        if (0 == token.Icmp("-")) {
            val tk2 = idToken()
            parser.ReadToken(tk2)
            if (0 == tk2.Icmp("type")) {
                parser.SkipUntilString("float2")
            } else {
                parser.UnreadToken(tk2)
                parser.UnreadToken(token)
            }
        } else {
            parser.UnreadToken(token)
        }

        //Read each tvert
        for (i in minIndex[0]..maxIndex[0]) {
            pMesh.tvertexes!![i].x = parser.ParseFloat()
            pMesh.tvertexes!![i].y = 1.0f - parser.ParseFloat()
        }
        return true
    }

    /*
     *	Quick check to see if the vert participates in a shared normal
     */
    fun MA_QuickIsVertShared(faceIndex: Int, vertIndex: Int): Boolean {
        val pMesh: maMesh_t = maGlobal!!.currentObject!!.mesh
        val vertNum: Int = pMesh.faces!![faceIndex]!!.vertexNum[vertIndex]
        for (i in 0..2) {
            var edge: Int = pMesh.faces!![faceIndex]!!.edge[i]
            if (edge < 0) {
                edge = (abs(edge.toFloat()) - 1).toInt()
            }
            if (pMesh.edges!![edge].z == 1.0f && (pMesh.edges!![edge].x == vertNum.toFloat() || pMesh.edges!![edge].y == vertNum.toFloat())) {
                return true
            }
        }
        return false
    }

    fun MA_GetSharedFace(faceIndex: Int, vertIndex: Int, sharedFace: IntArray, sharedVert: IntArray) {
        val pMesh: maMesh_t = maGlobal!!.currentObject!!.mesh
        val vertNum: Int = pMesh.faces!![faceIndex]!!.vertexNum[vertIndex]
        sharedFace[0] = -1
        sharedVert[0] = -1

        //Find a shared edge on this face that contains the specified vert
        for (edgeIndex in 0..2) {
            var edge: Int = pMesh.faces!![faceIndex]!!.edge[edgeIndex]
            if (edge < 0) {
                edge = (abs(edge.toFloat()) - 1).toInt()
            }
            if (pMesh.edges!![edge].z == 1.0f && (pMesh.edges!![edge].x == vertNum.toFloat() || pMesh.edges!![edge].y == vertNum.toFloat())) {
                for (i in 0 until faceIndex) {
                    for (j in 0..2) {
                        if (pMesh.faces!![i]!!.vertexNum[j] == vertNum) {
                            sharedFace[0] = i
                            sharedVert[0] = j
                            break
                        }
                    }
                }
            }
            if (sharedFace[0] != -1) {
                break
            }
        }
    }

    @Throws(idException::class)
    fun MA_ParseMesh(parser: idParser) {
        val `object`: maObject_t
        `object` = maObject_t()
        maGlobal!!.model!!.objects.Append(`object`)
        maGlobal!!.currentObject = `object`
        `object`.materialRef = -1

        //Get the header info from the mesh
        val nodeHeader = maNodeHeader_t()
        MA_ParseNodeHeader(parser, nodeHeader)

        //Find my parent
        if (nodeHeader.parent.isNotEmpty()) { //Find the parent
            val parent: Array<maTransform_s?> = arrayOfNulls(1)
            maGlobal!!.model!!.transforms.Get(nodeHeader.parent, parent)
            if (parent[0] != null) {
                maGlobal!!.currentObject!!.mesh.transform = parent[0]
            }
        }
        `object`.name = nodeHeader.name

        //Read the transform attributes
        val token = idToken()
        while (parser.ReadToken(token)) {
            if (IsNodeComplete(token)) {
                parser.UnreadToken(token)
                break
            }
            if (0 == token.Icmp("setAttr")) {
                val attribHeader = maAttribHeader_t()
                MA_ParseAttribHeader(parser, attribHeader)
                if (attribHeader.name!!.contains(".vt")) {
                    MA_ParseVertex(parser, attribHeader)
                } else if (attribHeader.name!!.contains(".ed")) {
                    MA_ParseEdge(parser, attribHeader)
                } else if (attribHeader.name!!.contains(".pt")) {
                    MA_ParseVertexTransforms(parser, attribHeader)
                } else if (attribHeader.name!!.contains(".n")) {
                    MA_ParseNormal(parser, attribHeader)
                } else if (attribHeader.name!!.contains(".fc")) {
                    MA_ParseFace(parser, attribHeader)
                } else if (attribHeader.name!!.contains(".clr")) {
                    MA_ParseColor(parser, attribHeader)
                } else if (attribHeader.name!!.contains(".uvst")) {
                    MA_ParseTVert(parser, attribHeader)
                } else {
                    parser.SkipRestOfLine()
                }
            }
        }
        val pMesh: maMesh_t = maGlobal!!.currentObject!!.mesh

        //Get the verts from the edge
        for (i in 0 until pMesh.numFaces) {
            for (j in 0..2) {
                var edge: Int = pMesh.faces!![i]!!.edge[j]
                if (edge < 0) {
                    edge = (abs(edge.toFloat()) - 1).toInt()
                    pMesh.faces!![i]!!.vertexNum[j] = pMesh.edges!![edge].y.toInt()
                } else {
                    pMesh.faces!![i]!!.vertexNum[j] = pMesh.edges!![edge].x.toInt()
                }
            }
        }

        //Get the normals
        if (pMesh.normalsParsed) {
            for (i in 0 until pMesh.numFaces) {
                for (j in 0..2) {

                    //Is this vertex shared
                    val sharedFace: IntArray = intArrayOf(-1)
                    val sharedVert: IntArray = intArrayOf(-1)
                    if (MA_QuickIsVertShared(i, j)) {
                        MA_GetSharedFace(i, j, sharedFace, sharedVert)
                    }
                    if (sharedFace[0] != -1) { //Get the normal from the share
                        pMesh.faces!![i]!!.vertexNormals[j]!!.set((pMesh.faces!![sharedFace[0]]!!.vertexNormals[sharedVert[0]])!!)
                    } else { //The vertex is not shared so get the next normal
                        if (pMesh.nextNormal >= pMesh.numNormals) { //We are using more normals than exist
                            throw idException(va("Maya Loader '%s': Invalid Normals Index.", parser.GetFileName()))
                        }
                        pMesh.faces!![i]!!.vertexNormals[j]!!.set(pMesh.normals!![pMesh.nextNormal])
                        pMesh.nextNormal++
                    }
                }
            }
        }

        //Now that the normals are good...lets reorder the verts to make the tris face the right way
        for (i in 0 until pMesh.numFaces) {
            var tmp: Int = pMesh.faces!![i]!!.vertexNum[1]
            pMesh.faces!![i]!!.vertexNum[1] = pMesh.faces!![i]!!.vertexNum[2]
            pMesh.faces!![i]!!.vertexNum[2] = tmp
            val tmpVec = idVec3((pMesh.faces!![i]!!.vertexNormals[1])!!)
            pMesh.faces!![i]!!.vertexNormals[1]!!.set((pMesh.faces!![i]!!.vertexNormals[2])!!)
            pMesh.faces!![i]!!.vertexNormals[2]!!.set(tmpVec)
            tmp = pMesh.faces!![i]!!.tVertexNum[1]
            pMesh.faces!![i]!!.tVertexNum[1] = pMesh.faces!![i]!!.tVertexNum[2]
            pMesh.faces!![i]!!.tVertexNum[2] = tmp
            tmp = pMesh.faces!![i]!!.vertexColors[1]
            pMesh.faces!![i]!!.vertexColors[1] = pMesh.faces!![i]!!.vertexColors[2]
            pMesh.faces!![i]!!.vertexColors[2] = tmp
        }

        //Now apply the pt transformations
        for (i in 0 until pMesh.numVertTransforms) {
            val idx = pMesh.vertTransforms!![i].w.toInt()
            if (idx < 0 || idx >= pMesh.numVertexes) {
                Common.common.Warning(
                    "Model %s tried to set an out-of-bounds vertex transform " + "(%d, but max vert. index is %d)!",
                    parser.GetFileName().toString(),
                    idx,
                    pMesh.numVertexes - 1
                )
                continue
            }
            pMesh.vertexes!![idx].plusAssign(pMesh.vertTransforms!![i].ToVec3())
        }
        MA_VERBOSE((va("MESH %s - parent %s\n", nodeHeader.name, nodeHeader.parent)))
        MA_VERBOSE((va("\tverts:%d\n", maGlobal!!.currentObject!!.mesh.numVertexes)))
        MA_VERBOSE((va("\tfaces:%d\n", maGlobal!!.currentObject!!.mesh.numFaces)))
    }

    @Throws(idException::class)
    fun MA_ParseFileNode(parser: idParser) {

        //Get the header info from the node
        val header = maNodeHeader_t()
        MA_ParseNodeHeader(parser, header)

        //Read the transform attributes
        val token = idToken()
        while (parser.ReadToken(token)) {
            if (IsNodeComplete(token)) {
                parser.UnreadToken(token)
                break
            }
            if (0 == token.Icmp("setAttr")) {
                val attribHeader = maAttribHeader_t()
                MA_ParseAttribHeader(parser, attribHeader)
                if (attribHeader.name!!.contains(".ftn")) {
                    parser.SkipUntilString("string")
                    parser.ReadToken(token)
                    if (0 == token.Icmp("(")) {
                        parser.ReadToken(token)
                    }
                    var fileNode: maFileNode_t
                    fileNode = maFileNode_t()
                    fileNode.name = header.name
                    fileNode.path = token.toString()
                    maGlobal!!.model!!.fileNodes.Set(fileNode.name, fileNode)
                } else {
                    parser.SkipRestOfLine()
                }
            }
        }
    }

    fun MA_ParseMaterialNode(parser: idParser) {

        //Get the header info from the node
        val header = maNodeHeader_t()
        MA_ParseNodeHeader(parser, header)
        val matNode = maMaterialNode_s()
        matNode.name = header.name
        maGlobal!!.model!!.materialNodes.Set(matNode.name, matNode)
    }

    @Throws(idException::class)
    fun MA_ParseCreateNode(parser: idParser) {
        val token = idToken()
        parser.ReadToken(token)
        if (0 == token.Icmp("transform")) {
            MA_ParseTransform(parser)
        } else if (0 == token.Icmp("mesh")) {
            MA_ParseMesh(parser)
        } else if (0 == token.Icmp("file")) {
            MA_ParseFileNode(parser)
        } else if ((0 == token.Icmp("shadingEngine")) || (0 == token.Icmp("lambert")) || (0 == token.Icmp("phong")) || (0 == token.Icmp(
                "blinn"
            ))
        ) {
            MA_ParseMaterialNode(parser)
        }
    }

    fun MA_AddMaterial(materialName: String?): Int {
        val destNode: Array<maMaterialNode_s?> = arrayOfNulls(1)
        maGlobal!!.model!!.materialNodes.Get(materialName, destNode)
        if (destNode[0] != null) {
            var matNode: maMaterialNode_s? = destNode[0]

            //Iterate down the tree until we get a file
            while (matNode != null && null == matNode.file) {
                matNode = matNode.child
            }
            if (matNode != null && matNode.file != null) {

                //Got the file
                val material: maMaterial_t
                material = maMaterial_t()

                //Remove the OS stuff
                val qPath: String
                qPath = fileSystem.OSPathToRelativePath(matNode.file!!.path!!)
                material.name = qPath
                maGlobal!!.model!!.materials.Append(material)
                return maGlobal!!.model!!.materials.Num() - 1
            }
        }
        return -1
    }

    @Throws(idException::class)
    fun MA_ParseConnectAttr(parser: idParser): Boolean {
        var temp: idStr
        val srcName: idStr
        val srcType: idStr
        val destName: idStr
        val destType: idStr
        val token = idToken()
        parser.ReadToken(token)
        temp = token
        var dot: Int = temp.Find(".")
        if (dot == -1) {
            throw idException(va("Maya Loader '%s': Invalid Connect Attribute.", parser.GetFileName()))
        }
        srcName = temp.Left(dot)
        srcType = temp.Right(temp.Length() - dot - 1)
        parser.ReadToken(token)
        temp = token
        dot = temp.Find(".")
        if (dot == -1) {
            throw idException(va("Maya Loader '%s': Invalid Connect Attribute.", parser.GetFileName()))
        }
        destName = temp.Left(dot)
        destType = temp.Right(temp.Length() - dot - 1)
        if (srcType.Find("oc") != -1) {

            //Is this attribute a material node attribute
            val matNode: Array<maMaterialNode_s?> = arrayOfNulls(1)
            maGlobal!!.model!!.materialNodes.Get(srcName.toString(), matNode)
            if (matNode[0] != null) {
                val destNode: Array<maMaterialNode_s?> = arrayOfNulls(1)
                maGlobal!!.model!!.materialNodes.Get(destName.toString(), destNode)
                if (destNode[0] != null) {
                    destNode[0]!!.child = matNode[0]
                }
            }

            //Is this attribute a file node
            val fileNode: Array<maFileNode_t?> = arrayOfNulls(1)
            maGlobal!!.model!!.fileNodes.Get(srcName.toString(), fileNode)
            if (fileNode[0] != null) {
                val destNode: Array<maMaterialNode_s?> = arrayOfNulls(1)
                maGlobal!!.model!!.materialNodes.Get(destName.toString(), destNode)
                if (destNode[0] != null) {
                    destNode[0]!!.file = fileNode[0]
                }
            }
        }
        if (srcType.Find("iog") != -1) { //Is this an attribute for one of our meshes
            for (i in 0 until maGlobal!!.model!!.objects.Num()) {
                if ((maGlobal!!.model!!.objects[i]!!.name == srcName.toString())) { //maGlobal.model.objects.get(i).materialRef = MA_AddMaterial(destName);
                    maGlobal!!.model!!.objects[i]!!.materialName = destName.toString()
                    break
                }
            }
        }
        return true
    }

    fun MA_BuildScale(mat: idMat4, x: Float, y: Float, z: Float) {
        mat.Identity()
        mat[0][0] = x
        mat[1][1] = y
        mat[2][2] = z
    }

    fun MA_BuildAxisRotation(mat: idMat4, ang: Float, axis: Int) {
        val sinAng: Float = Sin(ang)
        val cosAng: Float = Cos(ang)
        mat.Identity()
        when (axis) {
            0 -> {
                mat[1][1] = cosAng
                mat[1][2] = sinAng
                mat[2][1] = -sinAng
                mat[2][2] = cosAng
            }

            1 -> {
                mat[0][0] = cosAng
                mat[0][2] = -sinAng
                mat[2][0] = sinAng
                mat[2][2] = cosAng
            }

            2 -> {
                mat[0][0] = cosAng
                mat[0][1] = sinAng
                mat[1][0] = -sinAng
                mat[1][1] = cosAng
            }
        }
    }

    fun MA_ApplyTransformation(model: maModel_s?) {
        for (i in 0 until model!!.objects.Num()) {
            val mesh: maMesh_t = model.objects[i]!!.mesh
            var transform: maTransform_s? = mesh.transform
            while (transform != null) {
                val rotx = idMat4()
                val roty = idMat4()
                val rotz = idMat4()
                val scale = idMat4()
                rotx.Identity()
                roty.Identity()
                rotz.Identity()
                if (abs(transform.rotate.x) > 0.0f) {
                    MA_BuildAxisRotation(rotx, DEG2RAD(-transform.rotate.x), 0)
                }
                if (abs(transform.rotate.y) > 0.0f) {
                    MA_BuildAxisRotation(roty, DEG2RAD(transform.rotate.y), 1)
                }
                if (abs(transform.rotate.z) > 0.0f) {
                    MA_BuildAxisRotation(rotz, DEG2RAD(-transform.rotate.z), 2)
                }
                MA_BuildScale(scale, transform.scale.x, transform.scale.y, transform.scale.z)

                //Apply the transformation to each vert
                for (j in 0 until mesh.numVertexes) {
                    mesh.vertexes!![j].set(scale.times((mesh.vertexes!![j])))
                    mesh.vertexes!![j].set(rotx.times((mesh.vertexes!![j])))
                    mesh.vertexes!![j].set(rotz.times((mesh.vertexes!![j])))
                    mesh.vertexes!![j].set(roty.times((mesh.vertexes!![j])))
                    mesh.vertexes!![j].set(mesh.vertexes!![j].plus(transform.translate))
                }
                transform = transform.parent
            }
        }
    }

    /*
     =================
     MA_Parse
     =================
     */
    @Throws(idException::class)
    fun MA_Parse(buffer: CharBuffer, filename: String?, verbose: Boolean): maModel_s? {
        maGlobal = ma_t()
        maGlobal!!.verbose = verbose
        maGlobal!!.currentObject = null

        // NOTE: using new operator because aseModel_t contains idList class objects
        maGlobal!!.model = maModel_s()
        maGlobal!!.model!!.objects.Resize(32, 32)
        maGlobal!!.model!!.materials.Resize(32, 32)
        val parser = idParser()
        parser.SetFlags(LEXFL_NOSTRINGCONCAT)
        parser.LoadMemory(buffer, buffer.length, (filename)!!)
        val token = idToken()
        while (parser.ReadToken(token)) {
            if (0 == token.Icmp("createNode")) {
                MA_ParseCreateNode(parser)
            } else if (0 == token.Icmp("connectAttr")) {
                MA_ParseConnectAttr(parser)
            }
        }

        //Resolve The Materials
        for (i in 0 until maGlobal!!.model!!.objects.Num()) {
            maGlobal!!.model!!.objects[i]!!.materialRef = MA_AddMaterial(maGlobal!!.model!!.objects[i]!!.materialName)
        }

        //Apply Transformation
        MA_ApplyTransformation(maGlobal!!.model)
        return maGlobal!!.model
    }

    /*
     =================
     MA_Load
     =================
     */
    fun MA_Load(fileName: String?): maModel_s? {
        val buf: Array<ByteBuffer?> = arrayOf(null)
        val timeStamp = LongArray(1)
        var ma: maModel_s?
        fileSystem.ReadFile(fileName!!, buf, timeStamp)
        if (null == buf[0]) {
            return null
        }
        try {
            ma = MA_Parse(bbtocb(buf[0]!!), fileName, false)
            ma!!.timeStamp = timeStamp
        } catch (e: idException) {
            Common.common.Warning("%s", e.error)
            if (maGlobal!!.model != null) {
                MA_Free(maGlobal!!.model)
            }
            ma = null
        }

        return ma
    }

    /*
     =================
     MA_Free
     =================
     */
    fun MA_Free(ma: maModel_s?) {
        if (ma == null) {
            return
        }
        ma.objects.Clear()
        ma.materials.Clear()
        ma.transforms.Clear()
        ma.fileNodes.Clear()
        ma.materialNodes.Clear()
    }

    /*
     ===============================================================================

     MA loader. (Maya Ascii Format)

     ===============================================================================
     */
    class maNodeHeader_t {
        var name: String = ""
        var parent: String = ""
    }

    class maAttribHeader_t {
        var name: String? = null
        var size: Int = 0
    }

    class maTransform_s {
        var parent: maTransform_s? = null
        val rotate: idVec3 = idVec3()
        val scale: idVec3 = idVec3()
        val translate: idVec3 = idVec3()
    }

    class maFace_t {
        var edge: IntArray = IntArray(3)
        var tVertexNum: IntArray = IntArray(3)
        var vertexColors: IntArray = IntArray(3)
        val vertexNormals: Array<idVec3?> = idVec3.generateArray(3) as Array<idVec3?>
        var vertexNum: IntArray = IntArray(3)
    }

    class maMesh_t {
        var colors: ByteArray? = null
        var edges: Array<idVec3>? = null
        var faces: Array<maFace_t?>? = null
        var nextNormal: Int = 0
        var nextVertTransformIndex: Int = 0
        var normals: Array<idVec3>? = null
        var normalsParsed: Boolean = false

        //
        //Colors
        var numColors: Int = 0

        //
        //Edges
        var numEdges: Int = 0

        //
        //Faces
        var numFaces: Int = 0

        //
        //Normals
        var numNormals: Int = 0

        //
        //Texture Coordinates
        var numTVertexes: Int = 0
        var numVertTransforms: Int = 0

        //
        //Verts
        var numVertexes: Int = 0

        //Transform to be applied
        var transform: maTransform_s? = null
        var tvertexes: Array<idVec2>? = null
        var vertTransforms: Array<idVec4>? = null
        var vertexes: Array<idVec3>? = null
    }

    class maMaterial_t {
        var angle: Float = 0.0f // in clockwise radians
        var name: String? = null
        var uOffset: Float = 0.0f
        var vOffset: Float = 0.0f // max lets you offset by material without changing texCoords
        var uTiling: Float = 0.0f
        var vTiling: Float = 0.0f // multiply tex coords by this
    }

    class maObject_t {
        var materialName: String? = null
        var materialRef: Int = 0
        var mesh: maMesh_t = maMesh_t()
        var name: String? = null
    }

    class maFileNode_t {
        var name: String? = null
        var path: String? = null
    }

    class maMaterialNode_s {
        var child: maMaterialNode_s? = null
        var file: maFileNode_t? = null
        var name: String? = null
    }

    class maModel_s {
        //Material Resolution
        var fileNodes: idHashTable<maFileNode_t> = idHashTable()
        var materialNodes: idHashTable<maMaterialNode_s> = idHashTable()
        val materials: idList<maMaterial_t?> = idList()
        val objects: idList<maObject_t?> = idList()
        var  /*ID_TIME_T*/timeStamp: LongArray = LongArray(1)
        var transforms: idHashTable<maTransform_s> = idHashTable()
    }

    // working variables used during parsing
    class ma_t {
        var currentObject: maObject_t? = null
        var model: maModel_s? = null
        var verbose: Boolean = false
    }
}

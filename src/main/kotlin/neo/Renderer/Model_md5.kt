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

import neo.Renderer.Material.idMaterial
import neo.Renderer.Model.dynamicModel_t
import neo.Renderer.Model.idMD5Joint
import neo.Renderer.Model.idRenderModel
import neo.Renderer.Model.modelSurface_s
import neo.Renderer.Model.srfTriangles_s
import neo.Renderer.ModelOverlay.idRenderModelOverlay
import neo.Renderer.Model_local.idRenderModelStatic
import neo.Renderer.RenderWorld.renderEntity_s
import neo.framework.Common
import neo.framework.DeclManager
import neo.framework.FileSystem_h.fileSystem
import neo.framework.Session
import neo.idlib.*
import neo.idlib.BV.idBounds
import neo.idlib.Text.Lexer.LEXFL_ALLOWPATHNAMES
import neo.idlib.Text.Lexer.LEXFL_NOSTRINGESCAPECHARS
import neo.idlib.Text.Lexer.idLexer
import neo.idlib.Text.Str.idStr
import neo.idlib.Text.Str.idStr.Companion.Icmp
import neo.idlib.Text.Token.idToken
import neo.idlib.containers.CInt
import neo.idlib.containers.List.idList
import neo.idlib.geometry.DrawVert.idDrawVert
import neo.idlib.geometry.JointTransform.idJointMat
import neo.idlib.geometry.JointTransform.idJointQuat
import neo.idlib.math.*

object Model_md5 {
    val MD5_SnapshotName: String = "_MD5_Snapshot_"

    var c_numVerts: Int = 0
    var c_numWeightJoints: Int = 0
    var c_numWeights: Int = 0

    internal class vertexWeight_s {
        var joint: Int = 0
        var jointWeight: Float = 0.0f
        val offset: idVec3 = idVec3()
        var vert: Int = 0
    }

    /*
     ===============================================================================

     MD5 animated model

     ===============================================================================
     */
    class idMD5Mesh {
        var deformInfo: deformInfo_s? = null
        var numTris: Int = 0
        var numWeights: Int = 0
        private var scaledWeights: Array<idVec4?>? = null
        var shader: idMaterial? = null
        var surfaceNum: Int = 0
        val texCoords: idList<idVec2> = idList()
        private var weightIndex: IntArray? = null

        @Throws(idException::class)
        fun ParseMesh(parser: idLexer, numJoints: Int, joints: Array<idJointMat?>) {
            val token = idToken()
            val name = idToken()
            var num: Int
            var count: Int
            var jointnum: Int
            val shaderName: idStr
            var i: Int
            var j: Int
            val tris: idList<Int> = idList()
            val firstWeightForVertex: idList<Int> = idList()
            val numWeightsForVertex: idList<Int> = idList()
            var maxweight: Int
            val tempWeights: idList<vertexWeight_s> = idList()
            parser.ExpectTokenString("{")

            //
            // parse name
            //
            if (parser.CheckTokenString("name")) {
                parser.ReadToken(name)
            }

            //
            // parse shader
            //
            parser.ExpectTokenString("shader")
            parser.ReadToken(token)
            shaderName = token
            shader = DeclManager.declManager.FindMaterial(shaderName)

            //
            // parse texture coordinates
            //
            parser.ExpectTokenString("numverts")
            count = parser.ParseInt()
            if (count < 0) {
                parser.Error("Invalid size: %s", token.toString())
            }
            texCoords.SetNum(count)
            firstWeightForVertex.SetNum(count)
            numWeightsForVertex.SetNum(count)
            numWeights = 0
            maxweight = 0
            i = 0
            while (i < texCoords.Num()) {
                parser.ExpectTokenString("vert")
                parser.ParseInt()
                parser.Parse1DMatrix(2, texCoords.set(i, idVec2()))
                firstWeightForVertex[i] = parser.ParseInt()
                numWeightsForVertex[i] = parser.ParseInt()
                if (0 == numWeightsForVertex[i]) {
                    parser.Error("Vertex without any joint weights.")
                }
                numWeights += numWeightsForVertex[i]
                if (numWeightsForVertex[i] + firstWeightForVertex[i] > maxweight) {
                    maxweight = numWeightsForVertex[i] + firstWeightForVertex[i]
                }
                i++
            }

            //
            // parse tris
            //
            parser.ExpectTokenString("numtris")
            count = parser.ParseInt()
            if (count < 0) {
                parser.Error("Invalid size: %d", count)
            }
            tris.SetNum(count * 3)
            numTris = count
            i = 0
            while (i < count) {
                parser.ExpectTokenString("tri")
                parser.ParseInt()
                tris[i * 3 + 0] = parser.ParseInt()
                tris[i * 3 + 1] = parser.ParseInt()
                tris[i * 3 + 2] = parser.ParseInt()
                i++
            }

            //
            // parse weights
            //
            parser.ExpectTokenString("numweights")
            count = parser.ParseInt()
            if (count < 0) {
                parser.Error("Invalid size: %d", count)
            }
            if (maxweight > count) {
                parser.Warning("Vertices reference out of range weights in model (%d of %d weights).", maxweight, count)
            }
            tempWeights.SetNum(count)
            i = 0
            while (i < count) {
                parser.ExpectTokenString("weight")
                parser.ParseInt()
                jointnum = parser.ParseInt()
                if ((jointnum < 0) || (jointnum >= numJoints)) {
                    parser.Error("Joint Index out of range(%d): %d", numJoints, jointnum)
                }
                tempWeights[i] = vertexWeight_s()
                tempWeights[i].joint = jointnum
                tempWeights[i].jointWeight = parser.ParseFloat()
                parser.Parse1DMatrix(3, tempWeights[i].offset)
                i++
            }

            // create pre-scaled weights and an index for the vertex/joint lookup
            scaledWeights = arrayOfNulls(numWeights)
            weightIndex = IntArray(numWeights * 2)
            count = 0
            i = 0
            while (i < texCoords.Num()) {
                num = firstWeightForVertex[i]
                j = 0
                while (j < numWeightsForVertex[i]) {
                    scaledWeights!![count] = idVec4()
                    scaledWeights!![count]!!
                        .set(tempWeights[num].offset.times(tempWeights[num].jointWeight))
                    scaledWeights!![count]!!.w = tempWeights[num].jointWeight
                    weightIndex!![count * 2 + 0] = tempWeights[num].joint * idJointMat.SIZE
                    j++
                    num++
                    count++
                }
                weightIndex!![count * 2 - 1] = 1
                i++
            }
            tempWeights.Clear()
            numWeightsForVertex.Clear()
            firstWeightForVertex.Clear()
            parser.ExpectTokenString("}")

            // update counters
            c_numVerts += texCoords.Num()
            c_numWeights += numWeights
            c_numWeightJoints++
            i = 0
            while (i < numWeights) {
                c_numWeightJoints += weightIndex!![i * 2 + 1]
                i++
            }

            //
            // build the information that will be common to all animations of this mesh:
            // silhouette edge connectivity and normal / tangent generation information
            //
            val verts: Array<idDrawVert> = Array(texCoords.Num()) { idDrawVert() }
            i = 0
            while (i < texCoords.Num()) {
                verts[i].Clear()
                verts[i].st.set(texCoords[i])
                i++
            }
            TransformVerts(verts, joints)
            deformInfo =
                R_BuildDeformInfo(texCoords.Num(), verts, tris.Num(), tris, shader!!.UseUnsmoothedTangents())
        }

        fun UpdateSurface(ent: renderEntity_s?, entJoints: Array<idJointMat?>, surf: modelSurface_s?) {
            var i: Int
            val base: Int
            val tri: srfTriangles_s?
            tr.pc!!.c_deformedSurfaces++
            tr.pc!!.c_deformedVerts += deformInfo!!.numOutputVerts
            tr.pc!!.c_deformedIndexes += deformInfo!!.numIndexes
            surf!!.shader = shader
            if (surf.geometry != null) {
                // if the number of verts and indexes are the same we can re-use the triangle surface
                // the number of indexes must be the same to assure the correct amount of memory is allocated for the facePlanes
                if (surf.geometry!!.numVerts == deformInfo!!.numOutputVerts && surf.geometry!!.numIndexes == deformInfo!!.numIndexes) {
                    R_FreeStaticTriSurfVertexCaches(surf.geometry!!)
                } else {
                    R_FreeStaticTriSurf(surf.geometry)
                    surf.geometry = R_AllocStaticTriSurf()
                }
            } else {
                surf.geometry = R_AllocStaticTriSurf()
            }
            tri = surf.geometry

            // note that some of the data is references, and should not be freed
            tri!!.deformedSurface = true
            tri.tangentsCalculated = false
            tri.facePlanesCalculated = false
            tri.numIndexes = deformInfo!!.numIndexes
            tri.indexes = deformInfo!!.indexes
            tri.silIndexes = deformInfo!!.silIndexes
            tri.numMirroredVerts = deformInfo!!.numMirroredVerts
            tri.mirroredVerts = deformInfo!!.mirroredVerts
            tri.numDupVerts = deformInfo!!.numDupVerts
            tri.dupVerts = deformInfo!!.dupVerts
            tri.numSilEdges = deformInfo!!.numSilEdges
            tri.silEdges = deformInfo!!.silEdges as Array<Model.silEdge_t?>?
            tri.dominantTris = deformInfo!!.dominantTris as Array<Model.dominantTri_s?>?
            tri.numVerts = deformInfo!!.numOutputVerts
            if (tri.verts == null) {
                R_AllocStaticTriSurfVerts(tri, tri.numVerts)
                i = 0
                while (i < deformInfo!!.numSourceVerts) {
                    tri.verts!![i]!!.Clear()
                    tri.verts!![i]!!.st.set(texCoords[i])
                    i++
                }
            }
            if (ent!!.shaderParms[RenderWorld.SHADERPARM_MD5_SKINSCALE] != 0.0f) {
                TransformScaledVerts(
                    tri.verts as Array<idDrawVert>,
                    entJoints as Array<idJointMat>,
                    ent.shaderParms[RenderWorld.SHADERPARM_MD5_SKINSCALE]
                )
            } else {
                TransformVerts(tri.verts, entJoints)
            }

            // replicate the mirror seam vertexes
            base = deformInfo!!.numOutputVerts - deformInfo!!.numMirroredVerts
            i = 0
            while (i < deformInfo!!.numMirroredVerts) {
                tri.verts!![base + i].set(tri.verts!![deformInfo!!.mirroredVerts!![i]])
                i++
            }
            R_BoundTriSurf(tri)

            // If a surface is going to be have a lighting interaction generated, it will also have to call
            // R_DeriveTangents() to get normals, tangents, and face planes.  If it only
            // needs shadows generated, it will only have to generate face planes.  If it only
            // has ambient drawing, or is culled, no additional work will be necessary
            if (!r_useDeferredTangents!!.GetBool()) {
                // set face planes, vertex normals, tangents
                R_DeriveTangents(tri)
            }
        }

        fun CalcBounds(entJoints: Array<idJointMat?>): idBounds {
            val bounds = idBounds()
            val verts: Array<idDrawVert> = Array(texCoords.Num()) { idDrawVert() }
            TransformVerts(verts, entJoints)
            SIMDProcessor!!.MinMax(bounds[0], bounds[1], verts as Array<idDrawVert>, texCoords.Num())
            return bounds
        }

        fun NearestJoint(a: Int, b: Int, c: Int): Int {
            var i: Int
            var bestJoint: Int
            val vertNum: Int
            var weightVertNum: Int
            var bestWeight: Float

            // duplicated vertices might not have weights
            if (a >= 0 && a < texCoords.Num()) {
                vertNum = a
            } else if (b >= 0 && b < texCoords.Num()) {
                vertNum = b
            } else if (c >= 0 && c < texCoords.Num()) {
                vertNum = c
            } else {
                // all vertices are duplicates which shouldn't happen
                return 0
            }

            // find the first weight for this vertex
            weightVertNum = 0
            i = 0
            while (weightVertNum < vertNum) {
                weightVertNum += weightIndex!![i * 2 + 1]
                i++
            }

            // get the joint for the largest weight
            bestWeight = scaledWeights!![i]!!.w
            bestJoint = weightIndex!![i * 2 + 0] / idJointMat.SIZE
            while (weightIndex!![i * 2 + 1] == 0) {
                if (scaledWeights!![i]!!.w > bestWeight) {
                    bestWeight = scaledWeights!![i]!!.w
                    bestJoint = weightIndex!![i * 2 + 0] / idJointMat.SIZE
                }
                i++
            }
            return bestJoint
        }

        fun NumVerts(): Int {
            return texCoords.Num()
        }

        fun NumTris(): Int {
            return numTris
        }

        fun NumWeights(): Int {
            return numWeights
        }

        private fun TransformVerts(verts: Array<idDrawVert>?, entJoints: Array<idJointMat?>) {
            SIMDProcessor!!.TransformVerts(
                verts as Array<idDrawVert>,
                texCoords.Num(),
                entJoints as Array<idJointMat>,
                scaledWeights as Array<idVec4>,
                weightIndex!!,
                numWeights
            )
        }

        /*
         ====================
         idMD5Mesh::TransformScaledVerts

         Special transform to make the mesh seem fat or skinny.  May be used for zombie deaths
         ====================
         */
        private fun TransformScaledVerts(verts: Array<idDrawVert>, entJoints: Array<idJointMat>, scale: Float) {
            val localWeights = Array(numWeights) { i ->
                val src = this.scaledWeights!![i]!!
                val w = idVec4()
                w.x = src.x * scale
                w.y = src.y * scale
                w.z = src.z * scale
                w.w = src.w
                w
            }
            SIMDProcessor!!.TransformVerts(
                verts,
                texCoords.Num(),
                entJoints,
                localWeights,
                weightIndex!!,
                numWeights
            )
        }
    }

    class idRenderModelMD5 : idRenderModelStatic() {
        private val defaultPose: idList<idJointQuat> = idList()
        private val joints: idList<idMD5Joint?> = idList()
        private val meshes: idList<idMD5Mesh> = idList()

        override fun InitFromFile(fileName: String?) {
            name = idStr((fileName)!!)
            LoadModel()
        }

        override fun IsDynamicModel(): dynamicModel_t {
            return dynamicModel_t.DM_CACHED
        }

        /*
         ====================
         idRenderModelMD5::Bounds

         This calculates a rough bounds by using the joint radii without
         transforming all the points
         ====================
         */
        override fun Bounds(ent: renderEntity_s?): idBounds {
            if (null == ent) {
                // this is the bounds for the reference pose
                return bounds
            }
            return ent.bounds
        }

        override fun Print() {
            var i = 0
            Common.common.Printf("%s\n", name.toString())
            Common.common.Printf("Dynamic model.\n")
            Common.common.Printf("Generated smooth normals.\n")
            Common.common.Printf("    verts  tris weights material\n")
            var totalVerts = 0
            var totalTris = 0
            var totalWeights = 0
            for (mesh: idMD5Mesh in meshes.getList()) {
                totalVerts += mesh.NumVerts()
                totalTris += mesh.NumTris()
                totalWeights += mesh.NumWeights()
                Common.common.Printf(
                    "%2d: %5d %5d %7d %s\n",
                    i++,
                    mesh.NumVerts(),
                    mesh.NumTris(),
                    mesh.NumWeights(),
                    mesh.shader!!.GetName()
                )
            }
            Common.common.Printf("-----\n")
            Common.common.Printf("%4d verts.\n", totalVerts)
            Common.common.Printf("%4d tris.\n", totalTris)
            Common.common.Printf("%4d weights.\n", totalWeights)
            Common.common.Printf("%4d joints.\n", joints.Num())
        }

        override fun List() {
            var totalTris = 0
            var totalVerts = 0
            for (mesh: idMD5Mesh in meshes.getList()) {
                totalTris += mesh.numTris
                totalVerts += mesh.NumVerts()
            }
            Common.common.Printf(
                " %4dk %3d %4d %4d %s(MD5)",
                Memory() / 1024,
                meshes.Num(),
                totalVerts,
                totalTris,
                Name()
            )
            if (defaulted) {
                Common.common.Printf(" (DEFAULTED)")
            }
            Common.common.Printf("\n")
        }

        /*
         ====================
         idRenderModelMD5::TouchData

         models that are already loaded at level start time
         will still touch their materials to make sure they
         are kept loaded
         ====================
         */
        override fun TouchData() {
            for (mesh: idMD5Mesh in meshes.getList(Array<idMD5Mesh>::class.java)!!) {
                DeclManager.declManager.FindMaterial(mesh.shader!!.GetName())
            }
        }

        /*
         ===================
         idRenderModelMD5::PurgeModel

         frees all the data, but leaves the class around for dangling references,
         which can regenerate the data with LoadModel()
         ===================
         */
        override fun PurgeModel() {
            purged = true
            joints.Clear()
            defaultPose.Clear()
            meshes.Clear()
        }

        /*
         ====================
         idRenderModelMD5::LoadModel

         used for initial loads, reloadModel, and reloading the data of purged models
         Upon exit, the model will absolutely be valid, but possibly as a default model
         ====================
         */
        override fun LoadModel() {
            val version: Int
            var i: Int
            var num: Int
            var parentNum: Int
            val token = idToken()
            val parser = idLexer(LEXFL_ALLOWPATHNAMES or LEXFL_NOSTRINGESCAPECHARS)
            val poseMat3: Array<idJointMat?>
            if (!purged) {
                PurgeModel()
            }
            purged = false
            if (!parser.LoadFile(name)) {
                MakeDefaultModel()
                return
            }
            parser.ExpectTokenString(Model.MD5_VERSION_STRING)
            version = parser.ParseInt()
            if (version != Model.MD5_VERSION) {
                parser.Error("Invalid version %d.  Should be version %d\n", version, Model.MD5_VERSION)
            }

            //
            // skip commandline
            //
            parser.ExpectTokenString("commandline")
            parser.ReadToken(token)

            // parse num joints
            parser.ExpectTokenString("numJoints")
            num = parser.ParseInt()
            joints.SetGranularity(1)
            joints.SetNum(num)
            defaultPose.SetGranularity(1)
            defaultPose.SetNum(num)
            poseMat3 = arrayOfNulls(num)

            // parse num meshes
            parser.ExpectTokenString("numMeshes")
            num = parser.ParseInt()
            if (num < 0) {
                parser.Error("Invalid size: %d", num)
            }
            meshes.SetGranularity(1)
            meshes.SetNum(num)

            //
            // parse joints
            //
            parser.ExpectTokenString("joints")
            parser.ExpectTokenString("{")
            i = 0
            while (i < joints.Num()) {
                val pose: idJointQuat = defaultPose.set(i, idJointQuat())
                val joint: idMD5Joint? = joints.set(i, idMD5Joint())
                ParseJoint(parser, joint, pose)
                poseMat3[i] = idJointMat()
                poseMat3[i]!!.SetRotation(pose.q.ToMat3())
                poseMat3[i]!!.SetTranslation(pose.t)
                if (joint!!.parent != null) {
                    parentNum = (joints.Find(joint.parent))!!
                    pose.q.set(
                        (poseMat3[i]!!.ToMat3().times(poseMat3[parentNum]!!.ToMat3().Transpose())).ToQuat()
                    )
                    pose.t.set(
                        (poseMat3[i]!!
                            .ToVec3().minus(poseMat3[parentNum]!!.ToVec3())).times(
                                poseMat3[parentNum]!!.ToMat3().Transpose()
                            )
                    )
                }
                i++
            }
            parser.ExpectTokenString("}")
            i = 0
            while (i < meshes.Num()) {
                val mesh: idMD5Mesh = meshes.set(i, idMD5Mesh())
                parser.ExpectTokenString("mesh")
                mesh.ParseMesh(parser, defaultPose.Num(), poseMat3)
                i++
            }

            //
            // calculate the bounds of the model
            //
            CalculateBounds(poseMat3)

            // set the timestamp for reloadmodels
            fileSystem.ReadFile(name, null, timeStamp)
        }

        override fun Memory(): Int {
            var total: Int
            total = BYTES
            total += joints.MemoryUsed() + defaultPose.MemoryUsed() + meshes.MemoryUsed()

            // count up strings
            for (joint: idMD5Joint? in joints.getList()) {
                total += joint!!.name!!.DynamicMemoryUsed()
            }

            // count up meshes
            for (mesh: idMD5Mesh in meshes.getList()) {
                total += mesh.texCoords.MemoryUsed() + mesh.numWeights * (idVec4.BYTES + Integer.BYTES * 2)

                // sum up deform info
                total += deformInfo_s.BYTES
                total += R_DeformInfoMemoryUsed(mesh.deformInfo!!)
            }
            return total
        }

        override fun InstantiateDynamicModel(
            ent: renderEntity_s?,
            view: viewDef_s?,
            cachedModel: idRenderModel?
        ): idRenderModel? {
            var cachedModel: idRenderModel? = cachedModel
            val surfaceNum = CInt()
            val staticModel: idRenderModelStatic
            if (cachedModel != null && !r_useCachedDynamicModels!!.GetBool()) {
                cachedModel = null
            }
            if (purged) {
                Common.common.DWarning("model %s instantiated while purged", Name())
                LoadModel()
            }
            if (null == ent!!.joints) {
                Common.common.Printf(
                    "idRenderModelMD5::InstantiateDynamicModel: NULL joints on renderEntity for '%s'\n",
                    Name()
                )
                return null
            } else if (ent.numJoints != joints.Num()) {
                Common.common.Printf(
                    "idRenderModelMD5::InstantiateDynamicModel: renderEntity has different number of joints than model for '%s'\n",
                    Name()
                )
                return null
            }
            tr.pc!!.c_generateMd5++
            if (cachedModel != null) {
                assert((cachedModel is idRenderModelStatic))
                assert((Icmp(cachedModel.Name(), MD5_SnapshotName) == 0))
                staticModel = cachedModel as idRenderModelStatic
            } else {
                staticModel = idRenderModelStatic()
                staticModel.InitEmpty(MD5_SnapshotName)
            }
            staticModel.bounds.Clear()
            if (r_showSkel!!.GetInteger() != 0) {
                if ((view != null) && (!r_skipSuppress!!.GetBool() || (0 == ent.suppressSurfaceInViewID) || (ent.suppressSurfaceInViewID != view.renderView.viewID))) {
                    // only draw the skeleton
                    DrawJoints(ent, view)
                }
                if (r_showSkel!!.GetInteger() > 1) {
                    // turn off the model when showing the skeleton
                    staticModel.InitEmpty(MD5_SnapshotName)
                    return staticModel
                }
            }

            // create all the surfaces
            for (i in 0 until meshes.Num()) {
                val mesh: idMD5Mesh = meshes.getList(Array<idMD5Mesh>::class.java)!![i]

                // avoid deforming the surface if it will be a nodraw due to a skin remapping
                // FIXME: may have to still deform clipping hulls
                var shader: idMaterial? = mesh.shader
                shader = RenderWorld.R_RemapShaderBySkin(shader, ent.customSkin, ent.customShader)
                if (null == shader || (!shader.IsDrawn() && !shader.SurfaceCastsShadow())) {
                    staticModel.DeleteSurfaceWithId(i)
                    mesh.surfaceNum = -1
                    continue
                }
                var surf: modelSurface_s?
                if (staticModel.FindSurfaceWithId(i, surfaceNum)) {
                    mesh.surfaceNum = surfaceNum._val
                    surf = staticModel.surfaces[surfaceNum._val]
                } else {

                    // Remove Overlays before adding new surfaces
                    idRenderModelOverlay.RemoveOverlaySurfacesFromModel(staticModel)
                    mesh.surfaceNum = staticModel.NumSurfaces()
                    surf = modelSurface_s()
                    staticModel.surfaces.Append(surf)
                    surf.geometry = null
                    surf.shader = null
                    surf.id = i
                }
                mesh.UpdateSurface(ent, ent.joints as Array<idJointMat?>, surf)
                staticModel.bounds.AddPoint(surf!!.geometry!!.bounds[0])
                staticModel.bounds.AddPoint(surf.geometry!!.bounds[1])
            }
            return staticModel
        }

        override fun NumJoints(): Int {
            return joints.Num()
        }

        override fun GetJoints(): Array<idMD5Joint?>? {
            return joints.getList<idMD5Joint?>((Array<idMD5Joint?>::class.java))
        }

        override fun GetJointHandle(name: String?): Int {
            var i = 0
            for (joint: idMD5Joint in joints.getList<idMD5Joint>(Array<idMD5Joint>::class.java)!!) {
                if (Icmp((joint.name)!!, (name)!!) == 0) {
                    return i
                }
                i++
            }
            return Model.INVALID_JOINT
        }

        override fun GetJointName(handle: Int): String {
            if ((handle < 0) || (handle >= joints.Num())) {
                return "<invalid joint>"
            }
            return joints[handle]!!.name.toString()
        }

        override fun GetDefaultPose(): Array<idJointQuat?>? {
            return defaultPose.getList((Array<idJointQuat?>::class.java))
        }

        override fun NearestJoint(surfaceNum: Int, a: Int, b: Int, c: Int): Int {
            if (surfaceNum > meshes.Num()) {
                Common.common.Error("idRenderModelMD5::NearestJoint: surfaceNum > meshes.Num()")
            }
            for (mesh: idMD5Mesh in meshes.getList(Array<idMD5Mesh>::class.java)!!) {
                if (mesh.surfaceNum == surfaceNum) {
                    return mesh.NearestJoint(a, b, c)
                }
            }
            return 0
        }

        private fun CalculateBounds(entJoints: Array<idJointMat?>) {
            var i: Int
            bounds.Clear()
            for (i in 0 until meshes.Num()) {
                bounds.AddBounds(meshes[i].CalcBounds(entJoints))
            }
        }

        private fun DrawJoints(ent: renderEntity_s?, view: viewDef_s) {
            var i: Int
            var num: Int
            val pos = idVec3()
            var joint: idJointMat
            var md5Joint: idMD5Joint?
            var parentNum: Int
            num = ent!!.numJoints
            i = 0
            while (i < num) {
                joint = ent.joints!![i]!!
                md5Joint = joints[i]
                pos.set(ent.origin.plus(joint.ToVec3().times(ent.axis)))
                if (md5Joint!!.parent != null) {
                    parentNum = joints.IndexOf(md5Joint.parent)
                    Session.session.rw.DebugLine(
                        colorWhite, ent.origin.plus(
                            ent.joints!![parentNum]!!.ToVec3().times(
                                ent.axis
                            )
                        ), pos
                    )
                }
                Session.session.rw.DebugLine(
                    colorRed, pos, pos.plus(
                        joint.ToMat3()[0].times(2.0f).times(
                            ent.axis
                        )
                    )
                )
                Session.session.rw.DebugLine(
                    colorGreen, pos, pos.plus(
                        joint.ToMat3()[1].times(2.0f).times(
                            ent.axis
                        )
                    )
                )
                Session.session.rw.DebugLine(
                    colorBlue, pos, pos.plus(
                        joint.ToMat3()[2].times(2.0f).times(
                            ent.axis
                        )
                    )
                )
                i++
            }
            val bounds = idBounds()
            bounds.FromTransformedBounds(ent.bounds, vec3_zero, ent.axis)
            Session.session.rw.DebugBounds(colorMagenta, bounds, ent.origin)
            if ((r_jointNameScale!!.GetFloat() != 0.0f) && (bounds.Expand(128.0f).ContainsPoint(
                    view.renderView.vieworg.minus(
                        ent.origin
                    )
                ))
            ) {
                val offset = idVec3(0.0f, 0.0f, r_jointNameOffset!!.GetFloat())
                val scale: Float
                scale = r_jointNameScale!!.GetFloat()
                num = ent.numJoints
                i = 0
                while (i < num) {
                    joint = ent.joints!![i]!!
                    pos.set(ent.origin.plus(joint.ToVec3().times(ent.axis)))
                    Session.session.rw.DrawText(
                        joints[i]!!.name.toString(),
                        pos.plus(offset),
                        scale,
                        colorWhite,
                        view.renderView.viewaxis,
                        1
                    )
                    i++
                }
            }
        }

        @Throws(idException::class)
        private fun ParseJoint(parser: idLexer, joint: idMD5Joint?, defaultPose: idJointQuat) {
            val token = idToken()
            val num: Int

            //
            // parse name
            //
            parser.ReadToken(token)
            joint!!.name = token

            //
            // parse parent
            //
            num = parser.ParseInt()
            if (num < 0) {
                joint.parent = null
            } else {
                if (num >= joints.Num() - 1) {
                    parser.Error("Invalid parent for joint '%s'", joint.name!!)
                }
                joint.parent = joints[num]
            }

            //
            // parse default pose
            //
            parser.Parse1DMatrix(3, defaultPose.t)
            parser.Parse1DMatrix(3, defaultPose.q)
            defaultPose.q.w = defaultPose.q.CalcW()
        }

        companion object {
            val BYTES: Int = Integer.BYTES * 3
        }
    }
}

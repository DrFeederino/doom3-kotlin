/*
 * Copyright (C) 1999-2011 id Software LLC, a ZeniMax Media company.
 * Translated to Kotlin by Dr. Feederino with support of Claude Code
 *
 * This file is part of the Doom 3 Kotlin project.
 * Original source: neo/framework/DeclAF.h, neo/framework/DeclAF.cpp
 *
 * Doom 3 GPL Source Code is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package neo.framework

import neo.Renderer.Material
import neo.framework.DeclManager.idDecl
import neo.framework.File_h.idFile
import neo.framework.File_h.idFile_Memory
import neo.idlib.Text.Lexer.idLexer
import neo.idlib.Text.Str.idStr
import neo.idlib.Text.Token
import neo.idlib.Text.Token.idToken
import neo.idlib.containers.CInt
import neo.idlib.containers.List.idList
import neo.idlib.geometry.JointTransform.idJointMat
import neo.idlib.geometry.TraceModel.traceModel_t
import neo.idlib.idException
import neo.idlib.math.*
import neo.idlib.math.Matrix.idMat3

class DeclAF {
    /*
     ===============================================================================

     Articulated Figure

     ===============================================================================
     */
    enum class declAFConstraintType_t {
        DECLAF_CONSTRAINT_INVALID, DECLAF_CONSTRAINT_FIXED, DECLAF_CONSTRAINT_BALLANDSOCKETJOINT, DECLAF_CONSTRAINT_UNIVERSALJOINT, DECLAF_CONSTRAINT_HINGE, DECLAF_CONSTRAINT_SLIDER, DECLAF_CONSTRAINT_SPRING
    }

    enum class declAFJointMod_t {
        DECLAF_JOINTMOD_AXIS, DECLAF_JOINTMOD_ORIGIN, DECLAF_JOINTMOD_BOTH
    }

    abstract class getJointTransform_t {
        abstract fun run(
            model: Any,
            frame: Array<idJointMat>,
            jointName: idStr,
            origin: idVec3,
            axis: idMat3
        ): Boolean
    }

    class idAFVector {
        var joint1: idStr
        var joint2: idStr
        var idAFVectorType: idFVectorTypes = idFVectorTypes.VEC_COORDS

        private var negate: Boolean
        private val vec: idVec3

        @Throws(idException::class)
        fun Parse(src: idLexer): Boolean {
            val token = idToken()
            if (!src.ReadToken(token)) {
                return false
            }
            if (token.toString() == "-") {
                negate = true
                if (!src.ReadToken(token)) {
                    return false
                }
            } else {
                negate = false
            }
            if (token.toString() == "(") {
                idAFVectorType = idFVectorTypes.VEC_COORDS
                vec.x = src.ParseFloat()
                src.ExpectTokenString(",")
                vec.y = src.ParseFloat()
                src.ExpectTokenString(",")
                vec.z = src.ParseFloat()
                src.ExpectTokenString(")")
            } else if (token.toString() == "joint") {
                idAFVectorType = idFVectorTypes.VEC_JOINT
                src.ExpectTokenString("(")
                src.ReadToken(token)
                joint1.set(token)
                src.ExpectTokenString(")")
            } else if (token.toString() == "bonecenter") {
                idAFVectorType = idFVectorTypes.VEC_BONECENTER
                src.ExpectTokenString("(")
                src.ReadToken(token)
                joint1.set(token)
                src.ExpectTokenString(",")
                src.ReadToken(token)
                joint2.set(token)
                src.ExpectTokenString(")")
            } else if (token.toString() == "bonedir") {
                idAFVectorType = idFVectorTypes.VEC_BONEDIR
                src.ExpectTokenString("(")
                src.ReadToken(token)
                joint1.set(token)
                src.ExpectTokenString(",")
                src.ReadToken(token)
                joint2.set(token)
                src.ExpectTokenString(")")
            } else {
                src.Error("unknown token %s in vector", token.toString())
                return false
            }
            return true
        }

        @Throws(idException::class)
        fun Finish(
            fileName: String,
            GetJointTransform: getJointTransform_t,
            frame: Array<idJointMat>,
            model: Any
        ): Boolean {
            val axis = idMat3()
            val start = idVec3()
            val end = idVec3()
            when (idAFVectorType) {
                idFVectorTypes.VEC_COORDS -> {}
                idFVectorTypes.VEC_JOINT -> {
                    if (!GetJointTransform.run(model, frame, joint1, vec, axis)) {
                        Common.common.Warning("invalid joint %s in joint() in '%s'", joint1.toString(), fileName)
                        vec.Zero()
                    }
                }

                idFVectorTypes.VEC_BONECENTER -> {
                    if (!GetJointTransform.run(model, frame, joint1, start, axis)) {
                        Common.common.Warning("invalid joint %s in bonecenter() in '%s'", joint1.toString(), fileName)
                        start.Zero()
                    }
                    if (!GetJointTransform.run(model, frame, joint2, end, axis)) {
                        Common.common.Warning("invalid joint %s in bonecenter() in '%s'", joint2.toString(), fileName)
                        end.Zero()
                    }
                    vec.set(start.plus(end).times(0.5f))
                }

                idFVectorTypes.VEC_BONEDIR -> {
                    if (!GetJointTransform.run(model, frame, joint1, start, axis)) {
                        Common.common.Warning("invalid joint %s in bonedir() in '%s'", joint1.toString(), fileName)
                        start.Zero()
                    }
                    if (!GetJointTransform.run(model, frame, joint2, end, axis)) {
                        Common.common.Warning("invalid joint %s in bonedir() in '%s'", joint2.toString(), fileName)
                        end.Zero()
                    }
                    vec.set(end.minus(start))
                }

                else -> {
                    vec.Zero()
                }
            }
            if (negate) {
                vec.set(-vec)
            }
            return true
        }

        fun Write(f: idFile): Boolean {
            if (negate) {
                f.WriteFloatString("-")
            }
            when (idAFVectorType) {
                idFVectorTypes.VEC_COORDS -> {
                    f.WriteFloatString("( %f, %f, %f )", vec.x, vec.y, vec.z)
                }

                idFVectorTypes.VEC_JOINT -> {
                    f.WriteFloatString("joint( \"%s\" )", joint1.toString())
                }

                idFVectorTypes.VEC_BONECENTER -> {
                    f.WriteFloatString("bonecenter( \"%s\", \"%s\" )", joint1.toString(), joint2.toString())
                }

                idFVectorTypes.VEC_BONEDIR -> {
                    f.WriteFloatString("bonedir( \"%s\", \"%s\" )", joint1.toString(), joint2.toString())
                }

                else -> {}
            }
            return true
        }

        fun ToString(str: idStr, precision: Int /*= 8*/): String {
            when (idAFVectorType) {
                idFVectorTypes.VEC_COORDS -> {
                    val format: String //[128];
                    format = String.format("( %%.%df, %%.%df, %%.%df )", precision, precision, precision)
                    str.set(String.format(format, vec.x, vec.y, vec.z))
                }

                idFVectorTypes.VEC_JOINT -> {
                    str.set(String.format("joint( \"%s\" )", joint1.toString()))
                }

                idFVectorTypes.VEC_BONECENTER -> {
                    str.set(String.format("bonecenter( \"%s\", \"%s\" )", joint1.toString(), joint2.toString()))
                }

                idFVectorTypes.VEC_BONEDIR -> {
                    str.set(String.format("bonedir( \"%s\", \"%s\" )", joint1.toString(), joint2.toString()))
                }

                else -> {}
            }
            if (negate) {
                str.set("-$str")
            }
            return str.toString()
        }

        fun ToVec3(): idVec3 {
            return vec
        }

        enum class idFVectorTypes {
            VEC_COORDS, VEC_JOINT, VEC_BONECENTER, VEC_BONEDIR
        }

        init {
            idAFVectorType = idFVectorTypes.VEC_COORDS
            joint1 = idStr()
            joint2 = idStr()
            vec = idVec3()
            negate = false
        }
    }

    class idDeclAF_Body {
        val angles: idAngles = idAngles()
        var angularFriction = 0.0f
        var clipMask: CInt = CInt()
        var contactFriction = 0.0f
        var contactMotorDirection: idAFVector = idAFVector()
        val containedJoints: idStr = idStr()
        var contents: CInt = CInt()
        var density = 0.0f
        var frictionDirection: idAFVector = idAFVector()
        val inertiaScale: idMat3 = idMat3()
        var jointMod: declAFJointMod_t = declAFJointMod_t.DECLAF_JOINTMOD_AXIS
        val jointName: idStr = idStr()
        var linearFriction = 0.0f
        var modelType: traceModel_t = traceModel_t.TRM_BOX
        val name: idStr = idStr()
        var numSides = 0
        var origin: idAFVector = idAFVector()
        var selfCollision = false
        var v1: idAFVector = idAFVector()
        var v2: idAFVector = idAFVector()
        var width = 0.0f

        fun SetDefault(file: idDeclAF) {
            name.set("noname")
            modelType = traceModel_t.TRM_BOX
            v1 = idAFVector()
            v1.ToVec3().z = -10.0f
            v1.ToVec3().y = v1.ToVec3().z
            v1.ToVec3().x = v1.ToVec3().y
            v2 = idAFVector()
            v2.ToVec3().z = 10.0f
            v2.ToVec3().y = v2.ToVec3().z
            v2.ToVec3().x = v2.ToVec3().y
            numSides = 3
            origin = idAFVector()
            origin.ToVec3().Zero()
            angles.set(idAngles())
            angles.Zero()
            density = 0.2f
            inertiaScale.set(idMat3.getMat3_identity())
            linearFriction = file.defaultLinearFriction
            angularFriction = file.defaultAngularFriction
            contactFriction = file.defaultContactFriction
            // FIX: copy integer value, not the CInt reference — otherwise body.contents and
            // file.contents would alias the same CInt object, corrupting file state on ParseContents
            contents._val = file.contents._val
            clipMask._val = file.clipMask._val
            selfCollision = file.selfCollision
            frictionDirection = idAFVector()
            contactMotorDirection = idAFVector()
            jointName.set("origin")
            jointMod = declAFJointMod_t.DECLAF_JOINTMOD_AXIS
            containedJoints.set("*origin")
        }
    }

    class idDeclAF_Constraint {
        var anchor: idAFVector = idAFVector()
        var anchor2: idAFVector = idAFVector()
        var axis: idAFVector = idAFVector()
        val body1: idStr = idStr()
        val body2: idStr = idStr()
        var compress = 0.0f
        var damping = 0.0f
        var friction = 0.0f
        var limit = 0
        var limitAngles: FloatArray = FloatArray(3)
        var limitAxis: idAFVector = idAFVector()
        var maxLength = 0.0f
        var minLength = 0.0f
        val name: idStr = idStr()
        var restLength = 0.0f
        var shaft: Array<idAFVector> = arrayOf(idAFVector(), idAFVector())
        var stretch = 0.0f
        var type: declAFConstraintType_t = declAFConstraintType_t.DECLAF_CONSTRAINT_UNIVERSALJOINT

        fun SetDefault(file: idDeclAF) {
            name.set("noname")
            type = declAFConstraintType_t.DECLAF_CONSTRAINT_UNIVERSALJOINT
            if (file.bodies.Num() != 0) {
                body1.set(file.bodies[0].name)
            } else {
                body1.set("world")
            }
            body2.set("world")
            friction = file.defaultConstraintFriction
            anchor = idAFVector()
            anchor2 = idAFVector()
            axis.ToVec3().set(1.0f, 0.0f, 0.0f)
            shaft[0].ToVec3().set(0.0f, 0.0f, -1.0f)
            shaft[1].ToVec3().set(0.0f, 0.0f, 1.0f)
            limit = LIMIT_NONE
            limitAngles[2] = 0.0f
            limitAngles[1] = limitAngles[2]
            limitAngles[0] = limitAngles[1]
            limitAxis.ToVec3().set(0.0f, 0.0f, -1.0f)
        }

        companion object {
            const val LIMIT_CONE = 0
            const val LIMIT_NONE = -1
            const val LIMIT_PYRAMID = 1
        }
    }

    class idDeclAF : idDecl() {
        val bodies: idList<idDeclAF_Body> = idList()
        val constraints: idList<idDeclAF_Constraint> = idList()
        var clipMask: CInt = CInt()
        var contents: CInt = CInt()
        var defaultAngularFriction = 0.0f
        var defaultConstraintFriction = 0.0f
        var defaultContactFriction = 0.0f
        var defaultLinearFriction = 0.0f
        var maxMoveTime = 0.0f
        var minMoveTime = 0.0f
        val model: idStr = idStr()
        var modified = false
        var noMoveRotation = 0.0f
        var noMoveTime = 0.0f
        var noMoveTranslation = 0.0f
        var selfCollision = false
        val skin: idStr = idStr()
        val suspendAcceleration: idVec2 = idVec2()
        val suspendVelocity: idVec2 = idVec2()
        var totalMass = 0.0f
        override fun DefaultDefinition(): String {
            return """{
	settings {
		model ""
		skin ""
		friction 0.01, 0.01, 0.8, 0.5
		suspendSpeed 20, 30, 40, 60
		noMoveTime 1
		noMoveTranslation 10
		noMoveRotation 10
		minMoveTime -1
		maxMoveTime -1
		totalMass -1
		contents corpse
		clipMask solid, corpse
		selfCollision 1
	}
	body "body" {
		joint "origin"
		mod orientation
		model box( ( -10, -10, -10 ), ( 10, 10, 10 ) )
		origin ( 0, 0, 0 )
		density 0.2
		friction 0.01, 0.01, 0.8
		contents corpse
		clipMask solid, corpse
		selfCollision 1
		containedJoints "*origin"
	}
}
"""
        }

        /*
         ================
         idDeclAF::Parse
         ================
         */
        @Throws(idException::class)
        override fun Parse(text: String, textLength: Int): Boolean {
            var i: Int
            var j: Int
            val src = idLexer()
            val token = idToken()
            src.LoadMemory(text, textLength, GetFileName(), GetLineNum())
            src.SetFlags(DeclManager.DECL_LEXER_FLAGS)
            src.SkipUntilString("{")
            while (src.ReadToken(token)) {
                if (0 == token.Icmp("settings")) {
                    if (!ParseSettings(src)) {
                        return false
                    }
                } else if (0 == token.Icmp("body")) {
                    if (!ParseBody(src)) {
                        return false
                    }
                } else if (0 == token.Icmp("fixed")) {
                    if (!ParseFixed(src)) {
                        return false
                    }
                } else if (0 == token.Icmp("ballAndSocketJoint")) {
                    if (!ParseBallAndSocketJoint(src)) {
                        return false
                    }
                } else if (0 == token.Icmp("universalJoint")) {
                    if (!ParseUniversalJoint(src)) {
                        return false
                    }
                } else if (0 == token.Icmp("hinge")) {
                    if (!ParseHinge(src)) {
                        return false
                    }
                } else if (0 == token.Icmp("slider")) {
                    if (!ParseSlider(src)) {
                        return false
                    }
                } else if (0 == token.Icmp("spring")) {
                    if (!ParseSpring(src)) {
                        return false
                    }
                } else if (token.toString() == "}") {
                    break
                } else {
                    src.Error("unknown keyword %s", token)
                    return false
                }
            }
            i = 0
            while (i < bodies.Num()) {

                // check for multiple bodies with the same name
                j = i + 1
                while (j < bodies.Num()) {
                    if (bodies[i].name == bodies[j].name) {
                        src.Error("two bodies with the same name \"%s\"", bodies[i].name)
                    }
                    j++
                }
                i++
            }
            i = 0
            while (i < constraints.Num()) {

                // check for multiple constraints with the same name
                j = i + 1
                while (j < constraints.Num()) {
                    if (constraints[i].name == constraints[j].name) {
                        src.Error("two constraints with the same name \"%s\"", constraints[i].name)
                    }
                    j++
                }
                // check if there are two valid bodies set
                if (constraints[i].body1.IsEmpty()) {
                    src.Error("no valid body1 specified for constraint '%s'", constraints[i].name)
                }
                if (constraints[i].body2.IsEmpty()) {
                    src.Error("no valid body2 specified for constraint '%s'", constraints[i].name)
                }
                i++
            }

            // make sure the body which modifies the origin comes first
            i = 0
            while (i < bodies.Num()) {
                if (bodies[i].jointName.toString() == "origin") {
                    if (i != 0) {
                        val b = bodies[0]
                        bodies[0] = bodies[i]
                        bodies[i] = b
                    }
                    break
                }
                i++
            }
            return true
        }

        override fun FreeData() {
            modified = false
            defaultLinearFriction = 0.01f
            defaultAngularFriction = 0.01f
            defaultContactFriction = 0.8f
            defaultConstraintFriction = 0.5f
            totalMass = -1.0f
            suspendVelocity.set(20.0f, 30.0f)
            suspendAcceleration.set(40.0f, 60.0f)
            noMoveTime = 1.0f
            noMoveTranslation = 10.0f
            noMoveRotation = 10.0f
            minMoveTime = -1.0f
            maxMoveTime = -1.0f
            selfCollision = true
            contents._val = (Material.CONTENTS_CORPSE)
            clipMask._val = (Material.CONTENTS_SOLID or Material.CONTENTS_CORPSE)
            bodies.DeleteContents(true)
            constraints.DeleteContents(true)
        }

        @Throws(idException::class)  /*virtual */   fun Finish(
            GetJointTransform: getJointTransform_t,
            frame: Array<idJointMat>,
            model: Any
        ) {
            var i: Int
            val name = GetName()
            i = 0
            while (i < bodies.Num()) {
                val body = bodies[i]
                body.v1.Finish(name, GetJointTransform, frame, model)
                body.v2.Finish(name, GetJointTransform, frame, model)
                body.origin.Finish(name, GetJointTransform, frame, model)
                body.frictionDirection.Finish(name, GetJointTransform, frame, model)
                body.contactMotorDirection.Finish(name, GetJointTransform, frame, model)
                i++
            }
            i = 0
            while (i < constraints.Num()) {
                val constraint = constraints[i]
                constraint.anchor.Finish(name, GetJointTransform, frame, model)
                constraint.anchor2.Finish(name, GetJointTransform, frame, model)
                constraint.shaft[0].Finish(name, GetJointTransform, frame, model)
                constraint.shaft[1].Finish(name, GetJointTransform, frame, model)
                constraint.axis.Finish(name, GetJointTransform, frame, model)
                constraint.limitAxis.Finish(name, GetJointTransform, frame, model)
                i++
            }
        }

        /*
         ================
         idDeclAF::Save
         ================
         */
        @Throws(idException::class)
        fun Save(): Boolean {
            RebuildTextSource()
            ReplaceSourceFileText()
            modified = false
            return true
        }

        fun NewBody(name: String) {
            val body: idDeclAF_Body
            body = idDeclAF_Body()
            body.SetDefault(this)
            body.name.set(name)
            bodies.Append(body)
        }

        /*
         ================
         idDeclAF::RenameBody

         rename the body with the given name and rename
         all constraint body references
         ================
         */
        fun RenameBody(oldName: String, newName: String) {
            var i: Int
            i = 0
            while (i < bodies.Num()) {
                if (bodies[i].name.Icmp(oldName) == 0) {
                    bodies[i].name.set(newName)
                    break
                }
                i++
            }
            i = 0
            while (i < constraints.Num()) {
                if (constraints[i].body1.Icmp(oldName) == 0) {
                    constraints[i].body1.set(newName)
                } else if (constraints[i].body2.Icmp(oldName) == 0) {
                    constraints[i].body2.set(newName)
                }
                i++
            }
        }

        /*
         ================
         idDeclAF::DeleteBody

         delete the body with the given name and delete
         all constraints that reference the body
         ================
         */
        fun DeleteBody(name: String) {
            var i: Int
            i = 0
            while (i < bodies.Num()) {
                if (bodies[i].name.Icmp(name) == 0) {
                    bodies.RemoveIndex(i)
                    break
                }
                i++
            }
            i = 0
            while (i < constraints.Num()) {
                if (constraints[i].body1.Icmp(name) == 0
                    || constraints[i].body2.Icmp(name) == 0
                ) {
                    constraints.RemoveIndex(i)
                    i--
                }
                i++
            }
        }

        fun NewConstraint(name: String) {
            val constraint: idDeclAF_Constraint
            constraint = idDeclAF_Constraint()
            constraint.SetDefault(this)
            constraint.name.set(name)
            constraints.Append(constraint)
        }

        /*
         ================
         idDeclAF::RenameConstraint
         ================
         */
        fun RenameConstraint(oldName: String, newName: String) {
            var i: Int
            i = 0
            while (i < constraints.Num()) {
                if (constraints[i].name.Icmp(oldName) == 0) {
                    constraints[i].name.set(newName)
                    return
                }
                i++
            }
        }

        fun DeleteConstraint(name: String) {
            var i: Int
            i = 0
            while (i < constraints.Num()) {
                if (constraints[i].name.Icmp(name) == 0) {
                    constraints.RemoveIndex(i)
                    return
                }
                i++
            }
        }

        @Throws(idException::class)
        private fun ParseContents(src: idLexer, c: CInt): Boolean {
            val token = idToken()
            val str = idStr()
            while (src.ReadToken(token)) {
                str.Append(token)
                if (!src.CheckTokenString(",")) {
                    break
                }
                str.Append(",")
            }
            c._val = (ContentsFromString(str.toString()))
            return true
        }

        @Throws(idException::class)
        private fun ParseBody(src: idLexer): Boolean {
            var hasJoint = false
            val token = idToken()
            val angles = idAFVector()
            val body: idDeclAF_Body
            body = idDeclAF_Body()
            bodies.Append(body)
            body.SetDefault(this)
            if (0 == src.ExpectTokenType(Token.TT_STRING, 0, token)
                || !src.ExpectTokenString("{")
            ) {
                return false
            }
            body.name.set(token)
            if (0 == body.name.Icmp("origin") || 0 == body.name.Icmp("world")) {
                src.Error("a body may not be named \"origin\" or \"world\"")
                return false
            }
            while (src.ReadToken(token)) {
                if (0 == token.Icmp("model")) {
                    if (0 == src.ExpectTokenType(Token.TT_NAME, 0, token)) {
                        return false
                    }
                    if (0 == token.Icmp("box")) {
                        body.modelType = traceModel_t.TRM_BOX
                        if (!src.ExpectTokenString("(")
                            || !body.v1.Parse(src)
                            || !src.ExpectTokenString(",")
                            || !body.v2.Parse(src)
                            || !src.ExpectTokenString(")")
                        ) {
                            return false
                        }
                    } else if (0 == token.Icmp("octahedron")) {
                        body.modelType = traceModel_t.TRM_OCTAHEDRON
                        if (!src.ExpectTokenString("(")
                            || !body.v1.Parse(src)
                            || !src.ExpectTokenString(",")
                            || !body.v2.Parse(src)
                            || !src.ExpectTokenString(")")
                        ) {
                            return false
                        }
                    } else if (0 == token.Icmp("dodecahedron")) {
                        body.modelType = traceModel_t.TRM_DODECAHEDRON
                        if (!src.ExpectTokenString("(")
                            || !body.v1.Parse(src)
                            || !src.ExpectTokenString(",")
                            || !body.v2.Parse(src)
                            || !src.ExpectTokenString(")")
                        ) {
                            return false
                        }
                    } else if (0 == token.Icmp("cylinder")) {
                        body.modelType = traceModel_t.TRM_CYLINDER
                        if (!src.ExpectTokenString("(")
                            || !body.v1.Parse(src)
                            || !src.ExpectTokenString(",")
                            || !body.v2.Parse(src)
                            || !src.ExpectTokenString(",")
                        ) {
                            return false
                        }
                        body.numSides = src.ParseInt()
                        if (!src.ExpectTokenString(")")) {
                            return false
                        }
                    } else if (0 == token.Icmp("cone")) {
                        body.modelType = traceModel_t.TRM_CONE
                        if (!src.ExpectTokenString("(")
                            || !body.v1.Parse(src)
                            || !src.ExpectTokenString(",")
                            || !body.v2.Parse(src)
                            || !src.ExpectTokenString(",")
                        ) {
                            return false
                        }
                        body.numSides = src.ParseInt()
                        if (!src.ExpectTokenString(")")) {
                            return false
                        }
                    } else if (0 == token.Icmp("bone")) {
                        body.modelType = traceModel_t.TRM_BONE
                        if (!src.ExpectTokenString("(")
                            || !body.v1.Parse(src)
                            || !src.ExpectTokenString(",")
                            || !body.v2.Parse(src)
                            || !src.ExpectTokenString(",")
                        ) {
                            return false
                        }
                        body.width = src.ParseFloat()
                        if (!src.ExpectTokenString(")")) {
                            return false
                        }
                    } else if (0 == token.Icmp("custom")) {
                        src.Error("custom models not yet implemented")
                        return false
                    } else {
                        src.Error("unknown model type %s", token.toString())
                        return false
                    }
                } else if (0 == token.Icmp("origin")) {
                    if (!body.origin.Parse(src)) {
                        return false
                    }
                } else if (0 == token.Icmp("angles")) {
                    if (!angles.Parse(src)) {
                        return false
                    }
                    body.angles.set(idAngles(angles.ToVec3().x, angles.ToVec3().y, angles.ToVec3().z))
                } else if (0 == token.Icmp("joint")) {
                    if (0 == src.ExpectTokenType(Token.TT_STRING, 0, token)) {
                        return false
                    }
                    body.jointName.set(token)
                    hasJoint = true
                } else if (0 == token.Icmp("mod")) {
                    if (!src.ExpectAnyToken(token)) {
                        return false
                    }
                    body.jointMod = JointModFromString(token.toString())
                } else if (0 == token.Icmp("density")) {
                    body.density = src.ParseFloat()
                } else if (0 == token.Icmp("inertiaScale")) {
                    src.Parse1DMatrix(9, body.inertiaScale)
                } else if (0 == token.Icmp("friction")) {
                    body.linearFriction = src.ParseFloat()
                    src.ExpectTokenString(",")
                    body.angularFriction = src.ParseFloat()
                    src.ExpectTokenString(",")
                    body.contactFriction = src.ParseFloat()
                } else if (0 == token.Icmp("contents")) {
                    ParseContents(src, body.contents)
                } else if (0 == token.Icmp("clipMask")) {
                    ParseContents(src, body.clipMask)
                } else if (0 == token.Icmp("selfCollision")) {
                    body.selfCollision = src.ParseBool()
                } else if (0 == token.Icmp("containedjoints")) {
                    if (0 == src.ExpectTokenType(Token.TT_STRING, 0, token)) {
                        return false
                    }
                    body.containedJoints.set(token)
                } else if (0 == token.Icmp("frictionDirection")) {
                    if (!body.frictionDirection.Parse(src)) {
                        return false
                    }
                } else if (0 == token.Icmp("contactMotorDirection")) {
                    if (!body.contactMotorDirection.Parse(src)) {
                        return false
                    }
                } else if (token.toString() == "}") {
                    break
                } else {
                    src.Error("unknown token %s in body", token.toString())
                    return false
                }
            }
            if (body.modelType == traceModel_t.TRM_INVALID) {
                src.Error("no model set for body")
                return false
            }
            if (!hasJoint) {
                src.Error("no joint set for body")
                return false
            }
            body.clipMask._val = (body.clipMask._val or Material.CONTENTS_MOVEABLECLIP)
            return true
        }

        @Throws(idException::class)
        private fun ParseFixed(src: idLexer): Boolean {
            val token = idToken()
            val constraint: idDeclAF_Constraint
            constraint = idDeclAF_Constraint()
            constraints.Append(constraint)
            constraint.SetDefault(this)
            if (0 == src.ExpectTokenType(Token.TT_STRING, 0, token)
                || !src.ExpectTokenString("{")
            ) {
                return false
            }
            constraint.type = declAFConstraintType_t.DECLAF_CONSTRAINT_FIXED
            constraint.name.set(token)
            while (src.ReadToken(token)) {
                if (0 == token.Icmp("body1")) {
                    src.ExpectTokenType(Token.TT_STRING, 0, token)
                    constraint.body1.set(token)
                } else if (0 == token.Icmp("body2")) {
                    src.ExpectTokenType(Token.TT_STRING, 0, token)
                    constraint.body2.set(token)
                } else if (token.toString() == "}") {
                    break
                } else {
                    src.Error("unknown token %s in ball and socket joint", token.toString())
                    return false
                }
            }
            return true
        }

        @Throws(idException::class)
        private fun ParseBallAndSocketJoint(src: idLexer): Boolean {
            val token = idToken()
            val constraint: idDeclAF_Constraint
            constraint = idDeclAF_Constraint()
            constraints.Append(constraint)
            constraint.SetDefault(this)
            if (0 == src.ExpectTokenType(Token.TT_STRING, 0, token)
                || !src.ExpectTokenString("{")
            ) {
                return false
            }
            constraint.type = declAFConstraintType_t.DECLAF_CONSTRAINT_BALLANDSOCKETJOINT
            constraint.limit = idDeclAF_Constraint.LIMIT_NONE
            constraint.name.set(token)
            constraint.friction = 0.5f
            constraint.anchor.ToVec3().Zero()
            constraint.shaft[0].ToVec3().Zero()
            while (src.ReadToken(token)) {
                if (0 == token.Icmp("body1")) {
                    src.ExpectTokenType(Token.TT_STRING, 0, token)
                    constraint.body1.set(token)
                } else if (0 == token.Icmp("body2")) {
                    src.ExpectTokenType(Token.TT_STRING, 0, token)
                    constraint.body2.set(token)
                } else if (0 == token.Icmp("anchor")) {
                    if (!constraint.anchor.Parse(src)) {
                        return false
                    }
                } else if (0 == token.Icmp("conelimit")) {
                    if (!constraint.limitAxis.Parse(src)
                        || !src.ExpectTokenString(",")
                    ) {
                        return false
                    }
                    constraint.limitAngles[0] = src.ParseFloat()
                    if (!src.ExpectTokenString(",")
                        || !constraint.shaft[0].Parse(src)
                    ) {
                        return false
                    }
                    constraint.limit = idDeclAF_Constraint.LIMIT_CONE
                } else if (0 == token.Icmp("pyramidlimit")) {
                    if (!constraint.limitAxis.Parse(src)
                        || !src.ExpectTokenString(",")
                    ) {
                        return false
                    }
                    constraint.limitAngles[0] = src.ParseFloat()
                    if (!src.ExpectTokenString(",")) {
                        return false
                    }
                    constraint.limitAngles[1] = src.ParseFloat()
                    if (!src.ExpectTokenString(",")) {
                        return false
                    }
                    constraint.limitAngles[2] = src.ParseFloat()
                    if (!src.ExpectTokenString(",")
                        || !constraint.shaft[0].Parse(src)
                    ) {
                        return false
                    }
                    constraint.limit = idDeclAF_Constraint.LIMIT_PYRAMID
                } else if (0 == token.Icmp("friction")) {
                    constraint.friction = src.ParseFloat()
                } else if (token.toString() == "}") {
                    break
                } else {
                    src.Error("unknown token %s in ball and socket joint", token.toString())
                    return false
                }
            }
            return true
        }

        @Throws(idException::class)
        private fun ParseUniversalJoint(src: idLexer): Boolean {
            val token = idToken()
            val constraint: idDeclAF_Constraint
            constraint = idDeclAF_Constraint()
            constraints.Append(constraint)
            constraint.SetDefault(this)
            if (0 == src.ExpectTokenType(Token.TT_STRING, 0, token)
                || !src.ExpectTokenString("{")
            ) {
                return false
            }
            constraint.type = declAFConstraintType_t.DECLAF_CONSTRAINT_UNIVERSALJOINT
            constraint.limit = idDeclAF_Constraint.LIMIT_NONE
            constraint.name.set(token)
            constraint.friction = 0.5f
            constraint.anchor.ToVec3().Zero()
            constraint.shaft[0].ToVec3().Zero()
            constraint.shaft[1].ToVec3().Zero()
            while (src.ReadToken(token)) {
                if (0 == token.Icmp("body1")) {
                    src.ExpectTokenType(Token.TT_STRING, 0, token)
                    constraint.body1.set(token)
                } else if (0 == token.Icmp("body2")) {
                    src.ExpectTokenType(Token.TT_STRING, 0, token)
                    constraint.body2.set(token)
                } else if (0 == token.Icmp("anchor")) {
                    if (!constraint.anchor.Parse(src)) {
                        return false
                    }
                } else if (0 == token.Icmp("shafts")) {
                    if (!constraint.shaft[0].Parse(src)
                        || !src.ExpectTokenString(",")
                        || !constraint.shaft[1].Parse(src)
                    ) {
                        return false
                    }
                } else if (0 == token.Icmp("conelimit")) {
                    if (!constraint.limitAxis.Parse(src)
                        || !src.ExpectTokenString(",")
                    ) {
                        return false
                    }
                    constraint.limitAngles[0] = src.ParseFloat()
                    constraint.limit = idDeclAF_Constraint.LIMIT_CONE
                } else if (0 == token.Icmp("pyramidlimit")) {
                    if (!constraint.limitAxis.Parse(src)
                        || !src.ExpectTokenString(",")
                    ) {
                        return false
                    }
                    constraint.limitAngles[0] = src.ParseFloat()
                    if (!src.ExpectTokenString(",")) {
                        return false
                    }
                    constraint.limitAngles[1] = src.ParseFloat()
                    if (!src.ExpectTokenString(",")) {
                        return false
                    }
                    constraint.limitAngles[2] = src.ParseFloat()
                    constraint.limit = idDeclAF_Constraint.LIMIT_PYRAMID
                } else if (0 == token.Icmp("friction")) {
                    constraint.friction = src.ParseFloat()
                } else if (token.toString() == "}") {
                    break
                } else {
                    src.Error("unknown token %s in universal joint", token.toString())
                    return false
                }
            }
            return true
        }

        @Throws(idException::class)
        private fun ParseHinge(src: idLexer): Boolean {
            val token = idToken()
            val constraint: idDeclAF_Constraint
            constraint = idDeclAF_Constraint()
            constraints.Append(constraint)
            constraint.SetDefault(this)
            if (0 == src.ExpectTokenType(Token.TT_STRING, 0, token)
                || !src.ExpectTokenString("{")
            ) {
                return false
            }
            constraint.type = declAFConstraintType_t.DECLAF_CONSTRAINT_HINGE
            constraint.limit = idDeclAF_Constraint.LIMIT_NONE
            constraint.name.set(token)
            constraint.friction = 0.5f
            constraint.anchor.ToVec3().Zero()
            constraint.axis.ToVec3().Zero()
            while (src.ReadToken(token)) {
                if (0 == token.Icmp("body1")) {
                    src.ExpectTokenType(Token.TT_STRING, 0, token)
                    constraint.body1.set(token)
                } else if (0 == token.Icmp("body2")) {
                    src.ExpectTokenType(Token.TT_STRING, 0, token)
                    constraint.body2.set(token)
                } else if (0 == token.Icmp("anchor")) {
                    if (!constraint.anchor.Parse(src)) {
                        return false
                    }
                } else if (0 == token.Icmp("axis")) {
                    if (!constraint.axis.Parse(src)) {
                        return false
                    }
                } else if (0 == token.Icmp("limit")) {
                    constraint.limitAngles[0] = src.ParseFloat()
                    if (!src.ExpectTokenString(",")) {
                        return false
                    }
                    constraint.limitAngles[1] = src.ParseFloat()
                    if (!src.ExpectTokenString(",")) {
                        return false
                    }
                    constraint.limitAngles[2] = src.ParseFloat()
                    constraint.limit = idDeclAF_Constraint.LIMIT_CONE
                } else if (0 == token.Icmp("friction")) {
                    constraint.friction = src.ParseFloat()
                } else if (token.toString() == "}") {
                    break
                } else {
                    src.Error("unknown token %s in hinge", token.toString())
                    return false
                }
            }
            return true
        }

        @Throws(idException::class)
        private fun ParseSlider(src: idLexer): Boolean {
            val token = idToken()
            val constraint: idDeclAF_Constraint
            constraint = idDeclAF_Constraint()
            constraints.Append(constraint)
            constraint.SetDefault(this)
            if (0 == src.ExpectTokenType(Token.TT_STRING, 0, token)
                || !src.ExpectTokenString("{")
            ) {
                return false
            }
            constraint.type = declAFConstraintType_t.DECLAF_CONSTRAINT_SLIDER
            constraint.limit = idDeclAF_Constraint.LIMIT_NONE
            constraint.name.set(token)
            constraint.friction = 0.5f
            while (src.ReadToken(token)) {
                if (0 == token.Icmp("body1")) {
                    src.ExpectTokenType(Token.TT_STRING, 0, token)
                    constraint.body1.set(token)
                } else if (0 == token.Icmp("body2")) {
                    src.ExpectTokenType(Token.TT_STRING, 0, token)
                    constraint.body2.set(token)
                } else if (0 == token.Icmp("axis")) {
                    if (!constraint.axis.Parse(src)) {
                        return false
                    }
                } else if (0 == token.Icmp("friction")) {
                    constraint.friction = src.ParseFloat()
                } else if (token.toString() == "}") {
                    break
                } else {
                    src.Error("unknown token %s in slider", token.toString())
                    return false
                }
            }
            return true
        }

        @Throws(idException::class)
        private fun ParseSpring(src: idLexer): Boolean {
            val token = idToken()
            val constraint: idDeclAF_Constraint
            constraint = idDeclAF_Constraint()
            constraints.Append(constraint)
            constraint.SetDefault(this)
            if (0 == src.ExpectTokenType(Token.TT_STRING, 0, token)
                || !src.ExpectTokenString("{")
            ) {
                return false
            }
            constraint.type = declAFConstraintType_t.DECLAF_CONSTRAINT_SPRING
            constraint.limit = idDeclAF_Constraint.LIMIT_NONE
            constraint.name.set(token)
            constraint.friction = 0.5f
            while (src.ReadToken(token)) {
                if (0 == token.Icmp("body1")) {
                    src.ExpectTokenType(Token.TT_STRING, 0, token)
                    constraint.body1.set(token)
                } else if (0 == token.Icmp("body2")) {
                    src.ExpectTokenType(Token.TT_STRING, 0, token)
                    constraint.body2.set(token)
                } else if (0 == token.Icmp("anchor1")) {
                    if (!constraint.anchor.Parse(src)) {
                        return false
                    }
                } else if (0 == token.Icmp("anchor2")) {
                    if (!constraint.anchor2.Parse(src)) {
                        return false
                    }
                } else if (0 == token.Icmp("friction")) {
                    constraint.friction = src.ParseFloat()
                } else if (0 == token.Icmp("stretch")) {
                    constraint.stretch = src.ParseFloat()
                } else if (0 == token.Icmp("compress")) {
                    constraint.compress = src.ParseFloat()
                } else if (0 == token.Icmp("damping")) {
                    constraint.damping = src.ParseFloat()
                } else if (0 == token.Icmp("restLength")) {
                    constraint.restLength = src.ParseFloat()
                } else if (0 == token.Icmp("minLength")) {
                    constraint.minLength = src.ParseFloat()
                } else if (0 == token.Icmp("maxLength")) {
                    constraint.maxLength = src.ParseFloat()
                } else if (token.toString() == "}") {
                    break
                } else {
                    src.Error("unknown token %s in spring", token.toString())
                    return false
                }
            }
            return true
        }

        @Throws(idException::class)
        private fun ParseSettings(src: idLexer): Boolean {
            val token = idToken()
            if (!src.ExpectTokenString("{")) {
                return false
            }
            while (src.ReadToken(token)) {
                if (0 == token.Icmp("mesh")) {
                    if (0 == src.ExpectTokenType(Token.TT_STRING, 0, token)) {
                        return false
                    }
                } else if (0 == token.Icmp("anim")) {
                    if (0 == src.ExpectTokenType(Token.TT_STRING, 0, token)) {
                        return false
                    }
                } else if (0 == token.Icmp("model")) {
                    if (0 == src.ExpectTokenType(Token.TT_STRING, 0, token)) {
                        return false
                    }
                    model.set(token)
                } else if (0 == token.Icmp("skin")) {
                    if (0 == src.ExpectTokenType(Token.TT_STRING, 0, token)) {
                        return false
                    }
                    skin.set(token)
                } else if (0 == token.Icmp("friction")) {
                    defaultLinearFriction = src.ParseFloat()
                    if (!src.ExpectTokenString(",")) {
                        return false
                    }
                    defaultAngularFriction = src.ParseFloat()
                    if (!src.ExpectTokenString(",")) {
                        return false
                    }
                    defaultContactFriction = src.ParseFloat()
                    if (src.CheckTokenString(",")) {
                        defaultConstraintFriction = src.ParseFloat()
                    }
                } else if (0 == token.Icmp("totalMass")) {
                    totalMass = src.ParseFloat()
                } else if (0 == token.Icmp("suspendSpeed")) {
                    suspendVelocity[0] = src.ParseFloat()
                    if (!src.ExpectTokenString(",")) {
                        return false
                    }
                    suspendVelocity[1] = src.ParseFloat()
                    if (!src.ExpectTokenString(",")) {
                        return false
                    }
                    suspendAcceleration[0] = src.ParseFloat()
                    if (!src.ExpectTokenString(",")) {
                        return false
                    }
                    suspendAcceleration[1] = src.ParseFloat()
                } else if (0 == token.Icmp("noMoveTime")) {
                    noMoveTime = src.ParseFloat()
                } else if (0 == token.Icmp("noMoveTranslation")) {
                    noMoveTranslation = src.ParseFloat()
                } else if (0 == token.Icmp("noMoveRotation")) {
                    noMoveRotation = src.ParseFloat()
                } else if (0 == token.Icmp("minMoveTime")) {
                    minMoveTime = src.ParseFloat()
                } else if (0 == token.Icmp("maxMoveTime")) {
                    maxMoveTime = src.ParseFloat()
                } else if (0 == token.Icmp("contents")) {
                    ParseContents(src, contents)
                } else if (0 == token.Icmp("clipMask")) {
                    ParseContents(src, clipMask)
                } else if (0 == token.Icmp("selfCollision")) {
                    selfCollision = src.ParseBool()
                } else if (token.toString() == "}") {
                    break
                } else {
                    src.Error("unknown token %s in settings", token.toString())
                    return false
                }
            }
            return true
        }

        /*
         ================
         idDeclAF::WriteBody
         ================
         */
        private fun WriteBody(f: idFile, body: idDeclAF_Body): Boolean {
            val str = idStr()
            f.WriteFloatString("\nbody \"%s\" {\n", body.name.toString())
            f.WriteFloatString("\tjoint \"%s\"\n", body.jointName.toString())
            f.WriteFloatString("\tmod %s\n", JointModToString(body.jointMod))
            when (body.modelType) {
                traceModel_t.TRM_BOX -> {
                    f.WriteFloatString("\tmodel box( ")
                    body.v1.Write(f)
                    f.WriteFloatString(", ")
                    body.v2.Write(f)
                    f.WriteFloatString(" )\n")
                }

                traceModel_t.TRM_OCTAHEDRON -> {
                    f.WriteFloatString("\tmodel octahedron( ")
                    body.v1.Write(f)
                    f.WriteFloatString(", ")
                    body.v2.Write(f)
                    f.WriteFloatString(" )\n")
                }

                traceModel_t.TRM_DODECAHEDRON -> {
                    f.WriteFloatString("\tmodel dodecahedron( ")
                    body.v1.Write(f)
                    f.WriteFloatString(", ")
                    body.v2.Write(f)
                    f.WriteFloatString(" )\n")
                }

                traceModel_t.TRM_CYLINDER -> {
                    f.WriteFloatString("\tmodel cylinder( ")
                    body.v1.Write(f)
                    f.WriteFloatString(", ")
                    body.v2.Write(f)
                    f.WriteFloatString(", %d )\n", body.numSides)
                }

                traceModel_t.TRM_CONE -> {
                    f.WriteFloatString("\tmodel cone( ")
                    body.v1.Write(f)
                    f.WriteFloatString(", ")
                    body.v2.Write(f)
                    f.WriteFloatString(", %d )\n", body.numSides)
                }

                traceModel_t.TRM_BONE -> {
                    f.WriteFloatString("\tmodel bone( ")
                    body.v1.Write(f)
                    f.WriteFloatString(", ")
                    body.v2.Write(f)
                    f.WriteFloatString(", %f )\n", body.width)
                }

                else -> assert(false)
            }
            f.WriteFloatString("\torigin ")
            body.origin.Write(f)
            f.WriteFloatString("\n")
            // FIX: use structural equality (!=) not referential (!==); !== is always true since
            // body.angles and ang_zero are always distinct objects, causing angles to be written even when zero
            if (body.angles != ang_zero) {
                f.WriteFloatString("\tangles ( %f, %f, %f )\n", body.angles.pitch, body.angles.yaw, body.angles.roll)
            }
            f.WriteFloatString("\tdensity %f\n", body.density)
            if (body.inertiaScale != idMat3.getMat3_identity()) {
                val ic = body.inertiaScale
                f.WriteFloatString(
                    "\tinertiaScale (%f %f %f %f %f %f %f %f %f)\n",
                    ic[0][0], ic[0][1], ic[0][2],
                    ic[1][0], ic[1][1], ic[1][2],
                    ic[2][0], ic[2][1], ic[2][2]
                )
            }
            if (body.linearFriction != -1.0f) {
                f.WriteFloatString(
                    "\tfriction %f, %f, %f\n",
                    body.linearFriction,
                    body.angularFriction,
                    body.contactFriction
                )
            }
            f.WriteFloatString("\tcontents %s\n", ContentsToString(body.contents._val, str))
            f.WriteFloatString("\tclipMask %s\n", ContentsToString(body.clipMask._val, str))
            // FIX: Boolean is not a Number in Kotlin/JVM — FS_WriteFloatString casts %d args
            // via (arg as Number).toLong(), which throws ClassCastException for Boolean.
            // C++ implicitly promotes bool to int for variadic args; Kotlin needs explicit conversion.
            f.WriteFloatString("\tselfCollision %d\n", if (body.selfCollision) 1 else 0)
            if (body.frictionDirection.ToVec3() != vec3_origin) {
                f.WriteFloatString("\tfrictionDirection ")
                body.frictionDirection.Write(f)
                f.WriteFloatString("\n")
            }
            if (body.contactMotorDirection.ToVec3() != vec3_origin) {
                f.WriteFloatString("\tcontactMotorDirection ")
                body.contactMotorDirection.Write(f)
                f.WriteFloatString("\n")
            }
            f.WriteFloatString("\tcontainedJoints \"%s\"\n", body.containedJoints.toString())
            f.WriteFloatString("}\n")
            return true
        }

        private fun WriteFixed(f: idFile, c: idDeclAF_Constraint): Boolean {
            f.WriteFloatString("\nfixed \"%s\" {\n", c.name)
            f.WriteFloatString("\tbody1 \"%s\"\n", c.body1)
            f.WriteFloatString("\tbody2 \"%s\"\n", c.body2)
            f.WriteFloatString("}\n")
            return true
        }

        private fun WriteBallAndSocketJoint(f: idFile, c: idDeclAF_Constraint): Boolean {
            f.WriteFloatString("\nballAndSocketJoint \"%s\" {\n", c.name)
            f.WriteFloatString("\tbody1 \"%s\"\n", c.body1)
            f.WriteFloatString("\tbody2 \"%s\"\n", c.body2)
            f.WriteFloatString("\tanchor ")
            c.anchor.Write(f)
            f.WriteFloatString("\n")
            f.WriteFloatString("\tfriction %f\n", c.friction)
            if (c.limit == idDeclAF_Constraint.LIMIT_CONE) {
                f.WriteFloatString("\tconeLimit ")
                c.limitAxis.Write(f)
                f.WriteFloatString(", %f, ", c.limitAngles[0])
                c.shaft[0].Write(f)
                f.WriteFloatString("\n")
            } else if (c.limit == idDeclAF_Constraint.LIMIT_PYRAMID) {
                f.WriteFloatString("\tpyramidLimit ")
                c.limitAxis.Write(f)
                f.WriteFloatString(", %f, %f, %f, ", c.limitAngles[0], c.limitAngles[1], c.limitAngles[2])
                c.shaft[0].Write(f)
                f.WriteFloatString("\n")
            }
            f.WriteFloatString("}\n")
            return true
        }

        private fun WriteUniversalJoint(f: idFile, c: idDeclAF_Constraint): Boolean {
            f.WriteFloatString("\nuniversalJoint \"%s\" {\n", c.name)
            f.WriteFloatString("\tbody1 \"%s\"\n", c.body1)
            f.WriteFloatString("\tbody2 \"%s\"\n", c.body2)
            f.WriteFloatString("\tanchor ")
            c.anchor.Write(f)
            f.WriteFloatString("\n")
            f.WriteFloatString("\tshafts ")
            c.shaft[0].Write(f)
            f.WriteFloatString(", ")
            c.shaft[1].Write(f)
            f.WriteFloatString("\n")
            f.WriteFloatString("\tfriction %f\n", c.friction)
            if (c.limit == idDeclAF_Constraint.LIMIT_CONE) {
                f.WriteFloatString("\tconeLimit ")
                c.limitAxis.Write(f)
                f.WriteFloatString(", %f\n", c.limitAngles[0])
            } else if (c.limit == idDeclAF_Constraint.LIMIT_PYRAMID) {
                f.WriteFloatString("\tpyramidLimit ")
                c.limitAxis.Write(f)
                f.WriteFloatString(", %f, %f, %f\n", c.limitAngles[0], c.limitAngles[1], c.limitAngles[2])
            }
            f.WriteFloatString("}\n")
            return true
        }

        private fun WriteHinge(f: idFile, c: idDeclAF_Constraint): Boolean {
            f.WriteFloatString("\nhinge \"%s\" {\n", c.name)
            f.WriteFloatString("\tbody1 \"%s\"\n", c.body1)
            f.WriteFloatString("\tbody2 \"%s\"\n", c.body2)
            f.WriteFloatString("\tanchor ")
            c.anchor.Write(f)
            f.WriteFloatString("\n")
            f.WriteFloatString("\taxis ")
            c.axis.Write(f)
            f.WriteFloatString("\n")
            f.WriteFloatString("\tfriction %f\n", c.friction)
            if (c.limit == idDeclAF_Constraint.LIMIT_CONE) {
                f.WriteFloatString("\tlimit ")
                f.WriteFloatString("%f, %f, %f", c.limitAngles[0], c.limitAngles[1], c.limitAngles[2])
                f.WriteFloatString("\n")
            }
            f.WriteFloatString("}\n")
            return true
        }

        private fun WriteSlider(f: idFile, c: idDeclAF_Constraint): Boolean {
            f.WriteFloatString("\nslider \"%s\" {\n", c.name)
            f.WriteFloatString("\tbody1 \"%s\"\n", c.body1)
            f.WriteFloatString("\tbody2 \"%s\"\n", c.body2)
            f.WriteFloatString("\taxis ")
            c.axis.Write(f)
            f.WriteFloatString("\n")
            f.WriteFloatString("\tfriction %f\n", c.friction)
            f.WriteFloatString("}\n")
            return true
        }

        private fun WriteSpring(f: idFile, c: idDeclAF_Constraint): Boolean {
            f.WriteFloatString("\nspring \"%s\" {\n", c.name)
            f.WriteFloatString("\tbody1 \"%s\"\n", c.body1)
            f.WriteFloatString("\tbody2 \"%s\"\n", c.body2)
            f.WriteFloatString("\tanchor1 ")
            c.anchor.Write(f)
            f.WriteFloatString("\n")
            f.WriteFloatString("\tanchor2 ")
            c.anchor2.Write(f)
            f.WriteFloatString("\n")
            f.WriteFloatString("\tfriction %f\n", c.friction)
            f.WriteFloatString("\tstretch %f\n", c.stretch)
            f.WriteFloatString("\tcompress %f\n", c.compress)
            f.WriteFloatString("\tdamping %f\n", c.damping)
            f.WriteFloatString("\trestLength %f\n", c.restLength)
            f.WriteFloatString("\tminLength %f\n", c.minLength)
            f.WriteFloatString("\tmaxLength %f\n", c.maxLength)
            f.WriteFloatString("}\n")
            return true
        }

        private fun WriteConstraint(f: idFile, c: idDeclAF_Constraint): Boolean {
            when (c.type) {
                declAFConstraintType_t.DECLAF_CONSTRAINT_FIXED -> return WriteFixed(f, c)
                declAFConstraintType_t.DECLAF_CONSTRAINT_BALLANDSOCKETJOINT -> return WriteBallAndSocketJoint(f, c)
                declAFConstraintType_t.DECLAF_CONSTRAINT_UNIVERSALJOINT -> return WriteUniversalJoint(f, c)
                declAFConstraintType_t.DECLAF_CONSTRAINT_HINGE -> return WriteHinge(f, c)
                declAFConstraintType_t.DECLAF_CONSTRAINT_SLIDER -> return WriteSlider(f, c)
                declAFConstraintType_t.DECLAF_CONSTRAINT_SPRING -> return WriteSpring(f, c)
                else -> {}
            }
            return false
        }

        private fun WriteSettings(f: idFile): Boolean {
            val str = idStr()
            f.WriteFloatString("\nsettings {\n")
            f.WriteFloatString("\tmodel \"%s\"\n", model)
            f.WriteFloatString("\tskin \"%s\"\n", skin)
            f.WriteFloatString(
                "\tfriction %f, %f, %f, %f\n",
                defaultLinearFriction,
                defaultAngularFriction,
                defaultContactFriction,
                defaultConstraintFriction
            )
            f.WriteFloatString(
                "\tsuspendSpeed %f, %f, %f, %f\n",
                suspendVelocity[0],
                suspendVelocity[1],
                suspendAcceleration[0],
                suspendAcceleration[1]
            )
            f.WriteFloatString("\tnoMoveTime %f\n", noMoveTime)
            f.WriteFloatString("\tnoMoveTranslation %f\n", noMoveTranslation)
            f.WriteFloatString("\tnoMoveRotation %f\n", noMoveRotation)
            f.WriteFloatString("\tminMoveTime %f\n", minMoveTime)
            f.WriteFloatString("\tmaxMoveTime %f\n", maxMoveTime)
            f.WriteFloatString("\ttotalMass %f\n", totalMass)
            f.WriteFloatString("\tcontents %s\n", ContentsToString(contents._val, str))
            f.WriteFloatString("\tclipMask %s\n", ContentsToString(clipMask._val, str))
            // FIX: Boolean → Int for %d format (see WriteBody fix for explanation)
            f.WriteFloatString("\tselfCollision %d\n", if (selfCollision) 1 else 0)
            f.WriteFloatString("}\n")
            return true
        }

        /*
         ================
         idDeclAF::RebuildTextSource
         ================
         */
        private fun RebuildTextSource(): Boolean {
            var i: Int
            val f = idFile_Memory()
            f.WriteFloatString(
                """

/*
	Generated by the Articulated Figure Editor.
	Do not edit directly but launch the game and type 'editAFs' on the console.
*/
"""
            )
            f.WriteFloatString("\narticulatedFigure %s {\n", GetName())
            if (!WriteSettings(f)) {
                return false
            }
            i = 0
            while (i < bodies.Num()) {
                if (!WriteBody(f, bodies[i])) {
                    return false
                }
                i++
            }
            i = 0
            while (i < constraints.Num()) {
                if (!WriteConstraint(f, constraints[i])) {
                    return false
                }
                i++
            }
            f.WriteFloatString("\n}")
            SetText(String(f.GetDataPtr().array()))
            return true
        }

        companion object {
            /*
             ================
             idDeclAF::ContentsFromString
             ================
             */
            @Throws(idException::class)
            fun ContentsFromString(str: String): Int {
                var c: Int
                val token = idToken()
                val src = idLexer(str, str.length, "idDeclAF::ContentsFromString")
                c = 0
                while (src.ReadToken(token)) {
                    c = if (token.Icmp("none") == 0) {
                        0
                    } else if (token.Icmp("solid") == 0) {
                        c or Material.CONTENTS_SOLID
                    } else if (token.Icmp("body") == 0) {
                        c or Material.CONTENTS_BODY
                    } else if (token.Icmp("corpse") == 0) {
                        c or Material.CONTENTS_CORPSE
                    } else if (token.Icmp("playerclip") == 0) {
                        c or Material.CONTENTS_PLAYERCLIP
                    } else if (token.Icmp("monsterclip") == 0) {
                        c or Material.CONTENTS_MONSTERCLIP
                    } else return if (token.toString() == ",") {
                        continue
                    } else {
                        c
                    }
                }
                return c
            }

            fun ContentsToString(contents: Int, str: idStr): String {
                str.set("")
                if (contents and Material.CONTENTS_SOLID != 0) {
                    if (str.Length() != 0) {
                        str.Append(", ")
                    }
                    str.Append("solid")
                }
                if (contents and Material.CONTENTS_BODY != 0) {
                    if (str.Length() != 0) {
                        str.Append(", ")
                    }
                    str.Append("body")
                }
                if (contents and Material.CONTENTS_CORPSE != 0) {
                    if (str.Length() != 0) {
                        str.Append(", ")
                    }
                    str.Append("corpse")
                }
                if (contents and Material.CONTENTS_PLAYERCLIP != 0) {
                    if (str.Length() != 0) {
                        str.Append(", ")
                    }
                    str.Append("playerclip")
                }
                if (contents and Material.CONTENTS_MONSTERCLIP != 0) {
                    if (str.Length() != 0) {
                        str.Append(", ")
                    }
                    str.Append("monsterclip")
                }
                if (str.IsEmpty()) {
                    str.set("none")
                }
                return str.toString()
            }

            fun JointModFromString(str: String): declAFJointMod_t {
                if (idStr.Icmp(str, "orientation") == 0) {
                    return declAFJointMod_t.DECLAF_JOINTMOD_AXIS
                }
                if (idStr.Icmp(str, "position") == 0) {
                    return declAFJointMod_t.DECLAF_JOINTMOD_ORIGIN
                }
                return if (idStr.Icmp(str, "both") == 0) {
                    declAFJointMod_t.DECLAF_JOINTMOD_BOTH
                } else declAFJointMod_t.DECLAF_JOINTMOD_AXIS
            }

            /*
             ================
             idDeclAF::JointModToString
             ================
             */
            fun JointModToString(jointMod: declAFJointMod_t): String {
                when (jointMod) {
                    declAFJointMod_t.DECLAF_JOINTMOD_AXIS -> {
                        return "orientation"
                    }

                    declAFJointMod_t.DECLAF_JOINTMOD_ORIGIN -> {
                        return "position"
                    }

                    declAFJointMod_t.DECLAF_JOINTMOD_BOTH -> {
                        return "both"
                    }
                }
                return "orientation"
            }
        }

        init {
            FreeData()
        }
    }
}
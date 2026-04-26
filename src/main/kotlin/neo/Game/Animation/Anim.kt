/*
 * Copyright (C) 1999-2011 id Software LLC, a ZeniMax Media company.
 * Translated to Kotlin by Dr. Feederino with support of Claude Code
 *
 * This file is part of the Doom 3 Kotlin project.
 * Original source: neo/game/anim/Anim.cpp, neo/game/anim/Anim.h
 *
 * Doom 3 Source Code is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Doom 3 Source Code is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Doom 3 Source Code. If not, see <http://www.gnu.org/licenses/>.
 */

package neo.Game.Animation

import neo.Game.Game_local
import neo.Game.Game_local.idGameLocal
import neo.Game.Script.Script_Program.function_t
import neo.Renderer.Model
import neo.Renderer.Model.MD5_ANIM_EXT
import neo.Renderer.Model.idRenderModel
import neo.Sound.snd_shader.idSoundShader
import neo.framework.DeclSkin.idDeclSkin
import neo.idlib.BV.idBounds
import neo.idlib.Text.Lexer
import neo.idlib.Text.Lexer.idLexer
import neo.idlib.Text.Str.idStr
import neo.idlib.Text.Token.idToken
import neo.idlib.containers.HashTable.idHashTable
import neo.idlib.containers.List
import neo.idlib.containers.idHashIndex
import neo.idlib.containers.idStrList
import neo.idlib.geometry.JointTransform.idJointQuat
import neo.idlib.idException
import neo.idlib.math.Matrix.idMat3
import neo.idlib.math.Matrix.idMat3.Companion.getMat3_identity
import neo.idlib.math.SIMDProcessor
import neo.idlib.math.idCQuat
import neo.idlib.math.idQuat
import neo.idlib.math.idVec3

object Anim {
    // animation channels.  make sure to change script/doom_defs.script if you add any channels, or change their order
    const val ANIMCHANNEL_ALL = 0
    const val ANIMCHANNEL_EYELIDS = 4
    const val ANIMCHANNEL_HEAD = 3
    const val ANIMCHANNEL_LEGS = 2
    const val ANIMCHANNEL_TORSO = 1
    const val ANIM_MaxAnimsPerChannel = 3
    const val ANIM_MaxSyncedAnims = 3

    // animation channels
    // these can be changed by modmakers and licensees to be whatever they need.
    const val ANIM_NumAnimChannels = 5
    const val ANIM_QX = 1 shl 3 // BIT(3);
    const val ANIM_QY = 1 shl 4 // BIT(4);
    const val ANIM_QZ = 1 shl 5 // BIT(5);
    const val ANIM_TX = 1 shl 0 // BIT(0);
    const val ANIM_TY = 1 shl 1 // BIT(1);
    const val ANIM_TZ = 1 shl 2 // BIT(2);

    // for converting from 24 frames per second to milliseconds
    fun FRAME2MS(framenum: Int): Int {
        return (framenum * 1000) / 24
    }

    /*
     ==============================================================================================

     idAFPoseJointMod

     ==============================================================================================
     */
    enum class AFJointModType_t {
        AF_JOINTMOD_AXIS,
        AF_JOINTMOD_ORIGIN,
        AF_JOINTMOD_BOTH
    }

    enum class frameCommandType_t {
        FC_SCRIPTFUNCTION,
        FC_SCRIPTFUNCTIONOBJECT,
        FC_EVENTFUNCTION,
        FC_SOUND,
        FC_SOUND_VOICE,
        FC_SOUND_VOICE2,
        FC_SOUND_BODY,
        FC_SOUND_BODY2,
        FC_SOUND_BODY3,
        FC_SOUND_WEAPON,
        FC_SOUND_ITEM,
        FC_SOUND_GLOBAL,
        FC_SOUND_CHATTER,
        FC_SKIN,
        FC_TRIGGER,
        FC_TRIGGER_SMOKE_PARTICLE,
        FC_MELEE,
        FC_DIRECTDAMAGE,
        FC_BEGINATTACK,
        FC_ENDATTACK,
        FC_MUZZLEFLASH,
        FC_CREATEMISSILE,
        FC_LAUNCHMISSILE,
        FC_FIREMISSILEATTARGET,
        FC_FOOTSTEP,
        FC_LEFTFOOT,
        FC_RIGHTFOOT,
        FC_ENABLE_EYE_FOCUS,
        FC_DISABLE_EYE_FOCUS,
        FC_FX,
        FC_DISABLE_GRAVITY,
        FC_ENABLE_GRAVITY,
        FC_JUMP,
        FC_ENABLE_CLIP,
        FC_DISABLE_CLIP,
        FC_ENABLE_WALK_IK,
        FC_DISABLE_WALK_IK,
        FC_ENABLE_LEG_IK,
        FC_DISABLE_LEG_IK,
        FC_RECORDDEMO,
        FC_AVIGAME,

        // D3XP: AI particle emitter and FX frame commands
        FC_LAUNCH_PROJECTILE,
        FC_TRIGGER_FX,
        FC_START_EMITTER,
        FC_STOP_EMITTER
    }

    //
    // joint modifier modes.  make sure to change script/doom_defs.script if you add any, or change their order.
    //
    enum class jointModTransform_t {
        JOINTMOD_NONE,  // no modification
        JOINTMOD_LOCAL,  // modifies the joint's position or orientation in joint local space
        JOINTMOD_LOCAL_OVERRIDE,  // sets the joint's position or orientation in joint local space
        JOINTMOD_WORLD,  // modifies joint's position or orientation in model space
        JOINTMOD_WORLD_OVERRIDE // sets the joint's position or orientation in model space
    }

    class frameBlend_t {
        var backlerp = 0.0f
        var cycleCount // how many times the anim has wrapped to the begining (0 for clamped anims)
                = 0
        var frame1 = 0
        var frame2 = 0
        var frontlerp = 0.0f
    }

    class jointAnimInfo_t {
        var animBits = 0
        var firstComponent = 0
        var nameIndex = 0
        var parentNum = 0
    }

    class jointInfo_t {
        var channel = 0
        var   /*jointHandle_t*/num = 0
        var   /*jointHandle_t*/parentNum = 0
    }

    class jointMod_t {
        val mat: idMat3 = idMat3()
        var   /*jointHandle_t*/jointnum = 0
        val pos: idVec3 = idVec3()
        var transform_axis: jointModTransform_t = jointModTransform_t.JOINTMOD_NONE
        var transform_pos: jointModTransform_t = jointModTransform_t.JOINTMOD_NONE
    }

    class frameLookup_t {
        var firstCommand = 0
        var num = 0
    }

    class frameCommand_t {
        var function: function_t? = null
        var index = 0
        var skin: idDeclSkin? = null

        // union {
        var soundShader: idSoundShader? = null
        var string: idStr = idStr()
        var type: frameCommandType_t = frameCommandType_t.values()[0] // };
    }

    class animFlags_t {
        var ai_no_turn //: 1;
                = false
        var anim_turn //: 1;
                = false
        var prevent_idle_override //: 1;
                = false
        var random_cycle_start //: 1;
                = false
    }

    /*
     ==============================================================================================

     idMD5Anim

     ==============================================================================================
     */
    class idMD5Anim {
        private val baseFrame: List.idList<idJointQuat> = List.idList()
        private val bounds: List.idList<idBounds> = List.idList()
        private val componentFrames: List.idFloatList
        private val jointInfo: List.idList<jointAnimInfo_t> = List.idList()
        private val name: idStr
        private val totaldelta: idVec3
        private var animLength = 0
        private var frameRate = 24
        private var numAnimatedComponents = 0
        private var numFrames = 0
        private var numJoints = 0
        private var ref_count = 0

        fun Free() {
            numFrames = 0
            numJoints = 0
            frameRate = 24
            animLength = 0
            name.set("")
            totaldelta.Zero()

            jointInfo.Clear()
            bounds.Clear()
            componentFrames.Clear()
        }

        fun Reload(): Boolean {
            val filename: String = name.toString()
            Free()
            return LoadAnim(filename)
        }

        fun  /*size_t*/Allocated(): Int {
            return bounds.Allocated() + jointInfo.Allocated() + componentFrames.Allocated() + name.Allocated()
        }

        // FIX: Added missing Size() method — C++ Anim.h defines it inline as sizeof(*this) + Allocated()
        fun  /*size_t*/Size(): Int {
            return Allocated()
        }

        @Throws(idException::class)
        fun LoadAnim(filename: String): Boolean {
            val version: Int
            val parser =
                idLexer(Lexer.LEXFL_ALLOWPATHNAMES or Lexer.LEXFL_NOSTRINGESCAPECHARS or Lexer.LEXFL_NOSTRINGCONCAT)
            val token = idToken()
            var i: Int
            var j: Int
            var num: Int
            if (!parser.LoadFile(filename)) {
                return false
            }
            Free()
            name.set(filename)
            parser.ExpectTokenString(Model.MD5_VERSION_STRING)
            version = parser.ParseInt()
            if (version != Model.MD5_VERSION) {
                parser.Error("Invalid version %d.  Should be version %d\n", version, Model.MD5_VERSION)
            }

            // skip the commandline
            parser.ExpectTokenString("commandline")
            parser.ReadToken(token)

            // parse num frames
            parser.ExpectTokenString("numFrames")
            numFrames = parser.ParseInt()
            if (numFrames <= 0) {
                parser.Error("Invalid number of frames: %d", numFrames)
            }

            // parse num joints
            parser.ExpectTokenString("numJoints")
            numJoints = parser.ParseInt()
            if (numJoints <= 0) {
                parser.Error("Invalid number of joints: %d", numJoints)
            }

            // parse frame rate
            parser.ExpectTokenString("frameRate")
            frameRate = parser.ParseInt()
            if (frameRate < 0) {
                parser.Error("Invalid frame rate: %d", frameRate)
            }

            // parse number of animated components
            parser.ExpectTokenString("numAnimatedComponents")
            numAnimatedComponents = parser.ParseInt()
            if (numAnimatedComponents < 0 || numAnimatedComponents > numJoints * 6) {
                parser.Error("Invalid number of animated components: %d", numAnimatedComponents)
            }

            // parse the hierarchy
            jointInfo.SetGranularity(1)
            jointInfo.SetNum(numJoints)
            parser.ExpectTokenString("hierarchy")
            parser.ExpectTokenString("{")
            i = 0
            while (i < numJoints) {
                parser.ReadToken(token)
                val joint: jointAnimInfo_t = jointInfo.set(i, jointAnimInfo_t())
                joint.nameIndex = Game_local.animationLib.JointIndex(token.toString())

                // parse parent num
                joint.parentNum = parser.ParseInt()
                if (joint.parentNum >= i) {
                    parser.Error("Invalid parent num: %d", joint.parentNum)
                }
                if (i != 0 && joint.parentNum < 0) {
                    parser.Error("Animations may have only one root joint")
                }

                // parse anim bits
                joint.animBits = parser.ParseInt()
                if (joint.animBits and 63.inv() != 0) {
                    parser.Error("Invalid anim bits: %d", joint.animBits)
                }

                // parse first component
                joint.firstComponent = parser.ParseInt()
                if (numAnimatedComponents > 0 && (joint.firstComponent < 0 || joint.firstComponent >= numAnimatedComponents)) {
                    parser.Error("Invalid first component: %d", joint.firstComponent)
                }
                i++
            }
            parser.ExpectTokenString("}")

            // parse bounds
            parser.ExpectTokenString("bounds")
            parser.ExpectTokenString("{")
            bounds.SetGranularity(1)
            bounds.SetNum(numFrames)
            i = 0
            while (i < numFrames) {
                val bound: idBounds = bounds.set(i, idBounds())
                parser.Parse1DMatrix(3, bound[0])
                parser.Parse1DMatrix(3, bound[1])
                i++
            }
            parser.ExpectTokenString("}")

            // parse base frame
            baseFrame.SetGranularity(1)
            baseFrame.SetNum(numJoints)
            parser.ExpectTokenString("baseframe")
            parser.ExpectTokenString("{")
            i = 0
            while (i < numJoints) {
                val q = idCQuat()
                val frame: idJointQuat = baseFrame.set(i, idJointQuat())
                parser.Parse1DMatrix(3, frame.t)
                parser.Parse1DMatrix(3, q)
                frame.q.set(q.ToQuat())
                i++
            }
            parser.ExpectTokenString("}")

            // parse frames
            componentFrames.SetGranularity(1)
            componentFrames.SetNum(numAnimatedComponents * numFrames)

            var c_ptr = 0
            i = 0
            while (i < numFrames) {
                parser.ExpectTokenString("frame")
                num = parser.ParseInt()
                if (num != i) {
                    parser.Error("Expected frame number %d", i)
                }
                parser.ExpectTokenString("{")
                j = 0
                while (j < numAnimatedComponents) {
                    componentFrames[c_ptr] = parser.ParseFloat()
                    j++
                    c_ptr++
                }
                parser.ExpectTokenString("}")
                i++
            }

            // get total move delta
            if (0 == numAnimatedComponents) {
                totaldelta.Zero()
            } else {
                c_ptr = jointInfo[0].firstComponent
                if ((jointInfo[0].animBits and ANIM_TX) != 0) {
                    i = 0
                    while (i < numFrames) {
                        val index = c_ptr + numAnimatedComponents * i
                        componentFrames[index] = componentFrames[index] - baseFrame[0].t.x
                        i++
                    }
                    totaldelta.x = componentFrames[c_ptr + numAnimatedComponents * (numFrames - 1)]
                    c_ptr++
                } else {
                    totaldelta.x = 0.0f
                }

                if ((jointInfo[0].animBits and ANIM_TY) != 0) {
                    i = 0
                    while (i < numFrames) {
                        val index = c_ptr + numAnimatedComponents * i
                        componentFrames[index] = componentFrames[index] - baseFrame[0].t.y
                        i++
                    }
                    totaldelta.y = componentFrames[c_ptr + numAnimatedComponents * (numFrames - 1)]
                    c_ptr++
                } else {
                    totaldelta.y = 0.0f
                }

                if ((jointInfo[0].animBits and ANIM_TZ) != 0) {
                    i = 0
                    while (i < numFrames) {
                        val index = c_ptr + numAnimatedComponents * i
                        componentFrames[index] = componentFrames[index] - baseFrame[0].t.z
                        i++
                    }
                    totaldelta.z = componentFrames[c_ptr + numAnimatedComponents * (numFrames - 1)]
                } else {
                    totaldelta.z = 0.0f
                }
            }
            baseFrame[0].t.Zero()

            // we don't count last frame because it would cause a 1 frame pause at the end
            animLength = ((numFrames - 1) * 1000 + frameRate - 1) / frameRate

            // done
            return true
        }

        @Throws(idException::class)
        fun LoadAnim(filename: idStr): Boolean {
            return LoadAnim(filename.toString())
        }

        fun IncreaseRefs() {
            ref_count++
        }

        fun DecreaseRefs() {
            ref_count--
        }

        fun NumRefs(): Int {
            return ref_count
        }

        fun CheckModelHierarchy(model: idRenderModel) {
            var jointNum: Int
            var parent: Int
            if (jointInfo.Num() != model.NumJoints()) {
                idGameLocal.Error("Model '%s' has different # of joints than anim '%s'", model.Name(), name)
            }
            val modelJoints = model.GetJoints()
            var i = 0
            while (i < jointInfo.Num()) {
                jointNum = jointInfo[i].nameIndex
                if (modelJoints!![i]!!.name.toString() != Game_local.animationLib.JointName(jointNum)) {
                    idGameLocal.Error("Model '%s''s joint names don't match anim '%s''s", model.Name(), name)
                }
                parent = if (modelJoints[i]!!.parent != null) {
                    modelJoints.indexOf(modelJoints[i]!!.parent)
                } else {
                    -1
                }
                if (parent != jointInfo[i].parentNum) {
                    idGameLocal.Error(
                        "Model '%s' has different joint hierarchy than anim '%s'",
                        model.Name(),
                        name
                    )
                }
                i++
            }
        }

        fun GetInterpolatedFrame(
            frame: frameBlend_t,
            joints: Array<idJointQuat>,
            index: IntArray,
            numIndexes: Int
        ) {
            val jointframe1: FloatArray
            var jf1_ptr: Int
            var jf2_ptr: Int
            var infoPtr: jointAnimInfo_t
            var animBits: Int
            var jointPtr: idJointQuat
            var blendPtr: idJointQuat

            // copy the baseframe
            for (i in 0 until baseFrame.Num()) {
                joints[i] = idJointQuat(baseFrame[i])
            }

            if (0 == numAnimatedComponents) {
                return
            }
            val blendJoints: Array<idJointQuat> = Array(baseFrame.Num()) { idJointQuat() }
            val lerpIndex = IntArray(baseFrame.Num())
            var numLerpJoints = 0

            val f1_ptr: Int = frame.frame1 * numAnimatedComponents
            val f2_ptr: Int = frame.frame2 * numAnimatedComponents
            val jointframe2: FloatArray = componentFrames.Ptr()
            jointframe1 = jointframe2
            var i = 0
            while (i < numIndexes) {
                val j = index[i]
                jointPtr = joints[j]
                blendJoints[j] = idJointQuat()
                blendPtr = blendJoints[j]
                infoPtr = jointInfo[j]
                animBits = infoPtr.animBits
                if (animBits != 0) {
                    lerpIndex[numLerpJoints++] = j

//			jointframe2 = frame2 ;
                    jf1_ptr = f1_ptr + infoPtr.firstComponent
                    jf2_ptr = f2_ptr + infoPtr.firstComponent
                    when (animBits and (ANIM_TX or ANIM_TY or ANIM_TZ)) {
                        0 -> blendPtr.t.set(jointPtr.t)
                        ANIM_TX -> {
                            jointPtr.t.x = jointframe1[jf1_ptr + 0]
                            blendPtr.t.x = jointframe2[jf2_ptr + 0]
                            blendPtr.t.y = jointPtr.t.y
                            blendPtr.t.z = jointPtr.t.z
                            jf1_ptr++
                            jf2_ptr++
                        }

                        ANIM_TY -> {
                            jointPtr.t.y = jointframe1[jf1_ptr + 0]
                            blendPtr.t.y = jointframe2[jf2_ptr + 0]
                            blendPtr.t.x = jointPtr.t.x
                            blendPtr.t.z = jointPtr.t.z
                            jf1_ptr++
                            jf2_ptr++
                        }

                        ANIM_TZ -> {
                            jointPtr.t.z = jointframe1[jf1_ptr + 0]
                            blendPtr.t.z = jointframe2[jf2_ptr + 0]
                            blendPtr.t.x = jointPtr.t.x
                            blendPtr.t.y = jointPtr.t.y
                            jf1_ptr++
                            jf2_ptr++
                        }

                        ANIM_TX or ANIM_TY -> {
                            jointPtr.t.x = jointframe1[jf1_ptr + 0]
                            jointPtr.t.y = jointframe1[jf1_ptr + 1]
                            blendPtr.t.x = jointframe2[jf2_ptr + 0]
                            blendPtr.t.y = jointframe2[jf2_ptr + 1]
                            blendPtr.t.z = jointPtr.t.z
                            jf1_ptr += 2
                            jf2_ptr += 2
                        }

                        ANIM_TX or ANIM_TZ -> {
                            jointPtr.t.x = jointframe1[jf1_ptr + 0]
                            jointPtr.t.z = jointframe1[jf1_ptr + 1]
                            blendPtr.t.x = jointframe2[jf2_ptr + 0]
                            blendPtr.t.z = jointframe2[jf2_ptr + 1]
                            blendPtr.t.y = jointPtr.t.y
                            jf1_ptr += 2
                            jf2_ptr += 2
                        }

                        ANIM_TY or ANIM_TZ -> {
                            jointPtr.t.y = jointframe1[jf1_ptr + 0]
                            jointPtr.t.z = jointframe1[jf1_ptr + 1]
                            blendPtr.t.y = jointframe2[jf2_ptr + 0]
                            blendPtr.t.z = jointframe2[jf2_ptr + 1]
                            blendPtr.t.x = jointPtr.t.x
                            jf1_ptr += 2
                            jf2_ptr += 2
                        }

                        ANIM_TX or ANIM_TY or ANIM_TZ -> {
                            jointPtr.t.x = jointframe1[jf1_ptr + 0]
                            jointPtr.t.y = jointframe1[jf1_ptr + 1]
                            jointPtr.t.z = jointframe1[jf1_ptr + 2]
                            blendPtr.t.x = jointframe2[jf2_ptr + 0]
                            blendPtr.t.y = jointframe2[jf2_ptr + 1]
                            blendPtr.t.z = jointframe2[jf2_ptr + 2]
                            jf1_ptr += 3
                            jf2_ptr += 3
                        }
                    }
                    when (animBits and (ANIM_QX or ANIM_QY or ANIM_QZ)) {
                        0 -> blendPtr.q.set(jointPtr.q)
                        ANIM_QX -> {
                            jointPtr.q.x = jointframe1[jf1_ptr + 0]
                            blendPtr.q.x = jointframe2[jf2_ptr + 0]
                            blendPtr.q.y = jointPtr.q.y
                            blendPtr.q.z = jointPtr.q.z
                            jointPtr.q.w = jointPtr.q.CalcW()
                            blendPtr.q.w = blendPtr.q.CalcW()
                        }

                        ANIM_QY -> {
                            jointPtr.q.y = jointframe1[jf1_ptr + 0]
                            blendPtr.q.y = jointframe2[jf2_ptr + 0]
                            blendPtr.q.x = jointPtr.q.x
                            blendPtr.q.z = jointPtr.q.z
                            jointPtr.q.w = jointPtr.q.CalcW()
                            blendPtr.q.w = blendPtr.q.CalcW()
                        }

                        ANIM_QZ -> {
                            jointPtr.q.z = jointframe1[jf1_ptr + 0]
                            blendPtr.q.z = jointframe2[jf2_ptr + 0]
                            blendPtr.q.x = jointPtr.q.x
                            blendPtr.q.y = jointPtr.q.y
                            jointPtr.q.w = jointPtr.q.CalcW()
                            blendPtr.q.w = blendPtr.q.CalcW()
                        }

                        ANIM_QX or ANIM_QY -> {
                            jointPtr.q.x = jointframe1[jf1_ptr + 0]
                            jointPtr.q.y = jointframe1[jf1_ptr + 1]
                            blendPtr.q.x = jointframe2[jf2_ptr + 0]
                            blendPtr.q.y = jointframe2[jf2_ptr + 1]
                            blendPtr.q.z = jointPtr.q.z
                            jointPtr.q.w = jointPtr.q.CalcW()
                            blendPtr.q.w = blendPtr.q.CalcW()
                        }

                        ANIM_QX or ANIM_QZ -> {
                            jointPtr.q.x = jointframe1[jf1_ptr + 0]
                            jointPtr.q.z = jointframe1[jf1_ptr + 1]
                            blendPtr.q.x = jointframe2[jf2_ptr + 0]
                            blendPtr.q.z = jointframe2[jf2_ptr + 1]
                            blendPtr.q.y = jointPtr.q.y
                            jointPtr.q.w = jointPtr.q.CalcW()
                            blendPtr.q.w = blendPtr.q.CalcW()
                        }

                        ANIM_QY or ANIM_QZ -> {
                            jointPtr.q.y = jointframe1[jf1_ptr + 0]
                            jointPtr.q.z = jointframe1[jf1_ptr + 1]
                            blendPtr.q.y = jointframe2[jf2_ptr + 0]
                            blendPtr.q.z = jointframe2[jf2_ptr + 1]
                            blendPtr.q.x = jointPtr.q.x
                            jointPtr.q.w = jointPtr.q.CalcW()
                            blendPtr.q.w = blendPtr.q.CalcW()
                        }

                        ANIM_QX or ANIM_QY or ANIM_QZ -> {
                            jointPtr.q.x = jointframe1[jf1_ptr + 0]
                            jointPtr.q.y = jointframe1[jf1_ptr + 1]
                            jointPtr.q.z = jointframe1[jf1_ptr + 2]
                            blendPtr.q.x = jointframe2[jf2_ptr + 0]
                            blendPtr.q.y = jointframe2[jf2_ptr + 1]
                            blendPtr.q.z = jointframe2[jf2_ptr + 2]
                            jointPtr.q.w = jointPtr.q.CalcW()
                            blendPtr.q.w = blendPtr.q.CalcW()
                        }
                    }
                }
                i++
            }
            SIMDProcessor!!.BlendJoints(joints, blendJoints, frame.backlerp, lerpIndex, numLerpJoints)
            if (frame.cycleCount != 0) {
                joints[0].t.plusAssign(totaldelta.times(frame.cycleCount.toFloat()))
            }
        }

        fun GetSingleFrame(framenum: Int, joints: Array<idJointQuat>, index: IntArray, numIndexes: Int) {
            //	float				[]frame;
            var jointframe: FloatArray
            var jf_ptr: Int
            var animBits: Int
            var jointPtr: idJointQuat
            var infoPtr: jointAnimInfo_t

            // copy the baseframe
            for (_i in 0 until baseFrame.Num()) {
                joints[_i] = idJointQuat(baseFrame[_i])
            }
            if (framenum == 0 || 0 == numAnimatedComponents) {
                // just use the base frame
                return
            }

            val f_ptr: Int = framenum * numAnimatedComponents
            var i = 0
            while (i < numIndexes) {
                val j = index[i]
                jointPtr = joints[j]
                infoPtr = jointInfo[j]
                animBits = infoPtr.animBits
                if (animBits != 0) {
                    jointframe = componentFrames.Ptr()
                    jf_ptr = f_ptr + infoPtr.firstComponent
                    if ((animBits and (ANIM_TX or ANIM_TY or ANIM_TZ)) != 0) {
                        if ((animBits and ANIM_TX) != 0) {
                            jointPtr.t.x = jointframe[jf_ptr++]
                        }
                        if ((animBits and ANIM_TY) != 0) {
                            jointPtr.t.y = jointframe[jf_ptr++]
                        }
                        if ((animBits and ANIM_TZ) != 0) {
                            jointPtr.t.z = jointframe[jf_ptr++]
                        }
                    }
                    if ((animBits and (ANIM_QX or ANIM_QY or ANIM_QZ)) != 0) {
                        if ((animBits and ANIM_QX) != 0) {
                            jointPtr.q.x = jointframe[jf_ptr++]
                        }
                        if ((animBits and ANIM_QY) != 0) {
                            jointPtr.q.y = jointframe[jf_ptr++]
                        }
                        if ((animBits and ANIM_QZ) != 0) {
                            jointPtr.q.z = jointframe[jf_ptr]
                        }
                        jointPtr.q.w = jointPtr.q.CalcW()
                    }
                }
                i++
            }
        }

        fun Length(): Int {
            return animLength
        }

        fun NumFrames(): Int {
            return numFrames
        }

        fun NumJoints(): Int {
            return numJoints
        }

        fun TotalMovementDelta(): idVec3 {
            return totaldelta
        }

        fun Name(): String {
            return name.toString()
        }

        fun GetFrameBlend(framenum: Int, frame: frameBlend_t) {    // frame 1 is first frame
            var framenum = framenum
            frame.cycleCount = 0
            frame.backlerp = 0.0f
            frame.frontlerp = 1.0f

            // frame 1 is first frame
            framenum--
            if (framenum < 0) {
                framenum = 0
            } else if (framenum >= numFrames) {
                framenum = numFrames - 1
            }
            frame.frame1 = framenum
            frame.frame2 = framenum
        }

        fun ConvertTimeToFrame(time: Int, cyclecount: Int, frame: frameBlend_t) {
            if (numFrames <= 1) {
                frame.frame1 = 0
                frame.frame2 = 0
                frame.backlerp = 0.0f
                frame.frontlerp = 1.0f
                frame.cycleCount = 0
                return
            }
            if (time <= 0) {
                frame.frame1 = 0
                frame.frame2 = 1
                frame.backlerp = 0.0f
                frame.frontlerp = 1.0f
                frame.cycleCount = 0
                return
            }
            val frameTime: Int = time * frameRate
            val frameNum: Int = frameTime / 1000
            frame.cycleCount = frameNum / (numFrames - 1)
            if (cyclecount > 0 && frame.cycleCount >= cyclecount) {
                frame.cycleCount = cyclecount - 1
                frame.frame1 = numFrames - 1
                frame.frame2 = frame.frame1
                frame.backlerp = 0.0f
                frame.frontlerp = 1.0f
                return
            }
            frame.frame1 = frameNum % (numFrames - 1)
            frame.frame2 = frame.frame1 + 1
            if (frame.frame2 >= numFrames) {
                frame.frame2 = 0
            }
            frame.backlerp = frameTime % 1000 * 0.001f
            frame.frontlerp = 1.0f - frame.backlerp
        }

        fun GetOrigin(offset: idVec3, time: Int, cyclecount: Int) {
            val frame = frameBlend_t()
            offset.set(baseFrame[0].t)

            if (jointInfo[0].animBits and (ANIM_TX or ANIM_TY or ANIM_TZ) == 0) {
                return  // just use baseframe
            }

            ConvertTimeToFrame(time, cyclecount, frame)

            var c1_ptr = numAnimatedComponents * frame.frame1 + jointInfo[0].firstComponent
            var c2_ptr = numAnimatedComponents * frame.frame2 + jointInfo[0].firstComponent

            val componentPtr1 = componentFrames.Ptr()
            val componentPtr2 = componentPtr1

            if ((jointInfo[0].animBits and ANIM_TX) != 0) {
                offset.x = componentPtr1[c1_ptr] * frame.frontlerp + componentPtr2[c2_ptr] * frame.backlerp
                c1_ptr++
                c2_ptr++
            }

            if ((jointInfo[0].animBits and ANIM_TY) != 0) {
                offset.y = componentPtr1[c1_ptr] * frame.frontlerp + componentPtr2[c2_ptr] * frame.backlerp
                c1_ptr++
                c2_ptr++
            }

            if ((jointInfo[0].animBits and ANIM_TZ) != 0) {
                offset.z = componentPtr1[c1_ptr] * frame.frontlerp + componentPtr2[c2_ptr] * frame.backlerp
            }

            if (frame.cycleCount != 0) {
                offset.plusAssign(totaldelta.times(frame.cycleCount.toFloat()))
            }
        }

        fun GetOriginRotation(rotation: idQuat, time: Int, cyclecount: Int) {
            val frame = frameBlend_t()
            val animBits: Int = jointInfo[0].animBits
            if (animBits and (ANIM_QX or ANIM_QY or ANIM_QZ) == 0) {
                // just use the baseframe
                rotation.set(baseFrame[0].q)
                return
            }
            ConvertTimeToFrame(time, cyclecount, frame)
            val jointframe1 = componentFrames.Ptr()
            var j1_ptr: Int = numAnimatedComponents * frame.frame1 + jointInfo[0].firstComponent
            val jointframe2 = jointframe1
            var j2_ptr: Int = numAnimatedComponents * frame.frame2 + jointInfo[0].firstComponent
            if ((animBits and ANIM_TX) != 0) {
                j1_ptr++
                j2_ptr++
            }
            if ((animBits and ANIM_TY) != 0) {
                j1_ptr++
                j2_ptr++
            }
            if ((animBits and ANIM_TZ) != 0) {
                j1_ptr++
                j2_ptr++
            }
            val q1 = idQuat()
            val q2 = idQuat()
            when (animBits and (ANIM_QX or ANIM_QY or ANIM_QZ)) {
                ANIM_QX -> {
                    q1.x = jointframe1[j1_ptr + 0]
                    q2.x = jointframe2[j2_ptr + 0]
                    q1.y = baseFrame[0].q.y
                    q2.y = q1.y
                    q1.z = baseFrame[0].q.z
                    q2.z = q1.z
                    q1.w = q1.CalcW()
                    q2.w = q2.CalcW()
                }

                ANIM_QY -> {
                    q1.y = jointframe1[j1_ptr + 0]
                    q2.y = jointframe2[j2_ptr + 0]
                    q1.x = baseFrame[0].q.x
                    q2.x = q1.x
                    q1.z = baseFrame[0].q.z
                    q2.z = q1.z
                    q1.w = q1.CalcW()
                    q2.w = q2.CalcW()
                }

                ANIM_QZ -> {
                    q1.z = jointframe1[j1_ptr + 0]
                    q2.z = jointframe2[j2_ptr + 0]
                    q1.x = baseFrame[0].q.x
                    q2.x = q1.x
                    q1.y = baseFrame[0].q.y
                    q2.y = q1.y
                    q1.w = q1.CalcW()
                    q2.w = q2.CalcW()
                }

                ANIM_QX or ANIM_QY -> {
                    q1.x = jointframe1[j1_ptr + 0]
                    q1.y = jointframe1[j1_ptr + 1]
                    q2.x = jointframe2[j2_ptr + 0]
                    q2.y = jointframe2[j2_ptr + 1]
                    q1.z = baseFrame[0].q.z
                    q2.z = q1.z
                    q1.w = q1.CalcW()
                    q2.w = q2.CalcW()
                }

                ANIM_QX or ANIM_QZ -> {
                    q1.x = jointframe1[j1_ptr + 0]
                    q1.z = jointframe1[j1_ptr + 1]
                    q2.x = jointframe2[j2_ptr + 0]
                    q2.z = jointframe2[j2_ptr + 1]
                    q1.y = baseFrame[0].q.y
                    q2.y = q1.y
                    q1.w = q1.CalcW()
                    q2.w = q2.CalcW()
                }

                ANIM_QY or ANIM_QZ -> {
                    q1.y = jointframe1[j1_ptr + 0]
                    q1.z = jointframe1[j1_ptr + 1]
                    q2.y = jointframe2[j2_ptr + 0]
                    q2.z = jointframe2[j2_ptr + 1]
                    q1.x = baseFrame[0].q.x
                    q2.x = q1.x
                    q1.w = q1.CalcW()
                    q2.w = q2.CalcW()
                }

                ANIM_QX or ANIM_QY or ANIM_QZ -> {
                    q1.x = jointframe1[j1_ptr + 0]
                    q1.y = jointframe1[j1_ptr + 1]
                    q1.z = jointframe1[j1_ptr + 2]
                    q2.x = jointframe2[j2_ptr + 0]
                    q2.y = jointframe2[j2_ptr + 1]
                    q2.z = jointframe2[j2_ptr + 2]
                    q1.w = q1.CalcW()
                    q2.w = q2.CalcW()
                }
            }
            rotation.Slerp(q1, q2, frame.backlerp)
        }

        fun GetBounds(bnds: idBounds, time: Int, cyclecount: Int) {
            val frame = frameBlend_t()
            val offset = idVec3()
            var c1_ptr: Int
            var c2_ptr: Int
            ConvertTimeToFrame(time, cyclecount, frame)
            bnds.set(bounds[frame.frame1])
            bnds.AddBounds(bounds[frame.frame2])

            // origin position
            offset.set(baseFrame[0].t)
            if ((jointInfo[0].animBits and (ANIM_TX or ANIM_TY or ANIM_TZ)) != 0) {
                val componentPtr1 = componentFrames.Ptr()
                c1_ptr = numAnimatedComponents * frame.frame1 + jointInfo[0].firstComponent
                val componentPtr2 = componentPtr1
                c2_ptr = numAnimatedComponents * frame.frame2 + jointInfo[0].firstComponent
                if ((jointInfo[0].animBits and ANIM_TX) != 0) {
                    offset.x = componentPtr1[c1_ptr] * frame.frontlerp + componentPtr2[c2_ptr] * frame.backlerp
                    c1_ptr++
                    c2_ptr++
                }
                if ((jointInfo[0].animBits and ANIM_TY) != 0) {
                    offset.y = componentPtr1[c1_ptr] * frame.frontlerp + componentPtr2[c2_ptr] * frame.backlerp
                    c1_ptr++
                    c2_ptr++
                }
                if ((jointInfo[0].animBits and ANIM_TZ) != 0) {
                    offset.z = componentPtr1[c1_ptr] * frame.frontlerp + componentPtr2[c2_ptr] * frame.backlerp
                }
            }
            bnds[0].minusAssign(offset)
            bnds[1].minusAssign(offset)
        }

        //
        //
        init {
            componentFrames = List.idFloatList()
            name = idStr()
            totaldelta = idVec3()
        }
    }

    class idAFPoseJointMod {
        val origin: idVec3 = idVec3()
        val axis: idMat3 = getMat3_identity()
        var mod: AFJointModType_t = AFJointModType_t.AF_JOINTMOD_AXIS
    }

    /*
     ==============================================================================================

     idAnimManager

     ==============================================================================================
     */
    class idAnimManager {
        private val animations: idHashTable<idMD5Anim> = idHashTable()
        private val jointnames: idStrList
        private val jointnamesHash: idHashIndex

        fun Shutdown() {
            animations.DeleteContents()
            jointnames.clear()
            jointnamesHash.Free()
        }

        fun GetAnim(name: String): idMD5Anim? {
            val animPtr = arrayOf<idMD5Anim?>(null)
            var anim: idMD5Anim?

            // see if it has been asked for before
            if (animations.Get(name, animPtr)) {
                anim = animPtr[0]
            } else {
                val extension = idStr()
                val filename = idStr(name)
                filename.ExtractFileExtension(extension)
                if (!extension.equals(MD5_ANIM_EXT)) {
                    return null
                }
                anim = idMD5Anim()
                if (!anim.LoadAnim(filename)) {
                    Game_local.gameLocal.Warning("Couldn't load anim: '%s'", filename)
                    anim = null
                }
                animations.Set(filename.toString(), anim)
            }

            return anim
        }

        fun ReloadAnims() {
            var animptr: idMD5Anim?
            var i = 0

            i = 0
            while (i < animations.Num()) {
                animptr = animations.GetIndex(i)
                if (animptr != null) {
                    animptr.Reload()
                }
                i++
            }
        }

        fun ListAnims() {
            var animptr: idMD5Anim?
            var anim: idMD5Anim
            var   /*size_t*/s: Int
            var num = 0
            var   /*size_t*/size = 0
            var i = 0
            num = 0
            size = 0
            i = 0
            while (i < animations.Num()) {
                animptr = animations.GetIndex(i)
                if (animptr != null) {
                    anim = animptr
                    s = anim.Size()
                    Game_local.gameLocal.Printf("%8d bytes : %2d refs : %s\n", s, anim.NumRefs(), anim.Name())
                    size += s
                    num++
                }
                i++
            }

            var namesize: Int = jointnames.sizeStrings() + jointnamesHash.Size()
            i = 0
            while (i < jointnames.size()) {
                namesize += jointnames[i].Size()
                i++
            }

            Game_local.gameLocal.Printf("\n%d memory used in %d anims\n", size, num)
            Game_local.gameLocal.Printf("%d memory used in %d joint names\n", namesize, jointnames.size())
        }

        fun JointIndex(name: String): Int {
            var i: Int
            val hash: Int = jointnamesHash.GenerateKey(name)
            i = jointnamesHash.First(hash)
            while (i != -1) {
                if (jointnames[i].Cmp(name) == 0) {
                    return i
                }
                i = jointnamesHash.Next(i)
            }
            i = jointnames.add(name)
            jointnamesHash.Add(hash, i)
            return i
        }

        fun JointName(index: Int): String {
            return jointnames[index].toString()
        }

        fun FlushUnusedAnims() {
            var animptr: idMD5Anim?
            val removeAnims = List.idList<idMD5Anim>()
            var i = 0
            i = 0
            while (i < animations.Num()) {
                animptr = animations.GetIndex(i)
                if (animptr != null) {
                    if (animptr.NumRefs() <= 0) {
                        removeAnims.Append(animptr)
                    }
                }
                i++
            }

            i = 0
            while (i < removeAnims.Num()) {
                animations.Remove(removeAnims[i].Name())
                i++
            }
        }

        companion object {
            var forceExport = false
        }

        init {
            jointnames = idStrList()
            jointnamesHash = idHashIndex()
        }
    }
}

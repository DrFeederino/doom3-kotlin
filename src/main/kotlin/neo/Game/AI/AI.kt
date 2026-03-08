/*
 * Copyright (C) 1999-2011 id Software LLC, a ZeniMax Media company.
 * Translated to Kotlin by Dr. Feederino with support of Claude Code
 *
 * This file is part of the Doom 3 Kotlin project.
 * Original source: neo/Game/ai/AI.cpp, neo/Game/ai/AI.h
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
 */

package neo.Game.AI

import neo.Game.*
import neo.Game.Animation.Anim
import neo.Game.Animation.Anim.animFlags_t
import neo.Game.Animation.Anim.frameCommandType_t
import neo.Game.Animation.Anim.frameCommand_t
import neo.Game.Animation.Anim.jointModTransform_t
import neo.Game.Animation.idAnim
import neo.Game.Animation.idDeclModelDef
import neo.Game.GameSys.Class.*
import neo.Game.GameSys.EV_Remove
import neo.Game.GameSys.Event.idEventDef
import neo.Game.GameSys.SaveGame.idRestoreGame
import neo.Game.GameSys.SaveGame.idSaveGame
import neo.Game.GameSys.SysCvar
import neo.Game.Game_local.*
import neo.Game.Misc.idPathCorner
import neo.Game.Moveable.idMoveable
import neo.Game.Physics.Clip.idClipModel
import neo.Game.Physics.Physics.idPhysics
import neo.Game.Physics.Physics_Monster.idPhysics_Monster
import neo.Game.Physics.Physics_Monster.monsterMoveResult_t
import neo.Game.Player.idPlayer
import neo.Game.Projectile.idProjectile
import neo.Game.Projectile.idSoulCubeMissile
import neo.Game.Pvs.pvsHandle_t
import neo.Game.Script.Script_Program.idScriptBool
import neo.Game.Script.Script_Program.idScriptFloat
import neo.Game.Script.Script_Thread.idThread
import neo.Renderer.Material
import neo.Renderer.Model
import neo.Renderer.RenderWorld
import neo.Renderer.RenderWorld.renderLight_s
import neo.Sound.snd_shader.idSoundShader
import neo.TempDump
import neo.TempDump.btoi
import neo.Tools.Compilers.AAS.AASFile
import neo.cm.CM_CLIP_EPSILON
import neo.cm.trace_s
import neo.framework.CmdSystem.cmdFunction_t
import neo.framework.DeclManager
import neo.framework.DeclManager.declType_t
import neo.framework.DeclParticle.idDeclParticle
import neo.idlib.*
import neo.idlib.BV.idBounds
import neo.idlib.Dict_h.idDict
import neo.idlib.Dict_h.idKeyValue
import neo.idlib.Text.Str
import neo.idlib.Text.Str.idStr
import neo.idlib.containers.CBool
import neo.idlib.containers.CFloat
import neo.idlib.containers.CInt
import neo.idlib.containers.List.idList
import neo.idlib.geometry.TraceModel.idTraceModel
import neo.idlib.math.*
import neo.idlib.math.Matrix.idMat3
import kotlin.math.abs

val EV_CombatNode_MarkUsed: idEventDef = idEventDef("markUsed")

// path prediction
// typedef enum {
val SE_BLOCKED: Int = BIT(0)
val SE_ENTER_LEDGE_AREA: Int = BIT(1)
val SE_ENTER_OBSTACLE: Int = BIT(2)
val SE_FALL: Int = BIT(3)
val SE_LAND: Int = BIT(4)
const val AI_FLY_DAMPENING = 0.15f
const val AI_HEARING_RANGE = 2048.0f
const val AI_SEEK_PREDICTION = 0.3f
const val AI_TURN_PREDICTION = 0.2f
const val AI_TURN_SCALE = 60.0f

//
const val ATTACK_IGNORE = 0
const val ATTACK_ON_ACTIVATE = 2
const val ATTACK_ON_DAMAGE = 1
const val ATTACK_ON_SIGHT = 4
const val DEFAULT_FLY_OFFSET = 68
const val DI_NODIR = -1.0f

/*
     ===============================================================================

     idAI

     ===============================================================================
     */
const val SQUARE_ROOT_OF_2 = 1.414213562f
val moveCommandString /*[ NUM_MOVE_COMMANDS ]*/: Array<String> = arrayOf(
    "MOVE_NONE",
    "MOVE_FACE_ENEMY",
    "MOVE_FACE_ENTITY",
    "MOVE_TO_ENEMY",
    "MOVE_TO_ENEMYHEIGHT",
    "MOVE_TO_ENTITY",
    "MOVE_OUT_OF_RANGE",
    "MOVE_TO_ATTACK_POSITION",
    "MOVE_TO_COVER",
    "MOVE_TO_POSITION",
    "MOVE_TO_POSITION_DIRECT",
    "MOVE_SLIDE_TO_POSITION",
    "MOVE_WANDER"
)

/*
     ============
     ValidForBounds
     ============
     */
fun ValidForBounds(settings: AASFile.idAASSettings, bounds: idBounds): Boolean {
    var i: Int
    i = 0
    while (i < 3) {
        if (bounds[0, i] < settings.boundingBoxes[0][0, i]) {
            return false
        }
        if (bounds[1, i] > settings.boundingBoxes[0][1, i]) {
            return false
        }
        i++
    }
    return true
}

/*
     =====================
     Seek
     =====================
     */
fun Seek(vel: idVec3, org: idVec3, goal: idVec3, prediction: Float): idVec3 {
    val predictedPos = idVec3()
    val goalDelta = idVec3()
    val seekVel = idVec3()

    // predict our position
    predictedPos.set(org + vel * prediction)
    goalDelta.set(goal - predictedPos)
    seekVel.set(goalDelta * MS2SEC(idGameLocal.msec.toFloat()))
    return seekVel
}

enum class moveCommand_t {
    MOVE_NONE,
    MOVE_FACE_ENEMY,
    MOVE_FACE_ENTITY,

    // commands < NUM_NONMOVING_COMMANDS don't cause a change in position
    NUM_NONMOVING_COMMANDS,  //
    MOVE_TO_ENEMY,  // = NUM_NONMOVING_COMMANDS,
    MOVE_TO_ENEMYHEIGHT,
    MOVE_TO_ENTITY,
    MOVE_OUT_OF_RANGE,
    MOVE_TO_ATTACK_POSITION,
    MOVE_TO_COVER,
    MOVE_TO_POSITION,
    MOVE_TO_POSITION_DIRECT,
    MOVE_SLIDE_TO_POSITION,
    MOVE_WANDER,
    NUM_MOVE_COMMANDS
}

// status results from move commands
// make sure to change script/doom_defs.script if you add any, or change their order
enum class moveStatus_t {
    MOVE_STATUS_DONE, MOVE_STATUS_MOVING, MOVE_STATUS_WAITING, MOVE_STATUS_DEST_NOT_FOUND, MOVE_STATUS_DEST_UNREACHABLE, MOVE_STATUS_BLOCKED_BY_WALL, MOVE_STATUS_BLOCKED_BY_OBJECT, MOVE_STATUS_BLOCKED_BY_ENEMY, MOVE_STATUS_BLOCKED_BY_MONSTER
}

// } stopEvent_t;
//
// defined in script/ai_base.script.  please keep them up to date.
enum class moveType_t {
    MOVETYPE_DEAD, MOVETYPE_ANIM, MOVETYPE_SLIDE, MOVETYPE_FLY, MOVETYPE_STATIC, NUM_MOVETYPES
}

enum class talkState_t {
    TALK_NEVER, TALK_DEAD, TALK_OK, TALK_BUSY, NUM_TALK_STATES
}

// obstacle avoidance
class obstaclePath_s {
    val seekPos = idVec3() // seek position avoiding obstacles
    val seekPosOutsideObstacles = idVec3() // seek position outside obstacles
    val startPosOutsideObstacles = idVec3() // start position outside obstacles
    var firstObstacle // if != NULL the first obstacle along the path
            : idEntity? = null
    var seekPosObstacle // if != NULL the obstacle containing the seek position
            : idEntity? = null
    var startPosObstacle // if != NULL the obstacle containing the start position
            : idEntity? = null
}

class predictedPath_s {
    val endNormal: idVec3 = idVec3() // normal of blocking surface
    val endPos: idVec3 = idVec3() // final position
    val endVelocity: idVec3 = idVec3() // velocity at end position
    var blockingEntity // entity that blocks the movement
            : idEntity? = null
    var endEvent // event that stopped the prediction
            = 0
    var endTime // time predicted
            = 0
}

class particleEmitter_s {
    var   /*jointHandle_t*/joint: Int = Model.INVALID_JOINT
    var particle: idDeclParticle? = null
    var time = 0
}

class idMoveState {
    val goalEntity: idEntityPtr<idEntity?>
    val goalEntityOrigin // move to entity uses this to avoid checking the floor position every frame
            : idVec3
    val lastMoveOrigin: idVec3
    val moveDest: idVec3
    val moveDir // used for wandering and slide moves
            : idVec3
    val obstacle: idEntityPtr<idEntity?>
    var anim: Int
    var blockTime: Int
    var duration: Int
    var lastMoveTime: Int
    var moveCommand: moveCommand_t
    var moveStatus: moveStatus_t
    var moveType: moveType_t
    var nextWanderTime: Int
    var range: Float
    var speed // only used by flying creatures
            : Float
    var startTime: Int
    var toAreaNum: Int
    var wanderYaw: Float

    /*
     ============
     idMoveState::Save
     ============
     */
    fun Save(savefile: idSaveGame) {
        savefile.WriteInt(moveType.ordinal)
        savefile.WriteInt(moveCommand.ordinal)
        savefile.WriteInt(moveStatus.ordinal)
        savefile.WriteVec3(moveDest)
        savefile.WriteVec3(moveDir)
        goalEntity.Save(savefile)
        savefile.WriteVec3(goalEntityOrigin)
        savefile.WriteInt(toAreaNum)
        savefile.WriteInt(startTime)
        savefile.WriteInt(duration)
        savefile.WriteFloat(speed)
        savefile.WriteFloat(range)
        savefile.WriteFloat(wanderYaw)
        savefile.WriteInt(nextWanderTime)
        savefile.WriteInt(blockTime)
        obstacle.Save(savefile)
        savefile.WriteVec3(lastMoveOrigin)
        savefile.WriteInt(lastMoveTime)
        savefile.WriteInt(anim)
    }

    /*
     ============
     idMoveState::Restore
     ============
     */
    fun Restore(savefile: idRestoreGame) {
        moveType = moveType_t.entries.toTypedArray()[savefile.ReadInt()]
        moveCommand = moveCommand_t.entries.toTypedArray()[savefile.ReadInt()]
        moveStatus = moveStatus_t.entries.toTypedArray()[savefile.ReadInt()]
        savefile.ReadVec3(moveDest)
        savefile.ReadVec3(moveDir)
        goalEntity.Restore(savefile)
        savefile.ReadVec3(goalEntityOrigin)
        toAreaNum = savefile.ReadInt()
        startTime = savefile.ReadInt()
        duration = savefile.ReadInt()
        speed = savefile.ReadFloat()
        range = savefile.ReadFloat()
        wanderYaw = savefile.ReadFloat()
        nextWanderTime = savefile.ReadInt()
        blockTime = savefile.ReadInt()
        obstacle.Restore(savefile)
        savefile.ReadVec3(lastMoveOrigin)
        lastMoveTime = savefile.ReadInt()
        anim = savefile.ReadInt()
    }

    /*
     ============
     idMoveState::idMoveState
     ============
     */
    init {
        moveType = moveType_t.MOVETYPE_ANIM
        moveCommand = moveCommand_t.MOVE_NONE
        moveStatus = moveStatus_t.MOVE_STATUS_DONE
        moveDest = idVec3()
        moveDir = idVec3(1.0f, 0.0f, 0.0f)
        goalEntity = idEntityPtr()
        goalEntityOrigin = idVec3()
        toAreaNum = 0
        startTime = 0
        duration = 0
        speed = 0.0f
        range = 0.0f
        wanderYaw = 0.0f
        nextWanderTime = 0
        blockTime = 0
        obstacle = idEntityPtr()
        lastMoveOrigin = vec3_origin
        lastMoveTime = 0
        anim = 0
    }

    // Deep copy all fields from another idMoveState
    fun copyFrom(other: idMoveState) {
        moveType = other.moveType
        moveCommand = other.moveCommand
        moveStatus = other.moveStatus
        moveDest.set(other.moveDest)
        moveDir.set(other.moveDir)
        goalEntity.oSet(other.goalEntity.GetEntity())
        goalEntityOrigin.set(other.goalEntityOrigin)
        toAreaNum = other.toAreaNum
        startTime = other.startTime
        duration = other.duration
        speed = other.speed
        range = other.range
        wanderYaw = other.wanderYaw
        nextWanderTime = other.nextWanderTime
        blockTime = other.blockTime
        obstacle.oSet(other.obstacle.GetEntity())
        lastMoveOrigin.set(other.lastMoveOrigin)
        lastMoveTime = other.lastMoveTime
        anim = other.anim
    }
}

class idAASFindCover(hideFromPos: idVec3) : AAS.idAASCallback() {
    private val PVSAreas: IntArray = IntArray(idEntity.MAX_PVS_AREAS)
    private val hidePVS: pvsHandle_t

    // FIX: C++ destructor calls FreeCurrentPVS. Kotlin has no deterministic destructor,
    // so callers must call cleanup() explicitly after use.
    fun cleanup() {
        Game_local.gameLocal.pvs.FreeCurrentPVS(hidePVS)
    }

    // ~idAASFindCover();
    /*
     ============
     idAASFindCover::TestArea
     ============
     */
    override fun TestArea(aas: AAS.idAAS, areaNum: Int): Boolean {
        val areaCenter = idVec3()
        val numPVSAreas: Int
        val PVSAreas = IntArray(idEntity.MAX_PVS_AREAS)
        areaCenter.set(aas.AreaCenter(areaNum))
        areaCenter.plusAssign(2, 1.0f)
        numPVSAreas = Game_local.gameLocal.pvs.GetPVSAreas(
            idBounds(areaCenter).Expand(16.0f),
            PVSAreas,
            idEntity.MAX_PVS_AREAS
        )
        return !Game_local.gameLocal.pvs.InCurrentPVS(hidePVS, PVSAreas, numPVSAreas)
    }

    //
    init {
        val numPVSAreas: Int
        val bounds = idBounds(hideFromPos - idVec3(16.0f, 16.0f, 0.0f), hideFromPos + idVec3(16.0f, 16.0f, 64.0f))
        // setup PVS
        numPVSAreas = Game_local.gameLocal.pvs.GetPVSAreas(bounds, PVSAreas, idEntity.MAX_PVS_AREAS)
        hidePVS = Game_local.gameLocal.pvs.SetupCurrentPVS(PVSAreas, numPVSAreas)
    }
}

class idAASFindAreaOutOfRange(targetPos: idVec3, maxDist: Float) : AAS.idAASCallback() {
    private val maxDistSqr: Float
    private val targetPos: idVec3 = idVec3()

    /*
     ============
     idAASFindAreaOutOfRange::TestArea
     ============
     */
    override fun TestArea(aas: AAS.idAAS, areaNum: Int): Boolean {
        val areaCenter = aas.AreaCenter(areaNum)
        val trace = trace_s()
        val dist: Float
        dist = (targetPos.ToVec2() - areaCenter.ToVec2()).LengthSqr()
        if (maxDistSqr > 0.0f && dist < maxDistSqr) {
            return false
        }
        Game_local.gameLocal.clip.TracePoint(
            trace,
            targetPos,
            areaCenter + idVec3(0.0f, 0.0f, 1.0f),
            Game_local.MASK_OPAQUE,
            null
        )
        return trace.fraction >= 1.0f
    }

    //
    //
    init {
        this.targetPos.set(targetPos)
        maxDistSqr = maxDist * maxDist
    }
}

class idAASFindAttackPosition(
    self: idAI,
    gravityAxis: idMat3,
    target: idEntity,
    targetPos: idVec3,
    fireOffset: idVec3
) : AAS.idAASCallback() {
    private val PVSAreas: IntArray = IntArray(idEntity.MAX_PVS_AREAS)
    private val excludeBounds: idBounds
    private val fireOffset: idVec3 = idVec3()
    private val gravityAxis: idMat3 = idMat3()
    private var self: idAI = idAI()
    private val target: idEntity
    private val targetPVS: pvsHandle_t
    private val targetPos: idVec3 = idVec3()

    // ~idAASFindAttackPosition();
    // FIX: C++ destructor calls FreeCurrentPVS. Kotlin has no deterministic destructor,
    // so callers must call cleanup() explicitly after use.
    fun cleanup() {
        Game_local.gameLocal.pvs.FreeCurrentPVS(targetPVS)
    }

    /*
     ============
     idAASFindAttackPosition::TestArea
     ============
     */
    override fun TestArea(aas: AAS.idAAS, areaNum: Int): Boolean {
        val dir = idVec3()
        val local_dir = idVec3()
        val fromPos = idVec3()
        val axis: idMat3
        val areaCenter = idVec3()
        val numPVSAreas: Int
        val PVSAreas = IntArray(idEntity.MAX_PVS_AREAS)
        areaCenter.set(aas.AreaCenter(areaNum))
        areaCenter.plusAssign(2, 1.0f)
        if (excludeBounds.ContainsPoint(areaCenter)) {
            // too close to where we already are
            return false
        }
        numPVSAreas = Game_local.gameLocal.pvs.GetPVSAreas(
            idBounds(areaCenter).Expand(16.0f),
            PVSAreas,
            idEntity.MAX_PVS_AREAS
        )
        if (!Game_local.gameLocal.pvs.InCurrentPVS(targetPVS, PVSAreas, numPVSAreas)) {
            return false
        }

        // calculate the world transform of the launch position
        dir.set(targetPos.minus(areaCenter))
        gravityAxis.ProjectVector(dir, local_dir)
        local_dir.z = 0.0f
        local_dir.ToVec2_Normalize()
        axis = local_dir.ToMat3()
        fromPos.set(areaCenter.plus(fireOffset.times(axis)))
        return self.GetAimDir(fromPos, target, self, dir)
    }

    //
    //
    init {
        val numPVSAreas: Int
        this.target = target
        this.targetPos.set(targetPos)
        this.fireOffset.set(fireOffset)
        this.self = self
        this.gravityAxis.set(gravityAxis)
        excludeBounds = idBounds(idVec3(-64.0f, -64.0f, -8.0f), idVec3(64.0f, 64.0f, 64.0f))
        excludeBounds.TranslateSelf(self.GetPhysics().GetOrigin())

        // setup PVS
        val bounds = idBounds(targetPos.minus(idVec3(16, 16, 0)), targetPos.plus(idVec3(16, 16, 64)))
        numPVSAreas = Game_local.gameLocal.pvs.GetPVSAreas(bounds, PVSAreas, idEntity.MAX_PVS_AREAS)
        targetPVS = Game_local.gameLocal.pvs.SetupCurrentPVS(PVSAreas, numPVSAreas)
    }
}

open class idAI : idActor() {
    companion object {
        val Type = idTypeInfo("idAI", "idActor") { idAI() }

        private val eventCallbacks: MutableMap<idEventDef, eventCallback_t<*>> = HashMap()

        /*
         ============
         idAI::FindPathAroundObstacles

         Finds a path around dynamic obstacles using a path tree with clockwise and counter clockwise edge walks.
         ============
         */
        fun FindPathAroundObstacles(
            physics: idPhysics,
            aas: AAS.idAAS?,
            ignore: idEntity?,
            startPos: idVec3,
            seekPos: idVec3,
            path: obstaclePath_s
        ): Boolean {
            val numObstacles: Int
            val areaNum: Int
            val insideObstacle = CInt()
            val obstacles = Array(MAX_OBSTACLES) { obstacle_s() }
            val clipBounds = idBounds()
            val bounds = idBounds()
            val root: pathNode_s?
            val pathToGoalExists: Boolean
            path.seekPos.set(seekPos)
            path.firstObstacle = null
            path.startPosOutsideObstacles.set(startPos)
            path.startPosObstacle = null
            path.seekPosOutsideObstacles.set(seekPos)
            path.seekPosObstacle = null
            if (null == aas) {
                return true
            }
            // FIX: C++ copies by value; Kotlin reference would corrupt shared settings
            bounds[1].set(aas.GetSettings()!!.boundingBoxes[0][1])
            bounds[0].set(bounds[1].unaryMinus())
            bounds[1].z = 32.0f

            // get the AAS area number and a valid point inside that area
            areaNum = aas.PointReachableAreaNum(
                path.startPosOutsideObstacles,
                bounds,
                AASFile.AREA_REACHABLE_WALK or AASFile.AREA_REACHABLE_FLY
            )
            aas.PushPointIntoAreaNum(areaNum, path.startPosOutsideObstacles)

            // get all the nearby obstacles
            numObstacles = GetObstacles(
                physics,
                aas,
                ignore,
                areaNum,
                path.startPosOutsideObstacles,
                path.seekPosOutsideObstacles,
                obstacles,
                MAX_OBSTACLES,
                clipBounds
            )

            // get a source position outside the obstacles
            // FIX: ToVec2() returns a disconnected copy in Kotlin. In C++, it returns a reference
            // to the first two components of the idVec3, so modifications propagate back.
            // Use a temp idVec2 and write back to the idVec3.
            val startPosVec2 = path.startPosOutsideObstacles.ToVec2()
            GetPointOutsideObstacles(
                obstacles,
                numObstacles,
                startPosVec2,
                insideObstacle,
                CInt()
            )
            path.startPosOutsideObstacles.set(startPosVec2)
            if (insideObstacle.integerValue != -1) {
                path.startPosObstacle = obstacles[insideObstacle.integerValue].entity
            }

            // get a goal position outside the obstacles
            val seekPosVec2 = path.seekPosOutsideObstacles.ToVec2()
            GetPointOutsideObstacles(
                obstacles,
                numObstacles,
                seekPosVec2,
                insideObstacle,
                CInt()
            )
            path.seekPosOutsideObstacles.set(seekPosVec2)
            if (insideObstacle.integerValue != -1) {
                path.seekPosObstacle = obstacles[insideObstacle.integerValue].entity
            }

            // if start and destination are pushed to the same point, we don't have a path around the obstacle
            if (path.seekPosOutsideObstacles.ToVec2().minus(path.startPosOutsideObstacles.ToVec2())
                    .LengthSqr() < Square(1.0f)
            ) {
                if (seekPos.ToVec2().minus(startPos.ToVec2()).LengthSqr() > Square(2.0f)) {
                    return false
                }
            }

            // build a path tree
            root = BuildPathTree(
                obstacles,
                numObstacles,
                clipBounds,
                path.startPosOutsideObstacles.ToVec2(),
                path.seekPosOutsideObstacles.ToVec2(),
                path
            )

            // draw the path tree
            if (SysCvar.ai_showObstacleAvoidance.GetBool()) {
                DrawPathTree(root, physics.GetOrigin().z)
            }

            // prune the tree
            PrunePathTree(root, path.seekPosOutsideObstacles.ToVec2())

            // find the optimal path
            pathToGoalExists = FindOptimalPath(
                root,
                obstacles,
                numObstacles,
                physics.GetOrigin().z,
                physics.GetLinearVelocity(),
                path.seekPos
            )

            // free the tree
            FreePathTree_r(root)
            return pathToGoalExists
        }

        /*
         ============
         idAI::FreeObstacleAvoidanceNodes

         Frees any nodes used for the dynamic obstacle avoidance.
         ============
         */
        fun FreeObstacleAvoidanceNodes() {
            pathNodeAllocator = 0
        }

        /*
         ============
         idAI::PredictPath

         Can also be used when there is no AAS file available however ledges are not detected.
         // Predicts movement, returns true if a stop event was triggered.
         ============
         */
        fun PredictPath(
            ent: idEntity,
            aas: AAS.idAAS?,
            start: idVec3,
            velocity: idVec3,
            totalTime: Int,
            frameTime: Int,
            stopEvent: Int,
            path: predictedPath_s
        ): Boolean {
            var i: Int
            var j: Int
            var step: Int
            val numFrames: Int
            var curFrameTime: Int
            val delta = idVec3()
            val curStart = idVec3()
            val curEnd = idVec3()
            val curVelocity = idVec3()
            val lastEnd = idVec3()
            val stepUp = idVec3()
            val tmpStart = idVec3()
            val gravity = idVec3()
            val gravityDir = idVec3()
            val invGravityDir = idVec3()
            val maxStepHeight: Float
            val minFloorCos: Float
            val trace = pathTrace_s()
            if (aas != null && aas.GetSettings() != null) {
                gravity.set(aas.GetSettings()!!.gravity)
                gravityDir.set(aas.GetSettings()!!.gravityDir)
                invGravityDir.set(aas.GetSettings()!!.invGravityDir)
                maxStepHeight = aas.GetSettings()!!.maxStepHeight._val
                minFloorCos = aas.GetSettings()!!.minFloorCos._val
            } else {
                gravity.set(Game_local.DEFAULT_GRAVITY_VEC3)
                gravityDir.set(idVec3(0, 0, -1))
                invGravityDir.set(idVec3(0, 0, 1))
                maxStepHeight = 14.0f
                minFloorCos = 0.7f
            }
            path.endPos.set(start)
            path.endVelocity.set(velocity)
            path.endNormal.Zero()
            path.endEvent = 0
            path.endTime = 0
            path.blockingEntity = null
            curStart.set(start)
            curVelocity.set(velocity)
            numFrames = (totalTime + frameTime - 1) / frameTime
            curFrameTime = frameTime
            i = 0
            while (i < numFrames) {
                if (i == numFrames - 1) {
                    curFrameTime = totalTime - i * curFrameTime
                }
                delta.set(curVelocity.times(curFrameTime.toFloat()).times(0.001f))
                path.endVelocity.set(curVelocity)
                path.endTime = i * frameTime

                // allow sliding along a few surfaces per frame
                j = 0
                while (j < MAX_FRAME_SLIDE) {
                    val lineStart = idVec3(curStart)

                    // allow stepping up three times per frame
                    step = 0
                    while (step < 3) {
                        curEnd.set(curStart.plus(delta))
                        if (PathTrace(ent, aas, curStart, curEnd, stopEvent, trace, path)) {
                            return true
                        }
                        if (step != 0) {

                            // step down at end point
                            tmpStart.set(trace.endPos)
                            curEnd.set(tmpStart.minus(stepUp))
                            if (PathTrace(ent, aas, tmpStart, curEnd, stopEvent, trace, path)) {
                                return true
                            }

                            // if not moved any further than without stepping up, or if not on a floor surface
                            if (lastEnd.minus(start).LengthSqr() > trace.endPos.minus(start).LengthSqr() - 0.1f
                                || trace.normal.times(invGravityDir) < minFloorCos
                            ) {
                                if ((stopEvent and SE_BLOCKED) != 0) {
                                    path.endPos.set(lastEnd)
                                    path.endEvent = SE_BLOCKED
                                    if (SysCvar.ai_debugMove.GetBool()) {
                                        Game_local.gameRenderWorld!!.DebugLine(
                                            colorRed,
                                            lineStart,
                                            lastEnd
                                        )
                                    }
                                    return true
                                }
                                curStart.set(lastEnd)
                                break
                            }
                        }
                        path.endNormal.set(trace.normal)
                        path.blockingEntity = trace.blockingEntity

                        // if the trace is not blocked or blocked by a floor surface
                        if (trace.fraction >= 1.0f || trace.normal.times(invGravityDir) > minFloorCos) {
                            curStart.set(trace.endPos)
                            break
                        }

                        // save last result
                        lastEnd.set(trace.endPos)

                        // step up
                        stepUp.set(invGravityDir.times(maxStepHeight))
                        if (PathTrace(
                                ent,
                                aas,
                                curStart,
                                curStart.plus(stepUp),
                                stopEvent,
                                trace,
                                path
                            )
                        ) {
                            return true
                        }
                        stepUp.timesAssign(trace.fraction)
                        curStart.set(trace.endPos)
                        step++
                    }
                    if (SysCvar.ai_debugMove.GetBool()) {
                        Game_local.gameRenderWorld!!.DebugLine(colorRed, lineStart, curStart)
                    }
                    if (trace.fraction >= 1.0f) {
                        break
                    }
                    delta.ProjectOntoPlane(trace.normal, OVERCLIP)
                    curVelocity.ProjectOntoPlane(trace.normal, OVERCLIP)
                    if ((stopEvent and SE_BLOCKED) != 0) {
                        // if going backwards
                        if (curVelocity.minus(gravityDir.times(curVelocity.times(gravityDir)))
                                .times(velocity.minus(gravityDir.times(velocity.times(gravityDir)))) < 0.0f
                        ) {
                            path.endPos.set(curStart)
                            path.endEvent = SE_BLOCKED
                            return true
                        }
                    }
                    j++
                }
                if (j >= MAX_FRAME_SLIDE) {
                    if ((stopEvent and SE_BLOCKED) != 0) {
                        path.endPos.set(curStart)
                        path.endEvent = SE_BLOCKED
                        return true
                    }
                }

                // add gravity
                curVelocity.plusAssign(gravity.times(frameTime.toFloat()).times(0.001f))
                i++
            }
            path.endTime = totalTime
            path.endVelocity.set(curVelocity)
            path.endPos.set(curStart)
            path.endEvent = 0
            return false
        }

        /*
         ===============================================================================

         Trajectory Prediction

         Finds the best collision free trajectory for a clip model based on an
         initial position, target position and speed.

         ===============================================================================
         */
        /*
         ============
         idAI::TestTrajectory

         Return true if the trajectory of the clip model is collision free.
         ============
         */
        fun TestTrajectory(
            start: idVec3,
            end: idVec3,
            zVel: Float,
            gravity: Float,
            time: Float,
            max_height: Float,
            clip: idClipModel?,
            clipmask: Int,
            ignore: idEntity?,
            targetEntity: idEntity?,
            drawtime: Int
        ): Boolean {
            var i: Int
            val numSegments: Int
            val maxHeight: Float
            var t: Float
            var t2: Float
            val points: Array<idVec3> = idVec3.generateArray(5)
            val trace = trace_s()
            var result: Boolean
            t = zVel / gravity
            // maximum height of projectile
            maxHeight = start.z - 0.5f * gravity * (t * t)
            // time it takes to fall from the top to the end height
            t = idMath.Sqrt((maxHeight - end.z) / (0.5f * -gravity))

            // start of parabolic
            points[0].set(start)
            if (t < time) {
                numSegments = 4
                // point in the middle between top and start
                t2 = (time - t) * 0.5f
                points[1].set(start.ToVec2().plus(end.ToVec2().minus(start.ToVec2()).times(t2 / time)))
                points[1].z = start.z + t2 * zVel + 0.5f * gravity * t2 * t2
                // top of parabolic
                t2 = time - t
                points[2].set(start.ToVec2().plus(end.ToVec2().minus(start.ToVec2()).times(t2 / time)))
                points[2].z = start.z + t2 * zVel + 0.5f * gravity * t2 * t2
                // point in the middel between top and end
                t2 = time - t * 0.5f
                points[3].set(start.ToVec2().plus(end.ToVec2().minus(start.ToVec2()).times(t2 / time)))
                points[3].z = start.z + t2 * zVel + 0.5f * gravity * t2 * t2
            } else {
                numSegments = 2
                // point halfway through
                t2 = time * 0.5f
                points[1].set(start.ToVec2().plus(end.ToVec2().minus(start.ToVec2()).times(0.5f)))
                points[1].z = start.z + t2 * zVel + 0.5f * gravity * t2 * t2
            }

            // end of parabolic
            points[numSegments].set(end)
            if (drawtime != 0) {
                i = 0
                while (i < numSegments) {
                    Game_local.gameRenderWorld!!.DebugLine(colorRed, points[i], points[i + 1], drawtime)
                    i++
                }
            }

            // make sure projectile doesn't go higher than we want it to go
            i = 0
            while (i < numSegments) {
                if (points[i].z > max_height) {
                    // goes higher than we want to allow
                    return false
                }
                i++
            }
            result = true
            i = 0
            while (i < numSegments) {
                Game_local.gameLocal.clip.Translation(
                    trace,
                    points[i],
                    points[i + 1],
                    clip,
                    idMat3.getMat3_identity(),
                    clipmask,
                    ignore
                )
                if (trace.fraction < 1.0f) {
                    result = Game_local.gameLocal.GetTraceEntity(trace) == targetEntity
                    break
                }
                i++
            }
            if (drawtime != 0) {
                if (clip != null) {
                    Game_local.gameRenderWorld!!.DebugBounds(
                        if (result) colorGreen else colorYellow,
                        clip.GetBounds().Expand(1.0f),
                        trace.endpos,
                        drawtime
                    )
                } else {
                    val bnds = idBounds(trace.endpos)
                    bnds.ExpandSelf(1.0f)
                    Game_local.gameRenderWorld!!.DebugBounds(
                        if (result) colorGreen else colorYellow,
                        bnds,
                        vec3_zero,
                        drawtime
                    )
                }
            }
            return result
        }

        /*
         =====================
         idAI::PredictTrajectory

         returns true if there is a collision free trajectory for the clip model
         aimDir is set to the ideal aim direction in order to hit the target
         // Finds the best collision free trajectory for a clip model.
         =====================
         */
        fun PredictTrajectory(
            firePos: idVec3,
            target: idVec3,
            projectileSpeed: Float,
            projGravity: idVec3,
            clip: idClipModel,
            clipmask: Int,
            max_height: Float,
            ignore: idEntity?,
            targetEntity: idEntity,
            drawtime: Int,
            aimDir: idVec3
        ): Boolean {
            val n: Int
            var i: Int
            var j: Int
            var zVel: Float
            val a: Float
            var t: Float
            var pitch: Float
            val s = CFloat()
            val c = CFloat()
            val trace = trace_s()
            val ballistics =
                Array(2) { ballistics_s() }
            val dir: Array<idVec3> = idVec3.generateArray(2)
            val velocity = idVec3()
            val lastPos = idVec3()
            val pos = idVec3()
            assert(targetEntity != null)

            // check if the projectile starts inside the target
            if (targetEntity.GetPhysics().GetAbsBounds().IntersectsBounds(clip.GetBounds().Translate(firePos))) {
                aimDir.set(target.minus(firePos))
                aimDir.Normalize()
                return true
            }

            // if no velocity or the projectile is not affected by gravity
            if (projectileSpeed <= 0.0f || projGravity == vec3_origin) {
                aimDir.set(target.minus(firePos))
                aimDir.Normalize()
                Game_local.gameLocal.clip.Translation(
                    trace,
                    firePos,
                    target,
                    clip,
                    idMat3.getMat3_identity(),
                    clipmask,
                    ignore
                )
                if (drawtime != 0) {
                    Game_local.gameRenderWorld!!.DebugLine(colorRed, firePos, target, drawtime)
                    val bnds = idBounds(trace.endpos)
                    bnds.ExpandSelf(1.0f)
                    Game_local.gameRenderWorld!!.DebugBounds(
                        if (trace.fraction >= 1.0f || Game_local.gameLocal.GetTraceEntity(
                                trace
                            ) === targetEntity
                        ) colorGreen else colorYellow,
                        bnds,
                        vec3_zero,
                        drawtime
                    )
                }
                return trace.fraction >= 1.0f || Game_local.gameLocal.GetTraceEntity(trace) === targetEntity
            }
            n = Ballistics(firePos, target, projectileSpeed, projGravity[2], ballistics)
            if (n == 0) {
                // there is no valid trajectory
                aimDir.set(target.minus(firePos))
                aimDir.Normalize()
                return false
            }

            // make sure the first angle is the smallest
            if (n == 2) {
                if (ballistics[1].angle < ballistics[0].angle) {
                    a = ballistics[0].angle
                    ballistics[0].angle = ballistics[1].angle
                    ballistics[1].angle = a
                    t = ballistics[0].time
                    ballistics[0].time = ballistics[1].time
                    ballistics[1].time = t
                }
            }

            // test if there is a collision free trajectory
            i = 0
            while (i < n) {
                pitch = DEG2RAD(ballistics[i].angle)
                idMath.SinCos(pitch, s, c)
                dir[i].set(target.minus(firePos))
                dir[i].z = 0.0f
                dir[i].timesAssign(c._val * idMath.InvSqrt(dir[i].LengthSqr()))
                dir[i].z = s._val
                zVel = projectileSpeed * dir[i].z
                if (SysCvar.ai_debugTrajectory.GetBool()) {
                    t = ballistics[i].time / 100.0f
                    velocity.set(dir[i].times(projectileSpeed))
                    lastPos.set(firePos)
                    pos.set(firePos)
                    j = 1
                    while (j < 100) {
                        pos.plusAssign(velocity.times(t))
                        velocity.plusAssign(projGravity.times(t))
                        Game_local.gameRenderWorld!!.DebugLine(colorCyan, lastPos, pos)
                        lastPos.set(pos)
                        j++
                    }
                }
                if (TestTrajectory(
                        firePos,
                        target,
                        zVel,
                        projGravity[2],
                        ballistics[i].time,
                        firePos.z + max_height,
                        clip,
                        clipmask,
                        ignore,
                        targetEntity,
                        drawtime
                    )
                ) {
                    aimDir.set(dir[i])
                    return true
                }
                i++
            }
            aimDir.set(dir[0])

            // there is no collision free trajectory
            return false
        }

        fun getEventCallBacks(): MutableMap<idEventDef, eventCallback_t<*>> {
            return eventCallbacks
        }

        init {
            eventCallbacks.putAll(idActor.getEventCallBacks())
            eventCallbacks[EV_Activate] = eventCallback_t1<idAI> { obj: idAI, activator: idEventArg<*>? ->
                obj.Event_Activate(activator as idEventArg<idEntity?>)
            }
            eventCallbacks[EV_Touch] =
                eventCallback_t2<idAI> { obj: idAI, _other: idEventArg<*>?, trace: idEventArg<*>? ->
                    obj.Event_Touch(
                        _other as idEventArg<idEntity>,
                        trace as idEventArg<trace_s?>
                    )
                }
            eventCallbacks[AI_FindEnemy] =
                eventCallback_t1<idAI> { obj: idAI, useFOV: idEventArg<*>? -> obj.Event_FindEnemy(useFOV as idEventArg<Int>) }
            eventCallbacks[AI_FindEnemyAI] =
                eventCallback_t1<idAI> { obj: idAI, useFOV: idEventArg<*>? -> obj.Event_FindEnemyAI(useFOV as idEventArg<Int>) }
            eventCallbacks[AI_FindEnemyInCombatNodes] =
                eventCallback_t0<idAI> { obj: idAI -> obj.Event_FindEnemyInCombatNodes() }
            eventCallbacks[AI_ClosestReachableEnemyOfEntity] =
                eventCallback_t1<idAI> { obj: idAI, _team_mate: idEventArg<*>? ->
                    obj.Event_ClosestReachableEnemyOfEntity(_team_mate as idEventArg<idEntity?>)
                }
            eventCallbacks[AI_HeardSound] =
                eventCallback_t1<idAI> { obj: idAI, ignore_team: idEventArg<*>? ->
                    obj.Event_HeardSound(ignore_team as idEventArg<Int>)
                }
            eventCallbacks[AI_SetEnemy] =
                eventCallback_t1<idAI> { obj: idAI, _ent: idEventArg<*>? -> obj.Event_SetEnemy(_ent as idEventArg<idEntity?>) }
            eventCallbacks[AI_ClearEnemy] =
                eventCallback_t0<idAI> { obj: idAI -> obj.Event_ClearEnemy() }
            eventCallbacks[AI_MuzzleFlash] =
                eventCallback_t1<idAI> { obj: idAI, jointname: idEventArg<*>? ->
                    obj.Event_MuzzleFlash(jointname as idEventArg<String?>)
                }
            eventCallbacks[AI_CreateMissile] =
                eventCallback_t1<idAI> { obj: idAI, _jointname: idEventArg<*>? ->
                    obj.Event_CreateMissile(_jointname as idEventArg<String?>)
                }
            eventCallbacks[AI_AttackMissile] =
                eventCallback_t1<idAI> { obj: idAI, jointname: idEventArg<*>? ->
                    obj.Event_AttackMissile(jointname as idEventArg<String?>)
                }
            eventCallbacks[AI_FireMissileAtTarget] =
                eventCallback_t2<idAI> { obj: idAI, jointname: idEventArg<*>?, targetname: idEventArg<*>? ->
                    obj.Event_FireMissileAtTarget(jointname as idEventArg<String>, targetname as idEventArg<String>)
                }
            eventCallbacks[AI_LaunchMissile] =
                eventCallback_t2<idAI> { obj: idAI, _muzzle: idEventArg<*>?, _ang: idEventArg<*>? ->
                    obj.Event_LaunchMissile(_muzzle as idEventArg<idVec3>, _ang as idEventArg<idAngles>)
                }
            eventCallbacks[AI_AttackMelee] =
                eventCallback_t1<idAI> { obj: idAI, meleeDefName: idEventArg<*>? ->
                    obj.Event_AttackMelee(meleeDefName as idEventArg<String>)
                }
            eventCallbacks[AI_DirectDamage] =
                eventCallback_t2<idAI> { obj: idAI, damageTarget: idEventArg<*>?, damageDefName: idEventArg<*>? ->
                    obj.Event_DirectDamage(
                        damageTarget as idEventArg<idEntity>,
                        damageDefName as idEventArg<String>
                    )
                }
            eventCallbacks[AI_RadiusDamageFromJoint] =
                eventCallback_t2<idAI> { obj: idAI, jointname: idEventArg<*>?, damageDefName: idEventArg<*>? ->
                    obj.Event_RadiusDamageFromJoint(
                        jointname as idEventArg<String>,
                        damageDefName as idEventArg<String>
                    )
                }
            eventCallbacks[AI_BeginAttack] =
                eventCallback_t1<idAI> { obj: idAI, name: idEventArg<*>? -> obj.Event_BeginAttack(name as idEventArg<String>) }
            eventCallbacks[AI_EndAttack] =
                eventCallback_t0<idAI> { obj: idAI -> obj.Event_EndAttack() }
            eventCallbacks[AI_MeleeAttackToJoint] =
                eventCallback_t2<idAI> { obj: idAI, jointname: idEventArg<*>?, meleeDefName: idEventArg<*>? ->
                    obj.Event_MeleeAttackToJoint(
                        jointname as idEventArg<String>,
                        meleeDefName as idEventArg<String>
                    )
                }
            eventCallbacks[AI_RandomPath] =
                eventCallback_t0<idAI> { obj: idAI -> obj.Event_RandomPath() }
            eventCallbacks[AI_CanBecomeSolid] =
                eventCallback_t0<idAI> { obj: idAI -> obj.Event_CanBecomeSolid() }
            eventCallbacks[AI_BecomeSolid] =
                eventCallback_t0<idAI> { obj: idAI -> obj.Event_BecomeSolid() }
            eventCallbacks[EV_BecomeNonSolid] =
                eventCallback_t0<idAI> { obj: idAI -> obj.Event_BecomeNonSolid() }
            eventCallbacks[AI_BecomeRagdoll] =
                eventCallback_t0<idAI> { obj: idAI -> obj.Event_BecomeRagdoll() }
            eventCallbacks[AI_StopRagdoll] =
                eventCallback_t0<idAI> { obj: idAI -> obj.Event_StopRagdoll() }
            eventCallbacks[AI_SetHealth] =
                eventCallback_t1<idAI> { obj: idAI, newHealth: idEventArg<*>? -> obj.Event_SetHealth(newHealth as idEventArg<Float>) }
            eventCallbacks[AI_GetHealth] =
                eventCallback_t0<idAI> { obj: idAI -> obj.Event_GetHealth() }
            eventCallbacks[AI_AllowDamage] =
                eventCallback_t0<idAI> { obj: idAI -> obj.Event_AllowDamage() }
            eventCallbacks[AI_IgnoreDamage] =
                eventCallback_t0<idAI> { obj: idAI -> obj.Event_IgnoreDamage() }
            eventCallbacks[AI_GetCurrentYaw] =
                eventCallback_t0<idAI> { obj: idAI -> obj.Event_GetCurrentYaw() }
            eventCallbacks[AI_TurnTo] =
                eventCallback_t1<idAI> { obj: idAI, angle: idEventArg<*>? -> obj.Event_TurnTo(angle as idEventArg<Float>) }
            eventCallbacks[AI_TurnToPos] =
                eventCallback_t1<idAI> { obj: idAI, pos: idEventArg<*>? -> obj.Event_TurnToPos(pos as idEventArg<idVec3>) }
            eventCallbacks[AI_TurnToEntity] =
                eventCallback_t1<idAI> { obj: idAI, ent: idEventArg<*>? -> obj.Event_TurnToEntity(ent as idEventArg<idEntity>) }
            eventCallbacks[AI_MoveStatus] =
                eventCallback_t0<idAI> { obj: idAI -> obj.Event_MoveStatus() }
            eventCallbacks[AI_StopMove] =
                eventCallback_t0<idAI> { obj: idAI -> obj.Event_StopMove() }
            eventCallbacks[AI_MoveToCover] =
                eventCallback_t0<idAI> { obj: idAI -> obj.Event_MoveToCover() }
            eventCallbacks[AI_MoveToEnemy] =
                eventCallback_t0<idAI> { obj: idAI -> obj.Event_MoveToEnemy() }
            eventCallbacks[AI_MoveToEnemyHeight] =
                eventCallback_t0<idAI> { obj: idAI -> obj.Event_MoveToEnemyHeight() }
            eventCallbacks[AI_MoveOutOfRange] =
                eventCallback_t2<idAI> { obj: idAI, entity: idEventArg<*>?, range: idEventArg<*>? ->
                    obj.Event_MoveOutOfRange(entity as idEventArg<idEntity>, range as idEventArg<Float>)
                }
            eventCallbacks[AI_MoveToAttackPosition] =
                eventCallback_t2<idAI> { obj: idAI, entity: idEventArg<*>?, attack_anim: idEventArg<*>? ->
                    obj.Event_MoveToAttackPosition(
                        entity as idEventArg<idEntity>,
                        attack_anim as idEventArg<String>
                    )
                }
            eventCallbacks[AI_Wander] =
                eventCallback_t0<idAI> { obj: idAI -> obj.Event_Wander() }
            eventCallbacks[AI_MoveToEntity] =
                eventCallback_t1<idAI> { obj: idAI, ent: idEventArg<*>? -> obj.Event_MoveToEntity(ent as idEventArg<idEntity>) }
            eventCallbacks[AI_MoveToPosition] =
                eventCallback_t1<idAI> { obj: idAI, pos: idEventArg<*>? -> obj.Event_MoveToPosition(pos as idEventArg<idVec3>) }
            eventCallbacks[AI_SlideTo] =
                eventCallback_t2<idAI> { obj: idAI, pos: idEventArg<*>?, time: idEventArg<*>? ->
                    obj.Event_SlideTo(
                        pos as idEventArg<idVec3>,
                        time as idEventArg<Float>
                    )
                }
            eventCallbacks[AI_FacingIdeal] =
                eventCallback_t0<idAI> { obj: idAI -> obj.Event_FacingIdeal() }
            eventCallbacks[AI_FaceEnemy] =
                eventCallback_t0<idAI> { obj: idAI -> obj.Event_FaceEnemy() }
            eventCallbacks[AI_FaceEntity] =
                eventCallback_t1<idAI> { obj: idAI, ent: idEventArg<*>? -> obj.Event_FaceEntity(ent as idEventArg<idEntity>) }
            eventCallbacks[AI_WaitAction] =
                eventCallback_t1<idAI> { obj: idAI, waitForState: idEventArg<*>? ->
                    obj.Event_WaitAction(waitForState as idEventArg<String>)
                }
            eventCallbacks[AI_GetCombatNode] =
                eventCallback_t0<idAI> { obj: idAI -> obj.Event_GetCombatNode() }
            eventCallbacks[AI_EnemyInCombatCone] =
                eventCallback_t2<idAI> { obj: idAI, _ent: idEventArg<*>?, use_current_enemy_location: idEventArg<*>? ->
                    obj.Event_EnemyInCombatCone(
                        _ent as idEventArg<idEntity>,
                        use_current_enemy_location as idEventArg<Int>
                    )
                }
            eventCallbacks[AI_WaitMove] =
                eventCallback_t0<idAI> { obj: idAI -> obj.Event_WaitMove() }
            eventCallbacks[AI_GetJumpVelocity] =
                eventCallback_t3<idAI> { obj: idAI, _pos: idEventArg<*>?, _speed: idEventArg<*>?,
                                         _max_height: idEventArg<*>? ->
                    obj.Event_GetJumpVelocity(
                        _pos as idEventArg<idVec3>,
                        _speed as idEventArg<Float>,
                        _max_height as idEventArg<Float>
                    )
                }
            eventCallbacks[AI_EntityInAttackCone] =
                eventCallback_t1<idAI> { obj: idAI, ent: idEventArg<*>? ->
                    obj.Event_EntityInAttackCone(ent as idEventArg<idEntity>)
                }
            eventCallbacks[AI_CanSeeEntity] =
                eventCallback_t1<idAI> { obj: idAI, ent: idEventArg<*>? -> obj.Event_CanSeeEntity(ent as idEventArg<idEntity>) }
            eventCallbacks[AI_SetTalkTarget] =
                eventCallback_t1<idAI> { obj: idAI, _target: idEventArg<*>? ->
                    obj.Event_SetTalkTarget(_target as idEventArg<idEntity?>)
                }
            eventCallbacks[AI_GetTalkTarget] =
                eventCallback_t0<idAI> { obj: idAI -> obj.Event_GetTalkTarget() }
            eventCallbacks[AI_SetTalkState] =
                eventCallback_t1<idAI> { obj: idAI, _state: idEventArg<*>? -> obj.Event_SetTalkState(_state as idEventArg<Int>) }
            eventCallbacks[AI_EnemyRange] =
                eventCallback_t0<idAI> { obj: idAI -> obj.Event_EnemyRange() }
            eventCallbacks[AI_EnemyRange2D] =
                eventCallback_t0<idAI> { obj: idAI -> obj.Event_EnemyRange2D() }
            eventCallbacks[AI_GetEnemy] =
                eventCallback_t0<idAI> { obj: idAI -> obj.Event_GetEnemy() }
            eventCallbacks[AI_GetEnemyPos] =
                eventCallback_t0<idAI> { obj: idAI -> obj.Event_GetEnemyPos() }
            eventCallbacks[AI_GetEnemyEyePos] =
                eventCallback_t0<idAI> { obj: idAI -> obj.Event_GetEnemyEyePos() }
            eventCallbacks[AI_PredictEnemyPos] =
                eventCallback_t1<idAI> { obj: idAI, time: idEventArg<*>? -> obj.Event_PredictEnemyPos(time as idEventArg<Float>) }
            eventCallbacks[AI_CanHitEnemy] =
                eventCallback_t0<idAI> { obj: idAI -> obj.Event_CanHitEnemy() }
            eventCallbacks[AI_CanHitEnemyFromAnim] =
                eventCallback_t1<idAI> { obj: idAI, animname: idEventArg<*>? ->
                    obj.Event_CanHitEnemyFromAnim(animname as idEventArg<String>)
                }
            eventCallbacks[AI_CanHitEnemyFromJoint] =
                eventCallback_t1<idAI> { obj: idAI, jointname: idEventArg<*>? ->
                    obj.Event_CanHitEnemyFromJoint(jointname as idEventArg<String>)
                }
            eventCallbacks[AI_EnemyPositionValid] =
                eventCallback_t0<idAI> { obj: idAI -> obj.Event_EnemyPositionValid() }
            eventCallbacks[AI_ChargeAttack] =
                eventCallback_t1<idAI> { obj: idAI, damageDef: idEventArg<*>? ->
                    obj.Event_ChargeAttack(damageDef as idEventArg<String>)
                }
            eventCallbacks[AI_TestChargeAttack] =
                eventCallback_t0<idAI> { obj: idAI -> obj.Event_TestChargeAttack() }
            eventCallbacks[AI_TestAnimMoveTowardEnemy] =
                eventCallback_t1<idAI> { obj: idAI, animname: idEventArg<*>? ->
                    obj.Event_TestAnimMoveTowardEnemy(animname as idEventArg<String>)
                }
            eventCallbacks[AI_TestAnimMove] =
                eventCallback_t1<idAI> { obj: idAI, animname: idEventArg<*>? ->
                    obj.Event_TestAnimMove(animname as idEventArg<String>)
                }
            eventCallbacks[AI_TestMoveToPosition] =
                eventCallback_t1<idAI> { obj: idAI, _position: idEventArg<*>? ->
                    obj.Event_TestMoveToPosition(_position as idEventArg<idVec3>)
                }
            eventCallbacks[AI_TestMeleeAttack] =
                eventCallback_t0<idAI> { obj: idAI -> obj.Event_TestMeleeAttack() }
            eventCallbacks[AI_TestAnimAttack] =
                eventCallback_t1<idAI> { obj: idAI, animname: idEventArg<*>? ->
                    obj.Event_TestAnimAttack(animname as idEventArg<String>)
                }
            eventCallbacks[AI_Shrivel] =
                eventCallback_t1<idAI> { obj: idAI, shrivel_time: idEventArg<*>? -> obj.Event_Shrivel(shrivel_time as idEventArg<Float>) }
            eventCallbacks[AI_Burn] =
                eventCallback_t0<idAI> { obj: idAI -> obj.Event_Burn() }
            eventCallbacks[AI_PreBurn] =
                eventCallback_t0<idAI> { obj: idAI -> obj.Event_PreBurn() }
            eventCallbacks[AI_SetSmokeVisibility] =
                eventCallback_t2<idAI> { obj: idAI, _num: idEventArg<*>?, on: idEventArg<*>? ->
                    obj.Event_SetSmokeVisibility(_num as idEventArg<Int>, on as idEventArg<Int>)
                }
            eventCallbacks[AI_NumSmokeEmitters] =
                eventCallback_t0<idAI> { obj: idAI -> obj.Event_NumSmokeEmitters() }
            eventCallbacks[AI_ClearBurn] =
                eventCallback_t0<idAI> { obj: idAI -> obj.Event_ClearBurn() }
            eventCallbacks[AI_StopThinking] =
                eventCallback_t0<idAI> { obj: idAI -> obj.Event_StopThinking() }
            eventCallbacks[AI_GetTurnDelta] =
                eventCallback_t0<idAI> { obj: idAI -> obj.Event_GetTurnDelta() }
            eventCallbacks[AI_GetMoveType] =
                eventCallback_t0<idAI> { obj: idAI -> obj.Event_GetMoveType() }
            eventCallbacks[AI_SetMoveType] =
                eventCallback_t1<idAI> { obj: idAI, _moveType: idEventArg<*>? ->
                    obj.Event_SetMoveType(_moveType as idEventArg<Int>)
                }
            eventCallbacks[AI_SaveMove] =
                eventCallback_t0<idAI> { obj: idAI -> obj.Event_SaveMove() }
            eventCallbacks[AI_RestoreMove] =
                eventCallback_t0<idAI> { obj: idAI -> obj.Event_RestoreMove() }
            eventCallbacks[AI_AllowMovement] =
                eventCallback_t1<idAI> { obj: idAI, flag: idEventArg<*>? -> obj.Event_AllowMovement(flag as idEventArg<Float>) }
            eventCallbacks[AI_JumpFrame] =
                eventCallback_t0<idAI> { obj: idAI -> obj.Event_JumpFrame() }
            eventCallbacks[AI_EnableClip] =
                eventCallback_t0<idAI> { obj: idAI -> obj.Event_EnableClip() }
            eventCallbacks[AI_DisableClip] =
                eventCallback_t0<idAI> { obj: idAI -> obj.Event_DisableClip() }
            eventCallbacks[AI_EnableGravity] =
                eventCallback_t0<idAI> { obj: idAI -> obj.Event_EnableGravity() }
            eventCallbacks[AI_DisableGravity] =
                eventCallback_t0<idAI> { obj: idAI -> obj.Event_DisableGravity() }
            eventCallbacks[AI_EnableAFPush] =
                eventCallback_t0<idAI> { obj: idAI -> obj.Event_EnableAFPush() }
            eventCallbacks[AI_DisableAFPush] =
                eventCallback_t0<idAI> { obj: idAI -> obj.Event_DisableAFPush() }
            eventCallbacks[AI_SetFlySpeed] =
                eventCallback_t1<idAI> { obj: idAI, speed: idEventArg<*>? -> obj.Event_SetFlySpeed(speed as idEventArg<Float>) }
            eventCallbacks[AI_SetFlyOffset] =
                eventCallback_t1<idAI> { obj: idAI, offset: idEventArg<*>? -> obj.Event_SetFlyOffset(offset as idEventArg<Int>) }
            eventCallbacks[AI_ClearFlyOffset] =
                eventCallback_t0<idAI> { obj: idAI -> obj.Event_ClearFlyOffset() }
            eventCallbacks[AI_GetClosestHiddenTarget] =
                eventCallback_t1<idAI> { obj: idAI, type: idEventArg<*>? ->
                    obj.Event_GetClosestHiddenTarget(type as idEventArg<String>)
                }
            eventCallbacks[AI_GetRandomTarget] =
                eventCallback_t1<idAI> { obj: idAI, type: idEventArg<*>? -> obj.Event_GetRandomTarget(type as idEventArg<String>) }
            eventCallbacks[AI_TravelDistanceToPoint] =
                eventCallback_t1<idAI> { obj: idAI, pos: idEventArg<*>? ->
                    obj.Event_TravelDistanceToPoint(pos as idEventArg<idVec3>)
                }
            eventCallbacks[AI_TravelDistanceToEntity] =
                eventCallback_t1<idAI> { obj: idAI, ent: idEventArg<*>? ->
                    obj.Event_TravelDistanceToEntity(ent as idEventArg<idEntity>)
                }
            eventCallbacks[AI_TravelDistanceBetweenPoints] =
                eventCallback_t2<idAI> { obj: idAI, source: idEventArg<*>?, dest: idEventArg<*>? ->
                    obj.Event_TravelDistanceBetweenPoints(source as idEventArg<idVec3>, dest as idEventArg<idVec3>)
                }
            eventCallbacks[AI_TravelDistanceBetweenEntities] =
                eventCallback_t2<idAI> { obj: idAI, source: idEventArg<*>?, dest: idEventArg<*>? ->
                    obj.Event_TravelDistanceBetweenEntities(
                        source as idEventArg<idEntity>,
                        dest as idEventArg<idEntity>
                    )
                }
            eventCallbacks[AI_LookAtEntity] =
                eventCallback_t2<idAI> { obj: idAI, _ent: idEventArg<*>?, duration: idEventArg<*>? ->
                    obj.Event_LookAtEntity(
                        _ent as idEventArg<idEntity>,
                        duration as idEventArg<Float>
                    )
                }
            eventCallbacks[AI_LookAtEnemy] =
                eventCallback_t1<idAI> { obj: idAI, duration: idEventArg<*>? -> obj.Event_LookAtEnemy(duration as idEventArg<Float>) }
            eventCallbacks[AI_SetJointMod] =
                eventCallback_t1<idAI> { obj: idAI, allow: idEventArg<*>? -> obj.Event_SetJointMod(allow as idEventArg<Int>) }
            eventCallbacks[AI_ThrowMoveable] =
                eventCallback_t0<idAI> { obj: idAI -> obj.Event_ThrowMoveable() }
            eventCallbacks[AI_ThrowAF] =
                eventCallback_t0<idAI> { obj: idAI -> obj.Event_ThrowAF() }
            eventCallbacks[EV_GetAngles] =
                eventCallback_t0<idAI> { obj: idAI -> obj.Event_GetAngles() }
            eventCallbacks[EV_SetAngles] =
                eventCallback_t1<idAI> { obj: idAI, ang: idEventArg<*>? -> obj.Event_SetAngles(ang as idEventArg<idAngles>) }
            eventCallbacks[AI_RealKill] =
                eventCallback_t0<idAI> { obj: idAI -> obj.Event_RealKill() }
            eventCallbacks[AI_Kill] =
                eventCallback_t0<idAI> { obj: idAI -> obj.Event_Kill() }
            eventCallbacks[AI_WakeOnFlashlight] =
                eventCallback_t1<idAI> { obj: idAI, enable: idEventArg<*>? ->
                    obj.Event_WakeOnFlashlight(enable as idEventArg<Int>)
                }
            eventCallbacks[AI_LocateEnemy] =
                eventCallback_t0<idAI> { obj: idAI -> obj.Event_LocateEnemy() }
            eventCallbacks[AI_KickObstacles] =
                eventCallback_t2<idAI> { obj: idAI, kickEnt: idEventArg<*>?, force: idEventArg<*>? ->
                    obj.Event_KickObstacles(kickEnt as idEventArg<idEntity>, force as idEventArg<Float>)
                }
            eventCallbacks[AI_GetObstacle] =
                eventCallback_t0<idAI> { obj: idAI -> obj.Event_GetObstacle() }
            eventCallbacks[AI_PushPointIntoAAS] =
                eventCallback_t1<idAI> { obj: idAI, _pos: idEventArg<*>? ->
                    obj.Event_PushPointIntoAAS(_pos as idEventArg<idVec3>)
                }
            eventCallbacks[AI_GetTurnRate] =
                eventCallback_t0<idAI> { obj: idAI -> obj.Event_GetTurnRate() }
            eventCallbacks[AI_SetTurnRate] =
                eventCallback_t1<idAI> { obj: idAI, rate: idEventArg<*>? -> obj.Event_SetTurnRate(rate as idEventArg<Float>) }
            eventCallbacks[AI_AnimTurn] =
                eventCallback_t1<idAI> { obj: idAI, angles: idEventArg<*>? -> obj.Event_AnimTurn(angles as idEventArg<Float>) }
            eventCallbacks[AI_AllowHiddenMovement] =
                eventCallback_t1<idAI> { obj: idAI, enable: idEventArg<*>? ->
                    obj.Event_AllowHiddenMovement(enable as idEventArg<Int>)
                }
            eventCallbacks[AI_TriggerParticles] =
                eventCallback_t1<idAI> { obj: idAI, jointName: idEventArg<*>? ->
                    obj.Event_TriggerParticles(jointName as idEventArg<String>)
                }
            eventCallbacks[AI_FindActorsInBounds] =
                eventCallback_t2<idAI> { obj: idAI, mins: idEventArg<*>?, maxs: idEventArg<*>? ->
                    obj.Event_FindActorsInBounds(mins as idEventArg<idVec3>, maxs as idEventArg<idVec3>)
                }
            eventCallbacks[AI_CanReachPosition] =
                eventCallback_t1<idAI> { obj: idAI, pos: idEventArg<*>? -> obj.Event_CanReachPosition(pos as idEventArg<idVec3>) }
            eventCallbacks[AI_CanReachEntity] =
                eventCallback_t1<idAI> { obj: idAI, _ent: idEventArg<*>? -> obj.Event_CanReachEntity(_ent as idEventArg<idEntity>) }
            eventCallbacks[AI_CanReachEnemy] =
                eventCallback_t0<idAI> { obj: idAI -> obj.Event_CanReachEnemy() }
            eventCallbacks[AI_GetReachableEntityPosition] =
                eventCallback_t1<idAI> { obj: idAI, _ent: idEventArg<*>? ->
                    obj.Event_GetReachableEntityPosition(_ent as idEventArg<idEntity>)
                }
        }
    }

    protected val AI_ACTIVATED: idScriptBool
    protected val AI_BLOCKED: idScriptBool
    protected val AI_DAMAGE: idScriptBool
    protected val AI_DEAD: idScriptBool
    protected val AI_DEST_UNREACHABLE: idScriptBool
    protected val AI_ENEMY_DEAD: idScriptBool
    protected val AI_ENEMY_IN_FOV: idScriptBool
    protected val AI_ENEMY_REACHABLE: idScriptBool
    protected val AI_ENEMY_VISIBLE: idScriptBool
    protected val AI_FORWARD: idScriptBool
    protected val AI_HIT_ENEMY: idScriptBool
    protected val AI_JUMP: idScriptBool
    protected val AI_MOVE_DONE: idScriptBool
    protected val AI_OBSTACLE_IN_PATH: idScriptBool
    protected val AI_ONGROUND: idScriptBool
    protected val AI_PAIN: idScriptBool
    protected val AI_PUSHED: idScriptBool
    protected val AI_SPECIAL_DAMAGE: idScriptFloat

    // script variables
    protected val AI_TALK: idScriptBool
    protected val currentFocusPos: idVec3

    //
    // enemy variables
    protected val enemy: idEntityPtr<idActor> = idEntityPtr()
    protected val focusEntity: idEntityPtr<idEntity?>
    protected val lastReachableEnemyPos: idVec3
    protected val lastVisibleEnemyEyeOffset: idVec3
    protected val lastVisibleEnemyPos: idVec3
    protected val lastVisibleReachableEnemyPos: idVec3
    protected val missileLaunchOffset: idList<idVec3>
    protected val projectile: idEntityPtr<idProjectile?>
    protected val projectileGravity: idVec3
    protected val projectileVelocity: idVec3
    protected val talkTarget: idEntityPtr<idActor>

    // navigation
    protected var aas: AAS.idAAS? = null
    protected var af_push_moveables // allow the articulated figure to push moveable objects
            = false
    protected var alignHeadTime: Int
    protected var allowHiddenMovement // allows character to still move around while hidden
            : Boolean

    //
    protected var allowJointMod: Boolean

    //
    protected var allowMove // disables any animation movement
            : Boolean
    protected var anim_turn_amount: Float
    protected var anim_turn_angles: Float
    protected var anim_turn_yaw: Float
    protected val attack: idStr = idStr()
    protected var blockedAttackTime: Int
    protected var blockedMoveTime: Int
    protected var blockedRadius: Float
    protected var chat_max: Int
    protected var chat_min: Int

    //
    // chatter/talking
    protected var chat_snd: idSoundShader?
    protected var chat_time: Int
    protected var current_cinematic: Int
    protected var current_yaw: Float
    protected val destLookAng: idAngles = idAngles()
    protected var disableGravity // disables gravity and allows vertical movement by the animation
            = false
    protected val eyeAng: idAngles = idAngles()
    protected var eyeFocusRate: Float
    protected var eyeHorizontalOffset: Float
    protected val eyeMax: idAngles = idAngles()

    //
    // joint controllers
    protected val eyeMin: idAngles = idAngles()
    protected var eyeVerticalOffset: Float
    protected var   /*jointHandle_t*/flashJointWorld: Int
    protected var flashTime: Int

    //
    // flying
    protected var   /*jointHandle_t*/flyTiltJoint: Int
    protected var fly_bob_horz: Float
    protected var fly_bob_strength: Float
    protected var fly_bob_vert: Float
    protected var fly_offset // prefered offset from player's view
            : Int
    protected var fly_pitch: Float
    protected var fly_pitch_max: Float
    protected var fly_pitch_scale: Float
    protected var fly_roll: Float
    protected var fly_roll_max: Float
    protected var fly_roll_scale: Float
    protected var fly_seek_scale: Float
    protected var fly_speed: Float
    protected var focusAlignTime: Int
    protected var focusJoint: Int
    protected var focusTime: Int
    protected var forceAlignHeadTime: Int
    protected var headFocusRate: Float

    // turning
    protected var ideal_yaw: Float
    protected var ignore_obstacles: Boolean

    //
    protected var kickForce: Float
    protected var lastAttackTime: Int

    // weapon/attack vars
    protected var lastHitCheckResult: Boolean
    protected var lastHitCheckTime: Int
    protected val lookAng: idAngles = idAngles()
    protected val lookJointAngles: idList<idAngles>
    protected val lookJoints: idList<Int>
    protected val lookMax: idAngles = idAngles()
    protected val lookMin: idAngles = idAngles()
    protected var melee_range: Float

    protected var move: idMoveState = idMoveState()
    protected var muzzleFlashEnd: Int

    // cinematics
    protected var num_cinematics: Int
    protected var   /*jointHandle_t*/orientationJoint: Int
    protected val particles // particle data
            : idList<particleEmitter_s>

    //
    // physics
    protected var physicsObj: idPhysics_Monster
    protected var projectileClipModel: idClipModel?

    //
    protected var projectileDef: idDict?
    protected var projectileRadius: Float
    protected var projectileSpeed: Float
    protected var projectile_height_to_distance_ratio // calculates the maximum height a projectile can be thrown
            : Float

    //
    protected var restartParticles // should smoke emissions restart
            : Boolean
    protected var savedMove: idMoveState = idMoveState()

    //
    // special fx
    protected var shrivel_rate: Float
    protected var shrivel_start: Int
    protected var talk_state: talkState_t = talkState_t.TALK_NEVER
    protected var travelFlags: Int
    protected var turnRate: Float
    protected var turnVel: Float
    protected var useBoneAxis // use the bone vs the model axis
            : Boolean
    protected var wakeOnFlashlight: Boolean

    //
    protected var worldMuzzleFlash // positioned on world weapon bone
            : renderLight_s
    protected var worldMuzzleFlashHandle: Int

    /*
     ============
     idAI::Save
     ============
     */
    override fun Save(savefile: idSaveGame) {
        super.Save(savefile)
        var i: Int
        savefile.WriteInt(travelFlags)
        move.Save(savefile)
        savedMove.Save(savefile)
        savefile.WriteFloat(kickForce)
        savefile.WriteBool(ignore_obstacles)
        savefile.WriteFloat(blockedRadius)
        savefile.WriteInt(blockedMoveTime)
        savefile.WriteInt(blockedAttackTime)
        savefile.WriteFloat(ideal_yaw)
        savefile.WriteFloat(current_yaw)
        savefile.WriteFloat(turnRate)
        savefile.WriteFloat(turnVel)
        savefile.WriteFloat(anim_turn_yaw)
        savefile.WriteFloat(anim_turn_amount)
        savefile.WriteFloat(anim_turn_angles)
        savefile.WriteStaticObject(physicsObj)
        savefile.WriteFloat(fly_speed)
        savefile.WriteFloat(fly_bob_strength)
        savefile.WriteFloat(fly_bob_vert)
        savefile.WriteFloat(fly_bob_horz)
        savefile.WriteInt(fly_offset)
        savefile.WriteFloat(fly_seek_scale)
        savefile.WriteFloat(fly_roll_scale)
        savefile.WriteFloat(fly_roll_max)
        savefile.WriteFloat(fly_roll)
        savefile.WriteFloat(fly_pitch_scale)
        savefile.WriteFloat(fly_pitch_max)
        savefile.WriteFloat(fly_pitch)
        savefile.WriteBool(allowMove)
        savefile.WriteBool(allowHiddenMovement)
        savefile.WriteBool(disableGravity)
        savefile.WriteBool(af_push_moveables)
        savefile.WriteBool(lastHitCheckResult)
        savefile.WriteInt(lastHitCheckTime)
        savefile.WriteInt(lastAttackTime)
        savefile.WriteFloat(melee_range)
        savefile.WriteFloat(projectile_height_to_distance_ratio)
        savefile.WriteInt(missileLaunchOffset.Num())
        i = 0
        while (i < missileLaunchOffset.Num()) {
            savefile.WriteVec3(missileLaunchOffset[i])
            i++
        }
        val projectileName = idStr()
        spawnArgs.GetString("def_projectile", "", projectileName)
        savefile.WriteString(projectileName)
        savefile.WriteFloat(projectileRadius)
        savefile.WriteFloat(projectileSpeed)
        savefile.WriteVec3(projectileVelocity)
        savefile.WriteVec3(projectileGravity)
        projectile.Save(savefile)
        savefile.WriteString(attack)
        savefile.WriteSoundShader(chat_snd)
        savefile.WriteInt(chat_min)
        savefile.WriteInt(chat_max)
        savefile.WriteInt(chat_time)
        savefile.WriteInt(TempDump.etoi(talk_state))
        talkTarget.Save(savefile)
        savefile.WriteInt(num_cinematics)
        savefile.WriteInt(current_cinematic)
        savefile.WriteBool(allowJointMod)
        focusEntity.Save(savefile)
        savefile.WriteVec3(currentFocusPos)
        savefile.WriteInt(focusTime)
        savefile.WriteInt(alignHeadTime)
        savefile.WriteInt(forceAlignHeadTime)
        savefile.WriteAngles(eyeAng)
        savefile.WriteAngles(lookAng)
        savefile.WriteAngles(destLookAng)
        savefile.WriteAngles(lookMin)
        savefile.WriteAngles(lookMax)
        savefile.WriteInt(lookJoints.Num())
        i = 0
        while (i < lookJoints.Num()) {
            savefile.WriteJoint(lookJoints[i])
            savefile.WriteAngles(lookJointAngles[i])
            i++
        }
        savefile.WriteFloat(shrivel_rate)
        savefile.WriteInt(shrivel_start)
        savefile.WriteInt(particles.Num())
        i = 0
        while (i < particles.Num()) {
            savefile.WriteParticle(particles[i].particle)
            savefile.WriteInt(particles[i].time)
            savefile.WriteJoint(particles[i].joint)
            i++
        }
        savefile.WriteBool(restartParticles)
        savefile.WriteBool(useBoneAxis)
        enemy.Save(savefile)
        savefile.WriteVec3(lastVisibleEnemyPos)
        savefile.WriteVec3(lastVisibleEnemyEyeOffset)
        savefile.WriteVec3(lastVisibleReachableEnemyPos)
        savefile.WriteVec3(lastReachableEnemyPos)
        savefile.WriteBool(wakeOnFlashlight)
        savefile.WriteAngles(eyeMin)
        savefile.WriteAngles(eyeMax)
        savefile.WriteFloat(eyeVerticalOffset)
        savefile.WriteFloat(eyeHorizontalOffset)
        savefile.WriteFloat(eyeFocusRate)
        savefile.WriteFloat(headFocusRate)
        savefile.WriteInt(focusAlignTime)
        savefile.WriteJoint(flashJointWorld)
        savefile.WriteInt(muzzleFlashEnd)
        savefile.WriteJoint(focusJoint)
        savefile.WriteJoint(orientationJoint)
        savefile.WriteJoint(flyTiltJoint)
        savefile.WriteBool(GetPhysics() == physicsObj)
    }

    /*
     ============
     idAI::Restore
     ============
     */
    override fun Restore(savefile: idRestoreGame) {
        super.Restore(savefile)
        val restorePhysics = CBool(false)
        var i: Int
        var num: Int
        travelFlags = savefile.ReadInt()
        move.Restore(savefile)
        savedMove.Restore(savefile)
        kickForce = savefile.ReadFloat()
        ignore_obstacles = savefile.ReadBool()
        blockedRadius = savefile.ReadFloat()
        blockedMoveTime = savefile.ReadInt()
        blockedAttackTime = savefile.ReadInt()
        ideal_yaw = savefile.ReadFloat()
        current_yaw = savefile.ReadFloat()
        turnRate = savefile.ReadFloat()
        turnVel = savefile.ReadFloat()
        anim_turn_yaw = savefile.ReadFloat()
        anim_turn_amount = savefile.ReadFloat()
        anim_turn_angles = savefile.ReadFloat()
        savefile.ReadStaticObject(physicsObj)
        fly_speed = savefile.ReadFloat()
        fly_bob_strength = savefile.ReadFloat()
        fly_bob_vert = savefile.ReadFloat()
        fly_bob_horz = savefile.ReadFloat()
        fly_offset = savefile.ReadInt()
        fly_seek_scale = savefile.ReadFloat()
        fly_roll_scale = savefile.ReadFloat()
        fly_roll_max = savefile.ReadFloat()
        fly_roll = savefile.ReadFloat()
        fly_pitch_scale = savefile.ReadFloat()
        fly_pitch_max = savefile.ReadFloat()
        fly_pitch = savefile.ReadFloat()
        allowMove = savefile.ReadBool()
        allowHiddenMovement = savefile.ReadBool()
        disableGravity = savefile.ReadBool()
        af_push_moveables = savefile.ReadBool()
        lastHitCheckResult = savefile.ReadBool()
        lastHitCheckTime = savefile.ReadInt()
        lastAttackTime = savefile.ReadInt()
        melee_range = savefile.ReadFloat()
        projectile_height_to_distance_ratio = savefile.ReadFloat()
        num = savefile.ReadInt()
        missileLaunchOffset.SetGranularity(1)
        missileLaunchOffset.SetNum(num)
        i = 0
        while (i < num) {
            missileLaunchOffset[i] = idVec3()
            savefile.ReadVec3(missileLaunchOffset[i])
            i++
        }
        val projectileName = idStr()
        savefile.ReadString(projectileName)
        projectileDef = if (projectileName.Length() != 0) {
            Game_local.gameLocal.FindEntityDefDict(projectileName.toString())
        } else {
            null
        }
        projectileRadius = savefile.ReadFloat()
        projectileSpeed = savefile.ReadFloat()
        savefile.ReadVec3(projectileVelocity)
        savefile.ReadVec3(projectileGravity)
        projectile.Restore(savefile)
        savefile.ReadString(attack)
        // FIX: chat_snd may be null; create temp object for ReadSoundShader which requires non-null
        val tempSoundShader = savefile.ReadSoundShader()
        chat_snd = tempSoundShader
        chat_min = savefile.ReadInt()
        chat_max = savefile.ReadInt()
        chat_time = savefile.ReadInt()
        i = savefile.ReadInt()
        talk_state = talkState_t.entries.toTypedArray()[i]
        talkTarget.Restore(savefile)
        num_cinematics = savefile.ReadInt()
        current_cinematic = savefile.ReadInt()
        allowJointMod = savefile.ReadBool()
        focusEntity.Restore(savefile)
        savefile.ReadVec3(currentFocusPos)
        focusTime = savefile.ReadInt()
        alignHeadTime = savefile.ReadInt()
        forceAlignHeadTime = savefile.ReadInt()
        savefile.ReadAngles(eyeAng)
        savefile.ReadAngles(lookAng)
        savefile.ReadAngles(destLookAng)
        savefile.ReadAngles(lookMin)
        savefile.ReadAngles(lookMax)
        num = savefile.ReadInt()
        lookJoints.SetGranularity(1)
        lookJoints.SetNum(num)
        lookJointAngles.SetGranularity(1)
        lookJointAngles.SetNum(num)
        i = 0
        while (i < num) {
            lookJoints[i] = savefile.ReadJoint()
            lookJointAngles[i] = idAngles()
            savefile.ReadAngles(lookJointAngles[i])
            i++
        }
        shrivel_rate = savefile.ReadFloat()
        shrivel_start = savefile.ReadInt()
        num = savefile.ReadInt()
        particles.SetNum(num)
        i = 0
        while (i < particles.Num()) {
            particles[i] = particleEmitter_s()
            particles[i].particle = savefile.ReadParticle()
            particles[i].time = savefile.ReadInt()
            particles[i].joint = savefile.ReadJoint()
            i++
        }
        restartParticles = savefile.ReadBool()
        useBoneAxis = savefile.ReadBool()
        enemy.Restore(savefile)
        savefile.ReadVec3(lastVisibleEnemyPos)
        savefile.ReadVec3(lastVisibleEnemyEyeOffset)
        savefile.ReadVec3(lastVisibleReachableEnemyPos)
        savefile.ReadVec3(lastReachableEnemyPos)
        wakeOnFlashlight = savefile.ReadBool()
        savefile.ReadAngles(eyeMin)
        savefile.ReadAngles(eyeMax)
        eyeVerticalOffset = savefile.ReadFloat()
        eyeHorizontalOffset = savefile.ReadFloat()
        eyeFocusRate = savefile.ReadFloat()
        headFocusRate = savefile.ReadFloat()
        focusAlignTime = savefile.ReadInt()
        flashJointWorld = savefile.ReadJoint()
        muzzleFlashEnd = savefile.ReadInt()
        focusJoint = savefile.ReadJoint()
        orientationJoint = savefile.ReadJoint()
        flyTiltJoint = savefile.ReadJoint()
        savefile.ReadBool(restorePhysics)

        // Set the AAS if the character has the correct gravity vector
        val gravity = idVec3(spawnArgs.GetVector("gravityDir", "0 0 -1"))
        gravity.timesAssign(SysCvar.g_gravity.GetFloat())
        if (gravity == Game_local.gameLocal.GetGravity()) {
            SetAAS()
        }
        SetCombatModel()
        LinkCombat()
        InitMuzzleFlash()

        // Link the script variables back to the scriptobject
        LinkScriptVariables()
        if (restorePhysics._val) {
            RestorePhysics(physicsObj)
        }
    }

    /*
     ============
     idAI::Spawn
     ============
     */
    override fun Spawn() {
        super.Spawn()
        var kv: idKeyValue?
        val jointName = idStr()
        var jointScale: idAngles?
        var   /*jointHandle_t*/joint: Int
        val local_dir = idVec3()
        val talks = CBool(false)
        if (!SysCvar.g_monsters.GetBool()) {
            PostEventMS(EV_Remove, 0)
            return
        }
        team = spawnArgs.GetInt("team", "1")
        rank = spawnArgs.GetInt("rank", "0")
        fly_offset = spawnArgs.GetInt("fly_offset", "0")
        fly_speed = spawnArgs.GetFloat("fly_speed", "100")
        fly_bob_strength = spawnArgs.GetFloat("fly_bob_strength", "50")
        fly_bob_horz = spawnArgs.GetFloat("fly_bob_vert", "2")
        fly_bob_vert = spawnArgs.GetFloat("fly_bob_horz", "2.7")
        fly_seek_scale = spawnArgs.GetFloat("fly_seek_scale", "4")
        fly_roll_scale = spawnArgs.GetFloat("fly_roll_scale", "90")
        fly_roll_max = spawnArgs.GetFloat("fly_roll_max", "60")
        fly_pitch_scale = spawnArgs.GetFloat("fly_pitch_scale", "45")
        fly_pitch_max = spawnArgs.GetFloat("fly_pitch_max", "30")
        melee_range = spawnArgs.GetFloat("melee_range", "64")
        projectile_height_to_distance_ratio = spawnArgs.GetFloat("projectile_height_to_distance_ratio", "1")
        turnRate = spawnArgs.GetFloat("turn_rate", "360")
        spawnArgs.GetBool("talks", "0", talks)
        talk_state = if (spawnArgs.GetString("npc_name", null) != null) {
            if (talks._val) {
                talkState_t.TALK_OK
            } else {
                talkState_t.TALK_BUSY
            }
        } else {
            talkState_t.TALK_NEVER
        }
        disableGravity = spawnArgs.GetBool("animate_z", "0")
        af_push_moveables = spawnArgs.GetBool("af_push_moveables", "0")
        kickForce = spawnArgs.GetFloat("kick_force", "4096")
        ignore_obstacles = spawnArgs.GetBool("ignore_obstacles", "0")
        blockedRadius = spawnArgs.GetFloat("blockedRadius", "-1")
        blockedMoveTime = spawnArgs.GetInt("blockedMoveTime", "750")
        blockedAttackTime = spawnArgs.GetInt("blockedAttackTime", "750")
        num_cinematics = spawnArgs.GetInt("num_cinematics", "0")
        current_cinematic = 0
        LinkScriptVariables()
        fl.takedamage = !spawnArgs.GetBool("noDamage")
        enemy.oSet(null)
        allowMove = true
        allowHiddenMovement = false
        animator.RemoveOriginOffset(true)

        // create combat collision hull for exact collision detection
        SetCombatModel()
        lookMin.set(spawnArgs.GetAngles("look_min", "-80 -75 0"))
        lookMax.set(spawnArgs.GetAngles("look_max", "80 75 0"))
        lookJoints.SetGranularity(1)
        lookJointAngles.SetGranularity(1)
        kv = spawnArgs.MatchPrefix("look_joint", null)
        while (kv != null) {
            jointName.set(kv.GetKey())
            jointName.StripLeadingOnce("look_joint ")
            joint = animator.GetJointHandle(jointName)
            if (joint == Model.INVALID_JOINT) {
                Game_local.gameLocal.Warning("Unknown look_joint '%s' on entity %s", jointName, name)
            } else {
                jointScale = spawnArgs.GetAngles(kv.GetKey().toString(), "0 0 0")
                jointScale.roll = 0.0f

                // if no scale on any component, then don't bother adding it.  this may be done to
                // zero out rotation from an inherited entitydef.
                if (jointScale != ang_zero) {
                    lookJoints.Append(joint)
                    lookJointAngles.Append(jointScale)
                }
            }
            kv = spawnArgs.MatchPrefix("look_joint", kv)
        }

        // calculate joint positions on attack frames so we can do proper "can hit" tests
        CalculateAttackOffsets()
        eyeMin.set(spawnArgs.GetAngles("eye_turn_min", "-10 -30 0"))
        eyeMax.set(spawnArgs.GetAngles("eye_turn_max", "10 30 0"))
        eyeVerticalOffset = spawnArgs.GetFloat("eye_verticle_offset", "5")
        eyeHorizontalOffset = spawnArgs.GetFloat("eye_horizontal_offset", "-8")
        eyeFocusRate = spawnArgs.GetFloat("eye_focus_rate", "0.5f")
        headFocusRate = spawnArgs.GetFloat("head_focus_rate", "0.1f")
        focusAlignTime = SEC2MS(spawnArgs.GetFloat("focus_align_time", "1")).toInt()
        flashJointWorld = animator.GetJointHandle("flash")
        if (head?.GetEntity() != null) {
            val headAnimator = head.GetEntity()!!.GetAnimator()
            jointName.set(spawnArgs.GetString("bone_focus"))
            if (!jointName.IsEmpty()) {
                focusJoint = headAnimator.GetJointHandle(jointName)
                if (focusJoint == Model.INVALID_JOINT) {
                    Game_local.gameLocal.Warning("Joint '%s' not found on head on '%s'", jointName, name)
                }
            }
        } else {
            jointName.set(spawnArgs.GetString("bone_focus"))
            if (!jointName.IsEmpty()) {
                focusJoint = animator.GetJointHandle(jointName)
                if (focusJoint == Model.INVALID_JOINT) {
                    Game_local.gameLocal.Warning("Joint '%s' not found on '%s'", jointName, name)
                }
            }
        }
        jointName.set(spawnArgs.GetString("bone_orientation"))
        if (!jointName.IsEmpty()) {
            orientationJoint = animator.GetJointHandle(jointName)
            if (orientationJoint == Model.INVALID_JOINT) {
                Game_local.gameLocal.Warning("Joint '%s' not found on '%s'", jointName, name)
            }
        }
        jointName.set(spawnArgs.GetString("bone_flytilt"))
        if (!jointName.IsEmpty()) {
            flyTiltJoint = animator.GetJointHandle(jointName)
            if (flyTiltJoint == Model.INVALID_JOINT) {
                Game_local.gameLocal.Warning("Joint '%s' not found on '%s'", jointName, name)
            }
        }
        InitMuzzleFlash()
        physicsObj.SetSelf(this)
        physicsObj.SetClipModel(idClipModel(GetPhysics().GetClipModel()!!), 1.0f)
        physicsObj.SetMass(spawnArgs.GetFloat("mass", "100"))
        if (spawnArgs.GetBool("big_monster")) {
            physicsObj.SetContents(0)
            physicsObj.SetClipMask(Game_local.MASK_MONSTERSOLID and Material.CONTENTS_BODY.inv())
        } else {
            if (use_combat_bbox) {
                physicsObj.SetContents(Material.CONTENTS_BODY or Material.CONTENTS_SOLID)
            } else {
                physicsObj.SetContents(Material.CONTENTS_BODY)
            }
            physicsObj.SetClipMask(Game_local.MASK_MONSTERSOLID)
        }

        // move up to make sure the monster is at least an epsilon above the floor
        physicsObj.SetOrigin(GetPhysics().GetOrigin().plus(idVec3(0.0f, 0.0f, CM_CLIP_EPSILON)))
        if (num_cinematics != 0) {
            physicsObj.SetGravity(vec3_origin)
        } else {
            val gravity = idVec3(spawnArgs.GetVector("gravityDir", "0 0 -1"))
            gravity.timesAssign(SysCvar.g_gravity.GetFloat())
            physicsObj.SetGravity(gravity)
        }
        SetPhysics(physicsObj)
        physicsObj.GetGravityAxis().ProjectVector(viewAxis[0], local_dir)
        current_yaw = local_dir.ToYaw()
        ideal_yaw = idMath.AngleNormalize180(current_yaw)
        move.blockTime = 0
        SetAAS()
        projectile.oSet(null)
        projectileDef = null
        projectileClipModel = null
        val projectileName = idStr()
        if (spawnArgs.GetString("def_projectile", "", projectileName) && projectileName.Length() != 0) {
            projectileDef = Game_local.gameLocal.FindEntityDefDict(projectileName)
            CreateProjectile(vec3_origin, viewAxis[0])
            projectileRadius = projectile.GetEntity()!!.GetPhysics().GetClipModel()!!.GetBounds().GetRadius()
            projectileVelocity.set(idProjectile.GetVelocity(projectileDef!!))
            projectileGravity.set(idProjectile.GetGravity(projectileDef!!))
            projectileSpeed = projectileVelocity.Length()
            delete(projectile.GetEntity())
            projectile.oSet(null)
        }
        particles.Clear()
        restartParticles = true
        useBoneAxis = spawnArgs.GetBool("useBoneAxis")
        SpawnParticles("smokeParticleSystem")
        if (num_cinematics != 0 || spawnArgs.GetBool("hide") || spawnArgs.GetBool("teleport") || spawnArgs.GetBool("trigger_anim")) {
            fl.takedamage = false
            physicsObj.SetContents(0)
            physicsObj.GetClipModel()!!.Unlink()
            Hide()
        } else {
            // play a looping ambient sound if we have one
            StartSound("snd_ambient", gameSoundChannel_t.SND_CHANNEL_AMBIENT, 0, false, CInt())
        }
        if (health <= 0) {
            Game_local.gameLocal.Warning("entity '%s' doesn't have health set", name)
            health = 1
        }

        // set up monster chatter
        SetChatSound()
        BecomeActive(TH_THINK)
        if (af_push_moveables) {
            af.SetupPose(this, Game_local.gameLocal.time)
            af.GetPhysics().EnableClip()
        }

        // init the move variables
        StopMove(moveStatus_t.MOVE_STATUS_DONE)
    }

    /*
     ============
     idAI::GetEnemy
     ============
     */
    fun GetEnemy(): idActor? {
        return enemy.GetEntity()
    }

    /*
     ============
     idAI::TalkTo
     ============
     */
    fun TalkTo(actor: idActor) {
        if (talk_state != talkState_t.TALK_OK) {
            return
        }
        talkTarget.oSet(actor)
        AI_TALK.underscore(actor != null)
    }

    /*
     ============
     idAI::GetTalkState
     ============
     */
    fun GetTalkState(): talkState_t {
        if (talk_state != talkState_t.TALK_NEVER && AI_DEAD.underscore()!!) {
            return talkState_t.TALK_DEAD
        }
        return if (IsHidden()) {
            talkState_t.TALK_NEVER
        } else talk_state
    }

    /*
     ============
     idAI::GetAimDir
     ============
     */
    fun GetAimDir(firePos: idVec3, aimAtEnt: idEntity?, ignore: idEntity?, aimDir: idVec3): Boolean {
        val targetPos1 = idVec3()
        val targetPos2 = idVec3()
        val delta = idVec3()
        var max_height: Float
        var result: Boolean

        // if no aimAtEnt or projectile set
        if (null == aimAtEnt || null == projectileDef) {
            aimDir.set(viewAxis[0].times(physicsObj.GetGravityAxis()))
            return false
        }
        if (projectileClipModel == null) {
            CreateProjectileClipModel()
        }
        if (aimAtEnt == enemy.GetEntity()) {
            (aimAtEnt as idActor).GetAIAimTargets(lastVisibleEnemyPos, targetPos1, targetPos2)
        } else if (aimAtEnt is idActor) {
            (aimAtEnt as idActor).GetAIAimTargets(aimAtEnt.GetPhysics().GetOrigin(), targetPos1, targetPos2)
        } else {
            targetPos1.set(aimAtEnt.GetPhysics().GetAbsBounds().GetCenter())
            targetPos2.set(targetPos1)
        }

        // try aiming for chest
        delta.set(firePos.minus(targetPos1))
        max_height = delta.LengthFast() * projectile_height_to_distance_ratio
        result = PredictTrajectory(
            firePos,
            targetPos1,
            projectileSpeed,
            projectileGravity,
            projectileClipModel!!,
            Game_local.MASK_SHOT_RENDERMODEL,
            max_height,
            ignore,
            aimAtEnt,
            if (SysCvar.ai_debugTrajectory.GetBool()) 1000 else 0,
            aimDir
        )
        if (result || aimAtEnt !is idActor) {
            return result
        }

        // try aiming for head
        delta.set(firePos.minus(targetPos2))
        max_height = delta.LengthFast() * projectile_height_to_distance_ratio
        result = PredictTrajectory(
            firePos,
            targetPos2,
            projectileSpeed,
            projectileGravity,
            projectileClipModel!!,
            Game_local.MASK_SHOT_RENDERMODEL,
            max_height,
            ignore,
            aimAtEnt,
            if (SysCvar.ai_debugTrajectory.GetBool()) 1000 else 0,
            aimDir
        )
        return result
    }

    /*
     ============
     idAI::TouchedByFlashlight
     ============
     */
    fun TouchedByFlashlight(flashlight_owner: idActor?) {
        if (wakeOnFlashlight) {
            Activate(flashlight_owner)
        }
    }

    //
    // ai/ai.cpp
    //
    /*
     ============
     idAI::SetAAS
     ============
     */
    protected fun SetAAS() {
        val use_aas = idStr()
        spawnArgs.GetString("use_aas", null, use_aas)
        aas = Game_local.gameLocal.GetAAS(use_aas.toString())
        if (aas != null) {
            // FIX: GetSettings() returns nullable; was using !! which would NPE when settings is null
            val settings = aas!!.GetSettings()
            if (settings != null) {
                if (!ValidForBounds(settings, physicsObj.GetBounds())) {
                    idGameLocal.Error("%s cannot use use_aas %s\n", name, use_aas)
                }
                val height = settings.maxStepHeight._val
                physicsObj.SetMaxStepHeight(height)
                return
            } else {
                aas = null
            }
        }
        Game_local.gameLocal.Printf("WARNING: %s has no AAS file\n", name)
    }

    /*
     ================
     idAI::DormantBegin

     called when entity becomes dormant
     ================
     */
    override fun DormantBegin() {
        // since dormant happens on a timer, we wont get to update particles to
        // hidden through the think loop, but we need to hide them though.
        if (particles.Num() != 0) {
            for (i in 0 until particles.Num()) {
                particles[i].time = 0
            }
        }
        if (enemyNode.InList()) {
            // remove ourselves from the enemy's enemylist
            enemyNode.Remove()
        }
        super.DormantBegin()
    }

    /*
     ================
     idAI::DormantEnd

     called when entity wakes from being dormant
     ================
     */
    override fun DormantEnd() {
        if (enemy.GetEntity() != null && !enemyNode.InList()) {
            // let our enemy know we're back on the trail
            enemyNode.AddToEnd(enemy.GetEntity()!!.enemyList)
        }
        if (particles.Num() != 0) {
            for (i in 0 until particles.Num()) {
                particles[i].time = Game_local.gameLocal.time
            }
        }
        super.DormantEnd()
    }

    /*
     ============
     idAI::Think
     ============
     */
    override fun Think() {
        // if we are completely closed off from the player, don't do anything at all
        if (CheckDormant()) {
            return
        }
        if ((thinkFlags and TH_THINK) != 0) {
            // clear out the enemy when he dies or is hidden
            val enemyEnt = enemy.GetEntity()
            if (enemyEnt != null) {
                if (enemyEnt.health <= 0) {
                    EnemyDead()
                }
            }
            current_yaw += deltaViewAngles.yaw
            ideal_yaw = idMath.AngleNormalize180(ideal_yaw + deltaViewAngles.yaw)
            deltaViewAngles.Zero()
            viewAxis.set(idAngles(0.0f, current_yaw, 0.0f).ToMat3())
            if (num_cinematics != 0) {
                if (!IsHidden() && torsoAnim.AnimDone(0)) {
                    PlayCinematic()
                }
                RunPhysics()
            } else if (!allowHiddenMovement && IsHidden()) {
                // hidden monsters
                UpdateAIScript()
            } else {
                // clear the ik before we do anything else so the skeleton doesn't get updated twice
                walkIK.ClearJointMods()
                when (move.moveType) {
                    moveType_t.MOVETYPE_DEAD -> {
                        // dead monsters
                        UpdateAIScript()
                        DeadMove()
                    }

                    moveType_t.MOVETYPE_FLY -> {
                        // flying monsters
                        UpdateEnemyPosition()
                        UpdateAIScript()
                        FlyMove()
                        PlayChatter()
                        CheckBlink()
                    }

                    moveType_t.MOVETYPE_STATIC -> {
                        // static monsters
                        UpdateEnemyPosition()
                        UpdateAIScript()
                        StaticMove()
                        PlayChatter()
                        CheckBlink()
                    }

                    moveType_t.MOVETYPE_ANIM -> {
                        // animation based movement
                        UpdateEnemyPosition()
                        UpdateAIScript()
                        AnimMove()
                        PlayChatter()
                        CheckBlink()
                    }

                    moveType_t.MOVETYPE_SLIDE -> {
                        // velocity based movement
                        UpdateEnemyPosition()
                        UpdateAIScript()
                        SlideMove()
                        PlayChatter()
                        CheckBlink()
                    }

                    else -> {}
                }
            }

            // clear pain flag so that we recieve any damage between now and the next time we run the script
            AI_PAIN.underscore(false)
            AI_SPECIAL_DAMAGE.underscore(0.0f)
            AI_PUSHED.underscore(false)
        } else if ((thinkFlags and TH_PHYSICS) != 0) {
            RunPhysics()
        }
        if (af_push_moveables) {
            PushWithAF()
        }
        if (fl.hidden && allowHiddenMovement) {
            // UpdateAnimation won't call frame commands when hidden, so call them here when we allow hidden movement
            animator.ServiceAnims(Game_local.gameLocal.previousTime, Game_local.gameLocal.time)
        }
        /*	this still draws in retail builds.. not sure why.. don't care at this point.
             if ( !aas && developer.GetBool() && !fl.hidden && !num_cinematics ) {
             gameRenderWorld->DrawText( "No AAS", physicsObj.GetAbsBounds().GetCenter(), 0.1f, colorWhite, gameLocal.GetLocalPlayer()->viewAngles.ToMat3(), 1, gameLocal.msec );
             }
             */UpdateMuzzleFlash()
        UpdateAnimation()
        UpdateParticles()
        Present()
        UpdateDamageEffects()
        LinkCombat()
    }

    /*
     =====================
     idAI::Activate

     Notifies the script that a monster has been activated by a trigger or flashlight
     =====================
     */
    protected fun Activate(activator: idEntity?) {
        val player: idPlayer
        if (AI_DEAD.underscore()!!) {
            // ignore it when they're dead
            return
        }

        // make sure he's not dormant
        dormantStart = 0
        if (num_cinematics != 0) {
            PlayCinematic()
        } else {
            AI_ACTIVATED.underscore(true)
            player = if (activator == null || activator !is idPlayer) {
                Game_local.gameLocal.GetLocalPlayer()!!
            } else {
                activator
            }
            if ((ReactionTo(player) and ATTACK_ON_ACTIVATE) != 0) {
                SetEnemy(player)
            }

            // update the script in cinematics so that entities don't start anims or show themselves a frame late.
            if (cinematic) {
                UpdateAIScript()

                // make sure our model gets updated
                animator.ForceUpdate()

                // update the anim bounds
                UpdateAnimation()
                UpdateVisuals()
                Present()
                if (head?.GetEntity() != null) {
                    // since the body anim was updated, we need to run physics to update the position of the head
                    RunPhysics()

                    // make sure our model gets updated
                    head.GetEntity()!!.GetAnimator().ForceUpdate()

                    // update the anim bounds
                    head.GetEntity()!!.UpdateAnimation()
                    head.GetEntity()!!.UpdateVisuals()
                    head.GetEntity()!!.Present()
                }
            }
        }
    }

    /*
     ============
     idAI::ReactionTo
     ============
     */
    protected fun ReactionTo(ent: idEntity): Int {
        if (ent.fl.hidden) {
            // ignore hidden entities
            return ATTACK_IGNORE
        }
        if (ent !is idActor) {
            return ATTACK_IGNORE
        }
        val actor = ent as idActor
        if (actor is idPlayer && actor.noclip) {
            // ignore players in noclip mode
            return ATTACK_IGNORE
        }

        // actors on different teams will always fight each other
        if (actor.team != team) {
            return if (actor.fl.notarget) {
                // don't attack on sight when attacker is notargeted
                ATTACK_ON_DAMAGE or ATTACK_ON_ACTIVATE
            } else ATTACK_ON_SIGHT or ATTACK_ON_DAMAGE or ATTACK_ON_ACTIVATE
        }

        // monsters will fight when attacked by lower ranked monsters.  rank 0 never fights back.
        return if (rank != 0 && actor.rank < rank) {
            ATTACK_ON_DAMAGE
        } else ATTACK_IGNORE

        // don't fight back
    }

    /*
     ============
     idAI::EnemyDead
     ============
     */
    protected fun EnemyDead() {
        ClearEnemy()
        AI_ENEMY_DEAD.underscore(true)
    }

    /*
     ================
     idAI::CanPlayChatterSounds

     Used for playing chatter sounds on monsters.
     ================
     */
    override fun CanPlayChatterSounds(): Boolean {
        if (AI_DEAD.underscore()!!) {
            return false
        }
        if (IsHidden()) {
            return false
        }
        return if (enemy.GetEntity() != null) {
            true
        } else !spawnArgs.GetBool("no_idle_chatter")
    }

    /*
     ============
     idAI::SetChatSound
     ============
     */
    protected fun SetChatSound() {
        val snd: String?
        if (IsHidden()) {
            snd = null
        } else if (enemy.GetEntity() != null) {
            snd = spawnArgs.GetString("snd_chatter_combat", null)
            chat_min = SEC2MS(spawnArgs.GetFloat("chatter_combat_min", "5")).toInt()
            chat_max = SEC2MS(spawnArgs.GetFloat("chatter_combat_max", "10")).toInt()
        } else if (!spawnArgs.GetBool("no_idle_chatter", "0")) {
            snd = spawnArgs.GetString("snd_chatter", null)
            chat_min = SEC2MS(spawnArgs.GetFloat("chatter_min", "5")).toInt()
            chat_max = SEC2MS(spawnArgs.GetFloat("chatter_max", "10")).toInt()
        } else {
            snd = null
        }
        if (!snd.isNullOrEmpty()) {
            chat_snd = DeclManager.declManager.FindSound(snd)

            // set the next chat time
            chat_time =
                (Game_local.gameLocal.time + chat_min + Game_local.gameLocal.random.RandomFloat() * (chat_max - chat_min)).toInt()
        } else {
            chat_snd = null
        }
    }

    /*
     ============
     idAI::PlayChatter
     ============
     */
    protected fun PlayChatter() {
        // check if it's time to play a chat sound
        if (AI_DEAD.underscore()!! || chat_snd == null || chat_time > Game_local.gameLocal.time) {
            return
        }
        StartSoundShader(chat_snd, gameSoundChannel_t.SND_CHANNEL_VOICE, 0, false, CInt())

        // set the next chat time
        chat_time =
            (Game_local.gameLocal.time + chat_min + Game_local.gameLocal.random.RandomFloat() * (chat_max - chat_min)).toInt()
    }

    /*
     ============
     idAI::Hide
     ============
     */
    override fun Hide() {
        super.Hide()
        fl.takedamage = false
        physicsObj.SetContents(0)
        physicsObj.GetClipModel()!!.Unlink()
        StopSound(TempDump.etoi(gameSoundChannel_t.SND_CHANNEL_AMBIENT), false)
        SetChatSound()
        AI_ENEMY_IN_FOV.underscore(false)
        AI_ENEMY_VISIBLE.underscore(false)
        StopMove(moveStatus_t.MOVE_STATUS_DONE)
    }

    /*
     ============
     idAI::Show
     ============
     */
    override fun Show() {
        super.Show()
        if (spawnArgs.GetBool("big_monster")) {
            physicsObj.SetContents(0)
        } else if (use_combat_bbox) {
            physicsObj.SetContents(Material.CONTENTS_BODY or Material.CONTENTS_SOLID)
        } else {
            physicsObj.SetContents(Material.CONTENTS_BODY)
        }
        physicsObj.GetClipModel()!!.Link(Game_local.gameLocal.clip)
        fl.takedamage = !spawnArgs.GetBool("noDamage")
        SetChatSound()
        StartSound("snd_ambient", gameSoundChannel_t.SND_CHANNEL_AMBIENT, 0, false, CInt())
    }

    /*
     ============
     idAI::FirstVisiblePointOnPath
     ============
     */
    protected fun FirstVisiblePointOnPath(origin: idVec3, target: idVec3, travelFlags: Int): idVec3 {
        var i: Int
        val areaNum: Int
        val targetAreaNum: Int
        var curAreaNum: Int
        val travelTime = CInt()
        val curOrigin = idVec3()
        val reach = arrayOf<AASFile.idReachability?>(null)
        if (null == aas) {
            return origin
        }
        areaNum = PointReachableAreaNum(origin)
        targetAreaNum = PointReachableAreaNum(target)
        if (0 == areaNum || 0 == targetAreaNum) {
            return origin
        }
        if (areaNum == targetAreaNum || PointVisible(origin)) {
            return origin
        }
        curAreaNum = areaNum
        curOrigin.set(origin)
        i = 0
        while (i < 10) {
            if (!aas!!.RouteToGoalArea(curAreaNum, curOrigin, targetAreaNum, travelFlags, travelTime, reach)) {
                break
            }
            if (null == reach[0]) {
                return target
            }
            curAreaNum = reach[0]!!.toAreaNum.toInt()
            curOrigin.set(reach[0]!!.end)
            if (PointVisible(curOrigin)) {
                return curOrigin
            }
            i++
        }
        return origin
    }

    /*
     ===================
     idAI::CalculateAttackOffsets

     calculate joint positions on attack frames so we can do proper "can hit" tests
     ===================
     */
    protected fun CalculateAttackOffsets() {
        val modelDef: idDeclModelDef?
        val num: Int
        var i: Int
        var frame: Int
        val command = arrayOf<frameCommand_t?>(null)
        val axis = idMat3()
        var anim: idAnim?
        var   /*jointHandle_t*/joint: Int
        modelDef = animator.ModelDef()
        if (null == modelDef) {
            return
        }
        num = modelDef.NumAnims()

        // needs to be off while getting the offsets so that we account for the distance the monster moves in the attack anim
        animator.RemoveOriginOffset(false)

        // anim number 0 is reserved for non-existant anims.  to avoid off by one issues, just allocate an extra spot for
        // launch offsets so that anim number can be used without subtracting 1.
        missileLaunchOffset.SetGranularity(1)
        missileLaunchOffset.SetNum(num + 1)
        missileLaunchOffset[0] = idVec3()
        i = 1
        while (i <= num) {
            missileLaunchOffset[i] = idVec3()
            anim = modelDef.GetAnim(i)
            if (anim != null) {
                frame = anim.FindFrameForFrameCommand(frameCommandType_t.FC_LAUNCHMISSILE, command)
                if (frame >= 0) {
                    joint = animator.GetJointHandle(command[0]!!.string.toString())
                    if (joint == Model.INVALID_JOINT) {
                        idGameLocal.Error(
                            "Invalid joint '%s' on 'launch_missile' frame command on frame %d of model '%s'",
                            command[0]!!.string.toString(),
                            frame,
                            modelDef.GetName()
                        )
                    }
                    GetJointTransformForAnim(joint, i, Anim.FRAME2MS(frame), missileLaunchOffset[i], axis)
                }
            }
            i++
        }
        animator.RemoveOriginOffset(true)
    }

    /*
     ============
     idAI::PlayCinematic
     ============
     */
    protected fun PlayCinematic() {
        val animName = arrayOfNulls<String>(1)
        if (current_cinematic >= num_cinematics) {
            if (SysCvar.g_debugCinematic.GetBool()) {
                Game_local.gameLocal.Printf("%d: '%s' stop\n", Game_local.gameLocal.framenum, GetName())
            }
            if (!spawnArgs.GetBool("cinematic_no_hide")) {
                Hide()
            }
            current_cinematic = 0
            ActivateTargets(Game_local.gameLocal.GetLocalPlayer())
            fl.neverDormant = false
            return
        }
        Show()
        current_cinematic++
        allowJointMod = false
        allowEyeFocus = false
        spawnArgs.GetString(Str.va("anim%d", current_cinematic), "", animName)
        if (animName[0].isNullOrEmpty()) {
            Game_local.gameLocal.Warning("missing 'anim%d' key on %s", current_cinematic, name)
            return
        }
        if (SysCvar.g_debugCinematic.GetBool()) {
            Game_local.gameLocal.Printf(
                "%d: '%s' start '%s'\n",
                Game_local.gameLocal.framenum,
                GetName(),
                animName[0]
            )
        }
        headAnim.animBlendFrames = 0
        headAnim.lastAnimBlendFrames = 0
        headAnim.BecomeIdle()
        legsAnim.animBlendFrames = 0
        legsAnim.lastAnimBlendFrames = 0
        legsAnim.BecomeIdle()
        torsoAnim.animBlendFrames = 0
        torsoAnim.lastAnimBlendFrames = 0
        ProcessEvent(AI_PlayAnim, Anim.ANIMCHANNEL_TORSO, animName[0])

        // make sure our model gets updated
        animator.ForceUpdate()

        // update the anim bounds
        UpdateAnimation()
        UpdateVisuals()
        Present()
        if (head?.GetEntity() != null) {
            // since the body anim was updated, we need to run physics to update the position of the head
            RunPhysics()

            // make sure our model gets updated
            head.GetEntity()!!.GetAnimator().ForceUpdate()

            // update the anim bounds
            head.GetEntity()!!.UpdateAnimation()
            head.GetEntity()!!.UpdateVisuals()
            head.GetEntity()!!.Present()
        }
        fl.neverDormant = true
    }

    // movement
    /*
     ============
     idAI::ApplyImpulse
     ============
     */
    override fun ApplyImpulse(ent: idEntity?, id: Int, point: idVec3, impulse: idVec3) {
        // FIXME: Jim take a look at this and see if this is a reasonable thing to do
        // instead of a spawnArg flag.. Sabaoth is the only slide monster ( and should be the only one for D3 )
        // and we don't want him taking physics impulses as it can knock him off the path
        if (move.moveType != moveType_t.MOVETYPE_STATIC && move.moveType != moveType_t.MOVETYPE_SLIDE) {
            super.ApplyImpulse(ent, id, point, impulse)
        }
    }

    /*
     ============
     idAI::GetMoveDelta
     ============
     */
    protected fun GetMoveDelta(oldaxis: idMat3, axis: idMat3, delta: idVec3) {
        val oldModelOrigin = idVec3()
        val modelOrigin = idVec3()
        animator.GetDelta(Game_local.gameLocal.time - idGameLocal.msec, Game_local.gameLocal.time, delta)
        delta.set(axis.times(delta))
        if (modelOffset != vec3_zero) {
            // the pivot of the monster's model is around its origin, and not around the bounding
            // box's origin, so we have to compensate for this when the model is offset so that
            // the monster still appears to rotate around it's origin.
            oldModelOrigin.set(modelOffset.times(oldaxis))
            modelOrigin.set(modelOffset.times(axis))
            delta.plusAssign(oldModelOrigin.minus(modelOrigin))
        }
        delta.timesAssign(physicsObj.GetGravityAxis())
    }

    /*
     ============
     idAI::CheckObstacleAvoidance
     ============
     */
    protected fun CheckObstacleAvoidance(goalPos: idVec3, newPos: idVec3) {
        var obstacle: idEntity?
        val path = obstaclePath_s()
        val dir = idVec3()
        val dist: Float
        val foundPath: Boolean
        if (ignore_obstacles) {
            newPos.set(goalPos)
            move.obstacle.oSet(null)
            return
        }
        val origin = physicsObj.GetOrigin()
        obstacle = null
        AI_OBSTACLE_IN_PATH.underscore(false)
        foundPath = FindPathAroundObstacles(physicsObj, aas, enemy.GetEntity(), origin, goalPos, path)
        if (SysCvar.ai_showObstacleAvoidance.GetBool()) {
            Game_local.gameRenderWorld!!.DebugLine(
                colorBlue,
                goalPos.plus(idVec3(1.0f, 1.0f, 0.0f)),
                goalPos.plus(idVec3(1.0f, 1.0f, 64.0f)),
                idGameLocal.msec
            )
            Game_local.gameRenderWorld!!.DebugLine(
                if (foundPath) colorYellow else colorRed,
                path.seekPos,
                path.seekPos.plus(idVec3(0.0f, 0.0f, 64.0f)),
                idGameLocal.msec
            )
        }
        if (!foundPath) {
            // couldn't get around obstacles
            if (path.firstObstacle != null) {
                AI_OBSTACLE_IN_PATH.underscore(true)
                if (physicsObj.GetAbsBounds().Expand(2.0f)
                        .IntersectsBounds(path.firstObstacle!!.GetPhysics().GetAbsBounds())
                ) {
                    obstacle = path.firstObstacle
                }
            } else if (path.startPosObstacle != null) {
                AI_OBSTACLE_IN_PATH.underscore(true)
                if (physicsObj.GetAbsBounds().Expand(2.0f)
                        .IntersectsBounds(path.startPosObstacle!!.GetPhysics().GetAbsBounds())
                ) {
                    obstacle = path.startPosObstacle
                }
            } else {
                // Blocked by wall
                move.moveStatus = moveStatus_t.MOVE_STATUS_BLOCKED_BY_WALL
            }
        } else if (path.seekPosObstacle != null) {
            // if the AI is very close to the path.seekPos already and path.seekPosObstacle != NULL
            // then we want to push the path.seekPosObstacle entity out of the way
            AI_OBSTACLE_IN_PATH.underscore(true)

            // check if we're past where the goalPos was pushed out of the obstacle
            dir.set(goalPos.minus(origin))
            dir.Normalize()
            dist = path.seekPos.minus(origin).times(dir)
            if (dist < 1.0f) {
                obstacle = path.seekPosObstacle
            }
        }

        // if we had an obstacle, set our move status based on the type, and kick it out of the way if it's a moveable
        if (obstacle != null) {
            if (obstacle is idActor) {
                // monsters aren't kickable
                if (obstacle === enemy.GetEntity()) {
                    move.moveStatus = moveStatus_t.MOVE_STATUS_BLOCKED_BY_ENEMY
                } else {
                    move.moveStatus = moveStatus_t.MOVE_STATUS_BLOCKED_BY_MONSTER
                }
            } else {
                // try kicking the object out of the way
                move.moveStatus = moveStatus_t.MOVE_STATUS_BLOCKED_BY_OBJECT
            }
            newPos.set(obstacle.GetPhysics().GetOrigin())
            //newPos = path.seekPos;
            move.obstacle.oSet(obstacle)
        } else {
            newPos.set(path.seekPos)
            move.obstacle.oSet(null)
        }
    }

    /*
     ============
     idAI::DeadMove
     ============
     */
    protected fun DeadMove() {
        val delta = idVec3()
        val moveResult: monsterMoveResult_t?
        idVec3(physicsObj.GetOrigin())
        GetMoveDelta(viewAxis, viewAxis, delta)
        physicsObj.SetDelta(delta)
        RunPhysics()
        moveResult = physicsObj.GetMoveResult()
        AI_ONGROUND.underscore(physicsObj.OnGround())
    }

    /*
     ============
     idAI::AnimMove
     ============
     */
    protected fun AnimMove() {
        val goalPos = idVec3()
        val delta = idVec3()
        val goalDelta = idVec3()
        val goalDist: Float
        val moveResult: monsterMoveResult_t?
        val newDest = idVec3()
        val oldOrigin = idVec3(physicsObj.GetOrigin())
        // FIX: Was `val oldAxis = viewAxis` which copies the reference, not the value.
        // Turn() modifies viewAxis in-place via .set(), so oldAxis and viewAxis would be
        // the same object, causing GetMoveDelta to receive identical matrices.
        val oldAxis = idMat3(viewAxis)
        AI_BLOCKED.underscore(false)
        if (TempDump.etoi(move.moveCommand) < TempDump.etoi(moveCommand_t.NUM_NONMOVING_COMMANDS)) {
            move.lastMoveOrigin.Zero()
            move.lastMoveTime = Game_local.gameLocal.time
        }
        move.obstacle.oSet(null)
        if (move.moveCommand == moveCommand_t.MOVE_FACE_ENEMY && enemy.GetEntity() != null) {
            TurnToward(lastVisibleEnemyPos)
            goalPos.set(oldOrigin)
        } else if (move.moveCommand == moveCommand_t.MOVE_FACE_ENTITY && move.goalEntity.GetEntity() != null) {
            TurnToward(move.goalEntity.GetEntity()!!.GetPhysics().GetOrigin())
            goalPos.set(oldOrigin)
        } else if (GetMovePos(goalPos)) {
            if (move.moveCommand != moveCommand_t.MOVE_WANDER) {
                CheckObstacleAvoidance(goalPos, newDest)
                TurnToward(newDest)
            } else {
                TurnToward(goalPos)
            }
        }
        Turn()
        if (move.moveCommand == moveCommand_t.MOVE_SLIDE_TO_POSITION) {
            if (Game_local.gameLocal.time < move.startTime + move.duration) {
                goalPos.set(move.moveDest.minus(move.moveDir.times(MS2SEC((move.startTime + move.duration - Game_local.gameLocal.time).toFloat()))))
                delta.set(goalPos.minus(oldOrigin))
                delta.z = 0.0f
            } else {
                delta.set(move.moveDest.minus(oldOrigin))
                delta.z = 0.0f
                StopMove(moveStatus_t.MOVE_STATUS_DONE)
            }
        } else if (allowMove) {
            GetMoveDelta(oldAxis, viewAxis, delta)
        } else {
            delta.Zero()
        }
        if (move.moveCommand == moveCommand_t.MOVE_TO_POSITION) {
            goalDelta.set(move.moveDest.minus(oldOrigin))
            goalDist = goalDelta.LengthFast()
            if (goalDist < delta.LengthFast()) {
                delta.set(goalDelta)
            }
        }
        physicsObj.SetDelta(delta)
        physicsObj.ForceDeltaMove(disableGravity)
        RunPhysics()
        if (SysCvar.ai_debugMove.GetBool()) {
            Game_local.gameRenderWorld!!.DebugLine(colorCyan, oldOrigin, physicsObj.GetOrigin(), 5000)
        }
        moveResult = physicsObj.GetMoveResult()
        if (!af_push_moveables && attack.Length() != 0 && TestMelee()) {
            DirectDamage(attack, enemy.GetEntity()!!)
        } else {
            val blockEnt = physicsObj.GetSlideMoveEntity()
            if (blockEnt != null && blockEnt is idMoveable && blockEnt.GetPhysics().IsPushable()) {
                KickObstacles(viewAxis[0], kickForce, blockEnt)
            }
        }
        BlockedFailSafe()
        AI_ONGROUND.underscore(physicsObj.OnGround())
        val org = idVec3(physicsObj.GetOrigin())
        if (oldOrigin != org) { //FIXME: so this checks value instead of refs which COULD go wrong!
            TouchTriggers()
        }
        if (SysCvar.ai_debugMove.GetBool()) {
            Game_local.gameRenderWorld!!.DebugBounds(
                colorMagenta,
                physicsObj.GetBounds(),
                org,
                idGameLocal.msec
            )
            Game_local.gameRenderWorld!!.DebugBounds(
                colorMagenta,
                physicsObj.GetBounds(),
                move.moveDest,
                idGameLocal.msec
            )
            Game_local.gameRenderWorld!!.DebugLine(
                colorYellow,
                org.plus(EyeOffset()),
                org.plus(
                    EyeOffset().plus(
                        viewAxis[0].times(physicsObj.GetGravityAxis().times(16.0f))
                    )
                ),
                idGameLocal.msec,
                true
            )
            DrawRoute()
        }
    }

    /*
     ============
     idAI::SlideMove
     ============
     */
    protected fun SlideMove() {
        val goalPos = idVec3()
        val delta = idVec3()
        val goalDelta = idVec3()
        val goalDist: Float
        val moveResult: monsterMoveResult_t?
        val newDest = idVec3()
        val oldOrigin = idVec3(physicsObj.GetOrigin())
        viewAxis
        AI_BLOCKED.underscore(false)
        if (TempDump.etoi(move.moveCommand) < TempDump.etoi(moveCommand_t.NUM_NONMOVING_COMMANDS)) {
            move.lastMoveOrigin.Zero()
            move.lastMoveTime = Game_local.gameLocal.time
        }
        move.obstacle.oSet(null)
        if (move.moveCommand == moveCommand_t.MOVE_FACE_ENEMY && enemy.GetEntity() != null) {
            TurnToward(lastVisibleEnemyPos)
            goalPos.set(move.moveDest)
        } else if (move.moveCommand == moveCommand_t.MOVE_FACE_ENTITY && move.goalEntity.GetEntity() != null) {
            TurnToward(move.goalEntity.GetEntity()!!.GetPhysics().GetOrigin())
            goalPos.set(move.moveDest)
        } else if (GetMovePos(goalPos)) {
            CheckObstacleAvoidance(goalPos, newDest)
            TurnToward(newDest)
            goalPos.set(newDest)
        }
        if (move.moveCommand == moveCommand_t.MOVE_SLIDE_TO_POSITION) {
            if (Game_local.gameLocal.time < move.startTime + move.duration) {
                goalPos.set(move.moveDest.minus(move.moveDir.times(MS2SEC((move.startTime + move.duration - Game_local.gameLocal.time).toFloat()))))
            } else {
                goalPos.set(move.moveDest)
                StopMove(moveStatus_t.MOVE_STATUS_DONE)
            }
        }
        if (move.moveCommand == moveCommand_t.MOVE_TO_POSITION) {
            goalDelta.set(move.moveDest.minus(oldOrigin))
            goalDist = goalDelta.LengthFast()
            if (goalDist < delta.LengthFast()) {
                delta.set(goalDelta)
            }
        }
        val vel = idVec3(physicsObj.GetLinearVelocity())
        val z = vel.z
        val predictedPos = idVec3(oldOrigin.plus(vel.times(AI_SEEK_PREDICTION)))

        // seek the goal position
        goalDelta.set(goalPos.minus(predictedPos))
        vel.minusAssign(vel.times(AI_FLY_DAMPENING * MS2SEC(idGameLocal.msec.toFloat())))
        vel.plusAssign(goalDelta.times(MS2SEC(idGameLocal.msec.toFloat())))

        // cap our speed
        vel.Truncate(fly_speed)
        vel.z = z
        physicsObj.SetLinearVelocity(vel)
        physicsObj.UseVelocityMove(true)
        RunPhysics()
        if (move.moveCommand == moveCommand_t.MOVE_FACE_ENEMY && enemy.GetEntity() != null) {
            TurnToward(lastVisibleEnemyPos)
        } else if (move.moveCommand == moveCommand_t.MOVE_FACE_ENTITY && move.goalEntity.GetEntity() != null) {
            TurnToward(move.goalEntity.GetEntity()!!.GetPhysics().GetOrigin())
        } else if (move.moveCommand != moveCommand_t.MOVE_NONE) {
            if (vel.ToVec2().LengthSqr() > 0.1f) {
                TurnToward(vel.ToYaw())
            }
        }
        Turn()
        if (SysCvar.ai_debugMove.GetBool()) {
            Game_local.gameRenderWorld!!.DebugLine(colorCyan, oldOrigin, physicsObj.GetOrigin(), 5000)
        }
        moveResult = physicsObj.GetMoveResult()
        if (!af_push_moveables && attack.Length() != 0 && TestMelee()) {
            DirectDamage(attack, enemy.GetEntity()!!)
        } else {
            val blockEnt = physicsObj.GetSlideMoveEntity()
            if (blockEnt != null && blockEnt is idMoveable && blockEnt.GetPhysics().IsPushable()) {
                KickObstacles(viewAxis[0], kickForce, blockEnt)
            }
        }
        BlockedFailSafe()
        AI_ONGROUND.underscore(physicsObj.OnGround())
        val org = idVec3(physicsObj.GetOrigin())
        if (oldOrigin != org) {
            TouchTriggers()
        }
        if (SysCvar.ai_debugMove.GetBool()) {
            Game_local.gameRenderWorld!!.DebugBounds(
                colorMagenta,
                physicsObj.GetBounds(),
                org,
                idGameLocal.msec
            )
            Game_local.gameRenderWorld!!.DebugBounds(
                colorMagenta,
                physicsObj.GetBounds(),
                move.moveDest,
                idGameLocal.msec
            )
            Game_local.gameRenderWorld!!.DebugLine(
                colorYellow,
                org.plus(EyeOffset()),
                org.plus(
                    EyeOffset().plus(
                        viewAxis[0].times(physicsObj.GetGravityAxis().times(16.0f))
                    )
                ),
                idGameLocal.msec,
                true
            )
            DrawRoute()
        }
    }

    /*
     ============
     idAI::AdjustFlyingAngles
     ============
     */
    protected fun AdjustFlyingAngles() {
        val vel = idVec3()
        val speed: Float
        var roll: Float
        var pitch: Float
        vel.set(physicsObj.GetLinearVelocity())
        speed = vel.Length()
        if (speed < 5.0f) {
            roll = 0.0f
            pitch = 0.0f
        } else {
            roll = vel.times(viewAxis[1].times(-fly_roll_scale / fly_speed))
            if (roll > fly_roll_max) {
                roll = fly_roll_max
            } else if (roll < -fly_roll_max) {
                roll = -fly_roll_max
            }
            pitch = vel.times(viewAxis[2].times(-fly_pitch_scale / fly_speed))
            if (pitch > fly_pitch_max) {
                pitch = fly_pitch_max
            } else if (pitch < -fly_pitch_max) {
                pitch = -fly_pitch_max
            }
        }
        fly_roll = fly_roll * 0.95f + roll * 0.05f
        fly_pitch = fly_pitch * 0.95f + pitch * 0.05f
        if (flyTiltJoint != Model.INVALID_JOINT) {
            animator.SetJointAxis(
                flyTiltJoint,
                jointModTransform_t.JOINTMOD_WORLD,
                idAngles(fly_pitch, 0.0f, fly_roll).ToMat3()
            )
        } else {
            viewAxis.set(idAngles(fly_pitch, current_yaw, fly_roll).ToMat3())
        }
    }

    /*
     ============
     idAI::AddFlyBob
     ============
     */
    protected fun AddFlyBob(vel: idVec3) {
        val fly_bob_add = idVec3()
        val t: Float
        if (fly_bob_strength != 0.0f) {
            t = MS2SEC((Game_local.gameLocal.time + entityNumber * 497).toFloat())
            fly_bob_add.set(
                viewAxis[1].times(idMath.Sin16(t * fly_bob_horz))
                    .plus(viewAxis[2].times(idMath.Sin16(t * fly_bob_vert))).times(fly_bob_strength)
            )
            vel.plusAssign(fly_bob_add.times(MS2SEC(idGameLocal.msec.toFloat())))
            if (SysCvar.ai_debugMove.GetBool()) {
                val origin = physicsObj.GetOrigin()
                Game_local.gameRenderWorld!!.DebugArrow(
                    colorOrange,
                    origin,
                    origin.plus(fly_bob_add),
                    0
                )
            }
        }
    }

    /*
     ============
     idAI::AdjustFlyHeight
     ============
     */
    protected fun AdjustFlyHeight(vel: idVec3, goalPos: idVec3) {
        val origin = idVec3(physicsObj.GetOrigin())
        val path = predictedPath_s()
        val end = idVec3()
        val dest = idVec3()
        val trace = trace_s()
        val enemyEnt: idActor?
        var goLower: Boolean

        // make sure we're not flying too high to get through doors
        goLower = false
        if (origin.z > goalPos.z) {
            dest.set(goalPos)
            dest.z = origin.z + 128.0f
            PredictPath(this, aas, goalPos, dest.minus(origin), 1000, 1000, SE_BLOCKED, path)
            if (path.endPos.z < origin.z) {
                val addVel = idVec3(Seek(vel, origin, path.endPos, AI_SEEK_PREDICTION))
                vel.z += addVel.z
                goLower = true
            }
            if (SysCvar.ai_debugMove.GetBool()) {
                Game_local.gameRenderWorld!!.DebugBounds(
                    if (goLower) colorRed else colorGreen,
                    physicsObj.GetBounds(),
                    path.endPos,
                    idGameLocal.msec
                )
            }
        }
        if (!goLower) {
            // make sure we don't fly too low
            end.set(origin)
            enemyEnt = enemy.GetEntity()
            if (enemyEnt != null) {
                end.z = lastVisibleEnemyPos.z + lastVisibleEnemyEyeOffset.z + fly_offset
            } else {
                // just use the default eye height for the player
                end.z = goalPos.z + DEFAULT_FLY_OFFSET + fly_offset
            }
            Game_local.gameLocal.clip.Translation(
                trace,
                origin,
                end,
                physicsObj.GetClipModel()!!,
                idMat3.getMat3_identity(),
                Game_local.MASK_MONSTERSOLID,
                this
            )
            vel.plusAssign(Seek(vel, origin, trace.endpos, AI_SEEK_PREDICTION))
        }
    }

    /*
     ============
     idAI::FlySeekGoal
     ============
     */
    protected fun FlySeekGoal(vel: idVec3, goalPos: idVec3) {
        val seekVel = idVec3()

        // seek the goal position
        seekVel.set(Seek(vel, physicsObj.GetOrigin(), goalPos, AI_SEEK_PREDICTION))
        seekVel.timesAssign(fly_seek_scale)
        vel.plusAssign(seekVel)
    }

    /*
     ============
     idAI::AdjustFlySpeed
     ============
     */
    protected fun AdjustFlySpeed(vel: idVec3) {
        var speed: Float

        // apply dampening
        vel.minusAssign(vel.times(AI_FLY_DAMPENING * MS2SEC(idGameLocal.msec.toFloat())))

        // gradually speed up/slow down to desired speed
        speed = vel.Normalize()
        speed += (move.speed - speed) * MS2SEC(idGameLocal.msec.toFloat())
        if (speed < 0.0f) {
            speed = 0.0f
        } else if (move.speed != 0.0f && speed > move.speed) {
            speed = move.speed
        }
        vel.timesAssign(speed)
    }

    /*
     ============
     idAI::FlyTurn
     ============
     */
    protected fun FlyTurn() {
        if (move.moveCommand == moveCommand_t.MOVE_FACE_ENEMY) {
            TurnToward(lastVisibleEnemyPos)
        } else if (move.moveCommand == moveCommand_t.MOVE_FACE_ENTITY && move.goalEntity.GetEntity() != null) {
            TurnToward(move.goalEntity.GetEntity()!!.GetPhysics().GetOrigin())
        } else if (move.speed > 0.0f) {
            val vel = physicsObj.GetLinearVelocity()
            if (vel.ToVec2().LengthSqr() > 0.1f) {
                TurnToward(vel.ToYaw())
            }
        }
        Turn()
    }

    /*
     ============
     idAI::FlyMove
     ============
     */
    protected fun FlyMove() {
        val goalPos = idVec3()
        val oldorigin = idVec3()
        val newDest = idVec3()
        AI_BLOCKED.underscore(false)
        if (move.moveCommand != moveCommand_t.MOVE_NONE && ReachedPos(move.moveDest, move.moveCommand)) {
            StopMove(moveStatus_t.MOVE_STATUS_DONE)
        }
        if (SysCvar.ai_debugMove.GetBool()) {
            Game_local.gameLocal.Printf(
                "%d: %s: %s, vel = %.2f, sp = %.2f, maxsp = %.2f\n",
                Game_local.gameLocal.time,
                name,
                moveCommandString[TempDump.etoi(move.moveCommand)],
                physicsObj.GetLinearVelocity().Length(),
                move.speed,
                fly_speed
            )
        }
        if (move.moveCommand != moveCommand_t.MOVE_TO_POSITION_DIRECT) {
            val vel = idVec3(physicsObj.GetLinearVelocity())
            if (GetMovePos(goalPos)) {
                CheckObstacleAvoidance(goalPos, newDest)
                goalPos.set(newDest)
            }
            if (move.speed != 0.0f) {
                FlySeekGoal(vel, goalPos)
            }

            // add in bobbing
            AddFlyBob(vel)
            if (enemy.GetEntity() != null && move.moveCommand != moveCommand_t.MOVE_TO_POSITION) {
                AdjustFlyHeight(vel, goalPos)
            }
            AdjustFlySpeed(vel)
            physicsObj.SetLinearVelocity(vel)
        }

        // turn
        FlyTurn()

        // run the physics for this frame
        oldorigin.set(physicsObj.GetOrigin())
        physicsObj.UseFlyMove(true)
        physicsObj.UseVelocityMove(false)
        physicsObj.SetDelta(vec3_zero)
        physicsObj.ForceDeltaMove(disableGravity)
        RunPhysics()
        val moveResult = physicsObj.GetMoveResult()
        if (!af_push_moveables && attack.Length() != 0 && TestMelee()) {
            DirectDamage(attack, enemy.GetEntity()!!)
        } else {
            val blockEnt = physicsObj.GetSlideMoveEntity()
            if (blockEnt != null && blockEnt is idMoveable && blockEnt.GetPhysics().IsPushable()) {
                KickObstacles(viewAxis[0], kickForce, blockEnt)
            } else if (moveResult == monsterMoveResult_t.MM_BLOCKED) {
                move.blockTime = Game_local.gameLocal.time + 500
                AI_BLOCKED.underscore(true)
            }
        }
        val org = idVec3(physicsObj.GetOrigin())
        if (oldorigin != org) {
            TouchTriggers()
        }
        if (SysCvar.ai_debugMove.GetBool()) {
            Game_local.gameRenderWorld!!.DebugLine(colorCyan, oldorigin, physicsObj.GetOrigin(), 4000)
            Game_local.gameRenderWorld!!.DebugBounds(
                colorOrange,
                physicsObj.GetBounds(),
                org,
                idGameLocal.msec
            )
            Game_local.gameRenderWorld!!.DebugBounds(
                colorMagenta,
                physicsObj.GetBounds(),
                move.moveDest,
                idGameLocal.msec
            )
            Game_local.gameRenderWorld!!.DebugLine(
                colorRed,
                org,
                org.plus(physicsObj.GetLinearVelocity()),
                idGameLocal.msec,
                true
            )
            Game_local.gameRenderWorld!!.DebugLine(
                colorBlue,
                org,
                goalPos,
                idGameLocal.msec,
                true
            )
            Game_local.gameRenderWorld!!.DebugLine(
                colorYellow,
                org.plus(EyeOffset()),
                org.plus(
                    EyeOffset().plus(
                        viewAxis[0].times(physicsObj.GetGravityAxis().times(16.0f))
                    )
                ),
                idGameLocal.msec,
                true
            )
            DrawRoute()
        }
    }

    /*
     ============
     idAI::StaticMove
     ============
     */
    protected fun StaticMove() {
        val enemyEnt = enemy.GetEntity()
        if (AI_DEAD.underscore()!!) {
            return
        }
        if (move.moveCommand == moveCommand_t.MOVE_FACE_ENEMY && enemyEnt != null) {
            TurnToward(lastVisibleEnemyPos)
        } else if (move.moveCommand == moveCommand_t.MOVE_FACE_ENTITY && move.goalEntity.GetEntity() != null) {
            TurnToward(move.goalEntity.GetEntity()!!.GetPhysics().GetOrigin())
        } else if (move.moveCommand != moveCommand_t.MOVE_NONE) {
            TurnToward(move.moveDest)
        }
        Turn()
        physicsObj.ForceDeltaMove(true) // disable gravity
        RunPhysics()
        AI_ONGROUND.underscore(false)
        if (!af_push_moveables && attack.Length() != 0 && TestMelee()) {
            DirectDamage(attack, enemyEnt!!)
        }
        if (SysCvar.ai_debugMove.GetBool()) {
            val org = physicsObj.GetOrigin()
            Game_local.gameRenderWorld!!.DebugBounds(
                colorMagenta,
                physicsObj.GetBounds(),
                org,
                idGameLocal.msec
            )
            Game_local.gameRenderWorld!!.DebugLine(
                colorBlue,
                org,
                move.moveDest,
                idGameLocal.msec,
                true
            )
            Game_local.gameRenderWorld!!.DebugLine(
                colorYellow,
                org.plus(EyeOffset()),
                org.plus(
                    EyeOffset().plus(
                        viewAxis[0].times(physicsObj.GetGravityAxis().times(16.0f))
                    )
                ),
                idGameLocal.msec,
                true
            )
        }
    }

    // damage
    /*
     ============
     idAI::Pain
     ============
     */
    override fun Pain(
        inflictor: idEntity?,
        attacker: idEntity?,
        damage: Int,
        dir: idVec3,
        location: Int
    ): Boolean {
        val actor: idActor?
        AI_PAIN.underscore(super.Pain(inflictor, attacker, damage, dir, location))
        AI_DAMAGE.underscore(true)

        // force a blink
        blink_time = 0

        // ignore damage from self
        if (attacker !== this) {
            if (inflictor != null) {
                AI_SPECIAL_DAMAGE.underscore(inflictor.spawnArgs.GetInt("special_damage") * 1.0f)
            } else {
                AI_SPECIAL_DAMAGE.underscore(0.0f)
            }
            if (enemy.GetEntity() !== attacker && attacker is idActor) {
                actor = attacker
                if ((ReactionTo(actor) and ATTACK_ON_DAMAGE) != 0) {
                    Game_local.gameLocal.AlertAI(actor)
                    SetEnemy(actor)
                }
            }
        }
        return AI_PAIN.underscore()!! /*!= 0*/
    }

    /*
     ============
     idAI::Killed
     ============
     */
    override fun Killed(inflictor: idEntity?, attacker: idEntity?, damage: Int, dir: idVec3, location: Int) {
        val modelDeath = arrayOfNulls<String>(1)

        // make sure the monster is activated
        EndAttack()
        if (SysCvar.g_debugDamage.GetBool()) {
            Game_local.gameLocal.Printf(
                "Damage: joint: '%s', zone '%s'\n", animator.GetJointName(location),
                GetDamageGroup(location)
            )
        }
        if (inflictor != null) {
            AI_SPECIAL_DAMAGE.underscore(inflictor.spawnArgs.GetInt("special_damage") * 1.0f)
        } else {
            AI_SPECIAL_DAMAGE.underscore(0.0f)
        }
        if (AI_DEAD.underscore()!!) {
            AI_PAIN.underscore(true)
            AI_DAMAGE.underscore(true)
            return
        }

        // stop all voice sounds
        StopSound(TempDump.etoi(gameSoundChannel_t.SND_CHANNEL_VOICE), false)
        if (head?.GetEntity() != null) {
            head.GetEntity()!!.StopSound(TempDump.etoi(gameSoundChannel_t.SND_CHANNEL_VOICE), false)
            head.GetEntity()!!.GetAnimator().ClearAllAnims(Game_local.gameLocal.time, 100)
        }
        disableGravity = false
        move.moveType = moveType_t.MOVETYPE_DEAD
        af_push_moveables = false
        physicsObj.UseFlyMove(false)
        physicsObj.ForceDeltaMove(false)

        // end our looping ambient sound
        StopSound(TempDump.etoi(gameSoundChannel_t.SND_CHANNEL_AMBIENT), false)
        if (attacker != null && attacker is idActor) {
            Game_local.gameLocal.AlertAI(attacker)
        }

        // activate targets
        ActivateTargets(attacker)
        RemoveAttachments()
        RemoveProjectile()
        StopMove(moveStatus_t.MOVE_STATUS_DONE)
        ClearEnemy()
        AI_DEAD.underscore(true)

        // make monster nonsolid
        physicsObj.SetContents(0)
        physicsObj.GetClipModel()!!.Unlink()
        Unbind()
        if (StartRagdoll()) {
            StartSound("snd_death", gameSoundChannel_t.SND_CHANNEL_VOICE, 0, false, CInt())
        }
        if (spawnArgs.GetString("model_death", "", modelDeath)) {
            // lost soul is only case that does not use a ragdoll and has a model_death so get the death sound in here
            StartSound("snd_death", gameSoundChannel_t.SND_CHANNEL_VOICE, 0, false, CInt())
            renderEntity!!.shaderParms[RenderWorld.SHADERPARM_TIMEOFFSET] =
                -MS2SEC(Game_local.gameLocal.time.toFloat())
            SetModel(modelDeath[0]!!)
            physicsObj.SetLinearVelocity(vec3_zero)
            physicsObj.PutToRest()
            physicsObj.DisableImpact()
        }
        restartParticles = false
        state = GetScriptFunction("state_Killed")
        SetState(state)
        SetWaitState("")
        var kv = spawnArgs.MatchPrefix("def_drops", null)
        while (kv != null) {
            val args = idDict()
            args.Set("classname", kv.GetValue())
            args.Set("origin", physicsObj.GetOrigin().ToString())
            Game_local.gameLocal.SpawnEntityDef(args)
            kv = spawnArgs.MatchPrefix("def_drops", kv)
        }
        if (attacker != null && attacker is idPlayer && inflictor != null && inflictor !is idSoulCubeMissile) {
            attacker.AddAIKill()
        }
    }

    // navigation
    /*
     ============
     idAI::KickObstacles
     ============
     */
    protected fun KickObstacles(dir: idVec3, force: Float, alwaysKick: idEntity?) {
        var i: Int
        val numListedClipModels: Int
        val clipBounds: idBounds
        var obEnt: idEntity
        var clipModel: idClipModel
        val clipModelList = arrayOfNulls<idClipModel>(Game_local.MAX_GENTITIES)
        val clipmask: Int
        val org = idVec3()
        val forceVec = idVec3()
        val delta = idVec3()
        val perpendicular = idVec2()
        org.set(physicsObj.GetOrigin())

        // find all possible obstacles
        // FIX: GetAbsBounds() returns a reference in Kotlin; C++ copies by value.
        // Must deep-copy to avoid corrupting physics internal bounds via TranslateSelf/ExpandSelf.
        clipBounds = idBounds(physicsObj.GetAbsBounds())
        clipBounds.TranslateSelf(dir.times(32.0f))
        clipBounds.ExpandSelf(8.0f)
        clipBounds.AddPoint(org)
        clipmask = physicsObj.GetClipMask()
        numListedClipModels = Game_local.gameLocal.clip.ClipModelsTouchingBounds(
            clipBounds,
            clipmask,
            clipModelList,
            Game_local.MAX_GENTITIES
        )
        i = 0
        while (i < numListedClipModels) {
            clipModel = clipModelList[i]!!
            obEnt = clipModel.GetEntity()!!
            if (obEnt === alwaysKick) {
                // we'll kick this one outside the loop
                i++
                continue
            }
            if (!clipModel.IsTraceModel()) {
                i++
                continue
            }
            if (obEnt is idMoveable && obEnt.GetPhysics().IsPushable()) {
                delta.set(obEnt.GetPhysics().GetOrigin().minus(org))
                delta.NormalizeFast()
                perpendicular.x = -delta.y
                perpendicular.y = delta.x
                delta.z += 0.5f
                delta.ToVec2_oPluSet(perpendicular.times(Game_local.gameLocal.random.CRandomFloat() * 0.5f))
                forceVec.set(delta.times(force * obEnt.GetPhysics().GetMass()))
                obEnt.ApplyImpulse(this, 0, obEnt.GetPhysics().GetOrigin(), forceVec)
            }
            i++
        }
        if (alwaysKick != null) {
            delta.set(alwaysKick.GetPhysics().GetOrigin().minus(org))
            delta.NormalizeFast()
            perpendicular.x = -delta.y
            perpendicular.y = delta.x
            delta.z += 0.5f
            delta.ToVec2_oPluSet(perpendicular.times(Game_local.gameLocal.random.CRandomFloat() * 0.5f))
            forceVec.set(delta.times(force * alwaysKick.GetPhysics().GetMass()))
            alwaysKick.ApplyImpulse(this, 0, alwaysKick.GetPhysics().GetOrigin(), forceVec)
        }
    }

    /*
     ============
     idAI::ReachedPos
     ============
     */
    protected fun ReachedPos(pos: idVec3, moveCommand: moveCommand_t?): Boolean {
        return if (move.moveType == moveType_t.MOVETYPE_SLIDE) {
            val bnds = idBounds(idVec3(-4.0f, -4.0f, -8.0f), idVec3(4.0f, 4.0f, 64.0f))
            bnds.TranslateSelf(physicsObj.GetOrigin())
            bnds.ContainsPoint(pos)
        } else {
            if (moveCommand == moveCommand_t.MOVE_TO_ENEMY || moveCommand == moveCommand_t.MOVE_TO_ENTITY) {
                physicsObj.GetAbsBounds().IntersectsBounds(idBounds(pos).Expand(8.0f))
            } else {
                val bnds = idBounds(idVec3(-16.0f, -16.0f, -8.0f), idVec3(16.0f, 16.0f, 64.0f))
                bnds.TranslateSelf(physicsObj.GetOrigin())
                bnds.ContainsPoint(pos)
            }
        }
    }

    /*
     =====================
     idAI::TravelDistance

     Returns the approximate travel distance from one position to the goal, or if no AAS, the straight line distance.

     This is feakin' slow, so it's not good to do it too many times per frame.  It also is slower the further you
     are from the goal, so try to break the goals up into shorter distances.
     =====================
     */
    protected fun TravelDistance(start: idVec3, end: idVec3): Float {
        val fromArea: Int
        val toArea: Int
        val dist: Float
        val delta: idVec2
        //            aasPath_s path;
        if (aas == null) {
            // no aas, so just take the straight line distance
            delta = end.ToVec2().minus(start.ToVec2())
            dist = delta.LengthFast()
            if (SysCvar.ai_debugMove.GetBool()) {
                Game_local.gameRenderWorld!!.DebugLine(
                    colorBlue,
                    start,
                    end,
                    idGameLocal.msec,
                    false
                )
                Game_local.gameRenderWorld!!.DrawText(
                    Str.va("%d", dist.toInt()),
                    start.plus(end).times(0.5f),
                    0.1f,
                    colorWhite,
                    Game_local.gameLocal.GetLocalPlayer()!!.viewAngles.ToMat3()
                )
            }
            return dist
        }
        fromArea = PointReachableAreaNum(start)
        toArea = PointReachableAreaNum(end)
        if (0 == fromArea || 0 == toArea) {
            // can't seem to get there
            return -1.0f
        }
        if (fromArea == toArea) {
            // same area, so just take the straight line distance
            delta = end.ToVec2().minus(start.ToVec2())
            dist = delta.LengthFast()
            if (SysCvar.ai_debugMove.GetBool()) {
                Game_local.gameRenderWorld!!.DebugLine(
                    colorBlue,
                    start,
                    end,
                    idGameLocal.msec,
                    false
                )
                Game_local.gameRenderWorld!!.DrawText(
                    Str.va("%d", dist.toInt()),
                    start.plus(end).times(0.5f),
                    0.1f,
                    colorWhite,
                    Game_local.gameLocal.GetLocalPlayer()!!.viewAngles.ToMat3()
                )
            }
            return dist
        }
        val reach = arrayOf<AASFile.idReachability?>(null)
        val travelTime = CInt()
        if (!aas!!.RouteToGoalArea(fromArea, start, toArea, travelFlags, travelTime, reach)) {
            return -1.0f
        }
        if (SysCvar.ai_debugMove.GetBool()) {
            if (move.moveType == moveType_t.MOVETYPE_FLY) {
                aas!!.ShowFlyPath(start, toArea, end)
            } else {
                aas!!.ShowWalkPath(start, toArea, end)
            }
        }
        return travelTime.integerValue.toFloat()
    }

    /*
     ============
     idAI::PointReachableAreaNum
     ============
     */
    protected fun PointReachableAreaNum(pos: idVec3, boundsScale: Float = 2.0f /*= 2.0f*/): Int {
        val areaNum: Int
        val size = idVec3()
        val bounds = idBounds()
        if (aas == null) {
            return 0
        }
        size.set(aas!!.GetSettings()!!.boundingBoxes[0][1].times(boundsScale))
        bounds[0].set(size.unaryMinus())
        size.z = 32.0f
        // FIX: C++ copies by value; use .set() to avoid aliasing bounds[1] with size
        bounds[1].set(size)
        areaNum = if (move.moveType == moveType_t.MOVETYPE_FLY) {
            aas!!.PointReachableAreaNum(pos, bounds, AASFile.AREA_REACHABLE_WALK or AASFile.AREA_REACHABLE_FLY)
        } else {
            aas!!.PointReachableAreaNum(pos, bounds, AASFile.AREA_REACHABLE_WALK)
        }
        return areaNum
    }

    /*
     ============
     idAI::PathToGoal
     ============
     */
    protected fun PathToGoal(
        path: AAS.aasPath_s,
        areaNum: Int,
        origin: idVec3,
        goalAreaNum: Int,
        goalOrigin: idVec3
    ): Boolean {
        val org = idVec3()
        val goal = idVec3()
        if (aas == null) {
            return false
        }
        org.set(origin)
        aas!!.PushPointIntoAreaNum(areaNum, org)
        if (0 == areaNum) {
            return false
        }
        goal.set(goalOrigin)
        aas!!.PushPointIntoAreaNum(goalAreaNum, goal)
        if (0 == goalAreaNum) {
            return false
        }
        return if (move.moveType == moveType_t.MOVETYPE_FLY) {
            aas!!.FlyPathToGoal(path, areaNum, org, goalAreaNum, goal, travelFlags)
        } else {
            aas!!.WalkPathToGoal(path, areaNum, org, goalAreaNum, goal, travelFlags)
        }
    }

    /*
     ============
     idAI::DrawRoute
     ============
     */
    protected fun DrawRoute() {
        if (aas != null && move.toAreaNum != 0 && move.moveCommand != moveCommand_t.MOVE_NONE && move.moveCommand != moveCommand_t.MOVE_WANDER && move.moveCommand != moveCommand_t.MOVE_FACE_ENEMY && move.moveCommand != moveCommand_t.MOVE_FACE_ENTITY && move.moveCommand != moveCommand_t.MOVE_TO_POSITION_DIRECT) {
            if (move.moveType == moveType_t.MOVETYPE_FLY) {
                aas!!.ShowFlyPath(physicsObj.GetOrigin(), move.toAreaNum, move.moveDest)
            } else {
                aas!!.ShowWalkPath(physicsObj.GetOrigin(), move.toAreaNum, move.moveDest)
            }
        }
    }

    /*
     ============
     idAI::GetMovePos
     ============
     */
    protected fun GetMovePos(seekPos: idVec3): Boolean {
        val areaNum: Int
        val path = AAS.aasPath_s()
        var result: Boolean
        val org = idVec3()
        org.set(physicsObj.GetOrigin())
        seekPos.set(org)
        when (move.moveCommand) {
            moveCommand_t.MOVE_NONE -> {
                seekPos.set(move.moveDest)
                return false
            }

            moveCommand_t.MOVE_FACE_ENEMY, moveCommand_t.MOVE_FACE_ENTITY -> {
                seekPos.set(move.moveDest)
                return false
            }

            moveCommand_t.MOVE_TO_POSITION_DIRECT -> {
                seekPos.set(move.moveDest)
                if (ReachedPos(move.moveDest, move.moveCommand)) {
                    StopMove(moveStatus_t.MOVE_STATUS_DONE)
                }
                return false
            }

            moveCommand_t.MOVE_SLIDE_TO_POSITION -> {
                seekPos.set(org)
                return false
            }

            else -> {}
        }
        if (move.moveCommand == moveCommand_t.MOVE_TO_ENTITY) {
            MoveToEntity(move.goalEntity.GetEntity())
        }
        move.moveStatus = moveStatus_t.MOVE_STATUS_MOVING
        result = false
        if (Game_local.gameLocal.time > move.blockTime) {
            if (move.moveCommand == moveCommand_t.MOVE_WANDER) {
                move.moveDest.set(
                    org.plus(
                        viewAxis[0].times(physicsObj.GetGravityAxis().times(256.0f))
                    )
                )
            } else {
                if (ReachedPos(move.moveDest, move.moveCommand)) {
                    StopMove(moveStatus_t.MOVE_STATUS_DONE)
                    seekPos.set(org)
                    return false
                }
            }
            if (aas != null && move.toAreaNum != 0) {
                areaNum = PointReachableAreaNum(org)
                if (PathToGoal(path, areaNum, org, move.toAreaNum, move.moveDest)) {
                    seekPos.set(path.moveGoal)
                    result = true
                    move.nextWanderTime = 0
                } else {
                    AI_DEST_UNREACHABLE.underscore(true)
                }
            }
        }
        if (!result) {
            // wander around
            if (Game_local.gameLocal.time > move.nextWanderTime || !StepDirection(move.wanderYaw)) {
                result = NewWanderDir(move.moveDest)
                if (!result) {
                    StopMove(moveStatus_t.MOVE_STATUS_DEST_UNREACHABLE)
                    AI_DEST_UNREACHABLE.underscore(true)
                    seekPos.set(org)
                    return false
                }
            } else {
                result = true
            }
            seekPos.set(org.plus(move.moveDir.times(2048.0f)))
            if (SysCvar.ai_debugMove.GetBool()) {
                Game_local.gameRenderWorld!!.DebugLine(
                    colorYellow,
                    org,
                    seekPos,
                    idGameLocal.msec,
                    true
                )
            }
        } else {
            AI_DEST_UNREACHABLE.underscore(false)
        }
        if (result && SysCvar.ai_debugMove.GetBool()) {
            Game_local.gameRenderWorld!!.DebugLine(colorCyan, physicsObj.GetOrigin(), seekPos)
        }
        return result
    }

    /*
     ============
     idAI::MoveDone
     ============
     */
    protected fun MoveDone(): Boolean {
        return move.moveCommand == moveCommand_t.MOVE_NONE
    }

    /*
     ============
     idAI::EntityCanSeePos
     ============
     */
    protected fun EntityCanSeePos(actor: idActor, actorOrigin: idVec3, pos: idVec3): Boolean {
        val eye = idVec3()
        val point = idVec3()
        val results = trace_s()
        val handle: pvsHandle_t?
        handle = Game_local.gameLocal.pvs.SetupCurrentPVS(actor.GetPVSAreas(), actor.GetNumPVSAreas())
        if (!Game_local.gameLocal.pvs.InCurrentPVS(handle, GetPVSAreas(), GetNumPVSAreas())) {
            Game_local.gameLocal.pvs.FreeCurrentPVS(handle)
            return false
        }
        Game_local.gameLocal.pvs.FreeCurrentPVS(handle)
        eye.set(actorOrigin.plus(actor.EyeOffset()))
        point.set(pos)
        point.plusAssign(2, 1.0f)
        physicsObj.DisableClip()
        Game_local.gameLocal.clip.TracePoint(results, eye, point, Game_local.MASK_SOLID, actor)
        if (results.fraction >= 1.0f || Game_local.gameLocal.GetTraceEntity(results) === this) {
            physicsObj.EnableClip()
            return true
        }
        val bounds = physicsObj.GetBounds()
        point.plusAssign(2, bounds[1, 2] - bounds[0, 2])
        Game_local.gameLocal.clip.TracePoint(results, eye, point, Game_local.MASK_SOLID, actor)
        physicsObj.EnableClip()
        return results.fraction >= 1.0f || Game_local.gameLocal.GetTraceEntity(results) === this
    }

    /*
     ============
     idAI::BlockedFailSafe
     ============
     */
    protected fun BlockedFailSafe() {
        if (!SysCvar.ai_blockedFailSafe.GetBool() || blockedRadius < 0.0f) {
            return
        }
        if (!physicsObj.OnGround() || enemy.GetEntity() == null || physicsObj.GetOrigin()
                .minus(move.lastMoveOrigin).LengthSqr() > Square(blockedRadius)
        ) {
            move.lastMoveOrigin.set(physicsObj.GetOrigin())
            move.lastMoveTime = Game_local.gameLocal.time
        }
        if (move.lastMoveTime < Game_local.gameLocal.time - blockedMoveTime) {
            if (lastAttackTime < Game_local.gameLocal.time - blockedAttackTime) {
                AI_BLOCKED.underscore(true)
                move.lastMoveTime = Game_local.gameLocal.time
            }
        }
    }

    // movement control
    /*
     ============
     idAI::StopMove
     ============
     */
    protected fun StopMove(status: moveStatus_t) {
        AI_MOVE_DONE.underscore(true)
        AI_FORWARD.underscore(false)
        move.moveCommand = moveCommand_t.MOVE_NONE
        move.moveStatus = status
        move.toAreaNum = 0
        move.goalEntity.oSet(null)
        move.moveDest.set(physicsObj.GetOrigin())
        AI_DEST_UNREACHABLE.underscore(false)
        AI_OBSTACLE_IN_PATH.underscore(false)
        AI_BLOCKED.underscore(false)
        move.startTime = Game_local.gameLocal.time
        move.duration = 0
        move.range = 0.0f
        move.speed = 0.0f
        move.anim = 0
        move.moveDir.Zero()
        move.lastMoveOrigin.Zero()
        move.lastMoveTime = Game_local.gameLocal.time
    }

    /*
     =====================
     idAI::FaceEnemy

     Continually face the enemy's last known position.  MoveDone is always true in this case.
     =====================
     */
    protected fun FaceEnemy(): Boolean {
        val enemyEnt = enemy.GetEntity()
        if (null == enemyEnt) {
            StopMove(moveStatus_t.MOVE_STATUS_DEST_NOT_FOUND)
            return false
        }
        TurnToward(lastVisibleEnemyPos)
        move.goalEntity.oSet(enemyEnt)
        move.moveDest.set(physicsObj.GetOrigin())
        move.moveCommand = moveCommand_t.MOVE_FACE_ENEMY
        move.moveStatus = moveStatus_t.MOVE_STATUS_WAITING
        move.startTime = Game_local.gameLocal.time
        move.speed = 0.0f
        AI_MOVE_DONE.underscore(true)
        AI_FORWARD.underscore(false)
        AI_DEST_UNREACHABLE.underscore(false)
        return true
    }

    /*
     =====================
     idAI::FaceEntity

     Continually face the entity position.  MoveDone is always true in this case.
     =====================
     */
    protected fun FaceEntity(ent: idEntity?): Boolean {
        if (null == ent) {
            StopMove(moveStatus_t.MOVE_STATUS_DEST_NOT_FOUND)
            return false
        }
        val entityOrg = idVec3(ent.GetPhysics().GetOrigin())
        TurnToward(entityOrg)
        move.goalEntity.oSet(ent)
        move.moveDest.set(physicsObj.GetOrigin())
        move.moveCommand = moveCommand_t.MOVE_FACE_ENTITY
        move.moveStatus = moveStatus_t.MOVE_STATUS_WAITING
        move.startTime = Game_local.gameLocal.time
        move.speed = 0.0f
        AI_MOVE_DONE.underscore(true)
        AI_FORWARD.underscore(false)
        AI_DEST_UNREACHABLE.underscore(false)
        return true
    }

    /*
     ============
     idAI::DirectMoveToPosition
     ============
     */
    protected fun DirectMoveToPosition(pos: idVec3): Boolean {
        if (ReachedPos(pos, move.moveCommand)) {
            StopMove(moveStatus_t.MOVE_STATUS_DONE)
            return true
        }
        move.moveDest.set(pos)
        move.goalEntity.oSet(null)
        move.moveCommand = moveCommand_t.MOVE_TO_POSITION_DIRECT
        move.moveStatus = moveStatus_t.MOVE_STATUS_MOVING
        move.startTime = Game_local.gameLocal.time
        move.speed = fly_speed
        AI_MOVE_DONE.underscore(false)
        AI_DEST_UNREACHABLE.underscore(false)
        AI_FORWARD.underscore(true)
        if (move.moveType == moveType_t.MOVETYPE_FLY) {
            val dir = idVec3(pos.minus(physicsObj.GetOrigin()))
            dir.Normalize()
            dir.timesAssign(fly_speed)
            physicsObj.SetLinearVelocity(dir)
        }
        return true
    }

    /*
     =====================
     idAI::MoveToEnemyHeight
     =====================
     */
    protected fun MoveToEnemyHeight(): Boolean {
        val enemyEnt = enemy.GetEntity()
        if (null == enemyEnt || move.moveType != moveType_t.MOVETYPE_FLY) {
            StopMove(moveStatus_t.MOVE_STATUS_DEST_NOT_FOUND)
            return false
        }
        move.moveDest.z = lastVisibleEnemyPos.z + enemyEnt.EyeOffset().z + fly_offset
        move.goalEntity.oSet(enemyEnt)
        move.moveCommand = moveCommand_t.MOVE_TO_ENEMYHEIGHT
        move.moveStatus = moveStatus_t.MOVE_STATUS_MOVING
        move.startTime = Game_local.gameLocal.time
        move.speed = 0.0f
        AI_MOVE_DONE.underscore(false)
        AI_DEST_UNREACHABLE.underscore(false)
        AI_FORWARD.underscore(false)
        return true
    }

    /*
     =====================
     idAI::MoveOutOfRange
     =====================
     */
    protected fun MoveOutOfRange(ent: idEntity?, range: Float): Boolean {
        val areaNum: Int
        val obstacle = arrayOf(AAS.aasObstacle_s())
        val goal = AAS.aasGoal_s()
        //            idBounds bounds;
        val pos = idVec3()
        if (null == aas || null == ent) {
            StopMove(moveStatus_t.MOVE_STATUS_DEST_UNREACHABLE)
            AI_DEST_UNREACHABLE.underscore(true)
            return false
        }
        val org = physicsObj.GetOrigin()
        areaNum = PointReachableAreaNum(org)

        // consider the entity the monster is getting close to as an obstacle
        obstacle[0].absBounds.set(ent.GetPhysics().GetAbsBounds())
        if (ent === enemy.GetEntity()) {
            pos.set(lastVisibleEnemyPos)
        } else {
            pos.set(ent.GetPhysics().GetOrigin())
        }
        val findGoal = idAASFindAreaOutOfRange(pos, range)
        if (!aas!!.FindNearestGoal(goal, areaNum, org, pos, travelFlags, obstacle, 1, findGoal)) {
            StopMove(moveStatus_t.MOVE_STATUS_DEST_UNREACHABLE)
            AI_DEST_UNREACHABLE.underscore(true)
            return false
        }
        if (ReachedPos(goal.origin, move.moveCommand)) {
            StopMove(moveStatus_t.MOVE_STATUS_DONE)
            return true
        }
        move.moveDest.set(goal.origin)
        move.toAreaNum = goal.areaNum
        move.goalEntity.oSet(ent)
        move.moveCommand = moveCommand_t.MOVE_OUT_OF_RANGE
        move.moveStatus = moveStatus_t.MOVE_STATUS_MOVING
        move.range = range
        move.speed = fly_speed
        move.startTime = Game_local.gameLocal.time
        AI_MOVE_DONE.underscore(false)
        AI_DEST_UNREACHABLE.underscore(false)
        AI_FORWARD.underscore(true)
        return true
    }

    /*
     =====================
     idAI::MoveToAttackPosition
     =====================
     */
    protected fun MoveToAttackPosition(ent: idEntity?, attack_anim: Int): Boolean {
        val areaNum: Int
        val obstacle = arrayOf<AAS.aasObstacle_s>(AAS.aasObstacle_s())
        val goal = AAS.aasGoal_s()
        val pos = idVec3()
        if (null == aas || null == ent) {
            StopMove(moveStatus_t.MOVE_STATUS_DEST_UNREACHABLE)
            AI_DEST_UNREACHABLE.underscore(true)
            return false
        }
        val org = physicsObj.GetOrigin()
        areaNum = PointReachableAreaNum(org)

        // consider the entity the monster is getting close to as an obstacle
        obstacle[0].absBounds.set(ent.GetPhysics().GetAbsBounds())
        if (ent === enemy.GetEntity()) {
            pos.set(lastVisibleEnemyPos)
        } else {
            pos.set(ent.GetPhysics().GetOrigin())
        }
        val findGoal = idAASFindAttackPosition(
            this,
            physicsObj.GetGravityAxis(),
            ent,
            pos,
            missileLaunchOffset[attack_anim]
        )
        try {
        if (!aas!!.FindNearestGoal(goal, areaNum, org, pos, travelFlags, obstacle, 1, findGoal)) {
            StopMove(moveStatus_t.MOVE_STATUS_DEST_UNREACHABLE)
            AI_DEST_UNREACHABLE.underscore(true)
            return false
        }
        move.moveDest.set(goal.origin)
        move.toAreaNum = goal.areaNum
        move.goalEntity.oSet(ent)
        move.moveCommand = moveCommand_t.MOVE_TO_ATTACK_POSITION
        move.moveStatus = moveStatus_t.MOVE_STATUS_MOVING
        move.speed = fly_speed
        move.startTime = Game_local.gameLocal.time
        move.anim = attack_anim
        AI_MOVE_DONE.underscore(false)
        AI_DEST_UNREACHABLE.underscore(false)
        AI_FORWARD.underscore(true)
        return true
        } finally {
            findGoal.cleanup()
        }
    }

    /*
     =====================
     idAI::MoveToEnemy
     =====================
     */
    protected fun MoveToEnemy(): Boolean {
        val areaNum: Int
        val path = AAS.aasPath_s()
        val enemyEnt = enemy.GetEntity()
        if (null == enemyEnt) {
            StopMove(moveStatus_t.MOVE_STATUS_DEST_NOT_FOUND)
            return false
        }
        if (ReachedPos(lastVisibleReachableEnemyPos, moveCommand_t.MOVE_TO_ENEMY)) {
            if (!ReachedPos(lastVisibleEnemyPos, moveCommand_t.MOVE_TO_ENEMY) || !AI_ENEMY_VISIBLE.underscore()!!) {
                StopMove(moveStatus_t.MOVE_STATUS_DEST_UNREACHABLE)
                AI_DEST_UNREACHABLE.underscore(true)
                return false
            }
            StopMove(moveStatus_t.MOVE_STATUS_DONE)
            return true
        }
        val pos = idVec3(lastVisibleReachableEnemyPos)
        move.toAreaNum = 0
        if (aas != null) {
            move.toAreaNum = PointReachableAreaNum(pos)
            aas!!.PushPointIntoAreaNum(move.toAreaNum, pos)
            areaNum = PointReachableAreaNum(physicsObj.GetOrigin())
            if (!PathToGoal(path, areaNum, physicsObj.GetOrigin(), move.toAreaNum, pos)) {
                AI_DEST_UNREACHABLE.underscore(true)
                return false
            }
        }
        if (0 == move.toAreaNum) {
            // if only trying to update the enemy position
            if (move.moveCommand == moveCommand_t.MOVE_TO_ENEMY) {
                if (aas == null) {
                    // keep the move destination up to date for wandering
                    move.moveDest.set(pos)
                }
                return false
            }
            if (!NewWanderDir(pos)) {
                StopMove(moveStatus_t.MOVE_STATUS_DEST_UNREACHABLE)
                AI_DEST_UNREACHABLE.underscore(true)
                return false
            }
        }
        if (move.moveCommand != moveCommand_t.MOVE_TO_ENEMY) {
            move.moveCommand = moveCommand_t.MOVE_TO_ENEMY
            move.startTime = Game_local.gameLocal.time
        }
        move.moveDest.set(pos)
        move.goalEntity.oSet(enemyEnt)
        move.speed = fly_speed
        move.moveStatus = moveStatus_t.MOVE_STATUS_MOVING
        AI_MOVE_DONE.underscore(false)
        AI_DEST_UNREACHABLE.underscore(false)
        AI_FORWARD.underscore(true)
        return true
    }

    /*
     =====================
     idAI::MoveToEntity
     =====================
     */
    protected fun MoveToEntity(ent: idEntity?): Boolean {
        val areaNum: Int
        val path = AAS.aasPath_s()
        val pos = idVec3()
        if (null == ent) {
            StopMove(moveStatus_t.MOVE_STATUS_DEST_NOT_FOUND)
            return false
        }
        pos.set(ent.GetPhysics().GetOrigin())
        if (move.moveType != moveType_t.MOVETYPE_FLY && (move.moveCommand != moveCommand_t.MOVE_TO_ENTITY || move.goalEntityOrigin != pos)) {
            ent.GetFloorPos(64.0f, pos)
        }
        if (ReachedPos(pos, moveCommand_t.MOVE_TO_ENTITY)) {
            StopMove(moveStatus_t.MOVE_STATUS_DONE)
            return true
        }
        move.toAreaNum = 0
        if (aas != null) {
            move.toAreaNum = PointReachableAreaNum(pos)
            aas!!.PushPointIntoAreaNum(move.toAreaNum, pos)
            areaNum = PointReachableAreaNum(physicsObj.GetOrigin())
            if (!PathToGoal(path, areaNum, physicsObj.GetOrigin(), move.toAreaNum, pos)) {
                AI_DEST_UNREACHABLE.underscore(true)
                return false
            }
        }
        if (0 == move.toAreaNum) {
            // if only trying to update the entity position
            if (move.moveCommand == moveCommand_t.MOVE_TO_ENTITY) {
                if (aas == null) {
                    // keep the move destination up to date for wandering
                    move.moveDest.set(pos)
                }
                return false
            }
            if (!NewWanderDir(pos)) {
                StopMove(moveStatus_t.MOVE_STATUS_DEST_UNREACHABLE)
                AI_DEST_UNREACHABLE.underscore(true)
                return false
            }
        }
        if (move.moveCommand != moveCommand_t.MOVE_TO_ENTITY || move.goalEntity.GetEntity() != ent) {
            move.startTime = Game_local.gameLocal.time
            move.goalEntity.oSet(ent)
            move.moveCommand = moveCommand_t.MOVE_TO_ENTITY
        }
        move.moveDest.set(pos)
        move.goalEntityOrigin.set(ent.GetPhysics().GetOrigin())
        move.moveStatus = moveStatus_t.MOVE_STATUS_MOVING
        move.speed = fly_speed
        AI_MOVE_DONE.underscore(false)
        AI_DEST_UNREACHABLE.underscore(false)
        AI_FORWARD.underscore(true)
        return true
    }

    /*
     =====================
     idAI::MoveToPosition
     =====================
     */
    protected fun MoveToPosition(pos: idVec3): Boolean {
        val org = idVec3()
        val areaNum: Int
        val path = AAS.aasPath_s()
        if (ReachedPos(pos, move.moveCommand)) {
            StopMove(moveStatus_t.MOVE_STATUS_DONE)
            return true
        }
        org.set(pos)
        move.toAreaNum = 0
        if (aas != null) {
            move.toAreaNum = PointReachableAreaNum(org)
            aas!!.PushPointIntoAreaNum(move.toAreaNum, org)
            areaNum = PointReachableAreaNum(physicsObj.GetOrigin())
            if (!PathToGoal(path, areaNum, physicsObj.GetOrigin(), move.toAreaNum, org)) {
                StopMove(moveStatus_t.MOVE_STATUS_DEST_UNREACHABLE)
                AI_DEST_UNREACHABLE.underscore(true)
                return false
            }
        }
        if (0 == move.toAreaNum && !NewWanderDir(org)) {
            StopMove(moveStatus_t.MOVE_STATUS_DEST_UNREACHABLE)
            AI_DEST_UNREACHABLE.underscore(true)
            return false
        }
        move.moveDest.set(org)
        move.goalEntity.oSet(null)
        move.moveCommand = moveCommand_t.MOVE_TO_POSITION
        move.moveStatus = moveStatus_t.MOVE_STATUS_MOVING
        move.startTime = Game_local.gameLocal.time
        move.speed = fly_speed
        AI_MOVE_DONE.underscore(false)
        AI_DEST_UNREACHABLE.underscore(false)
        AI_FORWARD.underscore(true)
        return true
    }

    /*
     =====================
     idAI::MoveToCover
     =====================
     */
    protected fun MoveToCover(entity: idEntity?, hideFromPos: idVec3): Boolean {
        val areaNum: Int
        val obstacle = arrayOf(AAS.aasObstacle_s())
        val hideGoal = AAS.aasGoal_s()
        //            idBounds bounds;
        if (null == aas || null == entity) {
            StopMove(moveStatus_t.MOVE_STATUS_DEST_UNREACHABLE)
            AI_DEST_UNREACHABLE.underscore(true)
            return false
        }
        val org = physicsObj.GetOrigin()
        areaNum = PointReachableAreaNum(org)

        // consider the entity the monster tries to hide from as an obstacle
        obstacle[0].absBounds.set(entity.GetPhysics().GetAbsBounds())
        val findCover = idAASFindCover(hideFromPos)
        try {
        if (!aas!!.FindNearestGoal(hideGoal, areaNum, org, hideFromPos, travelFlags, obstacle, 1, findCover)) {
            StopMove(moveStatus_t.MOVE_STATUS_DEST_UNREACHABLE)
            AI_DEST_UNREACHABLE.underscore(true)
            return false
        }
        if (ReachedPos(hideGoal.origin, move.moveCommand)) {
            StopMove(moveStatus_t.MOVE_STATUS_DONE)
            return true
        }
        move.moveDest.set(hideGoal.origin)
        move.toAreaNum = hideGoal.areaNum
        move.goalEntity.oSet(entity)
        move.moveCommand = moveCommand_t.MOVE_TO_COVER
        move.moveStatus = moveStatus_t.MOVE_STATUS_MOVING
        move.startTime = Game_local.gameLocal.time
        move.speed = fly_speed
        AI_MOVE_DONE.underscore(false)
        AI_DEST_UNREACHABLE.underscore(false)
        AI_FORWARD.underscore(true)
        return true
        } finally {
            findCover.cleanup()
        }
    }

    /*
     =====================
     idAI::SlideToPosition
     =====================
     */
    protected fun SlideToPosition(pos: idVec3, time: Float): Boolean {
        StopMove(moveStatus_t.MOVE_STATUS_DONE)
        move.moveDest.set(pos)
        move.goalEntity.oSet(null)
        move.moveCommand = moveCommand_t.MOVE_SLIDE_TO_POSITION
        move.moveStatus = moveStatus_t.MOVE_STATUS_MOVING
        move.startTime = Game_local.gameLocal.time
        move.duration = idPhysics.SnapTimeToPhysicsFrame(SEC2MS(time).toInt())
        AI_MOVE_DONE.underscore(false)
        AI_DEST_UNREACHABLE.underscore(false)
        AI_FORWARD.underscore(false)
        if (move.duration > 0) {
            move.moveDir.set(pos.minus(physicsObj.GetOrigin()).div(MS2SEC(move.duration.toFloat())))
            if (move.moveType != moveType_t.MOVETYPE_FLY) {
                move.moveDir.z = 0.0f
            }
            move.speed = move.moveDir.LengthFast()
        }
        return true
    }

    /*
     =====================
     idAI::WanderAround
     =====================
     */
    protected fun WanderAround(): Boolean {
        StopMove(moveStatus_t.MOVE_STATUS_DONE)
        move.moveDest.set(
            physicsObj.GetOrigin().plus(viewAxis[0].times(physicsObj.GetGravityAxis().times(256.0f)))
        )
        if (!NewWanderDir(move.moveDest)) {
            StopMove(moveStatus_t.MOVE_STATUS_DEST_UNREACHABLE)
            AI_DEST_UNREACHABLE.underscore(true)
            return false
        }
        move.moveCommand = moveCommand_t.MOVE_WANDER
        move.moveStatus = moveStatus_t.MOVE_STATUS_MOVING
        move.startTime = Game_local.gameLocal.time
        move.speed = fly_speed
        AI_MOVE_DONE.underscore(false)
        AI_FORWARD.underscore(true)
        return true
    }

    /*
     ================
     idAI::StepDirection
     ================
     */
    protected fun StepDirection(dir: Float): Boolean {
        val path = predictedPath_s()
        val org = idVec3()
        move.wanderYaw = dir
        move.moveDir.set(idAngles(0.0f, move.wanderYaw, 0.0f).ToForward())
        org.set(physicsObj.GetOrigin())
        PredictPath(
            this,
            aas,
            org,
            move.moveDir.times(48.0f),
            1000,
            1000,
            if (move.moveType == moveType_t.MOVETYPE_FLY) SE_BLOCKED else SE_ENTER_OBSTACLE or SE_BLOCKED or SE_ENTER_LEDGE_AREA,
            path
        )
        if (path.blockingEntity != null && (move.moveCommand == moveCommand_t.MOVE_TO_ENEMY || move.moveCommand == moveCommand_t.MOVE_TO_ENTITY) && path.blockingEntity === move.goalEntity.GetEntity()) {
            // don't report being blocked if we ran into our goal entity
            return true
        }
        if (move.moveType == moveType_t.MOVETYPE_FLY && path.endEvent == SE_BLOCKED) {
            var z: Float
            move.moveDir.set(path.endVelocity.times(1.0f / 48.0f))

            // trace down to the floor and see if we can go forward
            PredictPath(this, aas, org, idVec3(0.0f, 0.0f, -1024.0f), 1000, 1000, SE_BLOCKED, path)
            val floorPos = idVec3(path.endPos)
            PredictPath(this, aas, floorPos, move.moveDir.times(48.0f), 1000, 1000, SE_BLOCKED, path)
            if (0 == path.endEvent) {
                move.moveDir.z = -1.0f
                return true
            }

            // trace up to see if we can go over something and go forward
            PredictPath(this, aas, org, idVec3(0.0f, 0.0f, 256.0f), 1000, 1000, SE_BLOCKED, path)
            val ceilingPos = idVec3(path.endPos)
            z = org.z
            while (z <= ceilingPos.z + 64.0f) {
                val start = idVec3()
                if (z <= ceilingPos.z) {
                    start.x = org.x
                    start.y = org.y
                    start.z = z
                } else {
                    start.set(ceilingPos)
                }
                PredictPath(this, aas, start, move.moveDir.times(48.0f), 1000, 1000, SE_BLOCKED, path)
                if (0 == path.endEvent) {
                    move.moveDir.z = 1.0f
                    return true
                }
                z += 64.0f
            }
            return false
        }
        return path.endEvent == 0
    }

    /*
     ================
     idAI::NewWanderDir
     ================
     */
    protected fun NewWanderDir(dest: idVec3): Boolean {
        val deltax: Float
        val deltay: Float
        val d = FloatArray(3)
        var tdir: Float
        val olddir: Float
        val turnaround: Float
        move.nextWanderTime =
            (Game_local.gameLocal.time + (Game_local.gameLocal.random.RandomFloat() * 500 + 500)).toInt()
        olddir = idMath.AngleNormalize360(((current_yaw / 45).toInt() * 45).toFloat())
        turnaround = idMath.AngleNormalize360(olddir - 180)
        val org = idVec3(physicsObj.GetOrigin())
        deltax = dest.x - org.x
        deltay = dest.y - org.y
        if (deltax > 10) {
            d[1] = 0.0f
        } else if (deltax < -10) {
            d[1] = 180.0f
        } else {
            d[1] = DI_NODIR
        }
        if (deltay < -10) {
            d[2] = 270.0f
        } else if (deltay > 10) {
            d[2] = 90.0f
        } else {
            d[2] = DI_NODIR
        }

        // try direct route
        if (d[1] != DI_NODIR && d[2] != DI_NODIR) {
            tdir = if (d[1] == 0.0f) {
                if (d[2] == 90.0f) 45.0f else 315.0f
            } else {
                if (d[2] == 90.0f) 135.0f else 215.0f
            }
            if (tdir != turnaround && StepDirection(tdir)) {
                return true
            }
        }

        // try other directions
        if ((Game_local.gameLocal.random.RandomInt() and 1) != 0 || abs(deltay) > abs(deltax)) {
            tdir = d[1]
            d[1] = d[2]
            d[2] = tdir
        }
        if (d[1] != DI_NODIR && d[1] != turnaround && StepDirection(d[1])) {
            return true
        }
        if (d[2] != DI_NODIR && d[2] != turnaround && StepDirection(d[2])) {
            return true
        }

        // there is no direct path to the player, so pick another direction
        if (olddir != DI_NODIR && StepDirection(olddir)) {
            return true
        }

        // randomly determine direction of search
        if ((Game_local.gameLocal.random.RandomInt() and 1) != 0) {
            tdir = 0.0f
            while (tdir <= 315) {
                if (tdir != turnaround && StepDirection(tdir)) {
                    return true
                }
                tdir += 45.0f
            }
        } else {
            tdir = 315.0f
            while (tdir >= 0) {
                if (tdir != turnaround && StepDirection(tdir)) {
                    return true
                }
                tdir -= 45.0f
            }
        }
        if (turnaround != DI_NODIR && StepDirection(turnaround)) {
            return true
        }

        // can't move
        StopMove(moveStatus_t.MOVE_STATUS_DEST_UNREACHABLE)
        return false
    }

    // effects
    /*
     =====================
     idAI::SpawnParticlesOnJoint
     =====================
     */
    protected fun SpawnParticlesOnJoint(
        pe: particleEmitter_s,
        particleName: idStr,
        jointName: String
    ): idDeclParticle? {
        val origin = idVec3()
        val axis = idMat3()
        if (particleName.IsEmpty()) {
            return pe.particle
        }
        pe.joint = animator.GetJointHandle(jointName)
        if (pe.joint == Model.INVALID_JOINT) {
            Game_local.gameLocal.Warning("Unknown particleJoint '%s' on '%s'", jointName, name)
            pe.time = 0
            pe.particle = null
        } else {
            animator.GetJointTransform(pe.joint, Game_local.gameLocal.time, origin, axis)
            origin.set(renderEntity!!.origin.plus(origin.times(renderEntity!!.axis)))
            BecomeActive(TH_UPDATEPARTICLES)
            if (0 == Game_local.gameLocal.time) {
                // particles with time of 0 don't show, so set the time differently on the first frame
                pe.time = 1
            } else {
                pe.time = Game_local.gameLocal.time
            }
            pe.particle = DeclManager.declManager.FindType(declType_t.DECL_PARTICLE, particleName) as idDeclParticle
            Game_local.gameLocal.smokeParticles!!.EmitSmoke(
                pe.particle,
                pe.time,
                Game_local.gameLocal.random.CRandomFloat(),
                origin,
                axis
            )
        }
        return pe.particle
    }

    /*
     =====================
     idAI::SpawnParticles
     =====================
     */
    protected fun SpawnParticles(keyName: String) {
        var kv = spawnArgs.MatchPrefix(keyName, null)
        while (kv != null) {
            val pe = particleEmitter_s()
            var particleName = kv.GetValue()
            if (particleName.Length() != 0) {
                var jointName = kv.GetValue()
                val dash = jointName.Find('-')
                if (dash > 0) {
                    particleName = particleName.Left(dash)
                    jointName = jointName.Right(jointName.Length() - dash - 1)
                }
                SpawnParticlesOnJoint(pe, particleName, jointName.toString())
                particles.Append(pe)
            }
            kv = spawnArgs.MatchPrefix(keyName, kv)
        }
    }

    //
    //        protected boolean ParticlesActive();
    //
    // turning
    /*
     =====================
     idAI::FacingIdeal
     =====================
     */
    protected fun FacingIdeal(): Boolean {
        val diff: Float
        if (0.0f == turnRate) {
            return true
        }
        diff = idMath.AngleNormalize180(current_yaw - ideal_yaw)
        if (abs(diff) < 0.01) {
            // force it to be exact
            current_yaw = ideal_yaw
            return true
        }
        return false
    }

    /*
     =====================
     idAI::Turn
     =====================
     */
    protected fun Turn() {
        val diff: Float
        val diff2: Float
        var turnAmount: Float
        val animflags: animFlags_t
        if (0.0f == turnRate) {
            return
        }

        // check if the animator has marker this anim as non-turning
        animflags = if (!legsAnim.Disabled() && !legsAnim.AnimDone(0)) {
            legsAnim.GetAnimFlags()
        } else {
            torsoAnim.GetAnimFlags()
        }
        if (animflags.ai_no_turn) {
            return
        }
        if (anim_turn_angles != 0.0f && animflags.anim_turn) {
            val rotateAxis = idMat3()

            // set the blend between no turn and full turn
            val frac = anim_turn_amount / anim_turn_angles
            animator.CurrentAnim(Anim.ANIMCHANNEL_LEGS).SetSyncedAnimWeight(0, 1.0f - frac)
            animator.CurrentAnim(Anim.ANIMCHANNEL_LEGS).SetSyncedAnimWeight(1, frac)
            animator.CurrentAnim(Anim.ANIMCHANNEL_TORSO).SetSyncedAnimWeight(0, 1.0f - frac)
            animator.CurrentAnim(Anim.ANIMCHANNEL_TORSO).SetSyncedAnimWeight(1, frac)

            // get the total rotation from the start of the anim
            animator.GetDeltaRotation(0, Game_local.gameLocal.time, rotateAxis)
            current_yaw = idMath.AngleNormalize180(anim_turn_yaw + rotateAxis[0].ToYaw())
        } else {
            diff = idMath.AngleNormalize180(ideal_yaw - current_yaw)
            turnVel += AI_TURN_SCALE * diff * MS2SEC(idGameLocal.msec.toFloat())
            if (turnVel > turnRate) {
                turnVel = turnRate
            } else if (turnVel < -turnRate) {
                turnVel = -turnRate
            }
            turnAmount = turnVel * MS2SEC(idGameLocal.msec.toFloat())
            if (diff >= 0.0f && turnAmount >= diff) {
                turnVel = diff / MS2SEC(idGameLocal.msec.toFloat())
                turnAmount = diff
            } else if (diff <= 0.0f && turnAmount <= diff) {
                turnVel = diff / MS2SEC(idGameLocal.msec.toFloat())
                turnAmount = diff
            }
            current_yaw += turnAmount
            current_yaw = idMath.AngleNormalize180(current_yaw)
            diff2 = idMath.AngleNormalize180(ideal_yaw - current_yaw)
            if (abs(diff2) < 0.1f) {
                current_yaw = ideal_yaw
            }
        }
        viewAxis.set(idAngles(0.0f, current_yaw, 0.0f).ToMat3())
        if (SysCvar.ai_debugMove.GetBool()) {
            val org = physicsObj.GetOrigin()
            Game_local.gameRenderWorld!!.DebugLine(
                colorRed,
                org,
                org.plus(idAngles(0.0f, ideal_yaw, 0.0f).ToForward().times(64.0f)),
                idGameLocal.msec
            )
            Game_local.gameRenderWorld!!.DebugLine(
                colorGreen,
                org,
                org.plus(idAngles(0.0f, current_yaw, 0.0f).ToForward().times(48.0f)),
                idGameLocal.msec
            )
            Game_local.gameRenderWorld!!.DebugLine(
                colorYellow,
                org,
                org.plus(idAngles(0.0f, current_yaw + turnVel, 0.0f).ToForward().times(32.0f)),
                idGameLocal.msec
            )
        }
    }

    /*
     =====================
     idAI::TurnToward
     =====================
     */
    protected fun TurnToward(yaw: Float): Boolean {
        ideal_yaw = idMath.AngleNormalize180(yaw)
        return FacingIdeal()
    }

    /*
     =====================
     idAI::TurnToward
     =====================
     */
    protected fun TurnToward(pos: idVec3): Boolean {
        val dir = idVec3()
        val local_dir = idVec3()
        val lengthSqr: Float
        dir.set(pos.minus(physicsObj.GetOrigin()))
        physicsObj.GetGravityAxis().ProjectVector(dir, local_dir)
        local_dir.z = 0.0f
        lengthSqr = local_dir.LengthSqr()
        if (lengthSqr > Square(2.0f) || lengthSqr > Square(0.1f) && enemy.GetEntity() == null) {
            ideal_yaw = idMath.AngleNormalize180(local_dir.ToYaw())
        }
        return FacingIdeal()
    }

    // enemy management
    /*
     =====================
     idAI::ClearEnemy
     =====================
     */
    protected fun ClearEnemy() {
        if (move.moveCommand == moveCommand_t.MOVE_TO_ENEMY) {
            StopMove(moveStatus_t.MOVE_STATUS_DEST_NOT_FOUND)
        }
        enemyNode.Remove()
        enemy.oSet(null)
        AI_ENEMY_IN_FOV.underscore(false)
        AI_ENEMY_VISIBLE.underscore(false)
        AI_ENEMY_DEAD.underscore(true)
        SetChatSound()
    }

    /*
     =====================
     idAI::EnemyPositionValid
     =====================
     */
    protected fun EnemyPositionValid(): Boolean {
        val tr = trace_s()
        idVec3()
        idMat3()
        if (null == enemy.GetEntity()) {
            return false
        }
        if (AI_ENEMY_VISIBLE.underscore()!!) {
            return true
        }
        Game_local.gameLocal.clip.TracePoint(
            tr,
            GetEyePosition(),
            lastVisibleEnemyPos.plus(lastVisibleEnemyEyeOffset),
            Game_local.MASK_OPAQUE,
            this
        )
        // can't see the area yet, so don't know if he's there or not
        return tr.fraction < 1.0f
    }

    /*
     =====================
     idAI::SetEnemyPosition
     =====================
     */
    protected fun SetEnemyPosition() {
        val enemyEnt = enemy.GetEntity()
        var enemyAreaNum: Int
        val areaNum: Int
        var lastVisibleReachableEnemyAreaNum = 0
        val path = AAS.aasPath_s()
        val pos = idVec3()
        var onGround: Boolean
        if (null == enemyEnt) {
            return
        }
        lastVisibleReachableEnemyPos.set(lastReachableEnemyPos)
        lastVisibleEnemyEyeOffset.set(enemyEnt.EyeOffset())
        lastVisibleEnemyPos.set(enemyEnt.GetPhysics().GetOrigin())
        if (move.moveType == moveType_t.MOVETYPE_FLY) {
            pos.set(lastVisibleEnemyPos)
            onGround = true
        } else {
            onGround = enemyEnt.GetFloorPos(64.0f, pos)
            if (enemyEnt.OnLadder()) {
                onGround = false
            }
        }
        if (!onGround) {
            if (move.moveCommand == moveCommand_t.MOVE_TO_ENEMY) {
                AI_DEST_UNREACHABLE.underscore(true)
            }
            return
        }

        // when we don't have an AAS, we can't tell if an enemy is reachable or not,
        // so just assume that he is.
        if (aas == null) {
            lastVisibleReachableEnemyPos.set(lastVisibleEnemyPos)
            if (move.moveCommand == moveCommand_t.MOVE_TO_ENEMY) {
                AI_DEST_UNREACHABLE.underscore(false)
            }
            enemyAreaNum = 0
            //                areaNum = 0;
        } else {
            lastVisibleReachableEnemyAreaNum = move.toAreaNum
            enemyAreaNum = PointReachableAreaNum(lastVisibleEnemyPos, 1.0f)
            if (0 == enemyAreaNum) {
                enemyAreaNum = PointReachableAreaNum(lastReachableEnemyPos, 1.0f)
                pos.set(lastReachableEnemyPos)
            }
            if (0 == enemyAreaNum) {
                if (move.moveCommand == moveCommand_t.MOVE_TO_ENEMY) {
                    AI_DEST_UNREACHABLE.underscore(true)
                }
                //                    areaNum = 0;
            } else {
                val org = physicsObj.GetOrigin()
                areaNum = PointReachableAreaNum(org)
                if (PathToGoal(path, areaNum, org, enemyAreaNum, pos)) {
                    lastVisibleReachableEnemyPos.set(pos)
                    lastVisibleReachableEnemyAreaNum = enemyAreaNum
                    if (move.moveCommand == moveCommand_t.MOVE_TO_ENEMY) {
                        AI_DEST_UNREACHABLE.underscore(false)
                    }
                } else if (move.moveCommand == moveCommand_t.MOVE_TO_ENEMY) {
                    AI_DEST_UNREACHABLE.underscore(true)
                }
            }
        }
        if (move.moveCommand == moveCommand_t.MOVE_TO_ENEMY) {
            if (aas == null) {
                // keep the move destination up to date for wandering
                move.moveDest.set(lastVisibleReachableEnemyPos)
            } else if (enemyAreaNum != 0) {
                move.toAreaNum = lastVisibleReachableEnemyAreaNum
                move.moveDest.set(lastVisibleReachableEnemyPos)
            }
            if (move.moveType == moveType_t.MOVETYPE_FLY) {
                val path2 = predictedPath_s()
                val end = idVec3(move.moveDest)
                end.z += enemyEnt.EyeOffset().z + fly_offset
                PredictPath(this, aas, move.moveDest, end.minus(move.moveDest), 1000, 1000, SE_BLOCKED, path2)
                move.moveDest.set(path2.endPos)
                move.toAreaNum = PointReachableAreaNum(move.moveDest, 1.0f)
            }
        }
    }

    /*
     =====================
     idAI::UpdateEnemyPosition
     =====================
     */
    protected fun UpdateEnemyPosition() {
        val enemyEnt = enemy.GetEntity()
        val enemyAreaNum: Int
        val areaNum: Int
        val path = AAS.aasPath_s()
        val enemyPos = idVec3()
        var onGround: Boolean
        if (null == enemyEnt) {
            return
        }
        val org = physicsObj.GetOrigin()
        if (move.moveType == moveType_t.MOVETYPE_FLY) {
            enemyPos.set(enemyEnt.GetPhysics().GetOrigin())
            onGround = true
        } else {
            onGround = enemyEnt.GetFloorPos(64.0f, enemyPos)
            if (enemyEnt.OnLadder()) {
                onGround = false
            }
        }
        if (onGround) {
            // when we don't have an AAS, we can't tell if an enemy is reachable or not,
            // so just assume that he is.
            if (aas == null) {
//                    enemyAreaNum = 0;
                lastReachableEnemyPos.set(enemyPos)
            } else {
                enemyAreaNum = PointReachableAreaNum(enemyPos, 1.0f)
                if (enemyAreaNum != 0) {
                    areaNum = PointReachableAreaNum(org)
                    if (PathToGoal(path, areaNum, org, enemyAreaNum, enemyPos)) {
                        lastReachableEnemyPos.set(enemyPos)
                    }
                }
            }
        }
        AI_ENEMY_IN_FOV.underscore(false)
        AI_ENEMY_VISIBLE.underscore(false)
        if (CanSee(enemyEnt, false)) {
            AI_ENEMY_VISIBLE.underscore(true)
            if (CheckFOV(enemyEnt.GetPhysics().GetOrigin())) {
                AI_ENEMY_IN_FOV.underscore(true)
            }
            SetEnemyPosition()
        } else {
            // check if we heard any sounds in the last frame
            if (enemyEnt === Game_local.gameLocal.GetAlertEntity()) {
                val dist = enemyEnt.GetPhysics().GetOrigin().minus(org).LengthSqr()
                if (dist < Square(AI_HEARING_RANGE)) {
                    SetEnemyPosition()
                }
            }
        }
        if (SysCvar.ai_debugMove.GetBool()) {
            Game_local.gameRenderWorld!!.DebugBounds(
                colorLtGrey,
                enemyEnt.GetPhysics().GetBounds(),
                lastReachableEnemyPos,
                idGameLocal.msec
            )
            Game_local.gameRenderWorld!!.DebugBounds(
                colorWhite,
                enemyEnt.GetPhysics().GetBounds(),
                lastVisibleReachableEnemyPos,
                idGameLocal.msec
            )
        }
    }

    /*
     =====================
     idAI::SetEnemy
     =====================
     */
    protected fun SetEnemy(newEnemy: idActor?) {
        val enemyAreaNum = CInt()
        if (AI_DEAD.underscore()!!) {
            ClearEnemy()
            return
        }
        AI_ENEMY_DEAD.underscore(false)
        if (null == newEnemy) {
            ClearEnemy()
        } else if (enemy.GetEntity() !== newEnemy) {
            enemy.oSet(newEnemy)
            enemyNode.AddToEnd(newEnemy.enemyList)
            if (newEnemy.health <= 0) {
                EnemyDead()
                return
            }
            // let the monster know where the enemy is
            newEnemy.GetAASLocation(aas, lastReachableEnemyPos, enemyAreaNum)
            SetEnemyPosition()
            SetChatSound()
            lastReachableEnemyPos.set(lastVisibleEnemyPos)
            lastVisibleReachableEnemyPos.set(lastReachableEnemyPos)
            enemyAreaNum.integerValue = (PointReachableAreaNum(lastReachableEnemyPos, 1.0f))
            if (aas != null && enemyAreaNum.integerValue != 0) {
                aas!!.PushPointIntoAreaNum(enemyAreaNum.integerValue, lastReachableEnemyPos)
                lastVisibleReachableEnemyPos.set(lastReachableEnemyPos)
            }
        }
    }

    // attacks
    /*
     =====================
     idAI::CreateProjectileClipModel
     =====================
     */
    protected fun CreateProjectileClipModel() {
        if (projectileClipModel == null) {
            val projectileBounds = idBounds(vec3_origin)
            projectileBounds.ExpandSelf(projectileRadius)
            projectileClipModel = idClipModel(idTraceModel(projectileBounds))
        }
    }

    /*
     =====================
     idAI::CreateProjectile
     =====================
     */
    protected fun CreateProjectile(pos: idVec3, dir: idVec3): idProjectile? {
        val ent = arrayOfNulls<idEntity>(1)
        var clsname: String?
        if (null == projectile.GetEntity()) {
            Game_local.gameLocal.SpawnEntityDef(projectileDef!!, ent, false)
            if (ent[0] == null) {
                clsname = projectileDef!!.GetString("classname")
                idGameLocal.Error("Could not spawn entityDef '%s'", clsname)
            }
            if (ent[0] !is idProjectile) {
                clsname = ent[0]!!.GetClassname()
                idGameLocal.Error("'%s' is not an idProjectile", clsname)
            }
            projectile.oSet(ent[0] as idProjectile?)
        }
        projectile.GetEntity()!!.Create(this, pos, dir)
        return projectile.GetEntity()
    }

    /*
     =====================
     idAI::RemoveProjectile
     =====================
     */
    protected fun RemoveProjectile() {
        if (projectile.GetEntity() != null) {
            projectile.GetEntity()!!.PostEventMS(EV_Remove, 0)
            projectile.oSet(null)
        }
    }

    /*
     =====================
     idAI::LaunchProjectile
     =====================
     */
    protected fun LaunchProjectile(
        jointname: String?,
        target: idEntity?,
        clampToAttackCone: Boolean
    ): idProjectile? {
        val muzzle = idVec3()
        val dir = idVec3()
        val start = idVec3()
        val tr = trace_s()
        val projBounds: idBounds
        val distance = CFloat()
        val projClip: idClipModel
        val attack_accuracy: Float
        val attack_cone: Float
        val projectile_spread: Float
        val diff: Float
        var angle: Float
        var spin: Float
        val ang: idAngles?
        val num_projectiles: Int
        var i: Int
        val axis = idMat3()
        val tmp = idVec3()
        var lastProjectile: idProjectile
        if (null == projectileDef) {
            Game_local.gameLocal.Warning("%s (%s) doesn't have a projectile specified", name, GetEntityDefName())
            return null
        }
        attack_accuracy = spawnArgs.GetFloat("attack_accuracy", "7")
        attack_cone = spawnArgs.GetFloat("attack_cone", "70")
        projectile_spread = spawnArgs.GetFloat("projectile_spread", "0")
        num_projectiles = spawnArgs.GetInt("num_projectiles", "1")
        GetMuzzle(jointname, muzzle, axis)
        if (null == projectile.GetEntity()) {
            CreateProjectile(muzzle, axis[0])
        }
        lastProjectile = projectile.GetEntity()!!
        axis.set(
            if (target != null) {
                tmp.set(target.GetPhysics().GetAbsBounds().GetCenter().minus(muzzle))
                tmp.Normalize()
                tmp.ToMat3()
            } else {
                viewAxis
            }
        )

        // rotate it because the cone points up by default
        tmp.set(axis[2])
        axis[2] = axis[0]
        axis[0] = tmp.unaryMinus()

        // make sure the projectile starts inside the monster bounding box
        val ownerBounds = physicsObj.GetAbsBounds()
        projClip = lastProjectile.GetPhysics().GetClipModel()!!
        projBounds = projClip.GetBounds().Rotate(axis)

        // check if the owner bounds is bigger than the projectile bounds
        if (ownerBounds[1, 0] - ownerBounds[0, 0] > projBounds[1, 0] - projBounds[0, 0]
            && ownerBounds[1, 1] - ownerBounds[0, 1] > projBounds[1, 1] - projBounds[0, 1]
            && ownerBounds[1, 2] - ownerBounds[0, 2] > projBounds[1, 2] - projBounds[0, 2]
        ) {
            if (ownerBounds.minus(projBounds).RayIntersection(muzzle, viewAxis[0], distance)) {
                start.set(muzzle.plus(viewAxis[0].times(distance._val)))
            } else {
                start.set(ownerBounds.GetCenter())
            }
        } else {
            // projectile bounds bigger than the owner bounds, so just start it from the center
            start.set(ownerBounds.GetCenter())
        }
        Game_local.gameLocal.clip.Translation(
            tr,
            start,
            muzzle,
            projClip,
            axis,
            Game_local.MASK_SHOT_RENDERMODEL,
            this
        )
        muzzle.set(tr.endpos)

        // set aiming direction
        GetAimDir(muzzle, target, this, dir)
        ang = dir.ToAngles()

        // adjust his aim so it's not perfect.  uses sine based movement so the tracers appear less random in their spread.
        val t = MS2SEC((Game_local.gameLocal.time + entityNumber * 497).toFloat())
        ang.pitch += idMath.Sin16(t * 5.1f) * attack_accuracy
        ang.yaw += idMath.Sin16(t * 6.7f) * attack_accuracy
        if (clampToAttackCone) {
            // clamp the attack direction to be within monster's attack cone so he doesn't do
            // things like throw the missile backwards if you're behind him
            diff = idMath.AngleDelta(ang.yaw, current_yaw)
            if (diff > attack_cone) {
                ang.yaw = current_yaw + attack_cone
            } else if (diff < -attack_cone) {
                ang.yaw = current_yaw - attack_cone
            }
        }
        axis.set(ang.ToMat3())
        val spreadRad = DEG2RAD(projectile_spread)
        i = 0
        while (i < num_projectiles) {

            // spread the projectiles out
            angle = idMath.Sin(spreadRad * Game_local.gameLocal.random.RandomFloat())
            spin = DEG2RAD(360.0f) * Game_local.gameLocal.random.RandomFloat()
            dir.set(
                axis[0].plus(
                    axis[2].times(angle * idMath.Sin(spin))
                        .minus(axis[1].times(angle * idMath.Cos(spin)))
                )
            )
            dir.Normalize()

            // launch the projectile
            if (null == projectile.GetEntity()) {
                CreateProjectile(muzzle, dir)
            }
            lastProjectile = projectile.GetEntity()!!
            lastProjectile.Launch(muzzle, dir, vec3_origin)
            projectile.oSet(null)
            i++
        }
        TriggerWeaponEffects(muzzle)
        lastAttackTime = Game_local.gameLocal.time
        return lastProjectile
    }

    /*
         ================
         obj.DamageFeedback

         callback function for when another entity received damage from this entity.  damage can be adjusted and returned to the caller.

         FIXME: This gets called when we call idPlayer::CalcDamagePoints from obj.AttackMelee, which then checks for a saving throw,
         possibly forcing a miss.  This is harmless behavior ATM, but is not intuitive.
         ================
         */
    override fun DamageFeedback(victim: idEntity?, inflictor: idEntity?, damage: CInt) {
        if (victim == this && inflictor is idProjectile) {
            // monsters only get half damage from their own projectiles
            damage.integerValue = ((damage.integerValue + 1) / 2) // round up so we don't do 0 damage
        } else if (victim == enemy.GetEntity()) {
            AI_HIT_ENEMY.underscore(true)
        }
    }

    /*
         =====================
         obj.DirectDamage

         Causes direct damage to an entity

         kickDir is specified in the monster's coordinate system, and gives the direction
         that the view kick and knockback should go
         =====================
         */
    protected fun DirectDamage(meleeDefName: String, ent: idEntity) {
        val meleeDef: idDict?
        val p: String?
        val shader: idSoundShader?
        meleeDef = Game_local.gameLocal.FindEntityDefDict(meleeDefName, false)
        if (null == meleeDef) {
            idGameLocal.Error("Unknown damage def '%s' on '%s'", meleeDefName, name)
            return
        }
        if (!ent.fl.takedamage) {
            val shader2 = DeclManager.declManager.FindSound(meleeDef.GetString("snd_miss"))
            StartSoundShader(shader2, gameSoundChannel_t.SND_CHANNEL_DAMAGE, 0, false, CInt())
            return
        }

        //
        // do the damage
        //
        p = meleeDef.GetString("snd_hit")
        if (p.isNotEmpty()) {
            shader = DeclManager.declManager.FindSound(p)
            StartSoundShader(shader, gameSoundChannel_t.SND_CHANNEL_DAMAGE, 0, false, CInt())
        }
        val kickDir = idVec3()
        meleeDef.GetVector("kickDir", "0 0 0", kickDir)
        val globalKickDir = idVec3()
        globalKickDir.set(viewAxis.times(physicsObj.GetGravityAxis()).times(kickDir))
        ent.Damage(this, this, globalKickDir, meleeDefName, 1.0f, Model.INVALID_JOINT)

        // end the attack if we're a multiframe attack
        EndAttack()
    }

    protected fun DirectDamage(meleeDefName: idStr, ent: idEntity) {
        DirectDamage(meleeDefName.toString(), ent)
    }

    /*
     =====================
     idAI::TestMelee
     =====================
     */
    protected fun TestMelee(): Boolean {
        val trace = trace_s()
        val enemyEnt = enemy.GetEntity()
        if (null == enemyEnt || 0.0f == melee_range) {
            return false
        }

        //FIXME: make work with gravity vector
        val org = idVec3(physicsObj.GetOrigin())
        val myBounds = physicsObj.GetBounds()
        val bounds = idBounds()

        // expand the bounds out by our melee range
        // expand the bounds out by our melee range
        bounds[0][0] = -melee_range
        bounds[0][1] = -melee_range
        bounds[0][2] = myBounds[0][2] - 4.0f
        bounds[1][0] = melee_range
        bounds[1][1] = melee_range
        bounds[1][2] = myBounds[1][2] + 4.0f
        bounds.TranslateSelf(org)
        val enemyOrg = idVec3(enemyEnt.GetPhysics().GetOrigin())
        // FIX: Was direct reference alias; TranslateSelf would corrupt the physics bounds in-place
        val enemyBounds = idBounds(enemyEnt.GetPhysics().GetBounds())
        enemyBounds.TranslateSelf(enemyOrg)
        if (SysCvar.ai_debugMove.GetBool()) {
            Game_local.gameRenderWorld!!.DebugBounds(
                colorYellow,
                bounds,
                vec3_zero,
                idGameLocal.msec
            )
        }
        if (!bounds.IntersectsBounds(enemyBounds)) {
            return false
        }
        val start = idVec3(GetEyePosition())
        val end = idVec3(enemyEnt.GetEyePosition())
        Game_local.gameLocal.clip.TracePoint(trace, start, end, Game_local.MASK_SHOT_BOUNDINGBOX, this)
        return trace.fraction == 1.0f || Game_local.gameLocal.GetTraceEntity(trace) == enemyEnt
    }

    /*
         =====================
         obj.AttackMelee

         jointname allows the endpoint to be exactly specified in the model,
         as for the commando tentacle.  If not specified, it will be set to
         the facing direction + melee_range.

         kickDir is specified in the monster's coordinate system, and gives the direction
         that the view kick and knockback should go
         =====================
         */
    protected fun AttackMelee(meleeDefName: String): Boolean {
        val meleeDef: idDict?
        val enemyEnt = enemy.GetEntity()
        val p: String?
        val shader: idSoundShader?
        meleeDef = Game_local.gameLocal.FindEntityDefDict(meleeDefName, false)
        if (null == meleeDef) {
            idGameLocal.Error("Unknown melee '%s'", meleeDefName)
            return false
        }
        if (null == enemyEnt) {
            p = meleeDef.GetString("snd_miss")
            if (p.isNotEmpty()) {
                shader = DeclManager.declManager.FindSound(p)
                StartSoundShader(shader, gameSoundChannel_t.SND_CHANNEL_DAMAGE, 0, false, CInt())
            }
            return false
        }

        // check for the "saving throw" automatic melee miss on lethal blow
        // stupid place for this.
        var forceMiss = false
        if (enemyEnt is idPlayer && SysCvar.g_skill.GetInteger() < 2) {
            val damage = CInt()
            val armor = CInt()
            val player = enemyEnt
            player.CalcDamagePoints(this, this, meleeDef, 1.0f, Model.INVALID_JOINT, damage, armor)
            if (enemyEnt.health <= damage.integerValue) {
                var t = Game_local.gameLocal.time - player.lastSavingThrowTime
                if (t > Player.SAVING_THROW_TIME) {
                    player.lastSavingThrowTime = Game_local.gameLocal.time
                    t = 0
                }
                if (t < 1000) {
                    Game_local.gameLocal.Printf("Saving throw.\n")
                    forceMiss = true
                }
            }
        }

        // make sure the trace can actually hit the enemy
        if (forceMiss || !TestMelee()) {
            // missed
            p = meleeDef.GetString("snd_miss")
            if (p.isNotEmpty()) {
                shader = DeclManager.declManager.FindSound(p)
                StartSoundShader(shader, gameSoundChannel_t.SND_CHANNEL_DAMAGE, 0, false, CInt())
            }
            return false
        }

        //
        // do the damage
        //
        p = meleeDef.GetString("snd_hit")
        if (p.isNotEmpty()) {
            shader = DeclManager.declManager.FindSound(p)
            StartSoundShader(shader, gameSoundChannel_t.SND_CHANNEL_DAMAGE, 0, false, CInt())
        }
        val kickDir = idVec3()
        meleeDef.GetVector("kickDir", "0 0 0", kickDir)
        val globalKickDir = idVec3()
        globalKickDir.set(viewAxis.times(physicsObj.GetGravityAxis()).times(kickDir))
        enemyEnt.Damage(this, this, globalKickDir, meleeDefName, 1.0f, Model.INVALID_JOINT)
        lastAttackTime = Game_local.gameLocal.time
        return true
    }

    /*
     =====================
     idAI::BeginAttack
     =====================
     */
    protected fun BeginAttack(name: String?) {
        attack.set(name)
        lastAttackTime = Game_local.gameLocal.time
    }

    /*
     =====================
     idAI::EndAttack
     =====================
     */
    protected fun EndAttack() {
        attack.set("")
    }

    /*
     ================
     idAI::PushWithAF
     ================
     */
    protected fun PushWithAF() {
        var i: Int
        var j: Int
        val touchList = Array<afTouch_s>(Game_local.MAX_GENTITIES) { afTouch_s() }
        val pushed_ents = arrayOfNulls<idEntity?>(Game_local.MAX_GENTITIES)
        var ent: idEntity?
        val vel = idVec3()
        var num_pushed: Int
        num_pushed = 0
        af.ChangePose(this, Game_local.gameLocal.time)
        val num = af.EntitiesTouchingAF(touchList)
        i = 0
        while (i < num) {
            if (touchList[i].touchedEnt is idProjectile) {
                // skip projectiles
                i++
                continue
            }

            // make sure we havent pushed this entity already.  this avoids causing double damage
            j = 0
            while (j < num_pushed) {
                if (pushed_ents[j] === touchList[i].touchedEnt) {
                    break
                }
                j++
            }
            if (j >= num_pushed) {
                ent = touchList[i].touchedEnt
                pushed_ents[num_pushed++] = ent
                vel.set(
                    ent.GetPhysics().GetAbsBounds().GetCenter().minus(touchList[i].touchedByBody.GetWorldOrigin())
                )
                vel.Normalize()
                if (attack.Length() != 0 && ent is idActor) {
                    ent.Damage(this, this, vel, attack.toString(), 1.0f, Model.INVALID_JOINT)
                } else {
                    ent.GetPhysics().SetLinearVelocity(vel.times(100.0f), touchList[i].touchedClipModel.GetId())
                }
            }
            i++
        }
    }

    // special effects
    /*
     ================
     idAI::GetMuzzle
     ================
     */
    protected fun GetMuzzle(jointname: String?, muzzle: idVec3, axis: idMat3) {
        val   /*jointHandle_t*/joint: Int
        if (jointname.isNullOrEmpty()) {
            muzzle.set(
                physicsObj.GetOrigin().plus(viewAxis[0].times(physicsObj.GetGravityAxis().times(14.0f)))
            )
            muzzle.minusAssign(physicsObj.GetGravityNormal().times(physicsObj.GetBounds()[1].z * 0.5f))
        } else {
            joint = animator.GetJointHandle(jointname)
            if (joint == Model.INVALID_JOINT) {
                idGameLocal.Error("Unknown joint '%s' on %s", jointname, GetEntityDefName())
            }
            GetJointWorldTransform(joint, Game_local.gameLocal.time, muzzle, axis)
        }
    }

    /*
     ===================
     idAI::InitMuzzleFlash
     ===================
     */
    protected fun InitMuzzleFlash() {
        val shader = idStr()
        val flashColor = idVec3()
        spawnArgs.GetString("mtr_flashShader", "muzzleflash", shader)
        spawnArgs.GetVector("flashColor", "0 0 0", flashColor)
        val flashRadius = spawnArgs.GetFloat("flashRadius")
        flashTime = SEC2MS(spawnArgs.GetFloat("flashTime", "0.25f")).toInt()

        worldMuzzleFlash = renderLight_s()
        worldMuzzleFlash.pointLight._val = true
        worldMuzzleFlash.shader = DeclManager.declManager.FindMaterial(shader, false)
        worldMuzzleFlash.shaderParms[RenderWorld.SHADERPARM_RED] = flashColor[0]
        worldMuzzleFlash.shaderParms[RenderWorld.SHADERPARM_GREEN] = flashColor[1]
        worldMuzzleFlash.shaderParms[RenderWorld.SHADERPARM_BLUE] = flashColor[2]
        worldMuzzleFlash.shaderParms[RenderWorld.SHADERPARM_ALPHA] = 1.0f
        worldMuzzleFlash.shaderParms[RenderWorld.SHADERPARM_TIMESCALE] = 1.0f
        worldMuzzleFlash.lightRadius[0] = flashRadius
        worldMuzzleFlash.lightRadius[1] = flashRadius
        worldMuzzleFlash.lightRadius[2] = flashRadius
        worldMuzzleFlashHandle = -1
    }

    /*
     ================
     idAI::TriggerWeaponEffects
     ================
     */
    protected fun TriggerWeaponEffects(muzzle: idVec3) {
        val org = idVec3()
        val axis = idMat3()
        if (!SysCvar.g_muzzleFlash.GetBool()) {
            return
        }

        // muzzle flash
        // offset the shader parms so muzzle flashes show up
        renderEntity!!.shaderParms[RenderWorld.SHADERPARM_TIMEOFFSET] =
            -MS2SEC(Game_local.gameLocal.time.toFloat())
        renderEntity!!.shaderParms[RenderWorld.SHADERPARM_DIVERSITY] = Game_local.gameLocal.random.CRandomFloat()
        if (flashJointWorld != Model.INVALID_JOINT) {
            GetJointWorldTransform(flashJointWorld, Game_local.gameLocal.time, org, axis)
            if (worldMuzzleFlash.lightRadius.x > 0.0f) {
                worldMuzzleFlash.axis.set(axis)
                worldMuzzleFlash.shaderParms[RenderWorld.SHADERPARM_TIMEOFFSET] =
                    -MS2SEC(Game_local.gameLocal.time.toFloat())
                if (worldMuzzleFlashHandle != -1) {
                    Game_local.gameRenderWorld!!.UpdateLightDef(worldMuzzleFlashHandle, worldMuzzleFlash)
                } else {
                    worldMuzzleFlashHandle = Game_local.gameRenderWorld!!.AddLightDef(worldMuzzleFlash)
                }
                muzzleFlashEnd = Game_local.gameLocal.time + flashTime
                UpdateVisuals()
            }
        }
    }

    /*
     ================
     idAI::UpdateMuzzleFlash
     ================
     */
    protected fun UpdateMuzzleFlash() {
        if (worldMuzzleFlashHandle != -1) {
            if (Game_local.gameLocal.time >= muzzleFlashEnd) {
                Game_local.gameRenderWorld!!.FreeLightDef(worldMuzzleFlashHandle)
                worldMuzzleFlashHandle = -1
            } else {
                val muzzle = idVec3()
                animator.GetJointTransform(
                    flashJointWorld,
                    Game_local.gameLocal.time,
                    muzzle,
                    worldMuzzleFlash.axis
                )
                animator.GetJointTransform(
                    flashJointWorld,
                    Game_local.gameLocal.time,
                    muzzle,
                    worldMuzzleFlash.axis
                )
                muzzle.set(physicsObj.GetOrigin() + (muzzle + modelOffset) * viewAxis * physicsObj.GetGravityAxis())
                worldMuzzleFlash.origin.set(muzzle)
                Game_local.gameRenderWorld!!.UpdateLightDef(worldMuzzleFlashHandle, worldMuzzleFlash)
            }
        }
    }

    /*
     ================
     idAI::UpdateAnimationControllers
     ================
     */
    override fun UpdateAnimationControllers(): Boolean {
        idVec3()
        val focusPos = idVec3()
        idQuat()
        val left = idVec3()
        val dir = idVec3()
        val orientationJointPos = idVec3()
        val localDir = idVec3()
        val newLookAng = idAngles()
        var diff: idAngles?
        idMat3()
        val axis = idMat3()
        val orientationJointAxis = idMat3()
        val headEnt = head?.GetEntity()
        val eyepos = idVec3()
        idVec3()
        var i: Int
        val jointAng = idAngles()
        val orientationJointYaw: Float
        if (AI_DEAD.underscore()!!) {
            return super.UpdateAnimationControllers()
        }
        if (orientationJoint == Model.INVALID_JOINT) {
            orientationJointAxis.set(viewAxis)
            orientationJointPos.set(physicsObj.GetOrigin())
            orientationJointYaw = current_yaw
        } else {
            GetJointWorldTransform(
                orientationJoint,
                Game_local.gameLocal.time,
                orientationJointPos,
                orientationJointAxis
            )
            orientationJointYaw = orientationJointAxis[2].ToYaw()
            orientationJointAxis.set(idAngles(0.0f, orientationJointYaw, 0.0f).ToMat3())
        }
        if (focusJoint != Model.INVALID_JOINT) {
            headEnt?.GetJointWorldTransform(focusJoint, Game_local.gameLocal.time, eyepos, axis)
                ?: GetJointWorldTransform(focusJoint, Game_local.gameLocal.time, eyepos, axis)
            eyeOffset.z = eyepos.z - physicsObj.GetOrigin().z
            if (SysCvar.ai_debugMove.GetBool()) {
                Game_local.gameRenderWorld!!.DebugLine(
                    colorRed,
                    eyepos,
                    eyepos.plus(orientationJointAxis[0].times(32.0f)),
                    idGameLocal.msec
                )
            }
        } else {
            eyepos.set(GetEyePosition())
        }
        if (headEnt != null) {
            CopyJointsFromBodyToHead()
        }

        // Update the IK after we've gotten all the joint positions we need, but before we set any joint positions.
        // Getting the joint positions causes the joints to be updated.  The IK gets joint positions itself (which
        // are already up to date because of getting the joints in this function) and then sets their positions, which
        // forces the heirarchy to be updated again next time we get a joint or present the model.  If IK is enabled,
        // or if we have a seperate head, we end up transforming the joints twice per frame.  Characters with no
        // head entity and no ik will only transform their joints once.  Set g_debuganim to the current entity number
        // in order to see how many times an entity transforms the joints per frame.
        super.UpdateAnimationControllers()
        val focusEnt = focusEntity.GetEntity()
        if (!allowJointMod || !allowEyeFocus || Game_local.gameLocal.time >= focusTime) {
            focusPos.set(GetEyePosition().plus(orientationJointAxis[0].times(512.0f)))
        } else if (focusEnt == null) {
            // keep looking at last position until focusTime is up
            focusPos.set(currentFocusPos)
        } else if (focusEnt == enemy.GetEntity()) {
            focusPos.set(
                lastVisibleEnemyPos.plus(lastVisibleEnemyEyeOffset)
                    .minus(enemy.GetEntity()!!.GetPhysics().GetGravityNormal().times(eyeVerticalOffset))
            )
        } else if (focusEnt is idActor) {
            focusPos.set(
                (focusEnt as idActor).GetEyePosition()
                    .minus(focusEnt.GetPhysics().GetGravityNormal().times(eyeVerticalOffset))
            )
        } else {
            focusPos.set(focusEnt.GetPhysics().GetOrigin())
        }
        // FIX: Was currentFocusPos.plus(focusPos.minus(currentFocusPos)).times(eyeFocusRate)
        // which chains left-to-right: (currentFocusPos + (focusPos - currentFocusPos)) * rate = focusPos * rate
        // C++ original: currentFocusPos + (focusPos - currentFocusPos) * eyeFocusRate (standard lerp)
        currentFocusPos.set(currentFocusPos.plus(focusPos.minus(currentFocusPos).times(eyeFocusRate)))
        // determine yaw from origin instead of from focus joint since joint may be offset, which can cause us to bounce between two angles
        dir.set(focusPos.minus(orientationJointPos))
        newLookAng.yaw = idMath.AngleNormalize180(dir.ToYaw() - orientationJointYaw)
        newLookAng.roll = 0.0f
        newLookAng.pitch = 0.0f
        // #if 0
        // gameRenderWorld!!.DebugLine( colorRed, orientationJointPos, focusPos, gameLocal.msec );
        // gameRenderWorld!!.DebugLine( colorYellow, orientationJointPos, orientationJointPos + orientationJointAxis[ 0 ] * 32.0f, gameLocal.msec );
        // gameRenderWorld!!.DebugLine( colorGreen, orientationJointPos, orientationJointPos + newLookAng.ToForward() * 48.0f, gameLocal.msec );
// #endif
        // determine pitch from joint position
        dir.set(focusPos.minus(eyepos))
        dir.NormalizeFast()
        orientationJointAxis.ProjectVector(dir, localDir)
        newLookAng.pitch = -idMath.AngleNormalize180(localDir.ToPitch())
        newLookAng.roll = 0.0f
        diff = newLookAng.minus(lookAng)
        if (eyeAng != diff) {
            eyeAng.set(diff)
            eyeAng.Clamp(eyeMin, eyeMax)
            val angDelta = diff.minus(eyeAng)
            alignHeadTime = if (!angDelta.Compare(ang_zero, 0.1f)) {
                Game_local.gameLocal.time
            } else {
                (Game_local.gameLocal.time + (0.5f + 0.5f * Game_local.gameLocal.random.RandomFloat()) * focusAlignTime).toInt()
            }
        }
        if (abs(newLookAng.yaw) < 0.1f) {
            alignHeadTime = Game_local.gameLocal.time
        }
        if (Game_local.gameLocal.time >= alignHeadTime || Game_local.gameLocal.time < forceAlignHeadTime) {
            alignHeadTime =
                (Game_local.gameLocal.time + (0.5f + 0.5f * Game_local.gameLocal.random.RandomFloat()) * focusAlignTime).toInt()
            destLookAng.set(newLookAng)
            destLookAng.Clamp(lookMin, lookMax)
        }
        diff = destLookAng.minus(lookAng)
        if (lookMin.pitch == -180.0f && lookMax.pitch == 180.0f) {
            if (diff.pitch > 180.0f || diff.pitch <= -180.0f) {
                diff.pitch = 360.0f - diff.pitch
            }
        }
        if (lookMin.yaw == -180.0f && lookMax.yaw == 180.0f) {
            if (diff.yaw > 180.0f) {
                diff.yaw -= 360.0f
            } else if (diff.yaw <= -180.0f) {
                diff.yaw += 360.0f
            }
        }
        lookAng.set(lookAng.plus(diff.times(headFocusRate)))
        lookAng.Normalize180()
        jointAng.roll = 0.0f
        i = 0
        while (i < lookJoints.Num()) {
            jointAng.pitch = lookAng.pitch * lookJointAngles[i].pitch
            jointAng.yaw = lookAng.yaw * lookJointAngles[i].yaw
            animator.SetJointAxis(lookJoints[i], jointModTransform_t.JOINTMOD_WORLD, jointAng.ToMat3())
            i++
        }
        if (move.moveType == moveType_t.MOVETYPE_FLY) {
            // lean into turns
            AdjustFlyingAngles()
        }
        if (headEnt != null) {
            val headAnimator = headEnt.GetAnimator()
            if (allowEyeFocus) {
                val eyeAxis = lookAng.plus(eyeAng).ToMat3()
                val headTranspose = headEnt.GetPhysics().GetAxis().Transpose()
                axis.set(eyeAxis.times(orientationJointAxis))
                left.set(axis[1].times(eyeHorizontalOffset))
                eyepos.minusAssign(headEnt.GetPhysics().GetOrigin())
                headAnimator.SetJointPos(
                    leftEyeJoint,
                    jointModTransform_t.JOINTMOD_WORLD_OVERRIDE,
                    eyepos.plus(axis[0].times(64.0f).plus(left).times(headTranspose))
                )
                headAnimator.SetJointPos(
                    rightEyeJoint,
                    jointModTransform_t.JOINTMOD_WORLD_OVERRIDE,
                    eyepos.plus(axis[0].times(64.0f).minus(left).times(headTranspose))
                )
            } else {
                headAnimator.ClearJoint(leftEyeJoint)
                headAnimator.ClearJoint(rightEyeJoint)
            }
        } else {
            if (allowEyeFocus) {
                val eyeAxis = lookAng.plus(eyeAng).ToMat3()
                axis.set(eyeAxis.times(orientationJointAxis))
                left.set(axis[1].times(eyeHorizontalOffset))
                eyepos.plusAssign(axis[0].times(64.0f).minus(physicsObj.GetOrigin()))
                animator.SetJointPos(leftEyeJoint, jointModTransform_t.JOINTMOD_WORLD_OVERRIDE, eyepos.plus(left))
                animator.SetJointPos(
                    rightEyeJoint,
                    jointModTransform_t.JOINTMOD_WORLD_OVERRIDE,
                    eyepos.minus(left)
                )
            } else {
                animator.ClearJoint(leftEyeJoint)
                animator.ClearJoint(rightEyeJoint)
            }
        }
        return true
    }

    /*
     =====================
     idAI::UpdateParticles
     =====================
     */
    protected fun UpdateParticles() {
        if ((thinkFlags and TH_UPDATEPARTICLES) != 0 && !IsHidden()) {
            val realVector = idVec3()
            val realAxis = idMat3()
            var particlesAlive = 0
            for (i in 0 until particles.Num()) {
                if (particles[i].particle != null && particles[i].time != 0) {
                    particlesAlive++
                    if (af.IsActive()) {
                        realAxis.set(idMat3.getMat3_identity())
                        realVector.set(GetPhysics().GetOrigin())
                    } else {
                        animator.GetJointTransform(
                            particles[i].joint,
                            Game_local.gameLocal.time,
                            realVector,
                            realAxis
                        )
                        realAxis.timesAssign(renderEntity!!.axis)
                        realVector.set(
                            physicsObj.GetOrigin().plus(
                                realVector.plus(modelOffset)
                                    .times(viewAxis.times(physicsObj.GetGravityAxis()))
                            )
                        )
                    }
                    if (!Game_local.gameLocal.smokeParticles!!.EmitSmoke(
                            particles[i].particle,
                            particles[i].time,
                            Game_local.gameLocal.random.CRandomFloat(),
                            realVector,
                            realAxis
                        )
                    ) {
                        if (restartParticles) {
                            particles[i].time = Game_local.gameLocal.time
                        } else {
                            particles[i].time = 0
                            particlesAlive--
                        }
                    }
                }
            }
            if (particlesAlive == 0) {
                BecomeInactive(TH_UPDATEPARTICLES)
            }
        }
    }

    /*
     =====================
     idAI::TriggerParticles
     =====================
     */
    protected fun TriggerParticles(jointName: String) {
        val   /*jointHandle_t*/jointNum: Int
        jointNum = animator.GetJointHandle(jointName)
        for (i in 0 until particles.Num()) {
            if (particles[i].joint == jointNum) {
                particles[i].time = Game_local.gameLocal.time
                BecomeActive(TH_UPDATEPARTICLES)
            }
        }
    }

    // AI script state management
    /*
     =====================
     idAI::LinkScriptVariables
     =====================
     */
    protected fun LinkScriptVariables() {
        AI_TALK.LinkTo(scriptObject, "AI_TALK")
        AI_DAMAGE.LinkTo(scriptObject, "AI_DAMAGE")
        AI_PAIN.LinkTo(scriptObject, "AI_PAIN")
        AI_SPECIAL_DAMAGE.LinkTo(scriptObject, "AI_SPECIAL_DAMAGE")
        AI_DEAD.LinkTo(scriptObject, "AI_DEAD")
        AI_ENEMY_VISIBLE.LinkTo(scriptObject, "AI_ENEMY_VISIBLE")
        AI_ENEMY_IN_FOV.LinkTo(scriptObject, "AI_ENEMY_IN_FOV")
        AI_ENEMY_DEAD.LinkTo(scriptObject, "AI_ENEMY_DEAD")
        AI_MOVE_DONE.LinkTo(scriptObject, "AI_MOVE_DONE")
        AI_ONGROUND.LinkTo(scriptObject, "AI_ONGROUND")
        AI_ACTIVATED.LinkTo(scriptObject, "AI_ACTIVATED")
        AI_FORWARD.LinkTo(scriptObject, "AI_FORWARD")
        AI_JUMP.LinkTo(scriptObject, "AI_JUMP")
        AI_BLOCKED.LinkTo(scriptObject, "AI_BLOCKED")
        AI_DEST_UNREACHABLE.LinkTo(scriptObject, "AI_DEST_UNREACHABLE")
        AI_HIT_ENEMY.LinkTo(scriptObject, "AI_HIT_ENEMY")
        AI_OBSTACLE_IN_PATH.LinkTo(scriptObject, "AI_OBSTACLE_IN_PATH")
        AI_PUSHED.LinkTo(scriptObject, "AI_PUSHED")
    }

    /*
     =====================
     idAI::UpdateAIScript
     =====================
     */
    protected fun UpdateAIScript() {
        UpdateScript()

        // clear the hit enemy flag so we catch the next time we hit someone
        AI_HIT_ENEMY.underscore(false)
        if (allowHiddenMovement || !IsHidden()) {
            // update the animstate if we're not hidden
            UpdateAnimState()
        }
    }

    //
    // ai/ai_events.cpp
    //
    /*
     ================
     idAI::Event_Activate
     ================
     */
    protected fun Event_Activate(activator: idEventArg<idEntity?>) {
        Activate(activator.value)
    }

    /*
     ================
     idAI::Event_Touch
     ================
     */
    protected fun Event_Touch(_other: idEventArg<idEntity>, trace: idEventArg<trace_s?>?) {
        val other = _other.value
        if (null == enemy.GetEntity() && !other.fl.notarget && (ReactionTo(other) and ATTACK_ON_ACTIVATE) != 0) {
            Activate(other)
        }
        AI_PUSHED.underscore(true)
    }

    /*
     ================
     idAI::Event_FindEnemy
     ================
     */
    protected fun Event_FindEnemy(useFOV: idEventArg<Int>) {
        var i: Int
        var ent: idEntity?
        var actor: idActor?
        if (Game_local.gameLocal.InPlayerPVS(this)) {
            i = 0
            while (i < Game_local.gameLocal.numClients) {
                ent = Game_local.gameLocal.entities[i]
                if (null == ent || ent !is idActor) {
                    i++
                    continue
                }
                actor = ent
                if (actor.health <= 0 || (ReactionTo(actor) and ATTACK_ON_SIGHT) == 0) {
                    i++
                    continue
                }
                if (CanSee(actor, useFOV.value != 0)) {
                    idThread.ReturnEntity(actor)
                    return
                }
                i++
            }
        }
        idThread.ReturnEntity(null)
    }

    /*
     ================
     idAI::Event_FindEnemyAI
     ================
     */
    protected fun Event_FindEnemyAI(useFOV: idEventArg<Int>) {
        var ent: idEntity?
        var actor: idActor?
        var bestEnemy: idActor?
        var bestDist: Float
        var dist: Float
        val delta = idVec3()
        val pvs: pvsHandle_t?
        pvs = Game_local.gameLocal.pvs.SetupCurrentPVS(GetPVSAreas(), GetNumPVSAreas())
        bestDist = idMath.INFINITY
        bestEnemy = null
        ent = Game_local.gameLocal.activeEntities.Next()
        while (ent != null) {
            if (ent.fl.hidden || ent.fl.isDormant || ent !is idActor) {
                ent = ent.activeNode.Next()
                continue
            }
            actor = ent
            if (actor.health <= 0 || 0 == ReactionTo(actor) and ATTACK_ON_SIGHT) {
                ent = ent.activeNode.Next()
                continue
            }
            if (!Game_local.gameLocal.pvs.InCurrentPVS(pvs, actor.GetPVSAreas(), actor.GetNumPVSAreas())) {
                ent = ent.activeNode.Next()
                continue
            }
            delta.set(physicsObj.GetOrigin().minus(actor.GetPhysics().GetOrigin()))
            dist = delta.LengthSqr()
            if (dist < bestDist && CanSee(actor, useFOV.value != 0)) {
                bestDist = dist
                bestEnemy = actor
            }
            ent = ent.activeNode.Next()
        }
        Game_local.gameLocal.pvs.FreeCurrentPVS(pvs)
        idThread.ReturnEntity(bestEnemy)
    }

    /*
     ================
     idAI::Event_FindEnemyInCombatNodes
     ================
     */
    protected fun Event_FindEnemyInCombatNodes() {
        var i: Int
        var j: Int
        var node: idCombatNode?
        var ent: idEntity?
        var targetEnt: idEntity?
        var actor: idActor?
        if (!Game_local.gameLocal.InPlayerPVS(this)) {
            // don't locate the player when we're not in his PVS
            idThread.ReturnEntity(null)
            return
        }
        i = 0
        while (i < Game_local.gameLocal.numClients) {
            ent = Game_local.gameLocal.entities[i]
            if (null == ent || ent !is idActor) {
                i++
                continue
            }
            actor = ent
            if (actor.health <= 0 || (ReactionTo(actor) and ATTACK_ON_SIGHT) == 0) {
                i++
                continue
            }
            j = 0
            while (j < targets.Num()) {
                targetEnt = targets[j].GetEntity()
                if (null == targetEnt || targetEnt !is idCombatNode) {
                    j++
                    continue
                }
                node = targetEnt
                if (!node.IsDisabled() && node.EntityInView(actor, actor.GetPhysics().GetOrigin())) {
                    idThread.ReturnEntity(actor)
                    return
                }
                j++
            }
            i++
        }
        idThread.ReturnEntity(null)
    }

    /*
     ================
     idAI::Event_ClosestReachableEnemyOfEntity
     ================
     */
    protected fun Event_ClosestReachableEnemyOfEntity(_team_mate: idEventArg<idEntity?>) {
        val team_mate = _team_mate.value
        val actor: idActor?
        var ent: idActor?
        var bestEnt: idActor?
        var bestDistSquared: Float
        var distSquared: Float
        val delta = idVec3()
        val areaNum: Int
        var enemyAreaNum: Int
        val path = AAS.aasPath_s()
        if (team_mate !is idActor) {
            idGameLocal.Error("Entity '%s' is not an AI character or player", team_mate!!.GetName())
        }
        actor = team_mate as idActor
        val origin = physicsObj.GetOrigin()
        areaNum = PointReachableAreaNum(origin)
        bestDistSquared = idMath.INFINITY
        bestEnt = null
        ent = actor.enemyList.Next()
        while (ent != null) {
            if (ent.fl.hidden) {
                ent = ent.enemyNode.Next()
                continue
            }
            delta.set(ent.GetPhysics().GetOrigin().minus(origin))
            distSquared = delta.LengthSqr()
            if (distSquared < bestDistSquared) {
                val enemyPos = ent.GetPhysics().GetOrigin()
                enemyAreaNum = PointReachableAreaNum(enemyPos)
                if (areaNum != 0 && PathToGoal(path, areaNum, origin, enemyAreaNum, enemyPos)) {
                    bestEnt = ent
                    bestDistSquared = distSquared
                }
            }
            ent = ent.enemyNode.Next()
        }
        idThread.ReturnEntity(bestEnt)
    }

    /*
     ================
     idAI::Event_HeardSound
     ================
     */
    protected fun Event_HeardSound(ignore_team: idEventArg<Int>) {
        // check if we heard any sounds in the last frame
        val actor = Game_local.gameLocal.GetAlertEntity()
        if (actor != null && (0 == ignore_team.value || (ReactionTo(actor) and ATTACK_ON_SIGHT) != 0) && Game_local.gameLocal.InPlayerPVS(
                this
            )
        ) {
            val pos = idVec3(actor.GetPhysics().GetOrigin())
            val org = idVec3(physicsObj.GetOrigin())
            val dist = pos.minus(org).LengthSqr()
            if (dist < Square(AI_HEARING_RANGE)) {
                idThread.ReturnEntity(actor)
                return
            }
        }
        idThread.ReturnEntity(null)
    }

    /*
     ================
     idAI::Event_SetEnemy
     ================
     */
    protected fun Event_SetEnemy(_ent: idEventArg<idEntity?>) {
        val ent = _ent.value
        if (null == ent) {
            ClearEnemy()
        } else if (ent !is idActor) {
            idGameLocal.Error("'%s' is not an idActor (player or ai controlled character)", ent.name)
        } else {
            SetEnemy(ent as idActor)
        }
    }

    /*
     ================
     idAI::Event_ClearEnemy
     ================
     */
    protected fun Event_ClearEnemy() {
        ClearEnemy()
    }

    /*
     ================
     idAI::Event_MuzzleFlash
     ================
     */
    protected fun Event_MuzzleFlash(jointname: idEventArg<String?>) {
        val muzzle = idVec3()
        val axis = idMat3()
        GetMuzzle(jointname.value, muzzle, axis)
        TriggerWeaponEffects(muzzle)
    }

    /*
     ================
     idAI::Event_CreateMissile
     ================
     */
    protected fun Event_CreateMissile(_jointname: idEventArg<String?>) {
        val jointname = _jointname.value
        val muzzle = idVec3()
        val axis = idMat3()
        if (null == projectileDef) {
            Game_local.gameLocal.Warning("%s (%s) doesn't have a projectile specified", name, GetEntityDefName())
            // FIX: C++ uses `return idThread::ReturnEntity(NULL)` to exit early
            return idThread.ReturnEntity(null)
        }
        GetMuzzle(jointname, muzzle, axis)
        CreateProjectile(muzzle, viewAxis[0].times(physicsObj.GetGravityAxis()))
        if (projectile.GetEntity() != null) {
            if (jointname.isNullOrEmpty()) {
                projectile.GetEntity()!!.Bind(this, true)
            } else {
                projectile.GetEntity()!!.BindToJoint(this, jointname, true)
            }
        }
        idThread.ReturnEntity(projectile.GetEntity())
    }

    /*
     ================
     idAI::Event_AttackMissile
     ================
     */
    protected fun Event_AttackMissile(jointname: idEventArg<String?>) {
        val proj: idProjectile?
        proj = LaunchProjectile(jointname.value, enemy.GetEntity(), true)
        idThread.ReturnEntity(proj)
    }

    /*
     ================
     idAI::Event_FireMissileAtTarget
     ================
     */
    protected fun Event_FireMissileAtTarget(jointname: idEventArg<String>, targetname: idEventArg<String>) {
        val aent: idEntity?
        val proj: idProjectile?
        aent = Game_local.gameLocal.FindEntity(targetname.value)
        if (null == aent) {
            Game_local.gameLocal.Warning("Entity '%s' not found for 'fireMissileAtTarget'", targetname.value)
        }
        proj = LaunchProjectile(jointname.value, aent, false)
        idThread.ReturnEntity(proj)
    }

    /*
     ================
     idAI::Event_LaunchMissile
     ================
     */
    protected fun Event_LaunchMissile(_muzzle: idEventArg<idVec3>, _ang: idEventArg<idAngles>) {
        val muzzle = idVec3(_muzzle.value)
        val ang = _ang.value
        val start = idVec3()
        val tr = trace_s()
        val projBounds: idBounds
        val projClip: idClipModel?
        val axis: idMat3
        val distance = CFloat()
        if (null == projectileDef) {
            Game_local.gameLocal.Warning("%s (%s) doesn't have a projectile specified", name, GetEntityDefName())
            idThread.ReturnEntity(null)
            return
        }
        axis = ang.ToMat3()
        if (null == projectile.GetEntity()) {
            CreateProjectile(muzzle, axis[0])
        }

        // make sure the projectile starts inside the monster bounding box
        val ownerBounds = physicsObj.GetAbsBounds()
        projClip = projectile.GetEntity()!!.GetPhysics().GetClipModel()!!
        projBounds = projClip.GetBounds().Rotate(projClip.GetAxis())

        // check if the owner bounds is bigger than the projectile bounds
        if (ownerBounds[1, 0] - ownerBounds[0, 0] > projBounds[1, 0] - projBounds[0, 0]
            && ownerBounds[1, 1] - ownerBounds[0, 1] > projBounds[1, 1] - projBounds[0, 1]
            && ownerBounds[1, 2] - ownerBounds[0, 2] > projBounds[1, 2] - projBounds[0, 2]
        ) {
            if (ownerBounds.minus(projBounds).RayIntersection(muzzle, viewAxis[0], distance)) {
                start.set(muzzle.plus(viewAxis[0].times(distance._val)))
            } else {
                start.set(ownerBounds.GetCenter())
            }
        } else {
            // projectile bounds bigger than the owner bounds, so just start it from the center
            start.set(ownerBounds.GetCenter())
        }
        Game_local.gameLocal.clip.Translation(
            tr,
            start,
            muzzle,
            projClip,
            projClip.GetAxis(),
            Game_local.MASK_SHOT_RENDERMODEL,
            this
        )

        // launch the projectile
        idThread.ReturnEntity(projectile.GetEntity())
        projectile.GetEntity()!!.Launch(tr.endpos, axis[0], vec3_origin)
        projectile.oSet(null)
        TriggerWeaponEffects(tr.endpos)
        lastAttackTime = Game_local.gameLocal.time
    }

    /*
     ================
     idAI::Event_AttackMelee
     ================
     */
    protected fun Event_AttackMelee(meleeDefName: idEventArg<String>) {
        val hit = if (AttackMelee(meleeDefName.value)) 1 else 0
        idThread.ReturnInt(hit)
    }

    /*
     ================
     idAI::Event_DirectDamage
     ================
     */
    protected fun Event_DirectDamage(damageTarget: idEventArg<idEntity>, damageDefName: idEventArg<String>) {
        DirectDamage(damageDefName.value, damageTarget.value)
    }

    /*
     ================
     idAI::Event_RadiusDamageFromJoint
     ================
     */
    protected fun Event_RadiusDamageFromJoint(
        jointname: idEventArg<String>,
        damageDefName: idEventArg<String>
    ) {
        val   /*jointHandle_t*/joint: Int
        val org = idVec3()
        val axis = idMat3()
        if (jointname.value.isEmpty()) {
            org.set(physicsObj.GetOrigin())
        } else {
            joint = animator.GetJointHandle(jointname.value)
            if (joint == Model.INVALID_JOINT) {
                idGameLocal.Error("Unknown joint '%s' on %s", jointname.value, GetEntityDefName())
            }
            GetJointWorldTransform(joint, Game_local.gameLocal.time, org, axis)
        }
        Game_local.gameLocal.RadiusDamage(org, this, this, this, this, damageDefName.value)
    }

    /*
     ================
     idAI::Event_BeginAttack
     ================
     */
    protected fun Event_BeginAttack(name: idEventArg<String>) {
        BeginAttack(name.value)
    }

    /*
     ================
     idAI::Event_EndAttack
     ================
     */
    protected fun Event_EndAttack() {
        EndAttack()
    }

    /*
     ================
     idAI::Event_MeleeAttackToJoint
     ================
     */
    protected fun Event_MeleeAttackToJoint(jointname: idEventArg<String>, meleeDefName: idEventArg<String>) {
        val   /*jointHandle_t*/joint: Int
        val start = idVec3()
        val end = idVec3()
        val axis = idMat3()
        val trace = trace_s()
        val hitEnt: idEntity?
        joint = animator.GetJointHandle(jointname.value)
        if (joint == Model.INVALID_JOINT) {
            idGameLocal.Error("Unknown joint '%s' on %s", jointname.value, GetEntityDefName())
        }
        animator.GetJointTransform(joint, Game_local.gameLocal.time, end, axis)
        end.set(
            physicsObj.GetOrigin()
                .plus(end.plus(modelOffset).times(viewAxis).times(physicsObj.GetGravityAxis()))
        )
        start.set(GetEyePosition())
        if (SysCvar.ai_debugMove.GetBool()) {
            Game_local.gameRenderWorld!!.DebugLine(colorYellow, start, end, idGameLocal.msec)
        }
        Game_local.gameLocal.clip.TranslationEntities(
            trace,
            start,
            end,
            null,
            idMat3.getMat3_identity(),
            Game_local.MASK_SHOT_BOUNDINGBOX,
            this
        )
        if (trace.fraction < 1.0f) {
            hitEnt = Game_local.gameLocal.GetTraceEntity(trace)
            if (hitEnt != null && hitEnt is idActor) {
                DirectDamage(meleeDefName.value, hitEnt)
                idThread.ReturnInt(true)
                return
            }
        }
        idThread.ReturnInt(false)
    }

    /*
     ================
     idAI::Event_RandomPath
     ================
     */
    protected fun Event_RandomPath() {
        val path: idPathCorner?
        path = idPathCorner.RandomPath(this, null)
        idThread.ReturnEntity(path)
    }

    /*
     ================
     idAI::Event_CanBecomeSolid
     ================
     */
    protected fun Event_CanBecomeSolid() {
        var i: Int
        val num: Int
        var hit: idEntity
        var cm: idClipModel
        val clipModels = arrayOfNulls<idClipModel>(Game_local.MAX_GENTITIES)
        num = Game_local.gameLocal.clip.ClipModelsTouchingBounds(
            physicsObj.GetAbsBounds(),
            Game_local.MASK_MONSTERSOLID,
            clipModels,
            Game_local.MAX_GENTITIES
        )
        i = 0
        while (i < num) {
            cm = clipModels[i]!!

            // don't check render entities
            if (cm.IsRenderModel()) {
                i++
                continue
            }
            hit = cm.GetEntity()!!
            if (hit == this || !hit.fl.takedamage) {
                i++
                continue
            }
            if (physicsObj.ClipContents(cm) != 0) {
                idThread.ReturnFloat(0.0f) //(false);
                return
            }
            i++
        }
        idThread.ReturnFloat(1.0f) //(true);
    }

    /*
     ================
     idAI::Event_BecomeSolid
     ================
     */
    protected fun Event_BecomeSolid() {
        physicsObj.EnableClip()
        if (spawnArgs.GetBool("big_monster")) {
            physicsObj.SetContents(0)
        } else if (use_combat_bbox) {
            physicsObj.SetContents(Material.CONTENTS_BODY or Material.CONTENTS_SOLID)
        } else {
            physicsObj.SetContents(Material.CONTENTS_BODY)
        }
        physicsObj.GetClipModel()!!.Link(Game_local.gameLocal.clip)
        fl.takedamage = !spawnArgs.GetBool("noDamage")
    }

    /*
     ================
     idAI::Event_BecomeNonSolid
     ================
     */
    protected fun Event_BecomeNonSolid() {
        fl.takedamage = false
        physicsObj.SetContents(0)
        physicsObj.GetClipModel()!!.Unlink()
    }

    /*
     ================
     idAI::Event_BecomeRagdoll
     ================
     */
    protected fun Event_BecomeRagdoll() {
        val result: Int
        result = if (StartRagdoll()) 1 else 0
        idThread.ReturnInt(result)
    }

    /*
     ================
     idAI::Event_StopRagdoll
     ================
     */
    protected fun Event_StopRagdoll() {
        StopRagdoll()

        // set back the monster physics
        SetPhysics(physicsObj)
    }

    /*
     ================
     idAI::Event_SetHealth
     ================
     */
    protected fun Event_SetHealth(newHealth: idEventArg<Float>) {
        health = newHealth.value.toInt()
        fl.takedamage = true
        if (health > 0) {
            AI_DEAD.underscore(false)
        } else {
            AI_DEAD.underscore(true)
        }
    }

    /*
     ================
     idAI::Event_GetHealth
     ================
     */
    protected fun Event_GetHealth() {
        idThread.ReturnFloat(health.toFloat())
    }

    /*
     ================
     idAI::Event_AllowDamage
     ================
     */
    protected fun Event_AllowDamage() {
        fl.takedamage = true
    }

    /*
     ================
     idAI::Event_IgnoreDamage
     ================
     */
    protected fun Event_IgnoreDamage() {
        fl.takedamage = false
    }

    /*
     ================
     idAI::Event_GetCurrentYaw
     ================
     */
    protected fun Event_GetCurrentYaw() {
        idThread.ReturnFloat(current_yaw)
    }

    /*
     ================
     idAI::Event_TurnTo
     ================
     */
    protected fun Event_TurnTo(angle: idEventArg<Float>) {
        TurnToward(angle.value)
    }

    /*
     ================
     idAI::Event_TurnToPos
     ================
     */
    protected fun Event_TurnToPos(pos: idEventArg<idVec3>) {
        TurnToward(pos.value)
    }

    /*
     ================
     idAI::Event_TurnToEntity
     ================
     */
    protected fun Event_TurnToEntity(ent: idEventArg<idEntity>) {
        if (ent.value != null) {
            TurnToward(ent.value.GetPhysics().GetOrigin())
        }
    }

    /*
     ================
     idAI::Event_MoveStatus
     ================
     */
    protected fun Event_MoveStatus() {
        idThread.ReturnInt(TempDump.etoi(move.moveStatus))
    }

    /*
     ================
     idAI::Event_StopMove
     ================
     */
    protected fun Event_StopMove() {
        StopMove(moveStatus_t.MOVE_STATUS_DONE)
    }

    /*
     ================
     idAI::Event_MoveToCover
     ================
     */
    protected fun Event_MoveToCover() {
        val enemyEnt = enemy.GetEntity()
        StopMove(moveStatus_t.MOVE_STATUS_DEST_NOT_FOUND)
        if (null == enemyEnt || !MoveToCover(enemyEnt, lastVisibleEnemyPos)) {
            return
        }
    }

    /*
     ================
     idAI::Event_MoveToEnemy
     ================
     */
    protected fun Event_MoveToEnemy() {
        StopMove(moveStatus_t.MOVE_STATUS_DEST_NOT_FOUND)
        if (null == enemy.GetEntity() || !MoveToEnemy()) {
            return
        }
    }

    /*
     ================
     idAI::Event_MoveToEnemyHeight
     ================
     */
    protected fun Event_MoveToEnemyHeight() {
        StopMove(moveStatus_t.MOVE_STATUS_DEST_NOT_FOUND)
        MoveToEnemyHeight()
    }

    /*
     ================
     idAI::Event_MoveOutOfRange
     ================
     */
    protected fun Event_MoveOutOfRange(entity: idEventArg<idEntity>, range: idEventArg<Float>) {
        StopMove(moveStatus_t.MOVE_STATUS_DEST_NOT_FOUND)
        MoveOutOfRange(entity.value, range.value)
    }

    /*
     ================
     idAI::Event_MoveToAttackPosition
     ================
     */
    protected fun Event_MoveToAttackPosition(entity: idEventArg<idEntity>, attack_anim: idEventArg<String>) {
        val anim: Int
        StopMove(moveStatus_t.MOVE_STATUS_DEST_NOT_FOUND)
        anim = GetAnim(Anim.ANIMCHANNEL_LEGS, attack_anim.value)
        if (0 == anim) {
            idGameLocal.Error("Unknown anim '%s'", attack_anim.value)
        }
        MoveToAttackPosition(entity.value, anim)
    }

    /*
     ================
     idAI::Event_MoveToEntity
     ================
     */
    protected fun Event_MoveToEntity(ent: idEventArg<idEntity>) {
        StopMove(moveStatus_t.MOVE_STATUS_DEST_NOT_FOUND)
        if (ent.value != null) {
            MoveToEntity(ent.value)
        }
    }

    /*
     ================
     idAI::Event_MoveToPosition
     ================
     */
    protected fun Event_MoveToPosition(pos: idEventArg<idVec3>) {
        StopMove(moveStatus_t.MOVE_STATUS_DONE)
        MoveToPosition(pos.value)
    }

    /*
     ================
     idAI::Event_SlideTo
     ================
     */
    protected fun Event_SlideTo(pos: idEventArg<idVec3>, time: idEventArg<Float>) {
        SlideToPosition(pos.value, time.value)
    }

    /*
     ================
     idAI::Event_Wander
     ================
     */
    protected fun Event_Wander() {
        WanderAround()
    }

    /*
     ================
     idAI::Event_FacingIdeal
     ================
     */
    protected fun Event_FacingIdeal() {
        val facing = FacingIdeal()
        idThread.ReturnInt(facing)
    }

    /*
     ================
     idAI::Event_FaceEnemy
     ================
     */
    protected fun Event_FaceEnemy() {
        FaceEnemy()
    }

    /*
     ================
     idAI::Event_FaceEntity
     ================
     */
    protected fun Event_FaceEntity(ent: idEventArg<idEntity>) {
        FaceEntity(ent.value)
    }

    /*
     ================
     idAI::Event_WaitAction
     ================
     */
    protected fun Event_WaitAction(waitForState: idEventArg<String>) {
        if (idThread.BeginMultiFrameEvent(this, AI_WaitAction)) {
            SetWaitState(waitForState.value)
        }
        if (null == WaitState()) {
            idThread.EndMultiFrameEvent(this, AI_WaitAction)
        }
    }

    /*
     ================
     idAI::Event_GetCombatNode
     ================
     */
    protected fun Event_GetCombatNode() {
        var i: Int
        var dist: Float
        var targetEnt: idEntity?
        var node: idCombatNode?
        var bestDist: Float
        var bestNode: idCombatNode?
        val enemyEnt = enemy.GetEntity()
        if (0 == targets.Num()) {
            // no combat nodes
            idThread.ReturnEntity(null)
            return
        }
        if (null == enemyEnt || !EnemyPositionValid()) {
            // don't return a combat node if we don't have an enemy or
            // if we can see he's not in the last place we saw him
            idThread.ReturnEntity(null)
            return
        }

        // find the closest attack node that can see our enemy and is closer than our enemy
        bestNode = null
        val myPos = physicsObj.GetOrigin()
        bestDist = myPos.minus(lastVisibleEnemyPos).LengthSqr()
        i = 0
        while (i < targets.Num()) {
            targetEnt = targets[i].GetEntity()
            if (null == targetEnt || targetEnt !is idCombatNode) {
                i++
                continue
            }
            node = targetEnt
            if (!node.IsDisabled() && node.EntityInView(enemyEnt, lastVisibleEnemyPos)) {
                val org = idVec3(node.GetPhysics().GetOrigin())
                dist = myPos.minus(org).LengthSqr()
                if (dist < bestDist) {
                    bestNode = node
                    bestDist = dist
                }
            }
            i++
        }
        idThread.ReturnEntity(bestNode)
    }

    /*
     ================
     idAI::Event_EnemyInCombatCone
     ================
     */
    protected fun Event_EnemyInCombatCone(
        _ent: idEventArg<idEntity>,
        use_current_enemy_location: idEventArg<Int>
    ) {
        val ent = _ent.value
        val node: idCombatNode?
        val result: Boolean
        val enemyEnt = enemy.GetEntity()
        if (0 == targets.Num()) {
            // no combat nodes
            idThread.ReturnInt(false)
            return
        }
        if (null == enemyEnt) {
            // have to have an enemy
            idThread.ReturnInt(false)
            return
        }
        if (null == ent || ent !is idCombatNode) {
            // not a combat node
            idThread.ReturnInt(false)
            return
        }
        node = ent
        result = if (use_current_enemy_location.value != 0) {
            val pos = enemyEnt.GetPhysics().GetOrigin()
            node.EntityInView(enemyEnt, pos)
        } else {
            node.EntityInView(enemyEnt, lastVisibleEnemyPos)
        }
        idThread.ReturnInt(result)
    }

    /*
     ================
     idAI::Event_WaitMove
     ================
     */
    protected fun Event_WaitMove() {
        idThread.BeginMultiFrameEvent(this, AI_WaitMove)
        if (MoveDone()) {
            idThread.EndMultiFrameEvent(this, AI_WaitMove)
        }
    }

    /*
     ================
     idAI::Event_GetJumpVelocity
     ================
     */
    protected fun Event_GetJumpVelocity(
        _pos: idEventArg<idVec3>,
        _speed: idEventArg<Float>,
        _max_height: idEventArg<Float>
    ) {
        val pos = idVec3(_pos.value)
        val speed: Float = _speed.value
        val max_height: Float = _max_height.value
        val start = idVec3()
        val end = idVec3()
        val dir = idVec3()
        var dist: Float
        val result: Boolean
        val enemyEnt: idEntity? = enemy.GetEntity()
        if (null == enemyEnt) {
            idThread.ReturnVector(vec3_zero)
            return
        }
        if (speed <= 0.0f) {
            idGameLocal.Error("Invalid speed.  speed must be > 0.")
        }
        start.set(physicsObj.GetOrigin())
        end.set(pos)
        dir.set(end.minus(start))
        dist = dir.Normalize()
        if (dist > 16.0f) {
            dist -= 16.0f
            end.minusAssign(dir * 16.0f)
        }
        result = PredictTrajectory(
            start,
            end,
            speed,
            physicsObj.GetGravity(),
            physicsObj.GetClipModel()!!,
            Game_local.MASK_MONSTERSOLID,
            max_height,
            this,
            enemyEnt,
            if (SysCvar.ai_debugMove.GetBool()) 4000 else 0,
            dir
        )
        if (result) {
            idThread.ReturnVector(dir.times(speed))
        } else {
            idThread.ReturnVector(vec3_zero)
        }
    }

    /*
     ================
     idAI::Event_EntityInAttackCone
     ================
     */
    protected fun Event_EntityInAttackCone(ent: idEventArg<idEntity>) {
        val attack_cone: Float
        val delta = idVec3()
        val yaw: Float
        val relYaw: Float
        if (null == ent.value) {
            idThread.ReturnInt(false)
            return
        }
        delta.set(ent.value.GetPhysics().GetOrigin().minus(GetEyePosition()))

        // get our gravity normal
        val gravityDir = GetPhysics().GetGravityNormal()

        // infinite vertical vision, so project it onto our orientation plane
        delta.minusAssign(gravityDir.times(gravityDir.times(delta)))
        delta.Normalize()
        yaw = delta.ToYaw()
        attack_cone = spawnArgs.GetFloat("attack_cone", "70")
        relYaw = idMath.AngleNormalize180(ideal_yaw - yaw)
        idThread.ReturnInt(abs(relYaw) < attack_cone * 0.5f)
    }

    /*
     ================
     idAI::Event_CanSeeEntity
     ================
     */
    protected fun Event_CanSeeEntity(ent: idEventArg<idEntity>) {
        if (null == ent.value) {
            idThread.ReturnInt(false)
            return
        }
        val cansee = CanSee(ent.value, false)
        idThread.ReturnInt(cansee)
    }

    /*
     ================
     idAI::Event_SetTalkTarget
     ================
     */
    protected fun Event_SetTalkTarget(_target: idEventArg<idEntity?>) {
        val target = _target.value
        if (target != null && target !is idActor) {
            idGameLocal.Error(
                "Cannot set talk target to '%s'.  Not a character or player.",
                target.GetName()
            )
        }
        talkTarget.oSet(target as idActor?)
        if (target != null) {
            AI_TALK.underscore(true)
        } else {
            AI_TALK.underscore(false)
        }
    }

    /*
     ================
     idAI::Event_GetTalkTarget
     ================
     */
    protected fun Event_GetTalkTarget() {
        idThread.ReturnEntity(talkTarget.GetEntity())
    }

    /*
     ================
     idAI::Event_SetTalkState
     ================
     */
    protected fun Event_SetTalkState(_state: idEventArg<Int>) {
        val state: Int = _state.value
        if (state < 0 || state >= TempDump.etoi(talkState_t.NUM_TALK_STATES)) {
            idGameLocal.Error("Invalid talk state (%d)", state)
        }
        talk_state = talkState_t.entries.toTypedArray()[state]
    }

    /*
     ================
     idAI::Event_EnemyRange
     ================
     */
    protected fun Event_EnemyRange() {
        val dist: Float
        val enemyEnt = enemy.GetEntity()
        dist = enemyEnt?.GetPhysics()?.GetOrigin()?.minus(GetPhysics().GetOrigin())?.Length()
            ?: // Just some really high number
                    idMath.INFINITY
        idThread.ReturnFloat(dist)
    }

    /*
     ================
     idAI::Event_EnemyRange2D
     ================
     */
    protected fun Event_EnemyRange2D() {
        val dist: Float
        val enemyEnt = enemy.GetEntity()
        dist = enemyEnt?.GetPhysics()?.GetOrigin()?.ToVec2()?.minus(GetPhysics().GetOrigin().ToVec2())?.Length()
            ?: // Just some really high number
                    idMath.INFINITY
        idThread.ReturnFloat(dist)
    }

    /*
     ================
     idAI::Event_GetEnemy
     ================
     */
    protected fun Event_GetEnemy() {
        idThread.ReturnEntity(enemy.GetEntity())
    }

    /*
     ================
     idAI::Event_GetEnemyPos
     ================
     */
    protected fun Event_GetEnemyPos() {
        idThread.ReturnVector(lastVisibleEnemyPos)
    }

    /*
     ================
     idAI::Event_GetEnemyEyePos
     ================
     */
    protected fun Event_GetEnemyEyePos() {
        idThread.ReturnVector(lastVisibleEnemyPos.plus(lastVisibleEnemyEyeOffset))
    }

    /*
     ================
     idAI::Event_PredictEnemyPos
     ================
     */
    protected fun Event_PredictEnemyPos(time: idEventArg<Float>) {
        val path = predictedPath_s()
        val enemyEnt = enemy.GetEntity()

        // if no enemy set
        if (null == enemyEnt) {
            idThread.ReturnVector(physicsObj.GetOrigin())
            return
        }

        // predict the enemy movement
        PredictPath(
            enemyEnt,
            aas,
            lastVisibleEnemyPos,
            enemyEnt.GetPhysics().GetLinearVelocity(),
            SEC2MS(time.value).toInt(),
            SEC2MS(time.value).toInt(),
            if (move.moveType == moveType_t.MOVETYPE_FLY) SE_BLOCKED else SE_BLOCKED or SE_ENTER_LEDGE_AREA,
            path
        )
        idThread.ReturnVector(path.endPos)
    }

    /*
     ================
     idAI::Event_CanHitEnemy
     ================
     */
    protected fun Event_CanHitEnemy() {
        val tr = trace_s()
        val hit: idEntity?
        val enemyEnt = enemy.GetEntity()
        if (!AI_ENEMY_VISIBLE.underscore()!! || enemyEnt == null) {
            idThread.ReturnInt(false)
            return
        }

        // don't check twice per frame
        if (Game_local.gameLocal.time == lastHitCheckTime) {
            idThread.ReturnInt(lastHitCheckResult)
            return
        }
        lastHitCheckTime = Game_local.gameLocal.time
        val toPos = idVec3(enemyEnt!!.GetEyePosition())
        val eye = idVec3(GetEyePosition())
        val dir = idVec3()

        // expand the ray out as far as possible so we can detect anything behind the enemy
        dir.set(toPos.minus(eye))
        dir.Normalize()
        toPos.set(eye.plus(dir.times(MAX_WORLD_SIZE.toFloat())))
        Game_local.gameLocal.clip.TracePoint(tr, eye, toPos, Game_local.MASK_SHOT_BOUNDINGBOX, this)
        hit = Game_local.gameLocal.GetTraceEntity(tr)
        lastHitCheckResult = if (tr.fraction >= 1.0f || hit == enemyEnt) {
            true
        } else tr.fraction < 1.0f && hit is idAI
                && hit.team != team
        idThread.ReturnInt(lastHitCheckResult)
    }

    /*
     ================
     idAI::Event_CanHitEnemyFromAnim
     ================
     */
    protected fun Event_CanHitEnemyFromAnim(animname: idEventArg<String>) {
        val anim: Int
        val local_dir = idVec3()
        val dir = idVec3()
        val fromPos = idVec3()
        val start = idVec3()
        val axis: idMat3
        val tr = trace_s()
        val distance = CFloat()
        val enemyEnt = enemy.GetEntity()
        if (!AI_ENEMY_VISIBLE.underscore()!! || enemyEnt == null) {
            idThread.ReturnInt(false)
            return
        }
        anim = GetAnim(Anim.ANIMCHANNEL_LEGS, animname.value)
        if (0 == anim) {
            idThread.ReturnInt(false)
            return
        }

        // just do a ray test if close enough
        if (enemyEnt!!.GetPhysics().GetAbsBounds().IntersectsBounds(physicsObj.GetAbsBounds().Expand(16.0f))) {
            Event_CanHitEnemy()
            return
        }

        // calculate the world transform of the launch position
        val org = physicsObj.GetOrigin()
        dir.set(lastVisibleEnemyPos.minus(org))
        physicsObj.GetGravityAxis().ProjectVector(dir, local_dir)
        local_dir.z = 0.0f
        local_dir.ToVec2_Normalize()
        axis = local_dir.ToMat3()
        fromPos.set(physicsObj.GetOrigin().plus(missileLaunchOffset[anim].times(axis)))
        if (projectileClipModel == null) {
            CreateProjectileClipModel()
        }

        // check if the owner bounds is bigger than the projectile bounds
        val ownerBounds = physicsObj.GetAbsBounds()
        val projBounds = projectileClipModel!!.GetBounds()
        if (ownerBounds[1, 0] - ownerBounds[0, 0] > projBounds[1, 0] - projBounds[0, 0]
            && ownerBounds[1, 1] - ownerBounds[0, 1] > projBounds[1, 1] - projBounds[0, 1]
            && ownerBounds[1, 2] - ownerBounds[0, 2] > projBounds[1, 2] - projBounds[0, 2]
        ) {
            if (ownerBounds.minus(projBounds).RayIntersection(org, viewAxis[0], distance)) {
                start.set(org.plus(viewAxis[0].times(distance._val)))
            } else {
                start.set(ownerBounds.GetCenter())
            }
        } else {
            // projectile bounds bigger than the owner bounds, so just start it from the center
            start.set(ownerBounds.GetCenter())
        }
        Game_local.gameLocal.clip.Translation(
            tr,
            start,
            fromPos,
            projectileClipModel,
            idMat3.getMat3_identity(),
            Game_local.MASK_SHOT_RENDERMODEL,
            this
        )
        fromPos.set(tr.endpos)
        idThread.ReturnInt(GetAimDir(fromPos, enemy.GetEntity(), this, dir))
    }

    /*
     ================
     idAI::Event_CanHitEnemyFromJoint
     ================
     */
    protected fun Event_CanHitEnemyFromJoint(jointname: idEventArg<String>) {
        val tr = trace_s()
        val muzzle = idVec3()
        val start = idVec3()
        val axis = idMat3()
        val distance = CFloat()
        val enemyEnt = enemy.GetEntity()
        if (!AI_ENEMY_VISIBLE.underscore()!! || null == enemyEnt) {
            idThread.ReturnInt(false)
            return
        }

        // don't check twice per frame
        if (Game_local.gameLocal.time == lastHitCheckTime) {
            idThread.ReturnInt(lastHitCheckResult)
            return
        }
        lastHitCheckTime = Game_local.gameLocal.time
        val org = physicsObj.GetOrigin()
        val toPos = idVec3(enemyEnt.GetEyePosition())
        val   /*jointHandle_t*/joint = animator.GetJointHandle(jointname.value)
        if (joint == Model.INVALID_JOINT) {
            idGameLocal.Error("Unknown joint '%s' on %s", jointname.value, GetEntityDefName())
        }
        animator.GetJointTransform(joint, Game_local.gameLocal.time, muzzle, axis)
        muzzle.set(org.plus(muzzle.plus(modelOffset).times(viewAxis).times(physicsObj.GetGravityAxis())))
        if (projectileClipModel == null) {
            CreateProjectileClipModel()
        }

        // check if the owner bounds is bigger than the projectile bounds
        val ownerBounds = physicsObj.GetAbsBounds()
        val projBounds = projectileClipModel!!.GetBounds()
        if (ownerBounds[1, 0] - ownerBounds[0, 0] > projBounds[1, 0] - projBounds[0, 0]
            && ownerBounds[1, 1] - ownerBounds[0, 1] > projBounds[1, 1] - projBounds[0, 1]
            && ownerBounds[1, 2] - ownerBounds[0, 2] > projBounds[1, 2] - projBounds[0, 2]
        ) {
            if (ownerBounds.minus(projBounds).RayIntersection(org, viewAxis[0], distance)) {
                start.set(org.plus(viewAxis[0].times(distance._val)))
            } else {
                start.set(ownerBounds.GetCenter())
            }
        } else {
            // projectile bounds bigger than the owner bounds, so just start it from the center
            start.set(ownerBounds.GetCenter())
        }
        Game_local.gameLocal.clip.Translation(
            tr,
            start,
            muzzle,
            projectileClipModel,
            idMat3.getMat3_identity(),
            Game_local.MASK_SHOT_BOUNDINGBOX,
            this
        )
        muzzle.set(tr.endpos)
        Game_local.gameLocal.clip.Translation(
            tr,
            muzzle,
            toPos,
            projectileClipModel,
            idMat3.getMat3_identity(),
            Game_local.MASK_SHOT_BOUNDINGBOX,
            this
        )
        lastHitCheckResult = tr.fraction >= 1.0f || Game_local.gameLocal.GetTraceEntity(tr) == enemyEnt
        idThread.ReturnInt(lastHitCheckResult)
    }

    /*
     ================
     idAI::Event_EnemyPositionValid
     ================
     */
    protected fun Event_EnemyPositionValid() {
        val result: Int
        result = if (EnemyPositionValid()) 1 else 0
        idThread.ReturnInt(result)
    }

    /*
     ================
     idAI::Event_ChargeAttack
     ================
     */
    protected fun Event_ChargeAttack(damageDef: idEventArg<String>) {
        val enemyEnt = enemy.GetEntity()
        StopMove(moveStatus_t.MOVE_STATUS_DEST_NOT_FOUND)
        if (enemyEnt != null) {
            val enemyOrg = idVec3()
            if (move.moveType == moveType_t.MOVETYPE_FLY) {
                // position destination so that we're in the enemy's view
                enemyOrg.set(enemyEnt.GetEyePosition())
                enemyOrg.minusAssign(enemyEnt.GetPhysics().GetGravityNormal().times(fly_offset.toFloat()))
            } else {
                enemyOrg.set(enemyEnt.GetPhysics().GetOrigin())
            }
            BeginAttack(damageDef.value)
            DirectMoveToPosition(enemyOrg)
            TurnToward(enemyOrg)
        }
    }

    /*
     ================
     idAI::Event_TestChargeAttack
     ================
     */
    protected fun Event_TestChargeAttack() {
        trace_s()
        val enemyEnt = enemy.GetEntity()
        val path = predictedPath_s()
        val end = idVec3()
        if (null == enemyEnt) {
            idThread.ReturnFloat(0.0f)
            return
        }
        if (move.moveType == moveType_t.MOVETYPE_FLY) {
            // position destination so that we're in the enemy's view
            end.set(enemyEnt.GetEyePosition())
            end.minusAssign(enemyEnt.GetPhysics().GetGravityNormal().times(fly_offset.toFloat()))
        } else {
            end.set(enemyEnt.GetPhysics().GetOrigin())
        }
        PredictPath(
            this,
            aas,
            physicsObj.GetOrigin(),
            end.minus(physicsObj.GetOrigin()),
            1000,
            1000,
            if (move.moveType == moveType_t.MOVETYPE_FLY) SE_BLOCKED else SE_ENTER_OBSTACLE or SE_BLOCKED or SE_ENTER_LEDGE_AREA,
            path
        )
        if (SysCvar.ai_debugMove.GetBool()) {
            Game_local.gameRenderWorld!!.DebugLine(
                colorGreen,
                physicsObj.GetOrigin(),
                end,
                idGameLocal.msec
            )
            Game_local.gameRenderWorld!!.DebugBounds(
                if (path.endEvent == 0) colorYellow else colorRed,
                physicsObj.GetBounds(),
                end,
                idGameLocal.msec
            )
        }
        if (path.endEvent == 0 || path.blockingEntity == enemyEnt) {
            val delta = idVec3(end.minus(physicsObj.GetOrigin()))
            val time = delta.LengthFast()
            idThread.ReturnFloat(time)
        } else {
            idThread.ReturnFloat(0.0f)
        }
    }

    /*
     ================
     idAI::Event_TestAnimMoveTowardEnemy
     ================
     */
    protected fun Event_TestAnimMoveTowardEnemy(animname: idEventArg<String>) {
        val anim: Int
        val path = predictedPath_s()
        val moveVec = idVec3()
        val delta = idVec3()
        val yaw: Float
        val enemyEnt: idActor?
        enemyEnt = enemy.GetEntity()
        if (null == enemyEnt) {
            idThread.ReturnInt(false)
            return
        }
        anim = GetAnim(Anim.ANIMCHANNEL_LEGS, animname.value)
        if (0 == anim) {
            Game_local.gameLocal.DWarning(
                "missing '%s' animation on '%s' (%s)",
                animname.value,
                name,
                GetEntityDefName()
            )
            idThread.ReturnInt(false)
            return
        }
        delta.set(enemyEnt.GetPhysics().GetOrigin().minus(physicsObj.GetOrigin()))
        yaw = delta.ToYaw()
        moveVec.set(
            animator.TotalMovementDelta(anim)
                .times(idAngles(0.0f, yaw, 0.0f).ToMat3().times(physicsObj.GetGravityAxis()))
        )
        PredictPath(
            this,
            aas,
            physicsObj.GetOrigin(),
            moveVec,
            1000,
            1000,
            if (move.moveType == moveType_t.MOVETYPE_FLY) SE_BLOCKED else SE_ENTER_OBSTACLE or SE_BLOCKED or SE_ENTER_LEDGE_AREA,
            path
        )
        if (SysCvar.ai_debugMove.GetBool()) {
            Game_local.gameRenderWorld!!.DebugLine(
                colorGreen,
                physicsObj.GetOrigin(),
                physicsObj.GetOrigin().plus(moveVec),
                idGameLocal.msec
            )
            Game_local.gameRenderWorld!!.DebugBounds(
                if (path.endEvent == 0) colorYellow else colorRed,
                physicsObj.GetBounds(),
                physicsObj.GetOrigin().plus(moveVec),
                idGameLocal.msec
            )
        }
        idThread.ReturnInt(path.endEvent == 0)
    }

    /*
     ================
     idAI::Event_TestAnimMove
     ================
     */
    protected fun Event_TestAnimMove(animname: idEventArg<String>) {
        val anim: Int
        val path = predictedPath_s()
        val moveVec = idVec3()
        anim = GetAnim(Anim.ANIMCHANNEL_LEGS, animname.value)
        if (0 == anim) {
            Game_local.gameLocal.DWarning(
                "missing '%s' animation on '%s' (%s)",
                animname.value,
                name,
                GetEntityDefName()
            )
            idThread.ReturnInt(false)
            return
        }
        moveVec.set(
            animator.TotalMovementDelta(anim)
                .times(idAngles(0.0f, ideal_yaw, 0.0f).ToMat3().times(physicsObj.GetGravityAxis()))
        )
        PredictPath(
            this,
            aas,
            physicsObj.GetOrigin(),
            moveVec,
            1000,
            1000,
            if (move.moveType == moveType_t.MOVETYPE_FLY) SE_BLOCKED else SE_ENTER_OBSTACLE or SE_BLOCKED or SE_ENTER_LEDGE_AREA,
            path
        )
        if (SysCvar.ai_debugMove.GetBool()) {
            Game_local.gameRenderWorld!!.DebugLine(
                colorGreen,
                physicsObj.GetOrigin(),
                physicsObj.GetOrigin().plus(moveVec),
                idGameLocal.msec
            )
            Game_local.gameRenderWorld!!.DebugBounds(
                if (path.endEvent == 0) colorYellow else colorRed,
                physicsObj.GetBounds(),
                physicsObj.GetOrigin().plus(moveVec),
                idGameLocal.msec
            )
        }
        idThread.ReturnInt(path.endEvent == 0)
    }

    /*
     ================
     idAI::Event_TestMoveToPosition
     ================
     */
    protected fun Event_TestMoveToPosition(_position: idEventArg<idVec3>) {
        val position = idVec3(_position.value)
        val path = predictedPath_s()
        PredictPath(
            this,
            aas,
            physicsObj.GetOrigin(),
            position.minus(physicsObj.GetOrigin()),
            1000,
            1000,
            if (move.moveType == moveType_t.MOVETYPE_FLY) SE_BLOCKED else SE_ENTER_OBSTACLE or SE_BLOCKED or SE_ENTER_LEDGE_AREA,
            path
        )
        if (SysCvar.ai_debugMove.GetBool()) {
            Game_local.gameRenderWorld!!.DebugLine(
                colorGreen,
                physicsObj.GetOrigin(),
                position,
                idGameLocal.msec
            )
            Game_local.gameRenderWorld!!.DebugBounds(
                colorYellow,
                physicsObj.GetBounds(),
                position,
                idGameLocal.msec
            )
            if (path.endEvent != 0) {
                Game_local.gameRenderWorld!!.DebugBounds(
                    colorRed,
                    physicsObj.GetBounds(),
                    path.endPos,
                    idGameLocal.msec
                )
            }
        }
        idThread.ReturnInt(path.endEvent == 0)
    }

    /*
     ================
     idAI::Event_TestMeleeAttack
     ================
     */
    protected fun Event_TestMeleeAttack() {
        val result = TestMelee()
        idThread.ReturnInt(result)
    }

    /*
     ================
     idAI::Event_TestAnimAttack
     ================
     */
    protected fun Event_TestAnimAttack(animname: idEventArg<String>) {
        val anim: Int
        val path = predictedPath_s()
        anim = GetAnim(Anim.ANIMCHANNEL_LEGS, animname.value)
        if (0 == anim) {
            Game_local.gameLocal.DWarning(
                "missing '%s' animation on '%s' (%s)",
                animname.value,
                name,
                GetEntityDefName()
            )
            idThread.ReturnInt(false)
            return
        }
        PredictPath(
            this,
            aas,
            physicsObj.GetOrigin(),
            animator.TotalMovementDelta(anim),
            1000,
            1000,
            if (move.moveType == moveType_t.MOVETYPE_FLY) SE_BLOCKED else SE_ENTER_OBSTACLE or SE_BLOCKED or SE_ENTER_LEDGE_AREA,
            path
        )
        idThread.ReturnInt(path.blockingEntity != null && path.blockingEntity == enemy.GetEntity())
    }

    /*
     ================
     idAI::Event_Shrivel
     ================
     */
    protected fun Event_Shrivel(shrivel_time: idEventArg<Float>) {
        var t: Float
        if (idThread.BeginMultiFrameEvent(this, AI_Shrivel)) {
            if (shrivel_time.value <= 0.0f) {
                idThread.EndMultiFrameEvent(this, AI_Shrivel)
                return
            }
            shrivel_rate = 0.001f / shrivel_time.value
            shrivel_start = Game_local.gameLocal.time
        }
        t = (Game_local.gameLocal.time - shrivel_start) * shrivel_rate
        if (t > 0.25f) {
            renderEntity!!.noShadow = true
        }
        if (t > 1.0f) {
            t = 1.0f
            idThread.EndMultiFrameEvent(this, AI_Shrivel)
        }
        renderEntity!!.shaderParms[RenderWorld.SHADERPARM_MD5_SKINSCALE] = 1.0f - t * 0.5f
        UpdateVisuals()
    }

    /*
     ================
     idAI::Event_Burn
     ================
     */
    protected fun Event_Burn() {
        renderEntity!!.shaderParms[RenderWorld.SHADERPARM_TIME_OF_DEATH] = Game_local.gameLocal.time * 0.001f
        SpawnParticles("smoke_burnParticleSystem")
        UpdateVisuals()
    }

    /*
     ================
     idAI::Event_PreBurn
     ================
     */
    protected fun Event_PreBurn() {
        // for now this just turns shadows off
        renderEntity!!.noShadow = true
    }

    /*
     ================
     idAI::Event_ClearBurn
     ================
     */
    protected fun Event_ClearBurn() {
        renderEntity!!.noShadow = spawnArgs.GetBool("noshadows")
        renderEntity!!.shaderParms[RenderWorld.SHADERPARM_TIME_OF_DEATH] = 0.0f
        UpdateVisuals()
    }

    /*
     ================
     idAI::Event_SetSmokeVisibility
     ================
     */
    protected fun Event_SetSmokeVisibility(_num: idEventArg<Int>, on: idEventArg<Int>) {
        val num: Int = _num.value
        var i: Int
        val time: Int
        if (num >= particles.Num()) {
            Game_local.gameLocal.Warning(
                "Particle #%d out of range (%d particles) on entity '%s'",
                num,
                particles.Num(),
                name
            )
            return
        }
        if (on.value != 0) {
            time = Game_local.gameLocal.time
            BecomeActive(TH_UPDATEPARTICLES)
        } else {
            time = 0
        }
        if (num >= 0) {
            particles[num].time = time
        } else {
            i = 0
            while (i < particles.Num()) {
                particles[i].time = time
                i++
            }
        }
        UpdateVisuals()
    }

    /*
     ================
     idAI::Event_NumSmokeEmitters
     ================
     */
    protected fun Event_NumSmokeEmitters() {
        idThread.ReturnInt(particles.Num())
    }

    /*
     ================
     idAI::Event_StopThinking
     ================
     */
    protected fun Event_StopThinking() {
        BecomeInactive(TH_THINK)
        val thread: idThread? = idThread.CurrentThread()
        if (thread != null) {
            thread.DoneProcessing()
        }
    }

    /*
     ================
     idAI::Event_GetTurnDelta
     ================
     */
    protected fun Event_GetTurnDelta() {
        val amount: Float
        if (turnRate != 0.0f) {
            amount = idMath.AngleNormalize180(ideal_yaw - current_yaw)
            idThread.ReturnFloat(amount)
        } else {
            idThread.ReturnFloat(0.0f)
        }
    }

    /*
     ================
     idAI::Event_GetMoveType
     ================
     */
    protected fun Event_GetMoveType() {
        idThread.ReturnInt(TempDump.etoi(move.moveType))
    }

    /*
     ================
     idAI::Event_SetMoveType
     ================
     */
    protected fun Event_SetMoveType(_moveType: idEventArg<Int>) {
        val moveType: Int = _moveType.value
        if (moveType < 0 || moveType >= TempDump.etoi(moveType_t.NUM_MOVETYPES)) {
            idGameLocal.Error("Invalid movetype %d", moveType)
        }
        move.moveType = moveType_t.entries.toTypedArray()[moveType]
        travelFlags = if (move.moveType == moveType_t.MOVETYPE_FLY) {
            AASFile.TFL_WALK or AASFile.TFL_AIR or AASFile.TFL_FLY
        } else {
            AASFile.TFL_WALK or AASFile.TFL_AIR
        }
    }

    /*
     ================
     idAI::Event_SaveMove
     ================
     */
    protected fun Event_SaveMove() {
        // FIX: Was `savedMove = move` which copies the reference (both point to same object).
        // C++ performs a memberwise value copy. Use copyFrom() for deep copy.
        savedMove.copyFrom(move)
    }

    /*
     ================
     idAI::Event_RestoreMove
     ================
     */
    protected fun Event_RestoreMove() {
        val goalPos = idVec3()
        val dest = idVec3()
        when (savedMove.moveCommand) {
            moveCommand_t.MOVE_NONE -> StopMove(savedMove.moveStatus)
            moveCommand_t.MOVE_FACE_ENEMY -> FaceEnemy()
            moveCommand_t.MOVE_FACE_ENTITY -> FaceEntity(savedMove.goalEntity.GetEntity())
            moveCommand_t.MOVE_TO_ENEMY -> MoveToEnemy()
            moveCommand_t.MOVE_TO_ENEMYHEIGHT -> MoveToEnemyHeight()
            moveCommand_t.MOVE_TO_ENTITY -> MoveToEntity(savedMove.goalEntity.GetEntity())
            moveCommand_t.MOVE_OUT_OF_RANGE -> MoveOutOfRange(savedMove.goalEntity.GetEntity(), savedMove.range)
            moveCommand_t.MOVE_TO_ATTACK_POSITION -> MoveToAttackPosition(
                savedMove.goalEntity.GetEntity(),
                savedMove.anim
            )

            moveCommand_t.MOVE_TO_COVER -> MoveToCover(savedMove.goalEntity.GetEntity(), lastVisibleEnemyPos)
            moveCommand_t.MOVE_TO_POSITION -> MoveToPosition(savedMove.moveDest)
            moveCommand_t.MOVE_TO_POSITION_DIRECT -> DirectMoveToPosition(savedMove.moveDest)
            moveCommand_t.MOVE_SLIDE_TO_POSITION -> SlideToPosition(
                savedMove.moveDest,
                savedMove.duration.toFloat()
            )

            moveCommand_t.MOVE_WANDER -> WanderAround()
            else -> {}
        }
        if (GetMovePos(goalPos)) {
            CheckObstacleAvoidance(goalPos, dest)
        }
    }

    /*
     ================
     idAI::Event_AllowMovement
     ================
     */
    protected fun Event_AllowMovement(flag: idEventArg<Float>) {
        allowMove = flag.value != 0.0f
    }

    /*
     ================
     idAI::Event_JumpFrame
     ================
     */
    protected fun Event_JumpFrame() {
        AI_JUMP.underscore(true)
    }

    /*
     ================
     idAI::Event_EnableClip
     ================
     */
    protected fun Event_EnableClip() {
        physicsObj.SetClipMask(Game_local.MASK_MONSTERSOLID)
        disableGravity = false
    }

    /*
     ================
     idAI::Event_DisableClip
     ================
     */
    protected fun Event_DisableClip() {
        physicsObj.SetClipMask(0)
        disableGravity = true
    }

    /*
     ================
     idAI::Event_EnableGravity
     ================
     */
    protected fun Event_EnableGravity() {
        disableGravity = false
    }

    /*
     ================
     idAI::Event_DisableGravity
     ================
     */
    protected fun Event_DisableGravity() {
        disableGravity = true
    }

    /*
     ================
     idAI::Event_EnableAFPush
     ================
     */
    protected fun Event_EnableAFPush() {
        af_push_moveables = true
    }

    /*
     ================
     idAI::Event_DisableAFPush
     ================
     */
    protected fun Event_DisableAFPush() {
        af_push_moveables = false
    }

    /*
     ================
     idAI::Event_SetFlySpeed
     ================
     */
    protected fun Event_SetFlySpeed(speed: idEventArg<Float>) {
        if (move.speed == fly_speed) {
            move.speed = speed.value
        }
        fly_speed = speed.value
    }

    /*
     ================
     idAI::Event_SetFlyOffset
     ================
     */
    protected fun Event_SetFlyOffset(offset: idEventArg<Int>) {
        fly_offset = offset.value
    }

    /*
     ================
     idAI::Event_ClearFlyOffset
     ================
     */
    protected fun Event_ClearFlyOffset() {
        fly_offset = spawnArgs.GetInt("fly_offset", "0")
    }

    /*
     ================
     idAI::Event_GetClosestHiddenTarget
     ================
     */
    protected fun Event_GetClosestHiddenTarget(type: idEventArg<String>) {
        var i: Int
        var ent: idEntity?
        var bestEnt: idEntity?
        var time: Float
        var bestTime: Float
        val org = physicsObj.GetOrigin()
        val enemyEnt = enemy.GetEntity()
        if (null == enemyEnt) {
            // no enemy to hide from
            idThread.ReturnEntity(null)
            return
        }
        if (targets.Num() == 1) {
            ent = targets[0].GetEntity()
            if (ent != null && idStr.Cmp(ent.GetEntityDefName(), type.value) == 0) {
                if (!EntityCanSeePos(enemyEnt, lastVisibleEnemyPos, ent.GetPhysics().GetOrigin())) {
                    idThread.ReturnEntity(ent)
                    return
                }
            }
            idThread.ReturnEntity(null)
            return
        }
        bestEnt = null
        bestTime = idMath.INFINITY
        i = 0
        while (i < targets.Num()) {
            ent = targets[i].GetEntity()
            if (ent != null && idStr.Cmp(ent.GetEntityDefName(), type.value) == 0) {
                val destOrg = ent.GetPhysics().GetOrigin()
                time = TravelDistance(org, destOrg)
                if (time >= 0.0f && time < bestTime) {
                    if (!EntityCanSeePos(enemyEnt, lastVisibleEnemyPos, destOrg)) {
                        bestEnt = ent
                        bestTime = time
                    }
                }
            }
            i++
        }
        idThread.ReturnEntity(bestEnt)
    }

    /*
     ================
     idAI::Event_GetRandomTarget
     ================
     */
    protected fun Event_GetRandomTarget(type: idEventArg<String>) {
        var i: Int
        var num: Int
        val which: Int
        var ent: idEntity?
        val ents = arrayOfNulls<idEntity?>(Game_local.MAX_GENTITIES)
        num = 0
        i = 0
        while (i < targets.Num()) {
            ent = targets[i].GetEntity()
            if (ent != null && idStr.Cmp(ent.GetEntityDefName(), type.value) == 0) {
                ents[num++] = ent
                if (num >= Game_local.MAX_GENTITIES) {
                    break
                }
            }
            i++
        }
        if (0 == num) {
            idThread.ReturnEntity(null)
            return
        }
        which = Game_local.gameLocal.random.RandomInt(num)
        idThread.ReturnEntity(ents[which])
    }

    /*
     ================
     idAI::Event_TravelDistanceToPoint
     ================
     */
    protected fun Event_TravelDistanceToPoint(pos: idEventArg<idVec3>) {
        val time: Float
        time = TravelDistance(physicsObj.GetOrigin(), pos.value)
        idThread.ReturnFloat(time)
    }

    /*
     ================
     idAI::Event_TravelDistanceToEntity
     ================
     */
    protected fun Event_TravelDistanceToEntity(ent: idEventArg<idEntity>) {
        val time: Float
        time = TravelDistance(physicsObj.GetOrigin(), ent.value.GetPhysics().GetOrigin())
        idThread.ReturnFloat(time)
    }

    /*
     ================
     idAI::Event_TravelDistanceBetweenPoints
     ================
     */
    protected fun Event_TravelDistanceBetweenPoints(source: idEventArg<idVec3>, dest: idEventArg<idVec3>) {
        val time: Float
        time = TravelDistance(source.value, dest.value)
        idThread.ReturnFloat(time)
    }

    /*
     ================
     idAI::Event_TravelDistanceBetweenEntities
     ================
     */
    protected fun Event_TravelDistanceBetweenEntities(
        source: idEventArg<idEntity>,
        dest: idEventArg<idEntity>
    ) {
        val time: Float
        assert(source.value != null)
        assert(dest.value != null)
        time = TravelDistance(source.value.GetPhysics().GetOrigin(), dest.value.GetPhysics().GetOrigin())
        idThread.ReturnFloat(time)
    }

    /*
     ================
     idAI::Event_LookAtEntity
     ================
     */
    protected fun Event_LookAtEntity(_ent: idEventArg<idEntity>, duration: idEventArg<Float>) {
        var ent: idEntity? = _ent.value
        // FIX: C++ sets ent = NULL when looking at self, then falls through to the second if.
        // Was using else-if (mutually exclusive) and had the null assignment commented out.
        if (ent === this) {
            ent = null
        }
        if (ent !== focusEntity.GetEntity() || focusTime < Game_local.gameLocal.time) {
            focusEntity.oSet(ent)
            alignHeadTime = Game_local.gameLocal.time
            forceAlignHeadTime = (Game_local.gameLocal.time + SEC2MS(1.0f)).toInt()
            blink_time = 0
        }
        focusTime = (Game_local.gameLocal.time + SEC2MS(duration.value)).toInt()
    }

    /*
     ================
     idAI::Event_LookAtEnemy
     ================
     */
    protected fun Event_LookAtEnemy(duration: idEventArg<Float>) {
        val enemyEnt: idActor?
        enemyEnt = enemy.GetEntity()
        if (enemyEnt != focusEntity.GetEntity() || focusTime < Game_local.gameLocal.time) {
            focusEntity.oSet(enemyEnt)
            alignHeadTime = Game_local.gameLocal.time
            forceAlignHeadTime = (Game_local.gameLocal.time + SEC2MS(1.0f)).toInt()
            blink_time = 0
        }
        focusTime = (Game_local.gameLocal.time + SEC2MS(duration.value)).toInt()
    }

    /*
     ================
     idAI::Event_SetJointMod
     ================
     */
    protected fun Event_SetJointMod(allow: idEventArg<Int>) {
        allowJointMod = TempDump.itob(allow.value)
    }

    /*
     ================
     idAI::Event_ThrowMoveable
     ================
     */
    protected fun Event_ThrowMoveable() {
        var ent: idEntity?
        var moveable: idEntity? = null
        ent = GetNextTeamEntity()
        while (ent != null) {
            if (ent.GetBindMaster() == this && ent is idMoveable) {
                moveable = ent
                break
            }
            ent = ent.GetNextTeamEntity()
        }
        if (moveable != null) {
            moveable.Unbind()
            moveable.PostEventMS(EV_SetOwner, 200, null)
        }
    }

    /*
     ================
     idAI::Event_ThrowAF
     ================
     */
    protected fun Event_ThrowAF() {
        var ent: idEntity?
        var af: idEntity? = null
        ent = GetNextTeamEntity()
        while (ent != null) {
            if (ent.GetBindMaster() == this && ent is idAFEntity_Base) {
                af = ent
                break
            }
            ent = ent.GetNextTeamEntity()
        }
        if (af != null) {
            af.Unbind()
            af.PostEventMS(EV_SetOwner, 200, null)
        }
    }

    /*
     ================
     idAI::Event_SetAngles
     ================
     */
    protected fun Event_SetAngles(ang: idEventArg<idAngles>) {
        current_yaw = ang.value.yaw
        viewAxis.set(idAngles(0.0f, current_yaw, 0.0f).ToMat3())
    }

    /*
     ================
     idAI::Event_GetAngles
     ================
     */
    protected fun Event_GetAngles() {
        idThread.ReturnVector(idVec3(0.0f, current_yaw, 0.0f))
    }

    /*
     ================
     idAI::Event_RealKill
     ================
     */
    protected fun Event_RealKill() {
        health = 0
        if (af.IsLoaded()) {
            // clear impacts
            af.Rest()

            // physics is turned off by calling af.Rest()
            BecomeActive(TH_PHYSICS)
        }
        Killed(this, this, 0, vec3_zero, Model.INVALID_JOINT)
    }

    /*
     ================
     idAI::Event_Kill
     ================
     */
    protected fun Event_Kill() {
        PostEventMS(AI_RealKill, 0)
    }

    /*
     ================
     idAI::Event_WakeOnFlashlight
     ================
     */
    protected fun Event_WakeOnFlashlight(enable: idEventArg<Int>) {
        wakeOnFlashlight = enable.value != 0
    }

    /*
     ================
     idAI::Event_LocateEnemy
     ================
     */
    protected fun Event_LocateEnemy() {
        val enemyEnt: idActor?
        val areaNum = CInt()
        enemyEnt = enemy.GetEntity()
        if (null == enemyEnt) {
            return
        }
        enemyEnt.GetAASLocation(aas, lastReachableEnemyPos, areaNum)
        SetEnemyPosition()
        UpdateEnemyPosition()
    }

    /*
     ================
     idAI::Event_KickObstacles
     ================
     */
    protected fun Event_KickObstacles(kickEnt: idEventArg<idEntity>, force: idEventArg<Float>) {
        val dir = idVec3()
        val obEnt: idEntity?
        obEnt = if (kickEnt.value != null) {
            kickEnt.value
        } else {
            move.obstacle.GetEntity()
        }
        if (obEnt != null) {
            dir.set(obEnt.GetPhysics().GetOrigin().minus(physicsObj.GetOrigin()))
            dir.Normalize()
        } else {
            dir.set(viewAxis[0])
        }
        KickObstacles(dir, force.value, obEnt)
    }

    /*
     ================
     idAI::Event_GetObstacle
     ================
     */
    protected fun Event_GetObstacle() {
        idThread.ReturnEntity(move.obstacle.GetEntity())
    }

    /*
     ================
     idAI::Event_PushPointIntoAAS
     ================
     */
    protected fun Event_PushPointIntoAAS(_pos: idEventArg<idVec3>) {
        val pos = idVec3(_pos.value)
        val newPos = idVec3()
        val areaNum: Int
        areaNum = PointReachableAreaNum(pos)
        if (areaNum != 0) {
            newPos.set(pos)
            aas!!.PushPointIntoAreaNum(areaNum, newPos)
            idThread.ReturnVector(newPos)
        } else {
            idThread.ReturnVector(pos)
        }
    }

    /*
     ================
     idAI::Event_GetTurnRate
     ================
     */
    protected fun Event_GetTurnRate() {
        idThread.ReturnFloat(turnRate)
    }

    /*
     ================
     idAI::Event_SetTurnRate
     ================
     */
    protected fun Event_SetTurnRate(rate: idEventArg<Float>) {
        turnRate = rate.value
    }

    /*
     ================
     idAI::Event_AnimTurn
     ================
     */
    protected fun Event_AnimTurn(angles: idEventArg<Float>) {
        turnVel = 0.0f
        anim_turn_angles = angles.value
        if (angles.value != 0.0f) {
            anim_turn_yaw = current_yaw
            anim_turn_amount = abs(idMath.AngleNormalize180(current_yaw - ideal_yaw))
            if (anim_turn_amount > anim_turn_angles) {
                anim_turn_amount = anim_turn_angles
            }
        } else {
            anim_turn_amount = 0.0f
            animator.CurrentAnim(Anim.ANIMCHANNEL_LEGS).SetSyncedAnimWeight(0, 1.0f)
            animator.CurrentAnim(Anim.ANIMCHANNEL_LEGS).SetSyncedAnimWeight(1, 0.0f)
            animator.CurrentAnim(Anim.ANIMCHANNEL_TORSO).SetSyncedAnimWeight(0, 1.0f)
            animator.CurrentAnim(Anim.ANIMCHANNEL_TORSO).SetSyncedAnimWeight(1, 0.0f)
        }
    }

    /*
     ================
     idAI::Event_AllowHiddenMovement
     ================
     */
    protected fun Event_AllowHiddenMovement(enable: idEventArg<Int>) {
        allowHiddenMovement = enable.value != 0
    }

    /*
     ================
     idAI::Event_TriggerParticles
     ================
     */
    protected fun Event_TriggerParticles(jointName: idEventArg<String>) {
        TriggerParticles(jointName.value)
    }

    /*
     ================
     idAI::Event_FindActorsInBounds
     ================
     */
    protected fun Event_FindActorsInBounds(mins: idEventArg<idVec3>, maxs: idEventArg<idVec3>) {
        var ent: idEntity
        val entityList = arrayOfNulls<idEntity?>(Game_local.MAX_GENTITIES)
        val numListedEntities: Int
        var i: Int
        numListedEntities = Game_local.gameLocal.clip.EntitiesTouchingBounds(
            idBounds(mins.value, maxs.value),
            Material.CONTENTS_BODY,
            entityList,
            Game_local.MAX_GENTITIES
        )
        i = 0
        while (i < numListedEntities) {
            ent = entityList[i]!!
            if (ent != this && !ent.IsHidden() && ent.health > 0 && ent is idActor) {
                idThread.ReturnEntity(ent)
                return
            }
            i++
        }
        idThread.ReturnEntity(null)
    }

    /*
     ================
     idAI::Event_CanReachPosition
     ================
     */
    protected fun Event_CanReachPosition(pos: idEventArg<idVec3>) {
        val path = AAS.aasPath_s()
        val toAreaNum: Int
        val areaNum: Int
        toAreaNum = PointReachableAreaNum(pos.value)
        areaNum = PointReachableAreaNum(physicsObj.GetOrigin())
        idThread.ReturnInt(
            0 != toAreaNum && PathToGoal(
                path,
                areaNum,
                physicsObj.GetOrigin(),
                toAreaNum,
                pos.value
            )
        )
    }

    /*
     ================
     idAI::Event_CanReachEntity
     ================
     */
    protected fun Event_CanReachEntity(_ent: idEventArg<idEntity>) {
        val ent = _ent.value
        val path = AAS.aasPath_s()
        val toAreaNum: Int
        val areaNum: Int
        val pos = idVec3()
        if (null == ent) {
            idThread.ReturnInt(false)
            return
        }
        if (move.moveType != moveType_t.MOVETYPE_FLY) {
            if (!ent.GetFloorPos(64.0f, pos)) {
                idThread.ReturnInt(false)
                return
            }
            if (ent is idActor && (ent as idActor).OnLadder()) {
                idThread.ReturnInt(false)
                return
            }
        } else {
            pos.set(ent.GetPhysics().GetOrigin())
        }
        toAreaNum = PointReachableAreaNum(pos)
        if (0 == toAreaNum) {
            idThread.ReturnInt(false)
            return
        }
        val org = physicsObj.GetOrigin()
        areaNum = PointReachableAreaNum(org)
        idThread.ReturnInt(0 != toAreaNum && PathToGoal(path, areaNum, org, toAreaNum, pos))
    }

    /*
     ================
     idAI::Event_CanReachEnemy
     ================
     */
    protected fun Event_CanReachEnemy() {
        val path = AAS.aasPath_s()
        val toAreaNum = CInt()
        val areaNum: Int
        val pos = idVec3()
        val enemyEnt: idActor?
        enemyEnt = enemy.GetEntity()
        if (null == enemyEnt) {
            idThread.ReturnInt(false)
            return
        }
        if (move.moveType != moveType_t.MOVETYPE_FLY) {
            if (enemyEnt.OnLadder()) {
                idThread.ReturnInt(false)
                return
            }
            enemyEnt.GetAASLocation(aas, pos, toAreaNum)
        } else {
            pos.set(enemyEnt.GetPhysics().GetOrigin())
            toAreaNum.integerValue = (PointReachableAreaNum(pos))
        }
        if (0 == toAreaNum.integerValue) {
            idThread.ReturnInt(false)
            return
        }
        val org = physicsObj.GetOrigin()
        areaNum = PointReachableAreaNum(org)
        idThread.ReturnInt(PathToGoal(path, areaNum, org, toAreaNum.integerValue, pos))
    }

    /*
     ================
     idAI::Event_GetReachableEntityPosition
     ================
     */
    protected fun Event_GetReachableEntityPosition(_ent: idEventArg<idEntity>) {
        val ent = _ent.value
        val toAreaNum: Int
        val pos = idVec3()
        if (move.moveType != moveType_t.MOVETYPE_FLY) {
            if (!ent.GetFloorPos(64.0f, pos)) {
                // FIX: Was missing return; C++ uses `return idThread::ReturnVector(vec3_zero)`
                idThread.ReturnVector(vec3_zero)
                return
            }
            if (ent is idActor && (ent as idActor).OnLadder()) {
                // FIX: Was missing return; C++ uses `return idThread::ReturnVector(vec3_zero)`
                idThread.ReturnVector(vec3_zero)
                return
            }
        } else {
            pos.set(ent.GetPhysics().GetOrigin())
        }
        if (aas != null) {
            toAreaNum = PointReachableAreaNum(pos)
            aas!!.PushPointIntoAreaNum(toAreaNum, pos)
        }
        idThread.ReturnVector(pos)
    }

    override fun oSet(oGet: idClass?) {
        throw UnsupportedOperationException("Not supported yet.")
    }

    override fun GetType(): idTypeInfo = Type
    override fun CreateInstance(): idClass = idAI()

    override fun getEventCallBack(event: idEventDef): eventCallback_t<*>? {
        return eventCallbacks[event]
    }

    /*
     =====================
     idAI::~idAI
     =====================
     */
    override fun _deconstructor() {
        if (projectileClipModel != null) idClipModel.delete(projectileClipModel!!)
        DeconstructScriptObject()
        scriptObject.Free()
        if (worldMuzzleFlashHandle != -1) {
            Game_local.gameRenderWorld!!.FreeLightDef(worldMuzzleFlashHandle)
            worldMuzzleFlashHandle = -1
        }
        super._deconstructor()
    }

    /*
     ===================
     idAI::List_f
     ===================
     */
    // Outputs a list of all monsters to the console.
    class List_f private constructor() : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            var e: Int
            var check: idEntity?
            var count: Int
            var statename: String?
            count = 0
            Game_local.gameLocal.Printf("%-4s  %-20s %s\n", " Num", "EntityDef", "Name")
            Game_local.gameLocal.Printf("------------------------------------------------\n")
            e = 0
            while (e < Game_local.MAX_GENTITIES) {
                check = Game_local.gameLocal.entities[e]
                if (null == check || check !is idAI) {
                    e++
                    continue
                }
                statename = if (check.state != null) {
                    check.state!!.Name()
                } else {
                    "NULL state"
                }
                Game_local.gameLocal.Printf(
                    "%4d: %-20s %-20s %s  move: %d\n",
                    e,
                    check.GetEntityDefName(),
                    check.name,
                    statename,
                    btoi(check.allowMove)
                )
                count++
                e++
            }
            Game_local.gameLocal.Printf("...%d monsters\n", count)
        }

        companion object {
            private val instance: cmdFunction_t = List_f()
            fun getInstance(): cmdFunction_t {
                return instance
            }
        }
    }

    //
    //
    init {
        travelFlags = AASFile.TFL_WALK or AASFile.TFL_AIR
        move = idMoveState()
        kickForce = 2048.0f
        ignore_obstacles = false
        blockedRadius = 0.0f
        blockedMoveTime = 750
        blockedAttackTime = 750
        turnRate = 360.0f
        turnVel = 0.0f
        anim_turn_yaw = 0.0f
        anim_turn_amount = 0.0f
        anim_turn_angles = 0.0f
        physicsObj = idPhysics_Monster()
        fly_offset = 0
        fly_seek_scale = 1.0f
        fly_roll_scale = 0.0f
        fly_roll_max = 0.0f
        fly_roll = 0.0f
        fly_pitch_scale = 0.0f
        fly_pitch_max = 0.0f
        fly_pitch = 0.0f
        allowMove = false
        allowHiddenMovement = false
        fly_speed = 0.0f
        fly_bob_strength = 0.0f
        fly_bob_vert = 0.0f
        fly_bob_horz = 0.0f
        lastHitCheckResult = false
        lastHitCheckTime = 0
        lastAttackTime = 0
        melee_range = 0.0f
        projectile_height_to_distance_ratio = 1.0f
        missileLaunchOffset = idList()
        projectileDef = null
        projectile = idEntityPtr()
        projectileClipModel = null
        projectileRadius = 0.0f
        projectileVelocity = vec3_origin
        projectileGravity = vec3_origin
        projectileSpeed = 0.0f
        chat_snd = null
        chat_min = 0
        chat_max = 0
        chat_time = 0
        talk_state = talkState_t.TALK_NEVER
        talkTarget = idEntityPtr()
        particles = idList()
        restartParticles = true
        useBoneAxis = false
        wakeOnFlashlight = false
        worldMuzzleFlash = renderLight_s() //memset( &worldMuzzleFlash, 0, sizeof ( worldMuzzleFlash ) );
        worldMuzzleFlashHandle = -1
        lastVisibleEnemyPos = idVec3()
        lastVisibleEnemyEyeOffset = idVec3()
        lastVisibleReachableEnemyPos = idVec3()
        lastReachableEnemyPos = idVec3()
        shrivel_rate = 0.0f
        shrivel_start = 0
        fl.neverDormant = false // AI's can go dormant
        current_yaw = 0.0f
        ideal_yaw = 0.0f
        num_cinematics = 0
        current_cinematic = 0
        allowEyeFocus = true
        allowPain = true
        allowJointMod = true
        focusEntity = idEntityPtr()
        focusTime = 0
        alignHeadTime = 0
        forceAlignHeadTime = 0
        currentFocusPos = idVec3()
        eyeAng.set(idAngles())
        lookAng.set(idAngles())
        destLookAng.set(idAngles())
        lookMin.set(idAngles())
        lookMax.set(idAngles())
        lookJoints = idList()
        lookJointAngles = idList()
        eyeMin.set(idAngles())
        eyeMax.set(idAngles())
        muzzleFlashEnd = 0
        flashTime = 0
        flashJointWorld = Model.INVALID_JOINT
        focusJoint = Model.INVALID_JOINT
        orientationJoint = Model.INVALID_JOINT
        flyTiltJoint = Model.INVALID_JOINT
        eyeVerticalOffset = 0.0f
        eyeHorizontalOffset = 0.0f
        eyeFocusRate = 0.0f
        headFocusRate = 0.0f
        focusAlignTime = 0
        AI_TALK = idScriptBool()
        AI_DAMAGE = idScriptBool()
        AI_PAIN = idScriptBool()
        AI_SPECIAL_DAMAGE = idScriptFloat()
        AI_DEAD = idScriptBool()
        AI_ENEMY_VISIBLE = idScriptBool()
        AI_ENEMY_IN_FOV = idScriptBool()
        AI_ENEMY_DEAD = idScriptBool()
        AI_MOVE_DONE = idScriptBool()
        AI_ONGROUND = idScriptBool()
        AI_ACTIVATED = idScriptBool()
        AI_FORWARD = idScriptBool()
        AI_JUMP = idScriptBool()
        AI_ENEMY_REACHABLE = idScriptBool()
        AI_BLOCKED = idScriptBool()
        AI_OBSTACLE_IN_PATH = idScriptBool()
        AI_DEST_UNREACHABLE = idScriptBool()
        AI_HIT_ENEMY = idScriptBool()
        AI_PUSHED = idScriptBool()
    }
}

class idCombatNode : idEntity() {

    companion object {
        val Type = idTypeInfo("idCombatNode", "idEntity") { idCombatNode() }

        // CLASS_PROTOTYPE( idCombatNode );
        private val eventCallbacks: MutableMap<idEventDef, eventCallback_t<*>> = HashMap()

        /*
         =====================
         idCombatNode::DrawDebugInfo
         =====================
         */
        fun DrawDebugInfo() {
            var ent: idEntity?
            var node: idCombatNode?
            val player = Game_local.gameLocal.GetLocalPlayer()
            val color = idVec4()
            val bounds = idBounds(idVec3(-16.0f, -16.0f, 0.0f), idVec3(16.0f, 16.0f, 0.0f))
            ent = Game_local.gameLocal.spawnedEntities.Next()
            while (ent != null) {
                if (ent !is idCombatNode) {
                    ent = ent.spawnNode.Next()
                    continue
                }
                node = ent
                color.set(
                    if (node.disabled) {
                        colorMdGrey
                    } else if (player != null && node.EntityInView(player, player.GetPhysics().GetOrigin())) {
                        colorYellow
                    } else {
                        colorRed
                    }
                )
                val leftDir = idVec3(-node.cone_left.y, node.cone_left.x, 0.0f)
                val rightDir = idVec3(node.cone_right.y, -node.cone_right.x, 0.0f)
                val org = idVec3(node.GetPhysics().GetOrigin() + node.offset)
                bounds[1].z = node.max_height
                leftDir.NormalizeFast()
                rightDir.NormalizeFast()
                val axis = node.GetPhysics().GetAxis()
                val cone_dot = node.cone_right.times(axis[1])
                if (abs(cone_dot) > 0.1f) {
                    val cone_dist = node.max_dist / cone_dot
                    val pos1 = org + leftDir * node.min_dist
                    val pos2 = org + leftDir * cone_dist
                    val pos3 = org + rightDir * node.min_dist
                    val pos4 = org + rightDir * cone_dist
                    Game_local.gameRenderWorld!!.DebugLine(
                        color,
                        node.GetPhysics().GetOrigin(),
                        pos1.plus(pos3).times(0.5f),
                        idGameLocal.msec
                    )
                    Game_local.gameRenderWorld!!.DebugLine(color, pos1, pos2, idGameLocal.msec)
                    Game_local.gameRenderWorld!!.DebugLine(color, pos1, pos3, idGameLocal.msec)
                    Game_local.gameRenderWorld!!.DebugLine(color, pos3, pos4, idGameLocal.msec)
                    Game_local.gameRenderWorld!!.DebugLine(color, pos2, pos4, idGameLocal.msec)
                    Game_local.gameRenderWorld!!.DebugBounds(color, bounds, org, idGameLocal.msec)
                }
                ent = ent.spawnNode.Next()
            }
        }

        fun getEventCallBacks(): MutableMap<idEventDef, eventCallback_t<*>> {
            return eventCallbacks
        }

        init {
            eventCallbacks.putAll(idEntity.getEventCallBacks())
            eventCallbacks[EV_CombatNode_MarkUsed] =
                eventCallback_t0<idCombatNode> { obj: idCombatNode -> obj.Event_MarkUsed() }
            eventCallbacks[EV_Activate] =
                eventCallback_t1<idCombatNode> { obj: idCombatNode, activator: idEventArg<*>? ->
                    obj.Event_Activate(activator as idEventArg<idEntity>)
                }
        }
    }

    private val cone_left: idVec3
    private val cone_right: idVec3
    private val offset: idVec3
    private var cone_dist = 0.0f
    private var disabled: Boolean
    private var max_dist = 0.0f
    private var max_height = 0.0f
    private var min_dist = 0.0f
    private var min_height = 0.0f

    /*
     =====================
     idCombatNode::Save
     =====================
     */
    override fun Save(savefile: idSaveGame) {
        super.Save(savefile)
        savefile.WriteFloat(min_dist)
        savefile.WriteFloat(max_dist)
        savefile.WriteFloat(cone_dist)
        savefile.WriteFloat(min_height)
        savefile.WriteFloat(max_height)
        savefile.WriteVec3(cone_left)
        savefile.WriteVec3(cone_right)
        savefile.WriteVec3(offset)
        savefile.WriteBool(disabled)
    }

    /*
     =====================
     idCombatNode::Restore
     =====================
     */
    override fun Restore(savefile: idRestoreGame) {
        super.Restore(savefile)
        min_dist = savefile.ReadFloat()
        max_dist = savefile.ReadFloat()
        cone_dist = savefile.ReadFloat()
        min_height = savefile.ReadFloat()
        max_height = savefile.ReadFloat()
        savefile.ReadVec3(cone_left)
        savefile.ReadVec3(cone_right)
        savefile.ReadVec3(offset)
        disabled = savefile.ReadBool()
    }

    /*
     =====================
     idCombatNode::Spawn
     =====================
     */
    override fun Spawn() {
        super.Spawn()
        val fov: Float
        val yaw: Float
        val height: Float
        min_dist = spawnArgs.GetFloat("min")
        max_dist = spawnArgs.GetFloat("max")
        height = spawnArgs.GetFloat("height")
        fov = spawnArgs.GetFloat("fov", "60")
        offset.set(spawnArgs.GetVector("offset"))
        val org = GetPhysics().GetOrigin() + offset
        min_height = org.z - height * 0.5f
        max_height = min_height + height
        val axis = GetPhysics().GetAxis()
        yaw = axis[0].ToYaw()
        val leftang = idAngles(0.0f, yaw + fov * 0.5f - 90.0f, 0.0f)
        cone_left.set(leftang.ToForward())
        val rightang = idAngles(0.0f, yaw - fov * 0.5f + 90.0f, 0.0f)
        cone_right.set(rightang.ToForward())
        disabled = spawnArgs.GetBool("start_off")
    }

    /*
     =====================
     idCombatNode::IsDisabled
     =====================
     */
    fun IsDisabled(): Boolean {
        return disabled
    }

    /*
     =====================
     idCombatNode::EntityInView
     =====================
     */
    fun EntityInView(actor: idActor, pos: idVec3): Boolean {
        if (null == actor || actor.health <= 0) {
            return false
        }
        val bounds = actor.GetPhysics().GetBounds()
        if (pos.z + bounds[1].z < min_height || pos.z + bounds[0].z >= max_height) {
            return false
        }
        val org = GetPhysics().GetOrigin() + offset
        val axis = GetPhysics().GetAxis()
        val dir = pos - org
        val dist = dir * axis[0]
        if (dist < min_dist || dist > max_dist) {
            return false
        }
        val left_dot = dir * cone_left
        if (left_dot < 0.0f) {
            return false
        }
        val right_dot = dir * cone_right
        return right_dot >= 0.0f
    }

    /*
     =====================
     idCombatNode::Event_Activate
     =====================
     */
    private fun Event_Activate(activator: idEventArg<idEntity>) {
        disabled = !disabled
    }

    /*
     =====================
     idCombatNode::Event_MarkUsed
     =====================
     */
    private fun Event_MarkUsed() {
        if (spawnArgs.GetBool("use_once")) {
            disabled = true
        }
    }

    override fun oSet(oGet: idClass?) {
        throw UnsupportedOperationException("Not supported yet.")
    }

    override fun GetType(): idTypeInfo = Type
    override fun CreateInstance(): idClass = idCombatNode()

    override fun getEventCallBack(event: idEventDef): eventCallback_t<*>? {
        return eventCallbacks[event]
    }

    //
    //
    init {
        cone_left = idVec3()
        cone_right = idVec3()
        offset = idVec3()
        disabled = false
    }
}

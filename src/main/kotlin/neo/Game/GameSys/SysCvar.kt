/*
 * Copyright (C) 1999-2011 id Software LLC, a ZeniMax Media company.
 * Translated to Kotlin by Dr. Feederino with support of Claude Code
 *
 * This file is part of the Doom 3 Kotlin project.
 * Original source: neo/game/gamesys/SysCvar.h, neo/game/gamesys/SysCvar.cpp
 *
 * Doom 3 Source Code is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package neo.Game.GameSys

import neo.Game.Game_local
import neo.Game.MultiplayerGame
import neo.framework.BUILD_NUMBER
import neo.framework.CVarSystem
import neo.framework.CVarSystem.idCVar
import neo.framework.CmdSystem.idCmdSystem.*
import neo.framework.Licensee
import neo.sys.BUILD_STRING
import java.util.*

class SysCvar {

    class gameVersion_s {
        val string: String

        init {
            string = String.format(
                "%s.%d%s %s %s", Licensee.ENGINE_VERSION, BUILD_NUMBER, BUILD_DEBUG, BUILD_STRING(), __DATE__
            )
        }
    }

    init {
        if (_DEBUG) {
            BUILD_DEBUG = "-debug"
        } else {
            BUILD_DEBUG = "-release"
        }
    }

    companion object {
        const val _DEBUG = false
        val __DATE__: String = java.text.SimpleDateFormat("MMM dd yyyy HH:mm:ss", Locale.US).format(Date())
        val aas_goalArea: idCVar = idCVar("aas_goalArea", "0", CVarSystem.CVAR_GAME or CVarSystem.CVAR_INTEGER, "")
        val aas_pullPlayer: idCVar = idCVar("aas_pullPlayer", "0", CVarSystem.CVAR_GAME or CVarSystem.CVAR_INTEGER, "")
        val aas_randomPullPlayer: idCVar =
            idCVar("aas_randomPullPlayer", "0", CVarSystem.CVAR_GAME or CVarSystem.CVAR_BOOL, "")
        val aas_showAreas: idCVar = idCVar("aas_showAreas", "0", CVarSystem.CVAR_GAME or CVarSystem.CVAR_BOOL, "")
        val aas_showFlyPath: idCVar =
            idCVar("aas_showFlyPath", "0", CVarSystem.CVAR_GAME or CVarSystem.CVAR_INTEGER, "")
        val aas_showHideArea: idCVar =
            idCVar("aas_showHideArea", "0", CVarSystem.CVAR_GAME or CVarSystem.CVAR_INTEGER, "")
        val aas_showPath: idCVar = idCVar("aas_showPath", "0", CVarSystem.CVAR_GAME or CVarSystem.CVAR_INTEGER, "")
        val aas_showPushIntoArea: idCVar =
            idCVar("aas_showPushIntoArea", "0", CVarSystem.CVAR_GAME or CVarSystem.CVAR_BOOL, "")

        //
        val aas_showWallEdges: idCVar =
            idCVar("aas_showWallEdges", "0", CVarSystem.CVAR_GAME or CVarSystem.CVAR_BOOL, "")

        //
        val aas_test: idCVar = idCVar("aas_test", "0", CVarSystem.CVAR_GAME or CVarSystem.CVAR_INTEGER, "")
        val af_contactFrictionScale: idCVar = idCVar(
            "af_contactFrictionScale", "0", CVarSystem.CVAR_GAME or CVarSystem.CVAR_FLOAT, "scales the contact friction"
        )
        val af_forceFriction: idCVar = idCVar(
            "af_forceFriction", "-1", CVarSystem.CVAR_GAME or CVarSystem.CVAR_FLOAT, "force the given friction value"
        )
        val af_highlightBody: idCVar =
            idCVar("af_highlightBody", "", CVarSystem.CVAR_GAME, "name of the body to highlight")
        val af_highlightConstraint: idCVar =
            idCVar("af_highlightConstraint", "", CVarSystem.CVAR_GAME, "name of the constraint to highlight")
        val af_jointFrictionScale: idCVar = idCVar(
            "af_jointFrictionScale", "0", CVarSystem.CVAR_GAME or CVarSystem.CVAR_FLOAT, "scales the joint friction"
        )
        val af_maxAngularVelocity: idCVar = idCVar(
            "af_maxAngularVelocity", "1.57", CVarSystem.CVAR_GAME or CVarSystem.CVAR_FLOAT, "maximum angular velocity"
        )
        val af_maxLinearVelocity: idCVar = idCVar(
            "af_maxLinearVelocity", "128", CVarSystem.CVAR_GAME or CVarSystem.CVAR_FLOAT, "maximum linear velocity"
        )
        val af_showActive: idCVar = idCVar(
            "af_showActive",
            "0",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_BOOL,
            "show tree-like structures of articulated figures not at rest"
        )
        val af_showBodies: idCVar =
            idCVar("af_showBodies", "0", CVarSystem.CVAR_GAME or CVarSystem.CVAR_BOOL, "show bodies")
        val af_showBodyNames: idCVar =
            idCVar("af_showBodyNames", "0", CVarSystem.CVAR_GAME or CVarSystem.CVAR_BOOL, "show body names")
        val af_showConstrainedBodies: idCVar = idCVar(
            "af_showConstrainedBodies",
            "0",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_BOOL,
            "show the two bodies contrained by the highlighted constraint"
        )
        val af_showConstraintNames: idCVar =
            idCVar("af_showConstraintNames", "0", CVarSystem.CVAR_GAME or CVarSystem.CVAR_BOOL, "show constraint names")
        val af_showConstraints: idCVar =
            idCVar("af_showConstraints", "0", CVarSystem.CVAR_GAME or CVarSystem.CVAR_BOOL, "show constraints")
        val af_showInertia: idCVar = idCVar(
            "af_showInertia", "0", CVarSystem.CVAR_GAME or CVarSystem.CVAR_BOOL, "show the inertia tensor of each body"
        )
        val af_showLimits: idCVar =
            idCVar("af_showLimits", "0", CVarSystem.CVAR_GAME or CVarSystem.CVAR_BOOL, "show joint limits")
        val af_showMass: idCVar =
            idCVar("af_showMass", "0", CVarSystem.CVAR_GAME or CVarSystem.CVAR_BOOL, "show the mass of each body")
        val af_showPrimaryOnly: idCVar = idCVar(
            "af_showPrimaryOnly", "0", CVarSystem.CVAR_GAME or CVarSystem.CVAR_BOOL, "show primary constraints only"
        )
        val af_showTimings: idCVar = idCVar(
            "af_showTimings", "0", CVarSystem.CVAR_GAME or CVarSystem.CVAR_BOOL, "show articulated figure cpu usage"
        )
        val af_showTotalMass: idCVar = idCVar(
            "af_showTotalMass",
            "0",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_BOOL,
            "show the total mass of each articulated figure"
        )
        val af_showTrees: idCVar =
            idCVar("af_showTrees", "0", CVarSystem.CVAR_GAME or CVarSystem.CVAR_BOOL, "show tree-like structures")
        val af_showVelocity: idCVar = idCVar(
            "af_showVelocity", "0", CVarSystem.CVAR_GAME or CVarSystem.CVAR_BOOL, "show the velocity of each body"
        )
        val af_skipFriction: idCVar =
            idCVar("af_skipFriction", "0", CVarSystem.CVAR_GAME or CVarSystem.CVAR_BOOL, "skip friction")
        val af_skipLimits: idCVar =
            idCVar("af_skipLimits", "0", CVarSystem.CVAR_GAME or CVarSystem.CVAR_BOOL, "skip joint limits")
        val af_skipSelfCollision: idCVar = idCVar(
            "af_skipSelfCollision", "0", CVarSystem.CVAR_GAME or CVarSystem.CVAR_BOOL, "skip self collision detection"
        )
        val af_testSolid: idCVar = idCVar(
            "af_testSolid",
            "1",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_BOOL,
            "test for bodies initially stuck in solid"
        )
        val af_timeScale: idCVar =
            idCVar("af_timeScale", "1", CVarSystem.CVAR_GAME or CVarSystem.CVAR_FLOAT, "scales the time")
        val af_useImpulseFriction: idCVar = idCVar(
            "af_useImpulseFriction",
            "0",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_BOOL,
            "use impulse based contact friction"
        )
        val af_useJointImpulseFriction: idCVar = idCVar(
            "af_useJointImpulseFriction",
            "0",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_BOOL,
            "use impulse based joint friction"
        )

        //
        val af_useLinearTime: idCVar = idCVar(
            "af_useLinearTime",
            "1",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_BOOL,
            "use linear time algorithm for tree-like structures"
        )
        val af_useSymmetry: idCVar = idCVar(
            "af_useSymmetry", "1", CVarSystem.CVAR_GAME or CVarSystem.CVAR_BOOL, "use constraint matrix symmetry"
        )
        val ai_blockedFailSafe: idCVar = idCVar(
            "ai_blockedFailSafe", "1", CVarSystem.CVAR_GAME or CVarSystem.CVAR_BOOL, "enable blocked fail safe handling"
        )
        val ai_debugMove: idCVar = idCVar(
            "ai_debugMove", "0", CVarSystem.CVAR_GAME or CVarSystem.CVAR_BOOL, "draws movement information for monsters"
        )

        //
        val ai_debugScript: idCVar = idCVar(
            "ai_debugScript",
            "-1",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_INTEGER,
            "displays script calls for the specified monster entity number"
        )
        val ai_debugTrajectory: idCVar = idCVar(
            "ai_debugTrajectory",
            "0",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_BOOL,
            "draws trajectory tests for monsters"
        )
        val ai_showCombatNodes: idCVar = idCVar(
            "ai_showCombatNodes", "0", CVarSystem.CVAR_GAME or CVarSystem.CVAR_BOOL, "draws attack cones for monsters"
        )
        val ai_showObstacleAvoidance: idCVar = idCVar(
            "ai_showObstacleAvoidance",
            "0",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_INTEGER,
            "draws obstacle avoidance information for monsters.  if 2, draws obstacles for player, as well",
            0.0f,
            2.0f,
            ArgCompletion_Integer(0, 2)
        )
        val ai_showPaths: idCVar =
            idCVar("ai_showPaths", "0", CVarSystem.CVAR_GAME or CVarSystem.CVAR_BOOL, "draws path_* entities")
        val ai_testPredictPath: idCVar =
            idCVar("ai_testPredictPath", "0", CVarSystem.CVAR_GAME or CVarSystem.CVAR_BOOL, "")

        //
        // change anytime vars
        val g_TDMArrows: idCVar = idCVar(
            "g_TDMArrows",
            "1",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_NETWORKSYNC or CVarSystem.CVAR_BOOL,
            "draw arrows over teammates in team deathmatch"
        )
        val g_armorProtection: idCVar = idCVar(
            "g_armorProtection",
            "0.3",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_FLOAT or CVarSystem.CVAR_ARCHIVE,
            "armor takes this percentage of damage"
        )
        val g_armorProtectionMP: idCVar = idCVar(
            "g_armorProtectionMP",
            "0.6",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_FLOAT or CVarSystem.CVAR_ARCHIVE,
            "armor takes this percentage of damage in mp"
        )
        val g_balanceTDM: idCVar =
            idCVar("g_balanceTDM", "1", CVarSystem.CVAR_GAME or CVarSystem.CVAR_BOOL, "maintain even teams")
        val g_blobSize: idCVar = idCVar("g_blobSize", "1", CVarSystem.CVAR_GAME or CVarSystem.CVAR_FLOAT, "")
        val g_blobTime: idCVar = idCVar("g_blobTime", "1", CVarSystem.CVAR_GAME or CVarSystem.CVAR_FLOAT, "")
        val g_bloodEffects: idCVar = idCVar(
            "g_bloodEffects",
            "1",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_ARCHIVE or CVarSystem.CVAR_BOOL,
            "show blood splats, sprays and gibs"
        )

        //
        val g_cinematic: idCVar = idCVar(
            "g_cinematic",
            "1",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_BOOL,
            "skips updating entities that aren't marked 'cinematic' '1' during cinematics"
        )
        val g_cinematicMaxSkipTime: idCVar = idCVar(
            "g_cinematicMaxSkipTime",
            "600",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_FLOAT,
            "# of seconds to allow game to run when skipping cinematic.  prevents lock-up when cinematic doesn't end.",
            0.0f,
            3600.0f
        )

        //
        val g_countDown: idCVar = idCVar(
            "g_countDown",
            "10",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_INTEGER or CVarSystem.CVAR_ARCHIVE,
            "pregame countdown in seconds",
            4.0f,
            3600.0f
        )
        val g_damageScale: idCVar = idCVar(
            "g_damageScale",
            "1",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_FLOAT or CVarSystem.CVAR_ARCHIVE,
            "scale final damage on player by this factor"
        )
        val g_debugAnim: idCVar = idCVar(
            "g_debugAnim",
            "-1",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_INTEGER,
            "displays information on which animations are playing on the specified entity number.  set to -1 to disable."
        )
        val g_debugBounds: idCVar = idCVar(
            "g_debugBounds", "0", CVarSystem.CVAR_GAME or CVarSystem.CVAR_BOOL, "checks for models with bounds > 2048"
        )
        val g_debugCinematic: idCVar = idCVar("g_debugCinematic", "0", CVarSystem.CVAR_GAME or CVarSystem.CVAR_BOOL, "")
        val g_debugDamage: idCVar = idCVar("g_debugDamage", "0", CVarSystem.CVAR_GAME or CVarSystem.CVAR_BOOL, "")
        val g_debugMove: idCVar = idCVar("g_debugMove", "0", CVarSystem.CVAR_GAME or CVarSystem.CVAR_BOOL, "")
        val g_debugMover: idCVar = idCVar("g_debugMover", "0", CVarSystem.CVAR_GAME or CVarSystem.CVAR_BOOL, "")
        val g_debugScript: idCVar = idCVar("g_debugScript", "0", CVarSystem.CVAR_GAME or CVarSystem.CVAR_BOOL, "")
        val g_debugTriggers: idCVar = idCVar("g_debugTriggers", "0", CVarSystem.CVAR_GAME or CVarSystem.CVAR_BOOL, "")
        val g_debugWeapon: idCVar = idCVar("g_debugWeapon", "0", CVarSystem.CVAR_GAME or CVarSystem.CVAR_BOOL, "")
        val g_decals: idCVar = idCVar(
            "g_decals",
            "1",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_ARCHIVE or CVarSystem.CVAR_BOOL,
            "show decals such as bullet holes"
        )

        //
        val g_disasm: idCVar = idCVar(
            "g_disasm",
            "0",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_BOOL,
            "disassemble script into base/script/disasm.txt on the local drive when script is compiled"
        )
        val g_doubleVision: idCVar = idCVar(
            "g_doubleVision",
            "1",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_ARCHIVE or CVarSystem.CVAR_BOOL,
            "show double vision when taking damage"
        )
        val g_dragDamping: idCVar = idCVar("g_dragDamping", "0.5f", CVarSystem.CVAR_GAME or CVarSystem.CVAR_FLOAT, "")
        val g_dragEntity: idCVar = idCVar(
            "g_dragEntity",
            "0",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_BOOL,
            "allows dragging physics objects around by placing the crosshair over them and holding the fire button"
        )
        val g_dragShowSelection: idCVar =
            idCVar("g_dragShowSelection", "0", CVarSystem.CVAR_GAME or CVarSystem.CVAR_BOOL, "")
        val g_dropItemRotation: idCVar = idCVar("g_dropItemRotation", "", CVarSystem.CVAR_GAME, "")
        val g_dvAmplitude: idCVar = idCVar("g_dvAmplitude", "0.001", CVarSystem.CVAR_GAME or CVarSystem.CVAR_FLOAT, "")
        val g_dvFrequency: idCVar = idCVar("g_dvFrequency", "0.5f", CVarSystem.CVAR_GAME or CVarSystem.CVAR_FLOAT, "")

        //
        val g_dvTime: idCVar = idCVar("g_dvTime", "1", CVarSystem.CVAR_GAME or CVarSystem.CVAR_FLOAT, "")
        val g_editEntityMode: idCVar = idCVar(
            "g_editEntityMode", "0", CVarSystem.CVAR_GAME or CVarSystem.CVAR_INTEGER, """
     0 = off
     1 = lights
     2 = sounds
     3 = articulated figures
     4 = particle systems
     5 = monsters
     6 = entity names
     7 = entity models
     """.trimIndent(), 0.0f, 7.0f, ArgCompletion_Integer(0, 7)
        )
        val g_exportMask: idCVar = idCVar("g_exportMask", "", CVarSystem.CVAR_GAME, "")
        val g_flushSave: idCVar = idCVar(
            "g_flushSave",
            "0",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_BOOL,
            "1 = don't buffer file writing for save games."
        )
        val g_fov: idCVar =
            idCVar("g_fov", "90", CVarSystem.CVAR_GAME or CVarSystem.CVAR_INTEGER or CVarSystem.CVAR_NOCHEAT, "")

        //
        val g_frametime: idCVar = idCVar(
            "g_frametime",
            "0",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_BOOL,
            "displays timing information for each game frame"
        )
        val g_gameReviewPause: idCVar = idCVar(
            "g_gameReviewPause",
            "10",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_NETWORKSYNC or CVarSystem.CVAR_INTEGER or CVarSystem.CVAR_ARCHIVE,
            "scores review time in seconds (at end game)",
            2.0f,
            3600.0f
        )
        val g_gravity: idCVar =
            idCVar("g_gravity", Game_local.DEFAULT_GRAVITY_STRING, CVarSystem.CVAR_GAME or CVarSystem.CVAR_FLOAT, "")
        val g_gun_x: idCVar = idCVar("g_gunX", "0", CVarSystem.CVAR_GAME or CVarSystem.CVAR_FLOAT, "")
        val g_gun_y: idCVar = idCVar("g_gunY", "0", CVarSystem.CVAR_GAME or CVarSystem.CVAR_FLOAT, "")
        val g_gun_z: idCVar = idCVar("g_gunZ", "0", CVarSystem.CVAR_GAME or CVarSystem.CVAR_FLOAT, "")
        val g_hitEffect: idCVar = idCVar(
            "g_hitEffect",
            "1",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_ARCHIVE or CVarSystem.CVAR_BOOL,
            "mess up player camera when taking damage"
        )

        val g_healthTakeAmt: idCVar = idCVar(
            "g_healthTakeAmt",
            "5",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_INTEGER or CVarSystem.CVAR_ARCHIVE,
            "how much health to take in nightmare mode"
        )
        val g_healthTakeLimit: idCVar = idCVar(
            "g_healthTakeLimit",
            "25",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_INTEGER or CVarSystem.CVAR_ARCHIVE,
            "how low can health get taken in nightmare mode"
        )
        val g_healthTakeTime: idCVar = idCVar(
            "g_healthTakeTime",
            "5",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_INTEGER or CVarSystem.CVAR_ARCHIVE,
            "how often to take health in nightmare mode"
        )
        val g_kickAmplitude: idCVar =
            idCVar("g_kickAmplitude", "0.0001", CVarSystem.CVAR_GAME or CVarSystem.CVAR_FLOAT, "")

        //
        val g_kickTime: idCVar = idCVar("g_kickTime", "1", CVarSystem.CVAR_GAME or CVarSystem.CVAR_FLOAT, "")
        val g_knockback: idCVar = idCVar("g_knockback", "1000", CVarSystem.CVAR_GAME or CVarSystem.CVAR_INTEGER, "")
        val g_mapCycle: idCVar = idCVar(
            "g_mapCycle",
            "mapcycle",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_ARCHIVE,
            "map cycling script for multiplayer games - see mapcycle.scriptcfg"
        )
        val g_maxShowDistance: idCVar =
            idCVar("g_maxShowDistance", "128", CVarSystem.CVAR_GAME or CVarSystem.CVAR_FLOAT, "")
        val g_monsters: idCVar = idCVar("g_monsters", "1", CVarSystem.CVAR_GAME or CVarSystem.CVAR_BOOL, "")
        val g_mpWeaponAngleScale: idCVar = idCVar(
            "g_mpWeaponAngleScale", "0", CVarSystem.CVAR_GAME or CVarSystem.CVAR_FLOAT, "Control the weapon sway in MP"
        )

        //
        val g_muzzleFlash: idCVar = idCVar(
            "g_muzzleFlash",
            "1",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_ARCHIVE or CVarSystem.CVAR_BOOL,
            "show muzzle flashes"
        )
        val g_nightmare: idCVar = idCVar(
            "g_nightmare",
            "0",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_ARCHIVE or CVarSystem.CVAR_BOOL,
            "if nightmare mode is allowed"
        )

        //
        val g_password: idCVar =
            idCVar("g_password", "", CVarSystem.CVAR_GAME or CVarSystem.CVAR_ARCHIVE, "game password")
        val g_projectileLights: idCVar = idCVar(
            "g_projectileLights",
            "1",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_ARCHIVE or CVarSystem.CVAR_BOOL,
            "show dynamic lights on projectiles"
        )
        val g_showActiveEntities: idCVar = idCVar(
            "g_showActiveEntities",
            "0",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_BOOL,
            "draws boxes around thinking entities.  dormant entities (outside of pvs) are drawn yellow.  non-dormant are green."
        )
        val g_showBrass: idCVar = idCVar(
            "g_showBrass",
            "1",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_ARCHIVE or CVarSystem.CVAR_BOOL,
            "enables ejected shells from weapon"
        )
        val g_showCollisionModels: idCVar =
            idCVar("g_showCollisionModels", "0", CVarSystem.CVAR_GAME or CVarSystem.CVAR_BOOL, "")
        val g_showCollisionTraces: idCVar =
            idCVar("g_showCollisionTraces", "0", CVarSystem.CVAR_GAME or CVarSystem.CVAR_BOOL, "")
        val g_showCollisionWorld: idCVar =
            idCVar("g_showCollisionWorld", "0", CVarSystem.CVAR_GAME or CVarSystem.CVAR_BOOL, "")
        val g_showEnemies: idCVar = idCVar(
            "g_showEnemies",
            "0",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_BOOL,
            "draws boxes around monsters that have targeted the the player"
        )
        val g_showEntityInfo: idCVar = idCVar("g_showEntityInfo", "0", CVarSystem.CVAR_GAME or CVarSystem.CVAR_BOOL, "")
        val g_showHud: idCVar =
            idCVar("g_showHud", "1", CVarSystem.CVAR_GAME or CVarSystem.CVAR_ARCHIVE or CVarSystem.CVAR_BOOL, "")

        //
        //
        //
        val g_showPVS: idCVar =
            idCVar("g_showPVS", "0", CVarSystem.CVAR_GAME or CVarSystem.CVAR_INTEGER, "", 0.0f, 2.0f)

        //
        val g_showPlayerShadow: idCVar = idCVar(
            "g_showPlayerShadow",
            "0",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_ARCHIVE or CVarSystem.CVAR_BOOL,
            "enables shadow of player model"
        )
        val g_showProjectilePct: idCVar = idCVar(
            "g_showProjectilePct",
            "0",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_ARCHIVE or CVarSystem.CVAR_BOOL,
            "enables display of player hit percentage"
        )
        val g_showTargets: idCVar = idCVar(
            "g_showTargets",
            "0",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_BOOL,
            "draws entities and their targets.  hidden entities are drawn grey."
        )
        val g_showTestModelFrame: idCVar = idCVar(
            "g_showTestModelFrame",
            "0",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_BOOL,
            "displays the current animation and frame # for testmodels"
        )
        val g_showTriggers: idCVar = idCVar(
            "g_showTriggers",
            "0",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_BOOL,
            "draws trigger entities (orange) and their targets (green).  disabled triggers are drawn grey."
        )
        val g_showcamerainfo: idCVar = idCVar(
            "g_showcamerainfo",
            "0",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_ARCHIVE,
            "displays the current frame # for the camera when playing cinematics"
        )
        val g_showviewpos: idCVar = idCVar("g_showviewpos", "0", CVarSystem.CVAR_GAME or CVarSystem.CVAR_BOOL, "")
        val g_skill: idCVar = idCVar("g_skill", "1", CVarSystem.CVAR_GAME or CVarSystem.CVAR_INTEGER, "")
        val g_skipFX: idCVar = idCVar("g_skipFX", "0", CVarSystem.CVAR_GAME or CVarSystem.CVAR_BOOL, "")
        val g_skipParticles: idCVar = idCVar("g_skipParticles", "0", CVarSystem.CVAR_GAME or CVarSystem.CVAR_BOOL, "")
        val g_skipViewEffects: idCVar = idCVar(
            "g_skipViewEffects", "0", CVarSystem.CVAR_GAME or CVarSystem.CVAR_BOOL, "skip damage and other view effects"
        )
        val g_stopTime: idCVar = idCVar("g_stopTime", "0", CVarSystem.CVAR_GAME or CVarSystem.CVAR_BOOL, "")
        val g_testDeath: idCVar = idCVar("g_testDeath", "0", CVarSystem.CVAR_GAME or CVarSystem.CVAR_BOOL, "")

        //
        val g_testHealthVision: idCVar =
            idCVar("g_testHealthVision", "0", CVarSystem.CVAR_GAME or CVarSystem.CVAR_FLOAT, "")
        val g_testModelAnimate: idCVar = idCVar(
            "g_testModelAnimate", "0", CVarSystem.CVAR_GAME or CVarSystem.CVAR_INTEGER, """
     test model animation,
     0 = cycle anim with origin reset
     1 = cycle anim with fixed origin
     2 = cycle anim with continuous origin
     3 = frame by frame with continuous origin
     4 = play anim once
     """.trimIndent(), 0.0f, 4.0f, ArgCompletion_Integer(0, 4)
        )
        val g_testModelBlend: idCVar = idCVar(
            "g_testModelBlend", "0", CVarSystem.CVAR_GAME or CVarSystem.CVAR_INTEGER, "number of frames to blend"
        )
        val g_testModelRotate: idCVar =
            idCVar("g_testModelRotate", "0", CVarSystem.CVAR_GAME, "test model rotation speed")

        //
        val g_testParticle: idCVar = idCVar(
            "g_testParticle",
            "0",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_INTEGER,
            "test particle visualation, set by the particle editor"
        )
        val g_testParticleName: idCVar = idCVar(
            "g_testParticleName", "", CVarSystem.CVAR_GAME, "name of the particle being tested by the particle editor"
        )
        val g_testPostProcess: idCVar =
            idCVar("g_testPostProcess", "", CVarSystem.CVAR_GAME, "name of material to draw over screen")
        val g_timeentities: idCVar = idCVar(
            "g_timeEntities",
            "0",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_FLOAT,
            "when non-zero, shows entities whose think functions exceeded the # of milliseconds specified"
        )
        val g_useDynamicProtection: idCVar = idCVar(
            "g_useDynamicProtection",
            "1",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_BOOL or CVarSystem.CVAR_ARCHIVE,
            "scale damage and armor dynamically to keep the player alive more often"
        )
        val g_vehicleForce: idCVar =
            idCVar("g_vehicleForce", "50000", CVarSystem.CVAR_GAME or CVarSystem.CVAR_FLOAT, "")
        val g_vehicleSuspensionDamping: idCVar =
            idCVar("g_vehicleSuspensionDamping", "400", CVarSystem.CVAR_GAME or CVarSystem.CVAR_FLOAT, "")
        val g_vehicleSuspensionDown: idCVar =
            idCVar("g_vehicleSuspensionDown", "20", CVarSystem.CVAR_GAME or CVarSystem.CVAR_FLOAT, "")
        val g_vehicleSuspensionKCompress: idCVar =
            idCVar("g_vehicleSuspensionKCompress", "200", CVarSystem.CVAR_GAME or CVarSystem.CVAR_FLOAT, "")
        val g_vehicleSuspensionUp: idCVar =
            idCVar("g_vehicleSuspensionUp", "32", CVarSystem.CVAR_GAME or CVarSystem.CVAR_FLOAT, "")
        val g_vehicleTireFriction: idCVar =
            idCVar("g_vehicleTireFriction", "0.8", CVarSystem.CVAR_GAME or CVarSystem.CVAR_FLOAT, "")

        //
        val g_vehicleVelocity: idCVar =
            idCVar("g_vehicleVelocity", "1000", CVarSystem.CVAR_GAME or CVarSystem.CVAR_FLOAT, "")
        val g_viewNodalX: idCVar = idCVar("g_viewNodalX", "0", CVarSystem.CVAR_GAME or CVarSystem.CVAR_FLOAT, "")
        val g_viewNodalZ: idCVar = idCVar("g_viewNodalZ", "0", CVarSystem.CVAR_GAME or CVarSystem.CVAR_FLOAT, "")

        //
        val g_voteFlags: idCVar = idCVar(
            "g_voteFlags",
            "0",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_NETWORKSYNC or CVarSystem.CVAR_INTEGER or CVarSystem.CVAR_ARCHIVE,
            """
     vote flags. bit mask of votes not allowed on this server
     bit 0 (+1)   restart now
     bit 1 (+2)   time limit
     bit 2 (+4)   frag limit
     bit 3 (+8)   game type
     bit 4 (+16)  kick player
     bit 5 (+32)  change map
     bit 6 (+64)  spectators
     bit 7 (+128) next map
     """.trimIndent()
        )
        val gamedate: idCVar = idCVar("gamedate", __DATE__, CVarSystem.CVAR_GAME or CVarSystem.CVAR_ROM, "")

        //
        // noset vars
        val gamename: idCVar = idCVar(
            "gamename",
            Game_local.GAME_VERSION,
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_SERVERINFO or CVarSystem.CVAR_ROM,
            ""
        )
        val ik_debug: idCVar =
            idCVar("ik_debug", "0", CVarSystem.CVAR_GAME or CVarSystem.CVAR_BOOL, "show IK debug lines")

        //
        val ik_enable: idCVar = idCVar("ik_enable", "1", CVarSystem.CVAR_GAME or CVarSystem.CVAR_BOOL, "enable IK")

        //
        val mod_validSkins: idCVar = idCVar(
            "mod_validSkins",
            "skins/characters/player/marine_mp;skins/characters/player/marine_mp_green;skins/characters/player/marine_mp_blue;skins/characters/player/marine_mp_red;skins/characters/player/marine_mp_yellow;skins/characters/player/marine_mp_purple;skins/characters/player/marine_mp_grey;skins/characters/player/marine_mp_orange",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_ARCHIVE,
            "valid skins for the game"
        )

        //
        val net_clientPredictGUI: idCVar = idCVar(
            "net_clientPredictGUI",
            "1",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_BOOL,
            "test guis in networking without prediction"
        )
        val net_serverDlBaseURL: idCVar = idCVar(
            "net_serverDlBaseURL",
            "",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_ARCHIVE,
            "base URL for the download redirection"
        )
        val net_serverDlTable: idCVar = idCVar(
            "net_serverDlTable",
            "",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_ARCHIVE,
            "pak names for which download is provided, separated by ;"
        )

        //
        val net_serverDownload: idCVar = idCVar(
            "net_serverDownload",
            "0",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_INTEGER or CVarSystem.CVAR_ARCHIVE,
            "enable server download redirects. 0: off 1: redirect to si_serverURL 2: use builtin download. see net_serverDl cvars for configuration"
        )
        val password: idCVar = idCVar(
            "password", "", CVarSystem.CVAR_GAME or CVarSystem.CVAR_NOCHEAT, "client password used when connecting"
        )
        val pm_airTics: idCVar = idCVar(
            "pm_air",
            "1800",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_NETWORKSYNC or CVarSystem.CVAR_INTEGER,
            "how long in milliseconds the player can go without air before he starts taking damage"
        )
        val pm_bboxwidth: idCVar = idCVar(
            "pm_bboxwidth",
            "32",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_NETWORKSYNC or CVarSystem.CVAR_FLOAT,
            "x/y size of player's bounding box"
        )
        val pm_bobpitch: idCVar = idCVar(
            "pm_bobpitch", "0.002", CVarSystem.CVAR_GAME or CVarSystem.CVAR_NETWORKSYNC or CVarSystem.CVAR_FLOAT, ""
        )
        val pm_bobroll: idCVar = idCVar(
            "pm_bobroll", "0.002", CVarSystem.CVAR_GAME or CVarSystem.CVAR_NETWORKSYNC or CVarSystem.CVAR_FLOAT, ""
        )
        val pm_bobup: idCVar = idCVar(
            "pm_bobup", "0.005", CVarSystem.CVAR_GAME or CVarSystem.CVAR_NETWORKSYNC or CVarSystem.CVAR_FLOAT, ""
        )
        val pm_crouchbob: idCVar = idCVar(
            "pm_crouchbob",
            "0.5f",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_NETWORKSYNC or CVarSystem.CVAR_FLOAT,
            "bob much faster when crouched"
        )
        val pm_crouchheight: idCVar = idCVar(
            "pm_crouchheight",
            "38",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_NETWORKSYNC or CVarSystem.CVAR_FLOAT,
            "height of player's bounding box while crouched"
        )
        val pm_crouchrate: idCVar = idCVar(
            "pm_crouchrate",
            "0.87",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_NETWORKSYNC or CVarSystem.CVAR_FLOAT,
            "time it takes for player's view to change from standing to crouching"
        )
        val pm_crouchspeed: idCVar = idCVar(
            "pm_crouchspeed",
            "80",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_NETWORKSYNC or CVarSystem.CVAR_FLOAT,
            "speed the player can move while crouched"
        )
        val pm_crouchviewheight: idCVar = idCVar(
            "pm_crouchviewheight",
            "32",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_NETWORKSYNC or CVarSystem.CVAR_FLOAT,
            "height of player's view while crouched"
        )
        val pm_deadheight: idCVar = idCVar(
            "pm_deadheight",
            "20",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_NETWORKSYNC or CVarSystem.CVAR_FLOAT,
            "height of player's bounding box while dead"
        )
        val pm_deadviewheight: idCVar = idCVar(
            "pm_deadviewheight",
            "10",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_NETWORKSYNC or CVarSystem.CVAR_FLOAT,
            "height of player's view while dead"
        )

        //
        // The default values for player movement cvars are set in def/player.def
        val pm_jumpheight: idCVar = idCVar(
            "pm_jumpheight",
            "48",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_NETWORKSYNC or CVarSystem.CVAR_FLOAT,
            "approximate height the player can jump"
        )
        val pm_maxviewpitch: idCVar = idCVar(
            "pm_maxviewpitch",
            "89",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_NETWORKSYNC or CVarSystem.CVAR_FLOAT,
            "amount player's view can look down"
        )
        val pm_minviewpitch: idCVar = idCVar(
            "pm_minviewpitch",
            "-89",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_NETWORKSYNC or CVarSystem.CVAR_FLOAT,
            "amount player's view can look up (negative values are up)"
        )
        val pm_modelView: idCVar = idCVar(
            "pm_modelView",
            "0",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_NETWORKSYNC or CVarSystem.CVAR_INTEGER,
            "draws camera from POV of player model (1 = always, 2 = when dead)",
            0.0f,
            2.0f,
            ArgCompletion_Integer(0, 2)
        )
        val pm_noclipspeed: idCVar = idCVar(
            "pm_noclipspeed",
            "200",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_NETWORKSYNC or CVarSystem.CVAR_FLOAT,
            "speed the player can move while in noclip"
        )
        val pm_normalheight: idCVar = idCVar(
            "pm_normalheight",
            "74",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_NETWORKSYNC or CVarSystem.CVAR_FLOAT,
            "height of player's bounding box while standing"
        )
        val pm_normalviewheight: idCVar = idCVar(
            "pm_normalviewheight",
            "68",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_NETWORKSYNC or CVarSystem.CVAR_FLOAT,
            "height of player's view while standing"
        )
        val pm_runbob: idCVar = idCVar(
            "pm_runbob",
            "0.4",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_NETWORKSYNC or CVarSystem.CVAR_FLOAT,
            "bob faster when running"
        )
        val pm_runpitch: idCVar = idCVar(
            "pm_runpitch", "0.002", CVarSystem.CVAR_GAME or CVarSystem.CVAR_NETWORKSYNC or CVarSystem.CVAR_FLOAT, ""
        )
        val pm_runroll: idCVar = idCVar(
            "pm_runroll", "0.005", CVarSystem.CVAR_GAME or CVarSystem.CVAR_NETWORKSYNC or CVarSystem.CVAR_FLOAT, ""
        )
        val pm_runspeed: idCVar = idCVar(
            "pm_runspeed",
            "220",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_NETWORKSYNC or CVarSystem.CVAR_FLOAT,
            "speed the player can move while running"
        )
        val pm_spectatebbox: idCVar = idCVar(
            "pm_spectatebbox",
            "32",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_NETWORKSYNC or CVarSystem.CVAR_FLOAT,
            "size of the spectator bounding box"
        )
        val pm_spectatespeed: idCVar = idCVar(
            "pm_spectatespeed",
            "450",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_NETWORKSYNC or CVarSystem.CVAR_FLOAT,
            "speed the player can move while spectating"
        )
        val pm_stamina: idCVar = idCVar(
            "pm_stamina",
            "24",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_NETWORKSYNC or CVarSystem.CVAR_FLOAT,
            "length of time player can run"
        )
        val pm_staminarate: idCVar = idCVar(
            "pm_staminarate",
            "0.75",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_NETWORKSYNC or CVarSystem.CVAR_FLOAT,
            "rate that player regains stamina. divide pm_stamina by this value to determine how long it takes to fully recharge."
        )
        val pm_staminathreshold: idCVar = idCVar(
            "pm_staminathreshold",
            "45",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_NETWORKSYNC or CVarSystem.CVAR_FLOAT,
            "when stamina drops below this value, player gradually slows to a walk"
        )
        val pm_stepsize: idCVar = idCVar(
            "pm_stepsize",
            "16",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_NETWORKSYNC or CVarSystem.CVAR_FLOAT,
            "maximum height the player can step up without jumping"
        )
        val pm_thirdPerson: idCVar = idCVar(
            "pm_thirdPerson",
            "0",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_NETWORKSYNC or CVarSystem.CVAR_BOOL,
            "enables third person view"
        )
        val pm_thirdPersonAngle: idCVar = idCVar(
            "pm_thirdPersonAngle",
            "0",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_NETWORKSYNC or CVarSystem.CVAR_FLOAT,
            "direction of camera from player in 3rd person in degrees (0 = behind player, 180 = in front)"
        )
        val pm_thirdPersonClip: idCVar = idCVar(
            "pm_thirdPersonClip",
            "1",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_NETWORKSYNC or CVarSystem.CVAR_BOOL,
            "clip third person view into world space"
        )
        val pm_thirdPersonDeath: idCVar = idCVar(
            "pm_thirdPersonDeath",
            "0",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_NETWORKSYNC or CVarSystem.CVAR_BOOL,
            "enables third person view when player dies"
        )
        val pm_thirdPersonHeight: idCVar = idCVar(
            "pm_thirdPersonHeight",
            "0",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_NETWORKSYNC or CVarSystem.CVAR_FLOAT,
            "height of camera from normal view height in 3rd person"
        )
        val pm_thirdPersonRange: idCVar = idCVar(
            "pm_thirdPersonRange",
            "80",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_NETWORKSYNC or CVarSystem.CVAR_FLOAT,
            "camera distance from player in 3rd person"
        )
        val pm_usecylinder: idCVar = idCVar(
            "pm_usecylinder",
            "0",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_NETWORKSYNC or CVarSystem.CVAR_BOOL,
            "use a cylinder approximation instead of a bounding box for player collision detection"
        )
        val pm_walkbob: idCVar = idCVar(
            "pm_walkbob",
            "0.3",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_NETWORKSYNC or CVarSystem.CVAR_FLOAT,
            "bob slowly when walking"
        )
        val pm_walkspeed: idCVar = idCVar(
            "pm_walkspeed",
            "140",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_NETWORKSYNC or CVarSystem.CVAR_FLOAT,
            "speed the player can move while walking"
        )

        //
        val r_aspectRatio: idCVar = idCVar(
            "r_aspectRatio",
            "-1",
            CVarSystem.CVAR_RENDERER or CVarSystem.CVAR_INTEGER or CVarSystem.CVAR_ARCHIVE,
            "aspect ratio of view:\n0 = 4:3\n1 = 16:9\n2 = 16:10\n-1 = auto (guess from resolution)",
            -1.0f,
            2.0f
        )
        val rb_showActive: idCVar = idCVar(
            "rb_showActive", "0", CVarSystem.CVAR_GAME or CVarSystem.CVAR_BOOL, "show rigid bodies that are not at rest"
        )
        val rb_showBodies: idCVar =
            idCVar("rb_showBodies", "0", CVarSystem.CVAR_GAME or CVarSystem.CVAR_BOOL, "show rigid bodies")
        val rb_showInertia: idCVar = idCVar(
            "rb_showInertia",
            "0",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_BOOL,
            "show the inertia tensor of each rigid body"
        )
        val rb_showMass: idCVar =
            idCVar("rb_showMass", "0", CVarSystem.CVAR_GAME or CVarSystem.CVAR_BOOL, "show the mass of each rigid body")

        //
        val rb_showTimings: idCVar =
            idCVar("rb_showTimings", "0", CVarSystem.CVAR_GAME or CVarSystem.CVAR_BOOL, "show rigid body cpu usage")
        val rb_showVelocity: idCVar = idCVar(
            "rb_showVelocity", "0", CVarSystem.CVAR_GAME or CVarSystem.CVAR_BOOL, "show the velocity of each rigid body"
        )
        val si_fragLimit: idCVar = idCVar(
            "si_fragLimit",
            "10",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_SERVERINFO or CVarSystem.CVAR_ARCHIVE or CVarSystem.CVAR_INTEGER,
            "frag limit",
            1.0f,
            MultiplayerGame.MP_PLAYER_MAXFRAGS.toFloat()
        )

        /*

         All game cvars should be defined here.

         */
        val si_gameTypeArgs: Array<String?> =
            arrayOf("singleplayer", "deathmatch", "Tourney", "Team DM", "Last Man", null)
        val si_gameType: idCVar = idCVar(
            "si_gameType",
            si_gameTypeArgs[0]!!,
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_SERVERINFO or CVarSystem.CVAR_ARCHIVE,
            "game type - singleplayer, deathmatch, Tourney, Team DM or Last Man",
            si_gameTypeArgs,
            ArgCompletion_String(si_gameTypeArgs)
        )
        val si_map: idCVar = idCVar(
            "si_map",
            "game/mp/d3dm1",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_SERVERINFO or CVarSystem.CVAR_ARCHIVE,
            "map to be played next on server",
            ArgCompletion_MapName.getInstance()
        )
        val si_maxPlayers: idCVar = idCVar(
            "si_maxPlayers",
            "4",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_SERVERINFO or CVarSystem.CVAR_ARCHIVE or CVarSystem.CVAR_INTEGER,
            "max number of players allowed on the server",
            1.0f,
            4.0f
        )

        //
        // server info
        val si_name: idCVar = idCVar(
            "si_name",
            "dhewm server",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_SERVERINFO or CVarSystem.CVAR_ARCHIVE,
            "name of the server"
        )
        val si_pure: idCVar = idCVar(
            "si_pure",
            "1",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_SERVERINFO or CVarSystem.CVAR_BOOL,
            "server is pure and does not allow modified data"
        )
        val si_serverURL: idCVar = idCVar(
            "si_serverURL",
            "",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_SERVERINFO or CVarSystem.CVAR_ARCHIVE,
            "where to reach the server admins and get information about the server"
        )
        val si_spectators: idCVar = idCVar(
            "si_spectators",
            "1",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_SERVERINFO or CVarSystem.CVAR_ARCHIVE or CVarSystem.CVAR_BOOL,
            "allow spectators or require all clients to play"
        )
        val si_teamDamage: idCVar = idCVar(
            "si_teamDamage",
            "0",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_SERVERINFO or CVarSystem.CVAR_ARCHIVE or CVarSystem.CVAR_BOOL,
            "enable team damage"
        )
        val si_timeLimit: idCVar = idCVar(
            "si_timeLimit",
            "10",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_SERVERINFO or CVarSystem.CVAR_ARCHIVE or CVarSystem.CVAR_INTEGER,
            "time limit in minutes",
            0.0f,
            60.0f
        )
        val si_usePass: idCVar = idCVar(
            "si_usePass",
            "0",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_SERVERINFO or CVarSystem.CVAR_ARCHIVE or CVarSystem.CVAR_BOOL,
            "enable client password checking"
        )
        val si_warmup: idCVar = idCVar(
            "si_warmup",
            "0",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_SERVERINFO or CVarSystem.CVAR_ARCHIVE or CVarSystem.CVAR_BOOL,
            "do pre-game warmup"
        )
        val ui_autoReload: idCVar = idCVar(
            "ui_autoReload",
            "1",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_USERINFO or CVarSystem.CVAR_ARCHIVE or CVarSystem.CVAR_BOOL,
            "auto reload weapon"
        )
        val ui_autoSwitch: idCVar = idCVar(
            "ui_autoSwitch",
            "1",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_USERINFO or CVarSystem.CVAR_ARCHIVE or CVarSystem.CVAR_BOOL,
            "auto switch weapon"
        )
        val ui_chat: idCVar = idCVar(
            "ui_chat",
            "0",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_USERINFO or CVarSystem.CVAR_BOOL or CVarSystem.CVAR_ROM or CVarSystem.CVAR_CHEAT,
            "player is chatting"
        )

        //
        // user info
        val ui_name: idCVar = idCVar(
            "ui_name",
            "Player",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_USERINFO or CVarSystem.CVAR_ARCHIVE,
            "player name"
        )
        val ui_showGun: idCVar = idCVar(
            "ui_showGun",
            "1",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_USERINFO or CVarSystem.CVAR_ARCHIVE or CVarSystem.CVAR_BOOL,
            "show gun"
        )

        //
        val ui_skinArgs: Array<String?> = arrayOf(
            "skins/characters/player/marine_mp",
            "skins/characters/player/marine_mp_red",
            "skins/characters/player/marine_mp_blue",
            "skins/characters/player/marine_mp_green",
            "skins/characters/player/marine_mp_yellow",
            "skins/characters/player/marine_mp_purple",
            "skins/characters/player/marine_mp_grey",
            "skins/characters/player/marine_mp_orange",
            null
        )
        val ui_skin: idCVar = idCVar(
            "ui_skin",
            ui_skinArgs[0]!!,
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_USERINFO or CVarSystem.CVAR_ARCHIVE,
            "player skin",
            ui_skinArgs,
            ArgCompletion_String(ui_skinArgs)
        )

        //
        //
        var BUILD_DEBUG: String = "-release"
        val gameVersion: gameVersion_s = gameVersion_s()

        //
        val g_version: idCVar =
            idCVar("g_version", gameVersion.string, CVarSystem.CVAR_GAME or CVarSystem.CVAR_ROM, "game version")
        val si_readyArgs: Array<String?> = arrayOf("Not Ready", "Ready", null)
        val ui_ready: idCVar = idCVar(
            "ui_ready",
            si_readyArgs[0]!!,
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_USERINFO,
            "player is ready to start playing",
            ArgCompletion_String(si_readyArgs)
        )
        val si_spectateArgs: Array<String?> = arrayOf("Play", "Spectate", null)
        val ui_spectate: idCVar = idCVar(
            "ui_spectate",
            si_spectateArgs[0]!!,
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_USERINFO,
            "play or spectate",
            ArgCompletion_String(si_spectateArgs)
        )
        val ui_teamArgs: Array<String?> = arrayOf("Red", "Blue", null)
        val ui_team: idCVar = idCVar(
            "ui_team",
            ui_teamArgs[0]!!,
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_USERINFO or CVarSystem.CVAR_ARCHIVE,
            "player team",
            ui_teamArgs,
            ArgCompletion_String(ui_teamArgs)
        )

        // -----------------------------------------------------------------------
        // D3XP (Resurrection of Evil) CVars — always registered, used when isD3XP
        // -----------------------------------------------------------------------

        // AI
        val ai_showHealth: idCVar = idCVar(
            "ai_showHealth", "0", CVarSystem.CVAR_GAME or CVarSystem.CVAR_BOOL, "Draws the AI's health above its head"
        )

        // Vehicle / portal sky / shockwave debug
        val g_vehicleDebug: idCVar = idCVar("g_vehicleDebug", "0", CVarSystem.CVAR_GAME or CVarSystem.CVAR_BOOL, "")
        val g_debugShockwave: idCVar =
            idCVar("g_debugShockwave", "0", CVarSystem.CVAR_GAME or CVarSystem.CVAR_BOOL, "Debug the shockwave")
        val g_enablePortalSky: idCVar =
            idCVar("g_enablePortalSky", "1", CVarSystem.CVAR_GAME or CVarSystem.CVAR_BOOL, "enables the portal sky")

        // Slow-motion
        val g_enableSlowmo: idCVar =
            idCVar("g_enableSlowmo", "0", CVarSystem.CVAR_GAME or CVarSystem.CVAR_BOOL, "for testing purposes only")
        val g_slowmoStepRate: idCVar =
            idCVar("g_slowmoStepRate", "0.02", CVarSystem.CVAR_GAME or CVarSystem.CVAR_FLOAT, "")

        // Fullscreen FX
        val g_testFullscreenFX: idCVar = idCVar(
            "g_testFullscreenFX",
            "-1",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_INTEGER,
            "index will activate specific fx, -2 is for all on, -1 is off"
        )
        val g_testHelltimeFX: idCVar = idCVar(
            "g_testHelltimeFX",
            "-1",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_INTEGER,
            "set to 0, 1, 2 to test helltime, -1 is off"
        )
        val g_testMultiplayerFX: idCVar = idCVar(
            "g_testMultiplayerFX",
            "-1",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_INTEGER,
            "set to 0, 1, 2 to test multiplayer, -1 is off"
        )
        val g_lowresFullscreenFX: idCVar = idCVar(
            "g_lowresFullscreenFX", "0", CVarSystem.CVAR_GAME or CVarSystem.CVAR_BOOL, "enable lores mode for fx"
        )

        // Damage
        val g_moveableDamageScale: idCVar = idCVar(
            "g_moveableDamageScale",
            "0.1",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_FLOAT,
            "scales damage wrt mass of object in multiplayer"
        )

        // Bloom
        val g_testBloomSpeed: idCVar =
            idCVar("g_testBloomSpeed", "1", CVarSystem.CVAR_GAME or CVarSystem.CVAR_FLOAT, "")
        val g_testBloomIntensity: idCVar =
            idCVar("g_testBloomIntensity", "-0.01", CVarSystem.CVAR_GAME or CVarSystem.CVAR_FLOAT, "")
        val g_testBloomNumPasses: idCVar =
            idCVar("g_testBloomNumPasses", "30", CVarSystem.CVAR_GAME or CVarSystem.CVAR_INTEGER, "")

        // CTF
        val si_flagDropTimeLimit: idCVar = idCVar(
            "si_flagDropTimeLimit",
            "30",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_SERVERINFO or CVarSystem.CVAR_ARCHIVE or CVarSystem.CVAR_INTEGER,
            "seconds before a dropped CTF flag is returned"
        )
        val si_midnight: idCVar = idCVar(
            "si_midnight",
            "0",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_INTEGER or CVarSystem.CVAR_SERVERINFO,
            "Start the game up in midnight CTF (completely dark)"
        )
        val g_CTFArrows: idCVar = idCVar(
            "g_CTFArrows",
            "1",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_NETWORKSYNC or CVarSystem.CVAR_BOOL,
            "draw arrows over teammates in CTF"
        )
        val g_flagAttachJoint: idCVar = idCVar(
            "g_flagAttachJoint",
            "Chest",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_CHEAT,
            "player joint to attach CTF flag to"
        )
        val g_flagAttachOffsetX: idCVar = idCVar(
            "g_flagAttachOffsetX",
            "8",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_CHEAT,
            "X offset of CTF flag when carried"
        )
        val g_flagAttachOffsetY: idCVar = idCVar(
            "g_flagAttachOffsetY",
            "4",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_CHEAT,
            "Y offset of CTF flag when carried"
        )
        val g_flagAttachOffsetZ: idCVar = idCVar(
            "g_flagAttachOffsetZ",
            "-12",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_CHEAT,
            "Z offset of CTF flag when carried"
        )
        val g_flagAttachAngleX: idCVar = idCVar(
            "g_flagAttachAngleX",
            "90",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_CHEAT,
            "X angle of CTF flag when carried"
        )
        val g_flagAttachAngleY: idCVar = idCVar(
            "g_flagAttachAngleY",
            "25",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_CHEAT,
            "Y angle of CTF flag when carried"
        )
        val g_flagAttachAngleZ: idCVar = idCVar(
            "g_flagAttachAngleZ",
            "-90",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_CHEAT,
            "Z angle of CTF flag when carried"
        )

        // Grabber (gravity gun)
        val g_grabberHoldSeconds: idCVar = idCVar(
            "g_grabberHoldSeconds",
            "3",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_FLOAT or CVarSystem.CVAR_CHEAT,
            "number of seconds to hold object"
        )
        val g_grabberEnableShake: idCVar = idCVar(
            "g_grabberEnableShake",
            "1",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_BOOL or CVarSystem.CVAR_CHEAT,
            "enable the grabber shake"
        )
        val g_grabberRandomMotion: idCVar = idCVar(
            "g_grabberRandomMotion",
            "1",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_BOOL or CVarSystem.CVAR_CHEAT,
            "enable random motion on the grabbed object"
        )
        val g_grabberHardStop: idCVar = idCVar(
            "g_grabberHardStop",
            "1",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_BOOL or CVarSystem.CVAR_CHEAT,
            "hard stops object if too fast"
        )
        val g_grabberDamping: idCVar = idCVar(
            "g_grabberDamping",
            "0.5",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_FLOAT or CVarSystem.CVAR_CHEAT,
            "damping of grabber"
        )

        // Key binding migration
        val g_xp_bind_run_once: idCVar = idCVar(
            "g_xp_bind_run_once",
            "0",
            CVarSystem.CVAR_GAME or CVarSystem.CVAR_BOOL or CVarSystem.CVAR_ARCHIVE,
            "Rebind all controls once for D3XP."
        )

    }
}
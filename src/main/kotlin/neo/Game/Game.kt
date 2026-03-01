/*
 * Copyright (C) 1999-2011 id Software LLC, a ZeniMax Media company.
 * Translated to Kotlin by Dr. Feederino with support of Claude Code
 *
 * This file is part of the Doom 3 Kotlin project.
 * Original source: neo/Game/Game.h
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

package neo.Game

import neo.Game.Animation.ANIM_GetModelDefFromEntityDef
import neo.Game.Animation.Anim
import neo.Game.Animation.Anim.frameBlend_t
import neo.Game.Animation.Anim.idMD5Anim
import neo.Game.Animation.idAnim
import neo.Game.Animation.idDeclModelDef
import neo.Game.Game_local.gameSoundChannel_t
import neo.Game.Game_local.idGameLocal
import neo.Game.Player.idPlayer
import neo.Renderer.Model.idMD5Joint
import neo.Renderer.Model.idRenderModel
import neo.Renderer.ModelManager
import neo.Renderer.ModelManager.idRenderModelManager
import neo.Renderer.RenderSystem.idRenderSystem
import neo.Renderer.RenderWorld
import neo.Renderer.RenderWorld.idRenderWorld
import neo.Renderer.RenderWorld.renderEntity_s
import neo.Renderer.RenderWorld.renderLight_s
import neo.Sound.snd_shader
import neo.Sound.snd_shader.idSoundShader
import neo.Sound.sound.idSoundEmitter
import neo.Sound.sound.idSoundSystem
import neo.Sound.sound.idSoundWorld
import neo.TempDump
import neo.Tools.Compilers.AAS.AASFileManager.idAASFileManager
import neo.cm.idCollisionModelManager
import neo.framework.Async.NetworkSystem.idNetworkSystem
import neo.framework.CVarSystem.idCVarSystem
import neo.framework.CmdSystem.idCmdSystem
import neo.framework.Common.idCommon
import neo.framework.DeclAF.*
import neo.framework.DeclManager
import neo.framework.DeclManager.declType_t
import neo.framework.DeclManager.idDeclManager
import neo.framework.FileSystem_h.idFileSystem
import neo.framework.File_h.idFile
import neo.framework.UsercmdGen.usercmd_t
import neo.idlib.BitMsg.idBitMsg
import neo.idlib.Dict_h.idDict
import neo.idlib.Dict_h.idKeyValue
import neo.idlib.MAX_STRING_CHARS
import neo.idlib.MapFile.idMapEntity
import neo.idlib.Text.Str
import neo.idlib.Text.Str.idStr
import neo.idlib.containers.CFloat
import neo.idlib.geometry.JointTransform.idJointMat
import neo.idlib.geometry.JointTransform.idJointQuat
import neo.idlib.geometry.TraceModel.traceModel_t
import neo.idlib.math.*
import neo.idlib.math.Matrix.idMat3
import neo.sys.sys_public.idSys
import neo.ui.UserInterface.idUserInterface
import neo.ui.UserInterface.idUserInterface.idUserInterfaceManager
import java.util.*

object Game {
    /*
     ===============================================================================

     Game API.

     ===============================================================================
     */
    const val GAME_API_VERSION = 8
    val SCRIPT_DEFAULT: String = "script/doom_main.script"

    /*
     ===============================================================================

     Public game interface with methods to run the game.

     ===============================================================================
     */
    // default scripts
    val SCRIPT_DEFAULTDEFS: String = "script/doom_defs.script"

    //
    val SCRIPT_DEFAULTFUNC: String = "doom_main"

    //enum {
    const val TEST_PARTICLE_MODEL = 0
    const val TEST_PARTICLE_IMPACT = 1 + TEST_PARTICLE_MODEL
    const val TEST_PARTICLE_FLIGHT = 3 + TEST_PARTICLE_MODEL
    const val TEST_PARTICLE_MUZZLE = 2 + TEST_PARTICLE_MODEL

    //
    const val TEST_PARTICLE_SELECTED = 4 + TEST_PARTICLE_MODEL

    //
    const val TIME_GROUP1 = 0
    const val TIME_GROUP2 = 1

    enum class allowReply_t {
        ALLOW_YES,  //= 0,
        ALLOW_BADPASS,  // core will prompt for password and connect again
        ALLOW_NOTYET,  // core will wait with transmitted message
        ALLOW_NO // core will abort with transmitted message
    }

    enum class escReply_t {
        ESC_IGNORE,  //= 0,	// do nothing
        ESC_MAIN,  // start main menu GUI
        ESC_GUI // set an explicit GUI
    }

    class gameReturn_t {
        var combat = 0
        var consistencyHash // used to check for network game divergence
                = 0
        var health = 0
        var heartRate = 0
        var sessionCommand: CharArray =
            CharArray(MAX_STRING_CHARS) // "map", "disconnect", "victory", etc
        var stamina = 0
        var syncNextGameFrame // used when cinematics are skipped to prevent session from simulating several game frames to
                = false // keep the game time in sync with real time
    }

    abstract class idGame {
        // virtual						~idGame() {}
        // Initialize the game for the first time.
        abstract fun Init()

        // Shut down the entire game.
        abstract fun Shutdown()

        // Set the local client number. Distinguishes listen ( == 0 ) / dedicated ( == -1 )
        abstract fun SetLocalClient(clientNum: Int)

        // Sets the user info for a client.
        // if canModify is true, the game can modify the user info in the returned dictionary pointer, server will forward the change back
        // canModify is never true on network client
        abstract fun SetUserInfo(clientNum: Int, userInfo: idDict, isClient: Boolean, canModify: Boolean): idDict?

        // Retrieve the game's userInfo dict for a client.
        abstract fun GetUserInfo(clientNum: Int): idDict?

        // The game gets a chance to alter userinfo before they are emitted to server.
        abstract fun ThrottleUserInfo()

        // Sets the serverinfo at map loads and when it changes.
        abstract fun SetServerInfo(serverInfo: idDict)

        // The session calls this before moving the single player game to a new level.
        abstract fun GetPersistentPlayerInfo(clientNum: Int): idDict

        // The session calls this right before a new level is loaded.
        abstract fun SetPersistentPlayerInfo(clientNum: Int, playerInfo: idDict)

        // Loads a map and spawns all the entities.
        abstract fun InitFromNewMap(
            mapName: String,
            renderWorld: idRenderWorld,
            soundWorld: idSoundWorld,
            isServer: Boolean,
            isClient: Boolean,
            randseed: Int
        )

        // Loads a map from a savegame file.
        abstract fun InitFromSaveGame(
            mapName: String,
            renderWorld: idRenderWorld,
            soundWorld: idSoundWorld,
            saveGameFile: idFile
        ): Boolean

        // Saves the current game state, the session may have written some data to the file already.
        abstract fun SaveGame(saveGameFile: idFile)

        // Shut down the current map.
        abstract fun MapShutdown()

        // Caches media referenced from in key/value pairs in the given dictionary.
        abstract fun CacheDictionaryMedia(dict: idDict?)

        // Spawns the player entity to be used by the client.
        abstract fun SpawnPlayer(clientNum: Int)

        // Runs a game frame, may return a session command for level changing, etc
        abstract fun RunFrame(clientCmds: Array<usercmd_t>): gameReturn_t

        // Makes rendering and sound system calls to display for a given clientNum.
        abstract fun Draw(clientNum: Int): Boolean

        // Let the game do it's own UI when ESCAPE is used
        abstract fun HandleESC(gui: idUserInterface?): escReply_t?

        // get the games menu if appropriate ( multiplayer )
        abstract fun StartMenu(): idUserInterface?

        // When the game is running it's own UI fullscreen, GUI commands are passed through here
        // return NULL once the fullscreen UI mode should stop, or "main" to go to main menu
        abstract fun HandleGuiCommands(menuCommand: String): String?

        // main menu commands not caught in the engine are passed here
        abstract fun HandleMainMenuCommands(menuCommand: String?, gui: idUserInterface?)

        // Early check to deny connect.
        abstract fun ServerAllowClient(
            numClients: Int,
            IP: String,
            guid: String,
            password: String,
            reason: CharArray /*[MAX_STRING_CHARS]*/
        ): allowReply_t

        // Connects a client.
        abstract fun ServerClientConnect(clientNum: Int, guid: String?)

        // Spawns the player entity to be used by the client.
        abstract fun ServerClientBegin(clientNum: Int)

        // Disconnects a client and removes the player entity from the game.
        abstract fun ServerClientDisconnect(clientNum: Int)

        // Writes initial reliable messages a client needs to recieve when first joining the game.
        abstract fun ServerWriteInitialReliableMessages(clientNum: Int)

        // Writes a snapshot of the server game state for the given client.
        abstract fun ServerWriteSnapshot(
            clientNum: Int,
            sequence: Int,
            msg: idBitMsg,
            clientInPVS: ByteArray,
            numPVSClients: Int
        )

        // Patches the network entity states at the server with a snapshot for the given client.
        abstract fun ServerApplySnapshot(clientNum: Int, sequence: Int): Boolean

        // Processes a reliable message from a client.
        abstract fun ServerProcessReliableMessage(clientNum: Int, msg: idBitMsg)

        // Reads a snapshot and updates the client game state.
        abstract fun ClientReadSnapshot(
            clientNum: Int,
            sequence: Int,
            gameFrame: Int,
            gameTime: Int,
            dupeUsercmds: Int,
            aheadOfServer: Int,
            msg: idBitMsg?
        )

        // Patches the network entity states at the client with a snapshot.
        abstract fun ClientApplySnapshot(clientNum: Int, sequence: Int): Boolean

        // Processes a reliable message from the server.
        abstract fun ClientProcessReliableMessage(clientNum: Int, msg: idBitMsg)

        // Runs prediction on entities at the client.
        abstract fun ClientPrediction(
            clientNum: Int,
            clientCmds: Array<usercmd_t>,
            lastPredictFrame: Boolean
        ): gameReturn_t

        // Used to manage divergent time-lines
        abstract fun SelectTimeGroup(timeGroup: Int)
        abstract fun GetTimeGroupTime(timeGroup: Int): Int
        abstract fun GetBestGameType(map: String, gametype: String, buf: CharArray /*[ MAX_STRING_CHARS ]*/)

        // Returns a summary of stats for a given client
        abstract fun GetClientStats(clientNum: Int, data: Array<String>, len: Int)

        // Switch a player to a particular team
        abstract fun SwitchTeam(clientNum: Int, team: Int)
        abstract fun DownloadRequest(
            IP: String,
            guid: String,
            paks: String,
            urls: CharArray /*[ MAX_STRING_CHARS ]*/
        ): Boolean

        abstract fun GetMapLoadingGUI(gui: CharArray? /*[ MAX_STRING_CHARS ]*/)
    }

    //};
    class refSound_t {
        // with idSoundWorld::AllocSoundEmitter() when needed
        val origin: idVec3
        var diversity // 0.0f to 1.0f value used to select which
                = 0.0f
        var listenerId // SSF_PRIVATE_SOUND only plays if == listenerId from PlaceListener
                = 0
        var parms // override volume, flags, etc
                : snd_shader.soundShaderParms_t
        var referenceSound // this is the interface to the sound system, created
                : idSoundEmitter? = null

        // no spatialization will be performed if == listenerID
        var shader // this really shouldn't be here, it is a holdover from single channel behavior
                : idSoundShader? = null

        // samples in a multi-sample list from the shader are used
        var waitfortrigger // don't start it at spawn time
                = false

        init {
            origin = idVec3()
            parms = snd_shader.soundShaderParms_t()
        }
    }

    // FIXME: this interface needs to be reworked but it properly separates code for the time being
    class idGameEdit {
        // virtual						~idGameEdit() {}
        // These are the canonical idDict to parameter parsing routines used by both the game and tools.
        /*
         ================
         idGameEdit::ParseSpawnArgsToRenderLight

         parse the light parameters
         this is the canonical renderLight parm parsing,
         which should be used by dmap and the editor
         ================
         */
        fun ParseSpawnArgsToRenderLight(args: idDict, renderLight: renderLight_s) {
            val gotTarget: Boolean
            val gotUp: Boolean
            val gotRight: Boolean
            val texture: String?
            val color = idVec3()
            //renderLight.clear() //memset( renderLight, 0, sizeof( *renderLight ) );
            if (!args.GetVector("light_origin", "", renderLight.origin)) {
                args.GetVector("origin", "", renderLight.origin)
            }
            gotTarget = args.GetVector("light_target", "", renderLight.target)
            gotUp = args.GetVector("light_up", "", renderLight.up)
            gotRight = args.GetVector("light_right", "", renderLight.right)
            args.GetVector("light_start", "0 0 0", renderLight.start)
            if (!args.GetVector("light_end", "", renderLight.end)) {
                renderLight.end.set(renderLight.target)
            }

            // we should have all of the target/right/up or none of them
            if ((gotTarget || gotUp || gotRight) != (gotTarget && gotUp && gotRight)) {
                Game_local.gameLocal.Printf(
                    "Light at (%f,%f,%f) has bad target info\n",
                    renderLight.origin[0], renderLight.origin[1], renderLight.origin[2]
                )
                return
            }
            if (!gotTarget) {
                renderLight.pointLight._val = true

                // allow an optional relative center of light and shadow offset
                args.GetVector("light_center", "0 0 0", renderLight.lightCenter)

                // create a point light
                if (!args.GetVector("light_radius", "300 300 300", renderLight.lightRadius)) {
                    val radius = CFloat()
                    args.GetFloat("light", "300", radius)
                    renderLight.lightRadius[0] =
                        renderLight.lightRadius.set(1, renderLight.lightRadius.set(2, radius._val))
                }
            }

            // get the rotation matrix in either full form, or single angle form
            val angles = idAngles()
            val mat = idMat3()
            if (!args.GetMatrix("light_rotation", "1 0 0 0 1 0 0 0 1", mat)) {
                if (!args.GetMatrix("rotation", "1 0 0 0 1 0 0 0 1", mat)) {
                    angles[1] = args.GetFloat("angle", "0")
                    angles[0] = 0.0f
                    angles[1] = idMath.AngleNormalize360(angles[1])
                    angles[2] = 0.0f
                    mat.set(angles.ToMat3())
                }
            }

            // fix degenerate identity matrices
            mat[0].FixDegenerateNormal()
            mat[1].FixDegenerateNormal()
            mat[2].FixDegenerateNormal()
            renderLight.axis.set(mat)

            // check for other attributes
            args.GetVector("_color", "1 1 1", color)
            renderLight.shaderParms[RenderWorld.SHADERPARM_RED] = color[0]
            renderLight.shaderParms[RenderWorld.SHADERPARM_GREEN] = color[1]
            renderLight.shaderParms[RenderWorld.SHADERPARM_BLUE] = color[2]
            renderLight.shaderParms[RenderWorld.SHADERPARM_TIMESCALE] = args.GetFloat("shaderParm3", "1")
            if (
                args.GetFloat("shaderParm4", "0")
                    .also { renderLight.shaderParms[RenderWorld.SHADERPARM_TIMEOFFSET] = it } == 0.0f
            ) {
                // offset the start time of the shader to sync it to the game time
                renderLight.shaderParms[RenderWorld.SHADERPARM_TIMEOFFSET] =
                    -MS2SEC(Game_local.gameLocal.time.toFloat())
            }
            renderLight.shaderParms[5] = args.GetFloat("shaderParm5", "0")
            renderLight.shaderParms[6] = args.GetFloat("shaderParm6", "0")
            renderLight.shaderParms[RenderWorld.SHADERPARM_MODE] = args.GetFloat("shaderParm7", "0")
            renderLight.noShadows._val = args.GetBool("noshadows", "0")
            renderLight.noSpecular._val = args.GetBool("nospecular", "0")
            renderLight.parallel._val = args.GetBool("parallel", "0")
            texture = args.GetString("texture", "lights/squarelight1")!!
            // allow this to be NULL
            renderLight.shader = DeclManager.declManager.FindMaterial(texture, false)
        }

        /*
         ================
         idGameEdit::ParseSpawnArgsToRenderEntity

         parse the static model parameters
         this is the canonical renderEntity parm parsing,
         which should be used by dmap and the editor
         ================
         */
        fun ParseSpawnArgsToRenderEntity(args: idDict, renderEntity: renderEntity_s) {
            var i: Int
            var temp: String?
            val color = idVec3()
            val angle: Float
            var modelDef: idDeclModelDef?
            renderEntity.clear() //	memset( renderEntity, 0, sizeof( *renderEntity ) );//TODO:clear?
            temp = args.GetString("model")
            modelDef = null
            if (temp.isNotEmpty()) {
                modelDef = TempDump.dynamic_cast(
                    idDeclModelDef::class.java,
                    DeclManager.declManager.FindType(declType_t.DECL_MODELDEF, temp, false)
                ) as idDeclModelDef?
                if (modelDef != null) {
                    renderEntity.hModel = modelDef.ModelHandle()
                }
                if (renderEntity.hModel == null) {
                    renderEntity.hModel = ModelManager.renderModelManager.FindModel(temp)
                }
            }
            if (renderEntity.hModel != null) {
                renderEntity.bounds.set(renderEntity.hModel!!.Bounds(renderEntity))
            } else {
                renderEntity.bounds.Zero()
            }
            temp = args.GetString("skin")
            if (temp.isNotEmpty()) {
                renderEntity.customSkin = DeclManager.declManager.FindSkin(temp)
            } else if (modelDef != null) {
                renderEntity.customSkin = modelDef.GetDefaultSkin()
            }
            temp = args.GetString("shader")
            if (temp.isNotEmpty()) {
                renderEntity.customShader = DeclManager.declManager.FindMaterial(temp)
            }
            args.GetVector("origin", "0 0 0", renderEntity.origin)

            // get the rotation matrix in either full form, or single angle form
            if (!args.GetMatrix("rotation", "1 0 0 0 1 0 0 0 1", renderEntity.axis)) {
                angle = args.GetFloat("angle")
                if (angle != 0.0f) {
                    renderEntity.axis.set(idAngles(0.0f, angle, 0.0f).ToMat3())
                } else {
                    renderEntity.axis.Identity()
                }
            }
            renderEntity.referenceSound = null

            // get shader parms
            args.GetVector("_color", "1 1 1", color)
            renderEntity.shaderParms[RenderWorld.SHADERPARM_RED] = color[0]
            renderEntity.shaderParms[RenderWorld.SHADERPARM_GREEN] = color[1]
            renderEntity.shaderParms[RenderWorld.SHADERPARM_BLUE] = color[2]
            renderEntity.shaderParms[3] = args.GetFloat("shaderParm3", "1")
            renderEntity.shaderParms[4] = args.GetFloat("shaderParm4", "0")
            renderEntity.shaderParms[5] = args.GetFloat("shaderParm5", "0")
            renderEntity.shaderParms[6] = args.GetFloat("shaderParm6", "0")
            renderEntity.shaderParms[7] = args.GetFloat("shaderParm7", "0")
            renderEntity.shaderParms[8] = args.GetFloat("shaderParm8", "0")
            renderEntity.shaderParms[9] = args.GetFloat("shaderParm9", "0")
            renderEntity.shaderParms[10] = args.GetFloat("shaderParm10", "0")
            renderEntity.shaderParms[11] = args.GetFloat("shaderParm11", "0")

            // check noDynamicInteractions flag
            renderEntity.noDynamicInteractions = args.GetBool("noDynamicInteractions")

            // check noshadows flag
            renderEntity.noShadow = args.GetBool("noshadows")

            // check noselfshadows flag
            renderEntity.noSelfShadow = args.GetBool("noselfshadows")

            // init any guis, including entity-specific states
            i = 0
            while (i < RenderWorld.MAX_RENDERENTITY_GUI) {
                temp = args.GetString(if (i == 0) "gui" else Str.va("gui%d", i + 1))
                if (temp.isNotEmpty()) {
                    renderEntity.gui[i] = AddRenderGui(temp, args)
                }
                i++
            }
        }

        /*
         ================
         idGameEdit::ParseSpawnArgsToRefSound

         parse the sound parameters
         this is the canonical refSound parm parsing,
         which should be used by dmap and the editor
         ================
         */
        fun ParseSpawnArgsToRefSound(args: idDict, refSound: refSound_t) {
            val temp: String?

            //	memset( refSound, 0, sizeof( *refSound ) );//TODO:clear?
            refSound.parms.minDistance = args.GetFloat("s_mindistance")
            refSound.parms.maxDistance = args.GetFloat("s_maxdistance")
            refSound.parms.volume = args.GetFloat("s_volume")
            refSound.parms.shakes = args.GetFloat("s_shakes")
            args.GetVector("origin", "0 0 0", refSound.origin)
            refSound.referenceSound = null

            // if a diversity is not specified, every sound start will make
            // a random one.  Specifying diversity is usefull to make multiple
            // lights all share the same buzz sound offset, for instance.
            refSound.diversity = args.GetFloat("s_diversity", "-1")
            refSound.waitfortrigger = args.GetBool("s_waitfortrigger")
            if (args.GetBool("s_omni")) {
                refSound.parms.soundShaderFlags = refSound.parms.soundShaderFlags or Sound.SSF_OMNIDIRECTIONAL
            }
            if (args.GetBool("s_looping")) {
                refSound.parms.soundShaderFlags = refSound.parms.soundShaderFlags or Sound.SSF_LOOPING
            }
            if (args.GetBool("s_occlusion")) {
                refSound.parms.soundShaderFlags = refSound.parms.soundShaderFlags or Sound.SSF_NO_OCCLUSION
            }
            if (args.GetBool("s_global")) {
                refSound.parms.soundShaderFlags = refSound.parms.soundShaderFlags or Sound.SSF_GLOBAL
            }
            if (args.GetBool("s_unclamped")) {
                refSound.parms.soundShaderFlags = refSound.parms.soundShaderFlags or Sound.SSF_UNCLAMPED
            }
            refSound.parms.soundClass = args.GetInt("s_soundClass")
            temp = args.GetString("s_shader")
            if (temp.isNotEmpty()) {
                refSound.shader = DeclManager.declManager.FindSound(temp)
            }
        }

        // Animation system calls for non-game based skeletal rendering.
        fun ANIM_GetModelFromEntityDef(classname: String): idRenderModel? {
            val args: idDict?
            args = Game_local.gameLocal.FindEntityDefDict(classname, false)
            return args?.let { ANIM_GetModelFromEntityDef(it) }
        }

        fun ANIM_GetModelOffsetFromEntityDef(classname: String): idVec3 {
            val args: idDict?
            val modelDef: idDeclModelDef?
            args = Game_local.gameLocal.FindEntityDefDict(classname, false)
            if (null == args) {
                return getVec3Origin()
            }
            modelDef = ANIM_GetModelDefFromEntityDef(args)
            return if (null == modelDef) {
                getVec3Origin()
            } else modelDef.GetVisualOffset()
        }

        fun ANIM_GetModelFromEntityDef(args: idDict): idRenderModel? {
            var model: idRenderModel?
            val modelDef: idDeclModelDef
            model = null
            val name = args.GetString("model")
            modelDef = DeclManager.declManager.FindType(declType_t.DECL_MODELDEF, name, false) as idDeclModelDef
            if (modelDef != null) {
                model = modelDef.ModelHandle()
            }
            if (null == model) {
                model = ModelManager.renderModelManager.FindModel(name)
            }
            return if (model != null && model.IsDefaultModel()) {
                null
            } else model
        }

        fun ANIM_GetModelFromName(modelName: String): idRenderModel? {
            val modelDef: idDeclModelDef?
            var model: idRenderModel?
            model = null
            modelDef = DeclManager.declManager.FindType(declType_t.DECL_MODELDEF, modelName, false) as idDeclModelDef?
            if (modelDef != null) {
                model = modelDef.ModelHandle()
            }
            if (null == model) {
                model = ModelManager.renderModelManager.FindModel(modelName)
            }
            return model
        }

        fun ANIM_GetAnimFromEntityDef(classname: String, animname: String): idMD5Anim? {
            val args: idDict?
            var md5anim: idMD5Anim?
            val anim: idAnim?
            val animNum: Int
            val modelname: String?
            val modelDef: idDeclModelDef?
            args = Game_local.gameLocal.FindEntityDefDict(classname, false)
            if (null == args) {
                return null
            }
            md5anim = null
            modelname = args.GetString("model")
            modelDef = DeclManager.declManager.FindType(declType_t.DECL_MODELDEF, modelname, false) as idDeclModelDef?
            if (modelDef != null) {
                animNum = modelDef.GetAnim(animname)
                if (animNum != 0) {
                    anim = modelDef.GetAnim(animNum)
                    if (anim != null) {
                        md5anim = anim.MD5Anim(0)
                    }
                }
            }
            return md5anim
        }

        fun ANIM_GetNumAnimsFromEntityDef(args: idDict): Int {
            val modelname: String?
            val modelDef: idDeclModelDef
            modelname = args.GetString("model")
            modelDef = DeclManager.declManager.FindType(declType_t.DECL_MODELDEF, modelname, false) as idDeclModelDef
            return modelDef.NumAnims()
        }

        fun ANIM_GetAnimNameFromEntityDef(args: idDict, animNum: Int): String {
            val modelname: String?
            val modelDef: idDeclModelDef?
            modelname = args.GetString("model")
            modelDef = DeclManager.declManager.FindType(declType_t.DECL_MODELDEF, modelname, false) as idDeclModelDef?
            if (modelDef != null) {
                val anim = modelDef.GetAnim(animNum)
                if (anim != null) {
                    return anim.FullName()
                }
            }
            return ""
        }

        fun ANIM_GetAnim(fileName: String): idMD5Anim? {
            return Game_local.animationLib.GetAnim(fileName)
        }

        fun ANIM_GetLength(anim: idMD5Anim?): Int {
            return anim?.Length() ?: 0
        }

        fun ANIM_GetNumFrames(anim: idMD5Anim?): Int {
            return anim?.NumFrames() ?: 0
        }

        fun ANIM_CreateAnimFrame(
            model: idRenderModel?,
            anim: idMD5Anim?,
            numJoints: Int,
            joints: Array<idJointMat?>?,
            time: Int,
            offset: idVec3,
            remove_origin_offset: Boolean
        ) {
            var i: Int
            val frame = frameBlend_t()
            val md5joints: Array<idMD5Joint>
            val index: IntArray
            if (null == model || model.IsDefaultModel() || null == anim) {
                return
            }
            if (numJoints != model.NumJoints()) {
                idGameLocal.Error(
                    "ANIM_CreateAnimFrame: different # of joints in renderEntity_t than in model (%s)",
                    model.Name()
                )
            }
            if (0 == model.NumJoints()) {
                // FIXME: Print out a warning?
                return
            }
            if (joints == null) {
                idGameLocal.Error(
                    "ANIM_CreateAnimFrame: NULL joint frame pointer on model (%s)",
                    model.Name()
                )
            }
            if (numJoints != anim.NumJoints()) {
                Game_local.gameLocal.Warning(
                    "Model '%s' has different # of joints than anim '%s'",
                    model.Name(),
                    anim.Name()
                )
                i = 0
                while (i < numJoints) {
                    joints!![i]!!.SetRotation(idMat3.getMat3_identity())
                    joints[i]!!.SetTranslation(offset)
                    i++
                }
                return
            }

            // create index for all joints
            index = IntArray(numJoints)
            i = 0
            while (i < numJoints) {
                index[i] = i
                i++
            }

            // create the frame
            anim.ConvertTimeToFrame(time, 1, frame)
            val jointFrame = arrayOfNulls<idJointQuat>(numJoints)
            anim.GetInterpolatedFrame(frame, jointFrame as Array<idJointQuat>, index, numJoints)

            // convert joint quaternions to joint matrices
            SIMDProcessor!!.ConvertJointQuatsToJointMats(
                joints as Array<idJointMat>,
                jointFrame as Array<idJointQuat>,
                numJoints
            )

            // first joint is always root of entire hierarchy
            if (remove_origin_offset) {
                joints[0].SetTranslation(offset)
            } else {
                joints[0].SetTranslation(joints[0].ToVec3().plus(offset))
            }

            // transform the children
            md5joints = model.GetJoints() as Array<idMD5Joint>
            i = 1
            while (i < numJoints) {
                joints[i].timesAssign(joints[md5joints.indexOf(md5joints[i].parent)])
                i++
            }
        }

        fun ANIM_CreateMeshForAnim(
            model: idRenderModel?,
            classname: String,
            animName: Array<String>,
            frame: Int,
            remove_origin_offset: Boolean
        ): idRenderModel? {
            val ent = renderEntity_s()
            val args: idDict?
            val temp: String?
            val newmodel: idRenderModel?
            val md5anim: idMD5Anim?
            var filename = idStr()
            val extension = idStr()
            val anim: idAnim?
            val animNum: Int
            val offset = idVec3()
            val modelDef: idDeclModelDef?
            if (null == model || model.IsDefaultModel()) {
                return null
            }
            args = Game_local.gameLocal.FindEntityDefDict(classname, false)
            if (null == args) {
                return null
            }

            //	memset( &ent, 0, sizeof( ent ) );
            ent.bounds.Clear()
            ent.suppressSurfaceInViewID = 0
            modelDef = ANIM_GetModelDefFromEntityDef(args)
            if (modelDef != null) {
                animNum = modelDef.GetAnim(animName[0])
                if (0 == animNum) {
                    return null
                }
                anim = modelDef.GetAnim(animNum)
                if (null == anim) {
                    return null
                }
                md5anim = anim.MD5Anim(0)
                ent.customSkin = modelDef.GetDefaultSkin()
                offset.set(modelDef.GetVisualOffset())
            } else {
                filename = idStr(animName[0])
                filename.ExtractFileExtension(extension)
                if (0 == extension.Length()) {
                    animName[0] = args.GetString(Str.va("anim %s", animName[0]))
                }
                md5anim = Game_local.animationLib.GetAnim(animName[0])
                offset.Zero()
            }
            if (null == md5anim) {
                return null
            }
            temp = args.GetString("skin", "")!!
            if (temp.isNotEmpty()) {
                ent.customSkin = DeclManager.declManager.FindSkin(temp)
            }
            ent.numJoints = model.NumJoints()
            ent.joints = Array(ent.numJoints) { idJointMat() }
            ANIM_CreateAnimFrame(
                model,
                md5anim,
                ent.numJoints,
                ent.joints,
                Anim.FRAME2MS(frame),
                offset,
                remove_origin_offset
            )
            newmodel = model.InstantiateDynamicModel(ent, null, null)
            ent.joints = null //Mem_Free16(ent.joints);
            return newmodel
        }

        // Articulated Figure calls for AF editor and Radiant.
        fun AF_SpawnEntity(fileName: String): Boolean {
            val args = idDict()
            val player: idPlayer?
            val ent: idAFEntity_Generic
            val af: idDeclAF?
            val org = idVec3()
            val yaw: Float
            player = Game_local.gameLocal.GetLocalPlayer()
            if (null == player || !Game_local.gameLocal.CheatsOk(false)) {
                return false
            }
            af = TempDump.dynamic_cast(
                idDeclAF::class.java,
                DeclManager.declManager.FindType(declType_t.DECL_AF, fileName)!!
            ) as idDeclAF?
            if (null == af) {
                return false
            }
            yaw = player.viewAngles.yaw
            args.Set("angle", Str.va("%f", yaw + 180))
            org.set(
                player.GetPhysics().GetOrigin()
                    .plus(idAngles(0.0f, yaw, 0.0f).ToForward().times(80.0f).plus(idVec3(0, 0, 1)))
            )
            args.Set("origin", org.ToString())
            args.Set("spawnclass", "idAFEntity_Generic")
            if (!af.model.IsEmpty()) {
                args.Set("model", af.model.toString())
            } else {
                args.Set("model", fileName)
            }
            if (!af.skin.IsEmpty()) {
                args.Set("skin", af.skin.toString())
            }
            args.Set("articulatedFigure", fileName)
            args.Set("nodrop", "1")
            ent = Game_local.gameLocal.SpawnEntityType(idAFEntity_Generic::class.java, args) as idAFEntity_Generic

            // always update this entity
            ent.BecomeActive(TH_THINK)
            ent.KeepRunningPhysics()
            ent.fl.forcePhysicsUpdate = true
            player.dragEntity.SetSelected(ent)
            return true
        }

        fun AF_UpdateEntities(fileName: String) {
            var ent: idEntity?
            var af: idAFEntity_Base?
            val name: idStr
            name = idStr(fileName)
            name.StripFileExtension()

            // reload any idAFEntity_Generic which uses the given articulated figure file
            ent = Game_local.gameLocal.spawnedEntities.Next()
            while (ent != null) {
                if (ent is idAFEntity_Base) {
                    af = ent
                    if (name.Icmp(af.GetAFName()) == 0) {
                        af.LoadAF()
                        af.GetAFPhysics().PutToRest()
                    }
                }
                ent = ent.spawnNode.Next()
            }
        }

        fun AF_UndoChanges() {
            var i: Int
            val c: Int
            var ent: idEntity?
            var af: idAFEntity_Base
            var decl: idDeclAF
            c = DeclManager.declManager.GetNumDecls(declType_t.DECL_AF)
            i = 0
            while (i < c) {
                decl = DeclManager.declManager.DeclByIndex(declType_t.DECL_AF, i, false) as idDeclAF
                if (!decl.modified) {
                    i++
                    continue
                }
                decl.Invalidate()
                DeclManager.declManager.FindType(declType_t.DECL_AF, decl.GetName())

                // reload all AF entities using the file
                ent = Game_local.gameLocal.spawnedEntities.Next()
                while (ent != null) {
                    if (ent is idAFEntity_Base) {
                        af = ent
                        if (idStr.Icmp(decl.GetName(), af.GetAFName()) == 0) {
                            af.LoadAF()
                        }
                    }
                    ent = ent.spawnNode.Next()
                }
                i++
            }
        }

        fun AF_CreateMesh(
            args: idDict,
            meshOrigin: idVec3,
            meshAxis: idMat3,
            poseIsSet: BooleanArray
        ): idRenderModel? {
            var i: Int
            var jointNum: Int
            val af: idDeclAF?
            var fb = idDeclAF_Body()
            val ent: renderEntity_s
            val origin = idVec3()
            val axis = idMat3()
            val bodyAxis: Array<idMat3>
            val newBodyAxis: Array<idMat3>
            val modifiedAxis: Array<idMat3>
            val jointMod: Array<declAFJointMod_t?>
            val angles = idAngles()
            val defArgs: idDict?
            var arg: idKeyValue?
            val name = idStr()
            val data = jointTransformData_t()
            val classname: String?
            val afName: String?
            val modelName: String?
            val md5: idRenderModel?
            val modelDef: idDeclModelDef?
            val MD5anim: idMD5Anim?
            var MD5joint: idMD5Joint
            val MD5joints: Array<idMD5Joint>?
            val numMD5joints: Int
            val originalJoints: Array<idJointMat?>
            var parentNum: Int
            poseIsSet[0] = false
            meshOrigin.Zero()
            meshAxis.Identity()
            classname = args.GetString("classname")
            defArgs = Game_local.gameLocal.FindEntityDefDict(classname)

            // get the articulated figure
            afName = GetArgString(args, defArgs, "articulatedFigure")
            af = TempDump.dynamic_cast(
                idDeclAF::class.java,
                DeclManager.declManager.FindType(declType_t.DECL_AF, afName)!!
            ) as idDeclAF?
            if (null == af) {
                return null
            }

            // get the md5 model
            modelName = GetArgString(args, defArgs, "model")
            modelDef = TempDump.dynamic_cast(
                idDeclModelDef::class.java,
                DeclManager.declManager.FindType(declType_t.DECL_MODELDEF, modelName, false)!!
            ) as idDeclModelDef?
            if (null == modelDef) {
                return null
            }

            // make sure model hasn't been purged
            if (modelDef.ModelHandle() != null && !modelDef.ModelHandle()!!.IsLoaded()) {
                modelDef.ModelHandle()!!.LoadModel()
            }

            // get the md5
            md5 = modelDef.ModelHandle()
            if (null == md5 || md5.IsDefaultModel()) {
                return null
            }

            // get the articulated figure pose anim
            val animNum = modelDef.GetAnim("af_pose")
            if (animNum == 0) {
                return null
            }
            val anim = modelDef.GetAnim(animNum)
            if (null == anim) {
                return null
            }
            MD5anim = anim.MD5Anim(0)
            MD5joints = md5.GetJoints() as Array<idMD5Joint>
            numMD5joints = md5.NumJoints()

            // setup a render entity
            ent = renderEntity_s() //memset( &ent, 0, sizeof( ent ) );
            ent.customSkin = modelDef.GetSkin()
            ent.bounds.Clear()
            ent.numJoints = numMD5joints
            ent.joints = Array(ent.numJoints) { idJointMat() }

            // create animation from of the af_pose
            ANIM_CreateAnimFrame(
                md5,
                MD5anim,
                ent.numJoints,
                ent.joints,
                1,
                modelDef.GetVisualOffset(),
                false
            )

            // buffers to store the initial origin and axis for each body
            val bodyOrigin: Array<idVec3> = idVec3.generateArray(af.bodies.Num())
            bodyAxis = Array(af.bodies.Num()) { idMat3() }
            val newBodyOrigin: Array<idVec3> = idVec3.generateArray(af.bodies.Num())
            newBodyAxis = Array(af.bodies.Num()) { idMat3() }

            // finish the AF positions
            data.ent = ent
            data.joints = MD5joints
            af.Finish(GetJointTransform.INSTANCE, ent.joints as Array<idJointMat>, data)

            // get the initial origin and axis for each AF body
            i = 0
            while (i < af.bodies.Num()) {
                fb = af.bodies[i]
                if (fb.modelType == traceModel_t.TRM_BONE) {
                    // axis of bone trace model
                    axis[2] = fb.v2.ToVec3().minus(fb.v1.ToVec3())
                    axis[2].Normalize()
                    axis[2].NormalVectors(axis[0], axis[1])
                    axis[1] = axis[1].unaryMinus()
                } else {
                    axis.set(fb.angles.ToMat3())
                }
                bodyOrigin[i].set(fb.origin.ToVec3())
                newBodyOrigin[i].set(bodyOrigin[i])
                bodyAxis[i].set(axis)
                newBodyAxis[i].set(bodyAxis[i])
                i++
            }

            // get any new body transforms stored in the key/value pairs
            arg = args.MatchPrefix("body ", null)
            while (arg != null) {
                name.set(arg.GetKey())
                name.Strip("body ")
                i = 0
                while (i < af.bodies.Num()) {
                    fb = af.bodies[i]
                    if (fb.name.Icmp(name) == 0) {
                        break
                    }
                    i++
                }
                if (i >= af.bodies.Num()) {
                    arg = args.MatchPrefix("body ", arg)
                    continue
                }
                //		sscanf( arg.GetValue(), "%f %f %f %f %f %f", &origin.x, &origin.y, &origin.z, &angles.pitch, &angles.yaw, &angles.roll );
                val sscanf = Scanner(arg.GetValue().toString())
                sscanf.useLocale(Locale.US)
                origin.x = sscanf.nextFloat()
                origin.y = sscanf.nextFloat()
                origin.z = sscanf.nextFloat()
                angles.pitch = sscanf.nextFloat()
                angles.yaw = sscanf.nextFloat()
                angles.roll = sscanf.nextFloat()
                if (fb.jointName.Icmp("origin") == 0) {
                    meshAxis.set(bodyAxis[i]!!.Transpose().times(angles.ToMat3()))
                    meshOrigin.set(origin.minus(bodyOrigin[i].times(meshAxis)))
                    poseIsSet[0] = true
                } else {
                    newBodyOrigin[i].set(origin)
                    newBodyAxis[i].set(angles.ToMat3())
                }
                arg = args.MatchPrefix("body ", arg)
            }

            // save the original joints
            originalJoints = Array(numMD5joints) { idJointMat(ent.joints!![it]!!) }
            // buffer to store the joint mods
            jointMod =
                arrayOfNulls<declAFJointMod_t>(numMD5joints) //memset(jointMod, -1, numMD5joints * sizeof(declAFJointMod_t));
            val modifiedOrigin: Array<idVec3> =
                idVec3.generateArray(numMD5joints) //memset(modifiedOrigin, 0, numMD5joints * sizeof(idVec3));
            modifiedAxis =
                Array<idMat3>(numMD5joints) { idMat3() }//memset(modifiedAxis, 0, numMD5joints * sizeof(idMat3));

            // get all the joint modifications
            i = 0
            while (i < af.bodies.Num()) {
                fb = af.bodies[i]
                if (fb.jointName.Icmp("origin") == 0) {
                    i++
                    continue
                }
                jointNum = 0
                while (jointNum < numMD5joints) {
                    if (MD5joints!![jointNum].name!!.Icmp(fb.jointName) == 0) {
                        break
                    }
                    jointNum++
                }
                if (jointNum >= 0 && jointNum < ent.numJoints) {
                    jointMod[jointNum] = fb.jointMod
                    modifiedAxis[jointNum].set(
                        bodyAxis[i]!!.times(originalJoints[jointNum]!!.ToMat3().Transpose()).Transpose()
                            .times(newBodyAxis[i]!!.times(meshAxis.Transpose()))
                    )
                    // FIXME: calculate correct modifiedOrigin
                    modifiedOrigin[jointNum].set(originalJoints[jointNum]!!.ToVec3())
                }
                i++
            }

            // apply joint modifications to the skeleton
            i = 1
            while (i < numMD5joints) {
                MD5joint = MD5joints!![i]
                parentNum = MD5joints.indexOf(MD5joint.parent)
                val parentAxis = originalJoints[parentNum]!!.ToMat3()
                val localm = originalJoints[i]!!.ToMat3().times(parentAxis.Transpose())
                val localt = idVec3(
                    originalJoints[i]!!.ToVec3().minus(originalJoints[parentNum]!!.ToVec3())
                        .times(parentAxis.Transpose())
                )
                when (jointMod[i]) {
                    declAFJointMod_t.DECLAF_JOINTMOD_ORIGIN -> {
                        ent.joints!![i]!!.SetRotation(localm.times(ent.joints!![parentNum]!!.ToMat3()))
                        ent.joints!![i]!!.SetTranslation(modifiedOrigin[i])
                    }

                    declAFJointMod_t.DECLAF_JOINTMOD_AXIS -> {
                        ent.joints!![i]!!.SetRotation(modifiedAxis[i]!!)
                        ent.joints!![i]!!.SetTranslation(
                            ent.joints!![parentNum]!!.ToVec3().plus(localt.times(ent.joints!![parentNum]!!.ToMat3()))
                        )
                    }

                    declAFJointMod_t.DECLAF_JOINTMOD_BOTH -> {
                        ent.joints!![i]!!.SetRotation(modifiedAxis[i]!!)
                        ent.joints!![i]!!.SetTranslation(modifiedOrigin[i])
                    }

                    else -> {
                        ent.joints!![i]!!.SetRotation(localm.times(ent.joints!![parentNum]!!.ToMat3()))
                        ent.joints!![i]!!.SetTranslation(
                            ent.joints!![parentNum]!!.ToVec3().plus(localt.times(ent.joints!![parentNum]!!.ToMat3()))
                        )
                    }
                }
                i++
            }

            // instantiate a mesh using the joint information from the render entity
            return md5.InstantiateDynamicModel(ent, null, null)
        }

        // Entity selection.
        fun ClearEntitySelection() {
            var ent: idEntity?
            ent = Game_local.gameLocal.spawnedEntities.Next()
            while (ent != null) {
                ent.fl.selected = false
                ent = ent.spawnNode.Next()
            }
            Game_local.gameLocal.editEntities!!.ClearSelectedEntities()
        }

        fun GetSelectedEntities(list: Array<idEntity>, max: Int): Int {
            var num = 0
            var ent: idEntity?
            ent = Game_local.gameLocal.spawnedEntities.Next()
            while (ent != null) {
                if (ent.fl.selected) {
                    list[num++] = ent
                    if (num >= max) {
                        break
                    }
                }
                ent = ent.spawnNode.Next()
            }
            return num
        }

        fun AddSelectedEntity(ent: idEntity?) {
            if (ent != null) {
                Game_local.gameLocal.editEntities!!.AddSelectedEntity(ent)
            }
        }

        // Selection methods
        fun TriggerSelected() {
            var ent: idEntity?
            ent = Game_local.gameLocal.spawnedEntities.Next()
            while (ent != null) {
                if (ent.fl.selected) {
                    ent.ProcessEvent(EV_Activate, Game_local.gameLocal.GetLocalPlayer())
                }
                ent = ent.spawnNode.Next()
            }
        }

        // Entity defs and spawning.
        fun FindEntityDefDict(name: String, makeDefault: Boolean /*= true*/): idDict? {
            return Game_local.gameLocal.FindEntityDefDict(name, makeDefault)
        }

        fun SpawnEntityDef(args: idDict, ent: Array<idEntity?>?) {
            Game_local.gameLocal.SpawnEntityDef(args, ent)
        }

        fun FindEntity(name: String): idEntity? {
            return Game_local.gameLocal.FindEntity(name)
        }

        fun GetUniqueEntityName(classname: String): String {
            var id: Int

            // can only have MAX_GENTITIES, so if we have a spot available, we're guaranteed to find one
            id = 0
            while (id < Game_local.MAX_GENTITIES) {
                idStr.snPrintf(name, name.capacity(), "%s_%d", classname, id)
                if (Game_local.gameLocal.FindEntity(name.toString()) == null) {
                    return name.toString()
                }
                id++
            }

            // id == MAX_GENTITIES + 1, which can't be in use if we get here
            idStr.snPrintf(name, name.capacity(), "%s_%d", classname, id)
            return name.toString()
        }

        // Entity methods.
        fun EntityGetOrigin(ent: idEntity?, org: idVec3) {
            if (ent != null) {
                org.set(ent.GetPhysics().GetOrigin())
            }
        }

        fun EntityGetAxis(ent: idEntity?, axis: idMat3) {
            if (ent != null) {
                axis.set(ent.GetPhysics().GetAxis())
            }
        }

        fun EntitySetOrigin(ent: idEntity?, org: idVec3) {
            ent?.SetOrigin(org)
        }

        fun EntitySetAxis(ent: idEntity?, axis: idMat3) {
            ent?.SetAxis(axis)
        }

        fun EntityTranslate(ent: idEntity?, org: idVec3) {
            ent?.GetPhysics()?.Translate(org)
        }

        fun EntityGetSpawnArgs(ent: idEntity?): idDict? {
            return ent?.spawnArgs
        }

        fun EntityUpdateChangeableSpawnArgs(ent: idEntity?, dict: idDict?) {
            ent?.UpdateChangeableSpawnArgs(dict)
        }

        fun EntityChangeSpawnArgs(ent: idEntity?, newArgs: idDict) {
            if (ent != null) {
                for (i in 0 until newArgs.GetNumKeyVals()) {
                    val kv = newArgs.GetKeyVal(i)!!
                    if (kv.GetValue().Length() > 0) {
                        ent.spawnArgs.Set(kv.GetKey(), kv.GetValue())
                    } else {
                        ent.spawnArgs.Delete(kv.GetKey())
                    }
                }
            }
        }

        fun EntityUpdateVisuals(ent: idEntity?) {
            ent?.UpdateVisuals()
        }

        fun EntitySetModel(ent: idEntity?, `val`: String) {
            if (ent != null) {
                ent.spawnArgs.Set("model", `val`)
                ent.SetModel(`val`)
            }
        }

        fun EntityStopSound(ent: idEntity?) {
            ent?.StopSound(TempDump.etoi(gameSoundChannel_t.SND_CHANNEL_ANY), false)
        }

        fun EntityDelete(ent: idEntity?) {}
        fun EntitySetColor(ent: idEntity?, color: idVec3) {
            ent?.SetColor(color)
        }

        // Player methods.
        fun PlayerIsValid(): Boolean {
            return Game_local.gameLocal.GetLocalPlayer() != null
        }

        fun PlayerGetOrigin(org: idVec3) {
            org.set(Game_local.gameLocal.GetLocalPlayer()!!.GetPhysics().GetOrigin())
        }

        fun PlayerGetAxis(axis: idMat3) {
            axis.set(Game_local.gameLocal.GetLocalPlayer()!!.GetPhysics().GetAxis())
        }

        fun PlayerGetViewAngles(angles: idAngles) {
            angles.set(Game_local.gameLocal.GetLocalPlayer()!!.viewAngles)
        }

        fun PlayerGetEyePosition(org: idVec3) {
            org.set(Game_local.gameLocal.GetLocalPlayer()!!.GetEyePosition())
        }

        // In game map editing support.
        fun MapGetEntityDict(name: String?): idDict? {
            val mapFile = Game_local.gameLocal.GetLevelMap()
            if (mapFile != null && null != name && !name.isNullOrEmpty()) {
                val mapent = mapFile.FindEntity(name)
                if (mapent != null) {
                    return mapent.epairs
                }
            }
            return null
        }

        fun MapSave(path: String? /*= NULL*/) {
            val mapFile = Game_local.gameLocal.GetLevelMap()
            mapFile?.Write(path ?: mapFile.GetName(), ".map")
        }

        fun MapSetEntityKeyVal(name: String?, key: String?, `val`: String) {
            val mapFile = Game_local.gameLocal.GetLevelMap()
            if (mapFile != null && null != name && !name.isNullOrEmpty()) {
                val mapent = mapFile.FindEntity(name)
                mapent?.epairs?.Set(key, `val`)
            }
        }

        fun MapCopyDictToEntity(name: String, dict: idDict) {
            val mapFile = Game_local.gameLocal.GetLevelMap()
            if (mapFile != null && name.isNotEmpty()) {
                val mapent = mapFile.FindEntity(name)
                if (mapent != null) {
                    for (i in 0 until dict.GetNumKeyVals()) {
                        val kv = dict.GetKeyVal(i)!!
                        val key = kv.GetKey().toString()
                        val `val` = kv.GetValue().toString()
                        mapent.epairs.Set(key, `val`)
                    }
                }
            }
        }

        fun MapGetUniqueMatchingKeyVals(key: String, list: Array<String>, max: Int): Int {
            val mapFile = Game_local.gameLocal.GetLevelMap()
            var count = 0
            if (mapFile != null) {
                for (i in 0 until mapFile.GetNumEntities()) {
                    val ent = mapFile.GetEntity(i)
                    if (ent != null) {
                        val k = ent.epairs.GetString(key)
                        if (k.isNotEmpty() && count < max) {
                            list[count++] = k
                        }
                    }
                }
            }
            return count
        }

        fun MapAddEntity(dict: idDict) {
            val mapFile = Game_local.gameLocal.GetLevelMap()
            if (mapFile != null) {
                val ent = idMapEntity()
                ent.epairs.set(dict)
                mapFile.AddEntity(ent)
            }
        }

        fun MapGetEntitiesMatchingClassWithString(
            classname: String,
            match: String?,
            list: Array<String>,
            max: Int
        ): Int {
            val mapFile = Game_local.gameLocal.GetLevelMap()
            var count = 0
            if (mapFile != null) {
                val entCount = mapFile.GetNumEntities()
                for (i in 0 until entCount) {
                    val ent = mapFile.GetEntity(i)
                    if (ent != null) {
                        val work = idStr(ent.epairs.GetString("classname"))
                        if (work.Icmp(classname) == 0) {
                            if (!match.isNullOrEmpty()) {
                                work.set(ent.epairs.GetString("soundgroup"))
                                if (count < max && work.Icmp(match) == 0) {
                                    list[count++] = ent.epairs.GetString("name")
                                }
                            } else if (count < max) {
                                list[count++] = ent.epairs.GetString("name")
                            }
                        }
                    }
                }
            }
            return count
        }

        fun MapRemoveEntity(name: String) {
            val mapFile = Game_local.gameLocal.GetLevelMap()
            if (mapFile != null) {
                val ent = mapFile.FindEntity(name)
                if (ent != null) {
                    mapFile.RemoveEntity(ent)
                }
            }
        }

        fun MapEntityTranslate(name: String, v: idVec3) {
            val mapFile = Game_local.gameLocal.GetLevelMap()
            if (mapFile != null && name.isNotEmpty()) {
                val mapent = mapFile.FindEntity(name)
                if (mapent != null) {
                    val origin = idVec3()
                    mapent.epairs.GetVector("origin", "", origin)
                    origin.plusAssign(v)
                    mapent.epairs.SetVector("origin", origin)
                }
            }
        }

        companion object {
            /*
         =============
         idGameEdit::GetUniqueEntityName

         generates a unique name for a given classname
         =============
         */
            var name: StringBuffer = StringBuffer(1024)
        }
    }

    class gameImport_t {
        lateinit var AASFileManager // AAS file manager
                : idAASFileManager
        lateinit var cmdSystem // console command system
                : idCmdSystem
        lateinit var collisionModelManager // collision model manager
                : idCollisionModelManager
        lateinit var common // common
                : idCommon
        lateinit var cvarSystem // console variable system
                : idCVarSystem
        lateinit var declManager // declaration manager
                : idDeclManager
        lateinit var fileSystem // file system
                : idFileSystem
        lateinit var networkSystem // network system
                : idNetworkSystem
        lateinit var renderModelManager // render model manager
                : idRenderModelManager
        lateinit var renderSystem // render system
                : idRenderSystem
        lateinit var soundSystem // sound system
                : idSoundSystem
        lateinit var sys // non-portable system services
                : idSys
        lateinit var uiManager // user interface manager
                : idUserInterfaceManager
        var version // API version
                = 0
    }

    class gameExport_t {
        lateinit var game // interface to run the game
                : idGame
        lateinit var gameEdit // interface for in-game editing
                : idGameEdit
        var version // API version
                = 0
    }
}
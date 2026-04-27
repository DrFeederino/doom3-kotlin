package neo.Game

import neo.Game.AI.*
import neo.Game.AI.AAS.idAAS
import neo.Game.Animation.Anim.idAnimManager
import neo.Game.Animation.Anim_Testmodel.idTestModel
import neo.Game.Animation.Anim_Testmodel.idTestModel.*
import neo.Game.Animation.idDeclModelDef
import neo.Game.Game.allowReply_t
import neo.Game.Game.escReply_t
import neo.Game.Game.gameExport_t
import neo.Game.Game.gameImport_t
import neo.Game.Game.gameReturn_t
import neo.Game.Game.idGame
import neo.Game.GameEdit.idEditEntities
import neo.Game.GameSys.Class.*
import neo.Game.GameSys.Class.idClass.DisplayInfo_f
import neo.Game.GameSys.Class.idClass.ListClasses_f
import neo.Game.GameSys.EV_Remove
import neo.Game.GameSys.Event.idEvent
import neo.Game.GameSys.SaveGame.idRestoreGame
import neo.Game.GameSys.SaveGame.idSaveGame
import neo.Game.GameSys.SysCmds
import neo.Game.GameSys.SysCmds.ArgCompletion_DefFile
import neo.Game.GameSys.SysCmds.Cmd_AASStats_f
import neo.Game.GameSys.SysCmds.Cmd_ActiveEntityList_f
import neo.Game.GameSys.SysCmds.Cmd_AddChatLine_f
import neo.Game.GameSys.SysCmds.Cmd_AddDebugLine_f
import neo.Game.GameSys.SysCmds.Cmd_BindRagdoll_f
import neo.Game.GameSys.SysCmds.Cmd_BlinkDebugLine_f
import neo.Game.GameSys.SysCmds.Cmd_CenterView_f
import neo.Game.GameSys.SysCmds.Cmd_ClearLights_f
import neo.Game.GameSys.SysCmds.Cmd_CloseViewNotes_f
import neo.Game.GameSys.SysCmds.Cmd_CollisionModelInfo_f
import neo.Game.GameSys.SysCmds.Cmd_Damage_f
import neo.Game.GameSys.SysCmds.Cmd_DeleteSelected_f
import neo.Game.GameSys.SysCmds.Cmd_DisasmScript_f
import neo.Game.GameSys.SysCmds.Cmd_EntityList_f
import neo.Game.GameSys.SysCmds.Cmd_ExportModels_f
import neo.Game.GameSys.SysCmds.Cmd_GameError_f
import neo.Game.GameSys.SysCmds.Cmd_GetViewpos_f
import neo.Game.GameSys.SysCmds.Cmd_Give_f
import neo.Game.GameSys.SysCmds.Cmd_God_f
import neo.Game.GameSys.SysCmds.Cmd_Kick_f
import neo.Game.GameSys.SysCmds.Cmd_KillMonsters_f
import neo.Game.GameSys.SysCmds.Cmd_KillMovables_f
import neo.Game.GameSys.SysCmds.Cmd_KillRagdolls_f
import neo.Game.GameSys.SysCmds.Cmd_Kill_f
import neo.Game.GameSys.SysCmds.Cmd_ListAnims_f
import neo.Game.GameSys.SysCmds.Cmd_ListCollisionModels_f
import neo.Game.GameSys.SysCmds.Cmd_ListDebugLines_f
import neo.Game.GameSys.SysCmds.Cmd_ListSpawnArgs_f
import neo.Game.GameSys.SysCmds.Cmd_NextGUI_f
import neo.Game.GameSys.SysCmds.Cmd_Noclip_f
import neo.Game.GameSys.SysCmds.Cmd_Notarget_f
import neo.Game.GameSys.SysCmds.Cmd_PlayerModel_f
import neo.Game.GameSys.SysCmds.Cmd_PopLight_f
import neo.Game.GameSys.SysCmds.Cmd_RecordViewNotes_f
import neo.Game.GameSys.SysCmds.Cmd_ReexportModels_f
import neo.Game.GameSys.SysCmds.Cmd_ReloadAnims_f
import neo.Game.GameSys.SysCmds.Cmd_ReloadScript_f
import neo.Game.GameSys.SysCmds.Cmd_RemoveDebugLine_f
import neo.Game.GameSys.SysCmds.Cmd_Remove_f
import neo.Game.GameSys.SysCmds.Cmd_SaveLights_f
import neo.Game.GameSys.SysCmds.Cmd_SaveMoveables_f
import neo.Game.GameSys.SysCmds.Cmd_SaveParticles_f
import neo.Game.GameSys.SysCmds.Cmd_SaveRagdolls_f
import neo.Game.GameSys.SysCmds.Cmd_SaveSelected_f
import neo.Game.GameSys.SysCmds.Cmd_SayTeam_f
import neo.Game.GameSys.SysCmds.Cmd_Say_f
import neo.Game.GameSys.SysCmds.Cmd_Script_f
import neo.Game.GameSys.SysCmds.Cmd_SetViewpos_f
import neo.Game.GameSys.SysCmds.Cmd_ShowViewNotes_f
import neo.Game.GameSys.SysCmds.Cmd_Spawn_f
import neo.Game.GameSys.SysCmds.Cmd_Teleport_f
import neo.Game.GameSys.SysCmds.Cmd_TestBoneFx_f
import neo.Game.GameSys.SysCmds.Cmd_TestDamage_f
import neo.Game.GameSys.SysCmds.Cmd_TestDeath_f
import neo.Game.GameSys.SysCmds.Cmd_TestFx_f
import neo.Game.GameSys.SysCmds.Cmd_TestId_f
import neo.Game.GameSys.SysCmds.Cmd_TestLight_f
import neo.Game.GameSys.SysCmds.Cmd_TestPointLight_f
import neo.Game.GameSys.SysCmds.Cmd_TestSave_f
import neo.Game.GameSys.SysCmds.Cmd_Trigger_f
import neo.Game.GameSys.SysCmds.Cmd_UnbindRagdoll_f
import neo.Game.GameSys.SysCmds.Cmd_WeaponSplat_f
import neo.Game.GameSys.SysCvar
import neo.Game.GameSys.TypeInfo.ListTypeInfo_f
import neo.Game.GameSys.TypeInfo.TestSaveGame_f
import neo.Game.GameSys.TypeInfo.WriteGameState_f
import neo.Game.GameSys.registerAllTypes
import neo.Game.Game_network.idEventQueue
import neo.Game.Game_network.idEventQueue.outOfOrderBehaviour_t
import neo.Game.Misc.idLocationEntity
import neo.Game.Misc.idPathCorner
import neo.Game.MultiplayerGame.gameType_t
import neo.Game.MultiplayerGame.idMultiplayerGame
import neo.Game.MultiplayerGame.idMultiplayerGame.*
import neo.Game.MultiplayerGame.snd_evt_t
import neo.Game.Physics.Clip.idClip
import neo.Game.Physics.Clip.idClipModel
import neo.Game.Physics.Force.idForce
import neo.Game.Physics.Physics.idPhysics
import neo.Game.Physics.Physics_Actor.idPhysics_Actor
import neo.Game.Physics.Physics_Parametric.idPhysics_Parametric
import neo.Game.Physics.Push.idPush
import neo.Game.Player.HELLTIME
import neo.Game.Player.idPlayer
import neo.Game.Projectile.idProjectile
import neo.Game.Pvs.idPVS
import neo.Game.Pvs.pvsHandle_t
import neo.Game.Pvs.pvsType_t
import neo.Game.Script.Script_Program.function_t
import neo.Game.Script.Script_Thread.idThread
import neo.Game.Script.Script_Thread.idThread.ListThreads_f
import neo.Game.Script.idProgram
import neo.Game.SmokeParticles.idSmokeParticles
import neo.Game.Trigger.idTrigger
import neo.Game.WorldSpawn.idWorldspawn
import neo.Renderer.*
import neo.Renderer.RenderWorld.idRenderWorld
import neo.Renderer.RenderWorld.modelTrace_s
import neo.Renderer.RenderWorld.portalConnection_t
import neo.Renderer.RenderWorld.renderView_s
import neo.Sound.snd_shader
import neo.Sound.snd_shader.idSoundShader
import neo.Sound.snd_system
import neo.Sound.sound.idSoundWorld
import neo.TempDump
import neo.TempDump.etoi
import neo.TempDump.void_callback
import neo.Tools.Compilers.AAS.AASFileManager
import neo.cm.collisionModelManager
import neo.cm.setCollisionModelManagers
import neo.cm.trace_s
import neo.framework.*
import neo.framework.Async.NetworkSystem
import neo.framework.CVarSystem.idCVar
import neo.framework.CmdSystem.cmdExecution_t
import neo.framework.CmdSystem.cmdFunction_t
import neo.framework.CmdSystem.idCmdSystem.ArgCompletion_Decl
import neo.framework.DeclEntityDef.idDeclEntityDef
import neo.framework.DeclManager.*
import neo.framework.DeclManager.Companion.declManager
import neo.framework.File_h.idFile
import neo.framework.Licensee.D3_ARCH
import neo.framework.Licensee.D3_OSTYPE
import neo.framework.Licensee.D3_SHORT_SIZE
import neo.framework.Licensee.ENGINE_VERSION
import neo.framework.UsercmdGen.usercmd_t
import neo.idlib.*
import neo.idlib.BV.idBounds
import neo.idlib.BitMsg.idBitMsg
import neo.idlib.BitMsg.idBitMsgDelta
import neo.idlib.Dict_h.idDict
import neo.idlib.Dict_h.idKeyValue
import neo.idlib.MapFile.idMapEntity
import neo.idlib.MapFile.idMapFile
import neo.idlib.Text.Str
import neo.idlib.Text.Str.idStr
import neo.idlib.Timer.idTimer
import neo.idlib.containers.*
import neo.idlib.containers.LinkList.idLinkList
import neo.idlib.containers.List.cmp_t
import neo.idlib.containers.List.idList
import neo.idlib.geometry.TraceModel.idTraceModel
import neo.idlib.geometry.TraceModel.traceModelPoly_t
import neo.idlib.geometry.Winding.idFixedWinding
import neo.idlib.math.*
import neo.idlib.math.Matrix.idMat3
import neo.idlib.math.Random.idRandom
import neo.sys.setSysLocal
import neo.sys.sys
import neo.ui.UserInterface
import neo.ui.UserInterface.idUserInterface
import java.nio.ByteBuffer
import kotlin.math.*


class Game_local {
    // these defines work for all startsounds from all entity types
    // make sure to change script/doom_defs.script if you add any channels, or change their order
    enum class gameSoundChannel_t {
        SND_CHANNEL_ANY,  //= SCHANNEL_ANY,
        SND_CHANNEL_VOICE,  // = SCHANNEL_ONE,
        SND_CHANNEL_VOICE2,
        SND_CHANNEL_BODY,
        SND_CHANNEL_BODY2,
        SND_CHANNEL_BODY3,
        SND_CHANNEL_WEAPON,
        SND_CHANNEL_ITEM,
        SND_CHANNEL_HEART,
        SND_CHANNEL_PDA,
        SND_CHANNEL_DEMONIC,
        SND_CHANNEL_RADIO,  // internal use only.  not exposed to script or framecommands.
        SND_CHANNEL_AMBIENT,
        SND_CHANNEL_DAMAGE
    }

    enum class gameState_t {
        GAMESTATE_UNINITIALIZED,  // prior to Init being called
        GAMESTATE_NOMAP,  // no map loaded
        GAMESTATE_STARTUP,  // inside InitFromNewMap().  spawning map entities.
        GAMESTATE_ACTIVE,  // normal gameplay
        GAMESTATE_SHUTDOWN // inside MapShutdown().  clearing memory.
    }

    /*
     ===============================================================================
     Public game interface with methods for in-game editing.
     ===============================================================================
     */
    class entityState_s {
        var entityNumber = 0
        var next: entityState_s? = null
        var state = idBitMsg()
        var stateBuf = ByteBuffer.allocate(MAX_ENTITY_STATE_SIZE)
    }

    class snapshot_s {
        var firstEntityState: entityState_s? = null
        var next: snapshot_s? = null
        var pvs: IntArray = IntArray(ENTITY_PVS_SIZE)
        var sequence = 0
    }

    class entityNetEvent_s {
        var event = 0
        var next: entityNetEvent_s? = null
        var paramsBuf = ByteBuffer.allocate(MAX_EVENT_PARAM_SIZE)
        var paramsSize = 0
        var prev: entityNetEvent_s? = null
        var spawnId = 0
        var time = 0
    }

    class spawnSpot_t {
        var dist = 0
        var ent: idEntity? = null
        var team = 0 // D3XP CTF
    }

    // D3XP: dual-timeline state snapshot — used for fast (player-speed) and slow timelines
    class timeState_t {
        var time = 0
        var previousTime = 0
        var msec = 0
        var msecPrecise = 0.0f
        var framenum = 0
        var realClientTime = 0

        fun Set(t: Int, pt: Int, ms: Int, f: Int, rct: Int, msp: Float) {
            time = t; previousTime = pt; msec = ms; framenum = f; realClientTime = rct; msecPrecise = msp
        }

        fun Save(savefile: idSaveGame) {
            savefile.WriteInt(time)
            savefile.WriteInt(previousTime)
            savefile.WriteInt(msec)
            savefile.WriteInt(framenum)
            savefile.WriteInt(realClientTime)
        }

        fun Restore(savefile: idRestoreGame) {
            val t = CInt()
            val pt = CInt()
            val ms = CInt()
            val fn = CInt()
            val rct = CInt()
            savefile.ReadInt(t); savefile.ReadInt(pt); savefile.ReadInt(ms)
            savefile.ReadInt(fn); savefile.ReadInt(rct)
            time = t._val; previousTime = pt._val; msec = ms._val
            framenum = fn._val; realClientTime = rct._val
            msecPrecise = if (msec == 16 || msec == 17) USERCMD_MSEC_PRECISE else msec.toFloat()
        }

        fun Increment(ms: Int) {
            framenum++
            previousTime = time
            msec = ms
            time += msec
            realClientTime = time
        }
    }

    // D3XP: slow-motion state machine
    enum class slowmoState_t {
        SLOWMO_STATE_OFF,
        SLOWMO_STATE_RAMPUP,
        SLOWMO_STATE_ON,
        SLOWMO_STATE_RAMPDOWN
    }

    //============================================================================
    open class idEntityPtr<out entity : idEntity?> {
        private var spawnId = 0

        //
        //
        constructor() {
            spawnId = 0
            oSet(null)
        }

        constructor(ent: entity?) {
            oSet(ent)
        }

        // save games
        fun Save(savefile: idSaveGame) {                    // archives object for save game file
            savefile.WriteInt(spawnId)
        }

        fun Restore(savefile: idRestoreGame) {                    // unarchives object from save game file
            val spawnId = CInt()
            savefile.ReadInt(spawnId)
            this.spawnId = spawnId._val
        }

        fun oSet(ent: idEntity?): idEntityPtr<idEntity> {
            spawnId = if (ent == null) {
                0
            } else {
                val entityNumber = ent.entityNumber
                gameLocal.spawnIds[entityNumber] shl GENTITYNUM_BITS or entityNumber
            }
            return this as idEntityPtr<idEntity>
        }

        // synchronize entity pointers over the network
        fun GetSpawnId(): Int {
            return spawnId
        }

        fun SetSpawnId(id: Int): Boolean {
            // the reason for this first check is unclear:
            // the function returning false may mean the spawnId is already set right, or the entity is missing
            if (id == spawnId) {
                return false
            }
            if (id shr GENTITYNUM_BITS == gameLocal.spawnIds[id and (1 shl GENTITYNUM_BITS) - 1]) {
                spawnId = id
                return true
            }
            return false
        }

        //        public boolean UpdateSpawnId();
        fun IsValid(): Boolean {
            return gameLocal.spawnIds[spawnId and (1 shl GENTITYNUM_BITS) - 1] == spawnId shr GENTITYNUM_BITS
        }

        fun GetEntity(): entity? {
            val entityNum = spawnId and (1 shl GENTITYNUM_BITS) - 1
            return if (gameLocal.spawnIds[entityNum] == spawnId shr GENTITYNUM_BITS) {
                gameLocal.entities[entityNum] as entity?
            } else null
        }

        fun GetEntityNum(): Int {
            return spawnId and (1 shl GENTITYNUM_BITS) - 1
        }
    }

    //============================================================================
    class idGameLocal : idGame() {
        val lastGUIEnt // last entity with a GUI, used by Cmd_NextGUI_f
                : idEntityPtr<idEntity>

        //
        private val aasList: idList<idAAS> = idList() // area system
        private val aasNames: idStrList = idStrList()

        //
        private val clientDeclRemap: Array<Array<idList<Int>?>> = Array(MAX_CLIENTS) {
            arrayOfNulls(
                etoi(declType_t.DECL_MAX_TYPES)
            )
        }

        //        private final idBlockAlloc<entityState_s> entityStateAllocator = new idBlockAlloc<>(256);
        //        private final idBlockAlloc<snapshot_s> snapshotAllocator = new idBlockAlloc<>(64);
        //
        private val eventQueue: idEventQueue = idEventQueue()

        //
        private val gravity: idVec3 = idVec3() // global gravity vector
        private val initialSpots: StaticList.idStaticList<idEntity> = StaticList.idStaticList(MAX_GENTITIES)

        //
        private val lastAIAlertEntity: idEntityPtr<idActor?>

        //
        private val mapFileName: idStr = idStr() // name of the map, empty string if no map loaded
        private val savedEventQueue: idEventQueue = idEventQueue()

        //
        private val spawnArgs: idDict =
            idDict() // spawn args used during entity spawning  FIXME: shouldn't be necessary anymore

        //
        private val spawnSpots: StaticList.idStaticList<spawnSpot_t> = StaticList.idStaticList(MAX_GENTITIES)
        var activeEntities: idLinkList<idEntity> = idLinkList() // all thinking entities (idEntity::thinkFlags != 0)
        var cinematicMaxSkipTime // time to end cinematic when skipping.  there's a possibility of an infinite loop if the map isn't set up right.
                = 0

        //
        var cinematicSkipTime // don't allow skipping cinemetics until this time has passed so player doesn't skip out accidently from a firefight
                = 0
        var cinematicStopTime // cinematics have several camera changes, so keep track of when we stop them so that we don't reset cinematicSkipTime unnecessarily
                = 0
        var clientSmoothing // smoothing of other clients in the view
                = 0.0f

        //
        val clip: idClip = idClip() // collision detection
        var editEntities // in game editing
                : idEditEntities? = null
        var entities: Array<idEntity?> = arrayOfNulls(MAX_GENTITIES) // index to entities
        var entityDefBits // bits required to store an entity def number
                = 0
        var entityHash: idHashIndex = idHashIndex() // hash table to quickly find entities by name
        var firstFreeIndex // first free index in the entities array
                = 0
        var frameCommandThread: idThread? = null

        // DG: msec is now mutable, recalculated each frame via CalcMSec() so 60 frames = exactly 1000ms
        var msec: Int = UsercmdGen.USERCMD_MSEC

        // D3XP: precise float msec (1000/60 = 16.6667) — updated by SelectTimeGroup; shadows companion constant
        var msecPrecise: Float = USERCMD_MSEC_PRECISE

        //
        // are kept up to date with changes to serverInfo
        var framenum = 0
        var restoreFrame = 0  // === DIAGNOSTIC: frame when restore completed ===

        //
        var gameType: gameType_t = gameType_t.GAME_SP

        //
        // can be used to automatically effect every material in the world that references globalParms
        var globalShaderParms: FloatArray = FloatArray(RenderWorld.MAX_GLOBAL_SHADER_PARMS)
        var inCinematic // game is playing cinematic (player controls frozen)
                = false
        var isClient // set if the game is run for a client
                = false
        var isMultiplayer // set if the game is run in multiplayer mode
                = false
        var isNewFrame // true if this is a new game frame, not a rerun due to prediction
                = false
        var isServer // set if the game is run for a dedicated or listen server
                = false
        var lastGUI // last GUI on the lastGUIEnt
                = 0

        // D3XP: portal sky entity reference
        var portalSkyEnt: idEntityPtr<idEntity> = idEntityPtr()
        var portalSkyActive: Boolean = false

        // D3XP: dual-timeline state (fast = player-speed, slow = world-speed during slow-mo)
        val fast: timeState_t = timeState_t()
        val slow: timeState_t = timeState_t()

        // D3XP: slow-motion state machine
        var slowmoState: slowmoState_t = slowmoState_t.SLOWMO_STATE_OFF
        var slowmoMsec: Float = 0.0f
        var quickSlowmoReset: Boolean = false

        // NOTE: on a listen server, isClient is false
        var localClientNum // number of the local client. MP: -1 on a dedicated
                = 0

        //
        var mpGame: idMultiplayerGame = idMultiplayerGame() // handles rules for standard dm
        var numClients // pulled from serverInfo and verified
                = 0
        var numEntitiesToDeactivate // number of entities that became inactive in current frame
                = 0
        var num_entities // current number <= MAX_GENTITIES
                = 0
        var persistentLevelInfo: idDict = idDict() // contains args that are kept around between levels
        var persistentPlayerInfo: Array<idDict> = Array(MAX_CLIENTS) { idDict() }
        var previousTime // time in msec of last frame
                = 0

        //
        var program: idProgram = idProgram() // currently loaded script and data space
        var push: idPush = idPush() // geometric pushing
        var pvs: idPVS = idPVS() // potential visible set

        //
        var random: idRandom = idRandom() // random number generator used throughout the game
        var realClientTime // real client time
                = 0
        var serverInfo: idDict = idDict() // all the tunable parameters, like numclients, etc

        //
        var sessionCommand: idStr = idStr() // a target_sessionCommand can set this to return something to the session
        var skipCinematic = false

        //
        var smokeParticles // global smoke trails
                : idSmokeParticles? = null
        var snapshotEntities // entities from the last snapshot
                : idLinkList<idEntity> = idLinkList()
        var sortPushers // true if active lists needs to be reordered to place pushers at the front
                = false
        var sortTeamMasters // true if active lists needs to be reordered to place physics team masters before their slaves
                = false
        var spawnIds: IntArray = IntArray(MAX_GENTITIES) // for use in idEntityPtr
        var spawnedEntities: idLinkList<idEntity> = idLinkList() // all spawned entities
        var sufaceTypeNames: Array<String> = arrayOf(
            "none", "metal", "stone", "flesh", "wood", "cardboard", "liquid", "glass", "plastic",
            "ricochet", "surftype10", "surftype11", "surftype12", "surftype13", "surftype14", "surftype15"
        ) // text names for surface types
        var testFx // for development testing of fx
                : idEntityFx? = null

        //
        var testmodel // for development testing of models
                : idTestModel? = null
        var time // in msec
                = 0
        var userInfo: Array<idDict> = Array(MAX_CLIENTS) { idDict() } // client specific settings
        var usercmds = Array(MAX_CLIENTS) { usercmd_t() } // client input commands

        //
        var vacuumAreaNum // -1 if level doesn't have any outside areas
                = 0
        var world // world entity
                : idWorldspawn? = null

        //
        private var camera: idCamera? = null

        //
        private var clientEntityStates: Array<Array<entityState_s?>> =
            Array(MAX_CLIENTS) { arrayOfNulls(MAX_GENTITIES) }
        private var clientPVS: Array<IntArray> = Array(MAX_CLIENTS) { IntArray(ENTITY_PVS_SIZE) }
        private var clientSnapshots: Array<snapshot_s?> = arrayOfNulls(MAX_CLIENTS)
        private var currentInitialSpot = 0

        // D3XP CTF team spawn spots
        private val teamSpawnSpots: Array<StaticList.idStaticList<spawnSpot_t>> =
            Array(2) { StaticList.idStaticList(MAX_GENTITIES) }
        private val teamInitialSpots: Array<StaticList.idStaticList<idEntity>> =
            Array(2) { StaticList.idStaticList(MAX_GENTITIES) }
        private var teamCurrentInitialSpot: IntArray = IntArray(2)
        private var gamestate // keeps track of whether we're spawning, shutting down, or normal gameplay
                : gameState_t = gameState_t.GAMESTATE_UNINITIALIZED
        private var globalMaterial // for overriding everything
                : Material.idMaterial? = null
        private var influenceActive // true when a phantasm is happening
                = false

        //
        private var lagometer: Array<Array<ByteArray>> =
            Array(LAGO_IMG_HEIGHT) { Array(LAGO_IMG_WIDTH) { ByteArray(4) } }
        private var lastAIAlertTime = 0

        //
        private var locationEntities // for location names, etc
                : Array<idLocationEntity?>? = null
        private var mapCycleLoaded = false
        private var mapFile // will be NULL during the game unless in-game editing is used
                : idMapFile? = null
        private var mapSpawnCount // it's handy to know which entities are part of the map
                = 0

        //
        private var newInfo: idDict = idDict()
        private var nextGibTime = 0
        private var playerConnectedAreas: pvsHandle_t = pvsHandle_t() // all areas connected to any player area

        //
        //
        //
        // ---------------------- Public idGame Interface -------------------
        //
        private var playerPVS: pvsHandle_t = pvsHandle_t() // merged pvs of all players

        //
        private val shakeSounds: idStrList = idStrList()

        //
        private var spawnCount = 0

        /*
         ===========
         idGameLocal::Init

         initialize the game object, only happens once at startup, not each level load
         ============
         */
        override fun Init() {
            val dict: idDict?
            var aas: idAAS
            // FIX: Was inverted — C++ uses #ifndef GAME_DLL for TestGameAPI, #else for idLib init
            if (!GAME_DLL) {
                TestGameAPI()
            } else {

                // initialize idLib
                idLib.Init()

                // register static cvars declared in the game
                idCVar.RegisterStaticVars()

                // initialize processor specific SIMD
                idSIMD.InitProcessor("game", com_forceGenericSIMD.GetBool())
            }
            // Detect D3XP before any system that depends on it
            isD3XP = FileSystem_h.fileSystem.RunningD3XP()

            // Correct dependant classes on build time variables based on isD3XP flag
            program = idProgram()

            Printf("--------- Initializing Game ----------\n")
            Printf("gamename: %s\n", GAME_VERSION)
            Printf("gamedate: %s\n", SysCvar.__DATE__)

            // register game specific decl types
            declManager.RegisterDeclType(
                "model", declType_t.DECL_MODELDEF
            ) { idDeclModelDef() }
            declManager.RegisterDeclType("export", declType_t.DECL_MODELEXPORT) { idDecl() }

            // register game specific decl folders
            declManager.RegisterDeclFolder("def", ".def", declType_t.DECL_ENTITYDEF)
            declManager.RegisterDeclFolder("fx", ".fx", declType_t.DECL_FX)
            declManager.RegisterDeclFolder("particles", ".prt", declType_t.DECL_PARTICLE)
            declManager.RegisterDeclFolder("af", ".af", declType_t.DECL_AF)
            declManager.RegisterDeclFolder("newpdas", ".pda", declType_t.DECL_PDA)
            CmdSystem.cmdSystem.AddCommand(
                "listModelDefs",
                idListDecls_f(declType_t.DECL_MODELDEF),
                CmdSystem.CMD_FL_SYSTEM or CmdSystem.CMD_FL_GAME,
                "lists model defs"
            )
            CmdSystem.cmdSystem.AddCommand(
                "printModelDefs",
                idPrintDecls_f(declType_t.DECL_MODELDEF),
                CmdSystem.CMD_FL_SYSTEM or CmdSystem.CMD_FL_GAME,
                "prints a model def",
                ArgCompletion_Decl(declType_t.DECL_MODELDEF)
            )
            Clear()
            idEvent.Init()
            registerAllTypes()
            idClass.INIT()
            InitConsoleCommands()

            // D3XP: Re-execute default.cfg once to bind D3XP-specific keys (grabber, etc.)
            if (isD3XP && !SysCvar.g_xp_bind_run_once.GetBool()) {
                CmdSystem.cmdSystem.BufferCommandText(cmdExecution_t.CMD_EXEC_APPEND, "exec default.cfg\n")
                CmdSystem.cmdSystem.BufferCommandText(cmdExecution_t.CMD_EXEC_APPEND, "seta g_xp_bind_run_once 1\n")
                CmdSystem.cmdSystem.ExecuteCommandBuffer()
            }

            // load default scripts
            program.Startup(Game.SCRIPT_DEFAULT)

            // D3XP: load game-specific main script file
            if (isD3XP) {
                var lastGamedir: String? = null
                for (i in 0..1) {
                    val gamedir = if (i == 0) {
                        CVarSystem.cvarSystem.GetCVarString("fs_game_base")
                    } else {
                        CVarSystem.cvarSystem.GetCVarString("fs_game")
                    }
                    if (!gamedir.isNullOrEmpty() && gamedir != lastGamedir) {
                        val scriptFile = "script/${gamedir}_main.script"
                        if (FileSystem_h.fileSystem.ReadFile(scriptFile, null, null) > 0) {
                            program.CompileFile(scriptFile)
                            program.FinishCompilation()
                        }
                        lastGamedir = gamedir
                    }
                }
            }

            smokeParticles = idSmokeParticles()

            // set up the aas
            dict = FindEntityDefDict("aas_types")
            if (null == dict) {
                Error("Unable to find entityDef for 'aas_types'")
                return
            }

            // allocate space for the aas
            var kv = dict.MatchPrefix("type")
            while (kv != null) {
                aas = idAAS.Alloc()
                aasList.Append(aas)
                aasNames.add(kv.GetValue())
                kv = dict.MatchPrefix("type", kv)
            }
            gamestate = gameState_t.GAMESTATE_NOMAP
            Printf("...%d aas types\n", aasList.Num())
            Printf("game initialized.\n")
            Printf("--------------------------------------\n")
        }

        /*
         ===========
         idGameLocal::Shutdown

         shut down the entire game
         ============
         */
        override fun Shutdown() {
            if (Common.common == null) {
                return
            }
            Printf("------------ Game Shutdown -----------\n")
            mpGame.Shutdown()
            MapShutdown()
            aasList.DeleteContents(true)
            aasNames.clear()
            idAI.FreeObstacleAvoidanceNodes()

            // shutdown the model exporter
            //idModelExport.Shutdown();
            idEvent.Shutdown()

//	delete[] locationEntities;
            locationEntities = null

//	delete smokeParticles;
            smokeParticles = null
            idClass.Shutdown()

            // clear list with forces
            idForce.ClearForceList()

            // free the program data
            program.FreeData()

            // delete the .map file
            mapFile = null

            // free the collision map
            collisionModelManager.FreeMap()
            ShutdownConsoleCommands()

            // free memory allocated by class objects
            Clear()

            // shut down the animation manager
            animationLib.Shutdown()
            Printf("--------------------------------------\n")
            if (GAME_DLL) {

                // remove auto-completion function pointers pointing into this DLL
                CVarSystem.cvarSystem.RemoveFlaggedAutoCompletion(CVarSystem.CVAR_GAME)

                // shutdown idLib
                idLib.ShutDown()
            }
        }

        override fun SetLocalClient(clientNum: Int) {
            localClientNum = clientNum
        }

        override fun ThrottleUserInfo() {
            mpGame.ThrottleUserInfo()
        }

        override fun SetUserInfo(clientNum: Int, userInfo: idDict, isClient: Boolean, canModify: Boolean): idDict? {
            var i: Int
            var modifiedInfo = false
            this.isClient = isClient
            if (clientNum >= 0 && clientNum < MAX_CLIENTS) {
                this.userInfo[clientNum].set(userInfo)

                // server sanity
                if (canModify) {

                    // don't let numeric nicknames, it can be exploited to go around kick and ban commands from the server
                    if (idStr.IsNumeric(this.userInfo[clientNum].GetString("ui_name"))) {
                        this.userInfo[clientNum].Set(
                            "ui_name",
                            Str.va("%s_", this.userInfo[clientNum].GetString("ui_name"))
                        )
                        modifiedInfo = true
                    }

                    // don't allow dupe nicknames
                    i = 0
                    while (i < numClients) {
                        if (i == clientNum) {
                            i++
                            continue
                        }
                        if (entities.getOrNull(i) != null && entities[i] is idPlayer) {
                            if (0 == idStr.Icmp(
                                    this.userInfo[clientNum].GetString("ui_name"), this.userInfo[i].GetString("ui_name")
                                )
                            ) {
                                this.userInfo[clientNum].Set(
                                    "ui_name",
                                    Str.va("%s_", this.userInfo[clientNum].GetString("ui_name"))
                                )
                                modifiedInfo = true
                                i = -1 // rescan
                                i++
                                continue
                            }
                        }
                        i++
                    }
                }
                if (entities.getOrNull(clientNum) != null && entities[clientNum] is idPlayer) {
                    modifiedInfo = modifiedInfo or (entities[clientNum] as idPlayer).UserInfoChanged(canModify)
                }
                if (!isClient) {
                    // now mark this client in game
                    mpGame.EnterGame(clientNum)
                }
            }
            if (modifiedInfo) {
                assert(canModify)
                newInfo = this.userInfo[clientNum]
                return newInfo
            }
            return null
        }

        override fun GetUserInfo(clientNum: Int): idDict? {
            return if (entities.getOrNull(clientNum) != null && entities[clientNum] is idPlayer) {
                userInfo[clientNum]
            } else null
        }

        override fun SetServerInfo(_serverInfo: idDict) {
            val outMsg = idBitMsg()
            val msgBuf = ByteBuffer.allocate(MAX_GAME_MESSAGE_SIZE)
            serverInfo.set(_serverInfo)
            UpdateServerInfoFlags()
            if (!isClient) {
                // Let our clients know the server info changed
                outMsg.Init(msgBuf, MAX_GAME_MESSAGE_SIZE)
                outMsg.WriteByte(GAME_RELIABLE_MESSAGE_SERVERINFO.toByte())
                outMsg.WriteDeltaDict(gameLocal.serverInfo, null)
                NetworkSystem.networkSystem.ServerSendReliableMessage(-1, outMsg)
            }
        }

        override fun GetPersistentPlayerInfo(clientNum: Int): idDict {
            val ent: idEntity?
            persistentPlayerInfo[clientNum].Clear()
            ent = entities.getOrNull(clientNum)
            if (ent != null && ent is idPlayer) {
                ent.SavePersistantInfo()
            }
            return persistentPlayerInfo[clientNum]
        }

        override fun SetPersistentPlayerInfo(clientNum: Int, playerInfo: idDict) {
            persistentPlayerInfo[clientNum].set(playerInfo)
        }

        override fun InitFromNewMap(
            mapName: String,
            renderWorld: idRenderWorld,
            soundWorld: idSoundWorld,
            isServer: Boolean,
            isClient: Boolean,
            randSeed: Int
        ) {
            this.isServer = isServer
            this.isClient = isClient
            isMultiplayer = isServer || isClient
            if (mapFileName.Length() != 0) {
                MapShutdown()
            }
            Printf("----------- Game Map Init ------------\n")
            gamestate = gameState_t.GAMESTATE_STARTUP
            gameRenderWorld = renderWorld
            gameSoundWorld = soundWorld
            LoadMap(mapName, randSeed)
            InitScriptForMap()
            MapPopulate()
            mpGame.Reset()
            mpGame.Precache()

            // free up any unused animations
            animationLib.FlushUnusedAnims()
            gamestate = gameState_t.GAMESTATE_ACTIVE
            Printf("--------------------------------------\n")
        }

        override fun InitFromSaveGame(
            mapName: String, renderWorld: idRenderWorld, soundWorld: idSoundWorld, saveGameFile: idFile
        ): Boolean {
            var i: Int
            var num: Int
            var ent: idEntity? = null
            val si = idDict()
            if (mapFileName.Length() != 0) {
                MapShutdown()
            }
            Printf("------- Game Map Init SaveGame -------\n")
            gamestate = gameState_t.GAMESTATE_STARTUP
            gameRenderWorld = renderWorld
            gameSoundWorld = soundWorld
            val savegame = idRestoreGame(saveGameFile)
            savegame.ReadBuildNumber()

            // DG: I enhanced the information in savegames a bit for dhewm3 1.5.1
            //     for which I bumped th BUILD_NUMBER to 1305
            if (savegame.GetBuildNumber() >= 1305) {
                savegame.ReadInternalSavegameVersion()
                if (savegame.GetInternalSavegameVersion() > INTERNAL_SAVEGAME_VERSION) {
                    Warning(
                        "Savegame from newer dhewm3 version, don't know how to load! (its version is %d, only up to %d supported)",
                        savegame.GetInternalSavegameVersion(),
                        INTERNAL_SAVEGAME_VERSION
                    )
                    return false
                }
                val osType = idStr()
                val cpuArch = idStr()
                val engineVersion = idStr()
                savegame.ReadString(osType) // operating system the savegame was crated on (written from D3_OSTYPE)
                savegame.ReadString(cpuArch) // written from D3_ARCH (which is set in CMake), like "x86" or "x86_64"
                savegame.ReadString(engineVersion) // written from ENGINE_VERSION
                val ptrSize =
                    savegame.ReadShort() // sizeof(void*) of system that created the savegame, 4 on 32bit systems, 8 on 64bit systems
                val byteorder = savegame.ReadShort() // SDL_LIL_ENDIAN or SDL_BIG_ENDIAN
                Printf(
                    "Savegame was created by %s on %s %s. BuildNumber was %d, savegameversion %d\n",
                    engineVersion.toString(),
                    osType.toString(),
                    cpuArch.toString(),
                    savegame.GetBuildNumber(),
                    savegame.GetInternalSavegameVersion()
                )

                // right now I have no further use for this information, but in the future
                // it can be used for quirks for (then-) old savegames
            }
            // DG end

            // Create the list of all objects in the game
            savegame.CreateObjects()

            // Load the idProgram, also checking to make sure scripting hasn't changed since the savegame
            if (program.Restore(savegame) == false) {

                // Abort the load process, and let the session know so that it can restart the level
                // with the player persistent data.
                savegame.DeleteObjects()
                program.Restart()
                return false
            }

            // load the map needed for this savegame
            LoadMap(mapName, 0)
            i = savegame.ReadInt()
            SysCvar.g_skill.SetInteger(i)

            // precache the player
            FindEntityDef("player_doommarine", false)

            // precache any media specified in the map
            i = 0
            while (i < mapFile!!.GetNumEntities()) {
                val mapEnt = mapFile!!.GetEntity(i)
                if (!InhibitEntitySpawn(mapEnt.epairs)) {
                    CacheDictionaryMedia(mapEnt.epairs)
                    val classname = mapEnt.epairs.GetString("classname")
                    if (!classname.isEmpty()) {
                        FindEntityDef(classname, false)
                    }
                }
                i++
            }
            si.set(savegame.ReadDict()!!)
            SetServerInfo(si)
            numClients = savegame.ReadInt()
            i = 0
            while (i < numClients) {
                userInfo[i].set(savegame.ReadDict()!!)
                savegame.ReadUsercmd(usercmds[i])
                persistentPlayerInfo[i].set(savegame.ReadDict()!!)
                i++
            }
            i = 0
            while (i < MAX_GENTITIES) {
                entities[i] = savegame.ReadObject() as idEntity?
                spawnIds[i] = savegame.ReadInt()

                // restore the entityNumber
                if (entities[i] != null) {
                    entities[i]?.entityNumber = i
                }
                i++
            }
            firstFreeIndex = savegame.ReadInt()
            num_entities = savegame.ReadInt()

            // enityHash is restored by idEntity.Restore setting the entity name.
            world = savegame.ReadObject() as idWorldspawn
            num = savegame.ReadInt()
            i = 0
            while (i < num) {
                ent = savegame.ReadObject() as idEntity?
                assert(ent != null)
                if (ent != null) {
                    ent.spawnNode.AddToEnd(spawnedEntities)
                }
                i++
            }
            num = savegame.ReadInt()
            i = 0
            while (i < num) {
                ent = savegame.ReadObject() as idEntity?
                assert(ent != null)
                if (ent != null) {
                    ent.activeNode.AddToEnd(activeEntities)
                }
                i++
            }
            numEntitiesToDeactivate = savegame.ReadInt()
            sortPushers = savegame.ReadBool()
            sortTeamMasters = savegame.ReadBool()
            persistentLevelInfo.set(savegame.ReadDict()!!)
            i = 0
            while (i < RenderWorld.MAX_GLOBAL_SHADER_PARMS) {
                globalShaderParms[i] = savegame.ReadFloat()
                i++
            }
            i = savegame.ReadInt()
            random.SetSeed(i)
            frameCommandThread = savegame.ReadObject() as idThread?

            // clip
            // push
            // pvs
            // testmodel = "<NULL>"
            // testFx = "<NULL>"
            savegame.ReadString(sessionCommand)

            // FIXME: save smoke particles
            cinematicSkipTime = savegame.ReadInt()
            cinematicStopTime = savegame.ReadInt()
            cinematicMaxSkipTime = savegame.ReadInt()
            inCinematic = savegame.ReadBool()
            skipCinematic = savegame.ReadBool()
            isMultiplayer = savegame.ReadBool()
            gameType = gameType_t.entries.toTypedArray()[savegame.ReadInt()]
            framenum = savegame.ReadInt()
            previousTime = savegame.ReadInt()
            time = savegame.ReadInt()
            if (isD3XP) {
                msec = savegame.ReadInt()
            }
            vacuumAreaNum = savegame.ReadInt()
            entityDefBits = savegame.ReadInt()
            isServer = savegame.ReadBool()
            isClient = savegame.ReadBool()
            localClientNum = savegame.ReadInt()

            // snapshotEntities is used for multiplayer only
            realClientTime = savegame.ReadInt()
            isNewFrame = savegame.ReadBool()
            clientSmoothing = savegame.ReadFloat()

            // D3XP: restore portal sky and slow-mo timeline state
            if (isD3XP) {
                portalSkyEnt.Restore(savegame)
                portalSkyActive = savegame.ReadBool()
                fast.Restore(savegame)
                slow.Restore(savegame)
                slowmoState = slowmoState_t.entries.toTypedArray()[savegame.ReadInt()]
                slowmoMsec = savegame.ReadFloat()
                quickSlowmoReset = savegame.ReadBool()
                if (slowmoState == slowmoState_t.SLOWMO_STATE_OFF) {
                    gameSoundWorld?.SetSlowmo(false)
                    msecPrecise = USERCMD_MSEC_PRECISE
                } else {
                    gameSoundWorld?.SetSlowmo(true)
                    msecPrecise = msec.toFloat()
                }
                gameSoundWorld?.SetSlowmoSpeed(slowmoMsec / USERCMD_MSEC_PRECISE)
            }

            mapCycleLoaded = savegame.ReadBool()
            spawnCount = savegame.ReadInt()
            num = savegame.ReadInt()
            if (num != 0) {
                if (num != gameRenderWorld!!.NumAreas()) {
                    savegame.Error("idGameLocal.InitFromSaveGame: number of areas in map differs from save game.")
                }
                locationEntities = arrayOfNulls(num)

                i = 0
                while (i < num) {
                    locationEntities!![i] = savegame.ReadObject() as idLocationEntity?
                    i++
                }
            }
            camera = savegame.ReadObject() as idCamera?
            globalMaterial = savegame.ReadMaterial()
            lastAIAlertEntity.Restore(savegame)
            lastAIAlertTime = savegame.ReadInt()
            spawnArgs.set(savegame.ReadDict()!!)
            playerPVS.i = savegame.ReadInt()
            playerPVS.h = savegame.ReadInt()
            playerConnectedAreas.i = savegame.ReadInt()
            playerConnectedAreas.h = savegame.ReadInt()
            savegame.ReadVec3(gravity)

            // gamestate is restored after restoring everything else
            influenceActive = savegame.ReadBool()
            nextGibTime = savegame.ReadInt()

            // spawnSpots
            // initialSpots
            // currentInitialSpot
            // newInfo
            // makingBuild
            // shakeSounds
            // Read out pending events
            idEvent.Restore(savegame)
            savegame.RestoreObjects()

            mpGame.Reset()
            mpGame.Precache()

            // free up any unused animations
            animationLib.FlushUnusedAnims()
            gamestate = gameState_t.GAMESTATE_ACTIVE
            Printf("--------------------------------------\n")
            restoreFrame = framenum
            return true
        }

        /*
         ===========
         idGameLocal::SaveGame

         save the current player state, level name, and level state
         the session may have written some data to the file already
         ============
         */
        override fun SaveGame(saveGameFile: idFile) {

            var i: Int
            var ent: idEntity?
            var link: idEntity?

            val savegame = idSaveGame(saveGameFile)
            if (SysCvar.g_flushSave.GetBool() == true) {
                // force flushing with each write... for tracking down
                // save game bugs.
                saveGameFile.ForceFlush()
            }

            savegame.WriteBuildNumber(BUILD_NUMBER)

            // DG: add some more information to savegame to make future quirks easier
            savegame.WriteInt(INTERNAL_SAVEGAME_VERSION) // to be independent of BUILD_NUMBER
            savegame.WriteString(D3_OSTYPE) // operating system - from CMake
            savegame.WriteString(D3_ARCH) // CPU architecture (e.g. "x86" or "x86_64") - from CMake
            savegame.WriteString(ENGINE_VERSION)
            savegame.WriteShort(D3_SHORT_SIZE) // tells us if it's from a 32bit (4) or 64bit system (8)
            savegame.WriteShort(1234) // byteOrder
            // DG end
            // go through all entities and threads and add them to the object list
            i = 0
            while (i < MAX_GENTITIES) {
                ent = entities[i]
                if (ent != null) {
                    if (ent.GetTeamMaster() != null && ent.GetTeamMaster() != ent) {
                        i++
                        continue
                    }
                    link = ent
                    while (link != null) {
                        savegame.AddObject(link)
                        link = link.GetNextTeamEntity()
                    }
                }
                i++
            }

            val threads: idList<idThread> = idThread.GetThreads()
            for (thread in threads.getList(Array<idThread>::class.java)!!) {
                savegame.AddObject(thread)
            }

            // write out complete object list
            savegame.WriteObjectList()
            program.Save(savegame)
            savegame.WriteInt(SysCvar.g_skill.GetInteger())
            savegame.WriteDict(serverInfo)
            savegame.WriteInt(numClients)
            i = 0
            while (i < numClients) {
                savegame.WriteDict(userInfo[i])
                savegame.WriteUsercmd(usercmds[i])
                savegame.WriteDict(persistentPlayerInfo[i])
                i++
            }

            i = 0
            while (i < MAX_GENTITIES) {
                savegame.WriteObject(entities[i])
                savegame.WriteInt(spawnIds[i])
                i++
            }

            savegame.WriteInt(firstFreeIndex)
            savegame.WriteInt(num_entities)

            // enityHash is restored by idEntity::Restore setting the entity name.
            savegame.WriteObject(world)

            savegame.WriteInt(spawnedEntities.Num())
            ent = spawnedEntities.Next()
            while (ent != null) {
                savegame.WriteObject(ent)
                ent = ent.spawnNode.Next()
            }

            savegame.WriteInt(activeEntities.Num())
            ent = activeEntities.Next()
            while (ent != null) {
                savegame.WriteObject(ent)
                ent = ent.activeNode.Next()
            }

            savegame.WriteInt(numEntitiesToDeactivate)
            savegame.WriteBool(sortPushers)
            savegame.WriteBool(sortTeamMasters)
            savegame.WriteDict(persistentLevelInfo)

            i = 0
            while (i < RenderWorld.MAX_GLOBAL_SHADER_PARMS) {
                savegame.WriteFloat(globalShaderParms[i])
                i++
            }

            savegame.WriteInt(random.GetSeed())
            savegame.WriteObject(frameCommandThread)

            // clip
            // push
            // pvs
            testmodel = null
            testFx = null

            savegame.WriteString(sessionCommand)

            // FIXME: save smoke particles
            savegame.WriteInt(cinematicSkipTime)
            savegame.WriteInt(cinematicStopTime)
            savegame.WriteInt(cinematicMaxSkipTime)
            savegame.WriteBool(inCinematic)
            savegame.WriteBool(skipCinematic)
            savegame.WriteBool(isMultiplayer)
            savegame.WriteInt(etoi(gameType))
            savegame.WriteInt(framenum)
            savegame.WriteInt(previousTime)
            savegame.WriteInt(time)
            if (isD3XP) savegame.WriteInt(msec)
            savegame.WriteInt(vacuumAreaNum)
            savegame.WriteInt(entityDefBits)
            savegame.WriteBool(isServer)
            savegame.WriteBool(isClient)
            savegame.WriteInt(localClientNum)

            // snapshotEntities is used for multiplayer only
            savegame.WriteInt(realClientTime)
            savegame.WriteBool(isNewFrame)
            savegame.WriteFloat(clientSmoothing)

            // D3XP: save portal sky and slow-mo timeline state
            if (isD3XP) {
                portalSkyEnt.Save(savegame)
                savegame.WriteBool(portalSkyActive)
                fast.Save(savegame)
                slow.Save(savegame)
                savegame.WriteInt(slowmoState.ordinal)
                savegame.WriteFloat(slowmoMsec)
                savegame.WriteBool(quickSlowmoReset)
            }


            savegame.WriteBool(mapCycleLoaded)
            savegame.WriteInt(spawnCount)
            if (locationEntities == null) {
                savegame.WriteInt(0)
            } else {
                savegame.WriteInt(gameRenderWorld!!.NumAreas())
                i = 0
                while (i < gameRenderWorld!!.NumAreas()) {
                    savegame.WriteObject(locationEntities!![i])
                    i++
                }
            }

            savegame.WriteObject(camera)
            savegame.WriteMaterial(globalMaterial)
            lastAIAlertEntity.Save(savegame)
            savegame.WriteInt(lastAIAlertTime)
            savegame.WriteDict(spawnArgs)
            savegame.WriteInt(playerPVS.i)
            savegame.WriteInt(playerPVS.h)
            savegame.WriteInt(playerConnectedAreas.i)
            savegame.WriteInt(playerConnectedAreas.h)
            savegame.WriteVec3(gravity)

            // gamestate
            savegame.WriteBool(influenceActive)
            savegame.WriteInt(nextGibTime)

            // spawnSpots
            // initialSpots
            // currentInitialSpot
            // newInfo
            // makingBuild
            // shakeSounds
            // write out pending events
            idEvent.Save(savegame)
            savegame.Close()
        }

        override fun MapShutdown() {
            Printf("--------- Game Map Shutdown ----------\n")
            gamestate = gameState_t.GAMESTATE_SHUTDOWN
            if (gameRenderWorld != null) {
                // clear any debug lines, text, and polygons
                gameRenderWorld!!.DebugClearLines(0)
                gameRenderWorld!!.DebugClearPolygons(0)
            }

            // clear out camera if we're in a cinematic
            if (inCinematic) {
                camera = null
                inCinematic = false
            }
            MapClear(true)

            // reset the script to the state it was before the map was started
            program.Restart()
            if (smokeParticles != null) {
                smokeParticles!!.Shutdown()
            }
            pvs.Shutdown()
            clip.Shutdown()
            idClipModel.ClearTraceModelCache()
            ShutdownAsyncNetwork()
            mapFileName.Clear()
            gamestate = gameState_t.GAMESTATE_NOMAP
            Printf("--------------------------------------\n")
        }

        /*
         ===================
         idGameLocal::CacheDictionaryMedia

         This is called after parsing an EntityDef and for each entity spawnArgs before
         merging the entitydef.  It could be done post-merge, but that would
         avoid the fast pre-cache check associated with each entityDef
         ===================
         */
        override fun CacheDictionaryMedia(dict: idDict?) {
            var kv: idKeyValue?
            if (dict == null) {
                if (CVarSystem.cvarSystem.GetCVarBool("com_makingBuild")) {
                    DumpOggSounds()
                }
                return
            }
            if (CVarSystem.cvarSystem.GetCVarBool("com_makingBuild")) {
                GetShakeSounds(dict)
            }
            kv = dict.MatchPrefix("model")
            while (kv != null) {
                if (kv.GetValue().Length() != 0) {
                    declManager.MediaPrint("Precaching model %s\n", kv.GetValue())
                    // precache model/animations
                    if (declManager.FindType(declType_t.DECL_MODELDEF, kv.GetValue(), false) == null) {
                        // precache the render model
                        ModelManager.renderModelManager.FindModel(kv.GetValue())
                        // precache .cm files only
                        collisionModelManager.LoadModel(kv.GetValue(), true)
                    }
                }
                kv = dict.MatchPrefix("model", kv)
            }
            kv = dict.FindKey("s_shader")
            if (kv != null && kv.GetValue().Length() != 0) {
                declManager.FindType(declType_t.DECL_SOUND, kv.GetValue())
            }
            kv = dict.MatchPrefix("snd", null)
            while (kv != null) {
                if (kv.GetValue().Length() != 0) {
                    declManager.FindType(declType_t.DECL_SOUND, kv.GetValue())
                }
                kv = dict.MatchPrefix("snd", kv)
            }
            kv = dict.MatchPrefix("gui", null)
            while (kv != null) {
                if (kv.GetValue().Length() != 0) {
                    if (0 == idStr.Icmp(
                            kv.GetKey(), "gui_noninteractive"
                        ) || 0 == idStr.Icmpn(
                            kv.GetKey(), "gui_parm", 8
                        ) || 0 == idStr.Icmp(kv.GetKey(), "gui_inventory")
                    ) {
                        // unfortunate flag names, they aren't actually a gui
                    } else {
                        declManager.MediaPrint("Precaching gui %s\n", kv.GetValue())
                        val gui = UserInterface.uiManager.Alloc()
                        if (gui != null) {
                            gui.InitFromFile(kv.GetValue().toString())
                            UserInterface.uiManager.DeAlloc(gui)
                        }
                    }
                }
                kv = dict.MatchPrefix("gui", kv)
            }
            kv = dict.FindKey("texture")
            if (kv != null && kv.GetValue().Length() != 0) {
                declManager.FindType(declType_t.DECL_MATERIAL, kv.GetValue())
            }
            kv = dict.MatchPrefix("mtr", null)
            while (kv != null) {
                if (kv.GetValue().Length() != 0) {
                    declManager.FindType(declType_t.DECL_MATERIAL, kv.GetValue())
                }
                kv = dict.MatchPrefix("mtr", kv)
            }

            // handles hud icons
            kv = dict.MatchPrefix("inv_icon", null)
            while (kv != null) {
                if (kv.GetValue().Length() != 0) {
                    declManager.FindType(declType_t.DECL_MATERIAL, kv.GetValue())
                }
                kv = dict.MatchPrefix("inv_icon", kv)
            }

            // handles teleport fx.. this is not ideal but the actual decision on which fx to use
            // is handled by script code based on the teleport number
            kv = dict.MatchPrefix("teleport", null)
            if (kv != null && kv.GetValue().Length() != 0) {
                val teleportType = TempDump.atoi(kv.GetValue())
                val p = if (teleportType != 0) Str.va("fx/teleporter%d.fx", teleportType) else "fx/teleporter.fx"
                declManager.FindType(declType_t.DECL_FX, p)
            }
            kv = dict.MatchPrefix("fx", null)
            while (kv != null) {
                if (kv.GetValue().Length() != 0) {
                    declManager.MediaPrint("Precaching fx %s\n", kv.GetValue())
                    declManager.FindType(declType_t.DECL_FX, kv.GetValue())
                }
                kv = dict.MatchPrefix("fx", kv)
            }
            kv = dict.MatchPrefix("smoke", null)
            while (kv != null) {
                if (kv.GetValue().Length() != 0) {
                    var prtName = kv.GetValue()
                    val dash = prtName.Find('-')
                    if (dash > 0) {
                        prtName = prtName.Left(dash)
                    }
                    declManager.FindType(declType_t.DECL_PARTICLE, prtName)
                }
                kv = dict.MatchPrefix("smoke", kv)
            }
            kv = dict.MatchPrefix("skin", null)
            while (kv != null) {
                if (kv.GetValue().Length() != 0) {
                    declManager.MediaPrint("Precaching skin %s\n", kv.GetValue())
                    declManager.FindType(declType_t.DECL_SKIN, kv.GetValue())
                }
                kv = dict.MatchPrefix("skin", kv)
            }
            kv = dict.MatchPrefix("def", null)
            while (kv != null) {
                if (kv.GetValue().Length() != 0) {
                    FindEntityDef(kv.GetValue().toString(), false)
                }
                kv = dict.MatchPrefix("def", kv)
            }
            kv = dict.MatchPrefix("pda_name", null)
            while (kv != null) {
                if (kv.GetValue().Length() != 0) {
                    declManager.FindType(declType_t.DECL_PDA, kv.GetValue(), false)
                }
                kv = dict.MatchPrefix("pda_name", kv)
            }
            kv = dict.MatchPrefix("video", null)
            while (kv != null) {
                if (kv.GetValue().Length() != 0) {
                    declManager.FindType(declType_t.DECL_VIDEO, kv.GetValue(), false)
                }
                kv = dict.MatchPrefix("video", kv)
            }
            kv = dict.MatchPrefix("audio", null)
            while (kv != null) {
                if (kv.GetValue().Length() != 0) {
                    declManager.FindType(declType_t.DECL_AUDIO, kv.GetValue(), false)
                }
                kv = dict.MatchPrefix("audio", kv)
            }
        }

        override fun SpawnPlayer(clientNum: Int) {
            val ent = arrayOfNulls<idEntity?>(1)
            val args = idDict()

            // they can connect
            Printf("SpawnPlayer: %d\n", clientNum)
            args.SetInt("spawn_entnum", clientNum)
            args.Set("name", Str.va("player%d", clientNum + 1))
            args.Set("classname", if (isMultiplayer) "player_doommarine_mp" else "player_doommarine")
            if (!SpawnEntityDef(args, ent) || null == entities[clientNum]) {
                Error("Failed to spawn player as '%s'", args.GetString("classname"))
            }

            // make sure it's a compatible class
            if (ent[0] !is idPlayer) {
                Error(
                    "'%s' spawned the player as a '%s'.  Player spawnclass must be a subclass of idPlayer.",
                    args.GetString("classname"),
                    ent[0]!!.GetClassname()
                )
            }
            if (clientNum >= numClients) {
                numClients = clientNum + 1
            }
            mpGame.SpawnPlayer(clientNum)
        }

        override fun RunFrame(clientCmds: Array<usercmd_t>): gameReturn_t {
            var ent: idEntity?
            var num: Int
            var ms: Float
            val timer_think = idTimer()
            val timer_events = idTimer()
            val timer_singlethink = idTimer()
            val ret = gameReturn_t()
            val player: idPlayer?
            var view: renderView_s?
            if (_DEBUG) {
                assert(!isMultiplayer || !isClient)
            }
            player = GetLocalPlayer()

            // D3XP: compute slow-mo state and restore slow timeline before the frame loop
            if (isD3XP) {
                ComputeSlowMsec()
                // slow.Get equivalent: restore slow-timeline state into global fields
                time = slow.time; previousTime = slow.previousTime
                msec = slow.msec; framenum = slow.framenum
                realClientTime = slow.realClientTime; msecPrecise = slow.msecPrecise
                msec = slowmoMsec.toInt()
                msecPrecise = slowmoMsec
            }

            if (!isMultiplayer && SysCvar.g_stopTime.GetBool()) {
                // clear any debug lines from a previous frame
                gameRenderWorld!!.DebugClearLines(time + 1)

                // set the user commands for this frame
                for (i in 0 until numClients) {
                    usercmds[i].set(clientCmds[i])
                }
                player?.Think()
            } else {
                do {
                    // update the game time
                    framenum++
                    previousTime = time
                    val msecFast = CalcMSec(framenum.toLong()) // 16 or 17 ms at 60Hz
                    if (!isD3XP || slowmoState == slowmoState_t.SLOWMO_STATE_OFF) {
                        msec = msecFast
                        if (isD3XP) msecPrecise = USERCMD_MSEC_PRECISE
                    }
                    time += msec
                    realClientTime = time
                    if (isD3XP) slow.Set(time, previousTime, msec, framenum, realClientTime, msecPrecise)
                    if (GAME_DLL) {
                        // allow changing SIMD usage on the fly
                        if (com_forceGenericSIMD.IsModified()) {
                            idSIMD.InitProcessor("game", com_forceGenericSIMD.GetBool())
                        }
                    }

                    // make sure the random number counter is used each frame so random events
                    // are influenced by the player's actions
                    random.RandomInt()
                    if (player != null) {
                        // update the renderview so that any gui videos play from the right frame
                        view = player.GetRenderView()
                        if (view != null) {
                            gameRenderWorld!!.SetRenderView(view)
                        }
                    }

                    // clear any debug lines from a previous frame
                    gameRenderWorld!!.DebugClearLines(time)

                    // clear any debug polygons from a previous frame
                    gameRenderWorld!!.DebugClearPolygons(time)

                    // set the user commands for this frame
//                    memcpy(usercmds, clientCmds, numClients * sizeof(usercmds[ 0]));
                    for (i in 0 until numClients) {
                        usercmds[i].set(clientCmds[i])
                    }

                    // free old smoke particles
                    smokeParticles!!.FreeSmokes()

                    // process events on the server
                    ServerProcessEntityNetworkEventQueue()

                    // update our gravity vector if needed.
                    UpdateGravity()

                    // create a merged pvs for all players
                    SetupPlayerPVS()

                    // sort the active entity list
                    SortActiveEntityList()
                    timer_think.Clear()
                    timer_think.Start()

                    // let entities think
                    if (SysCvar.g_timeentities.GetFloat() != 0.0f) {
                        num = 0
                        ent = activeEntities.Next()
                        while (ent != null) {
                            if (SysCvar.g_cinematic.GetBool() && inCinematic && !ent.cinematic) {
                                ent.GetPhysics().UpdateTime(time)
                                ent = ent.activeNode.Next()
                                continue
                            }
                            timer_singlethink.Clear()
                            timer_singlethink.Start()
                            ent.Think()
                            timer_singlethink.Stop()
                            ms = timer_singlethink.Milliseconds().toFloat()
                            if (ms >= SysCvar.g_timeentities.GetFloat()) {
                                Printf("%d: entity '%s': %.1f ms\n", time, ent.name, ms)
                            }
                            num++
                            ent = ent.activeNode.Next()
                        }
                    } else {
                        if (inCinematic) {
                            num = 0
                            ent = activeEntities.Next()
                            while (ent != null) {
                                if (SysCvar.g_cinematic.GetBool() && !ent.cinematic) {
                                    ent.GetPhysics().UpdateTime(time)
                                    ent = ent.activeNode.Next()
                                    continue
                                }
                                ent.Think()
                                num++
                                ent = ent.activeNode.Next()
                            }
                        } else {
                            num = 0
                            ent = activeEntities.Next()
                            while (ent != null) {
                                if (isD3XP && ent.timeGroup != TIME_GROUP1) {
                                    ent = ent.activeNode.Next()
                                    continue
                                }
                                ent.Think()
                                num++
                                ent = ent.activeNode.Next()
                            }
                        }
                    }

                    // D3XP: run TIME_GROUP2 entities on the fast timeline
                    if (isD3XP) RunTimeGroup2(msecFast)

                    // remove any entities that have stopped thinking
                    if (numEntitiesToDeactivate != 0) {
                        var next_ent: idEntity?
                        var c = 0
                        ent = activeEntities.Next()
                        while (ent != null) {
                            next_ent = ent.activeNode.Next()
                            if (0 == ent.thinkFlags) {
                                ent.activeNode.Remove()
                                c++
                            }
                            ent = next_ent
                        }
                        //assert( numEntitiesToDeactivate == c );
                        numEntitiesToDeactivate = 0
                    }
                    timer_think.Stop()
                    timer_events.Clear()
                    timer_events.Start()

                    // service any pending events
                    idEvent.ServiceEvents()

                    // D3XP: service fast events on the fast timeline
                    if (isD3XP) {
                        time = fast.time; previousTime = fast.previousTime
                        msec = fast.msec; framenum = fast.framenum
                        realClientTime = fast.realClientTime; msecPrecise = fast.msecPrecise
                        idEvent.ServiceFastEvents()
                        time = slow.time; previousTime = slow.previousTime
                        msec = slow.msec; framenum = slow.framenum
                        realClientTime = slow.realClientTime; msecPrecise = slow.msecPrecise
                    }
                    timer_events.Stop()

                    // free the player pvs
                    FreePlayerPVS()

                    // do multiplayer related stuff
                    if (isMultiplayer) {
                        mpGame.Run()
                    }

                    // display how long it took to calculate the current game frame
                    if (SysCvar.g_frametime.GetBool()) {
                        // FIX: Was %.1f (float) — C++ uses %u (unsigned int); Milliseconds() returns Long
                        Printf(
                            "game %d: all:%d th:%d ev:%d %d ents \n",
                            time,
                            timer_think.Milliseconds() + timer_events.Milliseconds(),
                            timer_think.Milliseconds(),
                            timer_events.Milliseconds(),
                            num
                        )
                    }

                    // build the return value
                    ret.consistencyHash = 0
                    ret.sessionCommand[0] = Char(0)
                    if (!isMultiplayer && player != null) {
                        ret.health = player.health
                        ret.heartRate = player.heartRate
                        ret.stamina = idMath.FtoiFast(player.stamina)
                        // combat is a 0-100 value based on lastHitTime and lastDmgTime
                        // each make up 50% of the time spread over 10 seconds
                        ret.combat = 0
                        if (player.lastDmgTime > 0 && time < player.lastDmgTime + 10000) {
                            ret.combat += (50.0f * (time - player.lastDmgTime) / 10000).toInt()
                        }
                        if (player.lastHitTime > 0 && time < player.lastHitTime + 10000) {
                            ret.combat += (50.0f * (time - player.lastHitTime) / 10000).toInt()
                        }
                    }

                    // see if a target_sessionCommand has forced a changelevel
                    if (sessionCommand.Length() != 0) {
//                        strncpy(ret.sessionCommand, sessionCommand, sizeof(ret.sessionCommand));
                        ret.sessionCommand = sessionCommand.c_str()
                        break
                    }

                    // make sure we don't loop forever when skipping a cinematic
                    if (skipCinematic && time > cinematicMaxSkipTime) {
                        Warning("Exceeded maximum cinematic skip length.  Cinematic may be looping infinitely.")
                        skipCinematic = false
                        break
                    }
                } while ((inCinematic || time < cinematicStopTime) && skipCinematic)
            }
            ret.syncNextGameFrame = skipCinematic
            if (skipCinematic) {
                snd_system.soundSystem.SetMute(false)
                skipCinematic = false
            }

            // show any debug info for this frame
            RunDebugInfo()
            SysCmds.D_DrawDebugLines()
            return ret
        }

        /*
         ================
         idGameLocal::Draw

         makes rendering and sound system calls
         ================
         */
        override fun Draw(clientNum: Int): Boolean {
            if (isMultiplayer) {
                return mpGame.Draw(clientNum)
            }
            val player = entities[clientNum] as idPlayer? ?: return false

            // render the scene
            player.playerView.RenderPlayerView(player.hud!!)
            return true
        }

        override fun HandleESC(): escReply_t {
            if (isMultiplayer) {
                escGui = StartMenu()
                // we may set the gui back to NULL to hide it
                return escReply_t.ESC_GUI
            }
            val player = GetLocalPlayer()
            return if (player != null) {
                if (player.HandleESC()) {
                    escReply_t.ESC_IGNORE
                } else {
                    escReply_t.ESC_MAIN
                }
            } else escReply_t.ESC_MAIN
        }

        override fun StartMenu(): idUserInterface? {
            return if (!isMultiplayer) {
                null
            } else mpGame.StartMenu()
        }

        override fun HandleGuiCommands(menuCommand: String): String? {
            return if (!isMultiplayer) {
                null
            } else mpGame.HandleGuiCommands(menuCommand)
        }

        override fun HandleMainMenuCommands(menuCommand: String?, gui: idUserInterface?) {}
        override fun ServerAllowClient(
            numClients: Int, IP: String, guid: String, password: String, reason: CharArray /*[MAX_STRING_CHARS]*/
        ): allowReply_t {
            reason[0] = '\u0000'
            if (serverInfo.GetInt("si_pure") != 0 && !mpGame.IsPureReady()) {
                idStr.snPrintf(reason, MAX_STRING_CHARS, "#str_07139")
                return allowReply_t.ALLOW_NOTYET
            }
            if (0 == serverInfo.GetInt("si_maxPlayers")) {
                idStr.snPrintf(reason, MAX_STRING_CHARS, "#str_07140")
                return allowReply_t.ALLOW_NOTYET
            }
            if (numClients >= serverInfo.GetInt("si_maxPlayers")) {
                idStr.snPrintf(reason, MAX_STRING_CHARS, "#str_07141")
                return allowReply_t.ALLOW_NOTYET
            }
            if (!CVarSystem.cvarSystem.GetCVarBool("si_usepass")) {
                return allowReply_t.ALLOW_YES
            }
            val pass = CVarSystem.cvarSystem.GetCVarString("g_password")
            //	if ( pass[ 0 ] == '\0' ) {
            if (pass.isEmpty()) {
                Common.common.Warning("si_usepass is set but g_password is empty")
                CmdSystem.cmdSystem.BufferCommandText(
                    cmdExecution_t.CMD_EXEC_NOW, "say si_usepass is set but g_password is empty"
                )
                // avoids silent misconfigured state
                idStr.snPrintf(reason, MAX_STRING_CHARS, "#str_07142")
                return allowReply_t.ALLOW_NOTYET
            }
            if (0 == idStr.Cmp(pass, password)) {
                return allowReply_t.ALLOW_YES
            }
            idStr.snPrintf(reason, MAX_STRING_CHARS, "#str_07143")
            Printf("Rejecting client %s from IP %s: invalid password\n", guid, IP)
            return allowReply_t.ALLOW_BADPASS
        }

        override fun ServerClientConnect(clientNum: Int, guid: String?) {
            // make sure no parasite entity is left
            if (entities[clientNum] != null) {
                Common.common.DPrintf("ServerClientConnect: remove old player entity\n")
                //		delete entities[ clientNum ];
                entities[clientNum] = null
            }
            userInfo[clientNum].Clear()
            mpGame.ServerClientConnect(clientNum)
            Printf("client %d connected.\n", clientNum)
        }

        override fun ServerClientBegin(clientNum: Int) {
            val outMsg = idBitMsg()
            val msgBuf = ByteBuffer.allocate(MAX_GAME_MESSAGE_SIZE)

            // initialize the decl remap
            InitClientDeclRemap(clientNum)

            // send message to initialize decl remap at the client (this is always the very first reliable game message)
            outMsg.Init(msgBuf, MAX_GAME_MESSAGE_SIZE)
            outMsg.BeginWriting()
            outMsg.WriteByte(GAME_RELIABLE_MESSAGE_INIT_DECL_REMAP.toByte())
            NetworkSystem.networkSystem.ServerSendReliableMessage(clientNum, outMsg)

            // spawn the player
            SpawnPlayer(clientNum)
            if (clientNum == localClientNum) {
                mpGame.EnterGame(clientNum)
            }

            // send message to spawn the player at the clients
            outMsg.Init(msgBuf, MAX_GAME_MESSAGE_SIZE)
            outMsg.BeginWriting()
            outMsg.WriteByte(GAME_RELIABLE_MESSAGE_SPAWN_PLAYER.toByte())
            outMsg.WriteByte(clientNum.toByte())
            outMsg.WriteLong(spawnIds[clientNum])
            NetworkSystem.networkSystem.ServerSendReliableMessage(-1, outMsg)
        }

        override fun ServerClientDisconnect(clientNum: Int) {
            var i: Int
            val outMsg = idBitMsg()
            val msgBuf = ByteBuffer.allocate(MAX_GAME_MESSAGE_SIZE)
            outMsg.Init(msgBuf, MAX_GAME_MESSAGE_SIZE)
            outMsg.BeginWriting()
            outMsg.WriteByte(GAME_RELIABLE_MESSAGE_DELETE_ENT.toByte())
            outMsg.WriteBits(spawnIds[clientNum] shl GENTITYNUM_BITS or clientNum, 32) // see GetSpawnId
            NetworkSystem.networkSystem.ServerSendReliableMessage(-1, outMsg)

            // free snapshots stored for this client
            FreeSnapshotsOlderThanSequence(clientNum, 0x7FFFFFFF)

            // free entity states stored for this client
            i = 0
            while (i < MAX_GENTITIES) {
                if (clientEntityStates[clientNum][i] != null) {
//                    entityStateAllocator.Free(clientEntityStates[ clientNum][ i]);
                    clientEntityStates[clientNum][i] = null
                }
                i++
            }

            // clear the client PVS
//	memset( clientPVS[ clientNum ], 0, sizeof( clientPVS[ clientNum ] ) );
            clientPVS[clientNum].fill(0)

            // delete the player entity
//	delete entities[ clientNum ];
            entities[clientNum] = null
            mpGame.DisconnectClient(clientNum)
        }

        /*
         ================
         idGameLocal::ServerWriteInitialReliableMessages

         Send reliable messages to initialize the client game up to a certain initial state.
         ================
         */
        override fun ServerWriteInitialReliableMessages(clientNum: Int) {
            var i: Int
            val outMsg = idBitMsg()
            val msgBuf = ByteBuffer.allocate(MAX_GAME_MESSAGE_SIZE)
            var event: entityNetEvent_s?

            // spawn players
            i = 0
            while (i < MAX_CLIENTS) {
                if (entities[i] == null || i == clientNum) {
                    i++
                    continue
                }
                outMsg.Init(msgBuf, MAX_GAME_MESSAGE_SIZE)
                outMsg.BeginWriting()
                outMsg.WriteByte(GAME_RELIABLE_MESSAGE_SPAWN_PLAYER.toByte())
                outMsg.WriteByte(i.toByte())
                outMsg.WriteLong(spawnIds[i])
                NetworkSystem.networkSystem.ServerSendReliableMessage(clientNum, outMsg)
                i++
            }

            // send all saved events
            event = savedEventQueue.Start()
            while (event != null) {
                outMsg.Init(msgBuf, MAX_GAME_MESSAGE_SIZE)
                outMsg.BeginWriting()
                outMsg.WriteByte(GAME_RELIABLE_MESSAGE_EVENT.toByte())
                outMsg.WriteBits(event.spawnId, 32)
                outMsg.WriteByte(event.event.toByte())
                outMsg.WriteLong(event.time)
                outMsg.WriteBits(event.paramsSize, idMath.BitsForInteger(MAX_EVENT_PARAM_SIZE))
                if (event.paramsSize != 0) {
                    outMsg.WriteData(event.paramsBuf, event.paramsSize)
                }
                NetworkSystem.networkSystem.ServerSendReliableMessage(clientNum, outMsg)
                event = event.next
            }

            // update portals for opened doors
            val numPortals = gameRenderWorld!!.NumPortals()
            outMsg.Init(msgBuf, MAX_GAME_MESSAGE_SIZE)
            outMsg.BeginWriting()
            outMsg.WriteByte(GAME_RELIABLE_MESSAGE_PORTALSTATES.toByte())
            outMsg.WriteLong(numPortals)
            i = 0
            while (i < numPortals) {
                outMsg.WriteBits(
                    gameRenderWorld!!.GetPortalState( /*(qhandle_t)*/i + 1), NUM_RENDER_PORTAL_BITS
                )
                i++
            }
            NetworkSystem.networkSystem.ServerSendReliableMessage(clientNum, outMsg)
            mpGame.ServerWriteInitialReliableMessages(clientNum)
        }

        /*
         ================
         idGameLocal::ServerWriteSnapshot

         Write a snapshot of the current game state for the given client.
         ================
         */
        override fun ServerWriteSnapshot(
            clientNum: Int, sequence: Int, msg: idBitMsg, clientInPVS: ByteArray, numPVSClients: Int
        ) {
            var i: Int
            val msgSize = CInt()
            val msgWriteBit = CInt()
            val player: idPlayer?
            val spectated: idPlayer?
            var ent: idEntity?
            var pvsHandle: pvsHandle_t?
            val deltaMsg = idBitMsgDelta()
            val snapshot: snapshot_s
            var base: entityState_s?
            var newBase: entityState_s
            val numSourceAreas: Int
            val sourceAreas = IntArray(idEntity.MAX_PVS_AREAS)
            var tagRandom = idRandom()
            player = entities[clientNum] as idPlayer?
            if (null == player) {
                return
            }
            spectated = if (player.spectating && player.spectator != clientNum && entities[player.spectator] != null) {
                entities[player.spectator] as idPlayer
            } else {
                player
            }

            // free too old snapshots
            FreeSnapshotsOlderThanSequence(clientNum, sequence - 64)

            // allocate new snapshot
            snapshot = snapshot_s() //snapshotAllocator.Alloc();
            snapshot.sequence = sequence
            snapshot.firstEntityState = null
            snapshot.next = clientSnapshots[clientNum]
            clientSnapshots[clientNum] = snapshot
            //            memset(snapshot.pvs, 0, sizeof(snapshot.pvs));
            snapshot.pvs.fill(0)

            // get PVS for this player
            // don't use PVSAreas for networking - PVSAreas depends on animations (and md5 bounds), which are not synchronized
            numSourceAreas = gameRenderWorld!!.BoundsInAreas(
                spectated.GetPlayerPhysics().GetAbsBounds(), sourceAreas, idEntity.MAX_PVS_AREAS
            )
            pvsHandle = gameLocal.pvs.SetupCurrentPVS(sourceAreas, numSourceAreas, pvsType_t.PVS_NORMAL)

            // D3XP: Add portalSky areas to PVS
            if (isD3XP && portalSkyEnt.GetEntity() != null) {
                val skyEnt = portalSkyEnt.GetEntity()!!
                val otherPVS = gameLocal.pvs.SetupCurrentPVS(skyEnt.GetPVSAreas(), skyEnt.GetNumPVSAreas())
                val newPVS = gameLocal.pvs.MergeCurrentPVS(pvsHandle, otherPVS)
                pvs.FreeCurrentPVS(pvsHandle)
                pvs.FreeCurrentPVS(otherPVS)
                pvsHandle = newPVS
            }

            if (Game_network.ASYNC_WRITE_TAGS) {
                tagRandom = idRandom()
                tagRandom.SetSeed(random.RandomInt())
                msg.WriteLong(tagRandom.GetSeed())
            }

            // create the snapshot
            ent = spawnedEntities.Next()
            while (ent != null) {


                // if the entity is not in the player PVS
                if (!ent.PhysicsTeamInPVS(pvsHandle) && ent.entityNumber != clientNum) {
                    ent = ent.spawnNode.Next()
                    continue
                }

                // add the entity to the snapshot pvs
                snapshot.pvs[ent.entityNumber shr 5] =
                    snapshot.pvs[ent.entityNumber shr 5] or (1 shl (ent.entityNumber and 31))

                // if that entity is not marked for network synchronization
                if (!ent.fl.networkSync) {
                    ent = ent.spawnNode.Next()
                    continue
                }

                // save the write state to which we can revert when the entity didn't change at all
                msg.SaveWriteState(msgSize, msgWriteBit)

                // write the entity to the snapshot
                msg.WriteBits(ent.entityNumber, GENTITYNUM_BITS)
                base = clientEntityStates[clientNum][ent.entityNumber]
                base?.state?.BeginReading()
                newBase = entityState_s() //entityStateAllocator.Alloc();
                newBase.entityNumber = ent.entityNumber
                newBase.state.Init(newBase.stateBuf)
                newBase.state.BeginWriting()
                deltaMsg.Init(base?.state, newBase.state, msg)
                deltaMsg.WriteBits(spawnIds[ent.entityNumber], 32 - GENTITYNUM_BITS)
                deltaMsg.WriteBits(ent.GetType().typeNum, idClass.GetTypeNumBits())
                deltaMsg.WriteBits(ServerRemapDecl(-1, declType_t.DECL_ENTITYDEF, ent.entityDefNumber), entityDefBits)

                // write the class specific data to the snapshot
                ent.WriteToSnapshot(deltaMsg)
                if (!deltaMsg.HasChanged()) {
                    msg.RestoreWriteState(msgSize._val, msgWriteBit._val)
                    //                    entityStateAllocator.Free(newBase);
                } else {
                    newBase.next = snapshot.firstEntityState
                    snapshot.firstEntityState = newBase
                    if (Game_network.ASYNC_WRITE_TAGS) {
                        msg.WriteLong(tagRandom.RandomInt())
                    }
                }
                ent = ent.spawnNode.Next()
            }
            msg.WriteBits(ENTITYNUM_NONE, GENTITYNUM_BITS)

            // write the PVS to the snapshot
            if (ASYNC_WRITE_PVS) {
                i = 0
                while (i < idEntity.MAX_PVS_AREAS) {
                    if (i < numSourceAreas) {
                        msg.WriteLong(sourceAreas[i])
                    } else {
                        msg.WriteLong(0)
                    }
                    i++
                }
                gameLocal.pvs.WritePVS(pvsHandle, msg)
            }
            i = 0
            while (i < ENTITY_PVS_SIZE) {
                msg.WriteDeltaLong(clientPVS[clientNum][i], snapshot.pvs[i])
                i++
            }

            // free the PVS
            pvs.FreeCurrentPVS(pvsHandle)

            // write the game and player state to the snapshot
            base = clientEntityStates[clientNum][ENTITYNUM_NONE] // ENTITYNUM_NONE is used for the game and player state
            base?.state?.BeginReading()
            newBase = entityState_s() //entityStateAllocator.Alloc();
            newBase.entityNumber = ENTITYNUM_NONE
            newBase.next = snapshot.firstEntityState
            snapshot.firstEntityState = newBase
            newBase.state.Init(newBase.stateBuf)
            newBase.state.BeginWriting()
            deltaMsg.Init(base?.state, newBase.state, msg)
            if (player.spectating && player.spectator != player.entityNumber && gameLocal.entities[player.spectator] != null && gameLocal.entities[player.spectator] is idPlayer) {
                (gameLocal.entities[player.spectator] as idPlayer).WritePlayerStateToSnapshot(deltaMsg)
            } else {
                player.WritePlayerStateToSnapshot(deltaMsg)
            }
            WriteGameStateToSnapshot(deltaMsg)

            // copy the client PVS string
            // C++ does memcpy from int[] to byte[] — pack int bits into bytes
            val numPVSBytes = (numPVSClients + 7) shr 3
            for (byteIdx in 0 until numPVSBytes) {
                val intIdx = byteIdx / 4
                val shift = (byteIdx % 4) * 8
                clientInPVS[byteIdx] = ((snapshot.pvs[intIdx] shr shift) and 0xFF).toByte()
            }
            LittleRevBytes(clientInPVS, clientInPVS.size)
        }

        override fun ServerApplySnapshot(clientNum: Int, sequence: Int): Boolean {
            return ApplySnapshot(clientNum, sequence)
        }

        override fun ServerProcessReliableMessage(clientNum: Int, msg: idBitMsg) {
            val id: Int
            id = msg.ReadByte().toInt()
            when (id) {
                GAME_RELIABLE_MESSAGE_CHAT, GAME_RELIABLE_MESSAGE_TCHAT -> {
                    val name = CharArray(128)
                    val text = CharArray(128)
                    msg.ReadString(name, 128)
                    msg.ReadString(text, 128)
                    mpGame.ProcessChatMessage(
                        clientNum, id == GAME_RELIABLE_MESSAGE_TCHAT, TempDump.ctos(name), TempDump.ctos(text), null
                    )
                }

                GAME_RELIABLE_MESSAGE_VCHAT -> {
                    val index = msg.ReadLong()
                    val team = msg.ReadBits(1) != 0
                    mpGame.ProcessVoiceChat(clientNum, team, index)
                }

                GAME_RELIABLE_MESSAGE_KILL -> {
                    mpGame.WantKilled(clientNum)
                }

                GAME_RELIABLE_MESSAGE_DROPWEAPON -> {
                    mpGame.DropWeapon(clientNum)
                }

                GAME_RELIABLE_MESSAGE_CALLVOTE -> {
                    mpGame.ServerCallVote(clientNum, msg)
                }

                GAME_RELIABLE_MESSAGE_CASTVOTE -> {
                    val vote = msg.ReadByte().toInt() != 0
                    mpGame.CastVote(clientNum, vote)
                }

                GAME_RELIABLE_MESSAGE_EVENT -> {
                    val event: entityNetEvent_s?

                    // allocate new event
                    event = eventQueue.Alloc()
                    eventQueue.Enqueue(event, outOfOrderBehaviour_t.OUTOFORDER_DROP)
                    event.spawnId = msg.ReadBits(32)
                    event.event = msg.ReadByte().toInt()
                    event.time = msg.ReadLong()
                    event.paramsSize = msg.ReadBits(idMath.BitsForInteger(MAX_EVENT_PARAM_SIZE))
                    if (event.paramsSize != 0) {
                        if (event.paramsSize > MAX_EVENT_PARAM_SIZE) {
                            NetworkEventWarning(event, "invalid param size")
                            return
                        }
                        msg.ReadByteAlign()
                        msg.ReadData(event.paramsBuf, event.paramsSize)
                    }
                }

                else -> {
                    Warning("Unknown client.server reliable message: %d", id)
                }
            }
        }

        override fun ClientReadSnapshot(
            clientNum: Int,
            sequence: Int,
            gameFrame: Int,
            gameTime: Int,
            dupeUsercmds: Int,
            aheadOfServer: Int,
            msg: idBitMsg?
        ) {
            var i: Int
            var typeNum: Int
            var entityDefNumber: Int
            var numBitsRead: Int
            var typeInfo: idTypeInfo?
            var ent: idEntity?
            var player: idPlayer?
            var spectated: idPlayer?
            var pvsHandle: pvsHandle_t
            val args = idDict()
            var classname: String?
            val deltaMsg = idBitMsgDelta()
            var snapshot: snapshot_s
            var base: entityState_s?
            var newBase: entityState_s
            var spawnId: Int
            var numSourceAreas: Int
            val sourceAreas = IntArray(idEntity.MAX_PVS_AREAS)
            var weap: Weapon.idWeapon?

            if (msg == null) {
                return
            }

            if (Game_network.net_clientLagOMeter.GetBool() && RenderSystem.renderSystem != null) {
                UpdateLagometer(aheadOfServer, dupeUsercmds)
                // flatten 3D lagometer array to ByteBuffer for upload
                val lagBuf = ByteBuffer.allocate(LAGO_IMG_WIDTH * LAGO_IMG_HEIGHT * 4)
                for (row in 0 until LAGO_IMG_HEIGHT) {
                    for (col in 0 until LAGO_IMG_WIDTH) {
                        lagBuf.put(lagometer[row][col])
                    }
                }
                lagBuf.flip()
                if (!RenderSystem.renderSystem.UploadImage(LAGO_IMAGE, lagBuf, LAGO_IMG_WIDTH, LAGO_IMG_HEIGHT)) {
                    Common.common.Printf("lagometer: UploadImage failed. turning off net_clientLagOMeter\n")
                    Game_network.net_clientLagOMeter.SetBool(false)
                }
            }

            InitLocalClient(clientNum)

            // clear any debug lines from a previous frame
            gameRenderWorld!!.DebugClearLines(time)

            // clear any debug polygons from a previous frame
            gameRenderWorld!!.DebugClearPolygons(time)

            // update the game time
            framenum = gameFrame
            time = gameTime
            previousTime = time - msec

            // so that StartSound/StopSound doesn't risk skipping
            isNewFrame = true

            // clear the snapshot entity list
            snapshotEntities.Clear()

            // allocate new snapshot
            snapshot = snapshot_s()
            snapshot.sequence = sequence
            snapshot.firstEntityState = null
            snapshot.next = clientSnapshots[clientNum]
            clientSnapshots[clientNum] = snapshot

            if (Game_network.ASYNC_WRITE_TAGS) {
                val tagRandom = idRandom()
                tagRandom.SetSeed(msg.ReadLong())
            }

            // read all entities from the snapshot
            i = msg.ReadBits(GENTITYNUM_BITS)
            while (i != ENTITYNUM_NONE) {
                base = clientEntityStates[clientNum][i]
                if (base != null) {
                    base.state.BeginReading()
                }
                newBase = entityState_s()
                newBase.entityNumber = i
                newBase.next = snapshot.firstEntityState
                snapshot.firstEntityState = newBase
                newBase.state.Init(newBase.stateBuf)
                newBase.state.BeginWriting()

                numBitsRead = msg.GetNumBitsRead()

                deltaMsg.Init(base?.state, newBase.state, msg)

                spawnId = deltaMsg.ReadBits(32 - GENTITYNUM_BITS)
                typeNum = deltaMsg.ReadBits(idClass.GetTypeNumBits())
                entityDefNumber = ClientRemapDecl(declType_t.DECL_ENTITYDEF, deltaMsg.ReadBits(entityDefBits))

                typeInfo = idClass.GetType(typeNum)
                if (typeInfo == null) {
                    Error("Unknown type number %d for entity %d with class number %d", typeNum, i, entityDefNumber)
                }

                ent = entities[i]

                // if there is no entity or an entity of the wrong type
                if (ent == null || ent.GetType().typeNum != typeNum || ent.entityDefNumber != entityDefNumber || spawnId != spawnIds[i]) {

                    if (i < MAX_CLIENTS && ent != null) {
                        // SPAWN_PLAYER should be taking care of spawning the entity with the right spawnId
                        Common.common.Warning("ClientReadSnapshot: recycling client entity %d\n", i)
                    }

                    ent?._deconstructor()

                    spawnCount = spawnId

                    args.Clear()
                    args.SetInt("spawn_entnum", i)
                    args.Set("name", Str.va("entity%d", i))

                    if (entityDefNumber >= 0) {
                        if (entityDefNumber >= declManager.GetNumDecls(declType_t.DECL_ENTITYDEF)) {
                            Error(
                                "server has %d entityDefs instead of %d",
                                entityDefNumber,
                                declManager.GetNumDecls(declType_t.DECL_ENTITYDEF)
                            )
                        }
                        classname =
                            declManager.DeclByIndex(declType_t.DECL_ENTITYDEF, entityDefNumber, false)!!.GetName()
                        args.Set("classname", classname)
                        val entOut = arrayOfNulls<idEntity>(1)
                        if (!SpawnEntityDef(
                                args, entOut
                            ) || entities[i] == null || entities[i]!!.GetType().typeNum != typeNum
                        ) {
                            Error(
                                "Failed to spawn entity with classname '%s' of type '%s'",
                                classname,
                                typeInfo!!.classname
                            )
                        }
                        ent = entOut[0]
                    } else {
                        ent = SpawnEntityType(typeInfo!!, args, true)
                        if (entities[i] == null || entities[i]!!.GetType().typeNum != typeNum) {
                            Error("Failed to spawn entity of type '%s'", typeInfo.classname)
                        }
                    }
                    if (i < MAX_CLIENTS && i >= numClients) {
                        numClients = i + 1
                    }
                }

                // add the entity to the snapshot list
                ent!!.snapshotNode.AddToEnd(snapshotEntities)
                ent.snapshotSequence = sequence

                // read the class specific data from the snapshot
                ent.ReadFromSnapshot(deltaMsg)

                ent.snapshotBits = msg.GetNumBitsRead() - numBitsRead

                if (Game_network.ASYNC_WRITE_TAGS) {
                    val tagRandom = idRandom()
                    if (msg.ReadLong() != tagRandom.RandomInt()) {
                        CmdSystem.cmdSystem.BufferCommandText(cmdExecution_t.CMD_EXEC_NOW, "writeGameState")
                        if (entityDefNumber >= 0 && entityDefNumber < declManager.GetNumDecls(declType_t.DECL_ENTITYDEF)) {
                            classname =
                                declManager.DeclByIndex(declType_t.DECL_ENTITYDEF, entityDefNumber, false)!!.GetName()
                            Error(
                                "write to and read from snapshot out of sync for classname '%s' of type '%s'",
                                classname,
                                typeInfo!!.classname
                            )
                        } else {
                            Error("write to and read from snapshot out of sync for type '%s'", typeInfo!!.classname)
                        }
                    }
                }

                i = msg.ReadBits(GENTITYNUM_BITS)
            }

            player = entities[clientNum] as? idPlayer
            if (player == null) {
                return
            }

            // if prediction is off, enable local client smoothing
            player.SetSelfSmooth(dupeUsercmds > 2)

            if (player.spectating && player.spectator != clientNum && entities[player.spectator] != null) {
                spectated = entities[player.spectator] as? idPlayer ?: player
            } else {
                spectated = player
            }

            // get PVS for this player
            // don't use PVSAreas for networking - PVSAreas depends on animations (and md5 bounds), which are not synchronized
            numSourceAreas = gameRenderWorld!!.BoundsInAreas(
                spectated.GetPlayerPhysics().GetAbsBounds(), sourceAreas, idEntity.MAX_PVS_AREAS
            )
            pvsHandle = gameLocal.pvs.SetupCurrentPVS(sourceAreas, numSourceAreas, pvsType_t.PVS_NORMAL)

            // D3XP: Add portalSky areas to PVS
            if (isD3XP && portalSkyEnt.GetEntity() != null) {
                val skyEnt = portalSkyEnt.GetEntity()!!
                val otherPVS = gameLocal.pvs.SetupCurrentPVS(skyEnt.GetPVSAreas(), skyEnt.GetNumPVSAreas())
                val newPVS = gameLocal.pvs.MergeCurrentPVS(pvsHandle, otherPVS)
                pvs.FreeCurrentPVS(pvsHandle)
                pvs.FreeCurrentPVS(otherPVS)
                pvsHandle = newPVS
            }

            // read the PVS from the snapshot
            if (ASYNC_WRITE_PVS) {
                val serverPVS = IntArray(idEntity.MAX_PVS_AREAS)
                i = numSourceAreas
                while (i < idEntity.MAX_PVS_AREAS) {
                    sourceAreas[i] = 0
                    i++
                }
                for (k in 0 until idEntity.MAX_PVS_AREAS) {
                    serverPVS[k] = msg.ReadLong()
                }
                if (!sourceAreas.contentEquals(serverPVS)) {
                    Common.common.Warning("client PVS areas != server PVS areas, sequence 0x%x", sequence)
                    for (k in 0 until idEntity.MAX_PVS_AREAS) {
                        Common.common.DPrintf("%3d ", sourceAreas[k])
                    }
                    Common.common.DPrintf("\n")
                    for (k in 0 until idEntity.MAX_PVS_AREAS) {
                        Common.common.DPrintf("%3d ", serverPVS[k])
                    }
                    Common.common.DPrintf("\n")
                }
                gameLocal.pvs.ReadPVS(pvsHandle, msg)
            }
            i = 0
            while (i < ENTITY_PVS_SIZE) {
                snapshot.pvs[i] = msg.ReadDeltaLong(clientPVS[clientNum][i])
                i++
            }

            // add entities in the PVS that haven't changed since the last applied snapshot
            ent = spawnedEntities.Next()
            while (ent != null) {
                // if the entity is already in the snapshot
                if (ent.snapshotSequence == sequence) {
                    ent = ent.spawnNode.Next()
                    continue
                }

                // if the entity is not in the snapshot PVS
                if (snapshot.pvs[ent.entityNumber shr 5] and (1 shl (ent.entityNumber and 31)) == 0) {
                    if (ent.PhysicsTeamInPVS(pvsHandle)) {
                        if (ent.entityNumber >= MAX_CLIENTS && ent.entityNumber < mapSpawnCount && !ent.spawnArgs.GetBool(
                                "net_dynamic",
                                "0"
                            )
                        ) {
                            // server says it's not in PVS, client says it's in PVS
                            Common.common.DWarning(
                                "client thinks map entity 0x%x (%s) is stale, sequence 0x%x",
                                ent.entityNumber,
                                ent.name.toString(),
                                sequence
                            )
                        } else {
                            ent.FreeModelDef()
                            // D3XP CTF: free leftover light defs on flags going stale
                            if (isD3XP) {
                                ent.FreeLightDef()
                            }
                            ent.UpdateVisuals()
                            ent.GetPhysics().UnlinkClip()
                        }
                    }
                    ent = ent.spawnNode.Next()
                    continue
                }

                // add the entity to the snapshot list
                ent.snapshotNode.AddToEnd(snapshotEntities)
                ent.snapshotSequence = sequence
                ent.snapshotBits = 0

                base = clientEntityStates[clientNum][ent.entityNumber]
                if (base == null) {
                    // entity has probably fl.networkSync set to false
                    ent = ent.spawnNode.Next()
                    continue
                }

                base.state.BeginReading()

                deltaMsg.Init(base.state, null, null)

                spawnId = deltaMsg.ReadBits(32 - GENTITYNUM_BITS)
                typeNum = deltaMsg.ReadBits(idClass.GetTypeNumBits())
                entityDefNumber = deltaMsg.ReadBits(entityDefBits)

                typeInfo = idClass.GetType(typeNum)

                // if the entity is not the right type
                if (typeInfo == null || ent.GetType().typeNum != typeNum || ent.entityDefNumber != entityDefNumber) {
                    // should never happen - it does though. with != entityDefNumber only?
                    Common.common.DWarning(
                        "entity '%s' is not the right type 0x%d 0x%x 0x%x 0x%x",
                        ent.GetName(), ent.GetType().typeNum,
                        typeNum,
                        ent.entityDefNumber,
                        entityDefNumber
                    )
                    ent = ent.spawnNode.Next()
                    continue
                }

                // read the class specific data from the base state
                ent.ReadFromSnapshot(deltaMsg)

                ent = ent.spawnNode.Next()
            }

            // free the PVS
            pvs.FreeCurrentPVS(pvsHandle)

            // read the game and player state from the snapshot
            base = clientEntityStates[clientNum][ENTITYNUM_NONE] // ENTITYNUM_NONE is used for the game and player state
            if (base != null) {
                base.state.BeginReading()
            }
            newBase = entityState_s()
            newBase.entityNumber = ENTITYNUM_NONE
            newBase.next = snapshot.firstEntityState
            snapshot.firstEntityState = newBase
            newBase.state.Init(newBase.stateBuf)
            newBase.state.BeginWriting()
            deltaMsg.Init(base?.state, newBase.state, msg)
            if (player.spectating && player.spectator != player.entityNumber && gameLocal.entities[player.spectator] != null && gameLocal.entities[player.spectator] is idPlayer) {
                (gameLocal.entities[player.spectator] as idPlayer).ReadPlayerStateFromSnapshot(deltaMsg)
                weap = (gameLocal.entities[player.spectator] as idPlayer).weapon.GetEntity()
                if (weap != null && weap.GetRenderEntity()!!.bounds[0] == weap.GetRenderEntity()!!.bounds[1]) {
                    // update the weapon's viewmodel bounds so that the model doesn't flicker in the spectator's view
                    weap.GetAnimator().GetBounds(gameLocal.time, weap.GetRenderEntity()!!.bounds)
                    weap.UpdateVisuals()
                }
            } else {
                player.ReadPlayerStateFromSnapshot(deltaMsg)
            }
            ReadGameStateFromSnapshot(deltaMsg)

            // visualize the snapshot
            ClientShowSnapshot(clientNum)

            // process entity events
            ClientProcessEntityNetworkEventQueue()
        }

        // ---------------------- Public idGameLocal Interface -------------------//TODO:
        //public		void					Printf( const char *fmt, ... ) const id_attribute((format(printf,2,3)));
        //public		void					DPrintf( const char *fmt, ... ) const id_attribute((format(printf,2,3)));
        //public		void					Warning( const char *fmt, ... ) const id_attribute((format(printf,2,3)));
        //public		void					DWarning( const char *fmt, ... ) const id_attribute((format(printf,2,3)));
        //public		void					Error( const char *fmt, ... ) const id_attribute((format(printf,2,3)));
        override fun ClientApplySnapshot(clientNum: Int, sequence: Int): Boolean {
            return ApplySnapshot(clientNum, sequence)
        }

        override fun ClientProcessReliableMessage(clientNum: Int, msg: idBitMsg) {
            val id: Int
            val line: Int
            val p: idPlayer?
            InitLocalClient(clientNum)
            id = msg.ReadByte().toInt()
            when (id) {
                GAME_RELIABLE_MESSAGE_INIT_DECL_REMAP -> {
                    InitClientDeclRemap(clientNum)
                }

                GAME_RELIABLE_MESSAGE_REMAP_DECL -> {
                    val type: Int
                    val index: Int
                    val name = CharArray(MAX_STRING_CHARS)
                    type = msg.ReadByte().toInt()
                    index = msg.ReadLong()
                    msg.ReadString(name, MAX_STRING_CHARS)
                    val decl = declManager.FindType(declType_t.entries[type], TempDump.ctos(name), false)
                    if (decl != null) {
                        if (index >= clientDeclRemap[clientNum][type]!!.Num()) {
                            clientDeclRemap[clientNum][type]!!.AssureSize(index + 1, -1)
                        }
                        clientDeclRemap[clientNum][type]!![index] = decl.Index()
                    }
                }

                GAME_RELIABLE_MESSAGE_SPAWN_PLAYER -> {
                    val client = msg.ReadByte().toInt()
                    val spawnId = msg.ReadLong()
                    if (null == entities[client]) {
                        SpawnPlayer(client)
                        entities[client]!!.FreeModelDef()
                    }
                    // fix up the spawnId to match what the server says
                    // otherwise there is going to be a bogus delete/new of the client entity in the first ClientReadFromSnapshot
                    spawnIds[client] = spawnId
                }

                GAME_RELIABLE_MESSAGE_DELETE_ENT -> {
                    val spawnId = msg.ReadBits(32)
                    val entPtr = idEntityPtr<idEntity?>()
                    if (!entPtr.SetSpawnId(spawnId)) {
                        return
                    }
                    entPtr.GetEntity()?._deconstructor()
                }

                GAME_RELIABLE_MESSAGE_CHAT, GAME_RELIABLE_MESSAGE_TCHAT -> {
                    // (client should never get a TCHAT though)
                    val name = CharArray(128)
                    val text = CharArray(128)
                    msg.ReadString(name, 128)
                    msg.ReadString(text, 128)
                    mpGame.AddChatLine("%s^0: %s\n", TempDump.ctos(name), TempDump.ctos(text))
                }

                GAME_RELIABLE_MESSAGE_SOUND_EVENT -> {
                    val snd_evt = snd_evt_t.entries[msg.ReadByte().toInt()]
                    mpGame.PlayGlobalSound(-1, snd_evt)
                }

                GAME_RELIABLE_MESSAGE_SOUND_INDEX -> {
                    val index = gameLocal.ClientRemapDecl(declType_t.DECL_SOUND, msg.ReadLong())
                    if (index >= 0 && index < declManager.GetNumDecls(declType_t.DECL_SOUND)) {
                        val shader = declManager.SoundByIndex(index)!!
                        mpGame.PlayGlobalSound(-1, snd_evt_t.SND_COUNT, shader.GetName())
                    }
                }

                GAME_RELIABLE_MESSAGE_DB -> {
                    val msg_evt = msg_evt_t.entries[msg.ReadByte().toInt()]
                    val parm1: Int
                    val parm2: Int
                    parm1 = msg.ReadByte().toInt()
                    parm2 = msg.ReadByte().toInt()
                    mpGame.PrintMessageEvent(-1, msg_evt, parm1, parm2)
                }

                GAME_RELIABLE_MESSAGE_EVENT -> {
                    val event: entityNetEvent_s?

                    // allocate new event
                    event = eventQueue.Alloc()
                    eventQueue.Enqueue(event, outOfOrderBehaviour_t.OUTOFORDER_IGNORE)
                    event.spawnId = msg.ReadBits(32)
                    event.event = msg.ReadByte().toInt()
                    event.time = msg.ReadLong()
                    event.paramsSize = msg.ReadBits(idMath.BitsForInteger(MAX_EVENT_PARAM_SIZE))
                    if (event.paramsSize != 0) {
                        if (event.paramsSize > MAX_EVENT_PARAM_SIZE) {
                            NetworkEventWarning(event, "invalid param size")
                            return
                        }
                        msg.ReadByteAlign()
                        msg.ReadData(event.paramsBuf, event.paramsSize)
                    }
                }

                GAME_RELIABLE_MESSAGE_SERVERINFO -> {
                    val info = idDict()
                    msg.ReadDeltaDict(info, null)
                    gameLocal.SetServerInfo(info)
                }

                GAME_RELIABLE_MESSAGE_RESTART -> {
                    // D3XP: read server info delta that the server writes
                    val newServerInfo = msg.ReadBits(1)
                    if (newServerInfo != 0) {
                        val info = idDict()
                        msg.ReadDeltaDict(info, null)
                        gameLocal.SetServerInfo(info)
                    }
                    MapRestart()
                }

                GAME_RELIABLE_MESSAGE_TOURNEYLINE -> {
                    line = msg.ReadByte().toInt()
                    p = entities[clientNum] as idPlayer?
                    if (null == p) {
                        return
                    }
                    p.tourneyLine = line
                }

                GAME_RELIABLE_MESSAGE_STARTVOTE -> {
                    val voteString = CharArray(MAX_STRING_CHARS)
                    val clientNum2 = msg.ReadByte().toInt()
                    msg.ReadString(voteString, MAX_STRING_CHARS)
                    mpGame.ClientStartVote(clientNum2, TempDump.ctos(voteString))
                }

                GAME_RELIABLE_MESSAGE_UPDATEVOTE -> {
                    val result = msg.ReadByte().toInt()
                    val yesCount = msg.ReadByte().toInt()
                    val noCount = msg.ReadByte().toInt()
                    mpGame.ClientUpdateVote(vote_result_t.entries[result], yesCount, noCount)
                }

                GAME_RELIABLE_MESSAGE_PORTALSTATES -> {
                    val numPortals = msg.ReadLong()
                    assert(numPortals == gameRenderWorld!!.NumPortals())
                    var i = 0
                    while (i < numPortals) {
                        gameRenderWorld!!.SetPortalState( /*(qhandle_t)*/i + 1, msg.ReadBits(NUM_RENDER_PORTAL_BITS)
                        )
                        i++
                    }
                }

                GAME_RELIABLE_MESSAGE_PORTAL -> {
                    val   /*qhandle_t*/portal = msg.ReadLong()
                    val blockingBits = msg.ReadBits(NUM_RENDER_PORTAL_BITS)
                    assert(portal > 0 && portal <= gameRenderWorld!!.NumPortals())
                    gameRenderWorld!!.SetPortalState(portal, blockingBits)
                }

                GAME_RELIABLE_MESSAGE_STARTSTATE -> {
                    mpGame.ClientReadStartState(msg)
                }

                GAME_RELIABLE_MESSAGE_WARMUPTIME -> {
                    mpGame.ClientReadWarmupTime(msg)
                }

                else -> {
                    Error("Unknown server.client reliable message: %d", id)
                }
            }
        }

        override fun ClientPrediction(
            clientNum: Int, clientCmds: Array<usercmd_t>, lastPredictFrame: Boolean
        ): gameReturn_t {
            var ent: idEntity?
            val player: idPlayer?
            val ret = gameReturn_t()
            ret.sessionCommand[0] = '\u0000'
            player = entities[clientNum] as idPlayer?
            if (null == player) {
                return ret
            }

            // check for local client lag
            player.isLagged =
                NetworkSystem.networkSystem.ClientGetTimeSinceLastPacket() >= Game_network.net_clientMaxPrediction.GetInteger()
            InitLocalClient(clientNum)

            // update the game time
            framenum++
            previousTime = time
            msec = CalcMSec(framenum.toLong())
            time += msec

            // update the real client time and the new frame flag
            if (time > realClientTime) {
                realClientTime = time
                isNewFrame = true
            } else {
                isNewFrame = false
            }

            // D3XP: sync slow/fast time states
            if (isD3XP) {
                slow.Set(time, previousTime, msec, framenum, realClientTime, msecPrecise)
                fast.Set(time, previousTime, msec, framenum, realClientTime, msecPrecise)
            }

            // set the user commands for this frame
            for (i in 0 until numClients) {
                usercmds[i].set(clientCmds[i])
            }

            // run prediction on all entities from the last snapshot
            ent = snapshotEntities.Next()
            while (ent != null) {
                ent.thinkFlags = ent.thinkFlags or TH_PHYSICS
                ent.ClientPredictionThink()
                ent = ent.snapshotNode.Next()
            }

            // service any pending events
            idEvent.ServiceEvents()

            // show any debug info for this frame
            if (isNewFrame) {
                RunDebugInfo()
                SysCmds.D_DrawDebugLines()
            }
            if (sessionCommand.Length() != 0) {
//                strncpy(ret.sessionCommand, sessionCommand, sizeof(ret.sessionCommand));
                ret.sessionCommand = sessionCommand.c_str()
            }
            return ret
        }

        override fun GetClientStats(clientNum: Int, data: Array<String>, len: Int) {
            mpGame.PlayerStats(clientNum, data, len)
        }

        override fun SwitchTeam(clientNum: Int, team: Int) {
            val player: idPlayer?
            player = if (clientNum >= 0) gameLocal.entities[clientNum] as idPlayer else null
            if (null == player) {
                return
            }
            val oldTeam = player.team

            // Put in spectator mode
            if (team == -1) {
                (entities[clientNum] as idPlayer).Spectate(true)
            } // Switch to a team
            else {
                mpGame.SwitchToTeam(clientNum, oldTeam, team)
            }
        }

        override fun DownloadRequest(
            IP: String, guid: String, paks: String, urls: CharArray /*[MAX_STRING_CHARS ]*/
        ): Boolean {
            if (0 == CVarSystem.cvarSystem.GetCVarInteger("net_serverDownload")) {
                return false
            }
            return if (CVarSystem.cvarSystem.GetCVarInteger("net_serverDownload") == 1) {
                // 1: single URL redirect
                if (CVarSystem.cvarSystem.GetCVarString("si_serverURL").isEmpty()) {
                    Common.common.Warning("si_serverURL not set")
                    return false
                }
                idStr.snPrintf(
                    urls, MAX_STRING_CHARS, "1;%s", CVarSystem.cvarSystem.GetCVarString("si_serverURL")
                )
                true
            } else {
                // 2: table of pak URLs
                // first token is the game pak if request, empty if not requested by the client
                // there may be empty tokens for paks the server couldn't pinpoint - the order matters
                var reply = "2;"
                val dlTable = idStrList()
                val pakList = idStrList()
                var i: Int
                var j: Int
                Tokenize(dlTable, CVarSystem.cvarSystem.GetCVarString("net_serverDlTable"))
                Tokenize(pakList, paks)
                i = 0
                while (i < pakList.size()) {
                    if (i > 0) {
                        reply += ";"
                    }
                    if (pakList[i].IsEmpty()) { //[ i ][ 0 ] == '\0' ) {
                        if (i == 0) {
                            // pak 0 will always miss when client doesn't ask for game bin
                            Common.common.DPrintf("no game pak request\n")
                        } else {
                            Common.common.DPrintf("no pak %d\n", i.toString())
                        }
                        i++
                        continue
                    }
                    j = 0
                    while (j < dlTable.size()) {
                        if (!FileSystem_h.fileSystem.FilenameCompare(pakList[i], dlTable[j])) {
                            break
                        }
                        j++
                    }
                    if (j == dlTable.size()) {
                        Common.common.Printf("download for %s: pak not matched: %s\n", IP, pakList[i])
                    } else {
                        val url = idStr(CVarSystem.cvarSystem.GetCVarString("net_serverDlBaseURL"))
                        url.AppendPath(dlTable[j])
                        reply += url
                        Common.common.DPrintf("download for %s: %s\n", IP, url.toString())
                    }
                    i++
                }
                idStr.Copynz(urls, reply, MAX_STRING_CHARS)
                true
            }
            //	return false;
        }

        /*
         ===================
         idGameLocal::LoadMap

         Initializes all map variables common to both save games and spawned games.
         ===================
         */
        fun LoadMap(mapName: String, randseed: Int) {
            var i: Int
            val sameMap = mapFile != null && idStr.Icmp(mapFileName, mapName) == 0

            // clear the sound system
            gameSoundWorld!!.ClearAllSoundEmitters()
            if (isD3XP) {
                gameSoundWorld!!.SetEnviroSuit(false)
                gameSoundWorld!!.SetSlowmo(false)
            }
            InitAsyncNetwork()
            if (!sameMap || mapFile != null && mapFile!!.NeedsReload()) {
                // load the .map file
                if (mapFile != null) {
                    mapFile = null
                }
                mapFile = idMapFile()
                if (!mapFile!!.Parse("$mapName.map")) {
                    mapFile = null
                    Error("Couldn't load %s", mapName)
                }
            }
            mapFileName.set(mapFile!!.GetName())

            // load the collision map
            collisionModelManager.LoadMap(mapFile)
            numClients = 0

            // initialize all entities for this game
            entities = arrayOfNulls(entities.size) //	memset( entities, 0, sizeof( entities ) );
            usercmds = Array(usercmds.size) { usercmd_t() } //memset( usercmds, 0, sizeof( usercmds ) );
            spawnIds = IntArray(spawnIds.size) { -1 } //memset( spawnIds, -1, sizeof( spawnIds ) );
            spawnCount = INITIAL_SPAWN_COUNT
            spawnedEntities.Clear()
            activeEntities.Clear()
            numEntitiesToDeactivate = 0
            sortTeamMasters = false
            sortPushers = false
            lastGUIEnt.oSet(null)
            lastGUI = 0
            globalMaterial = null
            globalShaderParms = FloatArray(globalShaderParms.size)

            // always leave room for the max number of clients,
            // even if they aren't all used, so numbers inside that
            // range are NEVER anything but clients
            num_entities = MAX_CLIENTS
            firstFreeIndex = MAX_CLIENTS

            // reset the random number generator.
            random.SetSeed(if (isMultiplayer) randseed else 0)
            camera = null
            world = null
            testmodel = null
            testFx = null
            lastAIAlertEntity.oSet(null)
            lastAIAlertTime = 0
            previousTime = 0
            time = 0
            framenum = 0
            msec = UsercmdGen.USERCMD_MSEC
            sessionCommand.set("")
            nextGibTime = 0

            // D3XP: reset portal sky and slow-mo state on map load
            if (isD3XP) {
                portalSkyEnt.oSet(null)
                portalSkyActive = false
                ResetSlowTimeVars()
            }

            vacuumAreaNum = -1 // if an info_vacuum is spawned, it will set this
            if (null == editEntities) {
                editEntities = idEditEntities()
            }
            gravity.set(0.0f, 0.0f, -SysCvar.g_gravity.GetFloat())
            spawnArgs.Clear()
            skipCinematic = false
            inCinematic = false
            cinematicSkipTime = 0
            cinematicStopTime = 0
            cinematicMaxSkipTime = 0
            clip.Init()
            pvs.Init()
            playerPVS.i = -1
            playerConnectedAreas.i = -1

            // load navigation system for all the different monster sizes
            i = 0
            while (i < aasNames.size()) {
                aasList[i].Init(idStr(mapFileName).SetFileExtension(aasNames[i]), mapFile!!.GetGeometryCRC())
                i++
            }

            // clear the smoke particle free list
            smokeParticles!!.Init()

            // cache miscellanious media references
            FindEntityDef("preCacheExtras", false)
            if (!sameMap) {
                mapFile!!.RemovePrimitiveData()
            }
        }

        fun LocalMapRestart() {
            var i: Int
            val latchSpawnCount: Int
            Printf("----------- Game Map Restart ------------\n")
            gamestate = gameState_t.GAMESTATE_SHUTDOWN
            i = 0
            while (i < MAX_CLIENTS) {
                if (entities[i] != null && entities[i] is idPlayer) {
                    (entities[i] as idPlayer).PrepareForRestart()
                }
                i++
            }
            eventQueue.Shutdown()
            savedEventQueue.Shutdown()
            MapClear(false)

            // clear the smoke particle free list
            smokeParticles!!.Init()

            // clear the sound system
            if (gameSoundWorld != null) {
                gameSoundWorld!!.ClearAllSoundEmitters()
                if (isD3XP) {
                    gameSoundWorld!!.SetEnviroSuit(false)
                    gameSoundWorld!!.SetSlowmo(false)
                }
            }

            // the spawnCount is reset to zero temporarily to spawn the map entities with the same spawnId
            // if we don't do that, network clients are confused and don't show any map entities
            latchSpawnCount = spawnCount
            spawnCount = INITIAL_SPAWN_COUNT
            gamestate = gameState_t.GAMESTATE_STARTUP
            program.Restart()
            InitScriptForMap()
            MapPopulate()

            // once the map is populated, set the spawnCount back to where it was so we don't risk any collision
            // (note that if there are no players in the game, we could just leave it at it's current value)
            spawnCount = latchSpawnCount

            // setup the client entities again
            i = 0
            while (i < MAX_CLIENTS) {
                if (entities[i] != null && entities[i] is idPlayer) {
                    (entities[i] as idPlayer).Restart()
                }
                i++
            }
            gamestate = gameState_t.GAMESTATE_ACTIVE
            Printf("--------------------------------------\n")
        }

        fun MapRestart() {
            val outMsg = idBitMsg()
            val msgBuf = ByteBuffer.allocate(MAX_GAME_MESSAGE_SIZE)
            val newInfo = idDict()
            var i: Int
            var keyval: idKeyValue?
            var keyval2: idKeyValue?
            // D3XP: auto-correct gametype if current map doesn't support it
            if (isD3XP && isMultiplayer && isServer) {
                val buf = CharArray(MAX_STRING_CHARS)
                GetBestGameType(SysCvar.si_map.GetString()!!, SysCvar.si_gameType.GetString()!!, buf)
                val bestGametype = String(buf).substringBefore('\u0000')
                if (bestGametype != SysCvar.si_gameType.GetString()) {
                    CVarSystem.cvarSystem.SetCVarString("si_gameType", bestGametype)
                }
            }
            if (isClient) {
                LocalMapRestart()
            } else {
                newInfo.set(CVarSystem.cvarSystem.MoveCVarsToDict(CVarSystem.CVAR_SERVERINFO))
                i = 0
                while (i < newInfo.GetNumKeyVals()) {
                    keyval = newInfo.GetKeyVal(i)!!
                    keyval2 = serverInfo.FindKey(keyval.GetKey())
                    if (null == keyval2) {
                        break
                    }
                    // a select set of si_ changes will cause a full restart of the server
                    if (keyval.GetValue().Cmp(keyval2.GetValue()) != 0 && (keyval.GetKey()
                            .Cmp("si_pure") == 0 || keyval.GetKey().Cmp("si_map") == 0)
                    ) {
                        break
                    }
                    i++
                }
                CmdSystem.cmdSystem.BufferCommandText(cmdExecution_t.CMD_EXEC_NOW, "rescanSI")
                if (i != newInfo.GetNumKeyVals()) {
                    CmdSystem.cmdSystem.BufferCommandText(cmdExecution_t.CMD_EXEC_APPEND, "nextMap")
                } else {
                    outMsg.Init(msgBuf, MAX_GAME_MESSAGE_SIZE)
                    outMsg.WriteByte(GAME_RELIABLE_MESSAGE_RESTART.toByte())
                    outMsg.WriteBits(1, 1)
                    outMsg.WriteDeltaDict(serverInfo, null)
                    NetworkSystem.networkSystem.ServerSendReliableMessage(-1, outMsg)
                    LocalMapRestart()
                    mpGame.MapRestart()
                }
            }
        }

        fun Printf(fmt: String, vararg args: Any?) {
//	va_list		argptr;
//	char		text[MAX_STRING_CHARS];
//
//	va_start( argptr, fmt );
//	idStr::vsnPrintf( text, sizeof( text ), fmt, argptr );
//	va_end( argptr );
            Common.common.Printf("%s", String.format(fmt, *args))
        }

        fun Warning(fmt: String, vararg args: Any?) {
//	va_list		argptr;
            val text = StringBuilder(MAX_STRING_CHARS)
            val thread: idThread?
            //
//	va_start( argptr, fmt );
//	idStr::vsnPrintf( text, sizeof( text ), fmt, argptr );
//	va_end( argptr );
            text.append(String.format(fmt, *args))
            thread = idThread.CurrentThread()
            if (thread != null) {
                thread.Warning("%s", text)
            } else {
                Common.common.Warning("%s", text)
            }
        }

        fun DWarning(fmt: String, vararg args: Any?) {
//	va_list		argptr;
            val text = StringBuilder(MAX_STRING_CHARS)
            val thread: idThread?
            if (!Common.com_developer.GetBool()) {
                return
            }
            text.append(String.format(fmt, *args))
            //	va_start( argptr, fmt );
//	idStr::vsnPrintf( text, sizeof( text ), fmt, argptr );
//	va_end( argptr );
            thread = idThread.CurrentThread()
            if (thread != null) {
                thread.Warning("%s", text)
            } else {
                Common.common.DWarning("%s", text)
            }
        }

        fun DPrintf(fmt: String, vararg args: Any?) {
//	va_list		argptr;
//	char		text[MAX_STRING_CHARS];
            if (!Common.com_developer.GetBool()) {
                return
            }

//	va_start( argptr, fmt );
//	idStr::vsnPrintf( text, sizeof( text ), fmt, argptr );
//	va_end( argptr );
//
            Common.common.Printf("%s", String.format(fmt, *args))
        }

        fun NextMap(): Boolean {    // returns wether serverinfo settings have been modified
            var func: function_t?
            val thread: idThread
            val newInfo = idDict()
            var keyval: idKeyValue?
            var keyval2: idKeyValue?
            var i: Int
            if (SysCvar.g_mapCycle.GetString().isNullOrEmpty()) {
                Printf("%s", Common.common.GetLanguageDict().GetString("#str_04294"))
                return false
            }
            if (FileSystem_h.fileSystem.ReadFile(SysCvar.g_mapCycle.GetString()!!, null, null) < 0) {
                if (FileSystem_h.fileSystem.ReadFile(
                        Str.va("%s.scriptcfg", SysCvar.g_mapCycle.GetString()!!), null, null
                    ) < 0
                ) {
                    Printf("map cycle script '%s': not found\n", SysCvar.g_mapCycle.GetString())
                    return false
                } else {
                    SysCvar.g_mapCycle.SetString(Str.va("%s.scriptcfg", SysCvar.g_mapCycle.GetString()!!))
                }
            }
            Printf("map cycle script: '%s'\n", SysCvar.g_mapCycle.GetString())
            func = program.FindFunction("mapcycle::cycle")
            if (func == null) {
                program.CompileFile(SysCvar.g_mapCycle.GetString()!!)
                func = program.FindFunction("mapcycle::cycle")
            }
            if (null == func) {
                Printf("Couldn't find mapcycle::cycle\n")
                return false
            }
            thread = idThread(func)
            thread.Start()
            //	delete thread;
            newInfo.set(CVarSystem.cvarSystem.MoveCVarsToDict(CVarSystem.CVAR_SERVERINFO))
            i = 0
            while (i < newInfo.GetNumKeyVals()) {
                keyval = newInfo.GetKeyVal(i)!!
                keyval2 = serverInfo.FindKey(keyval.GetKey())
                if (null == keyval2 || keyval.GetValue().Cmp(keyval2.GetValue()) != 0) {
                    break
                }
                i++
            }
            return i != newInfo.GetNumKeyVals()
        }

        /*
         ================
         idGameLocal::GetLevelMap

         should only be used for in-game level editing
         ================
         */
        fun GetLevelMap(): idMapFile? {
            if (mapFile != null && mapFile!!.HasPrimitiveData()) {
                return mapFile
            }
            if (0 == mapFileName.Length()) {
                return null
            }

//	if ( mapFile ) {
//		delete mapFile;
//	}
            mapFile = idMapFile()
            if (!mapFile!!.Parse(mapFileName)) {
//		delete mapFile;
                mapFile = null
            }
            return mapFile
        }

        fun GetMapName(): String {
            return mapFileName.toString()
        }

        fun NumAAS(): Int {
            return aasList.Num()
        }

        fun GetAAS(num: Int): idAAS? {
            if (num >= 0 && num < aasList.Num()) {
                if (aasList[num] != null && aasList[num].GetSettings() != null) {
                    return aasList[num]
                }
            }
            return null
        }

        fun GetAAS(name: String): idAAS? {
            var i: Int
            i = 0
            while (i < aasNames.size()) {
                if (aasNames[i].toString() == name) {
                    return if (aasList[i].GetSettings() == null) {
                        null
                    } else {
                        aasList[i]
                    }
                }
                i++
            }
            return null
        }

        fun SetAASAreaState(bounds: idBounds, areaContents: Int, closed: Boolean) {
            var i: Int
            i = 0
            while (i < aasList.Num()) {
                aasList[i].SetAreaState(bounds, areaContents, closed)
                i++
            }
        }

        fun  /*aasHandle_t*/AddAASObstacle(bounds: idBounds): Int {
            var i: Int
            val obstacle: Int
            var check: Int
            if (0 == aasList.Num()) {
                return -1
            }
            obstacle = aasList[0].AddObstacle(bounds)
            i = 1
            while (i < aasList.Num()) {
                check = aasList[i].AddObstacle(bounds)
                assert(check == obstacle)
                i++
            }
            return obstacle
        }

        fun RemoveAASObstacle(   /*aasHandle_t*/handle: Int) {
            var i: Int
            i = 0
            while (i < aasList.Num()) {
                aasList[i].RemoveObstacle(handle)
                i++
            }
        }

        fun RemoveAllAASObstacles() {
            var i: Int
            i = 0
            while (i < aasList.Num()) {
                aasList[i].RemoveAllObstacles()
                i++
            }
        }


        fun CheatsOk(requirePlayer: Boolean = true /*= true*/): Boolean {
            val player: idPlayer?
            if (isMultiplayer && !CVarSystem.cvarSystem.GetCVarBool("net_allowCheats")) {
                Printf("Not allowed in multiplayer.\n")
                return false
            }
            if (Common.com_developer.GetBool()) {
                return true
            }
            player = GetLocalPlayer()
            if (!requirePlayer || player != null && player.health > 0) {
                return true
            }
            Printf("You must be alive to use this command.\n")
            return false
        }

        fun SetSkill(value: Int) {
            val skill_level: Int
            skill_level = if (value < 0) {
                0
            } else if (value > 3) {
                3
            } else {
                value
            }
            SysCvar.g_skill.SetInteger(skill_level)
        }

        /*
         ==============
         idGameLocal::GameState

         Used to allow entities to know if they're being spawned during the initial spawn.
         ==============
         */
        fun GameState(): gameState_t {
            return gamestate
        }

        fun SpawnEntityType(
            classdef: idTypeInfo, args: idDict? /*= NULL*/, bIsClientReadSnapshot: Boolean /*= false*/
        ): idEntity? {
            var obj: idClass?
            if (_DEBUG) {
                assert(!isClient || bIsClientReadSnapshot)
            }
            if (!classdef.IsType(idEntity.Type)) {
                Error("Attempted to spawn non-entity class '%s'", classdef.classname)
            }
            try {
                if (args != null) {
                    spawnArgs.set(args)
                } else {
                    spawnArgs.Clear()
                }
                obj = classdef.createInstance.invoke()
                obj.CallSpawn()
            } catch (ex: idAllocError) {
                obj = null
            }
            spawnArgs.Clear()
            return obj as idEntity?
        }

        fun SpawnEntityType(classdef: idTypeInfo, args: idDict? /*= NULL*/): idEntity? {
            return SpawnEntityType(classdef, args, false)
        }


        fun SpawnEntityType(classdef: idTypeInfo): idEntity? {
            return SpawnEntityType(classdef, null)
        }


        fun SpawnEntityDef(
            args: idDict, ent: Array<idEntity?>? = null /*= NULL*/, setDefaults: Boolean = true /*= true*/
        ): Boolean {
            val classname = arrayOfNulls<String>(1)
            val spawn = arrayOfNulls<String>(1)
            var error = ""
            val name = arrayOfNulls<String>(1)

            if (ent != null) {
                ent[0] = null
            }
            spawnArgs.set(args)
            if (spawnArgs.GetString("name", "", name)) {
                error = String.format(" on '%s'", name[0])
            }
            spawnArgs.GetString("classname", "", classname)
            val def = FindEntityDef(classname[0]!!, false)
            if (null == def) {
                Warning("Unknown classname '%s'%s.", classname[0], error)
                return false
            }
            spawnArgs.SetDefaults(def.dict)

            // D3XP: set "slowmo" = false on fast-timeline entities (player, weapons, projectiles)
            if (isD3XP && spawnArgs.FindKey("slowmo") == null) {
                if (FAST_ENTITY_LIST.any { idStr.Cmp(classname[0]!!, it) == 0 }) {
                    spawnArgs.SetBool("slowmo", false)
                }
            }

            // check if we should spawn a class object
            spawnArgs.GetString("spawnclass", "", spawn)
            if (!spawn[0].isNullOrEmpty()) {
                val cls = idClass.GetClass(spawn[0])
                if (cls == null) {
                    Warning("Could not spawn '%s'. Class '%s' not found%s.", classname[0], spawn[0], error)
                    return false
                }
                val obj = cls.createInstance()
                obj.CallSpawn()
                if (ent != null && obj.IsType(idEntity.Type)) {
                    ent[0] = obj as idEntity
                }
                return true
            }

            // check if we should call a script function to spawn
            spawnArgs.GetString("spawnfunc", "", spawn)
            if (spawn[0] != null) {
                val func = program.FindFunction(spawn[0])
                if (null == func) {
                    Warning("Could not spawn '%s'.  Script function '%s' not found%s.", classname[0], spawn[0], error)
                    return false
                }
                val thread = idThread(func)
                thread.DelayedStart(0)
                return true
            }
            Warning("%s doesn't include a spawnfunc or spawnclass%s.", classname[0], error)
            return false
        }

        fun GetSpawnId(ent: idEntity): Int {
            return gameLocal.spawnIds[ent.entityNumber] shl GENTITYNUM_BITS or ent.entityNumber
        }


        fun FindEntityDef(name: String, makeDefault: Boolean = true /*= true*/): idDeclEntityDef? {
            var decl: idDecl? = null
            if (isMultiplayer) {
                decl = declManager.FindType(declType_t.DECL_ENTITYDEF, Str.va("%s_mp", name), false)
            }
            if (null == decl) {
                decl = declManager.FindType(declType_t.DECL_ENTITYDEF, name, makeDefault)
            }
            return decl as idDeclEntityDef?
        }


        fun FindEntityDefDict(name: String, makeDefault: Boolean = true /*= true*/): idDict? {
            val decl = FindEntityDef(name, makeDefault)
            return decl?.dict
        }

        fun FindEntityDefDict(name: idStr?): idDict? {
            return FindEntityDefDict(name.toString())
        }

        fun RegisterEntity(ent: idEntity) {
            val spawn_entnum = CInt()
            if (spawnCount >= 1 shl 32 - GENTITYNUM_BITS) {
                Error("idGameLocal::RegisterEntity: spawn count overflow")
            }
            if (!spawnArgs.GetInt("spawn_entnum", "0", spawn_entnum)) {
                while (entities[firstFreeIndex] != null && firstFreeIndex < ENTITYNUM_MAX_NORMAL) {
                    firstFreeIndex++
                }
                if (firstFreeIndex >= ENTITYNUM_MAX_NORMAL) {
                    Error("no free entities")
                }
                spawn_entnum._val = (firstFreeIndex++)
            }
            entities[spawn_entnum._val] = ent
            spawnIds[spawn_entnum._val] = spawnCount++
            ent.entityNumber = spawn_entnum._val
            ent.spawnNode.AddToEnd(spawnedEntities)
            ent.spawnArgs.TransferKeyValues(spawnArgs)
            if (spawn_entnum._val >= num_entities) {
                num_entities++
            }
        }

        fun UnregisterEntity(ent: idEntity) {
            assert(ent != null)
            if (editEntities != null) {
                editEntities!!.RemoveSelectedEntity(ent)
            }
            if (ent.entityNumber != ENTITYNUM_NONE && entities[ent.entityNumber] == ent) {
                ent.spawnNode.Remove()
                entities[ent.entityNumber] = null
                spawnIds[ent.entityNumber] = -1
                if (ent.entityNumber >= MAX_CLIENTS && ent.entityNumber < firstFreeIndex) {
                    firstFreeIndex = ent.entityNumber
                }
                ent.entityNumber = ENTITYNUM_NONE
            }
        }

        fun RequirementMet(activator: idEntity?, requires: idStr, removeItem: Int): Boolean {
            if (requires.Length() != 0) {
                if (activator is idPlayer) {
                    val player = activator
                    val item = player.FindInventoryItem(requires)
                    return if (item != null) {
                        if (removeItem != 0) {
                            player.RemoveInventoryItem(item)
                        }
                        true
                    } else {
                        false
                    }
                }
            }
            return true
        }

        fun AlertAI(ent: idEntity?) {
            if (ent != null && ent is idActor) {
                // alert them for the next frame
                lastAIAlertTime = (time + msecPrecise).toInt()
                lastAIAlertEntity.oSet(ent)
            }
        }

        fun GetAlertEntity(): idActor? {
            if (isD3XP) {
                val timeGroup = if (lastAIAlertTime != 0 && lastAIAlertEntity.GetEntity() != null)
                    lastAIAlertEntity.GetEntity()!!.timeGroup else 0
                SetTimeState(timeGroup).use {
                    return if (lastAIAlertTime >= time) lastAIAlertEntity.GetEntity() else null
                }
            }
            return if (lastAIAlertTime >= time) lastAIAlertEntity.GetEntity() else null
        }

        /*
         ================
         idGameLocal::InPlayerPVS

         should only be called during entity thinking and event handling
         ================
         */
        fun InPlayerPVS(ent: idEntity): Boolean {
            return if (playerPVS.i == -1) {
                false
            } else pvs.InCurrentPVS(playerPVS, ent.GetPVSAreas(), ent.GetNumPVSAreas())
        }

        /*
         ================
         idGameLocal::InPlayerConnectedArea

         should only be called during entity thinking and event handling
         ================
         */
        fun InPlayerConnectedArea(ent: idEntity): Boolean {
            return if (playerConnectedAreas.i == -1) {
                false
            } else pvs.InCurrentPVS(playerConnectedAreas, ent.GetPVSAreas(), ent.GetNumPVSAreas())
        }

        fun SetCamera(cam: idCamera?) {
            var i: Int
            var ent: idEntity?
            var ai: idAI?

            // this should fix going into a cinematic when dead.. rare but happens
            var client = GetLocalPlayer()!!
            if (client.health <= 0 || client.AI_DEAD.underscore()!!) {
                return
            }
            camera = cam
            if (camera != null) {
                inCinematic = true
                if (skipCinematic && camera!!.spawnArgs.GetBool("disconnect")) {
                    camera!!.spawnArgs.SetBool("disconnect", false)
                    CVarSystem.cvarSystem.SetCVarFloat("r_znear", 3.0f)
                    CmdSystem.cmdSystem.BufferCommandText(cmdExecution_t.CMD_EXEC_APPEND, "disconnect\n")
                    skipCinematic = false
                    return
                }
                if (time > cinematicStopTime) {
                    cinematicSkipTime = (time + CINEMATIC_SKIP_DELAY).toInt()
                }

                // set r_znear so that transitioning into/out of the player's head doesn't clip through the view
                CVarSystem.cvarSystem.SetCVarFloat("r_znear", 1.0f)

                // hide all the player models
                i = 0
                while (i < numClients) {
                    if (entities[i] != null) {
                        client = entities[i] as idPlayer
                        client.EnterCinematic()
                    }
                    i++
                }
                if (!cam!!.spawnArgs.GetBool("ignore_enemies")) {
                    // kill any active monsters that are enemies of the player
                    ent = spawnedEntities.Next()
                    while (ent != null) {
                        if (ent.cinematic || ent.fl.isDormant) {
                            // only kill entities that aren't needed for cinematics and aren't dormant
                            ent = ent.spawnNode.Next()
                            continue
                        }
                        if (ent is idAI) {
                            ai = ent
                            if (ai.GetEnemy() == null || !ai.IsActive()) {
                                // no enemy, or inactive, so probably safe to ignore
                                ent = ent.spawnNode.Next()
                                continue
                            }
                        } else if (ent is idProjectile) {
                            // remove all projectiles
                        } else if (ent.spawnArgs.GetBool("cinematic_remove")) {
                            // remove anything marked to be removed during cinematics
                        } else {
                            // ignore everything else
                            ent = ent.spawnNode.Next()
                            continue
                        }

                        // remove it
                        DPrintf("removing '%s' for cinematic\n", ent.GetName())
                        ent.PostEventMS(EV_Remove, 0)
                        ent = ent.spawnNode.Next()
                    }
                }
            } else {
                inCinematic = false
                cinematicStopTime = (time + msecPrecise).toInt()

                // restore r_znear
                CVarSystem.cvarSystem.SetCVarFloat("r_znear", 3.0f)

                // show all the player models
                i = 0
                while (i < numClients) {
                    if (entities[i] != null) {
                        val client2 = entities[i] as idPlayer
                        client2.ExitCinematic()
                    }
                    i++
                }
            }
        }

        fun GetCamera(): idCamera? {
            return camera
        }

        fun SkipCinematic(): Boolean {
            if (camera != null) {
                val camera = camera!!
                if (camera.spawnArgs.GetBool("disconnect")) {
                    camera.spawnArgs.SetBool("disconnect", false)
                    CVarSystem.cvarSystem.SetCVarFloat("r_znear", 3.0f)
                    CmdSystem.cmdSystem.BufferCommandText(cmdExecution_t.CMD_EXEC_APPEND, "disconnect\n")
                    skipCinematic = false
                    return false
                }
                if (camera.spawnArgs.GetBool("instantSkip")) {
                    camera.Stop()
                    return false
                }
            }
            snd_system.soundSystem.SetMute(true)
            if (!skipCinematic) {
                skipCinematic = true
                cinematicMaxSkipTime = (gameLocal.time + SEC2MS(SysCvar.g_cinematicMaxSkipTime.GetFloat()))
            }
            return true
        }

        /*
         ====================
         idGameLocal::CalcFov

         Calculates the horizontal and vertical field of view based on a horizontal field of view and custom aspect ratio
         ====================
         */
        fun CalcFov(base_fov: Float, fov_x: CFloat, fov_y: CFloat) {
            var x: Float
            var y: Float
            var ratio_x: Float
            var ratio_y: Float

//            if (!sys.FPU_StackIsEmpty()) {
//                Printf(sys.FPU_GetState());
//                Error("idGameLocal::CalcFov: FPU stack not empty");
//            }

            // first, calculate the vertical fov based on a 640x480 view
            x = (640.0f / tan((base_fov / 360.0f * idMath.PI)))
            y = atan2(480.0f, x)
            fov_y._val = (y * 360.0f / idMath.PI)
            assert(fov_y._val > 0)
            if (fov_y._val <= 0) {
                Error("idGameLocal::CalcFov: bad result, fov_y == %f, base_fov == %f", fov_y._val, base_fov)
            }
            when (SysCvar.r_aspectRatio.GetInteger()) {
                0 -> {
                    // 4:3
                    fov_x._val = (base_fov)
                    return
                }

                1 -> {
                    // 16:9
                    ratio_x = 16.0f
                    ratio_y = 9.0f
                }

                2 -> {
                    // 16:10
                    ratio_x = 16.0f
                    ratio_y = 10.0f
                }

                else -> {
                    // FIX: Was returning base_fov — C++ default/-1 case uses auto mode from screen resolution
                    // auto mode => use aspect ratio from resolution, assuming screen's pixels are squares
                    ratio_x = RenderSystem.renderSystem.GetScreenWidth().toFloat()
                    ratio_y = RenderSystem.renderSystem.GetScreenHeight().toFloat()
                    if (ratio_x <= 0.0f || ratio_y <= 0.0f) {
                        // for some reason (maybe this is a dedicated server?) GetScreenWidth()/Height()
                        // returned 0. Assume default 4:3 to avoid assert()/Error() below.
                        fov_x._val = base_fov
                        return
                    }
                }
            }
            y = (ratio_y / tan((fov_y._val / 360.0f * idMath.PI)))
            fov_x._val = ((atan2(ratio_x, y) * 360.0f / idMath.PI))
            if (fov_x._val < base_fov) {
                fov_x._val = (base_fov)
                x = (ratio_x / tan((fov_x._val / 360.0f * idMath.PI)))
                fov_y._val = ((atan2(ratio_y, x) * 360.0f / idMath.PI))
            }
            assert(fov_x._val > 0 && fov_y._val > 0)
            if (fov_y._val <= 0 || fov_x._val <= 0) {
                Error(
                    "idGameLocal::CalcFov: bad result, fov_y == %f, fov_x == %f, base_fov == %f",
                    fov_y._val,
                    fov_x._val,
                    base_fov
                )
            }
        }

        fun AddEntityToHash(name: String, ent: idEntity) {
            if (FindEntity(name) != null) {
                Error("Multiple entities named '%s'", name)
            }
            entityHash.Add(entityHash.GenerateKey(name, true), ent.entityNumber)
        }

        fun RemoveEntityFromHash(name: String, ent: idEntity?): Boolean {
            val hash: Int
            var i: Int
            hash = entityHash.GenerateKey(name, true)
            i = entityHash.First(hash)
            while (i != -1) {
                if (entities[i] != null && entities[i] == ent && entities[i]!!.name.Icmp(name) == 0) {
                    entityHash.Remove(hash, i)
                    return true
                }
                i = entityHash.Next(i)
            }
            return false
        }


        fun GetTargets(args: idDict, list: idList<idEntityPtr<idEntity>>, ref: String): Int {
            var i: Int
            val num: Int
            val refLength: Int
            var arg: idKeyValue?
            var ent: idEntity?
            list.Clear()
            refLength = ref.length
            num = args.GetNumKeyVals()
            i = 0
            while (i < num) {
                arg = args.GetKeyVal(i)!!
                if (arg.GetKey().Icmpn(ref, refLength) == 0) {
                    ent = FindEntity(arg.GetValue())
                    if (ent != null) {
                        val entityPtr = list.Alloc()!!
                        entityPtr.oSet(ent)
                    }
                }
                i++
            }
            return list.Num()
        }

        /*
         =============
         idGameLocal::GetTraceEntity

         returns the master entity of a trace.  for example, if the trace entity is the player's head, it will return the player.
         =============
         */
        fun GetTraceEntity(trace: trace_s): idEntity? {
            val master: idEntity?
            if (null == entities[trace.c.entityNum]) {
                return null
            }
            master = entities[trace.c.entityNum]!!.GetBindMaster()
            return master ?: entities[trace.c.entityNum]
        }

        /*
         =============
         idGameLocal::FindTraceEntity

         Searches all active entities for the closest ( to start ) match that intersects
         the line start,end
         =============
         */
        fun FindTraceEntity(start: idVec3, end: idVec3, c: idTypeInfo, skip: idEntity?): idEntity? {
            var ent: idEntity?
            var bestEnt: idEntity?
            val scale = CFloat()
            var bestScale: Float
            val b = idBounds()
            bestEnt = null
            bestScale = 1.0f
            ent = spawnedEntities.Next()
            while (ent != null) {
                if (ent.IsType(c) && ent !== skip) {
                    b.set(ent.GetPhysics().GetAbsBounds().Expand(16.0f))
                    if (b.RayIntersection(start, end.minus(start), scale)) {
                        if (scale._val >= 0.0f && scale._val < bestScale) {
                            bestEnt = ent
                            bestScale = scale._val
                        }
                    }
                }
                ent = ent.spawnNode.Next()
            }
            return bestEnt
        }

        /*
         =============
         idGameLocal::FindEntity

         Returns the entity whose name matches the specified string.
         =============
         */
        fun FindEntity(name: String): idEntity? {
            val hash: Int
            var i: Int
            hash = entityHash.GenerateKey(name, true)
            i = entityHash.First(hash)
            while (i != -1) {
                if (entities[i] != null && entities[i]!!.name.Icmp(name) == 0) {
                    return entities[i]
                }
                i = entityHash.Next(i)
            }
            return null
        }

        fun FindEntity(name: idStr?): idEntity? {
            return FindEntity(name.toString())
        }

        /*
         =============
         idGameLocal::FindEntityUsingDef

         Searches all active entities for the next one using the specified entityDef.

         Searches beginning at the entity after from, or the beginning if NULL
         NULL will be returned if the end of the list is reached.
         =============
         */
        fun FindEntityUsingDef(from: idEntity?, match: String): idEntity? {
            var ent: idEntity?
            ent = if (null == from) {
                spawnedEntities.Next()
            } else {
                from.spawnNode.Next()
            }
            while (ent != null) {
                assert(ent != null)
                if (idStr.Icmp(ent.GetEntityDefName(), match) == 0) {
                    return ent
                }
                ent = ent.spawnNode.Next()
            }
            return null
        }

        fun EntitiesWithinRadius(org: idVec3, radius: Float, entityList: Array<idEntity?>, maxCount: Int): Int {
            var ent: idEntity?
            val bo = idBounds(org)
            var entCount = 0
            bo.ExpandSelf(radius)
            ent = spawnedEntities.Next()
            while (ent != null) {
                if (ent.GetPhysics().GetAbsBounds().IntersectsBounds(bo)) {
                    entityList[entCount++] = ent
                }
                ent = ent.spawnNode.Next()
            }
            return entCount
        }

        /*
         =================
         idGameLocal::KillBox

         Kills all entities that would touch the proposed new positioning of ent. The ent itself will not being killed.
         Checks if player entities are in the teleporter, and marks them to die at teleport exit instead of immediately.
         If catch_teleport, this only marks teleport players for death on exit
         =================
         */

        fun KillBox(ent: idEntity, catch_teleport: Boolean = false /*= false*/) {
            var i: Int
            val num: Int
            var hit: idEntity
            var cm: idClipModel
            val clipModels = arrayOfNulls<idClipModel>(MAX_GENTITIES)
            val phys: idPhysics?
            phys = ent.GetPhysics()
            if (0 == phys.GetNumClipModels()) {
                return
            }
            num = clip.ClipModelsTouchingBounds(
                phys.GetAbsBounds(), phys.GetClipMask(), clipModels, MAX_GENTITIES
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
                if (hit == ent || !hit.fl.takedamage) {
                    i++
                    continue
                }
                if (0 == phys.ClipContents(cm)) {
                    i++
                    continue
                }

                // nail it
                if (hit is idPlayer && hit.IsInTeleport()) {
                    hit.TeleportDeath(ent.entityNumber)
                } else if (!catch_teleport) {
                    hit.Damage(ent, ent, vec3_origin, "damage_telefrag", 1.0f, Model.INVALID_JOINT)
                }
                if (!gameLocal.isMultiplayer) {
                    // let the mapper know about it
                    Warning("'%s' telefragged '%s'", ent.name, hit.name)
                }
                i++
            }
        }


        fun RadiusDamage(
            origin: idVec3,
            inflictor: idEntity?,
            attacker: idEntity?,
            ignoreDamage: idEntity?,
            ignorePush: idEntity?,
            damageDefName: String,
            dmgPower: Float = 1.0f /*= 1.0f*/
        ) {
            var inflictor = inflictor
            var attacker = attacker
            var ignoreDamage = ignoreDamage
            var dist: Float
            var damageScale: Float
            val attackerDamageScale = CFloat()
            val attackerPushScale = CFloat()
            var ent: idEntity?
            val entityList = arrayOfNulls<idEntity?>(MAX_GENTITIES)
            val numListedEntities: Int
            val bounds: idBounds
            val v = idVec3()
            val damagePoint = idVec3()
            val dir = idVec3()
            var i: Int
            var e: Int
            val damage = CInt()
            val radius = CInt()
            val push = CInt()
            val damageDef = FindEntityDefDict(damageDefName, false)
            if (null == damageDef) {
                Warning("Unknown damageDef '%s'", damageDefName)
                return
            }
            damageDef.GetInt("damage", "20", damage)
            damageDef.GetInt("radius", "50", radius)
            damageDef.GetInt("push", Str.va("%d", damage._val * 100), push)
            damageDef.GetFloat("attackerDamageScale", "0.5f", attackerDamageScale)
            damageDef.GetFloat("attackerPushScale", "0", attackerPushScale)
            if (radius._val < 1) {
                radius._val = (1)
            }
            bounds = idBounds(origin).Expand(radius._val.toFloat())

            // get all entities touching the bounds
            numListedEntities = clip.EntitiesTouchingBounds(bounds, -1, entityList, MAX_GENTITIES)
            if (inflictor != null && inflictor is idAFAttachment) {
                inflictor = inflictor.GetBody()
            }
            if (attacker != null && attacker is idAFAttachment) {
                attacker = attacker.GetBody()
            }
            if (ignoreDamage != null && ignoreDamage is idAFAttachment) {
                ignoreDamage = ignoreDamage.GetBody()
            }

            // apply damage to the entities
            e = 0
            while (e < numListedEntities) {
                ent = entityList[e]
                assert(ent != null)
                if (!ent!!.fl.takedamage) {
                    e++
                    continue
                }
                if (ent === inflictor || ent is idAFAttachment && ent.GetBody() === inflictor) {
                    e++
                    continue
                }
                if (ent === ignoreDamage || ent is idAFAttachment && ent.GetBody() === ignoreDamage) {
                    e++
                    continue
                }

                // don't damage a dead player
                if (isMultiplayer && ent.entityNumber < MAX_CLIENTS && ent is idPlayer && ent.health < 0) {
                    e++
                    continue
                }

                // find the distance from the edge of the bounding box
                i = 0
                while (i < 3) {
                    if (origin[i] < ent.GetPhysics().GetAbsBounds()[0, i]) {
                        v[i] = ent.GetPhysics().GetAbsBounds()[0, i] - origin[i]
                    } else if (origin[i] > ent.GetPhysics().GetAbsBounds()[1, i]) {
                        v[i] = origin[i] - ent.GetPhysics().GetAbsBounds()[1, i]
                    } else {
                        v[i] = 0.0f
                    }
                    i++
                }
                dist = v.Length()
                if (dist >= radius._val) {
                    e++
                    continue
                }
                if (ent.CanDamage(origin, damagePoint)) {
                    // push the center of mass higher than the origin so players
                    // get knocked into the air more
                    dir.set(ent.GetPhysics().GetOrigin().minus(origin))
                    dir.plusAssign(2, 24.0f)

                    // get the damage scale
                    damageScale = dmgPower * (1.0f - dist / radius._val)
                    if (ent === attacker || ent is idAFAttachment && ent.GetBody() === attacker) {
                        damageScale *= attackerDamageScale._val
                    }
                    ent.Damage(inflictor, attacker, dir, damageDefName, damageScale, Model.INVALID_JOINT)
                }
                e++
            }

            // push physics objects
            if (push._val != 0) {
                RadiusPush(
                    origin,
                    radius._val.toFloat(),
                    push._val * dmgPower,
                    attacker,
                    ignorePush,
                    attackerPushScale._val,
                    false
                )
            }
        }

        fun RadiusPush(
            origin: idVec3,
            radius: Float,
            push: Float,
            inflictor: idEntity?,
            ignore: idEntity?,
            inflictorScale: Float,
            quake: Boolean
        ) {
            var inflictor = inflictor
            var ignore = ignore
            var i: Int
            val numListedClipModels: Int
            var clipModel: idClipModel
            val clipModelList = arrayOfNulls<idClipModel>(MAX_GENTITIES)
            val dir = idVec3()
            val bounds: idBounds
            val result = modelTrace_s()
            var ent: idEntity?
            var scale: Float
            dir.set(0.0f, 0.0f, 1.0f)
            bounds = idBounds(origin).Expand(radius)

            // get all clip models touching the bounds
            numListedClipModels = clip.ClipModelsTouchingBounds(bounds, -1, clipModelList, MAX_GENTITIES)
            if (inflictor != null && inflictor is idAFAttachment) {
                inflictor = inflictor.GetBody()
            }
            if (ignore != null && ignore is idAFAttachment) {
                ignore = ignore.GetBody()
            }

            // apply impact to all the clip models through their associated physics objects
            i = 0
            while (i < numListedClipModels) {
                clipModel = clipModelList[i]!!

                // never push render models
                if (clipModel.IsRenderModel()) {
                    i++
                    continue
                }
                ent = clipModel.GetEntity()

                // never push projectiles
                if (ent is idProjectile) {
                    i++
                    continue
                }

                // players use "knockback" in idPlayer::Damage
                if (ent is idPlayer && !quake) {
                    i++
                    continue
                }

                // don't push the ignore entity
                if (ent === ignore || ent is idAFAttachment && ent.GetBody() === ignore) {
                    i++
                    continue
                }
                if (gameRenderWorld!!.FastWorldTrace(result, origin, clipModel.GetOrigin())) {
                    i++
                    continue
                }

                // scale the push for the inflictor
                scale = if (ent === inflictor || ent is idAFAttachment && ent.GetBody() === inflictor) {
                    inflictorScale
                } else {
                    1.0f
                }
                if (quake) {
                    clipModel.GetEntity()!!
                        .ApplyImpulse(world, clipModel.GetId(), clipModel.GetOrigin(), dir.times(scale * push))
                } else {
                    RadiusPushClipModel(origin, scale * push, clipModel)
                }
                i++
            }
        }

        fun RadiusPushClipModel(origin: idVec3, push: Float, clipModel: idClipModel) {
            var i: Int
            var j: Int
            var dot: Float
            var dist: Float
            var area: Float
            val trm: idTraceModel?
            var poly: traceModelPoly_t?
            val w = idFixedWinding()
            val v = idVec3()
            val localOrigin = idVec3()
            val center = idVec3()
            val impulse = idVec3()
            trm = clipModel.GetTraceModel()
            if (null == trm || true) {
                impulse.set(clipModel.GetAbsBounds().GetCenter().minus(origin))
                impulse.Normalize()
                impulse.z += 1.0f
                clipModel.GetEntity()!!
                    .ApplyImpulse(world, clipModel.GetId(), clipModel.GetOrigin(), impulse.times(push))
                return
            }
            localOrigin.set(origin.minus(clipModel.GetOrigin()).times(clipModel.GetAxis().Transpose()))
            i = 0
            while (i < trm.numPolys) {
                poly = trm.polys[i]
                center.Zero()
                j = 0
                while (j < poly.numEdges) {
                    v.set(trm.verts[trm.edges[abs(poly.edges[j])].v[INTSIGNBITSET(poly.edges[j])]])
                    center.plusAssign(v)
                    v.minusAssign(localOrigin)
                    v.NormalizeFast() // project point on a unit sphere
                    w.AddPoint(v)
                    j++
                }
                center.divAssign(poly.numEdges.toFloat())
                v.set(center.minus(localOrigin))
                dist = v.NormalizeFast()
                dot = v.times(poly.normal)
                if (dot > 0.0f) {
                    i++
                    continue
                }
                area = w.GetArea()
                // impulse in polygon normal direction
                impulse.set(poly.normal.times(clipModel.GetAxis()))
                // always push up for nicer effect
                impulse.z -= 1.0f
                // scale impulse based on visible surface area and polygon angle
                impulse.timesAssign(push * (dot * area * (1.0f / (4.0f * idMath.PI))))
                // scale away distance for nicer effect
                impulse.timesAssign(dist * 2.0f)
                // impulse is applied to the center of the polygon
                center.set(clipModel.GetOrigin().plus(center.times(clipModel.GetAxis())))
                clipModel.GetEntity()!!.ApplyImpulse(world, clipModel.GetId(), center, impulse)
                i++
            }
        }


        fun ProjectDecal(
            origin: idVec3,
            dir: idVec3,
            depth: Float,
            parallel: Boolean,
            size: Float,
            material: String,
            angle: Float = 0.0f /*= 0*/
        ) {
            var size = size
            val s = CFloat()
            val c = CFloat()
            val axis = idMat3()
            val axistemp = idMat3()
            val winding = idFixedWinding()
            val windingOrigin = idVec3()
            val projectionOrigin = idVec3()
            if (!SysCvar.g_decals.GetBool()) {
                return
            }

            // randomly rotate the decal winding
            idMath.SinCos16(if (angle != 0.0f) angle else random.RandomFloat() * idMath.TWO_PI, s, c)

            // winding orientation
            axis[2] = dir
            axis[2].Normalize()
            axis[2].NormalVectors(axistemp[0], axistemp[1])
            axis[0] = axistemp[0].times(c._val).plus(axistemp[1].times(-s._val))
            axis[1] = axistemp[0].times(-s._val).plus(axistemp[1].times(-c._val))
            windingOrigin.set(origin.plus(axis[2].times(depth)))
            if (parallel) {
                projectionOrigin.set(origin.minus(axis[2].times(depth)))
            } else {
                projectionOrigin.set(origin)
            }
            size *= 0.5f
            winding.Clear()
            winding.plusAssign(
                idVec5(
                    windingOrigin.plus(axis.times(decalWinding[0]).times(size)), idVec2(1.0f, 1.0f)
                )
            )
            winding.plusAssign(
                idVec5(
                    windingOrigin.plus(axis.times(decalWinding[1]).times(size)), idVec2(0.0f, 1.0f)
                )
            )
            winding.plusAssign(
                idVec5(
                    windingOrigin.plus(axis.times(decalWinding[2]).times(size)), idVec2(0.0f, 0.0f)
                )
            )
            winding.plusAssign(
                idVec5(
                    windingOrigin.plus(axis.times(decalWinding[3]).times(size)), idVec2(1.0f, 0.0f)
                )
            )
            gameRenderWorld!!.ProjectDecalOntoWorld(
                winding, projectionOrigin, parallel, depth * 0.5f, declManager.FindMaterial(material)!!, time
            )
        }

        fun BloodSplat(origin: idVec3, dir: idVec3, size: Float, material: String) {
            var size = size
            val halfSize = size * 0.5f
            val verts = arrayOf(
                idVec3(0.0f, +halfSize, +halfSize),
                idVec3(0.0f, +halfSize, -halfSize),
                idVec3(0.0f, -halfSize, -halfSize),
                idVec3(0.0f, -halfSize, +halfSize)
            )
            val trm = idTraceModel()
            val mdl = idClipModel()
            val results = trace_s()

            // FIXME: get from damage def
            if (!SysCvar.g_bloodEffects.GetBool()) {
                return
            }
            size = halfSize + random.RandomFloat() * halfSize
            trm.SetupPolygon(verts, 4)
            mdl.LoadModel(trm)
            clip.Translation(
                results,
                origin,
                origin.plus(dir.times(64.0f)),
                mdl,
                idMat3.getMat3_identity(),
                Material.CONTENTS_SOLID,
                null
            )
            ProjectDecal(results.endpos, dir, 2.0f * size, true, size, material)
        }

        fun CallFrameCommand(ent: idEntity, frameCommand: function_t) {
            frameCommandThread!!.CallFunction(ent, frameCommand, true)
            frameCommandThread!!.Execute()
        }

        fun CallObjectFrameCommand(ent: idEntity, frameCommand: String) {
            val func: function_t?
            func = ent.scriptObject.GetFunction(frameCommand)
            if (null == func) {
                if (ent !is idTestModel) {
                    Error("Unknown function '%s' called for frame command on entity '%s'", frameCommand, ent.name)
                }
            } else {
                frameCommandThread!!.CallFunction(ent, func, true)
                frameCommandThread!!.Execute()
            }
        }

        fun GetGravity(): idVec3 {
            return gravity
        }

        // added the following to assist licensees with merge issues
        fun GetFrameNum(): Int {
            return framenum
        }

        fun GetTime(): Int {
            return time
        }

        fun GetMSec(): Int {
            return msec
        }

        fun GetNextClientNum(_current: Int): Int {
            var i: Int
            var current: Int
            current = 0
            i = 0
            while (i < numClients) {
                current = (_current + i + 1) % numClients
                if (entities[current] != null && entities[current] is idPlayer) {
                    return current
                }
                i++
            }
            return current
        }

        fun GetClientByNum(current: Int): idPlayer? {
            var current = current
            if (current < 0 || current >= numClients) {
                current = 0
            }
            return if (entities[current] != null) {
                entities[current] as idPlayer?
            } else null
        }

        fun GetClientByName(name: String): idPlayer? {
            var i: Int
            var ent: idEntity?
            i = 0
            while (i < numClients) {
                ent = entities[i]
                if (ent != null && ent is idPlayer) {
                    if (idStr.IcmpNoColor(name, userInfo[i].GetString("ui_name")) == 0) {
                        return ent
                    }
                }
                i++
            }
            return null
        }

        fun GetClientByCmdArgs(args: CmdArgs.idCmdArgs?): idPlayer? {
            val player: idPlayer?
            val client = idStr(args!!.Argv(1))
            if (0 == client.Length()) {
                return null
            }
            // we don't allow numeric ui_name so this can't go wrong
            player = if (client.IsNumeric()) {
                GetClientByNum(client.toString().toInt())
            } else {
                GetClientByName(client.toString())
            }
            if (null == player) {
                Common.common.Printf("Player '%s' not found\n", client.toString())
            }
            return player
        }

        /*
         ================
         idGameLocal::GetLocalPlayer

         Nothing in the game tic should EVER make a decision based on what the
         local client number is, it shouldn't even be aware that there is a
         draw phase even happening.  This just returns client 0, which will
         be correct for single player.
         ================
         */
        fun GetLocalPlayer(): idPlayer? {
            if (localClientNum < 0) {
                return null
            }
            return if (null == entities[localClientNum] || entities[localClientNum] !is idPlayer) {
                // not fully in game yet
                null
            } else entities[localClientNum] as idPlayer
        }

        fun SpreadLocations() {
            var ent: idEntity?

            // allocate the area table
            val numAreas = gameRenderWorld!!.NumAreas()
            locationEntities = arrayOfNulls(numAreas)
            //	memset( locationEntities, 0, numAreas * sizeof( *locationEntities ) );

            // for each location entity, make pointers from every area it touches
            ent = spawnedEntities.Next()
            while (ent != null) {
                if (ent !is idLocationEntity) {
                    ent = ent.spawnNode.Next()
                    continue
                }
                val point = idVec3(ent.spawnArgs.GetVector("origin"))
                val areaNum = gameRenderWorld!!.PointInArea(point)
                if (areaNum < 0) {
                    Printf("SpreadLocations: location '%s' is not in a valid area\n", ent.spawnArgs.GetString("name"))
                    ent = ent.spawnNode.Next()
                    continue
                }
                if (areaNum >= numAreas) {
                    Error("idGameLocal::SpreadLocations: areaNum >= gameRenderWorld!!.NumAreas()")
                }
                if (locationEntities!![areaNum] != null) {
                    Warning(
                        "location entity '%s' overlaps '%s'",
                        ent.spawnArgs.GetString("name"),
                        locationEntities!![areaNum]!!.spawnArgs.GetString("name")
                    )
                    ent = ent.spawnNode.Next()
                    continue
                }
                locationEntities!![areaNum] = ent

                // spread to all other connected areas
                for (i in 0 until numAreas) {
                    if (i == areaNum) {
                        continue
                    }
                    if (gameRenderWorld!!.AreasAreConnected(
                            areaNum, i, portalConnection_t.PS_BLOCK_LOCATION
                        )
                    ) {
                        locationEntities!![i] = ent
                    }
                }
                ent = ent.spawnNode.Next()
            }
        }

        /*
         ===================
         idGameLocal::LocationForPoint

         The player checks the location each frame to update the HUD text display
         May return NULL
         ===================
         */
        fun LocationForPoint(point: idVec3): idLocationEntity? {
            if (null == locationEntities) {
                // before SpreadLocations() has been called
                return null
            }
            val areaNum = gameRenderWorld!!.PointInArea(point)
            if (areaNum < 0) {
                return null
            }
            if (areaNum >= gameRenderWorld!!.NumAreas()) {
                Error("idGameLocal::LocationForPoint: areaNum >= gameRenderWorld!!.NumAreas()")
            }
            return locationEntities!![areaNum]
        }

        /*
         ===========
         idGameLocal::SelectInitialSpawnPoint
         spectators are spawned randomly anywhere
         in-game clients are spawned based on distance to active players (randomized on the first half)
         upon map restart, initial spawns are used (randomized ordered list of spawns flagged "initial")
         if there are more players than initial spots, overflow to regular spawning
         ============
         */
        fun SelectInitialSpawnPoint(player: idPlayer): idEntity? {
            var i: Int
            var j: Int
            var which: Int
            var spot = spawnSpot_t()
            val pos = idVec3()
            var dist: Float
            var alone: Boolean

            // D3XP CTF: also check team spawn spots
            if (isD3XP) {
                if (!isMultiplayer || spawnSpots.Num() == 0 || (mpGame.IsGametypeFlagBased() && (teamSpawnSpots[0].Num() == 0 || teamSpawnSpots[1].Num() == 0))) {
                    spot.ent = FindEntityUsingDef(null, "info_player_start")
                    if (null == spot.ent) {
                        Error("No info_player_start on map.\n")
                        return null
                    }
                    return spot.ent
                }
            } else {
                if (!isMultiplayer || spawnSpots.Num() == 0) {
                    spot.ent = FindEntityUsingDef(null, "info_player_start")
                    if (null == spot.ent) {
                        Error("No info_player_start on map.\n")
                        return null
                    }
                    return spot.ent
                }
            }

            // D3XP CTF: determine useInitialSpots based on game type
            var useInitialSpots: Boolean
            if (isD3XP && mpGame.IsGametypeFlagBased()) {
                assert(player.team == 0 || player.team == 1)
                useInitialSpots =
                    player.useInitialSpawns && teamCurrentInitialSpot[player.team] < teamInitialSpots[player.team].Num()
            } else {
                useInitialSpots = player.useInitialSpawns && currentInitialSpot < initialSpots.Num()
            }

            if (player.spectating) {
                // plain random spot, don't bother
                return spawnSpots[random.RandomInt(spawnSpots.Num())].ent
            } else if (useInitialSpots) {
                // D3XP CTF: use team initial spots
                if (isD3XP && mpGame.IsGametypeFlagBased()) {
                    assert(player.team == 0 || player.team == 1)
                    player.useInitialSpawns = false // only use the initial spawn once
                    return teamInitialSpots[player.team][teamCurrentInitialSpot[player.team]++]
                }
                return initialSpots[currentInitialSpot++]
            } else {
                // check if we are alone in map
                alone = true
                j = 0
                while (j < MAX_CLIENTS) {
                    if (entities[j] != null && entities[j] != player) {
                        alone = false
                        break
                    }
                    j++
                }
                if (alone) {
                    // D3XP CTF: return random team spawn spot
                    if (isD3XP && mpGame.IsGametypeFlagBased()) {
                        assert(player.team == 0 || player.team == 1)
                        return teamSpawnSpots[player.team][random.RandomInt(teamSpawnSpots[player.team].Num())].ent
                    }
                    // don't do distance-based
                    return spawnSpots[random.RandomInt(spawnSpots.Num())].ent
                }

                // D3XP CTF: distance-based spawn selection per team
                if (isD3XP && mpGame.IsGametypeFlagBased()) {
                    val team = player.team
                    assert(team == 0 || team == 1)

                    // find the distance to the closest active player for each spawn spot
                    i = 0
                    while (i < teamSpawnSpots[team].Num()) {
                        pos.set(teamSpawnSpots[team][i].ent!!.GetPhysics().GetOrigin())

                        // skip initial spawn points for CTF
                        if (teamSpawnSpots[team][i].ent!!.spawnArgs.GetBool("initial")) {
                            teamSpawnSpots[team][i].dist = 0x0
                            i++
                            continue
                        }

                        teamSpawnSpots[team][i].dist = 0x7fffffff

                        j = 0
                        while (j < MAX_CLIENTS) {
                            if (null == entities[j] || entities[j] !is idPlayer || entities[j] == player || (entities[j] as idPlayer).spectating) {
                                j++
                                continue
                            }
                            dist = pos.minus(entities[j]!!.GetPhysics().GetOrigin()).LengthSqr()
                            if (dist < teamSpawnSpots[team][i].dist) {
                                teamSpawnSpots[team][i].dist = dist.toInt()
                            }
                            j++
                        }
                        i++
                    }

                    // sort the list
                    teamSpawnSpots[team].Ptr().sortWith(sortSpawnPoints())

                    // choose a random one in the top half
                    which = random.RandomInt(teamSpawnSpots[team].Num() / 2)
                    spot = teamSpawnSpots[team][which]

                    return spot.ent
                }

                // find the distance to the closest active player for each spawn spot
                i = 0
                while (i < spawnSpots.Num()) {
                    pos.set(spawnSpots[i].ent!!.GetPhysics().GetOrigin())
                    spawnSpots[i].dist = 0x7fffffff
                    j = 0
                    while (j < MAX_CLIENTS) {
                        if (null == entities[j] || entities[j] !is idPlayer || entities[j] == player || (entities[j] as idPlayer).spectating) {
                            j++
                            continue
                        }
                        dist = pos.minus(entities[j]!!.GetPhysics().GetOrigin()).LengthSqr()
                        if (dist < spawnSpots[i].dist) {
                            spawnSpots[i].dist = dist.toInt()
                        }
                        j++
                    }
                    i++
                }

                // sort the list
//                qsort( /*( void * )*/spawnSpots.Ptr(), spawnSpots.Num(), sizeof(spawnSpot_t), /*( int (*)(const void *, const void *) )*/ sortSpawnPoints);

                spawnSpots.Ptr().sortWith(sortSpawnPoints())

                // choose a random one in the top half
                which = random.RandomInt(spawnSpots.Num() / 2)
                spot = spawnSpots[which]
            }
            return spot.ent
        }

        fun SetPortalState(   /*qhandle_t*/portal: Int, blockingBits: Int) {
            val outMsg = idBitMsg()
            val msgBuf = ByteBuffer.allocate(MAX_GAME_MESSAGE_SIZE)
            if (!gameLocal.isClient) {
                outMsg.Init(msgBuf, MAX_GAME_MESSAGE_SIZE)
                outMsg.WriteByte(GAME_RELIABLE_MESSAGE_PORTAL.toByte())
                outMsg.WriteLong(portal)
                outMsg.WriteBits(blockingBits, NUM_RENDER_PORTAL_BITS)
                NetworkSystem.networkSystem.ServerSendReliableMessage(-1, outMsg)
            }
            gameRenderWorld!!.SetPortalState(portal, blockingBits)
        }

        fun SaveEntityNetworkEvent(ent: idEntity, eventId: Int, msg: idBitMsg?) {
            val event: entityNetEvent_s?
            event = savedEventQueue.Alloc()
            event.spawnId = GetSpawnId(ent)
            event.event = eventId
            event.time = time
            if (msg != null) {
                event.paramsSize = msg.GetSize()
                //		memcpy( event.paramsBuf, msg.GetData(), msg.GetSize() );
                System.arraycopy(msg.GetData()!!.array(), 0, event.paramsBuf.array(), 0, msg.GetSize())
            } else {
                event.paramsSize = 0
            }
            savedEventQueue.Enqueue(event, outOfOrderBehaviour_t.OUTOFORDER_IGNORE)
        }

        fun ServerSendChatMessage(to: Int, name: String?, text: String?) {
            val outMsg = idBitMsg()
            val msgBuf = ByteBuffer.allocate(MAX_GAME_MESSAGE_SIZE)
            outMsg.Init(msgBuf, MAX_GAME_MESSAGE_SIZE)
            outMsg.BeginWriting()
            outMsg.WriteByte(GAME_RELIABLE_MESSAGE_CHAT.toByte())
            outMsg.WriteString(name)
            outMsg.WriteString(text, -1, false)
            NetworkSystem.networkSystem.ServerSendReliableMessage(to, outMsg)
            if (to == -1 || to == localClientNum) {
                mpGame.AddChatLine("%s^0: %s\n", name, text)
            }
        }

        fun ServerRemapDecl(clientNum: Int, type: declType_t?, index: Int): Int {

            // only implicit materials and sound shaders decls are used
            if (type != declType_t.DECL_MATERIAL && type != declType_t.DECL_SOUND) {
                return index
            }
            if (clientNum == -1) {
                for (i in 0 until MAX_CLIENTS) {
                    ServerSendDeclRemapToClient(i, type, index)
                }
            } else {
                ServerSendDeclRemapToClient(clientNum, type, index)
            }
            return index
        }

        fun ClientRemapDecl(type: declType_t?, index: Int): Int {

            // only implicit materials and sound shaders decls are used
            if (type != declType_t.DECL_MATERIAL && type != declType_t.DECL_SOUND) {
                return index
            }

            // negative indexes are sometimes used for NULL decls
            if (index < 0) {
                return index
            }

            // make sure the index is valid
            if (clientDeclRemap[localClientNum][type.ordinal]!!.Num() == 0) {
                Error(
                    "client received decl index %d before %s decl remap was initialized",
                    index,
                    declManager.GetDeclNameFromType(type)
                )
                return -1
            }
            if (index >= clientDeclRemap[localClientNum][type.ordinal]!!.Num()) {
                Error(
                    "client received unmapped %s decl index %d from server",
                    declManager.GetDeclNameFromType(type),
                    index
                )
                return -1
            }
            if (clientDeclRemap[localClientNum][type.ordinal]!![index] == -1) {
                Error(
                    "client received unmapped %s decl index %d from server",
                    declManager.GetDeclNameFromType(type),
                    index
                )
                return -1
            }
            return clientDeclRemap[localClientNum][type.ordinal]!![index]
        }

        fun SetGlobalMaterial(mat: Material.idMaterial?) {
            globalMaterial = mat
        }

        fun GetGlobalMaterial(): Material.idMaterial? {
            return globalMaterial
        }

        fun SetGibTime(_time: Int) {
            nextGibTime = _time
        }

        fun GetGibTime(): Int {
            return nextGibTime
        }

        fun NeedRestart(): Boolean {
            val newInfo = idDict()
            var keyval: idKeyValue?
            var keyval2: idKeyValue?
            newInfo.set(CVarSystem.cvarSystem.MoveCVarsToDict(CVarSystem.CVAR_SERVERINFO))
            for (i in 0 until newInfo.GetNumKeyVals()) {
                keyval = newInfo.GetKeyVal(i)!!
                keyval2 = serverInfo.FindKey(keyval.GetKey())
                if (null == keyval2) {
                    return true
                }
                // a select set of si_ changes will cause a full restart of the server
                if (keyval.GetValue().Cmp(keyval2.GetValue().toString()) != 0 && (0 == keyval.GetKey()
                        .Cmp("si_pure") || 0 == keyval.GetKey().Cmp("si_map"))
                ) {
                    return true
                }
            }
            return false
        }

        private fun Clear() {
            var i: Int
            serverInfo.Clear()
            numClients = 0
            i = 0
            while (i < MAX_CLIENTS) {
                userInfo[i].Clear()
                persistentPlayerInfo[i].Clear()
                i++
            }
            //	memset( usercmds, 0, sizeof( usercmds ) );
            for (u in 0 until usercmds.size) {
                usercmds[u] = usercmd_t()
            }
            //	memset( entities, 0, sizeof( entities ) );
            entities.fill(null)
            // FIX: Was creating new array — use fill() to clear in-place and preserve references
            spawnIds.fill(-1) //	memset( spawnIds, -1, sizeof( spawnIds ) );
            firstFreeIndex = 0
            num_entities = 0
            spawnedEntities.Clear()
            activeEntities.Clear()
            numEntitiesToDeactivate = 0
            sortPushers = false
            sortTeamMasters = false
            persistentLevelInfo.Clear()
            // FIX: Was creating new array — use fill() to clear in-place and preserve references
            globalShaderParms.fill(0f) //memset( globalShaderParms, 0, sizeof( globalShaderParms ) );
            random.SetSeed(0)
            world = null
            frameCommandThread = null
            testmodel = null
            testFx = null
            clip.Shutdown()
            pvs.Shutdown()
            sessionCommand.Clear()
            locationEntities = null
            smokeParticles = null
            editEntities = null
            entityHash.Clear(1024, MAX_GENTITIES)
            inCinematic = false
            cinematicSkipTime = 0
            cinematicStopTime = 0
            cinematicMaxSkipTime = 0
            framenum = 0
            previousTime = 0
            time = 0
            msec = UsercmdGen.USERCMD_MSEC
            vacuumAreaNum = 0
            mapFileName.Clear()
            mapFile = null
            spawnCount = INITIAL_SPAWN_COUNT
            mapSpawnCount = 0
            camera = null
            aasList.Clear()
            aasNames.clear()
            lastAIAlertEntity.oSet(null)
            lastAIAlertTime = 0
            spawnArgs.Clear()
            gravity.set(0.0f, 0.0f, -1.0f)
            playerPVS.h =  /*(unsigned int)*/-1
            playerConnectedAreas.h =  /*(unsigned int)*/-1
            gamestate = gameState_t.GAMESTATE_UNINITIALIZED
            skipCinematic = false
            influenceActive = false
            localClientNum = 0
            isMultiplayer = false
            isServer = false
            isClient = false
            realClientTime = 0
            isNewFrame = true
            clientSmoothing = 0.1f
            entityDefBits = 0
            nextGibTime = 0
            globalMaterial = null
            newInfo.Clear()
            lastGUIEnt.oSet(null)
            lastGUI = 0

//	memset( clientEntityStates, 0, sizeof( clientEntityStates ) );
            // FIX: C++ zeroes pointers (= NULL), not creates new objects
            for (a in 0 until MAX_CLIENTS) {
                clientEntityStates[a].fill(null)
            }
            // FIX: Was creating new array — use fill() to clear in-place and preserve references
            for (a in 0 until MAX_CLIENTS) {
                clientPVS[a].fill(0)
            } //memset( clientPVS, 0, sizeof( clientPVS ) );
            //	memset( clientSnapshots, 0, sizeof( clientSnapshots ) );
            // FIX: C++ zeroes pointers (= NULL), not creates new objects
            clientSnapshots.fill(null)
            eventQueue.Init()
            savedEventQueue.Init()
            // FIX: Was creating new array — clear in-place to preserve references
            for (row in lagometer) {
                for (col in row) {
                    col.fill(0)
                }
            } //memset(lagometer, 0, sizeof(lagometer));

            // D3XP: reset portal sky and slow-mo vars
            if (isD3XP) {
                portalSkyEnt.oSet(null)
                portalSkyActive = false
                ResetSlowTimeVars()
            }
        }

        // returns true if the entity shouldn't be spawned at all in this game type or difficulty level
        private fun InhibitEntitySpawn(spawnArgs: idDict): Boolean {
            val result = CBool(false)
            if (isMultiplayer) {
                spawnArgs.GetBool("not_multiplayer", "0", result)
            } else if (SysCvar.g_skill.GetInteger() == 0) {
                spawnArgs.GetBool("not_easy", "0", result)
            } else if (SysCvar.g_skill.GetInteger() == 1) {
                spawnArgs.GetBool("not_medium", "0", result)
            } else {
                spawnArgs.GetBool("not_hard", "0", result)
                if (isD3XP && !result._val && SysCvar.g_skill.GetInteger() == 3) {
                    spawnArgs.GetBool("not_nightmare", "0", result)
                }
            }
            var name: String?
            // DG: dhewm3 removed ID_DEMO_BUILD guard — always inhibit medkits on nightmare
            if (SysCvar.g_skill.GetInteger() == 3) {
                name = spawnArgs.GetString("classname")
                if (idStr.Icmp(name, "item_medkit") == 0 || idStr.Icmp(name, "item_medkit_small") == 0
                    || (isD3XP && (idStr.Icmp(name, "moveable_item_medkit") == 0 || idStr.Icmp(
                        name,
                        "moveable_item_medkit_small"
                    ) == 0))
                ) {
                    result._val = true
                }
            }
            if (gameLocal.isMultiplayer) {
                name = spawnArgs.GetString("classname")
                if (idStr.Icmp(name, "weapon_bfg") == 0 || idStr.Icmp(
                        name, "weapon_soulcube"
                    ) == 0
                ) {
                    result._val = true
                }
            }
            return result._val
        }

        /*
         ==============
         idGameLocal::SpawnMapEntities

         Parses textual entity definitions out of an entstring and spawns gentities.
         ==============
         */
        // spawn entities from the map file
        private fun SpawnMapEntities() {
            var i: Int
            var num: Int
            var inhibit: Int
            var mapEnt: idMapEntity?
            val numEntities: Int
            var args: idDict?
            Printf("Spawning entities\n")
            if (mapFile == null) {
                Printf("No mapfile present\n")
                return
            }
            SetSkill(SysCvar.g_skill.GetInteger())
            numEntities = mapFile!!.GetNumEntities()
            if (numEntities == 0) {
                Error("...no entities")
            }

            // the worldspawn is a special that performs any global setup
            // needed by a level
            mapEnt = mapFile!!.GetEntity(0)
            args = mapEnt.epairs
            args.SetInt("spawn_entnum", ENTITYNUM_WORLD)
            if (!SpawnEntityDef(args) || null == entities[ENTITYNUM_WORLD] || entities[ENTITYNUM_WORLD] !is idWorldspawn) {
                Error("Problem spawning world entity")
            }
            num = 1
            inhibit = 0
            i = 1
            while (i < numEntities) {
                mapEnt = mapFile!!.GetEntity(i)
                args = mapEnt.epairs
                if (!InhibitEntitySpawn(args)) {
                    // precache any media specified in the map entity
                    CacheDictionaryMedia(args)
                    SpawnEntityDef(args)
                    num++
                } else {
                    inhibit++
                }
                i++
            }
            Printf("...%d entities spawned, %d inhibited\n\n", num, inhibit)
        }

        // commons used by init, shutdown, and restart
        private fun MapPopulate() {
            if (isMultiplayer) {
                CVarSystem.cvarSystem.SetCVarBool("r_skipSpecular", false)
            }
            // parse the key/value pairs and spawn entities
            SpawnMapEntities()

            // mark location entities in all connected areas
            SpreadLocations()

            // prepare the list of randomized initial spawn spots
            RandomizeInitialSpawns()

            // spawnCount - 1 is the number of entities spawned into the map, their indexes started at MAX_CLIENTS (included)
            // mapSpawnCount is used as the max index of map entities, it's the first index of non-map entities
            mapSpawnCount = MAX_CLIENTS + spawnCount - 1

            // execute pending events before the very first game frame
            // this makes sure the map script main() function is called
            // before the physics are run so entities can bind correctly
            Printf("==== Processing events ====\n")
            idEvent.ServiceEvents()
        }

        private fun MapClear(clearClients: Boolean) {
            var i: Int
            i = if (clearClients) 0 else MAX_CLIENTS
            while (i < MAX_GENTITIES) {
                if (entities[i] != null) {
                    idEvent.CancelEvents(entities[i]!!)
                    entities[i]!!._deconstructor()
                    entities[i] = null
                }
                assert(entities[i] == null)
                spawnIds[i] = -1
                i++
            }
            entityHash.Clear(1024, MAX_GENTITIES)
            if (!clearClients) {
                // add back the hashes of the clients
                i = 0
                while (i < MAX_CLIENTS) {
                    if (entities[i] == null) {
                        i++
                        continue
                    }
                    entityHash.Add(entityHash.GenerateKey(entities[i]!!.name.toString(), true), i)
                    i++
                }
            }

            if (frameCommandThread != null) {
                idEvent.CancelEvents(frameCommandThread!!)
            }
            frameCommandThread = null
            if (editEntities != null) {
                editEntities = null
            }

            locationEntities = null
        }

        private fun GetClientPVS(player: idPlayer, type: pvsType_t): pvsHandle_t {
            return if (player.GetPrivateCameraView() != null) {
                pvs.SetupCurrentPVS(
                    player.GetPrivateCameraView()!!.GetPVSAreas(), player.GetPrivateCameraView()!!.GetNumPVSAreas()
                )
            } else if (camera != null) {
                pvs.SetupCurrentPVS(camera!!.GetPVSAreas(), camera!!.GetNumPVSAreas())
            } else {
                pvs.SetupCurrentPVS(player.GetPVSAreas(), player.GetNumPVSAreas())
            }
        }

        private fun SetupPlayerPVS() {
            var i: Int
            var ent: idEntity?
            var player: idPlayer?
            var otherPVS: pvsHandle_t
            var newPVS: pvsHandle_t
            playerPVS.i = -1
            i = 0
            while (i < numClients) {
                ent = entities[i]
                if (null == ent || ent !is idPlayer) {
                    i++
                    continue
                }
                player = ent
                if (playerPVS.i == -1) {
                    playerPVS = GetClientPVS(player, pvsType_t.PVS_NORMAL)
                } else {
                    otherPVS = GetClientPVS(player, pvsType_t.PVS_NORMAL)
                    newPVS = pvs.MergeCurrentPVS(playerPVS, otherPVS)
                    pvs.FreeCurrentPVS(playerPVS)
                    pvs.FreeCurrentPVS(otherPVS)
                    playerPVS = newPVS
                }
                if (playerConnectedAreas.i == -1) {
                    playerConnectedAreas = GetClientPVS(player, pvsType_t.PVS_CONNECTED_AREAS)
                } else {
                    otherPVS = GetClientPVS(player, pvsType_t.PVS_CONNECTED_AREAS)
                    newPVS = pvs.MergeCurrentPVS(playerConnectedAreas, otherPVS)
                    pvs.FreeCurrentPVS(playerConnectedAreas)
                    pvs.FreeCurrentPVS(otherPVS)
                    playerConnectedAreas = newPVS
                }
                i++
            }

            // D3XP: if portalSky is present, merge into pvs so we get rotating brushes, etc.
            if (isD3XP) {
                val skyEnt = portalSkyEnt.GetEntity()
                if (skyEnt != null) {
                    otherPVS = pvs.SetupCurrentPVS(skyEnt.GetPVSAreas(), skyEnt.GetNumPVSAreas())
                    newPVS = pvs.MergeCurrentPVS(playerPVS, otherPVS)
                    pvs.FreeCurrentPVS(playerPVS)
                    pvs.FreeCurrentPVS(otherPVS)
                    playerPVS = newPVS

                    otherPVS = pvs.SetupCurrentPVS(skyEnt.GetPVSAreas(), skyEnt.GetNumPVSAreas())
                    newPVS = pvs.MergeCurrentPVS(playerConnectedAreas, otherPVS)
                    pvs.FreeCurrentPVS(playerConnectedAreas)
                    pvs.FreeCurrentPVS(otherPVS)
                    playerConnectedAreas = newPVS
                }
            }
        }

        private fun FreePlayerPVS() {
            if (playerPVS.i != -1) {
                pvs.FreeCurrentPVS(playerPVS)
                playerPVS.i = -1
            }
            if (playerConnectedAreas.i != -1) {
                pvs.FreeCurrentPVS(playerConnectedAreas)
                playerConnectedAreas.i = -1
            }
        }

        private fun UpdateGravity() {
            var ent: idEntity?
            if (SysCvar.g_gravity.IsModified()) {
                if (SysCvar.g_gravity.GetFloat() == 0.0f) {
                    SysCvar.g_gravity.SetFloat(1.0f)
                }
                gravity.set(0.0f, 0.0f, -SysCvar.g_gravity.GetFloat())

                // update all physics objects
                ent = spawnedEntities.Next()
                while (ent != null) {
                    if (ent is idAFEntity_Generic) {
                        val phys = ent.GetPhysics()
                        phys.SetGravity(gravity)
                    }
                    ent = ent.spawnNode.Next()
                }
                SysCvar.g_gravity.ClearModified()
            }
        }

        /*
         ================
         idGameLocal::SortActiveEntityList

         Sorts the active entity list such that pushing entities come first,
         actors come next and physics team slaves appear after their master.
         ================
         */
        private fun SortActiveEntityList() {
            var ent: idEntity?
            var next_ent: idEntity?
            var master: idEntity?
            var part: idEntity?

            // if the active entity list needs to be reordered to place physics team masters at the front
            if (sortTeamMasters) {
                ent = activeEntities.Next()
                while (ent != null) {
                    next_ent = ent.activeNode.Next()
                    master = ent.GetTeamMaster()
                    if (master != null && master === ent) {
                        ent.activeNode.Remove()
                        ent.activeNode.AddToFront(activeEntities)
                    }
                    ent = next_ent
                }
            }

            // if the active entity list needs to be reordered to place pushers at the front
            if (sortPushers) {
                ent = activeEntities.Next()
                while (ent != null) {
                    next_ent = ent.activeNode.Next()
                    master = ent.GetTeamMaster()
                    if (null == master || master === ent) {
                        // check if there is an actor on the team
                        part = ent
                        while (part != null) {
                            if (part.GetPhysics() is idPhysics_Actor) {
                                break
                            }
                            part = part.GetNextTeamEntity()
                        }
                        // if there is an actor on the team
                        if (part != null) {
                            ent.activeNode.Remove()
                            ent.activeNode.AddToFront(activeEntities)
                        }
                    }
                    ent = next_ent
                }
                ent = activeEntities.Next()
                while (ent != null) {
                    next_ent = ent.activeNode.Next()
                    master = ent.GetTeamMaster()
                    if (null == master || master === ent) {
                        // check if there is an entity on the team using parametric physics
                        part = ent
                        while (part != null) {
                            if (part.GetPhysics() is idPhysics_Parametric) {
                                break
                            }
                            part = part.GetNextTeamEntity()
                        }
                        // if there is an entity on the team using parametric physics
                        if (part != null) {
                            ent.activeNode.Remove()
                            ent.activeNode.AddToFront(activeEntities)
                        }
                    }
                    ent = next_ent
                }
            }
            sortTeamMasters = false
            sortPushers = false
        }

        private fun ShowTargets() {
            val axis = GetLocalPlayer()!!.viewAngles.ToMat3()
            val up = idVec3(axis[2].times(5.0f))
            val viewPos = GetLocalPlayer()!!.GetPhysics().GetOrigin()
            val viewTextBounds = idBounds(viewPos)
            val viewBounds = idBounds(viewPos)
            val box = idBounds(idVec3(-4.0f, -4.0f, -4.0f), idVec3(4.0f, 4.0f, 4.0f))
            var ent: idEntity?
            var target: idEntity?
            var i: Int
            val totalBounds = idBounds()
            viewTextBounds.ExpandSelf(128.0f)
            viewBounds.ExpandSelf(512.0f)
            ent = spawnedEntities.Next()
            while (ent != null) {
                totalBounds.set(ent.GetPhysics().GetAbsBounds())
                i = 0
                while (i < ent.targets.Num()) {
                    target = ent.targets[i].GetEntity()
                    if (target != null) {
                        totalBounds.AddBounds(target.GetPhysics().GetAbsBounds())
                    }
                    i++
                }
                if (!viewBounds.IntersectsBounds(totalBounds)) {
                    ent = ent.spawnNode.Next()
                    continue
                }
                val dist = CFloat()
                val dir = idVec3(totalBounds.GetCenter().minus(viewPos))
                dir.NormalizeFast()
                totalBounds.RayIntersection(viewPos, dir, dist)
                val frac = (512.0f - dist._val) / 512.0f
                if (frac < 0.0f) {
                    ent = ent.spawnNode.Next()
                    continue
                }
                gameRenderWorld!!.DebugBounds(
                    (if (ent.IsHidden()) colorLtGrey else colorOrange).times(
                        frac
                    ), ent.GetPhysics().GetAbsBounds()
                )
                if (viewTextBounds.IntersectsBounds(ent.GetPhysics().GetAbsBounds())) {
                    val center = idVec3(ent.GetPhysics().GetAbsBounds().GetCenter())
                    gameRenderWorld!!.DrawText(
                        ent.name.toString(), center.minus(up), 0.1f, colorWhite.times(frac), axis, 1
                    )
                    gameRenderWorld!!.DrawText(
                        ent.GetEntityDefName(), center, 0.1f, colorWhite.times(frac), axis, 1
                    )
                    gameRenderWorld!!.DrawText(
                        Str.va("#%d", ent.entityNumber), center.plus(up), 0.1f, colorWhite.times(frac), axis, 1
                    )
                }
                i = 0
                while (i < ent.targets.Num()) {
                    target = ent.targets[i].GetEntity()
                    if (target != null) {
                        gameRenderWorld!!.DebugArrow(
                            colorYellow.times(frac),
                            ent.GetPhysics().GetAbsBounds().GetCenter(),
                            target.GetPhysics().GetOrigin(),
                            10,
                            0
                        )
                        gameRenderWorld!!.DebugBounds(
                            colorGreen.times(frac), box, target.GetPhysics().GetOrigin()
                        )
                    }
                    i++
                }
                ent = ent.spawnNode.Next()
            }
        }

        private fun RunDebugInfo() {
            var ent: idEntity?
            val player: idPlayer?
            player = GetLocalPlayer()
            if (null == player) {
                return
            }
            val origin = player.GetPhysics().GetOrigin()
            if (SysCvar.g_showEntityInfo.GetBool()) {
                val axis = player.viewAngles.ToMat3()
                val up = idVec3(axis[2].times(5.0f))
                val viewTextBounds = idBounds(origin)
                val viewBounds = idBounds(origin)
                viewTextBounds.ExpandSelf(128.0f)
                viewBounds.ExpandSelf(512.0f)
                ent = spawnedEntities.Next()
                while (ent != null) {

                    // don't draw the worldspawn
                    if (ent === world) {
                        ent = ent.spawnNode.Next()
                        continue
                    }

                    // skip if the entity is very far away
                    if (!viewBounds.IntersectsBounds(ent.GetPhysics().GetAbsBounds())) {
                        ent = ent.spawnNode.Next()
                        continue
                    }
                    val entBounds = ent.GetPhysics().GetAbsBounds()
                    val contents = ent.GetPhysics().GetContents()
                    if ((contents and Material.CONTENTS_BODY) != 0) {
                        gameRenderWorld!!.DebugBounds(colorCyan, entBounds)
                    } else if ((contents and Material.CONTENTS_TRIGGER) != 0) {
                        gameRenderWorld!!.DebugBounds(colorOrange, entBounds)
                    } else if ((contents and Material.CONTENTS_SOLID) != 0) {
                        gameRenderWorld!!.DebugBounds(colorGreen, entBounds)
                    } else {
                        if (0.0f == entBounds.GetVolume()) {
                            gameRenderWorld!!.DebugBounds(colorMdGrey, entBounds.Expand(8.0f))
                        } else {
                            gameRenderWorld!!.DebugBounds(colorMdGrey, entBounds)
                        }
                    }
                    if (viewTextBounds.IntersectsBounds(entBounds)) {
                        gameRenderWorld!!.DrawText(
                            ent.name.toString(), entBounds.GetCenter(), 0.1f, colorWhite, axis, 1
                        )
                        gameRenderWorld!!.DrawText(
                            Str.va("#%d", ent.entityNumber), entBounds.GetCenter().plus(up), 0.1f, colorWhite, axis, 1
                        )
                    }
                    ent = ent.spawnNode.Next()
                }
            }

            // debug tool to draw bounding boxes around active entities
            if (SysCvar.g_showActiveEntities.GetBool()) {
                ent = activeEntities.Next()
                while (ent != null) {
                    val b = ent.GetPhysics().GetBounds()
                    if (b.GetVolume() <= 0) {
                        b[0, 0] = b.set(0, 1, b.set(0, 2, -8.0f))
                        b[1, 0] = b.set(1, 1, b.set(1, 2, 8.0f))
                    }
                    if (ent.fl.isDormant) {
                        gameRenderWorld!!.DebugBounds(
                            colorYellow, b, ent.GetPhysics().GetOrigin()
                        )
                    } else {
                        gameRenderWorld!!.DebugBounds(
                            colorGreen, b, ent.GetPhysics().GetOrigin()
                        )
                    }
                    ent = ent.activeNode.Next()
                }
            }
            if (SysCvar.g_showTargets.GetBool()) {
                ShowTargets()
            }
            if (SysCvar.g_showTriggers.GetBool()) {
                idTrigger.DrawDebugInfo() //TODO:
            }
            if (SysCvar.ai_showCombatNodes.GetBool()) {
                idCombatNode.DrawDebugInfo()
            }
            if (SysCvar.ai_showPaths.GetBool()) {
                idPathCorner.DrawDebugInfo()
            }
            if (SysCvar.g_editEntityMode.GetBool()) {
                editEntities!!.DisplayEntities()
            }
            if (SysCvar.g_showCollisionWorld.GetBool()) {
                collisionModelManager.DrawModel(
                    0, vec3_origin, idMat3.getMat3_identity(), origin, 128.0f
                )
            }
            if (SysCvar.g_showCollisionModels.GetBool()) {
                clip.DrawClipModels(
                    player.GetEyePosition(),
                    SysCvar.g_maxShowDistance.GetFloat(),
                    if (SysCvar.pm_thirdPerson.GetBool()) null else player
                )
            }
            if (SysCvar.g_showCollisionTraces.GetBool()) {
                clip.PrintStatistics()
            }
            if (SysCvar.g_showPVS.GetInteger() != 0) {
                pvs.DrawPVS(
                    origin,
                    if (SysCvar.g_showPVS.GetInteger() == 2) pvsType_t.PVS_ALL_PORTALS_OPEN else pvsType_t.PVS_NORMAL
                )
            }
            if (SysCvar.aas_test.GetInteger() >= 0) {
                val aas = GetAAS(SysCvar.aas_test.GetInteger())
                if (aas != null) {
                    aas.Test(origin)
                    if (SysCvar.ai_testPredictPath.GetBool()) {
                        val velocity = idVec3()
                        val path = predictedPath_s()
                        velocity.x = (cos(DEG2RAD(player.viewAngles.yaw)) * 100.0f)
                        velocity.y = (sin(DEG2RAD(player.viewAngles.yaw)) * 100.0f)
                        velocity.z = 0.0f
                        idAI.PredictPath(
                            player,
                            aas,
                            origin,
                            velocity,
                            1000,
                            100,
                            SE_ENTER_OBSTACLE or SE_BLOCKED or SE_ENTER_LEDGE_AREA,
                            path
                        )
                    }
                }
            }
            if (SysCvar.ai_showObstacleAvoidance.GetInteger() == 2) {
                val aas = GetAAS(0)
                if (aas != null) {
                    val seekPos = idVec3()
                    val path = obstaclePath_s()
                    seekPos.set(player.GetPhysics().GetOrigin().plus(player.viewAxis[0].times(200.0f)))
                    idAI.FindPathAroundObstacles(
                        player.GetPhysics(), aas, null, player.GetPhysics().GetOrigin(), seekPos, path
                    )
                }
            }

            // collision map debug output
            collisionModelManager.DebugOutput(player.GetEyePosition())
        }

        private fun InitScriptForMap() {
            // create a thread to run frame commands on
            frameCommandThread = idThread()
            frameCommandThread!!.ManualDelete()
            frameCommandThread!!.SetThreadName("frameCommands")

            // run the main game script function (not the level specific main)
            val func = program.FindFunction(Game.SCRIPT_DEFAULTFUNC)
            if (func != null) {
                val thread = idThread(func)
                if (thread.Start()) {
                    // thread has finished executing, so delete it
//			delete thread;
                }
            }
        }

        /*
         =================
         idGameLocal::InitConsoleCommands

         Let the system know about all of our commands
         so it can perform tab completion
         =================
         */
        private fun InitConsoleCommands() {
            CmdSystem.cmdSystem.AddCommand(
                "listTypeInfo", ListTypeInfo_f.getInstance(), CmdSystem.CMD_FL_GAME, "list type info"
            )
            CmdSystem.cmdSystem.AddCommand(
                "writeGameState", WriteGameState_f.getInstance(), CmdSystem.CMD_FL_GAME, "write game state"
            )
            CmdSystem.cmdSystem.AddCommand(
                "testSaveGame",
                TestSaveGame_f.getInstance(),
                CmdSystem.CMD_FL_GAME or CmdSystem.CMD_FL_CHEAT,
                "test a save game for a level"
            )
            CmdSystem.cmdSystem.AddCommand(
                "game_memory", DisplayInfo_f.getInstance(), CmdSystem.CMD_FL_GAME, "displays game class info"
            )
            CmdSystem.cmdSystem.AddCommand(
                "listClasses", ListClasses_f.getInstance(), CmdSystem.CMD_FL_GAME, "lists game classes"
            )
            CmdSystem.cmdSystem.AddCommand(
                "listThreads",
                ListThreads_f.instance,
                CmdSystem.CMD_FL_GAME or CmdSystem.CMD_FL_CHEAT,
                "lists script threads"
            )
            CmdSystem.cmdSystem.AddCommand(
                "listEntities",
                Cmd_EntityList_f.getInstance(),
                CmdSystem.CMD_FL_GAME or CmdSystem.CMD_FL_CHEAT,
                "lists game entities"
            )
            CmdSystem.cmdSystem.AddCommand(
                "listActiveEntities",
                Cmd_ActiveEntityList_f.getInstance(),
                CmdSystem.CMD_FL_GAME or CmdSystem.CMD_FL_CHEAT,
                "lists active game entities"
            )
            CmdSystem.cmdSystem.AddCommand(
                "listMonsters",
                idAI.List_f.getInstance(),
                CmdSystem.CMD_FL_GAME or CmdSystem.CMD_FL_CHEAT,
                "lists monsters"
            )
            CmdSystem.cmdSystem.AddCommand(
                "listSpawnArgs",
                Cmd_ListSpawnArgs_f.getInstance(),
                CmdSystem.CMD_FL_GAME or CmdSystem.CMD_FL_CHEAT,
                "list the spawn args of an entity",
                ArgCompletion_EntityName.getInstance()
            )
            CmdSystem.cmdSystem.AddCommand("say", Cmd_Say_f.getInstance(), CmdSystem.CMD_FL_GAME, "text chat")
            CmdSystem.cmdSystem.AddCommand(
                "sayTeam", Cmd_SayTeam_f.getInstance(), CmdSystem.CMD_FL_GAME, "team text chat"
            )
            CmdSystem.cmdSystem.AddCommand(
                "addChatLine",
                Cmd_AddChatLine_f.getInstance(),
                CmdSystem.CMD_FL_GAME,
                "internal use - core to game chat lines"
            )
            CmdSystem.cmdSystem.AddCommand(
                "gameKick", Cmd_Kick_f.getInstance(), CmdSystem.CMD_FL_GAME, "same as kick, but recognizes player names"
            )
            CmdSystem.cmdSystem.AddCommand(
                "give",
                Cmd_Give_f.getInstance(),
                CmdSystem.CMD_FL_GAME or CmdSystem.CMD_FL_CHEAT,
                "gives one or more items"
            )
            CmdSystem.cmdSystem.AddCommand(
                "centerview", Cmd_CenterView_f.getInstance(), CmdSystem.CMD_FL_GAME, "centers the view"
            )
            CmdSystem.cmdSystem.AddCommand(
                "god", Cmd_God_f.getInstance(), CmdSystem.CMD_FL_GAME or CmdSystem.CMD_FL_CHEAT, "enables god mode"
            )
            CmdSystem.cmdSystem.AddCommand(
                "notarget",
                Cmd_Notarget_f.getInstance(),
                CmdSystem.CMD_FL_GAME or CmdSystem.CMD_FL_CHEAT,
                "disables the player as a target"
            )
            CmdSystem.cmdSystem.AddCommand(
                "noclip",
                Cmd_Noclip_f.getInstance(),
                CmdSystem.CMD_FL_GAME or CmdSystem.CMD_FL_CHEAT,
                "disables collision detection for the player"
            )
            CmdSystem.cmdSystem.AddCommand(
                "kill", Cmd_Kill_f.getInstance(), CmdSystem.CMD_FL_GAME, "kills the player"
            )
            CmdSystem.cmdSystem.AddCommand(
                "where",
                Cmd_GetViewpos_f.getInstance(),
                CmdSystem.CMD_FL_GAME or CmdSystem.CMD_FL_CHEAT,
                "prints the current view position"
            )
            CmdSystem.cmdSystem.AddCommand(
                "getviewpos",
                Cmd_GetViewpos_f.getInstance(),
                CmdSystem.CMD_FL_GAME or CmdSystem.CMD_FL_CHEAT,
                "prints the current view position"
            )
            CmdSystem.cmdSystem.AddCommand(
                "setviewpos",
                Cmd_SetViewpos_f.getInstance(),
                CmdSystem.CMD_FL_GAME or CmdSystem.CMD_FL_CHEAT,
                "sets the current view position"
            )
            CmdSystem.cmdSystem.AddCommand(
                "teleport",
                Cmd_Teleport_f.getInstance(),
                CmdSystem.CMD_FL_GAME or CmdSystem.CMD_FL_CHEAT,
                "teleports the player to an entity location",
                ArgCompletion_EntityName.getInstance()
            )
            CmdSystem.cmdSystem.AddCommand(
                "trigger",
                Cmd_Trigger_f.getInstance(),
                CmdSystem.CMD_FL_GAME or CmdSystem.CMD_FL_CHEAT,
                "triggers an entity",
                ArgCompletion_EntityName.getInstance()
            )
            CmdSystem.cmdSystem.AddCommand(
                "spawn",
                Cmd_Spawn_f.getInstance(),
                CmdSystem.CMD_FL_GAME or CmdSystem.CMD_FL_CHEAT,
                "spawns a game entity",
                ArgCompletion_Decl(declType_t.DECL_ENTITYDEF)
            )
            CmdSystem.cmdSystem.AddCommand(
                "damage",
                Cmd_Damage_f.getInstance(),
                CmdSystem.CMD_FL_GAME or CmdSystem.CMD_FL_CHEAT,
                "apply damage to an entity",
                ArgCompletion_EntityName.getInstance()
            )
            CmdSystem.cmdSystem.AddCommand(
                "remove",
                Cmd_Remove_f.getInstance(),
                CmdSystem.CMD_FL_GAME or CmdSystem.CMD_FL_CHEAT,
                "removes an entity",
                ArgCompletion_EntityName.getInstance()
            )
            CmdSystem.cmdSystem.AddCommand(
                "killMonsters",
                Cmd_KillMonsters_f.getInstance(),
                CmdSystem.CMD_FL_GAME or CmdSystem.CMD_FL_CHEAT,
                "removes all monsters"
            )
            CmdSystem.cmdSystem.AddCommand(
                "killMoveables",
                Cmd_KillMovables_f.getInstance(),
                CmdSystem.CMD_FL_GAME or CmdSystem.CMD_FL_CHEAT,
                "removes all moveables"
            )
            CmdSystem.cmdSystem.AddCommand(
                "killRagdolls",
                Cmd_KillRagdolls_f.getInstance(),
                CmdSystem.CMD_FL_GAME or CmdSystem.CMD_FL_CHEAT,
                "removes all ragdolls"
            )
            CmdSystem.cmdSystem.AddCommand(
                "addline",
                Cmd_AddDebugLine_f.getInstance(),
                CmdSystem.CMD_FL_GAME or CmdSystem.CMD_FL_CHEAT,
                "adds a debug line"
            )
            CmdSystem.cmdSystem.AddCommand(
                "addarrow",
                Cmd_AddDebugLine_f.getInstance(),
                CmdSystem.CMD_FL_GAME or CmdSystem.CMD_FL_CHEAT,
                "adds a debug arrow"
            )
            CmdSystem.cmdSystem.AddCommand(
                "removeline",
                Cmd_RemoveDebugLine_f.getInstance(),
                CmdSystem.CMD_FL_GAME or CmdSystem.CMD_FL_CHEAT,
                "removes a debug line"
            )
            CmdSystem.cmdSystem.AddCommand(
                "blinkline",
                Cmd_BlinkDebugLine_f.getInstance(),
                CmdSystem.CMD_FL_GAME or CmdSystem.CMD_FL_CHEAT,
                "blinks a debug line"
            )
            CmdSystem.cmdSystem.AddCommand(
                "listLines",
                Cmd_ListDebugLines_f.getInstance(),
                CmdSystem.CMD_FL_GAME or CmdSystem.CMD_FL_CHEAT,
                "lists all debug lines"
            )
            CmdSystem.cmdSystem.AddCommand(
                "playerModel",
                Cmd_PlayerModel_f.getInstance(),
                CmdSystem.CMD_FL_GAME or CmdSystem.CMD_FL_CHEAT,
                "sets the given model on the player",
                ArgCompletion_Decl(declType_t.DECL_MODELDEF)
            )
            CmdSystem.cmdSystem.AddCommand(
                "testFx",
                Cmd_TestFx_f.getInstance(),
                CmdSystem.CMD_FL_GAME or CmdSystem.CMD_FL_CHEAT,
                "tests an FX system",
                ArgCompletion_Decl(declType_t.DECL_FX)
            )
            CmdSystem.cmdSystem.AddCommand(
                "testBoneFx",
                Cmd_TestBoneFx_f.getInstance(),
                CmdSystem.CMD_FL_GAME or CmdSystem.CMD_FL_CHEAT,
                "tests an FX system bound to a joint",
                ArgCompletion_Decl(declType_t.DECL_FX)
            )
            CmdSystem.cmdSystem.AddCommand(
                "testLight",
                Cmd_TestLight_f.getInstance(),
                CmdSystem.CMD_FL_GAME or CmdSystem.CMD_FL_CHEAT,
                "tests a light"
            )
            CmdSystem.cmdSystem.AddCommand(
                "testPointLight",
                Cmd_TestPointLight_f.getInstance(),
                CmdSystem.CMD_FL_GAME or CmdSystem.CMD_FL_CHEAT,
                "tests a point light"
            )
            CmdSystem.cmdSystem.AddCommand(
                "popLight",
                Cmd_PopLight_f.getInstance(),
                CmdSystem.CMD_FL_GAME or CmdSystem.CMD_FL_CHEAT,
                "removes the last created light"
            )
            CmdSystem.cmdSystem.AddCommand(
                "testDeath",
                Cmd_TestDeath_f.getInstance(),
                CmdSystem.CMD_FL_GAME or CmdSystem.CMD_FL_CHEAT,
                "tests death"
            )
            CmdSystem.cmdSystem.AddCommand(
                "testSave",
                Cmd_TestSave_f.getInstance(),
                CmdSystem.CMD_FL_GAME or CmdSystem.CMD_FL_CHEAT,
                "writes out a test savegame"
            )
            CmdSystem.cmdSystem.AddCommand(
                "testModel",
                TestModel_f.getInstance(),
                CmdSystem.CMD_FL_GAME or CmdSystem.CMD_FL_CHEAT,
                "tests a model",
                ArgCompletion_TestModel.getInstance()
            )
            CmdSystem.cmdSystem.AddCommand(
                "testSkin",
                TestSkin_f.getInstance(),
                CmdSystem.CMD_FL_GAME or CmdSystem.CMD_FL_CHEAT,
                "tests a skin on an existing testModel",
                ArgCompletion_Decl(declType_t.DECL_SKIN)
            )
            CmdSystem.cmdSystem.AddCommand(
                "testShaderParm",
                TestShaderParm_f.getInstance(),
                CmdSystem.CMD_FL_GAME or CmdSystem.CMD_FL_CHEAT,
                "sets a shaderParm on an existing testModel"
            )
            CmdSystem.cmdSystem.AddCommand(
                "keepTestModel",
                KeepTestModel_f.getInstance(),
                CmdSystem.CMD_FL_GAME or CmdSystem.CMD_FL_CHEAT,
                "keeps the last test model in the game"
            )
            CmdSystem.cmdSystem.AddCommand(
                "testAnim",
                TestAnim_f.getInstance(),
                CmdSystem.CMD_FL_GAME or CmdSystem.CMD_FL_CHEAT,
                "tests an animation",
                ArgCompletion_TestAnim.getInstance()
            )
            CmdSystem.cmdSystem.AddCommand(
                "testParticleStopTime",
                TestParticleStopTime_f.getInstance(),
                CmdSystem.CMD_FL_GAME or CmdSystem.CMD_FL_CHEAT,
                "tests particle stop time on a test model"
            )
            CmdSystem.cmdSystem.AddCommand(
                "nextAnim",
                TestModelNextAnim_f.getInstance(),
                CmdSystem.CMD_FL_GAME or CmdSystem.CMD_FL_CHEAT,
                "shows next animation on test model"
            )
            CmdSystem.cmdSystem.AddCommand(
                "prevAnim",
                TestModelPrevAnim_f.getInstance(),
                CmdSystem.CMD_FL_GAME or CmdSystem.CMD_FL_CHEAT,
                "shows previous animation on test model"
            )
            CmdSystem.cmdSystem.AddCommand(
                "nextFrame",
                TestModelNextFrame_f.getInstance(),
                CmdSystem.CMD_FL_GAME or CmdSystem.CMD_FL_CHEAT,
                "shows next animation frame on test model"
            )
            CmdSystem.cmdSystem.AddCommand(
                "prevFrame",
                TestModelPrevFrame_f.getInstance(),
                CmdSystem.CMD_FL_GAME or CmdSystem.CMD_FL_CHEAT,
                "shows previous animation frame on test model"
            )
            CmdSystem.cmdSystem.AddCommand(
                "testBlend",
                TestBlend_f.getInstance(),
                CmdSystem.CMD_FL_GAME or CmdSystem.CMD_FL_CHEAT,
                "tests animation blending"
            )
            CmdSystem.cmdSystem.AddCommand(
                "reloadScript",
                Cmd_ReloadScript_f.getInstance(),
                CmdSystem.CMD_FL_GAME or CmdSystem.CMD_FL_CHEAT,
                "reloads scripts"
            )
            CmdSystem.cmdSystem.AddCommand(
                "script",
                Cmd_Script_f.getInstance(),
                CmdSystem.CMD_FL_GAME or CmdSystem.CMD_FL_CHEAT,
                "executes a line of script"
            )
            CmdSystem.cmdSystem.AddCommand(
                "listCollisionModels",
                Cmd_ListCollisionModels_f.getInstance(),
                CmdSystem.CMD_FL_GAME,
                "lists collision models"
            )
            CmdSystem.cmdSystem.AddCommand(
                "collisionModelInfo",
                Cmd_CollisionModelInfo_f.getInstance(),
                CmdSystem.CMD_FL_GAME,
                "shows collision model info"
            )
            CmdSystem.cmdSystem.AddCommand(
                "reexportmodels",
                Cmd_ReexportModels_f.getInstance(),
                CmdSystem.CMD_FL_GAME or CmdSystem.CMD_FL_CHEAT,
                "reexports models",
                ArgCompletion_DefFile.getInstance()
            )
            CmdSystem.cmdSystem.AddCommand(
                "reloadanims",
                Cmd_ReloadAnims_f.getInstance(),
                CmdSystem.CMD_FL_GAME or CmdSystem.CMD_FL_CHEAT,
                "reloads animations"
            )
            CmdSystem.cmdSystem.AddCommand(
                "listAnims", Cmd_ListAnims_f.getInstance(), CmdSystem.CMD_FL_GAME, "lists all animations"
            )
            CmdSystem.cmdSystem.AddCommand(
                "aasStats", Cmd_AASStats_f.getInstance(), CmdSystem.CMD_FL_GAME, "shows AAS stats"
            )
            CmdSystem.cmdSystem.AddCommand(
                "testDamage",
                Cmd_TestDamage_f.getInstance(),
                CmdSystem.CMD_FL_GAME or CmdSystem.CMD_FL_CHEAT,
                "tests a damage def",
                ArgCompletion_Decl(declType_t.DECL_ENTITYDEF)
            )
            CmdSystem.cmdSystem.AddCommand(
                "weaponSplat",
                Cmd_WeaponSplat_f.getInstance(),
                CmdSystem.CMD_FL_GAME or CmdSystem.CMD_FL_CHEAT,
                "projects a blood splat on the player weapon"
            )
            CmdSystem.cmdSystem.AddCommand(
                "saveSelected",
                Cmd_SaveSelected_f.getInstance(),
                CmdSystem.CMD_FL_GAME or CmdSystem.CMD_FL_CHEAT,
                "saves the selected entity to the .map file"
            )
            CmdSystem.cmdSystem.AddCommand(
                "deleteSelected",
                Cmd_DeleteSelected_f.getInstance(),
                CmdSystem.CMD_FL_GAME or CmdSystem.CMD_FL_CHEAT,
                "deletes selected entity"
            )
            CmdSystem.cmdSystem.AddCommand(
                "saveMoveables",
                Cmd_SaveMoveables_f.getInstance(),
                CmdSystem.CMD_FL_GAME or CmdSystem.CMD_FL_CHEAT,
                "save all moveables to the .map file"
            )
            CmdSystem.cmdSystem.AddCommand(
                "saveRagdolls",
                Cmd_SaveRagdolls_f.getInstance(),
                CmdSystem.CMD_FL_GAME or CmdSystem.CMD_FL_CHEAT,
                "save all ragdoll poses to the .map file"
            )
            CmdSystem.cmdSystem.AddCommand(
                "bindRagdoll",
                Cmd_BindRagdoll_f.getInstance(),
                CmdSystem.CMD_FL_GAME or CmdSystem.CMD_FL_CHEAT,
                "binds ragdoll at the current drag position"
            )
            CmdSystem.cmdSystem.AddCommand(
                "unbindRagdoll",
                Cmd_UnbindRagdoll_f.getInstance(),
                CmdSystem.CMD_FL_GAME or CmdSystem.CMD_FL_CHEAT,
                "unbinds the selected ragdoll"
            )
            CmdSystem.cmdSystem.AddCommand(
                "saveLights",
                Cmd_SaveLights_f.getInstance(),
                CmdSystem.CMD_FL_GAME or CmdSystem.CMD_FL_CHEAT,
                "saves all lights to the .map file"
            )
            CmdSystem.cmdSystem.AddCommand(
                "saveParticles",
                Cmd_SaveParticles_f.getInstance(),
                CmdSystem.CMD_FL_GAME or CmdSystem.CMD_FL_CHEAT,
                "saves all lights to the .map file"
            )
            CmdSystem.cmdSystem.AddCommand(
                "clearLights",
                Cmd_ClearLights_f.getInstance(),
                CmdSystem.CMD_FL_GAME or CmdSystem.CMD_FL_CHEAT,
                "clears all lights"
            )
            CmdSystem.cmdSystem.AddCommand(
                "gameError",
                Cmd_GameError_f.getInstance(),
                CmdSystem.CMD_FL_GAME or CmdSystem.CMD_FL_CHEAT,
                "causes a game error"
            )
            if (!ID_DEMO_BUILD) { //#ifndef
                CmdSystem.cmdSystem.AddCommand(
                    "disasmScript",
                    Cmd_DisasmScript_f.getInstance(),
                    CmdSystem.CMD_FL_GAME or CmdSystem.CMD_FL_CHEAT,
                    "disassembles script"
                )
                CmdSystem.cmdSystem.AddCommand(
                    "recordViewNotes",
                    Cmd_RecordViewNotes_f.getInstance(),
                    CmdSystem.CMD_FL_GAME or CmdSystem.CMD_FL_CHEAT,
                    "record the current view position with notes"
                )
                CmdSystem.cmdSystem.AddCommand(
                    "showViewNotes",
                    Cmd_ShowViewNotes_f.getInstance(),
                    CmdSystem.CMD_FL_GAME or CmdSystem.CMD_FL_CHEAT,
                    "show any view notes for the current map, successive calls will cycle to the next note"
                )
                CmdSystem.cmdSystem.AddCommand(
                    "closeViewNotes",
                    Cmd_CloseViewNotes_f.getInstance(),
                    CmdSystem.CMD_FL_GAME or CmdSystem.CMD_FL_CHEAT,
                    "close the view showing any notes for this map"
                )
                CmdSystem.cmdSystem.AddCommand(
                    "exportmodels",
                    Cmd_ExportModels_f.getInstance(),
                    CmdSystem.CMD_FL_GAME or CmdSystem.CMD_FL_CHEAT,
                    "exports models",
                    ArgCompletion_DefFile.getInstance()
                )

                // multiplayer client commands ( replaces old impulses stuff )
                CmdSystem.cmdSystem.AddCommand(
                    "clientDropWeapon", DropWeapon_f.getInstance(), CmdSystem.CMD_FL_GAME, "drop current weapon"
                )
                CmdSystem.cmdSystem.AddCommand(
                    "clientMessageMode", MessageMode_f.getInstance(), CmdSystem.CMD_FL_GAME, "ingame gui message mode"
                )
                // FIXME: implement
//	cmdSystem.AddCommand( "clientVote",			idMultiplayerGame.Vote_f.getInstance(),	CMD_FL_GAME,				"cast your vote: clientVote yes | no" );
//	cmdSystem.AddCommand( "clientCallVote",		idMultiplayerGame.CallVote_f.getInstance(),	CMD_FL_GAME,			"call a vote: clientCallVote si_.. proposed_value" );
                CmdSystem.cmdSystem.AddCommand(
                    "clientVoiceChat",
                    VoiceChat_f.getInstance(),
                    CmdSystem.CMD_FL_GAME,
                    "voice chats: clientVoiceChat <sound shader>"
                )
                CmdSystem.cmdSystem.AddCommand(
                    "clientVoiceChatTeam",
                    VoiceChatTeam_f.getInstance(),
                    CmdSystem.CMD_FL_GAME,
                    "team voice chats: clientVoiceChat <sound shader>"
                )

                // multiplayer server commands
                CmdSystem.cmdSystem.AddCommand(
                    "serverMapRestart", MapRestart_f.getInstance(), CmdSystem.CMD_FL_GAME, "restart the current game"
                )
                CmdSystem.cmdSystem.AddCommand(
                    "serverForceReady", ForceReady_f.getInstance(), CmdSystem.CMD_FL_GAME, "force all players ready"
                )
                CmdSystem.cmdSystem.AddCommand(
                    "serverNextMap", NextMap_f.getInstance(), CmdSystem.CMD_FL_GAME, "change to the next map"
                )
            }

            // localization help commands
            CmdSystem.cmdSystem.AddCommand(
                "nextGUI",
                Cmd_NextGUI_f.getInstance(),
                CmdSystem.CMD_FL_GAME or CmdSystem.CMD_FL_CHEAT,
                "teleport the player to the next func_static with a gui"
            )
            CmdSystem.cmdSystem.AddCommand(
                "testid",
                Cmd_TestId_f.getInstance(),
                CmdSystem.CMD_FL_GAME or CmdSystem.CMD_FL_CHEAT,
                "output the string for the specified id."
            )
            // D3XP
            if (isD3XP) {
                CmdSystem.cmdSystem.AddCommand(
                    "setActorState",
                    SysCmds.Cmd_SetActorState_f.getInstance(),
                    CmdSystem.CMD_FL_GAME or CmdSystem.CMD_FL_CHEAT,
                    "Manually sets an actors script state"
                )
            }
        }

        private fun ShutdownConsoleCommands() {
            CmdSystem.cmdSystem.RemoveFlaggedCommands(CmdSystem.CMD_FL_GAME.toInt())
        }

        private fun InitAsyncNetwork() {
            var i: Int
            var type: Int
            i = 0
            while (i < MAX_CLIENTS) {
                type = 0
                while (type < declManager.GetNumDeclTypes()) {
                    clientDeclRemap[i][type] = idList()
                    type++
                }
                i++
            }

//	memset( clientEntityStates, 0, sizeof( clientEntityStates ) );
            clientEntityStates = Array(clientEntityStates.size) { arrayOfNulls(clientEntityStates[0].size) }
            //	memset( clientPVS, 0, sizeof( clientPVS ) );
            clientPVS = Array(clientPVS.size) { IntArray(clientPVS[0].size) }
            //	memset( clientSnapshots, 0, sizeof( clientSnapshots ) );
            clientSnapshots = arrayOfNulls(clientSnapshots.size)
            eventQueue.Init()
            savedEventQueue.Init()
            entityDefBits = -(idMath.BitsForInteger(declManager.GetNumDecls(declType_t.DECL_ENTITYDEF)) + 1)
            localClientNum = 0 // on a listen server SetLocalUser will set this right
            realClientTime = 0
            isNewFrame = true
            clientSmoothing = Game_network.net_clientSmoothing.GetFloat()
        }

        private fun ShutdownAsyncNetwork() {
//            entityStateAllocator.Shutdown();
//            snapshotAllocator.Shutdown();
            eventQueue.Shutdown()
            savedEventQueue.Shutdown()
            //	memset( clientEntityStates, 0, sizeof( clientEntityStates ) );
            clientEntityStates = Array(clientEntityStates.size) { arrayOfNulls(clientEntityStates[0].size) }
            //	memset( clientPVS, 0, sizeof( clientPVS ) );
            clientPVS = Array(clientPVS.size) { IntArray(clientPVS[0].size) }
            //	memset( clientSnapshots, 0, sizeof( clientSnapshots ) );
            clientSnapshots = arrayOfNulls(clientSnapshots.size)
        }

        private fun InitLocalClient(clientNum: Int) {
            isServer = false
            isClient = true
            localClientNum = clientNum
            clientSmoothing = Game_network.net_clientSmoothing.GetFloat()
        }

        private fun InitClientDeclRemap(clientNum: Int) {
            var type: Int
            var i: Int
            var num: Int
            type = 0
            while (type < declManager.GetNumDeclTypes()) {


                // only implicit materials and sound shaders decls are used
                if (type != etoi(declType_t.DECL_MATERIAL) && type != etoi(declType_t.DECL_SOUND)) {
                    type++
                    continue
                }
                num = declManager.GetNumDecls(type)
                clientDeclRemap[clientNum][type]!!.Clear()
                clientDeclRemap[clientNum][type]!!.AssureSize(num, -1)

                // pre-initialize the remap with non-implicit decls, all non-implicit decls are always going
                // to be in order and in sync between server and client because of the decl manager checksum
                i = 0
                while (i < num) {
                    val decl = declManager.DeclByIndex(declType_t.entries[type], i, false)!!
                    if (decl.IsImplicit()) {
                        // once the first implicit decl is found all remaining decls are considered implicit as well
                        break
                    }
                    clientDeclRemap[clientNum][type]!![i] = i
                    i++
                }
                type++
            }
        }

        private fun ServerSendDeclRemapToClient(clientNum: Int, type: declType_t, index: Int) {
            val outMsg = idBitMsg()
            val msgBuf = ByteBuffer.allocate(MAX_GAME_MESSAGE_SIZE)

            // if no client connected for this spot
            if (entities[clientNum] == null) {
                return
            }
            // increase size of list if required
            if (index >= clientDeclRemap[clientNum][type.ordinal]!!.Num()) {
                clientDeclRemap[clientNum][type.ordinal]!!.AssureSize(index + 1, -1)
            }
            // if already remapped
            if (clientDeclRemap[clientNum][type.ordinal]!![index] != -1) {
                return
            }
            val decl = declManager.DeclByIndex(type, index, false)
            if (decl == null) {
                Error(
                    "server tried to remap bad %s decl index %d", declManager.GetDeclNameFromType(type), index
                )
                return
            }

            // set the index at the server
            clientDeclRemap[clientNum][type.ordinal]!![index] = index

            // write update to client
            outMsg.Init(msgBuf, MAX_GAME_MESSAGE_SIZE)
            outMsg.BeginWriting()
            outMsg.WriteByte(GAME_RELIABLE_MESSAGE_REMAP_DECL.toByte())
            outMsg.WriteByte(etoi(type).toByte())
            outMsg.WriteLong(index)
            outMsg.WriteString(decl.GetName())
            NetworkSystem.networkSystem.ServerSendReliableMessage(clientNum, outMsg)
        }

        private fun FreeSnapshotsOlderThanSequence(clientNum: Int, sequence: Int) {
            var snapshot: snapshot_s?
            var lastSnapshot: snapshot_s?
            var nextSnapshot: snapshot_s?
            var state: entityState_s?
            lastSnapshot = null
            snapshot = clientSnapshots[clientNum]
            while (snapshot != null) {
                nextSnapshot = snapshot.next
                if (snapshot.sequence < sequence) {
                    state = snapshot.firstEntityState
                    while (state != null) {
                        snapshot.firstEntityState = snapshot.firstEntityState!!.next
                        state = snapshot.firstEntityState
                    }
                    if (lastSnapshot != null) {
                        lastSnapshot.next = snapshot.next
                    } else {
                        clientSnapshots[clientNum] = snapshot.next
                    }
                    //                    snapshotAllocator.Free(snapshot);
                } else {
                    lastSnapshot = snapshot
                }
                snapshot = nextSnapshot
            }
        }

        private fun ApplySnapshot(clientNum: Int, sequence: Int): Boolean {
            var snapshot: snapshot_s?
            var lastSnapshot: snapshot_s?
            var nextSnapshot: snapshot_s?
            var state: entityState_s?
            FreeSnapshotsOlderThanSequence(clientNum, sequence)
            lastSnapshot = null
            snapshot = clientSnapshots[clientNum]
            while (snapshot != null) {
                nextSnapshot = snapshot.next
                if (snapshot.sequence == sequence) {
                    state = snapshot.firstEntityState
                    while (state != null) {
                        if (clientEntityStates[clientNum][state.entityNumber] != null) {
//                            entityStateAllocator.Free(clientEntityStates[clientNum][state.entityNumber]);
                        }
                        clientEntityStates[clientNum][state.entityNumber] = state
                        state = state.next
                    }
                    //			memcpy( clientPVS[clientNum], snapshot.pvs, sizeof( snapshot.pvs ) );
                    System.arraycopy(snapshot.pvs, 0, clientPVS[clientNum], 0, snapshot.pvs.size)
                    if (lastSnapshot != null) {
                        lastSnapshot.next = nextSnapshot
                    } else {
                        clientSnapshots[clientNum] = nextSnapshot
                    }
                    //                    snapshotAllocator.Free(snapshot);
                    return true
                } else {
                    lastSnapshot = snapshot
                }
                snapshot = nextSnapshot
            }
            return false
        }

        private fun WriteGameStateToSnapshot(msg: idBitMsgDelta) {
            var i: Int
            i = 0
            while (i < RenderWorld.MAX_GLOBAL_SHADER_PARMS) {
                msg.WriteFloat(globalShaderParms[i])
                i++
            }
            mpGame.WriteToSnapshot(msg)
        }

        private fun ReadGameStateFromSnapshot(msg: idBitMsgDelta) {
            var i: Int
            i = 0
            while (i < RenderWorld.MAX_GLOBAL_SHADER_PARMS) {
                globalShaderParms[i] = msg.ReadFloat()
                i++
            }
            mpGame.ReadFromSnapshot(msg)
        }

        private fun NetworkEventWarning(
            event: entityNetEvent_s?, vararg fmt: String?
        ) {
            if (event == null) return
            val entityNum = event.spawnId and ((1 shl GENTITYNUM_BITS) - 1)
            val id = event.spawnId shr GENTITYNUM_BITS
            val msg = "event ${event.event} for entity $entityNum $id: ${fmt.joinToString("")}"
            Common.common.DWarning("%s", msg)
        }

        private fun ServerProcessEntityNetworkEventQueue() {
            var ent: idEntity
            var event: entityNetEvent_s?
            val eventMsg = idBitMsg()
            while (eventQueue.Start() != null) {
                event = eventQueue.Start()!!
                if (event.time > time) {
                    break
                }
                val entPtr = idEntityPtr<idEntity?>()
                if (!entPtr.SetSpawnId(event.spawnId)) {
                    NetworkEventWarning(event, "Entity does not exist any longer, or has not been spawned yet.")
                } else {
                    ent = entPtr.GetEntity()!!
                    assert(ent != null)
                    eventMsg.Init(event.paramsBuf, event.paramsBuf.capacity())
                    eventMsg.SetSize(event.paramsSize)
                    eventMsg.BeginReading()
                    if (!ent.ServerReceiveEvent(event.event, event.time, eventMsg)) {
                        NetworkEventWarning(event, "unknown event")
                    }
                }
                val freedEvent: entityNetEvent_s? = eventQueue.Dequeue()
                assert(freedEvent === event)
                eventQueue.Free(event)
            }
        }

        private fun ClientProcessEntityNetworkEventQueue() {
            var ent: idEntity
            var event: entityNetEvent_s?
            val eventMsg = idBitMsg()
            while (eventQueue.Start() != null) {
                event = eventQueue.Start()!!

                // only process forward, in order
                if (event.time > time) {
                    break
                }
                val entPtr = idEntityPtr<idEntity?>()
                if (!entPtr.SetSpawnId(event.spawnId)) {
                    if (null == gameLocal.entities[event.spawnId and (1 shl GENTITYNUM_BITS) - 1]) {
                        // if new entity exists in this position, silently ignore
                        NetworkEventWarning(event, "Entity does not exist any longer, or has not been spawned yet.")
                    }
                } else {
                    ent = entPtr.GetEntity()!!
                    assert(ent != null)
                    eventMsg.Init(event.paramsBuf, event.paramsBuf.capacity())
                    eventMsg.SetSize(event.paramsSize)
                    eventMsg.BeginReading()
                    if (!ent.ClientReceiveEvent(event.event, event.time, eventMsg)) {
                        NetworkEventWarning(event, "unknown event")
                    }
                }
                val freedEvent: entityNetEvent_s? = eventQueue.Dequeue()
                assert(freedEvent == event)
                eventQueue.Free(event)
            }
        }

        private fun ClientShowSnapshot(clientNum: Int) {
            var baseBits: Int
            var ent: idEntity?
            val player: idPlayer?
            val viewAxis: idMat3
            val viewBounds: idBounds
            var base: entityState_s?
            if (0 == Game_network.net_clientShowSnapshot.GetInteger()) {
                return
            }
            player = entities[clientNum] as idPlayer?
            if (null == player) {
                return
            }
            viewAxis = player.viewAngles.ToMat3()
            viewBounds = player.GetPhysics().GetAbsBounds().Expand(Game_network.net_clientShowSnapshotRadius.GetFloat())
            ent = snapshotEntities.Next()
            while (ent != null) {
                if (Game_network.net_clientShowSnapshot.GetInteger() == 1 && ent.snapshotBits == 0) {
                    ent = ent.snapshotNode.Next()
                    continue
                }
                val entBounds = ent.GetPhysics().GetAbsBounds()
                if (!entBounds.IntersectsBounds(viewBounds)) {
                    ent = ent.snapshotNode.Next()
                    continue
                }
                base = clientEntityStates[clientNum][ent.entityNumber]
                baseBits = base?.state?.GetNumBitsWritten() ?: 0
                if (Game_network.net_clientShowSnapshot.GetInteger() == 2 && baseBits == 0) {
                    ent = ent.snapshotNode.Next()
                    continue
                }
                gameRenderWorld!!.DebugBounds(colorGreen, entBounds)
                gameRenderWorld!!.DrawText(
                    Str.va(
                        "%d: %s (%d,%d bytes of %d,%d)\n",
                        ent.entityNumber,
                        ent.name,
                        ent.snapshotBits shr 3,
                        ent.snapshotBits and 7,
                        baseBits shr 3,
                        baseBits and 7
                    ), entBounds.GetCenter(), 0.1f, colorWhite, viewAxis, 1
                )
                ent = ent.snapshotNode.Next()
            }
        }

        // call after any change to serverInfo. Will update various quick-access flags
        private fun UpdateServerInfoFlags() {
            gameType = gameType_t.GAME_SP
            if (idStr.Icmp(serverInfo.GetString("si_gameType"), "deathmatch") == 0) {
                gameType = gameType_t.GAME_DM
            } else if (idStr.Icmp(serverInfo.GetString("si_gameType"), "Tourney") == 0) {
                gameType = gameType_t.GAME_TOURNEY
            } else if (idStr.Icmp(serverInfo.GetString("si_gameType"), "Team DM") == 0) {
                gameType = gameType_t.GAME_TDM
            } else if (idStr.Icmp(serverInfo.GetString("si_gameType"), "Last Man") == 0) {
                gameType = gameType_t.GAME_LASTMAN
            }
            if (gameType == gameType_t.GAME_LASTMAN) {
                if (0 == serverInfo.GetInt("si_warmup")) {
                    Common.common.Warning("Last Man Standing - forcing warmup on")
                    serverInfo.SetInt("si_warmup", 1)
                }
                if (serverInfo.GetInt("si_fraglimit") <= 0) {
                    Common.common.Warning("Last Man Standing - setting fraglimit 1")
                    serverInfo.SetInt("si_fraglimit", 1)
                }
            }
        }

        /*
         ===========
         idGameLocal::RandomizeInitialSpawns
         randomize the order of the initial spawns
         prepare for a sequence of initial player spawns
         ============
         */
        private fun RandomizeInitialSpawns() {
            val spot = spawnSpot_t()  // create fresh copies for each append (value semantics)
            var i: Int
            var j: Int
            var k: Int
            var ent: idEntity?
            if (!isMultiplayer || isClient) {
                return
            }
            spawnSpots.Clear()
            initialSpots.Clear()
            // D3XP CTF
            if (isD3XP) {
                teamSpawnSpots[0].Clear()
                teamSpawnSpots[1].Clear()
                teamInitialSpots[0].Clear()
                teamInitialSpots[1].Clear()
            }

            spot.dist = 0
            spot.ent = FindEntityUsingDef(null, "info_player_deathmatch")
            while (spot.ent != null) {
                // D3XP CTF: read team spawnarg and sort into team lists
                if (isD3XP) {
                    spot.team = spot.ent!!.spawnArgs.GetInt("team", "-1")
                    if (mpGame.IsGametypeFlagBased()) {
                        if (spot.team == 0 || spot.team == 1) {
                            teamSpawnSpots[spot.team].Append(spawnSpot_t().also {
                                it.ent = spot.ent; it.dist = spot.dist; it.team = spot.team
                            })
                        } else {
                            Common.common.Warning("info_player_deathmatch : invalid or no team attached to spawn point")
                        }
                    }
                }

                spawnSpots.Append(spawnSpot_t().also { it.ent = spot.ent; it.dist = spot.dist; it.team = spot.team })
                if (spot.ent!!.spawnArgs.GetBool("initial")) {
                    // D3XP CTF
                    if (isD3XP && mpGame.IsGametypeFlagBased()) {
                        assert(spot.team == 0 || spot.team == 1)
                        teamInitialSpots[spot.team].Append(spot.ent!!)
                    }

                    initialSpots.Append(spot.ent!!)
                }
                spot.ent = FindEntityUsingDef(spot.ent, "info_player_deathmatch")
            }

            // D3XP CTF: validate team spawn spots
            if (isD3XP && mpGame.IsGametypeFlagBased()) {
                if (teamSpawnSpots[0].Num() == 0) {
                    Common.common.Warning("red team : no info_player_deathmatch in map")
                }
                if (teamSpawnSpots[1].Num() == 0) {
                    Common.common.Warning("blue team : no info_player_deathmatch in map")
                }
                if (teamSpawnSpots[0].Num() == 0 || teamSpawnSpots[1].Num() == 0) {
                    return
                }
            }

            if (0 == spawnSpots.Num()) {
                Common.common.Warning("no info_player_deathmatch in map")
                return
            }

            // D3XP CTF: print team spawn info and fill in missing initial spots
            if (isD3XP && mpGame.IsGametypeFlagBased()) {
                Common.common.Printf(
                    "red team : %d spawns (%d initials)\n", teamSpawnSpots[0].Num(), teamInitialSpots[0].Num()
                )
                if (teamInitialSpots[0].Num() == 0) {
                    Common.common.Warning("red team : no info_player_deathmatch entities marked initial in map")
                    i = 0
                    while (i < teamSpawnSpots[0].Num()) {
                        teamInitialSpots[0].Append(teamSpawnSpots[0][i].ent!!)
                        i++
                    }
                }
                Common.common.Printf(
                    "blue team : %d spawns (%d initials)\n", teamSpawnSpots[1].Num(), teamInitialSpots[1].Num()
                )
                if (teamInitialSpots[1].Num() == 0) {
                    Common.common.Warning("blue team : no info_player_deathmatch entities marked initial in map")
                    i = 0
                    while (i < teamSpawnSpots[1].Num()) {
                        teamInitialSpots[1].Append(teamSpawnSpots[1][i].ent!!)
                        i++
                    }
                }
            }

            Common.common.Printf("%d spawns (%d initials)\n", spawnSpots.Num(), initialSpots.Num())
            // if there are no initial spots in the map, consider they can all be used as initial
            if (0 == initialSpots.Num()) {
                Common.common.Warning("no info_player_deathmatch entities marked initial in map")
                i = 0
                while (i < spawnSpots.Num()) {
                    initialSpots.Append(spawnSpots[i].ent!!)
                    i++
                }
            }

            // D3XP CTF: shuffle team initial spots
            if (isD3XP) {
                k = 0
                while (k < 2) {
                    i = 0
                    while (i < teamInitialSpots[k].Num()) {
                        j = random.RandomInt(teamInitialSpots[k].Num())
                        ent = teamInitialSpots[k][i]
                        teamInitialSpots[k][i] = teamInitialSpots[k][j]
                        teamInitialSpots[k][j] = ent
                        i++
                    }
                    k++
                }
            }

            i = 0
            while (i < initialSpots.Num()) {
                j = random.RandomInt(initialSpots.Num())
                ent = initialSpots[i]
                initialSpots[i] = initialSpots[j]
                initialSpots[j] = ent
                i++
            }
            // reset the counter
            currentInitialSpot = 0

            // D3XP CTF
            if (isD3XP) {
                teamCurrentInitialSpot[0] = 0
                teamCurrentInitialSpot[1] = 0
            }
        }


        private fun DumpOggSounds() {
            var i: Int
            var j: Int
            var k: Int
            var size: Int
            var totalSize: Int
            val file: idFile?
            val oggSounds = idStrList()
            val weaponSounds = idStrList()
            var soundShader: idSoundShader
            var parms: snd_shader.soundShaderParms_t?
            var soundName: idStr
            i = 0
            while (i < declManager.GetNumDecls(declType_t.DECL_SOUND)) {
                soundShader = declManager.DeclByIndex(declType_t.DECL_SOUND, i, false) as idSoundShader
                parms = soundShader.GetParms()
                if (soundShader.EverReferenced() && soundShader.GetState() != declState_t.DS_DEFAULTED) {
                    soundShader.EnsureNotPurged()
                    j = 0
                    while (j < soundShader.GetNumSounds()) {
                        soundName = idStr(soundShader.GetSound(j))
                        soundName.BackSlashesToSlashes()

                        // D3XP: skip sounds already in base pak files
                        if (isD3XP) {
                            if (FileSystem_h.fileSystem.FileIsInPAK(soundName.toString())) {
                                j++
                                continue
                            }
                            val testOgg = idStr(soundName)
                            testOgg.SetFileExtension(".ogg")
                            if (FileSystem_h.fileSystem.FileIsInPAK(testOgg.toString())) {
                                j++
                                continue
                            }
                        }
                        // don't OGG sounds that cause a shake because that would
                        // cause continuous seeking on the OGG file which is expensive
                        if (parms.shakes != 0.0f) {
                            shakeSounds.addUnique(soundName)
                            j++
                            continue
                        }

                        // if not voice over or combat chatter
                        if (soundName.Find("/vo/", false) == -1 && soundName.Find(
                                "/combat_chatter/", false
                            ) == -1 && soundName.Find("/bfgcarnage/", false) == -1 && soundName.Find(
                                "/enpro/", false
                            ) == -1 && soundName.Find("/soulcube/energize_01.wav", false) == -1
                        ) {
                            // don't OGG weapon sounds
                            if (soundName.Find("weapon", false) != -1 || soundName.Find(
                                    "gun", false
                                ) != -1 || soundName.Find("bullet", false) != -1 || soundName.Find(
                                    "bfg", false
                                ) != -1 || soundName.Find("plasma", false) != -1
                            ) {
                                weaponSounds.addUnique(soundName)
                                j++
                                continue
                            }
                        }
                        k = 0
                        while (k < shakeSounds.size()) {
                            if (shakeSounds[k].IcmpPath(soundName.toString()) == 0) {
                                break
                            }
                            k++
                        }
                        if (k < shakeSounds.size()) {
                            j++
                            continue
                        }
                        oggSounds.addUnique(soundName)
                        j++
                    }
                }
                i++
            }
            file = FileSystem_h.fileSystem.OpenFileWrite("makeogg.bat", "fs_savepath")
            if (file == null) {
                Common.common.Warning("Couldn't open makeogg.bat")
                return
            }

            // list all the shake sounds
            totalSize = 0
            i = 0
            while (i < shakeSounds.size()) {
                size = FileSystem_h.fileSystem.ReadFile(shakeSounds[i], null, null)
                totalSize += size
                shakeSounds[i].Replace("/", "\\")
                file.Printf("echo \"%s\" (%d kB)\n", shakeSounds[i], size shr 10)
                i++
            }
            file.Printf("echo %d kB in shake sounds\n\n\n", totalSize shr 10)

            // list all the weapon sounds
            totalSize = 0
            i = 0
            while (i < weaponSounds.size()) {
                size = FileSystem_h.fileSystem.ReadFile(weaponSounds[i], null, null)
                totalSize += size
                weaponSounds[i].Replace("/", "\\")
                file.Printf("echo \"%s\" (%d kB)\n", weaponSounds[i], size shr 10)
                i++
            }
            file.Printf("echo %d kB in weapon sounds\n\n\n", totalSize shr 10)

            // list commands to convert all other sounds to ogg
            totalSize = 0
            i = 0
            while (i < oggSounds.size()) {
                size = FileSystem_h.fileSystem.ReadFile(oggSounds[i], null, null)
                totalSize += size
                oggSounds[i].Replace("/", "\\")
                file.Printf("w:\\doom\\ogg\\oggenc -q 0 \"c:\\doom\\base\\%s\"\n", oggSounds[i])
                file.Printf("del \"c:\\doom\\base\\%s\"\n", oggSounds[i])
                i++
            }
            file.Printf("\n\necho %d kB in OGG sounds\n\n\n", totalSize shr 10)
            FileSystem_h.fileSystem.CloseFile(file)
            shakeSounds.clear()
        }

        private fun GetShakeSounds(dict: idDict) {
            val soundShader: idSoundShader?
            val soundShaderName: String?
            val soundName = idStr()
            soundShaderName = dict.GetString("s_shader")
            if (!soundShaderName.isEmpty() && dict.GetFloat("s_shakes") != 0.0f) {
                soundShader = declManager.FindSound(soundShaderName)!!
                for (i in 0 until soundShader.GetNumSounds()) {
                    soundName.set(soundShader.GetSound(i))
                    soundName.BackSlashesToSlashes()
                    shakeSounds.addUnique(soundName)
                }
            }
        }

        override fun GetTimeGroupTime(timeGroup: Int): Int {
            if (isD3XP) {
                return if (timeGroup != 0) fast.time else slow.time
            }
            return time
        }

        override fun GetBestGameType(map: String, gametype: String, buf: CharArray /*[MAX_STRING_CHARS ]*/) {
            // D3XP: delegate to mpGame to check map's supported gametypes
            val aux = mpGame.GetBestGametype(map, gametype)
            val src = aux.toCharArray()
            val len = minOf(src.size, MAX_STRING_CHARS - 1)
            System.arraycopy(src, 0, buf, 0, len)
            buf[len] = '\u0000'
            buf[MAX_STRING_CHARS - 1] = '\u0000'
        }

        private fun Tokenize(out: idStrList, `in`: String) {
//	char buf[ MAX_STRING_CHARS ];
//	char *token, *next;
//
//	idStr::Copynz( buf, in, MAX_STRING_CHARS );
//	token = buf;
//	next = strchr( token, ';' );
//	while ( token ) {
//		if ( next ) {
//			*next = '\0';
//		}
//		idStr::ToLower( token );
//		out.Append( token );
//		if ( next ) {
//			token = next + 1;
//			next = strchr( token, ';' );
//		} else {
//			token = NULL;
//		}
//	}
            val tokens: Array<String> = `in`.split(";").toTypedArray()
            for (token in tokens) {
                out.add(token.lowercase())
            }
        }

        private fun UpdateLagometer(aheadOfServer: Int, dupeUsercmds: Int) {
            var i: Int
            val j: Int
            val ahead: Int
            i = 0
            while (i < LAGO_HEIGHT) {

//                memmove( (byte *)lagometer + LAGO_WIDTH * 4 * i, (byte *)lagometer + LAGO_WIDTH * 4 * i + 4, ( LAGO_WIDTH - 1 ) * 4 );
                memmove(
                    lagometer, LAGO_WIDTH * 4 * i, lagometer, LAGO_WIDTH * 4 * i + 4, (LAGO_WIDTH - 1) * 4
                ) //TODO:flatten 3d array and copy
                i++
            }
            j = LAGO_WIDTH - 1
            i = 0
            while (i < LAGO_HEIGHT) {
                lagometer[i][j][3] = 0
                lagometer[i][j][2] = lagometer[i][j][3]
                lagometer[i][j][1] = lagometer[i][j][2]
                lagometer[i][j][0] = lagometer[i][j][1]
                i++
            }
            ahead = idMath.Rint(aheadOfServer.toFloat() / 16.0f).toInt()
            if (ahead >= 0) {
                i = 2 * Max(0, 5 - ahead)
                while (i < 2 * 5) {
                    lagometer[i][j][1] = CCLV
                    lagometer[i][j][3] = CCLV
                    i++
                }
            } else {
                i = 2 * 5
                while (i < 2 * (5 + Min(10, -ahead))) {
                    lagometer[i][j][0] = CCLV
                    lagometer[i][j][1] = CCLV
                    lagometer[i][j][3] = CCLV
                    i++
                }
            }
            i = LAGO_HEIGHT - 2 * Min(6, dupeUsercmds)
            while (i < LAGO_HEIGHT) {
                lagometer[i][j][0] = CCLV
                if (dupeUsercmds <= 2) {
                    lagometer[i][j][1] = CCLV
                }
                lagometer[i][j][3] = CCLV
                i++
            }
        }

        override fun GetMapLoadingGUI(gui: CharArray? /*[MAX_STRING_CHARS ]*/) {}
        class MapRestart_f private constructor() : cmdFunction_t() {
            override fun run(args: CmdArgs.idCmdArgs?) {
                if (!gameLocal.isMultiplayer || gameLocal.isClient) {
                    Common.common.Printf("server is not running - use spawnServer\n")
                    CmdSystem.cmdSystem.BufferCommandText(cmdExecution_t.CMD_EXEC_APPEND, "spawnServer\n")
                    return
                }
                gameLocal.MapRestart()
            }

            companion object {
                private val instance: cmdFunction_t = MapRestart_f()
                fun getInstance(): cmdFunction_t {
                    return instance
                }
            }
        }

        class NextMap_f private constructor() : cmdFunction_t() {
            override fun run(args: CmdArgs.idCmdArgs?) {
                if (!gameLocal.isMultiplayer || gameLocal.isClient) {
                    Common.common.Printf("server is not running\n")
                    return
                }
                gameLocal.NextMap()
                // next map was either voted for or triggered by a server command - always restart
                gameLocal.MapRestart()
            }

            companion object {
                private val instance: cmdFunction_t = NextMap_f()
                fun getInstance(): cmdFunction_t {
                    return instance
                }
            }
        }

        /*
         =============
         idGameLocal::ArgCompletion_EntityName

         Argument completion for entity names
         =============
         */
        class ArgCompletion_EntityName private constructor() : CmdSystem.argCompletion_t() {
            override fun run(args: CmdArgs.idCmdArgs?, callback: void_callback<String>) {
                var i: Int
                i = 0
                while (i < gameLocal.num_entities) {
                    if (gameLocal.entities[i] != null) {
                        callback.run(Str.va("%s %s", args!!.Argv(0), gameLocal.entities[i]!!.name))
                    }
                    i++
                }
            }

            companion object {
                private val instance: CmdSystem.argCompletion_t = ArgCompletion_EntityName()
                fun getInstance(): CmdSystem.argCompletion_t {
                    return instance
                }
            }
        }

        // -----------------------------------------------------------------------
        // D3XP time management methods
        // -----------------------------------------------------------------------

        fun ResetSlowTimeVars() {
            msec = UsercmdGen.USERCMD_MSEC
            msecPrecise = USERCMD_MSEC_PRECISE
            slowmoMsec = USERCMD_MSEC_PRECISE
            slowmoState = slowmoState_t.SLOWMO_STATE_OFF

            fast.framenum = 0; fast.previousTime = 0; fast.time = 0
            fast.msec = UsercmdGen.USERCMD_MSEC; fast.msecPrecise = USERCMD_MSEC_PRECISE

            slow.framenum = 0; slow.previousTime = 0; slow.time = 0
            slow.msec = UsercmdGen.USERCMD_MSEC
            fast.msecPrecise =
                USERCMD_MSEC_PRECISE // C++ bug: copy-paste sets fast.msecPrecise again instead of slow.msecPrecise
        }

        fun QuickSlowmoReset() {
            quickSlowmoReset = true
        }

        fun SetPortalSkyEnt(ent: idEntity?) {
            portalSkyEnt.oSet(ent)
        }

        fun IsPortalSkyActive(): Boolean {
            return portalSkyActive
        }

        override fun SelectTimeGroup(timeGroup: Int) {
            if (!isD3XP) return
            if (timeGroup != 0) {
                // fast timeline (player speed)
                time = fast.time; previousTime = fast.previousTime
                msec = fast.msec; framenum = fast.framenum
                realClientTime = fast.realClientTime; msecPrecise = fast.msecPrecise
            } else {
                // slow timeline (world speed)
                time = slow.time; previousTime = slow.previousTime
                msec = slow.msec; framenum = slow.framenum
                realClientTime = slow.realClientTime; msecPrecise = slow.msecPrecise
            }
        }

        fun GetPlayerPVS(): pvsHandle_t = playerPVS

        // Stub for Tier 5: full slow-mo msec computation
        fun ComputeSlowMsec() {
            if (!isD3XP) return

            // quick reset (triggered by QuickSlowmoReset)
            if (quickSlowmoReset) {
                quickSlowmoReset = false
                gameSoundWorld?.SetSlowmo(false)
                gameSoundWorld?.SetSlowmoSpeed(1.0f)
                slowmoState = slowmoState_t.SLOWMO_STATE_OFF
                slowmoMsec = USERCMD_MSEC_PRECISE
            }

            val player = GetLocalPlayer()
            val powerupOn = (player != null && player.PowerUpActive(HELLTIME)) || SysCvar.g_enableSlowmo.GetBool()

            if (powerupOn && slowmoState == slowmoState_t.SLOWMO_STATE_OFF) {
                slowmoState = slowmoState_t.SLOWMO_STATE_RAMPUP
                slowmoMsec = msecPrecise
                gameSoundWorld?.SetSlowmo(true)
                gameSoundWorld?.SetSlowmoSpeed(slowmoMsec / USERCMD_MSEC_PRECISE)
            } else if (!powerupOn && slowmoState == slowmoState_t.SLOWMO_STATE_ON) {
                slowmoState = slowmoState_t.SLOWMO_STATE_RAMPDOWN
                player?.PlayHelltimeStopSound()
            }

            val quarterFrameTime = USERCMD_MSEC_PRECISE * 0.25f
            if (slowmoState == slowmoState_t.SLOWMO_STATE_RAMPUP) {
                val delta = quarterFrameTime - slowmoMsec
                if (Math.abs(delta) < SysCvar.g_slowmoStepRate.GetFloat()) {
                    slowmoMsec = quarterFrameTime
                    slowmoState = slowmoState_t.SLOWMO_STATE_ON
                } else {
                    slowmoMsec += delta * SysCvar.g_slowmoStepRate.GetFloat()
                }
                gameSoundWorld?.SetSlowmoSpeed(slowmoMsec / USERCMD_MSEC_PRECISE)
            } else if (slowmoState == slowmoState_t.SLOWMO_STATE_RAMPDOWN) {
                val delta = USERCMD_MSEC_PRECISE - slowmoMsec
                if (Math.abs(delta) < SysCvar.g_slowmoStepRate.GetFloat()) {
                    slowmoMsec = USERCMD_MSEC_PRECISE
                    slowmoState = slowmoState_t.SLOWMO_STATE_OFF
                    gameSoundWorld?.SetSlowmo(false)
                } else {
                    slowmoMsec += delta * SysCvar.g_slowmoStepRate.GetFloat()
                }
                gameSoundWorld?.SetSlowmoSpeed(slowmoMsec / USERCMD_MSEC_PRECISE)
            }
        }

        // Stub for Tier 5: run entities on the slow timeline
        fun RunTimeGroup2(msecFast: Int) {
            if (!isD3XP) return

            fast.Increment(msecFast)
            // fast.Get equivalent: switch global time fields to fast timeline
            time = fast.time; previousTime = fast.previousTime
            msec = fast.msec; framenum = fast.framenum
            realClientTime = fast.realClientTime; msecPrecise = fast.msecPrecise

            var ent = activeEntities.Next()
            while (ent != null) {
                if (ent.timeGroup == TIME_GROUP2) {
                    ent.Think()
                }
                ent = ent.activeNode.Next()
            }

            // slow.Get equivalent: restore global time fields to slow timeline
            time = slow.time; previousTime = slow.previousTime
            msec = slow.msec; framenum = slow.framenum
            realClientTime = slow.realClientTime; msecPrecise = slow.msecPrecise
        }

        private class sortSpawnPoints : cmp_t<spawnSpot_t> {
            override fun compare(s1: spawnSpot_t, s2: spawnSpot_t): Int {
                val diff: Float
                diff = (s1.dist - s2.dist).toFloat()
                return if (diff < 0.0f) {
                    1
                } else if (diff > 0.0f) {
                    -1
                } else {
                    0
                }
            }
        }

        companion object {
            // DG: unlike msec (instance var), msecPrecise is the exact float value (1000/60 = 16.6667 instead of 16)
            const val msecPrecise = 1000.0f / 60.0f

            // DG: returns 16 or 17 for each frame so that 60 frames add up to exactly 1000ms
            fun CalcMSec(framenum: Long): Int {
                val divisor = 100L * UsercmdGen.USERCMD_HZ
                return ((framenum * 100000L) / divisor - ((framenum - 1) * 100000L) / divisor).toInt()
            }

            private const val CCLV = 255.toByte()

            //
            //
            private const val INITIAL_SPAWN_COUNT = 1
            private const val INTERNAL_SAVEGAME_VERSION = 1 // DG: added this for >= 1305 savegames
            private val decalWinding: Array<idVec3> = arrayOf(
                idVec3(1.0f, 1.0f, 0.0f),
                idVec3(-1.0f, 1.0f, 0.0f),
                idVec3(-1.0f, -1.0f, 0.0f),
                idVec3(1.0f, -1.0f, 0.0f)
            )

            fun Error(fmt: String, vararg args: Any?) {
//	va_list		argptr;
                val text = StringBuilder(MAX_STRING_CHARS)
                val thread: idThread?
                //
//	va_start( argptr, fmt );
//	idStr::vsnPrintf( text, sizeof( text ), fmt, argptr );
//	va_end( argptr );
                text.append(String.format(fmt, *args))
                thread = idThread.CurrentThread()
                if (thread != null) {
                    thread.Error("%s", text)
                } else {
                    Common.common.Error("%s", text)
                }
            }

            /*
         ===============
         gameError
         ===============
         */
            fun gameError(fmt: String, vararg args: Any?) {
//	va_list		argptr;
//	char		text[MAX_STRING_CHARS];
//
//	va_start( argptr, fmt );
//	idStr::vsnPrintf( text, sizeof( text ), fmt, argptr );
//	va_end( argptr );
                Error("%s", String.format(fmt, *args))
            }
        }

        init {
            lastGUIEnt = idEntityPtr()
            lastAIAlertEntity = idEntityPtr()
            Clear()
        }
    }

    //============================================================================
    class idGameError(text: String) : idException(text)
    companion object {
        //
        const val DEFAULT_GRAVITY = 1066.0f

        var gameRenderWorld: idRenderWorld? = null
        var gameSoundWorld: idSoundWorld? = null

        //============================================================================
        // the rest of the engine will only reference the "game" variable, while all local aspects stay hidden
        val gameLocal: idGameLocal =
            idGameLocal() //TODO:these globals should either be collected to a single file, or always be set at the top.


        val game: idGame = gameLocal // statically pointed at an idGameLocal

        // if set to 1 the server sends the client PVS with snapshots and the client compares against what it sees
        //#ifndef ASYNC_WRITE_PVS
        const val ASYNC_WRITE_PVS = false
        const val CINEMATIC_SKIP_DELAY = 2000.0f
        const val DEFAULT_GRAVITY_STRING: String = "1066"
        val DEFAULT_GRAVITY_VEC3: idVec3 = idVec3(0.0f, 0.0f, -DEFAULT_GRAVITY)
        const val GAME_RELIABLE_MESSAGE_CALLVOTE = 14
        const val GAME_RELIABLE_MESSAGE_CASTVOTE = 15
        const val GAME_RELIABLE_MESSAGE_CHAT = 4
        const val GAME_RELIABLE_MESSAGE_DB = 8
        const val GAME_RELIABLE_MESSAGE_DELETE_ENT = 3
        const val GAME_RELIABLE_MESSAGE_DROPWEAPON = 10
        const val GAME_RELIABLE_MESSAGE_EVENT = 24

        // enum {
        const val GAME_RELIABLE_MESSAGE_INIT_DECL_REMAP = 0
        const val GAME_RELIABLE_MESSAGE_KILL = 9
        const val GAME_RELIABLE_MESSAGE_MENU = 22
        const val GAME_RELIABLE_MESSAGE_PORTAL = 19
        const val GAME_RELIABLE_MESSAGE_PORTALSTATES = 18
        const val GAME_RELIABLE_MESSAGE_REMAP_DECL = 1
        const val GAME_RELIABLE_MESSAGE_RESTART = 11
        const val GAME_RELIABLE_MESSAGE_SERVERINFO = 12
        const val GAME_RELIABLE_MESSAGE_SOUND_EVENT = 6
        const val GAME_RELIABLE_MESSAGE_SOUND_INDEX = 7
        const val GAME_RELIABLE_MESSAGE_SPAWN_PLAYER = 2
        const val GAME_RELIABLE_MESSAGE_STARTSTATE = 21
        const val GAME_RELIABLE_MESSAGE_STARTVOTE = 16
        const val GAME_RELIABLE_MESSAGE_TCHAT = 5
        const val GAME_RELIABLE_MESSAGE_TOURNEYLINE = 13
        const val GAME_RELIABLE_MESSAGE_UPDATEVOTE = 17
        const val GAME_RELIABLE_MESSAGE_VCHAT = 20
        const val GAME_RELIABLE_MESSAGE_WARMUPTIME = 23

        //
        // the "gameversion" client command will print this plus compile date
        const val GAME_VERSION: String = "baseDOOM-1"
        const val GENTITYNUM_BITS = 12
        const val LAGO_HEIGHT = 44
        const val LAGO_IMAGE: String = "textures/sfx/lagometer.tga"
        const val LAGO_IMG_HEIGHT = 64

        /*
         ===============================================================================

         Local implementation of the public game interface.

         ===============================================================================
         */
        const val LAGO_IMG_WIDTH = 64
        const val LAGO_MATERIAL: String = "textures/sfx/lagometer"
        const val LAGO_WIDTH = 64

        //
        // content masks
        const val MASK_ALL = -1
        val MASK_DEADSOLID = Material.CONTENTS_SOLID or Material.CONTENTS_PLAYERCLIP
        val MASK_MONSTERSOLID = Material.CONTENTS_SOLID or Material.CONTENTS_MONSTERCLIP or Material.CONTENTS_BODY
        val MASK_OPAQUE = Material.CONTENTS_OPAQUE
        val MASK_PLAYERSOLID = Material.CONTENTS_SOLID or Material.CONTENTS_PLAYERCLIP or Material.CONTENTS_BODY
        val MASK_SHOT_BOUNDINGBOX = Material.CONTENTS_SOLID or Material.CONTENTS_BODY
        val MASK_SHOT_RENDERMODEL = Material.CONTENTS_SOLID or Material.CONTENTS_RENDERMODEL
        val MASK_SOLID = Material.CONTENTS_SOLID
        val MASK_WATER = Material.CONTENTS_WATER

        //
        const val MAX_CLIENTS = 32
        const val MAX_ENTITY_STATE_SIZE = 512
        const val MAX_EVENT_PARAM_SIZE = 128

        //============================================================================
        //============================================================================
        const val MAX_GAME_MESSAGE_SIZE = 8192

        //
        const val MAX_GENTITIES = 1 shl GENTITYNUM_BITS
        const val ENTITYNUM_NONE = MAX_GENTITIES - 1
        const val ENTITYNUM_WORLD = MAX_GENTITIES - 2

        // };
        const val ENTITYNUM_MAX_NORMAL = MAX_GENTITIES - 2
        const val ENTITY_PVS_SIZE = MAX_GENTITIES + 31 shr 5

        //============================================================================
        val NUM_RENDER_PORTAL_BITS = idMath.BitsForInteger(etoi(portalConnection_t.PS_BLOCK_ALL))
        val animationLib: idAnimManager = idAnimManager()


        //============================================================================
        const val GAME_DLL = false // Kotlin is monolithic (no DLL boundary), so game must not manage idLib lifecycle

        // D3XP (Resurrection of Evil) runtime flag — set during Init() based on fs_game/fs_game_base
        var isD3XP: Boolean = false

        // D3XP dual-timeline constants: entities run on fast (player-speed) or slow timeline
        const val TIME_GROUP1 = 0 // slow timeline (world entities; affected by slow-mo powerup)
        const val TIME_GROUP2 = 1 // fast timeline (player, weapons, projectiles; always full speed)

        // D3XP precise msec per frame at 60 Hz (used for slow-mo timing restoration from saves)
        const val USERCMD_MSEC_PRECISE = 1000.0f / 60.0f

        // D3XP: entity class names that run on the fast (player-speed) timeline during slow-mo
        val FAST_ENTITY_LIST = arrayOf(
            "player_doommarine",
            "weapon_chainsaw",
            "weapon_fists",
            "weapon_flashlight",
            "weapon_rocketlauncher",
            "projectile_rocket",
            "weapon_machinegun",
            "projectile_bullet_machinegun",
            "weapon_pistol",
            "projectile_bullet_pistol",
            "weapon_handgrenade",
            "projectile_grenade",
            "weapon_bfg",
            "projectile_bfg",
            "weapon_chaingun",
            "projectile_chaingunbullet",
            "weapon_pda",
            "weapon_plasmagun",
            "projectile_plasmablast",
            "weapon_shotgun",
            "projectile_bullet_shotgun",
            "weapon_soulcube",
            "projectile_soulblast",
            "weapon_shotgun_double",
            "projectile_shotgunbullet_double",
            "weapon_grabber",
            "weapon_bloodstone_active1",
            "weapon_bloodstone_active2",
            "weapon_bloodstone_active3",
            "weapon_bloodstone_passive"
        )

        //
        val com_forceGenericSIMD: idCVar = idCVar(
            "com_forceGenericSIMD",
            "1",
            CVarSystem.CVAR_BOOL or CVarSystem.CVAR_SYSTEM,
            "force generic platform independent SIMD"
        )

        /*
     ===========
     TestGameAPI
     ============
     */
        fun TestGameAPI() {
            val testImport = gameImport_t()
            var testExport = gameExport_t()
            testImport.sys = sys
            testImport.common = Common.common
            testImport.cmdSystem = CmdSystem.cmdSystem
            testImport.cvarSystem = CVarSystem.cvarSystem
            testImport.fileSystem = FileSystem_h.fileSystem
            testImport.networkSystem = NetworkSystem.networkSystem
            testImport.renderSystem = RenderSystem.renderSystem
            testImport.soundSystem = snd_system.soundSystem
            testImport.renderModelManager = ModelManager.renderModelManager
            testImport.uiManager = UserInterface.uiManager
            testImport.declManager = declManager
            testImport.AASFileManager = AASFileManager.AASFileManager
            testImport.collisionModelManager = collisionModelManager
            testExport = GetGameAPI(testImport)
        }

        /*
     ===========
     GetGameAPI
     ============
     */
        fun GetGameAPI(gameImport: gameImport_t): gameExport_t {
            if (gameImport.version == Game.GAME_API_VERSION) {
                // set interface pointers used by the game
                setSysLocal(gameImport.sys)
                Common.setCommons(gameImport.common)
                CmdSystem.setCmdSystems(gameImport.cmdSystem)
                CVarSystem.setCvarSystems(gameImport.cvarSystem)
                FileSystem_h.setFileSystems(gameImport.fileSystem) //TODO:set both the fileSystem and the fileSystemLocal it's referencing.
                NetworkSystem.setNetworkSystems(gameImport.networkSystem)
                RenderSystem.setRenderSystems(gameImport.renderSystem)
                snd_system.setSoundSystems(gameImport.soundSystem)
                ModelManager.setRenderModelManagers(gameImport.renderModelManager)
                UserInterface.setUiManagers(gameImport.uiManager)
                DeclManager.setDeclManagers(gameImport.declManager)
                AASFileManager.setAASFileManagers(gameImport.AASFileManager)
                setCollisionModelManagers(gameImport.collisionModelManager)
            }

            // set interface pointers used by idLib
            idLib.sys = sys
            idLib.common = Common.common
            idLib.cvarSystem = CVarSystem.cvarSystem
            idLib.fileSystem = FileSystem_h.fileSystem

            // setup export interface
            gameExport.version = Game.GAME_API_VERSION
            gameExport.game = game
            gameExport.gameEdit = GameEdit.gameEdit
            return gameExport
        }

        private val gameExport: gameExport_t = gameExport_t()
        private fun memmove(
            dst: Array<Array<ByteArray>>, dstOffset: Int, src: Array<Array<ByteArray>>, srcOffset: Int, length: Int
        ) {
            var sa: Int
            var sb: Int
            var sc: Int
            var da: Int
            var db: Int
            var dc: Int
            sc = srcOffset % src.size
            sb = (srcOffset - sc) / src.size
            sa = (srcOffset - sc - sb * src.size) / src[0].size
            dc = dstOffset % dst.size
            db = (dstOffset - dc) / dst.size
            da = (dstOffset - dc - db * dst.size) / dst[0].size
            var count = 0
            while (sa < src.size) {
                sb = 0
                while (sb < src[0].size) {
                    while (sc < src[0][0].size && count < length) {
                        dst[da][db][dc++] = src[sa][sb][sc]
                        if (dc == dst[0][0].size) {
                            dc = 0
                            if (++db == dst[0].size) {
                                db = 0
                                da++ //if this overflows, then we're fucked!
                            }
                        }
                        sc++
                        count++
                    }
                    sb++
                }
                sa++
            }
        }
    }
}

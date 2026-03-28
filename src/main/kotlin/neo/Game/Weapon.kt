/*
===========================================================================

Doom 3 GPL Source Code
Copyright (C) 1999-2011 id Software LLC, a ZeniMax Media company.
Translated to Kotlin by Dr. Feederino with support of Claude Code

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

In addition, the Doom 3 Source Code is also subject to certain additional terms.
You should have received a copy of these additional terms immediately following
the terms and conditions of the GNU General Public License which accompanied
the Doom 3 Source Code.  If not, please request a copy in writing from
id Software at the address below.

If you have questions concerning this license or the applicable additional terms,
you may contact in writing id Software LLC, c/o ZeniMax Media Inc., Suite 120,
Rockville, Maryland 20850 USA.

===========================================================================

Original source: neo/game/Weapon.cpp, neo/game/Weapon.h
*/
package neo.Game

import neo.Game.AI.idAI
import neo.Game.Animation.Anim
import neo.Game.Game.refSound_t
import neo.Game.GameSys.Class.*
import neo.Game.GameSys.Event.idEventDef
import neo.Game.GameSys.SaveGame.idRestoreGame
import neo.Game.GameSys.SaveGame.idSaveGame
import neo.Game.GameSys.SysCvar
import neo.Game.Game_local.*
import neo.Game.MultiplayerGame.gameType_t
import neo.Game.Player.idPlayer
import neo.Game.Projectile.idDebris
import neo.Game.Projectile.idProjectile
import neo.Game.Script.Script_Program.function_t
import neo.Game.Script.Script_Program.idScriptBool
import neo.Game.Script.Script_Thread.idThread
import neo.Game.Trigger.idTrigger
import neo.Renderer.Material
import neo.Renderer.Material.surfTypes_t
import neo.Renderer.Model
import neo.Renderer.RenderWorld
import neo.Renderer.RenderWorld.renderEntity_s
import neo.Renderer.RenderWorld.renderLight_s
import neo.Sound.snd_shader.idSoundShader
import neo.TempDump
import neo.cm.collisionModelManager
import neo.cm.trace_s
import neo.framework.DeclEntityDef.idDeclEntityDef
import neo.framework.DeclManager
import neo.framework.DeclManager.declType_t
import neo.framework.DeclParticle.idDeclParticle
import neo.framework.DeclSkin.idDeclSkin
import neo.framework.ID_DEMO_BUILD
import neo.idlib.BV.idBounds
import neo.idlib.BitMsg.idBitMsg
import neo.idlib.BitMsg.idBitMsgDelta
import neo.idlib.Dict_h.idDict
import neo.idlib.Dict_h.idKeyValue
import neo.idlib.Text.Str
import neo.idlib.Text.Str.idStr
import neo.idlib.colorGreen
import neo.idlib.colorRed
import neo.idlib.colorYellow
import neo.idlib.containers.CFloat
import neo.idlib.containers.CInt
import neo.idlib.geometry.TraceModel.idTraceModel
import neo.idlib.idLib
import neo.idlib.math.*
import neo.idlib.math.Matrix.idMat3
import neo.ui.UserInterface
import java.nio.ByteBuffer

val EV_Weapon_AddToClip: idEventDef = idEventDef("addToClip", "d")
val EV_Weapon_AllowDrop: idEventDef = idEventDef("allowDrop", "d")
val EV_Weapon_AmmoAvailable: idEventDef = idEventDef("ammoAvailable", null, 'f')
val EV_Weapon_AmmoInClip: idEventDef = idEventDef("ammoInClip", null, 'f')
val EV_Weapon_AutoReload: idEventDef = idEventDef("autoReload", null, 'f')
val EV_Weapon_Clear: idEventDef = idEventDef("<clear>")
val EV_Weapon_ClipSize: idEventDef = idEventDef("clipSize", null, 'f')
val EV_Weapon_CreateProjectile: idEventDef = idEventDef("createProjectile", null, 'e')
val EV_Weapon_EjectBrass: idEventDef = idEventDef("ejectBrass")
val EV_Weapon_Flashlight: idEventDef = idEventDef("flashlight", "d")
val EV_Weapon_GetOwner: idEventDef = idEventDef("getOwner", null, 'e')
val EV_Weapon_GetWorldModel: idEventDef = idEventDef("getWorldModel", null, 'e')
val EV_Weapon_IsInvisible: idEventDef = idEventDef("isInvisible", null, 'f')
val EV_Weapon_LaunchProjectiles: idEventDef = idEventDef("launchProjectiles", "dffff")
val EV_Weapon_Melee: idEventDef = idEventDef("melee", null, 'd')
val EV_Weapon_NetEndReload: idEventDef = idEventDef("netEndReload")
val EV_Weapon_NetReload: idEventDef = idEventDef("netReload")
val EV_Weapon_Next: idEventDef = idEventDef("nextWeapon")
val EV_Weapon_State: idEventDef = idEventDef("weaponState", "sd")
val EV_Weapon_TotalAmmoCount: idEventDef = idEventDef("totalAmmoCount", null, 'f')
val EV_Weapon_UseAmmo: idEventDef = idEventDef("useAmmo", "d")
val EV_Weapon_WeaponHolstered: idEventDef = idEventDef("weaponHolstered")
val EV_Weapon_WeaponLowering: idEventDef = idEventDef("weaponLowering")
val EV_Weapon_WeaponOutOfAmmo: idEventDef = idEventDef("weaponOutOfAmmo")
val EV_Weapon_WeaponReady: idEventDef = idEventDef("weaponReady")
val EV_Weapon_WeaponReloading: idEventDef = idEventDef("weaponReloading")
val EV_Weapon_WeaponRising: idEventDef = idEventDef("weaponRising")

object Weapon {
    const val AMMO_NUMTYPES = 16
    const val LIGHTID_VIEW_MUZZLE_FLASH = 100
    const val LIGHTID_WORLD_MUZZLE_FLASH = 1

    /*
     ===============================================================================

     Player Weapon

     ===============================================================================
     */
    enum class weaponStatus_t {
        WP_READY,
        WP_OUTOFAMMO,
        WP_RELOAD,
        WP_HOLSTERED,
        WP_RISING,
        WP_LOWERING
    }

    /* **********************************************************************

     idWeapon  
	
     ***********************************************************************/
    class idWeapon : idAnimatedEntity() {
        companion object {
            val Type = idTypeInfo("idWeapon", "idAnimatedEntity") { idWeapon() }

            val EVENT_RELOAD: Int = idEntity.EVENT_MAXEVENTS
            val EVENT_ENDRELOAD = EVENT_RELOAD + 1
            val EVENT_CHANGESKIN = EVENT_RELOAD + 2
            val EVENT_MAXEVENTS = EVENT_RELOAD + 3

            private val eventCallbacks: MutableMap<idEventDef, eventCallback_t<*>> = HashMap()

            /*
             ================
             idWeapon::CacheWeapon
             ================
             */
            fun CacheWeapon(weaponName: String) {
                val weaponDef: idDeclEntityDef?
                val brassDefName: String?
                val clipModelName = idStr()
                val trm = idTraceModel()
                val guiName: String?
                weaponDef = Game_local.gameLocal.FindEntityDef(weaponName, false)
                if (null == weaponDef) {
                    return
                }

                // precache the brass collision model
                brassDefName = weaponDef.dict.GetString("def_ejectBrass")
                if (brassDefName.isNotEmpty()) {
                    val brassDef = Game_local.gameLocal.FindEntityDef(brassDefName, false)
                    if (brassDef != null) {
                        brassDef.dict.GetString("clipmodel", "", clipModelName)
                        if (clipModelName.IsEmpty()) {
                            clipModelName.set(brassDef.dict.GetString("model")) // use the visual model
                        }
                        // load the trace model
                        collisionModelManager.TrmFromModel(clipModelName, trm)
                    }
                }
                guiName = weaponDef.dict.GetString("gui")
                if (guiName.isNotEmpty()) {
                    UserInterface.uiManager.FindGui(guiName, true, false, true)
                }
            }

            /* **********************************************************************

         Ammo

         ***********************************************************************/
            /*
             ================
             idWeapon::GetAmmoNumForName
             ================
             */
            fun  /*ammo_t*/GetAmmoNumForName(ammoname: String): Int {
                val num = CInt()
                val ammoDict: idDict?
                assert(ammoname != null)
                ammoDict = Game_local.gameLocal.FindEntityDefDict("ammo_types", false)
                if (null == ammoDict) {
                    idGameLocal.Error("Could not find entity definition for 'ammo_types'\n")
                }
                if (ammoname.isEmpty()) {
                    return 0
                }
                if (!ammoDict!!.GetInt(ammoname, "-1", num)) {
                    idGameLocal.Error("Unknown ammo type '%s'", ammoname)
                }
                if (num.integerValue < 0 || num.integerValue >= AMMO_NUMTYPES) {
                    idGameLocal.Error(
                        "Ammo type '%s' value out of range.  Maximum ammo types is %d.\n",
                        ammoname,
                        AMMO_NUMTYPES
                    )
                }
                return num.integerValue
            }

            /*
             ================
             idWeapon::GetAmmoNameForNum
             ================
             */
            fun GetAmmoNameForNum(   /*ammo_t*/ammonum: Int): String? {
                var i: Int
                val num: Int
                val ammoDict: idDict?
                var kv: idKeyValue?
                val text: String
                ammoDict = Game_local.gameLocal.FindEntityDefDict("ammo_types", false)
                if (null == ammoDict) {
                    idGameLocal.Error("Could not find entity definition for 'ammo_types'\n")
                }
                text = String.format("%d", ammonum)
                num = ammoDict!!.GetNumKeyVals()
                i = 0
                while (i < num) {
                    kv = ammoDict.GetKeyVal(i)!!
                    if (kv.GetValue().toString() == text) {
                        return kv.GetKey().toString()
                    }
                    i++
                }
                return null
            }

            /*
             ================
             idWeapon::GetAmmoPickupNameForNum
             ================
             */
            fun GetAmmoPickupNameForNum(   /*ammo_t*/ammonum: Int): String {
                var i: Int
                val num: Int
                val ammoDict: idDict?
                var kv: idKeyValue?
                ammoDict = Game_local.gameLocal.FindEntityDefDict("ammo_names", false)
                if (null == ammoDict) {
                    idGameLocal.Error("Could not find entity definition for 'ammo_names'\n")
                }
                val name = GetAmmoNameForNum(ammonum)
                if (!name.isNullOrEmpty()) {
                    num = ammoDict!!.GetNumKeyVals()
                    i = 0
                    while (i < num) {
                        kv = ammoDict.GetKeyVal(i)!!
                        if (idStr.Icmp(kv.GetKey().toString(), name) == 0) {
                            return kv.GetValue().toString()
                        }
                        i++
                    }
                }
                return ""
            }

            fun getEventCallBacks(): MutableMap<idEventDef, eventCallback_t<*>> {
                return eventCallbacks
            }

            init {
                eventCallbacks.putAll(idAnimatedEntity.getEventCallBacks())
                eventCallbacks[EV_Weapon_Clear] =
                    eventCallback_t0<idWeapon> { obj: idWeapon -> obj.Event_Clear() }
                eventCallbacks[EV_Weapon_GetOwner] =
                    eventCallback_t0<idWeapon> { obj: idWeapon -> obj.Event_GetOwner() }
                eventCallbacks[EV_Weapon_State] =
                    eventCallback_t2<idWeapon> { obj: idWeapon, _statename: idEventArg<*>, blendFrames: idEventArg<*> ->
                        obj.Event_WeaponState(_statename as idEventArg<String?>, blendFrames as idEventArg<Int>)
                    }
                eventCallbacks[EV_Weapon_WeaponReady] =
                    eventCallback_t0<idWeapon> { obj: idWeapon -> obj.Event_WeaponReady() }
                eventCallbacks[EV_Weapon_WeaponOutOfAmmo] =
                    eventCallback_t0<idWeapon> { obj: idWeapon -> obj.Event_WeaponOutOfAmmo() }
                eventCallbacks[EV_Weapon_WeaponReloading] =
                    eventCallback_t0<idWeapon> { obj: idWeapon -> obj.Event_WeaponReloading() }
                eventCallbacks[EV_Weapon_WeaponHolstered] =
                    eventCallback_t0<idWeapon> { obj: idWeapon -> obj.Event_WeaponHolstered() }
                eventCallbacks[EV_Weapon_WeaponRising] =
                    eventCallback_t0<idWeapon> { obj: idWeapon -> obj.Event_WeaponRising() }
                eventCallbacks[EV_Weapon_WeaponLowering] =
                    eventCallback_t0<idWeapon> { obj: idWeapon -> obj.Event_WeaponLowering() }
                eventCallbacks[EV_Weapon_UseAmmo] =
                    eventCallback_t1<idWeapon> { obj: idWeapon, _amount: idEventArg<*>? -> obj.Event_UseAmmo(_amount as idEventArg<Int>) }
                eventCallbacks[EV_Weapon_AddToClip] =
                    eventCallback_t1<idWeapon> { obj: idWeapon, amount: idEventArg<*>? -> obj.Event_AddToClip(amount as idEventArg<Int>) }
                eventCallbacks[EV_Weapon_AmmoInClip] =
                    eventCallback_t0<idWeapon> { obj: idWeapon -> obj.Event_AmmoInClip() }
                eventCallbacks[EV_Weapon_AmmoAvailable] =
                    eventCallback_t0<idWeapon> { obj: idWeapon -> obj.Event_AmmoAvailable() }
                eventCallbacks[EV_Weapon_TotalAmmoCount] =
                    eventCallback_t0<idWeapon> { obj: idWeapon -> obj.Event_TotalAmmoCount() }
                eventCallbacks[EV_Weapon_ClipSize] =
                    eventCallback_t0<idWeapon> { obj: idWeapon -> obj.Event_ClipSize() }
                eventCallbacks[AI_PlayAnim] =
                    eventCallback_t2<idWeapon> { obj: idWeapon, _channel: idEventArg<*>?, _animname: idEventArg<*>? ->
                        obj.Event_PlayAnim(
                            _channel as idEventArg<Int>,
                            _animname as idEventArg<String>
                        )
                    }
                eventCallbacks[AI_PlayCycle] =
                    eventCallback_t2<idWeapon> { obj: idWeapon, _channel: idEventArg<*>?, _animname: idEventArg<*>? ->
                        obj.Event_PlayCycle(
                            _channel as idEventArg<Int>,
                            _animname as idEventArg<String>
                        )
                    }
                eventCallbacks[AI_SetBlendFrames] =
                    eventCallback_t2<idWeapon> { obj: idWeapon, channel: idEventArg<*>?, blendFrames: idEventArg<*>? ->
                        obj.Event_SetBlendFrames(channel as idEventArg<Int>, blendFrames as idEventArg<Int>)
                    }
                eventCallbacks[AI_GetBlendFrames] =
                    eventCallback_t1<idWeapon> { obj: idWeapon, channel: idEventArg<*>? ->
                        obj.Event_GetBlendFrames(channel as idEventArg<Int>)
                    }
                eventCallbacks[AI_AnimDone] =
                    eventCallback_t2<idWeapon> { obj: idWeapon, channel: idEventArg<*>?, blendFrames: idEventArg<*>? ->
                        obj.Event_AnimDone(
                            channel as idEventArg<Int>,
                            blendFrames as idEventArg<Int>
                        )
                    }
                eventCallbacks[EV_Weapon_Next] =
                    eventCallback_t0<idWeapon> { obj: idWeapon -> obj.Event_Next() }
                eventCallbacks[EV_SetSkin] =
                    eventCallback_t1<idWeapon> { obj: idWeapon, _skinname: idEventArg<*>? -> obj.Event_SetSkin(_skinname as idEventArg<String?>) }
                eventCallbacks[EV_Weapon_Flashlight] =
                    eventCallback_t1<idWeapon> { obj: idWeapon, enable: idEventArg<*>? -> obj.Event_Flashlight(enable as idEventArg<Int>) }
                eventCallbacks[EV_Light_GetLightParm] =
                    eventCallback_t1<idWeapon> { obj: idWeapon, _parmnum: idEventArg<*>? ->
                        obj.Event_GetLightParm(_parmnum as idEventArg<Int>)
                    }
                eventCallbacks[EV_Light_SetLightParm] =
                    eventCallback_t2<idWeapon> { obj: idWeapon, _parmnum: idEventArg<*>?, _value: idEventArg<*>? ->
                        obj.Event_SetLightParm(_parmnum as idEventArg<Int>, _value as idEventArg<Float>)
                    }
                eventCallbacks[EV_Light_SetLightParms] =
                    eventCallback_t4<idWeapon> { obj: idWeapon,
                                                 parm0: idEventArg<*>?,
                                                 parm1: idEventArg<*>?,
                                                 parm2: idEventArg<*>?,
                                                 parm3: idEventArg<*>? ->
                        obj.Event_SetLightParms(
                            parm0 as idEventArg<Float>,
                            parm1 as idEventArg<Float>,
                            parm2 as idEventArg<Float>,
                            parm3 as idEventArg<Float>
                        )
                    }
                eventCallbacks[EV_Weapon_LaunchProjectiles] =
                    eventCallback_t5<idWeapon> { obj: idWeapon, _num_projectiles: idEventArg<*>?, _spread: idEventArg<*>?,
                                                 fuseOffset: idEventArg<*>?,
                                                 launchPower: idEventArg<*>?,
                                                 _dmgPower: idEventArg<*>? ->
                        obj.Event_LaunchProjectiles(
                            _num_projectiles as idEventArg<Int>, _spread as idEventArg<Float>,
                            fuseOffset as idEventArg<Float>,
                            launchPower as idEventArg<Float>,
                            _dmgPower as idEventArg<Float>
                        )
                    }
                eventCallbacks[EV_Weapon_CreateProjectile] =
                    eventCallback_t0<idWeapon> { obj: idWeapon -> obj.Event_CreateProjectile() }
                eventCallbacks[EV_Weapon_EjectBrass] =
                    eventCallback_t0<idWeapon> { obj: idWeapon -> obj.Event_EjectBrass() }
                eventCallbacks[EV_Weapon_Melee] =
                    eventCallback_t0<idWeapon> { obj: idWeapon -> obj.Event_Melee() }
                eventCallbacks[EV_Weapon_GetWorldModel] =
                    eventCallback_t0<idWeapon> { obj: idWeapon -> obj.Event_GetWorldModel() }
                eventCallbacks[EV_Weapon_AllowDrop] =
                    eventCallback_t1<idWeapon> { obj: idWeapon, allow: idEventArg<*>? -> obj.Event_AllowDrop(allow as idEventArg<Int>) }
                eventCallbacks[EV_Weapon_AutoReload] =
                    eventCallback_t0<idWeapon> { obj: idWeapon -> obj.Event_AutoReload() }
                eventCallbacks[EV_Weapon_NetReload] =
                    eventCallback_t0<idWeapon> { obj: idWeapon -> obj.Event_NetReload() }
                eventCallbacks[EV_Weapon_IsInvisible] =
                    eventCallback_t0<idWeapon> { obj: idWeapon -> obj.Event_IsInvisible() }
                eventCallbacks[EV_Weapon_NetEndReload] =
                    eventCallback_t0<idWeapon> { obj: idWeapon -> obj.Event_NetEndReload() }
            }
        }

        // script control
        private val WEAPON_ATTACK: idScriptBool = idScriptBool()
        private val WEAPON_LOWERWEAPON: idScriptBool = idScriptBool()
        private val WEAPON_NETENDRELOAD: idScriptBool = idScriptBool()
        private val WEAPON_NETFIRING: idScriptBool = idScriptBool()
        private val WEAPON_NETRELOAD: idScriptBool = idScriptBool()
        private val WEAPON_RAISEWEAPON: idScriptBool = idScriptBool()
        private val WEAPON_RELOAD: idScriptBool = idScriptBool()
        private val brassDict: idDict

        //
        private val flashColor: idVec3
        private val icon: idStr
        private val idealState: idStr
        private val meleeDefName: idStr
        private val muzzleAxis: idMat3

        //
        // the muzzle bone's position, used for launching projectiles and trailing smoke
        private val muzzleOrigin: idVec3
        private val muzzle_kick_angles: idAngles
        private val muzzle_kick_offset: idVec3

        //
        private val nozzleGlowColor // color of the nozzle glow
                : idVec3
        private val playerViewAxis: idMat3

        //
        // these are the player render view parms, which include bobbing
        private val playerViewOrigin: idVec3
        private val projectileDict: idDict

        //
        private val pushVelocity: idVec3
        private val state: idStr = idStr()
        private val strikePos // position of last melee strike
                : idVec3
        private val viewWeaponAxis: idMat3

        // the view weapon render entity parms
        private val viewWeaponOrigin: idVec3
        private val worldModel: idEntityPtr<idAnimatedEntity?>
        private var allowDrop: Boolean
        private var ammoClip = 0
        private var ammoRequired // amount of ammo to use each shot.  0 means weapon doesn't need ammo.
                = 0

        // ammo management
        private var   /*ammo_t*/ammoType = 0
        private var animBlendFrames = 0
        private var animDoneTime = 0

        // joints from models
        private var   /*jointHandle_t*/barrelJointView = 0
        private var   /*jointHandle_t*/barrelJointWorld = 0

        // berserk
        private var berserk: Int
        private var brassDelay: Int
        private var clipSize // 0 means no reload
                = 0
        private var continuousSmoke // if smoke is continuous ( chainsaw )
                = false
        private var disabled = false
        private var   /*jointHandle_t*/ejectJointView = 0
        private var   /*jointHandle_t*/ejectJointWorld = 0
        private var   /*jointHandle_t*/flashJointView = 0
        private var   /*jointHandle_t*/flashJointWorld = 0
        private var flashTime = 0

        // view weapon gui light
        private var guiLight: renderLight_s
        private var guiLightHandle: Int
        private var   /*jointHandle_t*/guiLightJointView = 0

        // effects
        private var hasBloodSplat = false
        private var hide = false
        private var hideDistance = 0.0f
        private var hideEnd = 0.0f
        private var hideOffset = 0.0f
        private var hideStart = 0.0f
        private var hideStartTime = 0

        // hiding (for GUIs and NPCs)
        private var hideTime = 0

        // a projectile is launched
        // mp client
        private var isFiring = false
        private var isLinked = false

        //
        // weapon kick
        private var kick_endtime = 0
        private var lastAttack // last time an attack occured
                = 0
        private var lightOn = false
        private var lowAmmo // if ammo in clip hits this threshold, snd_
                = 0
        private var meleeDef: idDeclEntityDef? = null
        private var meleeDistance = 0.0f

        //
        // muzzle flash
        private var muzzleFlash // positioned on view weapon bone
                : renderLight_s
        private var muzzleFlashEnd: Int
        private var muzzleFlashHandle: Int
        private var muzzle_kick_maxtime = 0
        private var muzzle_kick_time = 0
        private var nextStrikeFx // used for sound and decal ( may use for strike smoke too )
                = 0

        //
        // nozzle effects
        private var nozzleFx // does this use nozzle effects ( parm5 at rest, parm6 firing )
                = false

        // this also assumes a nozzle light atm
        private var nozzleFxFade // time it takes to fade between the effects
                = 0
        private var nozzleGlow // nozzle light
                : renderLight_s
        private var nozzleGlowHandle // handle for nozzle light
                : Int
        private var nozzleGlowRadius // radius of glow light
                = 0.0f
        private var nozzleGlowShader // shader for glow light
                : Material.idMaterial? = null

        //
        private var owner: idPlayer? = null
        private var powerAmmo // true if the clip reduction is a factor of the power setting when
                = false

        //
        // precreated projectile
        private var projectileEnt: idEntity? = null
        private var silent_fire = false

        //
        // sound
        private var sndHum: idSoundShader? = null
        private var status: weaponStatus_t = weaponStatus_t.WP_HOLSTERED
        private val strikeAxis // axis of last melee strike
                : idMat3 = idMat3()
        private var strikeSmoke // striking something in melee
                : idDeclParticle? = null
        private var strikeSmokeStartTime // timing
                = 0
        private var thread: idThread?
        private var   /*jointHandle_t*/ventLightJointView = 0

        // weighting for viewmodel angles
        private var weaponAngleOffsetAverages = 0
        private var weaponAngleOffsetMax = 0.0f
        private var weaponAngleOffsetScale = 0.0f

        // weapon definition
        // we maintain local copies of the projectile and brass dictionaries so they
        // do not have to be copied across the DLL boundary when entities are spawned
        private var weaponDef: idDeclEntityDef?
        private var weaponOffsetScale = 0.0f
        private var weaponOffsetTime = 0.0f

        // new style muzzle smokes
        private var weaponSmoke // null if it doesn't smoke
                : idDeclParticle? = null
        private var weaponSmokeStartTime // set to gameLocal.time every weapon fire
                = 0

        private var worldMuzzleFlash // positioned on world weapon bone
                : renderLight_s
        private var worldMuzzleFlashHandle: Int

        // zoom
        private var zoomFov // variable zoom fov per weapon
                = 0

        // Init

        /*
         ================
         idWeapon::Spawn
         ================
         */
        override fun Spawn() {
            super.Spawn()
            if (!Game_local.gameLocal.isClient) {
                // setup the world model
                worldModel.oSet(
                    Game_local.gameLocal.SpawnEntityType(
                        idAnimatedEntity.Type,
                        null
                    ) as idAnimatedEntity
                )
                worldModel.GetEntity()!!.fl.networkSync = true
            }
            thread = idThread()
            thread!!.ManualDelete()
            thread!!.ManualControl()
        }

        /*
         ================
         idWeapon::SetOwner

         Only called at player spawn time, not each weapon switch
         ================
         */
        fun SetOwner(_owner: idPlayer) {
            assert(null == owner)
            owner = _owner
            SetName(Str.va("%s_weapon", owner!!.name))
            if (worldModel.GetEntity() != null) {
                worldModel.GetEntity()!!.SetName(Str.va("%s_weapon_worldmodel", owner!!.name))
            }
        }

        /*
         ================
         idWeapon::GetOwner
         ================
         */
        fun GetOwner(): idPlayer? {
            return owner
        }

        /*
         ================
         idWeapon::ShouldConstructScriptObjectAtSpawn

         Called during idEntity::Spawn to see if it should construct the script object or not.
         Overridden by subclasses that need to spawn the script object themselves.
         ================
         */
        // FIX: Method name was corrupted from "Construct" to "finalruct"
        override fun ShouldConstructScriptObjectAtSpawn(): Boolean {
            return false
        }

        /*
         ================
         idWeapon::Save
         ================
         */
        // save games
        override fun Save(savefile: idSaveGame) {                    // archives object for save game file
            super.Save(savefile)
            savefile.WriteInt(TempDump.etoi(status))
            savefile.WriteObject(thread)
            savefile.WriteString(state)
            savefile.WriteString(idealState)
            savefile.WriteInt(animBlendFrames)
            savefile.WriteInt(animDoneTime)
            savefile.WriteBool(isLinked)
            savefile.WriteObject(owner)
            worldModel.Save(savefile)
            savefile.WriteInt(hideTime)
            savefile.WriteFloat(hideDistance)
            savefile.WriteInt(hideStartTime)
            savefile.WriteFloat(hideStart)
            savefile.WriteFloat(hideEnd)
            savefile.WriteFloat(hideOffset)
            savefile.WriteBool(hide)
            savefile.WriteBool(disabled)
            savefile.WriteInt(berserk)
            savefile.WriteVec3(playerViewOrigin)
            savefile.WriteMat3(playerViewAxis)
            savefile.WriteVec3(viewWeaponOrigin)
            savefile.WriteMat3(viewWeaponAxis)
            savefile.WriteVec3(muzzleOrigin)
            savefile.WriteMat3(muzzleAxis)
            savefile.WriteVec3(pushVelocity)
            savefile.WriteString(weaponDef!!.GetName())
            savefile.WriteFloat(meleeDistance)
            savefile.WriteString(meleeDefName)
            savefile.WriteInt(brassDelay)
            savefile.WriteString(icon)
            savefile.WriteInt(guiLightHandle)
            savefile.WriteRenderLight(guiLight)
            savefile.WriteInt(muzzleFlashHandle)
            savefile.WriteRenderLight(muzzleFlash)
            savefile.WriteInt(worldMuzzleFlashHandle)
            savefile.WriteRenderLight(worldMuzzleFlash)
            savefile.WriteVec3(flashColor)
            savefile.WriteInt(muzzleFlashEnd)
            savefile.WriteInt(flashTime)
            savefile.WriteBool(lightOn)
            savefile.WriteBool(silent_fire)
            savefile.WriteInt(kick_endtime)
            savefile.WriteInt(muzzle_kick_time)
            savefile.WriteInt(muzzle_kick_maxtime)
            savefile.WriteAngles(muzzle_kick_angles)
            savefile.WriteVec3(muzzle_kick_offset)
            savefile.WriteInt(ammoType)
            savefile.WriteInt(ammoRequired)
            savefile.WriteInt(clipSize)
            savefile.WriteInt(ammoClip)
            savefile.WriteInt(lowAmmo)
            savefile.WriteBool(powerAmmo)

            // savegames <= 17
            savefile.WriteInt(0)
            savefile.WriteInt(zoomFov)
            savefile.WriteJoint(barrelJointView)
            savefile.WriteJoint(flashJointView)
            savefile.WriteJoint(ejectJointView)
            savefile.WriteJoint(guiLightJointView)
            savefile.WriteJoint(ventLightJointView)
            savefile.WriteJoint(flashJointWorld)
            savefile.WriteJoint(barrelJointWorld)
            savefile.WriteJoint(ejectJointWorld)
            savefile.WriteBool(hasBloodSplat)
            savefile.WriteSoundShader(sndHum)
            savefile.WriteParticle(weaponSmoke)
            savefile.WriteInt(weaponSmokeStartTime)
            savefile.WriteBool(continuousSmoke)
            savefile.WriteParticle(strikeSmoke)
            savefile.WriteInt(strikeSmokeStartTime)
            savefile.WriteVec3(strikePos)
            savefile.WriteMat3(strikeAxis)
            savefile.WriteInt(nextStrikeFx)
            savefile.WriteBool(nozzleFx)
            savefile.WriteInt(nozzleFxFade)
            savefile.WriteInt(lastAttack)
            savefile.WriteInt(nozzleGlowHandle)
            savefile.WriteRenderLight(nozzleGlow)
            savefile.WriteVec3(nozzleGlowColor)
            savefile.WriteMaterial(nozzleGlowShader)
            savefile.WriteFloat(nozzleGlowRadius)
            savefile.WriteInt(weaponAngleOffsetAverages)
            savefile.WriteFloat(weaponAngleOffsetScale)
            savefile.WriteFloat(weaponAngleOffsetMax)
            savefile.WriteFloat(weaponOffsetTime)
            savefile.WriteFloat(weaponOffsetScale)
            savefile.WriteBool(allowDrop)
            savefile.WriteObject(projectileEnt)
        }

        /*
         ================
         idWeapon::Restore
         ================
         */
        override fun Restore(savefile: idRestoreGame) {                    // unarchives object from save game file
            super.Restore(savefile)
            status = weaponStatus_t.values()[savefile.ReadInt()]
            thread = savefile.ReadObject() as idThread?
            savefile.ReadString(state)
            savefile.ReadString(idealState)
            animBlendFrames = savefile.ReadInt()
            animDoneTime = savefile.ReadInt()
            isLinked = savefile.ReadBool()

            // Re-link script fields
            WEAPON_ATTACK.LinkTo(scriptObject, "WEAPON_ATTACK")
            WEAPON_RELOAD.LinkTo(scriptObject, "WEAPON_RELOAD")
            WEAPON_NETRELOAD.LinkTo(scriptObject, "WEAPON_NETRELOAD")
            WEAPON_NETENDRELOAD.LinkTo(scriptObject, "WEAPON_NETENDRELOAD")
            if (!ID_DEMO_BUILD) {
                WEAPON_NETFIRING.LinkTo(scriptObject, "WEAPON_NETFIRING")
            }
            WEAPON_RAISEWEAPON.LinkTo(scriptObject, "WEAPON_RAISEWEAPON")
            WEAPON_LOWERWEAPON.LinkTo(scriptObject, "WEAPON_LOWERWEAPON")
            owner = savefile.ReadObject() as idPlayer?
            worldModel.Restore(savefile)
            hideTime = savefile.ReadInt()
            hideDistance = savefile.ReadFloat()
            hideStartTime = savefile.ReadInt()
            hideStart = savefile.ReadFloat()
            hideEnd = savefile.ReadFloat()
            hideOffset = savefile.ReadFloat()
            hide = savefile.ReadBool()
            disabled = savefile.ReadBool()
            berserk = savefile.ReadInt()
            savefile.ReadVec3(playerViewOrigin)
            savefile.ReadMat3(playerViewAxis)
            savefile.ReadVec3(viewWeaponOrigin)
            savefile.ReadMat3(viewWeaponAxis)
            savefile.ReadVec3(muzzleOrigin)
            savefile.ReadMat3(muzzleAxis)
            savefile.ReadVec3(pushVelocity)
            val objectname = idStr()
            savefile.ReadString(objectname)
            weaponDef = Game_local.gameLocal.FindEntityDef(objectname.toString())
            meleeDef = Game_local.gameLocal.FindEntityDef(weaponDef!!.dict.GetString("def_melee"), false)
            val projectileDef = Game_local.gameLocal.FindEntityDef(weaponDef!!.dict.GetString("def_projectile"), false)
            if (projectileDef != null) {
                projectileDict.set(projectileDef.dict)
            } else {
                projectileDict.Clear()
            }
            val brassDef = Game_local.gameLocal.FindEntityDef(weaponDef!!.dict.GetString("def_ejectBrass"), false)
            if (brassDef != null) {
                brassDict.set(brassDef.dict)
            } else {
                brassDict.Clear()
            }
            meleeDistance = savefile.ReadFloat()
            savefile.ReadString(meleeDefName)
            brassDelay = savefile.ReadInt()
            savefile.ReadString(icon)
            guiLightHandle = savefile.ReadInt()
            savefile.ReadRenderLight(guiLight)
            // FIX: Get fresh light handle after loading (dhewm3 fix — stale handles point to wrong lights)
            if (guiLightHandle != -1) {
                guiLightHandle = Game_local.gameRenderWorld!!.AddLightDef(guiLight)
            }
            muzzleFlashHandle = savefile.ReadInt()
            savefile.ReadRenderLight(muzzleFlash)
            // FIX: Get fresh light handle after loading (dhewm3 fix)
            if (muzzleFlashHandle != -1) {
                muzzleFlashHandle = Game_local.gameRenderWorld!!.AddLightDef(muzzleFlash)
            }
            worldMuzzleFlashHandle = savefile.ReadInt()
            savefile.ReadRenderLight(worldMuzzleFlash)
            // FIX: Get fresh light handle after loading (dhewm3 fix)
            if (worldMuzzleFlashHandle != -1) {
                worldMuzzleFlashHandle = Game_local.gameRenderWorld!!.AddLightDef(worldMuzzleFlash)
            }
            savefile.ReadVec3(flashColor)
            muzzleFlashEnd = savefile.ReadInt()
            flashTime = savefile.ReadInt()
            lightOn = savefile.ReadBool()
            silent_fire = savefile.ReadBool()
            kick_endtime = savefile.ReadInt()
            muzzle_kick_time = savefile.ReadInt()
            muzzle_kick_maxtime = savefile.ReadInt()
            savefile.ReadAngles(muzzle_kick_angles)
            savefile.ReadVec3(muzzle_kick_offset)
            ammoType = savefile.ReadInt()
            ammoRequired = savefile.ReadInt()
            clipSize = savefile.ReadInt()
            ammoClip = savefile.ReadInt()
            lowAmmo = savefile.ReadInt()
            powerAmmo = savefile.ReadBool()

            // savegame versions <= 17
            val foo: Int
            foo = savefile.ReadInt()
            zoomFov = savefile.ReadInt()
            barrelJointView = savefile.ReadJoint()
            flashJointView = savefile.ReadJoint()
            ejectJointView = savefile.ReadJoint()
            guiLightJointView = savefile.ReadJoint()
            ventLightJointView = savefile.ReadJoint()
            flashJointWorld = savefile.ReadJoint()
            barrelJointWorld = savefile.ReadJoint()
            ejectJointWorld = savefile.ReadJoint()
            hasBloodSplat = savefile.ReadBool()
            sndHum = savefile.ReadSoundShader()
            weaponSmoke = savefile.ReadParticle()
            weaponSmokeStartTime = savefile.ReadInt()
            continuousSmoke = savefile.ReadBool()
            strikeSmoke = savefile.ReadParticle()
            strikeSmokeStartTime = savefile.ReadInt()
            savefile.ReadVec3(strikePos)
            savefile.ReadMat3(strikeAxis)
            nextStrikeFx = savefile.ReadInt()
            nozzleFx = savefile.ReadBool()
            nozzleFxFade = savefile.ReadInt()
            lastAttack = savefile.ReadInt()
            nozzleGlowHandle = savefile.ReadInt()
            savefile.ReadRenderLight(nozzleGlow)
            // FIX: Get fresh light handle after loading (dhewm3 fix)
            if (nozzleGlowHandle != -1) {
                nozzleGlowHandle = Game_local.gameRenderWorld!!.AddLightDef(nozzleGlow)
            }
            savefile.ReadVec3(nozzleGlowColor)
            // FIX: nozzleGlowShader may be null; create temp object for ReadMaterial which requires non-null
            nozzleGlowShader = savefile.ReadMaterial()
            nozzleGlowRadius = savefile.ReadFloat()
            weaponAngleOffsetAverages = savefile.ReadInt()
            weaponAngleOffsetScale = savefile.ReadFloat()
            weaponAngleOffsetMax = savefile.ReadFloat()
            weaponOffsetTime = savefile.ReadFloat()
            weaponOffsetScale = savefile.ReadFloat()
            allowDrop = savefile.ReadBool()
            projectileEnt = savefile.ReadObject() as idEntity?
        }

        /* **********************************************************************

         Weapon definition management

         ***********************************************************************/
        /*
         ================
         idWeapon::Clear
         ================
         */
        fun Clear() {
            CancelEvents(EV_Weapon_Clear)
            DeconstructScriptObject()
            scriptObject.Free()
            WEAPON_ATTACK.Unlink()
            WEAPON_RELOAD.Unlink()
            WEAPON_NETRELOAD.Unlink()
            WEAPON_NETENDRELOAD.Unlink()
            // FIX: Added IsLinked() guard — WEAPON_NETFIRING is not linked in demo mode
            if (WEAPON_NETFIRING.IsLinked()) {
                WEAPON_NETFIRING.Unlink()
            }
            WEAPON_RAISEWEAPON.Unlink()
            WEAPON_LOWERWEAPON.Unlink()
            if (muzzleFlashHandle != -1) {
                Game_local.gameRenderWorld!!.FreeLightDef(muzzleFlashHandle)
                muzzleFlashHandle = -1
            }
            if (muzzleFlashHandle != -1) {
                Game_local.gameRenderWorld!!.FreeLightDef(muzzleFlashHandle)
                muzzleFlashHandle = -1
            }
            if (worldMuzzleFlashHandle != -1) {
                Game_local.gameRenderWorld!!.FreeLightDef(worldMuzzleFlashHandle)
                worldMuzzleFlashHandle = -1
            }
            if (guiLightHandle != -1) {
                Game_local.gameRenderWorld!!.FreeLightDef(guiLightHandle)
                guiLightHandle = -1
            }
            if (nozzleGlowHandle != -1) {
                Game_local.gameRenderWorld!!.FreeLightDef(nozzleGlowHandle)
                nozzleGlowHandle = -1
            }

            renderEntity = renderEntity_s()
            renderEntity!!.entityNum = entityNumber
            renderEntity!!.noShadow = true
            renderEntity!!.noSelfShadow = true
            renderEntity!!.customSkin = null

            // set default shader parms
            renderEntity!!.shaderParms[RenderWorld.SHADERPARM_RED] = 1.0f
            renderEntity!!.shaderParms[RenderWorld.SHADERPARM_GREEN] = 1.0f
            renderEntity!!.shaderParms[RenderWorld.SHADERPARM_BLUE] = 1.0f
            renderEntity!!.shaderParms[3] = 1.0f
            renderEntity!!.shaderParms[RenderWorld.SHADERPARM_TIMEOFFSET] = 0.0f
            renderEntity!!.shaderParms[5] = 0.0f
            renderEntity!!.shaderParms[6] = 0.0f
            renderEntity!!.shaderParms[7] = 0.0f
            if (refSound.referenceSound != null) {
                refSound.referenceSound!!.Free(true)
            }
            refSound = refSound_t()

            // setting diversity to 0 results in no random sound.  -1 indicates random.
            refSound.diversity = -1.0f
            if (owner != null) {
                // don't spatialize the weapon sounds
                refSound.listenerId = owner!!.GetListenerId()
            }

            // clear out the sounds from our spawnargs since we'll copy them from the weapon def
            var kv = spawnArgs.MatchPrefix("snd_")
            while (kv != null) {
                spawnArgs.Delete(kv.GetKey())
                kv = spawnArgs.MatchPrefix("snd_")
            }
            hideTime = 300
            hideDistance = -15.0f
            hideStartTime = Game_local.gameLocal.time - hideTime
            hideStart = 0.0f
            hideEnd = 0.0f
            hideOffset = 0.0f
            hide = false
            disabled = false
            weaponSmoke = null
            weaponSmokeStartTime = 0
            continuousSmoke = false
            strikeSmoke = null
            strikeSmokeStartTime = 0
            strikePos.Zero()
            strikeAxis.set(idMat3.getMat3_identity())
            nextStrikeFx = 0
            icon.set("")
            playerViewAxis.Identity()
            playerViewOrigin.Zero()
            viewWeaponAxis.Identity()
            viewWeaponOrigin.Zero()
            muzzleAxis.Identity()
            muzzleOrigin.Zero()
            pushVelocity.Zero()
            status = weaponStatus_t.WP_HOLSTERED
            state.set("")
            idealState.set("")
            animBlendFrames = 0
            animDoneTime = 0
            projectileDict.Clear()
            meleeDef = null
            meleeDefName.set("")
            meleeDistance = 0.0f
            brassDict.Clear()
            flashTime = 250
            lightOn = false
            silent_fire = false
            ammoType = 0
            ammoRequired = 0
            ammoClip = 0
            clipSize = 0
            lowAmmo = 0
            powerAmmo = false
            kick_endtime = 0
            muzzle_kick_time = 0
            muzzle_kick_maxtime = 0
            muzzle_kick_angles.Zero()
            muzzle_kick_offset.Zero()
            zoomFov = 90
            barrelJointView = Model.INVALID_JOINT
            flashJointView = Model.INVALID_JOINT
            ejectJointView = Model.INVALID_JOINT
            guiLightJointView = Model.INVALID_JOINT
            ventLightJointView = Model.INVALID_JOINT
            barrelJointWorld = Model.INVALID_JOINT
            flashJointWorld = Model.INVALID_JOINT
            ejectJointWorld = Model.INVALID_JOINT
            hasBloodSplat = false
            nozzleFx = false
            nozzleFxFade = 1500
            lastAttack = 0
            nozzleGlowHandle = -1
            nozzleGlowShader = null
            nozzleGlowRadius = 10.0f
            nozzleGlowColor.Zero()
            weaponAngleOffsetAverages = 0
            weaponAngleOffsetScale = 0.0f
            weaponAngleOffsetMax = 0.0f
            weaponOffsetTime = 0.0f
            weaponOffsetScale = 0.0f
            allowDrop = true
            animator.ClearAllAnims(Game_local.gameLocal.time, 0)
            FreeModelDef()
            sndHum = null
            isLinked = false
            projectileEnt = null
            isFiring = false
        }

        /*
         ================
         idWeapon::GetWeaponDef
         ================
         */
        fun GetWeaponDef(objectName: String?, ammoinclip: Int) {
            val shader = arrayOfNulls<String>(1)
            val objectType = arrayOfNulls<String>(1)
            val vmodel: String?
            val guiName: String?
            val projectileName: String?
            val brassDefName: String?
            var smokeName: String?
            val ammoAvail: Int
            Clear()
            if (objectName == null || objectName.isNullOrEmpty()) { //|| !objectname[ 0 ] ) {
                return
            }
            assert(owner != null)
            weaponDef = Game_local.gameLocal.FindEntityDef(objectName)
            ammoType = GetAmmoNumForName(weaponDef!!.dict.GetString("ammoType"))
            ammoRequired = weaponDef!!.dict.GetInt("ammoRequired")
            clipSize = weaponDef!!.dict.GetInt("clipSize")
            lowAmmo = weaponDef!!.dict.GetInt("lowAmmo")
            icon.set(weaponDef!!.dict.GetString("icon"))
            silent_fire = weaponDef!!.dict.GetBool("silent_fire")
            powerAmmo = weaponDef!!.dict.GetBool("powerAmmo")
            muzzle_kick_time = SEC2MS(weaponDef!!.dict.GetFloat("muzzle_kick_time"))
            muzzle_kick_maxtime = SEC2MS(weaponDef!!.dict.GetFloat("muzzle_kick_maxtime"))
            muzzle_kick_angles.set(weaponDef!!.dict.GetAngles("muzzle_kick_angles"))
            muzzle_kick_offset.set(weaponDef!!.dict.GetVector("muzzle_kick_offset"))
            hideTime = SEC2MS(weaponDef!!.dict.GetFloat("hide_time", "0.3"))
            hideDistance = weaponDef!!.dict.GetFloat("hide_distance", "-15")

            // muzzle smoke
            smokeName = weaponDef!!.dict.GetString("smoke_muzzle")
            weaponSmoke = if (smokeName.isNotEmpty()) {
                DeclManager.declManager.FindType(declType_t.DECL_PARTICLE, smokeName) as idDeclParticle
            } else {
                null
            }
            continuousSmoke = weaponDef!!.dict.GetBool("continuousSmoke")
            weaponSmokeStartTime = if (continuousSmoke) Game_local.gameLocal.time else 0
            smokeName = weaponDef!!.dict.GetString("smoke_strike")
            strikeSmoke = if (smokeName.isNotEmpty()) {
                DeclManager.declManager.FindType(declType_t.DECL_PARTICLE, smokeName) as idDeclParticle
            } else {
                null
            }
            strikeSmokeStartTime = 0
            strikePos.Zero()
            strikeAxis.set(idMat3.getMat3_identity())
            nextStrikeFx = 0

            // setup gui light
            guiLight = renderLight_s()
            val guiLightShader = weaponDef!!.dict.GetString("mtr_guiLightShader")
            if (guiLightShader.isNotEmpty()) {
                guiLight.shader = DeclManager.declManager.FindMaterial(guiLightShader, false)
                guiLight.lightRadius[0] = guiLight.lightRadius.set(1, guiLight.lightRadius.set(2, 3.0f))
                guiLight.pointLight._val = true
            }

            // setup the view model
            vmodel = weaponDef!!.dict.GetString("model_view")
            SetModel(vmodel)

            // setup the world model
            InitWorldModel(weaponDef!!)

            // copy the sounds from the weapon view model def into out spawnargs
            var kv = weaponDef!!.dict.MatchPrefix("snd_")
            while (kv != null) {
                spawnArgs.Set(kv.GetKey(), kv.GetValue())
                kv = weaponDef!!.dict.MatchPrefix("snd_", kv)
            }

            // find some joints in the model for locating effects
            barrelJointView = animator.GetJointHandle("barrel")
            flashJointView = animator.GetJointHandle("flash")
            ejectJointView = animator.GetJointHandle("eject")
            guiLightJointView = animator.GetJointHandle("guiLight")
            ventLightJointView = animator.GetJointHandle("ventLight")

            // get the projectile
            projectileDict.Clear()
            projectileName = weaponDef!!.dict.GetString("def_projectile")
            if (projectileName.isNotEmpty()) {
                val projectileDef = Game_local.gameLocal.FindEntityDef(projectileName, false)
                if (null == projectileDef) {
                    Game_local.gameLocal.Warning("Unknown projectile '%s' in weapon '%s'", projectileName, objectName)
                } else {
                    val spawnclass = projectileDef.dict.GetString("spawnclass")
                    // NOTE: Differs from C++ — original uses idClass::GetClass(spawnclass)->IsType() to check
                    // type metadata without constructing an entity. Kotlin creates a throwaway entity via GetEntity().
                    val spawnEntity: idEntity = GetClass(spawnclass)!!.createInstance.invoke() as idEntity
                    if (spawnEntity !is idProjectile) {
                        Game_local.gameLocal.Warning(
                            "Invalid spawnclass '%s' on projectile '%s' (used by weapon '%s')",
                            spawnclass,
                            projectileName,
                            objectName
                        )
                    } else {
                        projectileDict.set(projectileDef.dict)
                    }
                }
            }

            // set up muzzleflash render light
            val flashShader: Material.idMaterial?
            val flashTarget = idVec3()
            val flashUp = idVec3()
            val flashRight = idVec3()
            val flashRadius: Float
            val flashPointLight: Boolean
            weaponDef!!.dict.GetString("mtr_flashShader", "", shader)
            flashShader = DeclManager.declManager.FindMaterial(shader[0]!!, false)
            flashPointLight = weaponDef!!.dict.GetBool("flashPointLight", "1")
            weaponDef!!.dict.GetVector("flashColor", "0 0 0", flashColor)
            flashRadius = weaponDef!!.dict.GetInt("flashRadius").toFloat() // if 0, no light will spawn
            flashTime = SEC2MS(weaponDef!!.dict.GetFloat("flashTime", "0.25"))
            flashTarget.set(weaponDef!!.dict.GetVector("flashTarget"))
            flashUp.set(weaponDef!!.dict.GetVector("flashUp"))
            flashRight.set(weaponDef!!.dict.GetVector("flashRight"))
            muzzleFlash = renderLight_s()
            muzzleFlash.lightId.integerValue = LIGHTID_VIEW_MUZZLE_FLASH + owner!!.entityNumber
            muzzleFlash.allowLightInViewID.integerValue = owner!!.entityNumber + 1

            // the weapon lights will only be in first person
            guiLight.allowLightInViewID.integerValue = owner!!.entityNumber + 1
            nozzleGlow.allowLightInViewID.integerValue = owner!!.entityNumber + 1
            muzzleFlash.pointLight._val = flashPointLight
            muzzleFlash.shader = flashShader
            muzzleFlash.shaderParms[RenderWorld.SHADERPARM_RED] = flashColor[0]
            muzzleFlash.shaderParms[RenderWorld.SHADERPARM_GREEN] = flashColor[1]
            muzzleFlash.shaderParms[RenderWorld.SHADERPARM_BLUE] = flashColor[2]
            muzzleFlash.shaderParms[RenderWorld.SHADERPARM_TIMESCALE] = 1.0f
            muzzleFlash.lightRadius[0] = flashRadius
            muzzleFlash.lightRadius[1] = flashRadius
            muzzleFlash.lightRadius[2] = flashRadius
            if (!flashPointLight) {
                muzzleFlash.target.set(flashTarget)
                muzzleFlash.up.set(flashUp)
                muzzleFlash.right.set(flashRight)
                muzzleFlash.end.set(flashTarget)
            }

            // the world muzzle flash is the same, just positioned differently
            worldMuzzleFlash = renderLight_s(muzzleFlash)
            worldMuzzleFlash.suppressLightInViewID.integerValue = owner!!.entityNumber + 1
            worldMuzzleFlash.allowLightInViewID.integerValue = 0
            worldMuzzleFlash.lightId.integerValue = LIGHTID_WORLD_MUZZLE_FLASH + owner!!.entityNumber

            //-----------------------------------
            nozzleFx = weaponDef!!.dict.GetBool("nozzleFx")
            nozzleFxFade = weaponDef!!.dict.GetInt("nozzleFxFade", "1500")
            nozzleGlowColor.set(weaponDef!!.dict.GetVector("nozzleGlowColor", "1 1 1"))
            nozzleGlowRadius = weaponDef!!.dict.GetFloat("nozzleGlowRadius", "10")
            weaponDef!!.dict.GetString("mtr_nozzleGlowShader", "", shader)
            nozzleGlowShader = DeclManager.declManager.FindMaterial(shader[0]!!, false)

            // get the melee damage def
            meleeDistance = weaponDef!!.dict.GetFloat("melee_distance")
            meleeDefName.set(weaponDef!!.dict.GetString("def_melee"))
            if (meleeDefName.Length() != 0) {
                meleeDef = Game_local.gameLocal.FindEntityDef(meleeDefName.toString(), false)
                if (null == meleeDef) {
                    idGameLocal.Error("Unknown melee '%s'", meleeDefName)
                }
            }

            // get the brass def
            brassDict.Clear()
            brassDelay = weaponDef!!.dict.GetInt("ejectBrassDelay", "0")
            brassDefName = weaponDef!!.dict.GetString("def_ejectBrass")
            if (brassDefName.isNotEmpty()) {
                val brassDef = Game_local.gameLocal.FindEntityDef(brassDefName, false)
                if (null == brassDef) {
                    Game_local.gameLocal.Warning("Unknown brass '%s'", brassDefName)
                } else {
                    brassDict.set(brassDef.dict)
                }
            }
            if (ammoType < 0 || ammoType >= AMMO_NUMTYPES) {
                Game_local.gameLocal.Warning("Unknown ammotype in object '%s'", objectName)
            }
            ammoClip = ammoinclip
            if (ammoClip < 0 || ammoClip > clipSize) {
                // first time using this weapon so have it fully loaded to start
                ammoClip = clipSize
                ammoAvail = owner!!.inventory.HasAmmo(ammoType, ammoRequired)
                if (ammoClip > ammoAvail) {
                    ammoClip = ammoAvail
                }
            }
            renderEntity!!.gui[0] = null
            guiName = weaponDef!!.dict.GetString("gui")
            if (guiName.isNotEmpty()) {
                renderEntity!!.gui[0] = UserInterface.uiManager.FindGui(guiName, true, false, true)!!
            }
            zoomFov = weaponDef!!.dict.GetInt("zoomFov", "70")
            berserk = weaponDef!!.dict.GetInt("berserk", "2")
            weaponAngleOffsetAverages = weaponDef!!.dict.GetInt("weaponAngleOffsetAverages", "10")
            weaponAngleOffsetScale = weaponDef!!.dict.GetFloat("weaponAngleOffsetScale", "0.25")
            weaponAngleOffsetMax = weaponDef!!.dict.GetFloat("weaponAngleOffsetMax", "10")
            weaponOffsetTime = weaponDef!!.dict.GetFloat("weaponOffsetTime", "400")
            weaponOffsetScale = weaponDef!!.dict.GetFloat("weaponOffsetScale", "0.005")
            if (!weaponDef!!.dict.GetString("weapon_scriptobject", "", objectType)) {
                idGameLocal.Error("No 'weapon_scriptobject' set on '%s'.", objectName)
            }

            // setup script object
            if (!scriptObject.SetType(objectType[0])) {
                idGameLocal.Error("Script object '%s' not found on weapon '%s'.", objectType[0], objectName)
            }
            WEAPON_ATTACK.LinkTo(scriptObject, "WEAPON_ATTACK")
            WEAPON_RELOAD.LinkTo(scriptObject, "WEAPON_RELOAD")
            WEAPON_NETRELOAD.LinkTo(scriptObject, "WEAPON_NETRELOAD")
            WEAPON_NETENDRELOAD.LinkTo(scriptObject, "WEAPON_NETENDRELOAD")
            if (!ID_DEMO_BUILD) WEAPON_NETFIRING.LinkTo(scriptObject, "WEAPON_NETFIRING")
            WEAPON_RAISEWEAPON.LinkTo(scriptObject, "WEAPON_RAISEWEAPON")
            WEAPON_LOWERWEAPON.LinkTo(scriptObject, "WEAPON_LOWERWEAPON")
            spawnArgs.set(weaponDef!!.dict)
            shader[0] = spawnArgs.GetString("snd_hum")
            if (!shader[0].isNullOrEmpty()) {
                sndHum = DeclManager.declManager.FindSound(shader[0]!!)
                StartSoundShader(sndHum, gameSoundChannel_t.SND_CHANNEL_BODY.ordinal, 0, false)
            }
            isLinked = true

            // call script object's constructor
            ConstructScriptObject()

            // make sure we have the correct skin
            UpdateSkin()
        }

        /*
         ================
         idWeapon::IsLinked
         ================
         */
        fun IsLinked(): Boolean {
            return isLinked
        }

        /*
         ================
         idWeapon::IsWorldModelReady
         ================
         */
        fun IsWorldModelReady(): Boolean {
            return worldModel.GetEntity() != null
        }

        /* **********************************************************************

         GUIs

         ***********************************************************************/
        /*
         ================
         idWeapon::Icon
         ================
         */
        fun Icon(): String {
            return icon.toString()
        }

        /*
         ================
         idWeapon::UpdateGUI
         ================
         */
        fun UpdateGUI() {
            if (null == renderEntity!!.gui[0]) {
                return
            }
            if (status == weaponStatus_t.WP_HOLSTERED) {
                return
            }
            if (owner!!.weaponGone) {
                // dropping weapons was implemented wierd, so we have to not update the gui when it happens or we'll get a negative ammo count
                return
            }
            if (Game_local.gameLocal.localClientNum != owner!!.entityNumber) {
                // if updating the hud for a followed client
                if (Game_local.gameLocal.localClientNum >= 0 && Game_local.gameLocal.entities[Game_local.gameLocal.localClientNum] != null && Game_local.gameLocal.entities[Game_local.gameLocal.localClientNum] is idPlayer) {
                    val p = Game_local.gameLocal.entities[Game_local.gameLocal.localClientNum] as idPlayer
                    if (!p.spectating || p.spectator != owner!!.entityNumber) {
                        return
                    }
                } else {
                    return
                }
            }
            val inclip = AmmoInClip()
            val ammoamount = AmmoAvailable()
            if (ammoamount < 0) {
                // show infinite ammo
                renderEntity!!.gui[0]!!.SetStateString("player_ammo", "")
            } else {
                // show remaining ammo
                renderEntity!!.gui[0]!!.SetStateString("player_totalammo", Str.va("%d", ammoamount - inclip))
                renderEntity!!.gui[0]!!.SetStateString(
                    "player_ammo",
                    if (ClipSize() != 0) Str.va("%d", inclip) else "--"
                )
                renderEntity!!.gui[0]!!.SetStateString(
                    "player_clips",
                    if (ClipSize() != 0) Str.va("%d", ammoamount / ClipSize()) else "--"
                )
                renderEntity!!.gui[0]!!.SetStateString("player_allammo", Str.va("%d/%d", inclip, ammoamount - inclip))
            }
            renderEntity!!.gui[0]!!.SetStateBool("player_ammo_empty", ammoamount == 0)
            renderEntity!!.gui[0]!!.SetStateBool("player_clip_empty", inclip == 0)
            renderEntity!!.gui[0]!!.SetStateBool("player_clip_low", inclip <= lowAmmo)
        }

        /*
         ================
         idWeapon::SetModel
         ================
         */
        override fun SetModel(modelname: String) {
            assert(modelname != null)
            if (modelDefHandle >= 0) {
                Game_local.gameRenderWorld!!.RemoveDecals(modelDefHandle)
            }
            renderEntity!!.hModel = animator.SetModel(modelname)
            if (renderEntity!!.hModel != null) {
                renderEntity!!.customSkin = animator.ModelDef()!!.GetDefaultSkin()
                renderEntity!!.numJoints = animator.GetJoints(renderEntity!!)
            } else {
                renderEntity!!.customSkin = null
                renderEntity!!.callback = null
                renderEntity!!.numJoints = 0
                renderEntity!!.joints = null
            }

            // hide the model until an animation is played
            Hide()
        }

        /*
         ================
         idWeapon::GetGlobalJointTransform

         This returns the offset and axis of a weapon bone in world space, suitable for attaching models or lights
         ================
         */
        fun GetGlobalJointTransform(
            viewModel: Boolean,    /*jointHandle_t*/
            jointHandle: Int,
            offset: idVec3,
            axis: idMat3
        ): Boolean {
            if (viewModel) {
                // view model
                if (animator.GetJointTransform(jointHandle, Game_local.gameLocal.time, offset, axis)) {
                    offset.set(offset * viewWeaponAxis + viewWeaponOrigin)
                    axis.set(axis * viewWeaponAxis)
                    return true
                }
            } else {
                // world model
                if (worldModel.GetEntity() != null && worldModel.GetEntity()!!.GetAnimator()
                        .GetJointTransform(jointHandle, Game_local.gameLocal.time, offset, axis)
                ) {
                    offset.set(
                        worldModel.GetEntity()!!.GetPhysics().GetOrigin() + offset * worldModel.GetEntity()!!
                            .GetPhysics().GetAxis()
                    )
                    axis.set(axis * worldModel.GetEntity()!!.GetPhysics().GetAxis())
                    return true
                }
            }
            offset.set(viewWeaponOrigin)
            axis.set(viewWeaponAxis)
            return false
        }

        /*
         ================
         idWeapon::SetPushVelocity
         ================
         */
        fun SetPushVelocity(pushVelocity: idVec3) {
            this.pushVelocity.set(pushVelocity)
        }

        /*
         ================
         idWeapon::UpdateSkin
         ================
         */
        fun UpdateSkin(): Boolean {
            val func: function_t?
            if (!isLinked) {
                return false
            }
            func = scriptObject.GetFunction("UpdateSkin")
            if (null == func) {
                idLib.common.Warning("Can't find function 'UpdateSkin' in object '%s'", scriptObject.GetTypeName())
                return false
            }

            // use the frameCommandThread since it's safe to use outside of framecommands
            Game_local.gameLocal.frameCommandThread!!.CallFunction(this, func, true)
            Game_local.gameLocal.frameCommandThread!!.Execute()
            return true
        }

        /* **********************************************************************

         State control/player interface

         ***********************************************************************/
        /*
         ================
         idWeapon::Think
         ================
         */
        override fun Think() {
            // do nothing because the present is called from the player through PresentWeapon
        }

        /*
         ================
         idWeapon::Raise
         ================
         */
        fun Raise() {
            if (isLinked) {
                WEAPON_RAISEWEAPON.underscore(true)
            }
        }

        /*
         ================
         idWeapon::PutAway
         ================
         */
        fun PutAway() {
            hasBloodSplat = false
            if (isLinked) {
                WEAPON_LOWERWEAPON.underscore(true)
            }
        }

        /*
         ================
         idWeapon::Reload
         NOTE: this is only for impulse-triggered reload, auto reload is scripted
         ================
         */
        fun Reload() {
            if (isLinked) {
                WEAPON_RELOAD.underscore(true)
            }
        }

        /*
         ================
         idWeapon::LowerWeapon
         ================
         */
        fun LowerWeapon() {
            if (!hide) {
                hideStart = 0.0f
                hideEnd = hideDistance
                hideStartTime = if (Game_local.gameLocal.time - hideStartTime < hideTime) {
                    Game_local.gameLocal.time - (hideTime - (Game_local.gameLocal.time - hideStartTime))
                } else {
                    Game_local.gameLocal.time
                }
                hide = true
            }
        }

        /*
         ================
         idWeapon::RaiseWeapon
         ================
         */
        fun RaiseWeapon() {
            Show()
            if (hide) {
                hideStart = hideDistance
                hideEnd = 0.0f
                hideStartTime = if (Game_local.gameLocal.time - hideStartTime < hideTime) {
                    Game_local.gameLocal.time - (hideTime - (Game_local.gameLocal.time - hideStartTime))
                } else {
                    Game_local.gameLocal.time
                }
                hide = false
            }
        }

        /*
         ================
         idWeapon::HideWeapon
         ================
         */
        fun HideWeapon() {
            Hide()
            if (worldModel.GetEntity() != null) {
                worldModel.GetEntity()!!.Hide()
            }
            muzzleFlashEnd = 0
        }

        /*
         ================
         idWeapon::ShowWeapon
         ================
         */
        fun ShowWeapon() {
            Show()
            if (worldModel.GetEntity() != null) {
                worldModel.GetEntity()!!.Show()
            }
            if (lightOn) {
                MuzzleFlashLight()
            }
        }

        /*
         ================
         idWeapon::HideWorldModel
         ================
         */
        fun HideWorldModel() {
            if (worldModel.GetEntity() != null) {
                worldModel.GetEntity()!!.Hide()
            }
        }

        /*
         ================
         idWeapon::ShowWorldModel
         ================
         */
        fun ShowWorldModel() {
            if (worldModel.GetEntity() != null) {
                worldModel.GetEntity()!!.Show()
            }
        }

        /*
         ================
         idWeapon::OwnerDied
         ================
         */
        fun OwnerDied() {
            if (isLinked) {
                SetState("OwnerDied", 0)
                thread!!.Execute()
            }
            Hide()
            if (worldModel.GetEntity() != null) {
                worldModel.GetEntity()!!.Hide()
            }

            // don't clear the weapon immediately since the owner might have killed himself by firing the weapon
            // within the current stack frame
            PostEventMS(EV_Weapon_Clear, 0)
        }

        /*
         ================
         idWeapon::BeginAttack
         ================
         */
        fun BeginAttack() {
            if (status != weaponStatus_t.WP_OUTOFAMMO) {
                lastAttack = Game_local.gameLocal.time
            }
            if (!isLinked) {
                return
            }
            if (!WEAPON_ATTACK.underscore()!!) {
                if (sndHum != null) {
                    StopSound(TempDump.etoi(gameSoundChannel_t.SND_CHANNEL_BODY), false)
                }
            }
            WEAPON_ATTACK.underscore(true)
        }

        /*
         ================
         idWeapon::EndAttack
         ================
         */
        fun EndAttack() {
            if (!WEAPON_ATTACK.IsLinked()) {
                return
            }
            if (WEAPON_ATTACK.underscore()!!) {
                WEAPON_ATTACK.underscore(false)
                if (sndHum != null) {
                    StartSoundShader(sndHum, gameSoundChannel_t.SND_CHANNEL_BODY.ordinal, 0, false)
                }
            }
        }

        /*
         ================
         idWeapon::IsReady
         ================
         */
        fun IsReady(): Boolean {
            return !hide && !IsHidden() && (status == weaponStatus_t.WP_RELOAD || status == weaponStatus_t.WP_READY || status == weaponStatus_t.WP_OUTOFAMMO)
        }

        /*
         ================
         idWeapon::IsReloading
         ================
         */
        fun IsReloading(): Boolean {
            return status == weaponStatus_t.WP_RELOAD
        }

        /*
         ================
         idWeapon::IsHolstered
         ================
         */
        fun IsHolstered(): Boolean {
            return status == weaponStatus_t.WP_HOLSTERED
        }

        /*
         ================
         idWeapon::ShowCrosshair
         ================
         */
        fun ShowCrosshair(): Boolean {
            return !(state.toString() == weaponStatus_t.WP_RISING.name || state.toString() == weaponStatus_t.WP_LOWERING.name || state.toString() == weaponStatus_t.WP_HOLSTERED.name)
        }

        /*
         =====================
         idWeapon::DropItem
         =====================
         */
        fun DropItem(velocity: idVec3, activateDelay: Int, removeDelay: Int, died: Boolean): idEntity? {
            if (null == weaponDef || null == worldModel.GetEntity()) {
                return null
            }
            if (!allowDrop) {
                return null
            }
            val classname = weaponDef!!.dict.GetString("def_dropItem")
            if (classname.isEmpty()) {
                return null
            }
            StopSound(TempDump.etoi(gameSoundChannel_t.SND_CHANNEL_BODY), true)
            StopSound(TempDump.etoi(gameSoundChannel_t.SND_CHANNEL_BODY3), true)
            return idMoveableItem.DropItem(
                classname,
                worldModel.GetEntity()!!.GetPhysics().GetOrigin(),
                worldModel.GetEntity()!!.GetPhysics().GetAxis(),
                velocity,
                activateDelay,
                removeDelay
            )
        }

        /*
         =====================
         idWeapon::CanDrop
         =====================
         */
        fun CanDrop(): Boolean {
            if (null == weaponDef || null == worldModel.GetEntity()) {
                return false
            }
            val classname = weaponDef!!.dict.GetString("def_dropItem")
            return classname.isNotEmpty()
        }

        /*
         ================
         idWeapon::WeaponStolen
         ================
         */
        fun WeaponStolen() {
            assert(!Game_local.gameLocal.isClient)
            if (projectileEnt != null) {
                if (isLinked) {
                    SetState("WeaponStolen", 0)
                    thread!!.Execute()
                }
                projectileEnt = null
            }

            // set to holstered so we can switch weapons right away
            status = weaponStatus_t.WP_HOLSTERED
            HideWeapon()
        }

        /* **********************************************************************

         Script state management

         ***********************************************************************/
        /*
         ================
         idWeapon::ConstructScriptObject

         Called during idEntity::Spawn.  Calls the constructor on the script object.
         Can be overridden by subclasses when a thread doesn't need to be allocated.
         ================
         */
        override fun ConstructScriptObject(): idThread? {
            val constructor: function_t?
            thread!!.EndThread()

            // call script object's constructor
            constructor = scriptObject.GetConstructor()
            if (null == constructor) {
                idGameLocal.Error("Missing constructor on '%s' for weapon", scriptObject.GetTypeName())
                return null
            }

            // init the script object's data
            scriptObject.ClearObject()
            thread!!.CallFunction(this, constructor, true)
            thread!!.Execute()
            return thread
        }

        /*
         ================
         idWeapon::DeconstructScriptObject

         Called during idEntity::~idEntity.  Calls the destructor on the script object.
         Can be overridden by subclasses when a thread doesn't need to be allocated.
         Not called during idGameLocal::MapShutdown.
         ================
         */
        // FIX: Method name was corrupted from "Deconstruct" to "Definalruct"
        override fun DeconstructScriptObject() {
            val destructor: function_t?
            if (null == thread) {
                return
            }

            // don't bother calling the script object's destructor on map shutdown
            if (Game_local.gameLocal.GameState() == gameState_t.GAMESTATE_SHUTDOWN) {
                return
            }
            thread!!.EndThread()

            // call script object's destructor
            destructor = scriptObject.GetDestructor()
            if (destructor != null) {
                // start a thread that will run immediately and end
                thread!!.CallFunction(this, destructor, true)
                thread!!.Execute()
                thread!!.EndThread()
            }

            // clear out the object's memory
            scriptObject.ClearObject()
        }

        /*
         =====================
         idWeapon::SetState
         =====================
         */
        fun SetState(statename: String, blendFrames: Int) {
            val func: function_t?
            if (!isLinked) {
                return
            }
            func = scriptObject.GetFunction(statename)
            if (null == func) {
                assert(false)
                idGameLocal.Error(
                    "Can't find function '%s' in object '%s'",
                    statename,
                    scriptObject.GetTypeName()
                )
                return
            }
            thread!!.CallFunction(this, func, true)
            state.set(statename)
            animBlendFrames = blendFrames
            if (SysCvar.g_debugWeapon.GetBool()) {
                Game_local.gameLocal.Printf("%d: weapon state : %s\n", Game_local.gameLocal.time, statename)
            }
            idealState.set("")
        }

        /*
         ================
         idWeapon::UpdateScript
         ================
         */
        fun UpdateScript() {
            var count: Int
            if (!isLinked) {
                return
            }

            // only update the script on new frames
            if (!Game_local.gameLocal.isNewFrame) {
                return
            }
            if (idealState.Length() != 0) {
                SetState(idealState.toString(), animBlendFrames)
            }

            // update script state, which may call Event_LaunchProjectiles, among other things
            count = 10
            while ((thread!!.Execute() || idealState.Length() != 0) && count-- != 0) {
                // happens for weapons with no clip (like grenades)
                if (idealState.Length() != 0) {
                    SetState(idealState.toString(), animBlendFrames)
                }
            }
            WEAPON_RELOAD.underscore(false)
        }

        /*
         ================
         idWeapon::EnterCinematic
         ================
         */
        fun EnterCinematic() {
            StopSound(TempDump.etoi(gameSoundChannel_t.SND_CHANNEL_ANY), false)
            if (isLinked) {
                SetState("EnterCinematic", 0)
                thread!!.Execute()
                WEAPON_ATTACK.underscore(false)
                WEAPON_RELOAD.underscore(false)
                WEAPON_NETRELOAD.underscore(false)
                WEAPON_NETENDRELOAD.underscore(false)
                // FIX: Added IsLinked() guard — WEAPON_NETFIRING is not linked in demo mode
                if (WEAPON_NETFIRING.IsLinked()) {
                    WEAPON_NETFIRING.underscore(false)
                }
                WEAPON_RAISEWEAPON.underscore(false)
                WEAPON_LOWERWEAPON.underscore(false)
            }
            disabled = true
            LowerWeapon()
        }

        /*
         ================
         idWeapon::ExitCinematic
         ================
         */
        fun ExitCinematic() {
            disabled = false
            if (isLinked) {
                SetState("ExitCinematic", 0)
                thread!!.Execute()
            }
            RaiseWeapon()
        }

        /*
         ================
         idWeapon::NetCatchup
         ================
         */
        fun NetCatchup() {
            if (isLinked) {
                SetState("NetCatchup", 0)
                thread!!.Execute()
            }
        }

        /* **********************************************************************

         Visual presentation

         ***********************************************************************/
        /*
         ================
         idWeapon::PresentWeapon
         ================
         */
        fun PresentWeapon(showViewModel: Boolean) {
            playerViewOrigin.set(owner!!.firstPersonViewOrigin)
            playerViewAxis.set(owner!!.firstPersonViewAxis)

            // calculate weapon position based on player movement bobbing
            owner!!.CalculateViewWeaponPos(viewWeaponOrigin, viewWeaponAxis)

            // hide offset is for dropping the gun when approaching a GUI or NPC
            // This is simpler to manage than doing the weapon put-away animation
            if (Game_local.gameLocal.time - hideStartTime < hideTime) {
                var frac: Float = (Game_local.gameLocal.time - hideStartTime).toFloat() / hideTime.toFloat()
                if (hideStart < hideEnd) {
                    frac = 1.0f - frac
                    frac = 1.0f - frac * frac
                } else {
                    frac = frac * frac
                }
                hideOffset = hideStart + (hideEnd - hideStart) * frac
            } else {
                hideOffset = hideEnd
                if (hide && disabled) {
                    Hide()
                }
            }
            viewWeaponOrigin.plusAssign(viewWeaponAxis[2].times(hideOffset))

            // kick up based on repeat firing
            MuzzleRise(viewWeaponOrigin, viewWeaponAxis)

            // set the physics position and orientation
            GetPhysics().SetOrigin(viewWeaponOrigin)
            GetPhysics().SetAxis(viewWeaponAxis)
            UpdateVisuals()

            // update the weapon script
            UpdateScript()
            UpdateGUI()

            // update animation
            UpdateAnimation()

            // only show the surface in player view
            renderEntity!!.allowSurfaceInViewID = owner!!.entityNumber + 1

            // crunch the depth range so it never pokes into walls this breaks the machine gun gui
            renderEntity!!.weaponDepthHack = true

            // present the model
            if (showViewModel) {
                Present()
            } else {
                FreeModelDef()
            }
            if (worldModel.GetEntity() != null && worldModel.GetEntity()!!.GetRenderEntity() != null) {
                // deal with the third-person visible world model
                // don't show shadows of the world model in first person
                if (Game_local.gameLocal.isMultiplayer || SysCvar.g_showPlayerShadow.GetBool() || SysCvar.pm_thirdPerson.GetBool()) {
                    worldModel.GetEntity()!!.GetRenderEntity()!!.suppressShadowInViewID = 0
                } else {
                    worldModel.GetEntity()!!.GetRenderEntity()!!.suppressShadowInViewID = owner!!.entityNumber + 1
                    worldModel.GetEntity()!!.GetRenderEntity()!!.suppressShadowInLightID =
                        LIGHTID_VIEW_MUZZLE_FLASH + owner!!.entityNumber
                }
            }
            if (nozzleFx) {
                UpdateNozzleFx()
            }

            // muzzle smoke
            if (showViewModel && !disabled && weaponSmoke != null && weaponSmokeStartTime != 0) {
                // use the barrel joint if available
                // FIX: Was != 0 which is wrong — INVALID_JOINT is -1, so != 0 passes for invalid joints.
                // Changed to != INVALID_JOINT for consistency with the rest of the file.
                if (barrelJointView != Model.INVALID_JOINT) {
                    GetGlobalJointTransform(true, barrelJointView, muzzleOrigin, muzzleAxis)
                } else {
                    // default to going straight out the view
                    muzzleOrigin.set(playerViewOrigin)
                    muzzleAxis.set(playerViewAxis)
                }
                // spit out a particle
                if (!Game_local.gameLocal.smokeParticles!!.EmitSmoke(
                        weaponSmoke,
                        weaponSmokeStartTime,
                        Game_local.gameLocal.random.RandomFloat(),
                        muzzleOrigin,
                        muzzleAxis
                    )
                ) {
                    weaponSmokeStartTime = if (continuousSmoke) Game_local.gameLocal.time else 0
                }
            }
            if (showViewModel && strikeSmoke != null && strikeSmokeStartTime != 0) {
                // spit out a particle
                if (!Game_local.gameLocal.smokeParticles!!.EmitSmoke(
                        strikeSmoke,
                        strikeSmokeStartTime,
                        Game_local.gameLocal.random.RandomFloat(),
                        strikePos,
                        strikeAxis
                    )
                ) {
                    strikeSmokeStartTime = 0
                }
            }

            // remove the muzzle flash light when it's done
            if (!lightOn && Game_local.gameLocal.time >= muzzleFlashEnd || IsHidden()) {
                if (muzzleFlashHandle != -1) {
                    Game_local.gameRenderWorld!!.FreeLightDef(muzzleFlashHandle)
                    muzzleFlashHandle = -1
                }
                if (worldMuzzleFlashHandle != -1) {
                    Game_local.gameRenderWorld!!.FreeLightDef(worldMuzzleFlashHandle)
                    worldMuzzleFlashHandle = -1
                }
            }

            // update the muzzle flash light, so it moves with the gun
            if (muzzleFlashHandle != -1) {
                UpdateFlashPosition()
                Game_local.gameRenderWorld!!.UpdateLightDef(muzzleFlashHandle, muzzleFlash)
                Game_local.gameRenderWorld!!.UpdateLightDef(worldMuzzleFlashHandle, worldMuzzleFlash)

                // wake up monsters with the flashlight
                if (!Game_local.gameLocal.isMultiplayer && lightOn && !owner!!.fl.notarget) {
                    AlertMonsters()
                }
            }

            // update the gui light
            if (guiLight.lightRadius[0] != 0.0f && guiLightJointView != Model.INVALID_JOINT) {
                GetGlobalJointTransform(true, guiLightJointView, guiLight.origin, guiLight.axis)
                if (guiLightHandle != -1) {
                    Game_local.gameRenderWorld!!.UpdateLightDef(guiLightHandle, guiLight)
                } else {
                    guiLightHandle = Game_local.gameRenderWorld!!.AddLightDef(guiLight)
                }
            }
            if (status != weaponStatus_t.WP_READY && sndHum != null) {
                StopSound(TempDump.etoi(gameSoundChannel_t.SND_CHANNEL_BODY), false)
            }
            UpdateSound()
        }

        /*
         ================
         idWeapon::GetZoomFov
         ================
         */
        fun GetZoomFov(): Int {
            return zoomFov
        }

        /*
         ================
         idWeapon::GetWeaponAngleOffsets
         ================
         */
        fun GetWeaponAngleOffsets(average: CInt, scale: CFloat, max: CFloat) {
            average.integerValue = (weaponAngleOffsetAverages)
            scale._val = (weaponAngleOffsetScale)
            max._val = (weaponAngleOffsetMax)
        }

        /*
         ================
         idWeapon::GetWeaponTimeOffsets
         ================
         */
        fun GetWeaponTimeOffsets(time: CFloat, scale: CFloat) {
            time._val = weaponOffsetTime
            scale._val = weaponOffsetScale
        }

        /*
         ================
         idWeapon::BloodSplat
         ================
         */
        fun BloodSplat(size: Float): Boolean {
            val s = CFloat()
            val c = CFloat()
            val localAxis = idMat3()
            val axistemp = idMat3()
            val localOrigin = idVec3()
            val normal = idVec3()
            if (hasBloodSplat) {
                return true
            }
            hasBloodSplat = true
            if (modelDefHandle < 0) {
                return false
            }
            if (!GetGlobalJointTransform(true, ejectJointView, localOrigin, localAxis)) {
                return false
            }
            localOrigin.plusAssign(0, Game_local.gameLocal.random.RandomFloat() * -10.0f)
            localOrigin.plusAssign(1, Game_local.gameLocal.random.RandomFloat() * 1.0f)
            localOrigin.plusAssign(2, Game_local.gameLocal.random.RandomFloat() * -2.0f)
            normal.set(
                idVec3(
                    Game_local.gameLocal.random.CRandomFloat(),
                    -Game_local.gameLocal.random.RandomFloat(),
                    -1.0f
                )
            )
            normal.Normalize()
            idMath.SinCos16(Game_local.gameLocal.random.RandomFloat() * idMath.TWO_PI, s, c)
            localAxis[2] = normal.unaryMinus()
            localAxis[2].NormalVectors(axistemp[0], axistemp[1])
            localAxis[0] = axistemp[0].times(c._val).plus(axistemp[1].times(-s._val))
            localAxis[1] = axistemp[0].times(-s._val).plus(axistemp[1].times(-c._val))
            localAxis[0].timesAssign(1.0f / size)
            localAxis[1].timesAssign(1.0f / size)
            val localPlane: Array<idPlane> = idPlane.generateArray(2)
            localPlane[0].set(localAxis[0])
            localPlane[0][3] = -localOrigin.times(localAxis[0]) + 0.5f
            localPlane[1].set(localAxis[1])
            localPlane[1][3] = -localOrigin.times(localAxis[1]) + 0.5f
            val mtr: Material.idMaterial? = DeclManager.declManager.FindMaterial("textures/decals/duffysplatgun")
            Game_local.gameRenderWorld!!.ProjectOverlay(modelDefHandle, localPlane as Array<idPlane?>, mtr)
            return true
        }

        /*
         ================
         idWeapon::GetAmmoType
         ================
         */
        fun  /*ammo_t*/GetAmmoType(): Int {
            return ammoType
        }

        /*
         ================
         idWeapon::AmmoAvailable
         ================
         */
        fun AmmoAvailable(): Int {
            return if (owner != null) {
                owner!!.inventory.HasAmmo(ammoType, ammoRequired)
            } else {
                0
            }
        }

        /*
         ================
         idWeapon::AmmoInClip
         ================
         */
        fun AmmoInClip(): Int {
            return ammoClip
        }

        /*
         ================
         idWeapon::ResetAmmoClip
         ================
         */
        fun ResetAmmoClip() {
            ammoClip = -1
        }

        /*
         ================
         idWeapon::ClipSize
         ================
         */
        fun ClipSize(): Int {
            return clipSize
        }

        /*
         ================
         idWeapon::LowAmmo
         ================
         */
        fun LowAmmo(): Int {
            return lowAmmo
        }

        /*
         ================
         idWeapon::AmmoRequired
         ================
         */
        fun AmmoRequired(): Int {
            return ammoRequired
        }

        /*
         ================
         idWeapon::WriteToSnapshot
         ================
         */
        override fun WriteToSnapshot(msg: idBitMsgDelta) {
            msg.WriteBits(ammoClip, Player.ASYNC_PLAYER_INV_CLIP_BITS)
            msg.WriteBits(worldModel.GetSpawnId(), 32)
            msg.WriteBits(TempDump.btoi(lightOn), 1)
            msg.WriteBits(if (isFiring) 1 else 0, 1)
        }

        /*
         ================
         idWeapon::ReadFromSnapshot
         ================
         */
        override fun ReadFromSnapshot(msg: idBitMsgDelta) {
            ammoClip = msg.ReadBits(Player.ASYNC_PLAYER_INV_CLIP_BITS)
            worldModel.SetSpawnId(msg.ReadBits(32))
            val snapLight = msg.ReadBits(1) != 0
            isFiring = msg.ReadBits(1) != 0

            // WEAPON_NETFIRING is only turned on for other clients we're predicting. not for local client
            if (owner != null && Game_local.gameLocal.localClientNum != owner!!.entityNumber && WEAPON_NETFIRING.IsLinked()) {

                // immediately go to the firing state so we don't skip fire animations
                if (!WEAPON_NETFIRING.underscore()!! && isFiring) {
                    idealState.set("Fire")
                }

                // immediately switch back to idle
                if (WEAPON_NETFIRING.underscore()!! && !isFiring) {
                    idealState.set("Idle")
                }
                WEAPON_NETFIRING.underscore(isFiring)
            }
            if (snapLight != lightOn) {
                Reload()
            }
        }

        /*
         ================
         idWeapon::ClientReceiveEvent
         ================
         */
        override fun ClientReceiveEvent(event: Int, time: Int, msg: idBitMsg): Boolean {
            return when (event) {
                EVENT_RELOAD -> {
                    if (Game_local.gameLocal.time - time < 1000) {
                        if (WEAPON_NETRELOAD.IsLinked()) {
                            WEAPON_NETRELOAD.underscore(true)
                            WEAPON_NETENDRELOAD.underscore(false)
                        }
                    }
                    true
                }

                EVENT_ENDRELOAD -> {
                    if (WEAPON_NETENDRELOAD.IsLinked()) {
                        WEAPON_NETENDRELOAD.underscore(true)
                    }
                    true
                }

                EVENT_CHANGESKIN -> {
                    val index = Game_local.gameLocal.ClientRemapDecl(declType_t.DECL_SKIN, msg.ReadLong())
                    renderEntity!!.customSkin = if (index != -1) DeclManager.declManager.DeclByIndex(
                        declType_t.DECL_SKIN,
                        index
                    ) as idDeclSkin else null
                    UpdateVisuals()
                    if (worldModel.GetEntity() != null) {
                        worldModel.GetEntity()!!.SetSkin(renderEntity!!.customSkin)
                    }
                    true
                }

                else -> {
                    super.ClientReceiveEvent(event, time, msg)
                }
            }
        }

        /*
         ===============
         idWeapon::ClientPredictionThink
         ===============
         */
        override fun ClientPredictionThink() {
            UpdateAnimation()
        }

        /*
         ================
         idWeapon::AlertMonsters
         ================
         */
        // flashlight
        private fun AlertMonsters() {
            val tr = trace_s()
            var ent: idEntity?
            val end = idVec3(muzzleFlash.origin.plus(muzzleFlash.axis.times(muzzleFlash.target)))
            Game_local.gameLocal.clip.TracePoint(
                tr,
                muzzleFlash.origin,
                end,
                Material.CONTENTS_OPAQUE or Game_local.MASK_SHOT_RENDERMODEL or Material.CONTENTS_FLASHLIGHT_TRIGGER,
                owner
            )
            if (SysCvar.g_debugWeapon.GetBool()) {
                Game_local.gameRenderWorld!!.DebugLine(colorYellow, muzzleFlash.origin, end, 0)
                Game_local.gameRenderWorld!!.DebugArrow(colorGreen, muzzleFlash.origin, tr.endpos, 2, 0)
            }
            if (tr.fraction < 1.0f) {
                ent = Game_local.gameLocal.GetTraceEntity(tr)
                if (ent is idAI) {
                    ent.TouchedByFlashlight(owner)
                } else if (ent is idTrigger) {
                    ent.Signal(signalNum_t.SIG_TOUCH)
                    ent.ProcessEvent(EV_Touch, owner, tr)
                }
            }

            // jitter the trace to try to catch cases where a trace down the center doesn't hit the monster
            end.plusAssign(muzzleFlash.axis.times(muzzleFlash.right.times(idMath.Sin16(MS2SEC(Game_local.gameLocal.time.toFloat()) * 31.34f))))
            end.plusAssign(muzzleFlash.axis.times(muzzleFlash.up.times(idMath.Sin16(MS2SEC(Game_local.gameLocal.time.toFloat()) * 12.17f))))
            Game_local.gameLocal.clip.TracePoint(
                tr,
                muzzleFlash.origin,
                end,
                Material.CONTENTS_OPAQUE or Game_local.MASK_SHOT_RENDERMODEL or Material.CONTENTS_FLASHLIGHT_TRIGGER,
                owner
            )
            if (SysCvar.g_debugWeapon.GetBool()) {
                Game_local.gameRenderWorld!!.DebugLine(colorYellow, muzzleFlash.origin, end, 0)
                Game_local.gameRenderWorld!!.DebugArrow(colorGreen, muzzleFlash.origin, tr.endpos, 2, 0)
            }
            if (tr.fraction < 1.0f) {
                ent = Game_local.gameLocal.GetTraceEntity(tr)
                if (ent is idAI) {
                    ent.TouchedByFlashlight(owner)
                } else if (ent is idTrigger) {
                    ent.Signal(signalNum_t.SIG_TOUCH)
                    ent.ProcessEvent(EV_Touch, owner, tr)
                }
            }
        }

        /*
         ================
         idWeapon::InitWorldModel
         ================
         */
        // Visual presentation
        private fun InitWorldModel(def: idDeclEntityDef) {
            val ent: idEntity
            ent = worldModel.GetEntity()!!
            assert(ent != null)
            assert(def != null)
            val model = def.dict.GetString("model_world")
            val attach = def.dict.GetString("joint_attach")
            ent.SetSkin(null)
            if (model.isNotEmpty() && attach.isNotEmpty()) {
                ent.Show()
                ent.SetModel(model)
                if (ent.GetAnimator().ModelDef() != null) {
                    ent.SetSkin(ent.GetAnimator().ModelDef()!!.GetDefaultSkin())
                }
                ent.GetPhysics().SetContents(0)
                ent.GetPhysics().SetClipModel(null, 1.0f)
                ent.BindToJoint(owner!!, attach, true)
                ent.GetPhysics().SetOrigin(vec3_origin)
                ent.GetPhysics().SetAxis(idMat3.getMat3_identity())

                // supress model in player views, but allow it in mirrors and remote views
                val worldModelRenderEntity = ent.GetRenderEntity()
                if (worldModelRenderEntity != null) {
                    worldModelRenderEntity.suppressSurfaceInViewID = owner!!.entityNumber + 1
                    worldModelRenderEntity.suppressShadowInViewID = owner!!.entityNumber + 1
                    worldModelRenderEntity.suppressShadowInLightID =
                        LIGHTID_VIEW_MUZZLE_FLASH + owner!!.entityNumber
                }
            } else {
                ent.SetModel("")
                ent.Hide()
            }
            flashJointWorld = ent.GetAnimator().GetJointHandle("flash")
            barrelJointWorld = ent.GetAnimator().GetJointHandle("muzzle")
            ejectJointWorld = ent.GetAnimator().GetJointHandle("eject")
        }

        /*
         ================
         idWeapon::MuzzleFlashLight
         ================
         */
        private fun MuzzleFlashLight() {
            if (!lightOn && (!SysCvar.g_muzzleFlash.GetBool() || 0.0f == muzzleFlash.lightRadius[0])) {
                return
            }
            if (flashJointView == Model.INVALID_JOINT) {
                return
            }
            UpdateFlashPosition()

            // these will be different each fire
            muzzleFlash.shaderParms[RenderWorld.SHADERPARM_TIMEOFFSET] =
                -MS2SEC(Game_local.gameLocal.time.toFloat())
            muzzleFlash.shaderParms[RenderWorld.SHADERPARM_DIVERSITY] =
                renderEntity!!.shaderParms[RenderWorld.SHADERPARM_DIVERSITY]
            worldMuzzleFlash.shaderParms[RenderWorld.SHADERPARM_TIMEOFFSET] =
                -MS2SEC(Game_local.gameLocal.time.toFloat())
            worldMuzzleFlash.shaderParms[RenderWorld.SHADERPARM_DIVERSITY] =
                renderEntity!!.shaderParms[RenderWorld.SHADERPARM_DIVERSITY]

            // the light will be removed at this time
            muzzleFlashEnd = Game_local.gameLocal.time + flashTime
            if (muzzleFlashHandle != -1) {
                Game_local.gameRenderWorld!!.UpdateLightDef(muzzleFlashHandle, muzzleFlash)
                Game_local.gameRenderWorld!!.UpdateLightDef(worldMuzzleFlashHandle, worldMuzzleFlash)
            } else {
                muzzleFlashHandle = Game_local.gameRenderWorld!!.AddLightDef(muzzleFlash)
                worldMuzzleFlashHandle = Game_local.gameRenderWorld!!.AddLightDef(worldMuzzleFlash)
            }
        }

        /*
         ================
         idWeapon::MuzzleRise

         The machinegun and chaingun will incrementally back up as they are being fired
         ================
         */
        private fun MuzzleRise(origin: idVec3, axis: idMat3) {
            var time: Int
            val amount: Float
            val ang: idAngles = idAngles()
            val offset = idVec3()
            time = kick_endtime - Game_local.gameLocal.time
            if (time <= 0) {
                return
            }
            if (muzzle_kick_maxtime <= 0) {
                return
            }
            if (time > muzzle_kick_maxtime) {
                time = muzzle_kick_maxtime
            }

            amount = time.toFloat() / muzzle_kick_maxtime.toFloat()
            ang.set(muzzle_kick_angles * amount)
            offset.set(muzzle_kick_offset * amount)

            origin.set(origin - axis * offset)
            axis.set(ang.ToMat3() * axis)
        }

        /*
         ================
         idWeapon::UpdateNozzleFx
         ================
         */
        private fun UpdateNozzleFx() {
            if (!nozzleFx) {
                return
            }

            //
            // shader parms
            //
            val la = Game_local.gameLocal.time - lastAttack + 1
            var s = 1.0f
            var l = 0.0f
            if (la < nozzleFxFade) {
                s = la.toFloat() / nozzleFxFade.toFloat()
                l = 1.0f - s
            }
            renderEntity!!.shaderParms[5] = s
            renderEntity!!.shaderParms[6] = l
            if (ventLightJointView == Model.INVALID_JOINT) {
                return
            }

            //
            // vent light
            //
            if (nozzleGlowHandle == -1) {
                nozzleGlow = renderLight_s()
                if (owner != null) {
                    nozzleGlow.allowLightInViewID.integerValue = owner!!.entityNumber + 1
                }
                nozzleGlow.pointLight._val = true
                nozzleGlow.noShadows._val = true
                nozzleGlow.lightRadius.x = nozzleGlowRadius
                nozzleGlow.lightRadius.y = nozzleGlowRadius
                nozzleGlow.lightRadius.z = nozzleGlowRadius
                nozzleGlow.shader = nozzleGlowShader
                nozzleGlow.shaderParms[RenderWorld.SHADERPARM_TIMESCALE] = 1.0f
                nozzleGlow.shaderParms[RenderWorld.SHADERPARM_TIMEOFFSET] =
                    -MS2SEC(Game_local.gameLocal.time.toFloat())
                GetGlobalJointTransform(true, ventLightJointView, nozzleGlow.origin, nozzleGlow.axis)
                nozzleGlowHandle = Game_local.gameRenderWorld!!.AddLightDef(nozzleGlow)
            }
            GetGlobalJointTransform(true, ventLightJointView, nozzleGlow.origin, nozzleGlow.axis)
            nozzleGlow.shaderParms[RenderWorld.SHADERPARM_RED] = nozzleGlowColor.x * s
            nozzleGlow.shaderParms[RenderWorld.SHADERPARM_GREEN] = nozzleGlowColor.y * s
            nozzleGlow.shaderParms[RenderWorld.SHADERPARM_BLUE] = nozzleGlowColor.z * s
            Game_local.gameRenderWorld!!.UpdateLightDef(nozzleGlowHandle, nozzleGlow)
        }

        /*
         ================
         idWeapon::UpdateFlashPosition
         ================
         */
        private fun UpdateFlashPosition() {
            // the flash has an explicit joint for locating it
            GetGlobalJointTransform(true, flashJointView, muzzleFlash.origin, muzzleFlash.axis)

            // if the desired point is inside or very close to a wall, back it up until it is clear
            val start = idVec3(muzzleFlash.origin - playerViewAxis[0] * 16)
            val end = idVec3(muzzleFlash.origin + playerViewAxis[0] * 8)
            val tr = trace_s()
            Game_local.gameLocal.clip.TracePoint(tr, start, end, Game_local.MASK_SHOT_RENDERMODEL, owner)
            // be at least 8 units away from a solid
            muzzleFlash.origin.set(tr.endpos - playerViewAxis[0] * 8)

            // put the world muzzle flash on the end of the joint, no matter what
            GetGlobalJointTransform(false, flashJointWorld, worldMuzzleFlash.origin, worldMuzzleFlash.axis)
        }

        /* **********************************************************************

         Script events

         ***********************************************************************/
        /*
         ===============
         idWeapon::Event_Clear
         ===============
         */
        private fun Event_Clear() {
            Clear()
        }

        /*
         ===============
         idWeapon::Event_GetOwner
         ===============
         */
        private fun Event_GetOwner() {
            idThread.ReturnEntity(owner)
        }

        /*
         ===============
         idWeapon::Event_WeaponState
         ===============
         */
        private fun Event_WeaponState(_statename: idEventArg<String?>, blendFrames: idEventArg<Int>) {
            val statename = _statename.value
            val func: function_t?
            func = scriptObject.GetFunction(statename)
            if (null == func) {
                assert(false)
                idGameLocal.Error(
                    "Can't find function '%s' in object '%s'",
                    statename,
                    scriptObject.GetTypeName()
                )
            }
            idealState.set(statename)
            isFiring = 0 == idealState.Icmp("Fire")
            animBlendFrames = blendFrames.value
            thread!!.DoneProcessing()
        }

        /*
         ===============
         idWeapon::Event_WeaponReady
         ===============
         */
        private fun Event_WeaponReady() {
            status = weaponStatus_t.WP_READY
            if (isLinked) {
                WEAPON_RAISEWEAPON.underscore(false)
            }
            if (sndHum != null) {
                StartSoundShader(sndHum, gameSoundChannel_t.SND_CHANNEL_BODY.ordinal, 0, false)
            }
        }

        /*
         ===============
         idWeapon::Event_WeaponOutOfAmmo
         ===============
         */
        private fun Event_WeaponOutOfAmmo() {
            status = weaponStatus_t.WP_OUTOFAMMO
            if (isLinked) {
                WEAPON_RAISEWEAPON.underscore(false)
            }
        }

        /*
         ===============
         idWeapon::Event_WeaponReloading
         ===============
         */
        private fun Event_WeaponReloading() {
            status = weaponStatus_t.WP_RELOAD
        }

        /*
         ===============
         idWeapon::Event_WeaponHolstered
         ===============
         */
        private fun Event_WeaponHolstered() {
            status = weaponStatus_t.WP_HOLSTERED
            if (isLinked) {
                WEAPON_LOWERWEAPON.underscore(false)
            }
        }

        /*
         ===============
         idWeapon::Event_WeaponRising
         ===============
         */
        private fun Event_WeaponRising() {
            status = weaponStatus_t.WP_RISING
            if (isLinked) {
                WEAPON_LOWERWEAPON.underscore(false)
            }
            owner!!.WeaponRisingCallback()
        }

        /*
         ===============
         idWeapon::Event_WeaponLowering
         ===============
         */
        private fun Event_WeaponLowering() {
            status = weaponStatus_t.WP_LOWERING
            if (isLinked) {
                WEAPON_RAISEWEAPON.underscore(false)
            }
            owner!!.WeaponLoweringCallback()
        }

        /*
         ===============
         idWeapon::Event_UseAmmo
         ===============
         */
        private fun Event_UseAmmo(_amount: idEventArg<Int>) {
            val amount: Int = _amount.value
            if (Game_local.gameLocal.isClient) {
                return
            }
            owner!!.inventory.UseAmmo(ammoType, if (powerAmmo) amount else amount * ammoRequired)
            if (clipSize != 0 && ammoRequired != 0) {
                ammoClip -= if (powerAmmo) amount else amount * ammoRequired
                if (ammoClip < 0) {
                    ammoClip = 0
                }
            }
        }

        /*
         ===============
         idWeapon::Event_AddToClip
         ===============
         */
        private fun Event_AddToClip(amount: idEventArg<Int>) {
            val ammoAvail: Int
            if (Game_local.gameLocal.isClient) {
                return
            }
            ammoClip += amount.value
            if (ammoClip > clipSize) {
                ammoClip = clipSize
            }
            ammoAvail = owner!!.inventory.HasAmmo(ammoType, ammoRequired)
            if (ammoClip > ammoAvail) {
                ammoClip = ammoAvail
            }
        }

        /*
         ===============
         idWeapon::Event_AmmoInClip
         ===============
         */
        private fun Event_AmmoInClip() {
            val ammo = AmmoInClip()
            idThread.ReturnFloat(ammo.toFloat())
        }

        /*
         ===============
         idWeapon::Event_AmmoAvailable
         ===============
         */
        private fun Event_AmmoAvailable() {
            val ammoAvail = owner!!.inventory.HasAmmo(ammoType, ammoRequired)
            idThread.ReturnFloat(ammoAvail.toFloat())
        }

        /*
         ===============
         idWeapon::Event_TotalAmmoCount
         ===============
         */
        private fun Event_TotalAmmoCount() {
            val ammoAvail = owner!!.inventory.HasAmmo(ammoType, 1)
            idThread.ReturnFloat(ammoAvail.toFloat())
        }

        /*
         ===============
         idWeapon::Event_ClipSize
         ===============
         */
        private fun Event_ClipSize() {
            idThread.ReturnFloat(clipSize.toFloat())
        }

        /*
         ===============
         idWeapon::Event_PlayAnim
         ===============
         */
        private fun Event_PlayAnim(_channel: idEventArg<Int>, _animname: idEventArg<String>) {
            val channel: Int = _channel.value
            val animname = _animname.value
            var anim: Int
            anim = animator.GetAnim(animname)
            if (0 == anim) {
                Game_local.gameLocal.Warning("missing '%s' animation on '%s' (%s)", animname, name, GetEntityDefName())
                animator.Clear(channel, Game_local.gameLocal.time, Anim.FRAME2MS(animBlendFrames))
                animDoneTime = 0
            } else {
                if (!(owner != null && owner!!.GetInfluenceLevel() != 0)) {
                    Show()
                }
                animator.PlayAnim(channel, anim, Game_local.gameLocal.time, Anim.FRAME2MS(animBlendFrames))
                animDoneTime = animator.CurrentAnim(channel).GetEndTime()
                if (worldModel.GetEntity() != null) {
                    anim = worldModel.GetEntity()!!.GetAnimator().GetAnim(animname)
                    if (anim != 0) {
                        worldModel.GetEntity()!!.GetAnimator()
                            .PlayAnim(channel, anim, Game_local.gameLocal.time, Anim.FRAME2MS(animBlendFrames))
                    }
                }
            }
            animBlendFrames = 0
            idThread.ReturnInt(0)
        }

        /*
         ===============
         idWeapon::Event_PlayCycle
         ===============
         */
        private fun Event_PlayCycle(_channel: idEventArg<Int>, _animname: idEventArg<String>) {
            val channel: Int = _channel.value
            val animname = _animname.value
            var anim: Int
            anim = animator.GetAnim(animname)
            if (0 == anim) {
                Game_local.gameLocal.Warning("missing '%s' animation on '%s' (%s)", animname, name, GetEntityDefName())
                animator.Clear(channel, Game_local.gameLocal.time, Anim.FRAME2MS(animBlendFrames))
                animDoneTime = 0
            } else {
                if (!(owner != null && owner!!.GetInfluenceLevel() != 0)) {
                    Show()
                }
                animator.CycleAnim(channel, anim, Game_local.gameLocal.time, Anim.FRAME2MS(animBlendFrames))
                animDoneTime = animator.CurrentAnim(channel).GetEndTime()
                if (worldModel.GetEntity() != null) {
                    anim = worldModel.GetEntity()!!.GetAnimator().GetAnim(animname)
                    worldModel.GetEntity()!!.GetAnimator()
                        .CycleAnim(channel, anim, Game_local.gameLocal.time, Anim.FRAME2MS(animBlendFrames))
                }
            }
            animBlendFrames = 0
            idThread.ReturnInt(0)
        }

        /*
         ===============
         idWeapon::Event_AnimDone
         ===============
         */
        private fun Event_AnimDone(channel: idEventArg<Int>, blendFrames: idEventArg<Int>) {
            idThread.ReturnInt(animDoneTime - Anim.FRAME2MS(blendFrames.value) <= Game_local.gameLocal.time)
        }

        /*
         ===============
         idWeapon::Event_SetBlendFrames
         ===============
         */
        private fun Event_SetBlendFrames(channel: idEventArg<Int>, blendFrames: idEventArg<Int>) {
            animBlendFrames = blendFrames.value
        }

        /*
         ===============
         idWeapon::Event_GetBlendFrames
         ===============
         */
        private fun Event_GetBlendFrames(channel: idEventArg<Int>) {
            idThread.ReturnInt(animBlendFrames)
        }

        /*
         ================
         idWeapon::Event_Next
         ================
         */
        private fun Event_Next() {
            // change to another weapon if possible
            owner!!.NextBestWeapon()
        }

        /*
         ================
         idWeapon::Event_SetSkin
         ================
         */
        private fun Event_SetSkin(_skinname: idEventArg<String?>) {
            val skinname = _skinname.value
            val skinDecl: idDeclSkin?
            skinDecl = if (skinname == null || skinname.isNullOrEmpty()) {
                null
            } else {
                DeclManager.declManager.FindSkin(skinname)
            }
            renderEntity!!.customSkin = skinDecl
            UpdateVisuals()
            if (worldModel.GetEntity() != null) {
                worldModel.GetEntity()!!.SetSkin(skinDecl)
            }
            if (Game_local.gameLocal.isServer) {
                val msg = idBitMsg()
                val msgBuf = ByteBuffer.allocate(Game_local.MAX_EVENT_PARAM_SIZE)
                msg.Init(msgBuf, Game_local.MAX_EVENT_PARAM_SIZE)
                msg.WriteLong(
                    if (skinDecl != null) Game_local.gameLocal.ServerRemapDecl(
                        -1,
                        declType_t.DECL_SKIN,
                        skinDecl.Index()
                    ) else -1
                )
                ServerSendEvent(EVENT_CHANGESKIN, msg, false, -1)
            }
        }

        /*
         ================
         idWeapon::Event_Flashlight
         ================
         */
        private fun Event_Flashlight(enable: idEventArg<Int>) {
            if (enable.value != 0) {
                lightOn = true
                MuzzleFlashLight()
            } else {
                lightOn = false
                muzzleFlashEnd = 0
            }
        }

        /*
         ================
         idWeapon::Event_GetLightParm
         ================
         */
        private fun Event_GetLightParm(_parmnum: idEventArg<Int>) {
            val parmnum: Int = _parmnum.value
            if (parmnum < 0 || parmnum >= Material.MAX_ENTITY_SHADER_PARMS) {
                idGameLocal.Error("shader parm index (%d) out of range", parmnum)
            }
            idThread.ReturnFloat(muzzleFlash.shaderParms[parmnum])
        }

        /*
         ================
         idWeapon::Event_SetLightParm
         ================
         */
        private fun Event_SetLightParm(_parmnum: idEventArg<Int>, _value: idEventArg<Float>) {
            val parmnum: Int = _parmnum.value
            val value: Float = _value.value
            if (parmnum < 0 || parmnum >= Material.MAX_ENTITY_SHADER_PARMS) {
                idGameLocal.Error("shader parm index (%d) out of range", parmnum)
            }
            muzzleFlash.shaderParms[parmnum] = value
            worldMuzzleFlash.shaderParms[parmnum] = value
            UpdateVisuals()
        }

        /*
         ================
         idWeapon::Event_SetLightParms
         ================
         */
        private fun Event_SetLightParms(
            parm0: idEventArg<Float>,
            parm1: idEventArg<Float>,
            parm2: idEventArg<Float>,
            parm3: idEventArg<Float>
        ) {
            muzzleFlash.shaderParms[RenderWorld.SHADERPARM_RED] = parm0.value
            muzzleFlash.shaderParms[RenderWorld.SHADERPARM_GREEN] = parm1.value
            muzzleFlash.shaderParms[RenderWorld.SHADERPARM_BLUE] = parm2.value
            muzzleFlash.shaderParms[RenderWorld.SHADERPARM_ALPHA] = parm3.value
            worldMuzzleFlash.shaderParms[RenderWorld.SHADERPARM_RED] = parm0.value
            worldMuzzleFlash.shaderParms[RenderWorld.SHADERPARM_GREEN] = parm1.value
            worldMuzzleFlash.shaderParms[RenderWorld.SHADERPARM_BLUE] = parm2.value
            worldMuzzleFlash.shaderParms[RenderWorld.SHADERPARM_ALPHA] = parm3.value
            UpdateVisuals()
        }

        /*
         ================
         idWeapon::Event_LaunchProjectiles
         ================
         */
        private fun Event_LaunchProjectiles(
            _num_projectiles: idEventArg<Int>,
            _spread: idEventArg<Float>,
            fuseOffset: idEventArg<Float>,
            launchPower: idEventArg<Float>,
            _dmgPower: idEventArg<Float>
        ) {
            val num_projectiles: Int = _num_projectiles.value
            val spread: Float = _spread.value
            var dmgPower: Float = _dmgPower.value
            var proj: idProjectile?
            val ent = arrayOfNulls<idEntity>(1)
            var i: Int
            val dir = idVec3()
            var ang: Float
            var spin: Float
            val distance = CFloat()
            val tr = trace_s()
            val start = idVec3()
            val muzzle_pos = idVec3()
            val ownerBounds: idBounds
            val projBounds: idBounds = idBounds()
            if (IsHidden()) {
                return
            }
            if (0 == projectileDict.GetNumKeyVals()) {
                val classname = weaponDef!!.dict.GetString("classname")
                Game_local.gameLocal.Warning("No projectile defined on '%s'", classname)
                return
            }

            // avoid all ammo considerations on an MP client
            if (!Game_local.gameLocal.isClient) {

                // check if we're out of ammo or the clip is empty
                val ammoAvail = owner!!.inventory.HasAmmo(ammoType, ammoRequired)
                if (0 == ammoAvail || clipSize != 0 && ammoClip <= 0) {
                    return
                }

                // if this is a power ammo weapon ( currently only the bfg ) then make sure
                // we only fire as much power as available in each clip
                if (powerAmmo) {
                    // power comes in as a float from zero to max
                    // if we use this on more than the bfg will need to define the max
                    // in the .def as opposed to just in the script so proper calcs
                    // can be done here.
                    dmgPower = (dmgPower.toInt() + 1).toFloat()
                    if (dmgPower > ammoClip) {
                        dmgPower = ammoClip.toFloat()
                    }
                }
                owner!!.inventory.UseAmmo(ammoType, (if (powerAmmo) dmgPower else ammoRequired).toInt())
                if (clipSize != 0 && ammoRequired != 0) {
                    ammoClip -= if (powerAmmo) dmgPower.toInt() else 1
                }
            }
            if (!silent_fire) {
                // wake up nearby monsters
                Game_local.gameLocal.AlertAI(owner)
            }

            // set the shader parm to the time of last projectile firing,
            // which the gun material shaders can reference for single shot barrel glows, etc
            renderEntity!!.shaderParms[RenderWorld.SHADERPARM_DIVERSITY] = Game_local.gameLocal.random.CRandomFloat()
            renderEntity!!.shaderParms[RenderWorld.SHADERPARM_TIMEOFFSET] =
                -MS2SEC(Game_local.gameLocal.realClientTime.toFloat())
            if (worldModel.GetEntity() != null) {
                worldModel.GetEntity()!!.SetShaderParm(
                    RenderWorld.SHADERPARM_DIVERSITY,
                    renderEntity!!.shaderParms[RenderWorld.SHADERPARM_DIVERSITY]
                )
                worldModel.GetEntity()!!.SetShaderParm(
                    RenderWorld.SHADERPARM_TIMEOFFSET,
                    renderEntity!!.shaderParms[RenderWorld.SHADERPARM_TIMEOFFSET]
                )
            }

            // calculate the muzzle position
            if (barrelJointView != Model.INVALID_JOINT && projectileDict.GetBool("launchFromBarrel")) {
                // there is an explicit joint for the muzzle
                GetGlobalJointTransform(true, barrelJointView, muzzleOrigin, muzzleAxis)
            } else {
                // go straight out of the view
                muzzleOrigin.set(playerViewOrigin)
                muzzleAxis.set(playerViewAxis)
            }

            // add some to the kick time, incrementally moving repeat firing weapons back
            if (kick_endtime < Game_local.gameLocal.realClientTime) {
                kick_endtime = Game_local.gameLocal.realClientTime
            }
            kick_endtime += muzzle_kick_time
            if (kick_endtime > Game_local.gameLocal.realClientTime + muzzle_kick_maxtime) {
                kick_endtime = Game_local.gameLocal.realClientTime + muzzle_kick_maxtime
            }
            if (Game_local.gameLocal.isClient) {

                // predict instant hit projectiles
                if (projectileDict.GetBool("net_instanthit")) {
                    val spreadRad = DEG2RAD(spread)
                    muzzle_pos.set(muzzleOrigin.plus(playerViewAxis[0].times(2.0f)))
                    i = 0
                    while (i < num_projectiles) {
                        ang = idMath.Sin(spreadRad * Game_local.gameLocal.random.RandomFloat())
                        spin = DEG2RAD(360.0f) * Game_local.gameLocal.random.RandomFloat()
                        dir.set(
                            playerViewAxis[0].plus(
                                playerViewAxis[2].times(ang * idMath.Sin(spin))
                                    .minus(playerViewAxis[1].times(ang * idMath.Cos(spin)))
                            )
                        )
                        dir.Normalize()
                        Game_local.gameLocal.clip.Translation(
                            tr,
                            muzzle_pos,
                            muzzle_pos.plus(dir.times(4096.0f)),
                            null,
                            idMat3.getMat3_identity(),
                            Game_local.MASK_SHOT_RENDERMODEL,
                            owner
                        )
                        if (tr.fraction < 1.0f) {
                            idProjectile.ClientPredictionCollide(
                                this,
                                projectileDict,
                                tr,
                                vec3_origin,
                                true
                            )
                        }
                        i++
                    }
                }
            } else {
                ownerBounds = owner!!.GetPhysics().GetAbsBounds()
                owner!!.AddProjectilesFired(num_projectiles)
                val spreadRad = DEG2RAD(spread)
                i = 0
                while (i < num_projectiles) {
                    ang = idMath.Sin(spreadRad * Game_local.gameLocal.random.RandomFloat())
                    spin = DEG2RAD(360.0f) * Game_local.gameLocal.random.RandomFloat()
                    dir.set(
                        playerViewAxis[0].plus(
                            playerViewAxis[2].times(ang * idMath.Sin(spin))
                                .minus(playerViewAxis[1].times(ang * idMath.Cos(spin)))
                        )
                    )
                    dir.Normalize()
                    if (projectileEnt != null) {
                        ent[0] = projectileEnt!!
                        ent[0]!!.Show()
                        ent[0]!!.Unbind()
                        projectileEnt = null
                    } else {
                        Game_local.gameLocal.SpawnEntityDef(projectileDict, ent, false)
                    }
                    if (null == ent || ent[0] !is idProjectile) {
                        val projectileName = weaponDef!!.dict.GetString("def_projectile")
                        idGameLocal.Error("'%s' is not an idProjectile", projectileName)
                    }
                    if (projectileDict.GetBool("net_instanthit")) {
                        // don't synchronize this on top of the already predicted effect
                        ent[0]!!.fl.networkSync = false
                    }
                    proj = ent[0] as idProjectile
                    proj.Create(owner, muzzleOrigin, dir)
                    projBounds.set(proj.GetPhysics().GetBounds().Rotate(proj.GetPhysics().GetAxis()))

                    // make sure the projectile starts inside the bounding box of the owner
                    if (i == 0) {
                        muzzle_pos.set(muzzleOrigin + playerViewAxis[0] * 2.0f)
                        // DG: sometimes the assertion in idBounds::operator-(const idBounds&) triggers
                        //     (would get bounding box with negative volume)
                        //     => check that before doing ownerBounds - projBounds (equivalent to the check in the assertion)
                        val obDiff = ownerBounds[1] - ownerBounds[0]
                        val pbDiff = projBounds[1] - projBounds[0]
                        val boundsSubLegal = obDiff.x > pbDiff.x && obDiff.y > pbDiff.y && obDiff.z > pbDiff.z
                        if (boundsSubLegal && (ownerBounds - projBounds).RayIntersection(
                                muzzle_pos,
                                playerViewAxis[0],
                                distance
                            )
                        ) {
                            start.set(muzzle_pos + playerViewAxis[0] * distance._val)
                        } else {
                            start.set(ownerBounds.GetCenter())
                        }
                        Game_local.gameLocal.clip.Translation(
                            tr,
                            start,
                            muzzle_pos,
                            proj.GetPhysics().GetClipModel(),
                            proj.GetPhysics().GetClipModel()!!.GetAxis(),
                            Game_local.MASK_SHOT_RENDERMODEL,
                            owner
                        )
                        muzzle_pos.set(tr.endpos)
                    }
                    proj.Launch(muzzle_pos, dir, pushVelocity, fuseOffset.value, launchPower.value, dmgPower)
                    i++
                }

                // toss the brass
                PostEventMS(EV_Weapon_EjectBrass, brassDelay)
            }

            // add the light for the muzzleflash
            if (!lightOn) {
                MuzzleFlashLight()
            }
            owner!!.WeaponFireFeedback(weaponDef!!.dict)

            // reset muzzle smoke
            weaponSmokeStartTime = Game_local.gameLocal.realClientTime
        }

        /*
         ================
         idWeapon::Event_CreateProjectile
         ================
         */
        private fun Event_CreateProjectile() {
            if (!Game_local.gameLocal.isClient) {
                val projectileEnt2 = arrayOfNulls<idEntity>(1)
                Game_local.gameLocal.SpawnEntityDef(projectileDict, projectileEnt2, false)
                projectileEnt = projectileEnt2[0]
                if (projectileEnt != null) {
                    projectileEnt!!.SetOrigin(GetPhysics().GetOrigin())
                    projectileEnt!!.Bind(owner, false)
                    projectileEnt!!.Hide()
                }
                idThread.ReturnEntity(projectileEnt)
            } else {
                idThread.ReturnEntity(null)
            }
        }

        /*
         ================
         idWeapon::Event_EjectBrass

         Toss a shell model out from the breach if the bone is present
         ================
         */
        private fun Event_EjectBrass() {
            if (!SysCvar.g_showBrass.GetBool() || !owner!!.CanShowWeaponViewmodel()) {
                return
            }
            if (ejectJointView == Model.INVALID_JOINT || 0 == brassDict.GetNumKeyVals()) {
                return
            }
            if (Game_local.gameLocal.isClient) {
                return
            }
            val axis = idMat3()
            val origin = idVec3()
            val linear_velocity = idVec3()
            val angular_velocity = idVec3()
            val ent = arrayOfNulls<idEntity>(1)
            if (!GetGlobalJointTransform(true, ejectJointView, origin, axis)) {
                return
            }
            Game_local.gameLocal.SpawnEntityDef(brassDict, ent, false)
            if (ent[0] == null || ent[0] !is idDebris) {
                idGameLocal.Error(
                    "'%s' is not an idDebris",
                    if (weaponDef != null) weaponDef!!.dict.GetString("def_ejectBrass") else "def_ejectBrass"
                )
            }
            val debris = ent[0] as idDebris
            debris.Create(owner, origin, axis)
            debris.Launch()
            linear_velocity.set(
                playerViewAxis[0].plus(playerViewAxis[1].plus(playerViewAxis[2])).times(40.0f)
            )
            angular_velocity.set(
                10 * Game_local.gameLocal.random.CRandomFloat(),
                10 * Game_local.gameLocal.random.CRandomFloat(),
                10 * Game_local.gameLocal.random.CRandomFloat()
            )
            debris.GetPhysics().SetLinearVelocity(linear_velocity)
            debris.GetPhysics().SetAngularVelocity(angular_velocity)
        }

        /*
         =====================
         idWeapon::Event_Melee
         =====================
         */
        private fun Event_Melee() {
            val ent: idEntity?
            val tr = trace_s()
            if (null == meleeDef) {
                idGameLocal.Error("No meleeDef on '%s'", weaponDef!!.dict.GetString("classname"))
            }
            if (!Game_local.gameLocal.isClient) {
                val start = idVec3(playerViewOrigin)
                val end = idVec3(
                    start.plus(
                        playerViewAxis[0].times(meleeDistance * owner!!.PowerUpModifier(Player.MELEE_DISTANCE))
                    )
                )
                Game_local.gameLocal.clip.TracePoint(tr, start, end, Game_local.MASK_SHOT_RENDERMODEL, owner)
                ent = if (tr.fraction < 1.0f) {
                    Game_local.gameLocal.GetTraceEntity(tr)
                } else {
                    null
                }
                if (SysCvar.g_debugWeapon.GetBool()) {
                    Game_local.gameRenderWorld!!.DebugLine(colorYellow, start, end, 100)
                    if (ent != null) {
                        Game_local.gameRenderWorld!!.DebugBounds(
                            colorRed,
                            ent.GetPhysics().GetBounds(),
                            ent.GetPhysics().GetOrigin(),
                            100
                        )
                    }
                }
                var hit = false
                var hitSound = meleeDef!!.dict.GetString("snd_miss")
                if (ent != null) {
                    val push = meleeDef!!.dict.GetFloat("push")
                    val impulse = idVec3(tr.c.normal.times(-push * owner!!.PowerUpModifier(Player.SPEED)))
                    if (Game_local.gameLocal.world!!.spawnArgs.GetBool("no_Weapons") && (ent is idActor || ent is idAFAttachment)) {
                        idThread.ReturnInt(0)
                        return
                    }
                    ent.ApplyImpulse(this, tr.c.id, tr.c.point, impulse)

                    // weapon stealing - do this before damaging so weapons are not dropped twice
                    if (Game_local.gameLocal.isMultiplayer
                        && weaponDef != null && weaponDef!!.dict.GetBool("stealing")
                        && ent is idPlayer
                        && !owner!!.PowerUpActive(Player.BERSERK)
                        && (Game_local.gameLocal.gameType != gameType_t.GAME_TDM || Game_local.gameLocal.serverInfo.GetBool(
                            "si_teamDamage"
                        ) || owner!!.team != ent.team)
                    ) {
                        owner!!.StealWeapon(ent)
                    }
                    if (ent.fl.takedamage) {
                        val kickDir = idVec3()
                        val globalKickDir = idVec3()
                        meleeDef!!.dict.GetVector("kickDir", "0 0 0", kickDir)
                        globalKickDir.set(muzzleAxis.times(kickDir))
                        ent.Damage(
                            owner,
                            owner,
                            globalKickDir,
                            meleeDefName.toString(),
                            owner!!.PowerUpModifier(Player.MELEE_DAMAGE),
                            tr.c.id
                        )
                        hit = true
                    }
                    if (weaponDef!!.dict.GetBool("impact_damage_effect")) {
                        if (ent.spawnArgs.GetBool("bleed")) {
                            hitSound =
                                meleeDef!!.dict.GetString(if (owner!!.PowerUpActive(Player.BERSERK)) "snd_hit_berserk" else "snd_hit")
                            ent.AddDamageEffect(tr, impulse, meleeDef!!.dict.GetString("classname"))
                        } else {
                            var type = tr.c.material!!.GetSurfaceType()
                            if (type == surfTypes_t.SURFTYPE_NONE) {
                                type = surfTypes_t.values()[GetDefaultSurfaceType()]
                            }
                            val materialType = Game_local.gameLocal.sufaceTypeNames[type.ordinal]

                            // start impact sound based on material type
                            hitSound = meleeDef!!.dict.GetString(Str.va("snd_%s", materialType))
                            if (hitSound.isEmpty()) {
                                hitSound = meleeDef!!.dict.GetString("snd_metal")
                            }
                            if (Game_local.gameLocal.time > nextStrikeFx) {
                                val decal: String?
                                // project decal
                                decal = weaponDef!!.dict.GetString("mtr_strike")
                                if (decal.isNotEmpty()) {
                                    Game_local.gameLocal.ProjectDecal(
                                        tr.c.point,
                                        tr.c.normal.unaryMinus(),
                                        8.0f,
                                        true,
                                        6.0f,
                                        decal
                                    )
                                }
                                nextStrikeFx = Game_local.gameLocal.time + 200
                            } else {
                                hitSound = ""
                            }
                            strikeSmokeStartTime = Game_local.gameLocal.time
                            strikePos.set(tr.c.point)
                            strikeAxis.set(tr.endAxis.unaryMinus())
                        }
                    }
                }
                if (hitSound.isNotEmpty()) {
                    val snd = DeclManager.declManager.FindSound(hitSound)
                    StartSoundShader(snd, gameSoundChannel_t.SND_CHANNEL_BODY2.ordinal, 0, true)
                }
                idThread.ReturnInt(hit)
                owner!!.WeaponFireFeedback(weaponDef!!.dict)
                return
            }
            idThread.ReturnInt(0)
            owner!!.WeaponFireFeedback(weaponDef!!.dict)
        }

        /*
         =====================
         idWeapon::Event_GetWorldModel
         =====================
         */
        private fun Event_GetWorldModel() {
            idThread.ReturnEntity(worldModel.GetEntity())
        }

        /*
         =====================
         idWeapon::Event_AllowDrop
         =====================
         */
        private fun Event_AllowDrop(allow: idEventArg<Int>) {
            allowDrop = allow.value != 0
        }

        /*
         ===============
         idWeapon::Event_AutoReload
         ===============
         */
        private fun Event_AutoReload() {
            assert(owner != null)
            if (Game_local.gameLocal.isClient) {
                idThread.ReturnFloat(0.0f)
                return
            }
            idThread.ReturnFloat(
                TempDump.btoi(Game_local.gameLocal.userInfo[owner!!.entityNumber].GetBool("ui_autoReload")).toFloat()
            )
        }

        /*
         ===============
         idWeapon::Event_NetReload
         ===============
         */
        private fun Event_NetReload() {
            assert(owner != null)
            if (Game_local.gameLocal.isServer) {
                ServerSendEvent(EVENT_RELOAD, null, false, -1)
            }
        }

        /*
         ===============
         idWeapon::Event_IsInvisible
         ===============
         */
        private fun Event_IsInvisible() {
            if (null == owner) {
                idThread.ReturnFloat(0.0f)
                return
            }
            idThread.ReturnFloat(if (owner!!.PowerUpActive(Player.INVISIBILITY)) 1.0f else 0.0f)
        }

        /*
         ===============
         idWeapon::Event_NetEndReload
         ===============
         */
        private fun Event_NetEndReload() {
            assert(owner != null)
            if (Game_local.gameLocal.isServer) {
                ServerSendEvent(EVENT_ENDRELOAD, null, false, -1)
            }
        }

        override fun oSet(oGet: idClass?) {
            throw UnsupportedOperationException("Not supported yet.")
        }

        override fun GetType(): idTypeInfo = Type
        override fun CreateInstance(): idClass = idWeapon()

        override fun getEventCallBack(event: idEventDef): eventCallback_t<*>? {
            return eventCallbacks[event]
        }

        /* **********************************************************************

         init

         ***********************************************************************/
        init {
            worldModel = idEntityPtr()
            weaponDef = null
            thread = null
            guiLight = renderLight_s()
            muzzleFlash = renderLight_s()
            worldMuzzleFlash = renderLight_s()
            nozzleGlow = renderLight_s()
            muzzleFlashEnd = 0
            flashColor = vec3_origin
            muzzleFlashHandle = -1
            worldMuzzleFlashHandle = -1
            guiLightHandle = -1
            nozzleGlowHandle = -1
            modelDefHandle = -1
            berserk = 2
            brassDelay = 0
            allowDrop = true
            state.set("")
            idealState = idStr()
            playerViewOrigin = idVec3()
            playerViewAxis = idMat3()
            viewWeaponOrigin = idVec3()
            viewWeaponAxis = idMat3()
            muzzleOrigin = idVec3()
            muzzleAxis = idMat3()
            pushVelocity = idVec3()
            projectileDict = idDict()
            meleeDefName = idStr()
            brassDict = idDict()
            icon = idStr()
            muzzle_kick_angles = idAngles()
            muzzle_kick_offset = idVec3()
            strikePos = idVec3()
            nozzleGlowColor = idVec3()
            Clear()
            fl.networkSync = true
        }
    }
}

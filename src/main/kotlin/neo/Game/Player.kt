/*
 * Copyright (C) 1999-2011 id Software LLC, a ZeniMax Media company.
 * Translated to Kotlin by Dr. Feederino with support of Claude Code
 *
 * This file is part of the Doom 3 Kotlin project.
 * Original source: neo/Game/Player.cpp, neo/Game/Player.h
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

import neo.Game.AI.AAS.idAAS
import neo.Game.AI.idAI
import neo.Game.AI.talkState_t
import neo.Game.Animation.Anim
import neo.Game.Animation.Anim.jointModTransform_t
import neo.Game.GameEdit.idDragEntity
import neo.Game.GameSys.Class.*
import neo.Game.GameSys.EV_Remove
import neo.Game.GameSys.Event.idEventDef
import neo.Game.GameSys.SaveGame.idRestoreGame
import neo.Game.GameSys.SaveGame.idSaveGame
import neo.Game.GameSys.SysCvar
import neo.Game.Game_local.*
import neo.Game.Game_local.Companion.gameLocal
import neo.Game.Game_local.Companion.isD3XP
import neo.Game.MultiplayerGame.gameType_t
import neo.Game.MultiplayerGame.idMultiplayerGame
import neo.Game.Physics.Clip.idClipModel
import neo.Game.Physics.Physics.idPhysics
import neo.Game.Physics.Physics_Player.idPhysics_Player
import neo.Game.Physics.Physics_Player.pmtype_t
import neo.Game.Physics.Physics_Player.waterLevel_t
import neo.Game.PlayerIcon.idPlayerIcon
import neo.Game.PlayerView.idPlayerView
import neo.Game.Projectile.idProjectile
import neo.Game.Script.Script_Program.idScriptBool
import neo.Game.Script.Script_Thread.idThread
import neo.Game.Weapon.AMMO_NUMTYPES
import neo.Game.Weapon.idWeapon
import neo.Renderer.Material
import neo.Renderer.Material.shaderStage_t
import neo.Renderer.Model
import neo.Renderer.RenderSystem
import neo.Renderer.RenderWorld
import neo.Renderer.RenderWorld.guiPoint_t
import neo.Renderer.RenderWorld.portalConnection_t
import neo.Renderer.RenderWorld.renderEntity_s
import neo.Renderer.RenderWorld.renderView_s
import neo.Sound.snd_shader
import neo.TempDump
import neo.TempDump.atof
import neo.Tools.Compilers.AAS.AASFile
import neo.cm.CM_BOX_EPSILON
import neo.cm.CM_CLIP_EPSILON
import neo.cm.trace_s
import neo.framework.*
import neo.framework.Async.NetworkSystem
import neo.framework.DeclEntityDef.idDeclEntityDef
import neo.framework.DeclManager.declType_t
import neo.framework.DeclPDA.*
import neo.framework.DeclSkin.idDeclSkin
import neo.framework.UsercmdGen.usercmd_t
import neo.idlib.BV.idBounds
import neo.idlib.BitMsg.idBitMsg
import neo.idlib.BitMsg.idBitMsgDelta
import neo.idlib.Dict_h.idDict
import neo.idlib.Dict_h.idKeyValue
import neo.idlib.Text.Lexer.idLexer
import neo.idlib.Text.Str
import neo.idlib.Text.Str.idStr
import neo.idlib.Text.Str.idStr.Companion.FindText
import neo.idlib.Text.Token.idToken
import neo.idlib.colorBlack
import neo.idlib.colorRed
import neo.idlib.colorWhite
import neo.idlib.containers.*
import neo.idlib.containers.List.idList
import neo.idlib.geometry.TraceModel.idTraceModel
import neo.idlib.idLib
import neo.idlib.math.*
import neo.idlib.math.Interpolate.idInterpolate
import neo.idlib.math.Matrix.idMat3
import neo.sys.sysEvent_s
import neo.ui.UserInterface
import neo.ui.UserInterface.idUserInterface
import java.nio.ByteBuffer
import java.util.*
import kotlin.experimental.and
import kotlin.math.*


val EV_Player_DisableWeapon: idEventDef = idEventDef("disableWeapon")
val EV_Player_EnableWeapon: idEventDef = idEventDef("enableWeapon")
val EV_Player_ExitTeleporter: idEventDef = idEventDef("exitTeleporter")
val EV_Player_GetButtons: idEventDef = idEventDef("getButtons", null, 'd')
val EV_Player_GetCurrentWeapon: idEventDef = idEventDef("getCurrentWeapon", null, 's')
val EV_Player_GetIdealWeapon: idEventDef = idEventDef("getIdealWeapon", null, 's')
val EV_Player_GetMove: idEventDef = idEventDef("getMove", null, 'v')
val EV_Player_GetPreviousWeapon: idEventDef = idEventDef("getPreviousWeapon", null, 's')
val EV_Player_GetViewAngles: idEventDef = idEventDef("getViewAngles", null, 'v')
val EV_Player_GetWeaponEntity: idEventDef = idEventDef("getWeaponEntity", null, 'e')
val EV_Player_HideTip: idEventDef = idEventDef("hideTip")
val EV_Player_InPDA: idEventDef = idEventDef("inPDA", null, 'd')
val EV_Player_LevelTrigger: idEventDef = idEventDef("levelTrigger")
val EV_Player_OpenPDA: idEventDef = idEventDef("openPDA")
val EV_Player_SelectWeapon: idEventDef = idEventDef("selectWeapon", "s")
val EV_Player_StopAudioLog: idEventDef = idEventDef("stopAudioLog")
val EV_Player_StopFxFov: idEventDef = idEventDef("stopFxFov")
val EV_SpectatorTouch: idEventDef = idEventDef("spectatorTouch", "et")

// D3XP player events
val EV_Player_GiveInventoryItem: idEventDef = idEventDef("giveInventoryItem", "s")
val EV_Player_RemoveInventoryItem: idEventDef = idEventDef("removeInventoryItem", "s")
val EV_Player_SetPowerupTime: idEventDef = idEventDef("setPowerupTime", "dd")
val EV_Player_IsPowerupActive: idEventDef = idEventDef("isPowerupActive", "d", 'd')
val EV_Player_WeaponAvailable: idEventDef = idEventDef("weaponAvailable", "s", 'd')
val EV_Player_StartWarp: idEventDef = idEventDef("startWarp")
val EV_Player_StopHelltime: idEventDef = idEventDef("stopHelltime", "d")
val EV_Player_ToggleBloom: idEventDef = idEventDef("toggleBloom", "d")
val EV_Player_SetBloomParms: idEventDef = idEventDef("setBloomParms", "ff")

object Player {
    const val ADRENALINE = 3

    // D3XP powerups (continue the enum after ADRENALINE)
    const val INVULNERABILITY = 4
    const val HELLTIME = 5     // slow-mo for the player (player on fast timeline, world on slow)
    const val ENVIROSUIT = 6   // environment protection suit
    const val ENVIROTIME = 7   // environmental time (used for durability UI)

    //
    val ASYNC_PLAYER_INV_AMMO_BITS = idMath.BitsForInteger(999) // 9 bits to cover the range [0, 999]
    const val ASYNC_PLAYER_INV_CLIP_BITS = -7 // -7 bits to cover the range [-1, 60]
    const val BASE_HEARTRATE = 70 // default

    //
    // powerups - the "type" in item .def must match
    // enum {
    const val BERSERK = 0

    //
    const val DEAD_HEARTRATE = 0 // fall to as you die
    const val DEATH_VOLUME = 15 // volume at death
    const val DMG_VOLUME = 5 // volume when taking damage
    const val DYING_HEARTRATE = 30 // used for volumen calc when dying/dead

    /*
     ===============================================================================

     Player control of the Doom Marine.
     This object handles all player movement and world interaction.

     ===============================================================================
     */

    const val FOCUS_GUI_TIME = 500
    const val FOCUS_TIME = 300

    //
    const val HEALTHPULSE_TIME = 333

    //
    // amount of health per dose from the health station
    const val HEALTH_PER_DOSE = 10
    const val INFLUENCE_LEVEL1 = 1 // no gun or hud
    const val INFLUENCE_LEVEL2 = 2 // no gun, hud, movement
    const val INFLUENCE_LEVEL3 = 3 // slow player movement

    // };
    //
    // influence levels
    // enum {
    const val INFLUENCE_NONE = 0 // none
    const val INVISIBILITY = 1

    //
    // distance between ladder rungs (actually is half that distance, but this sounds better)
    const val LADDER_RUNG_DISTANCE = 32
    const val LAND_DEFLECT_TIME = 150
    const val LAND_RETURN_TIME = 300
    const val LOWHEALTH_HEARTRATE_ADJ = 20 //
    const val MAX_HEARTRATE = 130 // maximum
    const val MAX_INVENTORY_ITEMS = 20
    const val MAX_PDAS = 64
    const val MAX_PDA_ITEMS = 128
    const val MAX_POWERUPS = 8  // D3XP: 4 → 8 (BERSERK, INVISIBILITY, MEGAHEALTH, ADRENALINE + 4 D3XP powerups)

    //
    //
    //
    const val MAX_RESPAWN_TIME = 10000

    //
    const val MAX_WEAPONS = 32  // D3XP: 16 → 32 (expansion adds new weapons)
    const val MEGAHEALTH = 2
    const val MELEE_DAMAGE = 2

    //
    const val MELEE_DISTANCE = 3

    //
    // minimum speed to bob and play run/walk animations at
    const val MIN_BOB_SPEED = 5.0f
    const val PROJECTILE_DAMAGE = 1
    const val RAGDOLL_DEATH_TIME = 3000

    //
    const val SAVING_THROW_TIME = 5000 // maximum one "saving throw" every five seconds

    //
    // how many units to raise spectator above default view height so it's in the head of someone
    const val SPECTATE_RAISE = 25

    // };
    //
    // powerup modifiers
    // enum {
    const val SPEED = 0
    const val STEPUP_TIME = 200

    //
    const val THIRD_PERSON_FOCUS_DISTANCE = 512.0f

    //
    // time before a weapon dropped to the floor disappears
    const val WEAPON_DROP_TIME = 20 * 1000

    //
    // time before a next or prev weapon switch happens
    const val WEAPON_SWITCH_DELAY = 150
    const val ZEROSTAMINA_HEARTRATE = 115 // no stamina
    const val ZERO_VOLUME = -40 // volume at zero

    class idItemInfo {
        var icon: idStr = idStr()
        var name: idStr = idStr()
    }

    class idObjectiveInfo {
        var screenshot: idStr = idStr()
        var text: idStr = idStr()
        var title: idStr = idStr()
    }

    class idLevelTriggerInfo {
        var levelName: idStr = idStr()
        var triggerName: idStr = idStr()
    }

    // D3XP: ammo type that recharges over time (e.g. soul cube charges)
    class RechargeAmmo_t {
        var ammo: Int = 0           // current recharge amount
        var rechargeTime: Int = 0   // ms between recharges
        var ammoName: String = ""   // ammo type name (max 128 chars in C++)
    }

    // D3XP: weapon group for weapon-toggle cycling (e.g. cycle between pistol variants)
    class WeaponToggle_t {
        var name: String = ""                   // group name (max 64 chars in C++)
        val toggleList: idList<Int> = idList()  // weapon indices in this toggle group
    }

    // };
    class idInventory {
        val ammo: IntArray = IntArray(AMMO_NUMTYPES)
        val clip: IntArray = IntArray(MAX_WEAPONS)
        val pdasViewed: IntArray = IntArray(4) // 128 bit flags for indicating if a pda has been viewed
        val powerupEndTime: IntArray = IntArray(MAX_POWERUPS)

        // D3XP: ammo types that recharge automatically over time
        val rechargeAmmo: Array<RechargeAmmo_t> = Array(AMMO_NUMTYPES) { RechargeAmmo_t() }
        // mp
        var ammoPredictTime = 0
        var ammoPulse = false
        var armor = 0
        var armorPulse = false
        var deplete_ammount = 0
        var deplete_armor = 0
        var deplete_rate = 0.0f
        var emails: idStrList
        val items: idList<idDict>
        var lastGiveTime = 0
        val levelTriggers: idList<idLevelTriggerInfo>
        var maxHealth = 0
        var maxarmor = 0
        var nextArmorDepleteTime = 0
        var nextItemNum = 0
        var nextItemPickup = 0
        val objectiveNames: idList<idObjectiveInfo> = idList()
        var onePickupTime = 0
        var pdaOpened = false
        var pdaSecurity: idStrList
        var pdas: idStrList
        val pickupItemNames = idList(idItemInfo::class.java)
        var powerups = 0
        var selAudio = 0
        var selEMail = 0
        var selPDA = 0
        var selVideo = 0
        var turkeyScore = false
        var videos: idStrList
        var weaponPulse = false
        var weapons = 0

        // save games
        // archives object for save game file
        fun Save(savefile: idSaveGame) {
            var i: Int
            savefile.WriteInt(maxHealth)
            savefile.WriteInt(weapons)
            savefile.WriteInt(powerups)
            savefile.WriteInt(armor)
            savefile.WriteInt(maxarmor)
            savefile.WriteInt(ammoPredictTime)
            savefile.WriteInt(deplete_armor)
            savefile.WriteFloat(deplete_rate)
            savefile.WriteInt(deplete_ammount)
            savefile.WriteInt(nextArmorDepleteTime)
            i = 0
            while (i < AMMO_NUMTYPES) {
                savefile.WriteInt(ammo[i])
                i++
            }
            i = 0
            while (i < MAX_WEAPONS) {
                savefile.WriteInt(clip[i])
                i++
            }
            i = 0
            while (i < MAX_POWERUPS) {
                savefile.WriteInt(powerupEndTime[i])
                i++
            }
            savefile.WriteInt(items.Num())
            i = 0
            while (i < items.Num()) {
                savefile.WriteDict(items[i])
                i++
            }
            savefile.WriteInt(pdasViewed[0])
            savefile.WriteInt(pdasViewed[1])
            savefile.WriteInt(pdasViewed[2])
            savefile.WriteInt(pdasViewed[3])
            savefile.WriteInt(selPDA)
            savefile.WriteInt(selVideo)
            savefile.WriteInt(selEMail)
            savefile.WriteInt(selAudio)
            savefile.WriteBool(pdaOpened)
            savefile.WriteBool(turkeyScore)
            savefile.WriteInt(pdas.size())
            i = 0
            while (i < pdas.size()) {
                savefile.WriteString(pdas[i])
                i++
            }
            savefile.WriteInt(pdaSecurity.size())
            i = 0
            while (i < pdaSecurity.size()) {
                savefile.WriteString(pdaSecurity[i])
                i++
            }
            savefile.WriteInt(videos.size())
            i = 0
            while (i < videos.size()) {
                savefile.WriteString(videos[i])
                i++
            }
            savefile.WriteInt(emails.size())
            i = 0
            while (i < emails.size()) {
                savefile.WriteString(emails[i])
                i++
            }
            savefile.WriteInt(nextItemPickup)
            savefile.WriteInt(nextItemNum)
            savefile.WriteInt(onePickupTime)
            savefile.WriteInt(pickupItemNames.Num())
            i = 0
            while (i < pickupItemNames.Num()) {
                savefile.WriteString(pickupItemNames[i].icon)
                savefile.WriteString(pickupItemNames[i].name)
                i++
            }
            savefile.WriteInt(objectiveNames.Num())
            i = 0
            while (i < objectiveNames.Num()) {
                savefile.WriteString(objectiveNames[i].screenshot)
                savefile.WriteString(objectiveNames[i].text)
                savefile.WriteString(objectiveNames[i].title)
                i++
            }
            savefile.WriteInt(levelTriggers.Num())
            i = 0
            while (i < levelTriggers.Num()) {
                savefile.WriteString(levelTriggers[i].levelName)
                savefile.WriteString(levelTriggers[i].triggerName)
                i++
            }
            savefile.WriteBool(ammoPulse)
            savefile.WriteBool(weaponPulse)
            savefile.WriteBool(armorPulse)
            savefile.WriteInt(lastGiveTime)
        }

        // unarchives object from save game file
        fun Restore(savefile: idRestoreGame) {
            var i: Int
            var num: Int
            maxHealth = savefile.ReadInt()
            weapons = savefile.ReadInt()
            powerups = savefile.ReadInt()
            armor = savefile.ReadInt()
            maxarmor = savefile.ReadInt()
            ammoPredictTime = savefile.ReadInt()
            deplete_armor = savefile.ReadInt()
            deplete_rate = savefile.ReadFloat()
            deplete_ammount = savefile.ReadInt()
            nextArmorDepleteTime = savefile.ReadInt()
            i = 0
            while (i < AMMO_NUMTYPES) {
                ammo[i] = savefile.ReadInt()
                i++
            }
            i = 0
            while (i < MAX_WEAPONS) {
                clip[i] = savefile.ReadInt()
                i++
            }
            i = 0
            while (i < MAX_POWERUPS) {
                powerupEndTime[i] = savefile.ReadInt()
                i++
            }
            num = savefile.ReadInt()
            i = 0
            while (i < num) {
                val itemdict = savefile.ReadDict()!!
                items.Append(itemdict)
                i++
            }

            // pdas
            pdasViewed[0] = savefile.ReadInt()
            pdasViewed[1] = savefile.ReadInt()
            pdasViewed[2] = savefile.ReadInt()
            pdasViewed[3] = savefile.ReadInt()
            selPDA = savefile.ReadInt()
            selVideo = savefile.ReadInt()
            selEMail = savefile.ReadInt()
            selAudio = savefile.ReadInt()
            pdaOpened = savefile.ReadBool()
            turkeyScore = savefile.ReadBool()
            num = savefile.ReadInt()
            i = 0
            while (i < num) {
                val strPda = idStr()
                savefile.ReadString(strPda)
                pdas.add(strPda.toString())
                i++
            }

            // pda security clearances
            num = savefile.ReadInt()
            i = 0
            while (i < num) {
                val invName = idStr()
                savefile.ReadString(invName)
                pdaSecurity.add(invName.toString())
                i++
            }

            // videos
            num = savefile.ReadInt()
            i = 0
            while (i < num) {
                val strVideo = idStr()
                savefile.ReadString(strVideo)
                videos.add(strVideo.toString())
                i++
            }

            // email
            num = savefile.ReadInt()
            i = 0
            while (i < num) {
                val strEmail = idStr()
                savefile.ReadString(strEmail)
                emails.add(strEmail.toString())
                i++
            }
            nextItemPickup = savefile.ReadInt()
            nextItemNum = savefile.ReadInt()
            onePickupTime = savefile.ReadInt()
            num = savefile.ReadInt()
            i = 0
            while (i < num) {
                val info = idItemInfo()
                savefile.ReadString(info.icon)
                savefile.ReadString(info.name)
                pickupItemNames.Append(info)
                i++
            }
            num = savefile.ReadInt()
            i = 0
            while (i < num) {
                val obj = idObjectiveInfo()
                savefile.ReadString(obj.screenshot)
                savefile.ReadString(obj.text)
                savefile.ReadString(obj.title)
                objectiveNames.Append(obj)
                i++
            }
            num = savefile.ReadInt()
            i = 0
            while (i < num) {
                val lti = idLevelTriggerInfo()
                savefile.ReadString(lti.levelName)
                savefile.ReadString(lti.triggerName)
                levelTriggers.Append(lti)
                i++
            }
            ammoPulse = savefile.ReadBool()
            weaponPulse = savefile.ReadBool()
            armorPulse = savefile.ReadBool()
            lastGiveTime = savefile.ReadInt()
        }

        fun Clear() {
            maxHealth = 0
            weapons = 0
            powerups = 0
            armor = 0
            maxarmor = 0
            deplete_armor = 0
            deplete_rate = 0.0f
            deplete_ammount = 0
            nextArmorDepleteTime = 0

//	memset( ammo, 0, sizeof( ammo ) );
            Arrays.fill(ammo, 0)
            ClearPowerUps()

            // set to -1 so that the gun knows to have a full clip the first time we get it and at the start of the level
//	memset( clip, -1, sizeof( clip ) );
            Arrays.fill(clip, -1)
            items.DeleteContents(true)
            //	memset(pdasViewed, 0, 4 * sizeof( pdasViewed[0] ) );
            Arrays.fill(pdasViewed, 0)
            pdas.clear()
            videos.clear()
            emails.clear()
            selVideo = 0
            selEMail = 0
            selPDA = 0
            selAudio = 0
            pdaOpened = false
            turkeyScore = false

            levelTriggers.Clear()

            nextItemPickup = 0
            nextItemNum = 1
            onePickupTime = 0
            pickupItemNames.Clear()
            objectiveNames.Clear()

            ammoPredictTime = 0
            lastGiveTime = 0

            ammoPulse = false
            weaponPulse = false
            armorPulse = false
        }

        fun GivePowerUp(player: idPlayer, powerup: Int, msec: Int) {
            var msec = msec
            if (0 == msec) {
                // get the duration from the .def files
                var def: idDeclEntityDef? = null
                when (powerup) {
                    BERSERK -> def = gameLocal.FindEntityDef("powerup_berserk", false)
                    INVISIBILITY -> def = gameLocal.FindEntityDef("powerup_invisibility", false)
                    MEGAHEALTH -> def = gameLocal.FindEntityDef("powerup_megahealth", false)
                    ADRENALINE -> def = gameLocal.FindEntityDef("powerup_adrenaline", false)
                }
                assert(def != null)
                msec = def!!.dict.GetInt("time") * 1000
            }
            powerups = powerups or (1 shl powerup)
            powerupEndTime[powerup] = gameLocal.time + msec
        }

        fun ClearPowerUps() {
            var i: Int
            i = 0
            while (i < MAX_POWERUPS) {
                powerupEndTime[i] = 0
                i++
            }
            powerups = 0
        }

        fun GetPersistantData(dict: idDict) {
            var i: Int
            var num: Int
            var item: idDict
            var key: String
            var kv: idKeyValue?
            var name: String?

            // armor
            dict.SetInt("armor", armor)

            // don't bother with powerups, maxhealth, maxarmor, or the clip
            // ammo
            i = 0
            while (i < AMMO_NUMTYPES) {
                name = idWeapon.GetAmmoNameForNum(i)
                if (name != null) {
                    dict.SetInt(name, ammo[i])
                }
                i++
            }

            // items
            num = 0
            i = 0
            while (i < items.Num()) {
                item = items[i]

                // copy all keys with "inv_"
                kv = item.MatchPrefix("inv_")
                if (kv != null) {
                    while (kv != null) {
                        key = String.format("item_%d %s", num, kv.GetKey())
                        dict.Set(key, kv.GetValue())
                        kv = item.MatchPrefix("inv_", kv)
                    }
                    num++
                }
                i++
            }
            dict.SetInt("items", num)

            // pdas viewed
            i = 0
            while (i < 4) {
                dict.SetInt(Str.va("pdasViewed_%d", i), pdasViewed[i])
                i++
            }
            dict.SetInt("selPDA", selPDA)
            dict.SetInt("selVideo", selVideo)
            dict.SetInt("selEmail", selEMail)
            dict.SetInt("selAudio", selAudio)
            dict.SetInt("pdaOpened", TempDump.btoi(pdaOpened))
            dict.SetInt("turkeyScore", TempDump.btoi(turkeyScore))

            // pdas
            i = 0
            while (i < pdas.size()) {
                key = String.format("pda_%d", i)
                dict.Set(key, pdas[i])
                i++
            }
            dict.SetInt("pdas", pdas.size())

            // video cds
            i = 0
            while (i < videos.size()) {
                key = String.format("video_%d", i)
                dict.Set(key, videos[i])
                i++
            }
            dict.SetInt("videos", videos.size())

            // emails
            i = 0
            while (i < emails.size()) {
                key = String.format("email_%d", i)
                dict.Set(key, emails[i])
                i++
            }
            dict.SetInt("emails", emails.size())

            // weapons
            dict.SetInt("weapon_bits", weapons)
            dict.SetInt("levelTriggers", levelTriggers.Num())
            i = 0
            while (i < levelTriggers.Num()) {
                key = String.format("levelTrigger_Level_%d", i)
                dict.Set(key, levelTriggers[i].levelName)
                key = String.format("levelTrigger_Trigger_%d", i)
                dict.Set(key, levelTriggers[i].triggerName)
                i++
            }
        }

        fun RestoreInventory(owner: idPlayer, dict: idDict) {
            var i: Int
            var num: Int
            var item: idDict
            val key = idStr()
            var itemname: String
            var kv: idKeyValue?
            var name: String?
            Clear()

            // health/armor
            maxHealth = dict.GetInt("maxhealth", "100")
            armor = dict.GetInt("armor", "50")
            maxarmor = dict.GetInt("maxarmor", "100")
            deplete_armor = dict.GetInt("deplete_armor", "0")
            deplete_rate = dict.GetFloat("deplete_rate", "2.0f")
            deplete_ammount = dict.GetInt("deplete_ammount", "1")

            // the clip and powerups aren't restored
            // ammo
            i = 0
            while (i < AMMO_NUMTYPES) {
                name = idWeapon.GetAmmoNameForNum(i)
                if (name != null) {
                    ammo[i] = dict.GetInt(name)
                }
                i++
            }

            // items
            num = dict.GetInt("items")
            items.SetNum(num)
            i = 0
            while (i < num) {
                item = idDict()
                items[i] = item
                itemname = String.format("item_%d ", i)
                kv = dict.MatchPrefix(itemname)
                while (kv != null) {
                    key.set(kv.GetKey())
                    key.Strip(itemname)
                    item.Set(key, kv.GetValue())
                    kv = dict.MatchPrefix(itemname, kv)
                }
                i++
            }

            // pdas viewed
            i = 0
            while (i < 4) {
                pdasViewed[i] = dict.GetInt(Str.va("pdasViewed_%d", i))
                i++
            }
            selPDA = dict.GetInt("selPDA")
            selEMail = dict.GetInt("selEmail")
            selVideo = dict.GetInt("selVideo")
            selAudio = dict.GetInt("selAudio")
            pdaOpened = dict.GetBool("pdaOpened")
            turkeyScore = dict.GetBool("turkeyScore")

            // pdas
            num = dict.GetInt("pdas")
            pdas.setSize(num)
            i = 0
            while (i < num) {
                itemname = String.format("pda_%d", i)
                pdas[i] = dict.GetString(itemname, "default")!!
                i++
            }

            // videos
            num = dict.GetInt("videos")
            videos.setSize(num)
            i = 0
            while (i < num) {
                itemname = String.format("video_%d", i)
                videos[i] = dict.GetString(itemname, "default")!!
                i++
            }

            // emails
            num = dict.GetInt("emails")
            emails.setSize(num)
            i = 0
            while (i < num) {
                itemname = String.format("email_%d", i)
                emails[i] = dict.GetString(itemname, "default")!!
                i++
            }

            // weapons are stored as a number for persistant data, but as strings in the entityDef
            weapons = dict.GetInt("weapon_bits", "0")
            if (ID_DEMO_BUILD) {
                Give(owner, dict, "weapon", dict.GetString("weapon"), null, false)
            } else {
                if (SysCvar.g_skill.GetInteger() >= 3) {
                    Give(owner, dict, "weapon", dict.GetString("weapon_nightmare"), null, false)
                } else {
                    Give(owner, dict, "weapon", dict.GetString("weapon"), null, false)
                }
            }
            num = dict.GetInt("levelTriggers")
            i = 0
            while (i < num) {
                itemname = String.format("levelTrigger_Level_%d", i)
                val lti = idLevelTriggerInfo()
                lti.levelName.set(dict.GetString(itemname))
                itemname = String.format("levelTrigger_Trigger_%d", i)
                lti.triggerName.set(dict.GetString(itemname))
                levelTriggers.Append(lti)
                i++
            }
        }

        fun Give(
            owner: idPlayer, spawnArgs: idDict, statname: String, value: String, idealWeapon: CInt?, updateHud: Boolean
        ): Boolean {
            var i: Int
            var pos: Int
            var end: Int
            var len: Int
            val max: Int
            var weaponDecl: idDeclEntityDef?
            var tookWeapon: Boolean
            val amount: Int
            val name: String?
            if (0 == idStr.Icmpn(statname, "ammo_", 5)) {
                i = AmmoIndexForAmmoClass(statname)
                max = MaxAmmoForAmmoClass(owner, statname)
                if (ammo[i] >= max) {
                    return false
                }
                amount = value.toInt()
                if (amount != 0) {
                    ammo[i] += amount
                    if (max > 0 && ammo[i] > max) {
                        ammo[i] = max
                    }
                    ammoPulse = true
                    name = AmmoPickupNameForIndex(i)
                    if (name != null && name.isNotEmpty()) {
                        AddPickupName(name, "")
                    }
                }
            } else if (0 == idStr.Icmp(statname, "armor")) {
                if (armor >= maxarmor) {
                    return false // can't hold any more, so leave the item
                }
                amount = value.toInt()
                if (amount != 0) {
                    armor += amount
                    if (armor > maxarmor) {
                        armor = maxarmor
                    }
                    nextArmorDepleteTime = 0
                    armorPulse = true
                }
            } else if (FindText(statname, "inclip_") == 0) {
                i = WeaponIndexForAmmoClass(spawnArgs, statname + 7)
                if (i != -1) {
                    // set, don't add. not going over the clip size limit.
                    clip[i] = value.toInt()
                }
            } else if (0 == idStr.Icmp(statname, "berserk")) {
                GivePowerUp(owner, BERSERK, SEC2MS(value.toFloat()))
            } else if (0 == idStr.Icmp(statname, "mega")) {
                GivePowerUp(owner, MEGAHEALTH, SEC2MS(value.toFloat()))
            } else if (0 == idStr.Icmp(statname, "weapon")) {
                tookWeapon = false
                pos = 0
                while (pos != -1) {
                    end = value.indexOf(',', pos)
                    if (end != -1) {
                        len = end - pos
                        end++
                    } else {
                        len = value.length - pos
                    }

//                        idStr weaponName( pos, 0, len );
                    val weaponName = value.substring(pos, pos + len)

                    // find the number of the matching weapon name
                    i = 0
                    while (i < MAX_WEAPONS) {
                        if (weaponName == spawnArgs.GetString(Str.va("def_weapon%d", i))) {
                            break
                        }
                        i++
                    }
                    if (i >= MAX_WEAPONS) {
                        idGameLocal.Error("Unknown weapon '%s'", weaponName)
                    }

                    // cache the media for this weapon
                    weaponDecl = gameLocal.FindEntityDef(weaponName, false)

                    // don't pickup "no ammo" weapon types twice
                    // not for D3 SP .. there is only one case in the game where you can get a no ammo
                    // weapon when you might already have it, in that case it is more conistent to pick it up
                    if (gameLocal.isMultiplayer && weaponDecl != null && (weapons and (1 shl i)) != 0 && 0 == weaponDecl.dict.GetInt(
                            "ammoRequired"
                        )
                    ) {
                        pos = end
                        continue
                    }
                    if (!gameLocal.world!!.spawnArgs.GetBool("no_Weapons") || "weapon_fists" == weaponName || "weapon_soulcube" == weaponName) { //TODO:string in global vars, or local constants.
                        if ((weapons and (1 shl i)) == 0 || gameLocal.isMultiplayer) {
                            if (owner.GetUserInfo().GetBool("ui_autoSwitch") && idealWeapon != null) {
                                assert(!gameLocal.isClient)
                                idealWeapon._val = (i)
                            }
                            if (owner.hud != null && updateHud && lastGiveTime + 1000 < gameLocal.time) {
                                owner.hud!!.SetStateInt("newWeapon", i)
                                owner.hud!!.HandleNamedEvent("newWeapon")
                                lastGiveTime = gameLocal.time
                            }
                            weaponPulse = true
                            weapons = weapons or (1 shl i)
                            tookWeapon = true
                        }
                    }
                    pos = end
                }
                return tookWeapon
            } else if (0 == idStr.Icmp(statname, "item") || 0 == idStr.Icmp(
                    statname, "icon"
                ) || 0 == idStr.Icmp(statname, "name")
            ) {
                // ignore these as they're handled elsewhere
                return false
            } else {
                // unknown item
                gameLocal.Warning("Unknown stat '%s' added to player's inventory", statname)
                return false
            }
            return true
        }

        fun Drop(spawnArgs: idDict, weapon_classname: Array<String>, weapon_index: Int) {
            // remove the weapon bit
            // also remove the ammo associated with the weapon as we pushed it in the item
            var weapon_index = weapon_index
            assert(weapon_index != -1 || weapon_classname[0] != null)
            if (weapon_index == -1) {
                weapon_index = 0
                while (weapon_index < MAX_WEAPONS) {
                    if (
                        idStr.Icmp(
                            weapon_classname[0], spawnArgs.GetString(Str.va("def_weapon%d", weapon_index))
                        ) == 0
                    ) {
                        break
                    }
                    weapon_index++
                }
                if (weapon_index >= MAX_WEAPONS) {
                    idGameLocal.Error("Unknown weapon '%s'", weapon_classname[0])
                }
            } else if (null == weapon_classname[0]) {
                weapon_classname[0] = spawnArgs.GetString(Str.va("def_weapon%d", weapon_index))
            }
            weapons = weapons and (-0x1 xor (1 shl weapon_index))
            val ammo_i = AmmoIndexForWeaponClass(weapon_classname[0], null)
            if (ammo_i != 0) {
                clip[weapon_index] = -1
                ammo[ammo_i] = 0
            }
        }

        fun AmmoIndexForAmmoClass(ammo_classname: String): Int {
            return idWeapon.GetAmmoNumForName(ammo_classname)
        }

        fun MaxAmmoForAmmoClass(owner: idPlayer, ammo_classname: String?): Int {
            return owner.spawnArgs.GetInt(Str.va("max_%s", ammo_classname), "0")
        }

        // D3XP
        fun CanGive(owner: idPlayer, spawnArgs: idDict, statname: String, value: String): Boolean {
            if (idStr.Icmp(statname, "ammo_bloodstone") == 0) {
                val max = MaxAmmoForAmmoClass(owner, statname)
                val i = AmmoIndexForAmmoClass(statname)
                if (max <= 0) {
                    return true
                } else {
                    if (ammo[i] >= max) {
                        ammo[i] = max
                        return false
                    }
                    return true
                }
            } else if (idStr.Icmp(statname, "item") == 0 || idStr.Icmp(statname, "icon") == 0 || idStr.Icmp(
                    statname,
                    "name"
                ) == 0
            ) {
                return false
            }
            return true
        }

        /*
         ==============
         idInventory::WeaponIndexForAmmoClass
         mapping could be prepared in the constructor
         ==============
         */
        fun WeaponIndexForAmmoClass(spawnArgs: idDict, ammo_classname: String): Int {
            var i: Int
            var weapon_classname: String
            i = 0
            while (i < MAX_WEAPONS) {
                weapon_classname = spawnArgs.GetString(Str.va("def_weapon%d", i))
                if (null == weapon_classname) {
                    i++
                    continue
                }
                val decl = gameLocal.FindEntityDef(weapon_classname, false)
                if (null == decl) {
                    i++
                    continue
                }
                if (0 == idStr.Icmp(ammo_classname, decl.dict.GetString("ammoType"))) {
                    return i
                }
                i++
            }
            return -1
        }

        fun AmmoIndexForWeaponClass(weapon_classname: String, ammoRequired: IntArray?): Int {
            val decl = gameLocal.FindEntityDef(weapon_classname, false)
            if (null == decl) {
                idGameLocal.Error("Unknown weapon in decl '%s'", weapon_classname)
                return -1
            }
            if (ammoRequired != null) {
                ammoRequired[0] = decl.dict.GetInt("ammoRequired")
            }
            return AmmoIndexForAmmoClass(decl.dict.GetString("ammoType"))
        }

        fun AmmoPickupNameForIndex(ammonum: Int): String {
            return idWeapon.GetAmmoPickupNameForNum(ammonum)
        }

        fun AddPickupName(name: String, icon: String) {
            val num: Int
            num = pickupItemNames.Num()
            if (num == 0 || pickupItemNames[num - 1].name.Icmp(name) != 0) {
                val info = pickupItemNames.Alloc()!!
                if (idStr.Cmpn(name, Common.STRTABLE_ID, Common.STRTABLE_ID_LENGTH) == 0) {
                    info.name = idStr(Common.common.GetLanguageDict().GetString(name))
                } else {
                    info.name = idStr(name)
                }
                info.icon = idStr(icon)
            }
        }

        fun HasAmmo(type: Int, amount: Int): Int {
            if (type == 0 || 0 == amount) {
                // always allow weapons that don't use ammo to fire
                return -1
            }

            // check if we have infinite ammo
            return if (ammo[type] < 0) {
                -1
            } else ammo[type] / amount

            // return how many shots we can fire
        }

        fun UseAmmo(type: Int, amount: Int): Boolean {
            if (HasAmmo(type, amount) == 0) {
                return false
            }

            // take an ammo away if not infinite
            if (ammo[type] >= 0) {
                ammo[type] -= amount
                ammoPredictTime =
                    gameLocal.time // mp client: we predict this. mark time so we're not confused by snapshots
            }
            return true
        }

        fun HasAmmo(weapon_classname: String): Int {            // looks up the ammo information for the weapon class first
            val ammoRequired = IntArray(1)
            val ammo_i = AmmoIndexForWeaponClass(weapon_classname, ammoRequired)
            return HasAmmo(ammo_i, ammoRequired[0])
        }

        fun UpdateArmor() {
            if (deplete_armor != 0 && deplete_armor < armor) {
                if (0 == nextArmorDepleteTime) {
                    nextArmorDepleteTime = (gameLocal.time + deplete_rate * 1000).toInt()
                } else if (gameLocal.time > nextArmorDepleteTime) {
                    armor -= deplete_ammount
                    if (armor < deplete_armor) {
                        armor = deplete_armor
                    }
                    nextArmorDepleteTime = (gameLocal.time + deplete_rate * 1000).toInt()
                }
            }
        }

        /*
        ===============
        idInventory::InitRechargeAmmo
        ===============
        * Loads any recharge ammo definitions from the ammo_types entity definitions.
        */
        fun InitRechargeAmmo(owner: idPlayer) {

            rechargeAmmo.fill(RechargeAmmo_t())

            var kv = owner.spawnArgs.MatchPrefix("ammorecharge_")
            while (kv != null) {
                val key: idStr = kv.GetKey()
                val ammoname: idStr = key.Right(key.Length() - "ammorecharge_".length)
                val ammoType = AmmoIndexForAmmoClass(ammoname.data)
                rechargeAmmo[ammoType].ammo = (atof(kv.GetValue().data) * 1000).toInt()
                rechargeAmmo[ammoType].ammoName = ammoname.data
                kv = owner.spawnArgs.MatchPrefix("ammorecharge_", kv)
            }
        }

        /*
        ===============
        idInventory::RechargeAmmo
        ===============
        * Called once per frame to update any ammo amount for ammo types that recharge.
        */
        fun RechargeAmmo(owner: idPlayer) {

            for (i in 0 until AMMO_NUMTYPES) {
                if (rechargeAmmo[i].ammo > 0) {
                    if (rechargeAmmo[i].rechargeTime == 0) {
                        //Initialize the recharge timer.
                        rechargeAmmo[i].rechargeTime = gameLocal.time
                    }
                    val elapsed = gameLocal.time - rechargeAmmo[i].rechargeTime
                    if (elapsed >= rechargeAmmo[i].ammo) {
                        val intervals = (gameLocal.time - rechargeAmmo[i].rechargeTime) / rechargeAmmo[i].ammo
                        ammo[i] += intervals

                        val max = MaxAmmoForAmmoClass(owner, rechargeAmmo[i].ammoName)
                        if (max > 0) {
                            if (ammo[i] > max) {
                                ammo[i] = max
                            }
                        }
                        rechargeAmmo[i].rechargeTime += intervals * rechargeAmmo[i].ammo
                    }
                }
            }
        }

        init {
            items = idList()
            pdas = idStrList()
            pdaSecurity = idStrList()
            videos = idStrList()
            emails = idStrList()
            levelTriggers = idList()
            Clear()
        }
    }

    class loggedAccel_t {
        val dir // scaled larger for running
                : idVec3
        var time = 0

        init {
            dir = idVec3()
        }
    }

    class aasLocation_t {
        val pos: idVec3 = idVec3()
        var areaNum = 0
    }

    class idPlayer : idActor() {
        companion object {
            val Type = idTypeInfo("idPlayer", "idActor") { idPlayer() }

            // enum {
            val EVENT_IMPULSE: Int = idEntity.EVENT_MAXEVENTS
            val EVENT_EXIT_TELEPORTER = EVENT_IMPULSE + 1
            val EVENT_ABORT_TELEPORTER = EVENT_IMPULSE + 2
            val EVENT_POWERUP = EVENT_IMPULSE + 3
            val EVENT_SPECTATE = EVENT_IMPULSE + 4
            val EVENT_PICKUPNAME = EVENT_IMPULSE + 5  // D3XP
            val EVENT_MAXEVENTS = EVENT_IMPULSE + 6

            //
            // mp stuff
            //        public static final idVec3[] colorBarTable = new idVec3[5];
            val colorBarTable: Array<idVec3> = arrayOf(
                idVec3(0.25f, 0.25f, 0.25f),
                idVec3(1.0f, 0.0f, 0.0f),
                idVec3(0.0f, 0.8f, 0.1f),
                idVec3(0.2f, 0.5f, 0.8f),
                idVec3(1.0f, 0.8f, 0.1f),
                // D3XP: 3 additional team colors for CTF
                idVec3(1.0f, 0.5f, 0.0f),
                idVec3(0.7f, 0.0f, 1.0f),
                idVec3(0.5f, 0.5f, 0.5f)
            )
            private const val NUM_LOGGED_ACCELS = 16 // for weapon turning angle offsets

            //
            private const val NUM_LOGGED_VIEW_ANGLES = 64 // for weapon turning angle offsets
            private val eventCallbacks: MutableMap<idEventDef, eventCallback_t<*>> = HashMap()

            //	virtual					~idPlayer();
            //
            fun getEventCallBacks(): MutableMap<idEventDef, eventCallback_t<*>> {
                return eventCallbacks
            }

            init {
                eventCallbacks.putAll(idActor.getEventCallBacks())
                eventCallbacks[EV_Player_GetButtons] = (eventCallback_t0 { obj: idPlayer -> obj.Event_GetButtons() })
                eventCallbacks[EV_Player_GetMove] = (eventCallback_t0 { obj: idPlayer -> obj.Event_GetMove() })
                eventCallbacks[EV_Player_GetViewAngles] =
                    (eventCallback_t0 { obj: idPlayer -> obj.Event_GetViewAngles() })
                eventCallbacks[EV_Player_StopFxFov] = (eventCallback_t0 { obj: idPlayer -> obj.Event_StopFxFov() })
                eventCallbacks[EV_Player_EnableWeapon] =
                    (eventCallback_t0 { obj: idPlayer -> obj.Event_EnableWeapon() })
                eventCallbacks[EV_Player_DisableWeapon] =
                    (eventCallback_t0 { obj: idPlayer -> obj.Event_DisableWeapon() })
                eventCallbacks[EV_Player_GetCurrentWeapon] =
                    (eventCallback_t0 { obj: idPlayer -> obj.Event_GetCurrentWeapon() })
                eventCallbacks[EV_Player_GetPreviousWeapon] =
                    (eventCallback_t0 { obj: idPlayer -> obj.Event_GetPreviousWeapon() })
                eventCallbacks[EV_Player_SelectWeapon] = (eventCallback_t1 { obj: idPlayer, weaponName: idEventArg<*> ->
                    obj.Event_SelectWeapon(
                        weaponName as idEventArg<String>
                    )
                })
                eventCallbacks[EV_Player_GetWeaponEntity] =
                    (eventCallback_t0 { obj: idPlayer -> obj.Event_GetWeaponEntity() })
                eventCallbacks[EV_Player_OpenPDA] = (eventCallback_t0 { obj: idPlayer -> obj.Event_OpenPDA() })
                eventCallbacks[EV_Player_InPDA] = (eventCallback_t0 { obj: idPlayer -> obj.Event_InPDA() })
                eventCallbacks[EV_Player_ExitTeleporter] =
                    (eventCallback_t0 { obj: idPlayer -> obj.Event_ExitTeleporter() })
                eventCallbacks[EV_Player_StopAudioLog] =
                    (eventCallback_t0 { obj: idPlayer -> obj.Event_StopAudioLog() })
                eventCallbacks[EV_Player_HideTip] = (eventCallback_t0 { obj: idPlayer -> obj.Event_HideTip() })
                eventCallbacks[EV_Player_LevelTrigger] =
                    (eventCallback_t0 { obj: idPlayer -> obj.Event_LevelTrigger() })
                eventCallbacks[EV_Gibbed] = (eventCallback_t0 { obj: idPlayer -> obj.Event_Gibbed() })
                eventCallbacks[EV_Player_GetIdealWeapon] =
                    (eventCallback_t0 { obj: idPlayer -> obj.Event_GetIdealWeapon() })

                // D3XP events
                eventCallbacks[EV_Player_GiveInventoryItem] =
                    eventCallback_t1 { obj: idPlayer, name: idEventArg<*>? ->
                        obj.Event_GiveInventoryItem(name as idEventArg<String>)
                    }
                eventCallbacks[EV_Player_RemoveInventoryItem] =
                    eventCallback_t1 { obj: idPlayer, name: idEventArg<*>? ->
                        obj.Event_RemoveInventoryItem(name as idEventArg<String>)
                    }
                eventCallbacks[EV_Player_SetPowerupTime] =
                    eventCallback_t2 { obj: idPlayer, powerup: idEventArg<*>, time: idEventArg<*> ->
                        obj.Event_SetPowerupTime(powerup as idEventArg<Int>, time as idEventArg<Int>)
                    }
                eventCallbacks[EV_Player_IsPowerupActive] =
                    eventCallback_t1 { obj: idPlayer, powerup: idEventArg<*>? ->
                        obj.Event_IsPowerupActive(powerup as idEventArg<Int>)
                    }
                eventCallbacks[EV_Player_WeaponAvailable] =
                    eventCallback_t1 { obj: idPlayer, name: idEventArg<*>? ->
                        obj.Event_WeaponAvailable(name as idEventArg<String>)
                    }
                eventCallbacks[EV_Player_StartWarp] =
                    eventCallback_t0 { obj: idPlayer -> obj.Event_StartWarp() }
                eventCallbacks[EV_Player_StopHelltime] =
                    eventCallback_t1 { obj: idPlayer, mode: idEventArg<*>? ->
                        obj.Event_StopHelltime(mode as idEventArg<Int>)
                    }
                eventCallbacks[EV_Player_ToggleBloom] =
                    eventCallback_t1 { obj: idPlayer, on: idEventArg<*>? ->
                        obj.Event_ToggleBloom(on as idEventArg<Int>)
                    }
                eventCallbacks[EV_Player_SetBloomParms] =
                    eventCallback_t2 { obj: idPlayer, speed: idEventArg<*>, intensity: idEventArg<*> ->
                        obj.Event_SetBloomParms(speed as idEventArg<Float>, intensity as idEventArg<Float>)
                    }
            }
        }

        val colorBar // used for scoreboard and hud display
                : idVec3

        //
        // the first person view values are always calculated, even
        // if a third person view is used
        val firstPersonViewOrigin: idVec3
        val soulCubeProjectile: idEntityPtr<idProjectile>

        //
        val teleportEntity // while being teleported, this is set to the entity we'll use for exit
                : idEntityPtr<idEntity>

        //
        val weapon: idEntityPtr<idWeapon>

        //
        private val aasLocation // for AI tracking the player
                : idList<aasLocation_t>
        private val baseSkinName: idStr
        private val centerView: idInterpolate<Float>
        private val gibsDir: idVec3
        private val lastDamageDir: idVec3
        private val loggedAccel // [currentLoggedAccel & (NUM_LOGGED_ACCELS-1)]
                : Array<loggedAccel_t>
        private val loggedViewAngles // [gameLocal.framenum&(LOGGED_VIEW_ANGLES-1)]
                : Array<idAngles>

        //
        private val pdaAudio: idStr
        private val pdaVideo: idStr
        private val pdaVideoWave: idStr

        //
        private val physicsObj // player physics
                : idPhysics_Player

        //
        private var skin: idDeclSkin?
        private val smoothedOrigin: idVec3
        private val viewBob: idVec3
        private val viewBobAngles: idAngles

        //
        private val zoomFov: idInterpolate<Float>
        var AI_ATTACK_HELD: idScriptBool = idScriptBool()
        var AI_BACKWARD: idScriptBool = idScriptBool()
        var AI_CROUCH: idScriptBool = idScriptBool()
        var AI_DEAD: idScriptBool = idScriptBool()

        //
        var AI_FORWARD: idScriptBool = idScriptBool()
        var AI_HARDLANDING: idScriptBool = idScriptBool()
        var AI_JUMP: idScriptBool = idScriptBool()
        var AI_ONGROUND: idScriptBool = idScriptBool()
        var AI_ONLADDER: idScriptBool = idScriptBool()
        var AI_PAIN: idScriptBool = idScriptBool()
        var AI_RELOAD: idScriptBool = idScriptBool()
        var AI_RUN: idScriptBool = idScriptBool()
        var AI_SOFTLANDING: idScriptBool = idScriptBool()
        var AI_STRAFE_LEFT: idScriptBool = idScriptBool()
        var AI_STRAFE_RIGHT: idScriptBool = idScriptBool()
        var AI_TELEPORT: idScriptBool = idScriptBool()
        var AI_TURN_LEFT: idScriptBool = idScriptBool()
        var AI_TURN_RIGHT: idScriptBool = idScriptBool()
        var AI_WEAPON_FIRED: idScriptBool = idScriptBool()

        //
        var buttonMask: Int
        val cmdAngles // player cmd angles
                : idAngles = idAngles()
        var colorBarIndex: Int
        var deathClearContentsTime: Int
        var doingDeathSkin: Boolean

        //
        var dragEntity: idDragEntity
        val firstPersonViewAxis: idMat3 = idMat3()
        var forceRespawn: Boolean
        var forceScoreBoard: Boolean
        var forcedReady: Boolean
        var godmode: Boolean
        var healthPool // amount of health to give over time
                : Float
        var healthPulse: Boolean
        var healthTake: Boolean
        var heartInfo: idInterpolate<Float>

        //
        var heartRate: Int

        //
        //
        var hiddenWeapon // if the weapon is hidden ( in noWeapons maps )
                : Boolean
        var hud // MP: is NULL if not local player
                : idUserInterface?

        //
        // inventory
        var inventory: idInventory
        var isChatting // replicated from server, true if the player is chatting.
                : Boolean
        var isLagged // replicated from server, true if packets haven't been received from client.
                : Boolean
        var lastArmorPulse // lastDmgTime if we had armor at time of hit
                : Int
        var lastDmgTime: Int
        var lastHeartAdjust: Int
        var lastHeartBeat: Int

        //
        var lastHitTime // last time projectile fired by player hit target
                : Int
        var lastHitToggle: Boolean
        var lastManOver // can't respawn in last man anymore (srv only)
                : Boolean
        var lastManPlayAgain // play again when end game delay is cancelled out before expiring (srv only)
                : Boolean
        var lastManPresent // true when player was in when game started (spectators can't join a running LMS)
                : Boolean
        var lastSavingThrowTime // for the "free miss" effect
                : Int
        var lastSndHitTime // MP hit sound - != lastHitTime because we throttle
                : Int
        var lastSpectateTeleport: Int
        var latchedTeam // need to track when team gets changed
                : Int
        var maxRespawnTime // force respawn after this time
                : Int

        //
        // timers
        var minRespawnTime // can respawn when time > this, force after g_forcerespawn
                : Int
        var nextHealthPulse: Int
        var nextHealthTake: Int

        //
        var noclip: Boolean
        var objectiveSystem: idUserInterface?
        var objectiveSystemOpen: Boolean
        var oldButtons: Int
        var oldFlags: Int

        //
        var playerView // handles damage kicks and effects
                : idPlayerView
        var scoreBoardOpen: Boolean
        val spawnAngles: idAngles = idAngles()

        //
        var spawnAnglesSet // on first usercmd, we must set deltaAngles
                : Boolean
        var spawnedTime // when client first enters the game
                : Int
        var spectating: Boolean
        var spectator: Int
        var stamina: Float
        var teleportKiller // entity number of an entity killing us at teleporter exit
                : Int
        var tourneyLine // client side - our spot in the wait line. 0 means no info.
                : Int
        var tourneyRank // for tourney cycling - the higher, the more likely to play next - server
                : Int
        var useInitialSpawns // toggled by a map restart to be active for the first game spawn
                : Boolean

        // };
        //
        var usercmd: usercmd_t
        val viewAngles // player view angles
                : idAngles = idAngles()
        var wantSpectate // from userInfo
                : Boolean
        var weaponGone // force stop firing
                : Boolean
        var weapon_fists: Int
        var weapon_pda: Int

        //
        var weapon_soulcube: Int

        // D3XP: CTF flag carrier state
        var carryingFlag: Boolean = false

        // D3XP: weapon toggle groups (cycling through weapon sets by script)
        val weaponToggles: HashTable.idHashTable<WeaponToggle_t> = HashTable.idHashTable()

        // D3XP: HUD powerup display
        var hudPowerup: Int = -1         // currently displayed powerup (-1 = none)
        var lastHudPowerup: Int = -1     // previous powerup for transition
        var hudPowerupDuration: Int = 0  // how long the powerup has been active (ms)

        // D3XP: bloodstone weapon indices
        var weapon_bloodstone: Int = -1
        var weapon_bloodstone_active1: Int = -1
        var weapon_bloodstone_active2: Int = -1
        var weapon_bloodstone_active3: Int = -1
        var harvest_lock: Boolean = false

        // D3XP: mounted object (turret)
        var mountedObject: idEntity? = null

        // D3XP: envirosuit light
        var enviroSuitLight: idEntityPtr<Light.idLight> = idEntityPtr()

        // D3XP: health recharge system
        var healthRecharge: Boolean = false
        var lastHealthRechargeTime: Int = 0
        var rechargeSpeed: Int = 500

        // D3XP: damage scale override
        var new_g_damageScale: Float = 1.0f

        // D3XP: bloom post-processing
        var bloomEnabled: Boolean = false
        var bloomSpeed: Float = 1.0f
        var bloomIntensity: Float = -0.01f
        private var MPAim // player num in aim
                : Int
        private var MPAimFadeTime // for GUI fade
                : Int
        private var MPAimHighlight: Boolean
        private var airTics // set to pm_airTics at start, drops in vacuum
                : Int

        //
        private var airless: Boolean
        private var bobCycle // for view bobbing and footstep generation
                : Int

        //
        private var bobFoot: Int
        private var bobFrac: Float
        private var bobfracsin: Float
        private var   /*jointHandle_t*/chestJoint: Int
        private var currentLoggedAccel: Int

        //
        private var currentWeapon: Int
        private var cursor: idUserInterface?
        private var focusCharacter: idAI?

        //
        // if there is a focusGUIent, the attack button will be changed into mouse clicks
        private var focusGUIent: idEntity?
        private var focusTime: Int
        private var focusUI // focusGUIent->renderEntity!!.gui, gui2, or gui3
                : idUserInterface?
        private var focusVehicle: idAFEntity_Vehicle?
        private var fxFov: Boolean

        //
        private var gibDeath: Boolean
        private var gibsLaunched: Boolean
        private var   /*jointHandle_t*/headJoint: Int

        //
        private var   /*jointHandle_t*/hipJoint: Int
        private var idealLegsYaw: Float
        private var idealWeapon: Int
        private var influenceActive // level of influence.. 1 == no gun or hud .. 2 == 1 + no movement
                : Int
        private var influenceEntity: idEntity?

        //
        private var influenceFov: Float
        private var influenceMaterial: Material.idMaterial?
        private var influenceRadius: Float
        private var influenceSkin: idDeclSkin?
        private var isTelefragged // proper obituaries
                : Boolean
        private var landChange: Int
        private var landTime: Int
        private var lastAirDamage: Int

        //
        private var lastDamageDef: Int
        private var lastDamageLocation: Int
        private var lastMPAim: Int
        private var lastMPAimTime // last time the aim changed
                : Int
        private /*unsigned*/  var lastSnapshotSequence // track state hitches on clients
                : Int
        private var lastSpectateChange: Int
        private var lastTeleFX: Int
        private var leader // for sudden death situations
                : Boolean
        private var legsForward: Boolean
        private var legsYaw: Float
        private var numProjectileHits // number of hits on mobs
                : Int

        //
        private var numProjectilesFired // number of projectiles fired
                : Int
        private var objectiveUp: Boolean

        //
        // full screen guis track mouse movements directly
        private var oldMouseX: Int
        private var oldMouseY: Int
        private var oldViewYaw: Float

        //
        private val playerIcon: idPlayerIcon = idPlayerIcon()
        private var powerUpSkin: idDeclSkin?
        private var previousWeapon: Int

        //
        private var privateCameraView: idCamera?

        //
        // mp
        private var ready // from userInfo
                : Boolean
        private var respawning // set to true while in SpawnToPoint for telefrag checks
                : Boolean

        //
        private var selfSmooth: Boolean
        private var showWeaponViewModel: Boolean
        private val smoothedAngles: idAngles
        private var smoothedFrame: Int
        private var smoothedOriginUpdated: Boolean
        private var stepUpDelta: Float
        private var stepUpTime: Int
        private var talkCursor // show the state of the focusCharacter (0 == can't talk/dead, 1 == ready to talk, 2 == busy talking)
                : Int

        //
        private var tipUp: Boolean
        private var weaponCatchup // raise up the weapon silently ( state catchups )
                : Boolean
        private var weaponEnabled: Boolean
        private var weaponSwitchTime: Int
        private var xyspeed: Float

        /*
         ==============
         idPlayer::Spawn

         Prepare any resources used by the player.
         ==============
         */
        override fun Spawn() {
            super.Spawn()
            val temp = idStr()
            //            idBounds bounds;
            if (entityNumber >= Game_local.MAX_CLIENTS) {
                idGameLocal.Error("entityNum > MAX_CLIENTS for player.  Player may only be spawned with a client.")
            }

            // allow thinking during cinematics
            cinematic = true
            if (gameLocal.isMultiplayer) {
                // always start in spectating state waiting to be spawned in
                // do this before SetClipModel to get the right bounding box
                spectating = true
            }

            // set our collision model
            physicsObj.SetSelf(this)
            SetClipModel()
            physicsObj.SetMass(spawnArgs.GetFloat("mass", "100"))
            physicsObj.SetContents(Material.CONTENTS_BODY)
            physicsObj.SetClipMask(Game_local.MASK_PLAYERSOLID)
            SetPhysics(physicsObj)
            InitAASLocation()
            skin = renderEntity!!.customSkin

            // only the local player needs guis
            if (!gameLocal.isMultiplayer || entityNumber == gameLocal.localClientNum) {

                // load HUD
                if (gameLocal.isMultiplayer) {
                    hud = UserInterface.uiManager.FindGui("guis/mphud.gui", true, false, true)
                } else if (spawnArgs.GetString("hud", "", temp)) {
                    hud = UserInterface.uiManager.FindGui(temp.toString(), true, false, true)
                }
                if (hud != null) {
                    hud!!.Activate(true, gameLocal.time)
                }

                // load cursor
                if (spawnArgs.GetString("cursor", "", temp)) {
                    cursor = UserInterface.uiManager.FindGui(
                        temp.toString(), true, gameLocal.isMultiplayer, gameLocal.isMultiplayer
                    )
                }
                if (cursor != null) {
                    // DG: make it scale to 4:3 so crosshair looks properly round
                    cursor!!.SetStateBool("scaleto43", true)
                    cursor!!.StateChanged(gameLocal.time)
                    cursor!!.Activate(true, gameLocal.time)
                }
                objectiveSystem = UserInterface.uiManager.FindGui("guis/pda.gui", true, false, true)
                objectiveSystemOpen = false
            }
            SetLastHitTime(0)

            // load the armor sound feedback
            DeclManager.declManager.FindSound("player_sounds_hitArmor")

            // set up conditions for animation
            LinkScriptVariables()
            animator.RemoveOriginOffset(true)

            // initialize user info related settings
            // on server, we wait for the userinfo broadcast, as this controls when the player is initially spawned in game
            if (gameLocal.isClient || entityNumber == gameLocal.localClientNum) {
                UserInfoChanged(false)
            }

            // create combat collision hull for exact collision detection
            SetCombatModel()

            // init the damage effects
            playerView.SetPlayerEntity(this)

            // supress model in non-player views, but allow it in mirrors and remote views
            renderEntity!!.suppressSurfaceInViewID = entityNumber + 1

            // don't project shadow on self or weapon
            renderEntity!!.noSelfShadow = true
            val headEnt = head.GetEntity()
            if (headEnt != null) {
                headEnt.GetRenderEntity()!!.suppressSurfaceInViewID = entityNumber + 1
                headEnt.GetRenderEntity()!!.noSelfShadow = true
            }
            if (gameLocal.isMultiplayer) {
                Init()
                Hide() // properly hidden if starting as a spectator
                if (!gameLocal.isClient) {
                    // set yourself ready to spawn. idMultiplayerGame will decide when/if appropriate and call SpawnFromSpawnSpot
                    SetupWeaponEntity()
                    SpawnFromSpawnSpot()
                    forceRespawn = true
                    assert(spectating)
                }
            } else {
                SetupWeaponEntity()
                SpawnFromSpawnSpot()
            }

            // trigger playtesting item gives, if we didn't get here from a previous level
            // the devmap key will be set on the first devmap, but cleared on any level
            // transitions
            if (!gameLocal.isMultiplayer && gameLocal.serverInfo.FindKey("devmap") != null) {
                // fire a trigger with the name "devmap"
                val ent = gameLocal.FindEntity("devmap")
                ent?.ActivateTargets(this)
            }
            if (hud != null) {
                if (!isD3XP) {
                    // We can spawn with a full soul cube, so we need to make sure the hud knows this
                    if (weapon_soulcube > 0 && (inventory.weapons and (1 shl weapon_soulcube)) != 0) {
                        val max_souls = inventory.MaxAmmoForAmmoClass(this, "ammo_souls")
                        if (inventory.ammo[idWeapon.GetAmmoNumForName("ammo_souls")] >= max_souls) {
                            hud!!.HandleNamedEvent("soulCubeReady")
                        }
                    }
                }
                if (isD3XP) {
                    // We can spawn with a full bloodstone, so make sure the hud knows
                    if (weapon_bloodstone > 0 && (inventory.weapons and (1 shl weapon_bloodstone)) != 0) {
                        hud!!.HandleNamedEvent("bloodstoneReady")
                    }
                }
                hud!!.HandleNamedEvent("itemPickup")
            }
            if (GetPDA() != null) {
                // Add any emails from the inventory
                for (i in 0 until inventory.emails.size()) {
                    GetPDA()!!.AddEmail(inventory.emails[i].toString())
                }
                GetPDA()!!.SetSecurity(Common.common.GetLanguageDict().GetString("#str_00066"))
            }
            if (gameLocal.world!!.spawnArgs.GetBool("no_Weapons")) {
                hiddenWeapon = true
                if (weapon.GetEntity() != null) {
                    weapon.GetEntity()!!.LowerWeapon()
                }
                idealWeapon = 0
            } else {
                hiddenWeapon = false
            }
            if (hud != null) {
                UpdateHudWeapon()
                hud!!.StateChanged(gameLocal.time)
            }
            tipUp = false
            objectiveUp = false
            if (inventory.levelTriggers.Num() != 0) {
                PostEventMS(EV_Player_LevelTrigger, 0)
            }
            inventory.pdaOpened = false
            inventory.selPDA = 0
            if (!gameLocal.isMultiplayer) {
                if (SysCvar.g_skill.GetInteger() < 2) {
                    if (health < 25) {
                        health = 25
                    }
                    if (SysCvar.g_useDynamicProtection.GetBool()) {
                        if (isD3XP) {
                            new_g_damageScale = 1.0f
                        } else {
                            SysCvar.g_damageScale.SetFloat(1.0f)
                        }
                    }
                } else {
                    if (isD3XP) {
                        new_g_damageScale = 1.0f
                    } else {
                        SysCvar.g_damageScale.SetFloat(1.0f)
                    }
                    SysCvar.g_armorProtection.SetFloat(if (SysCvar.g_skill.GetInteger() < 2) 0.4f else 0.2f)
                    if (SysCvar.g_skill.GetInteger() == 3) {
                        healthTake = true
                        nextHealthTake = gameLocal.time + SysCvar.g_healthTakeTime.GetInteger() * 1000
                    }
                }
            }

            if (isD3XP) {
                // Setup the weapon toggle lists
                var kv2 = spawnArgs.MatchPrefix("weapontoggle")
                while (kv2 != null) {
                    val newToggle = WeaponToggle_t()
                    newToggle.name = kv2.GetKey().toString()

                    val toggleData = kv2.GetValue()
                    val src = idLexer()
                    val token = idToken()
                    src.LoadMemory(toggleData.toString(), toggleData.Length(), "toggleData")
                    while (true) {
                        if (!src.ReadToken(token)) {
                            break
                        }
                        val index = token.toString().toInt()
                        newToggle.toggleList.Append(index)
                        // Skip the comma
                        src.ReadToken(token)
                    }
                    weaponToggles.Set(newToggle.name, newToggle)
                    kv2 = spawnArgs.MatchPrefix("weapontoggle", kv2)
                }
            }

            if (isD3XP) {
                if (SysCvar.g_skill.GetInteger() >= 3) {
                    if (!WeaponAvailable("weapon_bloodstone_passive")) {
                        GiveInventoryItem("weapon_bloodstone_passive")
                    }
                    if (!WeaponAvailable("weapon_bloodstone_active1")) {
                        GiveInventoryItem("weapon_bloodstone_active1")
                    }
                    if (!WeaponAvailable("weapon_bloodstone_active2")) {
                        GiveInventoryItem("weapon_bloodstone_active2")
                    }
                    if (!WeaponAvailable("weapon_bloodstone_active3")) {
                        GiveInventoryItem("weapon_bloodstone_active3")
                    }
                }

                bloomEnabled = false
                bloomSpeed = 1.0f
                bloomIntensity = -0.01f
            }
        }

        /*
         ==============
         idPlayer::Think

         Called every tic for each player
         ==============
         */
        override fun Think() {
            val headRenderEnt: renderEntity_s?
            UpdatePlayerIcons()

            // latch button actions
            oldButtons = usercmd.buttons.toInt()

            // grab out usercmd
            val oldCmd = usercmd
            usercmd = gameLocal.usercmds[entityNumber]
            buttonMask = buttonMask and usercmd.buttons.toInt()
            usercmd.buttons = usercmd.buttons and buttonMask.inv().toByte()
            if (gameLocal.inCinematic && gameLocal.skipCinematic) {
                return
            }

            // clear the ik before we do anything else so the skeleton doesn't get updated twice
            walkIK.ClearJointMods()

            // if this is the very first frame of the map, set the delta view angles
            // based on the usercmd angles
            if (!spawnAnglesSet && gameLocal.GameState() != gameState_t.GAMESTATE_STARTUP) {
                spawnAnglesSet = true
                SetViewAngles(spawnAngles)
                oldFlags = usercmd.flags.toInt()
            }
            if (objectiveSystemOpen || gameLocal.inCinematic || influenceActive != 0) {
                if (objectiveSystemOpen && AI_PAIN.underscore()!!) {
                    TogglePDA()
                }
                usercmd.forwardmove = 0
                usercmd.rightmove = 0
                usercmd.upmove = 0
            }

            // log movement changes for weapon bobbing effects
            if (usercmd.forwardmove != oldCmd.forwardmove) {
                val acc = loggedAccel[currentLoggedAccel and NUM_LOGGED_ACCELS - 1]
                currentLoggedAccel++
                acc.time = gameLocal.time
                acc.dir[0] = (usercmd.forwardmove - oldCmd.forwardmove).toFloat()
                acc.dir[1] = acc.dir.set(2, 0.0f)
            }
            if (usercmd.rightmove != oldCmd.rightmove) {
                val acc = loggedAccel[currentLoggedAccel and NUM_LOGGED_ACCELS - 1]
                currentLoggedAccel++
                acc.time = gameLocal.time
                acc.dir[1] = (usercmd.rightmove - oldCmd.rightmove).toFloat()
                acc.dir[0] = 0.0f
                acc.dir[2] = 0.0f
            }

            // freelook centering
            if (((usercmd.buttons.toInt() xor oldCmd.buttons.toInt()) and UsercmdGen.BUTTON_MLOOK) != 0) {
                centerView.Init(gameLocal.time.toFloat(), 200.0f, viewAngles.pitch, 0.0f)
            }

            // zooming
            if (((usercmd.buttons.toInt() xor oldCmd.buttons.toInt()) and UsercmdGen.BUTTON_ZOOM) != 0) {
                if ((usercmd.buttons.toInt() and UsercmdGen.BUTTON_ZOOM) != 0 && weapon.GetEntity() != null) {
                    zoomFov.Init(
                        gameLocal.time.toFloat(),
                        200.0f,
                        CalcFov(false),
                        weapon.GetEntity()!!.GetZoomFov().toFloat()
                    )
                } else {
                    zoomFov.Init(
                        gameLocal.time.toFloat(),
                        200.0f,
                        zoomFov.GetCurrentValue(gameLocal.time.toFloat()),
                        DefaultFov()
                    )
                }
            }

            // if we have an active gui, we will unrotate the view angles as
            // we turn the mouse movements into gui events
            val gui = ActiveGui()
            if (gui != null && gui !== focusUI) {
                RouteGuiMouse(gui)
            }

            // set the push velocity on the weapon before running the physics
            if (weapon.GetEntity() != null) {
                weapon.GetEntity()!!.SetPushVelocity(physicsObj.GetPushedLinearVelocity())
            }
            EvaluateControls()
            if (!af.IsActive()) {
                AdjustBodyAngles()
                CopyJointsFromBodyToHead()
            }
            Move()
            if (!SysCvar.g_stopTime.GetBool()) {
                if (!noclip && !spectating && health > 0 && !IsHidden()) {
                    TouchTriggers()
                }

                // not done on clients for various reasons. don't do it on server and save the sound channel for other things
                if (!gameLocal.isMultiplayer) {
                    SetCurrentHeartRate()
                    var scale = SysCvar.g_damageScale.GetFloat()
                    if (SysCvar.g_useDynamicProtection.GetBool() && scale < 1.0f && gameLocal.time - lastDmgTime > 500) {
                        if (scale < 1.0f) {
                            scale += 0.05f
                        }
                        if (scale > 1.0f) {
                            scale = 1.0f
                        }
                        SysCvar.g_damageScale.SetFloat(scale)
                    }
                }

                // update GUIs, Items, and character interactions
                UpdateFocus()
                UpdateLocation()

                // update player script
                UpdateScript()

                // service animations
                if (!spectating && !af.IsActive() && !gameLocal.inCinematic) {
                    UpdateConditions()
                    UpdateAnimState()
                    CheckBlink()
                }

                // clear out our pain flag so we can tell if we recieve any damage between now and the next time we think
                AI_PAIN.underscore(false)
            }

            // calculate the exact bobbed view position, which is used to
            // position the view weapon, among other things
            CalculateFirstPersonView()

            // this may use firstPersonView, or a thirdPeroson / camera view
            CalculateRenderView()
            inventory.UpdateArmor()
            if (spectating) {
                UpdateSpectating()
            } else if (health > 0) {
                UpdateWeapon()
            }
            UpdateAir()
            UpdateHud()
            UpdatePowerUps()
            UpdateDeathSkin(false)
            if (gameLocal.isMultiplayer) {
                DrawPlayerIcons()
            }
            headRenderEnt = if (head?.GetEntity() != null) {
                head.GetEntity()!!.GetRenderEntity()
            } else {
                null
            }
            if (headRenderEnt != null) {
                if (influenceSkin != null) {
                    headRenderEnt.customSkin = influenceSkin
                } else {
                    headRenderEnt.customSkin = null
                }
            }
            if (gameLocal.isMultiplayer || SysCvar.g_showPlayerShadow.GetBool()) {
                renderEntity!!.suppressShadowInViewID = 0
                if (headRenderEnt != null) {
                    headRenderEnt.suppressShadowInViewID = 0
                }
            } else {
                renderEntity!!.suppressShadowInViewID = entityNumber + 1
                if (headRenderEnt != null) {
                    headRenderEnt.suppressShadowInViewID = entityNumber + 1
                }
            }
            // never cast shadows from our first-person muzzle flashes
            renderEntity!!.suppressShadowInLightID = Weapon.LIGHTID_VIEW_MUZZLE_FLASH + entityNumber
            if (headRenderEnt != null) {
                headRenderEnt.suppressShadowInLightID = Weapon.LIGHTID_VIEW_MUZZLE_FLASH + entityNumber
            }
            if (!SysCvar.g_stopTime.GetBool()) {
                UpdateAnimation()
                Present()
                UpdateDamageEffects()
                LinkCombat()
                playerView.CalculateShake()
            }
            if (0 == thinkFlags and TH_THINK) {
                gameLocal.Printf("player %d not thinking\n", entityNumber)
            }
            if (SysCvar.g_showEnemies.GetBool()) {
                var ent: idActor?
                var num = 0
                ent = enemyList.Next()
                while (ent != null) {
                    gameLocal.Printf("enemy (%d)'%s'\n", ent.entityNumber, ent.name)
                    Game_local.gameRenderWorld!!.DebugBounds(
                        colorRed, ent.GetPhysics().GetBounds().Expand(2.0f), ent.GetPhysics().GetOrigin()
                    )
                    num++
                    ent = ent.enemyNode.Next()
                }
                gameLocal.Printf("%d: enemies\n", num)
            }

            if (isD3XP) {
                inventory.RechargeAmmo(this)

                if (healthRecharge) {
                    val elapsed = gameLocal.time - lastHealthRechargeTime
                    if (elapsed >= rechargeSpeed) {
                        val intervals = (gameLocal.time - lastHealthRechargeTime) / rechargeSpeed
                        Give("health", Str.va("%d", intervals))
                        lastHealthRechargeTime += intervals * rechargeSpeed
                    }
                }

                // determine if portal sky is in pvs
                gameLocal.portalSkyActive = gameLocal.pvs.CheckAreasForPortalSky(
                    gameLocal.GetPlayerPVS(), GetPhysics().GetOrigin()
                )
            }
        }

        // save games
        override fun Save(savefile: idSaveGame) {                    // archives object for save game file
            super.Save(savefile)
            var i: Int
            savefile.WriteUsercmd(usercmd)
            playerView.Save(savefile)
            savefile.WriteBool(noclip)
            savefile.WriteBool(godmode)

            // don't save spawnAnglesSet, since we'll have to reset them after loading the savegame
            savefile.WriteAngles(spawnAngles)
            savefile.WriteAngles(viewAngles)
            savefile.WriteAngles(cmdAngles)
            savefile.WriteInt(buttonMask)
            savefile.WriteInt(oldButtons)
            savefile.WriteInt(oldFlags)
            savefile.WriteInt(lastHitTime)
            savefile.WriteInt(lastSndHitTime)
            savefile.WriteInt(lastSavingThrowTime)

            // idBoolFields don't need to be saved, just re-linked in Restore
            inventory.Save(savefile)
            weapon.Save(savefile)
            savefile.WriteUserInterface(hud, false)
            savefile.WriteUserInterface(objectiveSystem, false)
            savefile.WriteBool(objectiveSystemOpen)
            savefile.WriteInt(weapon_soulcube)
            savefile.WriteInt(weapon_pda)
            savefile.WriteInt(weapon_fists)
            if (isD3XP) {
                savefile.WriteInt(weapon_bloodstone)
                savefile.WriteInt(weapon_bloodstone_active1)
                savefile.WriteInt(weapon_bloodstone_active2)
                savefile.WriteInt(weapon_bloodstone_active3)
                savefile.WriteBool(harvest_lock)
                savefile.WriteInt(hudPowerup)
                savefile.WriteInt(lastHudPowerup)
                savefile.WriteInt(hudPowerupDuration)
            }
            savefile.WriteInt(heartRate)
            savefile.WriteFloat(heartInfo.GetStartTime())
            savefile.WriteFloat(heartInfo.GetDuration())
            savefile.WriteFloat(heartInfo.GetStartValue())
            savefile.WriteFloat(heartInfo.GetEndValue())
            savefile.WriteInt(lastHeartAdjust)
            savefile.WriteInt(lastHeartBeat)
            savefile.WriteInt(lastDmgTime)
            savefile.WriteInt(deathClearContentsTime)
            savefile.WriteBool(doingDeathSkin)
            savefile.WriteInt(lastArmorPulse)
            savefile.WriteFloat(stamina)
            savefile.WriteFloat(healthPool)
            savefile.WriteInt(nextHealthPulse)
            savefile.WriteBool(healthPulse)
            savefile.WriteInt(nextHealthTake)
            savefile.WriteBool(healthTake)
            savefile.WriteBool(hiddenWeapon)
            soulCubeProjectile.Save(savefile)
            savefile.WriteInt(spectator)
            savefile.WriteVec3(colorBar)
            savefile.WriteInt(colorBarIndex)
            savefile.WriteBool(scoreBoardOpen)
            savefile.WriteBool(forceScoreBoard)
            savefile.WriteBool(forceRespawn)
            savefile.WriteBool(spectating)
            savefile.WriteInt(lastSpectateTeleport)
            savefile.WriteBool(lastHitToggle)
            savefile.WriteBool(forcedReady)
            savefile.WriteBool(wantSpectate)
            savefile.WriteBool(weaponGone)
            savefile.WriteBool(useInitialSpawns)
            savefile.WriteInt(latchedTeam)
            savefile.WriteInt(tourneyRank)
            savefile.WriteInt(tourneyLine)
            teleportEntity.Save(savefile)
            savefile.WriteInt(teleportKiller)
            savefile.WriteInt(minRespawnTime)
            savefile.WriteInt(maxRespawnTime)
            savefile.WriteVec3(firstPersonViewOrigin)
            savefile.WriteMat3(firstPersonViewAxis)

            // don't bother saving dragEntity since it's a dev tool
            savefile.WriteJoint(hipJoint)
            savefile.WriteJoint(chestJoint)
            savefile.WriteJoint(headJoint)
            savefile.WriteStaticObject(physicsObj)
            savefile.WriteInt(aasLocation.Num())
            i = 0
            while (i < aasLocation.Num()) {
                savefile.WriteInt(aasLocation[i].areaNum)
                savefile.WriteVec3(aasLocation[i].pos)
                i++
            }
            savefile.WriteInt(bobFoot)
            savefile.WriteFloat(bobFrac)
            savefile.WriteFloat(bobfracsin)
            savefile.WriteInt(bobCycle)
            savefile.WriteFloat(xyspeed)
            savefile.WriteInt(stepUpTime)
            savefile.WriteFloat(stepUpDelta)
            savefile.WriteFloat(idealLegsYaw)
            savefile.WriteFloat(legsYaw)
            savefile.WriteBool(legsForward)
            savefile.WriteFloat(oldViewYaw)
            savefile.WriteAngles(viewBobAngles)
            savefile.WriteVec3(viewBob)
            savefile.WriteInt(landChange)
            savefile.WriteInt(landTime)
            savefile.WriteInt(currentWeapon)
            savefile.WriteInt(idealWeapon)
            savefile.WriteInt(previousWeapon)
            savefile.WriteInt(weaponSwitchTime)
            savefile.WriteBool(weaponEnabled)
            savefile.WriteBool(showWeaponViewModel)
            savefile.WriteSkin(skin)
            savefile.WriteSkin(powerUpSkin)
            savefile.WriteString(baseSkinName)
            savefile.WriteInt(numProjectilesFired)
            savefile.WriteInt(numProjectileHits)
            savefile.WriteBool(airless)
            savefile.WriteInt(airTics)
            savefile.WriteInt(lastAirDamage)
            savefile.WriteBool(gibDeath)
            savefile.WriteBool(gibsLaunched)
            savefile.WriteVec3(gibsDir)
            savefile.WriteFloat(zoomFov.GetStartTime())
            savefile.WriteFloat(zoomFov.GetDuration())
            savefile.WriteFloat(zoomFov.GetStartValue())
            savefile.WriteFloat(zoomFov.GetEndValue())
            savefile.WriteFloat(centerView.GetStartTime())
            savefile.WriteFloat(centerView.GetDuration())
            savefile.WriteFloat(centerView.GetStartValue())
            savefile.WriteFloat(centerView.GetEndValue())
            savefile.WriteBool(fxFov)
            savefile.WriteFloat(influenceFov)
            savefile.WriteInt(influenceActive)
            savefile.WriteFloat(influenceRadius)
            savefile.WriteObject(influenceEntity)
            savefile.WriteMaterial(influenceMaterial)
            savefile.WriteSkin(influenceSkin)
            savefile.WriteObject(privateCameraView)
            i = 0
            while (i < NUM_LOGGED_VIEW_ANGLES) {
                savefile.WriteAngles(loggedViewAngles[i])
                i++
            }
            i = 0
            while (i < NUM_LOGGED_ACCELS) {
                savefile.WriteInt(loggedAccel[i].time)
                savefile.WriteVec3(loggedAccel[i].dir)
                i++
            }
            savefile.WriteInt(currentLoggedAccel)
            savefile.WriteObject(focusGUIent)
            // can't save focusUI
            savefile.WriteObject(focusCharacter)
            savefile.WriteInt(talkCursor)
            savefile.WriteInt(focusTime)
            savefile.WriteObject(focusVehicle)
            savefile.WriteUserInterface(cursor, false)
            savefile.WriteInt(oldMouseX)
            savefile.WriteInt(oldMouseY)
            savefile.WriteString(pdaAudio)
            savefile.WriteString(pdaVideo)
            savefile.WriteString(pdaVideoWave)
            savefile.WriteBool(tipUp)
            savefile.WriteBool(objectiveUp)
            savefile.WriteInt(lastDamageDef)
            savefile.WriteVec3(lastDamageDir)
            savefile.WriteInt(lastDamageLocation)
            savefile.WriteInt(smoothedFrame)
            savefile.WriteBool(smoothedOriginUpdated)
            savefile.WriteVec3(smoothedOrigin)
            savefile.WriteAngles(smoothedAngles)
            savefile.WriteBool(ready)
            savefile.WriteBool(respawning)
            savefile.WriteBool(leader)
            savefile.WriteInt(lastSpectateChange)
            savefile.WriteInt(lastTeleFX)
            savefile.WriteFloat(SysCvar.pm_stamina.GetFloat())
            if (isD3XP) {
                savefile.WriteObject(mountedObject)
                enviroSuitLight.Save(savefile)
                savefile.WriteBool(healthRecharge)
                savefile.WriteInt(lastHealthRechargeTime)
                savefile.WriteInt(rechargeSpeed)
                savefile.WriteFloat(new_g_damageScale)
                savefile.WriteBool(bloomEnabled)
                savefile.WriteFloat(bloomSpeed)
                savefile.WriteFloat(bloomIntensity)
            }
            if (hud != null) {
                hud!!.SetStateString("message", Common.common.GetLanguageDict().GetString("#str_02916"))
                hud!!.HandleNamedEvent("Message")
            }
        }

        override fun Restore(savefile: idRestoreGame) {                    // unarchives object from save game file
            super.Restore(savefile)
            var i: Int
            val num = CInt()
            val set = CFloat()
            savefile.ReadUsercmd(usercmd)
            playerView.Restore(savefile)
            noclip = savefile.ReadBool()
            godmode = savefile.ReadBool()
            savefile.ReadAngles(spawnAngles)
            savefile.ReadAngles(viewAngles)
            savefile.ReadAngles(cmdAngles)
            Arrays.fill(usercmd.angles, 0.toShort()) //damn you type safety!!
            SetViewAngles(viewAngles)
            spawnAnglesSet = true
            buttonMask = savefile.ReadInt()
            oldButtons = savefile.ReadInt()
            oldFlags = savefile.ReadInt()
            usercmd.flags = 0
            oldFlags = 0
            lastHitTime = savefile.ReadInt()
            lastSndHitTime = savefile.ReadInt()
            lastSavingThrowTime = savefile.ReadInt()

            // Re-link idBoolFields to the scriptObject, values will be restored in scriptObject's restore
            LinkScriptVariables()
            inventory.Restore(savefile)
            weapon.Restore(savefile)
            i = 0
            while (i < inventory.emails.size()) {
                GetPDA()!!.AddEmail(inventory.emails[i].toString())
                i++
            }
            hud = savefile.ReadUserInterface()
            objectiveSystem = savefile.ReadUserInterface()
            objectiveSystemOpen = savefile.ReadBool()
            weapon_soulcube = savefile.ReadInt()
            weapon_pda = savefile.ReadInt()
            weapon_fists = savefile.ReadInt()
            if (isD3XP) {
                weapon_bloodstone = savefile.ReadInt()
                weapon_bloodstone_active1 = savefile.ReadInt()
                weapon_bloodstone_active2 = savefile.ReadInt()
                weapon_bloodstone_active3 = savefile.ReadInt()
                harvest_lock = savefile.ReadBool()
                hudPowerup = savefile.ReadInt()
                lastHudPowerup = savefile.ReadInt()
                hudPowerupDuration = savefile.ReadInt()
            }
            heartRate = savefile.ReadInt()
            savefile.ReadFloat(set)
            heartInfo.SetStartTime(set._val)
            savefile.ReadFloat(set)
            heartInfo.SetDuration(set._val)
            savefile.ReadFloat(set)
            heartInfo.SetStartValue(set._val)
            savefile.ReadFloat(set)
            heartInfo.SetEndValue(set._val)
            lastHeartAdjust = savefile.ReadInt()
            lastHeartBeat = savefile.ReadInt()
            lastDmgTime = savefile.ReadInt()
            deathClearContentsTime = savefile.ReadInt()
            doingDeathSkin = savefile.ReadBool()
            lastArmorPulse = savefile.ReadInt()
            stamina = savefile.ReadFloat()
            healthPool = savefile.ReadFloat()
            nextHealthPulse = savefile.ReadInt()
            healthPulse = savefile.ReadBool()
            nextHealthTake = savefile.ReadInt()
            healthTake = savefile.ReadBool()
            hiddenWeapon = savefile.ReadBool()
            soulCubeProjectile.Restore(savefile)
            spectator = savefile.ReadInt()
            savefile.ReadVec3(colorBar)
            colorBarIndex = savefile.ReadInt()
            scoreBoardOpen = savefile.ReadBool()
            forceScoreBoard = savefile.ReadBool()
            forceRespawn = savefile.ReadBool()
            spectating = savefile.ReadBool()
            lastSpectateTeleport = savefile.ReadInt()
            lastHitToggle = savefile.ReadBool()
            forcedReady = savefile.ReadBool()
            wantSpectate = savefile.ReadBool()
            weaponGone = savefile.ReadBool()
            useInitialSpawns = savefile.ReadBool()
            latchedTeam = savefile.ReadInt()
            tourneyRank = savefile.ReadInt()
            tourneyLine = savefile.ReadInt()
            teleportEntity.Restore(savefile)
            teleportKiller = savefile.ReadInt()
            minRespawnTime = savefile.ReadInt()
            maxRespawnTime = savefile.ReadInt()
            savefile.ReadVec3(firstPersonViewOrigin)
            savefile.ReadMat3(firstPersonViewAxis)

            // don't bother saving dragEntity since it's a dev tool
            dragEntity.Clear()
            hipJoint = savefile.ReadJoint()
            chestJoint = savefile.ReadJoint()
            headJoint = savefile.ReadJoint()
            savefile.ReadStaticObject(physicsObj)
            RestorePhysics(physicsObj)
            savefile.ReadInt(num)
            aasLocation.SetGranularity(1)
            aasLocation.SetNum(num._val)
            i = 0
            while (i < num._val) {
                aasLocation[i] = aasLocation_t()
                aasLocation[i].areaNum = savefile.ReadInt()
                savefile.ReadVec3(aasLocation[i].pos)
                i++
            }
            bobFoot = savefile.ReadInt()
            bobFrac = savefile.ReadFloat()
            bobfracsin = savefile.ReadFloat()
            bobCycle = savefile.ReadInt()
            xyspeed = savefile.ReadFloat()
            stepUpTime = savefile.ReadInt()
            stepUpDelta = savefile.ReadFloat()
            idealLegsYaw = savefile.ReadFloat()
            legsYaw = savefile.ReadFloat()
            legsForward = savefile.ReadBool()
            oldViewYaw = savefile.ReadFloat()
            savefile.ReadAngles(viewBobAngles)
            savefile.ReadVec3(viewBob)
            landChange = savefile.ReadInt()
            landTime = savefile.ReadInt()
            currentWeapon = savefile.ReadInt()
            idealWeapon = savefile.ReadInt()
            previousWeapon = savefile.ReadInt()
            weaponSwitchTime = savefile.ReadInt()
            weaponEnabled = savefile.ReadBool()
            showWeaponViewModel = savefile.ReadBool()
            skin = savefile.ReadSkin()
            powerUpSkin = savefile.ReadSkin()
            savefile.ReadString(baseSkinName)
            numProjectilesFired = savefile.ReadInt()
            numProjectileHits = savefile.ReadInt()
            airless = savefile.ReadBool()
            airTics = savefile.ReadInt()
            lastAirDamage = savefile.ReadInt()
            gibDeath = savefile.ReadBool()
            gibsLaunched = savefile.ReadBool()
            savefile.ReadVec3(gibsDir)
            savefile.ReadFloat(set)
            zoomFov.SetStartTime(set._val)
            savefile.ReadFloat(set)
            zoomFov.SetDuration(set._val)
            savefile.ReadFloat(set)
            zoomFov.SetStartValue(set._val)
            savefile.ReadFloat(set)
            zoomFov.SetEndValue(set._val)
            savefile.ReadFloat(set)
            centerView.SetStartTime(set._val)
            savefile.ReadFloat(set)
            centerView.SetDuration(set._val)
            savefile.ReadFloat(set)
            centerView.SetStartValue(set._val)
            savefile.ReadFloat(set)
            centerView.SetEndValue(set._val)
            fxFov = savefile.ReadBool()
            influenceFov = savefile.ReadFloat()
            influenceActive = savefile.ReadInt()
            influenceRadius = savefile.ReadFloat()
            influenceEntity = savefile.ReadObject() as idEntity?
            influenceMaterial = savefile.ReadMaterial()
            influenceSkin = savefile.ReadSkin()
            privateCameraView = savefile.ReadObject() as idCamera?
            i = 0
            while (i < NUM_LOGGED_VIEW_ANGLES) {
                savefile.ReadAngles(loggedViewAngles[i])
                i++
            }
            i = 0
            while (i < NUM_LOGGED_ACCELS) {
                loggedAccel[i].time = savefile.ReadInt()
                savefile.ReadVec3(loggedAccel[i].dir)
                i++
            }
            currentLoggedAccel = savefile.ReadInt()
            focusGUIent = savefile.ReadObject() as idEntity?
            // can't save focusUI
            focusUI = null
            focusCharacter = savefile.ReadObject() as idAI?
            talkCursor = savefile.ReadInt()
            focusTime = savefile.ReadInt()
            focusVehicle = savefile.ReadObject() as idAFEntity_Vehicle?
            cursor = savefile.ReadUserInterface()
            // DG: make it scale to 4:3 so crosshair looks properly round
            cursor?.SetStateBool("scaleto43", true)
            cursor?.StateChanged(gameLocal.time)
            oldMouseX = savefile.ReadInt()
            oldMouseY = savefile.ReadInt()
            savefile.ReadString(pdaAudio)
            savefile.ReadString(pdaVideo)
            savefile.ReadString(pdaVideoWave)
            tipUp = savefile.ReadBool()
            objectiveUp = savefile.ReadBool()
            lastDamageDef = savefile.ReadInt()
            savefile.ReadVec3(lastDamageDir)
            lastDamageLocation = savefile.ReadInt()
            smoothedFrame = savefile.ReadInt()
            smoothedOriginUpdated = savefile.ReadBool()
            savefile.ReadVec3(smoothedOrigin)
            savefile.ReadAngles(smoothedAngles)
            ready = savefile.ReadBool()
            respawning = savefile.ReadBool()
            leader = savefile.ReadBool()
            lastSpectateChange = savefile.ReadInt()
            lastTeleFX = savefile.ReadInt()

            // set the pm_ cvars
            var kv: idKeyValue?
            kv = spawnArgs.MatchPrefix("pm_", null)
            while (kv != null) {
                CVarSystem.cvarSystem.SetCVarString(kv.GetKey().toString(), kv.GetValue().toString())
                kv = spawnArgs.MatchPrefix("pm_", kv)
            }
            savefile.ReadFloat(set)
            SysCvar.pm_stamina.SetFloat(set._val)

            if (isD3XP) {
                mountedObject = savefile.ReadObject() as? idEntity
                enviroSuitLight.Restore(savefile)
                healthRecharge = savefile.ReadBool()
                lastHealthRechargeTime = savefile.ReadInt()
                rechargeSpeed = savefile.ReadInt()
                new_g_damageScale = savefile.ReadFloat()
                bloomEnabled = savefile.ReadBool()
                bloomSpeed = savefile.ReadFloat()
                bloomIntensity = savefile.ReadFloat()
            }

            // create combat collision hull for exact collision detection
            SetCombatModel()

            // DG: workaround for lingering messages that are shown forever after loading a savegame
            if (hud != null) {
                hud!!.SetStateString("message", "")
            }
        }

        override fun Hide() {
            val weap: idWeapon
            super.Hide()
            weap = weapon.GetEntity()!!
            weap.HideWorldModel()
        }

        override fun Show() {
            val weap: idWeapon
            super.Show()
            weap = weapon.GetEntity()!!
            weap.ShowWorldModel()
        }

        override fun Init() {
            val value = arrayOfNulls<String>(1)
            var kv: idKeyValue?
            noclip = false
            godmode = false
            oldButtons = 0
            oldFlags = 0
            currentWeapon = -1
            idealWeapon = -1
            previousWeapon = -1
            weaponSwitchTime = 0
            weaponEnabled = true
            weapon_soulcube = SlotForWeapon("weapon_soulcube")
            weapon_pda = SlotForWeapon("weapon_pda")
            weapon_fists = SlotForWeapon("weapon_fists")
            if (isD3XP) {
                weapon_bloodstone = SlotForWeapon("weapon_bloodstone_passive")
                weapon_bloodstone_active1 = SlotForWeapon("weapon_bloodstone_active1")
                weapon_bloodstone_active2 = SlotForWeapon("weapon_bloodstone_active2")
                weapon_bloodstone_active3 = SlotForWeapon("weapon_bloodstone_active3")
                harvest_lock = false
                // mountedObject = null  // TODO: add when idFuncMountedObject is implemented
                if (enviroSuitLight.GetEntity() != null) {
                    enviroSuitLight.GetEntity()!!.PostEventMS(EV_Remove, 0)
                }
                enviroSuitLight.oSet(null)
                healthRecharge = false
                lastHealthRechargeTime = 0
                rechargeSpeed = 500
                new_g_damageScale = 1.0f
                bloomEnabled = false
                bloomSpeed = 1.0f
                bloomIntensity = -0.01f
                inventory.InitRechargeAmmo(this)
                hudPowerup = -1
                lastHudPowerup = -1
                hudPowerupDuration = 0
            }
            showWeaponViewModel = GetUserInfo().GetBool("ui_showGun")
            lastDmgTime = 0
            lastArmorPulse = -10000
            lastHeartAdjust = 0
            lastHeartBeat = 0
            heartInfo.Init(0.0f, 0.0f, 0.0f, 0.0f)
            bobCycle = 0
            bobFrac = 0.0f
            landChange = 0
            landTime = 0
            zoomFov.Init(0.0f, 0.0f, 0.0f, 0.0f)
            centerView.Init(0.0f, 0.0f, 0.0f, 0.0f)
            fxFov = false
            influenceFov = 0.0f
            influenceActive = 0
            influenceRadius = 0.0f
            influenceEntity = null
            influenceMaterial = null
            influenceSkin = null
            currentLoggedAccel = 0
            focusTime = 0
            focusGUIent = null
            focusUI = null
            focusCharacter = null
            talkCursor = 0
            focusVehicle = null

            // remove any damage effects
            playerView.ClearEffects()

            // damage values
            fl.takedamage = true
            ClearPain()

            // restore persistent data
            RestorePersistantInfo()
            bobCycle = 0
            stamina = 0.0f
            healthPool = 0.0f
            nextHealthPulse = 0
            healthPulse = false
            nextHealthTake = 0
            healthTake = false
            SetupWeaponEntity()
            currentWeapon = -1
            previousWeapon = -1
            heartRate = BASE_HEARTRATE
            AdjustHeartRate(BASE_HEARTRATE, 0.0f, 0.0f, true)
            idealLegsYaw = 0.0f
            legsYaw = 0.0f
            legsForward = true
            oldViewYaw = 0.0f

            // set the pm_ cvars
            if (!gameLocal.isMultiplayer || gameLocal.isServer) {
                kv = spawnArgs.MatchPrefix("pm_", null)
                while (kv != null) {
                    CVarSystem.cvarSystem.SetCVarString(kv.GetKey().toString(), kv.GetValue().toString())
                    kv = spawnArgs.MatchPrefix("pm_", kv)
                }
            }

            // disable stamina on hell levels
            if (gameLocal.world != null && gameLocal.world!!.spawnArgs.GetBool("no_stamina")) {
                SysCvar.pm_stamina.SetFloat(0.0f)
            }

            // stamina always initialized to maximum
            stamina = SysCvar.pm_stamina.GetFloat()

            // air always initialized to maximum too
            airTics = SysCvar.pm_airTics.GetFloat().toInt()
            airless = false
            gibDeath = false
            gibsLaunched = false
            gibsDir.Zero()

            // set the gravity
            physicsObj.SetGravity(gameLocal.GetGravity())

            // start out standing
            SetEyeHeight(SysCvar.pm_normalviewheight.GetFloat())
            stepUpTime = 0
            stepUpDelta = 0.0f
            viewBobAngles.Zero()
            viewBob.Zero()
            value[0] = spawnArgs.GetString("model")
            if (value.isNotEmpty() && !value[0].isNullOrEmpty()) {
                SetModel(value[0]!!)
            }
            if (cursor != null) {
                cursor!!.SetStateInt("talkcursor", 0)
                cursor!!.SetStateString("combatcursor", "1")
                cursor!!.SetStateString("itemcursor", "0")
                cursor!!.SetStateString("guicursor", "0")
                if (isD3XP) {
                    cursor!!.SetStateString("grabbercursor", "0")
                }
            }
            if ((gameLocal.isMultiplayer || SysCvar.g_testDeath.GetBool()) && skin != null) {
                SetSkin(skin)
                renderEntity!!.shaderParms[6] = 0.0f
            } else if (spawnArgs.GetString("spawn_skin", "", value)) {
                skin = DeclManager.declManager.FindSkin(value[0]!!)
                SetSkin(skin)
                renderEntity!!.shaderParms[6] = 0.0f
            }
            value[0] = spawnArgs.GetString("bone_hips", "")!!
            hipJoint = animator.GetJointHandle(value[0]!!)
            if (hipJoint == Model.INVALID_JOINT) {
                idGameLocal.Error("Joint '%s' not found for 'bone_hips' on '%s'", value[0], name)
            }
            value[0] = spawnArgs.GetString("bone_chest", "")!!
            chestJoint = animator.GetJointHandle(value[0]!!)
            if (chestJoint == Model.INVALID_JOINT) {
                idGameLocal.Error("Joint '%s' not found for 'bone_chest' on '%s'", value[0], name)
            }
            value[0] = spawnArgs.GetString("bone_head", "")!!
            headJoint = animator.GetJointHandle(value[0]!!)
            if (headJoint == Model.INVALID_JOINT) {
                idGameLocal.Error("Joint '%s' not found for 'bone_head' on '%s'", value[0], name)
            }

            // initialize the script variables
            AI_FORWARD.underscore(false)
            AI_BACKWARD.underscore(false)
            AI_STRAFE_LEFT.underscore(false)
            AI_STRAFE_RIGHT.underscore(false)
            AI_ATTACK_HELD.underscore(false)
            AI_WEAPON_FIRED.underscore(false)
            AI_JUMP.underscore(false)
            AI_DEAD.underscore(false)
            AI_CROUCH.underscore(false)
            AI_ONGROUND.underscore(true)
            AI_ONLADDER.underscore(false)
            AI_HARDLANDING.underscore(false)
            AI_SOFTLANDING.underscore(false)
            AI_RUN.underscore(false)
            AI_PAIN.underscore(false)
            AI_RELOAD.underscore(false)
            AI_TELEPORT.underscore(false)
            AI_TURN_LEFT.underscore(false)
            AI_TURN_RIGHT.underscore(false)

            // reset the script object
            ConstructScriptObject()

            // execute the script so the script object's constructor takes effect immediately
            scriptThread!!.Execute()
            forceScoreBoard = false
            forcedReady = false
            privateCameraView = null
            lastSpectateChange = 0
            lastTeleFX = -9999
            hiddenWeapon = false
            tipUp = false
            objectiveUp = false
            teleportEntity.oSet(null)
            teleportKiller = -1
            leader = false
            SetPrivateCameraView(null)
            lastSnapshotSequence = 0
            MPAim = -1
            lastMPAim = -1
            lastMPAimTime = 0
            MPAimFadeTime = 0
            MPAimHighlight = false
            if (hud != null) {
                hud!!.HandleNamedEvent("aim_clear")
            }
            CVarSystem.cvarSystem.SetCVarBool("ui_chat", false)
        }

        fun PrepareForRestart() {
            ClearPowerUps()
            Spectate(true)
            forceRespawn = true

            // we will be restarting program, clear the client entities from program-related things first
            ShutdownThreads()

            // the sound world is going to be cleared, don't keep references to emitters
            FreeSoundEmitter(false)
        }

        override fun Restart() {
            super.Restart()

            // client needs to setup the animation script object again
            if (gameLocal.isClient) {
                Init()
            } else {
                // choose a random spot and prepare the point of view in case player is left spectating
                assert(spectating)
                SpawnFromSpawnSpot()
            }
            useInitialSpawns = true
            UpdateSkinSetup(true)
        }

        /*
         ==============
         idPlayer::LinkScriptVariables

         set up conditions for animation
         ==============
         */
        fun LinkScriptVariables() {
            AI_FORWARD.LinkTo(scriptObject, "AI_FORWARD")
            AI_BACKWARD.LinkTo(scriptObject, "AI_BACKWARD")
            AI_STRAFE_LEFT.LinkTo(scriptObject, "AI_STRAFE_LEFT")
            AI_STRAFE_RIGHT.LinkTo(scriptObject, "AI_STRAFE_RIGHT")
            AI_ATTACK_HELD.LinkTo(scriptObject, "AI_ATTACK_HELD")
            AI_WEAPON_FIRED.LinkTo(scriptObject, "AI_WEAPON_FIRED")
            AI_JUMP.LinkTo(scriptObject, "AI_JUMP")
            AI_DEAD.LinkTo(scriptObject, "AI_DEAD")
            AI_CROUCH.LinkTo(scriptObject, "AI_CROUCH")
            AI_ONGROUND.LinkTo(scriptObject, "AI_ONGROUND")
            AI_ONLADDER.LinkTo(scriptObject, "AI_ONLADDER")
            AI_HARDLANDING.LinkTo(scriptObject, "AI_HARDLANDING")
            AI_SOFTLANDING.LinkTo(scriptObject, "AI_SOFTLANDING")
            AI_RUN.LinkTo(scriptObject, "AI_RUN")
            AI_PAIN.LinkTo(scriptObject, "AI_PAIN")
            AI_RELOAD.LinkTo(scriptObject, "AI_RELOAD")
            AI_TELEPORT.LinkTo(scriptObject, "AI_TELEPORT")
            AI_TURN_LEFT.LinkTo(scriptObject, "AI_TURN_LEFT")
            AI_TURN_RIGHT.LinkTo(scriptObject, "AI_TURN_RIGHT")
        }

        fun SetupWeaponEntity() {
            var w: Int
            var weap: String
            if (weapon.GetEntity() != null) {
                // get rid of old weapon
                weapon.GetEntity()!!.Clear()
                currentWeapon = -1
            } else if (!gameLocal.isClient) {
                weapon.oSet(gameLocal.SpawnEntityType(idWeapon.Type, null) as idWeapon)
                weapon.GetEntity()!!.SetOwner(this)
                currentWeapon = -1
            }
            w = 0
            while (w < MAX_WEAPONS) {
                weap = spawnArgs.GetString(Str.va("def_weapon%d", w))
                if (weap != null && weap.isNotEmpty()) {
                    idWeapon.CacheWeapon(weap)
                }
                w++
            }
        }

        /*
         ===========
         idPlayer::SelectInitialSpawnPoint

         Try to find a spawn point marked 'initial', otherwise
         use normal spawn selection.
         ============
         */
        fun SelectInitialSpawnPoint(origin: idVec3, angles: idAngles) {
            val spot: idEntity
            val skin = idStr()
            spot = gameLocal.SelectInitialSpawnPoint(this)!!

            // set the player skin from the spawn location
            if (spot.spawnArgs.GetString("skin", null, skin)) {
                spawnArgs.Set("spawn_skin", skin)
            }

            // activate the spawn locations targets
            spot.PostEventMS(EV_ActivateTargets, 0, this)
            origin.set(spot.GetPhysics().GetOrigin())
            origin.plusAssign(
                2, 4.0f + CM_BOX_EPSILON
            ) // move up to make sure the player is at least an epsilon above the floor
            angles.set(spot.GetPhysics().GetAxis().ToAngles())
        }

        /*
         ===========
         idPlayer::SpawnFromSpawnSpot

         Chooses a spawn location and spawns the player
         ============
         */
        fun SpawnFromSpawnSpot() {
            val spawn_origin = idVec3()
            val spawn_angles = idAngles()
            SelectInitialSpawnPoint(spawn_origin, spawn_angles)
            SpawnToPoint(spawn_origin, spawn_angles)
        }

        /*
         ===========
         idPlayer::SpawnToPoint

         Called every time a client is placed fresh in the world:
         after the first ClientBegin, and after each respawn
         Initializes all non-persistant parts of playerState

         when called here with spectating set to true, just place yourself and init
         ============
         */
        fun SpawnToPoint(spawn_origin: idVec3, spawn_angles: idAngles) {
            val spec_origin = idVec3()
            assert(!gameLocal.isClient)
            respawning = true
            Init()
            fl.noknockback = false

            // stop any ragdolls being used
            StopRagdoll()

            // set back the player physics
            SetPhysics(physicsObj)
            physicsObj.SetClipModelAxis()
            physicsObj.EnableClip()
            if (!spectating) {
                SetCombatContents(true)
            }
            physicsObj.SetLinearVelocity(vec3_origin)

            // setup our initial view
            if (!spectating) {
                SetOrigin(spawn_origin)
            } else {
                spec_origin.set(spawn_origin)
                spec_origin.plusAssign(2, SysCvar.pm_normalheight.GetFloat())
                spec_origin.plusAssign(2, SPECTATE_RAISE.toFloat())
                SetOrigin(spec_origin)
            }

            // if this is the first spawn of the map, we don't have a usercmd yet,
            // so the delta angles won't be correct.  This will be fixed on the first think.
            viewAngles.set(ang_zero)
            SetDeltaViewAngles(ang_zero)
            SetViewAngles(spawn_angles)
            spawnAngles.set(spawn_angles)
            spawnAnglesSet = false
            legsForward = true
            legsYaw = 0.0f
            idealLegsYaw = 0.0f
            oldViewYaw = viewAngles.yaw
            if (spectating) {
                Hide()
            } else {
                Show()
            }
            if (gameLocal.isMultiplayer) {
                if (!spectating) {
                    // we may be called twice in a row in some situations. avoid a double fx and 'fly to the roof'
                    if (lastTeleFX < gameLocal.time - 1000) {
                        idEntityFx.StartFx(
                            spawnArgs.GetString("fx_spawn"), spawn_origin, null, this, true
                        )
                        lastTeleFX = gameLocal.time
                    }
                }
                AI_TELEPORT.underscore(true)
            } else {
                AI_TELEPORT.underscore(false)
            }

            // kill anything at the new position
            if (!spectating) {
                physicsObj.SetClipMask(Game_local.MASK_PLAYERSOLID) // the clip mask is usually maintained in Move(), but KillBox requires it
                gameLocal.KillBox(this)
            }

            // don't allow full run speed for a bit
            physicsObj.SetKnockBack(100)

            // set our respawn time and buttons so that if we're killed we don't respawn immediately
            minRespawnTime = gameLocal.time
            maxRespawnTime = gameLocal.time
            if (!spectating) {
                forceRespawn = false
            }
            privateCameraView = null
            BecomeActive(TH_THINK)

            // run a client frame to drop exactly to the floor,
            // initialize animations and other things
            Think()
            respawning = false
            lastManOver = false
            lastManPlayAgain = false
            isTelefragged = false
        }

        fun SetClipModel() {
            val bounds = idBounds()
            if (spectating) {
                bounds.set(idBounds(vec3_origin).Expand(SysCvar.pm_spectatebbox.GetFloat() * 0.5f))
            } else {
                bounds[0].set(-SysCvar.pm_bboxwidth.GetFloat() * 0.5f, -SysCvar.pm_bboxwidth.GetFloat() * 0.5f, 0.0f)
                bounds[1].set(
                    SysCvar.pm_bboxwidth.GetFloat() * 0.5f,
                    SysCvar.pm_bboxwidth.GetFloat() * 0.5f,
                    SysCvar.pm_normalheight.GetFloat()
                )
            }
            // the origin of the clip model needs to be set before calling SetClipModel
            // otherwise our physics object's current origin value gets reset to 0
            val newClip: idClipModel
            if (SysCvar.pm_usecylinder.GetBool()) {
                newClip = idClipModel(idTraceModel(bounds, 8))
                newClip.Translate(physicsObj.PlayerGetOrigin())
                physicsObj.SetClipModel(newClip, 1.0f)
            } else {
                newClip = idClipModel(idTraceModel(bounds))
                newClip.Translate(physicsObj.PlayerGetOrigin())
                physicsObj.SetClipModel(newClip, 1.0f)
            }
        }

        /*
         ===============
         idPlayer::SavePersistantInfo

         Saves any inventory and player stats when changing levels.
         ===============
         */
        fun SavePersistantInfo() {
            val playerInfo = gameLocal.persistentPlayerInfo[entityNumber]
            playerInfo.Clear()
            inventory.GetPersistantData(playerInfo)
            playerInfo.SetInt("health", health)
            playerInfo.SetInt("current_weapon", currentWeapon)
        }

        /*
         ===============
         idPlayer::RestorePersistantInfo

         Restores any inventory and player stats when changing levels.
         ===============
         */
        fun RestorePersistantInfo() {
            if (gameLocal.isMultiplayer) {
                gameLocal.persistentPlayerInfo[entityNumber].Clear()
            }
            spawnArgs.Copy(gameLocal.persistentPlayerInfo[entityNumber])
            inventory.RestoreInventory(this, spawnArgs)
            health = spawnArgs.GetInt("health", "100")
            if (!gameLocal.isClient) {
                idealWeapon = spawnArgs.GetInt("current_weapon", "1")
            }
        }

        fun SetLevelTrigger(levelName: String?, triggerName: String?) {
            if (levelName != null && levelName.isNotEmpty() && triggerName != null && triggerName.isNotEmpty()) {
                val lti = idLevelTriggerInfo()
                lti.levelName.set(levelName)
                lti.triggerName.set(triggerName)
                inventory.levelTriggers.Append(lti)
            }
        }

        fun UserInfoChanged(canModify: Boolean): Boolean {
            val userInfo: idDict
            var modifiedInfo: Boolean
            val spec: Boolean
            val newready: Boolean
            userInfo = GetUserInfo()
            showWeaponViewModel = userInfo.GetBool("ui_showGun")
            if (!gameLocal.isMultiplayer) {
                return false
            }
            modifiedInfo = false
            spec = idStr.Icmp(userInfo.GetString("ui_spectate"), "Spectate") == 0
            if (gameLocal.serverInfo.GetBool("si_spectators")) {
                // never let spectators go back to game while sudden death is on
                if (canModify && gameLocal.mpGame.GetGameState() == idMultiplayerGame.gameState_t.SUDDENDEATH && !spec && wantSpectate) {
                    userInfo.Set("ui_spectate", "Spectate")
                    modifiedInfo = modifiedInfo or true
                } else {
                    if (spec != wantSpectate && !spec) {
                        // returning from spectate, set forceRespawn so we don't get stuck in spectate forever
                        forceRespawn = true
                    }
                    wantSpectate = spec
                }
            } else {
                if (canModify && spec) {
                    userInfo.Set("ui_spectate", "Play")
                    modifiedInfo = modifiedInfo or true
                } else if (spectating) {
                    // allow player to leaving spectator mode if they were in it when si_spectators got turned off
                    forceRespawn = true
                }
                wantSpectate = false
            }
            newready = idStr.Icmp(userInfo.GetString("ui_ready"), "Ready") == 0
            if (ready != newready && gameLocal.mpGame.GetGameState() == idMultiplayerGame.gameState_t.WARMUP && !wantSpectate) {
                gameLocal.mpGame.AddChatLine(
                    Common.common.GetLanguageDict().GetString("#str_07180"),
                    userInfo.GetString("ui_name"),
                    if (newready) Common.common.GetLanguageDict()
                        .GetString("#str_04300") else Common.common.GetLanguageDict().GetString("#str_04301")
                )
            }
            ready = newready
            team = idStr.Icmp(userInfo.GetString("ui_team"), "Blue") and 1 //== 0);
            // server maintains TDM balance
            if (canModify && gameLocal.gameType == gameType_t.GAME_TDM && !gameLocal.mpGame.IsInGame(
                    entityNumber
                ) && SysCvar.g_balanceTDM.GetBool()
            ) {
                modifiedInfo = modifiedInfo or BalanceTDM()
            }
            UpdateSkinSetup(false)
            isChatting = userInfo.GetBool("ui_chat", "0")
            if (canModify && isChatting && AI_DEAD.underscore()!!) {
                // if dead, always force chat icon off.
                isChatting = false
                userInfo.SetBool("ui_chat", false)
                modifiedInfo = modifiedInfo or true
            }
            return modifiedInfo
        }

        fun GetUserInfo(): idDict {
            return gameLocal.userInfo[entityNumber]
        }

        fun BalanceTDM(): Boolean {
            var i: Int
            var balanceTeam: Int
            val teamCount = IntArray(2)
            var ent: idEntity?
            teamCount[1] = 0
            teamCount[0] = teamCount[1]
            i = 0
            while (i < gameLocal.numClients) {
                ent = gameLocal.entities[i]
                if (ent != null && ent is idPlayer) {
                    teamCount[ent.team]++
                }
                i++
            }
            balanceTeam = -1
            if (teamCount[0] < teamCount[1]) {
                balanceTeam = 0
            } else if (teamCount[0] > teamCount[1]) {
                balanceTeam = 1
            }
            if (balanceTeam != -1 && team != balanceTeam) {
                Common.common.DPrintf(
                    "team balance: forcing player %d to %s team\n",
                    entityNumber,
                    if (TempDump.itob(balanceTeam)) "blue" else "red"
                )
                team = balanceTeam
                GetUserInfo().Set("ui_team", if (TempDump.itob(team)) "Blue" else "Red")
                return true
            }
            return false
        }

        fun CacheWeapons() {
            var weap: String
            var w: Int

            // check if we have any weapons
            if (0 == inventory.weapons) {
                return
            }
            w = 0
            while (w < MAX_WEAPONS) {
                if ((inventory.weapons and (1 shl w)) != 0) {
                    weap = spawnArgs.GetString(Str.va("def_weapon%d", w))
                    if ("" != weap) {
                        idWeapon.CacheWeapon(weap)
                    } else {
                        inventory.weapons = inventory.weapons and (1 shl w).inv()
                    }
                }
                w++
            }
        }

        fun EnterCinematic() {
            Hide()
            StopAudioLog()
            StopSound(TempDump.etoi(gameSoundChannel_t.SND_CHANNEL_PDA), false)
            if (hud != null) {
                hud!!.HandleNamedEvent("radioChatterDown")
            }
            physicsObj.SetLinearVelocity(vec3_origin)
            SetState("EnterCinematic")
            UpdateScript()
            if (weaponEnabled && weapon.GetEntity() != null) {
                weapon.GetEntity()!!.EnterCinematic()
            }
            AI_FORWARD.underscore(false)
            AI_BACKWARD.underscore(false)
            AI_STRAFE_LEFT.underscore(false)
            AI_STRAFE_RIGHT.underscore(false)
            AI_RUN.underscore(false)
            AI_ATTACK_HELD.underscore(false)
            AI_WEAPON_FIRED.underscore(false)
            AI_JUMP.underscore(false)
            AI_CROUCH.underscore(false)
            AI_ONGROUND.underscore(true)
            AI_ONLADDER.underscore(false)
            AI_DEAD.underscore(health <= 0)
            AI_RUN.underscore(false)
            AI_PAIN.underscore(false)
            AI_HARDLANDING.underscore(false)
            AI_SOFTLANDING.underscore(false)
            AI_RELOAD.underscore(false)
            AI_TELEPORT.underscore(false)
            AI_TURN_LEFT.underscore(false)
            AI_TURN_RIGHT.underscore(false)
        }

        fun ExitCinematic() {
            Show()
            if (weaponEnabled && weapon.GetEntity() != null) {
                weapon.GetEntity()!!.ExitCinematic()
            }
            SetState("ExitCinematic")
            UpdateScript()
        }

        fun HandleESC(): Boolean {
            if (gameLocal.inCinematic) {
                return SkipCinematic()
            }
            if (objectiveSystemOpen) {
                TogglePDA()
                return true
            }
            return false
        }

        fun SkipCinematic(): Boolean {
            StartSound("snd_skipcinematic", gameSoundChannel_t.SND_CHANNEL_ANY, 0, false)
            return gameLocal.SkipCinematic()
        }

        fun UpdateConditions() {
            val velocity = idVec3()
            val fallspeed: Float
            val forwardspeed: Float
            val sidespeed: Float

            // minus the push velocity to avoid playing the walking animation and sounds when riding a mover
            velocity.set(physicsObj.GetLinearVelocity().minus(physicsObj.GetPushedLinearVelocity()))
            fallspeed = velocity.times(physicsObj.GetGravityNormal())
            if (influenceActive != 0) {
                AI_FORWARD.underscore(false)
                AI_BACKWARD.underscore(false)
                AI_STRAFE_LEFT.underscore(false)
                AI_STRAFE_RIGHT.underscore(false)
            } else if (gameLocal.time - lastDmgTime < 500) {
                forwardspeed = velocity.times(viewAxis[0])
                sidespeed = velocity.times(viewAxis[1])
                AI_FORWARD.underscore(AI_ONGROUND.underscore()!! && forwardspeed > 20.01)
                AI_BACKWARD.underscore(AI_ONGROUND.underscore()!! && forwardspeed < -20.01)
                AI_STRAFE_LEFT.underscore(AI_ONGROUND.underscore()!! && sidespeed > 20.01)
                AI_STRAFE_RIGHT.underscore(AI_ONGROUND.underscore()!! && sidespeed < -20.01)
            } else if (xyspeed > MIN_BOB_SPEED) {
                AI_FORWARD.underscore(AI_ONGROUND.underscore()!! && usercmd.forwardmove > 0)
                AI_BACKWARD.underscore(AI_ONGROUND.underscore()!! && usercmd.forwardmove < 0)
                AI_STRAFE_LEFT.underscore(AI_ONGROUND.underscore()!! && usercmd.rightmove < 0)
                AI_STRAFE_RIGHT.underscore(AI_ONGROUND.underscore()!! && usercmd.rightmove > 0)
            } else {
                AI_FORWARD.underscore(false)
                AI_BACKWARD.underscore(false)
                AI_STRAFE_LEFT.underscore(false)
                AI_STRAFE_RIGHT.underscore(false)
            }
            AI_RUN.underscore(
                (usercmd.buttons.toInt() and UsercmdGen.BUTTON_RUN) != 0 && (
                        SysCvar.pm_stamina.GetFloat() == 0.0f || stamina > SysCvar.pm_staminathreshold.GetFloat()
                        )
            )
            AI_DEAD.underscore(health <= 0)
        }

        fun SetViewAngles(angles: idAngles) {
            UpdateDeltaViewAngles(angles)
            viewAngles.set(angles)
        }

        // delta view angles to allow movers to rotate the view of the player
        fun UpdateDeltaViewAngles(angles: idAngles) {
            // set the delta angle
            val delta = idAngles()
            for (i in 0..2) {
                delta[i] = angles[i] - SHORT2ANGLE(usercmd.angles[i])
            }
            SetDeltaViewAngles(delta)
        }

        override fun Collide(collision: trace_s, velocity: idVec3): Boolean {
            val other: idEntity?
            if (gameLocal.isClient) {
                return false
            }
            other = gameLocal.entities[collision.c.entityNum]
            if (other != null) {
                other.Signal(signalNum_t.SIG_TOUCH)
                if (!spectating) {
                    if (other.RespondsTo(EV_Touch)) {
                        other.ProcessEvent(EV_Touch, this, collision)
                    }
                } else {
                    if (other.RespondsTo(EV_SpectatorTouch)) {
                        other.ProcessEvent(EV_SpectatorTouch, this, collision)
                    }
                }
            }
            return false
        }

        override fun GetAASLocation(aas: idAAS?, pos: idVec3, areaNum: CInt) {
            var i: Int
            if (aas != null) {
                i = 0
                while (i < aasLocation.Num()) {
                    if (aas === gameLocal.GetAAS(i)) {
                        areaNum._val = (aasLocation[i].areaNum)
                        pos.set(aasLocation[i].pos)
                        return
                    }
                    i++
                }
            }
            areaNum._val = (0)
            pos.set(physicsObj.GetOrigin())
        }

        /*
         =====================
         idPlayer::GetAIAimTargets

         Returns positions for the AI to aim at.
         =====================
         */
        override fun GetAIAimTargets(lastSightPos: idVec3, headPos: idVec3, chestPos: idVec3) {
            val offset = idVec3()
            val axis = idMat3()
            val origin = idVec3()
            origin.set(lastSightPos.minus(physicsObj.GetOrigin()))
            GetJointWorldTransform(chestJoint, gameLocal.time, offset, axis)
            headPos.set(offset.plus(origin))
            GetJointWorldTransform(headJoint, gameLocal.time, offset, axis)
            chestPos.set(offset.plus(origin))
        }

        /*
         ================
         idPlayer::DamageFeedback

         callback function for when another entity received damage from this entity.  damage can be adjusted and returned to the caller.
         ================
         */
        override fun DamageFeedback(victim: idEntity?, inflictor: idEntity?, damage: CInt) {
            assert(!gameLocal.isClient)
            damage._val = ((PowerUpModifier(BERSERK) * damage._val).toInt())
            if (damage._val != 0 && victim !== this && victim is idActor) {
                SetLastHitTime(gameLocal.time)
            }
        }

        /*
         =================
         idPlayer::CalcDamagePoints

         Calculates how many health and armor points will be inflicted, but
         doesn't actually do anything with them.  This is used to tell when an attack
         would have killed the player, possibly allowing a "saving throw"
         =================
         */
        fun CalcDamagePoints(
            inflictor: idEntity,
            attacker: idEntity,
            damageDef: idDict,
            damageScale: Float,
            location: Int,
            health: CInt,
            armor: CInt
        ) {
            val damage = CInt()
            var armorSave: Int
            damageDef.GetInt("damage", "20", damage)
            damage._val = (GetDamageForLocation(damage._val, location))
            val player = if (attacker is idPlayer) attacker else null
            if (!gameLocal.isMultiplayer) {
                if (inflictor !== gameLocal.world) {
                    when (SysCvar.g_skill.GetInteger()) {
                        0 -> {
                            damage._val = ((damage._val * 0.80).toInt())
                            if (damage._val < 1) {
                                damage._val = (1)
                            }
                        }

                        2 -> damage._val = ((damage._val * 1.70).toInt())
                        3 -> damage._val = ((damage._val * 3.5).toInt())
                        else -> {}
                    }
                }
            }
            damage._val = ((damage._val * damageScale).toInt())

            // always give half damage if hurting self
            if (attacker == this) {
                if (gameLocal.isMultiplayer) {
                    // only do this in mp so single player plasma and rocket splash is very dangerous in close quarters
                    damage._val =
                        ((damage._val * damageDef.GetFloat("selfDamageScale", "0.5f")).toInt())
                } else {
                    damage._val = ((damage._val * damageDef.GetFloat("selfDamageScale", "1")).toInt())
                }
            }

            // check for completely getting out of the damage
            if (!damageDef.GetBool("noGod")) {
                // check for godmode
                if (godmode) {
                    damage._val = (0)
                }
            }

            // inform the attacker that they hit someone
            attacker.DamageFeedback(this, inflictor, damage)

            // save some from armor
            if (!damageDef.GetBool("noArmor")) {
                val armor_protection: Float
                armor_protection =
                    if (gameLocal.isMultiplayer) SysCvar.g_armorProtectionMP.GetFloat() else SysCvar.g_armorProtection.GetFloat()
                armorSave = ceil((damage._val * armor_protection)).toInt()
                if (armorSave >= inventory.armor) {
                    armorSave = inventory.armor
                }
                if (0 == damage._val) {
                    armorSave = 0
                } else if (armorSave >= damage._val) {
                    armorSave = damage._val - 1
                    damage._val = (1)
                } else {
                    damage._val = (damage._val - armorSave)
                }
            } else {
                armorSave = 0
            }

            // check for team damage
            if (gameLocal.gameType == gameType_t.GAME_TDM && !gameLocal.serverInfo.GetBool("si_teamDamage") && !damageDef.GetBool(
                    "noTeam"
                ) && player != null && player != this // you get self damage no matter what
                && player.team == team
            ) {
                damage._val = (0)
            }
            health._val = (damage._val)
            armor._val = (armorSave)
        }

        /*
         ============
         Damage

         this		entity that is being damaged
         inflictor	entity that is causing the damage
         attacker	entity that caused the inflictor to damage targ
         example: this=monster, inflictor=rocket, attacker=player

         dir			direction of the attack for knockback in global space

         damageDef	an idDict with all the options for damage effects

         inflictor, attacker, dir, and point can be NULL for environmental effects
         ============
         */
        override fun Damage(
            inflictor: idEntity?,
            attacker: idEntity?,
            dir: idVec3,
            damageDefName: String,
            damageScale: Float,
            location: Int
        ) {
            // TODO: this seems like another pointer bs, which needs to be handled differently
            var inflictor = inflictor
            var attacker = attacker
            val kick = idVec3()
            val damage = CInt()
            val armorSave = CInt()
            val knockback = CInt()
            val damage_from = idVec3()
            val localDamageVector = idVec3()
            val attackerPushScale = CFloat()

            // damage is only processed on server
            if (gameLocal.isClient) {
                return
            }
            if (!fl.takedamage || noclip || spectating || gameLocal.inCinematic) {
                return
            }
            if (null == inflictor) {
                inflictor = gameLocal.world
            }
            if (null == attacker) {
                attacker = gameLocal.world
            }
            if (attacker is idAI) {
                if (PowerUpActive(BERSERK)) {
                    return
                }
                // don't take damage from monsters during influences
                if (influenceActive != 0) {
                    return
                }
            }
            val damageDef = gameLocal.FindEntityDef(damageDefName, false)
            if (null == damageDef) {
                gameLocal.Warning("Unknown damageDef '%s'", damageDefName)
                return
            }
            if (damageDef.dict.GetBool("ignore_player")) {
                return
            }
            CalcDamagePoints(inflictor!!, attacker!!, damageDef.dict, damageScale, location, damage, armorSave)

            // determine knockback
            damageDef.dict.GetInt("knockback", "20", knockback)
            if (knockback._val != 0 && !fl.noknockback) {
                if (attacker === this) {
                    damageDef.dict.GetFloat("attackerPushScale", "0", attackerPushScale)
                } else {
                    attackerPushScale._val = (1.0f)
                }
                kick.set(dir)
                kick.Normalize()
                kick.timesAssign(SysCvar.g_knockback.GetFloat() * knockback._val * attackerPushScale._val / 200)
                physicsObj.SetLinearVelocity(physicsObj.GetLinearVelocity().plus(kick))

                // set the timer so that the player can't cancel out the movement immediately
                physicsObj.SetKnockBack(idMath.ClampInt(50, 200, knockback._val * 2))
            }

            // give feedback on the player view and audibly when armor is helping
            if (armorSave._val != 0) {
                inventory.armor -= armorSave._val
                if (gameLocal.time > lastArmorPulse + 200) {
                    StartSound("snd_hitArmor", gameSoundChannel_t.SND_CHANNEL_ITEM, 0, false)
                }
                lastArmorPulse = gameLocal.time
            }
            if (damageDef.dict.GetBool("burn")) {
                StartSound("snd_burn", gameSoundChannel_t.SND_CHANNEL_BODY3, 0, false)
            } else if (damageDef.dict.GetBool("no_air")) {
                if (0 == armorSave._val && health > 0) {
                    StartSound("snd_airGasp", gameSoundChannel_t.SND_CHANNEL_ITEM, 0, false)
                }
            }
            if (SysCvar.g_debugDamage.GetInteger() != 0) {
                gameLocal.Printf(
                    "client:%d health:%d damage:%d armor:%d\n",
                    entityNumber,
                    health,
                    damage._val,
                    armorSave._val
                )
            }

            // move the world direction vector to local coordinates
            damage_from.set(dir)
            damage_from.Normalize()
            viewAxis.ProjectVector(damage_from, localDamageVector)

            // add to the damage inflicted on a player this frame
            // the total will be turned into screen blends and view angle kicks
            // at the end of the frame
            if (health > 0) {
                playerView.DamageImpulse(localDamageVector, damageDef.dict)
            }

            // do the damage
            if (damage._val > 0) {
                if (!gameLocal.isMultiplayer) {
                    var scale = SysCvar.g_damageScale.GetFloat()
                    if (SysCvar.g_useDynamicProtection.GetBool() && SysCvar.g_skill.GetInteger() < 2) {
                        if (gameLocal.time > lastDmgTime + 500 && scale > 0.25f) {
                            scale -= 0.05f
                            SysCvar.g_damageScale.SetFloat(scale)
                        }
                    }
                    if (scale > 0) {
                        damage._val = ((damage._val * scale).toInt())
                    }
                }
                if (damage._val < 1) {
                    damage._val = (1)
                }
                health
                health -= damage._val
                if (health <= 0) {
                    if (health < -999) {
                        health = -999
                    }
                    isTelefragged = damageDef.dict.GetBool("telefrag")
                    lastDmgTime = gameLocal.time
                    Killed(inflictor, attacker, damage._val, dir, location)
                } else {
                    // force a blink
                    blink_time = 0

                    // let the anim script know we took damage
                    AI_PAIN.underscore(Pain(inflictor, attacker, damage._val, dir, location))
                    if (!SysCvar.g_testDeath.GetBool()) {
                        lastDmgTime = gameLocal.time
                    }
                }
            } else {
                // don't accumulate impulses
                if (af.IsLoaded()) {
                    // clear impacts
                    af.Rest()

                    // physics is turned off by calling af.Rest()
                    BecomeActive(TH_PHYSICS)
                }
            }
            lastDamageDef = damageDef.Index()
            lastDamageDir.set(damage_from)
            lastDamageLocation = location
        }

        // use exitEntityNum to specify a teleport with private camera view and delayed exit
        override fun Teleport(origin: idVec3, angles: idAngles, destination: idEntity?) {
            val org = idVec3()
            if (weapon.GetEntity() != null) {
                weapon.GetEntity()!!.LowerWeapon()
            }
            SetOrigin(origin.plus(idVec3(0.0f, 0.0f, CM_CLIP_EPSILON)))
            if (!gameLocal.isMultiplayer && GetFloorPos(16.0f, org)) {
                SetOrigin(org)
            }

            // clear the ik heights so model doesn't appear in the wrong place
            walkIK.EnableAll()
            GetPhysics().SetLinearVelocity(vec3_origin)
            SetViewAngles(angles)
            legsYaw = 0.0f
            idealLegsYaw = 0.0f
            oldViewYaw = viewAngles.yaw
            if (gameLocal.isMultiplayer) {
                playerView.Flash(colorWhite, 140)
            }
            UpdateVisuals()
            teleportEntity.oSet(destination)
            if (!gameLocal.isClient && !noclip) {
                if (gameLocal.isMultiplayer) {
                    // kill anything at the new position or mark for kill depending on immediate or delayed teleport
                    gameLocal.KillBox(this, destination != null)
                } else {
                    // kill anything at the new position
                    gameLocal.KillBox(this, true)
                }
            }
        }

        fun Kill(delayRespawn: Boolean, nodamage: Boolean) {
            if (spectating) {
                SpectateFreeFly(false)
            } else if (health > 0) {
                godmode = false
                if (nodamage) {
                    ServerSpectate(true)
                    forceRespawn = true
                } else {
                    Damage(this, this, vec3_origin, "damage_suicide", 1.0f, Model.INVALID_JOINT)
                    if (delayRespawn) {
                        forceRespawn = false
                        val delay = spawnArgs.GetFloat("respawn_delay")
                        minRespawnTime = (gameLocal.time + SEC2MS(delay))
                        maxRespawnTime = minRespawnTime + MAX_RESPAWN_TIME
                    }
                }
            }
        }

        override fun Killed(inflictor: idEntity?, attacker: idEntity?, damage: Int, dir: idVec3, location: Int) {
            val delay: Float
            assert(!gameLocal.isClient)

            // stop taking knockback once dead
            fl.noknockback = true
            if (health < -999) {
                health = -999
            }
            if (AI_DEAD.underscore()!!) {
                AI_PAIN.underscore(true)
                return
            }
            heartInfo.Init(0.0f, 0.0f, 0.0f, 0.0f + BASE_HEARTRATE)
            AdjustHeartRate(DEAD_HEARTRATE, 10.0f, 0.0f, true)
            if (!SysCvar.g_testDeath.GetBool()) {
                playerView.Fade(colorBlack, 12000)
            }
            AI_DEAD.underscore(true)
            SetAnimState(Anim.ANIMCHANNEL_LEGS, "Legs_Death", 4)
            SetAnimState(Anim.ANIMCHANNEL_TORSO, "Torso_Death", 4)
            SetWaitState("")
            animator.ClearAllJoints()
            if (StartRagdoll()) {
                SysCvar.pm_modelView.SetInteger(0)
                minRespawnTime = gameLocal.time + RAGDOLL_DEATH_TIME
                maxRespawnTime = minRespawnTime + MAX_RESPAWN_TIME
            } else {
                // don't allow respawn until the death anim is done
                // g_forcerespawn may force spawning at some later time
                delay = spawnArgs.GetFloat("respawn_delay")
                minRespawnTime = (gameLocal.time + SEC2MS(delay))
                maxRespawnTime = minRespawnTime + MAX_RESPAWN_TIME
            }
            physicsObj.SetMovementType(pmtype_t.PM_DEAD)
            StartSound("snd_death", gameSoundChannel_t.SND_CHANNEL_VOICE, 0, false)
            StopSound(TempDump.etoi(gameSoundChannel_t.SND_CHANNEL_BODY2), false)
            fl.takedamage = true // can still be gibbed

            // get rid of weapon
            weapon.GetEntity()!!.OwnerDied()

            // drop the weapon as an item
            DropWeapon(true)
            if (!SysCvar.g_testDeath.GetBool()) {
                LookAtKiller(inflictor!!, attacker!!)
            }
            if (gameLocal.isMultiplayer || SysCvar.g_testDeath.GetBool()) {
                var killer: idPlayer? = null
                // no gibbing in MP. Event_Gib will early out in MP
                if (attacker is idPlayer) {
                    killer = attacker
                    if (health < -20 || killer.PowerUpActive(BERSERK)) {
                        gibDeath = true
                        gibsDir.set(dir)
                        gibsLaunched = false
                    }
                }
                gameLocal.mpGame.PlayerDeath(this, killer, isTelefragged)
            } else {
                physicsObj.SetContents(Material.CONTENTS_CORPSE or Material.CONTENTS_MONSTERCLIP)
            }
            ClearPowerUps()
            UpdateVisuals()
            isChatting = false
        }

        fun StartFxOnBone(fx: String, bone: String) {
            val offset = idVec3()
            val axis = idMat3()
            val   /*jointHandle_t*/jointHandle = GetAnimator().GetJointHandle(bone)
            if (jointHandle == Model.INVALID_JOINT) {
                gameLocal.Printf("Cannot find bone %s\n", bone)
                return
            }
            if (GetAnimator().GetJointTransform(jointHandle, gameLocal.time, offset, axis)) {
                offset.set(GetPhysics().GetOrigin().plus(offset.times(GetPhysics().GetAxis())))
                axis.set(axis.times(GetPhysics().GetAxis()))
            }
            idEntityFx.StartFx(fx, offset, axis, this, true)
        }

        /*
         ==================
         idPlayer::GetRenderView

         Returns the renderView that was calculated for this tic
         ==================
         */
        override fun GetRenderView(): renderView_s? {
            return renderView
        }

        /*
         ==================
         idPlayer::CalculateRenderView

         create the renderView for the current tic
         ==================
         */
        fun CalculateRenderView() {    // called every tic by player code
            var i: Int
            val range: Float
            if (null == renderView) {
                renderView = renderView_s()
            }
            //	memset( renderView, 0, sizeof( *renderView ) );

            // copy global shader parms
            i = 0
            val renderView = renderView!!
            while (i < RenderWorld.MAX_GLOBAL_SHADER_PARMS) {
                renderView.shaderParms[i] = gameLocal.globalShaderParms[i]
                i++
            }
            renderView.globalMaterial = gameLocal.GetGlobalMaterial()
            renderView.time = gameLocal.time

            // calculate size of 3D view
            renderView.x = 0
            renderView.y = 0
            renderView.width = RenderSystem.SCREEN_WIDTH
            renderView.height = RenderSystem.SCREEN_HEIGHT
            renderView.viewID = 0

            // check if we should be drawing from a camera's POV
            if (!noclip && (gameLocal.GetCamera() != null || privateCameraView != null)) {
                // get origin, axis, and fov
                if (privateCameraView != null) {
                    privateCameraView!!.GetViewParms(renderView)
                } else {
                    gameLocal.GetCamera()!!.GetViewParms(renderView)
                }
            } else {
                if (SysCvar.g_stopTime.GetBool()) {
                    renderView.vieworg.set(firstPersonViewOrigin)
                    renderView.viewaxis.set(firstPersonViewAxis)
                    if (!SysCvar.pm_thirdPerson.GetBool()) {
                        // set the viewID to the clientNum + 1, so we can suppress the right player bodies and
                        // allow the right player view weapons
                        renderView.viewID = entityNumber + 1
                    }
                } else if (SysCvar.pm_thirdPerson.GetBool()) {
                    OffsetThirdPersonView(
                        SysCvar.pm_thirdPersonAngle.GetFloat(),
                        SysCvar.pm_thirdPersonRange.GetFloat(),
                        SysCvar.pm_thirdPersonHeight.GetFloat(),
                        SysCvar.pm_thirdPersonClip.GetBool()
                    )
                } else if (SysCvar.pm_thirdPersonDeath.GetBool()) {
                    range =
                        if (gameLocal.time < minRespawnTime) ((gameLocal.time + RAGDOLL_DEATH_TIME - minRespawnTime) * (120.0f / RAGDOLL_DEATH_TIME)) else 120.0f
                    OffsetThirdPersonView(0.0f, 20 + range, 0.0f, false)
                } else {
                    renderView.vieworg.set(firstPersonViewOrigin)
                    renderView.viewaxis.set(firstPersonViewAxis)

                    // set the viewID to the clientNum + 1, so we can suppress the right player bodies and
                    // allow the right player view weapons
                    renderView.viewID = entityNumber + 1
                }

                // field of view
                run {
                    val fov_x = CFloat(renderView.fov_x)
                    val fov_y = CFloat(renderView.fov_y)
                    gameLocal.CalcFov(CalcFov(true), fov_x, fov_y)
                    renderView.fov_x = fov_x._val
                    renderView.fov_y = fov_y._val
                }
            }
            if (renderView.fov_y == 0.0f) {
                Common.common.Error("renderView.fov_y == 0")
            }
            if (SysCvar.g_showviewpos.GetBool()) {
                gameLocal.Printf(
                    "%s : %s\n", renderView.vieworg.ToString(), renderView.viewaxis.ToAngles().ToString()
                )
            }
        }

        /*
        ===============
        idPlayer::CalculateFirstPersonView
        ===============
        */
        fun CalculateFirstPersonView() {
            if (SysCvar.pm_modelView.GetInteger() == 1 || SysCvar.pm_modelView.GetInteger() == 2 && health <= 0) {
                //	Displays the view from the point of view of the "camera" joint in the player model
                val axis = idMat3()
                val origin = idVec3()
                val ang: idAngles = idAngles()
                ang.set(viewBobAngles.plus(playerView.AngleOffset()))
                ang.yaw += viewAxis[0].ToYaw()
                val joint = animator.GetJointHandle("camera")
                animator.GetJointTransform(joint, gameLocal.time, origin, axis)
                firstPersonViewOrigin.set(
                    origin.plus(modelOffset).times(viewAxis.times(physicsObj.GetGravityAxis()))
                        .plus(physicsObj.GetOrigin()).plus(viewBob)
                )
                firstPersonViewAxis.set(axis.times(ang.ToMat3()).times(physicsObj.GetGravityAxis()))
            } else {
                // offset for local bobbing and kicks
                GetViewPos(firstPersonViewOrigin, firstPersonViewAxis)
                if (false) {
                    // shakefrom sound stuff only happens in first person
                    firstPersonViewAxis.set(firstPersonViewAxis.times(playerView.ShakeAxis()))
                }
            }
        }

        fun DrawHUD(_hud: idUserInterface) {
            if (weapon.GetEntity() == null || influenceActive != INFLUENCE_NONE || privateCameraView != null || gameLocal.GetCamera() != null ||
                _hud == null || !SysCvar.g_showHud.GetBool()
            ) {
                return
            }
            UpdateHudStats(_hud)
            _hud.SetStateString("weapicon", weapon.GetEntity()!!.Icon())

            // FIXME: this is temp to allow the sound meter to show up in the hud
            // it should be commented out before shipping but the code can remain
            // for mod developers to enable for the same functionality
            _hud.SetStateInt("s_debug", CVarSystem.cvarSystem.GetCVarInteger("s_showLevelMeter"))
            weapon.GetEntity()!!.UpdateGUI()
            _hud.Redraw(gameLocal.realClientTime)

            // weapon targeting crosshair
            if (!GuiActive()) {
                if (cursor != null && weapon.GetEntity()!!.ShowCrosshair()) {
                    cursor!!.Redraw(gameLocal.realClientTime)
                }
            }
        }

        /*
         ==================
         WeaponFireFeedback

         Called when a weapon fires, generates head twitches, etc
         ==================
         */
        fun WeaponFireFeedback(weaponDef: idDict) {
            // force a blink
            blink_time = 0

            // play the fire animation
            AI_WEAPON_FIRED.underscore(true)

            // update view feedback
            playerView.WeaponFireFeedback(weaponDef)
        }

        /*
         ====================
         idPlayer::DefaultFov

         Returns the base FOV
         ====================
         */
        fun DefaultFov(): Float {
            val fov: Float
            fov = SysCvar.g_fov.GetFloat()
            if (gameLocal.isMultiplayer) {
                if (fov < 90) {
                    return 90.0f
                } else if (fov > 110) {
                    return 110.0f
                }
            }
            return fov
        }

        /*
         ====================
         idPlayer::CalcFov

         Fixed fov at intermissions, otherwise account for fov variable and zooms.
         ====================
         */
        fun CalcFov(honorZoom: Boolean): Float {
            var fov: Float
            if (fxFov) {
                return (DefaultFov() + 10 + cos((gameLocal.time + 2000) * 0.01f) * 10)
            }
            if (influenceFov != 0.0f) {
                return influenceFov
            }
            if (zoomFov.IsDone(gameLocal.time.toFloat())) {
                fov =
                    if (honorZoom && (usercmd.buttons.toInt() and UsercmdGen.BUTTON_ZOOM) != 0 && weapon.GetEntity() != null) weapon.GetEntity()!!
                        .GetZoomFov().toFloat() else DefaultFov()
            } else {
                fov = zoomFov.GetCurrentValue(gameLocal.time.toFloat())
            }

            // bound normal viewsize
            if (fov < 1) {
                fov = 1.0f
            } else if (fov > 179) {
                fov = 179.0f
            }
            return fov
        }

        /*
         ==============
         idPlayer::CalculateViewWeaponPos

         Calculate the bobbing position of the view weapon
         ==============
         */
        fun CalculateViewWeaponPos(origin: idVec3, axis: idMat3) {
            var scale: Float
            val fracsin: Float
            val angles = idAngles()
            val delta: Int

            // CalculateRenderView must have been called first
            val viewOrigin = firstPersonViewOrigin
            val viewAxis = firstPersonViewAxis

            // these cvars are just for hand tweaking before moving a value to the weapon def
            val gunpos = idVec3(SysCvar.g_gun_x.GetFloat(), SysCvar.g_gun_y.GetFloat(), SysCvar.g_gun_z.GetFloat())

            // as the player changes direction, the gun will take a small lag
            val gunOfs = idVec3(GunAcceleratingOffset())
            origin.set(viewOrigin.plus(gunpos.plus(gunOfs).times(viewAxis)))

            // on odd legs, invert some angles
            scale = if ((bobCycle and 128) != 0) {
                -xyspeed
            } else {
                xyspeed
            }

            // gun angles from bobbing
            angles.roll = scale * bobfracsin * 0.005f
            angles.yaw = scale * bobfracsin * 0.01f
            angles.pitch = xyspeed * bobfracsin * 0.005f

            // gun angles from turning
            if (gameLocal.isMultiplayer) {
                val offset = GunTurningOffset()
                offset.timesAssign(SysCvar.g_mpWeaponAngleScale.GetFloat())
                angles.plusAssign(offset)
            } else {
                angles.plusAssign(GunTurningOffset())
            }
            val gravity = physicsObj.GetGravityNormal()

            // drop the weapon when landing after a jump / fall
            delta = gameLocal.time - landTime
            if (delta < LAND_DEFLECT_TIME) {
                origin.minusAssign(gravity.times(landChange * 0.25f * delta / LAND_DEFLECT_TIME))
            } else if (delta < LAND_DEFLECT_TIME + LAND_RETURN_TIME) {
                origin.minusAssign(gravity.times(landChange * 0.25f * (LAND_DEFLECT_TIME + LAND_RETURN_TIME - delta) / LAND_RETURN_TIME))
            }

            // speed sensitive idle drift
            scale = xyspeed + 40
            fracsin = (scale * sin(MS2SEC(gameLocal.time.toFloat())) * 0.01f)
            angles.roll += fracsin
            angles.yaw += fracsin
            angles.pitch += fracsin
            axis.set(angles.ToMat3().times(viewAxis))
        }

        override fun GetEyePosition(): idVec3 {
            val org = idVec3()

            // use the smoothed origin if spectating another player in multiplayer
            if (gameLocal.isClient && entityNumber != gameLocal.localClientNum) {
                org.set(smoothedOrigin)
            } else {
                org.set(GetPhysics().GetOrigin())
            }
            return org + (GetPhysics().GetGravityNormal() * -eyeOffset.z)
        }

        override fun GetViewPos(origin: idVec3, axis: idMat3) {
            val angles = idAngles()

            // if dead, fix the angle and don't add any kick
            if (health <= 0) {
                angles.yaw = viewAngles.yaw
                angles.roll = 40.0f
                angles.pitch = -15.0f
                axis.set(angles.ToMat3()) //TODO:null check
                origin.set(GetEyePosition())
            } else {
                origin.set(GetEyePosition() + viewBob)
                angles.set(viewAngles + viewBobAngles + playerView.AngleOffset())
                axis.set(angles.ToMat3() * physicsObj.GetGravityAxis())

                // adjust the origin based on the camera nodal distance (eye distance from neck)
                origin.plusAssign(physicsObj.GetGravityNormal() * SysCvar.g_viewNodalZ.GetFloat())
                origin.plusAssign(
                    axis[0] * SysCvar.g_viewNodalX.GetFloat() + axis[2] * SysCvar.g_viewNodalZ.GetFloat()
                )
            }
        }

        fun OffsetThirdPersonView(angle: Float, range: Float, height: Float, clip: Boolean) {
            val view = idVec3()
            //            idVec3 focusAngles;
            val trace = trace_s()
            val focusPoint = idVec3()
            var focusDist: Float
            val forwardScale = CFloat()
            val sideScale = CFloat()
            val origin = idVec3()
            val angles: idAngles
            val axis = idMat3()
            val bounds: idBounds
            angles = idAngles(viewAngles)
            GetViewPos(origin, axis)
            if (angle != 0.0f) {
                angles.pitch = 0.0f
            }
            if (angles.pitch > 45.0f) {
                angles.pitch = 45.0f // don't go too far overhead
            }
            focusPoint.set(origin.plus(angles.ToForward().times(THIRD_PERSON_FOCUS_DISTANCE)))
            focusPoint.z += height
            view.set(origin)
            view.z += 8 + height
            angles.pitch *= 0.5f
            renderView!!.viewaxis.set(angles.ToMat3().times(physicsObj.GetGravityAxis()))
            idMath.SinCos(DEG2RAD(angle), sideScale, forwardScale)
            view.minusAssign(renderView!!.viewaxis[0].times(range * forwardScale._val))
            view.plusAssign(renderView!!.viewaxis[1].times(range * sideScale._val))
            if (clip) {
                // trace a ray from the origin to the viewpoint to make sure the view isn't
                // in a solid block.  Use an 8 by 8 block to prevent the view from near clipping anything
                bounds = idBounds(idVec3(-4, -4, -4), idVec3(4, 4, 4))
                gameLocal.clip.TraceBounds(trace, origin, view, bounds, Game_local.MASK_SOLID, this)
                if (trace.fraction != 1.0f) {
                    view.set(trace.endpos)
                    view.z += (1.0f - trace.fraction) * 32.0f

                    // try another trace to this position, because a tunnel may have the ceiling
                    // close enough that this is poking out
                    gameLocal.clip.TraceBounds(trace, origin, view, bounds, Game_local.MASK_SOLID, this)
                    view.set(trace.endpos)
                }
            }

            // select pitch to look at focus point from vieword
            focusPoint.minusAssign(view)
            focusDist = idMath.Sqrt(focusPoint[0] * focusPoint[0] + focusPoint[1] * focusPoint[1])
            if (focusDist < 1.0f) {
                focusDist = 1.0f // should never happen
            }
            angles.pitch = -RAD2DEG(atan2(focusPoint.z, focusDist))
            angles.yaw -= angle
            renderView!!.vieworg.set(view)
            renderView!!.viewaxis.set(angles.ToMat3().timesAssign(physicsObj.GetGravityAxis()))
            renderView!!.viewID = 0
        }

        // D3XP
        fun CanGive(statname: String, value: String): Boolean {
            if (AI_DEAD.underscore()!!) {
                return false
            }
            if (idStr.Icmp(statname, "health") == 0) {
                return health < inventory.maxHealth
            } else if (idStr.Icmp(statname, "stamina") == 0) {
                return stamina < 100f
            } else if (idStr.Icmp(statname, "heartRate") == 0) {
                return true
            } else if (idStr.Icmp(statname, "air") == 0) {
                return airTics < SysCvar.pm_airTics.GetInteger()
            }
            return inventory.CanGive(this, spawnArgs, statname, value)
        }

        fun Give(statname: String, value: String): Boolean {
            val amount: Int
            if (AI_DEAD.underscore()!!) {
                return false
            }
            if (0 == idStr.Icmp(statname, "health")) {
                if (health >= inventory.maxHealth) {
                    return false
                }
                amount = value.toInt()
                if (amount != 0) {
                    health += amount
                    if (health > inventory.maxHealth) {
                        health = inventory.maxHealth
                    }
                    if (hud != null) {
                        hud!!.HandleNamedEvent("healthPulse")
                    }
                }
            } else if (0 == idStr.Icmp(statname, "stamina")) {
                if (stamina >= 100) {
                    return false
                }
                stamina += value.toFloat()
                if (stamina > 100) {
                    stamina = 100.0f
                }
            } else if (0 == idStr.Icmp(statname, "heartRate")) {
                heartRate += value.toInt()
                if (heartRate > MAX_HEARTRATE) {
                    heartRate = MAX_HEARTRATE
                }
            } else if (0 == idStr.Icmp(statname, "air")) {
                if (airTics >= SysCvar.pm_airTics.GetInteger()) {
                    return false
                }
                airTics += (value.toInt() / 100.0f * SysCvar.pm_airTics.GetInteger()).toInt()
                if (airTics > SysCvar.pm_airTics.GetInteger()) {
                    airTics = SysCvar.pm_airTics.GetInteger()
                }
            } else {
                val idealWeapon = CInt(idealWeapon)
                val result = inventory.Give(this, spawnArgs, statname, value, idealWeapon, true)
                this.idealWeapon = idealWeapon._val
                return result
            }
            return true
        }

        fun Give(statname: idStr, value: idStr): Boolean {
            return this.Give(statname.toString(), value.toString())
        }

        /*
         ===============
         idPlayer::GiveItem

         Returns false if the item shouldn't be picked up
         ===============
         */
        fun GiveItem(item: idItem): Boolean {
            var i: Int
            var arg: idKeyValue?
            val attr = idDict()
            var gave: Boolean
            val numPickup: Int
            if (gameLocal.isMultiplayer && spectating) {
                return false
            }
            item.GetAttributes(attr)
            gave = false
            numPickup = inventory.pickupItemNames.Num()
            i = 0
            while (i < attr.GetNumKeyVals()) {
                arg = attr.GetKeyVal(i)!!
                if (Give(arg.GetKey(), arg.GetValue())) {
                    gave = true
                }
                i++
            }
            arg = item.spawnArgs.MatchPrefix("inv_weapon", null)
            if (arg != null && hud != null) {
                // We need to update the weapon hud manually, but not
                // the armor/ammo/health because they are updated every
                // frame no matter what
                UpdateHudWeapon(false)
                hud!!.HandleNamedEvent("weaponPulse")
            }

            // display the pickup feedback on the hud
            if (gave && numPickup == inventory.pickupItemNames.Num()) {
                inventory.AddPickupName(item.spawnArgs.GetString("inv_name"), item.spawnArgs.GetString("inv_icon"))
            }
            return gave
        }

        fun GiveItem(itemName: String) {
            val args = idDict()
            args.Set("classname", itemName)
            args.Set("owner", name)
            gameLocal.SpawnEntityDef(args)
            if (hud != null) {
                hud!!.HandleNamedEvent("itemPickup")
            }
        }

        /*
         ===============
         idPlayer::GiveHealthPool

         adds health to the player health pool
         ===============
         */
        fun GiveHealthPool(amt: Float) {
            if (AI_DEAD.underscore()!!) {
                return
            }
            if (health > 0) {
                healthPool += amt
                if (healthPool > inventory.maxHealth - health) {
                    healthPool = (inventory.maxHealth - health).toFloat()
                }
                nextHealthPulse = gameLocal.time
            }
        }

        fun GiveInventoryItem(item: idDict): Boolean {
            if (gameLocal.isMultiplayer && spectating) {
                return false
            }
            inventory.items.Append(idDict(item))
            val info = idItemInfo()
            val itemName = item.GetString("inv_name")
            if (idStr.Cmpn(itemName, Common.STRTABLE_ID, Common.STRTABLE_ID_LENGTH) == 0) {
                info.name.set(Common.common.GetLanguageDict().GetString(itemName))
            } else {
                info.name.set(itemName)
            }
            info.icon.set(item.GetString("inv_icon"))
            inventory.pickupItemNames.Append(info)
            if (hud != null) {
                hud!!.SetStateString("itemicon", info.icon.toString())
                hud!!.HandleNamedEvent("invPickup")
            }
            return true
        }

        fun RemoveInventoryItem(item: idDict) {
            inventory.items.Remove(item)
            //	delete item;
        }

        fun GiveInventoryItem(name: String): Boolean {
            val args = idDict()
            args.Set("classname", name)
            args.Set("owner", this.name)
            gameLocal.SpawnEntityDef(args)
            return true
        }

        fun RemoveInventoryItem(name: String) {
            val item = FindInventoryItem(name)
            if (item != null) {
                RemoveInventoryItem(item)
            }
        }

        fun FindInventoryItem(name: String): idDict? {
            for (i in 0 until inventory.items.Num()) {
                val iname = inventory.items[i].GetString("inv_name")
                if (iname != null && iname.isNotEmpty()) {
                    if (idStr.Icmp(name, iname) == 0) {
                        return inventory.items[i]
                    }
                }
            }
            return null
        }

        fun FindInventoryItem(name: idStr): idDict? {
            return FindInventoryItem(name.toString())
        }

        fun GivePDA(pdaName: idStr?, item: idDict?) {
            var pdaName = pdaName
            if (gameLocal.isMultiplayer && spectating) {
                return
            }
            if (item != null) {
                inventory.pdaSecurity.addUnique(item.GetString("inv_name"))
            }
            if (pdaName == null || pdaName.IsEmpty()) {
                pdaName = idStr("personal")
            }

            val pda = DeclManager.declManager.FindType(declType_t.DECL_PDA, pdaName) as idDeclPDA?
            inventory.pdas.addUnique(pdaName.toString())

            // Copy any videos over
            for (i in 0 until pda!!.GetNumVideos()) {
                val video = pda.GetVideoByIndex(i)
                if (video != null) {
                    inventory.videos.addUnique(video.GetName())
                }
            }

            // This is kind of a hack, but it works nicely
            // We don't want to display the 'you got a new pda' message during a map load
            if (gameLocal.GetFrameNum() > 10) {
                if (pda != null && hud != null) {
                    pdaName.set(pda.GetPdaName())
                    pdaName.RemoveColors()
                    hud!!.SetStateString("pda", "1")
                    hud!!.SetStateString("pda_text", pdaName.toString())
                    val sec = pda.GetSecurity()
                    hud!!.SetStateString(
                        "pda_security", if (sec != null && !sec.isEmpty()) "1" else "0"
                    ) //TODO:!= null and !usEmpty, check that this combination isn't the wrong way around anywhere. null== instead of !=null
                    hud!!.HandleNamedEvent("pdaPickup")
                }
                if (inventory.pdas.size() == 1) {
                    GetPDA()!!.RemoveAddedEmailsAndVideos()
                    if (!objectiveSystemOpen) {
                        TogglePDA()
                    }
                    objectiveSystem!!.HandleNamedEvent("showPDATip")
                    //ShowTip( spawnArgs.GetString( "text_infoTitle" ), spawnArgs.GetString( "text_firstPDA" ), true );
                }
                if (inventory.pdas.size() > 1 && pda.GetNumVideos() > 0 && hud != null) {
                    hud!!.HandleNamedEvent("videoPickup")
                }
            }
        }

        fun GiveVideo(videoName: String?, item: idDict?) {
            if (videoName == null || videoName.isEmpty()) {
                return
            }
            inventory.videos.addUnique(videoName)
            if (item != null) {
                val info = idItemInfo()
                info.name.set(item.GetString("inv_name"))
                info.icon.set(item.GetString("inv_icon"))
                inventory.pickupItemNames.Append(info)
            }
            if (hud != null) {
                hud!!.HandleNamedEvent("videoPickup")
            }
        }

        fun GiveEmail(emailName: String?) {
            if (emailName == null || emailName.isEmpty()) {
                return
            }
            inventory.emails.addUnique(emailName)
            GetPDA()!!.AddEmail(emailName)
            if (hud != null) {
                hud!!.HandleNamedEvent("emailPickup")
            }
        }

        fun GiveSecurity(security: String) {
            GetPDA()!!.SetSecurity(security)
            if (hud != null) {
                hud!!.SetStateString("pda_security", "1")
                hud!!.HandleNamedEvent("securityPickup")
            }
        }

        fun GiveObjective(title: String, text: String, screenshot: String) {
            val info = idObjectiveInfo()
            info.title = idStr(title)
            info.text = idStr(text)
            info.screenshot = idStr(screenshot)
            inventory.objectiveNames.Append(info)
            ShowObjective("newObjective")
            if (hud != null) {
                hud!!.HandleNamedEvent("newObjective")
            }
        }

        fun CompleteObjective(title: String) {
            val c = inventory.objectiveNames.Num()
            for (i in 0 until c) {
                if (idStr.Icmp(inventory.objectiveNames[i].title.toString(), title) == 0) {
                    inventory.objectiveNames.RemoveIndex(i)
                    break
                }
            }
            ShowObjective("newObjectiveComplete")
            if (hud != null) {
                hud!!.HandleNamedEvent("newObjectiveComplete")
            }
        }

        fun GivePowerUp(powerup: Int, time: Int): Boolean {
            val sound = arrayOfNulls<String>(1)
            val skin = arrayOfNulls<String>(1)
            if (powerup >= 0 && powerup < MAX_POWERUPS) {
                if (gameLocal.isServer) {
                    val msg = idBitMsg()
                    val msgBuf = ByteBuffer.allocate(Game_local.MAX_EVENT_PARAM_SIZE)
                    msg.Init(msgBuf, Game_local.MAX_EVENT_PARAM_SIZE)
                    msg.WriteShort(powerup.toShort())
                    msg.WriteBits(1, 1)
                    ServerSendEvent(EVENT_POWERUP, msg, false, -1)
                }
                if (powerup != MEGAHEALTH) {
                    inventory.GivePowerUp(this, powerup, time)
                }
                val def: idDeclEntityDef?
                when (powerup) {
                    BERSERK -> {
                        if (spawnArgs.GetString("snd_berserk_third", "", sound)) {
                            StartSoundShader(
                                DeclManager.declManager.FindSound(sound[0]!!),
                                gameSoundChannel_t.SND_CHANNEL_DEMONIC.ordinal,
                                0,
                                false
                            )
                        }
                        if (baseSkinName.Length() != 0) {
                            powerUpSkin = DeclManager.declManager.FindSkin(baseSkinName.toString() + "_berserk")
                        }
                        if (!gameLocal.isClient) {
                            idealWeapon = 0
                        }
                    }

                    INVISIBILITY -> {
                        spawnArgs.GetString("skin_invisibility", "", skin)
                        powerUpSkin = DeclManager.declManager.FindSkin(skin[0]!!)
                        // remove any decals from the model
                        if (modelDefHandle != -1) {
                            Game_local.gameRenderWorld!!.RemoveDecals(modelDefHandle)
                        }
                        if (weapon.GetEntity() != null) {
                            weapon.GetEntity()!!.UpdateSkin()
                        }
                        if (spawnArgs.GetString("snd_invisibility", "", sound)) {
                            StartSoundShader(
                                DeclManager.declManager.FindSound(sound[0]!!),
                                gameSoundChannel_t.SND_CHANNEL_ANY.ordinal,
                                0,
                                false
                            )
                        }
                    }

                    ADRENALINE -> {
                        stamina = 100.0f
                    }

                    MEGAHEALTH -> {
                        if (spawnArgs.GetString("snd_megahealth", "", sound)) {
                            StartSoundShader(
                                DeclManager.declManager.FindSound(sound[0]!!),
                                gameSoundChannel_t.SND_CHANNEL_ANY.ordinal,
                                0,
                                false
                            )
                        }
                        def = gameLocal.FindEntityDef("powerup_megahealth", false)
                        if (def != null) {
                            health = def.dict.GetInt("inv_health")
                        }
                    }
                }
                if (hud != null) {
                    hud!!.HandleNamedEvent("itemPickup")
                }
                return true
            } else {
                gameLocal.Warning("Player given power up %d\n which is out of range", powerup)
            }
            return false
        }

        fun ClearPowerUps() {
            var i: Int
            i = 0
            while (i < MAX_POWERUPS) {
                if (PowerUpActive(i)) {
                    ClearPowerup(i)
                }
                i++
            }
            inventory.ClearPowerUps()
        }

        fun PowerUpActive(powerup: Int): Boolean {
            return (inventory.powerups and (1 shl powerup)) != 0
        }

        fun PlayHelltimeStopSound() {
            val sound = spawnArgs.GetString("snd_helltime_stop", "")
            if (!sound.isNullOrEmpty()) {
                PostEventMS(EV_StartSoundShader, 0, sound, gameSoundChannel_t.SND_CHANNEL_ANY.ordinal)
            }
        }

        fun PowerUpModifier(type: Int): Float {
            var mod = 1.0f
            if (PowerUpActive(BERSERK)) {
                when (type) {
                    SPEED -> {
                        mod *= 1.7f
                    }

                    PROJECTILE_DAMAGE -> {
                        mod *= 2.0f
                    }

                    MELEE_DAMAGE -> {
                        mod *= 30.0f
                    }

                    MELEE_DISTANCE -> {
                        mod *= 2.0f
                    }
                }
            }
            if (gameLocal.isMultiplayer && !gameLocal.isClient) {
                if (PowerUpActive(MEGAHEALTH)) {
                    if (healthPool <= 0) {
                        GiveHealthPool(100.0f)
                    }
                } else {
                    healthPool = 0.0f
                }
            }
            return mod
        }

        fun SlotForWeapon(weaponName: String): Int {
            var i: Int
            i = 0
            while (i < MAX_WEAPONS) {
                val weap = spawnArgs.GetString(Str.va("def_weapon%d", i))
                if (0 == idStr.Cmp(weap, weaponName)) {
                    return i
                }
                i++
            }

            // not found
            return -1
        }

        fun Reload() {
            if (gameLocal.isClient) {
                return
            }
            if (spectating || gameLocal.inCinematic || influenceActive != 0) {
                return
            }
            if (weapon.GetEntity() != null && weapon.GetEntity()!!.IsLinked()) {
                weapon.GetEntity()!!.Reload()
            }
        }

        fun NextWeapon() {
            var weap: String
            var w: Int
            if (!weaponEnabled || spectating || hiddenWeapon || gameLocal.inCinematic || gameLocal.world!!.spawnArgs.GetBool(
                    "no_Weapons"
                ) || health < 0
            ) {
                return
            }
            if (gameLocal.isClient) {
                return
            }

            // check if we have any weapons
            if (0 == inventory.weapons) {
                return
            }
            w = idealWeapon
            while (true) {
                w++
                if (w >= MAX_WEAPONS) {
                    w = 0
                }
                weap = spawnArgs.GetString(Str.va("def_weapon%d", w))
                if (!spawnArgs.GetBool(Str.va("weapon%d_cycle", w))) {
                    continue
                }
                if (weap.isEmpty()) {
                    continue
                }
                if ((inventory.weapons and (1 shl w)) == 0) {
                    continue
                }
                if (inventory.HasAmmo(weap) != 0) {
                    break
                }
            }
            if (w != currentWeapon && w != idealWeapon) {
                idealWeapon = w
                weaponSwitchTime = gameLocal.time + WEAPON_SWITCH_DELAY
                UpdateHudWeapon()
            }
        }

        fun NextBestWeapon() {
            var weap: String
            var w = MAX_WEAPONS
            if (gameLocal.isClient || !weaponEnabled) {
                return
            }
            while (w > 0) {
                w--
                weap = spawnArgs.GetString(Str.va("def_weapon%d", w))
                if (weap.isEmpty() || inventory.weapons and (1 shl w) == 0 || 0 == inventory.HasAmmo(weap)) {
                    continue
                }
                if (!spawnArgs.GetBool(Str.va("weapon%d_best", w))) {
                    continue
                }
                break
            }
            idealWeapon = w
            weaponSwitchTime = gameLocal.time + WEAPON_SWITCH_DELAY
            UpdateHudWeapon()
        }

        fun PrevWeapon() {
            var weap: String
            var w: Int
            if (!weaponEnabled || spectating || hiddenWeapon || gameLocal.inCinematic || gameLocal.world!!.spawnArgs.GetBool(
                    "no_Weapons"
                ) || health < 0
            ) {
                return
            }
            if (gameLocal.isClient) {
                return
            }

            // check if we have any weapons
            if (0 == inventory.weapons) {
                return
            }
            w = idealWeapon
            while (true) {
                w--
                if (w < 0) {
                    w = MAX_WEAPONS - 1
                }
                weap = spawnArgs.GetString(Str.va("def_weapon%d", w))
                if (!spawnArgs.GetBool(Str.va("weapon%d_cycle", w))) {
                    continue
                }
                if (weap.isEmpty()) {
                    continue
                }
                if ((inventory.weapons and (1 shl w)) == 0) {
                    continue
                }
                if (inventory.HasAmmo(weap) != 0) {
                    break
                }
            }
            if (w != currentWeapon && w != idealWeapon) {
                idealWeapon = w
                weaponSwitchTime = gameLocal.time + WEAPON_SWITCH_DELAY
                UpdateHudWeapon()
            }
        }

        fun SelectWeapon(num: Int, force: Boolean) {
            var num = num
            var weap: String
            if (!weaponEnabled || spectating || gameLocal.inCinematic || health < 0) {
                return
            }
            if (num < 0 || num >= MAX_WEAPONS) {
                return
            }
            if (gameLocal.isClient) {
                return
            }
            if (num != weapon_pda && gameLocal.world!!.spawnArgs.GetBool("no_Weapons")) {
                num = weapon_fists
                hiddenWeapon = hiddenWeapon xor true //1;
                if (hiddenWeapon && weapon.GetEntity() != null) {
                    weapon.GetEntity()!!.LowerWeapon()
                } else {
                    weapon.GetEntity()!!.RaiseWeapon()
                }
            }
            weap = spawnArgs.GetString(Str.va("def_weapon%d", num))
            if (weap.isEmpty()) {
                gameLocal.Printf("Invalid weapon\n")
                return
            }
            if (force || (inventory.weapons and (1 shl num)) != 0) {
                if (0 == inventory.HasAmmo(weap) && !spawnArgs.GetBool(Str.va("weapon%d_allowempty", num))) {
                    return
                }
                if (previousWeapon >= 0 && idealWeapon == num && spawnArgs.GetBool(Str.va("weapon%d_toggle", num))) {
                    weap = spawnArgs.GetString(Str.va("def_weapon%d", previousWeapon))
                    if (0 == inventory.HasAmmo(weap) && !spawnArgs.GetBool(
                            Str.va(
                                "weapon%d_allowempty", previousWeapon
                            )
                        )
                    ) {
                        return
                    }
                    idealWeapon = previousWeapon
                } else if (weapon_pda >= 0 && num == weapon_pda && inventory.pdas.size() == 0) {
                    ShowTip(spawnArgs.GetString("text_infoTitle"), spawnArgs.GetString("text_noPDA"), true)
                    return
                } else {
                    idealWeapon = num
                }
                UpdateHudWeapon()
            }
        }

        fun DropWeapon(died: Boolean) {
            val forward = idVec3()
            val up = idVec3()
            val inclip: Int
            val ammoavailable: Int
            assert(!gameLocal.isClient)
            if (spectating || weaponGone || weapon.GetEntity() == null) {
                return
            }
            if (!died && !weapon.GetEntity()!!.IsReady() || weapon.GetEntity()!!.IsReloading()) {
                return
            }
            // ammoavailable is how many shots we can fire
            // inclip is which amount is in clip right now
            ammoavailable = weapon.GetEntity()!!.AmmoAvailable()
            inclip = weapon.GetEntity()!!.AmmoInClip()

            // don't drop a grenade if we have none left
            if (
                idStr.Icmp(
                    idWeapon.GetAmmoNameForNum(
                        weapon.GetEntity()!!.GetAmmoType()
                    )!!, "ammo_grenades"
                ) == 0 && ammoavailable - inclip <= 0
            ) {
                return
            }

            // expect an ammo setup that makes sense before doing any dropping
            // ammoavailable is -1 for infinite ammo, and weapons like chainsaw
            // a bad ammo config usually indicates a bad weapon state, so we should not drop
            // used to be an assertion check, but it still happens in edge cases
            if (ammoavailable != -1 && ammoavailable - inclip < 0) {
                Common.common.DPrintf("idPlayer::DropWeapon: bad ammo setup\n")
                return
            }
            val item: idEntity?
            item = if (died) {
                // ain't gonna throw you no weapon if I'm dead
                weapon.GetEntity()!!.DropItem(vec3_origin, 0, WEAPON_DROP_TIME, died)
            } else {
                viewAngles.ToVectors(forward, null, up)
                weapon.GetEntity()!!.DropItem(forward.times(250.0f).plus(up.times(150.0f)), 500, WEAPON_DROP_TIME, died)
            }
            if (null == item) {
                return
            }
            // set the appropriate ammo in the dropped object
            val keyval = item.spawnArgs.MatchPrefix("inv_ammo_")
            if (keyval != null) {
                item.spawnArgs.SetInt(keyval.GetKey().toString(), ammoavailable)
                val inclipKey = keyval.GetKey()
                inclipKey.Insert("inclip_", 4)
                item.spawnArgs.SetInt(inclipKey.toString(), inclip)
            }
            if (!died) {
                // remove from our local inventory completely
                run {
                    val inv_weapon = arrayOf(item.spawnArgs.GetString("inv_weapon"))
                    inventory.Drop(spawnArgs, inv_weapon, -1)
                    item.spawnArgs.Set("inv_weapon", inv_weapon[0])
                }
                weapon.GetEntity()!!.ResetAmmoClip()
                NextWeapon()
                weapon.GetEntity()!!.WeaponStolen()
                weaponGone = true
            }
        }

        /*
         =================
         idPlayer::StealWeapon
         steal the target player's current weapon
         =================
         */
        fun StealWeapon(player: idPlayer) {
            assert(!gameLocal.isClient)

            // make sure there's something to steal
            val player_weapon = player.weapon.GetEntity()
            if (null == player_weapon || !player_weapon.CanDrop() || weaponGone) {
                return
            }
            // steal - we need to effectively force the other player to abandon his weapon
            val newweap = player.currentWeapon
            if (newweap == -1) {
                return
            }
            // might be just dropped - check inventory
            if (0 == player.inventory.weapons and (1 shl newweap)) {
                return
            }
            val weapon_classname = spawnArgs.GetString(Str.va("def_weapon%d", newweap))
            var ammoavailable = player.weapon.GetEntity()!!.AmmoAvailable()
            var inclip = player.weapon.GetEntity()!!.AmmoInClip()
            if (ammoavailable != -1 && ammoavailable - inclip < 0) {
                // see DropWeapon
                Common.common.DPrintf("idPlayer::StealWeapon: bad ammo setup\n")
                // we still steal the weapon, so let's use the default ammo levels
                inclip = -1
                val decl = gameLocal.FindEntityDef(weapon_classname)!!
                val keypair = decl.dict.MatchPrefix("inv_ammo_")!!
                ammoavailable = TempDump.atoi(keypair.GetValue())
            }
            player.weapon.GetEntity()!!.WeaponStolen()
            player.inventory.Drop(player.spawnArgs, arrayOf(), newweap)
            player.SelectWeapon(weapon_fists, false)
            // in case the robbed player is firing rounds with a continuous fire weapon like the chaingun/plasma etc.
            // this will ensure the firing actually stops
            player.weaponGone = true

            // give weapon, setup the ammo count
            Give("weapon", weapon_classname)
            val ammo_i = player.inventory.AmmoIndexForWeaponClass(weapon_classname, null)
            idealWeapon = newweap
            inventory.ammo[ammo_i] += ammoavailable
            inventory.clip[newweap] = inclip
        }

        fun AddProjectilesFired(count: Int) {
            numProjectilesFired += count
        }

        fun AddProjectileHits(count: Int) {
            numProjectileHits += count
        }

        fun SetLastHitTime(time: Int) {
            var aimed: idPlayer? = null
            if (time != 0 && lastHitTime != time) {
                lastHitToggle = lastHitToggle xor true //1;
            }
            lastHitTime = time
            if (0 == time) {
                // level start and inits
                return
            }
            if (gameLocal.isMultiplayer && time - lastSndHitTime > 10) {
                lastSndHitTime = time
                StartSound("snd_hit_feedback", gameSoundChannel_t.SND_CHANNEL_ANY, Sound.SSF_PRIVATE_SOUND, false)
            }
            if (cursor != null) {
                cursor!!.HandleNamedEvent("hitTime")
            }
            if (hud != null) {
                if (MPAim != -1) {
                    if (gameLocal.entities[MPAim] != null && gameLocal.entities[MPAim] is idPlayer) {
                        aimed = gameLocal.entities[MPAim] as idPlayer?
                    }
                    assert(aimed != null)
                    // full highlight, no fade till loosing aim
                    hud!!.SetStateString("aim_text", gameLocal.userInfo[MPAim].GetString("ui_name"))
                    if (aimed != null) {
                        hud!!.SetStateFloat("aim_color", aimed.colorBarIndex.toFloat())
                    }
                    hud!!.HandleNamedEvent("aim_flash")
                    MPAimHighlight = true
                    MPAimFadeTime = 0
                } else if (lastMPAim != -1) {
                    if (gameLocal.entities[lastMPAim] != null && gameLocal.entities[lastMPAim] is idPlayer) {
                        aimed = gameLocal.entities[lastMPAim] as idPlayer?
                    }
                    assert(aimed != null)
                    // start fading right away
                    hud!!.SetStateString("aim_text", gameLocal.userInfo[lastMPAim].GetString("ui_name"))
                    if (aimed != null) {
                        hud!!.SetStateFloat("aim_color", aimed.colorBarIndex.toFloat())
                    }
                    hud!!.HandleNamedEvent("aim_flash")
                    hud!!.HandleNamedEvent("aim_fade")
                    MPAimHighlight = false
                    MPAimFadeTime = gameLocal.realClientTime
                }
            }
        }

        fun LowerWeapon() {
            if (weapon.GetEntity() != null && !weapon.GetEntity()!!.IsHidden()) {
                weapon.GetEntity()!!.LowerWeapon()
            }
        }

        fun RaiseWeapon() {
            if (weapon.GetEntity() != null && weapon.GetEntity()!!.IsHidden()) {
                weapon.GetEntity()!!.RaiseWeapon()
            }
        }

        fun WeaponLoweringCallback() {
            SetState("LowerWeapon")
            UpdateScript()
        }

        fun WeaponRisingCallback() {
            SetState("RaiseWeapon")
            UpdateScript()
        }

        fun RemoveWeapon(weap: String) {
            if (weap != null && !weap.isEmpty()) {
                val w = arrayOf(spawnArgs.GetString(weap))
                inventory.Drop(spawnArgs, w, -1)
                spawnArgs.Set(weap, w[0])
            }
        }

        fun CanShowWeaponViewmodel(): Boolean {
            return showWeaponViewModel
        }

        fun AddAIKill() {
            val max_souls: Int
            val ammo_souls: Int
            if (weapon_soulcube < 0 || (inventory.weapons and (1 shl weapon_soulcube)) == 0) {
                return
            }
            assert(hud != null)
            ammo_souls = idWeapon.GetAmmoNumForName("ammo_souls")
            max_souls = inventory.MaxAmmoForAmmoClass(this, "ammo_souls")
            if (inventory.ammo[ammo_souls] < max_souls) {
                inventory.ammo[ammo_souls]++
                if (inventory.ammo[ammo_souls] >= max_souls) {
                    hud!!.HandleNamedEvent("soulCubeReady")
                    StartSound("snd_soulcube_ready", gameSoundChannel_t.SND_CHANNEL_ANY, 0, false)
                }
            }
        }

        fun SetSoulCubeProjectile(projectile: idProjectile?) {
            soulCubeProjectile.oSet(projectile)
        }

        /*
         ==============
         idPlayer::AdjustHeartRate

         Player heartrate works as follows

         DEF_HEARTRATE is resting heartrate

         Taking damage when health is above 75 adjusts heart rate by 1 beat per second
         Taking damage when health is below 75 adjusts heart rate by 5 beats per second
         Maximum heartrate from damage is MAX_HEARTRATE

         Firing a weapon adds 1 beat per second up to a maximum of COMBAT_HEARTRATE

         Being at less than 25% stamina adds 5 beats per second up to ZEROSTAMINA_HEARTRATE

         All heartrates are target rates.. the heart rate will start falling as soon as there have been no adjustments for 5 seconds
         Once it starts falling it always tries to get to DEF_HEARTRATE

         The exception to the above rule is upon death at which point the rate is set to DYING_HEARTRATE and starts falling
         immediately to zero

         Heart rate volumes go from zero ( -40 db for DEF_HEARTRATE to 5 db for MAX_HEARTRATE ) the volume is
         scaled linearly based on the actual rate

         Exception to the above rule is once the player is dead, the dying heart rate starts at either the current volume if
         it is audible or -10db and scales to 8db on the last few beats
         ==============
         */
        fun AdjustHeartRate(target: Int, timeInSecs: Float, delay: Float, force: Boolean) {
            if (heartInfo.GetEndValue() == target.toFloat()) {
                return
            }
            if (AI_DEAD.underscore()!! && !force) {
                return
            }
            lastHeartAdjust = gameLocal.time
            heartInfo.Init(
                (gameLocal.time + delay * 1000).toInt().toFloat(),
                (timeInSecs * 1000).toInt().toFloat(),
                0.0f + heartRate,
                target.toFloat()
            )
        }

        fun SetCurrentHeartRate() {
            val base =
                idMath.FtoiFast(BASE_HEARTRATE + LOWHEALTH_HEARTRATE_ADJ - health.toFloat() / 100 * LOWHEALTH_HEARTRATE_ADJ)
            if (PowerUpActive(ADRENALINE)) {
                heartRate = 135
            } else {
                heartRate = idMath.FtoiFast(heartInfo.GetCurrentValue(gameLocal.time.toFloat()))
                val currentRate = GetBaseHeartRate()
                if (health >= 0 && gameLocal.time > lastHeartAdjust + 2500) {
                    AdjustHeartRate(currentRate, 2.5f, 0.0f, false)
                }
            }
            val bps = idMath.FtoiFast(60.0f / heartRate * 1000.0f)
            if (gameLocal.time - lastHeartBeat > bps) {
                val dmgVol = DMG_VOLUME
                val deathVol = DEATH_VOLUME
                val zeroVol = ZERO_VOLUME
                var pct = 0.0f
                if (heartRate > BASE_HEARTRATE && health > 0) {
                    pct = (heartRate - base).toFloat() / (MAX_HEARTRATE - base)
                    pct *= dmgVol.toFloat() - zeroVol.toFloat()
                } else if (health <= 0) {
                    pct = (heartRate - DYING_HEARTRATE).toFloat() / (BASE_HEARTRATE - DYING_HEARTRATE)
                    if (pct > 1.0f) {
                        pct = 1.0f
                    } else if (pct < 0) {
                        pct = 0.0f
                    }
                    pct *= deathVol.toFloat() - zeroVol.toFloat()
                }
                pct += zeroVol.toFloat()
                if (pct != zeroVol.toFloat()) {
                    StartSound(
                        "snd_heartbeat", gameSoundChannel_t.SND_CHANNEL_HEART, Sound.SSF_PRIVATE_SOUND, false
                    )
                    // modify just this channel to a custom volume
                    val parms = snd_shader.soundShaderParms_t() //memset( &parms, 0, sizeof( parms ) );
                    parms.volume = pct
                    refSound.referenceSound!!.ModifySound(TempDump.etoi(gameSoundChannel_t.SND_CHANNEL_HEART), parms)
                }
                lastHeartBeat = gameLocal.time
            }
        }

        fun GetBaseHeartRate(): Int {
            val base =
                idMath.FtoiFast(BASE_HEARTRATE + LOWHEALTH_HEARTRATE_ADJ - health.toFloat() / 100 * LOWHEALTH_HEARTRATE_ADJ)
            var rate =
                idMath.FtoiFast(base + (ZEROSTAMINA_HEARTRATE - base) * (1.0f - stamina / SysCvar.pm_stamina.GetFloat()))
            val diff = if (lastDmgTime != 0) gameLocal.time - lastDmgTime else 99999
            rate += if (diff < 5000) if (diff < 2500) if (diff < 1000) 15 else 10 else 5 else 0
            return rate
        }

        fun UpdateAir() {
            if (health <= 0) {
                return
            }

            // see if the player is connected to the info_vacuum
            var newAirless = false
            if (gameLocal.vacuumAreaNum != -1) {
                val num = GetNumPVSAreas()
                if (num > 0) {
                    val areaNum: Int

                    // if the player box spans multiple areas, get the area from the origin point instead,
                    // otherwise a rotating player box may poke into an outside area
                    areaNum = if (num == 1) {
                        val pvsAreas = CInt(GetPVSAreas()[0])
                        pvsAreas._val
                    } else {
                        Game_local.gameRenderWorld!!.PointInArea(GetPhysics().GetOrigin())
                    }
                    newAirless = Game_local.gameRenderWorld!!.AreasAreConnected(
                        gameLocal.vacuumAreaNum, areaNum, portalConnection_t.PS_BLOCK_AIR
                    )
                }
            }
            if (newAirless) {
                if (!airless) {
                    StartSound("snd_decompress", gameSoundChannel_t.SND_CHANNEL_ANY, Sound.SSF_GLOBAL, false)
                    StartSound("snd_noAir", gameSoundChannel_t.SND_CHANNEL_BODY2, 0, false)
                    if (hud != null) {
                        hud!!.HandleNamedEvent("noAir")
                    }
                }
                airTics--
                if (airTics < 0) {
                    airTics = 0
                    // check for damage
                    val damageDef = gameLocal.FindEntityDefDict("damage_noair", false)
                    val dmgTiming: Int =
                        (1000 * (if (damageDef != null) damageDef.GetFloat("delay", "3.0f").toInt() else 3))
                    if (gameLocal.time > lastAirDamage + dmgTiming) {
                        Damage(null, null, vec3_origin, "damage_noair", 1.0f, 0)
                        lastAirDamage = gameLocal.time
                    }
                }
            } else {
                if (airless) {
                    StartSound("snd_recompress", gameSoundChannel_t.SND_CHANNEL_ANY, Sound.SSF_GLOBAL, false)
                    StopSound(TempDump.etoi(gameSoundChannel_t.SND_CHANNEL_BODY2), false)
                    if (hud != null) {
                        hud!!.HandleNamedEvent("Air")
                    }
                }
                airTics += 2 // regain twice as fast as lose
                if (airTics > SysCvar.pm_airTics.GetInteger()) {
                    airTics = SysCvar.pm_airTics.GetInteger()
                }
            }
            airless = newAirless
            if (hud != null) {
                hud!!.SetStateInt("player_air", 100 * airTics / SysCvar.pm_airTics.GetInteger())
            }
        }

        override fun HandleSingleGuiCommand(entityGui: idEntity?, src: idLexer): Boolean {
            val token = idToken()
            if (!src.ReadToken(token)) {
                return false
            }
            if (token.toString() == ";") {
                return false
            }
            if (token.Icmp("addhealth") == 0) {
                if (entityGui != null && health < 100) {
                    var _health = entityGui.spawnArgs.GetInt("gui_parm1")
                    val amt = min(_health, HEALTH_PER_DOSE)
                    _health -= amt
                    entityGui.spawnArgs.SetInt("gui_parm1", _health)
                    if (entityGui.GetRenderEntity() != null && entityGui.GetRenderEntity()!!.gui[0] != null) {
                        entityGui.GetRenderEntity()!!.gui[0]!!.SetStateInt("gui_parm1", _health)
                    }
                    health += amt
                    if (health > 100) {
                        health = 100
                    }
                }
                return true
            }
            if (token.Icmp("ready") == 0) {
                PerformImpulse(UsercmdGen.IMPULSE_17)
                return true
            }
            if (token.Icmp("updatepda") == 0) {
                UpdatePDAInfo(true)
                return true
            }
            if (token.Icmp("updatepda2") == 0) {
                UpdatePDAInfo(false)
                return true
            }
            if (token.Icmp("stoppdavideo") == 0) {
                if (objectiveSystem != null && objectiveSystemOpen && pdaVideoWave.Length() > 0) {
                    StopSound(TempDump.etoi(gameSoundChannel_t.SND_CHANNEL_PDA), false)
                }
                return true
            }
            if (token.Icmp("close") == 0) {
                if (objectiveSystem != null && objectiveSystemOpen) {
                    TogglePDA()
                }
            }
            if (token.Icmp("playpdavideo") == 0) {
                if (objectiveSystem != null && objectiveSystemOpen && pdaVideo.Length() > 0) {
                    val mat: Material.idMaterial? = DeclManager.declManager.FindMaterial(pdaVideo)
                    if (mat != null) {
                        val c: Int = mat.GetNumStages()
                        for (i in 0 until c) {
                            val stage: shaderStage_t? = mat.GetStage(i)
                            if (stage != null && stage.texture.cinematic[0] != null) {
                                stage.texture.cinematic[0]!!.ResetTime(gameLocal.time)
                            }
                        }
                        if (pdaVideoWave.Length() != 0) {
                            val shader = DeclManager.declManager.FindSound(pdaVideoWave)
                            return StartSoundShader(shader, gameSoundChannel_t.SND_CHANNEL_PDA.ordinal, 0, false)
                        }
                    }
                }
            }
            if (token.Icmp("playpdaaudio") == 0) {
                if (objectiveSystem != null && objectiveSystemOpen && pdaAudio.Length() > 0) {
                    val shader = DeclManager.declManager.FindSound(pdaAudio)
                    val ms = CInt()
                    StartSoundShader(shader, gameSoundChannel_t.SND_CHANNEL_PDA, 0, false, ms)
                    StartAudioLog()
                    CancelEvents(EV_Player_StopAudioLog)
                    PostEventMS(EV_Player_StopAudioLog, ms._val + 150)
                }
                return true
            }
            if (token.Icmp("stoppdaaudio") == 0) {
                if (objectiveSystem != null && objectiveSystemOpen && pdaAudio.Length() > 0) {
                    // idSoundShader *shader = declManager.FindSound( pdaAudio );
                    StopAudioLog()
                    StopSound(TempDump.etoi(gameSoundChannel_t.SND_CHANNEL_PDA), false)
                }
                return true
            }
            src.UnreadToken(token)
            return false
        }

        fun GuiActive(): Boolean {
            return focusGUIent != null
        }

        fun PerformImpulse(impulse: Int) {
            if (gameLocal.isClient) {
                val msg = idBitMsg()
                val msgBuf = ByteBuffer.allocate(Game_local.MAX_EVENT_PARAM_SIZE)
                assert(entityNumber == gameLocal.localClientNum)
                msg.Init(msgBuf, Game_local.MAX_EVENT_PARAM_SIZE)
                msg.BeginWriting()
                msg.WriteBits(impulse, 6)
                ClientSendEvent(EVENT_IMPULSE, msg)
            }
            if (impulse >= UsercmdGen.IMPULSE_0 && impulse <= UsercmdGen.IMPULSE_12) {
                SelectWeapon(impulse, false)
                return
            }
            when (impulse) {
                UsercmdGen.IMPULSE_13 -> {
                    Reload()
                }

                UsercmdGen.IMPULSE_14 -> {
                    NextWeapon()
                }

                UsercmdGen.IMPULSE_15 -> {
                    PrevWeapon()
                }

                UsercmdGen.IMPULSE_17 -> {
                    if (gameLocal.isClient || entityNumber == gameLocal.localClientNum) {
                        gameLocal.mpGame.ToggleReady()
                    }
                }

                UsercmdGen.IMPULSE_18 -> {
                    centerView.Init(gameLocal.time.toFloat(), 200.0f, viewAngles.pitch, 0.0f)
                }

                UsercmdGen.IMPULSE_19 -> {

                    // when we're not in single player, IMPULSE_19 is used for showScores
                    // otherwise it opens the pda
                    if (!gameLocal.isMultiplayer) {
                        if (objectiveSystemOpen) {
                            TogglePDA()
                        } else if (weapon_pda >= 0) {
                            SelectWeapon(weapon_pda, true)
                        }
                    }
                }

                UsercmdGen.IMPULSE_20 -> {
                    if (gameLocal.isClient || entityNumber == gameLocal.localClientNum) {
                        gameLocal.mpGame.ToggleTeam()
                    }
                }

                UsercmdGen.IMPULSE_22 -> {
                    if (gameLocal.isClient || entityNumber == gameLocal.localClientNum) {
                        gameLocal.mpGame.ToggleSpectate()
                    }
                }

                UsercmdGen.IMPULSE_28 -> {
                    if (gameLocal.isClient || entityNumber == gameLocal.localClientNum) {
                        gameLocal.mpGame.CastVote(gameLocal.localClientNum, true)
                    }
                }

                UsercmdGen.IMPULSE_29 -> {
                    if (gameLocal.isClient || entityNumber == gameLocal.localClientNum) {
                        gameLocal.mpGame.CastVote(gameLocal.localClientNum, false)
                    }
                }

                UsercmdGen.IMPULSE_40 -> {
                    UseVehicle()
                }
            }
        }

        fun Spectate(spectate: Boolean) {
            val msg = idBitMsg()
            val msgBuf = ByteBuffer.allocate(Game_local.MAX_EVENT_PARAM_SIZE)
            assert(teleportEntity.GetEntity() != null || IsHidden() == spectating)
            if (spectating == spectate) {
                return
            }
            spectating = spectate
            if (gameLocal.isServer) {
                msg.Init(msgBuf, Game_local.MAX_EVENT_PARAM_SIZE)
                msg.WriteBits(TempDump.btoi(spectating), 1)
                ServerSendEvent(EVENT_SPECTATE, msg, false, -1)
            }
            if (spectating) {
                // join the spectators
                ClearPowerUps()
                spectator = entityNumber
                Init()
                StopRagdoll()
                SetPhysics(physicsObj)
                physicsObj.DisableClip()
                Hide()
                Event_DisableWeapon()
                if (hud != null) {
                    hud!!.HandleNamedEvent("aim_clear")
                    MPAimFadeTime = 0
                }
            } else {
                // put everything back together again
                currentWeapon = -1 // to make sure the def will be loaded if necessary
                Show()
                Event_EnableWeapon()
            }
            SetClipModel()
        }

        fun TogglePDA() {
            if (objectiveSystem == null) {
                return
            }
            if (inventory.pdas.size() == 0) {
                ShowTip(spawnArgs.GetString("text_infoTitle"), spawnArgs.GetString("text_noPDA"), true)
                return
            }
            assert(hud != null)
            if (!objectiveSystemOpen) {
                var j: Int
                val c = inventory.items.Num()
                objectiveSystem!!.SetStateInt("inv_count", c)
                j = 0
                while (j < MAX_INVENTORY_ITEMS) {
                    objectiveSystem!!.SetStateString(Str.va("inv_name_%d", j), "")
                    objectiveSystem!!.SetStateString(Str.va("inv_icon_%d", j), "")
                    objectiveSystem!!.SetStateString(Str.va("inv_text_%d", j), "")
                    j++
                }
                j = 0
                while (j < c) {
                    val item = inventory.items[j]
                    if (!item.GetBool("inv_pda")) {
                        val iname = item.GetString("inv_name")
                        val iicon = item.GetString("inv_icon")
                        val itext = item.GetString("inv_text")
                        objectiveSystem!!.SetStateString(Str.va("inv_name_%d", j), iname)
                        objectiveSystem!!.SetStateString(Str.va("inv_icon_%d", j), iicon)
                        objectiveSystem!!.SetStateString(Str.va("inv_text_%d", j), itext)
                        val kv = item.MatchPrefix("inv_id", null)
                        if (kv != null) {
                            objectiveSystem!!.SetStateString(Str.va("inv_id_%d", j), kv.GetValue().toString())
                        }
                    }
                    j++
                }
                j = 0
                while (j < MAX_WEAPONS) {
                    val weapnum = Str.va("def_weapon%d", j)
                    val hudWeap = Str.va("weapon%d", j)
                    var weapstate = 0
                    if ((inventory.weapons and (1 shl j)) != 0) {
                        val weap = spawnArgs.GetString(weapnum)
                        if (weap != null && !weap.isEmpty()) {
                            weapstate++
                        }
                    }
                    objectiveSystem!!.SetStateInt(hudWeap, weapstate)
                    j++
                }
                objectiveSystem!!.SetStateInt("listPDA_sel_0", inventory.selPDA)
                objectiveSystem!!.SetStateInt("listPDAVideo_sel_0", inventory.selVideo)
                objectiveSystem!!.SetStateInt("listPDAAudio_sel_0", inventory.selAudio)
                objectiveSystem!!.SetStateInt("listPDAEmail_sel_0", inventory.selEMail)
                UpdatePDAInfo(false)
                UpdateObjectiveInfo()
                objectiveSystem!!.Activate(true, gameLocal.time)
                hud!!.HandleNamedEvent("pdaPickupHide")
                hud!!.HandleNamedEvent("videoPickupHide")
            } else {
                inventory.selPDA = objectiveSystem!!.State().GetInt("listPDA_sel_0")
                inventory.selVideo = objectiveSystem!!.State().GetInt("listPDAVideo_sel_0")
                inventory.selAudio = objectiveSystem!!.State().GetInt("listPDAAudio_sel_0")
                inventory.selEMail = objectiveSystem!!.State().GetInt("listPDAEmail_sel_0")
                objectiveSystem!!.Activate(false, gameLocal.time)
            }
            objectiveSystemOpen = objectiveSystemOpen xor true //1;
        }

        fun ToggleScoreboard() {
            scoreBoardOpen = scoreBoardOpen xor true //1;
        }

        fun RouteGuiMouse(gui: idUserInterface) {
            val ev: sysEvent_s
            val command: String?
            if (usercmd.mx.toInt() != oldMouseX || usercmd.my.toInt() != oldMouseY) {
                ev = idLib.sys.GenerateMouseMoveEvent(usercmd.mx - oldMouseX, usercmd.my - oldMouseY)
                command = gui.HandleEvent(ev, gameLocal.time)
                oldMouseX = usercmd.mx.toInt()
                oldMouseY = usercmd.my.toInt()
            }
        }

        fun UpdateHud() {
            val aimed: idPlayer?
            if (null == hud) {
                return
            }
            if (entityNumber != gameLocal.localClientNum) {
                return
            }
            val c = inventory.pickupItemNames.Num()
            if (c > 0) {
                if (gameLocal.time > inventory.nextItemPickup) {
                    if (inventory.nextItemPickup != 0 && gameLocal.time - inventory.nextItemPickup > 2000) {
                        inventory.nextItemNum = 1
                    }
                    var i: Int
                    i = 0
                    while (i < 5 && i < c) {
                        hud!!.SetStateString(
                            Str.va("itemtext%d", inventory.nextItemNum), inventory.pickupItemNames[0].name.toString()
                        )
                        hud!!.SetStateString(
                            Str.va("itemicon%d", inventory.nextItemNum), inventory.pickupItemNames[0].icon.toString()
                        )
                        hud!!.HandleNamedEvent(Str.va("itemPickup%d", inventory.nextItemNum++))
                        inventory.pickupItemNames.RemoveIndex(0)
                        if (inventory.nextItemNum == 1) {
                            inventory.onePickupTime = gameLocal.time
                        } else if (inventory.nextItemNum > 5) {
                            inventory.nextItemNum = 1
                            inventory.nextItemPickup = inventory.onePickupTime + 2000
                        } else {
                            inventory.nextItemPickup = gameLocal.time + 400
                        }
                        i++
                    }
                }
            }
            if (gameLocal.realClientTime == lastMPAimTime) {
                if (MPAim != -1 && gameLocal.gameType == gameType_t.GAME_TDM && gameLocal.entities[MPAim] != null && gameLocal.entities[MPAim] is idPlayer && (gameLocal.entities[MPAim] as idPlayer).team == team) {
                    aimed = gameLocal.entities[MPAim] as idPlayer
                    hud!!.SetStateString("aim_text", gameLocal.userInfo[MPAim].GetString("ui_name"))
                    hud!!.SetStateFloat("aim_color", aimed.colorBarIndex.toFloat())
                    hud!!.HandleNamedEvent("aim_flash")
                    MPAimHighlight = true
                    MPAimFadeTime = 0 // no fade till loosing focus
                } else if (MPAimHighlight) {
                    hud!!.HandleNamedEvent("aim_fade")
                    MPAimFadeTime = gameLocal.realClientTime
                    MPAimHighlight = false
                }
            }
            if (MPAimFadeTime != 0) {
                assert(!MPAimHighlight)
                if (gameLocal.realClientTime - MPAimFadeTime > 2000) {
                    MPAimFadeTime = 0
                }
            }
            hud!!.SetStateInt("g_showProjectilePct", SysCvar.g_showProjectilePct.GetInteger())
            if (numProjectilesFired != 0) {
                hud!!.SetStateString(
                    "projectilepct", Str.va("Hit %% %.1f", numProjectileHits.toFloat() / numProjectilesFired * 100)
                )
            } else {
                hud!!.SetStateString("projectilepct", "Hit % 0.0")
            }
            if (isLagged && gameLocal.isMultiplayer && gameLocal.localClientNum == entityNumber) {
                hud!!.SetStateString("hudLag", "1")
            } else {
                hud!!.SetStateString("hudLag", "0")
            }
        }

        fun GetPDA(): idDeclPDA? {
            return if (inventory.pdas.size() != 0) {
                DeclManager.declManager.FindType(declType_t.DECL_PDA, inventory.pdas[0]) as idDeclPDA
            } else {
                null
            }
        }

        fun GetVideo(index: Int): idDeclVideo? {
            return if (index >= 0 && index < inventory.videos.size()) {
                DeclManager.declManager.FindType(
                    declType_t.DECL_VIDEO, inventory.videos[index], false
                ) as idDeclVideo
            } else null
        }

        fun SetInfluenceFov(fov: Float) {
            influenceFov = fov
        }

        fun SetInfluenceView(mtr: String?, skinname: String?, radius: Float, ent: idEntity?) {
            influenceMaterial = null
            influenceEntity = null
            influenceSkin = null
            if (mtr != null && !mtr.isEmpty()) {
                influenceMaterial = DeclManager.declManager.FindMaterial(mtr)
            }
            if (skinname != null && !skinname.isEmpty()) {
                influenceSkin = DeclManager.declManager.FindSkin(skinname)
                if (head?.GetEntity() != null) {
                    head.GetEntity()!!.GetRenderEntity()!!.shaderParms[RenderWorld.SHADERPARM_TIMEOFFSET] =
                        -MS2SEC(gameLocal.time.toFloat())
                }
                UpdateVisuals()
            }
            influenceRadius = radius
            if (radius > 0) {
                influenceEntity = ent
            }
        }

        fun SetInfluenceLevel(level: Int) {
            if (level != influenceActive) {
                if (level != 0) {
                    var ent = gameLocal.spawnedEntities.Next()
                    while (ent != null) {
                        if (ent.IsType(idProjectile.Type)) {
                            // remove all projectiles
                            ent.PostEventMS(EV_Remove, 0)
                        }
                        ent = ent.spawnNode.Next()
                    }
                    if (weaponEnabled && weapon.GetEntity() != null) {
                        weapon.GetEntity()!!.EnterCinematic()
                    }
                } else {
                    physicsObj.SetLinearVelocity(vec3_origin)
                    if (weaponEnabled && weapon.GetEntity() != null) {
                        weapon.GetEntity()!!.ExitCinematic()
                    }
                }
                influenceActive = level
            }
        }

        fun GetInfluenceLevel(): Int {
            return influenceActive
        }

        fun SetPrivateCameraView(camView: idCamera?) {
            privateCameraView = camView
            if (camView != null) {
                StopFiring()
                Hide()
            } else {
                if (!spectating) {
                    Show()
                }
            }
        }

        fun GetPrivateCameraView(): idCamera? {
            return privateCameraView
        }

        fun StartFxFov(duration: Float) {
            fxFov = true
            PostEventSec(EV_Player_StopFxFov, duration)
        }


        fun UpdateHudWeapon(flashWeapon: Boolean = true /*= true*/) {
            var hud = hud

            // if updating the hud of a followed client
            if (gameLocal.localClientNum >= 0 && gameLocal.entities[gameLocal.localClientNum] != null && gameLocal.entities[gameLocal.localClientNum] is idPlayer) {
                val p = gameLocal.entities[gameLocal.localClientNum] as idPlayer
                if (p.spectating && p.spectator == entityNumber) {
                    assert(p.hud != null)
                    hud = p.hud
                }
            }
            if (null == hud) {
                return
            }
            for (i in 0 until MAX_WEAPONS) {
                val weapnum = Str.va("def_weapon%d", i)
                val hudWeap = Str.va("weapon%d", i)
                var weapstate = 0
                if ((inventory.weapons and (1 shl i)) != 0) {
                    val weap = spawnArgs.GetString(weapnum)
                    if (weap != null && !weap.isEmpty()) {
                        weapstate++
                    }
                    if (idealWeapon == i) {
                        weapstate++
                    }
                }
                hud.SetStateInt(hudWeap, weapstate)
            }
            if (flashWeapon) {
                hud.HandleNamedEvent("weaponChange")
            }
        }

        fun UpdateHudStats(_hud: idUserInterface) {
            val staminapercentage: Int
            val max_stamina: Float
            assert(_hud != null)
            max_stamina = SysCvar.pm_stamina.GetFloat()
            staminapercentage = if (0.0f == max_stamina) {
                // stamina disabled, so show full stamina bar
                100
            } else {
                idMath.FtoiFast(100 * stamina / max_stamina)
            }
            _hud.SetStateInt("player_health", health)
            _hud.SetStateInt("player_stamina", staminapercentage)
            _hud.SetStateInt("player_armor", inventory.armor)
            _hud.SetStateInt("player_hr", heartRate.toInt())
            _hud.SetStateInt("player_nostamina", if (max_stamina == 0.0f) 1 else 0)
            _hud.HandleNamedEvent("updateArmorHealthAir")
            if (healthPulse) {
                _hud.HandleNamedEvent("healthPulse")
                StartSound("snd_healthpulse", gameSoundChannel_t.SND_CHANNEL_ITEM, 0, false)
                healthPulse = false
            }
            if (healthTake) {
                _hud.HandleNamedEvent("healthPulse")
                StartSound("snd_healthtake", gameSoundChannel_t.SND_CHANNEL_ITEM, 0, false)
                healthTake = false
            }
            if (inventory.ammoPulse) {
                _hud.HandleNamedEvent("ammoPulse")
                inventory.ammoPulse = false
            }
            if (inventory.weaponPulse) {
                // We need to update the weapon hud manually, but not
                // the armor/ammo/health because they are updated every
                // frame no matter what
                UpdateHudWeapon()
                _hud.HandleNamedEvent("weaponPulse")
                inventory.weaponPulse = false
            }
            if (inventory.armorPulse) {
                _hud.HandleNamedEvent("armorPulse")
                inventory.armorPulse = false
            }
            UpdateHudAmmo(_hud)
        }

        fun UpdateHudAmmo(_hud: idUserInterface) {
            val inclip: Int
            val ammoamount: Int
            assert(weapon.GetEntity() != null)
            assert(_hud != null)
            inclip = weapon.GetEntity()!!.AmmoInClip()
            ammoamount = weapon.GetEntity()!!.AmmoAvailable()
            if (ammoamount < 0 || !weapon.GetEntity()!!.IsReady()) {
                // show infinite ammo
                _hud.SetStateString("player_ammo", "")
                _hud.SetStateString("player_totalammo", "")
            } else {
                // show remaining ammo
                _hud.SetStateString("player_totalammo", Str.va("%d", ammoamount - inclip))
                _hud.SetStateString(
                    "player_ammo", if (weapon.GetEntity()!!.ClipSize() != 0) Str.va("%d", inclip) else "--"
                ) // how much in the current clip
                _hud.SetStateString(
                    "player_clips", if (weapon.GetEntity()!!.ClipSize() != 0) Str.va(
                        "%d", ammoamount / weapon.GetEntity()!!.ClipSize()
                    ) else "--"
                )
                _hud.SetStateString("player_allammo", Str.va("%d/%d", inclip, ammoamount - inclip))
            }
            _hud.SetStateBool("player_ammo_empty", ammoamount == 0)
            _hud.SetStateBool("player_clip_empty", weapon.GetEntity()!!.ClipSize() != 0 && inclip == 0)
            _hud.SetStateBool(
                "player_clip_low", weapon.GetEntity()!!.ClipSize() != 0 && inclip <= weapon.GetEntity()!!.LowAmmo()
            )
            _hud.HandleNamedEvent("updateAmmo")
        }

        fun Event_StopAudioLog() {
            StopAudioLog()
        }

        fun StartAudioLog() {
            if (hud != null) {
                hud!!.HandleNamedEvent("audioLogUp")
            }
        }

        fun StopAudioLog() {
            if (hud != null) {
                hud!!.HandleNamedEvent("audioLogDown")
            }
        }

        fun ShowTip(title: String, tip: String, autoHide: Boolean) {
            if (tipUp) {
                return
            }
            hud!!.SetStateString("tip", tip)
            hud!!.SetStateString("tiptitle", title)
            hud!!.HandleNamedEvent("tipWindowUp")
            if (autoHide) {
                PostEventSec(EV_Player_HideTip, 5.0f)
            }
            tipUp = true
        }

        fun HideTip() {
            hud!!.HandleNamedEvent("tipWindowDown")
            tipUp = false
        }

        fun IsTipVisible(): Boolean {
            return tipUp
        }

        fun ShowObjective(obj: String) {
            hud!!.HandleNamedEvent(obj)
            objectiveUp = true
        }

        fun HideObjective() {
            hud!!.HandleNamedEvent("closeObjective")
            objectiveUp = false
        }

        override fun ClientPredictionThink() {
            val headRenderEnt: renderEntity_s?
            oldFlags = usercmd.flags.toInt()
            oldButtons = usercmd.buttons.toInt()
            usercmd = gameLocal.usercmds[entityNumber]
            if (entityNumber != gameLocal.localClientNum) {
                // ignore attack button of other clients. that's no good for predictions
                usercmd.buttons = usercmd.buttons and UsercmdGen.BUTTON_ATTACK.inv().toByte()
            }
            buttonMask = buttonMask and usercmd.buttons.toInt()
            usercmd.buttons = usercmd.buttons and buttonMask.inv().toByte()
            if (objectiveSystemOpen) {
                usercmd.forwardmove = 0
                usercmd.rightmove = 0
                usercmd.upmove = 0
            }

            // clear the ik before we do anything else so the skeleton doesn't get updated twice
            walkIK.ClearJointMods()
            if (gameLocal.isNewFrame) {
                if ((usercmd.flags.toInt() and UsercmdGen.UCF_IMPULSE_SEQUENCE) != (oldFlags and UsercmdGen.UCF_IMPULSE_SEQUENCE)) {
                    PerformImpulse(usercmd.impulse.toInt())
                }
            }
            scoreBoardOpen = (usercmd.buttons.toInt() and UsercmdGen.BUTTON_SCORES) != 0 || forceScoreBoard
            AdjustSpeed()
            UpdateViewAngles()

            // update the smoothed view angles
            if (gameLocal.framenum >= smoothedFrame && entityNumber != gameLocal.localClientNum) {
                val anglesDiff = viewAngles.minus(smoothedAngles)
                anglesDiff.Normalize180()
                if (abs(anglesDiff.yaw) < 90 && abs(anglesDiff.pitch) < 90) {
                    // smoothen by pushing back to the previous angles
                    viewAngles.minusAssign(anglesDiff.times(gameLocal.clientSmoothing))
                    viewAngles.Normalize180()
                }
                smoothedAngles.set(viewAngles)
            }
            smoothedOriginUpdated = false
            if (!af.IsActive()) {
                AdjustBodyAngles()
            }
            if (!isLagged) {
                // don't allow client to move when lagged
                Move()
            }

            // update GUIs, Items, and character interactions
            UpdateFocus()

            // service animations
            if (!spectating && !af.IsActive()) {
                UpdateConditions()
                UpdateAnimState()
                CheckBlink()
            }

            // clear out our pain flag so we can tell if we recieve any damage between now and the next time we think
            AI_PAIN.underscore(false)

            // calculate the exact bobbed view position, which is used to
            // position the view weapon, among other things
            CalculateFirstPersonView()

            // this may use firstPersonView, or a thirdPerson / camera view
            CalculateRenderView()
            if (!gameLocal.inCinematic && weapon.GetEntity() != null && health > 0 && !(gameLocal.isMultiplayer && spectating)) {
                UpdateWeapon()
            }
            UpdateHud()
            if (gameLocal.isNewFrame) {
                UpdatePowerUps()
            }
            UpdateDeathSkin(false)
            headRenderEnt = if (head?.GetEntity() != null) {
                head.GetEntity()!!.GetRenderEntity()
            } else {
                null
            }
            if (headRenderEnt != null) {
                if (influenceSkin != null) {
                    headRenderEnt.customSkin = influenceSkin
                } else {
                    headRenderEnt.customSkin = null
                }
            }
            if (gameLocal.isMultiplayer || SysCvar.g_showPlayerShadow.GetBool()) {
                renderEntity!!.suppressShadowInViewID = 0
                if (headRenderEnt != null) {
                    headRenderEnt.suppressShadowInViewID = 0
                }
            } else {
                renderEntity!!.suppressShadowInViewID = entityNumber + 1
                if (headRenderEnt != null) {
                    headRenderEnt.suppressShadowInViewID = entityNumber + 1
                }
            }
            // never cast shadows from our first-person muzzle flashes
            renderEntity!!.suppressShadowInLightID = Weapon.LIGHTID_VIEW_MUZZLE_FLASH + entityNumber
            if (headRenderEnt != null) {
                headRenderEnt.suppressShadowInLightID = Weapon.LIGHTID_VIEW_MUZZLE_FLASH + entityNumber
            }
            if (!gameLocal.inCinematic) {
                UpdateAnimation()
            }
            if (gameLocal.isMultiplayer) {
                DrawPlayerIcons()
            }
            Present()
            UpdateDamageEffects()
            LinkCombat()
            if (gameLocal.isNewFrame && entityNumber == gameLocal.localClientNum) {
                playerView.CalculateShake()
            }

            if (isD3XP) {
                // determine if portal sky is in pvs
                val clientPVS = gameLocal.pvs.SetupCurrentPVS(GetPVSAreas(), GetNumPVSAreas())
                gameLocal.portalSkyActive = gameLocal.pvs.CheckAreasForPortalSky(
                    clientPVS, GetPhysics().GetOrigin()
                )
                gameLocal.pvs.FreeCurrentPVS(clientPVS)
            }
        }

        override fun WriteToSnapshot(msg: idBitMsgDelta) {
            physicsObj.WriteToSnapshot(msg)
            WriteBindToSnapshot(msg)
            msg.WriteDeltaFloat(0.0f, deltaViewAngles[0])
            msg.WriteDeltaFloat(0.0f, deltaViewAngles[1])
            msg.WriteDeltaFloat(0.0f, deltaViewAngles[2])
            msg.WriteShort(health)
            msg.WriteBits(
                gameLocal.ServerRemapDecl(-1, declType_t.DECL_ENTITYDEF, lastDamageDef),
                gameLocal.entityDefBits
            )
            msg.WriteDir(lastDamageDir, 9)
            msg.WriteShort(lastDamageLocation)
            msg.WriteBits(idealWeapon, idMath.BitsForInteger(MAX_WEAPONS))
            msg.WriteBits(inventory.weapons, MAX_WEAPONS)
            msg.WriteBits(weapon.GetSpawnId(), 32)
            msg.WriteBits(spectator, idMath.BitsForInteger(Game_local.MAX_CLIENTS))
            msg.WriteBits(TempDump.btoi(lastHitToggle), 1)
            msg.WriteBits(TempDump.btoi(weaponGone), 1)
            msg.WriteBits(TempDump.btoi(isLagged), 1)
            msg.WriteBits(TempDump.btoi(isChatting), 1)
        }

        override fun ReadFromSnapshot(msg: idBitMsgDelta) {
            var i: Int
            val oldHealth: Int
            val newIdealWeapon: Int
            val weaponSpawnId: Int
            val newHitToggle: Boolean
            val stateHitch: Boolean
            stateHitch = snapshotSequence - lastSnapshotSequence > 1
            lastSnapshotSequence = snapshotSequence
            oldHealth = health
            physicsObj.ReadFromSnapshot(msg)
            ReadBindFromSnapshot(msg)
            deltaViewAngles[0] = msg.ReadDeltaFloat(0.0f)
            deltaViewAngles[1] = msg.ReadDeltaFloat(0.0f)
            deltaViewAngles[2] = msg.ReadDeltaFloat(0.0f)
            health = msg.ReadShort()
            lastDamageDef = gameLocal.ClientRemapDecl(
                declType_t.DECL_ENTITYDEF, msg.ReadBits(gameLocal.entityDefBits)
            )
            lastDamageDir.set(msg.ReadDir(9))
            lastDamageLocation = msg.ReadShort()
            newIdealWeapon = msg.ReadBits(idMath.BitsForInteger(MAX_WEAPONS))
            inventory.weapons = msg.ReadBits(MAX_WEAPONS)
            weaponSpawnId = msg.ReadBits(32)
            spectator = msg.ReadBits(idMath.BitsForInteger(Game_local.MAX_CLIENTS))
            newHitToggle = msg.ReadBits(1) != 0
            weaponGone = msg.ReadBits(1) != 0
            isLagged = msg.ReadBits(1) != 0
            isChatting = msg.ReadBits(1) != 0

            // no msg reading below this
            if (weapon.SetSpawnId(weaponSpawnId)) {
                if (weapon.GetEntity() != null) {
                    // maintain ownership locally
                    weapon.GetEntity()!!.SetOwner(this)
                }
                currentWeapon = -1
            }
            // if not a local client assume the client has all ammo types
            if (entityNumber != gameLocal.localClientNum) {
                i = 0
                while (i < AMMO_NUMTYPES) {
                    inventory.ammo[i] = 999
                    i++
                }
            }
            if (oldHealth > 0 && health <= 0) {
                if (stateHitch) {
                    // so we just hide and don't show a death skin
                    UpdateDeathSkin(true)
                }
                // die
                AI_DEAD.underscore(true)
                ClearPowerUps()
                SetAnimState(Anim.ANIMCHANNEL_LEGS, "Legs_Death", 4)
                SetAnimState(Anim.ANIMCHANNEL_TORSO, "Torso_Death", 4)
                SetWaitState("")
                animator.ClearAllJoints()
                if (entityNumber == gameLocal.localClientNum) {
                    playerView.Fade(colorBlack, 12000)
                }
                StartRagdoll()
                physicsObj.SetMovementType(pmtype_t.PM_DEAD)
                if (!stateHitch) {
                    StartSound("snd_death", gameSoundChannel_t.SND_CHANNEL_VOICE, 0, false)
                }
                if (weapon.GetEntity() != null) {
                    weapon.GetEntity()!!.OwnerDied()
                }
            } else if (oldHealth <= 0 && health > 0) {
                // respawn
                Init()
                StopRagdoll()
                SetPhysics(physicsObj)
                physicsObj.EnableClip()
                SetCombatContents(true)
            } else if (health < oldHealth && health > 0) {
                if (stateHitch) {
                    lastDmgTime = gameLocal.time
                } else {
                    // damage feedback
                    val def = DeclManager.declManager.DeclByIndex(
                        declType_t.DECL_ENTITYDEF, lastDamageDef, false
                    ) as idDeclEntityDef
                    if (def != null) {
                        playerView.DamageImpulse(lastDamageDir.times(viewAxis.Transpose()), def.dict)
                        AI_PAIN.underscore(Pain(null, null, oldHealth - health, lastDamageDir, lastDamageLocation))
                        lastDmgTime = gameLocal.time
                    } else {
                        Common.common.Warning("NET: no damage def for damage feedback '%d'\n", lastDamageDef)
                    }
                }
            } else if (health > oldHealth && PowerUpActive(MEGAHEALTH) && !stateHitch) {
                // just pulse, for any health raise
                healthPulse = true
            }

            // If the player is alive, restore proper physics object
            if (health > 0 && IsActiveAF()) {
                StopRagdoll()
                SetPhysics(physicsObj)
                physicsObj.EnableClip()
                SetCombatContents(true)
            }
            if (idealWeapon != newIdealWeapon) {
                if (stateHitch) {
                    weaponCatchup = true
                }
                idealWeapon = newIdealWeapon
                UpdateHudWeapon()
            }
            if (lastHitToggle != newHitToggle) {
                SetLastHitTime(gameLocal.realClientTime)
            }
            if (msg.HasChanged()) {
                UpdateVisuals()
            }
        }

        fun WritePlayerStateToSnapshot(msg: idBitMsgDelta) {
            var i: Int
            msg.WriteByte(bobCycle)
            msg.WriteLong(stepUpTime)
            msg.WriteFloat(stepUpDelta)
            msg.WriteShort(inventory.weapons)
            msg.WriteByte(inventory.armor)
            i = 0
            while (i < AMMO_NUMTYPES) {
                msg.WriteBits(inventory.ammo[i], ASYNC_PLAYER_INV_AMMO_BITS)
                i++
            }
            i = 0
            while (i < MAX_WEAPONS) {
                msg.WriteBits(inventory.clip[i], ASYNC_PLAYER_INV_CLIP_BITS)
                i++
            }
        }

        fun ReadPlayerStateFromSnapshot(msg: idBitMsgDelta) {
            var i: Int
            var ammo: Int
            bobCycle = msg.ReadByte()
            stepUpTime = msg.ReadLong()
            stepUpDelta = msg.ReadFloat()
            inventory.weapons = msg.ReadShort()
            inventory.armor = msg.ReadByte()
            i = 0
            while (i < AMMO_NUMTYPES) {
                ammo = msg.ReadBits(ASYNC_PLAYER_INV_AMMO_BITS)
                if (gameLocal.time >= inventory.ammoPredictTime) {
                    inventory.ammo[i] = ammo
                }
                i++
            }
            i = 0
            while (i < MAX_WEAPONS) {
                inventory.clip[i] = msg.ReadBits(ASYNC_PLAYER_INV_CLIP_BITS)
                i++
            }
        }

        override fun ServerReceiveEvent(event: Int, time: Int, msg: idBitMsg?): Boolean {
            return if (idEntity_ServerReceiveEvent(event, time, msg)) {
                true
            } else when (event) {
                EVENT_IMPULSE -> {
                    PerformImpulse(msg!!.ReadBits(6))
                    true
                }

                else -> {
                    false
                }
            }
        }

        override fun GetPhysicsToVisualTransform(origin: idVec3, axis: idMat3): Boolean {
            if (af.IsActive()) {
                af.GetPhysicsToVisualTransform(origin, axis)
                return true
            }

            // smoothen the rendered origin and angles of other clients
            // smooth self origin if snapshots are telling us prediction is off
            if (gameLocal.isClient && gameLocal.framenum >= smoothedFrame && (entityNumber != gameLocal.localClientNum || selfSmooth)) {
                // render origin and axis
                val renderAxis = viewAxis.times(GetPhysics().GetAxis())
                val renderOrigin = idVec3(GetPhysics().GetOrigin().plus(modelOffset.times(renderAxis)))

                // update the smoothed origin
                if (!smoothedOriginUpdated) {
                    val originDiff = renderOrigin.ToVec2().minus(smoothedOrigin.ToVec2())
                    if (originDiff.LengthSqr() < Square(100.0f)) {
                        // smoothen by pushing back to the previous position
                        if (selfSmooth) {
                            assert(entityNumber == gameLocal.localClientNum)
                            renderOrigin.ToVec2_oMinSet(originDiff.times(Game_network.net_clientSelfSmoothing.GetFloat()))
                        } else {
                            renderOrigin.ToVec2_oMinSet(originDiff.times(gameLocal.clientSmoothing))
                        }
                    }
                    smoothedOrigin.set(renderOrigin)
                    smoothedFrame = gameLocal.framenum
                    smoothedOriginUpdated = true
                }
                axis.set(idAngles(0.0f, smoothedAngles.yaw, 0.0f).ToMat3())
                origin.set(smoothedOrigin.minus(GetPhysics().GetOrigin()).times(axis.Transpose()))
            } else {
                axis.set(viewAxis)
                origin.set(modelOffset)
            }
            return true
        }

        override fun GetPhysicsToSoundTransform(origin: idVec3, axis: idMat3): Boolean {
            val camera: idCamera?
            camera = if (privateCameraView != null) {
                privateCameraView
            } else {
                gameLocal.GetCamera()
            }
            return if (camera != null) {
                val view = renderView_s()

//		memset( &view, 0, sizeof( view ) );
                camera.GetViewParms(view)
                origin.set(view.vieworg)
                axis.set(view.viewaxis)
                true
            } else {
                super.GetPhysicsToSoundTransform(origin, axis)
            }
        }

        override fun ClientReceiveEvent(event: Int, time: Int, msg: idBitMsg): Boolean {
            val powerup: Int
            val start: Boolean
            return when (event) {
                EVENT_EXIT_TELEPORTER -> {
                    Event_ExitTeleporter()
                    true
                }

                EVENT_ABORT_TELEPORTER -> {
                    SetPrivateCameraView(null)
                    true
                }

                EVENT_POWERUP -> {
                    powerup = msg.ReadShort().toInt()
                    start = msg.ReadBits(1) != 0
                    if (start) {
                        GivePowerUp(powerup, 0)
                    } else {
                        ClearPowerup(powerup)
                    }
                    true
                }

                EVENT_SPECTATE -> {
                    val spectate = msg.ReadBits(1) != 0
                    Spectate(spectate)
                    true
                }

                EVENT_ADD_DAMAGE_EFFECT -> {
                    if (spectating) {
                        // if we're spectating, ignore
                        // happens if the event and the spectate change are written on the server during the same frame (fraglimit)
                        true
                    } else super.ClientReceiveEvent(event, time, msg)
                }

                else -> {
                    super.ClientReceiveEvent(event, time, msg)
                }
            }
            //            return false;
        }

        fun IsReady(): Boolean {
            return ready || forcedReady
        }

        fun IsRespawning(): Boolean {
            return respawning
        }

        fun IsInTeleport(): Boolean {
            return teleportEntity.GetEntity() != null
        }

        fun GetInfluenceEntity(): idEntity? {
            return influenceEntity
        }

        fun GetInfluenceMaterial(): Material.idMaterial? {
            return influenceMaterial
        }

        fun GetInfluenceRadius(): Float {
            return influenceRadius
        }

        // server side work for in/out of spectate. takes care of spawning it into the world as well
        fun ServerSpectate(spectate: Boolean) {
            assert(!gameLocal.isClient)
            if (spectating != spectate) {
                Spectate(spectate)
                if (spectate) {
                    SetSpectateOrigin()
                } else {
                    if (gameLocal.gameType == gameType_t.GAME_DM) {
                        // make sure the scores are reset so you can't exploit by spectating and entering the game back
                        // other game types don't matter, as you either can't join back, or it's team scores
                        gameLocal.mpGame.ClearFrags(entityNumber)
                    }
                }
            }
            if (!spectate) {
                SpawnFromSpawnSpot()
            }
        }

        // for very specific usage. != GetPhysics()
        fun GetPlayerPhysics(): idPhysics {
            return physicsObj
        }

        fun TeleportDeath(killer: Int) {
            teleportKiller = killer
        }

        fun SetLeader(lead: Boolean) {
            leader = lead
        }

        fun IsLeader(): Boolean {
            return leader
        }

        fun UpdateSkinSetup(restart: Boolean) {
            if (restart) {
                team = if (idStr.Icmp(GetUserInfo().GetString("ui_team"), "Blue") == 0) 1 else 0
            }
            if (gameLocal.gameType == gameType_t.GAME_TDM) {
                if (team != 0) {
                    baseSkinName.set("skins/characters/player/marine_mp_blue")
                } else {
                    baseSkinName.set("skins/characters/player/marine_mp_red")
                }
                if (!gameLocal.isClient && team != latchedTeam) {
                    gameLocal.mpGame.SwitchToTeam(entityNumber, latchedTeam, team)
                }
                latchedTeam = team
            } else {
                baseSkinName.set(GetUserInfo().GetString("ui_skin"))
            }
            if (0 == baseSkinName.Length()) {
                baseSkinName.set("skins/characters/player/marine_mp")
            }
            skin = DeclManager.declManager.FindSkin(baseSkinName, false)
            assert(skin != null)
            // match the skin to a color band for scoreboard
            colorBarIndex = if (baseSkinName.Find("red") != -1) {
                1
            } else if (baseSkinName.Find("green") != -1) {
                2
            } else if (baseSkinName.Find("blue") != -1) {
                3
            } else if (baseSkinName.Find("yellow") != -1) {
                4
            } else {
                0
            }
            colorBar.set(colorBarTable[colorBarIndex])
            if (PowerUpActive(BERSERK)) {
                powerUpSkin = DeclManager.declManager.FindSkin(baseSkinName.toString() + "_berserk")!!
            }
        }

        override fun OnLadder(): Boolean {
            return physicsObj.OnLadder()
        }

        fun UpdatePlayerIcons() {
            val time = NetworkSystem.networkSystem.ServerGetClientTimeSinceLastPacket(entityNumber)
            isLagged = time > CVarSystem.cvarSystem.GetCVarInteger("net_clientMaxPrediction")
        }

        fun DrawPlayerIcons() {
            if (!NeedsIcon()) {
                playerIcon.FreeIcon()
                return
            }
            playerIcon.Draw(this, headJoint)
        }

        fun HidePlayerIcons() {
            playerIcon.FreeIcon()
        }

        fun NeedsIcon(): Boolean {
            // local clients don't render their own icons... they're only info for other clients
            return entityNumber != gameLocal.localClientNum && (isLagged || isChatting)
        }

        fun SelfSmooth(): Boolean {
            return selfSmooth
        }

        fun SetSelfSmooth(b: Boolean) {
            selfSmooth = b
        }

        private fun LookAtKiller(inflictor: idEntity, attacker: idEntity) {
            val dir = idVec3()
            if (this != attacker) {
                dir.set(attacker.GetPhysics().GetOrigin().minus(GetPhysics().GetOrigin()))
            } else if (this != inflictor) {
                dir.set(inflictor.GetPhysics().GetOrigin().minus(GetPhysics().GetOrigin()))
            } else {
                dir.set(viewAxis[0])
            }
            val ang = idAngles(0.0f, dir.ToYaw(), 0.0f)
            SetViewAngles(ang)
        }

        private fun StopFiring() {
            AI_ATTACK_HELD.underscore(false)
            AI_WEAPON_FIRED.underscore(false)
            AI_RELOAD.underscore(false)
            if (weapon.GetEntity() != null) {
                weapon.GetEntity()!!.EndAttack()
            }
        }

        private fun FireWeapon() {
            val axis = idMat3()
            val muzzle = idVec3()
            if (privateCameraView != null) {
                return
            }
            if (SysCvar.g_editEntityMode.GetInteger() != 0) {
                GetViewPos(muzzle, axis)
                if (gameLocal.editEntities!!.SelectEntity(muzzle, axis[0], this)) {
                    return
                }
            }
            if (!hiddenWeapon && weapon.GetEntity()!!.IsReady()) {
                if (weapon.GetEntity()!!.AmmoInClip() != 0 || weapon.GetEntity()!!.AmmoAvailable() != 0) {
                    AI_ATTACK_HELD.underscore(true)
                    weapon.GetEntity()!!.BeginAttack()
                    if (weapon_soulcube >= 0 && currentWeapon == weapon_soulcube) {
                        if (hud != null) {
                            hud!!.HandleNamedEvent("soulCubeNotReady")
                        }
                        SelectWeapon(previousWeapon, false)
                    }
                } else {
                    NextBestWeapon()
                }
            }
            if (hud != null) {
                if (tipUp) {
                    HideTip()
                }
                // may want to track with with a bool as well
                // keep from looking up named events so often
                if (objectiveUp) {
                    HideObjective()
                }
            }
        }

        private fun Weapon_Combat() {
            if (influenceActive != 0 || !weaponEnabled || gameLocal.inCinematic || privateCameraView != null) {
                return
            }
            weapon.GetEntity()!!.RaiseWeapon()
            if (weapon.GetEntity()!!.IsReloading()) {
                if (!AI_RELOAD.underscore()!!) {
                    AI_RELOAD.underscore(true)
                    SetState("ReloadWeapon")
                    UpdateScript()
                }
            } else {
                AI_RELOAD.underscore(false)
            }
            if (idealWeapon == weapon_soulcube && soulCubeProjectile != null && soulCubeProjectile.GetEntity() != null) {
                idealWeapon = currentWeapon
            }
            if (idealWeapon != currentWeapon) {
                if (weaponCatchup) {
                    assert(gameLocal.isClient)
                    currentWeapon = idealWeapon
                    weaponGone = false
                    animPrefix.set(spawnArgs.GetString(Str.va("def_weapon%d", currentWeapon)))
                    weapon.GetEntity()!!.GetWeaponDef(animPrefix.toString(), inventory.clip[currentWeapon])
                    animPrefix.Strip("weapon_")
                    weapon.GetEntity()!!.NetCatchup()
                    val newstate = GetScriptFunction("NetCatchup")
                    if (newstate != null) {
                        SetState(newstate)
                        UpdateScript()
                    }
                    weaponCatchup = false
                } else {
                    if (weapon.GetEntity()!!.IsReady()) {
                        weapon.GetEntity()!!.PutAway()
                    }
                    if (weapon.GetEntity()!!.IsHolstered()) {
                        assert(idealWeapon >= 0)
                        assert(idealWeapon < MAX_WEAPONS)
                        if (currentWeapon != weapon_pda && !spawnArgs.GetBool(
                                Str.va(
                                    "weapon%d_toggle", currentWeapon
                                )
                            )
                        ) {
                            previousWeapon = currentWeapon
                        }
                        currentWeapon = idealWeapon
                        weaponGone = false
                        animPrefix.set(spawnArgs.GetString(Str.va("def_weapon%d", currentWeapon)))
                        weapon.GetEntity()!!.GetWeaponDef(animPrefix.toString(), inventory.clip[currentWeapon])
                        animPrefix.Strip("weapon_")
                        weapon.GetEntity()!!.Raise()
                    }
                }
            } else {
                weaponGone = false // if you drop and re-get weap, you may miss the = false above
                if (weapon.GetEntity()!!.IsHolstered()) {
                    if (weapon.GetEntity()!!.AmmoAvailable() == 0) {
                        // weapons can switch automatically if they have no more ammo
                        NextBestWeapon()
                    } else {
                        weapon.GetEntity()!!.Raise()
                        state = GetScriptFunction("RaiseWeapon")
                        if (state != null) {
                            SetState(state)
                        }
                    }
                }
            }

            // check for attack
            AI_WEAPON_FIRED.underscore(false)
            if (0 == influenceActive) {
                if ((usercmd.buttons.toInt() and UsercmdGen.BUTTON_ATTACK) != 0 && !weaponGone) {
                    FireWeapon()
                } else if ((oldButtons and UsercmdGen.BUTTON_ATTACK) != 0) {
                    AI_ATTACK_HELD.underscore(false)
                    weapon.GetEntity()!!.EndAttack()
                }
            }

            // update our ammo clip in our inventory
            if (currentWeapon >= 0 && currentWeapon < MAX_WEAPONS) {
                inventory.clip[currentWeapon] = weapon.GetEntity()!!.AmmoInClip()
                if (hud != null && currentWeapon == idealWeapon) {
                    UpdateHudAmmo(hud!!)
                }
            }
        }

        private fun Weapon_NPC() {
            if (idealWeapon != currentWeapon) {
                Weapon_Combat()
            }
            StopFiring()
            weapon.GetEntity()!!.LowerWeapon()
            if ((usercmd.buttons.toInt() and UsercmdGen.BUTTON_ATTACK) != 0 && (oldButtons and UsercmdGen.BUTTON_ATTACK) == 0) {
                buttonMask = buttonMask or UsercmdGen.BUTTON_ATTACK
                focusCharacter!!.TalkTo(this)
            }
        }

        private fun Weapon_GUI() {
            if (!objectiveSystemOpen) {
                if (idealWeapon != currentWeapon) {
                    Weapon_Combat()
                }
                StopFiring()
                weapon.GetEntity()!!.LowerWeapon()
            }

            // disable click prediction for the GUIs. handy to check the state sync does the right thing
            if (gameLocal.isClient && !SysCvar.net_clientPredictGUI.GetBool()) {
                return
            }
            if (((oldButtons xor usercmd.buttons.toInt()) and UsercmdGen.BUTTON_ATTACK) != 0) {
                val ev: sysEvent_s
                var command: String? = ""
                val updateVisuals = CBool(false)
                val ui = ActiveGui()
                if (ui != null) {
                    ev =
                        idLib.sys.GenerateMouseButtonEvent(
                            1,
                            (usercmd.buttons.toInt() and UsercmdGen.BUTTON_ATTACK) != 0
                        )
                    command = ui.HandleEvent(ev, gameLocal.time, updateVisuals)
                    if (updateVisuals._val && focusGUIent != null && ui == focusUI) {
                        focusGUIent!!.UpdateVisuals()
                    }
                }
                if (gameLocal.isClient) {
                    // we predict enough, but don't want to execute commands
                    return
                }
                if (focusGUIent != null) {
                    HandleGuiCommands(focusGUIent, command)
                } else {
                    HandleGuiCommands(this, command)
                }
            }
        }

        private fun UpdateWeapon() {
            if (health <= 0) {
                return
            }
            assert(!spectating)
            if (gameLocal.isClient) {
                // clients need to wait till the weapon and it's world model entity
                // are present and synchronized ( weapon.worldModel idEntityPtr to idAnimatedEntity )
                if (!weapon.GetEntity()!!.IsWorldModelReady()) {
                    return
                }
            }

            // always make sure the weapon is correctly setup before accessing it
            if (!weapon.GetEntity()!!.IsLinked()) {
                if (idealWeapon != -1) {
                    animPrefix.set(spawnArgs.GetString(Str.va("def_weapon%d", idealWeapon)))
                    weapon.GetEntity()!!.GetWeaponDef(animPrefix.toString(), inventory.clip[idealWeapon])
                    assert(weapon.GetEntity()!!.IsLinked())
                } else {
                    return
                }
            }
            if (hiddenWeapon && tipUp && (usercmd.buttons.toInt() and UsercmdGen.BUTTON_ATTACK) != 0) {
                HideTip()
            }
            if (SysCvar.g_dragEntity.GetBool()) {
                StopFiring()
                weapon.GetEntity()!!.LowerWeapon()
                dragEntity.Update(this)
            } else if (ActiveGui() != null) {
                // gui handling overrides weapon use
                Weapon_GUI()
            } else if (focusCharacter != null && focusCharacter!!.health > 0) {
                Weapon_NPC()
            } else {
                Weapon_Combat()
            }
            if (hiddenWeapon) {
                weapon.GetEntity()!!.LowerWeapon()
            }

            // update weapon state, particles, dlights, etc
            weapon.GetEntity()!!.PresentWeapon(showWeaponViewModel)
        }

        private fun UpdateSpectating() {
            assert(spectating)
            assert(!gameLocal.isClient)
            assert(IsHidden())
            val player: idPlayer?
            if (!gameLocal.isMultiplayer) {
                return
            }
            player = gameLocal.GetClientByNum(spectator)
            if (null == player || player.spectating && player !== this) { //TODO:equals instead of != or ==
                SpectateFreeFly(true)
            } else if (usercmd.upmove > 0) {
                SpectateFreeFly(false)
            } else if ((usercmd.buttons.toInt() and UsercmdGen.BUTTON_ATTACK) != 0) {
                SpectateCycle()
            }
        }

        private fun SpectateFreeFly(force: Boolean) {    // ignore the timeout to force when followed spec is no longer valid
            val player: idPlayer?
            val newOrig = idVec3()
            val spawn_origin = idVec3()
            val spawn_angles = idAngles()
            player = gameLocal.GetClientByNum(spectator)
            if (force || gameLocal.time > lastSpectateChange) {
                spectator = entityNumber
                if (player != null && player !== this && !player.spectating && !player.IsInTeleport()) {
                    newOrig.set(player.GetPhysics().GetOrigin())
                    if (player.physicsObj.IsCrouching()) {
                        newOrig.plusAssign(2, SysCvar.pm_crouchviewheight.GetFloat())
                    } else {
                        newOrig.plusAssign(2, SysCvar.pm_normalviewheight.GetFloat())
                    }
                    newOrig.plusAssign(2, SPECTATE_RAISE.toFloat())
                    val b = idBounds(vec3_origin).Expand(SysCvar.pm_spectatebbox.GetFloat() * 0.5f)
                    val start = idVec3(player.GetPhysics().GetOrigin())
                    start.plusAssign(2, SysCvar.pm_spectatebbox.GetFloat() * 0.5f)
                    val t = trace_s()
                    // assuming spectate bbox is inside stand or crouch box
                    gameLocal.clip.TraceBounds(t, start, newOrig, b, Game_local.MASK_PLAYERSOLID, player)
                    newOrig.Lerp(start, newOrig, t.fraction)
                    SetOrigin(newOrig)
                    val angle = player.viewAngles
                    angle[2] = 0.0f
                    SetViewAngles(angle)
                } else {
                    SelectInitialSpawnPoint(spawn_origin, spawn_angles)
                    spawn_origin.plusAssign(2, SysCvar.pm_normalviewheight.GetFloat())
                    spawn_origin.plusAssign(2, SPECTATE_RAISE.toFloat())
                    SetOrigin(spawn_origin)
                    SetViewAngles(spawn_angles)
                }
                lastSpectateChange = gameLocal.time + 500
            }
        }

        private fun SpectateCycle() {
            var player: idPlayer?
            if (gameLocal.time > lastSpectateChange) {
                val latchedSpectator = spectator
                spectator = gameLocal.GetNextClientNum(spectator)
                player = gameLocal.GetClientByNum(spectator)
                assert(
                    player != null // never call here when the current spectator is wrong
                )
                // ignore other spectators
                while (latchedSpectator != spectator && player!!.spectating) {
                    spectator = gameLocal.GetNextClientNum(spectator)
                    player = gameLocal.GetClientByNum(spectator)
                }
                lastSpectateChange = gameLocal.time + 500
            }
        }

        /*
         ==============
         idPlayer::GunTurningOffset

         generate a rotational offset for the gun based on the view angle
         history in loggedViewAngles
         ==============
         */
        private fun GunTurningOffset(): idAngles {
            val a = idAngles()

//            a.Zero();
            if (gameLocal.framenum < NUM_LOGGED_VIEW_ANGLES) {
                return a
            }
            val current = loggedViewAngles[gameLocal.framenum and NUM_LOGGED_VIEW_ANGLES - 1]
            val av: idAngles //, base;
            val weaponAngleOffsetAverages = CInt()
            val weaponAngleOffsetScale = CFloat()
            val weaponAngleOffsetMax = CFloat()
            weapon.GetEntity()!!
                .GetWeaponAngleOffsets(weaponAngleOffsetAverages, weaponAngleOffsetScale, weaponAngleOffsetMax)
            av = idAngles(current)

            // calcualte this so the wrap arounds work properly
            for (j in 1 until weaponAngleOffsetAverages._val) {
                val a2 = loggedViewAngles[gameLocal.framenum - j and NUM_LOGGED_VIEW_ANGLES - 1]
                val delta = a2.minus(current)
                if (delta[1] > 180) {
                    delta.minusAssign(1, 360.0f)
                } else if (delta[1] < -180) {
                    delta.plusAssign(1, 360.0f)
                }
                av.plusAssign(delta.times(1.0f / weaponAngleOffsetAverages._val))
            }
            a.set(av.minus(current).times(weaponAngleOffsetScale._val))
            for (i in 0..2) {
                if (a[i] < -weaponAngleOffsetMax._val) {
                    a[i] = -weaponAngleOffsetMax._val
                } else if (a[i] > weaponAngleOffsetMax._val) {
                    a[i] = weaponAngleOffsetMax._val
                }
            }
            return a
        }

        //
        //        private void UseObjects();
        //
        //
        /*
         ==============
         idPlayer::GunAcceleratingOffset

         generate a positional offset for the gun based on the movement
         history in loggedAccelerations
         ==============
         */
        private fun GunAcceleratingOffset(): idVec3 {
            val ofs = idVec3()
            val weaponOffsetTime = CFloat()
            val weaponOffsetScale = CFloat()
            ofs.Zero()
            weapon.GetEntity()!!.GetWeaponTimeOffsets(weaponOffsetTime, weaponOffsetScale)
            var stop = currentLoggedAccel - NUM_LOGGED_ACCELS
            if (stop < 0) {
                stop = 0
            }
            for (i in currentLoggedAccel - 1 downTo stop + 1) {
                val acc = loggedAccel[i and NUM_LOGGED_ACCELS - 1]
                var f: Float
                val t = (gameLocal.time - acc.time).toFloat()
                if (t >= weaponOffsetTime._val) {
                    break // remainder are too old to care about
                }
                f = t / weaponOffsetTime._val
                f = ((cos((f * 2.0f * idMath.PI)) - 1.0f) * 0.5f)
                ofs.plusAssign(acc.dir.times(f * weaponOffsetScale._val))
            }
            return ofs
        }

        /*
         =================
         idPlayer::CrashLand

         Check for hard landings that generate sound events
         =================
         */
        private fun CrashLand(oldOrigin: idVec3, oldVelocity: idVec3) {
            val origin = idVec3()
            idVec3()
            val gravityVector = idVec3()
            val gravityNormal = idVec3()
            var delta: Float
            val hardDelta: Float
            val fatalDelta: Float
            val dist: Float
            val vel: Float
            val acc: Float
            val t: Float
            val a: Float
            val b: Float
            val c: Float
            val den: Float
            val waterLevel: waterLevel_t
            var noDamage: Boolean
            AI_SOFTLANDING.underscore(false)
            AI_HARDLANDING.underscore(false)

            // if the player is not on the ground
            if (!physicsObj.HasGroundContacts()) {
                return
            }
            gravityNormal.set(physicsObj.GetGravityNormal())

            // if the player wasn't going down
            if (oldVelocity.times(gravityNormal.unaryMinus()) >= 0) {
                return
            }
            waterLevel = physicsObj.GetWaterLevel()

            // never take falling damage if completely underwater
            if (waterLevel == waterLevel_t.WATERLEVEL_HEAD) {
                return
            }

            // no falling damage if touching a nodamage surface
            noDamage = false
            for (i in 0 until physicsObj.GetNumContacts()) {
                val contact = physicsObj.GetContact(i)!!
                if (contact.material != null && (contact.material!!.GetSurfaceFlags() and Material.SURF_NODAMAGE) != 0) {
                    noDamage = true
                    StartSound("snd_land_hard", gameSoundChannel_t.SND_CHANNEL_ANY, 0, false)
                    break
                }
            }
            origin.set(GetPhysics().GetOrigin())
            gravityVector.set(physicsObj.GetGravity())

            // calculate the exact velocity on landing
            dist = origin.minus(oldOrigin).times(gravityNormal.unaryMinus())
            vel = oldVelocity.times(gravityNormal.unaryMinus())
            acc = -gravityVector.Length()
            a = acc / 2.0f
            b = vel
            c = -dist
            den = b * b - 4.0f * a * c
            if (den < 0) {
                return
            }
            t = (-b - idMath.Sqrt(den)) / (2.0f * a)
            delta = vel + t * acc
            delta = delta * delta * 0.0001f

            // reduce falling damage if there is standing water
            if (waterLevel == waterLevel_t.WATERLEVEL_WAIST) {
                delta *= 0.25f
            }
            if (waterLevel == waterLevel_t.WATERLEVEL_FEET) {
                delta *= 0.5f
            }
            if (delta < 1.0f) {
                return
            }

            // allow falling a bit further for multiplayer
            if (gameLocal.isMultiplayer) {
                fatalDelta = 75.0f
                hardDelta = 50.0f
            } else {
                fatalDelta = 65.0f
                hardDelta = 45.0f
            }
            if (delta > fatalDelta) {
                AI_HARDLANDING.underscore(true)
                landChange = -32
                landTime = gameLocal.time
                if (!noDamage) {
                    pain_debounce_time =
                        gameLocal.time + pain_delay + 1 // ignore pain since we'll play our landing anim
                    Damage(null, null, idVec3(0, 0, -1), "damage_fatalfall", 1.0f, 0)
                }
            } else if (delta > hardDelta) {
                AI_HARDLANDING.underscore(true)
                landChange = -24
                landTime = gameLocal.time
                if (!noDamage) {
                    pain_debounce_time =
                        gameLocal.time + pain_delay + 1 // ignore pain since we'll play our landing anim
                    Damage(null, null, idVec3(0, 0, -1), "damage_hardfall", 1.0f, 0)
                }
            } else if (delta > 30) {
                AI_HARDLANDING.underscore(true)
                landChange = -16
                landTime = gameLocal.time
                if (!noDamage) {
                    pain_debounce_time =
                        gameLocal.time + pain_delay + 1 // ignore pain since we'll play our landing anim
                    Damage(null, null, idVec3(0, 0, -1), "damage_softfall", 1.0f, 0)
                }
            } else if (delta > 7) {
                AI_SOFTLANDING.underscore(true)
                landChange = -8
                landTime = gameLocal.time
            } else if (delta > 3) {
                // just walk on
            }
        }

        private fun BobCycle(pushVelocity: idVec3) {
            val bobmove: Float
            val old: Int
            var deltaTime: Int
            val vel = idVec3()
            val gravityDir = idVec3()
            val velocity = idVec3()
            val viewaxis: idMat3
            var bob: Float
            var delta: Float
            val speed: Float
            val f: Float

            //
            // calculate speed and cycle to be used for
            // all cyclic walking effects
            //
            velocity.set(physicsObj.GetLinearVelocity().minus(pushVelocity))
            gravityDir.set(physicsObj.GetGravityNormal())
            vel.set(velocity.minus(gravityDir.times(velocity.times(gravityDir))))
            xyspeed = vel.LengthFast()

            // do not evaluate the bob for other clients
            // when doing a spectate follow, don't do any weapon bobbing
            if (gameLocal.isClient && entityNumber != gameLocal.localClientNum) {
                viewBobAngles.Zero()
                viewBob.Zero()
                return
            }
            if (!physicsObj.HasGroundContacts() || influenceActive == INFLUENCE_LEVEL2 || gameLocal.isMultiplayer && spectating) {
                // airborne
                bobCycle = 0
                bobFoot = 0
                bobfracsin = 0.0f
            } else if (0 == usercmd.forwardmove.toInt() && 0 == usercmd.rightmove.toInt() || xyspeed <= MIN_BOB_SPEED) {
                // start at beginning of cycle again
                bobCycle = 0
                bobFoot = 0
                bobfracsin = 0.0f
            } else {
                bobmove = if (physicsObj.IsCrouching()) {
                    SysCvar.pm_crouchbob.GetFloat()
                    // ducked characters never play footsteps
                } else {
                    // vary the bobbing based on the speed of the player
                    SysCvar.pm_walkbob.GetFloat() * (1.0f - bobFrac) + SysCvar.pm_runbob.GetFloat() * bobFrac
                }

                // check for footstep / splash sounds
                old = bobCycle
                bobCycle = (old + bobmove * idGameLocal.msecPrecise).toInt() and 255
                bobFoot = bobCycle and 128 shr 7
                bobfracsin = abs(sin((bobCycle and 127) / 127.0f * idMath.PI))
            }

            // calculate angles for view bobbing
            viewBobAngles.Zero()
            viewaxis = viewAngles.ToMat3().times(physicsObj.GetGravityAxis())

            // add angles based on velocity
            delta = velocity.times(viewaxis[0])
            viewBobAngles.pitch += delta * SysCvar.pm_runpitch.GetFloat()
            delta = velocity.times(viewaxis[1])
            viewBobAngles.roll -= delta * SysCvar.pm_runroll.GetFloat()

            // add angles based on bob
            // make sure the bob is visible even at low speeds
            speed = if (xyspeed > 200) xyspeed else 200.0f
            delta = bobfracsin * SysCvar.pm_bobpitch.GetFloat() * speed
            if (physicsObj.IsCrouching()) {
                delta *= 3.0f // crouching
            }
            viewBobAngles.pitch += delta
            delta = bobfracsin * SysCvar.pm_bobroll.GetFloat() * speed
            if (physicsObj.IsCrouching()) {
                delta *= 3.0f // crouching accentuates roll
            }
            if ((bobFoot and 1) != 0) {
                delta = -delta
            }
            viewBobAngles.roll += delta

            // calculate position for view bobbing
            viewBob.Zero()
            if (physicsObj.HasSteppedUp()) {

                // check for stepping up before a previous step is completed
                deltaTime = gameLocal.time - stepUpTime
                stepUpDelta = if (deltaTime < STEPUP_TIME) {
                    stepUpDelta * (STEPUP_TIME - deltaTime) / STEPUP_TIME + physicsObj.GetStepUp()
                } else {
                    physicsObj.GetStepUp()
                }
                if (stepUpDelta > 2.0f * SysCvar.pm_stepsize.GetFloat()) {
                    stepUpDelta = 2.0f * SysCvar.pm_stepsize.GetFloat()
                }
                stepUpTime = gameLocal.time
            }
            val gravity = idVec3(physicsObj.GetGravityNormal())

            // if the player stepped up recently
            deltaTime = gameLocal.time - stepUpTime
            if (deltaTime < STEPUP_TIME) {
                viewBob.plusAssign(gravity.times(stepUpDelta * (STEPUP_TIME - deltaTime) / STEPUP_TIME))
            }

            // add bob height after any movement smoothing
            bob = bobfracsin * xyspeed * SysCvar.pm_bobup.GetFloat()
            if (bob > 6) {
                bob = 6.0f
            }
            viewBob.plusAssign(2, bob)

            // add fall height
            delta = (gameLocal.time - landTime).toFloat()
            if (delta < LAND_DEFLECT_TIME) {
                f = delta / LAND_DEFLECT_TIME
                viewBob.minusAssign(gravity.times(landChange * f))
            } else if (delta < LAND_DEFLECT_TIME + LAND_RETURN_TIME) {
                delta -= LAND_DEFLECT_TIME.toFloat()
                f = 1.0f - delta / LAND_RETURN_TIME
                viewBob.minusAssign(gravity.times(landChange * f))
            }
        }

        private fun UpdateViewAngles() {
            var i: Int
            idAngles()
            if (!noclip && (gameLocal.inCinematic || privateCameraView != null || gameLocal.GetCamera() != null || influenceActive == INFLUENCE_LEVEL2 || objectiveSystemOpen)) {
                // no view changes at all, but we still want to update the deltas or else when
                // we get out of this mode, our view will snap to a kind of random angle
                UpdateDeltaViewAngles(viewAngles)
                return
            }

            // if dead
            if (health <= 0) {
                if (SysCvar.pm_thirdPersonDeath.GetBool()) {
                    viewAngles.roll = 0.0f
                    viewAngles.pitch = 30.0f
                } else {
                    viewAngles.roll = 40.0f
                    viewAngles.pitch = -15.0f
                }
                return
            }

            // circularly clamp the angles with deltas
            i = 0
            while (i < 3) {
                cmdAngles[i] = SHORT2ANGLE(usercmd.angles[i])
                if (influenceActive == INFLUENCE_LEVEL3) {
                    viewAngles.plusAssign(
                        i, idMath.ClampFloat(
                            -1.0f, 1.0f, idMath.AngleDelta(
                                idMath.AngleNormalize180(
                                    SHORT2ANGLE(usercmd.angles[i]) + deltaViewAngles[i]
                                ), viewAngles[i]
                            )
                        )
                    )
                } else {
                    viewAngles[i] = idMath.AngleNormalize180(SHORT2ANGLE(usercmd.angles[i]) + deltaViewAngles[i])
                }
                i++
            }
            if (!centerView.IsDone(gameLocal.time.toFloat())) {
                viewAngles.pitch = centerView.GetCurrentValue(gameLocal.time.toFloat())
            }

            // clamp the pitch
            if (noclip) {
                if (viewAngles.pitch > 89.0f) {
                    // don't let the player look down more than 89 degrees while noclipping
                    viewAngles.pitch = 89.0f
                } else if (viewAngles.pitch < -89.0f) {
                    // don't let the player look up more than 89 degrees while noclipping
                    viewAngles.pitch = -89.0f
                }
            } else {
                if (viewAngles.pitch > SysCvar.pm_maxviewpitch.GetFloat()) {
                    // don't let the player look down enough to see the shadow of his (non-existant) feet
                    viewAngles.pitch = SysCvar.pm_maxviewpitch.GetFloat()
                } else if (viewAngles.pitch < SysCvar.pm_minviewpitch.GetFloat()) {
                    // don't let the player look up more than 89 degrees
                    viewAngles.pitch = SysCvar.pm_minviewpitch.GetFloat()
                }
            }
            UpdateDeltaViewAngles(viewAngles)

            // orient the model towards the direction we're looking
            SetAngles(idAngles(0.0f, viewAngles.yaw, 0.0f))

            // save in the log for analyzing weapon angle offsets
            loggedViewAngles[gameLocal.framenum and NUM_LOGGED_VIEW_ANGLES - 1].set(viewAngles)
        }

        private fun EvaluateControls() {
            // check for respawning
            if (health <= 0) {
                if (gameLocal.time > minRespawnTime && (usercmd.buttons.toInt() and UsercmdGen.BUTTON_ATTACK) != 0) {
                    forceRespawn = true
                } else if (gameLocal.time > maxRespawnTime) {
                    forceRespawn = true
                }
            }

            // in MP, idMultiplayerGame decides spawns
            if (forceRespawn && !gameLocal.isMultiplayer && !SysCvar.g_testDeath.GetBool()) {
                // in single player, we let the session handle restarting the level or loading a game
                gameLocal.sessionCommand.set("died")
            }
            if ((usercmd.flags.toInt() and UsercmdGen.UCF_IMPULSE_SEQUENCE) != (oldFlags and UsercmdGen.UCF_IMPULSE_SEQUENCE)) {
                PerformImpulse(usercmd.impulse.toInt())
            }
            scoreBoardOpen = (usercmd.buttons.toInt() and UsercmdGen.BUTTON_SCORES) != 0 || forceScoreBoard
            oldFlags = usercmd.flags.toInt()
            AdjustSpeed()

            // update the viewangles
            UpdateViewAngles()
        }

        private fun AdjustSpeed() {
            var speed = 0.0f
            var rate = 0.0f
            if (spectating) {
                speed = SysCvar.pm_spectatespeed.GetFloat()
                bobFrac = 0.0f
            } else if (noclip) {
                speed = SysCvar.pm_noclipspeed.GetFloat()
                bobFrac = 0.0f
            } else if (!physicsObj.OnLadder() && (usercmd.buttons.toInt() and UsercmdGen.BUTTON_RUN) != 0 && (usercmd.forwardmove.toInt() != 0 || usercmd.rightmove.toInt() != 0) && usercmd.upmove >= 0) {
                if (!gameLocal.isMultiplayer && !physicsObj.IsCrouching() && !PowerUpActive(ADRENALINE)) {
                    stamina -= MS2SEC(gameLocal.msec.toFloat())
                }
                if (stamina < 0) {
                    stamina = 0.0f
                }
                bobFrac = if (
                    SysCvar.pm_stamina.GetFloat() == 0.0f || stamina > SysCvar.pm_staminathreshold.GetFloat()
                ) {
                    1.0f
                } else if (SysCvar.pm_staminathreshold.GetFloat() <= 0.0001) {
                    0.0f
                } else {
                    stamina / SysCvar.pm_staminathreshold.GetFloat()
                }
                speed = SysCvar.pm_walkspeed.GetFloat() * (1.0f - bobFrac) + SysCvar.pm_runspeed.GetFloat() * bobFrac
            } else {
                rate = SysCvar.pm_staminarate.GetFloat()

                // increase 25% faster when not moving
                if (usercmd.forwardmove.toInt() == 0 && usercmd.rightmove.toInt() == 0 && (!physicsObj.OnLadder() || usercmd.upmove.toInt() == 0)) {
                    rate *= 1.25f
                }
                stamina += rate * MS2SEC(gameLocal.msec.toFloat())
                if (stamina > SysCvar.pm_stamina.GetFloat()) {
                    stamina = SysCvar.pm_stamina.GetFloat()
                }
                speed = SysCvar.pm_walkspeed.GetFloat()
                bobFrac = 0.0f
            }
            speed *= PowerUpModifier(SPEED)
            if (influenceActive == INFLUENCE_LEVEL3) {
                speed *= 0.33f
            }
            physicsObj.SetSpeed(speed, SysCvar.pm_crouchspeed.GetFloat())
        }

        private fun AdjustBodyAngles() {
//            idMat3 lookAxis;
            val legsAxis: idMat3
            var blend: Boolean
            val diff: Float
            val frac: Float
            val upBlend: Float
            val forwardBlend: Float
            val downBlend: Float
            if (health < 0) {
                return
            }
            blend = true
            if (!physicsObj.HasGroundContacts()) {
                idealLegsYaw = 0.0f
                legsForward = true
            } else if (usercmd.forwardmove < 0) {
                idealLegsYaw = idMath.AngleNormalize180(
                    idVec3(
                        -usercmd.forwardmove.toFloat(), usercmd.rightmove.toFloat(), 0.0f
                    ).ToYaw()
                )
                legsForward = false
            } else if (usercmd.forwardmove > 0) {
                idealLegsYaw = idMath.AngleNormalize180(
                    idVec3(
                        usercmd.forwardmove.toFloat(), -usercmd.rightmove.toFloat(), 0.0f
                    ).ToYaw()
                )
                legsForward = true
            } else if (usercmd.rightmove.toInt() != 0 && physicsObj.IsCrouching()) {
                idealLegsYaw = if (!legsForward) {
                    idMath.AngleNormalize180(
                        idVec3(
                            abs(usercmd.rightmove.toFloat()), usercmd.rightmove.toFloat(), 0.0f
                        ).ToYaw()
                    )
                } else {
                    idMath.AngleNormalize180(
                        idVec3(
                            idMath.Abs(usercmd.rightmove.toInt()), -usercmd.rightmove, 0
                        ).ToYaw()
                    )
                }
            } else if (usercmd.rightmove.toInt() != 0) {
                idealLegsYaw = 0.0f
                legsForward = true
            } else {
                legsForward = true
                diff = abs(idealLegsYaw - legsYaw)
                idealLegsYaw = idealLegsYaw - idMath.AngleNormalize180(viewAngles.yaw - oldViewYaw)
                if (diff < 0.1f) {
                    legsYaw = idealLegsYaw
                    blend = false
                }
            }
            if (!physicsObj.IsCrouching()) {
                legsForward = true
            }
            oldViewYaw = viewAngles.yaw
            AI_TURN_LEFT.underscore(false)
            AI_TURN_RIGHT.underscore(false)
            if (idealLegsYaw < -45.0f) {
                idealLegsYaw = 0.0f
                AI_TURN_RIGHT.underscore(true)
                blend = true
            } else if (idealLegsYaw > 45.0f) {
                idealLegsYaw = 0.0f
                AI_TURN_LEFT.underscore(true)
                blend = true
            }
            if (blend) {
                legsYaw = legsYaw * 0.9f + idealLegsYaw * 0.1f
            }
            legsAxis = idAngles(0.0f, legsYaw, 0.0f).ToMat3()
            animator.SetJointAxis(hipJoint, jointModTransform_t.JOINTMOD_WORLD, legsAxis)

            // calculate the blending between down, straight, and up
            frac = viewAngles.pitch / 90
            if (frac > 0) {
                downBlend = frac
                forwardBlend = 1.0f - frac
                upBlend = 0.0f
            } else {
                downBlend = 0.0f
                forwardBlend = 1.0f + frac
                upBlend = -frac
            }
            animator.CurrentAnim(Anim.ANIMCHANNEL_TORSO).SetSyncedAnimWeight(0, downBlend)
            animator.CurrentAnim(Anim.ANIMCHANNEL_TORSO).SetSyncedAnimWeight(1, forwardBlend)
            animator.CurrentAnim(Anim.ANIMCHANNEL_TORSO).SetSyncedAnimWeight(2, upBlend)
            animator.CurrentAnim(Anim.ANIMCHANNEL_LEGS).SetSyncedAnimWeight(0, downBlend)
            animator.CurrentAnim(Anim.ANIMCHANNEL_LEGS).SetSyncedAnimWeight(1, forwardBlend)
            animator.CurrentAnim(Anim.ANIMCHANNEL_LEGS).SetSyncedAnimWeight(2, upBlend)
        }

        private fun InitAASLocation() {
            var i: Int
            val num: Int
            val size = idVec3()
            val bounds = idBounds()
            var aas: idAAS?
            val origin = idVec3()
            GetFloorPos(64.0f, origin)
            num = gameLocal.NumAAS()
            aasLocation.SetGranularity(1)
            aasLocation.SetNum(num)
            i = 0
            while (i < aasLocation.Num()) {
                aasLocation[i] = aasLocation_t()
                aasLocation[i].areaNum = 0
                aasLocation[i].pos.set(origin)
                aas = gameLocal.GetAAS(i)
                if (aas != null && aas.GetSettings() != null) {
                    size.set(aas.GetSettings()!!.boundingBoxes[0][1])
                    bounds[0] = size.unaryMinus()
                    size.z = 32.0f
                    bounds[1] = size
                    aasLocation[i].areaNum = aas.PointReachableAreaNum(origin, bounds, AASFile.AREA_REACHABLE_WALK)
                }
                i++
            }
        }

        private fun SetAASLocation() {
            var i: Int
            var areaNum: Int
            val size = idVec3()
            val bounds = idBounds()
            var aas: idAAS?
            val origin = idVec3()
            if (!GetFloorPos(64.0f, origin)) {
                return
            }
            i = 0
            while (i < aasLocation.Num()) {
                aas = gameLocal.GetAAS(i)
                if (null == aas) {
                    i++
                    continue
                }
                size.set(aas.GetSettings()!!.boundingBoxes[0][1])
                bounds[0] = size.unaryMinus()
                size.z = 32.0f
                bounds[1] = size
                areaNum = aas.PointReachableAreaNum(origin, bounds, AASFile.AREA_REACHABLE_WALK)
                if (areaNum != 0) {
                    aasLocation[i].pos.set(origin)
                    aasLocation[i].areaNum = areaNum
                }
                i++
            }
        }

        private fun Move() {
            val newEyeOffset: Float
            val oldOrigin = idVec3()
            val oldVelocity = idVec3()
            val pushVelocity = idVec3()

            // save old origin and velocity for crashlanding
            oldOrigin.set(physicsObj.GetOrigin())
            oldVelocity.set(physicsObj.GetLinearVelocity())
            pushVelocity.set(physicsObj.GetPushedLinearVelocity())

            // set physics variables
            physicsObj.SetMaxStepHeight(SysCvar.pm_stepsize.GetFloat())
            physicsObj.SetMaxJumpHeight(SysCvar.pm_jumpheight.GetFloat())
            if (noclip) {
                physicsObj.SetContents(0)
                physicsObj.SetMovementType(pmtype_t.PM_NOCLIP)
            } else if (spectating) {
                physicsObj.SetContents(0)
                physicsObj.SetMovementType(pmtype_t.PM_SPECTATOR)
            } else if (health <= 0) {
                physicsObj.SetContents(Material.CONTENTS_CORPSE or Material.CONTENTS_MONSTERCLIP)
                physicsObj.SetMovementType(pmtype_t.PM_DEAD)
            } else if (gameLocal.inCinematic || gameLocal.GetCamera() != null || privateCameraView != null || influenceActive == INFLUENCE_LEVEL2) {
                physicsObj.SetContents(Material.CONTENTS_BODY)
                physicsObj.SetMovementType(pmtype_t.PM_FREEZE)
            } else {
                physicsObj.SetContents(Material.CONTENTS_BODY)
                physicsObj.SetMovementType(pmtype_t.PM_NORMAL)
            }
            if (spectating) {
                physicsObj.SetClipMask(Game_local.MASK_DEADSOLID)
            } else if (health <= 0) {
                physicsObj.SetClipMask(Game_local.MASK_DEADSOLID)
            } else {
                physicsObj.SetClipMask(Game_local.MASK_PLAYERSOLID)
            }
            physicsObj.SetDebugLevel(SysCvar.g_debugMove.GetBool())
            physicsObj.SetPlayerInput(usercmd, viewAngles)

            // FIXME: physics gets disabled somehow
            BecomeActive(TH_PHYSICS)
            RunPhysics()

            // update our last valid AAS location for the AI
            SetAASLocation()
            newEyeOffset = if (spectating) {
                0.0f
            } else if (health <= 0) {
                SysCvar.pm_deadviewheight.GetFloat()
            } else if (physicsObj.IsCrouching()) {
                SysCvar.pm_crouchviewheight.GetFloat()
            } else if (GetBindMaster() != null && GetBindMaster() is idAFEntity_Vehicle) {
                0.0f
            } else {
                SysCvar.pm_normalviewheight.GetFloat()
            }
            if (EyeHeight() != newEyeOffset) {
                if (spectating) {
                    SetEyeHeight(newEyeOffset)
                } else {
                    // smooth out duck height changes
                    SetEyeHeight(EyeHeight() * SysCvar.pm_crouchrate.GetFloat() + newEyeOffset * (1.0f - SysCvar.pm_crouchrate.GetFloat()))
                }
            }
            if (noclip || gameLocal.inCinematic || influenceActive == INFLUENCE_LEVEL2) {
                AI_CROUCH.underscore(false)
                AI_ONGROUND.underscore(influenceActive == INFLUENCE_LEVEL2)
                AI_ONLADDER.underscore(false)
                AI_JUMP.underscore(false)
            } else {
                AI_CROUCH.underscore(physicsObj.IsCrouching())
                AI_ONGROUND.underscore(physicsObj.HasGroundContacts())
                AI_ONLADDER.underscore(physicsObj.OnLadder())
                AI_JUMP.underscore(physicsObj.HasJumped())

                // check if we're standing on top of a monster and give a push if we are
                val groundEnt = physicsObj.GetGroundEntity()
                if (groundEnt is idAI) {
                    val vel = idVec3(physicsObj.GetLinearVelocity())
                    if (vel.ToVec2().LengthSqr() < 0.1f) {
                        vel.set(
                            physicsObj.GetOrigin().ToVec2()
                                .minus(groundEnt.GetPhysics().GetAbsBounds().GetCenter().ToVec2())
                        )
                        vel.ToVec2_NormalizeFast()
                        vel.ToVec2_oMulSet(SysCvar.pm_walkspeed.GetFloat()) //TODO:ToVec2 back ref.
                    } else {
                        // give em a push in the direction they're going
                        vel.timesAssign(1.1f)
                    }
                    physicsObj.SetLinearVelocity(vel)
                }
            }
            if (AI_JUMP.underscore()!!) {
                // bounce the view weapon
                val acc = loggedAccel[currentLoggedAccel and NUM_LOGGED_ACCELS - 1]
                currentLoggedAccel++
                acc.time = gameLocal.time
                acc.dir[2] = 200.0f
                acc.dir[0] = acc.dir.set(1, 0.0f)
            }
            if (AI_ONLADDER.underscore()!!) {
                val old_rung = (oldOrigin.z / LADDER_RUNG_DISTANCE).toInt()
                val new_rung = (physicsObj.GetOrigin().z / LADDER_RUNG_DISTANCE).toInt()
                if (old_rung != new_rung) {
                    StartSound("snd_stepladder", gameSoundChannel_t.SND_CHANNEL_ANY, 0, false)
                }
            }
            BobCycle(pushVelocity)
            CrashLand(oldOrigin, oldVelocity)
        }

        private fun UpdatePowerUps() {
            var i: Int
            if (!gameLocal.isClient) {
                i = 0
                while (i < MAX_POWERUPS) {
                    if (PowerUpActive(i) && inventory.powerupEndTime[i] <= gameLocal.time) {
                        ClearPowerup(i)
                    }
                    i++
                }
            }
            if (health > 0) {
                if (powerUpSkin != null) {
                    renderEntity!!.customSkin = powerUpSkin
                } else {
                    renderEntity!!.customSkin = skin
                }
            }
            if (healthPool != 0.0f && gameLocal.time > nextHealthPulse && !AI_DEAD.underscore()!! && health > 0) {
                assert(
                    !gameLocal.isClient // healthPool never be set on client
                )
                val amt = (if (healthPool > 5) 5 else healthPool).toInt()
                health += amt
                if (health > inventory.maxHealth) {
                    health = inventory.maxHealth
                    healthPool = 0.0f
                } else {
                    healthPool -= amt.toFloat()
                }
                nextHealthPulse = gameLocal.time + HEALTHPULSE_TIME
                healthPulse = true
            }
            if (ID_DEMO_BUILD) {
                if (!gameLocal.inCinematic && influenceActive == 0 && SysCvar.g_skill.GetInteger() == 3 && gameLocal.time > nextHealthTake && !AI_DEAD.underscore()!! && health > SysCvar.g_healthTakeLimit.GetInteger()) {
                    assert(
                        !gameLocal.isClient // healthPool never be set on client
                    )
                    health -= SysCvar.g_healthTakeAmt.GetInteger()
                    if (health < SysCvar.g_healthTakeLimit.GetInteger()) {
                        health = SysCvar.g_healthTakeLimit.GetInteger()
                    }
                    nextHealthTake = gameLocal.time + SysCvar.g_healthTakeTime.GetInteger() * 1000
                    healthTake = true
                }
            }
        }

        private fun UpdateDeathSkin(state_hitch: Boolean) {
            if (!(gameLocal.isMultiplayer || SysCvar.g_testDeath.GetBool())) {
                return
            }
            if (health <= 0) {
                if (!doingDeathSkin) {
                    deathClearContentsTime = spawnArgs.GetInt("deathSkinTime")
                    doingDeathSkin = true
                    renderEntity!!.noShadow = true
                    if (state_hitch) {
                        renderEntity!!.shaderParms[RenderWorld.SHADERPARM_TIME_OF_DEATH] =
                            gameLocal.time * 0.001f - 2.0f
                    } else {
                        renderEntity!!.shaderParms[RenderWorld.SHADERPARM_TIME_OF_DEATH] =
                            gameLocal.time * 0.001f
                    }
                    UpdateVisuals()
                }

                // wait a bit before switching off the content
                if (deathClearContentsTime != 0 && gameLocal.time > deathClearContentsTime) {
                    SetCombatContents(false)
                    deathClearContentsTime = 0
                }
            } else {
                renderEntity!!.noShadow = false
                renderEntity!!.shaderParms[RenderWorld.SHADERPARM_TIME_OF_DEATH] = 0.0f
                UpdateVisuals()
                doingDeathSkin = false
            }
        }

        private fun ClearPowerup(i: Int) {
            if (gameLocal.isServer) {
                val msg = idBitMsg()
                val msgBuf = ByteBuffer.allocate(Game_local.MAX_EVENT_PARAM_SIZE)
                msg.Init(msgBuf, Game_local.MAX_EVENT_PARAM_SIZE)
                msg.WriteShort(i.toShort())
                msg.WriteBits(0, 1)
                ServerSendEvent(EVENT_POWERUP, msg, false, -1)
            }
            powerUpSkin = null
            inventory.powerups = inventory.powerups and (1 shl i).inv()
            inventory.powerupEndTime[i] = 0
            when (i) {
                BERSERK -> {
                    StopSound(TempDump.etoi(gameSoundChannel_t.SND_CHANNEL_DEMONIC), false)
                }

                INVISIBILITY -> {
                    if (weapon.GetEntity() != null) {
                        weapon.GetEntity()!!.UpdateSkin()
                    }
                }
            }
        }

        private fun SetSpectateOrigin() {
            val neworig = idVec3()
            neworig.set(GetPhysics().GetOrigin())
            neworig.plusAssign(2, EyeHeight())
            neworig.plusAssign(2, 25.0f)
            SetOrigin(neworig)
        }

        /*
         ================
         idPlayer::ClearFocus

         Clears the focus cursor
         ================
         */
        private fun ClearFocus() {
            focusCharacter = null
            focusGUIent = null
            focusUI = null
            focusVehicle = null
            talkCursor = 0
        }

        /*
         ================
         idPlayer::UpdateFocus

         Searches nearby entities for interactive guis, possibly making one of them
         the focus and sending it a mouse move event
         ================
         */
        private fun UpdateFocus() {
            val clipModelList = arrayOfNulls<idClipModel>(Game_local.MAX_GENTITIES)
            var clip: idClipModel
            val listedClipModels: Int
            val oldFocus: idEntity?
            var ent: idEntity
            val oldUI: idUserInterface?
            val oldChar: idAI?
            val oldTalkCursor: Int
            val oldVehicle: idAFEntity_Vehicle?
            var i: Int
            var j: Int
            val start = idVec3()
            val end = idVec3()
            val allowFocus: Boolean
            var command: String?
            val trace = trace_s()
            var pt: guiPoint_t
            var kv: idKeyValue?
            var ev: sysEvent_s
            var ui: idUserInterface?
            if (gameLocal.inCinematic) {
                return
            }

            // only update the focus character when attack button isn't pressed so players
            // can still chainsaw NPC's
            allowFocus =
                !gameLocal.isMultiplayer && (focusCharacter != null || (usercmd.buttons.toInt() and UsercmdGen.BUTTON_ATTACK) == 0)
            oldFocus = focusGUIent
            oldUI = focusUI
            oldChar = focusCharacter
            oldTalkCursor = talkCursor
            oldVehicle = focusVehicle
            if (focusTime <= gameLocal.time) {
                ClearFocus()
            }

            // don't let spectators interact with GUIs
            if (spectating) {
                return
            }
            start.set(GetEyePosition())
            end.set(start.plus(viewAngles.ToForward().times(80.0f)))

            // player identification . names to the hud
            if (gameLocal.isMultiplayer && entityNumber == gameLocal.localClientNum) {
                val end2 = idVec3(start.plus(viewAngles.ToForward().times(768.0f)))
                gameLocal.clip.TracePoint(trace, start, end2, Game_local.MASK_SHOT_BOUNDINGBOX, this)
                var iclient = -1
                if (trace.fraction < 1.0f && trace.c.entityNum < Game_local.MAX_CLIENTS) {
                    iclient = trace.c.entityNum
                }
                if (MPAim != iclient) {
                    lastMPAim = MPAim
                    MPAim = iclient
                    lastMPAimTime = gameLocal.realClientTime
                }
            }
            val bounds = idBounds(start)
            bounds.AddPoint(end)
            listedClipModels =
                gameLocal.clip.ClipModelsTouchingBounds(bounds, -1, clipModelList, Game_local.MAX_GENTITIES)

            // no pretense at sorting here, just assume that there will only be one active
            // gui within range along the trace
            i = 0
            while (i < listedClipModels) {
                clip = clipModelList[i]!!
                ent = clip.GetEntity()!!
                if (ent.IsHidden()) {
                    i++
                    continue
                }
                if (allowFocus) {
                    if (ent is idAFAttachment) {
                        val body = ent.GetBody()
                        if (body != null && body is idAI && TempDump.etoi(body.GetTalkState()) >= TempDump.etoi(
                                talkState_t.TALK_OK
                            )
                        ) {
                            gameLocal.clip.TracePoint(
                                trace, start, end, Game_local.MASK_SHOT_RENDERMODEL, this
                            )
                            if (trace.fraction < 1.0f && trace.c.entityNum == ent.entityNumber) {
                                ClearFocus()
                                focusCharacter = body
                                talkCursor = 1
                                focusTime = gameLocal.time + FOCUS_TIME
                                break
                            }
                        }
                        i++
                        continue
                    }
                    if (ent is idAI) {
                        if (TempDump.etoi(ent.GetTalkState()) >= TempDump.etoi(talkState_t.TALK_OK)) {
                            gameLocal.clip.TracePoint(
                                trace, start, end, Game_local.MASK_SHOT_RENDERMODEL, this
                            )
                            if (trace.fraction < 1.0f && trace.c.entityNum == ent.entityNumber) {
                                ClearFocus()
                                focusCharacter = ent
                                talkCursor = 1
                                focusTime = gameLocal.time + FOCUS_TIME
                                break
                            }
                        }
                        i++
                        continue
                    }
                    if (ent is idAFEntity_Vehicle) {
                        gameLocal.clip.TracePoint(trace, start, end, Game_local.MASK_SHOT_RENDERMODEL, this)
                        if (trace.fraction < 1.0f && trace.c.entityNum == ent.entityNumber) {
                            ClearFocus()
                            focusVehicle = ent
                            focusTime = gameLocal.time + FOCUS_TIME
                            break
                        }
                        i++
                        continue
                    }
                }
                if (ent.GetRenderEntity() == null || ent.GetRenderEntity()!!.gui[0] == null || !ent.GetRenderEntity()!!.gui[0]!!.IsInteractive()) {
                    i++
                    continue
                }
                if (ent.spawnArgs.GetBool("inv_item")) {
                    // don't allow guis on pickup items focus
                    i++
                    continue
                }
                pt = Game_local.gameRenderWorld!!.GuiTrace(ent.GetModelDefHandle(), start, end)
                if (pt.x != -1.0f) {
                    // we have a hit
                    val focusGUIrenderEntity = ent.GetRenderEntity()
                    if (focusGUIrenderEntity == null) {
                        i++
                        continue
                    }
                    ui = if (pt.guiId == 1) {
                        focusGUIrenderEntity!!.gui[0]
                    } else if (pt.guiId == 2) {
                        focusGUIrenderEntity!!.gui[1]
                    } else {
                        focusGUIrenderEntity!!.gui[2]
                    }
                    if (ui == null) {
                        i++
                        continue
                    }
                    ClearFocus()
                    focusGUIent = ent
                    focusUI = ui
                    if (oldFocus !== ent) {
                        // new activation
                        // going to see if we have anything in inventory a gui might be interested in
                        // need to enumerate inventory items
                        focusUI!!.SetStateInt("inv_count", inventory.items.Num())
                        j = 0
                        while (j < inventory.items.Num()) {
                            val item = inventory.items[j]
                            val iname = item.GetString("inv_name")
                            val iicon = item.GetString("inv_icon")
                            val itext = item.GetString("inv_text")
                            focusUI!!.SetStateString(Str.va("inv_name_%d", j), iname)
                            focusUI!!.SetStateString(Str.va("inv_icon_%d", j), iicon)
                            focusUI!!.SetStateString(Str.va("inv_text_%d", j), itext)
                            kv = item.MatchPrefix("inv_id", null)
                            if (kv != null) {
                                focusUI!!.SetStateString(Str.va("inv_id_%d", j), kv.GetValue().toString())
                            }
                            focusUI!!.SetStateInt(iname, 1)
                            j++
                        }
                        j = 0
                        while (j < inventory.pdaSecurity.size()) {
                            val p = inventory.pdaSecurity[j].toString()
                            if (p.isNotEmpty()) {
                                focusUI!!.SetStateInt(p, 1)
                            }
                            j++
                        }
                        val staminapercentage = (100.0f * stamina / SysCvar.pm_stamina.GetFloat()).toInt()
                        focusUI!!.SetStateString("player_health", Str.va("%d", health))
                        focusUI!!.SetStateString("player_stamina", Str.va("%d%%", staminapercentage))
                        focusUI!!.SetStateString("player_armor", Str.va("%d%%", inventory.armor))
                        kv = focusGUIent!!.spawnArgs.MatchPrefix("gui_parm", null)
                        while (kv != null) {
                            focusUI!!.SetStateString(kv.GetKey().toString(), kv.GetValue().toString())
                            kv = focusGUIent!!.spawnArgs.MatchPrefix("gui_parm", kv)
                        }
                    }

                    // clamp the mouse to the corner
                    ev = idLib.sys.GenerateMouseMoveEvent(-2000, -2000)
                    command = focusUI!!.HandleEvent(ev, gameLocal.time)
                    HandleGuiCommands(focusGUIent, command)

                    // move to an absolute position
                    ev = idLib.sys.GenerateMouseMoveEvent(
                        (pt.x * RenderSystem.SCREEN_WIDTH).toInt(), (pt.y * RenderSystem.SCREEN_HEIGHT).toInt()
                    )
                    command = focusUI!!.HandleEvent(ev, gameLocal.time)
                    HandleGuiCommands(focusGUIent, command)
                    focusTime = gameLocal.time + FOCUS_GUI_TIME
                    break
                }
                i++
            }
            if (focusGUIent != null && focusUI != null) {
                if (oldFocus == null || oldFocus != focusGUIent) {
                    // DG: tell the old UI it isn't focused anymore
                    if (oldFocus != null && oldUI != null) {
                        command = oldUI.Activate(false, gameLocal.time)
                    }
                    command = focusUI!!.Activate(true, gameLocal.time)
                    HandleGuiCommands(focusGUIent, command)
                    StartSound("snd_guienter", gameSoundChannel_t.SND_CHANNEL_ANY, 0, false)
                    // HideTip();
                    // HideObjective();
                }
            } else if (oldFocus != null && oldUI != null) {
                command = oldUI.Activate(false, gameLocal.time)
                HandleGuiCommands(oldFocus, command)
                StartSound("snd_guiexit", gameSoundChannel_t.SND_CHANNEL_ANY, 0, false)
            }
            if (cursor != null && oldTalkCursor != talkCursor) {
                cursor!!.SetStateInt("talkcursor", talkCursor)
            }
            if (oldChar !== focusCharacter && hud != null) {
                if (focusCharacter != null) {
                    hud!!.SetStateString("npc", focusCharacter!!.spawnArgs.GetString("npc_name", "Joe")!!)
                    hud!!.HandleNamedEvent("showNPC")
                    // HideTip();
                    // HideObjective();
                } else {
                    hud!!.SetStateString("npc", "")
                    hud!!.HandleNamedEvent("hideNPC")
                }
            }
        }

        /*
         ================
         idPlayer::UpdateLocation

         Searches nearby locations
         ================
         */
        private fun UpdateLocation() {
            if (hud != null) {
                val locationEntity = gameLocal.LocationForPoint(GetEyePosition())
                if (locationEntity != null) {
                    hud!!.SetStateString("location", locationEntity.GetLocation())
                } else {
                    hud!!.SetStateString("location", Common.common.GetLanguageDict().GetString("#str_02911"))
                }
            }
        }

        private fun ActiveGui(): idUserInterface? {
            return if (objectiveSystemOpen) {
                objectiveSystem
            } else focusUI
        }

        private fun UpdatePDAInfo(updatePDASel: Boolean) {
            var sel: Int
            if (objectiveSystem == null) {
                return
            }

            assert(hud != null)

            var currentPDA = objectiveSystem!!.State().GetInt("listPDA_sel_0", "0")
            if (currentPDA == -1) {
                currentPDA = 0
            }

            if (updatePDASel) {
                objectiveSystem!!.SetStateInt("listPDAVideo_sel_0", 0)
                objectiveSystem!!.SetStateInt("listPDAEmail_sel_0", 0)
                objectiveSystem!!.SetStateInt("listPDAAudio_sel_0", 0)
            }
            if (currentPDA > 0) {
                currentPDA = inventory.pdas.size() - currentPDA
            }

            // Mark in the bit array that this pda has been read
            if (currentPDA < 128) {
                inventory.pdasViewed[currentPDA shr 5] =
                    inventory.pdasViewed[currentPDA shr 5] or (1 shl (currentPDA and 31))
            }

            pdaAudio.set("")
            pdaVideo.set("")
            pdaVideoWave.set("")
            var name: String
            var data: String
            for (j in 0 until MAX_PDAS) {
                objectiveSystem!!.SetStateString(Str.va("listPDA_item_%d", j), "")

            }
            for (j in 0 until MAX_PDA_ITEMS) {
                objectiveSystem!!.SetStateString(Str.va("listPDAVideo_item_%d", j), "")
                objectiveSystem!!.SetStateString(Str.va("listPDAAudio_item_%d", j), "")
                objectiveSystem!!.SetStateString(Str.va("listPDAEmail_item_%d", j), "")
                objectiveSystem!!.SetStateString(Str.va("listPDASecurity_item_%d", j), "")
            }

            for (j in 0 until inventory.pdas.size()) {
                val pda = DeclManager.declManager.FindType(declType_t.DECL_PDA, inventory.pdas[j], false) as idDeclPDA?

                if (pda == null) {
                    continue
                }

                var index = inventory.pdas.size() - j
                if (j == 0) {
                    // Special case for the first PDA
                    index = 0
                }

                if (j != currentPDA && j < 128 && (inventory.pdasViewed[j shr 5] and (1 shl (j and 31))) != 0) {
                    // This pda has been read already, mark in gray
                    objectiveSystem!!.SetStateString(
                        Str.va("listPDA_item_%d", index), Str.va(Str.S_COLOR_GRAY, "%s", pda.GetPdaName())
                    )
                } else {
                    // This pda has not been read yet
                    objectiveSystem!!.SetStateString(Str.va("listPDA_item_%d", index), pda.GetPdaName())
                }

                var security = pda.GetSecurity()
                if (j == currentPDA || (currentPDA == 0 && security.isNotEmpty())) {
                    if (security.isEmpty()) {
                        security = Common.common.GetLanguageDict().GetString("#str_00066")
                    }
                    objectiveSystem!!.SetStateString("PDASecurityClearance", security)
                }

                if (j == currentPDA) {

                    objectiveSystem!!.SetStateString("pda_icon", pda.GetIcon())
                    objectiveSystem!!.SetStateString("pda_id", pda.GetID())
                    objectiveSystem!!.SetStateString("pda_title", pda.GetTitle())

                    if (j == 0) {
                        // Selected, personal pda
                        // Add videos
                        if (updatePDASel || !inventory.pdaOpened) {
                            objectiveSystem!!.HandleNamedEvent("playerPDAActive")
                            objectiveSystem!!.SetStateString("pda_personal", "1")
                            inventory.pdaOpened = true
                        }
                        objectiveSystem!!.SetStateString("pda_location", hud!!.State().GetString("location"))
                        objectiveSystem!!.SetStateString("pda_name", CVarSystem.cvarSystem.GetCVarString("ui_name"))
                        AddGuiPDAData(declType_t.DECL_VIDEO, "listPDAVideo", pda, objectiveSystem!!)
                        sel = objectiveSystem!!.State().GetInt("listPDAVideo_sel_0", "0")
                        var vid: idDeclVideo? = null
                        if (sel >= 0 && sel < inventory.videos.size()) {
                            vid = DeclManager.declManager.FindType(
                                declType_t.DECL_VIDEO, inventory.videos[sel], false
                            ) as idDeclVideo?
                        }
                        if (vid != null) {
                            pdaVideo.set(vid.GetRoq())
                            pdaVideoWave.set(vid.GetWave())
                            objectiveSystem!!.SetStateString("PDAVideoTitle", vid.GetVideoName())
                            objectiveSystem!!.SetStateString("PDAVideoVid", vid.GetRoq())
                            objectiveSystem!!.SetStateString("PDAVideoIcon", vid.GetPreview())
                            objectiveSystem!!.SetStateString("PDAVideoInfo", vid.GetInfo())
                        } else {
                            //FIXME: need to precache these in the player def
                            objectiveSystem!!.SetStateString("PDAVideoVid", "sound/vo/video/welcome.tga")
                            objectiveSystem!!.SetStateString("PDAVideoIcon", "sound/vo/video/welcome.tga")
                            objectiveSystem!!.SetStateString("PDAVideoTitle", "")
                            objectiveSystem!!.SetStateString("PDAVideoInfo", "")
                        }
                    } else {
                        // Selected, non-personal pda
                        // Add audio logs
                        if (updatePDASel) {
                            objectiveSystem!!.HandleNamedEvent("playerPDANotActive")
                            objectiveSystem!!.SetStateString("pda_personal", "0")
                            inventory.pdaOpened = true
                        }
                        objectiveSystem!!.SetStateString("pda_location", pda.GetPost())
                        objectiveSystem!!.SetStateString("pda_name", pda.GetFullName())
                        val audioCount = AddGuiPDAData(declType_t.DECL_AUDIO, "listPDAAudio", pda, objectiveSystem!!)
                        objectiveSystem!!.SetStateInt("audioLogCount", audioCount)
                        sel = objectiveSystem!!.State().GetInt("listPDAAudio_sel_0", "0")
                        var aud: idDeclAudio? = null
                        if (sel >= 0) {
                            aud = pda.GetAudioByIndex(sel)
                        }
                        if (aud != null) {
                            pdaAudio.set(aud.GetWave())
                            objectiveSystem!!.SetStateString("PDAAudioTitle", aud.GetAudioName())
                            objectiveSystem!!.SetStateString("PDAAudioIcon", aud.GetPreview())
                            objectiveSystem!!.SetStateString("PDAAudioInfo", aud.GetInfo())
                        } else {
                            objectiveSystem!!.SetStateString("PDAAudioIcon", "sound/vo/video/welcome.tga")
                            objectiveSystem!!.SetStateString("PDAAutioTitle", "")
                            objectiveSystem!!.SetStateString("PDAAudioInfo", "")
                        }
                    }
                    // add emails
                    name = ""
                    data = ""
                    val numEmails = pda.GetNumEmails()
                    if (numEmails > 0) {
                        AddGuiPDAData(declType_t.DECL_EMAIL, "listPDAEmail", pda, objectiveSystem!!)
                        sel = objectiveSystem!!.State().GetInt("listPDAEmail_sel_0", "-1")
                        if (sel >= 0 && sel < numEmails) {
                            val email = pda.GetEmailByIndex(sel)!!
                            name = email.GetSubject()
                            data = email.GetBody()
                        }
                    }
                    objectiveSystem!!.SetStateString("PDAEmailTitle", name)
                    objectiveSystem!!.SetStateString("PDAEmailText", data)
                }
            }
            if (objectiveSystem!!.State().GetInt("listPDA_sel_0", "-1") == -1) {
                objectiveSystem!!.SetStateInt("listPDA_sel_0", 0)
            }
            objectiveSystem!!.StateChanged(gameLocal.time)
        }

        //
        //        private void ExtractEmailInfo(final idStr email, final String scan, idStr out);
        //
        private fun AddGuiPDAData(
            dataType: declType_t, listName: String, src: idDeclPDA, gui: idUserInterface
        ): Int {
            val c: Int
            var work: String
            if (dataType == declType_t.DECL_EMAIL) {
                c = src.GetNumEmails()
                for (i in 0 until c) {
                    val email = src.GetEmailByIndex(i)
                    if (email == null) {
                        work = Str.va("-\tEmail %d not found\t-", i)
                    } else {
                        work = email.GetFrom()
                        work += "\t"
                        work += email.GetSubject()
                        work += "\t"
                        work += email.GetDate()
                    }
                    gui.SetStateString(Str.va("%s_item_%d", listName, i), work)
                }
                return c
            } else if (dataType == declType_t.DECL_AUDIO) {
                c = src.GetNumAudios()
                for (i in 0 until c) {
                    val audio = src.GetAudioByIndex(i)
                    work = if (audio == null) {
                        Str.va("Audio Log %d not found", i)
                    } else {
                        audio.GetAudioName()
                    }
                    gui.SetStateString(Str.va("%s_item_%d", listName, i), work)
                }
                return c
            } else if (dataType == declType_t.DECL_VIDEO) {
                c = inventory.videos.size()
                for (i in 0 until c) {
                    val video = GetVideo(i)
                    work = if (video == null) {
                        Str.va("Video CD %s not found", inventory.videos[i])
                    } else {
                        video.GetVideoName()
                    }
                    gui.SetStateString(Str.va("%s_item_%d", listName, i), work)
                }
                return c
            }
            return 0
        }

        private fun UpdateObjectiveInfo() {
            if (objectiveSystem == null) {
                return
            }
            val objectiveSystem = objectiveSystem!!
            objectiveSystem.SetStateString("objective1", "")
            objectiveSystem.SetStateString("objective2", "")
            objectiveSystem.SetStateString("objective3", "")
            for (i in 0 until inventory.objectiveNames.Num()) {
                objectiveSystem.SetStateString(Str.va("objective%d", i + 1), "1")
                objectiveSystem.SetStateString(
                    Str.va("objectivetitle%d", i + 1), inventory.objectiveNames[i].title.toString()
                )
                objectiveSystem.SetStateString(
                    Str.va("objectivetext%d", i + 1), inventory.objectiveNames[i].text.toString()
                )
                objectiveSystem.SetStateString(
                    Str.va("objectiveshot%d", i + 1), inventory.objectiveNames[i].screenshot.toString()
                )
            }
            objectiveSystem.StateChanged(gameLocal.time)
        }

        private fun UseVehicle() {
            val trace = trace_s()
            val start = idVec3()
            val end = idVec3()
            val ent: idEntity?
            if (GetBindMaster() != null && GetBindMaster() is idAFEntity_Vehicle) {
                Show()
                (GetBindMaster() as idAFEntity_Vehicle).Use(this)
            } else {
                start.set(GetEyePosition())
                end.set(start.plus(viewAngles.ToForward().times(80.0f)))
                gameLocal.clip.TracePoint(trace, start, end, Game_local.MASK_SHOT_RENDERMODEL, this)
                if (trace.fraction < 1.0f) {
                    ent = gameLocal.entities[trace.c.entityNum]
                    if (ent != null && ent is idAFEntity_Vehicle) {
                        Hide()
                        ent.Use(this)
                    }
                }
            }
        }

        private fun Event_GetButtons() {
            idThread.ReturnInt(usercmd.buttons.toInt())
        }

        private fun Event_GetMove() {
            val move = idVec3(usercmd.forwardmove.toInt(), usercmd.rightmove.toInt(), usercmd.upmove.toInt())
            idThread.ReturnVector(move)
        }

        private fun Event_GetViewAngles() {
            idThread.ReturnVector(idVec3(viewAngles[0], viewAngles[1], viewAngles[2]))
        }

        private fun Event_StopFxFov() {
            fxFov = false
        }

        private fun Event_EnableWeapon() {
            hiddenWeapon = gameLocal.world!!.spawnArgs.GetBool("no_Weapons")
            weaponEnabled = true
            if (weapon.GetEntity() != null) {
                weapon.GetEntity()!!.ExitCinematic()
            }
        }

        private fun Event_DisableWeapon() {
            hiddenWeapon = gameLocal.world!!.spawnArgs.GetBool("no_Weapons")
            weaponEnabled = false
            if (weapon.GetEntity() != null) {
                weapon.GetEntity()!!.EnterCinematic()
            }
        }

        private fun Event_GetCurrentWeapon() {
            val weapon: String
            if (currentWeapon >= 0) {
                weapon = spawnArgs.GetString(Str.va("def_weapon%d", currentWeapon))
                idThread.ReturnString(weapon)
            } else {
                idThread.ReturnString("")
            }
        }

        private fun Event_GetPreviousWeapon() {
            val weapon: String
            if (previousWeapon >= 0) {
                val pw = if (gameLocal.world!!.spawnArgs.GetBool("no_Weapons")) 0 else previousWeapon
                weapon = spawnArgs.GetString(Str.va("def_weapon%d", pw))
                idThread.ReturnString(weapon)
            } else {
                idThread.ReturnString(spawnArgs.GetString("def_weapon0"))
            }
        }

        private fun Event_SelectWeapon(weaponName: idEventArg<String>) {
            var i: Int
            var weaponNum: Int
            if (gameLocal.isClient) {
                gameLocal.Warning("Cannot switch weapons from script in multiplayer")
                return
            }
            if (hiddenWeapon && gameLocal.world!!.spawnArgs.GetBool("no_Weapons")) {
                idealWeapon = weapon_fists
                weapon.GetEntity()!!.HideWeapon()
                return
            }
            weaponNum = -1
            i = 0
            while (i < MAX_WEAPONS) {
                if ((inventory.weapons and (1 shl i)) != 0) {
                    val weap = spawnArgs.GetString(Str.va("def_weapon%d", i))
                    if (idStr.Cmp(weap, weaponName.value) == 0) {
                        weaponNum = i
                        break
                    }
                }
                i++
            }
            if (weaponNum < 0) {
                gameLocal.Warning("%s is not carrying weapon '%s'", name, weaponName.value)
                return
            }
            hiddenWeapon = false
            idealWeapon = weaponNum
            UpdateHudWeapon()
        }

        private fun Event_GetWeaponEntity() {
            idThread.ReturnEntity(weapon.GetEntity())
        }

        //
        //        private void Event_PDAAvailable();
        //
        private fun Event_OpenPDA() {
            if (!gameLocal.isMultiplayer) {
                TogglePDA()
            }
        }

        private fun Event_InPDA() {
            idThread.ReturnInt(objectiveSystemOpen)
        }

        private fun Event_ExitTeleporter() {
            val exitEnt: idEntity?
            val pushVel: Float

            // verify and setup
            exitEnt = teleportEntity.GetEntity()
            if (null == exitEnt) {
                Common.common.DPrintf("Event_ExitTeleporter player %d while not being teleported\n", entityNumber)
                return
            }
            pushVel = exitEnt.spawnArgs.GetFloat("push", "300")
            if (gameLocal.isServer) {
                ServerSendEvent(EVENT_EXIT_TELEPORTER, null, false, -1)
            }
            SetPrivateCameraView(null)
            // setup origin and push according to the exit target
            SetOrigin(exitEnt.GetPhysics().GetOrigin().plus(idVec3(0.0f, 0.0f, CM_CLIP_EPSILON)))
            SetViewAngles(exitEnt.GetPhysics().GetAxis().ToAngles())
            physicsObj.SetLinearVelocity(exitEnt.GetPhysics().GetAxis()[0].times(pushVel))
            physicsObj.ClearPushedVelocity()
            // teleport fx
            playerView.Flash(colorWhite, 120)

            // clear the ik heights so model doesn't appear in the wrong place
            walkIK.EnableAll()
            UpdateVisuals()
            StartSound("snd_teleport_exit", gameSoundChannel_t.SND_CHANNEL_ANY, 0, false)
            if (teleportKiller != -1) {
                // we got killed while being teleported
                Damage(
                    gameLocal.entities[teleportKiller],
                    gameLocal.entities[teleportKiller],
                    vec3_origin,
                    "damage_telefrag",
                    1.0f,
                    Model.INVALID_JOINT
                )
                teleportKiller = -1
            } else {
                // kill anything that would have waited at teleport exit
                gameLocal.KillBox(this)
            }
            teleportEntity.oSet(null)
        }

        private fun Event_HideTip() {
            HideTip()
        }

        private fun Event_LevelTrigger() {
            val mapName = idStr(gameLocal.GetMapName())
            mapName.StripPath()
            mapName.StripFileExtension()
            for (i in inventory.levelTriggers.Num() - 1 downTo 0) {
                if (idStr.Icmp(mapName, inventory.levelTriggers[i].levelName) == 0) {
                    val ent = gameLocal.FindEntity(inventory.levelTriggers[i].triggerName)!!
                    ent.PostEventMS(EV_Activate, 1, this)
                }
            }
        }

        private fun Event_Gibbed() {}

        // D3XP event handlers
        private fun Event_GiveInventoryItem(name: idEventArg<String>) {
            GiveInventoryItem(name.value as String)
        }

        private fun Event_RemoveInventoryItem(name: idEventArg<String>) {
            RemoveInventoryItem(name.value as String)
        }

        private fun Event_SetPowerupTime(powerup: idEventArg<Int>, time: idEventArg<Int>) {
            val p = powerup.value as Int
            val t = time.value as Int
            if (t > 0) {
                GivePowerUp(p, t)
            } else {
                ClearPowerup(p)
            }
        }

        private fun Event_IsPowerupActive(powerup: idEventArg<Int>) {
            idThread.ReturnInt(if (PowerUpActive(powerup.value as Int)) 1 else 0)
        }

        private fun Event_WeaponAvailable(name: idEventArg<String>) {
            idThread.ReturnInt(if (WeaponAvailable(name.value as String)) 1 else 0)
        }

        private fun Event_StartWarp() {
            playerView.AddWarp(
                idVec3(0f, 0f, 0f),
                (RenderSystem.SCREEN_WIDTH / 2).toFloat(),
                (RenderSystem.SCREEN_HEIGHT / 2).toFloat(),
                100f, 1000f
            )
        }

        private fun Event_StopHelltime(mode: idEventArg<Int>) {
            StopHelltime(mode.value as Int == 1)
        }

        private fun Event_ToggleBloom(on: idEventArg<Int>) {
            bloomEnabled = (on.value as Int) != 0
        }

        private fun Event_SetBloomParms(speed: idEventArg<Float>, intensity: idEventArg<Float>) {
            bloomSpeed = speed.value as Float
            bloomIntensity = intensity.value as Float
        }

        // D3XP methods
        fun WeaponAvailable(name: String): Boolean {
            for (i in 0 until MAX_WEAPONS) {
                if (inventory.weapons and (1 shl i) != 0) {
                    val weap = spawnArgs.GetString(Str.va("def_weapon%d", i))
                    if (idStr.Cmp(weap, name) == 0) {
                        return true
                    }
                }
            }
            return false
        }

        fun StopHelltime(quick: Boolean = true) {
            if (!PowerUpActive(HELLTIME)) {
                return
            }
            if (PowerUpActive(INVULNERABILITY)) {
                ClearPowerup(INVULNERABILITY)
            }
            if (PowerUpActive(BERSERK)) {
                ClearPowerup(BERSERK)
            }
            if (PowerUpActive(HELLTIME)) {
                ClearPowerup(HELLTIME)
            }
            StopSound(gameSoundChannel_t.SND_CHANNEL_DEMONIC.ordinal, false)
            if (quick) {
                gameLocal.QuickSlowmoReset()
            }
        }

        fun UpdatePowerupHud() {
            if (health <= 0) {
                return
            }
            if (lastHudPowerup != hudPowerup) {
                if (hudPowerup == -1) {
                    hud?.HandleNamedEvent("noPowerup")
                } else {
                    hud?.HandleNamedEvent("Powerup")
                }
                lastHudPowerup = hudPowerup
            }
            if (hudPowerup != -1) {
                if (PowerUpActive(hudPowerup)) {
                    val remaining = inventory.powerupEndTime[hudPowerup] - gameLocal.time
                    val filledbar = idMath.ClampInt(0, hudPowerupDuration, remaining)
                    hud?.SetStateInt("player_powerup", 100 * filledbar / hudPowerupDuration)
                    hud?.SetStateInt("player_poweruptime", remaining / 1000)
                }
            }
        }

        fun StartHealthRecharge(speed: Int) {
            lastHealthRechargeTime = gameLocal.time
            healthRecharge = true
            rechargeSpeed = speed
        }

        fun StopHealthRecharge() {
            healthRecharge = false
        }

        fun GetCurrentWeapon(): String {
            return if (currentWeapon >= 0) {
                spawnArgs.GetString(Str.va("def_weapon%d", currentWeapon))
            } else {
                ""
            }
        }

        // D3XP: CTF flag handling (full implementation in T8)
        fun DropFlag() {
            if (!carryingFlag || !gameLocal.isMultiplayer) {
                return
            }
            // TODO T8: full CTF flag drop via mpGame.GetTeamFlag
            carryingFlag = false
        }

        fun ReturnFlag() {
            if (!carryingFlag || !gameLocal.isMultiplayer) {
                return
            }
            // TODO T8: full CTF flag return via mpGame.GetTeamFlag
            carryingFlag = false
        }

        private fun Event_GetIdealWeapon() {
            val weapon: String
            if (idealWeapon >= 0) {
                weapon = spawnArgs.GetString(Str.va("def_weapon%d", idealWeapon))
                idThread.ReturnString(weapon)
            } else {
                idThread.ReturnString("")
            }
        }

        override fun GetType(): idTypeInfo = Type
        override fun CreateInstance(): idClass = idPlayer()

        override fun getEventCallBack(event: idEventDef): eventCallback_t<*>? {
            return eventCallbacks[event]
        }

        //
        //
        init {
            usercmd = usercmd_t() //memset( &usercmd, 0, sizeof( usercmd ) );
            playerView = idPlayerView()
            noclip = false
            godmode = false
            spawnAnglesSet = false
            spawnAngles.set(ang_zero)
            viewAngles.set(ang_zero)
            cmdAngles.set(ang_zero)
            oldButtons = 0
            buttonMask = 0
            oldFlags = 0
            lastHitTime = 0
            lastSndHitTime = 0
            lastSavingThrowTime = 0
            inventory = idInventory()
            weapon = idEntityPtr()
            hud = null
            objectiveSystem = null
            objectiveSystemOpen = false
            heartRate = BASE_HEARTRATE
            heartInfo = idInterpolate(0f)
            heartInfo.Init(0.0f, 0.0f, 0.0f, 0.0f)
            lastHeartAdjust = 0
            lastHeartBeat = 0
            lastDmgTime = 0
            deathClearContentsTime = 0
            lastArmorPulse = -10000
            stamina = 0.0f
            healthPool = 0.0f
            nextHealthPulse = 0
            healthPulse = false
            nextHealthTake = 0
            healthTake = false
            scoreBoardOpen = false
            forceScoreBoard = false
            forceRespawn = false
            spectating = false
            spectator = 0
            colorBar = vec3_zero
            colorBarIndex = 0
            forcedReady = false
            wantSpectate = false
            lastHitToggle = false
            minRespawnTime = 0
            maxRespawnTime = 0
            firstPersonViewOrigin = vec3_zero
            firstPersonViewAxis.set(idMat3.getMat3_identity())
            dragEntity = idDragEntity()
            physicsObj = idPhysics_Player()
            aasLocation = idList()
            hipJoint = Model.INVALID_JOINT
            chestJoint = Model.INVALID_JOINT
            headJoint = Model.INVALID_JOINT
            bobFoot = 0
            bobFrac = 0.0f
            bobfracsin = 0.0f
            bobCycle = 0
            xyspeed = 0.0f
            stepUpTime = 0
            stepUpDelta = 0.0f
            idealLegsYaw = 0.0f
            legsYaw = 0.0f
            legsForward = true
            oldViewYaw = 0.0f
            viewBobAngles = idAngles()
            viewBob = vec3_zero
            landChange = 0
            landTime = 0
            currentWeapon = -1
            idealWeapon = -1
            previousWeapon = -1
            weaponSwitchTime = 0
            weaponEnabled = true
            weapon_soulcube = -1
            weapon_pda = -1
            weapon_fists = -1
            showWeaponViewModel = true
            skin = null
            powerUpSkin = null
            baseSkinName = idStr("")
            numProjectilesFired = 0
            numProjectileHits = 0
            airless = false
            airTics = 0
            lastAirDamage = 0
            gibDeath = false
            gibsLaunched = false
            gibsDir = vec3_zero
            zoomFov = idInterpolate(0f)
            zoomFov.Init(0.0f, 0.0f, 0.0f, 0.0f)
            centerView = idInterpolate(0f)
            centerView.Init(0.0f, 0.0f, 0.0f, 0.0f)
            fxFov = false
            influenceFov = 0.0f
            influenceActive = 0
            influenceRadius = 0.0f
            influenceEntity = null
            influenceMaterial = null
            influenceSkin = null
            privateCameraView = null

//	memset( loggedViewAngles, 0, sizeof( loggedViewAngles ) );
            loggedViewAngles = Array(NUM_LOGGED_VIEW_ANGLES) { idAngles() }
            //	memset( loggedAccel, 0, sizeof( loggedAccel ) );
            loggedAccel = Array(NUM_LOGGED_ACCELS) { loggedAccel_t() }
            currentLoggedAccel = 0
            focusTime = 0
            focusGUIent = null
            focusUI = null
            focusCharacter = null
            talkCursor = 0
            focusVehicle = null
            cursor = null
            oldMouseX = 0
            oldMouseY = 0
            pdaAudio = idStr("")
            pdaVideo = idStr("")
            pdaVideoWave = idStr("")
            lastDamageDef = 0
            lastDamageDir = vec3_zero
            lastDamageLocation = 0
            smoothedFrame = 0
            smoothedOriginUpdated = false
            smoothedOrigin = vec3_zero
            smoothedAngles = idAngles()
            fl.networkSync = true
            latchedTeam = -1
            doingDeathSkin = false
            weaponGone = false
            useInitialSpawns = false
            tourneyRank = 0
            lastSpectateTeleport = 0
            tourneyLine = 0
            hiddenWeapon = false
            tipUp = false
            objectiveUp = false
            teleportEntity = idEntityPtr()
            teleportKiller = -1
            respawning = false
            ready = false
            leader = false
            lastSpectateChange = 0
            lastTeleFX = -9999
            weaponCatchup = false
            lastSnapshotSequence = 0
            MPAim = -1
            lastMPAim = -1
            lastMPAimTime = 0
            MPAimFadeTime = 0
            MPAimHighlight = false
            spawnedTime = 0
            lastManOver = false
            lastManPlayAgain = false
            lastManPresent = false
            isTelefragged = false
            isLagged = false
            isChatting = false
            selfSmooth = false
            soulCubeProjectile = idEntityPtr()
        }
    }
}
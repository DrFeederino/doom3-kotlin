/*
 * Copyright (C) 1999-2011 id Software LLC, a ZeniMax Media company.
 * Translated to Kotlin by Dr. Feederino with support of Claude Code
 *
 * This file is part of the Doom 3 Kotlin project.
 * Original source: neo/Game/PlayerView.cpp, neo/Game/PlayerView.h
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

import neo.Game.GameSys.SaveGame.idRestoreGame
import neo.Game.GameSys.SaveGame.idSaveGame
import neo.Game.GameSys.SysCvar
import neo.Game.Player.idPlayer
import neo.Renderer.Material
import neo.Renderer.RenderSystem.SCREEN_HEIGHT
import neo.Renderer.RenderSystem.SCREEN_WIDTH
import neo.Renderer.RenderSystem.renderSystem
import neo.Renderer.RenderWorld.renderView_s
import neo.framework.CVarSystem.cvarSystem
import neo.framework.DeclManager
import neo.idlib.Dict_h.idDict
import neo.idlib.Text.Str.idStr
import neo.idlib.colorWhite
import neo.idlib.containers.CInt
import neo.idlib.idLib
import neo.idlib.math.*
import neo.idlib.math.Matrix.idMat3
import neo.ui.UserInterface.idUserInterface
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

object PlayerView {
    const val IMPULSE_DELAY = 150
    const val MAX_SCREEN_BLOBS = 8

    /*
     ===============================================================================

     Player view.

     ===============================================================================
     */
    // screenBlob_t is for the on-screen damage claw marks, etc
    class screenBlob_t {
        var driftAmount = 0.0f
        var finishTime = 0
        var material: Material.idMaterial? = null
        var s1 = 0.0f
        var t1 = 0.0f
        var s2 = 0.0f
        var t2 = 0.0f
        var startFadeTime = 0
        var x = 0.0f
        var y = 0.0f
        var w = 0.0f
        var h = 0.0f
    }

    class idPlayerView {
        private val screenBlobs: Array<screenBlob_t> = Array(MAX_SCREEN_BLOBS) { screenBlob_t() }
        private var armorMaterial // armor damage view effect
                : Material.idMaterial?
        private var berserkMaterial // berserk effect
                : Material.idMaterial?
        private var bfgMaterial // when targeted with BFG
                : Material.idMaterial?

        //
        private var bfgVision: Boolean
        private var bloodSprayMaterial // blood spray
                : Material.idMaterial?

        //
        private var dvFinishTime // double vision will be stopped at this time
                : Int
        private var dvMaterial // material to take the double vision screen shot
                : Material.idMaterial?

        //
        private val fadeColor // fade color
                : idVec4
        private val fadeFromColor // color to fade from
                : idVec4
        private var fadeRate // fade rate
                : Float
        private var fadeTime // fade time
                : Int
        private val fadeToColor // color to fade to
                : idVec4
        private var irGogglesMaterial // ir effect
                : Material.idMaterial?
        private val kickAngles: idAngles

        //
        private var kickFinishTime // view kick will be stopped at this time
                : Int
        private val lagoMaterial // lagometer drawing
                : Material.idMaterial?
        private var lastDamageTime // accentuate the tunnel effect for a while
                : Float

        //
        private var player: idPlayer?

        //
        private val shakeAng // from the sound sources
                : idAngles

        //
        private var tunnelMaterial // health tunnel vision
                : Material.idMaterial?
        private val view: renderView_s
        fun Save(savefile: idSaveGame) {
            for (i in 0 until MAX_SCREEN_BLOBS) {
                val blob = screenBlobs[i]
                savefile.WriteMaterial(blob.material)
                savefile.WriteFloat(blob.x)
                savefile.WriteFloat(blob.y)
                savefile.WriteFloat(blob.w)
                savefile.WriteFloat(blob.h)
                savefile.WriteFloat(blob.s1)
                savefile.WriteFloat(blob.t1)
                savefile.WriteFloat(blob.s2)
                savefile.WriteFloat(blob.t2)
                savefile.WriteInt(blob.finishTime)
                savefile.WriteInt(blob.startFadeTime)
                savefile.WriteFloat(blob.driftAmount)
            }
            savefile.WriteInt(dvFinishTime)
            savefile.WriteMaterial(dvMaterial)
            savefile.WriteInt(kickFinishTime)
            savefile.WriteAngles(kickAngles)
            savefile.WriteBool(bfgVision)
            savefile.WriteMaterial(tunnelMaterial)
            savefile.WriteMaterial(armorMaterial)
            savefile.WriteMaterial(berserkMaterial)
            savefile.WriteMaterial(irGogglesMaterial)
            savefile.WriteMaterial(bloodSprayMaterial)
            savefile.WriteMaterial(bfgMaterial)
            savefile.WriteFloat(lastDamageTime)
            savefile.WriteVec4(fadeColor)
            savefile.WriteVec4(fadeToColor)
            savefile.WriteVec4(fadeFromColor)
            savefile.WriteFloat(fadeRate)
            savefile.WriteInt(fadeTime)
            savefile.WriteAngles(shakeAng)
            savefile.WriteObject(player)
            savefile.WriteRenderView(view)
        }

        fun Restore(savefile: idRestoreGame) {
            for (i in 0 until MAX_SCREEN_BLOBS) {
                val blob = screenBlobs[i]
                blob.material = savefile.ReadMaterial()
                blob.x = savefile.ReadFloat()
                blob.y = savefile.ReadFloat()
                blob.w = savefile.ReadFloat()
                blob.h = savefile.ReadFloat()
                blob.s1 = savefile.ReadFloat()
                blob.t1 = savefile.ReadFloat()
                blob.s2 = savefile.ReadFloat()
                blob.t2 = savefile.ReadFloat()
                blob.finishTime = savefile.ReadInt()
                blob.startFadeTime = savefile.ReadInt()
                blob.driftAmount = savefile.ReadFloat()
            }
            dvFinishTime = savefile.ReadInt()
            dvMaterial = savefile.ReadMaterial()
            kickFinishTime = savefile.ReadInt()
            savefile.ReadAngles(kickAngles)
            bfgVision = savefile.ReadBool()
            tunnelMaterial = savefile.ReadMaterial()
            armorMaterial = savefile.ReadMaterial()
            berserkMaterial = savefile.ReadMaterial()
            irGogglesMaterial = savefile.ReadMaterial()
            bloodSprayMaterial = savefile.ReadMaterial()
            bfgMaterial = savefile.ReadMaterial()
            lastDamageTime = savefile.ReadFloat()
            savefile.ReadVec4(fadeColor)
            savefile.ReadVec4(fadeToColor)
            savefile.ReadVec4(fadeFromColor)
            fadeRate = savefile.ReadFloat()
            fadeTime = savefile.ReadInt()
            savefile.ReadAngles(shakeAng)
            player = savefile.ReadObject() as idPlayer?
            savefile.ReadRenderView(view)
        }

        fun SetPlayerEntity(playerEnt: idPlayer) {
            player = playerEnt
        }

        fun ClearEffects() {
            lastDamageTime = MS2SEC((Game_local.gameLocal.time - 99999).toFloat())
            dvFinishTime = Game_local.gameLocal.time - 99999
            kickFinishTime = Game_local.gameLocal.time - 99999
            for (i in 0 until MAX_SCREEN_BLOBS) {
                screenBlobs[i].finishTime = Game_local.gameLocal.time
            }
            fadeTime = 0
            bfgVision = false
        }

        /*
         ==============
         idPlayerView::DamageImpulse

         LocalKickDir is the direction of force in the player's coordinate system,
         which will determine the head kick direction
         ==============
         */
        fun DamageImpulse(localKickDir: idVec3, damageDef: idDict) {
            if (SysCvar.g_hitEffect.GetBool()) {
                //
                // double vision effect
                //
                if (lastDamageTime > 0.0f && SEC2MS(lastDamageTime) + IMPULSE_DELAY > Game_local.gameLocal.time) {
                    // keep shotgun from obliterating the view
                    return
                }
                val dvTime = damageDef.GetFloat("dv_time")
                if (dvTime != 0.0f) {
                    if (dvFinishTime < Game_local.gameLocal.time) {
                        dvFinishTime = Game_local.gameLocal.time
                    }
                    dvFinishTime += (SysCvar.g_dvTime.GetFloat() * dvTime).toInt()
                    // don't let it add up too much in god mode
                    if (dvFinishTime > Game_local.gameLocal.time + 5000) {
                        dvFinishTime = Game_local.gameLocal.time + 5000
                    }
                }

                //
                // head angle kick
                //
                val kickTime = damageDef.GetFloat("kick_time")
                if (kickTime != 0.0f) {
                    kickFinishTime = (Game_local.gameLocal.time + SysCvar.g_kickTime.GetFloat() * kickTime).toInt()

                    // forward / back kick will pitch view
                    kickAngles[0] = localKickDir[0]

                    // side kick will yaw view
                    kickAngles[1] = localKickDir[1] * 0.5f

                    // up / down kick will pitch view
                    kickAngles.plusAssign(0, localKickDir[2])

                    // roll will come from  side
                    kickAngles[2] = localKickDir[1]
                    val kickAmplitude = damageDef.GetFloat("kick_amplitude")
                    if (kickAmplitude != 0.0f) {
                        kickAngles.timesAssign(kickAmplitude)
                    }
                }

                //
                // screen blob
                //
                val blobTime = damageDef.GetFloat("blob_time")
                if (blobTime != 0.0f) {
                    val blob = GetScreenBlob()
                    blob.startFadeTime = Game_local.gameLocal.time
                    blob.finishTime = (Game_local.gameLocal.time + blobTime * SysCvar.g_blobTime.GetFloat()).toInt()
                    val materialName = damageDef.GetString("mtr_blob")
                    blob.material = DeclManager.declManager.FindMaterial(materialName)
                    blob.x = damageDef.GetFloat("blob_x")
                    blob.x += ((Game_local.gameLocal.random.RandomInt() and 63) - 32).toFloat()
                    blob.y = damageDef.GetFloat("blob_y")
                    blob.y += ((Game_local.gameLocal.random.RandomInt() and 63) - 32).toFloat()
                    val scale = (256 + ((Game_local.gameLocal.random.RandomInt() and 63) - 32)) / 256.0f
                    blob.w = damageDef.GetFloat("blob_width") * SysCvar.g_blobSize.GetFloat() * scale
                    blob.h = damageDef.GetFloat("blob_height") * SysCvar.g_blobSize.GetFloat() * scale
                    blob.s1 = 0.0f
                    blob.t1 = 0.0f
                    blob.s2 = 1.0f
                    blob.t2 = 1.0f
                }

                //
                // save lastDamageTime for tunnel vision accentuation
                //
                lastDamageTime = MS2SEC(Game_local.gameLocal.time.toFloat())
            }
        }

        /*
         ==================
         idPlayerView::WeaponFireFeedback

         Called when a weapon fires, generates head twitches, etc
         ==================
         */
        fun WeaponFireFeedback(weaponDef: idDict) {
            val recoilTime: Int
            recoilTime = weaponDef.GetInt("recoilTime")
            // don't shorten a damage kick in progress
            if (recoilTime != 0 && kickFinishTime < Game_local.gameLocal.time) {
                val angles = idAngles()
                weaponDef.GetAngles("recoilAngles", "5 0 0", angles)
                kickAngles.set(angles)
                val finish = (Game_local.gameLocal.time + SysCvar.g_kickTime.GetFloat() * recoilTime).toInt()
                kickFinishTime = finish
            }
        }

        /*
         ===================
         idPlayerView::AngleOffset

         kickVector, a world space direction that the attack should 
         ===================
         */
        fun AngleOffset(): idAngles {            // returns the current kick angle
            val ang: idAngles = idAngles()
            ang.Zero()
            if (Game_local.gameLocal.time < kickFinishTime) {
                val offset = (kickFinishTime - Game_local.gameLocal.time).toFloat()
                ang.set(kickAngles * offset * offset * SysCvar.g_kickAmplitude.GetFloat())
                for (i in 0 until 3) {
                    if (ang[i] > 70.0f) {
                        ang[i] = 70.0f
                    } else if (ang[i] < -70.0f) {
                        ang[i] = -70.0f
                    }
                }
            }
            return ang
        }

        fun ShakeAxis(): idMat3 {            // returns the current shake angle
            return shakeAng.ToMat3()
        }

        fun CalculateShake() {
//            idVec3 origin, matrix;
            val shakeVolume = Game_local.gameSoundWorld!!.CurrentShakeAmplitudeForPosition(
                Game_local.gameLocal.time,
                player!!.firstPersonViewOrigin
            )
            //
            // shakeVolume should somehow be molded into an angle here
            // it should be thought of as being in the range 0.0f . 1.0f, although
            // since CurrentShakeAmplitudeForPosition() returns all the shake sounds
            // the player can hear, it can go over 1.0f too.
            //
            shakeAng[0] = Game_local.gameLocal.random.CRandomFloat() * shakeVolume
            shakeAng[1] = Game_local.gameLocal.random.CRandomFloat() * shakeVolume
            shakeAng[2] = Game_local.gameLocal.random.CRandomFloat() * shakeVolume
        }

        // this may involve rendering to a texture and displaying
        // that with a warp model or in double vision mode
        fun RenderPlayerView(hud: idUserInterface) {
            val view = player!!.GetRenderView()
            if (SysCvar.g_skipViewEffects.GetBool()) {
                SingleView(hud, view)
            } else {
                if (player!!.GetInfluenceMaterial() != null || player!!.GetInfluenceEntity() != null) {
                    InfluenceVision(hud, view)
                } else if (Game_local.gameLocal.time < dvFinishTime) {
                    FloatVision(hud, view, dvFinishTime - Game_local.gameLocal.time)
                } else if (player!!.PowerUpActive(Player.BERSERK)) {
                    BerserkVision(hud, view)
                } else {
                    SingleView(hud, view)
                }
                ScreenFade()
            }
            if (Game_network.net_clientLagOMeter.GetBool() && lagoMaterial != null && Game_local.gameLocal.isClient) {
                //#modified-fva; BEGIN
                var x = 10.0f
                var y = 380.0f
                var w = 64.0f
                var h = 64.0f
                if (cvarSystem.GetCVarBool("cst_hudAdjustAspect")) {
                    // similar to CST_ANCHOR_BOTTOM_LEFT
                    var glWidth: CInt = CInt()
                    var glHeight: CInt = CInt()
                    renderSystem.GetGLSettings(glWidth, glHeight)
                    if (glWidth.integerValue > 0 && glHeight.integerValue > 0) {
                        val glAspectRatio = glWidth.integerValue.toFloat() / glHeight.integerValue.toFloat()

                        val vidWidth = SCREEN_WIDTH
                        val vidHeight = SCREEN_HEIGHT
                        val vidAspectRatio = SCREEN_WIDTH.toFloat() / SCREEN_HEIGHT.toFloat()

                        var modWidth = vidWidth.toFloat()
                        var modHeight = vidHeight.toFloat()
                        if (glAspectRatio >= vidAspectRatio) {
                            modWidth = modHeight * glAspectRatio
                        } else {
                            modHeight = modWidth / glAspectRatio
                        }

                        val xScale = vidWidth / modWidth
                        val yScale = vidHeight / modHeight
                        val xOffset = 0.0f
                        val yOffset = vidHeight * (1.0f - yScale)

                        x = x * xScale + xOffset
                        y = y * yScale + yOffset
                        w *= xScale
                        h *= yScale
                    }
                }
                renderSystem.SetColor4(1.0f, 1.0f, 1.0f, 1.0f)
                renderSystem.DrawStretchPic(x, y, w, h, 0.0f, 0.0f, 1.0f, 1.0f, lagoMaterial)
                //#modified-fva; END
            }
        }

        /*
         =================
         idPlayerView::Fade

         used for level transition fades
         assumes: color.w is 0 or 1
         =================
         */
        fun Fade(color: idVec4, time: Int) {
            var time = time
            if (0 == fadeTime) {
                fadeFromColor.set(0.0f, 0.0f, 0.0f, 1.0f - color[3])
            } else {
                fadeFromColor.set(fadeColor)
            }
            fadeToColor.set(color)
            if (time <= 0) {
                fadeRate = 0.0f
                time = 0
                fadeColor.set(fadeToColor)
            } else {
                fadeRate = 1.0f / time.toFloat()
            }
            fadeTime = if (Game_local.gameLocal.realClientTime == 0 && time == 0) {
                1
            } else {
                Game_local.gameLocal.realClientTime + time
            }
        }

        /*
         =================
         idPlayerView::Flash

         flashes the player view with the given color
         =================
         */
        fun Flash(color: idVec4, time: Int) {
            Fade(idVec4(0.0f, 0.0f, 0.0f, 0.0f), time)
            fadeFromColor.set(colorWhite)
        }

        /*
         ==================
         idPlayerView::AddBloodSpray

         If we need a more generic way to add blobs then we can do that
         but having it localized here lets the material be pre-looked up etc.
         ==================
         */
        fun AddBloodSpray(duration: Float) { //TODO:fix?

        }

        // temp for view testing
        fun EnableBFGVision(b: Boolean) {
            bfgVision = b
        }

        private fun SingleView(hud: idUserInterface, view: renderView_s?) {

            // normal rendering
            if (null == view) {
                return
            }

            // place the sound origin for the player
            Game_local.gameSoundWorld!!.PlaceListener(
                view.vieworg,
                view.viewaxis,
                player!!.entityNumber + 1,
                Game_local.gameLocal.time,
                idStr(if (hud != null) hud.State().GetString("location") else "Undefined")
            )

            // if the objective system is up, don't do normal drawing
            if (player!!.objectiveSystemOpen) {
                player!!.objectiveSystem!!.Redraw(Game_local.gameLocal.time)
                return
            }

            // hack the shake in at the very last moment, so it can't cause any consistency problems
            val hackedView = renderView_s(view)
            hackedView.viewaxis.set(hackedView.viewaxis.times(ShakeAxis()))
            Game_local.gameRenderWorld!!.RenderScene(hackedView)
            if (player!!.spectating) {
                return
            }

            // draw screen blobs
            if (!SysCvar.pm_thirdPerson.GetBool() && !SysCvar.g_skipViewEffects.GetBool()) {
                for (i in 0 until MAX_SCREEN_BLOBS) {
                    val blob = screenBlobs[i]
                    if (blob.finishTime <= Game_local.gameLocal.time) {
                        continue
                    }
                    blob.y += blob.driftAmount
                    var fade: Float =
                        (blob.finishTime - Game_local.gameLocal.time).toFloat() / (blob.finishTime - blob.startFadeTime)
                    if (fade > 1.0f) {
                        fade = 1.0f
                    }
                    if (fade != 0.0f) {
                        renderSystem.SetColor4(1.0f, 1.0f, 1.0f, fade)
                        renderSystem.DrawStretchPic(
                            blob.x,
                            blob.y,
                            blob.w,
                            blob.h,
                            blob.s1,
                            blob.t1,
                            blob.s2,
                            blob.t2,
                            blob.material
                        )
                    }
                }
                player!!.DrawHUD(hud)

                // armor impulse feedback
                val armorPulse = (Game_local.gameLocal.time - player!!.lastArmorPulse) / 250.0f
                if (armorPulse > 0.0f && armorPulse < 1.0f) {
                    renderSystem.SetColor4(1.0f, 1.0f, 1.0f, 1.0f - armorPulse)
                    renderSystem.DrawStretchPic(
                        0.0f,
                        0.0f,
                        640.0f,
                        480.0f,
                        0.0f,
                        0.0f,
                        1.0f,
                        1.0f,
                        armorMaterial
                    )
                }

                // tunnel vision
                val health: Float
                health = if (SysCvar.g_testHealthVision.GetFloat() != 0.0f) {
                    SysCvar.g_testHealthVision.GetFloat()
                } else {
                    player!!.health.toFloat()
                }
                var alpha = health / 100.0f
                if (alpha < 0.0f) {
                    alpha = 0.0f
                }
                if (alpha > 1.0f) {
                    alpha = 1.0f
                }
                if (alpha < 1.0f) {
                    renderSystem.SetColor4(
                        if (player!!.health <= 0.0f) MS2SEC(Game_local.gameLocal.time.toFloat()) else lastDamageTime,
                        1.0f,
                        1.0f,
                        if (player!!.health <= 0.0f) 0.0f else alpha
                    )
                    renderSystem.DrawStretchPic(
                        0.0f,
                        0.0f,
                        640.0f,
                        480.0f,
                        0.0f,
                        0.0f,
                        1.0f,
                        1.0f,
                        tunnelMaterial
                    )
                }
                if (player!!.PowerUpActive(Player.BERSERK)) {
                    val berserkTime = player!!.inventory.powerupEndTime[Player.BERSERK] - Game_local.gameLocal.time
                    if (berserkTime > 0) {
                        // start fading if within 10 seconds of going away
                        alpha = if (berserkTime < 10000) berserkTime.toFloat() / 10000 else 1.0f
                        renderSystem.SetColor4(1.0f, 1.0f, 1.0f, alpha)
                        renderSystem.DrawStretchPic(
                            0.0f,
                            0.0f,
                            640.0f,
                            480.0f,
                            0.0f,
                            0.0f,
                            1.0f,
                            1.0f,
                            berserkMaterial
                        )
                    }
                }
                if (bfgVision) {
                    renderSystem.SetColor4(1.0f, 1.0f, 1.0f, 1.0f)
                    renderSystem.DrawStretchPic(
                        0.0f,
                        0.0f,
                        640.0f,
                        480.0f,
                        0.0f,
                        0.0f,
                        1.0f,
                        1.0f,
                        bfgMaterial
                    )
                }
            }

            // test a single material drawn over everything
            if (!SysCvar.g_testPostProcess.GetString().isNullOrEmpty()) {
                val mtr: Material.idMaterial? =
                    DeclManager.declManager.FindMaterial(SysCvar.g_testPostProcess.GetString()!!, false)
                if (null == mtr) {
                    idLib.common.Printf("Material not found.\n")
                    SysCvar.g_testPostProcess.SetString("")
                } else {
                    renderSystem.SetColor4(1.0f, 1.0f, 1.0f, 1.0f)
                    renderSystem.DrawStretchPic(0.0f, 0.0f, 640.0f, 480.0f, 0.0f, 0.0f, 1.0f, 1.0f, mtr)
                }
            }
        }

        private fun FloatVision(hud: idUserInterface, view: renderView_s?, offset: Int) {
            if (!SysCvar.g_doubleVision.GetBool()) {
                SingleView(hud, view)
                return
            }
            var scale = offset * SysCvar.g_dvAmplitude.GetFloat()
            if (scale > 0.5f) {
                scale = 0.5f
            }
            var shift = (scale * sin(sqrt(offset.toFloat()) * SysCvar.g_dvFrequency.GetFloat())).toFloat()
            shift = abs(shift)

            // if double vision, render to a texture
            renderSystem.CropRenderSize(512, 256, true)
            SingleView(hud, view)
            renderSystem.CaptureRenderToImage("_scratch")
            renderSystem.UnCrop()

            // carry red tint if in berserk mode
            val color = idVec4(1.0f, 1.0f, 1.0f, 1.0f)
            if (Game_local.gameLocal.time < player!!.inventory.powerupEndTime[Player.BERSERK]) {
                color.y = 0.0f
                color.z = 0.0f
            }
            renderSystem.SetColor4(color.x, color.y, color.z, 1.0f)
            renderSystem.DrawStretchPic(
                0.0f,
                0.0f,
                SCREEN_WIDTH.toFloat(),
                SCREEN_HEIGHT.toFloat(),
                shift.toFloat(),
                1.0f,
                1.0f,
                0.0f,
                dvMaterial
            )
            renderSystem.SetColor4(color.x, color.y, color.z, 0.5f)
            renderSystem.DrawStretchPic(
                0.0f,
                0.0f,
                SCREEN_WIDTH.toFloat(),
                SCREEN_HEIGHT.toFloat(),
                0.0f,
                1.0f,
                (1 - shift).toFloat(),
                0.0f,
                dvMaterial
            )
        }

        private fun BerserkVision(hud: idUserInterface, view: renderView_s?) {
            renderSystem.CropRenderSize(512, 256, true)
            SingleView(hud, view)
            renderSystem.CaptureRenderToImage("_scratch")
            renderSystem.UnCrop()
            renderSystem.SetColor4(1.0f, 1.0f, 1.0f, 1.0f)
            renderSystem.DrawStretchPic(
                0.0f,
                0.0f,
                SCREEN_WIDTH.toFloat(),
                SCREEN_HEIGHT.toFloat(),
                0.0f,
                1.0f,
                1.0f,
                0.0f,
                dvMaterial
            )
        }

        private fun InfluenceVision(hud: idUserInterface, view: renderView_s?) {
            val distance: Float
            var pct = 1.0f
            if (player!!.GetInfluenceEntity() != null) {
                distance =
                    player!!.GetInfluenceEntity()!!.GetPhysics().GetOrigin().minus(player!!.GetPhysics().GetOrigin())
                        .Length()
                if (player!!.GetInfluenceRadius() != 0.0f && distance < player!!.GetInfluenceRadius()) {
                    pct = distance / player!!.GetInfluenceRadius()//TODO:wtf?
                    pct = 1.0f - idMath.ClampFloat(0.0f, 1.0f, pct)
                }
            }
            if (player!!.GetInfluenceMaterial() != null) {
                SingleView(hud, view)
                renderSystem.CaptureRenderToImage("_currentRender")
                renderSystem.SetColor4(1.0f, 1.0f, 1.0f, pct)
                renderSystem.DrawStretchPic(
                    0.0f,
                    0.0f,
                    640.0f,
                    480.0f,
                    0.0f,
                    0.0f,
                    1.0f,
                    1.0f,
                    player!!.GetInfluenceMaterial()!!
                )
            } else if (player!!.GetInfluenceEntity() == null) {
                SingleView(hud, view)
                //		return;
            } else {
                val offset = (25 + sin(Game_local.gameLocal.time.toFloat())).toInt()
                FloatVision(hud, view, (pct * offset).toInt())
            }
        }

        private fun ScreenFade() {
            val msec: Int
            val t: Float
            if (0 == fadeTime) {
                return
            }
            msec = fadeTime - Game_local.gameLocal.realClientTime
            if (msec <= 0) {
                fadeColor.set(fadeToColor)
                if (fadeColor[3] == 0.0f) {
                    fadeTime = 0
                }
            } else {
                t = msec.toFloat() * fadeRate
                fadeColor.set(fadeFromColor.times(t).plus(fadeToColor.times(1.0f - t)))
            }
            if (fadeColor[3] != 0.0f) {
                renderSystem.SetColor4(
                    fadeColor[0],
                    fadeColor[1],
                    fadeColor[2],
                    fadeColor[3]
                )
                renderSystem.DrawStretchPic(
                    0.0f,
                    0.0f,
                    640.0f,
                    480.0f,
                    0.0f,
                    0.0f,
                    1.0f,
                    1.0f,
                    DeclManager.declManager.FindMaterial("_white")
                )
            }
        }

        private fun GetScreenBlob(): screenBlob_t {
            var oldest = screenBlobs[0]
            for (i in 1 until MAX_SCREEN_BLOBS) {
                if (screenBlobs[i].finishTime < oldest.finishTime) {
                    oldest = screenBlobs[i]
                }
            }
            return oldest
        }

        //
        //
        init {
//	memset( screenBlobs, 0, sizeof( screenBlobs ) );
//	memset( &view, 0, sizeof( view ) );
            view = renderView_s()
            player = null
            dvMaterial = DeclManager.declManager.FindMaterial("_scratch")
            tunnelMaterial = DeclManager.declManager.FindMaterial("textures/decals/tunnel")
            armorMaterial = DeclManager.declManager.FindMaterial("armorViewEffect")
            berserkMaterial = DeclManager.declManager.FindMaterial("textures/decals/berserk")
            irGogglesMaterial = DeclManager.declManager.FindMaterial("textures/decals/irblend")
            bloodSprayMaterial = DeclManager.declManager.FindMaterial("textures/decals/bloodspray")
            bfgMaterial = DeclManager.declManager.FindMaterial("textures/decals/bfgvision")
            lagoMaterial = DeclManager.declManager.FindMaterial(Game_local.LAGO_MATERIAL, false)
            bfgVision = false
            dvFinishTime = 0
            kickFinishTime = 0
            kickAngles = idAngles()
            lastDamageTime = 0.0f
            fadeTime = 0
            fadeRate = 0.0f
            fadeFromColor = idVec4()
            fadeToColor = idVec4()
            fadeColor = idVec4()
            shakeAng = idAngles()
            ClearEffects()
        }
    }
}
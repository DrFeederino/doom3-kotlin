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
import neo.Game.Game_local.Companion.isD3XP
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

    /*
    ===============================================================================

      D3XP Fullscreen FX System

    ===============================================================================
    */

    // D3XP warp effect polygon
    class WarpPolygon_t {
        val outer1: idVec4 = idVec4()
        val outer2: idVec4 = idVec4()
        val center: idVec4 = idVec4()
    }

    // D3XP warp effect
    class Warp_t {
        var id: Int = 0
        var active: Boolean = false
        var startTime: Int = 0
        var initialRadius: Float = 0f
        val worldOrigin: idVec3 = idVec3()
        val screenOrigin: idVec2 = idVec2()
        var durationMsec: Int = 0
        val polys: MutableList<WarpPolygon_t> = mutableListOf()
    }

    // D3XP FX fader
    class FxFader {
        companion object {
            const val FX_STATE_OFF = 0
            const val FX_STATE_RAMPUP = 1
            const val FX_STATE_RAMPDOWN = 2
            const val FX_STATE_ON = 3
        }

        private var time: Int = 0
        private var state: Int = FX_STATE_OFF
        private var alpha: Float = 0f
        private var msec: Int = 1000

        fun SetTriggerState(active: Boolean): Boolean {
            // handle on/off states
            if (active && state == FX_STATE_OFF) {
                state = FX_STATE_RAMPUP
                time = Game_local.gameLocal.slow.time + msec
            } else if (!active && state == FX_STATE_ON) {
                state = FX_STATE_RAMPDOWN
                time = Game_local.gameLocal.slow.time + msec
            }

            // handle rampup/rampdown states
            if (state == FX_STATE_RAMPUP) {
                if (Game_local.gameLocal.slow.time >= time) {
                    state = FX_STATE_ON
                }
            } else if (state == FX_STATE_RAMPDOWN) {
                if (Game_local.gameLocal.slow.time >= time) {
                    state = FX_STATE_OFF
                }
            }

            // compute alpha
            when (state) {
                FX_STATE_ON -> alpha = 1f
                FX_STATE_OFF -> alpha = 0f
                FX_STATE_RAMPUP -> alpha = 1f - (time - Game_local.gameLocal.slow.time).toFloat() / msec
                FX_STATE_RAMPDOWN -> alpha = (time - Game_local.gameLocal.slow.time).toFloat() / msec
            }

            return alpha > 0f
        }

        fun Save(savefile: idSaveGame) {
            savefile.WriteInt(time)
            savefile.WriteInt(state)
            savefile.WriteFloat(alpha)
            savefile.WriteInt(msec)
        }

        fun Restore(savefile: idRestoreGame) {
            time = savefile.ReadInt()
            state = savefile.ReadInt()
            alpha = savefile.ReadFloat()
            msec = savefile.ReadInt()
        }

        fun SetFadeTime(t: Int) {
            msec = t
        }

        fun GetFadeTime(): Int = msec
        fun GetAlpha(): Float = alpha
    }

    // D3XP fullscreen effect base class
    abstract class FullscreenFX {
        protected var name: idStr = idStr()
        protected var fader: FxFader = FxFader()
        var fxman: FullscreenFXManager? = null

        abstract fun Initialize()
        abstract fun Active(): Boolean
        abstract fun HighQuality()
        open fun LowQuality() {}
        open fun AccumPass(view: renderView_s) {}
        open fun HasAccum(): Boolean = false

        fun SetName(n: idStr) {
            name = n
        }

        fun GetName(): idStr = name

        fun SetFXManager(fx: FullscreenFXManager) {
            fxman = fx
        }

        fun SetTriggerState(state: Boolean): Boolean = fader.SetTriggerState(state)
        fun SetFadeSpeed(msec: Int) {
            fader.SetFadeTime(msec)
        }

        fun GetFadeAlpha(): Float = fader.GetAlpha()

        open fun Save(savefile: idSaveGame) {
            fader.Save(savefile)
        }

        open fun Restore(savefile: idRestoreGame) {
            fader.Restore(savefile)
        }
    }

    // D3XP helltime effect
    class FullscreenFX_Helltime : FullscreenFX() {
        private val acInitMaterials = arrayOfNulls<Material.idMaterial>(3)
        private val acCaptureMaterials = arrayOfNulls<Material.idMaterial>(3)
        private val acDrawMaterials = arrayOfNulls<Material.idMaterial>(3)
        private val crCaptureMaterials = arrayOfNulls<Material.idMaterial>(3)
        private val crDrawMaterials = arrayOfNulls<Material.idMaterial>(3)
        private var clearAccumBuffer: Boolean = false

        private fun DetermineLevel(): Int {
            val player = fxman?.GetPlayer() ?: return -1
            if (player.PowerUpActive(Player.INVULNERABILITY)) return 2
            if (player.PowerUpActive(Player.BERSERK)) return 1
            if (player.PowerUpActive(Player.HELLTIME)) return 0
            return -1
        }

        override fun Initialize() {
            acInitMaterials[0] = DeclManager.declManager.FindMaterial("textures/smf/bloodorb1/ac_init")
            acInitMaterials[1] = DeclManager.declManager.FindMaterial("textures/smf/bloodorb2/ac_init")
            acInitMaterials[2] = DeclManager.declManager.FindMaterial("textures/smf/bloodorb3/ac_init")
            acCaptureMaterials[0] = DeclManager.declManager.FindMaterial("textures/smf/bloodorb1/ac_capture")
            acCaptureMaterials[1] = DeclManager.declManager.FindMaterial("textures/smf/bloodorb2/ac_capture")
            acCaptureMaterials[2] = DeclManager.declManager.FindMaterial("textures/smf/bloodorb3/ac_capture")
            acDrawMaterials[0] = DeclManager.declManager.FindMaterial("textures/smf/bloodorb1/ac_draw")
            acDrawMaterials[1] = DeclManager.declManager.FindMaterial("textures/smf/bloodorb2/ac_draw")
            acDrawMaterials[2] = DeclManager.declManager.FindMaterial("textures/smf/bloodorb3/ac_draw")
            crCaptureMaterials[0] = DeclManager.declManager.FindMaterial("textures/smf/bloodorb1/cr_capture")
            crCaptureMaterials[1] = DeclManager.declManager.FindMaterial("textures/smf/bloodorb2/cr_capture")
            crCaptureMaterials[2] = DeclManager.declManager.FindMaterial("textures/smf/bloodorb3/cr_capture")
            crDrawMaterials[0] = DeclManager.declManager.FindMaterial("textures/smf/bloodorb1/cr_draw")
            crDrawMaterials[1] = DeclManager.declManager.FindMaterial("textures/smf/bloodorb2/cr_draw")
            crDrawMaterials[2] = DeclManager.declManager.FindMaterial("textures/smf/bloodorb3/cr_draw")
            clearAccumBuffer = true
        }

        override fun Active(): Boolean {
            if (Game_local.gameLocal.inCinematic || Game_local.gameLocal.isMultiplayer) return false
            if (DetermineLevel() >= 0) return true
            if (fader.GetAlpha() == 0f) clearAccumBuffer = true
            return false
        }

        override fun HighQuality() {
            var level = DetermineLevel()
            if (level < 0 || level > 2) level = 0

            val shiftScale = fxman!!.GetShiftScale()
            renderSystem.SetColor4(1f, 1f, 1f, 1f)

            renderSystem.DrawStretchPic(
                0f, 0f, SCREEN_WIDTH.toFloat(), SCREEN_HEIGHT.toFloat(), 0f, 1f, 1f, 0f, acDrawMaterials[level]
            )
            renderSystem.DrawStretchPic(
                0f,
                0f,
                SCREEN_WIDTH.toFloat(),
                SCREEN_HEIGHT.toFloat(),
                0f,
                shiftScale.y,
                shiftScale.x,
                0f,
                crDrawMaterials[level]
            )
        }

        override fun AccumPass(view: renderView_s) {
            var level = DetermineLevel()
            if (level < 0 || level > 2) level = 0

            val shiftScale = fxman!!.GetShiftScale()
            renderSystem.SetColor4(1f, 1f, 1f, 1f)

            if (clearAccumBuffer) {
                clearAccumBuffer = false
                renderSystem.DrawStretchPic(
                    0f, 0f, SCREEN_WIDTH.toFloat(), SCREEN_HEIGHT.toFloat(), 0f, 1f, 1f, 0f, acInitMaterials[level]
                )
            } else {
                renderSystem.DrawStretchPic(
                    0f, 0f, SCREEN_WIDTH.toFloat(), SCREEN_HEIGHT.toFloat(), 0f, 1f, 1f, 0f, acCaptureMaterials[level]
                )
                renderSystem.DrawStretchPic(
                    0f,
                    0f,
                    SCREEN_WIDTH.toFloat(),
                    SCREEN_HEIGHT.toFloat(),
                    0f,
                    shiftScale.y,
                    shiftScale.x,
                    0f,
                    crCaptureMaterials[level]
                )
            }

            renderSystem.CaptureRenderToImage("_accum")
        }

        override fun HasAccum(): Boolean = true

        override fun Restore(savefile: idRestoreGame) {
            super.Restore(savefile)
            clearAccumBuffer = true
        }
    }

    // D3XP multiplayer effect
    class FullscreenFX_Multiplayer : FullscreenFX() {
        private var acInitMaterials: Material.idMaterial? = null
        private var acCaptureMaterials: Material.idMaterial? = null
        private var acDrawMaterials: Material.idMaterial? = null
        private var crCaptureMaterials: Material.idMaterial? = null
        private var crDrawMaterials: Material.idMaterial? = null
        private var clearAccumBuffer: Boolean = false

        private fun DetermineLevel(): Int {
            val player = fxman?.GetPlayer() ?: return -1
            if (player.PowerUpActive(Player.INVULNERABILITY)) return 2
            if (player.PowerUpActive(Player.BERSERK)) return 0
            return -1
        }

        override fun Initialize() {
            acInitMaterials = DeclManager.declManager.FindMaterial("textures/smf/multiplayer1/ac_init")
            acCaptureMaterials = DeclManager.declManager.FindMaterial("textures/smf/multiplayer1/ac_capture")
            acDrawMaterials = DeclManager.declManager.FindMaterial("textures/smf/multiplayer1/ac_draw")
            crCaptureMaterials = DeclManager.declManager.FindMaterial("textures/smf/multiplayer1/cr_capture")
            crDrawMaterials = DeclManager.declManager.FindMaterial("textures/smf/multiplayer1/cr_draw")
            clearAccumBuffer = true
        }

        override fun Active(): Boolean {
            if (!Game_local.gameLocal.isMultiplayer) return false
            if (DetermineLevel() >= 0) return true
            if (fader.GetAlpha() == 0f) clearAccumBuffer = true
            return false
        }

        override fun HighQuality() {
            var level = DetermineLevel()
            if (level < 0 || level > 2) level = 0

            val shiftScale = fxman!!.GetShiftScale()
            renderSystem.SetColor4(1f, 1f, 1f, 1f)

            renderSystem.DrawStretchPic(
                0f, 0f, SCREEN_WIDTH.toFloat(), SCREEN_HEIGHT.toFloat(), 0f, 1f, 1f, 0f, acDrawMaterials
            )
            renderSystem.DrawStretchPic(
                0f,
                0f,
                SCREEN_WIDTH.toFloat(),
                SCREEN_HEIGHT.toFloat(),
                0f,
                shiftScale.y,
                shiftScale.x,
                0f,
                crDrawMaterials
            )
        }

        override fun AccumPass(view: renderView_s) {
            var level = DetermineLevel()
            if (level < 0 || level > 2) level = 0

            val shiftScale = fxman!!.GetShiftScale()
            renderSystem.SetColor4(1f, 1f, 1f, 1f)

            if (clearAccumBuffer) {
                clearAccumBuffer = false
                renderSystem.DrawStretchPic(
                    0f, 0f, SCREEN_WIDTH.toFloat(), SCREEN_HEIGHT.toFloat(), 0f, 1f, 1f, 0f, acInitMaterials
                )
            } else {
                renderSystem.DrawStretchPic(
                    0f, 0f, SCREEN_WIDTH.toFloat(), SCREEN_HEIGHT.toFloat(), 0f, 1f, 1f, 0f, acCaptureMaterials
                )
                renderSystem.DrawStretchPic(
                    0f,
                    0f,
                    SCREEN_WIDTH.toFloat(),
                    SCREEN_HEIGHT.toFloat(),
                    0f,
                    shiftScale.y,
                    shiftScale.x,
                    0f,
                    crCaptureMaterials
                )
            }

            renderSystem.CaptureRenderToImage("_accum")
        }

        override fun HasAccum(): Boolean = true

        override fun Restore(savefile: idRestoreGame) {
            super.Restore(savefile)
            clearAccumBuffer = true
        }
    }

    // D3XP warp effect (grabber)
    class FullscreenFX_Warp : FullscreenFX() {
        private var material: Material.idMaterial? = null
        private var grabberEnabled: Boolean = false
        private var startWarpTime: Int = 0

        private fun DrawWarp(wp: WarpPolygon_t, interp: Float) {
            val shiftScale = fxman!!.GetShiftScale()
            val trans = wp

            // compute mid points
            val mid1 = trans.outer1.times(interp).plus(trans.center.times(1f - interp))
            val mid2 = trans.outer2.times(interp).plus(trans.center.times(1f - interp))
            val mid1_uv = trans.outer1.times(0.5f).plus(trans.center.times(0.5f))
            val mid2_uv = trans.outer2.times(0.5f).plus(trans.center.times(0.5f))

            val drawPts = Array(6) { idVec2() }

            // draw [outer1, mid2, mid1]
            drawPts[0].set(trans.outer1.x, trans.outer1.y)
            drawPts[1].set(mid2.x, mid2.y)
            drawPts[2].set(mid1.x, mid1.y)
            drawPts[3].set(trans.outer1.z, trans.outer1.w)
            drawPts[4].set(mid2_uv.z, mid2_uv.w)
            drawPts[5].set(mid1_uv.z, mid1_uv.w)
            for (j in 0 until 3) {
                drawPts[j + 3].x *= shiftScale.x
                drawPts[j + 3].y *= shiftScale.y
            }
            renderSystem.DrawStretchTri(
                drawPts[0], drawPts[1], drawPts[2], drawPts[3], drawPts[4], drawPts[5], material
            )

            // draw [outer1, outer2, mid2]
            drawPts[0].set(trans.outer1.x, trans.outer1.y)
            drawPts[1].set(trans.outer2.x, trans.outer2.y)
            drawPts[2].set(mid2.x, mid2.y)
            drawPts[3].set(trans.outer1.z, trans.outer1.w)
            drawPts[4].set(trans.outer2.z, trans.outer2.w)
            drawPts[5].set(mid2_uv.z, mid2_uv.w)
            for (j in 0 until 3) {
                drawPts[j + 3].x *= shiftScale.x
                drawPts[j + 3].y *= shiftScale.y
            }
            renderSystem.DrawStretchTri(
                drawPts[0], drawPts[1], drawPts[2], drawPts[3], drawPts[4], drawPts[5], material
            )

            // draw [mid1, mid2, center]
            drawPts[0].set(mid1.x, mid1.y)
            drawPts[1].set(mid2.x, mid2.y)
            drawPts[2].set(trans.center.x, trans.center.y)
            drawPts[3].set(mid1_uv.z, mid1_uv.w)
            drawPts[4].set(mid2_uv.z, mid2_uv.w)
            drawPts[5].set(trans.center.z, trans.center.w)
            for (j in 0 until 3) {
                drawPts[j + 3].x *= shiftScale.x
                drawPts[j + 3].y *= shiftScale.y
            }
            renderSystem.DrawStretchTri(
                drawPts[0], drawPts[1], drawPts[2], drawPts[3], drawPts[4], drawPts[5], material
            )
        }

        override fun Initialize() {
            material = DeclManager.declManager.FindMaterial("textures/smf/warp")
            grabberEnabled = false
            startWarpTime = 0
        }

        override fun Active(): Boolean = grabberEnabled
        override fun HighQuality() {
            val STEP = 9
            var interp = (sin((Game_local.gameLocal.slow.time - startWarpTime).toFloat() / 1000f) + 1f) / 2f
            interp = 0.7f * (1f - interp) + 0.3f * interp

            val center = idVec2(320f, 240f)
            val radius = 200f

            var i = 0f
            while (i < 360f) {
                val x1 = idMath.Sin(DEG2RAD(i))
                val y1 = idMath.Cos(DEG2RAD(i))
                val x2 = idMath.Sin(DEG2RAD(i + STEP))
                val y2 = idMath.Cos(DEG2RAD(i + STEP))

                val p = WarpPolygon_t()

                p.outer1.x = center.x + x1 * radius
                p.outer1.y = center.y + y1 * radius
                p.outer1.z = p.outer1.x / 640f
                p.outer1.w = 1f - (p.outer1.y / 480f)

                p.outer2.x = center.x + x2 * radius
                p.outer2.y = center.y + y2 * radius
                p.outer2.z = p.outer2.x / 640f
                p.outer2.w = 1f - (p.outer2.y / 480f)

                p.center.x = center.x
                p.center.y = center.y
                p.center.z = p.center.x / 640f
                p.center.w = 1f - (p.center.y / 480f)

                DrawWarp(p, interp)
                i += STEP
            }
        }

        fun EnableGrabber(active: Boolean) {
            grabberEnabled = active
            startWarpTime = Game_local.gameLocal.slow.time
        }

        override fun Save(savefile: idSaveGame) {
            super.Save(savefile)
            savefile.WriteBool(grabberEnabled)
            savefile.WriteInt(startWarpTime)
        }

        override fun Restore(savefile: idRestoreGame) {
            super.Restore(savefile)
            grabberEnabled = savefile.ReadBool()
            startWarpTime = savefile.ReadInt()
        }
    }

    // D3XP envirosuit effect
    class FullscreenFX_EnviroSuit : FullscreenFX() {
        private var material: Material.idMaterial? = null

        override fun Initialize() {
            material = DeclManager.declManager.FindMaterial("textures/smf/enviro_suit")
        }

        override fun Active(): Boolean {
            val player = fxman?.GetPlayer() ?: return false
            return player.PowerUpActive(Player.ENVIROSUIT)
        }

        override fun HighQuality() {
            renderSystem.SetColor4(1f, 1f, 1f, 1f)
            renderSystem.DrawStretchPic(0f, 0f, 640f, 480f, 0f, 0f, 1f, 1f, material)
        }
    }

    // D3XP double vision effect
    class FullscreenFX_DoubleVision : FullscreenFX() {
        private var material: Material.idMaterial? = null

        override fun Initialize() {
            material = DeclManager.declManager.FindMaterial("textures/smf/doubleVision")
        }

        override fun Active(): Boolean {
            val pv = fxman?.GetPlayerView() ?: return false
            return Game_local.gameLocal.fast.time < pv.dvFinishTime
        }

        override fun HighQuality() {
            var offset = fxman!!.GetPlayerView()!!.dvFinishTime - Game_local.gameLocal.fast.time
            var scale = offset * SysCvar.g_dvAmplitude.GetFloat()
            val player = fxman!!.GetPlayer()!!
            val shiftScale = fxman!!.GetShiftScale()

            offset *= 2 // crutch up for higher res

            if (scale > 0.5f) {
                scale = 0.5f
            }
            val shift = abs(scale * sin(sqrt(offset.toFloat()) * SysCvar.g_dvFrequency.GetFloat()))

            // carry red tint if in berserk mode
            val color = idVec4(1f, 1f, 1f, 1f)
            if (Game_local.gameLocal.fast.time < player.inventory.powerupEndTime[Player.BERSERK]) {
                color.y = 0f
                color.z = 0f
            }
            if (!Game_local.gameLocal.isMultiplayer && (Game_local.gameLocal.fast.time < player.inventory.powerupEndTime[Player.HELLTIME] || Game_local.gameLocal.fast.time < player.inventory.powerupEndTime[Player.INVULNERABILITY])) {
                color.y = 0f
                color.z = 0f
            }

            renderSystem.SetColor4(color.x, color.y, color.z, 1.0f)
            renderSystem.DrawStretchPic(
                0f, 0f, SCREEN_WIDTH.toFloat(), SCREEN_HEIGHT.toFloat(), shift, shiftScale.y, shiftScale.x, 0f, material
            )
            renderSystem.SetColor4(color.x, color.y, color.z, 0.5f)
            renderSystem.DrawStretchPic(
                0f,
                0f,
                SCREEN_WIDTH.toFloat(),
                SCREEN_HEIGHT.toFloat(),
                0f,
                shiftScale.y,
                (1f - shift) * shiftScale.x,
                0f,
                material
            )
        }
    }

    // D3XP influence vision effect
    class FullscreenFX_InfluenceVision : FullscreenFX() {
        override fun Initialize() {}

        override fun Active(): Boolean {
            val player = fxman?.GetPlayer() ?: return false
            return player.GetInfluenceMaterial() != null || player.GetInfluenceEntity() != null
        }

        override fun HighQuality() {
            var distance = 0f
            var pct = 1f
            val shiftScale = fxman!!.GetShiftScale()
            val player = fxman!!.GetPlayer() ?: return

            if (player.GetInfluenceEntity() != null) {
                distance = (player.GetInfluenceEntity()!!.GetPhysics().GetOrigin()
                    .minus(player.GetPhysics().GetOrigin())).Length()
                if (player.GetInfluenceRadius() != 0f && distance < player.GetInfluenceRadius()) {
                    pct = distance / player.GetInfluenceRadius()
                    pct = 1f - idMath.ClampFloat(0f, 1f, pct)
                }
            }

            if (player.GetInfluenceMaterial() != null) {
                renderSystem.SetColor4(1f, 1f, 1f, pct)
                renderSystem.DrawStretchPic(0f, 0f, 640f, 480f, 0f, 0f, 1f, 1f, player.GetInfluenceMaterial())
            } else if (player.GetInfluenceEntity() == null) {
                return
            }
        }
    }

    // D3XP bloom effect
    class FullscreenFX_Bloom : FullscreenFX() {
        private var drawMaterial: Material.idMaterial? = null
        private var initMaterial: Material.idMaterial? = null
        private var currentMaterial: Material.idMaterial? = null
        private var currentIntensity: Float = 0f
        private var targetIntensity: Float = 0f

        override fun Initialize() {
            drawMaterial = DeclManager.declManager.FindMaterial("textures/smf/bloom2/draw")
            initMaterial = DeclManager.declManager.FindMaterial("textures/smf/bloom2/init")
            currentMaterial = DeclManager.declManager.FindMaterial("textures/smf/bloom2/currentMaterial")
            currentIntensity = 0f
            targetIntensity = 0f
        }

        override fun Active(): Boolean {
            val player = fxman?.GetPlayer() ?: return false
            return player.bloomEnabled
        }

        override fun HighQuality() {
            var shift = 1f
            val player = fxman!!.GetPlayer()
            val shiftScale = fxman!!.GetShiftScale()
            renderSystem.SetColor4(1f, 1f, 1f, 1f)

            // if intensity value is different, start the blend
            targetIntensity = SysCvar.g_testBloomIntensity.GetFloat()

            if (player != null && player.bloomEnabled) {
                targetIntensity = player.bloomIntensity
            }

            val delta = targetIntensity - currentIntensity
            var step = 0.001f

            if (step < abs(delta)) {
                if (delta < 0) {
                    step = -step
                }
                currentIntensity += step
            }

            // draw the blends
            val num = SysCvar.g_testBloomNumPasses.GetInteger()

            for (i in 0 until num) {
                var s1 = 0f
                var t1 = 0f
                var s2 = 1f
                var t2 = 1f

                // do the center scale
                s1 -= 0.5f; s1 *= shift; s1 += 0.5f; s1 *= shiftScale.x
                t1 -= 0.5f; t1 *= shift; t1 += 0.5f; t1 *= shiftScale.y
                s2 -= 0.5f; s2 *= shift; s2 += 0.5f; s2 *= shiftScale.x
                t2 -= 0.5f; t2 *= shift; t2 += 0.5f; t2 *= shiftScale.y

                val alpha = if (num == 1) 1f else 1f - i.toFloat() / (num - 1)

                renderSystem.SetColor4(alpha, alpha, alpha, 1f)
                renderSystem.DrawStretchPic(
                    0f, 0f, SCREEN_WIDTH.toFloat(), SCREEN_HEIGHT.toFloat(), s1, t2, s2, t1, drawMaterial
                )

                shift += currentIntensity
            }
        }

        override fun Save(savefile: idSaveGame) {
            super.Save(savefile)
            savefile.WriteFloat(currentIntensity)
            savefile.WriteFloat(targetIntensity)
        }

        override fun Restore(savefile: idRestoreGame) {
            super.Restore(savefile)
            currentIntensity = savefile.ReadFloat()
            targetIntensity = savefile.ReadFloat()
        }
    }

    // D3XP fullscreen FX manager
    class FullscreenFXManager {
        private val fx: MutableList<FullscreenFX> = mutableListOf()
        private var highQualityMode: Boolean = false
        private val shiftScale: idVec2 = idVec2()

        var playerView: idPlayerView? = null
            private set
        private var blendBackMaterial: Material.idMaterial? = null

        private fun CreateFX(name: idStr, fxtype: idStr, fade: Int) {
            val pfx: FullscreenFX? = when (fxtype.toString()) {
                "helltime" -> FullscreenFX_Helltime()
                "warp" -> FullscreenFX_Warp()
                "envirosuit" -> FullscreenFX_EnviroSuit()
                "doublevision" -> FullscreenFX_DoubleVision()
                "multiplayer" -> FullscreenFX_Multiplayer()
                "influencevision" -> FullscreenFX_InfluenceVision()
                "bloom" -> FullscreenFX_Bloom()
                else -> {
                    assert(false); null
                }
            }
            if (pfx != null) {
                pfx.Initialize()
                pfx.SetFXManager(this)
                pfx.SetName(name)
                pfx.SetFadeSpeed(fade)
                fx.add(pfx)
            }
        }

        fun Initialize(pv: idPlayerView) {
            playerView = pv
            blendBackMaterial = DeclManager.declManager.FindMaterial("textures/smf/blendBack")

            CreateFX(idStr("helltime"), idStr("helltime"), 1000)
            CreateFX(idStr("warp"), idStr("warp"), 0)
            CreateFX(idStr("envirosuit"), idStr("envirosuit"), 500)
            CreateFX(idStr("doublevision"), idStr("doublevision"), 0)
            CreateFX(idStr("multiplayer"), idStr("multiplayer"), 1000)
            CreateFX(idStr("influencevision"), idStr("influencevision"), 1000)
            CreateFX(idStr("bloom"), idStr("bloom"), 0)

            // pre-cache texture grabs
            renderSystem.CropRenderSize(512, 512, true)
            renderSystem.CaptureRenderToImage("_accum")
            renderSystem.UnCrop()

            renderSystem.CropRenderSize(512, 256, true)
            renderSystem.CaptureRenderToImage("_scratch")
            renderSystem.UnCrop()

            renderSystem.CaptureRenderToImage("_currentRender")
        }

        fun Process(view: renderView_s) {
            var allpass = false

            if (SysCvar.g_testFullscreenFX.GetInteger() == -2) {
                allpass = true
            }

            highQualityMode = !SysCvar.g_lowresFullscreenFX.GetBool()

            // compute the shift scale
            if (highQualityMode) {
                val vidWidth = CInt()
                val vidHeight = CInt()
                renderSystem.GetGLSettings(vidWidth, vidHeight)

                var pot = 1
                while (pot < vidWidth._val) pot = pot shl 1
                shiftScale.x = vidWidth._val.toFloat() / pot

                pot = 1
                while (pot < vidHeight._val) pot = pot shl 1
                shiftScale.y = vidHeight._val.toFloat() / pot
            } else {
                shiftScale.x = 1f
                shiftScale.y = 1f
                renderSystem.CropRenderSize(512, 512, true)
            }

            // do the first render
            Game_local.gameRenderWorld!!.RenderScene(view)

            // process each effect
            for (i in 0 until fx.size) {
                val pfx = fx[i]
                val drawIt: Boolean

                if (pfx.Active() || SysCvar.g_testFullscreenFX.GetInteger() == i || allpass) {
                    drawIt = pfx.SetTriggerState(true)
                } else {
                    drawIt = pfx.SetTriggerState(false)
                }

                if (drawIt) {
                    CaptureCurrentRender()

                    if (pfx.HasAccum()) {
                        if (highQualityMode) {
                            renderSystem.CropRenderSize(512, 512, true)
                            pfx.AccumPass(view)
                            renderSystem.UnCrop()
                        } else {
                            pfx.AccumPass(view)
                        }
                    }

                    pfx.HighQuality()
                    Blendback(pfx.GetFadeAlpha())
                }
            }

            if (!highQualityMode) {
                CaptureCurrentRender()
                renderSystem.UnCrop()
                renderSystem.SetColor4(1f, 1f, 1f, 1f)
                renderSystem.DrawStretchPic(0f, 0f, 640f, 480f, 0f, 1f, 1f, 0f, blendBackMaterial)
            }
        }

        fun CaptureCurrentRender() {
            renderSystem.CaptureRenderToImage("_currentRender")
        }

        fun Blendback(alpha: Float) {
            if (alpha < 1f) {
                renderSystem.SetColor4(1f, 1f, 1f, 1f - alpha)
                renderSystem.DrawStretchPic(
                    0f, 0f, 640f, 480f, 0f, shiftScale.y, shiftScale.x, 0f, blendBackMaterial
                )
            }
        }

        fun GetShiftScale(): idVec2 = shiftScale
        fun GetPlayerView(): idPlayerView? = playerView
        fun GetPlayer(): idPlayer? = Game_local.gameLocal.GetLocalPlayer()

        fun GetNum(): Int = fx.size
        fun GetFX(index: Int): FullscreenFX = fx[index]
        fun FindFX(name: idStr): FullscreenFX? {
            for (i in 0 until fx.size) {
                if (fx[i].GetName() == name) return fx[i]
            }
            return null
        }

        fun Save(savefile: idSaveGame) {
            savefile.WriteBool(highQualityMode)
            savefile.WriteVec2(shiftScale)
            for (i in 0 until fx.size) {
                fx[i].Save(savefile)
            }
        }

        fun Restore(savefile: idRestoreGame) {
            highQualityMode = savefile.ReadBool()
            savefile.ReadVec2(shiftScale)
            for (i in 0 until fx.size) {
                fx[i].Restore(savefile)
            }
        }
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
        var dvFinishTime // double vision will be stopped at this time
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

        // D3XP fullscreen FX manager
        var fxManager: FullscreenFXManager? = null

        // D3XP warp effects
        fun AddWarp(
            worldOrigin: idVec3, centerx: Float, centery: Float, initialRadius: Float, durationMsec: Float
        ): Int {
            val fx = fxManager?.FindFX(idStr("warp")) as? FullscreenFX_Warp
            fx?.EnableGrabber(true)
            return 1
        }

        fun FreeWarp(id: Int) {
            val fx = fxManager?.FindFX(idStr("warp")) as? FullscreenFX_Warp
            fx?.EnableGrabber(false)
        }

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

            // D3XP: save FX manager state
            if (isD3XP) {
                fxManager?.Save(savefile)
            }
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

            // D3XP: restore FX manager state
            if (isD3XP) {
                fxManager?.Restore(savefile)
            }
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
                Game_local.gameLocal.time, player!!.firstPersonViewOrigin
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
            if (isD3XP) {
                // D3XP: all effects handled by fxManager inside SingleView
                SingleView(hud, view)
            } else if (SysCvar.g_skipViewEffects.GetBool()) {
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
            }
            ScreenFade()
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
                    if (glWidth._val > 0 && glHeight._val > 0) {
                        val glAspectRatio = glWidth._val.toFloat() / glHeight._val.toFloat()

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
            val ts = if (isD3XP) SetTimeState(player!!.timeGroup) else null
            try {
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
            } finally {
                ts?.close()
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
                if (isD3XP) Game_local.gameLocal.slow.time else Game_local.gameLocal.time,
                idStr(if (hud != null) hud.State().GetString("location") else "Undefined")
            )

            // if the objective system is up, don't do normal drawing
            if (player!!.objectiveSystemOpen) {
                player!!.objectiveSystem!!.Redraw(if (isD3XP) Game_local.gameLocal.fast.time else Game_local.gameLocal.time)
                return
            }

            // hack the shake in at the very last moment, so it can't cause any consistency problems
            val hackedView = renderView_s(view)
            hackedView.viewaxis.set(hackedView.viewaxis.times(ShakeAxis()))

            // D3XP: portal sky rendering
            if (isD3XP && Game_local.gameLocal.portalSkyEnt.GetEntity() != null && Game_local.gameLocal.IsPortalSkyActive() && SysCvar.g_enablePortalSky.GetBool()) {
                val portalView = renderView_s(hackedView)
                portalView.vieworg.set(
                    Game_local.gameLocal.portalSkyEnt.GetEntity()!!.GetPhysics().GetOrigin()
                )

                // setup global fixup projection vars
                val vidWidth = CInt()
                val vidHeight = CInt()
                renderSystem.GetGLSettings(vidWidth, vidHeight)

                var pot = 1
                while (pot < vidWidth._val) pot = pot shl 1
                val shiftX = vidWidth._val.toFloat() / pot

                pot = 1
                while (pot < vidHeight._val) pot = pot shl 1
                val shiftY = vidHeight._val.toFloat() / pot

                hackedView.shaderParms[4] = shiftX
                hackedView.shaderParms[5] = shiftY

                Game_local.gameRenderWorld!!.RenderScene(portalView)
                renderSystem.CaptureRenderToImage("_currentRender")

                hackedView.forceUpdate = true // FIX: for smoke particles not drawing when portalSky present
            }

            // D3XP: process fullscreen effects (includes RenderScene internally)
            if (isD3XP) {
                fxManager?.Process(hackedView)
            } else {
                Game_local.gameRenderWorld!!.RenderScene(hackedView)
            }

            if (player!!.spectating) {
                return
            }

            if (isD3XP && hud == null) {
                return
            }

            // draw screen blobs
            if (!SysCvar.pm_thirdPerson.GetBool() && !SysCvar.g_skipViewEffects.GetBool()) {
                for (i in 0 until MAX_SCREEN_BLOBS) {
                    val blob = screenBlobs[i]
                    if (blob.finishTime <= (if (isD3XP) Game_local.gameLocal.slow.time else Game_local.gameLocal.time)) {
                        continue
                    }
                    blob.y += blob.driftAmount
                    var fade: Float =
                        (blob.finishTime - (if (isD3XP) Game_local.gameLocal.slow.time else Game_local.gameLocal.time)).toFloat() / (blob.finishTime - blob.startFadeTime)
                    if (fade > 1.0f) {
                        fade = 1.0f
                    }
                    if (fade != 0.0f) {
                        renderSystem.SetColor4(1.0f, 1.0f, 1.0f, fade)
                        renderSystem.DrawStretchPic(
                            blob.x, blob.y, blob.w, blob.h, blob.s1, blob.t1, blob.s2, blob.t2, blob.material
                        )
                    }
                }
                player!!.DrawHUD(hud)

                // armor impulse feedback
                val armorPulse =
                    ((if (isD3XP) Game_local.gameLocal.fast.time else Game_local.gameLocal.time) - player!!.lastArmorPulse) / 250.0f
                if (armorPulse > 0.0f && armorPulse < 1.0f) {
                    renderSystem.SetColor4(1.0f, 1.0f, 1.0f, 1.0f - armorPulse)
                    renderSystem.DrawStretchPic(
                        0.0f, 0.0f, 640.0f, 480.0f, 0.0f, 0.0f, 1.0f, 1.0f, armorMaterial
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
                        if (player!!.health <= 0.0f) MS2SEC((if (isD3XP) Game_local.gameLocal.slow.time else Game_local.gameLocal.time).toFloat()) else lastDamageTime,
                        1.0f,
                        1.0f,
                        if (player!!.health <= 0.0f) 0.0f else alpha
                    )
                    renderSystem.DrawStretchPic(
                        0.0f, 0.0f, 640.0f, 480.0f, 0.0f, 0.0f, 1.0f, 1.0f, tunnelMaterial
                    )
                }
                // D3XP: berserk overlay is handled by FullscreenFX_Helltime, only draw in base game
                if (!isD3XP && player!!.PowerUpActive(Player.BERSERK)) {
                    val berserkTime = player!!.inventory.powerupEndTime[Player.BERSERK] - Game_local.gameLocal.time
                    if (berserkTime > 0) {
                        // start fading if within 10 seconds of going away
                        alpha = if (berserkTime < 10000) berserkTime.toFloat() / 10000 else 1.0f
                        renderSystem.SetColor4(1.0f, 1.0f, 1.0f, alpha)
                        renderSystem.DrawStretchPic(
                            0.0f, 0.0f, 640.0f, 480.0f, 0.0f, 0.0f, 1.0f, 1.0f, berserkMaterial
                        )
                    }
                }
                if (bfgVision) {
                    renderSystem.SetColor4(1.0f, 1.0f, 1.0f, 1.0f)
                    renderSystem.DrawStretchPic(
                        0.0f, 0.0f, 640.0f, 480.0f, 0.0f, 0.0f, 1.0f, 1.0f, bfgMaterial
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
                0.0f, 0.0f, SCREEN_WIDTH.toFloat(), SCREEN_HEIGHT.toFloat(), 0.0f, 1.0f, 1.0f, 0.0f, dvMaterial
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
                    0.0f, 0.0f, 640.0f, 480.0f, 0.0f, 0.0f, 1.0f, 1.0f, player!!.GetInfluenceMaterial()!!
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
            val ts = if (isD3XP) SetTimeState(player!!.timeGroup) else null
            try {
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
                        fadeColor[0], fadeColor[1], fadeColor[2], fadeColor[3]
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
            } finally {
                ts?.close()
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

            // D3XP: create fullscreen FX manager
            if (isD3XP) {
                fxManager = FullscreenFXManager()
                fxManager!!.Initialize(this)
            }
        }
    }
}

package neo.ui

import neo.Renderer.Material
import neo.Renderer.Material.idMaterial
import neo.framework.CVarSystem.CVAR_FLOAT
import neo.framework.CVarSystem.idCVar
import neo.framework.DeclManager
import neo.framework.File_h.idFile
import neo.framework.KeyInput.K_MOUSE1
import neo.framework.Session
import neo.idlib.Text.Parser.idParser
import neo.idlib.Text.Str.idStr
import neo.idlib.Text.Str.idStr.Companion.Icmp
import neo.idlib.Text.Str.va
import neo.idlib.colorWhite
import neo.idlib.containers.CBool
import neo.idlib.containers.List.idList
import neo.idlib.math.DEG2RAD
import neo.idlib.math.RAD2DEG
import neo.idlib.math.Random.idRandom
import neo.idlib.math.idMath.ClampFloat
import neo.idlib.math.idMath.Cos
import neo.idlib.math.idMath.Sin
import neo.idlib.math.idVec2
import neo.idlib.math.idVec4
import neo.sys.sysEventType_t
import neo.sys.sysEvent_s
import neo.ui.DeviceContext.idDeviceContext
import neo.ui.SimpleWindow.drawWin_t
import neo.ui.UserInterfaceLocal.idUserInterfaceLocal
import neo.ui.Window.idWindow
import neo.ui.Winvar.idWinBool
import neo.ui.Winvar.idWinVar
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2

object GameBearShootWindow {
    const val BEAR_GRAVITY = 240
    const val BEAR_SHRINK_TIME = 2000.0f
    const val BEAR_SIZE = 24.0f
    const val MAX_WINDFORCE = 100.0f
    val bearTurretAngle = idCVar("bearTurretAngle", "0", CVAR_FLOAT, "")
    val bearTurretForce = idCVar("bearTurretForce", "200", CVAR_FLOAT, "")

    //
    /*
     *****************************************************************************
     * BSEntity
     ****************************************************************************
     */
    class BSEntity(//
        var game: idGameBearShootWindow
    ) {
        val entColor: idVec4 = idVec4()
        var fadeIn: Boolean
        var fadeOut: Boolean
        var material: idMaterial?
        var materialName: idStr
        val position: idVec2 = idVec2()
        var rotation: Float
        var rotationSpeed: Float
        val velocity: idVec2 = idVec2()
        var visible = true
        var width: Float
        var height: Float

        //
        init {
            entColor.set(colorWhite)
            materialName = idStr("")
            material = null
            height = 8.0f
            width = height
            rotation = 0.0f
            rotationSpeed = 0.0f
            fadeIn = false
            fadeOut = false
            position.Zero()
            velocity.Zero()
        }

        //	// virtual				~BSEntity();
        //
        fun WriteToSaveGame(savefile: idFile) {
            game.WriteSaveGameString(materialName.toString(), savefile)
            savefile.WriteFloat(width)
            savefile.WriteFloat(height)
            savefile.WriteBool(visible)
            savefile.Write(entColor)
            savefile.Write(position)
            savefile.WriteFloat(rotation)
            savefile.WriteFloat(rotationSpeed)
            savefile.Write(velocity)
            savefile.WriteBool(fadeIn)
            savefile.WriteBool(fadeOut)
        }

        fun ReadFromSaveGame(savefile: idFile, _game: idGameBearShootWindow) {
            game = _game
            game.ReadSaveGameString(materialName, savefile)
            SetMaterial(materialName.toString())
            width = savefile.ReadFloat()
            height = savefile.ReadFloat()
            visible = savefile.ReadBool()
            savefile.Read(entColor)
            savefile.Read(position)
            rotation = savefile.ReadFloat()
            rotationSpeed = savefile.ReadFloat()
            savefile.Read(velocity)
            fadeIn = savefile.ReadBool()
            fadeOut = savefile.ReadBool()
        }

        fun SetMaterial(name: String?) {
            materialName.set(name)
            material = DeclManager.declManager.FindMaterial(name!!)
            material!!.SetSort(Material.SS_GUI.toFloat())
        }

        fun SetSize(_width: Float, _height: Float) {
            width = _width
            height = _height
        }

        fun SetVisible(isVisible: Boolean) {
            visible = isVisible
        }

        fun Update(timeslice: Float) {
            if (!visible) {
                return
            }

            // Fades
            if (fadeIn && entColor.w < 1.0f) {
                entColor.w += 1 * timeslice
                if (entColor.w >= 1.0f) {
                    entColor.w = 1.0f
                    fadeIn = false
                }
            }
            if (fadeOut && entColor.w > 0.0f) {
                entColor.w -= 1 * timeslice
                if (entColor.w <= 0.0f) {
                    entColor.w = 0.0f
                    fadeOut = false
                }
            }

            // Move the entity
            position.plusAssign(velocity.times(timeslice))

            // Rotate Entity
            rotation += rotationSpeed * timeslice
        }

        fun Draw(dc: idDeviceContext) {
            if (visible) {
                dc.DrawMaterialRotated(
                    position.x,
                    position.y,
                    width,
                    height,
                    material,
                    entColor,
                    1.0f,
                    1.0f,
                    DEG2RAD(rotation)
                )
            }
        }
    }

    /*
     *****************************************************************************
     * idGameBearShootWindow
     ****************************************************************************
     */
    class idGameBearShootWindow : idWindow {
        private var bear: BSEntity? = null
        private var bearHitTarget = false
        private var bearIsShrinking = false
        private var bearScale = 0.0f
        private var bearShrinkStartTime = 0
        private var currentLevel = 0
        private val entities = idList<BSEntity>()
        private var gameOver = false
        private val gamerunning: idWinBool = idWinBool()
        private var goal: BSEntity? = null
        private var goalsHit = 0
        private var gunblast: BSEntity? = null
        private var helicopter: BSEntity? = null
        private val onContinue: idWinBool = idWinBool()
        private val onFire: idWinBool = idWinBool()
        private val onNewGame: idWinBool = idWinBool()
        private var timeRemaining = 0.0f
        private var timeSlice = 0.0f
        private var turret: BSEntity? = null
        private var turretAngle = 0.0f
        private var turretForce = 0.0f
        private var updateScore = false
        private var wind: BSEntity? = null
        private var windForce = 0.0f
        private var windUpdateTime = 0

        constructor(gui: idUserInterfaceLocal) : super(gui) {
            this.gui = gui
            CommonInit()
        }

        constructor(dc: idDeviceContext?, gui: idUserInterfaceLocal?) : super(dc, gui) {
            this.dc = dc
            this.gui = gui
            CommonInit()
        }

        override fun WriteToSaveGame(savefile: idFile) {
            super.WriteToSaveGame(savefile)
            gamerunning.WriteToSaveGame(savefile)
            onFire.WriteToSaveGame(savefile)
            onContinue.WriteToSaveGame(savefile)
            onNewGame.WriteToSaveGame(savefile)
            savefile.WriteFloat(timeSlice)
            savefile.WriteFloat(timeRemaining)
            savefile.WriteBool(gameOver)
            savefile.WriteInt(currentLevel)
            savefile.WriteInt(goalsHit)
            savefile.WriteBool(updateScore)
            savefile.WriteBool(bearHitTarget)
            savefile.WriteFloat(bearScale)
            savefile.WriteBool(bearIsShrinking)
            savefile.WriteInt(bearShrinkStartTime)
            savefile.WriteFloat(turretAngle)
            savefile.WriteFloat(turretForce)
            savefile.WriteFloat(windForce)
            savefile.WriteInt(windUpdateTime)
            val numberOfEnts = entities.Num()
            savefile.WriteInt(numberOfEnts)
            for (i in 0 until numberOfEnts) {
                entities[i].WriteToSaveGame(savefile)
            }
            var index: Int
            index = entities.FindIndex(turret)
            savefile.WriteInt(index)
            index = entities.FindIndex(bear)
            savefile.WriteInt(index)
            index = entities.FindIndex(helicopter)
            savefile.WriteInt(index)
            index = entities.FindIndex(goal)
            savefile.WriteInt(index)
            index = entities.FindIndex(wind)
            savefile.WriteInt(index)
            index = entities.FindIndex(gunblast)
            savefile.WriteInt(index)
        }

        override fun ReadFromSaveGame(savefile: idFile) {
            super.ReadFromSaveGame(savefile)

            // Remove all existing entities
            entities.DeleteContents(true)
            gamerunning.ReadFromSaveGame(savefile)
            onFire.ReadFromSaveGame(savefile)
            onContinue.ReadFromSaveGame(savefile)
            onNewGame.ReadFromSaveGame(savefile)
            timeSlice = savefile.ReadFloat()
            timeRemaining = savefile.ReadFloat()
            gameOver = savefile.ReadBool()
            currentLevel = savefile.ReadInt()
            goalsHit = savefile.ReadInt()
            updateScore = savefile.ReadBool()
            bearHitTarget = savefile.ReadBool()
            bearScale = savefile.ReadFloat()
            bearIsShrinking = savefile.ReadBool()
            bearShrinkStartTime = savefile.ReadInt()
            turretAngle = savefile.ReadFloat()
            turretForce = savefile.ReadFloat()
            windForce = savefile.ReadFloat()
            windUpdateTime = savefile.ReadInt()
            val numberOfEnts: Int
            numberOfEnts = savefile.ReadInt()
            for (i in 0 until numberOfEnts) {
                var ent: BSEntity
                ent = BSEntity(this)
                ent.ReadFromSaveGame(savefile, this)
                entities.Append(ent)
            }
            var index: Int
            index = savefile.ReadInt()
            turret = entities[index]
            index = savefile.ReadInt()
            bear = entities[index]
            index = savefile.ReadInt()
            helicopter = entities[index]
            index = savefile.ReadInt()
            goal = entities[index]
            index = savefile.ReadInt()
            wind = entities[index]
            index = savefile.ReadInt()
            gunblast = entities[index]
        }

        override fun HandleEvent(event: sysEvent_s, updateVisuals: CBool?): String? {
            val key = event.evValue

            // need to call this to allow proper focus and capturing on embedded children
            val ret = super.HandleEvent(event, updateVisuals)
            if (event.evType == sysEventType_t.SE_KEY) {
                if (0 == event.evValue2) {
                    return ret
                }
                if (key == K_MOUSE1) {
                    // Mouse was clicked
                } else {
                    return ret
                }
            }
            return ret
        }

        override fun Draw(time: Int, x: Float, y: Float) {
            var i: Int

            //Update the game every frame before drawing
            UpdateGame()
            i = entities.Num() - 1
            while (i >= 0) {
                entities[i].Draw(dc!!)
                i--
            }
        }

        override fun GetWinVarByName(
            _name: String?,
            winLookup: Boolean /*= false*/,
            owner: Array<drawWin_t?>? /*= NULL*/
        ): idWinVar? {
            var retVar: idWinVar? = null
            if (Icmp(_name!!, "gamerunning") == 0) {
                retVar = gamerunning
            } else if (Icmp(_name, "onFire") == 0) {
                retVar = onFire
            } else if (Icmp(_name, "onContinue") == 0) {
                retVar = onContinue
            } else if (Icmp(_name, "onNewGame") == 0) {
                retVar = onNewGame
            }
            return retVar ?: super.GetWinVarByName(_name, winLookup, owner)
        }

        private fun CommonInit() {
            var ent: BSEntity

            // Precache sounds
            DeclManager.declManager.FindSound("arcade_beargroan")
            DeclManager.declManager.FindSound("arcade_sargeshoot")
            DeclManager.declManager.FindSound("arcade_balloonpop")
            DeclManager.declManager.FindSound("arcade_levelcomplete1")

            // Precache dynamically used materials
            DeclManager.declManager.FindMaterial("game/bearshoot/helicopter_broken")
            DeclManager.declManager.FindMaterial("game/bearshoot/goal_dead")
            DeclManager.declManager.FindMaterial("game/bearshoot/gun_blast")
            ResetGameState()
            ent = BSEntity(this)
            turret = ent
            ent.SetMaterial("game/bearshoot/turret")
            ent.SetSize(272.0f, 144.0f)
            ent.position.x = -44.0f
            ent.position.y = 260.0f
            entities.Append(ent)
            ent = BSEntity(this)
            ent.SetMaterial("game/bearshoot/turret_base")
            ent.SetSize(144.0f, 160.0f)
            ent.position.x = 16.0f
            ent.position.y = 280.0f
            entities.Append(ent)
            ent = BSEntity(this)
            bear = ent
            ent.SetMaterial("game/bearshoot/bear")
            ent.SetSize(BEAR_SIZE, BEAR_SIZE)
            ent.SetVisible(false)
            ent.position.x = 0.0f
            ent.position.y = 0.0f
            entities.Append(ent)
            ent = BSEntity(this)
            helicopter = ent
            ent.SetMaterial("game/bearshoot/helicopter")
            ent.SetSize(64.0f, 64.0f)
            ent.position.x = 550.0f
            ent.position.y = 100.0f
            entities.Append(ent)
            ent = BSEntity(this)
            goal = ent
            ent.SetMaterial("game/bearshoot/goal")
            ent.SetSize(64.0f, 64.0f)
            ent.position.x = 550.0f
            ent.position.y = 164.0f
            entities.Append(ent)
            ent = BSEntity(this)
            wind = ent
            ent.SetMaterial("game/bearshoot/wind")
            ent.SetSize(100.0f, 40.0f)
            ent.position.x = 500.0f
            ent.position.y = 430.0f
            entities.Append(ent)
            ent = BSEntity(this)
            gunblast = ent
            ent.SetMaterial("game/bearshoot/gun_blast")
            ent.SetSize(64.0f, 64.0f)
            ent.SetVisible(false)
            entities.Append(ent)
        }

        private fun ResetGameState() {
            gamerunning.data = false
            gameOver = false
            onFire.data = false
            onContinue.data = false
            onNewGame.data = false

            // Game moves forward 16 milliseconds every frame
            timeSlice = 0.016f
            timeRemaining = 60.0f
            goalsHit = 0
            updateScore = false
            bearHitTarget = false
            currentLevel = 1
            turretAngle = 0.0f
            turretForce = 200.0f
            windForce = 0.0f
            windUpdateTime = 0
            bearIsShrinking = false
            bearShrinkStartTime = 0
            bearScale = 1.0f
        }

        private fun UpdateBear() {
            val time = gui!!.GetTime()
            var startShrink = false

            // Apply gravity
            bear!!.velocity.y += BEAR_GRAVITY * timeSlice

            // Apply wind
            bear!!.velocity.x += windForce * timeSlice

            // Check for collisions
            if (!bearHitTarget && !gameOver) {
                val bearCenter = idVec2()
                var collision = false
                bearCenter.x = bear!!.position.x + bear!!.width / 2
                bearCenter.y = bear!!.position.y + bear!!.height / 2
                if (bearCenter.x > helicopter!!.position.x + 16 && bearCenter.x < helicopter!!.position.x + helicopter!!.width - 29) {
                    if (bearCenter.y > helicopter!!.position.y + 12 && bearCenter.y < helicopter!!.position.y + helicopter!!.height - 7) {
                        collision = true
                    }
                }
                if (collision) {
                    // balloons pop and bear tumbles to ground
                    helicopter!!.SetMaterial("game/bearshoot/helicopter_broken")
                    helicopter!!.velocity.y = 230.0f
                    goal!!.velocity.y = 230.0f
                    Session.session.sw.PlayShaderDirectly("arcade_balloonpop")
                    bear!!.SetVisible(false)
                    if (bear!!.velocity.x > 0) {
                        bear!!.velocity.x *= -1.0f
                    }
                    bear!!.velocity.timesAssign(0.666f)
                    bearHitTarget = true
                    updateScore = true
                    startShrink = true
                }
            }

            // Check for ground collision
            if (bear!!.position.y > 380) {
                bear!!.position.y = 380.0f
                if (bear!!.velocity.Length() < 25) {
                    bear!!.velocity.Zero()
                } else {
                    startShrink = true
                    bear!!.velocity.y *= -1.0f
                    bear!!.velocity.timesAssign(0.5f)
                    if (bearScale != 0.0f) {
                        Session.session.sw.PlayShaderDirectly("arcade_balloonpop")
                    }
                }
            }

            // Bear rotation is based on velocity
            val angle: Float
            val dir = idVec2()
            dir.set(bear!!.velocity)
            dir.NormalizeFast()
            angle = RAD2DEG(atan2(dir.x, dir.y))
            bear!!.rotation = angle - 90

            // Update Bear scale
            if (bear!!.position.x > 650) {
                startShrink = true
            }
            if (!bearIsShrinking && bearScale != 0.0f && startShrink) {
                bearShrinkStartTime = time
                bearIsShrinking = true
            }
            if (bearIsShrinking) {
                bearScale = if (bearHitTarget) {
                    1 - (time - bearShrinkStartTime).toFloat() / BEAR_SHRINK_TIME
                } else {
                    1 - (time - bearShrinkStartTime).toFloat() / 750
                }
                bearScale *= BEAR_SIZE
                bear!!.SetSize(bearScale, bearScale)
                if (bearScale < 0) {
                    gui!!.HandleNamedEvent("EnableFireButton")
                    bearIsShrinking = false
                    bearScale = 0.0f
                    if (bearHitTarget) {
                        goal!!.SetMaterial("game/bearshoot/goal")
                        goal!!.position.x = 550.0f
                        goal!!.position.y = 164.0f
                        goal!!.velocity.Zero()
                        goal!!.velocity.y = ((currentLevel - 1) * 30).toFloat()
                        goal!!.entColor.w = 0.0f
                        goal!!.fadeIn = true
                        goal!!.fadeOut = false
                        helicopter!!.SetVisible(true)
                        helicopter!!.SetMaterial("game/bearshoot/helicopter")
                        helicopter!!.position.x = 550.0f
                        helicopter!!.position.y = 100.0f
                        helicopter!!.velocity.Zero()
                        helicopter!!.velocity.y = goal!!.velocity.y
                        helicopter!!.entColor.w = 0.0f
                        helicopter!!.fadeIn = true
                        helicopter!!.fadeOut = false
                    }
                }
            }
        }

        private fun UpdateHelicopter() {
            if (bearHitTarget && bearIsShrinking) {
                if (helicopter!!.velocity.y != 0.0f && helicopter!!.position.y > 264) {
                    helicopter!!.velocity.y = 0.0f
                    goal!!.velocity.y = 0.0f
                    helicopter!!.SetVisible(false)
                    goal!!.SetMaterial("game/bearshoot/goal_dead")
                    Session.session.sw.PlayShaderDirectly("arcade_beargroan", 1)
                    helicopter!!.fadeOut = true
                    goal!!.fadeOut = true
                }
            } else if (currentLevel > 1) {
                val height = helicopter!!.position.y.toInt()
                val speed = ((currentLevel - 1) * 30).toFloat()
                if (height > 240) {
                    helicopter!!.velocity.y = -speed
                    goal!!.velocity.y = -speed
                } else if (height < 30) {
                    helicopter!!.velocity.y = speed
                    goal!!.velocity.y = speed
                }
            }
        }

        private fun UpdateTurret() {
            val pt = idVec2()
            val turretOrig = idVec2()
            val right = idVec2()
            val dot: Float
            val angle: Float
            pt.x = gui!!.CursorX()
            pt.y = gui!!.CursorY()
            turretOrig.set(80.0f, 348.0f)
            pt.set(pt.minus(turretOrig))
            pt.NormalizeFast()
            right.x = 1.0f
            right.y = 0.0f
            dot = pt.times(right)
            angle = RAD2DEG(acos(dot))
            turretAngle = ClampFloat(0.0f, 90.0f, angle)
        }

        private fun UpdateButtons() {
            if (onFire.data) {
                val vec = idVec2()
                gui!!.HandleNamedEvent("DisableFireButton")
                Session.session.sw.PlayShaderDirectly("arcade_sargeshoot")
                bear!!.SetVisible(true)
                bearScale = 1.0f
                bear!!.SetSize(BEAR_SIZE, BEAR_SIZE)
                vec.x = Cos(DEG2RAD(turretAngle))
                vec.x += (1 - vec.x) * 0.18f
                vec.y = -Sin(DEG2RAD(turretAngle))
                turretForce = bearTurretForce.GetFloat()
                bear!!.position.x = 80 + 96 * vec.x
                bear!!.position.y = 334 + 96 * vec.y
                bear!!.velocity.x = vec.x * turretForce
                bear!!.velocity.y = vec.y * turretForce
                gunblast!!.position.x = 55 + 96 * vec.x
                gunblast!!.position.y = 310 + 100 * vec.y
                gunblast!!.SetVisible(true)
                gunblast!!.entColor.w = 1.0f
                gunblast!!.rotation = turretAngle
                gunblast!!.fadeOut = true
                bearHitTarget = false
                onFire.data = false
            }
        }

        private fun UpdateGame() {
            var i: Int
            if (onNewGame.data) {
                ResetGameState()
                goal!!.position.x = 550.0f
                goal!!.position.y = 164.0f
                goal!!.velocity.Zero()
                helicopter!!.position.x = 550.0f
                helicopter!!.position.y = 100.0f
                helicopter!!.velocity.Zero()
                bear!!.SetVisible(false)
                bearTurretAngle.SetFloat(0.0f)
                bearTurretForce.SetFloat(200.0f)
                gamerunning.data = true
            }
            if (onContinue.data) {
                gameOver = false
                timeRemaining = 60.0f
                onContinue.data = false
            }
            if (gamerunning.data) {
                val current_time = gui!!.GetTime()
                val rnd = idRandom(current_time)

                // Check for button presses
                UpdateButtons()
                if (bear != null) {
                    UpdateBear()
                }
                if (helicopter != null && goal != null) {
                    UpdateHelicopter()
                }

                // Update Wind
                if (windUpdateTime < current_time) {
                    val scale: Float
                    val width: Int
                    windForce = rnd.CRandomFloat() * (MAX_WINDFORCE * 0.75f)
                    if (windForce > 0) {
                        windForce += MAX_WINDFORCE * 0.25f
                        wind!!.rotation = 0.0f
                    } else {
                        windForce -= MAX_WINDFORCE * 0.25f
                        wind!!.rotation = 180.0f
                    }
                    scale = (1.0f - (MAX_WINDFORCE - abs(windForce)) / MAX_WINDFORCE)
                    width = (100 * scale).toInt()
                    if (windForce < 0) {
                        wind!!.position.x = (500 - width + 1).toFloat()
                    } else {
                        wind!!.position.x = 500.0f
                    }
                    wind!!.SetSize(width.toFloat(), 40.0f)
                    windUpdateTime = current_time + 7000 + rnd.RandomInt(5000)
                }

                // Update turret rotation angle
                if (turret != null) {
                    turretAngle = bearTurretAngle.GetFloat()
                    turret!!.rotation = turretAngle
                }
                i = 0
                while (i < entities.Num()) {
                    entities[i].Update(timeSlice)
                    i++
                }

                // Update countdown timer
                timeRemaining -= timeSlice
                timeRemaining = ClampFloat(0.0f, 99999.0f, timeRemaining)
                gui!!.SetStateString("time_remaining", va("%2.1f", timeRemaining))
                if (timeRemaining <= 0.0f && !gameOver) {
                    gameOver = true
                    updateScore = true
                }
                if (updateScore) {
                    UpdateScore()
                    updateScore = false
                }
            }
        }

        private fun UpdateScore() {
            if (gameOver) {
                gui!!.HandleNamedEvent("GameOver")
                return
            }
            goalsHit++
            gui!!.SetStateString("player_score", va("%d", goalsHit))

            // Check for level progression
            if (0 == goalsHit % 5) {
                currentLevel++
                gui!!.SetStateString("current_level", va("%d", currentLevel))
                Session.session.sw.PlayShaderDirectly("arcade_levelcomplete1", 3)
                timeRemaining += 30.0f
            }
        }

        override fun ParseInternalVar(_name: String?, src: idParser): Boolean {
            if (Icmp(_name!!, "gamerunning") == 0) {
                gamerunning.set(src.ParseBool())
                return true
            }
            if (Icmp(_name, "onFire") == 0) {
                onFire.set(src.ParseBool())
                return true
            }
            if (Icmp(_name, "onContinue") == 0) {
                onContinue.set(src.ParseBool())
                return true
            }
            if (Icmp(_name, "onNewGame") == 0) {
                onNewGame.set(src.ParseBool())
                return true
            }
            return super.ParseInternalVar(_name, src)
        }
    }
}

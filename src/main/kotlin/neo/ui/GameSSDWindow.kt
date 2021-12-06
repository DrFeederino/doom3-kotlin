package neo.ui

import neo.Renderer.Material
import neo.Renderer.Material.idMaterial
import neo.TempDump
import neo.TempDump.SERiAL
import neo.framework.*
import neo.framework.File_h.idFile
import neo.idlib.BV.Bounds.idBounds
import neo.idlib.Lib
import neo.idlib.Text.Parser.idParser
import neo.idlib.Text.Str
import neo.idlib.Text.Str.idStr
import neo.idlib.Text.Token.idToken
import neo.idlib.containers.List.idList
import neo.idlib.math.Math_h
import neo.idlib.math.Math_h.idMath
import neo.idlib.math.Random.idRandom
import neo.idlib.math.Vector.idVec2
import neo.idlib.math.Vector.idVec3
import neo.idlib.math.Vector.idVec4
import neo.sys.sys_public.sysEventType_t
import neo.sys.sys_public.sysEvent_s
import neo.ui.DeviceContext.idDeviceContext
import neo.ui.Rectangle.idRectangle
import neo.ui.SimpleWindow.drawWin_t
import neo.ui.UserInterfaceLocal.idUserInterfaceLocal
import neo.ui.Window.idWindow
import neo.ui.Winvar.idWinBool
import neo.ui.Winvar.idWinVar
import java.nio.*

/**
 *
 */
object GameSSDWindow {
    val ASTEROID_MATERIAL: String = "game/SSD/asteroid"
    val ASTRONAUT_MATERIAL: String = "game/SSD/astronaut"

    //
    /*
     *****************************************************************************
     * SSDCrossHair
     ****************************************************************************
     */
    val CROSSHAIR_STANDARD_MATERIAL: String = "game/SSD/crosshair_standard"
    val CROSSHAIR_SUPER_MATERIAL: String = "game/SSD/crosshair_super"
    const val ENTITY_START_DIST = 3000
    const val EXPLOSION_MATERIAL_COUNT = 2

    /*
     *****************************************************************************
     * SSDAsteroid
     ****************************************************************************
     */
    const val MAX_ASTEROIDS = 64

    //
    /*
     *****************************************************************************
     * SSDAstronaut
     ****************************************************************************
     */
    const val MAX_ASTRONAUT = 8

    /*
     *****************************************************************************
     * SSDExplosion
     ****************************************************************************
     */
    const val MAX_EXPLOSIONS = 64

    /*
     *****************************************************************************
     * SSDPoints
     ****************************************************************************
     */
    const val MAX_POINTS = 16

    //
    const val MAX_POWERUPS = 64

    /*
     *****************************************************************************
     * SSDProjectile
     ****************************************************************************
     */
    const val MAX_PROJECTILES = 64

    //
    const val POWERUP_MATERIAL_COUNT = 6
    val PROJECTILE_MATERIAL: String = "game/SSD/fball"
    const val V_HEIGHT = 480.0f

    //
    const val V_WIDTH = 640.0f
    const val Z_FAR = 4000.0f
    const val Z_NEAR = 100.0f
    val explosionMaterials: Array<String> = arrayOf(
        "game/SSD/fball",
        "game/SSD/teleport"
    )

    /*
     *****************************************************************************
     * SSDPowerup
     ****************************************************************************
     */
    val powerupMaterials /*[][2]*/: Array<Array<String>> = arrayOf(
        arrayOf("game/SSD/powerupHealthClosed", "game/SSD/powerupHealthOpen"),
        arrayOf("game/SSD/powerupSuperBlasterClosed", "game/SSD/powerupSuperBlasterOpen"),
        arrayOf("game/SSD/powerupNukeClosed", "game/SSD/powerupNukeOpen"),
        arrayOf("game/SSD/powerupRescueClosed", "game/SSD/powerupRescueOpen"),
        arrayOf("game/SSD/powerupBonusPointsClosed", "game/SSD/powerupBonusPointsOpen"),
        arrayOf("game/SSD/powerupDamageClosed", "game/SSD/powerupDamageOpen")
    )

    enum class SSD {
        SSD_ENTITY_BASE,  //= 0,
        SSD_ENTITY_ASTEROID, SSD_ENTITY_ASTRONAUT, SSD_ENTITY_EXPLOSION, SSD_ENTITY_POINTS, SSD_ENTITY_PROJECTILE, SSD_ENTITY_POWERUP
    }

    class SSDCrossHair  //
    {
        //	};
        var crosshairMaterial: Array<idMaterial?> = arrayOfNulls<idMaterial?>(CROSSHAIR_COUNT)
        var crosshairWidth = 0f
        var crosshairHeight = 0f
        var currentCrosshair = 0

        //				~SSDCrossHair();
        fun WriteToSaveGame(savefile: idFile) {
            savefile.WriteInt(currentCrosshair)
            savefile.WriteFloat(crosshairWidth)
            savefile.WriteFloat(crosshairHeight)
        }

        fun ReadFromSaveGame(savefile: idFile) {
            InitCrosshairs()
            currentCrosshair = savefile.ReadInt()
            crosshairWidth = savefile.ReadFloat()
            crosshairHeight = savefile.ReadFloat()
        }

        fun InitCrosshairs() {
            crosshairMaterial.get(CROSSHAIR_STANDARD) =
                DeclManager.declManager.FindMaterial(CROSSHAIR_STANDARD_MATERIAL)
            crosshairMaterial.get(CROSSHAIR_SUPER) = DeclManager.declManager.FindMaterial(CROSSHAIR_SUPER_MATERIAL)
            crosshairWidth = 64f
            crosshairHeight = 64f
            currentCrosshair = CROSSHAIR_STANDARD
        }

        fun Draw(dc: idDeviceContext, cursor: idVec2) {
            dc.DrawMaterial(
                cursor.x - crosshairWidth / 2, cursor.y - crosshairHeight / 2,
                crosshairWidth, crosshairHeight,
                crosshairMaterial.get(currentCrosshair), Lib.Companion.colorWhite, 1.0f, 1.0f
            )
        }

        companion object {
            const val CROSSHAIR_COUNT = 2

            //	enum {
            const val CROSSHAIR_STANDARD = 0
            const val CROSSHAIR_SUPER = 1
        }
    }

    /*
     *****************************************************************************
     * SSDEntity
     ****************************************************************************
     */
    open class SSDEntity {
        val position: idVec3 = idVec3()
        var currentTime = 0

        //
        var destroyed = false
        var elapsed = 0
        val foreColor: idVec4 = null

        //
        var game: idGameSSDWindow? = null
        var hitRadius = 0f
        var id = 0

        //
        var inUse = false
        var lastUpdate = 0

        //
        var matColor: idVec4? = null
        var material: idMaterial? = null
        var materialName: idStr? = null
        var noHit = false
        var noPlayerDamage = false
        var radius = 0f
        var rotation = 0f
        var size: idVec2? = null

        //
        var text: idStr? = null
        var textScale = 0f

        //SSDEntity Information
        var type: SSD? = null

        //	virtual				~SSDEntity();
        open fun WriteToSaveGame(savefile: idFile?) {
            savefile.WriteInt(type)
            game.WriteSaveGameString(materialName, savefile)
            savefile.Write(position)
            savefile.Write(size)
            savefile.WriteFloat(radius)
            savefile.WriteFloat(hitRadius)
            savefile.WriteFloat(rotation)
            savefile.Write(matColor)
            game.WriteSaveGameString(text, savefile)
            savefile.WriteFloat(textScale)
            savefile.Write(foreColor)
            savefile.WriteInt(currentTime)
            savefile.WriteInt(lastUpdate)
            savefile.WriteInt(elapsed)
            savefile.WriteBool(destroyed)
            savefile.WriteBool(noHit)
            savefile.WriteBool(noPlayerDamage)
            savefile.WriteBool(inUse)
        }

        open fun ReadFromSaveGame(savefile: idFile?, _game: idGameSSDWindow?) {
            type = SSD.values()[savefile.ReadInt()]
            game.ReadSaveGameString(materialName, savefile)
            SetMaterial(materialName.toString())
            savefile.Read(position)
            savefile.Read(size)
            radius = savefile.ReadFloat()
            hitRadius = savefile.ReadFloat()
            rotation = savefile.ReadFloat()
            savefile.Read(matColor)
            game.ReadSaveGameString(text, savefile)
            textScale = savefile.ReadFloat()
            savefile.Read(foreColor)
            game = _game
            currentTime = savefile.ReadInt()
            lastUpdate = savefile.ReadInt()
            elapsed = savefile.ReadInt()
            destroyed = savefile.ReadBool()
            noHit = savefile.ReadBool()
            noPlayerDamage = savefile.ReadBool()
            inUse = savefile.ReadBool()
        }

        fun EntityInit() {
            inUse = false
            type = SSD.SSD_ENTITY_BASE
            materialName = idStr("")
            material = null
            position.Zero()
            size.Zero()
            radius = 0.0f
            hitRadius = 0.0f
            rotation = 0.0f
            currentTime = 0
            lastUpdate = 0
            destroyed = false
            noHit = false
            noPlayerDamage = false
            matColor.set(1f, 1f, 1f, 1f)
            text = idStr("")
            textScale = 1.0f
            foreColor.set(1f, 1f, 1f, 1f)
        }

        fun SetGame(_game: idGameSSDWindow?) {
            game = _game
        }

        fun SetMaterial(_name: String?) {
            materialName.set(_name)
            material = DeclManager.declManager.FindMaterial(_name)
            material.SetSort(Material.SS_GUI.toFloat())
        }

        fun SetPosition(_position: idVec3?) {
            position.set(_position) //TODO:is this by value, or by reference?
        }

        fun SetSize(_size: idVec2?) {
            size = _size
        }

        fun SetRadius(_radius: Float, _hitFactor: Float /*= 1.0f*/) {
            radius = _radius
            hitRadius = _radius * _hitFactor
        }

        fun SetRotation(_rotation: Float) {
            rotation = _rotation
        }

        fun Update() {
            currentTime = game.ssdTime

            //Is this the first update
            if (lastUpdate == 0) {
                lastUpdate = currentTime
                return
            }
            elapsed = currentTime - lastUpdate
            EntityUpdate()
            lastUpdate = currentTime
        }

        fun HitTest(pt: idVec2?): Boolean {
            if (noHit) {
                return false
            }
            val screenPos = idVec3(WorldToScreen(position))

            //Scale the radius based on the distance from the player
            val scale = 1.0f - (screenPos.z - Z_NEAR) / (Z_FAR - Z_NEAR)
            val scaledRad = scale * hitRadius

            //So we can compare against the square of the length between two points
            val scaleRadSqr = scaledRad * scaledRad
            val diff = screenPos.ToVec2().oMinus(pt)
            val dist = Math.abs(diff.LengthSqr())
            return dist < scaleRadSqr
        }

        open fun EntityUpdate() {}
        fun Draw(dc: idDeviceContext?) {
            val persize = idVec2()
            val x: Float
            val y: Float
            val bounds = idBounds()
            bounds.set(0, idVec3(position.x - size.x / 2.0f, position.y - size.y / 2.0f, position.z))
            bounds.set(1, idVec3(position.x + size.x / 2.0f, position.y + size.y / 2.0f, position.z))
            val screenBounds = WorldToScreen(bounds)
            persize.x = Math.abs(screenBounds.get(1).x - screenBounds.get(0).x)
            persize.y = Math.abs(screenBounds.get(1).y - screenBounds.get(0).y)

//	idVec3 center = screenBounds.GetCenter();
            x = screenBounds.get(0).x
            y = screenBounds.get(1).y
            dc.DrawMaterialRotated(x, y, persize.x, persize.y, material, matColor, 1.0f, 1.0f, Math_h.DEG2RAD(rotation))
            if (text.Length() > 0) {
                val rect = idRectangle(x, y, DeviceContext.VIRTUAL_WIDTH, DeviceContext.VIRTUAL_HEIGHT)
                dc.DrawText(text.toString(), textScale, 0, foreColor, rect, false)
            }
        }

        fun DestroyEntity() {
            inUse = false
        }

        open fun OnHit(key: Int) {}
        open fun OnStrikePlayer() {}
        fun WorldToScreen(worldBounds: idBounds?): idBounds? {
            val screenMin = idVec3(WorldToScreen(worldBounds.get(0)))
            val screenMax = idVec3(WorldToScreen(worldBounds.get(1)))
            return idBounds(screenMin, screenMax)
        }

        fun WorldToScreen(worldPos: idVec3?): idVec3? {
            val d = 0.5f * V_WIDTH * idMath.Tan(Math_h.DEG2RAD(90.0f) / 2.0f)

            //World To Camera Coordinates
            val cameraTrans = idVec3(0, 0, d)
            val cameraPos = idVec3()
            cameraPos.set(worldPos.oPlus(cameraTrans))

            //Camera To Screen Coordinates
            val screenPos = idVec3()
            screenPos.x = d * cameraPos.x / cameraPos.z + (0.5f * V_WIDTH - 0.5f)
            screenPos.y = -d * cameraPos.y / cameraPos.z + (0.5f * V_HEIGHT - 0.5f)
            screenPos.z = cameraPos.z
            return screenPos
        }

        fun ScreenToWorld(screenPos: idVec3?): idVec3? {
            val worldPos = idVec3()
            worldPos.x = screenPos.x - 0.5f * V_WIDTH
            worldPos.y = -(screenPos.y - 0.5f * V_HEIGHT)
            worldPos.z = screenPos.z
            return worldPos
        }

        //
        //
        init {
            EntityInit()
        }
    }

    /*
     *****************************************************************************
     * SSDMover
     ****************************************************************************
     */
    open class SSDMover  //
        : SSDEntity() {
        val speed: idVec3? = idVec3()
        var rotationSpeed = 0f

        // virtual				~SSDMover();
        override fun WriteToSaveGame(savefile: idFile?) {
            super.WriteToSaveGame(savefile)
            savefile.Write(speed)
            savefile.WriteFloat(rotationSpeed)
        }

        override fun ReadFromSaveGame(savefile: idFile?, _game: idGameSSDWindow?) {
            super.ReadFromSaveGame(savefile, _game)
            savefile.Read(speed)
            rotationSpeed = savefile.ReadFloat()
        }

        fun MoverInit(_speed: idVec3?, _rotationSpeed: Float) {
            speed.set(_speed)
            rotationSpeed = _rotationSpeed
        }

        override fun EntityUpdate() {
            super.EntityUpdate()

            //Move forward based on speed (units per second)
            val moved = idVec3(speed.times(elapsed.toFloat() / 1000.0f))
            position.plusAssign(moved)
            val rotated = elapsed.toFloat() / 1000.0f * rotationSpeed * 360.0f
            rotation += rotated
            if (rotation >= 360) {
                rotation -= 360.0f
            }
            if (rotation < 0) {
                rotation += 360.0f
            }
        }
    }

    class SSDAsteroid  // ~SSDAsteroid();
        : SSDMover() {
        var health = 0
        override fun WriteToSaveGame(savefile: idFile?) {
            super.WriteToSaveGame(savefile)
            savefile.WriteInt(health)
        }

        override fun ReadFromSaveGame(savefile: idFile?, _game: idGameSSDWindow?) {
            super.ReadFromSaveGame(savefile, _game)
            health = savefile.ReadInt()
        }

        fun Init(
            _game: idGameSSDWindow?,
            startPosition: idVec3?,
            _size: idVec2?,
            _speed: Float,
            rotate: Float,
            _health: Int
        ) {
            EntityInit()
            MoverInit(idVec3(0, 0, -_speed), rotate)
            SetGame(_game)
            type = SSD.SSD_ENTITY_ASTEROID
            SetMaterial(ASTEROID_MATERIAL)
            SetSize(_size)
            SetRadius(Lib.Companion.Max(size.x, size.y), 0.3f)
            SetRotation(idGameSSDWindow.random.RandomInt(360.0).toFloat())
            position.set(startPosition)
            health = _health
        }

        companion object {
            //
            protected val asteroidPool: Array<SSDAsteroid?>? = arrayOfNulls<SSDAsteroid?>(MAX_ASTEROIDS)
            fun GetNewAsteroid(
                _game: idGameSSDWindow?,
                startPosition: idVec3?,
                _size: idVec2?,
                _speed: Float,
                rotate: Float,
                _health: Int
            ): SSDAsteroid? {
                for (i in 0 until MAX_ASTEROIDS) {
                    if (!asteroidPool.get(i).inUse) {
                        asteroidPool.get(i).Init(_game, startPosition, _size, _speed, rotate, _health)
                        asteroidPool.get(i).inUse = true
                        asteroidPool.get(i).id = i
                        return asteroidPool.get(i)
                    }
                }
                return null
            }

            fun GetSpecificAsteroid(id: Int): SSDAsteroid? {
                return asteroidPool.get(id)
            }

            fun WriteAsteroids(savefile: idFile?) {
                var count = 0
                for (i in 0 until MAX_ASTEROIDS) {
                    if (asteroidPool.get(i).inUse) {
                        count++
                    }
                }
                savefile.WriteInt(count)
                for (i in 0 until MAX_ASTEROIDS) {
                    if (asteroidPool.get(i).inUse) {
                        savefile.WriteInt(asteroidPool.get(i).id)
                        asteroidPool.get(i).WriteToSaveGame(savefile)
                    }
                }
            }

            fun ReadAsteroids(savefile: idFile?, _game: idGameSSDWindow?) {
                val count: Int
                count = savefile.ReadInt()
                for (i in 0 until count) {
                    var id: Int
                    id = savefile.ReadInt()
                    val ent = GetSpecificAsteroid(id)
                    ent.ReadFromSaveGame(savefile, _game)
                }
            }
        }
    }

    class SSDAstronaut  // ~SSDAstronaut();
        : SSDMover() {
        var health = 0
        override fun WriteToSaveGame(savefile: idFile?) {
            super.WriteToSaveGame(savefile)
            savefile.WriteInt(health)
        }

        override fun ReadFromSaveGame(savefile: idFile?, _game: idGameSSDWindow?) {
            super.ReadFromSaveGame(savefile, _game)
            health = savefile.ReadInt()
        }

        fun Init(_game: idGameSSDWindow?, startPosition: idVec3?, _speed: Float, rotate: Float, _health: Int) {
            EntityInit()
            MoverInit(idVec3(0, 0, -_speed), rotate)
            SetGame(_game)
            type = SSD.SSD_ENTITY_ASTRONAUT
            SetMaterial(ASTRONAUT_MATERIAL)
            SetSize(idVec2(256, 256))
            SetRadius(Lib.Companion.Max(size.x, size.y), 0.3f)
            SetRotation(idGameSSDWindow.random.RandomInt(360.0).toFloat())
            position.set(startPosition)
            health = _health
        }

        companion object {
            //
            protected val astronautPool: Array<SSDAstronaut?>? = arrayOfNulls<SSDAstronaut?>(MAX_ASTRONAUT)
            fun GetNewAstronaut(
                _game: idGameSSDWindow?,
                startPosition: idVec3?,
                _speed: Float,
                rotate: Float,
                _health: Int
            ): SSDAstronaut? {
                for (i in 0 until MAX_ASTRONAUT) {
                    if (!astronautPool.get(i).inUse) {
                        astronautPool.get(i).Init(_game, startPosition, _speed, rotate, _health)
                        astronautPool.get(i).inUse = true
                        astronautPool.get(i).id = i
                        return astronautPool.get(i)
                    }
                }
                return null
            }

            fun GetSpecificAstronaut(id: Int): SSDAstronaut? {
                return astronautPool.get(id)
            }

            fun WriteAstronauts(savefile: idFile?) {
                var count = 0
                for (i in 0 until MAX_ASTRONAUT) {
                    if (astronautPool.get(i).inUse) {
                        count++
                    }
                }
                savefile.WriteInt(count)
                for (i in 0 until MAX_ASTRONAUT) {
                    if (astronautPool.get(i).inUse) {
                        savefile.WriteInt(astronautPool.get(i).id)
                        astronautPool.get(i).WriteToSaveGame(savefile)
                    }
                }
            }

            fun ReadAstronauts(savefile: idFile?, _game: idGameSSDWindow?) {
                val count: Int
                count = savefile.ReadInt()
                for (i in 0 until count) {
                    var id: Int
                    id = savefile.ReadInt()
                    val ent = GetSpecificAstronaut(id)
                    ent.ReadFromSaveGame(savefile, _game)
                }
            }
        }
    }

    class SSDExplosion : SSDEntity() {
        var beginTime = 0

        //
        //The entity that is exploding
        var buddy: SSDEntity? = null
        var endTime = 0
        var explosionType = 0
        var finalSize: idVec2? = null
        var followBuddy = false
        var killBuddy = false

        // };
        var length = 0
        override fun WriteToSaveGame(savefile: idFile?) {
            super.WriteToSaveGame(savefile)
            savefile.Write(finalSize)
            savefile.WriteInt(length)
            savefile.WriteInt(beginTime)
            savefile.WriteInt(endTime)
            savefile.WriteInt(explosionType)
            savefile.WriteInt(buddy.type)
            savefile.WriteInt(buddy.id)
            savefile.WriteBool(killBuddy)
            savefile.WriteBool(followBuddy)
        }

        override fun ReadFromSaveGame(savefile: idFile?, _game: idGameSSDWindow?) {
            super.ReadFromSaveGame(savefile, _game)
            savefile.Read(finalSize)
            length = savefile.ReadInt()
            beginTime = savefile.ReadInt()
            endTime = savefile.ReadInt()
            explosionType = savefile.ReadInt()
            val type: SSD
            val id: Int
            type = SSD.values()[savefile.ReadInt()]
            id = savefile.ReadInt()

            //Get a pointer to my buddy
            buddy = _game.GetSpecificEntity(type, id)
            killBuddy = savefile.ReadBool()
            followBuddy = savefile.ReadBool()
        }

        fun Init(
            _game: idGameSSDWindow?,
            _position: idVec3?,
            _size: idVec2?,
            _length: Int,
            _type: Int,
            _buddy: SSDEntity?,
            _killBuddy: Boolean /*= true*/,
            _followBuddy: Boolean /*= true*/
        ) {
            EntityInit()
            SetGame(_game)
            type = SSD.SSD_ENTITY_EXPLOSION
            explosionType = _type
            SetMaterial(explosionMaterials.get(explosionType))
            SetPosition(_position)
            position.z -= 50f
            finalSize = _size
            length = _length
            beginTime = game.ssdTime
            endTime = beginTime + length
            buddy = _buddy
            killBuddy = _killBuddy
            followBuddy = _followBuddy

            //Explosion Starts from nothing and will increase in size until it gets to final size
            size.Zero()
            noPlayerDamage = true
            noHit = true
        }

        override fun EntityUpdate() {
            super.EntityUpdate()

            //Always set my position to my buddies position except change z to be on top
            if (followBuddy) {
                position.set(buddy.position)
                position.z -= 50f
            } else {
                //Only mess with the z if we are not following
                position.z = buddy.position.z - 50
            }

            //Scale the image based on the time
            size = finalSize.oMultiply(((currentTime - beginTime) / length).toFloat())

            //Destroy myself after the explosion is done
            if (currentTime > endTime) {
                destroyed = true
                if (killBuddy) {
                    //Destroy the exploding object
                    buddy.destroyed = true
                }
            }
        }

        companion object {
            // enum {
            const val EXPLOSION_NORMAL = 0
            const val EXPLOSION_TELEPORT = 1

            //
            protected val explosionPool: Array<SSDExplosion?>? = arrayOfNulls<SSDExplosion?>(MAX_EXPLOSIONS)

            @JvmOverloads
            fun GetNewExplosion(
                _game: idGameSSDWindow?,
                _position: idVec3?,
                _size: idVec2?,
                _length: Int,
                _type: Int,
                _buddy: SSDEntity?,
                _killBuddy: Boolean = true /*= true*/,
                _followBuddy: Boolean = true /*= true*/
            ): SSDExplosion? {
                for (i in 0 until MAX_EXPLOSIONS) {
                    if (!explosionPool.get(i).inUse) {
                        explosionPool.get(i)
                            .Init(_game, _position, _size, _length, _type, _buddy, _killBuddy, _followBuddy)
                        explosionPool.get(i).inUse = true
                        return explosionPool.get(i)
                    }
                }
                return null
            }

            fun GetSpecificExplosion(id: Int): SSDExplosion? {
                return explosionPool.get(id)
            }

            fun WriteExplosions(savefile: idFile?) {
                var count = 0
                for (i in 0 until MAX_EXPLOSIONS) {
                    if (explosionPool.get(i).inUse) {
                        count++
                    }
                }
                savefile.WriteInt(count)
                for (i in 0 until MAX_EXPLOSIONS) {
                    if (explosionPool.get(i).inUse) {
                        savefile.WriteInt(explosionPool.get(i).id)
                        explosionPool.get(i).WriteToSaveGame(savefile)
                    }
                }
            }

            fun ReadExplosions(savefile: idFile?, _game: idGameSSDWindow?) {
                val count: Int
                count = savefile.ReadInt()
                for (i in 0 until count) {
                    var id: Int
                    id = savefile.ReadInt()
                    val ent = GetSpecificExplosion(id)
                    ent.ReadFromSaveGame(savefile, _game)
                }
            }
        }

        // ~SSDExplosion();
        init {
            type = SSD.SSD_ENTITY_EXPLOSION
        }
    }

    class SSDPoints : SSDEntity() {
        val beginPosition: idVec3? = idVec3()
        val endPosition: idVec3? = idVec3()
        var beginColor: idVec4? = null
        var beginTime = 0
        var distance = 0
        var endColor: idVec4? = null
        var endTime = 0
        var length = 0
        override fun WriteToSaveGame(savefile: idFile?) {
            super.WriteToSaveGame(savefile)
            savefile.WriteInt(length)
            savefile.WriteInt(distance)
            savefile.WriteInt(beginTime)
            savefile.WriteInt(endTime)
            savefile.Write(beginPosition)
            savefile.Write(endPosition)
            savefile.Write(beginColor)
            savefile.Write(endColor)
        }

        override fun ReadFromSaveGame(savefile: idFile?, _game: idGameSSDWindow?) {
            super.ReadFromSaveGame(savefile, _game)
            length = savefile.ReadInt()
            distance = savefile.ReadInt()
            beginTime = savefile.ReadInt()
            endTime = savefile.ReadInt()
            savefile.Read(beginPosition)
            savefile.Read(endPosition)
            savefile.Read(beginColor)
            savefile.Read(endColor)
        }

        fun Init(
            _game: idGameSSDWindow?,
            _ent: SSDEntity?,
            _points: Int,
            _length: Int,
            _distance: Int,
            color: idVec4?
        ) {
            EntityInit()
            SetGame(_game)
            length = _length
            distance = _distance
            beginTime = game.ssdTime
            endTime = beginTime + length
            textScale = 0.4f
            text = idStr(Str.va("%d", _points))
            var width = 0f
            for (i in 0 until text.Length()) {
                width += game.GetDC().CharWidth(text.get(i), textScale).toFloat()
            }
            size.set(0f, 0f)

            //Set the start position at the top of the passed in entity
            position.set(WorldToScreen(_ent.position))
            position.set(ScreenToWorld(position))
            position.z = 0f
            position.x -= width / 2.0f
            beginPosition.set(position)
            endPosition.set(beginPosition)
            endPosition.y += _distance.toFloat()

            //beginColor.Set(0,1,0,1);
            endColor.set(1f, 1f, 1f, 0f)
            beginColor = color
            beginColor.w = 1f
            noPlayerDamage = true
            noHit = true
        }

        override fun EntityUpdate() {
            val t = (currentTime - beginTime).toFloat() / length.toFloat()

            //Move up from the start position
            position.Lerp(beginPosition, endPosition, t)

            //Interpolate the color
            foreColor.Lerp(beginColor, endColor, t)
            if (currentTime > endTime) {
                destroyed = true
            }
        }

        companion object {
            //
            protected val pointsPool: Array<SSDPoints?>? = arrayOfNulls<SSDPoints?>(MAX_POINTS)
            fun GetNewPoints(
                _game: idGameSSDWindow?,
                _ent: SSDEntity?,
                _points: Int,
                _length: Int,
                _distance: Int,
                color: idVec4?
            ): SSDPoints? {
                for (i in 0 until MAX_POINTS) {
                    if (!pointsPool.get(i).inUse) {
                        pointsPool.get(i).Init(_game, _ent, _points, _length, _distance, color)
                        pointsPool.get(i).inUse = true
                        return pointsPool.get(i)
                    }
                }
                return null
            }

            fun GetSpecificPoints(id: Int): SSDPoints? {
                return pointsPool.get(id)
            }

            fun WritePoints(savefile: idFile?) {
                var count = 0
                for (i in 0 until MAX_POINTS) {
                    if (pointsPool.get(i).inUse) {
                        count++
                    }
                }
                savefile.WriteInt(count)
                for (i in 0 until MAX_POINTS) {
                    if (pointsPool.get(i).inUse) {
                        savefile.WriteInt(pointsPool.get(i).id)
                        pointsPool.get(i).WriteToSaveGame(savefile)
                    }
                }
            }

            fun ReadPoints(savefile: idFile?, _game: idGameSSDWindow?) {
                val count: Int
                count = savefile.ReadInt()
                for (i in 0 until count) {
                    var id: Int
                    id = savefile.ReadInt()
                    val ent = GetSpecificPoints(id)
                    ent.ReadFromSaveGame(savefile, _game)
                }
            }
        }

        // ~SSDPoints();
        init {
            type = SSD.SSD_ENTITY_POINTS
        }
    }

    class SSDProjectile : SSDEntity() {
        val dir: idVec3? = idVec3()
        val endPosition: idVec3? = idVec3()
        val speed: idVec3? = idVec3()
        var beginTime = 0
        var endTime = 0
        override fun WriteToSaveGame(savefile: idFile?) {
            super.WriteToSaveGame(savefile)
            savefile.Write(dir)
            savefile.Write(speed)
            savefile.WriteInt(beginTime)
            savefile.WriteInt(endTime)
            savefile.Write(endPosition)
        }

        override fun ReadFromSaveGame(savefile: idFile?, _game: idGameSSDWindow?) {
            super.ReadFromSaveGame(savefile, _game)
            savefile.Read(dir)
            savefile.Read(speed)
            beginTime = savefile.ReadInt()
            endTime = savefile.ReadInt()
            savefile.Read(endPosition)
        }

        fun Init(_game: idGameSSDWindow?, _beginPosition: idVec3?, _endPosition: idVec3?, _speed: Float, _size: Float) {
            EntityInit()
            SetGame(_game)
            SetMaterial(PROJECTILE_MATERIAL)
            size.set(_size, _size)
            position.set(_beginPosition)
            endPosition.set(_endPosition)
            dir.set(_endPosition.minus(position))
            dir.Normalize()

            //speed.Zero();
            speed.z = _speed
            speed.y = speed.z
            speed.x = speed.y
            noHit = true
        }

        override fun EntityUpdate() {
            super.EntityUpdate()

            //Move forward based on speed (units per second)
            val moved = idVec3(dir.times(elapsed.toFloat() / 1000.0f * speed.z))
            position.plusAssign(moved)
            if (position.z > endPosition.z) {
                //We have reached our position
                destroyed = true
            }
        }

        companion object {
            //
            protected val projectilePool: Array<SSDProjectile?>? = arrayOfNulls<SSDProjectile?>(MAX_PROJECTILES)
            fun GetNewProjectile(
                _game: idGameSSDWindow?,
                _beginPosition: idVec3?,
                _endPosition: idVec3?,
                _speed: Float,
                _size: Float
            ): SSDProjectile? {
                for (i in 0 until MAX_PROJECTILES) {
                    if (!projectilePool.get(i).inUse) {
                        projectilePool.get(i).Init(_game, _beginPosition, _endPosition, _speed, _size)
                        projectilePool.get(i).inUse = true
                        return projectilePool.get(i)
                    }
                }
                return null
            }

            fun GetSpecificProjectile(id: Int): SSDProjectile? {
                return projectilePool.get(id)
            }

            fun WriteProjectiles(savefile: idFile?) {
                var count = 0
                for (i in 0 until MAX_PROJECTILES) {
                    if (projectilePool.get(i).inUse) {
                        count++
                    }
                }
                savefile.WriteInt(count)
                for (i in 0 until MAX_PROJECTILES) {
                    if (projectilePool.get(i).inUse) {
                        savefile.WriteInt(projectilePool.get(i).id)
                        projectilePool.get(i).WriteToSaveGame(savefile)
                    }
                }
            }

            fun ReadProjectiles(savefile: idFile?, _game: idGameSSDWindow?) {
                val count: Int
                count = savefile.ReadInt()
                for (i in 0 until count) {
                    var id: Int
                    id = savefile.ReadInt()
                    val ent = GetSpecificProjectile(id)
                    ent.ReadFromSaveGame(savefile, _game)
                }
            }
        }

        // ~SSDProjectile();
        init {
            type = SSD.SSD_ENTITY_PROJECTILE
        }
    }
    //    
    /**
     * Powerups work in two phases: 1.) Closed container hurls at you If you
     * shoot the container it open 3.) If an opened powerup hits the player he
     * aquires the powerup Powerup Types: Health - Give a specific amount of
     * health Super Blaster - Increases the power of the blaster (lasts a
     * specific amount of time) Asteroid Nuke - Destroys all asteroids on screen
     * as soon as it is aquired Rescue Powerup - Rescues all astronauts as soon
     * as it is acquited Bonus Points - Gives some bonus points when acquired
     */
    class SSDPowerup  // virtual ~SSDPowerup();
        : SSDMover() {
        //        };
        //
        var powerupState = 0

        //
        var powerupType = 0
        override fun WriteToSaveGame(savefile: idFile?) {
            super.WriteToSaveGame(savefile)
            savefile.WriteInt(powerupState)
            savefile.WriteInt(powerupType)
        }

        override fun ReadFromSaveGame(savefile: idFile?, _game: idGameSSDWindow?) {
            super.ReadFromSaveGame(savefile, _game)
            powerupState = savefile.ReadInt()
            powerupType = savefile.ReadInt()
        }

        override fun OnHit(key: Int) {
            if (powerupState == POWERUP_STATE_CLOSED) {

                //Small explosion to indicate it is opened
                val explosion = SSDExplosion.GetNewExplosion(
                    game,
                    position,
                    size.oMultiply(2.0f),
                    300,
                    SSDExplosion.EXPLOSION_NORMAL,
                    this,
                    false,
                    true
                )
                game.entities.Append(explosion)
                powerupState = POWERUP_STATE_OPEN
                SetMaterial(powerupMaterials.get(powerupType).get(powerupState))
            } else {
                //Destory the powerup with a big explosion
                val explosion: SSDExplosion =
                    GetNewExplosion(game, position, size.oMultiply(2f), 300, SSDExplosion.EXPLOSION_NORMAL, this)
                game.entities.Append(explosion)
                game.PlaySound("arcade_explode")
                noHit = true
                noPlayerDamage = true
            }
        }

        override fun OnStrikePlayer() {
            if (powerupState == POWERUP_STATE_OPEN) {
                //The powerup was open so activate it
                OnActivatePowerup()
            }

            //Just destroy the powerup
            destroyed = true
        }

        fun OnOpenPowerup() {}
        fun OnActivatePowerup() {
            when (powerupType) {
                POWERUP_TYPE_HEALTH -> {
                    game.AddHealth(10)
                }
                POWERUP_TYPE_SUPER_BLASTER -> {
                    game.OnSuperBlaster()
                }
                POWERUP_TYPE_ASTEROID_NUKE -> {
                    game.OnNuke()
                }
                POWERUP_TYPE_RESCUE_ALL -> {
                    game.OnRescueAll()
                }
                POWERUP_TYPE_BONUS_POINTS -> {
                    val points = (idGameSSDWindow.random.RandomInt(5.0) + 1) * 100
                    game.AddScore(this, points)
                }
                POWERUP_TYPE_DAMAGE -> {
                    game.AddDamage(10)
                    game.PlaySound("arcade_explode")
                }
            }
        }

        fun Init(_game: idGameSSDWindow?, _speed: Float, _rotation: Float) {
            EntityInit()
            MoverInit(idVec3(0, 0, -_speed), _rotation)
            SetGame(_game)
            SetSize(idVec2(200, 200))
            SetRadius(Lib.Companion.Max(size.x, size.y), 0.3f)
            type = SSD.SSD_ENTITY_POWERUP
            val startPosition = idVec3()
            startPosition.x = idGameSSDWindow.random.RandomInt(V_WIDTH.toDouble()) - V_WIDTH / 2.0f
            startPosition.y = idGameSSDWindow.random.RandomInt(V_HEIGHT.toDouble()) - V_HEIGHT / 2.0f
            startPosition.z = ENTITY_START_DIST.toFloat()
            position.set(startPosition)
            //SetPosition(startPosition);
            powerupState = POWERUP_STATE_CLOSED
            powerupType = idGameSSDWindow.random.RandomInt((POWERUP_TYPE_MAX + 1).toDouble())
            if (powerupType >= POWERUP_TYPE_MAX) {
                powerupType = 0
            }

            /*OutputDebugString(va("Powerup: %d\n", powerupType));
             if(powerupType == 0) {
             int x = 0;
             }*/SetMaterial(powerupMaterials.get(powerupType).get(powerupState))
        }

        companion object {
            //
            protected val powerupPool: Array<SSDPowerup?>? = arrayOfNulls<SSDPowerup?>(MAX_POWERUPS)

            //        enum POWERUP_STATE {
            const val POWERUP_STATE_CLOSED = 0
            const val POWERUP_STATE_OPEN = 1
            const val POWERUP_TYPE_ASTEROID_NUKE = 2
            const val POWERUP_TYPE_BONUS_POINTS = 4
            const val POWERUP_TYPE_DAMAGE = 5

            //        };
            //        enum POWERUP_TYPE {
            const val POWERUP_TYPE_HEALTH = 0
            const val POWERUP_TYPE_MAX = 6
            const val POWERUP_TYPE_RESCUE_ALL = 3
            const val POWERUP_TYPE_SUPER_BLASTER = 1
            fun GetNewPowerup(_game: idGameSSDWindow?, _speed: Float, _rotation: Float): SSDPowerup? {
                for (i in 0 until MAX_POWERUPS) {
                    if (!powerupPool.get(i).inUse) {
                        powerupPool.get(i).Init(_game, _speed, _rotation)
                        powerupPool.get(i).inUse = true
                        return powerupPool.get(i)
                    }
                }
                return null
            }

            fun GetSpecificPowerup(id: Int): SSDPowerup? {
                return powerupPool.get(id)
            }

            fun WritePowerups(savefile: idFile?) {
                var count = 0
                for (i in 0 until MAX_POWERUPS) {
                    if (powerupPool.get(i).inUse) {
                        count++
                    }
                }
                savefile.WriteInt(count)
                for (i in 0 until MAX_POWERUPS) {
                    if (powerupPool.get(i).inUse) {
                        savefile.WriteInt(powerupPool.get(i).id)
                        powerupPool.get(i).WriteToSaveGame(savefile)
                    }
                }
            }

            fun ReadPowerups(savefile: idFile?, _game: idGameSSDWindow?) {
                val count: Int
                count = savefile.ReadInt()
                for (i in 0 until count) {
                    var id: Int
                    id = savefile.ReadInt()
                    val ent = GetSpecificPowerup(id)
                    ent.ReadFromSaveGame(savefile, _game)
                }
            }
        }
    }

    class SSDLevelData_t : SERiAL {
        var needToWin = 0
        var spawnBuffer = 0f
        override fun AllocBuffer(): ByteBuffer? {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        override fun Read(buffer: ByteBuffer?) {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        override fun Write(): ByteBuffer? {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }
    }

    class SSDAsteroidData_t : SERiAL {
        var asteroidDamage = 0
        var asteroidHealth = 0
        var asteroidPoints = 0
        var rotateMin = 0f
        var rotateMax = 0f
        var sizeMin = 0f
        var sizeMax = 0f
        var spawnMin = 0
        var spawnMax = 0
        var speedMin = 0f
        var speedMax = 0f
        override fun AllocBuffer(): ByteBuffer? {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        override fun Read(buffer: ByteBuffer?) {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        override fun Write(): ByteBuffer? {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }
    }

    class SSDAstronautData_t : SERiAL {
        var health = 0
        var penalty = 0
        var points = 0
        var rotateMin = 0f
        var rotateMax = 0f
        var spawnMin = 0
        var spawnMax = 0
        var speedMin = 0f
        var speedMax = 0f
        override fun AllocBuffer(): ByteBuffer? {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        override fun Read(buffer: ByteBuffer?) {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        override fun Write(): ByteBuffer? {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }
    }

    class SSDPowerupData_t : SERiAL {
        var rotateMin = 0f
        var rotateMax = 0f
        var spawnMin = 0
        var spawnMax = 0
        var speedMin = 0f
        var speedMax = 0f
        override fun AllocBuffer(): ByteBuffer? {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        override fun Read(buffer: ByteBuffer?) {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        override fun Write(): ByteBuffer? {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }
    }

    class SSDWeaponData_t : SERiAL {
        var damage = 0
        var size = 0
        var speed = 0f
        override fun AllocBuffer(): ByteBuffer? {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        override fun Read(buffer: ByteBuffer?) {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        override fun Write(): ByteBuffer? {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }
    }

    /**
     * SSDLevelStats_t Data that is used for each level. This data is reset each
     * new level.
     */
    class SSDLevelStats_t {
        var destroyedAsteroids = 0
        var hitCount = 0

        //
        var killedAstronauts = 0
        var nextAsteroidSpawnTime = 0

        //
        //Astronaut Level Data
        var nextAstronautSpawnTime = 0

        //
        //Powerup Level Data
        var nextPowerupSpawnTime = 0
        var savedAstronauts = 0
        var shotCount = 0

        //
        var targetEnt: SSDEntity? = null
    }

    /**
     * SSDGameStats_t Data that is used for the game that is currently running.
     * Memset this to completely reset the game
     */
    class SSDGameStats_t : SERiAL {
        var currentLevel = 0

        //
        var currentWeapon = 0
        var gameRunning = false

        //
        var health = 0

        //
        var levelStats: SSDLevelStats_t? = null
        var nextLevel = 0
        var prebonusscore = 0

        //
        var score = 0
        override fun AllocBuffer(): ByteBuffer? {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        override fun Read(buffer: ByteBuffer?) {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        override fun Write(): ByteBuffer? {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }
    }

    /*
     *****************************************************************************
     * idGameSSDWindow
     ****************************************************************************
     */
    class idGameSSDWindow : idWindow {
        val asteroidData: idList<SSDAsteroidData_t?>? = idList()
        val astronautData: idList<SSDAstronautData_t?>? = idList()
        val entities: idList<SSDEntity?>? = idList()
        val levelData: idList<SSDLevelData_t?>? = idList()
        val powerupData: idList<SSDPowerupData_t?>? = idList()

        //	~idGameSSDWindow();
        val weaponData: idList<SSDWeaponData_t?>? = idList()

        //WinVars used to call functions from the guis
        var beginLevel: idWinBool? = null
        var continueGame: idWinBool? = null

        //
        var crosshair: SSDCrossHair? = null

        //
        var currentSound = 0

        //
        //All current game data is stored in this structure (except the entity list)
        var gameStats: SSDGameStats_t? = null

        //
        //Level Data
        var levelCount = 0
        var refreshGuiData: idWinBool? = null
        var resetGame: idWinBool? = null
        var screenBounds: idBounds? = null
        var ssdTime = 0

        //
        //
        //
        var superBlasterTimeout = 0

        //
        //
        //Weapon Data
        var weaponCount = 0

        constructor(gui: idUserInterfaceLocal?) : super(gui) {
            this.gui = gui
            CommonInit()
        }

        constructor(dc: idDeviceContext?, gui: idUserInterfaceLocal?) : super(dc, gui) {
            this.dc = dc
            this.gui = gui
            CommonInit()
        }

        override fun WriteToSaveGame(savefile: idFile?) {
            super.WriteToSaveGame(savefile)
            savefile.WriteInt(ssdTime)
            beginLevel.WriteToSaveGame(savefile)
            resetGame.WriteToSaveGame(savefile)
            continueGame.WriteToSaveGame(savefile)
            refreshGuiData.WriteToSaveGame(savefile)
            crosshair.WriteToSaveGame(savefile)
            savefile.Write(screenBounds)
            savefile.WriteInt(levelCount)
            for (i in 0 until levelCount) {
                savefile.Write(levelData.get(i))
                savefile.Write(asteroidData.get(i))
                savefile.Write(astronautData.get(i))
                savefile.Write(powerupData.get(i))
            }
            savefile.WriteInt(weaponCount)
            for (i in 0 until weaponCount) {
                savefile.Write(weaponData.get(i))
            }
            savefile.WriteInt(superBlasterTimeout)
            savefile.Write(gameStats)

            //Write All Static Entities
            SSDAsteroid.WriteAsteroids(savefile)
            SSDAstronaut.WriteAstronauts(savefile)
            SSDExplosion.WriteExplosions(savefile)
            SSDPoints.WritePoints(savefile)
            SSDProjectile.WriteProjectiles(savefile)
            SSDPowerup.WritePowerups(savefile)
            val entCount = entities.Num()
            savefile.WriteInt(entCount)
            for (i in 0 until entCount) {
                savefile.WriteInt(entities.get(i).type)
                savefile.WriteInt(entities.get(i).id)
            }
        }

        override fun ReadFromSaveGame(savefile: idFile?) {
            super.ReadFromSaveGame(savefile)
            ssdTime = savefile.ReadInt()
            beginLevel.ReadFromSaveGame(savefile)
            resetGame.ReadFromSaveGame(savefile)
            continueGame.ReadFromSaveGame(savefile)
            refreshGuiData.ReadFromSaveGame(savefile)
            crosshair.ReadFromSaveGame(savefile)
            savefile.Read(screenBounds)
            levelCount = savefile.ReadInt()
            for (i in 0 until levelCount) {
                val newLevel = SSDLevelData_t()
                savefile.Read(newLevel)
                levelData.Append(newLevel)
                val newAsteroid = SSDAsteroidData_t()
                savefile.Read(newAsteroid)
                asteroidData.Append(newAsteroid)
                val newAstronaut = SSDAstronautData_t()
                savefile.Read(newAstronaut)
                astronautData.Append(newAstronaut)
                val newPowerup = SSDPowerupData_t()
                savefile.Read(newPowerup)
                powerupData.Append(newPowerup)
            }
            weaponCount = savefile.ReadInt()
            for (i in 0 until weaponCount) {
                val newWeapon = SSDWeaponData_t()
                savefile.Read(newWeapon)
                weaponData.Append(newWeapon)
            }
            superBlasterTimeout = savefile.ReadInt()
            savefile.Read(gameStats)
            //Reset this because it is no longer valid
            gameStats.levelStats.targetEnt = null
            SSDAsteroid.ReadAsteroids(savefile, this)
            SSDAstronaut.ReadAstronauts(savefile, this)
            SSDExplosion.ReadExplosions(savefile, this)
            SSDPoints.ReadPoints(savefile, this)
            SSDProjectile.ReadProjectiles(savefile, this)
            SSDPowerup.ReadPowerups(savefile, this)
            val entCount: Int
            entCount = savefile.ReadInt()
            for (i in 0 until entCount) {
                var type: SSD
                var id: Int
                type = SSD.values()[savefile.ReadInt()]
                id = savefile.ReadInt()
                val ent = GetSpecificEntity(type, id)
                if (ent != null) {
                    entities.Append(ent)
                }
            }
        }

        override fun HandleEvent(event: sysEvent_s?, updateVisuals: BooleanArray?): String? {

            // need to call this to allow proper focus and capturing on embedded children
            val ret = super.HandleEvent(event, updateVisuals)
            if (!gameStats.gameRunning) {
                return ret
            }
            val key = event.evValue
            if (event.evType == sysEventType_t.SE_KEY) {
                if (0 == event.evValue2) {
                    return ret
                }
                if (key == KeyInput.K_MOUSE1 || key == KeyInput.K_MOUSE2) {
                    FireWeapon(key)
                } else {
                    return ret
                }
            }
            return ret
        }

        override fun GetWinVarByName(
            _name: String?,
            winLookup: Boolean /*= false*/,
            owner: Array<drawWin_t?>? /*= NULL*/
        ): idWinVar? {
            var retVar: idWinVar? = null
            if (idStr.Companion.Icmp(_name, "beginLevel") == 0) {
                retVar = beginLevel
            }
            if (idStr.Companion.Icmp(_name, "resetGame") == 0) {
                retVar = resetGame
            }
            if (idStr.Companion.Icmp(_name, "continueGame") == 0) {
                retVar = continueGame
            }
            if (idStr.Companion.Icmp(_name, "refreshGuiData") == 0) {
                retVar = refreshGuiData
            }
            return retVar ?: super.GetWinVarByName(_name, winLookup, owner)
        }

        override fun Draw(time: Int, x: Float, y: Float) {

            //Update the game every frame before drawing
            UpdateGame()
            RefreshGuiData()
            if (gameStats.gameRunning) {
                ZOrderEntities()

                //Draw from back to front
                for (i in entities.Num() - 1 downTo 0) {
                    entities.get(i).Draw(dc)
                }

                //The last thing to draw is the crosshair
                val cursor = idVec2()
                //GetCursor(cursor);
                cursor.x = gui.CursorX()
                cursor.y = gui.CursorY()
                crosshair.Draw(dc, cursor)
            }
        }

        fun AddHealth(health: Int) {
            gameStats.health += health
            gameStats.health = Lib.Companion.Min(100, gameStats.health)
        }

        fun AddScore(ent: SSDEntity?, points: Int) {
            val pointsEnt: SSDPoints?
            pointsEnt = if (points > 0) {
                SSDPoints.GetNewPoints(this, ent, points, 1000, 50, idVec4(0, 1, 0, 1))
            } else {
                SSDPoints.GetNewPoints(this, ent, points, 1000, 50, idVec4(1, 0, 0, 1))
            }
            entities.Append(pointsEnt)
            gameStats.score += points
            gui.SetStateString("player_score", Str.va("%d", gameStats.score))
        }

        fun AddDamage(damage: Int) {
            gameStats.health -= damage
            gui.SetStateString("player_health", Str.va("%d", gameStats.health))
            gui.HandleNamedEvent("playerDamage")
            if (gameStats.health <= 0) {
                //The player is dead
                GameOver()
            }
        }

        fun OnNuke() {
            gui.HandleNamedEvent("nuke")

            //Destory All Asteroids
            for (i in 0 until entities.Num()) {
                if (entities.get(i).type == SSD.SSD_ENTITY_ASTEROID) {

                    //The asteroid has been destroyed
                    val explosion: SSDExplosion = GetNewExplosion(
                        this,
                        entities.get(i).position,
                        entities.get(i).size.oMultiply(2f),
                        300,
                        SSDExplosion.EXPLOSION_NORMAL,
                        entities.get(i)
                    )
                    entities.Append(explosion)
                    AddScore(entities.get(i), asteroidData.get(gameStats.currentLevel).asteroidPoints)

                    //Don't let the player hit it anymore because
                    entities.get(i).noHit = true
                    gameStats.levelStats.destroyedAsteroids++
                }
            }
            PlaySound("arcade_explode")

            //Check to see if a nuke ends the level
            /*if(gameStats.levelStats.destroyedAsteroids >= levelData[gameStats.currentLevel].needToWin) {
             LevelComplete();

             }*/
        }

        fun OnRescueAll() {
            gui.HandleNamedEvent("rescueAll")

            //Rescue All Astronauts
            for (i in 0 until entities.Num()) {
                if (entities.get(i).type == SSD.SSD_ENTITY_ASTRONAUT) {
                    AstronautStruckPlayer(entities.get(i) as SSDAstronaut?)
                }
            }
        }

        fun OnSuperBlaster() {
            StartSuperBlaster()
        }

        fun GetSpecificEntity(type: SSD?, id: Int): SSDEntity? {
            var ent: SSDEntity? = null
            when (type) {
                SSD.SSD_ENTITY_ASTEROID -> ent = SSDAsteroid.GetSpecificAsteroid(id)
                SSD.SSD_ENTITY_ASTRONAUT -> ent = SSDAstronaut.GetSpecificAstronaut(id)
                SSD.SSD_ENTITY_EXPLOSION -> ent = SSDExplosion.GetSpecificExplosion(id)
                SSD.SSD_ENTITY_POINTS -> ent = SSDPoints.GetSpecificPoints(id)
                SSD.SSD_ENTITY_PROJECTILE -> ent = SSDProjectile.GetSpecificProjectile(id)
                SSD.SSD_ENTITY_POWERUP -> ent = SSDPowerup.GetSpecificPowerup(id)
            }
            return ent
        }

        fun PlaySound(sound: String?) {
            Session.Companion.session.sw.PlayShaderDirectly(sound, currentSound)
            currentSound++
            if (currentSound >= MAX_SOUND_CHANNEL) {
                currentSound = 0
            }
        }

        //
        //Initialization
        override fun ParseInternalVar(_name: String?, src: idParser?): Boolean {
            if (idStr.Companion.Icmp(_name, "beginLevel") == 0) {
                beginLevel.oSet(src.ParseBool())
                return true
            }
            if (idStr.Companion.Icmp(_name, "resetGame") == 0) {
                resetGame.oSet(src.ParseBool())
                return true
            }
            if (idStr.Companion.Icmp(_name, "continueGame") == 0) {
                continueGame.oSet(src.ParseBool())
                return true
            }
            if (idStr.Companion.Icmp(_name, "refreshGuiData") == 0) {
                refreshGuiData.oSet(src.ParseBool())
                return true
            }
            if (idStr.Companion.Icmp(_name, "levelcount") == 0) {
                levelCount = src.ParseInt()
                for (i in 0 until levelCount) {
                    val newLevel = SSDLevelData_t()
                    //                    memset(newLevel, 0, sizeof(SSDLevelData_t));
                    levelData.Append(newLevel)
                    val newAsteroid = SSDAsteroidData_t()
                    //                    memset(newAsteroid, 0, sizeof(SSDAsteroidData_t));
                    asteroidData.Append(newAsteroid)
                    val newAstronaut = SSDAstronautData_t()
                    //                    memset(newAstronaut, 0, sizeof(SSDAstronautData_t));
                    astronautData.Append(newAstronaut)
                    val newPowerup = SSDPowerupData_t()
                    //                    memset(newPowerup, 0, sizeof(SSDPowerupData_t));
                    powerupData.Append(newPowerup)
                }
                return true
            }
            if (idStr.Companion.Icmp(_name, "weaponCount") == 0) {
                weaponCount = src.ParseInt()
                for (i in 0 until weaponCount) {
                    val newWeapon = SSDWeaponData_t()
                    //                    memset(newWeapon, 0, sizeof(SSDWeaponData_t));
                    weaponData.Append(newWeapon)
                }
                return true
            }
            if (FindText(_name, "leveldata", false) >= 0) {
                val tempName = idStr(_name)
                val level = TempDump.atoi(tempName.Right(2)) - 1
                val levelData = idStr()
                ParseString(src, levelData)
                ParseLevelData(level, levelData)
                return true
            }
            if (FindText(_name, "asteroiddata", false) >= 0) {
                val tempName = idStr(_name)
                val level = TempDump.atoi(tempName.Right(2)) - 1
                val asteroidData = idStr()
                ParseString(src, asteroidData)
                ParseAsteroidData(level, asteroidData)
                return true
            }
            if (FindText(_name, "weapondata", false) >= 0) {
                val tempName = idStr(_name)
                val weapon = TempDump.atoi(tempName.Right(2)) - 1
                val weaponData = idStr()
                ParseString(src, weaponData)
                ParseWeaponData(weapon, weaponData)
                return true
            }
            if (FindText(_name, "astronautdata", false) >= 0) {
                val tempName = idStr(_name)
                val level = TempDump.atoi(tempName.Right(2)) - 1
                val astronautData = idStr()
                ParseString(src, astronautData)
                ParseAstronautData(level, astronautData)
                return true
            }
            if (FindText(_name, "powerupdata", false) >= 0) {
                val tempName = idStr(_name)
                val level = TempDump.atoi(tempName.Right(2)) - 1
                val powerupData = idStr()
                ParseString(src, powerupData)
                ParsePowerupData(level, powerupData)
                return true
            }
            return super.ParseInternalVar(_name, src)
        }

        private fun ParseLevelData(level: Int, levelDataString: idStr?) {
            val parser = idParser()
            var token: idToken
            parser.LoadMemory(levelDataString.toString(), levelDataString.Length(), "LevelData")
            levelData.get(level).spawnBuffer = parser.ParseFloat()
            levelData.get(level).needToWin = parser.ParseInt() //Required Destroyed
        }

        private fun ParseAsteroidData(level: Int, asteroidDataString: idStr?) {
            val parser = idParser()
            var token: idToken
            parser.LoadMemory(asteroidDataString.toString(), asteroidDataString.Length(), "AsteroidData")
            asteroidData.get(level).speedMin = parser.ParseFloat() //Speed Min
            asteroidData.get(level).speedMax = parser.ParseFloat() //Speed Max
            asteroidData.get(level).sizeMin = parser.ParseFloat() //Size Min
            asteroidData.get(level).sizeMax = parser.ParseFloat() //Size Max
            asteroidData.get(level).rotateMin = parser.ParseFloat() //Rotate Min (rotations per second)
            asteroidData.get(level).rotateMax = parser.ParseFloat() //Rotate Max (rotations per second)
            asteroidData.get(level).spawnMin = parser.ParseInt() //Spawn Min
            asteroidData.get(level).spawnMax = parser.ParseInt() //Spawn Max
            asteroidData.get(level).asteroidHealth = parser.ParseInt() //Health of the asteroid
            asteroidData.get(level).asteroidDamage = parser.ParseInt() //Asteroid Damage
            asteroidData.get(level).asteroidPoints = parser.ParseInt() //Points awarded for destruction
        }

        private fun ParseWeaponData(weapon: Int, weaponDataString: idStr?) {
            val parser = idParser()
            var token: idToken
            parser.LoadMemory(weaponDataString.toString(), weaponDataString.Length(), "WeaponData")
            weaponData.get(weapon).speed = parser.ParseFloat()
            weaponData.get(weapon).damage = parser.ParseFloat().toInt()
            weaponData.get(weapon).size = parser.ParseFloat().toInt()
        }

        private fun ParseAstronautData(level: Int, astronautDataString: idStr?) {
            val parser = idParser()
            var token: idToken
            parser.LoadMemory(astronautDataString.toString(), astronautDataString.Length(), "AstronautData")
            astronautData.get(level).speedMin = parser.ParseFloat() //Speed Min
            astronautData.get(level).speedMax = parser.ParseFloat() //Speed Max
            astronautData.get(level).rotateMin = parser.ParseFloat() //Rotate Min (rotations per second)
            astronautData.get(level).rotateMax = parser.ParseFloat() //Rotate Max (rotations per second)
            astronautData.get(level).spawnMin = parser.ParseInt() //Spawn Min
            astronautData.get(level).spawnMax = parser.ParseInt() //Spawn Max
            astronautData.get(level).health = parser.ParseInt() //Health of the asteroid
            astronautData.get(level).points = parser.ParseInt() //Asteroid Damage
            astronautData.get(level).penalty = parser.ParseInt() //Points awarded for destruction
        }

        private fun ParsePowerupData(level: Int, powerupDataString: idStr?) {
            val parser = idParser()
            var token: idToken
            parser.LoadMemory(powerupDataString.toString(), powerupDataString.Length(), "PowerupData")
            powerupData.get(level).speedMin = parser.ParseFloat() //Speed Min
            powerupData.get(level).speedMax = parser.ParseFloat() //Speed Max
            powerupData.get(level).rotateMin = parser.ParseFloat() //Rotate Min (rotations per second)
            powerupData.get(level).rotateMax = parser.ParseFloat() //Rotate Max (rotations per second)
            powerupData.get(level).spawnMin = parser.ParseInt() //Spawn Min
            powerupData.get(level).spawnMax = parser.ParseInt() //Spawn Max
        }

        private fun CommonInit() {
            crosshair.InitCrosshairs()
            beginLevel.data = false
            resetGame.data = false
            continueGame.data = false
            refreshGuiData.data = false
            ssdTime = 0
            levelCount = 0
            weaponCount = 0
            screenBounds = idBounds(idVec3(-320, -240, 0), idVec3(320, 240, 0))
            superBlasterTimeout = 0
            currentSound = 0

            //Precahce all assets that are loaded dynamically
            DeclManager.declManager.FindMaterial(ASTEROID_MATERIAL)
            DeclManager.declManager.FindMaterial(ASTRONAUT_MATERIAL)
            for (i in 0 until EXPLOSION_MATERIAL_COUNT) {
                DeclManager.declManager.FindMaterial(explosionMaterials.get(i))
            }
            DeclManager.declManager.FindMaterial(PROJECTILE_MATERIAL)
            for (i in 0 until POWERUP_MATERIAL_COUNT) {
                DeclManager.declManager.FindMaterial(powerupMaterials.get(i).get(0))
                DeclManager.declManager.FindMaterial(powerupMaterials.get(i).get(1))
            }

            // Precache sounds
            DeclManager.declManager.FindSound("arcade_blaster")
            DeclManager.declManager.FindSound("arcade_capture ")
            DeclManager.declManager.FindSound("arcade_explode")
            ResetGameStats()
        }

        private fun ResetGameStats() {
            ResetEntities()

            //Reset the gamestats structure
//            memset(gameStats, 0);
            gameStats = SSDGameStats_t()
            gameStats.health = 100
        }

        private fun ResetLevelStats() {
            ResetEntities()

            //Reset the level statistics structure
//            memset(gameStats.levelStats, 0, sizeof(gameStats.levelStats));
            gameStats.levelStats = SSDLevelStats_t()
        }

        private fun ResetEntities() {
            //Destroy all of the entities
            for (i in 0 until entities.Num()) {
                entities.get(i).DestroyEntity()
            }
            entities.Clear()
        }

        //Game Running Methods
        private fun StartGame() {
            gameStats.gameRunning = true
        }

        private fun StopGame() {
            gameStats.gameRunning = false
        }

        private fun GameOver() {
            StopGame()
            gui.HandleNamedEvent("gameOver")
        }

        //Starting the Game
        private fun BeginLevel(level: Int) {
            ResetLevelStats()
            gameStats.currentLevel = level
            StartGame()
        }

        /**
         * Continue game resets the players health
         */
        private fun ContinueGame() {
            gameStats.health = 100
            StartGame()
        }

        //Stopping the Game
        private fun LevelComplete() {
            gameStats.prebonusscore = gameStats.score

            // Add the bonuses
            val accuracy: Int
            accuracy = if (0 == gameStats.levelStats.shotCount) {
                0
            } else {
                (gameStats.levelStats.hitCount.toFloat() / gameStats.levelStats.shotCount.toFloat() * 100.0f).toInt()
            }
            var accuracyPoints: Int = Lib.Companion.Max(0, accuracy - 50) * 20
            gui.SetStateString("player_accuracy_score", Str.va("%d", accuracyPoints))
            gameStats.score += accuracyPoints
            val saveAccuracy: Int
            val totalAst = gameStats.levelStats.savedAstronauts + gameStats.levelStats.killedAstronauts
            saveAccuracy = if (0 == totalAst) {
                0
            } else {
                (gameStats.levelStats.savedAstronauts.toFloat() / totalAst.toFloat() * 100.0f).toInt()
            }
            accuracyPoints = Lib.Companion.Max(0, saveAccuracy - 50) * 20
            gui.SetStateString("save_accuracy_score", Str.va("%d", accuracyPoints))
            gameStats.score += accuracyPoints
            StopSuperBlaster()
            gameStats.nextLevel++
            if (gameStats.nextLevel >= levelCount) {
                //Have they beaten the game
                GameComplete()
            } else {

                //Make sure we don't go above the levelcount
                //min(gameStats.nextLevel, levelCount-1);
                StopGame()
                gui.HandleNamedEvent("levelComplete")
            }
        }

        private fun GameComplete() {
            StopGame()
            gui.HandleNamedEvent("gameComplete")
        }

        private fun UpdateGame() {

            //Check to see if and functions where called by the gui
            if (beginLevel.data == true) {
                beginLevel.data = false
                BeginLevel(gameStats.nextLevel)
            }
            if (resetGame.data == true) {
                resetGame.data = false
                ResetGameStats()
            }
            if (continueGame.data == true) {
                continueGame.data = false
                ContinueGame()
            }
            if (refreshGuiData.data == true) {
                refreshGuiData.data = false
                RefreshGuiData()
            }
            if (gameStats.gameRunning) {

                //We assume an upate every 16 milliseconds
                ssdTime += 16
                if (superBlasterTimeout != 0 && ssdTime > superBlasterTimeout) {
                    StopSuperBlaster()
                }

                //Find if we are targeting and enemy
                val cursor = idVec2()
                //GetCursor(cursor);
                cursor.x = gui.CursorX()
                cursor.y = gui.CursorY()
                gameStats.levelStats.targetEnt = EntityHitTest(cursor)

                //Update from back to front
                for (i in entities.Num() - 1 downTo 0) {
                    entities.get(i).Update()
                }
                CheckForHits()

                //Delete entities that need to be deleted
                for (i in entities.Num() - 1 downTo 0) {
                    if (entities.get(i).destroyed) {
                        val ent = entities.get(i)
                        ent.DestroyEntity()
                        entities.RemoveIndex(i)
                    }
                }

                //Check if we can spawn an asteroid
                SpawnAsteroid()

                //Check if we should spawn an astronaut
                SpawnAstronaut()

                //Check if we should spawn an asteroid
                SpawnPowerup()
            }
        }

        private fun CheckForHits() {

            //See if the entity has gotten close enough
            for (i in 0 until entities.Num()) {
                val ent = entities.get(i)
                if (ent.position.z <= Z_NEAR) {
                    if (!ent.noPlayerDamage) {

                        //Is the object still in the screen
                        val entPos = idVec3(ent.position)
                        entPos.z = 0f
                        val entBounds = idBounds(entPos)
                        entBounds.ExpandSelf(ent.hitRadius)
                        if (screenBounds.IntersectsBounds(entBounds)) {
                            ent.OnStrikePlayer()

                            //The entity hit the player figure out what is was and act appropriately
                            if (ent.type == SSD.SSD_ENTITY_ASTEROID) {
                                AsteroidStruckPlayer(ent as SSDAsteroid?)
                            } else if (ent.type == SSD.SSD_ENTITY_ASTRONAUT) {
                                AstronautStruckPlayer(ent as SSDAstronaut?)
                            }
                        } else {
                            //Tag for removal later in the frame
                            ent.destroyed = true
                        }
                    }
                }
            }
        }

        private fun ZOrderEntities() {
            //Z-Order the entities
            //Using a simple sorting method
            for (i in entities.Num() - 1 downTo 0) {
                var flipped = false
                for (j in 0 until i) {
                    if (entities.get(j).position.z > entities.get(j + 1).position.z) {
                        val ent = entities.get(j)
                        entities.set(j, entities.get(j + 1))
                        entities.set(j + 1, ent)
                        flipped = true
                    }
                }
                if (!flipped) {
                    //Jump out because it is sorted
                    break
                }
            }
        }

        private fun SpawnAsteroid() {
            val currentTime = ssdTime
            if (currentTime < gameStats.levelStats.nextAsteroidSpawnTime) {
                //Not time yet
                return
            }

            //Lets spawn it
            val startPosition = idVec3()
            val spawnBuffer = levelData.get(gameStats.currentLevel).spawnBuffer * 2.0f
            startPosition.x = random.RandomInt((V_WIDTH + spawnBuffer).toDouble()) - (V_WIDTH / 2.0f + spawnBuffer)
            startPosition.y = random.RandomInt((V_HEIGHT + spawnBuffer).toDouble()) - (V_HEIGHT / 2.0f + spawnBuffer)
            startPosition.z = ENTITY_START_DIST.toFloat()
            val speed =
                random.RandomInt((asteroidData.get(gameStats.currentLevel).speedMax - asteroidData.get(gameStats.currentLevel).speedMin).toDouble()) + asteroidData.get(
                    gameStats.currentLevel
                ).speedMin
            val size =
                random.RandomInt((asteroidData.get(gameStats.currentLevel).sizeMax - asteroidData.get(gameStats.currentLevel).sizeMin).toDouble()) + asteroidData.get(
                    gameStats.currentLevel
                ).sizeMin
            val rotate =
                random.RandomFloat() * (asteroidData.get(gameStats.currentLevel).rotateMax - asteroidData.get(
                    gameStats.currentLevel
                ).rotateMin) + asteroidData.get(gameStats.currentLevel).rotateMin
            val asteroid = SSDAsteroid.GetNewAsteroid(
                this,
                startPosition,
                idVec2(size, size),
                speed,
                rotate,
                asteroidData.get(gameStats.currentLevel).asteroidHealth
            )
            entities.Append(asteroid)
            gameStats.levelStats.nextAsteroidSpawnTime = currentTime + random.RandomInt(
                (asteroidData.get(gameStats.currentLevel).spawnMax - asteroidData.get(gameStats.currentLevel).spawnMin).toDouble()
            ) + asteroidData.get(gameStats.currentLevel).spawnMin
        }

        private fun FireWeapon(key: Int) {
            val cursorWorld = GetCursorWorld()
            val cursor = idVec2()
            //GetCursor(cursor);
            cursor.x = gui.CursorX()
            cursor.y = gui.CursorY()
            if (key == KeyInput.K_MOUSE1) {
                gameStats.levelStats.shotCount++
                if (gameStats.levelStats.targetEnt != null) {
                    //Aim the projectile from the bottom of the screen directly at the ent
                    //SSDProjectile* newProj = new SSDProjectile(this, idVec3(320,0,0), gameStats.levelStats.targetEnt.position, weaponData[gameStats.currentWeapon].speed, weaponData[gameStats.currentWeapon].size);
                    val newProj = SSDProjectile.GetNewProjectile(
                        this,
                        idVec3(0, -180, 0),
                        gameStats.levelStats.targetEnt.position,
                        weaponData.get(gameStats.currentWeapon).speed,
                        weaponData.get(gameStats.currentWeapon).size.toFloat()
                    )
                    entities.Append(newProj)
                    //newProj = SSDProjectile::GetNewProjectile(this, idVec3(-320,-0,0), gameStats.levelStats.targetEnt.position, weaponData[gameStats.currentWeapon].speed, weaponData[gameStats.currentWeapon].size);
                    //entities.Append(newProj);

                    //We hit something
                    gameStats.levelStats.hitCount++
                    gameStats.levelStats.targetEnt.OnHit(key)
                    if (gameStats.levelStats.targetEnt.type == SSD.SSD_ENTITY_ASTEROID) {
                        HitAsteroid(gameStats.levelStats.targetEnt as SSDAsteroid?, key)
                    } else if (gameStats.levelStats.targetEnt.type == SSD.SSD_ENTITY_ASTRONAUT) {
                        HitAstronaut(gameStats.levelStats.targetEnt as SSDAstronaut?, key)
                    } else if (gameStats.levelStats.targetEnt.type == SSD.SSD_ENTITY_ASTRONAUT) {
                    }
                } else {
                    ////Aim the projectile at the cursor position all the way to the far clipping
                    //SSDProjectile* newProj = SSDProjectile::GetNewProjectile(this, idVec3(0,-180,0), idVec3(cursorWorld.x, cursorWorld.y, (Z_FAR-Z_NEAR)/2.0f), weaponData[gameStats.currentWeapon].speed, weaponData[gameStats.currentWeapon].size);

                    //Aim the projectile so it crosses the cursor 1/4 of screen
                    val vec = idVec3(cursorWorld.x, cursorWorld.y, (Z_FAR - Z_NEAR) / 8.0f)
                    vec.timesAssign(8f)
                    val newProj = SSDProjectile.GetNewProjectile(
                        this,
                        idVec3(0, -180, 0),
                        vec,
                        weaponData.get(gameStats.currentWeapon).speed,
                        weaponData.get(gameStats.currentWeapon).size.toFloat()
                    )
                    entities.Append(newProj)
                }

                //Play the blaster sound
                PlaySound("arcade_blaster")
            } /*else if (key == K_MOUSE2) {
             if(gameStats.levelStats.targetEnt) {
             if(gameStats.levelStats.targetEnt.type == SSD_ENTITY_ASTRONAUT) {
             HitAstronaut(static_cast<SSDAstronaut*>(gameStats.levelStats.targetEnt), key);
             }
             }
             }*/
        }

        private fun EntityHitTest(pt: idVec2?): SSDEntity? {
            for (i in 0 until entities.Num()) {
                //Since we ZOrder the entities every frame we can stop at the first entity we hit.
                //ToDo: Make sure this assumption is true
                if (entities.get(i).HitTest(pt)) {
                    return entities.get(i)
                }
            }
            return null
        }

        private fun HitAsteroid(asteroid: SSDAsteroid?, key: Int) {
            asteroid.health -= weaponData.get(gameStats.currentWeapon).damage
            if (asteroid.health <= 0) {

                //The asteroid has been destroyed
                val explosion: SSDExplosion = GetNewExplosion(
                    this,
                    asteroid.position,
                    asteroid.size.oMultiply(2f),
                    300,
                    SSDExplosion.EXPLOSION_NORMAL,
                    asteroid
                )
                entities.Append(explosion)
                PlaySound("arcade_explode")
                AddScore(asteroid, asteroidData.get(gameStats.currentLevel).asteroidPoints)

                //Don't let the player hit it anymore because 
                asteroid.noHit = true
                gameStats.levelStats.destroyedAsteroids++
                //if(gameStats.levelStats.destroyedAsteroids >= levelData[gameStats.currentLevel].needToWin) {
                //	LevelComplete();
                //}
            } else {
                //This was a damage hit so create a real small quick explosion
                val explosion = SSDExplosion.GetNewExplosion(
                    this,
                    asteroid.position,
                    asteroid.size.oDivide(2.0f),
                    200,
                    SSDExplosion.EXPLOSION_NORMAL,
                    asteroid,
                    false,
                    false
                )
                entities.Append(explosion)
            }
        }

        private fun AsteroidStruckPlayer(asteroid: SSDAsteroid?) {
            asteroid.noPlayerDamage = true
            asteroid.noHit = true
            AddDamage(asteroidData.get(gameStats.currentLevel).asteroidDamage)
            val explosion: SSDExplosion = GetNewExplosion(
                this,
                asteroid.position,
                asteroid.size.oMultiply(2f),
                300,
                SSDExplosion.EXPLOSION_NORMAL,
                asteroid
            )
            entities.Append(explosion)
            PlaySound("arcade_explode")
        }

        private fun RefreshGuiData() {
            gui.SetStateString("nextLevel", Str.va("%d", gameStats.nextLevel + 1))
            gui.SetStateString("currentLevel", Str.va("%d", gameStats.currentLevel + 1))
            val accuracy: Float
            accuracy = if (0 == gameStats.levelStats.shotCount) {
                0f
            } else {
                gameStats.levelStats.hitCount.toFloat() / gameStats.levelStats.shotCount.toFloat() * 100.0f
            }
            gui.SetStateString("player_accuracy", Str.va("%d%%", accuracy.toInt()))
            val saveAccuracy: Float
            val totalAst = gameStats.levelStats.savedAstronauts + gameStats.levelStats.killedAstronauts
            saveAccuracy = if (0 == totalAst) {
                0f
            } else {
                gameStats.levelStats.savedAstronauts.toFloat() / totalAst.toFloat() * 100.0f
            }
            gui.SetStateString("save_accuracy", Str.va("%d%%", saveAccuracy.toInt()))
            if (gameStats.levelStats.targetEnt != null) {
                var dist = (gameStats.levelStats.targetEnt.position.z / 100.0f).toInt()
                dist *= 100
                gui.SetStateString("target_info", Str.va("%d meters", dist))
            } else {
                gui.SetStateString("target_info", "No Target")
            }
            gui.SetStateString("player_health", Str.va("%d", gameStats.health))
            gui.SetStateString("player_score", Str.va("%d", gameStats.score))
            gui.SetStateString("player_prebonusscore", Str.va("%d", gameStats.prebonusscore))
            gui.SetStateString(
                "level_complete",
                Str.va("%d/%d", gameStats.levelStats.savedAstronauts, levelData.get(gameStats.currentLevel).needToWin)
            )
            if (superBlasterTimeout != 0) {
                val timeRemaining = (superBlasterTimeout - ssdTime) / 1000.0f
                gui.SetStateString("super_blaster_time", Str.va("%.2f", timeRemaining))
            }
        }

        private fun GetCursorWorld(): idVec2? {
            val cursor = idVec2()
            //GetCursor(cursor);
            cursor.x = gui.CursorX()
            cursor.y = gui.CursorY()
            cursor.x = cursor.x - 0.5f * V_WIDTH
            cursor.y = -(cursor.y - 0.5f * V_HEIGHT)
            return cursor
        }

        //Astronaut Methods
        private fun SpawnAstronaut() {
            val currentTime = ssdTime
            if (currentTime < gameStats.levelStats.nextAstronautSpawnTime) {
                //Not time yet
                return
            }

            //Lets spawn it
            val startPosition = idVec3()
            startPosition.x = random.RandomInt(V_WIDTH.toDouble()) - V_WIDTH / 2.0f
            startPosition.y = random.RandomInt(V_HEIGHT.toDouble()) - V_HEIGHT / 2.0f
            startPosition.z = ENTITY_START_DIST.toFloat()
            val speed =
                random.RandomInt((astronautData.get(gameStats.currentLevel).speedMax - astronautData.get(gameStats.currentLevel).speedMin).toDouble()) + astronautData.get(
                    gameStats.currentLevel
                ).speedMin
            val rotate =
                random.RandomFloat() * (astronautData.get(gameStats.currentLevel).rotateMax - astronautData.get(
                    gameStats.currentLevel
                ).rotateMin) + astronautData.get(gameStats.currentLevel).rotateMin
            val astronaut = SSDAstronaut.GetNewAstronaut(
                this,
                startPosition,
                speed,
                rotate,
                astronautData.get(gameStats.currentLevel).health
            )
            entities.Append(astronaut)
            gameStats.levelStats.nextAstronautSpawnTime = currentTime + random.RandomInt(
                (astronautData.get(gameStats.currentLevel).spawnMax - astronautData.get(gameStats.currentLevel).spawnMin).toDouble()
            ) + astronautData.get(gameStats.currentLevel).spawnMin
        }

        private fun HitAstronaut(astronaut: SSDAstronaut?, key: Int) {
            if (key == KeyInput.K_MOUSE1) {
                astronaut.health -= weaponData.get(gameStats.currentWeapon).damage
                if (astronaut.health <= 0) {
                    gameStats.levelStats.killedAstronauts++

                    //The astronaut has been destroyed
                    val explosion: SSDExplosion = GetNewExplosion(
                        this,
                        astronaut.position,
                        astronaut.size.oMultiply(2f),
                        300,
                        SSDExplosion.EXPLOSION_NORMAL,
                        astronaut
                    )
                    entities.Append(explosion)
                    PlaySound("arcade_explode")

                    //Add the penalty for killing the astronaut
                    AddScore(astronaut, astronautData.get(gameStats.currentLevel).penalty)

                    //Don't let the player hit it anymore
                    astronaut.noHit = true
                } else {
                    //This was a damage hit so create a real small quick explosion
                    val explosion = SSDExplosion.GetNewExplosion(
                        this,
                        astronaut.position,
                        astronaut.size.oDivide(2.0f),
                        200,
                        SSDExplosion.EXPLOSION_NORMAL,
                        astronaut,
                        false,
                        false
                    )
                    entities.Append(explosion)
                }
            }
        }

        private fun AstronautStruckPlayer(astronaut: SSDAstronaut?) {
            gameStats.levelStats.savedAstronauts++
            astronaut.noPlayerDamage = true
            astronaut.noHit = true

            //We are saving an astronaut
            val explosion: SSDExplosion = GetNewExplosion(
                this,
                astronaut.position,
                astronaut.size.oMultiply(2f),
                300,
                SSDExplosion.EXPLOSION_TELEPORT,
                astronaut
            )
            entities.Append(explosion)
            PlaySound("arcade_capture")

            //Give the player points for saving the astronaut
            AddScore(astronaut, astronautData.get(gameStats.currentLevel).points)
            if (gameStats.levelStats.savedAstronauts >= levelData.get(gameStats.currentLevel).needToWin) {
                LevelComplete()
            }
        }

        //Powerup Methods
        private fun SpawnPowerup() {
            val currentTime = ssdTime
            if (currentTime < gameStats.levelStats.nextPowerupSpawnTime) {
                //Not time yet
                return
            }
            val speed =
                random.RandomInt((powerupData.get(gameStats.currentLevel).speedMax - powerupData.get(gameStats.currentLevel).speedMin).toDouble()) + powerupData.get(
                    gameStats.currentLevel
                ).speedMin
            val rotate =
                random.RandomFloat() * (powerupData.get(gameStats.currentLevel).rotateMax - powerupData.get(gameStats.currentLevel).rotateMin) + powerupData.get(
                    gameStats.currentLevel
                ).rotateMin
            val powerup = SSDPowerup.GetNewPowerup(this, speed, rotate)
            entities.Append(powerup)
            gameStats.levelStats.nextPowerupSpawnTime = currentTime + random.RandomInt(
                (powerupData.get(gameStats.currentLevel).spawnMax - powerupData.get(gameStats.currentLevel).spawnMin).toDouble()
            ) + powerupData.get(gameStats.currentLevel).spawnMin
        }

        private fun StartSuperBlaster() {
            gui.HandleNamedEvent("startSuperBlaster")
            gameStats.currentWeapon = 1
            superBlasterTimeout = ssdTime + 10000
        }

        private fun StopSuperBlaster() {
            gui.HandleNamedEvent("stopSuperBlaster")
            gameStats.currentWeapon = 0
            superBlasterTimeout = 0
        } // void FreeSoundEmitter(bool immediate);

        companion object {
            const val MAX_SOUND_CHANNEL = 8

            //
            var random: idRandom? = null
        }
    }
}
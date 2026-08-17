/*
 * Copyright (C) 1999-2011 id Software LLC, a ZeniMax Media company.
 * Translated to Kotlin by Dr. Feederino with support of Claude Code
 *
 * This file is part of the Doom 3 Kotlin project.
 * Original source: neo/d3xp/Grabber.h, neo/d3xp/Grabber.cpp
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

import neo.Game.AI.idAI
import neo.Game.GameSys.Class.idClass
import neo.Game.GameSys.Class.idTypeInfo
import neo.Game.GameSys.SaveGame.idRestoreGame
import neo.Game.GameSys.SaveGame.idSaveGame
import neo.Game.GameSys.SysCvar.Companion.g_grabberDamping
import neo.Game.GameSys.SysCvar.Companion.g_grabberEnableShake
import neo.Game.GameSys.SysCvar.Companion.g_grabberHardStop
import neo.Game.GameSys.SysCvar.Companion.g_grabberHoldSeconds
import neo.Game.Game_local.Companion.MASK_MONSTERSOLID
import neo.Game.Game_local.Companion.MASK_SHOT_RENDERMODEL
import neo.Game.Game_local.Companion.TIME_GROUP1
import neo.Game.Game_local.Companion.TIME_GROUP2
import neo.Game.Game_local.Companion.gameLocal
import neo.Game.Game_local.idEntityPtr
import neo.Game.Misc.idBeam
import neo.Game.Moveable.idExplodingBarrel
import neo.Game.Moveable.idMoveable
import neo.Game.Physics.Force_Grab.idForce_Grab
import neo.Game.Physics.Physics_AF.idPhysics_AF
import neo.Game.Player.idPlayer
import neo.Game.Projectile.idProjectile
import neo.Renderer.Material.CONTENTS_BODY
import neo.Renderer.Material.CONTENTS_MOVEABLECLIP
import neo.Renderer.Material.CONTENTS_PROJECTILE
import neo.Renderer.Material.CONTENTS_SOLID
import neo.Renderer.Model.INVALID_JOINT
import neo.Renderer.RenderSystem.SCREEN_HEIGHT
import neo.Renderer.RenderSystem.SCREEN_WIDTH
import neo.framework.DeclManager.Companion.declManager
import neo.framework.UsercmdGen.BUTTON_ATTACK
import neo.framework.UsercmdGen.IMPULSE_13
import neo.framework.UsercmdGen.UCF_IMPULSE_SEQUENCE
import neo.idlib.BV.idBounds
import neo.idlib.Dict_h.idDict
import neo.idlib.Text.Str.idStr
import neo.idlib.containers.CBool
import neo.idlib.containers.CFloat
import neo.idlib.containers.CInt
import neo.idlib.math.*
import neo.idlib.math.Matrix.idMat3

/*
===============================================================================

    Grabber Object - Class to extend idWeapon to include functionality for
                        manipulating physics objects.

===============================================================================
*/

class Grabber {

    companion object {
        const val MAX_DRAG_TRACE_DISTANCE = 384.0f
        const val TRACE_BOUNDS_SIZE = 3.0f
        const val HOLD_DISTANCE = 72.0f
        const val FIRING_DELAY = 1000.0f
        const val DRAG_FAIL_LEN = 64.0f
        const val THROW_SCALE = 1000
        const val MAX_PICKUP_VELOCITY = 1500 * 1500
        const val MAX_PICKUP_SIZE = 96
    }

    class idGrabber : idEntity() {
        companion object {
            val Type: idTypeInfo = idTypeInfo(
                "idGrabber", "idEntity"
            ) { idGrabber() }
        }

        override fun GetType(): idTypeInfo = Type
        override fun CreateInstance(): idClass = idGrabber()

        private var dragEnt: idEntityPtr<idEntity> = idEntityPtr()
        private var drag: idForce_Grab = idForce_Grab()
        private var saveGravity: idVec3 = idVec3()

        private var id: Int = 0                    // id of body being dragged
        private var localPlayerPoint: idVec3 = idVec3()  // dragged point in player space
        private var owner: idEntityPtr<idPlayer> = idEntityPtr()
        private var oldUcmdFlags: Int = 0
        private var holdingAF: Boolean = false
        private var shakeForceFlip: Boolean = false
        private var endTime: Int = 0
        private var lastFiredTime: Int = (-FIRING_DELAY).toInt()
        private var dragFailTime: Int = 0
        private var startDragTime: Int = 0
        private var dragTraceDist: Float = MAX_DRAG_TRACE_DISTANCE
        private var savedContents: Int = 0
        private var savedClipmask: Int = 0

        private var beam: idBeam? = null
        private var beamTarget: idBeam? = null

        private var warpId: Int = -1

        /*
        ==============
        idGrabber::Save
        ==============
        */
        override fun Save(savefile: idSaveGame) {
            super.Save(savefile)
            dragEnt.Save(savefile) // savefile.WriteStaticObject(drag) — TODO: implement static object serialization
            drag.Save(savefile)

            savefile.WriteVec3(saveGravity)
            savefile.WriteInt(id)

            savefile.WriteVec3(localPlayerPoint)

            owner.Save(savefile)

            savefile.WriteBool(holdingAF)
            savefile.WriteBool(shakeForceFlip)

            savefile.WriteInt(endTime)
            savefile.WriteInt(lastFiredTime)
            savefile.WriteInt(dragFailTime)
            savefile.WriteInt(startDragTime)
            savefile.WriteFloat(dragTraceDist)
            savefile.WriteInt(savedContents)
            savefile.WriteInt(savedClipmask)

            savefile.WriteObject(beam)
            savefile.WriteObject(beamTarget)

            savefile.WriteInt(warpId)
        }

        /*
        ==============
        idGrabber::Restore
        ==============
        */
        override fun Restore(savefile: idRestoreGame) {
            super.Restore(savefile) // Spawn the beams
            Initialize()

            dragEnt.Restore(savefile) // savefile.ReadStaticObject(drag) — TODO: implement static object serialization
            drag.Restore(savefile)

            savefile.ReadVec3(saveGravity)
            val _id = CInt()
            savefile.ReadInt(_id)
            id = _id._val

            // Restore the drag force's physics object
            if (dragEnt.IsValid()) {
                drag.SetPhysics(
                    dragEnt.GetEntity()!!.GetPhysics(), id, dragEnt.GetEntity()!!.GetPhysics().GetOrigin()
                )
            }

            savefile.ReadVec3(localPlayerPoint)

            owner.Restore(savefile)

            val _holdingAF = CBool(false)
            val _shakeForceFlip = CBool(false)
            savefile.ReadBool(_holdingAF)
            holdingAF = _holdingAF._val
            savefile.ReadBool(_shakeForceFlip)
            shakeForceFlip = _shakeForceFlip._val

            val _endTime = CInt()
            val _lastFiredTime = CInt()
            val _dragFailTime = CInt()
            val _startDragTime = CInt()
            val _dragTraceDist = CFloat()
            val _savedContents = CInt()
            val _savedClipmask = CInt()

            savefile.ReadInt(_endTime); endTime = _endTime._val
            savefile.ReadInt(_lastFiredTime); lastFiredTime = _lastFiredTime._val
            savefile.ReadInt(_dragFailTime); dragFailTime = _dragFailTime._val
            savefile.ReadInt(_startDragTime); startDragTime = _startDragTime._val
            savefile.ReadFloat(_dragTraceDist); dragTraceDist = _dragTraceDist._val
            savefile.ReadInt(_savedContents); savedContents = _savedContents._val
            savefile.ReadInt(_savedClipmask); savedClipmask = _savedClipmask._val

            beam = savefile.ReadObject() as idBeam?
            beamTarget = savefile.ReadObject() as idBeam?

            val _warpId = CInt()
            savefile.ReadInt(_warpId); warpId = _warpId._val
        }

        /*
        ==============
        idGrabber::Initialize
        ==============
        */
        fun Initialize() {
            if (!gameLocal.isMultiplayer) {
                val args = idDict()

                if (beamTarget == null) {
                    args.SetVector("origin", vec3_origin)
                    args.SetBool("start_off", true)
                    beamTarget = gameLocal.SpawnEntityType(idBeam.Type, args) as? idBeam
                }

                if (beam == null) {
                    args.Clear()
                    args.Set("target", beamTarget!!.name.toString())
                    args.SetVector("origin", vec3_origin)
                    args.SetBool("start_off", true)
                    args.Set("width", "6")
                    args.Set("skin", "textures/smf/flareSizeable")
                    args.Set("_color", "0.0235 0.843 0.969 0.2")
                    beam = gameLocal.SpawnEntityType(idBeam.Type, args) as? idBeam
                    beam?.SetShaderParm(6, 1.0f)
                }

                endTime = 0
                dragTraceDist = MAX_DRAG_TRACE_DISTANCE
            } else {
                beam = null
                beamTarget = null
                endTime = 0
                dragTraceDist = MAX_DRAG_TRACE_DISTANCE
            }
        }

        /*
        ==============
        idGrabber::SetDragDistance
        ==============
        */
        fun SetDragDistance(dist: Float) {
            dragTraceDist = dist
        }

        /*
        ==============
        idGrabber::StartDrag
        ==============
        */
        private fun StartDrag(grabEnt: idEntity, id: Int) {
            var clipModelId = id
            val thePlayer = owner.GetEntity() ?: return

            holdingAF = false
            dragFailTime = gameLocal.slow.time
            startDragTime = gameLocal.slow.time

            oldUcmdFlags = thePlayer.usercmd.flags.toInt()

            // set grabbed state for networking
            grabEnt.SetGrabbedState(true)

            // This is the new object to drag around
            dragEnt.oSet(grabEnt)

            // Show the beams!
            UpdateBeams()
            beam?.Show()
            beamTarget?.Show()

            // Move the object to the fast group (helltime)
            grabEnt.timeGroup = TIME_GROUP2

            // Handle specific class types
            if (grabEnt.IsType(idProjectile.Type)) {
                val p = grabEnt as idProjectile

                p.CatchProjectile(thePlayer, "_catch")

                // Make the projectile non-solid to other projectiles/enemies
                if (idStr.Cmp(grabEnt.GetEntityDefName(), "projectile_helltime_killer") == 0) {
                    savedContents = CONTENTS_PROJECTILE
                    savedClipmask = MASK_SHOT_RENDERMODEL or CONTENTS_PROJECTILE
                } else {
                    savedContents = grabEnt.GetPhysics().GetContents()
                    savedClipmask = grabEnt.GetPhysics().GetClipMask()
                }
                grabEnt.GetPhysics().SetContents(0)
                grabEnt.GetPhysics().SetClipMask(CONTENTS_SOLID or CONTENTS_BODY)

            } else if (grabEnt.IsType(idExplodingBarrel.Type)) {
                val ebarrel = grabEnt as idExplodingBarrel
                ebarrel.StartBurning()

            } else if (grabEnt.IsType(idAFEntity_Gibbable.Type)) {
                holdingAF = true
                clipModelId = 0

                if (grabbableAI(grabEnt.spawnArgs.GetString("classname"))) {
                    val aiEnt = grabEnt as idAI
                    aiEnt.StartRagdoll()
                }
            } else if (grabEnt.IsType(idMoveableItem.Type)) {
                grabEnt.PostEventMS(EV_Touch, 250, thePlayer, 0)
            }

            // Get the current physics object to manipulate
            val phys = grabEnt.GetPhysics()

            // Turn off gravity on object
            saveGravity.set(phys.GetGravity())
            phys.SetGravity(vec3_origin)

            // hold it directly in front of player
            localPlayerPoint.set(
                thePlayer.firstPersonViewAxis[0].times(HOLD_DISTANCE).times(thePlayer.firstPersonViewAxis.Transpose())
            )

            // Set the ending time for the hold
            endTime = gameLocal.time + (g_grabberHoldSeconds.GetFloat() * 1000).toInt()

            // Start up the Force_Drag to bring it in
            drag.Init(g_grabberDamping.GetFloat())
            drag.SetPhysics(
                phys, clipModelId,
                thePlayer.firstPersonViewOrigin.plus(localPlayerPoint.times(thePlayer.firstPersonViewAxis))
            )

            // start the screen warp
            warpId = thePlayer.playerView.AddWarp(
                phys.GetOrigin(), SCREEN_WIDTH / 2f, SCREEN_HEIGHT / 2f, 160f, 2000f
            )
        }

        /*
        ==============
        idGrabber::StopDrag
        ==============
        */
        private fun StopDrag(dropOnly: Boolean) {
            val thePlayer = owner.GetEntity()

            beam?.Hide()
            beamTarget?.Hide()

            if (dragEnt.IsValid()) {
                val ent = dragEnt.GetEntity()!!

                // set grabbed state for networking
                ent.SetGrabbedState(false)

                // If a cinematic has started, allow dropped object to think in cinematics
                if (gameLocal.inCinematic) {
                    ent.cinematic = true
                }

                // Restore Gravity
                ent.GetPhysics().SetGravity(saveGravity)

                // Move the object back to the slow group (helltime)
                ent.timeGroup = TIME_GROUP1

                if (holdingAF) {
                    val af = ent as idAFEntity_Gibbable
                    val afPhys = af.GetPhysics() as idPhysics_AF

                    if (grabbableAI(ent.spawnArgs.GetString("classname"))) {
                        val aiEnt = ent as idAI
                        aiEnt.Damage(thePlayer, thePlayer, vec3_origin, "damage_suicide", 1.0f, INVALID_JOINT)
                    }

                    af.SetThrown(!dropOnly)

                    // Reset timers so that it isn't forcibly put to rest in mid-air
                    afPhys.PutToRest()
                    afPhys.Activate()

                    afPhys.SetTimeScaleRamp(
                        MS2SEC(gameLocal.slow.time.toFloat()) - 1.5f, MS2SEC(gameLocal.slow.time.toFloat()) + 1.0f
                    )
                }

                // If the object isn't near its goal, just drop it in place
                if (!ent.IsType(idProjectile.Type) && (dropOnly || drag.GetDistanceToGoal() > DRAG_FAIL_LEN)) {
                    ent.GetPhysics().SetLinearVelocity(vec3_origin)
                    thePlayer?.StartSoundShader(
                        declManager.FindSound("grabber_maindrop"),
                        Game_local.gameSoundChannel_t.SND_CHANNEL_WEAPON.ordinal,
                        0,
                        false,
                        CInt()
                    )

                    if (ent.IsType(idExplodingBarrel.Type)) {
                        val ebarrel = ent as idExplodingBarrel
                        ebarrel.SetStability(true)
                        ebarrel.StopBurning()
                    }
                } else { // Shoot the object forward
                    ent.ApplyImpulse(
                        thePlayer, 0, ent.GetPhysics().GetOrigin(),
                        thePlayer!!.firstPersonViewAxis[0].times((THROW_SCALE * ent.GetPhysics().GetMass()))
                    )
                    thePlayer.StartSoundShader(
                        declManager.FindSound("grabber_release"),
                        Game_local.gameSoundChannel_t.SND_CHANNEL_WEAPON.ordinal,
                        0,
                        false,
                        CInt()
                    )

                    // Orient projectiles away from the player
                    if (ent.IsType(idProjectile.Type)) {
                        val player = owner.GetEntity()!!
                        val ang = player.firstPersonViewAxis[0].ToAngles()
                        ang.pitch += 90f
                        ent.GetPhysics().SetAxis(ang.ToMat3())
                        ent.GetPhysics().SetAngularVelocity(vec3_origin)

                        // Restore projectile contents
                        ent.GetPhysics().SetContents(savedContents)
                        ent.GetPhysics().SetClipMask(savedClipmask)

                    } else if (ent.IsType(idMoveable.Type)) { // Turn on damage for this object
                        val obj = ent as idMoveable
                        obj.EnableDamage(true, 2.5f)
                        obj.SetAttacker(thePlayer)

                        if (ent.IsType(idExplodingBarrel.Type)) {
                            val ebarrel = ent as idExplodingBarrel
                            ebarrel.SetStability(false)
                        }

                    } else if (ent.IsType(idMoveableItem.Type)) {
                        ent.GetPhysics().SetClipMask(MASK_MONSTERSOLID)
                    }
                }

                // Remove the Force_Drag's control of the entity
                drag.RemovePhysics(ent.GetPhysics())
            }

            if (warpId != -1) {
                thePlayer?.playerView?.FreeWarp(warpId)
                warpId = -1
            }

            lastFiredTime = gameLocal.time
            dragEnt.oSet(null)
            endTime = 0
        }

        /*
        ==============
        idGrabber::Update
        ==============
        */
        fun Update(player: idPlayer, hide: Boolean): Int {
            val trace = neo.cm.trace_s()
            var newEnt: idEntity?

            // pause before allowing refire
            if (lastFiredTime + FIRING_DELAY > gameLocal.time) {
                return 3
            }

            // Dead players release the trigger
            if (hide || player.health <= 0) {
                StopDrag(true)
                if (hide) {
                    lastFiredTime = gameLocal.time - FIRING_DELAY.toInt() + 250
                }
                return 3
            }

            // Check if object being held has been removed (dead demon, projectile, etc.)
            if (endTime > gameLocal.time) {
                var abort = !dragEnt.IsValid()

                if (!abort && dragEnt.GetEntity()!!.IsType(idProjectile.Type)) {
                    val proj = dragEnt.GetEntity() as idProjectile
                    if (proj.GetProjectileState() >= 3) {
                        abort = true
                    }
                }
                if (!abort && dragEnt.GetEntity()!!.IsHidden()) {
                    abort = true
                } // Not in multiplayer :: Pressing "reload" lets you carefully drop an item
                if (!gameLocal.isMultiplayer && !abort && ((player.usercmd.flags.toInt() and UCF_IMPULSE_SEQUENCE) != (oldUcmdFlags and UCF_IMPULSE_SEQUENCE)) && (player.usercmd.impulse.toInt() == IMPULSE_13)) {
                    abort = true
                }

                if (abort) {
                    StopDrag(true)
                    return 3
                }
            }

            owner.oSet(player)

            // if no entity selected for dragging
            if (dragEnt.GetEntity() == null) {
                val bounds = idBounds()
                val end = player.firstPersonViewOrigin.plus(
                    player.firstPersonViewAxis[0].times(dragTraceDist)
                )

                bounds.Zero()
                bounds.ExpandSelf(TRACE_BOUNDS_SIZE)

                gameLocal.clip.TraceBounds(
                    trace, player.firstPersonViewOrigin, end, bounds,
                    MASK_SHOT_RENDERMODEL or CONTENTS_PROJECTILE or CONTENTS_MOVEABLECLIP,
                    player
                )

                // If the trace hit something
                if (trace.fraction < 1.0f) {
                    newEnt = gameLocal.entities[trace.c.entityNum]

                    // if entity is already being grabbed then bypass
                    if (newEnt != null && gameLocal.isMultiplayer && newEnt.IsGrabbed()) {
                        return 0
                    }

                    // Check if this is a valid entity to hold
                    if (newEnt != null && (newEnt.IsType(idMoveable.Type) || newEnt.IsType(idMoveableItem.Type) || newEnt.IsType(
                            idProjectile.Type
                        ) || newEnt.IsType(idAFEntity_Gibbable.Type)) && !newEnt.noGrab && newEnt.GetPhysics()
                            .GetBounds().GetRadius() < MAX_PICKUP_SIZE && newEnt.GetPhysics().GetLinearVelocity()
                            .LengthSqr() < MAX_PICKUP_VELOCITY
                    ) {

                        var validAF = true

                        if (newEnt.IsType(idAFEntity_Gibbable.Type)) {
                            val afEnt = newEnt as idAFEntity_Gibbable

                            if (grabbableAI(newEnt.spawnArgs.GetString("classname"))) { // Make sure it's also active
                                if (!afEnt.IsActive()) {
                                    validAF = false
                                }
                            } else if (!afEnt.IsActiveAF()) {
                                validAF = false
                            }
                        }

                        if (validAF && (player.usercmd.buttons.toInt() and BUTTON_ATTACK) != 0) { // Grab this entity and start dragging it around
                            StartDrag(newEnt, trace.c.id)
                        } else if (validAF) { // A holdable object is ready to be grabbed
                            return 1
                        }
                    }
                }
            }

            // check backwards server time in multiplayer
            var allow = true

            if (gameLocal.isMultiplayer) { // if we've marched backwards
                if (gameLocal.slow.time < startDragTime) {
                    allow = false
                }
            }

            // if there is an entity selected for dragging
            if (dragEnt.GetEntity() != null && allow) {
                val entPhys = dragEnt.GetEntity()!!.GetPhysics()
                val goalPos: idVec3

                // If the player lets go of attack, or time is up
                if ((player.usercmd.buttons.toInt() and BUTTON_ATTACK) == 0) {
                    StopDrag(false)
                    return 3
                }
                if (gameLocal.time > endTime) {
                    StopDrag(true)
                    return 3
                }

                // Check if the player is standing on the object
                if (!holdingAF) {
                    val playerBounds = idBounds()
                    val objectBounds = entPhys.GetAbsBounds()
                    val newPoint = idVec3(player.GetPhysics().GetOrigin())

                    // create a bounds at the players feet
                    playerBounds.Clear()
                    playerBounds.AddPoint(newPoint)
                    newPoint.z -= 1f
                    playerBounds.AddPoint(newPoint)
                    playerBounds.ExpandSelf(8f)

                    // If it intersects the object bounds, then drop it
                    if (playerBounds.IntersectsBounds(objectBounds)) {
                        StopDrag(true)
                        return 3
                    }
                }

                // Shake the object at the end of the hold
                if (g_grabberEnableShake.GetBool() && !gameLocal.isMultiplayer) {
                    ApplyShake()
                }

                // Set and evaluate drag force
                goalPos = player.firstPersonViewOrigin.plus(
                    localPlayerPoint.times(player.firstPersonViewAxis)
                )

                drag.SetGoalPosition(goalPos)
                drag.Evaluate(gameLocal.time)

                // If an object is flying too fast toward the player, stop it hard
                if (g_grabberHardStop.GetBool()) {
                    val theWall = idPlane()
                    val toPlayerVelocity: idVec3
                    val objectCenter: idVec3
                    val toPlayerSpeed: Float

                    toPlayerVelocity = player.firstPersonViewAxis[0].unaryMinus()
                    toPlayerSpeed = entPhys.GetLinearVelocity().times(toPlayerVelocity)

                    if (toPlayerSpeed > 64f) {
                        objectCenter = entPhys.GetAbsBounds().GetCenter()

                        theWall.SetNormal(player.firstPersonViewAxis[0])
                        theWall.FitThroughPoint(goalPos)

                        if (theWall.Side(objectCenter, 0.1f) == PLANESIDE_BACK) {
                            val num = entPhys.GetNumClipModels()
                            for (i in 0 until num) {
                                entPhys.SetLinearVelocity(vec3_origin, i)
                            }
                        }
                    }

                    // Make sure the object isn't spinning too fast
                    val MAX_ROTATION_SPEED = 12f

                    val angVel = entPhys.GetAngularVelocity()
                    val rotationSpeed = angVel.LengthFast()

                    if (rotationSpeed > MAX_ROTATION_SPEED) {
                        angVel.NormalizeFast()
                        angVel.timesAssign(MAX_ROTATION_SPEED)
                        entPhys.SetAngularVelocity(angVel)
                    }
                }

                // Orient projectiles away from the player
                if (dragEnt.GetEntity()!!.IsType(idProjectile.Type)) {
                    val ang = player.firstPersonViewAxis[0].ToAngles()
                    ang.pitch += 90f
                    entPhys.SetAxis(ang.ToMat3())
                }

                // Some kind of effect from gun to object?
                UpdateBeams()

                // If the object is stuck away from its intended position for more than 500ms, let it go
                if (drag.GetDistanceToGoal() > DRAG_FAIL_LEN) {
                    if (dragFailTime < (gameLocal.slow.time - 500)) {
                        StopDrag(true)
                        return 3
                    }
                } else {
                    dragFailTime = gameLocal.slow.time
                }

                // Currently holding an object
                return 2
            }

            // Not holding, nothing to hold
            return 0
        }

        /*
        ======================
        idGrabber::UpdateBeams
        ======================
        */
        private fun UpdateBeams() {
            if (beam == null) {
                return
            }

            if (dragEnt.IsValid()) {
                val thePlayer = owner.GetEntity() ?: return

                beamTarget?.SetOrigin(dragEnt.GetEntity()!!.GetPhysics().GetAbsBounds().GetCenter())

                val muzzleJoint =
                    thePlayer.weapon.GetEntity()?.GetAnimator()?.GetJointHandle("particle_upper") ?: INVALID_JOINT
                val muzzleOrigin = idVec3()
                val muzzleAxis = idMat3()

                if (muzzleJoint != INVALID_JOINT) {
                    thePlayer.weapon.GetEntity()!!.GetJointWorldTransform(
                        muzzleJoint, gameLocal.time, muzzleOrigin, muzzleAxis
                    )
                } else {
                    muzzleOrigin.set(thePlayer.GetPhysics().GetOrigin())
                }

                beam!!.SetOrigin(muzzleOrigin)
                val re = beam!!.GetRenderEntity()
                re!!.origin.set(muzzleOrigin)

                beam!!.UpdateVisuals()
                beam!!.Present()
            }
        }

        /*
        ==============
        idGrabber::ApplyShake
        ==============
        */
        private fun ApplyShake() {
            val u = 1f - (endTime - gameLocal.time).toFloat() / (g_grabberHoldSeconds.GetFloat() * 1000f)

            if (u >= 0.8f) {
                val point: idVec3
                val impulse = idVec3()
                var shakeForceMagnitude = 450f
                val mass = dragEnt.GetEntity()!!.GetPhysics().GetMass()

                shakeForceFlip = !shakeForceFlip

                // get point to rotate around
                point = idVec3(dragEnt.GetEntity()!!.GetPhysics().GetOrigin())
                point.y += 1f

                // Articulated figures get less violent shake
                if (holdingAF) {
                    shakeForceMagnitude = 120f
                }

                // calc impulse
                if (shakeForceFlip) {
                    impulse.set(0f, 0f, shakeForceMagnitude * u * mass)
                } else {
                    impulse.set(0f, 0f, -shakeForceMagnitude * u * mass)
                }

                dragEnt.GetEntity()!!.ApplyImpulse(null, 0, point, impulse)
            }
        }

        /*
        ==============
        idGrabber::grabbableAI
        ==============
        */
        private fun grabbableAI(aiName: String): Boolean { // skip "monster_"
            if (aiName.length <= 8) return false
            val name = aiName.substring(8)

            return (name.startsWith("flying_lostsoul") || name.startsWith("demon_trite") || name == "flying_forgotten" || name == "demon_cherub" || name == "demon_tick")
        }

        override fun _deconstructor() {
            StopDrag(true)
            beam?._deconstructor()
            beam = null
            beamTarget?._deconstructor()
            beamTarget = null
            super._deconstructor()
        }
    }
}

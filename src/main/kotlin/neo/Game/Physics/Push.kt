/*
 * Copyright (C) 1999-2011 id Software LLC, a ZeniMax Media company.
 * Translated to Kotlin by Dr. Feederino with support of Claude Code
 *
 * This file is part of the Doom 3 Kotlin project.
 * Original source: neo/Game/Physics/Push.h, neo/Game/Physics/Push.cpp
 */

package neo.Game.Physics

import neo.Game.AFEntity.idAFEntity_Base
import neo.Game.Actor.idActor
import neo.Game.EV_Explode
import neo.Game.EV_Gib
import neo.Game.Entity.idEntity
import neo.Game.Game_local
import neo.Game.Game_local.idGameLocal
import neo.Game.Item.idMoveableItem
import neo.Game.Moveable.idMoveable
import neo.Game.Physics.Clip.idClipModel
import neo.Game.Physics.Physics.idPhysics
import neo.Game.Physics.Physics_Actor.idPhysics_Actor
import neo.Game.Player.idPlayer
import neo.Game.Projectile.idProjectile
import neo.cm.contactInfo_t
import neo.cm.trace_s
import neo.idlib.BV.idBounds
import neo.idlib.math.Matrix.idMat3
import neo.idlib.math.getVec3Origin
import neo.idlib.math.idAngles
import neo.idlib.math.idRotation
import neo.idlib.math.idVec3

object Push {
    const val PUSHFL_APPLYIMPULSE = 16 // apply impulse to pushed entities
    const val PUSHFL_CLIP = 4 // also clip against all non-moveable entities
    const val PUSHFL_CRUSH = 8 // kill blocking entities
    const val PUSHFL_NOGROUNDENTITIES = 2 // don't push entities the clip model rests upon

    /*
     ===============================================================================

     Allows physics objects to be pushed geometrically.

     ===============================================================================
     */
    const val PUSHFL_ONLYMOVEABLE = 1 // only push moveable entities
    const val PUSH_BLOCKED = 2 // blocked

    //
    //
    //enum {
    const val PUSH_NO = 0 // not pushed
    const val PUSH_OK = 1 // pushed ok

    //};
    //#define NEW_PUSH
    class idPush {
        private val pushed: Array<pushed_s> = Array(Game_local.MAX_GENTITIES) { pushed_s() } // pushed entities
        var pushedGroupSize = 0
        private var numPushed // number of pushed entities
                = 0
        private val pushedGroup: Array<pushedGroup_s?> = arrayOfNulls<pushedGroup_s?>(Game_local.MAX_GENTITIES)

        /*
         ============
         idPush::ClipTranslationalPush

         Try to push other entities by moving the given entity.
         ============
         */
        // If results.fraction < 1.0f the move was blocked by results.c.entityNum
        // Returns total mass of all pushed entities.
        fun ClipTranslationalPush(
            results: trace_s,
            pusher: idEntity,
            flags: Int,
            newOrigin: idVec3,
            translation: idVec3
        ): Float {
            var results = results
            var i: Int
            var listedEntities: Int
            var res: Int
            var check: idEntity
            val entityList = arrayOfNulls<idEntity?>(Game_local.MAX_GENTITIES)
            val bounds: idBounds
            val pushBounds = idBounds()
            val clipMove = idVec3()
            val clipOrigin = idVec3()
            val oldOrigin = idVec3()
            val dir = idVec3()
            val impulse = idVec3()
            val pushResults = trace_s()
            val wasEnabled: Boolean
            var totalMass: Float
            val clipModel: idClipModel = pusher.GetPhysics().GetClipModel()!!
            totalMass = 0.0f
            results.fraction = 1.0f
            results.endpos.set(newOrigin)
            results.endAxis.set(clipModel.GetAxis())
            results.c = contactInfo_t() //memset( &results.c, 0, sizeof( results.c ) );//TODO:
            if (translation == getVec3Origin()) {
                return totalMass
            }
            dir.set(translation)
            dir.Normalize()
            dir.z += 1.0f
            dir.timesAssign(10.0f)

            // get bounds for the whole movement
            bounds = idBounds(clipModel.GetBounds())
            if (bounds[0].x >= bounds[1].x) {
                return totalMass
            }
            pushBounds.FromBoundsTranslation(bounds, clipModel.GetOrigin(), clipModel.GetAxis(), translation)
            wasEnabled = clipModel.IsEnabled()

            // make sure we don't get the pushing clip model in the list
            clipModel.Disable()
            listedEntities =
                Game_local.gameLocal.clip.EntitiesTouchingBounds(pushBounds, -1, entityList, Game_local.MAX_GENTITIES)

            // discard entities we cannot or should not push
            listedEntities = DiscardEntities(entityList, listedEntities, flags, pusher)
            if ((flags and PUSHFL_CLIP) != 0) {

                // can only clip movement of a trace model
                assert(clipModel.IsTraceModel())

                // disable to be pushed entities for collision detection
                i = 0
                while (i < listedEntities) {
                    entityList[i]!!.GetPhysics().DisableClip()
                    i++
                }
                Game_local.gameLocal.clip.Translation(
                    results,
                    clipModel.GetOrigin(),
                    clipModel.GetOrigin() + translation,
                    clipModel,
                    clipModel.GetAxis(),
                    pusher.GetPhysics().GetClipMask(),
                    null
                )

                // enable to be pushed entities for collision detection
                i = 0
                while (i < listedEntities) {
                    entityList[i]!!.GetPhysics().EnableClip()
                    i++
                }
                if (results.fraction == 0.0f) {
                    if (wasEnabled) {
                        clipModel.Enable()
                    }
                    return totalMass
                }
                clipMove.set(results.endpos.minus(clipModel.GetOrigin()))
                clipOrigin.set(results.endpos)
            } else {
                clipMove.set(translation)
                clipOrigin.set(newOrigin)
            }

            // we have to enable the clip model because we use it during pushing
            clipModel.Enable()

            // save pusher old position
            oldOrigin.set(clipModel.GetOrigin())

            // try to push the entities
            i = 0
            while (i < listedEntities) {
                check = entityList[i]!!
                val physics = check.GetPhysics()

                // disable the entity for collision detection
                physics.DisableClip()
                res = TryTranslatePushEntity(pushResults, check, clipModel, flags, clipOrigin, clipMove)

                // enable the entity for collision detection
                physics.EnableClip()

                // if the entity is pushed
                if (res == PUSH_OK) {
                    // set the pusher in the translated position
                    clipModel.Link(
                        Game_local.gameLocal.clip,
                        clipModel.GetEntity(),
                        clipModel.GetId(),
                        newOrigin,
                        clipModel.GetAxis()
                    )
                    // the entity might be pushed off the ground
                    physics.EvaluateContacts()
                    // put pusher back in old position
                    clipModel.Link(
                        Game_local.gameLocal.clip,
                        clipModel.GetEntity(),
                        clipModel.GetId(),
                        oldOrigin,
                        clipModel.GetAxis()
                    )

                    // wake up this object
                    if ((flags and PUSHFL_APPLYIMPULSE) != 0) {
                        impulse.set(dir.times(physics.GetMass()))
                    } else {
                        impulse.Zero()
                    }
                    check.ApplyImpulse(clipModel.GetEntity(), clipModel.GetId(), clipModel.GetOrigin(), impulse)

                    // add mass of pushed entity
                    totalMass += physics.GetMass()
                }

                // if the entity is not blocking
                if (res != PUSH_BLOCKED) {
                    i++
                    continue
                }

                // if the blocking entity is a projectile
                if (check is idProjectile) {
                    check.ProcessEvent(EV_Explode)
                    i++
                    continue
                }

                // if blocking entities should be crushed
                if ((flags and PUSHFL_CRUSH) != 0) {
                    check.Damage(
                        clipModel.GetEntity(),
                        clipModel.GetEntity(),
                        getVec3Origin(),
                        "damage_crush",
                        1.0f,
                        Clip.CLIPMODEL_ID_TO_JOINT_HANDLE(pushResults.c.id)
                    )
                    i++
                    continue
                }

                // if the entity is an active articulated figure and gibs
                if (check is idAFEntity_Base && check.spawnArgs.GetBool("gib")) {
                    if (check.IsActiveAF()) {
                        check.ProcessEvent(EV_Gib, "damage_Gib")
                    }
                }

                // if the entity is a moveable item and gibs
                if (check is idMoveableItem && check.spawnArgs.GetBool("gib")) {
                    check.ProcessEvent(EV_Gib, "damage_Gib")
                }

                // blocked
                results.set(pushResults)
                results.fraction = 0.0f
                results.endAxis.set(clipModel.GetAxis())
                results.endpos.set(clipModel.GetOrigin())
                results.c.entityNum = check.entityNumber
                results.c.id = 0
                if (!wasEnabled) {
                    clipModel.Disable()
                }
                return totalMass
                i++
            }
            if (!wasEnabled) {
                clipModel.Disable()
            }
            return totalMass
        }

        /*
         ============
         idPush::ClipRotationalPush

         Try to push other entities by moving the given entity.
         ============
         */
        fun ClipRotationalPush(
            results: trace_s,
            pusher: idEntity,
            flags: Int,
            newAxis: idMat3,
            rotation: idRotation
        ): Float {
            var i: Int
            var listedEntities: Int
            var res: Int
            var check: idEntity
            val entityList = arrayOfNulls<idEntity?>(Game_local.MAX_GENTITIES)
            val bounds: idBounds
            val pushBounds = idBounds()
            val clipRotation: idRotation
            val clipAxis: idMat3
            val oldAxis: idMat3
            val pushResults = trace_s()
            val wasEnabled: Boolean
            var totalMass: Float
            val clipModel: idClipModel?
            clipModel = pusher.GetPhysics().GetClipModel()!!
            totalMass = 0.0f
            results.fraction = 1.0f
            results.endpos.set(clipModel.GetOrigin())
            results.endAxis.set(newAxis)
            results.c = contactInfo_t() //memset( &results.c, 0, sizeof( results.c ) );//TODOS:
            if (0.0f == rotation.GetAngle()) {
                return totalMass
            }

            // get bounds for the whole movement
            bounds = clipModel.GetBounds()
            if (bounds[0].x >= bounds[1].x) {
                return totalMass
            }
            pushBounds.FromBoundsRotation(bounds, clipModel.GetOrigin(), clipModel.GetAxis(), rotation)
            wasEnabled = clipModel.IsEnabled()

            // make sure we don't get the pushing clip model in the list
            clipModel.Disable()
            listedEntities =
                Game_local.gameLocal.clip.EntitiesTouchingBounds(pushBounds, -1, entityList, Game_local.MAX_GENTITIES)

            // discard entities we cannot or should not push
            listedEntities = DiscardEntities(entityList, listedEntities, flags, pusher)
            if ((flags and PUSHFL_CLIP) != 0) {

                // can only clip movement of a trace model
                assert(clipModel.IsTraceModel())

                // disable to be pushed entities for collision detection
                i = 0
                while (i < listedEntities) {
                    entityList[i]!!.GetPhysics().DisableClip()
                    i++
                }
                Game_local.gameLocal.clip.Rotation(
                    results,
                    clipModel.GetOrigin(),
                    rotation,
                    clipModel,
                    clipModel.GetAxis(),
                    pusher.GetPhysics().GetClipMask(),
                    null
                )

                // enable to be pushed entities for collision detection
                i = 0
                while (i < listedEntities) {
                    entityList[i]!!.GetPhysics().EnableClip()
                    i++
                }
                if (results.fraction == 0.0f) {
                    if (wasEnabled) {
                        clipModel.Enable()
                    }
                    return totalMass
                }
                clipRotation = rotation.times(results.fraction)
                clipAxis = results.endAxis
            } else {
                clipRotation = rotation
                clipAxis = newAxis
            }

            // we have to enable the clip model because we use it during pushing
            clipModel.Enable()

            // save pusher old position
            oldAxis = clipModel.GetAxis()

            // try to push all the entities
            i = 0
            while (i < listedEntities) {
                check = entityList[i]!!
                val physics = check.GetPhysics()

                // disable the entity for collision detection
                physics.DisableClip()
                res = TryRotatePushEntity(pushResults, check, clipModel, flags, clipAxis, clipRotation)

                // enable the entity for collision detection
                physics.EnableClip()

                // if the entity is pushed
                if (res == PUSH_OK) {
                    // set the pusher in the rotated position
                    clipModel.Link(
                        Game_local.gameLocal.clip,
                        clipModel.GetEntity(),
                        clipModel.GetId(),
                        clipModel.GetOrigin(),
                        newAxis
                    )
                    // the entity might be pushed off the ground
                    physics.EvaluateContacts()
                    // put pusher back in old position
                    clipModel.Link(
                        Game_local.gameLocal.clip,
                        clipModel.GetEntity(),
                        clipModel.GetId(),
                        clipModel.GetOrigin(),
                        oldAxis
                    )

                    // wake up this object
                    check.ApplyImpulse(
                        clipModel.GetEntity(),
                        clipModel.GetId(),
                        clipModel.GetOrigin(),
                        getVec3Origin()
                    )

                    // add mass of pushed entity
                    totalMass += physics.GetMass()
                }

                // if the entity is not blocking
                if (res != PUSH_BLOCKED) {
                    i++
                    continue
                }

                // if the blocking entity is a projectile
                if (check is idProjectile) {
                    check.ProcessEvent(EV_Explode)
                    i++
                    continue
                }

                // if blocking entities should be crushed
                if ((flags and PUSHFL_CRUSH) != 0) {
                    check.Damage(
                        clipModel.GetEntity(),
                        clipModel.GetEntity(),
                        getVec3Origin(),
                        "damage_crush",
                        1.0f,
                        Clip.CLIPMODEL_ID_TO_JOINT_HANDLE(pushResults.c.id)
                    )
                    i++
                    continue
                }

                // if the entity is an active articulated figure and gibs
                if (check is idAFEntity_Base && check.spawnArgs.GetBool("gib")) {
                    if (check.IsActiveAF()) {
                        check.ProcessEvent(EV_Gib, "damage_Gib")
                    }
                }

                // blocked
                results.set(pushResults)
                results.fraction = 0.0f
                results.endAxis.set(clipModel.GetAxis())
                results.endpos.set(clipModel.GetOrigin())
                results.c.entityNum = check.entityNumber
                results.c.id = 0
                if (!wasEnabled) {
                    clipModel.Disable()
                }
                return totalMass
                i++
            }
            if (!wasEnabled) {
                clipModel.Disable()
            }
            return totalMass
        }

        /*
         ============
         idPush::ClipPush

         Try to push other entities by moving the given entity.
         ============
         */
        fun ClipPush(
            results: trace_s,
            pusher: idEntity,
            flags: Int,
            oldOrigin: idVec3,
            oldAxis: idMat3,
            newOrigin: idVec3,
            newAxis: idMat3
        ): Float {
            val translation = idVec3()
            val rotation: idRotation
            var mass: Float
            mass = 0.0f
            results.fraction = 1.0f
            results.endpos.set(newOrigin)
            results.endAxis.set(newAxis)
            results.c = contactInfo_t() //memset( &results.c, 0, sizeof( results.c ) );//TODOS:

            // translational push
            translation.set(newOrigin.minus(oldOrigin))

            // if the pusher translates
            if (translation != getVec3Origin()) {
                mass += ClipTranslationalPush(results, pusher, flags, newOrigin, translation)
                if (results.fraction < 1.0f) {
                    newOrigin.set(oldOrigin)
                    newAxis.set(oldAxis)
                    return mass
                }
            } else {
                newOrigin.set(oldOrigin)
            }

            // rotational push
            rotation = oldAxis.Transpose().times(newAxis).ToRotation()
            rotation.SetOrigin(newOrigin)
            rotation.Normalize180()
            rotation.ReCalculateMatrix() // recalculate the rotation matrix to avoid accumulating rounding errors

            // if the pusher rotates
            if (rotation.GetAngle() != 0.0f) {

                // recalculate new axis to avoid floating point rounding problems
                newAxis.set(oldAxis.times(rotation.ToMat3()))
                newAxis.OrthoNormalizeSelf()
                newAxis.FixDenormals()
                newAxis.FixDegeneracies()
                pusher.GetPhysics().GetClipModel()!!.SetPosition(newOrigin, oldAxis)
                mass += ClipRotationalPush(results, pusher, flags, newAxis, rotation)
                if (results.fraction < 1.0f) {
                    newOrigin.set(oldOrigin)
                    newAxis.set(oldAxis)
                    return mass
                }
            } else {
                newAxis.set(oldAxis)
            }
            return mass
        }

        // initialize saving the positions of entities being pushed
        fun InitSavingPushedEntityPositions() {
            numPushed = 0
        }

        // move all pushed entities back to their previous position
        fun RestorePushedEntityPositions() {
            var i: Int
            i = 0
            while (i < numPushed) {


                // if the entity is an actor
                if (pushed[i].ent is idActor) {
                    // set back the delta view angles
                    (pushed[i].ent as idActor).SetDeltaViewAngles(pushed[i].deltaViewAngles)
                }

                // restore the physics state
                pushed[i].ent.GetPhysics().RestoreState()
                i++
            }
        }

        // returns the number of pushed entities
        fun GetNumPushedEntities(): Int {
            return numPushed
        }

        //
        // get the ith pushed entity
        fun GetPushedEntity(i: Int): idEntity {
            assert(i >= 0 && i < numPushed)
            return pushed[i].ent
        }

        private fun SaveEntityPosition(ent: idEntity) {
            var i: Int

            // if already saved the physics state for this entity
            i = 0
            while (i < numPushed) {
                if (pushed[i].ent === ent) {
                    return
                }
                i++
            }

            // don't overflow
            if (numPushed >= Game_local.MAX_GENTITIES) {
                idGameLocal.Error("more than MAX_GENTITIES pushed entities")
                return
            }
            pushed[numPushed] = pushed_s()
            pushed[numPushed].ent = ent

            // if the entity is an actor
            if (ent is idActor) {
                // save the delta view angles
                pushed[numPushed].deltaViewAngles.set(ent.GetDeltaViewAngles())
            }

            // save the physics state
            ent.GetPhysics().SaveState()
            numPushed++
        }

        private fun RotateEntityToAxial(ent: idEntity, rotationPoint: idVec3): Boolean {
            var i: Int
            val trace = trace_s()
            var rotation: idRotation
            val axis: idMat3 = idMat3()
            val physics: idPhysics
            physics = ent.GetPhysics()
            axis.set(physics.GetAxis())
            if (!axis.IsRotated()) {
                return true
            }
            // try to rotate the bbox back to axial with at most four rotations
            i = 0
            while (i < 4) {
                axis.set(physics.GetAxis())
                rotation = axis.ToRotation()
                rotation.Scale(-1.0f)
                rotation.SetOrigin(rotationPoint)
                // tiny float numbers in the clip axis, this can get the entity stuck
                if (rotation.GetAngle() == 0.0f) {
                    physics.SetAxis(idMat3.getMat3_identity())
                    return true
                }
                //
                ent.GetPhysics().ClipRotation(trace, rotation, null)
                // if the full rotation is possible
                if (trace.fraction >= 1.0f) {
                    // set bbox in final axial position
                    physics.SetOrigin(trace.endpos)
                    physics.SetAxis(idMat3.getMat3_identity())
                    return true
                } // if partial rotation was possible
                else if (trace.fraction > 0.0f) {
                    // partial rotation
                    physics.SetOrigin(trace.endpos)
                    physics.SetAxis(trace.endAxis)
                }
                // next rotate around collision point
                rotationPoint.set(trace.c.point)
                i++
            }
            return false
        }

        //
        //
        private fun ClipEntityRotation(
            trace: trace_s,
            ent: idEntity,
            clipModel: idClipModel?,
            skip: idClipModel?,
            rotation: idRotation
        ) {
            skip?.Disable()

            ent.GetPhysics().ClipRotation(trace, rotation, clipModel)

            skip?.Enable()
        }

        private fun ClipEntityTranslation(
            trace: trace_s,
            ent: idEntity,
            clipModel: idClipModel?,
            skip: idClipModel?,
            translation: idVec3
        ) {
            skip?.Disable()

            ent.GetPhysics().ClipTranslation(trace, translation, clipModel)

            skip?.Enable()
        }

        private fun TryTranslatePushEntity(
            results: trace_s,
            check: idEntity,
            clipModel: idClipModel,
            flags: Int,
            newOrigin: idVec3,
            move: idVec3
        ): Int {
            val trace = trace_s()
            val checkMove = idVec3()
            val physics: idPhysics?
            physics = check.GetPhysics()

            results.fraction = 1.0f
            results.endpos.set(newOrigin)
            results.endAxis.set(clipModel.GetAxis())
            results.c = contactInfo_t() //memset( &results.c, 0, sizeof( results.c ) );//TODOS:

            // always pushed when standing on the pusher
            if (physics.IsGroundClipModel(clipModel.GetEntity()!!.entityNumber, clipModel.GetId())) {
                // move the entity colliding with all other entities except the pusher itself
                ClipEntityTranslation(trace, check, null, clipModel, move)
                // if there is a collision
                if (trace.fraction < 1.0f) {
                    // vector along which the entity is pushed
                    checkMove.set(move.times(trace.fraction))
                    // test if the entity can stay at it's partly pushed position by moving the entity in reverse only colliding with pusher
                    ClipEntityTranslation(results, check, clipModel, null, move.minus(checkMove).unaryMinus())
                    // if there is a collision
                    if (results.fraction < 1.0f) {

                        // FIXME: try to push the blocking entity as well or try to slide along collision plane(s)?
                        results.c.normal.set(results.c.normal.unaryMinus())
                        results.c.dist = -results.c.dist

                        // the entity will be crushed between the pusher and some other entity
                        return PUSH_BLOCKED
                    }
                } else {
                    // vector along which the entity is pushed
                    checkMove.set(move)
                }
            } else {
                // move entity in reverse only colliding with pusher
                ClipEntityTranslation(results, check, clipModel, null, move.unaryMinus())
                // if no collision with the pusher then the entity is not pushed by the pusher
                if (results.fraction >= 1.0f) {
                    return PUSH_NO
                }
                // vector along which the entity is pushed
                checkMove.set(move.times(1.0f - results.fraction))
                // move the entity colliding with all other entities except the pusher itself
                ClipEntityTranslation(trace, check, null, clipModel, checkMove)
                // if there is a collisions
                if (trace.fraction < 1.0f) {
                    results.c.normal.set(results.c.normal.unaryMinus())
                    results.c.dist = -results.c.dist

                    // FIXME: try to push the blocking entity as well ?
                    // FIXME: handle sliding along more than one collision plane ?
                    // FIXME: this code has issues, player pushing box into corner in "maps/mre/aaron/test.map"

                    /*
                     oldOrigin = physics.GetOrigin();

                     // movement still remaining
                     checkMove *= (1.0f - trace.fraction);

                     // project the movement along the collision plane
                     if ( !checkMove.ProjectAlongPlane( trace.c.normal, 0.1f, 1.001f ) ) {
                     return PUSH_BLOCKED;
                     }
                     checkMove *= 1.001f;

                     // move entity from collision point along the collision plane
                     physics.SetOrigin( trace.endpos );
                     ClipEntityTranslation( trace, check, NULL, NULL, checkMove );

                     if ( trace.fraction < 1.0f ) {
                     physics.SetOrigin( oldOrigin );
                     return PUSH_BLOCKED;
                     }

                     checkMove = trace.endpos - oldOrigin;

                     // move entity in reverse only colliding with pusher
                     physics.SetOrigin( trace.endpos );
                     ClipEntityTranslation( trace, check, clipModel, NULL, -move );

                     physics.SetOrigin( oldOrigin );
                     */if (trace.fraction < 1.0f) {
                        return PUSH_BLOCKED
                    }
                }
            }
            SaveEntityPosition(check)

            // translate the entity
            physics.Translate(checkMove)

// #ifdef TRANSLATIONAL_PUSH_DEBUG
            // // set the pusher in the translated position
            // clipModel.Link( gameLocal.clip, clipModel.GetEntity(), clipModel.GetId(), newOrigin, clipModel.GetAxis() );
            // if ( physics.ClipContents( clipModel ) ) {
            // if ( !startsolid ) {
            // int bah = 1;
            // }
            // }
// #endif
            return PUSH_OK
        }

        private fun TryRotatePushEntity(
            results: trace_s,
            check: idEntity,
            clipModel: idClipModel,
            flags: Int,
            newAxis: idMat3,
            rotation: idRotation
        ): Int {
            val trace = trace_s()
            val rotationPoint = idVec3()
            var newRotation: idRotation = idRotation()
            val checkAngle: Float
            val physics: idPhysics
            physics = check.GetPhysics()

// #ifdef ROTATIONAL_PUSH_DEBUG
            // bool startsolid = false;
            // if ( physics.ClipContents( clipModel ) ) {
            // startsolid = true;
            // }
// #endif
            results.fraction = 1.0f
            results.endpos.set(clipModel.GetOrigin())
            results.endAxis.set(newAxis)
            results.c = contactInfo_t() //memset( &results.c, 0, sizeof( results.c ) );//TODOS:

            // always pushed when standing on the pusher
            if (physics.IsGroundClipModel(clipModel.GetEntity()!!.entityNumber, clipModel.GetId())) {
                // rotate the entity colliding with all other entities except the pusher itself
                ClipEntityRotation(trace, check, null, clipModel, rotation)
                // if there is a collision
                if (trace.fraction < 1.0f) {
                    // angle along which the entity is pushed
                    checkAngle = rotation.GetAngle() * trace.fraction
                    // test if the entity can stay at it's partly pushed position by rotating
                    // the entity in reverse only colliding with pusher
                    newRotation.Set(rotation.GetOrigin(), rotation.GetVec(), -(rotation.GetAngle() - checkAngle))
                    ClipEntityRotation(results, check, clipModel, null, newRotation)
                    // if there is a collision
                    if (results.fraction < 1.0f) {

                        // FIXME: try to push the blocking entity as well or try to slide along collision plane(s)?
                        results.c.normal.set(results.c.normal.unaryMinus())
                        results.c.dist = -results.c.dist

                        // the entity will be crushed between the pusher and some other entity
                        return PUSH_BLOCKED
                    }
                } else {
                    // angle along which the entity is pushed
                    checkAngle = rotation.GetAngle()
                }
                // point to rotate entity bbox around back to axial
                rotationPoint.set(physics.GetOrigin())
            } else {
                // rotate entity in reverse only colliding with pusher
                // FIX: C++ struct assignment does value copy; Kotlin = was reference alias
                // Scale(-1) would mutate the original rotation parameter
                newRotation.Set(rotation.GetOrigin(), rotation.GetVec(), rotation.GetAngle())
                newRotation.Scale(-1.0f)
                //
                ClipEntityRotation(results, check, clipModel, null, newRotation)
                // if no collision with the pusher then the entity is not pushed by the pusher
                if (results.fraction >= 1.0f) {
// #ifdef ROTATIONAL_PUSH_DEBUG
                    // // set pusher into final position
                    // clipModel.Link( gameLocal.clip, clipModel.GetEntity(), clipModel.GetId(), clipModel.GetOrigin(), newAxis );
                    // if ( physics.ClipContents( clipModel ) ) {
                    // if ( !startsolid ) {
                    // int bah = 1;
                    // }
                    // }
// #endif
                    return PUSH_NO
                }
                // get point to rotate bbox around back to axial
                rotationPoint.set(results.c.point)
                // angle along which the entity will be pushed
                checkAngle = rotation.GetAngle() * (1.0f - results.fraction)
                // rotate the entity colliding with all other entities except the pusher itself
                newRotation.Set(rotation.GetOrigin(), rotation.GetVec(), checkAngle)
                ClipEntityRotation(trace, check, null, clipModel, newRotation)
                // if there is a collision
                if (trace.fraction < 1.0f) {

                    // FIXME: try to push the blocking entity as well or try to slide along collision plane(s)?
                    results.c.normal.set(results.c.normal.unaryMinus())
                    results.c.dist = -results.c.dist

                    // the entity will be crushed between the pusher and some other entity
                    return PUSH_BLOCKED
                }
            }
            SaveEntityPosition(check)
            newRotation.Set(rotation.GetOrigin(), rotation.GetVec(), checkAngle)
            // NOTE:	this code prevents msvc 6.0f & 7.0f from screwing up the above code in
            //			release builds moving less floats than it should
//	static float shit = checkAngle;
            newRotation.RotatePoint(rotationPoint)

            // rotate the entity
            physics.Rotate(newRotation)

            // set pusher into final position
            clipModel.Link(
                Game_local.gameLocal.clip,
                clipModel.GetEntity(),
                clipModel.GetId(),
                clipModel.GetOrigin(),
                newAxis
            )

// #ifdef ROTATIONAL_PUSH_DEBUG
            // if ( physics.ClipContents( clipModel ) ) {
            // if ( !startsolid ) {
            // int bah = 1;
            // }
            // }
// #endif
            // if the entity uses actor physics
            if (physics is idPhysics_Actor) {

                // rotate the collision model back to axial
                if (!RotateEntityToAxial(check, rotationPoint)) {
                    // don't allow rotation if the bbox is no longer axial
                    return PUSH_BLOCKED
                }
            }

// #ifdef ROTATIONAL_PUSH_DEBUG
            // if ( physics.ClipContents( clipModel ) ) {
            // if ( !startsolid ) {
            // int bah = 1;
            // }
            // }
// #endif
            // if the entity is an actor using actor physics
            if (check is idActor && physics is idPhysics_Actor) {

                // if the entity is standing ontop of the pusher
                if (physics.IsGroundClipModel(clipModel.GetEntity()!!.entityNumber, clipModel.GetId())) {
                    // rotate actor view
                    val actor = check
                    val delta = actor.GetDeltaViewAngles()
                    delta.yaw += newRotation.ToMat3()[0].ToYaw()
                    actor.SetDeltaViewAngles(delta)
                }
            }
            return PUSH_OK
        }

        private fun DiscardEntities(
            entityList: Array<idEntity?>,
            numEntities: Int,
            flags: Int,
            pusher: idEntity
        ): Int {
            var i: Int
            var num: Int
            var check: idEntity

            // remove all entities we cannot or should not push from the list
            num = 0.also { i = it }
            while (i < numEntities) {
                check = entityList[i]!!

                // if the physics object is not pushable
                if (!check.GetPhysics().IsPushable()) {
                    i++
                    continue
                }

                // if the entity doesn't clip with this pusher
                if (0 == check.GetPhysics().GetClipMask() and pusher.GetPhysics().GetContents()) {
                    i++
                    continue
                }

                // don't push players in noclip mode
                if (check is idPlayer && check.noclip) {
                    i++
                    continue
                }

                // if we should only push idMoveable entities
                if ((flags and PUSHFL_ONLYMOVEABLE) != 0 && check !is idMoveable) {
                    i++
                    continue
                }

                // if we shouldn't push entities the clip model rests upon
                if ((flags and PUSHFL_NOGROUNDENTITIES) != 0) {
                    if (pusher.GetPhysics().IsGroundEntity(check.entityNumber)) {
                        i++
                        continue
                    }
                }

                // keep entity in list
                entityList[num++] = entityList[i]
                i++
            }
            return num
        }

        //
        //
        private class pushed_s {
            val deltaViewAngles // actor delta view angles
                    : idAngles = idAngles()
            lateinit var ent // pushed entity
                    : idEntity
        }

        private class pushedGroup_s {
            var ent: idEntity? = null
            var fraction = 0.0f
            var groundContact = false
            var test = false
        } // #endif
    }
}
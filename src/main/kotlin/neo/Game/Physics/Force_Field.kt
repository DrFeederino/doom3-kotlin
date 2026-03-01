/*
 * Copyright (C) 1999-2011 id Software LLC, a ZeniMax Media company.
 * Translated to Kotlin by Dr. Feederino with support of Claude Code
 *
 * This file is part of the Doom 3 Kotlin project.
 * Original source: neo/Game/Physics/Force_Field.h, neo/Game/Physics/Force_Field.cpp
 */

package neo.Game.Physics

import neo.Game.GameSys.SaveGame.idRestoreGame
import neo.Game.GameSys.SaveGame.idSaveGame
import neo.Game.Game_local
import neo.Game.Game_local.idGameLocal
import neo.Game.Physics.Clip.idClipModel
import neo.Game.Physics.Force.idForce
import neo.Game.Physics.Physics_Monster.idPhysics_Monster
import neo.Game.Physics.Physics_Player.idPhysics_Player
import neo.TempDump
import neo.idlib.BV.idBounds
import neo.idlib.math.idVec3

class Force_Field {
    enum class forceFieldApplyType {
        FORCEFIELD_APPLY_FORCE, FORCEFIELD_APPLY_VELOCITY, FORCEFIELD_APPLY_IMPULSE;

        companion object {
            fun oGet(index: Int): forceFieldApplyType {
                return if (index >= entries.size) {
                    entries[0]
                } else {
                    entries[index]
                }
            }
        }
    }

    /*
     ===============================================================================

     Force field

     ===============================================================================
     */
    enum class forceFieldType {
        FORCEFIELD_UNIFORM, FORCEFIELD_EXPLOSION, FORCEFIELD_IMPLOSION;

        companion object {
            fun oGet(index: Int): forceFieldType {
                return if (index >= entries.size) {
                    entries[0]
                } else {
                    entries[index]
                }
            }
        }
    }

    class idForce_Field : idForce() {
        //	CLASS_PROTOTYPE( idForce_Field );
        private var applyType: forceFieldApplyType
        private var clipModel: idClipModel?
        private val dir: idVec3
        private var magnitude: Float
        private var monsterOnly: Boolean
        private var playerOnly: Boolean
        private var randomTorque: Float

        // force properties
        private var type: forceFieldType
        override fun Save(savefile: idSaveGame) {
            savefile.WriteInt(TempDump.etoi(type))
            savefile.WriteInt(applyType.ordinal)
            savefile.WriteFloat(magnitude)
            savefile.WriteVec3(dir)
            savefile.WriteFloat(randomTorque)
            savefile.WriteBool(playerOnly)
            savefile.WriteBool(monsterOnly)
            savefile.WriteClipModel(clipModel)
        }

        override fun Restore(savefile: idRestoreGame) {
            type = forceFieldType.entries.toTypedArray()[savefile.ReadInt()]
            applyType = forceFieldApplyType.entries.toTypedArray()[savefile.ReadInt()]
            magnitude = savefile.ReadFloat()
            savefile.ReadVec3(dir)
            randomTorque = savefile.ReadFloat()
            playerOnly = savefile.ReadBool()
            monsterOnly = savefile.ReadBool()
            savefile.ReadClipModel(clipModel!!)
        }

        //	virtual				~idForce_Field( void );
        // uniform constant force
        fun Uniform(force: idVec3) {
            dir.set(force)
            magnitude = dir.Normalize()
            type = forceFieldType.FORCEFIELD_UNIFORM
        }

        // explosion from clip model origin	
        fun Explosion(force: Float) {
            magnitude = force
            type = forceFieldType.FORCEFIELD_EXPLOSION
        }

        // implosion towards clip model origin	
        fun Implosion(force: Float) {
            magnitude = force
            type = forceFieldType.FORCEFIELD_IMPLOSION
        }

        // add random torque	
        fun RandomTorque(force: Float) {
            randomTorque = force
        }

        // should the force field apply a force, velocity or impulse	
        fun SetApplyType(type: forceFieldApplyType) {
            applyType = type
        }

        // make the force field only push players	
        fun SetPlayerOnly(set: Boolean) {
            playerOnly = set
        }

        // make the force field only push monsters	
        fun SetMonsterOnly(set: Boolean) {
            monsterOnly = set
        }

        // clip model describing the extents of the force field	
        fun SetClipModel(clipModel: idClipModel) {
            if (this.clipModel != null && clipModel !== this.clipModel) {
                idClipModel.delete(this.clipModel!!)
            }
            this.clipModel = clipModel
        }

        // common force interface
        override fun Evaluate(time: Int) {
            val numClipModels: Int
            var i: Int
            val bounds = idBounds()
            val force = idVec3()
            val torque = idVec3()
            val angularVelocity = idVec3()
            var cm: idClipModel
            val clipModelList = arrayOfNulls<idClipModel>(Game_local.MAX_GENTITIES)
            assert(clipModel != null)
            bounds.FromTransformedBounds(clipModel!!.GetBounds(), clipModel!!.GetOrigin(), clipModel!!.GetAxis())
            numClipModels =
                Game_local.gameLocal.clip.ClipModelsTouchingBounds(bounds, -1, clipModelList, Game_local.MAX_GENTITIES)
            torque.Zero()
            i = 0
            while (i < numClipModels) {
                cm = clipModelList[i]!!
                if (!cm.IsTraceModel()) {
                    i++
                    continue
                }
                val entity = cm.GetEntity()
                if (null == entity) {
                    i++
                    continue
                }
                val physics = entity.GetPhysics()
                if (playerOnly) {
                    if (physics !is idPhysics_Player) {
                        i++
                        continue
                    }
                } else if (monsterOnly) {
                    if (physics !is idPhysics_Monster) {
                        i++
                        continue
                    }
                }
                if (
                    Game_local.gameLocal.clip.ContentsModel(
                        cm.GetOrigin(),
                        cm,
                        cm.GetAxis(),
                        -1,
                        clipModel!!.Handle(),
                        clipModel!!.GetOrigin(),
                        clipModel!!.GetAxis()
                    ) == 0
                ) {
                    i++
                    continue
                }
                when (type) {
                    forceFieldType.FORCEFIELD_UNIFORM -> {
                        force.set(dir)
                    }

                    forceFieldType.FORCEFIELD_EXPLOSION -> {
                        force.set(cm.GetOrigin().minus(clipModel!!.GetOrigin()))
                        force.Normalize()
                    }

                    forceFieldType.FORCEFIELD_IMPLOSION -> {
                        force.set(clipModel!!.GetOrigin().minus(cm.GetOrigin()))
                        force.Normalize()
                    }

                    else -> {
                        idGameLocal.Error("idForce_Field: invalid type")
                    }
                }
                if (randomTorque != 0.0f) {
                    torque[0] = Game_local.gameLocal.random.CRandomFloat()
                    torque[1] = Game_local.gameLocal.random.CRandomFloat()
                    torque[2] = Game_local.gameLocal.random.CRandomFloat()
                    if (torque.Normalize() == 0.0f) {
                        torque[2] = 1.0f
                    }
                }
                when (applyType) {
                    forceFieldApplyType.FORCEFIELD_APPLY_FORCE -> {
                        if (randomTorque != 0.0f) {
                            entity.AddForce(
                                Game_local.gameLocal.world,
                                cm.GetId(),
                                cm.GetOrigin() + torque.Cross(dir) * randomTorque,
                                dir * magnitude
                            )
                        } else {
                            entity.AddForce(
                                Game_local.gameLocal.world,
                                cm.GetId(),
                                cm.GetOrigin(),
                                force * magnitude
                            )
                        }
                    }

                    forceFieldApplyType.FORCEFIELD_APPLY_VELOCITY -> {
                        physics.SetLinearVelocity(force.times(magnitude), cm.GetId())
                        if (randomTorque != 0.0f) {
                            angularVelocity.set(physics.GetAngularVelocity(cm.GetId()))
                            physics.SetAngularVelocity(
                                (angularVelocity + torque * randomTorque) * 0.5f, cm.GetId()
                            )
                        }
                    }

                    forceFieldApplyType.FORCEFIELD_APPLY_IMPULSE -> {
                        if (randomTorque != 0.0f) {
                            entity.ApplyImpulse(
                                Game_local.gameLocal.world,
                                cm.GetId(),
                                cm.GetOrigin() + torque.Cross(dir) * randomTorque,
                                dir * magnitude
                            )
                        } else {
                            entity.ApplyImpulse(
                                Game_local.gameLocal.world,
                                cm.GetId(),
                                cm.GetOrigin(),
                                force * magnitude
                            )
                        }
                    }

                    else -> {
                        idGameLocal.Error("idForce_Field: invalid apply type")
                    }
                }
                i++
            }
        }

        init {
            type = forceFieldType.FORCEFIELD_UNIFORM
            applyType = forceFieldApplyType.FORCEFIELD_APPLY_FORCE
            magnitude = 0.0f
            dir = idVec3(0.0f, 0.0f, 1.0f)
            randomTorque = 0.0f
            playerOnly = false
            monsterOnly = false
            clipModel = null
        }
    }
}
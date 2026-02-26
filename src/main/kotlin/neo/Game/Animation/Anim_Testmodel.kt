package neo.Game.Animation

import neo.Game.Actor.copyJoints_t
import neo.Game.Animation.Anim.jointModTransform_t
import neo.Game.Animation.Anim_Blend.idAnim
import neo.Game.Animation.Anim_Blend.idAnimator
import neo.Game.Animation.Anim_Import.idModelExport
import neo.Game.EV_FootstepLeft
import neo.Game.EV_FootstepRight
import neo.Game.Entity
import neo.Game.Entity.idAnimatedEntity
import neo.Game.Entity.idEntity
import neo.Game.GameSys.Class.*
import neo.Game.GameSys.EV_Remove
import neo.Game.GameSys.Event.idEventDef
import neo.Game.GameSys.SaveGame.idRestoreGame
import neo.Game.GameSys.SaveGame.idSaveGame
import neo.Game.GameSys.SysCvar
import neo.Game.Game_local
import neo.Game.Game_local.gameSoundChannel_t
import neo.Game.Game_local.idEntityPtr
import neo.Game.Physics.Physics_Parametric.idPhysics_Parametric
import neo.Game.Player.idPlayer
import neo.Renderer.Material
import neo.Renderer.Model
import neo.Renderer.ModelManager
import neo.Renderer.RenderWorld
import neo.TempDump
import neo.TempDump.void_callback
import neo.framework.CmdSystem
import neo.framework.CmdSystem.cmdFunction_t
import neo.framework.DeclManager
import neo.framework.DeclManager.declType_t
import neo.idlib.BV.idBounds
import neo.idlib.CmdArgs
import neo.idlib.Dict_h.idDict
import neo.idlib.Dict_h.idKeyValue
import neo.idlib.Text.Str
import neo.idlib.Text.Str.idStr
import neo.idlib.containers.List
import neo.idlib.idLib
import neo.idlib.math.*
import neo.idlib.math.Matrix.idMat3

/*
 =============================================================================

 MODEL TESTING

 Model viewing can begin with either "testmodel <modelname>"

 The names must be the full pathname after the basedir, like 
 "models/weapons/v_launch/tris.md3" or "players/male/tris.md3"

 Extension will default to ".ase" if not specified.

 Testmodel will create a fake entity 100 units in front of the current view
 position, directly facing the viewer.  It will remain immobile, so you can
 move around it to view it from different angles.

 g_testModelRotate
 g_testModelAnimate
 g_testModelBlend

 =============================================================================
 */
class Anim_Testmodel {
    /*
     ==============================================================================================

     idTestModel

     ==============================================================================================
     */
    class idTestModel     //
    //
        : idAnimatedEntity() {
        companion object {
            // CLASS_PROTOTYPE( idTestModel );
            private val eventCallbacks: MutableMap<idEventDef, eventCallback_t<*>> = HashMap()

            // ~idTestModel();
            fun getEventCallBacks(): MutableMap<idEventDef, eventCallback_t<*>> {
                return eventCallbacks
            }

            init {
                eventCallbacks.putAll(idAnimatedEntity.getEventCallBacks())
                eventCallbacks[EV_FootstepLeft] =
                    eventCallback_t0 { obj: idTestModel -> obj.Event_Footstep() }
                eventCallbacks[EV_FootstepRight] =
                    eventCallback_t0 { obj: idTestModel -> obj.Event_Footstep() }
            }
        }

        private var anim = 0
        private val animName: idStr = idStr()
        private var animTime = 0

        //
        private val copyJoints: List.idList<copyJoints_t> = List.idList()
        private val customAnim: idAnim? = null
        private var frame = 0
        private val head: idEntityPtr<idEntity?> = idEntityPtr()
        private var headAnim = 0
        private var headAnimator: idAnimator? = null
        private var mode = 0
        private val physicsObj: idPhysics_Parametric = idPhysics_Parametric()
        private var startTime = 0
        override fun Save(savefile: idSaveGame) {}
        override fun Restore(savefile: idRestoreGame) {
            // FIXME: one day we may actually want to save/restore test models, but for now we'll just delete them
//	delete this;
        }

        override fun Spawn() {
            super.Spawn()
            val size = idVec3()
            val bounds = idBounds()
            val headModel: String?
            val   /*jointHandle_t*/joint: Int
            var jointName = idStr()
            val origin = idVec3()
            val modelOffset = idVec3()
            val axis = idMat3()
            var kv: idKeyValue?
            if (renderEntity!!.hModel != null && renderEntity!!.hModel!!.IsDefaultModel() && animator.ModelDef() == null) {
                Game_local.gameLocal.Warning(
                    "Unable to create testmodel for '%s' : model defaulted",
                    spawnArgs.GetString("model")
                )
                PostEventMS(EV_Remove, 0)
                return
            }
            mode = SysCvar.g_testModelAnimate.GetInteger()
            animator.RemoveOriginOffset(SysCvar.g_testModelAnimate.GetInteger() == 1)
            physicsObj.SetSelf(this)
            physicsObj.SetOrigin(GetPhysics().GetOrigin())
            physicsObj.SetAxis(GetPhysics().GetAxis())
            if (spawnArgs.GetVector("mins", null, bounds[0])) {
                spawnArgs.GetVector("maxs", null, bounds[1])
                physicsObj.SetClipBox(bounds, 1.0f)
                physicsObj.SetContents(0)
            } else if (spawnArgs.GetVector("size", null, size)) {
                bounds[0].set(size.x * -0.5f, size.y * -0.5f, 0.0f)
                bounds[1].set(size.x * 0.5f, size.y * 0.5f, size.z)
                physicsObj.SetClipBox(bounds, 1.0f)
                physicsObj.SetContents(0)
            }
            spawnArgs.GetVector("offsetModel", "0 0 0", modelOffset)

            // add the head model if it has one
            headModel = spawnArgs.GetString("def_head", "")!!
            if (headModel.isNotEmpty()) {
                jointName.set(spawnArgs.GetString("head_joint"))
                joint = animator.GetJointHandle(jointName.toString())
                if (joint == Model.INVALID_JOINT) {
                    Game_local.gameLocal.Warning("Joint '%s' not found for 'head_joint'", jointName)
                } else {
                    // copy any sounds in case we have frame commands on the head
                    val args = idDict()
                    var sndKV = spawnArgs.MatchPrefix("snd_", null)
                    while (sndKV != null) {
                        args.Set(sndKV.GetKey(), sndKV.GetValue())
                        sndKV = spawnArgs.MatchPrefix("snd_", sndKV)
                    }
                    head.oSet(Game_local.gameLocal.SpawnEntityType(idAnimatedEntity::class.java, args))
                    animator.GetJointTransform(joint, Game_local.gameLocal.time, origin, axis)
                    origin.set(
                        GetPhysics().GetOrigin() + (origin + modelOffset) * GetPhysics().GetAxis()
                    )
                    head.GetEntity()!!.SetModel(headModel)
                    head.GetEntity()!!.SetOrigin(origin)
                    head.GetEntity()!!.SetAxis(GetPhysics().GetAxis())
                    head.GetEntity()!!.BindToJoint(this, animator.GetJointName(joint), true)
                    headAnimator = head.GetEntity()!!.GetAnimator()

                    // set up the list of joints to copy to the head
                    kv = spawnArgs.MatchPrefix("copy_joint", null)
                    while (kv != null) {
                        val copyJoint = copyJoints_t()
                        jointName.set(kv.GetKey())
                        if (jointName.StripLeadingOnce("copy_joint_world ")) {
                            copyJoint.mod = jointModTransform_t.JOINTMOD_WORLD_OVERRIDE
                        } else {
                            jointName.StripLeadingOnce("copy_joint ")
                            copyJoint.mod = jointModTransform_t.JOINTMOD_LOCAL_OVERRIDE
                        }
                        copyJoint.from._val = animator.GetJointHandle(jointName.toString())
                        if (copyJoint.from._val == Model.INVALID_JOINT) {
                            Game_local.gameLocal.Warning("Unknown copy_joint '%s'", jointName)
                            kv = spawnArgs.MatchPrefix("copy_joint", kv)
                            continue
                        }
                        copyJoint.to._val = headAnimator!!.GetJointHandle(jointName.toString())
                        if (copyJoint.to._val == Model.INVALID_JOINT) {
                            Game_local.gameLocal.Warning("Unknown copy_joint '%s' on head", jointName)
                            kv = spawnArgs.MatchPrefix("copy_joint", kv)
                            continue
                        }
                        copyJoints.Append(copyJoint)
                        kv = spawnArgs.MatchPrefix("copy_joint", kv)
                    }
                }
            }

            // start any shader effects based off of the spawn time
            renderEntity!!.shaderParms[RenderWorld.SHADERPARM_TIMEOFFSET] =
                -MS2SEC(Game_local.gameLocal.time.toFloat())
            SetPhysics(physicsObj)
            Game_local.gameLocal.Printf(
                "Added testmodel at origin = '%s',  angles = '%s'\n",
                GetPhysics().GetOrigin().ToString(),
                GetPhysics().GetAxis().ToAngles().ToString()
            )
            BecomeActive(Entity.TH_THINK)
        }

        /*
         ================
         idTestModel::ShouldConstructScriptObjectAtSpawn

         Called during idEntity::Spawn to see if it should construct the script object or not.
         Overridden by subclasses that need to spawn the script object themselves.
         ================
         */
        override fun ShouldConstructScriptObjectAtSpawn(): Boolean {
            return false
        }

        fun NextAnim(args: CmdArgs.idCmdArgs?) {
            if (animator.NumAnims() == 0) {
                return
            }
            anim++
            if (anim >= animator.NumAnims()) {
                // anim 0 is no anim
                anim = 1
            }
            startTime = Game_local.gameLocal.time
            animTime = animator.AnimLength(anim)
            animName.set(animator.AnimFullName(anim))
            headAnim = 0
            if (headAnimator != null) {
                headAnimator!!.ClearAllAnims(Game_local.gameLocal.time, 0)
                headAnim = headAnimator!!.GetAnim(animName.toString())
                if (0 == headAnim) {
                    headAnim = headAnimator!!.GetAnim("idle")
                }
                if (headAnim != 0 && headAnimator!!.AnimLength(headAnim) > animTime) {
                    animTime = headAnimator!!.AnimLength(headAnim)
                }
            }
            Game_local.gameLocal.Printf(
                "anim '%s', %d.%03d seconds, %d frames\n",
                animName,
                animator.AnimLength(anim) / 1000,
                animator.AnimLength(anim) % 1000,
                animator.NumFrames(anim)
            )
            if (headAnim != 0) {
                Game_local.gameLocal.Printf(
                    "head '%s', %d.%03d seconds, %d frames\n",
                    headAnimator!!.AnimFullName(headAnim),
                    headAnimator!!.AnimLength(headAnim) / 1000,
                    headAnimator!!.AnimLength(headAnim) % 1000,
                    headAnimator!!.NumFrames(headAnim)
                )
            }

            // reset the anim
            mode = -1
            frame = 1
        }

        fun PrevAnim(args: CmdArgs.idCmdArgs?) {
            if (animator.NumAnims() == 0) {
                return
            }
            headAnim = 0
            anim--
            if (anim < 0) {
                anim = animator.NumAnims() - 1
            }
            startTime = Game_local.gameLocal.time
            animTime = animator.AnimLength(anim)
            animName.set(animator.AnimFullName(anim))
            headAnim = 0
            if (headAnimator != null) {
                headAnimator!!.ClearAllAnims(Game_local.gameLocal.time, 0)
                headAnim = headAnimator!!.GetAnim(animName.toString())
                if (0 == headAnim) {
                    headAnim = headAnimator!!.GetAnim("idle")
                }
                if (headAnim != 0 && headAnimator!!.AnimLength(headAnim) > animTime) {
                    animTime = headAnimator!!.AnimLength(headAnim)
                }
            }
            Game_local.gameLocal.Printf(
                "anim '%s', %d.%03d seconds, %d frames\n",
                animName,
                animator.AnimLength(anim) / 1000,
                animator.AnimLength(anim) % 1000,
                animator.NumFrames(anim)
            )
            if (headAnim != 0) {
                Game_local.gameLocal.Printf(
                    "head '%s', %d.%03d seconds, %d frames\n",
                    headAnimator!!.AnimFullName(headAnim),
                    headAnimator!!.AnimLength(headAnim) / 1000,
                    headAnimator!!.AnimLength(headAnim) % 1000,
                    headAnimator!!.NumFrames(headAnim)
                )
            }

            // reset the anim
            mode = -1
            frame = 1
        }

        fun NextFrame(args: CmdArgs.idCmdArgs?) {
            if (0 == anim || SysCvar.g_testModelAnimate.GetInteger() != 3 && SysCvar.g_testModelAnimate.GetInteger() != 5) {
                return
            }
            frame++
            if (frame > animator.NumFrames(anim)) {
                frame = 1
            }
            Game_local.gameLocal.Printf(
                "^5 Anim: ^7%s\n^5Frame: ^7%d/%d\n\n",
                animator.AnimFullName(anim),
                frame,
                animator.NumFrames(anim)
            )

            // reset the anim
            mode = -1
        }

        fun PrevFrame(args: CmdArgs.idCmdArgs?) {
            if (0 == anim || SysCvar.g_testModelAnimate.GetInteger() != 3 && SysCvar.g_testModelAnimate.GetInteger() != 5) {
                return
            }
            frame--
            if (frame < 1) {
                frame = animator.NumFrames(anim)
            }
            Game_local.gameLocal.Printf(
                "^5 Anim: ^7%s\n^5Frame: ^7%d/%d\n\n",
                animator.AnimFullName(anim),
                frame,
                animator.NumFrames(anim)
            )

            // reset the anim
            mode = -1
        }

        fun TestAnim(args: CmdArgs.idCmdArgs?) {
            val name: String
            val animNum: Int
            if (args!!.Argc() < 2) {
                Game_local.gameLocal.Printf("usage: testanim <animname>\n")
                return
            }
            name = args.Argv(1)
            //if (false){
//	if ( strstr( name, ".ma" ) || strstr( name, ".mb" ) ) {
//		const idMD5Anim	*md5anims[ ANIM_MaxSyncedAnims ];
//		idModelExport exporter;
//		exporter.ExportAnim( name );
//		name.SetFileExtension( MD5_ANIM_EXT );
//		md5anims[ 0 ] = animationLib.GetAnim( name );
//		if ( md5anims[ 0 ] ) {
//			customAnim.SetAnim( animator.ModelDef(), name, name, 1, md5anims );
//			newanim = &customAnim;
//		}
//	} else {
//		animNum = animator.GetAnim( name );
//	}
//        }else{
            animNum = animator.GetAnim(name)
            //    }
            if (0 == animNum) {
                Game_local.gameLocal.Printf("Animation '%s' not found.\n", name)
                return
            }
            anim = animNum
            startTime = Game_local.gameLocal.time
            animTime = animator.AnimLength(anim)
            headAnim = 0
            if (headAnimator != null) {
                headAnimator!!.ClearAllAnims(Game_local.gameLocal.time, 0)
                headAnim = headAnimator!!.GetAnim(animName.toString())
                if (0 == headAnim) {
                    headAnim = headAnimator!!.GetAnim("idle")
                    if (0 == headAnim) {
                        Game_local.gameLocal.Printf("Missing 'idle' anim for head.\n")
                    }
                }
                if (headAnim != 0 && headAnimator!!.AnimLength(headAnim) > animTime) {
                    animTime = headAnimator!!.AnimLength(headAnim)
                }
            }
            animName.set(name)
            Game_local.gameLocal.Printf(
                "anim '%s', %d.%03d seconds, %d frames\n",
                animName.toString(),
                animator.AnimLength(anim) / 1000,
                animator.AnimLength(anim) % 1000,
                animator.NumFrames(anim)
            )

            // reset the anim
            mode = -1
        }

        /* **********************************************************************

         Testmodel console commands

         ***********************************************************************/
        fun BlendAnim(args: CmdArgs.idCmdArgs?) {
            val anim1: Int
            val anim2: Int
            if (args!!.Argc() < 4) {
                Game_local.gameLocal.Printf("usage: testblend <anim1> <anim2> <frames>\n")
                return
            }
            anim1 = Game_local.gameLocal.testmodel!!.animator.GetAnim(args.Argv(1))
            if (0 == anim1) {
                Game_local.gameLocal.Printf("Animation '%s' not found.\n", args.Argv(1))
                return
            }
            anim2 = Game_local.gameLocal.testmodel!!.animator.GetAnim(args.Argv(2))
            if (0 == anim2) {
                Game_local.gameLocal.Printf("Animation '%s' not found.\n", args.Argv(2))
                return
            }
            animName.set(args.Argv(2))
            animator.CycleAnim(Anim.ANIMCHANNEL_ALL, anim1, Game_local.gameLocal.time, 0)
            animator.CycleAnim(
                Anim.ANIMCHANNEL_ALL,
                anim2,
                Game_local.gameLocal.time,
                Anim.FRAME2MS(args.Argv(3).toInt())
            )
            anim = anim2
            headAnim = 0
        }

        override fun Think() {
            val pos = idVec3()
            val axis = idMat3()
            val ang = idAngles()
            var i: Int
            if (thinkFlags and Entity.TH_THINK != 0) {
                if (anim != 0 && Game_local.gameLocal.testmodel == this && mode != SysCvar.g_testModelAnimate.GetInteger()) {
                    StopSound(TempDump.etoi(gameSoundChannel_t.SND_CHANNEL_ANY), false)
                    if (head.GetEntity() != null) {
                        head.GetEntity()!!.StopSound(TempDump.etoi(gameSoundChannel_t.SND_CHANNEL_ANY), false)
                    }
                    when (SysCvar.g_testModelAnimate.GetInteger()) {
                        0 -> {
                            // cycle anim with origin reset
                            if (animator.NumFrames(anim) <= 1) {
                                // single frame animations end immediately, so just cycle it since it's the same result
                                animator.CycleAnim(
                                    Anim.ANIMCHANNEL_ALL,
                                    anim,
                                    Game_local.gameLocal.time,
                                    Anim.FRAME2MS(SysCvar.g_testModelBlend.GetInteger())
                                )
                                if (headAnim != 0) {
                                    headAnimator!!.CycleAnim(
                                        Anim.ANIMCHANNEL_ALL,
                                        headAnim,
                                        Game_local.gameLocal.time,
                                        Anim.FRAME2MS(SysCvar.g_testModelBlend.GetInteger())
                                    )
                                }
                            } else {
                                animator.PlayAnim(
                                    Anim.ANIMCHANNEL_ALL,
                                    anim,
                                    Game_local.gameLocal.time,
                                    Anim.FRAME2MS(SysCvar.g_testModelBlend.GetInteger())
                                )
                                if (headAnim != 0) {
                                    headAnimator!!.PlayAnim(
                                        Anim.ANIMCHANNEL_ALL,
                                        headAnim,
                                        Game_local.gameLocal.time,
                                        Anim.FRAME2MS(SysCvar.g_testModelBlend.GetInteger())
                                    )
                                    if (headAnimator!!.AnimLength(headAnim) > animator.AnimLength(anim)) {
                                        // loop the body anim when the head anim is longer
                                        animator.CurrentAnim(Anim.ANIMCHANNEL_ALL).SetCycleCount(-1)
                                    }
                                }
                            }
                            animator.RemoveOriginOffset(false)
                        }

                        1 -> {
                            // cycle anim with fixed origin
                            animator.CycleAnim(
                                Anim.ANIMCHANNEL_ALL,
                                anim,
                                Game_local.gameLocal.time,
                                Anim.FRAME2MS(SysCvar.g_testModelBlend.GetInteger())
                            )
                            animator.RemoveOriginOffset(true)
                            if (headAnim != 0) {
                                headAnimator!!.CycleAnim(
                                    Anim.ANIMCHANNEL_ALL,
                                    headAnim,
                                    Game_local.gameLocal.time,
                                    Anim.FRAME2MS(SysCvar.g_testModelBlend.GetInteger())
                                )
                            }
                        }

                        2 -> {
                            // cycle anim with continuous origin
                            animator.CycleAnim(
                                Anim.ANIMCHANNEL_ALL,
                                anim,
                                Game_local.gameLocal.time,
                                Anim.FRAME2MS(SysCvar.g_testModelBlend.GetInteger())
                            )
                            animator.RemoveOriginOffset(false)
                            if (headAnim != 0) {
                                headAnimator!!.CycleAnim(
                                    Anim.ANIMCHANNEL_ALL,
                                    headAnim,
                                    Game_local.gameLocal.time,
                                    Anim.FRAME2MS(SysCvar.g_testModelBlend.GetInteger())
                                )
                            }
                        }

                        3 -> {
                            // frame by frame with continuous origin
                            animator.SetFrame(
                                Anim.ANIMCHANNEL_ALL,
                                anim,
                                frame,
                                Game_local.gameLocal.time,
                                Anim.FRAME2MS(SysCvar.g_testModelBlend.GetInteger())
                            )
                            animator.RemoveOriginOffset(false)
                            if (headAnim != 0) {
                                headAnimator!!.SetFrame(
                                    Anim.ANIMCHANNEL_ALL,
                                    headAnim,
                                    frame,
                                    Game_local.gameLocal.time,
                                    Anim.FRAME2MS(SysCvar.g_testModelBlend.GetInteger())
                                )
                            }
                        }

                        4 -> {
                            // play anim once
                            animator.PlayAnim(
                                Anim.ANIMCHANNEL_ALL,
                                anim,
                                Game_local.gameLocal.time,
                                Anim.FRAME2MS(SysCvar.g_testModelBlend.GetInteger())
                            )
                            animator.RemoveOriginOffset(false)
                            if (headAnim != 0) {
                                headAnimator!!.PlayAnim(
                                    Anim.ANIMCHANNEL_ALL,
                                    headAnim,
                                    Game_local.gameLocal.time,
                                    Anim.FRAME2MS(SysCvar.g_testModelBlend.GetInteger())
                                )
                            }
                        }

                        5 -> {
                            // frame by frame with fixed origin
                            animator.SetFrame(
                                Anim.ANIMCHANNEL_ALL,
                                anim,
                                frame,
                                Game_local.gameLocal.time,
                                Anim.FRAME2MS(SysCvar.g_testModelBlend.GetInteger())
                            )
                            animator.RemoveOriginOffset(true)
                            if (headAnim != 0) {
                                headAnimator!!.SetFrame(
                                    Anim.ANIMCHANNEL_ALL,
                                    headAnim,
                                    frame,
                                    Game_local.gameLocal.time,
                                    Anim.FRAME2MS(SysCvar.g_testModelBlend.GetInteger())
                                )
                            }
                        }

                        else -> {
                            if (animator.NumFrames(anim) <= 1) {
                                animator.CycleAnim(
                                    Anim.ANIMCHANNEL_ALL,
                                    anim,
                                    Game_local.gameLocal.time,
                                    Anim.FRAME2MS(SysCvar.g_testModelBlend.GetInteger())
                                )
                                if (headAnim != 0) {
                                    headAnimator!!.CycleAnim(
                                        Anim.ANIMCHANNEL_ALL,
                                        headAnim,
                                        Game_local.gameLocal.time,
                                        Anim.FRAME2MS(SysCvar.g_testModelBlend.GetInteger())
                                    )
                                }
                            } else {
                                animator.PlayAnim(
                                    Anim.ANIMCHANNEL_ALL,
                                    anim,
                                    Game_local.gameLocal.time,
                                    Anim.FRAME2MS(SysCvar.g_testModelBlend.GetInteger())
                                )
                                if (headAnim != 0) {
                                    headAnimator!!.PlayAnim(
                                        Anim.ANIMCHANNEL_ALL,
                                        headAnim,
                                        Game_local.gameLocal.time,
                                        Anim.FRAME2MS(SysCvar.g_testModelBlend.GetInteger())
                                    )
                                    if (headAnimator!!.AnimLength(headAnim) > animator.AnimLength(anim)) {
                                        animator.CurrentAnim(Anim.ANIMCHANNEL_ALL).SetCycleCount(-1)
                                    }
                                }
                            }
                            animator.RemoveOriginOffset(false)
                        }
                    }
                    mode = SysCvar.g_testModelAnimate.GetInteger()
                }
                if (mode == 0 && Game_local.gameLocal.time >= startTime + animTime) {
                    startTime = Game_local.gameLocal.time
                    StopSound(TempDump.etoi(gameSoundChannel_t.SND_CHANNEL_ANY), false)
                    animator.PlayAnim(
                        Anim.ANIMCHANNEL_ALL,
                        anim,
                        Game_local.gameLocal.time,
                        Anim.FRAME2MS(SysCvar.g_testModelBlend.GetInteger())
                    )
                    if (headAnim != 0) {
                        headAnimator!!.PlayAnim(
                            Anim.ANIMCHANNEL_ALL,
                            headAnim,
                            Game_local.gameLocal.time,
                            Anim.FRAME2MS(SysCvar.g_testModelBlend.GetInteger())
                        )
                        if (headAnimator!!.AnimLength(headAnim) > animator.AnimLength(anim)) {
                            // loop the body anim when the head anim is longer
                            animator.CurrentAnim(Anim.ANIMCHANNEL_ALL).SetCycleCount(-1)
                        }
                    }
                }
                if (headAnimator != null) {
                    // copy the animation from the body to the head
                    i = 0
                    while (i < copyJoints.Num()) {
                        if (copyJoints[i].mod == jointModTransform_t.JOINTMOD_WORLD_OVERRIDE) {
                            val mat = head.GetEntity()!!.GetPhysics().GetAxis().Transpose()
                            GetJointWorldTransform(
                                copyJoints[i].from._val,
                                Game_local.gameLocal.time,
                                pos,
                                axis
                            )
                            pos.minusAssign(head.GetEntity()!!.GetPhysics().GetOrigin())
                            headAnimator!!.SetJointPos(
                                copyJoints[i].to._val,
                                copyJoints[i].mod,
                                pos.times(mat)
                            )
                            headAnimator!!.SetJointAxis(
                                copyJoints[i].to._val,
                                copyJoints[i].mod,
                                axis.times(mat)
                            )
                        } else {
                            animator.GetJointLocalTransform(
                                copyJoints[i].from._val,
                                Game_local.gameLocal.time,
                                pos,
                                axis
                            )
                            headAnimator!!.SetJointPos(copyJoints[i].to._val, copyJoints[i].mod, pos)
                            headAnimator!!.SetJointAxis(copyJoints[i].to._val, copyJoints[i].mod, axis)
                        }
                        i++
                    }
                }

                // update rotation
                RunPhysics()
                physicsObj.GetAngles(ang)
                physicsObj.SetAngularExtrapolation(
                    Extrapolate.EXTRAPOLATION_LINEAR or Extrapolate.EXTRAPOLATION_NOSTOP,
                    Game_local.gameLocal.time,
                    0,
                    ang,
                    idAngles(0.0f, SysCvar.g_testModelRotate.GetFloat() * 360.0f / 60.0f, 0.0f),
                    ang_zero
                )
                val clip = physicsObj.GetClipModel()
                if (clip != null && animator.ModelDef() != null) {
                    val neworigin = idVec3()
                    //			idMat3 axis;
                    val   /*jointHandle_t*/joint: Int
                    joint = animator.GetJointHandle("origin")
                    animator.GetJointTransform(joint, Game_local.gameLocal.time, neworigin, axis)
                    neworigin.set(
                        ((neworigin - animator.ModelDef()!!
                            .GetVisualOffset()) * physicsObj.GetAxis()) + GetPhysics().GetOrigin()
                    )
                    clip.Link(Game_local.gameLocal.clip, this, 0, neworigin, clip.GetAxis())
                }
            }
            UpdateAnimation()
            Present()
            if (Game_local.gameLocal.testmodel == this
                && SysCvar.g_showTestModelFrame.GetInteger() != 0 && anim != 0
            ) {
                Game_local.gameLocal.Printf(
                    "^5 Anim: ^7%s  ^5Frame: ^7%d/%d  Time: %.3f\n",
                    animator.AnimFullName(anim),
                    animator.CurrentAnim(Anim.ANIMCHANNEL_ALL).GetFrameNumber(Game_local.gameLocal.time),
                    animator.CurrentAnim(Anim.ANIMCHANNEL_ALL).NumFrames(),
                    MS2SEC(
                        (Game_local.gameLocal.time - animator.CurrentAnim(Anim.ANIMCHANNEL_ALL)
                            .GetStartTime()).toFloat()
                    )
                )
                if (headAnim != 0) {
                    Game_local.gameLocal.Printf(
                        "^5 Head: ^7%s  ^5Frame: ^7%d/%d  Time: %.3f\n\n",
                        headAnimator!!.AnimFullName(headAnim),
                        headAnimator!!.CurrentAnim(Anim.ANIMCHANNEL_ALL).GetFrameNumber(Game_local.gameLocal.time),
                        headAnimator!!.CurrentAnim(Anim.ANIMCHANNEL_ALL).NumFrames(),
                        MS2SEC(
                            (Game_local.gameLocal.time - headAnimator!!.CurrentAnim(Anim.ANIMCHANNEL_ALL)
                                .GetStartTime()).toFloat()
                        )
                    )
                } else {
                    Game_local.gameLocal.Printf("\n\n")
                }
            }
        }

        private fun Event_Footstep() {
            StartSound("snd_footstep", gameSoundChannel_t.SND_CHANNEL_BODY, 0, false, null)
        }

        override fun oSet(oGet: idClass?) {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        override fun getEventCallBack(event: idEventDef): eventCallback_t<*>? {
            return eventCallbacks[event]
        }

        /*
         =================
         idTestModel::KeepTestModel_f

         Makes the current test model permanent, allowing you to place
         multiple test models
         =================
         */
        class KeepTestModel_f private constructor() : cmdFunction_t() {
            override fun run(args: CmdArgs.idCmdArgs?) {
                if (Game_local.gameLocal.testmodel == null) {
                    Game_local.gameLocal.Printf("No active testModel.\n")
                    return
                }
                Game_local.gameLocal.Printf(
                    "modelDef %p kept\n",
                    Game_local.gameLocal.testmodel!!.renderEntity!!.hModel
                )
                Game_local.gameLocal.testmodel = null
            }

            companion object {
                private val instance: cmdFunction_t = KeepTestModel_f()
                fun getInstance(): cmdFunction_t {
                    return instance
                }
            }
        }

        /*
         =================
         idTestModel::TestSkin_f

         Sets a skin on an existing testModel
         =================
         */
        class TestSkin_f private constructor() : cmdFunction_t() {
            override fun run(args: CmdArgs.idCmdArgs?) {
                idVec3()
                val name = idStr()
                val player: idPlayer?
                player = Game_local.gameLocal.GetLocalPlayer()
                if (null == player || !Game_local.gameLocal.CheatsOk()) {
                    return
                }

                // delete the testModel if active
                if (Game_local.gameLocal.testmodel == null) {
                    idLib.common.Printf("No active testModel\n")
                    return
                }
                if (args!!.Argc() < 2) {
                    idLib.common.Printf("removing testSkin.\n")
                    Game_local.gameLocal.testmodel!!.SetSkin(null)
                    return
                }
                name.set(args.Argv(1))
                Game_local.gameLocal.testmodel!!.SetSkin(DeclManager.declManager.FindSkin(name))
            }

            companion object {
                private val instance: cmdFunction_t = TestSkin_f()
                fun getInstance(): cmdFunction_t {
                    return instance
                }
            }
        }

        /*
         =================
         idTestModel::TestShaderParm_f

         Sets a shaderParm on an existing testModel
         =================
         */
        class TestShaderParm_f private constructor() : cmdFunction_t() {
            override fun run(args: CmdArgs.idCmdArgs?) {
                idVec3()
                val player: idPlayer?
                player = Game_local.gameLocal.GetLocalPlayer()
                if (null == player || !Game_local.gameLocal.CheatsOk()) {
                    return
                }

                // delete the testModel if active
                if (Game_local.gameLocal.testmodel == null) {
                    idLib.common.Printf("No active testModel\n")
                    return
                }
                if (args!!.Argc() != 3) {
                    idLib.common.Printf("USAGE: testShaderParm <parmNum> <float | \"time\">\n")
                    return
                }
                val parm = args.Argv(1).toInt()
                if (parm < 0 || parm >= Material.MAX_ENTITY_SHADER_PARMS) {
                    idLib.common.Printf("parmNum %d out of range\n", parm)
                    return
                }
                val value: Float
                value = if (idStr.Icmp(args.Argv(2), "time") == 0) {
                    Game_local.gameLocal.time * -0.001f
                } else {
                    args.Argv(2).toFloat()
                }
                Game_local.gameLocal.testmodel!!.SetShaderParm(parm, value)
            }

            companion object {
                private val instance: cmdFunction_t = TestShaderParm_f()
                fun getInstance(): cmdFunction_t {
                    return instance
                }
            }
        }

        /*
         =================
         idTestModel::TestModel_f

         Creates a static modelDef in front of the current position, which
         can then be moved around
         =================
         */
        class TestModel_f private constructor() : cmdFunction_t() {
            override fun run(args: CmdArgs.idCmdArgs?) {
                val offset = idVec3()
                val name = idStr()
                val player: idPlayer?
                val entityDef: idDict?
                var dict = idDict()
                player = Game_local.gameLocal.GetLocalPlayer()
                if (null == player || !Game_local.gameLocal.CheatsOk()) {
                    return
                }

                // delete the testModel if active
                if (Game_local.gameLocal.testmodel != null) {
//		delete gameLocal.testmodel;
                    Game_local.gameLocal.testmodel = null
                }
                if (args!!.Argc() < 2) {
                    return
                }
                name.set(args.Argv(1))
                entityDef = Game_local.gameLocal.FindEntityDefDict(name.toString(), false)
                if (entityDef != null) {
                    dict = idDict(entityDef)
                } else {
                    if (DeclManager.declManager.FindType(declType_t.DECL_MODELDEF, name, false) != null) {
                        dict.Set("model", name)
                    } else {
                        // allow map models with underscore prefixes to be tested during development
                        // without appending an ase
                        if (name[0] != '_') {
                            name.DefaultFileExtension(".ase")
                        }
                        if (name.toString().contains(".ma") || name.toString().contains(".mb")) {
                            val exporter = idModelExport()
                            exporter.ExportModel(name.toString())
                            name.SetFileExtension(Model.MD5_MESH_EXT)
                        }
                        if (ModelManager.renderModelManager.CheckModel(name.toString()) == null) {
                            Game_local.gameLocal.Printf("Can't register model\n")
                            return
                        }
                        dict.Set("model", name)
                    }
                }
                offset.set(player.GetPhysics().GetOrigin() + player.viewAngles.ToForward() * 100.0f)
                dict.Set("origin", offset.ToString())
                dict.Set("angle", Str.va("%f", player.viewAngles.yaw + 180.0f))
                Game_local.gameLocal.testmodel =
                    Game_local.gameLocal.SpawnEntityType(idTestModel::class.java, dict) as idTestModel
                Game_local.gameLocal.testmodel!!.renderEntity!!.shaderParms[RenderWorld.SHADERPARM_TIMEOFFSET] =
                    -MS2SEC(Game_local.gameLocal.time.toFloat())
            }

            companion object {
                private val instance: cmdFunction_t = TestModel_f()
                fun getInstance(): cmdFunction_t {
                    return instance
                }
            }
        }

        /*
         =====================
         idTestModel::ArgCompletion_TestModel
         =====================
         */
        class ArgCompletion_TestModel private constructor() : CmdSystem.argCompletion_t() {
            override fun run(args: CmdArgs.idCmdArgs?, callback: void_callback<String>) {
                var i: Int
                var num: Int
                num = DeclManager.declManager.GetNumDecls(declType_t.DECL_ENTITYDEF)
                i = 0
                while (i < num) {
                    callback.run(
                        idStr(args!!.Argv(0)).toString() + " " + DeclManager.declManager.DeclByIndex(
                            declType_t.DECL_ENTITYDEF,
                            i,
                            false
                        )!!.GetName()
                    )
                    i++
                }
                num = DeclManager.declManager.GetNumDecls(declType_t.DECL_MODELDEF)
                i = 0
                while (i < num) {
                    callback.run(
                        idStr(args!!.Argv(0)).toString() + " " + DeclManager.declManager.DeclByIndex(
                            declType_t.DECL_MODELDEF,
                            i,
                            false
                        )!!.GetName()
                    )
                    i++
                }
                CmdSystem.cmdSystem.ArgCompletion_FolderExtension(
                    args,
                    callback,
                    "models/",
                    false,
                    ".lwo",
                    ".ase",
                    ".md5mesh",
                    ".ma",
                    ".mb",
                    null
                )
            }

            companion object {
                private val instance: CmdSystem.argCompletion_t = ArgCompletion_TestModel()
                fun getInstance(): CmdSystem.argCompletion_t {
                    return instance
                }
            }
        }

        /*
         =====================
         idTestModel::TestParticleStopTime_f
         =====================
         */
        class TestParticleStopTime_f private constructor() : cmdFunction_t() {
            override fun run(args: CmdArgs.idCmdArgs?) {
                if (Game_local.gameLocal.testmodel == null) {
                    Game_local.gameLocal.Printf("No testModel active.\n")
                    return
                }
                Game_local.gameLocal.testmodel!!.renderEntity!!.shaderParms[RenderWorld.SHADERPARM_PARTICLE_STOPTIME] =
                    MS2SEC(Game_local.gameLocal.time.toFloat())
                Game_local.gameLocal.testmodel!!.UpdateVisuals()
            }

            companion object {
                private val instance: cmdFunction_t = TestParticleStopTime_f()
                fun getInstance(): cmdFunction_t {
                    return instance
                }
            }
        }

        /*
         =====================
         idTestModel::TestAnim_f
         =====================
         */
        class TestAnim_f private constructor() : cmdFunction_t() {
            override fun run(args: CmdArgs.idCmdArgs?) {
                if (Game_local.gameLocal.testmodel == null) {
                    Game_local.gameLocal.Printf("No testModel active.\n")
                    return
                }
                Game_local.gameLocal.testmodel?.TestAnim(args)
            }

            companion object {
                private val instance: cmdFunction_t = TestAnim_f()
                fun getInstance(): cmdFunction_t {
                    return instance
                }
            }
        }

        /*
         =====================
         idTestModel::ArgCompletion_TestAnim
         =====================
         */
        class ArgCompletion_TestAnim private constructor() : CmdSystem.argCompletion_t() {
            override fun run(args: CmdArgs.idCmdArgs?, callback: void_callback<String>) {
                if (Game_local.gameLocal.testmodel != null) {
                    val animator = Game_local.gameLocal.testmodel!!.GetAnimator()
                    for (i in 0 until animator.NumAnims()) {
                        callback.run(Str.va("%s %s", args!!.Argv(0), animator.AnimFullName(i)))
                    }
                }
            }

            companion object {
                private val instance: CmdSystem.argCompletion_t = ArgCompletion_TestAnim()
                fun getInstance(): CmdSystem.argCompletion_t {
                    return instance
                }
            }
        }

        /*
         =====================
         idTestModel::TestBlend_f
         =====================
         */
        class TestBlend_f private constructor() : cmdFunction_t() {
            override fun run(args: CmdArgs.idCmdArgs?) {
                if (Game_local.gameLocal.testmodel == null) {
                    Game_local.gameLocal.Printf("No testModel active.\n")
                    return
                }
                Game_local.gameLocal.testmodel?.BlendAnim(args)
            }

            companion object {
                private val instance: cmdFunction_t = TestBlend_f()
                fun getInstance(): cmdFunction_t {
                    return instance
                }
            }
        }

        /*
         =====================
         idTestModel::TestModelNextAnim_f
         =====================
         */
        class TestModelNextAnim_f private constructor() : cmdFunction_t() {
            override fun run(args: CmdArgs.idCmdArgs?) {
                if (Game_local.gameLocal.testmodel == null) {
                    Game_local.gameLocal.Printf("No testModel active.\n")
                    return
                }
                Game_local.gameLocal.testmodel?.NextAnim(args)
            }

            companion object {
                private val instance: cmdFunction_t = TestModelNextAnim_f()
                fun getInstance(): cmdFunction_t {
                    return instance
                }
            }
        }

        /*
         =====================
         idTestModel::TestModelPrevAnim_f
         =====================
         */
        class TestModelPrevAnim_f private constructor() : cmdFunction_t() {
            override fun run(args: CmdArgs.idCmdArgs?) {
                if (Game_local.gameLocal.testmodel == null) {
                    Game_local.gameLocal.Printf("No testModel active.\n")
                    return
                }
                Game_local.gameLocal.testmodel?.PrevAnim(args)
            }

            companion object {
                private val instance: cmdFunction_t = TestModelPrevAnim_f()
                fun getInstance(): cmdFunction_t {
                    return instance
                }
            }
        }

        /*
         =====================
         idTestModel::TestModelNextFrame_f
         =====================
         */
        class TestModelNextFrame_f private constructor() : cmdFunction_t() {
            override fun run(args: CmdArgs.idCmdArgs?) {
                if (Game_local.gameLocal.testmodel == null) {
                    Game_local.gameLocal.Printf("No testModel active.\n")
                    return
                }
                Game_local.gameLocal.testmodel?.NextFrame(args)
            }

            companion object {
                private val instance: cmdFunction_t = TestModelNextFrame_f()
                fun getInstance(): cmdFunction_t {
                    return instance
                }
            }
        }

        /*
         =====================
         idTestModel::TestModelPrevFrame_f
         =====================
         */
        class TestModelPrevFrame_f private constructor() : cmdFunction_t() {
            override fun run(args: CmdArgs.idCmdArgs?) {
                if (Game_local.gameLocal.testmodel == null) {
                    Game_local.gameLocal.Printf("No testModel active.\n")
                    return
                }
                Game_local.gameLocal.testmodel?.PrevFrame(args)
            }

            companion object {
                private val instance: cmdFunction_t = TestModelPrevFrame_f()
                fun getInstance(): cmdFunction_t {
                    return instance
                }
            }
        }
    }
}
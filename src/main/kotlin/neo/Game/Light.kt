package neo.Game

import neo.Game.GameSys.Class.*
import neo.Game.GameSys.Event.idEventDef
import neo.Game.GameSys.SaveGame.idRestoreGame
import neo.Game.GameSys.SaveGame.idSaveGame
import neo.Game.GameSys.SysCvar
import neo.Game.Game_local.gameSoundChannel_t
import neo.Game.Game_local.idGameLocal
import neo.Game.Physics.Clip.idClipModel
import neo.Game.Script.Script_Thread.idThread
import neo.Renderer.Material
import neo.Renderer.ModelManager
import neo.Renderer.RenderWorld
import neo.Renderer.RenderWorld.renderLight_s
import neo.TempDump
import neo.framework.Common
import neo.framework.DeclManager
import neo.framework.DeclManager.declType_t
import neo.idlib.BitMsg.idBitMsg
import neo.idlib.BitMsg.idBitMsgDelta
import neo.idlib.Dict_h.idDict
import neo.idlib.PackColor
import neo.idlib.Text.Str
import neo.idlib.Text.Str.idStr
import neo.idlib.UnpackColor
import neo.idlib.colorBlack
import neo.idlib.containers.CBool
import neo.idlib.containers.CInt
import neo.idlib.idLib
import neo.idlib.math.*
import neo.idlib.math.Matrix.idMat3

val EV_Light_FadeIn: idEventDef = idEventDef("fadeInLight", "f")
val EV_Light_FadeOut: idEventDef = idEventDef("fadeOutLight", "f")
val EV_Light_GetLightParm: idEventDef = idEventDef("getLightParm", "d", 'f')
val EV_Light_Off: idEventDef = idEventDef("Off", null)
val EV_Light_On: idEventDef = idEventDef("On", null)
val EV_Light_SetLightParm: idEventDef = idEventDef("setLightParm", "df")
val EV_Light_SetLightParms: idEventDef = idEventDef("setLightParms", "ffff")
val EV_Light_SetRadius: idEventDef = idEventDef("setRadius", "f")
val EV_Light_SetRadiusXYZ: idEventDef = idEventDef("setRadiusXYZ", "fff")
val EV_Light_SetShader: idEventDef = idEventDef("setShader", "s")

object Light {

    /*
     ===============================================================================

     Generic light.

     ===============================================================================
     */

    class idLight : idEntity() {
        companion object {
            // enum {
            val EVENT_BECOMEBROKEN: Int = idEntity.EVENT_MAXEVENTS
            val EVENT_MAXEVENTS = EVENT_BECOMEBROKEN + 1

            // public 	CLASS_PROTOTYPE( idLight );
            private val eventCallbacks: MutableMap<idEventDef, eventCallback_t<*>> = HashMap()
            fun getEventCallBacks(): MutableMap<idEventDef, eventCallback_t<*>> {
                return eventCallbacks
            }

            init {
                eventCallbacks.putAll(idEntity.getEventCallBacks())
                eventCallbacks[EV_Light_SetShader] =
                    eventCallback_t1<idLight> { obj: idLight, shadername: idEventArg<*>? ->
                        obj.Event_SetShader(
                            shadername as idEventArg<String>
                        )
                    }
                eventCallbacks[EV_Light_GetLightParm] =
                    eventCallback_t1<idLight> { obj: idLight, _parmnum: idEventArg<*>? ->
                        obj.Event_GetLightParm(_parmnum as idEventArg<Int>)
                    }
                eventCallbacks[EV_Light_SetLightParm] =
                    eventCallback_t2<idLight> { obj: idLight, parmnum: idEventArg<*>?, value: idEventArg<*>? ->
                        obj.Event_SetLightParm(
                            parmnum as idEventArg<Int>,
                            value as idEventArg<Float>
                        )
                    }
                eventCallbacks[EV_Light_SetLightParms] =
                    eventCallback_t4<idLight> { obj: idLight, parm0: idEventArg<*>?,
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
                eventCallbacks[EV_Light_SetRadiusXYZ] =
                    eventCallback_t3<idLight> { obj: idLight, x: idEventArg<*>?, y: idEventArg<*>?, z: idEventArg<*>? ->
                        obj.Event_SetRadiusXYZ(
                            x as idEventArg<Float>,
                            y as idEventArg<Float>,
                            z as idEventArg<Float>
                        )
                    }
                eventCallbacks[EV_Light_SetRadius] =
                    eventCallback_t1<idLight> { obj: idLight, radius: idEventArg<*>? -> obj.Event_SetRadius(radius as idEventArg<Float>) }
                eventCallbacks[EV_Hide] =
                    eventCallback_t0<idLight> { obj: idLight -> obj.Event_Hide() }
                eventCallbacks[EV_Show] =
                    eventCallback_t0<idLight> { obj: idLight -> obj.Event_Show() }
                eventCallbacks[EV_Light_On] =
                    eventCallback_t0<idLight> { obj: idLight -> obj.Event_On() }
                eventCallbacks[EV_Light_Off] =
                    eventCallback_t0<idLight> { obj: idLight -> obj.Event_Off() }
                eventCallbacks[EV_Activate] =
                    eventCallback_t1<idLight> { obj: idLight, activator: idEventArg<*>? ->
                        obj.Event_ToggleOnOff(activator as idEventArg<idEntity>)
                    }
                eventCallbacks[EV_PostSpawn] =
                    eventCallback_t0<idLight> { obj: idLight -> obj.Event_SetSoundHandles() }
                eventCallbacks[EV_Light_FadeOut] =
                    eventCallback_t1<idLight> { obj: idLight, time: idEventArg<*>? -> obj.Event_FadeOut(time as idEventArg<Float>) }
                eventCallbacks[EV_Light_FadeIn] =
                    eventCallback_t1<idLight> { obj: idLight, time: idEventArg<*>? -> obj.Event_FadeIn(time as idEventArg<Float>) }
            }
        }

        private val baseColor: idVec3 = idVec3()
        private var breakOnTrigger: Boolean
        private val brokenModel: idStr
        private var count: Int
        private var currentLevel: Int
        private var fadeEnd: Int
        private val fadeFrom: idVec4 = idVec4()
        private var fadeStart: Int
        private val fadeTo: idVec4 = idVec4()
        private val levels: CInt = CInt()
        private var   /*qhandle_t*/lightDefHandle // handle to renderer light def
                : Int
        private var lightParent: idEntity?
        private val localLightAxis // light axis relative to physics axis
                : idMat3 = idMat3()
        private val localLightOrigin // light origin relative to the physics origin
                : idVec3 = idVec3()
        private val renderLight // light presented to the renderer
                : renderLight_s

        //
        //
        private var soundWasPlaying: Boolean

        // ~idLight();
        private var triggercount: Int
        override fun Spawn() {
            super.Spawn()
            val start_off = CBool(false)
            var needBroken: Boolean
            val demonic_shader = arrayOfNulls<String>(1)

            // do the parsing the same way dmap and the editor do
            GameEdit.gameEdit.ParseSpawnArgsToRenderLight(spawnArgs, renderLight)

            // we need the origin and axis relative to the physics origin/axis
            localLightOrigin.set(
                renderLight.origin.minus(GetPhysics().GetOrigin()).times(GetPhysics().GetAxis().Transpose())
            )
            localLightAxis.set(renderLight.axis.times(GetPhysics().GetAxis().Transpose()))

            // set the base color from the shader parms
            baseColor.set(
                renderLight.shaderParms[RenderWorld.SHADERPARM_RED],
                renderLight.shaderParms[RenderWorld.SHADERPARM_GREEN],
                renderLight.shaderParms[RenderWorld.SHADERPARM_BLUE]
            )

            // set the number of light levels
            spawnArgs.GetInt("levels", "1", levels)
            currentLevel = levels._val
            if (levels._val <= 0) {
                idGameLocal.Error("Invalid light level set on entity #%d(%s)", entityNumber, name)
            }

            // make sure the demonic shader is cached
            if (spawnArgs.GetString("mat_demonic", "", demonic_shader)) {
                DeclManager.declManager.FindType(declType_t.DECL_MATERIAL, demonic_shader[0]!!)
            }

            // game specific functionality, not mirrored in
            // editor or dmap light parsing
            // also put the light texture on the model, so light flares
            // can get the current intensity of the light
            renderEntity!!.referenceShader = renderLight.shader
            lightDefHandle = -1 // no static version yet

            // see if an optimized shadow volume exists
            // the renderer will ignore this value after a light has been moved,
            // but there may still be a chance to get it wrong if the game moves
            // a light before the first present, and doesn't clear the prelight
            renderLight.prelightModel = null
            if (name[0].code != 0) {
                // this will return 0 if not found
                renderLight.prelightModel = ModelManager.renderModelManager.CheckModel(Str.va("_prelight_%s", name))
            }
            spawnArgs.GetBool("start_off", "0", start_off)
            if (start_off._val) {
                Off()
            }
            health = spawnArgs.GetInt("health", "0")
            spawnArgs.GetString("broken", "", brokenModel)
            breakOnTrigger = spawnArgs.GetBool("break", "0")
            count = spawnArgs.GetInt("count", "1")
            triggercount = 0
            fadeFrom.set(1.0f, 1.0f, 1.0f, 1.0f)
            fadeTo.set(1.0f, 1.0f, 1.0f, 1.0f)
            fadeStart = 0
            fadeEnd = 0

            // if we have a health make light breakable
            if (health != 0) {
                val model = idStr(spawnArgs.GetString("model")) // get the visual model
                if (0 == model.Length()) {
                    idGameLocal.Error(
                        "Breakable light without a model set on entity #%d(%s)",
                        entityNumber,
                        name
                    )
                }
                fl.takedamage = true

                // see if we need to create a broken model name
                needBroken = true
                if (model.Length() != 0 && brokenModel.Length() == 0) {
                    var pos: Int
                    needBroken = false
                    pos = model.Find(".")
                    if (pos < 0) {
                        pos = model.Length()
                    }
                    if (pos > 0) {
                        model.Left(pos, brokenModel)
                    }
                    brokenModel.plusAssign("_broken")
                    if (pos > 0) {
                        brokenModel.plusAssign(model.substring(pos))
                    }
                }

                // make sure the model gets cached
                if (ModelManager.renderModelManager.CheckModel(brokenModel) == null) {
                    if (needBroken) {
                        idGameLocal.Error(
                            "Model '%s' not found for entity %d(%s)",
                            brokenModel,
                            entityNumber,
                            name
                        )
                    } else {
                        brokenModel.set("")
                    }
                }
                GetPhysics().SetContents(if (spawnArgs.GetBool("nonsolid")) 0 else Material.CONTENTS_SOLID)

                // make sure the collision model gets cached
                idClipModel.CheckModel(brokenModel)
            }
            PostEventMS(EV_PostSpawn, 0)
            UpdateVisuals()
        }

        /*
         ================
         idLight::Save

         archives object for save game file
         ================
         */
        override fun Save(savefile: idSaveGame) {
            savefile.WriteRenderLight(renderLight)
            savefile.WriteBool(renderLight.prelightModel != null)
            savefile.WriteVec3(localLightOrigin)
            savefile.WriteMat3(localLightAxis)
            savefile.WriteString(brokenModel)
            savefile.WriteInt(levels._val)
            savefile.WriteInt(currentLevel)
            savefile.WriteVec3(baseColor)
            savefile.WriteBool(breakOnTrigger)
            savefile.WriteInt(count)
            savefile.WriteInt(triggercount)
            savefile.WriteObject(lightParent)
            savefile.WriteVec4(fadeFrom)
            savefile.WriteVec4(fadeTo)
            savefile.WriteInt(fadeStart)
            savefile.WriteInt(fadeEnd)
            savefile.WriteBool(soundWasPlaying)
        }

        /*
         ================
         idLight::Restore

         unarchives object from save game file
         ================
         */
        override fun Restore(savefile: idRestoreGame) {
            val hadPrelightModel = CBool(false)
            savefile.ReadRenderLight(renderLight)
            savefile.ReadBool(hadPrelightModel)
            renderLight.prelightModel = ModelManager.renderModelManager.CheckModel(Str.va("_prelight_%s", name))
            if (renderLight.prelightModel == null && hadPrelightModel._val) {
                assert(false)
                if (Common.com_developer.GetBool()) {
                    // we really want to know if this happens
                    idGameLocal.Error("idLight::Restore: prelightModel '_prelight_%s' not found", name)
                } else {
                    // but let it slide after release
                    Game_local.gameLocal.Warning("idLight::Restore: prelightModel '_prelight_%s' not found", name)
                }
            }
            savefile.ReadVec3(localLightOrigin)
            savefile.ReadMat3(localLightAxis)
            savefile.ReadString(brokenModel)
            savefile.ReadInt(levels)
            currentLevel = savefile.ReadInt()
            savefile.ReadVec3(baseColor)
            breakOnTrigger = savefile.ReadBool()
            count = savefile.ReadInt()
            triggercount = savefile.ReadInt()
            savefile.ReadObject( /*reinterpret_cast<idClass *&>*/lightParent)
            savefile.ReadVec4(fadeFrom)
            savefile.ReadVec4(fadeTo)
            fadeStart = savefile.ReadInt()
            fadeEnd = savefile.ReadInt()
            soundWasPlaying = savefile.ReadBool()
            lightDefHandle = -1
            SetLightLevel()
        }

        override fun UpdateChangeableSpawnArgs(source: idDict?) {
            super.UpdateChangeableSpawnArgs(source)
            source?.Print()
            FreeSoundEmitter(true)
            GameEdit.gameEdit.ParseSpawnArgsToRefSound(source ?: spawnArgs, refSound)
            if (refSound.shader != null && !refSound.waitfortrigger) {
                StartSoundShader(refSound.shader, gameSoundChannel_t.SND_CHANNEL_ANY.ordinal, 0, false)
            }
            GameEdit.gameEdit.ParseSpawnArgsToRenderLight(source ?: spawnArgs, renderLight)
            UpdateVisuals()
        }

        override fun Think() {
            val color: idVec4 = idVec4()
            if ((thinkFlags and TH_THINK) != 0) {
                if (fadeEnd > 0) {
                    if (Game_local.gameLocal.time < fadeEnd) {
                        color.Lerp(
                            fadeFrom,
                            fadeTo,
                            (Game_local.gameLocal.time - fadeStart).toFloat() / (fadeEnd - fadeStart).toFloat()
                        )
                    } else {
                        color.set(fadeTo)
                        fadeEnd = 0
                        BecomeInactive(TH_THINK)
                    }
                    SetColor(color)
                }
            }
            RunPhysics()
            Present()
        }

        override fun FreeLightDef() {
            if (lightDefHandle != -1) {
                Game_local.gameRenderWorld!!.FreeLightDef(lightDefHandle)
                lightDefHandle = -1
            }
        }

        override fun GetPhysicsToSoundTransform(origin: idVec3, axis: idMat3): Boolean {
            origin.set(localLightOrigin.plus(renderLight.lightCenter))
            axis.set(localLightAxis.times(GetPhysics().GetAxis()))
            return true
        }

        override fun Present() {
            // don't present to the renderer if the entity hasn't changed
            if (0 == thinkFlags and TH_UPDATEVISUALS) {
                return
            }

            // add the model
            super.Present()

            // current transformation
            renderLight.axis.set(localLightAxis * GetPhysics().GetAxis())
            renderLight.origin.set(GetPhysics().GetOrigin() + GetPhysics().GetAxis() * localLightOrigin)

            // reference the sound for shader synced effects
            if (lightParent != null) {
                renderLight.referenceSound = lightParent!!.GetSoundEmitter()
                renderEntity!!.referenceSound = lightParent!!.GetSoundEmitter()
            } else {
                renderLight.referenceSound = refSound.referenceSound
                renderEntity!!.referenceSound = refSound.referenceSound
            }

            // update the renderLight and renderEntity to render the light and flare
            PresentLightDefChange()
            PresentModelDefChange()
        }

        fun SaveState(args: idDict) {
            var i: Int
            val c = spawnArgs.GetNumKeyVals()
            i = 0
            while (i < c) {
                val pv = spawnArgs.GetKeyVal(i)!!
                if (pv.GetKey().Find("editor_", false) >= 0 || pv.GetKey().Find("parse_", false) >= 0) {
                    i++
                    continue
                }
                args.Set(pv.GetKey(), pv.GetValue())
                i++
            }
        }

        override fun SetColor(red: Float, green: Float, blue: Float) {
            baseColor.set(red, green, blue)
            SetLightLevel()
        }

        override fun SetColor(color: idVec4) {
            baseColor.set(color.ToVec3())
            renderLight.shaderParms[RenderWorld.SHADERPARM_ALPHA] = color[3]
            renderEntity!!.shaderParms[RenderWorld.SHADERPARM_ALPHA] = color[3]
            SetLightLevel()
        }

        override fun GetColor(out: idVec3) {
            out[0] = renderLight.shaderParms[RenderWorld.SHADERPARM_RED]
            out[1] = renderLight.shaderParms[RenderWorld.SHADERPARM_GREEN]
            out[2] = renderLight.shaderParms[RenderWorld.SHADERPARM_BLUE]
        }

        override fun GetColor(out: idVec4) {
            out[0] = renderLight.shaderParms[RenderWorld.SHADERPARM_RED]
            out[1] = renderLight.shaderParms[RenderWorld.SHADERPARM_GREEN]
            out[2] = renderLight.shaderParms[RenderWorld.SHADERPARM_BLUE]
            out[3] = renderLight.shaderParms[RenderWorld.SHADERPARM_ALPHA]
        }

        fun GetBaseColor(): idVec3 {
            return baseColor
        }

        fun SetShader(shadername: String) {
            // allow this to be NULL
            renderLight.shader = DeclManager.declManager.FindMaterial(shadername, false)
            PresentLightDefChange()
        }

        fun SetLightParm(parmnum: Int, value: Float) {
            if (parmnum < 0 || parmnum >= Material.MAX_ENTITY_SHADER_PARMS) {
                idGameLocal.Error("shader parm index (%d) out of range", parmnum)
            }
            renderLight.shaderParms[parmnum] = value
            PresentLightDefChange()
        }

        fun SetLightParms(parm0: Float, parm1: Float, parm2: Float, parm3: Float) {
            renderLight.shaderParms[RenderWorld.SHADERPARM_RED] = parm0
            renderLight.shaderParms[RenderWorld.SHADERPARM_GREEN] = parm1
            renderLight.shaderParms[RenderWorld.SHADERPARM_BLUE] = parm2
            renderLight.shaderParms[RenderWorld.SHADERPARM_ALPHA] = parm3
            renderEntity!!.shaderParms[RenderWorld.SHADERPARM_RED] = parm0
            renderEntity!!.shaderParms[RenderWorld.SHADERPARM_GREEN] = parm1
            renderEntity!!.shaderParms[RenderWorld.SHADERPARM_BLUE] = parm2
            renderEntity!!.shaderParms[RenderWorld.SHADERPARM_ALPHA] = parm3
            PresentLightDefChange()
            PresentModelDefChange()
        }

        fun SetRadiusXYZ(x: Float, y: Float, z: Float) {
            renderLight.lightRadius[0] = x
            renderLight.lightRadius[1] = y
            renderLight.lightRadius[2] = z
            PresentLightDefChange()
        }

        fun SetRadius(radius: Float) {
            renderLight.lightRadius[0] = renderLight.lightRadius.set(1, renderLight.lightRadius.set(2, radius))
            PresentLightDefChange()
        }

        fun On() {
            currentLevel = levels._val
            // offset the start time of the shader to sync it to the game time
            renderLight.shaderParms[RenderWorld.SHADERPARM_TIMEOFFSET] =
                -MS2SEC(Game_local.gameLocal.time.toFloat())
            if ((soundWasPlaying || refSound.waitfortrigger) && refSound.shader != null) {
                StartSoundShader(refSound.shader, gameSoundChannel_t.SND_CHANNEL_ANY.ordinal, 0, false)
                soundWasPlaying = false
            }
            SetLightLevel()
            BecomeActive(TH_UPDATEVISUALS)
        }

        fun Off() {
            currentLevel = 0
            // kill any sound it was making
            if (refSound.referenceSound != null && refSound.referenceSound!!.CurrentlyPlaying()) {
                StopSound(TempDump.etoi(gameSoundChannel_t.SND_CHANNEL_ANY), false)
                soundWasPlaying = true
            }
            SetLightLevel()
            BecomeActive(TH_UPDATEVISUALS)
        }

        fun Fade(to: idVec4, fadeTime: Float) {
            GetColor(fadeFrom)
            fadeTo.set(to)
            fadeStart = Game_local.gameLocal.time
            fadeEnd = (Game_local.gameLocal.time + SEC2MS(fadeTime)).toInt()
            BecomeActive(TH_THINK)
        }

        fun FadeOut(time: Float) {
            Fade(colorBlack, time)
        }

        fun FadeIn(time: Float) {
            val color = idVec3()
            val color4 = idVec4()
            currentLevel = levels._val
            spawnArgs.GetVector("_color", "1 1 1", color)
            color4.set(color.x, color.y, color.z, 1.0f)
            Fade(color4, time)
        }

        override fun Killed(inflictor: idEntity?, attacker: idEntity?, damage: Int, dir: idVec3, location: Int) {
            BecomeBroken(attacker)
        }

        fun BecomeBroken(activator: idEntity?) {
            val damageDefName = arrayOfNulls<String>(1)
            fl.takedamage = false
            if (brokenModel.Length() != 0) {
                SetModel(brokenModel.toString())
                if (!spawnArgs.GetBool("nonsolid")) {
                    GetPhysics().SetClipModel(idClipModel(brokenModel.toString()), 1.0f)
                    GetPhysics().SetContents(Material.CONTENTS_SOLID)
                }
            } else if (spawnArgs.GetBool("hideModelOnBreak")) {
                SetModel("")
                GetPhysics().SetContents(0)
            }
            if (Game_local.gameLocal.isServer) {
                ServerSendEvent(EVENT_BECOMEBROKEN, null, true, -1)
                if (spawnArgs.GetString("def_damage", "", damageDefName)) {
                    val origin =
                        idVec3(renderEntity!!.origin.plus(renderEntity!!.bounds.GetCenter().times(renderEntity!!.axis)))
                    Game_local.gameLocal.RadiusDamage(origin, activator, activator, this, this, damageDefName[0]!!)
                }
            }
            ActivateTargets(activator)

            // offset the start time of the shader to sync it to the game time
            renderEntity!!.shaderParms[RenderWorld.SHADERPARM_TIMEOFFSET] =
                -MS2SEC(Game_local.gameLocal.time.toFloat())
            renderLight.shaderParms[RenderWorld.SHADERPARM_TIMEOFFSET] =
                -MS2SEC(Game_local.gameLocal.time.toFloat())

            // set the state parm
            renderEntity!!.shaderParms[RenderWorld.SHADERPARM_MODE] = 1.0f
            renderLight.shaderParms[RenderWorld.SHADERPARM_MODE] = 1.0f

            // if the light has a sound, either start the alternate (broken) sound, or stop the sound
            var parm = spawnArgs.GetString("snd_broken")
            if (refSound.shader != null || parm != null && !parm.isEmpty()) {
                StopSound(TempDump.etoi(gameSoundChannel_t.SND_CHANNEL_ANY), false)
                val alternate =
                    if (refSound.shader != null) refSound.shader!!.GetAltSound() else DeclManager.declManager.FindSound(
                        parm
                    )
                if (alternate != null) {
                    // start it with no diversity, so the leadin break sound plays
                    refSound.referenceSound!!.StartSound(
                        alternate,
                        TempDump.etoi(gameSoundChannel_t.SND_CHANNEL_ANY),
                        0.0f,
                        0
                    )
                }
            }
            parm = spawnArgs.GetString("mtr_broken")
            if (parm != null && !parm.isEmpty()) {
                SetShader(parm)
            }
            UpdateVisuals()
        }

        fun  /*qhandle_t*/GetLightDefHandle(): Int {
            return lightDefHandle
        }

        fun SetLightParent(lparent: idEntity?) {
            lightParent = lparent
        }

        fun SetLightLevel() {
            val color = idVec3()
            val intensity: Float
            intensity = currentLevel.toFloat() / levels._val.toFloat()
            color.set(baseColor.times(intensity))
            renderLight.shaderParms[RenderWorld.SHADERPARM_RED] = color[0]
            renderLight.shaderParms[RenderWorld.SHADERPARM_GREEN] = color[1]
            renderLight.shaderParms[RenderWorld.SHADERPARM_BLUE] = color[2]
            renderEntity!!.shaderParms[RenderWorld.SHADERPARM_RED] = color[0]
            renderEntity!!.shaderParms[RenderWorld.SHADERPARM_GREEN] = color[1]
            renderEntity!!.shaderParms[RenderWorld.SHADERPARM_BLUE] = color[2]
            PresentLightDefChange()
            PresentModelDefChange()
        }

        // };
        override fun ShowEditingDialog() {
            if (SysCvar.g_editEntityMode.GetInteger() == 1) {
                idLib.common.InitTool(Common.EDITOR_LIGHT, spawnArgs)
            } else {
                idLib.common.InitTool(Common.EDITOR_SOUND, spawnArgs)
            }
        }

        override fun ClientPredictionThink() {
            Think()
        }

        override fun WriteToSnapshot(msg: idBitMsgDelta) {
            GetPhysics().WriteToSnapshot(msg)
            WriteBindToSnapshot(msg)
            msg.WriteByte(currentLevel)
            msg.WriteLong(PackColor(baseColor).toInt())
            // msg.WriteBits( lightParent.GetEntityNum(), GENTITYNUM_BITS );

            /*	// only helps prediction
             msg.WriteLong( PackColor( fadeFrom ) );
             msg.WriteLong( PackColor( fadeTo ) );
             msg.WriteLong( fadeStart );
             msg.WriteLong( fadeEnd );
             */
            // FIXME: send renderLight.shader
            msg.WriteFloat(renderLight.lightRadius[0], 5, 10)
            msg.WriteFloat(renderLight.lightRadius[1], 5, 10)
            msg.WriteFloat(renderLight.lightRadius[2], 5, 10)
            msg.WriteLong(
                PackColor(
                    idVec4(
                        renderLight.shaderParms[RenderWorld.SHADERPARM_RED],
                        renderLight.shaderParms[RenderWorld.SHADERPARM_GREEN],
                        renderLight.shaderParms[RenderWorld.SHADERPARM_BLUE],
                        renderLight.shaderParms[RenderWorld.SHADERPARM_ALPHA]
                    )
                ).toInt()
            )
            msg.WriteFloat(renderLight.shaderParms[RenderWorld.SHADERPARM_TIMESCALE], 5, 10)
            msg.WriteLong(renderLight.shaderParms[RenderWorld.SHADERPARM_TIMEOFFSET].toInt())
            //msg.WriteByte( renderLight.shaderParms[SHADERPARM_DIVERSITY] );
            msg.WriteShort(renderLight.shaderParms[RenderWorld.SHADERPARM_MODE].toInt())
            WriteColorToSnapshot(msg)
        }

        override fun ReadFromSnapshot(msg: idBitMsgDelta) {
            val shaderColor = idVec4()
            val oldCurrentLevel = currentLevel
            val oldBaseColor = idVec3(baseColor)
            GetPhysics().ReadFromSnapshot(msg)
            ReadBindFromSnapshot(msg)
            currentLevel = msg.ReadByte()
            if (currentLevel != oldCurrentLevel) {
                // need to call On/Off for flickering lights to start/stop the sound
                // while doing it this way rather than through events, the flickering is out of sync between clients
                // but at least there is no question about saving the event and having them happening globally in the world
                if (currentLevel != 0) {
                    On()
                } else {
                    Off()
                }
            }
            UnpackColor(msg.ReadLong().toLong(), baseColor)
            // lightParentEntityNum = msg.ReadBits( GENTITYNUM_BITS );

            /*	// only helps prediction
             UnpackColor( msg.ReadLong(), fadeFrom );
             UnpackColor( msg.ReadLong(), fadeTo );
             fadeStart = msg.ReadLong();
             fadeEnd = msg.ReadLong();
             */
            // FIXME: read renderLight.shader
            renderLight.lightRadius[0] = msg.ReadFloat(5, 10)
            renderLight.lightRadius[1] = msg.ReadFloat(5, 10)
            renderLight.lightRadius[2] = msg.ReadFloat(5, 10)
            UnpackColor(msg.ReadLong().toLong(), shaderColor)
            renderLight.shaderParms[RenderWorld.SHADERPARM_RED] = shaderColor[0]
            renderLight.shaderParms[RenderWorld.SHADERPARM_GREEN] = shaderColor[1]
            renderLight.shaderParms[RenderWorld.SHADERPARM_BLUE] = shaderColor[2]
            renderLight.shaderParms[RenderWorld.SHADERPARM_ALPHA] = shaderColor[3]
            renderLight.shaderParms[RenderWorld.SHADERPARM_TIMESCALE] = msg.ReadFloat(5, 10)
            renderLight.shaderParms[RenderWorld.SHADERPARM_TIMEOFFSET] = msg.ReadLong().toFloat()
            //renderLight.shaderParms[SHADERPARM_DIVERSITY] = msg.ReadFloat()
            renderLight.shaderParms[RenderWorld.SHADERPARM_MODE] = msg.ReadShort().toFloat()
            ReadColorFromSnapshot(msg)
            if (msg.HasChanged()) {
                if (currentLevel != oldCurrentLevel || baseColor != oldBaseColor) {
                    SetLightLevel()
                } else {
                    PresentLightDefChange()
                    PresentModelDefChange()
                }
            }
        }

        override fun ClientReceiveEvent(event: Int, time: Int, msg: idBitMsg): Boolean {
            return when (event) {
                EVENT_BECOMEBROKEN -> {
                    BecomeBroken(null)
                    true
                }

                else -> super.ClientReceiveEvent(event, time, msg)
            }
            //            return false;
        }

        private fun PresentLightDefChange() {
            // let the renderer apply it to the world
            if (lightDefHandle != -1) {
                Game_local.gameRenderWorld!!.UpdateLightDef(lightDefHandle, renderLight)
            } else {
                lightDefHandle = Game_local.gameRenderWorld!!.AddLightDef(renderLight)
            }
        }

        private fun PresentModelDefChange() {
            if (null == renderEntity!!.hModel || IsHidden()) {
                return
            }

            // add to refresh list
            if (modelDefHandle == -1) {
                modelDefHandle = Game_local.gameRenderWorld!!.AddEntityDef(renderEntity!!)
            } else {
                Game_local.gameRenderWorld!!.UpdateEntityDef(modelDefHandle, renderEntity!!)
            }
        }

        private fun Event_SetShader(shadername: idEventArg<String>) {
            SetShader(shadername.value)
        }

        private fun Event_GetLightParm(_parmnum: idEventArg<Int>) {
            val parmnum: Int = _parmnum.value
            if (parmnum < 0 || parmnum >= Material.MAX_ENTITY_SHADER_PARMS) {
                idGameLocal.Error("shader parm index (%d) out of range", parmnum)
            }
            idThread.ReturnFloat(renderLight.shaderParms[parmnum])
        }

        private fun Event_SetLightParm(parmnum: idEventArg<Int>, value: idEventArg<Float>) {
            SetLightParm(parmnum.value, value.value)
        }

        private fun Event_SetLightParms(
            parm0: idEventArg<Float>,
            parm1: idEventArg<Float>,
            parm2: idEventArg<Float>,
            parm3: idEventArg<Float>
        ) {
            SetLightParms(parm0.value, parm1.value, parm2.value, parm3.value)
        }

        private fun Event_SetRadiusXYZ(x: idEventArg<Float>, y: idEventArg<Float>, z: idEventArg<Float>) {
            SetRadiusXYZ(x.value, y.value, z.value)
        }

        private fun Event_SetRadius(radius: idEventArg<Float>) {
            SetRadius(radius.value)
        }

        private fun Event_Hide() {
            Hide()
            PresentModelDefChange()
            Off()
        }

        private fun Event_Show() {
            Show()
            PresentModelDefChange()
            On()
        }

        private fun Event_On() {
            On()
        }

        private fun Event_Off() {
            Off()
        }

        private fun Event_ToggleOnOff(activator: idEventArg<idEntity>) {
            triggercount++
            if (triggercount < count) {
                return
            }

            // reset trigger count
            triggercount = 0
            if (breakOnTrigger) {
                BecomeBroken(activator.value)
                breakOnTrigger = false
                return
            }
            if (0 == currentLevel) {
                On()
            } else {
                currentLevel--
                if (0 == currentLevel) {
                    Off()
                } else {
                    SetLightLevel()
                }
            }
        }

        /*
         ================
         idLight::Event_SetSoundHandles

         set the same sound def handle on all targeted lights
         ================
         */
        private fun Event_SetSoundHandles() {
            var i: Int
            var targetEnt: idEntity?
            if (refSound.referenceSound == null) {
                return
            }
            i = 0
            while (i < targets.Num()) {
                targetEnt = targets[i].GetEntity()
                if (targetEnt != null && targetEnt is idLight) {
                    val light = targetEnt
                    light.lightParent = this

                    // explicitly delete any sounds on the entity
                    light.FreeSoundEmitter(true)

                    // manually set the refSound to this light's refSound
                    light.renderEntity!!.referenceSound = renderEntity!!.referenceSound

                    // update the renderEntity to the renderer
                    light.UpdateVisuals()
                }
                i++
            }
        }

        private fun Event_FadeOut(time: idEventArg<Float>) {
            FadeOut(time.value)
        }

        private fun Event_FadeIn(time: idEventArg<Float>) {
            FadeIn(time.value)
        }

        override fun CreateInstance(): idClass {
            throw UnsupportedOperationException("Not supported yet.")
        }

        override fun getEventCallBack(event: idEventDef): eventCallback_t<*>? {
            return eventCallbacks[event]
        }

        override fun _deconstructor() {
            if (lightDefHandle != -1) {
                Game_local.gameRenderWorld!!.FreeLightDef(lightDefHandle)
            }
            super._deconstructor()
        }

        init {
            renderLight = renderLight_s()
            localLightOrigin.set(getVec3_zero())
            localLightAxis.set(idMat3.getMat3_identity())
            lightDefHandle = -1
            brokenModel = idStr()
            levels._val = 0
            currentLevel = 0
            baseColor.set(getVec3_zero())
            breakOnTrigger = false
            count = 0
            triggercount = 0
            lightParent = null
            fadeFrom.set(idVec4(1.0f, 1.0f, 1.0f, 1.0f))
            fadeTo.set(idVec4(1.0f, 1.0f, 1.0f, 1.0f))
            fadeStart = 0
            fadeEnd = 0
            soundWasPlaying = false
        }
    }
}
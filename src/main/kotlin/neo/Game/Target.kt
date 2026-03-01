package neo.Game

import neo.Game.GameSys.Class.*
import neo.Game.GameSys.EV_Remove
import neo.Game.GameSys.Event.idEventDef
import neo.Game.GameSys.SaveGame.idRestoreGame
import neo.Game.GameSys.SaveGame.idSaveGame
import neo.Game.GameSys.SysCvar
import neo.Game.Game_local.gameSoundChannel_t
import neo.Game.Game_local.idGameLocal
import neo.Game.Light.idLight
import neo.Game.Misc.idStaticEntity
import neo.Game.Mover.idDoor
import neo.Game.Player.idPlayer
import neo.Game.Script.Script_Program.function_t
import neo.Game.Script.Script_Thread.idThread
import neo.Game.Sound.idSound
import neo.Renderer.Material
import neo.Renderer.Model
import neo.Renderer.ModelManager
import neo.Renderer.RenderWorld
import neo.Sound.snd_shader.idSoundShader
import neo.TempDump
import neo.cm.collisionModelManager
import neo.framework.*
import neo.framework.DeclManager.declType_t
import neo.idlib.Dict_h.idDict
import neo.idlib.Dict_h.idKeyValue
import neo.idlib.Text.Str
import neo.idlib.Text.Str.idStr
import neo.idlib.containers.CFloat
import neo.idlib.containers.CInt
import neo.idlib.containers.List.idList
import neo.idlib.math.*
import neo.idlib.math.Interpolate.idInterpolate
import neo.ui.UserInterface
import kotlin.experimental.and

val EV_ClearFlash: idEventDef = idEventDef("<ClearFlash>", "f")
val EV_Flash: idEventDef = idEventDef("<Flash>", "fd")
val EV_GatherEntities: idEventDef = idEventDef("<GatherEntities>")
val EV_RestoreInfluence: idEventDef = idEventDef("<RestoreInfluece>")
val EV_RestoreVolume: idEventDef = idEventDef("<RestoreVolume>")
val EV_TipOff: idEventDef = idEventDef("<TipOff>")


object Target {

    /*
     ===============================================================================

     idTarget

     ===============================================================================
     */
    open class idTarget : idEntity() {
        override fun CreateInstance(): idClass {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }
    }

    /*
     ===============================================================================

     idTarget_Remove

     ===============================================================================
     */
    class idTarget_Remove : idTarget() {
        companion object {
            private val eventCallbacks: MutableMap<idEventDef, eventCallback_t<*>> = HashMap()
            fun getEventCallBacks(): MutableMap<idEventDef, eventCallback_t<*>> {
                return eventCallbacks
            }

            init {
                eventCallbacks.putAll(idEntity.getEventCallBacks())
                eventCallbacks[EV_Activate] =
                    eventCallback_t1<idTarget_Remove> { obj: idTarget_Remove, activator: idEventArg<*>? ->
                        obj.Event_Activate(activator as idEventArg<idEntity>)
                    }
            }
        }

        private fun Event_Activate(activator: idEventArg<idEntity>) {
            var i: Int
            var ent: idEntity?
            i = 0
            while (i < targets.Num()) {
                ent = targets[i].GetEntity()
                ent?.PostEventMS(EV_Remove, 0)
                i++
            }

            // delete our self when done
            PostEventMS(EV_Remove, 0)
        }

        override fun getEventCallBack(event: idEventDef): eventCallback_t<*>? {
            return eventCallbacks[event]
        }
    }

    /*
     ===============================================================================

     idTarget_Show

     ===============================================================================
     */
    class idTarget_Show : idTarget() {
        companion object {
            // CLASS_PROTOTYPE( idTarget_Show );
            private val eventCallbacks: MutableMap<idEventDef, eventCallback_t<*>> = HashMap()
            fun getEventCallBacks(): MutableMap<idEventDef, eventCallback_t<*>> {
                return eventCallbacks
            }

            init {
                eventCallbacks.putAll(idEntity.getEventCallBacks())
                eventCallbacks[EV_Activate] =
                    eventCallback_t1<idTarget_Show> { obj: idTarget_Show, activator: idEventArg<*>? ->
                        obj.Event_Activate(activator as idEventArg<idEntity>)
                    }
            }
        }

        private fun Event_Activate(activator: idEventArg<idEntity>) {
            var i: Int
            var ent: idEntity?
            i = 0
            while (i < targets.Num()) {
                ent = targets[i].GetEntity()
                ent?.Show()
                i++
            }

            // delete our self when done
            PostEventMS(EV_Remove, 0)
        }

        override fun getEventCallBack(event: idEventDef): eventCallback_t<*>? {
            return eventCallbacks[event]
        }
    }

    /*
     ===============================================================================

     idTarget_Damage

     ===============================================================================
     */
    class idTarget_Damage : idTarget() {
        companion object {
            // CLASS_PROTOTYPE( idTarget_Damage );
            private val eventCallbacks: MutableMap<idEventDef, eventCallback_t<*>> = HashMap()
            fun getEventCallBacks(): MutableMap<idEventDef, eventCallback_t<*>> {
                return eventCallbacks
            }

            init {
                eventCallbacks.putAll(idEntity.getEventCallBacks())
                eventCallbacks[EV_Activate] =
                    eventCallback_t1<idTarget_Damage> { obj: idTarget_Damage, activator: idEventArg<*>? ->
                        obj.Event_Activate(activator as idEventArg<idEntity>)
                    }
            }
        }

        private fun Event_Activate(activator: idEventArg<idEntity>) {
            var i: Int
            val damage: String?
            var ent: idEntity?
            damage = spawnArgs.GetString("def_damage", "damage_generic")!!
            i = 0
            while (i < targets.Num()) {
                ent = targets[i].GetEntity()
                ent?.Damage(this, this, getVec3Origin(), damage, 1.0f, Model.INVALID_JOINT)
                i++
            }
        }

        override fun getEventCallBack(event: idEventDef): eventCallback_t<*>? {
            return eventCallbacks[event]
        }
    }

    /*
     ===============================================================================

     idTarget_SessionCommand

     ===============================================================================
     */
    class idTarget_SessionCommand : idTarget() {
        companion object {
            //	CLASS_PROTOTYPE(idTarget_SessionCommand );
            private val eventCallbacks: MutableMap<idEventDef, eventCallback_t<*>> = HashMap()
            fun getEventCallBacks(): MutableMap<idEventDef, eventCallback_t<*>> {
                return eventCallbacks
            }

            init {
                eventCallbacks.putAll(idEntity.getEventCallBacks())
                eventCallbacks[EV_Activate] =
                    eventCallback_t1<idTarget_SessionCommand> { obj: idTarget_SessionCommand, activator: idEventArg<*>? ->
                        obj.Event_Activate(activator as idEventArg<idEntity>)
                    }
            }
        }

        private fun Event_Activate(activator: idEventArg<idEntity>) {
            Game_local.gameLocal.sessionCommand.set(spawnArgs.GetString("command"))
        }

        override fun getEventCallBack(event: idEventDef): eventCallback_t<*>? {
            return eventCallbacks[event]
        }
    }

    /*
     ===============================================================================

     idTarget_EndLevel

     Just a modified form of idTarget_SessionCommand
     ===============================================================================
     */
    class idTarget_EndLevel : idTarget() {
        companion object {
            // CLASS_PROTOTYPE( idTarget_EndLevel );
            private val eventCallbacks: MutableMap<idEventDef, eventCallback_t<*>> = HashMap()
            fun getEventCallBacks(): MutableMap<idEventDef, eventCallback_t<*>> {
                return eventCallbacks
            }

            init {
                eventCallbacks.putAll(idEntity.getEventCallBacks())
                eventCallbacks[EV_Activate] =
                    eventCallback_t1<idTarget_EndLevel> { obj: idTarget_EndLevel, activator: idEventArg<*>? ->
                        obj.Event_Activate(activator as idEventArg<idEntity>)
                    }
            }
        }

        private fun Event_Activate(activator: idEventArg<idEntity>) {
            val nextMap = arrayOfNulls<String>(1)
            if (spawnArgs.GetBool("endOfGame")) {
                CVarSystem.cvarSystem.SetCVarBool("g_nightmare", true)
                Game_local.gameLocal.sessionCommand.set("disconnect")
                return
            }
            if (!spawnArgs.GetString("nextMap", "", nextMap)) {
                Game_local.gameLocal.Printf("idTarget_SessionCommand::Event_Activate: no nextMap key\n")
                return
            }
            if (spawnArgs.GetInt("devmap", "0") != 0) {
                Game_local.gameLocal.sessionCommand.set("devmap ") // only for special demos
            } else {
                Game_local.gameLocal.sessionCommand.set("map ")
            }
            Game_local.gameLocal.sessionCommand.plusAssign(nextMap[0]!!)
        }

        override fun getEventCallBack(event: idEventDef): eventCallback_t<*>? {
            return eventCallbacks[event]
        }
    }

    /*
     ===============================================================================

     idTarget_WaitForButton

     ===============================================================================
     */
    class idTarget_WaitForButton : idTarget() {
        companion object {
            // CLASS_PROTOTYPE( idTarget_WaitForButton );
            private val eventCallbacks: MutableMap<idEventDef, eventCallback_t<*>> = HashMap()
            fun getEventCallBacks(): MutableMap<idEventDef, eventCallback_t<*>> {
                return eventCallbacks
            }

            init {
                eventCallbacks.putAll(idEntity.getEventCallBacks())
                eventCallbacks[EV_Activate] =
                    eventCallback_t1<idTarget_WaitForButton> { obj: idTarget_WaitForButton, activator: idEventArg<*>? ->
                        obj.Event_Activate(activator as idEventArg<idEntity>)
                    }
            }
        }

        override fun Think() {
            val player: idPlayer?
            if ((thinkFlags and TH_THINK) != 0) {
                player = Game_local.gameLocal.GetLocalPlayer()
                if (player != null && (player.oldButtons.inv() and UsercmdGen.BUTTON_ATTACK) != 0 && (player.usercmd.buttons.toInt() and UsercmdGen.BUTTON_ATTACK) != 0) {
                    player.usercmd.buttons = player.usercmd.buttons and UsercmdGen.BUTTON_ATTACK.inv().toByte()
                    BecomeInactive(TH_THINK)
                    ActivateTargets(player)
                }
            } else {
                BecomeInactive(TH_ALL)
            }
        }

        private fun Event_Activate(activator: idEventArg<idEntity>) {
            if ((thinkFlags and TH_THINK) != 0) {
                BecomeInactive(TH_THINK)
            } else {
                // always allow during cinematics
                cinematic = true
                BecomeActive(TH_THINK)
            }
        }

        override fun getEventCallBack(event: idEventDef): eventCallback_t<*>? {
            return eventCallbacks[event]
        }
    }

    /*
     ===============================================================================

     idTarget_SetGlobalShaderTime

     ===============================================================================
     */
    class idTarget_SetGlobalShaderTime : idTarget() {
        companion object {
            // CLASS_PROTOTYPE( idTarget_SetGlobalShaderTime );
            private val eventCallbacks: MutableMap<idEventDef, eventCallback_t<*>> = HashMap()
            fun getEventCallBacks(): MutableMap<idEventDef, eventCallback_t<*>> {
                return eventCallbacks
            }

            init {
                eventCallbacks.putAll(idEntity.getEventCallBacks())
                eventCallbacks[EV_Activate] =
                    eventCallback_t1<idTarget_SetGlobalShaderTime> { obj: idTarget_SetGlobalShaderTime, activator: idEventArg<*>? ->
                        obj.Event_Activate(activator as idEventArg<idEntity?>)
                    }
            }
        }

        private fun Event_Activate(activator: idEventArg<idEntity?>?) {
            val parm = spawnArgs.GetInt("globalParm")
            val time = -MS2SEC(Game_local.gameLocal.time.toFloat())
            if (parm >= 0 && parm < RenderWorld.MAX_GLOBAL_SHADER_PARMS) {
                Game_local.gameLocal.globalShaderParms[parm] = time
            }
        }

        override fun getEventCallBack(event: idEventDef): eventCallback_t<*>? {
            return eventCallbacks[event]
        }
    }

    /*
     ===============================================================================

     idTarget_SetShaderParm

     ===============================================================================
     */
    class idTarget_SetShaderParm : idTarget() {
        companion object {
            // CLASS_PROTOTYPE( idTarget_SetShaderParm );
            private val eventCallbacks: MutableMap<idEventDef, eventCallback_t<*>> = HashMap()
            fun getEventCallBacks(): MutableMap<idEventDef, eventCallback_t<*>> {
                return eventCallbacks
            }

            init {
                eventCallbacks.putAll(idEntity.getEventCallBacks())
                eventCallbacks[EV_Activate] =
                    eventCallback_t1<idTarget_SetShaderParm> { obj: idTarget_SetShaderParm, activator: idEventArg<*>? ->
                        obj.Event_Activate(activator as idEventArg<idEntity>)
                    }
            }
        }

        private fun Event_Activate(activator: idEventArg<idEntity>) {
            var i: Int
            var ent: idEntity?
            val value = CFloat()
            val color = idVec3()
            var parmnum: Int

            // set the color on the targets
            if (spawnArgs.GetVector("_color", "1 1 1", color)) {
                i = 0
                while (i < targets.Num()) {
                    ent = targets[i].GetEntity()
                    ent?.SetColor(color[0], color[1], color[2])
                    i++
                }
            }

            // set any shader parms on the targets
            parmnum = 0
            while (parmnum < Material.MAX_ENTITY_SHADER_PARMS) {
                if (spawnArgs.GetFloat(Str.va("shaderParm%d", parmnum), "0", value)) {
                    i = 0
                    while (i < targets.Num()) {
                        ent = targets[i].GetEntity()
                        ent?.SetShaderParm(parmnum, value._val)
                        i++
                    }
                    if (spawnArgs.GetBool("toggle") && (value._val == 0.0f || value._val == 1.0f)) {
                        var `val` = value._val.toInt()
                        `val` = `val` xor 1
                        value._val = `val`.toFloat()
                        spawnArgs.SetFloat(Str.va("shaderParm%d", parmnum), value._val)
                    }
                }
                parmnum++
            }
        }

        override fun getEventCallBack(event: idEventDef): eventCallback_t<*>? {
            return eventCallbacks[event]
        }
    }

    /*
     ===============================================================================

     idTarget_SetShaderTime

     ===============================================================================
     */
    class idTarget_SetShaderTime : idTarget() {
        companion object {
            // CLASS_PROTOTYPE( idTarget_SetShaderTime );
            private val eventCallbacks: MutableMap<idEventDef, eventCallback_t<*>> = HashMap()
            fun getEventCallBacks(): MutableMap<idEventDef, eventCallback_t<*>> {
                return eventCallbacks
            }

            init {
                eventCallbacks.putAll(idEntity.getEventCallBacks())
                eventCallbacks[EV_Activate] =
                    eventCallback_t1<idTarget_SetShaderTime> { obj: idTarget_SetShaderTime, activator: idEventArg<*>? ->
                        obj.Event_Activate(activator as idEventArg<idEntity>)
                    }
            }
        }

        private fun Event_Activate(activator: idEventArg<idEntity>) {
            var i: Int
            var ent: idEntity?
            val time: Float
            time = -MS2SEC(Game_local.gameLocal.time.toFloat())
            i = 0
            while (i < targets.Num()) {
                ent = targets[i].GetEntity()
                if (ent != null) {
                    ent.SetShaderParm(RenderWorld.SHADERPARM_TIMEOFFSET, time)
                    if (ent is idLight) {
                        ent.SetLightParm(RenderWorld.SHADERPARM_TIMEOFFSET, time)
                    }
                }
                i++
            }
        }

        override fun getEventCallBack(event: idEventDef): eventCallback_t<*>? {
            return eventCallbacks[event]
        }
    }

    /*
     ===============================================================================

     idTarget_FadeEntity

     ===============================================================================
     */
    class idTarget_FadeEntity : idTarget() {
        companion object {
            // CLASS_PROTOTYPE( idTarget_FadeEntity );
            private val eventCallbacks: MutableMap<idEventDef, eventCallback_t<*>> = HashMap()
            fun getEventCallBacks(): MutableMap<idEventDef, eventCallback_t<*>> {
                return eventCallbacks
            }

            init {
                eventCallbacks.putAll(idEntity.getEventCallBacks())
                eventCallbacks[EV_Activate] =
                    eventCallback_t1<idTarget_FadeEntity> { obj: idTarget_FadeEntity, activator: idEventArg<*>? ->
                        obj.Event_Activate(activator as idEventArg<idEntity>)
                    }
            }
        }

        private var fadeEnd: Int
        private val fadeFrom: idVec4
        private var fadeStart: Int
        override fun Save(savefile: idSaveGame) {
            savefile.WriteVec4(fadeFrom)
            savefile.WriteInt(fadeStart)
            savefile.WriteInt(fadeEnd)
        }

        override fun Restore(savefile: idRestoreGame) {
            savefile.ReadVec4(fadeFrom)
            fadeStart = savefile.ReadInt()
            fadeEnd = savefile.ReadInt()
        }

        override fun Think() {
            var i: Int
            var ent: idEntity?
            val color: idVec4 = idVec4()
            val fadeTo = idVec4()
            val frac: Float
            if ((thinkFlags and TH_THINK) != 0) {
                GetColor(fadeTo)
                if (Game_local.gameLocal.time >= fadeEnd) {
                    color.set(fadeTo)
                    BecomeInactive(TH_THINK)
                } else {
                    frac = ((Game_local.gameLocal.time - fadeStart) / (fadeEnd - fadeStart)).toFloat()
                    color.Lerp(fadeFrom, fadeTo, frac)
                }

                // set the color on the targets
                i = 0
                while (i < targets.Num()) {
                    ent = targets[i].GetEntity()
                    ent?.SetColor(color)
                    i++
                }
            } else {
                BecomeInactive(TH_ALL)
            }
        }

        private fun Event_Activate(activator: idEventArg<idEntity>) {
            var ent: idEntity?
            var i: Int
            if (0 == targets.Num()) {
                return
            }

            // always allow during cinematics
            cinematic = true
            BecomeActive(TH_THINK)

//	ent = this;
            i = 0
            while (i < targets.Num()) {
                ent = targets[i].GetEntity()
                if (ent != null) {
                    ent.GetColor(fadeFrom)
                    break
                }
                i++
            }
            fadeStart = Game_local.gameLocal.time
            fadeEnd = (Game_local.gameLocal.time + SEC2MS(spawnArgs.GetFloat("fadetime")))
        }

        override fun getEventCallBack(event: idEventDef): eventCallback_t<*>? {
            return eventCallbacks[event]
        }

        //
        //
        init {
            fadeFrom = idVec4()
            fadeStart = 0
            fadeEnd = 0
        }
    }

    /*
     ===============================================================================

     idTarget_LightFadeIn

     ===============================================================================
     */
    class idTarget_LightFadeIn : idTarget() {
        companion object {
            // CLASS_PROTOTYPE( idTarget_LightFadeIn );
            private val eventCallbacks: MutableMap<idEventDef, eventCallback_t<*>> = HashMap()
            fun getEventCallBacks(): MutableMap<idEventDef, eventCallback_t<*>> {
                return eventCallbacks
            }

            init {
                eventCallbacks.putAll(idEntity.getEventCallBacks())
                eventCallbacks[EV_Activate] =
                    eventCallback_t1<idTarget_LightFadeIn> { obj: idTarget_LightFadeIn, activator: idEventArg<*>? ->
                        obj.Event_Activate(activator as idEventArg<idEntity>)
                    }
            }
        }

        private fun Event_Activate(activator: idEventArg<idEntity>) {
            var ent: idEntity?
            var light: idLight?
            var i: Int
            val time: Float
            if (0 == targets.Num()) {
                return
            }
            time = spawnArgs.GetFloat("fadetime")
            //	ent = this;
            i = 0
            while (i < targets.Num()) {
                ent = targets[i].GetEntity()
                if (null == ent) {
                    i++
                    continue
                }
                if (ent is idLight) {
                    light = ent
                    light.FadeIn(time)
                } else {
                    Game_local.gameLocal.Printf("'%s' targets non-light '%s'", name, ent.GetName())
                }
                i++
            }
        }

        override fun getEventCallBack(event: idEventDef): eventCallback_t<*>? {
            return eventCallbacks[event]
        }
    }

    /*
     ===============================================================================

     idTarget_LightFadeOut

     ===============================================================================
     */
    class idTarget_LightFadeOut : idTarget() {
        companion object {
            // CLASS_PROTOTYPE( idTarget_LightFadeOut );
            private val eventCallbacks: MutableMap<idEventDef, eventCallback_t<*>> = HashMap()
            fun getEventCallBacks(): MutableMap<idEventDef, eventCallback_t<*>> {
                return eventCallbacks
            }

            init {
                eventCallbacks.putAll(idEntity.getEventCallBacks())
                eventCallbacks[EV_Activate] =
                    eventCallback_t1<idTarget_LightFadeOut> { obj: idTarget_LightFadeOut, activator: idEventArg<*>? ->
                        obj.Event_Activate(activator as idEventArg<idEntity>)
                    }
            }
        }

        private fun Event_Activate(activator: idEventArg<idEntity>) {
            var ent: idEntity?
            var light: idLight?
            var i: Int
            val time: Float
            if (0 == targets.Num()) {
                return
            }
            time = spawnArgs.GetFloat("fadetime")
            //	ent = this;
            i = 0
            while (i < targets.Num()) {
                ent = targets[i].GetEntity()
                if (null == ent) {
                    i++
                    continue
                }
                if (ent is idLight) {
                    light = ent
                    light.FadeOut(time)
                } else {
                    Game_local.gameLocal.Printf("'%s' targets non-light '%s'", name, ent.GetName())
                }
                i++
            }
        }

        override fun getEventCallBack(event: idEventDef): eventCallback_t<*>? {
            return eventCallbacks[event]
        }
    }

    /*
     ===============================================================================

     idTarget_Give

     ===============================================================================
     */
    class idTarget_Give : idTarget() {
        companion object {
            // CLASS_PROTOTYPE( idTarget_Give );
            private val eventCallbacks: MutableMap<idEventDef, eventCallback_t<*>> = HashMap()
            private var giveNum = 0
            fun getEventCallBacks(): MutableMap<idEventDef, eventCallback_t<*>> {
                return eventCallbacks
            }

            init {
                eventCallbacks.putAll(idEntity.getEventCallBacks())
                eventCallbacks[EV_Activate] =
                    eventCallback_t1<idTarget_Give> { obj: idTarget_Give, activator: idEventArg<*>? ->
                        obj.Event_Activate(activator as idEventArg<idEntity?>)
                    }
            }
        }

        override fun Spawn() {
            super.Spawn()
            if (spawnArgs.GetBool("onSpawn")) {
                PostEventMS(EV_Activate, 50)
            }
        }

        private fun Event_Activate(activator: idEventArg<idEntity?>?) {
            if (spawnArgs.GetBool("development") && Common.com_developer.GetInteger() == 0) {
                return
            }
            val player = Game_local.gameLocal.GetLocalPlayer()
            if (player != null) {
                var kv = spawnArgs.MatchPrefix("item", null)
                while (kv != null) {
                    val dict = Game_local.gameLocal.FindEntityDefDict(kv.GetValue().toString(), false)
                    if (dict != null) {
                        val d2 = idDict()
                        d2.Copy(dict)
                        d2.Set("name", Str.va("givenitem_%d", giveNum++))
                        val ent = arrayOfNulls<idEntity>(1)
                        if (Game_local.gameLocal.SpawnEntityDef(d2, ent) && ent.isNotEmpty() && ent[0] is idItem) {
                            val item = ent[0] as idItem
                            item.GiveToPlayer(Game_local.gameLocal.GetLocalPlayer())
                        }
                    }
                    kv = spawnArgs.MatchPrefix("item", kv)
                }
            }
        }

        override fun getEventCallBack(event: idEventDef): eventCallback_t<*>? {
            return eventCallbacks[event]
        }
    }

    /*
     ===============================================================================

     idTarget_GiveEmail

     ===============================================================================
     */
    class idTarget_GiveEmail : idTarget() {
        companion object {
            // CLASS_PROTOTYPE( idTarget_GiveEmail );
            private val eventCallbacks: MutableMap<idEventDef, eventCallback_t<*>> = HashMap()
            fun getEventCallBacks(): MutableMap<idEventDef, eventCallback_t<*>> {
                return eventCallbacks
            }

            init {
                eventCallbacks.putAll(idEntity.getEventCallBacks())
                eventCallbacks[EV_Activate] =
                    eventCallback_t1<idTarget_GiveEmail> { obj: idTarget_GiveEmail, activator: idEventArg<*>? ->
                        obj.Event_Activate(activator as idEventArg<idEntity>)
                    }
            }
        }

        private fun Event_Activate(activator: idEventArg<idEntity>) {
            val player = Game_local.gameLocal.GetLocalPlayer()!!
            val pda = player.GetPDA()
            if (pda != null) {
                player.GiveEmail(spawnArgs.GetString("email"))
            } else {
                player.ShowTip(spawnArgs.GetString("text_infoTitle"), spawnArgs.GetString("text_PDANeeded"), true)
            }
        }

        override fun getEventCallBack(event: idEventDef): eventCallback_t<*>? {
            return eventCallbacks[event]
        }
    }

    /*
     ===============================================================================

     idTarget_SetModel

     ===============================================================================
     */
    class idTarget_SetModel : idTarget() {
        companion object {
            // CLASS_PROTOTYPE( idTarget_SetModel );
            private val eventCallbacks: MutableMap<idEventDef, eventCallback_t<*>> = HashMap()
            fun getEventCallBacks(): MutableMap<idEventDef, eventCallback_t<*>> {
                return eventCallbacks
            }

            init {
                eventCallbacks.putAll(idEntity.getEventCallBacks())
                eventCallbacks[EV_Activate] =
                    eventCallback_t1<idTarget_SetModel> { obj: idTarget_SetModel, activator: idEventArg<*>? ->
                        obj.Event_Activate(activator as idEventArg<idEntity>)
                    }
            }
        }

        override fun Spawn() {
            super.Spawn()
            val model: idStr
            model = idStr(spawnArgs.GetString("newmodel"))
            if (DeclManager.declManager.FindType(declType_t.DECL_MODELDEF, model, false) == null) {
                // precache the render model
                ModelManager.renderModelManager.FindModel(model)
                // precache .cm files only
                collisionModelManager.LoadModel(model, true)
            }
        }

        private fun Event_Activate(activator: idEventArg<idEntity>) {
            for (i in 0 until targets.Num()) {
                val ent = targets[i].GetEntity()
                ent?.SetModel(spawnArgs.GetString("newmodel"))
            }
        }

        override fun getEventCallBack(event: idEventDef): eventCallback_t<*>? {
            return eventCallbacks[event]
        }
    }

    /*
     ===============================================================================

     idTarget_SetInfluence

     ===============================================================================
     */
    class idTarget_SetInfluence : idTarget() {
        companion object {
            // CLASS_PROTOTYPE( idTarget_SetInfluence );
            private val eventCallbacks: MutableMap<idEventDef, eventCallback_t<*>> = HashMap()
            fun getEventCallBacks(): MutableMap<idEventDef, eventCallback_t<*>> {
                return eventCallbacks
            }

            init {
                eventCallbacks.putAll(idEntity.getEventCallBacks())
                eventCallbacks[EV_Activate] =
                    eventCallback_t1<idTarget_SetInfluence> { obj: idTarget_SetInfluence, activator: idEventArg<*>? ->
                        obj.Event_Activate(activator as idEventArg<idEntity>)
                    }
                eventCallbacks[EV_RestoreInfluence] =
                    eventCallback_t0<idTarget_SetInfluence> { obj: idTarget_SetInfluence -> obj.Event_RestoreInfluence() }
                eventCallbacks[EV_GatherEntities] =
                    eventCallback_t0<idTarget_SetInfluence> { obj: idTarget_SetInfluence -> obj.Event_GatherEntities() }
                eventCallbacks[EV_Flash] =
                    eventCallback_t2<idTarget_SetInfluence> { obj: idTarget_SetInfluence, _flash: idEventArg<*>?, _out: idEventArg<*>? ->
                        obj.Event_Flash(_flash as idEventArg<Float>, _out as idEventArg<Int>)
                    }
                eventCallbacks[EV_ClearFlash] =
                    eventCallback_t1<idTarget_SetInfluence> { obj: idTarget_SetInfluence, flash: idEventArg<*>? ->
                        obj.Event_ClearFlash(flash as idEventArg<Float>)
                    }
            }
        }

        private var delay: Float
        private var flashIn: Float
        private lateinit var flashInSound: idStr
        private var flashOut: Float
        private lateinit var flashOutSound: idStr
        private val fovSetting: idInterpolate<Float> = idInterpolate()
        private val genericList: idList<Int>
        private val guiList: idList<Int>
        private val lightList: idList<Int>
        private var restoreOnTrigger: Boolean
        private var soundFaded: Boolean
        private val soundList: idList<Int>
        private var switchToCamera: idEntity?
        override fun Save(savefile: idSaveGame) {
            var i: Int
            savefile.WriteInt(lightList.Num())
            i = 0
            while (i < lightList.Num()) {
                savefile.WriteInt(lightList[i])
                i++
            }
            savefile.WriteInt(guiList.Num())
            i = 0
            while (i < guiList.Num()) {
                savefile.WriteInt(guiList[i])
                i++
            }
            savefile.WriteInt(soundList.Num())
            i = 0
            while (i < soundList.Num()) {
                savefile.WriteInt(soundList[i])
                i++
            }
            savefile.WriteInt(genericList.Num())
            i = 0
            while (i < genericList.Num()) {
                savefile.WriteInt(genericList[i])
                i++
            }
            savefile.WriteFloat(flashIn)
            savefile.WriteFloat(flashOut)
            savefile.WriteFloat(delay)
            savefile.WriteString(flashInSound)
            savefile.WriteString(flashOutSound)
            savefile.WriteObject(switchToCamera)
            savefile.WriteFloat(fovSetting.GetStartTime())
            savefile.WriteFloat(fovSetting.GetDuration())
            savefile.WriteFloat(fovSetting.GetStartValue())
            savefile.WriteFloat(fovSetting.GetEndValue())
            savefile.WriteBool(soundFaded)
            savefile.WriteBool(restoreOnTrigger)
        }

        override fun Restore(savefile: idRestoreGame) {
            var i: Int
            val num = CInt()
            val itemNum = CInt()
            val set = CFloat()
            savefile.ReadInt(num)
            i = 0
            while (i < num._val) {
                savefile.ReadInt(itemNum)
                lightList.Append(itemNum._val)
                i++
            }
            savefile.ReadInt(num)
            i = 0
            while (i < num._val) {
                savefile.ReadInt(itemNum)
                guiList.Append(itemNum._val)
                i++
            }
            savefile.ReadInt(num)
            i = 0
            while (i < num._val) {
                savefile.ReadInt(itemNum)
                soundList.Append(itemNum._val)
                i++
            }
            savefile.ReadInt(num)
            i = 0
            while (i < num._val) {
                savefile.ReadInt(itemNum)
                genericList.Append(itemNum._val)
                i++
            }
            flashIn = savefile.ReadFloat()
            flashOut = savefile.ReadFloat()
            delay = savefile.ReadFloat()
            savefile.ReadString(flashInSound)
            savefile.ReadString(flashOutSound)
            savefile.ReadObject( /*reinterpret_cast<idClass *&>*/switchToCamera)
            savefile.ReadFloat(set)
            fovSetting.SetStartTime(set._val)
            savefile.ReadFloat(set)
            fovSetting.SetDuration(set._val)
            savefile.ReadFloat(set)
            fovSetting.SetStartValue(set._val)
            savefile.ReadFloat(set)
            fovSetting.SetEndValue(set._val)
            soundFaded = savefile.ReadBool()
            restoreOnTrigger = savefile.ReadBool()
        }

        override fun Spawn() {
            super.Spawn()
            PostEventMS(EV_GatherEntities, 0)
            flashIn = spawnArgs.GetFloat("flashIn", "0")
            flashOut = spawnArgs.GetFloat("flashOut", "0")
            flashInSound = idStr(spawnArgs.GetString("snd_flashin"))
            flashOutSound = idStr(spawnArgs.GetString("snd_flashout"))
            delay = spawnArgs.GetFloat("delay")
            soundFaded = false
            restoreOnTrigger = false

            // always allow during cinematics
            cinematic = true
        }

        private fun Event_Activate(activator: idEventArg<idEntity>) {
            var i: Int
            var j: Int
            var ent: idEntity?
            var light: idLight?
            var sound: idSound?
            var generic: idStaticEntity?
            var parm: String?
            val skin: String?
            var update: Boolean
            val color = idVec3()
            val colorTo = idVec4()
            val player: idPlayer
            player = Game_local.gameLocal.GetLocalPlayer()!!
            if (spawnArgs.GetBool("triggerActivate")) {
                if (restoreOnTrigger) {
                    ProcessEvent(EV_RestoreInfluence)
                    restoreOnTrigger = false
                    return
                }
                restoreOnTrigger = true
            }
            val fadeTime = spawnArgs.GetFloat("fadeWorldSounds")
            if (delay > 0.0f) {
                PostEventSec(EV_Activate, delay, activator.value)
                delay = 0.0f
                // start any sound fading now
                if (fadeTime != 0.0f) {
                    Game_local.gameSoundWorld!!.FadeSoundClasses(0, -40.0f, fadeTime)
                    soundFaded = true
                }
                return
            } else if (fadeTime != 0.0f && !soundFaded) {
                Game_local.gameSoundWorld!!.FadeSoundClasses(0, -40.0f, fadeTime)
                soundFaded = true
            }
            if (spawnArgs.GetBool("triggerTargets")) {
                ActivateTargets(activator.value)
            }
            if (flashIn != 0.0f) {
                PostEventSec(EV_Flash, 0.0f, flashIn, 0)
            }
            parm = spawnArgs.GetString("snd_influence")
            if (parm.isNotEmpty()) {
                PostEventSec(EV_StartSoundShader, flashIn, parm, gameSoundChannel_t.SND_CHANNEL_ANY)
            }
            if (switchToCamera != null) {
                switchToCamera!!.PostEventSec(EV_Activate, flashIn + 0.05f, this)
            }
            val fov = spawnArgs.GetInt("fov").toFloat()
            if (fov != 0.0f) {
                fovSetting.Init(
                    Game_local.gameLocal.time.toFloat(),
                    SEC2MS(spawnArgs.GetFloat("fovTime")).toFloat(),
                    player.DefaultFov(),
                    fov
                )
                BecomeActive(TH_THINK)
            }
            i = 0
            while (i < genericList.Num()) {
                ent = Game_local.gameLocal.entities[genericList[i]]
                if (ent == null) {
                    i++
                    continue
                }
                generic = ent as idStaticEntity
                color.set(generic.spawnArgs.GetVector("color_demonic"))
                colorTo.set(color.x, color.y, color.z, 1.0f)
                generic.Fade(colorTo, spawnArgs.GetFloat("fade_time", "0.25f"))
                i++
            }
            i = 0
            while (i < lightList.Num()) {
                ent = Game_local.gameLocal.entities[lightList[i]]
                if (ent == null || ent !is idLight) {
                    i++
                    continue
                }
                light = ent
                parm = light.spawnArgs.GetString("mat_demonic")
                if (parm.isNotEmpty()) {
                    light.SetShader(parm)
                }
                color.set(light.spawnArgs.GetVector("_color"))
                color.set(light.spawnArgs.GetVector("color_demonic", color.ToString()))
                colorTo.set(color.x, color.y, color.z, 1.0f)
                light.Fade(colorTo, spawnArgs.GetFloat("fade_time", "0.25f"))
                i++
            }
            i = 0
            while (i < soundList.Num()) {
                ent = Game_local.gameLocal.entities[soundList[i]]
                if (ent == null || ent !is idSound) {
                    i++
                    continue
                }
                sound = ent
                parm = sound.spawnArgs.GetString("snd_demonic")
                if (parm.isNotEmpty()) {
                    if (sound.spawnArgs.GetBool("overlayDemonic")) {
                        sound.StartSound("snd_demonic", gameSoundChannel_t.SND_CHANNEL_DEMONIC, 0, false)
                    } else {
                        sound.StopSound(TempDump.etoi(gameSoundChannel_t.SND_CHANNEL_ANY), false)
                        sound.SetSound(parm)
                    }
                }
                i++
            }
            i = 0
            while (i < guiList.Num()) {
                ent = Game_local.gameLocal.entities[guiList[i]]
                if (ent == null || ent.GetRenderEntity() == null) {
                    i++
                    continue
                }
                update = false
                j = 0
                while (j < RenderWorld.MAX_RENDERENTITY_GUI) {
                    if (ent.GetRenderEntity()!!.gui[j] != null
                        && ent.spawnArgs.FindKey(if (j == 0) "gui_demonic" else Str.va("gui_demonic%d", j + 1)) != null
                    ) {
                        ent.GetRenderEntity()!!.gui[j] = UserInterface.uiManager.FindGui(
                            ent.spawnArgs.GetString(
                                if (j == 0) "gui_demonic" else Str.va(
                                    "gui_demonic%d",
                                    j + 1
                                )
                            ), true
                        )!!
                        update = true
                    }
                    j++
                }
                if (update) {
                    ent.UpdateVisuals()
                    ent.Present()
                }
                i++
            }
            player.SetInfluenceLevel(spawnArgs.GetInt("influenceLevel"))
            val snapAngle = spawnArgs.GetInt("snapAngle")
            if (snapAngle != 0) {
                val ang = idAngles(0.0f, snapAngle.toFloat(), 0.0f)
                player.SetViewAngles(ang)
                player.SetAngles(ang)
            }
            if (spawnArgs.GetBool("effect_vision")) {
                parm = spawnArgs.GetString("mtrVision")
                skin = spawnArgs.GetString("skinVision")
                player.SetInfluenceView(parm, skin, spawnArgs.GetInt("visionRadius").toFloat(), this)
            }
            parm = spawnArgs.GetString("mtrWorld")
            if (parm.isNotEmpty()) {
                Game_local.gameLocal.SetGlobalMaterial(DeclManager.declManager.FindMaterial(parm))
            }
            if (!restoreOnTrigger) {
                PostEventMS(EV_RestoreInfluence, SEC2MS(spawnArgs.GetFloat("time")))
            }
        }

        private fun Event_RestoreInfluence() {
            var i: Int
            var j: Int
            var ent: idEntity?
            var light: idLight?
            var sound: idSound?
            var generic: idStaticEntity?
            var update: Boolean
            val color = idVec3()
            val colorTo = idVec4()
            if (flashOut != 0.0f) {
                PostEventSec(EV_Flash, 0.0f, flashOut, 1)
            }
            if (switchToCamera != null) {
                switchToCamera!!.PostEventMS(EV_Activate, 0.0f, this)
            }
            i = 0
            while (i < genericList.Num()) {
                ent = Game_local.gameLocal.entities[genericList[i]]
                if (ent == null) {
                    i++
                    continue
                }
                generic = ent as idStaticEntity
                colorTo.set(1.0f, 1.0f, 1.0f, 1.0f)
                generic.Fade(colorTo, spawnArgs.GetFloat("fade_time", "0.25f"))
                i++
            }
            i = 0
            while (i < lightList.Num()) {
                ent = Game_local.gameLocal.entities[lightList[i]]
                if (ent == null || ent !is idLight) {
                    i++
                    continue
                }
                light = ent
                if (!light.spawnArgs.GetBool("leave_demonic_mat")) {
                    val texture = light.spawnArgs.GetString("texture", "lights/squarelight1")!!
                    light.SetShader(texture)
                }
                color.set(light.spawnArgs.GetVector("_color"))
                colorTo.set(color.x, color.y, color.z, 1.0f)
                light.Fade(colorTo, spawnArgs.GetFloat("fade_time", "0.25f"))
                i++
            }
            i = 0
            while (i < soundList.Num()) {
                ent = Game_local.gameLocal.entities[soundList[i]]
                if (ent == null || ent !is idSound) {
                    i++
                    continue
                }
                sound = ent
                sound.StopSound(TempDump.etoi(gameSoundChannel_t.SND_CHANNEL_ANY), false)
                sound.SetSound(sound.spawnArgs.GetString("s_shader"))
                i++
            }
            i = 0
            while (i < guiList.Num()) {
                ent = Game_local.gameLocal.entities[guiList[i]]
                if (ent == null || GetRenderEntity() == null) {
                    i++
                    continue
                }
                update = false
                j = 0
                while (j < RenderWorld.MAX_RENDERENTITY_GUI) {
                    if (ent.GetRenderEntity()!!.gui[j] != null) {
                        ent.GetRenderEntity()!!.gui[j] = UserInterface.uiManager.FindGui(
                            ent.spawnArgs.GetString(
                                if (j == 0) "gui" else Str.va(
                                    "gui%d",
                                    j + 1
                                )
                            )
                        )!!
                        update = true
                    }
                    j++
                }
                if (update) {
                    ent.UpdateVisuals()
                    ent.Present()
                }
                i++
            }
            val player = Game_local.gameLocal.GetLocalPlayer()!!
            player.SetInfluenceLevel(0)
            player.SetInfluenceView(null, null, 0.0f, null)
            player.SetInfluenceFov(0.0f)
            Game_local.gameLocal.SetGlobalMaterial(null)
            val fadeTime = spawnArgs.GetFloat("fadeWorldSounds")
            if (fadeTime != 0.0f) {
                Game_local.gameSoundWorld!!.FadeSoundClasses(0, 0.0f, fadeTime / 2.0f)
            }
        }

        private fun Event_GatherEntities() {
            var i: Int
            val listedEntities: Int
            val entityList = arrayOfNulls<idEntity>(Game_local.MAX_GENTITIES)

            spawnArgs.GetBool("effect_demonic")
            var lights = spawnArgs.GetBool("effect_lights")
            var sounds = spawnArgs.GetBool("effect_sounds")
            var guis = spawnArgs.GetBool("effect_guis")
            var models = spawnArgs.GetBool("effect_models")
            var vision = spawnArgs.GetBool("effect_vision")
            val targetsOnly = spawnArgs.GetBool("targetsOnly")

            lightList.Clear()
            guiList.Clear()
            soundList.Clear()

            if (spawnArgs.GetBool("effect_all")) {
                vision = true
                models = vision
                guis = models
                sounds = guis
                lights = sounds
            }
            if (targetsOnly) {
                listedEntities = targets.Num()
                i = 0
                while (i < listedEntities) {
                    entityList[i] = targets[i].GetEntity()!!
                    i++
                }
            } else {
                val radius = spawnArgs.GetFloat("radius")
                listedEntities = Game_local.gameLocal.EntitiesWithinRadius(
                    GetPhysics().GetOrigin(),
                    radius,
                    entityList,
                    Game_local.MAX_GENTITIES
                )
            }
            i = 0
            while (i < listedEntities) {
                val ent = entityList[i]
                if (ent != null) {
                    if (lights && ent is idLight && ent.spawnArgs.FindKey("color_demonic") != null) {
                        lightList.Append(ent.entityNumber)
                        i++
                        continue
                    }
                    if (sounds && ent is idSound && ent.spawnArgs.FindKey("snd_demonic") != null) {
                        soundList.Append(ent.entityNumber)
                        i++
                        continue
                    }
                    if (guis && ent.GetRenderEntity() != null && ent.GetRenderEntity()!!.gui[0] != null && ent.spawnArgs.FindKey(
                            "gui_demonic"
                        ) != null
                    ) {
                        guiList.Append(ent.entityNumber)
                        i++
                        continue
                    }
                    if (ent is idStaticEntity && ent.spawnArgs.FindKey("color_demonic") != null) {
                        genericList.Append(ent.entityNumber)
                        //                        continue;
                    }
                }
                i++
            }
            val temp: String?
            temp = spawnArgs.GetString("switchToView")
            switchToCamera = if (temp.length != 0) Game_local.gameLocal.FindEntity(temp) else null
        }

        private fun Event_Flash(_flash: idEventArg<Float>, _out: idEventArg<Int>) {
            val flash: Float = _flash.value
            val out: Int = _out.value
            val player = Game_local.gameLocal.GetLocalPlayer()!!
            player.playerView.Fade(idVec4(1.0f, 1.0f, 1.0f, 1.0f), flash.toInt())
            val shader: idSoundShader?
            if (0 == out && flashInSound.Length() != 0) {
                shader = DeclManager.declManager.FindSound(flashInSound)
                player.StartSoundShader(shader, gameSoundChannel_t.SND_CHANNEL_VOICE.ordinal, 0, false)
            } else if (out != 0 && (flashOutSound.Length() != 0 || flashInSound.Length() != 0)) {
                shader =
                    DeclManager.declManager.FindSound(if (flashOutSound.Length() != 0) flashOutSound else flashInSound)
                player.StartSoundShader(shader, gameSoundChannel_t.SND_CHANNEL_VOICE.ordinal, 0, false)
            }
            PostEventSec(EV_ClearFlash, flash, flash)
        }

        private fun Event_ClearFlash(flash: idEventArg<Float>) {
            val player = Game_local.gameLocal.GetLocalPlayer()!!
            player.playerView.Fade(getVec4_zero(), flash.value.toInt())
        }

        override fun Think() {
            if ((thinkFlags and TH_THINK) != 0) {
                val player = Game_local.gameLocal.GetLocalPlayer()!!
                player.SetInfluenceFov(fovSetting.GetCurrentValue(Game_local.gameLocal.time.toFloat()))
                if (fovSetting.IsDone(Game_local.gameLocal.time.toFloat())) {
                    if (!spawnArgs.GetBool("leaveFOV")) {
                        player.SetInfluenceFov(0.0f)
                    }
                    BecomeInactive(TH_THINK)
                }
            } else {
                BecomeInactive(TH_ALL)
            }
        }

        override fun getEventCallBack(event: idEventDef): eventCallback_t<*>? {
            return eventCallbacks[event]
        }

        //
        //
        init {
            lightList = idList()
            guiList = idList()
            soundList = idList()
            genericList = idList()
            flashIn = 0.0f
            flashOut = 0.0f
            delay = 0.0f
            switchToCamera = null
            soundFaded = false
            restoreOnTrigger = false
        }
    }

    /*
     ===============================================================================

     idTarget_SetKeyVal

     ===============================================================================
     */
    class idTarget_SetKeyVal : idTarget() {
        companion object {
            // CLASS_PROTOTYPE( idTarget_SetKeyVal );
            private val eventCallbacks: MutableMap<idEventDef, eventCallback_t<*>> = HashMap()
            fun getEventCallBacks(): MutableMap<idEventDef, eventCallback_t<*>> {
                return eventCallbacks
            }

            init {
                eventCallbacks.putAll(idEntity.getEventCallBacks())
                eventCallbacks[EV_Activate] =
                    eventCallback_t1<idTarget_SetKeyVal> { obj: idTarget_SetKeyVal, activator: idEventArg<*>? ->
                        obj.Event_Activate(activator as idEventArg<idEntity>)
                    }
            }
        }

        private fun Event_Activate(activator: idEventArg<idEntity>) {
            var i: Int
            var key: String
            var `val`: String
            var ent: idEntity?
            var kv: idKeyValue?
            var n: Int
            i = 0
            while (i < targets.Num()) {
                ent = targets[i].GetEntity()
                if (ent != null) {
                    kv = spawnArgs.MatchPrefix("keyval")
                    while (kv != null) {
                        n = kv.GetValue().Find(";")
                        if (n > 0) {
                            key = kv.GetValue().Left(n).toString()
                            `val` = kv.GetValue().Right(kv.GetValue().Length() - n - 1).toString()
                            ent.spawnArgs.Set(key, `val`)
                            for (j in 0 until RenderWorld.MAX_RENDERENTITY_GUI) {
                                if (ent.GetRenderEntity()!!.gui[j] != null) {
                                    if (idStr.Icmpn(key, "gui_", 4) == 0) {
                                        ent.GetRenderEntity()!!.gui[j]!!.SetStateString(key, `val`)
                                        ent.GetRenderEntity()!!.gui[j]!!.StateChanged(Game_local.gameLocal.time)
                                    }
                                }
                            }
                        }
                        kv = spawnArgs.MatchPrefix("keyval", kv)
                    }
                    ent.UpdateChangeableSpawnArgs(null)
                    ent.UpdateVisuals()
                    ent.Present()
                }
                i++
            }
        }

        override fun getEventCallBack(event: idEventDef): eventCallback_t<*>? {
            return eventCallbacks[event]
        }
    }

    /*
     ===============================================================================

     idTarget_SetFov

     ===============================================================================
     */
    class idTarget_SetFov : idTarget() {
        companion object {
            // CLASS_PROTOTYPE( idTarget_SetFov );
            private val eventCallbacks: MutableMap<idEventDef, eventCallback_t<*>> = HashMap()

            //
            //
            fun getEventCallBacks(): MutableMap<idEventDef, eventCallback_t<*>> {
                return eventCallbacks
            }

            init {
                eventCallbacks.putAll(idEntity.getEventCallBacks())
                eventCallbacks[EV_Activate] =
                    eventCallback_t1<idTarget_SetFov> { obj: idTarget_SetFov, activator: idEventArg<*>? ->
                        obj.Event_Activate(activator as idEventArg<idEntity>)
                    }
            }
        }

        private val fovSetting: idInterpolate<Int> = idInterpolate()
        override fun Save(savefile: idSaveGame) {
            savefile.WriteFloat(fovSetting.GetStartTime())
            savefile.WriteFloat(fovSetting.GetDuration())
            savefile.WriteFloat(fovSetting.GetStartValue().toFloat())
            savefile.WriteFloat(fovSetting.GetEndValue().toFloat())
        }

        override fun Restore(savefile: idRestoreGame) {
            val setting = CFloat()
            savefile.ReadFloat(setting)
            fovSetting.SetStartTime(setting._val)
            savefile.ReadFloat(setting)
            fovSetting.SetDuration(setting._val)
            savefile.ReadFloat(setting)
            fovSetting.SetStartValue(setting._val.toInt())
            savefile.ReadFloat(setting)
            fovSetting.SetEndValue(setting._val.toInt())
            fovSetting.GetCurrentValue(Game_local.gameLocal.time.toFloat())
        }

        override fun Think() {
            if ((thinkFlags and TH_THINK) != 0) {
                val player = Game_local.gameLocal.GetLocalPlayer()!!
                player.SetInfluenceFov(fovSetting.GetCurrentValue(Game_local.gameLocal.time.toFloat()).toFloat())
                if (fovSetting.IsDone(Game_local.gameLocal.time.toFloat())) {
                    player.SetInfluenceFov(0.0f)
                    BecomeInactive(TH_THINK)
                }
            } else {
                BecomeInactive(TH_ALL)
            }
        }

        private fun Event_Activate(activator: idEventArg<idEntity>) {
            // always allow during cinematics
            cinematic = true
            val player = Game_local.gameLocal.GetLocalPlayer()
            fovSetting.Init(
                Game_local.gameLocal.time.toFloat(), SEC2MS(spawnArgs.GetFloat("time")).toFloat(), (player?.DefaultFov()
                    ?: SysCvar.g_fov.GetFloat()).toInt(), spawnArgs.GetFloat("fov").toInt()
            )
            BecomeActive(TH_THINK)
        }

        override fun getEventCallBack(event: idEventDef): eventCallback_t<*>? {
            return eventCallbacks[event]
        }
    }

    /*
     ===============================================================================

     idTarget_SetPrimaryObjective

     ===============================================================================
     */
    class idTarget_SetPrimaryObjective : idTarget() {
        companion object {
            // CLASS_PROTOTYPE( idTarget_SetPrimaryObjective );
            private val eventCallbacks: MutableMap<idEventDef, eventCallback_t<*>> = HashMap()
            fun getEventCallBacks(): MutableMap<idEventDef, eventCallback_t<*>> {
                return eventCallbacks
            }

            init {
                eventCallbacks.putAll(idEntity.getEventCallBacks())
                eventCallbacks[EV_Activate] =
                    eventCallback_t1<idTarget_SetPrimaryObjective> { obj: idTarget_SetPrimaryObjective, activator: idEventArg<*>? ->
                        obj.Event_Activate(activator as idEventArg<idEntity?>)
                    }
            }
        }

        private fun Event_Activate(activator: idEventArg<idEntity?>?) {
            val player = Game_local.gameLocal.GetLocalPlayer()
            if (player != null && player.objectiveSystem != null) {
                player.objectiveSystem!!.SetStateString(
                    "missionobjective",
                    spawnArgs.GetString("text", Common.common.GetLanguageDict().GetString("#str_04253"))!!
                )
            }
        }

        override fun getEventCallBack(event: idEventDef): eventCallback_t<*>? {
            return eventCallbacks[event]
        }
    }

    /*
     ===============================================================================

     idTarget_LockDoor

     ===============================================================================
     */
    class idTarget_LockDoor : idTarget() {
        companion object {
            // CLASS_PROTOTYPE( idTarget_LockDoor );
            private val eventCallbacks: MutableMap<idEventDef, eventCallback_t<*>> = HashMap()
            fun getEventCallBacks(): MutableMap<idEventDef, eventCallback_t<*>> {
                return eventCallbacks
            }

            init {
                eventCallbacks.putAll(idEntity.getEventCallBacks())
                eventCallbacks[EV_Activate] =
                    eventCallback_t1<idTarget_LockDoor> { obj: idTarget_LockDoor, activator: idEventArg<*>? ->
                        obj.Event_Activate(activator as idEventArg<idEntity>)
                    }
            }
        }

        private fun Event_Activate(activator: idEventArg<idEntity>) {
            var i: Int
            var ent: idEntity?
            val lock: Int
            lock = spawnArgs.GetInt("locked", "1")
            i = 0
            while (i < targets.Num()) {
                ent = targets[i].GetEntity()
                if (ent != null && ent is idDoor) {
                    if (ent.IsLocked() != 0) {
                        ent.Lock(0)
                    } else {
                        ent.Lock(lock)
                    }
                }
                i++
            }
        }

        override fun getEventCallBack(event: idEventDef): eventCallback_t<*>? {
            return eventCallbacks[event]
        }
    }

    /*
     ===============================================================================

     idTarget_CallObjectFunction

     ===============================================================================
     */
    class idTarget_CallObjectFunction : idTarget() {
        companion object {
            // CLASS_PROTOTYPE( idTarget_CallObjectFunction );
            private val eventCallbacks: MutableMap<idEventDef, eventCallback_t<*>> = HashMap()
            fun getEventCallBacks(): MutableMap<idEventDef, eventCallback_t<*>> {
                return eventCallbacks
            }

            init {
                eventCallbacks.putAll(idEntity.getEventCallBacks())
                eventCallbacks[EV_Activate] =
                    eventCallback_t1<idTarget_CallObjectFunction> { obj: idTarget_CallObjectFunction, activator: idEventArg<*>? ->
                        obj.Event_Activate(activator as idEventArg<idEntity>)
                    }
            }
        }

        private fun Event_Activate(activator: idEventArg<idEntity>) {
            var i: Int
            var ent: idEntity?
            var func: function_t?
            val funcName: String?
            var thread: idThread
            funcName = spawnArgs.GetString("call")
            i = 0
            while (i < targets.Num()) {
                ent = targets[i].GetEntity()
                if (ent != null && ent.scriptObject.HasObject()) {
                    func = ent.scriptObject.GetFunction(funcName)
                    if (null == func) {
                        idGameLocal.Error(
                            "Function '%s' not found on entity '%s' for function call from '%s'",
                            funcName,
                            ent.name,
                            name
                        )
                    }
                    if (func!!.type!!.NumParameters() != 1) {
                        idGameLocal.Error(
                            "Function '%s' on entity '%s' has the wrong number of parameters for function call from '%s'",
                            funcName,
                            ent.name,
                            name
                        )
                    }
                    if (!ent.scriptObject.GetTypeDef()!!.Inherits(func.type!!.GetParmType(0))) {
                        idGameLocal.Error(
                            "Function '%s' on entity '%s' is the wrong type for function call from '%s'",
                            funcName,
                            ent.name,
                            name
                        )
                    }
                    // create a thread and call the function
                    thread = idThread()
                    thread.CallFunction(ent, func, true)
                    thread.Start()
                }
                i++
            }
        }

        override fun getEventCallBack(event: idEventDef): eventCallback_t<*>? {
            return eventCallbacks[event]
        }
    }

    /*
     ===============================================================================

     idTarget_LockDoor

     ===============================================================================
     */
    class idTarget_EnableLevelWeapons : idTarget() {
        companion object {
            // CLASS_PROTOTYPE( idTarget_EnableLevelWeapons );
            private val eventCallbacks: MutableMap<idEventDef, eventCallback_t<*>> = HashMap()
            fun getEventCallBacks(): MutableMap<idEventDef, eventCallback_t<*>> {
                return eventCallbacks
            }

            init {
                eventCallbacks.putAll(idEntity.getEventCallBacks())
                eventCallbacks[EV_Activate] =
                    eventCallback_t1<idTarget_EnableLevelWeapons> { obj: idTarget_EnableLevelWeapons, activator: idEventArg<*>? ->
                        obj.Event_Activate(activator as idEventArg<idEntity>)
                    }
            }
        }

        private fun Event_Activate(activator: idEventArg<idEntity>) {
            var i: Int
            val weap: String?
            Game_local.gameLocal.world!!.spawnArgs.SetBool("no_Weapons", spawnArgs.GetBool("disable"))
            if (spawnArgs.GetBool("disable")) {
                i = 0
                while (i < Game_local.gameLocal.numClients) {
                    if (Game_local.gameLocal.entities[i] != null) {
                        Game_local.gameLocal.entities[i]!!.ProcessEvent(EV_Player_DisableWeapon)
                    }
                    i++
                }
            } else {
                weap = spawnArgs.GetString("weapon")
                i = 0
                while (i < Game_local.gameLocal.numClients) {
                    if (Game_local.gameLocal.entities[i] != null) {
                        Game_local.gameLocal.entities[i]!!.ProcessEvent(EV_Player_EnableWeapon)
                        if (weap.isNotEmpty()) {
                            Game_local.gameLocal.entities[i]!!.PostEventSec(EV_Player_SelectWeapon, 0.5f, weap)
                        }
                    }
                    i++
                }
            }
        }

        override fun getEventCallBack(event: idEventDef): eventCallback_t<*>? {
            return eventCallbacks[event]
        }
    }

    /*
     ===============================================================================

     idTarget_Tip

     ===============================================================================
     */
    class idTarget_Tip : idTarget() {
        companion object {
            // CLASS_PROTOTYPE( idTarget_Tip );
            private val eventCallbacks: MutableMap<idEventDef, eventCallback_t<*>> = HashMap()
            fun getEventCallBacks(): MutableMap<idEventDef, eventCallback_t<*>> {
                return eventCallbacks
            }

            init {
                eventCallbacks.putAll(idEntity.getEventCallBacks())
                eventCallbacks[EV_Activate] =
                    eventCallback_t1<idTarget_Tip> { obj: idTarget_Tip, activator: idEventArg<*>? ->
                        obj.Event_Activate(activator as idEventArg<idEntity>)
                    }
                eventCallbacks[EV_TipOff] =
                    eventCallback_t0<idTarget_Tip> { obj: idTarget_Tip -> obj.Event_TipOff() }
                eventCallbacks[EV_GetPlayerPos] =
                    eventCallback_t0<idTarget_Tip> { obj: idTarget_Tip -> obj.Event_GetPlayerPos() }
            }
        }

        private val playerPos: idVec3 = idVec3()
        override fun Save(savefile: idSaveGame) {
            savefile.WriteVec3(playerPos)
        }

        override fun Restore(savefile: idRestoreGame) {
            savefile.ReadVec3(playerPos)
        }

        private fun Event_Activate(activator: idEventArg<idEntity>) {
            val player = Game_local.gameLocal.GetLocalPlayer()
            if (player != null) {
                if (player.IsTipVisible()) {
                    PostEventSec(EV_Activate, 5.1f, activator.value)
                    return
                }
                player.ShowTip(spawnArgs.GetString("text_title"), spawnArgs.GetString("text_tip"), false)
                PostEventMS(EV_GetPlayerPos, 2000)
            }
        }

        private fun Event_TipOff() {
            val player = Game_local.gameLocal.GetLocalPlayer()
            if (player != null) {
                val v = idVec3(player.GetPhysics().GetOrigin().minus(playerPos))
                if (v.Length() > 96.0f) {
                    player.HideTip()
                } else {
                    PostEventMS(EV_TipOff, 100)
                }
            }
        }

        private fun Event_GetPlayerPos() {
            val player = Game_local.gameLocal.GetLocalPlayer()
            if (player != null) {
                playerPos.set(player.GetPhysics().GetOrigin())
                PostEventMS(EV_TipOff, 100)
            }
        }

        override fun getEventCallBack(event: idEventDef): eventCallback_t<*>? {
            return eventCallbacks[event]
        }
    }

    /*
     ===============================================================================

     idTarget_GiveSecurity

     ===============================================================================
     */
    class idTarget_GiveSecurity : idTarget() {
        companion object {
            // CLASS_PROTOTYPE( idTarget_GiveSecurity );
            private val eventCallbacks: MutableMap<idEventDef, eventCallback_t<*>> = HashMap()
            fun getEventCallBacks(): MutableMap<idEventDef, eventCallback_t<*>> {
                return eventCallbacks
            }

            init {
                eventCallbacks.putAll(idEntity.getEventCallBacks())
                eventCallbacks[EV_Activate] =
                    eventCallback_t1<idTarget_GiveSecurity> { obj: idTarget_GiveSecurity, activator: idEventArg<*>? ->
                        obj.Event_Activate(activator as idEventArg<idEntity?>)
                    }
            }
        }

        private fun Event_Activate(activator: idEventArg<idEntity?>?) {
            val player = Game_local.gameLocal.GetLocalPlayer()
            player?.GiveSecurity(spawnArgs.GetString("text_security"))
        }

        override fun getEventCallBack(event: idEventDef): eventCallback_t<*>? {
            return eventCallbacks[event]
        }
    }

    /*
     ===============================================================================

     idTarget_RemoveWeapons

     ===============================================================================
     */
    class idTarget_RemoveWeapons : idTarget() {
        companion object {
            // CLASS_PROTOTYPE( idTarget_RemoveWeapons );
            private val eventCallbacks: MutableMap<idEventDef, eventCallback_t<*>> = HashMap()
            fun getEventCallBacks(): MutableMap<idEventDef, eventCallback_t<*>> {
                return eventCallbacks
            }

            init {
                eventCallbacks.putAll(idEntity.getEventCallBacks())
                eventCallbacks[EV_Activate] =
                    eventCallback_t1<idTarget_RemoveWeapons> { obj: idTarget_RemoveWeapons, activator: idEventArg<*>? ->
                        obj.Event_Activate(activator as idEventArg<idEntity?>)
                    }
            }
        }

        private fun Event_Activate(activator: idEventArg<idEntity?>?) {
            for (i in 0 until Game_local.gameLocal.numClients) {
                if (Game_local.gameLocal.entities[i] != null) {
                    val player = Game_local.gameLocal.entities[i] as idPlayer
                    var kv = spawnArgs.MatchPrefix("weapon", null)
                    while (kv != null) {
                        player.RemoveWeapon(kv.GetValue().toString())
                        kv = spawnArgs.MatchPrefix("weapon", kv)
                    }
                    player.SelectWeapon(player.weapon_fists, true)
                }
            }
        }

        override fun getEventCallBack(event: idEventDef): eventCallback_t<*>? {
            return eventCallbacks[event]
        }
    }

    /*
     ===============================================================================

     idTarget_LevelTrigger

     ===============================================================================
     */
    class idTarget_LevelTrigger : idTarget() {
        companion object {
            // CLASS_PROTOTYPE( idTarget_LevelTrigger );//TODO:understand this fucking macro
            private val eventCallbacks: MutableMap<idEventDef, eventCallback_t<*>> = HashMap()
            fun getEventCallBacks(): MutableMap<idEventDef, eventCallback_t<*>> {
                return eventCallbacks
            }

            init {
                eventCallbacks.putAll(idEntity.getEventCallBacks())
                eventCallbacks[EV_Activate] =
                    eventCallback_t1<idTarget_LevelTrigger> { obj: idTarget_LevelTrigger, activator: idEventArg<*>? ->
                        obj.Event_Activate(activator as idEventArg<idEntity?>)
                    }
            }
        }

        private fun Event_Activate(activator: idEventArg<idEntity?>?) {
            for (i in 0 until Game_local.gameLocal.numClients) {
                if (Game_local.gameLocal.entities[i] != null) {
                    val player = Game_local.gameLocal.entities[i] as idPlayer
                    player.SetLevelTrigger(spawnArgs.GetString("levelName"), spawnArgs.GetString("triggerName"))
                }
            }
        }

        override fun getEventCallBack(event: idEventDef): eventCallback_t<*>? {
            return eventCallbacks[event]
        }
    }

    /*
     ===============================================================================

     idTarget_EnableStamina

     ===============================================================================
     */
    class idTarget_EnableStamina : idTarget() {
        companion object {
            // CLASS_PROTOTYPE( idTarget_EnableStamina );
            private val eventCallbacks: MutableMap<idEventDef, eventCallback_t<*>> = HashMap()
            fun getEventCallBacks(): MutableMap<idEventDef, eventCallback_t<*>> {
                return eventCallbacks
            }

            init {
                eventCallbacks.putAll(idEntity.getEventCallBacks())
                eventCallbacks[EV_Activate] =
                    eventCallback_t1<idTarget_EnableStamina> { obj: idTarget_EnableStamina, activator: idEventArg<*>? ->
                        obj.Event_Activate(activator as idEventArg<idEntity>)
                    }
            }
        }

        private fun Event_Activate(activator: idEventArg<idEntity>) {
            for (i in 0 until Game_local.gameLocal.numClients) {
                if (Game_local.gameLocal.entities[i] != null) {
                    val player = Game_local.gameLocal.entities[i] as idPlayer
                    if (spawnArgs.GetBool("enable")) {
                        SysCvar.pm_stamina.SetFloat(player.spawnArgs.GetFloat("pm_stamina"))
                    } else {
                        SysCvar.pm_stamina.SetFloat(0.0f)
                    }
                }
            }
        }

        override fun getEventCallBack(event: idEventDef): eventCallback_t<*>? {
            return eventCallbacks[event]
        }
    }

    /*
     ===============================================================================

     idTarget_FadeSoundClass

     ===============================================================================
     */
    class idTarget_FadeSoundClass : idTarget() {
        companion object {
            // CLASS_PROTOTYPE( idTarget_FadeSoundClass );
            private val eventCallbacks: MutableMap<idEventDef, eventCallback_t<*>> = HashMap()
            fun getEventCallBacks(): MutableMap<idEventDef, eventCallback_t<*>> {
                return eventCallbacks
            }

            init {
                eventCallbacks.putAll(idEntity.getEventCallBacks())
                eventCallbacks[EV_Activate] =
                    eventCallback_t1<idTarget_FadeSoundClass> { obj: idTarget_FadeSoundClass, activator: idEventArg<*>? ->
                        obj.Event_Activate(activator as idEventArg<idEntity>)
                    }
                eventCallbacks[EV_RestoreVolume] =
                    eventCallback_t0<idTarget_FadeSoundClass> { obj: idTarget_FadeSoundClass -> obj.Event_RestoreVolume() }
            }
        }

        private fun Event_Activate(activator: idEventArg<idEntity>) {
            val fadeTime = spawnArgs.GetFloat("fadeTime")
            val fadeDB = spawnArgs.GetFloat("fadeDB")
            val fadeDuration = spawnArgs.GetFloat("fadeDuration")
            val fadeClass = spawnArgs.GetInt("fadeClass")
            // start any sound fading now
            if (fadeTime != 0.0f) {
                Game_local.gameSoundWorld!!.FadeSoundClasses(
                    fadeClass,
                    if (spawnArgs.GetBool("fadeIn")) fadeDB else  /*0.0f */ -fadeDB,
                    fadeTime
                )
                if (fadeDuration != 0.0f) {
                    PostEventSec(EV_RestoreVolume, fadeDuration)
                }
            }
        }

        private fun Event_RestoreVolume() {
            val fadeTime = spawnArgs.GetFloat("fadeTime")
            val fadeDB = spawnArgs.GetFloat("fadeDB")
            spawnArgs.GetInt("fadeClass")
            // restore volume
            Game_local.gameSoundWorld!!.FadeSoundClasses(0, fadeDB, fadeTime)
        }

        override fun getEventCallBack(event: idEventDef): eventCallback_t<*>? {
            return eventCallbacks[event]
        }
    }
}
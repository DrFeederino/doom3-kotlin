package neo.framework

import neo.Renderer.ModelManager
import neo.framework.DeclManager.declType_t
import neo.framework.DeclManager.idDecl
import neo.idlib.Text.Lexer.idLexer
import neo.idlib.Text.Str.idStr
import neo.idlib.Text.Token.idToken
import neo.idlib.containers.List.idList
import neo.idlib.idException
import neo.idlib.math.Matrix.idMat3
import neo.idlib.math.getVec3Origin
import neo.idlib.math.idAngles
import neo.idlib.math.idVec3

class DeclFX {
    /*
     ===============================================================================

     idDeclFX

     ===============================================================================
     */
    enum class fx_enum {
        FX_LIGHT, FX_PARTICLE, FX_DECAL, FX_MODEL, FX_SOUND, FX_SHAKE, FX_ATTACHLIGHT, FX_ATTACHENTITY, FX_LAUNCH, FX_SHOCKWAVE
    }

    // single fx structure
    class idFXSingleAction {
        val axis: idMat3 = idMat3()
        var bindParticles = false
        val data: idStr = idStr()
        var delay = 0.0f
        var duration = 0.0f
        var explicitAxis = false
        var fadeInTime = 0.0f
        var fadeOutTime = 0.0f
        val fire: idStr = idStr()
        val lightColor: idVec3 = idVec3()
        var lightRadius = 0.0f
        val name: idStr = idStr()
        var noshadows = false
        val offset: idVec3 = idVec3()
        var particleTrackVelocity = false
        var random1 = 0.0f
        var random2 = 0.0f
        var restart = 0.0f
        var rotate = 0.0f
        var shakeAmplitude = 0.0f
        var shakeDistance = 0.0f
        var shakeFalloff = false
        var shakeIgnoreMaster = false
        var shakeImpulse = 0.0f
        var shakeStarted = false
        var shakeTime = 0.0f
        var sibling = 0
        var size = 0.0f

        //
        var soundStarted = false
        var trackOrigin = false
        var type: fx_enum? = fx_enum.values()[0]
    }

    //
    // grouped fx structures
    //
    class idDeclFX : idDecl() {
        //
        //
        val events: idList<idFXSingleAction> = idList()
        val joint: idStr = idStr()
        override fun DefaultDefinition(): String {
            run {
                return """{
	{
		duration	5
		model		_default
	}
}"""
            }
        }

        @Throws(idException::class)
        override fun Parse(text: String, textLength: Int): Boolean {
            val src = idLexer()
            src.LoadMemory(text, textLength, GetFileName(), GetLineNum())
            src.SetFlags(DeclManager.DECL_LEXER_FLAGS)
            src.SkipUntilString("{")

            // scan through, identifying each individual parameter
            while (true) {
                val token = idToken()
                if (!src.ReadToken(token)) {
                    break
                }
                if (token.toString() == "}") {
                    break
                }
                if (0 == token.Icmp("bindto")) {
                    src.ReadToken(token)
                    joint.set(token)
                    continue
                }
                if (0 == token.Icmp("{")) {
                    val action = idFXSingleAction()
                    ParseSingleFXAction(src, action)
                    events.Append(action)
                    continue
                }
            }
            if (src.HadError()) {
                src.Warning("FX decl '%s' had a parse error", GetName())
                return false
            }
            return true
        }

        override fun FreeData() {
            events.Clear()
        }

        @Throws(idException::class)
        override fun Print() {
            val list = this
            //            final fx_enum[] values = fx_enum.values();
            Common.common.Printf("%d events\n", list.events.Num())
            for (i in 0 until list.events.Num()) {
                when (list.events[i].type!!) {
                    fx_enum.FX_LIGHT -> Common.common.Printf("FX_LIGHT %s\n", list.events[i].data.toString())
                    fx_enum.FX_PARTICLE -> Common.common.Printf("FX_PARTICLE %s\n", list.events[i].data.toString())
                    fx_enum.FX_MODEL -> Common.common.Printf("FX_MODEL %s\n", list.events[i].data.toString())
                    fx_enum.FX_SOUND -> Common.common.Printf("FX_SOUND %s\n", list.events[i].data.toString())
                    fx_enum.FX_DECAL -> Common.common.Printf("FX_DECAL %s\n", list.events[i].data.toString())
                    fx_enum.FX_SHAKE -> Common.common.Printf("FX_SHAKE %s\n", list.events[i].data.toString())
                    fx_enum.FX_ATTACHLIGHT -> Common.common.Printf(
                        "FX_ATTACHLIGHT %s\n",
                        list.events[i].data.toString()
                    )

                    fx_enum.FX_ATTACHENTITY -> Common.common.Printf(
                        "FX_ATTACHENTITY %s\n",
                        list.events[i].data.toString()
                    )

                    fx_enum.FX_LAUNCH -> Common.common.Printf("FX_LAUNCH %s\n", list.events[i].data.toString())
                    fx_enum.FX_SHOCKWAVE -> Common.common.Printf(
                        "FX_SHOCKWAVE %s\n",
                        list.events[i].data.toString()
                    )
                }
            }
        }

        @Throws(idException::class)
        override fun List() {
            Common.common.Printf("%s, %d stages\n", GetName(), events.Num())
        }

        //
        @Throws(idException::class)
        private fun ParseSingleFXAction(src: idLexer, FXAction: idFXSingleAction) {
            val token = idToken()
            FXAction.type = null
            FXAction.sibling = -1
            FXAction.data.set("<none>")
            FXAction.name.set("<none>")
            FXAction.fire.set("<none>")
            FXAction.delay = 0.0f
            FXAction.duration = 0.0f
            FXAction.restart = 0.0f
            FXAction.size = 0.0f
            FXAction.fadeInTime = 0.0f
            FXAction.fadeOutTime = 0.0f
            FXAction.shakeTime = 0.0f
            FXAction.shakeAmplitude = 0.0f
            FXAction.shakeDistance = 0.0f
            FXAction.shakeFalloff = false
            FXAction.shakeImpulse = 0.0f
            FXAction.shakeIgnoreMaster = false
            FXAction.lightRadius = 0.0f
            FXAction.rotate = 0.0f
            FXAction.random1 = 0.0f
            FXAction.random2 = 0.0f
            FXAction.lightColor.set(getVec3Origin())
            FXAction.offset.set(getVec3Origin())
            FXAction.axis.set(idMat3.getMat3_identity())
            FXAction.bindParticles = false
            FXAction.explicitAxis = false
            FXAction.noshadows = false
            FXAction.particleTrackVelocity = false
            FXAction.trackOrigin = false
            FXAction.soundStarted = false
            while (true) {
                if (!src.ReadToken(token)) {
                    break
                }
                if (0 == token.Icmp("}")) {
                    break
                }
                if (0 == token.Icmp("shake")) {
                    FXAction.type = fx_enum.FX_SHAKE
                    FXAction.shakeTime = src.ParseFloat()
                    src.ExpectTokenString(",")
                    FXAction.shakeAmplitude = src.ParseFloat()
                    src.ExpectTokenString(",")
                    FXAction.shakeDistance = src.ParseFloat()
                    src.ExpectTokenString(",")
                    FXAction.shakeFalloff = src.ParseBool()
                    src.ExpectTokenString(",")
                    FXAction.shakeImpulse = src.ParseFloat()
                    continue
                }
                if (0 == token.Icmp("noshadows")) {
                    FXAction.noshadows = true
                    continue
                }
                if (0 == token.Icmp("name")) {
                    src.ReadToken(token)
                    FXAction.name.set(token)
                    continue
                }
                if (0 == token.Icmp("fire")) {
                    src.ReadToken(token)
                    FXAction.fire.set(token)
                    continue
                }
                if (0 == token.Icmp("random")) {
                    FXAction.random1 = src.ParseFloat()
                    src.ExpectTokenString(",")
                    FXAction.random2 = src.ParseFloat()
                    FXAction.delay = 0.0f // check random
                    continue
                }
                if (0 == token.Icmp("delay")) {
                    FXAction.delay = src.ParseFloat()
                    continue
                }
                if (0 == token.Icmp("rotate")) {
                    FXAction.rotate = src.ParseFloat()
                    continue
                }
                if (0 == token.Icmp("duration")) {
                    FXAction.duration = src.ParseFloat()
                    continue
                }
                if (0 == token.Icmp("trackorigin")) {
                    FXAction.trackOrigin = src.ParseBool()
                    continue
                }
                if (0 == token.Icmp("restart")) {
                    FXAction.restart = src.ParseFloat()
                    continue
                }
                if (0 == token.Icmp("fadeIn")) {
                    FXAction.fadeInTime = src.ParseFloat()
                    continue
                }
                if (0 == token.Icmp("fadeOut")) {
                    FXAction.fadeOutTime = src.ParseFloat()
                    continue
                }
                if (0 == token.Icmp("size")) {
                    FXAction.size = src.ParseFloat()
                    continue
                }
                if (0 == token.Icmp("offset")) {
                    FXAction.offset.x = src.ParseFloat()
                    src.ExpectTokenString(",")
                    FXAction.offset.y = src.ParseFloat()
                    src.ExpectTokenString(",")
                    FXAction.offset.z = src.ParseFloat()
                    continue
                }
                if (0 == token.Icmp("axis")) {
                    val v = idVec3()
                    v.x = src.ParseFloat()
                    src.ExpectTokenString(",")
                    v.y = src.ParseFloat()
                    src.ExpectTokenString(",")
                    v.z = src.ParseFloat()
                    v.Normalize()
                    FXAction.axis.set(v.ToMat3())
                    FXAction.explicitAxis = true
                    continue
                }
                if (0 == token.Icmp("angle")) {
                    val a = idAngles()
                    a[0] = src.ParseFloat()
                    src.ExpectTokenString(",")
                    a[1] = src.ParseFloat()
                    src.ExpectTokenString(",")
                    a[2] = src.ParseFloat()
                    FXAction.axis.set(a.ToMat3())
                    FXAction.explicitAxis = true
                    continue
                }
                if (0 == token.Icmp("uselight")) {
                    src.ReadToken(token)
                    FXAction.data.set(token)
                    for (i in 0 until events.Num()) {
                        if (events[i].name.Icmp(FXAction.data.toString()) == 0) {
                            FXAction.sibling = i
                            FXAction.lightColor.set(events[i].lightColor)
                            FXAction.lightRadius = events[i].lightRadius
                        }
                    }
                    FXAction.type = fx_enum.FX_LIGHT

                    // precache the light material
                    DeclManager.declManager.FindMaterial(FXAction.data)
                    continue
                }
                if (0 == token.Icmp("attachlight")) {
                    src.ReadToken(token)
                    FXAction.data.set(token)
                    FXAction.type = fx_enum.FX_ATTACHLIGHT

                    // precache it
                    DeclManager.declManager.FindMaterial(FXAction.data)
                    continue
                }
                if (0 == token.Icmp("attachentity")) {
                    src.ReadToken(token)
                    FXAction.data.set(token)
                    FXAction.type = fx_enum.FX_ATTACHENTITY

                    // precache the model
                    ModelManager.renderModelManager.FindModel(FXAction.data)
                    continue
                }
                if (0 == token.Icmp("launch")) {
                    src.ReadToken(token)
                    FXAction.data.set(token)
                    FXAction.type = fx_enum.FX_LAUNCH

                    // precache the entity def
                    DeclManager.declManager.FindType(declType_t.DECL_ENTITYDEF, FXAction.data)
                    continue
                }
                if (0 == token.Icmp("useModel")) {
                    src.ReadToken(token)
                    FXAction.data.set(token)
                    for (i in 0 until events.Num()) {
                        if (events[i].name.Icmp(FXAction.data) == 0) {
                            FXAction.sibling = i
                        }
                    }
                    FXAction.type = fx_enum.FX_MODEL

                    // precache the model
                    ModelManager.renderModelManager.FindModel(FXAction.data)
                    continue
                }
                if (0 == token.Icmp("light")) {
                    src.ReadToken(token)
                    FXAction.data.set(token)
                    src.ExpectTokenString(",")
                    FXAction.lightColor[0] = src.ParseFloat()
                    src.ExpectTokenString(",")
                    FXAction.lightColor[1] = src.ParseFloat()
                    src.ExpectTokenString(",")
                    FXAction.lightColor[2] = src.ParseFloat()
                    src.ExpectTokenString(",")
                    FXAction.lightRadius = src.ParseFloat()
                    FXAction.type = fx_enum.FX_LIGHT

                    // precache the light material
                    DeclManager.declManager.FindMaterial(FXAction.data)
                    continue
                }
                if (0 == token.Icmp("model")) {
                    src.ReadToken(token)
                    FXAction.data.set(token)
                    FXAction.type = fx_enum.FX_MODEL

                    // precache it
                    ModelManager.renderModelManager.FindModel(FXAction.data.toString())
                    continue
                }
                if (0 == token.Icmp("particle")) {    // FIXME: now the same as model
                    src.ReadToken(token)
                    FXAction.data.set(token)
                    FXAction.type = fx_enum.FX_PARTICLE

                    // precache it
                    ModelManager.renderModelManager.FindModel(FXAction.data.toString())
                    continue
                }
                if (0 == token.Icmp("decal")) {
                    src.ReadToken(token)
                    FXAction.data.set(token)
                    FXAction.type = fx_enum.FX_DECAL

                    // precache it
                    DeclManager.declManager.FindMaterial(FXAction.data)
                    continue
                }
                if (0 == token.Icmp("particleTrackVelocity")) {
                    FXAction.particleTrackVelocity = true
                    continue
                }
                if (0 == token.Icmp("sound")) {
                    src.ReadToken(token)
                    FXAction.data.set(token)
                    FXAction.type = fx_enum.FX_SOUND

                    // precache it
                    DeclManager.declManager.FindSound(FXAction.data)
                    continue
                }
                if (0 == token.Icmp("ignoreMaster")) {
                    FXAction.shakeIgnoreMaster = true
                    continue
                }
                if (0 == token.Icmp("shockwave")) {
                    src.ReadToken(token)
                    FXAction.data.set(token)
                    FXAction.type = fx_enum.FX_SHOCKWAVE

                    // precache the entity def
                    DeclManager.declManager.FindType(declType_t.DECL_ENTITYDEF, FXAction.data)
                    continue
                }
                src.Warning("FX File: bad token")
                continue
            }
        }

        fun oSet(idDeclFX: idDeclFX?) {
            throw UnsupportedOperationException("Not supported yet.")
        }
    }
}
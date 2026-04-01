/*
 * Copyright (C) 1999-2011 id Software LLC, a ZeniMax Media company.
 * Translated to Kotlin by Dr. Feederino with support of Claude Code
 *
 * This file is part of the Doom 3 Kotlin project.
 * Original source: neo/game/Camera.cpp, neo/game/Camera.h
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

import neo.Game.GameSys.Class.*
import neo.Game.GameSys.Event.idEventDef
import neo.Game.GameSys.SaveGame.idRestoreGame
import neo.Game.GameSys.SaveGame.idSaveGame
import neo.Game.GameSys.SysCvar
import neo.Game.Game_local.Companion.isD3XP
import neo.Game.Game_local.idEntityPtr
import neo.Game.Game_local.idGameLocal
import neo.Game.Script.EV_Thread_SetCallback
import neo.Game.Script.Script_Thread.idThread
import neo.Renderer.Model
import neo.Renderer.RenderWorld.renderView_s
import neo.framework.UsercmdGen
import neo.idlib.Text.Lexer
import neo.idlib.Text.Lexer.idLexer
import neo.idlib.Text.Str
import neo.idlib.Text.Str.idStr
import neo.idlib.Text.Token.idToken
import neo.idlib.containers.CFloat
import neo.idlib.containers.List
import neo.idlib.math.idCQuat
import neo.idlib.math.idQuat
import neo.idlib.math.idVec3

val EV_Camera_Start: idEventDef = idEventDef("start", null)
val EV_Camera_Stop: idEventDef = idEventDef("stop", null)
val EV_Camera_SetAttachments: idEventDef = idEventDef("<getattachments>", null)


/*
 ===============================================================================

 Camera providing an alternative view of the level.

 ===============================================================================
 */

/*
 ===============================================================================

   idCamera

   Base class for cameras

 ===============================================================================
 */
abstract class idCamera : idEntity() {
    companion object {
        val Type = idTypeInfo("idCamera", "idEntity")
    }

    override fun GetType(): idTypeInfo = Type

    /*
     =====================
     idCamera::GetViewParms
     =====================
     */
    abstract fun GetViewParms(view: renderView_s?)

    /*
     =====================
     idCamera::GetRenderView
     =====================
     */
    override fun GetRenderView(): renderView_s? {
        val rv = super.GetRenderView()
        GetViewParms(rv)
        return rv
    }

    open fun Stop() {}
}

/*
 ***********************************************************************

   idCameraView

 ***********************************************************************
 */
class idCameraView : idCamera() {
    companion object {
        val Type = idTypeInfo("idCameraView", "idCamera") { idCameraView() }

        private val eventCallbacks: MutableMap<idEventDef, eventCallback_t<*>> = HashMap()
        fun getEventCallBacks(): MutableMap<idEventDef, eventCallback_t<*>> {
            return eventCallbacks
        }

        init {
            eventCallbacks.putAll(idEntity.getEventCallBacks())
            eventCallbacks[EV_Activate] =
                eventCallback_t1 { obj: idCameraView, activator: idEventArg<*>? ->
                    obj.Event_Activate(activator as idEventArg<idEntity>)
                }
            eventCallbacks[EV_Camera_SetAttachments] =
                eventCallback_t0 { obj: idCameraView -> obj.Event_SetAttachments() }
        }
    }

    protected var attachedTo: idEntity? = null
    protected var attachedView: idEntity? = null
    protected var fov = 90.0f

    /*
     ===============
     idCameraView::Save
     ================
     */
    override fun Save(savefile: idSaveGame) {
        super.Save(savefile)
        savefile.WriteFloat(fov)
        savefile.WriteObject(attachedTo)
        savefile.WriteObject(attachedView)
    }

    /*
     ===============
     idCameraView::Restore
     ================
     */
    override fun Restore(savefile: idRestoreGame) {
        super.Restore(savefile)
        val fov = CFloat(fov)
        savefile.ReadFloat(fov)
        attachedTo = savefile.ReadObject() as idEntity?
        attachedView = savefile.ReadObject() as idEntity?
        this.fov = fov._val
    }

    /*
     =====================
     idCameraView::Spawn
     =====================
     */
    override fun Spawn() {
        super.Spawn()

        // if no target specified use ourself
        val cam = spawnArgs.GetString("cameraTarget")
        if (cam.isEmpty()) {
            spawnArgs.Set("cameraTarget", spawnArgs.GetString("name"))
        }
        fov = spawnArgs.GetFloat("fov", "90")
        PostEventMS(EV_Camera_SetAttachments, 0)
        UpdateChangeableSpawnArgs(null)
    }

    /*
     =====================
     idCameraView::GetViewParms
     =====================
     */
    override fun GetViewParms(view: renderView_s?) {
        assert(view != null)
        if (view == null) {
            return
        }
        val dir = idVec3()
        val ent: idEntity?
        ent = if (attachedTo != null) {
            attachedTo
        } else {
            this
        }
        view.vieworg.set(ent!!.GetPhysics().GetOrigin())
        if (attachedView != null) {
            dir.set(attachedView!!.GetPhysics().GetOrigin().minus(view.vieworg))
            dir.Normalize()
            // FIX: Removed unnecessary idMat3() constructor wrapping - dir.ToMat3() already returns idMat3
            view.viewaxis.set(dir.ToMat3())
        } else {
            // FIX: Removed unnecessary idMat3() constructor wrapping - GetAxis() already returns idMat3
            view.viewaxis.set(ent.GetPhysics().GetAxis())
        }
        val fov_x = CFloat(view.fov_x)
        val fov_y = CFloat(view.fov_y)
        Game_local.gameLocal.CalcFov(fov, fov_x, fov_y)
        view.fov_x = fov_x._val
        view.fov_y = fov_y._val
    }

    /*
     =====================
     idCameraView::Stop
     =====================
     */
    override fun Stop() {
        if (SysCvar.g_debugCinematic.GetBool()) {
            Game_local.gameLocal.Printf("%d: '%s' stop\n", Game_local.gameLocal.framenum, GetName())
        }
        Game_local.gameLocal.SetCamera(null)
        ActivateTargets(Game_local.gameLocal.GetLocalPlayer())
    }

    /*
     ===============
     idCameraView::Event_Activate
     ================
     */
    protected fun Event_Activate(activator: idEventArg<idEntity>) {
        if (spawnArgs.GetBool("trigger")) {
            if (Game_local.gameLocal.GetCamera() !== this) {
                if (SysCvar.g_debugCinematic.GetBool()) {
                    Game_local.gameLocal.Printf("%d: '%s' start\n", Game_local.gameLocal.framenum, GetName())
                }
                Game_local.gameLocal.SetCamera(this)
            } else {
                if (SysCvar.g_debugCinematic.GetBool()) {
                    Game_local.gameLocal.Printf("%d: '%s' stop\n", Game_local.gameLocal.framenum, GetName())
                }
                Game_local.gameLocal.SetCamera(null)
            }
        }
    }

    /*
     ===============
     idCameraView::Event_SetAttachments
     ================
     */
    protected fun Event_SetAttachments() {
        val attachedTo = arrayOf(attachedTo)
        val attachedView = arrayOf(attachedView)
        SetAttachment(attachedTo, "attachedTo")
        SetAttachment(attachedView, "attachedView")
        this.attachedTo = attachedTo[0]
        this.attachedView = attachedView[0]
    }

    /*
     =====================
     idCameraView::SetAttachment
     =====================
     */
    protected fun SetAttachment(e: Array<idEntity?>, p: String) {
        val cam = spawnArgs.GetString(p)
        if (cam.isNotEmpty()) {
            // FIX: Removed !! — C++ assigns FindEntity result directly, which can be NULL.
            // Using !! would throw NPE if the entity name doesn't resolve.
            e[0] = Game_local.gameLocal.FindEntity(cam)
        }
    }

    override fun GetType(): idTypeInfo = Type
    override fun CreateInstance(): idClass = idCameraView()

    override fun getEventCallBack(event: idEventDef): eventCallback_t<*>? {
        return eventCallbacks[event]
    }
}

/*
 ===============================================================================

 A camera which follows a path defined by an animation.

 ===============================================================================
 */
class cameraFrame_t {
    val q: idCQuat = idCQuat()
    val t: idVec3 = idVec3()
    var fov = 0.0f
}

/*
 ===============================================================================

   idCameraAnim

 ===============================================================================
 */
class idCameraAnim : idCamera() {
    companion object {
        val Type = idTypeInfo("idCameraAnim", "idCamera") { idCameraAnim() }

        private val eventCallbacks: MutableMap<idEventDef, eventCallback_t<*>> = HashMap()

        fun getEventCallBacks(): MutableMap<idEventDef, eventCallback_t<*>> {
            return eventCallbacks
        }

        init {
            eventCallbacks.putAll(idEntity.getEventCallBacks())
            eventCallbacks[EV_Thread_SetCallback] =
                eventCallback_t0<idCameraAnim> { obj: idCameraAnim -> obj.Event_SetCallback() }
            eventCallbacks[EV_Camera_Stop] =
                eventCallback_t0<idCameraAnim> { obj: idCameraAnim -> obj.Event_Stop() }
            eventCallbacks[EV_Camera_Start] =
                eventCallback_t0<idCameraAnim> { obj: idCameraAnim -> obj.Event_Start() }
            eventCallbacks[EV_Activate] =
                eventCallback_t1<idCameraAnim> { obj: idCameraAnim, _activator: idEventArg<*>? ->
                    obj.Event_Activate(_activator as idEventArg<idEntity>)
                }
        }
    }

    private val activator: idEntityPtr<idEntity>
    private val camera: List.idList<cameraFrame_t> = List.idList()
    private val cameraCuts: List.idList<Int> = List.idList()
    private var cycle: Int
    private var frameRate: Int
    private val offset: idVec3
    private var starttime: Int
    private var threadNum = 0

    /*
     ===============
     idCameraAnim::Save
     ================
     */
    override fun Save(savefile: idSaveGame) {
        super.Save(savefile)
        savefile.WriteInt(threadNum)
        savefile.WriteVec3(offset)
        savefile.WriteInt(frameRate)
        savefile.WriteInt(starttime)
        savefile.WriteInt(cycle)
        activator.Save(savefile)
    }

    /*
     ===============
     idCameraAnim::Restore
     ================
     */
    override fun Restore(savefile: idRestoreGame) {
        super.Restore(savefile)
        threadNum = savefile.ReadInt()
        savefile.ReadVec3(offset)
        frameRate = savefile.ReadInt()
        starttime = savefile.ReadInt()
        cycle = savefile.ReadInt()
        activator.Restore(savefile)
        LoadAnim()
    }

    /*
     =====================
     idCameraAnim::Spawn
     =====================
     */
    override fun Spawn() {
        super.Spawn()
        if (spawnArgs.GetVector("old_origin", "0 0 0", offset)) {
            offset.set(GetPhysics().GetOrigin().minus(offset))
        } else {
            offset.Zero()
        }

        // always think during cinematics
        cinematic = true
        LoadAnim()
    }

    /*
     =====================
     idCameraAnim::GetViewParms
     =====================
     */
    override fun GetViewParms(view: renderView_s?) {
        val realFrame: Int
        var frame: Int
        val frameTime: Int
        val lerp: Float
        val invlerp: Float
        val camFrame: cameraFrame_t?
        var i: Int
        var cut: Int
        val q1 = idQuat()
        val q2 = idQuat()
        val q3 = idQuat()
        assert(view != null)
        if (null == view) {
            return
        }
        if (camera.Num() == 0) {
            // we most likely are in the middle of a restore
            // FIXME: it would be better to fix it so this doesn't get called during a restore
            return
        }
        val ts = if (isD3XP) SetTimeState(timeGroup) else null
        try {
        if (frameRate == UsercmdGen.USERCMD_HZ) {
            frameTime = Game_local.gameLocal.time - starttime
            frame = (frameTime / idGameLocal.msecPrecise).toInt()
            lerp = 0.0f
        } else {
            frameTime = (Game_local.gameLocal.time - starttime) * frameRate
            frame = frameTime / 1000
            lerp = (frameTime % 1000) * 0.001f
        }

        // skip any frames where camera cuts occur
        realFrame = frame
        cut = 0
        i = 0
        while (i < cameraCuts.Num()) {
            if (frame < cameraCuts[i]) {
                break
            }
            frame++
            cut++
            i++
        }
        if (SysCvar.g_debugCinematic.GetBool()) {
            val prevFrameTime: Int =
                (Game_local.gameLocal.time - starttime - Game_local.gameLocal.msec) * frameRate
            var prevFrame = prevFrameTime / 1000
            var prevCut: Int
            prevCut = 0
            i = 0
            while (i < cameraCuts.Num()) {
                if (prevFrame < cameraCuts[i]) {
                    break
                }
                prevFrame++
                prevCut++
                i++
            }
            if (prevCut != cut) {
                Game_local.gameLocal.Printf("%d: '%s' cut %d\n", Game_local.gameLocal.framenum, GetName(), cut)
            }
        }

        // clamp to the first frame.  also check if this is a one frame anim.  one frame anims would end immediately,
        // but since they're mainly used for static cams anyway, just stay on it infinitely.
        if (frame < 0 || camera.Num() < 2) {
            view.viewaxis.set(camera[0].q.ToQuat().ToMat3())
            view.vieworg.set(camera[0].t.plus(offset))
            view.fov_x = camera[0].fov
        } else if (frame > camera.Num() - 2) {
            if (cycle > 0) {
                cycle--
            }
            if (cycle != 0) {
                // advance start time so that we loop
                starttime += (camera.Num() - cameraCuts.Num()) * 1000 / frameRate
                GetViewParms(view)
                return
            }
            Stop()
            if (Game_local.gameLocal.GetCamera() != null) {
                // we activated another camera when we stopped, so get it's viewparms instead
                Game_local.gameLocal.GetCamera()!!.GetViewParms(view)
                return
            } else {
                // just use our last frame
                camFrame = camera[camera.Num() - 1]
                view.viewaxis.set(camFrame.q.ToQuat().ToMat3())
                view.vieworg.set(camFrame.t.plus(offset))
                view.fov_x = camFrame.fov
            }
        } else if (lerp == 0.0f) {
            camFrame = camera[frame]
            view.viewaxis.set(camFrame.q.ToMat3())
            view.vieworg.set(camFrame.t + offset)
            view.fov_x = camFrame.fov
        } else {
            camFrame = camera[frame]
            val nextFrame = camera[frame + 1]
            invlerp = 1.0f - lerp
            q1.set(camFrame.q.ToQuat())
            q2.set(nextFrame.q.ToQuat())
            q3.Slerp(q1, q2, lerp)
            view.viewaxis.set(q3.ToMat3())
            view.vieworg.set(
                camFrame.t * invlerp + nextFrame.t * lerp + offset
            )
            view.fov_x = camFrame.fov * invlerp + nextFrame.fov * lerp
        }

        val fov_x = CFloat(view.fov_x)
        val fov_y = CFloat(view.fov_y)
        Game_local.gameLocal.CalcFov(view.fov_x, fov_x, fov_y)
        view.fov_x = fov_x._val
        view.fov_y = fov_y._val

        // setup the pvs for this frame
        UpdatePVSAreas(view.vieworg)

        if (SysCvar.g_showcamerainfo.GetBool()) {
            Game_local.gameLocal.Printf("^5Frame: ^7%d/%d\n\n\n", realFrame + 1, camera.Num() - cameraCuts.Num())
        }
        } finally {
            ts?.close()
        }
    }

    /*
     ===============
     idCameraAnim::Start
     ================
     */
    private fun Start() {
        cycle = spawnArgs.GetInt("cycle")
        if (0 == cycle) {
            cycle = 1
        }
        if (SysCvar.g_debugCinematic.GetBool()) {
            Game_local.gameLocal.Printf("%d: '%s' start\n", Game_local.gameLocal.framenum, GetName())
        }
        starttime = Game_local.gameLocal.time
        Game_local.gameLocal.SetCamera(this)
        BecomeActive(TH_THINK)

        // if the player has already created the renderview for this frame, have him update it again so that the camera starts this frame
        if (Game_local.gameLocal.GetLocalPlayer()!!.GetRenderView()!!.time == Game_local.gameLocal.time) {
            Game_local.gameLocal.GetLocalPlayer()!!.CalculateRenderView()
        }
    }

    /*
     =====================
     idCameraAnim::Stop
     =====================
     */
    override fun Stop() {
        if (Game_local.gameLocal.GetCamera() == this) {
            if (SysCvar.g_debugCinematic.GetBool()) {
                Game_local.gameLocal.Printf("%d: '%s' stop\n", Game_local.gameLocal.framenum, GetName())
            }
            BecomeInactive(TH_THINK)
            Game_local.gameLocal.SetCamera(null)
            if (threadNum != 0) {
                idThread.ObjectMoveDone(threadNum, this)
                threadNum = 0
            }
            ActivateTargets(activator.GetEntity())
        }
    }

    /*
     =====================
     idCameraAnim::Think
     =====================
     */
    override fun Think() {
        val frame: Int
        val frameTime: Int
        if ((thinkFlags and TH_THINK) != 0) {
            // check if we're done in the Think function when the cinematic is being skipped (idCameraAnim::GetViewParms isn't called when skipping cinematics).
            if (!Game_local.gameLocal.skipCinematic) {
                return
            }
            if (camera.Num() < 2) {
                // 1 frame anims never end
                return
            }
            if (frameRate == UsercmdGen.USERCMD_HZ) {
                frameTime = Game_local.gameLocal.time - starttime
                frame = (frameTime / idGameLocal.msecPrecise).toInt()
            } else {
                frameTime = (Game_local.gameLocal.time - starttime) * frameRate
                frame = frameTime / 1000
            }
            if (frame > camera.Num() + cameraCuts.Num() - 2) {
                if (cycle > 0) {
                    cycle--
                }
                if (cycle != 0) {
                    // advance start time so that we loop
                    starttime += (camera.Num() - cameraCuts.Num()) * 1000 / frameRate
                } else {
                    Stop()
                }
            }
        }
    }

    /*
     ================
     idCameraAnim::LoadAnim
     ================
     */
    private fun LoadAnim() {
        val version: Int
        val parser =
            idLexer(Lexer.LEXFL_ALLOWPATHNAMES or Lexer.LEXFL_NOSTRINGESCAPECHARS or Lexer.LEXFL_NOSTRINGCONCAT)
        val token = idToken()
        val numFrames: Int
        val numCuts: Int
        var i: Int
        val filename: idStr
        val key: String?
        key = spawnArgs.GetString("anim")
        if (null == key || key.isEmpty()) {
            idGameLocal.Error("Missing 'anim' key on '%s'", name)
        }
        filename = idStr(spawnArgs.GetString(Str.va("anim %s", key)))
        if (0 == filename.Length()) {
            idGameLocal.Error("Missing 'anim %s' key on '%s'", key, name)
        }
        filename.SetFileExtension(Model.MD5_CAMERA_EXT)
        if (!parser.LoadFile(filename)) {
            idGameLocal.Error("Unable to load '%s' on '%s'", filename, name)
        }

        cameraCuts.Clear()
        cameraCuts.SetGranularity(1)
        camera.Clear()
        camera.SetGranularity(1)

        parser.ExpectTokenString(Model.MD5_VERSION_STRING)
        version = parser.ParseInt()
        if (version != Model.MD5_VERSION) {
            parser.Error("Invalid version %d.  Should be version %d\n", version, Model.MD5_VERSION)
        }

        // skip the commandline
        parser.ExpectTokenString("commandline")
        parser.ReadToken(token)

        // parse num frames
        parser.ExpectTokenString("numFrames")
        numFrames = parser.ParseInt()
        if (numFrames <= 0) {
            parser.Error("Invalid number of frames: %d", numFrames)
        }

        // parse framerate
        parser.ExpectTokenString("frameRate")
        frameRate = parser.ParseInt()
        if (frameRate <= 0) {
            parser.Error("Invalid framerate: %d", frameRate)
        }

        // parse num cuts
        parser.ExpectTokenString("numCuts")
        numCuts = parser.ParseInt()
        if (numCuts < 0 || numCuts > numFrames) {
            parser.Error("Invalid number of camera cuts: %d", numCuts)
        }

        // parse the camera cuts
        parser.ExpectTokenString("cuts")
        parser.ExpectTokenString("{")
        cameraCuts.SetNum(numCuts)
        i = 0
        while (i < numCuts) {
            cameraCuts[i] = parser.ParseInt()
            if (cameraCuts[i] < 1 || cameraCuts[i] >= numFrames) {
                parser.Error("Invalid camera cut")
            }
            i++
        }
        parser.ExpectTokenString("}")

        // parse the camera frames
        parser.ExpectTokenString("camera")
        parser.ExpectTokenString("{")
        camera.SetNum(numFrames)
        i = 0
        while (i < numFrames) {
            val cam = cameraFrame_t()
            parser.Parse1DMatrix(3, cam.t)
            parser.Parse1DMatrix(3, cam.q)
            cam.fov = parser.ParseFloat()
            camera[i] = cam
            i++
        }
        parser.ExpectTokenString("}")
    }

    /*
     ===============
     idCameraAnim::Event_Start
     ================
     */
    private fun Event_Start() {
        Start()
    }

    /*
     ===============
     idCameraAnim::Event_Stop
     ================
     */
    private fun Event_Stop() {
        Stop()
    }

    /*
     ================
     idCameraAnim::Event_SetCallback
     ================
     */
    private fun Event_SetCallback() {
        if (Game_local.gameLocal.GetCamera() == this && 0 == threadNum) {
            threadNum = idThread.CurrentThreadNum()
            idThread.ReturnInt(true)
        } else {
            idThread.ReturnInt(false)
        }
    }

    /*
     ===============
     idCameraAnim::Event_Activate
     ================
     */
    private fun Event_Activate(_activator: idEventArg<idEntity>) {
        activator.oSet(_activator.value)
        if ((thinkFlags and TH_THINK) != 0) {
            Stop()
        } else {
            Start()
        }
    }

    override fun GetType(): idTypeInfo = Type
    override fun CreateInstance(): idClass = idCameraAnim()

    override fun getEventCallBack(event: idEventDef): eventCallback_t<*>? {
        return eventCallbacks[event]
    }

    /*
     =====================
     idCameraAnim::idCameraAnim
     =====================
     */
    init {
        offset = idVec3()
        frameRate = 0
        starttime = 0
        cycle = 1
        activator = idEntityPtr()
    }

    // NOTE: C++ ~idCameraAnim() clears gameLocal camera if this == active camera.
    // Kotlin has no deterministic destructors; cameras are always explicitly stopped
    // via Stop(), so this is safe in practice.
}

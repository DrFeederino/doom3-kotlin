/*
 * Copyright (C) 1999-2011 id Software LLC, a ZeniMax Media company.
 * Translated to Kotlin by Dr. Feederino with support of Claude Code
 *
 * This file is part of the Doom 3 Kotlin project.
 * Original source: neo/Game/GameEdit.cpp, neo/Game/GameEdit.h
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
import neo.Game.Game.idGameEdit
import neo.Game.GameSys.Class
import neo.Game.GameSys.Class.idTypeInfo
import neo.Game.GameSys.SysCvar
import neo.Game.Game_local.idEntityPtr
import neo.Game.Light.idLight
import neo.Game.Misc.idFuncEmitter
import neo.Game.Physics.Clip
import neo.Game.Physics.Force_Drag.idForce_Drag
import neo.Game.Physics.Physics_AF.idPhysics_AF
import neo.Game.Physics.Physics_Monster.idPhysics_Monster
import neo.Game.Physics.Physics_RigidBody.idPhysics_RigidBody
import neo.Game.Player.idPlayer
import neo.Game.Sound.idSound
import neo.Game.WorldSpawn.idWorldspawn
import neo.Renderer.Material
import neo.Renderer.Model
import neo.cm.trace_s
import neo.framework.DeclManager
import neo.framework.DeclManager.declState_t
import neo.framework.UsercmdGen
import neo.idlib.*
import neo.idlib.BV.Box.idBox
import neo.idlib.BV.idBounds
import neo.idlib.Dict_h.idKeyValue
import neo.idlib.Text.Lexer.idLexer
import neo.idlib.Text.Str
import neo.idlib.Text.Str.idStr
import neo.idlib.Text.Token.idToken
import neo.idlib.containers.List
import neo.idlib.math.Matrix.idMat3
import neo.idlib.math.idVec3
import neo.idlib.math.idVec4
import java.util.*
import java.util.regex.Pattern
import kotlin.experimental.and

object GameEdit {
    /*
     ===============================================================================

     Allows entities to be dragged through the world with physics.

     ===============================================================================
     */
    const val MAX_DRAG_TRACE_DISTANCE = 2048.0f
    private val gameEditLocal: idGameEdit = idGameEdit()


    val gameEdit = gameEditLocal
    private fun sscanf(key: idStr, pattern: String): Int {
        var pattern = pattern
        var a = -1
        pattern = pattern.replace("%d".toRegex(), "\\d+")
        var scanner = Scanner(key.toString())
        val result: String? = scanner.findInLine(Pattern.compile(pattern))
        if (result != null && result.isNotEmpty()) {
            scanner = Scanner(result)
            scanner.findInLine("bind")
            a = scanner.nextInt()
        }
        scanner.close()
        return a
    }

    /*
     ===============================================================================

     Ingame cursor.

     ===============================================================================
     */
    class idCursor3D : idEntity() {
        companion object {
            val Type = idTypeInfo("idCursor3D", "idEntity") { idCursor3D() }
        }

        override fun GetType(): idTypeInfo = Type
        override fun CreateInstance(): Class.idClass = idCursor3D()

        //
        //
        var drag: idForce_Drag = idForce_Drag()

        //~idCursor3D( void );
        val draggedPosition: idVec3 = idVec3()

        override fun Present() {
            // don't present to the renderer if the entity hasn't changed
            if (0 == thinkFlags and TH_UPDATEVISUALS) {
                return
            }
            BecomeInactive(TH_UPDATEVISUALS)
            val origin = GetPhysics().GetOrigin()
            val axis = GetPhysics().GetAxis()
            Game_local.gameRenderWorld!!.DebugArrow(
                colorYellow,
                origin + (axis[1] * -5.0f + axis[2] * 5.0f),
                origin,
                2
            )
            Game_local.gameRenderWorld!!.DebugArrow(colorRed, origin, draggedPosition, 2)
        }

        override fun Think() {
            if ((thinkFlags and TH_THINK) != 0) {
                drag.Evaluate(Game_local.gameLocal.time)
            }
            Present()
        }

        //    public 	CLASS_PROTOTYPE( idCursor3D );
        init {
            draggedPosition.Zero()
        }
    }

    class idDragEntity {
        private var bodyName // name of the body being dragged
                : idStr
        private var cursor // cursor entity
                : idCursor3D? = null
        private val dragEnt // entity being dragged
                : idEntityPtr<idEntity?>
        private var id // id of body being dragged
                = 0
        private var   /*jointHandle_t*/joint // joint being dragged
                = 0
        private val localEntityPoint // dragged point in entity space
                : idVec3
        private val localPlayerPoint // dragged point in player space
                : idVec3
        private val selected // last dragged entity
                : idEntityPtr<idEntity?>

        // ~idDragEntity( void );
        fun Clear() {
            dragEnt.oSet(null)
            joint = Model.INVALID_JOINT
            id = 0
            localEntityPoint.Zero()
            localPlayerPoint.Zero()
            bodyName.Clear()
            selected.oSet(null)
        }

        fun Update(player: idPlayer) {
            val viewPoint = idVec3()
            val origin = idVec3()
            val viewAxis = idMat3()
            val axis = idMat3()
            val trace = trace_s()
            var newEnt: idEntity?
            var   /*jointHandle_t*/newJoint = 0
            var newBodyName = idStr()
            player.GetViewPos(viewPoint, viewAxis)

            // if no entity selected for dragging
            if (dragEnt.GetEntity() == null) {
                if ((player.usercmd.buttons.toInt() and UsercmdGen.BUTTON_ATTACK) != 0) {
                    Game_local.gameLocal.clip.TracePoint(
                        trace,
                        viewPoint,
                        viewPoint + viewAxis[0] * MAX_DRAG_TRACE_DISTANCE,
                        Material.CONTENTS_SOLID or Material.CONTENTS_RENDERMODEL or Material.CONTENTS_BODY,
                        player
                    )
                    if (trace.fraction < 1.0f) {
                        newEnt = Game_local.gameLocal.entities[trace.c.entityNum]
                        if (newEnt != null) {
                            if (newEnt.GetBindMaster() != null) {
                                if (newEnt.GetBindJoint() != 0) {
                                    trace.c.id = Clip.JOINT_HANDLE_TO_CLIPMODEL_ID(newEnt.GetBindJoint())
                                } else {
                                    trace.c.id = newEnt.GetBindBody()
                                }
                                newEnt = newEnt.GetBindMaster()
                            }
                            if (newEnt is idAFEntity_Base && newEnt.IsActiveAF()) {
                                val af = newEnt

                                // joint being dragged
                                newJoint = Clip.CLIPMODEL_ID_TO_JOINT_HANDLE(trace.c.id)
                                // get the body id from the trace model id which might be a joint handle
                                trace.c.id = af.BodyForClipModelId(trace.c.id)
                                // get the name of the body being dragged
                                newBodyName = af.GetAFPhysics().GetBody(trace.c.id)!!.GetName()
                            } else if (newEnt !is idWorldspawn) {
                                newJoint = if (trace.c.id < 0) {
                                    Clip.CLIPMODEL_ID_TO_JOINT_HANDLE(trace.c.id)
                                } else {
                                    Model.INVALID_JOINT
                                }
                                newBodyName = idStr("")
                            } else {
                                newJoint = Model.INVALID_JOINT
                                newEnt = null
                            }
                        }
                        if (newEnt != null) {
                            dragEnt.oSet(newEnt)
                            selected.oSet(newEnt)
                            joint = newJoint
                            id = trace.c.id
                            bodyName = newBodyName
                            if (null == cursor) {
                                cursor = Game_local.gameLocal.SpawnEntityType(idCursor3D.Type) as idCursor3D
                            }
                            val phys = dragEnt.GetEntity()!!.GetPhysics()
                            localPlayerPoint.set(trace.c.point.minus(viewPoint).times(viewAxis.Transpose()))
                            origin.set(phys.GetOrigin(id))
                            axis.set(phys.GetAxis(id))
                            localEntityPoint.set(trace.c.point.minus(origin).times(axis.Transpose()))
                            cursor!!.drag.Init(SysCvar.g_dragDamping.GetFloat())
                            cursor!!.drag.SetPhysics(phys, id, localEntityPoint)
                            cursor!!.Show()
                            if (phys is idPhysics_AF
                                || phys is idPhysics_RigidBody
                                || phys is idPhysics_Monster
                            ) {
                                cursor!!.BecomeActive(TH_THINK)
                            }
                        }
                    }
                }
            }

            // if there is an entity selected for dragging
            val drag = dragEnt.GetEntity()
            if (drag != null) {
                if (0 == (player.usercmd.buttons and UsercmdGen.BUTTON_ATTACK.toByte()).toInt()) {
                    StopDrag()
                    return
                }
                cursor!!.SetOrigin(viewPoint.plus(localPlayerPoint.times(viewAxis)))
                cursor!!.SetAxis(viewAxis)
                cursor!!.drag.SetDragPosition(cursor!!.GetPhysics().GetOrigin())
                val renderEntity = drag.GetRenderEntity()
                val dragAnimator = drag.GetAnimator()
                if (joint != Model.INVALID_JOINT && renderEntity != null && dragAnimator != null) {
                    dragAnimator.GetJointTransform(joint, Game_local.gameLocal.time, cursor!!.draggedPosition, axis)
                    cursor!!.draggedPosition.set(renderEntity.origin.plus(cursor!!.draggedPosition.times(renderEntity.axis)))
                    Game_local.gameRenderWorld!!.DrawText(
                        Str.va(
                            "%s\n%s\n%s, %s",
                            drag.GetName(),
                            drag.GetType().name,
                            dragAnimator.GetJointName(joint),
                            bodyName
                        ), cursor!!.GetPhysics().GetOrigin(), 0.1f, colorWhite, viewAxis, 1
                    )
                } else {
                    cursor!!.draggedPosition.set(cursor!!.GetPhysics().GetOrigin())
                    Game_local.gameRenderWorld!!.DrawText(
                        Str.va(
                            "%s\n%s\n%s",
                            drag.GetName(),
                            drag.GetType().name,
                            bodyName
                        ), cursor!!.GetPhysics().GetOrigin(), 0.1f, colorWhite, viewAxis, 1
                    )
                }
            }

            // if there is a selected entity
            if (selected.GetEntity() != null && SysCvar.g_dragShowSelection.GetBool()) {
                // draw the bbox of the selected entity
                val renderEntity = selected.GetEntity()!!.GetRenderEntity()
                if (renderEntity != null) {
                    Game_local.gameRenderWorld!!.DebugBox(
                        colorYellow,
                        idBox(renderEntity.bounds, renderEntity.origin, renderEntity.axis)
                    )
                }
            }
        }

        fun SetSelected(ent: idEntity?) {
            selected.oSet(ent)
            StopDrag()
        }

        fun GetSelected(): idEntity? {
            return selected.GetEntity()
        }

        fun DeleteSelected() {
//	delete selected.GetEntity();
            selected.oSet(null)
            StopDrag()
        }

        fun BindSelected() {
            var num: Int
            var largestNum: Int
            val lexer = idLexer()
            val type = idToken()
            val bodyName = idToken()
            val key = idStr()
            val bindBodyName: idStr
            val value: String
            var kv: idKeyValue?
            val af: idAFEntity_Base?
            af = dragEnt.GetEntity() as idAFEntity_Base?
            if (null == af || af !is idAFEntity_Base || !af.IsActiveAF()) {
                return
            }
            bindBodyName = af.GetAFPhysics().GetBody(id)!!.GetName()
            largestNum = 1

            // parse all the bind constraints
            kv = af.spawnArgs.MatchPrefix("bindConstraint ", null)
            while (kv != null) {
                key.set(kv.GetKey())
                key.Strip("bindConstraint ")
                if (sscanf(key, "bind%d").also { num = it } != -1) {
                    if (num >= largestNum) {
                        largestNum = num + 1
                    }
                }
                lexer.LoadMemory(kv.GetValue(), kv.GetValue().Length(), kv.GetKey())
                lexer.ReadToken(type)
                lexer.ReadToken(bodyName)
                lexer.FreeSource()

                // if there already exists a bind constraint for this body
                if (bodyName.Icmp(bindBodyName) == 0) {
                    // delete the bind constraint
                    af.spawnArgs.Delete(kv.GetKey())
                    kv = null
                }
                kv = af.spawnArgs.MatchPrefix("bindConstraint ", kv)
            }
            key.set(String.format("bindConstraint bind%d", largestNum))
            value = String.format("ballAndSocket %s %s", bindBodyName.toString(), af.GetAnimator().GetJointName(joint))
            af.spawnArgs.Set(key, value)
            af.spawnArgs.Set("bind", "worldspawn")
            af.Bind(Game_local.gameLocal.world, true)
        }

        fun UnbindSelected() {
            var kv: idKeyValue?
            val af: idAFEntity_Base?
            af = selected.GetEntity() as idAFEntity_Base?
            if (null == af || af !is idAFEntity_Base || !af.IsActiveAF()) {
                return
            }

            // unbind the selected entity
            af.Unbind()

            // delete all the bind constraints
            kv = selected.GetEntity()!!.spawnArgs.MatchPrefix("bindConstraint ", null)
            while (kv != null) {
                selected.GetEntity()!!.spawnArgs.Delete(kv.GetKey())
                kv = selected.GetEntity()!!.spawnArgs.MatchPrefix("bindConstraint ", null)
            }

            // delete any bind information
            af.spawnArgs.Delete("bind")
            af.spawnArgs.Delete("bindToJoint")
            af.spawnArgs.Delete("bindToBody")
        }

        private fun StopDrag() {
            dragEnt.oSet(null)
            if (cursor != null) {
                cursor!!.BecomeInactive(TH_THINK)
            }
        }

        //
        //
        init {
            localEntityPoint = idVec3()
            localPlayerPoint = idVec3()
            bodyName = idStr()
            selected = idEntityPtr()
            dragEnt = idEntityPtr()
            Clear()
        }
    }

    /*
     ===============================================================================

     Handles ingame entity editing.

     ===============================================================================
     */
    class selectedTypeInfo_s {
        var textKey: idStr = idStr()
        var typeInfo: idTypeInfo? = null
    }

    class idEditEntities {
        private var nextSelectTime: Int
        private val selectableEntityClasses: List.idList<selectedTypeInfo_s>
        private val selectedEntities: List.idList<idEntity>
        fun SelectEntity(origin: idVec3, dir: idVec3, skip: idEntity?): Boolean {
            val end = idVec3()
            var ent: idEntity?
            if (0 == SysCvar.g_editEntityMode.GetInteger() || selectableEntityClasses.Num() == 0) {
                return false
            }
            if (Game_local.gameLocal.time < nextSelectTime) {
                return true
            }
            nextSelectTime = Game_local.gameLocal.time + 300
            end.set(origin.plus(dir.times(4096.0f)))
            ent = null
            for (i in 0 until selectableEntityClasses.Num()) {
                ent = Game_local.gameLocal.FindTraceEntity(origin, end, selectableEntityClasses[i].typeInfo!!, skip)
                if (ent != null) {
                    break
                }
            }
            if (ent != null) {
                ClearSelectedEntities()
                if (EntityIsSelectable(ent)) {
                    AddSelectedEntity(ent)
                    Game_local.gameLocal.Printf("entity #%d: %s '%s'\n", ent.entityNumber, ent.GetClassname(), ent.name)
                    ent.ShowEditingDialog()
                    return true
                }
            }
            return false
        }

        fun AddSelectedEntity(ent: idEntity) {
            ent.fl.selected = true
            selectedEntities.AddUnique(ent)
        }

        fun RemoveSelectedEntity(ent: idEntity?) {
            if (selectedEntities.Find(ent) != null) {
                selectedEntities.Remove(ent!!)
            }
        }

        fun ClearSelectedEntities() {
            var i: Int
            val count: Int
            count = selectedEntities.Num()
            i = 0
            while (i < count) {
                selectedEntities[i].fl.selected = false
                i++
            }
            selectedEntities.Clear()
        }

        fun DisplayEntities() {
            var ent: idEntity?
            if (Game_local.gameLocal.GetLocalPlayer() == null) {
                return
            }
            selectableEntityClasses.Clear()
            val sit = selectedTypeInfo_s()
            when (SysCvar.g_editEntityMode.GetInteger()) {
                1 -> {
                    sit.typeInfo = idLight.Type
                    sit.textKey.set("texture")
                    selectableEntityClasses.Append(sit)
                }

                2 -> {
                    sit.typeInfo = idSound.Type
                    sit.textKey.set("s_shader")
                    selectableEntityClasses.Append(sit)
                    val sit2 = selectedTypeInfo_s()
                    sit2.typeInfo = idLight.Type
                    sit2.textKey.set("texture")
                    selectableEntityClasses.Append(sit2)
                }

                3 -> {
                    sit.typeInfo = idAFEntity_Base.Type
                    sit.textKey.set("articulatedFigure")
                    selectableEntityClasses.Append(sit)
                }

                4 -> {
                    sit.typeInfo = idFuncEmitter.Type
                    sit.textKey.set("model")
                    selectableEntityClasses.Append(sit)
                }

                5 -> {
                    sit.typeInfo = idAI.Type
                    sit.textKey.set("name")
                    selectableEntityClasses.Append(sit)
                }

                6 -> {
                    sit.typeInfo = idEntity.Type
                    sit.textKey.set("name")
                    selectableEntityClasses.Append(sit)
                }

                7 -> {
                    sit.typeInfo = idEntity.Type
                    sit.textKey.set("model")
                    selectableEntityClasses.Append(sit)
                }

                else -> return
            }
            val viewBounds = idBounds(Game_local.gameLocal.GetLocalPlayer()!!.GetPhysics().GetOrigin())
            val viewTextBounds = idBounds(Game_local.gameLocal.GetLocalPlayer()!!.GetPhysics().GetOrigin())
            val axis = Game_local.gameLocal.GetLocalPlayer()!!.viewAngles.ToMat3()
            viewBounds.ExpandSelf(512.0f)
            viewTextBounds.ExpandSelf(128.0f)
            var textKey: idStr
            ent = Game_local.gameLocal.spawnedEntities.Next()
            while (ent != null) {
                val color = idVec4()
                textKey = idStr("")
                if (!EntityIsSelectable(ent, color, textKey)) {
                    ent = ent.spawnNode.Next()
                    continue
                }
                var drawArrows = false
                if (ent.IsType(idAFEntity_Base.Type)) {
                    if (!(ent as idAFEntity_Base).IsActiveAF()) {
                        ent = ent.spawnNode.Next()
                        continue
                    }
                } else if (ent.IsType(idSound.Type)) {
                    if (ent.fl.selected) {
                        drawArrows = true
                    }
                    val ss = DeclManager.declManager.FindSound(ent.spawnArgs.GetString(textKey.toString()))!!
                    if (ss.HasDefaultSound() || ss.base!!.GetState() == declState_t.DS_DEFAULTED) {
                        color.set(1.0f, 0.0f, 1.0f, 1.0f)
                    }
                } else if (ent.IsType(idFuncEmitter.Type)) {
                    if (ent.fl.selected) {
                        drawArrows = true
                    }
                }
                if (!viewBounds.ContainsPoint(ent.GetPhysics().GetOrigin())) {
                    ent = ent.spawnNode.Next()
                    continue
                }
                Game_local.gameRenderWorld!!.DebugBounds(color, idBounds(ent.GetPhysics().GetOrigin()).Expand(8.0f))
                if (drawArrows) {
                    val start = idVec3(ent.GetPhysics().GetOrigin())
                    val end = idVec3(start.plus(idVec3(1, 0, 0).times(20.0f)))
                    Game_local.gameRenderWorld!!.DebugArrow(colorWhite, start, end, 2)
                    Game_local.gameRenderWorld!!.DrawText(
                        "x+",
                        end.plus(idVec3(4, 0, 0)),
                        0.15f,
                        colorWhite,
                        axis
                    )
                    end.set(start.plus(idVec3(1, 0, 0).times(-20.0f)))
                    Game_local.gameRenderWorld!!.DebugArrow(colorWhite, start, end, 2)
                    Game_local.gameRenderWorld!!.DrawText(
                        "x-",
                        end.plus(idVec3(-4, 0, 0)),
                        0.15f,
                        colorWhite,
                        axis
                    )
                    end.set(start.plus(idVec3(0, 1, 0).times(20.0f)))
                    Game_local.gameRenderWorld!!.DebugArrow(colorGreen, start, end, 2)
                    Game_local.gameRenderWorld!!.DrawText(
                        "y+",
                        end.plus(idVec3(0, 4, 0)),
                        0.15f,
                        colorWhite,
                        axis
                    )
                    end.set(start.plus(idVec3(0, 1, 0).times(-20.0f)))
                    Game_local.gameRenderWorld!!.DebugArrow(colorGreen, start, end, 2)
                    Game_local.gameRenderWorld!!.DrawText(
                        "y-",
                        end.plus(idVec3(0, -4, 0)),
                        0.15f,
                        colorWhite,
                        axis
                    )
                    end.set(start.plus(idVec3(0, 0, 1).times(20.0f)))
                    Game_local.gameRenderWorld!!.DebugArrow(colorBlue, start, end, 2)
                    Game_local.gameRenderWorld!!.DrawText(
                        "z+",
                        end.plus(idVec3(0, 0, 4)),
                        0.15f,
                        colorWhite,
                        axis
                    )
                    end.set(start.plus(idVec3(0, 0, 1).times(-20.0f)))
                    Game_local.gameRenderWorld!!.DebugArrow(colorBlue, start, end, 2)
                    Game_local.gameRenderWorld!!.DrawText(
                        "z-",
                        end.plus(idVec3(0, 0, -4)),
                        0.15f,
                        colorWhite,
                        axis
                    )
                }
                if (textKey.Length() != 0) {
                    val text = ent.spawnArgs.GetString(textKey.toString())
                    if (viewTextBounds.ContainsPoint(ent.GetPhysics().GetOrigin())) {
                        Game_local.gameRenderWorld!!.DrawText(
                            text,
                            ent.GetPhysics().GetOrigin().plus(idVec3(0, 0, 12)),
                            0.25f,
                            colorWhite,
                            axis,
                            1
                        )
                    }
                }
                ent = ent.spawnNode.Next()
            }
        }


        fun EntityIsSelectable(
            ent: idEntity,
            color: idVec4? = null /* = NULL*/,
            text: idStr? = null /*= NULL*/
        ): Boolean {
            for (i in 0 until selectableEntityClasses.Num()) {
                if (ent.IsType(selectableEntityClasses[i].typeInfo!!)) {
                    text?.set(selectableEntityClasses[i].textKey)
                    if (color != null) {
                        if (ent.fl.selected) {
                            color.set(colorRed)
                        } else {
                            when (i) {
                                1 -> color.set(colorYellow)
                                2 -> color.set(colorBlue)
                                else -> color.set(colorGreen)
                            }
                        }
                    }
                    return true
                }
            }
            return false
        }

        //
        //
        init {
            selectableEntityClasses = List.idList()
            selectedEntities = List.idList()
            nextSelectTime = 0
        }
    }
}